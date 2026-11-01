#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE="${LIVE_DEV_BASE:-$(dirname "$REPO")/.live-server}"
RUN_ROOT="$REPO/run-live"
SSH="${LIVE_DEV_SSH:-domovoy@192.168.0.147}"
SERVER_DIR="${LIVE_DEV_SERVER_DIR:-/opt/minecraft/server}"
BACKUPS="${LIVE_DEV_BACKUPS:-/opt/minecraft/backups/world}"
MAX_COPIES="${LIVE_DEV_MAX:-4}"
OWNER="${LIVE_DEV_OWNER_PID:-$PPID}"
TTL="${LIVE_DEV_LEASE_TTL:-180}"
LIVE_DEBUG="$REPO/scripts/live-debug.sh"
JBR=""
NAME="$(basename "$REPO" | tr -cd 'A-Za-z0-9._-')"
GAME_PORT=""
REST_PORT=""
JDWP_PORT=""

die() {
    echo "$*" >&2
    exit 1
}

say() {
    echo "$*"
}

win_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s' "$1"
    fi
}

py() {
    if command -v python >/dev/null 2>&1; then
        python "$@"
    elif command -v python3 >/dev/null 2>&1; then
        python3 "$@"
    else
        die "нет python: нужен для свободных портов"
    fi
}

native_alive() {
    local pid="$1"
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    powershell.exe -NoProfile -NonInteractive -Command \
        "if (Get-Process -Id $pid -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" \
        >/dev/null 2>&1
}

msys_alive() {
    local pid="$1"
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    kill -0 "$pid" 2>/dev/null
}

free_ports() {
    local ports
    ports="$(py - <<'PY'
import socket
socks = []
for _ in range(3):
    item = socket.socket()
    item.bind(("127.0.0.1", 0))
    socks.append(item)
print(" ".join(str(item.getsockname()[1]) for item in socks))
PY
)"
    read -r GAME_PORT REST_PORT JDWP_PORT <<<"$ports"
    [ -n "$GAME_PORT" ] && [ -n "$REST_PORT" ] && [ -n "$JDWP_PORT" ] || die "не удалось выбрать порты"
}

find_jbr() {
    JBR="$(ls -d "$BASE"/jbrsdk-*/bin/java.exe 2>/dev/null | head -n 1)"
    if [ -z "$JBR" ]; then
        JBR="$(ls -d "$BASE"/jbrsdk-*/bin/java 2>/dev/null | head -n 1)"
    fi
}

ensure_base() {
    mkdir -p "$BASE"
    local locked=0
    if ! mkdir "$BASE/.setup" 2>/dev/null; then
        locked=1
        say "база готовится другим процессом, ждём…"
        local i
        for i in $(seq 1 900); do
            [ -f "$BASE/.ready" ] && break
            sleep 2
        done
    fi
    if [ "$locked" = 1 ]; then
        [ -f "$BASE/.ready" ] || die "база не доготовилась: $BASE"
        find_jbr
        [ -n "$JBR" ] || die "в базе нет JBR SDK: $BASE"
        return 0
    fi
    trap 'rmdir "$BASE/.setup" 2>/dev/null || true' EXIT
    if [ ! -d "$BASE/libraries" ] || [ ! -d "$BASE/mods" ]; then
        say "копирую библиотеки и моды с $SSH (один раз)"
        ssh "$SSH" "cd $SERVER_DIR && tar -cf - libraries run.sh eula.txt mods" \
            | tar -xf - -C "$BASE" || die "не удалось скопировать базу сервера"
    fi
    find_jbr
    if [ -z "$JBR" ]; then
        say "качаю JBR SDK 21 (≈280 МБ, один раз; шрифт прогресса ниже)"
        py "$SCRIPT_DIR/dev-server/fetch-jbr.py" "$(win_path "$BASE")" || die "JBR SDK не скачался"
        tar -xzf "$BASE/jbrsdk.tar.gz" -C "$BASE" || die "JBR SDK не распаковался"
        rm -f "$BASE/jbrsdk.tar.gz"
        find_jbr
    fi
    [ -n "$JBR" ] || die "в базе нет JBR SDK: $BASE"
    date >"$BASE/.ready"
    rmdir "$BASE/.setup" 2>/dev/null || true
    trap - EXIT
}

copy_path() {
    printf '%s/%s' "$RUN_ROOT" "$1"
}

lease_field() {
    local file="$1" index="$2"
    [ -f "$file" ] || return 1
    IFS='|' read -r -a fields <"$file"
    printf '%s' "${fields[$index]:-}"
}

