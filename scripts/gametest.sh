#!/usr/bin/env bash
# Тонкая обёртка над scripts/test.sh gametest для совместимости (docs/TESTING.md → «Два уровня»).
# Замок «один сервер на машину», пределы времени и формат вывода — в scripts/test.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash scripts/test.sh gametest "$@"
