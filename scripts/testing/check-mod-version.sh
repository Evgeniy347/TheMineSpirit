#!/usr/bin/env bash
# lib: проверка scripts/mod-version.sh на живых строках debug.log NeoForge и на этом дереве: имя jar в mods/
# lib: не доказывает, какую сборку загрузил сервер (10.11 крутился 0.1.3454 при новой сборке на диске).
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
work="$root/TestResults/mod-version-check.$$"
trap 'rm -rf "$work"' EXIT
rm -rf "$work"
mkdir -p "$work/logs"
failed=0

ok() { echo "OK $1"; }
fail() {
    echo "FAIL $1"
    failed=1
}

cat >"$work/logs/debug.log" <<'EOF'
[09Nov2026 22:45:03.186] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader/SCAN]: Found META-INF/neoforge.mods.toml mod of type null: /opt/minecraft/server/mods/theminespirit-0.1.3454.jar
[09Nov2026 22:45:03.191] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.ModFileInfo/LOADING]: Found valid mod file theminespirit-0.1.3454.jar with {theminespirit} mods - versions {0.1.3454}
EOF
got="$(bash scripts/mod-version.sh "$work/logs" 2>/dev/null || true)"
if [ "$got" = "0.1.3454" ]; then
    ok "загруженная версия из строки NeoForge: $got"
else
    fail "загруженная версия: ждали 0.1.3454, получили '$got'"
fi

cat >"$work/logs/debug.log" <<'EOF'
[09Nov2026 22:45:03.191] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.ModFileInfo/LOADING]: Found valid mod file theminespirit-0.1.3454.jar with {theminespirit} mods - versions {0.1.3454}
[10Nov2026 02:45:03.191] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.ModFileInfo/LOADING]: Found valid mod file theminespirit-0.1.3480.jar with {theminespirit} mods - versions {0.1.3480}
EOF
got="$(bash scripts/mod-version.sh "$work/logs" 2>/dev/null || true)"
if [ "$got" = "0.1.3480" ]; then
    ok "после перезапуска берётся последняя загрузка: $got"
else
    fail "после перезапуска: ждали 0.1.3480, получили '$got'"
fi

printf '%s\n' '[10Nov2026 02:45:03.191] [main/INFO] [net.neoforged.fml.loading.moddiscovery.ModDiscoverer/SCAN]: Found mod file "theminespirit-0.1.3480.jar"' >"$work/logs/debug.log"
if bash scripts/mod-version.sh "$work/logs" >/dev/null 2>"$work/err"; then
    fail "без строки «versions {…}» скрипт обязан сказать, что версию не узнать"
elif grep -q "versions" "$work/err"; then
    ok "без записи о загрузке версия не выдумывается, причина названа"
else
    fail "без записи о загрузке нет причины в stderr: $(cat "$work/err")"
fi

if bash scripts/mod-version.sh "$work/нет-такого" >/dev/null 2>&1; then
    fail "нет debug.log — версия не должна печататься"
else
    ok "нет debug.log — код 1"
fi

minor="$(sed -n 's/^mod_version_minor=//p' gradle.properties | head -n 1)"
commits="$(git rev-list --count HEAD)"
got="$(bash scripts/mod-version.sh --built . 2>/dev/null || true)"
if [ "$got" = "$minor.$commits" ]; then
    ok "собранная версия дерева: $got"
else
    fail "собранная версия: ждали $minor.$commits, получили '$got'"
fi

exit "$failed"
