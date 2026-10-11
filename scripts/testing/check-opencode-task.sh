#!/usr/bin/env bash
# Регрессионная проверка scripts/opencode-task.sh на подделке opencode (~3 мин). Каждая строка вывода начинается с OK или FAIL.
cd "$(dirname "$0")/../.."
work="TestResults/opencode-check"
rm -rf "$work"
mkdir -p "$work"
export OPENCODE_BIN="$PWD/scripts/testing/fake-opencode.sh"

check() {
    local name="$1" expected_code="$2" expected_runs="$3"
    shift 3
    export FAKE_STATE="$work/$name.state"
    local started=$(date +%s)
    env "$@" scripts/opencode-task.sh "тестовое задание" >"$work/$name.out" 2>&1
    local code=$? runs=$(cat "$FAKE_STATE") took=$(( $(date +%s) - started ))
    local verdict=OK
    [ "$code" = "$expected_code" ] && [ "$runs" = "$expected_runs" ] || verdict=FAIL
    echo "$verdict $name: код=$code (ожидал $expected_code), запусков=$runs (ожидал $expected_runs), $took с"
}

check two_failures 0 3 FAKE_FAILURES=2
grep -c -- "-s ses_fake123 " "$work/two_failures.state.args" | sed 's/^/  перезапусков с -s ses_fake123 (ровно один id): /'
grep -c "^ses_fake123$" "$work/two_failures.out" | sed 's/^/  строк с голым id сессии в выводе (ожидаю 0): /'
grep -c "Продолжай выполнение" "$work/two_failures.state.args" | sed 's/^/  перезапусков с текстом продолжения: /'
check tool_read_mentions_error 0 1 FAKE_TOOL_READ=1
check stderr_log_failure 0 2 FAKE_FAILURES=1 FAKE_ERROR_CHANNEL=stderr
check exhausted 75 6 FAKE_FAILURES=99
grep "перезапуск\|Остановлено" "$work/exhausted.out" | sed 's/^/  /'
check stalled 76 1 FAKE_HANG=1 OPENCODE_IDLE_LIMIT=6
