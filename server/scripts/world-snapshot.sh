#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"
TMUX_SESSION="${TMUX_SESSION:-minecraft}"
WORLD_BACKUPS="${WORLD_BACKUPS:-/opt/minecraft/backups/world}"

KEEP_ALL_HOURS="${KEEP_ALL_HOURS:-24}"
KEEP_HOURLY_DAYS="${KEEP_HOURLY_DAYS:-7}"
KEEP_DAILY_DAYS="${KEEP_DAILY_DAYS:-30}"
MIN_FREE_MB="${MIN_FREE_MB:-10240}"

usage() {
    cat <<'EOF'
Использование: world-snapshot.sh [метка]

Снимает консистентный архив мира, не останавливая сервер:
  save-off → save-all flush → tar --zstd → save-on.

  метка   необязательный суффикс имени: world-<дата_время>-<метка>.tar.zst;
          архивы с меткой ротация не удаляет (ручной снимок «до эксперимента»).

Переменные окружения: SERVER_DIR, TMUX_SESSION, WORLD_BACKUPS, MIN_FREE_MB,
KEEP_ALL_HOURS (24), KEEP_HOURLY_DAYS (7), KEEP_DAILY_DAYS (30).
EOF
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
esac

label="${1:-}"
label="$(printf '%s' "$label" | tr ' ' '-' | tr -cd 'A-Za-z0-9._-')"

is_running() { tmux has-session -t "$TMUX_SESSION" 2>/dev/null; }
send_command() { tmux send-keys -t "$TMUX_SESSION" "$1" Enter; }
log_size() { stat -c %s "$SERVER_DIR/logs/latest.log" 2>/dev/null || echo 0; }

wait_for_log() {
    local pattern="$1" offset="$2" timeout="${3:-60}"
    for _ in $(seq 1 "$timeout"); do
        if tail -c +"$((offset + 1))" "$SERVER_DIR/logs/latest.log" 2>/dev/null | grep -q "$pattern"; then
            return 0
        fi
        sleep 1
    done
    return 1
}

free_mb() {
    df -Pm "$WORLD_BACKUPS" 2>/dev/null | awk 'NR==2 {print $4}'
}

archive_time() {
    local name="$1" stamp
    stamp="${name#world-}"
    stamp="${stamp:0:19}"
    date -d "${stamp:0:10} ${stamp:11:2}:${stamp:14:2}:${stamp:17:2}" +%s 2>/dev/null || true
}

is_labeled() {
    local stem="${1%.tar.zst}"
    [ "${#stem}" -gt 25 ]
}

delete_archive() {
    local path="$1"
    rm -f "$path" "$path.sha256"
    echo "ротация: удалён $(basename "$path")"
}

prune_archives() {
    local now base path age stamp hour day
    local -A keep_hour=() keep_day=()
    now="$(date +%s)"
    while IFS= read -r path; do
        base="$(basename "$path")"
        is_labeled "$base" && continue
        stamp="$(archive_time "$base")"
        [ -n "$stamp" ] || continue
        age=$(( now - stamp ))
        if [ "$age" -le $(( KEEP_ALL_HOURS * 3600 )) ]; then
            continue
        elif [ "$age" -le $(( KEEP_HOURLY_DAYS * 86400 )) ]; then
            hour="${base:6:13}"
            if [ -z "${keep_hour[$hour]:-}" ]; then
                keep_hour[$hour]="$base"
                continue
            fi
        elif [ "$age" -le $(( KEEP_DAILY_DAYS * 86400 )) ]; then
            day="${base:6:10}"
            if [ -z "${keep_day[$day]:-}" ]; then
                keep_day[$day]="$base"
                continue
            fi
        fi
        delete_archive "$path"
    done < <(ls -1r "$WORLD_BACKUPS"/world-*.tar.zst 2>/dev/null)
}

enforce_free_space() {
    local free path
    free="$(free_mb)"
    while [ -n "$free" ] && [ "$free" -lt "$MIN_FREE_MB" ]; do
        path="$(ls -1r "$WORLD_BACKUPS"/world-*.tar.zst 2>/dev/null | head -n 1)"
        if [ -z "$path" ]; then
            echo "мало места: свободно ${free} МБ, архивов больше нет (нужно ${MIN_FREE_MB} МБ)" >&2
            return 1
        fi
        echo "мало места: свободно ${free} МБ, удаляю самый старый $(basename "$path")" >&2
        delete_archive "$path"
        free="$(free_mb)"
    done
}

mkdir -p "$WORLD_BACKUPS"

exec 9>"$WORLD_BACKUPS/.snapshot.lock"
if ! flock -n 9; then
    echo "снимок пропущен: уже идёт другой снимок"
    exit 0
fi
if [ "${WORLD_SNAPSHOT_INSIDE_RESTORE:-0}" != "1" ]; then
    exec 8>"$WORLD_BACKUPS/.restore.lock"
    if ! flock -n 8; then
        echo "снимок пропущен: идёт восстановление мира"
        exit 0
    fi
fi

enforce_free_space

stamp="$(date '+%Y-%m-%d_%H-%M-%S')"
name="world-$stamp"
[ -n "$label" ] && name="$name-$label"
archive="$WORLD_BACKUPS/$name.tar.zst"
tmp="$WORLD_BACKUPS/.$name.tar.zst.tmp.$$"

save_disabled=false
cleanup() {
    rm -f "$tmp"
    if $save_disabled && is_running; then
        send_command "save-on"
    fi
}
trap cleanup EXIT

started="$(date +%s)"
echo "снимок: $(basename "$archive") → $WORLD_BACKUPS (свободно $(free_mb) МБ)"

if is_running; then
    offset="$(log_size)"
    send_command "save-off"
    save_disabled=true
    send_command "save-all flush"
    if ! wait_for_log 'Saved the game' "$offset" 120; then
        echo "снимок отменён: сервер не подтвердил сохранение мира" >&2
        exit 1
    fi
    echo "мир сброшен на диск, автосохранение приостановлено"
else
    echo "сервер не запущен: архив снимается с состояния на диске"
fi

# lib: tar возвращает 1, когда файл изменился во время чтения (живой лог мода пишется и при save-off);
# lib: такой архив пригоден, ошибкой считаем только код 2 и выше.
tar_code=0
tar --zstd -cf "$tmp" -C "$SERVER_DIR" world || tar_code=$?
if [ "$tar_code" -gt 1 ]; then
    echo "снимок отменён: tar завершился с кодом $tar_code" >&2
    exit 1
fi
[ "$tar_code" -eq 1 ] && echo "предупреждение: часть файлов менялась при чтении (tar 1), архив пригоден"

if $save_disabled && is_running; then
    send_command "save-on"
    save_disabled=false
    echo "автосохранение включено"
fi

if [ ! -s "$tmp" ]; then
    echo "снимок отменён: архив пуст" >&2
    exit 1
fi
# lib: grep -q закрывает поток раньше tar; при pipefail SIGPIPE выглядит как порча архива.
if ! (set +o pipefail; tar --zstd -tf "$tmp" | grep -qx 'world/level.dat'); then
    echo "снимок отменён: в архиве нет world/level.dat" >&2
    exit 1
fi

mv "$tmp" "$archive"
(cd "$WORLD_BACKUPS" && sha256sum "$(basename "$archive")" >"$(basename "$archive").sha256")
size="$(du -h "$archive" | cut -f1)"
took=$(( $(date +%s) - started ))
echo "снимок готов: $(basename "$archive") ($size, ${took} с)"

prune_archives
