#!/usr/bin/env bash
# Корректно останавливает сервер (с сохранением мира).
set -euo pipefail
source "$(dirname "$0")/common.sh"

if ! is_running; then
    echo "Сервер не запущен"
    exit 0
fi

send_command "stop"
for _ in $(seq 1 120); do
    is_running || { echo "Сервер остановлен"; exit 0; }
    sleep 1
done
echo "Сервер не остановился за 2 минуты" >&2
exit 1
