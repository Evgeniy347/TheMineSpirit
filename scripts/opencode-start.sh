#!/usr/bin/env bash
# Запуск задания opencode в tmux для главного агента: сессия переживает обрыв ssh, вывод — в TestResults/opencode/<задание>.out,
# в конце строка EXIT=<код>, задание добавляется в watch.list (его читает монитор главного агента).
# Использование: scripts/opencode-start.sh <worktree> <модель|auto> <задание> <файл-сообщения> [<id сессии opencode>]
# Пример: scripts/opencode-start.sh /opt/minecraft/wt/12-mine opencode/big-pickle task12r4 /tmp/msg.txt ses_ed40…
set -euo pipefail
[ $# -ge 4 ] || { sed -n 2,5p "$0" >&2; exit 2; }
worktree="$1" model="$2" task="$3" message="$4" session="${5:-}"
main="$(cd "$(dirname "$0")/.." && pwd)"
# Модель auto — выбрать по свежим сбоям и загрузке (scripts/opencode-pick-model.sh).
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
# Надзор всегда берётся из main: копии скрипта в worktree устаревают, а правки надзора нужны всем потокам сразу.
# Исполнитель и всё, что он запускает (gradle, java GameTest, find…), — с низшим приоритетом CPU и диска: на сервере
# идут записи видео и игровой сервер (владелец 16.10). Дочерние процессы наследуют nice и ionice.
printf -v command 'cd %q && OPENCODE_ALLOW_DIRTY=1 OPENCODE_REPO_DIR=%q OPENCODE_MODEL=%q OPENCODE_LOG_ROOT=%q nice -n 19 ionice -c3 %q %s "$(cat %q)" > %q 2>&1; echo EXIT=$? >> %q' \
    "$worktree" "$worktree" "$model" "$results" "$main/scripts/opencode-task.sh" "${resume[*]:-}" "$results/$task.msg" "$results/$task.out" "$results/$task.out"
tmux new-session -d -s "$tmux_name" "$command"
echo "$task:$tmux_name" >> "$results/watch.list"
echo "запущено: $tmux_name ($model) в $worktree"
