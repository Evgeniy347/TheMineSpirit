#!/usr/bin/env bash
# lib: Проверка scripts/publish.sh на живых jar: выкладка не начинается, если jar собран без
# META-INF/neoforge.mods.toml (0 байт — «Missing ModLoader» на сервере), без modId или без классов;
# годный jar проходит до установки в поддельные сервер, пак клиента и страницу.
# Настоящие Gradle, tmux и сервер не нужны: gradlew, tmux и crontab — подделки, каталоги — временные.
cd "$(dirname "$0")/../.." || exit 2
root="$PWD"
. scripts/python-bin.sh
work="$root/TestResults/publish-check.$$"
trap 'rm -rf "$work"' EXIT
rm -rf "$work"
mkdir -p "$work/bin" "$work/jars"
failed=0

ok() { echo "OK $1"; }
fail() {
    echo "FAIL $1"
    failed=1
}

export GIT_AUTHOR_NAME="publish-check"
export GIT_AUTHOR_EMAIL="publish-check@example.invalid"
export GIT_COMMITTER_NAME="publish-check"
export GIT_COMMITTER_EMAIL="publish-check@example.invalid"

PYTHON="$(python_bin zipfile)" || {
    echo "FAIL нет рабочего python 3 для фикстур jar"
    exit 1
}

make_jar() {
    local out="$1" toml="$2" classes="$3"
    "$PYTHON" - "$out" "$toml" "$classes" <<'PY'
import sys
import zipfile

out, toml_text, classes = sys.argv[1], sys.argv[2], int(sys.argv[3])
with zipfile.ZipFile(out, 'w') as archive:
    archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
    if toml_text != '-':
        archive.writestr('META-INF/neoforge.mods.toml', toml_text)
    for index in range(classes):
        archive.writestr('com/theminespirit/Example%d.class' % index, b'\xca\xfe\xba\xbe')
PY
}

good_toml='modLoader="javafml"
loaderVersion="[1,)"
license="All Rights Reserved"
[[mods]]
modId="theminespirit"
version="0.1.9"
displayName="The Mine Spirit"'
no_id_toml='modLoader="javafml"
[[mods]]
version="0.1.9"'

good_jar="$work/jars/theminespirit-0.1.9.jar"
make_jar "$good_jar" "$good_toml" 1001
empty_jar="$work/jars/theminespirit-0.1.9-empty-toml.jar"
make_jar "$empty_jar" '' 1001
no_toml_jar="$work/jars/theminespirit-0.1.9-no-toml.jar"
make_jar "$no_toml_jar" '-' 1001
no_id_jar="$work/jars/theminespirit-0.1.9-no-id.jar"
make_jar "$no_id_jar" "$no_id_toml" 1001
border_jar="$work/jars/theminespirit-0.1.9-1000.jar"
make_jar "$border_jar" "$good_toml" 1000

out=$(bash scripts/publish.sh --verify-jar "$good_jar" 2>&1)
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "jar проверен" \
    && echo "$out" | grep -q "modId=theminespirit" && echo "$out" | grep -q "классов 1001" \
    && ok "годный jar: код=0, modId и число классов названы" \
    || fail "годный jar: код=$code, вывод: $out"

