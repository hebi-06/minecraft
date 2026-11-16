"""2026-11-16 비전 (매지션 라인 — 사용자 "다음 직업으로 마법사 작업"): 원소 공용 비전 묶음 el_arcane 의 매지션 · 아크메이지 대표 그림.

jobs.json 매지션 빛 (남빛 [0.3, 0.36, 0.95] · 금빛 [1, 0.84, 0.36]) · 아크메이지 (밤빛 · 금빛 별). 예전 그림 (rfx_j… — 흰 그림에 빛 두 겹) 과 **같은 틀**:
칸 수 · 칸의 가로세로 비 · 머리 자리 (날아가는 그림의 머리 = 예전 HEAD 몫) · 고리 반지름이 같음 — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 그림만 바뀜.
금서의 세 주문 (불 · 얼음 · 번개) 과 얼음 결정은 원소 빛 (불 주황 · 얼음 하늘 · 번개 보라 + 노랑), 프리즘 레이는 무지개 대신 남빛 · 금빛 광선 (사용자 "무지개 빼").
빛 번짐은 칸 끝에 닿기 전에 사라지게 (win — 칸 끝에서 잘려 네모가 보이지 않게).

매지션 (예전 rfx):
- star (jstar · 256 · 8 되풀이): 떠도는 별 — 돌아가는 네 뿔 별 · 금빛 속 · 기울어진 궤도 고리 둘 (서로 다른 빠르기) · 궤도 구슬 · 반짝임
- bolt (jbolt2 · 240 × 80 · 4): 별 마탄 — 머리 (x 0.58) 의 네 뿔 별 · 가늘어지는 빛 꼬리 · 금가루
- beam (jbeam2 · 256 × 64 · 6): 광선 — 가늘게 → 굵고 흐르는 빛 (흰 심 · 남빛 · 금빛 실 두 가닥) → 가늘어짐, 끝은 뾰족 (조각끼리 겹침)
- shatter (jshatter2 · 256 · 8): 깨지는 마력 방패 — 별 문양 유리 원판 번쩍 → 금 → 부채꼴 유리 조각이 돌며 흩어짐
- fireball · icelance · sparkorb (금서의 주문 · 4 되풀이): 불덩이 (머리 x 0.6) · 얼음 창 (창끝 x 0.9) · 번개 구슬 (머리 x 0.56)
- frost (jfrost · 256 · 6): 얼어붙은 적의 눈꽃 — 반짝 → 자라는 눈꽃 → 육각 테 · 번쩍 → 머묾 → 가지가 부서져 흩어짐
- dash (jdash2 · 256 × 64 · 6): 순간 이동 줄기 — 빛 줄이 앞으로 그어짐 → 머리의 초승달 물결 → 꼬리만 남아 옅어짐
- aura (jaura2 · 128 × 256 · 8 되풀이): 몸에서 솟는 별빛 — 일렁이며 오르는 빛 띠 · 오르는 반짝임 (되풀이 이음 없음)
아크메이지:
- starnode (jstarnode · 256 · 8): 별자리의 별 — 0 번쩍 · 1 피어남 · 2 ~ 5 반짝임 (되풀이) · 6 크게 번쩍 · 7 별빛 조각으로 흩어짐
- fallstar (jfallstar · 256 × 96 · 4): 별똥 — 다섯 뿔 별 머리 (x 0.78) · 긴 꼬리 · 반짝이는 가루
- galaxy (jgalaxy · 2 × 2 · 512 · 8): 별의 소용돌이 (땅에 누움 — 팔 끝 0.9) — 펼쳐짐 → 소용돌이 (되풀이 — 별만 반짝임) → 조여 듦 → 고리 · 번쩍
- halo (jhalo · 2 × 2 · 512 · 8): 빛 고리 (반지름 0.88) — 밝게 → 가는 빛살 · 반짝임 · 조금 퍼지며 사라짐
- aegis (jaegis2 · 204 × 256 · 6): 빛의 방패 (연 모양 · 날개 달린 별 문장) — 빛 줄 → 나타남 → 번쩍 → 버팀 (빛이 비스듬히 지나감)
맞은 자리 (원소 kind arcane — element.frames_of 의 xslash): X 베기 대신 별빛 (네 뿔 별 번쩍 → 고리 · 별가루)

  cd tools && python3 -m jobvfx.arcane --preview 폴더 [--only star,bolt]
"""
import math
import sys

import numpy as np
from scipy import ndimage

from .base import Noise3, WHITE, bloom, compose, grid, preview, ramp, sstep, tiled, to_img
from .element import STYLES, bolt_points, star4
from .mirror import poly

STAR_FRAMES = 8
BOLT_FRAMES = 4
BEAM_FRAMES = 6
SHATTER_FRAMES = 8
SPELL_FRAMES = 4
FROST_FRAMES = 6
DASH_FRAMES = 6
AURA_FRAMES = 8
STARNODE_FRAMES = 8
FALLSTAR_FRAMES = 4
GALAXY_FRAMES = 8
HALO_FRAMES = 8
AEGIS_FRAMES = 6
STARHIT_FRAMES = 8

FIRE = ramp([(0.0, "#1A0400"), (0.16, "#4A0C02"), (0.32, "#8E1E04"), (0.48, "#D8480C"), (0.62, "#FF7E22"), (0.76, "#FFB44A"), (0.88, "#FFE49A"),
             (0.96, "#FFF8E0"), (1.0, "#FFFFFF")])
ICE = ramp([(0.0, "#02121C"), (0.18, "#06324E"), (0.36, "#11628E"), (0.54, "#2E9CCC"), (0.7, "#6CCDEE"), (0.84, "#AEEBFF"), (0.94, "#E4FAFF"),
            (1.0, "#FFFFFF")])
VOLT = ramp([(0.0, "#0E0622"), (0.2, "#2A1466"), (0.4, "#5532C0"), (0.58, "#8A66F2"), (0.72, "#BCA4FF"), (0.86, "#FFF3A8"), (1.0, "#FFFFFF")])


def S0():
    return STYLES["arcane"]


def rect(w, h):
    """가로 w · 세로 h 픽셀 — x 는 [-1, 1], y 는 [-h/w, h/w] (예전 파티클의 세로 몫과 같은 단위, 위가 +)"""
    ky = h / w
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = ((np.arange(h) + 0.5) / h * 2 - 1) * ky
    x, y = np.meshgrid(xs, -ys)
    return x, y, ky


def win(x, y, ky, mx=0.72, my=0.55):
    """부드러운 창 (빛 번짐 · 안개가 칸 끝에 닿기 전에 0 — 잘린 네모가 보이지 않게)"""
    return (1 - sstep(mx, 0.97, np.abs(x))) * (1 - sstep(ky * my, ky * 0.96, np.abs(y)))


def edge(x, y, ky, m=0.04):
    return (1 - sstep(1 - m, 1.0, np.abs(x))) * (1 - sstep(ky * (1 - m * 1.5), ky, np.abs(y)))


def seg_glow(x, y, pts, w):
    """꺾은 선 (점 목록) 까지의 거리로 만든 가는 줄 (exp)"""
    m = np.zeros_like(x)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        ex, ey = bx - ax, by - ay
        L = math.hypot(ex, ey) + 1e-9
        tt = np.clip(((x - ax) * ex + (y - ay) * ey) / (L * L), 0, 1)
        dd = np.hypot(x - (ax + tt * ex), y - (ay + tt * ey))
        m = np.maximum(m, np.exp(-(dd / w) ** 2))
    return m


