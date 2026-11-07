"""모드 없는 클라이언트의 창 그림 (1번 4b): 상자 메뉴 제목 글자로 그리는 바탕 · 글자.

바닐라 상자 메뉴는 제목 (글자) 을 칸 아이템보다 먼저 그림 → 리소스 팩 글꼴의 큰 그림 글자 하나를 제목 자리에 두면 창 전체의 바탕이 되고,
뒤로 가는 빈 글자 (space) 로 자리를 옮겨 그 위에 글자를 씀. 세로 자리는 글꼴마다 다른 ascent (줄 글꼴 rpg:gl<d>) 로.

메뉴 (Shift + F — VanillaMenu) 2판 (사용자 2026-11-08: "너무 똑같은데 조금은 다르게", "색이나 배열이나 그런거 다르게 해서 그리고
바닐라 아이템으로 꾸미는가 너무 밤티남"): 6줄 상자 메뉴 (9 × 6) 하나에 쪽을 바꿔 그림. 모드 창 (rpgui — 남색 판 · 금테 · 주황 보석) 과 같은 계열,
위 탭 (홈 · 스탯 · 장비 · 스킬) + 닫기, 아래 진짜 가방 (어두운 칸). 쪽마다 바탕 그림 하나 (176 × 236 — 창 176 × 222 + 위 14: 금 장식).
바닐라 상자 그림 (창 0 ~ 175 × 0 ~ 220) 위는 모두 불투명하게. 그림 조각 (아이콘) 은 모두 직접 그림 (tools/vanilla_gui_art.py).

만드는 것 (src/main/resources/mmorpg/vanilla/gui — 모드 클라이언트가 읽는 assets 밖, VanillaPack 이 팩에만 넣음):
  text.png · text_chars.json · text_widths.json — 갈무리9 (Galmuri9, SIL OFL 1.1 — quiple) 를 10px 로 그린 글자판
      (ASCII · 쓰는 기호 · 한글 KS X 1001 2350자 + 데이터에 나오는 글자 + 그림 글자 U+E100 ~ — 동전 · 하트 · 마름모 · 뽑기권).
      칸 12 × 12, 기준선 10. 너비 = 오른쪽 끝 + 2 (마인크래프트 비트맵 글꼴 규칙)
  num.png · num_widths.json — 작은 숫자판 (갈무리7 — 같은 폴더의 Galmuri7.ttf): 스탯 값 · 강화 +N
  menu_home · menu_stats · menu_equip · menu_skills · menu_tree · menu_gacha .png — 메뉴 쪽 바탕
  icons/*.png — 칸에 놓는 아이템 그림 (rpg:vui/<이름> — 빈 그림 · 스탯 트리 보너스 종류 kind_* · 갈래 · 카드 뒷면 · 단추)
  tooltip/menu_background.png · menu_frame.png — 메뉴 아이템 설명 상자 (tooltip_style rpg:menu)

python3 tools/vanilla_gui.py <Galmuri9.ttf>   (같은 폴더에 Galmuri7.ttf · Galmuri11-Bold.ttf)
"""
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vanilla_gui_art as art  # noqa: E402

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

# ------------------------------------------------------------------ 색 (모드 창 rpgui 와 같은 계열 — 남색 판 · 금테 · 주황 보석)
VOID = (10, 10, 18)          # 판 사이 틈
PANEL = (28, 31, 56)         # 판
PANEL_HI = (36, 40, 72)      # 내용 판 (위 탭과 이어짐)
PANEL_LO = (21, 23, 42)      # 꺼진 탭 · 우묵한 곳
GLOW = (60, 68, 122)         # 가운데 빛
OUTLINE = (6, 6, 10)
GOLD_HI = (250, 226, 156)
GOLD = (214, 170, 80)
GOLD_MID = (176, 134, 58)
GOLD_LO = (120, 88, 34)
GOLD_DK = (70, 52, 22)
SLOT = (18, 19, 31)          # 칸 (rpgui slot.png)
SLOT_DK = (6, 6, 10)
SLOT_HI = (70, 66, 92)
BTN = (28, 30, 48)           # 단추 (rpgui button.png)
BTN_HI = (55, 57, 73)
GEM = (226, 112, 60)
GEM_HI = (255, 196, 128)
CREAM = (240, 228, 200)
MUTED = (150, 154, 190)
DIM = (92, 96, 132)
WHITE = (255, 255, 255)

MW, MH, MTOP = 176, 222, 14   # 9 × 6 상자 창 · 위 여유 (금 장식)
VANILLA_H = 221               # 바닐라 상자 그림 높이 (17 + 6 × 18 + 96) — 이 안은 모두 불투명


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
    """9 × 6 메뉴의 줄 r 칸 틀 위 (0 ~ 5 메뉴 칸, 6 ~ 8 가방, 9 핫바)"""
    return 17 + 18 * r if r < 6 else (138 + 18 * (r - 6) if r < 9 else 196)


def mid_x(c):
    return 16 + 18 * c


def mid_y(r):
    return cell_y(r) + 9


FONTS = {}


def font(name, size):
    key = (name, size)
    if key not in FONTS:
        FONTS[key] = ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    return FONTS[key]


FONT_DIR = ""


def sprite(path):
    return Image.open(os.path.join(SPRITES, path)).convert("RGBA")


# ------------------------------------------------------------------ 조각

