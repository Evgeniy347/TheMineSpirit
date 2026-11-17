#!/usr/bin/env bash
# lib: Проверка server/scripts/world-snapshot.sh без живого сервера: tmux, flock и tar — подделки,
# lib: журнал сервера — файл. Снимок обязан дождаться подтверждения каждой команды сохранения даже на большом
# lib: журнале (старый grep -q под pipefail получал SIGPIPE от tail и ложно отменял снимок), вернуть save-on,
# lib: назвать мёртвую консоль и занятый замок и не выбрасывать готовый архив.
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
        printf '%s\n' "$command" >>"$FAKE_TMUX_COMMANDS"
        echo "тик сервера" >>"$FAKE_SERVER_DIR/logs/latest.log"
        case "$command" in
            save-off)
                [ "${FAKE_SAVE_OFF:-ok}" = ok ] && echo "Automatic saving is now disabled" >>"$FAKE_SERVER_DIR/logs/latest.log"
                ;;
            save-all*)
                if [ "${FAKE_SAVE_ALL:-ok}" = ok ]; then
                    {
                        printf 'Saved the game\n'
                        head -c 5000000 /dev/zero | tr '\0' 'x'
                        printf '\n'
                    } >>"$FAKE_SERVER_DIR/logs/latest.log"
                fi
                ;;
            save-on)
                [ "${FAKE_SAVE_ON:-ok}" = ok ] && echo "Automatic saving is now enabled" >>"$FAKE_SERVER_DIR/logs/latest.log"
                ;;
        esac
        exit 0
        ;;
esac
exit 0
FAKE
cat >"$work/bin/flock" <<'FAKE'
#!/usr/bin/env bash
if [ -n "${FAKE_FLOCK_BUSY:-}" ]; then
    exit 1
fi
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
cat >"$work/bin/df" <<'FAKE'
#!/usr/bin/env bash
if [ -z "${FAKE_FREE_FILE:-}" ]; then
    exec /usr/bin/df "$@"
fi
free=$(cat "$FAKE_FREE_FILE" 2>/dev/null || echo 0)
echo $((free + 1000)) >"$FAKE_FREE_FILE"
echo "Filesystem 1M-blocks Used Available Capacity Mounted"
echo "/dev/fake 1000000 900000 $free 90% /"
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
        MIN_FREE_MB="${MIN_FREE_MB:-1}" \
        SAVE_ACK_TIMEOUT=2 \
        SAVE_TIMEOUT=2 \
        SNAPSHOT_LOCK_WAIT="${SNAPSHOT_LOCK_WAIT:-0}" \
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

# lib: консоль умерла: save-off не подтверждён, причина названа, save-on всё равно отправлен, архива нет.
server="$work/dead/server"
backups="$work/dead/backups"
mkdir -p "$backups"
prepare "$server"
out=$(FAKE_SAVE_OFF=fail FAKE_SAVE_ALL=fail FAKE_SAVE_ON=fail run_snapshot "$server" "$backups" dead)
code=$?
[ "$code" -ne 0 ] && echo "$out" | grep -q "не подтвердила save-off" \
    && echo "$out" | grep -q "консоль не читает stdin" \
    && ok "мёртвая консоль: снимок отменён с причиной, код $code" \
    || fail "мёртвая консоль: ждали отказ с причиной «не подтвердила save-off», код $code: $out"
grep -qx "save-on" "$backups/commands" 2>/dev/null \
    && ok "мёртвая консоль: save-on всё равно отправлен" \
    || fail "мёртвая консоль: save-on не отправлен, автосохранение осталось бы выключенным"
ls "$backups"/world-*.tar.zst >/dev/null 2>&1 \
    && fail "мёртвая консоль: архив не должен сниматься без подтверждённого save-all" \
    || ok "мёртвая консоль: архив не создан"

# lib: save-all молчит, но консоль жива: автосохранение возвращается подтверждённым save-on.
server="$work/noflush/server"
backups="$work/noflush/backups"
mkdir -p "$backups"
prepare "$server"
out=$(FAKE_SAVE_ALL=fail run_snapshot "$server" "$backups" noflush)
code=$?
[ "$code" -ne 0 ] && echo "$out" | grep -q "не подтвердил сохранение мира" \
    && ok "save-all молчит: снимок отменён с причиной, код $code" \
    || fail "save-all молчит: ждали отказ «не подтвердил сохранение мира», код $code: $out"
grep -qx "save-on" "$backups/commands" 2>/dev/null \
    && ! echo "$out" | grep -q "могло остаться выключенным" \
    && ok "save-all молчит: автосохранение возвращено подтверждённым save-on" \
    || fail "save-all молчит: save-on не подтверждён — мир остался бы без автосохранения: $out"

