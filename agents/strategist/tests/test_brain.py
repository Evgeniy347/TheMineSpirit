import dataclasses
import json
import os
import re
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import brain as brain_module
import config
from brain import (BrainError, BrainUnavailable, CommandBrain, FallbackBrain, OpencodeBrain, agent_violations, ensure_sandbox,
                   verify_sandbox)

APP = Path(__file__).resolve().parent.parent
FAKE = APP / "tests" / "fake_opencode.sh"
SKIP_REPLY = '{"reason":"нечего делать","skip":true}'
FORBIDDEN_FLAGS = ("--auto", "--yolo", "--dangerously-skip-permissions")
TOOLS_FROM_CHECKLIST = ("read", "edit", "bash", "webfetch", "websearch", "task", "skill", "external_directory", "question")


def make_config(tmp: Path, **overrides) -> config.Config:
    base = config.from_env({"STRATEGIST_HOME": str(tmp / "home")})
    return dataclasses.replace(base, opencode_bin=str(FAKE), brain_timeout=3.0, probe_timeout=3.0, **overrides)


class BrainTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.state = self.dir / "fake.state"
        self.cfg = make_config(self.dir)
        self.env = {"FAKE_STATE": str(self.state)}

    def brain(self, model="opencode/test-free", **env):
        return OpencodeBrain(self.cfg, model, env={**self.env, **{k: str(v) for k, v in env.items()}})

    def runs(self):
        return int(self.state.read_text()) if self.state.exists() else 0

    def args_log(self):
        return (self.state.with_name("fake.state.args")).read_text(encoding="utf-8")


