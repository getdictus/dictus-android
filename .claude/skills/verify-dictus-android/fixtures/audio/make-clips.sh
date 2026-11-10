#!/usr/bin/env bash
# Regenerate the French test clips: libespeak-ng (voice "fr", 140 wpm) through espeak_wav.py,
# then ffmpeg: 0.5 s of silence before, 0.7 s after, 16 kHz mono 16-bit PCM.
set -euo pipefail
cd "$(dirname "$0")"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
for clip in fr-bonjour-test fr-beau-paris; do
  python3 -I espeak_wav.py "$(cat "$clip.txt")" fr "$tmp/$clip.wav" 140
  ffmpeg -loglevel error -y -i "$tmp/$clip.wav" -af "adelay=500,apad=pad_dur=0.7" \
    -ar 16000 -ac 1 -c:a pcm_s16le -map_metadata -1 -fflags +bitexact "$clip.wav"
done
ls -l ./*.wav
