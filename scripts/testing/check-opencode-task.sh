#!/usr/bin/env bash
cd "$(dirname "$0")/../.."
work="TestResults/opencode-check"
rm -rf "$work"
mkdir -p "$work"
export OPENCODE_BIN="$PWD/scripts/testing/fake-opencode.sh"
export CODEGRAPH_BIN="$PWD/scripts/testing/fake-codegraph.sh"
export OPENCODE_LOG_ROOT="$PWD/$work/logs"
export OPENCODE_ALLOW_DIRTY=1
export OPENCODE_DIRTY_SHORT_ANSWER=0
export OPENCODE_TASK_FILE="$PWD/$work/task.md"
export OPENCODE_NO_SWEEP=1
failures_log="$OPENCODE_LOG_ROOT/failures.log"

check() {
    local name="$1" expected_code="$2" expected_runs="$3"
    shift 3
    export FAKE_STATE="$PWD/$work/$name.state"
    local started=$(date +%s)
    env "$@" scripts/opencode-task.sh "${task_args[@]}" "тестовое задание" >"$work/$name.out" 2>&1
    local code=$? runs=$(cat "$FAKE_STATE" 2>/dev/null || echo 0) took=$(( $(date +%s) - started ))
    local verdict=OK
    [ "$code" = "$expected_code" ] && [ "$runs" = "$expected_runs" ] || verdict=FAIL
    echo "$verdict $name: код=$code (ожидал $expected_code), запусков=$runs (ожидал $expected_runs), $took с"
}

task_args=()
failures_before=$(cat "$failures_log" 2>/dev/null | wc -l)
check json_error_twice 0 3 FAKE_FAILURES=2
grep -c -- "-s ses_fake123 " "$work/json_error_twice.state.args" | sed 's/^/  перезапусков с -s ses_fake123 (ровно один id): /'
grep -c "Продолжай выполнение" "$work/json_error_twice.state.args" | sed 's/^/  перезапусков с текстом продолжения: /'
check stderr_rate_limit 0 2 FAKE_FAILURES=1 FAKE_ERROR_CHANNEL=stderr
check network_error_json 0 2 FAKE_NETWORK=1 OPENCODE_IDLE_LIMIT=6
check network_error_stderr 0 2 FAKE_NETWORK=1 FAKE_ERROR_CHANNEL=stderr OPENCODE_IDLE_LIMIT=6
for net_case in network_error_json network_error_stderr; do
    if grep -q "сбой провайдера, перезапуск" "$work/$net_case.out"; then
        echo "OK $net_case: сетевой сбой опознан как сбой провайдера, а не молчание"
    else
        echo "FAIL $net_case: в $net_case.out нет «сбой провайдера, перезапуск» — сеть не опознана"
    fi
done
check silent_model 0 2 FAKE_SILENT=1 OPENCODE_IDLE_LIMIT=6
check busy_tool_is_not_silence 0 1 FAKE_BUSY_TOOL=15 OPENCODE_IDLE_LIMIT=6
check heartbeat_is_not_silence 0 1 FAKE_HEARTBEAT=15 OPENCODE_IDLE_LIMIT=6 OPENCODE_NO_PGREP=1
check silent_without_pgrep 0 2 FAKE_SILENT=1 OPENCODE_IDLE_LIMIT=6 OPENCODE_NO_PGREP=1
check tool_read_mentions_error 0 1 FAKE_TOOL_READ=1
check info_log_mentions_limit 0 1 FAKE_INFO_LOG=1
check unfinished_answer_continues 0 3 FAKE_UNFINISHED=2
check unfinished_answer_limit 0 4 FAKE_UNFINISHED=99 OPENCODE_UNFINISHED_LIMIT=3
check compaction_restarts_with_reminder 0 2 FAKE_COMPACTION=1
check loop_restarts_with_prompt 0 2 FAKE_LOOP=1
grep -c "зацикливание" "$work/loop_restarts_with_prompt.state.args" | sed 's/^/  продолжений с напоминанием о зацикливании: /'
check loop_limit_stops 76 4 FAKE_LOOP=99 OPENCODE_LOOP_LIMIT=3
grep -q "Контекст сессии только что сжат" "$work/compaction_restarts_with_reminder.state.args" && v=OK || v=FAIL
echo "$v compaction_restarts_with_reminder: второй запуск напоминает перечитать task.md и progress.md"
[ "$(cat "$OPENCODE_TASK_FILE" 2>/dev/null)" = "тестовое задание" ] && v=OK || v=FAIL
echo "$v task_file_saved: задание сохранено в TestResults/task.md"
dirty_repo="$PWD/$work/dirty-repo"
git init -q "$dirty_repo" && echo "чужая работа" > "$dirty_repo/foreign.txt"
check dirty_tree_new_task 3 0 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=(-s ses_existing)
check dirty_tree_continue_session 0 1 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=()
edited_repo="$PWD/$work/edited-repo"
git init -q "$edited_repo" && echo v1 > "$edited_repo/a.txt" && git -C "$edited_repo" add a.txt     && git -C "$edited_repo" -c user.email=t@t -c user.name=t commit -qm init --no-verify && echo v2 > "$edited_repo/a.txt"
check dirty_short_answer_continues 0 2 OPENCODE_REPO_DIR="$edited_repo" OPENCODE_DIRTY_SHORT_ANSWER=600 OPENCODE_UNFINISHED_LIMIT=1
check exhausted 75 6 FAKE_FAILURES=99 OPENCODE_WAIT_LIMIT=0
grep "перезапуск\|Остановлено" "$work/exhausted.out" | sed 's/^/  /'
failures_after=$(wc -l < "$failures_log")
echo "  строк добавлено в failures.log: $((failures_after - failures_before)) (ожидаю 2+1+2+1+2+3+6 = 17: сбои провайдера, сетевые сбои, молчание, оборванные ответы, исчерпание; плюс записи зацикливания)"
tail -n 1 "$failures_log" | sed 's/^/  последняя: /'

