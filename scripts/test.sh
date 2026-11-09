#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."

PROJECT_ROOT="$(pwd -P)"
. scripts/module-paths.sh

GAMETEST_WORKTREE="$PROJECT_ROOT"
GAMETEST_RUN_DIR="$GAMETEST_WORKTREE/${THEMINESPIRIT_GAMETEST_RUN_DIR:-build/gametest-run}"

GRADLEW="${THEMINESPIRIT_GRADLEW:-./gradlew}"
LOG_DIR="TestResults/logs"
mkdir -p "$LOG_DIR"

UNIT_WARN="${THEMINESPIRIT_UNIT_WARN_SECONDS:-600}"
UNIT_STOP="${THEMINESPIRIT_UNIT_STOP_SECONDS:-900}"
UNIT_CPU_WARN="${THEMINESPIRIT_UNIT_CPU_WARN_SECONDS:-90}"
UNIT_CPU_STOP="${THEMINESPIRIT_UNIT_CPU_STOP_SECONDS:-200}"
GAMETEST_WARN="${THEMINESPIRIT_GAMETEST_WARN_SECONDS:-120}"
GAMETEST_STOP="${THEMINESPIRIT_GAMETEST_STOP_SECONDS:-200}"
GAMETEST_FULL_WARN="${THEMINESPIRIT_GAMETEST_FULL_WARN_SECONDS:-180}"
GAMETEST_FULL_STOP="${THEMINESPIRIT_GAMETEST_FULL_STOP_SECONDS:-300}"
GAMETEST_PREPARE_WARN="${THEMINESPIRIT_GAMETEST_PREPARE_WARN_SECONDS:-300}"
GAMETEST_PREPARE_STOP="${THEMINESPIRIT_GAMETEST_PREPARE_STOP_SECONDS:-600}"

case "$OSTYPE" in
    msys*|cygwin*|win*)
        GAMETEST_LOCK="${THEMINESPIRIT_GAMETEST_LOCK:-${ALLUSERSPROFILE:-C:/ProgramData}/theminespirit/theminespirit-gametest.lock}"
        ;;
    *)
        GAMETEST_LOCK="${THEMINESPIRIT_GAMETEST_LOCK:-/tmp/theminespirit-gametest.lock}"
        ;;
esac
GAMETEST_WAIT="${THEMINESPIRIT_GAMETEST_WAIT:-1800}"
GAMETEST_LOCK_DIR="$GAMETEST_LOCK.d"
GAMETEST_TOKEN=""
GAMETEST_BEAT_INTERVAL="${THEMINESPIRIT_GAMETEST_BEAT_SECONDS:-10}"
GAMETEST_STALE="${THEMINESPIRIT_GAMETEST_STALE_SECONDS:-120}"
GAMETEST_LEGACY_STALE="${THEMINESPIRIT_GAMETEST_LEGACY_STALE_SECONDS:-900}"
LOCK_BEAT_PID=""
DIR_LOCK_HELD=0
MAIN_PID=$$
PARENT_WATCH_PID=""
PAUSE_FIFO=""
PAUSE_FD_OPEN=0

usage() {
    cat <<'EOF'
Использование:
  scripts/test.sh unit [--tests <фильтр gradle>]… [--changed | --module <модуль>]… [--fast-analyzers]
  scripts/test.sh gametest [--full] [--seed N] [имя пачки или теста…]

Режимы:
  unit      быстрые JUnit-тесты (без запуска игры)
  gametest  GameTest под замком: общий набор без ручных демо-аудитов (--full — он же, с пределом полного)

Отбор для unit:
  --changed         только модули изменённых путей и те, кто от них зависит (scripts/changed-modules.sh)
  --module <м>      только модуль (:board, mech:farming, root); повторяемый
  --tests <фильтр>  тесты под фильтр; модули ищутся по исходникам тестов, чужие не собираются

Обычный цикл проверяет только формат Spotless (Error Prone — в компиляторе). SpotBugs, PMD и Checkstyle
запускаются ночным прогоном по main и приёмкой с -Ptheminespirit.analyzers=on; --fast-analyzers оставлен
для совместимости и ничего не меняет.

Мир GameTest всегда засеян: по умолчанию сид 0, поэтому место сетки тестов и случайность мира повторяются
от прогона к прогону; --seed N выбирает другой повторяемый мир (и пересоздаёт его), сид пишется в лог
строкой [scene].

Шарды GameTest: на ПК 3 сервера, на Linux 1; THEMINESPIRIT_GAMETEST_SHARDS перекрывает.

Примеры:
  scripts/test.sh unit
  scripts/test.sh unit --changed
  scripts/test.sh unit --module board
  scripts/test.sh unit --module mech:farming --tests "*Farming*"
  scripts/test.sh unit --tests "*GuardTests*"
  scripts/test.sh unit --fast-analyzers
  scripts/test.sh gametest
  scripts/test.sh gametest --full
  scripts/test.sh gametest demo_audit_familyhouse
  scripts/test.sh gametest TacticsScenesGameTests
  scripts/test.sh gametest scene_tactics_arena
  scripts/test.sh gametest --seed 42 first_stage
  scripts/test.sh gametest CraftScenesGameTests.craftScene_ArenaSteps_AllPass

Переменные окружения:
  THEMINESPIRIT_GRADLEW              команда запуска gradle (по умолчанию ./gradlew)
  THEMINESPIRIT_UNIT_WARN_SECONDS    предупреждение unit по часам (по умолчанию 600)
  THEMINESPIRIT_UNIT_STOP_SECONDS    стоп unit по часам — защита от зависания (по умолчанию 900)
  THEMINESPIRIT_UNIT_CPU_WARN_SECONDS  предупреждение: время процессора всех тестов (по умолчанию 90)
  THEMINESPIRIT_UNIT_CPU_STOP_SECONDS  предел: время процессора всех тестов, красный (по умолчанию 200)
  THEMINESPIRIT_GAMETEST_WARN_SECONDS предупреждение быстрого gametest (по умолчанию 120)
  THEMINESPIRIT_GAMETEST_STOP_SECONDS стоп быстрого gametest (по умолчанию 200)
  THEMINESPIRIT_GAMETEST_FULL_WARN_SECONDS  предупреждение --full (180)
  THEMINESPIRIT_GAMETEST_FULL_STOP_SECONDS  стоп --full (300, от старта сцен)
  THEMINESPIRIT_GAMETEST_PREPARE_WARN_SECONDS  предупреждение подготовки сборки и загрузки мира (300)
  THEMINESPIRIT_GAMETEST_PREPARE_STOP_SECONDS  стоп подготовки сборки и загрузки мира (600, отдельный бюджет)
  THEMINESPIRIT_GAMETEST_SHARDS      число параллельных серверов GameTest (ПК 3, Linux 1)
  THEMINESPIRIT_GAMETEST_LOCK        путь к замку (Windows: C:\ProgramData\theminespirit\theminespirit-gametest.lock, Linux: /tmp/theminespirit-gametest.lock)
  THEMINESPIRIT_GAMETEST_WAIT        ожидание замка, с (по умолчанию 1800)
  THEMINESPIRIT_GAMETEST_STALE_SECONDS  возраст пульса, после которого mkdir-замок протух (120)
  THEMINESPIRIT_GAMETEST_BEAT_SECONDS   период обновления пульса владельцем (10)
  THEMINESPIRIT_GAMETEST_LEGACY_STALE_SECONDS  порог для маркера старого test.sh без пульса (900)
  THEMINESPIRIT_GAMETEST_NOFLOCK=1   принудительно использовать mkdir-замок (для Git Bash на Windows)
  THEMINESPIRIT_PYTHON               python для разбора отчётов unit (по умолчанию PYTHON, python3, python); none — только awk
  THEMINESPIRIT_TEST_HEARTBEAT       файл пульса для надзора (по умолчанию TestResults/test-running)
  THEMINESPIRIT_TEST_DETACHED=1      не гасить прогон при смерти родителя (осознанно отвязанный запуск)
EOF
    exit 2
}

