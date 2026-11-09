#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2
REPO_DIR="$PWD"
. scripts/module-paths.sh
TEST_DIR="$MOD_TEST_DIR"
TEST_DIRS=("$MOD_TEST_DIR")
for module_test_root in ${MOD_MODULE_TEST_ROOTS//,/ }; do
    TEST_DIRS+=("$module_test_root")
done
REPORT_DIRS=("TestResults/unit/xml")
for module_test_root in ${MOD_MODULE_TEST_ROOTS//,/ }; do
    module_base="${module_test_root%%/src/*}"
    REPORT_DIRS+=("TestResults/$(basename "$module_base")/xml")
done
GAMETEST_DIR="$MOD_GAME_TESTS_DIR"
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

changed_tests="$(git diff --name-only --diff-filter=AM "$parent" "$fix" -- "${TEST_DIRS[@]}")"
gametest_changed="$(git diff --name-only --diff-filter=AM "$parent" "$fix" -- "$GAMETEST_DIR")"
if [ -n "$gametest_changed" ]; then
    echo "GameTest не проверяется скриптом, доказывай подлогом вручную: $(echo "$gametest_changed" | xargs -n1 basename | tr '\n' ' ')"
fi
if [ -z "$changed_tests" ]; then
    echo "Коммит $short не добавил и не изменил JUnit-тестов в ${TEST_DIRS[*]} — доказывать нечего."
    exit "$EXIT_NOTHING"
fi

classes=()
filters=()
patterns=()
targets=()
declare -A declared=()
while IFS= read -r file; do
    git show "$fix:$file" | grep -qE '@(Test|Example|Property|ParameterizedTest|RepeatedTest|TestFactory)\b' || continue
    fqn=""
    target=""
    for test_dir in "${TEST_DIRS[@]}"; do
        case "$file" in
            "$test_dir"/*)
                fqn="${file#"$test_dir"/}"
                if [ "$test_dir" = "$MOD_TEST_DIR" ]; then
                    target=":test"
                else
                    module="${test_dir%/src/test/java}"
                    target=":${module//\//:}:test"
                fi
                break
                ;;
        esac
    done
    [ -n "$fqn" ] || continue
    if [ -z "${declared[$target]:-}" ]; then
        declared[$target]=1
        targets+=("$target")
    fi
    fqn="${fqn%.java}"
    fqn="${fqn//\//.}"
    classes+=("$fqn")
    filters+=("$fqn" "$fqn\$*")
    patterns+=("$fqn" "$fqn\$*")
done <<<"$changed_tests"
if [ "${#classes[@]}" -eq 0 ]; then
    echo "В изменённых файлах нет тестовых методов (@Test/@Example/@Property) — доказывать нечего."
    exit "$EXIT_NOTHING"
fi
if [ -n "$user_filter" ]; then
    filters=("$user_filter")
    patterns=("$user_filter")
fi
filter_args=()
for f in "${filters[@]}"; do filter_args+=(--tests "$f"); done
# lib: --tests действует на все задачи test, поэтому не запускаем чужие модули без этих тестов;
# их задачи падали бы с «No tests found for given includes» (как в многомодульной сборке).
target_args=("${targets[@]}")
module_filter="$(IFS=,; printf '%s' "${patterns[*]}")"

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

run_gradle() {
    local log="$1" code
    local report_dir
    for report_dir in "${REPORT_DIRS[@]}"; do
        rm -rf "$work/$report_dir"
    done
    (cd "$work" && ./gradlew "${target_args[@]}" --rerun --console=plain "${filter_args[@]}" \
        -Ptheminespirit.filtered "-Ptheminespirit.tests=$module_filter") >"$log" 2>&1
    code=$?
    [ "$code" -eq 0 ] && return 0
    if grep -qE "Execution failed for task ':.*compileTestJava'" "$log"; then
        return 11
    fi
    for report_dir in "${REPORT_DIRS[@]}"; do
        if ls "$work/$report_dir"/TEST-*.xml >/dev/null 2>&1; then
            return 10
        fi
    done
    return 12
}

pick_python() {
    local cand
    for cand in python3 python python3.exe python.exe; do
        if command -v "$cand" >/dev/null 2>&1 \
                && "$cand" -c "import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)" >/dev/null 2>&1; then
            echo "$cand"
            return 0
        fi
    done
    return 1
}
PYTHON="$(pick_python)" || {
    echo "Не найден рабочий python 3.8+ (нужен для разбора XML-отчётов test)" >&2
    exit 2
}

class_statuses() {
    "$PYTHON" -I - "$work" "$@" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
root, wanted = sys.argv[1], sys.argv[2:]
stats = {}
for xml_dir in [os.path.join(root, "TestResults", "unit", "xml")] + [
    os.path.join(root, "TestResults", name, "xml") for name in os.listdir(os.path.join(root, "TestResults"))
    if os.path.isdir(os.path.join(root, "TestResults", name, "xml"))
]:
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