class OpencodeBrainTests(BrainTestCase):
    def test_complete_NormalReply_ReturnsTextModelAndSeconds(self):
        reply = self.brain().complete("системная часть", "пользовательская часть")
        self.assertEqual(SKIP_REPLY, reply.text)
        self.assertEqual("opencode/test-free", reply.model)
        self.assertGreaterEqual(reply.seconds, 0)
        self.assertEqual(0, reply.tool_attempts)
        self.assertEqual(1, self.runs())

    def test_complete_PassesSandboxDirAgentModelAndUserTextInArguments(self):
        self.brain().complete("СИСТЕМА-ТЕКСТ", "ПОЛЬЗОВАТЕЛЬ-ТЕКСТ")
        log = self.args_log()
        self.assertIn("-m opencode/test-free", log)
        self.assertIn("--format json", log)
        self.assertIn("--agent strategist", log)
        self.assertIn(f"--dir {self.cfg.sandbox_dir}", log)
        self.assertIn("ПОЛЬЗОВАТЕЛЬ-ТЕКСТ", log)
        self.assertIn(f"CWD: {self.cfg.sandbox_dir}", log, "процесс запускается в песочнице")
        self.assertIn("СИСТЕМА-ТЕКСТ", log, "системная часть лежит в файле агента песочницы")

    def test_complete_NeverPassesAutoApproveFlag(self):
        self.brain().complete("с", "п")
        built = " " + " ".join(self.brain().build_args("п")) + " "
        for flag in FORBIDDEN_FLAGS:
            self.assertNotIn(f" {flag}", self.args_log(), f"{flag} превращает prompt injection в выполнение команд")
            self.assertNotIn(f" {flag} ", built, f"{flag} в build_args отключает отказ на ask")
            self.assertNotIn(f" {flag}", " " + " ".join(self.brain().build_args("п", agent=False)))

    def test_complete_LastTextEventWins(self):
        reply = self.brain(FAKE_REPLY='{"reason":"второй","skip":true}').complete("с", "п")
        self.assertIn("второй", reply.text)

    def test_complete_ProviderErrorInJson_RaisesProviderQuicklyAndKillsProcess(self):
        for text in ("Free usage exceeded", "Rate limit exceeded", "HTTP 429 Too Many Requests", "upstream error 502 bad gateway"):
            with self.subTest(text=text):
                started = time.monotonic()
                with self.assertRaises(BrainUnavailable) as ctx:
                    self.brain(FAKE_ERROR="json", FAKE_ERROR_TEXT=text).complete("с", "п")
                self.assertEqual("provider", ctx.exception.kind)
                self.assertIn(text.split()[0], ctx.exception.text)
                self.assertLess(time.monotonic() - started, 2.5, "сбой провайдера опознаётся сразу, а не по тайм-ауту")

    def test_complete_NetworkErrorEvent_RaisesProviderQuickly(self):
        texts = ("Cannot connect to API: Unable to connect. Is the computer able to access the url?",
                 "fetch failed: ECONNRESET", "getaddrinfo ENOTFOUND opencode.ai", "connect ETIMEDOUT 104.18.0.1:443")
        for text in texts:
            with self.subTest(text=text):
                started = time.monotonic()
                with self.assertRaises(BrainUnavailable, msg="обрыв сети до провайдера — недоступность, а не ожидание до конца ретраев opencode") as ctx:
                    self.brain(FAKE_ERROR="json", FAKE_ERROR_TEXT=text).complete("с", "п")
                self.assertEqual("provider", ctx.exception.kind)
                self.assertLess(time.monotonic() - started, 2.5)

    def test_complete_ProviderErrorInStderr_RaisesProvider(self):
        with self.assertRaises(BrainUnavailable) as ctx:
            self.brain(FAKE_ERROR="stderr", FAKE_ERROR_TEXT="Rate limit exceeded. Please try again later.").complete("с", "п")
        self.assertEqual("provider", ctx.exception.kind)

    def test_failure_KeepsRawOutputForDiagnosis(self):
        with self.assertRaises(BrainUnavailable):
            self.brain(FAKE_ERROR="stderr", FAKE_ERROR_TEXT="Rate limit exceeded. Please try again later.").complete("с", "п")
        kept = list((self.cfg.home / "runs").glob("failed-*.err"))
        self.assertEqual(1, len(kept), "сырой вывод упавшего вызова сохраняется для разбора")
        self.assertIn("Rate limit exceeded", kept[0].read_text(encoding="utf-8"))
        self.brain().complete("с", "п")
        self.assertEqual(1, len(list((self.cfg.home / "runs").glob("failed-*.err"))), "успешный вызов ничего не сохраняет")

    def test_complete_Silence_RaisesSilenceAndKillsWholeProcessGroup(self):
        cfg = dataclasses.replace(self.cfg, brain_timeout=1.0)
        brain = OpencodeBrain(cfg, "opencode/test-free", env={**self.env, "FAKE_SILENT": "1"})
        started = time.monotonic()
        with self.assertRaises(BrainUnavailable) as ctx:
            brain.complete("с", "п")
        self.assertEqual("silence", ctx.exception.kind)
        self.assertLess(time.monotonic() - started, 4, "тайм-аут срабатывает по STRATEGIST_BRAIN_TIMEOUT")
        time.sleep(0.3)
        for name in ("fake.state.pid", "fake.state.childpid"):
            pid = int((self.dir / name).read_text())
            self.assertFalse(self.pid_alive(pid), f"процесс из {name} должен быть убит вместе с группой")

    @staticmethod
    def pid_alive(pid):
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return False
        stat = Path(f"/proc/{pid}/stat")
        return not (stat.exists() and stat.read_text().split()[2] == "Z")

    def test_complete_GarbageOutput_RaisesBrainError(self):
        with self.assertRaises(BrainError) as ctx:
            self.brain(FAKE_GARBAGE="1").complete("с", "п")
        self.assertIn("текстового ответа", str(ctx.exception))

    def test_complete_NonZeroExitWithoutOutput_RaisesBrainErrorWithCode(self):
        with self.assertRaises(BrainError) as ctx:
            self.brain(FAKE_EXIT="3").complete("с", "п")
        self.assertIn("3", str(ctx.exception))

    def test_complete_RejectedToolCall_CountedButReplyStillAccepted(self):
        reply = self.brain(FAKE_TOOL="reject", FAKE_REPLY=SKIP_REPLY).complete("с", "п")
        self.assertEqual(1, reply.tool_attempts, "попытка вызвать инструмент (её отклонили) видна в журнале")
        self.assertEqual(SKIP_REPLY, reply.text)

    def test_complete_RejectedToolCallWithoutText_RaisesBrainErrorNamingTool(self):
        with self.assertRaises(BrainError) as ctx:
            self.brain(FAKE_TOOL="reject").complete("с", "п")
        self.assertIn("bash", str(ctx.exception), "в ошибке названа попытка вызвать инструмент")

    def test_complete_ExecutedToolCall_RaisesBrainErrorAsSandboxBreach(self):
        with self.assertRaises(BrainError) as ctx:
            self.brain(FAKE_TOOL="executed").complete("с", "п")
        self.assertIn("песочниц", str(ctx.exception), "выполненный инструмент — нарушение песочницы, а не обычный ответ")

    def test_probe_Ok_And_ProviderFailure(self):
        self.assertEqual("OK", self.brain().probe().text.strip())
        with self.assertRaises(BrainUnavailable) as ctx:
            self.brain(FAKE_PROBE_FAIL="1").probe()
        self.assertEqual("provider", ctx.exception.kind)
        self.assertIn("ответь OK", self.args_log())

    def test_sandbox_CreatedByCodeAndSystemTextUpdatedEachCall(self):
        self.brain().complete("ПЕРВАЯ-СИСТЕМА", "п")
        agent = self.cfg.sandbox_dir / ".opencode" / "agent" / "strategist.md"
        self.assertIn("ПЕРВАЯ-СИСТЕМА", agent.read_text(encoding="utf-8"))
        self.brain().complete("ВТОРАЯ-СИСТЕМА", "п")
        self.assertIn("ВТОРАЯ-СИСТЕМА", agent.read_text(encoding="utf-8"))
        self.assertNotIn("ПЕРВАЯ-СИСТЕМА", agent.read_text(encoding="utf-8"))
        self.assertTrue((self.cfg.sandbox_dir / "opencode.json").is_file())


class SandboxGuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def test_sandbox_EveryToolFromChecklistIsNeverAllowed(self):
        ensure_sandbox(self.dir, "система")
        permission = json.loads((self.dir / "opencode.json").read_text(encoding="utf-8"))["permission"]
        for tool in TOOLS_FROM_CHECKLIST:
            self.assertIn(tool, permission, f"инструмент {tool} должен быть назван в песочнице явно")
            self.assertIn(permission[tool], ("deny", "ask"), f"{tool}: allow в песочнице превращает инъекцию в выполнение. "
                          "Запретить инструмент: deny; ask без флага автоодобрения отклоняется автоматически")
        for tool in ("webfetch", "websearch", "task", "skill", "external_directory", "question"):
            self.assertEqual("deny", permission[tool], f"{tool} можно запретить полностью")

    def test_sandbox_CoreToolsAreAskNotDeny_BecauseFreeTierRejectsRequestsWithoutThem(self):
        permission = json.loads(self._written()).get("permission")
        for tool in ("read", "edit", "bash"):
            self.assertEqual("ask", permission[tool],
                             f"{tool}: deny убирает инструмент из запроса, и бесплатный тариф отвечает 403 "
                             "«free tier can only be used from within OpenCode» (спайк 2026-10-11); нужен ask")

    def _written(self):
        ensure_sandbox(self.dir, "система")
        return (self.dir / "opencode.json").read_text(encoding="utf-8")

    def test_verify_AllowedTool_Fails(self):
        ensure_sandbox(self.dir, "система")
        path = self.dir / "opencode.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["permission"]["bash"] = "allow"
        path.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(BrainError) as ctx:
            verify_sandbox(self.dir)
        self.assertIn("bash", str(ctx.exception))

    def test_verify_MissingTool_Fails(self):
        ensure_sandbox(self.dir, "система")
        path = self.dir / "opencode.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        del data["permission"]["webfetch"]
        path.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(BrainError) as ctx:
            verify_sandbox(self.dir)
        self.assertIn("webfetch", str(ctx.exception))

    def test_ensure_RewritesTamperedConfig(self):
        ensure_sandbox(self.dir, "система")
        (self.dir / "opencode.json").write_text('{"permission": {"bash": "allow"}}', encoding="utf-8")
        ensure_sandbox(self.dir, "система")
        verify_sandbox(self.dir)


REAL_AGENT = json.loads((APP / "tests" / "fixtures" / "opencode-agent-debug.json").read_text(encoding="utf-8"))


