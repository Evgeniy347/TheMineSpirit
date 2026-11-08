#!/usr/bin/env bash
# lib: Ночной полный прогон анализаторов и тестов по main (решение владельца 07.11).
#
# Раз в сутки cron запускает скрипт на сервере: git main → чистый отдельный checkout (рабочие деревья сервера
# не трогаются) → ./gradlew check -Ptheminespirit.analyzers=on (Spotless, Checkstyle, PMD, SpotBugs и все
# unit-тесты) с офлайн-кэшем Gradle, nice 19 и ionice -c 3. Если main не менялся с прошлой проверки (sha в
# STATE-файле), скрипт выходит сразу и ничего не собирает. Публикаций и перезапусков сервера здесь нет.
#
# Использование:
#   scripts/nightly-analyzers.sh [--dry-run] [--force] [--help]
#
# Переменные:
#   NIGHTLY_REPO      bare/рабочий репозиторий с веткой main (по умолчанию /srv/git/the-mine-spirit.git)
#   NIGHTLY_BRANCH    ветка (main)
#   NIGHTLY_WORK      отдельный чистый checkout (по умолчанию /opt/minecraft/nightly/mine-spirit)
#   NIGHTLY_STATE     файл sha последнего проверенного коммита (/opt/minecraft/nightly/last-checked-sha)
#   NIGHTLY_STATUS    статус-файл (/opt/minecraft/logs/nightly-analyzers.status)
#   NIGHTLY_LOG       краткий лог (/opt/minecraft/logs/nightly-analyzers.log)
#   NIGHTLY_BUILD_LOG полный вывод Gradle (/opt/minecraft/logs/nightly-analyzers-build.log)
#   NIGHTLY_LOCK      замок публикации/сборки (/opt/minecraft/tmp/build.lock; его же берёт scripts/publish.sh)
#   NIGHTLY_LOCK_WAIT ожидание замка, с (0 — не ждать)
#   NIGHTLY_FORCE=1   собрать даже без изменений
#   NIGHTLY_DRY_RUN=1 показать решение и команду, ничего не менять
#   NIGHTLY_TIMEOUT   предел сборки, с (7200), дальше код 124
#   NIGHTLY_GRADLEW   команда Gradle (по умолчанию ./gradlew в worktree)
#   NIGHTLY_SINCE     окно «реальных изменений» для git log (1 day ago)
set -uo pipefail

REPO="${NIGHTLY_REPO:-/srv/git/the-mine-spirit.git}"
BRANCH="${NIGHTLY_BRANCH:-main}"
WORK="${NIGHTLY_WORK:-/opt/minecraft/nightly/mine-spirit}"
STATE="${NIGHTLY_STATE:-/opt/minecraft/nightly/last-checked-sha}"
STATUS="${NIGHTLY_STATUS:-/opt/minecraft/logs/nightly-analyzers.status}"
LOG="${NIGHTLY_LOG:-/opt/minecraft/logs/nightly-analyzers.log}"
BUILD_LOG="${NIGHTLY_BUILD_LOG:-/opt/minecraft/logs/nightly-analyzers-build.log}"
LOCK="${NIGHTLY_LOCK:-/opt/minecraft/tmp/build.lock}"
LOCK_WAIT="${NIGHTLY_LOCK_WAIT:-0}"
TIMEOUT="${NIGHTLY_TIMEOUT:-7200}"
FORCE="${NIGHTLY_FORCE:-0}"
DRY_RUN="${NIGHTLY_DRY_RUN:-0}"
SINCE="${NIGHTLY_SINCE:-1 day ago}"

say() { printf '%s %s\n' "$(date '+%F %T')" "$*"; }
die() {
    local code="$1"
    shift
    say "ОШИБКА: $*"
    exit "$code"
}

usage() {
    sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        --force) FORCE=1 ;;
        -h|--help) usage ;;
        *) echo "Неизвестный аргумент: $1" >&2; usage ;;
    esac
    shift
done

# lib: cron запускает без терминала: пишем в свой лог, а не в почту. Ручной запуск видит вывод на экране.
if [ "$DRY_RUN" != 1 ] && [ ! -t 1 ]; then
    mkdir -p "$(dirname "$LOG")" || exit 4
    exec >>"$LOG" 2>&1
fi

say "ночной анализатор: ветка $BRANCH, репозиторий $REPO"
if [ "$DRY_RUN" = 1 ]; then
    say "режим --dry-run: решение и команда, без сборки"
fi

command -v git >/dev/null 2>&1 || die 4 "нужен git"
git -C "$REPO" rev-parse --verify --quiet "refs/heads/$BRANCH" >/dev/null \
    || die 4 "в $REPO нет ветки $BRANCH"

main_sha="$(git -C "$REPO" rev-parse "refs/heads/$BRANCH")" || die 4 "не прочитать sha ветки $BRANCH"
last_sha="$(cat "$STATE" 2>/dev/null | tr -d '[:space:]')"
recent="$(git -C "$REPO" log -1 --since="$SINCE" --format=%H "refs/heads/$BRANCH" 2>/dev/null)"