def star5(x, y, cx, cy, R, rot=0.0, inner=0.45, aa=0.01):
    """다섯 뿔 별 (꼭짓점 하나가 위 — rot 만큼 반시계)"""
    px, py = x - cx, y - cy
    k = 2 * math.pi / 5
    th = np.arctan2(py, px) - rot - math.pi / 2
    a = np.abs(np.mod(th + k / 2, k) - k / 2)
    vx, vy = R * inner * math.cos(k / 2), R * inner * math.sin(k / 2)
    den = np.cos(a) - np.sin(a) * (vx - R) / vy
    rb = R / np.maximum(den, 1e-6)
    return 1 - sstep(rb - aa, rb + aa, np.hypot(px, py))


def gold_star(S, x, y, cx, cy, R, rot, aa, rim=1.0, k=1.0):
    """칠한 다섯 뿔 별: 남빛 테 (조금 큰 별) · 금빛 몸 · 흰 속"""
    outer = star5(x, y, cx, cy, R * 1.2, rot, 0.47, aa)
    body = star5(x, y, cx, cy, R, rot, 0.45, aa)
    r = np.hypot(x - cx, y - cy)
    return [(S.main(0.82), outer * rim * k), (S.accent(0.72 + 0.26 * np.clip(1 - r / R, 0, 1)), body * k),
            (WHITE, body * np.clip(1 - r / (R * 0.55), 0, 1) ** 0.7 * k)]


# ------------------------------------------------------------------ 떠도는 별 (jstar)

def star_frame(S, f, n=256):
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    ph = f / STAR_FRAMES * 2 * math.pi
    layers = [(S.main(0.45), np.exp(-(r / 0.55) ** 2) * 0.4), (S.main(0.75), np.exp(-(r / 0.26) ** 2) * 0.7)]
    for i, (tilt, spd, rx, ry) in enumerate(((0.45, 1.0, 0.72, 0.3), (-0.5, -1.0, 0.7, 0.26))):
        ca, sa = math.cos(tilt), math.sin(tilt)
        px, py = x * ca + y * sa, -x * sa + y * ca
        e = np.hypot(px / rx, py / ry)
        th = np.arctan2(py / ry, px / rx)
        front = 0.55 + 0.45 * np.sin(th)
        ring = np.exp(-((e - 1) / 0.045) ** 2)
        layers += [(S.main(0.7), ring * 0.55 * front), (S.main(0.92), np.exp(-((e - 1) / 0.015) ** 2) * 0.8 * front)]
        for kk in range(2):
            a = ph * spd + kk * math.pi + i
            bx, by = rx * math.cos(a), ry * math.sin(a)
            wx, wy = bx * ca - by * sa, bx * sa + by * ca
            layers += [(S.accent(0.85), np.exp(-((x - wx) ** 2 + (y - wy) ** 2) / 0.0025)), (WHITE, np.exp(-((x - wx) ** 2 + (y - wy) ** 2) / 0.0005))]
    rot = ph / 4
    layers += [(S.accent(0.75), star4(x, y, 0, 0, 0.62, 0.075, rot, aa) * 0.9), (WHITE, star4(x, y, 0, 0, 0.5, 0.04, rot, aa)),
               (S.accent(0.9), star4(x, y, 0, 0, 0.3, 0.05, rot + math.pi / 4, aa) * 0.7), (WHITE, np.exp(-(r / 0.12) ** 2))]
    for kk in range(5):
        a = kk * 1.3 + ph * 0.3
        sx, sy = 0.82 * math.cos(a), 0.78 * math.sin(a * 1.7)
        tw = 0.5 + 0.5 * math.cos(ph * 2 + kk * 2)
        layers.append((WHITE, star4(x, y, sx, sy, 0.07 * tw, 0.01, 0.0, aa)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.55)
    return rgb, a * (1 - sstep(0.94, 0.995, r))


# ------------------------------------------------------------------ 별 마탄 (jbolt2)

def bolt_frame(S, f, w=240, h=80, head=0.58):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1201 + f)
    u = np.clip((head - x) / (head + 0.98), 0, 1)
    behind = ((x < head) & (x > -0.98)).astype(float)
    wv = 0.1 * (1 - u) ** 1.25 + 0.004
    wig = 0.012 * np.sin(x * 14 - f * math.pi / 2) * u
    d = np.abs(y - wig)
    tail = (1 - sstep(wv * 0.7, wv * 1.05 + aa, d)) * behind
    core = (1 - sstep(wv * 0.2, wv * 0.38 + aa, d)) * behind
    glow = np.exp(-(d / (wv * 2.6 + 0.012)) ** 2) * (1 - sstep(head - 0.12, head + 0.12, x)) * (x > -0.98) * (1 - u) ** 0.7
    dust = np.zeros_like(x)
    for i in range(10):
        dx = rng.uniform(-0.85, head - 0.12)
        dy = rng.uniform(-0.12, 0.12) * (head - dx) * 0.8
        dust = np.maximum(dust, star4(x, y, dx, dy, rng.uniform(0.02, 0.035), 0.006, rng.uniform(0, 1), aa) * rng.uniform(0.5, 1))
    hr = np.hypot(x - head, y)
    rot = f * math.pi / 8
    layers = [(S.main(0.45), glow * 0.65 * win(x, y, ky, 0.85, 0.6)), (S.main(0.6 + 0.3 * (1 - u)), tail * (1 - u) ** 0.5),
              (S.main(0.9), core * (1 - u) ** 0.6), (WHITE, core * (1 - u) ** 2.2), (S.accent(0.88), dust),
              (S.main(0.72), np.exp(-(hr / 0.17) ** 2) * 0.85), (S.accent(0.82), star4(x, y, head, 0, 0.32, 0.055, rot, aa) * 0.95),
              (WHITE, star4(x, y, head, 0, 0.26, 0.026, rot, aa)), (WHITE, np.exp(-(hr / 0.055) ** 2))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.02, gain=0.55)
    return rgb, a * edge(x, y, ky)


# ------------------------------------------------------------------ 광선 (jbeam2)

def beam_frame(S, f, w=256, h=64):
    N = Noise3(1221)
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1222 + f)
    prof = [0.2, 0.75, 1.0, 0.95, 0.62, 0.22][f]
    t = f / BEAM_FRAMES
    taper = np.clip((1 - np.abs(x)) / 0.06, 0, 1) ** 0.75  # 끝만 조금 (조각끼리 겹치는 곳이 마디로 보이지 않게)
    hw = (0.19 * prof + 0.014) * taper + 1e-4
    wob = 0.01 * prof * np.sin(x * 9 + t * 2 * math.pi * 2)
    d = np.abs(y - wob)
    body = 1 - sstep(hw * 0.84, hw + aa, d)
    core = 1 - sstep(hw * 0.26, hw * 0.44 + aa, d)
    flow = N.fbm(x * 5 - t * 6, y * 10, 1.0 + t * 2, 3)
    streak = sstep(0.56, 0.74, N.fbm(x * 2.4 - t * 9, y * 26, 3.0, 2)) * (1 - np.clip(d / hw, 0, 1)) ** 0.5
    fil = np.zeros_like(x)
    for s in (-1, 1):
        yy = s * hw * 0.9 + 0.012 * np.sin(x * 23 + s * 1.3 + t * 2 * math.pi * 3)
        fil = np.maximum(fil, np.exp(-((y - yy) / 0.0055) ** 2))
    fil *= float(prof > 0.4) * taper
    sp = np.zeros_like(x)
    hmax = 0.19 * prof + 0.014
    for i in range(7):
        sx = rng.uniform(-0.85, 0.85)
        sy = rng.choice([-1, 1]) * rng.uniform(0.6, 1.2) * hmax
        sp = np.maximum(sp, star4(x, y, sx, sy, rng.uniform(0.025, 0.045), 0.006, 0.3, aa) * prof)
    glow = np.exp(-(d / (hw * 1.7 + 0.02)) ** 2) * win(x, y, ky, 0.9, 0.62)
    layers = [(S.main(0.42), glow * 0.55), (S.main(0.52 + 0.33 * flow), body), (S.main(0.86), body * streak * 0.7), (S.accent(0.86), fil * 0.85),
              (S.main(0.96), core), (WHITE, core * (0.7 + 0.3 * flow)), (S.accent(0.9), sp * 0.9)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.66, sigma=0.03, gain=0.4)
    return rgb, a * edge(x, y, ky, 0.02)


