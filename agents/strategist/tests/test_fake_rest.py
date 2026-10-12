"""Тест харнесса: каждая ручка фейкового REST действительно меняет ответ (иначе сценарии зеленеют вхолостую);
in-process клиент отвечает так же, как сокет, — сценарии без сети проверяют то же поведение."""

import json
import time
import unittest
import urllib.error
import urllib.request

from rest_client import ApiError, RestClient
from tests.fake_rest import FakeRest, InProcessClient, default_capabilities, observation_hash
from tests.helpers import FakeClock


def call(base, method, path, body=None, timeout=5):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(base + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, dict(response.headers), json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        return error.code, dict(error.headers), json.loads(raw)


def task_body(request_id="r-1", goal="срубить деревья"):
    return {"request_id": request_id, "goal": goal,
            "plan": {"type": "script", "script": "chop_tree", "params": {"count": 3}}}


class FakeRestTests(unittest.TestCase):
    def setUp(self):
        self.fake = FakeRest().start()
        self.addCleanup(self.fake.stop)
        self.fake.add_bot("spirit_1", name="Первый")
        self.base = self.fake.url

    def test_capabilities_ReturnsFixtureCatalog(self):
        status, _, body = call(self.base, "GET", "/v1/capabilities")
        self.assertEqual(200, status, "capabilities должен отвечать 200")
        self.assertEqual(default_capabilities(), body, "фейк обязан отдавать каталог из fixtures/capabilities.json")
        self.assertIn("goto", [s["id"] for s in body["scripts"]], "в образце каталога должен быть скрипт goto")

    def test_capabilities_Replaced_ChangesResponse(self):
        self.fake.capabilities = {"scripts": []}
        _, _, body = call(self.base, "GET", "/v1/capabilities")
        self.assertEqual({"scripts": []}, body, "подмена каталога ручкой должна менять ответ")

    def test_bots_ListAndSingle_ReflectState(self):
        self.fake.add_bot("spirit_2", state="busy")
        _, _, listing = call(self.base, "GET", "/v1/bots")
        states = {b["id"]: b["state"] for b in listing["bots"]}
        self.assertEqual({"spirit_1": "idle", "spirit_2": "busy"}, states, "список духов отражает состояния")
        self.fake.set_state("spirit_1", "dead")
        _, _, single = call(self.base, "GET", "/v1/bots/spirit_1")
        self.assertEqual("dead", single["state"], "смена состояния ручкой должна менять ответ GET /bots/{id}")

    def test_botsAsList_Knob_ReturnsBareArray(self):
        self.assertIsInstance(call(self.base, "GET", "/v1/bots")[2], dict)
        self.fake.bots_as_list = True
        self.assertIsInstance(call(self.base, "GET", "/v1/bots")[2], list, "ручка bots_as_list отдаёт голый массив")

    def test_bot_Unknown_Returns404WithIdInDetails(self):
        status, _, body = call(self.base, "GET", "/v1/bots/ghost")
        self.assertEqual(404, status)
        self.assertEqual("not_found", body["error"]["code"])
        self.assertEqual("ghost", body["error"]["details"]["id"])

    def test_observe_HashStableAcrossTicksAndChangesWithWorld(self):
        _, _, first = call(self.base, "POST", "/v1/bots/spirit_1/observe", {})
        _, _, second = call(self.base, "POST", "/v1/bots/spirit_1/observe", {})
        self.assertNotEqual(first["tick"], second["tick"], "tick растёт между запросами")
        self.assertEqual(first["hash"], second["hash"], "hash не зависит от tick")
        self.assertEqual(64, len(first["hash"]), "hash — 64 hex-символа")
        self.fake.set_observation("spirit_1", {"radius": 32, "pos": [1, 2, 3], "inventory": [{"item": "x", "count": 1}],
                                                "blocks": [], "entities": [], "containers": []})
        _, _, third = call(self.base, "POST", "/v1/bots/spirit_1/observe", {})
        self.assertNotEqual(first["hash"], third["hash"], "смена картины мира должна менять hash")
        self.assertEqual("spirit_1", third["bot"])

    def test_observationHash_IgnoresDistanceAndTick(self):
        a = {"pos": [0, 0, 0], "entities": [{"type": "cow", "pos": [1, 1, 1], "distance": 1.0}], "tick": 1}
        b = {"pos": [0, 0, 0], "entities": [{"type": "cow", "pos": [1, 1, 1], "distance": 9.0}], "tick": 2}
        self.assertEqual(observation_hash(a), observation_hash(b))

    def test_observe_BusyAfterObserveKnob_FlipsBotToBusy(self):
        call(self.base, "POST", "/v1/bots/spirit_1/observe", {})
        self.assertEqual("idle", self.fake.bots["spirit_1"]["state"], "без ручки observe состояние не меняет")
        self.fake.busy_after_observe.add("spirit_1")
        call(self.base, "POST", "/v1/bots/spirit_1/observe", {})
        self.assertEqual("busy", self.fake.bots["spirit_1"]["state"], "ручка busy_after_observe делает духа занятым")

    def test_postTask_Accepted_MakesBotBusyAndLogsRequest(self):
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(202, status)
        self.assertEqual("accepted", body["state"])
        self.assertEqual("r-1", body["request_id"])
        self.assertEqual("busy", self.fake.bots["spirit_1"]["state"])
        posted = self.fake.posted_tasks()
        self.assertEqual(1, len(posted), "журнал запросов фейка должен содержать POST задачи")
        self.assertEqual("r-1", posted[0]["body"]["request_id"])

    def test_postTask_SameRequestIdSameBody_ReturnsSameTaskWithoutDuplicate(self):
        _, _, first = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        status, _, second = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(202, status)
        self.assertEqual(first["task_id"], second["task_id"], "повтор с тем же request_id — та же задача")
        self.assertEqual(1, len(self.fake.tasks), "дубль задачи создаваться не должен")

    def test_postTask_SameRequestIdOtherBody_Returns409(self):
        call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body(goal="другая цель"))
        self.assertEqual(409, status)
        self.assertEqual("conflict", body["error"]["code"])

    def test_postTask_BusyBot_Returns409(self):
        self.fake.set_state("spirit_1", "busy")
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(409, status, "занятый дух не принимает новую задачу")
        self.assertEqual("conflict", body["error"]["code"])
        self.assertEqual(0, len(self.fake.tasks))

    def test_postTask_RouteDisabled_Returns404(self):
        self.fake.tasks_route = False
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(404, status, "ручка tasks_route=False имитирует мод без чеклиста 15")
        self.assertEqual("not_found", body["error"]["code"])

    def test_task_SucceedsAfterKPolls(self):
        self.fake.set_fate(None, "succeeded", after_polls=3)
        _, _, posted = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        states = [call(self.base, "GET", f"/v1/tasks/{posted['task_id']}")[2]["state"] for _ in range(3)]
        self.assertEqual(["running", "running", "succeeded"], states, "успех ровно на K-м опросе")
        self.assertEqual("idle", self.fake.bots["spirit_1"]["state"], "после завершения дух снова свободен")

    def test_task_FailsWithReason(self):
        self.fake.set_fate("spirit_1", "failed", after_polls=1, error="нет подходящей жилы в радиусе")
        _, _, posted = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        _, _, view = call(self.base, "GET", f"/v1/tasks/{posted['task_id']}")
        self.assertEqual("failed", view["state"])
        self.assertEqual("нет подходящей жилы в радиусе", view["error"])

    def test_task_PerBotFateOverridesDefault(self):
        self.fake.add_bot("spirit_2")
        self.fake.set_fate("spirit_2", "failed", after_polls=1)
        _, _, a = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body("r-a"))
        _, _, b = call(self.base, "POST", "/v1/bots/spirit_2/tasks", task_body("r-b"))
        state_a = call(self.base, "GET", f"/v1/tasks/{a['task_id']}")[2]["state"]
        state_b = call(self.base, "GET", f"/v1/tasks/{b['task_id']}")[2]["state"]
        self.assertEqual(("running", "failed"), (state_a, state_b), "судьба задана для духа, умолчание — для остальных")

    def test_task_NeverFinishes_StaysRunning(self):
        self.fake.set_fate(None, "never")
        _, _, posted = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        states = {call(self.base, "GET", f"/v1/tasks/{posted['task_id']}")[2]["state"] for _ in range(6)}
        self.assertEqual({"running"}, states)

    def test_deleteTask_Cancels_AndFreesBot(self):
        self.fake.set_fate(None, "never")
        _, _, posted = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        status, _, view = call(self.base, "DELETE", f"/v1/tasks/{posted['task_id']}")
        self.assertEqual((200, "cancelled"), (status, view["state"]))
        self.assertEqual("idle", self.fake.bots["spirit_1"]["state"])
        self.assertEqual("cancelled", call(self.base, "GET", f"/v1/tasks/{posted['task_id']}")[2]["state"])

    def test_forgetTasks_SimulatesRestart_TaskBecomes404(self):
        _, _, posted = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.fake.forget_tasks()
        status, _, body = call(self.base, "GET", f"/v1/tasks/{posted['task_id']}")
        self.assertEqual(404, status)
        self.assertEqual("idle", self.fake.bots["spirit_1"]["state"], "после «рестарта мода» дух свободен")

    def test_plan_ValidByDefault_ErrorKnobGives422_RouteKnobGives404(self):
        status, _, body = call(self.base, "POST", "/v1/plan", {"plan": {}})
        self.assertEqual((200, {"valid": True}), (status, body))
        self.fake.plan_error = "Неизвестный скрипт 'x'"
        status, _, body = call(self.base, "POST", "/v1/plan", {"plan": {}})
        self.assertEqual(422, status)
        self.assertEqual("invalid_plan", body["error"]["code"])
        self.assertIn("Неизвестный скрипт", body["error"]["message"])
        self.fake.plan_error = None
        self.fake.plan_route = False
        self.assertEqual(404, call(self.base, "POST", "/v1/plan", {"plan": {}})[0])

    def test_rateLimited_Knob_Gives429WithRetryAfterThenRecovers(self):
        self.assertEqual(200, call(self.base, "GET", "/v1/bots")[0])
        self.fake.rate_limited.append(2)
        status, headers, body = call(self.base, "GET", "/v1/bots")
        self.assertEqual(429, status)
        self.assertEqual("2", headers["Retry-After"])
        self.assertEqual("rate_limited", body["error"]["code"])
        self.assertEqual(200, call(self.base, "GET", "/v1/bots")[0], "ручка срабатывает один раз на элемент очереди")

    def test_serverErrors_Knob_Gives5xxThenRecovers(self):
        self.fake.server_errors.extend([503, 500])
        self.assertEqual([503, 500, 200], [call(self.base, "GET", "/v1/bots")[0] for _ in range(3)])

    def test_serverErrors_TupleEntry_SetsStatusAndCode(self):
        self.fake.server_errors.append((422, "invalid_plan"))
        status, _, body = call(self.base, "GET", "/v1/bots")
        self.assertEqual((422, "invalid_plan"), (status, body["error"]["code"]))
        self.assertEqual({"status": 422}, body["error"]["details"])

    def test_taskPostErrors_Knob_AffectsOnlyTaskPost(self):
        self.fake.task_post_errors.append((409, "conflict"))
        self.assertEqual(200, call(self.base, "GET", "/v1/bots")[0], "очередь ошибок постановки не трогает другие ручки")
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual((409, "conflict"), (status, body["error"]["code"]))
        self.assertEqual(0, len(self.fake.tasks), "при ошибке задача не создаётся")
        self.assertEqual(202, call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())[0])

    def test_vanishOnTaskPost_Knob_RemovesBotAndAnswers404(self):
        self.fake.vanish_on_task_post = {"spirit_1"}
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual((404, "not_found"), (status, body["error"]["code"]))
        self.assertEqual(404, call(self.base, "GET", "/v1/bots/spirit_1")[0], "после этого духа на сервере нет")

    def test_botGetErrors_Knob_NoneMeansPassThrough(self):
        self.fake.bot_get_errors.extend([None, (500, "internal")])
        self.assertEqual([200, 500, 200], [call(self.base, "GET", "/v1/bots/spirit_1")[0] for _ in range(3)])

    def test_observeErrors_Knob_AffectsOnlyObserve(self):
        self.fake.observe_errors.append((500, "internal"))
        self.assertEqual(200, call(self.base, "GET", "/v1/bots/spirit_1")[0])
        self.assertEqual(500, call(self.base, "POST", "/v1/bots/spirit_1/observe", {})[0])
        self.assertEqual(200, call(self.base, "POST", "/v1/bots/spirit_1/observe", {})[0])

    def test_taskPostLoseResponse_Knob_CreatesTaskButBreaksConnection(self):
        self.fake.task_post_lose_response = 1
        with self.assertRaises((urllib.error.URLError, ConnectionError, OSError)):
            call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(1, len(self.fake.tasks), "сервер задачу создал, клиент ответа не увидел")
        status, _, body = call(self.base, "POST", "/v1/bots/spirit_1/tasks", task_body())
        self.assertEqual(202, status)
        self.assertEqual(1, len(self.fake.tasks), "повтор с тем же request_id дубля не создаёт")

    def test_clock_Knob_StampsRequestsWithFakeTime(self):
        class Clock:
            def now(self):
                return 4242.0
        call(self.base, "GET", "/v1/bots")
        self.assertNotIn("fake_t", self.fake.requests[-1])
        self.fake.clock = Clock()
        call(self.base, "GET", "/v1/bots")
        self.assertEqual(4242.0, self.fake.requests[-1]["fake_t"])

    def test_delayNext_Knob_MakesResponseSlow(self):
        started = time.monotonic()
        call(self.base, "GET", "/v1/bots")
        fast = time.monotonic() - started
        self.fake.delay_next.append(0.4)
        started = time.monotonic()
        call(self.base, "GET", "/v1/bots")
        slow = time.monotonic() - started
        self.assertGreater(slow, fast + 0.3, "ручка delay_next должна замедлять ответ")

    def test_dropConnection_Knob_BreaksRequest(self):
        self.fake.drop_connection_next = 1
        with self.assertRaises((urllib.error.URLError, ConnectionError, OSError), msg="обрыв должен быть исключением"):
            call(self.base, "GET", "/v1/bots")
        self.assertEqual(200, call(self.base, "GET", "/v1/bots")[0])

    def test_garbageNext_Knob_ReturnsNonJson(self):
        self.fake.garbage_next = 1
        with self.assertRaises(ValueError, msg="ответ-мусор не разбирается как JSON"):
            call(self.base, "GET", "/v1/bots")
        self.assertEqual(200, call(self.base, "GET", "/v1/bots")[0])

    def test_unknownPath_Returns404(self):
        self.assertEqual(404, call(self.base, "GET", "/v1/nope")[0])

    def clients(self):
        """HTTP-клиент по сокету и in-process клиент над тем же фейком — оба транспорта один ответ."""
        return (RestClient(self.base, clock=FakeClock(), rps=1000, timeout=2.0),
                InProcessClient(self.fake, clock=FakeClock(), rps=1000, timeout=2.0))

    def test_garbageNext_HttpAndInProcess_GiveSameBadResponse(self):
        seen = []
        for client in self.clients():
            with self.subTest(client=type(client).__name__):
                self.fake.garbage_next = 1
                with self.assertRaises(ApiError) as ctx:
                    client.get("/v1/bots")
                seen.append((ctx.exception.code, ctx.exception.status))
                self.assertEqual(("bad_response", 200), seen[-1], "мусор вместо JSON — ошибка контракта на обоих "
                                                                 "транспортах, а не пустой объект в одном из них")
        self.assertEqual(seen[0], seen[1], "in-process транспорт отдаёт те же байты, что ушёл бы по сокету")

    def test_dropConnection_HttpAndInProcess_RetryGetTheSameWay(self):
        attempts = []
        for client in self.clients():
            with self.subTest(client=type(client).__name__):
                self.fake.requests.clear()
                self.fake.drop_connection_next = 1
                body = client.get("/v1/bots")
                self.assertEqual("spirit_1", body["bots"][0]["id"], "обрыв GET повторяется на обоих транспортах")
                attempts.append(len(self.fake.requests))
        self.assertEqual([2, 2], attempts, "по одному обрыву и одной повторной попытке: сокет и процесс считают одинаково")


if __name__ == "__main__":
    unittest.main()
