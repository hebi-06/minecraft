"""2026-11-15 블레이드 레인저 전용 이펙트 그림 (그림판 br_* — 사용자 "직업마다 전용으로 그릴래", 팬텀 아처 pa_* · 아스널 킹 ak_* 다음). 투영한 쌍검을 들고
파고드는 단조 칼잡이: 색은 흰빛 → 노랑 → 주황 → 짙은 주홍 → 검붉은 쇳물 + 푸른 강철 (날의 차가운 빛 — 한 가지 색이 아니게) · 검은 쇠 연기.

- 베기 br_slash (2 × 2 · 12칸): 달아오른 날이 호를 따라 휘두름 — 머리 (흰빛 · 뾰족) 가 0 ~ 2 칸에 호를 지나가고 몸은 주황 → 검붉게 식으며 꼬리부터 거둬짐,
  바깥 날은 흰 금빛 · 안쪽 날은 푸른 강철빛 줄 · 머리가 지난 자리에서 불티가 튐. 자바 (PhantomFx.slash) 가 휘두른 면에 고정 · 앞뒤 두 장.
- br_xslash (2 × 2 · 10칸): 쌍검 X 베기 (두 줄기 차례로 · 교차점 섬광 · 불티 · 쇳물 방울)
- 큰 그림 (2 × 2): br_boom (단조 폭발 16칸 — 쇳물 방울 · 불티 줄기 · 검은 쇠 연기) · br_shock (열 충격파 10칸) · br_crack (녹은 금 5칸) · br_ring (톱니 고리 4칸)
- 한 장 256: br_boom_s · br_shock_s · br_crack_s · br_flash (불티 별 8칸) · br_charge (모이는 열 10칸) · br_dust (그을음 · 불티 8칸)
- 128: br_spark (불티 4칸) · br_shard (달아오른 쇠 조각 8칸) · br_flame (단조 불꽃 128 × 256 · 10칸)

  cd tools && python3 -m jobvfx.forge --preview 폴더 [--only slash,boom]
"""
import math
import sys

import numpy as np
from scipy import ndimage

from .base import (WHITE, H, Noise3, banded, bloom, compose, grid, preview, ramp, spike, sstep, star_shape, tiled, to_img)

LIB = "br"

# 쇳물 (흰빛 → 노랑 → 주황 → 주홍 → 검붉은 → 검은 쇠)
FORGE = ramp([(0.0, "#0E0806"), (0.12, "#24100A"), (0.26, "#561608"), (0.4, "#9C2A08"), (0.52, "#DA4A0C"), (0.64, "#F77A18"),
              (0.76, "#FFAA36"), (0.86, "#FFD47A"), (0.94, "#FFF1CC"), (1.0, "#FFFFFF")])
STEEL = ramp([(0.0, "#10141E"), (0.4, "#2C3A52"), (0.7, "#7F95B8"), (0.9, "#D6E2F5"), (1.0, "#FFFFFF")])
SOOT = ramp([(0.0, "#0C0A0A"), (0.45, "#26201E"), (0.8, "#4A403A"), (1.0, "#6E625A")])


def edge_fade(x, y, lo=0.93, hi=0.99):
    return 1 - sstep(lo, hi, np.maximum(np.abs(x), np.abs(y)))


