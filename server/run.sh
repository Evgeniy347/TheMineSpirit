#!/usr/bin/env sh
# Forge requires a configured set of both JVM and program arguments.
# Add custom JVM arguments to the user_jvm_args.txt
# Add custom program arguments {such as nogui} to this file in the next line before the "$@" or
#  pass them to this script directly

# Отладка постоянного сервера выключена: JDWP на *:5005 держал открытый порт в сети и подозревался
# в зависании консоли. Включается осознанно: MINESPIRIT_JDWP_PORT=5005 scripts/start.sh.
JDWP=""
if [ -n "${MINESPIRIT_JDWP_PORT:-}" ]; then
    JDWP="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:${MINESPIRIT_JDWP_PORT}"
fi

# shellcheck disable=SC2086
java $JDWP @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.257/unix_args.txt "$@"
