#!/usr/bin/env python3
"""0.28.0 직업 스킬 아이콘 — 전사 · 매지션 · 궁수 (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음)."""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (GOLD, GOLD_HI, GOLD_LO, SS, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask,  # noqa: E402
                             mix, paint, poly, ring, solid, stack, star, sword_layers)

N = 32
WOOD = (150, 96, 54)
WOOD_LO = (88, 54, 30)
DARK = (0, 0, 0)


def P(x, y):
    return (x * SS, y * SS)


def axe_masks(head, tail, blade=7.0, side=1, back=True):
    """도끼: tail (자루 끝) → head (자루 위끝). 날 = 자루 위끝 한쪽 (side 1 = p 쪽 · −1 = 반대) 의 큰 반달 (날 끝이 바깥으로 둥글게), 반대쪽 짧은 뿔"""
    hx, hy = head
    tx, ty = tail
    L = math.hypot(hx - tx, hy - ty)
    ux, uy = (hx - tx) / L, (hy - ty) / L
    px, py = -uy * side, ux * side
    k = blade / 8.0

    def at(a, b):
        return (hx + ux * a * k + px * b * k, hy + uy * a * k + py * b * k)

    handle = mask()
    line(handle, [tail, (hx + ux * 1.2 * k, hy + uy * 1.2 * k)], 1.8)
    bl = mask()
    edge_pts = []
    for i in range(13):
        t = i / 12
        a = math.radians(-80 + 160 * t)
        edge_pts.append(at(math.sin(a) * 4.6, 5.0 + math.cos(a) * 3.2))
    pts = [at(-1.4, 0.6), at(-2.6, 2.2)] + edge_pts[::-1][::-1] + [at(2.6, 2.2), at(1.4, 0.6)]
    # 위 (a +) → 아래 순서로 다시: 자루 쪽 위 → 날 끝 위 → 둥근 날 → 날 끝 아래 → 자루 쪽 아래
    pts = [at(1.5, 0.5), at(2.9, 2.4)] + edge_pts[::-1] + [at(-2.9, 2.4), at(-1.5, 0.5)]
    poly(bl, pts)
    if back:
        poly(bl, [at(1.0, -0.4), at(0.0, -2.4), at(-1.0, -0.4)])
    edge = mask()
    line(edge, edge_pts, 1.0)
    return handle, bl, edge


def axe_layers(head, tail, blade=7.0, tint=None, side=1):
    handle, bl, edge = axe_masks(head, tail, blade, side)
    steel_hi = mix(STEEL_HI, tint, 0.15) if tint else STEEL_HI
    steel = mix(STEEL, tint, 0.25) if tint else STEEL
    return [layer(handle, WOOD, WOOD_LO, mix(WOOD, WHITE, 0.3), None),
            layer(bl, steel_hi, steel, WHITE, STEEL_LO, axis=(0.7, 0.7)),
            layer(edge, WHITE, WHITE)]


def flame_mask(cx, base_y, w, h, tongues=5, seed=0):
    """불꽃: 아래 둥근 몸 + 위로 뾰족한 혀 tongues 개 (가운데가 가장 높음)"""
    m = mask()
    pts = []
    # 아래 반원 (왼쪽 → 오른쪽)
    for i in range(13):
        a = math.pi - math.pi * i / 12
        pts.append((cx + math.cos(a) * w / 2, base_y - h * 0.3 + math.sin(a) * h * 0.3))
    # 위: 오른쪽 → 왼쪽으로 혀 (봉우리 · 골)
    n = tongues
    for i in range(n * 2 + 1):
        t = i / (n * 2)
        x = cx + w / 2 - w * t
        if i % 2 == 1:
            mid = 1 - abs(t - 0.5) * 1.3
            hh = h * (0.55 + 0.45 * mid) * (0.9 + 0.1 * math.sin(i * 2.3 + seed))
            pts.append((x, base_y - hh))
        else:
            pts.append((x, base_y - h * (0.32 + 0.18 * (1 - abs(t - 0.5) * 2))))
    poly(m, pts)
    return m


# ------------------------------------------------------------ 전사 (빛: 주황 → 진홍)

def warrior_back(A, B):
    return backdrop(mix(B, (20, 6, 4), 0.86), mix(A, (60, 16, 6), 0.45))


