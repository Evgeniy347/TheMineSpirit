#!/usr/bin/env bash
# Выполняет задание в opencode (модель big-pickle) и следит за ним.
# Сбой провайдера модели («Free usage exceeded», «Rate limit exceeded», квота, 429, модель недоступна), сетевой сбой
# (нет соединения, «Endpoint is unavailable») и молчание модели (повтор отложен надолго или ответ не пришёл) лечит
# перезапуском той же сессии в новом процессе
# (новое HTTP-подключение без старых cookie) с паузами 5, 5, 10, 30, 60 с. После пятого перезапуска включает режим
# ожидания: сбои провайдера — это временная недоступность на минуты, поэтому раз в OPENCODE_PROBE_INTERVAL (120 с)
# скрипт шлёт короткую пробу той же модели (тайм-аут OPENCODE_PROBE_TIMEOUT, 60 с) из пустого каталога вне репозитория:
# проба не трогает дерево и не создаёт сессию в нём. Проба удалась — та же сессия продолжается, счётчик быстрых
# перезапусков сбрасывается. Общий предел потерянного на сбои времени (паузы и ожидание за весь запуск) —
# OPENCODE_WAIT_LIMIT (7200 с); исчерпан — остановка с кодом 75. OPENCODE_WAIT_LIMIT=0 — режим ожидания выключен.
#
# Использование:
#   scripts/opencode-task.sh "текст задания"
#   scripts/opencode-task.sh -f задание.md
#   scripts/opencode-task.sh -s ses_XXXX "замечания ревью"     (продолжить существующую сессию)
#
# Коды выхода: 0 — opencode завершил работу; 75 — сбой провайдера не прошёл ни после 5 перезапусков, ни за предел ожидания;
# 3 — новое задание в грязном дереве (там может работать другой агент); иначе — код самого opencode.
# Каждый сбой, проба ожидания и возврат провайдера пишутся строкой в TestResults/opencode/failures.log.
set -uo pipefail

