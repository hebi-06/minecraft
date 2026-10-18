#!/usr/bin/env python3
"""0.18.0 대지 가르기의 대지의 대검 — 아이템 표시 엔티티 (item_display) 로 그리는 3D 모델 · 텍스처 · 아이템 정의.

  python3 tools/terra_blade.py            # 모델 · 텍스처 · 아이템 정의를 씀
  python3 tools/terra_blade.py --preview  # 모델을 앞 · 옆 · 비스듬히 그려 scratch 그림으로 (확인용)

만들어지는 것
- src/main/resources/assets/rpg/items/fx_terra_blade.json          아이템 정의 (rpg:fx_terra_blade — ItemStack 의 item_model 로 씀)
- src/main/resources/assets/rpg/models/item/fx/terra_blade.json    모델 (elements — 돌 칼날 · 호박빛 맥 (light_emission) · 돌 코등이 · 가죽 손잡이)
- src/main/resources/assets/rpg/textures/item/fx/terra_blade.png   텍스처 128×128 (UV 0 ~ 16 = 그림 전체 — 1 UV = 8 픽셀)

모델 좌표 (자바 아이템 모델 — 한 블록 = 16, 상자는 -16 ~ 32 안): 칼끝 = +y, 칼날 너비 = x (양날 ±x), 두께 = z (넓은 면 ±z).
손잡이 가운데 (8, 8, 8) = 모델 가운데 — 표시 엔티티는 이 점을 엔티티 자리에 두고 돌림 (RuneTerra: 이 점을 축으로 내려침).
칼날 y 12 ~ 32 (20 = 표시 크기 7 이면 8.75칸), 코등이 너비 14, 손잡이 · 머리 y 0.4 ~ 9.6.
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
NAME = "terra_blade"
TEXEL = 8  # 1 UV = 8 픽셀 (128 / 16)

# 텍스처 영역 (픽셀 x0, y0, x1, y1)
REG = {
    "face": (0, 0, 64, 128),     # 칼날 넓은 면 (돌 — 위 = 칼끝)
    "side": (64, 0, 80, 128),    # 칼날 두께 면
    "guard": (80, 0, 128, 32),   # 코등이 (현무암 · 호박빛 알갱이)
    "rock": (80, 32, 128, 64),   # 거친 바위 (머리 · 코등이 끝 · 돌기)
    "grip": (80, 64, 96, 128),   # 가죽 손잡이
    "glow": (96, 64, 112, 128),  # 호박빛 맥 (빛남)
    "gem": (112, 64, 128, 80),   # 보석 (빛남)
    "hot": (112, 80, 128, 96),   # 가장 뜨거운 빛
    "edge": (112, 96, 128, 128),  # 벼린 날 (밝은 돌)
}


# ------------------------------------------------------------ 텍스처

def noise(rng, w, h, cell):
    """값 노이즈 (cell 픽셀마다 무작위 → 이중 선형)"""
    gw, gh = w // cell + 2, h // cell + 2
    g = rng.random((gh, gw))
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


def fbm(rng, w, h, cells=(16, 8, 4, 2), weights=(0.5, 0.25, 0.15, 0.1)):
    out = np.zeros((h, w))
    for c, k in zip(cells, weights):
        out += noise(rng, w, h, c) * k
    return out / sum(weights)


def mix(c0, c1, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(c0, float) * (1 - t) + np.array(c1, float) * t


def cracks(rng, w, h, n, length, dark):
    """어두운 금 (무작위 걸음) — 0 ~ 1 (1 = 금)"""
    m = np.zeros((h, w))
    for _ in range(n):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        ang = rng.uniform(0, math.tau)
        for _ in range(length):
            ix, iy = int(x) % w, int(y) % h
            m[iy, ix] = max(m[iy, ix], dark)
            ang += rng.normal(0, 0.45)
            x += math.cos(ang)
            y += math.sin(ang)
    return m


def texture():
    rng = np.random.default_rng(1805)
    img = np.zeros((128, 128, 4))
    img[..., 3] = 255

    def put(key, rgb):
        x0, y0, x1, y1 = REG[key]
        img[y0:y1, x0:x1, :3] = rgb

    # 칼날 넓은 면: 따뜻한 회갈색 돌 (얼룩 · 결) + 어두운 금 + 가장자리 쪽은 벼린 듯 밝게 + 가운데 홈 (맥이 지나는 자리) 은 어둡게
    w, h = 64, 128
    n = fbm(rng, w, h)
    strata = 0.5 + 0.5 * np.sin(np.arange(h)[:, None] / h * math.tau * 5 + noise(rng, w, h, 32) * 3)
    base = mix((78, 66, 54), (128, 112, 92), n * 0.85 + strata * 0.15)
    xx = np.arange(w)[None, :] / (w - 1)
    bevel = np.clip(1 - np.minimum(xx, 1 - xx) / 0.16, 0, 1) ** 1.5
    base = mix(base, (168, 152, 126), bevel * 0.55)
    fuller = np.exp(-((xx - 0.5) / 0.09) ** 2) * np.clip((np.arange(h)[:, None] / h - 0.08) / 0.08, 0, 1)  # 칼끝 (위 8%) 에는 홈 없음
    base = base * (1 - 0.35 * fuller[..., None])
    cr = cracks(rng, w, h, 14, 40, 1.0)
    cr[:14] = 0  # 칼끝 자리에는 금 없음 (좁은 칼끝이 검게 보이지 않게)
    base = mix(base, (44, 36, 30), cr * 0.85)
    speck = rng.random((h, w)) > 0.985
    base[speck] = base[speck] * 0.6 + np.array([200, 170, 120]) * 0.4
    put("face", base)
    # 두께 면 (밝은 돌)
    n = fbm(rng, 16, 128, cells=(8, 4, 2, 1))
    put("side", mix((120, 106, 88), (170, 154, 128), n))
    # 코등이: 어두운 현무암 + 호박빛 알갱이
    n = fbm(rng, 48, 32, cells=(8, 4, 2, 1))
    g = mix((48, 42, 40), (84, 74, 66), n)
    fleck = rng.random((32, 48)) > 0.96
    g[fleck] = np.array([255, 160, 60])
    put("guard", g)
    # 거친 바위
    n = fbm(rng, 48, 32, cells=(8, 4, 2, 1))
    r = mix((70, 60, 50), (118, 102, 84), n)
    pits = rng.random((32, 48)) > 0.93
    r[pits] = r[pits] * 0.55
    put("rock", r)
    # 가죽 손잡이: 비스듬히 감은 띠
    yy, xx2 = np.mgrid[0:64, 0:16]
    band = ((yy + xx2 * 0.6) % 8) < 5.5
    gr = np.where(band[..., None], np.array([104, 66, 40], float), np.array([58, 36, 22], float))
    gr = gr * (0.85 + 0.3 * fbm(rng, 16, 64, cells=(4, 2, 1, 1))[..., None])
    put("grip", gr)
    # 호박빛 맥: 가운데 밝고 가장자리 주황 + 흐르는 무늬
    yy, xx3 = np.mgrid[0:64, 0:16]
    cx = np.abs(xx3 - 7.5) / 7.5
    flow = noise(rng, 16, 64, 6)
    gl = mix((255, 236, 160), (255, 128, 30), cx ** 1.4 * 0.9 + flow * 0.25)
    put("glow", gl)
    # 보석: 가운데 밝은 원 + 흰 반짝임
    yy, xx4 = np.mgrid[0:16, 0:16]
    d = np.hypot(xx4 - 7.5, yy - 7.5) / 8
    gem = mix((255, 236, 160), (214, 96, 24), d ** 1.2)
    gem[3:5, 4:6] = (255, 255, 236)
    put("gem", gem)
    put("hot", mix((255, 250, 220), (255, 200, 110), fbm(rng, 16, 16, cells=(4, 2, 1, 1))))
    n = fbm(rng, 16, 32, cells=(4, 2, 1, 1))
    put("edge", mix((150, 136, 112), (196, 182, 156), n))
    return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 모델

def uvr(key):
    x0, y0, x1, y1 = REG[key]
    return x0 / TEXEL, y0 / TEXEL, x1 / TEXEL, y1 / TEXEL


def sub(key, fu0, fv0, fu1, fv1):
    """영역의 일부 (비율 0 ~ 1) → UV"""
    u0, v0, u1, v1 = uvr(key)
    return [round(u0 + (u1 - u0) * fu0, 4), round(v0 + (v1 - v0) * fv0, 4), round(u0 + (u1 - u0) * fu1, 4), round(v0 + (v1 - v0) * fv1, 4)]


RNG = random.Random(1805)


def box(name, frm, to, key, emit=0, rot=None, faces=None, blade_map=False):
    """상자 하나 — 면마다 영역의 일부. blade_map 이면 넓은 면 (north · south) 을 칼날 위의 자리대로 (돌 무늬가 이어지게)"""
    e = {"name": name, "from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {}}
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    for f in faces or ("north", "south", "east", "west", "up", "down"):
        k = key
        if blade_map and f in ("north", "south"):
            fu0 = (frm[0] - 4.2) / 7.6
            fu1 = (to[0] - 4.2) / 7.6
            fv0 = (32 - to[1]) / 20
            fv1 = (32 - frm[1]) / 20
            e["faces"][f] = {"uv": sub("face", max(0, fu0), max(0, fv0), min(1, fu1), min(1, fv1)), "texture": "#0"}
            continue
        if blade_map and f in ("east", "west", "up", "down"):
            k = "side" if f in ("east", "west") else "edge"
        # 면 크기에 맞는 영역 일부 (무작위 자리 — 결정적)
        a, b = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]
        u0, v0, u1, v1 = uvr(k)
        du = min(u1 - u0, max(0.25, a * 0.5))
        dv = min(v1 - v0, max(0.25, b * 0.5))
        ou = u0 + RNG.uniform(0, (u1 - u0) - du)
        ov = v0 + RNG.uniform(0, (v1 - v0) - dv)
        e["faces"][f] = {"uv": [round(ou, 4), round(ov, 4), round(ou + du, 4), round(ov + dv, 4)], "texture": "#0"}
    if emit:
        e["light_emission"] = emit
        e["shade"] = False
    if rot:
        e["rotation"] = rot
    return e


def elements():
    els = []
    T0, T1 = 7.2, 8.8  # 칼날 두께 (z)
    # 칼날 (넓은 부분 → 끝으로 좁아짐)
    els.append(box("blade", (4.8, 12.0, T0), (11.2, 26.0, T1), "face", blade_map=True))
    els.append(box("blade_taper1", (5.6, 26.0, T0 + 0.1), (10.4, 28.4, T1 - 0.1), "face", blade_map=True))
    els.append(box("blade_taper2", (6.6, 28.4, T0 + 0.2), (9.4, 30.4, T1 - 0.2), "face", blade_map=True))
    els.append(box("blade_tip", (7.4, 30.4, T0 + 0.35), (8.6, 32.0, T1 - 0.35), "face", blade_map=True))
    # 벼린 날 (양쪽 — 조금 얇고 밖으로)
    for sgn, x0, x1 in ((-1, 4.3, 4.8), (1, 11.2, 11.7)):
        els.append(box(f"edge_{'l' if sgn < 0 else 'r'}", (x0, 12.6, 7.55), (x1, 26.0, 8.45), "edge"))
    els.append(box("edge_l2", (5.2, 26.0, 7.6), (5.6, 28.4, 8.4), "edge"))
    els.append(box("edge_r2", (10.4, 26.0, 7.6), (10.8, 28.4, 8.4), "edge"))
    els.append(box("edge_l3", (6.3, 28.4, 7.7), (6.6, 30.4, 8.3), "edge"))
    els.append(box("edge_r3", (9.4, 28.4, 7.7), (9.7, 30.4, 8.3), "edge"))
    # 가운데 호박빛 맥 (넓은 면 둘 다에서 보이게 칼날보다 조금 두껍게)
    els.append(box("vein", (7.55, 13.0, 7.05), (8.45, 29.6, 8.95), "glow", emit=15))
    # 갈라져 나가는 맥 (z 축으로 22.5 · 45° 기욺 — 바깥쪽이 위로)
    for i, (y, side, ang, ln) in enumerate(((15.0, 1, 22.5, 2.6), (17.8, -1, -22.5, 2.4), (20.8, 1, 45, 2.2), (23.4, -1, -45, 2.1), (26.2, 1, 22.5, 1.6),
                                            (16.4, -1, -45, 1.4), (22.2, 1, 22.5, 1.5))):
        x0, x1 = (8.0, 8.0 + ln) if side > 0 else (8.0 - ln, 8.0)
        els.append(box(f"vein_b{i}", (x0, y - 0.28, 7.08), (x1, y + 0.28, 8.92), "glow", emit=15,
                       rot={"angle": ang, "axis": "z", "origin": [8.0, y, 8.0]}))
    # 바위 돌기 (넓은 면에 붙은 거친 조각)
    for i, (x0, y0, x1, y1, front) in enumerate(((5.1, 13.3, 6.5, 14.5, True), (9.6, 19.2, 10.8, 20.2, True), (5.0, 22.4, 6.1, 23.6, False),
                                                 (9.9, 15.6, 11.0, 16.9, False), (5.4, 18.4, 6.2, 19.2, True))):
        z0, z1 = (6.88, T0) if front else (T1, 9.12)
        els.append(box(f"chip{i}", (x0, y0, z0), (x1, y1, z1), "rock"))
    # 코등이 (현무암 막대 · 바위 끝 · 칼날을 감싼 돌기 · 빛나는 줄 · 보석)
    els.append(box("guard", (2.0, 9.6, 6.6), (14.0, 12.0, 9.4), "guard"))
    els.append(box("guard_l", (0.6, 9.0, 6.3), (2.6, 12.8, 9.7), "rock"))
    els.append(box("guard_r", (13.4, 9.0, 6.3), (15.4, 12.8, 9.7), "rock"))
    els.append(box("prong_l", (3.6, 12.0, 6.9), (4.9, 14.0, 9.1), "rock"))
    els.append(box("prong_r", (11.1, 12.0, 6.9), (12.4, 14.0, 9.1), "rock"))
    els.append(box("guard_vein", (3.0, 10.5, 6.45), (13.0, 11.1, 9.55), "glow", emit=12))
    els.append(box("guard_gem", (6.9, 9.9, 6.2), (9.1, 12.4, 9.8), "gem", emit=15))
    # 손잡이 (가죽) · 감은 띠 · 머리 (바위 + 빛나는 고리 · 보석)
    els.append(box("grip", (7.0, 3.4, 7.0), (9.0, 9.6, 9.0), "grip"))
    for i, y in enumerate((4.6, 6.4, 8.2)):
        els.append(box(f"wrap{i}", (6.85, y, 6.85), (9.15, y + 0.4, 9.15), "guard"))
    els.append(box("pommel", (6.3, 1.0, 6.3), (9.7, 3.6, 9.7), "rock"))
    els.append(box("pommel_ring", (6.15, 2.0, 6.15), (9.85, 2.5, 9.85), "glow", emit=13))
    els.append(box("pommel_gem", (7.2, 0.4, 7.2), (8.8, 1.2, 8.8), "gem", emit=15))
    return els


def main():
    tex_dir = os.path.join(RES, "textures", "item", "fx")
    model_dir = os.path.join(RES, "models", "item", "fx")
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)
    tex = texture()
    tex.save(os.path.join(tex_dir, f"{NAME}.png"))
    els = elements()
    model = {"credit": "MMO RPG — 0.18.0 대지 가르기의 대지의 대검 (tools/terra_blade.py 가 만듦, 손으로 고치지 않음)", "texture_size": [128, 128],
             "textures": {"0": f"rpg:item/fx/{NAME}", "particle": f"rpg:item/fx/{NAME}"}, "elements": els}
    with open(os.path.join(model_dir, f"{NAME}.json"), "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=1)
        f.write("\n")
    with open(os.path.join(RES, "items", f"fx_{NAME}.json"), "w", encoding="utf-8") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"rpg:item/fx/{NAME}"}}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    for e in els:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (e["name"], v)
    print(f"{NAME}: {len(els)} elements, texture {tex.size}")
    if "--preview" in sys.argv:
        preview(els, tex)


def preview(els, tex):
    """앞 (+z 에서) · 옆 (+x 에서) 정사영 — 확인용 (scratch)"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import java_model_view as mv
    view = mv.View(els, np.array(tex))
    S = int(os.environ.get("PREVIEW_S", "6"))  # 픽셀 / 모델 단위
    out = []
    for D, axes in (((0, 0, -1), (0, 1)), ((-1, 0, 0), (2, 1))):
        ys = np.arange(34 * S)[::-1] / S - 1.0
        xs = np.arange(18 * S) / S - 1.0
        X, Y = np.meshgrid(xs, ys)
        P = np.zeros((X.size, 3))
        P[:, axes[0]] = X.ravel()
        P[:, axes[1]] = Y.ravel()
        far = [i for i in range(3) if i not in axes][0]
        P[:, far] = 1000.0 * -np.array(D)[far]
        col, glow, _ = view.trace(P, np.array(D, float))
        im = col.reshape(X.shape[0], X.shape[1], 4)
        bg = np.zeros_like(im)
        bg[..., :3] = (110, 150, 200)
        bg[..., 3] = 255
        a = im[..., 3:4] / 255
        comp = bg.copy()
        comp[..., :3] = im[..., :3] * a + bg[..., :3] * (1 - a)
        out.append(Image.fromarray(comp.astype(np.uint8), "RGBA"))
    W = sum(o.size[0] for o in out) + 10
    sheet = Image.new("RGBA", (W, out[0].size[1]), (110, 150, 200, 255))
    x = 0
    for o in out:
        sheet.paste(o, (x, 0))
        x += o.size[0] + 10
    path = os.environ.get("PREVIEW", "/tmp/terra_blade_preview.png")
    sheet.save(path)
    print("preview", path)


if __name__ == "__main__":
    main()
