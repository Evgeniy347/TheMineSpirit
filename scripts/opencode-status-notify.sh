#!/usr/bin/env bash
# Сводка о задании opencode в тему «Оповещения» (таймер раз в 5 мин). Молчит, если opencode не запущен.
status="$(sudo -u domovoy $(dirname "$0")/opencode-status.sh 1 2>/dev/null | sed -n '1p;3p' | tr '\n' ' ')"
case "$status" in
    *"не запущен"*|"") exit 0 ;;
esac
failures=$(wc -l < $(dirname "$0")/../TestResults/opencode/failures.log 2>/dev/null || echo 0)
sudo -u ids-notify notify --level info --subject "minecraft: opencode" "$status | сбоев в логе: $failures"
