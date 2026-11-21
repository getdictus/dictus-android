"""Touch-zone figures of one profile, Dictus against Gboard, from two touch maps (#370).

usage: python3 -I metrics.py <dictus dir> <gboard dir>   (each: map.tsv, keys.tsv, screen.png)
Prints a markdown table: px, then dp.
"""
import re
import sys
from collections import Counter
from pathlib import Path

from PIL import Image


def load(d):
    d = Path(d)
    text = (d / "map.tsv").read_text().splitlines()
    m = re.search(r"density ([\d.]+).*bar bottom (\d+)", text[0])
    density, bar = float(m[1]), int(m[2])
    lines = {}
    for row in text[2:]:
        f = row.split("\t")
        if len(f) == 4:
            lines.setdefault(f[0], []).append((int(f[1]), int(f[2]), f[3]))
    # The double space's period and the automatic capital after it (touchmap.py).
    for pts in lines.values():
        for i in range(1, len(pts)):
            if pts[i][2] == "edit" and pts[i - 1][2] == "space":
                pts[i] = (pts[i][0], pts[i][1], "space")
            elif pts[i][2] == "modifier" and pts[i - 1][2] == "space" and (i + 1 == len(pts) or pts[i + 1][2] == "none"):
                pts[i] = (pts[i][0], pts[i][1], "none")
    return density, bar, lines


def runs(pts):
    vertical = len({p[0] for p in pts}) == 1
    out = []
    for x, y, r in pts:
        c = y if vertical else x
        if out and out[-1][0] == r:
            out[-1][2] = c
        else:
            out.append([r, c, c])
    return out


def length(run):
    # Taps are 2 px apart: a run of n taps spans about 2n px.
    return abs(run[2] - run[1]) + 2


def find(lines, prefix):
    return next((v for k, v in lines.items() if k.startswith(prefix)), None)


def dead_between(pts, before, after):
    """The dead run between a run of `before` and a run of `after`, or 0 if they touch."""
    rs = runs(pts)
    for i in range(len(rs) - 1):
        if rs[i][0] == before:
            j = i + 1
            dead = 0
            while j < len(rs) and rs[j][0] == "none":
                dead += length(rs[j])
                j += 1
            if j < len(rs) and rs[j][0] == after:
                return dead
    return None


def first_dead(pts):
    rs = runs(pts)
    # A tap on the screen's very edge can draw the system back-gesture arrow over the keys,
    # which the map reads as keys that changed: nothing was typed there.
    if len(rs) > 1 and rs[0][0] == "modifier" and length(rs[0]) <= 2 and rs[1][0] == "none":
        rs = [["none", rs[0][1], rs[1][2]]] + rs[2:]
    return length(rs[0]) if rs and rs[0][0] == "none" else 0


def drawn_bottom(d, x, y):
    """The drawn key's last row of pixels under (x, y) + 1, read on the capture."""
    im = Image.open(Path(d) / "screen.png").convert("RGB")
    # The key's own colour at the middle of its touch area, away from its label (Gboard 8 draws
    # only a pill there).
    key = Counter(im.getpixel((x, r)) for r in range(y - 4, y + 5)).most_common(1)[0][0]
    yy = y
    while yy + 1 < im.height and max(abs(a - b) for a, b in zip(im.getpixel((x, yy + 1)), key)) <= 10:
        yy += 1
    return yy + 1


def metrics(d):
    density, bar, lines = load(d)
    out = {}
    top = lines.get("top:e") or find(lines, "top:")
    rs = runs(top)
    out["Row 1 to the bar above it: dead"] = sum(length(r) for r in rs if r[0] == "none")
    col = lines["column:r"]
    out["Row 1 to row 2 (column r): dead"] = dead_between(col, "r", "f")
    sv = lines["spaceV:centre"]
    rs = runs(sv)
    letter = rs[0][0]
    out["Row 3 to the space bar: dead"] = dead_between(sv, letter, "space")
    sp = [r for r in rs if r[0] == "space"]
    nav = next((r[1] for line, pts in lines.items() if line.startswith("bottomV") for r in runs(pts)
                if r[0] in ("hidden", "system")), None)
    below = sp[-1][2] + 2 if sp else None
    out["Space bar: touch area below the drawn key (Gboard 8: its pill)"] = below - drawn_bottom(d, (sv[0][0] + lines["spaceV:left"][0][0]) // 2, (sp[-1][1] + sp[-1][2]) // 2) if sp else None
    out["Below the space bar to the screen bottom: types nothing"] = sum(length(r) for r in rs if r[0] == "none" and r[1] > sp[-1][2])
    for side in ("left", "right"):
        pts = lines.get(f"spaceH:{side}:mid")
        r = runs(pts)
        neighbour = r[0][0]
        out[f"Space bar, {side} side (mid height): dead"] = dead_between(pts, neighbour, "space")
        pts = lines.get(f"spaceH:{side}:top")
        r = runs(pts)
        out[f"Space bar, {side} side (top corner): dead"] = dead_between(pts, r[0][0], "space")
    edges = [k for k in lines if k.startswith("edge:") and k.endswith(":mid")]
    names = {}
    for k in edges:
        label = k.split(":")[1]
        pts = lines[k]
        left = pts[0][0] == 0
        if len(label) == 1 and label.isalpha():
            name = label
        else:
            name = "shift" if left else "delete"
        names[name] = first_dead(pts)
    for name in ("a", "q", "p", "m", "shift", "delete"):
        if name in names:
            out[f"Screen edge beside {name}: dead"] = names[name]
    return density, out


da, ma = metrics(sys.argv[1])
dg, mg = metrics(sys.argv[2])
print("| Zone | Dictus px (dp) | Gboard px (dp) |")
print("|---|---:|---:|")
for k in ma:
    a, g = ma[k], mg.get(k)

    def fmt(v, dens):
        return "n/a" if v is None else f"{v} ({v / dens:.1f})"
    print(f"| {k} | {fmt(a, da)} | {fmt(g, dg)} |")