def seg_dist(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    ll = dx * dx + dy * dy + 1e-12
    s = np.clip(((x - ax) * dx + (y - ay) * dy) / ll, 0, 1)
    return np.hypot(x - (ax + s * dx), y - (ay + s * dy)), s


def streaks(x, y, items, aa):
    """불티 줄기 [(머리 x, y, 꼬리 x, y, 굵기, 밝기)] → 알파 (머리 쪽이 밝음)"""
    m = np.zeros_like(x)
    for hx, hy, tx, ty, w, b in items:
        if b <= 0.01:
            continue
        d, s = seg_dist(x, y, tx, ty, hx, hy)
        m = np.maximum(m, (1 - sstep(w * 0.6, w + aa, d)) * (0.35 + 0.65 * s) * b)
    return m


# ------------------------------------------------------------------ 베기 (2 × 2)

SLASH_FRAMES = 12
# 그림 안의 호: 가운데 (0, SLASH_CY) · 반지름 SLASH_R (그림 단위 — 자바 PhantomFx.slash: 칼 반지름 r = SLASH_R 그림 단위 → 그림 한 변 = 2r / SLASH_R,
# 그림 가운데 = 호 가운데 + 볼록한 쪽 × (−SLASH_CY / SLASH_R) r)
SLASH_CY = -0.4
SLASH_R = 0.9
SLASH_A0 = math.radians(184)   # 휘두름 시작 (왼쪽)
SLASH_A1 = math.radians(-4)    # 끝 (오른쪽)


def slash_frame(f, n=512, seed=201):
    """달아오른 쌍검 베기 (초승달 — 볼록한 쪽이 그림 위, 왼쪽 → 위 → 오른쪽으로 휘두름): 0 ~ 2 칸 머리 (흰빛 · 뾰족) 가 호를 지나감 · 몸은 바깥 날 (흰 노랑 —
    자르는 날) 에서 안쪽으로 두꺼운 초승달 (머리 뒤가 가장 굵고 꼬리 · 머리 끝은 뾰족) — 노랑 → 주황 → 주홍 → 검붉은 꼬리, 안쪽 가장자리는 푸른 강철빛 줄 ·
    옅은 번짐 띠 (움직임 흐림) · 주황 열기 → 3 ~ 11 칸 꼬리부터 거둬지며 검붉게 식은 잔상 · 머리가 지난 바깥 날에서 불티 줄기가 휘어 떨어짐"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    aa = 1.6 / n
    yy = y - SLASH_CY
    rho = np.hypot(x, yy)
    al = np.arctan2(yy, x)
    al = np.where(al < SLASH_A1 - 0.6, al + 2 * math.pi, al)
    s = (SLASH_A0 - al) / (SLASH_A0 - SLASH_A1)                 # 0 (시작) → 1 (끝)
    d = (rho - SLASH_R) / SLASH_R                               # 바깥 날 = 0 · 몸은 안쪽 (−)
    sh = (1 - (1 - min(1.0, (f + 1) / 3.0)) ** 1.6) * 1.05
    st = sstep(1.6, 11.0, f) * 1.08 - 0.03
    span = max(sh - st, 1e-3)
    sp_ = np.clip((s - st) / span, 0, 1)                         # 0 꼬리 → 1 머리
    vis = ((s < sh) & (s > st) & (s > -0.03) & (s < 1.03)).astype(float)
    W = 0.3 * (1 - 0.5 * sstep(2, 10, f))
    w = W * sp_ ** 1.1 * (1 - sp_) ** 0.35 / 0.449
    inside = (1 - sstep(w - aa * 1.5, w + aa * 1.5, -d)) * sstep(-aa * 1.5, aa * 1.5, -d)
    body = vis * inside
    k = 1 - sstep(7.5, 11.5, f)
    rel = np.clip(-d / np.maximum(w, 1e-4), 0, 1)               # 0 바깥 날 → 1 안쪽
    cool = sstep(2.5, 11, f)
    T = sp_ ** 0.9 * 0.55 + (1 - rel) ** 1.5 * 0.33 + 0.12 - 0.3 * cool * (1 - sp_)
    Tb = banded(np.clip(T, 0, 1), [0.3, 0.42, 0.54, 0.66, 0.78, 0.88])
    layers = []
    # 열기 · 움직임 흐림 띠
    smear = vis * (1 - sstep(w * 1.7, w * 1.7 + aa * 3, -d)) * sstep(-0.02, 0.0, -d) * (1 - sp_) ** 0.3 * 0.28
    glow = ndimage.gaussian_filter(body * (0.4 + 0.6 * sp_), n * 0.02)
    layers.append((FORGE(0.5), np.clip(glow * 2.0, 0, 1) * 0.75 * k))
    layers.append((FORGE(0.42), smear * k))
    layers.append((FORGE(0.28 + 0.72 * Tb), body * (0.6 + 0.4 * sp_) * k))
    # 안쪽 가장자리 푸른 강철빛 줄 · 바깥 날 흰 노랑
    rim_in = body * np.exp(-((rel - 0.88) / 0.07) ** 2) * (w > 0.025) * (0.4 + 0.6 * sp_) * 0.85
    layers.append((STEEL(0.82), rim_in * k))
    edge = vis * np.exp(-(d / 0.008) ** 2) * sp_ ** 0.6 * (w > 0.004)
    layers.append((FORGE(0.92), edge * k))
    layers.append((WHITE, edge * sp_ ** 1.8 * k))
    head = vis * np.exp(-(((s - sh) * 4.0) ** 2 + (d / 0.04) ** 2) / 0.008) * (1 - sstep(2.5, 4.0, f))
    layers.append((WHITE, np.clip(head * 1.5, 0, 1)))
    # 불티 (머리가 지난 바깥 날에서 휘어 떨어짐)
    items = []
    for i in range(16):
        se = rng.uniform(0.08, 0.98)
        te = se * 2.2
        age = f - te
        if age < 0 or age > 6:
            continue
        a = SLASH_A0 - se * (SLASH_A0 - SLASH_A1)
        px, py = SLASH_R * math.cos(a), SLASH_R * math.sin(a) + SLASH_CY
        tx, ty = math.sin(a), -math.cos(a)
        ox, oy = math.cos(a), math.sin(a)
        v = rng.uniform(0.6, 1.0)
        mx, my = (tx * 0.7 + ox * 0.55) * v, (ty * 0.7 + oy * 0.55) * v
        tt = age * 0.075
        hx, hy = px + mx * tt, py + my * tt - 0.9 * tt * tt
        vx, vy = mx, my - 1.8 * tt
        nn = math.hypot(vx, vy) + 1e-6
        L = 0.07 + 0.06 * v
        items.append((hx, hy, hx - vx / nn * L, hy - vy / nn * L, 0.009, (1 - age / 6.0) ** 1.1))
    layers.append((FORGE(0.9), np.clip(streaks(x, y, items, aa) * 1.4, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.02, gain=0.85 * k + 0.1)
    return rgb, a * edge_fade(x, y)


def slash():
    return [to_img(*slash_frame(f)) for f in range(SLASH_FRAMES)]


# ------------------------------------------------------------------ X 베기 (2 × 2)

XSLASH_FRAMES = 10


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


def xslash_frame(f, n=512, seed=211):
    """쌍검 X 베기: 0 ~ 2 칸 두 줄기가 차례로 (머리 흰빛 · 몸 주황 · 가운데 흰 노랑 · 가장자리 푸른 강철빛) → 2 ~ 5 칸 교차점 섬광 · 굵은 빛살 넷 → 불티 줄기 ·
    쇳물 방울이 튀어 떨어짐 → 4 ~ 9 칸 꼬리부터 검붉게 식으며 거둬짐"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    layers = []
    body_all = np.zeros_like(r)
    core_all = np.zeros_like(r)
    hot_all = np.zeros_like(r)
    rim_all = np.zeros_like(r)
    for i, ang in enumerate((-math.pi / 4, math.pi / 4)):
        g = f - i
        if g < 0:
            continue
        head = -1.05 + 2.2 * min(1.0, (g + 1) / 2.5)
        tail = -1.05 + 2.1 * sstep(2.5, 8.5, g)
        w0 = 0.09 * (1 - 0.5 * sstep(2, 9, g)) + 0.01
        body, hot, mid = _stroke(x, y, ang + math.pi, 0.08 * (1 if i == 0 else -1), 0.86, w0, head, tail, aa)
        k = 1 - sstep(6, 9.5, g)
        body_all = np.maximum(body_all, body * k)
        core_all = np.maximum(core_all, body * np.clip(1 - mid * 1.8, 0, 1) * k)
        rim_all = np.maximum(rim_all, body * np.exp(-((mid - 0.85) / 0.08) ** 2) * k)
        hot_all = np.maximum(hot_all, body * hot * (1 - sstep(2.5, 4.5, g)))
    cool = sstep(3, 9, f)
    glow = ndimage.gaussian_filter(body_all, n * 0.02)
    layers.append((FORGE(0.48), np.clip(glow * 1.6, 0, 1) * 0.75))
    layers.append((FORGE(0.62 - 0.22 * cool + 0.15 * core_all), body_all))
    layers.append((STEEL(0.8), rim_all * 0.7))
    layers.append((FORGE(0.88 - 0.2 * cool), core_all))
    layers.append((WHITE, np.clip(hot_all * 1.2 + core_all * 0.3 * (1 - cool), 0, 1)))
    if 2 <= f <= 5:
        k = 1 - (f - 2) / 4.0
        rays = np.zeros_like(r)
        for j in range(4):
            rays = np.maximum(rays, spike(x, y, j * math.pi / 2 + 0.1, 0.46 + 0.1 * (f - 2), 0.05 * k + 0.008, 1.2, aa))
        layers.append((FORGE(0.82), rays * k))
        layers.append((WHITE, np.clip(np.exp(-(r / (0.1 + 0.04 * (f - 2))) ** 2) * k * 1.3, 0, 1)))
    if 2 <= f <= 9:
        q = (f - 2) / 7.0
        items = []
        drops = np.zeros_like(r)
        for j in range(30):
            a0 = rng.uniform(0, 2 * math.pi)
            v0 = rng.uniform(0.4, 1.0)
            d0 = 0.06 + 0.75 * v0 * (1 - (1 - q) ** 1.8)
            hx, hy = d0 * math.cos(a0), d0 * math.sin(a0) - 0.2 * q * q
            tx, ty = (d0 - 0.07 * v0) * math.cos(a0), (d0 - 0.07 * v0) * math.sin(a0) - 0.2 * q * q * 0.85
            if j % 3 == 0:
                drops = np.maximum(drops, np.exp(-((x - hx) ** 2 + (y - hy) ** 2) / (0.012 * (1 - 0.5 * q) + 0.004) ** 2) * (1 - q))
            else:
                items.append((hx, hy, tx, ty, 0.006, (1 - q) ** 1.2))
        layers.append((FORGE(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
        layers.append((FORGE(0.8 - 0.25 * q), np.clip(drops * 1.4, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.022, gain=0.8)
    return rgb, a * edge_fade(x, y)


def xslash():
    return [to_img(*xslash_frame(f)) for f in range(XSLASH_FRAMES)]


# ------------------------------------------------------------------ 단조 폭발 (2 × 2)

BOOM_FRAMES = 16


def boom_frame(f, n=512, seed=221):
    """단조 폭발 (포지 브레이커 · 아이언 템페스트 끝 · 칼이 부서짐): 0 ~ 3 흰 섬광 → 1 ~ 8 셀 띠 쇳물 불덩이 (흰빛 → 노랑 → 주황 → 주홍) → 6 ~ 15 식은 불은 줄고
    검은 쇠 연기가 떠오르며 흩어짐 · 바깥으로 튀는 불티 줄기 (빠름) · 포물선으로 떨어지는 쇳물 방울 (식으며 검붉게)"""
    N = Noise3(seed)
    N2 = Noise3(seed + 1)
    x, y = grid(n)
    u = f / (BOOM_FRAMES - 1)
    aa = 1.6 / n
    R = 0.18 + 0.56 * (1 - (1 - min(1.0, f / 6.0)) ** 2.4) + 0.06 * max(0.0, (f - 6) / 9.0)
    rise = 0.09 * max(0.0, (f - 4) / 11.0) ** 1.2
    yy = y - rise
    r = np.hypot(x, yy)
    th = np.arctan2(yy, x)
    t = f * 0.22
    wx = N.fbm(x * 2.6 + 3, yy * 2.6, t, 4)
    wy = N.fbm(x * 2.6 - 7, yy * 2.6 + 2, t + 9, 4)
    q = N2.fbm(x * 3.6 + wx * 1.7, yy * 3.6 + wy * 1.7, t * 1.2, 5)
    lick = N.fbm(np.cos(th) * 2.4 + 11, np.sin(th) * 2.4 - 4, t * 1.4, 3)
    layers = []
    # 검은 쇠 연기 (위로 · 불빛을 받은 아랫면은 주홍)
    lick2 = N2.fbm(np.cos(th) * 1.5 - 3, np.sin(th) * 1.5 + 6, t * 0.8, 3)
    q2 = N.fbm(x * 2.0 + wx * 0.9 + 21, yy * 2.0 + wy * 0.9, t * 0.7, 4)
    Rs = R * (0.98 + 0.25 * u) * (0.7 + 0.55 * lick2 + 0.12 * (q2 - 0.5))
    cloud = 1 - sstep(Rs * 0.82, Rs * 1.02, r)
    thr = 0.14 + 0.36 * sstep(0.3, 1.0, u)
    puff = sstep(thr, thr + 0.22, q2 + 0.18 * (1 - r / np.maximum(Rs, 1e-3)))
    s_alpha = cloud * puff * (0.55 - 0.25 * u) * (1 - sstep(0.62, 1.0, u)) * sstep(0.1, 0.25, u)
    shade = np.clip(0.45 + 0.5 * (yy / np.maximum(Rs, 1e-3)) + 0.35 * (q2 - 0.5), 0, 1)
    heat_low = np.clip(-yy / np.maximum(Rs, 1e-3), 0, 1) * (1 - u)
    layers.append((SOOT(shade) * (1 - 0.5 * heat_low[..., None]) + FORGE(0.45) * (0.5 * heat_low[..., None]), s_alpha))
    # 불덩이
    Rf = R * (1.0 - 0.55 * sstep(0.25, 0.92, u)) * (0.82 + 0.34 * lick + 0.12 * (q - 0.5))
    fire = 1 - sstep(Rf * 0.86, Rf * 1.02, r)
    cool = sstep(0.15, 1.0, u) * 0.5
    T = (1 - np.clip(r / np.maximum(Rf, 1e-3), 0, 1)) ** 0.75 * 0.8 + (q - 0.5) * 0.55 + 0.36 - cool
    T = T + 0.55 * (1 - sstep(0.0, 0.16, u)) * np.exp(-(r / (R * 0.6)) ** 2)
    Tb = banded(np.clip(T, 0, 1), [0.24, 0.36, 0.5, 0.62, 0.74, 0.86])
    f_alpha = fire * np.clip(T * 2.2 + 0.2, 0, 1) * np.clip((T - 0.12) * 4.0, 0, 1) * (1 - sstep(0.45, 0.8, u))
    layers.append((FORGE(0.3 + 0.7 * Tb), f_alpha))
    if f <= 4:
        k = 1 - f / 5.0
        layers.append((WHITE, np.clip(np.exp(-(r / (0.24 + 0.14 * f / 4)) ** 2) * k * 1.2, 0, 1)))
    # 불티 줄기 (빠름) · 쇳물 방울 (포물선)
    pr = np.random.default_rng(seed + 5)
    items = []
    drops = np.zeros_like(r)
    for i in range(44):
        a0 = pr.uniform(0, 2 * math.pi)
        v = pr.uniform(0.5, 1.0)
        if i < 30:
            if not (1 <= f <= 9):
                continue
            prog = 1 - (1 - min(1.0, f / 9.0)) ** 1.5
            d0 = 0.15 + v * 0.8 * prog
            L = 0.07 + 0.09 * v * (1 - 0.5 * prog)
            hx, hy = d0 * math.cos(a0), d0 * math.sin(a0) + rise
            items.append((hx, hy, (d0 - L) * math.cos(a0), (d0 - L) * math.sin(a0) + rise, 0.006, (1 - f / 10.0)))
        else:
            if f < 2:
                continue
            tt = (f - 2) / 13.0
            vx, vy = math.cos(a0) * v * 0.75, abs(math.sin(a0)) * v * 0.9 + 0.2
            px = vx * tt
            py = vy * tt - 1.0 * tt * tt
            life = 1 - sstep(0.7, 1.0, tt)
            drops = np.maximum(drops, np.exp(-((x - px) ** 2 + (y - py) ** 2) / (0.016 * (1 - 0.4 * tt)) ** 2) * life)
    layers.append((FORGE(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    layers.append((FORGE(0.85 - 0.3 * u), np.clip(drops * 1.5, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.58, sigma=0.03, gain=1.0 - 0.5 * u)
    return rgb, a * edge_fade(x, y, 0.9, 0.98)


def boom():
    return [to_img(*boom_frame(f)) for f in range(BOOM_FRAMES)]


def boom_s():
    return [to_img(*boom_frame(int(round(i * (BOOM_FRAMES - 1) / 11)), n=256, seed=223)) for i in range(12)]


# ------------------------------------------------------------------ 열 충격파 (2 × 2 — 땅)

SHOCK_FRAMES = 10


def shock_frame(f, n=512, seed=231):
    """땅의 열 충격파 (위에서 봄): 흰 노랑 심 앞 날 + 주황 겉 · 바로 뒤 짧은 주홍 빛 (속은 비움) · 그을음 띠 · 앞 날에서 바깥으로 튀는 불티 줄기"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (SHOCK_FRAMES - 1)
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
    soot = np.clip((soot_n - 0.35) * 2.0, 0, 1) * np.exp(-((d + 0.08) / 0.07) ** 2) * (1 - u) ** 0.7 * 0.45
    items = []
    for i in range(34):
        a0 = rng.uniform(0, 2 * math.pi)
        L = rng.uniform(0.04, 0.11)
        hx, hy = (R + L) * math.cos(a0), (R + L) * math.sin(a0)
        items.append((hx, hy, R * math.cos(a0), R * math.sin(a0), 0.0045, (1 - u) ** 1.2 * rng.uniform(0.5, 1.0)))
    layers = [(SOOT(0.4), soot), (FORGE(0.5 + 0.25 * trail), trail * 0.85), (FORGE(0.9), np.clip(streaks(x, y, items, aa) * 1.2, 0, 1)),
              (FORGE(0.8), edge * 0.9), (FORGE(0.95), core), (WHITE, core * 0.6)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.018, gain=0.85 * k)
    return rgb, a * (1 - sstep(0.93, 0.99, r))


def shock():
    return [to_img(*shock_frame(f)) for f in range(SHOCK_FRAMES)]


def shock_s():
    return [to_img(*shock_frame(f, n=256, seed=233)) for f in range(SHOCK_FRAMES)]


# ------------------------------------------------------------------ 녹은 금 (2 × 2 — 땅)

CRACK_FRAMES = 5


def crack_frame(f, n=512, seed=241):
    """칼이 내려친 · 터진 자리 (위에서 봄): 검게 탄 자국 + 가운데서 뻗은 굵은 금 (쇳물처럼 흰 노랑 → 주황 → 검붉게 식음 — 가장자리부터) · 가운데 웅덩이 빛"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    u = f / (CRACK_FRAMES - 1)
    burn = (1 - sstep(0.32, 0.8, r + (N.fbm(x * 4, y * 4, 1.0, 4) - 0.5) * 0.35)) * 0.88
    dist = np.full_like(r, 9.0)
    for i in range(8):
        a = rng.uniform(0, 2 * math.pi)
        px, py = 0.0, 0.0
        L = rng.uniform(0.5, 0.88)
        steps = 6
        for s in range(steps):
            a += rng.uniform(-0.5, 0.5)
            qx, qy = px + math.cos(a) * L / steps, py + math.sin(a) * L / steps
            dd, _ = seg_dist(x, y, px, py, qx, qy)
            dist = np.minimum(dist, dd / (0.026 * (1 - s / steps) + 0.006))
            px, py = qx, qy
    crack = np.clip(1 - dist, 0, 1)
    heat = (1 - u) ** 1.2
    T = crack ** 0.6 * (0.55 + 0.45 * heat) - 0.15 * u
    pool = np.exp(-(r / 0.14) ** 2) * heat
    layers = [(SOOT(0.15), burn * (0.9 - 0.3 * u)), (FORGE(0.3 + 0.65 * np.clip(T, 0, 1)), (crack > 0.02) * np.clip(crack * 2.5, 0, 1) * (0.5 + 0.5 * heat)),
              (FORGE(0.88), pool * 0.85)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.5, sigma=0.012, gain=0.75 * heat + 0.1)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


def crack():
    return [to_img(*crack_frame(f)) for f in range(CRACK_FRAMES)]


def crack_s():
    return [to_img(*crack_frame(f, n=256, seed=243)) for f in range(CRACK_FRAMES)]


# ------------------------------------------------------------------ 톱니 고리 (2 × 2 · 되풀이)

RING_FRAMES = 4


def ring_frame(f, n=512, seed=251):
    """땅에 머무는 단조 고리 (블레이드 스톰 · 돌며 베기 · 아이언 템페스트 들판): 반지름 0.88 — 흰 노랑 심 줄 + 주황 빛 · 바깥 톱니 서른여섯 (네모 이빨) ·
    안쪽 푸른 강철 가는 고리 · 숨쉼 (되풀이 4칸)"""
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / RING_FRAMES * 2 * math.pi
    R = 0.86
    pulse = 0.85 + 0.15 * math.sin(ph)
    teeth_on = (np.cos(th * 36) > 0.25).astype(float)
    teeth = teeth_on * sstep(R + 0.005, R + 0.012, r) * (1 - sstep(R + 0.04, R + 0.048, r))
    layers = [(FORGE(0.5), np.exp(-(np.clip(R - r, 0, None) / 0.05) ** 1.3) * (r < R) * 0.5 * pulse),
              (FORGE(0.56), np.exp(-(np.clip(r - R, 0, None) / 0.035) ** 1.3) * (r >= R) * 0.5 * pulse),
              (FORGE(0.78), teeth * 0.95 * pulse), (STEEL(0.85), np.exp(-((r - R * 0.9) / 0.004) ** 2) * 0.75),
              (FORGE(0.9), np.exp(-((r - R) / 0.015) ** 2) * 0.95), (WHITE, np.exp(-((r - R) / 0.005) ** 2))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.012, gain=0.6)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


def ring():
    return [to_img(*ring_frame(f)) for f in range(RING_FRAMES)]


# ------------------------------------------------------------------ 불티 별 (256)

FLASH_FRAMES = 8


def flash_frame(f, n=256, seed=261):
    """불티 별 (칼이 나옴 · 부서짐 · 내려침): 0 ~ 2 칸 단단한 흰 노랑 별 (꼭짓점 여섯) → 굵은 빛살 여섯 (흰 심 · 주황 겉) 이 가늘어지며 · 사방으로 튀는 불티 줄기
    (떨어짐) · 퍼지는 주홍 고리"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (FLASH_FRAMES - 1)
    aa = 1.6 / n
    grow = min(1.0, (f + 1) / 2.0)
    k = (1 - u) ** 1.1
    layers = [(FORGE(0.66 + 0.2 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.3 + 0.16 * u)) ** 2) * 0.75 * k)]
    ring_r = 0.18 + 0.7 * (1 - (1 - u) ** 1.6)
    layers.append((FORGE(0.72), np.exp(-((r - ring_r) / (0.025 + 0.02 * u)) ** 2) * (1 - u) ** 1.3 * 0.75))
    thin = 1 - 0.6 * sstep(0.15, 1.0, u)
    o = np.zeros_like(r)
    i_ = np.zeros_like(r)
    for i in range(6):
        a0 = 0.3 + i * math.pi / 3
        L = (0.5 + 0.4 * grow) * (1 - 0.25 * u) * (1.0 if i % 2 == 0 else 0.75)
        o = np.maximum(o, spike(x, y, a0, L, 0.065 * thin, 1.4, aa))
        i_ = np.maximum(i_, spike(x, y, a0, L * 0.8, 0.024 * thin, 1.4, aa))
    layers += [(FORGE(0.78), o * k), (WHITE, i_ * k)]
    if f <= 2:
        K = 6
        outs = rng.uniform(0.2, 0.32, K)
        Rst = star_shape(r, th, K, 0.1, outs * grow, 0.3, 2.0) * (1 - 0.3 * f / 2)
        shape = 1 - sstep(Rst - aa, Rst + aa, r)
        layers.append((FORGE(0.9), shape))
        layers.append((WHITE, shape * (1 - sstep(Rst * 0.45, Rst * 0.9, r))))
    items = []
    for j in range(16):
        a0 = rng.uniform(0, 2 * math.pi)
        v = rng.uniform(0.5, 1.0)
        d0 = 0.12 + 0.75 * v * (1 - (1 - u) ** 1.5)
        hx, hy = d0 * math.cos(a0), d0 * math.sin(a0) - 0.15 * u * u
        L = 0.06 * v
        items.append((hx, hy, hx - L * math.cos(a0), hy - L * math.sin(a0), 0.008, (1 - u) ** 1.1))
    layers.append((FORGE(0.9), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.08 + 0.05 * grow)) ** 2) * k * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.7 * k)
    return rgb, a * edge_fade(x, y)


def flash():
    return [to_img(*flash_frame(f)) for f in range(FLASH_FRAMES)]


# ------------------------------------------------------------------ 모이는 열 (256 · 되풀이)

CHARGE_FRAMES = 10


def charge_frame(f, n=256, seed=271):
    """거대한 칼 끝 · 치켜든 칼에 모이는 열 (되풀이): 숨쉬는 흰 노랑 심 + 주황 빛 무리 · 바깥에서 안으로 휘감겨 드는 불티 줄기 · 도는 톱니 고리 조각 셋"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / CHARGE_FRAMES
    aa = 1.6 / n
    pulse = 0.88 + 0.12 * math.sin(ph * 2 * math.pi * 2)
    layers = [(FORGE(0.62 + 0.28 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.36 * pulse)) ** 2) * 0.75)]
    rr = 0.64
    seg = np.zeros_like(r)
    for i in range(3):
        a0 = i * 2 * math.pi / 3 + ph * 2 * math.pi / 3
        dd = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        seg = np.maximum(seg, 1 - sstep(0.55, 0.7, dd))
    teeth = (np.cos((th - ph * 2 * math.pi / 3) * 30) > 0.2) * sstep(rr + 0.004, rr + 0.01, r) * (1 - sstep(rr + 0.035, rr + 0.042, r))
    layers.append((FORGE(0.8), (np.exp(-((r - rr) / 0.014) ** 2) + teeth * 0.8) * seg * 0.9))
    items = []
    for i in range(14):
        a0 = rng.uniform(0, 2 * math.pi)
        q = (rng.uniform(0, 1) + ph) % 1.0
        dh = 0.95 - 0.8 * q
        ah = a0 + q * 1.2               # 휘감김
        hx, hy = dh * math.cos(ah), dh * math.sin(ah)
        dt = min(0.98, dh + 0.12)
        at = ah - 0.15
        fade = math.sin(math.pi * min(1.0, q * 1.15)) ** 0.6
        items.append((hx, hy, dt * math.cos(at), dt * math.sin(at), 0.01 * (0.6 + 0.4 * q), fade))
    layers.append((FORGE(0.86), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.12 * pulse)) ** 2) * 1.25, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.035, gain=0.75)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


def charge():
    return [to_img(*charge_frame(f)) for f in range(CHARGE_FRAMES)]


# ------------------------------------------------------------------ 불티 · 쇠 조각 (128) · 불꽃 (128 × 256)

SPARK_FRAMES = 4


def spark():
    """불티 (둥근 — 카메라를 보는 그림이라 어느 쪽에서도 같은 꼴): 흰 심 · 노랑 · 주황 빛, 네 칸 깜박임"""
    n = 128
    x, y = grid(n)
    r = np.hypot(x, y)
    out = []
    for f in range(SPARK_FRAMES):
        k = [1.0, 0.78, 0.92, 0.66][f]
        halo = np.exp(-(r / (0.4 * (0.9 + 0.1 * k))) ** 2) * 0.32
        mid = np.exp(-(r / (0.22 * k)) ** 2) * 0.95
        core = np.exp(-(r / (0.1 * k)) ** 2) * 1.35
        rgb, a = compose([(FORGE(0.6), halo), (FORGE(0.84), mid), (WHITE, np.clip(core, 0, 1))], n)
        out.append(to_img(rgb, a * (1 - sstep(0.88, 0.99, r))))
    return out


SHARD_FRAMES = 8


def shard():
    """달아오른 쇠 조각 (칼이 부서짐): 납작한 조각이 구르는 여덟 칸 — 빛 받는 면은 푸른 강철, 어두운 면은 검은 쇠, 깨진 가장자리는 주황으로 굵게 달아오름 · 둘레 주홍 빛"""
    n = 128
    x, y = grid(n)
    aa = 1.6 / n
    poly = np.array([[-0.66, -0.06, 0], [-0.08, 0.32, 0], [0.64, 0.1, 0], [0.3, -0.34, 0]])
    light = np.array([-0.4, 0.75, 0.55])
    light = light / np.linalg.norm(light)
    out = []
    for f in range(SHARD_FRAMES):
        a1 = f / SHARD_FRAMES * 2 * math.pi
        rx = np.array([[1, 0, 0], [0, math.cos(a1), -math.sin(a1)], [0, math.sin(a1), math.cos(a1)]])
        a2 = a1 * 0.5 + 0.3
        rz = np.array([[math.cos(a2), -math.sin(a2), 0], [math.sin(a2), math.cos(a2), 0], [0, 0, 1]])
        m = rz @ rx
        P = poly @ m.T
        nrm = m @ np.array([0, 0, 1.0])
        lit = abs(float(nrm @ light))
        facing = abs(float(nrm[2]))
        px, py = P[:, 0], P[:, 1]
        k = len(px)
        orient = np.sign((px[1] - px[0]) * (py[2] - py[0]) - (py[1] - py[0]) * (px[2] - px[0])) or 1.0
        sd = np.full_like(x, 9.0)
        for i in range(k):
            x0, y0, x1, y1 = px[i], py[i], px[(i + 1) % k], py[(i + 1) % k]
            ex, ey = x1 - x0, y1 - y0
            el = math.hypot(ex, ey) + 1e-9
            sd = np.minimum(sd, ((x - x0) * ey - (y - y0) * ex) / el * -orient)
        body = sstep(-aa, aa, sd) * (0.35 + 0.65 * min(1.0, facing * 1.6 + 0.25))
        edge = np.exp(-(np.clip(sd, 0, None) / 0.06) ** 2) * body
        shade = lit * 0.75 + 0.25 * (y * 0.5 + 0.5)
        halo = np.exp(-(np.clip(-sd, 0, None) / 0.12) ** 2) * (sd < 0) * 0.55
        rgb, a = compose([(FORGE(0.55), halo), (STEEL(np.clip(shade * 0.85, 0, 1)), body), (FORGE(0.82), edge)], n)
        out.append(to_img(rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))))
    return out


FLAME_FRAMES = 10


def flame_frame(f, w=128, h=256, seed=281):
    """단조 불꽃 (포지 브레이커 — 땅에서 솟는 칼 둘레): 아래 흰 노랑 심 → 주황 → 주홍 → 검붉은 끝, 혀 셋 (가운데 굵게 · 곁 둘) 이 솟았다 사그라듦 · 위로 오르는 불티 몇"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
    u = f / (FLAME_FRAMES - 1)
    aa = 1.6 / h
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = (np.arange(h) + 0.5) / h * 2 - 1
    x, y = np.meshgrid(xs, -ys)
    x = x * 0.5
    base_y = -0.9
    grow = 1 - (1 - min(1.0, (f + 1) / 3.5)) ** 2
    shrink = sstep(0.5, 1.0, u)
    Hm = (0.55 + 1.2 * grow) * (1 - 0.6 * shrink)
    t = f * 0.4
    total = np.zeros_like(x)
    temp = np.zeros_like(x)
    for (ox, hk, wk, ph) in [(0.0, 1.0, 0.22, 0.0), (-0.15, 0.6, 0.13, 1.9), (0.16, 0.68, 0.12, 3.3)]:
        Hh = Hm * hk
        yy = (y - base_y) / max(Hh, 1e-3)
        sway = 0.08 * np.sin(yy * 4.6 + t * 2.2 + ph) * yy + (N.fbm(yy * 2.5 + ph, t * 0.8, ph, 3) - 0.5) * 0.2 * yy
        cx = ox + sway
        wid = wk * 1.5 * np.clip(yy + 0.06, 0, 1) ** 0.32 * np.clip(1 - yy, 0, 1) ** 1.0
        wid = wid * (0.85 + 0.3 * (N.fbm(yy * 6 + ph, x * 3, t, 3) - 0.5))
        d = np.abs(x - cx) / np.maximum(wid, 1e-3)
        inside = (yy > -0.08) & (yy < 1.0)
        m = np.where(inside, 1 - sstep(0.82, 1.02, d), 0.0) * sstep(-0.08, 0.04, yy)
        cut = N.fbm(x * 5 + ph, y * 4 - t * 2.4, t, 4)
        m = m * (1 - sstep(0.55, 0.67, cut) * sstep(0.55, 0.9, yy))
        tt = np.clip(1 - np.sqrt((d * 0.95) ** 2 + ((yy - 0.14) / 0.9) ** 2), 0, 1) * 1.08 + 0.05
        total = np.maximum(total, m)
        temp = np.maximum(temp, m * np.clip(tt, 0, 1))
    T = banded(np.clip(temp * 1.12 + 0.1, 0, 1), [0.24, 0.4, 0.54, 0.68, 0.82, 0.92])
    fade = 1 - sstep(0.8, 1.0, u)
    glow = np.exp(-((x * 3.4) ** 2 + ((y - base_y - Hm * 0.3) / (Hm * 0.55 + 0.15)) ** 2) * 1.6) * 0.42 * fade
    sp = np.zeros_like(x)
    for j in range(5):
        sx0 = rng.uniform(-0.2, 0.2)
        q = (u * 1.4 + rng.uniform(0, 0.6)) % 1.0
        px = sx0 + 0.05 * math.sin(q * 6 + j)
        py = base_y + 0.2 + q * (Hm + 0.4)
        sp = np.maximum(sp, np.exp(-((x - px) ** 2 + (y - py) ** 2) / 0.0004) * (1 - q) * fade)
    rgb, a = compose([(FORGE(0.45), glow), (FORGE(0.3 + 0.68 * T), total * fade), (FORGE(0.92), np.clip(sp * 1.4, 0, 1))], (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.03, gain=0.7 * fade)
    a = a * (1 - sstep(0.94, 0.99, np.abs(x) * 2)) * (1 - sstep(0.94, 0.99, np.abs(y)))
    return rgb, a


def flame():
    return [to_img(*flame_frame(f)) for f in range(FLAME_FRAMES)]


# ------------------------------------------------------------------ 그을음 · 불티 (칼이 꽂힘 · 256)

DUST_FRAMES = 8


def dust_frame(f, n=256, seed=291):
    """칼이 땅에 꽂힘 · 솟음 (아이언 템페스트 들판 · 포지 브레이커 칼): 밑에서 주황 번쩍 → 검은 그을음 · 흙먼지가 옆 · 위로 퍼지며 옅어짐 (아랫면 불빛) · 튀는 불티.
    땅 자리 = 그림 아래쪽 1/4"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
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
    col = SOOT(light) * (1 - 0.45 * low)[..., None] + FORGE(0.5) * (0.45 * low)[..., None]
    layers = [(col, dmask * 0.8)]
    if f <= 3:
        k = 1 - f / 4.0
        layers.insert(0, (FORGE(0.62), np.exp(-((x / 0.35) ** 2 + ((y - gy) / 0.12) ** 2)) * k * 0.85))
        layers.append((WHITE, np.exp(-((x / 0.12) ** 2 + ((y - gy) / 0.05) ** 2)) * k))
    items = []
    for j in range(14):
        a = rng.uniform(0.2, math.pi - 0.2)
        v = rng.uniform(0.5, 1.0)
        px = math.cos(a) * v * 0.9 * u
        py = gy + math.sin(a) * v * 1.0 * u - 0.9 * u * u
        vx, vy = math.cos(a) * v * 0.9, math.sin(a) * v - 1.8 * u
        nn = math.hypot(vx, vy) + 1e-6
        if py > gy - 0.02 and u > 0.06:
            items.append((px, py, px - vx / nn * 0.06, py - vy / nn * 0.06, 0.008, 1 - u))
    layers.append((FORGE(0.88), np.clip(streaks(x, y, items, aa) * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    return rgb, a * edge_fade(x, y, 0.9, 0.99)


def dust():
    return [to_img(*dust_frame(f)) for f in range(DUST_FRAMES)]


# ------------------------------------------------------------------ 목록 (tools/vfx_sprites.py 가 더함)

def sprites():
    p = LIB + "_"
    return (tiled(p + "slash", slash) + tiled(p + "xslash", xslash) + tiled(p + "boom", boom) + tiled(p + "shock", shock) + tiled(p + "crack", crack)
            + tiled(p + "ring", ring)
            + [(p + "boom_s", boom_s, True), (p + "shock_s", shock_s, True), (p + "crack_s", crack_s, True), (p + "flash", flash, True),
               (p + "charge", charge, True), (p + "spark", spark, True), (p + "shard", shard, True), (p + "flame", flame, True), (p + "dust", dust, True)])


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
        allsets = {"slash": slash, "xslash": xslash, "boom": boom, "boom_s": boom_s, "shock": shock, "crack": crack, "ring": ring, "flash": flash,
                   "charge": charge, "small": lambda: spark() + shard(), "flame": flame, "dust": dust}
        preview({k: v() for k, v in allsets.items() if only is None or k in only}, out)