REPO_DIR="${OPENCODE_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
OPENCODE_BIN="${OPENCODE_BIN:-$HOME/.opencode/bin/opencode}"
MODEL="${OPENCODE_MODEL:-opencode/big-pickle}"
read -ra RETRY_DELAYS <<<"${OPENCODE_RETRY_DELAYS:-5 5 10 30 60}"
PROBE_INTERVAL="${OPENCODE_PROBE_INTERVAL:-120}"
PROBE_TIMEOUT="${OPENCODE_PROBE_TIMEOUT:-60}"
WAIT_LIMIT="${OPENCODE_WAIT_LIMIT:-7200}"
PROBE_PROMPT="ответь OK"
SILENCE_LIMIT="${OPENCODE_IDLE_LIMIT:-300}"
PROVIDER_PATTERN='free usage exceeded|freeusagelimiterror|free.?tier|rate.?limit|usage.?limit|quota|too many requests|resource.?exhausted|(model|модель).{0,40}(unavailable|not available|недоступн)|[^0-9]429[^0-9]|upstream|idle.?timeout|[^0-9]50[234][^0-9]|server_error|stream interrupted|readtimeout'
NETWORK_PATTERN='cannot connect|unable to connect|econnrefused|econnreset|enotfound|etimedout|fetch failed|socket hang up|network is unreachable|endpoint.{0,40}unavailable'
FAILURE_PATTERN="$PROVIDER_PATTERN|$NETWORK_PATTERN"
CONTINUE_PROMPT="Продолжай выполнение задания с того места, где остановился. Если не помнишь деталей — сначала \
перечитай TestResults/task.md (задание) и TestResults/progress.md (твой прогресс)."
# opencode сжимает разговор, когда контекст модели заполнен (у моделей на 200 тыс. — каждые 20–60 шагов), и подробности
# задания теряются. Надзор ловит сжатие, останавливает ход и продолжает сессию с напоминанием перечитать файлы.
COMPACTED_PROMPT="Контекст сессии только что сжат — подробности задания и сделанного могли потеряться. Перечитай \
TestResults/task.md (задание) и TestResults/progress.md (твой прогресс), сверь с git log и продолжай с нужного места."
# Ловим конец сжатия (синтетическое «Continue…» после сводки), а не его начало (agent=compaction в логе): оборвать само
# сжатие — значит вернуть полный контекст и зациклиться на новом сжатии.
COMPACTION_PATTERN='"compaction_continue":true'
TASK_FILE="${OPENCODE_TASK_FILE:-$REPO_DIR/TestResults/task.md}"
UNFINISHED_PROMPT="Ты закончил ход на полуслове, не доделав задание и без итогового отчёта. Продолжай с того места, где \
остановился, и не заканчивай ход, пока не будет итогового отчёта: хэши коммитов, итог тестов, что не сделано."
# Модель иногда заканчивает ход фразой «Теперь посмотрю …:» — надзор продолжает ту же сессию, не больше UNFINISHED_LIMIT раз.
UNFINISHED_LIMIT="${OPENCODE_UNFINISHED_LIMIT:-3}"
# Ответ короче этого при грязном дереве — не итоговый отчёт (отчёт перечисляет коммиты, тесты, несделанное).
DIRTY_SHORT_ANSWER="${OPENCODE_DIRTY_SHORT_ANSWER:-600}"
EXECUTOR_RULES="Правила исполнителя (AGENTS.md → «Исполнитель»): ты работаешь без собеседника — вопросов не задавай \
и разрешения не спрашивай, решай сам по правилам проекта; тесты запускаешь сам, красный JUnit или GameTest не коммитишь; по ходу работы гоняешь только затронутое — ./gradlew test --tests <свои классы> и GameTest только после правки GameTest/адаптера, не больше одного прогона на пункт; полный ./gradlew test перед коммитом запускает хук — сам его не повторяй; полный GameTest — только в финале (3 прогона), машина общая с другими исполнителями; коммитишь сам, частями, по \
docs/GIT.md; к каждому fix:-коммиту с JUnit-тестом прикладываешь вывод scripts/prove-red.sh <коммит>; каждый новый тест \
доказываешь подлогом по существу (ломаешь поведение так, что код компилируется, и видишь красный тест) — «на родителе \
не компилируется» не доказательство; в проверке предела ожидание — число в самом тесте, а факт — из production (assertEquals(24, Limits.X)), \
сравнение литерала с литералом — не проверка; GameTest-сервер запускаешь только через bash scripts/gametest.sh (замок «один сервер на машину», память общая); в конце работы переносишь ветку на main (git rebase main) и только потом гоняешь финальные тесты — docs/GIT.md → «Финал ветки»; публикацию мода и перезапуск сервера не делаешь. Закончи работу целиком, в конце — отчёт: хэши \
коммитов, итог тестов, что не сделано и почему. Контекст модели ограничен и сжимается: задание лежит в TestResults/task.md; \
веди TestResults/progress.md — после каждого коммита и важного вывода коротко: что сделано (хэши), что дальше, принятые \
решения и найденные причины; после продолжения сессии сначала перечитай оба файла. Вывод сборок и тестов целиком не \
печатай: полный лог — в файл, на экран — grep/tail не больше 40 строк. Машина общая с другими исполнителями: процессы \nостанавливай только свои, по PID (запомни PID при запуске или найди по своему worktree в /proc/<pid>/cwd); pkill -f, killall \nи kill по общему шаблону (имя замка, gradle, java, GameTestServer) запрещены — они убивают чужие прогоны и чужих исполнителей."
POLL_SECONDS=2
# opencode запускается через setsid и переживает свой надзор: при остановке надзора (tmux kill-session, Ctrl+C)
# его надо гасить явно, иначе осиротевший процесс продолжит править дерево рядом с новым исполнителем.
current_pid=""
trap '[ -n "$current_pid" ] && kill "$current_pid" 2>/dev/null' EXIT
trap 'exit 143' TERM HUP INT
# На Windows python3 — заглушка магазина: там задают PYTHON=python.
PYTHON="${PYTHON:-python3}"
EXIT_PROVIDER_EXHAUSTED=75
EXIT_DIRTY_TREE=3
ATTEMPT_PROVIDER=200
ATTEMPT_SILENT=201
ATTEMPT_COMPACTED=202

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
failure_text=""

