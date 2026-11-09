#!/usr/bin/env python3
"""Один вход в базу логов: клетка, дух, задание, провалы, смерть, предметы, текст.

Цель выбирается как у scripts/live.sh: своя копия дерева (run-live/*/debug.env),
иначе основной сервер только на чтение через ssh.
"""
import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import loader

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SSH = os.environ.get("LIVE_SSH", "domovoy@192.168.0.147")
MAIN_SERVER = "/opt/minecraft/server"
MAIN_SCRIPTS = "/opt/minecraft/scripts/logdb"
FTS = "messages_fts"


def find_copy():
    root = REPO / "run-live"
    if not root.is_dir():
        return None
    for name in sorted(os.listdir(root)):
        path = root / name
        if (path / "debug.env").exists():
            return path
    return None


def since_ms(value, default_ms=0):
    if value is None:
        return default_ms
    match = re.match(r"^(\d+)([smhd])$", value)
    if match:
        unit = {"s": 1, "m": 60, "h": 3600, "d": 86400}[match.group(2)]
        return int(dt.datetime.now(dt.timezone.utc).timestamp() * 1000) - int(match.group(1)) * unit * 1000
    if value.isdigit():
        return int(value)
    text = value.replace("T", " ")
    for pattern in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%d.%m.%Y %H:%M:%S", "%d.%m.%Y %H:%M"):
        try:
            return int(dt.datetime.strptime(text, pattern).replace(tzinfo=dt.timezone.utc).timestamp() * 1000)
        except ValueError:
            continue
    raise SystemExit("время '%s' не понято: нужен формат 30m, 1h, мс или 2026-11-09 11:02" % value)


def parse_cell(items):
    text = " ".join(items)
    patterns = (
        r"BlockCoord\[x=(-?\d+), y=(-?\d+), z=(-?\d+)\]",
        r"\(\s*x=(-?\d+(?:\.\d+)?),\s*y=(-?\d+(?:\.\d+)?),\s*z=(-?\d+(?:\.\d+)?)\s*\)",
        r"\(?\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)?",
    )
    values = []
    if len(items) >= 3 and all(re.fullmatch(r"-?\d+", item) for item in items[:3]):
        values = [int(items[0]), int(items[1]), int(items[2])]
    else:
        for pattern in patterns:
            match = re.fullmatch(pattern, text)
            if match:
                values = [int(float(match.group(1))), int(float(match.group(2))), int(float(match.group(3)))]
                break
    if len(values) != 3:
        raise SystemExit(
            "координаты '%s' не поняты: примите (-181, 68, -518), BlockCoord[x=-181, y=68, z=-518], "
            "-181,68,-518 или (x=-180.5, y=68.0, z=-518.5)" % text
        )
    return values


def table(rows, headers, limit):
    if not rows:
        print("нет записей")
        return
    shown = rows[:limit]
    widths = [len(str(h)) for h in headers]
    for row in shown:
        for index, value in enumerate(row):
            widths[index] = min(48, max(widths[index], len(str(value))))
    line = "  ".join(str(h).ljust(widths[i]) for i, h in enumerate(headers))
    print(line)
    print("  ".join("-" * w for w in widths))
    for row in shown:
        print("  ".join(str(value)[: widths[i]].ljust(widths[i]) for i, value in enumerate(row)))
    if len(rows) > limit:
        print("... показано %d из %d; увеличьте --limit" % (limit, len(rows)))


def query(conn, sql, params=()):
    return conn.execute(sql, params).fetchall()


def describe(conn, table_name):
    return [row[1] for row in conn.execute('PRAGMA table_info("%s")' % table_name)]


def run_local(args):
    if args.db:
        db = Path(args.db)
        events = (Path(args.logs) / "events") if args.logs else None
    else:
        logs = Path(args.logs) if args.logs else (find_copy() / "logs")
        db = logs / "events.db"
        events = logs / "events"
    conn = loader.connect(db)
    loader.ensure_schema(conn)
    if events and events.is_dir():
        loader.ingest_events_dir(conn, events)
        conn.commit()
    conn.create_function("regexp", 2, lambda pattern, text: 1 if re.search(pattern, text or "") else 0)
    result = dispatch(args, conn)
    conn.close()
    return result


