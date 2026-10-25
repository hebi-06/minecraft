#!/usr/bin/env python3
"""0.43.0 아티팩트 (신화 유물 열) 아이콘 (32×32 — 장신구 · 전리품 아이콘처럼: 둥근 빛 바탕 · 진한 테두리 · 왼쪽 위 빛 · 반짝임) + 아티팩트 판 창 그림.

종류 열 × 등급 다섯 = 50:
- src/main/resources/assets/rpg/textures/item/relic/<종류>_<등급>.png
- src/main/resources/assets/rpg/items/relic_<종류>_<등급>.json · models/item/relic_<종류>_<등급>.json (generated)
종류마다 제 재료 색 (가죽 띠 · 나무 배 · 금 사과 · 돌 샘 · 쇠 장갑 · 청동 판 · 금 고리 · 상아 뿔 · 비단 끈 · 금 목걸이) 은 그대로 두고, 등급은 바탕 빛 · 보석 ·
테 금속 색으로 (일반 회색 · 희귀 파랑 · 고귀 보라 · 영웅 주황 · 초월 업화 — 장신구와 같은 등급 색). 초월은 불티 · 붉은 테.
아티팩트 판 창 그림 (assets/mmorpg/textures/gui/sprites/relic/):
- board.png (100×100 — 판: 남빛 돌판 · 금테 두 줄 · 가운데 옅은 룬 원 · 모서리 매듭) · socket.png (24×24 — 돌을 판 홈: 빗면 · 어두운 안 · 금빛 안테)

사용: python3 tools/relic_icons.py [--preview 경로]
"""
import importlib.util
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets")
_spec = importlib.util.spec_from_file_location("loot_icons", os.path.join(ROOT, "tools", "loot_icons.py"))
L = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(L)
Icon, clamp, mix, shade = L.Icon, L.clamp, L.mix, L.shade

TYPES = ["megingjord", "skidbladnir", "idunn", "mimir", "jarngreipr", "aegishjalmr", "draupnir", "gjallarhorn", "gleipnir", "brisingamen"]
GRADES = ["common", "rare", "noble", "hero", "transcend"]

# 등급마다: 테 금속 · 금속 빛 · 보석 · 보석 빛 · 바탕 빛 (장신구와 같은 계열)
PAL = {
    "common": dict(metal=(176, 182, 196), metal_hi=(236, 240, 250), gem=(206, 220, 236), gem_hi=(255, 255, 255), glow=(200, 200, 212)),
    "rare": dict(metal=(150, 172, 214), metal_hi=(224, 236, 255), gem=(52, 108, 236), gem_hi=(170, 210, 255), glow=(76, 139, 255)),
    "noble": dict(metal=(176, 150, 214), metal_hi=(236, 220, 255), gem=(150, 74, 236), gem_hi=(228, 184, 255), glow=(182, 108, 255)),
    "hero": dict(metal=(232, 176, 52), metal_hi=(255, 236, 150), gem=(236, 84, 34), gem_hi=(255, 196, 120), glow=(255, 154, 46)),
    "transcend": dict(metal=(120, 52, 46), metal_hi=(255, 150, 70), gem=(255, 72, 36), gem_hi=(255, 240, 196), glow=(255, 74, 61), vein=(255, 128, 40)),
}

LEATHER = (128, 78, 44)
WOOD = (152, 100, 54)
GOLD = (236, 184, 60)
GOLD_HI = (255, 238, 156)
STONE = (132, 132, 142)
IRON = (148, 154, 168)
BRONZE = (118, 88, 56)
IVORY = (234, 222, 192)
APPLE = (246, 194, 50)
LEAF = (92, 172, 70)
SILK = (224, 228, 242)
AMBER = (240, 150, 40)


def metal(ic, cells, p, seed, grad=0.22):
    ic.fill(cells, p["metal"], light=0.35, dark=0.35, grad=grad, noise=4, seed=seed)
    if "vein" in p:  # 초월: 어두운 금속 속을 흐르는 업화 줄
        for (x, y) in cells:
            k = (x * 3 + y * 5 + seed) % 13
            if k == 0:
                ic.put(x, y, p["vein"])
            elif k == 1:
                ic.put(x, y, mix(p["vein"], p["metal"], 0.55))


