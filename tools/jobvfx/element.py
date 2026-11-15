"""2026-11-15 원소 공용 이펙트 그림 + 직업 대표 그림 (사용자 "원소 공용 + 직업 대표" — 남은 30 직업: 같은 원소의 직업이 그림판 묶음 하나를 함께 씀).
사용자 질문 "그렇게 하면 퀄리티가 떨어지는거 아닌가" 뒤 고침: 한 가지 그리기에 색만 바꾸면 피가 "분홍 솜뭉치" 로 나왔음 (첫 시안) → 폭발 · 베기 · 맞은 자리 ·
기둥 · 작은 그림은 원소 성질대로 따로 그림 (강철 = 빛 구슬 · 빛살 · 반짝임 / 피 = 흩뿌리는 물보라 · 방울 · 윤기 / 금 = 반짝임). 고리 · 충격파 · 땅 자국 ·
모으는 빛 · 조각 · 흙먼지만 같은 코드에 색 램프만 다름.

묶음 (그림판 이름 = 묶음 접두 + "_" + 이름):
- 강철 el_steel (검사 · 수호기사): 2 × 2 slash · slashm · xslash · boom (빛 폭발) · shock · ring · spin · spinm (허리 높이 한 바퀴 베기 — 위에서 봄) /
  256 boom_s · shock_s · crack_s · flash · charge · dust · rift (땅 가르기 한 토막) · cut (일섬 — 그어짐 · 머묾 · 터짐) / 128 ember (네 갈래 반짝임) · shard ·
  pillar (빛기둥 128 × 256)
- 피 el_blood (블러드 나이트 · 워록 · 다크 나이트): 2 × 2 slash · slashm · xslash · boom (피 물보라) · shock · ring · moon (핏빛 달 — 되풀이) /
  256 boom_s · shock_s · crack_s · flash (맞은 자리 피 튐) · charge · dust / 128 ember (핏방울) · shard · pillar (피 기둥 128 × 256)
- 금 ak (아스널 킹 묶음에 더함 — 소드마스터): 2 × 2 slash · slashm · xslash / 256 cut / 256 × 1024 excal (빛의 대검 1 × 4 조각)

  cd tools && python3 -m jobvfx.element --preview 폴더 --style steel [--only slash,boom]
"""
import math
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

from .base import (TILE, WHITE, Noise3, banded, bloom, compose, grid, preview, ramp, spike, sstep, star_shape, tiled, to_img)


class Style:
    """원소 하나: main (몸 — 어둠 → 흰빛) · accent (둘째 색) · smoke (연기 · 자국) · kind (steel · blood · gold — 폭발 · 맞은 자리 · 작은 그림을 고름)"""

    def __init__(self, lib, kind, main, accent, smoke, ring="ticks", smoke_alpha=0.4, seed=0):
        self.lib = lib
        self.kind = kind
        self.main = main
        self.accent = accent
        self.smoke = smoke
        self.ring = ring
        self.smoke_alpha = smoke_alpha
        self.seed = seed

    @property
    def drops(self):
        return self.kind == "blood"


def edge_fade(x, y, lo=0.93, hi=0.99):
    return 1 - sstep(lo, hi, np.maximum(np.abs(x), np.abs(y)))


