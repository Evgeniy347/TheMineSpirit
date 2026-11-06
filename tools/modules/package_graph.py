#!/usr/bin/env python3
"""Граф зависимостей пакетов The Mine Spirit по исходникам.

Зачем: решение о разбивке проекта на Gradle-модули требует фактов — кто от кого
зависит, где циклы, где механика знает о доске/графе, кто тянет NeoForge.
Скрипт читает .java, достаёт package/import и упоминания классов, строит рёбра
пакет → пакет, ищет циклы (SCC) и печатает срезы.

Запуск (из корня репозитория):
    python tools/modules/package_graph.py summary
    python tools/modules/package_graph.py cycles --depth 2
    python tools/modules/package_graph.py mutual --depth 2
    python tools/modules/package_graph.py backrefs --into core.board
    python tools/modules/package_graph.py evidence --from core.mining --to core.board
    python tools/modules/package_graph.py neoforge
    python tools/modules/package_graph.py tests
    python tools/modules/package_graph.py matrix --depth 2 --csv build/tmp/m.csv
    python tools/modules/package_graph.py json --out build/tmp/graph.json
    python tools/modules/package_graph.py selftest

Правда о точности: import'ы — факт; упоминания классов ловят только те связи,
что не требуют import (внутрипакетные и через wildcard-import), плюс
полностью квалифицированные имена. Одноимённый вложенный тип не создаёт ребро
(его имя объявлено в том же пакете). Рёбер «по строке» (reflection, данные
в JSON) скрипт не видит — оговорено в docs/research/modules.md.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT_PACKAGE = "com.theminespirit"


def _layout_extra(key: str) -> str:
    path = Path("config/layout.properties")
    if not path.is_file():
        return ""
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if text.startswith(key + "="):
            return text[len(key) + 1 :]
    return ""


DEFAULT_MAIN = ",".join(["src/main/java", "api/src/main/java"] + [p for p in _layout_extra("module_roots").split(",") if p])
DEFAULT_TEST = ",".join(
    ["src/test/java", "api/src/test/java"] + [p for p in _layout_extra("module_test_roots").split(",") if p]
)

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w.*]+)\s*;", re.MULTILINE)
CLASS_RE = re.compile(
    r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\s+)*"
    r"(?:class|interface|enum|record|@interface)\s+(\w+)",
    re.MULTILINE,
)
IDENT_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\b")
TEST_ANNOTATION_RE = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b")
GAME_IMPORTS = ("net.minecraft", "net.neoforged", "com.mojang")


@dataclass
class SourceFile:
    """Один .java: пакет, объявления, импорты и упоминания простых имён классов."""

    path: str
    package: str
    imports: list[str] = field(default_factory=list)
    wildcards: list[str] = field(default_factory=list)
    classes: list[str] = field(default_factory=list)
    identifiers: set[str] = field(default_factory=set)
    fqns: set[str] = field(default_factory=set)
    tests: int = 0


def strip_noise(text: str) -> str:
    """Убирает комментарии — иначе примеры импортов в доках дают ложные рёбра."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", text)


def parse_file(path: Path, rel: str, count_tests: bool = False) -> SourceFile | None:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None
    body = strip_noise(text)
    m = PACKAGE_RE.search(body)
    if not m:
        return None
    sf = SourceFile(path=rel, package=m.group(1))
    for _, name in IMPORT_RE.findall(body):
        if name.endswith(".*"):
            sf.wildcards.append(name[:-2])
        else:
            sf.imports.append(name)
    sf.classes = CLASS_RE.findall(body)
    without_imports = IMPORT_RE.sub("", body)
    sf.identifiers = set(IDENT_RE.findall(without_imports))
    sf.fqns = set(re.findall(r"\b(com\.theminespirit(?:\.[a-z0-9_]+)*\.[A-Z]\w*)", without_imports))
    if count_tests:
        sf.tests = len(TEST_ANNOTATION_RE.findall(without_imports))
    return sf


def scan(root: Path, count_tests: bool = False) -> list[SourceFile]:
    files: list[SourceFile] = []
    if not root.is_dir():
        return files
    for path in root.rglob("*.java"):
        sf = parse_file(path, path.as_posix(), count_tests=count_tests)
        if sf is not None:
            files.append(sf)
    return files