def warrior_crush(A, B):
    # 내려찍은 도끼 (날이 땅을 향함) · 휘두른 자국 · 바닥 금 · 터짐
    swing = mask()
    crescent(swing, 13.0, 17.0, 13.0, 3.4, -150, -60)
    crack = mask()
    for pts in (((12, 27), (8, 28.4), (4.5, 27.4), (1.5, 28.6)), ((12, 27), (16, 28.6), (20, 27.4), (24.5, 28.8)), ((12, 27), (10.5, 30.6)), ((12, 27), (14.5, 30.8)),
                ((16, 28.6), (17.5, 31))):
        line(crack, list(pts), 1.0)
    ground = mask()
    poly(ground, [(1, 26.2), (31, 26.2), (31, 27.2), (1, 27.2)])
    burst = mask()
    star(burst, 12.0, 25.6, 6.0, 0.18, 0)
    axe = axe_layers((11.5, 18.5), (28.5, 3.5), blade=9.0, tint=A, side=-1)
    subject = stack(layer(swing, mix(A, WHITE, 0.4), B, None, None, axis=(1, -1)), layer(ground, mix(A, (40, 20, 10), 0.5), mix(B, (20, 6, 4), 0.6)),
                    layer(crack, WHITE, A), *axe, layer(burst, WHITE, A))
    return finish(subject, [glow(swing, A, 1.6, 0.9), glow(burst, B, 2.0, 1.0)], warrior_back(A, B))


def warrior_charge(A, B):
    # 오른쪽으로 내닫는 쐐기 (어깨) · 뒤의 빠른 줄 · 앞의 부딪힘 · 아래 흙먼지
    lines = mask()
    for y, x0, x1, w in ((8.5, 1.0, 9.5, 1.3), (13.0, 2.5, 8.0, 1.2), (19.0, 2.5, 8.0, 1.2), (23.5, 1.0, 9.5, 1.3)):
        line(lines, [(x0, y), (x1, y)], w)
    wedge = mask()
    poly(wedge, [(9.0, 6.0), (21.0, 6.0), (28.5, 16.0), (21.0, 26.0), (9.0, 26.0), (16.0, 16.0)])
    inner = mask()
    poly(inner, [(12.5, 10.0), (19.5, 10.0), (24.0, 16.0), (19.5, 22.0), (12.5, 22.0), (17.0, 16.0)])
    hit = mask()
    star(hit, 28.5, 16.0, 5.6, 0.16, 0)
    dust = mask()
    for x, y, r in ((6, 28.5, 2.2), (10.5, 29.5, 1.8), (3, 29.8, 1.4)):
        disc(dust, x, y, r)
    subject = stack(layer(lines, WHITE, A, None, None, axis=(1, 0)), layer(dust, mix(A, (90, 60, 40), 0.6), mix(B, (40, 20, 10), 0.6)),
                    layer(wedge, A, B, mix(A, WHITE, 0.55), mix(B, DARK, 0.4), axis=(1, 0)), layer(inner, mix(A, WHITE, 0.5), A, None, None, axis=(1, 0)),
                    layer(hit, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(wedge, A, 1.6, 0.8), glow(hit, A, 1.8, 1.0)], warrior_back(A, B))


def warrior_whirl(A, B):
    # 도는 도끼 날 셋 (초승달) · 가운데 불꽃 · 흩날리는 불티
    blades = []
    for i in range(3):
        m = mask()
        a0 = -90 + i * 120
        crescent(m, 16, 16, 13.5, 5.2, a0, a0 + 95)
        blades.append(m)
    core = flame_mask(16, 20.5, 7.5, 10.0, 3, 0.4)
    sp = mask()
    for x, y, r in ((27.5, 6.5, 1.0), (5.5, 9.0, 0.8), (26.0, 26.5, 0.9), (6.5, 25.5, 1.0)):
        disc(sp, x, y, r)
    dark = mix(B, DARK, 0.35)
    allm = ImageChops.lighter(ImageChops.lighter(blades[0], blades[1]), blades[2])
    subject = stack(*[layer(m, WHITE, A, WHITE, dark, axis=(0.7, 0.7)) for m in blades], layer(core, mix(A, WHITE, 0.4), B, None, None, axis=(0, -1)),
                    layer(sp, WHITE, A))
    return finish(subject, [glow(allm, A, 1.6, 0.9), glow(core, B, 1.6, 0.9)], warrior_back(A, B))


