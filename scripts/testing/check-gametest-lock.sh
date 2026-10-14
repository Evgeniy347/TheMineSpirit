#!/usr/bin/env bash
# Регрессионная проверка замка scripts/gametest.sh без Gradle (секунды): фейковый gradlew пишет время старта и конца
# и спит, поэтому видно, пересекаются ли запуски. Каждая строка вывода начинается с OK или FAIL; код выхода 1 при FAIL.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/gametest-lock-check"
rm -rf "$work"
mkdir -p "$work/bin" "$work/tmp"
failed=0

# Каталог без flock: запасной путь включается, если PATH его не содержит (kill — встроенная команда bash).
mkdir -p "$work/nopath"
for tool in bash sh env cat rm mkdir sleep date dirname awk grep sed head tail; do
    p=$(command -v "$tool" 2>/dev/null) && ln -sf "$p" "$work/nopath/$tool"
done

# Фейковый gradlew: время старта/конца в файл и сон на заданное время.
cat >"$work/fake-gradlew" <<'FAKE'
#!/usr/bin/env bash
log="${FAKE_LOG:?}"
echo "start $(date +%s%N)" >>"$log"
sleep "${FAKE_SLEEP:-2}"
code="${FAKE_EXIT:-0}"
echo "end $(date +%s%N)" >>"$log"
exit "$code"
FAKE
chmod +x "$work/fake-gradlew"

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

export THEMINESPIRIT_GRADLEW="$work/fake-gradlew"
export THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock"

# 1. Два параллельных запуска не пересекаются по времени.
log="$work/parallel.log"; : >"$log"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$log" FAKE_SLEEP=3 bash scripts/gametest.sh >"$work/parallel1.out" 2>&1 &
p1=$!
sleep 1
THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$log" FAKE_SLEEP=3 bash scripts/gametest.sh >"$work/parallel2.out" 2>&1 &
p2=$!
wait "$p1"; wait "$p2"
starts=$(grep -c '^start ' "$log")
ends=$(grep -c '^end ' "$log")
first_end=$(grep '^end ' "$log" | head -n1 | awk '{print $2}')
last_start=$(grep '^start ' "$log" | tail -n1 | awk '{print $2}')
if [ "$starts" = 2 ] && [ "$ends" = 2 ] && [ "$last_start" -ge "$first_end" ]; then
    ok "parallel: запуски не пересекаются (второй старт после первого конца)"
else
    fail "parallel: пересечение запусков (стартов $starts, концов $ends)"
fi

# 2. Протухший замок снимается, запуск идёт.
rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo 999999 >"$work/tmp/lock.d/pid"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$work/stale.log" FAKE_SLEEP=1 bash scripts/gametest.sh >"$work/stale.out" 2>&1
code=$?
[ "$code" = 0 ] && ok "stale: код=0" || fail "stale: код=$code (ожидал 0)"
[ ! -d "$work/tmp/lock.d" ] && ok "stale: каталог замка удалён после выхода" || fail "stale: каталог замка остался"
grep -q 'протухший' "$work/stale.out" && ok "stale: сообщение о протухшем замке напечатано" || fail "stale: нет сообщения о протухшем замке"

# 3. Живой замок и малое ожидание — код 75.
rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo $$ >"$work/tmp/lock.d/pid"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 THEMINESPIRIT_GAMETEST_WAIT=3 FAKE_LOG="$work/busy.log" FAKE_SLEEP=1 bash scripts/gametest.sh >"$work/busy.out" 2>&1
code=$?
[ "$code" = 75 ] && ok "busy: код=75" || fail "busy: код=$code (ожидал 75)"
rm -rf "$work/tmp/lock.d"

# 4. Код выхода фейкового gradlew возвращается как есть.
THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$work/exitcode.log" FAKE_SLEEP=1 FAKE_EXIT=3 bash scripts/gametest.sh >"$work/exitcode.out" 2>&1
code=$?
[ "$code" = 3 ] && ok "exitcode: код=3" || fail "exitcode: код=$code (ожидал 3)"
[ ! -d "$work/tmp/lock.d" ] && ok "exitcode: каталог замка удалён после падения gradlew" || fail "exitcode: каталог замка остался"

# 5. Ctrl+C (INT) снимает замок.
THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$work/int.log" FAKE_SLEEP=30 bash scripts/gametest.sh >"$work/int.out" 2>&1 &
pi=$!
sleep 1
kill -INT "$pi" 2>/dev/null
wait "$pi" 2>/dev/null
sleep 1
[ ! -d "$work/tmp/lock.d" ] && ok "int: каталог замка снят по Ctrl+C" || fail "int: каталог замка остался после Ctrl+C"

exit $failed
