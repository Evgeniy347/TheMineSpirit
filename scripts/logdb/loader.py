#!/usr/bin/env python3
"""Загрузчик JSONL и старых текстовых логов в SQLite.

Схема — scripts/logdb/schema.json, единственное описание таблиц и полей.
Загрузчик идемпотентен: состояние хранится по пути файла и смещению.
"""
import argparse
import datetime as dt
import gzip
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCHEMA_FILE = HERE / "schema.json"
SCHEMA = json.loads(SCHEMA_FILE.read_text(encoding="utf-8"))
VERSION = int(SCHEMA["version"])
CAUSES = set(SCHEMA["causes"])
TYPES = SCHEMA["types"]
TABLES = SCHEMA["tables"]
SQL_TYPES = {"text": "TEXT", "integer": "INTEGER", "real": "REAL"}
COMMON = ["ts", "tick", "build", "source"]


def connect(db_path):
    path = Path(db_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path), timeout=10)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA busy_timeout=10000")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def ensure_schema(conn):
    conn.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    conn.execute("CREATE TABLE IF NOT EXISTS migrations (version INTEGER PRIMARY KEY, applied_ts INTEGER NOT NULL)")
    row = conn.execute("SELECT value FROM meta WHERE key='schema_version'").fetchone()
    current = int(row[0]) if row else 0
    if current > VERSION:
        raise SystemExit(
            "База логов новее загрузчика: схема %d, загрузчик понимает %d. Обновите scripts/logdb."
            % (current, VERSION)
        )
    if current == VERSION:
        return
    for version in range(current + 1, VERSION + 1):
        _migrate(conn, version)
        conn.execute(
            "INSERT OR REPLACE INTO meta(key, value) VALUES ('schema_version', ?)", (str(version),)
        )
        conn.execute(
            "INSERT OR REPLACE INTO migrations(version, applied_ts) VALUES (?, ?)",
            (version, int(dt.datetime.now(dt.timezone.utc).timestamp() * 1000)),
        )
        conn.commit()


def _migrate(conn, version):
    if version == 1:
        _create_v1(conn)
        return
    raise SystemExit("Нет миграции %d: схема базы не обновится, новые таблицы не появятся" % version)


