#!/usr/bin/env bash
# Logcat helpers for proofs (Timber DebugTree writes every Dictus log line to logcat in debug builds).
#   log.sh mark                         print the device time (epoch seconds with milliseconds)
#   log.sh wait '<regex>' [secs] [mark] wait until a Dictus line matching <regex> appears after <mark>
#   log.sh since <mark>                 print every Dictus line since <mark>
# Lines are selected by the app's uid, so they survive a crash or a restart of the process;
# `wait` tells "no matching line" apart from "no Dictus process".
set -u
source "$(dirname "$0")/env.sh"
dictus_uid() {
  local uid; uid="$(adbs shell pm list packages -U "$DICTUS_PKG" | sed -n 's/.*uid://p' | tr -d '\r' | head -1)"
  echo "$uid"
}
dictus_lines() {
  local uid; uid="$(dictus_uid)"
  if [ -n "$uid" ]; then adbs logcat -d -v threadtime -T "$1" --uid="$uid" 2>/dev/null; return; fi
  # No uid (package missing): fall back to the live pid, if any.
  local pid; pid="$(adbs shell pidof "$DICTUS_PKG" | tr -d '\r' || true)"
  [ -n "$pid" ] && adbs logcat -d -v threadtime -T "$1" --pid="$pid" 2>/dev/null
}
case "${1:-}" in
  mark)  device_mark ;;
  since) dictus_lines "${2:?mark}" ;;
  wait)
    re="${2:?regex}"; secs="${3:-15}"; mark="${4:-$(device_mark)}"
    for _ in $(seq 1 "$secs"); do
      line="$(dictus_lines "$mark" | grep -E "$re" | head -1)"
      if [ -n "$line" ]; then echo "found: $line"; exit 0; fi
      sleep 1
    done
    if [ -z "$(adbs shell pidof "$DICTUS_PKG" | tr -d '\r' || true)" ]; then
      echo "error: no Dictus process running (crashed or stopped), and no line matching /$re/ after $mark"
    else
      echo "error: Dictus is running but logged no line matching /$re/ after $mark within ${secs}s"
    fi
    exit 1 ;;
  *) echo "usage: log.sh mark | since <mark> | wait '<regex>' [secs] [mark]"; exit 2 ;;
esac
