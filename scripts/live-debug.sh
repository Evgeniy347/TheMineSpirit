#!/usr/bin/env bash
set -euo pipefail

HOST="${LIVE_DEBUG_HOST:-192.168.0.147}"
SSH="${LIVE_DEBUG_SSH:-domovoy@$HOST}"
PORT="${LIVE_DEBUG_PORT:-5005}"
REST="${LIVE_DEBUG_REST:-http://$HOST:8765}"
AGENT="${LIVE_DEBUG_AGENT:-${USER:-agent}}"
TTL="${LIVE_DEBUG_TTL:-14400}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO/scripts/live-debug"
OUT="$REPO/build/live-debug/classes"
SERVER_DIR="/opt/minecraft/server"
LOCAL=0
RUN_DIR=""

if ! [[ "$AGENT" =~ ^[A-Za-z0-9_.@-]+$ ]]; then
    echo "LIVE_DEBUG_AGENT содержит недопустимые символы: $AGENT" >&2
    exit 2
fi
if ! [[ "$TTL" =~ ^[0-9]+$ ]]; then
    echo "LIVE_DEBUG_TTL должен быть числом секунд" >&2
    exit 2
fi

die() {
    echo "$*" >&2
    exit 1
}

lock_remote() {
    ssh "$SSH" bash -s -- "$1" "$AGENT" "$TTL" <<'REMOTE'
set -euo pipefail
mode="$1"
holder="$2"
ttl="$3"
lock=/opt/minecraft/debug.lock
lease=/opt/minecraft/debug.lease
exec 9>>"$lock"
flock -w 5 9 || { echo "замок: не дождался flock"; exit 1; }
now=$(date +%s)
held=""
until=0
since=0
if [ -f "$lease" ]; then
    IFS='|' read -r held until since < "$lease" || true
fi
expired=1
if [ -n "$held" ] && [ "${until:-0}" -gt "$now" ]; then
    expired=0
fi
show_until() { date -d "@$1" '+%H:%M:%S %d.%m'; }
case "$mode" in
    acquire)
        if [ -n "$held" ] && [ "$held" != "$holder" ] && [ "$expired" -eq 0 ]; then
            echo "замок занят: $held до $(show_until "$until")"
            exit 2
        fi
        printf '%s|%s|%s\n' "$holder" "$((now + ttl))" "$now" > "$lease"
        echo "замок взят: $holder до $(show_until "$((now + ttl))")"
        ;;
    require)
        if [ -z "$held" ] || [ "$held" != "$holder" ] || [ "$expired" -eq 1 ]; then
            echo "замок не ваш (держите: ${held:-никто}); возьмите: scripts/live-debug.sh lock"
            exit 3
        fi
        printf '%s|%s|%s\n' "$holder" "$((now + ttl))" "${since:-$now}" > "$lease"
        ;;
    release)
        if [ -n "$held" ] && [ "$held" != "$holder" ] && [ "$expired" -eq 0 ]; then
            echo "замок держит $held; снять может только он (или истечение $ttl с)"
            exit 4
        fi
        rm -f "$lease"
        echo "замок снят: ${held:-свободен}"
        ;;
    status)
        if [ -z "$held" ]; then
            echo "замок свободен"
        elif [ "$expired" -eq 1 ]; then
            echo "замок истёк: $held (был до $(show_until "$until"))"
        else
            echo "замок: $held до $(show_until "$until")"
        fi
        ;;
esac
REMOTE
}

require_lock() {
    lock_remote require >/dev/null
}

need_lock() {
    [ "$LOCAL" = 1 ] && return 0
    require_lock || {
        lock_remote status || true
        exit 3
    }
}

