#!/usr/bin/env bash
# Единый запуск тестов (docs/TESTING.md → «Два уровня»). Исполнители запускают тесты ТОЛЬКО через этот скрипт.
# Полный лог всегда в TestResults/logs/<дата-время>-<режим>.log; на экран — только итог.
# Режимы:
#   scripts/test.sh unit [--tests <фильтр gradle>]…
#   scripts/test.sh gametest [имя пачки или теста…]
# Пределы времени (переменные окружения, секунды):
#   THEMINESPIRIT_UNIT_WARN_SECONDS (по умолчанию 180), THEMINESPIRIT_UNIT_STOP_SECONDS (360)
#   THEMINESPIRIT_GAMETEST_WARN_SECONDS (300), THEMINESPIRIT_GAMETEST_STOP_SECONDS (1500)
# Коды выхода: 0 — успех; 1 — падение тестов; 2 — ошибка запуска; 75 — чужой GameTest; 124 — стоп по пределу времени.
# Без -e: коды проверяются явно; под -e пустой grep в разборе падения молча обрывал скрипт без итога.
set -uo pipefail
cd "$(dirname "$0")/.."

# Реальный путь worktree (pwd -P): /proc/<pid>/cwd отдаёт разыменованный путь, и по логическому
# (с символической ссылкой) каталог сервера не нашёлся бы.
GAMETEST_WORKTREE="$(pwd -P)"
GAMETEST_RUN_DIR="$GAMETEST_WORKTREE/build/gametest-run"

GRADLEW="${THEMINESPIRIT_GRADLEW:-./gradlew}"
LOG_DIR="TestResults/logs"
mkdir -p "$LOG_DIR"

# Пределы времени (секунды)
UNIT_WARN="${THEMINESPIRIT_UNIT_WARN_SECONDS:-180}"
UNIT_STOP="${THEMINESPIRIT_UNIT_STOP_SECONDS:-360}"
GAMETEST_WARN="${THEMINESPIRIT_GAMETEST_WARN_SECONDS:-300}"
GAMETEST_STOP="${THEMINESPIRIT_GAMETEST_STOP_SECONDS:-1500}"

# Замок GameTest (переиспользуем логику из scripts/gametest.sh).
# Дефолт: на Windows — C:\ProgramData\theminespirit (общий для всех сессий и пользователей машины;
# /tmp в Git Bash монтируется в %TEMP% текущего пользователя — у разных сессий opencode TEMP может
# указывать в разные каталоги, и замок перестаёт быть общим). На Linux — /tmp.
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
# Протухание mkdir-замка — по возрасту пульса, а не по PID: в Git Bash PID чужого сеанса MSYS
# может выглядеть мёртвым для kill -0, и живой замок снимался как протухший (токен перезаписывался,
# а вышедший первым владелец удалял уже чужой маркер). Владелец обновляет heartbeat раз в
# GAMETEST_BEAT_INTERVAL; маркер старого test.sh (pid без токена и пульса) стареет по mtime.
GAMETEST_BEAT_INTERVAL="${THEMINESPIRIT_GAMETEST_BEAT_SECONDS:-10}"
GAMETEST_STALE="${THEMINESPIRIT_GAMETEST_STALE_SECONDS:-120}"
GAMETEST_LEGACY_STALE="${THEMINESPIRIT_GAMETEST_LEGACY_STALE_SECONDS:-900}"
LOCK_BEAT_PID=""

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
  THEMINESPIRIT_GAMETEST_STOP_SECONDS стоп gametest (по умолчанию 1500)
  THEMINESPIRIT_GAMETEST_LOCK        путь к замку (Windows: C:\ProgramData\theminespirit\theminespirit-gametest.lock, Linux: /tmp/theminespirit-gametest.lock)
  THEMINESPIRIT_GAMETEST_WAIT        ожидание замка, с (по умолчанию 1800)
  THEMINESPIRIT_GAMETEST_STALE_SECONDS  возраст пульса, после которого mkdir-замок протух (120)
  THEMINESPIRIT_GAMETEST_BEAT_SECONDS   период обновления пульса владельцем (10)
  THEMINESPIRIT_GAMETEST_LEGACY_STALE_SECONDS  порог для маркера старого test.sh без пульса (900)
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

file_mtime() {
    stat -c %Y "$1" 2>/dev/null || echo 0
}

# ── Гашение сервера GameTest своего worktree ─────────────────────────────────
# Игру запускает демон Gradle отдельным процессом, чья рабочая папка — <worktree>/build/gametest-run
# (build.gradle → gameTestServer.gameDirectory). Стоп по пределу убивает только клиент gradle, а игровой
# сервер переживает его, ест CPU, и следующий прогон идёт параллельно уже без замка (todo.md 17.10).
# Поэтому сервер гасим сами — и до снятия замка. Чужие worktree не трогаем: у каждого свой каталог.

