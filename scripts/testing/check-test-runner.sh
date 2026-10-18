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

# ── Разбор 6000 отчётов: одним процессом и быстро ────────────────────────────
# Прежний разбор делал ~18 fork на XML: на Windows 50 файлов разбирались 150 с, 640 — полчаса, и test.sh
# после BUILD SUCCESSFUL висел, держа пульс свежим. Фейковый gradle одним awk пишет 6000 XML (в каждом
# 1–5 тестов; падение в файлах i%1000==7, ошибка в i%2500==11) и время своего конца; от конца gradle до
# итога test.sh должно пройти меньше REPORT_LIMIT секунд.
REPORT_LIMIT=30
cat >"$work/bin/many-reports.awk" <<'GEN'
BEGIN {
    for (i = 1; i <= n; i++) {
        f = dir "/TEST-com.example.Suite" i ".xml"
        t = (i % 5) + 1; bad = (i % 1000 == 7); err = (i % 2500 == 11)
        print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" > f
        print "<testsuite name=\"com.example.Suite" i "\" tests=\"" t "\" skipped=\"0\" failures=\"" bad "\" errors=\"" err "\" time=\"0.01\">" > f
        for (k = 1; k <= t; k++) {
            if (k == 1 && (bad || err)) {
                tagname = bad ? "failure" : "error"
                print "  <testcase name=\"case" k "\" classname=\"com.example.Suite" i "\" time=\"0.001\">" > f
                print "    <" tagname " message=\"boom\" type=\"java.lang.AssertionError\">java.lang.AssertionError: boom" > f
                print "  </" tagname ">" > f
                print "  </testcase>" > f
            } else {
                print "  <testcase name=\"case" k "\" classname=\"com.example.Suite" i "\" time=\"0.001\"/>" > f
            }
        }
        # Вывод теста с «тегами» внутри CDATA — не тесты и не падения.
        print "  <system-out><![CDATA[<testsuite tests=\"99\" failures=\"99\"> <failure message=\"x\">" > f
        print "]]></system-out>" > f
        print "</testsuite>" > f
        close(f)
    }
}
GEN
cat >"$work/bin/many-reports" <<'FAKE'
#!/usr/bin/env bash
mkdir -p TestResults/unit/xml
awk -v n="${FAKE_XML_COUNT:?}" -v dir=TestResults/unit/xml -f "${FAKE_GEN:?}"
echo "BUILD SUCCESSFUL"
date +%s >"${FAKE_DONE:?}"
exit 0
FAKE
chmod +x "$work/bin/many-reports"
# 6000 файлов: сумма (i%5)+1 = 18000 тестов; падения 7,1007…5007 (6), ошибки 11,2511,5011 (3) — 9 упавших.
for parser in default none; do
    rm -f "$work/many.done"
    out=$(THEMINESPIRIT_PYTHON="$([ "$parser" = none ] && echo none)" THEMINESPIRIT_GRADLEW="$work/bin/many-reports" \
        FAKE_XML_COUNT=6000 FAKE_GEN="$work/bin/many-reports.awk" FAKE_DONE="$work/many.done" \
        bash scripts/test.sh unit 2>&1)
    code=$?
    finished=$(date +%s)
    done_at=$(cat "$work/many.done" 2>/dev/null || echo 0)
    took=$((finished - done_at))
    [ "$code" = 1 ] && echo "$out" | grep -q "FAIL unit: прошло 17991, упало 9" \
        && echo "$out" | grep -qx "com.example.Suite7.case1" \
        && echo "$out" | grep -qx "com.example.Suite11.case1" \
        && ok "many-reports ($parser): 6000 XML — 18000 тестов, 9 упавших, имена упавших" \
        || fail "many-reports ($parser): код=$code, вывод: $(echo "$out" | head -8)"
    [ "$done_at" -gt 0 ] && [ "$took" -lt "$REPORT_LIMIT" ] \
        && ok "many-reports ($parser): итог через $took с после конца gradle (предел $REPORT_LIMIT)" \
        || fail "many-reports ($parser): итог через $took с после конца gradle (предел $REPORT_LIMIT)"