def package_of_class(fqcn: str) -> str:
    return fqcn.rsplit(".", 1)[0]


def declared_names(files: list[SourceFile]) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    """Простое имя → полные имена классов, и пакет → имена, объявленные внутри него."""
    by_simple: dict[str, set[str]] = defaultdict(set)
    local_names: dict[str, set[str]] = defaultdict(set)
    for sf in files:
        for cls in sf.classes:
            by_simple[cls].add(sf.package + "." + cls)
            local_names[sf.package].add(cls)
    return by_simple, local_names


def build_edges(files: list[SourceFile]) -> tuple[dict[tuple[str, str], int], list[str]]:
    """Рёбра пакет → пакет: импорты + упоминания без импорта. Возвращает (рёбра, предупреждения)."""
    by_simple, local_names = declared_names(files)
    edges: dict[tuple[str, str], int] = defaultdict(int)
    warnings: list[str] = []
    ambiguous = {name for name, owners in by_simple.items() if len(owners) > 1}

    for sf in files:
        targets: set[str] = set()
        for imp in sf.imports:
            targets.add(package_of_class(imp))
        targets.update(sf.wildcards)
        own = {sf.package + "." + cls for cls in sf.classes}
        for fq in sf.fqns:
            if fq not in own:
                targets.add(package_of_class(fq))
        for ident in sf.identifiers:
            if ident in local_names[sf.package]:
                continue
            owners = by_simple.get(ident)
            if owners and len(owners) == 1:
                targets.add(package_of_class(next(iter(owners))))
            elif owners and ident in ambiguous:
                warnings.append(f"{sf.path}: имя {ident} есть в {len(owners)} пакетах — связь не учтена")
        targets.discard(sf.package)
        for target in targets:
            edges[(sf.package, target)] += 1
    return edges, warnings


def group(pkg: str, depth: int) -> str:
    """com.theminespirit.core.board.graph при depth=2 → core.board."""
    relative = pkg[len(ROOT_PACKAGE) + 1:] if pkg.startswith(ROOT_PACKAGE + ".") else pkg
    parts = relative.split(".")
    return ".".join(parts[:depth]) if depth > 0 else relative


def full(pkg: str) -> str:
    """Короткое имя пакета (core.board) → полное (com.theminespirit.core.board)."""
    return pkg if pkg.startswith(ROOT_PACKAGE) else ROOT_PACKAGE + "." + pkg


def aggregate(edges: dict[tuple[str, str], int], depth: int) -> dict[tuple[str, str], int]:
    agg: dict[tuple[str, str], int] = defaultdict(int)
    for (src, dst), count in edges.items():
        a, b = group(src, depth), group(dst, depth)
        if a != b:
            agg[(a, b)] += count
    return agg


def strongly_connected(nodes: set[str], edges: dict[tuple[str, str], int]) -> list[list[str]]:
    """Тарьян: компоненты сильной связности размером > 1 (они и есть циклы)."""
    adj: dict[str, set[str]] = defaultdict(set)
    for (src, dst) in edges:
        if src in nodes and dst in nodes:
            adj[src].add(dst)
    index: dict[str, int] = {}
    low: dict[str, int] = {}
    stack: list[str] = []
    on_stack: set[str] = set()
    result: list[list[str]] = []
    counter = [0]

    def visit(v: str) -> None:
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        on_stack.add(v)
        for w in sorted(adj.get(v, ())):
            if w not in index:
                visit(w)
                low[v] = min(low[v], low[w])
            elif w in on_stack:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            component = []
            while True:
                w = stack.pop()
                on_stack.discard(w)
                component.append(w)
                if w == v:
                    break
            if len(component) > 1:
                result.append(sorted(component))

    for node in sorted(nodes):
        if node not in index:
            visit(node)
    return sorted(result, key=lambda c: (-len(c), c))


def file_counts(files: list[SourceFile], depth: int) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for sf in files:
        counts[group(sf.package, depth)] += 1
    return counts


def scan_roots(value: str, count_tests: bool = False) -> list[SourceFile]:
    """Несколько корней через запятую: после переезда исходники живут и в модулях."""
    files: list[SourceFile] = []
    for part in value.split(","):
        part = part.strip()
        if part:
            files.extend(scan(Path(part), count_tests=count_tests))
    return files


