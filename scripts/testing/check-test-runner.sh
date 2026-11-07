#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/test-runner-check"
rm -rf "$work"
mkdir -p "$work/bin" "$work/tmp"
failed=0

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

export THEMINESPIRIT_GAMETEST_SHARDS=1


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

cat >"$work/bin/unit-static-fail" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >"${FAKE_ARGS_FILE:?}"
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Success.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Success" tests="3" failures="0" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Success"/>
</testsuite>
XML
echo "* What went wrong:"
echo "Execution failed for task ':spotlessJavaCheck'."
echo "BUILD FAILED"
exit 1
FAKE

cat >"$work/bin/unit-build-error" <<'FAKE'
#!/usr/bin/env bash
echo "src/main/java/com/example/Broken.java:10: error: cannot find symbol"
echo "BUILD FAILED"
exit 1
FAKE

cat >"$work/bin/slow" <<'FAKE'
#!/usr/bin/env bash
sleep 30
exit 0
FAKE

cat >"$work/bin/gametest-success" <<'FAKE'
#!/usr/bin/env bash
echo "[10:18:40] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'rest_events_failed:0' (1 tests)..."
echo "[10:18:43] [Server thread/INFO] [minecraft/GameTestServer]: All 42 required tests passed :)"
exit 0
FAKE

cat >"$work/bin/gametest-fail" <<'FAKE'
#!/usr/bin/env bash
echo "[10:00:00] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'combat_directive:0' (1 tests)..."
echo "[10:00:01] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :("
echo "[10:00:01] [Server thread/INFO] [minecraft/GameTestServer]:    - combatgametests.combat_target_spirit"
exit 1
FAKE