out=$(bash scripts/publish.sh --verify-jar "$empty_jar" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "пуст (0 байт)" && echo "$out" | grep -q "Missing ModLoader" \
    && ok "пустой neoforge.mods.toml (0 байт): код=1, причина «Missing ModLoader»" \
    || fail "пустой neoforge.mods.toml: код=$code, вывод: $out"

out=$(bash scripts/publish.sh --verify-jar "$no_toml_jar" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "нет META-INF/neoforge.mods.toml" \
    && ok "jar без neoforge.mods.toml: код=1, причина названа" \
    || fail "jar без neoforge.mods.toml: код=$code, вывод: $out"

out=$(bash scripts/publish.sh --verify-jar "$no_id_jar" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "не объявляет modId=theminespirit" \
    && ok "neoforge.mods.toml без modId: код=1, причина названа" \
    || fail "neoforge.mods.toml без modId: код=$code, вывод: $out"

out=$(bash scripts/publish.sh --verify-jar "$border_jar" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "классов 1000" \
    && ok "граница классов: 1000 (не больше 1000) — код=1" \
    || fail "граница классов: код=$code, вывод: $out"

out=$(bash scripts/publish.sh --verify-jar "$work/jars/нет.jar" 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "jar не найден" \
    && ok "несуществующий jar: код=1, причина названа" \
    || fail "несуществующий jar: код=$code, вывод: $out"

cat >"$work/bin/tmux" <<'FAKE'
#!/usr/bin/env bash
exit 1
FAKE
cat >"$work/bin/crontab" <<'FAKE'
#!/usr/bin/env bash
case "${1:-}" in
    -l) exit 1 ;;
    -) cat >/dev/null ;;
esac
exit 0
FAKE
chmod +x "$work/bin"/*

make_repo() {
    local repo="$1"
    mkdir -p "$repo/scripts" "$repo/config" "$repo/build"
    cp scripts/publish.sh scripts/module-paths.sh scripts/publish-status.sh scripts/python-bin.sh "$repo/scripts/"
    cp config/layout.properties "$repo/config/"
    printf 'mod_id=theminespirit\n' >"$repo/gradle.properties"
    printf "include ':mod'\n" >"$repo/settings.gradle"
    cp -a server "$repo/server"
    cat >"$repo/gradlew" <<'FAKE'
#!/usr/bin/env bash
mkdir -p build/libs
cp "${FAKE_JAR:?}" "build/libs/theminespirit-0.1.9.jar"
FAKE
    chmod +x "$repo/gradlew"
    git init -q -b main "$repo"
    git -C "$repo" add -A
    git -C "$repo" commit -qm "исходное дерево"
    git -C "$repo" checkout -q -b task/check
}

make_gateway() {
    local gateway="$1"
    mkdir -p "$gateway/users/admin/sites/the-mine-spirit/server"
    git init -q "$gateway"
    echo "gateway" >"$gateway/README.md"
    git -C "$gateway" add -A
    git -C "$gateway" commit -qm "страница «Сервер»"
}

run_publish() {
    local repo="$1" server="$2" client="$3" gateway="$4" jar="$5"
    mkdir -p "$client/pack/mods"
    printf '#!/usr/bin/env bash\nexit 0\n' >"$client/publish.sh"
    chmod +x "$client/publish.sh"
    make_gateway "$gateway"
    FAKE_JAR="$jar" PATH="$work/bin:$PATH" \
        SERVER_DIR="$server" CLIENT_DIR="$client" GATEWAY_DIR="$gateway" \
        CLEANUP_SCRIPT_DIR="$work/cleanup" HANG_WATCH_LOG="$work/hang-watch.log" \
        BUILD_LOCK="$work/build.lock" TMUX_SESSION=publish-check \
        bash "$repo/scripts/publish.sh" --temp -y 2>&1
}

repo="$work/repo-good"
make_repo "$repo"
out=$(run_publish "$repo" "$work/good/server" "$work/good/client" "$work/good/gateway" "$good_jar")
code=$?
[ "$code" = 0 ] && echo "$out" | grep -q "Временная публикация" \
    && [ -f "$work/good/server/mods/theminespirit-0.1.9.jar" ] \
    && [ -f "$work/good/client/pack/mods/theminespirit-0.1.9.jar" ] \
    && grep -q '"version": "0.1.9"' "$work/good/gateway/users/admin/sites/the-mine-spirit/server/mod.json" \
    && ok "годный jar: публикация дошла до установки, пака и страницы (код=0)" \
    || fail "годный jar: код=$code, вывод: $out"

# lib: след сборки в выводе: по нему разбирают инцидент выкладки (какой коммит, какой файл, когда собран).
echo "$out" | grep -q "Коммит: " && echo "$out" | grep -q "изменён " && echo "$out" | grep -q "sha256 " \
    && ok "годный jar: вывод называет коммит, время сборки и sha256 выбранного jar" \
    || fail "нет следа сборки (коммит/время/sha256) в выводе публикации: $out"

repo="$work/repo-bad"
make_repo "$repo"
out=$(run_publish "$repo" "$work/bad/server" "$work/bad/client" "$work/bad/gateway" "$empty_jar")
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "пуст (0 байт)" \
    && echo "$out" | grep -q "сервер не тронут" \
    && [ ! -d "$work/bad/server/scripts" ] && [ ! -d "$work/bad/server/mods" ] \
    && [ ! -e "$work/bad/client/pack/mods/theminespirit-0.1.9.jar" ] \
    && ok "пустой neoforge.mods.toml: код=1, остановка до скриптов сервера, mods/ и пака" \
    || fail "пустой neoforge.mods.toml: код=$code, вывод: $out"

mkdir -p "$work/sync/server/mods" "$work/sync/client/pack/mods"
cp "$empty_jar" "$work/sync/server/mods/theminespirit-0.1.9.jar"
echo keep >"$work/sync/client/pack/mods/keep.txt"
out=$(SERVER_DIR="$work/sync/server" CLIENT_DIR="$work/sync/client" GATEWAY_DIR="$work/sync/gateway" \
    PATH="$work/bin:$PATH" bash scripts/publish.sh --sync 2>&1)
code=$?
[ "$code" = 1 ] && echo "$out" | grep -q "негоден" \
    && [ -f "$work/sync/client/pack/mods/keep.txt" ] && [ ! -e "$work/sync/client/publish.sh" ] \
    && ok "--sync: битый jar на сервере не расходится по клиентам (код=1, пак не тронут)" \
    || fail "--sync: код=$code, вывод: $out"

exit $failed
