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
- 0.16.0 한정 초월 룬 (RUNE_LIMITED_ORDER — 초월 하나씩 · 0.17.0 무간참역 · 0.18.0 대지 가르기 · 0.19.0 세계수의 가호 · 0.20.0 심연의 낙인 더함): 같은 초월 돌 위에 여러 색 문양 (LIMITED_GLYPHS — 층마다 심지 · 빛 · 빛무리 색),
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


def endless_layers():
    """0.17.0 무간참역: 끊어진 보랏빛 고리 (영역) 위로 엇갈린 참격 여섯 (흰 심 · 먹빛 테두리 — / 셋 · \\ 셋) + 가장 긴 흰 일섬 한 줄"""
    cx, cy = 16.0, 15.6

    def ring(g):
        def f(x, y):
            if abs(math.hypot(x - cx, y - cy) - 6.4) > 0.36 + g * 0.3:
                return False
            a = math.degrees(math.atan2(y - cy, x - cx)) % 90
            return 12 < a < 78  # 네 토막 (사이가 끊김)
        return f
    cuts = [((9.2, 14.4), (16.4, 9.2), 0.6), ((15.4, 22.8), (22.8, 17.4), 0.6), ((10.0, 9.6), (14.6, 14.2), 0.5), ((17.4, 17.2), (21.8, 21.6), 0.5),
            ((19.2, 8.0), (22.6, 11.4), 0.42), ((9.4, 18.6), (12.4, 21.6), 0.42)]
    small = lambda g: union(*[seg(p0[0], p0[1], p1[0], p1[1], w + g * 0.45) for p0, p1, w in cuts])
    big = lambda g: seg(7.4, 21.4, 24.0, 9.0, 1.0 + g * 0.6)
    return [(ring, (200, 176, 255), (122, 86, 210), (30, 16, 52)),
            (small, (255, 255, 255), (214, 206, 244), (10, 6, 18)),
            (big, (255, 255, 255), (236, 230, 255), (20, 10, 38))]


def terra_layers():
    """0.18.0 대지 가르기: 칼끝이 아래로 꽂힌 넓은 돌 대검 (돌 빛 · 어두운 테두리) + 칼날 가운데 호박빛 맥 + 칼끝에서 좌우로 갈라진 땅 (호박빛 틈)"""
    cx = 16.0
    tip = 22.4

    def crack(g):
        left = [(cx, tip), (13.6, 22.9), (12.0, 21.9), (9.8, 22.8), (8.0, 21.8), (6.0, 22.6)]
        right = [(cx, tip), (18.4, 21.8), (20.0, 22.9), (22.2, 21.9), (24.0, 22.8), (26.0, 22.0)]
        w = 0.8 + g * 0.55
        parts = [seg(a[0], a[1], b[0], b[1], w * (1 - 0.08 * i)) for path in (left, right) for i, (a, b) in enumerate(zip(path, path[1:]))]
        parts += [seg(12.0, 21.9, 11.2, 24.6, 0.55 + g * 0.45), seg(20.0, 22.9, 21.0, 25.0, 0.55 + g * 0.45), seg(cx, tip, cx + 0.4, 25.2, 0.7 + g * 0.5)]
        return union(*parts)

    def sword(g):
        def blade(x, y):
            if not (8.3 - g * 0.4 <= y <= tip + g * 0.5):
                return False
            half = 2.4 + g * 0.5
            if y > 19.4:
                half *= max(0.0, (tip + g * 0.5 - y) / (tip - 19.4 + g * 0.5))
            return abs(x - cx) <= half
        guard = lambda x, y: abs(y - 7.6) <= 0.75 + g * 0.45 and abs(x - cx) <= 3.8 + g * 0.45
        grip = lambda x, y: 5.0 - g * 0.4 <= y <= 7.0 and abs(x - cx) <= 0.7 + g * 0.45
        pommel = disc(cx, 4.5, 0.95 + g * 0.45)
        return union(blade, guard, grip, pommel)

    vein = lambda g: union(seg(cx, 9.0, cx, 20.6, 0.75 + g * 0.6), seg(cx, 11.6, cx + 1.5, 10.3, 0.45 + g * 0.4), seg(cx, 14.8, cx - 1.5, 13.5, 0.45 + g * 0.4),
                           seg(cx, 17.6, cx + 1.3, 16.5, 0.4 + g * 0.4))
    return [(crack, (255, 226, 140), (240, 122, 30), (70, 26, 6)),
            (sword, (172, 156, 132), (92, 76, 60), (20, 12, 6)),
            (vein, (255, 240, 176), (255, 150, 40), (110, 46, 8))]


