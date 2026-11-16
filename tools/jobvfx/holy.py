"""2026-11-16 성광 (사제 · 세인트 — 남은 직업 위르드식, 사용자 "저거 고치고 ... 자동으로 다음 작업으로"): 직업 묶음 el_holy 의 대표 그림.

예전 그림 (rfx_jheal2 · jnote2 · jstigma2 · jbell2 · jlotus · jbloom · jfly — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 가운데 ·
반지름 몫) — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT). 빛 = 크림 금빛 (사제 · 세인트 첫째 빛) · 둘째 빛 = 회복의 연둣빛.

- heal (jheal2 · 6칸): 회복 십자 — 0 반짝 → 1 피어남 → 2 ~ 3 활짝 (퍼지는 고리 · 반짝임) → 4 ~ 5 떠오르며 옅어짐
- note (jnote2 · 4장): 음표 넷 (4분 · 8분 · 이은 8분 둘 · 이은 16분 둘) — 금빛 몸 · 흰 윤기 · 연둣빛 반짝임
- stigma (jstigma2 · 4칸 되풀이): 빛나는 라틴 십자가 · 도는 빛살 · 숨 쉬는 빛 무리
- bell (jbell2 · 8칸): 장식 종 — 왼쪽 → 가운데 (2 = 가만히) → 오른쪽 → 가운데로 흔들림 · 끝에서 아래로 퍼지는 소리 고리
- lotus (jlotus · 8칸 — 땅에 누움): 빛의 연꽃 — 0 씨앗 빛 · 1 ~ 2 꽃잎 두 겹이 열림 → 3 ~ 5 활짝 (꽃잎 끝 반짝임 · 속 맥동) → 6 꽃잎이 바깥으로 떠오름 · 7 흐려짐
- bloom (jbloom · 8칸): 큰 꽃 — 0 꽃봉오리 (빛나는 틈) · 1 부풂 → 2 ~ 3 열림 → 4 ~ 5 활짝 (빛살 · 꽃가루) → 6 꽃잎이 흩날림 · 7 흐려짐
- fly (jfly · 8칸): 빛나는 나비 — 0 ~ 5 날갯짓 → 6 반짝이로 흩어짐 · 7 흐려짐

  cd tools && python3 -m jobvfx.holy --preview 폴더 [--style holy] [--only heal,bell]
"""
import math
import sys

import numpy as np

from .base import WHITE, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper
from .element import star4, wet

HEAL_FRAMES = 6
NOTE_FRAMES = 4
STIGMA_FRAMES = 4
BELL_FRAMES = 8
LOTUS_FRAMES = 8
BLOOM_FRAMES = 8
FLY_FRAMES = 8


def rbox(x, y, hw, hh, r, aa):
    """둥근 모서리 사각 (반 너비 hw · 반 높이 hh · 모서리 반지름 r)"""
    qx = np.abs(x) - (hw - r)
    qy = np.abs(y) - (hh - r)
    d = np.hypot(np.clip(qx, 0, None), np.clip(qy, 0, None)) + np.minimum(np.maximum(qx, qy), 0) - r
    return 1 - sstep(-aa, aa, d)


def lens(x, y, cx, cy, ang, L, W, aa, base=0.0):
    """끝이 뾰족한 꽃잎 (밑동 (cx, cy) 에서 ang 쪽으로 길이 L · 가장 넓은 반폭 W — 밑동 쪽 반폭 base)"""
    ca, sa = math.cos(ang), math.sin(ang)
    u = (x - cx) * ca + (y - cy) * sa
    v = -(x - cx) * sa + (y - cy) * ca
    t = np.clip(u / max(L, 1e-6), 0, 1)
    w = (base + (W - base) * np.sin(np.pi * t) ** 0.85) * (1 - 0.15 * t)
    inside = (u > -aa) & (u < L + aa)
    return (1 - sstep(w - aa, w + aa, np.abs(v))) * inside, t, v / np.maximum(w, 1e-4)


# ------------------------------------------------------------------ 회복 십자 (jheal2 — 카메라를 봄 · 반 크기 = 칸 반)

