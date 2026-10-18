#!/usr/bin/env python3
"""0.19.0 세계수의 가호의 세계수 그림자 — 아이템 표시 엔티티 (item_display) 로 그리는 3D 나무 모델 · 텍스처 · 아이템 정의.

  python3 tools/worldtree_spirit.py            # 모델 · 텍스처 · 아이템 정의를 씀
  python3 tools/worldtree_spirit.py --preview  # 앞 · 옆 · 위에서 비스듬히 그린 그림 (확인용, PREVIEW=경로 · PREVIEW_S=배율)

만들어지는 것
- src/main/resources/assets/rpg/items/fx_worldtree.json               아이템 정의 (rpg:fx_worldtree — ItemStack 의 item_model)
- src/main/resources/assets/rpg/models/item/fx/worldtree.json         모델 (뿌리 · 비틀린 줄기 · 비취빛 맥 (빛남) · 가지 · 반투명 잎 덩이 · 안쪽 빛 잎 · 금빛 열매 (빛남))
- src/main/resources/assets/rpg/textures/item/fx/worldtree.png        텍스처 128×128 (1 UV = 8 픽셀, 잎은 알파 — 반투명)

모델 좌표 (자바 아이템 모델 — 한 블록 = 16, 상자는 -16 ~ 32): 땅 = y -16, 꼭대기 = y 32 (높이 48 = 표시 크기 s 면 3s 칸), 가운데 x · z = 8.
표시 엔티티는 모델 가운데 (8, 8, 8) 를 엔티티 자리에 두므로 RuneGrove 는 땅에 둔 엔티티에서 위로 1.5s 칸 옮겨 밑동을 땅에 맞춤.
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
NAME = "worldtree"
TEXEL = 8

REG = {
    "bark": (0, 0, 32, 128),         # 줄기 껍질 (짙은 갈색 · 세로 골 · 이끼)
    "root": (32, 0, 48, 128),        # 뿌리 (밝은 껍질)
    "vein": (48, 0, 56, 128),        # 비취빛 맥 (빛남)
    "leaf": (56, 0, 104, 48),        # 잎 덩이 바깥 (반투명 — 잎 사이 틈)
    "leaf_in": (56, 48, 104, 96),    # 안쪽 빛 잎 (더 옅고 밝음 — 빛남)
    "fruit": (104, 0, 120, 16),      # 금빛 열매 (빛남)
    "bloom": (104, 16, 120, 32),     # 연한 금빛 꽃 (빛남)
    "moss": (104, 32, 128, 64),      # 이끼 · 밑동
    "branch": (56, 96, 104, 128),    # 가지 껍질
}


def noise(rng, w, h, cell):
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


def texture():
    rng = np.random.default_rng(1902)
    img = np.zeros((128, 128, 4))

    def put(key, rgb, alpha=None):
        x0, y0, x1, y1 = REG[key]
        img[y0:y1, x0:x1, :3] = rgb
        img[y0:y1, x0:x1, 3] = 255 if alpha is None else alpha

    # 줄기: 짙은 갈색 + 세로 골 (어두운 줄) + 이끼 얼룩
    w, h = 32, 128
    n = fbm(rng, w, h)
    xx = np.arange(w)[None, :]
    grooves = (np.sin(xx / w * math.tau * 4 + noise(rng, w, h, 16) * 4) > 0.55).astype(float)
    bark = mix((54, 38, 26), (104, 76, 50), n)
    bark = mix(bark, (34, 24, 16), grooves * 0.7)
    moss = fbm(rng, w, h, cells=(8, 4, 2, 1)) > 0.62
    bark[moss] = bark[moss] * 0.5 + np.array([70, 120, 60]) * 0.5
    put("bark", bark)
    n = fbm(rng, 16, 128, cells=(8, 4, 2, 1))
    put("root", mix((84, 62, 42), (136, 104, 72), n))
    n = fbm(rng, 48, 32, cells=(8, 4, 2, 1))
    put("branch", mix((70, 50, 34), (118, 88, 58), n))
    # 비취빛 맥: 가운데 밝은 민트 → 가장자리 비취
    yy, xv = np.mgrid[0:128, 0:8]
    cx = np.abs(xv - 3.5) / 3.5
    flow = noise(rng, 8, 128, 8)
    put("vein", mix((220, 255, 230), (60, 200, 130), cx ** 1.3 * 0.9 + flow * 0.2))
    # 잎 덩이 바깥: 두 가지 초록 + 금빛 잎끝, 잎 사이 틈은 투명 (알파 0), 나머지 알파 215
    w, h = 48, 48
    n = fbm(rng, w, h, cells=(8, 4, 2, 1))
    leaf = mix((40, 120, 60), (110, 200, 100), n)
    tips = rng.random((h, w)) > 0.93
    leaf[tips] = np.array([235, 220, 120])
    holes = fbm(rng, w, h, cells=(4, 2, 1, 1)) < 0.28
    alpha = np.where(holes, 0, 215).astype(float)
    put("leaf", leaf, alpha)
    # 안쪽 빛 잎: 연한 민트 · 금빛 (알파 120 — 바깥 잎 사이로 비침)
    n = fbm(rng, w, h, cells=(8, 4, 2, 1))
    put("leaf_in", mix((170, 255, 190), (255, 240, 160), n), np.full((h, w), 120.0))
    # 열매 · 꽃
    yy, xx2 = np.mgrid[0:16, 0:16]
    d = np.hypot(xx2 - 7.5, yy - 7.5) / 8
    put("fruit", mix((255, 246, 190), (230, 160, 40), d ** 1.2))
    put("bloom", mix((255, 255, 240), (250, 220, 140), d))
    n = fbm(rng, 24, 32, cells=(4, 2, 1, 1))
    put("moss", mix((50, 90, 44), (96, 150, 70), n))
    return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGBA")


def uvr(key):
    x0, y0, x1, y1 = REG[key]
    return x0 / TEXEL, y0 / TEXEL, x1 / TEXEL, y1 / TEXEL


RNG = random.Random(1902)


def box(name, frm, to, key, emit=0, rot=None):
    e = {"name": name, "from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {}}
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    for f in ("north", "south", "east", "west", "up", "down"):
        a, b = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]
        u0, v0, u1, v1 = uvr(key)
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


def cbox(name, cx, cy0, cz, w, h, d, key, emit=0, rot=None):
    """가운데 (cx, cz) · 밑 cy0 · 크기 w × h × d 상자"""
    return box(name, (cx - w / 2, cy0, cz - d / 2), (cx + w / 2, cy0 + h, cz + d / 2), key, emit, rot)


def elements():
    els = []
    C = 8.0
    # 뿌리: 밑동에서 여덟 방향으로 (가로 막대 넷 — 0 · 45 · 90 · 135°) + 이끼 밑동
    for i, ang in enumerate((0, 45, 0, -45)):
        along_x = i < 2 or i == 3
        if i == 2:
            els.append(cbox("root_z", C, -16, C, 3.2, 3.0, 30, "root"))
        else:
            els.append(box(f"root_{i}", (C - 15, -16, C - 1.6), (C + 15, -13, C + 1.6), "root",
                           rot={"angle": ang, "axis": "y", "origin": [C, -14.5, C]} if ang else None))
    for i, ang in enumerate((22.5, -22.5)):
        els.append(box(f"root_mid{i}", (C - 11, -16, C - 1.3), (C + 11, -12, C + 1.3), "root", rot={"angle": ang, "axis": "y", "origin": [C, -14, C]}))
        els.append(cbox(f"root_midz{i}", C, -16, C, 2.6, 4.0, 22, "root", rot={"angle": ang, "axis": "y", "origin": [C, -14, C]}))
    els.append(cbox("base", C, -16, C, 12, 4, 12, "moss"))
    # 줄기: 위로 갈수록 가늘고 마디마다 돌아감 (비틀림), 마디마다 앞뒤로 비취빛 맥
    segs = [(-14, -8, 11.0, 0), (-8, -1, 8.6, 22.5), (-1, 6, 7.4, 45), (6, 12, 6.4, -22.5), (12, 18, 5.4, 0), (18, 23, 4.4, 22.5)]
    for i, (y0, y1, w, ang) in enumerate(segs):
        rot = {"angle": ang, "axis": "y", "origin": [C, (y0 + y1) / 2, C]} if ang else None
        els.append(cbox(f"trunk{i}", C, y0, C, w, y1 - y0, w, "bark", rot=rot))
        for k, (dx, dz, vw, vd) in enumerate(((0, w / 2 + 0.05, 0.9, 0.3), (0, -w / 2 - 0.05, 0.9, 0.3), (w / 2 + 0.05, 0, 0.3, 0.9))):
            els.append(cbox(f"vein{i}_{k}", C + dx, y0 + 0.5, C + dz, vw, y1 - y0 - 1.0, vd, "vein", emit=14, rot=rot))
    # 버팀 뿌리: 밑동 네 곳에서 비스듬히 (22.5°) 땅으로
    for i, (axis, ang, dx, dz) in enumerate((("z", 22.5, -4.2, 0), ("z", -22.5, 4.2, 0), ("x", -22.5, 0, -4.2), ("x", 22.5, 0, 4.2))):
        els.append(cbox(f"buttress{i}", C + dx, -16, C + dz, 3.4, 9, 3.4, "bark", rot={"angle": ang, "axis": axis, "origin": [C + dx, -12, C + dz]}))
    # 가지: 줄기 위쪽에서 네 방향으로 비스듬히 (45° — 아래 고리 덩이까지) + 사이 넷 (22.5° — 가운데 고리까지)
    for i, (axis, ang) in enumerate((("z", 45), ("z", -45), ("x", 45), ("x", -45))):
        els.append(cbox(f"branch{i}", C, 9, C, 2.6, 17, 2.6, "branch", rot={"angle": ang, "axis": axis, "origin": [C, 9, C]}))
    for i, (axis, ang) in enumerate((("z", 22.5), ("z", -22.5), ("x", 22.5), ("x", -22.5))):
        els.append(cbox(f"twig{i}", C, 15, C, 1.8, 12, 1.8, "branch", rot={"angle": ang, "axis": axis, "origin": [C, 15, C]}))
    # 잎 덩이 (반투명 — 둥근 지붕): 아래 고리 (여덟 덩이 · 반지름 15) · 가운데 고리 (여섯 · 반지름 9) · 꼭대기 (넷 + 가운데),
    # 덩이마다 크기 · 높이를 조금씩 달리하고 몇은 y 축으로 22.5 · 45° 돌림. 안쪽에 빛 잎 (빛남) — 바깥 잎 사이로 비침
    rng = random.Random(7)
    clusters = []
    for k in range(8):
        a = math.tau * k / 8 + 0.2
        clusters.append((C + math.cos(a) * 15.5, 13.5 + rng.uniform(-1, 1.5), C + math.sin(a) * 15.5, rng.uniform(10, 13), rng.uniform(6, 8)))
    for k in range(6):
        a = math.tau * k / 6 + 0.5
        clusters.append((C + math.cos(a) * 9, 19 + rng.uniform(-0.5, 1.5), C + math.sin(a) * 9, rng.uniform(12, 15), rng.uniform(7, 9)))
    clusters.append((C, 20, C, 18, 9))
    for k in range(4):
        a = math.tau * k / 4 + 0.8
        clusters.append((C + math.cos(a) * 5, 26 + rng.uniform(0, 1.5), C + math.sin(a) * 5, rng.uniform(9, 11), rng.uniform(4, 5)))
    clusters.append((C, 28, C, 10, 4))
    for i, (cx, cy, cz, w, h) in enumerate(clusters):
        half = min(w / 2, cx + 16, 32 - cx, cz + 16, 32 - cz)
        w = 2 * half
        top = min(cy + h, 32)
        rot = {"angle": (22.5, 45, -22.5, 0)[i % 4], "axis": "y", "origin": [cx, cy + h / 2, cz]} if i % 4 != 3 else None
        els.append(box(f"leaf{i}", (cx - half, cy, cz - half), (cx + half, top, cz + half), "leaf", rot=rot))
        if (top - cy) > 3:
            els.append(box(f"leaf_in{i}", (cx - half * 0.78, cy + 1.0, cz - half * 0.78), (cx + half * 0.78, top - 1.0, cz + half * 0.78), "leaf_in", emit=10, rot=rot))
    # 금빛 열매 · 꽃: 아래 고리 덩이 밑에 매달림 (가는 줄기 + 열매) · 위쪽 덩이 겉에 꽃
    for k in range(8):
        a = math.tau * k / 8 + 0.2 + math.tau / 16
        x, z = C + math.cos(a) * 12.5, C + math.sin(a) * 12.5
        drop = rng.uniform(2.0, 4.0)
        els.append(cbox(f"stem{k}", x, 13.2 - drop, z, 0.3, drop, 0.3, "branch"))
        els.append(cbox(f"fruit{k}", x, 13.2 - drop - 1.8, z, 1.8, 1.8, 1.8, "fruit", emit=15))
    for k in range(6):
        a = math.tau * k / 6 + 0.1
        x, z = C + math.cos(a) * 11.5, C + math.sin(a) * 11.5
        els.append(cbox(f"bloom{k}", x, 27.0 + rng.uniform(-1, 1), z, 1.6, 1.6, 1.6, "bloom", emit=15))
    els.append(cbox("crown", C, 30.2, C, 1.8, 1.8, 1.8, "bloom", emit=15))
    for e in els:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (e["name"], v)
    return els


def main():
    tex_dir = os.path.join(RES, "textures", "item", "fx")
    model_dir = os.path.join(RES, "models", "item", "fx")
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)
    tex = texture()
    tex.save(os.path.join(tex_dir, f"{NAME}.png"))
    els = elements()
    model = {"credit": "MMO RPG — 0.19.0 세계수의 가호의 세계수 그림자 (tools/worldtree_spirit.py 가 만듦, 손으로 고치지 않음)", "texture_size": [128, 128],
             "textures": {"0": f"rpg:item/fx/{NAME}", "particle": f"rpg:item/fx/{NAME}"}, "elements": els}
    with open(os.path.join(model_dir, f"{NAME}.json"), "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=1)
        f.write("\n")
    with open(os.path.join(RES, "items", f"fx_{NAME}.json"), "w", encoding="utf-8") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"rpg:item/fx/{NAME}"}}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{NAME}: {len(els)} elements, texture {tex.size}")
    if "--preview" in sys.argv:
        preview(els, tex)


def preview(els, tex):
    """앞 (+z 에서) · 옆 (+x 에서) · 위에서 비스듬히 정사영 (확인용)"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import java_model_view as mv
    view = mv.View(els, np.array(tex))
    S = int(os.environ.get("PREVIEW_S", "5"))
    out = []
    lo, hi = -17.0, 33.0
    span = hi - lo
    for D in ((0, 0, -1), (-1, 0, 0), (-0.55, -0.45, -0.7)):
        D = np.array(D, float)
        D /= np.linalg.norm(D)
        # 화면 축: 가로 = up × D, 세로 = D × 가로
        up = np.array([0, 1.0, 0])
        hx = np.cross(up, D)
        hx /= np.linalg.norm(hx)
        vy = np.cross(D, hx)
        n = int(span * S)
        us = (np.arange(n) / S + lo) - 8
        vs = (np.arange(n)[::-1] / S + lo) - 8
        U, Vv = np.meshgrid(us, vs)
        center = np.array([8.0, 8.0, 8.0])
        P = center + U.ravel()[:, None] * hx + Vv.ravel()[:, None] * vy - D * 200
        col, glow, _ = view.trace(P, D)
        im = col.reshape(n, n, 4)
        bg = np.zeros_like(im)
        bg[..., :3] = (120, 160, 205)
        a = im[..., 3:4] / 255
        comp = np.zeros_like(im)
        comp[..., :3] = im[..., :3] * a + bg[..., :3] * (1 - a)
        comp[..., 3] = 255
        out.append(Image.fromarray(comp.astype(np.uint8), "RGBA"))
    W = sum(o.size[0] for o in out) + 20
    sheet = Image.new("RGBA", (W, out[0].size[1]), (120, 160, 205, 255))
    x = 0
    for o in out:
        sheet.paste(o, (x, 0))
        x += o.size[0] + 10
    path = os.environ.get("PREVIEW", "/tmp/worldtree_preview.png")
    sheet.save(path)
    print("preview", path)


if __name__ == "__main__":
    main()
