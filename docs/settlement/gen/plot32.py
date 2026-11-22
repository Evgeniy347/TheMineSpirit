#!/usr/bin/env python3
"""Центрирует схемы уровней в коробке участка из town.json.

Схема получает `size: ШxВxД` по участку и `plot: ШxД`; содержимое сдвигается
на (left, front), недостающие строки справа/сзади — ANY (пробелы), вход
сдвигается на те же величины. Запуск: `python docs/settlement/gen/plot32.py`
(правка файлов) или `--check` (только проверка, код возврата 1 при расхождении).
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SCHEMES = ROOT / "mod/src/main/resources/data/theminespirit/schematics"
TOWN = ROOT / "mod/src/main/resources/data/theminespirit/board/town.json"
BUILDINGS = SCHEMES / "buildings.json"

SIZE_RE = re.compile(r"^size:\s*(\d+)\s*[x×]\s*(\d+)\s*[x×]\s*(\d+)\s*(#.*)?$")
PLOT_RE = re.compile(r"^plot:\s*\d+\s*[x×]\s*\d+\s*(#.*)?$")
ENTRANCE_RE = re.compile(r"^entrance:\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(.*)$")
LAYER_RE = re.compile(r"^(\s*)- y(\d+)(?:\s+[^:|]*)?:\s*\|\s*$")

EXEMPT = {"mine"}


def stripped(name: str) -> str:
    candidate = name
    cut = candidate.rfind("_")
    while cut > 0:
        candidate = candidate[:cut]
        yield candidate
        cut = candidate.rfind("_")
    yield name


def catalog_id(name: str, known: dict) -> str | None:
    if name in known:
        return name
    for candidate in stripped(name):
        if candidate in known:
            return candidate
    return None


def load() -> tuple[dict, dict]:
    town = json.loads(TOWN.read_text(encoding="utf-8"))
    buildings = json.loads(BUILDINGS.read_text(encoding="utf-8"))
    known = {item["id"]: item for item in buildings["buildings"]}
    return town, known


def plots(town: dict, known: dict) -> dict[str, tuple[int, int, list[str]]]:
    """catalog id -> (width, depth, [lot ids])."""
    result: dict[str, tuple[int, int, list[str]]] = {}
    for entry in town["buildings"]:
        lot = entry["id"]
        if lot in EXEMPT:
            continue
        catalog = catalog_id(lot, known)
        if catalog is None:
            continue
        size = (entry["width"], entry["depth"])
        if catalog in result and result[catalog][:2] != size:
            raise SystemExit(
                f"общий каталог '{catalog}' у лотов {result[catalog][2]} и '{lot}' "
                f"имеет разные участки {result[catalog][:2]} и {size}"
            )
        lots = result.get(catalog, (size[0], size[1], []))[2]
        result[catalog] = (size[0], size[1], lots + [lot])
    return result


def pad_layer(lines: list[str], index: int, left: int, front: int, depth: int) -> tuple[list[str], int]:
    header = lines[index]
    match = LAYER_RE.match(header)
    if match is None:
        raise SystemExit(f"ожидается заголовок слоя, а встретилось: {header!r}")
    dash_indent = len(match.group(1))
    row_indent = dash_indent + 4
    index += 1
    rows: list[str] = []
    while index < len(lines) and not LAYER_RE.match(lines[index]):
        raw = lines[index]
        if raw.strip() and len(raw) - len(raw.lstrip()) < row_indent:
            break
        rows.append(raw[row_indent:] if len(raw) > row_indent else "")
        index += 1
    while rows and not rows[-1].strip():
        rows.pop()
    if not rows:
        raise SystemExit(f"слой y{match.group(2)} пуст: паддинг нечего сдвигать")
    indent = " " * row_indent
    out = [header] + [""] * front
    for row in rows:
        out.append(indent + " " * left + row)
    return out, index


def transform(text: str, width: int, depth: int, origin: str) -> str:
    lines = text.splitlines()
    size_index = None
    old_width = old_depth = None
    for index, line in enumerate(lines):
        match = SIZE_RE.match(line)
        if match:
            size_index = index
            old_width, old_depth = int(match.group(1)), int(match.group(3))
            break
    if size_index is None:
        raise SystemExit(f"{origin}: нет строки size:")
    if old_width > width or old_depth > depth:
        raise SystemExit(
            f"{origin}: схема {old_width}×{old_depth} больше участка {width}×{depth}: сначала ужмите постройку"
        )
    left = (width - old_width) // 2
    front = (depth - old_depth) // 2
    match = SIZE_RE.match(lines[size_index])
    comment = match.group(4) or ""
    tail = f"  {comment}" if comment else ""
    lines[size_index] = f"size: {width}x{match.group(2)}x{depth}{tail}".rstrip()
    plot_line = f"plot: {width}x{depth}"
    if size_index + 1 < len(lines) and PLOT_RE.match(lines[size_index + 1]):
        lines[size_index + 1] = plot_line
    else:
        lines.insert(size_index + 1, plot_line)
        size_index += 1
    for index, line in enumerate(lines):
        if line.startswith("layers:"):
            break
    else:
        raise SystemExit(f"{origin}: нет раздела layers:")
    out = lines[: index + 1]
    index += 1
    while index < len(lines):
        if LAYER_RE.match(lines[index]):
            part, index = pad_layer(lines, index, left, front, depth)
            out.extend(part)
        else:
            out.append(lines[index])
            index += 1
    for index, line in enumerate(out):
        entrance = ENTRANCE_RE.match(line)
        if entrance:
            out[index] = (
                f"entrance: {int(entrance.group(1)) + left},{entrance.group(2)},{int(entrance.group(3)) + front}"
                + entrance.group(4)
            )
            break
    return "\n".join(out) + "\n"


def main() -> int:
    check = "--check" in sys.argv[1:]
    town, known = load()
    mapping = plots(town, known)
    changed: list[str] = []
    problems: list[str] = []
    for catalog, (width, depth, lots) in sorted(mapping.items()):
        for level in known[catalog]["levels"]:
            scheme = level["scheme"]
            path = SCHEMES / f"{scheme}.txt"
            if not path.exists():
                problems.append(f"{catalog}: схемы {scheme} нет в {path}")
                continue
            before = path.read_text(encoding="utf-8")
            after = transform(before, width, depth, f"{catalog}/{scheme}")
            if after == before:
                continue
            changed.append(f"{scheme}: {width}×{depth} ({','.join(lots)})")
            if not check:
                path.write_text(after, encoding="utf-8", newline="\n")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    if check and changed:
        print("не приведены к участкам:\n" + "\n".join(changed), file=sys.stderr)
        return 1
    print(f"схем к правке: {len(changed)}" + ("" if check else " — записаны"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
