#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/gametest-lock-check"
rm -rf "$work"
mkdir -p "$work/bin" "$work/tmp"
failed=0

export THEMINESPIRIT_GAMETEST_SHARDS=1

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

rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo 999999 >"$work/tmp/lock.d/pid"
echo old-token >"$work/tmp/lock.d/token"
echo $(( $(date +%s) - 3600 )) >"$work/tmp/lock.d/heartbeat"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 THEMINESPIRIT_GAMETEST_STALE_SECONDS=60 \
    FAKE_LOG="$work/stale.log" FAKE_SLEEP=1 bash scripts/gametest.sh >"$work/stale.out" 2>&1
code=$?
[ "$code" = 1 ] && ok "stale: код=1 (FAIL)" || fail "stale: код=$code (ожидал 1)"
[ ! -d "$work/tmp/lock.d" ] && ok "stale: каталог замка удалён после выхода" || fail "stale: каталог замка остался"
grep -q 'протухший' "$work/stale.out" && ok "stale: сообщение о протухшем замке напечатано" || fail "stale: нет сообщения о протухшем замке"

rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo $$ >"$work/tmp/lock.d/pid"
echo held-token >"$work/tmp/lock.d/token"
echo "$(date +%s)" >"$work/tmp/lock.d/heartbeat"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 THEMINESPIRIT_GAMETEST_STALE_SECONDS=60 THEMINESPIRIT_GAMETEST_WAIT=3 \
    FAKE_LOG="$work/busy.log" FAKE_SLEEP=1 bash scripts/gametest.sh >"$work/busy.out" 2>&1
code=$?
[ "$code" = 75 ] && ok "busy: код=75" || fail "busy: код=$code (ожидал 75)"
[ "$(cat "$work/tmp/lock.d/token" 2>/dev/null)" = held-token ] \
    && ok "busy: чужой токен не перезаписан" || fail "busy: чужой маркер изменён или снят"
rm -rf "$work/tmp/lock.d"

THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$work/exitcode.log" FAKE_SLEEP=1 FAKE_EXIT=3 bash scripts/gametest.sh >"$work/exitcode.out" 2>&1
code=$?
[ "$code" = 1 ] && ok "exitcode: код=1 (FAIL)" || fail "exitcode: код=$code (ожидал 1)"
[ ! -d "$work/tmp/lock.d" ] && ok "exitcode: каталог замка удалён после падения gradlew" || fail "exitcode: каталог замка остался"

THEMINESPIRIT_GAMETEST_NOFLOCK=1 FAKE_LOG="$work/int.log" FAKE_SLEEP=30 bash scripts/gametest.sh >"$work/int.out" 2>&1 &
pi=$!
sleep 1
kill -INT "$pi" 2>/dev/null
wait "$pi" 2>/dev/null
sleep 1
[ ! -d "$work/tmp/lock.d" ] && ok "int: каталог замка снят по Ctrl+C" || fail "int: каталог замка остался после Ctrl+C"

rm -rf "$work/tmp/lock.d" "$work"/tmp/lock.d.stale.*
mkdir -p "$work/tmp/lock.d"
echo 999999 >"$work/tmp/lock.d/pid"
echo old-token >"$work/tmp/lock.d/token"
echo $(( $(date +%s) - 3600 )) >"$work/tmp/lock.d/heartbeat"
log="$work/stale-race.log"; : >"$log"
THEMINESPIRIT_GAMETEST_NOFLOCK=1 THEMINESPIRIT_GAMETEST_STALE_SECONDS=60 FAKE_LOG="$log" FAKE_SLEEP=2 bash scripts/gametest.sh >"$work/stale-race1.out" 2>&1 &
r1=$!
THEMINESPIRIT_GAMETEST_NOFLOCK=1 THEMINESPIRIT_GAMETEST_STALE_SECONDS=60 FAKE_LOG="$log" FAKE_SLEEP=2 bash scripts/gametest.sh >"$work/stale-race2.out" 2>&1 &
r2=$!
wait "$r1"; c1=$?
wait "$r2"; c2=$?
msgs=$(cat "$work/stale-race1.out" "$work/stale-race2.out" | grep -c 'снимаю протухший')
starts=$(grep -c '^start ' "$log")
ends=$(grep -c '^end ' "$log")
first_end=$(grep '^end ' "$log" | head -n1 | awk '{print $2}')
last_start=$(grep '^start ' "$log" | tail -n1 | awk '{print $2}')
if [ "$c1" = 1 ] && [ "$c2" = 1 ]; then
    ok "stale-race: оба ожидающих отработали (коды 1 и 1)"
else
    fail "stale-race: коды $c1 и $c2 (ожидал 1 и 1)"