def gold_frame(c, x0, y0, x1, y1, fill, gems=True, r=2):
    """금테 판 (모드 rpgui frame.png 처럼): 검은 테 · 금 띠 (위 · 왼쪽 밝게) · 안쪽 어두운 줄 · 판 + 모서리 보석"""
    w, h = x1 - x0 + 1, y1 - y0 + 1
    m0 = rounded(w, h, r)
    m1 = erode(m0)
    m2 = erode(m1)
    m3 = erode(m2)
    c.mask(x0, y0, m0, OUTLINE)
    # 금 띠 2px: 위 · 왼쪽 = 밝게, 아래 · 오른쪽 = 어둡게
    for yy in range(h):
        for xx in range(w):
            if m1[yy, xx] and not m3[yy, xx]:
                light = (xx + yy) < (w + h) / 2 and (yy < 3 or xx < 3)
                dark = yy >= h - 3 or xx >= w - 3
                outer = not m2[yy, xx]
                col = (GOLD_HI if outer else GOLD) if light else (GOLD_LO if dark and outer else (GOLD_MID if dark else GOLD))
                c.put(x0 + xx, y0 + yy, col)
    m4 = erode(m3)
    for yy in range(h):
        for xx in range(w):
            if m3[yy, xx] and not m4[yy, xx]:
                c.put(x0 + xx, y0 + yy, GOLD_DK)
            elif m4[yy, xx]:
                c.put(x0 + xx, y0 + yy, fill)
    if gems:
        for (gx, gy) in [(x0 + 1, y0 + 1), (x1 - 3, y0 + 1), (x0 + 1, y1 - 3), (x1 - 3, y1 - 3)]:
            gem(c, gx, gy)


def gem(c, x, y, big=False):
    """주황 보석 (3 × 3 마름모 — 큰 것 5 × 5)"""
    if big:
        pts = {(2, 0): GEM_HI, (1, 1): GEM_HI, (2, 1): GEM_HI, (3, 1): GEM, (0, 2): GEM_HI, (1, 2): GEM, (2, 2): WHITE, (3, 2): GEM,
               (4, 2): GEM, (1, 3): GEM, (2, 3): GEM, (3, 3): (170, 70, 40), (2, 4): (170, 70, 40)}
        for (dx, dy), col in pts.items():
            c.put(x + dx, y + dy, col)
        for (dx, dy) in [(2, -1), (-1, 2), (5, 2), (2, 5), (1, 0), (3, 0), (0, 1), (4, 1), (0, 3), (4, 3), (1, 4), (3, 4)]:
            if (dx, dy) not in pts:
                c.put(x + dx, y + dy, OUTLINE)
        return
    c.put(x + 1, y, GEM_HI)
    c.put(x, y + 1, GEM_HI)
    c.put(x + 1, y + 1, WHITE)
    c.put(x + 2, y + 1, GEM)
    c.put(x + 1, y + 2, GEM)


def thin_frame(c, x0, y0, x1, y1, fill, edge=GOLD_LO, r=2, glow=None):
    """얇은 금 줄 판 (안쪽 판 · 카드)"""
    w, h = x1 - x0 + 1, y1 - y0 + 1
    m0 = rounded(w, h, r)
    m1 = erode(m0)
    c.mask(x0, y0, m0, edge)
    c.mask(x0, y0, m1, fill)
    if glow:
        cx, cy, rad, col, a = glow
        for yy in range(h):
            for xx in range(w):
                if not m1[yy, xx]:
                    continue
                d = math.hypot(x0 + xx + 0.5 - cx, y0 + yy + 0.5 - cy) / rad
                if d < 1:
                    c.blend(x0 + xx, y0 + yy, col, a * (1 - d) ** 1.5)


def slot(c, col, row):
    """칸 (rpgui slot.png — 우묵: 위 · 왼쪽 어둡게, 아래 · 오른쪽 밝게)"""
    x, y = cell_x(col), cell_y(row)
    slot_at(c, x, y)


def slot_at(c, x, y):
    c.rect(x, y, x + 17, y + 17, SLOT)
    c.rect(x, y, x + 17, y, SLOT_DK)
    c.rect(x, y, x, y + 17, SLOT_DK)
    c.rect(x + 1, y + 17, x + 17, y + 17, SLOT_HI)
    c.rect(x + 17, y + 1, x + 17, y + 17, SLOT_HI)


def button(c, x0, y0, x1, y1, tint=None, gold=True):
    """단추 (rpgui button.png — 남색 + 금 테두리, 위 밝은 줄, 아래 그림자)"""
    fill = BTN if not tint else tuple(int(BTN[i] * 0.45 + tint[i] * 0.55) for i in range(3))
    hi = BTN_HI if not tint else tuple(min(255, int(fill[i] * 1.35 + 14)) for i in range(3))
    lo = tuple(int(fill[i] * 0.7) for i in range(3))
    w, h = x1 - x0 + 1, y1 - y0 + 1
    m0 = rounded(w, h, 2)
    m1 = erode(m0)
    m2 = erode(m1)
    c.mask(x0, y0, m0, OUTLINE)
    c.mask(x0, y0, m1, GOLD_MID if gold else (60, 62, 96))
    c.mask(x0, y0, m2, fill)
    rows = np.where(m2.any(axis=1))[0]
    for xx in np.where(m2[rows[0]])[0]:
        c.put(x0 + xx, y0 + rows[0], hi)
    for k in (1, 2):
        for xx in np.where(m2[rows[-k]])[0]:
            c.put(x0 + xx, y0 + rows[-k], lo)
    if gold:
        for xx in np.where(m1[1])[0]:
            if not m2[1, xx]:
                c.put(x0 + xx, y0 + 1, GOLD_HI)


