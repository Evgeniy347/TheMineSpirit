#!/usr/bin/env bash
dir="$(cd "$(dirname "$0")" && pwd)"
agents_file="${LEAD_AGENTS:-/opt/minecraft/lead-agents.txt}"
now=$(date +%s)
agents=""
if [ -f "$agents_file" ]; then
    while IFS='|' read -r host model task start; do
        [ -z "$host" ] || [ "${host:0:1}" = "#" ] && continue
        mins=$(( (now - ${start:-$now}) / 60 ))
        agents+=$'\n'"$host; $model; $task; $(printf '%d:%02d' $((mins / 60)) $((mins % 60)))"
    done <"$agents_file"
fi
[ -z "$agents" ] && exit 0
progress="$("$dir/progress.sh")"
sudo -u ids-notify notify --level info --subject "minecraft" "Прогресс плана: $progress$agents"
