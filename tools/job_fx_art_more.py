#!/usr/bin/env python3
"""0.28.0 직업 스킬 연출 그림 — 전사 · 매지션 · 궁수 (메이플식 2D 그림, tools/job_fx_art.py 와 같은 꼴: 흰색 = 알파 · 빛 장면들 다음에 심지 장면들).
tools/job_fx_art.py 가 함께 부름 (python3 tools/job_fx_art.py [--sheet <폴더>]).

- rfx_jsmash   땅에 눕는 내려찍기 (192×192 × 8장): 가운데 번쩍 · 퍼지는 충격 고리 · 갈라지는 금 일곱 · 튀는 돌조각 (전사 파쇄격 · 돌격 · 격노 도약)
- rfx_jbreak   방어 깨짐 표시 (카메라를 봄, 128×128 × 6장): 방패가 금이 가 두 쪽으로 갈라지며 조각이 흩어짐 (파쇄격 — 맞은 적 머리 위)
- rfx_jwhirl   땅에 눕는 회전 칼날 (192×192 × 8장 — 이어 돌림): 도끼 날 같은 초승달 셋이 도는 원판 (대회전)
- rfx_jtwister 솟는 회오리 (세워 카메라 쪽으로 돎, 128×256 × 8장 — 이어 돌림): 아래가 좁고 위가 넓은 깔때기의 고리 · 나선 줄기가 위로 흐름 (대회전)
- rfx_jcomet   불꽃 꼬리 (방향으로 누움 — 오른쪽이 앞, 256×96 × 4장 — 이어 돌림): 머리의 불덩이 · 흔들리는 꼬리 · 불티 (격노 도약 · 금서의 불 주문)
- rfx_jbolt    마탄 (방향으로 누움, 128×64 × 4장 — 이어 돌림): 도는 별 머리 · 가는 꼬리 · 반짝임 (마탄 연사)
- rfx_jshatter 깨지는 거울 (카메라를 봄, 192×192 × 8장): 가운데서 퍼지는 금 → 조각이 돌며 흩어짐 (위상 전환의 잔상)
- rfx_jorb     마력 구체 (카메라를 봄, 128×128 × 6장 — 이어 돌림): 둥근 빛 · 테두리 · 안에서 도는 소용돌이 셋 · 둘레를 도는 반짝임 (비전 구체)
- rfx_jbeam    굵은 광선 (방향으로 누움, 256×64 × 6장): 가늘게 그어져 굵어지고 가장자리가 일렁이다 가늘어짐 (마나 과부하 · 관천일시)
- rfx_jbook    펼쳐진 책 (카메라를 봄, 192×192 × 8장): 0 ~ 5 = 한 장씩 넘어가는 책장 (이어 돌림), 6 ~ 7 = 책이 닫힘 (금서 개방)
- rfx_jpage    날리는 책장 (카메라를 봄, 64×64 × 4장 — 이어 돌림): 펄럭이는 종이 (금서 개방)
- rfx_jarrow   빛의 화살 (방향으로 누움 — 오른쪽이 촉, 256×64 × 4장 — 이어 돌림): 촉 · 살대 · 깃 · 뒤의 빛줄기 · 살대를 따라 흐르는 반짝임 (궁수)
- rfx_jbow     빛의 활 (카메라를 봄, 192×192 × 6장): 0 ~ 3 = 시위를 당김 (뒤에서 본 활 — 활대 · 가운데로 모이는 시위 · 모이는 화살 빛), 4 ~ 5 = 놓음 (속사 · 관천일시)
- rfx_jtarget  과녁 (192×192 × 6장): 고리 셋 · 네 눈금이 조여 들어 맞춰짐 (화살비 바닥 · 정조준 1인칭 조준 표시)
- rfx_jwind    바람 고리 (땅에 눕힘, 192×192 × 8장): 나선으로 도는 바람 줄기 셋이 퍼지며 사라짐 (바람 타기)
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음).
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402
from job_fx_art import Cell, add, blur, ease_out, lighter, rgba, scale, sheet, star  # noqa: E402

TAU = math.tau


def glowify(out, k=0.72, r=5, g=1.2):
    """빛 장면: 또렷한 모양 + 흐리게 퍼진 빛"""
    return add(scale(out, k), scale(blur(out, r), g))


def two_sheets(fn, frames, cols, name, cw, ch, facing):
    cells = [rgba(fn(f, frames, core=False)) for f in range(frames)] + [rgba(fn(f, frames, core=True)) for f in range(frames)]
    save(name, sheet(cells, cols), cw, ch, facing)
    return cells


def jagged(rnd, x0, y0, ang, length, segs=5, jit=0.18):
    """x0, y0 에서 ang 쪽으로 length 만큼 지그재그 줄"""
    pts = [(x0, y0)]
    for i in range(1, segs + 1):
        t = i / segs
        a = ang + rnd.uniform(-jit, jit)
        pts.append((x0 + math.cos(a) * length * t, y0 + math.sin(a) * length * t))
    return pts


def rot(pts, cx, cy, a):
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def arc_band(c, cx, cy, r, a0, a1, w0, w1, v0=255, v1=255, n=40, rx=1.0, ry=1.0):
    """반지름 r 의 호 (a0 → a1 라디안 — 화면 좌표) 를 두께 w0 → w1 · 밝기 v0 → v1 로"""
    for i in range(n):
        t0, t1 = i / n, (i + 1) / n
        b0, b1 = a0 + (a1 - a0) * t0, a0 + (a1 - a0) * t1
        wa, wb = w0 + (w1 - w0) * t0, w0 + (w1 - w0) * t1
        v = v0 + (v1 - v0) * t1
        pts = [(cx + math.cos(b0) * (r + wa / 2) * rx, cy + math.sin(b0) * (r + wa / 2) * ry),
               (cx + math.cos(b1) * (r + wb / 2) * rx, cy + math.sin(b1) * (r + wb / 2) * ry),
               (cx + math.cos(b1) * (r - wb / 2) * rx, cy + math.sin(b1) * (r - wb / 2) * ry),
               (cx + math.cos(b0) * (r - wa / 2) * rx, cy + math.sin(b0) * (r - wa / 2) * ry)]
        c.poly(pts, max(0, min(255, v)))


# ------------------------------------------------------------ 내려찍기 (rfx_jsmash — 땅에 눕힘)

def smash_frame(f, frames=8, S=192, core=False):
    c = S / 2
    p = f / (frames - 1)
    m = Cell(S, S)
    if f <= 2:
        m.disc(c, c, S * (0.15 + 0.05 * f) * (1.15 if not core else 0.65), 255 * (1 - f / 3.0))
    # 충격 고리
    r = S * (0.1 + 0.36 * ease_out(min(1.0, (f + 0.6) / 5.0)))
    m.ring(c, c, r, S * (0.075 if not core else 0.03) * (1 - p * 0.8), 255 * (1 - p ** 1.3))
    # 갈라지는 금: 일곱 줄기 (처음 넷 장에 뻗고 뒤에 흐려짐) + 잔가지
    rnd = random.Random(42)
    grow = ease_out(min(1.0, (f + 1) / 3.5))
    fade = 1.0 if f < 5 else max(0.0, 1 - (f - 4) / 3.5)
    for i in range(7):
        ang = i * TAU / 7 + rnd.uniform(-0.3, 0.3)
        L = S * rnd.uniform(0.3, 0.45) * grow
        pts = jagged(rnd, c, c, ang, L, 5, 0.22)
        wd = S * (0.024 if not core else 0.011) * (1 - 0.4 * p)
        m.line(pts, wd, 255 * fade)
        if len(pts) > 3:
            bx, by = pts[2]
            br = jagged(rnd, bx, by, ang + rnd.choice((-0.7, 0.7)), L * 0.35, 3, 0.25)
            m.line(br, wd * 0.7, 220 * fade)
    # 튀는 돌조각
    for k in range(11):
        ang = rnd.uniform(0, TAU)
        rr = S * (0.1 + 0.38 * ease_out(p) * rnd.uniform(0.7, 1.0))
        x, y = c + math.cos(ang) * rr, c + math.sin(ang) * rr
        sz = S * rnd.uniform(0.018, 0.03) * (1 - p * 0.5) * (1 if not core else 0.7)
        quad = [(x - sz, y - sz * 0.6), (x + sz * 0.7, y - sz), (x + sz, y + sz * 0.5), (x - sz * 0.5, y + sz)]
        m.poly(rot(quad, x, y, rnd.uniform(0, TAU) + p * 3), 255 * (1 - p ** 1.5))
    out = m.out()
    return glowify(out) if not core else out


# ------------------------------------------------------------ 방어 깨짐 (rfx_jbreak)

def shield_curve(S, side):
    """방패 옆 (side −1 왼쪽 · 1 오른쪽) 아래 곡선: 옆 0.52 높이 → 아래 끝 (가운데 0.86) 사이 점들"""
    pts = []
    for i in range(1, 12):
        t = i / 12
        pts.append((0.5 * S + side * (0.23 * S - 0.23 * S * t), 0.52 * S + 0.34 * S * math.sin(t * math.pi / 2)))
    return pts


def shield_outline(S):
    return ([(0.27 * S, 0.2 * S), (0.73 * S, 0.2 * S), (0.73 * S, 0.52 * S)] + shield_curve(S, 1) + [(0.5 * S, 0.86 * S)]
            + shield_curve(S, -1)[::-1] + [(0.27 * S, 0.52 * S)])


def break_frame(f, frames=6, S=128, core=False):
    m = Cell(S, S)
    crack = [(0.5 * S, 0.2 * S), (0.45 * S, 0.34 * S), (0.55 * S, 0.47 * S), (0.46 * S, 0.62 * S), (0.53 * S, 0.74 * S), (0.5 * S, 0.86 * S)]
    full = shield_outline(S)
    halves = []
    for side in (-1, 1):
        x = 0.5 * S + side * 0.23 * S
        halves.append(([crack[0], (x, 0.2 * S), (x, 0.52 * S)] + shield_curve(S, side) + [crack[-1]] + crack[-2:0:-1], side))
    if f <= 1:
        if not core:
            m.poly(full, 140 + 60 * f)
        m.line(full + [full[0]], S * (0.035 if not core else 0.018), 255)
        part = crack[: 3 + f * 3]
        m.line(part, S * (0.03 if not core else 0.016), 255)
        if f == 1:
            star(m, 0.5 * S, 0.47 * S, S * 0.22, 0.16, 0, 255)
    else:
        q = (f - 1) / (frames - 2)
        dx = S * 0.13 * ease_out(q)
        dy = S * 0.06 * q * q
        a = 0.35 * q
        v = 255 * (1 - q ** 1.4)
        for poly_, sgn in halves:
            moved = [(x + sgn * dx, y + dy) for x, y in poly_]
            cx = sum(x for x, _ in moved) / len(moved)
            cy = sum(y for _, y in moved) / len(moved)
            moved = rot(moved, cx, cy, sgn * a)
            if not core:
                m.poly(moved, v * 0.7)
            m.line(moved + [moved[0]], S * (0.03 if not core else 0.016), v)
        rnd = random.Random(7)
        for _ in range(8):
            ang = rnd.uniform(0, TAU)
            rr = S * (0.1 + 0.32 * ease_out(q) * rnd.uniform(0.6, 1))
            x, y = 0.5 * S + math.cos(ang) * rr, 0.5 * S + math.sin(ang) * rr
            sz = S * 0.03 * (1 - q * 0.5)
            m.poly(rot([(x, y - sz), (x + sz * 0.8, y + sz * 0.6), (x - sz * 0.8, y + sz * 0.6)], x, y, rnd.uniform(0, TAU)), v)
    out = m.out()
    return glowify(out, 0.75, 3, 1.1) if not core else out


# ------------------------------------------------------------ 회전 칼날 (rfx_jwhirl — 땅에 눕힘, 이어 돌림)

def whirl_frame(f, frames=8, S=192, core=False):
    c = S / 2
    m = Cell(S, S)
    base = math.radians(f * 120 / frames)
    R = S * 0.4
    if not core:
        m.ring(c, c, R * 0.7, S * 0.014, 90)
        m.ring(c, c, R * 1.06, S * 0.01, 60)
    for b in range(3):
        head = base + b * TAU / 3
        tail = head - math.radians(105)
        # 도끼 날 같은 초승달: 꼬리 (가늘고 흐림) → 머리 (굵고 밝음)
        arc_band(m, c, c, R, tail, head, S * 0.012, S * (0.11 if not core else 0.05), 60, 255, n=48)
        # 머리 끝의 빛
        hx, hy = c + math.cos(head) * R, c + math.sin(head) * R
        star(m, hx, hy, S * (0.07 if core else 0.09), 0.2, math.degrees(head), 255)
    out = m.out()
    return glowify(out, 0.75, 4, 1.15) if not core else out


# ------------------------------------------------------------ 회오리 (rfx_jtwister — 세워 카메라 쪽으로 돎, 이어 돌림)

def twister_frame(f, frames=8, W=128, H=256, core=False):
    m = Cell(W, H)
    cx = W / 2
    top, bottom = H * 0.05, H * 0.97

    def radius(u):
        return W * (0.08 + 0.36 * u ** 0.85)

    def sway(u):
        return math.sin(u * TAU * 0.9 + 0.6) * W * 0.05

    if not core:
        # 깔때기 몸 (흐린 채움 — 먼저: 그림은 덮어 그림)
        left, right = [], []
        for i in range(31):
            u = i / 30
            y = bottom - u * (bottom - top)
            left.append((cx + sway(u) - radius(u) * 0.9, y))
            right.append((cx + sway(u) + radius(u) * 0.9, y))
        m.poly(left + right[::-1], 55)
    n = 9
    for k in range(n):
        u = ((k + f / frames) / n) % 1.0  # 0 = 아래 → 1 = 위 (장마다 위로 흐름)
        y = bottom - u * (bottom - top)
        rr = radius(u)
        v = 255 * math.sin(math.pi * min(1.0, u * 1.04)) ** 0.6
        wd = H * (0.026 if not core else 0.011) * (0.6 + 0.8 * u)
        # 기운 고리: 앞쪽 반 (밝고 굵게) · 뒤쪽 반 (흐리게) — 바람 띠가 한쪽으로 휘감김 (앞쪽 반은 머리가 굵음)
        arc_band(m, cx + sway(u), y, rr, math.pi * 1.05, math.pi * 1.95, wd * 0.5, wd * 0.5, v * 0.35, v * 0.35, n=20, ry=0.26)
        arc_band(m, cx + sway(u), y, rr, -math.pi * 0.05, math.pi * 1.0, wd * 0.4, wd, v * 0.55, v, n=30, ry=0.26)
    # 가는 나선 줄기 하나
    pts = []
    for i in range(41):
        t = i / 40
        y = bottom - t * (bottom - top)
        a = t * TAU * 1.4 + f / frames * TAU
        pts.append((cx + sway(t) + math.cos(a) * radius(t), y + math.sin(a) * radius(t) * 0.26))
    m.line(pts, W * (0.016 if not core else 0.008), 120)
    out = m.out()
    out = glowify(out, 0.7, 4, 1.2) if not core else out
    grad = Image.linear_gradient("L").resize((W, H))
    return ImageChops.multiply(out, grad.point(lambda v: min(255, int(90 + v * 0.75))))


# ------------------------------------------------------------ 불꽃 꼬리 (rfx_jcomet — 오른쪽이 앞, 이어 돌림)

def comet_frame(f, frames=4, W=256, H=96, core=False):
    m = Cell(W, H)
    cy = H / 2
    hx = W * 0.82
    rnd = random.Random(300 + f)
    # 꼬리: 꼬리 끝 (흐림) 에서 머리 (밝음) 쪽으로 원을 겹침 (그림은 덮어 그림 — 밝은 쪽이 나중)
    n = 46
    for i in range(n - 1, -1, -1):
        t = i / (n - 1)  # 0 = 머리 → 1 = 꼬리 끝
        x = hx - t * W * 0.78
        y = cy + math.sin(t * 9 + f * 1.7) * H * 0.07 * t
        r = H * (0.24 if not core else 0.12) * (1 - t) ** 0.9 * (0.85 + 0.15 * math.sin(t * 17 + f * 2.3))
        m.disc(x, y, r, 255 * (1 - t) ** 0.6)
    # 갈라진 불꽃 (위 · 아래로 흔들리는 가는 꼬리 — 원을 겹쳐 부드럽게)
    for s in (-1, 1):
        for i in range(27, -1, -1):
            t = i / 27
            x = hx - H * 0.1 - t * W * 0.42
            y = cy + s * H * (0.08 + 0.14 * t) + math.sin(t * 6 + f * 2.1 + s) * H * 0.04
            m.disc(x, y, H * (0.09 if not core else 0.045) * (1 - t) ** 0.8, 200 * (1 - t) ** 0.7)
    # 머리
    m.disc(hx, cy, H * (0.26 if not core else 0.15), 255)
    if core:
        star(m, hx + H * 0.04, cy, H * 0.3, 0.18, f * 22.5, 255)
    # 불티
    for _ in range(8):
        t = rnd.uniform(0.2, 1.0)
        m.disc(hx - t * W * 0.8, cy + rnd.uniform(-0.32, 0.32) * H, H * rnd.uniform(0.015, 0.03), 230 * (1 - t * 0.6))
    out = m.out()
    return glowify(out, 0.7, 4, 1.25) if not core else out


# ------------------------------------------------------------ 마탄 (rfx_jbolt — 오른쪽이 앞, 이어 돌림)

def bolt_frame(f, frames=4, W=128, H=64, core=False):
    m = Cell(W, H)
    cy = H / 2
    hx = W * 0.78
    m.poly([(hx, cy - H * (0.13 if not core else 0.07)), (W * 0.06, cy), (hx, cy + H * (0.13 if not core else 0.07))], 200)
    m.disc(hx, cy, H * (0.2 if not core else 0.11), 255)
    star(m, hx, cy, H * (0.42 if not core else 0.34), 0.16, f * 22.5, 255)
    rnd = random.Random(40 + f)
    for _ in range(3):
        x = rnd.uniform(W * 0.2, W * 0.6)
        star(m, x, cy + rnd.uniform(-0.18, 0.18) * H, H * 0.1, 0.22, 0, 220)
    out = m.out()
    return glowify(out, 0.75, 3, 1.25) if not core else out


# ------------------------------------------------------------ 깨지는 거울 (rfx_jshatter)

def shard_polys(S):
    c = S / 2
    rnd = random.Random(19)
    n = 8
    angs = [i * TAU / n + rnd.uniform(-0.18, 0.18) for i in range(n)]
    rings = [S * 0.07, S * 0.2, S * 0.33, S * 0.45]
    pts = [[(c + math.cos(a) * r * rnd.uniform(0.9, 1.1), c + math.sin(a) * r * rnd.uniform(0.9, 1.1)) for a in angs] for r in rings]
    polys = []
    for ri in range(len(rings) - 1):
        for i in range(n):
            j = (i + 1) % n
            polys.append([pts[ri][i], pts[ri][j], pts[ri + 1][j], pts[ri + 1][i]])
    inner = pts[0]
    return polys, inner


def shatter_frame(f, frames=8, S=192, core=False):
    c = S / 2
    m = Cell(S, S)
    polys, inner = shard_polys(S)
    rnd = random.Random(5)
    if f <= 1:
        # 금: 가운데 번쩍 · 방사형 금 · 고리 금
        m.disc(c, c, S * (0.1 if not core else 0.05) * (1 + f), 255)
        wd = S * (0.016 if not core else 0.008)
        for poly_ in polys:
            m.line(poly_ + [poly_[0]], wd, 255 if f == 1 else 200)
        if not core:
            for poly_ in polys:
                m.poly(poly_, 70 + 40 * f)
    else:
        q = (f - 1) / (frames - 2)
        v = 255 * (1 - q) ** 1.2
        for k, poly_ in enumerate(polys):
            cx = sum(x for x, _ in poly_) / 4
            cy = sum(y for _, y in poly_) / 4
            d = math.hypot(cx - c, cy - c) or 1
            ox, oy = (cx - c) / d, (cy - c) / d
            push = S * 0.36 * ease_out(q) * (0.6 + 0.6 * (d / (S * 0.45)))
            moved = [(x + ox * push, y + oy * push) for x, y in poly_]
            mx, my = cx + ox * push, cy + oy * push
            shrink = 1 - q * 0.45
            moved = [(mx + (x - mx) * shrink, my + (y - my) * shrink) for x, y in moved]
            moved = rot(moved, mx, my, rnd.uniform(-1, 1) * q * 2.2)
            if not core:
                m.poly(moved, v * 0.8)
            m.line(moved + [moved[0]], S * (0.014 if not core else 0.008), v)
    out = m.out()
    return glowify(out, 0.75, 4, 1.15) if not core else out


# ------------------------------------------------------------ 마력 구체 (rfx_jorb — 이어 돌림)

def orb_frame(f, frames=6, S=128, core=False):
    c = S / 2
    R = S * 0.3
    m = Cell(S, S)
    if not core:
        m.disc(c, c, R * 1.2, 150)
    m.disc(c, c, R * (0.45 if core else 0.7), 255)
    m.ring(c, c, R, S * (0.045 if not core else 0.02), 255)
    base = math.radians(f * 120 / frames)
    for k in range(3):
        a0 = base + k * TAU / 3
        arc_band(m, c, c, R * 0.62, a0, a0 + math.radians(75), S * 0.012, S * (0.05 if not core else 0.028), 120, 255, n=20)
    for k in range(4):
        a = math.radians(f * 360 / frames / 2) + k * TAU / 4
        star(m, c + math.cos(a) * R * 1.35, c + math.sin(a) * R * 1.35, S * (0.06 if core else 0.075), 0.2, 0, 255)
    out = m.out()
    return glowify(out, 0.7, 4, 1.2) if not core else out


# ------------------------------------------------------------ 굵은 광선 (rfx_jbeam — 방향으로 누움)

def beam_frame(f, frames=6, W=256, H=64, core=False):
    m = Cell(W, H)
    cy = H / 2
    th = [0.06, 0.3, 0.42, 0.38, 0.22, 0.07][min(f, 5)] * H * (1 if not core else 0.42)
    top, bot = [], []
    n = 60
    for i in range(n + 1):
        t = i / n
        x = 2 + t * (W - 4)
        taper = min(1.0, t / 0.06, (1 - t) / 0.06)
        wob = 1 + (0.14 * math.sin(t * 40 + f * 1.9) if not core else 0.05 * math.sin(t * 30 + f))
        h = th * taper * wob
        top.append((x, cy - h))
        bot.append((x, cy + h))
    m.poly(top + bot[::-1], 255 if f < 5 else 170)
    if not core and 1 <= f <= 4:
        rnd = random.Random(f * 13)
        for _ in range(10):
            x = rnd.uniform(W * 0.05, W * 0.95)
            star(m, x, cy + rnd.uniform(-1.6, 1.6) * th, H * 0.12, 0.2, 0, 200)
    out = m.out()
    return glowify(out, 0.8, 3, 1.3) if not core else out


# ------------------------------------------------------------ 펼쳐진 책 (rfx_jbook)

def page_quad(S, side, open_k=1.0, lift=0.0):
    """책장 하나 (side −1 = 왼쪽 · 1 = 오른쪽): 등 (가운데) → 바깥, 위아래가 조금 처짐. open_k = 펼친 정도 (1 = 다 펼침)"""
    c = S / 2
    w = S * 0.36 * open_k
    top, bot = [], []
    for i in range(13):
        t = i / 12
        x = c + side * w * t
        sag = math.sin(t * math.pi) * S * 0.035
        top.append((x, S * 0.3 + sag - lift * math.sin(t * math.pi) * S * 0.12 + S * 0.04 * t))
        bot.append((x, S * 0.66 + sag * 0.6 + S * 0.03 * t))
    return top + bot[::-1]


def book_frame(f, frames=8, S=192, core=False):
    c = S / 2
    m = Cell(S, S)
    if f <= 5:
        open_k = 1.0
    else:
        open_k = max(0.06, 1 - (f - 5) / 2.2)
    wd = S * (0.016 if not core else 0.009)
    if not core:
        m.disc(c, S * 0.48, S * 0.42, 40)  # 먼저 (그림은 덮어 그림)
    for side in (-1, 1):
        q = page_quad(S, side, open_k)
        if not core:
            m.poly(q, 130)
        m.line(q + [q[0]], wd, 255)
        # 표지 (책장 아래 조금 더 큰 테두리)
        cov = [(x + side * S * 0.012, y + S * 0.018) for x, y in q]
        m.line(cov[len(cov) // 2:] + [cov[0]], wd * 1.2, 200)
        # 글줄 (모양 없는 줄)
        if open_k > 0.5:
            for k in range(5):
                y = S * (0.38 + 0.05 * k)
                x0, x1 = c + side * S * 0.05 * open_k, c + side * S * 0.3 * open_k
                m.line([(x0, y + S * 0.02), (x1, y + S * 0.035)], S * 0.008, 150 if core else 110)
    m.line([(c, S * 0.3), (c, S * 0.68)], wd * 1.3, 255)
    if f <= 5:
        # 넘어가는 책장: 오른쪽에서 등을 축으로 왼쪽으로
        th = math.pi * (f + 0.5) / 6
        k = math.cos(th)
        q = page_quad(S, 1 if k >= 0 else -1, abs(k) * 0.98 + 0.02, lift=math.sin(th))
        if not core:
            m.poly(q, 170)
        m.line(q + [q[0]], wd, 255)
    else:
        star(m, c, S * 0.48, S * (0.2 + 0.12 * (f - 5)), 0.16, 0, 255)
    out = m.out()
    return glowify(out, 0.75, 4, 1.15) if not core else out


def page_frame(f, frames=4, S=64, core=False):
    m = Cell(S, S)
    c = S / 2
    k = 0.3 + 0.7 * abs(math.cos(f / frames * math.pi))
    w, h = S * 0.24 * k, S * 0.32
    skew = math.sin(f / frames * TAU) * S * 0.06
    q = [(c - w, c - h + skew), (c + w, c - h - skew), (c + w, c + h - skew), (c - w, c + h + skew)]
    if not core:
        m.poly(q, 200)
    m.line(q + [q[0]], S * (0.05 if not core else 0.03), 255)
    for i in range(3):
        y = c - h * 0.5 + i * h * 0.45
        m.line([(c - w * 0.65, y + skew * 0.5), (c + w * 0.65, y - skew * 0.5)], S * 0.025, 180)
    out = m.out()
    return glowify(out, 0.8, 2, 1.1) if not core else out


# ------------------------------------------------------------ 빛의 화살 (rfx_jarrow — 오른쪽이 촉)

def arrow_frame(f, frames=4, W=256, H=64, core=False):
    m = Cell(W, H)
    cy = H / 2
    if not core:
        m.poly([(W * 0.8, cy - H * 0.16), (W * 0.02, cy), (W * 0.8, cy + H * 0.16)], 110)
    m.line([(W * 0.12, cy), (W * 0.84, cy)], H * (0.07 if not core else 0.035), 255)
    hh = H * (0.22 if not core else 0.15)
    m.poly([(W * 0.985, cy), (W * 0.8, cy - hh), (W * 0.84, cy), (W * 0.8, cy + hh)], 255)
    for dx in (0.0, 0.07):
        x0 = W * (0.25 - dx)
        m.line([(x0, cy), (x0 - W * 0.11, cy - H * 0.22)], H * (0.045 if not core else 0.025), 230)
        m.line([(x0, cy), (x0 - W * 0.11, cy + H * 0.22)], H * (0.045 if not core else 0.025), 230)
    sx = W * (0.3 + 0.13 * f)
    star(m, sx, cy, H * (0.22 if core else 0.28), 0.18, 0, 255)
    out = m.out()
    return glowify(out, 0.78, 3, 1.25) if not core else out


# ------------------------------------------------------------ 빛의 활 (rfx_jbow — 뒤에서 본 활)

def bow_frame(f, frames=6, S=192, core=False):
    m = Cell(S, S)
    pull = [0.05, 0.13, 0.21, 0.27, 0.0, 0.0][f] * S
    ecx, ecy, rx, ry = S * 0.6, S * 0.5, S * 0.27, S * 0.43
    pts = []
    widths = []
    n = 40
    for i in range(n + 1):
        t = i / n
        a = math.radians(122 + 116 * t)
        pts.append((ecx + math.cos(a) * rx, ecy + math.sin(a) * ry))
        widths.append(S * (0.058 if not core else 0.028) * (0.35 + 0.65 * math.sin(math.pi * t)))
    for i in range(n):
        m.line([pts[i], pts[i + 1]], (widths[i] + widths[i + 1]) / 2, 255)
    top, bot = pts[0], pts[-1]
    for tip in (top, bot):
        star(m, tip[0], tip[1], S * (0.05 if core else 0.065), 0.2, 0, 255)
    nx, ny = (top[0] + bot[0]) / 2 + pull, S * 0.5
    sw = S * (0.016 if not core else 0.009)
    if f < 4:
        m.line([top, (nx, ny), bot], sw, 230)
        k = (f + 1) / 4
        star(m, nx, ny, S * (0.07 + 0.09 * k) * (1.2 if not core else 0.9), 0.16, 45 * k, 255)
        m.disc(nx, ny, S * 0.025 * (1 + k), 255)
    else:
        off = S * 0.03 * (1 if f == 4 else -0.4)
        m.line([top, ((top[0] + bot[0]) / 2 + off, S * 0.5), bot], sw, 220 if f == 4 else 150)
        if f == 4:
            star(m, nx + S * 0.05, ny, S * 0.24, 0.12, 0, 255)
    out = m.out()
    return glowify(out, 0.75, 4, 1.2) if not core else out


# ------------------------------------------------------------ 과녁 (rfx_jtarget)

def target_frame(f, frames=6, S=192, core=False):
    c = S / 2
    m = Cell(S, S)
    k = [1.12, 1.07, 1.03, 1.0, 1.0, 1.0][f]
    v = [150, 220, 255, 255, 220, 255][f]
    for i, rr in enumerate((0.42, 0.28, 0.14)):
        m.ring(c, c, S * rr * k, S * (0.024 if not core else 0.012) * (1.2 if i == 0 else 1), v)
    for i in range(4):
        a = i * math.pi / 2
        r0, r1 = S * 0.32 * k, S * 0.47 * k
        m.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], S * (0.03 if not core else 0.016), v)
    m.disc(c, c, S * (0.03 if not core else 0.018), v)
    out = m.out()
    return glowify(out, 0.75, 3, 1.15) if not core else out


# ------------------------------------------------------------ 바람 고리 (rfx_jwind — 땅에 눕힘)

def wind_frame(f, frames=8, S=192, core=False):
    c = S / 2
    p = f / (frames - 1)
    m = Cell(S, S)
    base = math.radians(f * 24)
    grow = 0.85 + 0.25 * ease_out(p)
    for s in range(3):
        a0 = base + s * TAU / 3
        pts = []
        wds = []
        for i in range(31):
            t = i / 30
            a = a0 + t * math.radians(150)
            r = S * (0.2 + 0.24 * t) * grow
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
            wds.append(S * (0.05 if not core else 0.024) * math.sin(math.pi * min(1.0, t * 1.15)) ** 0.8)
        v = 255 * (1 - p ** 1.6)
        for i in range(30):
            m.line([pts[i], pts[i + 1]], max(0.5, (wds[i] + wds[i + 1]) / 2), v)
    out = m.out()
    return glowify(out, 0.75, 4, 1.15) if not core else out


# ------------------------------------------------------------ 모두

def main():
    made = {
        "rfx_jsmash": (two_sheets(smash_frame, 8, 4, "rfx_jsmash", 192, 192, "direction_z"), 8),
        "rfx_jbreak": (two_sheets(break_frame, 6, 6, "rfx_jbreak", 128, 128, "rotate_xyz"), 6),
        "rfx_jwhirl": (two_sheets(whirl_frame, 8, 4, "rfx_jwhirl", 192, 192, "direction_z"), 8),
        "rfx_jtwister": (two_sheets(twister_frame, 8, 8, "rfx_jtwister", 128, 256, "lookat_y"), 8),
        "rfx_jcomet": (two_sheets(comet_frame, 4, 4, "rfx_jcomet", 256, 96, "lookat_direction"), 4),
        "rfx_jbolt": (two_sheets(bolt_frame, 4, 4, "rfx_jbolt", 128, 64, "lookat_direction"), 4),
        "rfx_jshatter": (two_sheets(shatter_frame, 8, 4, "rfx_jshatter", 192, 192, "rotate_xyz"), 8),
        "rfx_jorb": (two_sheets(orb_frame, 6, 6, "rfx_jorb", 128, 128, "rotate_xyz"), 6),
        "rfx_jbeam": (two_sheets(beam_frame, 6, 2, "rfx_jbeam", 256, 64, "lookat_direction"), 6),
        "rfx_jbook": (two_sheets(book_frame, 8, 4, "rfx_jbook", 192, 192, "rotate_xyz"), 8),
        "rfx_jpage": (two_sheets(page_frame, 4, 4, "rfx_jpage", 64, 64, "rotate_xyz"), 4),
        "rfx_jarrow": (two_sheets(arrow_frame, 4, 2, "rfx_jarrow", 256, 64, "lookat_direction"), 4),
        "rfx_jbow": (two_sheets(bow_frame, 6, 6, "rfx_jbow", 192, 192, "rotate_xyz"), 6),
        "rfx_jtarget": (two_sheets(target_frame, 6, 6, "rfx_jtarget", 192, 192, "direction_z"), 6),
        "rfx_jwind": (two_sheets(wind_frame, 8, 4, "rfx_jwind", 192, 192, "direction_z"), 8),
    }
    return made


if __name__ == "__main__":
    main()
