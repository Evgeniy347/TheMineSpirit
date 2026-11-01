#!/usr/bin/env bash
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"
CLIENT_DIR="${CLIENT_DIR:-/opt/minecraft/client}"
GATEWAY_DIR="${GATEWAY_DIR:-/opt/gateway}"
STATUS_REL="users/admin/sites/the-mine-spirit/server/mod.json"

assume_yes=false
dry_run=false
do_restart=true
mode=full

usage() {
    cat <<'EOF'
Использование: scripts/publish.sh [опции]

Публикует мод одним запуском: сборка → снимок мира (полная) → jar в mods/ сервера →
пак клиента и раздача лаунчера (/opt/minecraft/client/publish.sh) → перезапуск сервера →
страница «Сервер» (mod.json) → тег (полная). Вручную копировать jar и править пак/страницу
не нужно и запрещено: иначе клиент разойдётся с сервером.

Публикация и перезапуск выполняются ТОЛЬКО ПО СОГЛАСОВАНИЮ С ПОЛЬЗОВАТЕЛЕМ:
без явного подтверждения (или флага -y) скрипт не меняет ничего.

Режимы:
  (по умолчанию)  полная публикация из main: снимок мира, тег и отправка тега в backup
      --temp      временная сборка для живой проверки из своего дерева (не main): без снимка
                  мира, тега и отправки тега; пак клиента и страница обновляются
      --sync      только согласовать пак клиента и страницу с уже установленным jar;
                  без сборки, перезапуска и подтверждения (так делает scripts/start.sh)

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
        --temp)
            [ "$mode" = full ] || { echo "Режим уже выбран: $mode" >&2; exit 2; }
            mode=temp
            ;;
        --sync)
            [ "$mode" = full ] || { echo "Режим уже выбран: $mode" >&2; exit 2; }
            mode=sync
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Неизвестная опция: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

current_branch() {
    git -C "$MOD_DIR" symbolic-ref --quiet --short HEAD 2>/dev/null || true
}

version_of() {
    local name="${1##*/}"
    name="${name#theminespirit-}"
    printf '%s' "${name%.jar}"
}

sha256_of() {
    sha256sum "$1" 2>/dev/null | cut -d' ' -f1
}

server_jars() {
    find "$SERVER_DIR/mods" -maxdepth 1 -name 'theminespirit-*.jar' -printf '%T@ %p\n' 2>/dev/null \
        | sort -rn | cut -d' ' -f2-
}

update_page() {
    local jar="$1" pending="${2:-}"
    if [ -n "$pending" ]; then
        bash "$MOD_DIR/scripts/publish-status.sh" "$jar" --pending
    else
        bash "$MOD_DIR/scripts/publish-status.sh" "$jar"
    fi
}

sync_client_pack() {
    local jar="$1" name pack old
    name="${jar##*/}"
    pack="$CLIENT_DIR/pack/mods"
    if [ ! -d "$pack" ]; then
        echo "ОШИБКА: нет каталога клиентского пака $pack" >&2
        return 1
    fi
    if [ ! -f "$CLIENT_DIR/publish.sh" ]; then
        echo "ОШИБКА: нет $CLIENT_DIR/publish.sh — пак лаунчера не обновить" >&2
        return 1
    fi
    for old in "$pack"/theminespirit-*.jar; do
        [ -e "$old" ] || continue
        if [ "$old" != "$pack/$name" ] && ! rm -f "$old"; then
            echo "ОШИБКА: не удалить старый jar пака: $old" >&2
            return 1
        fi
    done
    if ! cp -f "$jar" "$pack/$name"; then
        echo "ОШИБКА: не скопировать $jar в $pack/$name" >&2
        return 1
    fi
    if ! bash "$CLIENT_DIR/publish.sh"; then
        echo "ОШИБКА: $CLIENT_DIR/publish.sh не выложил пак клиента" >&2
        return 1
    fi
    if [ "$(sha256_of "$jar")" != "$(sha256_of "$pack/$name")" ]; then
        echo "ОШИБКА: jar в паке клиента ($pack/$name) не совпал с серверным" >&2
        return 1
    fi
    echo "Пак клиента обновлён: $pack/$name"
}

