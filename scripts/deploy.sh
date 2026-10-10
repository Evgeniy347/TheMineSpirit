#!/usr/bin/env bash
# Собирает мод и кладёт jar в mods/ сервера. Работающий сервер перезапускается.
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="${SERVER_DIR:-/opt/minecraft/server}"

cd "$MOD_DIR"
./gradlew --quiet build

jar="$(ls build/libs/theminespirit-*.jar | grep -v -- '-sources\|-javadoc')"
was_running=false
if tmux has-session -t minecraft 2>/dev/null; then
    was_running=true
    "$SERVER_DIR/scripts/stop.sh"
fi

rm -f "$SERVER_DIR"/mods/theminespirit-*.jar
cp "$jar" "$SERVER_DIR/mods/"
echo "Установлен $(basename "$jar")"

if $was_running; then
    "$SERVER_DIR/scripts/start.sh"
fi
