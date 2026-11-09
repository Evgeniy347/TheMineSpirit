#!/usr/bin/env python3
"""Импорт старых текстовых логов (latest.log, *.log.gz, debug.log) в events.db.

Разбирает известные строки мода в типизированные таблицы, остальное кладёт
в messages. Отчёт по файлам — JSON: строки, события и что не разобралось.
Файл удаляется только по --delete-verified и только если сошлись строки,
события в базе и не было ошибок разбора.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import loader


def discover(paths, exclude=()):
    excluded = {name.lower() for name in exclude}
    found = []
    for raw in paths:
        path = Path(raw)
        if path.is_dir():
            found.extend(sorted(path.glob("*.log")))
            found.extend(sorted(path.glob("*.log.gz")))
        elif path.exists():
            found.append(path)
        else:
            raise SystemExit("файл или каталог логов не найден: %s" % raw)
    seen = set()
    unique = []
    for path in found:
        if path.name.lower() in excluded:
            continue
        key = str(path.resolve())
        if key not in seen:
            seen.add(key)
            unique.append(path)
    return sorted(unique)


def db_rows(conn, key):
    rows = conn.execute(
        "SELECT table_name, min_id, max_id FROM ingest_ids WHERE path=?", (key,)
    ).fetchall()
    return sum(max_id - min_id + 1 for _, min_id, max_id in rows)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Импорт старых текстовых логов в SQLite")
    parser.add_argument("--db", required=True)
    parser.add_argument("--logs", nargs="+", required=True)
    parser.add_argument("--report")
    parser.add_argument("--source", default="import")
    parser.add_argument("--exclude", nargs="*", default=[], help="имена файлов, которые не импортировать")
    parser.add_argument("--levels", help="оставить только уровни через запятую (например, DEBUG,TRACE)")
    parser.add_argument(
        "--delete-verified", action="store_true",
        help="удалить *.log.gz, у которых строки и события в базе сошлись",
    )
    args = parser.parse_args(argv)

    levels = None
    if args.levels:
        levels = {item.strip().upper() for item in args.levels.split(",") if item.strip()}
    conn = loader.connect(args.db)
    loader.ensure_schema(conn)
    files = discover(args.logs, args.exclude)
    report = {"db": str(Path(args.db).resolve()), "version": loader.VERSION, "files": []}
    total = {
        "files": 0, "lines": 0, "typed": 0, "messages": 0, "errors": 0,
        "merged": 0, "stored": 0, "skipped": 0, "tail": 0,
    }
    unverified = 0
    for path in files:
        key = str(path.resolve())
        before = loader._state(conn, key)
        counters = loader.ingest_text(
            conn, path, default_source=args.source, levels=levels
        )
        conn.commit()
        rows = db_rows(conn, key)
        typed = sum(counters["typed"].values())
        fresh = before is None or before[1] == 0
        verified = (
            fresh
            and counters["tail"] == 0
            and counters["errors"] == 0
            and counters["lines"] == counters["stored"] + counters["merged"] + counters["skipped"]
            and rows == counters["stored"]
        )
        deleted = False
        if args.delete_verified and verified and path.suffix == ".gz":
            path.unlink()
            deleted = True
        if not verified:
            unverified += 1
        total["files"] += 1
        total["lines"] += counters["lines"]
        total["typed"] += typed
        total["messages"] += counters["messages"]
        total["errors"] += counters["errors"]
        total["merged"] += counters["merged"]
        total["stored"] += counters["stored"]
        total["skipped"] += counters["skipped"]
        total["tail"] += counters["tail"]
        report["files"].append(
            {
                "file": path.name,
                "path": str(path),
                "lines": counters["lines"],
                "tail": counters["tail"],
                "typed": typed,
                "tables": counters["typed"],
                "messages": counters["messages"],
                "errors": counters["errors"],
                "merged": counters["merged"],
                "stored": counters["stored"],
                "db_rows": rows,
                "skipped": counters["skipped"],
                "skipped_by_logger": counters["skipped_by_logger"],
                "skipped_samples": counters["skipped_samples"],
                "error_samples": counters["error_samples"],
                "verified": verified,
                "deleted": deleted,
            }
        )
        print(
            "%-30s строк %7d  событий %7d  текстом %6d  мимо %6d  слито %3d  база %7d  %s%s"
            % (
                path.name[:30], counters["lines"], typed, counters["messages"],
                counters["skipped"], counters["merged"], rows,
                "сверено" if verified else "НЕ СВЕРЕНО",
                "  удалён" if deleted else "",
            )
        )
    report["total"] = total
    if args.report:
        Path(args.report).parent.mkdir(parents=True, exist_ok=True)
        Path(args.report).write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    conn.close()
    print(
        "итог: файлов %(files)d, строк %(lines)d, событий %(typed)d, текстом %(messages)d, "
        "слито %(merged)d, мимо %(skipped)d, ошибок %(errors)d, хвост %(tail)d" % total
    )
    if unverified:
        print("НЕ СВЕРЕНО файлов: %d — они не удалены" % unverified)
    return 1 if unverified else 0


if __name__ == "__main__":
    sys.exit(main())