def yggdrasil_layers():
    """0.19.0 세계수의 가호: 둥근 비췻빛 잎 지붕 (덩이 다섯) + 금빛 줄기 · 가지 · 아래로 퍼지는 뿌리 + 잎 사이 금빛 열매 셋 + 둘레의 가호 고리 (아래가 열린 호)"""
    cx = 16.0

    def halo(g):
        def f(x, y):
            if abs(math.hypot(x - cx, y - 15.2) - 9.3) > 0.3 + g * 0.3:
                return False
            a = math.degrees(math.atan2(y - 15.2, x - cx))
            return not (40 < a < 140)  # 아래 (뿌리 쪽) 는 열림
        return f

    def crown(g):
        return union(disc(cx, 8.9, 3.4 + g), disc(12.0, 11.3, 2.8 + g), disc(20.0, 11.3, 2.8 + g), disc(13.6, 13.4, 2.4 + g), disc(18.4, 13.4, 2.4 + g),
                     disc(cx, 12.2, 2.6 + g))

    def wood(g):
        trunk = seg(cx, 13.4, cx, 21.2, 1.2 + g * 0.6)
        arms = union(seg(cx, 15.6, 13.0, 12.6, 0.55 + g * 0.45), seg(cx, 15.8, 19.0, 12.8, 0.55 + g * 0.45))
        roots = union(seg(cx, 20.8, 11.2, 23.6, 0.75 + g * 0.5), seg(cx, 20.8, 20.8, 23.6, 0.75 + g * 0.5), seg(cx, 21.2, 13.6, 24.8, 0.6 + g * 0.45),
                      seg(cx, 21.2, 18.4, 24.8, 0.6 + g * 0.45), seg(cx, 21.2, cx, 25.0, 0.6 + g * 0.45))
        return union(trunk, arms, roots)

    fruits = lambda g: union(disc(12.6, 10.4, 0.7 + g * 0.55), disc(19.4, 10.4, 0.7 + g * 0.55), disc(cx, 7.4, 0.7 + g * 0.55))
    return [(halo, (236, 255, 214), (150, 220, 140), (24, 60, 30)),
            (crown, (150, 236, 150), (52, 168, 86), (10, 46, 22)),
            (wood, (255, 232, 168), (176, 120, 52), (44, 24, 6)),
            (fruits, (255, 255, 236), (255, 204, 72), (110, 70, 10))]


def abyss_layers():
    """0.20.0 심연의 낙인: 엇갈린 짙은 사슬 둘 (고리 마디) 위에 낙인 — 가시 여섯이 돋은 자홍 고리 · 가운데 아래를 향한 세모 · 세로로 갈라진 눈"""
    cx, cy = 16.0, 15.0

    def chains(g):
        parts = []
        for (x0, y0, x1, y1) in ((7.0, 24.0, 25.0, 7.0), (7.0, 7.0, 25.0, 24.0)):
            for k in range(7):
                t = (k + 0.5) / 7
                x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                parts.append(lambda px, py, x=x, y=y, g=g: abs(math.hypot(px - x, py - y) - 1.0) <= 0.32 + g * 0.3)
        return union(*parts)

    def seal(g):
        def f(x, y):
            r = math.hypot(x - cx, y - cy)
            if abs(r - 6.2) <= 0.42 + g * 0.35:
                return True
            a = (math.degrees(math.atan2(y - cy, x - cx)) + 90) % 60
            return 6.2 <= r <= 8.0 + g * 0.3 and min(a, 60 - a) <= (8.0 + g * 0.3 - r) * 4.5
        return f

    def tri(g):
        def f(x, y):
            top, bot, half = cy - 3.0 - g * 0.5, cy + 3.6 + g * 0.6, 3.6 + g * 0.6
            if not (top <= y <= bot):
                return False
            return abs(x - cx) <= half * (bot - y) / (bot - top)
        return f

    eye = lambda g: (lambda x, y: ((x - cx) / (0.9 + g * 0.5)) ** 2 + ((y - (cy - 0.6)) / (2.4 + g * 0.5)) ** 2 <= 1)
    return [(chains, (176, 120, 196), (96, 50, 120), (16, 4, 26)),
            (seal, (255, 190, 236), (226, 40, 150), (70, 6, 50)),
            (tri, (150, 40, 120), (70, 10, 70), (20, 2, 24)),
            (eye, (255, 236, 250), (255, 120, 210), (90, 10, 70))]


