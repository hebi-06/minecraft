#!/usr/bin/env python3
"""2026-11-16 메뉴 아이콘 (사용자 "바닐라 아이템은 다 빼버려 다 빼고 너가 직접 그려서 다른 걸로 넣어"): 창 · 선택지에 쓰던 바닐라 아이템 그림 (네더의 별 · 경험치 병 ·
시계 · 나침반 · 책 · 상자 · 철 검 · 방패 …) 대신 직접 그린 32 × 32 도트 아이콘 (재료 아이템 그림과 같은 결 — 짙은 외곽선 · 왼쪽 위 밝은 테 · 오른쪽 아래 그늘 ·
몇 단 명암 · 반짝임). 서버 (ui.UiIcons) 가 바닐라 아이템 칸을 이 아이콘 (종이 + item_model rpg:icon/<이름>) 으로 바꿈.

  python3 tools/ui_icons.py                 # 씀 (assets/rpg/textures/item/icon/<이름>.png · models/item/icon/<이름>.json · items/icon/<이름>.json)
  python3 tools/ui_icons.py --check
  python3 tools/ui_icons.py --preview <파일.png>
"""
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
N = 32
OUTLINE = (27, 20, 32)

# 램프: (그늘, 가운데, 밝음, 하이라이트)
P = {
    "steel": [(70, 82, 104), (132, 148, 172), (196, 208, 226), (244, 248, 255)],
    "iron": [(78, 78, 88), (138, 138, 150), (190, 190, 202), (236, 236, 244)],
    "gold": [(150, 92, 18), (220, 156, 36), (255, 210, 90), (255, 246, 200)],
    "wood": [(92, 54, 28), (140, 88, 46), (182, 124, 70), (214, 164, 104)],
    "leather": [(86, 46, 30), (134, 74, 44), (176, 108, 66), (208, 146, 98)],
    "paper": [(176, 150, 112), (220, 200, 160), (240, 228, 196), (255, 250, 232)],
    "red": [(120, 20, 30), (196, 40, 48), (238, 88, 80), (255, 168, 150)],
    "violet": [(70, 32, 132), (124, 70, 214), (178, 140, 255), (236, 222, 255)],
    "blue": [(28, 52, 132), (52, 102, 214), (110, 168, 255), (214, 236, 255)],
    "teal": [(18, 92, 96), (34, 150, 150), (92, 214, 204), (210, 255, 246)],
    "green": [(20, 96, 52), (40, 160, 84), (104, 216, 128), (214, 255, 220)],
    "grey": [(64, 66, 78), (110, 114, 128), (160, 164, 178), (214, 218, 230)],
    "white": [(170, 186, 210), (214, 226, 244), (240, 246, 255), (255, 255, 255)],
    "orange": [(150, 62, 12), (222, 112, 26), (255, 168, 64), (255, 230, 170)],
    "slate": [(36, 40, 58), (58, 64, 92), (88, 98, 136), (140, 152, 196)],
}


# ------------------------------------------------------------------ 모양

def grid():
    y, x = np.mgrid[0:N, 0:N]
    return x + 0.5, y + 0.5


def poly(pts):
    x, y = grid()
    inside = np.zeros((N, N), bool)
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        cond = (y1 > y) != (y2 > y)
        xi = (x2 - x1) * (y - y1) / ((y2 - y1) if y2 != y1 else 1e-9) + x1
        inside ^= cond & (x < xi)
    return inside


def circle(cx, cy, r):
    x, y = grid()
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ring(cx, cy, r0, r1):
    x, y = grid()
    d = (x - cx) ** 2 + (y - cy) ** 2
    return (d <= r1 * r1) & (d >= r0 * r0)


def rect(x0, y0, x1, y1):
    x, y = grid()
    return (x >= x0) & (x < x1) & (y >= y0) & (y < y1)


def line(x0, y0, x1, y1, w=1.0):
    x, y = grid()
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1)
    d = np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))
    return d <= w / 2


def star(cx, cy, ro, ri, n=4, rot=-math.pi / 2):
    pts = []
    for i in range(2 * n):
        r = ro if i % 2 == 0 else ri
        a = rot + i * math.pi / n
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return poly(pts)


# ------------------------------------------------------------------그리기

