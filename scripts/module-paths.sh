#!/usr/bin/env bash

: "${PROJECT_ROOT:=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MODULE_LAYOUT_FILE="${THEMINESPIRIT_LAYOUT:-$PROJECT_ROOT/config/layout.properties}"

if [ ! -f "$MODULE_LAYOUT_FILE" ]; then
    echo "ОШИБКА: нет карты путей $MODULE_LAYOUT_FILE" >&2
    return 1 2>/dev/null || exit 1
fi

module_layout_value() {
    awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$MODULE_LAYOUT_FILE"
}

MOD_PACKAGE_ROOT="$(module_layout_value package_root)"
MOD_MAIN_DIR=""
MOD_TEST_DIR="src/test/java"
MOD_RESOURCES_DIR="$(module_layout_value resources)"
MOD_TEST_RESOURCES_DIR="$(module_layout_value test_resources)"
MOD_TEMPLATES_DIR="$(module_layout_value templates)"
MOD_GENERATED_DIR="$(module_layout_value generated_resources)"
MOD_GAME_TESTS_DIR="$(module_layout_value gametests)"
MOD_JAR_DIR="${THEMINESPIRIT_JAR_DIR:-$(module_layout_value jar_dir)}"
MOD_SETTINGS_FILE="${THEMINESPIRIT_SETTINGS:-$PROJECT_ROOT/settings.gradle}"

if [ ! -f "$MOD_SETTINGS_FILE" ]; then
    echo "ОШИБКА: нет списка модулей $MOD_SETTINGS_FILE" >&2
    return 1 2>/dev/null || exit 1
fi

settings_module_dirs() {
    sed -nE "s/^[[:space:]]*include[[:space:]]+'(:[^']+)'.*/\1/p" "$MOD_SETTINGS_FILE" |
        while IFS= read -r id; do
            case "$id" in
                :api) continue ;;
            esac
            printf '%s\n' "${id#:}" | tr ':' '/'
        done
}

# lib: корень main-исходников — sourceSet модуля :mod (соглашение build-logic),
# а не отдельная строка layout.properties: иначе путь разъедется со settings.gradle.
settings_module_dir() {
    sed -nE "s/^[[:space:]]*include[[:space:]]+'(:[^']+)'.*/\1/p" "$MOD_SETTINGS_FILE" |
        while IFS= read -r id; do
            [ "$id" = "$1" ] && printf '%s\n' "${id#:}" | tr ':' '/'
        done
}

MOD_MAIN_DIR="$(settings_module_dir ':mod')/src/main/java"

module_roots_from_settings() {
    local source_set="$1" out="" dir
    while IFS= read -r dir; do
        [ -n "$dir" ] || continue
        out="${out:+$out,}$dir/$source_set"
    done < <(settings_module_dirs)
    printf '%s' "$out"
}

MOD_MODULE_MAIN_ROOTS="$(module_roots_from_settings src/main/java)"
MOD_MODULE_TEST_ROOTS="$(module_roots_from_settings src/test/java)"

for name in MOD_PACKAGE_ROOT MOD_MAIN_DIR MOD_TEST_DIR MOD_RESOURCES_DIR MOD_TEST_RESOURCES_DIR \
        MOD_TEMPLATES_DIR MOD_GENERATED_DIR MOD_GAME_TESTS_DIR MOD_JAR_DIR MOD_MODULE_MAIN_ROOTS \
        MOD_MODULE_TEST_ROOTS; do
    if [ -z "${!name}" ]; then
        echo "ОШИБКА: в $MODULE_LAYOUT_FILE нет значения для $name" >&2
        return 1 2>/dev/null || exit 1
    fi
done
