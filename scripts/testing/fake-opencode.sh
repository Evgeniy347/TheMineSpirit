#!/usr/bin/env bash
# Подделка opencode для проверки scripts/opencode-task.sh.
# FAKE_FAILURES=N  — первые N запусков сообщают об ошибке квоты и висят (как opencode в retry);
#                    FAKE_ERROR_CHANNEL=json (по умолчанию) — событием error в stdout, stderr — строкой лога.
# FAKE_TOOL_READ=1 — модель «читает» файл, где упомянута ошибка квоты; это не сбой.
# FAKE_HANG=1      — молчит и висит.
state="${FAKE_STATE:?}"
count=$(cat "$state" 2>/dev/null || echo 0)
echo $((count + 1)) > "$state"
echo "ARGS: $*" >> "$state.args"
echo '{"type":"step_start","sessionID":"ses_fake123","part":{"sessionID":"ses_fake123"}}'
[ -n "${FAKE_HANG:-}" ] && sleep 600
if [ -n "${FAKE_TOOL_READ:-}" ]; then
    echo '{"type":"tool_use","sessionID":"ses_fake123","part":{"tool":"read","state":{"output":"перезапуск при «Free usage exceeded» (5, 5, 10, 30, 60 с)"}}}'
fi
if [ "$count" -lt "${FAKE_FAILURES:-0}" ]; then
    if [ "${FAKE_ERROR_CHANNEL:-json}" = "stderr" ]; then
        echo 'ERROR service=session.processor error=Free usage exceeded, add credits' >&2
    else
        echo '{"type":"error","sessionID":"ses_fake123","error":{"name":"APIError","data":{"message":"Free usage exceeded"}}}'
    fi
    sleep 600
fi
echo '{"type":"text","sessionID":"ses_fake123","part":{"type":"text","text":"готово"}}'