class Icon:
    def __init__(self):
        self.rgba = np.zeros((N, N, 4), np.uint8)
        self.solid = np.zeros((N, N), bool)

    def layer(self, mask, ramp, grad=True, rim=True):
        """mask 를 램프로 칠함: 왼쪽 위 가장자리 밝음 · 오른쪽 아래 가장자리 그늘 · 위가 조금 밝은 단"""
        ramp = P[ramp] if isinstance(ramp, str) else ramp
        m = mask
        up = np.zeros_like(m)
        up[1:] = m[:-1]
        left = np.zeros_like(m)
        left[:, 1:] = m[:, :-1]
        down = np.zeros_like(m)
        down[:-1] = m[1:]
        right = np.zeros_like(m)
        right[:, :-1] = m[:, 1:]
        ys = np.nonzero(m.any(1))[0]
        y0, y1 = (ys.min(), ys.max()) if len(ys) else (0, N - 1)
        yy = np.mgrid[0:N, 0:N][0]
        t = (yy - y0) / max(1, y1 - y0)
        idx = np.where(t < 0.4, 2, 1) if grad else np.full((N, N), 1)
        if rim:
            idx = np.where(m & (~up | ~left), 3 if grad else 2, idx)
            idx = np.where(m & (~down | ~right) & ~(~up | ~left), 0, idx)
        for k in range(4):
            sel = m & (idx == k)
            self.rgba[sel, :3] = ramp[k]
            self.rgba[sel, 3] = 255
        self.solid |= m
        return self

    def fill(self, mask, color):
        self.rgba[mask, :3] = color
        self.rgba[mask, 3] = 255
        self.solid |= mask
        return self

    def px(self, pts, color):
        for (x, y) in pts:
            if 0 <= x < N and 0 <= y < N:
                self.rgba[y, x] = (*color, 255)
                self.solid[y, x] = True
        return self

    def outline(self):
        m = self.solid
        o = np.zeros_like(m)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                sh = np.zeros_like(m)
                ys = slice(max(0, dy), N + min(0, dy))
                yd = slice(max(0, -dy), N + min(0, -dy))
                xs = slice(max(0, dx), N + min(0, dx))
                xd = slice(max(0, -dx), N + min(0, -dx))
                sh[yd, xd] = m[ys, xs]
                o |= sh
        o &= ~m
        self.rgba[o, :3] = OUTLINE
        self.rgba[o, 3] = 255
        return self

    def sparkle(self, x, y, big=False, color=(255, 255, 240)):
        pts = [(x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)]
        if big:
            pts += [(x - 2, y), (x + 2, y), (x, y - 2), (x, y + 2)]
        for (px, py) in pts:
            if 0 <= px < N and 0 <= py < N:
                self.rgba[py, px] = (*color, 255)
        return self

    def image(self):
        return Image.fromarray(self.rgba, "RGBA")


# ------------------------------------------------------------------ 아이콘

def breakstar():
    """돌파의 별 (네더의 별 대신): 보랏빛 여덟 갈래 결정 별 · 흰 심 · 반짝임"""
    I = Icon()
    I.layer(star(16, 16, 14, 5.5, 4, rot=-math.pi / 4) & ~circle(16, 16, 0), "violet")
    I.layer(star(16, 16, 13.5, 4.5, 4), "violet")
    I.layer(star(16, 16, 7, 3, 4), "white")
    I.fill(circle(16, 16, 1.6), (255, 255, 255))
    I.outline().sparkle(27, 5, big=True).sparkle(5, 26)
    return I


def level_badge():
    """레벨 증표 (경험치 병 대신): 초록 방패 꼴 증표 · 금빛 위 화살 둘"""
    I = Icon()
    I.layer(poly([(5, 4), (27, 4), (27, 17), (16, 29), (5, 17)]), "green")
    I.layer(poly([(7, 6), (25, 6), (25, 16.5), (16, 26.5), (7, 16.5)]), "teal", rim=False)
    I.layer(poly([(16, 8), (23, 15), (20, 15), (16, 11), (12, 15), (9, 15)]), "gold")
    I.layer(poly([(16, 14), (23, 21), (20, 21), (16, 17), (12, 21), (9, 21)]), "gold")
    I.outline().sparkle(27, 3)
    return I