def heal_frame(S, f, n=256):
    x, y = grid(n)
    aa = 2.0 / n
    grow = [0.25, 0.7, 1.0, 1.0, 1.0, 0.95][f]
    lift = [0.0, 0.0, 0.0, 0.03, 0.08, 0.14][f]
    k = [0.9, 1.0, 1.0, 0.95, 0.75, 0.4][f]
    yy = y - lift
    L = 0.62 * grow
    W = 0.17 * grow
    cross = np.maximum(rbox(x, yy, W, L, W * 0.55, aa), rbox(x, yy, L, W, W * 0.55, aa))
    rim = np.clip(np.maximum(rbox(x, yy, W + 0.035, L + 0.035, W * 0.6, aa), rbox(x, yy, L + 0.035, W + 0.035, W * 0.6, aa)) - cross, 0, 1)
    core = np.maximum(rbox(x, yy, W * 0.32, L * 0.82, W * 0.3, aa), rbox(x, yy, L * 0.82, W * 0.32, W * 0.3, aa))
    lit = wet(cross, n)
    shade = np.clip(0.55 + 0.25 * (yy / max(L, 1e-3)) + 0.3 * lit, 0, 1)
    r = np.hypot(x, yy)
    layers = [(S.accent(0.45), np.exp(-(r / (0.55 * grow + 0.05)) ** 2) * 0.35 * k), (S.main(0.78), rim * k), (S.accent(np.clip(0.45 + 0.4 * shade, 0, 1)), cross * k),
              (S.accent(0.95), core * 0.8 * k), (WHITE, core * lit * 0.6 * k)]
    if f == 0:
        layers.append((WHITE, star4(x, y, 0, 0, 0.42, 0.05, 0.0, aa)))
        layers.append((S.main(0.9), star4(x, y, 0, 0, 0.55, 0.06, 0.0, aa) * 0.6))
    if f in (2, 3):
        q = (f - 1.5)
        R = 0.5 + 0.28 * q
        layers.append((S.main(0.88), np.exp(-((r - R) / 0.03) ** 2) * (0.8 - 0.3 * q)))
        rng = np.random.default_rng(1900 + f)
        for i in range(5):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.55, 0.85)
            layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a) + lift, 0.09, 0.015, a, aa)))
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.7, sigma=0.03, gain=0.5)


# ------------------------------------------------------------------ 음표 (jnote2 — 네 장 · 높이 약 반 크기 × 2)

def note_frame(S, kind, n=128):
    x, y = grid(n)
    aa = 2.5 / n

    def head(cx, cy):
        ca, sa = math.cos(-0.35), math.sin(-0.35)
        u = (x - cx) * ca + (y - cy) * sa
        v = -(x - cx) * sa + (y - cy) * ca
        return 1 - sstep(1 - aa * 6, 1 + aa * 6, np.hypot(u / 0.2, v / 0.135))

    def stem(cx, y0, y1, w=0.045):
        return (1 - sstep(w - aa, w + aa, np.abs(x - cx))) * (y > y0) * (y < y1)

    def beam(x0, y0, x1, y1, t=0.1):
        s = (y1 - y0) / (x1 - x0)
        yc = y0 + (x - x0) * s
        return (1 - sstep(t / 2 - aa, t / 2 + aa, np.abs(y - yc))) * (x > x0 - 0.03) * (x < x1 + 0.03)
    if kind == 0:
        body = np.maximum(head(-0.12, -0.48), stem(0.04, -0.48, 0.62))
    elif kind == 1:
        flag = taper(x, y, [(0.06, 0.62), (0.2, 0.42), (0.34, 0.24), (0.28, 0.02)], 0.07, 0.025, aa)
        body = np.maximum.reduce([head(-0.12, -0.48), stem(0.04, -0.48, 0.62), flag])
    elif kind == 2:
        body = np.maximum.reduce([head(-0.46, -0.5), head(0.28, -0.36), stem(-0.3, -0.5, 0.5), stem(0.44, -0.36, 0.64), beam(-0.3, 0.5, 0.44, 0.64, 0.12)])
    else:
        body = np.maximum.reduce([head(-0.46, -0.5), head(0.28, -0.36), stem(-0.3, -0.5, 0.5), stem(0.44, -0.36, 0.64), beam(-0.3, 0.5, 0.44, 0.64, 0.1),
                                  beam(-0.3, 0.3, 0.44, 0.44, 0.1)])
    from scipy import ndimage
    rim = np.clip(ndimage.maximum_filter(body, size=5) - body, 0, 1)
    lit = wet(body, n)
    layers = [(S.main(0.4), rim * 0.9), (S.main(0.8), body), (WHITE, body * lit * 0.8),
              (S.accent(0.9), star4(x, y, 0.55, 0.62, 0.16, 0.025, 0.3, aa) * (kind % 2 == 0)),
              (S.accent(0.9), star4(x, y, -0.55, 0.2, 0.13, 0.02, 0.0, aa) * (kind % 2 == 1))]
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.35)


