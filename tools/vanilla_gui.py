"""모드 없는 클라이언트의 창 그림 (1번 4b — 사용자 "텍스처랑 넣고 … 최대한 비슷하게", "위르드 서버 처럼"): 상자 메뉴 제목 글자로 그리는 바탕 · 글자.

바닐라 상자 메뉴는 제목 (글자) 을 칸 아이템보다 먼저 그림 → 리소스 팩 글꼴의 큰 그림 글자 하나를 제목 자리에 두면 창 전체의 바탕이 되고,
뒤로 가는 빈 글자 (space) 로 자리를 옮겨 그 위에 글자를 씀. 세로 자리는 글꼴마다 다른 ascent (줄 글꼴 rpg:gl<d>) 로.

메뉴 (Shift + F — VanillaMenu): 4줄 상자 메뉴 (9 × 4) 하나에 쪽 (홈 · 스탯 · 스킬 · 장비 · 뽑기) 을 바꿔 그림. 쪽마다 바탕 그림 하나
(176 × 198 — 창 176 × 186 + 위 12: 왼쪽 탭 막대의 걸이). 바닐라 상자 그림 (창 0 ~ 175 × 0 ~ 184) 위는 모두 불투명하게 (비치지 않게).
양피지 판 · 초록 탭 막대 · 아래 가방 판 (가방 3줄 = 판 · 핫바 = 갈색 칸). 그림은 모두 이 파일이 그림 (위르드 그림을 쓰지 않음 — 배치 · 느낌만).

만드는 것 (src/main/resources/mmorpg/vanilla/gui — 모드 클라이언트가 읽는 assets 밖, VanillaPack 이 팩에만 넣음):
  text.png · text_chars.json · text_widths.json — 갈무리9 (Galmuri9, SIL OFL 1.1 — quiple) 를 10px 로 그린 글자판
      (ASCII · 쓰는 기호 · 한글 KS X 1001 2350자 + 데이터에 나오는 글자 + 그림 글자 U+E100 ~ — 동전 · 하트 · 마름모).
      칸 12 × 12, 기준선 10. 너비 = 오른쪽 끝 + 2 (마인크래프트 비트맵 글꼴 규칙)
  menu_home.png · menu_stats.png · menu_skills.png · menu_equip.png · menu_gacha.png — 메뉴 쪽 바탕
  icons/*.png — 칸에 놓는 아이템 그림 (빈 그림 · + 단추 · 빼기)
  tooltip/menu_background.png · menu_frame.png — 메뉴 아이템 설명 상자 (tooltip_style rpg:menu)

  num.png · num_widths.json — 작은 숫자판 (갈무리7 — 같은 폴더의 Galmuri7.ttf): 스탯 값 · 강화 +N
  menu_*.png — 메뉴 쪽 바탕

python3 tools/vanilla_gui.py <Galmuri9.ttf>   (같은 폴더에 Galmuri7.ttf)
"""
import json
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "mmorpg", "vanilla", "gui")
SPRITES = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites")

CELL_W = 12
CELL_H = 12
BASE = 10  # 칸 안의 기준선 (위에서)
COLS = 64

# 그림 글자 (글자판 끝에 — 글자 색 흰색으로 쓰면 그림 색 그대로)
ICON_GLYPHS = {
    "": "coin",
    "": "heart",
    "": "gem",
    "": "ticket",
}

# ------------------------------------------------------------------ 색 (양피지 · 초록 탭 — 위르드 느낌, 그림은 새로)
INK = (34, 24, 16)          # 창 사이 틈 (어두운 갈색 — 흐린 세상 위 그림자처럼)
EDGE = (84, 58, 38)         # 판 테두리
EDGE_HI = (120, 86, 56)
CREAM = (229, 211, 168)     # 판
CREAM_HI = (243, 230, 196)
CREAM_LO = (204, 182, 138)
CREAM_LO2 = (186, 162, 118)
TAN = (196, 168, 120)       # 가방 판
TAN_LO = (172, 144, 100)
TAN_HI = (214, 189, 143)
BROWN = (112, 78, 50)       # 막대 · 칸
BROWN_HI = (140, 102, 66)
BROWN_LO = (88, 60, 38)
SLOT = (74, 50, 32)         # 장비 칸 안
SLOT_LO = (56, 38, 24)
SLOT_HI = (98, 70, 46)
GREEN = (58, 80, 54)        # 탭 막대 · 띠
GREEN_HI = (80, 106, 70)
GREEN_LO = (38, 52, 36)
LEAF = (160, 190, 112)      # 꺼진 탭 그림 · 리본
LEAF_LO = (112, 142, 76)
TAB_ON = (236, 227, 190)    # 켜진 탭 바탕
TAB_ON_LO = (204, 194, 150)
GOLD = (232, 182, 82)
GOLD_HI = (252, 216, 128)
GOLD_LO = (176, 124, 52)
WHITE = (255, 255, 255)

