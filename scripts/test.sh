#!/usr/bin/env bash
# Единый запуск тестов (docs/TESTING.md → «Два уровня»). Исполнители запускают тесты ТОЛЬКО через этот скрипт.
# Полный лог всегда в TestResults/logs/<дата-время>-<режим>.log; на экран — только итог.
# Режимы:
#   scripts/test.sh unit [--tests <фильтр gradle>]…
#   scripts/test.sh gametest [имя пачки или теста…]
# Пределы времени (переменные окружения, секунды):
#   THEMINESPIRIT_UNIT_WARN_SECONDS (по умолчанию 180), THEMINESPIRIT_UNIT_STOP_SECONDS (360)
#   THEMINESPIRIT_GAMETEST_WARN_SECONDS (300), THEMINESPIRIT_GAMETEST_STOP_SECONDS (600)
# Коды выхода: 0 — успех; 1 — падение тестов; 2 — ошибка запуска; 75 — чужой GameTest; 124 — стоп по пределу времени.
set -uo pipefail
cd "$(dirname "$0")/.."

GRADLEW="${THEMINESPIRIT_GRADLEW:-./gradlew}"
LOG_DIR="TestResults/logs"
mkdir -p "$LOG_DIR"

# Пределы времени (секунды)
UNIT_WARN="${THEMINESPIRIT_UNIT_WARN_SECONDS:-180}"
UNIT_STOP="${THEMINESPIRIT_UNIT_STOP_SECONDS:-360}"
GAMETEST_WARN="${THEMINESPIRIT_GAMETEST_WARN_SECONDS:-300}"
GAMETEST_STOP="${THEMINESPIRIT_GAMETEST_STOP_SECONDS:-600}"

# Замок GameTest (переиспользуем логику из scripts/gametest.sh)
GAMETEST_LOCK="${THEMINESPIRIT_GAMETEST_LOCK:-/tmp/theminespirit-gametest.lock}"
GAMETEST_WAIT="${THEMINESPIRIT_GAMETEST_WAIT:-1800}"
GAMETEST_LOCK_DIR="$GAMETEST_LOCK.d"

usage() {
    cat <<'EOF'
Использование:
  scripts/test.sh unit [--tests <фильтр gradle>]…
  scripts/test.sh gametest [имя пачки или теста…]

Режимы:
  unit      быстрые JUnit-тесты (без запуска игры)
  gametest  GameTest под замком (один сервер на машину)

Примеры:
  scripts/test.sh unit
  scripts/test.sh unit --tests "*Guard*"
  scripts/test.sh gametest
  scripts/test.sh gametest CombatGameTests
  scripts/test.sh gametest combat_directive
  scripts/test.sh gametest CombatGameTests.combat_target_spirit

Переменные окружения:
  THEMINESPIRIT_GRADLEW              команда запуска gradle (по умолчанию ./gradlew)
  THEMINESPIRIT_UNIT_WARN_SECONDS    предупреждение unit (по умолчанию 180)
  THEMINESPIRIT_UNIT_STOP_SECONDS    стоп unit (по умолчанию 360)
  THEMINESPIRIT_GAMETEST_WARN_SECONDS предупреждение gametest (по умолчанию 300)
  THEMINESPIRIT_GAMETEST_STOP_SECONDS стоп gametest (по умолчанию 600)
  THEMINESPIRIT_GAMETEST_LOCK        путь к замку (по умолчанию /tmp/theminespirit-gametest.lock)
  THEMINESPIRIT_GAMETEST_WAIT        ожидание замка, с (по умолчанию 1800)
  THEMINESPIRIT_GAMETEST_NOFLOCK=1   принудительно использовать mkdir-замок (для Git Bash на Windows)
EOF
    exit 2
}

# ── Парсинг аргументов ───────────────────────────────────────────────────────
MODE=""
UNIT_TESTS=()
GAMETEST_FILTERS=()