def _create_v1(conn):
    for table, spec in TABLES.items():
        columns = [
            "id INTEGER PRIMARY KEY AUTOINCREMENT",
            "ts INTEGER NOT NULL",
            "tick INTEGER NOT NULL DEFAULT 0",
            "build TEXT NOT NULL DEFAULT 'unknown'",
            "source TEXT NOT NULL CHECK (source IN ('live', 'import'))",
        ]
        for name, column in spec["columns"].items():
            sql = '"%s" %s' % (name, SQL_TYPES[column["type"]])
            if column.get("required"):
                sql += " NOT NULL"
            columns.append(sql)
        conn.execute('CREATE TABLE IF NOT EXISTS "%s" (%s)' % (table, ", ".join(columns)))
        indexes = list(spec.get("indexes", []))
        indexes.append(["ts"])
        for cols in indexes:
            name = "idx_%s_%s" % (table, "_".join(cols))
            conn.execute(
                'CREATE INDEX IF NOT EXISTS "%s" ON "%s" (%s)'
                % (name, table, ", ".join('"%s"' % col for col in cols))
            )
    conn.execute(
        "CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5("
        "text, content='messages', content_rowid='id', tokenize='unicode61')"
    )
    conn.execute(
        "CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN "
        "INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text); END"
    )
    conn.execute(
        "CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN "
        "INSERT INTO messages_fts(messages_fts, rowid, text) VALUES ('delete', old.id, old.text); END"
    )
    conn.execute(
        "CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN "
        "INSERT INTO messages_fts(messages_fts, rowid, text) VALUES ('delete', old.id, old.text); "
        "INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text); END"
    )
    conn.execute(
        "CREATE VIEW IF NOT EXISTS timeline AS "
        "SELECT ts, 'block_change' AS type, CASE WHEN actor_type='spirit' THEN actor END AS spirit, "
        "ticket, x, y, z, action || ' ' || block_before || ' -> ' || block_after AS summary FROM block_changes "
        "UNION ALL SELECT ts, 'death', spirit, ticket, x, y, z, "
        "event || ' ' || cause || COALESCE(' (' || killer || ')', '') FROM deaths "
        "UNION ALL SELECT ts, 'task_event', spirit, ticket, target_x, target_y, target_z, "
        "event || ' ' || board_case || ' ' || COALESCE(reason, '') FROM task_events "
        "UNION ALL SELECT ts, 'graph_decision', spirit, NULL, NULL, NULL, NULL, "
        "node || ' ' || priority || ' ' || COALESCE(why, '') FROM graph_decisions "
        "UNION ALL SELECT ts, 'route', spirit, NULL, from_x, from_y, from_z, "
        "purpose || ' ' || status || ' ' || COALESCE(reason, '') FROM routes "
        "UNION ALL SELECT ts, 'stuck', spirit, NULL, x, y, z, event || ' ' || COALESCE(reason, '') FROM stuck "
        "UNION ALL SELECT ts, 'item_move', spirit, ticket, container_x, container_y, container_z, "
        "action || ' ' || item || ' x' || count FROM item_moves "
        "UNION ALL SELECT ts, 'spirit_sample', spirit, ticket, x, y, z, state || ' ' || COALESCE(script, '') "
        "FROM spirit_samples "
        "UNION ALL SELECT ts, 'server_health', NULL, NULL, NULL, NULL, NULL, "
        "'mspt=' || mspt || ' players=' || players FROM server_health "
        "UNION ALL SELECT ts, 'message', NULL, NULL, NULL, NULL, NULL, text FROM messages"
    )
    conn.executescript(
        "CREATE TABLE IF NOT EXISTS ingest_files ("
        "path TEXT PRIMARY KEY, size INTEGER NOT NULL, offset INTEGER NOT NULL, mtime REAL, gz INTEGER NOT NULL DEFAULT 0);"
        "CREATE TABLE IF NOT EXISTS ingest_ids ("
        "path TEXT NOT NULL, table_name TEXT NOT NULL, min_id INTEGER NOT NULL, max_id INTEGER NOT NULL,"
        "PRIMARY KEY(path, table_name));"
    )


def _state(conn, path):
    return conn.execute(
        "SELECT size, offset, mtime, gz FROM ingest_files WHERE path=?", (path,)
    ).fetchone()


def _save_state(conn, path, size, offset, mtime, gz):
    conn.execute(
        "INSERT INTO ingest_files(path, size, offset, mtime, gz) VALUES (?, ?, ?, ?, ?) "
        "ON CONFLICT(path) DO UPDATE SET size=excluded.size, offset=excluded.offset, "
        "mtime=excluded.mtime, gz=excluded.gz",
        (path, size, offset, mtime, 1 if gz else 0),
    )


def _drop_ranges(conn, path):
    for table_name, min_id, max_id in conn.execute(
        "SELECT table_name, min_id, max_id FROM ingest_ids WHERE path=?", (path,)
    ).fetchall():
        conn.execute(
            'DELETE FROM "%s" WHERE id BETWEEN ? AND ?' % table_name, (min_id, max_id)
        )
    conn.execute("DELETE FROM ingest_ids WHERE path=?", (path,))


def _note_range(conn, path, table_name, row_id):
    if row_id is None:
        return
    row = conn.execute(
        "SELECT min_id, max_id FROM ingest_ids WHERE path=? AND table_name=?", (path, table_name)
    ).fetchone()
    if row is None:
        conn.execute(
            "INSERT INTO ingest_ids(path, table_name, min_id, max_id) VALUES (?, ?, ?, ?)",
            (path, table_name, row_id, row_id),
        )
    else:
        conn.execute(
            "UPDATE ingest_ids SET min_id=?, max_id=? WHERE path=? AND table_name=?",
            (min(row[0], row_id), max(row[1], row_id), path, table_name),
        )


def _typed_table(record):
    kind = record.get("type")
    if kind not in TYPES:
        raise ValueError("неизвестный тип записи '%s'" % kind)
    return TYPES[kind]