wait_env=(OPENCODE_RETRY_DELAYS="1 1 1 1 1" OPENCODE_PROBE_INTERVAL=2 OPENCODE_PROBE_TIMEOUT=3)
check_probes() {
    local name="$1" min="$2" max="$3" probes
    probes=$(cat "$work/$name.state.probes" 2>/dev/null || echo 0)
    local verdict=FAIL
    [ "$probes" -ge "$min" ] && [ "$probes" -le "$max" ] && verdict=OK
    echo "$verdict $name: проб=$probes (ожидал $min..$max)"
}
failures_before=$(wc -l < "$failures_log")
check provider_returns_during_wait 0 7 FAKE_FAILURES=6 FAKE_PROBE_FAILURES=2 OPENCODE_WAIT_LIMIT=60 "${wait_env[@]}"
check_probes provider_returns_during_wait 3 3
last_run="$(grep '^ARGS: ' "$work/provider_returns_during_wait.state.args" | tail -n 1)"
case "$last_run" in
    *"-s ses_fake123 "*"Продолжай выполнение"*) echo "OK provider_returns_during_wait: после возврата продолжена та же сессия (-s ses_fake123, текст продолжения)" ;;
    *) echo "FAIL provider_returns_during_wait: последний запуск не продолжает сессию: ${last_run:0:120}" ;;
esac
probe_dir_used="$(grep '^PROBE: ' "$work/provider_returns_during_wait.state.args" | sort -u | sed 's/^PROBE: //')"
case "$probe_dir_used" in
    "$PWD"*) echo "FAIL provider_returns_during_wait: проба шла внутри репозитория: $probe_dir_used" ;;
    *) echo "OK provider_returns_during_wait: проба шла вне репозитория ($probe_dir_used)" ;;