# Жив ли процесс. Зомби (state Z в /proc/<pid>/stat) уже мёртв: его не считаем и не ждём — иначе
# ожидание KILL тянется 10 с, пока родитель не соберёт статус. На не-Linux — обычный kill -0.
pid_is_live() {
    local pid="$1" rest
    if [ -r "/proc/$pid/stat" ]; then
        rest=$(cat "/proc/$pid/stat" 2>/dev/null)
        rest="${rest##*) }"
        case "$rest" in
            Z*) return 1 ;;
        esac
        return 0
    fi
    kill -0 "$pid" 2>/dev/null
}

# PID java-процессов, чья рабочая папка — каталог GameTest этого worktree ($1).
gametest_server_pids() {
    local dir="$1" d pid exe cwd
    case "$OSTYPE" in
        msys*|cygwin*|win*)
            # Git Bash на Windows: /proc/<pid>/cwd нет. Ищем java.exe по CommandLine — путь worktree
            # (в fml.modFolders и classpath) или gameDir; чужой worktree даёт другой путь.
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
                [ "$cwd" = "$dir" ] && pid_is_live "$pid" && printf '%s\n' "$pid"
            done
            ;;
    esac
    return 0
}

# Сначала TERM, через 10 с — KILL. Пустой список — тихо выходим (обычный успешный прогон: сервер уже мёртв).
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
        sleep 1
        waited=$((waited + 1))
    done
    for pid in $pids; do
        kill -KILL "$pid" 2>/dev/null || true
    done
    return 0
}

stop_lock_beat() {
    if [ -n "$LOCK_BEAT_PID" ]; then
        kill "$LOCK_BEAT_PID" 2>/dev/null || true
        LOCK_BEAT_PID=""
    fi
}

# Пульс замка: пишется только пока в маркере наш токен (перехваченный чужой маркер не трогаем).
start_lock_beat() {
    (
        while kill -0 "$$" 2>/dev/null \
                && [ "$(cat "$GAMETEST_LOCK_DIR/token" 2>/dev/null)" = "$GAMETEST_TOKEN" ]; do
            printf '%s\n' "$(date +%s)" >"$GAMETEST_LOCK_DIR/heartbeat.tmp" 2>/dev/null \
                && mv -f "$GAMETEST_LOCK_DIR/heartbeat.tmp" "$GAMETEST_LOCK_DIR/heartbeat" 2>/dev/null
            sleep "$GAMETEST_BEAT_INTERVAL"
        done
    ) >/dev/null 2>&1 &
    LOCK_BEAT_PID=$!
}

release_dir_lock() {
    stop_lock_beat
    [ -n "${GAMETEST_TOKEN:-}" ] || return 0
    local owner
    owner=$(cat "$GAMETEST_LOCK_DIR/token" 2>/dev/null || echo "")
    if [ "$owner" = "$GAMETEST_TOKEN" ]; then
        rm -rf "$GAMETEST_LOCK_DIR"
    elif [ -z "$owner" ] && [ "$(cat "$GAMETEST_LOCK_DIR/pid" 2>/dev/null)" = "$$" ]; then
        # Наш pid без токена (запись токена сорвалась) — маркер всё равно наш.
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
            # Один trap на выход: остановить монитор (он гасит сервер GameTest) И снять замок.
            # Порядок важен — замок снимается только после смерти сервера, иначе следующий прогон
            # стартует параллельно. Отдельный trap release_dir_lock перезаписал бы stop_monitor,
            # и фоновый монитор остался бы жив (на Windows без setsid).
            trap 'stop_monitor; release_dir_lock' EXIT INT TERM
            return 0
        fi
        holder_pid=$(cat "$GAMETEST_LOCK_DIR/pid" 2>/dev/null || echo "")
        holder_token=$(cat "$GAMETEST_LOCK_DIR/token" 2>/dev/null || echo "")
        if [ -f "$GAMETEST_LOCK_DIR/heartbeat" ]; then
            beat=$(tr -dc '0-9' <"$GAMETEST_LOCK_DIR/heartbeat" 2>/dev/null)
            [ -n "$beat" ] || beat=$(file_mtime "$GAMETEST_LOCK_DIR/heartbeat")
            limit="$GAMETEST_STALE"
        else
            # Маркер старого test.sh: pid без токена и пульса — возраст по mtime pid/каталога.
            beat=$(file_mtime "$GAMETEST_LOCK_DIR/pid")
            [ "$beat" -gt 0 ] 2>/dev/null || beat=$(file_mtime "$GAMETEST_LOCK_DIR")
            limit="$GAMETEST_LEGACY_STALE"
        fi
        age=$(( $(date +%s) - beat ))
        [ "$age" -lt 0 ] && age=0
        if [ "$age" -gt "$limit" ]; then
            stale="$GAMETEST_LOCK_DIR.stale.$$"
            if mv "$GAMETEST_LOCK_DIR" "$stale" 2>/dev/null; then
                # Снимаем, только если переименовали именно тот маркер, который судили:
                # иначе пока читали, замок перехватил другой — возвращаем на место.
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
    # Монитор в ветке setsid — отдельный bash: функции и переменные для гашения сервера нужны в окружении.
    export MODE GAMETEST_RUN_DIR GAMETEST_WORKTREE
    export -f pid_is_live gametest_server_pids kill_gametest_server
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
                    # Сначала гасим сервер GameTest своего worktree, потом клиент gradle: иначе
                    # снятие замка (flock/$LOCK.d) обгонит смерть сервера, и следующий прогон
                    # пойдёт параллельно уже без замка (todo.md 17.10).
                    kill_gametest_server
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
                    # Сначала гасим сервер GameTest своего worktree, потом клиент gradle: иначе
                    # снятие замка (flock/$LOCK.d) обгонит смерть сервера, и следующий прогон
                    # пойдёт параллельно уже без замка (todo.md 17.10).
                    kill_gametest_server
                    if [ -f "$MONITOR_LOG_FILE.gradlepid" ]; then
                        GRADLE_PID_FROM_FILE=$(cat "$MONITOR_LOG_FILE.gradlepid")
                        kill -TERM -- "-$GRADLE_PID_FROM_FILE" 2>/dev/null || kill -TERM "$GRADLE_PID_FROM_FILE" 2>/dev/null || true
                    fi
                    exit 124
                fi
            done
        ) >/dev/null 2>&1 &
    fi
    MONITOR_PID=$!
}

