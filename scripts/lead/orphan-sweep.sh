#!/usr/bin/env bash
ps -eo pid=,ppid=,etimes=,comm=,args= | awk '$2==1 && $3>900 && $4 ~ /^(find|grep|bash|sh|timeout|sleep|tail|head|xargs|java|python3|opencode)$/ && ($0 ~ /\/opt\/minecraft\/wt\// || $0 ~ /find \/ /) && $0 !~ /GradleDaemon/ && $0 !~ /\/opt\/minecraft\/server/ {print $1" "$4" "int($3/60)}' | while read -r pid comm age; do
  kill "$pid" 2>/dev/null && echo "domovoy: убит сирота $comm pid=$pid, жил $age мин"
done