MODE=""
UNIT_TESTS=()
UNIT_TEST_PATTERNS=()
GAMETEST_FILTERS=()
GAMETEST_FULL=0
GAMETEST_SEED=""
UNIT_CHANGED=0
UNIT_MODULES=()
UNIT_SUBSET=0
UNIT_NO_MATCH=0

while [ $# -gt 0 ]; do
    case "$1" in
        unit|gametest)
            [ -n "$MODE" ] && { echo "Режим уже выбран: $MODE" >&2; usage; }
            MODE="$1"
            shift
            ;;
        --full)
            [ "$MODE" = "gametest" ] || { echo "--full только для режима gametest" >&2; usage; }
            GAMETEST_FULL=1
            shift
            ;;
        --seed)
            [ "$MODE" = "gametest" ] || { echo "--seed только для режима gametest" >&2; usage; }
            shift
            case "${1:-}" in
                ''|*[!0-9-]*|?*-*) echo "--seed требует целое число" >&2; usage ;;
            esac
            GAMETEST_SEED="$1"
            shift
            ;;
        --fast-analyzers)
            [ "$MODE" = "unit" ] || { echo "--fast-analyzers только для режима unit" >&2; usage; }
            # lib: флаг оставлен для старых вызовов; unit и так гоняет только формат Spotless.
            shift
            ;;
        --changed)
            [ "$MODE" = "unit" ] || { echo "--changed только для режима unit" >&2; usage; }
            UNIT_CHANGED=1
            shift
            ;;
        --module)
            [ "$MODE" = "unit" ] || { echo "--module только для режима unit" >&2; usage; }
            shift
            [ $# -gt 0 ] || { echo "--module требует аргумент" >&2; usage; }
            UNIT_MODULES+=("$1")
            shift
            ;;
        --tests)
            [ "$MODE" = "unit" ] || { echo "--tests только для режима unit" >&2; usage; }
            shift
            [ $# -gt 0 ] || { echo "--tests требует аргумент" >&2; usage; }
            UNIT_TESTS+=("--tests" "$1")
            UNIT_TEST_PATTERNS+=("$1")
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
[ "$UNIT_CHANGED" = 1 ] && [ ${#UNIT_MODULES[@]} -gt 0 ] \
    && { echo "--changed и --module вместе не выбираются" >&2; usage; }

all_unit_modules() {
    sed -nE "s/^[[:space:]]*include[[:space:]]+'(:[^']+)'.*/\1/p" "$MOD_SETTINGS_FILE"
}

normalize_module_id() {
    local module="$1"
    case "$module" in
        root|:root|:) printf ':root'; return 0 ;;
    esac
    module="${module#:}"
    module="${module//\//:}"
    printf ':%s' "$module"
}

module_is_valid() {
    local wanted="$1" known
    [ "$wanted" = ":root" ] && return 0
    while IFS= read -r known; do
        [ "$known" = "$wanted" ] && return 0
    done < <(all_unit_modules)
    return 1
}

test_source_roots() {
    local module dir
    printf 'src/test/java\n'
    while IFS= read -r module; do
        [ -n "$module" ] || continue
        dir="${module#:}"
        dir="${dir//:/\/}"
        [ -d "$dir/src/test/java" ] && printf '%s/src/test/java\n' "$dir"
    done < <(all_unit_modules)
}

# lib: фильтр --tests не называет модуль, поэтому ищем совпадение по именам тестовых классов.
# Иначе пришлось бы собирать все модули, как раньше.
resolve_test_modules() {
    local pattern root file relative fqcn base matched module
    local -A found=()
    while IFS= read -r pattern; do
        while IFS= read -r root; do
            [ -d "$root" ] || continue
            while IFS= read -r file; do
                relative="${file#"$root"/}"
                fqcn="${relative%.java}"
                fqcn="${fqcn//\//.}"
                base="${fqcn##*.}"
                matched=0
                case "$fqcn" in $pattern) matched=1 ;; esac
                case "$base" in $pattern) matched=1 ;; esac
                case "$pattern" in "$fqcn".*|"$base".*) matched=1 ;; esac
                [ "$matched" = 1 ] && found["$root"]=1
            done < <(find "$root" -type f -name '*.java')
        done < <(test_source_roots)
    done < <(printf '%s\n' "${UNIT_TEST_PATTERNS[@]}")
    UNIT_MODULES=()
    for root in "${!found[@]}"; do
        if [ "$root" = "src/test/java" ]; then
            UNIT_MODULES+=(":root")
        else
            module="${root%/src/test/java}"
            UNIT_MODULES+=(":${module//\//:}")
        fi
    done
    [ ${#UNIT_MODULES[@]} -gt 1 ] && mapfile -t UNIT_MODULES < <(printf '%s\n' "${UNIT_MODULES[@]}" | sort)
}

if [ "$MODE" = "unit" ]; then
    if [ "$UNIT_CHANGED" = 1 ]; then
        mapfile -t UNIT_MODULES < <(bash scripts/changed-modules.sh)
        UNIT_SUBSET=1
    elif [ ${#UNIT_MODULES[@]} -gt 0 ]; then
        normalized=()
        for unit_module in "${UNIT_MODULES[@]}"; do
            unit_module="$(normalize_module_id "$unit_module")"
            module_is_valid "$unit_module" \
                || { echo "test.sh: нет модуля ${unit_module#:} (список — settings.gradle)" >&2; exit 2; }
            normalized+=("$unit_module")
        done
        UNIT_MODULES=("${normalized[@]}")
        UNIT_SUBSET=1
    elif [ ${#UNIT_TEST_PATTERNS[@]} -gt 0 ]; then
        resolve_test_modules
        UNIT_SUBSET=1
        [ ${#UNIT_MODULES[@]} -eq 0 ] && UNIT_NO_MATCH=1
    fi
    if [ "$UNIT_NO_MATCH" = 1 ]; then
        echo "test.sh: фильтр не совпал ни с одним тестовым классом: ${UNIT_TEST_PATTERNS[*]}" >&2
        exit 1
    fi
fi

GAMETEST_SHARDS="${THEMINESPIRIT_GAMETEST_SHARDS:-}"
if [ -z "$GAMETEST_SHARDS" ]; then
    case "$OSTYPE" in
        msys*|cygwin*|win*) GAMETEST_SHARDS=3 ;;
        *) GAMETEST_SHARDS=1 ;;
    esac
fi
case "$GAMETEST_SHARDS" in ''|*[!0-9]*) GAMETEST_SHARDS=1 ;; esac
[ "$GAMETEST_SHARDS" -ge 1 ] || GAMETEST_SHARDS=1
[ ${#GAMETEST_FILTERS[@]} -gt 0 ] && GAMETEST_SHARDS=1

noflock() {
    [ -n "${THEMINESPIRIT_GAMETEST_NOFLOCK:-}" ] && return 0
    command -v flock >/dev/null 2>&1 && return 1
    return 0
}

file_mtime() {
    stat -c %Y "$1" 2>/dev/null || echo 0
}

init_pause_tick() {
    local dir="${TMPDIR:-/tmp}"
    PAUSE_FIFO="$dir/theminespirit-test-pause.$$"
    if ! mkfifo "$PAUSE_FIFO" 2>/dev/null; then
        PAUSE_FIFO="$PROJECT_ROOT/TestResults/.test-pause.$$"
        mkdir -p "$PROJECT_ROOT/TestResults" 2>/dev/null || true
        mkfifo "$PAUSE_FIFO" 2>/dev/null || { PAUSE_FIFO=""; return 0; }
    fi
    if exec 9<>"$PAUSE_FIFO"; then
        PAUSE_FD_OPEN=1
        export PAUSE_FD_OPEN
    fi
    rm -f "$PAUSE_FIFO"
    PAUSE_FIFO=""
    return 0
}

pause_tick() {
    local seconds="$1" rc=1
    if [ "$PAUSE_FD_OPEN" = 1 ]; then
        read -r -t "$seconds" -u 9 _ 2>/dev/null
        rc=$?
        [ "$rc" -ne 2 ] && return 0
        PAUSE_FD_OPEN=0
    fi
    sleep "$seconds" 2>/dev/null
    return 0
}

close_pause_tick() {
    if [ "$PAUSE_FD_OPEN" = 1 ]; then
        exec 9<&-
        PAUSE_FD_OPEN=0
    fi
    [ -n "$PAUSE_FIFO" ] && rm -f "$PAUSE_FIFO"
    return 0
}


pid_is_live() {
    local pid="$1" rest
    if [ -r "/proc/$pid/stat" ]; then
        rest=""
        read -r rest <"/proc/$pid/stat" 2>/dev/null
        [ -n "$rest" ] || return 1
        rest="${rest##*) }"
        case "$rest" in
            Z*) return 1 ;;
        esac
        return 0
    fi
    kill -0 "$pid" 2>/dev/null
}

gametest_server_pids() {
    local dir="$1" d pid exe cwd
    case "$OSTYPE" in
        msys*|cygwin*|win*)
            local win_worktree win_dir
            win_worktree=$(cygpath -w "$GAMETEST_WORKTREE" 2>/dev/null || printf '%s' "$GAMETEST_WORKTREE")
            win_dir=$(cygpath -w "$dir" 2>/dev/null || printf '%s' "$dir")
            powershell -NoProfile -Command \
                "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*$win_worktree*' -or \$_.CommandLine -like '*$win_dir*' } | ForEach-Object { \$_.ProcessId }" \
                2>/dev/null | tr -d '\r'
            ;;
        *)
            for d in /proc/[0-9]*; do
                [ -r "$d/cmdline" ] || continue
                exe=$(readlink "$d/exe" 2>/dev/null) || continue
                case "$exe" in
                    */java|*/java.exe) ;;
                    *) continue ;;
                esac
                cwd=$(readlink "$d/cwd" 2>/dev/null) || continue
                pid="${d#/proc/}"
                case "$cwd" in
                    "$dir"|"$dir"/*) pid_is_live "$pid" && printf '%s\n' "$pid" ;;
                esac
            done
            ;;
    esac
    return 0
}

kill_gametest_server() {
    [ "${MODE:-}" = "gametest" ] || return 0
    local pids pid alive waited
    pids=$(gametest_server_pids "$GAMETEST_RUN_DIR")
    [ -n "$pids" ] || return 0
    for pid in $pids; do
        kill -TERM "$pid" 2>/dev/null || true
    done
    waited=0
    while [ "$waited" -lt 10 ]; do
        alive=""
        for pid in $pids; do
            pid_is_live "$pid" && alive="$alive $pid"
        done
        [ -n "$alive" ] || return 0
        pids="$alive"
        pause_tick 1
        waited=$((waited + 1))
    done
    for pid in $pids; do
        kill -KILL "$pid" 2>/dev/null || true
    done
    return 0
}


now_s() {
    printf -v "$1" '%(%s)T' -1
}

own_tree_pids() {
    ps -ef 2>/dev/null | awk -v root="$1" '
        $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ { kids[$3] = kids[$3] " " $2; alive[$2] = 1 }
        END {
            if (!(root in alive)) exit
            queue[1] = root; head = 1; tail = 1; queued[root] = 1
            while (head <= tail) {
                p = queue[head++]; print p
                n = split(kids[p], list, " ")
                for (k = 1; k <= n; k++) if (!(list[k] in queued)) { queued[list[k]] = 1; queue[++tail] = list[k] }
            }
        }'
}

kill_own_tree() {
    local root="$1" grace="${2:-5}" pids pid alive now deadline fresh child
    [ -n "$root" ] || return 0
    pids=$(own_tree_pids "$root")
    if [ -z "$pids" ]; then
        pid_is_live "$root" || return 0
        pids="$root"
    fi
    [ "${3:-}" = group ] && kill -TERM -- "-$root" 2>/dev/null
    for pid in $pids; do
        kill -TERM "$pid" 2>/dev/null
    done
    now_s now
    deadline=$((now + grace))
    while :; do
        fresh=""
        for pid in $pids; do
            for child in $(own_tree_pids "$pid"); do
                case " $pids " in *" $child "*) ;; *) fresh="$fresh $child" ;; esac
            done
        done
        [ -n "$fresh" ] && pids="$pids$fresh"
        alive=""
        for pid in $pids; do
            pid_is_live "$pid" && alive="$alive $pid"
        done
        [ -n "$alive" ] || return 0
        pids="$alive"
        now_s now
        [ "$now" -lt "$deadline" ] || break
        pause_tick 0.2
    done
    for pid in $pids; do
        kill -KILL "$pid" 2>/dev/null
    done
    return 0
}

stop_own_loop() {
    local pid="$1" grace="${2:-2}" now deadline
    [ -n "$pid" ] || return 0
    kill -TERM "$pid" 2>/dev/null || return 0
    now_s now
    deadline=$((now + grace))
    while pid_is_live "$pid"; do
        now_s now
        if [ "$now" -ge "$deadline" ]; then
            kill_own_tree "$pid" 0
            return 0
        fi
        pause_tick 0.05
    done
    return 0
}

stop_lock_beat() {
    if [ -n "$LOCK_BEAT_PID" ]; then
        stop_own_loop "$LOCK_BEAT_PID" 2
        LOCK_BEAT_PID=""
    fi
}

start_lock_beat() {
    (
        beat=0
        while kill -0 "$$" 2>/dev/null; do
            tok=""
            read -r tok <"$GAMETEST_LOCK_DIR/token" 2>/dev/null
            [ "$tok" = "$GAMETEST_TOKEN" ] || break
            now_s now
            if [ "$beat" = 0 ] || [ $((now - beat)) -ge "$GAMETEST_BEAT_INTERVAL" ]; then
                printf '%s\n' "$now" >"$GAMETEST_LOCK_DIR/heartbeat.tmp" 2>/dev/null \
                    && mv -f "$GAMETEST_LOCK_DIR/heartbeat.tmp" "$GAMETEST_LOCK_DIR/heartbeat" 2>/dev/null
                beat=$now
            fi
            pause_tick 1
        done
    ) </dev/null >/dev/null 2>&1 &
    LOCK_BEAT_PID=$!
}

release_dir_lock() {
    stop_lock_beat
    DIR_LOCK_HELD=0
    [ -n "${GAMETEST_TOKEN:-}" ] || return 0
    local owner
    owner=$(cat "$GAMETEST_LOCK_DIR/token" 2>/dev/null || echo "")
    if [ "$owner" = "$GAMETEST_TOKEN" ]; then
        rm -rf "$GAMETEST_LOCK_DIR"
    elif [ -z "$owner" ] && [ "$(cat "$GAMETEST_LOCK_DIR/pid" 2>/dev/null)" = "$$" ]; then
        rm -rf "$GAMETEST_LOCK_DIR"
    elif [ -n "$owner" ]; then
        echo "gametest: замок уже не наш (чужой токен), не снимаю $GAMETEST_LOCK_DIR." >&2
    fi
}

acquire_dir_lock() {
    local waited=0 holder_pid holder_token stale beat age limit
    while true; do
        if mkdir "$GAMETEST_LOCK_DIR" 2>/dev/null; then
            echo $$ >"$GAMETEST_LOCK_DIR/pid"
            echo "$GAMETEST_TOKEN" >"$GAMETEST_LOCK_DIR/token"
            printf '%s\n' "$(date +%s)" >"$GAMETEST_LOCK_DIR/heartbeat"
            export GAMETEST_LOCK GAMETEST_TOKEN
            start_lock_beat
            DIR_LOCK_HELD=1
            return 0
        fi
        holder_pid=$(cat "$GAMETEST_LOCK_DIR/pid" 2>/dev/null || echo "")
        holder_token=$(cat "$GAMETEST_LOCK_DIR/token" 2>/dev/null || echo "")
        if [ -f "$GAMETEST_LOCK_DIR/heartbeat" ]; then
            beat=$(tr -dc '0-9' <"$GAMETEST_LOCK_DIR/heartbeat" 2>/dev/null)
            [ -n "$beat" ] || beat=$(file_mtime "$GAMETEST_LOCK_DIR/heartbeat")
            limit="$GAMETEST_STALE"
        else
            beat=$(file_mtime "$GAMETEST_LOCK_DIR/pid")
            [ "$beat" -gt 0 ] 2>/dev/null || beat=$(file_mtime "$GAMETEST_LOCK_DIR")
            limit="$GAMETEST_LEGACY_STALE"
        fi
        age=$(( $(date +%s) - beat ))
        [ "$age" -lt 0 ] && age=0
        if [ "$age" -gt "$limit" ]; then
            stale="$GAMETEST_LOCK_DIR.stale.$$"
            if mv "$GAMETEST_LOCK_DIR" "$stale" 2>/dev/null; then
                if [ "$(cat "$stale/token" 2>/dev/null || echo "")" = "$holder_token" ] \
                        && [ "$(cat "$stale/pid" 2>/dev/null || echo "")" = "$holder_pid" ]; then
                    echo "gametest: снимаю протухший замок $GAMETEST_LOCK_DIR (пульс не обновлялся ${age} с)." >&2
                    rm -rf "$stale"
                    continue
                fi
                if [ ! -e "$GAMETEST_LOCK_DIR" ]; then
                    mv "$stale" "$GAMETEST_LOCK_DIR" 2>/dev/null || rm -rf "$stale"
                else
                    rm -rf "$stale"
                fi
            fi
        fi
        [ "$waited" -ge "$GAMETEST_WAIT" ] && return 75
        pause_tick 2
        waited=$((waited + 2))
    done
}

busy_hint() {
    echo "gametest: ждали ${GAMETEST_WAIT} с, идёт чужой GameTest (блокировка $1)." >&2
    echo "gametest: GameTest — один на машину; дождись освобождения или освободи блокировку вручную." >&2
}

WARN_SECONDS=0
STOP_SECONDS=0
MONITOR_PID=""
GRADLE_PID=""
START_TIME=0

monitor_loop() {
    local warned=0 now elapsed gpid
    while kill -0 "$MONITOR_MAIN_PID" 2>/dev/null; do
        pause_tick 1
        now_s now
        elapsed=$((now - MONITOR_START_TIME))
        if [ "$warned" = 0 ] && [ "$elapsed" -ge "$MONITOR_WARN_SECONDS" ]; then
            if [ "$MONITOR_MODE" = "prepare" ]; then
                echo "ВНИМАНИЕ: подготовка сборки идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься" >&2
            elif [ "$MONITOR_MODE" = "gametest" ]; then
                echo "ВНИМАНИЕ: полный GameTest идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
            else
                echo "ВНИМАНИЕ: unit идёт дольше $MONITOR_WARN_SECONDS с — повод задуматься (регрессия скорости?)" >&2
            fi
            warned=1
        fi
        if [ "$elapsed" -ge "$MONITOR_STOP_SECONDS" ]; then
            echo "стоп по пределу $MONITOR_STOP_SECONDS с" >&2
            printf '%s' "$MONITOR_MODE" >"$MONITOR_LOG_FILE.stopped"
            kill_gametest_server
            if [ -f "$MONITOR_LOG_FILE.gradlepids" ]; then
                while read -r gpid; do
                    [ -n "$gpid" ] && kill_own_tree "$gpid" 10 "$MONITOR_GRADLE_GROUP"
                done <"$MONITOR_LOG_FILE.gradlepids"
            fi
            trap - TERM
            kill -TERM "$MONITOR_MAIN_PID" 2>/dev/null
            exit 124
        fi
    done
}

start_monitor() {
    local mode="$1" warn="${2:-}" stop="${3:-}"
    if [ "$mode" = "unit" ]; then
        WARN_SECONDS="$UNIT_WARN"
        STOP_SECONDS="$UNIT_STOP"
    else
        WARN_SECONDS="${warn:-$GAMETEST_WARN}"
        STOP_SECONDS="${stop:-$GAMETEST_STOP}"
    fi

    export MONITOR_LOG_FILE="$LOG_FILE" MONITOR_START_TIME="$START_TIME" \
           MONITOR_WARN_SECONDS="$WARN_SECONDS" MONITOR_STOP_SECONDS="$STOP_SECONDS" \
           MONITOR_MODE="$mode" MONITOR_MAIN_PID="$$" MONITOR_GRADLE_GROUP=""
    export MODE GAMETEST_RUN_DIR GAMETEST_WORKTREE
    export -f pid_is_live gametest_server_pids kill_gametest_server now_s own_tree_pids kill_own_tree pause_tick monitor_loop
    if command -v setsid >/dev/null 2>&1; then
        MONITOR_GRADLE_GROUP=group
        setsid bash -c monitor_loop </dev/null >/dev/null 2>&1 &
    else
        ( monitor_loop ) </dev/null >/dev/null 2>&1 &
    fi
    MONITOR_PID=$!
}

stop_timer() {
    if [ -n "$MONITOR_PID" ]; then
        stop_own_loop "$MONITOR_PID" 2
        MONITOR_PID=""
    fi
}

HEARTBEAT_FILE="${THEMINESPIRIT_TEST_HEARTBEAT:-TestResults/test-running}"
HEARTBEAT_PID=""
start_heartbeat() {
    local now deadline
    case "$HEARTBEAT_FILE" in */*) mkdir -p "${HEARTBEAT_FILE%/*}" ;; esac
    : >"$HEARTBEAT_FILE"
    now_s now
    deadline=$(( now + GAMETEST_WAIT + GAMETEST_PREPARE_STOP + GAMETEST_PREPARE_STOP + (UNIT_STOP > GAMETEST_FULL_STOP ? UNIT_STOP : GAMETEST_FULL_STOP) + 300 ))
    (
        beat=0
        while kill -0 "$$" 2>/dev/null; do
            now_s now
            [ "$now" -lt "$deadline" ] || break
            if [ "$beat" = 0 ] || [ $((now - beat)) -ge 20 ]; then
                printf '%s\n' "$BASHPID" >"$HEARTBEAT_FILE" 2>/dev/null
                beat=$now
            fi
            pause_tick 1
        done
    ) </dev/null >/dev/null 2>&1 &
    HEARTBEAT_PID=$!
}

