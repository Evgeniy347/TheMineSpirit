#!/usr/bin/env bash
# Список работающих агентов для сводки в Telegram (scripts/opencode-status-notify.sh, таймер mc-progress раз в 5 мин).
# Файл вне репозитория, ведёт только главный агент: при постановке задачи — start, при завершении — stop.
#   agent-list.sh start <машина: gtx54|domovoy> <модель> <задача>   — строка с текущим временем начала
#   agent-list.sh stop <часть названия задачи>                      — убрать строку
#   agent-list.sh show
set -euo pipefail
file="${LEAD_AGENTS:-/opt/minecraft/lead-agents.txt}"
touch "$file"
case "${1:-show}" in
    start) printf '%s|%s|%s|%s\n' "$2" "$3" "$4" "$(date +%s)" >>"$file" ;;
    stop) grep -vF -- "$2" "$file" >"$file.tmp" || true; mv "$file.tmp" "$file" ;;
    show) cat "$file" ;;
    *) echo "использование: agent-list.sh start <машина> <модель> <задача> | stop <задача> | show" >&2; exit 2 ;;
esac
