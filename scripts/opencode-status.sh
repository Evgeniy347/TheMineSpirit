#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
actions="${1:-5}"

run_dir="$(ls -d TestResults/opencode/2026* TestResults/opencode/20[3-9]* 2>/dev/null | sort | tail -n 1)"
[ -z "$run_dir" ] && { echo "Запусков opencode ещё не было"; exit 0; }
attempt="$(ls "$run_dir"/attempt-*.jsonl 2>/dev/null | sort -V | tail -n 1)"

if pgrep -f "opencode run -m" >/dev/null; then state="работает"; else state="не запущен"; fi
age=$(( $(date +%s) - $(stat -c %Y "$attempt" "${attempt%.jsonl}.err" 2>/dev/null | sort -n | tail -n 1) ))
echo "opencode: $state; последняя запись $age с назад; попытка $(basename "$attempt" .jsonl); $(tmux ls 2>/dev/null | cut -d: -f1 | grep '^oc-' | tr '\n' ' ')"

python3 -I - "$attempt" "$actions" <<'PY'
import json, sys
path, limit = sys.argv[1], int(sys.argv[2])
steps = []
for line in open(path, encoding="utf-8", errors="replace"):
    try:
        event = json.loads(line)
    except ValueError:
        continue
    part = event.get("part") or {}
    if event.get("type") == "tool_use":
        tool = part.get("tool", "?")
        data = (part.get("state") or {}).get("input") or {}
        target = data.get("command") or data.get("filePath") or data.get("pattern") or data.get("description") or ""
        lines = str(target).strip().splitlines()
        steps.append(f"{tool}: {lines[0][:110] if lines else ''}")
    elif event.get("type") == "text" and (part.get("text") or "").strip():
        steps.append("ответ: " + part["text"].strip().splitlines()[0][:110])
    elif event.get("type") == "error":
        steps.append("ОШИБКА: " + json.dumps(event.get("error"), ensure_ascii=False)[:110])
print(f"действий в попытке: {len(steps)}")
for step in steps[-limit:]:
    print("  " + step)
PY
echo "git: $(git log -1 --format='%h %s' | cut -c1-90); изменённых файлов: $(git status --short | wc -l)"