while [ $# -gt 0 ]; do
    case "$1" in
        unit|gametest)
            [ -n "$MODE" ] && { echo "Режим уже выбран: $MODE" >&2; usage; }
            MODE="$1"
            shift
            ;;
        --tests)
            [ "$MODE" = "unit" ] || { echo "--tests только для режима unit" >&2; usage; }
            shift
            [ $# -gt 0 ] || { echo "--tests требует аргумент" >&2; usage; }
            UNIT_TESTS+=("--tests" "$1")
            shift
            ;;
        -h|--help)
            usage
            ;;
        -*)
            echo "Неизвестный аргумент: $1" >&2
            usage
            ;;
        *)
            [ "$MODE" = "gametest" ] || { echo "Для режима unit используйте --tests <фильтр>" >&2; usage; }
            GAMETEST_FILTERS+=("$1")
            shift
            ;;
    esac
done

[ -n "$MODE" ] || usage

# ── Замок GameTest (flock на Linux, mkdir на Windows Git Bash) ───────────────
noflock() {
    [ -n "${THEMINESPIRIT_GAMETEST_NOFLOCK:-}" ] && return 0
    command -v flock >/dev/null 2>&1 && return 1
    return 0
}

release_dir_lock() {
    rm -rf "$GAMETEST_LOCK_DIR"
}

acquire_dir_lock() {
    local waited=0 holder stale
    while true; do
        if mkdir "$GAMETEST_LOCK_DIR" 2>/dev/null; then
            echo $$ >"$GAMETEST_LOCK_DIR/pid"
            trap release_dir_lock EXIT INT TERM
            return 0
        fi
        holder=$(cat "$GAMETEST_LOCK_DIR/pid" 2>/dev/null || echo "")
        if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
            stale="$GAMETEST_LOCK_DIR.stale.$$"
            if mv "$GAMETEST_LOCK_DIR" "$stale" 2>/dev/null; then
                echo "gametest: снимаю протухший замок $GAMETEST_LOCK_DIR (процесс $holder мёртв)." >&2
                rm -rf "$stale"
                continue
            fi
        fi
        [ "$waited" -ge "$GAMETEST_WAIT" ] && return 75
        sleep 2
        waited=$((waited + 2))
    done
}

busy_hint() {
    echo "gametest: ждали ${GAMETEST_WAIT} с, идёт чужой GameTest (блокировка $1)." >&2
    echo "gametest: GameTest — один на машину; дождись освобождения или освободи блокировку вручную." >&2
}

# ── Мониторинг времени ───────────────────────────────────────────────────────
WARN_SECONDS=0
STOP_SECONDS=0
MONITOR_PID=""
GRADLE_PID=""
START_TIME=0

start_monitor() {
    local mode="$1"
    if [ "$mode" = "unit" ]; then
        WARN_SECONDS="$UNIT_WARN"
        STOP_SECONDS="$UNIT_STOP"
    else
        WARN_SECONDS="$GAMETEST_WARN"
        STOP_SECONDS="$GAMETEST_STOP"
    fi

    export MONITOR_LOG_FILE="$LOG_FILE" MONITOR_START_TIME="$START_TIME" \
           MONITOR_WARN_SECONDS="$WARN_SECONDS" MONITOR_STOP_SECONDS="$STOP_SECONDS" \
           MONITOR_MODE="$mode"
    if command -v setsid >/dev/null 2>&1; then
        setsid bash -c '
            warned=0
            while true; do
                sleep 1
                elapsed=$(( $(date +%s) - MONITOR_START_TIME ))
                if [ "$warned" = 0 ] && [ "$elapsed" -ge "$MONITOR_WARN_SECONDS" ]; then
                    if [ "$MONITOR_MODE" = "gametest" ]; then
                        echo "ВНИМАНИЕ: полный GameTest идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
                    else
                        echo "ВНИМАНИЕ: unit идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
                    fi
                    warned=1
                fi
                if [ "$elapsed" -ge "$MONITOR_STOP_SECONDS" ]; then
                    echo "стоп по пределу $MONITOR_STOP_SECONDS с" >&2
                    touch "$MONITOR_LOG_FILE.stopped"
                    if [ -f "$MONITOR_LOG_FILE.gradlepid" ]; then
                        GRADLE_PID_FROM_FILE=$(cat "$MONITOR_LOG_FILE.gradlepid")
                        kill -TERM -- "-$GRADLE_PID_FROM_FILE" 2>/dev/null || kill -TERM "$GRADLE_PID_FROM_FILE" 2>/dev/null || true
                    fi
                    exit 124
                fi
            done
        ' >/dev/null 2>&1 &
    else
        (
            warned=0
            while true; do
                sleep 1
                elapsed=$(( $(date +%s) - MONITOR_START_TIME ))
                if [ "$warned" = 0 ] && [ "$elapsed" -ge "$MONITOR_WARN_SECONDS" ]; then
                    if [ "$MONITOR_MODE" = "gametest" ]; then
                        echo "ВНИМАНИЕ: полный GameTest идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
                    else
                        echo "ВНИМАНИЕ: unit идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
                    fi
                    warned=1
                fi
                if [ "$elapsed" -ge "$MONITOR_STOP_SECONDS" ]; then
                    echo "стоп по пределу $MONITOR_STOP_SECONDS с" >&2
                    touch "$MONITOR_LOG_FILE.stopped"
                    if [ -f "$MONITOR_LOG_FILE.gradlepid" ]; then
                        GRADLE_PID_FROM_FILE=$(cat "$MONITOR_LOG_FILE.gradlepid")
                        kill -TERM -- "-$GRADLE_PID_FROM_FILE" 2>/dev/null || kill -TERM "$GRADLE_PID_FROM_FILE" 2>/dev/null || true
                    fi
                    exit 124
                fi
            done
        ) &
    fi
    MONITOR_PID=$!
}