MW, MH, MTOP = 176, 186, 12   # 9 × 4 상자 창 · 위 여유 (탭 막대 걸이)
VANILLA_H = 185               # 바닐라 상자 그림 높이 (17 + 4 × 18 + 96) — 이 안은 모두 불투명


def charset():
    chars = set(chr(c) for c in range(0x21, 0x7F))
    chars.update("°±²·»½×÷—…←→↔↑↓⇧−√∞≈≤≥②━▶◀◆◇◈◎★☆「」『』♥♡●○■□▲▼△▽")
    for b1 in range(0xB0, 0xC9):
        for b2 in range(0xA1, 0xFF):
            try:
                c = bytes([b1, b2]).decode("euc-kr")
            except UnicodeDecodeError:
                continue
            if "가" <= c <= "힣":
                chars.add(c)
    pat = re.compile("[가-힣]")
    for base in ["core/src/main/resources", "src/main/resources/mmorpg", "src/main/java", "core/src/main/java"]:
        for dp, _, fn in os.walk(os.path.join(ROOT, base)):
            for f in fn:
                if f.endswith((".json", ".java")):
                    try:
                        chars.update(pat.findall(open(os.path.join(dp, f), encoding="utf-8").read()))
                    except (OSError, UnicodeDecodeError):
                        pass
    return chars


# ------------------------------------------------------------------ 작은 그림 (글자 · 리본 · 탭)

def pattern(rows, color, shade=None, light=None):
    """글자 그림 ('#' = 색, '+' = 어두운 색, '*' = 밝은 색, 그 밖 = 빔)"""
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == "#":
                img.putpixel((x, y), color + (255,))
            elif ch == "+" and shade:
                img.putpixel((x, y), shade + (255,))
            elif ch == "*" and light:
                img.putpixel((x, y), light + (255,))
    return img


ICON_ART = {
    "coin": ([
        "..####..",
        ".#****#.",
        "#**##*+#",
        "#*#**#+#",
        "#*#**#+#",
        "#**##++#",
        ".#++++#.",
        "..####..",
    ], (232, 182, 82), (176, 124, 52), (255, 230, 150)),
    "heart": ([
        ".##.##.",
        "#**#**#",
        "#*####+",
        "#####++",
        ".###++.",
        "..#++..",
        "...+...",
    ], (230, 70, 80), (170, 40, 52), (255, 170, 170)),
    "gem": ([
        "...#...",
        "..#*#..",
        ".#**##.",
        "#*####+",
        ".##++#.",
        "..#++..",
        "...+...",
    ], (120, 200, 255), (60, 120, 200), (220, 245, 255)),
    "ticket": ([
        "########",
        "#**#**+#",
        ".*#*#*+.",
        "#**#**+#",
        "########",
    ], (236, 214, 160), (176, 140, 90), (255, 244, 214)),
}


def icon_glyph(name):
    rows, col, shade, light = ICON_ART[name]
    return pattern(rows, col, shade, light)


def text_sheet(font_path):
    from fontTools.ttLib import TTFont
    cmap = TTFont(font_path).getBestCmap()
    font = ImageFont.truetype(font_path, 10)
    chars = sorted(c for c in charset() if ord(c) in cmap) + sorted(ICON_GLYPHS)
    rows = (len(chars) + COLS - 1) // COLS
    sheet = Image.new("RGBA", (COLS * CELL_W, rows * CELL_H), (0, 0, 0, 0))
    widths = {}
    lines = []
    for r in range(rows):
        row = chars[r * COLS:(r + 1) * COLS]
        row += ["\u0000"] * (COLS - len(row))
        lines.append("".join(row))
        for c_i, ch in enumerate(row):
            if ch == "\u0000":
                continue
            if ch in ICON_GLYPHS:
                g = icon_glyph(ICON_GLYPHS[ch])
                cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
                gy = BASE - g.size[1]  # 바닥을 기준선 위에 (글자와 같은 높이)
                cell.alpha_composite(g, (0, max(0, gy)))
                sheet.paste(cell, (c_i * CELL_W, r * CELL_H))
                widths[ch] = g.size[0] + 1
                continue
            cell = Image.new("L", (CELL_W, CELL_H), 0)
            d = ImageDraw.Draw(cell)
            d.fontmode = "1"
            d.text((0, BASE), ch, font=font, fill=255, anchor="ls")
            px = cell.load()
            right = -1
            for x in range(CELL_W):
                if any(px[x, y] for y in range(CELL_H)):
                    right = x
            if right < 0:
                continue
            white = Image.new("RGBA", (CELL_W, CELL_H), (255, 255, 255, 0))
            white.putalpha(cell)
            sheet.paste(white, (c_i * CELL_W, r * CELL_H))
            widths[ch] = right + 2  # 마인크래프트: 그림 너비 (오른쪽 끝 + 1) + 글자 사이 1
    return sheet, lines, widths


