#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
sweep="$PWD/scripts/lead/orphan-sweep-win.sh"
work="$PWD/TestResults/orphan-sweep-win-check"
rm -rf "$work"
mkdir -p "$work"
list="$work/processes.tsv"
failed=0

row() {
    printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5"
}

{
    row 200 1 40 explorer.exe 'C:\Windows\explorer.exe'
    row 101 999 20 find.exe 'find.exe "F:\\" -name l-observe4.out'
    row 103 200 7 find.exe 'find.exe "/" -name l-observe4.out'
    row 104 200 3 find.exe 'find.exe "/" -name young.out'
    row 105 999 60 java.exe 'java -cp gradle-launcher.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon'
    row 106 999 60 java.exe 'java -jar minecraft_server.jar nogui'
    row 107 999 60 opencode.exe 'C:\Users\u\AppData\Local\Programs\@opencode-aidesktop\opencode.exe'
    row 108 999 60 bash.exe 'bash.exe /c/Users/u/AppData/Local/Programs/claude/claude.exe'
    row 109 999 30 java.exe 'java -jar app.jar'
    row 110 200 7 find.exe 'find.exe C:/Users -name x'
    row 111 999 30 opencode.exe 'opencode run'
    row 112 200 7 find.exe 'find.exe /home -name x'
    row 113 200 30 tail.exe 'tail.exe -f log.txt'
    row 114 999 16 tail.exe 'tail.exe -f other.log'
    row 115 999 15 grep.exe 'grep.exe foo bar.txt'
    row 116 200 5 find.exe 'find.exe "/" -name boundary.out'
    row 117 200 6 find.exe "find.exe '/' -name quoted.out"
    row 118 999 20 find.exe 'find.exe /'
    row 119 999 60 bash.exe 'bash -c "find / -name x"'
    row 120 999 60 opencode.exe 'C:\Program Files\OpenCode\opencode.exe'
    row 30001 1 130 bash.exe 'bash.exe scripts/lead/next-slot.sh'
    row 30002 30001 120 bash.exe 'bash.exe scripts/lead/run-one.sh observe7'
    row 30003 30002 100 bash.exe 'bash.exe scripts/opencode-task.sh -f observe7.msg'
    row 30004 30003 90 opencode.exe 'opencode.exe run'
    row 30010 1 130 bash.exe 'bash.exe -c loop'
    row 30011 30010 90 bash.exe 'bash.exe -c inner'
    row 30030 1 40 bash.exe 'bash.exe -c sleep 30'
    row 30040 1 70 bash.exe 'bash.exe -c stale'
    row 30050 1 40 java.exe 'java -cp gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain gradlew test'
    row 40001 999 60 java.exe 'java.exe -Dlivedebug.copy=copy-live -jar server.jar nogui'
} >"$list"

expect_killed=(101 103 109 111 114 117 118 119 120 30040 50002 50003)
expect_kept=(104 105 106 107 108 110 112 113 115 116 200 30001 30002 30003 30004 30010 30011 30030 30050 40001 50001)

live="$work/live"
mkdir -p "$live/copy-live" "$live/copy-dead" "$live/copy-tree"
now="$(date +%s)"
printf '%s|%s|%s|%s|%s|%s
' "$PWD" "$$" "$((now + 3600))" copy-live 50001 "$live/copy-live" >"$live/copy-live/owner.lease"
printf '%s|%s|%s|%s|%s|%s
' "$PWD" 999999 1 copy-dead 50002 "$live/copy-dead" >"$live/copy-dead/owner.lease"
printf '%s|%s|%s|%s|%s|%s
' "$work/missing-tree" "$$" "$((now + 3600))" copy-tree 50003 "$live/copy-tree" >"$live/copy-tree/owner.lease"
ORPHAN_SWEEP_WIN_LIVE_ROOT="$live" ORPHAN_SWEEP_WIN_LIST="$list" bash "$sweep" --dry-run >"$work/dry.out" 2>&1
code=$?
[ "$code" = 0 ] && echo "OK dry_run_exit: код 0" || { echo "FAIL dry_run_exit: код $code"; failed=1; }

