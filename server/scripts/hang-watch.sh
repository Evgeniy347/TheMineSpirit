#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

LOG_DIR="${HANG_WATCH_LOG_DIR:-/opt/minecraft/logs}"
TMP="${HANG_WATCH_TMP:-$TMP_DIR}"
THRESHOLD="${HANG_WATCH_THRESHOLD:-180}"
DEBUG_LEASE="${HANG_WATCH_DEBUG_LEASE:-/opt/minecraft/debug.lease}"
REST="${HANG_WATCH_REST:-http://127.0.0.1:8765}"
HEARTBEAT="${HANG_WATCH_HEARTBEAT:-$LOG_DIR/tick.heartbeat}"
MAIN_LOG="${HANG_WATCH_MAIN_LOG:-$LOG_DIR/latest.log}"
HANG_LOG="${HANG_WATCH_HANG_LOG:-$LOG_DIR/hang.log}"
JCMD="${HANG_WATCH_JCMD:-jcmd}"
STOP_SCRIPT="${HANG_WATCH_STOP_SCRIPT:-$SERVER_DIR/scripts/stop.sh}"
START_SCRIPT="${HANG_WATCH_START_SCRIPT:-$SERVER_DIR/scripts/start.sh}"
CURL="${HANG_WATCH_CURL:-curl}"
MODE="${HANG_WATCH_MODE:-act}"
DRY_RUN="${HANG_WATCH_DRY_RUN:-0}"
NOW="${HANG_WATCH_NOW:-$(date +%s)}"
STOP_MARKER="${HANG_WATCH_STOP_MARKER:-$TMP/server-stop.lock}"

mkdir -p "$TMP" "$LOG_DIR"

if [ "${HANG_WATCH_NO_LOCK:-0}" != 1 ]; then
    LOCK="$TMP/hang-watch.lock"
    if ! mkdir "$LOCK" 2>/dev/null; then
        holder="$(cat "$LOCK/pid" 2>/dev/null || true)"
        if [ -n "$holder" ] && kill -0 "$holder" 2>/dev/null; then
            exit 0
        fi
        rm -rf "$LOCK"
        mkdir "$LOCK" 2>/dev/null || exit 0
    fi
    printf '%s\n' "$$" >"$LOCK/pid"
    trap 'rm -rf "$LOCK"' EXIT
fi

server_running() {
    [ "${HANG_WATCH_ASSUME_RUNNING:-0}" = 1 ] && return 0
    is_running
}