def _convert(value, column):
    if value is None:
        return None
    kind = column["type"]
    if kind == "integer":
        if isinstance(value, bool):
            return 1 if value else 0
        if isinstance(value, int):
            return value
        if isinstance(value, float) and value.is_integer():
            return int(value)
        raise ValueError("поле ожидает целое, получено %r" % (value,))
    if kind == "real":
        return float(value)
    return str(value)


def _validate(record, table_name):
    table = TABLES[table_name]
    kind = record.get("type")
    if kind is None:
        raise ValueError("нет поля type")
    allowed = set(COMMON) | set(table["columns"]) | {"type"}
    extra = set(record) - allowed
    if extra:
        raise ValueError("поля не описаны схемой: %s" % ", ".join(sorted(extra)))
    if not isinstance(record.get("ts"), int) or record["ts"] <= 0:
        raise ValueError("ts должен быть положительным целым (мс UTC)")
    if not isinstance(record.get("tick"), int) or record["tick"] < 0:
        raise ValueError("tick должен быть неотрицательным целым")
    for name, column in table["columns"].items():
        if column.get("required") and (record.get(name) is None or record.get(name) == ""):
            raise ValueError("нет обязательного поля %s" % name)
        if name in record and record[name] is not None:
            _convert(record[name], column)
        if "cause" in column and record.get("cause") is not None:
            if str(record["cause"]) not in CAUSES:
                raise ValueError("неизвестная причина '%s'" % record["cause"])
        values = column.get("values")
        if values and record.get(name) is not None:
            if str(record[name]) not in SCHEMA["event_values"][values]:
                raise ValueError("неизвестное значение %s='%s'" % (name, record[name]))


def _insert(conn, record, source, build):
    table_name = _typed_table(record)
    _validate(record, table_name)
    columns = TABLES[table_name]["columns"]
    if record.get("type") == "death" and record.get("event") == "respawn":
        updated = conn.execute(
            "UPDATE deaths SET respawn_x=?, respawn_y=?, respawn_z=?, respawn_ts=?, "
            "killer=COALESCE(NULLIF(killer, ''), ?) WHERE id=("
            "SELECT id FROM deaths WHERE spirit=? AND event='death' ORDER BY ts DESC LIMIT 1)",
            (
                record.get("respawn_x"),
                record.get("respawn_y"),
                record.get("respawn_z"),
                record.get("respawn_ts"),
                record.get("killer"),
                record.get("spirit"),
            ),
        )
        if updated.rowcount:
            return table_name, None
    names = ["ts", "tick", "build", "source"] + list(columns)
    values = [
        record["ts"],
        record["tick"],
        build or record.get("build") or "unknown",
        source or record.get("source") or "import",
    ]
    for name, column in columns.items():
        values.append(_convert(record.get(name), column))
    placeholders = ", ".join("?" for _ in names)
    cursor = conn.execute(
        'INSERT INTO "%s" (%s) VALUES (%s)'
        % (table_name, ", ".join('"%s"' % name for name in names), placeholders),
        values,
    )
    return table_name, cursor.lastrowid


def _error_message(conn, ts, tick, build, text):
    conn.execute(
        'INSERT INTO "messages" (ts, tick, build, source, level, tag, class, text) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        (ts, tick, build or "unknown", "import", "ERROR", "loader", "logdb.loader", text[:8000]),
    )


def ingest_events_dir(conn, events_dir, default_source=None):
    directory = Path(events_dir)
    if not directory.is_dir():
        return 0
    count = 0
    for path in sorted(directory.glob("events-*.jsonl")):
        count += ingest_jsonl(conn, path, default_source=default_source)
    return count


def ingest_jsonl(conn, path, default_source=None):
    path = Path(path)
    key = str(path.resolve())
    stat = path.stat()
    state = _state(conn, key)
    offset = state[1] if state else 0
    if stat.st_size < offset:
        _drop_ranges(conn, key)
        offset = 0
    if stat.st_size == offset:
        return 0
    data = path.read_bytes()[offset:]
    position = 0
    inserted = 0
    conn.commit()
    try:
        conn.execute("BEGIN")
        while True:
            newline = data.find(b"\n", position)
            if newline < 0:
                break
            raw = data[position:newline]
            position = newline + 1
            line = raw.decode("utf-8", "replace").strip()
            if not line:
                continue
            try:
                record = json.loads(line)
                table_name, row_id = _insert(conn, record, default_source, None)
                _note_range(conn, key, table_name, row_id)
                inserted += 1
            except (ValueError, KeyError, TypeError) as error:
                _error_message(
                    conn,
                    int(dt.datetime.now(dt.timezone.utc).timestamp() * 1000),
                    0,
                    "unknown",
                    "строка JSONL не загружена: %s; строка: %s" % (error, line[:500]),
                )
        new_offset = offset + position
        _save_state(conn, key, stat.st_size, new_offset, stat.st_mtime, False)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    return inserted


