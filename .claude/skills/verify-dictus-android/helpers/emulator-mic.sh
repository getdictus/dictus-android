#!/usr/bin/env bash
# Android emulator with a virtual microphone fed from a WAV file, under the emulator lock.
#   emulator-mic.sh build            ./gradlew assembleDebug $DICTUS_EMULATOR_GRADLE_PROP (x86_64
#                                    libs), then ./gradlew --stop; refused while an emulator runs
#   emulator-mic.sh start [N]        take the emulator lock, create the PipeWire virtual mic N
#                                    (sink dictus_feed_N -> source dictus_mic_N), boot the AVD
#                                    on it, wait for boot_completed, print the serial
#   emulator-mic.sh install [N]      adb install -r the debug APK, make Dictus the IME, grant the mic
#   emulator-mic.sh play <wav> [N]   play a WAV into dictus_feed_N (blocking); the AVD hears it
#   emulator-mic.sh stop [N]         kill the emulator and the virtual mic, release the lock
#                                    (safe twice, safe when nothing runs)
#   emulator-mic.sh status           lock owner, then per instance: pids, serial, PipeWire nodes
# N defaults to 1; the serial is emulator-<5552+2N> (N=1 -> emulator-5554). One emulator at a
# time on this machine (12 GB): start waits while another session holds the emulator lock, or
# while any qemu-system or Gradle daemon runs (retry every 2 min, up to 30 min). A lock older than
# 45 min with no qemu-system and no pw-loopback running is stale: start removes it and says so.
# Overrides: DICTUS_AVD (dictus_api35), DICTUS_EMU_MEMORY (2048), DICTUS_EMU_WAIT_MIN (30, 0 =
# refuse at once), DICTUS_EMU_STALE_MIN (45), DICTUS_EMU_BOOT_TIMEOUT (300 s).
set -euo pipefail
H="$(cd "$(dirname "$0")" && pwd)"
export DICTUS_LOCK_NAME=emulator
source "$H/env.sh"
EMULATOR_BIN="$ANDROID_HOME/emulator/emulator"
AVD="${DICTUS_AVD:-dictus_api35}"
STATE_ROOT="$DICTUS_PROOFS/emulator-mic"
# Match process names, not whole command lines: a shell whose command line merely mentions
# "qemu-system" (an agent's own pgrep) must not count as a running emulator.
running_emulators() { pgrep -a '^qemu-system' || true; }
running_gradle() { pgrep -af '^[^ ]*java .*GradleDaemon' || true; }
running_loopbacks() { pgrep -ax pw-loopback || true; }
WAIT_MIN="${DICTUS_EMU_WAIT_MIN:-30}"; RETRY_SEC=120; STALE_MIN="${DICTUS_EMU_STALE_MIN:-45}"

