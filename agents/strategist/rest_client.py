"""Клиент REST мода (docs/API.md) на urllib: тайм-ауты, ApiError, повторы только GET, лимитер, Retry-After."""

from __future__ import annotations

import http.client
import json
import time
import urllib.error
import urllib.request

RETRYABLE_STATUSES = (502, 503, 504)
MAX_RETRY_AFTER = 60.0
WINDOW_SECONDS = 1.0


class RealClock:
    def now(self) -> float:
        return time.time()

    def sleep(self, seconds: float) -> None:
        time.sleep(seconds)


class ApiError(Exception):
    def __init__(self, code: str, status: int, message: str, details: dict | None = None):
        super().__init__(f"{code} ({status}): {message}")
        self.code = code
        self.status = status
        self.message = message
        self.details = details or {}


class NetworkError(Exception):
    pass


class RestClient:
    def __init__(self, base: str, clock=None, rps: int = 5, timeout: float = 10.0, get_retries: int = 2,
                 rate_limit_retries: int = 3, backoff: float = 1.0):
        self.base = base.rstrip("/")
        self.clock = clock or RealClock()
        self.rps = rps
        self.timeout = timeout
        self.get_retries = get_retries
        self.rate_limit_retries = rate_limit_retries
        self.backoff = backoff
        self.sent_at: list[float] = []
        self._opener: urllib.request.OpenerDirector | None = None

    def get(self, path: str) -> dict:
        return self._request("GET", path, None)

    def post(self, path: str, body: dict) -> dict:
        return self._request("POST", path, body)

    def delete(self, path: str) -> dict:
        return self._request("DELETE", path, None)

    def _request(self, method: str, path: str, body) -> dict:
        network_failures = 0
        throttled = 0
        while True:
            self._throttle()
            try:
                status, headers, raw = self._send(method, path, body)
            except (urllib.error.URLError, OSError, http.client.HTTPException) as error:
                network_failures += 1
                if method != "GET" or network_failures > self.get_retries:
                    raise NetworkError(
                        f"{method} {self.base}{path}: нет ответа ({error}); проверьте, что сервер Minecraft и REST мода "
                        f"запущены и SPIRIT_API указывает на них") from error
                self.clock.sleep(self.backoff * 2 ** (network_failures - 1))
                continue
            if status == 429 and throttled < self.rate_limit_retries:
                throttled += 1
                self.clock.sleep(self._retry_after(headers))
                continue
            if method == "GET" and status in RETRYABLE_STATUSES and network_failures < self.get_retries:
                network_failures += 1
                self.clock.sleep(self.backoff * 2 ** (network_failures - 1))
                continue
            return self._finish(method, path, status, raw)

    def _throttle(self) -> None:
        while True:
            now = self.clock.now()
            recent = [t for t in self.sent_at if t > now - WINDOW_SECONDS]
            if len(recent) < self.rps:
                break
            self.clock.sleep(max(recent[0] + WINDOW_SECONDS - now, 0.001))
        self.sent_at.append(self.clock.now())
        del self.sent_at[:-1000]

    def _send(self, method: str, path: str, body):
        if self._opener is None:
            self._opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base + path, data=data, method=method,
                                         headers={"Content-Type": "application/json; charset=utf-8",
                                                  "Accept": "application/json"})
        try:
            with self._opener.open(request, timeout=self.timeout) as response:
                return response.status, response.headers, response.read()
        except urllib.error.HTTPError as error:
            try:
                return error.code, error.headers, error.read()
            finally:
                error.close()

    @staticmethod
    def _retry_after(headers) -> float:
        raw = headers.get("Retry-After") if headers is not None else None
        try:
            return min(max(float(raw), 0.0), MAX_RETRY_AFTER)
        except (TypeError, ValueError):
            return 1.0

    @staticmethod
    def _finish(method: str, path: str, status: int, raw: bytes) -> dict:
        try:
            parsed = json.loads(raw.decode("utf-8")) if raw else {}
        except (ValueError, UnicodeDecodeError):
            raise ApiError("bad_response", status,
                           f"{method} {path}: ответ не JSON ({raw[:80]!r}); проверьте, что SPIRIT_API указывает на REST мода",
                           ) from None
        if status >= 400:
            error = parsed.get("error") if isinstance(parsed, dict) else None
            if not isinstance(error, dict):
                raise ApiError("bad_response", status, f"{method} {path}: ошибка без объекта error в теле")
            raise ApiError(str(error.get("code", "unknown")), status, str(error.get("message", "")),
                           error.get("details") if isinstance(error.get("details"), dict) else {})
        if not isinstance(parsed, dict):
            raise ApiError("bad_response", status, f"{method} {path}: ожидался JSON-объект")
        return parsed
