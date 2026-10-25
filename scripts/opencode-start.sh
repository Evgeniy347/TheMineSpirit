#!/usr/bin/env bash
set -euo pipefail
[ $# -ge 4 ] || { sed -n 2,5p "$0" >&2; exit 2; }
worktree="$1" model="$2" task="$3" message="$4" session="${5:-}"
main="$(cd "$(dirname "$0")/.." && pwd)"
[ "$model" = auto ] && model="$("$main/scripts/opencode-pick-model.sh")"
results="$main/TestResults/opencode"
mkdir -p "$results"
[ -d "$worktree" ] || { echo "Нет worktree: $worktree" >&2; exit 2; }
[ -s "$message" ] || { echo "Пустой или отсутствующий файл сообщения: $message" >&2; exit 2; }
cp "$message" "$results/$task.msg"
tmux_name="oc-$task"
if tmux has-session -t "$tmux_name" 2>/dev/null; then
    echo "tmux-сессия $tmux_name уже есть — задание, видимо, идёт" >&2
    exit 3
fi
resume=()
[ -n "$session" ] && resume=(-s "$session")
printf -v command 'cd %q && OPENCODE_ALLOW_DIRTY=1 OPENCODE_REPO_DIR=%q OPENCODE_MODEL=%q OPENCODE_LOG_ROOT=%q nice -n 19 ionice -c3 %q %s "$(cat %q)" > %q 2>&1; echo EXIT=$? >> %q' \
    "$worktree" "$worktree" "$model" "$results" "$main/scripts/opencode-task.sh" "${resume[*]:-}" "$results/$task.msg" "$results/$task.out" "$results/$task.out"
tmux new-session -d -s "$tmux_name" "$command"
echo "$task:$tmux_name" >> "$results/watch.list"
echo "запущено: $tmux_name ($model) в $worktree"
