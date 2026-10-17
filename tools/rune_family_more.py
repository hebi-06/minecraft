#!/usr/bin/env python3
"""자바 전용 룬 계열의 아이콘 · 아이템 모델 · 아이템 정의 (0.12.6 — 사신 계열).

tools/rune_family_more.py

rules.json 위에 rules_more.json 을 덧붙인 RUNE_FAMILY_ORDER 에서 애드온에 없던 계열마다 다섯 등급:

- 아이콘 assets/rpg/textures/item/rune/rune_<계열>_<등급>.png (32×32): 애드온 룬 그림과 같은 돌 · 등급 테두리 위에 계열 문양.
  돌은 그 등급의 애드온 룬 16장에서 문양이 없는 쪽 (칸마다 무채색 · 어두운 돌 빛에 가장 가까운 것) 을 모으고, 문양 자리 (가운데 10 × 14) 는
  둘레 돌 빛으로 맞춘 매끈한 면 (2차 곡면) 으로 채움. 문양은 8 × 8 겹쳐 그려 가장자리를 부드럽게: 바깥 (굵게) = 계열 빛, 안 (가늘게) = 연한 심지,
  둘레 1 ~ 2칸 = 어두운 빛무리 (애드온 문양처럼).
  사신 (reaper): 핏빛 초승달 (왼쪽이 볼록 — 가운데가 굵음) + 아래 끝에 맺힌 핏방울
- 아이템 모델 assets/rpg/models/item/rune_<계열>_<등급>.json (generated), 아이템 정의 assets/rpg/items/rune_<계열>_<등급>.json
  (tools/rune_break_badges.py 와 같은 모양 — custom_model_data 첫 수 = 돌파 단계로 range_dispatch → 배지 모델 composite)
- 0.16.0 한정 초월 룬 (RUNE_LIMITED_ORDER — 초월 하나씩): 같은 초월 돌 위에 여러 색 문양 (LIMITED_GLYPHS — 층마다 심지 · 빛 · 빛무리 색),
  테두리의 붉은 빛을 무지갯빛으로 (가운데에서 본 각도마다 색 — 한정 룬 표시)
  만상 (arcana): 네 스탯 구슬 (위 힘 주황 · 오른쪽 민첩 초록 · 아래 체력 분홍 · 왼쪽 마력 파랑) + 잇는 고리 + 가운데 흰 네 갈래 별
  칠성 (dipper): 북두칠성 일곱 별 (흰 · 금빛 별, 푸른 빛무리) + 잇는 선
  천공 (skyeye): 금빛 눈 (흰자 · 해빛 눈동자) + 아래로 내리꽂는 빛줄기
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
TEX = os.path.join(RES, "textures", "item", "rune")
GRADES = ["common", "rare", "noble", "hero", "transcend"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ------------------------------------------------------------ 돌 (문양 없는 룬)

def blank_stone(grade, families):
    """그 등급의 애드온 룬들에서 문양을 지운 돌 (테두리 · 빛은 그대로)"""
    ims = np.stack([np.array(Image.open(os.path.join(TEX, f"rune_{f}_{grade}.png")).convert("RGBA")).astype(float) for f in families])
    out = ims[0].copy()
    for y in range(6, 27):
        for x in range(9, 23):
            px = ims[:, y, x, :3]
            score = np.abs(px.mean(1) - 56) + (px.max(1) - px.min(1)) * 1.5  # 어두운 무채색 돌 빛에서 먼 정도
            out[y, x, :3] = px[int(np.argmin(score))]
    # 문양 자리는 둘레 돌 빛으로 맞춘 매끈한 면
    pts, vals = [], []
    for y in range(7, 25):
        for x in range(10, 22):
            if x in (10, 11, 20, 21) or y in (7, 8, 23, 24):
                pts.append((x, y))
                vals.append(out[y, x, :3])
    a = np.array([[1, x, y, x * x, y * y, x * y] for x, y in pts], float)
    coef, *_ = np.linalg.lstsq(a, np.array(vals, float), rcond=None)
    for y in range(9, 23):
        for x in range(11, 21):
            out[y, x, :3] = np.array([1, x, y, x * x, y * y, x * y], float) @ coef
    return out


# ------------------------------------------------------------ 문양

def coverage(fn, ss=8):
    cov = np.zeros((32, 32))
    for y in range(32):
        for x in range(32):
            n = 0
            for j in range(ss):
                for i in range(ss):
                    if fn(x + (i + 0.5) / ss, y + (j + 0.5) / ss):
                        n += 1
            cov[y, x] = n / (ss * ss)
    return cov


def reaper_shape(grow):
    """핏빛 초승달 (가운데 (16, 15.4) · 반지름 6.4, 위 끝 58° → 왼쪽 → 아래 끝 296°, 가운데가 굵음) + 아래 끝에 맺힌 핏방울"""
    cx, cy, r0, t0, t1 = 16.0, 15.4, 6.4, 58.0, 296.0

    def crescent(x, y):
        th = (math.degrees(math.atan2(y - cy, x - cx)) + 360) % 360
        if not (t0 - grow * 10 <= th <= t1 + grow * 10):
            return False
        q = max(0.0, min(1.0, (th - t0) / (t1 - t0)))
        thick = 0.3 + 2.3 * math.sin(math.pi * q)
        r = math.hypot(x - cx, y - cy)
        return r0 - thick - grow <= r <= r0 + grow * 0.8

    def drop(x, y):
        dx, dy, rr = 20.6, 23.6, 1.05 + grow
        if (x - dx) ** 2 + (y - dy) ** 2 <= rr * rr:
            return True
        top = dy - 2.7 - grow
        if top <= y <= dy:
            return abs(x - dx) <= rr * (y - top) / (dy - top)
        return False

    return lambda x, y: crescent(x, y) or drop(x, y)


# 계열 → (문양 모양, 심지 · 빛 · 빛무리 색)
GLYPHS = {
    "reaper": (reaper_shape, (255, 214, 206), (232, 52, 66), (150, 16, 34)),
}


def draw_glyph(stone, shape, core, mid, glow):
    cov_in = coverage(shape(0.0))
    cov_out = coverage(shape(0.85))
    near = np.zeros_like(cov_out)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if dx == 0 and dy == 0:
                continue
            w = 0.42 if max(abs(dx), abs(dy)) == 1 else 0.16
            near = np.maximum(near, np.roll(np.roll(cov_out, dy, 0), dx, 1) * w)
    out = stone.copy()
    core, mid, glow = (np.array(c, float) for c in (core, mid, glow))
    for y in range(32):
        for x in range(32):
            co, ci = cov_out[y, x], cov_in[y, x]
            px = out[y, x, :3]
            g = near[y, x] * (1 - co)
            px = px * (1 - g) + glow * g
            px = px * (1 - co) + mid * co
            px = px * (1 - ci) + core * ci
            out[y, x, :3] = px
    return Image.fromarray(np.clip(np.round(out), 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 모델 · 아이템 정의

def item_files(name, levels):
    write_json(os.path.join(RES, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/{name}"}})
    base = {"type": "minecraft:model", "model": f"rpg:item/{name}"}
    entries = [{"threshold": n, "model": {"type": "minecraft:composite", "models": [base, {"type": "minecraft:model", "model": f"rpg:item/rune/break_{n}"}]}}
               for n in range(1, levels + 1)]
    write_json(os.path.join(RES, "items", f"{name}.json"), {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                                                                      "fallback": base, "entries": entries}})


# ------------------------------------------------------------ 0.16.0 한정 초월 룬

def disc(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def seg(x0, y0, x1, y1, w):
    """두께 w 의 선분"""
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy

    def f(x, y):
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / L2))
        px, py = x0 + dx * t, y0 + dy * t
        return (x - px) ** 2 + (y - py) ** 2 <= (w / 2) ** 2
    return f


def union(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def grow_shape(make):
    """reaper_shape 처럼 grow (바깥 빛 두께) 를 받는 꼴로"""
    return lambda grow: make(grow)


def star4(cx, cy, r, w):
    """네 갈래 별 (가는 십자 + 작은 마름모)"""
    return union(seg(cx - r, cy, cx + r, cy, w), seg(cx, cy - r, cx, cy + r, w), lambda x, y: abs(x - cx) + abs(y - cy) <= w * 1.3)


ORB_COL = {"str": ((255, 236, 214), (255, 128, 52), (150, 52, 12)), "dex": ((226, 255, 232), (80, 230, 120), (20, 110, 46)),
           "vit": ((255, 226, 242), (255, 110, 186), (130, 30, 86)), "int": ((224, 236, 255), (100, 150, 255), (28, 52, 140))}


def arcana_layers():
    pos = {"str": (16.0, 9.6), "dex": (20.6, 15.6), "vit": (16.0, 21.6), "int": (11.4, 15.6)}
    ring = lambda g: (lambda x, y: abs(math.hypot(x - 16, y - 15.6) - 5.1) <= 0.32 + g * 0.3)
    layers = [(ring, (236, 232, 255), (170, 160, 230), (70, 60, 120))]
    for k, (cx, cy) in pos.items():
        core, mid, glow = ORB_COL[k]
        layers.append((lambda g, cx=cx, cy=cy: disc(cx, cy, 1.75 + g), core, mid, glow))
    layers.append((lambda g: star4(16, 15.6, 2.4 + g, 0.7 + g * 0.6), (255, 255, 255), (236, 230, 255), (120, 110, 170)))
    return layers


def dipper_points():
    """북두칠성 (두베 → 알카이드) 을 돌 면에 맞춘 자리: 국자가 왼쪽 위 (사각형), 손잡이가 오른쪽 아래로 휘어 내려감"""
    return [(11.0, 7.8), (10.2, 12.6), (14.6, 14.4), (15.6, 9.6), (18.2, 12.9), (19.6, 16.6), (21.0, 20.8)]


def dipper_layers():
    """일곱 별 (밝은 점 — 빛무리는 옅게) + 잇는 가는 선 + 왼쪽 아래 떨어지는 큰 별 (네 갈래 · 꼬리)"""
    p = dipper_points()
    lines = [(0, 1), (1, 2), (2, 3), (3, 0), (3, 4), (4, 5), (5, 6)]
    line = lambda g: union(*[seg(p[i][0], p[i][1], p[j][0], p[j][1], 0.28 + g * 0.2) for i, j in lines])
    layers = [(line, (140, 160, 230), (96, 116, 196), (34, 38, 58))]
    for i, (x, y) in enumerate(p):
        r = 1.0 if i in (0, 6) else 0.8
        layers.append((lambda g, x=x, y=y, r=r: disc(x, y, r + g * 0.7), (255, 255, 240), (255, 226, 140), (52, 60, 96)))
    trail = lambda g: seg(10.6, 17.4, 13.4, 21.6, 0.7 + g * 0.6)
    layers.append((trail, (255, 236, 170), (220, 170, 70), (60, 48, 30)))
    layers.append((lambda g: union(star4(13.6, 22.0, 2.4 + g, 0.42 + g * 0.3), disc(13.6, 22.0, 1.0 + g)), (255, 255, 246), (255, 214, 96), (120, 86, 24)))
    return layers


def skyeye_layers():
    """하늘의 눈: 해빛 눈 (흰자 · 해빛 눈동자) 둘레로 빛살 여덟 + 아래로 퍼지며 내리꽂는 빛줄기 셋"""
    cy = 12.4

    def eye(g):
        def f(x, y):
            u = (x - 16) / (5.0 + g)
            if abs(u) >= 1:
                return False
            lim = (2.7 + g) * (1 - u * u) ** 0.85
            return abs(y - cy) <= lim
        return f

    def rays(g):
        fs = []
        for k in range(8):
            a = k * math.pi / 4
            r0, r1 = (6.0, 7.6) if k % 2 == 0 else (5.2, 6.4)
            if k == 2:  # 아래쪽은 빛줄기 자리
                continue
            fs.append(seg(16 + math.cos(a) * r0, cy + math.sin(a) * r0 * 0.8, 16 + math.cos(a) * r1, cy + math.sin(a) * r1 * 0.8, 0.55 + g * 0.45))
        return union(*fs)
    beam = lambda g: seg(16, cy + 3.2, 16, 25.0, 0.75 + g * 0.6)
    side = lambda g: union(seg(15.0, cy + 3.0, 11.8, 24.4, 0.34 + g * 0.35), seg(17.0, cy + 3.0, 20.2, 24.4, 0.34 + g * 0.35))
    return [(side, (236, 210, 150), (170, 130, 60), (44, 36, 26)), (beam, (255, 252, 230), (250, 214, 120), (80, 56, 20)), (rays, (255, 240, 180), (255, 200, 80), (110, 66, 10)),
            (eye, (252, 252, 244), (255, 206, 92), (110, 70, 16)), (lambda g: disc(16, cy, 1.7 + g), (255, 196, 70), (255, 150, 30), (120, 60, 0)),
            (lambda g: disc(16, cy, 0.7), (60, 30, 10), (60, 30, 10), (60, 30, 10))]


LIMITED_GLYPHS = {"arcana": arcana_layers, "dipper": dipper_layers, "skyeye": skyeye_layers}


def prism_frame(stone):
    """붉은 테두리 · 빛무리 → 무지갯빛 (가운데에서 본 각도마다 색, 밝기 · 알파는 그대로)"""
    import colorsys
    out = stone.copy()
    for y in range(32):
        for x in range(32):
            r, g, b, a = out[y, x]
            if a <= 0 or not (r > 80 and r > g * 1.7 and r > b * 1.7):
                continue
            hue = (math.degrees(math.atan2(y - 15.5, x - 15.5)) + 360 + 90) % 360 / 360
            rr, gg, bb = colorsys.hsv_to_rgb(hue, 0.72, min(1.0, r / 255 * 1.08))
            out[y, x, :3] = (rr * 255, gg * 255, bb * 255)
    return out


def draw_layers(stone, layers):
    img = Image.fromarray(np.clip(np.round(stone), 0, 255).astype(np.uint8), "RGBA")
    for shape, core, mid, glow in layers:
        img = draw_glyph(np.array(img).astype(float), shape, core, mid, glow)
    return img


def main():
    rules = json.load(open(os.path.join(CORE, "rules.json"), encoding="utf-8"))
    more = json.load(open(os.path.join(CORE, "rules_more.json"), encoding="utf-8"))
    rb = json.load(open(os.path.join(CORE, "rune_break.json"), encoding="utf-8"))
    addon = rules["RUNE_FAMILY_ORDER"]
    order = more.get("RUNE_FAMILY_ORDER", addon)
    made = []
    for fam in order:
        if fam in addon:
            continue
        if fam not in GLYPHS:
            raise SystemExit(f"{fam}: 문양이 없음 (GLYPHS)")
        shape, core, mid, glow = GLYPHS[fam]
        for g in GRADES:
            name = f"rune_{fam}_{g}"
            draw_glyph(blank_stone(g, addon), shape, core, mid, glow).save(os.path.join(TEX, f"{name}.png"))
            item_files(name, int(rb["max"]))
            made.append(name)
    # 0.16.0 한정 초월 룬 (초월 하나씩)
    for fam in more.get("RUNE_LIMITED_ORDER", []):
        if fam not in LIMITED_GLYPHS:
            raise SystemExit(f"{fam}: 문양이 없음 (LIMITED_GLYPHS)")
        name = f"rune_{fam}_transcend"
        stone = prism_frame(blank_stone("transcend", addon))
        draw_layers(stone, LIMITED_GLYPHS[fam]()).save(os.path.join(TEX, f"{name}.png"))
        item_files(name, int(rb["max"]))
        made.append(name)
    print(f"rune items: {len(made)} ({', '.join(made)})")


if __name__ == "__main__":
    main()