count_live_copies() {
    local lease dir pid count=0
    for lease in "$(dirname "$REPO")"/*/run-live/*/owner.lease; do
        [ -f "$lease" ] || continue
        dir="$(dirname "$lease")"
        pid="$(cat "$dir/pid" 2>/dev/null || true)"
        if native_alive "$pid"; then
            count=$((count + 1))
        fi
    done
    printf '%s' "$count"
}

stop_copy() {
    local name="$1"
    local dir
    dir="$(copy_path "$name")"
    local hb=""
    local pid=""
    local shellpid=""
    if [ -f "$dir/heartbeat.pid" ]; then
        hb="$(cat "$dir/heartbeat.pid" 2>/dev/null || true)"
    fi
    if [ -n "$hb" ]; then
        kill "$hb" 2>/dev/null || true
    fi
    if [ -f "$dir/pid" ]; then
        pid="$(cat "$dir/pid" 2>/dev/null || true)"
    fi
    if [ -z "$pid" ]; then
        pid="$(find_java_pid "$name")"
    fi
    if native_alive "$pid"; then
        powershell.exe -NoProfile -NonInteractive -Command \
            "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
        local i
        for i in $(seq 1 20); do
            native_alive "$pid" || break
            sleep 1
        done
    fi
    if [ -f "$dir/shell.pid" ]; then
        shellpid="$(cat "$dir/shell.pid" 2>/dev/null || true)"
    fi
    if native_alive "$shellpid"; then
        powershell.exe -NoProfile -NonInteractive -Command \
            "Stop-Process -Id $shellpid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
    fi
    local i
    for i in $(seq 1 15); do
        rm -rf "$dir" 2>/dev/null && break
        sleep 1
    done
    rm -f "$RUN_ROOT/$name.lock"
    if [ -d "$dir" ]; then
        echo "не удалось удалить $dir" >&2
        return 1
    fi
}

write_lease() {
    local dir="$1" name="$2" pid="$3"
    local now expiry
    now="$(date +%s)"
    expiry=$((now + TTL))
    printf '%s|%s|%s|%s|%s|%s\n' "$REPO" "$OWNER" "$expiry" "$name" "$pid" "$dir" >"$dir/owner.lease"
}