process_alive() {
    [ "${HANG_WATCH_ASSUME_ALIVE:-0}" = 1 ] && return 0
    [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null
}

# lib: обновление JDK на месте (apt unattended) убивает консоль работающего сервера: тик, REST и heartbeat живы,
# lib: а stdin/stdout мёртв — команды save-* и stop не подтверждаются, и сторож по одному REST молчал час.
# lib: jcmd прикрепляется к JVM через механизм attach и отвечает только у живого процесса: ответ VM.version —
# lib: доказательство живого потока консоли. Нет jcmd (JRE без JDK) — не судим.
console_stream_alive() {
    local dead
    if [ "${HANG_WATCH_ASSUME_RUNNING:-0}" != 1 ]; then
        dead="$(tmux list-panes -t "$TMUX_SESSION" -F '#{pane_dead}' 2>/dev/null | head -n 1 || true)"
        if [ -n "$dead" ] && [ "$dead" != 0 ]; then
            return 1
        fi
    fi
    if [ -z "$pid" ] || ! process_alive "$pid"; then
        return 0
    fi
    command -v "$JCMD" >/dev/null 2>&1 || return 0
    "$JCMD" "$pid" VM.version >/dev/null 2>&1
}

console_lost() {
    local stamp dump line
    if [ "$MODE" = check ]; then
        printf 'console\n'
        exit 0
    fi
    stamp="$(date '+%Y%m%d-%H%M%S')"
    dump="$LOG_DIR/console-$stamp.txt"
    {
        printf 'консоль не отвечает: PID %s\n' "${pid:-нет}"
        printf 'jcmd: %s\n' "$JCMD"
        "$JCMD" "$pid" Thread.print 2>&1 || printf 'jcmd не прикрепился к PID %s\n' "$pid"
        printf 'tmux: %s\n' "$(tmux list-panes -t "$TMUX_SESSION" -F '#{pane_dead} #{pane_current_command}' 2>&1 || true)"
    } >"$dump" 2>&1
    line="$(date '+%F %T') консоль мертва: pid=${pid:-нет} jcmd не ответил — перезапуск (обновление JDK на месте?)"
    printf '%s\n' "$line" >>"$HANG_LOG"
    if [ "$DRY_RUN" = 1 ]; then
        printf '%s\n' "$(date '+%F %T') перезапуск пропущен (dry-run)" >>"$HANG_LOG"
        exit 0
    fi
    restart_server
}

find_java_pid() {
    if [ -n "${HANG_WATCH_PID:-}" ]; then
        printf '%s' "$HANG_WATCH_PID"
        return 0
    fi
    local pane child next
    pane="$(tmux list-panes -t "$TMUX_SESSION" -F '#{pane_pid}' 2>/dev/null | head -n 1 || true)"
    [ -n "$pane" ] || return 1
    child="$pane"
    for _ in 1 2 3 4 5; do
        next="$(pgrep -P "$child" 2>/dev/null | head -n 1 || true)"
        [ -n "$next" ] || break
        child="$next"
    done
    printf '%s' "$child"
}

file_age() {
    local file="$1" mtime
    if [ ! -f "$file" ]; then
        printf '999999999'
        return 0
    fi
    mtime="$(stat -c %Y "$file" 2>/dev/null || true)"
    if [ -z "$mtime" ]; then
        printf '999999999'
        return 0
    fi
    printf '%s' "$(( NOW - mtime ))"
}

process_age() {
    ps -o etimes= -p "$1" 2>/dev/null | tr -d ' ' || true
}

stop_in_progress() {
    [ -f "$STOP_MARKER" ] || return 1
    local age
    age="$(file_age "$STOP_MARKER")"
    [ "$age" -le 600 ]
}

debug_paused() {
    [ -f "$DEBUG_LEASE" ] || return 1
    local first holder until since
    first="$(head -n 1 "$DEBUG_LEASE" 2>/dev/null || true)"
    IFS='|' read -r holder until since <<<"$first" || true
    case "${until:-}" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$until" -gt "$NOW" ]
}

fetch_health() {
    [ -n "$REST" ] || return 1
    "$CURL" -fsS --max-time 5 "$REST/v1/health" 2>/dev/null
}

read_heartbeat() {
    [ -f "$HEARTBEAT" ] || return 1
    local key value hb_pid="" hb_now=""
    while IFS='=' read -r key value; do
        case "$key" in
            pid) hb_pid="$value" ;;
            now_ms) hb_now="$value" ;;
        esac
    done <"$HEARTBEAT"
    case "$hb_pid" in
        ''|*[!0-9]*) return 1 ;;
    esac
    case "$hb_now" in
        ''|*[!0-9]*) return 1 ;;
    esac
    printf '%s %s' "$hb_pid" "$hb_now"
}

fallback_verdict() {
    local hb hb_pid hb_now age log_age start_age
    if hb="$(read_heartbeat)"; then
        hb_pid="${hb%% *}"
        hb_now="${hb##* }"
        if [ -z "$pid" ] || [ "$hb_pid" = "$pid" ]; then
            age=$(( NOW * 1000 - hb_now ))
            [ "$age" -lt 0 ] && age=0
            if [ "$age" -ge "$(( THRESHOLD * 1000 ))" ]; then
                printf 'hung'
            else
                printf 'ok'
            fi
            return 0
        fi
    fi
    log_age="$(file_age "$MAIN_LOG")"
    if [ "$log_age" -le "$THRESHOLD" ]; then
        printf 'ok'
        return 0
    fi
    if [ -n "$pid" ] && process_alive "$pid"; then
        start_age="$(process_age "$pid")"
        if [ -n "$start_age" ] && [ "$start_age" -le "$THRESHOLD" ]; then
            printf 'ok'
            return 0
        fi
        printf 'hung'
        return 0
    fi
    printf 'stopped'
}

