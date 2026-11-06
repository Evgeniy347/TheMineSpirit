#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
src="$PWD"
work="$src/TestResults/pre-commit-check"
clone="$work/clone"
rm -rf "$work"
mkdir -p "$work"
git clone -q --no-hardlinks "$src" "$clone" || exit 2
cp "$src"/scripts/git-hooks/* "$clone/scripts/git-hooks/"
cd "$clone" || exit 2
git config core.hooksPath scripts/git-hooks
git config core.autocrlf input
git config user.name check
git config user.email check@example.invalid
probe="src/test/java/com/theminespirit/ProbeTests.java"
mkdir -p "$(dirname "$probe")"

write_probe() {
    printf 'package com.theminespirit;\n\nimport static org.junit.jupiter.api.Assertions.%s;\n\nimport org.junit.jupiter.api.Test;\n\nclass ProbeTests {\n    @Test\n    void probe_Always_Result() {\n        %s\n    }\n}\n' \
        "$1" "$2" >"$probe"
}

check() {
    local name="$1" want_code="$2" want_run="$3"
    shift 3
    local started=$(date +%s)
    "$@" >"$work/$name.out" 2>&1
    local code=$? ran=нет verdict=OK
    grep -q "прогоняю scripts/test.sh unit" "$work/$name.out" && ran=да
    [ "$code" = "$want_code" ] && [ "$ran" = "$want_run" ] || verdict=FAIL
    echo "$verdict $name: код=$code (ожидал $want_code), прогон хука=$ran (ожидал $want_run), $(( $(date +%s) - started )) с"
}

write_probe fail 'fail("красный тест-зонд");'
git add "$probe"
check red_test_rejected 1 да git commit -m "test: красный зонд"
grep -E "Коммит отклонён|FAILED" "$work/red_test_rejected.out" | head -n 3 | sed 's/^/  /'
git reset -q "$probe"

echo "правка документа" >>docs/TESTING.md
git add docs/TESTING.md
check docs_commit_no_run 0 нет git commit -m "docs: правка без прогона"

write_probe assertTrue 'assertTrue(true, "зелёный тест-зонд");'
git add "$probe"
check green_test_accepted 0 да git commit -m "test: зелёный зонд"

write_probe fail 'fail("красный тест-зонд");'
check partial_commit_red_rejected 1 да git commit -m "test: красный зонд через путь" -- "$probe"
write_probe assertTrue 'assertTrue(true, "зелёный тест-зонд");'

printf 'package com.theminespirit;

class Unstaged {}
' >src/test/java/com/theminespirit/Unstaged.java
git add src/test/java/com/theminespirit/Unstaged.java
echo "" >>"$probe"
check unstaged_warning 0 да git commit -m "test: пустой класс-зонд"
grep -q "ВНИМАНИЕ — прогон относится к рабочему дереву" "$work/unstaged_warning.out"     && echo "OK предупреждение о незастейдженных правках напечатано" || echo "FAIL предупреждения о незастейдженных правках нет"
git checkout -q HEAD -- "$probe"

git checkout -q -b side
echo "" >>src/test/java/com/theminespirit/Unstaged.java
git add -A && git commit -q -m "test: правка на ветке"
git checkout -q -
echo "ещё документ" >>docs/TESTING.md
git add docs/TESTING.md && git commit -q -m "docs: правка на основной ветке"
check merge_no_run 0 нет git merge --no-ff -m "chore: слияние side" side
