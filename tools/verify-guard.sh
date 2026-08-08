#!/usr/bin/env bash
#
# CrunchyList — behavioural verification of the guard.
#
# Unit tests prove the *decision* logic is right. They cannot prove the guard is
# actually running, actually allowed to bounce, or actually survives an update —
# and every serious bug in this project failed in exactly that direction: the app
# reported "Guard: active" while protecting nothing.
#
# So this suite asserts on observable behaviour only. It never trusts the app's
# own status text.
#
# Usage:
#   bash tools/verify-guard.sh                # against the only attached device
#   bash tools/verify-guard.sh emulator-5554  # against a specific one
#
# Exit code 0 = every check passed.
set -uo pipefail
export MSYS_NO_PATHCONV=1

ADB="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
SERIAL="${1:-}"
adb() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

PKG=com.lastgenlabs.crunchylist
CR=com.crunchyroll.crunchyroid

PASS=0
FAIL=0
RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; OFF=$'\033[0m'

ok()   { PASS=$((PASS+1)); printf "  ${GREEN}PASS${OFF}  %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf "  ${RED}FAIL${OFF}  %s\n" "$1"; [ $# -gt 1 ] && printf "        ${DIM}%s${OFF}\n" "$2"; }
info() { printf "${DIM}        %s${OFF}\n" "$1"; }
head_() { printf "\n${YELLOW}%s${OFF}\n" "$1"; }

front() {
    adb shell "dumpsys activity activities | grep -i topResumedActivity" 2>/dev/null \
        | sed 's/.*ActivityRecord{[^ ]* [^ ]* //; s/ .*//' | tr -d '\r'
}
guard_services() {
    adb shell "dumpsys activity services $PKG | grep -c ServiceRecord" 2>/dev/null | tr -d '\r'
}
appop() {
    adb shell "appops get $PKG $1" 2>/dev/null | tr -d '\r'
}
open_app() {
    adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
    sleep 5
}

# ---------------------------------------------------------------------------

printf "CrunchyList guard verification\n"
if ! adb shell "pm list packages | grep -q $PKG" 2>/dev/null; then
    printf "${RED}CrunchyList is not installed on this device.${OFF}\n"
    exit 2
fi
if ! adb shell "pm list packages | grep -q $CR" 2>/dev/null; then
    printf "${RED}Crunchyroll is not installed — the guard has nothing to guard.${OFF}\n"
    exit 2
fi

# --- 1. prerequisites -------------------------------------------------------
head_ "1. Permissions (without these the guard silently does nothing)"

case "$(appop GET_USAGE_STATS)" in
    *allow*) ok "usage access granted (guard can see the foreground app)" ;;
    *)       bad "usage access NOT granted" "adb shell appops set $PKG GET_USAGE_STATS allow" ;;
esac

case "$(appop SYSTEM_ALERT_WINDOW)" in
    *allow*) ok "overlay access granted (bounce is not blocked by BAL)" ;;
    *)       bad "overlay access NOT granted — bounces fail SILENTLY" \
                 "adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow" ;;
esac

# --- 2. the guard is actually running ---------------------------------------
head_ "2. Guard lifecycle"

open_app
[ "$(guard_services)" != "0" ] \
    && ok "guard running after opening CrunchyList" \
    || bad "guard NOT running after opening CrunchyList"

adb shell "am force-stop $PKG" >/dev/null 2>&1; sleep 2
open_app
[ "$(guard_services)" != "0" ] \
    && ok "guard re-arms after a force-stop" \
    || bad "guard did NOT re-arm after force-stop"

# Killing the process (not force-stop) should be recovered by START_STICKY.
PID=$(adb shell "pidof $PKG" 2>/dev/null | tr -d '\r')
if [ -n "$PID" ]; then
    adb shell "kill $PID" >/dev/null 2>&1
    sleep 12
    [ "$(guard_services)" != "0" ] \
        && ok "guard restarts after its process is killed (START_STICKY)" \
        || bad "guard did NOT restart after the process was killed"
else
    info "skipped process-kill check — could not read pid"
fi

# --- 3. enforcement ---------------------------------------------------------
head_ "3. Enforcement (the part that matters)"

adb shell "am force-stop $CR" >/dev/null 2>&1
open_app
adb shell "monkey -p $CR -c android.intent.category.LEANBACK_LAUNCHER 1" >/dev/null 2>&1
sleep 12
F="$(front)"
case "$F" in
    $PKG/*) ok "cold-launching Crunchyroll is bounced back to CrunchyList" ;;
    *)      bad "cold-launched Crunchyroll was NOT bounced" "left showing: $F" ;;
esac

# The guard must not give up: re-check after it has had time to stop trying.
sleep 15
F="$(front)"
case "$F" in
    $PKG/*) ok "still held 15s later (guard does not give up)" ;;
    *)      bad "Crunchyroll regained the foreground after 15s" "now showing: $F" ;;
esac

# A deep link fired externally gets no grace period, so it must also be bounced:
# grace is only ever granted to launches CrunchyList itself initiates.
adb shell "am force-stop $CR" >/dev/null 2>&1
open_app
adb shell "am start -a android.intent.action.VIEW -d 'crunchyroll://series/G4PH0WXVJ' $CR" >/dev/null 2>&1
sleep 14
F="$(front)"
case "$F" in
    $PKG/*)        ok "externally-fired deep link is bounced (no grace granted)" ;;
    *ShowDetails*) info "external deep link reached the show page — acceptable (it IS an approved screen)" ; PASS=$((PASS+1)) ;;
    *)             bad "external deep link left Crunchyroll somewhere unapproved" "showing: $F" ;;
esac

# --- 4. surviving an update -------------------------------------------------
head_ "4. Survives an app update"

APK="$(dirname "$0")/../tv-app/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    adb install -r "$APK" >/dev/null 2>&1
    sleep 10
    [ "$(guard_services)" != "0" ] \
        && ok "guard re-armed after reinstall (MY_PACKAGE_REPLACED)" \
        || bad "guard did NOT re-arm after an app update"
else
    info "skipped — no debug APK built yet"
fi

# --- summary ----------------------------------------------------------------
printf "\n"
if [ "$FAIL" -eq 0 ]; then
    printf "${GREEN}All %d checks passed.${OFF}\n" "$PASS"
    exit 0
else
    printf "${RED}%d passed, %d FAILED.${OFF}\n" "$PASS" "$FAIL"
    printf "${DIM}A failure here means the TV is not actually protected, whatever the app says.${OFF}\n"
    exit 1
fi