def gem(ic, cells, p, seed):
    """보석: 칸들을 보석 색으로 · 왼쪽 위 빛 한 점"""
    ic.fill(cells, p["gem"], light=0.45, dark=0.35, grad=0.3, noise=3, seed=seed)
    if not cells:
        return
    xs = [x for x, _ in cells]
    ys = [y for _, y in cells]
    hx = round(min(xs) + (max(xs) - min(xs)) * 0.32)
    hy = round(min(ys) + (max(ys) - min(ys)) * 0.3)
    if (hx, hy) in cells:
        ic.put(hx, hy, p["gem_hi"])


def embers(ic, pts):
    """초월: 위로 오르는 불티"""
    for i, (x, y) in enumerate(pts):
        if 0 <= x < 32 and 0 <= y < 32 and ic.px[y][x] is None:
            ic.put(x, y, (255, 214, 120) if i % 2 == 0 else (255, 140, 52))


def stroke(ic, pts, r):
    """굵은 선 가림 (점마다 반지름 r 원)"""
    def draw(d):
        for (x, y, rr) in pts:
            d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=255)
    return ic.mask(draw)


# ---------------------------------------------------------------- 모양


def megingjord(ic, p, g):
    # 힘의 허리띠: 가로로 휜 굵은 가죽 띠 (위아래 바느질 · 쇠 징) + 가운데 큰 버클 (등급 금속) · 버클 속 보석
    band = ic.mask(lambda d: d.polygon([(1, 12), (8, 14), (16, 15), (24, 14), (30, 12), (30, 21), (24, 23), (16, 24), (8, 23), (1, 21)], fill=255))
    ic.fill(band, LEATHER, light=0.25, dark=0.35, grad=0.25, noise=5, seed=1)
    for x in range(2, 30, 2):  # 바느질
        for (cx, y0) in ((x, 13 + (abs(16 - x) < 9) * 1 + (abs(16 - x) < 4) * 1), (x, 21 + (abs(16 - x) < 9) * 1 + (abs(16 - x) < 4) * 1)):
            if (cx, y0) in band:
                ic.put(cx, y0, mix(LEATHER, (255, 230, 190), 0.45))
    for (sx, sy) in ((4, 16), (7, 17), (25, 17), (28, 16)):  # 징
        ic.put(sx, sy, p["metal_hi"])
        ic.put(sx + 1, sy + 1, shade(p["metal"], 0.7))
    frame = ic.mask(lambda d: d.rounded_rectangle((10, 9, 22, 27), radius=2, fill=255))
    metal(ic, frame, p, 2, grad=0.15)
    inner = ic.mask(lambda d: d.rectangle((12, 11, 20, 25), fill=255))
    ic.fill(inner, shade(LEATHER, 0.62), light=0.1, dark=0.2, noise=3, seed=3)
    stone = ic.mask(lambda d: d.polygon([(16, 12), (20, 18), (16, 24), (12, 18)], fill=255))
    gem(ic, stone, p, 4)
    for (x, y) in ((10, 9), (22, 9), (10, 27), (22, 27)):
        ic.put(x, y, p["metal_hi"])
    sp = [(27, 7, p["gem_hi"]), (4, 26, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 8), (13, 6), (19, 6), (11, 4), (21, 4)])
        sp.append((28, 3, (255, 220, 140)))
    return sp


