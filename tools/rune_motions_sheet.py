#!/usr/bin/env python3
"""0.46.0 룬 동작 한눈에: 동작마다 한 줄 (3/4 · 오른쪽 모습 · 1인칭 손) — 열쇠 시각들을 골라 찍음.
  python3 tools/rune_motions_sheet.py <동작 ...> --out 그림.png [--n 8]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hands_preview as hp  # noqa: E402
import motion_preview as mp  # noqa: E402


def times(m, n):
    ks = sorted({k["t"] for k in m["keys"] if not k.get("gen") and 0 < k["t"] < 1})
    if len(ks) > n:
        step = len(ks) / n
        ks = [ks[int(i * step)] for i in range(n)]
    return ks


def row(name, m, n, cell=150):
    ts = times(m, n)
    W = cell * n + 220
    img = Image.new("RGB", (W, cell * 2 + 92), (20, 20, 26))
    d = ImageDraw.Draw(img)
    d.text((4, 2), "%s (%dt)" % (name, m["ticks"]), fill=(255, 220, 120))
    main = m.get("hands", {}).get("main", "weapon")
    hide = m.get("hide", [-1, -1])
    for i, t in enumerate(ts):
        pose = mp.evaluate(m, t)
        polys = mp.to_ours(mp.build(pose, weapon=main in ("weapon", "thrown", "swap") and not (hide[0] <= t <= hide[1])), pose)
        for j, v in enumerate(("34", "right")):
            im = mp.render(polys, v, 220).resize((cell, cell))
            img.paste(im, (220 + i * cell, 14 + j * cell))
        fr = hp.frame(m, t, 160, 90).resize((cell - 4, 76))
        img.paste(fr, (220 + i * cell + 2, 14 + 2 * cell))
        d.text((220 + i * cell + 3, 14), "t%.0f" % (t * m["ticks"]), fill=(255, 255, 255))
    return img


def main(argv):
    out = "sheet.png"
    n = 8
    names = []
    i = 0
    while i < len(argv):
        if argv[i] == "--out":
            out = argv[i + 1]
            i += 2
        elif argv[i] == "--n":
            n = int(argv[i + 1])
            i += 2
        else:
            names.append(argv[i])
            i += 1
    with open(mp.POSES, encoding="utf8") as f:
        motions = json.load(f)["motions"]
    rows = [row(nm, motions[nm], n) for nm in names]
    H = sum(r.height for r in rows)
    W = max(r.width for r in rows)
    sheet = Image.new("RGB", (W, H), (10, 10, 14))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