build_tool() {
    local stamp="$OUT/.stamp"
    if [ ! -f "$stamp" ] || [ -n "$(find "$SRC" -name '*.java' -newer "$stamp" 2>/dev/null)" ]; then
        rm -rf "$OUT"
        mkdir -p "$OUT"
        javac --add-modules jdk.jshell,jdk.jdi -d "$OUT" "$SRC/LiveShell.java" "$SRC/LiveDebug.java" \
            || die "клиент живой отладки не собрался"
        touch "$stamp"
    fi
}

win_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s' "$1"
    fi
}

classes_arg() {
    if [ -n "${LIVE_DEBUG_CLASSES:-}" ]; then
        win_path "$LIVE_DEBUG_CLASSES"
    else
        win_path "$REPO/build/classes/java/main"
    fi
}

run_client() {
    build_tool
    LIVE_DEBUG_HOST="$HOST" \
    LIVE_DEBUG_PORT="$PORT" \
    LIVE_DEBUG_REST="$REST" \
    LIVE_DEBUG_HELPER="$(win_path "$OUT/livedebug")" \
    LIVE_DEBUG_CLASSES="$(classes_arg)" \
    LIVE_DEBUG_TIMEOUT="${LIVE_DEBUG_TIMEOUT:-120}" \
    LIVE_DEBUG_JARS_FILE="$(win_path "$REPO/build/live-debug/server-classpath.txt")" \
        java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 --add-modules jdk.jdi -cp "$(win_path "$OUT")" LiveDebug "$@"
}

prepare_classpath() {
    mkdir -p "$REPO/build/live-debug"
    if [ "$LOCAL" = 1 ]; then
        printf '%s' "${LIVE_DEBUG_JARS:-}" > "$REPO/build/live-debug/server-classpath.txt"
    else
        server_classpath > "$REPO/build/live-debug/server-classpath.txt"
    fi
}

server_classpath() {
    ssh "$SSH" "cd $SERVER_DIR && for f in mods/*.jar \
        libraries/net/neoforged/neoforge/21.1.257/neoforge-21.1.257-server.jar \
        libraries/net/neoforged/neoforge/21.1.257/neoforge-21.1.257-universal.jar \
        libraries/net/minecraft/server/1.21.1-*/server-*-srg.jar; do \
        [ -f \"\$f\" ] && printf '/opt/minecraft/server/%s:' \"\$f\"; done" \
        | sed 's/:$//'
}

if [ "${1:-}" = "--run" ]; then
    RUN_DIR="${2:-}"
    [ -n "$RUN_DIR" ] || die "укажите каталог копии: live-debug.sh --run <каталог> <команда>"
    shift 2
    [ -f "$RUN_DIR/debug.env" ] || die "нет $RUN_DIR/debug.env"
    set -a
    . "$RUN_DIR/debug.env"
    set +a
    LOCAL=1
    REPO="${LIVE_DEBUG_TREE:-$REPO}"
    SRC="$REPO/scripts/live-debug"
    OUT="$REPO/build/live-debug/classes"
    HOST="${LIVE_DEBUG_HOST:-127.0.0.1}"
    PORT="${LIVE_DEBUG_PORT:-5005}"
    REST="${LIVE_DEBUG_REST:-http://127.0.0.1:8765}"
    AGENT="${LIVE_DEBUG_AGENT:-live}"
fi

command="${1:-help}"
shift || true