say "main=$main_sha, последний проверенный=${last_sha:-нет}"

if [ "$FORCE" != 1 ] && [ "$last_sha" = "$main_sha" ]; then
    say "изменений в $BRANCH с прошлой проверки нет: выходим, ничего не собирая"
    exit 0
fi
if [ "$FORCE" != 1 ] && [ -z "$recent" ] && [ -n "$last_sha" ]; then
    say "в $BRANCH не было коммитов за «$SINCE», но есть непроверенные изменения с прошлого раза: собираем"
elif [ "$FORCE" != 1 ] && [ -z "$recent" ] && [ -z "$last_sha" ]; then
    say "первый прогон: запоминаем состояние main и собираем базовую проверку"
fi
[ "$FORCE" = 1 ] && say "NIGHTLY_FORCE=1: собираем даже без изменений"

gradlew="${NIGHTLY_GRADLEW:-./gradlew}"
build_cmd=()
if command -v nice >/dev/null 2>&1; then
    build_cmd+=(nice -n 19)
fi
if command -v ionice >/dev/null 2>&1; then
    build_cmd+=(ionice -c 3)
fi
build_cmd+=("$gradlew" --offline --no-daemon --console=plain -Ptheminespirit.analyzers=on check --continue)

if [ "$DRY_RUN" = 1 ]; then
    say "план: git clone/fetch $REPO → $WORK, checkout $main_sha, git clean -fdx"
    say "план: (cd $WORK && ${build_cmd[*]})"
    exit 0
fi

mkdir -p "$(dirname "$LOCK")" "$(dirname "$STATE")" "$(dirname "$STATUS")" "$(dirname "$BUILD_LOG")" \
    || die 4 "не создать каталоги состояния/логов"

# lib: тот же замок берёт scripts/publish.sh: ночная сборка и публикация не идут одновременно.
if command -v flock >/dev/null 2>&1; then
    exec 9>"$LOCK" || die 4 "не открыть замок $LOCK"
    if ! flock -w "$LOCK_WAIT" 9; then
        say "замок $LOCK занят публикацией или сборкой: выходим"
        exit 3
    fi
else
    lock_dir="$LOCK.d"
    if ! mkdir "$lock_dir" 2>/dev/null; then
        say "замок $lock_dir занят: выходим"
        exit 3
    fi
    trap 'rmdir "$lock_dir" 2>/dev/null || true' EXIT
fi

mkdir -p "$(dirname "$WORK")" || die 4 "не создать каталог $WORK"
if [ ! -d "$WORK/.git" ]; then
    say "первый прогон: клонирую $REPO в $WORK"
    git clone --quiet --no-checkout "$REPO" "$WORK" || die 4 "не склонировать $REPO в $WORK"
fi
git -C "$WORK" fetch --quiet "$REPO" "+refs/heads/$BRANCH:refs/remotes/nightly/$BRANCH" \
    || die 4 "не обновить $WORK из $REPO"
git -C "$WORK" checkout --quiet --detach "$main_sha" || die 4 "не перейти на $main_sha в $WORK"
# lib: чистое дерево: снимаем возможные локальные правки и артефакты прошлой ночи; рабочие деревья сервера
# (the-mine-spirit) не трогаем — они остаются как есть.
git -C "$WORK" clean -qfdx || die 4 "не очистить $WORK"

say "сборка: ${build_cmd[*]}"
started=$(date +%s)
: >"$BUILD_LOG"
limit=()
if command -v timeout >/dev/null 2>&1; then
    limit=(timeout -k 60 "$TIMEOUT")
fi
(cd "$WORK" && "${limit[@]}" "${build_cmd[@]}") >>"$BUILD_LOG" 2>&1
code=$?
elapsed=$(( $(date +%s) - started ))

result="ЗЕЛЁНЫЙ"
[ "$code" -ne 0 ] && result="КРАСНЫЙ"
[ "$code" -eq 124 ] && result="КРАСНЫЙ (предел ${TIMEOUT} с)"

