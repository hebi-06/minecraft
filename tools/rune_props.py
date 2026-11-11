#!/usr/bin/env python3
"""2026-11-12 룬 전용 소품 (사용자: 도끼 룬의 3D 도끼는 "룬 전용 도끼 하나" — 룬 등급 · 손에 든 무기와 상관없이, 검 · 망치 룬도 같은 규칙).
룬이 불러내는 마력 무기 — 무기 디자인 (사용자 Claude Design) 과 따로. 자바 아이템 모델 (상자) + 그림 (64 × 64, 빛이 맥동하는 프레임 + .png.mcmeta) + 아이템 정의.

  python3 tools/rune_props.py              # 씀 (assets/rpg/models/item/vfx/rune_<물건>.json · textures/item/vfx/rune_<물건>.png · items/vfx/rune_<물건>.json)
  python3 tools/rune_props.py --check      # 쓸 것이 없는지 (CI)
  python3 tools/rune_props.py --preview <폴더>   # 여러 방향에서 본 그림 (모델 · 그림 확인)

축 (RuneAxe.axeRot 와 같음): 자루 = 모델 +Y (끝 → 머리), 날 = 모델 −Z, 넓은 면 = ±X (두께 가운데 x 8).
"""
import argparse
import io
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
TEX = 64          # 그림 한 변 (텍셀) — 모델 1 픽셀 = 2 텍셀
UVK = 16.0 / TEX  # 텍셀 → UV
FRAMES = 6        # 빛 맥동 프레임 (위아래로 이어 붙임)
FRAMETIME = 2

# 도끼 룬 색 (rules.json RUNE.color.axe — 빛 · 속빛)
GLOW = (0.95, 0.20, 0.14)
CORE = (1.0, 0.78, 0.62)


def rgb(c, a=255):
    return (int(round(c[0] * 255)), int(round(c[1] * 255)), int(round(c[2] * 255)), a)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


# ------------------------------------------------------------------ 도끼

# 날 옆면 그림판 (모델 z · y 범위 — 날 상자들의 ±X 면이 이 그림의 제 자리를 씀)
BLADE_Z = (-3.0, 6.5)
BLADE_Y = (4.5, 26.5)
ART = (0, 0)  # 그림판 왼쪽 위 (텍셀) — 19 × 44
# 재질 견본 (8 × 8 텍셀) 자리
SW = {"haft": (24, 0), "band": (32, 0), "gem": (40, 0), "metal": (48, 0), "edge": (56, 0), "blade": (24, 8)}

AXE = [
    # 이름, 재질, from, to — 초승달 날 (넓게 휜 날 끝 · 위아래로 뻗은 뿔) · 흑요석 자루 · 빛 띠 셋 · 보석 자루 끝 · 위 창끝 · 뒤 가시
    ("haft", "haft", (7.25, -5.0, 7.25), (8.75, 14.0, 8.75)),
    ("band0", "band", (7.0, -3.0, 7.0), (9.0, -2.0, 9.0)),
    ("band1", "band", (7.0, 1.5, 7.0), (9.0, 2.5, 9.0)),
    ("band2", "band", (7.0, 6.0, 7.0), (9.0, 7.0, 9.0)),
    ("pommel", "gem", (6.75, -7.0, 6.75), (9.25, -5.0, 9.25)),
    ("pommel_tip", "gem", (7.4, -8.0, 7.4), (8.6, -7.0, 8.6)),
    ("socket", "metal", (6.6, 10.0, 6.4), (9.4, 21.0, 9.9)),
    ("spike", "edge", (7.4, 21.0, 7.4), (8.6, 24.0, 8.6)),
    ("spike_tip", "edge", (7.7, 24.0, 7.7), (8.3, 26.0, 8.3)),
    ("back", "metal", (7.4, 13.5, 9.9), (8.6, 17.5, 12.25)),
    ("back_tip", "edge", (7.65, 14.5, 12.25), (8.35, 16.5, 14.25)),
    ("blade1", "art", (7.1, 11.0, 5.0), (8.9, 20.0, 6.4)),
    ("blade2", "art", (7.2, 9.5, 3.5), (8.8, 21.5, 5.0)),
    ("blade3", "art", (7.3, 8.0, 2.0), (8.7, 23.0, 3.5)),
    ("blade4", "art", (7.4, 7.0, 0.5), (8.6, 24.0, 2.0)),
    ("blade5", "art", (7.45, 6.5, -1.0), (8.55, 24.5, 0.5)),
    ("rim", "art", (7.6, 7.0, -2.2), (8.4, 24.0, -1.0)),
    ("horn_top", "art", (7.6, 24.5, -3.0), (8.4, 26.5, -0.5)),
    ("horn_low", "art", (7.6, 4.5, -3.0), (8.4, 6.5, -0.5)),
]