def person():
    """사람 (플레이어 머리 대신): 둥근 테 안의 사람 그림자"""
    I = Icon()
    I.layer(circle(16, 16, 14.5), "slate")
    I.layer(circle(16, 12, 5.2), "white")
    I.layer(poly([(6, 28), (8, 21), (12, 18.5), (20, 18.5), (24, 21), (26, 28)]) & circle(16, 16, 13.5), "white")
    I.outline()
    return I


def hourglass():
    """모래시계 (시계 대신): 나무 틀 · 유리 · 금빛 모래"""
    I = Icon()
    I.layer(rect(6, 3, 26, 7), "wood")
    I.layer(rect(6, 25, 26, 29), "wood")
    glass = poly([(9, 7), (23, 7), (17.5, 16), (23, 25), (9, 25), (14.5, 16)])
    I.layer(glass, "white", grad=False)
    I.layer(glass & rect(0, 19, 32, 25), "gold")
    I.layer(glass & rect(0, 8, 32, 11), "gold")
    I.fill(line(16, 12, 16, 19, 1.0), P["gold"][2])
    I.layer(rect(7, 7, 9, 25), "wood")
    I.layer(rect(23, 7, 25, 25), "wood")
    I.outline()
    return I


def sort():
    """정렬 (깔때기 대신): 길이가 줄어드는 막대 셋 · 아래 화살"""
    I = Icon()
    I.layer(rect(4, 5, 20, 9), "steel")
    I.layer(rect(4, 13, 16, 17), "steel")
    I.layer(rect(4, 21, 12, 25), "steel")
    I.layer(rect(23, 4, 27, 20), "gold")
    I.layer(poly([(19, 19), (31, 19), (25, 28)]), "gold")
    I.outline()
    return I


def compass_rose():
    """길잡이 별 (나침반 대신): 금 테 둥근 판 · 네 갈래 바늘 (북 = 빨강)"""
    I = Icon()
    I.layer(circle(16, 16, 14), "gold")
    I.layer(circle(16, 16, 11.5), "slate", rim=False)
    I.layer(poly([(16, 4.5), (18.5, 16), (13.5, 16)]), "red")
    I.layer(poly([(16, 27.5), (18.5, 16), (13.5, 16)]), "white")
    I.layer(poly([(4.5, 16), (16, 14.2), (16, 17.8)]) | poly([(27.5, 16), (16, 14.2), (16, 17.8)]), "steel")
    I.fill(circle(16, 16, 1.5), P["gold"][3])
    I.outline()
    return I


def tome(cover="leather", rune=None, glow=False):
    I = Icon()
    I.layer(poly([(6, 5), (24, 3), (27, 26), (9, 29)]), cover)
    I.layer(poly([(9, 27), (26, 24.5), (27, 26), (9, 29)]), "paper", rim=False)
    I.layer(poly([(21, 3.4), (24, 3), (24.6, 8), (22.8, 7), (21.6, 8.4)]), "gold")
    if rune is not None:
        I.layer(rune, "gold" if not glow else "white")
    I.outline()
    if glow:
        I.sparkle(27, 4, big=True).sparkle(4, 25)
    return I


def guide_book():
    """안내서 (책 대신): 갈색 가죽 책 · 금 책갈피 · 금 마름모 새김"""
    return tome("leather", poly([(16, 10), (20, 15), (16, 20), (12, 15)]))


def spell_tome():
    """마법서 (마법이 부여된 책 대신): 보랏빛 책 · 빛나는 룬"""
    rune = line(16, 9, 16, 21, 2) | line(12, 12, 20, 12, 2) | line(13, 18, 19, 18, 2)
    return tome("violet", rune, glow=True)


def quill_scroll():
    """깃펜과 두루마리 (책과 깃펜 대신)"""
    I = Icon()
    I.layer(rect(5, 9, 22, 27), "paper")
    I.layer(rect(4, 7, 23, 10), "wood")
    I.layer(rect(4, 26, 23, 29), "wood")
    for yy in (13, 16, 19, 22):
        I.fill(rect(8, yy, 19, yy + 1), P["leather"][1])
    I.layer(poly([(28, 2), (30, 4), (18, 21), (16, 19)]), "white")
    I.fill(line(17, 20, 14, 24, 1.2), OUTLINE)
    I.outline()
    return I