def skidbladnir(ic, p, g):
    # 접히는 배: 용머리 뱃머리 · 나무 배 몸 (널 줄) · 둥근 방패 줄 (등급 색) · 돛대 · 줄무늬 돛 (등급 보석 색) · 아래 물결
    hull = ic.mask(lambda d: (d.polygon([(2, 19), (30, 19), (27, 25), (6, 25)], fill=255),
                              d.polygon([(2, 19), (1, 13), (3, 10), (6, 11), (5, 14), (6, 19)], fill=255),
                              d.polygon([(30, 19), (31, 14), (29, 13), (27, 19)], fill=255)))
    ic.fill(hull, WOOD, light=0.3, dark=0.35, grad=0.2, noise=5, seed=5)
    for x in range(5, 28):
        for y in (21, 23):
            if (x, y) in hull:
                ic.put(x, y, shade(WOOD, 0.72))
    ic.put(3, 11, (255, 220, 120))  # 용 눈
    mast = {(16, y) for y in range(3, 19)} | {(15, y) for y in range(3, 19)}
    ic.fill(mast, shade(WOOD, 0.85), light=0.2, dark=0.3, noise=3, seed=6)
    sail = ic.mask(lambda d: d.polygon([(8, 5), (24, 5), (25, 16), (16, 17), (7, 16)], fill=255))
    for (x, y) in sail:
        base = SILK if ((x - 7) // 3) % 2 == 0 else p["gem"]
        t = (y - 5) / 12
        c = shade(base, 1.08 - 0.25 * t)
        if (x - 1, y) not in sail or (x, y - 1) not in sail:
            c = mix(c, (255, 255, 255), 0.25)
        ic.put(x, y, c)
    for i, x in enumerate(range(8, 27, 4)):  # 방패
        cells = ic.mask(lambda d, x=x: d.ellipse((x - 2, 18, x + 1, 21), fill=255))
        if i % 2 == 0:
            gem(ic, cells, p, 20 + i)
        else:
            metal(ic, cells, p, 20 + i, grad=0.1)
    for x in range(4, 29):  # 물결
        y = 27 + round(math.sin(x * 0.9) * 0.8)
        if ic.px[y][x] is None:
            ic.put(x, y, (150, 200, 255) if x % 3 else (230, 246, 255))
    sp = [(28, 4, p["gem_hi"]), (4, 6, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 1), (12, 3), (20, 2), (26, 8), (6, 8)])
        sp.append((29, 9, (255, 220, 140)))
    return sp


def idunn(ic, p, g):
    # 젊음의 사과: 큰 금빛 사과 · 겉에 등급 색으로 빛나는 생명 룬 (세 갈래) · 꼭지 · 잎 둘
    body = ic.mask(lambda d: (d.ellipse((5, 9, 18, 29), fill=255), d.ellipse((13, 9, 27, 29), fill=255), d.ellipse((8, 12, 24, 30), fill=255)))
    ic.fill(body, APPLE, light=0.35, dark=0.38, grad=0.3, noise=4, seed=7)
    rune = [(16, y) for y in range(15, 27)] + [(15, 18), (14, 17), (13, 16), (17, 18), (18, 17), (19, 16)]
    for (x, y) in rune:  # 룬 둘레를 조금 어둡게 (금 위에서도 보이게)
        for dx, dy in ((1, 0), (-1, 0), (0, 1)):
            q = (x + dx, y + dy)
            if q in body and q not in rune:
                ic.put(q[0], q[1], shade(APPLE, 0.72))
    for (x, y) in rune:
        ic.put(x, y, p["gem"])
    for (x, y) in ((16, 15), (13, 16), (19, 16)):
        ic.put(x, y, p["gem_hi"])
    for (x, y) in ((9, 14), (10, 13), (8, 16)):  # 빛
        ic.put(x, y, (255, 250, 214))
    stem = {(16, 5), (16, 6), (16, 7), (17, 8), (16, 9)}
    ic.fill(stem, (110, 72, 40), light=0.1, dark=0.2, noise=2, seed=8)
    leaf = ic.mask(lambda d: d.polygon([(17, 7), (21, 3), (26, 3), (22, 8)], fill=255))
    ic.fill(leaf, LEAF, light=0.35, dark=0.3, noise=3, seed=9)
    ic.line([(18, 7), (24, 4)], shade(LEAF, 0.7), leaf)
    leaf2 = ic.mask(lambda d: d.polygon([(15, 8), (11, 5), (7, 6), (11, 9)], fill=255))
    leaf2 = {c for c in leaf2 if ic.px[c[1]][c[0]] is None}
    ic.fill(leaf2, shade(LEAF, 0.85), light=0.3, dark=0.3, noise=3, seed=10)
    sp = [(4, 6, p["gem_hi"]), (28, 27, mix(p["gem_hi"], (255, 255, 255), 0.5)), (27, 12, p["gem_hi"])]
    if g == "transcend":
        embers(ic, [(27, 10), (29, 14), (25, 6), (6, 3), (12, 2)])
        sp.append((3, 22, (255, 220, 140)))
    return sp


