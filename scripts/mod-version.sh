#!/usr/bin/env bash
# lib: имя jar в mods/ доказывает установку, а не загрузку: 10.11 сервер крутил 0.1.3454, пока в mods/ уже
# lib: лежала новая сборка. Загруженную версию пишет сам NeoForge при старте в debug.log, её и читаем.
# lib: разбор — один, в mod_version.py: обёртка не держит bash в живых путях (на ПК `bash` бывает WSL,
# lib: и пути Windows ему чужие), а живой скрипт scripts/lead/live.py читает ту же функцию на python.
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$dir/python-bin.sh"

python="$(python_bin)" || {
    echo "нет рабочего python 3: версию мода не прочитать" >&2
    exit 1
}
exec "$python" "$dir/mod_version.py" "$@"
