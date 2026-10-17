#!/usr/bin/env bash
# Регрессионная проверка scripts/test.sh на фейковом gradle без Gradle (секунды).
# Каждая строка вывода начинается с OK или FAIL; код выхода 1 при FAIL.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/test-runner-check"
rm -rf "$work"
mkdir -p "$work/bin" "$work/tmp"
failed=0

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

# ── Фейковые gradlew ─────────────────────────────────────────────────────────

# Успех unit: пишет XML-отчёт и выходит 0
cat >"$work/bin/unit-success" <<'FAKE'
#!/usr/bin/env bash
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Success.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Success" tests="3" failures="0" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Success"/>
  <testcase name="test2" classname="com.example.Success"/>
  <testcase name="test3" classname="com.example.Success"/>
</testsuite>
XML
echo "BUILD SUCCESSFUL"
exit 0
FAKE

# Падение unit: пишет XML с падениями и выходит 1
cat >"$work/bin/unit-fail" <<'FAKE'
#!/usr/bin/env bash
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Fail.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Fail" tests="3" failures="2" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Fail"/>
  <testcase name="test2" classname="com.example.Fail">
    <failure message="expected: &lt;1&gt; but was: &lt;2&gt;">java.lang.AssertionError: expected: &lt;1&gt; but was: &lt;2&gt;
    at com.example.Fail.test2(Fail.java:10)
    at org.junit.platform.engine...
  </failure>
  </testcase>
  <testcase name="test3" classname="com.example.Fail">
    <failure message="NPE">java.lang.NullPointerException: null
    at com.example.Fail.test3(Fail.java:20)
  </failure>
  </testcase>
</testsuite>
XML
echo "com.example.Fail.test2 FAILED"
echo "com.example.Fail.test3 FAILED"
echo "java.lang.AssertionError: expected: <1> but was: <2>"
echo "    at com.example.Fail.test2(Fail.java:10)"
exit 1
FAKE

# Ошибка компиляции
cat >"$work/bin/unit-build-error" <<'FAKE'
#!/usr/bin/env bash
echo "src/main/java/com/example/Broken.java:10: error: cannot find symbol"
echo "BUILD FAILED"
exit 1
FAKE

# Долгий запуск (для проверки предела)
cat >"$work/bin/slow" <<'FAKE'
#!/usr/bin/env bash
sleep 30
exit 0
FAKE

# Успех gametest: таймстамп (число тестов не берётся из часов) и имя пачки со словом «failed»
# (не должно читаться как провал)
cat >"$work/bin/gametest-success" <<'FAKE'
#!/usr/bin/env bash
echo "[10:18:40] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'rest_events_failed:0' (1 tests)..."
echo "[10:18:43] [Server thread/INFO] [minecraft/GameTestServer]: All 42 required tests passed :)"
exit 0
FAKE

# Падение gametest: формат строки-итога сервера
cat >"$work/bin/gametest-fail" <<'FAKE'
#!/usr/bin/env bash
echo "[10:00:00] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'combat_directive:0' (1 tests)..."
echo "[10:00:01] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :("
echo "[10:00:01] [Server thread/INFO] [minecraft/GameTestServer]:    - combatgametests.combat_target_spirit"
exit 1
FAKE

