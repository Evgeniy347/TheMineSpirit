#!/usr/bin/env bash
# Подделка opencode для тестов brain.py (образец: scripts/testing/fake-opencode.sh).
# FAKE_STATE=путь       — счётчик запусков в $FAKE_STATE, аргументы и каталог в $FAKE_STATE.args (обязательна)
# FAKE_REPLY=текст      — текст ответа модели (по умолчанию валидный skip-JSON)
# FAKE_ERROR=json|stderr — сбой провайдера: событие error в stdout или строка level=ERROR в stderr, затем зависание
# FAKE_ERROR_TEXT=текст — текст сбоя (по умолчанию «Free usage exceeded»)
# FAKE_SILENT=1         — молчит и висит (дочерний процесс sleep, чтобы проверить убийство группы)
# FAKE_GARBAGE=1        — печатает мусор, не JSON, и выходит с кодом 0
# FAKE_EXIT=N           — выходит с кодом N без вывода
# FAKE_TOOL=reject|executed — событие tool_use: отклонённое (status error) или выполненное (status completed)
# FAKE_BAD_MODEL=m      — для модели m (аргумент -m) сбой провайдера; для остальных работает
# FAKE_PROBE_FAIL=1     — проба «ответь OK» сообщает о сбое провайдера
state="${FAKE_STATE:?FAKE_STATE обязателен}"
args="$*"
count=$(cat "$state" 2>/dev/null || echo 0)
echo $((count + 1)) > "$state"
model=""
prev=""
for a in "$@"; do
    [ "$prev" = "-m" ] && model="$a"
    prev="$a"
done
{
    echo "ARGS: $args"
    echo "CWD: $PWD"
    echo "PID: $$"
    [ -f "$PWD/.opencode/agent/strategist.md" ] && echo "AGENT_FILE: $(tr '\n' ' ' < "$PWD/.opencode/agent/strategist.md" | cut -c1-300)"
} >> "$state.args"
echo $$ > "$state.pid"

fail_provider() {
    if [ "${FAKE_ERROR:-json}" = "stderr" ]; then
        echo "timestamp=2026-10-11T11:00:00Z level=ERROR message=\"stream error\" providerID=opencode modelID=$model error.error=\"AI_APICallError: ${FAKE_ERROR_TEXT:-Free usage exceeded}\"" >&2
    else
        echo "{\"type\":\"error\",\"sessionID\":\"ses_fake\",\"error\":{\"name\":\"APIError\",\"data\":{\"message\":\"${FAKE_ERROR_TEXT:-Free usage exceeded}\"}}}"
    fi
    exec sleep 600
}

case "$args" in
    *"ответь OK"*)
        [ -n "${FAKE_PROBE_FAIL:-}" ] && fail_provider
        echo '{"type":"text","sessionID":"ses_probe","part":{"type":"text","text":"OK"}}'
        exit 0
        ;;
esac

[ -n "${FAKE_BAD_MODEL:-}" ] && [ "$model" = "$FAKE_BAD_MODEL" ] && fail_provider
[ -n "${FAKE_ERROR:-}" ] && fail_provider
if [ -n "${FAKE_SILENT:-}" ]; then
    sleep 600 &
    echo $! > "$state.childpid"
    wait
    exit 0
fi
[ -n "${FAKE_GARBAGE:-}" ] && { echo "это вообще не JSON ><"; echo "{{{{"; exit 0; }
[ -n "${FAKE_EXIT:-}" ] && exit "$FAKE_EXIT"

echo '{"type":"step_start","sessionID":"ses_fake","part":{"type":"step-start"}}'
case "${FAKE_TOOL:-}" in
    reject)
        echo '{"type":"tool_use","sessionID":"ses_fake","part":{"type":"tool","tool":"bash","state":{"status":"error","input":{"command":"ls"},"error":"The user rejected permission to use this specific tool call."}}}'
        [ -z "${FAKE_REPLY+x}" ] && exit 0
        ;;
    executed)
        echo '{"type":"tool_use","sessionID":"ses_fake","part":{"type":"tool","tool":"bash","state":{"status":"completed","input":{"command":"ls"},"output":"файлы"}}}'
        ;;
esac
default_reply='{"reason":"нечего делать","skip":true}'
reply="${FAKE_REPLY-$default_reply}"
python3 -I -c '
import json, sys
print(json.dumps({"type": "text", "sessionID": "ses_fake", "part": {"type": "text", "text": sys.argv[1]}}, ensure_ascii=False))
' "$reply"
echo '{"type":"step_finish","sessionID":"ses_fake","part":{"type":"step-finish","reason":"stop"}}'
