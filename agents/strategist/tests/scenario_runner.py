"""Прогон сценария-таблицы tests/scenarios/*.json: фейковый REST внутри процесса (без HTTP), подделанные часы,
скриптовый мозг, проверки по журналу."""

from __future__ import annotations

import copy
import json
import os
import uuid
from pathlib import Path

import config as config_module
from brain import FallbackBrain
from strategist import build
from tests.fake_rest import FIXTURES, FakeRest, InProcessClient
from tests.helpers import FakeClock
from tests.scripted_brain import ScriptedBrain

PHRASE_TAGS = ("<observation>", "</observation>")


def load_world(name: str, tweak: int | None = None, entity_name: str | None = None) -> dict:
    data = json.loads((FIXTURES / f"world-{name}.json").read_text(encoding="utf-8"))
    observation = data["observation"]
    observation = copy.deepcopy(observation)
    if tweak is not None:
        observation["inventory"] = observation["inventory"] + [{"item": f"tweak_{tweak}", "count": tweak + 1}]
    if entity_name is not None:
        observation["entities"] = [{"type": "minecraft:villager", "name": entity_name, "pos": observation["pos"], "distance": 0.5}] \
            + observation["entities"]
    observation.pop("hash", None)
    return observation


class ScenarioRun:
    def __init__(self, scenario: dict, tmp: Path):
        self.scenario = scenario
        self.name = scenario["name"]
        self.tmp = Path(tmp)
        self.clock = FakeClock()
        self.fake = FakeRest()
        self.fake.clock = self.clock
        env = {"STRATEGIST_HOME": str(self.tmp / "home"), "STRATEGIST_MODE": "live"}
        env.update({k: str(v) for k, v in scenario.get("config", {}).items()})
        self.config = config_module.from_env(env)
        self.brains = self._make_brains()
        self.client = InProcessClient(self.fake, clock=self.clock, rps=self.config.rest_per_second,
                                      timeout=self.config.rest_timeout)
        self.strategist = self._new_strategist()
        self._setup_bots()
        self.repeat_index = 0

    def close(self):
        self.fake.stop()

    def _make_brains(self):
        spec = self.scenario.get("brains") or {"primary": self.scenario.get("brain", {"default": {"json": {"reason": "ждать", "skip": True}}})}
        brains = {}
        for key, part in spec.items():
            brains[key] = ScriptedBrain(part.get("model", f"opencode/{key}-free"), part.get("script"), part.get("default"),
                                         part.get("probe"), part.get("probe_default"))
        return brains

    def _brain(self):
        if "fallback" in self.brains:
            return FallbackBrain(self.brains["primary"], self.brains["fallback"])
        return self.brains["primary"]

    def _new_strategist(self):
        return build(self.config, self._brain(), clock=self.clock, client=self.client, out=None)

    def _setup_bots(self):
        for spec in self.scenario["bots"]:
            if "count" in spec:
                for index in range(spec["count"]):
                    bot_id = f"{spec.get('prefix', 'b')}{index:02d}"
                    self.fake.add_bot(bot_id, observation=self._world(spec, tweak=index), state=spec.get("state", "idle"))
            else:
                self.fake.add_bot(spec["id"], name=spec.get("name"), state=spec.get("state", "idle"),
                                  observation=self._world(spec, spec.get("tweak"), spec.get("entity_name")))
        for bot, fate in self.scenario.get("fates", {}).items():
            self.fake.set_fate(None if bot == "*" else bot, fate["outcome"], fate.get("after_polls", 2), fate.get("error"))

    @staticmethod
    def _world(spec: dict, tweak: int | None = None, entity_name: str | None = None) -> dict:
        observation = load_world(spec["world"], tweak, entity_name)
        extra = spec.get("extra")
        if isinstance(extra, dict):
            observation.update(extra)
        return observation

    def run(self):
        self.run_steps(self.scenario["steps"])
        self.check(self.scenario.get("expect", []), "в конце сценария")

    def run_steps(self, steps):
        for step in steps:
            self.run_step(step)

    def run_step(self, step: dict):
        if "repeat" in step:
            for index in range(step["repeat"]):
                self.repeat_index = index
                self.run_steps(step["steps"])
        elif "cycle" in step:
            self.strategist.run_cycle()
        elif "advance" in step:
            remaining = float(step["advance"])
            poll = self.config.task_poll
            while remaining > 0:
                chunk = min(poll, remaining)
                self.clock.advance(chunk)
                self.strategist.poll_tasks()
                remaining -= chunk
        elif "poll" in step:
            self.strategist.poll_tasks()
        elif "fake" in step:
            self.fake_step(step["fake"])
        elif "pause_file" in step:
            path = self.config.pause_path
            if step["pause_file"]:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("пауза\n", encoding="utf-8")
            elif path.exists():
                path.unlink()
        elif "restart" in step:
            self.strategist = self._new_strategist()
        elif "expect" in step:
            self.check(step["expect"], "в середине сценария")
        else:
            raise AssertionError(f"{self.name}: неизвестный шаг сценария {step}")

    def fake_step(self, spec: dict):
        for key, value in spec.items():
            if key == "set_state":
                self.fake.set_state(value["bot"], value["state"])
            elif key == "set_world":
                world = value["world"] if "world" in value else value["worlds"][self.repeat_index % len(value["worlds"])]
                self.fake.set_observation(value["bot"], load_world(world, value.get("tweak"), value.get("entity_name")))
            elif key == "fate":
                self.fake.set_fate(value.get("bot"), value["outcome"], value.get("after_polls", 2), value.get("error"))
            elif key == "forget_tasks":
                self.fake.forget_tasks()
            elif key == "vanish_on_task_post":
                self.fake.vanish_on_task_post = set(value)
            elif key == "busy_after_observe":
                self.fake.busy_after_observe = set(value)
            elif key == "warehouse":
                self.fake.set_warehouse(**value)
            elif key == "quarry":
                self.fake.set_quarry(**value)
            elif key in ("tasks_route", "plan_route", "plan_error", "task_post_lose_response", "drop_connection_next",
                         "bots_as_list", "warehouse_route"):
                setattr(self.fake, key, value)
            elif key in ("task_post_errors", "server_errors", "rate_limited", "observe_errors", "bot_get_errors"):
                getattr(self.fake, key).extend([tuple(v) if isinstance(v, list) else v for v in value])
            else:
                raise AssertionError(f"{self.name}: неизвестная ручка фейка {key}")

    def events(self) -> list[dict]:
        return self.strategist.journal.read().events

    def task_posts(self):
        return self.fake.posted_tasks()

    def check(self, checks, when: str):
        for check in checks:
            for kind, expected in check.items():
                getattr(self, f"check_{kind}")(expected, when)

    def fail(self, when: str, message: str):
        raise AssertionError(f"[сценарий {self.name}, {when}] {message}")

    def check_posts(self, expected, when):
        actual = len(self.task_posts())
        if actual != expected:
            self.fail(when, f"POST /tasks получено {actual}, ожидалось {expected}; запросы: "
                      f"{[(r['path'], r['status']) for r in self.task_posts()]}")

    def check_warehouse_posts(self, expected, when):
        actual = len(self.fake.requests_to("POST", "/v1/settlement/warehouse"))
        if actual != expected:
            self.fail(when, f"POST /v1/settlement/warehouse получено {actual}, ожидалось {expected}; запросы: "
                      f"{[(r['path'], r['status'], r['body']) for r in self.fake.requests_to('POST', '/v1/settlement/warehouse')]}")

    def check_quarry_posts(self, expected, when):
        actual = len(self.fake.requests_to("POST", "/v1/quarry/site"))
        if actual != expected:
            self.fail(when, f"POST /v1/quarry/site получено {actual}, ожидалось {expected}; запросы: "
                      f"{[(r['path'], r['status'], r['body']) for r in self.fake.requests_to('POST', '/v1/quarry/site')]}")

    def check_quarry_body(self, expected, when):
        posts = self.fake.requests_to("POST", "/v1/quarry/site")
        if not posts:
            self.fail(when, "для проверки тела нужно решение о месте добычи, а POST /v1/quarry/site нет")
        for field, value in expected.items():
            if posts[0]["body"].get(field) != value:
                self.fail(when, f"поле {field} места добычи {posts[0]['body'].get(field)!r}, ожидалось {value!r}")

    def check_warehouse_body(self, expected, when):
        posts = self.fake.requests_to("POST", "/v1/settlement/warehouse")
        if len(posts) != 1:
            self.fail(when, f"для проверки тела нужно ровно одно решение о складе, а их {len(posts)}")
        for field, value in expected.items():
            if posts[0]["body"].get(field) != value:
                self.fail(when, f"поле {field} решения о складе {posts[0]['body'].get(field)!r}, ожидалось {value!r}")

    def check_tasks_created(self, expected, when):
        if len(self.fake.tasks) != expected:
            self.fail(when, f"задач на сервере {len(self.fake.tasks)}, ожидалось {expected}")

    def check_brain_calls(self, expected, when):
        actual = sum(len(b.calls) for b in self.brains.values())
        if actual != expected:
            self.fail(when, f"вызовов модели {actual}, ожидалось {expected}")

    def check_probes(self, expected, when):
        actual = sum(b.probes for b in self.brains.values())
        if actual != expected:
            self.fail(when, f"проб модели {actual}, ожидалось {expected}")

    def check_journal(self, expected, when):
        counts: dict[str, int] = {}
        for event in self.events():
            counts[event["event"]] = counts.get(event["event"], 0) + 1
        for name, count in expected.items():
            if counts.get(name, 0) != count:
                self.fail(when, f"событий журнала {name}: {counts.get(name, 0)}, ожидалось {count}; всего: {counts}")

    @staticmethod
    def _matches(event: dict, pattern: dict) -> bool:
        for key, value in pattern.items():
            if key.endswith("_contains"):
                if value not in str(event.get(key[: -len("_contains")], "")):
                    return False
            elif event.get(key) != value:
                return False
        return True

    def check_journal_has(self, expected, when):
        for pattern in expected if isinstance(expected, list) else [expected]:
            if not any(self._matches(e, pattern) for e in self.events()):
                self.fail(when, f"в журнале нет события {pattern}; события: "
                          f"{[{k: e.get(k) for k in ('event', 'bot', 'stage', 'reason', 'outcome') if k in e} for e in self.events()]}")

    def check_journal_lacks(self, expected, when):
        for pattern in expected if isinstance(expected, list) else [expected]:
            if any(self._matches(e, pattern) for e in self.events()):
                self.fail(when, f"в журнале есть событие {pattern}, которого быть не должно")

    def check_posted_scripts(self, expected, when):
        actual = [sorted(scripts_in(request["body"].get("plan"))) for request in self.task_posts()]
        if actual != expected:
            self.fail(when, f"скрипты отправленных задач {actual}, ожидались {expected}")

    def check_plan_calls(self, expected, when):
        actual = len(self.fake.requests_to("POST", "/v1/plan"))
        if actual != expected:
            self.fail(when, f"POST /v1/plan {actual}, ожидалось {expected}")

    def check_deletes(self, expected, when):
        actual = len(self.fake.requests_to("DELETE", "/v1/tasks/"))
        if actual != expected:
            self.fail(when, f"DELETE задач {actual}, ожидалось {expected}")

    def check_request_id_uuid(self, expected, when):
        for request in self.task_posts():
            value = request["body"]["request_id"]
            try:
                uuid.UUID(value)
            except ValueError:
                self.fail(when, f"request_id {value!r} не UUID")

    def check_interval(self, expected, when):
        actual = self.strategist.next_interval()
        if actual != expected:
            self.fail(when, f"интервал между циклами {actual}, ожидался {expected}")

    def check_mode(self, expected, when):
        if self.strategist.effective_mode != expected:
            self.fail(when, f"режим {self.strategist.effective_mode}, ожидался {expected}")

    def check_active_tasks(self, expected, when):
        actual = len(self.strategist.state["active"])
        if actual != expected:
            self.fail(when, f"активных задач в состоянии {actual}, ожидалось {expected}")

    def check_fake_state(self, expected, when):
        for bot, state in expected.items():
            if self.fake.bots[bot]["state"] != state:
                self.fail(when, f"дух {bot} на сервере в состоянии {self.fake.bots[bot]['state']}, ожидалось {state}")

    def check_injection_only_inside_observation(self, phrase, when):
        prompts = [user for brain in self.brains.values() for _, user in brain.calls]
        if not prompts:
            self.fail(when, "модель не вызывалась, проверять нечего")
        for user in prompts:
            start, end = user.find(PHRASE_TAGS[0]), user.find(PHRASE_TAGS[1])
            positions = [i for i in range(len(user)) if user.startswith(phrase, i)]
            if not positions or not all(start < i < end for i in positions):
                self.fail(when, f"фраза {phrase!r} должна быть в промпте только внутри <observation>; позиции {positions}, блок {start}..{end}")

    def check_prompt_contains(self, expected, when):
        for item in expected:
            calls = [call for brain in self.brains.values() for call in brain.calls]
            user = calls[item["call"]][1]
            if item["contains"] not in user:
                self.fail(when, f"в промпте вызова {item['call']} нет текста {item['contains']!r}")

    def check_system_contains(self, expected, when):
        systems = [call[0] for brain in self.brains.values() for call in brain.calls]
        if not systems:
            self.fail(when, "модель не вызывалась, проверять системное сообщение нечего")
        for needle in expected if isinstance(expected, list) else [expected]:
            if not all(needle in system for system in systems):
                self.fail(when, f"в системном сообщении нет текста {needle!r}")

    def check_distinct_request_ids(self, expected, when):
        actual = len({r["body"]["request_id"] for r in self.task_posts()})
        if actual != expected:
            self.fail(when, f"различных request_id среди POST /tasks {actual}, ожидалось {expected}")

    def check_clock_slept_at_least(self, expected, when):
        if sum(self.clock.sleeps) < expected:
            self.fail(when, f"клиент ждал {sum(self.clock.sleeps)} с, ожидалось не меньше {expected} (Retry-After)")

    def check_phrase_only_inside_tag(self, expected, when):
        calls = [call for brain in self.brains.values() for call in brain.calls]
        user = calls[expected["call"]][1]
        tag = expected["tag"]
        start, end = user.find(f"<{tag}>"), user.find(f"</{tag}>")
        positions = [i for i in range(len(user)) if user.startswith(expected["phrase"], i)]
        ok = positions and start != -1 and all(start < i < end for i in positions) and user.count(f"</{tag}>") == 1
        if not ok:
            self.fail(when, f"фраза {expected['phrase']!r} должна встречаться в вызове {expected['call']} только внутри <{tag}>; "
                      f"позиции {positions}, блок {start}..{end}")

    def check_halted(self, expected, when):
        if bool(self.strategist.halted) != expected:
            self.fail(when, f"остановка агента: {self.strategist.halted!r}, ожидалось {expected}")

    def check_pause_exists(self, expected, when):
        if self.config.pause_path.exists() != expected:
            self.fail(when, f"файл PAUSE существует={self.config.pause_path.exists()}, ожидалось {expected}")

    def check_posted_scripts_allowed(self, expected, when):
        allowed = set(json.loads(self.config.policy_path.read_text(encoding="utf-8"))["allowed_scripts"])
        for request in self.task_posts():
            used = scripts_in(request["body"].get("plan"))
            if not used <= allowed:
                self.fail(when, f"на сервер ушёл скрипт вне белого списка: {sorted(used - allowed)}")


def scripts_in(node) -> set:
    found = set()
    stack = [node]
    while stack:
        item = stack.pop()
        if isinstance(item, dict):
            if item.get("type") == "script":
                found.add(item.get("script"))
            stack.extend(item.values())
        elif isinstance(item, list):
            stack.extend(item)
    return found


def load_scenarios(directory: Path) -> list[dict]:
    return [json.loads(path.read_text(encoding="utf-8")) for path in sorted(Path(directory).glob("*.json"))]