NUM_CHARS = "0123456789+-%./:,×k"


def num_sheet(font_path):
    """작은 숫자판 (갈무리7 — 8px, 숫자 4px 너비): 스탯 값 · 강화 +N · 수. 칸 12 × 12, 기준선 10 (글자판과 같은 규칙)"""
    font = ImageFont.truetype(font_path, 8)
    sheet = Image.new("RGBA", (len(NUM_CHARS) * CELL_W, CELL_H), (0, 0, 0, 0))
    widths = {}
    for i, ch in enumerate(NUM_CHARS):
        cell = Image.new("L", (CELL_W, CELL_H), 0)
        d = ImageDraw.Draw(cell)
        d.fontmode = "1"
        d.text((0, BASE), ch, font=font, fill=255, anchor="ls")
        bb = cell.getbbox()
        if not bb:
            continue
        white = Image.new("RGBA", (CELL_W, CELL_H), (255, 255, 255, 0))
        white.putalpha(cell)
        sheet.paste(white, (i * CELL_W, 0))
        widths[ch] = bb[2] + 1
    return sheet, widths


# ------------------------------------------------------------------ 그리기 (창 좌표 — 위 여유 MTOP 만큼 내려 그림)

def rounded(w, h, r):
    """모서리를 r 만큼 깎은 네모 (픽셀 둥글기 — dx + dy < r 인 모서리 칸을 뺌)"""
    m = np.ones((h, w), dtype=bool)
    for dy in range(r):
        for dx in range(r - dy):
            m[dy, dx] = m[dy, w - 1 - dx] = m[h - 1 - dy, dx] = m[h - 1 - dy, w - 1 - dx] = False
    return m


def erode(m):
    e = m.copy()
    e[1:, :] &= m[:-1, :]
    e[:-1, :] &= m[1:, :]
    e[:, 1:] &= m[:, :-1]
    e[:, :-1] &= m[:, 1:]
    e[0, :] = e[-1, :] = False
    e[:, 0] = e[:, -1] = False
    return e


class Canvas:
    def __init__(self, w=MW, h=MH, top=MTOP):
        self.w, self.h, self.top = w, h, top
        self.img = Image.new("RGBA", (w, h + top), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x, y, col):
        yy = y + self.top
        if 0 <= x < self.w and 0 <= yy < self.h + self.top:
            self.px[x, yy] = tuple(col[:3]) + ((col[3],) if len(col) > 3 else (255,))

    def blend(self, x, y, col, a):
        yy = y + self.top
        if not (0 <= x < self.w and 0 <= yy < self.h + self.top):
            return
        r, g, b, al = self.px[x, yy]
        self.px[x, yy] = (round(r + (col[0] - r) * a), round(g + (col[1] - g) * a), round(b + (col[2] - b) * a), max(al, 255 if a >= 1 else al))

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, col)

    def mask(self, x0, y0, m, col):
        h, w = m.shape
        for yy in range(h):
            for xx in range(w):
                if m[yy, xx]:
                    self.put(x0 + xx, y0 + yy, col)

    def box(self, x0, y0, x1, y1, fill, edge=None, r=2, hi=None, lo=None, lo_h=1, hi_h=1):
        """둥근 상자 (끝 좌표 포함): 테두리 · 안 · 안쪽 위 밝은 줄 · 안쪽 아래 어두운 줄"""
        w, h = x1 - x0 + 1, y1 - y0 + 1
        m = rounded(w, h, r)
        inner = erode(m) if edge else m
        if edge:
            self.mask(x0, y0, m, edge)
        self.mask(x0, y0, inner, fill)
        rows = np.where(inner.any(axis=1))[0]
        if len(rows) == 0:
            return
        if hi:
            for k in range(hi_h):
                y = rows[0] + k
                for xx in np.where(inner[y])[0]:
                    self.put(x0 + xx, y0 + y, hi)
        if lo:
            for k in range(lo_h):
                y = rows[-1] - k
                for xx in np.where(inner[y])[0]:
                    self.put(x0 + xx, y0 + y, lo)

    def paste(self, img, x, y, tint=None, alpha=1.0):
        src = img.convert("RGBA")
        for yy in range(src.size[1]):
            for xx in range(src.size[0]):
                r, g, b, a = src.getpixel((xx, yy))
                if a == 0:
                    continue
                if tint:
                    r, g, b = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255)
                self.blend(x + xx, y + yy, (r, g, b), a / 255 * alpha)

    def text(self, x, y, s, font, col, outline=None, center=False):
        """글자 (갈무리 — 픽셀 글꼴, 테두리 1px). y = 글자 위"""
        tmp = Image.new("L", (200, 24), 0)
        d = ImageDraw.Draw(tmp)
        d.fontmode = "1"
        d.text((2, 14), s, font=font, fill=255, anchor="ls")
        bb = tmp.getbbox()
        if not bb:
            return
        g = np.array(tmp.crop(bb)) > 0
        h, w = g.shape
        if center:
            x = x - w // 2
        if outline:
            pad = np.zeros((h + 2, w + 2), dtype=bool)
            pad[1:-1, 1:-1] = g
            o = pad.copy()
            o[1:, :] |= pad[:-1, :]
            o[:-1, :] |= pad[1:, :]
            o[:, 1:] |= pad[:, :-1]
            o[:, :-1] |= pad[:, 1:]
            self.mask(x - 1, y - 1, o & ~pad, outline)
        self.mask(x, y, g, col)
        return w

    def final(self):
        """바닐라 상자 그림 자리 (0 ~ 175 × 0 ~ 184) 가 빈 곳 없이 불투명한지 확인"""
        a = np.array(self.img)[self.top:self.top + VANILLA_H, :, 3]
        if (a < 255).any():
            ys, xs = np.where(a < 255)
            raise SystemExit("바닐라 상자 그림이 비침: %d칸 (예: x %d y %d)" % (len(xs), xs[0], ys[0]))
        return self.img