# Пульс для надзора исполнителя (scripts/opencode-task.sh): пока скрипт жив — ждёт замок или гоняет тесты —
# файл обновляется раз в 20 с, и надзор не путает долгий прогон с молчанием модели.
HEARTBEAT_FILE="${THEMINESPIRIT_TEST_HEARTBEAT:-TestResults/test-running}"
HEARTBEAT_PID=""
start_heartbeat() {
    mkdir -p "$(dirname "$HEARTBEAT_FILE")"
    touch "$HEARTBEAT_FILE"
    # stdout/stderr — в /dev/null: иначе фоновый sleep наследует пайп вызывающего
    # (out=$(scripts/test.sh …)) и держит его открытым до конца сна, задерживая проверки.
    # Пульс живёт не дольше самого долгого законного прогона (ожидание замка + предел + запас): на Windows
    # осиротевший test.sh без gradle (17.10) держал пульс часами, и надзор не перезапускал зависшую сессию.
    local beats=$(( (GAMETEST_WAIT + (UNIT_STOP > GAMETEST_STOP ? UNIT_STOP : GAMETEST_STOP) + 300) / 20 ))
    ( n=0; while [ "$n" -lt "$beats" ] && kill -0 "$$" 2>/dev/null; do touch "$HEARTBEAT_FILE" 2>/dev/null; sleep 20; n=$((n + 1)); done ) >/dev/null 2>&1 &
    HEARTBEAT_PID=$!
}

stop_monitor() {
    # Сервер GameTest гасим до снятия замка (release_dir_lock в trap идёт после stop_monitor):
    # иначе замок освободится, пока старый сервер ещё работает, и следующий прогон пойдёт параллельно.
    kill_gametest_server
    if [ -n "$HEARTBEAT_PID" ]; then
        kill "$HEARTBEAT_PID" 2>/dev/null || true
        HEARTBEAT_PID=""
        rm -f "$HEARTBEAT_FILE"
    fi
    if [ -n "$MONITOR_PID" ]; then
        kill -TERM "$MONITOR_PID" 2>/dev/null || kill -TERM -- "-$MONITOR_PID" 2>/dev/null || true
        MONITOR_PID=""
    fi
    return 0
}

trap stop_monitor EXIT TERM INT

# ── Анализ результатов unit ──────────────────────────────────────────────────
# Отчёты JUnit разбираются ОДНИМ процессом на все файлы: python, если есть, иначе один awk.
# Прежний разбор делал ~18 fork на каждый XML (подоболочки $(echo | grep | head | grep), awk | sort на файл);
# на Windows (MSYS) при сотнях XML и общей машине (ulimit -u 256, сотни bash) fork кончались, bash повторял
# каждый ~30 с, и test.sh после BUILD SUCCESSFUL висел часами. Вывод разборщика: первая строка
# «тестов падений ошибок», дальше — по строке на упавший тест «класс.имя» (без повторов, в порядке файлов).
# THEMINESPIRIT_PYTHON — путь к python для разбора; none — только awk.
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