verify_publication() {
    local jar="$1" pending="${2:-}" name version sha pack status failed=0
    name="${jar##*/}"
    version="$(version_of "$jar")"
    sha="$(sha256_of "$jar")"
    pack="$CLIENT_DIR/pack/mods/$name"
    status="$GATEWAY_DIR/$STATUS_REL"
    if [ -z "$pending" ] && [ "$(sha256_of "$pack")" != "$sha" ]; then
        echo "ОШИБКА: пак клиента не содержит $name с тем же sha256" >&2
        failed=1
    fi
    if [ ! -f "$status" ]; then
        echo "ОШИБКА: страницы «Сервер» нет: $status" >&2
        failed=1
    elif ! grep -q "\"version\": \"$version\"" "$status" || ! grep -q "\"sha256\": \"$sha\"" "$status"; then
        echo "ОШИБКА: $status не совпадает с $name" >&2
        failed=1
    fi
    return "$failed"
}

sync_existing() {
    local jars=() jar name version pack status pack_needs=0 page_needs=0 old
    mapfile -t jars < <(server_jars)
    if [ "${#jars[@]}" -eq 0 ]; then
        echo "mods/: мода нет, синхронизировать нечего"
        return 0
    fi
    jar="${jars[0]}"
    if [ "${#jars[@]}" -gt 1 ]; then
        echo "mods/: несколько jar мода, оставляю свежий ${jar##*/}, лишние удаляю:" >&2
        printf '  %s\n' "${jars[@]:1}" >&2
        for old in "${jars[@]:1}"; do
            rm -f "$old"
        done
    fi
    name="${jar##*/}"
    version="$(version_of "$jar")"
    pack="$CLIENT_DIR/pack/mods/$name"
    for old in "$CLIENT_DIR"/pack/mods/theminespirit-*.jar; do
        [ -e "$old" ] || continue
        [ "$old" = "$pack" ] || pack_needs=1
    done
    [ "$(sha256_of "$pack")" = "$(sha256_of "$jar")" ] || pack_needs=1
    status="$GATEWAY_DIR/$STATUS_REL"
    if [ ! -f "$status" ] \
        || ! grep -q "\"version\": \"$version\"" "$status" \
        || ! grep -q "\"sha256\": \"$(sha256_of "$jar")\"" "$status"; then
        page_needs=1
    fi
    if [ "$pack_needs" -eq 0 ] && [ "$page_needs" -eq 0 ]; then
        echo "Клиент и страница совпадают с $name ($version), синхронизация не нужна"
        return 0
    fi
    if [ "$pack_needs" -eq 1 ]; then
        echo "Рассинхрон пака: сервер $name ($version), пак клиента приводится к нему"
        sync_client_pack "$jar"
    else
        echo "Пак клиента совпадает с $name ($version)"
    fi
    if [ "$page_needs" -eq 1 ]; then
        echo "Рассинхрон страницы: записываю $name ($version) в mod.json"
        update_page "$jar"
    fi
    verify_publication "$jar"
}

if [ "$mode" = sync ]; then
    if $dry_run; then
        echo "Режим --sync: сравнить $SERVER_DIR/mods/theminespirit-*.jar с паком клиента и $GATEWAY_DIR/$STATUS_REL"
        echo "и при расхождении обновить пак (/opt/minecraft/client/publish.sh) и страницу."
        exit 0
    fi
    if tmux has-session -t "${TMUX_SESSION:-minecraft}" 2>/dev/null; then
        echo "Сервер работает: какая сборка загружена, по файлам не видно — синхронизацию откладываю до запуска (start.sh)"
        exit 0
    fi
    sync_existing
    exit 0
fi

if [ "$mode" = full ]; then
    branch="$(current_branch)"
    if [ "$branch" != main ]; then
        echo "ОШИБКА: полная публикация — только из main (сейчас '${branch:-detached HEAD}')." >&2
        echo "Для живой проверки из своего дерева: scripts/publish.sh --temp." >&2
        exit 2
    fi
else
    branch="$(current_branch)"
    if [ -z "$branch" ] || [ "$branch" = main ]; then
        echo "ОШИБКА: --temp — из дерева задачи (не main). Возврат на main: scripts/publish.sh без --temp." >&2
        exit 2
    fi
