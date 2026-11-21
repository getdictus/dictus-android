"""Dictus and Gboard touch maps of one profile side by side (#370).

usage: python3 -I sidebyside.py <dictus map.png> <gboard map.png> <out.png>
"""
import sys

from PIL import Image

a = Image.open(sys.argv[1])
b = Image.open(sys.argv[2])
h = max(a.height, b.height)
gap = 24
out = Image.new("RGB", (a.width + b.width + gap, h), (255, 255, 255))
out.paste(a, (0, h - a.height))
out.paste(b, (a.width + gap, h - b.height))
# Small enough for a README, wide enough to read a 2 px line.
scale = min(1.0, 1600 / out.width)
if scale < 1:
    out = out.resize((int(out.width * scale), int(out.height * scale)), Image.LANCZOS)
out.save(sys.argv[3], optimize=True)
