#!/usr/bin/env python3
"""Агент-стратег духов: раз в INTERVAL решает, чем заняться простаивающим духам, и ставит задачи по REST.

Команды: once | run | status | probe | sandbox-check | smoke  [--fixtures КАТАЛОГ] [--model МОДЕЛЬ] [--runs N].
Подробности: docs/STRATEGIST.md.
"""

from __future__ import annotations

import argparse
import dataclasses
import signal
import sys
import threading
from datetime import datetime, timezone
from pathlib import Path

import config as config_module
from brain import BrainError, BrainUnavailable, CommandBrain, FallbackBrain, OpencodeBrain, ensure_sandbox
from config import APP_DIR, Config, ConfigError
from cycle import CycleRunner, CycleSummary
from guard import Guard
from journal import Journal
from plan_check import PlanLimits, load_policy
from prompt import PromptError, Prompter
from rest_client import ApiError, NetworkError, RealClock, RestClient
from runtime import Runtime
from state import StateStore
from tracker import Tracker

EXIT_CHECK_FAILED = 1
EXIT_BAD_CONFIG = 2
EXIT_LOCKED = 3
EXIT_BROKEN = 4
EXIT_BREACH = 5


class Strategist:
    def __init__(self, rt: Runtime, brain, prompter, policy, limits):
        self.rt = rt
        self.tracker = Tracker(rt)
        self.cycles = CycleRunner(rt, brain, prompter, policy, limits, self.tracker)
        self.stopping = False
        self._wake = threading.Event()

    @property
    def state(self) -> dict:
        return self.rt.state

    @property
    def journal(self):
        return self.rt.journal

    @property
    def guard(self):
        return self.rt.guard

    @property
    def effective_mode(self) -> str:
        return self.cycles.effective_mode

    def run_cycle(self) -> CycleSummary:
        return self.cycles.run()

    def poll_tasks(self) -> int:
        return self.tracker.poll()

    def next_interval(self) -> float:
        return self.cycles.provider.interval()

    @property
    def halted(self) -> str | None:
        return self.cycles.halted

    def stop(self) -> None:
        self.stopping = True
        self.cycles.stop_requested = True
        self.cycles.brain.request_stop()
        self._wake.set()

    def _pause(self, seconds: float) -> None:
        if isinstance(self.rt.clock, RealClock):
            self._wake.wait(seconds)
        else:
            self.rt.clock.sleep(seconds)

    def once(self) -> CycleSummary:
        summary = self.run_cycle()
        self.poll_tasks()
        return summary

    def run_forever(self) -> None:
        clock = self.rt.clock
        while not self.stopping:
            self.run_cycle()
            if self.halted:
                break
            deadline = clock.now() + self.next_interval()
            while not self.stopping and clock.now() < deadline:
                self.poll_tasks()
                self._pause(max(0.0, min(self.rt.config.task_poll, deadline - clock.now())))
        self.rt.save()


def build(config: Config, brain, clock=None, client=None, out=print, strict: bool = False) -> Strategist:
    clock = clock or RealClock()
    config.home.mkdir(parents=True, exist_ok=True)
    policy = load_policy(config.policy_path)
    limits = PlanLimits(max_nodes=config.max_nodes, max_root_steps=config.max_root_steps)
    prompter = Prompter(APP_DIR, config.repo_root / "docs" / "api" / "plan.md", policy, limits)
    journal = Journal(config.journal_path, clock, config.journal_max_bytes, config.journal_files)
    store = StateStore(config.state_path, clock)
    loaded = store.load(journal)
    state = loaded.state
    client = client or RestClient(config.api, clock=clock, rps=config.rest_per_second, timeout=config.rest_timeout)
    rt = Runtime(config=config, client=client, journal=journal, guard=Guard(config, state.get("guard")), store=store,
                 state=state, clock=clock, out=out, strict=strict)
    rt.load_recent()
    if loaded.corrupt_path is not None:
        rt.say(f"ВНИМАНИЕ: {loaded.reason}")
    return Strategist(rt, brain, prompter, policy, limits)