# ------------------------------------------------------------------ 깨지는 마력 방패 (jshatter2)

def _hexagram(x, y, R, aa):
    """육망성 (삼각형 둘) 의 선"""
    m = np.zeros_like(x)
    for s in (0, 1):
        pts = [(R * math.cos(math.pi / 2 + s * math.pi + i * 2 * math.pi / 3), R * math.sin(math.pi / 2 + s * math.pi + i * 2 * math.pi / 3)) for i in range(3)]
        for (ax, ay), (bx, by) in zip(pts, pts[1:] + pts[:1]):
            ex, ey = bx - ax, by - ay
            L = math.hypot(ex, ey)
            along = ((x - ax) * ex + (y - ay) * ey) / L
            perp = np.abs((x - ax) * ey - (y - ay) * ex) / L
            m = np.maximum(m, (1 - sstep(0.004, 0.004 + aa, perp)) * (along > 0) * (along < L))
    return m


def _disc(S, x, y, r, th, R, aa):
    disc = 1 - sstep(R - aa, R + aa, r)
    lit = np.clip(0.5 + 0.5 * np.cos(th - 2.3) * np.clip(r / R, 0, 1), 0, 1)
    rings = np.exp(-((r - R * 0.62) / 0.006) ** 2) + np.exp(-((r - R * 0.8) / 0.005) ** 2)
    return [(S.main(0.3 + 0.18 * (1 - r / R) + 0.12 * lit), disc * 0.72), (S.main(0.62), disc * np.exp(-((r - R * 0.9) / 0.06) ** 2) * 0.6),
            (S.accent(0.78), np.clip(_hexagram(x, y, R * 0.62, aa) + rings, 0, 1) * disc * 0.75),
            (S.accent(0.62), np.exp(-((r - R) / 0.02) ** 2)), (S.accent(0.92), np.exp(-((r - R) / 0.007) ** 2))]


def shatter_frame(S, f, n=256):
    rng = np.random.default_rng(1241)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    R = 0.9
    layers = []
    if f <= 1:
        layers += _disc(S, x, y, r, th, R, aa)
        if f == 0:
            layers += [(S.main(0.75), np.exp(-(r / 0.3) ** 2) * 0.8), (WHITE, star4(x, y, 0, 0, 0.75, 0.05, 0.35, aa)), (WHITE, np.exp(-(r / 0.1) ** 2))]
        else:
            cr = np.zeros_like(x)
            for i in range(11):
                a = i * 2 * math.pi / 11 + rng.uniform(-0.2, 0.2)
                cr = np.maximum(cr, seg_glow(x, y, bolt_points(rng, 0.0, 0.0, R * math.cos(a), R * math.sin(a), depth=3, jag=0.12), 0.006))
            for rr in (0.35, 0.65):
                cr = np.maximum(cr, np.exp(-((r - rr * R) / 0.005) ** 2) * 0.85)
            cr *= (r < R)
            layers += [(S.main(0.95), cr), (WHITE, cr * 0.85), (S.main(0.7), np.exp(-(r / 0.2) ** 2) * 0.5)]
    else:
        k = (f - 2) / (SHATTER_FRAMES - 3)
        vis = 1 - k ** 1.5
        layers.append((S.main(0.6), np.exp(-(r / 0.45) ** 2) * 0.4 * (1 - k) ** 2))
        pieces = []
        for r0, r1, cnt in ((0.0, 0.35, 5), (0.35, 0.65, 8), (0.65, 0.9, 11)):
            edges = (np.arange(cnt) + rng.uniform(-0.25, 0.25, cnt)) * 2 * math.pi / cnt
            for i in range(cnt):
                a0 = edges[i]
                a1 = edges[i + 1] if i + 1 < cnt else edges[0] + 2 * math.pi
                pieces.append((r0 * R, r1 * R, a0, a1))
        for r0, r1, a0, a1 in pieces:
            am = (a0 + a1) / 2
            rc = max(0.16, (r0 + r1) / 2)
            cx0, cy0 = rc * math.cos(am), rc * math.sin(am)
            dist = k ** 0.8 * (0.22 + 0.3 * rng.uniform()) * (1 + rc)
            cx, cy = cx0 + dist * math.cos(am), cy0 + dist * math.sin(am)
            rot = k * rng.uniform(-2.2, 2.2)
            sc = 1 - 0.32 * k
            ca, sa = math.cos(-rot), math.sin(-rot)
            px, py = (x - cx) / sc, (y - cy) / sc
            lx, ly = px * ca - py * sa + cx0, px * sa + py * ca + cy0
            lr = np.hypot(lx, ly)
            la = np.mod(np.arctan2(ly, lx) - a0, 2 * math.pi)
            span = a1 - a0
            gap = 0.012
            arc_in = la * lr
            arc_out = (span - la) * lr
            rin = sstep(r0 + gap - aa, r0 + gap + aa, lr) if r0 > 0 else np.ones_like(lr)
            m = rin * (1 - sstep(r1 - gap - aa, r1 - gap + aa, lr)) * (la < span) * sstep(gap - aa, gap + aa, arc_in) * sstep(gap - aa, gap + aa, arc_out)
            if not m.any():
                continue
            ed = np.minimum(np.abs(r1 - gap - lr), np.minimum(arc_in, arc_out))
            if r0 > 0:
                ed = np.minimum(ed, np.abs(lr - r0 - gap))
            rim = (1 - sstep(0.008, 0.022, ed)) * m
            tone = rng.uniform(0.32, 0.5)
            lit = np.clip(0.5 + 0.9 * ((lx - cx0) * -0.5 + (ly - cy0) * 0.8), 0, 1)
            layers += [(S.main(tone + 0.25 * lit), m * 0.82 * vis), (S.accent(0.8), rim * 0.85 * vis), (WHITE, rim * lit * 0.6 * vis)]
            if r1 > 0.8 * R:
                layers.append((S.accent(0.9), m * np.exp(-((lr - R) / 0.02) ** 2) * vis))
        for i in range(12):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.3, 0.95) * (0.6 + 0.6 * k)
            layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.05 * (1 - k), 0.008, a, aa) * (1 - k)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.022, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 금서의 주문

