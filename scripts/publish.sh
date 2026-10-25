#!/usr/bin/env bash
# Каноническая публикация мода: сборка, снимок мира, установка jar на сервер, перезапуск.
#
# ВНИМАНИЕ: публикация мода и перезапуск сервера выполняются ТОЛЬКО ПО ПРЕДВАРИТЕЛЬНОМУ
# СОГЛАСОВАНИЮ С ПОЛЬЗОВАТЕЛЕМ. По умолчанию скрипт требует интерактивного подтверждения
# и без него не меняет ничего. Флаг -y/--yes означает, что согласование уже получено.
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"

assume_yes=false
dry_run=false
do_restart=true

usage() {
    cat <<'EOF'
Использование: scripts/publish.sh [опции]

Собирает мод, ставит jar в mods/ сервера и перезапускает работающий сервер.
Публикация и перезапуск выполняются ТОЛЬКО ПО СОГЛАСОВАНИЮ С ПОЛЬЗОВАТЕЛЕМ:
без явного подтверждения (или флага -y) скрипт не меняет ничего.

Опции:
  -y, --yes         согласование уже получено, не спрашивать подтверждения
  -n, --dry-run     показать план, ничего не менять
      --no-restart  установить jar, но не перезапускать работающий сервер
  -h, --help        эта справка
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -y|--yes) assume_yes=true ;;
        -n|--dry-run) dry_run=true ;;
        --no-restart) do_restart=false ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Неизвестная опция: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

# --- Гейт согласования: до любых изменений ---------------------------------
echo "ВНИМАНИЕ: публикация мода и перезапуск сервера выполняются ТОЛЬКО ПО"
echo "ПРЕДВАРИТЕЛЬНОМУ СОГЛАСОВАНИЮ С ПОЛЬЗОВАТЕЛЕМ."
echo

if $assume_yes; then
    echo "Согласование подтверждено флагом -y/--yes."
elif ! $dry_run; then
    printf 'Продолжить публикацию на %s? [y/N] ' "$SERVER_DIR"
    read -r answer || answer=""
    case "$answer" in
        y|Y|yes|YES|да|Да|ДА) ;;
        *)
            echo "Отменено: согласование не получено, изменения не внесены." >&2
            exit 1
            ;;
    esac
fi

# --- Режим dry-run: только план --------------------------------------------
if $dry_run; then
    echo "Режим --dry-run: ниже план, изменения не вносятся."
    echo "  1. сборка мода: ./gradlew --quiet assemble (без тестов — решение владельца 25.10)"
    echo "  2. поиск build/libs/theminespirit-*.jar (кроме -sources/-javadoc), берётся самый свежий"
    echo "  3. снимок мира: $SERVER_DIR/scripts/snapshot.sh (если есть)"
    echo "  4. удаление старых $SERVER_DIR/mods/theminespirit-*.jar"
    echo "  5. копирование нового jar в $SERVER_DIR/mods/"
    if $do_restart; then
        echo "  6. перезапуск сервера, если tmux-сессия активна (stop.sh/start.sh)"
    else
        echo "  6. перезапуск сервера отключён (--no-restart)"
    fi
    echo "  7. запись версии и даты установки в mod.json страницы «Сервер» gateway и коммит в gateway"
    echo "  8. тег v<версия> в текущем git-репозитории и отправка тега в remote backup (если настроен)"
    echo "Изменения не вносились."
    exit 0
fi

# --- Сборка ----------------------------------------------------------------
cd "$MOD_DIR"
echo "Сборка мода..."
./gradlew --quiet assemble

# --- Поиск jar мода по шаблону ---------------------------------------------
# Имя содержит версию, поэтому старые jar остаются в build/libs; свежесобранный — самый новый.
mapfile -t jars < <(find build/libs -maxdepth 1 -name 'theminespirit-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
if [ "${#jars[@]}" -eq 0 ]; then
    echo "Сборка не дала theminespirit-*.jar в build/libs/" >&2
    exit 1
fi
if [ "${#jars[@]}" -gt 1 ]; then
    echo "В build/libs несколько jar мода, беру самый свежий:" >&2
    printf '  %s\n' "${jars[@]}" >&2
fi
jar="${jars[0]}"
size="$(stat -c %s "$jar")"
if [ "$size" -lt 4096 ]; then
    echo "jar подозрительно мал ($size байт): $jar" >&2
    exit 1
fi
echo "Найден $(basename "$jar") ($size байт)"

# --- Определение состояния сервера -----------------------------------------
if [ -f "$SERVER_DIR/scripts/common.sh" ]; then
    # shellcheck source=/dev/null
    source "$SERVER_DIR/scripts/common.sh"
    TMUX_SESSION="${TMUX_SESSION:-minecraft}"
else
    TMUX_SESSION="${TMUX_SESSION:-minecraft}"
    is_running() { tmux has-session -t "$TMUX_SESSION" 2>/dev/null; }
fi

was_running=false
if is_running; then
    was_running=true
fi

# --- Снимок мира (если есть) -----------------------------------------------
if [ -x "$SERVER_DIR/scripts/snapshot.sh" ]; then
    echo "Снимок мира..."
    "$SERVER_DIR/scripts/snapshot.sh"
else
    echo "snapshot.sh не найден, снимок мира пропущен."
fi

# --- Установка мода --------------------------------------------------------
if $do_restart && $was_running; then
    echo "Остановка сервера..."
    "$SERVER_DIR/scripts/stop.sh"
fi

echo "Установка мода в $SERVER_DIR/mods/"
rm -f "$SERVER_DIR"/mods/theminespirit-*.jar
cp "$jar" "$SERVER_DIR/mods/"
echo "Установлен $(basename "$jar")"

# --- Перезапуск ------------------------------------------------------------
if $do_restart; then
    if $was_running; then
        echo "Запуск сервера..."
        "$SERVER_DIR/scripts/start.sh"
    else
        echo "Сервер не запущен (tmux: $TMUX_SESSION), запуск не требуется."
    fi
elif $was_running; then
    echo "ВНИМАНИЕ: сервер работает; новый jar вступит в силу после ручного перезапуска"
    echo "(выбран --no-restart)."
fi

# --- Страница «Сервер» на gateway ------------------------------------------
# Дата берётся из mtime установленного jar: cp в mods/ ставит его в момент установки.
installed="$SERVER_DIR/mods/${jar##*/}"
status_args=("$installed")
if $was_running && ! $do_restart; then
    status_args+=(--pending)
fi
if ! bash "$MOD_DIR/scripts/publish-status.sh" "${status_args[@]}"; then
    echo "ВНИМАНИЕ: публикация состоялась, но страница «Сервер» не обновлена" >&2
fi

# --- Тег версии -------------------------------------------------------------
version="${jar##*/}"
version="${version#theminespirit-}"
version="${version%.jar}"
tag="v${version}"
if git -C "$MOD_DIR" rev-parse -q --verify "refs/tags/$tag" >/dev/null 2>&1; then
    echo "Тег $tag уже существует"
else
    git -C "$MOD_DIR" tag "$tag"
    echo "Поставлен тег $tag"
fi
if git -C "$MOD_DIR" remote get-url backup >/dev/null 2>&1; then
    if git -C "$MOD_DIR" push backup "refs/tags/$tag"; then
        echo "Тег $tag отправлен в backup"
    else
        echo "ВНИМАНИЕ: выкладка удалась, но тег $tag не отправлен в backup" >&2
    fi
else
    echo "Remote backup не настроен: тег $tag остался локальным"
fi
