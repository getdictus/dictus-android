"""Drawn key rectangles from a keyboard capture, for KeyboardReplay's `rects` (#370).

usage: python3 -I rects.py <screen.png> <keys.tsv>  > rects.txt
Rows: the letter rows and the space bar's row from keys.tsv; each row is scanned 0.2 row above its
centre for key spans, and down a column inside its first key for its top and bottom.
"""
import re
import sys
from collections import Counter

from PIL import Image

im = Image.open(sys.argv[1]).convert("RGB")
text = open(sys.argv[2]).read()
pitch_y = float(re.search(r"pitch_y ([\d.]+)", text)[1])
keys = {}
for line in text.splitlines():
    f = line.split("\t")
    if len(f) == 8 and f[0] not in ("key", "-") and not f[0].startswith("#"):
        keys[f[0]] = (float(f[6]), float(f[7]))
W, H = im.size
row_ys = sorted({round(v[1]) for k, v in keys.items() if len(k) == 1}) + [round(keys["space"][1])]
edge = [im.getpixel((x, y)) for y in row_ys[:3] for x in (0, 1)]
bg = Counter(edge).most_common(1)[0][0]


def is_key(p):
    return max(abs(a - b) for a, b in zip(p, bg)) > 10


def runs(values, offset=0):
    out, start = [], None
    for i, v in enumerate(values + [False]):
        if v and start is None:
            start = i
        if not v and start is not None:
            out.append((start + offset, i - 1 + offset))
            start = None
    return out


rects = []
for y in row_ys:
    yy = int(y - 0.2 * pitch_y)
    spans = [s for s in runs([is_key(im.getpixel((x, yy))) for x in range(W)]) if s[1] - s[0] > 10]
    x = spans[0][0] + (spans[0][1] - spans[0][0]) // 5  # clear of the rounded corner and of the glyph
    top = next(r for r in runs([is_key(im.getpixel((x, v))) for v in range(H)]) if r[0] <= y <= r[1])
    for a, b in spans:
        rects.append(f"{a},{top[0]},{b + 1},{top[1] + 1}")
print(";".join(rects))
