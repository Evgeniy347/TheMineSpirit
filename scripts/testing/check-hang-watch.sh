#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/hang-watch-check"
script="$root/server/scripts/hang-watch.sh"
rm -rf "$work"
mkdir -p "$work/logs" "$work/tmp" "$work/server/scripts"
calls="$work/calls.log"
fails=0

write_fake() {
    local path="$1" body="$2"
    printf '%b\n' "$body" >"$path"
    chmod +x "$path"
}

write_fake "$work/fake-curl.sh" '#!/usr/bin/env bash
case "${FAKE_HEALTH:-}" in
    down) exit 7 ;;
    "") exit 0 ;;
esac
printf "%s\n" "$FAKE_HEALTH"'
write_fake "$work/fake-jcmd.sh" '#!/usr/bin/env bash
case "${2:-}" in
    VM.version)
        printf "probe\n" >>"$FAKE_CALLS"
        [ "${FAKE_JCMD_CODE:-0}" = 0 ] || exit "${FAKE_JCMD_CODE}"
        ;;
    *)
        printf "jcmd\n" >>"$FAKE_CALLS"
        printf "Thread dump for %s\n" "$1"
        ;;
esac'
write_fake "$work/fake-stop.sh" '#!/usr/bin/env bash
printf "stop\n" >>"$FAKE_CALLS"
exit "${FAKE_STOP_CODE:-0}"'
write_fake "$work/fake-start.sh" '#!/usr/bin/env bash
printf "start\n" >>"$FAKE_CALLS"
exit "${FAKE_START_CODE:-0}"'

export SERVER_DIR="$work/server"
export TMP_DIR="$work/tmp"
export HANG_WATCH_LOG_DIR="$work/logs"
export HANG_WATCH_TMP="$work/tmp"
export HANG_WATCH_STOP_MARKER="$work/tmp/server-stop.lock"
export HANG_WATCH_THRESHOLD=120
export HANG_WATCH_REST="http://fake"
export HANG_WATCH_CURL="$work/fake-curl.sh"
export HANG_WATCH_JCMD="$work/fake-jcmd.sh"
export HANG_WATCH_STOP_SCRIPT="$work/fake-stop.sh"
export HANG_WATCH_START_SCRIPT="$work/fake-start.sh"
export HANG_WATCH_HEARTBEAT="$work/logs/tick.heartbeat"
export HANG_WATCH_MAIN_LOG="$work/logs/latest.log"
export HANG_WATCH_HANG_LOG="$work/logs/hang.log"
export HANG_WATCH_DEBUG_LEASE="$work/debug.lease"
export HANG_WATCH_ASSUME_RUNNING=1
export HANG_WATCH_NO_LOCK=1
export FAKE_CALLS="$calls"
export FAKE_HEALTH=""
export FAKE_STOP_CODE=0
export FAKE_START_CODE=0
export FAKE_JCMD_CODE=0
export FAKE_JCMD_CODE=0
export HANG_WATCH_MODE=act
export HANG_WATCH_DRY_RUN=0
NOW="$(date +%s)"
export HANG_WATCH_NOW="$NOW"
export HANG_WATCH_PID="$$"

reset_case() {
    rm -rf "$work/logs" "$work/tmp" "$work/debug.lease" "$calls"
    mkdir -p "$work/logs" "$work/tmp"
    touch "$work/logs/latest.log"
    export FAKE_HEALTH=""
    export FAKE_STOP_CODE=0
    export FAKE_START_CODE=0
    export FAKE_JCMD_CODE=0
    export HANG_WATCH_MODE=act
    export HANG_WATCH_DRY_RUN=0
    export HANG_WATCH_PID="$$"
    export HANG_WATCH_JCMD="$work/fake-jcmd.sh"
}

write_heartbeat() {
    printf 'pid=%s\nstarted_ms=%s\ntick=10\nnow_ms=%s\n' "$1" "$2" "$3" >"$work/logs/tick.heartbeat"
}