def text_width(s, f):
    tmp = Image.new("L", (240, 24), 0)
    d = ImageDraw.Draw(tmp)
    d.fontmode = "1"
    d.text((2, 14), s, font=f, fill=255, anchor="ls")
    bb = tmp.getbbox()
    return 0 if not bb else bb[2] - bb[0]


def icon(c, rows, x, y, col):
    c.paste(art.art(rows, one=col), x, y)


def label(c, x, y, s, f, col, outline=OUTLINE, center=False):
    return c.text(x, y, s, f, col, outline=outline, center=center)


def glow(c, cx, cy, rad, col, a, box=None):
    x0, y0, x1, y1 = box or (int(cx - rad), int(cy - rad), int(cx + rad), int(cy + rad))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / rad
            if d < 1:
                c.blend(x, y, col, a * (1 - d) ** 1.6)


def dotted_h(c, x0, x1, y, col):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        if (x - x0) % 2 == 0:
            c.put(x, y, col)


def dotted_v(c, x, y0, y1, col):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        if (y - y0) % 2 == 0:
            c.put(x, y, col)


def ring(c, cx, cy, r0, r1, col, a=1.0):
    """원 띠 (반지름 r0 ~ r1)"""
    for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
        for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                c.blend(x, y, col, a)


def node_socket(c, col, row, color=GOLD_LO, fork=False):
    """나무 칸 받침: 칸 + 1px 색 테 (갈래는 금 테 + 모서리 보석)"""
    x, y = cell_x(col), cell_y(row)
    slot_at(c, x, y)
    for k in range(18):
        c.put(x + k, y, color)
        c.put(x + k, y + 17, color)
        c.put(x, y + k, color)
        c.put(x + 17, y + k, color)
    if fork:
        for (gx, gy) in ((x - 1, y - 1), (x + 16, y - 1), (x - 1, y + 16), (x + 16, y + 16)):
            gem(c, gx, gy)


def socket(c, col, row, color=GOLD_LO, diamond=False):
    """칸 둘레 둥근 받침 (스탯 칸)"""
    cx, cy = mid_x(col), mid_y(row)
    if diamond:
        for y in range(cy - 12, cy + 13):
            for x in range(cx - 12, cx + 13):
                d = abs(x + 0.5 - cx) + abs(y + 0.5 - cy)
                if 11.0 <= d <= 12.2:
                    c.put(x, y, color)
                elif 12.2 < d <= 13.2:
                    c.put(x, y, OUTLINE)
    else:
        ring(c, cx, cy, 9.6, 10.8, color)
        ring(c, cx, cy, 10.8, 11.8, OUTLINE)
    slot_at(c, cx - 9, cy - 9)


# ------------------------------------------------------------------ 공통 틀

TABS = [("home", "홈"), ("stats", "스탯"), ("equip", "장비"), ("skills", "스킬")]


