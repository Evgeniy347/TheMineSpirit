#!/usr/bin/env bash
# Регрессионная проверка агента-стратега (~1 мин): python3 -m unittest в agents/strategist и сценарии scripts/strategist.sh
# на временном STRATEGIST_HOME с фейковым REST (--fixtures) и командным «мозгом». Реального opencode и сервера Minecraft нет.
# Каждая строка вывода начинается с OK или FAIL; код выхода 1, если есть хотя бы один FAIL.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/strategist-check"
rm -rf "$work"
mkdir -p "$work"
failed=0

ok() { echo "OK $1${2:+: $2}"; }
fail() { echo "FAIL $1${2:+: $2}"; failed=1; }
expect_code() {
    local name="$1" expected="$2" actual="$3" detail="${4:-}"
    if [ "$actual" = "$expected" ]; then ok "$name" "код=$actual${detail:+, $detail}"; else fail "$name" "код=$actual (ожидал $expected)${detail:+, $detail}"; fi
}
expect_text() {
    local name="$1" needle="$2" file="$3"
    if grep -qF -- "$needle" "$file" 2>/dev/null; then ok "$name" "найдено «$needle»"; else fail "$name" "в $file нет «$needle»"; fi
}

(cd agents/strategist && python3 -m unittest discover -s tests -t . >"$work/unittest.log" 2>&1)
code=$?
ran="$(grep -m1 '^Ran ' "$work/unittest.log")"
if [ "$code" = 0 ]; then ok "unittest" "$ran, $(tail -n 1 "$work/unittest.log")"; else fail "unittest" "$ran; $(tail -n 3 "$work/unittest.log" | tr '\n' ' ') (см. $work/unittest.log)"; fi

cat >"$work/brain.sh" <<'EOF'
#!/usr/bin/env bash
cat >/dev/null
printf '%s' '{"reason":"Рядом лес.","goal":"срубить деревья","plan":{"type":"script","script":"chop_tree","params":{"count":3}}}'
EOF
chmod +x "$work/brain.sh"

export STRATEGIST_HOME="$work/home"
export STRATEGIST_SESSION="mc-strategist-check-$$"
export STRATEGIST_ARGS="--fixtures $root/agents/strategist/tests/fixtures"
export STRATEGIST_BRAIN=command
export STRATEGIST_BRAIN_COMMAND="$work/brain.sh"
export STRATEGIST_MODE=dry
export STRATEGIST_TASK_POLL=30
export STRATEGIST_REST_PER_SECOND=20
export STRATEGIST_START_WAIT=3
export STRATEGIST_STOP_WAIT=30
script="scripts/strategist.sh"
cleanup() { tmux kill-session -t "$STRATEGIST_SESSION" 2>/dev/null; }
trap cleanup EXIT

"$script" nonsense >"$work/usage.out" 2>&1
expect_code "usage_error" 2 $?

STRATEGIST_MODE=live "$script" start >"$work/live.out" 2>&1
code=$?
expect_code "live_without_confirmation" 2 "$code"
expect_text "live_without_confirmation_message" "согласия" "$work/live.out"

STRATEGIST_INTERVAL=1 "$script" start >"$work/badcfg.out" 2>&1
expect_code "bad_config_start_fails" 1 $?
expect_text "bad_config_names_variable" "STRATEGIST_INTERVAL" "$work/badcfg.out"
rm -f "$STRATEGIST_HOME/strategist.log"

"$script" start >"$work/start.out" 2>&1
expect_code "start" 0 $? "$(head -n 1 "$work/start.out")"
"$script" start >"$work/start2.out" 2>&1
expect_code "start_twice_refused" 3 $?

"$script" status >"$work/status.out" 2>&1
expect_code "status_running" 0 $?
expect_text "status_running_text" "агент: работает" "$work/status.out"

waited=0
until grep -q '"event":"posted"' "$STRATEGIST_HOME/journal.jsonl" 2>/dev/null || [ "$waited" -ge 30 ]; do sleep 1; waited=$((waited + 1)); done
if grep -q '"event":"posted"' "$STRATEGIST_HOME/journal.jsonl" 2>/dev/null; then ok "first_cycle_journaled" "за $waited с"; else fail "first_cycle_journaled" "за 30 с в журнале нет posted; лог: $(tail -n 3 "$STRATEGIST_HOME/strategist.log" 2>/dev/null | tr '\n' ' ')"; fi

"$script" pause >"$work/pause.out" 2>&1
if [ -e "$STRATEGIST_HOME/PAUSE" ]; then ok "pause_creates_file"; else fail "pause_creates_file" "PAUSE не создан"; fi
"$script" status >"$work/status2.out" 2>&1
expect_text "status_shows_pause" "пауза PAUSE: есть" "$work/status2.out"
"$script" resume >"$work/resume.out" 2>&1
if [ ! -e "$STRATEGIST_HOME/PAUSE" ]; then ok "resume_removes_file"; else fail "resume_removes_file" "PAUSE остался"; fi

"$script" logs 3 >"$work/logs.out" 2>&1
expect_code "logs" 0 $? "строк $(wc -l <"$work/logs.out")"
"$script" logs abc >"$work/logs2.out" 2>&1
expect_code "logs_bad_argument" 2 $?

"$script" stop >"$work/stop.out" 2>&1
expect_code "stop" 0 $? "$(head -n 1 "$work/stop.out")"
if tmux has-session -t "$STRATEGIST_SESSION" 2>/dev/null; then fail "stop_ends_session" "сессия жива"; else ok "stop_ends_session"; fi
expect_text "stop_is_graceful" "остановлен корректно" "$STRATEGIST_HOME/strategist.log"
"$script" stop >"$work/stop2.out" 2>&1
expect_code "stop_when_not_running" 0 $?

"$script" status >"$work/status3.out" 2>&1
expect_code "status_stopped" 1 $?
expect_text "status_stopped_text" "агент: не запущен" "$work/status3.out"

"$script" once >"$work/once.out" 2>&1
expect_code "once" 0 $? "$(tail -n 1 "$work/once.out" | cut -c1-80)"

"$script" smoke >"$work/smoke.out" 2>&1
expect_code "smoke_with_command_brain" 0 $?
report="$(ls "$STRATEGIST_HOME"/smoke-*.md 2>/dev/null | head -n 1)"
if [ -n "$report" ] && grep -q 'валидных ответов' "$report"; then ok "smoke_report" "$(basename "$report")"; else fail "smoke_report" "отчёта нет или в нём нет долей"; fi

exit "$failed"
