#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"
TMUX_SESSION="${TMUX_SESSION:-minecraft}"
WORLD_BACKUPS="${WORLD_BACKUPS:-/opt/minecraft/world-backups}"
FORCELOAD_AREA="${FORCELOAD_AREA:--270 -587 -70 -387}"

usage() {
    cat <<'EOF'
Использование: world-restore.sh [-y] [архив]

  архив   имя архива в WORLD_BACKUPS (можно без .tar.zst) или полный путь.
          Без архива печатается список снимков.

  -y, --yes   не спрашивать подтверждение (обязателен без терминала: из скрипта, ssh-команды).
  -l, --list  список снимков.
  -h, --help  эта справка.

Порядок: снимок текущего мира «before-restore» → stop → разворот архива → start →
difficulty peaceful + forceload. При сбое запуска прежний мир возвращается на место.
Переменные: SERVER_DIR, TMUX_SESSION, WORLD_BACKUPS, FORCELOAD_AREA.
EOF
}

assume_yes=false
list_only=false
args=()
while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        -y|--yes) assume_yes=true ;;
        -l|--list) list_only=true ;;
        -*) echo "Неизвестная опция: $1" >&2; usage >&2; exit 2 ;;
        *) args+=("$1") ;;
    esac
    shift
done
set -- ${args[@]+"${args[@]}"}

is_running() { tmux has-session -t "$TMUX_SESSION" 2>/dev/null; }
send_command() { tmux send-keys -t "$TMUX_SESSION" "$1" Enter; }
snapshot_script="$(cd "$(dirname "$0")" && pwd)/world-snapshot.sh"
[ -x "$snapshot_script" ] || snapshot_script="$SERVER_DIR/scripts/world-snapshot.sh"

list_archives() {
    local path
    echo "Снимки мира в $WORLD_BACKUPS:"
    for path in $(ls -1r "$WORLD_BACKUPS"/world-*.tar.zst 2>/dev/null); do
        printf '  %-52s %6s  %s\n' "$(basename "$path")" \
            "$(du -h "$path" | cut -f1)" "$(stat -c %y "$path" | cut -d. -f1)"
    done
}

if $list_only || [ $# -eq 0 ]; then
    list_archives
    exit 0
fi

name="$1"
case "$name" in
    */*) archive="$name" ;;
    *.tar.zst) archive="$WORLD_BACKUPS/$name" ;;
    *) archive="$WORLD_BACKUPS/$name.tar.zst" ;;
esac
if [ ! -f "$archive" ]; then
    echo "Архив не найден: $archive" >&2
    list_archives >&2
    exit 1
fi

if [ -f "$archive.sha256" ]; then
    if ! (cd "$WORLD_BACKUPS" && sha256sum -c "$(basename "$archive").sha256" >/dev/null 2>&1); then
        echo "Контрольная сумма не совпала: $(basename "$archive") повреждён" >&2
        exit 1
    fi
    echo "Контрольная сумма совпала: $(basename "$archive")"
fi

if ! $assume_yes; then
    if [ ! -t 0 ]; then
        echo "Подтверждение обязательно: запустите с -y (без терминала спросить некого)." >&2
        exit 2
    fi
    printf 'Откатить мир на %s? Текущий мир будет сохранён в архив before-restore. [y/N] ' "$(basename "$archive")"
    read -r answer || answer=""
    case "$answer" in
        y|Y|yes|YES|да|Да|ДА) ;;
        *) echo "Отменено." ; exit 1 ;;
    esac
fi

mkdir -p "$WORLD_BACKUPS"
exec 8>"$WORLD_BACKUPS/.restore.lock"
if ! flock -n 8; then
    echo "Уже идёт восстановление мира." >&2
    exit 1
fi

echo "Снимок текущего мира (before-restore)…"
snapshot_output="$(WORLD_SNAPSHOT_INSIDE_RESTORE=1 "$snapshot_script" before-restore)"
echo "$snapshot_output"
before_archive="$(printf '%s\n' "$snapshot_output" | sed -n 's/^снимок готов: \([^ ]*\).*/\1/p' | tail -n 1)"

echo "Остановка сервера…"
if [ -x "$SERVER_DIR/scripts/stop.sh" ]; then
    "$SERVER_DIR/scripts/stop.sh"
elif is_running; then
    send_command "stop"
    for _ in $(seq 1 120); do
        is_running || break
        sleep 1
    done
fi
if is_running; then
    echo "Сервер не остановился, откат отменён." >&2
    exit 1
fi

old="$WORLD_BACKUPS/.restore-old.$$"
rm -rf "$old"
mv "$SERVER_DIR/world" "$old"

rollback() {
    echo "Откат не удался — возвращаю прежний мир." >&2
    if [ -x "$SERVER_DIR/scripts/stop.sh" ]; then
        "$SERVER_DIR/scripts/stop.sh" || true
    fi
    rm -rf "$SERVER_DIR/world"
    mv "$old" "$SERVER_DIR/world"
    if [ -x "$SERVER_DIR/scripts/start.sh" ]; then
        "$SERVER_DIR/scripts/start.sh" || true
    fi
    exit 1
}
trap rollback ERR

echo "Разворот $(basename "$archive")…"
tar --zstd -xf "$archive" -C "$SERVER_DIR"
[ -f "$SERVER_DIR/world/level.dat" ] || { echo "В архиве нет world/level.dat" >&2; false; }

# lib: lock fd закрываем перед запуском tmux: иначе новый tmux-сервер наследует fd и держит
# lib: .restore.lock вечно — cron-снимки навсегда пропускаются с «идёт восстановление».
exec 8>&-
echo "Запуск сервера…"
if [ -x "$SERVER_DIR/scripts/start.sh" ]; then
    "$SERVER_DIR/scripts/start.sh" || rollback
else
    tmux new-session -d -s "$TMUX_SESSION" -c "$SERVER_DIR" "./run.sh nogui"
    sleep 30
fi

send_command "difficulty peaceful"
send_command "forceload add $FORCELOAD_AREA"
sleep 2
send_command "save-all flush"
trap - ERR

rm -rf "$old"
echo "Мир откачен на $(basename "$archive")."
[ -n "$before_archive" ] && echo "Прежний мир сохранён: $before_archive"
echo "Сервер запущен: difficulty peaceful, forceload $FORCELOAD_AREA."