def fireball_frame(f, w=256, h=96, head=0.6):
    N = Noise3(1261)
    x, y, ky = rect(w, h)
    rng = np.random.default_rng(1262 + f)
    t = f / SPELL_FRAMES
    R = 0.2
    xt = -0.8
    tx = x + 0.05 * (N.fbm(x * 4 + t * 4, y * 6, 1 + t * 4, 3) - 0.5)
    ty = y + 0.1 * (N.fbm(x * 5 + t * 5, y * 7 + 3, 2 + t * 4, 3) - 0.5) * np.clip((head - x) / 0.7, 0, 1.6)
    u = np.clip((head - tx) / (head - xt), 0, 1)
    halfw = R * 1.05 * np.clip(1 - u, 0, 1) ** 0.6
    body_sdf = np.where(tx < head, np.abs(ty) - halfw, 9.0)
    hr = np.hypot(tx - head, ty)
    s = np.minimum(hr - R, body_sdf)
    m = 1 - sstep(-0.014, 0.012, s)
    heat = 1 - 0.3 * np.clip(hr / R, 0, 1) - u
    heat = np.clip(heat * (0.8 + 0.45 * N.fbm(x * 9 - t * 8, y * 9, 4 + t * 4, 3)) - 0.12 * np.clip(np.abs(ty) / np.maximum(halfw, 1e-3), 0, 1), 0, 1)
    tongues = np.zeros_like(x)
    for i in range(6):
        y0 = rng.uniform(-0.07, 0.07)
        yy = y0 + 0.045 * np.sin(x * 8 + t * 2 * math.pi + i * 1.7) * np.clip(head - x, 0, 1)
        L0 = rng.uniform(-0.95, -0.6)
        along = (x > L0) & (x < head - 0.1)
        tongues = np.maximum(tongues, np.exp(-((y - yy) / 0.012) ** 2) * along * np.clip((x - L0) / 0.4, 0, 1))
    emb = np.zeros_like(x)
    for i in range(12):
        ex = rng.uniform(-0.95, head - 0.25)
        ey = rng.uniform(-0.28, 0.28) * np.clip((head - ex) / 1.2, 0.3, 1)
        emb = np.maximum(emb, np.exp(-((x - ex) ** 2 + (y - ey) ** 2) / rng.uniform(0.0002, 0.0005)))
    hr0 = np.hypot(x - head, y)
    layers = [(FIRE(0.5), np.exp(-(hr0 / 0.36) ** 2) * 0.5 * win(x, y, ky, 0.8, 0.5)), (FIRE(0.28 + 0.7 * heat), m * (0.5 + 0.5 * sstep(0.0, 0.35, heat))),
              (FIRE(0.72), tongues * 0.6 * (1 - u)), (FIRE(0.86), emb * 0.9),
              (FIRE(0.92), np.exp(-(hr0 / (R * 0.75)) ** 2)), (WHITE, np.clip(np.exp(-(hr0 / (R * 0.45)) ** 2) * 1.3, 0, 1))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.62, sigma=0.03, gain=0.45)
    return rgb, a * edge(x, y, ky)


def icelance_frame(f, w=256, h=64, head=0.9):
    N = Noise3(1281)
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1282 + f)
    t = f / SPELL_FRAMES
    hw = np.where(x > 0.32, 0.078 * np.clip((head - x) / (head - 0.32), 0, 1), 0.034 + 0.044 * np.clip((x + 0.55) / 0.87, 0, 1)) * (x > -0.55)
    blade = (1 - sstep(hw - aa, hw + aa, np.abs(y))) * (x < head)
    fins = np.zeros_like(x)
    for s in (-1, 1):
        a = [(-0.26, s * 0.03), (-0.5, s * 0.13), (-0.56, s * 0.03)]
        b = [(0.3, s * 0.06), (0.2, s * 0.095), (0.17, s * 0.05)]
        if s < 0:
            a, b = a[::-1], b[::-1]
        fins = np.maximum(fins, np.maximum(poly(x, y, a, aa), poly(x, y, b, aa)))
    body = np.clip(blade + fins, 0, 1)
    facet = np.where(y > 0, 0.84, 0.56) + 0.03 * np.sin(x * 30)
    seams = np.zeros_like(x)
    for sx in (-0.3, 0.0, 0.32):
        seams = np.maximum(seams, np.exp(-((x - sx - 0.25 * np.abs(y) / 0.08) / 0.006) ** 2))
    ridge = np.exp(-((y - 0.004) / 0.004) ** 2) * (x > -0.5) * (x < head - 0.02)
    mist = N.fbm(x * 6 + t * 3, y * 12, 2 + t * 2, 3)
    trail = np.clip(mist * 1.6 - 0.45, 0, 1) * np.clip((-0.45 - x) / 0.35, 0, 1) * sstep(-0.99, -0.85, x) * np.exp(-(y / (0.06 + 0.1 * np.clip(-0.45 - x, 0, 1))) ** 2)
    spk = np.zeros_like(x)
    for i in range(8):
        sx, sy = rng.uniform(-0.95, -0.45), rng.uniform(-0.14, 0.14)
        spk = np.maximum(spk, star4(x, y, sx, sy, rng.uniform(0.02, 0.04), 0.006, 0.4, aa))
    glint = star4(x, y, head - 0.015, 0, 0.14, 0.012, 0.0, aa) * (0.65 + 0.35 * (f % 2))
    layers = [(ICE(0.5), np.exp(-(y / 0.11) ** 2) * (x > -0.65) * (x < head) * 0.35 * win(x, y, ky, 0.9, 0.6)), (ICE(0.66), trail * 0.75),
              (ICE(facet), body), (ICE(0.38), body * seams * 0.6), (WHITE, ridge * blade * 0.9), (WHITE, spk * 0.9), (WHITE, glint)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.68, sigma=0.025, gain=0.45)
    return rgb, a * edge(x, y, ky)


def sparkorb_frame(f, w=256, h=128, head=0.56):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1301 + f)
    hr = np.hypot(x - head, y)
    bolts = np.zeros_like(x)
    tips = np.zeros_like(x)
    for i in range(6):
        y1 = rng.uniform(-0.38, 0.38)
        x1 = rng.uniform(-0.92, -0.55)
        pts = bolt_points(rng, head - 0.1, rng.uniform(-0.04, 0.04), x1, y1, depth=4, jag=0.2)
        fade = np.clip((x - x1) / (head - x1), 0.25, 1)
        bolts = np.maximum(bolts, seg_glow(x, y, pts, 0.007) * fade)
        tips = np.maximum(tips, np.exp(-((x - x1) ** 2 + (y - y1) ** 2) / 0.0006))
    glow = np.exp(-(hr / 0.3) ** 2) * 0.7 * win(x, y, ky, 0.8, 0.6)
    bglow = np.clip(ndimage.gaussian_filter(bolts, w * 0.012) * 2.5, 0, 1) * win(x, y, ky, 0.9, 0.8)
    spk = np.zeros_like(x)
    for i in range(6):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.17, 0.26)
        spk = np.maximum(spk, star4(x, y, head + rr * math.cos(a), rr * math.sin(a) * 0.9, rng.uniform(0.03, 0.05), 0.007, a, aa))
    layers = [(VOLT(0.45), glow * 0.75), (VOLT(0.55), bglow * 0.7), (VOLT(0.78), bolts), (WHITE, bolts ** 2.5), (VOLT(0.85), tips * 0.9),
              (VOLT(0.62), 1 - sstep(0.15, 0.17, hr)), (VOLT(0.8), 1 - sstep(0.105, 0.125, hr)), (VOLT(0.9), 1 - sstep(0.06, 0.08, hr)),
              (WHITE, 1 - sstep(0.03, 0.05, hr)), (VOLT(0.88), spk)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.03, gain=0.55)
    return rgb, a * edge(x, y, ky)


# ------------------------------------------------------------------ 얼음 결정 (jfrost)

