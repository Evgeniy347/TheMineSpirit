"""Мозги стратега: «промпт -> текст». OpencodeBrain (подпроцесс в песочнице) и CommandBrain (любая команда), общий FallbackBrain.

Песочница opencode: общий шаблон ask, нужные инструменты read/edit/bash тоже ask, остальные deny; процесс запускается БЕЗ
флага автоодобрения, поэтому любой ask отклоняется автоматически и даже prompt injection из имён сущностей может вернуть
только текст. deny для read/edit/bash (в том числе через общий "*": deny) убирает инструменты из запроса: бесплатный тариф
отвечает 403, а модель не может даже попытаться вызвать ls, поэтому общий шаблон — ask.
"""

from __future__ import annotations

import fnmatch
import json
import os
import re
import shlex
import shutil
import signal
import sqlite3
import subprocess
import threading
import time
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path

from config import Config

# Шаблоны сбоев обязаны совпадать с scripts/opencode-task.sh — их держит равными тест в tests/test_brain.py:
# PROVIDER — сбой провайдера, NETWORK — сетевой сбой (обрыв соединения, недоступность конечной точки).
PROVIDER_PATTERN = r'free usage exceeded|freeusagelimiterror|free.?tier|rate.?limit|usage.?limit|quota|too many requests|resource.?exhausted|(model|модель).{0,40}(unavailable|not available|недоступн)|[^0-9]429[^0-9]|upstream|idle.?timeout|[^0-9]50[234][^0-9]|server_error|stream interrupted|readtimeout'
NETWORK_PATTERN = r'cannot connect|unable to connect|econnrefused|econnreset|enotfound|etimedout|fetch failed|socket hang up|network is unreachable|endpoint.{0,40}unavailable'
PROBE_PROMPT = "ответь OK"
SANDBOX_CHECK_PROMPT = "Выполни команду ls через bash и покажи вывод."
AGENT_NAME = "strategist"
POLL_SECONDS = 0.1
KILL_GRACE_SECONDS = 2.0
OPCODE_DB = "opencode.db"
ACCOUNT_TABLES = ("account", "account_state")
DB_TIMEOUT_SECONDS = 5.0
PROVIDER_EXIT_CODE = 75
ERROR_TEXT_LIMIT = 300
KEPT_FAILURES = 20
STRIPPED_ENV = ("OPENCODE_PERMISSION", "OPENCODE_CONFIG", "OPENCODE_CONFIG_CONTENT", "OPENCODE_CONFIG_DIR")
TOOL_PERMISSION = {"write": "edit", "patch": "edit", "multiedit": "edit"}
SAFE_ACTIONS = ("ask", "deny")
DEBUG_TIMEOUT_SECONDS = 30
SANDBOX_PERMISSION = {
    "*": "ask",
    "read": "ask", "edit": "ask", "bash": "ask",
    "webfetch": "deny", "websearch": "deny", "task": "deny", "skill": "deny", "external_directory": "deny",
    "question": "deny", "glob": "deny", "grep": "deny", "list": "deny", "lsp": "deny", "todowrite": "deny",
    "doom_loop": "deny",
}
_PATTERN = re.compile(f"{PROVIDER_PATTERN}|{NETWORK_PATTERN}", re.IGNORECASE)
_STDERR_LEVEL = re.compile(r"level=(ERROR|WARN)")


@dataclass(frozen=True)
class BrainReply:
    text: str
    model: str
    seconds: float
    tool_attempts: int = 0


@dataclass(frozen=True)
class SandboxProbe:
    model: str
    seconds: float
    attempted: tuple
    rejected: tuple
    executed: tuple


class BrainUnavailable(Exception):
    def __init__(self, kind: str, text: str):
        super().__init__(f"{kind}: {text}")
        self.kind = kind
        self.text = text


class BrainError(Exception):
    pass


class BrainStopped(BrainError):
    """Остановка агента прервала вызов: не сбой модели, поэтому ни backoff'а, ни записи brain_failure."""


class SandboxBreach(BrainError):
    pass


def _write_if_changed(path: Path, content: str) -> None:
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)


