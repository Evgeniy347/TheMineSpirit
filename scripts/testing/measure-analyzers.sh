#!/usr/bin/env bash
# lib: Замер цены анализаторов на типичной правке: пробельная строка в классе :mod и в классе другого модуля,
# затем полный `scripts/test.sh unit` (как в финале исполнителя) и `./gradlew check --profile`. Время групп
# (компиляция, тесты, Spotless, Checkstyle, PMD, SpotBugs) берётся из профиля Gradle по задачам и модулям.
# Вариант конфигурации (B–E) — временный init-скрипт этого прогона; дерево и build-logic не меняются.
# Кэш сборки на замере выключен: иначе повторная пробельная правка даёт FROM-CACHE и нулевое время.
#
# Анализаторы в main выключены по умолчанию; скрипт включает их флагом -Ptheminespirit.analyzers=on.
#
# Использование:
#   scripts/testing/measure-analyzers.sh [--variant A|B|C|D|E|none] [--omit "Detector1,Detector2"]
#                                        [--label ИМЯ] [--samples N] [--out КАТАЛОГ] [--analyzers-only]
#   --analyzers-only  без полного unit: только задачи анализаторов :mod и :gametest (быстрый замер под нагрузкой)
#
# Варианты:
#   A/none  как сейчас
#   B       SpotBugs effort=MIN на :mod и :gametest
#   C       SpotBugs выключен на :mod и :gametest
#   D       PMD выключен на :mod
#   E       B + D
#   --omit  SpotBugs omitVisitors на :mod и :gametest (вариант F и проверка групп детекторов)
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2
ROOT="$(pwd -P)"

VARIANT="none"
LABEL=""
SAMPLES=2
OUT="${THEMINESPIRIT_ANALYZERS_OUT:-TestResults/analyzers}"
OMIT=""
ANALYZERS_ONLY=0
INIT_SCRIPT=""
EDIT_TARGETS=(
    "mod/src/main/java/com/theminespirit/neoforge/BuildSquadRegistry.java|BuildSquad squad = squads.computeIfAbsent(key, ignored -> new BuildSquad());"
    "gametest/src/main/java/com/theminespirit/gametest/BoardGameTests.java|private static final int GATE_LOGS = 1;"
)

usage() {
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --variant) VARIANT="${2:-}"; shift 2 ;;
        --omit) OMIT="${2:-}"; shift 2 ;;
        --label) LABEL="${2:-}"; shift 2 ;;
        --samples) SAMPLES="${2:-}"; shift 2 ;;
        --out) OUT="${2:-}"; shift 2 ;;
        --analyzers-only) ANALYZERS_ONLY=1; shift ;;
        -h|--help) usage ;;
        *) echo "Неизвестный аргумент: $1" >&2; usage ;;
    esac
done
[ -n "$LABEL" ] || LABEL="$VARIANT"
case "$SAMPLES" in ''|*[!0-9]*) echo "--samples требует число" >&2; exit 2 ;; esac
case "$VARIANT" in A|B|C|D|E|none) ;; *) echo "Вариант: A, B, C, D, E или none" >&2; exit 2 ;; esac

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd -P)"
PROFILE_WRAPPER="$OUT/gradlew-profile"
MEASURE_TSV="$OUT/measurements.tsv"
mkdir -p TestResults/logs

newline_of() {
    if head -1 "$1" | grep -q $'\r'; then printf '\r\n'; else printf '\n'; fi
}