run_watch() {
    bash "$script" >"$work/out.txt" 2>&1
    echo $?
}

calls_of() {
    local count
    [ -f "$calls" ] || { printf '0'; return; }
    count="$(grep -c "^$1$" "$calls" 2>/dev/null || true)"
    printf '%s' "${count:-0}"
}

expect() {
    local name="$1" actual="$2" expected="$3"
    if [ "$actual" = "$expected" ]; then
        echo "OK $name: $actual"
    else
        echo "FAIL $name: $actual (ожидал $expected)"
        fails=$((fails + 1))
    fi
}

expect_file() {
    local name="$1" file="$2" pattern="$3"
    if [ -f "$file" ] && grep -q "$pattern" "$file"; then
        echo "OK $name: «$pattern» найден"
    else
        echo "FAIL $name: в $file нет «$pattern»"
        fails=$((fails + 1))
    fi
}

expect_no_file() {
    local name="$1" file="$2"
    if [ -e "$file" ]; then
        echo "FAIL $name: файл $file не должен появиться"
        fails=$((fails + 1))
    else
        echo "OK $name: файла нет"
    fi
}

reset_case
export FAKE_HEALTH='{"verdict":"ok","age_ms":120,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect live_rest_ok_exit_zero "$code" 0
expect live_rest_ok_no_stop "$(calls_of stop)" 0
expect_no_file live_rest_ok_no_dump "$work/logs/hang.log"

reset_case
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect hung_rest_exit_zero "$code" 0
expect hung_rest_stop_once "$(calls_of stop)" 1
expect hung_rest_start_once "$(calls_of start)" 1
expect hung_rest_jcmd_once "$(calls_of jcmd)" 1
expect_file hung_rest_hang_log "$work/logs/hang.log" "зависание"
dump_count="$(find "$work/logs" -maxdepth 1 -name 'hang-*.txt' | wc -l | tr -d ' ')"
expect hung_rest_dump_created "$dump_count" 1

reset_case
export FAKE_HEALTH='{"verdict":"debug","age_ms":-1,"tick":0,"last_tick_ms":0,"threshold_ms":120000,"debug_paused":true}'
code="$(run_watch)"
expect debug_rest_exit_zero "$code" 0
expect debug_rest_no_stop "$(calls_of stop)" 0

reset_case
printf 'agent|%s|%s\n' "$((NOW + 600))" "$NOW" >"$work/debug.lease"
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect debug_lease_exit_zero "$code" 0
expect debug_lease_no_stop "$(calls_of stop)" 0

reset_case
printf 'agent|%s|%s\n' "$((NOW - 1))" "$NOW" >"$work/debug.lease"
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect expired_lease_exit_zero "$code" 0
expect expired_lease_stops "$(calls_of stop)" 1

reset_case
touch "$work/tmp/server-stop.lock"
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect stopping_marker_exit_zero "$code" 0
expect stopping_marker_no_stop "$(calls_of stop)" 0

# lib: обновление JDK на месте убивает поток консоли: тик и REST живы, а jcmd не прикрепляется.
# lib: без этой проверки сторож видел «ok» и молчал, пока save-* и stop не подтверждались.
reset_case
export FAKE_JCMD_CODE=1
export FAKE_HEALTH='{"verdict":"ok","age_ms":120,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect console_dead_rest_ok_exit_zero "$code" 0
expect console_dead_probes_jcmd "$(calls_of probe)" 1
expect console_dead_stops "$(calls_of stop)" 1
expect console_dead_starts "$(calls_of start)" 1
expect_file console_dead_names_cause "$work/logs/hang.log" "консоль мертва"
console_dumps="$(find "$work/logs" -maxdepth 1 -name 'console-*.txt' | wc -l | tr -d ' ')"
expect console_dead_dump_created "$console_dumps" 1