start_heartbeat() {
    local dir="$1" name="$2"
    local java_pid="$3"
    cat >"$dir/heartbeat.sh" <<HB
set -u
COPY='$(win_path "$dir" | sed "s/'/'\\\\''/g")'
COPY_POSIX='$dir'
OWNER='$OWNER'
TREE='$REPO'
NAME='$name'
while [ -d "\$TREE" ] && kill -0 "\$OWNER" 2>/dev/null; do
    now=\$(date +%s)
    jpid=\$(cat "\$COPY_POSIX/pid" 2>/dev/null || true)
    printf '%s|%s|%s|%s|%s|%s\n' "\$TREE" "\$OWNER" "\$((now + $TTL))" "\$NAME" "\$jpid" "\$COPY_POSIX" >"\$COPY_POSIX/owner.lease.tmp" 2>/dev/null || exit 0
    mv "\$COPY_POSIX/owner.lease.tmp" "\$COPY_POSIX/owner.lease" 2>/dev/null || exit 0
    sleep 20
done
pid=\$(cat "\$COPY_POSIX/pid" 2>/dev/null || true)
if [ -z "\$pid" ]; then
    pid=\$(powershell.exe -NoProfile -NonInteractive -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*livedebug.copy=$name*' } | Select-Object -First 1 -ExpandProperty ProcessId" 2>/dev/null | tr -d '\r' | head -n 1)
fi
if [ -n "\$pid" ]; then
    powershell.exe -NoProfile -NonInteractive -Command "Stop-Process -Id \$pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
fi
sleep 2
powershell.exe -NoProfile -NonInteractive -Command "Start-Process -WindowStyle Hidden -FilePath cmd.exe -ArgumentList '/c','timeout /t 3 /nobreak >nul & rmdir /s /q \"\$COPY\"'" >/dev/null 2>&1 || true
HB
    chmod +x "$dir/heartbeat.sh"
    nohup bash "$dir/heartbeat.sh" >"$dir/logs/heartbeat.log" 2>&1 &
    echo $! >"$dir/heartbeat.pid"
}

patch_copy() {
    local dir="$1"
    sed -i \
        -e "s/^server-port=.*/server-port=$GAME_PORT/" \
        -e "s/^server-ip=.*/server-ip=127.0.0.1/" \
        -e "s/^max-tick-time=.*/max-tick-time=-1/" \
        -e "s/^difficulty=.*/difficulty=peaceful/" \
        -e "s/^motd=.*/motd=live-$NAME/" \
        -e "s/^max-players=.*/max-players=5/" \
        -e "s/^enable-rcon=.*/enable-rcon=false/" \
        -e "s/^enable-query=.*/enable-query=false/" \
        -e "s/^level-name=.*/level-name=world/" \
        "$dir/server.properties"
    sed -i \
        -e "s/^[[:space:]]*port = .*/port = $REST_PORT/" \
        -e "s/^[[:space:]]*bindAddress = .*/bindAddress = \"127.0.0.1\"/" \
        "$dir/config/theminespirit-rest.toml"
}

fetch_world() {
    local dir="$1"
    local snapshot
    snapshot="$(ssh "$SSH" "ls -1t $BACKUPS/world-*.tar.zst | head -n 1")"
    [ -n "$snapshot" ] || die "нет снимков мира в $BACKUPS"
    say "мир: $(basename "$snapshot")"
    ssh "$SSH" "tmp=\$(mktemp -d /opt/minecraft/tmp/livedev.XXXXXX) || exit 1; tar --zstd -xf '$snapshot' -C \"\$tmp\" || { rm -rf \"\$tmp\"; exit 1; }; tar -cf - -C \"\$tmp\" world; rc=\$?; rm -rf \"\$tmp\"; exit \$rc" \
        | tar -xf - -C "$dir" || die "мир не развернулся"
    [ -f "$dir/world/level.dat" ] || die "в копии нет world/level.dat"
}

write_jvm_args() {
    local dir="$1" name="$2"
    cat >"$dir/user_jvm_args.txt" <<ARGS
-Xms1G
-Xmx4G
-Dlivedebug.copy=$name
-Dlivedebug.tree=$REPO
-XX:+UnlockExperimentalVMOptions
-XX:+AllowEnhancedClassRedefinition
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:$JDWP_PORT
--add-modules=jdk.jshell,jdk.jdi
ARGS
    cat >"$dir/start-live.bat" <<BAT
@echo off
cd /d "%~dp0"
"$(win_path "$JBR")" -Dlivedebug.copy=$name @user_jvm_args.txt @libraries\\net\\neoforged\\neoforge\\21.1.257\\win_args.txt nogui > logs\\console.log 2>&1
BAT
}

find_java_pid() {
    local name="$1"
    powershell.exe -NoProfile -NonInteractive -Command \
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*livedebug.copy=$name*' } | Select-Object -First 1 -ExpandProperty ProcessId" \
        2>/dev/null | tr -d '\r' | head -n 1
}

wait_ready() {
    local dir="$1"
    local i
    for i in $(seq 1 300); do
        if grep -q 'Done (' "$dir/logs/latest.log" 2>/dev/null; then
            return 0
        fi
        if [ -f "$dir/logs/console.log" ] && grep -qE 'Exception in thread|Failed to start the minecraft server|Mod loading has failed' "$dir/logs/console.log" 2>/dev/null; then
            return 1
        fi
        sleep 1
    done
    return 1
}

write_debug_env() {
    local dir="$1" name="$2" pid="$3"
    local jars
    local classes
    local mod
    jars="$(win_path "$REPO/build/classes/java/main")"
    for mod in "$dir"/mods/*.jar; do
        [ -f "$mod" ] || continue
        jars="$jars;$(win_path "$mod")"
    done
    jars="$jars;$(win_path "$BASE/libraries/net/neoforged/neoforge/21.1.257/neoforge-21.1.257-server.jar")"
    jars="$jars;$(win_path "$BASE/libraries/net/neoforged/neoforge/21.1.257/neoforge-21.1.257-universal.jar")"
    local srg
    srg="$(ls "$BASE"/libraries/net/minecraft/server/1.21.1-*/server-*-srg.jar 2>/dev/null | head -n 1)"
    if [ -n "$srg" ]; then
        jars="$jars;$(win_path "$srg")"
    fi
    classes="$(win_path "$REPO/build/classes/java/main")"
    {
        printf "LIVE_DEBUG_HOST=127.0.0.1\n"
        printf "LIVE_DEBUG_PORT=%s\n" "$JDWP_PORT"
        printf "LIVE_DEBUG_REST=http://127.0.0.1:%s\n" "$REST_PORT"
        printf "LIVE_DEBUG_AGENT=live-%s\n" "$name"
        printf "LIVE_DEBUG_TREE='%s'\n" "$REPO"
        printf "LIVE_DEBUG_NAME='%s'\n" "$name"
        printf "LIVE_DEBUG_COPY='%s'\n" "$dir"
        printf "LIVE_DEBUG_PID=%s\n" "$pid"
        printf "LIVE_DEBUG_CLASSES='%s'\n" "$classes"
        printf "LIVE_DEBUG_JARS='%s'\n" "$jars"
    } >"$dir/debug.env"
}

