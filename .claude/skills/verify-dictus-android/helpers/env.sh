#!/usr/bin/env bash
# Sourced by every helper: toolchain, device, package, evidence root, session, lock, shared checks.
# Override any of these from the environment before calling a helper.
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

DICTUS_SERIAL="${DICTUS_SERIAL:-99071FFAZ00EBN}"                  # Pixel 4, USB, LineageOS, French
DICTUS_PKG="dev.pivisolutions.dictus"
DICTUS_ACTIVITY="$DICTUS_PKG/.MainActivity"
DICTUS_IME="$DICTUS_PKG/.ime.DictusImeService"
DICTUS_PROOFS="${DICTUS_PROOFS:-/home/pierre/Dev/dictus-proofs}"
DICTUS_EVIDENCE_ROOT="${DICTUS_EVIDENCE_ROOT:-$DICTUS_PROOFS/verify-dictus-android}"
# Device lock: "pixel" (default, every existing caller) or "emulator" (emulator-mic.sh).
DICTUS_LOCK_NAME="${DICTUS_LOCK_NAME:-pixel}"
case "$DICTUS_LOCK_NAME" in pixel|emulator) ;; *) echo "error: DICTUS_LOCK_NAME must be pixel or emulator" >&2; exit 2 ;; esac
DICTUS_LOCK="$DICTUS_PROOFS/.$DICTUS_LOCK_NAME-lock"
DICTUS_PREFS_REMOTE="files/datastore/dictus_prefs.preferences_pb"
REPO_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
DICTUS_APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
# Gradle property that adds the x86_64 native libs to the debug APK (emulator builds only);
# the only place that names it.
DICTUS_EMULATOR_GRADLE_PROP="${DICTUS_EMULATOR_GRADLE_PROP:--Pdictus.emulatorAbi=true}"

# Session = this checkout (full path, hashed), or DICTUS_VERIFY_SESSION when two agents share
# one checkout. The session directory holds the lock token and the run pointer, so a session
# never resolves another session's run and never releases another session's lock.
DICTUS_SESSION="${DICTUS_VERIFY_SESSION:-$(basename "$REPO_ROOT")-$(printf '%s' "$REPO_ROOT" | sha256sum | cut -c1-12)}"
DICTUS_SESSION_DIR="$DICTUS_EVIDENCE_ROOT/sessions/$DICTUS_SESSION"
# Lock token of this session, one file per lock: "token" (Pixel, unchanged) or "token.emulator".
DICTUS_SESSION_TOKEN="$DICTUS_SESSION_DIR/token"
[ "$DICTUS_LOCK_NAME" = pixel ] || DICTUS_SESSION_TOKEN="$DICTUS_SESSION_DIR/token.$DICTUS_LOCK_NAME"
DICTUS_SESSION_PTR="$DICTUS_SESSION_DIR/run"       # "<token> <run-dir>", written by launch.sh

adbs() { adb -s "$DICTUS_SERIAL" "$@"; }

# Lock ownership: the token in the lock's owner file against this session's token.
session_token() { cat "$DICTUS_SESSION_TOKEN" 2>/dev/null || true; }
lock_token() { sed -n 's/^token=//p' "$DICTUS_LOCK/owner" 2>/dev/null || true; }
lock_is_mine() { local mine; mine="$(session_token)"; [ -n "$mine" ] && [ "$(lock_token)" = "$mine" ]; }
lock_owner_line() { paste -sd' ' "$DICTUS_LOCK/owner" 2>/dev/null || echo "?"; }

# 0 when the Pixel shows the keyguard or the lock-screen dream: never unlock it, stop and report.
pixel_locked() { adbs shell dumpsys window | grep -qE 'isKeyguardShowing=true|mDreamingLockscreen=true'; }

# Log mark: device epoch with milliseconds ("sssss.mmm", a format `logcat -T` accepts).
device_mark() {
  local ns; ns="$(adbs shell 'date +%s%N' | tr -d '\r')"
  case "$ns" in
    *N|"") adbs shell 'date +%s' | tr -d '\r' | sed 's/$/.000/' ;;        # no %N: second precision
    *) printf '%s.%s\n' "${ns:0:10}" "${ns:10:3}" ;;
  esac
}