def price_tag():
    """가격표 (이름표 대신): 끈 달린 꼬리표 · 금 동전 새김"""
    I = Icon()
    I.layer(poly([(10, 6), (28, 6), (28, 24), (10, 24), (4, 15)]), "paper")
    I.fill(circle(9, 15, 1.6), OUTLINE)
    I.layer(circle(19, 15, 5.2), "gold")
    I.fill(rect(18, 12, 20, 18), P["gold"][0])
    I.fill(line(2, 8, 8, 14, 1.2), P["red"][1])
    I.outline()
    return I


def crate():
    """나무 궤짝 (상자 대신): 판자 · 쇠 띠 · 가운데 자물쇠"""
    I = Icon()
    I.layer(rect(3, 8, 29, 28), "wood")
    for yy in (13, 18, 23):
        I.fill(rect(4, yy, 28, yy + 1), P["wood"][0])
    I.layer(rect(3, 7, 29, 11), "iron")
    I.layer(rect(3, 25, 29, 28), "iron")
    I.layer(rect(6, 7, 9, 28), "iron")
    I.layer(rect(23, 7, 26, 28), "iron")
    I.layer(rect(13, 14, 19, 21), "gold")
    I.fill(rect(15, 16, 17, 19), P["gold"][0])
    I.outline()
    return I


def strongbox():
    """보관함 (나무통 대신): 쇠 금고 · 둥근 손잡이"""
    I = Icon()
    I.layer(rect(4, 5, 28, 28), "slate")
    I.layer(rect(7, 8, 25, 25), "steel", rim=False)
    I.layer(ring(16, 16.5, 3.2, 5.6), "gold")
    I.fill(line(16, 16.5, 19, 13.5, 1.4), P["gold"][1])
    I.layer(rect(9, 28, 12, 30), "slate")
    I.layer(rect(20, 28, 23, 30), "slate")
    I.outline()
    return I


def deny():
    """막힘 (방벽 대신): 빨간 원 · 빗금"""
    I = Icon()
    I.layer(ring(16, 16, 9.5, 14), "red")
    I.layer(line(8, 24, 24, 8, 4.4) & circle(16, 16, 11), "red")
    I.outline()
    return I


def smith_hammer():
    """대장장이 망치 (모루 대신): 쇠 망치 머리 · 나무 자루 · 불꽃"""
    I = Icon()
    I.layer(line(9, 27, 21, 11, 3.2), "wood")
    I.layer(poly([(13, 6), (26, 15), (23, 20), (10, 11)]), "iron")
    I.layer(poly([(10, 11), (13, 6), (11, 5), (8, 9)]), "steel")
    I.outline().sparkle(27, 24, color=(255, 214, 120)).sparkle(23, 28, color=(255, 170, 70)).sparkle(29, 19, color=(255, 236, 170))
    return I


def sword_emblem():
    """검 (철 검 대신): 비스듬한 은빛 날 · 금 코등이 · 푸른 보석"""
    I = Icon()
    I.layer(poly([(27, 3), (29, 5), (14, 20), (12, 18)]), "white")
    I.fill(line(27.5, 4.5, 14, 18.5, 0.8), P["white"][3])
    I.layer(line(8, 15, 17, 24, 3.2), "gold")
    I.layer(line(5, 27, 12, 20, 3), "leather")
    I.layer(circle(4.5, 27.5, 2.4), "gold")
    I.fill(circle(12.6, 19.4, 1.3), P["blue"][2])
    I.outline()
    return I


def armor_emblem():
    """갑옷 (철 흉갑 대신): 강철 흉갑 · 금 테 · 가운데 문장"""
    I = Icon()
    body = poly([(5, 6), (11, 4), (16, 7), (21, 4), (27, 6), (25, 15), (24, 27), (8, 27), (7, 15)])
    I.layer(body, "steel")
    I.layer(body & ~poly([(7, 7.5), (11, 6), (16, 9), (21, 6), (25, 7.5), (23.5, 15), (22.5, 25.5), (9.5, 25.5), (8.5, 15)]), "gold")
    I.layer(poly([(16, 12), (20, 15), (16, 21), (12, 15)]), "blue")
    I.outline()
    return I


