#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ $# -eq 0 ] || [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    sed -n '2,4p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
fi
joined=""
for arg in "$@"; do
    joined="${joined}${arg}"$'\001'
done
exec ./gradlew -q schematicTool "-Dtheminespirit.schematic.args=${joined}"
