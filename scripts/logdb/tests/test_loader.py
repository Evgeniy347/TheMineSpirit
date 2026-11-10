import gzip
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

HERE = Path(__file__).resolve().parent
LOGDB = HERE.parent
sys.path.insert(0, str(LOGDB))
import loader
import logq

FIXTURE = LOGDB / "fixtures" / "legacy.log"


def record(kind, **fields):
    data = {
        "type": kind,
        "ts": 1_700_000_000_000,
        "tick": 1,
        "build": "1.0+test",
        "source": "live",
    }
    data.update(fields)
    return data


def block(index=1):
    return record(
        "block_change",
        actor_type="spirit",
        actor="дух-1",
        action="break",
        block_before="Block{minecraft:stone}",
        block_after="Block{minecraft:air}",
        x=index,
        y=64,
        z=index,
        cause="break",
    )


class LoaderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)
        self.events = self.dir / "events"
        self.events.mkdir()
        self.db = self.dir / "events.db"
        self.conn = loader.connect(self.db)
        loader.ensure_schema(self.conn)

    def tearDown(self):
        self.conn.close()
        self.temp.cleanup()

    def write(self, name, rows, newline=True):
        text = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows)
        if newline:
            text += "\n"
        (self.events / name).write_text(text, encoding="utf-8")

    def count(self, table):
        return self.conn.execute('SELECT COUNT(*) FROM "%s"' % table).fetchone()[0]

    def test_jsonlSecondRun_DoesNotDuplicate(self):
        self.write("events-00001.jsonl", [block(1), block(2)])
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(0, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_incompleteLastLine_IsReadAfterNewline(self):
        line = json.dumps(block(1), ensure_ascii=False)
        (self.events / "events-00001.jsonl").write_text(line, encoding="utf-8")
        self.assertEqual(0, loader.ingest_events_dir(self.conn, self.events))
        with (self.events / "events-00001.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("\n" + json.dumps(block(2), ensure_ascii=False) + "\n")
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_rotation_ReadsEveryFile(self):
        self.write("events-00001.jsonl", [block(1)])
        self.write("events-00002.jsonl", [block(2)])
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_unknownType_GoesToMessagesNotCrash(self):
        self.write("events-00001.jsonl", [{"type": "no_such", "ts": 1, "tick": 0}])
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(0, self.count("block_changes"))
        self.assertEqual(1, self.count("messages"))

    def test_truncatedFile_DropsItsRowsAndReadsAgain(self):
        self.write("events-00001.jsonl", [block(1), block(2)])
        loader.ingest_events_dir(self.conn, self.events)
        (self.events / "events-00001.jsonl").write_text("", encoding="utf-8")
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(0, self.count("block_changes"))
        self.write("events-00001.jsonl", [block(3)])
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(1, self.count("block_changes"))

    def test_schema_HasVersionAndMigrationOnce(self):
        version = self.conn.execute("SELECT value FROM meta WHERE key='schema_version'").fetchone()[0]
        loader.ensure_schema(self.conn)
        migrations = self.conn.execute("SELECT COUNT(*) FROM migrations").fetchone()[0]
        self.assertEqual(str(loader.VERSION), version)
        self.assertEqual(1, migrations)

    def test_text_AllFourCoordinateFormats_AreParsed(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.assertEqual(4, counters["typed"].get("block_changes"))
        points = self.conn.execute(
            "SELECT x, y, z FROM block_changes ORDER BY ts"
        ).fetchall()
        self.assertEqual([(-181, 68, -518), (-182, 69, -519), (-183, 69, -520), (-184, 70, -521)], points)

    def test_text_MojibakeIncident_IsRepaired(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.assertGreaterEqual(counters["typed"].get("task_events", 0), 2)
        reason = self.conn.execute(
            "SELECT reason FROM task_events WHERE event='FAILED'"
        ).fetchone()[0]
        self.assertIn("нет пути", reason)

    def test_text_AllTypedTables_AreFilled(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        for table in ("block_changes", "task_events", "deaths", "routes", "stuck", "item_moves", "spirit_samples", "server_health", "graph_decisions"):
            self.assertGreater(self.count(table), 0, table)
        self.assertGreater(counters["messages"], 0)

    def test_logq_cell_AcceptsEveryCoordinateFormat(self):
        loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.conn.close()
        for cell in (
            ["-181,68,-518"],
            ["(-182, 69, -519)"],
            ["BlockCoord[x=-183, y=69, z=-520]"],
            ["(x=-184.5, y=70.0, z=-521.5)"],
        ):
            output = subprocess.run(
                [sys.executable, str(LOGDB / "logq.py"), "--db", str(self.db), "--json", "--limit", "5", "cell", *cell],
                capture_output=True,
                check=True,
                text=True,
                encoding="utf-8",
            ).stdout
            rows = json.loads(output)
            self.assertTrue(rows, cell)
        self.conn = loader.connect(self.db)

    def test_logq_sql_RejectsWrites(self):
        result = subprocess.run(
            [sys.executable, str(LOGDB / "logq.py"), "--db", str(self.db), "sql", "DELETE FROM block_changes"],
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("только чтение", result.stderr + result.stdout)

    def write_log(self, name, lines):
        path = self.dir / name
        path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")
        return path

    def test_remoteCommand_ReadsOnlyWithoutJsonlIngest(self):
        args = logq.build_parser().parse_args(["--main", "cell", "--", "-177", "68", "-517"])
        command = logq.remote_command(args, "/db/events.db")
        self.assertNotIn("--logs", command, "основная база пишется только cron, logq --main не дочитывает JSONL")
        self.assertEqual(["cell", "-177", "68", "-517"], command[-4:])
        failures = logq.build_parser().parse_args(["--main", "failures", "--since", "1h"])
        command = logq.remote_command(failures, "/db/events.db")
        self.assertEqual(["--since", "1h"], command[-2:])

    def test_remoteCommand_ForwardsSinceForEveryFilteredCommand(self):
        cases = (
            ["spirit", "дух-1"],
            ["failures"],
            ["blocks", "--spirit", "дух-1"],
            ["deaths"],
            ["stuck"],
            ["items", "stone"],
            ["health"],
            ["text", "нет пути"],
            ["grep", "rescue"],
        )
        for argv in cases:
            with self.subTest(command=argv[0]):
                args = logq.build_parser().parse_args(["--main", *argv, "--since", "1h"])
                command = logq.remote_command(args, "/db/events.db")
                self.assertEqual(
                    ["--since", "1h"],
                    command[-2:],
                    "фильтр по времени обязан доехать до основного сервера: %s" % argv[0],
                )

    def test_remoteSync_ChangedOrMissingFile_IsUploaded(self):
        local = self.dir / "local"
        local.mkdir()
        (local / "schema.json").write_bytes(b"same")
        (local / "loader.py").write_bytes(b"new-loader")
        (local / "logq.py").write_bytes(b"new-logq")
        remote = {
            "%s/schema.json" % logq.MAIN_SCRIPTS: b"same",
            "%s/loader.py" % logq.MAIN_SCRIPTS: b"old-loader",
        }
        uploaded = []

        def fake_run(command, **kwargs):
            if command[-1].startswith("cat > "):
                uploaded.append(command[-1].split("/")[-1])
                return SimpleNamespace(returncode=0, stdout=b"")
            return SimpleNamespace(returncode=0, stdout=remote.get(command[-1][len("cat ") :], b""))

        logq.sync_remote(["ssh", "host"], run=fake_run, here=local)

        self.assertEqual(
            ["loader.py", "logq.py"],
            uploaded,
            "устаревший loader.py обязан обновиться, отсутствующий logq.py — залиться: иначе правка "
            "health --since не действует на основном сервере до ручной раскладки",
        )

    def test_remoteSync_SameFiles_AreNotUploaded(self):
        local = self.dir / "local"
        local.mkdir()
        for name in ("schema.json", "loader.py", "logq.py"):
            (local / name).write_bytes(name.encode())
        remote = {"%s/%s" % (logq.MAIN_SCRIPTS, name): name.encode() for name in ("schema.json", "loader.py", "logq.py")}
        uploaded = []

        def fake_run(command, **kwargs):
            if command[-1].startswith("cat > "):
                uploaded.append(command[-1])
                return SimpleNamespace(returncode=0, stdout=b"")
            return SimpleNamespace(returncode=0, stdout=remote[command[-1][len("cat ") :]])

        logq.sync_remote(["ssh", "host"], run=fake_run, here=local)

        self.assertEqual([], uploaded, "одинаковые файлы не перезаливаются при каждом logq --main")

    def test_health_Since_LeavesOnlyFreshRows(self):
        self.write(
            "events-00001.jsonl",
            [
                record("server_health", ts=1_000_000_000_000, mspt=1.0, players=0),
                record("server_health", ts=1_700_000_000_000, mspt=2.0, players=0),
            ],
        )
        loader.ingest_events_dir(self.conn, self.events)
        self.conn.commit()

        args = logq.build_parser().parse_args(["health", "--since", "1500000000000"])
        _, rows = logq.dispatch(args, self.conn)

        self.assertEqual(1, len(rows), "здоровье сервера тоже фильтруется по ts, как и остальные команды")
        self.assertEqual(2.0, rows[0][2], "свежая строка осталась, старая отсечена")

    def test_text_SkippedAndStored_AddUp(self):
        path = self.write_log(
            "plain.log",
            [
                "[09Nov2026 11:00:00.000] [Server thread/INFO] [net.minecraft.Foo/]: обычная строка",
                "[09Nov2026 11:00:01.000] [Server thread/WARN] [net.minecraft.Foo/]: предупреждение",
            ],
        )
        counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(2, counters["lines"])
        self.assertEqual(1, counters["skipped"])
        self.assertEqual(1, counters["messages"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(counters["lines"], counters["stored"] + counters["merged"] + counters["skipped"])
        self.assertEqual({"net.minecraft.Foo/": 1}, counters["skipped_by_logger"])
        self.assertTrue(counters["skipped_samples"])

    def test_text_LevelFilter_LeavesOnlyRequestedLevel(self):
        path = self.write_log(
            "mixed.log",
            [
                "[09Nov2026 11:00:00.000] [Server thread/INFO] [com.theminespirit.Board/]: [board] "
                "ASSIGNED задание=t-1 кейс=GATHER_MATERIALS попыток=1 выбор=spirit-1",
                "[09Nov2026 11:00:01.000] [Server thread/DEBUG] [com.theminespirit.Move/]: подробности шага",
            ],
        )
        counters = loader.ingest_text(self.conn, path, levels={"DEBUG"})
        self.conn.commit()
        self.assertEqual(2, counters["lines"])
        self.assertEqual(1, counters["skipped"])
        self.assertEqual(1, counters["messages"])
        self.assertEqual({}, counters["typed"])

    def test_text_BrokenRecord_IsStoredAsErrorAndDoesNotAbort(self):
        path = self.write_log(
            "broken.log",
            ["[09Nov2026 11:00:00.000] [Server thread/INFO] [com.theminespirit.Board/]: [board] сломано"],
        )
        bad = {"type": "block_change", "ts": 1_700_000_000_000, "tick": 0, "build": "x", "source": "import"}
        with mock.patch.object(loader, "parse_text", return_value=(bad, True)):
            counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(1, counters["lines"])
        self.assertEqual(1, counters["errors"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(0, counters["messages"])
        self.assertEqual(counters["lines"], counters["stored"] + counters["merged"] + counters["skipped"])
        self.assertEqual(1, self.count("messages"))
        self.assertIn("нет обязательного поля", counters["error_samples"][0])

    def test_text_GzipWithoutFinalNewline_IsImported(self):
        path = self.dir / "tail.log.gz"
        with gzip.open(path, "wb") as stream:
            stream.write(
                b"[09Nov2026 11:00:00.000] [Server thread/WARN] [net.minecraft.Foo/]: \xd1\x85\xd0\xb2\xd0\xbe\xd1\x81\xd1\x82"
            )
        counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(1, counters["lines"])
        self.assertEqual(0, counters["tail"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(1, counters["messages"])

    def test_import_DeleteVerified_RemovesGzAndKeepsLatest(self):
        logs = self.dir / "logs"
        logs.mkdir()
        old = logs / "old.log.gz"
        with gzip.open(old, "wb") as stream:
            stream.write(
                b"[09Nov2026 11:00:00.000] [Server thread/WARN] [net.minecraft.Foo/]: warning\n"
            )
        latest = logs / "latest.log"
        latest.write_text(
            "[09Nov2026 11:00:01.000] [Server thread/WARN] [net.minecraft.Foo/]: warning\n",
            encoding="utf-8",
        )
        report = self.dir / "report.json"
        result = subprocess.run(
            [
                sys.executable, str(LOGDB / "import_legacy.py"),
                "--db", str(self.db), "--logs", str(logs),
                "--exclude", "latest.log", "--report", str(report),
                "--delete-verified",
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(old.exists(), "сверенный архив должен удаляться")
        self.assertTrue(latest.exists(), "latest.log не трогаем")
        data = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(1, len(data["files"]))
        self.assertTrue(data["files"][0]["verified"])
        self.assertTrue(data["files"][0]["deleted"])
        self.assertEqual(1, data["files"][0]["db_rows"])


if __name__ == "__main__":
    unittest.main()
