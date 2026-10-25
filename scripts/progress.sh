#!/usr/bin/env bash
todo="$(cd "$(dirname "$0")/.." && pwd)/todo.md"
closed=$(grep -m1 -oE '^Прогресс: сделано [0-9]+' "$todo" | grep -oE '[0-9]+$')
closed=${closed:-0}
open=$(grep -cE '^\s*- \[ \]' "$todo")
total=$((closed + open))
[ "$total" -eq 0 ] && { echo "0% (0 из 0)"; exit 0; }
echo "$((closed * 100 / total))% ($closed из $total)"
