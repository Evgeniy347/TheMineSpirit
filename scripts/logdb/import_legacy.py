#!/usr/bin/env python3
"""Импорт старых текстовых логов (latest.log, *.log.gz, debug.log) в events.db.

Разбирает известные строки мода в типизированные таблицы, остальное кладёт
в messages. Отчёт по файлам — JSON, чтобы сверить число строк и событий.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import loader


def discover(paths):
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
        key = str(path.resolve())
        if key not in seen:
            seen.add(key)
            unique.append(path)
    return sorted(unique)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Импорт старых текстовых логов в SQLite")
    parser.add_argument("--db", required=True)
    parser.add_argument("--logs", nargs="+", required=True)
    parser.add_argument("--report")
    parser.add_argument("--source", default="import")
    args = parser.parse_args(argv)

    conn = loader.connect(args.db)
    loader.ensure_schema(conn)
    files = discover(args.logs)
    report = {"db": str(Path(args.db).resolve()), "version": loader.VERSION, "files": []}
    total = {"lines": 0, "typed": 0, "messages": 0, "skipped": 0}
    for path in files:
        counters = loader.ingest_text(conn, path, default_source=args.source)
        conn.commit()
        typed = sum(counters["typed"].values())
        total["lines"] += counters["lines"]
        total["typed"] += typed
        total["messages"] += counters["messages"]
        total["skipped"] += counters["skipped"]
        report["files"].append(
            {
                "file": str(path),
                "lines": counters["lines"],
                "typed": typed,
                "messages": counters["messages"],
                "skipped": counters["skipped"],
                "tables": counters["typed"],
            }
        )
        print("%-52s строк %7d  событий %7d  текстом %6d  мимо %5d"
              % (path.name[:52], counters["lines"], typed, counters["messages"], counters["skipped"]))
    report["total"] = total
    if args.report:
        Path(args.report).parent.mkdir(parents=True, exist_ok=True)
        Path(args.report).write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    conn.close()
    print("итог: строк %(lines)d, событий %(typed)d, текстом %(messages)d, мимо %(skipped)d" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
