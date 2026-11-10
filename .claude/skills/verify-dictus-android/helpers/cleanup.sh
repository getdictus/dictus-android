#!/usr/bin/env bash
# Tear down what this session changed, keep the evidence.
#   cleanup.sh [run-dir]
# With a run of THIS session (its token in run.env): copies dictus.log, compares the settings with
# the snapshot taken at launch (key names and hashes only: prints "PREFS same" or
# "PREFS differ: <keys>", never a value), restores the IME launch.sh replaced (if any).
# Then, run or no run, releases the Pixel lock if this session holds it.
# Never uninstalls, never clears app data, never kills by name, never deletes evidence.
set -u
source "$(dirname "$0")/env.sh"
run="$(resolve_run "${1:-}")" || run=""
if [ -n "$run" ]; then
  adbs shell run-as "$DICTUS_PKG" cat files/dictus.log > "$run/dictus.full.log" 2>/dev/null \
    && echo "copied dictus.log ($(wc -l < "$run/dictus.full.log") lines)" || echo "warning: dictus.log not copied"
  prefs_tmp="$(pull_prefs)"
  if [ -s "$prefs_tmp" ]; then prefs_tool digest "$prefs_tmp" > "$run/prefs-after.digest"; fi
  rm -f "$prefs_tmp"
  if [ -s "$run/prefs-before.digest" ] && [ -s "$run/prefs-after.digest" ]; then
    changed="$(cat "$run/prefs-before.digest" "$run/prefs-after.digest" | sort | uniq -u | cut -d' ' -f1 | sort -u | paste -sd' ')"
    if [ -z "$changed" ]; then echo "PREFS same"; else echo "PREFS differ: $changed"; fi
    report "$run" "PREFS ${changed:-same}"
  else
    echo "warning: settings not compared (no snapshot)"
  fi
  prev="$(sed -n 's/^PREV_IME=//p' "$run/run.env")"
  if [ -n "$prev" ] && [ "$prev" != "$DICTUS_IME" ] && [ "$prev" != "null" ]; then
    adbs shell ime set "$prev" >/dev/null && echo "IME restored to $prev"
  fi
  report "$run" "CLEANUP OK"
else
  echo "no run of this session: run steps skipped; releasing this session's lock if it holds one"
fi
"$(dirname "$0")/lock.sh" release || true
echo "CLEANUP OK run=${run:-none}${run:+ files=$(find "$run" -maxdepth 1 -type f | wc -l)}"
echo "NEXT: stop Argent for the Pixel only — mcp__argent__stop-all-simulator-servers {devices:[\"$DICTUS_SERIAL\"]} (CLI: argent run stop-all-simulator-servers --devices $DICTUS_SERIAL)"
