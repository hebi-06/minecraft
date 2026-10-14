#!/usr/bin/env python3
"""무기 디자인 미리보기 그림 (tools/weapon_art/<디자인>.geo.json + .png → 옆 · 앞 · 비스듬히 세 모습, 손잡이 자리 표시).
디자인을 넘겨 드리거나 다듬어 받은 디자인을 확인할 때 씀 (게임 화면은 사진첩 weapons12).

  python3 tools/weapon_preview.py <디자인> [...] --out <폴더>
  python3 tools/weapon_preview.py --sheet <출력.png> <디자인> [...]     # 여러 디자인을 한 장에 (비스듬히 모습만)
"""
import argparse
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
BG = (34, 36, 44, 255)
GRIP = (8.0, 18.0)  # 손이 잡는 자리 (y — 애드온 무기 손잡이)

# 면 → (법선, 꼭짓점 4개를 고르는 함수)
FACE_NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (-1, 0, 0), "west": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def font(size):
    try:
        return ImageFont.truetype(FONT, size, index=1)
    except OSError:
        return ImageFont.load_default()


def corners(c, face):
    (x0, y0, z0), (x1, y1, z1) = c.lo, c.hi
    return {
        "north": [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        "south": [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        "east": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "west": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }[face]


def basis(f):
    """카메라: f = 보는 방향 (three.js 좌표 — 베드락 x 를 뒤집은 오른손 좌표, Blockbench 화면과 같음) → (오른쪽, 위, 앞)"""
    n = math.sqrt(sum(v * v for v in f))
    f = tuple(v / n for v in f)
    up = (0.0, 1.0, 0.0)
    r = (f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0])
    rn = math.sqrt(sum(v * v for v in r)) or 1.0
    r = tuple(v / rn for v in r)
    u = (r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    return r, u, f


def view(p, cam):
    """베드락 좌표 p → (화면 x, 화면 y, 깊이)"""
    r, u, f = cam
    q = (-p[0], p[1], p[2])
    return (sum(a * b for a, b in zip(q, r)), sum(a * b for a, b in zip(q, u)), sum(a * b for a, b in zip(q, f)))


def face_color(m, c, face):
    r = c.faces.get(face)
    if r is None:
        return None
    col = m.face_pixel(r, 0.5, 0.5)
    if col[3] == 0:
        return None
    return col


def render(m, f, scale, pad=24, grip=True, label=None, min_w=200):
    """모델 한 모습 (정사영, f = 보는 방향): 보이는 면을 먼 것부터 — 색은 면 가운데 픽셀 · 빛나는 칸은 밝게"""
    cam = basis(f)
    polys = []
    for c in m.cubes:
        for face, n in FACE_NORMAL.items():
            nq = (-n[0], n[1], n[2])
            if sum(a * b for a, b in zip(nq, cam[2])) >= -1e-6:  # 카메라 쪽을 향하지 않으면 안 보임
                continue
            col = face_color(m, c, face)
            if col is None:
                continue
            pts = [view(p, cam) for p in corners(c, face)]
            depth = sum(p[2] for p in pts) / 4
            shade = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.82, "east": 0.68, "west": 0.68}[face]
            r, g, b, a = col
            if a < 255:  # 빛나는 칸
                r, g, b = (min(255, int(v * 1.15 + 25)) for v in (r, g, b))
                shade = max(shade, 0.95)
            polys.append((depth, [(p[0], p[1]) for p in pts], (int(r * shade), int(g * shade), int(b * shade), 255)))
    xs = [p[0] for _, pts, _ in polys for p in pts]
    ys = [p[1] for _, pts, _ in polys for p in pts]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    mw = int((x1 - x0) * scale) + 2 * pad
    w = max(mw, min_w)
    h = int((y1 - y0) * scale) + 2 * pad + (28 if label else 0)
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    top = 28 if label else 0
    left = pad + (w - mw) // 2

    def to_px(p):
        return (left + (p[0] - x0) * scale, top + pad + (y1 - p[1]) * scale)

    if grip:  # 손잡이 자리 (손이 잡는 y 범위) — 무기 뒤에 옅은 띠
        a, b = to_px(view((-6, GRIP[0], 0), cam)), to_px(view((-6, GRIP[1], 0), cam))
        d.line([a, b], fill=(255, 210, 90, 255), width=max(6, int(4.5 * scale)))
    for _, pts, col in sorted(polys, key=lambda t: -t[0]):
        d.polygon([to_px(p) for p in pts], fill=col, outline=tuple(max(0, v - 40) for v in col[:3]) + (255,))
    if label:
        d.text((8, 4), label, font=font(18), fill=(240, 240, 240, 255))
    return img


def preview(design, art=ART):
    m = geo_tex.load(design, os.path.join(art, f"{design}.geo.json"), os.path.join(art, f"{design}.png"))
    length = max(c.hi[1] for c in m.cubes) - min(c.lo[1] for c in m.cubes)
    scale = max(4.0, min(9.0, 520 / max(1.0, length)))
    views = [render(m, (1, 0, 0), scale, label="옆 (+x 쪽에서)"), render(m, (0, 0, 1), scale, label="앞 (날 쪽 -z 에서)"),
             render(m, (0.55, -0.35, 0.75), scale, label="비스듬히")]
    tex = m.tex
    tscale = max(1, int(160 / max(tex.size)))
    tex_img = Image.new("RGBA", (max(tex.size[0] * tscale + 16, 290), tex.size[1] * tscale + 44), BG)
    td = ImageDraw.Draw(tex_img)
    td.text((8, 4), f"텍스처 {tex.size[0]}×{tex.size[1]}" + (" (팔레트 — 칸 = 색)" if m.palette else ""), font=font(16), fill=(240, 240, 240, 255))
    checker = Image.new("RGBA", (tex.size[0] * tscale, tex.size[1] * tscale), (60, 60, 70, 255))
    big = tex.resize((tex.size[0] * tscale, tex.size[1] * tscale), Image.NEAREST)
    checker.alpha_composite(big)
    tex_img.alpha_composite(checker, (8, 32))
    parts = views + [tex_img]
    W = sum(p.size[0] for p in parts) + 12 * (len(parts) + 1)
    H = max(p.size[1] for p in parts) + 70
    sheet = Image.new("RGBA", (W, H), BG)
    sd = ImageDraw.Draw(sheet)
    sd.text((12, 8), design, font=font(24), fill=(255, 228, 150, 255))
    sd.text((12, 38), f"상자 {len(m.cubes)}개 · 노란 띠 = 손이 잡는 자리 (y {GRIP[0]:g} ~ {GRIP[1]:g}) · 위 = 무기 끝 (+y)", font=font(15),
            fill=(200, 200, 210, 255))
    x = 12
    for p in parts:
        sheet.alpha_composite(p, (x, 64))
        x += p.size[0] + 12
    return sheet, m


def sheet_of(designs, out, cols=5, art=ART, labels=None):
    imgs = []
    for d in designs:
        m = geo_tex.load(d, os.path.join(art, f"{d}.geo.json"), os.path.join(art, f"{d}.png"))
        imgs.append(render(m, (0.55, -0.35, 0.75), 4.2, label=(labels or {}).get(d, d)))
    cw = max(i.size[0] for i in imgs)
    ch = max(i.size[1] for i in imgs)
    rows = (len(imgs) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch), BG)
    for i, im in enumerate(imgs):
        sheet.alpha_composite(im, ((i % cols) * cw + (cw - im.size[0]) // 2, (i // cols) * ch + (ch - im.size[1])))
    sheet.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("designs", nargs="+")
    ap.add_argument("--out", default=".")
    ap.add_argument("--sheet")
    ap.add_argument("--art", default=ART)
    a = ap.parse_args()
    if a.sheet:
        print(sheet_of(a.designs, a.sheet, art=a.art))
        return
    os.makedirs(a.out, exist_ok=True)
    for d in a.designs:
        img, m = preview(d, a.art)
        p = os.path.join(a.out, f"{d}_preview.png")
        img.save(p)
        print(p, img.size, *(f"경고: {w}" for w in m.warnings))


if __name__ == "__main__":
    main()