stop_heartbeat() {
    if [ -n "$HEARTBEAT_PID" ]; then
        stop_own_loop "$HEARTBEAT_PID" 2
        HEARTBEAT_PID=""
        rm -f "$HEARTBEAT_FILE"
    fi
}

start_parent_watch() {
    [ "${THEMINESPIRIT_TEST_DETACHED:-0}" = 1 ] && return 0
    local parent="${PPID:-0}"
    case "$parent" in ''|*[!0-9]*) return 0 ;; esac
    [ "$parent" -gt 1 ] || return 0
    (
        while kill -0 "$parent" 2>/dev/null && kill -0 "$MAIN_PID" 2>/dev/null; do
            pause_tick 1
        done
        kill -0 "$MAIN_PID" 2>/dev/null || exit 0
        printf '%s\n' "$parent" >"$LOG_FILE.parent-gone" 2>/dev/null
        kill -TERM "$MAIN_PID" 2>/dev/null
    ) </dev/null >/dev/null 2>&1 &
    PARENT_WATCH_PID=$!
}

stop_parent_watch() {
    if [ -n "$PARENT_WATCH_PID" ]; then
        stop_own_loop "$PARENT_WATCH_PID" 2
        PARENT_WATCH_PID=""
    fi
}

stop_monitor() {
    kill_gametest_server
    if [ -n "$GRADLE_PID" ]; then
        kill_own_tree "$GRADLE_PID" 10 "${MONITOR_GRADLE_GROUP:-}"
        GRADLE_PID=""
    fi
    if [ -n "${LOG_FILE:-}" ] && [ -f "$LOG_FILE.gradlepids" ]; then
        local gp
        while read -r gp; do
            [ -n "$gp" ] && kill_own_tree "$gp" 10 "${MONITOR_GRADLE_GROUP:-}"
        done <"$LOG_FILE.gradlepids"
        rm -f "$LOG_FILE.gradlepids"
    fi
    stop_timer
    stop_heartbeat
    stop_parent_watch
    close_pause_tick
    [ -n "${LOG_FILE:-}" ] && rm -f "$LOG_FILE.parent-gone"
    return 0
}

