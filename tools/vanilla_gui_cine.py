"""룬 뽑기 연출 그림 (모드 없는 클라이언트 — GachaCinematic): 화면 가운데 큰 글자 (부제 — 2배 크기) 로 그리는 그림 · 글꼴 자리.

사용자 2026-11-08: "네 버튼을 누르면 2번, 3번 사진과 똑같이는 아니고 우리만의 뽑기 연출" (화면 흔들림 없음 · 무지개 없음).
연출 "룬 각성": 어두운 화면 → 마법진이 그려짐 (금) → 빛이 모임 (가장 높은 등급 색) → 번쩍 → 봉인된 룬석 (등급 색 문양) →
좌클릭: 룬석이 깨지며 룬 (등급 빛 · 이름 · NEW · 픽업), 10회는 다섯 × 두 줄로 차례로 깨짐 · 초월은 따로 크게. Shift = 건너뛰기.

그림은 모두 흰색 (밝기) 으로 그려 글자 색으로 물들임 (룬석 바탕 · NEW 처럼 색이 정해진 것은 흰색으로 씀).
좌표: 부제 글자 1 = 화면 2px (fu). 그림 1px (텍셀) = 1 fu = 2px (룬 그림만 큰 것 3px · 작은 것 1px).
부제의 글자 위 = 12 − ascent (fu, 화면 가운데 기준 아래 +) → 가운데 Yc 높이 H: ascent = 12 − Yc + H / 2.
제목 (4배) 의 글자 위 = −3 − ascent (fu × 4px) → 바탕 (1024 fu) 을 가운데에: ascent 509.

만드는 것: src/main/resources/mmorpg/vanilla/gui/cine/*.png · cine.json (글꼴 → 제공자 목록, 그림 이름 → 글꼴 · 글자 · 너비 · 왼쪽 x)
python3 tools/vanilla_gui_cine.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "mmorpg", "vanilla", "gui", "cine")
RUNES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg", "textures", "item", "rune")

# 자리 (fu — 화면 가운데 기준, 아래 +)
GRID_Y = [-14, 14]          # 10회 두 줄 가운데
GRID_X = [-50, -25, 0, 25, 50]
BIG_Y = -8                  # 가운데 큰 것 (마법진 · 룬석 · 큰 룬 · 빛) 의 가운데 — 아래 글자 자리를 위해 조금 위
NAME_Y, SUB_Y, HINT_Y, HEAD_Y = 33, 44, 54, -54
BIG_RUNE_H, SMALL_RUNE_H = 64, 16   # 룬 그림 32px → 큰 것 4px · 작은 것 1px (화면)


def W(a=1.0):
    return (255, 255, 255, int(255 * max(0.0, min(1.0, a))))


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.float32)

    def plot(self, x, y, a=1.0, rgb=(255, 255, 255)):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h and a > 0:
            old = self.a[y, x]
            na = a + old[3] / 255 * (1 - a)
            if na <= 0:
                return
            for k in range(3):
                old[k] = (rgb[k] * a + old[k] * old[3] / 255 * (1 - a)) / na
            old[3] = na * 255

    def set(self, x, y, rgb, a=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = (*rgb, 255 * a)

    def ring(self, cx, cy, r, width=1.0, a=1.0, a0=0.0, a1=360.0):
        """원 (각도 a0 ~ a1 만 — 0 = 위, 시계 방향)"""
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if abs(d - r) <= width / 2:
                    ang = (math.degrees(math.atan2(x + 0.5 - cx, -(y + 0.5 - cy))) + 360) % 360
                    if a0 <= ang <= a1:
                        self.plot(x, y, a)

    def line(self, x0, y0, x1, y1, a=1.0, t=1.0):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(int(n * t) + 1):
            f = i / n
            self.plot(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, a)

    def glow(self, cx, cy, rad, a, rgb=(255, 255, 255)):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / rad
                if d < 1:
                    self.plot(x, y, a * (1 - d) ** 2, rgb)

    def image(self):
        return Image.fromarray(np.clip(self.a, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------------ 마법진 (128 × 128 — 반지름 50 = 화면 100px)

C = 64
MARK_SHAPES = [  # 룬 표시 (5 × 5)
    ["..#..", ".###.", "#.#.#", "..#..", "..#.."],
    ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    ["#####", "#....", "####.", "#....", "#####"],
    ["..#..", ".#.#.", "#...#", ".#.#.", "..#.."],
    ["#.#.#", "#.#.#", "#####", "..#..", "..#.."],
    ["###..", "#..#.", "###..", "#.#..", "#..#."],
]


def circle_img(progress=1.0, rot=0.0):
    im = Img(128, 128)
    p = progress
    outer = min(1.0, p / 0.55)
    inner = max(0.0, min(1.0, (p - 0.15) / 0.5))
    hexa = max(0.0, min(1.0, (p - 0.35) / 0.45))
    marks = max(0.0, min(1.0, (p - 0.5) / 0.5))
    if p >= 1.0:
        im.glow(C, C, 54, 0.22)
    im.ring(C, C, 50, 1.6, 1.0, 0, 360 * outer)
    im.ring(C, C, 46.5, 0.9, 0.55, 0, 360 * outer)
    im.ring(C, C, 41, 1.2, 0.9, 0, 360 * inner)
    im.ring(C, C, 22, 1.0, 0.75 * inner, 0, 360)
    # 바깥 눈금 (돌아감)
    if outer > 0:
        for k in range(48):
            ang = math.radians(k * 7.5 + rot)
            if (k * 7.5) / 360 > outer:
                continue
            r0, r1 = (47.5, 50.5) if k % 4 else (45, 51)
            im.line(C + r0 * math.sin(ang), C - r0 * math.cos(ang), C + r1 * math.sin(ang), C - r1 * math.cos(ang), 0.8)
    # 육각별 (두 삼각형 — 돌아감)
    if hexa > 0:
        for tri in range(2):
            pts = []
            for k in range(3):
                ang = math.radians(rot * -1.5 + tri * 60 + k * 120)
                pts.append((C + 41 * math.sin(ang), C - 41 * math.cos(ang)))
            for k in range(3):
                (x0, y0), (x1, y1) = pts[k], pts[(k + 1) % 3]
                im.line(x0, y0, x1, y1, 0.85, hexa)
    # 룬 표시 12 (바깥 띠 안 — 하나씩 나타남, 돌아감)
    for k in range(12):
        if marks <= k / 12:
            continue
        ang = math.radians(k * 30 + 15 + rot)
        mx, my = C + 44 * math.sin(ang) - 2, C - 44 * math.cos(ang) - 2
        shape = MARK_SHAPES[k % len(MARK_SHAPES)]
        for yy, row in enumerate(shape):
            for xx, ch in enumerate(row):
                if ch == "#":
                    im.plot(mx + xx, my + yy, 0.95)
    # 가운데
    if p >= 0.7:
        im.glow(C, C, 10 + 8 * p, 0.5 * p)
        for k in range(4):
            ang = math.radians(k * 90 + 45 + rot * 2)
            im.line(C + 4 * math.sin(ang), C - 4 * math.cos(ang), C + 12 * math.sin(ang), C - 12 * math.cos(ang), 0.7)
    return im.image()


def spark_img(f, frames=6, n=26, seed=7):
    """빛 모임: 나선을 그리며 가운데로"""
    rnd = random.Random(seed)
    im = Img(128, 128)
    for i in range(n):
        a0 = rnd.random() * 360
        r0 = 52 + rnd.random() * 10
        sp = 0.85 + rnd.random() * 0.3
        t = min(1.0, (f + 1) / frames * sp)
        for k in range(4):  # 꼬리
            tt = max(0.0, t - k * 0.04)
            r = r0 * (1 - tt) + 3
            ang = math.radians(a0 + tt * 140)
            x, y = C + r * math.sin(ang), C - r * math.cos(ang)
            a = (1 - k * 0.28) * (0.5 + 0.5 * tt)
            im.plot(x, y, a)
            if k == 0:
                im.plot(x + 1, y, a * 0.6)
                im.plot(x, y + 1, a * 0.6)
    im.glow(C, C, 6 + 16 * (f + 1) / frames, 0.35 + 0.5 * (f + 1) / frames)
    return im.image()


def flash_img(f):
    im = Img(128, 128)
    if f == 0:
        im.glow(C, C, 30, 1.0)
        for x in range(128):
            for y in range(128):
                if math.hypot(x + 0.5 - C, y + 0.5 - C) < 14:
                    im.plot(x, y, 1.0)
        for k in range(8):
            ang = math.radians(k * 45)
            im.line(C, C, C + 60 * math.sin(ang), C - 60 * math.cos(ang), 0.8)
    else:
        r = [0, 30, 46, 58][f]
        a = [1, 0.95, 0.6, 0.3][f]
        im.ring(C, C, r, 3.0 - f * 0.5, a)
        im.glow(C, C, r * 0.9, a * 0.6)
    return im.image()


# ------------------------------------------------------------------ 룬석 (봉인 — 큰 48 × 48 · 작은 20 × 20)

STONE = (44, 46, 72)
STONE_HI = (96, 102, 150)
STONE_LO = (24, 25, 42)
EDGE = (8, 8, 14)


def hexagon_mask(w, h, inset=0):
    m = np.zeros((h, w), dtype=bool)
    cx, cy = w / 2, h / 2
    rx, ry = w / 2 - inset, h / 2 - inset
    for y in range(h):
        for x in range(w):
            dx, dy = abs(x + 0.5 - cx) / rx, abs(y + 0.5 - cy) / ry
            if dx <= 1 and dy <= 1 and dy <= 1.5 - dx * 0.5 * 1.0 + 0.0:  # 뾰족한 위 · 아래
                if dy + dx * 0.5 <= 1.0:
                    m[y, x] = True
    return m


def stone_base(size):
    w = h = size
    im = Img(w, h)
    m = hexagon_mask(w, h, 1)
    inner = m.copy()
    inner[1:, :] &= m[:-1, :]
    inner[:-1, :] &= m[1:, :]
    inner[:, 1:] &= m[:, :-1]
    inner[:, :-1] &= m[:, 1:]
    for y in range(h):
        for x in range(w):
            if not m[y, x]:
                continue
            if not inner[y, x]:
                im.set(x, y, EDGE)
            else:
                light = (x - w / 2) * -0.5 + (y - h / 2) * -0.8
                col = STONE_HI if light > size * 0.28 else (STONE if light > -size * 0.2 else STONE_LO)
                im.set(x, y, col)
    # 안쪽 새김 줄 (육각 테)
    m2 = hexagon_mask(w, h, max(3, size // 7))
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if m2[y, x] and not (m2[y - 1, x] and m2[y + 1, x] and m2[y, x - 1] and m2[y, x + 1]):
                im.set(x, y, STONE_LO)
    return im.image()


def stone_mark(size):
    """봉인 문양 (흰색 — 등급 색으로 물들임): 마름모 두 겹 + 가운데 점 + 빛"""
    im = Img(size, size)
    c = size / 2
    im.glow(c, c, size * 0.42, 0.45)
    for r, a in ((size * 0.30, 1.0), (size * 0.17, 0.85)):
        for y in range(size):
            for x in range(size):
                d = abs(x + 0.5 - c) + abs(y + 0.5 - c)
                if abs(d - r) <= 0.6:
                    im.plot(x, y, a)
    for k in range(4):
        ang = math.radians(k * 90)
        im.line(c + size * 0.30 * math.sin(ang), c - size * 0.30 * math.cos(ang), c + size * 0.40 * math.sin(ang), c - size * 0.40 * math.cos(ang), 0.9)
    for y in range(size):
        for x in range(size):
            if math.hypot(x + 0.5 - c, y + 0.5 - c) <= max(1.0, size * 0.05):
                im.plot(x, y, 1.0)
    return im.image()


def stone_crack(size, stage, seed=3):
    rnd = random.Random(seed)
    im = Img(size, size)
    c = size / 2
    n = 4 if stage == 0 else 7
    for k in range(n):
        ang = rnd.random() * 2 * math.pi
        x, y = c, c
        length = size * (0.22 if stage == 0 else 0.42) * (0.7 + rnd.random() * 0.5)
        steps = max(3, int(length))
        for s in range(steps):
            ang += (rnd.random() - 0.5) * 0.7
            x += math.cos(ang)
            y += math.sin(ang)
            im.plot(x, y, 1.0)
    im.glow(c, c, size * (0.18 if stage == 0 else 0.3), 0.6)
    return im.image()


def stone_shards(size, stage, seed=11):
    """깨진 조각 (돌 색) + 흰 불티가 밖으로"""
    rnd = random.Random(seed)
    im = Img(size, size)
    c = size / 2
    dist = size * (0.28 if stage == 0 else 0.44)
    for k in range(9):
        ang = k / 9 * 2 * math.pi + rnd.random() * 0.4
        d = dist * (0.7 + rnd.random() * 0.5)
        x, y = c + d * math.cos(ang), c + d * math.sin(ang)
        s = max(1, int(size * (0.07 if stage == 0 else 0.05)))
        for yy in range(-s, s + 1):
            for xx in range(-s, s + 1):
                if abs(xx) + abs(yy) <= s:
                    im.set(int(x + xx), int(y + yy), STONE_HI if (xx + yy) < 0 else STONE, 1.0 if stage == 0 else 0.7)
        for j in range(3):
            d2 = d * (1.15 + j * 0.12)
            im.plot(c + d2 * math.cos(ang + 0.2), c + d2 * math.sin(ang + 0.2), 0.9 - j * 0.25)
    im.glow(c, c, size * 0.5, 0.7 if stage == 0 else 0.35)
    return im.image()


def aura(size, f):
    im = Img(size, size)
    c = size / 2
    im.glow(c, c, size * 0.5, 0.55)
    for k in range(8):
        ang = math.radians(k * 45 + (22.5 if f else 0))
        long_ = size * (0.48 if k % 2 == 0 else 0.36)
        for s in range(int(size * 0.18), int(long_)):
            t = s / long_
            for w in (-1, 0, 1):
                px = c + s * math.sin(ang) + w * math.cos(ang) * (1 - t)
                py = c - s * math.cos(ang) + w * math.sin(ang) * (1 - t)
                im.plot(px, py, 0.55 * (1 - t) * (1 if w == 0 else 0.5))
    return im.image()


def frame_ring(size):
    im = Img(size, size)
    for k in range(2, size - 2):
        for (x, y) in ((k, 0), (k, size - 1), (0, k), (size - 1, k)):
            im.plot(x, y, 1.0)
    for (x, y) in ((1, 1), (size - 2, 1), (1, size - 2), (size - 2, size - 2)):
        im.plot(x, y, 1.0)
    for k in range(2, 6):
        for (x, y) in ((k, 1), (1, k), (size - 1 - k, 1), (size - 2, k), (k, size - 2), (1, size - 1 - k), (size - 1 - k, size - 2), (size - 2, size - 1 - k)):
            im.plot(x, y, 0.55)
    return im.image()


NEW = ["#..#.####.#...#", "##.#.#....#...#", "#.##.###..#.#.#", "#..#.#....##.##", "#..#.####.#...#"]


def new_badge():
    w, h = len(NEW[0]) + 2, len(NEW) + 2
    im = Img(w, h)
    for y in range(h):
        for x in range(w):
            im.set(x, y, (120, 40, 20))
    for y, row in enumerate(NEW):
        for x, ch in enumerate(row):
            if ch == "#":
                im.set(x + 1, y + 1, (255, 232, 120))
    return im.image()


def stars(w=256, h=180, n=170, seed=5):
    """희미한 별 (가운데 진하고 가장자리로 옅어짐 — 화면이 이 그림보다 넓어도 경계가 보이지 않게)"""
    rnd = random.Random(seed)
    im = Img(w, h)
    for _ in range(n):
        x, y = rnd.random() * w, rnd.random() * h
        d = math.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2))
        if d > 1:
            continue
        a = (0.15 + 0.45 * rnd.random()) * (1 - d) ** 0.8
        im.plot(x, y, a, (200, 210, 255) if rnd.random() < 0.7 else (255, 230, 190))
    im.glow(w / 2, h / 2 - 4, 90, 0.06, (120, 130, 220))
    return im.image()


def new_star():
    """처음 얻은 룬 표시 (10회 칸 — 글자 대신 작은 노란 별, 사용자 "글자를 최대한 줄여서")"""
    rows = ["...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."]
    im = Img(7, 7)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == "#":
                im.set(x, y, (255, 236, 120) if (x == 3 or y == 3) else (255, 196, 80))
    im.set(3, 3, (255, 255, 230))
    return im.image()


def backdrop():
    im = Image.new("RGBA", (4, 4), (5, 6, 12, 255))
    return im


# ------------------------------------------------------------------ 글꼴 자리 · 파일

def adv(im, scale=1.0):
    a = np.array(im)[:, :, 3]
    cols = np.where(a.any(axis=0))[0]
    w = 0 if len(cols) == 0 else int(cols[-1]) + 1
    return int(0.5 + w * scale) + 1


class Fonts:
    def __init__(self):
        self.fonts = {}      # 글꼴 → 제공자 목록
        self.glyphs = {}     # 이름 → {font, char, adv, x}
        self.code = 0xE500

    def sheet(self, fname, items, font, yc, height=None, scale=1.0, ascent=None):
        """items = [(이름, 그림)] (같은 크기) → 파일 하나 + 제공자 (글꼴 font, 가운데 yc · 높이 height fu).
        마인크래프트 비트맵 글꼴은 ascent ≤ height 만 받음 → 가운데보다 높이 올릴 그림 (ascent 가 큼) 은 칸 아래를 비워 늘림 (위 자리는 같음)"""
        w, h = items[0][1].size
        height = height or h
        sc = height / h
        asc = ascent if ascent is not None else 12 - yc + height // 2
        pad = max(0, asc - height)                  # fu
        pad_tex = int(math.ceil(pad / sc))
        hh = h + pad_tex
        sheet = Image.new("RGBA", (w * len(items), hh), (0, 0, 0, 0))
        chars = ""
        for i, (name, im) in enumerate(items):
            sheet.paste(im, (i * w, 0))
            ch = chr(self.code)
            self.code += 1
            chars += ch
            self.glyphs[name] = {"font": font, "char": ch, "adv": adv(im, sc), "x": -int(round(w * sc / 2))}
        sheet.save(os.path.join(OUT, fname + ".png"))
        height = int(round(hh * sc))
        assert asc <= height, (fname, asc, height)
        self.fonts.setdefault(font, []).append({"type": "bitmap", "file": "rpg:gui/cine/" + fname + ".png", "height": height, "ascent": asc,
                                                "chars": [chars]})


def rune_sheet(f):
    """룬 그림판 (32 × 32 칸 — assets/rpg/textures/item/rune/rune_*.png · 뽑기권): 큰 것 (가운데 · 48 fu) · 작은 것 (10회 두 줄 · 16 fu)"""
    names = sorted(n[:-4] for n in os.listdir(RUNES) if n.endswith(".png") and not n.startswith("break_"))
    cols = 16
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 32, rows * 32), (0, 0, 0, 0))
    lines = []
    advs = {}
    base = 0xE800
    for i, n in enumerate(names):
        im = Image.open(os.path.join(RUNES, n + ".png")).convert("RGBA").resize((32, 32), Image.NEAREST)
        sheet.paste(im, ((i % cols) * 32, (i // cols) * 32))
        advs[n] = (adv(im, BIG_RUNE_H / 32), adv(im, SMALL_RUNE_H / 32))
    for r in range(rows):
        s = ""
        for c in range(cols):
            i = r * cols + c
            s += chr(base + i) if i < len(names) else "\u0000"
        lines.append(s)
    sheet.save(os.path.join(OUT, "runes.png"))
    for font, yc, h in (("cine_c", BIG_Y, BIG_RUNE_H), ("cine_g0", GRID_Y[0], SMALL_RUNE_H), ("cine_g1", GRID_Y[1], SMALL_RUNE_H)):
        asc = 12 - yc + h // 2
        file = "runes.png"
        height = h
        if asc > h:  # 위로 올릴 것: 칸 아래를 비운 그림판 (ascent ≤ height)
            sc = h / 32
            pad_tex = int(math.ceil((asc - h) / sc))
            padded = Image.new("RGBA", (cols * 32, rows * (32 + pad_tex)), (0, 0, 0, 0))
            for r in range(rows):
                padded.paste(sheet.crop((0, r * 32, cols * 32, r * 32 + 32)), (0, r * (32 + pad_tex)))
            file = "runes_%s.png" % font
            padded.save(os.path.join(OUT, file))
            height = int(round((32 + pad_tex) * sc))
        assert asc <= height, (font, asc, height)
        f.fonts.setdefault(font, []).append({"type": "bitmap", "file": "rpg:gui/cine/" + file, "height": height, "ascent": asc, "chars": lines})
    return {n: {"char": chr(base + i), "big": advs[n][0], "small": advs[n][1]} for i, n in enumerate(names)}


def main():
    os.makedirs(OUT, exist_ok=True)
    for fn in os.listdir(OUT):
        if fn.endswith(".png"):
            os.remove(os.path.join(OUT, fn))
    f = Fonts()
    # 바탕 (제목 — 4배): 1024 fu 정사각 (가운데) — ascent 509 (위 = −3 − 509 = −512)
    f.sheet("backdrop", [("backdrop", backdrop())], "cine_bg", 0, height=1024, ascent=509)
    f.glyphs["backdrop"]["x"] = -512
    # 가운데 판 (128 칸)
    stage = [("circle_in%d" % k, circle_img((k + 1) / 8)) for k in range(8)]
    stage += [("circle_spin%d" % k, circle_img(1.0, rot=k * 10)) for k in range(6)]
    stage += [("spark%d" % k, spark_img(k)) for k in range(6)]
    stage += [("flash%d" % k, flash_img(k)) for k in range(4)]
    f.sheet("stage", stage, "cine_c", BIG_Y)
    f.sheet("stars", [("stars", stars())], "cine_c", -4)
    # 큰 룬석 (64) · 큰 빛 (112)
    f.sheet("seal_big", [("seal_big", stone_base(64)), ("mark_big", stone_mark(64)), ("crack_big0", stone_crack(64, 0)), ("crack_big1", stone_crack(64, 1)),
                         ("shard_big0", stone_shards(64, 0)), ("shard_big1", stone_shards(64, 1))], "cine_c", BIG_Y)
    f.sheet("aura_big", [("aura_big0", aura(112, 0)), ("aura_big1", aura(112, 1))], "cine_c", BIG_Y)
    # 10회 두 줄: 작은 룬석 (20) · 작은 빛 (24) · 등급 테 (22) · NEW
    for row, yc in enumerate(GRID_Y):
        font = "cine_g%d" % row
        f.sheet("seal_small%d" % row, [("seal_s%d" % row, stone_base(20)), ("mark_s%d" % row, stone_mark(20)), ("crack_s%d" % row, stone_crack(20, 1)),
                                       ("shard_s%d" % row, stone_shards(20, 1))], font, yc)
        f.sheet("aura_small%d" % row, [("aura_s%d_0" % row, aura(24, 0)), ("aura_s%d_1" % row, aura(24, 1))], font, yc)
        f.sheet("ring_small%d" % row, [("ring_s%d" % row, frame_ring(22))], font, yc)
        f.sheet("new_small%d" % row, [("new_s%d" % row, new_star())], font, yc - 9)
    f.sheet("new_big", [("new_big", new_badge())], "cine_name", NAME_Y - 9)
    runes = rune_sheet(f)
    # 글자 (갈무리9 글자판 rpg:gui/text.png): 큰 이름 (2배 — 12 fu) · 작은 글 (1배 — 6 fu)
    rows = json.load(open(os.path.join(ROOT, "src", "main", "resources", "mmorpg", "vanilla", "gui", "text_chars.json"), encoding="utf-8"))
    texts = {"cine_name": (NAME_Y, 12, 4), "cine_sub": (SUB_Y, 6, 2), "cine_hint": (HINT_Y, 6, 2)}
    for font, (yc, h, space) in texts.items():
        top = yc - h // 2
        assert 12 - top <= h, (font, 12 - top, h)
        f.fonts.setdefault(font, []).insert(0, {"type": "space", "advances": {" ": space}})
        f.fonts[font].append({"type": "bitmap", "file": "rpg:gui/text.png", "height": h, "ascent": 12 - top, "chars": rows})
    meta = {"fonts": f.fonts, "glyphs": f.glyphs, "runes": runes, "text": {k: {"scale": v[1] / 12, "space": v[2]} for k, v in texts.items()},
            "grid": {"x": GRID_X, "y": GRID_Y}, "big": {"y": BIG_Y, "rune": BIG_RUNE_H, "small": SMALL_RUNE_H}}
    json.dump(meta, open(os.path.join(OUT, "cine.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("cine glyphs", len(f.glyphs), "runes", len(runes), "fonts", list(f.fonts))


if __name__ == "__main__":
    main()
