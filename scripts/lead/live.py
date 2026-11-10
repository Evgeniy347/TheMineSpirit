#!/usr/bin/env python3
"""Единый взгляд на живой мир: духи, доска, падения, срез блоков, фикстура для теста.

Цель выбирается сама: своя копия дерева (run-live/*/debug.env), иначе основной сервер только на чтение.
Команды и примеры: scripts/live.sh help.
"""
import argparse
import collections
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
SSH = os.environ.get("LIVE_SSH", "domovoy@192.168.0.147")
MAIN_REST = os.environ.get("LIVE_REST", "http://192.168.0.147:8765")
MAIN_SERVER = "/opt/minecraft/server"
SLICE = os.path.join(HERE, "world-slice.py")
HELP = """scripts/live.sh <команда>  (цель: своя копия дерева, иначе основной сервер только на чтение; --main — основной)

  status                 духи, доска, падения источников, свежие сбои; с этого начинать
  spirit <имя> [строк]   один дух: состояние, его задания, журнал, срез мира вокруг
  slice <x> <y> <z> [радиус] [вверх] [вниз]   карта блоков слоями
  loops [строк]          задания, которые падают снова и снова: что и почему, за весь журнал
  log <шаблон> [строк]   строки журнала мода по регулярному выражению
  fixture <имя духа> <файл> [радиус] [--at x y z]   мир вокруг духа (или точки) в mech/movement/src/test/resources/live/<файл>.live
  check [--wait секунд] [--version <в>]  граф жив, работа есть, версия мода на сервере та же, что собрана
"""


def run_dir():
    root = os.path.join(REPO, "run-live")
    if not os.path.isdir(root):
        return None
    for name in sorted(os.listdir(root)):
        if os.path.exists(os.path.join(root, name, "debug.env")):
            return os.path.join(root, name)
    return None


class Target:
    def __init__(self, force_main):
        self.copy = None if force_main else run_dir()
        self.rest = MAIN_REST
        if self.copy:
            env = {}
            with open(os.path.join(self.copy, "debug.env"), encoding="utf-8") as f:
                for line in f:
                    if "=" in line:
                        key, value = line.strip().split("=", 1)
                        env[key] = value.strip("'\"")
            url = next((v for k, v in env.items() if "REST" in k and v.startswith("http")), None)
            port = next((v for k, v in env.items() if "REST" in k and v.isdigit()), None)
            self.rest = url or "http://127.0.0.1:%s" % port
        print("цель: %s" % ("копия " + os.path.basename(self.copy) if self.copy else "основной сервер (чтение)"))

    def get(self, path):
        with urllib.request.urlopen(self.rest + path, timeout=10) as reply:
            return json.load(reply)

    def mod_dirs(self):
        if self.copy:
            return os.path.join(self.copy, "mods"), os.path.join(self.copy, "logs")
        return MAIN_SERVER + "/mods", MAIN_SERVER + "/logs"

    def running_mod_version(self):
        _, logs = self.mod_dirs()
        script = os.path.join(REPO, "scripts", "mod-version.sh")
        if self.copy:
            out = subprocess.run(["bash", script, logs], capture_output=True, check=True).stdout
        else:
            with open(script, "rb") as source:
                out = subprocess.run(
                    ["ssh", "-o", "ConnectTimeout=8", SSH, "bash -s -- " + logs],
                    stdin=source,
                    capture_output=True,
                    check=True,
                ).stdout
        return out.decode("utf-8", "replace").strip()

    def installed_mod_version(self):
        mods, _ = self.mod_dirs()
        prefix = "theminespirit-"
        if self.copy:
            if not os.path.isdir(mods):
                return None
            jars = [
                os.path.join(mods, name)
                for name in os.listdir(mods)
                if name.startswith(prefix) and name.endswith(".jar")
            ]
            if not jars:
                return None
            newest = max(jars, key=os.path.getmtime)
            return os.path.basename(newest)[len(prefix) : -len(".jar")]
        out = subprocess.run(
            ["ssh", "-o", "ConnectTimeout=8", SSH, "ls -1t %s/%s*.jar 2>/dev/null || true" % (mods, prefix)],
            capture_output=True,
            check=True,
        ).stdout
        names = [os.path.basename(line) for line in out.decode("utf-8", "replace").splitlines() if line.strip()]
        return names[0][len(prefix) : -len(".jar")] if names else None

    def log(self, lines):
        if self.copy:
            path = os.path.join(self.copy, "logs", "latest.log")
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.readlines()[-lines:]
        else:
            out = subprocess.run(
                ["ssh", "-o", "ConnectTimeout=8", SSH, "tail -n %d %s/logs/latest.log" % (lines, MAIN_SERVER)],
                capture_output=True,
                check=True,
            ).stdout
            text = out.decode("utf-8", "replace").splitlines()
        return [mod_line(line) for line in text if "theminespirit" in line and "\tat " not in line]

    def logq(self, args):
        command = [sys.executable, os.path.join(REPO, "scripts", "logdb", "logq.py")]
        if self.copy:
            command.extend(["--logs", os.path.join(self.copy, "logs")])
        else:
            command.append("--main")
        command.extend(["--json", *args])
        try:
            output = subprocess.run(command, capture_output=True, check=True).stdout
            return json.loads(output.decode("utf-8", "replace"))
        except (OSError, subprocess.CalledProcessError, json.JSONDecodeError):
            return []

    def slice_args(self, args):
        if self.copy:
            cmd = [sys.executable, SLICE, *map(str, args), "--world", os.path.join(self.copy, "world")]
            return subprocess.run(cmd, capture_output=True, check=True).stdout.decode("utf-8", "replace")
        with open(SLICE, "rb") as script:
            out = subprocess.run(
                ["ssh", "-o", "ConnectTimeout=8", SSH, "python3 - " + " ".join(map(str, args))],
                stdin=script,
                capture_output=True,
                check=True,
            ).stdout
        return out.decode("utf-8", "replace")