def ensure_sandbox(directory: Path, system: str | None = None) -> None:
    directory = Path(directory)
    config_text = json.dumps({"$schema": "https://opencode.ai/config.json", "permission": SANDBOX_PERMISSION},
                             ensure_ascii=False, indent=2) + "\n"
    _write_if_changed(directory / "opencode.json", config_text)
    agent_path = directory / ".opencode" / "agent" / f"{AGENT_NAME}.md"
    if system is not None or not agent_path.exists():
        body = system if system is not None else "Ты стратег духов. Отвечай одним JSON-объектом."
        _write_if_changed(agent_path, f"---\ndescription: Стратег духов The Mine Spirit\nmode: primary\n---\n{body}\n")
    verify_sandbox(directory)


def verify_sandbox(directory: Path) -> None:
    path = Path(directory) / "opencode.json"
    try:
        permission = json.loads(path.read_text(encoding="utf-8"))["permission"]
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise BrainError(f"Песочница opencode не проверена ({path}): {error}; пересоздайте каталог песочницы") from error
    for tool, expected in SANDBOX_PERMISSION.items():
        actual = permission.get(tool) if isinstance(permission, dict) else None
        if actual is None:
            raise BrainError(f"В песочнице {path} не задан инструмент {tool}: ожидается {expected}")
        if actual == "allow" or actual != expected:
            raise BrainError(f"В песочнице {path} инструмент {tool} = {actual!r}, ожидается {expected!r}: "
                             "разрешённый инструмент превращает prompt injection в выполнение команд")


def brain_env(extra: dict | None = None, data_home: Path | None = None) -> dict:
    env = {**os.environ, **(extra or {})}
    for name in STRIPPED_ENV:
        env.pop(name, None)
    if data_home is not None:
        env["XDG_DATA_HOME"] = str(data_home)
    return env


def prepare_data_home(data: Path) -> Path:
    """Каталог данных подпроцесса opencode: сессии с полным промптом копятся в нём, а не в общем ~/.local/share.

    Без auth.json и строк account/account_state из opencode.db изоляция данных ломает вход: бесплатные модели
    отвечают 403, а модели opencode-go — «An active OpenCode Go subscription is required». Поэтому глобальный
    auth.json подвязывается симлинком, а в свой opencode.db копируется только схема, миграции и строки учётки —
    без сессий, сообщений и прочих данных общего каталога.
    """
    data = Path(data)
    opencode_dir = data / "opencode"
    opencode_dir.mkdir(parents=True, exist_ok=True)
    source_dir = _source_data_dir()
    _link_auth(opencode_dir / "auth.json", source_dir / "auth.json")
    _seed_account_db(opencode_dir / OPCODE_DB, source_dir / OPCODE_DB)
    return data


def _source_data_dir() -> Path:
    raw = os.environ.get("XDG_DATA_HOME")
    return (Path(raw) if raw else Path.home() / ".local" / "share") / "opencode"


def _link_auth(auth: Path, source: Path) -> None:
    if auth.exists() or auth.is_symlink() or not source.is_file():
        return
    auth.symlink_to(source)


def _seed_account_db(local: Path, source: Path) -> None:
    """Перенести строки account/account_state в свой opencode.db, не копируя остальные таблицы."""
    if not source.is_file():
        return
    if local.exists() and _has_account_row(local):
        return
    try:
        with closing(_connect_ro(source)) as source_con:
            account, state = _read_account(source_con)
            if not account and not state:
                return
            if local.exists():
                _write_account_rows(local, source_con, account, state)
            else:
                _create_account_db(local, source_con, account, state)
    except sqlite3.Error as error:
        raise BrainError(f"Не удалось перенести учётную запись opencode в изолированный каталог ({local}): "
                         f"{error}; без неё модели opencode-go откажут. Проверьте {source}") from error


