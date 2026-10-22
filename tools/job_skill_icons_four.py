#!/usr/bin/env python3
"""0.30.0 직업 스킬 아이콘 — 성기사 · 사제 · 수호기사 (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음)."""
import math
import os
import sys

from PIL import ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (SS, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix, poly, ring,  # noqa: E402
                             stack, star)

N = 32
DARK = (0, 0, 0)
WOOD = (150, 96, 54)
WOOD_LO = (88, 54, 30)
STONE = (168, 166, 160)
STONE_LO = (96, 94, 92)
STONE_HI = (214, 212, 204)
FLAG = (60, 110, 220)
FLAG_LO = (30, 56, 140)
CLOUD = (70, 64, 104)
CLOUD_LO = (36, 32, 60)


def P(x, y):
    return (x * SS, y * SS)


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def local(cx, cy, ang, pts):
    """(x, y) 를 (cx, cy) 를 가운데로 ang 도 돌려 놓음"""
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]


def zigzag(m, pts, w):
    line(m, pts, w)
    return m


def hammer(cx, cy, ang, head_w=11.0, head_h=6.0, handle=12.0, shaft=1.6):
    """망치: 머리 가운데 (cx, cy) · ang = 자루가 뻗는 쪽 (도 — 0 = 오른쪽, 90 = 아래) — (자루, 머리, 머리 띠) 마스크"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    sh = mask()
    s0 = head_h * 0.45
    s1 = head_h * 0.5 + handle
    line(sh, [(cx + ux * s0, cy + uy * s0), (cx + ux * s1, cy + uy * s1)], shaft)
    disc(sh, cx + ux * (s1 + 0.4), cy + uy * (s1 + 0.4), shaft * 0.85)
    hw, hh, c = head_w / 2, head_h / 2, min(head_w, head_h) * 0.18
    # 머리: 자루와 수직인 긴 쪽 (로컬 x = 자루에 수직 · y = 자루 쪽)
    pts = [(-hw + c, -hh), (hw - c, -hh), (hw, -hh + c), (hw, hh - c), (hw - c, hh), (-hw + c, hh), (-hw, hh - c), (-hw, -hh + c)]
    hd = mask()
    poly(hd, local(cx, cy, ang - 90, pts))
    band = mask()
    for bx in (-hw * 0.42, hw * 0.42):
        line(band, local(cx, cy, ang - 90, [(bx, -hh + 0.3), (bx, hh - 0.3)]), 0.8)
    return sh, hd, band


def hammer_layers(A, B, cx, cy, ang, head_w=11.0, head_h=6.0, handle=12.0, shaft=1.6):
    sh, hd, band = hammer(cx, cy, ang, head_w, head_h, handle, shaft)
    return [layer(sh, WOOD, WOOD_LO, mix(WOOD, WHITE, 0.3), None), layer(hd, mix(STEEL_HI, A, 0.2), mix(STEEL, A, 0.35), WHITE, STEEL_LO, axis=(0.6, 0.8)),
            layer(band, mix(A, WHITE, 0.2), A)], hd


KITE = [(-0.5, -0.36), (-0.3, -0.45), (0.0, -0.5), (0.3, -0.45), (0.5, -0.36), (0.52, -0.08), (0.42, 0.16), (0.24, 0.36), (0.0, 0.5), (-0.24, 0.36),
        (-0.42, 0.16), (-0.52, -0.08)]


def kite(cx, cy, w, h, ang=0.0, k=1.0):
    """연 모양 방패 (위 볼록 · 아래 뾰족): 가운데 (cx, cy) · 너비 w · 높이 h · ang 도 기욺"""
    m = mask()
    poly(m, local(cx, cy, ang, [(x * w * k, y * h * k) for x, y in KITE]))
    return m


def kite_layers(A, B, cx, cy, w, h, ang=0.0):
    outer = kite(cx, cy, w, h, ang)
    inner = kite(cx, cy, w, h, ang, 0.72)
    rim = ImageChops.subtract(outer, inner)
    ridge = mask()
    line(ridge, local(cx, cy, ang, [(0.0, -h * 0.32), (0.0, h * 0.3)]), 1.0)
    gem = mask()
    poly(gem, local(cx, cy, ang, [(0, -h * 0.2), (w * 0.12, -h * 0.07), (0, h * 0.06), (-w * 0.12, -h * 0.07)]))
    return [layer(inner, mix(A, (30, 40, 70), 0.35), mix(A, DARK, 0.45), mix(A, WHITE, 0.35), None, axis=(0.5, 1)),
            layer(rim, mix(B, WHITE, 0.4), mix(A, B, 0.4), WHITE, mix(A, DARK, 0.4)), layer(ridge, mix(A, WHITE, 0.3), A),
            layer(gem, WHITE, mix(B, WHITE, 0.5))], outer


def bolt_pts(x0, y0, x1, y1, n=5, amp=2.2, seed=1):
    """x0, y0 → x1, y1 지그재그 번개"""
    pts = [(x0, y0)]
    for i in range(1, n):
        t = i / n
        side = 1 if (i + seed) % 2 == 0 else -1
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1
        px, py = -dy / L, dx / L
        pts.append((x0 + dx * t + px * amp * side, y0 + dy * t + py * amp * side))
    pts.append((x1, y1))
    return pts


def bolt_mask(x0, y0, x1, y1, w=1.4, n=5, amp=2.2, seed=1):
    m = mask()
    line(m, bolt_pts(x0, y0, x1, y1, n, amp, seed), w)
    return m


def cross(cx, cy, arm, w, up=0.0):
    """십자 (up = 가로대를 위로 — 0 이면 + · 0.3 이면 라틴 십자)"""
    m = mask()
    line(m, [(cx, cy - arm), (cx, cy + arm)], w)
    hy = cy - arm * up
    line(m, [(cx - arm * (1 - up * 0.6), hy), (cx + arm * (1 - up * 0.6), hy)], w)
    return m


def wing(cx, cy, side, span, lift=0.0):
    """날개 하나 (side −1 = 왼쪽): 뿌리 (cx, cy) 에서 바깥 위로 — 위 가장자리는 둥글고 아래는 깃털 끝 넷이 물결"""
    k = span / 14.0
    top = [(0, 0), (3, -4), (7, -8), (11.5, -10.2), (14, -9.4)]
    bottom = [(12.6, -4.2), (11.2, -6.4), (9.6, -1.6), (8.0, -4.4), (6.2, 0.6), (4.8, -2.4), (2.6, 1.8)]
    pts = [(cx + side * x * k, cy + y * k - lift) for x, y in top + bottom]
    m = mask()
    poly(m, pts)
    return m


def wing_lines(cx, cy, side, span):
    """날개 안의 깃털 줄 (어두운 선)"""
    k = span / 14.0
    m = mask()
    for (x0, y0, x1, y1) in ((11.2, -6.4, 10.0, -9.2), (8.0, -4.4, 7.4, -7.4), (4.8, -2.4, 4.4, -5.0)):
        line(m, [(cx + side * x0 * k, cy + y0 * k), (cx + side * x1 * k, cy + y1 * k)], 0.7)
    return m


def bell_mask(cx, top, w, h, ang=0.0):
    """종: 꼭대기 (cx, top) 에 매달림 · 아래 너비 w · 높이 h (ang 도 흔들림) — (몸, 입술 띠, 추)"""
    left = [(-0.2, 0.12), (-0.24, 0.3), (-0.27, 0.52), (-0.36, 0.78), (-0.5, 0.9), (-0.5, 1.0)]
    pts = [(x * w, y * h) for x, y in left] + [(-x * w, y * h) for x, y in left[::-1]]
    body = mask()
    poly(body, local(cx, top, ang, pts))
    loop_ = mask()
    ring(loop_, *local(cx, top, ang, [(0, h * 0.04)])[0], max(1.0, w * 0.1), 0.8)
    lip = mask()
    line(lip, local(cx, top, ang, [(-w * 0.46, h * 0.86), (w * 0.46, h * 0.86)]), 0.9)
    clap = mask()
    cxx, cyy = local(cx, top, ang * 0.6, [(0, h * 1.06)])[0]
    disc(clap, cxx, cyy, max(1.0, w * 0.1))
    return lighter(body, loop_), lip, clap


def note_mask(x, y, kind=0, k=1.0):
    """음표 (머리 가운데 x, y): kind 0 = 8분 · 1 = 이어진 8분 둘"""
    m = mask()
    d = ImageDraw.Draw(m)
    hw, hh = 2.0 * k, 1.5 * k
    if kind == 0:
        d.ellipse([P(x - hw, y - hh), P(x + hw, y + hh)], fill=255)
        line(m, [(x + hw * 0.8, y - 0.2), (x + hw * 0.8, y - 8 * k)], 0.9 * k)
        line(m, [(x + hw * 0.8, y - 8 * k), (x + hw * 0.8 + 2.4 * k, y - 5.6 * k), (x + hw * 0.8 + 2.0 * k, y - 3.6 * k)], 0.9 * k)
    else:
        x2, y2 = x + 6 * k, y - 1.2 * k
        for xx, yy in ((x, y), (x2, y2)):
            d.ellipse([P(xx - hw, yy - hh), P(xx + hw, yy + hh)], fill=255)
            line(m, [(xx + hw * 0.8, yy - 0.2), (xx + hw * 0.8, yy - 7.5 * k)], 0.9 * k)
        poly(m, [(x + hw * 0.8 - 0.4, y - 7.5 * k - 0.9), (x2 + hw * 0.8 + 0.4, y2 - 7.5 * k - 0.9), (x2 + hw * 0.8 + 0.4, y2 - 7.5 * k + 0.9),
                 (x + hw * 0.8 - 0.4, y - 7.5 * k + 0.9)])
    return m


def foot(cx, cy, ang, k=1.0):
    """발자국 하나 (발뒤꿈치 · 앞꿈치 · 사이 · 발가락 다섯 — 발가락이 위)"""
    m = mask()
    for (x, y, r) in ((0.0, 2.6, 1.7), (0.3, -1.2, 2.2)):
        px, py = local(cx, cy, ang, [(x * k, y * k)])[0]
        disc(m, px, py, r * k)
    poly(m, local(cx, cy, ang, [(-1.6 * k, 2.4 * k), (-1.7 * k, -1.0 * k), (2.3 * k, -1.2 * k), (1.6 * k, 2.4 * k)]))
    for (tx, ty, r) in ((-1.9, -4.2, 0.75), (-0.7, -4.9, 0.7), (0.5, -5.1, 0.65), (1.6, -4.8, 0.6), (2.5, -4.0, 0.55)):
        x, y = local(cx, cy, ang, [(tx * k, ty * k)])[0]
        disc(m, x, y, r * k)
    return m


def person(cx, feet_y, h=12.0):
    """작은 사람 (머리 · 몸) — 아군"""
    m = mask()
    r = h * 0.17
    disc(m, cx, feet_y - h + r, r)
    poly(m, [(cx - h * 0.22, feet_y - h + r * 2.3), (cx + h * 0.22, feet_y - h + r * 2.3), (cx + h * 0.28, feet_y), (cx - h * 0.28, feet_y)])
    return m


# ------------------------------------------------------------ 성기사 (빛: 금빛 → 하늘빛)

def paladin_back(A, B):
    return backdrop(mix(B, (4, 8, 26), 0.86), mix(B, (40, 56, 120), 0.5))


def paladin_smite(A, B):
    # 내려친 망치 (머리가 아래왼쪽) · 위오른쪽에서 맞은 자리로 떨어지는 벼락 · 땅의 번쩍 · 부채꼴 충격 줄
    ham, hd = hammer_layers(A, B, 10.5, 21.0, -45, head_w=12.0, head_h=7.0, handle=12.5, shaft=1.8)
    bl = bolt_mask(27.0, 0.5, 15.5, 25.0, 1.6, 5, 2.4)
    hit = mask()
    star(hit, 14.0, 27.0, 6.0, 0.15, 0)
    fan = mask()
    for a in (-160, -125, -55, -20):
        r = math.radians(a)
        line(fan, [(14.0 + math.cos(r) * 7.0, 28.0 + math.sin(r) * 2.2), (14.0 + math.cos(r) * 12.5, 28.0 + math.sin(r) * 3.6)], 0.9)
    ground = mask()
    poly(ground, [(1.0, 28.6), (31.0, 28.6), (31.0, 29.8), (1.0, 29.8)])
    subject = stack(layer(ground, mix(A, (60, 50, 30), 0.6), mix(A, DARK, 0.6)), layer(fan, mix(A, WHITE, 0.4), A), layer(bl, WHITE, mix(B, WHITE, 0.4)), *ham,
                    layer(hit, WHITE, A))
    return finish(subject, [glow(bl, B, 1.8, 1.0), glow(hit, A, 2.0, 1.0)], paladin_back(A, B))


def paladin_charge(A, B):
    # 오른쪽으로 내닫는 연 방패 · 뒤의 금빛 빠른 줄 · 방패 앞 부딪힘 번쩍 · 둘레의 작은 방패 빛 둘
    lines = mask()
    for y, x0, x1 in ((9.0, 1.0, 9.0), (15.5, 2.0, 10.5), (22.0, 1.0, 9.0), (27.5, 3.0, 8.5)):
        line(lines, [(x0, y), (x1, y)], 1.0)
    ks, outer = kite_layers(A, B, 18.0, 16.5, 13.0, 19.0, 8)
    hit = mask()
    star(hit, 28.0, 15.0, 5.0, 0.16, 0)
    small = mask()
    for x, y, r in ((6.0, 4.0, 1.8), (27.5, 27.5, 1.8), (28.5, 5.0, 1.3)):
        star(small, x, y, r)
    subject = stack(layer(lines, mix(A, WHITE, 0.5), A, None, None, axis=(1, 0)), layer(small, WHITE, A), *ks, layer(hit, WHITE, A))
    return finish(subject, [glow(outer, A, 1.8, 0.9), glow(hit, A, 1.8, 1.0)], paladin_back(A, B))


def paladin_quake(A, B):
    # 땅을 내려친 망치 (머리가 아래 가운데) · 퍼지는 번개 고리 셋 (타원) · 고리 위 작은 벼락
    rings_ = mask()
    for rx, ry, w in ((7.5, 2.6, 1.0), (11.5, 3.8, 1.0), (15.0, 5.0, 1.0)):
        d = ImageDraw.Draw(rings_)
        d.ellipse([P(16 - rx, 26 - ry), P(16 + rx, 26 + ry)], outline=255, width=int(w * SS))
    sparks = mask()
    for x, y in ((4.0, 24.5), (28.0, 25.0), (9.0, 29.5), (24.0, 29.6)):
        line(sparks, bolt_pts(x, y - 4.0, x, y, 3, 0.9), 0.8)
    ham, hd = hammer_layers(A, B, 16.0, 21.0, -90, head_w=12.0, head_h=6.5, handle=13.0)
    hit = mask()
    star(hit, 16.0, 25.5, 4.5, 0.18, 0)
    subject = stack(layer(rings_, mix(B, WHITE, 0.35), B), layer(sparks, WHITE, B), *ham, layer(hit, WHITE, A))
    return finish(subject, [glow(rings_, B, 1.6, 1.0), glow(sparks, B, 1.0, 0.8)], paladin_back(A, B))


def paladin_oath(A, B):
    # 펼친 빛의 날개 · 가운데 작은 연 방패 · 위의 빛살
    rays = mask()
    for a in range(-140, -30, 36):
        r = math.radians(a)
        poly(rays, [(16.0, 13.0), (16.0 + math.cos(r - 0.06) * 15, 13.0 + math.sin(r - 0.06) * 15), (16.0 + math.cos(r + 0.06) * 15, 13.0 + math.sin(r + 0.06) * 15)])
    wings = lighter(wing(14.5, 19.0, -1, 14.0), wing(17.5, 19.0, 1, 14.0))
    feathers = lighter(wing_lines(14.5, 19.0, -1, 14.0), wing_lines(17.5, 19.0, 1, 14.0))
    ks, outer = kite_layers(A, B, 16.0, 19.0, 9.0, 12.5)
    subject = stack(layer(rays, mix(A, WHITE, 0.35), A), layer(wings, WHITE, mix(A, WHITE, 0.45), WHITE, mix(A, DARK, 0.3), axis=(0, 1)),
                    layer(feathers, mix(A, DARK, 0.15), mix(A, DARK, 0.3)), *ks)
    return finish(subject, [glow(wings, A, 1.8, 0.9), glow(rays, A, 1.2, 0.6)], paladin_back(A, B))


def paladin_ult(A, B):
    # 위의 먹구름 · 내려오는 번개 대망치 (머리가 아래) · 양옆 벼락 · 땅의 큰 번쩍
    cloud = mask()
    for x, y, r in ((5.0, 4.0, 4.5), (11.0, 3.0, 5.0), (18.0, 3.5, 5.2), (25.0, 3.2, 4.8), (29.5, 5.0, 3.6)):
        disc(cloud, x, y, r)
    b1 = bolt_mask(5.0, 7.0, 3.0, 26.0, 1.2, 5, 1.6, 2)
    b2 = bolt_mask(27.0, 7.0, 29.0, 26.0, 1.2, 5, 1.6, 1)
    ham, hd = hammer_layers(A, B, 16.0, 21.0, -90, head_w=14.0, head_h=7.0, handle=11.0, shaft=1.8)
    edge = mask()
    line(edge, bolt_pts(10.0, 19.5, 22.0, 22.5, 4, 1.0), 0.8)
    hit = mask()
    star(hit, 16.0, 27.5, 7.5, 0.13, 0)
    subject = stack(layer(cloud, CLOUD, CLOUD_LO, mix(CLOUD, WHITE, 0.3), None), layer(lighter(b1, b2), WHITE, mix(B, WHITE, 0.4)), layer(hit, WHITE, A), *ham,
                    layer(edge, WHITE, B))
    return finish(subject, [glow(lighter(b1, b2), B, 1.6, 1.0), glow(hd, B, 2.2, 1.0), glow(hit, A, 2.0, 1.0)], paladin_back(A, B))


# ------------------------------------------------------------ 사제 (빛: 옅은 금빛 → 초록)

def priest_back(A, B):
    return backdrop(mix(B, (2, 16, 8), 0.88), mix(B, (24, 60, 34), 0.62))


def priest_heal(A, B):
    # 아래에서 펼친 손 · 그 위에 빛나는 십자 · 반짝임
    hand = mask()
    poly(hand, [(9.0, 31.0), (8.5, 24.0), (10.0, 21.5), (21.0, 21.5), (23.5, 24.0), (23.0, 31.0)])
    for x, top in ((10.8, 15.5), (14.2, 14.2), (17.6, 14.6), (21.0, 16.4)):
        line(hand, [(x, 22.5), (x, top)], 2.4)
    line(hand, [(22.5, 26.0), (26.5, 22.0)], 2.4)
    cr = cross(16.0, 8.0, 5.0, 3.0)
    sp = mask()
    for x, y, r in ((6.0, 6.0, 1.8), (26.5, 9.5, 1.6), (25.0, 3.0, 1.2), (5.0, 14.0, 1.2)):
        star(sp, x, y, r)
    subject = stack(layer(hand, mix(A, WHITE, 0.35), mix(A, (190, 150, 110), 0.5), WHITE, mix(A, DARK, 0.45)), layer(cr, WHITE, mix(B, WHITE, 0.25)),
                    layer(sp, WHITE, A))
    return finish(subject, [glow(cr, B, 2.2, 1.0), glow(sp, A, 1.0, 0.8)], priest_back(A, B))


def priest_step(A, B):
    # 위오른쪽으로 미끄러지는 빛의 꺾쇠 셋 (뒤로 갈수록 흐림) · 끝의 작은 방패 빛 · 반짝임
    chev = []
    for k, (x, y) in enumerate(((7.0, 25.0), (13.5, 18.5), (20.0, 12.0))):
        m = mask()
        line(m, local(x, y, -45, [(-3.6, -3.2), (0.4, 0.0), (-3.6, 3.2)]), 2.2)
        chev.append(m)
    shield = kite(27.0, 5.5, 6.0, 8.0)
    sp = mask()
    for x, y, r in ((4.0, 16.0, 1.4), (14.0, 28.5, 1.2), (28.5, 16.0, 1.5)):
        star(sp, x, y, r)
    subject = stack(layer(chev[0], mix(B, DARK, 0.25), mix(B, DARK, 0.45)), layer(chev[1], mix(B, WHITE, 0.25), B), layer(chev[2], WHITE, mix(B, WHITE, 0.4)),
                    layer(shield, WHITE, A, WHITE, mix(A, DARK, 0.4)), layer(sp, WHITE, A))
    return finish(subject, [glow(lighter(*chev), B, 1.6, 0.9), glow(shield, A, 1.4, 0.9)], priest_back(A, B))


def priest_hymn(A, B):
    # 떠오르는 음표 셋 · 아래의 빛 고리 (타원)
    ring_ = mask()
    d = ImageDraw.Draw(ring_)
    d.ellipse([P(3.0, 22.5), P(29.0, 30.5)], outline=255, width=int(1.3 * SS))
    n1 = note_mask(8.0, 19.0, 0, 1.05)
    n2 = note_mask(16.0, 14.5, 1, 1.0)
    n3 = note_mask(23.5, 22.0, 0, 0.85)
    sp = mask()
    for x, y, r in ((5.0, 6.0, 1.6), (28.0, 5.0, 1.4), (14.0, 26.5, 1.2)):
        star(sp, x, y, r)
    notes = lighter(n1, n2, n3)
    subject = stack(layer(ring_, mix(B, WHITE, 0.3), B), layer(notes, WHITE, mix(A, WHITE, 0.2), WHITE, mix(B, DARK, 0.3)), layer(sp, WHITE, A))
    return finish(subject, [glow(notes, B, 1.0, 0.3), glow(ring_, B, 1.2, 0.7)], priest_back(A, B))


def priest_stigma(A, B):
    # 빛나는 라틴 십자 · 뒤의 빛살 · 아래의 과녁 (적) 표시 — 작은 꺾쇠 둘
    rays = mask()
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        L = 14.0 if i % 2 == 0 else 10.0
        poly(rays, [(16.0 + math.cos(a + 1.5) * 0.9, 12.0 + math.sin(a + 1.5) * 0.9), (16.0 + math.cos(a) * L, 12.0 + math.sin(a) * L),
                    (16.0 + math.cos(a - 1.5) * 0.9, 12.0 + math.sin(a - 1.5) * 0.9)])
    cr = mask()
    line(cr, [(16.0, 3.0), (16.0, 24.0)], 2.6)
    line(cr, [(10.0, 9.5), (22.0, 9.5)], 2.6)
    marks = mask()
    for sx in (-1, 1):
        line(marks, [(16.0 + sx * 9.5, 24.5), (16.0 + sx * 12.0, 27.5), (16.0 + sx * 9.5, 30.5)], 1.1)
    subject = stack(layer(rays, mix(A, WHITE, 0.2), A), layer(cr, WHITE, mix(A, WHITE, 0.4), WHITE, mix(B, DARK, 0.3)), layer(marks, mix(B, WHITE, 0.3), B))
    return finish(subject, [glow(cr, A, 2.0, 1.0), glow(rays, A, 1.2, 0.6)], priest_back(A, B))


def priest_ult(A, B):
    # 큰 종 (흔들림) · 양옆 소리 물결 · 위의 새벽빛 반짝임
    body, lip, clap = bell_mask(16.0, 3.5, 16.0, 21.0, -10)
    waves = mask()
    for r, w in ((3.2, 1.1), (6.0, 1.0)):
        crescent(waves, 6.5, 21.0, r, w, 140, 220, taper=False)
        crescent(waves, 25.5, 21.0, r, w, -40, 40, taper=False)
    sp = mask()
    for x, y, r in ((5.0, 6.0, 2.0), (27.0, 5.0, 1.6), (24.0, 13.0, 1.2), (8.0, 13.0, 1.2)):
        star(sp, x, y, r)
    subject = stack(layer(waves, WHITE, mix(A, WHITE, 0.3)), layer(sp, WHITE, A),
                    layer(body, mix((255, 214, 110), WHITE, 0.2), (214, 150, 50), WHITE, (120, 80, 30), axis=(0.6, 0.8)), layer(lip, WHITE, (255, 230, 150)),
                    layer(clap, (255, 230, 150), (160, 110, 40)))
    return finish(subject, [glow(body, A, 2.4, 1.0), glow(waves, A, 1.2, 0.8)], priest_back(A, B))


# ------------------------------------------------------------ 수호기사 (빛: 강철 파랑 → 흰빛)

def guardian_back(A, B):
    return backdrop(mix(A, (4, 8, 20), 0.86), mix(A, (36, 56, 100), 0.5))


def guardian_bash(A, B):
    # 오른쪽으로 내지른 연 방패 · 부딪힘 번쩍 · 맞은 적의 기절 별 셋
    lines = mask()
    for y, x0, x1 in ((10.0, 1.0, 7.5), (17.0, 1.5, 8.5), (24.0, 1.0, 7.5)):
        line(lines, [(x0, y), (x1, y)], 1.0)
    ks, outer = kite_layers(A, B, 14.0, 17.0, 13.0, 19.0, 10)
    hit = mask()
    star(hit, 24.5, 15.0, 6.5, 0.14, 0)
    stars = mask()
    for x, y in ((25.0, 4.5), (29.5, 7.5), (21.0, 6.0)):
        star(stars, x, y, 1.7, 0.35, 45)
    subject = stack(layer(lines, mix(B, WHITE, 0.2), A, None, None, axis=(1, 0)), *ks, layer(hit, WHITE, B), layer(stars, (255, 236, 130), (230, 180, 60)))
    return finish(subject, [glow(outer, A, 1.6, 0.8), glow(hit, B, 2.0, 1.0), glow(stars, (255, 220, 100), 1.0, 0.8)], guardian_back(A, B))


def guardian_rush(A, B):
    # 왼쪽에서 달려 든 둥근 방패 · 오른쪽 아군 (작은 사람) · 둘을 잇는 빛의 사슬
    lines = mask()
    for y, x0, x1 in ((11.0, 0.5, 6.0), (18.0, 0.5, 5.0), (25.0, 0.5, 6.0)):
        line(lines, [(x0, y), (x1, y)], 1.0)
    sh = mask()
    disc(sh, 12.5, 18.0, 7.5)
    rim = mask()
    ring(rim, 12.5, 18.0, 7.5, 1.6)
    boss = mask()
    disc(boss, 12.5, 18.0, 2.0)
    ally = person(26.0, 30.0, 13.0)
    chain = mask()
    for i in range(4):
        x = 16.5 + i * 2.3
        d = ImageDraw.Draw(chain)
        if i % 2 == 0:
            d.ellipse([P(x - 1.4, 8.0), P(x + 1.4, 10.4)], outline=255, width=int(0.8 * SS))
        else:
            line(chain, [(x - 1.2, 9.2), (x + 1.2, 9.2)], 0.9)
    subject = stack(layer(lines, mix(B, WHITE, 0.2), A, None, None, axis=(1, 0)), layer(chain, WHITE, B),
                    layer(ally, mix(A, WHITE, 0.55), mix(A, WHITE, 0.2), WHITE, mix(A, DARK, 0.3)),
                    layer(sh, mix(A, (30, 40, 70), 0.3), mix(A, DARK, 0.45)), layer(rim, mix(B, WHITE, 0.4), mix(A, B, 0.4), WHITE, mix(A, DARK, 0.4)),
                    layer(boss, WHITE, B))
    return finish(subject, [glow(rim, A, 1.6, 0.9), glow(chain, B, 1.2, 0.9)], guardian_back(A, B))


def guardian_throw(A, B):
    # 돌며 날아가는 둥근 방패 (도는 자국) · 튕겨 가는 점선 · 맞은 자리 번쩍 둘
    trail = mask()
    for (x0, y0, x1, y1) in ((14.0, 18.0, 20.0, 10.0), (21.5, 9.0, 25.0, 17.5), (26.0, 19.5, 29.5, 11.0)):
        for i in range(3):
            t0, t1 = i / 3, (i + 0.55) / 3
            line(trail, [(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0), (x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1)], 0.9)
    sh = mask()
    disc(sh, 9.0, 21.0, 7.0)
    rim = mask()
    ring(rim, 9.0, 21.0, 7.0, 1.5)
    inner = mask()
    ring(inner, 9.0, 21.0, 4.0, 0.8)
    boss = mask()
    disc(boss, 9.0, 21.0, 1.8)
    spin = mask()
    crescent(spin, 9.0, 21.0, 10.0, 1.8, 200, 320)
    hits = mask()
    star(hits, 20.5, 9.5, 3.4, 0.2, 0)
    star(hits, 25.5, 18.5, 3.0, 0.2, 0)
    subject = stack(layer(spin, mix(B, WHITE, 0.3), A), layer(trail, WHITE, B), layer(sh, mix(A, (30, 40, 70), 0.3), mix(A, DARK, 0.45)),
                    layer(rim, mix(B, WHITE, 0.4), mix(A, B, 0.4), WHITE, mix(A, DARK, 0.4)), layer(inner, mix(A, WHITE, 0.3), A), layer(boss, WHITE, B),
                    layer(hits, WHITE, B))
    return finish(subject, [glow(rim, A, 1.6, 0.9), glow(hits, B, 1.4, 1.0)], guardian_back(A, B))


def guardian_bulwark(A, B):
    # 크게 세운 연 방패 · 오른쪽에서 날아와 막혀 꺾인 화살 셋 · 막힌 자리 번쩍
    ks, outer = kite_layers(A, B, 13.0, 16.5, 17.0, 26.0)
    arrows = mask()
    for y, bend in ((7.0, -1), (16.0, 1), (24.0, -1)):
        line(arrows, [(31.0, y + bend * 1.0), (24.0, y)], 0.9)
        poly(arrows, [(23.0, y), (25.0, y - 1.2), (25.0, y + 1.2)])
    hits = mask()
    for y in (7.0, 16.0, 24.0):
        star(hits, 22.0, y, 2.4, 0.2, 0)
    subject = stack(*ks, layer(arrows, STEEL_HI, STEEL_LO), layer(hits, WHITE, B))
    return finish(subject, [glow(outer, A, 1.8, 0.9), glow(hits, B, 1.4, 1.0)], guardian_back(A, B))


def guardian_ult(A, B):
    # 돌 성벽 (성가퀴) · 양쪽 망루와 파란 깃발 · 위의 빛 고리 (요새)
    dome = mask()
    crescent(dome, 16.0, 22.0, 15.0, 1.6, -180, 0, taper=False)
    wall = mask()
    poly(wall, [(5.0, 30.5), (27.0, 30.5), (27.0, 20.0), (5.0, 20.0)])
    for x in (7.0, 11.5, 16.0, 20.5, 25.0):
        poly(wall, [(x - 1.5, 20.5), (x + 1.5, 20.5), (x + 1.5, 17.8), (x - 1.5, 17.8)])
    towers = mask()
    for x in (3.5, 28.5):
        poly(towers, [(x - 3.0, 30.5), (x + 3.0, 30.5), (x + 3.0, 13.0), (x - 3.0, 13.0)])
        for dx in (-2.2, 0.0, 2.2):
            poly(towers, [(x + dx - 0.8, 13.5), (x + dx + 0.8, 13.5), (x + dx + 0.8, 11.3), (x + dx - 0.8, 11.3)])
    gate = mask()
    poly(gate, [(13.5, 30.5), (18.5, 30.5), (18.5, 25.0), (16.0, 23.2), (13.5, 25.0)])
    poles = mask()
    flags = mask()
    for x in (3.5, 28.5):
        line(poles, [(x, 11.5), (x, 4.0)], 0.8)
        side = 1 if x < 16 else -1
        poly(flags, [(x, 4.0), (x + side * 5.0, 5.4), (x, 7.6)])
    bricks = mask()
    for y in (23.5, 27.0):
        line(bricks, [(5.0, y), (27.0, y)], 0.6)
    subject = stack(layer(dome, mix(B, WHITE, 0.3), A), layer(poles, WOOD, WOOD_LO), layer(flags, mix(FLAG, WHITE, 0.2), FLAG_LO),
                    layer(towers, STONE_HI, STONE, WHITE, STONE_LO), layer(wall, STONE_HI, STONE, WHITE, STONE_LO), layer(bricks, STONE_LO, STONE_LO),
                    layer(gate, mix(A, DARK, 0.55), mix(A, DARK, 0.7)))
    return finish(subject, [glow(dome, B, 1.6, 0.9), glow(wall, A, 1.6, 0.5)], guardian_back(A, B))


ICONS = {
    "paladin_smite": paladin_smite,
    "paladin_charge": paladin_charge,
    "paladin_quake": paladin_quake,
    "paladin_oath": paladin_oath,
    "paladin_ult": paladin_ult,
    "priest_heal": priest_heal,
    "priest_step": priest_step,
    "priest_hymn": priest_hymn,
    "priest_stigma": priest_stigma,
    "priest_ult": priest_ult,
    "guardian_bash": guardian_bash,
    "guardian_rush": guardian_rush,
    "guardian_throw": guardian_throw,
    "guardian_bulwark": guardian_bulwark,
    "guardian_ult": guardian_ult,
}