esac
new_lines="$(tail -n +$((failures_before + 1)) "$failures_log")"
waits=$(grep -cP 'ожидание провайдера	' <<<"$new_lines"); back=$(grep -c 'провайдер вернулся' <<<"$new_lines")
[ "$waits" = 2 ] && [ "$back" = 1 ] && v=OK || v=FAIL
echo "$v provider_returns_during_wait: в failures.log проб ожидания=$waits (ожидал 2), «провайдер вернулся»=$back (ожидал 1)"
failures_before=$(wc -l < "$failures_log")
check wait_limit_exhausted 75 6 FAKE_FAILURES=99 FAKE_PROBE_FAILURES=99 OPENCODE_WAIT_LIMIT=14 "${wait_env[@]}"
check_probes wait_limit_exhausted 2 4
grep "жду провайдера\|предел ожидания" "$work/wait_limit_exhausted.out" | sed 's/^/  /'
gave_up=$(tail -n +$((failures_before + 1)) "$failures_log" | grep -c 'ожидание провайдера исчерпано')
[ "$gave_up" = 1 ] && v=OK || v=FAIL
echo "$v wait_limit_exhausted: в failures.log «ожидание провайдера исчерпано»=$gave_up (ожидал 1)"
sweep_count="$PWD/$work/sweep.count"
sweep_fake="$PWD/$work/fake-sweep.sh"
rm -f "$sweep_count"
printf '#!/usr/bin/env bash\nprintf x >> "%s"\n' "$sweep_count" >"$sweep_fake"
chmod +x "$sweep_fake"
check sweep_runs_at_start_and_finish 0 1 OPENCODE_NO_SWEEP= OPENCODE_ORPHAN_SWEEP_WIN="$sweep_fake"
sweeps=$(wc -c < "$sweep_count" 2>/dev/null || echo 0)
[ "$sweeps" = 2 ] && v=OK || v=FAIL
echo "$v sweep_runs_at_start_and_finish: вызовов уборки=$sweeps (ожидал 2 — начало и завершение)"
fake_codegraph="$PWD/scripts/testing/fake-codegraph.sh"
cg_seed_repo="$PWD/$work/cg-seed-repo"
git init -q "$cg_seed_repo"
printf 'class A {}\n' >"$cg_seed_repo/A.java"
cg_seed_dir="$PWD/$work/cg-seed"
mkdir -p "$cg_seed_dir"
printf 'SEED' >"$cg_seed_dir/codegraph.db"
cg_state="$PWD/$work/cg-seed.state"
rm -f "$cg_state"*
check codegraph_seed_copies_and_full_scans 0 1 OPENCODE_REPO_DIR="$cg_seed_repo" CODEGRAPH_BIN="$fake_codegraph" FAKE_CODEGRAPH_STATE="$cg_state" OPENCODE_CODEGRAPH_SEED="$cg_seed_dir"
cg_db="$(cat "$cg_seed_repo/.codegraph/codegraph.db" 2>/dev/null || echo missing)"
if [ "$cg_db" = SEED ] && grep -q '^ARGS: sync ' "$cg_state" && ! grep -q '^ARGS: init ' "$cg_state" && grep -q '^GIT_DIR: .\+' "$cg_state"; then
    echo "OK codegraph_seed_copies_and_full_scans: база скопирована, sync без init и без git fast path"
else
    echo "FAIL codegraph_seed_copies_and_full_scans: db=$cg_db, вызовы: $(tr '\n' ' ' <"$cg_state" 2>/dev/null)"
fi
cg_init_repo="$PWD/$work/cg-init-repo"
git init -q "$cg_init_repo"
printf 'class B {}\n' >"$cg_init_repo/B.java"
cg_state="$PWD/$work/cg-init.state"
rm -f "$cg_state"*
check codegraph_no_seed_inits 0 1 OPENCODE_REPO_DIR="$cg_init_repo" CODEGRAPH_BIN="$fake_codegraph" FAKE_CODEGRAPH_STATE="$cg_state" OPENCODE_CODEGRAPH_SEED=
cg_db="$(cat "$cg_init_repo/.codegraph/codegraph.db" 2>/dev/null || echo missing)"
if [ "$cg_db" = INIT ] && grep -q '^ARGS: init -i ' "$cg_state" && ! grep -q '^ARGS: sync ' "$cg_state"; then
    echo "OK codegraph_no_seed_inits: без готовой базы построен полный индекс"
else
    echo "FAIL codegraph_no_seed_inits: db=$cg_db, вызовы: $(tr '\n' ' ' <"$cg_state" 2>/dev/null)"
fi
cg_fail_repo="$PWD/$work/cg-fail-repo"
git init -q "$cg_fail_repo"
printf 'class C {}\n' >"$cg_fail_repo/C.java"
cg_state="$PWD/$work/cg-fail.state"
rm -f "$cg_state"*
check codegraph_sync_failure_falls_back_to_init 0 1 OPENCODE_REPO_DIR="$cg_fail_repo" CODEGRAPH_BIN="$fake_codegraph" FAKE_CODEGRAPH_STATE="$cg_state" OPENCODE_CODEGRAPH_SEED="$cg_seed_dir" FAKE_CODEGRAPH_SYNC_FAIL=1
cg_db="$(cat "$cg_fail_repo/.codegraph/codegraph.db" 2>/dev/null || echo missing)"
if [ "$cg_db" = INIT ] && grep -q '^ARGS: sync ' "$cg_state" && grep -q '^ARGS: init -i ' "$cg_state"; then
    echo "OK codegraph_sync_failure_falls_back_to_init: сбой sync заменён полным индексом"
