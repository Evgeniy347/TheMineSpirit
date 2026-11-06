#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLEW="${THEMINESPIRIT_GRADLEW:-./gradlew}"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

echo "Прогрев кэша Gradle (online): $GRADLE_HOME"
"$GRADLEW" --quiet help assemble

echo "Проверка: та же сборка offline"
"$GRADLEW" --quiet --offline help assemble

echo "Кэш прогрет: scripts/publish.sh сможет собрать jar без сети"
