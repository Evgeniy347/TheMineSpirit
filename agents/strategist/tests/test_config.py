import unittest

import config
from config import ConfigError, from_env


class ConfigTests(unittest.TestCase):
    def test_defaults_EmptyEnvironment_MatchChecklist(self):
        cfg = from_env({})
        self.assertEqual("http://127.0.0.1:8765", cfg.api)
        self.assertEqual("/opt/minecraft/strategist", cfg.home.as_posix())
        self.assertEqual((300, "dry", "opencode"), (cfg.interval, cfg.mode, cfg.brain),
                         "интервал 300 с, режим dry по умолчанию")
        self.assertEqual("opencode/longcat-2.5-preview-free", cfg.model)
        self.assertEqual("opencode/nemotron-3-ultra-free", cfg.model_fallback)
        self.assertEqual((10, 60, 30, 1800, 3, 300), (cfg.tasks_per_hour, cfg.task_gap, cfg.brain_calls_per_hour,
                                                        cfg.plan_cooldown, cfg.failures_to_pause, cfg.pause_duration),
                         "10 задач на духа в час и пауза 5 мин после 3 провалов — умолчания, смягчённые после этапа 2")
        self.assertEqual((10, 1800, 5, 32, 8), (cfg.task_poll, cfg.task_timeout, cfg.rest_per_second, cfg.max_nodes,
                                                 cfg.max_root_steps))
        self.assertEqual(240, cfg.brain_timeout, "STRATEGIST_BRAIN_TIMEOUT по умолчанию 240 с")
        self.assertEqual(900, cfg.pending_ttl, "срок годности неотправленного решения 15 мин")
        self.assertEqual(24, cfg.observe_radius, "радиус наблюдения в пределах ScanRadius 1..32")

    def test_overrides_ValidValues_Applied(self):
        cfg = from_env({"SPIRIT_API": "http://192.168.0.147:8765/", "STRATEGIST_HOME": "/tmp/x",
                        "STRATEGIST_INTERVAL": "90", "STRATEGIST_MODE": "live", "STRATEGIST_MODEL": "opencode/other-free"})
        self.assertEqual("http://192.168.0.147:8765", cfg.api, "завершающий слэш адреса убирается")
        self.assertEqual(("live", 90, "opencode/other-free"), (cfg.mode, cfg.interval, cfg.model))

    def test_invalid_Value_RaisesConfigErrorNamingVariable(self):
        table = [
            ("STRATEGIST_INTERVAL", "59"), ("STRATEGIST_INTERVAL", "3601"), ("STRATEGIST_INTERVAL", "часто"),
            ("STRATEGIST_MODE", "боевой"), ("STRATEGIST_BRAIN", "gpt"), ("SPIRIT_API", "192.168.0.1:8765"),
            ("SPIRIT_API", ""), ("STRATEGIST_MODEL", "  "), ("STRATEGIST_BRAIN_TIMEOUT", "0"),
            ("STRATEGIST_TASKS_PER_HOUR", "0"), ("STRATEGIST_TASKS_PER_HOUR", "6.5"),
            ("STRATEGIST_REST_PER_SECOND", "100"), ("STRATEGIST_MAX_NODES", "257"), ("STRATEGIST_TASK_POLL", "0"),
            ("STRATEGIST_CONFIRM_LIVE", "да"), ("STRATEGIST_JOURNAL_FILES", "0"), ("STRATEGIST_OBSERVE_RADIUS", "0"), ("STRATEGIST_PENDING_TTL", "59"), ("STRATEGIST_OBSERVE_RADIUS", "33"), ("STRATEGIST_TASK_TIMEOUT", "5"),
        ]
        for name, value in table:
            with self.subTest(name=name, value=value):
                with self.assertRaises(ConfigError,
                                       msg=f"{name}={value!r} должно отвергаться, а не заменяться умолчанием") as ctx:
                    from_env({name: value})
                self.assertIn(name, str(ctx.exception), "сообщение называет переменную")

    def test_interval_Boundaries(self):
        self.assertEqual(60, from_env({"STRATEGIST_INTERVAL": "60"}).interval)
        self.assertEqual(3600, from_env({"STRATEGIST_INTERVAL": "3600"}).interval)
        with self.assertRaises(ConfigError):
            from_env({"STRATEGIST_INTERVAL": "59.9"})

    def test_commandBrain_WithoutCommand_RaisesConfigError(self):
        with self.assertRaises(ConfigError) as ctx:
            from_env({"STRATEGIST_BRAIN": "command"})
        self.assertIn("STRATEGIST_BRAIN_COMMAND", str(ctx.exception))
        self.assertEqual("cat", from_env({"STRATEGIST_BRAIN": "command", "STRATEGIST_BRAIN_COMMAND": "cat"}).brain_command)

    def test_paths_DerivedFromHome(self):
        cfg = from_env({"STRATEGIST_HOME": "/srv/s"})
        self.assertEqual(["state.json", "journal.jsonl", "PAUSE", "sandbox", "opencode-data"],
                         [cfg.state_path.name, cfg.journal_path.name, cfg.pause_path.name, cfg.sandbox_dir.name,
                          cfg.opencode_data.name])
        self.assertEqual("/srv/s", cfg.state_path.parent.as_posix())
        self.assertEqual("/srv/s/opencode-data", cfg.opencode_data.as_posix(),
                         "каталог данных мозга живёт в STRATEGIST_HOME, а не в общем ~/.local/share/opencode")

    def test_confirmLive_OneMeansTrue(self):
        self.assertTrue(from_env({"STRATEGIST_CONFIRM_LIVE": "1"}).confirm_live)
        self.assertFalse(from_env({"STRATEGIST_CONFIRM_LIVE": "0"}).confirm_live)

    def test_module_ExposesRepoRootWithApiDoc(self):
        self.assertTrue((config.REPO_ROOT / "docs" / "API.md").is_file(),
                        "промпт вырезает «Формат плана» из docs/API.md")


if __name__ == "__main__":
    unittest.main()