cleanup() {
    stop_monitor
    [ "$DIR_LOCK_HELD" = 1 ] && release_dir_lock
    return 0
}

on_signal() {
    trap - TERM INT
    if [ -n "${LOG_FILE:-}" ] && [ -f "$LOG_FILE.parent-gone" ]; then
        echo "test.sh: родитель прогона умер — прогон остановлен (сироту не оставляем)" >&2
        exit 143
    fi
    if [ -n "${LOG_FILE:-}" ] && [ -f "$LOG_FILE.stopped" ]; then
        if [ "$(cat "$LOG_FILE.stopped" 2>/dev/null)" = "prepare" ]; then
            echo "стоп: подготовка сборки и загрузка мира (предел ${GAMETEST_PREPARE_STOP} с)"
        else
            echo "стоп по пределу ${STOP_SECONDS} с"
        fi
        echo "полный лог: $LOG_FILE"
        exit 124
    fi
    exit "$1"
}

trap cleanup EXIT
trap 'on_signal 143' TERM
trap 'on_signal 130' INT

read -r -d '' UNIT_REPORT_PY <<'PYEOF'
import glob, html, os, re, sys
attr = re.compile(r'([\w:.-]+)=\x22([^\x22]*)\x22')
tag = re.compile(r'<(testsuite|testcase|failure|error)(?=[\s/>])([^>]*)>')
cdata = re.compile(r'<!\[CDATA\[.*?\]\]>', re.S)
def num(a, k):
    try:
        return int(a.get(k, '0'))
    except ValueError:
        return 0
