"""One geometry table per profile, Dictus against Gboard (#370), from geometry.py's output.

usage: python3 -I geotable.py <dictus dir> <gboard dir>
Prints markdown: px, then dp. A Gboard that draws no key background (8.0) gets its touch areas.
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent


def geometry(d):
    out = subprocess.run([sys.executable, "-I", str(HERE / "geometry.py"), d], capture_output=True, text=True)
    return out.stdout if out.returncode == 0 else None


def parse(text, d):
    density = float(re.search(r"density ([\d.]+)", text)[1])
    rows = {}
    for m in re.finditer(r"^\| (\S+)(?: \(space\))? \| ([\d. ]+) \| ([^|]+) \| ([^|]+) \| (\d+) / (\d+)", text, re.M):
        spans = [tuple(map(int, s.split(".."))) for s in m[2].replace("..", " ").split() and m[2].split()]
        rows[m[1]] = {"spans": spans, "gap": spans[1][0] - spans[0][1] - 1, "margin": int(m[5])}
    col = re.search(r"^\| r \(x=\d+\) \| ([\d. ]+) \|", text, re.M)
    vspans = [tuple(map(int, s.split(".."))) for s in col[1].split()]
    get = lambda key: int(re.search(key + r": (\d+) px", text)[1])
    letter = rows["1"]["spans"][1]
    return {
        "density": density,
        "Letter key, width": letter[1] - letter[0] + 1,
        "Letter key, height": vspans[0][1] - vspans[0][0] + 1,
        "Gap between two keys": rows["1"]["gap"],
        "Gap between two rows": vspans[1][0] - vspans[0][1] - 1,
        "Screen edge to the first key": rows["1"]["margin"],
        "Row pitch (centre to centre)": vspans[1][0] - vspans[0][0],
        "Space bar, width": max(b - a + 1 for a, b in rows["4"]["spans"]),
        "Bar above the keys (toolbar, strip)": get("bar \\(window top to bar bottom\\)"),
        "Keys, top of row 1 to bottom of row 4": vspans[-1][1] - vspans[0][0] + 1,
        "Keyboard, top of the bar to the screen bottom": get("keyboard height, window top to screen bottom"),
        "Below row 4 to the screen bottom (navigation bar included)": get("below row 4 to the screen bottom"),
    }


def touch_areas(d):
    """Gboard 8 draws no key background: its key nodes, which are its touch areas."""
    d = Path(d)
    keys = {}
    for line in (d / "keys.tsv").read_text().splitlines():
        f = line.split("\t")
        if len(f) == 8 and f[0] == "-":
            keys.setdefault(f[1], tuple(map(int, f[2:6])))
    head = (d / "map.tsv").read_text().splitlines()[0]
    m = re.search(r"screen (\d+) x (\d+) px, density ([\d.]+), ime window \[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\], bar bottom (\d+)", head)
    H, density, top, bar = int(m[2]), float(m[3]), int(m[5]), int(m[8])
    a, z, q = keys["a"], keys["z"], keys["q"]
    space = keys.get("espace") or keys["space"]
    return {
        "density": density,
        "Letter key, width": z[2] - z[0],
        "Letter key, height": a[3] - a[1],
        "Gap between two keys": 0,
        "Gap between two rows": 0,
        "Screen edge to the first key": 0,
        "Row pitch (centre to centre)": q[1] - a[1],
        "Space bar, width": space[2] - space[0],
        "Bar above the keys (toolbar, strip)": bar - top,
        "Keys, top of row 1 to bottom of row 4": space[3] - a[1],
        "Keyboard, top of the bar to the screen bottom": H - top,
        "Below row 4 to the screen bottom (navigation bar included)": H - space[3],
    }


def measure(d):
    text = geometry(d)
    if text:
        return parse(text, d), "drawn"
    # Only Gboard's nodes are touch areas; Dictus's are its labels, so a failure there is an error.
    if "gboard" not in Path(d).name:
        raise SystemExit(f"{d}: geometry.py found no drawn keys")
    return touch_areas(d), "touch areas"


dm, dk = measure(sys.argv[1])
gm, gk = measure(sys.argv[2])
print(f"| | Dictus ({dk}) | Gboard ({gk}) |")
print("|---|---:|---:|")
for k in dm:
    if k == "density":
        continue
    print(f"| {k} | {dm[k]} ({dm[k] / dm['density']:.1f}) | {gm[k]} ({gm[k] / gm['density']:.1f}) |")