case "$command" in
    lock)
        if [ "$LOCAL" = 1 ]; then
            echo "копия принадлежит дереву: замок основного сервера не нужен"
        else
            lock_remote acquire
        fi
        ;;
    unlock)
        if [ "$LOCAL" = 1 ]; then
            echo "у копии нет отдельного замка"
        else
            lock_remote release
        fi
        ;;
    status)
        if [ "$LOCAL" = 1 ]; then
            echo "копия: ${LIVE_DEBUG_COPY:-$RUN_DIR}"
            echo "PID: ${LIVE_DEBUG_PID:-нет}; JDWP: $PORT; REST: $REST"
            curl -s --max-time 3 "$REST/v1/bots" 2>/dev/null | head -c 200 || true
            echo
            exit 0
        fi
        lock_remote status
        ssh "$SSH" "ss -ltn | grep -E ':(25565|8765|$PORT)\b' || true" || true
        curl -s --max-time 3 "$REST/v1/bots" 2>/dev/null | head -c 160 || true
        echo
        ;;
    start)
        [ "$LOCAL" = 1 ] && die "для копии: scripts/dev-server.sh up"
        need_lock
        ssh "$SSH" "cd $SERVER_DIR && scripts/start.sh && tmux send-keys -t minecraft 'difficulty peaceful' Enter \
            && tmux send-keys -t minecraft 'forceload add -270 -587 -70 -387' Enter" \
            || die "сервер не запустился"
        sleep 2
        if ! ssh "$SSH" "ss -ltn | grep -q ':$PORT '"; then
            die "JDWP не слушает $PORT: добавьте в $SERVER_DIR/user_jvm_args.txt строку -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$PORT и перезапустите"
        fi
        echo "сервер и JDWP на $PORT готовы"
        ;;
    stop)
        [ "$LOCAL" = 1 ] && die "для копии: scripts/dev-server.sh down"
        if [ "${1:-}" = "--server" ]; then
            need_lock
            ssh "$SSH" "cd $SERVER_DIR && scripts/stop.sh"
        else
            lock_remote release
            echo "сервер оставлен работать; остановить его: scripts/live-debug.sh stop --server"
        fi
        ;;
    classes)
        run_client classes "$@"
        ;;
    init)
        need_lock
        prepare_classpath
        run_client init
        ;;
    eval)
        need_lock
        prepare_classpath
        run_client eval "$@"
        ;;
    hotswap)
        need_lock
        build=1
        names=()
        for argument in "$@"; do
            case "$argument" in
                --no-build) build=0 ;;
                *) names+=("$argument") ;;
            esac
        done
        if [ "$build" -eq 1 ]; then
            mkdir -p "$REPO/build/live-debug"
            if ! (cd "$REPO" && ./gradlew -q compileJava > "$REPO/build/live-debug/compile.log" 2>&1); then
                tail -20 "$REPO/build/live-debug/compile.log" >&2
                die "compileJava упал: hotswap не отправлен (лог: build/live-debug/compile.log)"
            fi
        fi
        [ "${#names[@]}" -gt 0 ] || die "укажите класс: hotswap com.theminespirit.neoforge.Example"
        run_client hotswap "${names[@]}"
        ;;
    break)
        need_lock
        run_client break "$@"
        ;;
    help|-h|--help)
        cat <<'USAGE'
scripts/live-debug.sh <команда> [аргументы]

  lock                    взять замок одного агента (LIVE_DEBUG_AGENT, по умолчанию $USER)
  unlock                  снять свой замок (или истёкший)
  stop [--server]         снять замок; --server — остановить сервер
  start                   поднять сервер с JDWP и вернуть peaceful/forceload
  status                  замок, порты, /v1/bots
  init                    внедрить JShell-мост в процесс сервера
  --run <каталог> <cmd>   команда против копии сервера (debug.env из dev-server.sh)
  eval '<код>'            выполнить фрагмент в моде; @файл или - (stdin)
  hotswap <класс> [...]   compileJava и redefine классов на живом сервере
  break <класс:строка> [-i] [--thread имя]   брейкпоинт; -i — REPL до cont
  classes [подстрока]     загруженные классы мода

Один агент за раз: остальные команды отказывают, пока замок держит другой.
Переменные: LIVE_DEBUG_AGENT, LIVE_DEBUG_HOST, LIVE_DEBUG_PORT, LIVE_DEBUG_REST,
            LIVE_DEBUG_TTL, LIVE_DEBUG_TIMEOUT.
USAGE
        ;;
    *)
        die "неизвестная команда: $command (см. help)"
        ;;
esac