python_bin() {
    local path
    for path in "${THEMINESPIRIT_PYTHON:-}" "${PYTHON:-}" python3 python; do
        [ -n "$path" ] || continue
        path="$(command -v "$path" 2>/dev/null)" || continue
        case "$path" in
            */WindowsApps/*) continue ;;
        esac
        printf '%s' "$path"
        return 0
    done
    return 1
}

summary=""
PYTHON_EXE="$(python_bin || true)"
if [ -n "$PYTHON_EXE" ]; then
summary="$("$PYTHON_EXE" - "$WORK" <<'PY' 2>/dev/null
import glob
import os
import sys
import xml.etree.ElementTree as ET

work = sys.argv[1]
LIMIT = 15


def module_of(path):
    rel = os.path.relpath(path, work).replace(os.sep, '/')
    return rel.split('/build/', 1)[0]


def local(tag):
    return tag.rsplit('}', 1)[-1]


def report(kind, title, files, extract):
    total = 0
    rows = []
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except Exception:
            continue
        parents = {child: parent for parent in root.iter() for child in parent}
        for item in root.iter():
            if local(item.tag) != kind:
                continue
            values = extract(item, parents)
            if values is None:
                continue
            total += 1
            if len(rows) < LIMIT:
                rows.append('  %s %s' % (module_of(path), values))
    print('%s %d' % (title, total))
    for row in rows:
        print(row)
    if total > LIMIT:
        print('  … ещё %d' % (total - LIMIT))


def text(item, tag, default=''):
    child = item.find(tag)
    return (child.text or '').strip() if child is not None else default


def spot(item, parents):
    return '%s %s' % (item.get('type', '?'), text(item, 'ShortMessage'))


def located(item, parents):
    parent = parents.get(item)
    if parent is None:
        return ''
    return os.path.basename(parent.get('name', '') or '')


def pmd(item, parents):
    where = located(item, parents)
    return '%s%s %s' % (where + ':' if where else '', item.get('beginline', '?'), item.get('rule', '?'))


def check(item, parents):
    where = located(item, parents)
    return '%s%s %s' % (
        where + ':' if where else '', item.get('line', '?'), (item.get('message') or '')[:160])


def test(item):
    bad = item.find('failure') if item.find('failure') is not None else item.find('error')
    return '%s.%s %s' % (
        item.get('classname', '?'), item.get('name', '?'), ((bad.get('message') if bad is not None else '') or '')[:160])


report('BugInstance', 'SPOTBUGS', glob.glob(os.path.join(work, '**', 'build', 'reports', 'spotbugs', '*.xml'), recursive=True), spot)
report('violation', 'PMD', glob.glob(os.path.join(work, '**', 'build', 'reports', 'pmd', '*.xml'), recursive=True), pmd)
report('error', 'CHECKSTYLE', glob.glob(os.path.join(work, '**', 'build', 'reports', 'checkstyle', '*.xml'), recursive=True), check)

failures = 0
rows = []
for path in glob.glob(os.path.join(work, '**', 'build', 'test-results', 'test', 'TEST-*.xml'), recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in (node for node in root.iter() if local(node.tag) == 'testcase'):
        if case.find('failure') is not None or case.find('error') is not None:
            failures += 1
            if len(rows) < LIMIT:
                rows.append('  ' + test(case))
print('TESTS %d' % failures)
for row in rows:
    print(row)
if failures > LIMIT:
    print('  … ещё %d' % (failures - LIMIT))
PY
)"
fi

if [ -z "$summary" ]; then
    # lib: python нет — считаем нарушения по XML и печатаем типы/правила, чтобы находки были видны без python.
    count_xml() {
        local pattern="$1" tag="$2"
        find "$WORK" -path "*/$pattern/*.xml" -exec grep -ho "$tag" {} + 2>/dev/null | wc -l | tr -d ' '
    }
    tags_xml() {
        local pattern="$1" tag="$2"
        find "$WORK" -path "*/$pattern/*.xml" -exec grep -ho "$tag" {} + 2>/dev/null \
            | sort | uniq -c | sort -rn | head -15 | sed 's/^/  /'
    }
    summary="SPOTBUGS $(count_xml reports/spotbugs '<BugInstance')
$(tags_xml reports/spotbugs 'type="[^"]*"')
PMD $(count_xml reports/pmd '<violation')
$(tags_xml reports/pmd 'rule="[^"]*"')
CHECKSTYLE $(count_xml reports/checkstyle '<error')
TESTS $(count_xml test-results/test '<failure')
(подробности: нет python3)"
fi

spotless="ok"
if grep -qE "Execution failed for task ':?[^']*spotless" "$BUILD_LOG"; then
    spotless="КРАСНЫЙ"
fi
[ "$code" -ne 0 ] && [ "$result" = "ЗЕЛЁНЫЙ" ] && result="КРАСНЫЙ"

printf '%s\n' "$summary" >"$STATUS.tmp"
{
    echo "дата: $(date '+%F %T %z')"
    echo "ветка: $BRANCH"
    echo "sha: $main_sha"
    echo "результат: $result (код $code, ${elapsed} с)"
    echo "spotless: $spotless"
    echo "сборка: ${build_cmd[*]}"
    echo "полный лог: $BUILD_LOG"
    echo "находки:"
    printf '%s\n' "$summary"
} >>"$STATUS.tmp"
mv "$STATUS.tmp" "$STATUS" || die 4 "не записать статус $STATUS"

printf '%s' "$main_sha" >"$STATE.tmp" && mv "$STATE.tmp" "$STATE"

say "итог: $result за ${elapsed} с, sha $main_sha"
say "статус: $STATUS, полный лог: $BUILD_LOG"
printf '%s\n' "$summary"
[ "$code" -eq 0 ] || exit 1
exit 0