# lib: save-on молчит: успех снимка не выдаётся, но готовый архив уже на диске.
server="$work/noenable/server"
backups="$work/noenable/backups"
mkdir -p "$backups"
prepare "$server"
out=$(FAKE_SAVE_ON=fail run_snapshot "$server" "$backups" noenable)
code=$?
[ "$code" -ne 0 ] && echo "$out" | grep -q "автосохранение НЕ включено" \
    && ok "save-on молчит: успех снимка не выдаётся, код $code" \
    || fail "save-on молчит: ждали ошибку «автосохранение НЕ включено», код $code: $out"
ls "$backups"/world-*.tar.zst >/dev/null 2>&1 \
    && ok "save-on молчит: архив при этом снят (данные не потеряны)" \
    || fail "save-on молчит: архив не снят — работа сохранения потеряна зря"

# lib: замок занят другим снимком: публикация обязана узнать причину, а не получить молчаливый успех.
server="$work/busy/server"
backups="$work/busy/backups"
mkdir -p "$backups"
prepare "$server"
out=$(FAKE_FLOCK_BUSY=1 SNAPSHOT_LOCK_WAIT=2 run_snapshot "$server" "$backups" busy)
code=$?
[ "$code" -eq 75 ] && echo "$out" | grep -q "замок .* занят" \
    && ok "занятый замок: снимок не притворяется успешным, код 75 и кто держит" \
    || fail "занятый замок: ждали код 75 и «замок … занят», код $code: $out"

# lib: ротация: старые безымянные и метки старше 7 дней удаляются, свежие метки живут, из publish живут три
# lib: последних; имя архива (дата) важнее mtime файла.
server="$work/prune/server"
backups="$work/prune/backups"
mkdir -p "$backups"
prepare "$server"
t10="$(date -d '10 days ago' +%Y-%m-%d_%H-%M-%S)"
d2="$(date -d '2 days ago' +%Y-%m-%d)"
h2="$(date -d '2 days ago' +%H)"
for f in "world-$t10.tar.zst" "world-$t10.tar.zst.sha256" "world-$t10-before-x.tar.zst" \
    "world-${d2}_${h2}-05-00.tar.zst" "world-${d2}_${h2}-35-00.tar.zst" "world-$t10-publish-1.tar.zst" \
    "world-$t10-publish-2.tar.zst" "world-$t10-publish-3.tar.zst" "world-$t10-publish-4.tar.zst" \
    "world-${d2}_${h2}-40-00-before-y.tar.zst"; do
    echo x >"$backups/$f"
done
out=$(KEEP_ALL_HOURS=1 KEEP_HOURLY_DAYS=3 KEEP_DAILY_DAYS=7 KEEP_LABELED_DAYS=7 KEEP_PUBLISH=3 \
    run_snapshot "$server" "$backups" prune)
code=$?
[ "$code" = 0 ] && [ ! -e "$backups/world-$t10.tar.zst" ] && [ ! -e "$backups/world-$t10.tar.zst.sha256" ] \
    && [ ! -e "$backups/world-$t10-before-x.tar.zst" ] && [ ! -e "$backups/world-$t10-publish-1.tar.zst" ] \
    && [ -e "$backups/world-$t10-publish-4.tar.zst" ] && [ -e "$backups/world-${d2}_${h2}-35-00.tar.zst" ] \
    && [ ! -e "$backups/world-${d2}_${h2}-05-00.tar.zst" ] && [ -e "$backups/world-${d2}_${h2}-40-00-before-y.tar.zst" ] \
    && ok "ротация: старое удалено, свежая метка жива, три последних publish живы, в часе оставлен новый" \
    || fail "ротация: код=$code, состав: $(ls -1 "$backups" | tr '\n' ' ')"

# lib: мало места: удаляются самые старые архивы, а не самые свежие (до 17.11 удалялся новый).
server="$work/free/server"
backups="$work/free/backups"
mkdir -p "$backups"
prepare "$server"
a=()
for i in 1 2 3 4; do
    a+=("$(date -d "@$(( $(date +%s) - i * 3600 ))" +%Y-%m-%d_%H-%M-%S)")
    echo x >"$backups/world-${a[$((i - 1))]}.tar.zst"
done
echo 2000 >"$work/free.mb"
out=$(FAKE_FREE_FILE="$work/free.mb" MIN_FREE_MB=5000 run_snapshot "$server" "$backups" free)
code=$?
[ "$code" = 0 ] && [ -e "$backups/world-${a[0]}.tar.zst" ] && [ ! -e "$backups/world-${a[3]}.tar.zst" ] \
    && ok "мало места: удалён самый старый архив, свежий цел" \
    || fail "мало места: код=$code, состав: $(ls -1 "$backups" | tr '\n' ' '): $out"

exit "$failed"
