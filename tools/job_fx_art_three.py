#!/usr/bin/env python3
"""0.29.0 직업 스킬 연출 그림 — 암살자 · 창술사 · 사신 (메이플식 2D 그림, tools/job_fx_art.py 와 같은 꼴: 흰색 = 알파 · 빛 장면들 다음에 심지 장면들).
tools/job_fx_art.py 가 함께 부름 (python3 tools/job_fx_art.py [--sheet <폴더>]).

- rfx_jpetal   꽃 · 꽃잎 (카메라를 봄, 64×64 × 8장): 0 ~ 3 = 다섯 꽃잎 꽃이 조금씩 돌며 숨 쉼 (이어 돌림 — 암살자 표식), 4 ~ 7 = 꽃잎 하나가 팔랑임 (낙화 · 흑야)
- rfx_jdagger  도는 비수 (카메라를 봄 — 화면에서 돎, 128×128 × 4장): 날 · 코등이 · 자루 + 날 끝이 그리는 호 (귀환 비수)
- rfx_jsmoke   그림자 연기 (카메라를 봄, 128×128 × 8장): 뭉게뭉게 퍼지며 사라지는 연기 (잔영 · 암영 · 흑야 · 사신)
- rfx_jcross   엇갈린 두 베기 (카메라를 봄, 128×128 × 6장): X 자로 차례로 그어지고 가운데 번쩍 → 가늘어지며 사라짐 (낙화 · 흑야 · 등 뒤 치기)
- rfx_jstab    찌르기 번쩍 (카메라를 봄, 128×128 × 6장): 길쭉한 창끝 빛 · 가로 충격 줄 · 퍼지는 고리 · 뻗는 줄 (창술사 — 1인칭 찌르기)
- rfx_jspin    도는 창 (면이 방향 쪽, 192×192 × 8장 — 이어 돌림): 양끝에 창날이 달린 자루가 돌며 날 끝이 꼬리를 그림 (선회창)
- rfx_jspear   빛의 창 (방향으로 누움 — 오른쪽이 창끝, 256×64 × 4장 — 이어 돌림): 잎 모양 창날 · 자루 · 뒤의 빛줄기 · 흐르는 반짝임 (천창유성 · 장대 도약 · 창벽)
- rfx_jsoul    혼불 (카메라를 봄, 64×64 × 6장 — 이어 돌림): 아래가 둥글고 위로 흔들리며 가늘어지는 불꽃 (사신 「혼」)
- rfx_jreaper  사신 그림자 (세워 카메라 쪽으로 돎, 192×256 × 8장): 0 ~ 3 = 두건 · 긴 옷 · 큰 낫을 든 채 흔들림 (이어 돌림), 4 ~ 7 = 낫을 크게 휘두름 (사신 현신)
- rfx_jgate    명계의 문 (세워 카메라 쪽으로 돎, 256×256 × 8장): 0 ~ 2 = 땅에서 솟음, 3 ~ 5 = 문짝이 열리고 안에서 소용돌이 (이어 돌림), 6 ~ 7 = 닫힘 (황천문)
- rfx_jhand    망자의 손 (세워 카메라 쪽으로 돎, 64×128 × 6장): 땅에서 솟아 손가락을 펴고 → 움켜쥐고 → 가라앉음 (황천문)
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음). 룬 문양 · 마법진은 그리지 않음.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_fx_art import SS, Cell, blur, ease_out, star  # noqa: E402
from job_fx_art_more import arc_band, glowify, two_sheets  # noqa: E402

TAU = math.tau


def taper_band(m, pts, w0, w1, v=255, power=0.7):
    """줄 pts 를 따라 굵기 w0 (처음) → w1 (끝) — 가운데가 sin 으로 부푼 띠 (양 끝 뾰족)"""
    n = len(pts)
    if n < 2:
        return
    left, right = [], []
    for i in range(n):
        x, y = pts[i]
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n - 1:
            dx, dy = x - pts[i - 1][0], y - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        L = math.hypot(dx, dy) or 1
        px, py = -dy / L, dx / L
        t = i / (n - 1)
        w = (w0 + (w1 - w0) * t) * (math.sin(math.pi * t) ** power)
        left.append((x + px * w, y + py * w))
        right.append((x - px * w, y - py * w))
    m.poly(left + right[::-1], v)


# ------------------------------------------------------------ 꽃 · 꽃잎 (rfx_jpetal)

def petal_poly(cx, cy, ang, L, W, base=0.0):
    """꽃잎 하나: (cx, cy) 에서 ang 쪽으로 base 떨어진 곳부터 길이 L · 폭 W (끝이 살짝 패임)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    left, right = [], []
    n = 16
    for i in range(n + 1):
        t = i / n
        w = W * (math.sin(math.pi * min(1.0, 0.08 + t * 0.92)) ** 0.7) * (0.5 + 0.5 * t)
        d = base + L * t
        left.append((cx + ux * d + px * w, cy + uy * d + py * w))
        right.append((cx + ux * d - px * w, cy + uy * d - py * w))
    notch = (cx + ux * (base + L * 0.88), cy + uy * (base + L * 0.88))
    return left + [notch] + right[::-1]


