#!/usr/bin/env bash
# lib: какие Gradle-модули затронуты правкой: путь → модуль (settings.gradle) → модули, зависящие от него.
# Зовёт scripts/test.sh unit --changed; проверка — scripts/testing/check-changed-modules.sh.
#
# Использование:
#   scripts/changed-modules.sh [--tasks] [--all] [путь…]
# Без путей берёт изменения рабочего дерева и индекса (git diff + git ls-files --others).
# --tasks  печатать задачи Gradle (:board:test, :test) вместо модулей (:board, :root)
# --all    печатать все модули — проверка допустимого имени для --module
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/module-paths.sh

MODE="modules"
PRINT_ALL=0
PATHS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --tasks) MODE="tasks" ;;
        --modules) MODE="modules" ;;
        --all) PRINT_ALL=1 ;;
        --)
            shift
            PATHS+=("$@")
            break
            ;;
        -h|--help)
            sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        -*)
            echo "changed-modules: неизвестный аргумент: $1" >&2
            exit 2
            ;;
        *) PATHS+=("$1") ;;
    esac
    shift
done

raw_modules() {
    sed -nE "s/^[[:space:]]*include[[:space:]]+'(:[^']+)'.*/\1/p" "$MOD_SETTINGS_FILE"
}

all_modules() {
    printf '%s\n' "${MODULE_LIST[@]}"
}

print_module() {
    if [ "$MODE" = "tasks" ]; then
        if [ "$1" = ":root" ]; then
            printf ':test\n'
        else
            printf '%s:test\n' "$1"
        fi
    else
        printf '%s\n' "$1"
    fi
}

if [ "$PRINT_ALL" = 1 ]; then
    print_module ":root"
    while IFS= read -r module; do
        [ -n "$module" ] && print_module "$module"
    done < <(raw_modules)
    exit 0
fi