def _flake(x, y, R, aa, grow=1.0):
    """여섯 가지 눈꽃 (가지마다 잔가지 셋 — 바깥으로 갈수록 짧음) — 알파"""
    m = np.zeros_like(x)
    L = R * grow
    if L <= 1e-3:
        return m
    for i in range(6):
        a = math.pi / 2 + i * math.pi / 3
        ca, sa = math.cos(a), math.sin(a)
        along = x * ca + y * sa
        perp = np.abs(-x * sa + y * ca)
        wv = 0.017 * (1 - 0.6 * np.clip(along / L, 0, 1))
        m = np.maximum(m, (1 - sstep(wv, wv + aa, perp)) * (along > 0) * (along < L))
        for p, ln in ((0.38, 0.32), (0.6, 0.26), (0.8, 0.16)):
            bx, by = p * L * ca, p * L * sa
            for s in (-1, 1):
                b = a + s * math.pi / 3
                cb, sb = math.cos(b), math.sin(b)
                al = (x - bx) * cb + (y - by) * sb
                pp = np.abs(-(x - bx) * sb + (y - by) * cb)
                bl = ln * L
                ww = 0.011 * (1 - 0.6 * np.clip(al / bl, 0, 1))
                m = np.maximum(m, (1 - sstep(ww, ww + aa, pp)) * (al > 0) * (al < bl))
    return m