def petal_frame(f, frames=8, S=64, core=False):
    m = Cell(S, S)
    c = S / 2
    if f < 4:
        rot = math.radians(f * 9) - math.pi / 2
        k = 1 + 0.06 * math.sin(f * math.pi / 2)
        R = S * 0.36 * k
        for i in range(5):
            a = rot + i * TAU / 5
            m.poly(petal_poly(c, c, a, R * (0.82 if core else 1.0), S * (0.1 if core else 0.15), S * 0.02), 235 if core else 255)
        m.disc(c, c, S * (0.07 if core else 0.11), 255)
        if core:
            for i in range(5):
                a = rot + (i + 0.5) * TAU / 5
                m.disc(c + math.cos(a) * S * 0.13, c + math.sin(a) * S * 0.13, S * 0.022, 255)
    else:
        g = f - 4
        flip = abs(math.cos(g * math.pi / 4))
        a = math.radians(-60 + g * 25)
        L = S * 0.72
        cx = c - math.cos(a) * L / 2
        cy = c - math.sin(a) * L / 2
        m.poly(petal_poly(cx, cy, a, L * (0.86 if core else 1.0), S * (0.1 if core else 0.17) * (0.3 + 0.7 * flip)), 255)
    out = m.out()
    return glowify(out, 0.78, 2, 1.15) if not core else out


# ------------------------------------------------------------ 도는 비수 (rfx_jdagger — 날이 위오른쪽, 화면에서 시계 반대로 돎)

def dagger_frame(f, frames=4, S=128, core=False):
    m = Cell(S, S)
    c = S / 2
    a = math.radians(-45 + f * 2)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux

    def at(d, s=0.0):
        return (c + ux * d + px * s, c + uy * d + py * s)

    # 도는 자국: 날 끝 · 자루 끝이 그리는 호 (날 끝 뒤쪽으로 흐려짐 — 화면에서 시계 반대로 도니 각이 커지는 쪽이 뒤)
    if not core:
        m.ring(c, c, S * 0.43, S * 0.012, 60)
        arc_band(m, c, c, S * 0.43, a + math.radians(95), a + math.radians(5), S * 0.004, S * 0.045, 20, 210, n=30)
        arc_band(m, c, c, S * 0.27, a + math.radians(275), a + math.radians(185), S * 0.003, S * 0.03, 20, 160, n=24)
    tip = S * 0.45
    gd = -S * 0.02
    bw = S * (0.075 if not core else 0.04)
    m.poly([at(tip), at(gd + S * 0.14, bw), at(gd, bw * 0.75), at(gd, -bw * 0.75), at(gd + S * 0.14, -bw)], 255)
    if core:
        m.line([at(tip * 0.9), at(gd + S * 0.04)], S * 0.012, 255)
    gw = S * (0.12 if not core else 0.09)
    m.line([at(gd, gw), at(gd, -gw)], S * (0.045 if not core else 0.022), 255)
    hd = -S * 0.25
    m.line([at(gd), at(hd)], S * (0.05 if not core else 0.026), 225)
    m.disc(*at(hd - S * 0.02), S * (0.04 if not core else 0.022), 255)
    star(m, *at(tip * 0.98), S * (0.07 if core else 0.09), 0.2, 45 * f, 255)
    out = m.out()
    return glowify(out, 0.8, 3, 1.2) if not core else out