def frame(active):
    """판 · 위 탭 넷 + 닫기 · 내용 판 · 가방 판 (모든 쪽 같음 — 켜진 탭만 다름)"""
    c = Canvas()
    c.rect(0, 0, MW - 1, VANILLA_H - 1, VOID)
    f9 = font("Galmuri9.ttf", 10)
    # 위 판 (0 ~ 129) · 내용 판 (탭 아래)
    gold_frame(c, 0, 0, MW - 1, 129, PANEL)
    thin_frame(c, 3, 35, MW - 4, 126, PANEL_HI, edge=GOLD_LO, r=2, glow=(88, 80, 90, GLOW, 0.55))
    # 탭 (줄 0 · 칸 2i ~ 2i + 1) — 글자만 (가운데), 켜진 탭은 내용 판과 이어지고 보석
    for i, (key, name) in enumerate(TABS):
        x0, x1 = cell_x(2 * i) + 1, cell_x(2 * i + 1) + 16
        on = key == active
        tw = text_width(name, f9)
        tx = (x0 + x1 + 1) // 2 - tw // 2
        if on:
            m = rounded(x1 - x0 + 1, 20, 2)
            m[-2:, :] = True
            inner = erode(m)
            inner[-2:, 1:-1] = True
            c.mask(x0, 17, m, OUTLINE)
            c.mask(x0, 17, inner, GOLD_MID)
            inner2 = erode(inner)
            inner2[-3:, 2:-2] = True
            c.mask(x0, 17, inner2, PANEL_HI)
            c.rect(x0 + 2, 18, x1 - 2, 18, GOLD_HI)
            c.rect(x0 + 2, 35, x1 - 2, 36, PANEL_HI)
            gem(c, tx - 6, 25)
            label(c, tx + 1, 23, name, f9, CREAM, outline=None)
        else:
            thin_frame(c, x0, 20, x1, 35, PANEL_LO, edge=GOLD_DK, r=2)
            c.rect(x0, 35, x1, 35, GOLD_LO)
            label(c, tx, 25, name, f9, MUTED, outline=None)
    # 닫기 (칸 8 · 줄 0)
    button(c, cell_x(8) + 1, 19, cell_x(8) + 16, 34, tint=(150, 50, 50))
    icon(c, art.QUICK["close"], cell_x(8) + 3, 21, GOLD_HI)
    # 가방 판 (131 ~ 220): 칸 · 핫바 칸 (사이 금 줄)
    gold_frame(c, 0, 131, MW - 1, VANILLA_H - 1, PANEL)
    for row in range(6, 10):
        for col in range(9):
            slot(c, col, row)
    c.rect(8, 193, MW - 9, 193, GOLD_LO)
    c.rect(8, 194, MW - 9, 194, GOLD_DK)
    # 위 금 장식 (창 위 가운데 — 날개 + 보석)
    cx = MW // 2
    for dx in range(-30, 31):
        t = abs(dx) / 30
        hgt = int(round(4 * (1 - t) ** 0.7))
        for dy in range(hgt + 1):
            y = -1 - dy
            col = GOLD_HI if dy == hgt else (GOLD if dy > hgt // 2 else GOLD_MID)
            c.put(cx + dx, y, col)
        c.put(cx + dx, -2 - hgt, OUTLINE)
    for dx in range(-9, 10):
        for dy in range(-9, 1):
            d = abs(dx) + abs(dy + 5)
            if d <= 5:
                c.put(cx + dx, dy - 1, OUTLINE if d == 5 else (GEM_HI if dy < -5 and dx <= 0 else GEM if d > 2 else WHITE if d == 0 else (255, 150, 90)))
    return c


# ------------------------------------------------------------------ 홈

TILES = [  # (칸 c0, 줄 r0, 이름, 설명, 색)
    (0, 1, "장비", "갑옷 · 무기", (64, 110, 170)),
    (5, 1, "스킬", "룬 · 직업", (36, 140, 128)),
    (0, 3, "장터", "사고 팔기", (196, 124, 40)),
    (5, 3, "뽑기", "룬 뽑기", (128, 70, 186)),
]
QUICK_ROW = ["quest", "storage", "mount", "mount_select", "relic", "tree", "cosmetic", "trash", "help"]


def menu_home():
    c = frame("home")
    fb = font("Galmuri11-Bold.ttf", 12)
    f7 = font("Galmuri7.ttf", 8)
    for (c0, r0, name, sub, accent) in TILES:
        x0, y0 = cell_x(c0), cell_y(r0)
        x1, y1 = cell_x(c0 + 3) + 17, cell_y(r0 + 1) + 17
        thin_frame(c, x0, y0 + 1, x1, y1 - 1, (22, 24, 46), edge=GOLD_MID, r=3)
        # 그림 자리 (왼쪽 30 × 30 — 색 판 + 빛)
        wx0, wy0 = x0 + 3, y0 + 3
        dark = tuple(int(v * 0.42) for v in accent)
        thin_frame(c, wx0, wy0, wx0 + 29, wy0 + 29, dark, edge=tuple(int(v * 0.8) for v in accent), r=2,
                   glow=(wx0 + 15, wy0 + 15, 16, accent, 0.9))
        # 이름 · 설명
        label(c, x0 + 37, y0 + 7, name, fb, CREAM)
        c.text(x0 + 37, y0 + 23, sub, f7, MUTED)
    # 가운데 세로 장식 (칸 4 · 줄 1 ~ 4)
    xm = mid_x(4)
    for y in range(cell_y(1) + 4, cell_y(5) - 4):
        c.put(xm, y, GOLD_DK if (y // 2) % 2 else GOLD_LO)
    gem(c, xm - 2, mid_y(2) + 7, big=True)
    # 빠른 단추 (줄 5 · 칸 0 ~ 8)
    for i, key in enumerate(QUICK_ROW):
        x, y = cell_x(i), cell_y(5)
        dim = key == "cosmetic"
        button(c, x + 1, y + 1, x + 16, y + 16, tint=(60, 60, 80) if dim else None)
        icon(c, art.QUICK[key], x + 3, y + 3, DIM if dim else GOLD_HI)
    return c.final()


# ------------------------------------------------------------------ 스탯 (기둥 넷 — 칸 1 · 3 · 5 · 7)

STATS = [("힘", (226, 92, 84)), ("민첩", (96, 206, 112)), ("체력", (236, 120, 170)), ("마력", (96, 150, 240))]


def menu_stats():
    c = frame("stats")
    f9 = font("Galmuri9.ttf", 10)
    for i, (name, col) in enumerate(STATS):
        cc = 1 + 2 * i
        x0, x1 = mid_x(cc) - 15, mid_x(cc) + 14
        y0, y1 = cell_y(1) + 2, cell_y(4) + 15
        thin_frame(c, x0, y0, x1, y1, (22, 24, 46), edge=GOLD_LO, r=3, glow=(mid_x(cc), mid_y(2), 18, col, 0.35))
        # 머리 띠 (스탯 색) + 이름
        band = tuple(int(v * 0.55) for v in col)
        c.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 11, band)
        c.rect(x0 + 1, y0 + 12, x1 - 1, y0 + 12, tuple(int(v * 0.8) for v in col))
        label(c, mid_x(cc) + 1, y0 + 2, name, f9, WHITE, outline=tuple(int(v * 0.3) for v in col), center=True)
        # 그림 받침 (줄 2)
        socket(c, cc, 2, color=tuple(int(v * 0.85) for v in col))
        # 값 받침 (줄 3 위 — 숫자 글꼴 d 68: y 74 ~ 80) · 효과 (d 78: y 84 ~ 90)
        c.rect(x0 + 3, 72, x1 - 3, 82, (14, 15, 28))
        c.rect(x0 + 3, 82, x1 - 3, 82, GOLD_DK)
        # 아래 + 표시
        px, py = mid_x(cc), y1 - 9
        for k in range(-2, 3):
            c.put(px + k, py, GOLD_MID)
            c.put(px, py + k, GOLD_MID)
    # 가운데 아래로 흐르는 점선 + 화살표 → 스탯 트리 단추
    xm = mid_x(4)
    dotted_v(c, xm, cell_y(1) + 6, cell_y(4) + 8, GOLD_LO)
    arrow = ["#######", ".#####.", "..###..", "...#..."]
    icon(c, arrow, xm - 3, cell_y(4) + 10, GOLD)
    # 스탯 트리 단추 (줄 5 · 칸 2 ~ 6 — 가운데)
    bx0, bx1 = cell_x(2) + 1, cell_x(6) + 16
    button(c, bx0, cell_y(5) + 1, bx1, cell_y(5) + 16, tint=(40, 110, 90))
    tw = text_width("스탯 트리", f9)
    sx = (bx0 + bx1) // 2 - (tw + 16) // 2
    icon(c, art.QUICK["tree"], sx, cell_y(5) + 3, GOLD_HI)
    label(c, sx + 16, cell_y(5) + 4, "스탯 트리", f9, CREAM)
    return c.final()


# ------------------------------------------------------------------ 장비 (가운데 사람 · 왼쪽 갑옷 · 오른쪽 아티팩트 · 아래 손)

def figure(c, cx, top):
    """사람 그림 (앞모습 — 어두운 몸 + 금 테두리 빛)"""
    body = np.zeros((70, 40), dtype=bool)

    def ellipse(x0, y0, x1, y1):
        cxx, cyy, rx, ry = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                if ((x + 0.5 - cxx) / rx) ** 2 + ((y + 0.5 - cyy) / ry) ** 2 <= 1 and 0 <= y < 70 and 0 <= x < 40:
                    body[y, x] = True

    def poly(pts):
        from matplotlib.path import Path  # noqa
        p = Path(pts)
        for y in range(70):
            for x in range(40):
                if p.contains_point((x + 0.5, y + 0.5)):
                    body[y, x] = True

    ellipse(14, 0, 26, 12)                       # 머리
    poly([(17, 11), (23, 11), (23, 15), (17, 15)])  # 목
    poly([(8, 15), (32, 15), (29, 38), (11, 38)])    # 몸
    poly([(8, 15), (11, 17), (6, 38), (3, 37)])      # 왼팔
    poly([(32, 15), (29, 17), (34, 38), (37, 37)])   # 오른팔
    ellipse(2, 36, 7, 41)                        # 왼손
    ellipse(33, 36, 38, 41)                      # 오른손
    poly([(11, 37), (19, 37), (18, 66), (12, 66)])   # 왼다리
    poly([(21, 37), (29, 37), (28, 66), (22, 66)])   # 오른다리
    poly([(10, 64), (19, 64), (19, 69), (9, 69)])    # 왼발
    poly([(21, 64), (30, 64), (31, 69), (21, 69)])   # 오른발
    x0 = cx - 20
    for y in range(70):
        for x in range(40):
            if not body[y, x]:
                continue
            on_edge = (x == 0 or not body[y, x - 1]) or (x == 39 or not body[y, x + 1]) or (y == 0 or not body[y - 1, x]) or (y == 69 or not body[y + 1, x])
            if on_edge:
                c.put(x0 + x, top + y, GOLD if x < 20 else GOLD_MID)
            else:
                shade = 1 - abs(x - 19.5) / 24
                c.put(x0 + x, top + y, (int(40 + 28 * shade), int(46 + 30 * shade), int(84 + 48 * shade)))


EQUIP_ARMOR = ["helmet", "chest", "legs", "boots"]
ART_GHOSTS = ["ghost_ring", "ghost_ring", "ghost_necklace", "ghost_earring", "ghost_bracelet"]


def menu_equip():
    c = frame("equip")
    cx = mid_x(4)
    glow(c, cx, 70, 46, GLOW, 0.8)
    # 받침 (발 아래 금 타원)
    for y in range(102, 110):
        for x in range(cx - 26, cx + 27):
            d = ((x + 0.5 - cx) / 26) ** 2 + ((y + 0.5 - 106) / 4) ** 2
            if 0.62 <= d <= 1.0:
                c.put(x, y, GOLD_LO if y > 105 else GOLD_MID)
    figure(c, cx, 36)
    # 왼쪽 갑옷 (칸 0 · 줄 1 ~ 4) — 점선으로 몸까지
    body_x = [cx - 7, cx - 12, cx - 6, cx - 6]
    body_y = [42, 62, 84, 101]
    for i, key in enumerate(EQUIP_ARMOR):
        r = 1 + i
        slot(c, 0, r)
        icon(c, art.GHOST[key], cell_x(0) + 3, cell_y(r) + 3, (54, 58, 92))
        y = mid_y(r)
        dotted_h(c, cell_x(1), body_x[i] - 3, y, GOLD_DK)
        if body_y[i] != y:
            dotted_v(c, body_x[i] - 3, y, body_y[i], GOLD_DK)
        c.put(body_x[i] - 2, body_y[i], GOLD)
    # 오른쪽 아티팩트 (칸 8 · 줄 1 ~ 5) — 강화 +N 은 칸 왼쪽 (칸 7)
    for i, g in enumerate(ART_GHOSTS):
        r = 1 + i
        slot(c, 8, r)
        c.paste(sprite("artifact/" + g + ".png"), cell_x(8) + 1, cell_y(r) + 1, tint=(120, 110, 170), alpha=0.45)
        y = mid_y(r)
        if r <= 4:
            dotted_h(c, cx + 14, cell_x(7) + 2, y, GOLD_DK)  # 칸 7 오른쪽은 강화 +N 자리
    # 아래 손 (줄 5 · 칸 3 = 무기, 칸 5 = 왼손) · 정보 단추 (칸 0)
    for col, key in ((3, "sword"), (5, "shield")):
        slot(c, col, 5)
        icon(c, art.GHOST[key], cell_x(col) + 3, cell_y(5) + 3, (54, 58, 92))
    button(c, cell_x(0) + 1, cell_y(5) + 1, cell_x(0) + 16, cell_y(5) + 16, tint=(60, 120, 80))
    icon(c, art.QUICK["info"], cell_x(0) + 3, cell_y(5) + 3, GOLD_HI)
    return c.final()


# ------------------------------------------------------------------ 스킬 장착 (숫자 키 모양)

def keycap(c, col, row, tint=None):
    x, y = cell_x(col), cell_y(row)
    fill = (40, 44, 78) if not tint else tint
    c.rect(x, y, x + 17, y + 17, OUTLINE)
    c.rect(x + 1, y + 1, x + 16, y + 16, tuple(int(v * 0.6) for v in fill))
    c.rect(x + 1, y + 1, x + 16, y + 14, fill)
    c.rect(x + 2, y + 1, x + 15, y + 1, tuple(min(255, int(v * 1.6) + 10) for v in fill))
    c.rect(x + 1, y + 2, x + 1, y + 13, tuple(min(255, int(v * 1.3) + 6) for v in fill))


def menu_skills():
    """룬 줄 (줄 2 · 칸 0 ~ 7 — 아래 숫자 1 ~ 8) · 빼기 (칸 8), 직업 줄 (줄 4 · 칸 0 ~ 4 — 아래 1 ~ 5) · 도움말 (칸 8).
    머리 글 (Java): 줄 1 (d 35) 룬 · 줄 3 아래 (d 75) 직업 — 앞에 색 보석"""
    c = frame("skills")
    f7 = font("Galmuri7.ttf", 8)
    for i in range(8):
        keycap(c, i, 2, tint=(34, 58, 74))
        c.text(mid_x(i) - 1, cell_y(3), str(i + 1), f7, (150, 220, 230), center=True)
    button(c, cell_x(8) + 1, cell_y(2) + 1, cell_x(8) + 16, cell_y(2) + 16, tint=(160, 50, 50))
    icon(c, art.QUICK["remove"], cell_x(8) + 3, cell_y(2) + 3, GOLD_HI)
    for i in range(5):
        keycap(c, i, 4, tint=(70, 40, 80))
        c.text(mid_x(i) - 1, cell_y(5) + 1, str(i + 1), f7, (230, 170, 240), center=True)
    button(c, cell_x(8) + 1, cell_y(4) + 1, cell_x(8) + 16, cell_y(4) + 16, tint=(50, 90, 150))
    icon(c, art.QUICK["help"], cell_x(8) + 3, cell_y(4) + 3, GOLD_HI)
    # 머리 글 앞 보석 (룬 = 줄 1 왼쪽, 직업 = 줄 4 오른쪽 — 글은 Java: d 35 · d 89 / 103)
    for (x, y, col) in ((6, cell_y(1) + 7, (110, 220, 230)), (cell_x(5) + 2, cell_y(4) + 7, (230, 150, 240))):
        c.put(x, y, col)
        c.put(x - 1, y + 1, col)
        c.put(x, y + 1, WHITE)
        c.put(x + 1, y + 1, col)
        c.put(x, y + 2, col)
    # 룬 줄 위 · 직업 줄 옆 가는 줄
    c.rect(cell_x(0) + 2, cell_y(2) - 2, cell_x(8) + 15, cell_y(2) - 2, (40, 70, 90))
    return c.final()


# ------------------------------------------------------------------ 스탯 트리 (위로 자라는 나무 — 줄기 칸 3, 칸 2 · 4 에 칸 열 개)

TREE_NODES = [(5, 2), (5, 4), (4, 2), (4, 4), (3, 2), (3, 4), (2, 2), (2, 4), (1, 2), (1, 4)]  # (줄, 칸) — 1 ~ 10
TREE_FORKS = {2, 5, 8}


def menu_tree():
    c = frame("stats")
    tx = mid_x(3)
    # 잎 빛 (위) · 줄기 · 뿌리
    glow(c, tx, cell_y(1) + 14, 44, (50, 120, 100), 0.7)
    for y in range(cell_y(1) + 4, 126):
        w = 3 if y < cell_y(4) else 4 if y < cell_y(5) + 8 else 5 + (y - cell_y(5) - 8) // 2
        for x in range(tx - w, tx + w):
            edge = x == tx - w or x == tx + w - 1
            c.put(x, y, (70, 52, 30) if edge else ((120, 86, 50) if x < tx else (96, 68, 40)))
    # 가지 (칸 2 · 4 의 가운데로)
    for i, (r, col) in enumerate(TREE_NODES):
        y = mid_y(r)
        x_from, x_to = (tx - 4, mid_x(col) + 6) if col < 3 else (tx + 3, mid_x(col) - 7)
        for x in range(min(x_from, x_to), max(x_from, x_to) + 1):
            c.put(x, y, (120, 86, 50))
            c.put(x, y + 1, (86, 60, 34))
    for i, (r, col) in enumerate(TREE_NODES):
        node_socket(c, col, r, color=GOLD if i in TREE_FORKS else GOLD_LO, fork=i in TREE_FORKS)
    # 왼쪽 스탯 탭 (칸 0 · 줄 1 ~ 4) · 초기화 (칸 0 · 줄 5)
    for r in range(1, 5):
        node_socket(c, 0, r, color=GOLD_DK)
    button(c, cell_x(0) + 1, cell_y(5) + 1, cell_x(0) + 16, cell_y(5) + 16, tint=(150, 50, 50))
    icon(c, art.QUICK["remove"], cell_x(0) + 3, cell_y(5) + 3, GOLD_HI)
    # 오른쪽 판 (칸 5 ~ 8 · 줄 1 ~ 5): 머리 줄 · 갈래 둘 (그림 칸 5 + 이름) · 글 두 줄 · 합계 (칸 8 · 줄 5)
    px0, px1 = cell_x(5) + 2, cell_x(8) + 17
    thin_frame(c, px0, cell_y(1) + 1, px1, cell_y(5) + 17, (20, 22, 42), edge=GOLD_LO, r=3)
    c.rect(px0 + 3, cell_y(2) - 1, px1 - 3, cell_y(2) - 1, GOLD_DK)
    for r in (2, 3):
        slot(c, 5, r)
        c.rect(cell_x(6) + 1, cell_y(r) + 2, cell_x(8) + 15, cell_y(r) + 15, (30, 33, 62))
    c.rect(px0 + 3, cell_y(4) - 1, px1 - 3, cell_y(4) - 1, GOLD_DK)
    button(c, cell_x(8) + 1, cell_y(5) + 1, cell_x(8) + 16, cell_y(5) + 16, tint=(60, 120, 80))
    icon(c, art.QUICK["info"], cell_x(8) + 3, cell_y(5) + 3, GOLD_HI)
    return c.final()


# ------------------------------------------------------------------ 룬 뽑기 (마법진 위 결과 10칸 · 1회 / 10회 단추)

def magic_circle(c, cx, cy, r):
    ring(c, cx, cy, r - 1.2, r, GOLD, 1.0)
    ring(c, cx, cy, r - 4.5, r - 3.6, GOLD_MID, 0.9)
    ring(c, cx, cy, r * 0.55 - 0.6, r * 0.55 + 0.4, GOLD_LO, 0.8)
    for k in range(6):  # 육각별
        a0 = math.pi / 2 + k * math.pi / 3
        a1 = a0 + 2 * math.pi / 3
        x0, y0 = cx + (r - 4) * math.cos(a0), cy - (r - 4) * math.sin(a0)
        x1, y1 = cx + (r - 4) * math.cos(a1), cy - (r - 4) * math.sin(a1)
        n = int(max(abs(x1 - x0), abs(y1 - y0)))
        for t in range(n + 1):
            c.blend(int(x0 + (x1 - x0) * t / n), int(y0 + (y1 - y0) * t / n), GOLD_MID, 0.75)
    for k in range(16):  # 룬 점
        a = k * math.pi / 8
        c.blend(int(cx + (r - 2.4) * math.cos(a)), int(cy + (r - 2.4) * math.sin(a)), GOLD_HI, 0.9)


def menu_gacha():
    c = frame("home")
    f9 = font("Galmuri9.ttf", 10)
    # 마법진 + 빛 (결과 10칸 뒤 — 줄 2 ~ 3 · 칸 2 ~ 6)
    cx, cy = mid_x(4), cell_y(3) + 1
    glow(c, cx, cy, 54, (110, 70, 170), 0.6)
    magic_circle(c, cx, cy, 38)
    # 뽑기 종류 줄 (줄 1 · 칸 0 ~ 6 — 어두운 띠 위) · 확률 안내 (칸 8)
    thin_frame(c, cell_x(0) - 2, cell_y(1) - 1, cell_x(6) + 19, cell_y(1) + 18, (18, 20, 38), edge=GOLD_DK, r=2)
    for col in range(7):
        slot(c, col, 1)
    button(c, cell_x(8) + 1, cell_y(1) + 1, cell_x(8) + 16, cell_y(1) + 16, tint=(60, 90, 150))
    icon(c, art.QUICK["info"], cell_x(8) + 3, cell_y(1) + 3, GOLD_HI)
    # 결과 칸 (테두리만 — 마법진이 비침)
    for r in (2, 3):
        for col in range(2, 7):
            x, y = cell_x(col), cell_y(r)
            for k in range(1, 17):
                c.blend(x + k, y + 1, (200, 170, 255), 0.35)
                c.blend(x + k, y + 16, (60, 40, 90), 0.6)
                c.blend(x + 1, y + k, (200, 170, 255), 0.25)
                c.blend(x + 16, y + k, (60, 40, 90), 0.5)
            for k in range(2, 16):
                for j in range(2, 16):
                    c.blend(x + k, y + j, (20, 14, 36), 0.35)
    # 양쪽 수정 (칸 0 ~ 1 · 칸 7 ~ 8)
    for cxp in (cell_x(1) + 1, cell_x(7) + 16):
        crystal(c, cxp, cell_y(3) + 4)
    # 단추 (줄 5 · 칸 0 ~ 3 = 1회, 칸 5 ~ 8 = 10회)
    for (c0, txt, tint) in ((0, "1회 뽑기", (96, 64, 160)), (5, "10회 뽑기", (190, 120, 40))):
        x0, x1 = cell_x(c0) + 1, cell_x(c0 + 3) + 16
        button(c, x0, cell_y(5) + 1, x1, cell_y(5) + 16, tint=tint)
        tw = text_width(txt, f9)
        sx = (x0 + x1) // 2 - (tw + 16) // 2
        icon(c, art.QUICK["ticket"], sx, cell_y(5) + 3, GOLD_HI)
        label(c, sx + 15, cell_y(5) + 4, txt, f9, CREAM)
    return c.final()


def crystal(c, cx, base):
    """보라 수정 (아래 받침 + 길쭉한 결정 셋)"""
    glow(c, cx, base - 12, 14, (170, 120, 255), 0.5)
    for (dx, h, w) in ((0, 22, 3), (-4, 13, 2), (4, 15, 2)):
        for k in range(h):
            yy = base - k
            ww = w if k < h - w else max(0, h - 1 - k)
            for x in range(cx + dx - ww, cx + dx + ww + 1):
                col = (200, 170, 255) if x < cx + dx else ((140, 100, 220) if x == cx + dx else (96, 64, 170))
                c.put(x, yy, col)
            c.put(cx + dx - ww - 1, yy, OUTLINE)
            c.put(cx + dx + ww + 1, yy, OUTLINE)
        c.put(cx + dx, base - h, OUTLINE)
    c.rect(cx - 7, base + 1, cx + 7, base + 2, GOLD_LO)
    c.rect(cx - 7, base + 3, cx + 7, base + 3, GOLD_DK)


# ------------------------------------------------------------------ 아이템 그림 · 설명 상자

def tooltip_sprites():
    """설명 상자 (nine_slice 32 × 32, 테두리 9): 남색 바탕 + 금 테 (모서리 깎음).
    바닐라는 이 그림을 글자 둘레 12px 바깥까지 늘려 그림 (여백 3 + 9) — 보이는 상자는 그림 안쪽 8px 부터 (글자 둘레 4px)"""
    bg = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    fr = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    m = rounded(16, 16, 2)
    edge = m & ~erode(m)
    inner = erode(m) & ~erode(erode(m))
    for y in range(16):
        for x in range(16):
            if m[y, x]:
                bg.putpixel((x + 8, y + 8), (16, 18, 34, 242))
            if edge[y, x]:
                fr.putpixel((x + 8, y + 8), GOLD_MID + (255,))
            elif inner[y, x]:
                fr.putpixel((x + 8, y + 8), (70, 52, 22, 255))
    return bg, fr


def item_icons():
    """칸 아이템 그림 (rpg:vui/<이름>): 빈 그림 · 스탯 트리 보너스 종류 · 갈래 고르기 · 카드 뒷면 · 정보"""
    out = {"blank": Image.new("RGBA", (16, 16), (0, 0, 0, 0))}
    for k, im in art.kind_icons().items():
        out["kind_" + k.lower()] = im  # 리소스 경로는 소문자만 (critDmg → kind_critdmg)
    out["fork"] = art.fork_icon()
    out["card_back"] = art.card_icon()
    info = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    info.alpha_composite(art.art(art.QUICK["info"], one=GOLD_HI), (2, 2))
    out["info"] = info
    return out


PAGES = [("menu_home", menu_home), ("menu_stats", menu_stats), ("menu_equip", menu_equip), ("menu_skills", menu_skills),
         ("menu_tree", menu_tree), ("menu_gacha", menu_gacha)]


def main():
    global FONT_DIR
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    FONT_DIR = os.path.dirname(os.path.abspath(sys.argv[1]))
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "tooltip"), exist_ok=True)
    sheet, lines, widths = text_sheet(sys.argv[1])
    sheet.save(os.path.join(OUT, "text.png"))
    json.dump(lines, open(os.path.join(OUT, "text_chars.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump({"space": 4, "default": 6, "widths": widths}, open(os.path.join(OUT, "text_widths.json"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    nsheet, nwidths = num_sheet(os.path.join(FONT_DIR, "Galmuri7.ttf"))
    nsheet.save(os.path.join(OUT, "num.png"))
    json.dump({"chars": NUM_CHARS, "space": 3, "widths": nwidths}, open(os.path.join(OUT, "num_widths.json"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    for name, fn in PAGES:
        fn().save(os.path.join(OUT, name + ".png"))
    keep = {n + ".png" for n, _ in PAGES} | {"text.png", "num.png"}
    for f in os.listdir(OUT):
        if f.endswith(".png") and f not in keep:
            os.remove(os.path.join(OUT, f))
    for f in os.listdir(os.path.join(OUT, "icons")):
        os.remove(os.path.join(OUT, "icons", f))
    for name, im in item_icons().items():
        im.save(os.path.join(OUT, "icons", name + ".png"))
    bg, fr = tooltip_sprites()
    bg.save(os.path.join(OUT, "tooltip", "menu_background.png"))
    fr.save(os.path.join(OUT, "tooltip", "menu_frame.png"))
    print("glyphs", len(widths), "sheet", sheet.size, "rows", len(lines))


if __name__ == "__main__":
    main()
