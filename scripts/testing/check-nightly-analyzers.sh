#!/usr/bin/env bash
# lib: Проверка scripts/nightly-analyzers.sh на подделках: «нет изменений → выход без сборки», зелёный прогон
# обновляет sha и статус, красный прогон возвращает код 1 и перечисляет находки, NIGHTLY_FORCE собирает повторно.
# Настоящий Gradle и сеть не нужны: NIGHTLY_GRADLEW — подделка, репозиторий — временный.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/nightly-analyzers-check"
rm -rf "$work"
mkdir -p "$work/bin"
failed=0

ok() { echo "OK $1"; }
fail() {
    echo "FAIL $1"
    failed=1
}

export GIT_AUTHOR_NAME="nightly-check"
export GIT_AUTHOR_EMAIL="nightly-check@example.invalid"
export GIT_COMMITTER_NAME="nightly-check"
export GIT_COMMITTER_EMAIL="nightly-check@example.invalid"

upstream="$work/upstream"
git init -q -b main "$upstream" || exit 2
echo one >"$upstream/file.txt"
git -C "$upstream" add file.txt
git -C "$upstream" commit -qm "первый коммит"

cat >"$work/bin/gradlew-green" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"${FAKE_ARGS:?}"
echo "BUILD SUCCESSFUL"
exit 0
FAKE
cat >"$work/bin/gradlew-red" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"${FAKE_ARGS:?}"
mkdir -p mod/build/reports/spotbugs
cat >mod/build/reports/spotbugs/main.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<BugCollection>
  <BugInstance type="MS_EXAMPLE">
    <ShortMessage>поле не читается</ShortMessage>
  </BugInstance>
