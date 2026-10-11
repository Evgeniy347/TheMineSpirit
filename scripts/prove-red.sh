#!/usr/bin/env bash
# Механическое доказательство правила «тест сначала красный» (docs/TESTING.md → «Тест, который может упасть»).
# Берёт тестовые файлы fix-коммита (src/test/java), кладёт их на РОДИТЕЛЯ коммита и ждёт красный прогон, затем
# прогоняет на самом коммите и ждёт зелёный. Временный worktree — TestResults/prove-red/<хэш>/, в конце удаляется;
# основное дерево и ветки не меняются.
#
# Использование:
#   scripts/prove-red.sh <fix-коммит> [фильтр-тестов]
# Фильтр — формат `gradlew test --tests` (например, '*FallbackPlannerTests'); по умолчанию — классы из изменённых файлов.
#
# Итог — строка на тест-класс: OK красный→зелёный | FAIL зелёный уже до исправления | FAIL красный после исправления.
# Тест, который на родителе не компилируется (новый API), тоже «красный», но доказательство слабее: печатается отдельно.
# Коды выхода: 0 — все классы OK; 1 — есть FAIL; 2 — ошибка запуска/сборки, вердикта нет; 3 — JUnit-тестов в коммите нет.
# Подробные логи gradle — TestResults/prove-red/<хэш>-parent.log и <хэш>-fix.log. PROVE_RED_KEEP=1 — не удалять worktree.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2
REPO_DIR="$PWD"
TEST_DIR="src/test/java"
GAMETEST_DIR="src/main/java/com/theminespirit/gametest"
EXIT_ERROR=2
EXIT_NOTHING=3

