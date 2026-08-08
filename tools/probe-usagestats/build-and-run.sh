#!/usr/bin/env bash
# CrunchyList — build, install and run the UsageStats guard probe.
#
# Deliberately Gradle-free: this probe has zero dependencies (no AndroidX,
# no Kotlin), so it builds straight from the SDK build-tools in a few seconds
# instead of pulling ~1GB of Gradle + AndroidX to answer one question.
#
# Usage:  bash tools/probe-usagestats/build-and-run.sh
set -euo pipefail
# NOTE: do NOT set MSYS_NO_PATHCONV globally here. Git Bash's path conversion is
# needed so POSIX paths reach the Windows tools correctly; it is only harmful for
# adb arguments that must stay literal (component specs, /sdcard paths), which are
# handled with an inline MSYS_NO_PATHCONV=1 below.

SDK="${LOCALAPPDATA}/Android/Sdk"
BT="${SDK}/build-tools/36.1.0"
PLATFORM="${SDK}/platforms/android-36.1/android.jar"
JBR="/c/Program Files/Android/Android Studio/jbr/bin"
ADB="${SDK}/platform-tools/adb.exe"

# d8.bat / apksigner.bat resolve java via JAVA_HOME. Must be a *Windows* path.
export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${HERE}/build"
PKG="com.lastgenlabs.clprobe"
# Keystore lives OUTSIDE build/ — build/ is wiped each run, and regenerating the
# key every time makes every rebuild signature-incompatible with the installed app
# (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
KS="${HERE}/debug.keystore"

rm -rf "${OUT}"
mkdir -p "${OUT}/classes" "${OUT}/dex"

echo "==> 1/6 compile java (javac 21, target 21 for d8)"
"${JBR}/javac.exe" -source 21 -target 21 -nowarn \
    -cp "${PLATFORM}" \
    -d "${OUT}/classes" \
    "${HERE}/src/com/lastgenlabs/clprobe/ProbeActivity.java"

echo "==> 2/6 dex (d8)"
"${BT}/d8.bat" --lib "${PLATFORM}" --min-api 29 \
    --output "${OUT}/dex" \
    "${OUT}/classes/com/lastgenlabs/clprobe/"*.class

echo "==> 3/6 link resources (aapt2) -> base apk"
"${BT}/aapt2.exe" link \
    -o "${OUT}/base.apk" \
    -I "${PLATFORM}" \
    --manifest "${HERE}/AndroidManifest.xml" \
    --min-sdk-version 29 \
    --target-sdk-version 36

echo "==> 4/6 add classes.dex to apk"
( cd "${OUT}/dex" && "${JBR}/jar.exe" uf "${OUT}/base.apk" classes.dex )

echo "==> 5/6 sign"
if [ ! -f "${KS}" ]; then
  "${JBR}/keytool.exe" -genkeypair -keystore "${KS}" -storepass android \
      -keypass android -alias probe -keyalg RSA -keysize 2048 -validity 3650 \
      -dname "CN=CrunchyList Probe, OU=dev, O=LastGenLabs, C=US" >/dev/null 2>&1
fi
"${BT}/zipalign.exe" -f 4 "${OUT}/base.apk" "${OUT}/probe.apk"
"${BT}/apksigner.bat" sign --ks "${KS}" --ks-pass pass:android \
    --key-pass pass:android "${OUT}/probe.apk"

echo "==> 6/6 install + grant usage access + launch"
if ! "${ADB}" install -r "${OUT}/probe.apk" 2>&1 | tee /dev/stderr | grep -q "Success"; then
  echo "    (install failed — uninstalling and retrying)"
  MSYS_NO_PATHCONV=1 "${ADB}" uninstall "${PKG}" >/dev/null 2>&1 || true
  "${ADB}" install "${OUT}/probe.apk"
fi
# PACKAGE_USAGE_STATS is an appop, not a runtime permission — install flags won't cover it.
MSYS_NO_PATHCONV=1 "${ADB}" shell appops set "${PKG}" GET_USAGE_STATS allow
# BAL exemption — without this, startActivity() from background is silently blocked.
MSYS_NO_PATHCONV=1 "${ADB}" shell appops set "${PKG}" SYSTEM_ALERT_WINDOW allow
MSYS_NO_PATHCONV=1 "${ADB}" shell am start -n "${PKG}/.ProbeActivity"

echo
echo "probe running. tail its output with:"
echo "  \"${ADB}\" logcat -s CLPROBE"
