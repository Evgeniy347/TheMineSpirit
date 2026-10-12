"""Фейковый REST мода (docs/API.md) для тестов стратега: http.server в потоке, порт 0.

Каждая ручка сценария меняет ответ; test_fake_rest.py проверяет это отдельно, чтобы сценарии не зеленели вхолостую.
Разбор запроса живёт в `exchange()`: его зовут и HTTP-обработчик, и in-process клиент (сценарии без сети).
"""

from __future__ import annotations

import hashlib
import http.client
import json
import threading
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from rest_client import RestClient

FIXTURES = Path(__file__).resolve().parent / "fixtures"


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def default_capabilities() -> dict:
    return load_json(FIXTURES / "capabilities.json")


def observation_hash(observation: dict) -> str:
    picture = {k: observation[k] for k in sorted(observation) if k not in ("tick", "hash", "bot")}
    picture["entities"] = [
        {k: v for k, v in entity.items() if k != "distance"} for entity in observation.get("entities", [])
    ]
    return hashlib.sha256(json.dumps(picture, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()


def error_body(code: str, message: str, details: dict | None = None) -> dict:
    return {"error": {"code": code, "message": message, "details": details or {}}}


GARBAGE_BODY = "<html>не json</html>".encode("utf-8")


def response_bytes(payload: dict | None) -> bytes:
    """Байты ответа: мусор отдаётся теми же байтами и по сокету, и внутри процесса, иначе клиенты разойдутся."""
    return GARBAGE_BODY if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")


@dataclass(frozen=True)
class Exchange:
    """Ответ фейка: статус, разобранное тело (None — ответ-мусор), байты ответа, заголовки и признак «соединение
    закрыто без ответа»."""

    status: int | None
    payload: dict | None
    raw: bytes
    headers: dict
    closed: bool


class FakeRest:
    TERMINAL = ("succeeded", "failed", "cancelled")

    def __init__(self, capabilities: dict | None = None):
        self.capabilities = capabilities if capabilities is not None else default_capabilities()
        self.bots: dict[str, dict] = {}
        self.observations: dict[str, dict] = {}
        self.tasks: dict[str, dict] = {}
        self.by_request_id: dict[str, tuple[str, str]] = {}
        self.requests: list[dict] = []
        self.fates: dict[str, dict] = {}
        self.default_fate: dict = {"outcome": "succeeded", "after_polls": 2}
        self.tasks_route = True
        self.plan_route = True
        self.plan_error: str | None = None
        self.rate_limited: list[float] = []
        self.server_errors: list[int] = []
        self.delay_next: list[float] = []
        self.busy_after_observe: set[str] = set()
        self.drop_connection_next = 0
        self.garbage_next = 0
        self.task_post_errors: list = []
        self.observe_errors: list = []
        self.bots_as_list = False
        self.vanish_on_task_post: set = set()
        self.bot_get_errors: list = []
        self.task_post_lose_response = 0
        self.clock = None
        self.tick = 1000
        self._lock = threading.RLock()
        self._counter = 0
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None

    def add_bot(self, bot_id: str, name: str | None = None, state: str = "idle", observation: dict | None = None,
                position: list | None = None) -> None:
        with self._lock:
            self.bots[bot_id] = {
                "id": bot_id, "name": name or bot_id, "state": state,
                "position": position or [10, 64, -3], "dimension": "minecraft:overworld", "current_task": None,
            }
            self.observations[bot_id] = observation if observation is not None else {
                "radius": 32, "pos": position or [10, 64, -3], "inventory": [], "blocks": [], "entities": [],
                "containers": [],
            }

    def set_observation(self, bot_id: str, observation: dict) -> None:
        with self._lock:
            self.observations[bot_id] = observation

    def set_state(self, bot_id: str, state: str) -> None:
        with self._lock:
            self.bots[bot_id]["state"] = state

    def set_fate(self, bot_id: str | None, outcome: str, after_polls: int = 2, error: str | None = None) -> None:
        fate = {"outcome": outcome, "after_polls": after_polls, "error": error}
        with self._lock:
            if bot_id is None:
                self.default_fate = fate
            else:
                self.fates[bot_id] = fate

    def requests_to(self, method: str, path_prefix: str) -> list[dict]:
        with self._lock:
            return [r for r in self.requests if r["method"] == method and r["path"].startswith(path_prefix)]

    def posted_tasks(self) -> list[dict]:
        return [r for r in self.requests_to("POST", "/v1/bots/") if r["path"].endswith("/tasks")]

    def start(self) -> "FakeRest":
        owner = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            wbufsize = 65536

            def log_message(self, *args):
                return

            def do_GET(self):
                owner._handle(self, "GET")

            def do_POST(self):
                owner._handle(self, "POST")

            def do_DELETE(self):
                owner._handle(self, "DELETE")

        self._server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self._server.daemon_threads = True
        self._thread = threading.Thread(target=self._server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
        self._thread.start()
        return self

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self._server.server_address[1]}"

    def stop(self) -> None:
        if self._server is not None:
            self._server.shutdown()
            self._server.server_close()
            self._server = None

    def __enter__(self) -> "FakeRest":
        return self.start()

    def __exit__(self, *exc) -> None:
        self.stop()

    def _handle(self, handler: BaseHTTPRequestHandler, method: str) -> None:
        length = int(handler.headers.get("Content-Length") or 0)
        raw = handler.rfile.read(length) if length else b""
        path = handler.path
        try:
            body = json.loads(raw.decode("utf-8")) if raw else None
        except ValueError:
            body = {"_unparsable": raw.decode("utf-8", "replace")}
        result = self.exchange(method, path, body)
        if result.closed:
            handler.connection.close()
            return
        handler.send_response(result.status)
        handler.send_header("Content-Type", "application/json; charset=utf-8")
        handler.send_header("Content-Length", str(len(result.raw)))
        for name, value in result.headers.items():
            handler.send_header(name, value)
        handler.end_headers()
        handler.wfile.write(result.raw)

    def exchange(self, method: str, path: str, body) -> Exchange:
        """Запрос без сети: журнал, ручки, обрыв и мусорный ответ. Зовут HTTP-обработчик и in-process клиент."""
        path = path.split("?", 1)[0]
        with self._lock:
            entry = {"method": method, "path": path, "body": body, "t": time.monotonic(), "status": None}
            if self.clock is not None:
                entry["fake_t"] = self.clock.now()
            self.requests.append(entry)
            delay = self.delay_next.pop(0) if self.delay_next else 0
            drop = self.drop_connection_next
            self.drop_connection_next = max(0, drop - 1)
        if delay:
            time.sleep(delay)
        if drop:
            entry["status"] = "dropped"
            return Exchange(None, None, b"", {}, True)
        with self._lock:
            status, payload, headers = self._route(method, path, body)
            entry["status"] = status
            is_task_post = method == "POST" and path.endswith("/tasks") and path.startswith("/v1/bots/")
            if is_task_post and self.task_post_lose_response and status == 202:
                self.task_post_lose_response -= 1
                entry["status"] = "response_lost"
                return Exchange(None, None, b"", {}, True)
            if self.garbage_next:
                self.garbage_next -= 1
                payload = None
        return Exchange(status, payload, response_bytes(payload), headers, False)

    def _route(self, method: str, path: str, body):
        if self.rate_limited:
            retry_after = self.rate_limited.pop(0)
            return 429, error_body("rate_limited", "Слишком много запросов: подождите"), {"Retry-After": str(retry_after)}
        if self.server_errors:
            item = self.server_errors.pop(0)
            status, code = item if isinstance(item, tuple) else (item, "internal")
            return status, error_body(code, f"Ошибка {code}: проверьте запрос", {"status": status}), {}
        parts = [p for p in path.split("/") if p]
        if parts[:1] != ["v1"]:
            return 404, error_body("not_found", f"Путь {path} не найден"), {}
        parts = parts[1:]
        if parts == ["capabilities"] and method == "GET":
            return 200, self.capabilities, {}
        if parts == ["bots"] and method == "GET":
            listing = [dict(b) for b in self.bots.values()]
            return 200, listing if self.bots_as_list else {"bots": listing}, {}
        if len(parts) == 2 and parts[0] == "bots" and method == "GET":
            return self._bot(parts[1])
        if len(parts) == 3 and parts[0] == "bots" and parts[2] == "observe" and method == "POST":
            return self._observe(parts[1])
        if len(parts) == 3 and parts[0] == "bots" and parts[2] == "tasks" and method == "POST":
            return self._post_task(parts[1], body)
        if parts == ["plan"] and method == "POST":
            return self._plan(body)
        if len(parts) == 2 and parts[0] == "tasks" and method == "GET":
            return self._get_task(parts[1])
        if len(parts) == 2 and parts[0] == "tasks" and method == "DELETE":
            return self._delete_task(parts[1])
        return 404, error_body("not_found", f"Путь {path} не найден"), {}

    def _bot(self, bot_id: str):
        if self.bot_get_errors:
            entry = self.bot_get_errors.pop(0)
            if entry is not None:
                status, code = entry
                return status, error_body(code, f"Ошибка {code} при чтении духа", {"status": status}), {}
        bot = self.bots.get(bot_id)
        if bot is None:
            return 404, error_body("not_found", f"Дух {bot_id} не найден", {"id": bot_id}), {}
        return 200, dict(bot), {}

    def _observe(self, bot_id: str):
        if self.observe_errors:
            status, code = self.observe_errors.pop(0)
            return status, error_body(code, f"Ошибка {code} при наблюдении", {"status": status}), {}
        if bot_id not in self.bots:
            return 404, error_body("not_found", f"Дух {bot_id} не найден", {"id": bot_id}), {}
        self.tick += 20
        observation = dict(self.observations[bot_id])
        observation["bot"] = bot_id
        observation["tick"] = self.tick
        observation.setdefault("hash", observation_hash(observation))
        if bot_id in self.busy_after_observe:
            self.bots[bot_id]["state"] = "busy"
        return 200, observation, {}

    def _plan(self, body):
        if not self.plan_route:
            return 404, error_body("not_found", "Путь /v1/plan не найден"), {}
        if self.plan_error:
            return 422, error_body("invalid_plan", self.plan_error), {}
        return 200, {"valid": True}, {}

    def _post_task(self, bot_id: str, body):
        if self.task_post_errors:
            status, code = self.task_post_errors.pop(0)
            return status, error_body(code, f"Ошибка {code} при постановке задачи", {"status": status}), {}
        if bot_id in self.vanish_on_task_post and bot_id in self.bots:
            del self.bots[bot_id]
            return 404, error_body("not_found", f"Дух {bot_id} не найден", {"id": bot_id}), {}
        if not self.tasks_route:
            return 404, error_body("not_found", "Путь задач не найден"), {}
        bot = self.bots.get(bot_id)
        if bot is None:
            return 404, error_body("not_found", f"Дух {bot_id} не найден", {"id": bot_id}), {}
        if not isinstance(body, dict) or "request_id" not in body:
            return 400, error_body("invalid_request", "Не задан request_id"), {}
        request_id = body["request_id"]
        known = self.by_request_id.get(request_id)
        fingerprint = json.dumps({k: body.get(k) for k in ("goal", "plan")}, sort_keys=True)
        if known is not None:
            task_id, earlier = known
            if earlier != fingerprint:
                return 409, error_body("conflict", f"request_id {request_id} уже использован с другим телом"), {}
            return 202, self._task_view(self.tasks[task_id], state="accepted"), {}
        if bot["state"] != "idle":
            return 409, error_body("conflict", f"Дух {bot_id} занят: {bot['state']}"), {}
        self._counter += 1
        task_id = f"t-{self._counter}"
        fate = self.fates.get(bot_id, self.default_fate)
        self.tasks[task_id] = {
            "task_id": task_id, "bot": bot_id, "goal": body.get("goal"), "request_id": request_id,
            "state": "accepted", "polls": 0, "fate": fate,
        }
        self.by_request_id[request_id] = (task_id, fingerprint)
        bot["state"] = "busy"
        bot["current_task"] = task_id
        return 202, self._task_view(self.tasks[task_id], state="accepted"), {}

    def _task_view(self, task: dict, state: str | None = None) -> dict:
        view = {
            "task_id": task["task_id"], "bot": task["bot"], "state": state or task["state"],
            "goal": task["goal"], "request_id": task["request_id"], "progress": None, "result": None, "error": None,
        }
        if view["state"] == "failed":
            view["error"] = task["fate"].get("error") or "задача не выполнена"
        return view

    def _get_task(self, task_id: str):
        task = self.tasks.get(task_id)
        if task is None:
            return 404, error_body("not_found", f"Задача {task_id} не найдена", {"id": task_id}), {}
        if task["state"] not in self.TERMINAL:
            task["polls"] += 1
            fate = task["fate"]
            if fate["outcome"] == "never":
                task["state"] = "running"
            elif task["polls"] >= fate["after_polls"]:
                task["state"] = fate["outcome"]
                self._release(task)
            else:
                task["state"] = "running"
        return 200, self._task_view(task), {}

    def _delete_task(self, task_id: str):
        task = self.tasks.get(task_id)
        if task is None:
            return 404, error_body("not_found", f"Задача {task_id} не найдена", {"id": task_id}), {}
        if task["state"] not in self.TERMINAL:
            task["state"] = "cancelled"
            self._release(task)
        return 200, self._task_view(task), {}

    def _release(self, task: dict) -> None:
        bot = self.bots.get(task["bot"])
        if bot is not None and bot.get("current_task") == task["task_id"]:
            bot["state"] = "idle"
            bot["current_task"] = None

    def forget_tasks(self) -> None:
        with self._lock:
            self.tasks.clear()
            for bot in self.bots.values():
                bot["state"] = "idle"
                bot["current_task"] = None


class InProcessClient(RestClient):
    """Тот же фейковый REST, но без сокета: запрос идёт прямо в FakeRest.exchange().

    Вся логика клиента (лимитер, повторы GET, 429, ApiError) остаётся боевой RestClient — меняется только
    транспорт. Тесты сценариев и свойств идут внутри процесса и заметно быстрее, а сами ручки фейка проверяет
    test_fake_rest.py по-настоящему, чтобы сценарии не зеленели вхолостую."""

    def __init__(self, fake: FakeRest, clock=None, rps: int = 5, timeout: float = 10.0, get_retries: int = 2,
                 rate_limit_retries: int = 3, backoff: float = 1.0):
        super().__init__("http://in-process", clock=clock, rps=rps, timeout=timeout, get_retries=get_retries,
                         rate_limit_retries=rate_limit_retries, backoff=backoff)
        self.fake = fake

    def _send(self, method: str, path: str, body):
        result = self.fake.exchange(method, path, body)
        if result.closed:
            raise http.client.RemoteDisconnected(f"{method} {path}: фейк закрыл соединение без ответа")
        return result.status, result.headers, result.raw