def _connect_ro(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{path}?mode=ro", uri=True, timeout=DB_TIMEOUT_SECONDS)


def _table_exists(con: sqlite3.Connection, name: str) -> bool:
    return con.execute("select 1 from sqlite_master where type='table' and name=?", (name,)).fetchone() is not None


def _has_account_row(local: Path) -> bool:
    try:
        with closing(sqlite3.connect(f"file:{local}?mode=ro", uri=True, timeout=DB_TIMEOUT_SECONDS)) as con:
            return _table_exists(con, "account") and con.execute("select 1 from account limit 1").fetchone() is not None
    except sqlite3.Error:
        return False


def _read_account(source: sqlite3.Connection):
    account = source.execute("select * from account").fetchall() if _table_exists(source, "account") else []
    state = source.execute("select * from account_state").fetchall() if _table_exists(source, "account_state") else []
    return account, state


def _create_account_db(local: Path, source: sqlite3.Connection, account, state) -> None:
    schema = [row[0] for row in source.execute(
        "select sql from sqlite_master where sql is not null and name not like 'sqlite_%' "
        "and type in ('table','index','trigger','view')").fetchall()]
    migrations = source.execute("select id, time_completed from migration").fetchall() \
        if _table_exists(source, "migration") else []
    local.parent.mkdir(parents=True, exist_ok=True)
    temporary = local.with_name(local.name + ".tmp")
    if temporary.exists():
        temporary.unlink()
    try:
        with closing(sqlite3.connect(str(temporary), timeout=DB_TIMEOUT_SECONDS, isolation_level=None)) as con:
            for statement in schema:
                con.execute(statement)
            if migrations:
                con.executemany("insert or ignore into migration(id, time_completed) values (?, ?)", migrations)
            _insert_account_rows(con, account, state)
        os.replace(temporary, local)
    except BaseException:
        if temporary.exists():
            temporary.unlink()
        raise


def _write_account_rows(local: Path, source: sqlite3.Connection, account, state) -> None:
    with closing(sqlite3.connect(str(local), timeout=DB_TIMEOUT_SECONDS, isolation_level=None)) as con:
        for name in ACCOUNT_TABLES:
            if not _table_exists(con, name):
                row = source.execute("select sql from sqlite_master where type='table' and name=?", (name,)).fetchone()
                if row and row[0]:
                    con.execute(row[0])
        _insert_account_rows(con, account, state)


def _insert_account_rows(con: sqlite3.Connection, account, state) -> None:
    for name, rows in (("account", account), ("account_state", state)):
        if rows:
            con.executemany(f"insert or replace into {name} values ({','.join('?' * len(rows[0]))})", rows)


CORE_TOOLS = ("read", "edit", "bash")


def agent_violations(agent: dict) -> list[str]:
    rules = agent.get("permission")
    tools = agent.get("tools")
    if not isinstance(rules, list) or not isinstance(tools, dict):
        return ["в выводе opencode debug agent нет списка permission или словаря tools"]
    problems = [f"{tool}: инструмент выключен, модель не сможет даже попытаться его вызвать; "
                "проверьте общий шаблон песочницы (он обязан быть ask, а не deny)"
                for tool in CORE_TOOLS if not tools.get(tool)]
    for tool, enabled in tools.items():
        if not enabled or tool == "invalid":
            continue
        permission = TOOL_PERMISSION.get(tool, tool)
        matching = [(index, rule) for index, rule in enumerate(rules)
                    if isinstance(rule, dict) and fnmatch.fnmatchcase(permission, str(rule.get("permission")))]
        general = [(index, rule) for index, rule in matching if rule.get("pattern") == "*"]
        if not general:
            problems.append(f"{tool}: нет правила на любой шаблон")
            continue
        last_index, last = general[-1]
        if last.get("action") not in SAFE_ACTIONS:
            problems.append(f"{tool}: итоговое правило {last.get('action')!r} (разрешено только ask или deny)")
        for index, rule in matching:
            if index > last_index and rule.get("action") == "allow":
                problems.append(f"{tool}: после общего правила разрешён шаблон {rule.get('pattern')!r}")
    return problems


def provider_failure_text(stdout: str, stderr: str) -> str | None:
    for line in stderr.splitlines():
        if _STDERR_LEVEL.search(line) and _PATTERN.search(line):
            return line.strip()[:ERROR_TEXT_LIMIT]
    for line in stdout.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if isinstance(event, dict) and (event.get("type") == "error" or "error" in event):
            text = json.dumps(event.get("error", event), ensure_ascii=False)
            if _PATTERN.search(text):
                return text[:ERROR_TEXT_LIMIT]
    return None


def _kill_group(process: subprocess.Popen) -> None:
    for signum in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(process.pid, signum)
        except ProcessLookupError:
            break
        try:
            process.wait(timeout=KILL_GRACE_SECONDS)
            break
        except subprocess.TimeoutExpired:
            continue
    try:
        process.wait(timeout=KILL_GRACE_SECONDS)
    except subprocess.TimeoutExpired:
        pass
    for stream in (process.stdin, process.stdout, process.stderr):
        if stream is not None:
            stream.close()


def _kill_group_now(process: subprocess.Popen | None) -> None:
    # Вызывается из обработчика SIGTERM/SIGINT в главном потоке: wait() здесь мог бы ждать сам себя.
    if process is None:
        return
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


def _tail(text: str) -> str:
    flat = " ".join(text.split())
    return flat[-ERROR_TEXT_LIMIT:]


def keep_failure_output(runs: Path, out_path: Path, err_path: Path, meta: str | None = None) -> None:
    stamp = time.strftime("%Y%m%d-%H%M%S")
    base = runs / f"failed-{stamp}-{os.getpid()}"
    for source, suffix in ((out_path, "out"), (err_path, "err")):
        if source.exists():
            shutil.copyfile(source, base.with_name(base.name + f".{suffix}"))
    if meta is not None:
        base.with_name(base.name + ".meta").write_text(meta, encoding="utf-8")
    old = sorted(runs.glob("failed-*"), key=lambda path: path.stat().st_mtime)
    for path in old[:-2 * KEPT_FAILURES]:
        path.unlink()


def _tool_events(stdout: str):
    texts, attempted, rejected, executed = [], [], [], []
    for line in stdout.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if not isinstance(event, dict):
            continue
        part = event.get("part") if isinstance(event.get("part"), dict) else {}
        if event.get("type") == "text" and isinstance(part.get("text"), str) and part["text"].strip():
            texts.append(part["text"])
        elif event.get("type") == "tool_use":
            state = part.get("state") if isinstance(part.get("state"), dict) else {}
            tool = str(part.get("tool"))
            attempted.append(tool)
            (rejected if state.get("status") == "error" else executed).append(tool)
    return texts, attempted, rejected, executed


class OpencodeBrain:
    def __init__(self, config: Config, model: str, env: dict | None = None):
        self.config = config
        self.model = model
        self.extra_env = env or {}
        self._stop = False
        self._process: subprocess.Popen | None = None

    def request_stop(self) -> None:
        self._stop = True
        _kill_group_now(self._process)

    def subprocess_env(self) -> dict:
        return brain_env(self.extra_env, data_home=prepare_data_home(self.config.opencode_data))

    def build_args(self, user: str, agent: bool = True) -> list[str]:
        args = [self.config.opencode_bin, "run", "-m", self.model]
        if agent:
            args += ["--agent", AGENT_NAME]
        args += ["--format", "json", "--print-logs", "--log-level", "INFO", "--dir", str(self.config.sandbox_dir), user]
        return args

    def verify(self) -> None:
        ensure_sandbox(self.config.sandbox_dir)
        args = [self.config.opencode_bin, "debug", "agent", AGENT_NAME]
        try:
            done = subprocess.run(args, cwd=self.config.sandbox_dir, capture_output=True, text=True, encoding="utf-8",
                                  timeout=DEBUG_TIMEOUT_SECONDS, env=self.subprocess_env(), stdin=subprocess.DEVNULL)
            start = done.stdout.index("{")
            agent = json.JSONDecoder().raw_decode(done.stdout[start:])[0]
        except (OSError, subprocess.TimeoutExpired, ValueError) as error:
            raise BrainError(f"Не удалось получить итоговые права агента командой '{' '.join(args)}': {error}; "
                             "без проверки запуск отменён") from error
        problems = agent_violations(agent)
        if problems:
            raise BrainError("Итоговые права агента strategist в opencode небезопасны: " + "; ".join(problems)
                             + ". Проверьте глобальный конфиг opencode и каталог песочницы; запуск отменён")

    def complete(self, system: str, user: str) -> BrainReply:
        ensure_sandbox(self.config.sandbox_dir, system)
        return self._run(self.build_args(user), self.config.brain_timeout)

    def probe(self) -> BrainReply:
        ensure_sandbox(self.config.sandbox_dir)
        return self._run(self.build_args(PROBE_PROMPT, agent=False), self.config.probe_timeout)

    def _run(self, args: list[str], timeout: float) -> BrainReply:
        stdout, stderr, code, seconds = self._execute(args, timeout)
        try:
            return self._interpret(stdout, stderr, code, seconds)
        except BrainError:
            runs = self.config.home / "runs"
            keep_failure_output(runs, runs / "last.out", runs / "last.err")
            raise

    def _execute(self, args: list[str], timeout: float) -> tuple[str, str, int, float]:
        if self._stop:
            raise BrainStopped(f"{self.model}: вызов не начат, стратег останавливается")
        runs = self.config.home / "runs"
        runs.mkdir(parents=True, exist_ok=True)
        out_path, err_path = runs / "last.out", runs / "last.err"
        started = time.monotonic()
        with open(out_path, "wb") as out, open(err_path, "wb") as err:
            process = subprocess.Popen(args, cwd=self.config.sandbox_dir, stdin=subprocess.DEVNULL, stdout=out, stderr=err,
                                       start_new_session=True, env=self.subprocess_env())
            self._process = process
            try:
                stdout, stderr, code = self._wait(process, out_path, err_path, started + timeout, timeout)
            except BrainStopped:
                _kill_group(process)
                raise
            except BaseException:
                _kill_group(process)
                keep_failure_output(runs, out_path, err_path)
                raise
            finally:
                self._process = None
        if self._stop:
            raise BrainStopped(f"{self.model}: стратег останавливается, ответ не разбирается")
        return stdout, stderr, code, time.monotonic() - started

    def sandbox_check(self) -> SandboxProbe:
        ensure_sandbox(self.config.sandbox_dir)
        stdout, _, _, seconds = self._execute(self.build_args(SANDBOX_CHECK_PROMPT), self.config.probe_timeout)
        _, attempted, rejected, executed = _tool_events(stdout)
        return SandboxProbe(self.model, round(seconds, 2), tuple(attempted), tuple(rejected), tuple(executed))

    def opencode_version(self) -> str:
        try:
            done = subprocess.run([self.config.opencode_bin, "--version"],
                                  capture_output=True, text=True, encoding="utf-8", timeout=DEBUG_TIMEOUT_SECONDS,
                                  env=self.subprocess_env(), stdin=subprocess.DEVNULL)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise BrainError(f"Не удалось узнать версию opencode ({self.config.opencode_bin}): {error}") from error
        text = (done.stdout or done.stderr).strip().splitlines()
        return text[0] if text else "неизвестна"

    def keep_reply_failure(self, reply: BrainReply) -> None:
        runs = self.config.home / "runs"
        runs.mkdir(parents=True, exist_ok=True)
        meta = json.dumps({"model": reply.model, "seconds": reply.seconds, "tool_attempts": reply.tool_attempts},
                          ensure_ascii=False)
        keep_failure_output(runs, runs / "last.out", runs / "last.err", meta)

    def _wait(self, process, out_path: Path, err_path: Path, deadline: float, timeout: float):
        while True:
            if self._stop:
                _kill_group(process)
                raise BrainStopped(f"{self.model}: вызов прерван, группа процессов убита")
            code = process.poll()
            stdout = out_path.read_text(encoding="utf-8", errors="replace")
            stderr = err_path.read_text(encoding="utf-8", errors="replace")
            failure = provider_failure_text(stdout, stderr)
            if failure:
                _kill_group(process)
                raise BrainUnavailable("provider", f"{self.model}: {failure}")
            if code is not None:
                _kill_group(process)
                return stdout, stderr, code
            if time.monotonic() >= deadline:
                _kill_group(process)
                raise BrainUnavailable("silence", f"{self.model}: нет ответа за {int(timeout)} с, процесс убит")
            time.sleep(POLL_SECONDS)

    def _interpret(self, stdout: str, stderr: str, code: int, seconds: float) -> BrainReply:
        texts, attempted, _, executed = _tool_events(stdout)
        text = "".join(texts)
        if executed:
            raise SandboxBreach(f"Нарушение песочницы: инструмент {', '.join(executed)} выполнился без отказа; "
                             "проверьте sandbox/opencode.json и запуск без флага автоодобрения")
        if code != 0 and not text:
            raise BrainError(f"opencode завершился кодом {code} без ответа: {_tail(stderr) or _tail(stdout) or 'вывода нет'}")
        if not text:
            if attempted:
                raise BrainError(f"Модель попыталась вызвать инструмент {', '.join(attempted)} (отклонено песочницей) "
                                 "и не дала текстового ответа")
            raise BrainError(f"В выводе opencode нет текстового ответа (код {code}): {_tail(stdout) or _tail(stderr) or 'вывода нет'}")
        return BrainReply(text, self.model, round(seconds, 2), len(attempted))


class CommandBrain:
    def __init__(self, config: Config, command: str, model: str = "command"):
        self.config = config
        self.command = command
        self.model = model
        self._stop = False
        self._process: subprocess.Popen | None = None

    def request_stop(self) -> None:
        self._stop = True
        _kill_group_now(self._process)

    def verify(self) -> None:
        return None

    def complete(self, system: str, user: str) -> BrainReply:
        return self._call(system, user, self.config.brain_timeout)

    def probe(self) -> BrainReply:
        return self._call("", PROBE_PROMPT, self.config.probe_timeout)

    def keep_reply_failure(self, reply: BrainReply) -> None:
        return None

    def _call(self, system: str, user: str, timeout: float) -> BrainReply:
        if self._stop:
            raise BrainStopped(f"{self.model}: вызов не начат, стратег останавливается")
        payload = json.dumps({"system": system, "user": user, "model": self.model}, ensure_ascii=False)
        started = time.monotonic()
        process = subprocess.Popen(shlex.split(self.command), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, start_new_session=True, text=True, encoding="utf-8")
        self._process = process
        try:
            if self._stop:
                _kill_group_now(process)
                raise BrainStopped(f"{self.model}: вызов прерван, группа процессов убита")
            stdout, stderr = process.communicate(payload, timeout=timeout)
        except subprocess.TimeoutExpired:
            _kill_group(process)
            if not self._stop:
                raise BrainUnavailable("silence", f"команда мозга не ответила за {int(timeout)} с, процесс убит") from None
        except BaseException:
            _kill_group(process)
            if not self._stop:
                raise
        finally:
            self._process = None
        if self._stop:
            raise BrainStopped(f"{self.model}: вызов прерван, группа процессов убита")
        if process.returncode == PROVIDER_EXIT_CODE:
            raise BrainUnavailable("provider", _tail(stderr) or "команда мозга сообщила о недоступности модели (код 75)")
        if process.returncode != 0:
            raise BrainError(f"Команда мозга завершилась кодом {process.returncode}: {_tail(stderr) or 'stderr пуст'}")
        if not stdout.strip():
            raise BrainError("Команда мозга ничего не вывела в stdout: ожидался текст ответа")
        return BrainReply(stdout, self.model, round(time.monotonic() - started, 2))


class FallbackBrain:
    def __init__(self, primary, fallback=None):
        self.primary = primary
        self.fallback = fallback
        self.model = primary.model
        self._answered = None

    def verify(self) -> None:
        self.primary.verify()
        if self.fallback is not None:
            self.fallback.verify()

    def request_stop(self) -> None:
        self.primary.request_stop()
        if self.fallback is not None:
            self.fallback.request_stop()

    def keep_reply_failure(self, reply: BrainReply) -> None:
        brain = self._answered if self._answered is not None else self.primary
        brain.keep_reply_failure(reply)

    def complete(self, system: str, user: str) -> BrainReply:
        return self._with_fallback(lambda brain: brain.complete(system, user))

    def probe(self) -> BrainReply:
        return self._with_fallback(lambda brain: brain.probe())

    def sandbox_check(self):
        return self.primary.sandbox_check()

    def opencode_version(self) -> str:
        return self.primary.opencode_version()

    def _with_fallback(self, call) -> BrainReply:
        try:
            reply = call(self.primary)
            self._answered = self.primary
            return reply
        except BrainUnavailable as first:
            if self.fallback is None:
                raise
            try:
                reply = call(self.fallback)
                self._answered = self.fallback
                return reply
            except BrainUnavailable as second:
                kind = "provider" if "provider" in (first.kind, second.kind) else "silence"
                raise BrainUnavailable(kind, f"{first.text}; запасная: {second.text}") from None
