#!/usr/bin/env bash
set -uo pipefail

REPO_DIR="${OPENCODE_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
export LIVE_DEV_OWNER_PID="$$"
ORPHAN_SWEEP_WIN="${OPENCODE_ORPHAN_SWEEP_WIN:-$REPO_DIR/scripts/lead/orphan-sweep-win.sh}"
sweep_orphans() {
    [ -z "${OPENCODE_NO_SWEEP:-}" ] || return 0
    [ -f "$ORPHAN_SWEEP_WIN" ] || return 0
    bash "$ORPHAN_SWEEP_WIN" >/dev/null 2>&1 || true
}
OPENCODE_BIN="${OPENCODE_BIN:-$HOME/.opencode/bin/opencode}"
MODEL="${OPENCODE_MODEL:-opencode/big-pickle}"
read -ra RETRY_DELAYS <<<"${OPENCODE_RETRY_DELAYS:-5 5 10 30 60}"
PROBE_INTERVAL="${OPENCODE_PROBE_INTERVAL:-120}"
PROBE_TIMEOUT="${OPENCODE_PROBE_TIMEOUT:-60}"
WAIT_LIMIT="${OPENCODE_WAIT_LIMIT:-7200}"
PROBE_PROMPT="ответь OK"
if command -v pgrep >/dev/null || command -v powershell.exe >/dev/null; then SILENCE_LIMIT="${OPENCODE_IDLE_LIMIT:-300}"; else SILENCE_LIMIT="${OPENCODE_IDLE_LIMIT:-1200}"; fi
HEARTBEAT_FRESH="${OPENCODE_HEARTBEAT_FRESH:-120}"
PROVIDER_PATTERN='free usage exceeded|freeusagelimiterror|free.?tier|rate.?limit|usage.?limit|quota|too many requests|resource.?exhausted|(model|модель).{0,40}(unavailable|not available|недоступн)|[^0-9]429[^0-9]|upstream|idle.?timeout|[^0-9]50[234][^0-9]|server_error|stream interrupted|readtimeout'
NETWORK_PATTERN='cannot connect|unable to connect|econnrefused|econnreset|enotfound|etimedout|fetch failed|socket hang up|network is unreachable|endpoint.{0,40}unavailable'
FAILURE_PATTERN="$PROVIDER_PATTERN|$NETWORK_PATTERN"
CONTINUE_PROMPT="Продолжай выполнение задания с того места, где остановился. Если не помнишь деталей — сначала \
перечитай TestResults/task.md (задание) и TestResults/progress.md (твой прогресс)."
COMPACTED_PROMPT="Контекст сессии только что сжат — подробности задания и сделанного могли потеряться. Перечитай \
TestResults/task.md (задание) и TestResults/progress.md (твой прогресс), сверь с git log и продолжай с нужного места."
COMPACTION_PATTERN='"compaction_continue":true'
TASK_FILE="${OPENCODE_TASK_FILE:-$REPO_DIR/TestResults/task.md}"
UNFINISHED_PROMPT="Ты закончил ход на полуслове, не доделав задание и без итогового отчёта. Продолжай с того места, где \
остановился, и не заканчивай ход, пока не будет итогового отчёта: хэши коммитов, итог тестов, что не сделано."
UNFINISHED_LIMIT="${OPENCODE_UNFINISHED_LIMIT:-3}"
DIRTY_SHORT_ANSWER="${OPENCODE_DIRTY_SHORT_ANSWER:-600}"
LOOP_REPEAT="${OPENCODE_LOOP_REPEAT:-15}"
LOOP_LIMIT="${OPENCODE_LOOP_LIMIT:-3}"
LOOP_PROMPT="Ты ${LOOP_REPEAT} раз подряд выполнил одну и ту же команду — это зацикливание. Остановись: посмотри git status (правки в индексе видны в git diff --cached), перечитай TestResults/task.md и TestResults/progress.md, реши, какой следующий шаг задания, и сделай его другой командой."
EXECUTOR_RULES="Правила исполнителя (AGENTS.md → «Исполнитель»): работаешь без собеседника — вопросов не задавай, решай сам по правилам проекта. Работа БЛОКАМИ: законченный кусок правишь целиком, затем тесты, затем один коммит; тесты — когда считаешь, что всё должно стать зелёным, а не после каждой правки; коммиты не мельчить. Тесты — только scripts/test.sh (unit [--changed | --module …] [--tests …] / gametest [пачка…]), полный лог уже в TestResults/logs/; итерации — unit --changed или --module, полный unit и GameTest --full — один раз в финале (хук коммита гоняет --changed). Красное не коммитишь, хук не обходишь (--no-verify запрещён). Тесты крупные (docs/TESTING.md «Крупные сценарии»): несколько духов и полный сценарий в модели мира; новый баг — укрупни существующий сценарий механики, а не пиши тест-одиночку; сложное, что модель не проверит честно, — шагом в существующую сцену GameTest (мелочь не нести, решаешь сам); в отчёте раздел «Тесты»: что укрупнил, менял ли сцены GameTest. Новый тест доказываешь подлогом по существу (код компилируется, тест краснеет); сторожа и проверки не ослабляешь, таймауты не поднимаешь вместо исправления. Код смотри через codegraph (MCP codegraph_*), полученному коду верь и не перечитывай; правки — точечные (edit), не переписывание файла целиком; вывод команд — не больше 40 строк. Задание — TestResults/task.md; в конце каждого блока — запись в TestResults/progress.md (хэши, решения, что дальше); после продолжения сессии сначала перечитай оба файла. Процессы останавливай только свои, по PID; pkill -f/killall по шаблону запрещены. Поиск по всему диску запрещён: не запускай find/grep с корнем «/» (find / …), ищи в каталоге репозитория — такой процесс живёт часами после смерти родителя и грузит диск. Публикацию мода и перезапуск сервера не делаешь. В конце — отчёт: хэши коммитов, итог тестов, что не сделано и почему; раздел «Техдолг»: мелкое замеченное по ходу правь сразу сам, крупное — опиши (что, где, почему плохо, предложение, объём)."
POLL_SECONDS=2
current_pid=""
finish_run() {
    [ -n "$current_pid" ] && stop_attempt "$current_pid"
    if [ -f "$REPO_DIR/scripts/dev-server.sh" ]; then
        bash "$REPO_DIR/scripts/dev-server.sh" down --tree >/dev/null 2>&1 || true
    fi
    sweep_orphans
}
trap finish_run EXIT
trap 'exit 143' TERM HUP INT
PYTHON="${PYTHON:-$(python3 -c "" >/dev/null 2>&1 && echo python3 || echo python)}"
EXIT_PROVIDER_EXHAUSTED=75
EXIT_DIRTY_TREE=3
ATTEMPT_PROVIDER=200
ATTEMPT_SILENT=201
ATTEMPT_COMPACTED=202
ATTEMPT_LOOP=203
EXIT_LOOP=76