fi
[ "$msgs" = 1 ] && ok "stale-race: протухший замок снял ровно один ожидающий" || fail "stale-race: сообщений о снятии $msgs (ожидал 1)"
if [ "$starts" = 2 ] && [ "$ends" = 2 ] && [ "$last_start" -ge "$first_end" ]; then
    ok "stale-race: запуски отработали по очереди, без пересечения"
else
    fail "stale-race: стартов $starts, концов $ends — запуски пересеклись или не все отработали"
fi
leftovers=$(ls -d "$work"/tmp/lock.d.stale.* 2>/dev/null | wc -l)
[ "$leftovers" = 0 ] && ok "stale-race: переименованных остатков .stale.* не осталось" || fail "stale-race: остались $leftovers каталогов .stale.*"

cat >"$work/fake-check-lock" <<'FAKE'
#!/usr/bin/env bash
log="${FAKE_LOG:?}"
echo "start $(date +%s%N)" >>"$log"
echo "default_marker=$([ -d "${CHECK_DEFAULT_DIR:?}" ] && echo yes || echo no)" >>"$log"
echo "tmp_marker=$([ -d "${CHECK_TMP_DIR:?}" ] && echo yes || echo no)" >>"$log"
sleep "${FAKE_SLEEP:-2}"
code="${FAKE_EXIT:-0}"
echo "end $(date +%s%N)" >>"$log"
exit "$code"
FAKE
chmod +x "$work/fake-check-lock"

unset THEMINESPIRIT_GAMETEST_LOCK
case "$OSTYPE" in
    msys*|cygwin*|win*)
        export CHECK_DEFAULT_DIR="$work/programdata/theminespirit/theminespirit-gametest.lock.d"
        export CHECK_TMP_DIR="$(cygpath -w /tmp)/theminespirit-gametest.lock.d"
        export ALLUSERSPROFILE="$work/programdata"
        ;;
    *)
        export CHECK_DEFAULT_DIR="/tmp/theminespirit-gametest.lock.d"
        export CHECK_TMP_DIR=""
        ;;
esac
THEMINESPIRIT_GRADLEW="$work/fake-check-lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    FAKE_LOG="$work/default.log" FAKE_SLEEP=1 TMP= TEMP= \
    bash scripts/gametest.sh >"$work/default.out" 2>&1
code=$?
[ "$code" = 1 ] && ok "default-path: код=1 (FAIL)" || fail "default-path: код=$code (ожидал 1)"
default_marker=$(grep -o 'default_marker=[a-z]*' "$work/default.log" | head -1 | cut -d= -f2)
[ "$default_marker" = yes ] \
    && ok "default-path: маркер создан по дефолту ($CHECK_DEFAULT_DIR)" \
    || fail "default-path: маркер по дефолту не создан (default_marker=$default_marker)"
if [ -n "$CHECK_TMP_DIR" ]; then
    tmp_marker=$(grep -o 'tmp_marker=[a-z]*' "$work/default.log" | head -1 | cut -d= -f2)
    [ "$tmp_marker" = no ] \
        && ok "default-path: маркер не в %TEMP% (TMP обнулён, замок всё равно по общему пути)" \
        || fail "default-path: маркер создался в %TEMP% (tmp_marker=$tmp_marker)"
fi
unset CHECK_DEFAULT_DIR CHECK_TMP_DIR

rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo 999999 >"$work/tmp/lock.d/pid"
echo held-token >"$work/tmp/lock.d/token"
echo "$(date +%s)" >"$work/tmp/lock.d/heartbeat"
THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_STALE_SECONDS=60 THEMINESPIRIT_GAMETEST_WAIT=3 \
    THEMINESPIRIT_GRADLEW="$work/fake-gradlew" FAKE_LOG="$work/deadpid.log" FAKE_SLEEP=1 \
    bash scripts/test.sh gametest >"$work/deadpid.out" 2>&1
code=$?
[ "$code" = 75 ] && ok "deadpid-fresh-beat: код=75, замок не снят" || fail "deadpid-fresh-beat: код=$code (ожидал 75)"
[ "$(cat "$work/tmp/lock.d/token" 2>/dev/null)" = held-token ] \
    && ok "deadpid-fresh-beat: чужой токен не перезаписан" || fail "deadpid-fresh-beat: чужой маркер изменён или снят"
grep -q 'протухший' "$work/deadpid.out" \
    && fail "deadpid-fresh-beat: замок сняли как протухший" || ok "deadpid-fresh-beat: протухшим не признан"
rm -rf "$work/tmp/lock.d"

