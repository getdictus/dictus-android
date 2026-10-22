#!/usr/bin/env bash
# Capture the current screen for the screen map: screens/<screen-id>.png in this skill,
# resized to 1140 px high and palette-quantised with python3 PIL when available (target < 300 KB).
#   screen.sh <screen-id>
set -euo pipefail
source "$(dirname "$0")/env.sh"
id="${1:?usage: screen.sh <screen-id>}"
out="$(dirname "$0")/../screens/$id.png"
sleep 0.5
adbs exec-out screencap -p > "$out"
if python3 -c 'import PIL' 2>/dev/null; then
  python3 - "$out" <<'PY'
import sys
from PIL import Image
p = sys.argv[1]
im = Image.open(p).convert("RGB")
h = 1140
im = im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
im.quantize(colors=256, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG).save(p, optimize=True)
PY
fi
echo "screen $id: $out ($(du -k "$out" | cut -f1) KB)"
