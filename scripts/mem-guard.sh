#!/usr/bin/env bash
# Сторож памяти для параллельных исполнителей на domovoy (решение владельца 13.10: свободно не меньше 15 %).
# Свободной памяти (MemAvailable) меньше LOW % — приостанавливает (SIGSTOP) самый тяжёлый java-процесс сборки
# исполнителей (Gradle-демон, JVM тестов, GameTest-сервер из /opt/minecraft/wt/*); больше HIGH % — возобновляет
# (SIGCONT) по одному, последний приостановленный первым. Каждое действие — строка в stdout и в журнал.
# Запуск: tmux new -d -s mem-guard scripts/mem-guard.sh
set -u
LOW="${MEM_GUARD_LOW:-15}"
HIGH="${MEM_GUARD_HIGH:-25}"
INTERVAL="${MEM_GUARD_INTERVAL:-15}"
LOG="${MEM_GUARD_LOG:-/opt/minecraft/the-mine-spirit/TestResults/mem-guard.log}"
paused=()

say() { echo "$(date +%H:%M:%S) $*" | tee -a "$LOG"; }

avail_percent() {
    awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {printf "%d", a*100/t}' /proc/meminfo
}

heaviest_build_java() {
    ps -u "$(id -u)" -o pid=,rss=,stat=,args= --sort=-rss \
        | awk '$3 !~ /T/ && /java/ && (/GradleDaemon|Gradle Test Executor|gametest|\/opt\/minecraft\/wt\//) {print $1, $2; exit}'
}

say "сторож памяти запущен: пауза ниже ${LOW} %, продолжение выше ${HIGH} %"
while true; do
    p="$(avail_percent)"
    if [ "$p" -lt "$LOW" ]; then
        read -r pid rss < <(heaviest_build_java) || pid=""
        if [ -n "${pid:-}" ] && kill -STOP "$pid" 2>/dev/null; then
            paused+=("$pid")
            say "свободно ${p} % < ${LOW} %: приостановлен pid ${pid} (RSS $((rss / 1024)) МБ)"
        fi
    elif [ "$p" -gt "$HIGH" ] && [ "${#paused[@]}" -gt 0 ]; then
        last=$((${#paused[@]} - 1))
        pid="${paused[$last]}"
        unset "paused[$last]"
        paused=("${paused[@]}")
        kill -CONT "$pid" 2>/dev/null && say "свободно ${p} % > ${HIGH} %: продолжен pid ${pid}"
    fi
    sleep "$INTERVAL"
done