start_copy() {
    local name="$1"
    local dir
    dir="$(copy_path "$name")"
    say "сборка мода"
    mkdir -p "$REPO/build/live-debug"
    if ! (cd "$REPO" && ./gradlew -q assemble > "$REPO/build/live-debug/assemble.log" 2>&1); then
        tail -20 "$REPO/build/live-debug/assemble.log" >&2
        die "assemble упал: копия не поднята"
    fi
    local jar
    jar="$(ls -1t "$REPO"/build/libs/theminespirit-*.jar 2>/dev/null | grep -vE -- '-(sources|javadoc)\.jar$' | head -n 1 || true)"
    [ -n "$jar" ] || die "нет собранного мода: ./gradlew assemble"
    say "сборка: $(basename "$jar")"
    mkdir -p "$dir/logs" "$dir/mods"
    cp -r "$BASE/libraries" "$dir/libraries"
    cp "$BASE/run.sh" "$BASE/eula.txt" "$dir/"
    local mod
    for mod in "$BASE"/mods/*.jar; do
        [ -f "$mod" ] || continue
        case "$(basename "$mod")" in
            theminespirit-*) continue ;;
        esac
        cp "$mod" "$dir/mods/"
    done
    cp "$jar" "$dir/mods/"
    say "конфиги и мир"
    ssh "$SSH" "cd $SERVER_DIR && tar -cf - config server.properties" | tar -xf - -C "$dir"
    fetch_world "$dir"
    free_ports
    patch_copy "$dir"
    write_jvm_args "$dir" "$name"
    say "порты: игра $GAME_PORT, REST $REST_PORT, JDWP $JDWP_PORT"
    write_lease "$dir" "$name" 0
    start_heartbeat "$dir" "$name" 0
    local shellpid
    shellpid="$(powershell.exe -NoProfile -NonInteractive -Command \
        "Start-Process -WindowStyle Hidden -FilePath 'cmd.exe' -ArgumentList '/c','$(win_path "$dir/start-live.bat")' -WorkingDirectory '$(win_path "$dir")' -PassThru | Select-Object -ExpandProperty Id" \
        2>/dev/null | tr -d '\r' | head -n 1)"
    [ -n "$shellpid" ] || die "сервер копии не запустился"
    echo "$shellpid" >"$dir/shell.pid"
    local pid=""
    local i
    for i in $(seq 1 60); do
        pid="$(find_java_pid "$name")"
        [ -n "$pid" ] && break
        sleep 1
    done
    [ -n "$pid" ] || {
        ls -la "$dir" "$dir/logs" 2>/dev/null || true
        cat "$dir/start-live.bat" 2>/dev/null || true
        tail -n 30 "$dir/logs/console.log" 2>/dev/null || true
        die "java копии не поднялась"
    }
    echo "$pid" >"$dir/pid"
    write_lease "$dir" "$name" "$pid"
    write_debug_env "$dir" "$name" "$pid"
    say "жду готовности копии (PID $pid)…"
    if ! wait_ready "$dir"; then
        tail -n 40 "$dir/logs/latest.log" 2>/dev/null || true
        tail -n 20 "$dir/logs/console.log" 2>/dev/null || true
        die "копия не поднялась за 5 минут"
    fi
    say "forceload"
    bash "$LIVE_DEBUG" --run "$dir" init >/dev/null 2>&1 || die "eval-мост копии не поднялся"
    local result
    result="$(bash "$LIVE_DEBUG" --run "$dir" eval 'onServer(() -> { server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "forceload add -270 -587 -70 -387"); return "forceload ok"; })' 2>&1)" \
        || die "forceload не выполнился: $result"
    say "$result"
}

up() {
    local name="$1"
    [ "$(uname -s | cut -c1-5)" != "MINGW" ] && die "копии сервера запускаются на ПК агента (Windows); сервер domovoy не используется"
    ensure_base
    [ -n "$OWNER" ] || die "не задан владелец копии"
    case "$OWNER" in *[!0-9]*) die "LIVE_DEV_OWNER_PID должен быть числом" ;; esac
    msys_alive "$OWNER" || die "процесс агента $OWNER не жив: копия не будет запущена"
    if [ -d "$RUN_ROOT/$name.lock" ]; then
        die "копия $name уже поднимается (есть $RUN_ROOT/$name.lock)"
    fi
    mkdir -p "$RUN_ROOT"
    if ! mkdir "$RUN_ROOT/.up-$name.lock" 2>/dev/null; then
        die "копия $name уже поднимается (есть $RUN_ROOT/.up-$name.lock)"
    fi
    local dir
    dir="$(copy_path "$name")"
    local done_flag=0
    trap 'rmdir "'"$RUN_ROOT/.up-$name.lock"'" 2>/dev/null || true; if [ "${done_flag:-0}" = 0 ] && [ "${LIVE_DEV_KEEP_FAILED:-0}" != 1 ]; then stop_copy "'"$name"'" || true; fi' EXIT
    stop_copy "$name" || true
    local live
    live="$(count_live_copies)"
    if [ "$live" -ge "$MAX_COPIES" ]; then
        die "уже работает копий: $live, предел $MAX_COPIES; сначала scripts/dev-server.sh down"
    fi
    start_copy "$name"
    done_flag=1
    rmdir "$RUN_ROOT/.up-$name.lock" 2>/dev/null || true
    trap - EXIT
    say "копия $name: $(copy_path "$name")"
    say "дальше: scripts/live-debug.sh --run $(copy_path "$name") eval '...'"
    say "статус: scripts/dev-server.sh status $name; остановка: scripts/dev-server.sh down $name"
}

down() {
    local name="${1:-$NAME}"
    if [ "$name" = "--tree" ]; then
        local lease dir live_name
        for lease in "$RUN_ROOT"/*/owner.lease; do
            [ -f "$lease" ] || continue
            dir="$(dirname "$lease")"
            live_name="$(basename "$dir")"
            if [ "$(lease_field "$lease" 0)" = "$REPO" ]; then
                say "гашу копию $live_name"
                stop_copy "$live_name" || true
            fi
        done
        return 0
    fi
    stop_copy "$name"
    say "копия $name погашена"
}