reset_case
export FAKE_JCMD_CODE=1
export HANG_WATCH_MODE=check
code="$(run_watch)"
expect console_check_exit_zero "$code" 0
expect console_check_reports "$(cat "$work/out.txt")" "console"
expect console_check_no_stop "$(calls_of stop)" 0

reset_case
export FAKE_JCMD_CODE=1
export HANG_WATCH_DRY_RUN=1
code="$(run_watch)"
expect console_dry_run_exit_zero "$code" 0
expect console_dry_run_no_stop "$(calls_of stop)" 0
expect_file console_dry_run_marker "$work/logs/hang.log" "dry-run"

reset_case
export HANG_WATCH_JCMD="$work/нет-jcmd"
export FAKE_HEALTH='{"verdict":"ok","age_ms":120,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect no_jcmd_exit_zero "$code" 0
expect no_jcmd_no_stop "$(calls_of stop)" 0

reset_case
export FAKE_HEALTH=down
write_heartbeat "$$" "$((NOW * 1000))" "$((NOW * 1000))"
code="$(run_watch)"
expect rest_down_fresh_heartbeat_exit_zero "$code" 0
expect rest_down_fresh_heartbeat_no_stop "$(calls_of stop)" 0

reset_case
export FAKE_HEALTH=down
write_heartbeat "$$" "$((NOW * 1000))" "$(((NOW - 121) * 1000))"
code="$(run_watch)"
expect rest_down_stale_heartbeat_exit_zero "$code" 0
expect rest_down_stale_heartbeat_stops "$(calls_of stop)" 1

reset_case
export FAKE_HEALTH=down
write_heartbeat 999999 "$((NOW * 1000))" "$(((NOW - 600) * 1000))"
code="$(run_watch)"
expect rest_down_foreign_pid_and_fresh_log_exit_zero "$code" 0
expect rest_down_foreign_pid_and_fresh_log_no_stop "$(calls_of stop)" 0

reset_case
export FAKE_HEALTH='{"verdict":"unknown","age_ms":-1,"tick":0,"last_tick_ms":0,"threshold_ms":120000,"debug_paused":false}'
write_heartbeat "$$" "$((NOW * 1000))" "$(((NOW - 121) * 1000))"
code="$(run_watch)"
expect unknown_rest_stale_heartbeat_exit_zero "$code" 0
expect unknown_rest_stale_heartbeat_stops "$(calls_of stop)" 1

reset_case
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
export HANG_WATCH_MODE=check
code="$(run_watch)"
expect check_mode_exit_zero "$code" 0
expect check_mode_reports_hung "$(cat "$work/out.txt")" "hung"
expect check_mode_no_stop "$(calls_of stop)" 0
expect_no_file check_mode_no_dump "$work/logs/hang.log"

reset_case
sleep 300 &
victim=$!
export HANG_WATCH_PID="$victim"
export FAKE_STOP_CODE=1
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
if kill -0 "$victim" 2>/dev/null; then
    echo "FAIL stop_failure_kills_process: PID $victim жив после отказа stop.sh"
    fails=$((fails + 1))
    kill -9 "$victim" 2>/dev/null || true
else
    echo "OK stop_failure_kills_process: PID погашен после отказа stop.sh"
fi
expect stop_failure_restarts "$(calls_of start)" 1
expect stop_failure_exit_zero "$code" 0

reset_case
export FAKE_START_CODE=1
export FAKE_HEALTH='{"verdict":"hung","age_ms":900000,"tick":10,"last_tick_ms":1,"threshold_ms":120000,"debug_paused":false}'
code="$(run_watch)"
expect start_failure_exit_one "$code" 1
expect_file start_failure_reports_human "$work/logs/hang.log" "нужен человек"

echo
if [ "$fails" -eq 0 ]; then
    echo "OK hang-watch: все сценарии прошли"
    exit 0
fi
echo "FAIL hang-watch: провалено сценариев $fails"
exit 1