def load(args: argparse.Namespace) -> tuple[list[SourceFile], dict[tuple[str, str], int], list[str]]:
    files = scan_roots(args.main)
    edges, warnings = build_edges(files)
    return files, edges, warnings


def cmd_summary(args: argparse.Namespace) -> int:
    files, edges, warnings = load(args)
    counts = file_counts(files, args.depth)
    outgoing: dict[str, int] = defaultdict(int)
    incoming: dict[str, int] = defaultdict(int)
    for (src, dst), count in edges.items():
        outgoing[group(src, args.depth)] += count
        incoming[group(dst, args.depth)] += count
    print(f"пакетов: {len(counts)}, файлов: {len(files)}, рёбер (полный пакет): {len(edges)}, "
          f"срез depth={args.depth}")
    print(f"{'пакет':40} {'файлов':>7} {'исходящих':>10} {'входящих':>9}")
    for pkg in sorted(counts, key=lambda p: (-counts[p], p)):
        print(f"{pkg:40} {counts[pkg]:7} {outgoing.get(pkg, 0):10} {incoming.get(pkg, 0):9}")
    if warnings:
        print(f"\nпредупреждений о неоднозначных именах: {len(warnings)} (примеры)")
        for line in warnings[:5]:
            print("  " + line)
    return 0


def cmd_cycles(args: argparse.Namespace) -> int:
    _, edges, _ = load(args)
    agg = aggregate(edges, args.depth)
    nodes = {group(src, args.depth) for src, _ in edges} | {group(dst, args.depth) for _, dst in edges}
    cycles = strongly_connected(nodes, agg)
    if not cycles:
        print("циклов нет")
        return 0
    print(f"циклов (SCC > 1) на depth={args.depth}: {len(cycles)}")
    for component in cycles:
        print(f"\n[{len(component)}] {' <-> '.join(component)}")
        for src in component:
            for dst in component:
                if src != dst and (src, dst) in agg:
                    print(f"  {src} -> {dst}: {agg[(src, dst)]} упоминаний")
    return 0


def cmd_backrefs(args: argparse.Namespace) -> int:
    """Кто извне зависит от пакета — и обратные рёбра «механика → доска/граф»."""
    files, edges, _ = load(args)
    targets = set(args.into)

    def visible(dst: str) -> bool:
        return args.all or dst.startswith(ROOT_PACKAGE)

    print(f"срез depth={args.depth}, цели: {', '.join(sorted(targets))}")
    for target in sorted(targets):
        dependents: dict[str, int] = defaultdict(int)
        dependencies: dict[str, int] = defaultdict(int)
        for (src, dst), count in edges.items():
            if group(dst, args.depth) == target and group(src, args.depth) != target:
                dependents[group(src, args.depth)] += count
            if group(src, args.depth) == target and group(dst, args.depth) != target and visible(dst):
                dependencies[group(dst, args.depth)] += count
        print(f"\n{target}: зависят (depth {args.depth})")
        for name, count in sorted(dependents.items(), key=lambda kv: (-kv[1], kv[0])):
            print(f"  {name:35} {count:5}")
        print(f"{target}: цель зависит от")
        for name, count in sorted(dependencies.items(), key=lambda kv: (-kv[1], kv[0])):
            print(f"  -> {name:32} {count:5}")
    return 0


def cmd_neoforge(args: argparse.Namespace) -> int:
    """Кто из com.theminespirit тянет net.minecraft/net.neoforged/com.mojang."""
    files, _, _ = load(args)
    rows = [sf for sf in files if any(imp.startswith(GAME_IMPORTS) for imp in sf.imports)]
    by_pkg: dict[str, int] = defaultdict(int)
    for sf in rows:
        by_pkg[group(sf.package, args.depth)] += 1
    print(f"файлов с импортом игры: {len(rows)} из {len(files)}")
    for pkg in sorted(by_pkg, key=lambda p: (-by_pkg[p], p)):
        print(f"  {pkg:40} {by_pkg[pkg]:5}")
    print("\nфайлы вне com.theminespirit.neoforge (склейка):")
    for sf in rows:
        if not sf.package.startswith("com.theminespirit.neoforge"):
            print("  " + sf.path)
    return 0