chmod +x "$work"/bin/*

# ── Тесты ────────────────────────────────────────────────────────────────────

export THEMINESPIRIT_GRADLEW="$work/bin/unit-success"
out=$(bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "OK unit: 3 тестов" \
    && ok "unit-success: код=0, строка OK" \
    || fail "unit-success: код=$code, вывод: $out"

export THEMINESPIRIT_GRADLEW="$work/bin/unit-fail"
out=$(bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "FAIL unit: прошло 1, упало 2" \
    && echo "$out" | grep -q "expected: <1> but was: <2>" \
    && echo "$out" | grep -q "полный лог:" \
    && ok "unit-fail: код=1, счётчик + первая ошибка + путь к логу" \
    || fail "unit-fail: код=$code, вывод: $out"

export THEMINESPIRIT_GRADLEW="$work/bin/unit-build-error"
out=$(bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "BUILD ERROR\|error:" \
    && ok "unit-build-error: код=1, BUILD ERROR + ошибка компиляции" \
    || fail "unit-build-error: код=$code, вывод: $out"

# Предел unit: уменьшаем до 2 с
export THEMINESPIRIT_GRADLEW="$work/bin/slow"
export THEMINESPIRIT_UNIT_WARN_SECONDS=1
export THEMINESPIRIT_UNIT_STOP_SECONDS=2
out=$(bash scripts/test.sh unit 2>&1)
code=$?
unset THEMINESPIRIT_UNIT_WARN_SECONDS THEMINESPIRIT_UNIT_STOP_SECONDS
[ "$code" = 124 ] && echo "$out" | grep -q "стоп по пределу 2 с" \
    && ok "unit-limit: код=124, стоп по пределу" \
    || fail "unit-limit: код=$code, вывод: $out"

# Предел gametest: уменьшаем до 2 с
export THEMINESPIRIT_GRADLEW="$work/bin/slow"
export THEMINESPIRIT_GAMETEST_WARN_SECONDS=1
export THEMINESPIRIT_GAMETEST_STOP_SECONDS=2
export THEMINESPIRIT_GAMETEST_NOFLOCK=1
export THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock"
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
unset THEMINESPIRIT_GAMETEST_WARN_SECONDS THEMINESPIRIT_GAMETEST_STOP_SECONDS
[ "$code" = 124 ] && echo "$out" | grep -q "стоп по пределу 2 с" \
    && ok "gametest-limit: код=124, стоп по пределу" \
    || fail "gametest-limit: код=$code, вывод: $out"

# Успех gametest
export THEMINESPIRIT_GRADLEW="$work/bin/gametest-success"
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "OK gametest: 42 тест" \
    && ok "gametest-success: код=0, строка OK" \
    || fail "gametest-success: код=$code, вывод: $out"

# Падение gametest
export THEMINESPIRIT_GRADLEW="$work/bin/gametest-fail"
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "FAIL gametest" \
    && ok "gametest-fail: код=1, FAIL" \
    || fail "gametest-fail: код=$code, вывод: $out"

# Фильтр должен дойти до gradle системным свойством (build.gradle читает его через System.getProperty)
cat >"$work/bin/args-record" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$@" >"${ARGS_FILE:?}"
echo "All 2 required tests passed"
exit 0
FAKE
chmod +x "$work/bin/args-record"
export THEMINESPIRIT_GRADLEW="$work/bin/args-record"
argsfile="$work/gradle-args.txt"
out=$(ARGS_FILE="$argsfile" bash scripts/test.sh gametest combat_target_spirit 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.filter=combat_target_spirit" "$argsfile" \
    && grep -qx "runGameTestServer" "$argsfile" \
    && ok "gametest-filter: имя пачки уходит в gradle системным свойством" \
    || fail "gametest-filter: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

out=$(ARGS_FILE="$argsfile" bash scripts/test.sh gametest combat_a combat_b 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.filter=combat_a,combat_b" "$argsfile" \
    && ok "gametest-filter: несколько фильтров через запятую" \
    || fail "gametest-filter: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

# Замок и токен уходят в gradle системными свойствами: build.gradle сверяет их с маркером
# замка в runGameTestServer, поэтому прямой ./gradlew runGameTestServer падает (docs/TESTING.md).
out=$(ARGS_FILE="$argsfile" THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.lock=$work/tmp/lock" "$argsfile" \
    && grep -q -- "-Dtheminespirit.gametest.token=" "$argsfile" \
    && ok "gametest-lock-props: замок и токен уходят в gradle системными свойствами" \
    || fail "gametest-lock-props: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

# Параллельный gametest ждёт замок: занимаем замок живым процессом
rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
sleep 30 &
holder=$!
echo $holder >"$work/tmp/lock.d/pid"
export THEMINESPIRIT_GAMETEST_WAIT=2
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
kill $holder 2>/dev/null
[ "$code" = 75 ] && echo "$out" | grep -q "чужой GameTest" \
    && ok "gametest-busy: код=75, ждёт замок" \
    || fail "gametest-busy: код=$code, вывод: $out"
rm -rf "$work/tmp/lock.d"

# Параллельный запуск: два процесса не пересекаются
cat >"$work/bin/parallel" <<'FAKE'
#!/usr/bin/env bash
log="${FAKE_LOG:?}"
echo "start $(date +%s%N)" >>"$log"
sleep "${FAKE_SLEEP:-2}"
echo "end $(date +%s%N)" >>"$log"
echo "All 42 required tests passed"
exit 0
FAKE
chmod +x "$work/bin/parallel"
export THEMINESPIRIT_GAMETEST_NOFLOCK=1
export THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock"
log="$work/parallel.log"; : >"$log"
export FAKE_LOG="$log" FAKE_SLEEP=2
THEMINESPIRIT_GRADLEW="$work/bin/parallel" bash scripts/test.sh gametest >"$work/parallel1.out" 2>&1 &
p1=$!
sleep 1
THEMINESPIRIT_GRADLEW="$work/bin/parallel" bash scripts/test.sh gametest >"$work/parallel2.out" 2>&1 &
p2=$!
wait "$p1"; wait "$p2"
starts=$(grep -c '^start ' "$log")
ends=$(grep -c '^end ' "$log")
first_end=$(grep '^end ' "$log" | head -n1 | awk '{print $2}')
last_start=$(grep '^start ' "$log" | tail -n1 | awk '{print $2}')
if [ "$starts" = 2 ] && [ "$ends" = 2 ] && [ "$last_start" -ge "$first_end" ]; then
    ok "parallel: запуски не пересекаются"
else
    fail "parallel: пересечение (стартов $starts, концов $ends)"
fi

# Стоп по пределу гасит только свой сервер: два параллельных «сервера» в разных каталогах,
# у каждого свой лог и свой предел. Стоп первого не должен убить второй.
cat >"$work/bin/stop-owner" <<'FAKE'
#!/usr/bin/env bash
log="${FAKE_LOG:?}"
echo "start $(date +%s%N)" >>"$log"
sleep "${FAKE_SLEEP:-2}"
echo "end $(date +%s%N)" >>"$log"
echo "All 42 required tests passed"
exit 0
FAKE
chmod +x "$work/bin/stop-owner"
export THEMINESPIRIT_GAMETEST_NOFLOCK=1
export THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock"
log1="$work/stop1.log"; : >"$log1"
log2="$work/stop2.log"; : >"$log2"
export FAKE_LOG="$log1" FAKE_SLEEP=10
export THEMINESPIRIT_GRADLEW="$work/bin/stop-owner"
export THEMINESPIRIT_GAMETEST_WARN_SECONDS=600
export THEMINESPIRIT_GAMETEST_STOP_SECONDS=600
bash scripts/test.sh gametest >"$work/stop1.out" 2>&1 &
p1=$!
sleep 0.5
export FAKE_LOG="$log2" FAKE_SLEEP=10
export THEMINESPIRIT_GAMETEST_WARN_SECONDS=600
export THEMINESPIRIT_GAMETEST_STOP_SECONDS=600
export THEMINESPIRIT_GAMETEST_WAIT=1
THEMINESPIRIT_GRADLEW="$work/bin/stop-owner" bash scripts/test.sh gametest >"$work/stop2.out" 2>&1 &
p2=$!
wait "$p1"; c1=$?
wait "$p2"; c2=$?
[ "$c1" = 0 ] && [ "$c2" = 75 ] \
    && ok "stop-owner: первый отработал (код 0), второй ждёт замок (код 75)" \
    || fail "stop-owner: коды $c1 и $c2 (ожидал 0 и 75)"
starts1=$(grep -c '^start ' "$log1")
ends1=$(grep -c '^end ' "$log1")
starts2=$(grep -c '^start ' "$log2")
ends2=$(grep -c '^end ' "$log2")
[ "$starts1" = 1 ] && [ "$ends1" = 1 ] && [ "$starts2" = 0 ] && [ "$ends2" = 0 ] \
    && ok "stop-owner: второй сервер не стартовал (замок занят)" \
    || fail "stop-owner: стартов1=$starts1 концов1=$ends1 стартов2=$starts2 концов2=$ends2"

exit $failed