def dispatch(args, conn):
    limit = args.limit
    command = args.command
    if command == "cell":
        points = list(args.coords)
        radius = 0
        if len(points) >= 4 and re.fullmatch(r"\d+", points[-1]):
            radius = int(points.pop())
        elif len(points) == 2 and re.fullmatch(r"\d+", points[1]):
            radius = int(points.pop())
        x, y, z = parse_cell(points)
        rows = query(
            conn,
            "SELECT ts, type, spirit, ticket, x, y, z, summary FROM timeline "
            "WHERE x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ? "
            "ORDER BY ts DESC LIMIT ?",
            (x - radius, x + radius, z - radius, z + radius, y - radius, y + radius, limit + 1),
        )
        return "ts,type,spirit,ticket,x,y,z,summary", rows
    if command == "spirit":
        since = since_ms(args.since, since_ms("30m"))
        rows = query(
            conn,
            "SELECT ts, type, ticket, x, y, z, summary FROM timeline "
            "WHERE spirit=? AND ts>=? ORDER BY ts DESC LIMIT ?",
            (args.name, since, limit + 1),
        )
        return "ts,type,ticket,x,y,z,summary", rows
    if command == "ticket":
        rows = query(
            conn,
            "SELECT ts, type, spirit, x, y, z, summary FROM timeline WHERE ticket=? ORDER BY ts LIMIT ?",
            (args.ticket, limit + 1),
        )
        return "ts,type,spirit,x,y,z,summary", rows
    if command == "failures":
        since = since_ms(args.since, since_ms("1h"))
        rows = query(
            conn,
            "SELECT board_case, cause, reason, COUNT(*) AS n FROM task_events "
            "WHERE event='FAILED' AND ts>=? GROUP BY board_case, cause, reason ORDER BY n DESC LIMIT ?",
            (since, limit + 1),
        )
        return "board_case,cause,reason,n", rows
    if command == "blocks":
        since = since_ms(args.since, 0)
        rows = query(
            conn,
            "SELECT ts, action, block_before, block_after, x, y, z, ticket, cause, reason "
            "FROM block_changes WHERE actor_type='spirit' AND actor=? AND ts>=? ORDER BY ts DESC LIMIT ?",
            (args.spirit, since, limit + 1),
        )
        return "ts,action,block_before,block_after,x,y,z,ticket,cause,reason", rows
    if command == "deaths":
        since = since_ms(args.since, 0)
        rows = query(
            conn,
            "SELECT ts, event, spirit, x, y, z, cause, reason, killer, respawn_x, respawn_y, respawn_z "
            "FROM deaths WHERE ts>=? ORDER BY ts DESC LIMIT ?",
            (since, limit + 1),
        )
        return "ts,event,spirit,x,y,z,cause,reason,killer,respawn_x,respawn_y,respawn_z", rows
    if command == "stuck":
        since = since_ms(args.since, 0)
        rows = query(
            conn,
            "SELECT ts, spirit, event, x, y, z, helper, reason, since_tick FROM stuck "
            "WHERE ts>=? ORDER BY ts DESC LIMIT ?",
            (since, limit + 1),
        )
        return "ts,spirit,event,x,y,z,helper,reason,since_tick", rows
    if command == "items":
        since = since_ms(args.since, 0)
        rows = query(
            conn,
            "SELECT ts, spirit, action, item, count, container_x, container_y, container_z, ticket "
            "FROM item_moves WHERE item LIKE ? AND ts>=? ORDER BY ts DESC LIMIT ?",
            ("%" + args.item + "%", since, limit + 1),
        )
        return "ts,spirit,action,item,count,container_x,container_y,container_z,ticket", rows
    if command == "where":
        at = since_ms(args.at, int(dt.datetime.now(dt.timezone.utc).timestamp() * 1000))
        rows = query(
            conn,
            "SELECT ts, spirit, x, y, z, state, script, ticket, food, health FROM spirit_samples "
            "WHERE spirit=? AND ts<=? ORDER BY ts DESC LIMIT 1",
            (args.name, at),
        )
        return "ts,spirit,x,y,z,state,script,ticket,food,health", rows
    if command == "health":
        rows = query(
            conn,
            "SELECT ts, event, mspt, worst_mspt, players, source_failures, pending, active, blocked, board_ms, prune_ms, tickets "
            "FROM server_health ORDER BY ts DESC LIMIT ?",
            (limit + 1,),
        )
        return "ts,event,mspt,worst_mspt,players,source_failures,pending,active,blocked,board_ms,prune_ms,tickets", rows
    if command == "text":
        since = since_ms(args.since, 0)
        words = " ".join(args.words)
        try:
            rows = query(
                conn,
                "SELECT m.ts, m.level, m.tag, m.class, m.text FROM messages m "
                "JOIN messages_fts f ON f.rowid=m.id WHERE messages_fts MATCH ? AND m.ts>=? "
                "ORDER BY m.ts DESC LIMIT ?",
                (words, since, limit + 1),
            )
        except Exception:
            rows = query(
                conn,
                "SELECT ts, level, tag, class, text FROM messages WHERE text LIKE ? AND ts>=? "
                "ORDER BY ts DESC LIMIT ?",
                ("%" + words + "%", since, limit + 1),
            )
        return "ts,level,tag,class,text", rows
    if command == "grep":
        since = since_ms(args.since, 0)
        rows = query(
            conn,
            "SELECT ts, type, spirit, summary FROM timeline WHERE summary REGEXP ? AND ts>=? "
            "ORDER BY ts DESC LIMIT ?",
            (args.pattern, since, limit + 1),
        )
        return "ts,type,spirit,summary", rows
    if command == "sql":
        text = args.query.strip()
        if not re.match(r"(?is)^(select|with|explain|pragma)\b", text):
            raise SystemExit("sql принимает только чтение: SELECT/WITH/EXPLAIN/PRAGMA")
        if re.search(r"(?is)\b(insert|update|delete|drop|alter|create|replace|attach|detach|vacuum)\b", text):
            raise SystemExit("sql принимает только чтение: запрос меняет базу")
        conn.execute("PRAGMA query_only=ON")
        try:
            cursor = conn.execute(text)
            headers = [item[0] for item in cursor.description] if cursor.description else []
            rows = cursor.fetchmany(limit + 1)
        finally:
            conn.execute("PRAGMA query_only=OFF")
        return ",".join(headers), rows
    if command == "schema":
        version = conn.execute("SELECT value FROM meta WHERE key='schema_version'").fetchone()
        print("schema_version: %s" % (version[0] if version else "?"))
        for name in loader.TABLES:
            print("%-16s %s" % (name, ", ".join(describe(conn, name))))
            indexes = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name=? ORDER BY name", (name,)
            ).fetchall()
            print("%-16s индексы: %s" % ("", ", ".join(row[0] for row in indexes)))
        return None, None
    raise SystemExit("неизвестная команда: %s" % command)


