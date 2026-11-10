#!/usr/bin/env bash
# lib: Проверка server/scripts/world-snapshot.sh без живого сервера: tmux, flock и tar — подделки,
# журнал сервера — файл. Снимок обязан дождаться подтверждения сохранения даже на большом журнале
# (старый grep -q под pipefail получал SIGPIPE от tail и ложно отменял снимок).
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/world-snapshot-check.$$"
trap 'rm -rf "$work"' EXIT
rm -rf "$work"
mkdir -p "$work/bin"
failed=0

ok() { echo "OK $1"; }
fail() {
    echo "FAIL $1"
    failed=1
}

cat >"$work/bin/tmux" <<'FAKE'
#!/usr/bin/env bash
case "${1:-}" in
    has-session) exit "${FAKE_TMUX_RUNNING:-0}" ;;
    send-keys)
        command="${4:-}"
        echo "$command" >>"$FAKE_TMUX_COMMANDS"
        if [ "$command" = "save-all flush" ]; then
            {
                printf 'Saved the game\n'
                head -c 5000000 /dev/zero | tr '\0' 'x'
                printf '\n'
            } >>"$FAKE_SERVER_DIR/logs/latest.log"
        fi
        exit 0
        ;;
esac
exit 0
FAKE
cat >"$work/bin/flock" <<'FAKE'
#!/usr/bin/env bash
exit 0
FAKE
cat >"$work/bin/tar" <<'FAKE'
#!/usr/bin/env bash
mode=""
out=""
prev=""
for arg in "$@"; do
    [ "$prev" = "-cf" ] && out="$arg"
    case "$arg" in
        -cf) mode=create ;;
        -tf) mode=list ;;
    esac
    prev="$arg"
done
case "$mode" in
    create) echo "поддельный архив" >"$out" ;;
    list) echo "world/level.dat" ;;
esac
exit 0
FAKE
chmod 755 "$work/bin"/*

prepare() {
    local dir="$1"
    mkdir -p "$dir/scripts" "$dir/world" "$dir/logs"
    cp server/scripts/common.sh server/scripts/world-snapshot.sh "$dir/scripts/"
    echo "level" >"$dir/world/level.dat"
    : >"$dir/logs/latest.log"
}

run_snapshot() {
    local server="$1" backups="$2" label="$3"
    FAKE_SERVER_DIR="$server" \
        FAKE_TMUX_COMMANDS="$backups/commands" \
        SERVER_DIR="$server" \
        WORLD_BACKUPS="$backups" \
        TMUX_SESSION=check \
        PATH="$work/bin:$PATH" \
        bash "$server/scripts/world-snapshot.sh" "$label" 2>&1
}

# lib: сервер запущен: ждём подтверждение сохранения в журнале и снимаем архив.
server="$work/run/server"
backups="$work/run/backups"
mkdir -p "$backups"
prepare "$server"
out=$(run_snapshot "$server" "$backups" check)
code=$?
archive=$(ls "$backups"/world-*-check.tar.zst 2>/dev/null | head -n 1)
[ "$code" = 0 ] && [ -n "$archive" ] && echo "$out" | grep -q "снимок готов" \
    && ok "запущенный сервер: снимок создан после подтверждения" \
    || fail "запущенный сервер: код=$code, архив='$archive', вывод: $out"
grep -qx "save-off" "$backups/commands" 2>/dev/null \
    && grep -qx "save-all flush" "$backups/commands" 2>/dev/null \
    && grep -qx "save-on" "$backups/commands" 2>/dev/null \
    && ok "автосохранение выключено, сброшено и включено обратно" \
    || fail "команды сохранения не отправлены или не полны: $(cat "$backups/commands" 2>/dev/null)"

# lib: сервер остановлен: архив снимается с диска, консоли не касаемся.
server="$work/stopped/server"
backups="$work/stopped/backups"
mkdir -p "$backups"
prepare "$server"
out=$(FAKE_TMUX_RUNNING=1 run_snapshot "$server" "$backups" stopped)
code=$?
archive=$(ls "$backups"/world-*-stopped.tar.zst 2>/dev/null | head -n 1)
[ "$code" = 0 ] && [ -n "$archive" ] && [ ! -f "$backups/commands" ] \
    && ok "остановленный сервер: снимок с диска без команд консоли" \
    || fail "остановленный сервер: код=$code, архив='$archive', вывод: $out"

exit "$failed"