restart_server() {
    local stopped=0
    if [ -n "$pid" ] && process_alive "$pid"; then
        if bash "$STOP_SCRIPT" >/dev/null 2>&1; then
            stopped=1
        fi
        if [ "$stopped" = 0 ]; then
            kill -TERM "$pid" 2>/dev/null || true
            for _ in $(seq 1 30); do
                process_alive "$pid" || break
                sleep 1
            done
            if process_alive "$pid"; then
                kill -KILL "$pid" 2>/dev/null || true
                for _ in $(seq 1 10); do
                    process_alive "$pid" || break
                    sleep 1
                done
            fi
        fi
    else
        bash "$STOP_SCRIPT" >/dev/null 2>&1 || true
    fi
    if [ "${HANG_WATCH_ASSUME_RUNNING:-0}" != 1 ]; then
        for _ in $(seq 1 30); do
            server_running || break
            sleep 1
        done
        if server_running; then
            tmux kill-session -t "$TMUX_SESSION" 2>/dev/null || true
        fi
    fi
    if bash "$START_SCRIPT" >>"$HANG_LOG" 2>&1; then
        printf '%s\n' "$(date '+%F %T') сервер перезапущен после зависания" >>"$HANG_LOG"
    else
        printf '%s\n' "$(date '+%F %T') перезапуск после зависания не удался, нужен человек" >>"$HANG_LOG"
        exit 1
    fi
}

handle_hang() {
    local stamp dump line hb hb_now
    if [ -z "$age_ms" ] && hb="$(read_heartbeat)"; then
        hb_now="${hb##* }"
        age_ms=$(( NOW * 1000 - hb_now ))
        [ "$age_ms" -lt 0 ] && age_ms=0
    fi
    stamp="$(date '+%Y%m%d-%H%M%S')"
    if [ "$MODE" = check ]; then
        printf 'hung\n'
        exit 0
    fi
    dump="$LOG_DIR/hang-$stamp.txt"
    if [ -n "$pid" ] && process_alive "$pid"; then
        "$JCMD" "$pid" Thread.print >"$dump" 2>&1 \
            || printf 'jcmd не снял дамп: PID %s\n' "$pid" >>"$dump"
    else
        printf 'процесс не найден: PID %s\n' "${pid:-нет}" >"$dump"
    fi
    line="$(date '+%F %T') зависание: pid=${pid:-нет} возраст_мс=${age_ms:-неизвестен} дамп=$dump"
    printf '%s\n' "$line" >>"$HANG_LOG"
    if [ "$DRY_RUN" = 1 ]; then
        printf '%s\n' "$(date '+%F %T') перезапуск пропущен (dry-run)" >>"$HANG_LOG"
        exit 0
    fi
    restart_server
}

finish() {
    if [ "$MODE" = check ]; then
        printf '%s\n' "$1"
    fi
    exit 0
}

pid="$(find_java_pid || true)"
if ! server_running; then
    finish stopped
fi
if stop_in_progress; then
    finish stopping
fi
if debug_paused; then
    finish debug
fi
if ! console_stream_alive; then
    console_lost
fi

verdict=""
age_ms=""
health="$(fetch_health || true)"
if [ -n "$health" ]; then
    verdict="$(printf '%s' "$health" | sed -n 's/.*"verdict":"\([a-z]*\)".*/\1/p' | head -n 1)"
    age_ms="$(printf '%s' "$health" | sed -n 's/.*"age_ms":\([-0-9]*\).*/\1/p' | head -n 1)"
    case "$verdict" in
        ok|debug)
            finish "$verdict"
            ;;
        hung) ;;
        *)
            verdict=""
            ;;
    esac
fi
if [ -z "$verdict" ]; then
    verdict="$(fallback_verdict)"
fi
case "$verdict" in
    hung)
        handle_hang
        ;;
    *)
        finish "$verdict"
        ;;
esac
