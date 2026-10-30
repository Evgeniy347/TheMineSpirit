#!/usr/bin/env bash
set -uo pipefail

DRY_RUN=0
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        -h|--help)
            echo "Использование: scripts/lead/orphan-sweep-win.sh [--dry-run]"
            echo "Ищет и убивает по PID осиротевшие процессы исполнителей, запущенные на ПК (Windows, Git Bash)."
            echo "find/grep/bash/sh/timeout/tail/xargs/java/opencode старше 15 мин с умершим родителем и без живых потомков;"
            echo "процессы с opencode-task.sh/run-one.sh/next-slot.sh/lead-merge.sh/test.sh/gradlew не трогает;"
            echo "bash/sh — только без потомков и старше 60 мин; find с аргументом «/» — старше 5 мин."
            echo "GradleDaemon, Minecraft, OpenCode desktop и Claude не трогает."
            exit 0
            ;;
        *) echo "orphan-sweep-win: неизвестный аргумент $arg" >&2; exit 2 ;;
    esac
done

OLD_MIN="${ORPHAN_SWEEP_WIN_OLD_MINUTES:-15}"
FIND_SLASH_MIN="${ORPHAN_SWEEP_WIN_FIND_SLASH_MINUTES:-5}"
SHELL_MIN="${ORPHAN_SWEEP_WIN_SHELL_MINUTES:-60}"

if [ -z "${ORPHAN_SWEEP_WIN_LIST:-}" ] && ! command -v powershell.exe >/dev/null 2>&1; then
    exit 0
fi

process_table() {
    if [ -n "${ORPHAN_SWEEP_WIN_LIST:-}" ]; then
        cat "$ORPHAN_SWEEP_WIN_LIST"
        return 0
    fi
    powershell.exe -NoProfile -NonInteractive -Command '
$now = Get-Date
Get-CimInstance Win32_Process | ForEach-Object {
    $age = if ($_.CreationDate) { [int]($now - $_.CreationDate).TotalMinutes } else { 0 }
    $cmd = $_.CommandLine
    if ($null -eq $cmd) { $cmd = "" }
    $cmd = $cmd -replace "`t", " " -replace "\r?\n", " "
    "{0}`t{1}`t{2}`t{3}`t{4}" -f $_.ProcessId, $_.ParentProcessId, $age, $_.Name, $cmd
}' | tr -d '\r'
}

select_orphans() {
    awk -F '\t' -v old="$OLD_MIN" -v find_slash="$FIND_SLASH_MIN" -v shell_min="$SHELL_MIN" '
    function lower(s) { return tolower(s) }
    function target_name(name,   n) {
        n = lower(name)
        sub(/\.exe$/, "", n)
        return n ~ /^(find|grep|bash|sh|timeout|tail|xargs|java|opencode)$/
    }
    function shell_name(name,   n) {
        n = lower(name)
        sub(/\.exe$/, "", n)
        return n == "bash" || n == "sh"
    }
    function script_marker(cmd,   c) {
        c = lower(cmd)
        gsub(/\\/, "/", c)
        return c ~ /opencode-task\.sh|run-one\.sh|next-slot\.sh|lead-merge\.sh|test\.sh|gradlew/
    }
    function has_live_descendant(pid,   i, c) {
        if (!(pid in cnt)) return 0
        for (i = 1; i <= cnt[pid]; i++) {
            c = children[pid, i]
            if (c in live) return 1
            if (has_live_descendant(c)) return 1
        }
        return 0
    }
    function find_slash_root(cmd,   c) {
        c = lower(cmd)
        gsub(/["\047]/, "", c)
        return c ~ /(^|[\/ ])find(\.exe)?[ \t]+\/([ \t]|$)/
    }
    function protected(name, cmd,   n, c) {
        n = lower(name)
        sub(/\.exe$/, "", n)
        if (n == "gradledaemon" || n == "minecraft" || n == "claude") return 1
        c = lower(name " " cmd)
        if (c ~ /gradledaemon/ || c ~ /minecraft/) return 1
        if (c ~ /@opencode-aidesktop/ || c ~ /opencode-aidesktop/) return 1
        if (c ~ /(^|[\/\\ ])claude(\.exe)?([ \t]|$)/) return 1
        return 0
    }
    { live[$1 + 0] = 1; rows[NR] = $0; total = NR }
    END {
        for (i = 1; i <= total; i++) {
            split(rows[i], f, "\t")
            children[f[2] + 0, ++cnt[f[2] + 0]] = f[1] + 0
        }
        for (i = 1; i <= total; i++) {
            split(rows[i], f, "\t")
            pid = f[1] + 0
            ppid = f[2] + 0
            age = f[3] + 0
            name = f[4]
            cmd = f[5]
            if (protected(name, cmd)) continue
            reason = ""
            if (find_slash_root(cmd) && age > find_slash) {
                reason = "find / старше " find_slash " мин"
            } else if (target_name(name) && age > old && (ppid == 0 || !(ppid in live))) {
                if (has_live_descendant(pid)) {
                    continue
                } else if (script_marker(cmd)) {
                    continue
                } else if (shell_name(name) && age <= shell_min) {
                    continue
                }
                reason = "родитель " ppid " умер, жил " age " мин"
            }
            if (reason != "") printf "%d\t%s\t%d\t%s\n", pid, name, age, reason
        }
    }'
}

kill_pid() {
    local pid="$1"
    case "$pid" in ''|*[!0-9]*) return 0 ;; esac
    if [ -n "${ORPHAN_SWEEP_WIN_KILL:-}" ]; then
        "$ORPHAN_SWEEP_WIN_KILL" "$pid"
        return 0
    fi
    powershell.exe -NoProfile -NonInteractive -Command \
        "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
    if powershell.exe -NoProfile -NonInteractive -Command \
        "if (Get-Process -Id $pid -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >/dev/null 2>&1; then
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
    fi
}

found="$(process_table | select_orphans)"
if [ -z "$found" ]; then
    [ "$DRY_RUN" = 1 ] && echo "dry-run: осиротевших процессов нет"
    exit 0
fi

while IFS=$'\t' read -r pid name age reason; do
    [ -n "$pid" ] || continue
    if [ "$DRY_RUN" = 1 ]; then
        echo "dry-run: убил бы $name pid=$pid ($reason)"
    else
        kill_pid "$pid"
        echo "orphan-sweep-win: убит $name pid=$pid ($reason)"
    fi
done <<< "$found"
