#!/usr/bin/env bash
# lib: Gradle кэширует анализаторы только при стабильных входах; живая проверка — второй прогон и один набор классов :gametest.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
log_dir="$root/TestResults/logs"
mkdir -p "$log_dir"
first_log="$log_dir/static-analysis-cache.log"
second_log="$log_dir/static-analysis-cache-second.log"
tasks=(:mod:spotbugsMain :mod:pmdMain :gametest:spotbugsMain :gametest:pmdMain)
failed=0

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

if ! ./gradlew "${tasks[@]}" --console=plain >"$first_log" 2>&1; then
    echo "FAIL первичный прогон анализаторов (лог: $first_log)"
    tail -20 "$first_log"
    exit 1
fi
if ! ./gradlew "${tasks[@]}" --info --console=plain >"$second_log" 2>&1; then
    echo "FAIL повторный прогон анализаторов (лог: $second_log)"
    tail -20 "$second_log"
    exit 1
fi

for task in "${tasks[@]}"; do
    if grep -qF "> Task $task UP-TO-DATE" "$second_log" || grep -qF "> Task $task FROM-CACHE" "$second_log"; then
        ok "$task не пересчитывался (UP-TO-DATE/FROM-CACHE)"
    else
        fail "$task выполнился повторно на неизменном коде — сломан кэш/инкрементальность"
    fi
done

analyse="$root/gametest/build/spotbugsMain-analyse-class-file.txt"
if [ -f "$analyse" ]; then
    count=$(wc -l <"$analyse")
    if [ "$count" -le 200 ]; then
        ok "spotbugsMain :gametest анализирует $count своих классов (классы :mod не дублируются)"
    else
        fail "spotbugsMain :gametest анализирует $count классов — снова втянуты классы :mod"
    fi
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "OK анализаторы кэшируются, дублирующего анализа нет"