def frost_frame(f, n=256):
    rng = np.random.default_rng(1321)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    hexd = r * np.cos(np.mod(th - math.pi / 2, math.pi / 3) - math.pi / 6)
    layers = []
    if f == 0:
        layers += [(ICE(0.6), np.exp(-(r / 0.22) ** 2) * 0.7), (WHITE, star4(x, y, 0, 0, 0.35, 0.03, 0.0, aa)), (ICE(0.9), _flake(x, y, 0.16, aa)),
                   (WHITE, np.exp(-(r / 0.05) ** 2))]
    elif f <= 4:
        grow = [0, 0.62, 1.0, 1.0, 1.0][f]
        R = 0.66
        fl = _flake(x, y, R, aa, grow)
        hexm = np.exp(-((hexd - 0.8) / 0.012) ** 2) * float(f >= 2)
        core = 1 - sstep(0.11 - aa, 0.11 + aa, hexd)
        shim = 0.75 + 0.25 * math.cos(f * 2.1)
        layers += [(ICE(0.55), np.exp(-(r / 0.6) ** 2) * 0.45), (ICE(0.62), fl), (ICE(0.9), fl * np.clip(1 - r / R, 0, 1) ** 0.4),
                   (WHITE, fl * np.exp(-((r - 0.3) / 0.12) ** 2) * shim * 0.6), (ICE(0.78), hexm * 0.9), (WHITE, hexm * 0.5), (ICE(0.72), core),
                   (WHITE, core * 0.6)]
        if f == 2:
            layers += [(WHITE, star4(x, y, 0, 0, 0.9, 0.04, 0.0, aa)), (WHITE, np.exp(-(r / 0.12) ** 2))]
        for i in range(4):
            a = i * math.pi / 2 + 0.6 + f * 0.4
            layers.append((WHITE, star4(x, y, 0.78 * math.cos(a), 0.78 * math.sin(a), 0.07 * float(f >= 2) * (0.6 + 0.4 * math.cos(f + i)), 0.01, 0.3, aa)))
    else:
        # 가지 여섯이 떨어져 바깥으로 (조금씩 돌며) — 가지 하나 = 눈꽃을 그 가지 쪽 부채꼴로 잘라 옮긴 것
        fl_all = np.zeros_like(x)
        for i in range(6):
            a = math.pi / 2 + i * math.pi / 3
            spin = rng.uniform(-0.5, 0.5)
            off = 0.2 + rng.uniform(0, 0.08)
            ox, oy = off * math.cos(a), off * math.sin(a)
            ca, sa = math.cos(-spin), math.sin(-spin)
            px, py = x - ox, y - oy
            lx, ly = px * ca - py * sa, px * sa + py * ca
            fl = _flake(lx, ly, 0.6, aa)
            la = np.abs(np.mod(np.arctan2(ly, lx) - a + math.pi, 2 * math.pi) - math.pi)
            fl_all = np.maximum(fl_all, fl * (la < math.pi / 6) * (np.hypot(lx, ly) > 0.13))
        layers += [(ICE(0.5), np.exp(-(r / 0.55) ** 2) * 0.25), (ICE(0.66), fl_all * 0.85), (WHITE, fl_all * 0.35)]
        layers.append((ICE(0.6), (1 - sstep(0.09, 0.1, hexd)) * 0.6))
        for i in range(8):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.55, 0.9)
            layers.append((WHITE, np.exp(-((x - rr * math.cos(a)) ** 2 + (y - rr * math.sin(a)) ** 2) / 0.0005)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.66, sigma=0.022, gain=0.45)
    return rgb, a * (1 - sstep(0.94, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 순간 이동 줄기 (jdash2)

def dash_frame(S, f, w=256, h=64):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1341)
    lines = np.zeros_like(x)
    cores = np.zeros_like(x)
    heads = np.zeros_like(x)
    for i in range(7):
        yy = (i - 3) / 3 * 0.19 + rng.uniform(-0.012, 0.012)
        w0 = 0.006 + 0.004 * (1 - abs(i - 3) / 3)
        hx = [0.55, 0.92, 0.95, 0.9, 0.8, 0.62][f] - abs(i - 3) * 0.05 + rng.uniform(-0.04, 0.04)
        tl = [-0.4, -0.95, -0.95, -0.55, -0.15, 0.25][f] + rng.uniform(-0.08, 0.08) + (rng.uniform(0, 0.3) if f >= 4 else 0.0)
        along = ((x > tl) & (x < hx)).astype(float)
        q = np.clip((x - tl) / max(1e-3, hx - tl), 0, 1)
        dd = np.abs(y - yy)
        lines = np.maximum(lines, (1 - sstep(w0 * 1.6, w0 * 1.6 + aa, dd)) * along * q ** 0.8)
        cores = np.maximum(cores, (1 - sstep(w0 * 0.5, w0 * 0.5 + aa, dd)) * along * q ** 1.5)
        heads = np.maximum(heads, np.exp(-((x - hx) ** 2 / 0.0008 + (y - yy) ** 2 / 0.0002)) * float(f <= 3))
    k = [0.6, 1.0, 1.0, 0.85, 0.6, 0.35][f]
    arc = np.zeros_like(x)
    if 2 <= f <= 3:
        cx = 0.62 + 0.12 * (f - 2)
        ar = np.hypot((x - cx) / 0.32, y / ky * 0.95)
        arc = np.exp(-((ar - 1) / 0.04) ** 2) * (x > cx + 0.08) * (1 - 0.4 * (f - 2))
    layers = [(S.main(0.45), np.clip(lines * 2, 0, 1) * 0.35 * k * win(x, y, ky, 0.95, 0.75)), (S.main(0.7), lines * k), (S.main(0.92), cores * k),
              (WHITE, cores * k * 0.8), (S.accent(0.9), heads), (S.main(0.85), arc * 0.9), (WHITE, arc * 0.5)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.66, sigma=0.03, gain=0.35)
    return rgb, a * edge(x, y, ky, 0.02)


# ------------------------------------------------------------------ 몸에서 솟는 별빛 (jaura2)

def ribbons(x, y, ky, f, frames, rng, n=11, base_w=0.13, sway=0.1):
    """오르는 빛 띠 (불꽃 혀처럼 — 아래 넓고 위로 가늘어져 흩어짐, 되풀이 이음 없음: 물결이 frames 칸에 한 주기 위로): 띠 알파 · 띠 가운데 밝기 · 높이 몫"""
    ph = f / frames * 2 * math.pi
    hgt = np.clip((y + ky) / (2 * ky), 0, 1)
    out = np.zeros_like(x)
    lit = np.zeros_like(x)
    for i in range(n):
        x0 = (i - (n - 1) / 2) / ((n - 1) / 2) * 0.66 + rng.uniform(-0.05, 0.05)
        k1 = rng.uniform(4.5, 7.5)
        top = rng.uniform(0.55, 0.95)
        xc = x0 * (1 - 0.3 * hgt) + sway * np.sin(y * k1 - ph + i * 1.9) * hgt ** 0.7
        wv = np.maximum(base_w * np.clip(1 - hgt / top, 0, 1) ** 0.9 * (0.8 + 0.2 * np.sin(y * k1 * 0.5 - ph * 2 + i)), 1e-4)
        dd = np.abs(x - xc) / wv
        m = np.clip(1 - dd, 0, 1) ** 0.8 * (hgt < top)
        out = np.maximum(out, m * (0.45 + 0.55 * (1 - hgt / top)))
        lit = np.maximum(lit, np.clip(1 - dd * 2.4, 0, 1) * np.clip(1 - hgt / top, 0, 1) ** 0.5)
    return out, lit, hgt


def aura_frame(S, f, w=128, h=256):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1361)
    body, lit, hgt = ribbons(x, y, ky, f, AURA_FRAMES, rng)
    spk = np.zeros_like(x)
    period = 2 * ky
    for i in range(12):
        sx = rng.uniform(-0.75, 0.75)
        y0 = rng.uniform(0, period)
        sy = (y0 + f / AURA_FRAMES * period * (1 if i % 2 else 2)) % period - ky
        life = 1 - abs((sy + ky) / period * 2 - 1)
        spk = np.maximum(spk, star4(x, y, sx, sy, 0.08 * life, 0.014, 0.0, aa) * life)
    column = np.exp(-(x / 0.55) ** 2) * np.clip(1 - hgt, 0, 1) ** 1.2
    base = np.exp(-((y + ky * 0.92) / 0.07) ** 2) * np.exp(-(x / 0.8) ** 2)
    layers = [(S.main(0.38), column * 0.45 * win(x, y, ky, 0.85, 0.85)), (S.main(0.5 + 0.3 * (1 - hgt)), body * 0.75),
              (S.main(0.82), lit * 0.85), (WHITE, lit * (1 - hgt) ** 1.5 * 0.55), (S.accent(0.85), spk), (S.main(0.85), base * 0.7)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.64, sigma=0.025, gain=0.4)
    return rgb, a * edge(x, y, ky, 0.03)


# ------------------------------------------------------------------ 별자리의 별 (jstarnode)

def starnode_frame(S, f, n=256):
    rng = np.random.default_rng(1401)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    layers = []
    if f == 0:
        layers += [(S.main(0.6), np.exp(-(r / 0.3) ** 2) * 0.7), (S.main(0.85), star4(x, y, 0, 0, 0.88, 0.022, 0.0, aa, short=0.25)),
                   (WHITE, star4(x, y, 0, 0, 0.7, 0.012, 0.0, aa, short=0.2)), (WHITE, np.exp(-(r / 0.06) ** 2))]
    elif f <= 6:
        R, L, tw, rays = {1: (0.19, 0.5, 0.8, 0.55), 6: (0.4, 0.98, 1.25, 1.0)}.get(f, (0.3, 0.85, [1.0, 0.82, 0.96, 0.78][(f - 2) % 4], 0.9))
        rr = 0.035 * math.sin((f - 2) / 4 * 2 * math.pi) if 2 <= f <= 5 else 0.0
        layers += [(S.main(0.5), np.exp(-(r / (R * 1.5)) ** 2) * 0.6 * min(1.0, tw) * (1 - sstep(0.55, 0.92, r))),
                   (S.main(0.8), star4(x, y, 0, 0, L, 0.03, 0.08 + rr, aa, short=0.3) * rays),
                   (WHITE, star4(x, y, 0, 0, L * 0.8, 0.012, 0.08 + rr, aa, short=0.3) * rays)]
        layers += gold_star(S, x, y, 0, 0, R, 0.0, aa)
        if f == 6:
            layers.append((WHITE, np.exp(-(r / 0.3) ** 2) * 0.55 * (1 - sstep(0.5, 0.9, r))))
        for i in range(4):
            a = 0.5 + i * math.pi / 2 + ((f - 2) * math.pi / 8 if 2 <= f <= 5 else 0)
            d = 0.6 if f != 1 else 0.42
            s = 0.07 * (0.6 + 0.4 * math.cos(f * 1.7 + i * 2.3))
            layers.append((WHITE, star4(x, y, d * math.cos(a), d * math.sin(a), s, 0.01, 0.3, aa)))
    else:
        layers += gold_star(S, x, y, 0, 0, 0.14, 0.4, aa, k=0.5)
        for i in range(10):
            a = i * 2 * math.pi / 10 + rng.uniform(-0.2, 0.2)
            d = rng.uniform(0.55, 0.82)
            cx, cy = d * math.cos(a), d * math.sin(a)
            ex, ey = math.cos(a), math.sin(a)
            along = (x - cx) * ex + (y - cy) * ey
            perp = np.abs(-(x - cx) * ey + (y - cy) * ex)
            tail = (1 - sstep(0.008, 0.012, perp)) * (along < 0) * (along > -0.16) * (1 + along / 0.16)
            layers += [(S.main(0.75), tail * 0.7), (WHITE, star4(x, y, cx, cy, 0.06, 0.012, a, aa))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.025, gain=0.55)
    return rgb, a * (1 - sstep(0.94, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 별똥 (jfallstar)

def fallstar_frame(S, f, w=256, h=96, head=0.78):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1421)
    u = np.clip((head - x) / (head + 0.98), 0, 1)
    behind = ((x < head) & (x > -0.98)).astype(float)
    wv = 0.11 * (1 - u) ** 1.15 + 0.004
    d = np.abs(y)
    tail = (1 - sstep(wv * 0.7, wv * 1.05 + aa, d)) * behind
    core = (1 - sstep(wv * 0.18, wv * 0.34 + aa, d)) * behind
    glow = np.exp(-(d / (wv * 2.4 + 0.012)) ** 2) * (1 - sstep(head - 0.12, head + 0.12, x)) * (x > -0.98) * (1 - u) ** 0.6
    dust = np.zeros_like(x)
    for i in range(12):
        sx = rng.uniform(-0.9, head - 0.18)
        sy = rng.uniform(-1, 1) * (0.1 * (1 - (head - sx) / 1.8) + 0.02)
        tw = 0.5 + 0.5 * math.cos(f / FALLSTAR_FRAMES * 2 * math.pi + i * 2.1)
        dust = np.maximum(dust, star4(x, y, sx, sy, rng.uniform(0.025, 0.04) * (0.6 + 0.4 * tw), 0.006, math.pi / 4, aa) * (0.4 + 0.6 * tw))
    rot = f / FALLSTAR_FRAMES * 2 * math.pi / 5
    hr = np.hypot(x - head, y)
    layers = [(S.main(0.42), glow * 0.65 * win(x, y, ky, 0.88, 0.6)), (S.main(0.6 + 0.28 * (1 - u)), tail * (1 - u) ** 0.4),
              (S.accent(0.82), core * (1 - u) ** 0.8), (WHITE, core * (1 - u) ** 3), (S.accent(0.9), dust), (S.main(0.7), np.exp(-(hr / 0.2) ** 2) * 0.8)]
    layers += gold_star(S, x, y, head, 0, 0.17, rot, aa)
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.62, sigma=0.022, gain=0.55)
    return rgb, a * edge(x, y, ky)


# ------------------------------------------------------------------ 별의 소용돌이 (jgalaxy — 2 × 2)

def galaxy_frame(S, f, n=512):
    N = Noise3(1441)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    layers = []
    ext = [0.18, 0.55, 1.0, 1.0, 1.0, 1.0, 0.55, 0.0][f]
    if f <= 6:
        Rm = 0.9 * ext
        arms = np.zeros_like(x)
        for i in range(2):
            arm_th = i * math.pi + 4.2 * np.clip(r / 0.9, 0, 1) ** 0.75
            dd = np.abs(np.mod(th - arm_th + math.pi, 2 * math.pi) - math.pi) * np.maximum(r, 0.02)
            wv = 0.05 + 0.12 * np.clip(r / 0.9, 0, 1)
            arms = np.maximum(arms, np.exp(-(dd / wv) ** 2))
        dust = N.fbm(x * 6, y * 6, 1.0, 4)
        rad = 1 - sstep(Rm * 0.75, Rm + 0.02, r)
        arms = arms * rad * (0.75 + 0.5 * (dust - 0.5))
        lanes = sstep(0.55, 0.7, dust) * arms
        tw = [0.0, 0.3, 1.0, 0.85, 0.95, 0.8, 0.6, 0][f]
        clouds = np.clip(N.fbm(x * 3.2 + 7, y * 3.2, 2.0, 4) * 1.8 - 0.55, 0, 1) * np.clip(arms * 2.2, 0, 1)
        stars = np.zeros_like(x)
        srng = np.random.default_rng(1443)
        for i in range(150):
            rr = 0.9 * math.sqrt(srng.uniform(0.02, 1))
            ai = i % 2 * math.pi + 4.2 * (rr / 0.9) ** 0.75 + srng.normal(0, 0.28)
            s = srng.uniform(0.006, 0.014) * (0.6 + 0.4 * math.cos(f * 2.3 + i * 1.7))
            if rr > Rm:
                continue
            stars = np.maximum(stars, np.exp(-((x - rr * math.cos(ai)) ** 2 + (y - rr * math.sin(ai)) ** 2) / (s * s)))
        bulge = np.exp(-(r / (0.16 + 0.06 * ext + 0.08 * float(f == 6))) ** 2)
        layers += [(S.main(0.42), np.exp(-(r / max(0.2, Rm * 0.9)) ** 2) * 0.3), (S.main(0.5), clouds * 0.5), (S.main(0.5 + 0.3 * np.clip(1 - r, 0, 1)), arms * 0.85),
                   (S.main(0.78), arms ** 2 * 0.8), (S.main(0.16), lanes * 0.5), (S.accent(0.78), arms ** 3 * 0.35 * (r < 0.5)),
                   (WHITE, stars * (0.5 + 0.5 * tw)), (S.accent(0.85), bulge * 0.9), (WHITE, np.clip(bulge * 1.3 - 0.2, 0, 1))]
        for i in range(6):
            a = srng.uniform(0, 2 * math.pi)
            rr = srng.uniform(0.25, 0.85) * max(ext, 0.2)
            layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.05 * tw, 0.006, 0.3, aa)))
    else:
        layers += [(S.main(0.5), np.exp(-(r / 0.5) ** 2) * 0.55), (S.main(0.75), np.exp(-((r - 0.82) / 0.03) ** 2)), (WHITE, np.exp(-((r - 0.82) / 0.008) ** 2)),
                   (S.accent(0.85), np.exp(-((r - 0.76) / 0.006) ** 2) * 0.7), (S.main(0.85), star4(x, y, 0, 0, 0.85, 0.04, 0.35, aa)),
                   (WHITE, star4(x, y, 0, 0, 0.7, 0.016, 0.35, aa)), (WHITE, np.exp(-(r / 0.07) ** 2))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.018, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 빛 고리 (jhalo — 2 × 2)

def halo_frame(S, f, n=512, rays=True):
    rng = np.random.default_rng(1461)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    k = [1.0, 0.95, 0.88, 0.78, 0.66, 0.52, 0.36, 0.2][f]
    R = 0.86 + 0.03 * f / (HALO_FRAMES - 1)
    layers = [(S.main(0.3), np.exp(-(np.clip(R - r, 0, None) / 0.22) ** 1.5) * (r < R) * 0.45 * k)]
    if rays:
        ray = np.zeros_like(x)
        for i in range(40):
            a = i * 2 * math.pi / 40 + rng.uniform(-0.04, 0.04)
            ln = rng.uniform(0.12, 0.3)
            dd = np.abs(np.mod(th - a + math.pi, 2 * math.pi) - math.pi) * r
            ray = np.maximum(ray, (1 - sstep(0.002, 0.005, dd)) * (r < R) * (r > R - ln) * ((r - (R - ln)) / ln))
        layers.append((S.main(0.75), ray * 0.6 * k))
    ticks = np.zeros_like(x)
    for i in range(36):
        a = i * 2 * math.pi / 36
        dd = np.abs(np.mod(th - a + math.pi, 2 * math.pi) - math.pi) * r
        ticks = np.maximum(ticks, (1 - sstep(0.003, 0.006, dd)) * (np.abs(r - R * 0.93) < (0.025 if i % 3 == 0 else 0.012)))
    spk = np.zeros_like(x)
    for i in range(12):
        a = rng.uniform(0, 2 * math.pi)
        rr = R * rng.uniform(0.95, 1.03)
        tw = 0.5 + 0.5 * math.cos(f * 1.9 + i * 2.2)
        spk = np.maximum(spk, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.045 * tw, 0.006, 0.3, aa) * tw)
    # 2026-11-16 영상 (CI 43777285755): 예전 고리보다 가늘어 멀리서 약했음 → 띠 · 빛 번짐을 굵게
    layers += [(S.main(0.5), np.exp(-((r - R) / 0.09) ** 2) * 0.5 * k), (S.main(0.64), np.exp(-((r - R) / 0.045) ** 2) * k),
               (S.accent(0.82), ticks * 0.75 * k), (S.accent(0.85), np.exp(-((r - R * 0.885) / 0.005) ** 2) * 0.75 * k),
               (S.main(0.9), np.exp(-((r - R) / 0.018) ** 2) * k), (WHITE, np.exp(-((r - R) / 0.007) ** 2) * k), (WHITE, spk * k)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.012, gain=0.55)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 빛의 방패 (jaegis2 — 204 × 256)

