#!/usr/bin/env bash
# lib: Проверка server/scripts/world-snapshot.sh на поддельной консоли: снимок не начинается без подтверждения
# lib: консоли, save-off без save-on не остаётся, занятый замок называется, ошибка не маскируется нулём.
# lib: Запускать на Linux (нужны flock, fuser, zstd и tar --zstd). Настоящий сервер и мир не нужны.
set -u
snapshot="${1:-server/scripts/world-snapshot.sh}"
[ -f "$snapshot" ] || {
    echo "FAIL нет скрипта снимка: $snapshot"
    exit 2
}
snapshot="$(cd "$(dirname "$snapshot")" && pwd)/$(basename "$snapshot")"
root="$(pwd)"
work="$root/TestResults/world-snapshot-check.$$"
trap 'rm -rf "$work"' EXIT
rm -rf "$work"
mkdir -p "$work/bin" "$work/server/world" "$work/server/logs"
printf 'level' >"$work/server/world/level.dat"
printf 'region' >"$work/server/world/r.0.0.mca"
failed=0

ok() { echo "OK $1"; }
fail() {
    echo "FAIL $1"
    failed=1
}

cat >"$work/bin/tmux" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
    has-session) exit 0 ;;
    send-keys)
        cmd="${4:-}"
        printf '%s\n' "$cmd" >>"$FAKE_COMMANDS"
        echo "[fake] тик сервера" >>"$FAKE_LOG"
        case "$cmd" in
            save-off)
                [ "${FAKE_SAVE_OFF:-ok}" = ok ] && echo "[fake] Automatic saving is now disabled" >>"$FAKE_LOG"
                ;;
            save-all*)
                [ "${FAKE_SAVE_ALL:-ok}" = ok ] && echo "[fake] Saved the game" >>"$FAKE_LOG"
                ;;
            save-on)
                [ "${FAKE_SAVE_ON:-ok}" = ok ] && echo "[fake] Automatic saving is now enabled" >>"$FAKE_LOG"
                ;;
        esac
        ;;
esac
exit 0
EOF
chmod +x "$work/bin/tmux"

# lib: опыт: имя, save-off, save-all, save-on, ожидание замка, не пересоздавать каталог архивов
run_snapshot() {
    local name="$1" save_off="${2:-ok}" save_all="${3:-ok}" save_on="${4:-ok}" wait="${5:-0}" keep="${6:-0}"
    backups="$work/backups-$name"
    [ "$keep" = 1 ] || rm -rf "$backups"
    mkdir -p "$backups"
    : >"$work/server/logs/latest.log"
    : >"$work/commands-$name"
    PATH="$work/bin:$PATH" \
        SERVER_DIR="$work/server" \
        WORLD_BACKUPS="$backups" \
        MIN_FREE_MB=1 \
        SAVE_ACK_TIMEOUT=2 \
        SAVE_TIMEOUT=2 \
        SNAPSHOT_LOCK_WAIT="$wait" \
        FAKE_LOG="$work/server/logs/latest.log" \
        FAKE_COMMANDS="$work/commands-$name" \
        FAKE_SAVE_OFF="$save_off" \
        FAKE_SAVE_ALL="$save_all" \
        FAKE_SAVE_ON="$save_on" \
        bash "$snapshot" >"$work/out-$name" 2>"$work/err-$name"
    code=$?
}

run_snapshot dead fail fail fail
if [ "$code" -ne 0 ] && grep -q "не подтвердила save-off" "$work/err-dead" && grep -q "консоль не читает stdin" "$work/err-dead"; then
    ok "мёртвая консоль: снимок отменён с причиной, код $code"
else
    fail "мёртвая консоль: ждали отказ с причиной «не подтвердила save-off», код $code: $(cat "$work/err-dead")"
fi
if grep -qx "save-on" "$work/commands-dead"; then
    ok "мёртвая консоль: save-on всё равно отправлен (не оставляем автосохранение выключенным)"
else
    fail "мёртвая консоль: save-on не отправлен, автосохранение осталось бы выключенным"
fi
if ls "$work/backups-dead"/world-*.tar.zst >/dev/null 2>&1; then
    fail "мёртвая консоль: архив не должен сниматься без подтверждённого save-all"
else
    ok "мёртвая консоль: архив не создан"
fi

run_snapshot noflush ok fail ok
if [ "$code" -ne 0 ] && grep -q "не подтвердил сохранение мира" "$work/err-noflush"; then
    ok "save-all молчит: снимок отменён с причиной, код $code"
else
    fail "save-all молчит: ждали отказ «не подтвердил сохранение мира», код $code: $(cat "$work/err-noflush")"
fi
if grep -qx "save-on" "$work/commands-noflush" && ! grep -q "могло остаться выключенным" "$work/err-noflush"; then
    ok "save-all молчит: автосохранение возвращено подтверждённым save-on"
else
    fail "save-all молчит: save-on не подтверждён — мир остался бы без автосохранения"
fi

run_snapshot noenable ok ok fail
if [ "$code" -ne 0 ] && grep -q "автосохранение НЕ включено" "$work/err-noenable"; then
    ok "save-on молчит: успех снимка не выдаётся, код $code"
else
    fail "save-on молчит: ждали ошибку «автосохранение НЕ включено», код $code: $(cat "$work/err-noenable")"
fi
if ls "$work/backups-noenable"/world-*.tar.zst >/dev/null 2>&1; then
    ok "save-on молчит: архив при этом снят (данные не потеряны)"
else
    fail "save-on молчит: архив не снят — работа сохранения потеряна зря"
fi

mkdir -p "$work/backups-busy"
(
    exec 9>"$work/backups-busy/.snapshot.lock"
    flock 9
    sleep 15
) &
holder=$!
sleep 1
run_snapshot busy ok ok ok 2 1
if [ "$code" -eq 75 ] && grep -q "замок .* занят" "$work/err-busy"; then
    ok "занятый замок: снимок не притворяется успешным, код 75 и кто держит"
else
    fail "занятый замок: ждали код 75 и «замок … занят», код $code: $(cat "$work/err-busy")"
fi
kill "$holder" 2>/dev/null
wait "$holder" 2>/dev/null

run_snapshot live ok ok ok
archive="$(ls "$work/backups-live"/world-*.tar.zst 2>/dev/null | head -n 1)"
if [ "$code" -eq 0 ] && [ -n "$archive" ] && [ -s "$archive.sha256" ]; then
    ok "живая консоль: архив и sha256 созданы"
else
    fail "живая консоль: ждали код 0, архив и sha256, код $code: $(cat "$work/err-live")"
fi
if [ -n "$archive" ] && (set +o pipefail; tar --zstd -tf "$archive" | grep -qx "world/level.dat"); then
    ok "живая консоль: в архиве world/level.dat"
else
    fail "живая консоль: в архиве нет world/level.dat"
fi
order="$(tr '\n' ' ' <"$work/commands-live")"
case "$order" in
    "save-off save-all flush save-on "*) ok "команды в порядке save-off → save-all flush → save-on" ;;
    *) fail "порядок команд снимка: получили «$order»" ;;
esac

exit "$failed"
