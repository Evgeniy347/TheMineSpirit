#!/usr/bin/env bash
# Регрессионная проверка scripts/lead/chkq.sh на пустом временном репозитории (секунды, без Gradle и Minecraft):
# фейковые ветки и подмена scripts/test.sh. Строки вывода начинаются с OK или FAIL; код 1, если есть FAIL.
cd "$(dirname "$0")/../.." || exit 2
chkq="$PWD/scripts/lead/chkq.sh"
work="$PWD/TestResults/chkq-check"
rm -rf "$work"
mkdir -p "$work/repo/scripts"
cd "$work/repo" || exit 2
git -c init.defaultBranch=main init -q .
git config user.email "check-chkq@example.invalid"
git config user.name "check-chkq"
failed=0

# Подмена scripts/test.sh: unit падает, если в ветке есть scripts/FAIL_UNIT; иначе unit и gametest — 0.
cat > scripts/test.sh <<'EOF'
#!/usr/bin/env bash
[ "${1:-}" = unit ] && [ -e "$(dirname "$0")/FAIL_UNIT" ] && exit 1
exit 0
EOF
chmod +x scripts/test.sh
echo base > base.txt
git add -A
git commit -q -m "main: база"

git checkout -q -b ok
echo ok > ok.txt
git add -A
git commit -q -m "ok: файл"

git checkout -q main && git checkout -q -b red
echo red > red.txt
: > scripts/FAIL_UNIT
git add -A
git commit -q -m "red: падающий unit"

git checkout -q main && git checkout -q -b conflict
echo branch > base.txt
git add -A
git commit -q -m "conflict: правка базы"

git checkout -q main
echo main > base.txt
git add -A
git commit -q -m "main: встречная правка"

THEMINESPIRIT_CHKQ_REPO="$work/repo" THEMINESPIRIT_CHKQ_WT="$work/wt" THEMINESPIRIT_CHKQ_OUT="$work/out" \
    bash "$chkq" 1 - conflict red ok > "$work/chkq.out" 2>&1

summary="$work/out/summary-1"
expect() {
    if grep -qx "$1" "$summary" 2>/dev/null; then
        echo "OK $2"
    else
        echo "FAIL $2: нет строки «$1» в сводке"
        sed 's/^/  /' "$summary" 2>/dev/null
        failed=1
    fi
}

expect "conflict merge CONFLICT" "conflict_merge_conflict_is_reported"
expect "red unit EXIT=1" "red_unit_exit_nonzero_is_in_summary"
expect "ok unit EXIT=0" "green_unit_exit_zero_is_in_summary"
expect "ok gametest EXIT=0" "green_gametest_exit_zero_is_in_summary"
expect "DONE" "summary_has_done"

if [ "$(tail -1 "$summary" 2>/dev/null)" = "DONE" ]; then
    echo "OK done_is_last_line"
else
    echo "FAIL done_is_last_line: DONE не последняя строка сводки"
    failed=1
fi

if grep -q "^conflict unit" "$summary" 2>/dev/null; then
    echo "FAIL conflict_runs_no_tests: конфликт не должен запускать unit"
    failed=1
else
    echo "OK conflict_runs_no_tests"
fi

exit $failed
