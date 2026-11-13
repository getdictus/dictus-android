#!/usr/bin/env bash
# Fetch sherpa-onnx's x86_64 Android libraries into asr/src/emulator/jniLibs/x86_64 (not in git),
# so a debug build made with -Pdictus.emulatorAbi=true can run Parakeet on an x86_64 emulator.
# Same release as the ARM libraries in asr/src/main/jniLibs; archive and files checked by sha256
# (docs/sherpa-onnx-provenance.md). Phone, CI and release builds do not need it.
set -euo pipefail

VERSION=v2.0.1
ARCHIVE="sherpa-onnx-$VERSION-android.tar.bz2"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/$VERSION/$ARCHIVE"
ARCHIVE_SHA256=39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330
declare -A LIB_SHA256=(
  [libonnxruntime.so]=087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e
  [libsherpa-onnx-jni.so]=878d61116f244131263eec24297ef05bf508d02bccdede2a4e3ad3202bbdaa48
)

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO/asr/src/emulator/jniLibs/x86_64"

ok() { [ "$(sha256sum "$1" | cut -d' ' -f1)" = "$2" ]; }

all_present=1
for lib in "${!LIB_SHA256[@]}"; do
  [ -f "$DEST/$lib" ] && ok "$DEST/$lib" "${LIB_SHA256[$lib]}" || all_present=0
done
if [ "$all_present" = 1 ]; then
  echo "sherpa-onnx $VERSION x86_64 libraries already in $DEST"
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
echo "downloading $URL"
curl -fsSL -o "$WORK/$ARCHIVE" "$URL"
ok "$WORK/$ARCHIVE" "$ARCHIVE_SHA256" || { echo "sha256 mismatch for $ARCHIVE" >&2; exit 1; }
tar -xjf "$WORK/$ARCHIVE" -C "$WORK" ./jniLibs/x86_64/libonnxruntime.so ./jniLibs/x86_64/libsherpa-onnx-jni.so
mkdir -p "$DEST"
for lib in "${!LIB_SHA256[@]}"; do
  ok "$WORK/jniLibs/x86_64/$lib" "${LIB_SHA256[$lib]}" || { echo "sha256 mismatch for $lib" >&2; exit 1; }
  cp "$WORK/jniLibs/x86_64/$lib" "$DEST/$lib"
done
echo "sherpa-onnx $VERSION x86_64 libraries written to $DEST"
