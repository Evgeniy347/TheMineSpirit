#!/usr/bin/env bash
# Подключает git-хуки проекта из scripts/git-hooks (нужно один раз после клонирования).
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x scripts/git-hooks/*
git config core.hooksPath scripts/git-hooks
# git blame пропускает механические коммиты форматирования
git config blame.ignoreRevsFile .git-blame-ignore-revs
echo "git-хуки подключены: $(git config core.hooksPath)"
