#!/usr/bin/env bash
# Постройки в текстовом формате слоёв (чеклист 43) вне игры: тот же разбор и сторожа, что в моде.
#   scripts/schematic.sh show <файл|id> [0|90|180|270]   шапка, материалы, срезы по слоям, нарушения
#   scripts/schematic.sh check [файл|id…]                разбор и сторожа всех построек (код 1 при ошибке)
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