def seg_dist(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    ll = dx * dx + dy * dy + 1e-12
    s = np.clip(((x - ax) * dx + (y - ay) * dy) / ll, 0, 1)
    return np.hypot(x - (ax + s * dx), y - (ay + s * dy)), s


def streaks(x, y, items, aa):
    """줄기 [(머리 x, y, 꼬리 x, y, 굵기, 밝기)] → 알파 (머리 쪽이 밝음)"""
    m = np.zeros_like(x)
    for hx, hy, tx, ty, w, b in items:
        if b <= 0.01:
            continue
        d, s = seg_dist(x, y, tx, ty, hx, hy)
        m = np.maximum(m, (1 - sstep(w * 0.6, w + aa, d)) * (0.35 + 0.65 * s) * b)
    return m


def polyline(x, y, pts, w, aa):
    m = np.zeros_like(x)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        d, _ = seg_dist(x, y, ax, ay, bx, by)
        m = np.maximum(m, 1 - sstep(w * 0.5, w + aa, d))
    return m


def bolt_points(rng, x0, y0, x1, y1, depth=4, jag=0.18):
    pts = [(x0, y0), (x1, y1)]
    for _ in range(depth):
        out = [pts[0]]
        for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
            mx, my = (ax + bx) / 2, (ay + by) / 2
            L = math.hypot(bx - ax, by - ay)
            nx, ny = -(by - ay) / (L + 1e-9), (bx - ax) / (L + 1e-9)
            off = rng.uniform(-1, 1) * jag * L
            out += [(mx + nx * off, my + ny * off), (bx, by)]
        pts = out
    return pts


def star4(x, y, cx, cy, L, w, rot=0.0, aa=0.01, short=0.45):
    """네 갈래 반짝임 (긴 갈래 넷 · 짧은 대각 넷)"""
    m = np.zeros_like(x)
    for i in range(4):
        m = np.maximum(m, spike(x - cx, y - cy, rot + i * math.pi / 2, L, w, 1.6, aa))
        m = np.maximum(m, spike(x - cx, y - cy, rot + math.pi / 4 + i * math.pi / 2, L * short, w * 0.7, 1.6, aa))
    return m


def drops_layer(x, y, items, aa):
    """방울 [(x, y, 반지름, 움직임 x, y, 밝기)] — 몸 · 윤기 (위 왼쪽 빛) 알파 둘. 움직이는 반대쪽으로 꼬리가 늘어남 (빠를수록 길게)"""
    body = np.zeros_like(x)
    hi = np.zeros_like(x)
    for px, py, r, vx, vy, b in items:
        if b <= 0.01 or r <= 0:
            continue
        vl = math.hypot(vx, vy) + 1e-9
        ux, uy = vx / vl, vy / vl
        dx, dy = x - px, y - py
        along = dx * ux + dy * uy
        perp = -dx * uy + dy * ux
        st = 1 + min(2.2, vl * 0.9)
        a2 = np.where(along < 0, along / st, along)
        d = np.sqrt(a2 ** 2 + perp ** 2)
        body = np.maximum(body, (1 - sstep(r - aa, r + aa, d)) * b)
        hi = np.maximum(hi, np.exp(-((dx + r * 0.32) ** 2 + (dy - r * 0.32) ** 2) / (r * r * 0.1)) * b)
    return body, hi


def wet(mask, n, light=(-0.55, 0.8)):
    """덩이 마스크의 윤기: 흐린 마스크의 기울기로 겉면 방향 → 위 왼쪽을 보는 쪽이 밝음 (0 ~ 1)"""
    nn = max(n) if isinstance(n, tuple) else n
    g = ndimage.gaussian_filter(mask, max(1.0, nn * 0.006))
    gy, gx = np.gradient(g)
    nx, ny = -gx, gy
    ln = np.hypot(nx, ny) + 1e-6
    lit = np.clip((nx * light[0] + ny * light[1]) / ln, 0, 1) * np.clip(ln * nn * 0.5, 0, 1)
    return lit


# ------------------------------------------------------------------ 베기 (2 × 2 — 볼록한 쪽이 그림 위 · 왼쪽 → 오른쪽으로 휘두름)

SLASH_FRAMES = 10
SLASH_CY = -0.4
SLASH_R = 0.9
SLASH_A0 = math.radians(184)
SLASH_A1 = math.radians(-4)


def arc_coords(x, y):
    yy = y - SLASH_CY
    rho = np.hypot(x, yy)
    al = np.arctan2(yy, x)
    al = np.where(al < SLASH_A1 - 0.6, al + 2 * math.pi, al)
    s = (SLASH_A0 - al) / (SLASH_A0 - SLASH_A1)
    d = (rho - SLASH_R) / SLASH_R
    return rho, s, d


def arc_point(s, dr=0.0):
    a = SLASH_A0 - s * (SLASH_A0 - SLASH_A1)
    r = SLASH_R * (1 + dr)
    return r * math.cos(a), r * math.sin(a) + SLASH_CY, a


def slash_frame(S, f, n=512):
    """칼빛 초승달 (2판): 머리 (흰빛 · 뾰족) 가 0 ~ 2 칸에 호를 지나감 → 3 ~ 9 칸 꼬리부터 거둬짐. 몸은 바깥 날 (흰 줄 · 밝음) 에서 안쪽 (짙고 옅음) 으로 —
    호를 따라 결 (빛줄) · 안쪽 가장자리 짙은 테 (밝은 바탕에서도 보이게) · accent 줄 · 넓은 빛 번짐. 원소 결: 강철 · 금 = 반짝임 · 빛 줄기 / 피 = 튀는 방울 ·
    흩뿌린 자국"""
    rng = np.random.default_rng(301 + S.seed)
    N = Noise3(303 + S.seed)
    x, y = grid(n)
    aa = 1.6 / n
    rho, s, d = arc_coords(x, y)
    sh = (1 - (1 - min(1.0, (f + 1) / 2.6)) ** 1.6) * 1.05
    st = sstep(1.4, 9.2, f) * 1.08 - 0.03
    span = max(sh - st, 1e-3)
    sp_ = np.clip((s - st) / span, 0, 1)
    vis = ((s < sh) & (s > st) & (s > -0.03) & (s < 1.03)).astype(float)
    W = 0.31 * (1 - 0.45 * sstep(2, 9, f))
    w = W * sp_ ** 1.1 * (1 - sp_) ** 0.35 / 0.449
    inside = (1 - sstep(w - aa * 1.5, w + aa * 1.5, -d)) * sstep(-aa * 1.5, aa * 1.5, -d)
    body = vis * inside
    k = 1 - sstep(6.5, 9.6, f)
    rel = np.clip(-d / np.maximum(w, 1e-4), 0, 1)
    cool = sstep(2.0, 9.2, f)
    band = 0.5 + 0.5 * np.sin((rho - SLASH_R) * 2 * math.pi * 15 + (N.fbm(s * 3 + 1, rho * 3, 0.5, 3) - 0.5) * 9)
    streak = sstep(0.6, 0.8, band) * (1 - rel) ** 0.6
    solid = S.kind != "gold"  # 금은 빛 (짙은 테 없이 · 밝고 속이 옅게 — 첫 영상에서 짙은 테 · 띠가 바나나 껍질처럼 보였음)
    T = (1 - rel) ** 1.4 * (0.62 + 0.38 * sp_) + 0.2 * streak + 0.06 - 0.3 * cool * (1 - sp_) + (0.0 if solid else 0.16)
    Tb = banded(np.clip(T, 0, 1), [0.3, 0.45, 0.6, 0.74, 0.86])
    if solid:
        A = body * (0.97 - 0.52 * rel ** 1.5) * (0.6 + 0.4 * sp_ ** 0.6)
    else:
        A = body * (0.92 * (1 - rel) ** 1.2 + 0.06) * (0.6 + 0.4 * sp_ ** 0.6)
    layers = []
    glow = ndimage.gaussian_filter(body * (0.35 + 0.65 * sp_), n * (0.022 if solid else 0.03))
    layers.append((S.main(0.55 if solid else 0.7), np.clip(glow * (1.9 if solid else 2.3), 0, 1) * (0.7 if solid else 0.8) * k))
    smear = vis * (1 - sstep(w * 1.8, w * 1.8 + aa * 3, -d)) * sstep(-0.02, 0.0, -d) * (1 - sp_) ** 0.3 * 0.22
    layers.append((S.main(0.45 if solid else 0.7), smear * k))
    outl = body * sstep(0.8, 0.95, rel) * (w > 0.02)
    if solid:
        layers.append((S.main(0.14), outl * 0.85 * k))
    layers.append((S.main(0.32 + 0.68 * Tb), A * k))
    rim_in = body * np.exp(-((rel - 0.7) / 0.06) ** 2) * (w > 0.03) * (0.35 + 0.65 * sp_) * 0.8
    layers.append((S.accent(0.82), rim_in * k))
    edge = vis * np.exp(-(d / 0.009) ** 2) * sp_ ** 0.5 * (w > 0.004)
    layers.append((S.main(0.93), np.clip(edge * 1.2, 0, 1) * k))
    layers.append((WHITE, edge * sp_ ** 1.2 * k))
    head = vis * np.exp(-(((s - sh) * 4.0) ** 2 + (d / 0.045) ** 2) / 0.008) * (1 - sstep(2.5, 4.0, f))
    layers.append((WHITE, np.clip(head * 1.5, 0, 1)))
    if S.kind == "blood":
        items = []
        for i in range(22):
            se = rng.uniform(0.06, 0.98)
            age = f - se * 2.2
            if age < 0 or age > 6.5:
                continue
            px, py, a = arc_point(se, rng.uniform(-0.05, 0.02))
            tx, ty = math.sin(a), -math.cos(a)
            ox, oy = math.cos(a), math.sin(a)
            v = rng.uniform(0.6, 1.1)
            mx, my = (tx * 0.55 + ox * 0.6) * v, (ty * 0.55 + oy * 0.6) * v
            tt = age * 0.08
            items.append((px + mx * tt, py + my * tt - 0.7 * tt * tt, rng.uniform(0.014, 0.03) * (1 - 0.25 * tt), mx, my - 1.4 * tt, (1 - age / 7.0) ** 0.7))
        bd, hd = drops_layer(x, y, items, aa)
        dots = []
        for i in range(26):
            se = rng.uniform(0.0, 1.0)
            if se > float(sh) or f > 8:
                continue
            px, py, _ = arc_point(se, -rng.uniform(0.08, 0.3))
            dots.append((px, py, rng.uniform(0.006, 0.016), 0.0, -0.01, (1 - sstep(4.0, 8.5, f)) * 0.9))
        sd, _ = drops_layer(x, y, dots, aa)
        layers.append((S.main(0.2), sd * 0.8))
        layers.append((S.main(0.3), bd))
        layers.append((S.main(0.82), hd * 0.85))
    else:
        sp_m = np.zeros_like(x)
        for i in range(14):
            se = rng.uniform(0.08, 0.97)
            age = f - se * 2.2
            if age < 0 or age > 5.5:
                continue
            px, py, a = arc_point(se, rng.uniform(-0.12, 0.06))
            ox, oy = math.cos(a), math.sin(a)
            drift = age * 0.025
            tw = (1 - age / 6.0) * (0.65 + 0.35 * math.cos(age * 2.4 + i))
            L = rng.uniform(0.035, 0.07) * (0.7 + 0.3 * tw)
            sp_m = np.maximum(sp_m, star4(x, y, px + ox * drift, py + oy * drift, L, L * 0.16, rng.uniform(-0.3, 0.3), aa) * tw)
        items = []
        for i in range(12):
            se = rng.uniform(0.08, 0.98)
            age = f - se * 2.2
            if age < 0 or age > 5:
                continue
            px, py, a = arc_point(se)
            tx, ty = math.sin(a), -math.cos(a)
            ox, oy = math.cos(a), math.sin(a)
            v = rng.uniform(0.6, 1.0)
            mx, my = (tx * 0.7 + ox * 0.55) * v, (ty * 0.7 + oy * 0.55) * v
            tt = age * 0.075
            hx, hy = px + mx * tt, py + my * tt
            nn = math.hypot(mx, my) + 1e-6
            L = 0.06 + 0.05 * v
            items.append((hx, hy, hx - mx / nn * L, hy - my / nn * L, 0.006, (1 - age / 5.0) ** 1.1))
        layers.append((S.main(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
        layers.append((S.main(0.88), np.clip(sp_m * 1.2, 0, 1)))
        layers.append((WHITE, np.clip(sp_m * 1.4 - 0.35, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.02, gain=0.8 * k + 0.1)
    return rgb, a * edge_fade(x, y)


# ------------------------------------------------------------------ X 베기 (2 × 2)

XSLASH_FRAMES = 8


def _stroke(x, y, ang, bow, half, w0, head, tail, aa):
    ca, sa = math.cos(ang), math.sin(ang)
    u = (x * ca + y * sa) / half
    v = -x * sa + y * ca
    v = v - bow * (1 - np.clip(u, -1, 1) ** 2)
    w = w0 * np.clip(1 - np.abs(u) ** 1.6, 0, 1) ** 0.8
    body = (1 - sstep(w - aa, w + aa, np.abs(v))) * (np.abs(u) <= 1)
    vis = sstep(tail - 0.04, tail + 0.04, u) * (1 - sstep(head - 0.02, head + 0.02, u))
    hot = np.exp(-np.clip(head - u, 0, None) / 0.25)
    mid = np.abs(v) / np.maximum(w, 1e-4)
    return body * vis, hot, mid


def xslash_frame(S, f, n=512):
    """X 베기: 두 줄기가 차례로 (머리 흰빛 · 몸 main · 짙은 테) → 교차점 섬광 · 빛살 넷 → 튀는 줄기 (피 = 방울) → 꼬리부터 식으며 거둬짐"""
    rng = np.random.default_rng(311 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    layers = []
    body_all = np.zeros_like(r)
    core_all = np.zeros_like(r)
    hot_all = np.zeros_like(r)
    rim_all = np.zeros_like(r)
    for i, ang in enumerate((-math.pi / 4, math.pi / 4)):
        g = f - i * 0.8
        if g < 0:
            continue
        head = -1.05 + 2.2 * min(1.0, (g + 1) / 2.2)
        tail = -1.05 + 2.1 * sstep(2.0, 7.0, g)
        w0 = 0.085 * (1 - 0.5 * sstep(2, 7.5, g)) + 0.01
        body, hot, mid = _stroke(x, y, ang + math.pi, 0.08 * (1 if i == 0 else -1), 0.86, w0, head, tail, aa)
        kk = 1 - sstep(5, 7.8, g)
        body_all = np.maximum(body_all, body * kk)
        core_all = np.maximum(core_all, body * np.clip(1 - mid * 1.8, 0, 1) * kk)
        rim_all = np.maximum(rim_all, body * sstep(0.75, 0.95, mid) * kk)
        hot_all = np.maximum(hot_all, body * hot * (1 - sstep(2.0, 4.0, g)))
    cool = sstep(2.5, 7.5, f)
    glow = ndimage.gaussian_filter(body_all, n * 0.02)
    layers.append((S.main(0.5), np.clip(glow * 1.6, 0, 1) * 0.75))
    layers.append((S.main(0.62 - 0.2 * cool + 0.15 * core_all + (0.0 if S.kind != "gold" else 0.14)), body_all))
    if S.kind != "gold":
        layers.append((S.main(0.16), rim_all * 0.75))
    layers.append((S.main(0.9 - 0.18 * cool), core_all))
    layers.append((WHITE, np.clip(hot_all * 1.2 + core_all * 0.3 * (1 - cool), 0, 1)))
    if 2 <= f <= 4:
        k = 1 - (f - 2) / 3.0
        rays = np.zeros_like(r)
        for j in range(4):
            rays = np.maximum(rays, spike(x, y, j * math.pi / 2 + 0.1, 0.46 + 0.1 * (f - 2), 0.05 * k + 0.008, 1.2, aa))
        layers.append((S.main(0.85), rays * k))
        layers.append((WHITE, np.clip(np.exp(-(r / (0.1 + 0.04 * (f - 2))) ** 2) * k * 1.3, 0, 1)))
    if 2 <= f <= 7:
        q = (f - 2) / 5.0
        if S.drops:
            items = []
            for j in range(22):
                a0 = rng.uniform(0, 2 * math.pi)
                v0 = rng.uniform(0.4, 1.0)
                d0 = 0.06 + 0.72 * v0 * (1 - (1 - q) ** 1.8)
                items.append((d0 * math.cos(a0), d0 * math.sin(a0) - 0.25 * q * q, rng.uniform(0.012, 0.03), math.cos(a0) * v0, math.sin(a0) * v0 - q,
                              (1 - q) ** 0.8))
            bd, hd = drops_layer(x, y, items, aa)
            layers.append((S.main(0.3), bd))
            layers.append((S.main(0.8), hd * 0.85))
        else:
            items = []
            for j in range(24):
                a0 = rng.uniform(0, 2 * math.pi)
                v0 = rng.uniform(0.4, 1.0)
                d0 = 0.06 + 0.75 * v0 * (1 - (1 - q) ** 1.8)
                hx, hy = d0 * math.cos(a0), d0 * math.sin(a0)
                tx, ty = (d0 - 0.07 * v0) * math.cos(a0), (d0 - 0.07 * v0) * math.sin(a0)
                items.append((hx, hy, tx, ty, 0.006, (1 - q) ** 1.2))
            layers.append((S.main(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.022, gain=0.8)
    return rgb, a * edge_fade(x, y)


# ------------------------------------------------------------------ 폭발 (2 × 2 · 256): 강철 = 빛 폭발 · 피 = 물보라

BOOM_FRAMES = 14


def boom_light(S, f, n=512, frames=BOOM_FRAMES, seed_add=0):
    """빛 폭발 (강철 · 달빛): 0 ~ 2 흰 섬광 + 길고 가는 빛 칼날 열둘 (길이 셋 — 별 모양) → 1 ~ 7 셀 띠 빛 구슬 (흰 → 옅은 하늘 → 파랑, 가장자리가 잡음으로 일렁) 이
    부풀었다 줄어듦 · 가는 두 겹 고리가 퍼짐 · 빛 조각 (마름모) 이 사방으로 → 3 ~ 13 반짝이는 알갱이가 흩어지며 깜박이고 옅은 푸른 안개가 퍼져 사라짐"""
    rng = np.random.default_rng(611 + S.seed + seed_add)
    N = Noise3(612 + S.seed + seed_add)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    aa = 1.6 / n
    u = f / (frames - 1)
    layers = []
    Rm = 0.22 + 0.62 * (1 - (1 - u) ** 2)
    mist_n = N.fbm(x * 3 + 5, y * 3, u * 3, 4)
    mist = np.exp(-((r - Rm * 0.75) / (0.2 + 0.12 * u)) ** 2) * np.clip(mist_n * 1.7 - 0.35, 0, 1) * (1 - u) ** 1.1 * 0.5 * sstep(0.0, 0.15, u)
    layers.append((S.smoke(0.85), mist))
    Ro = (0.14 + 0.3 * min(1.0, (f + 1) / 4.0)) * (1 - 0.55 * sstep(0.3, 0.9, u))
    wob = (N.fbm(np.cos(th) * 2 + 3, np.sin(th) * 2, u * 4, 3) - 0.5) * 0.3
    q = r / max(Ro, 1e-3) * (1 + wob)
    T = np.clip(1.08 - q ** 1.3, 0, 1) * (1 - 0.35 * sstep(0.35, 0.9, u)) + 0.25 * (1 - sstep(0.0, 0.2, u)) * np.exp(-q * q)
    Tb = banded(np.clip(T, 0, 1), [0.22, 0.42, 0.6, 0.76, 0.9])
    orb_a = np.clip((1.06 - q) / 0.2, 0, 1) * (1 - sstep(0.55, 0.95, u))
    layers.append((S.main(0.5), np.clip(ndimage.gaussian_filter(orb_a, n * 0.03) * 1.3, 0, 1) * 0.6 * (1 - u)))
    layers.append((S.main(0.42 + 0.58 * Tb), orb_a))
    if u < 0.62:
        kb = (1 - u / 0.62) ** 1.1
        grow = 0.55 + 0.45 * min(1.0, (f + 1) / 3.0)
        ro = np.zeros_like(r)
        ri = np.zeros_like(r)
        for i in range(12):
            a0 = i * 2 * math.pi / 12 + 0.13 + rng.uniform(-0.06, 0.06)
            lv = [0.98, 0.5, 0.72][i % 3]
            L = lv * grow * (1 - 0.3 * u)
            w0 = [0.032, 0.018, 0.024][i % 3] * (1 - 0.4 * u)
            ro = np.maximum(ro, spike(x, y, a0, L, w0, 1.5, aa))
            ri = np.maximum(ri, spike(x, y, a0, L * 0.85, w0 * 0.36, 1.5, aa))
        layers.append((S.accent(0.78), ro * kb))
        layers.append((WHITE, ri * kb))
    Rr = 0.18 + 0.74 * (1 - (1 - u) ** 1.8)
    ring = np.exp(-((r - Rr) / (0.011 + 0.018 * u)) ** 2) * (1 - u) ** 1.3 * sstep(0.0, 0.08, u)
    ring2 = np.exp(-((r - Rr * 0.84) / 0.007) ** 2) * (1 - u) ** 1.7 * 0.6 * sstep(0.0, 0.1, u)
    layers += [(S.main(0.86), ring), (WHITE, ring * 0.6), (S.accent(0.85), ring2)]
    frag = np.zeros_like(r)
    if f >= 1:
        prog = 1 - (1 - min(1.0, (f - 1) / (frames * 0.55))) ** 1.6
        for i in range(16):
            a0 = rng.uniform(0, 2 * math.pi)
            v = rng.uniform(0.45, 1.0)
            d0 = 0.12 + v * 0.82 * prog
            px, py = d0 * math.cos(a0), d0 * math.sin(a0)
            sz = 0.028 * (1 - prog) + 0.006
            rot = a0 + f * 0.6 * (1 if i % 2 else -1)
            cx_, cy_ = math.cos(rot), math.sin(rot)
            lx = (x - px) * cx_ + (y - py) * cy_
            ly = -(x - px) * cy_ + (y - py) * cx_
            dia = np.abs(lx) / (sz * 1.8) + np.abs(ly) / (sz * 0.7)
            frag = np.maximum(frag, (1 - sstep(0.85, 1.0, dia)) * (1 - sstep(0.6, 1.0, prog)))
    layers.append((S.main(0.9), frag))
    tw_m = np.zeros_like(r)
    if f >= 3:
        prog = (f - 3) / max(1, frames - 4)
        for i in range(14):
            a0 = rng.uniform(0, 2 * math.pi)
            v = rng.uniform(0.3, 1.0)
            d0 = 0.2 + v * 0.7 * (1 - (1 - prog) ** 1.4)
            tw = (0.5 + 0.5 * math.cos(f * 1.9 + i * 2.3)) * (1 - sstep(0.6, 1.0, prog))
            L = 0.04 * (0.6 + 0.4 * tw)
            tw_m = np.maximum(tw_m, star4(x, y, d0 * math.cos(a0), d0 * math.sin(a0) + 0.05 * prog, L, L * 0.15, 0.2, aa) * tw)
    layers.append((S.main(0.92), np.clip(tw_m * 1.2, 0, 1)))
    layers.append((WHITE, np.clip(tw_m * 1.3 - 0.4, 0, 1)))
    if f <= 2:
        kk = 1 - f / 3.0
        layers.append((WHITE, np.clip(np.exp(-(r / (0.2 + 0.12 * f / 2)) ** 2) * kk * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=1.0 - 0.5 * u)
    return rgb, a * edge_fade(x, y, 0.9, 0.98)


def splash_shape(x, y, f, frames, rng, jets, Lmax, w0, grav, aa):
    """피 물보라의 덩이 마스크: 바깥으로 뻗는 줄기 (밑동 굵고 끝은 가늘며 끝에 방울 · 처짐) → 줄기가 밑동부터 끊겨 방울로 날아감 (중력 아래로).
    반환 (줄기 마스크, 방울 목록 [(x, y, r, vx, vy, b)])"""
    u = f / (frames - 1)
    m = np.zeros_like(x)
    drops = []
    grow = 1 - (1 - min(1.0, (f + 1) / (frames * 0.3))) ** 2
    brk = float(sstep(frames * 0.32, frames * 0.62, f))
    for i in range(jets):
        a0 = i * 2 * math.pi / jets + rng.uniform(-0.18, 0.18)
        L = Lmax * rng.uniform(0.55, 1.0)
        ww = w0 * rng.uniform(0.6, 1.1)
        ca, sa = math.cos(a0), math.sin(a0)
        tip = L * grow
        sag = grav * u * u * tip
        b0 = tip * brk * 0.85
        segs = 6
        pts = []
        for kq in range(segs + 1):
            t = b0 + (tip - b0) * kq / segs
            px, py = ca * t, sa * t - sag * (t / max(tip, 1e-3)) ** 2
            pts.append((px, py, ww * max(0.0, 1 - t / max(L, 1e-3)) ** 0.7 + 0.004))
        if tip - b0 > 0.02:
            for (ax, ay, aw), (bx, by, bw) in zip(pts[:-1], pts[1:]):
                d, s = seg_dist(x, y, ax, ay, bx, by)
                wl = aw + (bw - aw) * s
                m = np.maximum(m, 1 - sstep(wl - aa, wl + aa, d))
        ex, ey, _ = pts[-1]
        rb = ww * 0.75 * (1 - 0.4 * u)
        m = np.maximum(m, 1 - sstep(rb - aa, rb + aa, np.hypot(x - ex, y - ey)))
        if brk > 0.05:
            for qd in range(2):
                tq = b0 * (0.35 + 0.4 * qd)
                fly = max(0.0, (f - frames * 0.32) / frames) * 0.6
                px = ca * (tq + fly * L)
                py = sa * (tq + fly * L) - grav * (fly + u) ** 2 * 0.5
                drops.append((px, py, ww * (0.5 - 0.15 * qd) * (1 - 0.5 * u), ca, sa - grav * u, float(1 - sstep(0.7, 1.0, u))))
    return m, drops


def boom_splash(S, f, n=512, frames=BOOM_FRAMES, seed_add=0):
    """피 물보라 폭발: 0 ~ 2 붉은 섬광 (흰빛 아님 — 진홍 둘레 · 밝은 붉은 심) → 가운데 짙은 핏덩이 + 바깥으로 뻗는 줄기 열넷 (밑동 굵고 끝에 방울 · 처짐) →
    줄기가 끊겨 방울로 흩어져 떨어짐 · 윤기 (위 왼쪽 빛 — 젖은 겉) · 검붉은 안개 고리가 퍼져 사라짐"""
    rng = np.random.default_rng(621 + S.seed + seed_add)
    N = Noise3(622 + S.seed + seed_add)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    aa = 1.6 / n
    u = f / (frames - 1)
    layers = []
    Rm = 0.25 + 0.6 * (1 - (1 - u) ** 2)
    mn = N.fbm(x * 3.2 + 2, y * 3.2, u * 3, 4)
    mist = np.exp(-((r - Rm * 0.7) / (0.22 + 0.1 * u)) ** 2) * np.clip(mn * 1.8 - 0.4, 0, 1) * (1 - u) * 0.55 * sstep(0.0, 0.12, u)
    layers.append((S.smoke(0.55), mist))
    if f <= 2:
        kk = 1 - f / 3.0
        layers.append((S.main(0.55), np.exp(-(r / (0.34 + 0.1 * f)) ** 2) * kk * 0.9))
        layers.append((S.main(0.9), np.clip(np.exp(-(r / (0.13 + 0.05 * f)) ** 2) * kk * 1.3, 0, 1)))
    Rb = 0.2 * (1 + 0.5 * min(1.0, f / 2.0)) * (1 - 0.75 * sstep(0.2, 0.75, u))
    wob = (N.fbm(np.cos(th) * 2.5 + 7, np.sin(th) * 2.5, u * 3, 3) - 0.5) * 0.45
    blob = 1 - sstep(Rb * (1 + wob) - aa, Rb * (1 + wob) + aa, r)
    jm, drops = splash_shape(x, y, f, frames, rng, 14, 0.82, 0.06, 0.55, aa)
    mask = np.maximum(blob, jm) * (1 - sstep(0.72, 1.0, u))
    lit = wet(mask, n)
    inner = ndimage.gaussian_filter(mask, n * 0.012)
    T = 0.28 + 0.22 * np.clip(inner * 1.4 - 0.3, 0, 1) + 0.25 * lit
    layers.append((S.main(0.1), np.clip(ndimage.gaussian_filter(mask, n * 0.006) * 1.5, 0, 1) * 0.8 * (1 - u)))
    layers.append((S.main(T), mask))
    spec = mask * np.clip(lit * 1.6 - 0.75, 0, 1)
    layers.append((S.main(0.9), spec))
    bd, hd = drops_layer(x, y, drops, aa)
    layers.append((S.main(0.3), bd))
    layers.append((S.main(0.85), hd * 0.9))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.02, gain=0.6 * (1 - u))
    return rgb, a * edge_fade(x, y, 0.9, 0.98)


def boom_frame(S, f, n=512, frames=BOOM_FRAMES, seed_add=0):
    return (boom_splash if S.kind == "blood" else boom_light)(S, f, n, frames, seed_add)


# ------------------------------------------------------------------ 땅 충격파 (2 × 2 · 256)

SHOCK_FRAMES = 8


def shock_frame(S, f, n=512, frames=SHOCK_FRAMES, seed_add=0):
    """땅의 충격파 (위에서 봄): 흰 심 앞 날 + main 겉 · 뒤 짧은 빛 (속은 비움) · smoke 띠 · 앞 날에서 바깥으로 긁힌 줄 (피 = 튀는 방울)"""
    N = Noise3(361 + S.seed + seed_add)
    rng = np.random.default_rng(361 + S.seed + seed_add)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (frames - 1)
    aa = 1.6 / n
    R = 0.14 + 0.8 * (1 - (1 - u) ** 2.2)
    jag = (N.fbm(np.cos(th) * 3 + 2, np.sin(th) * 3, f * 0.3, 3) - 0.5) * 0.035
    d = r - (R + jag)
    k = (1 - u) ** 0.9
    w = 0.028 * (1 - 0.5 * u) + 0.011
    core = np.exp(-(d / (w * 0.5)) ** 2) * k
    edge = np.exp(-(d / (w * 1.4)) ** 2) * k
    trail = np.exp(-(np.clip(-d, 0, None) / (0.08 + 0.04 * u)) ** 1.5) * (d < 0) * k
    soot_n = N.fbm(x * 6 + 5, y * 6, f * 0.4, 4)
    soot = np.clip((soot_n - 0.35) * 2.0, 0, 1) * np.exp(-((d + 0.08) / 0.07) ** 2) * (1 - u) ** 0.7 * 0.4
    layers = [(S.smoke(0.4), soot), (S.main(0.5 + 0.25 * trail), trail * 0.85)]
    if S.drops:
        items = []
        for i in range(26):
            a0 = rng.uniform(0, 2 * math.pi)
            L = rng.uniform(0.02, 0.09)
            items.append(((R + L) * math.cos(a0), (R + L) * math.sin(a0), rng.uniform(0.008, 0.016), math.cos(a0), math.sin(a0), k * rng.uniform(0.6, 1.0)))
        bd, hd = drops_layer(x, y, items, aa)
        layers += [(S.main(0.32), bd), (S.main(0.8), hd * 0.8)]
    else:
        items = []
        for i in range(34):
            a0 = rng.uniform(0, 2 * math.pi)
            L = rng.uniform(0.04, 0.11)
            items.append(((R + L) * math.cos(a0), (R + L) * math.sin(a0), R * math.cos(a0), R * math.sin(a0), 0.0045, k * rng.uniform(0.5, 1.0)))
        layers.append((S.main(0.9), np.clip(streaks(x, y, items, aa) * 1.2, 0, 1)))
    layers += [(S.accent(0.8), edge * 0.85), (S.main(0.95), core), (WHITE, core * (0.35 if S.drops else 0.6))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.018, gain=0.85 * k)
    return rgb, a * (1 - sstep(0.93, 0.99, r))


# ------------------------------------------------------------------ 땅 자국 (256)

CRACK_FRAMES = 5


def crack_frame(S, f, n=256):
    """땅 자국 (위에서 봄): smoke 로 물든 자국 + 가운데서 뻗은 금 (밝음 → 식음 · 피 = 흩뿌린 얼룩)"""
    N = Noise3(371 + S.seed)
    rng = np.random.default_rng(371 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    u = f / (CRACK_FRAMES - 1)
    aa = 1.6 / n
    burn = (1 - sstep(0.32, 0.8, r + (N.fbm(x * 4, y * 4, 1.0, 4) - 0.5) * 0.35)) * 0.8
    cr = np.zeros_like(r)
    for i in range(8):
        a = rng.uniform(0, 2 * math.pi)
        L = rng.uniform(0.5, 0.88)
        pts = bolt_points(rng, 0, 0, L * math.cos(a), L * math.sin(a), 4, 0.2)
        for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
            dd, sv = seg_dist(x, y, ax, ay, bx, by)
            prog = math.hypot(ax, ay) / L
            wid = 0.02 * (1 - prog) + 0.005
            cr = np.maximum(cr, 1 - sstep(wid * 0.5, wid + aa, dd))
    heat = (1 - u) ** 1.2
    layers = [(S.smoke(0.2), burn * (0.85 - 0.3 * u))]
    if S.drops:
        sp = np.zeros_like(r)
        for i in range(14):
            a = rng.uniform(0, 2 * math.pi)
            dd0 = rng.uniform(0.2, 0.75)
            rr = rng.uniform(0.03, 0.08)
            sp = np.maximum(sp, 1 - sstep(rr * 0.8, rr, np.hypot(x - dd0 * math.cos(a), y - dd0 * math.sin(a))))
        layers.append((S.main(0.25), sp * 0.85))
    layers.append((S.main(0.4 + 0.55 * heat), cr * (0.45 + 0.55 * heat)))
    layers.append((S.main(0.88), np.exp(-(r / 0.14) ** 2) * heat * 0.8))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.5, sigma=0.014, gain=0.7 * heat + 0.1)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


# ------------------------------------------------------------------ 땅 고리 (2 × 2 · 되풀이)

RING_FRAMES = 4


def ring_frame(S, f, n=512):
    """땅에 머무는 고리 (예고 · 테두리): 반지름 0.86 — 흰 심 줄 + main 빛 (안 · 밖) + 결: ticks (안쪽 눈금) · thorns (가시) · 안쪽 accent 가는 고리 · 숨쉼"""
    x, y = grid(n)
    r = np.hypot(x, y)
    ph = f / RING_FRAMES * 2 * math.pi
    aa = 1.6 / n
    R = 0.86
    pulse = 0.85 + 0.15 * math.sin(ph)
    layers = [(S.main(0.5), np.exp(-(np.clip(R - r, 0, None) / 0.05) ** 1.3) * (r < R) * 0.5 * pulse),
              (S.main(0.56), np.exp(-(np.clip(r - R, 0, None) / 0.035) ** 1.3) * (r >= R) * 0.5 * pulse)]
    deco = np.zeros_like(r)
    if S.ring == "thorns":
        for i in range(24):
            a0 = i * 2 * math.pi / 24 + (0.06 if i % 2 else 0)
            L = 0.07 if i % 2 == 0 else 0.045
            deco = np.maximum(deco, spike(x - R * math.cos(a0), y - R * math.sin(a0), a0, L, 0.012, 1.2, aa))
    else:
        for i in range(24):
            a0 = i * 2 * math.pi / 24 + 0.13
            L = 0.07 if i % 2 == 0 else 0.04
            ca, sa = math.cos(a0), math.sin(a0)
            along = x * ca + y * sa
            perp = np.abs(-x * sa + y * ca)
            m = (1 - sstep(0.003, 0.007, perp)) * sstep(R - 0.025 - L, R - 0.025 - L + 0.01, along) * (1 - sstep(R - 0.03, R - 0.02, along))
            deco = np.maximum(deco, m)
    layers += [(S.main(0.82), deco * 0.95 * pulse), (S.accent(0.85), np.exp(-((r - R * 0.93) / 0.004) ** 2) * 0.75),
               (S.main(0.9), np.exp(-((r - R) / 0.015) ** 2) * 0.95), (WHITE, np.exp(-((r - R) / 0.005) ** 2) * (0.6 if S.drops else 1.0))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.012, gain=0.6)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 섬광 · 맞은 자리 (256)

FLASH_FRAMES = 8


def flash_light(S, f, n=256):
    """섬광 (강철 · 금): 0 ~ 2 단단한 흰 별 (꼭짓점 여섯) → 굵은 빛살 여섯이 가늘어지며 · 퍼지는 고리 · 튀는 줄기"""
    rng = np.random.default_rng(381 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (FLASH_FRAMES - 1)
    aa = 1.6 / n
    grow = min(1.0, (f + 1) / 2.0)
    k = (1 - u) ** 1.1
    layers = [(S.main(0.66 + 0.2 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.3 + 0.16 * u)) ** 2) * 0.75 * k)]
    ring_r = 0.18 + 0.7 * (1 - (1 - u) ** 1.6)
    layers.append((S.accent(0.75), np.exp(-((r - ring_r) / (0.025 + 0.02 * u)) ** 2) * (1 - u) ** 1.3 * 0.75))
    thin = 1 - 0.6 * sstep(0.15, 1.0, u)
    o = np.zeros_like(r)
    i_ = np.zeros_like(r)
    for i in range(6):
        a0 = 0.3 + i * math.pi / 3
        L = (0.5 + 0.4 * grow) * (1 - 0.25 * u) * (1.0 if i % 2 == 0 else 0.75)
        o = np.maximum(o, spike(x, y, a0, L, 0.065 * thin, 1.4, aa))
        i_ = np.maximum(i_, spike(x, y, a0, L * 0.8, 0.024 * thin, 1.4, aa))
    layers += [(S.main(0.78), o * k), (WHITE, i_ * k)]
    if f <= 2:
        Rst = star_shape(r, th, 6, 0.1, rng.uniform(0.2, 0.32, 6) * grow, 0.3, 2.0) * (1 - 0.3 * f / 2)
        shape = 1 - sstep(Rst - aa, Rst + aa, r)
        layers.append((S.main(0.9), shape))
        layers.append((WHITE, shape * (1 - sstep(Rst * 0.45, Rst * 0.9, r))))
    items = []
    for j in range(16):
        a0 = rng.uniform(0, 2 * math.pi)
        v = rng.uniform(0.5, 1.0)
        d0 = 0.12 + 0.75 * v * (1 - (1 - u) ** 1.5)
        hx, hy = d0 * math.cos(a0), d0 * math.sin(a0) - 0.15 * u * u
        L = 0.06 * v
        items.append((hx, hy, hx - L * math.cos(a0), hy - L * math.sin(a0), 0.008, (1 - u) ** 1.1))
    layers.append((S.main(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.08 + 0.05 * grow)) ** 2) * k * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.7 * k)
    return rgb, a * edge_fade(x, y)


def flash_splash(S, f, n=256):
    """맞은 자리 피 튐 (피): 0 ~ 2 붉은 섬광 · 짧은 붉은 빛살 넷 → 줄기 열 · 방울이 바깥으로 튀어 떨어짐 · 윤기"""
    rng = np.random.default_rng(391 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    frames = FLASH_FRAMES
    u = f / (frames - 1)
    layers = []
    if f <= 2:
        kk = 1 - f / 3.0
        layers.append((S.main(0.55), np.exp(-(r / (0.36 + 0.1 * f)) ** 2) * kk * 0.85))
        rays = np.zeros_like(r)
        for i in range(4):
            rays = np.maximum(rays, spike(x, y, 0.5 + i * math.pi / 2, 0.75 * (0.7 + 0.3 * f / 2), 0.05, 1.4, aa))
        layers.append((S.main(0.82), rays * kk))
        layers.append((S.main(0.95), np.clip(np.exp(-(r / (0.1 + 0.04 * f)) ** 2) * kk * 1.4, 0, 1)))
    jm, drops = splash_shape(x, y, f, frames, rng, 10, 0.78, 0.07, 0.6, aa)
    blob = (1 - sstep(0.1 * (1 - u) - aa, 0.1 * (1 - u) + aa, r)) * (f >= 1)
    mask = np.maximum(jm * (f >= 1), blob) * (1 - sstep(0.7, 1.0, u))
    lit = wet(mask, n)
    layers.append((S.main(0.1), np.clip(ndimage.gaussian_filter(mask, n * 0.008) * 1.5, 0, 1) * 0.75 * (1 - u)))
    layers.append((S.main(0.3 + 0.28 * lit), mask))
    layers.append((S.main(0.9), mask * np.clip(lit * 1.6 - 0.75, 0, 1)))
    bd, hd = drops_layer(x, y, drops, aa)
    layers.append((S.main(0.3), bd))
    layers.append((S.main(0.85), hd * 0.9))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.025, gain=0.6 * (1 - u))
    return rgb, a * edge_fade(x, y)


def flash_frame(S, f, n=256):
    return (flash_splash if S.kind == "blood" else flash_light)(S, f, n)


CHARGE_FRAMES = 10


def charge_frame(S, f, n=256):
    """모으는 빛 (되풀이): 숨쉬는 심 + main 빛 무리 · 바깥에서 안으로 휘감겨 드는 줄기 · 도는 끊긴 고리 셋 (accent)"""
    rng = np.random.default_rng(391 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / CHARGE_FRAMES
    aa = 1.6 / n
    pulse = 0.88 + 0.12 * math.sin(ph * 2 * math.pi * 2)
    layers = [(S.main(0.62 + 0.28 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.36 * pulse)) ** 2) * 0.75)]
    rr = 0.64
    seg = np.zeros_like(r)
    for i in range(3):
        a0 = i * 2 * math.pi / 3 + ph * 2 * math.pi / 3
        dd = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        seg = np.maximum(seg, 1 - sstep(0.55, 0.7, dd))
    layers.append((S.accent(0.8), np.exp(-((r - rr) / 0.014) ** 2) * seg * 0.9))
    items = []
    for i in range(14):
        a0 = rng.uniform(0, 2 * math.pi)
        q = (rng.uniform(0, 1) + ph) % 1.0
        dh = 0.95 - 0.8 * q
        ah = a0 + q * 1.2
        dt = min(0.98, dh + 0.12)
        at = ah - 0.15
        fade = math.sin(math.pi * min(1.0, q * 1.15)) ** 0.6
        items.append((dh * math.cos(ah), dh * math.sin(ah), dt * math.cos(at), dt * math.sin(at), 0.01 * (0.6 + 0.4 * q), fade))
    layers.append((S.main(0.86), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    layers.append((WHITE if not S.drops else S.main(0.95), np.clip(np.exp(-(r / (0.12 * pulse)) ** 2) * 1.25, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.035, gain=0.75)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


# ------------------------------------------------------------------ 날아가는 작은 그림 · 조각 (128)

EMBER_FRAMES = 4


def ember(S):
    """날아가는 작은 그림 (카메라를 봄): 강철 · 금 = 네 갈래 반짝임 · 피 = 핏방울 (윤기), 네 칸 깜박임"""
    n = 128
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    out = []
    for f in range(EMBER_FRAMES):
        k = [1.0, 0.78, 0.92, 0.66][f]
        if S.drops:
            bd, hd = drops_layer(x, y, [(0, 0, 0.5 * (0.85 + 0.15 * k), 0, -0.6, 1.0)], aa)
            rgb, a = compose([(S.main(0.45), np.exp(-(r / 0.62) ** 2) * 0.25), (S.main(0.3), bd), (S.main(0.82), hd)], n)
        else:
            rot = [0.0, 0.12, 0.05, 0.18][f]
            m = np.zeros_like(x)
            mc = np.zeros_like(x)
            for i in range(4):
                m = np.maximum(m, spike(x, y, rot + i * math.pi / 2, 0.9 * k, 0.07, 1.6, aa))
                m = np.maximum(m, spike(x, y, rot + math.pi / 4 + i * math.pi / 2, 0.4 * k, 0.05, 1.6, aa))
                mc = np.maximum(mc, spike(x, y, rot + i * math.pi / 2, 0.7 * k, 0.025, 1.6, aa))
            rgb, a = compose([(S.main(0.66), np.exp(-(r / (0.32 * (0.85 + 0.15 * k))) ** 2) * 0.35), (S.main(0.86), m), (WHITE, mc),
                              (WHITE, np.clip(np.exp(-(r / (0.12 * k)) ** 2) * 1.3, 0, 1))], n)
        out.append(to_img(rgb, a * (1 - sstep(0.88, 0.99, np.maximum(np.abs(x), np.abs(y))))))
    return out


SHARD_FRAMES = 8


def shard(S):
    """조각 (구르는 여덟 칸): 빛 받는 면은 main 밝은 쪽 · 어두운 면은 main 어두운 쪽, 가장자리 accent 빛 · 둘레 옅은 빛"""
    n = 128
    x, y = grid(n)
    aa = 1.6 / n
    poly = np.array([[-0.64, -0.08, 0], [-0.1, 0.34, 0], [0.64, 0.12, 0], [0.32, -0.34, 0]])
    light = np.array([-0.4, 0.75, 0.55])
    light = light / np.linalg.norm(light)
    out = []
    for f in range(SHARD_FRAMES):
        a1 = f / SHARD_FRAMES * 2 * math.pi
        rx = np.array([[1, 0, 0], [0, math.cos(a1), -math.sin(a1)], [0, math.sin(a1), math.cos(a1)]])
        a2 = a1 * 0.5 + 0.4
        rz = np.array([[math.cos(a2), -math.sin(a2), 0], [math.sin(a2), math.cos(a2), 0], [0, 0, 1]])
        m = rz @ rx
        P = poly @ m.T
        nrm = m @ np.array([0, 0, 1.0])
        lit = abs(float(nrm @ light))
        facing = abs(float(nrm[2]))
        px, py = P[:, 0], P[:, 1]
        kk = len(px)
        orient = np.sign((px[1] - px[0]) * (py[2] - py[0]) - (py[1] - py[0]) * (px[2] - px[0])) or 1.0
        sd = np.full_like(x, 9.0)
        for i in range(kk):
            x0, y0, x1, y1 = px[i], py[i], px[(i + 1) % kk], py[(i + 1) % kk]
            ex, ey = x1 - x0, y1 - y0
            el = math.hypot(ex, ey) + 1e-9
            sd = np.minimum(sd, ((x - x0) * ey - (y - y0) * ex) / el * -orient)
        body = sstep(-aa, aa, sd) * (0.35 + 0.65 * min(1.0, facing * 1.6 + 0.25))
        edge = np.exp(-(np.clip(sd, 0, None) / 0.05) ** 2) * body
        shade = lit * 0.7 + 0.2 * (y * 0.5 + 0.5) + (x * 0.6 + y * 0.8) * 0.12
        halo = np.exp(-(np.clip(-sd, 0, None) / 0.12) ** 2) * (sd < 0) * 0.45
        rgb, a = compose([(S.main(0.55), halo), (S.main(np.clip(0.3 + 0.55 * shade, 0, 1)), body), (S.accent(0.85), edge)], n)
        out.append(to_img(rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))))
    return out


# ------------------------------------------------------------------ 세로 그림 (128 × 256): 빛기둥 · 피 기둥

PILLAR_FRAMES = 10


def pillar_grid(w=128, h=256):
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = (np.arange(h) + 0.5) / h * 2 - 1
    x, y = np.meshgrid(xs, -ys)
    return x * 0.5, y


def pillar_beam(S, f, w=128, h=256):
    """빛기둥: 땅에서 솟음 (흰 심 · main 겉 · 넓은 옅은 빛) · 오르는 빛 알갱이 · 밑동 번쩍"""
    rng = np.random.default_rng(501 + S.seed)
    u = f / (PILLAR_FRAMES - 1)
    x, y = pillar_grid(w, h)
    base_y = -0.9
    grow = 1 - (1 - min(1.0, (f + 1) / 3.0)) ** 2
    k = 1 - sstep(0.55, 1.0, u)
    top = base_y + 1.85 * grow
    vert = sstep(base_y - 0.02, base_y + 0.06, y) * (1 - sstep(top - 0.25, top, y))
    wid = 0.16 * (1 - 0.45 * sstep(0.4, 1.0, u)) * (1 + 0.25 * np.exp(-((y - base_y) / 0.15) ** 2))
    core = np.exp(-(x / (wid * 0.35)) ** 2) * vert
    bodyb = np.exp(-(x / wid) ** 2) * vert
    motes = np.zeros_like(x)
    for j in range(9):
        q = (rng.uniform(0, 1) + u * 1.3) % 1.0
        px = rng.uniform(-0.2, 0.2)
        py = base_y + q * 1.8
        motes = np.maximum(motes, np.exp(-((x - px) ** 2 + (y - py) ** 2) / 0.0005) * (1 - q))
    foot = np.exp(-((x / 0.35) ** 2 + ((y - base_y) / 0.05) ** 2)) * (1 - u)
    layers = [(S.main(0.55), np.exp(-(x / (wid * 2.2)) ** 2) * vert * 0.45 * k), (S.main(0.8), bodyb * 0.85 * k), (WHITE, core * k),
              (S.accent(0.9), np.clip(motes * 1.3, 0, 1) * k), (S.main(0.9), foot)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.03, gain=0.6 * k)
    a = a * (1 - sstep(0.94, 0.99, np.abs(x) * 2)) * (1 - sstep(0.94, 0.99, np.abs(y)))
    return rgb, a


def pillar_geyser(S, f, w=128, h=256):
    """피 기둥: 땅에서 피가 솟구침 — 밑동 굵은 줄기가 위로 뻗고 (윤기 · 짙은 테) 꼭대기가 왕관처럼 터져 방울로 흩어짐 → 줄기가 꺼지며 떨어지는 방울 · 밑동 웅덩이"""
    rng = np.random.default_rng(511 + S.seed)
    N = Noise3(512 + S.seed)
    u = f / (PILLAR_FRAMES - 1)
    x, y = pillar_grid(w, h)
    aa = 1.6 / h
    base_y = -0.9
    up = 1 - (1 - min(1.0, (f + 1) / 3.0)) ** 2
    fall = float(sstep(0.45, 1.0, u))
    top = base_y + 1.6 * up * (1 - 0.75 * fall)
    hh = (y - base_y) / max(top - base_y, 1e-3)
    wid = (0.13 * np.clip(1 - hh, 0, 1) ** 0.6 + 0.035) * (1 - 0.5 * fall)
    wob = (N.fbm(y * 4 + 3, f * 0.6 + 0.5, 0.5, 3) - 0.5) * 0.06 * np.clip(hh, 0, 1)
    col = (1 - sstep(wid - aa, wid + aa, np.abs(x - wob))) * (y >= base_y - 0.01) * (hh <= 1.0)
    crown = np.zeros_like(x)
    drops = []
    if up > 0.5:
        cy = top
        for i in range(7):
            a0 = math.pi / 2 + (i - 3) * 0.42 + rng.uniform(-0.1, 0.1)
            L = 0.22 * rng.uniform(0.7, 1.0) * min(1.0, (up - 0.5) * 2.5)
            ex, ey = math.cos(a0) * L, cy + math.sin(a0) * L * 0.7
            d, s = seg_dist(x, y, 0, cy, ex, ey)
            wl = 0.03 * (1 - s) + 0.008
            crown = np.maximum(crown, 1 - sstep(wl - aa, wl + aa, d))
            tt = u * 1.4
            drops.append((ex + math.cos(a0) * tt * 0.3, ey + math.sin(a0) * tt * 0.25 - 1.6 * tt * tt * 0.4, 0.028 * (1 - 0.3 * u), math.cos(a0),
                          math.sin(a0) - 2 * tt, float(1 - sstep(0.75, 1.0, u))))
    pool = np.exp(-((x / 0.32) ** 2 + ((y - base_y) / 0.035) ** 2)) * (1 - 0.6 * u)
    mask = np.clip(np.maximum(col, crown * (1 - fall)), 0, 1)
    lit = wet(mask, (h, w))
    layers = [(S.main(0.2), pool * 0.9), (S.main(0.12), np.clip(ndimage.gaussian_filter(mask, h * 0.008) * 1.6, 0, 1) * 0.7),
              (S.main(0.3 + 0.3 * lit + 0.1 * (1 - np.clip(np.abs(x) / 0.12, 0, 1))), mask), (S.main(0.9), mask * np.clip(lit * 1.7 - 0.8, 0, 1))]
    bd, hd = drops_layer(x, y, drops, aa)
    layers += [(S.main(0.3), bd), (S.main(0.85), hd * 0.9)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.55, sigma=0.02, gain=0.5)
    a = a * (1 - sstep(0.94, 0.99, np.abs(x) * 2)) * (1 - sstep(0.96, 0.995, np.abs(y)))
    return rgb, a


def pillar_frame(S, f):
    return (pillar_geyser if S.kind == "blood" else pillar_beam)(S, f)


# ------------------------------------------------------------------ 꽂힌 · 내려친 자리 (256)

DUST_FRAMES = 8


def dust_frame(S, f, n=256):
    """꽂힌 · 내려친 자리 (땅 = 그림 아래쪽 1/4 — 자바 PhantomFx.dust 가 올려 띄움): 밑에서 main 번쩍 → smoke 덩이가 옆 · 위로 퍼지며 옅어짐 · 튀는 줄기 · 방울"""
    N = Noise3(521 + S.seed)
    rng = np.random.default_rng(521 + S.seed)
    x, y = grid(n)
    u = f / (DUST_FRAMES - 1)
    aa = 1.6 / n
    gy = -0.5
    dens = np.zeros_like(x)
    grow = 0.15 + 0.85 * (1 - (1 - u) ** 2)
    for j in range(4):
        a0 = rng.uniform(0.2, math.pi / 2 - 0.1)
        sp = rng.uniform(0.35, 0.8)
        rr = 0.12 + 0.2 * u + rng.uniform(0, 0.06)
        for a in (a0, math.pi - a0 + rng.uniform(-0.15, 0.15)):
            dens = np.maximum(dens, np.exp(-((x - math.cos(a) * sp * grow) ** 2 + (y - (gy + 0.08 + math.sin(a) * sp * 0.55 * grow)) ** 2) / (rr * rr)))
    nz = N.fbm(x * 4 + 3, y * 4, f * 0.4, 4)
    dmask = np.clip((dens + (nz - 0.5) * 0.6 - 0.35) * 2.2, 0, 1) * (1 - sstep(0.5, 1.0, u)) * (y > gy - 0.05)
    light = np.clip(0.5 + (y - gy) * 0.8 + (nz - 0.5) * 0.4, 0, 1)
    low = np.exp(-((y - gy) / 0.15) ** 2)
    col = S.smoke(light) * (1 - 0.45 * low)[..., None] + S.main(0.5) * (0.45 * low)[..., None]
    layers = [(col, dmask * 0.75)]
    if f <= 3:
        k = 1 - f / 4.0
        layers.insert(0, (S.main(0.62), np.exp(-((x / 0.35) ** 2 + ((y - gy) / 0.12) ** 2)) * k * 0.85))
        layers.append((WHITE if not S.drops else S.main(0.92), np.exp(-((x / 0.12) ** 2 + ((y - gy) / 0.05) ** 2)) * k))
    items = []
    drops = []
    for j in range(14):
        a = rng.uniform(0.2, math.pi - 0.2)
        v = rng.uniform(0.5, 1.0)
        px = math.cos(a) * v * 0.9 * u
        py = gy + math.sin(a) * v * 1.0 * u - 0.9 * u * u
        vx, vy = math.cos(a) * v * 0.9, math.sin(a) * v - 1.8 * u
        nn = math.hypot(vx, vy) + 1e-6
        if py > gy - 0.02 and u > 0.06:
            if S.drops:
                drops.append((px, py, 0.022, vx, vy, 1 - u))
            else:
                items.append((px, py, px - vx / nn * 0.06, py - vy / nn * 0.06, 0.008, 1 - u))
    if S.drops:
        bd, hd = drops_layer(x, y, drops, aa)
        layers.append((S.main(0.3), bd))
        layers.append((S.main(0.8), hd * 0.8))
    else:
        layers.append((S.main(0.88), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    return rgb, a * edge_fade(x, y, 0.9, 0.99)


# ------------------------------------------------------------------ 직업 대표: 한 바퀴 베기 (2 × 2 — 위에서 봄, 강철)

SPIN_FRAMES = 8


def spin_frame(S, f, n=512):
    """허리 높이 한 바퀴 베기 (위에서 봄 — 자바가 눕혀 띄움): 칼빛 머리가 0 ~ 3 칸에 시계 방향으로 한 바퀴 돌며 띠 (반지름 0.8) 를 남김 — 머리 쪽 굵고 밝고 꼬리는
    가늘고 짙게 식음, 바깥 날 흰 줄 · 안쪽 짙은 테 · accent 가는 고리 · 반짝임 → 4 ~ 7 칸 조금 퍼지며 옅어짐"""
    rng = np.random.default_rng(531 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    aa = 1.6 / n
    a_s = math.pi / 2
    sw = 2 * math.pi * (1 - (1 - min(1.0, (f + 1) / 3.4)) ** 1.7)
    head = a_s - sw
    delta = np.mod(th - head, 2 * math.pi)
    vis = (delta <= sw + 1e-6).astype(float)
    q = np.clip(1 - delta / max(sw, 1e-3), 0, 1)
    fade = 1 - sstep(3.5, 7.4, f)
    R = 0.8 * (1 + 0.06 * sstep(3, 7.5, f))
    W = 0.15 * (1 - 0.35 * sstep(3, 7.5, f))
    w = W * (0.25 + 0.75 * q ** 0.8)
    dd = R - r
    inside = (1 - sstep(w - aa * 1.5, w + aa * 1.5, dd)) * sstep(-aa * 1.5, aa * 1.5, dd)
    body = vis * inside
    rel = np.clip(dd / np.maximum(w, 1e-4), 0, 1)
    band = 0.5 + 0.5 * np.sin((r - R) * 2 * math.pi * 18 + np.sin(th * 3) * 1.5)
    streak = sstep(0.62, 0.8, band) * (1 - rel) ** 0.6
    T = (1 - rel) ** 1.4 * (0.5 + 0.5 * q) + 0.2 * streak + 0.05
    Tb = banded(np.clip(T, 0, 1), [0.3, 0.45, 0.6, 0.74, 0.86])
    A = body * (0.95 - 0.5 * rel ** 1.5) * (0.45 + 0.55 * q ** 0.7)
    layers = []
    glow = ndimage.gaussian_filter(body * (0.3 + 0.7 * q), n * 0.02)
    layers.append((S.main(0.55), np.clip(glow * 1.9, 0, 1) * 0.7 * fade))
    layers.append((S.main(0.14), body * sstep(0.8, 0.95, rel) * (w > 0.02) * 0.8 * fade))
    layers.append((S.main(0.32 + 0.68 * Tb), A * fade))
    layers.append((S.accent(0.82), vis * np.exp(-((r - (R - W * 1.25)) / 0.005) ** 2) * (0.3 + 0.7 * q) * 0.8 * fade))
    edge = vis * np.exp(-((r - R) / 0.008) ** 2) * q ** 0.5
    layers.append((S.main(0.93), np.clip(edge * 1.2, 0, 1) * fade))
    layers.append((WHITE, edge * q ** 1.2 * fade))
    if f <= 3:
        hx, hy = R * math.cos(head), R * math.sin(head)
        hd = np.exp(-((x - hx) ** 2 + (y - hy) ** 2) / 0.004)
        layers.append((WHITE, np.clip(hd * 1.4, 0, 1)))
    sp_m = np.zeros_like(r)
    for i in range(16):
        ai = a_s - rng.uniform(0, 2 * math.pi)
        behind = float(np.mod(ai - head, 2 * math.pi))
        if behind > sw:
            continue
        tw = (0.55 + 0.45 * math.cos(f * 2.1 + i * 1.7)) * fade
        rr = R * (1 + rng.uniform(-0.12, 0.05))
        L = rng.uniform(0.03, 0.055)
        sp_m = np.maximum(sp_m, star4(x, y, rr * math.cos(ai), rr * math.sin(ai), L, L * 0.16, 0.3, aa) * tw)
    layers.append((S.main(0.9), np.clip(sp_m * 1.2, 0, 1)))
    layers.append((WHITE, np.clip(sp_m * 1.4 - 0.35, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.02, gain=0.8 * fade + 0.1)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 직업 대표: 땅 가르기 한 토막 (256 — 가로, 강철)

RIFT_FRAMES = 6


def rift_frame(S, f, n=256):
    """땅이 갈라진 한 토막 (위에서 봄 — 가로로 이어 붙임, 양 끝은 옅어짐): 0 흰 금이 번쩍 → 1 ~ 2 벌어진 틈 (짙은 속 · 밝은 가장자리 · 가지 금) →
    3 ~ 5 빛이 식고 짙은 틈만 남아 옅어짐"""
    rng = np.random.default_rng(541 + S.seed)
    x, y = grid(n)
    aa = 1.6 / n
    pts = bolt_points(rng, -1.0, 0.0, 1.0, 0.0, 5, 0.06)
    env = np.clip(1 - np.abs(x) / 1.0, 0, 1) ** 0.5
    open_ = [0.2, 0.85, 1.0, 0.9, 0.8, 0.7][f]
    glowk = [1.0, 1.0, 0.85, 0.5, 0.25, 0.1][f]
    k = 1 - sstep(3.5, 5.6, f)
    dist = np.full_like(x, 9.0)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        dist = np.minimum(dist, seg_dist(x, y, ax, ay, bx, by)[0])
    wid = 0.05 * open_ * env + 0.004
    gap = 1 - sstep(wid - aa, wid + aa, dist)
    br = np.zeros_like(x)
    for i in range(6):
        p0 = pts[rng.integers(4, len(pts) - 4)]
        a0 = rng.choice([-1, 1]) * rng.uniform(0.5, 1.2) + (0 if rng.uniform() < 0.5 else math.pi)
        L = rng.uniform(0.15, 0.35)
        bp = bolt_points(rng, p0[0], p0[1], p0[0] + L * math.cos(a0), p0[1] + L * math.sin(a0), 3, 0.25)
        br = np.maximum(br, polyline(x, y, bp, 0.012 * open_ + 0.002, aa))
    br = br * env
    lip = np.exp(-(np.clip(dist - wid, 0, None) / (0.03 + 0.02 * glowk)) ** 1.2) * (dist >= wid) * env
    layers = [(S.smoke(0.15), np.exp(-(dist / 0.16) ** 2) * env * 0.5 * k),
              (S.main(0.6), lip * glowk * 0.9),
              (S.main(0.04), np.maximum(gap, br * 0.9) * k),
              (S.main(0.85), np.exp(-(np.abs(dist - wid) / 0.006) ** 2) * env * glowk),
              (WHITE, np.exp(-(dist / 0.01) ** 2) * env * (1.0 if f == 0 else 0.0))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.03, gain=0.8 * glowk)
    return rgb, a * (1 - sstep(0.8, 0.98, np.abs(x))) * (1 - sstep(0.85, 0.98, np.abs(y)))


# ------------------------------------------------------------------ 직업 대표: 일섬 (256 — 가로 줄)

CUT_FRAMES = 8


def cut_frame(S, f, n=256):
    """일섬 · 허공에 걸린 칼자국: 0 ~ 1 왼쪽에서 오른쪽으로 그어짐 (머리 흰빛) → 2 ~ 4 머묾 (이어 돌림 — 빛이 숨쉼) → 5 터짐 (굵어지며 흰 섬광 · 튀는 줄기) →
    6 ~ 7 두 갈래로 벌어지며 사라짐"""
    rng = np.random.default_rng(551 + S.seed)
    x, y = grid(n)
    aa = 1.6 / n
    X0, X1 = -0.92, 0.92
    t = np.clip((x - X0) / (X1 - X0), 0, 1)
    prof = np.clip(1 - np.abs(2 * t - 1) ** 2.2, 0, 1) ** 0.6
    layers = []
    if f <= 4:
        headx = X0 + (X1 - X0) * [0.55, 1.0, 1.0, 1.0, 1.0][f]
        vis = (x <= headx) * (x >= X0) * 1.0
        br = [1.0, 1.0, 0.85, 1.0, 0.9][f]
        w = 0.022 * prof * br + 0.003
        core = (1 - sstep(w * 0.45, w * 0.45 + aa, np.abs(y))) * vis
        body = (1 - sstep(w - aa, w + aa, np.abs(y))) * vis
        glow = np.exp(-(np.abs(y) / (0.05 + 0.02 * br)) ** 2) * prof * vis
        layers += [(S.main(0.55), glow * 0.6 * br), (S.main(0.85), body), (WHITE, core)]
        if f == 0:
            layers.append((WHITE, np.exp(-((x - headx) ** 2 / 0.004 + y ** 2 / 0.002))))
    else:
        g = f - 5
        sep = [0.0, 0.05, 0.1][g]
        kk = [1.0, 0.6, 0.25][g]
        w = (0.05 if g == 0 else 0.016) * prof + 0.003
        for sgn in (-1, 1):
            yy = y - sgn * sep * prof
            layers += [(S.main(0.6), np.exp(-(np.abs(yy) / 0.07) ** 2) * prof * 0.6 * kk), (S.main(0.9), (1 - sstep(w - aa, w + aa, np.abs(yy))) * kk),
                       (WHITE, (1 - sstep(w * 0.4, w * 0.4 + aa, np.abs(yy))) * kk)]
        if g == 0:
            layers.append((WHITE, np.exp(-(y / 0.12) ** 2) * prof * 0.8))
        items = []
        for i in range(14):
            px = rng.uniform(X0 * 0.8, X1 * 0.8)
            a0 = (math.pi / 2 if rng.uniform() < 0.5 else -math.pi / 2) + rng.uniform(-0.6, 0.6)
            d0 = 0.05 + 0.25 * (g + 1) / 3 * rng.uniform(0.5, 1.0)
            hx, hy = px + math.cos(a0) * d0, math.sin(a0) * d0
            items.append((hx, hy, hx - math.cos(a0) * 0.05, hy - math.sin(a0) * 0.05, 0.007, kk))
        layers.append((S.main(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.7)
    return rgb, a * (1 - sstep(0.94, 0.99, np.abs(x))) * (1 - sstep(0.9, 0.99, np.abs(y)))


# ------------------------------------------------------------------ 직업 대표: 핏빛 달 (2 × 2 — 되풀이, 피)

MOON_FRAMES = 4


def moon_frame(S, f, n=512):
    """핏빛 달 (하늘에 떠 있음 — 되풀이 넷): 짙은 진홍 → 붉은 원판 (테두리 어두워짐 · 위 왼쪽이 밝음) · 잡음 얼룩 · 구덩이 몇 · 밝은 붉은 테 · 일렁이는 붉은 무리
    (넷이 이어 돎) · 넓은 검붉은 아지랑이 · 아래 가장자리에서 흘러내리는 핏줄기 셋"""
    N = Noise3(561 + S.seed)
    rng = np.random.default_rng(561 + S.seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    aa = 1.6 / n
    ph = f / MOON_FRAMES * 2 * math.pi
    Rm = 0.6
    disc = 1 - sstep(Rm - aa, Rm + aa, r)
    zz = np.sqrt(np.clip(1 - (r / Rm) ** 2, 0, 1))
    nx_, ny_ = x / Rm, y / Rm
    lightd = np.clip(-0.45 * nx_ + 0.55 * ny_ + 0.7 * zz, 0, 1)
    tex = N.fbm(x * 3 + 4, y * 3, 0.0, 5)
    tex2 = N.fbm(x * 9, y * 9 + 3, 2.0, 3)
    cr = np.zeros_like(r)
    for i in range(7):
        cx_, cy_ = rng.uniform(-0.4, 0.4), rng.uniform(-0.4, 0.4)
        rr = rng.uniform(0.04, 0.1)
        if math.hypot(cx_, cy_) > 0.45:
            continue
        dd = np.hypot(x - cx_, y - cy_)
        cr = cr - (1 - sstep(rr * 0.75, rr, dd)) * 0.12 + np.exp(-((dd - rr) / 0.01) ** 2) * 0.08
    T = 0.22 + 0.42 * lightd + 0.18 * (tex - 0.5) + 0.08 * (tex2 - 0.5) + cr
    T = np.clip(T, 0.05, 0.85)
    Tb = banded(T, [0.25, 0.38, 0.5, 0.62, 0.74])
    halo_n = N.fbm(np.cos(th) * 3 + math.cos(ph) * 0.6, np.sin(th) * 3 + math.sin(ph) * 0.6, 1.0, 3)
    corona = np.exp(-np.clip(r - Rm, 0, None) / (0.05 + 0.04 * halo_n)) * (r >= Rm - aa) * (0.7 + 0.3 * np.sin(th * 7 + ph + halo_n * 3))
    haze = np.exp(-(np.clip(r - Rm * 0.9, 0, None) / 0.3) ** 1.5) * 0.45
    drips = np.zeros_like(r)
    for a0, L in [(-1.75, 0.16), (-1.45, 0.24), (-1.25, 0.12)]:
        sx, sy = Rm * math.cos(a0), Rm * math.sin(a0)
        d, s = seg_dist(x, y, sx, sy + 0.02, sx, sy - L)
        wl = 0.016 * (1 - 0.5 * s) + 0.006
        drips = np.maximum(drips, 1 - sstep(wl - aa, wl + aa, d))
        drips = np.maximum(drips, 1 - sstep(0.022 - aa, 0.022 + aa, np.hypot(x - sx, y - (sy - L))))
    layers = [(S.main(0.3), haze), (S.main(0.6), np.clip(corona, 0, 1) * 0.85), (S.main(0.12 + 0.75 * Tb), disc),
              (S.main(0.85), np.exp(-((r - Rm) / 0.008) ** 2) * (0.5 + 0.5 * lightd) * 0.9), (S.main(0.35), drips * 0.95)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.5, sigma=0.03, gain=0.6)
    return rgb, a * (1 - sstep(0.93, 0.995, r))


# ------------------------------------------------------------------ 직업 대표: 빛의 대검 (256 × 1024 — 1 × 4 조각, 금)

EXCAL_FRAMES = 4


def excal_frame(S, f, w=256, h=1024):
    """빛의 대검 (위를 봄 — 되풀이 넷): 아래 끝 손잡이 · 금 코등이 (가운데 흰 금빛 보석) → 긴 칼날 (밑동 반폭 0.24 → 끝 뾰족) 은 빛 — 가운데 흰 심 · 금빛 몸 ·
    밝은 날 줄 · 넓은 금빛 아지랑이, 칼날을 따라 오르는 반짝임 (넷이 이어 돎)"""
    rng = np.random.default_rng(571 + S.seed)
    N = Noise3(572 + S.seed)
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = (np.arange(h) + 0.5) / h * 2 - 1
    x, Y = np.meshgrid(xs, -ys)
    Y = Y * (h / w)
    aa = 1.6 / w * 2
    ph = f / EXCAL_FRAMES
    tipY = 3.88
    baseY = -2.95
    t = np.clip((Y - baseY) / (tipY - baseY), 0, 1)
    half = 0.34 * (1 - t) ** 0.8 + 0.008
    blade_v = ((Y >= baseY) & (Y <= tipY)).astype(float)
    blade = (1 - sstep(half - aa, half + aa, np.abs(x))) * blade_v
    rel = np.clip(np.abs(x) / np.maximum(half, 1e-3), 0, 1)
    shimmer = N.fbm(x * 4, Y * 1.5 - ph * 4, ph * 3, 3)
    T = (1 - rel) ** 1.3 * 0.75 + 0.2 + 0.12 * (shimmer - 0.5)
    Tb = banded(np.clip(T, 0, 1), [0.45, 0.6, 0.75, 0.88])
    aura = np.exp(-(np.clip(np.abs(x) - half, 0, None) / (0.22 + 0.05 * math.sin(ph * 2 * math.pi))) ** 1.2) * (Y >= baseY - 0.2) * (Y <= tipY + 0.3)
    aura = aura * (0.75 + 0.25 * shimmer)
    edge = np.exp(-((np.abs(x) - half) / 0.012) ** 2) * blade_v
    core = np.exp(-(x / (0.035 + 0.02 * (1 - t))) ** 2) * blade_v
    guardY = -3.08
    guard = ((np.abs(Y - guardY) < 0.09 - 0.05 * np.abs(x) / 0.78) & (np.abs(x) < 0.78)).astype(float) * (1 - sstep(0.7, 0.78, np.abs(x)))
    grip = ((Y < guardY - 0.05) & (Y > -3.78) & (np.abs(x) < 0.07)).astype(float)
    wrap = 0.5 + 0.5 * np.sin(Y * 40)
    pommel = 1 - sstep(0.08, 0.1, np.hypot(x, Y + 3.86))
    gem = 1 - sstep(0.05, 0.065, np.hypot(x, Y - guardY))
    metal = np.clip(0.55 + 0.35 * np.cos(x * 3) - 0.2 * (np.abs(Y - guardY) / 0.09), 0, 1)
    motes = np.zeros_like(x)
    for i in range(22):
        q = (rng.uniform(0, 1) + ph) % 1.0
        px = rng.uniform(-0.45, 0.45)
        py = baseY + q * (tipY - baseY)
        tw = math.sin(math.pi * q) * (0.5 + 0.5 * math.cos(i * 2.1 + ph * 6.28))
        motes = np.maximum(motes, star4(x, Y, px, py, 0.05 * (0.5 + 0.5 * tw), 0.008, 0.2, aa) * tw)
    layers = [(S.main(0.6), aura * 0.75), (S.main(0.55 + 0.45 * Tb), blade), (S.main(0.97), edge * 0.9), (WHITE, np.clip(core * 1.2, 0, 1)),
              (S.main(0.3 + 0.55 * metal), guard), (S.main(0.25 + 0.35 * wrap), grip), (S.main(0.75), pommel), (WHITE, gem),
              (S.main(0.95), np.clip(motes * 1.2, 0, 1))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.62, sigma=0.012, gain=0.7)
    a = a * (1 - sstep(0.92, 0.99, np.abs(x))) * (1 - sstep(0.985, 0.999, np.abs(Y) / (h / w)))
    return rgb, a


# ------------------------------------------------------------------ 직업 대표: 발키리의 날개 (2 × 2 — 카메라를 봄, 날갯짓 되풀이, 강철)

WINGS_FRAMES = 6


def _bezier(p0, p1, p2, u):
    return ((1 - u) ** 2 * p0[0] + 2 * (1 - u) * u * p1[0] + u * u * p2[0], (1 - u) ** 2 * p0[1] + 2 * (1 - u) * u * p1[1] + u * u * p2[1])


def _feather(x, y, rx, ry, dx, dy, L, w, aa):
    """깃털 한 장 (뿌리 rx, ry · 쪽 dx, dy · 길이 L · 반폭 w): 잎 모양 마스크 · 길이 쪽 t · 가로 쪽 b (−1 ~ 1)"""
    a = (x - rx) * dx + (y - ry) * dy
    b = -(x - rx) * dy + (y - ry) * dx
    t = a / L
    hw = w * np.clip(4 * t * (1 - t), 0, 1) ** 0.6 * (1 - 0.3 * np.clip(t, 0, 1)) + 1e-4
    m = (1 - sstep(hw - aa, hw + aa, np.abs(b))) * (t >= 0) * (t <= 1)
    return m, np.clip(t, 0, 1), np.clip(b / hw, -1, 1)


def wings_frame(S, f, n=512):
    """발키리의 날개 한 쌍 (등 뒤 — 카메라를 봄): 어깨에서 위 · 바깥으로 휜 날개 줄기 (흰 빛 띠) 아래로 깃털 세 겹 (긴 첫째 깃 · 둘째 깃 · 덮깃 — 바깥일수록 길고
    바깥으로 기욺) · 깃마다 뿌리 쪽 밝음 → 끝 쪽 짙음 · 깃대 · 빛나는 가장자리, 넓은 빛 무리 · 떨어지는 반짝임. 여섯 칸 날갯짓 (어깨 둘레로 ±11° · 깃이 벌어졌다 모임)"""
    rng = np.random.default_rng(581 + S.seed)
    x, y = grid(n)
    aa = 1.6 / n
    ph = f / WINGS_FRAMES * 2 * math.pi
    flap = math.radians(11) * math.sin(ph)
    spread = 1 + 0.08 * math.cos(ph)
    layers = []
    masks = []
    edge_all = np.zeros_like(x)
    for side in (-1, 1):
        sx = 0.07 * side
        sy = -0.06
        ca, sa = math.cos(flap * side), math.sin(flap * side)

        def rot(px, py):
            # 어깨 둘레로 돌림 (오른쪽 날개 + 는 위로 · 왼쪽은 거울)
            qx, qy = px - sx, py - sy
            return sx + qx * ca - qy * sa, sy + qx * sa + qy * ca
        P0 = (sx, sy)
        P1 = rot(0.36 * side, 0.8)
        P2 = rot(0.88 * side, 0.62)
        for row, (cnt, lscale, wscale, shade0) in enumerate([(13, 1.0, 1.0, 0.0), (11, 0.62, 0.95, 0.08), (9, 0.33, 0.8, 0.16)]):
            for i in range(cnt):
                u = 0.1 + 0.88 * i / (cnt - 1)
                ex, ey = _bezier(P0, P1, P2, u)
                beta = math.radians(8 + 50 * u ** 1.3) * spread
                # 아래 (0, −1) 를 바깥쪽으로 beta 돌림 → 날개마다 거울
                dx, dy = math.sin(beta) * side, -math.cos(beta)
                ang = math.atan2(dy, dx) + flap * side * 0.6
                dx, dy = math.cos(ang), math.sin(ang)
                L = (0.42 + 0.55 * u ** 1.2) * lscale * (1 + rng.uniform(-0.04, 0.04))
                w = (0.05 + 0.025 * (1 - u)) * wscale
                m, t, b = _feather(x, y, ex, ey, dx, dy, L, w, aa)
                if not m.any():
                    continue
                T = 0.42 + 0.34 * (1 - t) ** 0.8 + 0.1 * b * side + shade0
                layers.append((S.main(0.16), np.clip(ndimage.gaussian_filter(m, 1.2) * 1.2, 0, 1) * 0.55))
                layers.append((S.main(np.clip(T, 0, 1)), m * 0.92))
                rim = m * sstep(0.7, 0.95, np.abs(b))
                layers.append((S.main(0.93), rim * 0.8))
                rach = m * np.exp(-((b * w) / 0.004) ** 2) * (1 - t) ** 0.6
                layers.append((WHITE, rach * 0.6))
                masks.append(m)
        # 날개 줄기 (흰 빛 띠 — 어깨에서 끝으로 가늘어짐)
        for i in range(24):
            u0, u1 = i / 24, (i + 1) / 24
            ax, ay = _bezier(P0, P1, P2, u0)
            bx, by = _bezier(P0, P1, P2, u1)
            d, sv = seg_dist(x, y, ax, ay, bx, by)
            wl = 0.018 * (1 - 0.7 * (u0 + (u1 - u0) * sv)) + 0.003
            edge_all = np.maximum(edge_all, 1 - sstep(wl - aa, wl + aa, d))
    body = np.clip(np.sum(np.stack(masks), 0), 0, 1) if masks else np.zeros_like(x)
    glow = ndimage.gaussian_filter(np.maximum(body, edge_all), n * 0.03)
    base = [(S.main(0.6), np.clip(glow * 1.6, 0, 1) * 0.55)]
    layers = base + layers + [(S.main(0.9), edge_all), (WHITE, edge_all * 0.8)]
    sp_m = np.zeros_like(x)
    for i in range(16):
        px = rng.uniform(-0.9, 0.9)
        py0 = rng.uniform(-0.6, 0.6)
        q = (rng.uniform(0, 1) + f / WINGS_FRAMES) % 1.0
        py = py0 - 0.35 * q
        tw = math.sin(math.pi * q)
        L = 0.03 * (0.6 + 0.4 * tw)
        sp_m = np.maximum(sp_m, star4(x, y, px, py, L, L * 0.16, 0.2, aa) * tw * (abs(px) > 0.15))
    layers += [(S.main(0.92), np.clip(sp_m * 1.2, 0, 1)), (WHITE, np.clip(sp_m * 1.3 - 0.4, 0, 1))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.02, gain=0.6)
    return rgb, a * edge_fade(x, y, 0.92, 0.99)


# ------------------------------------------------------------------ 원소 결

STYLES = {
    # 은빛 · 푸른 달빛 (검사 — 뒤에 수호기사)
    "steel": Style("el_steel", "steel",
                   main=ramp([(0.0, "#060A16"), (0.12, "#0E1A34"), (0.26, "#1C3762"), (0.4, "#2C5D9A"), (0.52, "#4589CC"), (0.64, "#6FB0EA"),
                              (0.76, "#A2D2FA"), (0.86, "#CFE8FF"), (0.94, "#EEF7FF"), (1.0, "#FFFFFF")]),
                   accent=ramp([(0.0, "#0A2430"), (0.5, "#3FB8E6"), (0.8, "#A8F0FF"), (1.0, "#FFFFFF")]),
                   smoke=ramp([(0.0, "#0C1220"), (0.45, "#22304A"), (0.8, "#46587A"), (1.0, "#8EA4C8")]),
                   ring="ticks", smoke_alpha=0.3, seed=1),
    # 피 (블러드 나이트 — 뒤에 워록 · 다크 나이트)
    "blood": Style("el_blood", "blood",
                   main=ramp([(0.0, "#080103"), (0.12, "#1E0207"), (0.26, "#45040D"), (0.4, "#760A16"), (0.52, "#A8121F"), (0.64, "#D4232D"),
                              (0.76, "#EF4D4F"), (0.86, "#FF8C86"), (0.94, "#FFD0CA"), (1.0, "#FFFFFF")]),
                   accent=ramp([(0.0, "#140206"), (0.5, "#5A0A1A"), (0.8, "#C0283E"), (1.0, "#FF8A96")]),
                   smoke=ramp([(0.0, "#0A0306"), (0.45, "#24080F"), (0.8, "#42121D"), (1.0, "#62202E")]),
                   ring="thorns", smoke_alpha=0.55, seed=3),
    # 금빛 (아스널 킹 묶음 ak 에 더함 — 소드마스터): GOLD 램프는 tools/jobvfx/arsenal.py 와 같음
    "gold": Style("ak", "gold",
                  main=ramp([(0.0, "#140C06"), (0.12, "#2E1A08"), (0.26, "#5E3608"), (0.4, "#9A5C0C"), (0.52, "#D08A14"), (0.64, "#F0B028"),
                             (0.76, "#FFD24E"), (0.86, "#FFE896"), (0.94, "#FFF7DC"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#3A2A16"), (0.5, "#C8A56A"), (0.8, "#FFF0C0"), (1.0, "#FFFFFF")]),
                  smoke=ramp([(0.0, "#1A120C"), (0.45, "#3E2C1C"), (0.8, "#6E5236"), (1.0, "#A8875A")]),
                  seed=5),
}


def frames_of(S, name):
    """미리 보기 · 그림판에 쓰는 칸 목록"""
    if name == "slash":
        return [to_img(*slash_frame(S, f)) for f in range(SLASH_FRAMES)]
    if name == "xslash":
        return [to_img(*xslash_frame(S, f)) for f in range(XSLASH_FRAMES)]
    if name == "boom":
        return [to_img(*boom_frame(S, f)) for f in range(BOOM_FRAMES)]
    if name == "boom_s":
        return [to_img(*boom_frame(S, int(round(i * (BOOM_FRAMES - 1) / 11)), n=256, seed_add=7)) for i in range(12)]
    if name == "shock":
        return [to_img(*shock_frame(S, f)) for f in range(SHOCK_FRAMES)]
    if name == "shock_s":
        return [to_img(*shock_frame(S, f, n=256, frames=10, seed_add=3)) for f in range(10)]
    if name == "ring":
        return [to_img(*ring_frame(S, f)) for f in range(RING_FRAMES)]
    if name == "crack":
        return [to_img(*crack_frame(S, f)) for f in range(CRACK_FRAMES)]
    if name == "flash":
        return [to_img(*flash_frame(S, f)) for f in range(FLASH_FRAMES)]
    if name == "charge":
        return [to_img(*charge_frame(S, f)) for f in range(CHARGE_FRAMES)]
    if name == "small":
        return ember(S) + shard(S)
    if name == "pillar":
        return [to_img(*pillar_frame(S, f)) for f in range(PILLAR_FRAMES)]
    if name == "dust":
        return [to_img(*dust_frame(S, f)) for f in range(DUST_FRAMES)]
    if name == "spin":
        return [to_img(*spin_frame(S, f)) for f in range(SPIN_FRAMES)]
    if name == "rift":
        return [to_img(*rift_frame(S, f)) for f in range(RIFT_FRAMES)]
    if name == "cut":
        return [to_img(*cut_frame(S, f)) for f in range(CUT_FRAMES)]
    if name == "moon":
        return [to_img(*moon_frame(S, f)) for f in range(MOON_FRAMES)]
    if name == "excal":
        return [to_img(*excal_frame(S, f)) for f in range(EXCAL_FRAMES)]
    if name == "wings":
        return [to_img(*wings_frame(S, f)) for f in range(WINGS_FRAMES)]
    raise KeyError(name)


def grid_tiles(name, make, rows, cols):
    """한 그림 (cols·256 × rows·256) 의 칸들을 조각 rows × cols 판으로: 이름_rc"""
    cache = {}

    def frames():
        if "f" not in cache:
            cache["f"] = make()
        return cache["f"]
    out = []
    for r in range(rows):
        for c in range(cols):
            def mk(r=r, c=c):
                return [im.crop((c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE)) for im in frames()]
            out.append(("%s_%d%d" % (name, r, c), mk, True))
    return out


def sets_for(S):
    """그림판 목록 [(이름, 만들기, colored)] — tools/vfx_sprites.py 가 더함"""
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(S, name)
            return cache[name]
        return mk

    def mirrored(name):
        return lambda: [im.transpose(Image.FLIP_LEFT_RIGHT) for im in fr(name)()]
    if S.kind == "gold":
        return (tiled(p + "slash", fr("slash")) + tiled(p + "slashm", mirrored("slash")) + tiled(p + "xslash", fr("xslash"))
                + [(p + "cut", fr("cut"), True)] + grid_tiles(p + "excal", fr("excal"), 4, 1))
    out = (tiled(p + "slash", fr("slash")) + tiled(p + "slashm", mirrored("slash")) + tiled(p + "xslash", fr("xslash")) + tiled(p + "boom", fr("boom"))
           + tiled(p + "shock", fr("shock")) + tiled(p + "ring", fr("ring"))
           + [(p + "boom_s", fr("boom_s"), True), (p + "shock_s", fr("shock_s"), True), (p + "crack_s", fr("crack"), True), (p + "flash", fr("flash"), True),
              (p + "charge", fr("charge"), True), (p + "ember", lambda: ember(S), True), (p + "shard", lambda: shard(S), True),
              (p + "pillar", fr("pillar"), True), (p + "dust", fr("dust"), True)])
    if S.kind == "steel":
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "rift", fr("rift"), True), (p + "cut", fr("cut"), True)]
                + tiled(p + "wings", fr("wings")))
    if S.kind == "blood":
        out += tiled(p + "moon", fr("moon")) + [(p + "rift", fr("rift"), True)]
    return out


def sprites():
    out = []
    for key in ("steel", "blood", "gold"):
        out += sets_for(STYLES[key])
    return out


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        S = STYLES[sys.argv[sys.argv.index("--style") + 1]]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else ["slash", "xslash", "boom", "flash", "small", "pillar", "dust",
                                                                                               "shock", "ring", "charge", "crack"]
        preview({k: frames_of(S, k) for k in names}, out)