# ------------------------------------------------------------------ 성흔 (jstigma2 — 되풀이 · 반 크기 = 칸 반)

def stigma_frame(S, f, n=256):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    pulse = [1.0, 0.85, 0.7, 0.85][f]
    rays = np.zeros_like(x)
    for i in range(12):
        a = i * math.pi / 6 + f * math.radians(7.5)
        L = (0.92 if i % 2 == 0 else 0.7) * (0.9 + 0.1 * pulse)
        from .base import spike
        rays = np.maximum(rays, spike(x, y, a, L, 0.035 if i % 2 == 0 else 0.022, 1.4, aa))
    cross = np.maximum(rbox(x, y + 0.04, 0.075, 0.6, 0.03, aa), rbox(x, y - 0.24, 0.38, 0.075, 0.03, aa))
    rim = np.clip(np.maximum(rbox(x, y + 0.04, 0.115, 0.64, 0.05, aa), rbox(x, y - 0.24, 0.42, 0.115, 0.05, aa)) - cross, 0, 1)
    core = np.maximum(rbox(x, y + 0.04, 0.025, 0.52, 0.02, aa), rbox(x, y - 0.24, 0.3, 0.025, 0.02, aa))
    halo = np.exp(-(r / (0.42 + 0.06 * pulse)) ** 2) * (0.45 + 0.25 * pulse)
    ring = np.exp(-((r - 0.36) / 0.02) ** 2) * 0.6 * pulse
    layers = [(S.main(0.6), halo), (S.main(0.85), rays * (0.55 + 0.35 * pulse) * (1 - sstep(0.2, 0.0, r))), (S.accent(0.85), ring),
              (S.main(0.5), rim), (S.main(0.9), cross), (WHITE, core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.7, sigma=0.03, gain=0.55)
    return rgb, a * (1 - sstep(0.93, 0.99, r))


# ------------------------------------------------------------------ 종 (jbell2 — 세움 · 높이 약 반 크기 × 2)

BELL_ANG = [-14, -7, 0, 7, 14, 7, 0, -7]


def bell_frame(S, f, n=256):
    x, y = grid(n)
    aa = 2.0 / n
    ang = math.radians(BELL_ANG[f])
    py = 0.66  # 매단 곳
    ca, sa = math.cos(ang), math.sin(ang)
    # 종 좌표 (매단 곳을 둘레로 돌림 — u 가로 · v 아래로 0 ~ 1)
    u = (x * ca + (y - py) * sa)
    w_ = (-x * sa + (y - py) * ca)
    v = -w_ / 1.05
    prof = 0.11 + 0.18 * np.clip(v, 0, 1) ** 1.5 + 0.16 * sstep(0.7, 1.0, v)
    dome = (v > -0.05)
    body = (1 - sstep(prof - aa, prof + aa, np.abs(u))) * dome * (v < 1.0) * (1 - sstep(0.07, 0.0, v + 0.05 - 0.12 * (np.abs(u) / 0.12) ** 2))
    crown = disk(u, v, 0, 0.02, 0.12, 0.08, aa)
    body = np.maximum(body, crown * (v < 0.2))
    lip = body * sstep(0.86, 0.93, v)
    band = body * (np.exp(-((v - 0.32) / 0.012) ** 2) + np.exp(-((v - 0.38) / 0.008) ** 2) + np.exp(-((v - 0.8) / 0.012) ** 2))
    cyl = np.clip(u / np.maximum(prof, 1e-3), -1, 1)
    shade = np.clip(0.42 + 0.45 * np.cos((cyl + 0.35) * 1.4) - 0.15 * v, 0, 1)
    spec = body * np.exp(-((cyl + 0.45) / 0.12) ** 2) * (v > 0.1) * (v < 0.85)
    loop = np.exp(-((np.hypot(u, v + 0.1) - 0.07) / 0.016) ** 2) * (v < -0.05)
    cl_u = 0.0 - math.sin(ang) * 0.06
    clapper = disk(u, v, cl_u, 1.04, 0.07, 0.07, aa)
    layers = [(S.main(0.25), np.clip(body * 1.2, 0, 1) * 0.0 + loop * 0.9), (S.main(np.clip(0.35 + 0.55 * shade, 0, 1)), body), (S.main(0.95), band * 0.8),
              (S.main(0.98), lip * 0.6), (WHITE, spec * 0.75), (S.main(0.6), clapper), (WHITE, clapper * 0.4)]
    # 소리 고리 (끝 0 · 4 에서 울려 다음 칸으로 아래로 퍼짐)
    ph = {0: 0, 1: 1, 2: 2, 4: 0, 5: 1, 6: 2}.get(f)
    if ph is not None:
        R = 0.2 + 0.22 * ph
        ky = -0.5 - 0.06 * ph
        e = np.hypot(x / R, (y - ky) / (R * 0.32))
        ring = np.exp(-((e - 1) / (0.08 / max(R, 0.2))) ** 2) * (0.9 - 0.25 * ph)
        layers.insert(0, (S.accent(0.85), ring))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.03, gain=0.4)
    return rgb, a


