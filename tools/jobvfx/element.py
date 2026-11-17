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
        return self.kind in ("blood", "hex")


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
    return (boom_splash if S.kind in ("blood", "hex") else boom_light)(S, f, n, frames, seed_add)


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


def crack_frame(S, f, n=256, burn_on=True):
    """땅 자국 (위에서 봄): smoke 로 물든 자국 + 가운데서 뻗은 금 (밝음 → 식음 · 피 = 흩뿌린 얼룩).
    2026-11-17 burn_on False = 하늘 금 (crackv — 그을린 자국 · 얼룩 없이 빛나는 금만: 세운 금 RuneKit.skyPlate 의 하늘에 흙빛 자국이 떠 보였음, CI 44001064291)"""
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
    layers = [(S.smoke(0.2), burn * (0.85 - 0.3 * u))] if burn_on else []
    if S.drops and burn_on:
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
    return (flash_splash if S.kind in ("blood", "hex") else flash_light)(S, f, n)


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
    return (pillar_geyser if S.kind in ("blood", "hex") else pillar_beam)(S, f)


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


# ------------------------------------------------------------------ 1인칭 빠른 줄 (256 — 되풀이 4칸)

SPEED_FRAMES = 4


def speed_frame(S, f, n=256, frames=SPEED_FRAMES):
    """2026-11-16 1인칭 빠른 줄 (예전 rfx_jspeed2 와 같은 틀 — 반 크기 1.35 · 눈앞 1.4칸 · 되풀이): 가운데는 비고 반지름 0.5 ~ 1 에 바깥으로 흐르는 가는 빛줄기
    (칸마다 자리 · 길이가 바뀌어 깜빡이며 흐름 — 안쪽 끝이 가늘고 바깥이 굵음) — 짙은 빛 몸 · 밝은 빛 · 흰 심"""
    rng = np.random.default_rng(907 + S.seed * 13 + f * 31)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    body = np.zeros_like(x)
    core = np.zeros_like(x)
    for i in range(30):
        a = rng.uniform(0, 2 * math.pi)
        r0 = rng.uniform(0.5, 0.8)
        L = rng.uniform(0.12, 0.3)
        w = rng.uniform(0.012, 0.024)
        dth = np.angle(np.exp(1j * (th - a)))
        perp = np.abs(dth) * r
        along = (r - r0) / L
        inside = ((along > 0) & (along < 1)).astype(float)
        wid = w * (0.25 + 0.75 * np.clip(along, 0, 1))
        s = (1 - sstep(wid - aa, wid + aa, perp)) * inside * np.sin(np.pi * np.clip(along, 0, 1)) ** 0.5
        body = np.maximum(body, s)
        core = np.maximum(core, s * (perp < wid * 0.45))
    layers = [(S.main(0.55), body * 0.9), (S.accent(0.85), core), (WHITE, core * 0.6)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.7, sigma=0.01, gain=0.35)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 직업 대표: 일섬 (256 — 가로 줄)

CUT_FRAMES = 8


def cut_frame(S, f, n=256):
    """일섬 · 허공에 걸린 칼자국: 0 ~ 1 왼쪽에서 오른쪽으로 그어짐 (머리 흰빛) → 2 ~ 4 머묾 (이어 돌림 — 빛이 숨쉼) → 5 터짐 (굵어지며 흰 섬광 · 튀는 줄기) →
    6 ~ 7 두 갈래로 벌어지며 사라짐"""
    x, y = grid(n)
    return _cut(S, f, x, y, 1.6 / n, n)


def cutl_frame(S, f, w=256, h=40):
    """2026-11-17 긴 일섬 (예전 rpg:light_cut 의 긴 줄 — 룬 무간참역 · 하늘 가르기 · 꿰뚫는 줄): 일섬과 같은 모양을 가로로 긴 칸 (6.4 : 1) 에. 정사각 일섬을 9칸 넘게
    키우면 한 텍셀 줄이 디더링으로 점선처럼 끊겨 사탕 막대처럼 보였음 (CI 43977395799 무간참역) — 긴 칸은 줄을 따라 텍셀이 256 이라 길어도 촘촘함"""
    from .arcane import rect
    x, y, ky = rect(w, h)
    rgb, a = _cut(S, f, x, y, 1.6 / w, (h, w))
    return rgb, a * (1 - sstep(ky * 0.7, ky * 0.98, np.abs(y)))


