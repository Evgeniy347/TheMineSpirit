import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOGDB = HERE.parent
sys.path.insert(0, str(LOGDB))
import loader

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


if __name__ == "__main__":
    unittest.main()
