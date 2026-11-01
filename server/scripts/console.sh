#!/usr/bin/env bash
# Подключается к консоли сервера. Отключиться, не останавливая сервер: Ctrl+B, затем D.
set -euo pipefail
source "$(dirname "$0")/common.sh"

is_running || { echo "Сервер не запущен" >&2; exit 1; }
tmux attach -t "$TMUX_SESSION"