def _cut(S, f, x, y, aa, n):
    """일섬 칸 하나 (x · y = 칸의 자리 — 가로 [-1, 1], aa = 가장자리 부드러움, n = 칸 크기 한 변 또는 (높이, 너비))"""
    rng = np.random.default_rng(551 + S.seed)
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
    # 2026-11-16 물들이는 그림 (VfxKit — 서버가 계열 빛으로 물들임): 회색 단계 (어두운 테 → 흰 심). pixel.sheet_cells 가 부드러운 가장자리를 어둡게 바꾼 뒤 이 램프로
    "tint": Style("tint", "tint",
                  main=ramp([(0.0, "#2A2A2A"), (0.3, "#555555"), (0.55, "#8A8A8A"), (0.75, "#BDBDBD"), (0.9, "#E6E6E6"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#9A9A9A"), (0.6, "#E0E0E0"), (1.0, "#FFFFFF")]),
                  smoke=ramp([(0.0, "#303030"), (0.6, "#6A6A6A"), (1.0, "#A0A0A0")]),
                  ring="ticks", smoke_alpha=0.3, seed=0),
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
    # 2026-11-15 그림자 (암살자 — 자홍 · 짙은 보라: jobs.json assassin 의 빛): 그림자 연기 · 엇갈린 베기 · 검은 장미 · 꽃 · 비수 · 일식 (tools/jobvfx/shadow.py)
    "shadow": Style("el_shadow", "shadow",
                    main=ramp([(0.0, "#06020A"), (0.12, "#14061F"), (0.26, "#2A0C44"), (0.4, "#45136E"), (0.52, "#6A1D9C"), (0.64, "#9333C8"),
                               (0.76, "#BC5FE6"), (0.86, "#DE9DF6"), (0.94, "#F5DDFD"), (1.0, "#FFFFFF")]),
                    accent=ramp([(0.0, "#1A0514"), (0.5, "#B0217F"), (0.8, "#FF6CCB"), (1.0, "#FFE0F4")]),
                    smoke=ramp([(0.0, "#050208"), (0.45, "#150B22"), (0.8, "#2C1C42"), (1.0, "#4E3A6C")]),
                    ring="ticks", smoke_alpha=0.5, seed=7),
    # 2026-11-15 환영 (트릭스터 — 자홍 · 짙은 보라 + 청록 유리빛): 거울 · 깨지는 거울 · 손거울 · 띠 고리 (tools/jobvfx/mirror.py). 2026-11-16 글꼴에 넣음
    "illusion": Style("el_illusion", "illusion",
                      main=ramp([(0.0, "#0E0210"), (0.12, "#2A0626"), (0.26, "#520C46"), (0.4, "#80156A"), (0.52, "#AC2188"), (0.64, "#D23BA6"),
                                 (0.76, "#EE66C2"), (0.86, "#FF9FDA"), (0.94, "#FFD8F0"), (1.0, "#FFFFFF")]),
                      accent=ramp([(0.0, "#04161C"), (0.5, "#147A8C"), (0.8, "#6FE6F4"), (1.0, "#E8FFFF")]),
                      smoke=ramp([(0.0, "#07030A"), (0.45, "#1A0A20"), (0.8, "#36183F"), (1.0, "#5A3466")]),
                      ring="ticks", smoke_alpha=0.45, seed=9),
    # 2026-11-15 서커스 (피에로 — 진홍 · 크림 금빛, 무지개 없이): 색종이 · 풍선 · 불꽃놀이 · 조명 (tools/jobvfx/circus.py). 2026-11-16 글꼴에 넣음
    "circus": Style("el_circus", "circus",
                    main=ramp([(0.0, "#0E0204"), (0.12, "#2C0509"), (0.26, "#5A0A14"), (0.4, "#8C1222"), (0.52, "#BC1E30"), (0.64, "#E23A44"),
                               (0.76, "#FF6A68"), (0.86, "#FF9E96"), (0.94, "#FFD8D0"), (1.0, "#FFFFFF")]),
                    accent=ramp([(0.0, "#2A1A06"), (0.5, "#C8962E"), (0.8, "#FFE28A"), (1.0, "#FFFBEA")]),
                    smoke=ramp([(0.0, "#0C0606"), (0.45, "#221012"), (0.8, "#3E2024"), (1.0, "#5E3A3C")]),
                    ring="ticks", smoke_alpha=0.4, seed=11),
    # 2026-11-16 비전 (매지션 · 아크메이지 — 남빛 · 금빛: jobs.json mage 의 빛 [0.3, 0.36, 0.95] · [1, 0.84, 0.36]): 떠도는 별 · 별 마탄 · 광선 · 깨지는 방패 ·
    # 금서의 주문 · 별자리의 별 · 별똥 · 별의 소용돌이 · 빛 고리 · 빛의 방패 (tools/jobvfx/arcane.py). 맞은 자리는 X 베기 대신 별빛 (arcane.starhit_frame)
    "arcane": Style("el_arcane", "arcane",
                    main=ramp([(0.0, "#05061A"), (0.12, "#0E1240"), (0.26, "#1B2470"), (0.4, "#2A3BA8"), (0.52, "#3F57D8"), (0.64, "#5D7CF2"),
                               (0.76, "#86A4FF"), (0.86, "#B5C8FF"), (0.94, "#E2EAFF"), (1.0, "#FFFFFF")]),
                    accent=ramp([(0.0, "#2A1A04"), (0.5, "#B07A1A"), (0.8, "#FFD25A"), (1.0, "#FFF6D6")]),
                    smoke=ramp([(0.0, "#04050E"), (0.45, "#0E1226"), (0.8, "#1E2444"), (1.0, "#343C66")]),
                    ring="ticks", smoke_alpha=0.35, seed=13),
    # 2026-11-16 혼 (네크로맨서 — 녹색 혼불 · 뼈빛: jobs.json necromancer 의 빛 [0.3, 0.92, 0.45] · [0.92, 0.88, 0.74]): 혼불 · 해골 · 갈라진 땅 · 무덤 · 뼈 창 ·
    # 망자의 손 · 연기 (tools/jobvfx/soul.py)
    "soul": Style("el_soul", "soul",
                  main=ramp([(0.0, "#020A05"), (0.12, "#06200E"), (0.26, "#0C3E1C"), (0.4, "#14662E"), (0.52, "#1F9244"), (0.64, "#35BC5E"),
                             (0.76, "#62DE84"), (0.86, "#A0F2B4"), (0.94, "#DAFCE2"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#1A160C"), (0.5, "#8C8266"), (0.8, "#EAE0BE"), (1.0, "#FFFBEE")]),
                  smoke=ramp([(0.0, "#030604"), (0.45, "#0C1A10"), (0.8, "#1E3424"), (1.0, "#3A5440")]),
                  ring="ticks", smoke_alpha=0.5, seed=15),
    # 2026-11-16 분노 (전사 — 주황 · 진홍 불씨: jobs.json warrior 의 빛 [1, 0.56, 0.16] · [0.9, 0.14, 0.12]): 불꽃 갈기 늑대 · 회오리 · 도는 도끼날 · 불꽃 꼬리 ·
    # 방패 깨짐 · 갈라지는 땅 (tools/jobvfx/beast.py)
    "rage": Style("el_rage", "rage",
                  main=ramp([(0.0, "#140405"), (0.12, "#360A0C"), (0.26, "#661012"), (0.4, "#A0161A"), (0.52, "#D8261E"), (0.64, "#F0521E"),
                             (0.76, "#FF8F28"), (0.86, "#FFC060"), (0.94, "#FFE8B0"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#2A0E04"), (0.5, "#C86A10"), (0.8, "#FFD050"), (1.0, "#FFFBE0")]),
                  smoke=ramp([(0.0, "#0C0606"), (0.45, "#2A1612"), (0.8, "#4A2C24"), (1.0, "#6A4A3E")]),
                  ring="ticks", smoke_alpha=0.45, seed=17),
    # 2026-11-16 곰 (워로드 — 호박빛 · 갈색: jobs.json warlord 의 빛 [1.0, 0.58, 0.16] · [0.66, 0.36, 0.14]): 곰의 혼 · 앞발 · 돌창 · 갈라지는 땅
    # (accent = 돌 · 흙빛 — 돌창 몸)
    "bear": Style("el_bear", "bear",
                  main=ramp([(0.0, "#100804"), (0.12, "#2A1408"), (0.26, "#4E260E"), (0.4, "#7A4016"), (0.52, "#A85C24"), (0.64, "#D8781E"),
                             (0.76, "#FF9A30"), (0.86, "#FFC266"), (0.94, "#FFE6B4"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#14100C"), (0.5, "#6A5A48"), (0.8, "#B8A488"), (1.0, "#F0E6D2")]),
                  smoke=ramp([(0.0, "#0E0A06"), (0.45, "#2C2016"), (0.8, "#4E3C2A"), (1.0, "#6E5A44")]),
                  ring="ticks", smoke_alpha=0.5, seed=19),
    # 2026-11-16 서리 (프로스트 레이더 — 옅은 얼음빛 · 빙하 남빛: jobs.json frostraider 의 빛 [0.6, 0.9, 1.0] · [0.94, 0.99, 1.0]): 서리 고리 · 얼음 결정 · 서리 띠
    # (arcane.halo_frame · frost_frame · aura_frame 을 서리 빛으로). 도끼 · 결정 · 얼음 가시는 3D 그대로
    "frost": Style("el_frost", "frost",
                   main=ramp([(0.0, "#040A18"), (0.12, "#0A1A34"), (0.26, "#123A66"), (0.4, "#1E5E96"), (0.52, "#2E86C2"), (0.64, "#4CAEE6"),
                              (0.76, "#7ED0F8"), (0.86, "#B4E8FF"), (0.94, "#E2F7FF"), (1.0, "#FFFFFF")]),
                   accent=ramp([(0.0, "#06102A"), (0.5, "#3A64B0"), (0.8, "#A8D2FF"), (1.0, "#F4FBFF")]),
                   smoke=ramp([(0.0, "#0C1420"), (0.45, "#2A3C52"), (0.8, "#5E7896"), (1.0, "#9AB2CC")]),
                   ring="ticks", smoke_alpha=0.4, seed=21),
    # 2026-11-16 룬 (마검사 · 아케인 블레이드 — 보랏빛 · 청록: jobs.json spellblade [0.62, 0.34, 1] · [0.34, 0.96, 0.95]): 룬 띠 · 새긴 글자 · 아홉 글자 ·
    # 빛기둥 · 허공의 찢어짐 (tools/jobvfx/rune.py)
    "rune": Style("el_rune", "rune",
                  main=ramp([(0.0, "#0A0418"), (0.12, "#1C0A3C"), (0.26, "#34146C"), (0.4, "#5022A0"), (0.52, "#6E36D0"), (0.64, "#9058F0"),
                             (0.76, "#B484FF"), (0.86, "#D2B0FF"), (0.94, "#EEE0FF"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#04161A"), (0.5, "#1C9EA8"), (0.8, "#57F5F2"), (1.0, "#E8FFFF")]),
                  smoke=ramp([(0.0, "#06030E"), (0.45, "#160C2C"), (0.8, "#2C1C4E"), (1.0, "#4A3A72")]),
                  ring="ticks", smoke_alpha=0.45, seed=23),
    # 2026-11-16 원소의 칼 (엘리멘탈 블레이드 — 금빛 주황 · 얼음 하늘빛: jobs.json elementalblade [1.0, 0.72, 0.3] · [0.55, 0.9, 1.0]): 번개 · 번개 고리 · 빛의 창
    # (storm.py) · 불꽃 (forge) · 회오리 · 도는 날 (beast) · 서리 띠 · 고리 (arcane). 물 · 얼음 빛은 서리 묶음, 바위는 곰 묶음 (PhantomFx.lib)
    "element": Style("el_element", "element",
                     main=ramp([(0.0, "#140A02"), (0.12, "#3A1C06"), (0.26, "#6E360C"), (0.4, "#A85A12"), (0.52, "#D8801C"), (0.64, "#F8A030"),
                                (0.76, "#FFB84C"), (0.86, "#FFD890"), (0.94, "#FFF2D8"), (1.0, "#FFFFFF")]),
                     accent=ramp([(0.0, "#061A2A"), (0.5, "#2E86B8"), (0.8, "#8CE6FF"), (1.0, "#F0FCFF")]),
                     smoke=ramp([(0.0, "#0E0A06"), (0.45, "#2C2016"), (0.8, "#4E3C2A"), (1.0, "#6E5A44")]),
                     ring="ticks", smoke_alpha=0.45, seed=25),
    # 2026-11-16 성광 (사제 · 세인트 — 크림 금빛 · 회복의 연둣빛: jobs.json priest [1, 0.93, 0.62] · [0.5, 0.95, 0.56]): 회복 십자 · 음표 · 성흔 · 종 · 연꽃 · 큰 꽃 ·
    # 나비 (tools/jobvfx/holy.py) + 맞은 자리 별빛 (arcane.starhit) · 빛의 방패 · 고리 · 띠
    "holy": Style("el_holy", "holy",
                  main=ramp([(0.0, "#140E04"), (0.12, "#33240A"), (0.26, "#5E4512"), (0.4, "#8E6C1E"), (0.52, "#BE942E"), (0.64, "#DDB54A"),
                             (0.76, "#F2D27A"), (0.86, "#FBE6A8"), (0.94, "#FFF6DC"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#04200C"), (0.5, "#2E9E4E"), (0.8, "#8FF0A6"), (1.0, "#EEFFF2")]),
                  smoke=ramp([(0.0, "#0E0C06"), (0.45, "#2A2414"), (0.8, "#4A4226"), (1.0, "#6E6440")]),
                  ring="ticks", smoke_alpha=0.45, seed=27),
    # 2026-11-16 꽃잎 (세인트가 PETAL 빛으로 부르는 꽃 · 연꽃 · 꽃잎 · 일섬 — 분홍 꽃잎 · 크림 금빛 속): tools/jobvfx/holy.py 의 꽃 그림을 꽃잎 빛으로
    "petal": Style("el_petal", "petal",
                   main=ramp([(0.0, "#1A0610"), (0.12, "#3C0E24"), (0.26, "#6E1E46"), (0.4, "#A6386C"), (0.52, "#D25A8E"), (0.64, "#EE80AC"),
                              (0.76, "#FFA6C8"), (0.86, "#FFC8DE"), (0.94, "#FFEAF2"), (1.0, "#FFFFFF")]),
                   accent=ramp([(0.0, "#2A1A06"), (0.5, "#D8B04A"), (0.8, "#FFE6A0"), (1.0, "#FFFBEA")]),
                   smoke=ramp([(0.0, "#120810"), (0.45, "#2E1626"), (0.8, "#4E2C42"), (1.0, "#6E4A62")]),
                   ring="ticks", smoke_alpha=0.45, seed=29),
    # 2026-11-16 혈주 (워록 — 진홍 피 · 금빛: jobs.json warlock [0.88, 0.07, 0.24] · [1, 0.76, 0.3]): 핏빛 창 · 웅덩이 · 핏물 기둥 · 피의 심장 · 핏빛 불기둥
    # (tools/jobvfx/hex.py) + 피처럼 튀는 터짐 · 섬광 (블러드 나이트와 같은 길)
    "hex": Style("el_hex", "hex",
                 main=ramp([(0.0, "#120206"), (0.12, "#2E040E"), (0.26, "#58081C"), (0.4, "#8A0E2A"), (0.52, "#B8163A"), (0.64, "#E0304C"),
                            (0.76, "#FF5A66"), (0.86, "#FF9A96"), (0.94, "#FFD8D0"), (1.0, "#FFFFFF")]),
                 accent=ramp([(0.0, "#2A1604"), (0.5, "#D08A20"), (0.8, "#FFD070"), (1.0, "#FFF6DA")]),
                 smoke=ramp([(0.0, "#0C0406"), (0.45, "#26101A"), (0.8, "#42202C"), (1.0, "#5E3440")]),
                 ring="ticks", smoke_alpha=0.45, seed=31),
    # 2026-11-16 수호 (수호기사 — 강철 푸른빛 · 흰 빛: jobs.json guardian [0.46, 0.62, 0.84] · [0.95, 0.97, 1]): 도는 방패 · 빛의 다리 · 빛의 사슬
    # (tools/jobvfx/ward.py) + 빛의 방패 (arcane.aegis) · 공용 그림
    "ward": Style("el_ward", "ward",
                  main=ramp([(0.0, "#060A14"), (0.12, "#101C34"), (0.26, "#1E3460"), (0.4, "#304E88"), (0.52, "#4668AE"), (0.64, "#6688CC"),
                             (0.76, "#8EAAE2"), (0.86, "#B8CCF2"), (0.94, "#E2ECFF"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#1A1A1A"), (0.5, "#B8C8E0"), (0.8, "#F0F6FF"), (1.0, "#FFFFFF")]),
                  smoke=ramp([(0.0, "#08080C"), (0.45, "#1A1E28"), (0.8, "#2E3444"), (1.0, "#4A5266")]),
                  ring="ticks", smoke_alpha=0.45, seed=33),
    # 2026-11-16 아가리 (다크 나이트 — 진홍 · 검은 공허: jobs.json darkknight [0.9, 0.12, 0.2] · [0.2, 0.1, 0.14]): 방패의 아가리 · 검은 혀 · 땅의 아가리 ·
    # 불씨 기둥 (tools/jobvfx/maw.py) + 공용 그림 · 연기 (shadow.py 를 아가리 빛으로). 몸은 짙은 공허 (smoke 램프) · 빛은 진홍 · 이빨은 뼈빛 (accent)
    "maw": Style("el_maw", "maw",
                 main=ramp([(0.0, "#100204"), (0.12, "#2A0408"), (0.26, "#520A14"), (0.4, "#84101E"), (0.52, "#B01A2A"), (0.64, "#D8303C"),
                            (0.76, "#F25A5E"), (0.86, "#FF8E8A"), (0.94, "#FFD0C8"), (1.0, "#FFFFFF")]),
                 accent=ramp([(0.0, "#1A1414"), (0.5, "#8C8078"), (0.8, "#E8DED0"), (1.0, "#FFFFFF")]),
                 smoke=ramp([(0.0, "#050203"), (0.45, "#1A080C"), (0.8, "#2E1218"), (1.0, "#4A2428")]),
                 ring="ticks", smoke_alpha=0.45, seed=35),
    # 2026-11-16 뇌광 (성기사 · 썬더로드 — 금빛 · 하늘빛: jobs.json paladin [1, 0.8, 0.26] · [0.5, 0.85, 1]): 벼락 · 번개 고리 · 빛의 창 (storm.py) + 번개 줄 ·
    # 먹구름 · 돌 껍데기 · 번개 문장 · 바퀴 자국 · 번개 망치 · 거인의 손 · 번개 기둥 (tools/jobvfx/thunder.py). 번개 빛은 하늘빛 (accent)
    "thunder": Style("el_thunder", "thunder",
                     main=ramp([(0.0, "#140E02"), (0.12, "#3A2806"), (0.26, "#6E4C0C"), (0.4, "#A67414"), (0.52, "#D89A1E"), (0.64, "#F4BC36"),
                                (0.76, "#FFD460"), (0.86, "#FFE6A0"), (0.94, "#FFF6DC"), (1.0, "#FFFFFF")]),
                     accent=ramp([(0.0, "#06182A"), (0.5, "#3A9AE0"), (0.8, "#9CDCFF"), (1.0, "#F0FAFF")]),
                     smoke=ramp([(0.0, "#0A0C12"), (0.45, "#262C3A"), (0.8, "#46506A"), (1.0, "#6A7690")]),
                     ring="ticks", smoke_alpha=0.45, seed=37),
    # 2026-11-16 폭군 (타이런트 — 보랏빛 · 핏빛: jobs.json tyrant [0.7, 0.32, 1] · [1, 0.3, 0.34]): 뇌광과 같은 그림을 보랏빛 몸 · 핏빛 번개로
    "tyrant": Style("el_tyrant", "tyrant",
                    main=ramp([(0.0, "#0E0418"), (0.12, "#240A3E"), (0.26, "#40146C"), (0.4, "#6020A0"), (0.52, "#8034CC"), (0.64, "#A050F0"),
                               (0.76, "#BC78FF"), (0.86, "#D6A6FF"), (0.94, "#F0DCFF"), (1.0, "#FFFFFF")]),
                    accent=ramp([(0.0, "#2A0408"), (0.5, "#D8303C"), (0.8, "#FF8080"), (1.0, "#FFECEC")]),
                    smoke=ramp([(0.0, "#08040C"), (0.45, "#1E1028"), (0.8, "#382246"), (1.0, "#563A66")]),
                    ring="ticks", smoke_alpha=0.45, seed=39),
    # 2026-11-17 심연 (한정 룬 심연의 낙인 — 도감 메모 "좀 더 어두웠으면 좋겠음"): 폭군과 같은 그림을 짙은 심연빛으로 — 몸은 거의 검은 보라에서 가라앉은 자줏빛까지
    # (흰빛 끝 없음 · 가장 밝은 테도 가라앉은 자줏빛 — CI 44057837261 1판은 고리 테가 옅은 라일락 · 분홍으로 밝았음), 둘째 빛은 검붉은 진홍, 연기는 먹빛 (짙게)
    "abyss": Style("el_abyss", "tyrant",
                   main=ramp([(0.0, "#030106"), (0.12, "#0A0312"), (0.26, "#160624"), (0.4, "#240A38"), (0.52, "#320E4E"), (0.64, "#461466"),
                              (0.76, "#5C1A82"), (0.86, "#6E2492"), (0.94, "#8A38AA"), (1.0, "#A452C0")]),
                   accent=ramp([(0.0, "#140206"), (0.5, "#6A0C20"), (0.8, "#A01830"), (1.0, "#D0304A")]),
                   smoke=ramp([(0.0, "#010002"), (0.45, "#07020C"), (0.8, "#12061C"), (1.0, "#1E0C2C")]),
                   ring="ticks", smoke_alpha=0.65, seed=43),
    # 2026-11-16 사신 라인: 사신 (청록 혼 — jobs.json reaper [0.22, 0.96, 0.86] · [0.1, 0.36, 0.38]) · 소울 리퍼 (하늘빛 · 남빛) · 엑시큐셔너 (금빛 · 자줏빛) —
    # 망자의 비명 · 사신 그림자 · 처형 표식 (tools/jobvfx/reap.py) + 혼불 · 해골 · 망자의 손 · 연기 (soul.py) 를 묶음 빛으로
    "reap": Style("el_reap", "reap",
                  main=ramp([(0.0, "#021210"), (0.12, "#062A26"), (0.26, "#0C4E46"), (0.4, "#127A6C"), (0.52, "#1AA694"), (0.64, "#2ECCB8"),
                             (0.76, "#62E8D6"), (0.86, "#A0F6EA"), (0.94, "#DEFFFA"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#04161A"), (0.5, "#2A9A9E"), (0.8, "#9AF6F0"), (1.0, "#F0FFFE")]),
                  smoke=ramp([(0.0, "#020606"), (0.45, "#0C1C1E"), (0.8, "#183234"), (1.0, "#2A4A4C")]),
                  ring="ticks", smoke_alpha=0.45, seed=41),
    "soulreap": Style("el_soulreap", "reap",
                      main=ramp([(0.0, "#020A16"), (0.12, "#06182E"), (0.26, "#0C2E56"), (0.4, "#144A86"), (0.52, "#1E6CB6"), (0.64, "#3492E0"),
                                 (0.76, "#64B8F6"), (0.86, "#A0D8FF"), (0.94, "#E0F4FF"), (1.0, "#FFFFFF")]),
                      accent=ramp([(0.0, "#04121E"), (0.5, "#3A8EC8"), (0.8, "#A8E2FF"), (1.0, "#F4FCFF")]),
                      smoke=ramp([(0.0, "#020408"), (0.45, "#0A1424"), (0.8, "#16263E"), (1.0, "#283C5A")]),
                      ring="ticks", smoke_alpha=0.45, seed=43),
    "exec": Style("el_exec", "reap",
                  main=ramp([(0.0, "#0E0A02"), (0.12, "#2E2006"), (0.26, "#5A3E0C"), (0.4, "#8A6214"), (0.52, "#BA8A1E"), (0.64, "#E2B03A"),
                             (0.76, "#F8CC68"), (0.86, "#FFE2A0"), (0.94, "#FFF4DC"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#160418"), (0.5, "#8A2A86"), (0.8, "#E07ADC"), (1.0, "#FFE8FE")]),
                  smoke=ramp([(0.0, "#08040A"), (0.45, "#1E0C22"), (0.8, "#36183C"), (1.0, "#523058")]),
                  ring="ticks", smoke_alpha=0.45, seed=45),
    # 2026-11-16 창술사 라인: 창술사 (금빛 · 흰빛 — jobs.json lancer [1, 0.82, 0.3] · [1, 0.98, 0.9]) · 스톰 랜서 (박하빛 · 흰빛) · 드라군 (주홍 · 주황) — 깃털 · 바람 고리 ·
    # 도는 창 · 바람 소용돌이 · 불꽃 덩이 · 용의 혼 (tools/jobvfx/lance.py)
    "lance": Style("el_lance", "lance",
                   main=ramp([(0.0, "#120C02"), (0.12, "#322206"), (0.26, "#5E420C"), (0.4, "#8E6614"), (0.52, "#C08E22"), (0.64, "#E4B23E"),
                              (0.76, "#F8D06E"), (0.86, "#FFE6A8"), (0.94, "#FFF8E4"), (1.0, "#FFFFFF")]),
                   accent=ramp([(0.0, "#1A1A16"), (0.5, "#CEC8B4"), (0.8, "#FFFAEC"), (1.0, "#FFFFFF")]),
                   smoke=ramp([(0.0, "#0C0A06"), (0.45, "#2A2418"), (0.8, "#4A4230"), (1.0, "#6E6450")]),
                   ring="ticks", smoke_alpha=0.45, seed=47),
    "gale": Style("el_gale", "lance",
                  main=ramp([(0.0, "#021410"), (0.12, "#063228"), (0.26, "#0C5C48"), (0.4, "#14886A"), (0.52, "#22B48C"), (0.64, "#40D8AA"),
                             (0.76, "#72F0C6"), (0.86, "#A8FADC"), (0.94, "#E2FFF4"), (1.0, "#FFFFFF")]),
                  accent=ramp([(0.0, "#0A1A16"), (0.5, "#9ED8C8"), (0.8, "#F2FFFA"), (1.0, "#FFFFFF")]),
                  smoke=ramp([(0.0, "#040A08"), (0.45, "#12241E"), (0.8, "#223E34"), (1.0, "#3A5C50")]),
                  ring="ticks", smoke_alpha=0.45, seed=49),
    "dragon": Style("el_dragon", "lance",
                    main=ramp([(0.0, "#140402"), (0.12, "#360A04"), (0.26, "#661406"), (0.4, "#9C200A"), (0.52, "#D0300E"), (0.64, "#F24E16"),
                               (0.76, "#FF7E2E"), (0.86, "#FFB070"), (0.94, "#FFE4C4"), (1.0, "#FFFFFF")]),
                    accent=ramp([(0.0, "#2A1404"), (0.5, "#E08A1A"), (0.8, "#FFD060"), (1.0, "#FFF6D8")]),
                    smoke=ramp([(0.0, "#0C0604"), (0.45, "#261410"), (0.8, "#42261E"), (1.0, "#5E3E34")]),
                    ring="ticks", smoke_alpha=0.45, seed=51),
    # 2026-11-16 푸른 불사조 (피닉스 가드 — 하늘빛 불꽃 · 흰빛: jobs.json phoenix [0.26, 0.62, 1] · [0.9, 0.97, 1]): 불꽃 날개 · 불사조 · 분노의 부채꼴 (tools/jobvfx/phoenix.py)
    "phoenix": Style("el_phoenix", "phoenix",
                     main=ramp([(0.0, "#020A1A"), (0.12, "#06183A"), (0.26, "#0C2E68"), (0.4, "#1648A0"), (0.52, "#2468D0"), (0.64, "#3C8CF0"),
                                (0.76, "#6EB0FF"), (0.86, "#A6D2FF"), (0.94, "#E2F0FF"), (1.0, "#FFFFFF")]),
                     accent=ramp([(0.0, "#061428"), (0.5, "#5AB4F0"), (0.8, "#C8ECFF"), (1.0, "#FFFFFF")]),
                     smoke=ramp([(0.0, "#04060A"), (0.45, "#141C2A"), (0.8, "#263246"), (1.0, "#3E4C64")]),
                     ring="ticks", smoke_alpha=0.45, seed=53),
}


def frames_of(S, name):
    """미리 보기 · 그림판에 쓰는 칸 목록"""
    if name == "slash":
        return [to_img(*slash_frame(S, f)) for f in range(SLASH_FRAMES)]
    if name == "xslash":
        # 2026-11-16 비전 · 혼 묶음의 맞은 자리는 X 베기 대신 별빛 (arcane.starhit) · 혼 터짐 (soul.soulhit) — 같은 자리 (PhantomFx.hit · 2 × 2)
        if S.kind in ("arcane", "holy", "petal"):  # 2026-11-16 성광 · 꽃잎 묶음도 별빛
            from . import arcane
            return arcane.frames_of("starhit", S)
        if S.kind == "soul":
            from . import soul
            return soul.frames_of("soulhit", S)
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
    if name == "crackv":
        return [to_img(*crack_frame(S, f, burn_on=False)) for f in range(CRACK_FRAMES)]
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
    if name == "speed":
        return [to_img(*speed_frame(S, f)) for f in range(SPEED_FRAMES)]
    if name == "cut":
        return [to_img(*cut_frame(S, f)) for f in range(CUT_FRAMES)]
    if name == "cutl":
        return [to_img(*cutl_frame(S, f)) for f in range(CUT_FRAMES)]
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
        # 2026-11-15 한 바퀴 베기 (spin · spinm — JobFx.ring): 없으면 얇은 땅 고리로 바뀌어 예전 칼날 고리 (rfx_jring2 — 도는 초승달) 와 달랐음 (리포스트 · 블레이드 에코)
        return (tiled(p + "slash", fr("slash")) + tiled(p + "slashm", mirrored("slash")) + tiled(p + "xslash", fr("xslash"))
                + [(p + "cut", fr("cut"), True)] + grid_tiles(p + "excal", fr("excal"), 4, 1) + tiled(p + "spin", fr("spin"))
                + tiled(p + "spinm", mirrored("spin")))
    if S.kind in ("arcane", "soul"):
        # 2026-11-16 매지션 라인 (사용자 "다음 직업으로 마법사 작업"): 마법 직업은 베기 · 땅 찍기를 쓰지 않음 — 맞은 자리 (xslash 자리에 별빛 · 혼 터짐) · 섬광 · 터짐 ·
        # 날아가는 작은 그림 + (매지션) 한 바퀴 고리 · 땅 고리 + 직업 대표 그림 (tools/jobvfx/arcane.py · soul.py). 글꼴 무게를 줄이려 쓰지 않는 공용 그림은 뺌
        out = (tiled(p + "xslash", fr("xslash")) + tiled(p + "boom", fr("boom"))
               + [(p + "boom_s", fr("boom_s"), True), (p + "flash", fr("flash"), True), (p + "ember", lambda: ember(S), True), (p + "shard", lambda: shard(S), True)])
        if S.kind == "arcane":
            # 2026-11-16 땅 고리 (ring) 는 뺌 — 칠한 직업의 JobFx.ring 은 예전 길 (jring2 → 칠한 spin) 이라 부르는 곳이 없음 (4.2 MB, 사용자 "용량 확보하자")
            from . import arcane
            return out + tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + arcane.sprites()
        from . import soul
        return out + soul.sprites()
    if S.kind in ("holy", "petal"):
        # 2026-11-16 사제 · 세인트 (성광 — 남은 직업 위르드식): 베기는 쓰지 않음 — 맞은 자리 별빛 · 터짐 · 땅 충격파 · 섬광 · 작은 그림 · 일섬 + (성광) 한 바퀴 고리 ·
        # 빛 고리 (jhalo) · 빛의 방패 (jaegis2) · 띠 (jaura2) · 광선 (jbeam2) · 1인칭 빠른 줄 + 대표 그림 (tools/jobvfx/holy.py — 회복 십자 · 음표 · 성흔 · 종 · 연꽃 ·
        # 큰 꽃 · 나비). 꽃잎 묶음은 세인트가 PETAL 빛으로 부르는 것만 (섬광 · 불씨 · 일섬 · 꽃 · 연꽃 · 나비 · 표식 꽃 · 꽃잎)
        from . import arcane, holy, lance, shadow
        # 2026-11-16 룬 위르드식: 체력 계열 룬 = 꽃잎 묶음 · 세계수 = 성광 묶음 → 공용 그림을 모두 (충격파 · 땅 고리 · X 베기 · 기운 · 방패 …) + 꽃잎 (세계수의 잎)
        extra = (tiled(p + "ring", fr("ring")) + [(p + "shock_s", fr("shock_s"), True), (p + "crack_s", fr("crack"), True), (p + "dust", fr("dust"), True)])
        if S.kind == "petal":
            extra += [(p + "blossom", lambda: shadow.frames_of("blossom", S), True)] + holy.sprites(S, ("lotus", "bloom", "fly"))
        else:
            extra += holy.sprites(S)
        return (extra + [(p + "petal", lambda: shadow.frames_of("petal", S), True)]
                + tiled(p + "xslash", fr("xslash")) + tiled(p + "boom", fr("boom")) + tiled(p + "shock", fr("shock"))
                + [(p + "boom_s", fr("boom_s"), True), (p + "flash", fr("flash"), True), (p + "ember", lambda: ember(S), True), (p + "shard", lambda: shard(S), True),
                   (p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin"))
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "aegis", lambda: [to_img(*arcane.aegis_frame(S, f)) for f in range(arcane.AEGIS_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "beam", lambda: [to_img(*arcane.beam_frame(S, f)) for f in range(arcane.BEAM_FRAMES)], True),
                   # 2026-11-16 CI 43903024474 에 남은 예전 그림: 돌진 줄기 (jdash2 — 홀리 스텝) · 깃털 (jfeather — 스티그마)
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True)]
                + lance.sprites(S, ("feather",)))
    out = (tiled(p + "slash", fr("slash")) + tiled(p + "slashm", mirrored("slash")) + tiled(p + "xslash", fr("xslash")) + tiled(p + "boom", fr("boom"))
           + tiled(p + "shock", fr("shock")) + tiled(p + "ring", fr("ring"))
           + [(p + "boom_s", fr("boom_s"), True), (p + "shock_s", fr("shock_s"), True), (p + "crack_s", fr("crack"), True), (p + "flash", fr("flash"), True),
              (p + "charge", fr("charge"), True), (p + "ember", lambda: ember(S), True), (p + "shard", lambda: shard(S), True),
              (p + "pillar", fr("pillar"), True), (p + "dust", fr("dust"), True)])
    if S.kind == "steel":
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "rift", fr("rift"), True), (p + "cut", fr("cut"), True)]
                + tiled(p + "wings", fr("wings")))
    if S.kind == "ward":
        # 2026-11-16 수호기사 (수호 — 남은 직업 위르드식): 공용 그림 (베기 — 칠한 묶음의 반달 · 날아가는 검기가 씀, 없으면 그림판 없음 오류) + 한 바퀴 고리 · 일섬 · 빛의 방패 (arcane.aegis) · 깨지는 방패 (arcane.shatter) ·
        # 띠 · 돌진 줄기 · 빠른 줄 + 대표 그림 (tools/jobvfx/ward.py — 도는 방패 · 빛의 다리 · 빛의 사슬)
        from . import arcane, holy, ward
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + [(p + "aegis", lambda: [to_img(*arcane.aegis_frame(S, f)) for f in range(arcane.AEGIS_FRAMES)], True),
                   (p + "shatter", lambda: [to_img(*arcane.shatter_frame(S, f)) for f in range(arcane.SHATTER_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True)]
                + ward.sprites(S) + holy.sprites(S, ("heal",)))  # 회복 십자 (수호기사 특성 — JobTraits4)
    if S.kind == "phoenix":
        # 2026-11-16 피닉스 가드 (푸른 불사조 — 남은 직업 위르드식): 공용 그림 + 한 바퀴 고리 · 일섬 · 빠른 줄 · 돌진 줄기 · 빛 고리 + 깃털 (lance.py) · 불씨 기둥 (maw.py) +
        # 불꽃 날개 · 불사조 · 분노의 부채꼴 (phoenix.py)
        from . import arcane, lance, maw, phoenix
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + [(p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True)]
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + lance.sprites(S, ("feather",)) + maw.sprites(S, ("cinder",)) + phoenix.sprites(S))
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 태세의 띠 (jaura2 — 이그니션) · 빛의 방패 (jaegis2) · 깨지는 방패 (jshatter2)
        out += [(p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                (p + "aegis", lambda: [to_img(*arcane.aegis_frame(S, f)) for f in range(arcane.AEGIS_FRAMES)], True),
                (p + "shatter", lambda: [to_img(*arcane.shatter_frame(S, f)) for f in range(arcane.SHATTER_FRAMES)], True)]
    if S.kind == "lance":
        # 2026-11-16 창술사 · 스톰 랜서 · 드라군 (창술사 라인 — 남은 직업 위르드식): 공용 그림 + 한 바퀴 고리 · 일섬 · 빠른 줄 · 돌진 줄기 · 띠 + 찌르기 번쩍 (shadow.py) ·
        # 빛의 창 (storm.py) · 회오리 · 도는 날 · 불꽃 꼬리 (beast.py) · 서 있는 불꽃 (forge.py) + 깃털 · 바람 고리 · 도는 창 · 바람 소용돌이 · 불꽃 덩이 · 용의 혼 (lance.py)
        from . import arcane, beast, forge, lance, shadow, storm
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + [(p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "stab", lambda: shadow.frames_of("stab", S), True)]
                + storm.sprites(S, ("spear",)) + [(p + k, (lambda k=k: beast.frames_of(k, S)), True) for k in ("twister", "whirl", "comet")]
                + lance.sprites(S))
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 분신 (rfx_shade — 창술사 궁의 잔상)
        from . import clone
        out += [(p + "shade", lambda: clone.frames(S), True)]
        if S.lib == "el_dragon":
            out += [(p + "flame", lambda: [to_img(*forge.flame_frame(f)) for f in range(forge.FLAME_FRAMES)], True)]
    if S.kind == "reap":
        # 2026-11-16 사신 · 소울 리퍼 · 엑시큐셔너 (사신 라인 — 남은 직업 위르드식): 공용 그림 + 한 바퀴 고리 · 일섬 · 빠른 줄 · 빛 고리 · 회오리 날 + 혼불 · 해골 ·
        # 망자의 손 (soul.py) · 연기 (shadow.py — soul 연기는 녹색 안개로 정해짐) + 망자의 비명 · 사신 그림자 · 처형 표식 (reap.py) + 사슬 (ward.py) · 조명 (circus.py)
        from . import arcane, beast, circus, reap, shadow, soul, ward
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "whirl", lambda: beast.frames_of("whirl", S), True), (p + "smoke", lambda: shadow.frames_of("smoke", S), True)]
                + [(p + k, (lambda k=k: soul.frames_of(k, S)), True) for k in ("soul", "skull", "hand")]
                + reap.sprites(S) + ward.sprites(S, ("chain",)) + [(p + "spot", lambda: circus.frames_of("spot", S), True)])
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 태세의 띠 (jaura2 — 사신 현신 · 소울 사이드 · 조종) · 돌진 줄기 (jdash2 — 레이스 · 트레스패스) · 광선 (jbeam2 —
        # 소울 하베스트 988장) · 분신 (rfx_shade — 레이스 · 크레센트)
        from . import clone
        out += [(p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True),
                (p + "beam", lambda: [to_img(*arcane.beam_frame(S, f)) for f in range(arcane.BEAM_FRAMES)], True),
                (p + "shade", lambda: clone.frames(S), True)]
    if S.kind in ("thunder", "tyrant"):
        # 2026-11-16 성기사 · 썬더로드 · 타이런트 (뇌광 · 폭군 — 남은 직업 위르드식): 공용 그림 + 한 바퀴 고리 · 일섬 · 빠른 줄 · 돌진 줄기 · 빛의 방패 · 띠 · 연기 +
        # 벼락 · 번개 고리 · 빛의 창 (storm.py) + 번개 줄 · 먹구름 · 돌 껍데기 · 번개 문장 · 바퀴 자국 · 번개 망치 · 거인의 손 · 번개 기둥 (thunder.py)
        from . import arcane, shadow, storm, thunder, ward
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True)]
                + [(p + "aegis", lambda: [to_img(*arcane.aegis_frame(S, f)) for f in range(arcane.AEGIS_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True),
                   (p + "smoke", lambda: shadow.frames_of("smoke", S), True)]
                + storm.sprites(S) + thunder.sprites(S) + ward.sprites(S, ("chain",)))  # 썬더로드 번개 사슬 (JobFx.chain)
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 혼 (jsoul2 — 타이런트 강탈 · 궁 108장) · 분신 (rfx_shade — 섀클) · 빛 고리 (jhalo)
        from . import clone, soul
        out += ([(p + "soul", lambda: soul.frames_of("soul", S), True), (p + "shade", lambda: clone.frames(S), True)]
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)]))
    if S.kind == "maw":
        # 2026-11-16 다크 나이트 (아가리 — 남은 직업 위르드식): 공용 그림 + 한 바퀴 고리 · 일섬 · 연기 (shadow.py 를 아가리 빛으로) · 빠른 줄 · 땅 가르기 +
        # 대표 그림 (tools/jobvfx/maw.py — 방패의 아가리 · 검은 혀 · 땅의 아가리 · 불씨 기둥)
        from . import maw, shadow
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "speed", fr("speed"), True),
                                                                                         (p + "rift", fr("rift"), True)]
                + [(p + "smoke", lambda: shadow.frames_of("smoke", S), True)] + maw.sprites(S))
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 태세의 띠 (jaura2 — 언홀리 프렌지) · 빛 고리 (jhalo — 궁)
        from . import arcane
        out += ([(p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True)]
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)]))
    if S.kind == "hex":
        # 2026-11-16 워록 (혈주 — 남은 직업 위르드식): 공용 그림 (피처럼 튀는 터짐 · 섬광 · 핏물 기둥) + 한 바퀴 고리 · 일섬 · 연기 (shadow.py 를 혈주 빛으로) ·
        # 띠 · 광선 + 대표 그림 (tools/jobvfx/hex.py — 핏빛 창 · 웅덩이 · 핏물 기둥 · 심장 · 핏빛 불기둥)
        from . import arcane, hex as hexart, shadow
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True), (p + "rift", fr("rift"), True)]
                + [(p + "smoke", lambda: shadow.frames_of("smoke", S), True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "beam", lambda: [to_img(*arcane.beam_frame(S, f)) for f in range(arcane.BEAM_FRAMES)], True)]
                + hexart.sprites(S))
        # 2026-11-16 CI 43903024474 에 남은 예전 그림: 빨아들이는 혼 (jsoul2 — 블러드 랜스 · 노바 · 궁 300장 넘게) · 빛 고리 (jhalo)
        from . import soul
        out += ([(p + "soul", lambda: soul.frames_of("soul", S), True)]
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)]))
    if S.kind == "blood":
        # 2026-11-15 한 바퀴 베기 (spin · spinm — JobFx.ring): 예전 칼날 고리처럼 도는 핏빛 초승달 (크림슨 다이브 · 블러드 클레이브)
        out += tiled(p + "moon", fr("moon")) + [(p + "rift", fr("rift"), True)] + tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin"))
    if S.kind == "shadow":
        # 한 바퀴 베기 (JobFx.ring) · 일섬 (JobFx.line) 은 공용 그림을 그림자 빛으로, 암살자 대표 그림은 tools/jobvfx/shadow.py
        from . import shadow
        out += tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True)] + shadow.sprites()
    if S.kind == "illusion":
        # 2026-11-16 트릭스터 (환영 — 남은 직업 위르드식 첫째): 공용 그림을 환영 빛으로 + 한 바퀴 베기 · 일섬 + 대표 그림 (tools/jobvfx/mirror.py — 거울 · 깨지는
        # 거울 · 손거울 · 띠 고리)
        from . import mirror
        out += tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True)] + mirror.sprites()
        # 2026-11-16 CI 43878674673 에 남은 예전 그림 (빛을 정한 거울 속 X 자국 jcross2 · 왈츠의 띠 jaura2 · 고리 jhalo · 1인칭 빠른 줄 jspeed2): 같은 틀을 환영 빛으로
        from . import arcane, shadow
        out += (tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "cross", lambda: shadow.frames_of("cross", S), True), (p + "speed", fr("speed"), True)])
        # 2026-11-16 분신 (예전 rfx_shade — 같은 틀 · 거울 유리 몸): tools/jobvfx/clone.py
        from . import clone
        out += [(p + "shade", lambda: clone.frames(S), True)]
    if S.kind == "circus":
        # 2026-11-16 피에로 (서커스): 공용 그림을 서커스 빛으로 + 한 바퀴 베기 · 일섬 + 대표 그림 (tools/jobvfx/circus.py — 색종이 · 풍선 동물 · 불꽃놀이 · 조명)
        from . import circus, beast
        out += tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True)] + circus.sprites()
        # 2026-11-16 불꽃놀이 꼬리 (피에로 궁극기의 jcomet2 160 — 예전 그림이 남았었음): 불꽃 꼬리를 서커스 빛으로
        out += [(p + "comet", lambda: beast.frames_of("comet", S), True)]
        # 2026-11-16 CI 43878674673 에 남은 예전 그림 (상자의 X 자국 jcross2 · 도는 비수 jdagger2 · 연기 jsmoke2 · 스프링 고리 jhalo): 같은 틀을 서커스 빛으로.
        # 연기는 피에로가 흰 · 금빛으로 부르는 펑 (소환물이 나타나고 사라짐) — 그림자 연기 (짙은 몸) 대신 크림 흰 몸 · 금빛 테
        from . import arcane, shadow
        poof = Style(S.lib, S.kind, main=S.accent, accent=S.accent, smoke=ramp([(0.0, "#8C7A66"), (0.3, "#C9B9A0"), (0.6, "#EFE6D4"), (1.0, "#FFFFFF")]),
                     seed=S.seed)
        out += (tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "cross", lambda: shadow.frames_of("cross", S), True), (p + "dagger", lambda: shadow.frames_of("dagger", S), True),
                   (p + "smoke", lambda: shadow.frames_of("smoke", poof), True)])
    if S.kind == "frost":
        # 2026-11-16 프로스트 레이더: 공용 그림 + 한 바퀴 베기 + 서리 고리 (jhalo — 2 × 2) · 얼음 결정 (jfrost — 맞은 자리) · 서리 띠 (jaura2) · 돌진 줄기 (jdash2)
        from . import arcane
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin"))
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "frost", lambda: [to_img(*arcane.frost_frame(f)) for f in range(arcane.FROST_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True)])
    if S.kind == "rune":
        # 2026-11-16 마검사 · 아케인 블레이드 (룬): 공용 그림 + 한 바퀴 베기 + 룬 그림 (tools/jobvfx/rune.py — 룬 띠 · 새긴 글자 · 아홉 글자 · 빛기둥 · 찢어짐) +
        # 번개 (tools/jobvfx/storm.py) + 광선 · 서리 띠 (arcane) · 회오리 · 도는 날 (beast)
        from . import arcane, beast, rune, storm
        # + 땅 가르기 (WarlordSkills.rift — 예전 fissure 대신) · 1인칭 빠른 줄 (jspeed2)
        out += [(p + "rift", fr("rift"), True), (p + "speed", fr("speed"), True)]
        # + 순간 이동 잔상 분신 (예전 rfx_shade — tools/jobvfx/clone.py)
        from . import clone
        out += [(p + "shade", lambda: clone.frames(S), True)]
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True)] + rune.sprites(S) + storm.sprites(S)
                + [(p + "beam", lambda: [to_img(*arcane.beam_frame(S, f)) for f in range(arcane.BEAM_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True),
                   (p + "whirl", lambda: beast.frames_of("whirl", S), True), (p + "twister", lambda: beast.frames_of("twister", S), True)])
    if S.kind == "element":
        # 2026-11-16 엘리멘탈 블레이드: 공용 그림 + 한 바퀴 베기 + 번개 · 빛의 창 + 불꽃 혀 (forge — 예전 rpg:fire_wall 자리) + 회오리 · 도는 날 + 서리 띠 · 고리
        from . import arcane, beast, forge, storm
        out += (tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + [(p + "cut", fr("cut"), True)] + storm.sprites(S)
                + tiled(p + "halo", lambda: [to_img(*arcane.halo_frame(S, f)) for f in range(arcane.HALO_FRAMES)])
                + [(p + "flame", lambda: [to_img(*forge.flame_frame(f)) for f in range(forge.FLAME_FRAMES)], True),
                   (p + "aura", lambda: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True),
                   (p + "dash", lambda: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True),
                   (p + "whirl", lambda: beast.frames_of("whirl", S), True), (p + "twister", lambda: beast.frames_of("twister", S), True)])
    if S.kind in ("rage", "bear"):
        # 2026-11-16 전사 라인 (남은 직업 위르드식): 공용 그림 + 한 바퀴 베기 (칠한 직업의 JobFx.ring · ringFacing · eyeRing 은 jring2 → spin) + 대표 그림
        # (tools/jobvfx/beast.py — 늑대 · 회오리 · 도는 도끼날 · 불꽃 꼬리 · 방패 깨짐 · 갈라지는 땅 · 곰의 혼 · 앞발 · 몸의 불꽃 · 돌진 줄기)
        from . import beast
        out += tiled(p + "spin", fr("spin")) + tiled(p + "spinm", mirrored("spin")) + beast.sprites(S)
        # 2026-11-16 CI 43878674673 에 남은 예전 그림: 전사 1인칭 빠른 줄 (jspeed2) · 워로드 땅 가르기 (WarlordSkills.rift — 예전 fissure)
        out += [(p + "speed", fr("speed"), True)] if S.kind == "rage" else [(p + "rift", fr("rift"), True)]
        if S.kind == "rage":
            # 2026-11-16 돌진 잔상 분신 (예전 rfx_shade — tools/jobvfx/clone.py)
            from . import clone
            out += [(p + "shade", lambda: clone.frames(S), True)]
    return out


# 2026-11-17 긴 일섬 (cutl) 을 갖는 묶음: 룬 계열 · 원소 묶음 (PhantomFx.runeLib · elementLib — 룬이 부른 예전 rpg:light_cut 을 그 묶음의 일섬으로)
CUTL_KEYS = ("rage", "gale", "arcane", "blood", "shadow", "reap", "petal", "maw", "bear", "holy", "tyrant", "abyss", "frost", "element")
# 2026-11-17 방패 깨짐 (beast.py shieldbreak — 분노 묶음에만 있었음) 을 더 갖는 묶음: 받는 피해 증가 표식을 거는 룬 계열 (도끼 = 피 · 암살 = 그림자 · 민첩 = 질풍) ·
# 원소 반응 (원소 · 서리 · 곰) — RuneMarks 의 머리 위 금 간 방패 (예전 rfx_jbreak)
SHIELDBREAK_KEYS = ("blood", "shadow", "gale", "element", "frost", "bear")
# 2026-11-17 솟는 가시 (beast.py spike — 곰 묶음의 돌창) 를 더 갖는 묶음: 얼음 가시 (예전 rpg:ice_spike — 서리 · 영혼 수확) · 심연 룬의 검은 가시 손 (rfx_thorn — 폭군)
SPIKE_KEYS = ("frost", "soulreap", "tyrant", "abyss", "arcane")
# 2026-11-17 뭉게 연기 (shadow.py smoke — 그 묶음의 연기 램프) 를 더 갖는 묶음: 보스의 숨결 (예전 rpg:breath — 이미르 서리 숨결) · 정령 룬의 부름 연기
# (rfx_jsmoke2 흰빛 — 질풍 묶음, CI 43987516768 정령 초월 20: 질풍의 연기 램프는 짙은 녹색이라 옅은 박하빛 램프로 · 도트 색은 그림 자체의 색 — vfx_sprites PIXEL_LIBS) ·
# 마력 룬 메테오의 불타는 연기 꼬리 · 버섯구름 (rfx_cloud 잿빛 — 원소 묶음의 흙빛 연기 램프 · 속빛은 불빛 FIRE_ACCENT, CI 43989923127 11)
SMOKE_KEYS = ("frost", "gale", "element")
SMOKE_RAMPS = {"gale": ramp([(0.0, "#24423A"), (0.45, "#5A8A7C"), (0.8, "#9CC8BA"), (1.0, "#D4F0E6")])}
# 2026-11-17 얼음 창 (arcane.py icelance — 얼음 빛 그대로) 을 더 갖는 묶음: 만년빙하 · 원소 얼음 룬의 날아가는 고드름 (예전 rfx_icicle)
ICELANCE_KEYS = ("frost",)
# 2026-11-17 땅 금 · 작은 충격파 (crack_s · shock_s — 한 장 256) 를 더 갖는 묶음: 혼 (네크로맨서의 녹색 땅 금 · 땅 고리 — CI 43979933145 남은 예전 그림 40)
GROUND_KEYS = ("soul", "arcane")
# 2026-11-17 마력 룬 (비전 묶음 — 마력 계열 빛은 얼음 하늘빛) 의 얼음 가시 · 땅 금 · 충격파 (CI 43989923127 마력 영웅 블리자드 47 — 비전 묶음에 가시 · 충격파가 없었음):
# 비전 둘째 빛 (금빛) 대신 서리의 둘째 빛 (얼음빛) 으로 — 가시 몸 · 고리 테가 둘째 빛
ICE_ACCENT_KEYS = ("arcane",)
# 2026-11-17 휘두른 반달 (slash · slashm 2 × 2 — 마법 묶음은 베기를 빼 두었음) 을 더 갖는 묶음: 혼 (네크로맨서 망자 선원의 도끼 찍기 — SkillFx.arc 녹색 반달,
# CI 43989923127 남은 예전 판 18)
SLASH_KEYS = ("soul",)
# 2026-11-17 용암 웅덩이 · 불기둥 (hex.py lava · pyre — 불빛을 뜻으로 받는 원소 묶음, PhantomFx.semanticLib): 원소 반응 용암 (ElementFx LAVA — 예전 rfx_jpool) ·
# 정령왕 불 원소의 불기둥 (예전 rfx_fountain). 원소 묶음의 둘째 빛 (accent) 은 얼음 푸른빛이라 이 둘은 불의 속빛 (금빛) 으로
FIRE_KEYS = ("element",)
# 2026-11-17 몸의 기운 (arcane.py aura — 위로 타오르는 띠, RUNE_PAINT aura) 을 더 갖는 묶음: 피 (도끼 룬 분노 버프의 몸 기운 — CI 43995311742 도끼 일반 48)
AURA_KEYS = ("blood",)
# 2026-11-17 진흙 웅덩이 (hex.py pool 을 곰 묶음의 흙빛으로 — 원소 반응 진흙 ElementFx MUD 의 rfx_jpool, CI 43989923127 정령 초월 6)
MUD_KEYS = ("bear",)
# 2026-11-17 룬 2판 남은 예전 그림 (CI 43997410683 18): 땅에 누워 도는 바람 소용돌이 (lance.py swirl) — 힘 희귀 흙먼지 소용돌이 (예전 rpg:whirlpool · 분노 묶음)
SWIRL_KEYS = ("rage",)
# 빛의 사슬 (ward.py chain — 고리 넷 · 반짝임이 흐름): 활 희귀 속박 화살의 깃 → 몸 사슬 (예전 rfx_chain · 질풍 묶음)
CHAIN_KEYS = ("gale",)
# 돌진 줄기 (arcane.py dash): 날아가는 빛 (RuneKit.flyStreak) — 암살 희귀 독 표창 (독빛 = 혼) · 그림자 빛 줄기 (그림자)
DASH_KEYS = ("soul", "shadow")
# 분신 (clone.py — 예전 rfx_shade 의 몸 · 테를 묶음 빛으로): 암살 영웅 그림자 분신 (그림자 묶음)
SHADE_KEYS = ("shadow",)
# 2026-11-17 하늘 금 (crackv — 땅 금 crack_s 에서 그을린 자국을 뺀 빛나는 금): 세운 금 (RuneKit.skyPlate "crack") 을 칠하는 묶음 — 힘 초월 천붕 (분노) · 민첩 (질풍) ·
# 마력 초월 메테오 (붉은 빛 = 원소) · 암살 초월 달 (그림자)
CRACKV_KEYS = ("rage", "gale", "element", "shadow")
FIRE_ACCENT = ramp([(0.0, "#2A1404"), (0.5, "#E08A1A"), (0.8, "#FFD060"), (1.0, "#FFF6D8")])


def sprites():
    out = []
    for key in ("steel", "blood", "gold", "shadow", "arcane", "soul", "illusion", "circus", "rage", "bear", "frost", "rune", "element", "holy", "petal", "hex", "ward", "maw", "thunder", "tyrant", "abyss", "reap", "soulreap", "exec", "lance", "gale", "dragon", "phoenix"):
        S = STYLES[key]
        out += sets_for(S)
        if key in CUTL_KEYS:
            out.append((S.lib + "_cutl", lambda S=S: frames_of(S, "cutl"), True))
        if key in SHIELDBREAK_KEYS:
            from . import beast
            out.append((S.lib + "_shieldbreak", lambda S=S: beast.frames_of("shieldbreak", S), True))
        G = Style(S.lib, S.kind, S.main, STYLES["frost"].accent, S.smoke, S.ring, S.smoke_alpha, S.seed) if key in ICE_ACCENT_KEYS else S
        if key in SPIKE_KEYS:
            from . import beast
            out.append((S.lib + "_spike", lambda G=G: beast.frames_of("spike", G), True))
        if key in SMOKE_KEYS:
            from . import shadow
            M = Style(S.lib, S.kind, S.main, FIRE_ACCENT if key in FIRE_KEYS else S.accent, SMOKE_RAMPS.get(key, S.smoke), S.ring, S.smoke_alpha, S.seed)
            out.append((S.lib + "_smoke", lambda M=M: shadow.frames_of("smoke", M), True))
        if key in ICELANCE_KEYS:
            from . import arcane
            out.append((S.lib + "_icelance", lambda S=S: arcane.frames_of("icelance", S), True))
        if key in GROUND_KEYS:
            out.append((S.lib + "_crack_s", lambda G=G: frames_of(G, "crack"), True))
            out.append((S.lib + "_shock_s", lambda G=G: frames_of(G, "shock_s"), True))
        if key in SLASH_KEYS:
            sl_cache = {}

            def sl(S=S, sl_cache=sl_cache):
                if "f" not in sl_cache:
                    sl_cache["f"] = frames_of(S, "slash")
                return sl_cache["f"]
            out += tiled(S.lib + "_slash", sl) + tiled(S.lib + "_slashm", lambda sl=sl: [im.transpose(Image.FLIP_LEFT_RIGHT) for im in sl()])
        if key in AURA_KEYS:
            from . import arcane
            out.append((S.lib + "_aura", lambda S=S: [to_img(*arcane.aura_frame(S, f)) for f in range(arcane.AURA_FRAMES)], True))
        if key in MUD_KEYS:
            from . import hex as hexart
            out.append((S.lib + "_pool", lambda S=S: hexart.frames_of("pool", S), True))
        if key in FIRE_KEYS:
            from . import arcane
            from . import hex as hexart
            F = Style(S.lib, S.kind, S.main, FIRE_ACCENT, S.smoke, S.ring, S.smoke_alpha, S.seed)
            out.append((S.lib + "_pool", lambda F=F: hexart.frames_of("lava", F), True))
            out.append((S.lib + "_pyre", lambda F=F: hexart.frames_of("pyre", F), True))
            # 2026-11-17 마력 룬 불덩이 (rfx_fireball — 미리 칠한 둥근 불덩이 · 빛 없이 부름) · 메테오 불꼬리 후광 (rfx_meteor): 불꽃 꼬리 불덩이 (arcane.py fireball —
            # 불빛 그대로 · 불꽃 혀 · 불티). 메테오 꼬리 (3.2칸 높이) 까지 같은 그림 — 1판의 comet (beast.py 고른 띠) 은 큰 크기에서 옅은 흙빛 관이었음
            out.append((S.lib + "_fireball", lambda: [to_img(*arcane.fireball_frame(f)) for f in range(arcane.SPELL_FRAMES)], True))
        if key in SWIRL_KEYS:
            from . import lance
            out.append((S.lib + "_swirl", lambda S=S: lance.frames_of("swirl", S), True))
        if key in CHAIN_KEYS:
            from . import ward
            out.append((S.lib + "_chain", lambda S=S: ward.frames_of("chain", S), True))
        if key in DASH_KEYS:
            from . import arcane
            out.append((S.lib + "_dash", lambda S=S: [to_img(*arcane.dash_frame(S, f)) for f in range(arcane.DASH_FRAMES)], True))
        if key in SHADE_KEYS:
            from . import clone
            out.append((S.lib + "_shade", lambda S=S: clone.frames(S), True))
        if key in CRACKV_KEYS:
            out.append((S.lib + "_crackv", lambda S=S: frames_of(S, "crackv"), True))
    return out


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        S = STYLES[sys.argv[sys.argv.index("--style") + 1]]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else ["slash", "xslash", "boom", "flash", "small", "pillar", "dust",
                                                                                               "shock", "ring", "charge", "crack"]
        preview({k: frames_of(S, k) for k in names}, out)