# Run directory: $1, else $DICTUS_VERIFY_RUN, else this session's pointer. A run belongs to the
# session whose token is in its run.env; any other run (older session, other agent) is refused.
resolve_run() {
  local run="${1:-${DICTUS_VERIFY_RUN:-}}" mine; mine="$(session_token)"
  if [ -z "$run" ] && [ -f "$DICTUS_SESSION_PTR" ]; then
    read -r ptr_token ptr_run < "$DICTUS_SESSION_PTR"
    [ -n "$mine" ] && [ "$ptr_token" = "$mine" ] && run="$ptr_run"
  fi
  if [ -z "$run" ] || [ ! -d "$run" ]; then
    echo "error: no run directory for this session (run launch.sh first, or export DICTUS_VERIFY_RUN)" >&2
    return 1
  fi
  if [ "$(sed -n 's/^TOKEN=//p' "$run/run.env" 2>/dev/null)" != "$mine" ] || [ -z "$mine" ]; then
    echo "error: $run belongs to another session or an older lock; not touching it" >&2
    return 1
  fi
  echo "$run"
}

report() { local run="$1"; shift; echo "$(date -Is) $*" >> "$run/report.txt"; }

# Settings without values. The DataStore is a protobuf map<string, Value>; this prints, per key,
# "<key> <sha256 of the value bytes>", or the value itself only for the whitelisted harmless keys
# (mode "show"). Volatile keys (last transcription, timestamps) are skipped by key, never by line.
#   prefs_tool digest <file.pb>   -> "key sha256" lines, sorted
#   prefs_tool show <file.pb>     -> "key=value" for active_model, transcription_language,
#                                    keyboard_language, keyboard_layout, keyboard_mode, theme
#   prefs_tool secret <file.pb>   -> the raw last-transcription text (for the leak check only;
#                                    write it to a chmod 600 file, never to the terminal)
prefs_tool() {
  python3 -I - "$@" <<'PY'
import hashlib, re, sys
mode, path = sys.argv[1], sys.argv[2]
data = open(path, "rb").read()
def varint(b, i):
    v = s = 0
    while True:
        c = b[i]; i += 1; v |= (c & 0x7F) << s; s += 7
        if c < 0x80: return v, i
def fields(b):
    i = 0
    while i < len(b):
        tag, i = varint(b, i); f, w = tag >> 3, tag & 7
        if w == 0: v, i = varint(b, i)
        elif w == 1: v = b[i:i+8]; i += 8
        elif w == 5: v = b[i:i+4]; i += 4
        elif w == 2:
            n, i = varint(b, i); v = b[i:i+n]; i += n
        else: raise ValueError("wire type %d" % w)
        yield f, w, v
entries = {}
for f, w, v in fields(data):
    if f == 1 and w == 2:
        key = val = None
        for ef, ew, ev in fields(v):
            if ef == 1: key = ev.decode("utf-8", "replace")
            elif ef == 2: val = ev
        if key is not None: entries[key] = val or b""
VOLATILE = re.compile(r"^last_transcription|_at$|_time$|timestamp")
SHOW = {"active_model", "transcription_language", "keyboard_language", "keyboard_layout", "keyboard_mode", "theme"}
def as_string(val):
    for f, w, v in fields(val):
        if f == 5 and w == 2: return v.decode("utf-8", "replace")
    return None
if mode == "digest":
    for k in sorted(entries):
        if not VOLATILE.search(k): print(k, hashlib.sha256(entries[k]).hexdigest())
elif mode == "show":
    print(" ".join("%s=%s" % (k, as_string(entries[k])) for k in sorted(entries) if k in SHOW))
elif mode == "secret":
    for k in sorted(entries):
        if k.startswith("last_transcription"):
            s = as_string(entries[k])
            if s: sys.stdout.write(s + "\n")
PY
}

# Pull the app's DataStore into a private temp file (chmod 600, outside the repo); prints its path.
pull_prefs() {
  local tmp; tmp="$(mktemp /tmp/dictus-prefs.XXXXXX)"; chmod 600 "$tmp"
  adbs exec-out run-as "$DICTUS_PKG" cat "$DICTUS_PREFS_REMOTE" > "$tmp" 2>/dev/null || true
  echo "$tmp"
}
