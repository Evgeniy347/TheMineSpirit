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

MAIN = "src/main/java"
TEST = "src/test/java"
DEFAULT_FILES = ("config/filesize-baseline.txt", "config/comment-baseline.txt")


def layout_roots(root: Path) -> list[str]:
    """Корни исходников из config/layout.properties: корень, api и модули."""
    package_root = "com/theminespirit"
    lines = (root / "config/layout.properties").read_text(encoding="utf-8").splitlines()
    values: dict[str, str] = {}
    for line in lines:
        text = line.strip()
        if not text or text.startswith("#") or "=" not in text:
            continue
        key, value = text.split("=", 1)
        values[key.strip()] = value.strip()
    package_root = values.get("package_root", package_root)
    extra: list[str] = []
    for key in ("module_roots", "module_test_roots"):
        extra.extend(part.strip() for part in values.get(key, "").split(",") if part.strip())
    return [f"{values.get('main', MAIN)}/{package_root}", f"{values.get('test', TEST)}/{package_root}"] + [
        f"{item}/{package_root}" for item in extra
    ]


def logical(path: str, prefixes: list[str]) -> str:
    for prefix in prefixes:
        if path.startswith(prefix + "/"):
            return path[len(prefix) + 1 :]
    return path


def convert(file: Path, check: bool, prefixes: list[str]) -> int:
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
        new = logical(old, prefixes)
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
    prefixes = layout_roots(root)
    changed = 0
    for name in args.files:
        file = root / name
        if not file.is_file():
            print(f"нет файла {file}", file=sys.stderr)
            return 2
        changed += convert(file, args.check, prefixes)
    action = "физических ключей" if args.check else "ключей переписано"
    print(f"{action}: {changed}")
    return 1 if args.check and changed else 0


if __name__ == "__main__":
    sys.exit(main())
