#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/prove-red-check"
rm -rf "$work"
mkdir -p "$work/repo"
repo="$work/repo"
failed=0

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

mkdir -p "$repo/scripts" "$repo/config" "$repo/board/src/test/java/x"
cp scripts/prove-red.sh scripts/module-paths.sh "$repo/scripts/"
cp config/layout.properties "$repo/config/"
cat >"$repo/settings.gradle" <<'GRADLE'
include ':api'
include ':board'
GRADLE
cat >"$repo/gradlew" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"$PROVE_LOG"
case " $* " in
    *" :api:test "*|*" test "*)
        echo "No tests found for given includes" >&2
        exit 1
        ;;
esac
case " $* " in
    *" :board:test "*) ;;
    *)
        echo "prove-red запустил не тот модуль: $*" >&2
        exit 1
        ;;
esac
case " $* " in
    *" -Ptheminespirit.filtered "*) ;;
    *)
        echo "prove-red не выключил падение пустых фильтров: $*" >&2
        exit 1
        ;;
esac
head="$(git rev-parse HEAD)"
mkdir -p TestResults/board/xml
xml="TestResults/board/xml/TEST-x.ChangedTest.xml"
if [ "$head" = "$FAKE_PARENT" ]; then
    cat >"$xml" <<'XML'
<testsuite name="x.ChangedTest" tests="1" failures="1" errors="0" skipped="0">
  <testcase name="changed" classname="x.ChangedTest"><failure message="red"/></testcase>
</testsuite>
XML
else
    cat >"$xml" <<'XML'
<testsuite name="x.ChangedTest" tests="1" failures="0" errors="0" skipped="0">
  <testcase name="changed" classname="x.ChangedTest"/>
</testsuite>
XML
fi
exit 0
FAKE
chmod +x "$repo/gradlew"
cat >"$repo/board/src/test/java/x/ChangedTest.java" <<'JAVA'
package x;

import org.junit.jupiter.api.Test;

class ChangedTest {
    @Test
    void changed() {}
}
JAVA

(
    cd "$repo" || exit 2
    git init -q
    git config user.email check@example.org
    git config user.name check
    git add .
    git commit -q -m "parent"
    parent="$(git rev-parse HEAD)"
    printf '\n// fix\n' >>board/src/test/java/x/ChangedTest.java
    git add .
    git commit -q -m "fix: тест"
    fix="$(git rev-parse HEAD)"
    printf '%s %s\n' "$parent" "$fix"
) >"$work/shas" || { fail "не удалось собрать временный git-репозиторий"; exit 1; }
read -r parent fix <"$work/shas"

log="$work/gradle.log"
: >"$log"
export PROVE_LOG="$log" FAKE_PARENT="$parent" FAKE_RED_KEEP=1 PROVE_RED_KEEP=1
if (cd "$repo" && bash scripts/prove-red.sh "$fix") >"$work/prove.out" 2>&1; then
    ok "prove-red дал вердикт: тест красный на родителе и зелёный на коммите"
else
    fail "prove-red не дал вердикта (код $?): $(tail -n 3 "$work/prove.out" | tr '\n' ' ')"
fi

if grep -q '^:board:test ' "$log" 2>/dev/null || grep -q ' :board:test ' "$log"; then
    ok "фильтр ушёл в тестовую задачу модуля :board"
else
    fail "prove-red не запустил :board:test: $(head -n 2 "$log" | tr '\n' ' ')"
fi
if grep -qE '(^| )(:api:test|test)( |$)' "$log"; then
    fail "prove-red запустил общую задачу test (снова упадёт на :api:test): $(head -n 2 "$log" | tr '\n' ' ')"
else
    ok "общая задача test и :api:test не запускались"
fi
if grep -q 'OK красный→зелёный: x.ChangedTest' "$work/prove.out"; then
    ok "итог называет класс и красное→зелёное"
else
    fail "в итоге нет вердикта по классу: $(tail -n 3 "$work/prove.out" | tr '\n' ' ')"
fi

rm -rf "$work"
exit "$failed"