def loot_sack():
    """전리품 자루 (꾸러미 대신): 묶은 자루 · 금 동전"""
    I = Icon()
    I.layer(poly([(11, 11), (21, 11), (27, 19), (25, 27), (7, 27), (5, 19)]), "leather")
    I.layer(poly([(12, 4), (20, 4), (18, 9), (14, 9)]), "leather")
    I.layer(rect(11, 9, 21, 12), "red")
    I.layer(circle(24, 24, 4.5), "gold")
    I.fill(rect(23, 22, 25, 26), P["gold"][0])
    I.outline()
    return I


def gem():
    """보석 (에메랄드 대신): 깎은 초록 보석"""
    I = Icon()
    I.layer(poly([(10, 5), (22, 5), (28, 12), (16, 28), (4, 12)]), "green")
    I.layer(poly([(10, 5), (22, 5), (19, 12), (13, 12)]), "teal")
    I.layer(poly([(13, 12), (19, 12), (16, 26)]), "green", rim=False)
    I.outline().sparkle(9, 9)
    return I


def metal_bar():
    """금속 주괴 (철 주괴 대신): 사다리꼴 강철 막대 · 룬 새김"""
    I = Icon()
    I.layer(poly([(4, 22), (10, 12), (28, 12), (24, 22)]), "steel")
    I.layer(poly([(4, 22), (24, 22), (24, 26), (4, 26)]), "slate")
    I.layer(poly([(24, 22), (28, 12), (28, 16), (24, 26)]), "slate", rim=False)
    I.fill(line(13, 17, 19, 17, 1.2) | line(16, 14, 16, 20, 1.2), P["blue"][2])
    I.outline()
    return I


def crosshair():
    """과녁 (치명타 확률 — 과녁 블록 대신): 빨강 · 흰 고리 · 십자"""
    I = Icon()
    I.layer(circle(16, 16, 13), "red")
    I.layer(circle(16, 16, 9.5), "white", rim=False)
    I.layer(circle(16, 16, 6), "red", rim=False)
    I.layer(circle(16, 16, 2.6), "white", rim=False)
    I.fill(rect(15, 1, 17, 8) | rect(15, 24, 17, 31) | rect(1, 15, 8, 17) | rect(24, 15, 31, 17), OUTLINE)
    I.outline()
    return I


def crit_burst():
    """치명 폭발 (치명타 피해 — 철퇴 대신): 주황 · 노랑 뾰족 폭발"""
    I = Icon()
    I.layer(star(16, 16, 15, 7, 8, rot=-math.pi / 8), "orange")
    I.layer(star(16, 16, 9, 4.5, 8), "gold")
    I.fill(circle(16, 16, 2.2), (255, 255, 230))
    I.outline()
    return I


def life_fruit():
    """생명의 열매 (최대 체력 — 황금 사과 대신): 하트 꼴 붉은 열매 · 잎 · 금빛 반짝임"""
    I = Icon()
    heart = circle(11, 14, 6.5) | circle(21, 14, 6.5) | poly([(4.8, 16), (27.2, 16), (16, 28.5)])
    I.layer(heart, "red")
    I.layer(poly([(16, 8), (22, 2), (25, 4), (18, 9)]), "green")
    I.outline().sparkle(10, 11, color=(255, 236, 200)).sparkle(27, 9, big=True, color=(255, 230, 140))
    return I


def shield_emblem():
    """방패 (받는 피해 — 방패 대신): 강철 테 · 푸른 판 · 금 십자"""
    I = Icon()
    sh = poly([(5, 4), (27, 4), (27, 15), (16, 29), (5, 15)])
    I.layer(sh, "steel")
    I.layer(poly([(8, 7), (24, 7), (24, 14.5), (16, 25.5), (8, 14.5)]), "blue")
    I.layer(rect(14.5, 8, 17.5, 23) | rect(10, 12, 22, 15), "gold")
    I.outline()
    return I


def wind_feather():
    """바람 깃털 (이동 속도 — 깃털 대신): 흰 · 하늘빛 깃털 · 바람 줄"""
    I = Icon()
    I.layer(poly([(27, 3), (29, 6), (12, 24), (9, 22)]) | poly([(26, 4), (20, 6), (12, 13), (9, 21), (14, 19), (22, 13)]), "white")
    I.fill(line(27, 5, 8, 25, 1.0), P["blue"][1])
    I.fill(line(3, 27, 9, 27, 1.2) | line(5, 30, 13, 30, 1.2), P["teal"][2])
    I.outline()
    return I