def remote(args):
    ssh = ["ssh", "-o", "ConnectTimeout=10", SSH]
    db = MAIN_SERVER + "/logs/events.db"
    probe = subprocess.run(ssh + ["test -f " + db], capture_output=True)
    if probe.returncode != 0:
        raise SystemExit(
            "на основном сервере ещё нет базы логов (%s): этап 2 выполнит импорт; "
            "пока ищите в своей копии (scripts/dev-server.sh up)" % db
        )
    subprocess.run(ssh + ["mkdir -p " + MAIN_SCRIPTS], check=True)
    for name in ("schema.json", "loader.py", "logq.py"):
        destination = "%s/%s" % (MAIN_SCRIPTS, name)
        present = subprocess.run(ssh + ["test -f " + destination], capture_output=True)
        if present.returncode == 0:
            continue
        subprocess.run(
            ssh + ["cat > " + destination],
            input=(HERE / name).read_bytes(),
            check=True,
        )
    command = [
        "python3", MAIN_SCRIPTS + "/logq.py",
        "--db", db,
        "--logs", MAIN_SERVER + "/logs",
        "--json",
        "--limit", str(args.limit),
        args.command,
    ]
    if args.command == "cell":
        command.extend(args.coords)
    elif args.command == "spirit":
        command.extend([args.name])
        if args.since:
            command.extend(["--since", args.since])
    elif args.command == "ticket":
        command.extend([args.ticket])
    elif args.command == "failures":
        if args.since:
            command.extend(["--since", args.since])
    elif args.command == "blocks":
        command.extend(["--spirit", args.spirit])
        if args.since:
            command.extend(["--since", args.since])
    elif args.command == "items":
        command.extend([args.item])
    elif args.command == "where":
        command.extend([args.name, "--at", args.at])
    elif args.command == "grep":
        command.extend([args.pattern])
    elif args.command == "text":
        command.extend(args.words)
    elif args.command == "sql":
        command.extend([args.query])
    output = subprocess.run(ssh + [" ".join(_quote(part) for part in command)], capture_output=True, check=True)
    return output.stdout.decode("utf-8", "replace")