usage() {
    sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

[ $# -ge 1 ] && [ $# -le 2 ] || usage
fix="$(git rev-parse --verify --quiet "$1^{commit}")" || { echo "Нет такого коммита: $1" >&2; exit 2; }
parent="$(git rev-parse --verify --quiet "$fix^")" || { echo "У коммита $1 нет родителя — сравнивать не с чем" >&2; exit 2; }
user_filter="${2:-}"
short="$(git rev-parse --short "$fix")"
out_root="$REPO_DIR/TestResults/prove-red"
work="$out_root/$short"
mkdir -p "$out_root"

# --- что изменил коммит ---
changed_tests="$(git diff --name-only --diff-filter=AM "$parent" "$fix" -- "$TEST_DIR")"
gametest_changed="$(git diff --name-only --diff-filter=AM "$parent" "$fix" -- "$GAMETEST_DIR")"
if [ -n "$gametest_changed" ]; then
    echo "GameTest не проверяется скриптом, доказывай подлогом вручную: $(echo "$gametest_changed" | xargs -n1 basename | tr '\n' ' ')"
fi
if [ -z "$changed_tests" ]; then
    echo "Коммит $short не добавил и не изменил JUnit-тестов в $TEST_DIR — доказывать нечего."
    exit "$EXIT_NOTHING"
fi

# Тест-классы: файлы с тестовыми аннотациями (остальные — вспомогательные, их копируем, но не оцениваем).
classes=()
filters=()
while IFS= read -r file; do
    git show "$fix:$file" | grep -qE '@(Test|Example|Property|ParameterizedTest|RepeatedTest|TestFactory)\b' || continue
    fqn="${file#"$TEST_DIR"/}"
    fqn="${fqn%.java}"
    fqn="${fqn//\//.}"
    classes+=("$fqn")
    filters+=("$fqn" "$fqn\$*")
done <<<"$changed_tests"
if [ "${#classes[@]}" -eq 0 ]; then
    echo "В изменённых файлах нет тестовых методов (@Test/@Example/@Property) — доказывать нечего."
    exit "$EXIT_NOTHING"
fi
if [ -n "$user_filter" ]; then
    filters=("$user_filter")
fi
filter_args=()
for f in "${filters[@]}"; do filter_args+=(--tests "$f"); done

# --- временный worktree ---
cleanup() {
    if [ -z "${PROVE_RED_KEEP:-}" ]; then
        git -C "$REPO_DIR" worktree remove --force "$work" 2>/dev/null
        rm -rf "$work"
        git -C "$REPO_DIR" worktree prune
    fi
}
trap cleanup EXIT
rm -rf "$work"
git worktree prune
git worktree add --detach --quiet "$work" "$parent" || { echo "Не удалось создать worktree $work" >&2; exit 2; }

# Прогон gradle в worktree. Код: 0 — зелёный; 10 — красный (тесты упали); 11 — красный (тесты не компилируются);
# 12 — сбой, вердикта нет. XML-отчёты остаются в $work/TestResults/unit/xml.
run_gradle() {
    local log="$1" code
    rm -rf "$work/TestResults/unit"
    (cd "$work" && ./gradlew test --rerun --console=plain "${filter_args[@]}") >"$log" 2>&1
    code=$?
    [ "$code" -eq 0 ] && return 0
    if grep -q "Execution failed for task ':compileTestJava'" "$log"; then
        return 11
    fi
    if ls "$work"/TestResults/unit/xml/TEST-*.xml >/dev/null 2>&1; then
        return 10
    fi
    return 12
}

# Статус каждого класса по XML: red | green | none. Печатает «класс статус».
class_statuses() {
    python3 -I - "$work/TestResults/unit/xml" "$@" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
xml_dir, wanted = sys.argv[1], sys.argv[2:]
stats = {}
for path in glob.glob(xml_dir + "/TEST-*.xml"):
    suite = ET.parse(path).getroot()
    top = suite.get("name", "").split("$")[0]
    ran = int(suite.get("tests", 0)) - int(suite.get("skipped", 0))
    bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    prev = stats.get(top, (0, 0))
    stats[top] = (prev[0] + ran, prev[1] + bad)
names = list(wanted) + sorted(n for n in stats if n not in wanted)
for name in names:
    ran, bad = stats.get(name, (0, 0))
    print(name, "red" if bad else ("green" if ran else "none"))
PY
}

# --- 1. родитель + тесты из fix ---
while IFS= read -r file; do
    mkdir -p "$work/$(dirname "$file")"
    git show "$fix:$file" >"$work/$file"
done <<<"$changed_tests"

echo "prove-red: коммит $short, родитель $(git rev-parse --short "$parent"), тест-классов: ${#classes[@]}"
started=$(date +%s)
echo "prove-red: [1/2] родитель + тесты коммита, ожидаю КРАСНЫЙ…"
run_gradle "$out_root/$short-parent.log"
parent_code=$?
parent_took=$(( $(date +%s) - started ))
if [ "$parent_code" -eq 12 ]; then
    echo "ОШИБКА: на родителе сборка упала не из-за тестов, вердикта нет. Хвост лога $out_root/$short-parent.log:" >&2
    tail -n 15 "$out_root/$short-parent.log" >&2
    exit "$EXIT_ERROR"
fi
declare -A parent_status
if [ "$parent_code" -eq 11 ]; then
    echo "prove-red: на родителе тесты не компилируются (${parent_took} с)"
    for c in "${classes[@]}"; do parent_status[$c]=compile; done
else
    while read -r name status; do parent_status[$name]=$status; done < <(class_statuses "${classes[@]}")
    echo "prove-red: родитель отработал за ${parent_took} с"
fi

# --- 2. сам fix ---
started=$(date +%s)
echo "prove-red: [2/2] коммит $short, ожидаю ЗЕЛЁНЫЙ…"
git -C "$work" checkout --quiet --force --detach "$fix" || { echo "Не удалось перейти на $short в worktree" >&2; exit 2; }
run_gradle "$out_root/$short-fix.log"
fix_code=$?
fix_took=$(( $(date +%s) - started ))
if [ "$fix_code" -eq 12 ] || [ "$fix_code" -eq 11 ]; then
    echo "ОШИБКА: на коммите $short сборка упала до запуска тестов, вердикта нет. Хвост лога $out_root/$short-fix.log:" >&2
    tail -n 15 "$out_root/$short-fix.log" >&2
    exit "$EXIT_ERROR"
fi
declare -A fix_status
all_classes=("${classes[@]}")
while read -r name status; do
    fix_status[$name]=$status
    [ -n "${parent_status[$name]:-}" ] || { parent_status[$name]=none; all_classes+=("$name"); }
done < <(class_statuses "${classes[@]}")
echo "prove-red: коммит отработал за ${fix_took} с"

# --- итог ---
echo "--- итог $short ---"
bad=0
for c in "${all_classes[@]}"; do
    p="${parent_status[$c]:-none}"
    f="${fix_status[$c]:-none}"
    if [ "$f" = red ]; then
        echo "FAIL красный после исправления: $c"
        bad=1
    elif [ "$f" = none ]; then
        echo "FAIL тесты класса не запустились на коммите (проверь фильтр): $c"
        bad=1
    elif [ "$p" = green ]; then
        echo "FAIL зелёный уже до исправления (тест не доказывает исправление): $c"
        bad=1
    elif [ "$p" = none ]; then
        echo "FAIL тесты класса не запустились на родителе (проверь фильтр): $c"
        bad=1
    elif [ "$p" = compile ]; then
        echo "OK красный→зелёный: $c (не компилируется на родителе — новый API; доказательство слабее, нужен тест на поведение)"
    else
        echo "OK красный→зелёный: $c"
    fi
done
exit "$bad"
