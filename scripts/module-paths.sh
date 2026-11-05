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
MOD_MAIN_DIR="$(module_layout_value main)"
MOD_TEST_DIR="$(module_layout_value test)"
MOD_RESOURCES_DIR="$(module_layout_value resources)"
MOD_TEST_RESOURCES_DIR="$(module_layout_value test_resources)"
MOD_TEMPLATES_DIR="$(module_layout_value templates)"
MOD_GENERATED_DIR="$(module_layout_value generated_resources)"
MOD_GAME_TESTS_DIR="$MOD_MAIN_DIR/$(module_layout_value gametests)"
MOD_JAR_DIR="${THEMINESPIRIT_JAR_DIR:-$(module_layout_value jar_dir)}"
MOD_MODULE_MAIN_ROOTS="$(module_layout_value module_roots)"
MOD_MODULE_TEST_ROOTS="$(module_layout_value module_test_roots)"

for name in MOD_PACKAGE_ROOT MOD_MAIN_DIR MOD_TEST_DIR MOD_RESOURCES_DIR MOD_TEST_RESOURCES_DIR \
        MOD_TEMPLATES_DIR MOD_GENERATED_DIR MOD_GAME_TESTS_DIR MOD_JAR_DIR MOD_MODULE_MAIN_ROOTS \
        MOD_MODULE_TEST_ROOTS; do
    if [ -z "${!name}" ]; then
        echo "ОШИБКА: в $MODULE_LAYOUT_FILE нет значения для $name" >&2
        return 1 2>/dev/null || exit 1
    fi
done
