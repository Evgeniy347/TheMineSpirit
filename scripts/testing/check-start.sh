#!/usr/bin/env bash
# lib: игра пересоздаёт latest.log не мгновенно — под лагом это случается позже первых секунд старта.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/start-check"
script="$root/server/scripts/start.sh"
rm -rf "$work"
mkdir -p "$work/bin" "$work/server/logs" "$work/tmp" "$work/mod"
fails=0

cat >"$work/bin/tmux" <<'FAKE'
#!/usr/bin/env bash
case "$1" in
    has-session) exit 1 ;;
    new-session)
        (
            sleep "${FAKE_ROTATE_DELAY:-1}"
            : >"$FAKE_LOG"
            printf '%s\n' "${FAKE_DONE:-[Server thread/INFO]: Done (3.1s)! For help, type help}" >"$FAKE_LOG"
        ) &
        printf '%s\n' "$!" >"$FAKE_ROTATE_PID"
        exit 0
        ;;
    *) exit 0 ;;
esac
FAKE
chmod +x "$work/bin/tmux"

export SERVER_DIR="$work/server"
export TMP_DIR="$work/tmp"
export MOD_DIR="$work/mod"
export PUBLISH_SYNC_LOG="$work/publish-sync.log"
export PATH="$work/bin:$PATH"
export FAKE_LOG="$work/server/logs/latest.log"
export FAKE_ROTATE_PID="$work/rotate.pid"
export FAKE_ROTATE_DELAY=1
export FAKE_DONE="[Server thread/INFO]: Done (3.1s)! For help, type help"
export START_READY_TIMEOUT=12

old_log() {
    for _ in $(seq 1 240); do
        printf '%s\n' "# старый лог прошлой сессии"
    done >"$FAKE_LOG"
}

stop_rotate() {
    if [ -f "$FAKE_ROTATE_PID" ]; then
        kill "$(cat "$FAKE_ROTATE_PID")" 2>/dev/null || true
        rm -f "$FAKE_ROTATE_PID"
    fi
}

run_start() {
    local name="$1"
    bash "$script" >"$work/$name.txt" 2>&1
    echo $?
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

rm -f "$FAKE_LOG"
old_log
FAKE_ROTATE_DELAY=1
code="$(run_start rotate_early)"
expect rotate_early_exit_zero "$code" 0
stop_rotate

rm -f "$FAKE_LOG"
old_log
FAKE_ROTATE_DELAY=5
code="$(run_start rotate_late)"
expect rotate_late_exit_zero "$code" 0
stop_rotate

rm -f "$FAKE_LOG"
old_log
FAKE_ROTATE_DELAY=30
START_READY_TIMEOUT=3
code="$(run_start never_ready)"
expect never_ready_exit_one "$code" 1
expect_file never_ready_reports_reason "$work/never_ready.txt" "не сообщил о готовности"
stop_rotate

if [ "$fails" -ne 0 ]; then
    echo "--- последний вывод start.sh ---"
    tail -20 "$work/never_ready.txt" 2>/dev/null
fi
rm -rf "$work"
if [ "$fails" -eq 0 ]; then
    echo "OK start: все опыты зелёные"
    exit 0
fi
echo "FAIL start: провалено опытов $fails"
exit 1
