#!/usr/bin/env bash
# Сводка в тему «Оповещения» Telegram (таймер раз в 5 мин): общий прогресс плана и работающие исполнители.
# Молчит, если ни один исполнитель (tmux-сессия oc-*) не работает.
dir="$(cd "$(dirname "$0")" && pwd)"
sessions="$(sudo -u domovoy tmux ls -F '#{session_name}' 2>/dev/null | grep '^oc-' | tr '\n' ' ')"
[ -z "$sessions" ] && exit 0
progress="$("$dir/progress.sh")"
status="$(sudo -u domovoy "$dir/opencode-status.sh" 1 2>/dev/null | sed -n '3p' | sed 's/^ *//')"
failures=$(cat /opt/minecraft/the-mine-spirit/TestResults/opencode/failures.log /opt/minecraft/wt/*/TestResults/opencode/failures.log 2>/dev/null | wc -l)
sudo -u ids-notify notify --level info --subject "minecraft" \
    "Прогресс плана: $progress | работают: $sessions| main: ${status:-—} | сбоев модели: $failures"
