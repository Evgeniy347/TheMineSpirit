#!/usr/bin/env bash
# Общие настройки для скриптов управления сервером.

SERVER_DIR="${SERVER_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MOD_DIR="${MOD_DIR:-/opt/minecraft/the-mine-spirit}"
TMUX_SESSION="${TMUX_SESSION:-minecraft}"
NEOFORGE_VERSION="21.1.257"
PUBLISH_SYNC_LOG="${PUBLISH_SYNC_LOG:-/opt/minecraft/logs/publish-sync.log}"
TMP_DIR="${TMP_DIR:-/opt/minecraft/tmp}"
STOP_MARKER="${STOP_MARKER:-$TMP_DIR/server-stop.lock}"

is_running() {
    tmux has-session -t "$TMUX_SESSION" 2>/dev/null
}

# Отправляет команду в консоль сервера. Замки снимка (fd 8/9) в tmux не наследуются: иначе замок
# держится вечно после выхода скрипта, и следующий снимок видит «занято».
send_command() {
    if ! tmux send-keys -t "$TMUX_SESSION" "$1" Enter 8>&- 9>&-; then
        echo "не отправить «$1» в консоль tmux «$TMUX_SESSION»: сессия пропала" >&2
        return 1
    fi
}

# Ждёт появления строки в логе после указанной позиции (в байтах).
# Без конвейера: grep -q закрывает stdin раньше, tail получает SIGPIPE, и под
# pipefail ожидание ложно считается проваленным.
wait_for_log() {
    local pattern="$1" offset="$2" timeout="${3:-60}"
    local file="$SERVER_DIR/logs/latest.log"
    local chunk="" size
    for _ in $(seq 1 "$timeout"); do
        if [ -f "$file" ]; then
            # lib: игра пересоздаёт latest.log не мгновенно: под лагом ротация случается позже первых секунд
            # lib: старта. Проверка «размер упал» один раз в начале пропускала новую строку готовности —
            # lib: старый offset больше нового лога, и ожидание «Done (» истекало при уже живом сервере.
            size="$(log_size)"
            if [ "$size" -lt "$offset" ]; then
                offset=0
            fi
            chunk="$(tail -c +"$((offset + 1))" "$file" 2>/dev/null || true)"
            case "$chunk" in
                *"$pattern"*) return 0 ;;
            esac
        fi
        sleep 1
    done
    return 1
}

log_size() {
    stat -c %s "$SERVER_DIR/logs/latest.log" 2>/dev/null || echo 0
}
