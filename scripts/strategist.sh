#!/usr/bin/env bash
# Надзор за агентом-стратегом духов (agents/strategist, docs/STRATEGIST.md).
#
# Использование:
#   scripts/strategist.sh start            запустить цикл в tmux-сессии mc-strategist (вывод — STRATEGIST_HOME/strategist.log)
#   scripts/strategist.sh stop             корректно остановить (SIGTERM); поставленные задачи не отменяются
#   scripts/strategist.sh status           жив ли, режим, модель, последний цикл, счётчики за час, активные задачи, последний сбой
#   scripts/strategist.sh pause|resume     создать/убрать STRATEGIST_HOME/PAUSE: новых задач нет, отслеживание идёт
#   scripts/strategist.sh once             один цикл в этом терминале и выход
#   scripts/strategist.sh sandbox-check    проверить, что модель не может выполнить инструмент (ls) в песочнице opencode
#   scripts/strategist.sh smoke [модель]   живая проба качества модели на записанных наблюдениях (в цикл не входит)
#   scripts/strategist.sh logs [N]         последние N строк strategist.log (по умолчанию 50)
#
# Переменные: SPIRIT_API, STRATEGIST_HOME (/opt/minecraft/strategist), STRATEGIST_MODE (dry по умолчанию), STRATEGIST_MODEL,
# STRATEGIST_MODEL_FALLBACK, STRATEGIST_INTERVAL и остальные из docs/STRATEGIST.md. Режим live только с согласия пользователя:
# нужен STRATEGIST_CONFIRM_LIVE=1. STRATEGIST_SESSION — имя tmux-сессии (mc-strategist; не oc-*, чтобы opencode-status-notify.sh
# не принял её за исполнителя), STRATEGIST_ARGS — дополнительные аргументы агента (например, --fixtures для проверок).
#
# Коды выхода: 0 — готово (status: работает, sandbox-check: отказ подтверждён); 1 — не запущен, не удалось запуститься
# или sandbox-check не подтвердил отказ; 2 — неверное использование или конфигурация;
# 3 — второй экземпляр отказался; 4 — остановка не завершилась за STRATEGIST_STOP_WAIT секунд.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$REPO_DIR/agents/strategist"
HOME_DIR="${STRATEGIST_HOME:-/opt/minecraft/strategist}"
SESSION="${STRATEGIST_SESSION:-mc-strategist}"
PYTHON="${STRATEGIST_PYTHON:-python3}"
LOG="$HOME_DIR/strategist.log"
STOP_WAIT="${STRATEGIST_STOP_WAIT:-40}"
START_WAIT="${STRATEGIST_START_WAIT:-3}"
EXIT_NOT_RUNNING=1
EXIT_USAGE=2
EXIT_LOCKED=3
EXIT_STOP_TIMEOUT=4

usage() {
    sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'
    exit "$EXIT_USAGE"
}

running() {
    tmux has-session -t "$SESSION" 2>/dev/null
}

agent_pid() {
    tmux list-panes -t "$SESSION" -F '#{pane_pid}' 2>/dev/null | head -n 1
}

agent_env() {
    env | grep -E '^(STRATEGIST_|SPIRIT_API=)' || true
}

require_tools() {
    command -v tmux >/dev/null || { echo "tmux не найден: установите tmux или запустите 'once' в обычном терминале" >&2; exit "$EXIT_USAGE"; }
    command -v "$PYTHON" >/dev/null || { echo "$PYTHON не найден: стратегу нужен Python 3 (стандартная библиотека)" >&2; exit "$EXIT_USAGE"; }
}

check_live_confirmation() {
    if [ "${STRATEGIST_MODE:-dry}" = "live" ] && [ "${STRATEGIST_CONFIRM_LIVE:-}" != "1" ]; then
        echo "Режим live (задачи духам по-настоящему) включается только с согласия пользователя (docs/STRATEGIST.md)." >&2
        echo "Получите согласие и запустите с STRATEGIST_CONFIRM_LIVE=1. Запуск отменён." >&2
        exit "$EXIT_USAGE"
    fi
}

