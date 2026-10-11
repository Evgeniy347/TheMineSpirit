#!/usr/bin/env bash
# Один GameTest-сервер на машину (docs/TESTING.md → «Два уровня», docs/research/test-performance.md → рек. 3).
# Без блокировки несколько исполнителей поднимают по JVM игры (~1 ГБ каждая), машина уходит в swap,
# а тесты начинают флакать под нагрузкой. flock ждёт освобождения и запускает ровно один сервер.
set -euo pipefail
cd "$(dirname "$0")/.."

lock="${THEMINESPIRIT_GAMETEST_LOCK:-/tmp/theminespirit-gametest.lock}"

set +e
flock -w 1800 -E 75 "$lock" ./gradlew runGameTestServer "$@"
code=$?
set -e

if [ "$code" -eq 75 ]; then
    echo "gametest: ждали 30 мин, идёт чужой GameTest (блокировка $lock)." >&2
    echo "gametest: GameTest — один на машину; дождись освобождения или освободи блокировку вручную." >&2
    exit 75
fi
exit "$code"
