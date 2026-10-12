"""Мозги стратега: «промпт -> текст». OpencodeBrain (подпроцесс в песочнице) и CommandBrain (любая команда), общий FallbackBrain.

Песочница opencode: инструменты read/edit/bash в режиме ask, остальные deny; процесс запускается БЕЗ флага автоодобрения,
поэтому любой ask отклоняется автоматически и даже prompt injection из имён сущностей может вернуть только текст.
deny для read/edit/bash убирает инструменты из запроса, и бесплатный тариф отвечает 403 («free tier can only be used from
within OpenCode»), поэтому для них - ask.
"""

from __future__ import annotations

import fnmatch
import json
import os
import re
import shlex
import shutil
import signal
import subprocess
import threading
import time
from dataclasses import dataclass
from pathlib import Path

from config import Config

PROVIDER_PATTERN = r'free usage exceeded|freeusagelimiterror|free.?tier|rate.?limit|usage.?limit|quota|too many requests|resource.?exhausted|(model|модель).{0,40}(unavailable|not available|недоступн)|[^0-9]429[^0-9]|upstream|idle.?timeout|[^0-9]50[234][^0-9]|server_error'
NETWORK_PATTERN = r'cannot connect|unable to connect|econnrefused|econnreset|enotfound|etimedout|fetch failed|socket hang up|network is unreachable'
PROBE_PROMPT = "ответь OK"
AGENT_NAME = "strategist"
POLL_SECONDS = 0.1
KILL_GRACE_SECONDS = 2.0
PROVIDER_EXIT_CODE = 75
ERROR_TEXT_LIMIT = 300
KEPT_FAILURES = 20
STRIPPED_ENV = ("OPENCODE_PERMISSION", "OPENCODE_CONFIG", "OPENCODE_CONFIG_CONTENT", "OPENCODE_CONFIG_DIR")
TOOL_PERMISSION = {"write": "edit", "patch": "edit", "multiedit": "edit"}
SAFE_ACTIONS = ("ask", "deny")
DEBUG_TIMEOUT_SECONDS = 30
SANDBOX_PERMISSION = {
    "*": "deny",
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


class BrainUnavailable(Exception):
    def __init__(self, kind: str, text: str):
        super().__init__(f"{kind}: {text}")
        self.kind = kind
        self.text = text


class BrainError(Exception):
    pass


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

    Без auth.json изоляция данных ломает вход в аккаунт бесплатных моделей, поэтому глобальный файл
    подвязывается симлинком: обновление токена opencode пишет через него в общий каталог.
    """
    data.mkdir(parents=True, exist_ok=True)
    auth = data / "opencode" / "auth.json"
    if auth.exists() or auth.is_symlink():
        return data
    raw = os.environ.get("XDG_DATA_HOME")
    source = (Path(raw) if raw else Path.home() / ".local" / "share") / "opencode" / "auth.json"
    if source.is_file():
        auth.parent.mkdir(parents=True, exist_ok=True)
        auth.symlink_to(source)
    return data


def agent_violations(agent: dict) -> list[str]:
    rules = agent.get("permission")
    tools = agent.get("tools")
    if not isinstance(rules, list) or not isinstance(tools, dict):
        return ["в выводе opencode debug agent нет списка permission или словаря tools"]
    problems = []
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


def _tail(text: str) -> str:
    flat = " ".join(text.split())
    return flat[-ERROR_TEXT_LIMIT:]


class OpencodeBrain:
    def __init__(self, config: Config, model: str, env: dict | None = None):
        self.config = config
        self.model = model
        self.extra_env = env or {}

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
        runs = self.config.home / "runs"
        runs.mkdir(parents=True, exist_ok=True)
        out_path, err_path = runs / "last.out", runs / "last.err"
        started = time.monotonic()
        with open(out_path, "wb") as out, open(err_path, "wb") as err:
            process = subprocess.Popen(args, cwd=self.config.sandbox_dir, stdin=subprocess.DEVNULL, stdout=out, stderr=err,
                                       start_new_session=True, env=self.subprocess_env())
            try:
                stdout, stderr, code = self._wait(process, out_path, err_path, started + timeout, timeout)
            except BaseException:
                _kill_group(process)
                self._keep_failure(runs, out_path, err_path)
                raise
        try:
            return self._interpret(stdout, stderr, code, time.monotonic() - started)
        except BrainError:
            self._keep_failure(runs, out_path, err_path)
            raise

    @staticmethod
    def _keep_failure(runs: Path, out_path: Path, err_path: Path) -> None:
        stamp = time.strftime("%Y%m%d-%H%M%S")
        for source, suffix in ((out_path, "out"), (err_path, "err")):
            if source.exists():
                shutil.copyfile(source, runs / f"failed-{stamp}-{os.getpid()}.{suffix}")
        old = sorted(runs.glob("failed-*"), key=lambda path: path.stat().st_mtime)
        for path in old[:-2 * KEPT_FAILURES]:
            path.unlink()

    def _wait(self, process, out_path: Path, err_path: Path, deadline: float, timeout: float):
        while True:
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
        text, attempted, executed = "", [], []
        for line in stdout.splitlines():
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if not isinstance(event, dict):
                continue
            part = event.get("part") if isinstance(event.get("part"), dict) else {}
            if event.get("type") == "text" and isinstance(part.get("text"), str) and part["text"].strip():
                text = part["text"]
            elif event.get("type") == "tool_use":
                state = part.get("state") if isinstance(part.get("state"), dict) else {}
                attempted.append(str(part.get("tool")))
                if state.get("status") != "error":
                    executed.append(str(part.get("tool")))
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

    def verify(self) -> None:
        return None

    def complete(self, system: str, user: str) -> BrainReply:
        return self._call(system, user, self.config.brain_timeout)

    def probe(self) -> BrainReply:
        return self._call("", PROBE_PROMPT, self.config.probe_timeout)

    def _call(self, system: str, user: str, timeout: float) -> BrainReply:
        payload = json.dumps({"system": system, "user": user, "model": self.model}, ensure_ascii=False)
        started = time.monotonic()
        process = subprocess.Popen(shlex.split(self.command), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, start_new_session=True, text=True, encoding="utf-8")
        try:
            stdout, stderr = process.communicate(payload, timeout=timeout)
        except subprocess.TimeoutExpired:
            _kill_group(process)
            raise BrainUnavailable("silence", f"команда мозга не ответила за {int(timeout)} с, процесс убит") from None
        except BaseException:
            _kill_group(process)
            raise
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

    def verify(self) -> None:
        self.primary.verify()
        if self.fallback is not None:
            self.fallback.verify()

    def complete(self, system: str, user: str) -> BrainReply:
        return self._with_fallback(lambda brain: brain.complete(system, user))

    def probe(self) -> BrainReply:
        return self._with_fallback(lambda brain: brain.probe())

    def _with_fallback(self, call) -> BrainReply:
        try:
            return call(self.primary)
        except BrainUnavailable as first:
            if self.fallback is None:
                raise
            try:
                return call(self.fallback)
            except BrainUnavailable as second:
                kind = "provider" if "provider" in (first.kind, second.kind) else "silence"
                raise BrainUnavailable(kind, f"{first.text}; запасная: {second.text}") from None
