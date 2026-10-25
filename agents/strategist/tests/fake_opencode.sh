#!/usr/bin/env bash
state="${FAKE_STATE:?FAKE_STATE обязателен}"
if [ "$1" = "--version" ]; then
    echo "fake-opencode 9.9.9"
    exit 0
fi
if [ "$1 $2" = "debug agent" ]; then
    echo "DEBUG_ENV: PERMISSION=${OPENCODE_PERMISSION-unset} CONFIG=${OPENCODE_CONFIG-unset} CONTENT=${OPENCODE_CONFIG_CONTENT-unset} DIR=${OPENCODE_CONFIG_DIR-unset} DATADIR=${XDG_DATA_HOME-unset}" >> "$state.args"
    case "${FAKE_AGENT:-}" in
        garbage) echo "это не JSON"; exit 0 ;;
        allow) last='{"permission":"bash","pattern":"*","action":"allow"}' ;;
        late_allow) last='{"permission":"read","pattern":"*.txt","action":"allow"}' ;;
        *) last='{"permission":"external_directory","pattern":"/tmp/x/*","action":"allow"}' ;;
    esac
    echo "timestamp=x level=INFO message=noise"
    echo '{"name":"strategist","mode":"primary","permission":[{"permission":"*","pattern":"*","action":"allow"},{"permission":"*","pattern":"*","action":"deny"},{"permission":"read","pattern":"*","action":"ask"},{"permission":"edit","pattern":"*","action":"ask"},{"permission":"bash","pattern":"*","action":"ask"},'"$last"'],"tools":{"invalid":true,"bash":true,"read":true,"edit":true,"write":true,"glob":false,"webfetch":false}}'
    exit 0
fi
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
    echo "ENV: PERMISSION=${OPENCODE_PERMISSION-unset} CONFIG=${OPENCODE_CONFIG-unset} CONTENT=${OPENCODE_CONFIG_CONTENT-unset} DIR=${OPENCODE_CONFIG_DIR-unset} DATADIR=${XDG_DATA_HOME-unset}"
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
        [ -z "${FAKE_REPLY+x}" ] && [ -z "${FAKE_REPLY_PARTS+x}" ] && exit 0
        ;;
    executed)
        echo '{"type":"tool_use","sessionID":"ses_fake","part":{"type":"tool","tool":"bash","state":{"status":"completed","input":{"command":"ls"},"output":"файлы"}}}'
        ;;
esac
if [ -n "${FAKE_REPLY_PARTS:-}" ]; then
    python3 -I -c '
import json, sys
for piece in json.loads(sys.argv[1]):
    print(json.dumps({"type": "text", "sessionID": "ses_fake", "part": {"type": "text", "text": piece}}, ensure_ascii=False))
' "$FAKE_REPLY_PARTS"
else
    default_reply='{"reason":"нечего делать","skip":true}'
    reply="${FAKE_REPLY-$default_reply}"
    python3 -I -c '
import json, sys
print(json.dumps({"type": "text", "sessionID": "ses_fake", "part": {"type": "text", "text": sys.argv[1]}}, ensure_ascii=False))
' "$reply"
fi
echo '{"type":"step_finish","sessionID":"ses_fake","part":{"type":"step-finish","reason":"stop"}}'