usage() {
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
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

if [ -z "$session" ] && [ -z "${OPENCODE_ALLOW_DIRTY:-}" ] && [ -n "$(git -C "$REPO_DIR" status --porcelain)" ]; then
    echo "Новое задание не запущено: в дереве незакоммиченные изменения — возможно, там работает другой агент" >&2
    echo "(например, агент владельца через opencode serve). Спроси владельца. Если это твоя работа и запуск" >&2
    echo "осознанный — OPENCODE_ALLOW_DIRTY=1. Продолжение своей сессии (-s) разрешено всегда." >&2
    git -C "$REPO_DIR" status --short >&2
    exit "$EXIT_DIRTY_TREE"
fi

log_root="${OPENCODE_LOG_ROOT:-$REPO_DIR/TestResults/opencode}"
log_dir="$log_root/$(date +%Y%m%d-%H%M%S)-$$"
failures_log="$log_root/failures.log"
mkdir -p "$log_dir"
sweep_orphans
failure_text=""

newest_mtime() {
    stat -c %Y "$@" 2>/dev/null | sort -n | tail -1
}

capture_session_id() {
    grep -m1 -o '"sessionID":"ses_[A-Za-z0-9]*"' "$1" 2>/dev/null | head -n 1 | cut -d'"' -f4
}

provider_failure_text() {
    local out="$1" err="$2" line
    line="$(grep -E 'level=(ERROR|WARN)' "$err" 2>/dev/null | grep -viE 'message="(mcp|retrying mcp)|server=[a-z0-9_-]+ error=' | grep -m1 -iE "$FAILURE_PATTERN")"
    if [ -n "$line" ]; then
        printf '%s' "$line"
        return 0
    fi
    grep -qiE "$FAILURE_PATTERN" "$out" || return 1
    "$PYTHON" -I -c '
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
' "$out" "$FAILURE_PATTERN"
}

waiting_for_model() {
    local pid="$1" hb="$REPO_DIR/TestResults/test-running"
    if [ -f "$hb" ] && [ $(( $(date +%s) - $(stat -c %Y "$hb" 2>/dev/null || echo 0) )) -lt "$HEARTBEAT_FRESH" ]; then
        return 1
    fi
    ! child_commands "$pid" | grep -qvE 'lsp|language.?server|jdtls|mcp'
}

child_commands() {
    local pid="$1" winpid
    [ -z "${OPENCODE_NO_PGREP:-}" ] || return 0
    if command -v pgrep >/dev/null; then
        pgrep -a -P "$pid" 2>/dev/null
    elif command -v powershell.exe >/dev/null && winpid="$(cat "/proc/$pid/winpid" 2>/dev/null)"; then
        ps -ef | awk -v p="$pid" '$3 == p'
        powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter 'ParentProcessId=$winpid' | ForEach-Object { \"\$(\$_.ProcessId) \$(\$_.CommandLine)\" }" 2>/dev/null | tr -d '\r'
    fi
}

record_failure() {
    local kind="$1" text="$2"
    printf '%s\tсессия=%s\tпопытка=%s\t%s\t%s\n' "$(date -Is)" "${session:-нет}" "$attempt" "$kind" \
        "$(printf '%s' "$text" | tr '\n\t' '  ' | cut -c1-300)" >>"$failures_log"
}

stop_attempt() {
    # lib: Git Bash не держит группу процессов для kill -TERM: таймаут обязан гасить всё дерево
    # (opencode → bash → gradle → java), иначе сироты продолжают прогон и шумят детектору.
    local pid="$1" winpid=""
    [ -r "/proc/$pid/winpid" ] && winpid="$(cat "/proc/$pid/winpid" 2>/dev/null)"
    if [ -n "$winpid" ] && command -v taskkill >/dev/null 2>&1; then
        taskkill //T //F //PID "$winpid" >/dev/null 2>&1 || true
    fi
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
}

check_bypass() {
    local out="$1" bypass_logged_var="$2"
    local logged="${!bypass_logged_var}"
    [ "$logged" = 1 ] && return 0
    if grep -qE '"name":"(bash|Bash)"' "$out" 2>/dev/null \
        && grep -oE '"command":"[^"]*gradlew[^"]*(test|runGameTestServer)[^"]*"' "$out" 2>/dev/null \
        | grep -qv "scripts/test.sh"; then
        record_failure "обход scripts/test.sh" "исполнитель вызвал напрямую gradlew test/runGameTestServer вместо scripts/test.sh"
        eval "$bypass_logged_var=1"
    fi
}

run_attempt() {
    local out="$1.jsonl" err="$1.err" message="$2"
    local args=(run -m "$MODEL" --format json --auto --standalone --print-logs --log-level info)
    [ -n "$session" ] && args+=(-s "$session")
    : >"$out"
    : >"$err"
    local detach=()
    [ "${OPENCODE_SETSID:-0}" = 1 ] && command -v setsid >/dev/null && detach=(setsid)
    (cd "$REPO_DIR" && exec "${detach[@]}" "$OPENCODE_BIN" "${args[@]}" "$message"$'\n\n'"$EXECUTOR_RULES") >"$out" 2>"$err" </dev/null &
    local pid=$!
    current_pid=$pid
    local bypass_logged=0
    while kill -0 "$pid" 2>/dev/null; do
        sleep "$POLL_SECONDS"
        [ -z "$session" ] && session="$(capture_session_id "$out")"
        check_bypass "$out" bypass_logged
        if failure_text="$(provider_failure_text "$out" "$err")"; then
            stop_attempt "$pid"
            return "$ATTEMPT_PROVIDER"
        fi
        if grep -qE "$COMPACTION_PATTERN" "$out" 2>/dev/null; then
            stop_attempt "$pid"
            return "$ATTEMPT_COMPACTED"
        fi
        if loop_command="$(command_loop "$out")"; then
            failure_text="одна команда ${LOOP_REPEAT} раз подряд: ${loop_command}"
            stop_attempt "$pid"
            return "$ATTEMPT_LOOP"
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
    check_bypass "$out" bypass_logged
    if failure_text="$(provider_failure_text "$out" "$err")"; then
        return "$ATTEMPT_PROVIDER"
    fi
    return "$code"
}

command_loop() {
    tail -n 300 "$1" 2>/dev/null | "$PYTHON" -I -c '
import json, sys
sys.stdin.reconfigure(encoding="utf-8", errors="replace")
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
need = int(sys.argv[1])
calls = []
for line in sys.stdin:
    if "\"tool_use\"" not in line:
        continue
    try:
        part = json.loads(line).get("part", {})
    except ValueError:
        continue
    calls.append(part.get("tool", "") + " " + json.dumps(part.get("state", {}).get("input", {}), sort_keys=True, ensure_ascii=False))
tail = calls[-need:]
if len(tail) == need and len(set(tail)) == 1:
    print(tail[0][:200])
    sys.exit(0)
sys.exit(1)
' "$LOOP_REPEAT"
}

answer_unfinished() {
    "$PYTHON" -I -c '
import re, sys
text = sys.stdin.read().strip()
if not text:
    sys.exit(1)
if text.endswith((":", "…", "...")):
    sys.exit(0)
if re.search(r"<(invoke|parameter|function_calls)|工具调用", text[-400:]):
    sys.exit(0)
starts = r"^(Теперь|Сейчас|Дальше|Продолжаю|Смотрю|Проверяю|Переписываю|Готовлю|Запускаю|Читаю|Let me|Now |Next)"
sys.exit(0 if len(text) < 400 and re.match(starts, text, re.I) else 1)
'
}

print_last_answer() {
    "$PYTHON" -I -c '
import json, sys
last = ""
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    try:
        event = json.loads(line)
    except ValueError:
        continue
    if event.get("type") == "text":
        last = (event.get("part") or {}).get("text") or last
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
print(last)
' "$1"
}

probe_text=""
probe_provider() {
    local out="$probe_dir/probe.jsonl" err="$probe_dir/probe.err" code
    : >"$out"
    : >"$err"
    (cd "$probe_dir" && timeout -k 5 "$PROBE_TIMEOUT" "$OPENCODE_BIN" run -m "$MODEL" --format json --standalone --print-logs         --log-level info "$PROBE_PROMPT" >"$out" 2>"$err" </dev/null)
    code=$?
    if probe_text="$(provider_failure_text "$out" "$err")"; then
        return 1
    fi
    if [ "$code" -ne 0 ]; then
        probe_text="проба завершилась кодом $code (124 — тайм-аут ${PROBE_TIMEOUT} с)"
        return 1
    fi
    grep -q '"type":"text"' "$out" || { probe_text="проба вернулась без текстового ответа"; return 1; }
    return 0
}

lost_seconds=0
wait_for_provider() {
    local started probes=0 elapsed remaining pause
    [ "$WAIT_LIMIT" -gt 0 ] || return 1
    started=$(date +%s)
    probe_dir="$(mktemp -d)"
    while true; do
        probes=$((probes + 1))
        if probe_provider; then
            lost_seconds=$((lost_seconds + $(date +%s) - started))
            record_failure "провайдер вернулся" "проба $probes удалась, ожидание $(( $(date +%s) - started )) с, потеряно за запуск ${lost_seconds} с"
            echo "opencode: провайдер вернулся после ${probes} проб(ы), продолжаю сессию ${session:-новую}" >&2
            rm -rf "$probe_dir"
            return 0
        fi
        elapsed=$(( lost_seconds + $(date +%s) - started ))
        if [ "$elapsed" -ge "$WAIT_LIMIT" ]; then
            record_failure "ожидание провайдера исчерпано" "проб $probes, потеряно ${elapsed} с из ${WAIT_LIMIT} с; последняя: $probe_text"
            rm -rf "$probe_dir"
            return 1
        fi
        record_failure "ожидание провайдера" "проба $probes не удалась, потеряно ${elapsed} с из ${WAIT_LIMIT} с: $probe_text"
        echo "opencode: ожидание провайдера, проба $probes не удалась (${elapsed}/${WAIT_LIMIT} с): ${probe_text:0:150}" >&2
        remaining=$((WAIT_LIMIT - elapsed))
        pause=$((PROBE_INTERVAL < remaining ? PROBE_INTERVAL : remaining))
        sleep "$pause"
    done
}

attempt=0
run_no=0
unfinished=0
loops=0
message="$prompt"
CODEGRAPH_BIN="${CODEGRAPH_BIN:-$(command -v codegraph || echo "$HOME/.npm-global/bin/codegraph")}"
codeseed_db_path() {
    case "$1" in
        */codegraph.db) printf '%s' "$1" ;;
        *) printf '%s/codegraph.db' "$1" ;;
    esac
}
find_codegraph_seed() {
    local repo="$1" root candidate head best="" best_mtime=0 match="" match_mtime=0 m
    if [ -n "${OPENCODE_CODEGRAPH_SEED:-}" ]; then
        candidate="$(codeseed_db_path "$OPENCODE_CODEGRAPH_SEED")"
        if [ -f "$candidate" ]; then
            printf '%s' "$candidate"
            return 0
        fi
    fi
    head="$(git -C "$repo" rev-parse HEAD 2>/dev/null || true)"
    while IFS= read -r root; do
        [ -n "$root" ] || continue
        case "$root" in
            */wt-owner|*/wt-owner/*|*/owner|*/owner/*) continue ;;
        esac
        candidate="$root/.codegraph/codegraph.db"
        [ -f "$candidate" ] || continue
        m=$(stat -c %Y "$candidate" 2>/dev/null) || continue
        if [ -n "$head" ] && [ -f "$root/.codegraph/indexed-commit" ] \
            && [ "$(cat "$root/.codegraph/indexed-commit" 2>/dev/null)" = "$head" ]; then
            if [ "$m" -gt "$match_mtime" ]; then
                match="$candidate"
                match_mtime="$m"
            fi
        fi
        if [ "$m" -gt "$best_mtime" ]; then
            best="$candidate"
            best_mtime="$m"
        fi
    done < <(git -C "$repo" worktree list --porcelain 2>/dev/null | sed -n 's/^worktree //p')
    if [ -n "$match" ]; then
        printf '%s' "$match"
    else
        printf '%s' "$best"
    fi
}
mark_codegraph_index() {
    local head
    head="$(git -C "$REPO_DIR" rev-parse HEAD 2>/dev/null)" || return 0
    [ -n "$head" ] || return 0
    printf '%s\n' "$head" >"$REPO_DIR/.codegraph/indexed-commit" 2>/dev/null || true
}
prepare_codegraph_index() {
    [ -z "${OPENCODE_NO_CODEGRAPH:-}" ] || return 0
    if ! [ -x "$CODEGRAPH_BIN" ] && ! command -v "$CODEGRAPH_BIN" >/dev/null 2>&1; then
        return 0
    fi
    local db="$REPO_DIR/.codegraph/codegraph.db" seed="" log="$log_dir/codegraph-init.log"
    if [ ! -f "$db" ]; then
        seed="$(find_codegraph_seed "$REPO_DIR")"
        if [ -n "$seed" ]; then
            mkdir -p "$REPO_DIR/.codegraph"
            if ! cp -f "$seed" "$db" 2>/dev/null; then
                rm -rf "$REPO_DIR/.codegraph"
                seed=""
            fi
        fi
    fi
    if [ ! -f "$db" ]; then
        if "$CODEGRAPH_BIN" init -i "$REPO_DIR" >"$log" 2>&1; then
            mark_codegraph_index
        else
            echo "opencode: индекс codegraph не построен, лог $log" >&2
        fi
        return 0
    fi
    if GIT_DIR="$REPO_DIR/.codegraph/.no-git" "$CODEGRAPH_BIN" sync "$REPO_DIR" >>"$log" 2>&1; then
        mark_codegraph_index
        return 0
    fi
    if [ -n "$seed" ]; then
        rm -rf "$REPO_DIR/.codegraph"
        if "$CODEGRAPH_BIN" init -i "$REPO_DIR" >>"$log" 2>&1; then
            mark_codegraph_index
        else
            echo "opencode: индекс codegraph не построен, лог $log" >&2
        fi
    elif "$CODEGRAPH_BIN" index "$REPO_DIR" >>"$log" 2>&1; then
        mark_codegraph_index
    else
        echo "opencode: codegraph sync не прошёл, оставляю прежний индекс, лог $log" >&2
    fi
}
prepare_codegraph_index
if [ "$prompt" != "$CONTINUE_PROMPT" ]; then
    mkdir -p "$(dirname "$TASK_FILE")"
    if [ -n "$session" ] && [ -s "$TASK_FILE" ]; then
        printf '\n\n--- Дополнение %s ---\n%s\n' "$(date '+%d.%m %H:%M')" "$prompt" >>"$TASK_FILE"
    else
        printf '%s\n' "$prompt" >"$TASK_FILE"
    fi
fi
while true; do
    attempt_log="$log_dir/attempt-$run_no"
    run_no=$((run_no + 1))
    run_attempt "$attempt_log" "$message"
    code=$?
    case "$code" in
        "$ATTEMPT_PROVIDER" | "$ATTEMPT_SILENT")
            [ "$code" = "$ATTEMPT_PROVIDER" ] && kind="сбой провайдера" || kind="молчание модели"
            record_failure "$kind" "$failure_text"
            if [ "$attempt" -ge "${#RETRY_DELAYS[@]}" ]; then
                echo "opencode: ${kind} не прошёл после ${#RETRY_DELAYS[@]} перезапусков, жду провайдера: ${failure_text:0:200}" >&2
                if wait_for_provider; then
                    attempt=0
                    [ -n "$session" ] && message="$CONTINUE_PROMPT"
                    continue
                fi
                echo "opencode: провайдер не вернулся за предел ожидания (${WAIT_LIMIT} с): ${failure_text:0:200}" >&2
                echo "Остановлено. Сообщи пользователю и сам не продолжай. Сессия: ${session:-не создана}, логи: $log_dir" >&2
                exit "$EXIT_PROVIDER_EXHAUSTED"
            fi
            delay="${RETRY_DELAYS[$attempt]}"
            attempt=$((attempt + 1))
            echo "opencode: ${kind}, перезапуск ${attempt}/${#RETRY_DELAYS[@]} через ${delay} с (сессия ${session:-ещё не создана}): ${failure_text:0:150}" >&2
            sleep "$delay"
            lost_seconds=$((lost_seconds + delay))
            [ -n "$session" ] && message="$CONTINUE_PROMPT"
            ;;
        "$ATTEMPT_LOOP")
            record_failure "зацикливание" "$failure_text"
            loops=$((loops + 1))
            if [ "$loops" -gt "$LOOP_LIMIT" ]; then
                echo "opencode: зацикливание повторилось ${loops} раз: ${failure_text:0:200}" >&2
                echo "Остановлено. Сообщи пользователю и сам не продолжай. Сессия: ${session:-не создана}, логи: $log_dir" >&2
                exit "$EXIT_LOOP"
            fi
            echo "opencode: зацикливание (${loops}/${LOOP_LIMIT}), продолжаю сессию ${session:-новую}: ${failure_text:0:150}" >&2
            attempt=0
            message="$LOOP_PROMPT"
            ;;
        "$ATTEMPT_COMPACTED")
            record_failure "сжатие контекста" "продолжаю с напоминанием перечитать task.md и progress.md"
            echo "opencode: контекст сжат, продолжаю сессию ${session:-новую} с напоминанием перечитать задание" >&2
            attempt=0
            message="$COMPACTED_PROMPT"
            ;;
        *)
            last_answer="$(print_last_answer "$attempt_log.jsonl")"
            dirty_now="$(git -C "$REPO_DIR" status --short --untracked-files=no)"
            if [ "$code" = 0 ] && [ -n "$session" ] && [ "$unfinished" -lt "$UNFINISHED_LIMIT" ] \
                && { printf '%s' "$last_answer" | answer_unfinished \
                     || { [ -n "$dirty_now" ] && [ "${#last_answer}" -lt "$DIRTY_SHORT_ANSWER" ]; }; }; then
                unfinished=$((unfinished + 1))
                record_failure "ответ оборван" "$(print_last_answer "$attempt_log.jsonl" | head -c 200)"
                echo "opencode: ответ оборван на полуслове, продолжаю сессию ${session} (${unfinished}/${UNFINISHED_LIMIT})" >&2
                attempt=0
                message="$UNFINISHED_PROMPT"
                continue
            fi
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
