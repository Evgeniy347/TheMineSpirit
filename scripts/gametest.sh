#!/usr/bin/env bash
# Один GameTest-сервер на машину (docs/TESTING.md → «Два уровня», docs/research/test-performance.md → рек. 3).
# Без замка несколько исполнителей поднимают по JVM игры (~1 ГБ каждая), машина уходит в swap,
# а тесты начинают флакать под нагрузкой. Замок ждёт освобождения и запускает ровно один сервер.
# На Linux это flock; в Git Bash на Windows flock нет — запасной замок на mkdir (создание каталога атомарно).
set -euo pipefail
cd "$(dirname "$0")/.."

lock="${THEMINESPIRIT_GAMETEST_LOCK:-/tmp/theminespirit-gametest.lock}"
gradlew="${THEMINESPIRIT_GRADLEW:-./gradlew}"
wait_seconds="${THEMINESPIRIT_GAMETEST_WAIT:-1800}"
lock_dir="$lock.d"

noflock() {
    [ -n "${THEMINESPIRIT_GAMETEST_NOFLOCK:-}" ] && return 0
    command -v flock >/dev/null 2>&1 && return 1
    return 0
}

release_dir_lock() {
    rm -rf "$lock_dir"
}

acquire_dir_lock() {
    local waited=0 holder stale
    while true; do
        if mkdir "$lock_dir" 2>/dev/null; then
            echo $$ >"$lock_dir/pid"
            trap release_dir_lock EXIT INT TERM
            return 0
        fi
        holder=$(cat "$lock_dir/pid" 2>/dev/null || echo "")
        if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
            # Снятие протухшего замка атомарным переименованием: ровно один из ожидающих
            # успевает mv (и печатает сообщение), проигравший mv не удался — ждёт дальше.
            # Прежний rm -rf по очереди у обоих ожидающих мог снести замок, который первый
            # только что снова взял себе.
            stale="$lock_dir.stale.$$"
            if mv "$lock_dir" "$stale" 2>/dev/null; then
                echo "gametest: снимаю протухший замок $lock_dir (процесс $holder мёртв)." >&2
                rm -rf "$stale"
                continue
            fi
        fi
        [ "$waited" -ge "$wait_seconds" ] && return 75
        sleep 2
        waited=$((waited + 2))
    done
}

busy_hint() {
    echo "gametest: ждали ${wait_seconds} с, идёт чужой GameTest (блокировка $1)." >&2
    echo "gametest: GameTest — один на машину; дождись освобождения или освободи блокировку вручную." >&2
}

if noflock; then
    code=0
    acquire_dir_lock || code=$?
    if [ "$code" -eq 75 ]; then
        busy_hint "$lock_dir"
        exit 75
    fi
    set +e
    "$gradlew" runGameTestServer "$@"
    code=$?
    set -e
    exit "$code"
fi

set +e
flock -w "$wait_seconds" -E 75 "$lock" "$gradlew" runGameTestServer "$@"
code=$?
set -e

if [ "$code" -eq 75 ]; then
    busy_hint "$lock"
    exit 75
fi
exit "$code"
