#!/usr/bin/env bash
# Build the debug APK, install it over the existing app (data kept), make the Dictus keyboard
# the default IME, launch the app and wait until it is usable.
#   launch.sh [--no-build]
# Requires the Pixel lock held by THIS session (lock.sh acquire): refuses with another session's lock.
# Last line on success: READY serial=… run=… pid=… version=… apk_sha=…
set -euo pipefail
source "$(dirname "$0")/env.sh"
build=1; [ "${1:-}" = "--no-build" ] && build=0

if ! lock_is_mine; then
  if [ -d "$DICTUS_LOCK" ]; then echo "error: the Pixel lock is held by another session [$(lock_owner_line)]: wait for it (helpers/lock.sh acquire)"
  else echo "error: take the Pixel lock first (helpers/lock.sh acquire)"; fi
  exit 1
fi
adbs get-state >/dev/null 2>&1 || { echo "error: $DICTUS_SERIAL not connected (adb devices)"; exit 1; }
if pixel_locked; then
  echo "error: Pixel is locked. Do not unlock it: run cleanup.sh (releases the lock) and report."; exit 1
fi

run_id="$(date +%Y%m%d-%H%M%S)-$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
run="$DICTUS_EVIDENCE_ROOT/$run_id"
mkdir -p "$run" "$DICTUS_SESSION_DIR"
# The run belongs to this session's lock token: cleanup.sh and evidence.sh refuse any other run.
echo "TOKEN=$(session_token)" > "$run/run.env"
echo "$(session_token) $run" > "$DICTUS_SESSION_PTR"
report "$run" "launch commit=$(git -C "$REPO_ROOT" rev-parse HEAD) branch=$(git -C "$REPO_ROOT" branch --show-current)"

if [ "$build" = 1 ]; then
  (cd "$REPO_ROOT" && git submodule update --init >/dev/null && ./gradlew assembleDebug) > "$run/build.log" 2>&1 \
    || { echo "error: build failed, see $run/build.log"; exit 1; }
fi
[ -f "$DICTUS_APK" ] || { echo "error: no APK at $DICTUS_APK"; exit 1; }
apk_sha="$(sha256sum "$DICTUS_APK" | cut -c1-16)"

# -r keeps the user's data (models, settings, notes). Never uninstall.
adbs install -r "$DICTUS_APK" > "$run/install.log" 2>&1 || { echo "error: install failed, see $run/install.log"; exit 1; }
report "$run" "installed apk_sha=$apk_sha"

# Settings snapshot, compared by cleanup.sh: a run must leave Pierre's settings as it found them.
# Only key names and value hashes are kept (prefs-before.digest), never the values themselves.
prefs_tmp="$(pull_prefs)"
[ -s "$prefs_tmp" ] && prefs_tool digest "$prefs_tmp" > "$run/prefs-before.digest"
rm -f "$prefs_tmp"

# Default IME: remember the previous one so cleanup.sh can put it back.
prev_ime="$(adbs shell settings get secure default_input_method | tr -d '\r')"
echo "PREV_IME=$prev_ime" >> "$run/run.env"
if [ "$prev_ime" != "$DICTUS_IME" ]; then
  adbs shell ime enable "$DICTUS_IME" >/dev/null
  adbs shell ime set "$DICTUS_IME" >/dev/null
  report "$run" "ime switched from $prev_ime to $DICTUS_IME"
fi

adbs logcat -c || true
adbs shell am start -W -n "$DICTUS_ACTIVITY" > "$run/am-start.log"
# Ready = process alive AND MainActivity resumed, held for 3 s (an app that crashes on start fails here).
pid=""
for i in $(seq 1 20); do
  pid="$(adbs shell pidof "$DICTUS_PKG" | tr -d '\r' || true)"   # pidof exits 1 while the app starts
  if [ -n "$pid" ] && adbs shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep -q "$DICTUS_ACTIVITY"; then
    sleep 3
    [ "$(adbs shell pidof "$DICTUS_PKG" | tr -d '\r' || true)" = "$pid" ] && break
  fi
  pid=""; sleep 1
done
[ -n "$pid" ] || { echo "error: Dictus not resumed after 20 s (see $run/am-start.log)"; exit 1; }

version="$(adbs shell dumpsys package "$DICTUS_PKG" | grep -m1 versionName | sed 's/.*=//' | tr -d '\r')"
{ echo "SERIAL=$DICTUS_SERIAL"; echo "PID=$pid"; echo "APP_UID=$(adbs shell pm list packages -U "$DICTUS_PKG" | sed -n 's/.*uid://p' | tr -d '\r' | head -1)"; echo "APK_SHA=$apk_sha"; echo "VERSION=$version"; } >> "$run/run.env"
adbs exec-out screencap -p > "$run/00-launch.png"
report "$run" "READY pid=$pid version=$version"
echo "READY serial=$DICTUS_SERIAL run=$run pid=$pid version=$version apk_sha=$apk_sha"
