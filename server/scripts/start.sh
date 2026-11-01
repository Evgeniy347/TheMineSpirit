#!/usr/bin/env bash
# Запускает сервер в tmux-сессии и ждёт готовности.
set -euo pipefail
source "$(dirname "$0")/common.sh"

if is_running; then
    echo "Сервер уже запущен (tmux: $TMUX_SESSION)"
    exit 0
fi

if [ -x "$MOD_DIR/scripts/publish.sh" ] || [ -f "$MOD_DIR/scripts/publish.sh" ]; then
    mkdir -p "$(dirname "$PUBLISH_SYNC_LOG")"
    if ! "$MOD_DIR/scripts/publish.sh" --sync 2>&1 | tee -a "$PUBLISH_SYNC_LOG"; then
        echo "ВНИМАНИЕ: пак клиента и страница не согласованы с $SERVER_DIR/mods/ — см. $PUBLISH_SYNC_LOG" >&2
    fi
else
    echo "ВНИМАНИЕ: нет $MOD_DIR/scripts/publish.sh — пак клиента и страница не проверены" >&2
fi

cd "$SERVER_DIR"
offset=$(log_size)
tmux new-session -d -s "$TMUX_SESSION" -c "$SERVER_DIR" "./run.sh nogui"

# При старте latest.log пересоздаётся, поэтому смещение сбрасываем.
sleep 3
[ "$(log_size)" -lt "$offset" ] && offset=0
if wait_for_log 'Done (' "$offset" 300; then
    echo "Сервер запущен. Консоль: scripts/console.sh"
else
    echo "Сервер не сообщил о готовности за 5 минут, проверь logs/latest.log" >&2
    exit 1
fi
