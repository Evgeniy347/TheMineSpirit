#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x scripts/git-hooks/*
git config core.hooksPath scripts/git-hooks
git config blame.ignoreRevsFile .git-blame-ignore-revs
echo "git-хуки подключены: $(git config core.hooksPath)"
