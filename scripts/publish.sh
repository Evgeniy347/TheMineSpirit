#!/usr/bin/env bash
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"
CLIENT_DIR="${CLIENT_DIR:-/opt/minecraft/client}"
GATEWAY_DIR="${GATEWAY_DIR:-/opt/gateway}"
STATUS_REL="users/admin/sites/the-mine-spirit/server/mod.json"
SERVER_FILES="$MOD_DIR/server"
. "$MOD_DIR/scripts/module-paths.sh"
. "$MOD_DIR/scripts/python-bin.sh"
JAR_DIR="$MOD_DIR/$MOD_JAR_DIR"
WORLD_BACKUPS="${WORLD_BACKUPS:-/opt/minecraft/backups/world}"
CLEANUP_SCRIPT_DIR="${CLEANUP_SCRIPT_DIR:-/opt/minecraft/scripts}"
BUILD_LOCK="${BUILD_LOCK:-/opt/minecraft/tmp/build.lock}"
BUILD_LOCK_WAIT="${BUILD_LOCK_WAIT:-1800}"
MOD_JAR_MIN_CLASSES="${MOD_JAR_MIN_CLASSES:-1000}"

assume_yes=false
dry_run=false
do_restart=true
check_build=false
verify_only=""
mode=full

usage() {
    cat <<'EOF'
Использование: scripts/publish.sh [опции]

Публикует мод одним запуском: сборка → раскладка скриптов и конфигов сервера из server/ →
снимок мира в архив (полная) → jar в mods/ сервера → пак клиента и раздача лаунчера
(/opt/minecraft/client/publish.sh) → перезапуск сервера → страница «Сервер» (mod.json) →
тег (полная). Вручную копировать jar, скрипты и править пак/страницу не нужно и запрещено:
иначе клиент или сервер разойдутся с репозиторием.

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
      --check-build только собрать :mod и показать путь/sha jar, ничего не выкладывая
      --verify-jar <файл> проверить содержимое jar и выйти (сборки и выкладки нет)
  -h, --help        эта справка

Перед установкой jar проверяется по содержимому: в нём обязан быть непустой
META-INF/neoforge.mods.toml с modId мода и больше 1000 классов (MOD_JAR_MIN_CLASSES).
Иначе — ОШИБКА и выход: сервер и пак клиента не трогаются (08.11 jar с пустым
neoforge.mods.toml уронил сервер в «Missing ModLoader»).
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -y|--yes) assume_yes=true ;;
        -n|--dry-run) dry_run=true ;;
        --no-restart) do_restart=false ;;
        --check-build) check_build=true ;;
        --verify-jar)
            shift
            [ $# -gt 0 ] || { echo "--verify-jar требует путь к jar" >&2; exit 2; }
            verify_only="$1"
            ;;
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

# lib: след выбранной сборки в выводе — по нему разбирают инцидент выкладки: какой коммит, какой файл,
# когда он собран. Без следа причина (гонка/кэш/старый jar) видна только по косвенным признакам.
trace_jar() {
    local jar="$1" size mtime sha
    size="$(stat -c %s "$jar" 2>/dev/null || echo '?')"
    mtime="$(date -r "$jar" '+%F %T' 2>/dev/null || echo '?')"
    sha="$(sha256_of "$jar")"
    echo "Коммит: $(git -C "$MOD_DIR" rev-parse HEAD 2>/dev/null || echo '?')"
    echo "jar: $jar ($size байт, изменён $mtime, sha256 ${sha:-?})"
}

# lib: jar читается по содержимому: 08.11 в 0.1.3253 уехал jar с пустым META-INF/neoforge.mods.toml,
# сервер не стартовал («Missing ModLoader»), простой ~20 минут. Проверка — до остановки сервера.
jar_reader() {
    if command -v unzip >/dev/null 2>&1; then
        printf '%s' unzip
        return 0
    fi
    python_bin zipfile
}

jar_entry_text() {
    local jar="$1" entry="$2" reader
    reader="$(jar_reader)" || {
        echo "ОШИБКА: проверить jar нечем: нужен unzip или python3" >&2
        return 3
    }
    case "$reader" in
        unzip) unzip -p "$jar" "$entry" ;;
        *)
            "$reader" - "$jar" "$entry" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    sys.stdout.buffer.write(archive.read(sys.argv[2]))