def poly(pts, g):
    """다각형 (점 목록) 안 — g 만큼 바깥으로 두껍게 (모서리까지 거리)"""
    n = len(pts)

    def inside(x, y):
        c = False
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
                c = not c
        return c

    def f(x, y):
        if inside(x, y):
            return True
        if g <= 0:
            return False
        for i in range(n):
            if seg(pts[i][0], pts[i][1], pts[(i + 1) % n][0], pts[(i + 1) % n][1], g * 2)(x, y):
                return True
        return False
    return f


def glacier_layers():
    """0.24.0 만년빙하: 땅의 얼음판에서 솟은 거대한 얼음 결정 셋 (가운데 높이 · 양옆은 바깥으로 기욺 — 끝이 뾰족한 육각 기둥) · 가운데 결정의 빛나는 모서리 ·
    위쪽 양옆의 눈꽃 반짝임 둘"""
    def spike(bx, by, tx, ty, half):
        # 밑동 (bx, by) → 끝 (tx, ty): 70% 까지 곧은 기둥, 그 위는 뾰족하게
        dx, dy = tx - bx, ty - by
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        nx, ny = -uy, ux
        sx, sy = bx + dx * 0.62, by + dy * 0.62
        return [(bx + nx * half, by + ny * half), (sx + nx * half, sy + ny * half), (tx, ty), (sx - nx * half, sy - ny * half), (bx - nx * half, by - ny * half)]

    crystals = lambda g: union(poly(spike(16.0, 23.4, 16.0, 6.2, 2.3), g), poly(spike(11.6, 23.4, 8.6, 12.4, 1.6), g), poly(spike(20.4, 23.4, 23.4, 12.4, 1.6), g))
    edge = lambda g: union(seg(16.0, 21.8, 16.0, 8.4, 0.5 + g * 0.5), seg(10.6, 21.6, 9.4, 15.0, 0.4 + g * 0.4), seg(21.4, 21.6, 22.6, 15.0, 0.4 + g * 0.4))
    floor = lambda g: seg(8.2, 23.8, 23.8, 23.8, 1.0 + g * 0.8)

    def flake(cx, cy, r):
        return lambda g: union(*[seg(cx - math.cos(a) * r, cy - math.sin(a) * r, cx + math.cos(a) * r, cy + math.sin(a) * r, 0.42 + g * 0.5)
                                 for a in (0.0, math.pi / 3, 2 * math.pi / 3)])

    return [(floor, (214, 240, 255), (96, 170, 236), (8, 36, 84)),
            (crystals, (196, 236, 255), (86, 176, 246), (10, 44, 104)),
            (edge, (255, 255, 255), (220, 246, 255), (60, 140, 220)),
            (flake(9.4, 8.2, 1.7), (255, 255, 255), (190, 236, 255), (40, 96, 160)),
            (flake(22.8, 7.4, 1.4), (255, 255, 255), (190, 236, 255), (40, 96, 160))]


LIMITED_GLYPHS = {"arcana": arcana_layers, "dipper": dipper_layers, "skyeye": skyeye_layers, "endless": endless_layers, "terra": terra_layers,
                  "yggdrasil": yggdrasil_layers, "abyss": abyss_layers, "glacier": glacier_layers}


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
