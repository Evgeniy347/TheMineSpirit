#!/usr/bin/env bash
# Регрессионная проверка scripts/opencode-task.sh на подделке opencode (~4 мин). Каждая строка вывода начинается с OK или FAIL.
cd "$(dirname "$0")/../.."
work="TestResults/opencode-check"
rm -rf "$work"
mkdir -p "$work"
export OPENCODE_BIN="$PWD/scripts/testing/fake-opencode.sh"
export OPENCODE_LOG_ROOT="$PWD/$work/logs"
export OPENCODE_ALLOW_DIRTY=1
failures_log="$OPENCODE_LOG_ROOT/failures.log"

check() {
    local name="$1" expected_code="$2" expected_runs="$3"
    shift 3
    export FAKE_STATE="$work/$name.state"
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
check silent_model 0 2 FAKE_SILENT=1 OPENCODE_IDLE_LIMIT=6
check busy_tool_is_not_silence 0 1 FAKE_BUSY_TOOL=15 OPENCODE_IDLE_LIMIT=6
check tool_read_mentions_error 0 1 FAKE_TOOL_READ=1
check info_log_mentions_limit 0 1 FAKE_INFO_LOG=1
dirty_repo="$PWD/$work/dirty-repo"
git init -q "$dirty_repo" && echo "чужая работа" > "$dirty_repo/foreign.txt"
check dirty_tree_new_task 3 0 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=(-s ses_existing)
check dirty_tree_continue_session 0 1 OPENCODE_ALLOW_DIRTY= OPENCODE_REPO_DIR="$dirty_repo"
task_args=()
check exhausted 75 6 FAKE_FAILURES=99
grep "перезапуск\|Остановлено" "$work/exhausted.out" | sed 's/^/  /'
failures_after=$(wc -l < "$failures_log")
echo "  строк добавлено в failures.log: $((failures_after - failures_before)) (ожидаю 2+1+1+6 = 10)"
tail -n 1 "$failures_log" | sed 's/^/  последняя: /'