def mod_line(line):
    match = re.match(r"^\[\S+ (\d\d:\d\d:\d\d)[^\]]*\] \[[^\]]*\] \[(?:com\.theminespirit\.)?(?:neoforge\.)?([^/\]]*)/?\]: ?(.*)", line)
    if match:
        return "%s %s %s" % (match.group(1), match.group(2).split(".")[-1], match.group(3))
    return line.rstrip()


def bots(target):
    return target.get("/v1/bots")["bots"]


def board(target):
    return target.get("/v1/board?limit=200")


def spirit_row(bot):
    task = bot.get("board_task") or {}
    return "  %-9s %-20s %-5s %-16s %-44s еда %s" % (
        bot["name"],
        ",".join(map(str, bot["position"])),
        bot["state"],
        bot.get("script") or "-",
        str(task.get("id") or "-")[:44],
        bot.get("food"),
    )


def status(target, _args):
    all_bots = bots(target)
    desk = board(target)
    print("тик %s  доска %s" % (all_bots[0]["tick"] if all_bots else "?", desk["groups"]))
    print(mod_versions(target))
    for bot in sorted(all_bots, key=lambda b: b["name"]):
        print(spirit_row(bot))
    lines = target.log(4000)
    falls = [line for line in lines if "источник доски" in line]
    if falls:
        print("!! ИСТОЧНИК ДОСКИ ПАДАЕТ (%d строк), последняя:\n   %s" % (len(falls), falls[-1][:260]))
    failures = collections.Counter()
    for line in lines[-1500:]:
        found = re.search(r"провалил задание доски (\S+?)[: ].*?\(кейс (\w+)\): (.{0,70})", line)
        if found:
            failures[(found.group(2), found.group(3))] += 1
    if failures:
        print("частые сбои заданий:")
        for (case, why), count in failures.most_common(6):
            print("  %3d %s: %s" % (count, case, why))
    stuck = [t for t in desk["tasks"] if "не двигается" in str(t.get("waitReason"))]
    if stuck:
        print("застряли: %s" % stuck[0]["waitReason"][:200])
    print("дальше: scripts/live.sh spirit <имя>")