cmd_start() {
    require_tools
    check_live_confirmation
    if running; then
        echo "Стратег уже запущен (tmux-сессия $SESSION). Второй экземпляр не запускаю; статус: scripts/strategist.sh status" >&2
        exit "$EXIT_LOCKED"
    fi
    mkdir -p "$HOME_DIR"
    local -a vars
    mapfile -t vars < <(agent_env)
    # shellcheck disable=SC2086
    tmux new-session -d -s "$SESSION" -c "$APP_DIR" env "${vars[@]}" bash -c \
        'exec "$1" -u strategist.py run ${STRATEGIST_ARGS:-} >>"$2" 2>&1' _ "$PYTHON" "$LOG"
    sleep "$START_WAIT"
    if ! running; then
        echo "Стратег не запустился. Последние строки $LOG:" >&2
        tail -n 15 "$LOG" >&2 2>/dev/null
        if grep -q 'Другой экземпляр' "$LOG" 2>/dev/null; then exit "$EXIT_LOCKED"; fi
        exit "$EXIT_NOT_RUNNING"
    fi
    echo "Стратег запущен: режим ${STRATEGIST_MODE:-dry}, сессия $SESSION, лог $LOG"
}

cmd_stop() {
    if ! running; then
        echo "Стратег не запущен (сессии $SESSION нет)."
        exit 0
    fi
    local pid waited=0
    pid="$(agent_pid)"
    kill -TERM "$pid" 2>/dev/null
    while running && [ "$waited" -lt "$STOP_WAIT" ]; do
        sleep 1
        waited=$((waited + 1))
    done
    if running; then
        echo "Стратег ещё завершает текущий вызов модели (прошло $waited с). Проверьте позже: scripts/strategist.sh status" >&2
        exit "$EXIT_STOP_TIMEOUT"
    fi
    echo "Стратег остановлен (SIGTERM); поставленные задачи не отменялись."
}

cmd_status() {
    require_tools
    "$PYTHON" "$APP_DIR/strategist.py" status
    local code=$?
    if running; then
        echo "tmux: сессия $SESSION жива"
        return "$code"
    fi
    echo "tmux: сессии $SESSION нет"
    return "$EXIT_NOT_RUNNING"
}

cmd_pause() {
    mkdir -p "$HOME_DIR"
    : >"$HOME_DIR/PAUSE"
    echo "PAUSE создан: новых задач не будет, отслеживание поставленных продолжается."
}

cmd_resume() {
    if [ -e "$HOME_DIR/PAUSE" ]; then
        rm -f "$HOME_DIR/PAUSE"
        echo "PAUSE убран: стратег снова ставит задачи."
    else
        echo "PAUSE не было."
    fi
}

cmd_once() {
    require_tools
    check_live_confirmation
    cd "$APP_DIR" || exit "$EXIT_USAGE"
    # shellcheck disable=SC2086
    exec "$PYTHON" -u strategist.py once ${STRATEGIST_ARGS:-}
}

cmd_sandbox_check() {
    command -v "$PYTHON" >/dev/null || { echo "$PYTHON не найден: стратегу нужен Python 3 (стандартная библиотека)" >&2; exit "$EXIT_USAGE"; }
    cd "$APP_DIR" || exit "$EXIT_USAGE"
    exec "$PYTHON" -u strategist.py sandbox-check
}

cmd_smoke() {
    require_tools
    cd "$APP_DIR" || exit "$EXIT_USAGE"
    local -a extra=()
    [ -n "${1:-}" ] && extra=(--model "$1")
    exec "$PYTHON" -u strategist.py smoke "${extra[@]}"
}

cmd_logs() {
    local lines="${1:-50}"
    [[ "$lines" =~ ^[0-9]+$ ]] || { echo "logs: число строк должно быть целым, получено '$lines'" >&2; exit "$EXIT_USAGE"; }
    [ -f "$LOG" ] || { echo "Лога ещё нет: $LOG" >&2; exit "$EXIT_NOT_RUNNING"; }
    tail -n "$lines" "$LOG"
}

[ $# -ge 1 ] || usage
command="$1"
shift
case "$command" in
    start) cmd_start ;;
    stop) cmd_stop ;;
    status) cmd_status ;;
    pause) cmd_pause ;;
    resume) cmd_resume ;;
    once) cmd_once ;;
    sandbox-check) cmd_sandbox_check ;;
    smoke) cmd_smoke "$@" ;;
    logs) cmd_logs "$@" ;;
    *) usage ;;
esac