def mimir(ic, p, g):
    # 지혜의 샘: 돌 우물 (돌 줄 · 이음) · 위 테 · 등급 색으로 빛나는 물 · 물에 비친 눈 · 위로 오르는 빛
    wall = ic.mask(lambda d: d.polygon([(5, 16), (27, 16), (26, 28), (6, 28)], fill=255))
    ic.fill(wall, STONE, light=0.25, dark=0.35, grad=0.25, noise=6, seed=10)
    for (x, y) in wall:
        if y in (20, 24):
            ic.put(x, y, shade(STONE, 0.68))
        seam = (x + (4 if 20 < y < 24 else 0)) % 8 == 0
        if seam and y not in (20, 24):
            ic.put(x, y, shade(STONE, 0.75))
    rim = ic.mask(lambda d: d.ellipse((3, 11, 29, 20), fill=255))
    ic.fill(rim, shade(STONE, 1.12), light=0.3, dark=0.3, grad=0.1, noise=5, seed=11)
    water = ic.mask(lambda d: d.ellipse((7, 12, 25, 18), fill=255))
    ic.fill(water, p["gem"], light=0.4, dark=0.2, grad=0.2, noise=2, seed=12)
    eye = ic.mask(lambda d: d.polygon([(11, 15), (14, 13), (18, 13), (21, 15), (18, 17), (14, 17)], fill=255))
    for (x, y) in eye:
        ic.put(x, y, mix(p["gem_hi"], (255, 255, 255), 0.6))
    for (x, y) in ((15, 15), (16, 15), (15, 14), (16, 14), (16, 16), (15, 16)):
        ic.put(x, y, shade(p["gem"], 0.45))
    ic.put(15, 14, (255, 255, 255))
    for (x, y) in ((10, 8), (22, 7), (16, 4)):
        ic.put(x, y, p["gem_hi"])
    sp = [(27, 6, p["gem_hi"]), (4, 7, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(10, 6), (22, 5), (16, 1), (25, 9), (7, 9)])
        sp.append((29, 25, (255, 220, 140)))
    return sp


def jarngreipr(ic, p, g):
    # 쇠장갑: 쥔 주먹 (손등 · 손가락 마디 넷 · 엄지) · 등급 금속 손목 테 · 손등 보석 · 못
    back = ic.mask(lambda d: d.rounded_rectangle((8, 10, 24, 23), radius=3, fill=255))
    ic.fill(back, IRON, light=0.35, dark=0.38, grad=0.25, noise=5, seed=13)
    for i, x in enumerate((9, 13, 17, 21)):
        f = ic.mask(lambda d, x=x: d.rounded_rectangle((x, 5, x + 3, 12), radius=1, fill=255))
        ic.fill(f, shade(IRON, 1.05), light=0.4, dark=0.35, grad=0.2, noise=4, seed=14 + i)
        ic.put(x, 8, shade(IRON, 0.62))
        ic.put(x + 3, 8, shade(IRON, 0.62))
    thumb = ic.mask(lambda d: d.polygon([(4, 13), (9, 12), (9, 20), (5, 19)], fill=255))
    ic.fill(thumb, shade(IRON, 0.95), light=0.35, dark=0.35, noise=4, seed=18)
    cuff = ic.mask(lambda d: d.rounded_rectangle((9, 22, 23, 30), radius=2, fill=255))
    metal(ic, cuff, p, 19, grad=0.18)
    for x in range(10, 23):
        ic.put(x, 25, shade(p["metal"], 0.7))
        ic.put(x, 24, p["metal_hi"])
    stone = ic.mask(lambda d: d.polygon([(16, 13), (19, 16), (16, 20), (13, 16)], fill=255))
    gem(ic, stone, p, 20)
    for (x, y) in ((10, 12), (22, 12), (10, 21), (22, 21)):
        ic.put(x, y, (232, 236, 246))
    sp = [(27, 5, p["gem_hi"]), (3, 27, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(11, 3), (15, 2), (19, 3), (23, 4), (6, 9)])
        sp.append((28, 12, (255, 220, 140)))
    return sp