def cell_x(c):
    return 7 + 18 * c


def cell_y(r):
    """9 × 4 메뉴의 줄 r 칸 틀 위 (0 ~ 3 메뉴 칸, 4 ~ 6 가방, 7 핫바)"""
    return 17 + 18 * r if r < 4 else (102 + 18 * (r - 4) if r < 7 else 160)


# ------------------------------------------------------------------ 탭 그림 (16 × 16)

TAB_ICONS = {
    "home": [
        "................",
        "................",
        ".......##.......",
        "......####......",
        ".....##..##.....",
        "....##....##....",
        "...##......##...",
        "..##........##..",
        ".###........###.",
        "...#........#...",
        "...#..####..#...",
        "...#..#..#..#...",
        "...#..#..#..#...",
        "...####..####...",
        "................",
        "................",
    ],
    "stats": [
        "................",
        "................",
        "...........###..",
        "...........#.#..",
        "...........#.#..",
        ".......###.#.#..",
        ".......#.#.#.#..",
        ".......#.#.#.#..",
        "...###.#.#.#.#..",
        "...#.#.#.#.#.#..",
        "...#.#.#.#.#.#..",
        "...#.#.#.#.#.#..",
        "..############..",
        "................",
        "................",
        "................",
    ],
    "skills": [
        "................",
        ".......#........",
        ".......#........",
        "......###.....#.",
        "......###....###",
        ".....#####....#.",
        "..###########...",
        "....#######.....",
        "......###.......",
        ".....#####......",
        ".....##.##......",
        "....##...##.....",
        "....#.....#.....",
        "................",
        "................",
        "................",
    ],
    "equip": [
        "................",
        "...###....###...",
        "..#..#....#..#..",
        ".#...#....#...#.",
        ".#....####....#.",
        ".#............#.",
        ".###........###.",
        "...#........#...",
        "...#...##...#...",
        "...#...##...#...",
        "...#........#...",
        "...#........#...",
        "...##########...",
        "................",
        "................",
        "................",
    ],
    "gacha": [
        "................",
        "..##########....",
        "..#........#....",
        "..#..####..#....",
        "..#.#....#.#.##.",
        "..#.#....#.#.#..",
        "..#..#..#..#.#..",
        "..#...##...#.#..",
        "..#..#..#..#.#..",
        "..#.#....#.#.#..",
        "..#.#....#.#.#..",
        "..#..####..#.#..",
        "..#........#.#..",
        "..##########.#..",
        "....##########..",
        "................",
    ],
}

# 리본 그림 (7 × 6)
RIBBON_ICONS = {
    "helmet": [".#####.", "#######", "##...##", "#.###.#", "#.#.#.#", "#.....#"],
    "chest": ["##...##", "#######", ".#####.", ".#####.", ".#####.", ".#####."],
    "legs": ["#######", "#######", "###.###", "##...##", "##...##", "##...##"],
    "boots": [".##.##.", ".##.##.", ".##.##.", ".##.##.", "###.###", "###.###"],
    "sword": ["......#", ".....#.", "#...#..", ".#.#...", "..#....", ".#.#..."],
    "shield": ["#######", "#.###.#", "#.###.#", ".#.#.#.", "..#.#..", "...#..."],
}


# ------------------------------------------------------------------ 공통 틀