newest_mtime() {
    stat -c %Y "$@" 2>/dev/null | sort -n | tail -1
}

capture_session_id() {
    grep -m1 -o '"sessionID":"ses_[A-Za-z0-9]*"' "$1" 2>/dev/null | head -n 1 | cut -d'"' -f4
}

provider_failure_text() {
    local out="$1" err="$2" line
    line="$(grep -E 'level=(ERROR|WARN)' "$err" 2>/dev/null | grep -m1 -iE "$FAILURE_PATTERN")"
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
    local pid="$1"
    # Без pgrep (Git Bash на Windows) дочерние процессы не видны: молчание считается по одному таймеру —
    # на ПК для долгих сборок ставь OPENCODE_IDLE_LIMIT побольше.
    command -v pgrep >/dev/null || return 0
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
    local detach=()
    command -v setsid >/dev/null && detach=(setsid)
    "${detach[@]}" "$OPENCODE_BIN" "${args[@]}" "$message"$'\n\n'"$EXECUTOR_RULES" >"$out" 2>"$err" </dev/null &
    local pid=$!
    current_pid=$pid
    while kill -0 "$pid" 2>/dev/null; do
        sleep "$POLL_SECONDS"
        [ -z "$session" ] && session="$(capture_session_id "$out")"
        if failure_text="$(provider_failure_text "$out" "$err")"; then
            stop_attempt "$pid"
            return "$ATTEMPT_PROVIDER"
        fi
        if grep -qE "$COMPACTION_PATTERN" "$out" 2>/dev/null; then
            stop_attempt "$pid"
            return "$ATTEMPT_COMPACTED"
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

# Ответ оборван: кончается двоеточием или многоточием либо это короткое «Теперь …/Сейчас …/Let me …» без отчёта.
answer_unfinished() {
    "$PYTHON" -I -c '
import re, sys
text = sys.stdin.read().strip()
if not text:
    sys.exit(1)
if text.endswith((":", "…", "...")):
    sys.exit(0)
# Мост к веб-модели (deepseek-web) иногда отдаёт вызов инструмента текстом «<invoke name=...» — это не конец работы.
if re.search(r"<(invoke|parameter|function_calls)", text[-400:]):
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

# Одна проба провайдера: та же модель, пустой каталог вне репозитория, жёсткий тайм-аут. Успех — выход 0, текстовый
# ответ и ни следа сбоя провайдера. Результат (текст ошибки при неудаче) — в probe_text.
probe_text=""
probe_provider() {
    local out="$probe_dir/probe.jsonl" err="$probe_dir/probe.err" code
    : >"$out"
    : >"$err"
    (cd "$probe_dir" && timeout -k 5 "$PROBE_TIMEOUT" "$OPENCODE_BIN" run -m "$MODEL" --format json --print-logs         --log-level INFO "$PROBE_PROMPT" >"$out" 2>"$err" </dev/null)
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

# Режим ожидания после быстрых перезапусков. 0 — провайдер вернулся, можно продолжать; 1 — предел исчерпан.
# lost_seconds — всё время, потерянное на сбои за запуск (паузы перезапусков и ожидание): предел общий.
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
message="$prompt"
# Задание — в файл: после сжатия контекста модель перечитывает его, а не вспоминает. Продолжение с новым текстом
# (правки по ревью) дописывается к заданию.
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
        "$ATTEMPT_COMPACTED")
            record_failure "сжатие контекста" "продолжаю с напоминанием перечитать task.md и progress.md"
            echo "opencode: контекст сжат, продолжаю сессию ${session:-новую} с напоминанием перечитать задание" >&2
            attempt=0
            message="$COMPACTED_PROMPT"
            ;;
        *)
            # Короткий последний ответ при незакоммиченных правках — тоже обрыв: модель ушла посреди подлога или правки.
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