rm -rf "$work/tmp/lock.d"
mkdir -p "$work/tmp/lock.d"
echo 999999 >"$work/tmp/lock.d/pid"
THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GAMETEST_WAIT=3 \
    THEMINESPIRIT_GRADLEW="$work/fake-gradlew" FAKE_LOG="$work/legacy.log" FAKE_SLEEP=1 \
    bash scripts/test.sh gametest >"$work/legacy.out" 2>&1
code=$?
[ "$code" = 75 ] && ok "legacy-fresh: код=75, старый маркер не снят" || fail "legacy-fresh: код=$code (ожидал 75)"
grep -q 'протухший' "$work/legacy.out" \
    && fail "legacy-fresh: старый маркер сняли как протухший" || ok "legacy-fresh: протухшим не признан"
rm -rf "$work/tmp/lock.d"

cat >"$work/fake-lock-check" <<'FAKE'
#!/usr/bin/env bash
log="${FAKE_LOG:?}"
lock=""; token=""
for a in "$@"; do
    case "$a" in
        -Dtheminespirit.gametest.lock=*) lock="${a#*=}" ;;
        -Dtheminespirit.gametest.token=*) token="${a#*=}" ;;
    esac
done
marker="$(cat "$lock.d/token" 2>/dev/null || echo "")"
if [ -n "$token" ] && [ "$marker" = "$token" ]; then
    echo "token_ok" >>"$log"
else
    echo "token_bad marker=[$marker] token=[$token]" >>"$log"
fi
echo "start $(date +%s%N)" >>"$log"
sleep "${FAKE_SLEEP:-2}"
echo "end $(date +%s%N)" >>"$log"
echo "All 42 required tests passed"
exit 0
FAKE
chmod +x "$work/fake-lock-check"
rm -rf "$work/tmp/lock.d"
log="$work/token-race.log"; : >"$log"
THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GRADLEW="$work/fake-lock-check" FAKE_LOG="$log" FAKE_SLEEP=2 \
    bash scripts/test.sh gametest >"$work/token-race1.out" 2>&1 &
t1=$!
sleep 1
THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GRADLEW="$work/fake-lock-check" FAKE_LOG="$log" FAKE_SLEEP=2 \
    bash scripts/test.sh gametest >"$work/token-race2.out" 2>&1 &
t2=$!
wait "$t1"; c1=$?
wait "$t2"; c2=$?
token_ok=$(grep -c '^token_ok$' "$log")
token_bad=$(grep -c '^token_bad' "$log")
starts=$(grep -c '^start ' "$log")
ends=$(grep -c '^end ' "$log")
first_end=$(grep '^end ' "$log" | head -n1 | awk '{print $2}')
last_start=$(grep '^start ' "$log" | tail -n1 | awk '{print $2}')
[ "$c1" = 0 ] && [ "$c2" = 0 ] && ok "token-race: оба запуска зелёные (коды 0 и 0)" || fail "token-race: коды $c1 и $c2 (ожидал 0 и 0)"
[ "$token_ok" = 2 ] && [ "$token_bad" = 0 ] \
    && ok "token-race: оба gradle увидели свой токен в маркере" \
    || fail "token-race: token_ok=$token_ok token_bad=$token_bad (чужой/устаревший маркер)"
if [ "$starts" = 2 ] && [ "$ends" = 2 ] && [ "$last_start" -ge "$first_end" ]; then
    ok "token-race: запуски не пересекаются"
else
    fail "token-race: стартов $starts, концов $ends — пересечение или не все отработали"
fi

cat >"$work/fake-foreign-token" <<'FAKE'
#!/usr/bin/env bash
lock=""
for a in "$@"; do
    case "$a" in
        -Dtheminespirit.gametest.lock=*) lock="${a#*=}" ;;
    esac
done
echo "foreign-token" >"$lock.d/token"
echo "All 42 required tests passed"
exit 0
FAKE
chmod +x "$work/fake-foreign-token"
rm -rf "$work/tmp/lock.d"
THEMINESPIRIT_GAMETEST_LOCK="$work/tmp/lock" THEMINESPIRIT_GAMETEST_NOFLOCK=1 \
    THEMINESPIRIT_GRADLEW="$work/fake-foreign-token" \
    bash scripts/test.sh gametest >"$work/foreign-release.out" 2>&1
code=$?
[ "$code" = 0 ] && ok "foreign-release: прогон завершился (код 0)" || fail "foreign-release: код=$code (ожидал 0)"
[ -d "$work/tmp/lock.d" ] && [ "$(cat "$work/tmp/lock.d/token" 2>/dev/null)" = foreign-token ] \
    && ok "foreign-release: чужой замок оставлен на месте" \
    || fail "foreign-release: чужой замок снят или испорчен"
rm -rf "$work/tmp/lock.d"

exit $failed
