#!/usr/bin/env bash
set -euo pipefail
file="${LEAD_AGENTS:-/opt/minecraft/lead-agents.txt}"
touch "$file"
case "${1:-show}" in
    start) printf '%s|%s|%s|%s\n' "$2" "$3" "$4" "$(date +%s)" >>"$file" ;;
    stop) grep -vF -- "$2" "$file" >"$file.tmp" || true; mv "$file.tmp" "$file" ;;
    show) cat "$file" ;;
    *) echo "использование: agent-list.sh start <машина> <модель> <задача> | stop <задача> | show" >&2; exit 2 ;;
esac