def frame(page_tab):
    """판 · 탭 막대 · 가방 판 (모든 쪽 같음 — 켜진 탭만 다름)"""
    c = Canvas()
    c.rect(0, 0, MW - 1, VANILLA_H - 1, INK)
    # 위 판 (0 ~ 92)
    c.box(0, 0, MW - 1, 92, CREAM, EDGE, r=2, hi=CREAM_HI, lo=CREAM_LO, lo_h=2)
    # 가방 판 (95 ~ 184)
    c.box(0, 95, MW - 1, VANILLA_H - 1, CREAM, EDGE, r=2, hi=CREAM_HI, lo=CREAM_LO, lo_h=2)
    c.box(6, 101, 169, 156, TAN, TAN_LO, r=1)
    c.rect(7, 155, 168, 155, TAN_HI)
    for col in range(9):
        x = cell_x(col)
        c.box(x + 1, 161, x + 16, 176, BROWN, None, r=1, hi=BROWN_HI, lo=BROWN_LO)
    # 탭 막대 (판 왼쪽 위에 걸림 — 위 걸이). 칸 0 의 아이템 (8 ~ 23) 은 막대 안, 칸 1 (25 ~) 부터 내용
    c.box(1, -8, 23, 92, GREEN, GREEN_LO, r=2, hi=GREEN_HI)
    c.rect(2, -6, 2, 90, GREEN_HI)
    c.box(-1 + 1, -11, 24, -8, BROWN_HI, EDGE, r=1)
    c.rect(1, -10, 23, -10, (170, 128, 86))
    for x in (0, 23):
        c.box(x, -12, x + 1, -7, EDGE_HI, EDGE, r=0)
    c.box(8, -5, 15, 2, GREEN_LO, None, r=2)
    c.box(9, -4, 14, 1, LEAF_LO, None, r=2)
    c.box(10, -3, 13, 0, GREEN_HI, None, r=1)
    order = ["home", "stats", "skills", "equip"]
    for i, key in enumerate(order):
        y = cell_y(i)
        on = key == page_tab
        if on:
            c.box(4, y, 21, y + 17, TAB_ON, GREEN_LO, r=2, lo=TAB_ON_LO, lo_h=2)
        c.paste(pattern(TAB_ICONS[key], GREEN if on else LEAF), 5 if on else 5, y + 1)
    return c


def card(c, x0, y0, x1, y1, base, label, font):
    """홈의 큰 카드 (색 판 · 가운데 밝은 원 · 아래 이름)"""
    hi = tuple(min(255, int(v * 1.25 + 12)) for v in base)
    lo = tuple(int(v * 0.72) for v in base)
    lo2 = tuple(int(v * 0.55) for v in base)
    c.box(x0, y0, x1, y1, base, lo2, r=3, hi=hi, lo=lo, lo_h=3)
    cx = (x0 + x1) / 2
    cy = y0 + 18.5
    for y in range(y0 + 2, y1 - 2):
        for x in range(x0 + 2, x1 - 1):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d < 12:
                c.blend(x, y, hi, 0.28 if d < 9 else 0.14)
    c.text(round(cx) + 1, y1 - 12, label, font, WHITE, outline=lo2, center=True)


def small_button(c, col, row, base):
    x, y = cell_x(col), cell_y(row)
    hi = tuple(min(255, int(v * 1.22 + 10)) for v in base)
    lo = tuple(int(v * 0.72) for v in base)
    edge = tuple(int(v * 0.5) for v in base)
    c.box(x, y, x + 17, y + 17, base, edge, r=2, hi=hi, lo=lo, lo_h=2)


def slot_box(c, col, row, dark=True):
    """장비 · 스킬 칸 (16 × 16 어두운 갈색 — 아이템이 꼭 덮음, 칸 사이 2px 는 막대 색): 안쪽 위 · 왼쪽 그림자"""
    x, y = cell_x(col) + 1, cell_y(row) + 1
    c.rect(x, y, x + 15, y + 15, SLOT)
    c.rect(x, y, x + 15, y, SLOT_LO)
    c.rect(x, y, x, y + 15, SLOT_LO)
    c.rect(x + 1, y + 15, x + 15, y + 15, SLOT_HI)
    c.rect(x + 15, y + 1, x + 15, y + 15, SLOT_HI)


def ribbon(c, cx, y, icon, color=LEAF, dark=LEAF_LO):
    """칸 아래 리본 (12 × 13, 아래 V 홈) + 흰 그림"""
    x0 = cx - 6
    for yy in range(13):
        for xx in range(12):
            notch = yy >= 10 and (abs(xx - 5.5) < (yy - 9))
            if notch:
                continue
            col = dark if xx in (0, 11) or yy == 0 else color
            c.put(x0 + xx, y + yy, col)
    c.rect(x0, y, x0 + 11, y, EDGE)
    c.paste(pattern(RIBBON_ICONS[icon], WHITE), x0 + 3, y + 2)


def sprite(path):
    return Image.open(os.path.join(SPRITES, path)).convert("RGBA")


# ------------------------------------------------------------------ 쪽

CARDS = [("장비", (70, 100, 124)), ("스킬", (54, 116, 98)), ("장터", (128, 46, 60)), ("뽑기", (100, 56, 132))]
SMALL = [(176, 82, 60), (92, 72, 136), (196, 132, 56), (132, 100, 66), (52, 118, 122), (78, 128, 62), (118, 118, 124), (120, 46, 46)]


