#!/usr/bin/env bash
# Device lock shared by every thread that installs or drives on the Pixel (other skills and agents
# use the same directory: /home/pierre/Dev/dictus-proofs/.pixel-lock). DICTUS_LOCK_NAME=emulator
# works on the emulator lock instead (.emulator-lock, token file token.emulator); default pixel.
#   lock.sh acquire [label]   wait (retry every DICTUS_LOCK_RETRY_SEC, default 60 s, up to
#                             DICTUS_LOCK_WAIT_MIN, default 30 min)
#                             until the lock is free, then take
#                             it with a token unique to this session (pid + epoch ms + random)
#   lock.sh release           release it, only if its token is this session's token
#   lock.sh status            print the owner and whether it is this session
# Emulator lock only: with DICTUS_LOCK_STALE_MIN set, each acquire attempt first removes another
# session's lock older than that many minutes while no qemu-system and no pw-loopback runs (age
# from the owner's since=, else the directory's mtime). Every check-and-change of the lock
# (stale recovery, mkdir + owner write, release) runs under one flock on $DICTUS_LOCK.flock, so a
# session never removes a lock that another session has just taken.
# The token lives in the session directory (env.sh: DICTUS_SESSION_TOKEN); the lock's owner file
# holds token=, session=, label=, since=. The label is informative only: it never grants the lock.
set -u
source "$(dirname "$0")/env.sh"
cmd="${1:-status}"; label="${2:-verify-dictus-android}"
mkdir -p "$DICTUS_PROOFS" "$DICTUS_SESSION_DIR"
STALE_MIN="${DICTUS_LOCK_STALE_MIN:-}"
[ -z "$STALE_MIN" ] || [ "$DICTUS_LOCK_NAME" = emulator ] || { echo "error: DICTUS_LOCK_STALE_MIN is for the emulator lock only" >&2; exit 2; }
# Serialize on fd 9 (held until the process or the subshell that opened it exits).
guard() {
  exec 9>"$DICTUS_LOCK.flock"
  flock -w 30 9 || { echo "error: no flock on $DICTUS_LOCK.flock after 30 s" >&2; exit 1; }
}
# Caller holds the guard. Remove another session's stale lock; the owner token read at the start
# must still be the one in place when the lock is removed.
break_stale() {
  [ -n "$STALE_MIN" ] && [ -d "$DICTUS_LOCK" ] && ! lock_is_mine || return 0
  local token since age
  token="$(lock_token)"
  since="$(sed -n 's/^since=//p' "$DICTUS_LOCK/owner" 2>/dev/null | head -1)"
  since="$(date -d "${since:-@$(stat -c %Y "$DICTUS_LOCK")}" +%s 2>/dev/null || stat -c %Y "$DICTUS_LOCK")"
  age=$(( ($(date +%s) - since) / 60 ))
  [ "$age" -ge "$STALE_MIN" ] || return 0
  if [ -n "$(pgrep '^qemu-system' || true)$(pgrep -x pw-loopback || true)" ]; then
    echo "$DICTUS_LOCK_NAME lock is ${age} min old but an emulator or pw-loopback runs: not stale"; return 0
  fi
  [ "$(lock_token)" = "$token" ] || { echo "$DICTUS_LOCK_NAME lock changed owner while checked: kept"; return 0; }
  echo "STALE $DICTUS_LOCK_NAME lock removed (${age} min old, no emulator running): [$(lock_owner_line)]"
  rm -f "$DICTUS_LOCK/owner"; rmdir "$DICTUS_LOCK" 2>/dev/null || true
}
case "$cmd" in
  acquire)
    if lock_is_mine; then echo "LOCK ACQUIRED (already held by this session, token $(session_token))"; exit 0; fi
    token="$$-$(date +%s%3N)-$(od -An -N4 -tx4 /dev/urandom | tr -d ' ')"
    wait_min="${DICTUS_LOCK_WAIT_MIN:-30}"; retry="${DICTUS_LOCK_RETRY_SEC:-60}"
    deadline=$(( $(date +%s) + wait_min * 60 ))
    while :; do
      # One attempt = one critical section; the guard is let go before sleeping.
      if ( guard; break_stale
           mkdir "$DICTUS_LOCK" 2>/dev/null || exit 1
           printf 'token=%s\nsession=%s\nlabel=%s\nsince=%s\n' "$token" "$DICTUS_SESSION" "$label" "$(date -Is)" > "$DICTUS_LOCK/owner"
           echo "$token" > "$DICTUS_SESSION_TOKEN" ); then
        echo "LOCK ACQUIRED token=$token session=$DICTUS_SESSION"; exit 0
      fi
      [ "$(( $(date +%s) + retry ))" -gt "$deadline" ] && break
      echo "$DICTUS_LOCK_NAME lock held by: $(lock_owner_line) (retry in $retry s, give up at $(date -d "@$deadline" +%H:%M))"
      sleep "$retry"
    done
    echo "LOCK TIMEOUT after $wait_min min: $DICTUS_LOCK_NAME lock held by [$(lock_owner_line)]"; exit 1 ;;
  release)
    guard
    if [ ! -d "$DICTUS_LOCK" ]; then rm -f "$DICTUS_SESSION_TOKEN"; echo "LOCK already free"; exit 0; fi
    if ! lock_is_mine; then
      echo "LOCK REFUSED: held by [$(lock_owner_line)], this session is $DICTUS_SESSION with token '$(session_token)': left in place"
      exit 1
    fi
    token="$(lock_token)"
    rm "$DICTUS_LOCK/owner" && rmdir "$DICTUS_LOCK" && rm -f "$DICTUS_SESSION_TOKEN" && echo "LOCK RELEASED token=$token" ;;
  status)
    if [ ! -d "$DICTUS_LOCK" ]; then echo "free"
    elif lock_is_mine; then echo "held by this session: $(lock_owner_line)"
    else echo "held by another session: $(lock_owner_line)"; fi ;;
  *) echo "usage: lock.sh acquire [label] | release | status"; exit 2 ;;
esac