done
rm -f TestResults/unit/xml/TEST-*.xml

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

# ── Свои фоновые процессы test.sh не переживают его выход ────────────────────
# На Windows зависший test.sh держал 3–5 своих bash (пульс, монитор, подоболочки), пульс оставался свежим,
# и надзор исполнителя считал агента занятым. Фейковый gradle записывает: всех потомков test.sh, кроме своего
# дерева (пульс, монитор и их sleep), PID пульса из файла пульса, свой PID и PID своего «рабочего» sleep.

# Живые PID из файла $1 (по одному в строке) — через пробел; пусто — все мертвы.
alive_from() {
    local pid left=""
    for pid in $(cat "$1" 2>/dev/null); do
        kill -0 "$pid" 2>/dev/null && left="$left $pid"
    done
    printf '%s' "$left"
}

# Уборка за красным тестом: только PID из своих файлов.
kill_from() {
    local pid
    for pid in $(cat "$@" 2>/dev/null); do kill -KILL "$pid" 2>/dev/null; done
}

# Запуск test.sh в фоне со страховкой: не вышел за $1 с — KILL по PID (старый test.sh висел в wait вечно).
# Код — в $code, вывод — в $out, время — в $took.
run_limited() {
    local limit="$1" pid i=0 start
    shift
    start=$(date +%s)
    "$@" >"$work/limited.out" 2>&1 &
    pid=$!
    while kill -0 "$pid" 2>/dev/null && [ "$i" -lt $((limit * 10)) ]; do
        sleep 0.1; i=$((i + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then
        kill -KILL "$pid" 2>/dev/null
        wait "$pid" 2>/dev/null
        code=timeout
    else
        wait "$pid"; code=$?
    fi
    took=$(( $(date +%s) - start ))
    out=$(cat "$work/limited.out")
}

cat >"$work/bin/record-kids" <<'FAKE'
#!/usr/bin/env bash
if [ -n "${FAKE_STUBBORN:-}" ]; then
    # «Gradle», который не слушает TERM (игнор наследует и его sleep): гасится только KILL.
    trap '' TERM
fi
echo $$ >"${FAKE_SELF:?}"
# Пульс пишет свой PID сразу после старта — ждём, пока запишет.
i=0
while [ "$i" -lt 50 ] && [ ! -s "${THEMINESPIRIT_TEST_HEARTBEAT:?}" ]; do sleep 0.1; i=$((i + 1)); done
cat "$THEMINESPIRIT_TEST_HEARTBEAT" >"${FAKE_HB:?}"
# Потомки родителя (test.sh), кроме себя и своих потомков.
ps -ef | awk -v root="$PPID" -v me="$$" '
    $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ { kids[$3] = kids[$3] " " $2 }
    END {
        q[1] = root; h = 1; t = 1
        while (h <= t) {
            p = q[h++]; n = split(kids[p], l, " ")
            for (k = 1; k <= n; k++) if (l[k] != me) { q[++t] = l[k]; print l[k] }
        }
    }' >"${FAKE_KIDS:?}"
if [ -n "${FAKE_HANG:-}" ]; then
    sleep 300 &
    echo $! >"${FAKE_WORKER:?}"
    wait $!
    exit 0
fi
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Kids.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Kids" tests="1" failures="0" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Kids"/>
</testsuite>
XML
echo "BUILD SUCCESSFUL"
exit 0
FAKE
chmod +x "$work/bin/record-kids"
export THEMINESPIRIT_TEST_HEARTBEAT="$work/heartbeat" FAKE_KIDS="$work/kids.pids" FAKE_HB="$work/hb.pid" \
       FAKE_SELF="$work/gradle.pid" FAKE_WORKER="$work/worker.pid"

# 1. Обычный зелёный прогон: после выхода нет ни пульса, ни монитора, ни их sleep; файл пульса убран.
: >"$FAKE_KIDS"; : >"$FAKE_HB"
run_limited 60 env THEMINESPIRIT_GRADLEW="$work/bin/record-kids" bash scripts/test.sh unit
sleep 0.5
hb_pid=$(tr -dc '0-9' <"$FAKE_HB")
left=$(alive_from "$FAKE_KIDS")
[ "$code" = 0 ] && echo "$out" | grep -q "OK unit: 1 тест" \
    && ok "own-procs: зелёный прогон, код=0" || fail "own-procs: код=$code, вывод: $out"
[ -n "$hb_pid" ] && grep -qx "$hb_pid" "$FAKE_KIDS" && [ -s "$FAKE_KIDS" ] && [ -z "$left" ] \
    && ok "own-procs: пульс (pid $hb_pid) и прочие фоновые процессы test.sh не пережили выход" \
    || fail "own-procs: пульс=$hb_pid, потомки=[$(tr '\n' ' ' <"$FAKE_KIDS")], живы:[$left]"
[ ! -e "$THEMINESPIRIT_TEST_HEARTBEAT" ] \
    && ok "own-procs: файл пульса убран" || fail "own-procs: файл пульса остался"
kill_from "$FAKE_KIDS"

# 2. Чужой TERM посреди прогона: test.sh выходит сразу (код 143), гасит свой gradle, его sleep и пульс.
: >"$FAKE_KIDS"; : >"$FAKE_HB"; : >"$FAKE_SELF"; : >"$FAKE_WORKER"
FAKE_HANG=1 THEMINESPIRIT_GRADLEW="$work/bin/record-kids" bash scripts/test.sh unit >"$work/term.out" 2>&1 &
term_pid=$!
i=0
while [ "$i" -lt 300 ] && [ ! -s "$FAKE_WORKER" ]; do sleep 0.1; i=$((i + 1)); done
kill -TERM "$term_pid" 2>/dev/null
i=0
while [ "$i" -lt 300 ] && kill -0 "$term_pid" 2>/dev/null; do sleep 0.1; i=$((i + 1)); done
if kill -0 "$term_pid" 2>/dev/null; then
    kill -KILL "$term_pid" 2>/dev/null; code=timeout
else
    wait "$term_pid"; code=$?
fi
sleep 0.5
left=$(alive_from "$FAKE_KIDS")$(alive_from "$FAKE_SELF")$(alive_from "$FAKE_WORKER")
[ "$code" = 143 ] && [ -s "$FAKE_WORKER" ] && [ -z "$left" ] \
    && ok "own-procs-term: TERM — код 143, gradle, его sleep и пульс погашены" \
    || fail "own-procs-term: код=$code, живы:[$left], вывод: $(cat "$work/term.out")"
kill_from "$FAKE_KIDS" "$FAKE_SELF" "$FAKE_WORKER"

# 3. Предел времени, gradle глух к TERM: монитор добивает его дерево KILL, test.sh выходит с 124
#    (старый test.sh ждал такой gradle в wait вечно, а метка .stopped появлялась без выхода).
: >"$FAKE_KIDS"; : >"$FAKE_HB"; : >"$FAKE_SELF"; : >"$FAKE_WORKER"
run_limited 60 env FAKE_HANG=1 FAKE_STUBBORN=1 THEMINESPIRIT_GRADLEW="$work/bin/record-kids" \
    THEMINESPIRIT_UNIT_WARN_SECONDS=1 THEMINESPIRIT_UNIT_STOP_SECONDS=2 bash scripts/test.sh unit
sleep 0.5
left=$(alive_from "$FAKE_KIDS")$(alive_from "$FAKE_SELF")$(alive_from "$FAKE_WORKER")
[ "$code" = 124 ] && echo "$out" | grep -q "стоп по пределу 2 с" \
    && ok "limit-kill: глухой к TERM gradle — код 124 за $took с" \
    || fail "limit-kill: код=$code за $took с, вывод: $out"
[ -s "$FAKE_SELF" ] && [ -s "$FAKE_WORKER" ] && [ -z "$left" ] \
    && ok "limit-kill: gradle, его sleep, пульс и монитор погашены" \
    || fail "limit-kill: живы:[$left] (gradle $(cat "$FAKE_SELF"), sleep $(cat "$FAKE_WORKER"))"
kill_from "$FAKE_KIDS" "$FAKE_SELF" "$FAKE_WORKER"
unset THEMINESPIRIT_TEST_HEARTBEAT FAKE_KIDS FAKE_HB FAKE_SELF FAKE_WORKER

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

# unit всегда с --rerun: стёртые XML не вернутся из UP-TO-DATE/FROM-CACHE
out=$(ARGS_FILE="$argsfile" bash scripts/test.sh unit --tests 'com.example.SomeTests' 2>&1)
grep -qx "test" "$argsfile" && grep -qx -- "--rerun" "$argsfile" \
    && ok "unit-rerun: gradle test идёт с --rerun" \
    || fail "unit-rerun: args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

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

# ── Стоп по пределу гасит и сервер GameTest своего worktree (todo.md 17.10) ──────────────────
# Демон Gradle запускает сервер игры отдельным процессом с рабочей папкой <worktree>/build/gametest-run.
# Фейковый gradlew повторяет это: отдельной сессией (setsid) поднимает «сервер» — долгий процесс с
# именем java и cwd build/gametest-run, — а сам остаётся «клиентом». Стоп по пределу обязан погасить
# оба, а замок снять только после смерти сервера (иначе следующий прогон идёт параллельно).
# Сценарий только для Linux: поиск сервера идёт по /proc/<pid>/cwd, в Git Bash его нет.
case "$OSTYPE" in
    linux*)
        cp "$(command -v sleep)" "$work/bin/java"
        chmod +x "$work/bin/java"
        cat >"$work/bin/stop-server" <<'FAKE'
#!/usr/bin/env bash
echo $$ >"${FAKE_CLIENT_PID:?}"
run_dir="${FAKE_RUN_DIR:?}"
mkdir -p "$run_dir"
( cd "$run_dir" && exec setsid "${FAKE_JAVA:?}" 300 ) >"${FAKE_SERVER_LOG:?}" 2>&1 &
echo "$!" >"${FAKE_SERVER_PID:?}"
sleep 300
exit 0
FAKE
        chmod +x "$work/bin/stop-server"

        run_dir="$(cd "$root" && pwd -P)/build/gametest-run"
        mkdir -p "$run_dir"
        server_pid_file="$work/server.pid"; : >"$server_pid_file"
        client_pid_file="$work/client.pid"; : >"$client_pid_file"
        order_file="$work/order.txt"; : >"$order_file"
        stopwatch="$work/stopwatch.stop"; rm -f "$stopwatch"

        # Наблюдатель: замок не должен исчезнуть, пока сервер жив. Если снять замок раньше гашения —
        # следующий прогон пошёл бы параллельно (ровно дефект из todo.md).
        (
            # Сначала ждём, пока test.sh возьмёт замок (до этого его нет — это не нарушение).
            i=0
            while [ "$i" -lt 300 ] && [ ! -d "$work/tmp/lock.d" ] && [ ! -f "$stopwatch" ]; do
                sleep 0.05; i=$((i + 1))
            done
            while [ ! -f "$stopwatch" ]; do
                if [ ! -d "$work/tmp/lock.d" ]; then
                    pid=$(cat "$server_pid_file" 2>/dev/null || echo "")
                    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
                        echo "lock-free-while-server-alive" >>"$order_file"
                    fi
                    break
                fi
                sleep 0.1
            done
        ) &
        watcher=$!

        export THEMINESPIRIT_GRADLEW="$work/bin/stop-server"
        export THEMINESPIRIT_GAMETEST_NOFLOCK=1
        export THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock"
        export THEMINESPIRIT_GAMETEST_WARN_SECONDS=1
        export THEMINESPIRIT_GAMETEST_STOP_SECONDS=2
        export FAKE_RUN_DIR="$run_dir" FAKE_JAVA="$work/bin/java" \
               FAKE_CLIENT_PID="$client_pid_file" FAKE_SERVER_PID="$server_pid_file" \
               FAKE_SERVER_LOG="$work/server.log"
        out=$(bash scripts/test.sh gametest 2>&1)
        code=$?
        # Пауза до остановки наблюдателя: дать ему увидеть состояние «замка нет» и проверить, жив ли сервер.
        sleep 2
        touch "$stopwatch"; wait "$watcher" 2>/dev/null

        server_pid=$(cat "$server_pid_file" 2>/dev/null || echo "")
        client_pid=$(cat "$client_pid_file" 2>/dev/null || echo "")
        sleep 1
        server_alive=no; kill -0 "$server_pid" 2>/dev/null && server_alive=yes
        client_alive=no; kill -0 "$client_pid" 2>/dev/null && client_alive=yes

        [ "$code" = 124 ] && echo "$out" | grep -q "стоп по пределу 2 с" \
            && ok "stop-server: код=124, стоп по пределу" \
            || fail "stop-server: код=$code, вывод: $out"
        [ "$server_alive" = no ] && [ -n "$server_pid" ] \
            && ok "stop-server: сервер GameTest погашен (pid $server_pid)" \
            || fail "stop-server: сервер GameTest жив (pid $server_pid)"
        [ "$client_alive" = no ] && [ -n "$client_pid" ] \
            && ok "stop-server: клиент gradle погашен (pid $client_pid)" \
            || fail "stop-server: клиент gradle жив (pid $client_pid)"
        [ ! -d "$work/tmp/lock.d" ] \
            && ok "stop-server: замок снят" || fail "stop-server: каталог замка остался"
        [ ! -s "$order_file" ] \
            && ok "stop-server: замок снят только после смерти сервера" \
            || fail "stop-server: замок освобождён раньше смерти сервера"

        # Уборка, если тест красный: чужой worktree не трогаем — только свои pid.
        [ "$server_alive" = yes ] && kill -KILL "$server_pid" 2>/dev/null
        [ "$client_alive" = yes ] && kill -KILL "$client_pid" 2>/dev/null
        rm -rf "$work/tmp/lock.d"

        # Выход по сигналу гасит сервер так же, как стоп по пределу (trap → stop_monitor).
        # Сигнал — TERM: фоновый процесс неинтерактивного скрипта игнорирует INT (SIG_IGN при `&`),
        # ровно как градл-клиент в отдельной сессии не получил бы Ctrl+C; в бою Ctrl+C приходит в
        # группу переднего плана, и trap ловит его тем же обработчиком INT/TERM.
        : >"$server_pid_file"; : >"$client_pid_file"; rm -f "$work/tmp/lock.d"
        export THEMINESPIRIT_GAMETEST_WARN_SECONDS=600
        export THEMINESPIRIT_GAMETEST_STOP_SECONDS=600
        bash scripts/test.sh gametest >"$work/signal.out" 2>&1 &
        sig_test=$!
        i=0
        while [ "$i" -lt 100 ]; do
            [ -n "$(cat "$server_pid_file" 2>/dev/null)" ] && break
            sleep 0.1; i=$((i + 1))
        done
        kill -TERM "$sig_test" 2>/dev/null
        wait "$sig_test" 2>/dev/null
        sleep 1
        sig_server=$(cat "$server_pid_file" 2>/dev/null || echo "")
        sig_client=$(cat "$client_pid_file" 2>/dev/null || echo "")
        sig_alive=no; kill -0 "$sig_server" 2>/dev/null && sig_alive=yes
        [ "$sig_alive" = no ] && [ -n "$sig_server" ] \
            && ok "signal-server: сервер GameTest погашен по TERM (pid $sig_server)" \
            || fail "signal-server: сервер GameTest жив после TERM (pid $sig_server)"
        [ "$sig_alive" = yes ] && kill -KILL "$sig_server" 2>/dev/null
        # Клиент gradle test.sh гасит сам (cleanup); за красным прогоном в тесте добиваем свой по PID.
        kill -KILL "$sig_client" 2>/dev/null
        rm -rf "$work/tmp/lock.d"
        ;;
    *)
        ok "stop-server: пропущено (не Linux: поиск сервера по /proc/<pid>/cwd недоступен)"
        ;;
esac

exit $failed