def cmd_tests(args: argparse.Namespace) -> int:
    files = scan_roots(args.test, count_tests=True)
    by_pkg: dict[str, tuple[int, int]] = defaultdict(lambda: (0, 0))
    for sf in files:
        key = group(sf.package, args.depth)
        f, t = by_pkg[key]
        by_pkg[key] = (f + 1, t + sf.tests)
    total_t = sum(t for _, t in by_pkg.values())
    print(f"тестовых файлов: {len(files)}, методов-тестов: {total_t}, depth={args.depth}")
    print(f"{'пакет тестов':40} {'файлов':>7} {'тестов':>7}")
    for pkg in sorted(by_pkg, key=lambda p: (-by_pkg[p][1], p)):
        print(f"{pkg:40} {by_pkg[pkg][0]:7} {by_pkg[pkg][1]:7}")
    return 0


def cmd_matrix(args: argparse.Namespace) -> int:
    _, edges, _ = load(args)
    agg = aggregate(edges, args.depth)
    nodes = sorted({group(src, args.depth) for src, _ in edges} |
                   {group(dst, args.depth) for _, dst in edges})
    if args.csv:
        out = Path(args.csv)
        with out.open("w", encoding="utf-8", newline="") as fh:
            fh.write("from\\to;" + ";".join(nodes) + "\n")
            for src in nodes:
                fh.write(src + ";" + ";".join(str(agg.get((src, dst), 0)) for dst in nodes) + "\n")
        print(f"матрица {len(nodes)}x{len(nodes)} записана в {out}")
        return 0
    print(f"матрица {len(nodes)}x{len(nodes)} (depth={args.depth}); используйте --csv для файла")
    for src in nodes:
        row = ", ".join(f"{dst}:{agg[(src, dst)]}" for dst in nodes if agg.get((src, dst)))
        print(f"{src}: {row or '—'}")
    return 0