class AgentViolationsTests(unittest.TestCase):
    def agent(self, *extra_rules):
        agent = json.loads(json.dumps(REAL_AGENT))
        agent["permission"].extend(extra_rules)
        return agent

    def test_realOpencodeOutput_NoViolations(self):
        self.assertEqual([], agent_violations(REAL_AGENT), "записанный вывод opencode debug agent для песочницы должен быть безопасным")
        enabled = [tool for tool, on in REAL_AGENT["tools"].items() if on and tool != "invalid"]
        self.assertIn("write", enabled, "образец содержит write: он проверяется по правилу edit")

    def test_finalAllowOnEnabledTool_Violation(self):
        for tool in ("bash", "read", "edit"):
            with self.subTest(tool=tool):
                problems = agent_violations(self.agent({"permission": tool, "pattern": "*", "action": "allow"}))
                self.assertTrue(any(problem.startswith(tool) or tool == "edit" and problem.startswith("write") for problem in problems),
                                f"итоговый allow для {tool} обязан быть найден: {problems}")

    def test_writeToolIsCheckedByEditRule(self):
        problems = agent_violations(self.agent({"permission": "edit", "pattern": "*", "action": "allow"}))
        self.assertTrue(any(p.startswith("write") for p in problems), f"write — это edit: {problems}")

    def test_narrowAllowAfterGeneralRule_Violation(self):
        problems = agent_violations(self.agent({"permission": "read", "pattern": "*.txt", "action": "allow"}))
        self.assertTrue(any("*.txt" in p for p in problems), f"узкий allow после общего ask обходит отказ: {problems}")

    def test_allowOnDisabledToolOrNonTool_Ignored(self):
        agent = self.agent({"permission": "glob", "pattern": "*", "action": "allow"},
                           {"permission": "external_directory", "pattern": "/x/*", "action": "allow"})
        self.assertEqual([], agent_violations(agent), "glob выключен, external_directory — не инструмент")

    def test_toolWithoutAnyRule_Violation(self):
        agent = {"permission": [{"permission": "bash", "pattern": "*", "action": "ask"}], "tools": {"read": True, "bash": True}}
        self.assertTrue(any(p.startswith("read") for p in agent_violations(agent)))

    def test_wildcardAllowThenSpecificAsk_Fine(self):
        agent = {"permission": [{"permission": "*", "pattern": "*", "action": "allow"},
                                {"permission": "read", "pattern": "*", "action": "ask"}], "tools": {"read": True}}
        self.assertEqual([], agent_violations(agent))

    def test_garbage_Violation(self):
        for garbage in ({}, {"permission": "x", "tools": {}}, {"permission": [], "tools": []}):
            self.assertTrue(agent_violations(garbage))


class SandboxVerificationTests(BrainTestCase):
    def test_sandboxConfig_StarDenyIsFirstKey(self):
        self.brain().complete("с", "п")
        permission = json.loads((self.cfg.sandbox_dir / "opencode.json").read_text(encoding="utf-8"))["permission"]
        self.assertEqual("*", next(iter(permission)), "\"*\": deny первым ключом: новые инструменты opencode по умолчанию запрещены")
        self.assertEqual("deny", permission["*"])

    def test_verify_SafeAgent_Passes(self):
        self.brain().verify()

    def test_verify_UnsafeAgents_RefuseToStart(self):
        for mode, needle in (("allow", "bash"), ("late_allow", "*.txt")):
            with self.subTest(mode=mode):
                with self.assertRaises(BrainError) as ctx:
                    self.brain(FAKE_AGENT=mode).verify()
                self.assertIn(needle, str(ctx.exception))

    def test_verify_GarbageOrMissingBinary_RefusesToStart(self):
        with self.assertRaises(BrainError):
            self.brain(FAKE_AGENT="garbage").verify()
        broken = OpencodeBrain(dataclasses.replace(self.cfg, opencode_bin=str(self.dir / "нет-такого")), "m", env=self.env)
        with self.assertRaises(BrainError) as ctx:
            broken.verify()
        self.assertIn("запуск отменён", str(ctx.exception))

    def test_fallbackVerify_ChecksBothBrains(self):
        FallbackBrain(self.brain("a"), self.brain("b")).verify()
        with self.assertRaises(BrainError):
            FallbackBrain(self.brain("a"), self.brain("b", FAKE_AGENT="allow")).verify()

    def test_environment_PermissionOverridesAreStrippedForRunAndDebug(self):
        hostile = {name: "{\"bash\":\"allow\"}" for name in ("OPENCODE_PERMISSION", "OPENCODE_CONFIG", "OPENCODE_CONFIG_CONTENT",
                                                              "OPENCODE_CONFIG_DIR")}
        with mock.patch.dict(os.environ, hostile):
            self.brain().verify()
            self.brain().complete("с", "п")
        log = self.args_log()
        self.assertEqual(2, log.count("PERMISSION=unset CONFIG=unset CONTENT=unset DIR=unset"),
                         f"переменные OPENCODE_* должны быть убраны из окружения debug и run: {log}")


class CommandBrainTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.cfg = make_config(self.dir)

    def script(self, body):
        path = self.dir / "brain.sh"
        path.write_text("#!/usr/bin/env bash\n" + body, encoding="utf-8")
        path.chmod(0o755)
        return str(path)

    def test_complete_PassesJsonOnStdinAndReturnsStdout(self):
        out = self.dir / "stdin.json"
        brain = CommandBrain(self.cfg, self.script(f"cat > {out}\nprintf '%s' '{SKIP_REPLY}'\n"), "sonnet-5.5")
        reply = brain.complete("СИСТЕМА", "ПОЛЬЗОВАТЕЛЬ")
        self.assertEqual(SKIP_REPLY, reply.text)
        self.assertEqual("sonnet-5.5", reply.model)
        self.assertEqual({"system": "СИСТЕМА", "user": "ПОЛЬЗОВАТЕЛЬ", "model": "sonnet-5.5"},
                         json.loads(out.read_text(encoding="utf-8")))

    def test_complete_NonZeroExit_RaisesBrainErrorWithStderr(self):
        with self.assertRaises(BrainError) as ctx:
            CommandBrain(self.cfg, self.script("echo 'сломалось' >&2\nexit 2\n"), "m").complete("с", "п")
        self.assertIn("сломалось", str(ctx.exception))

    def test_complete_Exit75_MeansProviderUnavailable(self):
        with self.assertRaises(BrainUnavailable) as ctx:
            CommandBrain(self.cfg, self.script("echo 'лимит' >&2\nexit 75\n"), "m").complete("с", "п")
        self.assertEqual("provider", ctx.exception.kind)

    def test_complete_Timeout_RaisesSilence(self):
        cfg = dataclasses.replace(self.cfg, brain_timeout=1.0)
        with self.assertRaises(BrainUnavailable) as ctx:
            CommandBrain(cfg, self.script("sleep 30\n"), "m").complete("с", "п")
        self.assertEqual("silence", ctx.exception.kind)

    def test_complete_EmptyStdout_RaisesBrainError(self):
        with self.assertRaises(BrainError):
            CommandBrain(self.cfg, self.script("exit 0\n"), "m").complete("с", "п")

    def test_probe_UsesOkPrompt(self):
        out = self.dir / "probe.json"
        reply = CommandBrain(self.cfg, self.script(f"cat > {out}\necho OK\n"), "m").probe()
        self.assertEqual("OK", reply.text.strip())
        self.assertIn("ответь OK", out.read_text(encoding="utf-8"))


class FallbackTests(BrainTestCase):
    def test_primaryUnavailable_FallbackAnswersInSameCall(self):
        env = {**self.env, "FAKE_BAD_MODEL": "opencode/primary-free"}
        brain = FallbackBrain(OpencodeBrain(self.cfg, "opencode/primary-free", env=env),
                              OpencodeBrain(self.cfg, "opencode/second-free", env=env))
        reply = brain.complete("с", "п")
        self.assertEqual("opencode/second-free", reply.model, "ответила запасная модель")
        self.assertEqual(2, self.runs(), "одна попытка на основной и одна на запасной")

    def test_bothUnavailable_RaisesUnavailableWithBothTexts(self):
        env = {**self.env, "FAKE_ERROR": "json", "FAKE_ERROR_TEXT": "Free usage exceeded"}
        brain = FallbackBrain(OpencodeBrain(self.cfg, "opencode/a-free", env=env), OpencodeBrain(self.cfg, "opencode/b-free", env=env))
        with self.assertRaises(BrainUnavailable) as ctx:
            brain.complete("с", "п")
        self.assertEqual("provider", ctx.exception.kind)
        self.assertIn("a-free", ctx.exception.text)
        self.assertIn("b-free", ctx.exception.text)

    def test_brainError_NotRetriedOnFallback(self):
        env = {**self.env, "FAKE_GARBAGE": "1"}
        brain = FallbackBrain(OpencodeBrain(self.cfg, "opencode/a-free", env=env), OpencodeBrain(self.cfg, "opencode/b-free", env=env))
        with self.assertRaises(BrainError):
            brain.complete("с", "п")
        self.assertEqual(1, self.runs())

    def test_primaryHealthy_FallbackUntouched(self):
        brain = FallbackBrain(self.brain("opencode/a-free"), self.brain("opencode/b-free"))
        self.assertEqual("opencode/a-free", brain.complete("с", "п").model)
        self.assertEqual(1, self.runs())

    def test_probe_ProviderDownOnPrimary_ProbesFallbackToo(self):
        env = {**self.env, "FAKE_PROBE_FAIL": "1"}
        brain = FallbackBrain(OpencodeBrain(self.cfg, "opencode/a-free", env=env), OpencodeBrain(self.cfg, "opencode/b-free", env=env))
        with self.assertRaises(BrainUnavailable):
            brain.probe()
        self.assertEqual(2, self.runs())