def warrior_spirit(A, B):
    # 몸을 감싸는 불꽃 (뾰족한 혀) · 가운데 곧게 선 도끼
    outer = flame_mask(16, 31.0, 27, 30, 5, 0.0)
    inner = flame_mask(16, 30.0, 16, 19, 3, 1.2)
    ring_m = ImageChops.subtract(outer, inner)
    axe = axe_layers((15.0, 9.0), (15.0, 30.0), blade=7.4, tint=A, side=1)
    sp = mask()
    star(sp, 27.0, 5.0, 2.2)
    star(sp, 5.0, 7.0, 1.8)
    subject = stack(layer(ring_m, mix(A, WHITE, 0.3), B, mix(A, WHITE, 0.6), mix(B, DARK, 0.35), axis=(0, -1)), layer(inner, mix(B, DARK, 0.45), mix(B, DARK, 0.6)),
                    *axe, layer(sp, WHITE, A))
    return finish(subject, [glow(outer, B, 2.0, 0.85)], warrior_back(A, B))


def warrior_ult(A, B):
    # 불타는 운석 (위왼쪽 → 아래오른쪽): 머리 쪽이 굵은 불꽃 꼬리 · 땅의 구덩이 · 터짐
    tail = mask()
    pts_l, pts_r = [], []
    for i in range(17):
        t = i / 16  # 0 = 머리 → 1 = 꼬리 끝
        cx, cy = 19.0 - 17.0 * t, 19.0 - 17.0 * t
        w = 5.0 * (1 - t) ** 0.8 * (1 + 0.25 * math.sin(t * 11))
        pts_l.append((cx - w * 0.707, cy + w * 0.707))
        pts_r.append((cx + w * 0.707, cy - w * 0.707))
    poly(tail, pts_l + pts_r[::-1])
    inner = mask()
    pts_l, pts_r = [], []
    for i in range(13):
        t = i / 12
        cx, cy = 19.0 - 12.0 * t, 19.0 - 12.0 * t
        w = 2.6 * (1 - t) ** 0.9
        pts_l.append((cx - w * 0.707, cy + w * 0.707))
        pts_r.append((cx + w * 0.707, cy - w * 0.707))
    poly(inner, pts_l + pts_r[::-1])
    head = mask()
    disc(head, 20.0, 20.0, 5.6)
    hcore = mask()
    disc(hcore, 20.6, 20.6, 3.0)
    crater = mask()
    d = ImageDraw.Draw(crater)
    d.ellipse([P(13.0, 25.0), P(31.0, 31.2)], fill=255)
    d.ellipse([P(15.5, 26.4), P(28.5, 29.8)], fill=0)
    burst = mask()
    star(burst, 21.5, 24.5, 8.0, 0.15, 0)
    subject = stack(layer(tail, B, mix(B, (60, 8, 4), 0.4), None, None, axis=(-1, -1)), layer(inner, mix(A, WHITE, 0.2), A, None, None, axis=(-1, -1)),
                    layer(crater, mix(A, WHITE, 0.25), B, None, None), layer(head, A, B, mix(A, WHITE, 0.6), mix(B, DARK, 0.3)), layer(hcore, WHITE, mix(A, WHITE, 0.5)),
                    layer(burst, WHITE, mix(A, WHITE, 0.4)))
    return finish(subject, [glow(head, A, 2.4, 1.0), glow(tail, B, 1.4, 0.7)], warrior_back(A, B))


# ------------------------------------------------------------ 매지션 (빛: 남색 → 금빛)

def mage_back(A, B):
    return backdrop(mix(A, (4, 6, 24), 0.85), mix(A, (16, 20, 70), 0.5))


