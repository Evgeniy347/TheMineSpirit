#!/usr/bin/env bash
PY=python3; command -v python3 >/dev/null 2>&1 && python3 -c "" 2>/dev/null || PY=python
export PYTHONUTF8=1
exec "$PY" "$(dirname "$0")/logdb/logq.py" "$@"
