#!/usr/bin/env bash
# Replays the committed touch runs on a keyboard installed on a running emulator, then
# prints the report (#382). The keyboard is driven as a user drives it: the taps go through
# the system's input dispatch into its window, and what it types is read back from a field.
#
#   tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard dictus > report.md
#   tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard gboard --langs fr > report.md
#
# Before: the harness APKs built (below), the emulator booted, the keyboard installed.
#   ./gradlew -p tools/keyboard-bench/harness assembleDebug assembleDebugAndroidTest
# Dictus is switched to each run's language by this script (dictus_prefs.py). Gboard is not:
# leave its keyboard on the run's language (docs/keyboard-measurement.md, "Gboard").
#
# Writes taps.tsv, commits.tsv, texts.tsv and keys.tsv per language under
# build/keyboard-bench/<keyboard>-<lang>/ and prints the report on stdout.
set -euo pipefail

BENCH="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$BENCH/../.." && pwd)"
HARNESS="$BENCH/harness/build/outputs/apk"
APP_ID=dev.pivisolutions.dictus.keyboardbench
SERIAL=""
KEYBOARD=""
LANGS="fr,en"
MODE=replay
SENTENCES=""
LINES=""
SNAP=""
RECTS=""
OUT="$ROOT/build/keyboard-bench"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --keyboard) KEYBOARD="$2"; shift 2 ;;
    --langs) LANGS="$2"; shift 2 ;;
    --mode) MODE="$2"; shift 2 ;;
    --sentences) SENTENCES="$2"; shift 2 ;;
    --lines) LINES="$2"; shift 2 ;;
    --snap) SNAP="$2"; shift 2 ;;
    --rects) RECTS="$(cat "$2")"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done
[[ -n "$SERIAL" ]] || { echo "pass --serial" >&2; exit 2; }
case "$KEYBOARD" in
  dictus) IME=dev.pivisolutions.dictus/.ime.DictusImeService ;;
  gboard) IME=com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME ;;
  *) echo "pass --keyboard dictus|gboard" >&2; exit 2 ;;
esac

adbs() { adb -s "$SERIAL" "$@"; }

# The keyboard the run is about, or a failure: stopping the Dictus app to set its language can
# leave the system on another keyboard, and a replay on the wrong one says nothing.
current_ime() { adbs shell settings get secure default_input_method | tr -d '\r'; }
select_ime() {
  for _ in 1 2 3 4 5; do
    adbs shell ime set "$IME" > /dev/null
    sleep 2
    [[ "$(current_ime)" == "$IME" ]] && return 0
  done
  echo "the keyboard is $(current_ime), not $IME" >&2
  exit 1
}

adbs install -r -t "$HARNESS/debug/keyboard-bench-harness-debug.apk" >&2
adbs install -r -t "$HARNESS/androidTest/debug/keyboard-bench-harness-debug-androidTest.apk" >&2
adbs shell ime enable "$IME" >&2
COMMIT="$(git -C "$ROOT" rev-parse --short HEAD)"
dirs=()
IFS=, read -r -a langs <<< "$LANGS"
for lang in "${langs[@]}"; do
  name="$KEYBOARD-$lang"
  if [[ "$KEYBOARD" == dictus ]]; then
    python3 -I "$BENCH/dictus_prefs.py" --serial "$SERIAL" set keyboard_language "$lang" >&2
  fi
  select_ime
  extra=()
  [[ -n "$SENTENCES" ]] && extra+=(-e sentences "$SENTENCES")
  # Quoted for the device shell, which adb hands the command to: a regex can hold | and parentheses.
  [[ -n "$LINES" ]] && extra+=(-e lines "'$LINES'")
  [[ -n "$SNAP" ]] && extra+=(-e snap "$SNAP")
  [[ -n "$RECTS" ]] && extra+=(-e rects "'$RECTS'")
  log="$(adbs shell am instrument -w -e lang "$lang" -e out "$name" -e mode "$MODE" "${extra[@]}" \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner")"
  echo "$log" >&2
  grep -q '^OK (1 test)' <<< "$log" || { echo "replay failed: $name" >&2; exit 1; }
  [[ "$(current_ime)" == "$IME" ]] || { echo "the keyboard changed during $name: $(current_ime)" >&2; exit 1; }
  mkdir -p "$OUT"
  rm -rf "${OUT:?}/$name"
  adbs pull "/sdcard/Android/data/$APP_ID/files/$name" "$OUT/" >&2
  dirs+=(--dir "$OUT/$name")
done

if [[ "$MODE" == replay ]]; then
  label="$KEYBOARD, emulator replay"
  [[ -n "$SNAP" ]] && label="$label, taps moved: $SNAP"
  python3 -I "$BENCH/score.py" "${dirs[@]}" --keyboard "$label" --commit "$COMMIT"
fi
