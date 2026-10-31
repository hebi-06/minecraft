#!/usr/bin/env python3
"""0.29.0 직업 스킬 아이콘 — 암살자 · 창술사 · 사신 (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음)."""
import math
import os
import sys

from PIL import ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (SS, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix, poly, ring,  # noqa: E402
                             stack, star, sword_layers)
from job_skill_icons_more import flame_mask  # noqa: E402

N = 32
DARK = (0, 0, 0)
WOOD = (150, 96, 54)
WOOD_LO = (88, 54, 30)


def P(x, y):
    return (x * SS, y * SS)


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def petal(m, cx, cy, ang, L, W):
    """꽃잎: (cx, cy) 에서 ang 도 쪽으로 길이 L · 폭 W (끝이 살짝 패임)"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    left, right = [], []
    for i in range(13):
        t = i / 12
        w = W * (math.sin(math.pi * min(1.0, 0.1 + t * 0.9)) ** 0.7) * (0.5 + 0.5 * t)
        left.append((cx + ux * L * t + px * w, cy + uy * L * t + py * w))
        right.append((cx + ux * L * t - px * w, cy + uy * L * t - py * w))
    poly(m, left + [(cx + ux * L * 0.86, cy + uy * L * 0.86)] + right[::-1])
    return m


def blossom(m, cx, cy, R, rot=0.0):
    for i in range(5):
        petal(m, cx, cy, rot - 90 + i * 72, R, R * 0.42)
    return m


def spear(tail, tip, head=5.0, hw=1.9, shaft=1.3):
    """창: tail (자루 끝) → tip (창끝). 자루 · 잎 모양 창날 (길이 head · 폭 hw) — (자루, 날) 마스크"""
    tx, ty = tail
    hx, hy = tip
    L = math.hypot(hx - tx, hy - ty)
    ux, uy = (hx - tx) / L, (hy - ty) / L
    px, py = -uy, ux
    sh = mask()
    line(sh, [tail, (hx - ux * head * 0.9, hy - uy * head * 0.9)], shaft)
    bl = mask()
    pts_l, pts_r = [], []
    for i in range(11):
        t = i / 10
        d = L - head + head * t
        w = hw * (math.sin(math.pi * (0.15 + 0.85 * t)) ** 0.9) * (1 - 0.25 * t)
        pts_l.append((tx + ux * d + px * w, ty + uy * d + py * w))
        pts_r.append((tx + ux * d - px * w, ty + uy * d - py * w))
    poly(bl, pts_l + pts_r[::-1])
    return sh, bl


def spear_layers(A, B, tail, tip, head=5.0, hw=1.9, shaft=1.3):
    sh, bl = spear(tail, tip, head, hw, shaft)
    return [layer(sh, mix(A, WHITE, 0.15), mix(A, DARK, 0.35)), layer(bl, WHITE, mix(B, A, 0.4), WHITE, mix(A, DARK, 0.3), axis=(0.7, 0.7))], bl


def scythe(hand_lo, top, blade=12.0, bw=3.2, side=1, shaft=1.5):
    """낫: hand_lo (자루 아래끝) → top (자루 위끝), 위끝에서 옆 (side 1 = 왼쪽으로 휨) 으로 휘어 내려오는 날 — (자루, 날, 날 끝 줄) 마스크"""
    lx, ly = hand_lo
    tx, ty = top
    L = math.hypot(tx - lx, ty - ly)
    ux, uy = (tx - lx) / L, (ty - ly) / L
    px, py = uy * side, -ux * side
    sh = mask()
    line(sh, [hand_lo, top], shaft)
    outer, inner = [], []
    for i in range(17):
        t = i / 16
        bend = t * t * 0.5
        x = tx + px * blade * t - ux * blade * bend
        y = ty + py * blade * t - uy * blade * bend
        w = bw * (1 - t) ** 0.75
        outer.append((x, y))
        inner.append((x - ux * w, y - uy * w))
    bl = mask()
    poly(bl, outer + inner[::-1])
    edge = mask()
    line(edge, outer[2:], 0.7)
    return sh, bl, edge


def soul(cx, base_y, w, h, lean=0.0):
    """혼불: 아래 둥근 몸 · 위로 흔들리며 가늘어지는 불꽃 (lean = 끝이 옆으로)"""
    m = mask()
    r = w / 2
    cy = base_y - r
    pts = []
    for i in range(13):
        a = math.pi * i / 12
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    for i in range(1, 9):
        t = i / 8
        pts.append((cx - r * (1 - t) ** 1.1 + lean * t * t, cy - (h - r) * t))
    for i in range(7, 0, -1):
        t = i / 8
        pts.append((cx + r * (1 - t) ** 1.1 + lean * t * t + 0.6 * math.sin(t * 5) * t, cy - (h - r) * t * 0.93))
    poly(m, pts)
    return m


def hood(cx, top_y, w, h):
    """두건 쓴 사신: 뾰족한 두건 · 아래로 넓어지는 옷 (밑단이 찢어짐) — (몸, 얼굴 구멍)"""
    body = mask()
    pts = [(cx, top_y)]
    for i in range(1, 7):
        t = i / 6
        pts.append((cx + w * 0.32 * math.sin(t * math.pi / 2), top_y + h * 0.33 * t))
    n = 8
    for i in range(n + 1):
        t = i / n
        x = cx + w * 0.5 - w * t
        y = top_y + h - (h * 0.07 if i % 2 == 1 else 0)
        pts.append((x, y))
    for i in range(6, 0, -1):
        t = i / 6
        pts.append((cx - w * 0.32 * math.sin(t * math.pi / 2), top_y + h * 0.33 * t))
    poly(body, pts)
    face = mask()
    d = ImageDraw.Draw(face)
    d.ellipse([P(cx - w * 0.15, top_y + h * 0.13), P(cx + w * 0.17, top_y + h * 0.33)], fill=255)
    return body, face


# ------------------------------------------------------------ 암살자 (빛: 자홍 → 짙은 보라)

def assassin_back(A, B):
    return backdrop(mix(B, (10, 2, 16), 0.8), mix(A, (40, 8, 52), 0.55))


def assassin_bloom(A, B):
    # 위오른쪽을 찌른 단검 · 끝에서 터지는 꽃 · 흩날리는 꽃잎
    dag = sword_layers((24.0, 8.0), (6.5, 25.5), blade=2.6, guard=5.2, blade_cols=(STEEL_HI, mix(STEEL, A, 0.25), WHITE, STEEL_LO))
    bl = mask()
    blossom(bl, 25.0, 7.0, 5.2, 10)
    ctr = mask()
    disc(ctr, 25.0, 7.0, 1.4)
    pt = mask()
    for (x, y, a) in ((14.0, 4.5, 30), (29.0, 16.5, 110), (19.5, 15.0, -40), (9.5, 10.5, 60), (27.0, 25.5, 150)):
        petal(pt, x, y, a, 3.4, 1.3)
    subject = stack(layer(pt, mix(A, WHITE, 0.45), A), *dag, layer(bl, mix(A, WHITE, 0.5), A, WHITE, mix(B, DARK, 0.2)), layer(ctr, WHITE, mix(A, WHITE, 0.6)))
    return finish(subject, [glow(bl, A, 2.0, 1.0), glow(pt, A, 1.2, 0.7)], assassin_back(A, B))


def assassin_shade(A, B):
    # 왼쪽의 그림자 분신 (짙은 두건 — 흐린 테두리) → 오른쪽으로 옮겨 간 몸 (밝은 두건 · 단검) · 사이의 빠른 줄 · 분신 발밑 연기
    sb, sf = hood(8.5, 6.0, 12.0, 21.0)
    mb, mf = hood(22.5, 4.5, 12.0, 22.0)
    dag = mask()
    poly(dag, [(30.5, 14.0), (27.0, 17.6), (26.2, 16.8)])
    line(dag, [(26.6, 17.2), (24.8, 19.0)], 1.1)
    lines = mask()
    for y, x0, x1 in ((10.5, 13.0, 17.5), (16.0, 14.5, 17.5), (21.5, 15.0, 17.5)):
        line(lines, [(x0, y), (x1, y)], 1.0)
    smoke = mask()
    for x, y, r in ((3.5, 28.5, 2.6), (8.0, 29.2, 2.9), (12.5, 28.6, 2.4)):
        disc(smoke, x, y, r)
    eyes = mask()
    for cx in (8.5, 22.5):
        disc(eyes, cx - 1.2, 10.0 if cx < 10 else 8.8, 0.75)
        disc(eyes, cx + 1.3, 10.0 if cx < 10 else 8.8, 0.75)
    subject = stack(layer(smoke, mix(B, (110, 80, 130), 0.35), mix(B, DARK, 0.35)), layer(sb, mix(B, A, 0.2), mix(B, DARK, 0.45), mix(B, A, 0.55), None),
                    layer(sf, mix(B, DARK, 0.7), mix(B, DARK, 0.8)), layer(lines, WHITE, A, None, None, axis=(1, 0)),
                    layer(mb, mix(A, WHITE, 0.3), A, WHITE, mix(B, DARK, 0.2), axis=(0.6, 0.8)), layer(mf, mix(B, DARK, 0.55), mix(B, DARK, 0.7)),
                    layer(eyes, WHITE, WHITE), layer(dag, WHITE, STEEL, WHITE, STEEL_LO))
    return finish(subject, [glow(mb, A, 1.8, 0.9), glow(sb, B, 1.4, 0.7)], assassin_back(A, B))


def assassin_daggers(A, B):
    # 0.50.0 요르문간드의 독니 (여덟 칼날을 바꿈 — 단검을 쥐고 S 자로 휘감아 지나가며 베고 독을 남김): 아래왼쪽에서 단검 쪽으로 S 자로 굽이치는 자홍 자국 (뱀처럼 —
    # 끝으로 갈수록 굵음) · 위오른쪽으로 찌르는 단검 · 칼날에서 떨어지는 초록 독 방울 셋
    venom, venom_hi = (88, 214, 64), (196, 255, 150)
    x0, y0, x1, y1 = 3.0, 29.5, 16.4, 16.2
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    pts = []
    for i in range(61):
        u = i / 60
        k = 3.0 * math.sin(2 * math.pi * u) * (0.4 + 0.6 * u)
        pts.append((x0 + dx * u + nx * k, y0 + dy * u + ny * k, 1.2 + 0.9 * u))
    a_side, b_side = [], []
    for i, (x, y, w) in enumerate(pts):
        xa, ya, _ = pts[max(0, i - 1)]
        xb, yb, _ = pts[min(len(pts) - 1, i + 1)]
        tx, ty = xb - xa, yb - ya
        tl = math.hypot(tx, ty) or 1
        px, py = -ty / tl, tx / tl
        a_side.append((x - px * w, y - py * w))
        b_side.append((x + px * w, y + py * w))
    trail = mask()
    poly(trail, a_side + b_side[::-1])
    dag = sword_layers((30.2, 1.8), (16.8, 15.6), blade=3.2, guard=6.4, blade_cols=(STEEL_HI, mix(STEEL, venom, 0.35), WHITE, STEEL_LO))
    drops = mask()
    for (cx, cy, r) in ((25.2, 12.6, 1.5), (28.6, 17.6, 1.25), (22.6, 19.2, 1.05)):
        disc(drops, cx, cy + r * 0.35, r)
        poly(drops, [(cx - r * 0.82, cy + r * 0.1), (cx, cy - r * 1.8), (cx + r * 0.82, cy + r * 0.1)])
    shine = mask()
    star(shine, 29.6, 2.4, 3.0, 0.22)
    subject = stack(layer(trail, mix(A, WHITE, 0.35), mix(B, A, 0.4), WHITE, mix(B, DARK, 0.3), axis=(0.7, -0.7)), *dag,
                    layer(drops, venom_hi, venom, WHITE, mix(venom, DARK, 0.4)), layer(shine, WHITE, WHITE))
    return finish(subject, [glow(trail, A, 1.6, 0.9), glow(drops, venom, 1.3, 1.0)], assassin_back(A, B))


def assassin_veil(A, B):
    # 그림자 연기에서 드러난 두건 · 빛나는 눈 · 초승달
    moon = mask()
    disc(moon, 24.5, 7.5, 5.0)
    cut = mask()
    disc(cut, 26.8, 6.1, 4.4)
    moon = ImageChops.subtract(moon, cut)
    body, face = hood(14.0, 4.0, 18.0, 23.0)
    face = mask()
    d = ImageDraw.Draw(face)
    d.ellipse([P(10.4, 8.0), P(18.0, 14.2)], fill=255)
    smoke = mask()
    for x, y, r in ((4.5, 28.0, 3.4), (10.5, 29.0, 3.4), (17.0, 28.6, 3.4), (23.5, 28.2, 3.6), (28.5, 26.8, 2.8)):
        disc(smoke, x, y, r)
    eyes = mask()
    disc(eyes, 12.4, 11.4, 1.1)
    disc(eyes, 16.0, 11.4, 1.1)
    subject = stack(layer(moon, mix(A, WHITE, 0.6), A), layer(body, mix(B, A, 0.3), mix(B, DARK, 0.45), mix(B, A, 0.65), None),
                    layer(face, mix(B, DARK, 0.75), mix(B, DARK, 0.85)), layer(eyes, WHITE, mix(A, WHITE, 0.5)),
                    layer(smoke, mix(B, (150, 120, 170), 0.35), mix(B, DARK, 0.3)))
    return finish(subject, [glow(eyes, A, 1.3, 1.0), glow(moon, A, 1.6, 0.8)], assassin_back(A, B))


def assassin_ult(A, B):
    # 밤 (위오른쪽 초승달 · 별) · 화면을 가로지르는 X 자 두 베기 · 가운데 번쩍 · 꽃잎
    moon = mask()
    disc(moon, 25.0, 7.0, 5.0)
    cut = mask()
    disc(cut, 27.2, 5.4, 4.4)
    moon = ImageChops.subtract(moon, cut)
    x1 = mask()
    crescent(x1, 58.4, -26.4, 60.0, 3.6, 108, 162)
    x2 = mask()
    crescent(x2, -26.4, -26.4, 60.0, 3.6, 18, 72)
    sp = mask()
    star(sp, 16.0, 16.0, 6.5, 0.15, 0)
    star(sp, 6.0, 5.0, 1.5)
    star(sp, 14.5, 3.5, 1.2)
    pt = mask()
    for (x, y, a) in ((5.5, 26.0, 40), (26.5, 25.0, 130), (22.0, 30.0, 70)):
        petal(pt, x, y, a, 3.2, 1.3)
    subject = stack(layer(moon, mix(A, WHITE, 0.55), A), layer(pt, mix(A, WHITE, 0.4), A), layer(x1, WHITE, A, WHITE, mix(B, DARK, 0.2), axis=(1, 1)),
                    layer(x2, WHITE, A, WHITE, mix(B, DARK, 0.2), axis=(-1, 1)), layer(sp, WHITE, A))
    return finish(subject, [glow(lighter(x1, x2), A, 1.8, 1.0), glow(sp, A, 2.0, 1.0)], assassin_back(A, B))


# ------------------------------------------------------------ 창술사 (빛: 금빛 → 흰빛)

def lancer_back(A, B):
    return backdrop(mix(A, (24, 14, 2), 0.86), mix(A, (90, 60, 12), 0.55))


def lancer_triple(A, B):
    # 오른쪽으로 찌른 창 셋 (아래로 갈수록 앞으로) · 뒤의 빠른 줄 · 맨 앞 창끝 번쩍
    layers, glows = [], []
    for k, (y, x1) in enumerate(((8.0, 23.0), (16.0, 26.5), (24.0, 30.0))):
        ls, bl = spear_layers(A, B, (x1 - 21.0, y + 2.5), (x1, y - 1.0), head=5.5, hw=1.9, shaft=1.2)
        layers += ls
        glows.append(glow(bl, B, 1.2, 0.9))
    lines = mask()
    for y, x0, x1 in ((5.5, 0.5, 6.0), (13.5, 1.0, 7.5), (21.5, 2.0, 9.5), (28.5, 3.0, 10.0)):
        line(lines, [(x0, y), (x1, y)], 0.9)
    sp = mask()
    star(sp, 29.5, 23.0, 3.6, 0.2, 0)
    subject = stack(layer(lines, mix(A, WHITE, 0.5), A, None, None, axis=(1, 0)), *layers, layer(sp, WHITE, B))
    return finish(subject, glows + [glow(sp, A, 1.6, 1.0)], lancer_back(A, B))


def lancer_vault(A, B):
    # 왼쪽 아래 짚은 창 · 크게 넘어가는 호 (점선) · 오른쪽 아래 내려찍은 자리의 번쩍 · 땅
    sh, bl = spear((3.0, 29.0), (9.5, 12.0), head=4.6, hw=1.7, shaft=1.2)
    arc = mask()
    for i in range(9):
        t0, t1 = i / 9, (i + 0.55) / 9
        pts = []
        for t in (t0, (t0 + t1) / 2, t1):
            x = 9.0 + 18.0 * t
            y = 26.0 - 22.0 * 4 * t * (1 - t) * 0.85
            pts.append((x, y))
        line(arc, pts, 1.3)
    ground = mask()
    poly(ground, [(1.0, 29.0), (31.0, 29.0), (31.0, 30.2), (1.0, 30.2)])
    hit = mask()
    star(hit, 26.5, 26.0, 5.6, 0.16, 0)
    crack = mask()
    for pts in (((26.5, 28.5), (22.5, 30.8)), ((26.5, 28.5), (30.0, 31.0))):
        line(crack, list(pts), 0.9)
    subject = stack(layer(ground, mix(A, (60, 40, 20), 0.6), mix(A, DARK, 0.6)), layer(arc, WHITE, A), layer(sh, mix(A, WHITE, 0.15), mix(A, DARK, 0.35)),
                    layer(bl, WHITE, mix(B, A, 0.4), WHITE, mix(A, DARK, 0.3)), layer(crack, WHITE, A), layer(hit, WHITE, B))
    return finish(subject, [glow(hit, A, 2.0, 1.0), glow(arc, A, 1.2, 0.7)], lancer_back(A, B))


def lancer_cyclone(A, B):
    # 비스듬히 도는 양끝 창 · 둘레의 도는 자국 둘
    trails = mask()
    crescent(trails, 16.0, 16.0, 14.0, 3.6, 200, 300)
    crescent(trails, 16.0, 16.0, 14.0, 3.6, 20, 120)
    sh = mask()
    line(sh, [(7.0, 25.0), (25.0, 7.0)], 1.4)
    bl = mask()
    for (tx, ty, a) in ((27.5, 4.5, -45), (4.5, 27.5, 135)):
        r = math.radians(a)
        ux, uy = math.cos(r), math.sin(r)
        px, py = -uy, ux
        pts_l, pts_r = [], []
        for i in range(9):
            t = i / 8
            d = -5.5 + 5.5 * t
            w = 1.9 * (math.sin(math.pi * (0.15 + 0.85 * t)) ** 0.9) * (1 - 0.25 * t)
            pts_l.append((tx + ux * d + px * w, ty + uy * d + py * w))
            pts_r.append((tx + ux * d - px * w, ty + uy * d - py * w))
        poly(bl, pts_l + pts_r[::-1])
    hub = mask()
    disc(hub, 16.0, 16.0, 2.0)
    subject = stack(layer(trails, WHITE, A, None, None, axis=(0.7, 0.7)), layer(sh, mix(A, WHITE, 0.15), mix(A, DARK, 0.35)),
                    layer(bl, WHITE, mix(B, A, 0.4), WHITE, mix(A, DARK, 0.3), axis=(0.7, 0.7)), layer(hub, WHITE, A))
    return finish(subject, [glow(trails, A, 1.6, 0.9), glow(bl, B, 1.4, 0.9)], lancer_back(A, B))


def lancer_wall(A, B):
    # 앞으로 펼친 창끝 다섯 (부채꼴) · 아래의 둥근 막이 (방패 같은 호)
    layers, glows = [], []
    for k in range(5):
        a = math.radians(-90 + (k - 2) * 22)
        tip = (16.0 + math.cos(a) * 14.0, 26.0 + math.sin(a) * 23.0)
        tail = (16.0 + math.cos(a) * 3.0, 26.0 + math.sin(a) * 3.0)
        ls, bl = spear_layers(A, B, tail, tip, head=4.8, hw=1.6, shaft=1.1)
        layers += ls
        glows.append(glow(bl, B, 1.1, 0.8))
    guard = mask()
    crescent(guard, 16.0, 34.0, 13.0, 3.0, -160, -20, taper=False)
    subject = stack(*layers, layer(guard, mix(A, WHITE, 0.35), A, WHITE, mix(A, DARK, 0.35)))
    return finish(subject, glows + [glow(guard, A, 1.6, 0.9)], lancer_back(A, B))


def lancer_ult(A, B):
    # 0.50.0 흐레스벨그의 발톱 (천 개의 창을 바꿈 — 제 창으로 찌르기 폭풍 → 돌진해 꿰뚫음): 왼쪽 아래 → 오른쪽 위로 내지른 큰 창 · 창끝에서 앞으로 벌어지는 원뿔 충격
    # (겹친 호 둘) · 창 양옆의 찌르기 줄 넷 (폭풍) · 창끝 반짝임
    ox, oy = 3.0, 29.0
    tip = (19.0, 13.0)
    L = math.hypot(tip[0] - ox, tip[1] - oy)
    ux, uy = (tip[0] - ox) / L, (tip[1] - oy) / L
    px, py = -uy, ux
    base = math.degrees(math.atan2(uy, ux))
    cone = mask()
    crescent(cone, tip[0] - ux * 1.5, tip[1] - uy * 1.5, 5.4, 1.5, base - 78, base + 78)
    crescent(cone, tip[0] - ux * 1.5, tip[1] - uy * 1.5, 9.6, 1.5, base - 62, base + 62)
    jabs = mask()
    for (off, d0, d1) in ((-4.2, 9.0, 16.5), (4.2, 9.0, 16.5), (-7.4, 5.5, 11.5), (7.4, 5.5, 11.5)):
        line(jabs, [(ox + px * off + ux * d0, oy + py * off + uy * d0), (ox + px * off + ux * d1, oy + py * off + uy * d1)], 0.9)
    main, mainb = spear_layers(A, B, (ox, oy), tip, head=7.6, hw=2.8, shaft=1.7)
    sp = mask()
    star(sp, tip[0] + ux * 0.4, tip[1] + uy * 0.4, 3.0, 0.2, 45)
    subject = stack(layer(jabs, mix(A, WHITE, 0.55), A, None, None, axis=(ux, uy)), layer(cone, WHITE, mix(B, A, 0.4), WHITE, mix(A, DARK, 0.3), axis=(ux, uy)),
                    *main, layer(sp, WHITE, B))
    return finish(subject, [glow(cone, A, 1.6, 0.9), glow(mainb, A, 1.8, 1.0), glow(sp, A, 1.6, 1.0)], lancer_back(A, B))


# ------------------------------------------------------------ 사신 (빛: 청록 → 짙은 청록)

def reaper_back(A, B):
    return backdrop(mix(B, (2, 10, 12), 0.8), mix(B, (20, 70, 72), 0.45))


def reaper_reap(A, B):
    # 크게 휘두른 낫 (날이 왼쪽 위) · 큰 초승달 자국 · 끌려오는 혼불 둘
    swing = mask()
    crescent(swing, 18.0, 20.0, 14.5, 3.6, -200, -40)
    sh, bl, edge = scythe((26.5, 30.0), (20.0, 4.0), blade=13.0, bw=3.4, side=1, shaft=1.6)
    s1 = soul(6.5, 27.0, 3.6, 6.5, 1.2)
    s2 = soul(12.0, 30.5, 3.0, 5.0, -1.0)
    subject = stack(layer(swing, mix(A, WHITE, 0.35), B, None, None, axis=(1, -1)), layer(lighter(s1, s2), mix(A, WHITE, 0.4), A),
                    layer(sh, WOOD, WOOD_LO, mix(WOOD, WHITE, 0.3), None), layer(bl, mix(STEEL_HI, A, 0.2), mix(STEEL, B, 0.3), WHITE, STEEL_LO, axis=(1, 1)),
                    layer(edge, WHITE, WHITE))
    return finish(subject, [glow(swing, A, 1.6, 0.9), glow(lighter(s1, s2), A, 1.4, 0.9)], reaper_back(A, B))


def reaper_wraith(A, B):
    # 오른쪽으로 내닫는 망령 (두건 · 뒤로 흩어지는 자락) · 뒤의 빠른 줄 · 혼불
    body = mask()
    pts = [(26.0, 6.0)]
    for i in range(1, 7):
        t = i / 6
        pts.append((26.0 + 4.0 * math.sin(t * math.pi / 2), 6.0 + 9.0 * t))
    for (x, y) in ((27.0, 22.0), (20.0, 24.0), (14.0, 21.5), (8.0, 23.5), (3.0, 20.0), (9.0, 18.0), (5.0, 14.5), (12.0, 14.0), (16.5, 9.0)):
        pts.append((x, y))
    poly(body, pts)
    face = mask()
    d = ImageDraw.Draw(face)
    d.ellipse([P(24.0, 8.5), P(28.6, 13.5)], fill=255)
    eyes = mask()
    disc(eyes, 25.6, 11.0, 0.7)
    disc(eyes, 27.5, 11.0, 0.7)
    lines = mask()
    for y, x0, x1 in ((9.0, 4.0, 12.0), (27.5, 2.0, 12.0), (5.0, 8.0, 15.0)):
        line(lines, [(x0, y), (x1, y)], 0.9)
    s1 = soul(20.0, 30.5, 3.2, 5.5, -1.0)
    subject = stack(layer(lines, mix(A, WHITE, 0.4), A, None, None, axis=(1, 0)), layer(body, mix(A, WHITE, 0.2), mix(B, A, 0.3), WHITE, mix(B, DARK, 0.2), axis=(1, 0)),
                    layer(face, mix(B, DARK, 0.6), mix(B, DARK, 0.7)), layer(eyes, WHITE, WHITE), layer(s1, mix(A, WHITE, 0.4), A))
    return finish(subject, [glow(body, A, 1.8, 0.9)], reaper_back(A, B))


def reaper_burst(A, B):
    # 가운데의 큰 혼불이 터짐 (위로 갈라진 불꽃 혀) · 양옆으로 퍼지는 충격 호 둘 · 바깥으로 튀는 작은 혼불 셋 · 불티
    big = flame_mask(16.0, 27.0, 13.0, 21.0, 3, 0.7)
    inner = flame_mask(16.0, 25.5, 6.5, 11.0, 3, 1.9)
    arcs = mask()
    crescent(arcs, 16.0, 17.0, 14.5, 2.6, 140, 220)
    crescent(arcs, 16.0, 17.0, 14.5, 2.6, -40, 40)
    small = lighter(flame_mask(4.5, 11.0, 3.4, 6.0, 2, 0.3), flame_mask(27.5, 10.0, 3.4, 6.0, 2, 1.1), flame_mask(26.0, 30.0, 3.0, 5.0, 2, 2.0))
    sp = mask()
    for x, y, r in ((7.0, 27.0, 1.8), (24.5, 3.5, 1.6), (5.0, 18.0, 1.4)):
        star(sp, x, y, r)
    subject = stack(layer(arcs, mix(A, WHITE, 0.3), A, None, None), layer(big, mix(A, WHITE, 0.25), mix(B, A, 0.35), mix(A, WHITE, 0.6), mix(B, DARK, 0.3), axis=(0, -1)),
                    layer(inner, WHITE, mix(A, WHITE, 0.5)), layer(small, mix(A, WHITE, 0.4), A), layer(sp, WHITE, A))
    return finish(subject, [glow(big, A, 2.2, 1.0), glow(arcs, A, 1.4, 0.8)], reaper_back(A, B))


def reaper_avatar(A, B):
    # 커다란 두건 사신 (빛나는 눈) · 오른쪽에 세워 든 낫 (날이 두건 위로)
    body, face = hood(12.5, 6.0, 21.0, 25.0)
    face = mask()
    d = ImageDraw.Draw(face)
    d.ellipse([P(9.0, 10.0), P(16.5, 16.2)], fill=255)
    eyes = mask()
    disc(eyes, 11.2, 13.2, 1.0)
    disc(eyes, 14.6, 13.2, 1.0)
    sh, bl, edge = scythe((27.5, 30.5), (26.0, 2.5), blade=10.5, bw=2.8, side=1, shaft=1.4)
    subject = stack(layer(body, mix(B, A, 0.35), mix(B, DARK, 0.4), mix(A, WHITE, 0.2), None), layer(face, mix(B, DARK, 0.75), mix(B, DARK, 0.85)),
                    layer(eyes, WHITE, A), layer(sh, WOOD, WOOD_LO, mix(WOOD, WHITE, 0.3), None),
                    layer(bl, mix(STEEL_HI, A, 0.25), mix(STEEL, B, 0.3), WHITE, STEEL_LO, axis=(1, 1)), layer(edge, WHITE, WHITE))
    return finish(subject, [glow(body, A, 1.8, 0.7), glow(eyes, A, 1.0, 1.0)], reaper_back(A, B))


def reaper_ult(A, B):
    # 명계의 문 (기둥 둘 · 아치) · 안의 소용돌이 · 아래에서 솟는 망자의 손 둘
    gate = mask()
    poly(gate, [(3.0, 30.0), (8.0, 30.0), (8.0, 9.0), (3.0, 9.0)])
    poly(gate, [(24.0, 30.0), (29.0, 30.0), (29.0, 9.0), (24.0, 9.0)])
    poly(gate, [(2.0, 9.5), (5.5, 5.0), (9.0, 9.5)])
    poly(gate, [(23.0, 9.5), (26.5, 5.0), (30.0, 9.5)])
    arch = mask()
    crescent(arch, 16.0, 13.0, 10.5, 2.4, -180, 0, taper=False)
    swirl = mask()
    for i in range(3):
        a0 = -90 + i * 120
        crescent(swirl, 16.0, 19.0, 6.5, 2.0, a0, a0 + 110)
    hole = mask()
    d = ImageDraw.Draw(hole)
    d.ellipse([P(9.0, 11.0), P(23.0, 29.5)], fill=255)
    hands = mask()
    for hx in (11.5, 20.5):
        line(hands, [(hx, 31.0), (hx, 26.0)], 1.6)
        for dx in (-1.6, -0.5, 0.6, 1.7):
            line(hands, [(hx + dx * 0.6, 26.0), (hx + dx, 22.8)], 0.7)
    subject = stack(layer(hole, mix(B, DARK, 0.55), mix(B, DARK, 0.75)), layer(swirl, mix(A, WHITE, 0.3), A),
                    layer(gate, mix(B, A, 0.3), mix(B, DARK, 0.4), mix(A, WHITE, 0.2), mix(B, DARK, 0.6)), layer(arch, mix(A, WHITE, 0.25), mix(B, A, 0.5)),
                    layer(hands, mix(A, WHITE, 0.55), A))
    return finish(subject, [glow(swirl, A, 1.8, 1.0), glow(hands, A, 1.2, 0.8)], reaper_back(A, B))


ICONS = {
    "assassin_bloom": assassin_bloom,
    "assassin_shade": assassin_shade,
    "assassin_daggers": assassin_daggers,
    "assassin_veil": assassin_veil,
    "assassin_ult": assassin_ult,
    "lancer_triple": lancer_triple,
    "lancer_vault": lancer_vault,
    "lancer_cyclone": lancer_cyclone,
    "lancer_wall": lancer_wall,
    "lancer_ult": lancer_ult,
    "reaper_reap": reaper_reap,
    "reaper_wraith": reaper_wraith,
    "reaper_burst": reaper_burst,
    "reaper_avatar": reaper_avatar,
    "reaper_ult": reaper_ult,
}
