#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
models="${OPENCODE_PICK_MODELS:-opencode/longcat-2.5-preview-free opencode/mimo-v2.6-flash-free opencode/muse-spark-1.3-contributor-free opencode/big-pickle opencode/nemotron-3-ultra-free opencode/step-5-preview-free opencode/ling-3.1-flash-free}"
window="${OPENCODE_PICK_WINDOW:-60}"
session_cost="${OPENCODE_PICK_SESSION_COST:-5}"
failures="TestResults/opencode/failures.log"
since="$(date -u -d "-$window min" +%Y-%m-%dT%H:%M:%S)"
best="" best_score=""
for model in $models; do
    id="${model#opencode/}"
    recent=0
    [ -f "$failures" ] && recent=$(awk -F'\t' -v since="$since" -v id="modelID=$id" \
        'substr($1, 1, 19) >= since && index($0, id) { n++ } END { print n + 0 }' "$failures")
    running=$(ps -eo args 2>/dev/null | grep -c "[o]pencode run -m $model " || true)
    score=$((recent + session_cost * running))
    [ "${1:-}" = "--table" ] && printf '%-42s сбоев за %s мин: %-4s процессов: %-2s оценка: %s\n' "$model" "$window" "$recent" "$running" "$score"
    if [ -z "$best_score" ] || [ "$score" -lt "$best_score" ]; then
        best="$model" best_score="$score"
    fi
done
[ "${1:-}" = "--table" ] || echo "$best"
