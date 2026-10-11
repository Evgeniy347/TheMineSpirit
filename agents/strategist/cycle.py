"""Один цикл стратега: капабилити -> духи -> для каждого простаивающего: страж, observe, промпт, мозг, проверки, отправка."""

from __future__ import annotations

from dataclasses import dataclass

from brain import BrainError, BrainUnavailable
from guard import ProviderHealth, fingerprint, request_id
from parse import ReplyError, parse_reply
from plan_check import Catalog, CatalogError, PlanError, PlanLimits, Policy, check_plan
from prompt import Prompter
from rest_client import ApiError, NetworkError
from runtime import Runtime
from tracker import Tracker

MAX_ATTEMPTS = 2
ROUTE_MISSING_STATUSES = (404, 405)
RETRY_HEADER = "# Ошибка предыдущего ответа"


@dataclass
class CycleSummary:
    cycle: int
    posted: int = 0
    dry: int = 0
    skipped: int = 0
    rejected: int = 0


@dataclass
class Accepted:
    reason: str
    goal: str
    plan: dict
    model: str
    prompt_sha: str
    obs_hash: str | None


@dataclass
class Evaluation:
    kind: str
    error: str | None = None
    accepted: Accepted | None = None


class CycleRunner:
    def __init__(self, rt: Runtime, brain, prompter: Prompter, policy: Policy, limits: PlanLimits, tracker: Tracker):
        self.rt = rt
        self.brain = brain
        self.prompter = prompter
        self.policy = policy
        self.limits = limits
        self.tracker = tracker
        self.provider = ProviderHealth(rt.config.interval, rt.config.max_interval_backoff)
        self.stop_requested = False
        self.dry_forced = False
        self.plan_route_missing = False
        self.provider_down = False
        self._tasks_route_alerted = False

    @property
    def effective_mode(self) -> str:
        return "dry" if self.rt.config.mode == "dry" or self.dry_forced else "live"

    def run(self) -> CycleSummary:
        rt = self.rt
        rt.state["cycle"] += 1
        cycle = rt.state["cycle"]
        summary = CycleSummary(cycle)
        self.provider_down = False
        rt.guard.begin_cycle()
        rt.guard.prune(rt.clock.now())
        rt.log("cycle_start", cycle=cycle, mode=self.effective_mode, interval=self.provider.interval(),
               active_tasks=len(rt.state["active"]))
        try:
            catalog = Catalog.from_capabilities(rt.client.get("/v1/capabilities"))
            bots = rt.client.get("/v1/bots").get("bots")
            if not isinstance(bots, list):
                raise CatalogError("в ответе GET /v1/bots нет списка bots")
        except (ApiError, NetworkError, CatalogError) as error:
            rt.alert(f"цикл {cycle} прерван: каталог или список духов недоступны ({error}); проверьте SPIRIT_API и сервер",
                     code="rest_unavailable", cycle=cycle)
            rt.save()
            return summary
        self._recover_pending(cycle)
        if self.provider.needs_probe and not self._probe(cycle):
            self.provider_down = True
        for bot in bots:
            if self.stop_requested:
                break
            self._handle_safely(bot, cycle, catalog, summary)
        rt.save()
        rt.say(f"цикл {cycle}: отправлено {summary.posted}, в dry {summary.dry}, пропущено {summary.skipped}, отклонено {summary.rejected}")
        return summary

    def _handle_safely(self, bot: dict, cycle: int, catalog: Catalog, summary: CycleSummary) -> None:
        try:
            self._handle_bot(bot, cycle, catalog, summary)
        except Exception as error:
            if self.rt.strict:
                raise
            self.rt.log("bot_skipped", cycle=cycle, bot=str(bot.get("id")) if isinstance(bot, dict) else None,
                        reason=f"внутренняя ошибка обработки: {type(error).__name__}: {error}", code="internal_error")
            self.rt.say(f"ошибка при обработке духа: {type(error).__name__}: {error}")

    def _skip(self, summary: CycleSummary, cycle: int, bot: str, reason: str, code: str = "") -> None:
        summary.skipped += 1
        self.rt.log("bot_skipped", cycle=cycle, bot=bot, reason=reason, code=code or None)

    def _probe(self, cycle: int) -> bool:
        try:
            self.brain.probe()
        except BrainUnavailable as error:
            self.provider.failure()
            self.rt.log("brain_failure", cycle=cycle, kind=error.kind, reason=error.text, probe=True)
            return False
        except BrainError as error:
            self.rt.log("brain_failure", cycle=cycle, kind="error", reason=str(error), probe=True)
            return False
        self.provider.success()
        return True

    def _provider_failed(self, cycle: int, bot_id: str, error: BrainUnavailable, summary: CycleSummary) -> None:
        if not self.provider_down:
            self.provider.failure()
            self.provider_down = True
        self.rt.log("brain_failure", cycle=cycle, bot=bot_id, kind=error.kind, reason=error.text)
        summary.skipped += 1

    def _handle_bot(self, bot: dict, cycle: int, catalog: Catalog, summary: CycleSummary) -> None:
        rt = self.rt
        bot_id = str(bot.get("id"))
        if bot.get("state") != "idle":
            return self._skip(summary, cycle, bot_id,
                              f"дух в состоянии {bot.get('state')}: задачи игрока и текущие важнее стратега", "not_idle")
        if any(entry["bot"] == bot_id for entry in rt.state["active"].values()):
            return self._skip(summary, cycle, bot_id, "задача духа ещё отслеживается", "tracked")
        if rt.config.pause_path.exists():
            return self._skip(summary, cycle, bot_id, "файл PAUSE: новых задач нет, отслеживание продолжается", "pause_file")
        if self.provider_down:
            return self._skip(summary, cycle, bot_id, "провайдер модели недоступен: вызовов в этом цикле нет", "provider_down")
        verdict = rt.guard.allow_brain_call(rt.clock.now(), bot_id)
        if not verdict.ok:
            return self._skip(summary, cycle, bot_id, verdict.reason, verdict.code)
        try:
            observation = rt.client.post(f"/v1/bots/{bot_id}/observe",
                                         {"radius": rt.config.observe_radius, "include_entities": True})
        except (ApiError, NetworkError) as error:
            return self._skip(summary, cycle, bot_id, f"observe не удался: {error}", "observe_failed")
        obs_hash = observation.get("hash")
        rt.log("observed", cycle=cycle, bot=bot_id, hash=obs_hash, blocks=len(observation.get("blocks", [])),
               entities=len(observation.get("entities", [])), inventory=len(observation.get("inventory", [])))
        verdict = rt.guard.allow_brain_call(rt.clock.now(), bot_id, obs_hash)
        if not verdict.ok:
            return self._skip(summary, cycle, bot_id, verdict.reason, verdict.code)
        accepted = self._decide(bot, cycle, catalog, observation, summary)
        if accepted is not None:
            self._send(bot_id, cycle, accepted, summary)

    def _decide(self, bot: dict, cycle: int, catalog: Catalog, observation: dict, summary: CycleSummary) -> Accepted | None:
        rt = self.rt
        bot_id = str(bot.get("id"))
        obs_hash = observation.get("hash")
        prompt = self.prompter.build(catalog.raw, bot, observation, rt.recent_for(bot_id), rt.clock.now())
        error_text = None
        for attempt in range(MAX_ATTEMPTS):
            verdict = rt.guard.allow_brain_call(rt.clock.now(), bot_id, retry=attempt > 0)
            if not verdict.ok:
                self._skip(summary, cycle, bot_id, verdict.reason, verdict.code)
                return None
            user = prompt.user if error_text is None else self._retry_user(prompt.user, error_text)
            try:
                reply = self.brain.complete(prompt.system, user)
            except BrainUnavailable as error:
                rt.guard.record_brain_call(rt.clock.now(), bot_id)
                self._provider_failed(cycle, bot_id, error, summary)
                return None
            except BrainError as error:
                rt.guard.record_brain_call(rt.clock.now(), bot_id)
                rt.log("brain_failure", cycle=cycle, bot=bot_id, kind="error", reason=str(error), prompt_sha=prompt.sha)
                summary.skipped += 1
                return None
            rt.guard.record_brain_call(rt.clock.now(), bot_id)
            self.provider.success()
            evaluation = self._evaluate(cycle, bot_id, reply, prompt.sha, catalog, summary)
            if evaluation.kind == "skip":
                rt.guard.record_decision(bot_id, obs_hash)
                return None
            if evaluation.kind == "abort":
                return None
            if evaluation.kind == "accepted":
                evaluation.accepted.obs_hash = obs_hash
                return evaluation.accepted
            error_text = evaluation.error
        rt.guard.record_decision(bot_id, obs_hash)
        return None

    @staticmethod
    def _retry_user(user: str, error: str) -> str:
        return f"{user}\n\n{RETRY_HEADER}\n{error.replace('<', '‹')}\nИсправь ответ и верни только JSON."

    def _evaluate(self, cycle: int, bot_id: str, reply, prompt_sha: str, catalog: Catalog, summary: CycleSummary) -> Evaluation:
        rt = self.rt
        try:
            decision = parse_reply(reply.text)
        except ReplyError as error:
            summary.rejected += 1
            rt.log("rejected", cycle=cycle, bot=bot_id, stage="parse", reason=str(error), reply=reply.text, model=reply.model,
                   prompt_sha=prompt_sha)
            return Evaluation("retry", str(error))
        rt.log("decision", cycle=cycle, bot=bot_id, model=reply.model, prompt_sha=prompt_sha, reason=decision.reason,
               goal=decision.goal, plan=decision.plan, skip=True if decision.skip else None, reply=reply.text,
               seconds=reply.seconds, tool_attempts=reply.tool_attempts or None)
        if decision.skip:
            return Evaluation("skip")
        try:
            check_plan(decision.plan, catalog, self.policy, self.limits)
        except PlanError as error:
            summary.rejected += 1
            rt.log("rejected", cycle=cycle, bot=bot_id, stage="plan_check", reason=str(error), goal=decision.goal,
                   plan=decision.plan, model=reply.model, prompt_sha=prompt_sha)
            return Evaluation("retry", str(error))
        server = self._server_check(cycle, bot_id, decision, reply.model, prompt_sha, summary)
        if server is not None:
            return server
        return Evaluation("accepted", accepted=Accepted(decision.reason, decision.goal, decision.plan, reply.model, prompt_sha, None))

    def _server_check(self, cycle: int, bot_id: str, decision, model: str, prompt_sha: str, summary: CycleSummary):
        rt = self.rt
        if self.plan_route_missing:
            return None
        try:
            rt.client.post("/v1/plan", {"plan": decision.plan})
        except ApiError as error:
            if error.status in ROUTE_MISSING_STATUSES:
                self.plan_route_missing = True
                rt.alert("маршрута POST /v1/plan в моде нет: дальше план проверяется только локально", code="plan_route_missing",
                         cycle=cycle)
                return None
            if error.status == 422 or error.code == "invalid_plan":
                summary.rejected += 1
                rt.log("rejected", cycle=cycle, bot=bot_id, stage="server", reason=error.message, goal=decision.goal,
                       plan=decision.plan, model=model, prompt_sha=prompt_sha)
                return Evaluation("retry", error.message)
            self._skip(summary, cycle, bot_id, f"проверка плана на сервере не удалась: {error}", "plan_check_failed")
            return Evaluation("abort")
        except NetworkError as error:
            self._skip(summary, cycle, bot_id, f"проверка плана на сервере не удалась: {error}", "plan_check_failed")
            return Evaluation("abort")
        return None

    def _send(self, bot_id: str, cycle: int, accepted: Accepted, summary: CycleSummary) -> None:
        rt = self.rt
        plan_fingerprint = fingerprint(accepted.goal, accepted.plan)
        verdict = rt.guard.allow_post(rt.clock.now(), bot_id, plan_fingerprint)
        if not verdict.ok:
            summary.rejected += 1
            rt.log("rejected", cycle=cycle, bot=bot_id, stage="guard", reason=verdict.reason, code=verdict.code,
                   goal=accepted.goal, plan=accepted.plan, fingerprint=plan_fingerprint, model=accepted.model)
            return
        try:
            fresh = rt.client.get(f"/v1/bots/{bot_id}")
        except (ApiError, NetworkError) as error:
            return self._skip(summary, cycle, bot_id, f"перед отправкой не удалось перечитать духа: {error}", "recheck_failed")
        if fresh.get("state") != "idle":
            return self._skip(summary, cycle, bot_id,
                              f"после наблюдения дух стал {fresh.get('state')}: решение отброшено", "became_busy")
        rid = request_id(bot_id, cycle, plan_fingerprint)
        base = {"request_id": rid, "goal": accepted.goal, "plan": accepted.plan, "fingerprint": plan_fingerprint,
                "reason": accepted.reason, "model": accepted.model, "prompt_sha": accepted.prompt_sha, "cycle": cycle,
                "obs_hash": accepted.obs_hash}
        if self.effective_mode == "dry":
            rt.guard.record_post(rt.clock.now(), bot_id, plan_fingerprint)
            rt.guard.record_decision(bot_id, accepted.obs_hash)
            summary.dry += 1
            rt.log("posted", cycle=cycle, bot=bot_id, dry=True, outcome="would_post", request_id=rid, goal=accepted.goal,
                   plan=accepted.plan, fingerprint=plan_fingerprint, model=accepted.model, prompt_sha=accepted.prompt_sha)
            return
        rt.state["pending"][bot_id] = base
        rt.save()
        if self._post(bot_id, base, recovered=False):
            summary.posted += 1

    def _recover_pending(self, cycle: int) -> None:
        rt = self.rt
        for bot_id, pending in list(rt.state["pending"].items()):
            if self.effective_mode != "live":
                del rt.state["pending"][bot_id]
                rt.alert(f"у духа {bot_id} была неотправленная задача, но режим теперь dry: решение сброшено", code="pending_dropped",
                         cycle=cycle, bot=bot_id)
                continue
            self._post(bot_id, pending, recovered=True)

    def _post(self, bot_id: str, pending: dict, recovered: bool) -> bool:
        rt = self.rt
        cycle = pending["cycle"]
        body = {"request_id": pending["request_id"], "goal": pending["goal"], "plan": pending["plan"]}
        try:
            response = rt.client.post(f"/v1/bots/{bot_id}/tasks", body)
        except NetworkError as error:
            rt.alert(f"ответ на POST задачи духа {bot_id} не получен ({error}); решение сохранено и будет отправлено повторно "
                     "с тем же request_id", code="post_unconfirmed", cycle=cycle, bot=bot_id)
            return False
        except ApiError as error:
            del rt.state["pending"][bot_id]
            if error.status in ROUTE_MISSING_STATUSES:
                self._tasks_route_missing(cycle, error)
            else:
                rt.guard.record_decision(bot_id, pending["obs_hash"])
                rt.log("rejected", cycle=cycle, bot=bot_id, stage="post", reason=str(error), goal=pending["goal"],
                       plan=pending["plan"], request_id=pending["request_id"], fingerprint=pending["fingerprint"])
            rt.save()
            return False
        task_id = response.get("task_id")
        now = rt.clock.now()
        del rt.state["pending"][bot_id]
        self.tracker.add(task_id, bot_id, pending["goal"], pending["request_id"], pending["fingerprint"], cycle, now)
        rt.guard.record_post(now, bot_id, pending["fingerprint"])
        rt.guard.record_decision(bot_id, pending["obs_hash"])
        rt.log("posted", cycle=cycle, bot=bot_id, dry=False, request_id=pending["request_id"], task_id=task_id,
               goal=pending["goal"], plan=pending["plan"], fingerprint=pending["fingerprint"], model=pending["model"],
               prompt_sha=pending["prompt_sha"], recovered=True if recovered else None)
        rt.save()
        return True

    def _tasks_route_missing(self, cycle: int, error: ApiError) -> None:
        self.dry_forced = True
        if not self._tasks_route_alerted:
            self._tasks_route_alerted = True
            self.rt.alert(f"маршрута постановки задач в моде нет ({error}): до конца запуска режим dry, "
                          "задачи в журнал как «отправил бы»", code="tasks_route_missing", cycle=cycle)