def aegishjalmr(ic, p, g):
    # 공포의 투구 (마법 문양): 둥근 청동 판 · 등급 금속 테 · 가운데에서 뻗은 여덟 갈래 빛 문양 (끝은 갈퀴) · 가운데 고리
    plate = ic.mask(lambda d: d.ellipse((2, 2, 29, 29), fill=255))
    ic.fill(plate, BRONZE, light=0.3, dark=0.35, grad=0.2, noise=5, seed=21)
    rim = plate - ic.mask(lambda d: d.ellipse((4, 4, 27, 27), fill=255))
    metal(ic, rim, p, 22, grad=0.15)
    cx, cy = 15.5, 15.5
    lit = mix(p["gem_hi"], p["gem"], 0.25)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        ux, uy = math.cos(a), math.sin(a)
        for s in range(3, 11):
            ic.put(round(cx + ux * s), round(cy + uy * s), lit if s < 10 else p["gem_hi"])
        # 갈퀴: 끝에서 양옆으로
        ex, ey = cx + ux * 10, cy + uy * 10
        vx, vy = -uy, ux
        for t in (-1.6, 1.6):
            ic.put(round(ex + vx * t), round(ey + vy * t), p["gem"])
            ic.put(round(ex + vx * t - ux), round(ey + vy * t - uy), shade(p["gem"], 0.8))
        # 가운데 쯤 가로 막대
        mx, my = cx + ux * 6.5, cy + uy * 6.5
        for t in (-1.2, 1.2):
            ic.put(round(mx + vx * t), round(my + vy * t), shade(lit, 0.9))
    ring = ic.mask(lambda d: d.ellipse((12, 12, 19, 19), fill=255)) - ic.mask(lambda d: d.ellipse((14, 14, 17, 17), fill=255))
    for (x, y) in ring:
        ic.put(x, y, p["gem_hi"])
    sp = [(29, 4, p["gem_hi"]), (2, 28, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 0), (8, 1), (24, 1), (30, 9), (1, 9)])
        sp.append((30, 30, (255, 220, 140)))
    return sp


def draupnir(ic, p, g):
    # 아홉째 밤의 금고리: 굵은 금 팔찌 고리 (꼬인 빛 줄) · 위 보석 (등급) · 옆에 떨어지는 작은 금고리 둘
    band = ic.mask(lambda d: d.ellipse((4, 8, 27, 28), fill=255)) - ic.mask(lambda d: d.ellipse((10, 13, 21, 23), fill=255))
    ic.fill(band, GOLD, light=0.38, dark=0.38, grad=0.25, noise=4, seed=23)
    for (x, y) in band:
        if (x + y) % 5 == 0:
            ic.put(x, y, mix(GOLD_HI, GOLD, 0.2))
        elif (x + y) % 5 == 1:
            ic.put(x, y, shade(GOLD, 0.78))
    for (x, y) in band:
        if y < 13:
            ic.put(x, y, shade(ic.px[y][x], 0.82))
    setting = ic.mask(lambda d: d.ellipse((12, 4, 20, 12), fill=255))
    metal(ic, setting, p, 24, grad=0.1)
    stone = ic.mask(lambda d: d.polygon([(16, 5), (19, 8), (16, 11), (13, 8)], fill=255))
    gem(ic, stone, p, 25)
    for i, (x0, y0) in enumerate(((23, 22), (1, 3))):
        small = ic.mask(lambda d, x0=x0, y0=y0: d.ellipse((x0, y0, x0 + 7, y0 + 6), fill=255)) - ic.mask(
            lambda d, x0=x0, y0=y0: d.ellipse((x0 + 2, y0 + 2, x0 + 5, y0 + 4), fill=255))
        small = {c for c in small if ic.px[c[1]][c[0]] is None}
        ic.fill(small, shade(GOLD, 0.95), light=0.35, dark=0.35, noise=3, seed=26 + i)
    sp = [(28, 6, p["gem_hi"]), (3, 27, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 2), (12, 1), (20, 1), (9, 4), (23, 4)])
        sp.append((29, 17, (255, 220, 140)))
    return sp