def cmd_json(args: argparse.Namespace) -> int:
    files, edges, warnings = load(args)
    agg = aggregate(edges, args.depth)
    payload = {
        "files": len(files),
        "packages": dict(sorted(file_counts(files, args.depth).items())),
        "edges": [
            {"from": src, "to": dst, "count": count}
            for (src, dst), count in sorted(agg.items(), key=lambda kv: (-kv[1], kv[0]))
        ],
        "warnings": warnings,
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
        print(f"JSON записан в {args.out}")
    else:
        print(text)
    return 0


def cmd_evidence(args: argparse.Namespace) -> int:
    """Файлы и символы, из-за которых есть ребро from → to (для разбора «что мешает»)."""
    files, _, _ = load(args)
    src_group = group(full(args.src), args.depth)
    dst_group = group(full(args.dst), args.depth)
    print(f"ребро {src_group} -> {dst_group} (depth {args.depth})")
    by_simple, local_names = declared_names(files)
    shown = 0
    for sf in files:
        if group(sf.package, args.depth) != src_group or src_group == dst_group:
            continue
        symbols = {imp for imp in sf.imports if group(package_of_class(imp), args.depth) == dst_group}
        symbols |= {fq for fq in sf.fqns if group(package_of_class(fq), args.depth) == dst_group}
        symbols |= {
            ident for ident in sf.identifiers
            if ident not in local_names[sf.package]
            and len(by_simple.get(ident, ())) == 1
            and group(package_of_class(next(iter(by_simple[ident]))), args.depth) == dst_group
        }
        if symbols:
            print(f"{sf.path}: {', '.join(sorted(symbols))}")
            shown += 1
            if args.limit and shown >= args.limit:
                print(f"... ещё не показано (лимит {args.limit})")
                return 0
    print(f"файлов с ребром: {shown}")
    return 0


def cmd_mutual(args: argparse.Namespace) -> int:
    """Взаимные рёбра A→B и B→A: это и есть минимальные циклы, которые мешают модулям."""
    _, edges, _ = load(args)
    agg = aggregate(edges, args.depth)
    pairs = []
    seen = set()
    for (src, dst), count in agg.items():
        if src == dst or (src, dst) in seen:
            continue
        back = agg.get((dst, src), 0)
        if back:
            seen.add((src, dst))
            seen.add((dst, src))
            pairs.append((count + back, src, dst, count, back))
    print(f"взаимных пар на depth={args.depth}: {len(pairs)}")
    for total, a, b, ab, ba in sorted(pairs, reverse=True):
        if args.min and total < args.min:
            continue
        print(f"  {a} <-> {b}: {ab} / {ba}")
    return 0


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def cmd_selftest(args: argparse.Namespace) -> int:
    """Проверка анализатора на синтетических файлах: рёбра, циклы, счёт тестов, срез, ложные связи."""
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        write(root / "com/theminespirit/a/X.java",
              "package com.theminespirit.a;\nimport com.theminespirit.b.Y;\n"
              "public class X { // import com.theminespirit.c.Z;\n Y y; }\n")
        write(root / "com/theminespirit/b/Y.java",
              "package com.theminespirit.b;\npublic class Y { com.theminespirit.a.X x; }\n")
        files = scan(root)
        assert len(files) == 2, f"ожидалось 2 файла, получено {len(files)}"
        edges, _ = build_edges(files)
        assert edges.get(("com.theminespirit.a", "com.theminespirit.b")) == 1, "импорт X→Y не найден"
        assert edges.get(("com.theminespirit.b", "com.theminespirit.a")) == 1, "FQN Y→X не найден"
        assert not any(dst.endswith("c") for (_, dst) in edges), "комментарий дал ложное ребро"
        write(root / "com/theminespirit/c/Link.java",
              "package com.theminespirit.c;\npublic record Link(String a, String b) {}\n")
        write(root / "com/theminespirit/a/W.java",
              "package com.theminespirit.a;\nclass W {\n    private record Link(int x) {}\n    Link l;\n}\n")
        edges, _ = build_edges(scan(root))
        assert edges.get(("com.theminespirit.a", "com.theminespirit.c")) is None, \
            "вложенный одноимённый тип дал ложное ребро на чужой пакет"
        agg = aggregate(edges, 2)
        cycles = strongly_connected({"a", "b"}, agg)
        assert cycles == [["a", "b"]], f"цикл не найден: {cycles}"
        write(root / "com/theminespirit/a/XTest.java",
              "package com.theminespirit.a;\nclass XTest { @Test void a(){} @ParameterizedTest void b(){} }\n")
        tests = scan(root, count_tests=True)
        assert sum(sf.tests for sf in tests) == 2, "счёт тестов неверен"
        assert group("com.theminespirit.core.board.graph", 2) == "core.board", "срез пакета неверен"
        assert group("com.theminespirit.core.board.graph", 3) == "core.board.graph", "срез depth=3 неверен"
        assert full("core.board") == "com.theminespirit.core.board", "короткое имя не развёрнуто"
    print("selftest: ok")
    return 0


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=[
        "summary", "cycles", "mutual", "backrefs", "evidence", "neoforge", "tests", "matrix", "json",
        "selftest"])
    parser.add_argument("--depth", type=int, default=2, help="глубина пакета от com.theminespirit (2 = core.board)")
    parser.add_argument("--main", default=DEFAULT_MAIN, help="корни main-исходников через запятую")
    parser.add_argument("--test", default=DEFAULT_TEST, help="корни тестовых исходников через запятую")
    parser.add_argument("--into", action="append", default=[], help="целевые пакеты для backrefs (можно повторять)")
    parser.add_argument("--from", dest="src", default="", help="источник ребра для evidence")
    parser.add_argument("--to", dest="dst", default="", help="цель ребра для evidence")
    parser.add_argument("--limit", type=int, default=0, help="сколько файлов печатать в evidence (0 = все)")
    parser.add_argument("--min", type=int, default=0, help="порог веса пары для mutual")
    parser.add_argument("--all", action="store_true", help="показывать и внешние пакеты (java.*, Jackson)")
    parser.add_argument("--csv", help="куда писать матрицу")
    parser.add_argument("--out", help="куда писать JSON")
    args = parser.parse_args(argv)
    if args.command == "backrefs" and not args.into:
        parser.error("backrefs требует --into <пакет>")
    if args.command == "evidence" and not (args.src and args.dst):
        parser.error("evidence требует --from <пакет> и --to <пакет>")
    handlers = {
        "summary": cmd_summary, "cycles": cmd_cycles, "mutual": cmd_mutual, "backrefs": cmd_backrefs,
        "evidence": cmd_evidence, "neoforge": cmd_neoforge, "tests": cmd_tests,
        "matrix": cmd_matrix, "json": cmd_json, "selftest": cmd_selftest,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
