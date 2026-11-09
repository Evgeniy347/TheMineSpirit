#!/usr/bin/env bash
# Живой взгляд на духов и мир; команды — scripts/live.sh help (docs/LIVE-DEBUG.md).
PY=python3; command -v python3 >/dev/null 2>&1 && python3 -c "" 2>/dev/null || PY=python
export PYTHONUTF8=1
exec "$PY" "$(dirname "$0")/lead/live.py" "$@"