stop_monitor() {
    if [ -n "$MONITOR_PID" ]; then
        kill -TERM "$MONITOR_PID" 2>/dev/null || kill -TERM -- "-$MONITOR_PID" 2>/dev/null || true
        MONITOR_PID=""
    fi
    return 0
}

trap stop_monitor EXIT TERM INT

# ── Анализ результатов unit ──────────────────────────────────────────────────
analyze_unit() {
    local log="$1"
    local xml_dir="TestResults/unit/xml"

    local total=0 failures=0 errors=0
    local first_error=""
    local first_stack=""
    local failed_names=()

    for xml in "$xml_dir"/TEST-*.xml; do
        [ -f "$xml" ] || continue
        while IFS= read -r line; do
            case "$line" in
                *'<testsuite '*)
                    t=$(echo "$line" | grep -o 'tests="[0-9]*"' | head -1 | grep -o '[0-9]*')
                    f=$(echo "$line" | grep -o 'failures="[0-9]*"' | head -1 | grep -o '[0-9]*')
                    e=$(echo "$line" | grep -o 'errors="[0-9]*"' | head -1 | grep -o '[0-9]*')
                    total=$((total + ${t:-0}))
                    failures=$((failures + ${f:-0}))
                    errors=$((errors + ${e:-0}))
                    ;;
            esac
        done < "$xml"

        while IFS= read -r name; do
            [ -n "$name" ] && failed_names+=("$name")
        done < <(awk -F'"' '/<testcase / { name = $2; cls = $4 } /<(failure|error) / { print cls "." name }' "$xml" | sort -u)
    done

    if [ "$failures" -eq 0 ] && [ "$errors" -eq 0 ] && [ "$total" -gt 0 ]; then
        echo "OK unit: $total тестов, $ELAPSED с"
        return 0
    fi

    if [ "$total" -eq 0 ]; then
        echo "BUILD ERROR"
        grep -m3 -E "error:|FAILED|Exception" "$log" | head -3
        echo "полный лог: $log"
        return 1
    fi

    echo "FAIL unit: прошло $((total - failures - errors)), упало $((failures + errors))"
    if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
        first_error=$(grep -m1 -E "FAILED|Exception|Error:" "$log" | head -1)
        first_stack=$(grep -A 10 -m1 -E "FAILED|Exception|Error:" "$log" | head -12)
        [ -n "$first_error" ] && echo "$first_error"
        [ -n "$first_stack" ] && echo "$first_stack"
    fi
    if [ ${#failed_names[@]} -gt 0 ]; then
        echo "Упавшие тесты:"
        printf '%s\n' "${failed_names[@]}" | head -20
    fi
    echo "полный лог: $log"
    return 1
}

# ── Анализ результатов gametest ──────────────────────────────────────────────
analyze_gametest() {
    local log="$1"

    local failed_line=$(grep -E "FAILED|failed" "$log" | grep -i "test" | head -1)

    if [ -n "$failed_line" ]; then
        local failed_count=$(grep -c "FAILED" "$log" 2>/dev/null || echo 0)
        echo "FAIL gametest: упало $failed_count"
        echo "$failed_line"
        echo "полный лог: $log"
        return 1
    fi

    local passed_line=$(grep -E "All [0-9]+ required tests passed|required tests passed" "$log" | tail -1)

    if [ -n "$passed_line" ]; then
        local count=$(echo "$passed_line" | grep -o '[0-9]\+' | head -1)
        echo "OK gametest: $count тестов, $ELAPSED с"
        return 0
    fi

    echo "FAIL gametest: результат не распознан"
    echo "полный лог: $log"
    return 1
}

# ── Запуск ───────────────────────────────────────────────────────────────────
STAMP=$(date +%Y%m%d-%H%M%S-%N)
LOG_FILE="$LOG_DIR/${STAMP}-${MODE}.log"
rm -f "$LOG_FILE.stopped"
START_TIME=$(date +%s)
ELAPSED=0

echo "Режим: $MODE" >&2
echo "Лог: $LOG_FILE" >&2

if [ "$MODE" = "unit" ]; then
    rm -f TestResults/unit/xml/TEST-*.xml
    start_monitor "unit"

    set +e
    if command -v setsid >/dev/null 2>&1; then
        setsid "$GRADLEW" test "${UNIT_TESTS[@]}" >"$LOG_FILE" 2>&1 &
    else
        "$GRADLEW" test "${UNIT_TESTS[@]}" >"$LOG_FILE" 2>&1 &
    fi
    GRADLE_PID=$!
    echo "$GRADLE_PID" >"$LOG_FILE.gradlepid"
    wait "$GRADLE_PID"
    CODE=$?
    GRADLE_PID=""
    rm -f "$LOG_FILE.gradlepid"
    set -e

    ELAPSED=$(( $(date +%s) - START_TIME ))

    if [ -f "$LOG_FILE.stopped" ]; then
        echo "стоп по пределу ${UNIT_STOP} с"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi

    analyze_unit "$LOG_FILE"
    exit $?

else
    # GameTest
    if [ ${#GAMETEST_FILTERS[@]} -gt 0 ]; then
        FILTER_VALUE=$(IFS=,; echo "${GAMETEST_FILTERS[*]}")
        export THEMINESPIRIT_GAMETEST_FILTER="$FILTER_VALUE"
    fi

    if noflock; then
        acquire_dir_lock
        LOCK_CODE=$?
        if [ "$LOCK_CODE" -eq 75 ]; then
            busy_hint "$GAMETEST_LOCK_DIR"
            exit 75
        fi
    fi

    start_monitor "gametest"

    set +e
    if noflock; then
        if command -v setsid >/dev/null 2>&1; then
            setsid "$GRADLEW" runGameTestServer >"$LOG_FILE" 2>&1 &
        else
            "$GRADLEW" runGameTestServer >"$LOG_FILE" 2>&1 &
        fi
    else
        flock -w "$GAMETEST_WAIT" -E 75 "$GAMETEST_LOCK" bash -c "'$GRADLEW' runGameTestServer" >"$LOG_FILE" 2>&1 &
    fi
    GRADLE_PID=$!
    echo "$GRADLE_PID" >"$LOG_FILE.gradlepid"
    wait "$GRADLE_PID"
    CODE=$?
    GRADLE_PID=""
    rm -f "$LOG_FILE.gradlepid"
    set -e

    ELAPSED=$(( $(date +%s) - START_TIME ))

    if noflock; then
        release_dir_lock
    fi

    if [ -f "$LOG_FILE.stopped" ]; then
        echo "стоп по пределу ${GAMETEST_STOP} с"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi

    if [ "$CODE" -eq 75 ]; then
        busy_hint "$GAMETEST_LOCK"
        exit 75
    fi

    analyze_gametest "$LOG_FILE"
    exit $?
fi