def blade_art(frame):
    """날 옆면 (z · y — 텍셀): 붉은 마력 (자루 쪽 짙게 → 날 끝 밝게) · 룬 글자 (속빛 — 맥동) · 날 끝 흰 빛. 날 상자 밖은 투명"""
    w = int((BLADE_Z[1] - BLADE_Z[0]) * 2)
    h = int((BLADE_Y[1] - BLADE_Y[0]) * 2)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    # 날 상자들의 (z, y) 덮는 곳
    boxes = [(e[2][2], e[3][2], e[2][1], e[3][1]) for e in AXE if e[1] == "art"]
    pulse = 0.5 + 0.5 * math.sin(frame / FRAMES * math.pi * 2)
    rnd = random.Random(7)
    for ty in range(h):
        for tz in range(w):
            z = BLADE_Z[0] + (tz + 0.5) / 2
            y = BLADE_Y[1] - (ty + 0.5) / 2
            if not any(z0 <= z <= z1 and y0 <= y <= y1 for z0, z1, y0, y1 in boxes):
                continue
            t = 1 - (z - BLADE_Z[0]) / (BLADE_Z[1] - BLADE_Z[0])  # 0 = 자루 쪽 · 1 = 날 끝
            c = mix((0.40, 0.03, 0.05), GLOW, min(1, 0.2 + t * 1.0))
            if t > 0.78:
                c = mix(c, (1.0, 0.95, 0.88), min(1, (t - 0.78) / 0.14))
            n = rnd.uniform(-0.06, 0.06)
            c = tuple(max(0, min(1, v + n)) for v in c)
            a = 228 if t < 0.78 else 255
            px[tz, ty] = rgb(c, a)
    # 룬 글자 (세로로 셋 — 날 가운데)
    d = ImageDraw.Draw(im)
    gl = mix(GLOW, CORE, 0.55 + 0.45 * pulse)
    col = rgb(gl, 255)
    cz = int((2.6 - BLADE_Z[0]) * 2)  # 글자 가운데 z ≈ 2.6 (날 가운데)
    for i, gy in enumerate((20.5, 15.5, 10.5)):
        ty = int((BLADE_Y[1] - gy) * 2)
        x0, y0 = cz - 3, ty - 4
        if i == 0:  # ᚦ 꼴 (가시)
            d.line([(x0 + 1, y0), (x0 + 1, y0 + 8)], fill=col)
            d.line([(x0 + 1, y0 + 2), (x0 + 5, y0 + 4), (x0 + 1, y0 + 6)], fill=col)
        elif i == 1:  # ᛉ 꼴 (보호)
            d.line([(x0 + 3, y0), (x0 + 3, y0 + 8)], fill=col)
            d.line([(x0, y0), (x0 + 3, y0 + 3), (x0 + 6, y0)], fill=col)
        else:  # ᛟ 꼴 (유산)
            d.line([(x0 + 3, y0), (x0 + 6, y0 + 3), (x0 + 3, y0 + 6), (x0, y0 + 3), (x0 + 3, y0)], fill=col)
            d.line([(x0 + 3, y0 + 6), (x0, y0 + 8)], fill=col)
            d.line([(x0 + 3, y0 + 6), (x0 + 6, y0 + 8)], fill=col)
    # 글자 둘레 빛 (한 텍셀 — 맥동)
    halo = rgb(mix(GLOW, CORE, 0.3 * pulse), 235)
    base = im.copy()
    bp = base.load()
    for ty in range(h):
        for tz in range(w):
            if bp[tz, ty][3] == 0 or bp[tz, ty] == col:
                continue
            if any(0 <= tz + dz < w and 0 <= ty + dy < h and bp[tz + dz, ty + dy] == col for dz, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[tz, ty] = halo
    return im


def swatch(kind, frame):
    """재질 견본 8 × 8"""
    pulse = 0.5 + 0.5 * math.sin(frame / FRAMES * math.pi * 2)
    im = Image.new("RGBA", (8, 8))
    px = im.load()
    rnd = random.Random(hash(kind) & 0xFFFF)
    for y in range(8):
        for x in range(8):
            n = rnd.uniform(-0.05, 0.05)
            if kind == "haft":  # 검은 흑요석 · 붉은 결
                c = (0.10 + n, 0.05 + n * 0.5, 0.06 + n * 0.5)
                if (x + y * 3) % 7 == 0:
                    c = mix(c, GLOW, 0.45 + 0.25 * pulse)
                a = 255
            elif kind == "band":  # 빛나는 띠 (가운데 줄 밝게)
                c = mix(GLOW, CORE, (0.25 if y in (0, 7) else 0.65) * (0.7 + 0.3 * pulse))
                a = 255
            elif kind == "gem":
                r = math.hypot(x - 3.5, y - 3.5) / 5
                c = mix(CORE, GLOW, min(1, r)) if r < 1 else GLOW
                c = mix(c, (1, 1, 1), 0.25 * pulse * (1 - min(1, r)))
                a = 255
            elif kind == "metal":  # 검은 쇠 · 붉은 테
                c = (0.16 + n, 0.12 + n, 0.14 + n)
                if x in (0, 7) or y in (0, 7):
                    c = mix(c, GLOW, 0.55 + 0.2 * pulse)
                a = 255
            elif kind == "edge":  # 흰 빛 → 속빛
                c = mix((1.0, 0.96, 0.9), CORE, rnd.uniform(0, 0.35))
                a = 255
            else:  # blade — 날 상자의 위 · 아래 · 안쪽 면
                c = mix((0.5, 0.06, 0.06), GLOW, 0.6 + 0.2 * pulse)
                a = 230
            px[x, y] = rgb(tuple(max(0, min(1, v)) for v in c), a)
    return im


def axe_texture():
    frames = []
    for f in range(FRAMES):
        im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        im.paste(blade_art(f), ART)
        for k, (sx, sy) in SW.items():
            im.paste(swatch(k, f), (sx, sy))
        frames.append(im)
    sheet = Image.new("RGBA", (TEX, TEX * FRAMES))
    for i, im in enumerate(frames):
        sheet.paste(im, (0, i * TEX))
    return sheet


def face_uv(name, f, t, mat):
    """면 UV (0 ~ 16): 날 상자의 ±X 면 = 그림판의 제 자리 (west = u 가 z 를 따라, east = 뒤집음), 나머지 = 재질 견본"""
    if mat == "art":
        if name in ("west", "east"):
            u0 = ART[0] + (f[2] - BLADE_Z[0]) * 2
            u1 = ART[0] + (t[2] - BLADE_Z[0]) * 2
            v0 = ART[1] + (BLADE_Y[1] - t[1]) * 2
            v1 = ART[1] + (BLADE_Y[1] - f[1]) * 2
            if name == "east":
                u0, u1 = u1, u0
            return [round(u0 * UVK, 4), round(v0 * UVK, 4), round(u1 * UVK, 4), round(v1 * UVK, 4)]
        mat = "blade"
    sx, sy = SW[mat]
    dims = {"north": (t[0] - f[0], t[1] - f[1]), "south": (t[0] - f[0], t[1] - f[1]), "east": (t[2] - f[2], t[1] - f[1]),
            "west": (t[2] - f[2], t[1] - f[1]), "up": (t[0] - f[0], t[2] - f[2]), "down": (t[0] - f[0], t[2] - f[2])}[name]
    w = min(8.0, max(1.0, dims[0] * 2))
    h = min(8.0, max(1.0, dims[1] * 2))
    return [round(sx * UVK, 4), round(sy * UVK, 4), round((sx + w) * UVK, 4), round((sy + h) * UVK, 4)]


def axe_model():
    els = []
    for name, mat, f, t in AXE:
        faces = {}
        for fn in ("north", "south", "east", "west", "up", "down"):
            faces[fn] = {"uv": face_uv(fn, f, t, mat), "texture": "#0"}
        els.append({"name": name, "from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "light_emission": 15, "faces": faces})
    return {"credit": "rune_props.py — 룬 전용 도끼 (2026-11-12)", "textures": {"0": "rpg:item/vfx/rune_axe", "particle": "rpg:item/vfx/rune_axe"},
            "elements": els}


def bounds(parts):
    lo = np.array([min(p[2][i] for p in parts) for i in range(3)])
    hi = np.array([max(p[3][i] for p in parts) for i in range(3)])
    return lo, hi


# ------------------------------------------------------------------ 쓰기 · 미리보기

def outputs():
    tex = axe_texture()
    buf = io.BytesIO()
    tex.save(buf, "PNG", optimize=True)
    files = {
        os.path.join(RES, "textures", "item", "vfx", "rune_axe.png"): buf.getvalue(),
        os.path.join(RES, "textures", "item", "vfx", "rune_axe.png.mcmeta"): (json.dumps({"animation": {"frametime": FRAMETIME, "interpolate": True}}, indent=2) + "\n").encode(),
        os.path.join(RES, "models", "item", "vfx", "rune_axe.json"): (json.dumps(axe_model(), ensure_ascii=False, indent=1) + "\n").encode(),
        os.path.join(RES, "items", "vfx", "rune_axe.json"): (json.dumps({"model": {"type": "minecraft:model", "model": "rpg:item/vfx/rune_axe"}}, indent=2) + "\n").encode(),
    }
    return files, tex


def preview(out_dir, tex):
    """세 방향 (옆 3/4 · 넓은 면 · 날 쪽) — 첫 프레임"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import bedrock_preview as bp
    frame = tex.crop((0, 0, TEX, TEX))
    px = frame.load()
    qs = []
    for name, mat, f, t in AXE:
        x0, y0, z0 = f
        x1, y1, z1 = t
        corners = {
            "north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
            "south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
            "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
            "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
            "up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
            "down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)),
        }
        for fn, (a, b, c) in corners.items():
            u0, v0, u1, v1 = (v / UVK for v in face_uv(fn, f, t, mat))
            a, b, c = (np.array(p, float) / 16 for p in (a, b, c))
            nu = max(1, int(round(abs(u1 - u0))))
            nv = max(1, int(round(abs(v1 - v0))))
            du, dv = (b - a) / nu, (c - a) / nv
            for j in range(nv):
                for i in range(nu):
                    tu = u0 + (u1 - u0) * (i + 0.5) / nu
                    tv = v0 + (v1 - v0) * (j + 0.5) / nv
                    col = px[min(TEX - 1, int(tu)), min(TEX - 1, int(tv))]
                    if col[3] == 0:
                        continue
                    p0 = a + du * i + dv * j
                    pts = np.array([p0, p0 + du, p0 + du + dv, p0 + dv])
                    n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
                    ln = np.linalg.norm(n)
                    if ln < 1e-12:
                        continue
                    qs.append((pts, (col[0], col[1], col[2], 254), -n / ln))  # 빛 (그늘 없음)
    allp = np.array([p for q in qs for p in q[0]])
    c = (allp.min(0) + allp.max(0)) / 2
    ext = float(max(allp.max(0) - allp.min(0))) * 1.5
    ims = []
    for dv in ((1.0, 0.3, -0.5), (1.0, 0.05, 0.0), (0.3, 0.25, -1.0)):
        dv = np.array(dv, float)
        dv /= np.linalg.norm(dv)
        ims.append(bp.render(qs, c + dv * ext * 1.6, c, bg=(40, 44, 56)))
    sh = Image.new("RGB", (sum(i.size[0] for i in ims), ims[0].size[1]))
    x = 0
    for i in ims:
        sh.paste(i, (x, 0))
        x += i.size[0]
    os.makedirs(out_dir, exist_ok=True)
    p = os.path.join(out_dir, "rune_axe.png")
    sh.save(p)
    tex.crop((0, 0, TEX, TEX)).resize((TEX * 6, TEX * 6), Image.NEAREST).save(os.path.join(out_dir, "rune_axe_tex.png"))
    return p


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview")
    a = ap.parse_args()
    files, tex = outputs()
    lo, hi = bounds(AXE)
    assert lo.min() >= -16 and hi.max() <= 32, (lo, hi)
    if a.preview:
        print(preview(a.preview, tex))
        return
    stale = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            stale.append(os.path.relpath(path, ROOT))
            if not a.check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if a.check:
        if stale:
            print("rune_props: 다시 만들어야 함:", ", ".join(stale))
            sys.exit(1)
        print("rune_props: ok")
    else:
        print("rune_props: 씀", len(stale), "· 도끼 길이 %.1f px (y %.1f ~ %.1f)" % (hi[1] - lo[1], lo[1], hi[1]))


if __name__ == "__main__":
    main()