PY
            ;;
    esac
}

jar_entries() {
    local jar="$1" reader
    reader="$(jar_reader)" || {
        echo "ОШИБКА: проверить jar нечем: нужен unzip или python3" >&2
        return 3
    }
    case "$reader" in
        unzip) unzip -Z1 "$jar" ;;
        *)
            "$reader" - "$jar" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in archive.namelist():
        print(name)
PY
            ;;
    esac
}

verify_mod_jar() {
    local jar="$1" entry="META-INF/neoforge.mods.toml" id text size classes
    if [ ! -f "$jar" ]; then
        echo "ОШИБКА: jar не найден: $jar" >&2
        return 1
    fi
    if ! size="$(jar_entry_text "$jar" "$entry" 2>/dev/null | wc -c)"; then
        echo "ОШИБКА: в $(basename "$jar") нет $entry — сервер не загрузит мод (Missing ModLoader)" >&2
        return 1
    fi
    if [ "${size:-0}" -le 0 ]; then
        echo "ОШИБКА: $entry в $(basename "$jar") пуст (0 байт) — сервер не загрузит мод (Missing ModLoader)." >&2
        echo "Мод не установлен. Лечение: ./gradlew clean assemble и повторить выкладку." >&2
        return 1
    fi
    if ! text="$(jar_entry_text "$jar" "$entry" 2>/dev/null)"; then
        echo "ОШИБКА: не прочитать $entry из $(basename "$jar")" >&2
        return 1
    fi
    id="$(awk -F= '$1 == "mod_id" { gsub(/[[:space:]]/, "", $2); print $2; exit }' "$MOD_DIR/gradle.properties")"
    if [ -z "$id" ]; then
        echo "ОШИБКА: в $MOD_DIR/gradle.properties нет mod_id — не с чем сверить $entry" >&2
        return 1
    fi
    if ! printf '%s' "$text" | grep -qE "^[[:space:]]*modId[[:space:]]*=[[:space:]]*\"?${id}\""; then
        echo "ОШИБКА: $entry в $(basename "$jar") не объявляет modId=$id — сервер не загрузит мод" >&2
        return 1
    fi
    classes="$(jar_entries "$jar" 2>/dev/null | grep -c '\.class$' || true)"
    if [ "${classes:-0}" -le "$MOD_JAR_MIN_CLASSES" ]; then
        echo "ОШИБКА: в $(basename "$jar") классов ${classes:-0}, а нужно больше $MOD_JAR_MIN_CLASSES —" >&2
        echo "jar собран неполным: публикация отменена, сервер не тронут" >&2
        return 1
    fi
    echo "jar проверен: $entry $size байт, modId=$id, классов $classes"
    return 0
}

if [ -n "$verify_only" ]; then
    if verify_mod_jar "$verify_only"; then
        exit 0
    fi
    exit 1
fi

