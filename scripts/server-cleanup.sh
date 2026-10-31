#!/usr/bin/env bash
set -euo pipefail

TMP_DIR="${TMP_DIR:-/opt/minecraft/tmp}"
LOG_DIR="${LOG_DIR:-/opt/minecraft/logs}"
TMP_MINUTES="${TMP_MINUTES:-1440}"
LOG_DAYS="${LOG_DAYS:-14}"

case "${1:-}" in
    -h|--help)
        echo "Использование: server-cleanup.sh"
        echo "Удаляет из $TMP_DIR записи старше ${TMP_MINUTES} мин, из $LOG_DIR — файлы старше ${LOG_DAYS} сут."
        echo "Переменные: TMP_DIR, LOG_DIR, TMP_MINUTES, LOG_DAYS."
        exit 0 ;;
esac

sweep() {
    local dir="$1" label="$2" before after
    shift 2
    [ -d "$dir" ] || return 0
    before="$(du -sk "$dir" | cut -f1)"
    find "$dir" -mindepth 1 -maxdepth 1 "$@" -print0 | xargs -0 -r rm -rf
    after="$(du -sk "$dir" | cut -f1)"
    echo "$(date '+%F %T') $label: $dir, освобождено $(( before - after )) КБ"
}

sweep "$TMP_DIR" "tmp" -mmin +"${TMP_MINUTES}"
sweep "$LOG_DIR" "logs" -mtime +"${LOG_DAYS}"
