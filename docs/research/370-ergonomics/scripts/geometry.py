"""Drawn key geometry from a keyboard capture (#370): key spans along each row and down each column.

A pixel is a key pixel when it is far from the keyboard background, read at the screen's left edge
beside the letter rows. Rows are scanned on several lines 0.12 to 0.24 row below the letter centres, columns on
several columns 0.2 to 0.32 pitch left of the key centres (away from Gboard's digit hints);
a pixel is a key pixel on any of them.

usage: python3 -I geometry.py <dir>   (reads keys.tsv, map.tsv header, screen.png; prints markdown)
"""
import re
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

d = Path(sys.argv[1])
head = (d / "map.tsv").read_text().splitlines()[0]
m = re.search(r"screen (\d+) x (\d+) px, density ([\d.]+), ime window \[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\], bar bottom (\d+), "
              r"pitch_x ([\d.]+), pitch_y ([\d.]+)", head)
W, H, density = int(m[1]), int(m[2]), float(m[3])
ime_top = int(m[5])
bar_bottom = int(m[8])
pitch_x, pitch_y = float(m[9]), float(m[10])
keys = {}
for line in (d / "keys.tsv").read_text().splitlines():
    f = line.split("\t")
    if len(f) == 8 and f[0] not in ("key", "-") and not f[0].startswith("#"):
        keys[f[0]] = (float(f[6]), float(f[7]))
im = Image.open(d / "screen.png").convert("RGB")
letters = {k: v for k, v in keys.items() if len(k) == 1}
row_ys = sorted({round(v[1]) for v in letters.values()})
space_y = round(keys["space"][1])
# The background: the screen's first two columns beside the letter rows, outside every key.
edge = [im.getpixel((x, y)) for y in row_ys for x in (0, 1)]
bg = Counter(edge).most_common(1)[0][0]


def is_key(p):
    return max(abs(a - b) for a, b in zip(p, bg)) > 10


def runs(values):
    out, start = [], None
    for i, v in enumerate(values + [False]):
        if v and start is None:
            start = i
        if not v and start is not None:
            out.append((start, i - 1))
            start = None
    return out


def merge(spans):
    """Joins spans 1-2 px apart: antialiased glyph edges inside a key, not gaps."""
    out = []
    for a, b in spans:
        if out and a - out[-1][1] <= 3:
            out[-1] = (out[-1][0], b)
        else:
            out.append((a, b))
    return out


def dp(px):
    return px / density


print(f"background {bg}, density {density}, screen {W} x {H} px ({dp(W):.0f} x {dp(H):.0f} dp)")
print()
print("| row | keys drawn (left..right px) | key width px (dp) | gap px (dp) | left / right margin px (dp) |")
print("|---|---|---|---|---|")
row_spans = {}
for name, y in [("1", row_ys[0]), ("2", row_ys[1]), ("3", row_ys[2]), ("4 (space)", space_y)]:
    # The union of several lines in the key's lower half: a corner or a glyph edge on one line
    # does not cut a key.
    ys = range(int(y + 0.12 * pitch_y), int(y + 0.24 * pitch_y), 2)
    spans = merge([s for s in runs([any(is_key(im.getpixel((x, yy))) for yy in ys) for x in range(W)]) if s[1] - s[0] > 2])
    spans = [s for s in spans if s[1] - s[0] > 4 * density]
    row_spans[name] = spans
    widths = [b - a + 1 for a, b in spans]
    gaps = [spans[i + 1][0] - spans[i][1] - 1 for i in range(len(spans) - 1)]
    left, right = spans[0][0], W - 1 - spans[-1][1]
    wtxt = ", ".join(sorted({f"{w}" for w in widths}, key=int))
    gtxt = ", ".join(sorted({f"{g}" for g in gaps}, key=int))
    print(f"| {name} | {' '.join(f'{a}..{b}' for a, b in spans)} | {wtxt} ({dp(min(widths)):.1f}-{dp(max(widths)):.1f}) | "
          f"{gtxt} ({dp(min(gaps)):.1f}-{dp(max(gaps)):.1f}) | {left} / {right} ({dp(left):.1f} / {dp(right):.1f}) |")
print()
print("| column at | key spans top..bottom px | key heights px (dp) | gaps between rows px (dp) |")
print("|---|---|---|---|")
col_info = []
for k in ("r", "g", "o"):
    cx, _ = letters[k]
    # The union of several columns left of the glyph, away from Gboard's top-right digit.
    xs = range(int(cx - 0.32 * pitch_x), int(cx - 0.2 * pitch_x), 2)
    x = xs[0]
    spans = merge([s for s in runs([any(is_key(im.getpixel((xx, y))) for xx in xs) for y in range(bar_bottom, H)]) if s[1] - s[0] > 2])
    # The four key rows; a dark navigation bar below them is not a key.
    spans = [s for s in spans if s[1] - s[0] > 8 * density][:4]
    spans = [(a + bar_bottom, b + bar_bottom) for a, b in spans]
    heights = [b - a + 1 for a, b in spans]
    gaps = [spans[i + 1][0] - spans[i][1] - 1 for i in range(len(spans) - 1)]
    col_info.append(spans)
    print(f"| {k} (x={x}) | {' '.join(f'{a}..{b}' for a, b in spans)} | {', '.join(map(str, heights))} "
          f"({', '.join(f'{dp(h):.1f}' for h in heights)}) | {', '.join(map(str, gaps))} ({', '.join(f'{dp(g):.1f}' for g in gaps)}) |")
spans = col_info[0]
print()
print(f"- keyboard window top (bar included): {ime_top} px; bar bottom: {bar_bottom} px")
print(f"- keyboard height, window top to screen bottom: {H - ime_top} px ({dp(H - ime_top):.1f} dp)")
print(f"- bar (window top to bar bottom): {bar_bottom - ime_top} px ({dp(bar_bottom - ime_top):.1f} dp)")
print(f"- keys, top of row 1 to bottom of row 4: {spans[-1][1] - spans[0][0] + 1} px ({dp(spans[-1][1] - spans[0][0] + 1):.1f} dp)")
print(f"- row pitch (letter centres): {pitch_y:.0f} px ({dp(pitch_y):.1f} dp); key pitch {pitch_x:.0f} px ({dp(pitch_x):.1f} dp)")
print(f"- below row 4 to the screen bottom: {H - 1 - spans[-1][1]} px ({dp(H - 1 - spans[-1][1]):.1f} dp)")