# Тот же разбор одним awk: список файлов идёт на stdin (не в argv — на Windows 6000 путей не влезут в командную
# строку), каждый файл читается getline. CDATA (system-out тестов) вырезается: текст в нём — не теги.
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

# Путь к python для разбора (без fork: hash/BASH_CMDS вместо $(command -v)). Заглушку магазина
# (…/WindowsApps/python3.exe) пропускаем: без установленного python она не разбирает, а открывает магазин.
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

# Печатает итог разбора (формат — выше). Python упал или выдал не то — разбираем awk.
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
        # Без -m1: на Windows (MSYS) grep с -m1 рано закрывает pipe, и `| head` теряет контекст -A.
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

# ── Анализ результатов gametest ──────────────────────────────────────────────
analyze_gametest() {
    local log="$1"

    # Провал — по строке-итогу сервера «N required tests failed :(», а не по любому «failed»:
    # имена пачек вроде rest_events_failed содержат «failed» и давали ложный FAIL.
    local failed_line=$(grep -E "[0-9]+ required tests failed" "$log" | tail -1)
    if [ -n "$failed_line" ]; then
        local failed_count=$(printf '%s' "$failed_line" | grep -oE "[0-9]+ required tests failed" | grep -oE '[0-9]+' | head -1)
        echo "FAIL gametest: упало ${failed_count:-неизвестно}"
        grep -A 20 "required tests failed" "$log" | sed -n '2,11p'
        echo "полный лог: $log"
        return 1
    fi

    local passed_line=$(grep -E "All [0-9]+ required tests passed" "$log" | tail -1)

    if [ -n "$passed_line" ]; then
        local count=$(printf '%s' "$passed_line" | grep -oE "[0-9]+ required tests passed" | grep -oE '[0-9]+' | head -1)
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
start_heartbeat

if [ "$MODE" = "unit" ]; then
    rm -f TestResults/unit/xml/TEST-*.xml
    start_monitor "unit"
    # --rerun: XML стёрты выше, а :test UP-TO-DATE/FROM-CACHE (build cache) их не вернёт — итог был бы total=0.

    if command -v setsid >/dev/null 2>&1; then
        setsid "$GRADLEW" test --rerun "${UNIT_TESTS[@]}" >"$LOG_FILE" 2>&1 &
    else
        "$GRADLEW" test --rerun "${UNIT_TESTS[@]}" >"$LOG_FILE" 2>&1 &
    fi
    GRADLE_PID=$!
    echo "$GRADLE_PID" >"$LOG_FILE.gradlepid"
    wait "$GRADLE_PID"
    CODE=$?
    GRADLE_PID=""
    rm -f "$LOG_FILE.gradlepid"

    ELAPSED=$(( $(date +%s) - START_TIME ))

    if [ -f "$LOG_FILE.stopped" ]; then
        echo "стоп по пределу ${UNIT_STOP} с"
        echo "полный лог: $LOG_FILE"
        exit 124
    fi

    analyze_unit "$LOG_FILE"
    exit $?

else
    # GameTest. Фильтр идёт системным свойством Gradle: его читает build.gradle на этапе
    # конфигурации и перекладывает в JVM игры (переменной окружения gradle-демон не увидит).
    GAMETEST_GRADLE_ARGS=()
    if [ ${#GAMETEST_FILTERS[@]} -gt 0 ]; then
        FILTER_VALUE=$(IFS=,; echo "${GAMETEST_FILTERS[*]}")
        GAMETEST_GRADLE_ARGS+=("-Dtheminespirit.gametest.filter=$FILTER_VALUE")
    fi
    if noflock; then
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

    start_monitor "gametest"

    if noflock; then
        if command -v setsid >/dev/null 2>&1; then
            setsid "$GRADLEW" "${GAMETEST_GRADLE_ARGS[@]}" runGameTestServer >"$LOG_FILE" 2>&1 &
        else
            "$GRADLEW" "${GAMETEST_GRADLE_ARGS[@]}" runGameTestServer >"$LOG_FILE" 2>&1 &
        fi
    else
        flock -w "$GAMETEST_WAIT" -E 75 "$GAMETEST_LOCK" bash -c '
            echo "$GAMETEST_TOKEN" >"$GAMETEST_LOCK.token"
            "$@"' bash \
            "$GRADLEW" "${GAMETEST_GRADLE_ARGS[@]}" runGameTestServer >"$LOG_FILE" 2>&1 &
    fi
    GRADLE_PID=$!
    echo "$GRADLE_PID" >"$LOG_FILE.gradlepid"
    wait "$GRADLE_PID"
    CODE=$?
    GRADLE_PID=""
    rm -f "$LOG_FILE.gradlepid"

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