def gjallarhorn(ic, p, g):
    # 파수꾼의 나팔: 왼쪽 아래 입에서 오른쪽 위 나팔 입으로 휘며 굵어지는 상아 뿔 · 등급 금속 띠 셋 · 나팔 입 테 · 소리 물결
    pts = []
    for k in range(0, 61):
        t = k / 60
        x = (1 - t) ** 2 * 4 + 2 * (1 - t) * t * 6 + t * t * 24
        y = (1 - t) ** 2 * 28 + 2 * (1 - t) * t * 10 + t * t * 8
        pts.append((x, y, 1.2 + 3.8 * t ** 1.4, t))
    horn = stroke(ic, [(x, y, r) for (x, y, r, _) in pts], 0)
    ic.fill(horn, IVORY, light=0.35, dark=0.38, grad=0.2, noise=4, seed=27)
    for tb in (0.32, 0.56, 0.8):
        x, y, r, _ = min(pts, key=lambda q: abs(q[3] - tb))
        band = ic.mask(lambda d, x=x, y=y, r=r: d.ellipse((x - r - 0.6, y - r - 0.6, x + r + 0.6, y + r + 0.6), fill=255))
        nx, ny = min(pts, key=lambda q: abs(q[3] - tb - 0.03))[:2]
        dx, dy = nx - x, ny - y
        ln = math.hypot(dx, dy) or 1
        dx, dy = dx / ln, dy / ln
        cells = {(cx, cy) for (cx, cy) in band & horn if abs((cx - x) * dx + (cy - y) * dy) <= 0.9}
        metal(ic, cells, p, 28, grad=0.1)
    bell = ic.mask(lambda d: d.ellipse((20, 3, 29, 13), fill=255))
    metal(ic, bell & (bell - ic.mask(lambda d: d.ellipse((22, 5, 27, 11), fill=255))), p, 29, grad=0.1)
    for (x, y) in ic.mask(lambda d: d.ellipse((22, 5, 27, 11), fill=255)):
        ic.put(x, y, shade(IVORY, 0.42))
    for k in range(3):  # 소리 물결
        r = 3 + k * 2.2
        for a in range(-50, 50, 12):
            t = math.radians(a - 45)
            x = round(25 + (r + 3) * math.cos(t))
            y = round(8 + (r + 3) * math.sin(t))
            if 0 <= x < 32 and 0 <= y < 32 and ic.px[y][x] is None and k < 2:
                ic.put(x, y, mix(p["gem_hi"], (255, 255, 255), 0.4))
    sp = [(4, 6, p["gem_hi"]), (28, 27, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(30, 3), (31, 7), (27, 1), (19, 2), (8, 22)])
        sp.append((14, 27, (255, 220, 140)))
    return sp


def gleipnir(ic, p, g):
    # 묶는 끈: 8 자로 매듭진 비단 끈 (가장자리에 등급 색 윤기) · 아래로 흐르는 두 끝 · 반짝이
    pts = []
    for k in range(0, 121):
        t = k / 120 * math.tau
        s = math.sin(t)
        x = 16 + 12.5 * math.cos(t) / (1 + s * s)
        y = 12 + 9.5 * s * math.cos(t) / (1 + s * s)
        pts.append((x, y, 1.5))
    for k in range(0, 21):
        t = k / 20
        pts.append((16 - 6 * t + math.sin(t * 6) * 0.8, 12 + 16 * t, 1.4))
        pts.append((16 + 6 * t + math.sin(t * 6 + 1) * 0.8, 12 + 16 * t, 1.4))
    rib = stroke(ic, pts, 0)
    ic.fill(rib, SILK, light=0.25, dark=0.3, grad=0.15, noise=3, seed=30)
    for (x, y) in rib:
        edge = any((x + dx, y + dy) not in rib for dx, dy in ((1, 0), (0, 1)))
        if edge and (x * 2 + y) % 3 == 0:
            ic.put(x, y, p["gem"])
        elif edge:
            ic.put(x, y, mix(p["gem_hi"], SILK, 0.4))
    knot = ic.mask(lambda d: d.ellipse((13, 9, 19, 15), fill=255))
    ic.fill(knot, shade(SILK, 0.9), light=0.3, dark=0.35, noise=2, seed=31)
    ic.put(15, 11, p["gem_hi"])
    for (x, y) in ((5, 3), (27, 3), (24, 27), (7, 27)):
        if ic.px[y][x] is None:
            ic.put(x, y, p["gem_hi"])
    sp = [(29, 13, p["gem_hi"]), (2, 13, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 2), (11, 1), (21, 1), (9, 26), (23, 26)])
        sp.append((16, 30, (255, 220, 140)))
    return sp