def bolt_mask(hx, hy, ang, L, w=2.2):
    """마탄: 머리 (마름모) + 뒤로 가늘어지는 꼬리 (ang = 날아가는 쪽, 도)"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    tailm = mask()
    poly(tailm, [(hx + px * w * 0.55, hy + py * w * 0.55), (hx - ux * L, hy - uy * L), (hx - px * w * 0.55, hy - py * w * 0.55)])
    headm = mask()
    poly(headm, [(hx + ux * w * 1.6, hy + uy * w * 1.6), (hx + px * w, hy + py * w), (hx - ux * w * 0.9, hy - uy * w * 0.9), (hx - px * w, hy - py * w)])
    return tailm, headm


def mage_bolts(A, B):
    # 완드 끝 (아래왼쪽) 에서 위오른쪽으로 갈라져 날아가는 마탄 셋
    layers = []
    glows = []
    for (hx, hy, L, ang) in ((25.5, 5.0, 13.0, -52), (28.0, 14.5, 13.5, -28), (17.0, 9.0, 9.5, -70)):
        tailm, headm = bolt_mask(hx, hy, ang, L, 2.7)
        layers += [layer(tailm, mix(A, WHITE, 0.45), A, None, None, axis=(1, -1)), layer(headm, WHITE, B, WHITE, mix(B, DARK, 0.3))]
        glows.append(glow(headm, B, 1.4, 1.0))
    tip = mask()
    star(tip, 7.0, 25.0, 5.4, 0.18, 0)
    wand = mask()
    line(wand, [(1.0, 31.0), (6.2, 25.8)], 2.0)
    subject = stack(layer(wand, mix(A, WHITE, 0.2), mix(A, DARK, 0.3)), *layers, layer(tip, WHITE, B))
    return finish(subject, glows + [glow(tip, B, 1.8, 1.0)], mage_back(A, B))


def mage_phase(A, B):
    # 금이 가 깨지는 거울 (마름모 판) · 흩어지는 조각 · 뒤로 휘어 돌아가는 금빛 화살표
    pane = mask()
    poly(pane, [(13.0, 3.0), (23.0, 13.0), (13.0, 23.0), (3.0, 13.0)])
    cracks = mask()
    for pts in (((13, 13), (9, 6.5)), ((13, 13), (19.5, 9.5)), ((13, 13), (17, 19.5)), ((13, 13), (6.5, 15.5)), ((13, 13), (13.5, 21.5))):
        line(cracks, list(pts), 0.9)
    shards = mask()
    for (x, y, s, r) in ((25.5, 5.0, 2.6, 20), (27.5, 11.5, 2.0, 70), (23.5, 20.5, 2.2, 40)):
        a = math.radians(r)
        poly(shards, [(x + math.cos(a) * s, y + math.sin(a) * s), (x + math.cos(a + 2.2) * s, y + math.sin(a + 2.2) * s),
                      (x + math.cos(a + 4.1) * s * 0.8, y + math.sin(a + 4.1) * s * 0.8)])
    arrow = mask()
    pts = []
    for i in range(16):
        t = i / 15
        a = math.radians(200 - 150 * t)
        pts.append((20.0 + math.cos(a) * 8.0, 23.5 + math.sin(a) * 5.0))
    line(arrow, pts, 1.7)
    ex, ey = pts[-1]
    poly(arrow, [(ex + 3.2, ey + 1.0), (ex - 1.6, ey - 2.2), (ex - 0.8, ey + 3.2)])
    subject = stack(layer(pane, mix(A, WHITE, 0.55), A, WHITE, mix(A, DARK, 0.35), axis=(0.6, 0.8)), layer(cracks, WHITE, WHITE),
                    layer(shards, mix(A, WHITE, 0.6), A, WHITE, None), layer(arrow, WHITE, B, WHITE, mix(B, DARK, 0.35), axis=(1, 0)))
    return finish(subject, [glow(pane, A, 1.6, 0.8), glow(arrow, B, 1.4, 0.8)], mage_back(A, B))


def mage_orb(A, B):
    rays = mask()
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        pts = [(16 + math.cos(r) * 8.5, 16 + math.sin(r) * 8.5), (16 + math.cos(r + 0.12) * 12.0, 16 + math.sin(r + 0.12) * 12.0),
               (16 + math.cos(r - 0.1) * 13.5, 16 + math.sin(r - 0.1) * 13.5), (16 + math.cos(r + 0.05) * 15.5, 16 + math.sin(r + 0.05) * 15.5)]
        line(rays, pts, 1.2)
    orb = mask()
    disc(orb, 16, 16, 8.6)
    rim = mask()
    ring(rim, 16, 16, 8.6, 1.4)
    swirl = mask()
    for i in range(3):
        a0 = -90 + i * 120
        crescent(swirl, 16, 16, 6.0, 2.0, a0, a0 + 80)
    core = mask()
    disc(core, 16, 16, 2.6)
    subject = stack(layer(rays, WHITE, B), layer(orb, mix(A, WHITE, 0.25), mix(A, DARK, 0.35), None, None, axis=(0.6, 0.8)), layer(swirl, mix(B, WHITE, 0.3), B),
                    layer(rim, mix(A, WHITE, 0.7), A), layer(core, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(orb, A, 2.2, 0.9), glow(rays, B, 1.2, 0.9)], mage_back(A, B))


def mage_overload(A, B):
    # 완드 (왼쪽 아래) 끝의 별에서 오른쪽으로 넓어지며 뻗는 광선 · 완드 둘레 기운 고리
    wand = mask()
    line(wand, [(1.5, 30.5), (8.0, 21.0)], 2.2)
    beam = mask()
    poly(beam, [(9.0, 18.0), (31.5, 9.5), (31.5, 24.5), (9.0, 21.0)])
    beam_core = mask()
    poly(beam_core, [(9.5, 19.0), (31.5, 15.2), (31.5, 18.8), (9.5, 20.0)])
    aura = mask()
    ring(aura, 8.6, 19.6, 5.6, 1.1)
    tip = mask()
    star(tip, 8.6, 19.6, 5.0, 0.18, 45)
    star(tip, 8.6, 19.6, 3.2, 0.3, 0)
    sp = mask()
    for x, y in ((3.5, 11.0), (16.5, 27.5), (21.0, 7.0)):
        star(sp, x, y, 2.0, 0.22, 0)
    subject = stack(layer(wand, mix(A, WHITE, 0.25), mix(A, DARK, 0.35)), layer(beam, mix(B, A, 0.15), mix(A, B, 0.35), None, None, axis=(1, 0)),
                    layer(beam_core, WHITE, mix(B, WHITE, 0.5)), layer(aura, mix(A, WHITE, 0.5), A), layer(tip, WHITE, B), layer(sp, WHITE, B))
    return finish(subject, [glow(beam, B, 2.0, 1.0), glow(tip, A, 1.8, 1.0)], mage_back(A, B))


def mage_ult(A, B):
    # 펼쳐진 금서 (V 자로 벌어진 두 쪽 · 가운데 등) · 날아오르는 책장 셋 · 위로 퍼지는 빛
    rays = mask()
    for a in (-125, -90, -55):
        r = math.radians(a)
        poly(rays, [(16 + math.cos(r - 0.14) * 3, 17 + math.sin(r - 0.14) * 3), (16 + math.cos(r) * 16, 17 + math.sin(r) * 16),
                    (16 + math.cos(r + 0.14) * 3, 17 + math.sin(r + 0.14) * 3)])
    cover = mask()
    poly(cover, [(16.0, 19.5), (1.5, 15.5), (1.5, 26.5), (16.0, 30.5), (30.5, 26.5), (30.5, 15.5)])
    left = mask()
    poly(left, [(15.6, 19.0), (3.0, 15.8), (3.0, 25.0), (15.6, 28.8)])
    right = mask()
    poly(right, [(16.4, 19.0), (29.0, 15.8), (29.0, 25.0), (16.4, 28.8)])
    textl = mask()
    for k in range(3):
        y = 19.5 + k * 2.4
        line(textl, [(5.5, y - 0.9), (13.0, y + 1.0)], 0.6)
        line(textl, [(19.0, y + 1.0), (26.5, y - 0.9)], 0.6)
    spine = mask()
    line(spine, [(16.0, 19.0), (16.0, 29.5)], 0.9)
    fly = mask()
    for (x, y, a) in ((8.5, 9.0, -25), (23.5, 7.0, 20), (16.0, 3.5, -8)):
        r = math.radians(a)
        w, h = 2.2, 2.9
        pts = [(-w, -h), (w, -h), (w, h), (-w, h)]
        poly(fly, [(x + px * math.cos(r) - py * math.sin(r), y + px * math.sin(r) + py * math.cos(r)) for px, py in pts])
    subject = stack(layer(rays, mix(B, WHITE, 0.5), B), layer(cover, mix(A, WHITE, 0.15), mix(A, DARK, 0.4)),
                    layer(left, WHITE, mix(B, WHITE, 0.4), WHITE, mix(B, DARK, 0.2)), layer(right, mix(B, WHITE, 0.6), mix(B, WHITE, 0.2), WHITE, mix(B, DARK, 0.2)),
                    layer(textl, mix(A, WHITE, 0.2), A), layer(spine, mix(A, DARK, 0.2), A), layer(fly, WHITE, B, WHITE, mix(B, DARK, 0.25)))
    return finish(subject, [glow(rays, B, 1.6, 0.9), glow(ImageChops.lighter(left, right), B, 1.6, 0.8)], mage_back(A, B))


# ------------------------------------------------------------ 궁수 (빛: 연두 → 금빛)

def archer_back(A, B):
    return backdrop(mix(A, (4, 16, 6), 0.86), mix(A, (20, 56, 16), 0.5))


def arrow_masks(tip, ang, L, head=3.0, fletch=True, shaft_w=1.5):
    """화살 (tip = 촉 끝, ang = 촉이 향한 쪽, 도): 살대 · 촉 · 깃"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    tx, ty = tip
    shaft = mask()
    line(shaft, [(tx - ux * (head * 0.8), ty - uy * (head * 0.8)), (tx - ux * L, ty - uy * L)], shaft_w)
    hd = mask()
    poly(hd, [(tx, ty), (tx - ux * head + px * head * 0.6, ty - uy * head + py * head * 0.6), (tx - ux * head * 0.7, ty - uy * head * 0.7),
              (tx - ux * head - px * head * 0.6, ty - uy * head - py * head * 0.6)])
    fl = mask()
    if fletch:
        for d in (0.0, 1.8):
            bx, by = tx - ux * (L - 1.6 - d), ty - uy * (L - 1.6 - d)
            line(fl, [(bx, by), (bx - ux * 2.6 + px * 2.0, by - uy * 2.6 + py * 2.0)], 1.0)
            line(fl, [(bx, by), (bx - ux * 2.6 - px * 2.0, by - uy * 2.6 - py * 2.0)], 1.0)
    return shaft, hd, fl