tot = fails = errs = 0
names, seen = [], set()
for path in sorted(glob.glob(os.path.join(sys.argv[1], 'TEST-*.xml'))):
    try:
        with open(path, encoding='utf-8', errors='replace') as fh:
            text = cdata.sub('', fh.read())
    except OSError:
        continue
    cls = name = ''
    for m in tag.finditer(text):
        kind, a = m.group(1), dict(attr.findall(m.group(2)))
        if kind == 'testsuite':
            tot += num(a, 'tests'); fails += num(a, 'failures'); errs += num(a, 'errors')
        elif kind == 'testcase':
            cls, name = html.unescape(a.get('classname', '')), html.unescape(a.get('name', ''))
        else:
            key = cls + '.' + name
            if key not in seen:
                seen.add(key); names.append(key)
out = ['%d %d %d' % (tot, fails, errs)] + names
sys.stdout.buffer.write(('\n'.join(out) + '\n').encode('utf-8'))
PYEOF

UNIT_REPORT_AWK='
function unesc(s) {
    gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&quot;/, "\"", s); gsub(/&apos;/, "\047", s)
    gsub(/&amp;/, "\\&", s)
    return s
}
function attr(s, key) {
    if (match(s, "[ \t]" key "=\"[^\"]*\"")) return substr(s, RSTART + length(key) + 3, RLENGTH - length(key) - 4)
    return ""
}
{
    file = $0; incdata = 0; cls = ""; name = ""
    while ((getline line < file) > 0) {
        kept = ""
        while (line != "") {
            if (incdata) {
                p = index(line, "]]>")
                if (!p) { line = ""; break }
                line = substr(line, p + 3); incdata = 0; continue
            }
            p = index(line, "<![CDATA[")
            if (!p) { kept = kept line; break }
            kept = kept substr(line, 1, p - 1); line = substr(line, p + 9); incdata = 1
        }
        if (kept ~ /<testsuite[ \t>]/) {
            tot += attr(kept, "tests") + 0; fails += attr(kept, "failures") + 0; errs += attr(kept, "errors") + 0
        }
        if (kept ~ /<testcase[ \t\/>]/) { cls = unesc(attr(kept, "classname")); name = unesc(attr(kept, "name")) }
        if (kept ~ /<(failure|error)[ \t\/>]/) {
            key = cls "." name
            if (!(key in seen)) { seen[key] = 1; order[++n] = key }
        }
    }
    close(file)
}
END { print tot + 0, fails + 0, errs + 0; for (i = 1; i <= n; i++) print order[i] }'