def make_brain(config: Config):
    if config.brain == "command":
        return CommandBrain(config, config.brain_command, config.model)
    primary = OpencodeBrain(config, config.model)
    if config.model_fallback and config.model_fallback != config.model:
        return FallbackBrain(primary, OpencodeBrain(config, config.model_fallback))
    return FallbackBrain(primary)


def acquire_lock(home: Path):
    import fcntl
    home.mkdir(parents=True, exist_ok=True)
    handle = open(home / "lock", "w")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        handle.close()
        return None
    handle.write(f"{datetime.now(timezone.utc).isoformat(timespec='seconds')}\n")
    handle.flush()
    return handle


def start_fixture_server(directory: Path):
    from tests.fake_rest import FakeRest, load_json
    fake = FakeRest()
    capabilities = directory / "capabilities.json"
    if capabilities.is_file():
        fake.capabilities = load_json(capabilities)
    for path in sorted(directory.glob("world-*.json")):
        data = load_json(path)
        bot_id = path.stem[len("world-"):]
        fake.add_bot(bot_id, name=data.get("name"), state=data.get("state", "idle"), observation=data["observation"])
    return fake.start()


def status_lines(config: Config) -> list[str]:
    import fcntl
    now = datetime.now(timezone.utc).timestamp()
    clock = RealClock()
    journal = Journal(config.journal_path, clock, config.journal_max_bytes, config.journal_files)
    events = journal.read().events
    hour = [e for e in events if _age(e, now) <= 3600]
    count = lambda name, pool=hour, **match: sum(1 for e in pool if e["event"] == name and all(e.get(k) == v for k, v in match.items()))
    last_cycle = next((e for e in reversed(events) if e["event"] == "cycle_start"), None)
    last_failure = next((e for e in reversed(events) if e["event"] in ("brain_failure", "alert")), None)
    lock = open(config.home / "lock", "a") if config.home.exists() else None
    running = False
    if lock is not None:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.flock(lock, fcntl.LOCK_UN)
        except OSError:
            running = True
        lock.close()
    state = StateStore(config.state_path, clock).load().state
    running_model = next((e["model"] for e in reversed(events) if e.get("model")), config.model)
    brain_line = f"мозг: {config.brain}, модель {running_model}"
    if running_model != config.model:
        brain_line += f" (в текущем окружении {config.model})"
    brain_line += f", запасная {config.model_fallback}"
    lines = [
        f"агент: {'работает' if running else 'не запущен'}",
        f"режим: {last_cycle.get('mode', config.mode) if last_cycle else config.mode} (настроен {config.mode})",
        brain_line,
        f"последний цикл: {last_cycle['ts'] + ' №' + str(last_cycle.get('cycle')) if last_cycle else 'нет'}",
        f"за час: решений {count('decision')}, отказов {count('rejected')}, задач отправлено "
        f"{sum(1 for e in hour if e['event'] == 'posted' and not e.get('dry'))}, в dry {sum(1 for e in hour if e['event'] == 'posted' and e.get('dry'))}, "
        f"завершено {count('task_done')}",
        f"активных задач: {len(state['active'])}" + "".join(
            f"\n  {tid}: дух {entry['bot']}, «{entry['goal']}», состояние {entry['last_state']}" for tid, entry in state["active"].items()),
        f"пауза PAUSE: {'есть' if config.pause_path.exists() else 'нет'}",
        f"последний сбой: {last_failure['ts'] + ' ' + str(last_failure.get('reason') or last_failure.get('kind')) if last_failure else 'нет'}",
    ]
    return lines


def _age(event: dict, now: float) -> float:
    try:
        return now - datetime.fromisoformat(event["ts"]).timestamp()
    except (KeyError, ValueError):
        return float("inf")