def arrow_layers(A, B, tip, ang, L, head=3.0, fletch=True):
    shaft, hd, fl = arrow_masks(tip, ang, L, head, fletch)
    return [layer(shaft, mix(A, WHITE, 0.55), A), layer(fl, WHITE, A), layer(hd, WHITE, B, WHITE, mix(B, DARK, 0.35))], ImageChops.lighter(shaft, hd)


def archer_rapid(A, B):
    # 위오른쪽으로 나란히 날아가는 빛의 화살 셋 (굵은 금빛 촉) · 뒤의 빠른 줄
    layers = []
    glows = []
    for (x, y) in ((28.5, 5.5), (24.0, 14.0), (19.5, 22.5)):
        ls, gm = arrow_layers(A, B, (x, y), -32, 18.0, 4.6)
        layers += ls
        glows.append(glow(gm, A, 1.4, 0.9))
    streak = mask()
    for (x, y) in ((7.0, 15.0), (3.5, 23.5), (11.0, 7.5)):
        line(streak, [(x, y), (x - 4.5, y + 2.8)], 0.9)
    return finish(stack(layer(streak, WHITE, A), *layers), glows, archer_back(A, B))


def archer_wind(A, B):
    gust = []
    for (cx, cy, r, a0) in ((12.0, 12.0, 8.0, 160), (19.0, 19.5, 7.0, -20), (11.0, 23.0, 5.0, 100)):
        m = mask()
        pts = []
        for i in range(25):
            t = i / 24
            a = math.radians(a0 + 300 * t)
            rr = r * (1 - 0.55 * t)
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        line(m, pts, 1.8)
        gust.append(m)
    leaf = mask()
    poly(leaf, [(27.5, 4.0), (30.0, 9.5), (26.0, 12.0), (23.5, 7.0)])
    vein = mask()
    line(vein, [(28.8, 4.5), (24.8, 11.2)], 0.6)
    allm = ImageChops.lighter(ImageChops.lighter(gust[0], gust[1]), gust[2])
    subject = stack(*[layer(m, WHITE, A, WHITE, mix(A, DARK, 0.3), axis=(1, 1)) for m in gust], layer(leaf, mix(A, WHITE, 0.3), mix(A, DARK, 0.2), WHITE, None),
                    layer(vein, mix(A, DARK, 0.4), mix(A, DARK, 0.4)))
    return finish(subject, [glow(allm, A, 1.6, 0.9)], archer_back(A, B))