for pid in "${expect_killed[@]}"; do
    if grep -q "pid=$pid " "$work/dry.out"; then
        echo "OK dry_run_finds_$pid"
    else
        echo "FAIL dry_run_finds_$pid: pid=$pid не попал в список"
        failed=1
    fi
done

for pid in "${expect_kept[@]}"; do
    if grep -q "pid=$pid " "$work/dry.out"; then
        echo "FAIL dry_run_keeps_$pid: pid=$pid не должен попадать в список"
        failed=1
    else
        echo "OK dry_run_keeps_$pid"
    fi
done

killer="$work/killer.sh"
kill_log="$work/killed.pids"
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$1" >> "%s"\n' "$kill_log" >"$killer"
chmod +x "$killer"
ORPHAN_SWEEP_WIN_LIVE_ROOT="$live" ORPHAN_SWEEP_WIN_LIST="$list" ORPHAN_SWEEP_WIN_KILL="$killer" bash "$sweep" >"$work/kill.out" 2>&1
code=$?
[ "$code" = 0 ] && echo "OK sweep_exit: код 0" || { echo "FAIL sweep_exit: код $code"; failed=1; }

if [ ! -e "$kill_log" ]; then
    echo "FAIL kill_only_expected: убийца не вызван"
    failed=1
else
    got="$(sort -n "$kill_log" | tr '\n' ' ' | sed 's/ $//')"
    want="$(printf '%s\n' "${expect_killed[@]}" | sort -n | tr '\n' ' ' | sed 's/ $//')"
    if [ "$got" = "$want" ]; then
        echo "OK kill_only_expected: убиты только выбранные PID"
    else
        echo "FAIL kill_only_expected: убиты «$got», ожидались «$want»"
        failed=1
    fi
    if grep -qvE '^[0-9]+$' "$kill_log"; then
        echo "FAIL kill_by_pid_only: убийце передан не PID"
        failed=1
    else
        echo "OK kill_by_pid_only: убийце передаются только числовые PID"
    fi
fi

[ "$(wc -l < "$kill_log" 2>/dev/null || echo 0)" = "${#expect_killed[@]}" ] \
    && echo "OK kill_count: убито ${#expect_killed[@]}" \
    || { echo "FAIL kill_count: строк $(wc -l < "$kill_log" 2>/dev/null || echo 0), ожидалось ${#expect_killed[@]}"; failed=1; }
if [ -e "$live/copy-dead" ] || [ -e "$live/copy-tree" ]; then
    echo "FAIL live_copy_dead_removed: каталог погашенной копии остался"
    failed=1
else
    echo "OK live_copy_dead_removed"
fi
if [ -e "$live/copy-live" ]; then
    echo "OK live_copy_kept"
else
    echo "FAIL live_copy_kept: каталог живой копии удалён"
    failed=1
fi

rm -f "$kill_log"
ORPHAN_SWEEP_WIN_LIVE_ROOT="$live" ORPHAN_SWEEP_WIN_LIST="$list" ORPHAN_SWEEP_WIN_KILL="$killer" bash "$sweep" --dry-run >"$work/dry2.out" 2>&1
if [ -e "$kill_log" ]; then
    echo "FAIL dry_run_no_kill: в dry-run убийца вызван"
    failed=1
else
    echo "OK dry_run_no_kill: dry-run ничего не убивает"
fi

empty="$work/empty.tsv"
: >"$empty"
ORPHAN_SWEEP_WIN_LIVE_ROOT="$live" ORPHAN_SWEEP_WIN_LIST="$empty" bash "$sweep" --dry-run >"$work/empty.out" 2>&1
grep -q "осиротевших процессов нет" "$work/empty.out" \
    && echo "OK empty_list: пустой список назван" \
    || { echo "FAIL empty_list: пустой список не назван"; failed=1; }

exit $failed