def breach_exit(strategist: Strategist) -> int:
    if not strategist.halted:
        return 0
    print(f"Стратег остановлен из-за нарушения песочницы: {strategist.halted}", file=sys.stderr, flush=True)
    return EXIT_BREACH


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="strategist.py", description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=["once", "run", "status", "probe", "sandbox-check", "smoke"])
    parser.add_argument("--model", help="smoke: модель вместо STRATEGIST_MODEL")
    parser.add_argument("--runs", type=int, default=2, help="smoke: прогонов на каждое место (по умолчанию 2)")
    parser.add_argument("--fixtures", type=Path, help="каталог с записанными наблюдениями: поднять фейковый REST вместо мода")
    args = parser.parse_args(argv)
    try:
        config = config_module.from_env()
    except ConfigError as error:
        print(f"Ошибка конфигурации: {error}", file=sys.stderr)
        return EXIT_BAD_CONFIG
    if args.command == "status":
        print("\n".join(status_lines(config)))
        return 0
    if config.mode == "live" and not config.confirm_live:
        print("Режим live требует согласия пользователя и STRATEGIST_CONFIRM_LIVE=1 (docs/STRATEGIST.md). Запуск отменён.",
              file=sys.stderr)
        return EXIT_BAD_CONFIG
    lock = acquire_lock(config.home)
    if lock is None:
        print(f"Другой экземпляр стратега уже работает (замок {config.home / 'lock'}). Второй не запускаю.", file=sys.stderr)
        return EXIT_LOCKED
    fixture_server = None
    try:
        if args.fixtures:
            fixture_server = start_fixture_server(args.fixtures)
            config = dataclasses.replace(config, api=fixture_server.url)
            print(f"Фейковый REST из {args.fixtures}: {fixture_server.url}")
        brain = make_brain(config)
        if config.brain == "opencode":
            ensure_sandbox(config.sandbox_dir)
            brain.verify()
        if args.command == "smoke":
            from smoke import run_smoke
            model = args.model or config.model
            smoke_brain = (CommandBrain(config, config.brain_command, model) if config.brain == "command"
                           else OpencodeBrain(config, model))
            report = run_smoke(config, smoke_brain, args.fixtures or APP_DIR / "tests" / "fixtures", runs=args.runs)
            print(f"Отчёт smoke: {report}")
            return 0
        if args.command == "probe":
            reply = brain.probe()
            print(f"Проба прошла: модель {reply.model}, {reply.seconds} с, ответ «{reply.text.strip()[:80]}»")
            return 0
        if args.command == "sandbox-check":
            if config.brain != "opencode":
                print("sandbox-check проверяет песочницу opencode: при STRATEGIST_BRAIN=command проверять нечего",
                      file=sys.stderr)
                return EXIT_BAD_CONFIG
            print(f"opencode: {brain.opencode_version()}")
            probe = brain.sandbox_check()
            if probe.executed:
                print(f"FAIL песочница: инструмент {', '.join(probe.executed)} выполнился (status не error) — "
                      "проверьте sandbox/opencode.json и запуск без флага автоодобрения", file=sys.stderr)
                return EXIT_CHECK_FAILED
            if not probe.attempted:
                print(f"FAIL песочница: за {probe.seconds} с модель не попыталась выполнить ls — проверка "
                      "не подтвердила отказ (модель не вызвала инструмент)", file=sys.stderr)
                return EXIT_CHECK_FAILED
            print(f"OK песочница: модель попыталась выполнить {', '.join(probe.attempted)} за {probe.seconds} с, "
                  "инструмент отклонён (status error), выполнения нет")
            return 0
        strategist = build(config, brain, out=lambda text: print(f"[{datetime.now().strftime('%H:%M:%S')}] {text}", flush=True))
        if args.command == "once":
            strategist.once()
            return breach_exit(strategist)
        for signum in (signal.SIGTERM, signal.SIGINT):
            signal.signal(signum, lambda *_: strategist.stop())
        print(f"Стратег запущен: режим {strategist.effective_mode}, модель {config.model}, интервал {config.interval} с, "
              f"домашний каталог {config.home}", flush=True)
        strategist.run_forever()
        if strategist.halted:
            return breach_exit(strategist)
        print("Стратег остановлен корректно: поставленные задачи не отменялись.", flush=True)
        return 0
    except (PromptError, ApiError, NetworkError, BrainError, BrainUnavailable, ValueError, OSError) as error:
        print(f"Ошибка запуска: {type(error).__name__}: {error}", file=sys.stderr)
        return EXIT_BROKEN
    finally:
        if fixture_server is not None:
            fixture_server.stop()
        lock.close()


if __name__ == "__main__":
    sys.exit(main())