def sprout():
    """새싹 (스탯 트리 뿌리 — 참나무 묘목 대신): 흙 언덕 · 두 잎 새싹"""
    I = Icon()
    I.layer(poly([(4, 28), (8, 22), (24, 22), (28, 28)]), "leather")
    I.layer(rect(15, 12, 17, 23), "green")
    I.layer(poly([(16, 13), (9, 7), (5, 10), (10, 15)]), "green")
    I.layer(poly([(16, 11), (23, 4), (28, 7), (22, 13)]), "green")
    I.outline().sparkle(26, 17, color=(220, 255, 210))
    return I


def trash():
    """버리기 (용암 양동이 대신): 쇠 휴지통 · 뚜껑"""
    I = Icon()
    I.layer(poly([(7, 10), (25, 10), (23, 29), (9, 29)]), "iron")
    for xx in (12, 16, 20):
        I.fill(rect(xx, 13, xx + 1, 26), P["iron"][0])
    I.layer(rect(5, 6, 27, 10), "steel")
    I.layer(rect(13, 3, 19, 6), "steel")
    I.outline()
    return I


def unknown():
    """모르는 그림 (그 밖 바닐라 아이템): 둥근 판 · 물음표"""
    I = Icon()
    I.layer(circle(16, 16, 14), "slate")
    q = (ring(16, 12, 3.5, 6.5) & ~rect(0, 13, 13.5, 32)) | rect(14.5, 16, 17.5, 21) | rect(14.5, 23, 17.5, 26)
    I.layer(q & ~rect(0, 12, 13, 17), "gold")
    I.outline()
    return I


ICONS = {
    "breakstar": breakstar, "level_badge": level_badge, "person": person, "hourglass": hourglass, "sort": sort, "compass_rose": compass_rose,
    "guide_book": guide_book, "spell_tome": spell_tome, "quill_scroll": quill_scroll, "price_tag": price_tag, "crate": crate, "strongbox": strongbox,
    "deny": deny, "smith_hammer": smith_hammer, "sword_emblem": sword_emblem, "armor_emblem": armor_emblem, "loot_sack": loot_sack, "gem": gem,
    "metal_bar": metal_bar, "crosshair": crosshair, "crit_burst": crit_burst, "life_fruit": life_fruit, "shield_emblem": shield_emblem,
    "wind_feather": wind_feather, "sprout": sprout, "trash": trash, "unknown": unknown,
}


def outputs():
    files = {}
    for name, fn in ICONS.items():
        buf = io.BytesIO()
        fn().image().save(buf, "PNG", optimize=True)
        files[os.path.join(RES, "textures", "item", "icon", name + ".png")] = buf.getvalue()
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": "rpg:item/icon/" + name}}
        files[os.path.join(RES, "models", "item", "icon", name + ".json")] = (json.dumps(model, indent=1) + "\n").encode()
        files[os.path.join(RES, "items", "icon", name + ".json")] = (json.dumps({"model": {"type": "minecraft:model", "model": "rpg:item/icon/" + name}}, indent=2)
                                                                    + "\n").encode()
    # 빈 칸 (그림 없는 칸 — 예전엔 맨 종이 그림이 보였음)
    files[os.path.join(RES, "items", "icon", "blank.json")] = (json.dumps({"model": {"type": "minecraft:empty"}}, indent=2) + "\n").encode()
    return files


def preview(path):
    ims = [fn().image().resize((128, 128), Image.NEAREST) for fn in ICONS.values()]
    cols = 9
    rows = (len(ims) + cols - 1) // cols
    out = Image.new("RGBA", (cols * 136, rows * 136), (52, 54, 64, 255))
    for i, im in enumerate(ims):
        out.alpha_composite(im, ((i % cols) * 136 + 4, (i // cols) * 136 + 4))
    out.save(path)


def main():
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
        return 0
    check = "--check" in sys.argv
    stale = []
    for path, data in outputs().items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            stale.append(os.path.relpath(path, ROOT))
            if not check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if check:
        if stale:
            print("ui_icons: 다시 만들어야 함:", ", ".join(stale))
            return 1
        print("ui_icons: ok (%d)" % len(ICONS))
        return 0
    print("ui_icons: %d 아이콘 · 씀 %d" % (len(ICONS), len(stale)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
