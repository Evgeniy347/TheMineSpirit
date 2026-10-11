#!/usr/bin/env bash
# Подделка opencode для проверки scripts/opencode-task.sh.
# FAKE_FAILURES=N   — первые N запусков сообщают о сбое провайдера и висят без дочерних процессов (как opencode в retry);
#                     FAKE_ERROR_CHANNEL=json (по умолчанию) — событием error в stdout, stderr — строкой лога level=ERROR.
# FAKE_SILENT=N     — первые N запусков молча ждут модель (без вывода и без дочерних процессов).
# FAKE_BUSY_TOOL=S  — «команда» работает S секунд без вывода (дочерний процесс), это не сбой.
# FAKE_TOOL_READ=1  — модель «читает» файл, где упомянута ошибка квоты; это не сбой.
# FAKE_INFO_LOG=1   — в логе INFO команда со словами «rate limit»; это не сбой.
state="${FAKE_STATE:?}"
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
echo '{"type":"text","sessionID":"ses_fake123","part":{"type":"text","text":"готово"}}'