# ------------------------------------------------------------ 그림자 연기 (rfx_jsmoke)

def smoke_frame(f, frames=8, S=128, core=False):
    rnd = random.Random(7)
    m = Cell(S, S)
    c = S / 2
    p = f / (frames - 1)
    grow = 0.5 + 0.62 * ease_out(p)
    v = 255 * (1 - p ** 1.5)
    # 가운데 한 덩이 + 둘레 일곱 덩이 (조금씩 다른 크기 · 위로 조금 뜸)
    puffs = [(0.0, 0.0, 0.2)]
    for i in range(7):
        a = i * TAU / 7 + rnd.uniform(-0.3, 0.3)
        d = rnd.uniform(0.13, 0.2)
        puffs.append((math.cos(a) * d, math.sin(a) * d * 0.85, rnd.uniform(0.11, 0.17)))
    for dx, dy, r in puffs:
        x = c + dx * S * grow * 1.3
        y = c + dy * S * grow * 1.3 - p * S * 0.06
        m.disc(x, y, r * S * grow * (0.7 if core else 1.0), v * (0.9 if core else 1.0))
    out = m.out()
    return blur(out, 5 if not core else 3)


# ------------------------------------------------------------ 엇갈린 두 베기 (rfx_jcross)

def cut_points(S, p0, p1, sag, n=28):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    return [(x0 + dx * t + nx * sag * math.sin(math.pi * t), y0 + dy * t + ny * sag * math.sin(math.pi * t)) for t in (i / n for i in range(n + 1))]


def cross_frame(f, frames=6, S=128, core=False):
    m = Cell(S, S)
    c = S / 2
    A = cut_points(S, (S * 0.13, S * 0.16), (S * 0.87, S * 0.84), S * 0.06)
    B = cut_points(S, (S * 0.87, S * 0.16), (S * 0.13, S * 0.84), -S * 0.06)
    # 장마다: (A 머리, A 꼬리, B 머리, B 꼬리, 굵기, 밝기)
    st = [(0.6, 0.0, 0.0, 0.0, 1.0, 255), (1.0, 0.0, 0.55, 0.0, 1.0, 255), (1.0, 0.05, 1.0, 0.0, 1.0, 255), (1.0, 0.12, 1.0, 0.08, 0.78, 235),
          (0.96, 0.2, 0.96, 0.16, 0.52, 180), (0.92, 0.3, 0.92, 0.26, 0.3, 115)][f]
    w = S * (0.055 if not core else 0.028) * st[4]
    for pts, head, tail in ((A, st[0], st[1]), (B, st[2], st[3])):
        if head <= tail + 0.02:
            continue
        i0, i1 = int(tail * (len(pts) - 1)), max(int(tail * (len(pts) - 1)) + 2, int(head * (len(pts) - 1)) + 1)
        taper_band(m, pts[i0:i1], w * 0.6, w * 1.2, st[5], 0.6)
    if f == 2 or f == 3:
        star(m, c, c, S * (0.2 if f == 2 else 0.14) * (0.8 if core else 1.0), 0.16, 45, 255)
    out = m.out()
    return glowify(out, 0.78, 3, 1.25) if not core else out


# ------------------------------------------------------------ 찌르기 번쩍 (rfx_jstab)

def stab_frame(f, frames=6, S=128, core=False):
    m = Cell(S, S)
    c = S / 2
    k = [0.45, 0.85, 1.0, 0.92, 0.75, 0.5][f]
    v = [255, 255, 255, 220, 160, 100][f]
    # 길쭉한 창끝 빛 (세로)
    h = S * 0.44 * k
    w = S * 0.085 * k * (0.55 if core else 1.0)
    m.poly([(c, c - h), (c + w, c - h * 0.12), (c, c + h * 0.5), (c - w, c - h * 0.12)], v)
    # 가로 충격: 가운데가 굵고 양끝이 뾰족한 렌즈
    if f >= 1:
        lw = S * 0.42 * k
        lt = S * (0.022 if core else 0.04) * (1.25 - f * 0.15)
        m.poly([(c - lw, c), (c - lw * 0.3, c - lt), (c + lw * 0.3, c - lt), (c + lw, c), (c + lw * 0.3, c + lt), (c - lw * 0.3, c + lt)], v * 0.9)
    # 충격파: 굵고 흐린 고리 (빛 장면만 — 흐려져 둥근 빛으로)
    if not core and 1 <= f <= 4:
        m.ring(c, c, S * (0.12 + 0.07 * f), S * (0.07 - 0.01 * f), v * 0.45)
    # 튀는 불티: 아무렇게나 기운 작은 반짝임 다섯 (가운데에서 멀어짐)
    if f >= 2:
        rnd = random.Random(31)
        for i in range(5):
            a = rnd.uniform(0, TAU)
            r = S * (0.16 + 0.07 * f) * rnd.uniform(0.85, 1.15)
            star(m, c + math.cos(a) * r, c + math.sin(a) * r, S * (0.035 if core else 0.05) * (1.3 - f * 0.15), 0.22, rnd.uniform(0, 45), v)
    out = m.out()
    return glowify(out, 0.8, 4 if not core else 3, 1.3) if not core else out