if [ ${#PATHS[@]} -eq 0 ]; then
    # lib: индекс и рабочее дерево: хук видит временный индекс, но тесты всё равно читают рабочее дерево,
    # поэтому берём оба и незакоммиченные новые файлы — лишнее лучше пропущенного.
    mapfile -t PATHS < <(
        {
            git diff --name-only
            git diff --cached --name-only
            git ls-files --others --exclude-standard
        } 2>/dev/null | sed '/^$/d' | sort -u
    )
fi

declare -A SELECTED=()
select_module() { SELECTED["$1"]=1; }

# lib: docs/, tools/, scripts/ и прочее вне сборки на тесты не влияют.
map_path() {
    local path="$1" module dir
    path="${path#./}"
    path="${path//\\//}"
    case "$path" in
        ''|docs/*|tools/*|scripts/*|TestResults/*|server/*|agents/*|.github/*|.idea/*|build/*|.gradle/*)
            return 0
            ;;
    esac
    case "$path" in *.java) select_module ":guards" ;; esac
    case "$path" in
        config/filesize-baseline.txt|config/comment-baseline.txt)
            # lib: списки исключений сторожа размера и комментариев читает только :guards — полный прогон не нужен.
            select_module ":guards"
            return 0
            ;;
    esac
    case "$path" in
        config/*|build-logic/*|gradle/*|gradle.properties|settings.gradle|build.gradle|gradlew|gradlew.bat)
            select_module ":root"
            while IFS= read -r module; do
                [ -n "$module" ] && select_module "$module"
            done < <(all_modules)
            return 0
            ;;
        src/test/resources/*)
            # lib: общие фикстуры: их читают тесты и корня, и всех модулей.
            select_module ":root"
            while IFS= read -r module; do
                [ -n "$module" ] && select_module "$module"
            done < <(all_modules)
            return 0
            ;;
        src/*)
            select_module ":root"
            return 0
            ;;
        mod/src/main/resources/*)
            # lib: ресурсы мода лежат на тестовом classpath каждого модуля (build-logic).
            select_module ":root"
            while IFS= read -r module; do
                [ -n "$module" ] && select_module "$module"
            done < <(all_modules)
            return 0
            ;;
        test-fixtures/*)
            # lib: фикстуры читают тесты всех модулей, а боевых рёбер к ним нет — считаем правку общей.
            select_module ":root"
            while IFS= read -r module; do
                [ -n "$module" ] && select_module "$module"
            done < <(all_modules)
            return 0
            ;;
    esac
    while IFS= read -r module; do
        [ -n "$module" ] || continue
        dir="${module#:}"
        dir="${dir//:/\/}"
        case "$path" in
            "$dir"/*)
                select_module "$module"
                return 0
                ;;
        esac
    done < <(all_modules)
    return 0
}

declare -A DEPS=()
declare -a MODULE_LIST=()
# lib: обратный граф — по боевым зависимостям (api/implementation/compileOnly).
# testImplementation/testFixturesImplementation не тянем: они связывают почти все модули через
# :test-fixtures и :mod, и --changed превращается в полный прогон. Полный unit в финале/слиянии
# ловит поломку тестовых зависимостей; хук — быстрый страж боевого графа.
collect_deps() {
    local module="$1" file dependency
    if [ "$module" = ":root" ]; then
        file="build.gradle"
    else
        file="${module#:}"
        file="${file//:/\/}/build.gradle"
    fi
    [ -f "$file" ] || return 0
    while IFS= read -r dependency; do
        [ -n "$dependency" ] && DEPS["$module"]="${DEPS[$module]:-} $dependency"
    done < <(grep -vE 'testImplementation|testCompileOnly|testRuntimeOnly|testFixtures' "$file" \
        | grep -oE "project\('[^']+'\)" | sed -E "s/project\('([^']+)'\)/\1/")
    # lib: build-logic — theminespirit.module сам даёт api project(':api').
    if [ "$module" != ":root" ] && grep -q "id 'theminespirit.module'" "$file" 2>/dev/null; then
        DEPS["$module"]="${DEPS[$module]:-} :api"
    fi
}
# lib: граф модулей (список + рёбра) кэшируется: на Windows sed/grep на каждый запуск заметно дороже
# чтения готового файла. Кэш перестраивается, когда старше settings.gradle, корневого build.gradle,
# build-logic или build.gradle любого модуля.
GRAPH_CACHE="build/tmp/changed-modules-graph.cache"
CACHE_VERSION="1"

cache_valid() {
    [ -f "$GRAPH_CACHE" ] || return 1
    [ "$GRAPH_CACHE" -nt "$MOD_SETTINGS_FILE" ] || return 1
    [ "$GRAPH_CACHE" -nt build.gradle ] || return 1
    [ "$GRAPH_CACHE" -nt build-logic/src/main/groovy/theminespirit.module.gradle ] || return 1
    [ "$GRAPH_CACHE" -nt build-logic/src/main/groovy/theminespirit.java-conventions.gradle ] || return 1
    local module file
    for module in "${MODULE_LIST[@]}"; do
        file="${module#:}"
        file="${file//:/\/}/build.gradle"
        [ -f "$file" ] || return 1
        [ "$GRAPH_CACHE" -nt "$file" ] || return 1
    done
    return 0
}

read_graph_cache() {
    local line module rest
    while IFS= read -r line; do
        case "$line" in
            "MODULE "*)
                module="${line#MODULE }"
                [ -n "$module" ] && MODULE_LIST+=("$module")
                ;;
            "DEP "*)
                read -r module rest <<<"${line#DEP }"
                DEPS["$module"]=" $rest"
                ;;
        esac
    done <"$GRAPH_CACHE"
}

build_graph_cache() {
    MODULE_LIST=()
    DEPS=()
    local module tmp
    while IFS= read -r module; do
        [ -n "$module" ] && MODULE_LIST+=("$module")
    done < <(raw_modules)
    collect_deps ":root"
    for module in "${MODULE_LIST[@]}"; do
        collect_deps "$module"
    done
    mkdir -p "$(dirname "$GRAPH_CACHE")"
    tmp="$GRAPH_CACHE.tmp.$$"
    {
        printf '# v%s\n' "$CACHE_VERSION"
        for module in "${MODULE_LIST[@]}"; do
            printf 'MODULE %s\n' "$module"
        done
        for module in ":root" "${MODULE_LIST[@]}"; do
            [ -n "${DEPS[$module]:-}" ] && printf 'DEP %s%s\n' "$module" "${DEPS[$module]}"
        done
    } >"$tmp"
    mv "$tmp" "$GRAPH_CACHE"
}

[ -f "$GRAPH_CACHE" ] && read_graph_cache
if ! cache_valid; then
    build_graph_cache
    MODULE_LIST=()
    DEPS=()
    read_graph_cache
fi

for path in "${PATHS[@]}"; do
    map_path "$path"
done

# lib: обратный граф транзитивно: если изменился X, перезапускаем тесты всех, кто от X зависит.
changed=1
while [ "$changed" = 1 ]; do
    changed=0
    for module in ":root" $(all_modules); do
        [ "${SELECTED[$module]:-}" = 1 ] && continue
        for dependency in ${DEPS[$module]:-}; do
            if [ "${SELECTED[$dependency]:-}" = 1 ]; then
                SELECTED["$module"]=1
                changed=1
                break
            fi
        done
    done
done

[ "${SELECTED[:root]:-}" = 1 ] && print_module ":root"
while IFS= read -r module; do
    [ -n "$module" ] && [ "${SELECTED[$module]:-}" = 1 ] && print_module "$module"
done < <(all_modules)
exit 0