fi

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

if $dry_run; then
    echo "Режим --dry-run: ниже план, изменения не вносятся."
    echo "  1. сборка мода: ./gradlew --quiet assemble (без тестов — решение владельца 25.10)"
    echo "  2. поиск build/libs/theminespirit-*.jar (кроме -sources/-javadoc), берётся самый свежий"
    if [ "$mode" = full ]; then
        echo "  3. снимок мира: $SERVER_DIR/scripts/snapshot.sh (если есть)"
    else
        echo "  3. снимок мира пропускается (--temp)"
    fi
    echo "  4. удаление старых $SERVER_DIR/mods/theminespirit-*.jar"
    echo "  5. копирование нового jar в $SERVER_DIR/mods/"
    if $do_restart; then
        echo "  6. перезапуск сервера, если tmux-сессия активна (stop.sh/start.sh)"
    else
        echo "  6. перезапуск сервера отключён (--no-restart)"
    fi
    echo "  7. пак клиента: $CLIENT_DIR/pack/mods/theminespirit-<версия>.jar и $CLIENT_DIR/publish.sh"
    echo "  8. версия и дата установки в mod.json страницы «Сервер» ($GATEWAY_DIR/$STATUS_REL) и коммит в gateway"
    if [ "$mode" = full ]; then
        echo "  9. тег v<версия> в git и отправка тега в remote backup (если настроен)"
    else
        echo "  9. тег и backup пропускаются (--temp)"
    fi
    echo "Изменения не вносились."
    exit 0
fi

cd "$MOD_DIR"
echo "Сборка мода..."
./gradlew --quiet assemble

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

if [ -f "$SERVER_DIR/scripts/common.sh" ]; then
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

if [ "$mode" = full ]; then
    if [ -x "$SERVER_DIR/scripts/snapshot.sh" ]; then
        echo "Снимок мира..."
        "$SERVER_DIR/scripts/snapshot.sh"
    else
        echo "snapshot.sh не найден, снимок мира пропущен."
    fi
fi

if $do_restart && $was_running; then
    echo "Остановка сервера..."
    "$SERVER_DIR/scripts/stop.sh"
fi

echo "Установка мода в $SERVER_DIR/mods/"
rm -f "$SERVER_DIR"/mods/theminespirit-*.jar
cp "$jar" "$SERVER_DIR/mods/"
installed="$SERVER_DIR/mods/${jar##*/}"
echo "Установлен $(basename "$installed")"

failed=0
pending=""
if $do_restart || ! $was_running; then
    if ! sync_client_pack "$installed"; then
        failed=1
    fi
else
    echo "ВНИМАНИЕ: сервер работает, пак клиента не меняю, чтобы клиент не разошёлся с ним;"
    echo "страница помечает версию как ждущую перезапуска (--no-restart)."
    pending=--pending
fi

if $do_restart; then
    if $was_running; then
        echo "Запуск сервера..."
        if ! "$SERVER_DIR/scripts/start.sh"; then
            echo "ОШИБКА: сервер не запустился" >&2
            failed=1
        fi
    else
        echo "Сервер не запущен (tmux: $TMUX_SESSION), запуск не требуется."
    fi
elif $was_running; then
    echo "ВНИМАНИЕ: сервер работает; новый jar вступит в силу после ручного перезапуска"
    echo "(выбран --no-restart)."
fi

if ! update_page "$installed" "$pending"; then
    echo "ВНИМАНИЕ: публикация состоялась, но страница «Сервер» не обновлена" >&2
    failed=1
fi
if ! verify_publication "$installed" "$pending"; then
    failed=1
fi

if [ "$failed" -ne 0 ]; then
    echo "ОШИБКА: публикация неполная — сервер $installed установлен, но пак клиента или страница" >&2
    echo "не совпали. Исправление: scripts/publish.sh --sync (или перезапуск сервера)." >&2
    exit 1
fi

if [ "$mode" = temp ]; then
    echo "Временная публикация $installed выполнена без тега; возврат на main: scripts/publish.sh."
    exit 0
fi

version="$(version_of "$installed")"
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
