#!/usr/bin/env bash
# lib: один выбор python на все скрипты (publish.sh, nightly-analyzers.sh, check-publish.sh): иначе копии
# расходятся, и на сервере один скрипт находит python, а другой молча остаётся без сводки.
#
# python_bin [модуль] — печатает путь к рабочему python: THEMINESPIRIT_PYTHON → PYTHON → python3 → python.
# Заглушки WindowsApps (магазинная установка) пропускаются. С аргументом проверяет, что python импортирует
# модуль (например, python_bin zipfile — для чтения jar), и берёт следующий кандидат, если не может.
python_bin() {
    local module="${1:-}" candidate path
    for candidate in "${THEMINESPIRIT_PYTHON:-}" "${PYTHON:-}" python3 python; do
        [ -n "$candidate" ] || continue
        path="$(command -v "$candidate" 2>/dev/null)" || continue
        case "$path" in
            */WindowsApps/*) continue ;;
        esac
        if [ -n "$module" ] && ! "$path" -c "import $module" >/dev/null 2>&1; then
            continue
        fi
        printf '%s' "$path"
        return 0
    done
    return 1
}
