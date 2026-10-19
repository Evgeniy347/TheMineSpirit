"""Живая проба качества: настоящий мозг на записанных наблюдениях (world-*.json), отчёт smoke-<время>.md в STRATEGIST_HOME."""

from __future__ import annotations

import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from brain import BrainError, BrainUnavailable
from config import APP_DIR, Config
from parse import ReplyError, parse_reply
from plan_check import Catalog, PlanError, PlanLimits, check_plan, load_policy
from prompt import Prompter
from rest_client import ApiError, RestClient

STOP_AFTER_UNAVAILABLE = 3
VALID_SHARE_TARGET = 80


def _share(part: int, whole: int) -> str:
    return f"{part} из {whole} ({round(100 * part / whole)} %)" if whole else f"{part} из 0 (нет данных)"


def run_smoke(config: Config, brain, fixtures_dir: Path, runs: int = 2, out=print) -> Path:
    from tests.fake_rest import FakeRest, load_json
    fixtures_dir = Path(fixtures_dir)
    capabilities = load_json(fixtures_dir / "capabilities.json")
    catalog = Catalog.from_capabilities(capabilities)
    policy = load_policy(config.policy_path)
    limits = PlanLimits(max_nodes=config.max_nodes, max_root_steps=config.max_root_steps)
    prompter = Prompter(APP_DIR, config.repo_root / "docs" / "api" / "plan.md", policy, limits)
    fake = FakeRest(capabilities).start()
    client = RestClient(fake.url, rps=1000, timeout=10)
    results = []
    unavailable_in_row = 0
    aborted = None
    try:
        for path in sorted(fixtures_dir.glob("world-*.json")):
            world = path.stem[len("world-"):]
            data = load_json(path)
            bot = {"id": world, "name": data.get("name", world), "state": "idle"}
            prompt = prompter.build(capabilities, bot, data["observation"], [], datetime.now(timezone.utc).timestamp())
            for run in range(1, runs + 1):
                result = _one_run(brain, prompt, world, run, catalog, policy, limits, client)
                results.append(result)
                if out is not None:
                    out(f"{world} #{run}: {result['outcome']} {result['seconds']} с {result['detail'][:100]}")
                unavailable_in_row = unavailable_in_row + 1 if result["outcome"] == "unavailable" else 0
                if unavailable_in_row >= STOP_AFTER_UNAVAILABLE:
                    aborted = result["detail"]
                    break
            if aborted:
                break
    finally:
        fake.stop()
    return _write_report(config, brain.model, results, runs, aborted)


def _one_run(brain, prompt, world: str, run: int, catalog, policy, limits, client) -> dict:
    result = {"world": world, "run": run, "outcome": "", "seconds": 0.0, "detail": "", "scripts": "", "summary": ""}
    try:
        reply = brain.complete(prompt.system, prompt.user)
    except BrainUnavailable as error:
        result.update(outcome="unavailable", detail=f"{error.kind}: {error.text}")
        return result
    except BrainError as error:
        result.update(outcome="brain_error", detail=str(error))
        return result
    result["seconds"] = reply.seconds
    try:
        decision = parse_reply(reply.text)
    except ReplyError as error:
        result.update(outcome="parse_error", detail=str(error).split(" Ответь ровно")[0], summary=reply.text.strip()[:120])
        return result
    if decision.skip:
        result.update(outcome="skip", summary=decision.reason)
        return result
    result["scripts"] = ", ".join(_scripts(decision.plan))
    result["summary"] = f"{decision.goal}: {decision.reason}"
    try:
        check_plan(decision.plan, catalog, policy, limits)
    except PlanError as error:
        result.update(outcome="plan_error", detail=str(error))
        return result
    try:
        client.post("/v1/plan", {"plan": decision.plan})
        result["outcome"] = "plan_ok"
    except ApiError as error:
        result.update(outcome="server_error", detail=str(error))
    return result


def _scripts(node) -> list[str]:
    found: list[str] = []
    stack = [node]
    while stack:
        item = stack.pop()
        if isinstance(item, dict):
            if item.get("type") == "script" and isinstance(item.get("script"), str):
                found.append(item["script"])
            stack.extend(reversed(list(item.values())))
        elif isinstance(item, list):
            stack.extend(reversed(item))
    return found


def _write_report(config: Config, model: str, results: list[dict], runs: int, aborted: str | None) -> Path:
    answered = [r for r in results if r["outcome"] not in ("unavailable", "brain_error")]
    plan_ok = [r for r in answered if r["outcome"] == "plan_ok"]
    skips = [r for r in answered if r["outcome"] == "skip"]
    local_ok = [r for r in answered if r["outcome"] in ("plan_ok", "server_error")]
    valid_percent = round(100 * (len(plan_ok) + len(skips)) / len(answered)) if answered else 0
    seconds = [r["seconds"] for r in answered if r["seconds"]]
    errors = Counter(r["detail"][:160] for r in results if r["detail"] and r["outcome"] != "unavailable")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    lines = [
        f"# Живая проба стратега: {model}", "",
        f"Дата: {datetime.now(timezone.utc).isoformat(timespec='seconds')}; прогонов на место: {runs}; всего запусков: {len(results)}.", "",
        "## Итоги", "",
        f"- ответов получено: {_share(len(answered), len(results))}; недоступна модель или сбой мозга: {len(results) - len(answered)}",
        f"- валидных ответов (план или skip): {_share(len(plan_ok) + len(skips), len(answered))}",
        f"- прошли plan_check: {_share(len(local_ok), len(answered))}",
        f"- прошли POST /v1/plan фейка: {_share(len(plan_ok), len(local_ok))}",
        (f"- Время ответа: среднее {sum(seconds) / len(seconds):.1f} с, минимум {min(seconds):.1f} с, максимум {max(seconds):.1f} с"
         if seconds else "- Время ответа: нет данных"),
        "",
    ]
    if aborted:
        lines += [f"**Прогон прерван:** провайдер недоступен {STOP_AFTER_UNAVAILABLE} раза подряд ({aborted}).", ""]
    lines += ["## Запуски", "", "| Место | Прогон | Исход | Время, с | Скрипты | Цель и причина | Разумно? (глазами) |",
              "|---|---|---|---|---|---|---|"]
    for r in results:
        cells = [r["world"], str(r["run"]), r["outcome"], f"{r['seconds']:.1f}", r["scripts"] or "—",
                 (r["summary"] or r["detail"] or "—").replace("|", "/").replace("\n", " ")[:160], ""]
        lines.append("| " + " | ".join(cells) + " |")
    lines += ["", "## Частые ошибки", ""]
    lines += [f"- ×{count}: {text}" for text, count in errors.most_common(5)] or ["- нет"]
    if answered and valid_percent < VALID_SHARE_TARGET:
        lines += ["", "## Предложение", "",
                  f"Доля валидных ответов {valid_percent} %, ниже {VALID_SHARE_TARGET} %. Варианты (решает главный агент): "
                  "переписать prompt/system.md на английский, сократить каталог или примеры, взять другую модель из "
                  "docs/EXECUTOR-MODELS.md."]
    config.home.mkdir(parents=True, exist_ok=True)
    path = config.home / f"smoke-{stamp}.md"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path
