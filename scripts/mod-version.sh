#!/usr/bin/env bash
# lib: имя jar в mods/ доказывает установку, а не загрузку: 10.11 сервер крутил 0.1.3454, пока в mods/ уже
# lib: лежала новая сборка. Загруженную версию пишет сам NeoForge при старте в debug.log, её и читаем.
set -euo pipefail

usage() {
    cat <<'EOF'
Использование:
  mod-version.sh <каталог логов>    версия мода, которую загрузил сервер (debug.log NeoForge)
  mod-version.sh --built <дерево>   версия, которую даст сборка из этого дерева (mod_version_minor + число коммитов)

Печатает одну строку — версию. Код 1 — версию не определить, причина в stderr.
EOF
}

DEBUG_SEARCH_LINES=4000

running() {
    local logs="$1" debug="$1/debug.log" line version
    if [ ! -f "$debug" ]; then
        echo "нет $debug: по нему не узнать, какую сборку загрузил сервер" >&2
        return 1
    fi
    line="$(head -n "$DEBUG_SEARCH_LINES" "$debug" | grep -a "Found valid mod file theminespirit-" | tail -n 1 || true)"
    version="$(printf '%s' "$line" | sed -n 's/.*versions {\([^}]*\)}.*/\1/p')"
    if [ -z "$version" ]; then
        echo "в $debug нет записи «Found valid mod file theminespirit-… versions {…}»: мод не загрузился" >&2
        return 1
    fi
    printf '%s\n' "$version"
}

built() {
    local tree="$1" minor count
    if [ ! -f "$tree/gradle.properties" ]; then
        echo "нет $tree/gradle.properties: не из чего взять mod_version_minor" >&2
        return 1
    fi
    minor="$(sed -n 's/^mod_version_minor=//p' "$tree/gradle.properties" | head -n 1)"
    count="$(git -C "$tree" rev-list --count HEAD 2>/dev/null || true)"
    if [ -z "$minor" ] || [ -z "$count" ]; then
        echo "не определить версию сборки: mod_version_minor='$minor', коммитов='$count'" >&2
        return 1
    fi
    printf '%s.%s\n' "$minor" "$count"
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    --built)
        [ $# -ge 2 ] || { usage >&2; exit 2; }
        built "$2"
        ;;
    "")
        usage >&2
        exit 2
        ;;
    *)
        running "$1"
        ;;
esac