def spirit(target, args):
    all_bots = bots(target)
    bot = next((b for b in all_bots if b["name"] == args.name or b["id"] == args.name), None)
    if bot is None:
        sys.exit("нет духа %s; есть: %s" % (args.name, ", ".join(sorted(b["name"] for b in all_bots))))
    print(spirit_row(bot))
    print("  инвентарь: %s" % {k.replace("minecraft:", ""): v for k, v in bot.get("inventory", {}).items()})
    for task in board(target)["tasks"]:
        if task.get("assignee") == bot["id"] and task["status"] != "DONE":
            print("  задание %s %s %s: %s" % (task["id"][:50], task["status"], task.get("effectivePriority"), str(task.get("waitReason"))[:110]))
    print("журнал:")
    rows = target.logq(["--limit", str(args.lines), "spirit", bot["name"], "--since", "1h"])
    if rows:
        for row in rows:
            print("  %s %-14s %s" % (
                stamp(row.get("ts")),
                row.get("type") or "-",
                (row.get("summary") or "")[:380],
            ))
    else:
        print("  в базе логов событий нет: загрузчик дочитает JSONL после следующего поиска")
    x, y, z = bot["position"]
    print(target.slice_args([x, y, z, 3, 3, 2]))


def slice_cmd(target, args):
    print(target.slice_args([args.x, args.y, args.z, args.radius, args.up, args.down]))


def loops(target, args):
    rows = target.logq(["--limit", str(args.lines), "failures", "--since", "30d"])
    if rows:
        total = sum(int(row.get("n") or 0) for row in rows)
        print("провалов %d за 30 дней" % total)
        for row in rows:
            print("%4s %-20s %-12s %s" % (
                row.get("n"),
                (row.get("board_case") or "-")[:20],
                (row.get("cause") or "-")[:12],
                (row.get("reason") or "")[:100],
            ))
        return
    lines = target.log(40000)
    counts = collections.Counter()
    reasons = {}
    for line in lines:
        found = re.search(r"провалил задание доски (\S+) \(кейс (\w+)\): (.{0,90})", line)
        if found:
            key = re.sub(r"[0-9a-f]{8}-[0-9a-f-]{27}|-?\d+,-?\d+(,-?\d+)?|3w5\w+", "*", found.group(1))
            counts[key] += 1
            reasons[key] = found.group(3)
    span = "%s..%s" % (lines[0][:8], lines[-1][:8]) if lines else "-"
    print("провалов %d за %s" % (sum(counts.values()), span))
    for key, count in counts.most_common(args.lines):
        print("%4d %-44s %s" % (count, key[:44], reasons[key][:90]))


def log_cmd(target, args):
    rows = target.logq(["--limit", str(args.lines), "grep", args.pattern, "--since", "24h"])
    for row in rows:
        print("%s %-12s %s" % (stamp(row.get("ts")), row.get("type") or "-", (row.get("summary") or "")[:460]))


def stamp(millis):
    if not millis:
        return "-"
    return dt.datetime.fromtimestamp(int(millis) / 1000, dt.timezone.utc).strftime("%d.%m %H:%M:%S")


def kind_of(name):
    short = name.replace("minecraft:", "")
    if short in ("air", "cave_air", "short_grass", "fern", "torch", "dandelion", "poppy"):
        return "AIR"
    if short in ("sand", "gravel", "red_sand"):
        return "FALLING"
    if short in ("dirt", "grass_block", "dirt_path", "farmland", "coarse_dirt", "podzol", "mud"):
        return "SOFT"
    if short == "water":
        return "WATER"
    if short == "lava":
        return "LAVA"
    return "HARD"


def fixture(target, args):
    if args.at:
        x, y, z = args.at
    else:
        bot = next((b for b in bots(target) if b["name"] == args.name), None)
        if bot is None:
            sys.exit("нет духа %s" % args.name)
        x, y, z = bot["position"]
    text = target.slice_args([x, y, z, args.radius, args.radius, args.radius, "--cells"])
    out = ["# %s в %d,%d,%d; снят scripts/live.sh fixture" % (args.name, x, y, z), "feet 0 0 0"]
    for line in text.splitlines():
        parts = line.split()
        if len(parts) == 4:
            out.append("%d %d %d %s" % (int(parts[0]) - x, int(parts[1]) - y, int(parts[2]) - z, kind_of(parts[3])))
    path = os.path.join(REPO, "mech/movement/src/test/resources/live", args.file + ".live")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print("%s: %d клеток; ноги в 0,0,0. Чтение в тесте: PitTestWorld.live(...)" % (os.path.relpath(path, REPO), len(out) - 2))


def mod_versions(target):
    try:
        running = target.running_mod_version()
    except (OSError, subprocess.CalledProcessError):
        running = "неизвестна"
    installed = target.installed_mod_version() or "нет"
    return "мод: загружен %s, в mods/ %s" % (running, installed)


