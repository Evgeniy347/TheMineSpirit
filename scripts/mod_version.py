#!/usr/bin/env python3
"""Версия мода: загруженная сервером (по debug.log NeoForge) и собранная из дерева.

Один разбор на все входы: scripts/mod-version.sh (обёртка), scripts/lead/live.py (копия и сервер)
и проверка scripts/testing/check-mod-version.sh. Поэтому запуск не требует bash: там, где `bash`
оказывается WSL (а пути Windows ему чужие), версия всё равно читается.
"""
import os
import subprocess
import sys

DEBUG_SEARCH_LINES = 4000
MARKER = "Found valid mod file theminespirit-"


def running(logs):
    """Версия, которую загрузил сервер: последняя запись NeoForge в начале debug.log."""
    debug = os.path.join(logs, "debug.log")
    if not os.path.isfile(debug):
        raise SystemExit("нет %s: по нему не узнать, какую сборку загрузил сервер" % debug)
    found = None
    with open(debug, "rb") as stream:
        for number, raw in enumerate(stream):
            if number >= DEBUG_SEARCH_LINES:
                break
            line = raw.decode("utf-8", "replace")
            if MARKER in line:
                found = line
    if found is None:
        raise SystemExit(
            "в %s нет записи «Found valid mod file theminespirit-… versions {…}»: мод не загрузился" % debug
        )
    start = found.find("versions {")
    if start < 0:
        raise SystemExit("в %s последняя запись о моде без версии (versions {…}): мод не загрузился" % debug)
    rest = found[start + len("versions {") :]
    end = rest.find("}")
    if end < 0:
        raise SystemExit("в %s запись о моде с незакрытым versions {…}: версию не прочитать" % debug)
    version = rest[:end].strip()
    if not version:
        raise SystemExit("в %s запись о моде с пустым versions {}: версию не прочитать" % debug)
    return version


def built(tree):
    """Версия, которую даст сборка дерева: mod_version_minor и число коммитов."""
    properties = os.path.join(tree, "gradle.properties")
    if not os.path.isfile(properties):
        raise SystemExit("нет %s: не из чего взять mod_version_minor" % properties)
    minor = ""
    with open(properties, encoding="utf-8") as stream:
        for line in stream:
            if line.startswith("mod_version_minor="):
                minor = line.strip().split("=", 1)[1]
                break
    try:
        count = subprocess.run(
            ["git", "-C", tree, "rev-list", "--count", "HEAD"],
            capture_output=True,
            check=True,
        ).stdout.decode("utf-8", "replace").strip()
    except (OSError, subprocess.CalledProcessError):
        count = ""
    if not minor or not count:
        raise SystemExit(
            "не определить версию сборки: mod_version_minor='%s', коммитов='%s'" % (minor, count)
        )
    return "%s.%s" % (minor, count)


def main(argv):
    if argv and argv[0] in ("-h", "--help"):
        usage()
        return 0
    if not argv:
        usage()
        return 2
    if argv[0] == "--built" and len(argv) >= 2:
        print(built(argv[1]))
        return 0
    if argv[0].startswith("-"):
        print("mod-version.sh: непонятный аргумент %s" % argv[0], file=sys.stderr)
        return 2
    print(running(argv[0]))
    return 0


def usage():
    print(
        "Использование:\n"
        "  mod-version.sh <каталог логов>    версия мода, которую загрузил сервер (debug.log NeoForge)\n"
        "  mod-version.sh --built <дерево>   версия, которую даст сборка из этого дерева\n"
        "\n"
        "Печатает одну строку — версию. Код 1 — версию не определить, причина в stderr.",
        file=sys.stderr if len(sys.argv) <= 1 else sys.stdout,
    )


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
