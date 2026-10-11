#!/usr/bin/env bash
# Выполняет задание в opencode (модель big-pickle) и следит за ним.
# Ложную ошибку «Free usage exceeded» лечит перезапуском той же сессии в новом процессе
# (новый процесс = новое HTTP-подключение без старых cookie) с паузами 5, 5, 10, 30, 60 с.
# После пятого перезапуска останавливается: дальше решает пользователь.
#
# Использование:
#   scripts/opencode-task.sh "текст задания"
#   scripts/opencode-task.sh -f задание.md
#   scripts/opencode-task.sh -s ses_XXXX "замечания ревью"     (продолжить существующую сессию)
#
# Коды выхода: 0 — opencode завершил работу; 75 — «Free usage exceeded» не прошёл после 5 перезапусков;
# 76 — opencode молчал дольше OPENCODE_IDLE_LIMIT секунд и остановлен; иначе — код самого opencode.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OPENCODE_BIN="${OPENCODE_BIN:-$HOME/.opencode/bin/opencode}"
MODEL="opencode/big-pickle"
RETRY_DELAYS=(5 5 10 30 60)
IDLE_LIMIT="${OPENCODE_IDLE_LIMIT:-900}"
QUOTA_PATTERN='free usage exceeded|freeusagelimiterror'
CONTINUE_PROMPT="Продолжай выполнение задания с того места, где остановился."
POLL_SECONDS=2
EXIT_QUOTA_EXHAUSTED=75
EXIT_STALLED=76
ATTEMPT_QUOTA=200
ATTEMPT_STALLED=201

usage() {
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

session=""
prompt=""
while getopts "f:s:h" opt; do
    case "$opt" in
        f) prompt="$(cat "$OPTARG")" || exit 2 ;;
        s) session="$OPTARG" ;;
        *) usage ;;
    esac
done
shift $((OPTIND - 1))
[ $# -gt 0 ] && prompt="$*"
[ -z "$prompt" ] && [ -z "$session" ] && usage
[ -z "$prompt" ] && prompt="$CONTINUE_PROMPT"

log_dir="$REPO_DIR/TestResults/opencode/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$log_dir"

newest_mtime() {
    stat -c %Y "$@" 2>/dev/null | sort -n | tail -1
}

capture_session_id() {
    grep -m1 -o '"sessionID":"ses_[A-Za-z0-9]*"' "$1" 2>/dev/null | head -n 1 | cut -d'"' -f4
}

quota_failure_seen() {
    local out="$1" err="$2"
    grep -qiE "$QUOTA_PATTERN" "$err" && return 0
    grep -qiE "$QUOTA_PATTERN" "$out" || return 1
    python3 -I -c '
import json, re, sys
pattern = re.compile(sys.argv[2], re.IGNORECASE)
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    try:
        event = json.loads(line)
    except ValueError:
        continue
    if not isinstance(event, dict):
        continue
    if event.get("type") == "error" or "error" in event:
        if pattern.search(json.dumps(event.get("error", event), ensure_ascii=False)):
            sys.exit(0)
sys.exit(1)
' "$out" "$QUOTA_PATTERN"
}

stop_attempt() {
    kill -TERM -- "-$1" 2>/dev/null || kill -TERM "$1" 2>/dev/null
    wait "$1" 2>/dev/null
}

run_attempt() {
    local out="$1.jsonl" err="$1.err" message="$2"
    local args=(run -m "$MODEL" --format json --auto --print-logs --log-level WARN --dir "$REPO_DIR")
    [ -n "$session" ] && args+=(-s "$session")
    : >"$out"
    : >"$err"
    setsid "$OPENCODE_BIN" "${args[@]}" "$message" >"$out" 2>"$err" </dev/null &
    local pid=$!
    while kill -0 "$pid" 2>/dev/null; do
        sleep "$POLL_SECONDS"
        [ -z "$session" ] && session="$(capture_session_id "$out")"
        if quota_failure_seen "$out" "$err"; then
            stop_attempt "$pid"
            return "$ATTEMPT_QUOTA"
        fi
        if [ $(( $(date +%s) - $(newest_mtime "$out" "$err") )) -ge "$IDLE_LIMIT" ]; then
            stop_attempt "$pid"
            return "$ATTEMPT_STALLED"
        fi
    done
    wait "$pid"
    local code=$?
    [ -z "$session" ] && session="$(capture_session_id "$out")"
    quota_failure_seen "$out" "$err" && return "$ATTEMPT_QUOTA"
    return "$code"
}

print_last_answer() {
    python3 -I -c '
import json, sys
last = ""
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    try:
        event = json.loads(line)
    except ValueError:
        continue
    if event.get("type") == "text":
        last = (event.get("part") or {}).get("text") or last
print(last)
' "$1"
}

attempt=0
message="$prompt"
while true; do
    attempt_log="$log_dir/attempt-$attempt"
    run_attempt "$attempt_log" "$message"
    code=$?
    case "$code" in
        "$ATTEMPT_QUOTA")
            if [ "$attempt" -ge "${#RETRY_DELAYS[@]}" ]; then
                echo "opencode: «Free usage exceeded» не прошёл после ${#RETRY_DELAYS[@]} перезапусков." >&2
                echo "Остановлено. Сообщи пользователю и сам не продолжай. Сессия: ${session:-не создана}, логи: $log_dir" >&2
                exit "$EXIT_QUOTA_EXHAUSTED"
            fi
            delay="${RETRY_DELAYS[$attempt]}"
            attempt=$((attempt + 1))
            echo "opencode: «Free usage exceeded», перезапуск ${attempt}/${#RETRY_DELAYS[@]} через ${delay} с (сессия ${session:-ещё не создана})" >&2
            sleep "$delay"
            [ -n "$session" ] && message="$CONTINUE_PROMPT"
            ;;
        "$ATTEMPT_STALLED")
            echo "opencode молчал ${IDLE_LIMIT} с и остановлен. Сессия: ${session:-не создана}, логи: $log_dir" >&2
            exit "$EXIT_STALLED"
            ;;
        *)
            echo "Сессия: ${session:-не создана}"
            echo "Логи: $log_dir"
            echo "--- последний ответ opencode ---"
            print_last_answer "$attempt_log.jsonl"
            exit "$code"
            ;;
    esac
done