REPORT_PYTHON=""
find_report_python() {
    local cand path
    for cand in "${THEMINESPIRIT_PYTHON:-}" "${PYTHON:-}" python3 python; do
        [ -n "$cand" ] || continue
        [ "$cand" = none ] && return 1
        case "$cand" in
            */*) [ -x "$cand" ] || continue; path="$cand" ;;
            *) hash "$cand" 2>/dev/null || continue; path="${BASH_CMDS[$cand]:-}" ;;
        esac
        [ -n "$path" ] || continue
        case "$path" in */WindowsApps/*) continue ;; esac
        REPORT_PYTHON="$path"
        return 0
    done
    return 1
}

parse_unit_reports() {
    local dir="$1" out=""
    if find_report_python; then
        out=$("$REPORT_PYTHON" -c "$UNIT_REPORT_PY" "$dir" 2>/dev/null)
        case "${out%%$'\n'*}" in
            [0-9]*' '[0-9]*' '[0-9]*) printf '%s\n' "$out"; return 0 ;;
        esac
    fi
    local files=("$dir"/TEST-*.xml)
    [ -e "${files[0]}" ] || files=()
    printf '%s\n' "${files[@]}" | awk "$UNIT_REPORT_AWK"
}

analyze_unit() {
    local log="$1"
    local gradle_code="${2:-0}"
    local subset="${3:-0}"
    local filtered="${4:-0}"
    local xml_dir="TestResults/unit/xml"

    local total=0 failures=0 errors=0
    local first_error=""
    local first_stack=""
    local failed_names=() report=()

    mapfile -t report < <(parse_unit_reports "$xml_dir")
    read -r total failures errors <<<"${report[0]:-}"
    case "$total" in ''|*[!0-9]*) total=0 ;; esac
    case "$failures" in ''|*[!0-9]*) failures=0 ;; esac
    case "$errors" in ''|*[!0-9]*) errors=0 ;; esac
    [ "${#report[@]}" -gt 1 ] && failed_names=("${report[@]:1}")

    local extra_dirs=(TestResults/api/xml)
    local module_root name
    for module_root in ${MOD_MODULE_MAIN_ROOTS//,/ }; do
        name="${module_root%%/src/*}"
        extra_dirs+=("TestResults/$(basename "$name")/xml")
    done
    local extra_dir extra_report=()
    for extra_dir in "${extra_dirs[@]}"; do
        [ -d "$extra_dir" ] || continue
        mapfile -t extra_report < <(parse_unit_reports "$extra_dir")
        local extra_total=0 extra_failures=0 extra_errors=0
        read -r extra_total extra_failures extra_errors <<<"${extra_report[0]:-}"
        case "$extra_total" in ''|*[!0-9]*) extra_total=0 ;; esac
        case "$extra_failures" in ''|*[!0-9]*) extra_failures=0 ;; esac
        case "$extra_errors" in ''|*[!0-9]*) extra_errors=0 ;; esac
        total=$((total + extra_total))
        failures=$((failures + extra_failures))
        errors=$((errors + extra_errors))
        [ "${#extra_report[@]}" -gt 1 ] && failed_names+=("${extra_report[@]:1}")
    done

    if [ "$failures" -eq 0 ] && [ "$errors" -eq 0 ] && [ "$total" -gt 0 ]; then
        if [ "$gradle_code" -ne 0 ]; then
            echo "FAIL static: $total тестов зелёные, но сборка упала (код gradle $gradle_code)"
            grep -A3 "What went wrong" "$log" | grep -v "^--$" | head -16
            grep -m10 -E "violation|\[(ERROR|WARN)\]|spotlessApply" "$log" | head -10
            echo "полный лог: $log"
            return 1
        fi
        local cpu_ms=0
        if [ -f TestResults/unit/test-cost.csv ]; then
            cpu_ms=$(awk -F, 'NR > 1 { sum += $4 } END { print sum + 0 }' TestResults/unit/test-cost.csv)
        fi
        local cost module_root cost_name
        for module_root in ${MOD_MODULE_MAIN_ROOTS//,/ }; do
            cost_name="$(basename "${module_root%%/src/*}")"
            cost="TestResults/$cost_name/test-cost.csv"
            if [ -f "$cost" ]; then
                cpu_ms=$((cpu_ms + $(awk -F, 'NR > 1 { sum += $4 } END { print sum + 0 }' "$cost")))
            fi
        done
        local cpu=$((cpu_ms / 1000))
        if [ "$cpu" -gt "$UNIT_CPU_STOP" ]; then
            echo "FAIL cpu: $total тестов зелёные, но время процессора тестов $cpu с больше предела $UNIT_CPU_STOP с"
            echo "самые тяжёлые — TestResults/unit/test-cost.csv (cpu_ms); ищи, что замедлило тесты"
            echo "полный лог: $log"
            return 1
        fi
        if [ "$cpu" -gt "$UNIT_CPU_WARN" ]; then
            echo "ВНИМАНИЕ: время процессора тестов $cpu с больше $UNIT_CPU_WARN с — повод задуматься" >&2
        fi
        echo "OK unit: $total тестов, $ELAPSED с, процессор тестов $cpu с"
        return 0
    fi

    if [ "$total" -eq 0 ]; then
        if [ "$subset" = 1 ] && [ "$filtered" = 0 ] && [ "$gradle_code" -eq 0 ]; then
            echo "OK unit: тестов нет (в выбранных модулях их нет)"
            return 0
        fi
        echo "BUILD ERROR"
        grep -m3 -E "error:|FAILED|Exception" "$log" | head -3
        echo "полный лог: $log"
        return 1
    fi

    echo "FAIL unit: прошло $((total - failures - errors)), упало $((failures + errors))"
    if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
        first_error=$(grep -E "FAILED|Exception|Error:" "$log" | head -1)
        first_stack=$(grep -A 10 -E "FAILED|Exception|Error:" "$log" | head -12)
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

gametest_shard_passed() {
    grep -E "All [0-9]+ required tests passed" "$1" 2>/dev/null | tail -1 \
        | sed -E 's/.*All ([0-9]+) required tests passed.*/\1/'
}

gametest_shard_share() {
    local line
    line=$(grep -E "GameTest filter .*осталось [0-9]+" "$1" 2>/dev/null | tail -1)
    [ -n "$line" ] || return 0
    printf '%s %s\n' \
        "$(printf '%s\n' "$line" | sed -E 's/.*из ([0-9]+), осталось.*/\1/')" \
        "$(printf '%s\n' "$line" | sed -E 's/.*осталось ([0-9]+).*/\1/')"
}

analyze_gametest() {
    local log="$1" shards="${2:-1}" base="${3:-$1}" code="${4:-0}"
    local i shard_log shard_code

    local failed_line=$(grep -E "[0-9]+ required tests failed" "$log" | tail -1)
    if [ -n "$failed_line" ]; then
        local failed_count=$(grep -E "[0-9]+ required tests failed" "$log" \
            | sed -E 's/.* ([0-9]+) required tests failed.*/\1/' | awk '{ s += $1 } END { print s + 0 }')
        echo "FAIL gametest: упало ${failed_count:-неизвестно}"
        grep -E "^\[[0-9:]+\].*GameTestServer\]:    - " "$log" \
            | sed -E 's/.*\]:    - //' | sort -u | head -20
        echo "полный лог: $log"
        return 1
    fi

    if [ "$shards" -gt 1 ]; then
        local counts=() total=0 registry="" s_total s_share n breakdown
        for ((i = 0; i < shards; i++)); do
            shard_log="$base.shard-$i"
            shard_code="${SHARD_CODES[$i]:-?}"
            if [ ! -s "$shard_log" ]; then
                echo "FAIL gametest: шард $i не запускал сцены — его лог пуст (код gradle $shard_code)"
                echo "шард обязан напечатать «All N required tests passed»; без этого его сцены не проверены"
                echo "полный лог: $log"
                return 1
            fi
            n=$(gametest_shard_passed "$shard_log")
            if [ -z "$n" ]; then
                echo "FAIL gametest: шард $i не дошёл до «All N required tests passed» (код gradle $shard_code)"
                grep -E "BUILD FAILED|What went wrong|Unable to|error:|Exception" "$shard_log" | head -3
                echo "полный лог: $log"
                return 1
            fi
            if [ "$shard_code" != 0 ]; then
                echo "FAIL gametest: шард $i напечатал итог, но сборка ушла с кодом $shard_code"
                grep -E "BUILD FAILED|What went wrong|Unable to|error:" "$shard_log" | head -3
                echo "полный лог: $log"
                return 1
            fi
            read -r s_total s_share <<<"$(gametest_shard_share "$shard_log")"
            if [ -n "${s_share:-}" ] && [ "$n" != "$s_share" ]; then
                echo "FAIL gametest: шард $i прошёл $n тестов, а ему назначено $s_share сцен — фильтр шарда не сработал"
                echo "полный лог: $log"
                return 1
            fi
            if [ -n "${s_total:-}" ]; then
                if [ -z "$registry" ]; then
                    registry="$s_total"
                elif [ "$registry" != "$s_total" ]; then
                    echo "FAIL gametest: шарды видят разный набор сцен ($registry и $s_total) — фильтр применился по-разному"
                    echo "полный лог: $log"
                    return 1
                fi
            fi
            counts+=("$n")
            total=$((total + n))
        done
        breakdown=$(printf '%s+' "${counts[@]}")
        breakdown="${breakdown%+}"
        echo "OK gametest: $total тестов ($shards шардов: $breakdown), $ELAPSED с"
        return 0
    fi

    local passed_line=$(grep -E "All [0-9]+ required tests passed" "$log" | tail -1)

    if [ -n "$passed_line" ] && [ "$code" -eq 0 ]; then
        local count
        count=$(grep -E "All [0-9]+ required tests passed" "$log" \
            | sed -E 's/.*All ([0-9]+) required tests passed.*/\1/' | awk '{s += $1} END {print s + 0}')
        echo "OK gametest: $count тестов, $ELAPSED с"
        return 0
    fi

    if [ -n "$passed_line" ]; then
        echo "FAIL gametest: тесты прошли, но прогон ушёл с кодом $code — результат не засчитан"
        grep -E "BUILD FAILED|What went wrong|Unable to|error:" "$log" | head -3
        echo "полный лог: $log"
        return 1
    fi

    echo "FAIL gametest: сервер сцен не дошёл до результата, причина из лога:"
    grep -E "не оставили ни одного теста|What went wrong|Exception caught|error:" "$log" | head -3 | cut -c1-240
    echo "полный лог: $log"
    return 1
}

