#!/usr/bin/env bash
set -uo pipefail

INTERVAL="${ORPHANS_WIN_INTERVAL:-2}"
LIST="${ORPHANS_WIN_LIST:-}"

usage() {
    cat <<'EOF'
Использование: scripts/lead/orphans-win.sh [--list <файл>]

Показывает сирот: bash.exe scripts/test.sh, gradlew, java.exe, чей родитель не существует
или цепочка родителей не доходит до живого opencode (исполнителя). Колонки: PID, CPU за
интервал, сколько живёт, worktree из командной строки, причина и команда.
Никого не убивает: решение и kill — за главным агентом, по PID.

  ORPHANS_WIN_LIST=<файл>   готовая таблица (pid, ppid, cpu_ms, минуты, имя, команда) для проверки
  ORPHANS_WIN_INTERVAL=2    окно замера CPU, с
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --list|-l)
            shift
            [ $# -gt 0 ] || { echo "orphans-win: --list требует файл" >&2; exit 2; }
            LIST="$1"
            shift
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "orphans-win: неизвестный аргумент $1" >&2; usage; exit 2 ;;
    esac
done

process_table() {
    if [ -n "$LIST" ]; then
        cat "$LIST"
        return 0
    fi
    command -v powershell.exe >/dev/null 2>&1 || return 0
    powershell.exe -NoProfile -NonInteractive -Command '
Get-CimInstance Win32_Process | ForEach-Object {
    $cpu = [int]($_.UserModeTime / 10000) + [int]($_.KernelModeTime / 10000)
    $cmd = $_.CommandLine
    if ($null -eq $cmd) { $cmd = "" }
    $cmd = $cmd -replace "`t", " " -replace "\r?\n", " "
    $age = 0
    if ($_.CreationDate) { $age = [int]((Get-Date) - $_.CreationDate).TotalMinutes }
    "{0}`t{1}`t{2}`t{3}`t{4}`t{5}" -f $_.ProcessId, $_.ParentProcessId, $cpu, $age, $_.Name, $cmd
}' | tr -d '\r'
}

pause_between_samples() {
    sleep "$INTERVAL" 2>/dev/null || true
}

measure_table() {
    if [ -n "$LIST" ]; then
        process_table
        return 0
    fi
    local first second
    first="$(process_table)"
    pause_between_samples
    second="$(process_table)"
    awk -F '\t' '
        NR == FNR { old[$1 + 0] = $3 + 0; next }
        { printf "%s\t%s\t%d\t%s\t%s\t%s\n", $1, $2, ($1 + 0 in old ? $3 - old[$1 + 0] : 0), $4, $5, $6 }' \
        <(printf '%s\n' "$first") <(printf '%s\n' "$second")
}

select_orphans() {
    awk -F '\t' '
    function lower(s) { return tolower(s) }
    function short(name,   n) { n = lower(name); sub(/\.exe$/, "", n); return n }
    function candidate(name, cmd,   n, c) {
        n = short(name)
        c = lower(cmd)
        gsub(/\\/, "/", c)
        if (c ~ /opencode(\.exe)?[^,]* run / || c ~ /--format json/) return 0
        if (n == "java") {
            if (c ~ /gradledaemon/ || c ~ /livedebug\.copy=/) return 0
            return c ~ /(wt-[a-z0-9._-]+|fml\.modfolders=)/
        }
        if (n != "bash" && n != "sh" && n != "cmd") return 0
        if (length(cmd) > 300) return 0
        return c ~ /(scripts\/test\.sh|test\.sh (unit|gametest)|gradlew)/
    }
    function worktree(cmd,   c) {
        c = lower(cmd)
        gsub(/\\/, "/", c)
        if (match(c, /wt-[a-z0-9._-]+/)) return substr(c, RSTART, RLENGTH)
        return "-"
    }
    function owned_by_opencode(pid,   p, steps) {
        p = pid
        steps = 0
        while (steps < 64 && (p in parent)) {
            p = parent[p]
            if (p == 0) return 0
            if (lower(name[p]) ~ /^opencode(\.exe)?$/) return 1
            steps++
        }
        return 0
    }
    {
        pid = $1 + 0
        parent[pid] = $2 + 0
        cpu[pid] = $3 + 0
        age[pid] = $4 + 0
        name[pid] = $5
        cmd[pid] = $6
        live[pid] = 1
    }
    END {
        found = 0
        for (pid in live) {
            if (!candidate(name[pid], cmd[pid])) continue
            if (owned_by_opencode(pid)) continue
            found++
            if (!(pid in parent) || parent[pid] == 0 || !(parent[pid] in live)) {
                reason = "родитель " ((pid in parent) ? parent[pid] : "?") " не существует"
            } else {
                reason = "цепочка родителей не ведёт к opencode"
            }
            printf "%d\t%d\t%d\t%s\t%s\t%s\n", pid, cpu[pid], age[pid], worktree(cmd[pid]), reason, cmd[pid]
        }
        if (found == 0) print "-"
    }'
}

table="$(measure_table)"
if [ -z "$table" ]; then
    echo "orphans-win: нет таблицы процессов (нужен powershell.exe или ORPHANS_WIN_LIST)" >&2
    exit 0
fi

rows="$(printf '%s\n' "$table" | select_orphans)"
count=0
[ "$rows" != "-" ] && count=$(printf '%s\n' "$rows" | grep -c .)
echo "сироты test.sh/gradlew/java: $count (CPU за ${INTERVAL} с)"
if [ "$count" = 0 ]; then
    exit 0
fi
echo "PID	CPU,мс	живёт,мин	worktree	причина	команда"
printf '%s\n' "$rows" | while IFS=$'\t' read -r pid cpu age wt reason cmd; do
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$pid" "$cpu" "$age" "$wt" "$reason" "$(printf '%s' "$cmd" | cut -c1-160)"
done
