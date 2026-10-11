#!/usr/bin/env bash
# Общий прогресс плана по todo.md: отмеченные пункты [x] / все пункты-чекбоксы. Печатает «N% (x из всего)».
todo="$(cd "$(dirname "$0")/.." && pwd)/todo.md"
closed=$(grep -cE '^\s*- \[x\]' "$todo")
open=$(grep -cE '^\s*- \[ \]' "$todo")
total=$((closed + open))
[ "$total" -eq 0 ] && { echo "0% (0 из 0)"; exit 0; }
echo "$((closed * 100 / total))% ($closed из $total)"