</BugCollection>
XML
echo "SpotBugs found 1 bug"
exit 1
FAKE
chmod +x "$work/bin"/*

export NIGHTLY_REPO="$upstream"
export NIGHTLY_WORK="$work/checkout"
export NIGHTLY_STATE="$work/state"
export NIGHTLY_STATUS="$work/status"
export NIGHTLY_LOG="$work/log"
export NIGHTLY_BUILD_LOG="$work/build.log"
export NIGHTLY_LOCK="$work/lock"
export NIGHTLY_GRADLEW="$work/bin/gradlew-green"
export FAKE_ARGS="$work/args"
: >"$FAKE_ARGS"

sha="$(git -C "$upstream" rev-parse main)"
printf '%s' "$sha" >"$NIGHTLY_STATE"

out=$(bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 0 ] && [ ! -s "$FAKE_ARGS" ] && grep -q "изменений в main" "$NIGHTLY_LOG" \
    && ok "без изменений: код=0, Gradle не запускался, в логе «изменений нет»" \
    || fail "без изменений: код=$code, аргументы Gradle: $(cat "$FAKE_ARGS" 2>/dev/null), лог: $(cat "$NIGHTLY_LOG" 2>/dev/null)"

echo two >>"$upstream/file.txt"
git -C "$upstream" add file.txt
git -C "$upstream" commit -qm "второй коммит"
new_sha="$(git -C "$upstream" rev-parse main)"
: >"$FAKE_ARGS"

out=$(bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 0 ] && grep -q -- "-Ptheminespirit.analyzers=on check --continue" "$FAKE_ARGS" \
    && grep -q -- "--offline" "$FAKE_ARGS" \
    && [ "$(cat "$NIGHTLY_STATE")" = "$new_sha" ] \
    && grep -q "sha: $new_sha" "$NIGHTLY_STATUS" \
    && grep -q "результат: ЗЕЛЁНЫЙ" "$NIGHTLY_STATUS" \
    && ok "новый коммит: полный check с анализаторами, sha и зелёный статус записаны" \
    || fail "новый коммит: код=$code, аргументы: $(cat "$FAKE_ARGS" 2>/dev/null), статус: $(cat "$NIGHTLY_STATUS" 2>/dev/null)"

: >"$FAKE_ARGS"
out=$(bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 0 ] && [ ! -s "$FAKE_ARGS" ] \
    && ok "повтор без изменений: код=0, сборки нет" \
    || fail "повтор без изменений: код=$code, аргументы: $(cat "$FAKE_ARGS" 2>/dev/null)"

echo three >>"$upstream/file.txt"
git -C "$upstream" add file.txt
git -C "$upstream" commit -qm "третий коммит"
red_sha="$(git -C "$upstream" rev-parse main)"
export NIGHTLY_GRADLEW="$work/bin/gradlew-red"
: >"$FAKE_ARGS"

out=$(bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 1 ] && grep -q "результат: КРАСНЫЙ" "$NIGHTLY_STATUS" \
    && grep -q "SPOTBUGS 1" "$NIGHTLY_STATUS" \
    && grep -q "MS_EXAMPLE" "$NIGHTLY_STATUS" \
    && [ "$(cat "$NIGHTLY_STATE")" = "$red_sha" ] \
    && ok "находка: код=1, статус красный, SpotBugs-находка в списке, sha записан" \
    || fail "находка: код=$code, статус: $(cat "$NIGHTLY_STATUS" 2>/dev/null)"

cat >"$work/bin/gradlew-reports" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"${FAKE_ARGS:?}"
mkdir -p mod/build/reports/spotbugs mod/build/reports/pmd mod/build/reports/checkstyle mod/build/test-results/test
{
    echo '<?xml version="1.0" encoding="UTF-8"?>'
    echo '<BugCollection>'
    for i in $(seq 0 16); do
        echo "  <BugInstance type=\"MS_$i\"><ShortMessage>находка $i</ShortMessage></BugInstance>"
    done
    echo '</BugCollection>'
} >mod/build/reports/spotbugs/main.xml
cat >mod/build/reports/pmd/main.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0">
  <file name="mod/src/main/java/com/theminespirit/core/Main.java">
    <violation beginline="7" rule="Guard"/>
  </file>
</pmd>
XML
cat >mod/build/reports/checkstyle/main.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<checkstyle>
  <file name="mod/src/main/java/com/theminespirit/core/Other.java">
    <error line="9" message="строка длиннее 120"/>
  </file>
</checkstyle>
XML
printf '<not-xml' >mod/build/reports/pmd/broken.xml
cat >mod/build/test-results/test/TEST-Main.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite>
  <testcase classname="pkg.MainTest" name="fails"><failure message="упал"/></testcase>
  <testcase classname="pkg.OtherTest" name="errs"><error message="ошибка"/></testcase>
  <testcase classname="pkg.OkTest" name="ok"/>
</testsuite>
XML
echo "BUILD FAILED"
exit 1
FAKE
chmod +x "$work/bin/gradlew-reports"

export NIGHTLY_GRADLEW="$work/bin/gradlew-reports"
: >"$FAKE_ARGS"
out=$(NIGHTLY_FORCE=1 bash scripts/nightly-analyzers.sh 2>&1)
code=$?
if [ "$code" = 1 ] && grep -q "SPOTBUGS 17" "$NIGHTLY_STATUS" \
    && grep -q "mod MS_0 находка 0" "$NIGHTLY_STATUS" \
    && grep -q "… ещё 2" "$NIGHTLY_STATUS" \
    && grep -q "mod Main.java:7 Guard" "$NIGHTLY_STATUS" \
    && grep -q "mod Other.java:9 строка длиннее 120" "$NIGHTLY_STATUS" \
    && grep -q "TESTS 2" "$NIGHTLY_STATUS" \
    && grep -q "pkg.MainTest.fails упал" "$NIGHTLY_STATUS" \
    && grep -q "pkg.OtherTest.errs ошибка" "$NIGHTLY_STATUS" \
    && grep -q "НЕ ПРОЧИТАН mod/build/reports/pmd/broken.xml" "$NIGHTLY_STATUS" \
    && ! grep -q "нет python3" "$NIGHTLY_STATUS"; then
    ok "сводка: имена модулей и файлов (в т.ч. PMD с namespace), предел 15, ошибки тестов, битый XML назван"
else
    fail "сводка: код=$code, статус: $(cat "$NIGHTLY_STATUS" 2>/dev/null)"
fi

export NIGHTLY_GRADLEW="$work/bin/gradlew-green"
: >"$FAKE_ARGS"
out=$(NIGHTLY_FORCE=1 bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 0 ] && grep -q -- "-Ptheminespirit.analyzers=on check" "$FAKE_ARGS" \
    && grep -q "результат: ЗЕЛЁНЫЙ" "$NIGHTLY_STATUS" \
    && ok "NIGHTLY_FORCE=1: сборка без изменений, статус снова зелёный" \
    || fail "NIGHTLY_FORCE=1: код=$code, аргументы: $(cat "$FAKE_ARGS" 2>/dev/null)"

out=$(NIGHTLY_DRY_RUN=1 NIGHTLY_FORCE=1 bash scripts/nightly-analyzers.sh 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "план:" && [ "$(cat "$NIGHTLY_STATE")" = "$red_sha" ] \
    && ok "--dry-run: показывает план и не меняет состояние" \
    || fail "--dry-run: код=$code, вывод: $out"

rm -rf "$work"
exit $failed
