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

# lib: сводка находок — один разбор XML на java из toolchain Gradle (python не нужен): два разбора расходились,
# а grep-запасной путь терял имена файлов и ошибки тестов. Java-исходник собирается source-лаунчером на месте,
# поэтому на сервер доезжает только этот скрипт.
JAVA_EXE=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="$(command -v java 2>/dev/null || true)"
fi
[ -n "$JAVA_EXE" ] || die 4 "нужен java 21 (toolchain Gradle): без него не построить сводку находок"

summary_dir="$(mktemp -d "${TMPDIR:-/tmp}/theminespirit-summary.XXXXXX")" \
    || die 4 "не создать каталог для сводки находок"
cat >"$summary_dir/AnalyzerSummary.java" <<'JAVA'
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class AnalyzerSummary {
    private static final int LIMIT = 15;
    private static final int MESSAGE = 160;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Использование: AnalyzerSummary <каталог сборки>");
            System.exit(2);
        }
        Path work = Paths.get(args[0]).toAbsolutePath().normalize();
        List<String> broken = new ArrayList<>();
        section(work, "SPOTBUGS", "spotbugs", "BugInstance", AnalyzerSummary::spot, broken);
        section(work, "PMD", "pmd", "violation", AnalyzerSummary::pmd, broken);
        section(work, "CHECKSTYLE", "checkstyle", "error", AnalyzerSummary::check, broken);
        tests(work, broken);
        for (String path : broken) {
            System.out.println("НЕ ПРОЧИТАН " + path);
        }
    }

    private static void section(
            Path work, String title, String kind, String element, Function<Element, String> row, List<String> broken)
            throws IOException {
        int total = 0;
        List<String> rows = new ArrayList<>();
        for (Path file : files(work, "build/reports/" + kind + "/")) {
            for (Element item : elements(work, file, element, broken)) {
                String value = row.apply(item);
                if (value == null) {
                    continue;
                }
                total++;
                if (rows.size() < LIMIT) {
                    rows.add("  " + module(work, file) + " " + value);
                }
            }
        }
        print(title, total, rows);
    }

    private static void tests(Path work, List<String> broken) throws IOException {
        int total = 0;
        List<String> rows = new ArrayList<>();
        for (Path file : files(work, "build/test-results/test/")) {
            if (!file.getFileName().toString().startsWith("TEST-")) {
                continue;
            }
            for (Element test : elements(work, file, "testcase", broken)) {
                Element bad = child(test, "failure");
                if (bad == null) {
                    bad = child(test, "error");
                }
                if (bad == null) {
                    continue;
                }
                total++;
                if (rows.size() < LIMIT) {
                    String message = bad.hasAttribute("message") ? bad.getAttribute("message") : bad.getTextContent();
                    rows.add("  " + test.getAttribute("classname") + "." + test.getAttribute("name") + " " + cut(message));
                }
            }
        }
        print("TESTS", total, rows);
    }

    private static void print(String title, int total, List<String> rows) {
        StringBuilder out = new StringBuilder(title).append(' ').append(total).append('\n');
        for (String row : rows) {
            out.append(row).append('\n');
        }
        if (total > LIMIT) {
            out.append("  … ещё ").append(total - LIMIT).append('\n');
        }
        System.out.print(out);
    }

    private static List<Path> files(Path work, String marker) throws IOException {
        List<Path> found = new ArrayList<>();
        try (var walk = Files.walk(work)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> relative(work, path).contains(marker))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .forEach(found::add);
        }
        return found;
    }

    private static List<Element> elements(Path work, Path file, String local, List<String> broken) {
        List<Element> found = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            collect(factory.newDocumentBuilder().parse(file.toFile()), local, found);
        } catch (Exception error) {
            broken.add(relative(work, file));
        }
        return found;
    }

    private static void collect(Node node, String local, List<Element> found) {
        if (node.getNodeType() == Node.ELEMENT_NODE && local.equals(name(node))) {
            found.add((Element) node);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, local, found);
        }
    }

    private static String name(Node node) {
        if (node.getLocalName() != null) {
            return node.getLocalName();
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private static String spot(Element item) {
        String message = text(item, "ShortMessage");
        String type = item.getAttribute("type");
        return message.isEmpty() ? type : type + " " + message;
    }

    private static String pmd(Element item) {
        return where(item, "beginline") + item.getAttribute("rule");
    }

    private static String check(Element item) {
        return where(item, "line") + cut(item.getAttribute("message"));
    }

    private static String where(Element item, String line) {
        String file = base(item.getParentNode() instanceof Element parent ? parent.getAttribute("name") : "");
        return (file.isEmpty() ? "" : file + ":") + item.getAttribute(line) + " ";
    }

    private static String base(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String cut(String message) {
        String text = message.replaceAll("\\s+", " ").trim();
        return text.length() <= MESSAGE ? text : text.substring(0, MESSAGE);
    }

    private static String module(Path work, Path file) {
        String relative = relative(work, file);
        int build = relative.indexOf("/build/");
        return build < 0 ? relative : relative.substring(0, build);
    }

    private static String relative(Path work, Path file) {
        return work.relativize(file).toString().replace('\\', '/');
    }

    private static String text(Element item, String tag) {
        Element child = child(item, tag);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element child(Element item, String tag) {
        for (Node node = item.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(name(node))) {
                return (Element) node;
            }
        }
        return null;
    }
}
JAVA
# lib: вывод java принудительно UTF-8: иначе на Windows-консоли он пишет cp866 и сводка расходится с остальным
# статус-файлом (bash пишет UTF-8).
if ! summary="$("$JAVA_EXE" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    "$summary_dir/AnalyzerSummary.java" "$WORK")"; then
    rm -rf "$summary_dir"
    die 4 "не разобрать отчёты анализаторов в $WORK"
fi
rm -rf "$summary_dir"

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
