import unittest

from rest_client import ApiError, NetworkError, RestClient
from tests.fake_rest import FakeRest
from tests.helpers import FakeClock


class RestClientTests(unittest.TestCase):
    def setUp(self):
        self.fake = FakeRest().start()
        self.addCleanup(self.fake.stop)
        self.fake.add_bot("spirit_1")
        self.clock = FakeClock()
        self.client = RestClient(self.fake.url, clock=self.clock, rps=5, timeout=2.0, get_retries=2)

    def test_get_Ok_ReturnsParsedJson(self):
        body = self.client.get("/v1/bots")
        self.assertEqual("spirit_1", body["bots"][0]["id"])

    def test_post_Ok_SendsJsonBodyAndReturnsParsed(self):
        body = self.client.post("/v1/bots/spirit_1/observe", {"radius": 16})
        self.assertEqual("spirit_1", body["bot"])
        self.assertEqual({"radius": 16}, self.fake.requests[-1]["body"], "тело уходит JSON-ом как есть")

    def test_error_EveryApiCode_BecomesApiErrorWithStatusCodeMessage(self):
        table = [(400, "invalid_request"), (403, "forbidden"), (404, "not_found"), (409, "conflict"),
                 (422, "invalid_plan"), (500, "internal")]
        for status, code in table:
            with self.subTest(status=status, code=code):
                self.fake.server_errors.append((status, code))
                with self.assertRaises(ApiError, msg=f"статус {status} обязан стать ApiError") as ctx:
                    self.client.post("/v1/plan", {"plan": {}})
                error = ctx.exception
                self.assertEqual((status, code), (error.status, error.code))
                self.assertIn(code, error.message, "сообщение сервера доходит до ApiError")
                self.assertEqual({"status": status}, error.details)

    def test_error_NotJsonBody_BecomesApiErrorBadResponse(self):
        self.fake.garbage_next = 1
        with self.assertRaises(ApiError) as ctx:
            self.client.post("/v1/plan", {"plan": {}})
        self.assertEqual("bad_response", ctx.exception.code, "мусор вместо JSON — отдельный код, а не падение разбора")

    def test_error_UnknownRoute_IsNotFoundApiError(self):
        with self.assertRaises(ApiError) as ctx:
            self.client.get("/v1/bots/ghost")
        self.assertEqual((404, "not_found"), (ctx.exception.status, ctx.exception.code))

    def test_get_ConnectionDrop_RetriedAndSucceeds(self):
        self.fake.drop_connection_next = 1
        body = self.client.get("/v1/bots")
        self.assertEqual("spirit_1", body["bots"][0]["id"])
        self.assertEqual(2, len(self.fake.requests_to("GET", "/v1/bots")), "обрыв GET повторяется")

    def test_post_ConnectionDrop_NotRetried(self):
        self.fake.drop_connection_next = 1
        with self.assertRaises(NetworkError):
            self.client.post("/v1/bots/spirit_1/observe", {})
        self.assertEqual(1, len(self.fake.requests_to("POST", "/v1/bots/spirit_1/observe")),
                         "POST не повторяется: повтор мог бы создать вторую задачу")

    def test_get_ServerDown_GivesUpAfterRetriesWithBackoff(self):
        url = self.fake.url
        self.fake.stop()
        client = RestClient(url, clock=self.clock, rps=5, timeout=1.0, get_retries=2)
        with self.assertRaises(NetworkError) as ctx:
            client.get("/v1/bots")
        self.assertIn(url, str(ctx.exception), "в сообщении адрес, который не отвечает")
        self.assertEqual(2, len([s for s in self.clock.sleeps if s > 0]), "две паузы между тремя попытками")

    def test_get_Retryable5xx_RetriedButPlain500Not(self):
        self.fake.server_errors.append(503)
        self.assertEqual("spirit_1", self.client.get("/v1/bots")["bots"][0]["id"], "503 на GET повторяется")
        self.fake.server_errors.append(500)
        with self.assertRaises(ApiError) as ctx:
            self.client.get("/v1/bots")
        self.assertEqual(500, ctx.exception.status, "500 internal не повторяется: это ответ мода")

    def test_post_Retryable5xx_NotRetried(self):
        self.fake.server_errors.extend([503, 503])
        with self.assertRaises(ApiError) as ctx:
            self.client.post("/v1/bots/spirit_1/observe", {})
        self.assertEqual(503, ctx.exception.status)
        self.assertEqual(1, len(self.fake.requests_to("POST", "/v1/bots/spirit_1/observe")), "POST на 5xx не повторяется")

    def test_rateLimited_WaitsRetryAfterThenRetries(self):
        self.fake.rate_limited.append(3)
        before = self.clock.t
        body = self.client.post("/v1/bots/spirit_1/observe", {})
        self.assertEqual("spirit_1", body["bot"])
        self.assertGreaterEqual(self.clock.t - before, 3, "клиент обязан выждать Retry-After")
        self.assertIn(3, self.clock.sleeps)

    def test_rateLimited_Persistent_RaisesRateLimitedAfterLimit(self):
        self.fake.rate_limited.extend([1, 1, 1, 1, 1, 1])
        with self.assertRaises(ApiError) as ctx:
            self.client.get("/v1/bots")
        self.assertEqual((429, "rate_limited"), (ctx.exception.status, ctx.exception.code))
        self.assertLessEqual(len(self.fake.requests), 5, "клиент не долбит сервер бесконечно")

    def test_timeout_SlowResponse_RaisesNetworkErrorForPost(self):
        client = RestClient(self.fake.url, clock=self.clock, rps=5, timeout=0.15, get_retries=2)
        self.fake.delay_next.append(0.5)
        with self.assertRaises(NetworkError):
            client.post("/v1/bots/spirit_1/observe", {})

    def test_limiter_BurstOfRequests_NeverMoreThanFivePerSecond(self):
        for _ in range(23):
            self.client.get("/v1/bots")
        times = self.client.sent_at
        self.assertEqual(23, len(times))
        for i, start in enumerate(times):
            in_window = [t for t in times if start - 1.0 < t <= start]
            self.assertLessEqual(len(in_window), 5, f"в окне 1 с, закрывающемся на запросе {i}, больше 5 запросов")
        self.assertGreaterEqual(times[-1] - times[0], 3.0, "23 запроса при 5/с занимают не меньше 3 с модельного времени")

    def test_limiter_SlowCallers_NotDelayed(self):
        for _ in range(4):
            self.client.get("/v1/bots")
            self.clock.advance(1.0)
        self.assertEqual([], [s for s in self.clock.sleeps if s > 0], "редкие запросы паузы не получают")

    def test_limiter_RateConfigurable(self):
        slow = RestClient(self.fake.url, clock=self.clock, rps=1, timeout=2.0)
        for _ in range(3):
            slow.get("/v1/bots")
        self.assertGreaterEqual(slow.sent_at[2] - slow.sent_at[0], 2.0, "rps=1: три запроса не быстрее 2 с")


if __name__ == "__main__":
    unittest.main()
