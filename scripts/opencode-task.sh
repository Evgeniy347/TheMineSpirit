#!/usr/bin/env bash
# Выполняет задание в opencode (модель big-pickle) и следит за ним.
# Сбой провайдера модели («Free usage exceeded», «Rate limit exceeded», квота, 429, модель недоступна) и молчание
# модели (повтор отложен надолго или ответ не пришёл) лечит перезапуском той же сессии в новом процессе
# (новое HTTP-подключение без старых cookie) с паузами 5, 5, 10, 30, 60 с. После пятого перезапуска останавливается.
#
# Использование:
#   scripts/opencode-task.sh "текст задания"
#   scripts/opencode-task.sh -f задание.md
#   scripts/opencode-task.sh -s ses_XXXX "замечания ревью"     (продолжить существующую сессию)
#
# Коды выхода: 0 — opencode завершил работу; 75 — сбой провайдера не прошёл после 5 перезапусков;
# иначе — код самого opencode. Каждый сбой пишется строкой в TestResults/opencode/failures.log.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OPENCODE_BIN="${OPENCODE_BIN:-$HOME/.opencode/bin/opencode}"
MODEL="opencode/big-pickle"
RETRY_DELAYS=(5 5 10 30 60)
SILENCE_LIMIT="${OPENCODE_IDLE_LIMIT:-300}"
PROVIDER_PATTERN='free usage exceeded|freeusagelimiterror|free.?tier|rate.?limit|usage.?limit|quota|too many requests|resource.?exhausted|(model|модель).{0,40}(unavailable|not available|недоступн)|[^0-9]429[^0-9]'
CONTINUE_PROMPT="Продолжай выполнение задания с того места, где остановился."
EXECUTOR_RULES="Правила исполнителя (AGENTS.md → «Исполнитель»): ты работаешь без собеседника — вопросов не задавай \
и разрешения не спрашивай, решай сам по правилам проекта; тесты запускаешь сам; коммитишь сам, частями, по \
docs/GIT.md; публикацию мода и перезапуск сервера не делаешь. Закончи работу целиком, в конце — отчёт: хэши \
коммитов, итог тестов, что не сделано и почему."
POLL_SECONDS=2
EXIT_PROVIDER_EXHAUSTED=75
ATTEMPT_PROVIDER=200
ATTEMPT_SILENT=201

usage() {
    sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
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

log_root="${OPENCODE_LOG_ROOT:-$REPO_DIR/TestResults/opencode}"
log_dir="$log_root/$(date +%Y%m%d-%H%M%S)"
failures_log="$log_root/failures.log"
mkdir -p "$log_dir"
failure_text=""

newest_mtime() {
    stat -c %Y "$@" 2>/dev/null | sort -n | tail -1
}

capture_session_id() {
    grep -m1 -o '"sessionID":"ses_[A-Za-z0-9]*"' "$1" 2>/dev/null | head -n 1 | cut -d'"' -f4
}

provider_failure_text() {
    local out="$1" err="$2" line
    line="$(grep -E 'level=(ERROR|WARN)' "$err" 2>/dev/null | grep -m1 -iE "$PROVIDER_PATTERN")"
    if [ -n "$line" ]; then
        printf '%s' "$line"
        return 0
    fi
    grep -qiE "$PROVIDER_PATTERN" "$out" || return 1
    python3 -I -c '
import json, re, sys
pattern = re.compile(sys.argv[2], re.IGNORECASE)
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    try:
        event = json.loads(line)
    except ValueError:
        continue
    if isinstance(event, dict) and (event.get("type") == "error" or "error" in event):
        text = json.dumps(event.get("error", event), ensure_ascii=False)
        if pattern.search(text):
            print(text[:300], end="")
            sys.exit(0)
sys.exit(1)
' "$out" "$PROVIDER_PATTERN"
}

waiting_for_model() {
    local pid="$1"
    ! pgrep -a -P "$pid" 2>/dev/null | grep -qvE 'lsp|language.?server|jdtls|mcp'
}

record_failure() {
    local kind="$1" text="$2"
    printf '%s\tсессия=%s\tпопытка=%s\t%s\t%s\n' "$(date -Is)" "${session:-нет}" "$attempt" "$kind" \
        "$(printf '%s' "$text" | tr '\n\t' '  ' | cut -c1-300)" >>"$failures_log"
}

stop_attempt() {
    kill -TERM -- "-$1" 2>/dev/null || kill -TERM "$1" 2>/dev/null
    wait "$1" 2>/dev/null
}

run_attempt() {
    local out="$1.jsonl" err="$1.err" message="$2"
    local args=(run -m "$MODEL" --format json --auto --print-logs --log-level INFO --dir "$REPO_DIR")
    [ -n "$session" ] && args+=(-s "$session")
    : >"$out"
    : >"$err"
    setsid "$OPENCODE_BIN" "${args[@]}" "$message"$'\n\n'"$EXECUTOR_RULES" >"$out" 2>"$err" </dev/null &
    local pid=$!
    while kill -0 "$pid" 2>/dev/null; do
        sleep "$POLL_SECONDS"
        [ -z "$session" ] && session="$(capture_session_id "$out")"
        if failure_text="$(provider_failure_text "$out" "$err")"; then
            stop_attempt "$pid"
            return "$ATTEMPT_PROVIDER"
        fi
        if [ $(( $(date +%s) - $(newest_mtime "$out" "$err") )) -ge "$SILENCE_LIMIT" ] && waiting_for_model "$pid"; then
            failure_text="модель молчит ${SILENCE_LIMIT} с, команд не выполняется; последнее событие: $(tail -n 1 "$out" | cut -c1-200)"
            stop_attempt "$pid"
            return "$ATTEMPT_SILENT"
        fi
    done
    wait "$pid"
    local code=$?
    [ -z "$session" ] && session="$(capture_session_id "$out")"
    if failure_text="$(provider_failure_text "$out" "$err")"; then
        return "$ATTEMPT_PROVIDER"
    fi
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
        "$ATTEMPT_PROVIDER" | "$ATTEMPT_SILENT")
            [ "$code" = "$ATTEMPT_PROVIDER" ] && kind="сбой провайдера" || kind="молчание модели"
            record_failure "$kind" "$failure_text"
            if [ "$attempt" -ge "${#RETRY_DELAYS[@]}" ]; then
                echo "opencode: ${kind} не прошёл после ${#RETRY_DELAYS[@]} перезапусков: ${failure_text:0:200}" >&2
                echo "Остановлено. Сообщи пользователю и сам не продолжай. Сессия: ${session:-не создана}, логи: $log_dir" >&2
                exit "$EXIT_PROVIDER_EXHAUSTED"
            fi
            delay="${RETRY_DELAYS[$attempt]}"
            attempt=$((attempt + 1))
            echo "opencode: ${kind}, перезапуск ${attempt}/${#RETRY_DELAYS[@]} через ${delay} с (сессия ${session:-ещё не создана}): ${failure_text:0:150}" >&2
            sleep "$delay"
            [ -n "$session" ] && message="$CONTINUE_PROMPT"
            ;;
        *)
            echo "Сессия: ${session:-не создана}"
            echo "Логи: $log_dir"
            dirty="$(git -C "$REPO_DIR" status --short --untracked-files=no)"
            if [ -n "$dirty" ]; then
                echo "ВНИМАНИЕ: в дереве остались незакоммиченные изменения — закоммиченное состояние могло не проходить тесты:"
                echo "$dirty"
            fi
            echo "--- последний ответ opencode ---"
            print_last_answer "$attempt_log.jsonl"
            exit "$code"
            ;;
    esac
done