HEADER = re.compile(
    r"^\[(\d\d)([A-Z][a-z]{2})(\d{4}) (\d\d):(\d\d):(\d\d)\.(\d{3})\] "
    r"\[([^/\]]+)/([A-Z]+)\] \[([^\]]+)\]: ?(.*)$"
)
MONTHS = {
    "Jan": 1, "Feb": 2, "Mar": 3, "Apr": 4, "May": 5, "Jun": 6,
    "Jul": 7, "Aug": 8, "Sep": 9, "Oct": 10, "Nov": 11, "Dec": 12,
}
BLOCK_COORD = re.compile(r"BlockCoord\[x=(-?\d+), y=(-?\d+), z=(-?\d+)\]")
COORD_X = re.compile(r"\(\s*x=(-?\d+(?:\.\d+)?),\s*y=(-?\d+(?:\.\d+)?),\s*z=(-?\d+(?:\.\d+)?)\s*\)")
COORD_PAREN = re.compile(r"\(\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)\s*\)")
COORD_BARE = re.compile(r"(?<![\w.])(-?\d+),(-?\d+),(-?\d+)(?![\w.])")


def repair_text(text):
    if "Р" not in text and "С" not in text:
        return text
    parts = re.split(r"(\s+)", text)
    for index, part in enumerate(parts):
        if "Р" not in part and "С" not in part:
            continue
        try:
            fixed = part.encode("cp1251").decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            continue
        if fixed.count("Р") + fixed.count("С") < part.count("Р") + part.count("С"):
            parts[index] = fixed
    return "".join(parts)


def coords(text, start=0):
    for regex in (BLOCK_COORD, COORD_X, COORD_PAREN, COORD_BARE):
        match = regex.search(text, start)
        if match:
            return (
                int(float(match.group(1))),
                int(float(match.group(2))),
                int(float(match.group(3))),
            ), match.end()
    return None, start


def _ms(day, month, year, hour, minute, second, millis):
    value = dt.datetime(
        int(year), MONTHS[month], int(day), int(hour), int(minute), int(second),
        int(millis) * 1000, tzinfo=dt.timezone.utc,
    )
    return int(value.timestamp() * 1000)


def _block(name):
    return name if name.startswith("Block{") else "Block{minecraft:%s}" % name.replace("minecraft:", "")


def _record(kind, ts, tick, build, source, **fields):
    record = {"type": kind, "ts": ts, "tick": tick, "build": build or "unknown", "source": source}
    record.update({key: value for key, value in fields.items() if value is not None})
    return record