# ------------------------------------------------------------------ 연꽃 (jlotus — 땅에 누움 · 꽃잎 끝 반지름 = 반 크기 × 0.9)

def lotus_frame(S, f, n=384):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    open_ = [0.0, 0.4, 0.75, 1.0, 1.0, 1.0, 0.95, 0.85][f]
    k = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.65, 0.3][f]
    drift = [0, 0, 0, 0, 0, 0, 0.06, 0.12][f]
    tw = [0, 0, 0, 0.0, 0.5, 1.0, 0, 0][f]
    layers = [(S.accent(0.5), np.exp(-(r / (0.35 + 0.2 * open_)) ** 2) * (0.35 + 0.25 * (f in (3, 5))) * k)]
    body = np.zeros_like(x)
    vein = np.zeros_like(x)
    edge = np.zeros_like(x)
    shade = np.zeros_like(x)
    for ring, (cnt, L0, W0, off) in enumerate(((8, 0.85, 0.2, math.pi / 8), (8, 0.6, 0.17, 0.0))):
        L = L0 * open_
        if L <= 0.02:
            continue
        for i in range(cnt):
            a = off + i * 2 * math.pi / cnt
            cx, cy = (0.05 + drift) * math.cos(a), (0.05 + drift) * math.sin(a)
            m, t, vv = lens(x, y, cx, cy, a, L, W0 * min(1.0, open_ + 0.2), aa, base=0.02)
            fresh = m * (1 - body)
            body = np.maximum(body, m)
            vein = np.maximum(vein, fresh * np.exp(-(vv / 0.12) ** 2) * (t > 0.1))
            edge = np.maximum(edge, fresh * sstep(0.7, 0.98, np.abs(vv)))
            shade = np.where(fresh > 0, 0.45 + 0.4 * t + 0.1 * ring, shade)
    layers += [(S.main(np.clip(shade, 0, 1)), body * k), (S.accent(0.8), vein * 0.55 * k), (WHITE, edge * 0.7 * k)]
    if f == 0:
        layers += [(S.accent(0.9), np.exp(-(r / 0.12) ** 2)), (WHITE, star4(x, y, 0, 0, 0.3, 0.035, 0.0, aa))]
    else:
        layers += [(S.accent(0.85), np.exp(-(r / 0.13) ** 2) * k), (WHITE, np.exp(-(r / 0.06) ** 2) * k)]
    if tw > 0 or f == 3:
        for i in range(8):
            a = math.pi / 8 + i * math.pi / 4
            on = (i % 2 == 0) == (f != 4)
            if not on:
                continue
            layers.append((WHITE, star4(x, y, 0.88 * open_ * math.cos(a), 0.88 * open_ * math.sin(a), 0.1, 0.014, a, aa)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 큰 꽃 (jbloom — 카메라를 봄 · 꽃 반지름 약 반 크기 × 0.85)

def bloom_frame(S, f, n=384):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    layers = []
    if f <= 1:
        sw = 1.0 + 0.15 * f
        bud = disk(x, y + 0.05, 0, 0, 0.2 * sw, 0.34 * sw, aa) * (1 - sstep(0.25 * sw, 0.33 * sw, y + 0.05))
        tip = lens(x, y, 0, 0.0, math.pi / 2, 0.42 * sw, 0.2 * sw, aa)[0]
        bud = np.maximum(bud, tip)
        seam = bud * np.exp(-(x / (0.012 + 0.012 * f)) ** 2)
        lit = wet(bud, n)
        layers += [(S.accent(0.55), np.exp(-(r / 0.45) ** 2) * (0.3 + 0.25 * f)), (S.main(0.55 + 0.25 * lit), bud), (WHITE, seam * (0.7 + 0.3 * f)),
                   (S.accent(0.85), seam * 0.5)]
        if f == 1:
            layers.append((WHITE, star4(x, y, 0, 0.1, 0.35, 0.03, 0.0, aa) * 0.8))
    else:
        open_ = [0, 0, 0.5, 0.8, 1.0, 1.0, 1.0, 1.0][f]
        scatter = [0, 0, 0, 0, 0, 0, 0.25, 0.5][f]
        k = [1, 1, 1, 1, 1, 1, 0.75, 0.35][f]
        body = np.zeros_like(x)
        shade = np.zeros_like(x)
        edge = np.zeros_like(x)
        for i in range(6):
            a = math.pi / 2 + i * math.pi / 3 + scatter * (0.6 if i % 2 else -0.4)
            d = 0.06 + scatter * 0.7
            m, t, vv = lens(x, y, d * math.cos(a), d * math.sin(a), a, 0.82 * open_ * (1 - 0.8 * scatter), 0.3 * min(1, open_ + 0.15) * (1 - 0.5 * scatter), aa,
                             base=0.06)
            fresh = m * (1 - body)
            body = np.maximum(body, m)
            shade = np.where(fresh > 0, 0.5 + 0.35 * t, shade)
            edge = np.maximum(edge, fresh * sstep(0.75, 0.98, np.abs(vv)))
        lit = wet(body, n)
        layers += [(S.accent(0.5), np.exp(-(r / 0.6) ** 2) * 0.3 * k), (S.main(np.clip(shade + 0.2 * lit, 0, 1)), body * k), (WHITE, edge * 0.6 * k),
                   (S.accent(0.85), np.exp(-(r / 0.16) ** 2) * k * (1 - scatter)), (WHITE, np.exp(-(r / 0.07) ** 2) * k * (1 - scatter))]
        if f in (4, 5):
            from .base import spike
            rays = np.zeros_like(x)
            for i in range(8):
                rays = np.maximum(rays, spike(x, y, i * math.pi / 4 + (f - 4) * math.pi / 8, 0.98, 0.03, 1.3, aa))
            layers.insert(0, (S.main(0.85), rays * 0.45))
            rng = np.random.default_rng(1950 + f)
            for i in range(9):
                a = rng.uniform(0, 2 * math.pi)
                rr = rng.uniform(0.25, 0.75)
                layers.append((S.accent(0.95), disk(x, y, rr * math.cos(a), rr * math.sin(a), 0.018, 0.018, aa)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 나비 (jfly — 카메라를 봄 · 날개 너비 약 반 크기 × 1.8)

FLAP = [1.0, 0.78, 0.45, 0.14, 0.45, 0.78]


def fly_frame(S, f, n=192):
    x, y = grid(n)
    aa = 2.0 / n
    sx = FLAP[f] if f < 6 else 0.9
    ax = np.abs(x) / max(sx, 0.08)
    up = disk(ax, y, 0.42, 0.22, 0.42, 0.34, aa) * (ax > 0.02)
    low = disk(ax, y, 0.3, -0.3, 0.27, 0.26, aa) * (ax > 0.02)
    wing = np.maximum(up, low)
    vein = wing * (np.exp(-((y - 0.22 - (ax - 0.42) * 0.25) / 0.012) ** 2) * (ax > 0.1) + np.exp(-((y + 0.3 + (ax - 0.3) * 0.3) / 0.01) ** 2) * (ax > 0.08))
    from scipy import ndimage
    rim = np.clip(ndimage.maximum_filter(wing, size=5) - wing, 0, 1)
    spot = disk(ax, y, 0.55, 0.3, 0.08, 0.08, aa) + disk(ax, y, 0.32, -0.36, 0.06, 0.06, aa)
    grad = np.clip(0.4 + 0.5 * ax / 0.85, 0, 1)
    body = disk(x, y, 0, -0.02, 0.04, 0.3, aa)
    ant = taper(x, y, [(0, 0.26), (0.08, 0.44), (0.16, 0.54)], 0.012, 0.006, aa) + taper(x, y, [(0, 0.26), (-0.08, 0.44), (-0.16, 0.54)], 0.012, 0.006, aa)
    if f < 6:
        layers = [(S.accent(0.5), np.exp(-(np.hypot(x, y) / 0.6) ** 2) * 0.25), (S.main(grad), wing * 0.95), (S.accent(0.85), np.clip(spot, 0, 1) * wing),
                  (WHITE, vein * 0.6), (WHITE, rim * 0.9 * (sx > 0.3)), (S.main(0.35), body), (S.main(0.7), np.clip(ant, 0, 1))]
    else:
        k = 1.0 if f == 6 else 0.45
        rng = np.random.default_rng(1990 + f)
        dots = np.zeros_like(x)
        for i in range(40):
            px, py = rng.uniform(-0.85, 0.85), rng.uniform(-0.55, 0.55)
            if disk(np.array([[abs(px) / 0.9]]), np.array([[py]]), 0.42, 0.22, 0.42, 0.34, 0.01)[0, 0] < 0.5 and \
                    disk(np.array([[abs(px) / 0.9]]), np.array([[py]]), 0.3, -0.3, 0.27, 0.26, 0.01)[0, 0] < 0.5:
                continue
            s = 1.15 + 0.15 * (f - 6)
            dots = np.maximum(dots, disk(x, y, px * s, py * s, 0.02, 0.02, aa))
        layers = [(S.main(0.85), dots * k), (WHITE, star4(x, y, 0, 0, 0.3, 0.03, 0.0, aa) * (f == 6))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.03, gain=0.4)
    return rgb, a * (1 - sstep(0.94, 0.99, np.maximum(np.abs(x), np.abs(y))))


FRAMES = {
    "heal": (HEAL_FRAMES, heal_frame),
    "note": (NOTE_FRAMES, note_frame),
    "stigma": (STIGMA_FRAMES, stigma_frame),
    "bell": (BELL_FRAMES, bell_frame),
    "lotus": (LOTUS_FRAMES, lotus_frame),
    "bloom": (BLOOM_FRAMES, bloom_frame),
    "fly": (FLY_FRAMES, fly_frame),
}


def frames_of(name, S):
    n, fn = FRAMES[name]
    return [to_img(*fn(S, f)) for f in range(n)]


def sprites(S, names=tuple(FRAMES)):
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    return [(p + k, fr(k), True) for k in names]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        from .element import STYLES
        out = sys.argv[sys.argv.index("--preview") + 1]
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "holy"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