def menu_home(font):
    c = frame("home")
    # 카드 띠 (줄 0 ~ 2) — 카드 넷 (칸 둘 × 줄 셋)
    c.box(25, 15, 171, 69, GREEN, GREEN_LO, r=2, hi=GREEN_HI)
    for i, (label, base) in enumerate(CARDS):
        x0 = cell_x(1 + 2 * i)
        card(c, x0 + 2, 17, x0 + 33, 67, base, label, font)
    # 작은 단추 줄 (줄 3)
    for i, base in enumerate(SMALL):
        small_button(c, 1 + i, 3, base)
    return c.final()


def menu_stats(font):
    c = frame("stats")
    # 진열장 (칸 1 ~ 8 · 줄 0 ~ 3 — 아래 단추가 테두리에 걸침)
    c.box(26, 15, 171, 80, (150, 112, 72), EDGE, r=3, hi=(176, 134, 88), lo=(128, 94, 60), lo_h=2)
    c.box(30, 18, 167, 77, (138, 102, 64), (118, 86, 54), r=2)
    # 선반 (칸 3 ~ 6 아래 — 사다리꼴 금색 판)
    sx0, sx1 = cell_x(3) - 6, cell_x(7) + 5
    for k, col in enumerate([GOLD_HI, GOLD, GOLD, GOLD_LO, GOLD_LO, (140, 96, 40)]):
        y = 34 + k
        inset = max(0, 3 - k)
        c.rect(sx0 + inset, y, sx1 - inset, y, col)
    c.rect(sx0 + 1, 40, sx1 - 1, 40, (96, 66, 30))
    # 값 받침 (스탯 값 글자 — 줄 글꼴 d 35: y 39 ~ 47)
    for i in range(4):
        x = cell_x(3 + i)
        c.box(x + 1, 42, x + 16, 52, (112, 80, 48), None, r=1)
    # 아래 화살표 (가운데)
    arrow = ["..###..", "..###..", "..###..", "#######", ".#####.", "..###..", "...#..."]
    c.paste(pattern(arrow, (96, 66, 40)), 94, 58)
    # 스탯 트리 단추 (칸 3 ~ 6 · 줄 3 — 진열장 아래 테두리에 걸침)
    bx0, bx1 = cell_x(3), cell_x(6) + 17
    c.box(bx0, 71, bx1, 88, (64, 98, 116), (34, 52, 62), r=2, hi=(92, 132, 152), lo=(48, 76, 90), lo_h=2)
    for (x, y, dx, dy) in [(bx0 + 2, 73, 1, 1), (bx1 - 2, 73, -1, 1), (bx0 + 2, 85, 1, -1), (bx1 - 2, 85, -1, -1)]:
        c.put(x, y, (180, 214, 226))
        c.put(x + dx, y, (180, 214, 226))
        c.put(x, y + dy, (180, 214, 226))
    tree = ["...###...", "..#####..", ".#######.", "#########", ".#######.", "...###...", "....#....", "....#....", "..#####.."]
    c.paste(pattern(tree, (150, 214, 120)), bx0 + 12, 75)
    c.text(bx0 + 24, 76, "스탯 트리", font, WHITE, outline=(34, 52, 62))
    return c.final()


def menu_skills(font):
    c = frame("skills")
    # 룬 칸 쟁반 (줄 1 · 칸 1 ~ 8)
    c.box(cell_x(1) - 1, cell_y(1) - 1, cell_x(8) + 18, cell_y(1) + 18, BROWN, EDGE, r=2, hi=BROWN_HI)
    for col in range(1, 9):
        slot_box(c, col, 1)
    # 직업 칸 쟁반 (줄 3 · 칸 1 ~ 5)
    c.box(cell_x(1) - 1, cell_y(3) - 1, cell_x(5) + 18, cell_y(3) + 18, (110, 64, 98), (70, 38, 62), r=2, hi=(138, 86, 124))
    for col in range(1, 6):
        slot_box(c, col, 3)
    # 빼기 · 도움말 단추
    small_button(c, 7, 3, (150, 56, 52))
    small_button(c, 8, 3, (70, 104, 128))
    # 줄 0 · 2 머리 줄 밑줄
    c.rect(cell_x(1) + 2, 32, cell_x(8) + 15, 32, CREAM_LO2)
    c.rect(cell_x(1) + 2, 68, cell_x(8) + 15, 68, CREAM_LO2)
    return c.final()


