"""Reads a #370 touch map (map.tsv, keys.tsv, screen.png from KeyboardReplay mode=map) and writes:
- zones.txt: per line, the runs of what a tap typed, in px and dp along the line;
- map.png: the keyboard capture with every tap painted by what it typed;
- geometry.tsv: drawn key spans measured on the capture.

usage: python3 -I touchmap.py <dir> <title>
"""
import colorsys
import hashlib
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

d = Path(sys.argv[1])
title = sys.argv[2]
lines = (d / "map.tsv").read_text().splitlines()
head = lines[0]
m = re.search(r"screen (\d+) x (\d+) px, density ([\d.]+), ime window \[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\], bar bottom (\d+), "
              r"pitch_x ([\d.]+), pitch_y ([\d.]+)", head)
W, H, density = int(m[1]), int(m[2]), float(m[3])
ime = tuple(int(m[i]) for i in range(4, 8))
bar_bottom = int(m[8])
pitch_x, pitch_y = float(m[9]), float(m[10])
rows = [l.split("\t") for l in lines[2:] if l]
taps = [(r[0], int(r[1]), int(r[2]), r[3]) for r in rows]


def dp(px):
    return px / density


# Zones per line.
out = [head, f"# a run: what a tap typed, from..to along the line (px, then dp from the line's start)", ""]
by_line = {}
for name, x, y, res in taps:
    by_line.setdefault(name, []).append((x, y, res))
# A second space in a row is the double space that types a period: the field reads "edit", the
# key was the space bar.
# After that period the keyboard turns its automatic capital on, which the next dead tap sees as
# keys that changed: that tap typed nothing.
for name, pts in by_line.items():
    for i in range(1, len(pts)):
        if pts[i][2] == "edit" and pts[i - 1][2] == "space":
            pts[i] = (pts[i][0], pts[i][1], "space")
        elif pts[i][2] == "modifier" and pts[i - 1][2] == "space" and (i + 1 == len(pts) or pts[i + 1][2] == "none"):
            pts[i] = (pts[i][0], pts[i][1], "none")
taps = [(name, x, y, res) for name, pts in by_line.items() for x, y, res in pts]
for name, pts in by_line.items():
    vertical = len({p[0] for p in pts}) == 1
    runs = []
    for x, y, res in pts:
        c = y if vertical else x
        if runs and runs[-1][0] == res:
            runs[-1][2] = c
        else:
            runs.append([res, c, c])
    axis = "y" if vertical else "x"
    fixed = f"x={pts[0][0]}" if vertical else f"y={pts[0][1]}"
    desc = "  ".join(f"{r}[{a}..{b}]" for r, a, b in runs)
    out.append(f"{name:24s} {fixed:7s} {axis}: {desc}")
(d / "zones.txt").write_text("\n".join(out) + "\n")

# Colours.
SPECIAL = {
    "none": (230, 0, 0), "space": (0, 120, 255), "delete": (255, 140, 0), "shift": (255, 200, 0),
    "enter": (0, 200, 120), "hidden": (90, 90, 90), "system": (90, 90, 90),
    "modifier": (170, 0, 200), "bar": (160, 160, 160), "edit": (255, 0, 255),
}


def colour(res):
    if res in SPECIAL:
        return SPECIAL[res]
    h = int(hashlib.md5(res.encode()).hexdigest(), 16) % 360
    r, g, b = colorsys.hls_to_rgb(h / 360, 0.55, 0.75)
    return int(r * 255), int(g * 255), int(b * 255)


shot = Image.open(d / "screen.png").convert("RGB")
top = max(0, min(ime[1], bar_bottom - 200))
crop = shot.crop((0, top, W, H)).copy()
faded = Image.blend(crop, Image.new("RGB", crop.size, (255, 255, 255)), 0.35)
draw = ImageDraw.Draw(faded)
half = max(2, int(round(2 * density)))  # painted 4 dp across the line
for name, pts in by_line.items():
    vertical = len({p[0] for p in pts}) == 1
    for x, y, res in pts:
        yy = y - top
        if vertical:
            draw.rectangle([x - half, yy, x + half, yy + 1], fill=colour(res))
        else:
            draw.rectangle([x, yy - half, x + 1, yy + half], fill=colour(res))
legend_h = int(26 * density)
img = Image.new("RGB", (faded.width, faded.height + legend_h), (255, 255, 255))
img.paste(faded, (0, legend_h))
ld = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(7 * density))
except OSError:
    font = ImageFont.load_default()
ld.text((8, 4), title, fill=(0, 0, 0), font=font)
x = 8
y = int(13 * density)
for label, key in [("dead (nothing)", "none"), ("space", "space"), ("delete", "delete"), ("return", "enter"),
                   ("shift, 123, emoji", "modifier"), ("bar, not tapped", "bar"), ("nav buttons", "hidden"), ("letters: a colour each", None)]:
    c = SPECIAL[key] if key else (120, 120, 120)
    if key:
        ld.rectangle([x, y + 2, x + int(8 * density), y + int(7 * density)], fill=c)
        x += int(10 * density)
    ld.text((x, y), label, fill=(0, 0, 0), font=font)
    x += int(ld.textlength(label, font=font)) + int(8 * density)
img.save(d / "map.png", optimize=True)
print(f"{d}: {len(taps)} taps, {sum(1 for t in taps if t[3] == 'none')} dead")
