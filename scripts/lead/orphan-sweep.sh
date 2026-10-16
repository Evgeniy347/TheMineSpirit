#!/usr/bin/env bash
# Запускается с ПК из scripts/lead/oc-watch.sh раз в 10 минут: ssh domovoy … scripts/lead/orphan-sweep.sh.
# Сироты исполнителей opencode: родитель — init (PPID 1), живут > 15 мин, относятся к worktree задач или ищут по всему диску.
# Демон Gradle, игровой сервер и сессии tmux не трогаем. Печатает строку на каждое убийство.
ps -eo pid=,ppid=,etimes=,comm=,args= | awk '$2==1 && $3>900 && $4 ~ /^(find|grep|bash|sh|timeout|sleep|tail|head|xargs|java|python3|opencode)$/ && ($0 ~ /\/opt\/minecraft\/wt\// || $0 ~ /find \/ /) && $0 !~ /GradleDaemon/ && $0 !~ /\/opt\/minecraft\/server/ {print $1" "$4" "int($3/60)}' | while read -r pid comm age; do
  kill "$pid" 2>/dev/null && echo "domovoy: убит сирота $comm pid=$pid, жил $age мин"
done
