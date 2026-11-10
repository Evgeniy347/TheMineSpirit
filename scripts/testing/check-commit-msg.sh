#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
hook="$PWD/scripts/git-hooks/commit-msg"
work="$PWD/TestResults/commit-msg-check"
rm -rf "$work"
mkdir -p "$work/repo"
cd "$work/repo" || exit 2
git init -q .
failed=0

check() {
    local name="$1" want="$2" message="$3"
    shift 3
    git rm -rq --cached . >/dev/null 2>&1
    for file in "$@"; do
        mkdir -p "$(dirname "$file")"
        echo "$name" >"$file"
        git add "$file"
    done
    printf '%s\n' "$message" >"$work/$name.msg"
    "$hook" "$work/$name.msg" >"$work/$name.out" 2>&1
    local code=$?
    if [ "$code" = "$want" ]; then
        echo "OK $name: код=$code"
    else
        echo "FAIL $name: код=$code (ожидал $want)"
        sed 's/^/  /' "$work/$name.out"
        failed=1
    fi
}

check bad_type 1 "build: сборка" src/main/java/A.java
check not_russian 1 "chore: build script" scripts/a.sh
check merge_skipped 0 "Merge branch 'x'"
check feat_with_junit 0 "feat: новое поведение" src/main/java/A.java src/test/java/ATests.java
check fix_with_gametest 0 "fix: поправил поведение" src/main/java/A.java src/main/java/com/theminespirit/gametest/AGameTests.java
check feat_without_test 1 "feat: новое поведение" src/main/java/A.java
check fix_only_junit 1 "fix: только тест" src/test/java/ATests.java
check feat_only_gametest 1 "feat: только GameTest" src/main/java/com/theminespirit/gametest/AGameTests.java
check test_only_junit 0 "test: только тест" src/test/java/ATests.java
check fix_data_and_test 0 "fix: данные и тест" src/main/resources/data.json src/test/java/ATests.java
check fix_with_mod_junit 0 "fix: поведение мода с тестом" mod/src/main/java/A.java mod/src/test/java/ATests.java
check fix_only_mod_junit 1 "fix: только тест мода" mod/src/test/java/ATests.java
check fix_script_only 0 "fix: скрипт без тестов" scripts/a.sh
grep -q "это test:" "$work/fix_only_junit.out" && echo "OK fix_only_junit_hint: подсказка «это test:»" \
    || { echo "FAIL fix_only_junit_hint: нет подсказки «это test:»"; failed=1; }
exit $failed
