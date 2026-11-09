#!/usr/bin/env bash
# lib: проверка pre-commit на worktree с заглушкой scripts/test.sh (по умолчанию) — без холодной сборки.
# CHECK_PRE_COMMIT_REAL=1 гоняет настоящий scripts/test.sh (медленно, для разовой проверки интеграции).
cd "$(dirname "$0")/../.." || exit 2
src="$PWD"
work="$src/TestResults/pre-commit-check"
clone="$work/worktree"
cleanup() {
    git -C "$src" worktree remove --force "$clone" >/dev/null 2>&1
    rm -rf "$work"
}
trap cleanup EXIT
rm -rf "$work"
mkdir -p "$work"
# lib: worktree вместо полного клона: общий объектный кэш, холодная сборка не нужна.
git worktree add -q --detach "$clone" HEAD || exit 2
cp "$src"/scripts/git-hooks/* "$clone/scripts/git-hooks/"
cd "$clone" || exit 2
# lib: конфиг через окружение — общий .git worktree не должен менять хуки и автора основной копии.
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=core.hooksPath GIT_CONFIG_VALUE_0=scripts/git-hooks
export GIT_CONFIG_KEY_1=core.autocrlf GIT_CONFIG_VALUE_1=input
export GIT_AUTHOR_NAME=check GIT_AUTHOR_EMAIL=check@example.invalid
export GIT_COMMITTER_NAME=check GIT_COMMITTER_EMAIL=check@example.invalid
if [ "${CHECK_PRE_COMMIT_REAL:-0}" != "1" ]; then
    cat >scripts/test.sh <<'STUB'
#!/usr/bin/env bash
# lib: проверка хука не собирает проект: зелёный/красный зонд виден по тексту ProbeTests.
echo "pre-commit: прогоняю scripts/test.sh unit --changed" >&2
if grep -q 'fail("' src/test/java/com/theminespirit/ProbeTests.java 2>/dev/null; then
    echo "STUB unit: красный зонд" >&2
    exit 1
fi
echo "STUB unit: зелёный зонд" >&2
exit 0
STUB
fi
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
check docs_commit_runs_guards 0 да git commit -m "docs: правка проходит сторожей"

write_probe assertTrue 'assertTrue(true, "зелёный тест-зонд");'
git add "$probe"
check green_test_accepted 0 да git commit -m "test: зелёный зонд"

guard_probe="guards/src/test/java/com/theminespirit/guards/ProbeGuardTests.java"
mkdir -p "$(dirname "$guard_probe")"
printf 'package com.theminespirit.guards;\n\nclass ProbeGuardTests {}\n' >"$guard_probe"
git add "$guard_probe"
check guards_commit_runs 0 да git commit -m "test: правка сторожа"
git reset -q "$guard_probe"
rm -f "$guard_probe"

write_probe fail 'fail("красный тест-зонд");'
check partial_commit_red_rejected 1 да git commit -m "test: красный зонд через путь" -- "$probe"
write_probe assertTrue 'assertTrue(true, "зелёный тест-зонд");'

printf 'package com.theminespirit;

class Unstaged {
    static final int VALUE = 1;
}
' >src/test/java/com/theminespirit/Unstaged.java
git add src/test/java/com/theminespirit/Unstaged.java
sed -i 's/VALUE = 1/VALUE = 2/' src/test/java/com/theminespirit/Unstaged.java
check unstaged_warning 0 да git commit -m "test: пустой класс-зонд"
grep -q "ВНИМАНИЕ — прогон относится к рабочему дереву" "$work/unstaged_warning.out"     && echo "OK предупреждение о незастейдженных правках напечатано" || echo "FAIL предупреждения о незастейдженных правках нет"
git checkout -q HEAD -- src/test/java/com/theminespirit/Unstaged.java

base=$(git rev-parse HEAD)
git checkout -q -B side
sed -i 's/VALUE = 1/VALUE = 3/' src/test/java/com/theminespirit/Unstaged.java
git add -A && git commit -q -m "test: правка на ветке"
git checkout -q "$base"
echo "ещё документ" >>docs/TESTING.md
git add docs/TESTING.md && git commit -q -m "docs: правка на основной ветке"
check merge_no_run 0 нет git merge --no-ff -m "chore: слияние side" side
git branch -q -D side 2>/dev/null || true