def parse_text(logger, level, message, ts, tick, build, source):
    """Вернуть (запись, разобрана ли) для известных строк мода."""
    message = repair_text(message)
    text = message
    if "[plot-break]" in text:
        match = re.search(r"\[plot-break\][^:]*: (сломан|отказ ломать) (\S+) в ", text)
        if match:
            point, _ = coords(text, match.end())
            if point:
                badge = re.search(r"дух (\S+) \(([0-9a-f-]{36})\)", text)
                player = re.search(r"игрок (\S+)", text)
                if badge:
                    actor_type, actor = "spirit", badge.group(1)
                elif player:
                    actor_type, actor = "player", player.group(1)
                else:
                    actor_type, actor = "world", "мир"
                ticket = re.search(r"задание ([^;]+);", text)
                basis = re.search(r"основание ([^;]+);", text)
                reason = re.search(r"причина (.+?)(?:; вызов |$)", text)
                kind = "break" if match.group(1) == "сломан" else "refuse"
                return _record(
                    "block_change", ts, tick, build, source,
                    actor_type=actor_type, actor=actor,
                    ticket=None if not ticket or ticket.group(1).strip() in ("—", "-") else ticket.group(1).strip(),
                    action=kind, block_before=_block(match.group(2)),
                    block_after=_block(match.group(2)) if kind == "refuse" else "Block{minecraft:air}",
                    x=point[0], y=point[1], z=point[2],
                    basis=basis.group(1) if basis else "",
                    cause="break" if kind == "break" else "refuse",
                    reason=reason.group(1) if reason else "",
                ), True
    if "[board]" in text:
        match = re.search(
            r"\[board\] (ASSIGNED|BLOCKED|UNBLOCKED|POSTED) задание=(\S+) кейс=(\S+) "
            r"попыток=(\d+)", text
        )
        if match:
            detail = re.search(r"подробности=(.*)$", text)
            target, _ = coords(detail.group(1)) if detail else (None, 0)
            chosen = re.search(r"выбор=([^\s—]+)", text)
            reason = re.search(r"причина=(.+?)(?:, history=|$)", text)
            return _record(
                "task_event", ts, tick, build, source,
                ticket=match.group(2), board_case=match.group(3), event=match.group(1),
                spirit=chosen.group(1) if chosen and chosen.group(1) != "—" else None,
                attempts=int(match.group(4)),
                cause={
                    "ASSIGNED": "chosen",
                    "POSTED": "chosen",
                    "BLOCKED": "blocked",
                    "UNBLOCKED": "unblocked",
                }.get(match.group(1), "failed"),
                reason=(reason.group(1) if reason else (detail.group(1)[:300] if detail else "")),
                target_x=target[0] if target else None,
                target_y=target[1] if target else None,
                target_z=target[2] if target else None,
            ), True
    if "[incident]" in text:
        match = re.search(
            r"\[incident\] дух (\S+) \(([0-9a-f-]{36})\) провалил задание доски (\S+) "
            r"\(кейс (\S+)\): (.+?); доска", text
        )
        if match:
            return _record(
                "task_event", ts, tick, build, source,
                ticket=match.group(3), board_case=match.group(4), event="FAILED",
                spirit=match.group(1), spirit_id=match.group(2), cause="failed",
                reason=match.group(5),
            ), True
    if "[death]" in text:
        match = re.search(r"\[death\] дух (\S+): (.*?), место ", text)
        if match:
            point, _ = coords(text, match.end())
            if point:
                inventory = re.search(r"инвентарь (\{.*?\}),", text)
                return _record(
                    "death", ts, tick, build, source,
                    event="death", spirit=match.group(1), x=point[0], y=point[1], z=point[2],
                    cause="death", killer="", reason=match.group(2),
                    inventory=inventory.group(1) if inventory else "",
                    script="",
                ), True
    if "[respawn]" in text:
        match = re.search(r"\[respawn\] дух (\S+) погиб \((.*?)\) → возрождён в ", text)
        if match:
            point, _ = coords(text, match.end())
            if point:
                return _record(
                    "death", ts, tick, build, source,
                    event="respawn", spirit=match.group(1), x=point[0], y=point[1], z=point[2],
                    cause="respawn", killer="", reason=match.group(2),
                    respawn_x=point[0], respawn_y=point[1], respawn_z=point[2],
                    respawn_ts=ts, recovered=0, script="",
                ), True
    if "[route]" in text:
        match = re.search(
            r"\[route\] дух (\S+) \(([^)]+)\): из .*? к .*? — (\w+), сегментов (\d+), "
            r"шагов (\d+), узлов (\d+), конец ", text
        )
        if match:
            from_point, _ = coords(text, text.find(" из ") + 4)
            to_point, end_pos = coords(text, text.find(" к ") + 3)
            end_point, _ = coords(text, text.find("конец ") + 6)
            millis = re.search(r", (\d+) мс", text)
            if from_point and to_point:
                return _record(
                    "route", ts, tick, build, source,
                    spirit=match.group(1), purpose=match.group(2), from_x=from_point[0],
                    from_y=from_point[1], from_z=from_point[2], to_x=to_point[0],
                    to_y=to_point[1], to_z=to_point[2], status=match.group(3),
                    nodes=int(match.group(6)), ms=int(millis.group(1)) if millis else None,
                    end_x=end_point[0] if end_point else None,
                    end_y=end_point[1] if end_point else None,
                    end_z=end_point[2] if end_point else None,
                    cause="chosen" if match.group(3) == "FOUND" else "no_path",
                    reason="",
                ), True
    if "[stuck]" in text:
        match = re.search(
            r"\[stuck\] дух (\S+): метка «не двигается» (поставлена|снята) с тика (\d+) в (.*?): (.*)",
            text,
        )
        if match:
            point, _ = coords(match.group(4))
            return _record(
                "stuck", ts, tick, build, source,
                spirit=match.group(1), event="mark" if match.group(2) == "поставлена" else "clear",
                x=point[0] if point else None, y=point[1] if point else None,
                z=point[2] if point else None, method="", helper="",
                reason=match.group(5), cause="stuck", since_tick=int(match.group(3)),
            ), True
    if "[withdraw]" in text or "[deposit]" in text:
        item = re.search(r"'([^']+)'", text)
        moved = re.search(r"перенесено (\d+)", text) or re.search(r"разгрузил (\d+)", text)
        if moved and int(moved.group(1)) > 0:
            point, _ = coords(text)
            action = "take" if "[withdraw]" in text else "deposit"
            badge = re.search(r"\[(?:withdraw|deposit)\] (?:дух )?(\S+?):", text)
            if not badge:
                badge = re.search(r"\[(?:withdraw|deposit)\] дух (\S+)", text)
            spirit = badge.group(1) if badge else "?"
            return _record(
                "item_move", ts, tick, build, source,
                spirit=spirit, action=action, item=item.group(1) if item else "*",
                count=int(moved.group(1)),
                container_x=point[0] if point else None,
                container_y=point[1] if point else None,
                container_z=point[2] if point else None,
            ), True
    if "[pos] сводка" in text:
        match = re.search(r"\[pos\] сводка: дух (\S+) остаётся в ", text)
        if match:
            point, _ = coords(text, match.end())
            script = re.search(r"script=([^,\s]+)", text)
            if point:
                return _record(
                    "spirit_sample", ts, tick, build, source,
                    spirit=match.group(1), x=point[0], y=point[1], z=point[2],
                    state="online", script=script.group(1) if script else "",
                ), True
    if "[tick] MSPT" in text:
        match = re.search(r"\[tick\] MSPT ([\d.]+), худший ([\d.]+) за (\d+) тиков", text)
        if match:
            return _record(
                "server_health", ts, tick, build, source,
                mspt=float(match.group(1)), worst_mspt=float(match.group(2)), players=0,
                event="sample",
            ), True
    if "[graph] решение" in text:
        match = re.search(r"\[graph\] решение: (\S+) → (\S+) \((\d+)\)", text)
        if match:
            next_node = re.search(r"следующий (\S+) \(\d+\): (.*)$", text)
            return _record(
                "graph_decision", ts, tick, build, source,
                node=match.group(2), spirit=match.group(1), priority=int(match.group(3)),
                feasible=1, chosen=1,
                next_node=next_node.group(1) if next_node else "",
                why=next_node.group(2) if next_node else "",
                cause="chosen",
            ), True
    return None, False