class BrainGuardTests(unittest.TestCase):
    PATTERN_LINE = re.compile(r"^PROVIDER_PATTERN='(.*)'$")

    def script_pattern(self):
        script = config.REPO_ROOT / "scripts" / "opencode-task.sh"
        grep = subprocess.run(["grep", "-m1", "^PROVIDER_PATTERN=", str(script)], capture_output=True, text=True, check=True)
        match = self.PATTERN_LINE.match(grep.stdout.strip())
        self.assertIsNotNone(match, "в scripts/opencode-task.sh не найдена строка PROVIDER_PATTERN='…'")
        return match.group(1)

    def test_providerPattern_EqualsOneInOpencodeTaskScript(self):
        self.assertEqual(self.script_pattern(), brain_module.PROVIDER_PATTERN,
                         "PROVIDER_PATTERN в brain.py разошёлся с scripts/opencode-task.sh: сбои провайдера "
                         "опознаются по-разному. Обновите обе копии одним коммитом")

    def test_providerPatternGuard_FindsDifferenceInSample(self):
        self.assertNotEqual(self.script_pattern() + "|новое", brain_module.PROVIDER_PATTERN, "сторож не слеп к расхождению")

    def test_providerPattern_RecognisesTypicalFailures(self):
        pattern = re.compile(brain_module.PROVIDER_PATTERN, re.IGNORECASE)
        for text in ("Free usage exceeded", "FreeUsageLimitError", "Rate limit exceeded", "quota exhausted", "status 429 ",
                     "OpenCode's free tier can only be used from within OpenCode",
                     "gateway 502 error", "upstream timeout"):
            self.assertRegex(text, pattern, f"«{text}» должно опознаваться как сбой провайдера")

    def test_source_HasNoAutoApproveFlag(self):
        source = (APP / "brain.py").read_text(encoding="utf-8")
        for flag in FORBIDDEN_FLAGS:
            self.assertNotIn(flag, source, f"в brain.py не должно быть {flag}: песочница держится на автоматическом отказе ask")
            self.assertIsNotNone(re.search(re.escape(flag), f'args = ["run", "{flag}"]'), f"сторож не слеп: находит {flag} в образце")

    def test_defaultModels_AreListedAsWorkingInExecutorModelsAndNotPaid(self):
        table = (config.REPO_ROOT / "docs" / "EXECUTOR-MODELS.md").read_text(encoding="utf-8")
        defaults = config.from_env({})
        for model in (defaults.model, defaults.model_fallback):
            self.assertFalse(model.startswith("opencode-go/"), f"{model}: opencode-go идёт через платную подписку")
            self.assertTrue(model.startswith("opencode/"))
            name = model.split("/", 1)[1]
            row = re.search(rf"^\| {re.escape(name)} \| (\S+) \|", table, re.MULTILINE)
            self.assertIsNotNone(row, f"модель {model} не найдена в таблице docs/EXECUTOR-MODELS.md")
            self.assertEqual("да", row.group(1), f"модель {model} в docs/EXECUTOR-MODELS.md не помечена рабочей")


if __name__ == "__main__":
    unittest.main()
