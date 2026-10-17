#!/usr/bin/env bash
# Подделка opencode для проверки scripts/opencode-task.sh.
# FAKE_FAILURES=N   — первые N запусков сообщают о сбое провайдера и висят без дочерних процессов (как opencode в retry);
#                     FAKE_ERROR_CHANNEL=json (по умолчанию) — событием error в stdout, stderr — строкой лога level=ERROR.
# FAKE_NETWORK=N    — первые N запусков сообщают о сетевом сбое: json — «Cannot connect», stderr — «Endpoint is unavailable».
# FAKE_SILENT=N     — первые N запусков молча ждут модель (без вывода и без дочерних процессов).
# FAKE_BUSY_TOOL=S  — «команда» работает S секунд без вывода (дочерний процесс), это не сбой.
# FAKE_TOOL_READ=1  — модель «читает» файл, где упомянута ошибка квоты; это не сбой.
# FAKE_INFO_LOG=1   — в логе INFO команда со словами «rate limit»; это не сбой.
# FAKE_PROBE_FAILURES=N — первые N проб ожидания (запуск с текстом «ответь OK») сообщают о сбое провайдера и висят
#                     до тайм-аута; пробы считаются отдельно от запусков в $FAKE_STATE.probes.
# FAKE_UNFINISHED=N — первые N запусков заканчивают ход оборванной фразой «Теперь посмотрю тесты:».
# FAKE_HEARTBEAT=S — S секунд без вывода, но с пульсом scripts/test.sh (TestResults/test-running в --dir): не молчание.
# FAKE_LOOP=N      — первые N запусков повторяют одну команду 30 раз и висят (зацикливание модели).
# FAKE_COMPACTION=N — первые N запусков сжимают контекст (лог agent=compaction и синтетическое «Continue…») и работают дальше.
state="${FAKE_STATE:?}"
case "$*" in
    *"ответь OK"*)
        probes=$(cat "$state.probes" 2>/dev/null || echo 0)
        echo $((probes + 1)) > "$state.probes"
        echo "PROBE: $PWD" >> "$state.args"
        if [ "$probes" -lt "${FAKE_PROBE_FAILURES:-0}" ]; then
            echo '{"type":"error","sessionID":"ses_probe","error":{"name":"APIError","data":{"message":"Free usage exceeded"}}}'
            exec sleep 600
        fi
        echo '{"type":"text","sessionID":"ses_probe","part":{"type":"text","text":"OK"}}'
        exit 0
        ;;
esac
count=$(cat "$state" 2>/dev/null || echo 0)
echo $((count + 1)) > "$state"
echo "ARGS: $*" >> "$state.args"
echo '{"type":"step_start","sessionID":"ses_fake123","part":{"sessionID":"ses_fake123"}}'
if [ -n "${FAKE_TOOL_READ:-}" ]; then
    echo '{"type":"tool_use","sessionID":"ses_fake123","part":{"tool":"read","state":{"output":"перезапуск при «Free usage exceeded» (5, 5, 10, 30, 60 с)"}}}'
fi
if [ -n "${FAKE_INFO_LOG:-}" ]; then
    echo 'timestamp=2026-10-11T11:00:00Z level=INFO message=evaluated permission=bash pattern="grep -i \"rate limit\" docs"' >&2
fi
if [ -n "${FAKE_HEARTBEAT:-}" ]; then
    dir=$(printf '%s
' "$@" | grep -A1 -x -- '--dir' | tail -n 1)
    mkdir -p "$dir/TestResults"
    for _ in $(seq 1 "$FAKE_HEARTBEAT"); do touch "$dir/TestResults/test-running"; sleep 1; done
    rm -f "$dir/TestResults/test-running"
fi
if [ -n "${FAKE_BUSY_TOOL:-}" ]; then
    sleep "$FAKE_BUSY_TOOL"
fi
if [ "$count" -lt "${FAKE_SILENT:-0}" ]; then
    exec sleep 600
fi
if [ "$count" -lt "${FAKE_FAILURES:-0}" ]; then
    if [ "${FAKE_ERROR_CHANNEL:-json}" = "stderr" ]; then
        echo 'timestamp=2026-10-11T11:00:00Z level=ERROR message="stream error" providerID=opencode modelID=big-pickle error.error="AI_APICallError: Rate limit exceeded. Please try again later."' >&2
    else
        echo '{"type":"error","sessionID":"ses_fake123","error":{"name":"APIError","data":{"message":"Free usage exceeded"}}}'
    fi
    exec sleep 600
fi
if [ "$count" -lt "${FAKE_NETWORK:-0}" ]; then
    if [ "${FAKE_ERROR_CHANNEL:-json}" = "stderr" ]; then
        echo 'timestamp=2026-10-11T11:00:00Z level=ERROR message="stream error" providerID=opencode modelID=big-pickle error.error="AI_APICallError: Endpoint is unavailable"' >&2
    else
        echo '{"type":"error","sessionID":"ses_fake123","error":{"name":"APIError","data":{"message":"Cannot connect to API: fetch failed"}}}'
    fi
    exec sleep 600
fi
if [ "$count" -lt "${FAKE_LOOP:-0}" ]; then
    for i in $(seq 1 30); do
        echo '{"type":"tool_use","sessionID":"ses_fake123","part":{"tool":"bash","state":{"status":"completed","input":{"command":"git diff -- a.java"},"output":""}}}'
    done
    exec sleep 600
fi
if [ "$count" -lt "${FAKE_COMPACTION:-0}" ]; then
    echo 'timestamp=2026-10-15T16:26:04Z level=INFO run=1 message=stream providerID=opencode modelID=big-pickle session.id=ses_fake123 small=false agent=compaction mode=primary' >&2
    echo '{"type":"text","sessionID":"ses_fake123","part":{"type":"text","metadata":{"compaction_continue":true},"synthetic":true,"text":"Continue if you have next steps."}}'
    exec sleep 600
fi
if [ "$count" -lt "${FAKE_UNFINISHED:-0}" ]; then
    echo '{"type":"text","sessionID":"ses_fake123","part":{"type":"text","text":"Теперь посмотрю тесты:"}}'
    exit 0
fi
echo '{"type":"text","sessionID":"ses_fake123","part":{"type":"text","text":"готово"}}'
