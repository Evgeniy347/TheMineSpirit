#!/usr/bin/env bash
# Пишет версию и дату установки мода в mod.json страницы «Сервер» сайта gateway.
# Вызывается из publish.sh после установки jar; вручную — чтобы заполнить страницу без пересборки.
#
# Использование: scripts/publish-status.sh <jar> [--pending] [--out <файл>]
set -euo pipefail

GATEWAY_DIR="${GATEWAY_DIR:-/opt/gateway}"
SITE_REL="users/admin/sites/the-mine-spirit/server"
STATUS_REL="$SITE_REL/mod.json"
STATUS_FILE="$GATEWAY_DIR/$STATUS_REL"

usage() {
    cat <<'EOF'
Использование: scripts/publish-status.sh <jar> [--pending] [--out <файл>]

Записывает версию и дату установки jar в mod.json страницы «Сервер» и коммитит файл
в gateway. Версия берётся из имени jar, дата — из времени изменения jar (момент установки).

Опции:
  --pending     сервер ещё не перезапущен: пометить на странице, что версия ждёт перезапуска
  --out <файл>  только записать JSON в файл, gateway не трогать (для проверки)
  -h, --help    эта справка

Переменная GATEWAY_DIR меняет корень gateway (по умолчанию /opt/gateway).
EOF
}

pending=false
out=""
jar=""
while [ $# -gt 0 ]; do
    case "$1" in
        --pending) pending=true ;;
        --out)
            shift
            [ $# -gt 0 ] || { echo "--out требует путь" >&2; exit 2; }
            out="$1"
            ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "Неизвестная опция: $1" >&2; usage >&2; exit 2 ;;
        *)
            [ -z "$jar" ] || { echo "Лишний аргумент: $1" >&2; exit 2; }
            jar="$1"
            ;;
    esac
    shift
done

if [ -z "$jar" ] || [ ! -f "$jar" ]; then
    echo "Нужен существующий jar мода. Подробности: scripts/publish-status.sh --help" >&2
    exit 2
fi

name="${jar##*/}"
version="${name#theminespirit-}"
version="${version%.jar}"
if [ "$version" = "$name" ] || [ -z "$version" ]; then
    echo "Имя jar не вида theminespirit-<версия>.jar: $jar" >&2
    exit 2
fi

sha="$(sha256sum "$jar" | cut -d' ' -f1)"
updated="$(date -Iseconds -r "$jar")"

write_json() {
    printf '{\n  "version": "%s",\n  "updated": "%s",\n  "jar": "%s",\n  "sha256": "%s",\n  "pending": %s\n}\n' \
        "$version" "$updated" "$name" "$sha" "$pending"
}

if [ -n "$out" ]; then
    write_json > "$out"
    echo "Записано: $out"
    exit 0
fi

if [ ! -d "$GATEWAY_DIR/$SITE_REL" ]; then
    echo "Страницы «Сервер» нет: $GATEWAY_DIR/$SITE_REL" >&2
    exit 1
fi

tmp="$(mktemp "$GATEWAY_DIR/$SITE_REL/.mod.json.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
write_json > "$tmp"
chmod 644 "$tmp"
mv -f "$tmp" "$STATUS_FILE"
trap - EXIT

if ! git -C "$GATEWAY_DIR" add -- "$STATUS_REL" >/dev/null 2>&1; then
    echo "ВНИМАНИЕ: $STATUS_FILE записан, но gateway не принял его в git" >&2
    exit 0
fi
if git -C "$GATEWAY_DIR" diff --cached --quiet -- "$STATUS_REL"; then
    echo "Страница «Сервер» уже показывает версию $version"
    exit 0
fi
if git -C "$GATEWAY_DIR" commit -q -m "users/admin/sites/the-mine-spirit: версия $version на сервере" -- "$STATUS_REL"; then
    echo "Страница «Сервер» обновлена: версия $version, $updated"
else
    echo "ВНИМАНИЕ: $STATUS_FILE записан, но не закоммичен в gateway" >&2
fi