if $check_build; then
    cd "$MOD_DIR"
    echo "Сборка мода для проверки (без выкладки)…"
    ./gradlew --quiet assemble
    mapfile -t jars < <(find "$JAR_DIR" -maxdepth 1 -name 'theminespirit-*.jar' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
    if [ "${#jars[@]}" -eq 0 ]; then
        echo "Сборка не дала theminespirit-*.jar в $MOD_JAR_DIR/" >&2
        exit 1
    fi
    trace_jar "${jars[0]}"
    if ! verify_mod_jar "${jars[0]}"; then
        echo "ОШИБКА: собранный jar негоден для сервера" >&2
        exit 1
    fi
    exit 0
fi

server_jars() {
    find "$SERVER_DIR/mods" -maxdepth 1 -name 'theminespirit-*.jar' -printf '%T@ %p\n' 2>/dev/null \
        | sort -rn | cut -d' ' -f2-
}

# lib: подмена файла через mv: читатель (cron-снимок, запущенный bash, сервер при старте) не должен увидеть
# lib: обрезанный файл: cp -f пишет на месте, и запущенный bash дочитывает уже чужое (10.11: «return: can only
# lib: return from a function»). Тот же приём для конфигов: публикация не оставляет половину файла.
deploy_file() {
    local src="$1" dst="$2" mode="${3:-}" tmp
    tmp="$(dirname "$dst")/.$(basename "$dst").new.$$"
    if ! cp -f "$src" "$tmp"; then
        rm -f "$tmp"
        echo "ОШИБКА: не скопировать $src в $dst" >&2
        return 1
    fi
    if [ -n "$mode" ] && ! chmod "$mode" "$tmp"; then
        rm -f "$tmp"
        echo "ОШИБКА: не выставить права $mode на $dst" >&2
        return 1
    fi
    if ! mv -f "$tmp" "$dst"; then
        rm -f "$tmp"
        echo "ОШИБКА: не подменить $dst" >&2
        return 1
    fi
}

deploy_tree() {
    local src="$1" dst="$2" relative
    while IFS= read -r relative; do
        [ -n "$relative" ] || continue
        mkdir -p "$dst/$(dirname "$relative")"
        deploy_file "$src/$relative" "$dst/$relative" || return 1
    done <<<"$(cd "$src" && find . -type f | sed 's|^\./||' | sort)"
}

deploy_server_files() {
    local files="$SERVER_FILES" name
    if [ ! -d "$files" ]; then
        echo "ОШИБКА: нет $files — скрипты и конфиги сервера не разложить" >&2
        return 1
    fi
    mkdir -p "$SERVER_DIR/scripts" "$SERVER_DIR/config" "$CLEANUP_SCRIPT_DIR"
    for name in common.sh console.sh hang-watch.sh install-neoforge.sh start.sh stop.sh world-snapshot.sh world-restore.sh; do
        if [ ! -f "$files/scripts/$name" ]; then
            echo "ОШИБКА: нет $files/scripts/$name" >&2
            return 1
        fi
        if ! deploy_file "$files/scripts/$name" "$SERVER_DIR/scripts/$name" 755; then
            echo "ОШИБКА: не разложить $name в $SERVER_DIR/scripts" >&2
            return 1
        fi
    done
    chmod 755 "$SERVER_DIR"/scripts/*.sh
    rm -f "$SERVER_DIR/scripts/snapshot.sh"
    if ! deploy_tree "$files/config" "$SERVER_DIR/config"; then
        echo "ОШИБКА: не скопировать конфиги из $files/config в $SERVER_DIR/config" >&2
        return 1
    fi
    for name in server.properties ops.json whitelist.json banned-ips.json banned-players.json eula.txt user_jvm_args.txt; do
        if [ ! -f "$files/$name" ]; then
            echo "ОШИБКА: нет $files/$name" >&2
            return 1
        fi
        if ! deploy_file "$files/$name" "$SERVER_DIR/$name"; then
            echo "ОШИБКА: не скопировать $name в $SERVER_DIR" >&2
            return 1
        fi
    done
    if ! deploy_file "$files/run.sh" "$SERVER_DIR/run.sh" 755 \
        || ! deploy_file "$files/README.md" "$SERVER_DIR/README.md"; then
        echo "ОШИБКА: не скопировать run.sh/README.md в $SERVER_DIR" >&2
        return 1
    fi
    if [ -f "$MOD_DIR/scripts/server-cleanup.sh" ]; then
        if ! deploy_file "$MOD_DIR/scripts/server-cleanup.sh" "$CLEANUP_SCRIPT_DIR/server-cleanup.sh" 755; then
            echo "ОШИБКА: не скопировать server-cleanup.sh в $CLEANUP_SCRIPT_DIR" >&2
            return 1
        fi
    fi
    if [ -f "$MOD_DIR/scripts/nightly-analyzers.sh" ]; then
        if ! deploy_file "$MOD_DIR/scripts/nightly-analyzers.sh" "$CLEANUP_SCRIPT_DIR/nightly-analyzers.sh" 755; then
            echo "ОШИБКА: не скопировать nightly-analyzers.sh в $CLEANUP_SCRIPT_DIR" >&2
            return 1
        fi
    fi
    if ! install_hang_watch_cron; then
        return 1
    fi
    if ! install_nightly_analyzers_cron; then
        return 1
    fi
    echo "Скрипты и конфиги сервера разложены: $files → $SERVER_DIR"
}

install_hang_watch_cron() {
    local script="$SERVER_DIR/scripts/hang-watch.sh"
    local log="${HANG_WATCH_LOG:-/opt/minecraft/logs/hang-watch.log}"
    local line current
    if ! command -v crontab >/dev/null 2>&1; then
        echo "ВНИМАНИЕ: crontab не найден — сторож зависания не поставлен в cron" >&2
        return 0
    fi
    line="* * * * * $script >>$log 2>&1"
    current="$(crontab -l 2>/dev/null || true)"
    if printf '%s\n' "$current" | grep -Fq "$script"; then
        echo "cron сторожа зависания уже стоит: $script"
        return 0
    fi
    mkdir -p "$(dirname "$log")"
    if ! {
        [ -z "$current" ] || printf '%s\n' "$current"
        printf '%s\n' "$line"
    } | crontab -; then
        echo "ОШИБКА: не поставить cron сторожа зависания ($script)" >&2
        return 1
    fi
    echo "cron сторожа зависания поставлен: раз в минуту"
}

install_nightly_analyzers_cron() {
    local script="$CLEANUP_SCRIPT_DIR/nightly-analyzers.sh"
    local line current
    if ! command -v crontab >/dev/null 2>&1; then
        echo "ВНИМАНИЕ: crontab не найден — ночной анализатор не поставлен в cron" >&2
        return 0
    fi
    if [ ! -x "$script" ]; then
        echo "ВНИМАНИЕ: нет $script — ночной анализатор не поставлен в cron" >&2
        return 0
    fi
    # lib: 03:00 — ночью, до уборки (04:25) и утренней работы людей; скрипт сам пишет лог и статус.
    line="0 3 * * * $script >/dev/null 2>&1"
    current="$(crontab -l 2>/dev/null || true)"
    if printf '%s\n' "$current" | grep -Fq "$script"; then
        echo "cron ночного анализатора уже стоит: $script"
        return 0
    fi
    if ! {
        [ -z "$current" ] || printf '%s\n' "$current"
        printf '%s\n' "$line"
    } | crontab -; then
        echo "ОШИБКА: не поставить cron ночного анализатора ($script)" >&2
        return 1
    fi
    echo "cron ночного анализатора поставлен: раз в сутки в 03:00"
}

acquire_build_lock() {
    if ! command -v flock >/dev/null 2>&1; then
        return 0
    fi
    mkdir -p "$(dirname "$BUILD_LOCK")"
    exec 9>"$BUILD_LOCK"
    if ! flock -w "$BUILD_LOCK_WAIT" 9; then
        echo "ОШИБКА: замок сборки $BUILD_LOCK занят дольше $BUILD_LOCK_WAIT с (ночной анализатор?)" >&2
        return 1
    fi
    return 0
}

snapshot_world() {
    local version="$1" script="$SERVER_DIR/scripts/world-snapshot.sh" snapshot_label archive
    snapshot_label="publish-$version"
    if [ ! -x "$script" ]; then
        echo "ОШИБКА: нет $script — снимок мира перед выкладкой не сделать" >&2
        return 1
    fi
    echo "Снимок мира ($snapshot_label)..."
    if ! SNAPSHOT_LOCK_WAIT="${SNAPSHOT_LOCK_WAIT:-900}" "$script" "$snapshot_label"; then
        echo "ОШИБКА: снимок мира не создан, публикация остановлена до остановки сервера" >&2
        return 1
    fi
    archive="$(ls -1t "$WORLD_BACKUPS"/world-*-"$snapshot_label".tar.zst 2>/dev/null | head -n 1)"
    if [ -z "$archive" ]; then
        echo "ОШИБКА: архива с меткой $snapshot_label нет в $WORLD_BACKUPS (снимок пропущен другим процессом?)" >&2
        return 1
    fi
    echo "Снимок мира: $(basename "$archive")"
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
    if ! verify_mod_jar "$jar"; then
        echo "ОШИБКА: установленный $name негоден — пак клиента и страницу не трогаю," >&2
        echo "иначе битый jar разойдётся по клиентам" >&2
        exit 1
    fi
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
    echo "  2. поиск $MOD_JAR_DIR/theminespirit-*.jar (кроме -sources/-javadoc), берётся самый свежий;"
    echo "     проверка содержимого jar (непустой META-INF/neoforge.mods.toml с modId, >1000 классов)"
    echo "  3. раскладка скриптов и конфигов сервера: $SERVER_FILES → $SERVER_DIR (cron сторожа зависания и ночного анализатора)"
    if [ "$mode" = full ]; then
        echo "  4. снимок мира: $SERVER_DIR/scripts/world-snapshot.sh publish-<версия>"
    else
        echo "  4. снимок мира пропускается (--temp)"
    fi
    echo "  5. удаление старых $SERVER_DIR/mods/theminespirit-*.jar"
    echo "  6. копирование нового jar в $SERVER_DIR/mods/"
    if $do_restart; then
        echo "  7. перезапуск сервера, если tmux-сессия активна (stop.sh/start.sh)"
    else
        echo "  7. перезапуск сервера отключён (--no-restart)"
    fi
    echo "  8. пак клиента: $CLIENT_DIR/pack/mods/theminespirit-<версия>.jar и $CLIENT_DIR/publish.sh"
    echo "  9. версия и дата установки в mod.json страницы «Сервер» ($GATEWAY_DIR/$STATUS_REL) и коммит в gateway"
    if [ "$mode" = full ]; then
        echo " 10. тег v<версия> в git и отправка тега в remote backup (если настроен)"
    else
        echo " 10. тег и backup пропускаются (--temp)"
    fi
    echo "Изменения не вносились."
    exit 0
fi

if ! acquire_build_lock; then
    exit 1
fi

cd "$MOD_DIR"
echo "Сборка мода..."
./gradlew --quiet assemble

mapfile -t jars < <(find "$JAR_DIR" -maxdepth 1 -name 'theminespirit-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
if [ "${#jars[@]}" -eq 0 ]; then
    echo "Сборка не дала theminespirit-*.jar в $MOD_JAR_DIR/" >&2
    exit 1
fi
if [ "${#jars[@]}" -gt 1 ]; then
    echo "В $MOD_JAR_DIR несколько jar мода, беру самый свежий:" >&2
    printf '  %s\n' "${jars[@]}" >&2
fi
jar="${jars[0]}"
size="$(stat -c %s "$jar")"
if [ "$size" -lt 4096 ]; then
    echo "jar подозрительно мал ($size байт): $jar" >&2
    exit 1
fi
trace_jar "$jar"
version="$(version_of "$jar")"

if ! verify_mod_jar "$jar"; then
    echo "ОШИБКА: jar не прошёл проверку содержимого, публикация отменена (сервер не тронут)" >&2
    exit 1
fi

if ! deploy_server_files; then
    echo "ОШИБКА: скрипты и конфиги сервера не разложены, публикация отменена" >&2
    exit 1
fi

if [ -f "$SERVER_DIR/scripts/common.sh" ]; then
    source "$SERVER_DIR/scripts/common.sh"
    TMUX_SESSION="${TMUX_SESSION:-minecraft}"
else
    TMUX_SESSION="${TMUX_SESSION:-minecraft}"
    is_running() { tmux has-session -t "$TMUX_SESSION" 2>/dev/null; }
fi

# lib: tmux наследует fd 9 — без закрытия замок сборки держится вечно
exec 9>&-

was_running=false
if is_running; then
    was_running=true
fi

if [ "$mode" = full ]; then
    snapshot_world "$version"
fi

if $do_restart && $was_running; then
    echo "Остановка сервера..."
    "$SERVER_DIR/scripts/stop.sh"
fi

echo "Установка мода в $SERVER_DIR/mods/"
mkdir -p "$SERVER_DIR/mods"
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
