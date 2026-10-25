#!/usr/bin/env bash
cd "$(dirname "$0")/../.." || exit 2
hooks="$PWD/scripts/git-hooks"
work="$PWD/TestResults/history-hooks-check"
rm -rf "$work"
mkdir -p "$work/repo"
cd "$work/repo" || exit 2
git init -q -b main .
git config user.name check
git config user.email check@example.invalid
git config core.hooksPath "$hooks"
failed=0
commit() { echo "$1" >"f-$1.txt"; git add "f-$1.txt"; git commit -q --no-verify -m "chore: $1" >/dev/null 2>&1; }
check() {
    local name="$1" want="$2"
    shift 2
    "$@" >"$work/$name.out" 2>&1
    local code=$?
    local got=ok
    [ "$code" -ne 0 ] && got=отказ
    if [ "$got" = "$want" ]; then echo "OK $name: $got"; else echo "FAIL $name: $got (ожидал $want)"; sed 's/^/  /' "$work/$name.out"; failed=1; fi
}
commit база
git checkout -q -b task/a
commit задание
git checkout -q main
commit main-вперёд
git checkout -q task/a
check rebase_task_onto_main ok git rebase -q main
check task_contains_main ok git merge-base --is-ancestor main task/a
git checkout -q main
check amend_main отказ git commit -q --amend -m "chore: правка"
git checkout -q -b other
commit чужое
check rebase_other_branch отказ git rebase task/a
git rebase --abort >/dev/null 2>&1
git checkout -q task/a
check rebase_task_onto_other отказ git rebase other
git rebase --abort >/dev/null 2>&1
check reset_task_back_not_onto_main отказ git reset -q --hard HEAD~1
git checkout -q main
check ff_merge_task ok git merge -q --ff-only task/a
exit $failed
