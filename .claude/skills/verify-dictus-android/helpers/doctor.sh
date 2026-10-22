#!/usr/bin/env bash
# Read-only health check: is this Pixel worth driving? Installs, taps and changes nothing.
#   doctor.sh [run-dir]
# One OK/WARN/FAIL line per check, then DOCTOR OK or DOCTOR FAIL <n> (exit 1).
set -u
source "$(dirname "$0")/env.sh"
fails=0
ok()   { echo "OK   $*"; }
warn() { echo "WARN $*"; }
fail() { echo "FAIL $*"; fails=$((fails+1)); }

command -v adb >/dev/null && ok "adb $(command -v adb)" || fail "adb not on PATH"
command -v argent >/dev/null && ok "argent $(argent --version 2>/dev/null | head -1)" || warn "argent CLI not on PATH (MCP tools may still work)"

if [ "$(adbs get-state 2>/dev/null)" = "device" ]; then ok "Pixel $DICTUS_SERIAL connected"; else fail "Pixel $DICTUS_SERIAL not connected"; echo "DOCTOR FAIL $fails"; exit 1; fi
if pixel_locked; then
  fail "Pixel locked (do not unlock it: run cleanup.sh, which releases the lock, and report)"
else ok "Pixel unlocked"; fi
[ "$(adbs shell getprop persist.sys.locale | tr -d '\r')" = "fr-FR" ] && ok "device locale fr-FR" || warn "device locale is not fr-FR: labels in the map will not match"

if lock_is_mine; then ok "lock held by this session ($DICTUS_SESSION, token $(session_token))"
elif [ -d "$DICTUS_LOCK" ]; then fail "lock held by ANOTHER session [$(lock_owner_line)]: do not drive"
else fail "Pixel lock not held (helpers/lock.sh acquire)"; fi

if adbs shell pm path "$DICTUS_PKG" >/dev/null 2>&1; then
  ok "Dictus installed: $(adbs shell dumpsys package "$DICTUS_PKG" | grep -E 'versionName|lastUpdateTime' | head -2 | tr -d '\r' | xargs)"
else fail "Dictus not installed"; fi
run="$(resolve_run "${1:-}" || true)"
if [ -n "$run" ] && [ -f "$run/run.env" ]; then
  APK_SHA="$(sed -n 's/^APK_SHA=//p' "$run/run.env")"   # read, never sourced
  dev_sha="$(adbs shell 'cat $(pm path '"$DICTUS_PKG"' | head -1 | cut -d: -f2)' | sha256sum | cut -c1-16)"
  [ "$dev_sha" = "${APK_SHA:-}" ] && ok "installed APK = this run's build ($dev_sha)" || fail "installed APK $dev_sha != this run's build ${APK_SHA:-?}"
else warn "no run of this session: build identity not checked"; fi

ime="$(adbs shell settings get secure default_input_method | tr -d '\r')"
[ "$ime" = "$DICTUS_IME" ] && ok "default IME is Dictus" || fail "default IME is $ime"
adbs shell dumpsys package "$DICTUS_PKG" | grep -q 'android.permission.RECORD_AUDIO: granted=true' \
  && ok "RECORD_AUDIO granted" || warn "RECORD_AUDIO not granted: the mic does nothing"
pid="$(adbs shell pidof "$DICTUS_PKG" | tr -d '\r' || true)"
[ -n "$pid" ] && ok "Dictus process running pid=$pid" || warn "Dictus process not running"
if adbs shell run-as "$DICTUS_PKG" ls files/ 2>/dev/null | grep -q dictus.log; then ok "dictus.log readable (run-as)"; else warn "dictus.log not readable (release build?)"; fi
# Harmless settings only (never the last transcription): what a run must leave as it found it.
prefs_tmp="$(pull_prefs)"
if [ -s "$prefs_tmp" ]; then ok "settings: $(prefs_tool show "$prefs_tmp")"; else warn "settings not readable (run-as)"; fi
rm -f "$prefs_tmp"
models="$(adbs shell run-as "$DICTUS_PKG" ls files/models 2>/dev/null | tr -d '\r' | xargs)"
[ -n "$models" ] && ok "models on device: $models" || warn "no model downloaded: dictation ends on « Aucun modèle vocal »"

# One driver at a time: anything else holding UiAutomation breaks Argent's describe.
others="$(adbs shell ps -A -o NAME 2>/dev/null | grep -E 'uiautomator|appium|maestro|io.github.mobile' | grep -v grep | tr '\n' ' ')"
[ -z "$others" ] && ok "no other UI driver on the device" || warn "device-side driver processes: $others (Argent's own uiautomator dump is short-lived; a long-lived one is another tool)"
hostdrv="$(pgrep -af 'mobile-mcp|maestro|appium' | grep -v pgrep | head -3)"
[ -z "$hostdrv" ] && ok "no other driver on the host" || fail "another driver runs on the host: $hostdrv"

if [ "$fails" -eq 0 ]; then echo "DOCTOR OK"; else echo "DOCTOR FAIL $fails"; exit 1; fi