# ------------------------------------------------------------ 도는 창 (rfx_jspin — 땅에 눕힘, 이어 돌림)

def spear_head(m, cx, cy, ang, r0, r1, hw, v=255):
    """잎 모양 창날: 가운데에서 ang 쪽 r0 → r1 (폭 hw)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    left, right = [], []
    for i in range(13):
        t = i / 12
        d = r0 + (r1 - r0) * t
        w = hw * (math.sin(math.pi * (0.15 + 0.85 * t)) ** 0.85) * (1 - 0.25 * t)
        left.append((cx + ux * d + px * w, cy + uy * d + py * w))
        right.append((cx + ux * d - px * w, cy + uy * d - py * w))
    m.poly(left + right[::-1], v)


def spin_frame(f, frames=8, S=192, core=False):
    m = Cell(S, S)
    c = S / 2
    R = S * 0.43
    base = math.radians(f * 180 / frames)
    for side in (0.0, math.pi):
        a0 = base + side
        arc_band(m, c, c, R * 0.88, a0 - math.radians(125), a0 - math.radians(4), S * 0.006, S * (0.09 if not core else 0.035), 10, 220, n=36)
    ux, uy = math.cos(base), math.sin(base)
    m.line([(c - ux * R * 0.64, c - uy * R * 0.64), (c + ux * R * 0.64, c + uy * R * 0.64)], S * (0.026 if not core else 0.013), 255)
    for side in (0.0, math.pi):
        spear_head(m, c, c, base + side, R * 0.6, R, S * (0.05 if not core else 0.028), 255)
    m.disc(c, c, S * (0.035 if not core else 0.02), 255)
    out = m.out()
    return glowify(out, 0.75, 4, 1.2) if not core else out


# ------------------------------------------------------------ 빛의 창 (rfx_jspear — 오른쪽이 창끝)

def spear_frame(f, frames=4, W=256, H=64, core=False):
    m = Cell(W, H)
    cy = H / 2
    if not core:
        m.poly([(W * 0.78, cy - H * 0.13), (W * 0.0, cy), (W * 0.78, cy + H * 0.13)], 90)
        rnd = random.Random(f * 5 + 1)
        for _ in range(4):
            y = cy + rnd.uniform(-0.32, 0.32) * H
            x0 = rnd.uniform(0.0, 0.3) * W
            m.line([(x0, y), (x0 + W * rnd.uniform(0.15, 0.35), y)], H * 0.03, 120)
    m.line([(W * 0.05, cy), (W * 0.77, cy)], H * (0.075 if not core else 0.038), 255)
    tip, base = W * 0.985, W * 0.74
    hw = H * (0.22 if not core else 0.12)
    top, bot = [], []
    for i in range(13):
        t = i / 12
        x = base + (tip - base) * t
        w = hw * (math.sin(math.pi * (0.12 + 0.88 * t)) ** 0.9) * (1 - 0.3 * t)
        top.append((x, cy - w))
        bot.append((x, cy + w))
    m.poly(top + bot[::-1], 255)
    m.line([(base, cy - H * (0.24 if not core else 0.16)), (base, cy + H * (0.24 if not core else 0.16))], W * (0.012 if not core else 0.007), 255)
    star(m, W * (0.28 + 0.13 * f), cy, H * (0.22 if core else 0.3), 0.18, 0, 255)
    out = m.out()
    return glowify(out, 0.8, 3, 1.25) if not core else out


# ------------------------------------------------------------ 혼불 (rfx_jsoul — 위로 흔들리는 불꽃)

def soul_frame(f, frames=6, S=64, core=False):
    m = Cell(S, S)
    c = S / 2
    ph = f * TAU / frames
    r = S * (0.2 if not core else 0.11)
    cy0 = S * (0.68 if not core else 0.7)
    tipy = S * (0.08 if not core else 0.3)
    sway = S * 0.07 * math.sin(ph)
    pts = []
    for i in range(25):
        a = math.pi * i / 24
        pts.append((c + math.cos(a) * r, cy0 + math.sin(a) * r))
    for i in range(1, 12):
        t = i / 11
        pts.append((c - r * (1 - t) ** 1.15 + sway * t * t + S * 0.03 * math.sin(ph * 2 + t * 6) * t, cy0 - (cy0 - tipy) * t))
    for i in range(10, 0, -1):
        t = i / 11
        pts.append((c + r * (1 - t) ** 1.15 + sway * t * t + S * 0.03 * math.sin(ph * 2 + t * 6 + 1.3) * t, cy0 - (cy0 - tipy) * t * 0.92))
    m.poly(pts, 255)
    if not core:
        # 옆으로 갈라지는 작은 혀 둘
        for s, off in ((-1, 0.0), (1, 2.1)):
            x0 = c + s * r * 0.7
            pts2 = [(x0 - S * 0.04, cy0 - r * 0.2), (x0 + s * S * 0.08 + S * 0.03 * math.sin(ph + off), cy0 - r * 1.6), (x0 + S * 0.04, cy0 - r * 0.2)]
            m.poly(pts2, 200)
    out = m.out()
    return glowify(out, 0.8, 3, 1.25) if not core else out


# ------------------------------------------------------------ 사신 그림자 (rfx_jreaper — 0 ~ 3 서 있음 · 4 ~ 7 휘두름)

def scythe(m, hx, hy, ang, up, down, blade, bw, sw, v=255, flip=1):
    """낫: 손 (hx, hy) 에서 자루가 ang 쪽 (위끝) up · 반대쪽 down, 위끝에서 옆 (flip 쪽) 으로 휘어 내려오는 날 (길이 blade · 뿌리 폭 bw)"""
    ux, uy = math.cos(ang), math.sin(ang)
    top = (hx + ux * up, hy + uy * up)
    bot = (hx - ux * down, hy - uy * down)
    m.line([bot, top], sw, v)
    px, py = -uy * flip, ux * flip
    outer, inner = [], []
    for i in range(19):
        t = i / 18
        bend = t * t * 0.55
        x = top[0] + px * blade * t - ux * blade * bend
        y = top[1] + py * blade * t - uy * blade * bend
        w = bw * (1 - t) ** 0.75
        outer.append((x, y))
        inner.append((x - ux * w, y - uy * w))
    m.poly(outer + inner[::-1], v)
    return top, outer[-1]


def reaper_body(m, W, H, dy, sway, core, v):
    """두건 · 얼굴 구멍 · 긴 옷 (아래가 찢어진 자락) — 가운데 x ≈ 0.45W"""
    cx = W * 0.45
    hood = [(cx + W * 0.02, H * 0.06 + dy)]
    for i in range(1, 10):
        t = i / 9
        hood.append((cx + W * 0.02 + W * 0.2 * math.sin(t * math.pi / 2), H * (0.06 + 0.26 * t) + dy))
    sh_r = (cx + W * 0.26, H * 0.36 + dy)
    sh_l = (cx - W * 0.25, H * 0.37 + dy)
    robe = hood + [sh_r]
    n = 12
    bottom = []
    for i in range(n + 1):
        t = i / n
        x = cx + W * 0.36 - W * 0.74 * t + sway * math.sin(t * 7 + sway * 0.2)
        y = H * 0.97 + dy * 0.3 - (H * 0.07 if i % 2 == 1 else 0) - H * 0.02 * math.sin(t * 5 + sway * 0.3)
        bottom.append((x, y))
    robe += bottom + [sh_l]
    for i in range(9, 0, -1):
        t = i / 9
        robe.append((cx + W * 0.02 - W * 0.19 * math.sin(t * math.pi / 2), H * (0.06 + 0.26 * t) + dy))
    # 눈: 안쪽이 내려간 비스듬한 가는 틈 둘 (둥근 점 눈은 귀여운 유령처럼 보여서 바꿈 — 사진 검토)
    fx = cx + W * 0.015
    eyes = []
    for ex in (-1, 1):
        x, y = fx + ex * W * 0.036, H * 0.212 + dy
        eyes.append([(x + ex * W * 0.024, y - H * 0.011), (x - ex * W * 0.016, y + H * 0.007)])
    if core:
        # 심지: 두건 테두리 · 옷 주름 줄 · 눈
        m.line(hood + [sh_r], W * 0.02, v)
        for k in range(3):
            x0 = cx - W * 0.1 + k * W * 0.1
            m.line([(x0, H * 0.42 + dy), (x0 + sway * 0.3 - W * 0.03 * (k - 1), H * 0.9 + dy * 0.3)], W * 0.012, v * 0.7)
        for e in eyes:
            m.line(e, W * 0.016, 255)
    else:
        m.poly(robe, v * 0.42)
        m.line(hood + [sh_r], W * 0.03, v)
        # 두건 속 어둠 (빛 장면에서 지움): 위가 뾰족하고 아래가 둥근 두건 구멍
        top, mid, bot, half = H * 0.115 + dy, H * 0.2 + dy, H * 0.29 + dy, W * 0.085
        hole = [(fx, top)]
        for i in range(1, 9):
            th = math.pi / 2 * i / 8
            hole.append((fx + half * math.sin(th), top + (mid - top) * (1 - math.cos(th))))
        for i in range(1, 16):
            th = math.pi * i / 16
            hole.append((fx + half * math.cos(th), mid + (bot - mid) * math.sin(th)))
        for i in range(8, 0, -1):
            th = math.pi / 2 * i / 8
            hole.append((fx - half * math.sin(th), top + (mid - top) * (1 - math.cos(th))))
        m.poly(hole, 0)
        # 어둠 속에서 빛나는 눈 (빛 장면 — 심지의 틈 둘레 번짐)
        for e in eyes:
            m.line(e, W * 0.034, v)
    return cx


def reaper_frame(f, frames=8, W=192, H=256, core=False):
    m = Cell(W, H)
    v = 255
    if f < 4:
        dy = H * 0.012 * math.sin(f * math.pi / 2)
        sway = W * 0.03 * math.sin(f * math.pi / 2 + 0.6)
        reaper_body(m, W, H, dy, sway, core, v)
        hx, hy = W * 0.7, H * 0.45 + dy
        top, tipp = scythe(m, hx, hy, math.radians(-96), H * 0.4, H * 0.42, W * 0.5, W * (0.07 if not core else 0.035), W * (0.028 if not core else 0.014), v, flip=-1)
        if f == 2:
            star(m, tipp[0], tipp[1], W * (0.06 if core else 0.08), 0.18, 0, 255)
        if not core:
            m.disc(hx, hy, W * 0.04, 200)
    else:
        g = f - 4
        reaper_body(m, W, H, 0, W * 0.02 * (1 - g / 3), core, v if g < 3 else 170)
        hx, hy = W * 0.6, H * 0.5
        angs = [-60, -150, -215, -235]
        a = math.radians(angs[g])
        if g in (1, 2):
            # 휘두른 자국: 손을 가운데로 한 큰 초승달 (날 끝이 지난 길)
            a0 = math.radians(angs[g - 1])
            if core:
                arc_band(m, hx, hy, H * 0.36, a0 + (a - a0) * 0.4, a, W * 0.004, W * 0.035, 60, 255, n=30)
            else:
                arc_band(m, hx, hy, H * 0.36, a0, a, W * 0.01, W * 0.1, 40, 230, n=36)
        scythe(m, hx, hy, a, H * 0.3, H * 0.24, W * 0.36, W * (0.065 if not core else 0.032), W * (0.026 if not core else 0.013), v if g < 3 else 150, flip=-1)
    out = m.out()
    return glowify(out, 0.8, 4, 1.15) if not core else out


# ------------------------------------------------------------ 명계의 문 (rfx_jgate)

def gate_frame(f, frames=8, S=256, core=False):
    m = Cell(S, S)
    rise = [0.35, 0.7, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    dy = (1 - rise) * S
    opening = [0.0, 0.0, 0.0, 0.8, 1.0, 1.0, 0.45, 0.0][f]
    v = 255 if f < 7 else 200
    gy = S * 0.985 + dy
    # 소용돌이 (열린 동안 — 문 안쪽)
    if opening > 0:
        cx, cy = S * 0.5, S * 0.6 + dy
        ph = f * 0.7
        inner_w = S * 0.2 * opening
        if not core:
            m.d.ellipse([(cx - inner_w) * SS, (S * 0.3 + dy) * SS, (cx + inner_w) * SS, gy * SS], fill=110)
        for s in range(3):
            pts = []
            for i in range(30):
                t = i / 29
                a = ph + s * TAU / 3 + t * 4.2
                r = S * 0.03 + t * S * 0.36
                pts.append((cx + math.cos(a) * min(r, inner_w + S * 0.02), cy + math.sin(a) * r * 1.05))
            taper_band(m, pts, S * 0.004, S * (0.035 if not core else 0.018), 255 if not core else 220, 0.6)
        m.disc(cx, cy, S * (0.05 if not core else 0.03), 255)
    # 기둥 둘 (위는 뿔 모양 머리)
    sv = v if core else int(v * 0.6)
    for x0, x1 in ((S * 0.14, S * 0.29), (S * 0.71, S * 0.86)):
        if core:
            m.line([(x0, gy), (x0, S * 0.26 + dy)], S * 0.014, sv)
            m.line([(x1, gy), (x1, S * 0.26 + dy)], S * 0.014, sv)
            for k in range(4):
                yy = S * (0.4 + 0.15 * k) + dy
                m.line([(x0, yy), (x1, yy)], S * 0.008, sv * 0.7)
        else:
            m.poly([(x0, gy), (x1, gy), (x1, S * 0.26 + dy), (x0, S * 0.26 + dy)], sv)
        m.poly([(x0 - S * 0.02, S * 0.27 + dy), ((x0 + x1) / 2, S * 0.13 + dy), (x1 + S * 0.02, S * 0.27 + dy)], sv)
    # 아치 (위로 볼록한 띠) · 가운데 뿔
    pts_o, pts_i = [], []
    for i in range(25):
        t = i / 24
        x = S * 0.22 + S * 0.56 * t
        hh = math.sin(math.pi * t)
        pts_o.append((x, S * (0.3 - 0.2 * hh) + dy))
        pts_i.append((x, S * (0.36 - 0.17 * hh) + dy))
    if core:
        m.line(pts_o, S * 0.014, sv)
        m.line(pts_i, S * 0.01, sv * 0.8)
    else:
        m.poly(pts_o + pts_i[::-1], sv)
    m.poly([(S * 0.47, S * 0.11 + dy), (S * 0.5, S * 0.02 + dy), (S * 0.53, S * 0.11 + dy)], sv)
    # 문짝 둘 (열리면 기둥 쪽으로 좁아짐)
    dw = S * 0.21 * (1 - opening)
    if dw > S * 0.01:
        for side in (-1, 1):
            xa = S * 0.5 + side * S * 0.21
            xb = xa - side * dw
            lo, hi = min(xa, xb), max(xa, xb)
            if core:
                m.line([(lo, S * 0.38 + dy), (lo, gy)], S * 0.008, 200)
                m.line([(hi, S * 0.38 + dy), (hi, gy)], S * 0.008, 200)
                for k in range(3):
                    yy = S * (0.48 + 0.16 * k) + dy
                    m.line([(lo, yy), (hi, yy)], S * 0.007, 170)
            else:
                m.poly([(lo, S * 0.37 + dy), (hi, S * 0.37 + dy), (hi, gy), (lo, gy)], 110)
        if f == 7:
            m.line([(S * 0.5, S * 0.36 + dy), (S * 0.5, gy)], S * (0.02 if not core else 0.01), 255)
            star(m, S * 0.5, S * 0.62 + dy, S * (0.14 if not core else 0.09), 0.14, 0, 255)
    out = m.out()
    return glowify(out, 0.8, 4, 1.15) if not core else out


# ------------------------------------------------------------ 망자의 손 (rfx_jhand — 아래 끝이 땅)

def hand_frame(f, frames=6, W=64, H=128, core=False):
    m = Cell(W, H)
    rise = [0.3, 0.65, 1.0, 1.0, 1.0, 0.75][f]
    curl = [0.0, 0.05, 0.0, 0.5, 1.0, 1.0][f]
    v = [255, 255, 255, 255, 240, 140][f]
    dy = (1 - rise) * H * 0.75
    cx = W * 0.5
    wrist = (cx, H * 0.5 + dy)
    arm_w = W * (0.15 if not core else 0.05)
    # 팔뚝 (아래로 넓어지며 땅에 닿음)
    if core:
        m.line([(cx - W * 0.05, H + 2), (cx - W * 0.03, wrist[1])], W * 0.035, v * 0.8)
        m.line([(cx + W * 0.06, H + 2), (cx + W * 0.04, wrist[1])], W * 0.03, v * 0.8)
    else:
        m.poly([(cx - arm_w * 1.6, H + 2), (cx - arm_w, wrist[1]), (cx + arm_w, wrist[1]), (cx + arm_w * 1.6, H + 2)], v * 0.8)
    # 손바닥
    pc = (cx, H * 0.42 + dy)
    m.disc(pc[0], pc[1], W * (0.17 if not core else 0.07), v)
    # 손가락 넷 + 엄지 (두 마디 — curl 만큼 안으로 굽음)
    for i, (ang, L) in enumerate(((-24, 0.24), (-8, 0.28), (8, 0.27), (24, 0.22), (-70, 0.17))):
        a = math.radians(-90 + ang)
        side = -1 if ang < 0 else 1
        x0 = pc[0] + math.cos(a) * W * 0.14
        y0 = pc[1] + math.sin(a) * W * 0.14
        l1 = H * L * 0.55
        x1, y1 = x0 + math.cos(a) * l1, y0 + math.sin(a) * l1
        a2 = a - side * math.radians(95) * curl if i < 4 else a + math.radians(70) * curl
        l2 = H * L * 0.5 * (1 - 0.2 * curl)
        x2, y2 = x1 + math.cos(a2) * l2, y1 + math.sin(a2) * l2
        wf = W * (0.075 if not core else 0.028)
        m.line([(x0, y0), (x1, y1), (x2, y2)], wf, v)
        if not core:
            m.disc(x2, y2, wf * 0.55, v)
    out = m.out()
    return glowify(out, 0.8, 3, 1.15) if not core else out


# ------------------------------------------------------------ 모두

def main():
    made = {
        "rfx_jpetal": (two_sheets(petal_frame, 8, 8, "rfx_jpetal", 64, 64, "rotate_xyz"), 8),
        "rfx_jdagger": (two_sheets(dagger_frame, 4, 4, "rfx_jdagger", 128, 128, "rotate_xyz"), 4),
        "rfx_jsmoke": (two_sheets(smoke_frame, 8, 4, "rfx_jsmoke", 128, 128, "rotate_xyz"), 8),
        "rfx_jcross": (two_sheets(cross_frame, 6, 6, "rfx_jcross", 128, 128, "rotate_xyz"), 6),
        "rfx_jstab": (two_sheets(stab_frame, 6, 6, "rfx_jstab", 128, 128, "rotate_xyz"), 6),
        "rfx_jspin": (two_sheets(spin_frame, 8, 4, "rfx_jspin", 192, 192, "direction_z"), 8),
        "rfx_jspear": (two_sheets(spear_frame, 4, 2, "rfx_jspear", 256, 64, "lookat_direction"), 4),
        "rfx_jsoul": (two_sheets(soul_frame, 6, 6, "rfx_jsoul", 64, 64, "rotate_xyz"), 6),
        "rfx_jreaper": (two_sheets(reaper_frame, 8, 4, "rfx_jreaper", 192, 256, "lookat_y"), 8),
        "rfx_jgate": (two_sheets(gate_frame, 8, 4, "rfx_jgate", 256, 256, "lookat_y"), 8),
        "rfx_jhand": (two_sheets(hand_frame, 6, 6, "rfx_jhand", 64, 128, "lookat_y"), 6),
    }
    return made


if __name__ == "__main__":
    main()