def _quote(text):
    return "'" + text.replace("'", "'\\''") + "'"


def build_parser():
    parser = argparse.ArgumentParser(description="Поиск по базе логов The Mine Spirit")
    parser.add_argument("--db")
    parser.add_argument("--logs")
    parser.add_argument("--main", action="store_true")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--limit", type=int, default=30)
    sub = parser.add_subparsers(dest="command", required=True)
    cell = sub.add_parser("cell")
    cell.add_argument("coords", nargs="+")
    spirit = sub.add_parser("spirit")
    spirit.add_argument("name")
    spirit.add_argument("--since")
    ticket = sub.add_parser("ticket")
    ticket.add_argument("ticket")
    failures = sub.add_parser("failures")
    failures.add_argument("--since")
    blocks = sub.add_parser("blocks")
    blocks.add_argument("--spirit", required=True)
    blocks.add_argument("--since")
    deaths = sub.add_parser("deaths")
    deaths.add_argument("--since")
    stuck = sub.add_parser("stuck")
    stuck.add_argument("--since")
    items = sub.add_parser("items")
    items.add_argument("item")
    items.add_argument("--since")
    where = sub.add_parser("where")
    where.add_argument("name")
    where.add_argument("--at", required=True)
    health = sub.add_parser("health")
    health.add_argument("--since")
    text = sub.add_parser("text")
    text.add_argument("words", nargs="+")
    text.add_argument("--since")
    grep = sub.add_parser("grep")
    grep.add_argument("pattern")
    grep.add_argument("--since")
    sql = sub.add_parser("sql")
    sql.add_argument("query")
    sub.add_parser("schema")
    return parser


def main(argv=None):
    arguments = list(argv) if argv is not None else sys.argv[1:]
    if "cell" in arguments:
        index = arguments.index("cell")
        if index + 1 < len(arguments) and arguments[index + 1].startswith("-") and not arguments[index + 1].startswith("--"):
            arguments.insert(index + 1, "--")
    args = build_parser().parse_args(arguments)
    copy = find_copy()
    use_remote = args.main or (not args.db and not args.logs and copy is None)
    if use_remote:
        output = remote(args)
        if args.json:
            print(output, end="")
        else:
            rows = json.loads(output)
            table([tuple(row.values()) for row in rows], list(rows[0].keys()) if rows else [], args.limit)
        return 0
    headers, rows = run_local(args)
    if args.json:
        keys = headers.split(",") if headers else []
        print(json.dumps([dict(zip(keys, row)) for row in rows], ensure_ascii=False, indent=2))
    elif headers is not None:
        table(rows, headers.split(","), args.limit)
    return 0


if __name__ == "__main__":
    sys.exit(main())
