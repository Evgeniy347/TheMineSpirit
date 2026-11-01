#!/usr/bin/env bash
# Устанавливает/восстанавливает серверные библиотеки NeoForge (каталог libraries/ не хранится в git).
# Нужен после свежего клона репозитория и при смене версии NeoForge (поменять NEOFORGE_VERSION в common.sh).
set -euo pipefail
source "$(dirname "$0")/common.sh"

is_running && { echo "Сначала останови сервер: scripts/stop.sh" >&2; exit 1; }

installer="neoforge-${NEOFORGE_VERSION}-installer.jar"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

curl -sfL -o "$tmp_dir/$installer" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}/${installer}"
cd "$SERVER_DIR"
java -jar "$tmp_dir/$installer" --install-server . > "$tmp_dir/install.log" 2>&1 \
    || { cat "$tmp_dir/install.log" >&2; exit 1; }
rm -f "${installer}.log" run.bat
echo "NeoForge ${NEOFORGE_VERSION} установлен"
