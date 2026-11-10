#!/usr/bin/env bash
# Capture one proof step into the run directory (never into the repository).
#   evidence.sh <name> [mark] [run-dir]
# Writes <name>.png (adb screencap, full resolution: Argent's own screenshot can lag one frame)
# and, when a mark is given, <name>.log with every Dictus logcat line since that mark.
set -euo pipefail
source "$(dirname "$0")/env.sh"
name="${1:?usage: evidence.sh <name> [mark] [run-dir]}"; mark="${2:-}"
run="$(resolve_run "${3:-}")"
sleep 0.5
adbs exec-out screencap -p > "$run/$name.png"
if [ -n "$mark" ]; then "$(dirname "$0")/log.sh" since "$mark" > "$run/$name.log"; fi
report "$run" "evidence $name"
echo "evidence $name: $run/$name.png${mark:+ + $name.log}"
