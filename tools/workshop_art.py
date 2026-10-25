#!/usr/bin/env python3
"""0.45.0 가게 창 (룬술사 · 상인) 의 패널 그림 — 대장장이 창 (tools/smith_art.py) 과 같은 크기 · 같은 그림체.

  python3 tools/workshop_art.py              # 모두 씀
  python3 tools/workshop_art.py --preview 그림.png   # 두 패널 (배경 + 받침 · 계산대) 미리 보기 (확인용)
  python3 tools/workshop_art.py --panel 그림.png     # 패널 미리 보기 + 상자 사람 (rune_channel · rune_merge · shop_hand · shop_count 의 열쇠)
                                                    # — SmithScreen 의 장면 상수 (NPC 자리 · 손이 닿는 곳) 맞추기

만들어지는 것 (assets/mmorpg/textures/gui/sprites/smith/)
- rune_backdrop.png  룬 공방 배경 112×194 (남보라 돌벽 · 아치 창의 달밤 · 책과 수정이 놓인 선반 · 바닥에 새긴 마법진)
- altar.png          룬 받침 40×50 (돌 기둥 · 빛나는 룬 새김 · 위의 수정 그릇 — 룬이 이 위에 떠오름)
- shop_backdrop.png  가게 배경 112×194 (나무 벽 · 물건 선반 · 무기 걸이 · 창의 햇빛 · 등)
- counter.png        계산대 112×52 (앞 — 상인 허리 아래를 가림: 윗판 · 널빤지 앞판 · 놋쇠 장식)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "smith")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smith_art as sa  # noqa: E402  (noise · fbm · mix · 패널 사영)

BW, BH = sa.BW, sa.BH
FLOOR = 150

# SmithScreen 의 장면 상수와 같은 값 (패널 기준 — NPC 발 · 1칸 · 몸 방향 · 카메라, 받침 · 계산대 자리)
RUNE = {"SMITH_X": 36, "FEET_Y": 168, "FIG": 44.0, "FACE": -70.0, "CAM": -8.0, "ALTAR_X": 54, "ALTAR_Y": 122}
SHOP = {"SMITH_X": 54, "FEET_Y": 176, "FIG": 44.0, "FACE": -18.0, "CAM": -6.0, "COUNTER_Y": 142}
AW, AH = 40, 50
CW, CH = 112, 52


def put(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x, :3] = c
        if img.shape[2] == 4:
            img[y, x, 3] = a


def line(img, x0, y0, x1, y1, c, a=255):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / max(1, n)
        put(img, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c, a)


def finish(img):
    out = np.zeros((img.shape[0], img.shape[1], 4))
    out[..., :3] = np.clip(img[..., :3], 0, 255)
    out[..., 3] = 255 if img.shape[2] == 3 else img[..., 3]
    return Image.fromarray(out.astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 룬 공방

def rune_backdrop():
    rng = random.Random(4501)
    nrng = np.random.default_rng(4502)
    img = np.zeros((BH, BW, 3))
    # 돌벽: 줄마다 높이 9 · 돌 너비 14 ~ 22, 남보라 (돌마다 조금씩)
    mortar = np.array((22, 19, 34), float)
    y0 = 0
    row = 0
    while y0 < FLOOR:
        h = 9
        x0 = -rng.randint(0, 12)
        while x0 < BW:
            w = rng.randint(14, 22)
            base = np.array((50, 45, 76), float) * rng.uniform(0.8, 1.14)
            for y in range(y0, min(FLOOR, y0 + h)):
                for x in range(max(0, x0), min(BW, x0 + w)):
                    if y == y0 or x == x0:
                        img[y, x] = mortar
                    else:
                        hl = 1.14 if y == y0 + 1 else (0.84 if y == y0 + h - 1 else 1.0)
                        img[y, x] = base * hl
            x0 += w
        y0 += h
        row += 1
    tex = sa.fbm(nrng, BW, BH, (8, 4, 2))
    img[:FLOOR] *= (0.82 + 0.32 * tex[:FLOOR])[..., None]

    # 바닥: 넓은 석판 (어두운 남색) · 줄눈
    for y in range(FLOOR, BH):
        k = (y - FLOOR) / (BH - FLOOR)
        off = 9 if (y - FLOOR) // 13 % 2 else 0
        for x in range(BW):
            c = np.array((44, 40, 62), float) * (1.05 if ((x + off) // 24) % 2 else 0.94) * (1 - 0.35 * k)
            if (y - FLOOR) % 13 == 0 or (x + off) % 24 == 0:
                c *= 0.7
            img[y, x] = c
    img[FLOOR] = (92, 84, 124)
    img[FLOOR + 1] = (64, 58, 90)
    img[FLOOR:] *= (0.9 + 0.2 * tex[FLOOR:])[..., None]

    # 아치 창 (오른쪽 위): 달밤 · 초승달 · 별 · 창살 — 돌 테
    wx0, wx1, wy0, wy1 = 62, 102, 10, 80
    cx = (wx0 + wx1 - 1) / 2
    rx = (wx1 - wx0) / 2
    for y in range(wy0, wy1):
        for x in range(wx0, wx1):
            arch = wy0 + rx - math.sqrt(max(0.0, rx * rx - (x - cx) ** 2))
            if y < arch:
                continue
            k = (y - wy0) / (wy1 - wy0)
            img[y, x] = np.array(sa.mix((14, 16, 44), (44, 34, 92), np.array(k)), float)
    for _ in range(26):
        x, y = rng.randint(wx0 + 2, wx1 - 3), rng.randint(wy0 + 8, wy1 - 6)
        arch = wy0 + rx - math.sqrt(max(0.0, rx * rx - (x - cx) ** 2))
        if y > arch + 1:
            img[y, x] = rng.choice([(220, 226, 255), (170, 180, 255), (255, 240, 200)])
    # 초승달
    mx, my, mr = 90, 30, 7.5
    for y in range(int(my - mr) - 1, int(my + mr) + 2):
        for x in range(int(mx - mr) - 1, int(mx + mr) + 2):
            d0 = math.hypot(x - mx, y - my)
            d1 = math.hypot(x - mx + 3.6, y - my - 1.5)
            if d0 <= mr and d1 > mr * 0.92:
                img[y, x] = (250, 244, 214) if d0 < mr - 1 else (200, 196, 176)
    # 달빛 (창 둘레로 퍼짐)
    yy, xx = np.mgrid[0:BH, 0:BW]
    d = np.hypot(xx - mx, (yy - my) * 1.2)
    img += (np.clip(1 - d / 28, 0, 1) ** 2)[..., None] * np.array((40, 40, 70))
    # 창살 (가운데 세로 · 가로 둘) · 돌 테
    for y in range(wy0 + 4, wy1):
        img[y, int(cx)] = (30, 26, 46)
    for yb in (44, 62):
        for x in range(wx0, wx1):
            arch = wy0 + rx - math.sqrt(max(0.0, rx * rx - (x - cx) ** 2))
            if yb >= arch:
                img[yb, x] = (30, 26, 46)
    for a in np.linspace(math.pi, 2 * math.pi, 80):
        for rr, col in ((rx + 0.5, (104, 98, 140)), (rx + 1.5, (76, 70, 104)), (rx + 2.5, (34, 30, 50))):
            x = int(round(cx + math.cos(a) * rr))
            y = int(round(wy0 + rx + math.sin(a) * rr))
            put(img, x, y, col)
    for y in range(int(wy0 + rx), wy1 + 2):
        for dx, col in ((-1, (104, 98, 140)), (-2, (76, 70, 104)), (-3, (34, 30, 50))):
            put(img, wx0 + dx + 1 - 1, y, col)
            put(img, wx1 - dx - 1 + 1, y, col)
    for x in range(wx0 - 3, wx1 + 3):
        put(img, x, wy1, (110, 104, 148))
        put(img, x, wy1 + 1, (70, 64, 98))
        put(img, x, wy1 + 2, (30, 26, 46))

    # 선반 둘 (왼쪽): 나무 판 · 받침
    for sy in (46, 86):
        for x in range(3, 52):
            img[sy, x] = (118, 84, 58)
            img[sy + 1, x] = (88, 60, 40)
            img[sy + 2, x] = (52, 34, 24)
        for bx in (8, 46):
            line(img, bx, sy + 3, bx + (4 if bx < 20 else -4), sy + 8, (70, 48, 32))
    # 윗선반: 책 (높이 · 색 여럿) · 수정 다발
    x = 5
    for col, h in (((126, 40, 52), 13), ((44, 70, 128), 11), ((58, 104, 64), 14), ((136, 104, 44), 10), ((90, 50, 120), 12), ((40, 90, 110), 13)):
        for yy_ in range(46 - h, 46):
            for xx_ in range(x, x + 3):
                c = np.array(col, float) * (1.15 if xx_ == x else (0.75 if xx_ == x + 2 else 1.0))
                img[yy_, xx_] = c
        img[46 - h + 2, x:x + 3] = np.array((220, 200, 120), float) * 0.8
        x += 4
    for i, (dx, h, c) in enumerate(((34, 11, (190, 120, 255)), (38, 15, (220, 160, 255)), (42, 9, (150, 90, 230)), (45, 7, (120, 200, 255)))):
        for k in range(h):
            w = max(1, int(round(2.2 * (1 - k / h))))
            for xx_ in range(dx - w, dx + w):
                c2 = np.array(c, float) * (1.25 if xx_ == dx - w else 0.85 if xx_ == dx + w - 1 else 1.0)
                put(img, xx_, 45 - k, c2)
    # 아랫선반: 물약 병 (둥근 몸 · 목 · 색 물) · 수정 구슬
    for bx, liquid in ((9, (60, 200, 220)), (17, (220, 70, 160)), (25, (120, 230, 120))):
        for yy_ in range(77, 86):
            for xx_ in range(bx - 3, bx + 4):
                if math.hypot(xx_ - bx, (yy_ - 81.5) * 0.9) <= 3.6:
                    c = np.array(liquid, float) * (0.7 + 0.5 * (yy_ > 80))
                    if xx_ == bx - 2 and yy_ < 81:
                        c = np.array((235, 240, 255), float)
                    img[yy_, xx_] = c
        for yy_ in range(73, 77):
            img[yy_, bx] = (170, 180, 200)
            img[yy_, bx + 1] = (120, 128, 150)
        img[72, bx:bx + 2] = (130, 90, 60)
    ox_, oy_ = 40, 79
    for yy_ in range(oy_ - 6, oy_ + 6):
        for xx_ in range(ox_ - 6, ox_ + 7):
            dd = math.hypot(xx_ - ox_, yy_ - oy_)
            if dd <= 5.5:
                img[yy_, xx_] = np.array(sa.mix((40, 120, 200), (170, 240, 255), np.array(max(0.0, 1 - dd / 5.5))), float)
    img[oy_ - 3, ox_ - 2] = (255, 255, 255)
    for xx_ in range(ox_ - 4, ox_ + 5):
        img[85, xx_] = (90, 70, 50)
    # 수정 구슬 빛
    d2 = np.hypot(xx - ox_, yy - oy_)
    img += (np.clip(1 - d2 / 18, 0, 1) ** 2)[..., None] * np.array((20, 60, 90))
    # 촛불 (선반 위 둘 · 작은 불꽃) — 불꽃 둘레 빛
    for cx_, cy_ in ((30, 46), (48, 86)):
        for yy_ in range(cy_ - 6, cy_):
            img[yy_, cx_] = (232, 224, 210)
            img[yy_, cx_ + 1] = (196, 186, 170)
        put(img, cx_, cy_ - 7, (255, 210, 120))
        put(img, cx_, cy_ - 8, (255, 240, 180))
        put(img, cx_ + 1, cy_ - 7, (255, 170, 80))
        dc = np.hypot(xx - cx_, yy - (cy_ - 7))
        img += (np.clip(1 - dc / 14, 0, 1) ** 2)[..., None] * np.array((90, 60, 30))

    # 벽의 룬 새김 (희미한 빛 — 돌 몇 개에)
    for gx, gy in ((10, 104), (24, 118), (52, 100), (8, 132), (104, 104), (98, 126)):
        c = np.array((120, 110, 200), float)
        for k in range(5):
            put(img, gx + (k % 2), gy + k, c * (0.6 + 0.1 * k))
        put(img, gx - 1, gy + 2, c * 0.8)
        put(img, gx + 2, gy + 2, c * 0.8)

    # 바닥 마법진 (받침 둘레 — 새긴 선: 겹 타원 · 눈금)
    acx, acy = RUNE["ALTAR_X"] + AW // 2, 170
    for rr_x, rr_y, col in ((29, 9, (110, 84, 170)), (24, 7, (84, 64, 140)), (16, 5, (70, 54, 120))):
        for a in np.linspace(0, 2 * math.pi, 220):
            x = int(round(acx + math.cos(a) * rr_x))
            y = int(round(acy + math.sin(a) * rr_y))
            if FLOOR + 2 <= y < BH and 0 <= x < BW:
                img[y, x] = np.array(col, float) * (0.85 + 0.25 * (math.sin(a) > 0))
    for i in range(12):
        a = i / 12 * 2 * math.pi
        x0_, y0_ = acx + math.cos(a) * 24, acy + math.sin(a) * 7
        x1_, y1_ = acx + math.cos(a) * 29, acy + math.sin(a) * 9
        if FLOOR + 2 <= min(y0_, y1_):
            line(img, x0_, y0_, x1_, y1_, (130, 104, 200))

    # 빛: 받침 위 (룬이 뜨는 곳) 보랏빛 · 가장자리 어둡게
    d = np.hypot(xx - acx, (yy - 108) / 1.3)
    img += (np.clip(1 - d / 62, 0, 1) ** 2)[..., None] * np.array((70, 36, 110))
    v = np.hypot((xx - BW / 2) / (BW / 2), (yy - BH * 0.55) / (BH * 0.62))
    img *= np.clip(1.15 - 0.55 * v ** 2, 0.35, 1.0)[..., None]
    return finish(img)


def altar():
    """룬 받침 (돌 · 위 수정 그릇 · 기둥의 룬 새김) — 위가 x 2 ~ 38, 바닥 그림자까지 50"""
    img = np.zeros((AH, AW, 4))
    rng = random.Random(4503)
    dark = np.array((26, 22, 40), float)
    mid = np.array((78, 72, 104), float)
    lite = np.array((128, 120, 160), float)
    glow = np.array((150, 220, 255), float)

    def stone(x, y, x0, x1, y0, y1, k0=1.15, k1=0.55):
        k = (y - y0) / max(1, y1 - y0)
        c = mid * (k0 - (k0 - k1) * k)
        if x <= x0 + 1:
            c = c * 0.7
        if x >= x1 - 2:
            c = c * 0.85 + np.array((60, 40, 90)) * 0.15
        return c * (0.93 + 0.14 * rng.random())

    # 윗판 (y 4 ~ 11): 테 · 가운데 오목한 그릇 (빛나는 수정 조각)
    for y in range(4, 11):
        for x in range(2, 38):
            put(img, x, y, stone(x, y, 2, 38, 4, 11, 1.25, 0.8))
    for x in range(2, 38):
        put(img, x, 4, lite * 1.1)
        put(img, x, 10, dark)
    for y in range(4, 11):
        put(img, 2, y, dark)
    # 그릇 (윗판 위로 볼록) · 수정
    for x in range(10, 30):
        h = int(round(3 * math.sqrt(max(0.0, 1 - ((x - 19.5) / 10) ** 2))))
        for y in range(4 - h, 4):
            put(img, x, y, mid * 0.9)
        put(img, x, 4 - h, lite)
    for x, h in ((14, 4), (17, 6), (20, 7), (23, 5), (26, 3)):
        for k in range(h):
            put(img, x, 3 - k, glow * (0.75 + 0.25 * (k / max(1, h))))
            if k < h - 1:
                put(img, x + 1, 3 - k, glow * 0.6)
    # 기둥 (y 11 ~ 36): 조금 좁아짐 · 룬 새김 (빛)
    for y in range(11, 36):
        k = (y - 11) / 25
        inset = 2 * math.sin(k * math.pi)
        xa, xb = int(round(11 + inset)), int(round(29 - inset))
        for x in range(xa, xb):
            put(img, x, y, stone(x, y, xa, xb, 11, 36, 0.95, 0.7))
        put(img, xa, y, dark)
    for gy in (15, 21, 27):
        for dx, dy in ((0, 0), (0, 1), (0, 2), (0, 3), (-1, 1), (1, 2), (-1, 3)):
            put(img, 20 + dx, gy + dy, glow * 0.85)
    for x in range(11, 29):
        put(img, x, 11, lite * 0.85)
    # 받침 (y 36 ~ 47): 아래로 퍼짐
    for y in range(36, 47):
        k = (y - 36) / 11
        spread = 6 * k ** 1.2
        xa, xb = int(round(9 - spread)), int(round(31 + spread))
        for x in range(xa, xb):
            put(img, x, y, stone(x, y, xa, xb, 36, 47, 0.95, 0.5))
        put(img, xa, y, dark)
    for x in range(9, 31):
        put(img, x, 36, lite * 0.9)
    for x in range(2, 38):
        if img[46, x, 3] > 0:
            put(img, x, 46, dark)
    # 바닥 그림자
    for y in (47, 48):
        for x in range(AW):
            dd = abs(x - 20) / 21
            a = int((120 if y == 47 else 60) * max(0.0, 1 - dd ** 2))
            if img[y, x, 3] == 0 and a > 0:
                put(img, x, y, (0, 0, 0), a)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 가게

def shop_backdrop():
    rng = random.Random(4511)
    nrng = np.random.default_rng(4512)
    img = np.zeros((BH, BW, 3))
    # 나무 벽: 세로 널빤지 (너비 7) · 틈 · 결
    grain = sa.fbm(nrng, BW, BH, (16, 6, 2))
    for x0 in range(0, BW, 7):
        base = np.array((112, 78, 50), float) * rng.uniform(0.82, 1.1)
        for x in range(x0, min(BW, x0 + 7)):
            for y in range(0, FLOOR):
                c = base * (0.86 + 0.28 * grain[y, x])
                if x == x0:
                    c = np.array((44, 28, 18), float)
                elif x == x0 + 1:
                    c = c * 1.12
                img[y, x] = c
    # 가로 띠 (위 · 가운데)
    for yb in (8, 9):
        img[yb, :] = (70, 46, 30) if yb == 9 else (140, 100, 66)
    # 바닥: 널빤지 (가로)
    for y in range(FLOOR, BH):
        k = (y - FLOOR) / (BH - FLOOR)
        for x in range(BW):
            c = np.array((96, 66, 42), float) * (0.95 + 0.1 * (((y - FLOOR) // 6) % 2)) * (1 - 0.3 * k)
            if (y - FLOOR) % 6 == 0:
                c *= 0.65
            img[y, x] = c
    img[FLOOR] = (150, 110, 74)

    # 창 (왼쪽 위): 하늘 · 햇빛 · 창틀 십자
    wx0, wx1, wy0, wy1 = 6, 36, 14, 46
    for y in range(wy0, wy1):
        for x in range(wx0, wx1):
            k = (y - wy0) / (wy1 - wy0)
            img[y, x] = np.array(sa.mix((150, 200, 240), (220, 236, 250), np.array(k)), float)
    for x in range(wx0, wx1):
        for y in (wy0 + 20, wy0 + 21):
            img[y, x] = (196, 220, 240)
    for y in range(wy0, wy1):
        img[y, (wx0 + wx1) // 2] = (90, 62, 40)
    img[(wy0 + wy1) // 2, wx0:wx1] = (90, 62, 40)
    for x in range(wx0 - 2, wx1 + 2):
        for y in (wy0 - 2, wy0 - 1, wy1, wy1 + 1):
            img[y, x] = (70, 46, 30) if y in (wy0 - 2, wy1 + 1) else (130, 92, 60)
    for y in range(wy0 - 2, wy1 + 2):
        for x in (wx0 - 2, wx0 - 1, wx1, wx1 + 1):
            img[y, x] = (70, 46, 30) if x in (wx0 - 2, wx1 + 1) else (130, 92, 60)
    # 창에서 들어오는 빛 (비스듬한 띠)
    yy, xx = np.mgrid[0:BH, 0:BW]
    band = np.clip(1 - np.abs((xx - 21) - (yy - 30) * 0.55) / 22, 0, 1) * (yy > 46) * np.clip(1 - (yy - 46) / 110, 0, 1)
    img += (band ** 1.5)[..., None] * np.array((60, 50, 30))

    # 선반 셋 (뒤 벽 가운데 ~ 오른쪽): 물건
    for sy in (58, 86):
        for x in range(40, 110):
            img[sy, x] = (150, 108, 70)
            img[sy + 1, x] = (104, 72, 46)
            img[sy + 2, x] = (56, 36, 22)
    # 위 선반: 물약 (빨강 · 파랑 · 초록) · 병 · 두루마리
    for bx, liquid in ((46, (210, 50, 50)), (53, (60, 110, 230)), (60, (70, 190, 80)), (67, (230, 170, 40))):
        for yy_ in range(50, 58):
            for xx_ in range(bx - 2, bx + 3):
                if math.hypot(xx_ - bx, (yy_ - 54) * 0.9) <= 3.0:
                    c = np.array(liquid, float) * (0.7 + 0.45 * (yy_ > 53))
                    if xx_ == bx - 1 and yy_ < 54:
                        c = np.array((240, 240, 250), float)
                    img[yy_, xx_] = c
        img[48:51, bx] = (190, 196, 210)
        img[47, bx] = (120, 84, 54)
    for sx in (76, 82):
        for yy_ in range(52, 58):
            for xx_ in range(sx, sx + 5):
                img[yy_, xx_] = (226, 210, 170) if yy_ % 2 else (200, 182, 140)
        img[52:58, sx + 2] = (170, 50, 40)
    # 방패 (위 선반 오른쪽 벽)
    shx, shy = 99, 34
    for yy_ in range(shy - 9, shy + 10):
        for xx_ in range(shx - 8, shx + 9):
            k = (yy_ - (shy - 9)) / 19
            half = 8 * (1 - max(0.0, k - 0.45) ** 1.5 * 1.6)
            if abs(xx_ - shx) <= half:
                edge = abs(abs(xx_ - shx) - half) < 1.2 or yy_ == shy - 9
                img[yy_, xx_] = (170, 140, 70) if edge else ((150, 40, 40) if (xx_ - shx) * (yy_ - shy) >= 0 else (210, 196, 170))
    # 아래 선반: 자루 · 투구 · 접은 천
    for bx in (48, 58):
        for yy_ in range(76, 86):
            for xx_ in range(bx - 4, bx + 5):
                if math.hypot((xx_ - bx) * 0.9, yy_ - 81.5) <= 4.8:
                    img[yy_, xx_] = np.array((168, 138, 96), float) * (0.75 + 0.35 * (xx_ < bx))
        img[76, bx - 1:bx + 2] = (110, 80, 50)
    hx, hy = 74, 80
    for yy_ in range(hy - 6, hy + 6):
        for xx_ in range(hx - 6, hx + 7):
            if math.hypot(xx_ - hx, yy_ - hy) <= 6 and yy_ <= hy + 2:
                img[yy_, xx_] = np.array((150, 156, 170), float) * (1.2 if xx_ < hx - 2 else 0.85)
    img[hy - 1, hx - 4:hx + 5] = (40, 40, 48)
    for yy_ in range(80, 86):
        for xx_ in range(86, 104):
            img[yy_, xx_] = (60, 90, 150) if (yy_ - 80) % 3 else (90, 120, 180)
    # 무기 걸이 (왼쪽 아래 벽): 칼 둘 (엇갈림) · 도끼
    def blade(x0, y0, x1, y1):
        line(img, x0, y0, x1, y1, (200, 206, 220))
        line(img, x0 + 1, y0, x1 + 1, y1, (130, 136, 150))
    blade(10, 60, 30, 92)
    blade(30, 60, 10, 92)
    for (gx, gy) in ((28, 89), (12, 89)):
        line(img, gx - 3, gy - 2, gx + 3, gy + 2, (150, 110, 50))
    # 도끼 (자루 · 초승달 날)
    line(img, 20, 98, 20, 128, (120, 84, 50))
    line(img, 21, 98, 21, 128, (84, 58, 34))
    for yy_ in range(97, 113):
        dy = (yy_ - 105) / 8
        w = int(round(7 * math.sqrt(max(0.0, 1 - dy * dy))))
        for xx_ in range(22, 23 + w):
            img[yy_, xx_] = (196, 202, 216) if xx_ >= 21 + w else (132, 138, 154)
    for yy_ in range(101, 109):
        img[yy_, 22] = (90, 94, 106)
    for x in range(6, 34):
        img[58, x] = (90, 60, 38)
    # 등 (위 가운데 오른쪽): 사슬 · 등 · 따뜻한 빛
    lx, ly = 66, 22
    for y in range(10, ly - 4):
        img[y, lx] = (60, 60, 64) if y % 2 else (110, 110, 118)
    for yy_ in range(ly - 4, ly + 6):
        for xx_ in range(lx - 3, lx + 4):
            img[yy_, xx_] = (255, 220, 140) if abs(xx_ - lx) <= 1 and ly - 2 <= yy_ <= ly + 3 else (60, 50, 40)
    dl = np.hypot(xx - lx, (yy - ly) * 1.1)
    img += (np.clip(1 - dl / 70, 0, 1) ** 2)[..., None] * np.array((110, 70, 25))
    img *= (0.9 + 0.2 * sa.fbm(nrng, BW, BH, (6, 3)))[..., None]
    v = np.hypot((xx - BW / 2) / (BW / 2), (yy - BH * 0.5) / (BH * 0.62))
    img *= np.clip(1.15 - 0.5 * v ** 2, 0.4, 1.0)[..., None]
    return finish(img)


def counter():
    """계산대 (앞 — 112 × 52): 윗판 (위에서 조금 보임) · 널빤지 앞판 · 테 · 놋쇠 장식 · 왼쪽 동전 접시"""
    img = np.zeros((CH, CW, 4))
    rng = random.Random(4513)
    nrng = np.random.default_rng(4514)
    grain = sa.fbm(nrng, CW, CH, (14, 5, 2))
    # 윗판 (y 0 ~ 6): 밝은 나무 · 앞 모서리
    for y in range(0, 6):
        for x in range(CW):
            c = np.array((176, 128, 82), float) * (0.9 + 0.2 * grain[y, x]) * (1.1 - 0.05 * y)
            put(img, x, y, c)
    for x in range(CW):
        put(img, x, 0, (206, 160, 108))
        put(img, x, 5, (226, 182, 126))
        put(img, x, 6, (96, 64, 40))
    # 앞판 (y 7 ~ 51): 세로 널빤지 · 가로 테 둘 · 그늘
    for y in range(7, CH):
        k = (y - 7) / (CH - 7)
        for x in range(CW):
            c = np.array((120, 82, 52), float) * (0.88 + 0.22 * grain[y, x]) * (1 - 0.35 * k)
            if x % 14 == 0:
                c = np.array((58, 38, 24), float)
            elif x % 14 == 1:
                c = c * 1.1
            put(img, x, y, c)
    for yb in (10, 11, 40, 41):
        for x in range(CW):
            put(img, x, yb, (150, 108, 70) if yb in (10, 40) else (70, 46, 30))
    # 놋쇠 못 · 모서리 장식
    for x in range(7, CW, 14):
        for yb in (10, 40):
            put(img, x, yb, (240, 200, 110))
    for x0 in (3, CW - 9):
        for yy_ in range(13, 38):
            for xx_ in range(x0, x0 + 6):
                if (yy_ - 13) % 6 < 3:
                    put(img, xx_, yy_, (200, 160, 80) if xx_ == x0 + 1 else (150, 112, 56))
    # 왼쪽 동전 접시 (놋쇠 — 윗판 위)
    tx, ty = 26, 3
    for xx_ in range(tx - 9, tx + 10):
        dd = abs(xx_ - tx) / 9.5
        if dd <= 1:
            put(img, xx_, ty, (230, 190, 100))
            put(img, xx_, ty + 1, (170, 130, 60))
            if dd < 0.85:
                put(img, xx_, ty - 1, (190, 150, 70))
    # 오른쪽 장부 (책 — 윗판 위)
    for yy_ in range(0, 4):
        for xx_ in range(92, 104):
            put(img, xx_, yy_, (120, 40, 40) if yy_ < 2 else (236, 228, 206))
    # 아래 그늘 (바닥으로)
    for y in range(CH - 4, CH):
        for x in range(CW):
            c = img[y, x, :3] * (0.7 - 0.1 * (y - (CH - 4)))
            put(img, x, y, c)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 미리 보기 (상자 사람)

def hand_point(pose, mp, right=True):
    """손 (팔 끝 가운데) — 사람 공간 (칸: 오른쪽 · 위 · 앞, 발 가운데 기준)"""
    w = pose["w"]
    body = [math.radians(x) * w for x in pose["body"]]
    Rb = mp.zyx(*body)
    key = "ra" if right else "la"
    R, bend, twist, wrist, dev = pose[key]
    R = mp.mat_of(mp.slerp(mp.quat_of(np.eye(3)), mp.quat_of(R), w))
    sh = mp.HIP + Rb @ (np.array([-5.0 if right else 5.0, 2.0, 0.0]) - mp.HIP)
    J = np.array([-1.0 if right else 1.0, 4.0, 0.0])
    tip = np.array([-1.0 if right else 1.0, 10.0, 0.0])
    g = mp.rx(bend * w) @ (tip - J) + J
    m = R @ g + sh
    o = np.array([-m[0] / 16.0, 1.501 - m[1] / 16.0, m[2] / 16.0]) * 0.9375 + np.array([0.0, pose["root"][0] * w, -pose["root"][1] * w])
    return np.array([o[0], o[1], -o[2]])


def panel(out):
    from PIL import ImageDraw
    mp = sa._mp()
    import json
    with open(os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json"), encoding="utf-8") as f:
        motions = json.load(f)["motions"]
    k = 3
    rows = []
    for scene, names, layout in (("rune", ("rune_channel", "rune_merge"), RUNE), ("shop", ("shop_hand", "shop_count"), SHOP)):
        for name in names:
            m = motions[name]
            idle_m = motions["rune_channel" if scene == "rune" else "shop_hand"]
            rest = dict(idle_m["keys"][1])
            loop = dict(m, keys=[dict(rest, t=0.0)] + m["keys"][1:-1] + [dict(rest, t=1.0)], fade=[0, 0])
            ts = [0.0] + list(m.get("impacts", [])) + [m["keys"][len(m["keys"]) // 2]["t"]]
            frames = []
            for t in ts:
                if scene == "rune":
                    img = rune_backdrop().convert("RGBA")
                    img.alpha_composite(altar(), (layout["ALTAR_X"], layout["ALTAR_Y"]))
                else:
                    img = shop_backdrop().convert("RGBA")
                img = img.resize((BW * k, BH * k), Image.NEAREST)
                d = ImageDraw.Draw(img)
                pose = mp.evaluate(loop, t)
                w = pose["w"]
                polys = [([(-p[0] / 16.0, 1.501 - p[1] / 16.0, p[2] / 16.0) for p in pts], col) for pts, col in mp.build(pose, weapon=False)]
                items = []
                for pts, col in polys:
                    ws = [np.array(p) * 0.9375 + np.array([0.0, pose["root"][0] * w, -pose["root"][1] * w]) for p in pts]
                    sc = [sa.to_panel((q[0], q[1], -q[2]), layout) for q in ws]
                    depth = sum((mp.rx(math.radians(layout["CAM"])) @ (mp.ry(math.radians(-layout["FACE"])) @ q))[2] for q in ws) / len(ws)
                    items.append((depth, sc, col))
                items.sort(key=lambda x: -x[0])
                for _, sc, col in items:
                    d.polygon([(x * k, y * k) for x, y in sc], fill=tuple(col) + (255,), outline=(20, 20, 24, 255))
                for right in (True, False):
                    hp = sa.to_panel(hand_point(pose, mp, right), layout)
                    d.ellipse([hp[0] * k - 4, hp[1] * k - 4, hp[0] * k + 4, hp[1] * k + 4], outline=(255, 80, 80, 255) if right else (80, 200, 255, 255))
                if scene == "shop":
                    img.alpha_composite(counter().resize((CW * k, CH * k), Image.NEAREST), (0, layout["COUNTER_Y"] * k))
                d = ImageDraw.Draw(img)
                d.text((4, 4), "%s t=%.2f" % (name, t), fill=(255, 230, 120, 255))
                hr = sa.to_panel(hand_point(pose, mp, True), layout)
                d.text((4, 16), "R hand (%.0f, %.0f)" % hr, fill=(255, 160, 160, 255))
                frames.append(img)
            row = Image.new("RGBA", (sum(f.width for f in frames), frames[0].height), (0, 0, 0, 255))
            x = 0
            for f in frames:
                row.paste(f, (x, 0))
                x += f.width
            rows.append(row)
    sheet = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows)), (0, 0, 0, 255))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(out)
    print("panel preview →", out)


def main(argv):
    if "--panel" in argv:
        i = argv.index("--panel")
        panel(argv[i + 1] if i + 1 < len(argv) else "workshop_panel.png")
        return
    if "--preview" in argv:
        i = argv.index("--preview")
        out = argv[i + 1] if i + 1 < len(argv) else "workshop_preview.png"
        a = rune_backdrop()
        a.alpha_composite(altar(), (RUNE["ALTAR_X"], RUNE["ALTAR_Y"]))
        b = shop_backdrop()
        b.alpha_composite(counter(), (0, SHOP["COUNTER_Y"]))
        k = 4
        sheet = Image.new("RGBA", (BW * k * 2 + 16, BH * k), (20, 20, 24, 255))
        sheet.paste(a.resize((BW * k, BH * k), Image.NEAREST), (0, 0))
        sheet.paste(b.resize((BW * k, BH * k), Image.NEAREST), (BW * k + 16, 0))
        sheet.save(out)
        print("preview →", out)
        return
    os.makedirs(GUI, exist_ok=True)
    rune_backdrop().save(os.path.join(GUI, "rune_backdrop.png"))
    altar().save(os.path.join(GUI, "altar.png"))
    shop_backdrop().save(os.path.join(GUI, "shop_backdrop.png"))
    counter().save(os.path.join(GUI, "counter.png"))
    print("workshop art written")


if __name__ == "__main__":
    main(sys.argv[1:])
