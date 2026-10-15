#!/usr/bin/env bash
# Регрессионная проверка scripts/opencode-task.sh на подделке opencode (~4 мин). Каждая строка вывода начинается с OK или FAIL.
cd "$(dirname "$0")/../.."
work="TestResults/opencode-check"
rm -rf "$work"
mkdir -p "$work"
export OPENCODE_BIN="$PWD/scripts/testing/fake-opencode.sh"
export OPENCODE_LOG_ROOT="$PWD/$work/logs"
export OPENCODE_ALLOW_DIRTY=1
export OPENCODE_TASK_FILE="$PWD/$work/task.md"
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
check tool_read_mentions_error 0 1 FAKE_TOOL_READ=1
check info_log_mentions_limit 0 1 FAKE_INFO_LOG=1
check unfinished_answer_continues 0 3 FAKE_UNFINISHED=2
check unfinished_answer_limit 0 4 FAKE_UNFINISHED=99 OPENCODE_UNFINISHED_LIMIT=3
check compaction_restarts_with_reminder 0 2 FAKE_COMPACTION=1
grep -q "TestResults/task.md" <(sed -n 2p "$work/compaction_restarts_with_reminder.state.args") && v=OK || v=FAIL
echo "$v compaction_restarts_with_reminder: второй запуск напоминает перечитать task.md и progress.md"
[ "$(cat "$OPENCODE_TASK_FILE" 2>/dev/null)" = "тестовое задание" ] && v=OK || v=FAIL
echo "$v task_file_saved: задание сохранено в TestResults/task.md"
dirty_repo="$PWD/$work/dirty-repo"
git init -q "$dirty_repo" && echo "чужая работа" > "$dirty_repo/foreign.txt"
check dirty_tree_new_task 3 0 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=(-s ses_existing)
check dirty_tree_continue_session 0 1 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=()
check exhausted 75 6 FAKE_FAILURES=99 OPENCODE_WAIT_LIMIT=0
grep "перезапуск\|Остановлено" "$work/exhausted.out" | sed 's/^/  /'
failures_after=$(wc -l < "$failures_log")
echo "  строк добавлено в failures.log: $((failures_after - failures_before)) (ожидаю 2+1+2+1+2+3+6 = 17: сбои провайдера, сетевые сбои, молчание, оборванные ответы, исчерпание)"
tail -n 1 "$failures_log" | sed 's/^/  последняя: /'

# Режим ожидания (короткие интервалы через переменные): 5 быстрых перезапусков исчерпаны, дальше — пробы провайдера.
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