chmod +x "$work"/bin/*


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

export THEMINESPIRIT_GRADLEW="$work/bin/unit-static-fail"
export FAKE_ARGS_FILE="$work/static-args"
out=$(bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "FAIL static: 3 тестов зелёные"     && echo "$out" | grep -q "spotlessJavaCheck"     && grep -q "staticAnalysis --continue" "$work/static-args"     && ok "unit-static-fail: полный прогон гоняет staticAnalysis, красный анализатор при зелёных тестах — код=1"     || fail "unit-static-fail: код=$code, аргументы: $(cat "$work/static-args" 2>/dev/null), вывод: $out"
out=$(bash scripts/test.sh unit --tests '*TestCostReporterTests*' 2>&1)
grep -q "staticAnalysis" "$work/static-args"     && fail "unit-static-fail: прогон с --tests не должен гонять staticAnalysis: $(cat "$work/static-args")"     || ok "unit-static-fail: прогон с --tests идёт без анализаторов"
out=$(bash scripts/test.sh unit --fast-analyzers 2>&1)
grep -q "staticAnalysisFast --continue" "$work/static-args" \
    && ! grep -qE -- "spotbugs|pmd" "$work/static-args" \
    && ok "unit-fast-analyzers: хук гоняет staticAnalysisFast без SpotBugs/PMD" \
    || fail "unit-fast-analyzers: аргументы: $(cat "$work/static-args" 2>/dev/null)"
unset FAKE_ARGS_FILE

export THEMINESPIRIT_GRADLEW="$work/bin/unit-build-error"
out=$(bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "BUILD ERROR\|error:" \
    && ok "unit-build-error: код=1, BUILD ERROR + ошибка компиляции" \
    || fail "unit-build-error: код=$code, вывод: $out"

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

cat >"$work/bin/unit-cpu-heavy" <<'FAKE'
#!/usr/bin/env bash
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Heavy.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Heavy" tests="2" failures="0" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Heavy"/>
  <testcase name="test2" classname="com.example.Heavy"/>
</testsuite>
XML
printf 'class,test,kind,cpu_ms,wall_ms,gc_ms,warmup
com.example.Heavy,test1,junit,150000,1,0,false
com.example.Heavy,test2,junit,60000,1,0,false
'     >TestResults/unit/test-cost.csv
echo "BUILD SUCCESSFUL"
exit 0
FAKE
chmod +x "$work/bin/unit-cpu-heavy"
out=$(THEMINESPIRIT_GRADLEW="$work/bin/unit-cpu-heavy" bash scripts/test.sh unit 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "FAIL cpu: 2 тестов зелёные, но время процессора тестов 210 с больше предела 200 с"     && ok "unit-cpu: 210 с процессора тестов — красный при зелёных тестах"     || fail "unit-cpu: код=$code, вывод: $out"
rm -f TestResults/unit/xml/TEST-*.xml TestResults/unit/test-cost.csv

export THEMINESPIRIT_GRADLEW="$work/bin/slow"
export THEMINESPIRIT_UNIT_WARN_SECONDS=1
export THEMINESPIRIT_UNIT_STOP_SECONDS=2
out=$(bash scripts/test.sh unit 2>&1)
code=$?
unset THEMINESPIRIT_UNIT_WARN_SECONDS THEMINESPIRIT_UNIT_STOP_SECONDS
[ "$code" = 124 ] && echo "$out" | grep -q "стоп по пределу 2 с" \
    && ok "unit-limit: код=124, стоп по пределу" \
    || fail "unit-limit: код=$code, вывод: $out"


alive_from() {
    local pid left=""
    for pid in $(cat "$1" 2>/dev/null); do
        kill -0 "$pid" 2>/dev/null && left="$left $pid"
    done
    printf '%s' "$left"
}

kill_from() {
    local pid
    for pid in $(cat "$@" 2>/dev/null); do kill -KILL "$pid" 2>/dev/null; done
}

tree_of() {
    ps -ef 2>/dev/null | awk -v root="$1" '
        $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ { kids[$3] = kids[$3] " " $2 }
        END {
            q[1] = root; head = 1; tail = 1
            while (head <= tail) {
                p = q[head++]
                n = split(kids[p], list, " ")
                for (k = 1; k <= n; k++) { q[++tail] = list[k]; print list[k] }
            }
        }'
}

test_sh_alive() {
    local pid
    for pid in "$@"; do
        kill -0 "$pid" 2>/dev/null || continue
        ps -ef 2>/dev/null | awk -v p="$pid" '$2 == p && /scripts\/test\.sh/ { found = 1 } END { exit !found }' && printf '%s ' "$pid"
    done
}

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
    trap '' TERM
fi
echo $$ >"${FAKE_SELF:?}"
i=0
while [ "$i" -lt 50 ] && [ ! -s "${THEMINESPIRIT_TEST_HEARTBEAT:?}" ]; do sleep 0.1; i=$((i + 1)); done
cat "$THEMINESPIRIT_TEST_HEARTBEAT" >"${FAKE_HB:?}"
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

mkdir -p "$work/bin/sleep-log"
cat >"$work/bin/sleep-log/sleep" <<FAKE
#!/usr/bin/env bash
printf '%s\n' "\$PPID" >>"$work/sleep-spawns"
exec /usr/bin/sleep "\$@"
FAKE
chmod +x "$work/bin/sleep-log/sleep"

cat >"$work/bin/unit-hold" <<'FAKE'
#!/usr/bin/env bash
echo $$ >"${FAKE_SELF:?}"
sleep "${HOLD_SECONDS:-4}"
mkdir -p TestResults/unit/xml
cat >TestResults/unit/xml/TEST-com.example.Hold.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.Hold" tests="1" failures="0" errors="0" skipped="0">
  <testcase name="test1" classname="com.example.Hold"/>
</testsuite>
XML
echo "BUILD SUCCESSFUL"
exit 0
FAKE
chmod +x "$work/bin/unit-hold"

: >"$work/sleep-spawns"; : >"$work/gradle-hold.pid"
out=$(PATH="$work/bin/sleep-log:$PATH" HOLD_SECONDS=4 FAKE_SELF="$work/gradle-hold.pid" \
    THEMINESPIRIT_GRADLEW="$work/bin/unit-hold" bash scripts/test.sh unit 2>&1)
code=$?
hold_pid=$(tr -dc '0-9' <"$work/gradle-hold.pid")
foreign=0
if [ -n "$hold_pid" ]; then
    foreign=$(awk -v g="$hold_pid" '$1 + 0 != g + 0' "$work/sleep-spawns" | wc -l | tr -d ' ')
fi
[ "$code" = 0 ] && [ -n "$hold_pid" ] && [ "$foreign" = 0 ] \
    && ok "wait-cpu: ожидание надзора не порождает процессов sleep (внешних: 0), прогон зелёный" \
    || fail "wait-cpu: код=$code, gradle pid=$hold_pid, внешних sleep=$foreign, вывод: $out"

: >"$FAKE_KIDS"; : >"$FAKE_HB"; : >"$FAKE_SELF"; : >"$FAKE_WORKER"
FAKE_HANG=1 FAKE_KIDS="$work/kids.pids" FAKE_HB="$work/hb.pid" FAKE_SELF="$work/gradle.pid" \
    FAKE_WORKER="$work/worker.pid" THEMINESPIRIT_GRADLEW="$work/bin/record-kids" \
    bash scripts/test.sh unit >"$work/killmain.out" 2>&1 &
main_pid=$!
i=0
while [ "$i" -lt 300 ] && [ ! -s "$work/worker.pid" ]; do sleep 0.1; i=$((i + 1)); done
kids=$(tree_of "$main_pid")
kill -KILL "$main_pid" 2>/dev/null
sleep 0.2
left=""
i=0
while [ "$i" -lt 30 ]; do
    left=$(test_sh_alive $kids)
    [ -z "$left" ] && break
    sleep 0.1; i=$((i + 1))
done
[ -z "$left" ] \
    && ok "kill-main: после SIGKILL main надзор и пульс умирают за ≤3 с" \
    || fail "kill-main: живы потомки test.sh: [$left]"
kill_from "$work/gradle.pid" "$work/worker.pid" "$work/kids.pids"
wait "$main_pid" 2>/dev/null

cat >"$work/bin/hold-launch" <<'FAKE'
#!/usr/bin/env bash
"$@" >"$HOLD_OUT" 2>&1 &
echo $! >"$HOLD_KID"
sleep 2
FAKE
chmod +x "$work/bin/hold-launch"
: >"$FAKE_SELF"; : >"$FAKE_WORKER"; : >"$work/parent.out"; : >"$work/testsh.pid"
HOLD_OUT="$work/parent.out" HOLD_KID="$work/testsh.pid" \
    FAKE_HANG=1 FAKE_KIDS="$work/kids2.pids" FAKE_HB="$work/hb2.pid" \
    FAKE_SELF="$work/gradle2.pid" FAKE_WORKER="$work/worker2.pid" \
    THEMINESPIRIT_GRADLEW="$work/bin/record-kids" \
    bash "$work/bin/hold-launch" bash scripts/test.sh unit >/dev/null 2>&1
i=0
while [ "$i" -lt 80 ] && { [ ! -s "$work/testsh.pid" ] || [ ! -s "$work/worker2.pid" ]; }; do
    sleep 0.1; i=$((i + 1))
done
testsh=$(tr -dc '0-9' <"$work/testsh.pid")
i=0
while [ "$i" -lt 80 ] && [ -n "$testsh" ] && kill -0 "$testsh" 2>/dev/null; do
    sleep 0.1; i=$((i + 1))
done
orphan_gradle=$(tr -dc '0-9' <"$work/gradle2.pid")
gradle_alive=0
[ -n "$orphan_gradle" ] && kill -0 "$orphan_gradle" 2>/dev/null && gradle_alive=1
if [ -n "$testsh" ] && ! kill -0 "$testsh" 2>/dev/null && [ "$gradle_alive" = 0 ]; then
    ok "parent-death: осиротевший test.sh погасил себя и gradle (≤8 с)"
else
    fail "parent-death: test.sh=$testsh жив=$(kill -0 "$testsh" 2>/dev/null && echo да || echo нет), gradle=$orphan_gradle жив=$gradle_alive"
fi
kill_from "$work/gradle2.pid" "$work/worker2.pid" "$work/kids2.pids"
[ -n "$testsh" ] && kill -KILL "$testsh" 2>/dev/null

cat >"$work/orphans.txt" <<'FIX'
100	1	0	90	opencode.exe	opencode run --dir F:/repos/minecraft/wt-foo
101	100	15	80	bash.exe	scripts/opencode-task.sh -f ../oc-results/task-x.msg
200	101	30	70	bash.exe	scripts/test.sh unit --module guards
300	999	7700	15	bash.exe	scripts/test.sh unit --module guards --tests *FileSizeGuardTests*
400	300	120	5	java.exe	java -Dfml.modFolders=x F:/repos/minecraft/wt-activitytickets
500	101	0	40	sh.exe	./gradlew :board:test
600	500	40	3	bash.exe	scripts/test.sh gametest
FIX
out=$(ORPHANS_WIN_LIST="$work/orphans.txt" bash scripts/lead/orphans-win.sh 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "сироты test.sh/gradlew/java: 2" \
    && echo "$out" | grep -qE '^300[[:space:]]+7700[[:space:]]+15' \
    && echo "$out" | grep -q "wt-activitytickets" \
    && ! echo "$out" | grep -qE '^200[[:space:]]' \
    && ! echo "$out" | grep -qE '^500[[:space:]]' \
    && ok "orphans-detector: сироты с CPU и worktree перечислены, живой прогон под opencode не тронут" \
    || fail "orphans-detector: код=$code, вывод: $out"
if grep -v '^[[:space:]]*#' scripts/lead/orphans-win.sh | grep -Eq 'taskkill|Stop-Process|kill_pid|kill -'; then
    fail "orphans-detector: детектор не должен убивать (нашёл kill/taskkill/Stop-Process)"
else
    ok "orphans-detector: только показывает, kill/taskkill в скрипте нет"
fi


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

export THEMINESPIRIT_GRADLEW="$work/bin/gametest-success"
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "OK gametest: 42 тест" \
    && ok "gametest-success: код=0, строка OK" \
    || fail "gametest-success: код=$code, вывод: $out"

export THEMINESPIRIT_GRADLEW="$work/bin/gametest-fail"
out=$(bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "FAIL gametest" \
    && ok "gametest-fail: код=1, FAIL" \
    || fail "gametest-fail: код=$code, вывод: $out"

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
    && grep -qx ":gametest:runGameTestServer" "$argsfile" \
    && ok "gametest-filter: имя пачки уходит в gradle системным свойством" \
    || fail "gametest-filter: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

out=$(ARGS_FILE="$argsfile" bash scripts/test.sh unit --tests 'com.theminespirit.testing.TestCostReporterTests' 2>&1)
grep -qx ":test-fixtures:test" "$argsfile" && grep -qx -- "--rerun" "$argsfile" \
    && ok "unit-rerun: gradle test идёт с --rerun" \
    || fail "unit-rerun: args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

cat >"$work/bin/module-record" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$@" >"${ARGS_FILE:?}"
suite() {
    local dir="$1" name="$2"
    mkdir -p "TestResults/$dir/xml"
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="2" failures="0" errors="0">\n<testcase name="a" classname="%s"/>\n<testcase name="b" classname="%s"/>\n</testsuite>\n' \
        "$name" "$name" "$name" >"TestResults/$dir/xml/TEST-$name.xml"
}
for a in "$@"; do
    case "$a" in
        :board:test) suite board com.example.Board ;;
        :guards:test) suite guards com.example.Guards ;;
        :test|test) suite unit com.example.Root ;;
    esac
done
echo "BUILD SUCCESSFUL"
exit 0
FAKE
chmod +x "$work/bin/module-record"
export THEMINESPIRIT_GRADLEW="$work/bin/module-record"
module_args="$work/module-args.txt"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --module board 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx ":board:test" "$module_args" \
    && ! grep -qx ":guards:test" "$module_args" && ! grep -q "staticAnalysis" "$module_args" \
    && echo "$out" | grep -q "OK unit: 2 тестов" \
    && ok "unit-module: только :board:test, без чужих модулей и анализаторов" \
    || fail "unit-module: код=$code, args=$(tr '\n' ' ' <"$module_args" 2>/dev/null), вывод: $out"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --module board --fast-analyzers 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx ":board:staticAnalysisFast" "$module_args" \
    && ! grep -qx ":guards:staticAnalysisFast" "$module_args" \
    && ok "unit-module-fast: анализаторы только у выбранного модуля" \
    || fail "unit-module-fast: код=$code, args=$(tr '\n' ' ' <"$module_args" 2>/dev/null)"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --module mech:farming 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx ":mech:farming:test" "$module_args" && echo "$out" | grep -q "тестов нет" \
    && ok "unit-module-empty: модуль без тестов — зелёный, без BUILD ERROR" \
    || fail "unit-module-empty: код=$code, args=$(tr '\n' ' ' <"$module_args" 2>/dev/null), вывод: $out"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --module nope 2>&1)
code=$?
[ "$code" = 2 ] && echo "$out" | grep -q "нет модуля" \
    && ok "unit-module-invalid: неизвестный модуль — код 2" \
    || fail "unit-module-invalid: код=$code, вывод: $out"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --tests "*BoardModuleGuardTests*" 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx ":guards:test" "$module_args" && ! grep -qx ":board:test" "$module_args" \
    && ok "unit-tests-resolve: фильтр нашёл :guards и не собрал :board" \
    || fail "unit-tests-resolve: код=$code, args=$(tr '\n' ' ' <"$module_args" 2>/dev/null), вывод: $out"

out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --tests "*NoSuchTestName*" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "не совпал" \
    && ok "unit-tests-nomatch: фильтр без совпадений — красный до сборки" \
    || fail "unit-tests-nomatch: код=$code, вывод: $out"

changed_probe="board/src/test/java/com/theminespirit/ChangedRunnerProbe.java"
mkdir -p "$(dirname "$changed_probe")"
printf 'package com.theminespirit;\n' >"$changed_probe"
out=$(ARGS_FILE="$module_args" bash scripts/test.sh unit --changed 2>&1)
code=$?
rm -f "$changed_probe"
[ "$code" = 0 ] && grep -qx ":board:test" "$module_args" && grep -qx ":guards:test" "$module_args" \
    && ok "unit-changed: правка в :board дала :board:test и :guards:test" \
    || fail "unit-changed: код=$code, args=$(tr '\n' ' ' <"$module_args" 2>/dev/null), вывод: $out"
export THEMINESPIRIT_GRADLEW="$work/bin/args-record"

out=$(ARGS_FILE="$argsfile" bash scripts/test.sh gametest combat_a combat_b 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.filter=combat_a,combat_b" "$argsfile" \
    && ok "gametest-filter: несколько фильтров через запятую" \
    || fail "gametest-filter: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

out=$(ARGS_FILE="$argsfile" THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.lock=$work/tmp/lock" "$argsfile" \
    && grep -q -- "-Dtheminespirit.gametest.token=" "$argsfile" \
    && ok "gametest-lock-props: замок и токен уходят в gradle системными свойствами" \
    || fail "gametest-lock-props: код=$code, args=$(tr '\n' ' ' <"$argsfile" 2>/dev/null)"

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
unset THEMINESPIRIT_GAMETEST_WAIT

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

        (
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

        [ "$server_alive" = yes ] && kill -KILL "$server_pid" 2>/dev/null
        [ "$client_alive" = yes ] && kill -KILL "$client_pid" 2>/dev/null
        rm -rf "$work/tmp/lock.d"

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
        kill -KILL "$sig_client" 2>/dev/null
        rm -rf "$work/tmp/lock.d"
        ;;
    *)
        ok "stop-server: пропущено (не Linux: поиск сервера по /proc/<pid>/cwd недоступен)"
        ;;
esac

cat >"$work/bin/shard-record" <<'FAKE'
#!/usr/bin/env bash
shard=0
for a in "$@"; do
    case "$a" in
        -Dtheminespirit.gametest.dir=*shard-*) shard="${a##*shard-}" ;;
    esac
done
printf '%s\n' "$@" >"${ARGS_DIR:?}/args-$shard"
echo "All 3 required tests passed"
exit 0
FAKE
chmod +x "$work/bin/shard-record"
argsdir="$work/shard-args"
rm -rf "$argsdir"; mkdir -p "$argsdir"
out=$(ARGS_DIR="$argsdir" THEMINESPIRIT_GRADLEW="$work/bin/shard-record" \
    THEMINESPIRIT_GAMETEST_SHARDS=2 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "OK gametest: 6 тест" \
    && [ -s "$argsdir/args-0" ] && [ -s "$argsdir/args-1" ] \
    && grep -qx "exclude=demo_audit" build/gametest-run/shard-0/theminespirit-gametest.properties \
    && grep -qx "shard=0/2" build/gametest-run/shard-0/theminespirit-gametest.properties \
    && grep -qx "shard=1/2" build/gametest-run/shard-1/theminespirit-gametest.properties \
    && grep -qx -- "-Dtheminespirit.gametest.dir=build/gametest-run/shard-0" "$argsdir/args-0" \
    && grep -qx -- "-Dtheminespirit.gametest.dir=build/gametest-run/shard-1" "$argsdir/args-1" \
    && ok "gametest-shards: 2 сервера, сумма итогов, быстрый набор исключает demo_audit, у шардов разные миры" \
    || fail "gametest-shards: код=$code, args0=$(tr '\n' ' ' <"$argsdir/args-0" 2>/dev/null), вывод: $out"
out=$(ARGS_DIR="$argsdir" THEMINESPIRIT_GRADLEW="$work/bin/shard-record" \
    THEMINESPIRIT_GAMETEST_SHARDS=1 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest --full 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx "exclude=demo_audit" build/gametest-run/theminespirit-gametest.properties \
    && ok "gametest-full: --full тоже не гоняет ручные демо-аудиты" \
    || fail "gametest-full: код=$code, args=$(tr '\n' ' ' <"$argsdir/args-0" 2>/dev/null), вывод: $out"
out=$(ARGS_DIR="$argsdir" THEMINESPIRIT_GRADLEW="$work/bin/shard-record" \
    THEMINESPIRIT_GAMETEST_SHARDS=1 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest demo_audit_familyhouse 2>&1)
code=$?
[ "$code" = 0 ] && grep -qx -- "-Dtheminespirit.gametest.filter=demo_audit_familyhouse" "$argsdir/args-0" \
    && grep -qx "exclude=" build/gametest-run/theminespirit-gametest.properties \
    && ok "gametest-manual-demo: явная пачка демо-аудита идёт без исключения" \
    || fail "gametest-manual-demo: код=$code, args=$(tr '\n' ' ' <"$argsdir/args-0" 2>/dev/null), вывод: $out"

cat >"$work/bin/shard-faulty" <<'FAKE'
#!/usr/bin/env bash
shard=0
for a in "$@"; do
    case "$a" in
        -Dtheminespirit.gametest.dir=*shard-*) shard="${a##*shard-}" ;;
    esac
done
case "${SHARD_CASE:-}" in
    silent) [ "$shard" = 2 ] && echo "All 7 required tests passed" ;;
    broken)
        if [ "$shard" = 1 ]; then
            echo "> Task :api:jar FAILED"
            echo "Unable to delete file 'api.jar'"
            echo "BUILD FAILED"
            exit 1
        fi
        echo "All 7 required tests passed"
        ;;
    shares)
        echo "GameTest filter []: удалено 0 тестов из 21, осталось 7 (исключения [], шард Shard[index=$shard, count=3])"
        echo "All 7 required tests passed :)"
        ;;
    mismatch)
        echo "GameTest filter []: удалено 0 тестов из 21, осталось 6 (исключения [], шард Shard[index=$shard, count=3])"
        echo "All 7 required tests passed :)"
        ;;
    single-bad) echo "All 7 required tests passed :)"; exit 1 ;;
esac
exit 0
FAKE
chmod +x "$work/bin/shard-faulty"
out=$(SHARD_CASE=silent THEMINESPIRIT_GRADLEW="$work/bin/shard-faulty" \
    THEMINESPIRIT_GAMETEST_SHARDS=3 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" != 0 ] && echo "$out" | grep -q "шард 0 не запускал сцены" \
    && ok "gametest-missing-shard: пустой лог шарда — красный, а не «OK 7 тестов»" \
    || fail "gametest-missing-shard: код=$code, вывод: $out"
out=$(SHARD_CASE=broken THEMINESPIRIT_GRADLEW="$work/bin/shard-faulty" \
    THEMINESPIRIT_GAMETEST_SHARDS=3 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" != 0 ] && echo "$out" | grep -q "шард 1 не дошёл до" \
    && echo "$out" | grep -q "Unable to delete" \
    && ok "gametest-failed-shard: упавшая сборка шарда называет причину" \
    || fail "gametest-failed-shard: код=$code, вывод: $out"
out=$(SHARD_CASE=shares THEMINESPIRIT_GRADLEW="$work/bin/shard-faulty" \
    THEMINESPIRIT_GAMETEST_SHARDS=3 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "OK gametest: 21 тестов (3 шардов: 7+7+7)" \
    && ok "gametest-shard-sum: итог — сумма сцен по шардам" \
    || fail "gametest-shard-sum: код=$code, вывод: $out"
out=$(SHARD_CASE=mismatch THEMINESPIRIT_GRADLEW="$work/bin/shard-faulty" \
    THEMINESPIRIT_GAMETEST_SHARDS=3 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest 2>&1)
code=$?
[ "$code" != 0 ] && echo "$out" | grep -q "шард 0 прошёл 7 тестов, а ему назначено 6 сцен" \
    && ok "gametest-shard-mismatch: шард прошёл не свои сцены — красный" \
    || fail "gametest-shard-mismatch: код=$code, вывод: $out"
out=$(SHARD_CASE=single-bad THEMINESPIRIT_GRADLEW="$work/bin/shard-faulty" \
    THEMINESPIRIT_GAMETEST_SHARDS=1 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" bash scripts/test.sh gametest --full 2>&1)
code=$?
[ "$code" != 0 ] && echo "$out" | grep -q "прогон ушёл с кодом 1" \
    && ok "gametest-code: ненулевой код gradle при «All N passed» — красный" \
    || fail "gametest-code: код=$code, вывод: $out"

cat >"$work/bin/slow-prepare" <<'FAKE'
#!/usr/bin/env bash
for a in "$@"; do
    case "$a" in
        :gametest:runGameTestServer|runGameTestServer)
            : >"${SHARD_MARK:?}"
            sleep "${SHARD_SLEEP:-30}"
            echo "All 1 required tests passed"
            exit 0
            ;;
    esac
done
sleep "${PREP_SLEEP:-0}"
echo "prepared"
exit 0
FAKE
chmod +x "$work/bin/slow-prepare"
mark="$work/shard-started"
rm -f "$mark"
out=$(PREP_SLEEP=30 SHARD_MARK="$mark" THEMINESPIRIT_GRADLEW="$work/bin/slow-prepare" \
    THEMINESPIRIT_GAMETEST_SHARDS=2 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" \
    THEMINESPIRIT_GAMETEST_PREPARE_WARN_SECONDS=600 THEMINESPIRIT_GAMETEST_PREPARE_STOP_SECONDS=1 \
    THEMINESPIRIT_GAMETEST_FULL_STOP_SECONDS=600 bash scripts/test.sh gametest --full 2>&1)
code=$?
[ "$code" = 124 ] && echo "$out" | grep -q "стоп: подготовка сборки" \
    && ok "gametest-prepare-limit: подготовка сборки останавливается своим пределом" \
    || fail "gametest-prepare-limit: код=$code, вывод: $out"
rm -rf "$work/tmp/lock.d"

rm -f "$mark"
out=$(PREP_SLEEP=3 SHARD_SLEEP=30 SHARD_MARK="$mark" THEMINESPIRIT_GRADLEW="$work/bin/slow-prepare" \
    THEMINESPIRIT_GAMETEST_SHARDS=2 THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" \
    THEMINESPIRIT_GAMETEST_PREPARE_WARN_SECONDS=600 THEMINESPIRIT_GAMETEST_PREPARE_STOP_SECONDS=600 \
    THEMINESPIRIT_GAMETEST_FULL_WARN_SECONDS=600 THEMINESPIRIT_GAMETEST_FULL_STOP_SECONDS=2 \
    bash scripts/test.sh gametest --full 2>&1)
code=$?
[ "$code" = 124 ] && [ -f "$mark" ] && echo "$out" | grep -q "стоп по пределу 2 с" \
    && ok "gametest-shard-budget: предел сценариев считается от старта шардов, а не от сборки" \
    || fail "gametest-shard-budget: код=$code, шард стартовал=$( [ -f "$mark" ] && echo да || echo нет), вывод: $out"
rm -rf "$work/tmp/lock.d"

rm -rf "$work/tmp/lock.d" "$argsdir"

exit $failed