def brisingamen(ic, p, g):
    # 여신의 목걸이: 위를 지나는 금 · 호박 구슬 줄 · 가운데 햇살 테 속 큰 보석 (등급) · 양옆 호박 물방울
    for k in range(0, 23):
        t = k / 22
        x = round(3 + 26 * t)
        y = round(3 + 13 * (1 - (2 * t - 1) ** 2) ** 0.9)
        c = GOLD_HI if k % 2 == 0 else AMBER
        ic.put(x, y, c)
        ic.put(x, y + 1, shade(c, 0.66))
        if k % 2 == 0:
            ic.put(x + 1, y, shade(c, 0.85))
    sun = ic.mask(lambda d: d.polygon([(16 + 9 * math.cos(a * math.pi / 8 - math.pi / 2) * (1 if a % 2 == 0 else 0.62),
                                        22 + 9 * math.sin(a * math.pi / 8 - math.pi / 2) * (1 if a % 2 == 0 else 0.62)) for a in range(16)], fill=255))
    ic.fill(sun, GOLD, light=0.4, dark=0.35, grad=0.2, noise=3, seed=32)
    stone = ic.mask(lambda d: d.ellipse((12, 18, 20, 26), fill=255))
    gem(ic, stone, p, 33)
    for i, x0 in enumerate((6, 23)):
        drop = ic.mask(lambda d, x0=x0: d.polygon([(x0 + 1.5, 15), (x0 + 3.5, 19), (x0 + 1.5, 22), (x0 - 0.5, 19)], fill=255))
        drop = {c for c in drop if ic.px[c[1]][c[0]] is None}
        ic.fill(drop, AMBER, light=0.4, dark=0.3, noise=3, seed=34 + i)
    sp = [(28, 24, p["gem_hi"]), (3, 25, mix(p["gem_hi"], (255, 255, 255), 0.5)), (16, 12, GOLD_HI)]
    if g == "transcend":
        embers(ic, [(16, 10), (11, 11), (21, 11), (9, 26), (23, 27)])
        sp.append((29, 3, (255, 220, 140)))
    return sp


PAINT = {"megingjord": megingjord, "skidbladnir": skidbladnir, "idunn": idunn, "mimir": mimir, "jarngreipr": jarngreipr,
         "aegishjalmr": aegishjalmr, "draupnir": draupnir, "gjallarhorn": gjallarhorn, "gleipnir": gleipnir, "brisingamen": brisingamen}


def icon(t, g):
    p = PAL[g]
    ic = Icon(p["glow"])
    sparkles = PAINT[t](ic, p, g)
    sparkles = [(x, y, c) for (x, y, c) in sparkles if 0 <= x < 32 and 0 <= y < 32]
    im = ic.render(sparkles)
    if g == "transcend":  # 업화: 바깥 테두리를 옅은 붉은 불빛으로
        px = im.load()
        for y in range(32):
            for x in range(32):
                if px[x, y][3] == 255 and px[x, y][:3] == L.OUT_DARK[:3]:
                    px[x, y] = (70, 16, 14, 255)
    return im


# ---------------------------------------------------------------- 창 그림


def board_sprite(w=100, h=100):
    """판: 남빛 돌판 (가운데 밝게 · 돌 얼룩) · 가운데 옅은 룬 원 · 금테 두 줄 · 모서리 매듭"""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    rnd = random.Random(43)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / (w * 0.6), (y - cy) / (h * 0.6))
            c = mix((40, 46, 86), (14, 15, 30), min(1, d))
            n = rnd.randint(-5, 5)
            px[x, y] = (*tuple(clamp(v + n) for v in c), 255)
    for r, a in ((38, 60), (33, 38), (20, 30)):
        for k in range(900):
            t = k / 900 * math.tau
            x = round(cx + r * math.cos(t))
            y = round(cy + r * math.sin(t))
            if 0 <= x < w and 0 <= y < h:
                o = px[x, y]
                px[x, y] = (*mix(o[:3], (236, 196, 104), a / 255), 255)
    for k in range(16):  # 룬 획
        t = k / 16 * math.tau + 0.2
        for s in range(0, 3):
            x = round(cx + (34.5 + s * 0.8) * math.cos(t + s * 0.05))
            y = round(cy + (34.5 + s * 0.8) * math.sin(t + s * 0.05))
            if 0 <= x < w and 0 <= y < h:
                o = px[x, y]
                px[x, y] = (*mix(o[:3], (255, 226, 150), 0.45), 255)
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                px[x, y] = (150, 110, 46, 255) if (x == w - 1 or y == h - 1) else (236, 196, 104, 255)
            elif edge == 1:
                px[x, y] = (24, 20, 14, 255)
            elif edge == 3:
                o = px[x, y]
                px[x, y] = (*mix(o[:3], (214, 170, 80), 0.55), 255)
    for (x0, y0) in ((2, 2), (w - 7, 2), (2, h - 7), (w - 7, h - 7)):  # 모서리 매듭
        for dy in range(5):
            for dx in range(5):
                if (dx + dy) % 2 == 0 and (dx in (0, 4) or dy in (0, 4) or (dx == 2 and dy == 2)):
                    px[x0 + dx, y0 + dy] = (255, 222, 140, 255)
    return im