else
    echo "FAIL codegraph_sync_failure_falls_back_to_init: db=$cg_db, вызовы: $(tr '\n' ' ' <"$cg_state" 2>/dev/null)"
fi
cg_existing_repo="$PWD/$work/cg-existing-repo"
git init -q "$cg_existing_repo"
printf 'class D {}\n' >"$cg_existing_repo/D.java"
mkdir -p "$cg_existing_repo/.codegraph"
printf 'OLD' >"$cg_existing_repo/.codegraph/codegraph.db"
cg_state="$PWD/$work/cg-existing.state"
rm -f "$cg_state"*
check codegraph_existing_index_is_synced 0 1 OPENCODE_REPO_DIR="$cg_existing_repo" CODEGRAPH_BIN="$fake_codegraph" FAKE_CODEGRAPH_STATE="$cg_state" OPENCODE_CODEGRAPH_SEED=
if grep -q '^ARGS: sync ' "$cg_state" && ! grep -q '^ARGS: init ' "$cg_state" && grep -q '^GIT_DIR: .\+' "$cg_state"; then
    echo "OK codegraph_existing_index_is_synced: существующий индекс обновлён полным сканом"
else
    echo "FAIL codegraph_existing_index_is_synced: вызовы: $(tr '\n' ' ' <"$cg_state" 2>/dev/null)"
fi
cg_none_repo="$PWD/$work/cg-none-repo"
git init -q "$cg_none_repo"
printf 'class E {}\n' >"$cg_none_repo/E.java"
cg_state="$PWD/$work/cg-none.state"
rm -f "$cg_state"*
check codegraph_missing_binary_is_skipped 0 1 OPENCODE_REPO_DIR="$cg_none_repo" CODEGRAPH_BIN="$PWD/$work/no-codegraph" FAKE_CODEGRAPH_STATE="$cg_state"
if [ ! -e "$cg_state" ] && [ ! -d "$cg_none_repo/.codegraph" ]; then
    echo "OK codegraph_missing_binary_is_skipped: без codegraph старт не падает и индекс не создаётся"
else
    echo "FAIL codegraph_missing_binary_is_skipped: созданы $(ls "$cg_none_repo/.codegraph" 2>/dev/null | tr '\n' ' ')"
fi
cg_base="$PWD/$work/cg-base"
git init -q "$cg_base"
printf 'class Base {}\n' >"$cg_base/Base.java"
git -C "$cg_base" add Base.java
git -C "$cg_base" -c user.email=t@t -c user.name=t commit -qm init --no-verify
cg_head="$(git -C "$cg_base" rev-parse HEAD)"
git -C "$cg_base" worktree add -q --detach "$PWD/$work/cg-match" HEAD
git -C "$cg_base" worktree add -q --detach "$PWD/$work/cg-newer" HEAD
git -C "$cg_base" worktree add -q --detach "$PWD/$work/cg-target" HEAD
mkdir -p "$PWD/$work/cg-match/.codegraph" "$PWD/$work/cg-newer/.codegraph"
printf 'MATCH' >"$PWD/$work/cg-match/.codegraph/codegraph.db"
printf '%s' "$cg_head" >"$PWD/$work/cg-match/.codegraph/indexed-commit"
sleep 1
printf 'NEWER' >"$PWD/$work/cg-newer/.codegraph/codegraph.db"
cg_state="$PWD/$work/cg-prefer.state"
rm -f "$cg_state"*
check codegraph_prefers_same_commit_seed 0 1 OPENCODE_REPO_DIR="$PWD/$work/cg-target" CODEGRAPH_BIN="$fake_codegraph" FAKE_CODEGRAPH_STATE="$cg_state" OPENCODE_CODEGRAPH_SEED=
cg_db="$(cat "$PWD/$work/cg-target/.codegraph/codegraph.db" 2>/dev/null || echo missing)"
if [ "$cg_db" = MATCH ]; then
    echo "OK codegraph_prefers_same_commit_seed: выбрана база с меткой текущего коммита, а не более свежая чужая"
else
    echo "FAIL codegraph_prefers_same_commit_seed: db=$cg_db, ожидал MATCH"
fi