def menu_equip(font):
    c = frame("equip")
    # 갑옷 막대 (줄 0 · 칸 1 ~ 4) + 리본
    c.box(cell_x(1) - 1, cell_y(0) - 2, cell_x(4) + 18, cell_y(0) + 19, BROWN, EDGE, r=2, hi=BROWN_HI)
    for i, key in enumerate(["helmet", "chest", "legs", "boots"]):
        slot_box(c, 1 + i, 0)
        ribbon(c, cell_x(1 + i) + 9, cell_y(1), key)
    # 손 막대 (줄 0 · 칸 6 ~ 7 — 강화 +N 은 칸 위)
    c.box(cell_x(6) - 1, 5, cell_x(7) + 18, cell_y(0) + 19, BROWN, EDGE, r=2, hi=BROWN_HI)
    for i, key in enumerate(["sword", "shield"]):
        slot_box(c, 6 + i, 0)
        ribbon(c, cell_x(6 + i) + 9, cell_y(1), key)
    # 정보 단추 (칸 8 · 줄 0)
    small_button(c, 8, 0, (78, 128, 62))
    # 아티팩트 막대 (줄 2 · 칸 1 ~ 5 — 강화 +N 은 칸 아래)
    c.box(cell_x(1) - 1, cell_y(2) - 2, cell_x(5) + 18, 84, (92, 58, 104), (56, 32, 64), r=2, hi=(120, 80, 134), lo=(80, 48, 92))
    ghosts = ["ghost_ring", "ghost_ring", "ghost_necklace", "ghost_earring", "ghost_bracelet"]
    for i, g in enumerate(ghosts):
        slot_box(c, 1 + i, 2)
        c.paste(sprite("artifact/" + g + ".png"), cell_x(1 + i) + 1, cell_y(2) + 1, tint=(150, 116, 160), alpha=0.5)
    # 유물 단추 (칸 8 · 줄 2)
    small_button(c, 8, 2, (52, 118, 122))
    return c.final()


def menu_gacha(font):
    c = frame("home")
    # 뽑기 종류 줄 (줄 0 · 칸 1 ~ 8)
    c.box(cell_x(1) - 1, cell_y(0) - 1, cell_x(8) + 18, cell_y(0) + 18, BROWN, EDGE, r=2, hi=BROWN_HI)
    for col in range(1, 9):
        slot_box(c, col, 0)
    # 결과 판 (줄 1 ~ 2 · 칸 1 ~ 5 — 10칸)
    c.box(cell_x(1) - 1, cell_y(1) + 1, cell_x(5) + 18, cell_y(2) + 18, (46, 30, 64), (26, 16, 38), r=3, hi=(70, 48, 94))
    for row in (1, 2):
        for col in range(1, 6):
            x, y = cell_x(col) + 1, cell_y(row) + 1
            c.box(x, y, x + 15, y + 15, (34, 22, 50), (62, 44, 86), r=1)
    # 뽑기 단추 (칸 7 ~ 8 · 줄 1 = 1회, 줄 2 = 10회)
    for row, label, base in [(1, "1회", (96, 64, 150)), (2, "10회", (176, 112, 44))]:
        x0, y0 = cell_x(7), cell_y(row)
        hi = tuple(min(255, int(v * 1.25 + 10)) for v in base)
        lo = tuple(int(v * 0.72) for v in base)
        edge = tuple(int(v * 0.48) for v in base)
        c.box(x0, y0 + 1, x0 + 35, y0 + 17, base, edge, r=2, hi=hi, lo=lo, lo_h=2)
        c.text(x0 + 18, y0 + 5, label, font, WHITE, outline=edge, center=True)
    return c.final()


def menu_tree(font):
    """스탯 트리 쪽 (스탯 쪽의 아래 단추): 줄 0 = 나무 탭 넷 · 초기화 · 합계, 줄 1 ~ 2 칸 1 ~ 5 = 칸 열 개 (뱀 모양 — 1 → 5 → 6 → 10),
    칸 6 ~ 8 = 갈래 둘 (그림 · 이름), 줄 3 = 설명 글"""
    c = frame("stats")
    # 나무 탭 (줄 0 · 칸 1 ~ 4)
    c.box(cell_x(1) - 1, cell_y(0) - 1, cell_x(4) + 18, cell_y(0) + 18, BROWN, EDGE, r=2, hi=BROWN_HI)
    for col in range(1, 5):
        slot_box(c, col, 0)
    small_button(c, 7, 0, (150, 70, 52))
    small_button(c, 8, 0, (78, 128, 62))
    # 덩굴 판 (줄 1 ~ 2 · 칸 1 ~ 5)
    x0, x1 = cell_x(1) - 1, cell_x(5) + 18
    c.box(x0, cell_y(1) - 1, x1, cell_y(2) + 18, (46, 66, 44), GREEN_LO, r=2, hi=(60, 84, 56))
    vine, vine_lo = (120, 152, 80), (78, 104, 56)
    ya, yb = cell_y(1) + 8, cell_y(2) + 8
    c.rect(cell_x(1) + 8, ya - 1, cell_x(5) + 9, ya + 1, vine)
    c.rect(cell_x(1) + 8, ya + 2, cell_x(5) + 9, ya + 2, vine_lo)
    c.rect(cell_x(5) + 8, ya - 1, cell_x(5) + 10, yb + 1, vine)
    c.rect(cell_x(1) + 8, yb - 1, cell_x(5) + 9, yb + 1, vine)
    c.rect(cell_x(1) + 8, yb + 2, cell_x(5) + 9, yb + 2, vine_lo)
    for row in (1, 2):
        for col in range(1, 6):
            x, y = cell_x(col) + 1, cell_y(row) + 1
            c.box(x, y, x + 15, y + 15, (30, 42, 30), (98, 128, 70), r=2)
    # 갈래 판 (줄 1 ~ 2 · 칸 6 ~ 8)
    c.box(cell_x(6) + 1, cell_y(1) - 1, cell_x(8) + 18, cell_y(2) + 18, (150, 112, 72), EDGE, r=2, hi=(176, 134, 88), lo=(128, 94, 60))
    for row in (1, 2):
        slot_box(c, 6, row)
        c.box(cell_x(7) - 1, cell_y(row) + 1, cell_x(8) + 16, cell_y(row) + 16, (112, 80, 48), None, r=1)
    c.rect(cell_x(6) + 3, cell_y(2) - 1, cell_x(8) + 15, cell_y(2) - 1, (118, 86, 54))
    return c.final()


