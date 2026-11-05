#!/usr/bin/env python3
"""Перевод baseline-файлов сторожей на логические пути (шаг 2 переезда на модули).

Логический путь — путь относительно корня пакета com/theminespirit. Он не зависит
от того, лежит ли файл в src/main/java, src/test/java или в каталоге Gradle-модуля.
Скрипт меняет только ключи строк (до табуляции), лимиты и текст причин не трогает.

    python tools/modules/logical_paths.py            # переписать config/*-baseline.txt
    python tools/modules/logical_paths.py --check    # показать, остались ли физические пути
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

MAIN = "src/main/java/com/theminespirit/"
TEST = "src/test/java/com/theminespirit/"
MECH_PREFIXES = (
    "mech/mining/src/main/java/com/theminespirit/",
    "mech/mining/src/test/java/com/theminespirit/",
    "mech/movement/src/main/java/com/theminespirit/",
    "mech/movement/src/test/java/com/theminespirit/",
    "mech/inventory/src/main/java/com/theminespirit/",
    "mech/inventory/src/test/java/com/theminespirit/",
)
DEFAULT_FILES = ("config/filesize-baseline.txt", "config/comment-baseline.txt")


def logical(path: str) -> str:
    for prefix in (MAIN, TEST, *MECH_PREFIXES):
        if path.startswith(prefix):
            return path[len(prefix):]
    return path


def convert(file: Path, check: bool) -> int:
    lines = file.read_text(encoding="utf-8").splitlines()
    changed = 0
    result = []
    for line in lines:
        if not line.strip():
            result.append(line)
            continue
        separator = line.find("\t")
        if separator < 0:
            result.append(line)
            continue
        old = line[:separator]
        new = logical(old)
        if new != old:
            changed += 1
            if check:
                print(f"{file}: {old} → {new}")
        result.append(new + line[separator:])
    if not check:
        with file.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(result) + ("\n" if lines else ""))
    return changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="только показать физические ключи, не менять файлы")
    parser.add_argument("--root", default=".", help="корень репозитория")
    parser.add_argument("files", nargs="*", default=list(DEFAULT_FILES), help="baseline-файлы")
    args = parser.parse_args(argv)
    root = Path(args.root)
    changed = 0
    for name in args.files:
        file = root / name
        if not file.is_file():
            print(f"нет файла {file}", file=sys.stderr)
            return 2
        changed += convert(file, args.check)
    action = "физических ключей" if args.check else "ключей переписано"
    print(f"{action}: {changed}")
    return 1 if args.check and changed else 0


if __name__ == "__main__":
    sys.exit(main())