def _kite(x, y):
    """연 모양 방패의 거리 (안 −, 대략): 위 가장자리 y 0.95 (조금 둥긂) · 어깨 x ±0.75 (y 0.25) · 아래 끝 (0, −1.1)"""
    top = y - (0.95 - 0.08 * x * x)
    yy = np.clip((0.25 - y) / 1.35, 0, 1)
    hw = 0.75 * (1 - yy ** 1.6)
    side = np.abs(x) - hw
    return np.maximum(np.maximum(top, side), -1.1 - y)


def aegis_frame(S, f, w=204, h=256):
    x, y, ky = rect(w, h)
    y = y / ky * 1.25  # 세로를 [-1.25, 1.25] 로
    aa = 2.5 / w
    d = _kite(x, y)
    inside = 1 - sstep(-aa, aa, d)
    rim_band = 1 - sstep(0.035, 0.035 + aa, np.abs(d + 0.02))
    layers = []
    if f == 0:
        line = np.exp(-(np.abs(d) / 0.012) ** 2)
        cross = np.exp(-(x / 0.012) ** 2) * (np.abs(y) < 1.0) + np.exp(-((y - 0.2) / 0.012) ** 2) * (np.abs(x) < 0.55)
        layers += [(S.main(0.8), np.clip(line * 0.8 + cross, 0, 1) * 0.85), (WHITE, np.clip(cross, 0, 1) * 0.7)]
        for sx in (-0.6, 0.6):
            layers.append((WHITE, star4(x, y, sx, 0.2, 0.12, 0.012, 0.0, aa)))
    else:
        lit = np.clip(0.5 - 0.35 * x + 0.25 * y, 0, 1)
        emb = np.zeros_like(x)
        for s in (-1, 1):
            for i in range(5):
                a = math.radians(160 - i * 16) if s < 0 else math.radians(20 + i * 16)
                L = 0.5 - i * 0.05
                ca, sa = math.cos(a), math.sin(a)
                px, py = x - s * 0.08, y - 0.12
                al = px * ca + py * sa
                pp = np.abs(-px * sa + py * ca)
                ww = 0.035 * (1 - np.clip(al / L, 0, 1)) + 0.006
                emb = np.maximum(emb, (1 - sstep(ww, ww + aa, pp)) * (al > 0.05) * (al < L))
        star = star4(x, y, 0, 0.12, 0.42, 0.06, 0.0, aa, short=0.35)
        layers += [(S.main(0.25 + 0.2 * lit), inside * 0.8), (S.main(0.45 + 0.25 * lit), inside * np.exp(-((d + 0.12) / 0.1) ** 2) * 0.6),
                   (S.accent(0.62), rim_band * 0.95), (S.accent(0.9), np.exp(-((d + 0.02) / 0.008) ** 2) * 0.9),
                   (S.main(0.85), np.exp(-((d + 0.07) / 0.006) ** 2) * inside * 0.6), (S.accent(0.82), emb * inside * 0.9), (S.accent(0.9), star * inside),
                   (WHITE, star * np.exp(-(x ** 2 + (y - 0.12) ** 2) / 0.02))]
        for sx, sy in ((-0.35, 0.62), (0.35, 0.62), (0, 0.75)):
            layers.append((WHITE, star4(x, y, sx, sy, 0.06, 0.01, 0.0, aa) * inside))
        if f == 1:
            layers = [(c, al * 0.75) for c, al in layers]
        if f == 2:
            layers += [(WHITE, star4(x, y, 0, 0.12, 0.95, 0.05, 0.0, aa, short=0.3)), (WHITE, np.exp(-(x ** 2 + (y - 0.12) ** 2) / 0.05) * 0.8)]
        if f >= 3:
            p = [-0.6, 0.05, 0.7][f - 3]
            sweep = np.exp(-(((x + y * 0.5) - p) / 0.07) ** 2) * 0.55 + np.exp(-(((x + y * 0.5) - p - 0.18) / 0.025) ** 2) * 0.4
            layers.append((WHITE, sweep * inside))
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.64, sigma=0.022, gain=0.45)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x))) * (1 - sstep(1.2, 1.245, np.abs(y)))