python_bin() {
    local cand path
    for cand in "${THEMINESPIRIT_PYTHON:-}" "${PYTHON:-}" python python3; do
        [ -n "$cand" ] || continue
        [ "$cand" = none ] && return 1
        case "$cand" in
            */*) [ -x "$cand" ] && { printf '%s' "$cand"; return 0; } ;;
            *)
                path="$(command -v "$cand" 2>/dev/null)" || continue
                case "$path" in */WindowsApps/*) continue ;; esac
                printf '%s' "$path"
                return 0
                ;;
        esac
    done
    return 1
}
PY="$(python_bin)" || { echo "measure-analyzers: нужен python" >&2; exit 2; }

edit_whitespace() {
    local mode="$1"
    "$PY" - "$mode" "${EDIT_TARGETS[@]}" <<'PY'
import sys
from pathlib import Path
mode = sys.argv[1]
for spec in sys.argv[2:]:
    path, marker = spec.split('|', 1)
    p = Path(path)
    lines = p.read_text(encoding='utf-8').splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if line.strip() == marker]
    if len(hits) != 1:
        raise SystemExit(f'{path}: ожидалась одна строка-маркер, найдено {len(hits)}')
    i = hits[0]
    has_blank = i + 1 < len(lines) and lines[i + 1].strip() == ''
    if mode == 'toggle':
        if has_blank:
            del lines[i + 1]
        else:
            newline = '\r\n' if lines[0].endswith('\r\n') else '\n'
            lines.insert(i + 1, newline)
    elif mode == 'remove':
        if has_blank:
            del lines[i + 1]
    else:
        raise SystemExit(f'неизвестный режим правки: {mode}')
    p.write_text(''.join(lines), encoding='utf-8', newline='')
PY
}

cleanup() {
    edit_whitespace remove >/dev/null 2>&1 || true
    rm -f "$INIT_SCRIPT"
}
trap cleanup EXIT INT TERM

# lib: вариант — init script, а не правка build-logic: иначе пересборка build-logic делает все compileJava
# неактуальными, и в замер попадает холодная сборка вместо правки кода.
apply_variant() {
    local block="" init
    case "$VARIANT" in
        none|A) return 0 ;;
        B) block='        if (name in ["mod", "gametest"]) { effort("MIN") }' ;;
        C) block='        if (name in ["mod", "gametest"]) { disable("spotbugs") }' ;;
        D) block='        if (name == "mod") { disable("pmd") }' ;;
        E) block='        if (name in ["mod", "gametest"]) { effort("MIN") }
        if (name == "mod") { disable("pmd") }' ;;
    esac
    if [ -n "$OMIT" ]; then
        local list
        list="$(printf '"%s", ' ${OMIT//,/ } | sed 's/, $//')"
        block="        if (name in [\"mod\", \"gametest\"]) { omit([$list]) }"
    fi
    [ -n "$block" ] || return 0
    INIT_SCRIPT="$OUT/variant-$LABEL.init.gradle"
    cat >"$INIT_SCRIPT" <<EOF
// lib: measure-analyzers TEMP (не коммитить) — временный вариант конфигурации анализаторов.
gradle.allprojects { p ->
    def name = p.name
    def disable = { prefix ->
        p.tasks.matching { it.name.startsWith(prefix) }.configureEach { enabled = false }
    }
    def effort = { level ->
        p.tasks.matching { it.name.startsWith("spotbugs") }.configureEach { t ->
            if (t.hasProperty("effort")) {
                def type = t.class.classLoader.loadClass("com.github.spotbugs.snom.Effort")
                t.effort.set(type.enumConstants.find { it.name() == level })
            }
        }
    }
    def omit = { visitors ->
        p.tasks.matching { it.name.startsWith("spotbugs") }.configureEach { t ->
            if (t.hasProperty("omitVisitors")) { t.omitVisitors.set(visitors) }
        }
    }
    p.afterEvaluate {
$block
    }
}
EOF
}

java_count() {
    ps -W 2>/dev/null | awk 'tolower($0) ~ /java/ { n++ } END { print n + 0 }'
}

write_wrapper() {
    cat >"$PROFILE_WRAPPER" <<EOF
#!/usr/bin/env bash
if [ -n "$INIT_SCRIPT" ]; then
    exec "$ROOT/gradlew" --profile --no-build-cache -Ptheminespirit.analyzers=on -I "$INIT_SCRIPT" "\$@"
fi
exec "$ROOT/gradlew" --profile --no-build-cache -Ptheminespirit.analyzers=on "\$@"
EOF
    chmod +x "$PROFILE_WRAPPER"
}

latest_profile() {
    ls -t build/reports/profile/profile-*.html 2>/dev/null | head -1
}

# lib: профиль Gradle — HTML-таблица задач; группируем по имени задачи и модулю.
parse_profile() {
    "$PY" - "$1" <<'PY'
import os, re, sys
path = sys.argv[1]
text = open(path, encoding='utf-8', errors='replace').read()
rows = re.findall(r'<td class="indentPath">([^<]+)</td>\s*<td class="numeric">([^<]+)</td>', text)
def seconds(value):
    match = re.match(r'(?:(\d+)m)?([\d.]+)s$', value.strip())
    return int(match.group(1) or 0) * 60 + float(match.group(2)) if match else 0.0
groups = {'compile': 0.0, 'test': 0.0, 'spotless': 0.0, 'checkstyle': 0.0, 'pmd': 0.0, 'spotbugs': 0.0}
per_module = {}
for name, duration in rows:
    task = name.rsplit(':', 1)[-1]
    if task in ('compileJava', 'compileTestJava', 'compileTestFixturesJava'):
        group = 'compile'
    elif task in ('test', 'slowTest'):
        group = 'test'
    elif task.startswith('spotless'):
        group = 'spotless'
    elif task.startswith('checkstyle'):
        group = 'checkstyle'
    elif task.startswith('pmd'):
        group = 'pmd'
    elif task.startswith('spotbugs'):
        group = 'spotbugs'
    else:
        continue
    value = seconds(duration)
    groups[group] += value
    module = name.rsplit(':', 1)[0] or ':root'
    per_module.setdefault(module, {}).setdefault(group, 0.0)
    per_module[module][group] += value
analyzers = sum(groups[key] for key in ('spotless', 'checkstyle', 'pmd', 'spotbugs'))
base = groups['compile'] + groups['test'] + analyzers
print('TOTAL\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f' % (
    groups['compile'], groups['test'], groups['spotless'], groups['checkstyle'], groups['pmd'],
    groups['spotbugs'], analyzers, base, (100.0 * analyzers / base if base else 0.0)))
for module in sorted(per_module):
    row = per_module[module]
    print('MODULE\t%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f' % (
        module, row.get('compile', 0.0), row.get('test', 0.0), row.get('spotless', 0.0),
        row.get('checkstyle', 0.0), row.get('pmd', 0.0), row.get('spotbugs', 0.0)))
PY
}

run_measured() {
    local command="$1" sample="$2" start end wall profile summary total java
    local extra=()
    [ -n "$INIT_SCRIPT" ] && extra=(-I "$INIT_SCRIPT")
    java="$(java_count)"
    start=$(date +%s)
    if [ "$command" = "unit" ]; then
        THEMINESPIRIT_GRADLEW="$PROFILE_WRAPPER" bash scripts/test.sh unit >"$OUT/$LABEL-s$sample-unit.log" 2>&1
    elif [ "$command" = "analyzers" ]; then
        ./gradlew :mod:spotbugsMain :gametest:spotbugsMain :mod:pmdMain :gametest:pmdMain \
            :mod:checkstyleMain :gametest:checkstyleMain :mod:spotlessCheck :gametest:spotlessCheck \
            --profile --no-build-cache -Ptheminespirit.analyzers=on \
            "${extra[@]}" --console=plain >"$OUT/$LABEL-s$sample-analyzers.log" 2>&1
    else
        ./gradlew check --profile --no-build-cache -Ptheminespirit.analyzers=on \
            "${extra[@]}" --console=plain >"$OUT/$LABEL-s$sample-check.log" 2>&1
    fi
    local code=$?
    end=$(date +%s)
    wall=$((end - start))
    profile="$(latest_profile)"
    if [ -z "$profile" ]; then
        echo "measure-analyzers: нет профиля после $command (код $code)" >&2
        return 1
    fi
    cp "$profile" "$OUT/$LABEL-s$sample-$command-profile.html"
    summary="$(parse_profile "$profile")"
    total="$(printf '%s\n' "$summary" | awk -F'\t' '$1=="TOTAL" {print}')"
    if [ -z "$total" ]; then
        echo "measure-analyzers: профиль без задач ($profile)" >&2
        return 1
    fi
    printf '%s\n' "$summary" >"$OUT/$LABEL-s$sample-$command-summary.tsv"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$LABEL" "$VARIANT" "$sample" "$command" "$wall" "$java" \
        "$(printf '%s' "$total" | cut -f2)" "$(printf '%s' "$total" | cut -f3)" \
        "$(printf '%s' "$total" | cut -f4)" "$(printf '%s' "$total" | cut -f5)" \
        "$(printf '%s' "$total" | cut -f6)" "$(printf '%s' "$total" | cut -f7)" \
        "$(printf '%s' "$total" | cut -f8)" "$(printf '%s' "$total" | cut -f9)" \
        "$(printf '%s' "$total" | cut -f10)" >>"$MEASURE_TSV"
    echo "== $LABEL s$sample $command: код $code, стена ${wall}с, java-процессов $java"
    printf '%s\n' "$summary" | awk -F'\t' '$1=="TOTAL" {
        printf "   компиляция %.1fс, тесты %.1fс, Spotless %.1fс, Checkstyle %.1fс, PMD %.1fс, SpotBugs %.1fс; анализаторы %.1f%%\n",
        $2, $3, $4, $5, $6, $7, $10 }'
    printf '%s\n' "$summary" | awk -F'\t' '$1=="MODULE" && ($3+$4+$5+$6+$7+$8)>0 {
        printf "   %-22s compile %6.1f  test %6.1f  spotless %5.1f  checkstyle %6.1f  pmd %6.1f  spotbugs %6.1f\n", $2,$3,$4,$5,$6,$7,$8 }'
    return "$code"
}

if [ ! -f "$MEASURE_TSV" ]; then
    printf 'label\tvariant\tsample\tcommand\twall_s\tjava\tcompile\ttest\tspotless\tcheckstyle\tpmd\tspotbugs\tanalyzers\tbase\tshare_pct\n' >"$MEASURE_TSV"
fi

apply_variant
write_wrapper
[ -n "$INIT_SCRIPT" ] && cp "$INIT_SCRIPT" "$OUT/$LABEL-variant.init.gradle"
printf 'measure-analyzers: вариант %s, метка %s, образцов %s, java сейчас %s\n' \
    "$VARIANT" "$LABEL" "$SAMPLES" "$(java_count)"
printf 'measure-analyzers: файл данных %s\n' "$MEASURE_TSV"

# lib: прогрев поднимает демон и configuration cache; его время не относится к правке кода.
warmup=()
[ -n "$INIT_SCRIPT" ] && warmup=(-I "$INIT_SCRIPT")
./gradlew help --no-build-cache -Ptheminespirit.analyzers=on "${warmup[@]}" --console=plain >"$OUT/$LABEL-warmup.log" 2>&1 \
    || { echo "measure-analyzers: прогрев не прошёл, см. $OUT/$LABEL-warmup.log" >&2; exit 1; }

failed=0
for sample in $(seq 1 "$SAMPLES"); do
    if [ "$ANALYZERS_ONLY" = 1 ]; then
        edit_whitespace toggle
        run_measured analyzers "$sample" || failed=1
    else
        edit_whitespace toggle
        run_measured unit "$sample" || failed=1
        edit_whitespace toggle
        run_measured check "$sample" || failed=1
    fi
done

echo "measure-analyzers: итог в $MEASURE_TSV"
exit "$failed"