def archer_rain(A, B):
    # 하늘에서 비스듬히 쏟아지는 화살 (촉이 아래 · 위로 빛줄기) → 바닥 과녁
    target = mask()
    d = ImageDraw.Draw(target)
    d.ellipse([P(2.5, 23.5), P(29.5, 31.0)], fill=255)
    d.ellipse([P(5.5, 25.0), P(26.5, 29.5)], fill=0)
    d.ellipse([P(11.5, 26.0), P(20.5, 28.5)], fill=255)
    layers = []
    glows = []
    trails = mask()
    for (x, y, L) in ((7.5, 25.0, 9.0), (14.0, 27.0, 10.0), (21.0, 25.5, 9.5), (26.5, 22.5, 8.0), (10.5, 15.0, 7.0), (21.5, 13.0, 7.0), (16.0, 6.0, 5.5)):
        ls, gm = arrow_layers(A, B, (x, y), 110, L, 3.4, fletch=False)
        layers += ls
        glows.append(glow(gm, A, 1.0, 0.6))
        a = math.radians(110)
        line(trails, [(x - math.cos(a) * L, y - math.sin(a) * L), (x - math.cos(a) * (L + 4.0), y - math.sin(a) * (L + 4.0))], 0.7)
    subject = stack(layer(target, mix(B, WHITE, 0.3), B, None, None), layer(trails, mix(A, WHITE, 0.3), A), *layers)
    return finish(subject, glows + [glow(target, B, 1.4, 0.7)], archer_back(A, B))