def expected_mod_version():
    branch = subprocess.run(
        ["git", "-C", REPO, "rev-parse", "--abbrev-ref", "HEAD"], capture_output=True, check=True
    ).stdout.decode("utf-8", "replace").strip()
    if branch != "main":
        return ""
    minor = ""
    with open(os.path.join(REPO, "gradle.properties"), encoding="utf-8") as source:
        for line in source:
            if line.startswith("mod_version_minor="):
                minor = line.strip().split("=", 1)[1]
                break
    count = subprocess.run(
        ["git", "-C", REPO, "rev-list", "--count", "HEAD"], capture_output=True, check=True
    ).stdout.decode("utf-8", "replace").strip()
    return "%s.%s" % (minor, count) if minor and count else ""


def check_once(target, expected=""):
    problems = []
    all_bots = bots(target)
    desk = board(target)
    work = desk["groups"].get("ACTIVE", 0) + desk["groups"].get("AVAILABLE", 0) + desk["groups"].get("WAITING", 0)
    if work == 0:
        problems.append("доска пуста: графу нечего раздать или он падает")
    if all_bots and all(b["state"] == "idle" for b in all_bots) and work > 0:
        problems.append("все духи стоят при работе на доске")
    recent = [line for line in target.log(600) if "источник доски" in line]
    if recent:
        problems.append("источник доски падает: " + recent[-1][:220])
    try:
        running = target.running_mod_version()
    except (OSError, subprocess.CalledProcessError) as error:
        problems.append("не узнать загруженную версию мода: %s" % error)
        running = ""
    try:
        installed = target.installed_mod_version()
    except (OSError, subprocess.CalledProcessError):
        installed = None
    if not installed:
        problems.append("в mods/ нет jar мода: сервер не загрузит мод")
    elif running and running != installed:
        problems.append(
            "сервер загрузил мод %s, а в mods/ лежит %s: сборка не вступила в силу (нужен перезапуск)"
            % (running, installed)
        )
    if expected and installed and installed != expected:
        problems.append(
            "в mods/ версия %s, а собранная из этого дерева %s: выкладка не доехала до сервера"
            % (installed, expected)
        )
    return problems


def check(target, args):
    expected = args.version or expected_mod_version()
    deadline = time.time() + args.wait
    while True:
        try:
            problems = check_once(target, expected)
        except OSError as error:
            problems = ["сервер не отвечает: %s" % error]
        if not problems:
            print("ОК: граф жив, работа есть, мод %s" % (expected or "на месте"))
            return
        if time.time() >= deadline:
            print("НЕ ОК:")
            for problem in problems:
                print("  " + problem)
            sys.exit(1)
        time.sleep(15)


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("help", "-h", "--help"):
        print(HELP)
        return
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--main", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("status").set_defaults(run=status)
    one = sub.add_parser("spirit")
    one.add_argument("name")
    one.add_argument("lines", type=int, nargs="?", default=14)
    one.set_defaults(run=spirit)
    cut = sub.add_parser("slice")
    for name in ("x", "y", "z"):
        cut.add_argument(name, type=int)
    cut.add_argument("radius", type=int, nargs="?", default=4)
    cut.add_argument("up", type=int, nargs="?", default=3)
    cut.add_argument("down", type=int, nargs="?", default=2)
    cut.set_defaults(run=slice_cmd)
    loop = sub.add_parser("loops")
    loop.add_argument("lines", type=int, nargs="?", default=15)
    loop.set_defaults(run=loops)
    grep = sub.add_parser("log")
    grep.add_argument("pattern")
    grep.add_argument("lines", type=int, nargs="?", default=30)
    grep.set_defaults(run=log_cmd)
    fix = sub.add_parser("fixture")
    fix.add_argument("name")
    fix.add_argument("file")
    fix.add_argument("radius", type=int, nargs="?", default=8)
    fix.add_argument("--at", type=int, nargs=3, metavar=("X", "Y", "Z"))
    fix.set_defaults(run=fixture)
    ok = sub.add_parser("check")
    ok.add_argument("--wait", type=int, default=0)
    ok.add_argument("--version", default="")
    ok.set_defaults(run=check)
    args = parser.parse_args()
    args.run(Target(args.main), args)


if __name__ == "__main__":
    main()