status_one() {
    local name="$1"
    local dir
    dir="$(copy_path "$name")"
    if [ ! -f "$dir/debug.env" ]; then
        echo "копии $name нет"
        return 1
    fi
    local pid
    pid="$(cat "$dir/pid" 2>/dev/null || true)"
    echo "копия: $name"
    echo "каталог: $dir"
    echo "PID: ${pid:-нет} ($(native_alive "$pid" && echo жив || echo мёртв))"
    echo "порты: $(grep -E '^LIVE_DEBUG_(PORT|REST)=' "$dir/debug.env" | tr '\n' ' ')"
    echo "аренда: $(cat "$dir/owner.lease" 2>/dev/null || echo нет)"
    local rest
    rest="$(grep '^LIVE_DEBUG_REST=' "$dir/debug.env" | cut -d= -f2- | tr -d "'")"
    curl -s --max-time 3 "$rest/v1/bots" 2>/dev/null | head -c 160 || true
    echo
}

list_copies() {
    local lease dir name
    for lease in "$RUN_ROOT"/*/owner.lease; do
        [ -f "$lease" ] || continue
        dir="$(dirname "$lease")"
        name="$(basename "$dir")"
        status_one "$name"
    done
}

case "${1:-help}" in
    up)
        shift
        [ "${1:-}" = "--name" ] && { NAME="${2:-}"; shift 2; }
        up "$NAME"
        ;;
    down)
        shift
        down "${1:-$NAME}"
        ;;
    status)
        shift
        status_one "${1:-$NAME}"
        ;;
    list)
        list_copies
        ;;
    setup)
        ensure_base
        say "база: $BASE"
        ;;
    help|-h|--help)
        cat <<'USAGE'
scripts/dev-server.sh up [--name ИМЯ]
scripts/dev-server.sh down [ИМЯ|--tree]
scripts/dev-server.sh status [ИМЯ]
scripts/dev-server.sh list
scripts/dev-server.sh setup

up       поднимает копию сервера в <дерево>/run-live/<имя>: свежий снимок мира,
         конфиги, jar своей сборки, свободные порты, JBR с AllowEnhancedClassRedefinition,
         JDWP, REST, peaceful, forceload. База (libraries, mods, JBR SDK) — в .live-server
         рядом с деревьями; скачивается один раз.
down     гасит копию по PID и удаляет её каталог; --tree — все копии этого дерева.
status   PID, порты, аренда, краткий ответ /v1/bots.
list     все копии дерева.
setup    только подготовка базы.

Переменные: LIVE_DEV_OWNER_PID (PID агента; ставит opencode-task.sh), LIVE_DEV_MAX (4),
LIVE_DEV_BASE, LIVE_DEV_SSH, LIVE_DEV_NAME, LIVE_DEV_LEASE_TTL (180 с).
USAGE
        ;;
    *)
        die "неизвестная команда: $1"
        ;;
esac
