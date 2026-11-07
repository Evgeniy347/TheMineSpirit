#!/usr/bin/env bash
# lib: проверка scripts/changed-modules.sh на подделках: путь → модуль, обратный граф, общие пути.
cd "$(dirname "$0")/../.." || exit 2
failed=0

ok() { echo "OK $1"; }
fail() { echo "FAIL $1"; failed=1; }

modules() { bash scripts/changed-modules.sh "$@"; }

expect_has() {
    local name="$1" path="$2"
    shift 2
    local out
    out="$(modules "$path")"
    local missing=""
    local want
    for want in "$@"; do
        printf '%s\n' "$out" | grep -qx -- "$want" || missing="$missing $want"
    done
    [ -z "$missing" ] && ok "$name: есть${*:+ $*}" || fail "$name: нет$missing (вывод: $(printf '%s' "$out" | tr '\n' ' '))"
}

expect_not() {
    local name="$1" path="$2" unwanted="$3"
    local out
    out="$(modules "$path")"
    if printf '%s\n' "$out" | grep -qx -- "$unwanted"; then
        fail "$name: лишний $unwanted (вывод: $(printf '%s' "$out" | tr '\n' ' '))"
    else
        ok "$name: без $unwanted"
    fi
}

expect_empty() {
    local name="$1" path="$2" out
    out="$(modules "$path")"
    [ -z "$out" ] && ok "$name: пусто" || fail "$name: ожидал пусто, получил: $(printf '%s' "$out" | tr '\n' ' ')"
}

expect_empty "docs" "docs/TESTING.md"
expect_empty "tools" "tools/graph-gpu/graph_model.py"
expect_empty "scripts" "scripts/test.sh"
expect_empty "TestResults" "TestResults/task.md"

expect_has "mining" "mech/mining/src/main/java/com/theminespirit/X.java" \
    ":mech:mining" ":content" ":guards"
expect_not "mining" "mech/mining/src/main/java/com/theminespirit/X.java" ":mech:farming"
expect_not "mining" "mech/mining/src/main/java/com/theminespirit/X.java" ":tactics"

expect_has "farming-backslash" "mech\\farming\\src\\main\\java\\X.java" ":mech:farming" ":content"
expect_not "farming-backslash" "mech\\farming\\src\\main\\java\\X.java" ":mech:mining"

expect_has "board" "board/src/main/java/com/theminespirit/X.java" ":board" ":content" ":guards"
expect_not "board" "board/src/main/java/com/theminespirit/X.java" ":mech:farming"

expect_has "guards-only" "guards/src/test/java/com/theminespirit/guards/XTests.java" ":guards"
expect_not "guards-only" "guards/src/test/java/com/theminespirit/guards/XTests.java" ":board"

expect_has "api" "api/src/main/java/com/theminespirit/X.java" ":api" ":board" ":mech:farming"
expect_has "fixtures" "test-fixtures/src/main/java/com/theminespirit/X.java" ":test-fixtures" ":board" ":root"
expect_has "config" "config/pmd/main.xml" ":root" ":board" ":mech:farming"
expect_has "shared-resources" "src/test/resources/board/case.json" ":root" ":board" ":mech:farming"
expect_has "mod-resources" "mod/src/main/resources/data/theminespirit/x.json" ":root" ":board" ":mech:farming"
expect_has "root-test" "src/test/java/com/theminespirit/testing/XTests.java" ":root" ":guards"
expect_not "root-test" "src/test/java/com/theminespirit/testing/XTests.java" ":board"
expect_has "mod" "mod/src/main/java/com/theminespirit/X.java" ":mod" ":gametest" ":guards"
expect_not "mod" "mod/src/main/java/com/theminespirit/X.java" ":board"
expect_not "mod" "mod/src/main/java/com/theminespirit/X.java" ":root"

tasks="$(bash scripts/changed-modules.sh --tasks board/src/main/java/X.java)"
printf '%s\n' "$tasks" | grep -qx ":board:test" && ok "tasks: :board:test" || fail "tasks: нет :board:test ($(printf '%s' "$tasks" | tr '\n' ' '))"
tasks="$(bash scripts/changed-modules.sh --tasks src/test/java/com/theminespirit/XTests.java)"
printf '%s\n' "$tasks" | grep -qx ":test" && ok "tasks: :test для корня" || fail "tasks: нет :test"

all="$(bash scripts/changed-modules.sh --all)"
for want in ":root" ":api" ":board" ":mech:farming" ":gametest"; do
    printf '%s\n' "$all" | grep -qx -- "$want" && ok "all: $want" || fail "all: нет $want"
done

probe="mech/mining/src/main/java/com/theminespirit/ChangedModulesProbe.java"
mkdir -p "$(dirname "$probe")"
printf 'package com.theminespirit;\n' >"$probe"
from_git="$(bash scripts/changed-modules.sh)"
rm -f "$probe"
printf '%s\n' "$from_git" | grep -qx ":mech:mining" \
    && ok "git: незакоммиченный путь попал в набор" \
    || fail "git: нет :mech:mining (вывод: $(printf '%s' "$from_git" | tr '\n' ' '))"
printf '%s\n' "$from_git" | grep -qx ":guards" \
    && ok "git: правка .java добавила :guards" \
    || fail "git: нет :guards"

exit $failed