gametest_scene_started() {
    local log="$1" pid="$2"
    grep -q "tests are now running" "$log" 2>/dev/null && return 0
    kill -0 "$pid" 2>/dev/null || return 0
    return 1
}

wait_gametest_scenes() {
    local shard pid log waiting
    while :; do
        waiting=0
        for ((shard = 0; shard < ${#GRADLE_PIDS[@]}; shard++)); do
            pid="${GRADLE_PIDS[$shard]}"
            log="$LOG_FILE"
            [ "$GAMETEST_SHARDS" -gt 1 ] && log="$LOG_FILE.shard-$shard"
            gametest_scene_started "$log" "$pid" || waiting=1
        done
        [ "$waiting" = 0 ] && return 0
        [ -f "$LOG_FILE.stopped" ] && return 1
        pause_tick 1
    done
}

STAMP=$(date +%Y%m%d-%H%M%S-%N)
LOG_FILE="$LOG_DIR/${STAMP}-${MODE}.log"
rm -f "$LOG_FILE.stopped"
now_s START_TIME
ELAPSED=0

echo "Режим: $MODE" >&2
echo "Лог: $LOG_FILE" >&2
init_pause_tick
start_heartbeat
start_parent_watch

if [ "$MODE" = "unit" ]; then
    rm -f TestResults/unit/xml/TEST-*.xml TestResults/unit/test-cost.csv
    rm -f TestResults/api/xml/TEST-*.xml
    for module_root in ${MOD_MODULE_MAIN_ROOTS//,/ }; do
        name="${module_root%%/src/*}"
        rm -f "TestResults/$(basename "$name")/xml/"TEST-*.xml "TestResults/$(basename "$name")/test-cost.csv"
    done
    start_monitor "unit"

    UNIT_EXTRA=()
    UNIT_EXCLUDE=()
    UNIT_PROPS=()
    UNIT_TARGETS=()
    if [ ${#UNIT_TESTS[@]} -gt 0 ]; then
        UNIT_EXCLUDE=(-Ptheminespirit.filtered)
        # lib: --tests действует только на задачу test того проекта, который назван в командной строке;
        # тесты модулей фильтруются тем же списком через свойство.
        local_unit_patterns=()
        for ((unit_index = 0; unit_index < ${#UNIT_TESTS[@]}; unit_index += 2)); do
            local_unit_patterns+=("${UNIT_TESTS[$((unit_index + 1))]}")
        done
        [ ${#local_unit_patterns[@]} -gt 0 ] && UNIT_PROPS=(-Ptheminespirit.tests="$(IFS=,; printf '%s' "${local_unit_patterns[*]}")")
    fi

    if [ "$UNIT_SUBSET" = 1 ]; then
        if [ ${#UNIT_MODULES[@]} -eq 0 ]; then
            echo "unit --changed: изменений в тестируемых путях нет — прогонять нечего"
            exit 0
        fi
        for unit_module in "${UNIT_MODULES[@]}"; do
            if [ "$unit_module" = ":root" ]; then
                UNIT_TARGETS+=(":test")
                UNIT_PROPS+=(-Ptheminespirit.rootOnly)
            else
                UNIT_TARGETS+=("$unit_module:test")
            fi
        done
        if [ ${#UNIT_TESTS[@]} -eq 0 ]; then
            for unit_module in "${UNIT_MODULES[@]}"; do
                if [ "$unit_module" = ":root" ]; then
                    UNIT_EXTRA+=(:spotlessCheck)
                else
                    UNIT_EXTRA+=("$unit_module:formatCheck")
                fi
            done
            UNIT_EXTRA+=(--continue)
        fi
        echo "unit: модули ${UNIT_MODULES[*]}" >&2
    else
        UNIT_TARGETS=(:test)
        if [ ${#UNIT_TESTS[@]} -eq 0 ]; then
            UNIT_EXTRA=(formatCheck --continue)
        fi
    fi

    if command -v setsid >/dev/null 2>&1; then
        setsid "$GRADLEW" "${UNIT_TARGETS[@]}" --rerun "${UNIT_TESTS[@]}" "${UNIT_EXCLUDE[@]}" "${UNIT_PROPS[@]}" "${UNIT_EXTRA[@]}" >"$LOG_FILE" 2>&1 &
    else
        "$GRADLEW" "${UNIT_TARGETS[@]}" --rerun "${UNIT_TESTS[@]}" "${UNIT_EXCLUDE[@]}" "${UNIT_PROPS[@]}" "${UNIT_EXTRA[@]}" >"$LOG_FILE" 2>&1 &
    fi
    GRADLE_PID=$!
    echo "$GRADLE_PID" >"$LOG_FILE.gradlepid"
    wait "$GRADLE_PID"
    CODE=$?
    GRADLE_PID=""
    stop_timer
    rm -f "$LOG_FILE.gradlepid"

    now_s ELAPSED
    ELAPSED=$((ELAPSED - START_TIME))

    if [ -f "$LOG_FILE.stopped" ]; then
        echo "стоп по пределу ${UNIT_STOP} с"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi

    analyze_unit "$LOG_FILE" "$CODE" "$UNIT_SUBSET" "${#UNIT_TESTS[@]}"
    exit $?

else
    GAMETEST_GRADLE_ARGS=()
    if [ ${#GAMETEST_FILTERS[@]} -gt 0 ]; then
        FILTER_VALUE=$(IFS=,; echo "${GAMETEST_FILTERS[*]}")
        GAMETEST_GRADLE_ARGS+=("-Dtheminespirit.gametest.filter=$FILTER_VALUE")
    fi
    if [ -n "$GAMETEST_SEED" ]; then
        GAMETEST_GRADLE_ARGS+=("-Dtheminespirit.gametest.seed=$GAMETEST_SEED")
    fi
    GAMETEST_EXCLUDE=""
    if [ ${#GAMETEST_FILTERS[@]} -eq 0 ]; then
        GAMETEST_EXCLUDE="demo_audit"
    fi

    USE_DIR_LOCK=0
    noflock && USE_DIR_LOCK=1
    [ "$GAMETEST_SHARDS" -gt 1 ] && USE_DIR_LOCK=1

    if [ "$USE_DIR_LOCK" = 1 ]; then
        GAMETEST_TOKEN="$(date +%s%N)-$$"
        mkdir -p "$(dirname "$GAMETEST_LOCK_DIR")" 2>/dev/null || true
        acquire_dir_lock
        LOCK_CODE=$?
        if [ "$LOCK_CODE" -eq 75 ]; then
            busy_hint "$GAMETEST_LOCK_DIR"
            exit 75
        fi
    else
        GAMETEST_TOKEN="$(date +%s%N)-$$"
        export GAMETEST_LOCK GAMETEST_TOKEN
    fi
    GAMETEST_GRADLE_ARGS+=("-Dtheminespirit.gametest.lock=$GAMETEST_LOCK" "-Dtheminespirit.gametest.token=$GAMETEST_TOKEN")

    if [ "$GAMETEST_FULL" = 1 ]; then
        START_WARN="$GAMETEST_FULL_WARN"
        START_STOP="$GAMETEST_FULL_STOP"
    else
        START_WARN="$GAMETEST_WARN"
        START_STOP="$GAMETEST_STOP"
    fi

    GRADLE_PIDS=()
    : >"$LOG_FILE.gradlepids"

    SHARD_EXCLUDES=()
    if [ "$GAMETEST_SHARDS" -gt 1 ]; then
        now_s START_TIME
        start_monitor "prepare" "$GAMETEST_PREPARE_WARN" "$GAMETEST_PREPARE_STOP"
        PRE_TASKS=(:gametest:createMinecraftArtifacts :gametest:classes :gametest:jar
            :gametest:writeGameTestServerLegacyClasspath :gametest:prepareGameTestServerRun)
        while read -r module_path; do
            [ -n "$module_path" ] && PRE_TASKS+=("$module_path:jar")
        done < <(sed -nE "s/^include +'(:[^.][^']*)'.*/\1/p" settings.gradle)
        "$GRADLEW" "${PRE_TASKS[@]}" >"$LOG_FILE.preflight" 2>&1 &
        PRE_PID=$!
        echo "$PRE_PID" >>"$LOG_FILE.gradlepids"
        wait "$PRE_PID"
        PRE_CODE=$?
        : >"$LOG_FILE.gradlepids"
        stop_timer
        if [ -f "$LOG_FILE.stopped" ]; then
            cat "$LOG_FILE.preflight" >"$LOG_FILE"
            rm -f "$LOG_FILE.preflight"
            echo "стоп: подготовка сборки и загрузка мира (предел ${GAMETEST_PREPARE_STOP} с)"
            echo "полный лог: $LOG_FILE"
            exit 124
        fi
        if [ "$PRE_CODE" -ne 0 ]; then
            cat "$LOG_FILE.preflight" >"$LOG_FILE"
            rm -f "$LOG_FILE.preflight"
            echo "FAIL gametest: подготовка сборки упала (код $PRE_CODE)"
            echo "полный лог: $LOG_FILE"
            exit 2
        fi
        rm -f "$LOG_FILE.preflight"
        SHARD_EXCLUDES=(-x createMinecraftArtifacts -x compileJava -x generateModMetadata
            -x processResources -x classes -x downloadAssets -x jar -x :build-logic:jar
            -x writeGameTestServerLegacyClasspath -x prepareGameTestServerRun)
    fi

    now_s START_TIME
    start_monitor "prepare" "$GAMETEST_PREPARE_WARN" "$GAMETEST_PREPARE_STOP"
    echo "gametest: подготовка сборки и загрузка мира — отдельный бюджет ${GAMETEST_PREPARE_STOP} с" >&2

    for ((shard = 0; shard < GAMETEST_SHARDS; shard++)); do
        shard_log="$LOG_FILE"
        shard_args=("${GAMETEST_GRADLE_ARGS[@]}")
        shard_run_dir="$GAMETEST_RUN_DIR"
        shard_value=""
        if [ "$GAMETEST_SHARDS" -gt 1 ]; then
            shard_log="$LOG_FILE.shard-$shard"
            : >"$shard_log"
            shard_run_dir="$GAMETEST_RUN_DIR/shard-$shard"
            shard_value="$shard/$GAMETEST_SHARDS"
            shard_args+=("-Dtheminespirit.gametest.dir=build/gametest-run/shard-$shard")
        fi
        mkdir -p "$shard_run_dir"
        if [ -n "$GAMETEST_SEED" ]; then
            rm -rf "$shard_run_dir/world"
        fi
        {
            echo "exclude=$GAMETEST_EXCLUDE"
            echo "shard=$shard_value"
        } >"$shard_run_dir/theminespirit-gametest.properties"
        CACHE_ARGS=()
        [ "$GAMETEST_SHARDS" -gt 1 ] && CACHE_ARGS=(--project-cache-dir "$GAMETEST_WORKTREE/build/.gradle-gametest-shard-$shard")
        if [ "$USE_DIR_LOCK" = 1 ]; then
            if command -v setsid >/dev/null 2>&1; then
                setsid "$GRADLEW" "${CACHE_ARGS[@]}" "${shard_args[@]}" "${SHARD_EXCLUDES[@]}" :gametest:runGameTestServer >"$shard_log" 2>&1 &
            else
                "$GRADLEW" "${CACHE_ARGS[@]}" "${shard_args[@]}" "${SHARD_EXCLUDES[@]}" :gametest:runGameTestServer >"$shard_log" 2>&1 &
            fi
        else
            flock -w "$GAMETEST_WAIT" -E 75 "$GAMETEST_LOCK" bash -c '
                echo "$GAMETEST_TOKEN" >"$GAMETEST_LOCK.token"
                "$@"' bash \
                "$GRADLEW" "${shard_args[@]}" "${SHARD_EXCLUDES[@]}" :gametest:runGameTestServer >"$shard_log" 2>&1 &
        fi
        GRADLE_PIDS+=("$!")
        echo "$!" >>"$LOG_FILE.gradlepids"
    done

    if ! wait_gametest_scenes; then
        stop_timer
        [ "$GAMETEST_SHARDS" -gt 1 ] && cat "$LOG_FILE".shard-* >"$LOG_FILE" 2>/dev/null
        echo "стоп: подготовка сборки и загрузка мира (предел ${GAMETEST_PREPARE_STOP} с)"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi
    stop_timer
    now_s START_TIME
    start_monitor "gametest" "$START_WARN" "$START_STOP"
    echo "gametest: бюджет сценариев ${START_STOP} с — от старта сцен (подготовка сборки и загрузка мира — ${GAMETEST_PREPARE_STOP} с отдельно)" >&2

    CODE=0
    SHARD_CODES=()
    for pid in "${GRADLE_PIDS[@]}"; do
        wait "$pid"
        shard_code=$?
        SHARD_CODES+=("$shard_code")
        if [ "$shard_code" -ne 0 ] && [ "$CODE" -eq 0 ]; then
            CODE=$shard_code
        fi
    done
    stop_timer

    now_s ELAPSED
    ELAPSED=$((ELAPSED - START_TIME))

    if [ "$USE_DIR_LOCK" = 1 ]; then
        release_dir_lock
    fi

    if [ "$GAMETEST_SHARDS" -gt 1 ]; then
        cat "$LOG_FILE".shard-* >"$LOG_FILE"
    fi
    rm -f "$LOG_FILE.gradlepids"

    if [ -f "$LOG_FILE.stopped" ]; then
        echo "стоп по пределу ${START_STOP} с"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi

    if [ "$CODE" -eq 75 ]; then
        busy_hint "$GAMETEST_LOCK"
        exit 75
    fi

    analyze_gametest "$LOG_FILE" "$GAMETEST_SHARDS" "$LOG_FILE" "$CODE"
    RESULT=$?
    [ "$GAMETEST_SHARDS" -gt 1 ] && rm -f "$LOG_FILE".shard-*
    exit $RESULT
fi