cmd="${1:-status}"
die() { echo "error: $*" >&2; exit 1; }
instance() {
  local n="${1:-1}"
  [[ "$n" =~ ^[1-9]$ ]] || die "instance N must be 1..9, got '$n'"
  N="$n"; PORT=$((5552 + 2 * N)); SERIAL="emulator-$PORT"
  FEED="dictus_feed_$N"; MIC="dictus_mic_$N"; STATE="$STATE_ROOT/$N"
}
# pid from a state file, only if it is alive and still runs the expected command.
live_pid() {
  local file="$1" pattern="$2" pid
  pid="$(cat "$file" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -- "$pattern" >/dev/null && echo "$pid"
  return 0
}
# grep without -q in pipes: under pipefail an early grep exit fails the pipeline (SIGPIPE).
node_exists() { pw-cli ls Node 2>/dev/null | grep "node.name = \"$1\"" >/dev/null; }
booted() { [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; }
# 0 when an instance other than N runs (state dirs are per machine); its number in OTHER.
other_instances_running() {
  local d; OTHER=""
  for d in "$STATE_ROOT"/*/; do
    [ -d "$d" ] || continue
    [ "$(basename "$d")" = "$N" ] && continue
    [ -n "$(live_pid "$d/emulator.pid" qemu-system)$(live_pid "$d/loopback.pid" pw-loopback)" ] && { OTHER="$(basename "$d")"; return 0; }
  done
  return 1
}

# Remove the lock of another session when it is older than STALE_MIN and nothing it could guard
# runs (no qemu-system, no pw-loopback). Age from the owner's since=, else the directory's mtime.
break_stale_lock() {
  [ -d "$DICTUS_LOCK" ] && ! lock_is_mine || return 0
  local since age
  since="$(grep -o 'since=[^ ]*' "$DICTUS_LOCK/owner" 2>/dev/null | head -1 | cut -d= -f2- || true)"
  since="$(date -d "${since:-@$(stat -c %Y "$DICTUS_LOCK")}" +%s 2>/dev/null || stat -c %Y "$DICTUS_LOCK")"
  age=$(( ($(date +%s) - since) / 60 ))
  [ "$age" -ge "$STALE_MIN" ] || return 0
  if [ -n "$(running_emulators)$(running_loopbacks)" ]; then
    echo "emulator lock is ${age} min old but an emulator or pw-loopback runs: not stale, waiting"; return 0
  fi
  echo "STALE emulator lock removed (${age} min old, no emulator running): [$(lock_owner_line)]"
  rm -f "$DICTUS_LOCK/owner"; rmdir "$DICTUS_LOCK" 2>/dev/null || true
}
# Wait (RETRY_SEC, up to WAIT_MIN) until no emulator and no Gradle daemon runs on this machine.
wait_machine_idle() {
  local deadline=$(( $(date +%s) + WAIT_MIN * 60 )) busy
  while busy="$(running_emulators; running_gradle)"; [ -n "$busy" ]; do
    [ -d "$DICTUS_LOCK" ] && echo "emulator lock held by: $(lock_owner_line)"
    [ "$(( $(date +%s) + RETRY_SEC ))" -gt "$deadline" ] && die "machine still busy after $WAIT_MIN min: $(echo "$busy" | cut -c1-160)"
    echo "busy (an emulator or a Gradle build runs), retry in $RETRY_SEC s:"; echo "$busy" | cut -c1-160
    sleep "$RETRY_SEC"
  done
}

build() {
  if [ -n "$(running_emulators)" ]; then die "an emulator runs: stop it before building (12 GB RAM)"; fi
  echo "building: ./gradlew assembleDebug $DICTUS_EMULATOR_GRADLE_PROP"
  (cd "$REPO_ROOT" && ./gradlew assembleDebug "$DICTUS_EMULATOR_GRADLE_PROP" && ./gradlew --stop)
  echo "BUILT $DICTUS_APK"
}

install_apk() {
  instance "${1:-1}"
  booted || die "$SERIAL is not booted: start $N first"
  [ -f "$DICTUS_APK" ] || die "no APK at $DICTUS_APK: run 'emulator-mic.sh build' first"
  unzip -l "$DICTUS_APK" | grep 'lib/x86_64/' >/dev/null || die "$DICTUS_APK has no lib/x86_64: build it with 'emulator-mic.sh build'"
  adb -s "$SERIAL" install -r "$DICTUS_APK"
  adb -s "$SERIAL" shell pm grant "$DICTUS_PKG" android.permission.RECORD_AUDIO
  adb -s "$SERIAL" shell ime enable "$DICTUS_IME" >/dev/null
  adb -s "$SERIAL" shell ime set "$DICTUS_IME" >/dev/null
  echo "INSTALLED on $SERIAL: $(adb -s "$SERIAL" shell dumpsys package "$DICTUS_PKG" | grep -m1 versionName | tr -d ' \r'), IME $DICTUS_IME"
}

start() {
  instance "${1:-1}"
  command -v pw-loopback >/dev/null && command -v pw-cli >/dev/null || die "pw-loopback/pw-cli missing (PipeWire)"
  [ -x "$EMULATOR_BIN" ] || die "no emulator at $EMULATOR_BIN"
  "$EMULATOR_BIN" -list-avds 2>/dev/null | grep -x "$AVD" >/dev/null || die "no AVD named $AVD"
  grep -qx 'hw.audioInput *= *yes' "$HOME/.android/avd/$AVD.avd/config.ini" \
    || echo "warn: $AVD has no 'hw.audioInput = yes' in config.ini: Android may not see a microphone" >&2

  # Already up (idempotent start): print the serial and leave everything as it is.
  if lock_is_mine && [ -n "$(live_pid "$STATE/emulator.pid" qemu-system)" ] && booted; then
    echo "already running: serial=$SERIAL mic=$MIC feed=$FEED"; echo "SERIAL $SERIAL"; return 0
  fi
  if other_instances_running; then
    die "instance $OTHER already runs (emulator lock: [$(lock_owner_line)]); one emulator at a time"
  fi

  # Wait for the machine before taking the lock, so a build elsewhere is not blocked by an idle
  # holder; check again once the lock is ours.
  wait_machine_idle
  break_stale_lock
  DICTUS_LOCK_WAIT_MIN="$WAIT_MIN" DICTUS_LOCK_RETRY_SEC="$RETRY_SEC" "$H/lock.sh" acquire "emulator-mic $N" \
    || die "emulator lock not acquired (see the owner above); nothing started"
  # From here a failure undoes this start (emulator, virtual mic, lock): nothing is left half up.
  trap '[ "$?" = 0 ] || { echo "start failed: undoing" >&2; stop "$N" >&2; }' EXIT
  mkdir -p "$STATE"
  wait_machine_idle

  if [ -z "$(live_pid "$STATE/loopback.pid" pw-loopback)" ]; then
    node_exists "$FEED" && die "PipeWire node $FEED exists but is not ours (no live pid in $STATE); remove it first"
    # Capture side = a sink apps can play into; playback side = a source the emulator records.
    setsid pw-loopback -n "dictus-virtual-mic-$N" \
      --capture-props="media.class=Audio/Sink node.name=$FEED node.description=Dictus-feed-$N audio.position=[MONO]" \
      --playback-props="media.class=Audio/Source node.name=$MIC node.description=Dictus-mic-$N audio.position=[MONO]" \
      > "$STATE/loopback.log" 2>&1 < /dev/null &
    echo $! > "$STATE/loopback.pid"
    for _ in $(seq 1 20); do node_exists "$MIC" && node_exists "$FEED" && break; sleep 0.25; done
    node_exists "$MIC" && node_exists "$FEED" || die "virtual mic nodes did not appear (see $STATE/loopback.log)"
    echo "virtual mic: $FEED -> $MIC (pw-loopback pid $(cat "$STATE/loopback.pid"))"
  fi

  # Not -no-window: it selects qemu-system-*-headless, whose PulseAudio driver is a stub
  # (emulator 37.1.11: "Could not init `pa' audio driver", the guest records silence). The Qt
  # binary links libpulse; -qt-hide-window with Qt's offscreen platform keeps it windowless.
  # No audio output: the only sink may be dictus_feed_N, and the guest's sounds would loop back
  # into its own microphone.
  PULSE_SOURCE="$MIC" QEMU_AUDIO_OUT_DRV=none QT_QPA_PLATFORM=offscreen setsid "$EMULATOR_BIN" -avd "$AVD" -port "$PORT" \
    -memory "${DICTUS_EMU_MEMORY:-2048}" -qt-hide-window -no-snapshot-save -no-boot-anim \
    -gpu swiftshader_indirect -allow-host-audio -grpc "$((PORT + 3000))" -grpc-use-token \
    > "$STATE/emulator.log" 2>&1 < /dev/null &
  echo $! > "$STATE/emulator.pid"
  # The launcher may hand over to a qemu-system process: record that one (it carries our -port).
  local qpid=""
  for _ in $(seq 1 40); do
    qpid="$(pgrep -f "^[^ ]*qemu-system[^ ]* .*-port $PORT( |\$)" | head -1 || true)"
    [ -n "$qpid" ] && break; sleep 0.5
  done
  [ -n "$qpid" ] || die "no qemu-system process for port $PORT (see $STATE/emulator.log)"
  echo "$qpid" > "$STATE/emulator.pid"
  echo "emulator: avd=$AVD serial=$SERIAL pid=$(cat "$STATE/emulator.pid") PULSE_SOURCE=$MIC (log $STATE/emulator.log)"

  local timeout="${DICTUS_EMU_BOOT_TIMEOUT:-300}" t=0
  until booted; do
    [ -n "$(live_pid "$STATE/emulator.pid" qemu-system)" ] || die "emulator exited during boot (see $STATE/emulator.log)"
    [ "$t" -ge "$timeout" ] && die "no boot_completed after ${timeout}s"
    sleep 5; t=$((t + 5))
  done
  if grep -F "Could not init \`pa' audio driver" "$STATE/emulator.log" >/dev/null; then
    die "the emulator has no PulseAudio input, the virtual mic would be silent (see $STATE/emulator.log)"
  fi
  # -allow-host-audio alone still hands the guest zeroes: switch the host microphone on.
  [ "$(adb -s "$SERIAL" emu avd hostmicon 2>/dev/null | tr -d '\r' | sed -n 1p)" = OK ] \
    || die "'adb emu avd hostmicon' failed: the guest would record silence"
  echo "$SERIAL" > "$STATE/serial"
  trap - EXIT
  echo "booted in ~${t}s"
  echo "SERIAL $SERIAL"
}

play() {
  local wav="${1:-}"; instance "${2:-1}"
  [ -f "$wav" ] || die "no WAV file '$wav'"
  node_exists "$FEED" || die "no PipeWire node $FEED: run 'emulator-mic.sh start $N' first"
  sleep 0.3   # lead-in: let the recorder settle before the first word
  echo "playing $wav -> $FEED"
  pw-play --target "$FEED" "$wav"
  echo "PLAYED $(basename "$wav")"
}

stop() {
  instance "${1:-1}"
  local pid
  pid="$(live_pid "$STATE/emulator.pid" qemu-system)"
  if [ -n "$pid" ]; then
    adb -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null || true; sleep 2; kill -9 "$pid" 2>/dev/null || true; fi
    echo "emulator $SERIAL stopped (pid $pid)"
  else
    echo "emulator $N: not running"
  fi
  pid="$(live_pid "$STATE/loopback.pid" pw-loopback)"
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 10); do kill -0 "$pid" 2>/dev/null || break; sleep 0.3; done
    echo "virtual mic $MIC removed (pw-loopback pid $pid)"
  else
    echo "virtual mic $N: not running"
  fi
  rm -f "$STATE/emulator.pid" "$STATE/loopback.pid" "$STATE/serial"
  if other_instances_running; then
    echo "emulator lock kept: instance $OTHER still runs"
  else
    "$H/lock.sh" release || true
  fi
  echo "STOPPED $N"
}

status() {
  echo "emulator lock: $("$H/lock.sh" status)"
  local d found=0
  for d in "$STATE_ROOT"/*/; do
    [ -d "$d" ] || continue
    instance "$(basename "$d")"; found=1
    local epid lpid
    epid="$(live_pid "$STATE/emulator.pid" qemu-system)"; lpid="$(live_pid "$STATE/loopback.pid" pw-loopback)"
    printf 'instance %s: emulator=%s serial=%s booted=%s pw-loopback=%s nodes=%s/%s\n' "$N" \
      "${epid:-down}" "$SERIAL" "$( [ -n "$epid" ] && booted && echo yes || echo no)" "${lpid:-down}" \
      "$(node_exists "$FEED" && echo "$FEED" || echo -)" "$(node_exists "$MIC" && echo "$MIC" || echo -)"
  done
  [ "$found" = 1 ] || echo "no instance started from this machine yet"
  if [ -d "$DICTUS_LOCK" ] && [ -z "$(running_emulators)$(running_loopbacks)" ]; then
    echo "note: the lock is held but no emulator or pw-loopback runs; if its owner session is gone, it is stale:"
    echo "      check, then remove $DICTUS_LOCK by hand (lock.sh releases only its own token)"
  fi
}

case "$cmd" in
  build) build ;;
  start) start "${2:-1}" ;;
  install) install_apk "${2:-1}" ;;
  play) play "${2:-}" "${3:-1}" ;;
  stop) stop "${2:-1}" ;;
  status) status ;;
  *) echo "usage: emulator-mic.sh build | start [N] | install [N] | play <wav> [N] | stop [N] | status"; exit 2 ;;
esac
