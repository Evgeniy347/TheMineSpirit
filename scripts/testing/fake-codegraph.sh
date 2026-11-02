#!/usr/bin/env bash
set -u
state="${FAKE_CODEGRAPH_STATE:-/dev/null}"
printf 'ARGS: %s\nGIT_DIR: %s\n' "$*" "${GIT_DIR:-}" >>"$state"
case "${1:-}" in
    init)
        path="${3:-}"
        [ -z "${FAKE_CODEGRAPH_INIT_FAIL:-}" ] || exit 1
        mkdir -p "$path/.codegraph"
        printf 'INIT' >"$path/.codegraph/codegraph.db"
        ;;
    sync)
        path="${2:-}"
        [ -z "${FAKE_CODEGRAPH_SYNC_FAIL:-}" ] || exit 1
        ;;
esac
exit 0