def archer_focus(A, B):
    rings = mask()
    ring(rings, 16, 16, 13.0, 1.5)
    ring(rings, 16, 16, 7.5, 1.2)
    ticks = mask()
    for (a, b) in (((16, 0.5), (16, 6.5)), ((16, 25.5), (16, 31.5)), ((0.5, 16), (6.5, 16)), ((25.5, 16), (31.5, 16))):
        line(ticks, [a, b], 1.4)
    tip = mask()
    star(tip, 16, 16, 5.5, 0.18, 45)
    star(tip, 16, 16, 3.0, 0.3, 0)
    subject = stack(layer(rings, WHITE, A, WHITE, mix(A, DARK, 0.3)), layer(ticks, WHITE, A), layer(tip, WHITE, B))
    return finish(subject, [glow(rings, A, 1.4, 0.8), glow(tip, B, 1.8, 1.0)], archer_back(A, B))


def archer_ult(A, B):
    # 거대한 빛의 화살이 고리 둘을 꿰뚫음 · 뒤의 빛줄기
    beam = mask()
    poly(beam, [(0.5, 12.5), (24.0, 14.5), (24.0, 17.5), (0.5, 19.5)])
    rings = mask()
    for x, h in ((9.5, 9.5), (17.5, 12.0)):
        d = ImageDraw.Draw(rings)
        d.ellipse([P(x - 3.4, 16 - h), P(x + 3.4, 16 + h)], outline=255, width=int(1.3 * SS))
    shaft = mask()
    poly(shaft, [(4.0, 14.6), (22.5, 14.6), (22.5, 17.4), (4.0, 17.4)])
    head = mask()
    poly(head, [(31.5, 16.0), (21.5, 9.0), (24.0, 16.0), (21.5, 23.0)])
    fl = mask()
    for d0 in (0.0, 2.6):
        line(fl, [(6.5 + d0, 16.0), (2.0 + d0, 11.5)], 1.1)
        line(fl, [(6.5 + d0, 16.0), (2.0 + d0, 20.5)], 1.1)
    sp = mask()
    star(sp, 27.0, 6.0, 2.6)
    star(sp, 25.0, 27.0, 2.2)
    subject = stack(layer(beam, mix(A, WHITE, 0.35), A, None, None, axis=(1, 0)), layer(rings, B, mix(B, WHITE, 0.4)), layer(shaft, WHITE, mix(A, WHITE, 0.4)),
                    layer(fl, WHITE, A), layer(head, WHITE, B, WHITE, mix(B, DARK, 0.3)), layer(sp, WHITE, B))
    return finish(subject, [glow(beam, A, 2.0, 0.9), glow(head, B, 1.6, 1.0)], archer_back(A, B))


ICONS = {
    "warrior_crush": warrior_crush,
    "warrior_charge": warrior_charge,
    "warrior_whirl": warrior_whirl,
    "warrior_spirit": warrior_spirit,
    "warrior_ult": warrior_ult,
    "mage_bolts": mage_bolts,
    "mage_phase": mage_phase,
    "mage_orb": mage_orb,
    "mage_overload": mage_overload,
    "mage_ult": mage_ult,
    "archer_rapid": archer_rapid,
    "archer_wind": archer_wind,
    "archer_rain": archer_rain,
    "archer_focus": archer_focus,
    "archer_ult": archer_ult,
}
