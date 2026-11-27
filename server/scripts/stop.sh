#!/usr/bin/env bash
# Корректно останавливает сервер (с сохранением мира).
set -euo pipefail
source "$(dirname "$0")/common.sh"

# lib: мир в 1 ГиБ сохраняется дольше двух минут, поэтому ожидание выхода снимается переменной.
STOP_TIMEOUT="${STOP_TIMEOUT:-600}"

mkdir -p "$TMP_DIR"
printf '%s\n' "$$" >"$STOP_MARKER"
trap 'rm -f "$STOP_MARKER"' EXIT

if ! is_running; then
    echo "Сервер не запущен"
    exit 0
fi

offset="$(log_size)"
send_command "stop"
# lib: консоль сервера теряет команду, если в её ввод попал незакрытый текст; подтверждение
# остановки в логе отличает «мир ещё сохраняется» от «команда не дошла» и даёт шанс повторить.
if ! wait_for_log "Stopping server" "$offset" 30; then
    echo "консоль не подтвердила «stop» — повторяю отправку" >&2
    send_command "stop"
    if ! wait_for_log "Stopping server" "$offset" 30; then
        echo "сервер не принимает «stop»: проверьте консоль tmux «$TMUX_SESSION» (scripts/console.sh)" >&2
        exit 1
    fi
fi

for _ in $(seq 1 "$STOP_TIMEOUT"); do
    is_running || { echo "Сервер остановлен"; exit 0; }
    sleep 1
done
echo "Сервер не остановился за $STOP_TIMEOUT секунд" >&2
exit 1