# ------------------------------------------------------------------ 아이템 그림 · 설명 상자

def icon_plus(on):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col = (124, 255, 138, 255) if on else (90, 90, 100, 255)
    shade = (40, 110, 52, 255) if on else (40, 40, 48, 255)
    d.rectangle((7, 4, 9, 12), fill=shade)
    d.rectangle((4, 7, 12, 9), fill=shade)
    d.rectangle((6, 3, 8, 11), fill=col)
    d.rectangle((3, 6, 11, 8), fill=col)
    return img


def icon_info():
    rows = [
        "................",
        "................",
        "................",
        ".......##.......",
        ".......##.......",
        "................",
        "......###.......",
        ".......##.......",
        ".......##.......",
        ".......##.......",
        ".......##.......",
        "......####......",
        "................",
        "................",
        "................",
        "................",
    ]
    return pattern(rows, WHITE)


def tooltip_sprites():
    """설명 상자 (nine_slice 32 × 32, 테두리 9): 어두운 초록 바탕 + 올리브 테 (모서리 깎음).
    바닐라는 이 그림을 글자 둘레 12px 바깥까지 늘려 그림 (여백 3 + 9) — 보이는 상자는 그림 안쪽 8px 부터 (글자 둘레 4px)"""
    bg = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    fr = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    m = rounded(16, 16, 2)
    edge = m & ~erode(m)
    inner = erode(m) & ~erode(erode(m))
    for y in range(16):
        for x in range(16):
            if m[y, x]:
                bg.putpixel((x + 8, y + 8), (24, 32, 22, 244))
            if edge[y, x]:
                fr.putpixel((x + 8, y + 8), (122, 150, 84, 255))
            elif inner[y, x]:
                fr.putpixel((x + 8, y + 8), (54, 70, 46, 255))
    return bg, fr


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "tooltip"), exist_ok=True)
    sheet, lines, widths = text_sheet(sys.argv[1])
    sheet.save(os.path.join(OUT, "text.png"))
    json.dump(lines, open(os.path.join(OUT, "text_chars.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump({"space": 4, "default": 6, "widths": widths}, open(os.path.join(OUT, "text_widths.json"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    small = os.path.join(os.path.dirname(sys.argv[1]), "Galmuri7.ttf")
    nsheet, nwidths = num_sheet(small)
    nsheet.save(os.path.join(OUT, "num.png"))
    json.dump({"chars": NUM_CHARS, "space": 3, "widths": nwidths}, open(os.path.join(OUT, "num_widths.json"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    font = ImageFont.truetype(sys.argv[1], 10)
    for name, fn in [("menu_home", menu_home), ("menu_stats", menu_stats), ("menu_skills", menu_skills), ("menu_equip", menu_equip),
                     ("menu_gacha", menu_gacha), ("menu_tree", menu_tree)]:
        fn(font).save(os.path.join(OUT, name + ".png"))
    for old in ("stats.png", "skills.png"):
        p = os.path.join(OUT, old)
        if os.path.exists(p):
            os.remove(p)
    icon_plus(True).save(os.path.join(OUT, "icons", "plus_on.png"))
    icon_plus(False).save(os.path.join(OUT, "icons", "plus_off.png"))
    icon_info().save(os.path.join(OUT, "icons", "info.png"))
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(os.path.join(OUT, "icons", "blank.png"))
    bg, fr = tooltip_sprites()
    bg.save(os.path.join(OUT, "tooltip", "menu_background.png"))
    fr.save(os.path.join(OUT, "tooltip", "menu_frame.png"))
    print("glyphs", len(widths), "sheet", sheet.size, "rows", len(lines))


if __name__ == "__main__":
    main()