def socket_sprite():
    """돌을 판 홈 (24×24): 바깥 어두운 테 · 빗면 (왼쪽 위 밝게 · 오른쪽 아래 어둡게) · 금빛 안테 · 어두운 안 (가운데 조금 밝게)"""
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    px = im.load()
    for y in range(24):
        for x in range(24):
            edge = min(x, y, 23 - x, 23 - y)
            if edge == 0:
                c = (12, 12, 20, 255)
            elif edge == 1:
                c = (96, 100, 128, 255) if (x == 1 or y == 1) else (30, 30, 44, 255)
            elif edge == 2:
                c = (70, 72, 98, 255) if (x == 2 or y == 2) else (40, 40, 58, 255)
            elif edge == 3:
                c = (196, 156, 76, 255) if (x + y) % 6 else (255, 222, 140, 255)
            else:
                d = math.hypot(x - 11.5, y - 11.5) / 11
                c = (*mix((30, 34, 62), (10, 11, 22), min(1, d)), 255)
            px[x, y] = c
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    tex = os.path.join(RES, "rpg", "textures", "item", "relic")
    os.makedirs(tex, exist_ok=True)
    icons = {}
    for t in TYPES:
        for g in GRADES:
            im = icon(t, g)
            icons[(t, g)] = im
            im.save(os.path.join(tex, f"{t}_{g}.png"))
            name = f"relic_{t}_{g}"
            write_json(os.path.join(RES, "rpg", "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
            write_json(os.path.join(RES, "rpg", "models", "item", f"{name}.json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/relic/{t}_{g}"}})
    gui = os.path.join(RES, "mmorpg", "textures", "gui", "sprites", "relic")
    os.makedirs(gui, exist_ok=True)
    board_sprite().save(os.path.join(gui, "board.png"))
    socket_sprite().save(os.path.join(gui, "socket.png"))
    if preview:
        k = 4
        cols = len(GRADES)
        sheet = Image.new("RGBA", (cols * (32 * k + 8) + 8, len(TYPES) * (32 * k + 8) + 8), (46, 46, 52, 255))
        for i, t in enumerate(TYPES):
            for j, g in enumerate(GRADES):
                im = icons[(t, g)].resize((32 * k, 32 * k), Image.NEAREST)
                sheet.paste(im, (8 + j * (32 * k + 8), 8 + i * (32 * k + 8)), im)
        sheet.save(preview)
        b = board_sprite()
        s = socket_sprite()
        for i in range(3):
            for j in range(3):
                b.paste(s, (4 + j * 34, 4 + i * 34), s)
        b.resize((b.width * 4, b.height * 4), Image.NEAREST).save(preview.replace(".png", "_board.png"))
        small = Image.new("RGBA", (len(TYPES) * 18 + 2, 18 * 5 + 2), (46, 46, 52, 255))
        for i, t in enumerate(TYPES):
            for j, g in enumerate(GRADES):
                im = icons[(t, g)].resize((16, 16), Image.BOX)
                small.paste(im, (2 + i * 18, 2 + j * 18), im)
        small.resize((small.width * 3, small.height * 3), Image.NEAREST).save(preview.replace(".png", "_16px.png"))
    print("relic icons:", len(icons), "· gui sprites: board · socket")


if __name__ == "__main__":
    main()