# ------------------------------------------------------------------ 별빛 맞음 (X 베기 대신 — element.frames_of 가 씀, 2 × 2)

def starhit_frame(S, f, n=512, frames=STARHIT_FRAMES):
    rng = np.random.default_rng(1341)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    k = f / (frames - 1)
    grow = min(1.0, (f + 1) / 2.5)
    fade = 1.0 if f < 3 else max(0.0, 1 - (f - 3) / (frames - 3.5))
    rot = 0.25 + 0.15 * k
    layers = [(S.main(0.5), np.exp(-(r / (0.35 + 0.3 * k)) ** 2) * 0.45 * fade),
              (S.accent(0.8), star4(x, y, 0, 0, 0.9 * grow, 0.08 * (1 - 0.5 * k), rot, aa) * fade),
              (WHITE, star4(x, y, 0, 0, 0.75 * grow, 0.035 * (1 - 0.5 * k), rot, aa) * fade),
              (S.main(0.85), star4(x, y, 0, 0, 0.5 * grow, 0.05, rot + math.pi / 4, aa) * fade * 0.8),
              (WHITE, np.exp(-(r / (0.1 * (1 - 0.6 * k) + 0.01)) ** 2) * fade)]
    R = 0.2 + 0.65 * k
    ring = np.exp(-((r - R) / (0.02 + 0.02 * k)) ** 2) * (1 - k) ** 0.9 * (f >= 1)
    layers += [(S.main(0.8), ring * 0.8), (S.accent(0.9), np.exp(-((r - R * 0.92) / 0.006) ** 2) * (1 - k) * (f >= 1) * 0.7)]
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        rr = (0.25 + 0.7 * k) * rng.uniform(0.7, 1.1)
        layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.06 * (1 - k), 0.01, a, aa) * (1 - k) * (f >= 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.02, gain=0.55)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 묶음

FRAMES = {
    "star": (STAR_FRAMES, lambda S, f: star_frame(S, f)),
    "bolt": (BOLT_FRAMES, lambda S, f: bolt_frame(S, f)),
    "beam": (BEAM_FRAMES, lambda S, f: beam_frame(S, f)),
    "shatter": (SHATTER_FRAMES, lambda S, f: shatter_frame(S, f)),
    "fireball": (SPELL_FRAMES, lambda S, f: fireball_frame(f)),
    "icelance": (SPELL_FRAMES, lambda S, f: icelance_frame(f)),
    "sparkorb": (SPELL_FRAMES, lambda S, f: sparkorb_frame(f)),
    "frost": (FROST_FRAMES, lambda S, f: frost_frame(f)),
    "dash": (DASH_FRAMES, lambda S, f: dash_frame(S, f)),
    "aura": (AURA_FRAMES, lambda S, f: aura_frame(S, f)),
    "starnode": (STARNODE_FRAMES, lambda S, f: starnode_frame(S, f)),
    "fallstar": (FALLSTAR_FRAMES, lambda S, f: fallstar_frame(S, f)),
    "galaxy": (GALAXY_FRAMES, lambda S, f: galaxy_frame(S, f)),
    "halo": (HALO_FRAMES, lambda S, f: halo_frame(S, f)),
    "aegis": (AEGIS_FRAMES, lambda S, f: aegis_frame(S, f)),
    "starhit": (STARHIT_FRAMES, lambda S, f: starhit_frame(S, f)),
}


def frames_of(name, S=None):
    S = S or S0()
    n, fn = FRAMES[name]
    return [to_img(*fn(S, f)) for f in range(n)]


SINGLE = ("star", "bolt", "beam", "shatter", "fireball", "icelance", "sparkorb", "frost", "dash", "aura", "starnode", "fallstar", "aegis")
TILED = ("galaxy", "halo")


def sprites():
    """그림판 목록 [(이름, 만들기, colored)] — element.sets_for 가 더함 (el_arcane_ 접두 · galaxy · halo 는 2 × 2 조각)"""
    S = S0()
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    out = [(p + k, fr(k), True) for k in SINGLE]
    for k in TILED:
        out += tiled(p + k, fr(k))
    return out


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k) for k in names}, out)