def _text_messages(logger, level, message, ts, tick, build, source):
    tag = re.match(r"\[([^\]]+)\]", message)
    return _record(
        "message", ts, tick, build, source,
        level=level, tag=tag.group(1) if tag else "", **{"class": logger},
        text=message[:8000],
    )


def _skip_line(counters, logger, line):
    counters["skipped"] += 1
    name = logger or "—"
    by_logger = counters["skipped_by_logger"]
    by_logger[name] = by_logger.get(name, 0) + 1
    if len(counters["skipped_samples"]) < 5:
        counters["skipped_samples"].append(line[:300])


def _store_message(conn, key, counters, logger, level, message, ts, build, source):
    table_name, row_id = _insert(
        conn, _text_messages(logger, level, message, ts, 0, build, source), source, build
    )
    if row_id is None:
        return False
    _note_range(conn, key, table_name, row_id)
    counters["stored"] += 1
    return True


def ingest_text(conn, path, default_source="import", build="unknown", levels=None):
    """Загрузить текстовый лог. levels — оставить только эти уровни (для debug-логов)."""
    path = Path(path)
    key = str(path.resolve())
    stat = path.stat()
    gz = path.suffix == ".gz"
    state = _state(conn, key)
    if gz:
        if state and state[1] >= stat.st_size:
            return _empty_counters()
        data = gzip.decompress(path.read_bytes())
        offset = 0
        file_size = stat.st_size
    else:
        offset = state[1] if state else 0
        if stat.st_size < offset:
            _drop_ranges(conn, key)
            offset = 0
        data = path.read_bytes()[offset:]
        file_size = stat.st_size
    counters = _empty_counters()
    current_build = build
    conn.commit()
    conn.execute("BEGIN")

    def read_line(raw):
        nonlocal current_build
        try:
            line = raw.decode("utf-8")
        except UnicodeDecodeError:
            try:
                line = raw.decode("cp1251")
            except UnicodeDecodeError:
                line = raw.decode("utf-8", "replace")
        counters["lines"] += 1
        header = HEADER.match(line)
        if not header:
            _skip_line(counters, "", line)
            return
        try:
            ts = _ms(*header.group(1, 2, 3, 4, 5, 6, 7))
        except (ValueError, KeyError):
            _skip_line(counters, "", line)
            return
        level = header.group(9)
        logger = header.group(10)
        message = repair_text(header.group(11))
        if levels and level not in levels:
            _skip_line(counters, logger, line)
            return
        build_match = re.search(r"The Mine Spirit ([\w.+-]+) загружен", message)
        if build_match:
            current_build = build_match.group(1)
        record, parsed = parse_text(logger, level, message, ts, 0, current_build, default_source)
        if parsed:
            try:
                table_name, row_id = _insert(conn, record, default_source, current_build)
            except (ValueError, KeyError, TypeError) as error:
                counters["errors"] += 1
                if len(counters["error_samples"]) < 5:
                    counters["error_samples"].append("%s: %s" % (error, line[:300]))
                try:
                    _store_message(
                        conn, key, counters, logger, level, message, ts, current_build, default_source
                    )
                except (ValueError, KeyError, TypeError):
                    counters["errors"] += 1
                return
            counters["typed"][table_name] = counters["typed"].get(table_name, 0) + 1
            if row_id is None:
                counters["merged"] += 1
            else:
                _note_range(conn, key, table_name, row_id)
                counters["stored"] += 1
            return
        if "theminespirit" in logger or level in ("WARN", "ERROR"):
            if _store_message(
                conn, key, counters, logger, level, message, ts, current_build, default_source
            ):
                counters["messages"] += 1
        else:
            _skip_line(counters, logger, line)

    segments = data.split(b"\n")
    trailing = segments.pop()
    for raw in segments:
        read_line(raw)
    if gz and trailing:
        read_line(trailing)
    elif trailing:
        counters["tail"] = 1
    consumed = len(data) - len(trailing)
    _save_state(conn, key, file_size, file_size if gz else offset + consumed, stat.st_mtime, gz)
    conn.commit()
    return counters


def _empty_counters():
    return {
        "lines": 0, "typed": {}, "messages": 0, "skipped": 0,
        "errors": 0, "merged": 0, "stored": 0, "tail": 0,
        "skipped_by_logger": {}, "skipped_samples": [], "error_samples": [],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Загрузить JSONL событий в logs/events.db")
    parser.add_argument("--db", required=True)
    parser.add_argument("--events")
    parser.add_argument("--text")
    parser.add_argument("--source", default=None)
    args = parser.parse_args(argv)
    conn = connect(args.db)
    ensure_schema(conn)
    count = 0
    if args.events:
        count += ingest_events_dir(conn, args.events, default_source=args.source)
    if args.text:
        counters = ingest_text(conn, args.text, default_source=args.source or "import")
        count += sum(counters["typed"].values()) + counters["messages"]
    conn.commit()
    conn.close()
    print("загружено записей: %d" % count)


if __name__ == "__main__":
    main()
