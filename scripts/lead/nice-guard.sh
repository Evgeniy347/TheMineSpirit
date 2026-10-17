#!/usr/bin/env bash
# Сторож приоритета на сервере: исполнители opencode и всё, что они породили (bash, gradle, java GameTest), —
# с наименьшим приоритетом процессора (nice 19) и диска (ionice idle), чтобы живой сервер и машина не тормозили.
# Цели: потомки надзора scripts/opencode-task.sh и opencode; процессы с рабочей папкой в /opt/minecraft/wt/;
# демоны и рабочие Gradle (у демона рабочая папка ~/.gradle/daemon — он собирает worktree исполнителей).
# Не трогаем никогда: живой сервер (/opt/minecraft/server), tmux (в его командной строке бывает имя надзора — тогда
# всё в tmux оказалось бы «потомком»), сторожей (mem-guard, nice-guard).
# Запуск: tmux new-session -d -s nice-guard "bash /opt/minecraft/the-mine-spirit/scripts/lead/nice-guard.sh";
# журнал — /opt/minecraft/wt/logs/nice-guard.log (строка на каждое понижение).
set -u
INTERVAL="${NICE_GUARD_INTERVAL:-30}"
LOG="${NICE_GUARD_LOG:-/opt/minecraft/wt/logs/nice-guard.log}"
mkdir -p "$(dirname "$LOG")"

untouchable() {
    local pid="$1" args="$2" cwd="$3"
    case "$(ps -o comm= -p "$pid" 2>/dev/null)" in tmux*|"") return 0 ;; esac
    case "$args $cwd" in *"/opt/minecraft/server"*|*mem-guard*|*nice-guard*) return 0 ;; esac
    return 1
}

targets() {
    local roots="" p
    for p in $(pgrep -f 'opencode-task[.]sh|[.]opencode/bin/opencode'); do
        case "$(ps -o comm= -p "$p" 2>/dev/null)" in tmux*|"") ;; *) roots="$roots $p" ;; esac
    done
    # Потомки корней: обход дерева по таблице pid→ppid за один снимок ps.
    ps -eo pid=,ppid= | awk -v roots="$roots" '
        { parent[$1] = $2; pids[NR] = $1 }
        END {
            n = split(roots, r, " "); for (i = 1; i <= n; i++) mark[r[i]] = 1
            changed = 1
            while (changed) {
                changed = 0
                for (k in pids) { p = pids[k]; if (!(p in mark) && (parent[p] in mark)) { mark[p] = 1; changed = 1 } }
            }
            for (p in mark) print p
        }'
    # Рабочая папка в worktree исполнителей (временные проверки — тоже там), демоны и рабочие Gradle.
    local d cwd
    for d in /proc/[0-9]*; do
        cwd=$(readlink "$d/cwd" 2>/dev/null) || continue
        case "$cwd" in
            /opt/minecraft/wt/*|"$HOME"/.gradle/daemon/*) echo "${d#/proc/}" ;;
        esac
    done
    pgrep -f 'GradleDaemon|GradleWorkerMain'
}

while true; do
    for pid in $(targets | sort -un); do
        [ "$pid" = "$$" ] && continue
        [ -r "/proc/$pid/cmdline" ] || continue
        args=$( { tr '\0' ' ' <"/proc/$pid/cmdline"; } 2>/dev/null) || continue
        cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null)
        untouchable "$pid" "$args" "$cwd" && continue
        ni=$(ps -o ni= -p "$pid" 2>/dev/null | tr -d ' ')
        [ -n "$ni" ] || continue
        if [ "$ni" -lt 19 ]; then
            renice -n 19 -p "$pid" >/dev/null 2>&1 && ionice -c 3 -p "$pid" >/dev/null 2>&1
            printf '%s nice %s→19 pid=%s %s\n' "$(date '+%F %T')" "$ni" "$pid" "${args:0:120}" >>"$LOG"
        fi
    done
    sleep "$INTERVAL"
done
