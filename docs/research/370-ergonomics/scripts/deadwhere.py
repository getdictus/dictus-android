"""Where the replay's taps that typed nothing landed, against the measured key rectangles (#370).
usage: python3 -I deadwhere.py <taps.tsv> <rects.txt>"""
import sys
rects = [tuple(map(int, r.split(","))) for r in open(sys.argv[2]).read().strip().split(";")]
top = min(r[1] for r in rects); bottom = max(r[3] for r in rects)
left = min(r[0] for r in rects); right = max(r[2] for r in rects)
counts = {}
for line in open(sys.argv[1]).read().splitlines()[1:]:
    f = line.split("\t")
    if f[4] != "none":
        continue
    x, y = float(f[5]), float(f[6])
    if y < top: where = "above the top row"
    elif y >= bottom: where = "below the bottom row"
    elif x < left or x >= right: where = "screen-edge margin"
    else:
        inside_row = any(r[1] <= y < r[3] for r in rects)
        where = "between two keys of a row" if inside_row else "between two rows"
    key = f"{where} (aimed {'space' if f[3] == 'space' else 'letter'})"
    counts[key] = counts.get(key, 0) + 1
for k, v in sorted(counts.items(), key=lambda kv: -kv[1]):
    print(f"{v:3d}  {k}")
