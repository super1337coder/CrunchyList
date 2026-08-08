#!/usr/bin/env bash
#
# CrunchyList TV — build wrapper.
#
# WHY THIS EXISTS (do not "simplify" it away):
#
# On this machine AF_UNIX connect() fails with "Invalid argument" for sockets
# created under C:\Users\...\AppData\Local\Temp, while working fine elsewhere.
# JDK 21's NIO selector (WEPollSelectorImpl -> PipeImpl) builds its wakeup pipe
# as an AF_UNIX socket pair in the temp dir, so Selector.open() throws
#
#     java.io.IOException: Unable to establish loopback connection
#
# which surfaces from Gradle as a daemon that can never be reached. Setting
# -Djava.io.tmpdir or -Djdk.nio.channels.unixdomain.tmpdir is NOT enough — the
# location is resolved before those apply. The TEMP/TMP *environment* must point
# somewhere outside AppData\Local\Temp before the JVM starts.
#
# See docs/AUDIT-2026-08.md §6 for the full diagnosis.
#
# Usage:  bash tv-app/build.sh [gradle args...]     (default: assembleDebug)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"

JVMTMP="C:\\Users\\${USERNAME:-$USER}\\.jvmtmp"
mkdir -p "/c/Users/${USERNAME:-$USER}/.jvmtmp"
export TEMP="$JVMTMP"
export TMP="$JVMTMP"

cd "$HERE"
if [ $# -eq 0 ]; then
    ./gradlew assembleDebug
else
    ./gradlew "$@"
fi
