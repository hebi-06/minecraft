#!/usr/bin/env python3
"""2026-11-15 시범 2D 프레임 애니메이션 (사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐 나는 압도적인 퀄을 중요하게 여김" — 팬텀 버스트):
빛 · 폭발 · 충격파 · 섬광 · 모으는 빛 · 빛 무리 · 불티 · 그을린 땅을 미리 렌더링한 칠한 그림 (colored — 글자 색 흰색으로 그대로).

- 글자 표시 (text display) 는 명암 계산이 없어 칠한 색이 그대로 나옴. 글꼴 텍스처는 가장 가까운 점 (확대하면 픽셀이 커짐) → 화면에서 크게 보이는 것은
  2 × 2 조각 (512 픽셀 — 조각마다 256 글자) 으로 나눠 띄움 (PhantomArcher.tiled).
- 그림: 시간에 따라 바뀌는 3D 값 잡음 (x · y · t) + 좌표 비틀기 → 불덩이의 결, 온도 → 셀 띠 색 램프 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인 → 보랏빛 연기),
  밝은 곳은 흐리게 번진 빛 (블룸) 을 더함, 연기는 잡음 문턱이 올라가며 흩어짐.
- 팔레트: 진홍 폭발 (팬텀 아처) + 푸른 흰빛 (투영의 빛 — 충격파 앞 날 · 섬광 빛살) — 한 가지 색이 아니게.

  python3 tools/vfx_pilot_art.py --preview 폴더   # 칸마다 어두운 · 풀밭 · 노을 바탕에 그려 봄
tools/vfx_sprites.py 가 SPRITES 에 더함 (colored).
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

TILE = 256


# ------------------------------------------------------------------ 잡음

class Noise3:
    """값 잡음 (x · y · t — 격자 64³ 반복, 부드러운 이음)"""

    def __init__(self, seed, n=64):
        self.n = n
        self.g = np.random.default_rng(seed).random((n, n, n))

    def __call__(self, x, y, t):
        n = self.n
        xi, yi, ti = np.floor(x).astype(int), np.floor(y).astype(int), int(math.floor(t))
        fx, fy, ft = x - xi, y - yi, t - ti
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        ft = ft * ft * (3 - 2 * ft)
        x0, y0, t0 = xi % n, yi % n, ti % n
        x1, y1, t1 = (x0 + 1) % n, (y0 + 1) % n, (t0 + 1) % n
        g = self.g

        def plane(tt):
            a = g[x0, y0, tt] * (1 - fx) + g[x1, y0, tt] * fx
            b = g[x0, y1, tt] * (1 - fx) + g[x1, y1, tt] * fx
            return a * (1 - fy) + b * fy
        return plane(t0) * (1 - ft) + plane(t1) * ft

    def fbm(self, x, y, t, oct=5, lac=2.0, gain=0.5):
        s, a, tot = 0.0, 1.0, 0.0
        for o in range(oct):
            s = s + a * self(x * lac ** o + o * 17.3, y * lac ** o - o * 9.1, t * (1 + 0.3 * o) + o * 5.7)
            tot += a
            a *= gain
        return s / tot


def sstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def H(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def ramp(stops):
    """[(위치, '#색')] → f(t) (선형)"""
    P = np.array([p for p, _ in stops])
    C = np.stack([H(c) for _, c in stops])

    def f(t):
        t = np.clip(np.asarray(t, float), P[0], P[-1])
        out = np.empty(t.shape + (3,))
        for ch in range(3):
            out[..., ch] = np.interp(t, P, C[:, ch])
        return out
    return f


def banded(t, edges, soft=0.035):
    """온도를 셀 띠로 (부드러운 경계) — 손으로 칠한 띠 느낌"""
    out = np.zeros_like(t)
    lo = 0.0
    for e in edges:
        out = out + (e - lo) * sstep(e - soft, e + soft, t)
        lo = e
    return out + (t - out) * 0.35


# 진홍 폭발 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인 → 보랏빛 연기)
FIRE = ramp([(0.0, "#1B0E1C"), (0.12, "#3A0F24"), (0.26, "#7A0D2A"), (0.4, "#C8182F"), (0.52, "#F2412F"), (0.64, "#FF7A2E"),
             (0.76, "#FFB348"), (0.86, "#FFE08C"), (0.94, "#FFF6D6"), (1.0, "#FFFFFF")])
SMOKE = ramp([(0.0, "#160C14"), (0.45, "#33192A"), (0.8, "#5A3446"), (1.0, "#7A5060")])
CYAN = ramp([(0.0, "#2A5CFF"), (0.45, "#6FD4FF"), (0.8, "#D8F6FF"), (1.0, "#FFFFFF")])


def grid(n):
    g = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(g, -g)
    return x, y


def compose(layers, n):
    """[(rgb, a)] 를 차례로 겹침 (앞이 아래) → 곧은 알파 RGBA"""
    rgb = np.zeros((n, n, 3))
    a = np.zeros((n, n))
    for c, al in layers:
        al = np.clip(al, 0, 1)
        c = np.broadcast_to(c, (n, n, 3))
        oa = al + a * (1 - al)
        rgb = (c * al[..., None] + rgb * (a * (1 - al))[..., None]) / np.maximum(oa, 1e-9)[..., None]
        a = oa
    return rgb, a


def bloom(rgb, a, n, thresh=0.62, sigma=0.035, gain=0.9, tint=None):
    """밝은 곳 (밝기 > thresh) 을 흐리게 번진 빛으로 — 몸 바깥에 빛 무리 (알파) · 몸 안은 밝게"""
    lum = (rgb * np.array([0.3, 0.55, 0.15])).sum(-1) * a
    e = np.clip((lum - thresh) / (1 - thresh), 0, 1)
    src = rgb * e[..., None]
    s = sigma * n
    blur = np.stack([ndimage.gaussian_filter(src[..., i], s) for i in range(3)], -1)
    ba = ndimage.gaussian_filter(e, s)
    ba2 = ndimage.gaussian_filter(e, s * 2.6) * 0.6
    gcol = blur / np.maximum(ba, 1e-6)[..., None]
    if tint is not None:
        gcol = gcol * 0.5 + tint * 0.5
    ga = np.clip((ba * 1.6 + ba2) * gain, 0, 1)
    gl = np.clip(gcol, 0, 1)
    # 빛 무리 (아래) + 몸 (위)
    return compose([(gl, ga), (rgb, a)], n)


def to_img(rgb, a):
    out = np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], -1)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def tiles(img, k=2):
    """한 칸 그림 (k·256) → 조각 k × k (위 왼쪽부터 줄마다)"""
    out = []
    for r in range(k):
        for c in range(k):
            out.append(img.crop((c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE)))
    return out


# ------------------------------------------------------------------ 폭발 (2 × 2)

BOOM_FRAMES = 20


def boom_frame(f, n=512, seed=11):
    """진홍 대폭발 칸 f / BOOM_FRAMES (칸을 꽉 채움 — 반지름 0.86 까지): 0 ~ 3 흰 섬광 (원판 · 빛살 여덟 — 푸른 흰 · 진홍) → 1 ~ 8 거칠게 부푸는 불덩이
    (가장자리 불혀 · 흰 심) → 6 ~ 19 불은 안으로 식어 줄고 바깥은 불빛을 받은 와인 · 보랏빛 연기 구름이 떠오르며 커지다 흩어짐 + 바깥으로 튀는 불티 줄기"""
    N = Noise3(seed)
    N2 = Noise3(seed + 1)
    x, y = grid(n)
    u = f / (BOOM_FRAMES - 1)
    layers = []
    R = 0.2 + 0.58 * (1 - (1 - min(1.0, f / 7.0)) ** 2.4) + 0.08 * max(0.0, (f - 7) / 12.0)
    rise = 0.08 * max(0.0, (f - 5) / 14.0) ** 1.2
    yy = y - rise
    r = np.hypot(x, yy)
    th = np.arctan2(yy, x)
    t = f * 0.2
    wx = N.fbm(x * 2.6 + 3, yy * 2.6, t, 4)
    wy = N.fbm(x * 2.6 - 7, yy * 2.6 + 2, t + 9, 4)
    q = N2.fbm(x * 3.6 + wx * 1.7, yy * 3.6 + wy * 1.7, t * 1.2, 5)
    lick = N.fbm(np.cos(th) * 2.4 + 11, np.sin(th) * 2.4 - 4, t * 1.4, 3)
    # 연기 구름 (바깥 · 크게) — 불빛을 받은 와인 · 보랏빛, 뒤로 갈수록 잡음 문턱이 올라가며 흩어짐
    lick2 = N2.fbm(np.cos(th) * 1.5 - 3, np.sin(th) * 1.5 + 6, t * 0.8, 3)
    q2 = N.fbm(x * 2.0 + wx * 0.9 + 21, yy * 2.0 + wy * 0.9, t * 0.7, 4)
    Rs = R * (0.98 + 0.22 * u) * (0.7 + 0.55 * lick2 + 0.12 * (q2 - 0.5))
    cloud = 1 - sstep(Rs * 0.82, Rs * 1.02, r)
    thr = 0.12 + 0.34 * sstep(0.3, 1.0, u)
    puff = sstep(thr, thr + 0.22, q2 + 0.18 * (1 - r / np.maximum(Rs, 1e-3)))
    s_alpha = cloud * puff * (0.62 - 0.3 * u) * (1 - sstep(0.7, 1.0, u))
    # 불 (안 — 식으며 줄어듦)
    Rf = R * (1.0 - 0.5 * sstep(0.25, 0.95, u)) * (0.82 + 0.34 * lick + 0.12 * (q - 0.5))
    fire = 1 - sstep(Rf * 0.86, Rf * 1.02, r)
    cool = sstep(0.15, 1.0, u) * 0.5
    T = (1 - np.clip(r / np.maximum(Rf, 1e-3), 0, 1)) ** 0.75 * 0.8 + (q - 0.5) * 0.55 + 0.34 - cool
    T = T + 0.55 * (1 - sstep(0.0, 0.16, u)) * np.exp(-(r / (R * 0.6)) ** 2)
    Tb = banded(np.clip(T, 0, 1), [0.18, 0.32, 0.46, 0.6, 0.74, 0.86])
    heat_near = np.exp(-np.clip(r - Rf, 0, None) / (0.06 + 0.1 * u)) * (1 - 0.6 * u)
    shade = np.clip(0.45 + 0.5 * (yy / np.maximum(Rs, 1e-3)) + 0.35 * (q2 - 0.5), 0, 1)  # 위가 밝은 뭉게 (빛 받은 연기)
    s_col = SMOKE(shade) * (1 - 0.7 * heat_near[..., None]) + FIRE(0.3 + 0.2 * heat_near) * (0.7 * heat_near[..., None])
    layers.append((s_col, s_alpha))
    # 2026-11-15 2판: 식은 불 (낮은 온도 — 검붉은 색) 은 옅게 · 불은 u 0.45 ~ 0.78 에 사라짐 (1인칭 영상: 늦은 칸의 검붉은 덩이가 화면을 오래 덮었음)
    f_alpha = fire * np.clip(T * 2.2 + 0.2, 0, 1) * np.clip((T - 0.12) * 4.0, 0, 1) * (1 - sstep(0.45, 0.78, u))
    layers.append((FIRE(Tb), f_alpha))
    # 흰 섬광 (처음): 원판 · 빛살 여덟 (긴 넷 푸른 흰 · 짧은 넷 진홍)
    if f <= 4:
        k = 1 - f / 5.0
        disk = np.exp(-(r / (0.24 + 0.14 * f / 4)) ** 2) * k
        rays = np.zeros_like(r)
        rays_w = np.zeros_like(r)
        for i in range(8):
            a0 = i * math.pi / 4 + 0.2
            long_ = i % 2 == 0
            L = (0.98 if long_ else 0.66) * (0.62 + 0.38 * f / 4)
            d_ang = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
            w = 0.012 + 0.032 * np.clip(1 - r / L, 0, 1)
            ray = (1 - sstep(0.0, w, d_ang * np.maximum(r, 1e-3))) * (1 - sstep(L * 0.6, L, r))
            if long_:
                rays = np.maximum(rays, ray)
            else:
                rays_w = np.maximum(rays_w, ray)
        layers.append((FIRE(0.62 + 0.3 * rays_w), rays_w * k))
        layers.append((CYAN(0.7 + 0.3 * rays), rays * k))
        layers.append((np.ones(r.shape + (3,)) * H("#FFFDF4"), np.clip(disk, 0, 1)))
    # 불티 줄기 (바깥으로)
    if 1 <= f <= 13:
        rng = np.random.default_rng(seed + 5)
        sp = np.zeros_like(r)
        for i in range(26):
            a0 = rng.uniform(0, 2 * math.pi)
            v = rng.uniform(0.55, 1.0)
            prog = 1 - (1 - min(1.0, f / 13.0)) ** 1.6
            d0 = 0.2 + v * 0.74 * prog
            L = 0.06 + 0.1 * v * (1 - 0.5 * prog)
            px, py = d0 * math.cos(a0), d0 * math.sin(a0) + rise - 0.05 * prog ** 2
            qx, qy = (d0 - L) * math.cos(a0), (d0 - L) * math.sin(a0) + rise - 0.05 * prog ** 2 * 0.8
            dx, dy = px - qx, py - qy
            ll = dx * dx + dy * dy
            s_ = np.clip(((x - qx) * dx + (y - qy) * dy) / ll, 0, 1)
            dd = np.hypot(x - (qx + s_ * dx), y - (qy + s_ * dy))
            sp = np.maximum(sp, (1 - sstep(0.005, 0.013, dd)) * (0.35 + 0.65 * s_) * (1 - f / 14.0))
        layers.append((FIRE(0.82 + 0.18 * sp), sp))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.56, sigma=0.03, gain=1.0 - 0.55 * u)
    edge = 1 - sstep(0.9, 0.98, np.maximum(np.abs(x), np.abs(y)))
    return rgb, a * edge


def boom():
    return [to_img(*boom_frame(f)) for f in range(BOOM_FRAMES)]


# ------------------------------------------------------------------ 모양 도구 (2판)

def spike(x, y, a0, L, w0, p=1.0, aa=0.01):
    """가운데에서 a0 방향으로 뻗는 뾰족한 빛살 (밑동 반폭 w0 → 끝 L 에서 0, 굵기 곡선 p) — 가장자리 부드럽게 aa"""
    ca, sa = math.cos(a0), math.sin(a0)
    along = x * ca + y * sa
    perp = np.abs(-x * sa + y * ca)
    t = np.clip(along / L, 0, 1)
    w = w0 * (1 - t) ** p
    return (1 - sstep(w - aa, w + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L - aa, L + aa, along))


def star_shape(r, th, K, r_in, outs, rot, sharp=2.2, jitter=None):
    """뾰족한 별 테두리 반지름 R(th) — 꼭짓점 K 개 (바깥 반지름 outs[i])"""
    prof = np.zeros_like(r)
    for i in range(K):
        a0 = i * 2 * math.pi / K + rot + (0 if jitter is None else jitter[i])
        d = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        hw = math.pi / K
        prof = np.maximum(prof, np.clip(1 - d / hw, 0, 1) ** sharp * outs[i])
    return r_in + prof


WHITE = H("#FFFDF6")


# ------------------------------------------------------------------ 땅 충격파 (2 × 2 — 땅에 눕혀 씀)

SHOCK_FRAMES = 12


def shock_frame(f, n=512, seed=21):
    """땅의 충격파 (위에서 봄 — 2판: 속이 빈 고리): 흰 심 앞 날 + 푸른 흰빛 · 바로 뒤 짧은 진홍 빛 띠 (속은 비움 — 1판은 속을 채워 갈색 접시처럼 보였음) ·
    앞 날 바깥 흙먼지 띠 · 앞 날에서 바깥으로 짧게 긁힌 빛 줄. 비스듬히 보여도 읽히게 고리를 굵게"""
    N = Noise3(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (SHOCK_FRAMES - 1)
    R = 0.14 + 0.8 * (1 - (1 - u) ** 2.2)
    jag = (N.fbm(np.cos(th) * 3 + 2, np.sin(th) * 3, f * 0.3, 3) - 0.5) * 0.035
    d = r - (R + jag)
    k = (1 - u) ** 0.9
    w = 0.03 * (1 - 0.5 * u) + 0.012
    core = np.exp(-(d / (w * 0.5)) ** 2) * k
    edge = np.exp(-(d / (w * 1.4)) ** 2) * k
    trail = np.exp(-(np.clip(-d, 0, None) / (0.09 + 0.05 * u)) ** 1.5) * (d < 0) * k
    trail = trail * (0.75 + 0.5 * (N.fbm(x * 5, y * 5, f * 0.5 + 3, 3) - 0.5))
    dust_n = N.fbm(x * 7 + 5, y * 7, f * 0.4, 4)
    dust = np.clip((dust_n - 0.3) * 2.2, 0, 1) * np.exp(-((d - w * 2.2) / (w * 2.6 + 0.02)) ** 2) * (1 - u) ** 0.6 * 0.75
    lines = np.zeros_like(r)
    rng = np.random.default_rng(seed)
    for i in range(36):
        a0 = rng.uniform(0, 2 * math.pi)
        L = rng.uniform(0.04, 0.11)
        dth = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi) * r
        lines = np.maximum(lines, (1 - sstep(0.002, 0.006, dth)) * sstep(R - 0.005, R + 0.01, r) * (1 - sstep(R + L * 0.6, R + L, r)))
    lines *= (1 - u) ** 1.2 * 0.85
    layers = [(SMOKE(0.55) * 0.55 + H("#4A2A22") * 0.45, dust),
              (FIRE(0.42 + 0.3 * trail), trail),
              (FIRE(0.82), lines),
              (CYAN(0.55 + 0.4 * edge), edge * 0.9),
              (WHITE, core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.018, gain=0.85 * k)
    a = a * (1 - sstep(0.93, 0.99, r))
    return rgb, a


def shock():
    return [to_img(*shock_frame(f)) for f in range(SHOCK_FRAMES)]


# ------------------------------------------------------------------ 섬광 (1 장 256)

FLASH_FRAMES = 8


def flash_frame(f, n=256, seed=31):
    """섬광 (2판 — 화면에서 또렷하게: 굵은 모양): 0 ~ 2 칸 단단한 흰 별 모양 (꼭짓점 열 — 금빛 테) 이 터지고 → 굵은 빛살 여덟 (긴 넷 흰 심 · 푸른 겉,
    짧은 넷 흰 심 · 금 · 진홍 겉) 이 가늘어지며 → 퍼지는 금빛 고리 · 부드러운 진홍 빛 무리"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (FLASH_FRAMES - 1)
    aa = 1.6 / n
    grow = min(1.0, (f + 1) / 2.0)
    k = (1 - u) ** 1.15
    rot = 0.2
    layers = []
    glow = np.exp(-(r / (0.34 + 0.18 * u)) ** 2) * 0.8 * k
    layers.append((FIRE(0.6 + 0.3 * np.exp(-(r / 0.22) ** 2)), glow))
    # 퍼지는 고리
    ring_r = 0.2 + 0.72 * (1 - (1 - u) ** 1.6)
    ring = np.exp(-((r - ring_r) / (0.03 + 0.025 * u)) ** 2) * (1 - u) ** 1.3
    layers.append((FIRE(0.78 + 0.12 * ring), ring * 0.9))
    # 빛살 여덟 (긴 넷 · 짧은 넷)
    thin = (1 - 0.65 * sstep(0.15, 1.0, u))
    long_len = (0.62 + 0.36 * grow) * (1 - 0.25 * u)
    short_len = (0.4 + 0.22 * grow) * (1 - 0.35 * u)
    outer_l = np.zeros_like(r)
    inner_l = np.zeros_like(r)
    outer_s = np.zeros_like(r)
    inner_s = np.zeros_like(r)
    for i in range(8):
        a0 = rot + i * math.pi / 4
        if i % 2 == 0:
            outer_l = np.maximum(outer_l, spike(x, y, a0, long_len, 0.07 * thin, 1.3, aa))
            inner_l = np.maximum(inner_l, spike(x, y, a0, long_len * 0.82, 0.03 * thin, 1.3, aa))
        else:
            outer_s = np.maximum(outer_s, spike(x, y, a0, short_len, 0.06 * thin, 1.1, aa))
            inner_s = np.maximum(inner_s, spike(x, y, a0, short_len * 0.75, 0.025 * thin, 1.1, aa))
    layers.append((FIRE(0.6), outer_s * k))
    layers.append((CYAN(0.62), outer_l * k))
    layers.append((FIRE(0.9), inner_s * k))
    layers.append((WHITE, inner_l * k))
    # 단단한 흰 별 모양 (처음 세 칸)
    if f <= 2:
        K = 10
        outs = rng.uniform(0.2, 0.42, K)
        jit = rng.uniform(-0.15, 0.15, K)
        R = star_shape(r, th, K, 0.12, outs * grow, rot + 0.15, 2.0, jit) * (1 - 0.35 * f / 2)
        shape = 1 - sstep(R - aa, R + aa, r)
        body = shape * sstep(R * 0.5, R * 0.95, r)
        layers.append((FIRE(0.86), shape))
        layers.append((WHITE, shape * (1 - body * 0.85)))
    core = np.exp(-(r / (0.09 + 0.05 * grow)) ** 2) * k * 1.3
    layers.append((WHITE, np.clip(core, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.7 * k)
    a = a * (1 - sstep(0.93, 0.995, np.maximum(np.abs(x), np.abs(y))))
    return rgb, a


def flash():
    return [to_img(*flash_frame(f)) for f in range(FLASH_FRAMES)]


# ------------------------------------------------------------------ 모으는 빛 (1 장 256 · 되풀이)

CHARGE_FRAMES = 12


def charge_frame(f, n=256, seed=41):
    """시위를 당기는 동안 칼끝에 모이는 빛 (2판 — 굵게, 되풀이 12칸): 맥박치는 흰 금빛 심 + 진홍 빛 무리 · 바깥에서 안으로 빨려 드는 굵은 빛 줄기
    (끝이 뾰족 — 흰 심 · 진홍 겉) · 천천히 도는 끊긴 고리 셋 (금빛)"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / CHARGE_FRAMES
    aa = 1.6 / n
    layers = []
    pulse = 0.88 + 0.12 * math.sin(ph * 2 * math.pi * 2)
    halo = np.exp(-(r / (0.36 * pulse)) ** 2) * 0.7
    layers.append((FIRE(0.5 + 0.35 * np.exp(-(r / 0.2) ** 2)), halo))
    # 끊긴 고리 셋 (돎)
    ring_r = 0.66
    ring = np.exp(-((r - ring_r) / 0.022) ** 2)
    gaps = np.zeros_like(r)
    for i in range(3):
        a0 = i * 2 * math.pi / 3 + ph * 2 * math.pi / 3
        dd = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        gaps = np.maximum(gaps, 1 - sstep(0.62, 0.78, dd))
    layers.append((FIRE(0.84), ring * gaps * 0.9))
    # 빨려 드는 빛 줄기 (열 개 — 시작 차례가 다름)
    outer = np.zeros_like(r)
    inner = np.zeros_like(r)
    for i in range(10):
        a0 = rng.uniform(0, 2 * math.pi)
        s0 = rng.uniform(0, 1)
        q = (s0 + ph) % 1.0                       # 0 → 1: 바깥 → 안
        head = 0.95 - 0.8 * q                     # 줄기 머리 (안쪽 끝)
        L = 0.16 + 0.12 * (1 - q)
        # 머리에서 바깥으로 뻗는 줄기 (끝이 뾰족 — 바깥쪽이 꼬리)
        ca, sa = math.cos(a0), math.sin(a0)
        along = x * ca + y * sa - head
        perp = np.abs(-x * sa + y * ca)
        t = np.clip(along / L, 0, 1)
        w = 0.026 * (1 - t) ** 0.8 * (0.6 + 0.4 * q)
        m = (1 - sstep(w - aa, w + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L - aa, L + aa, along))
        fade = np.sin(math.pi * min(1.0, q * 1.15)) ** 0.6
        outer = np.maximum(outer, m * fade)
        w2 = w * 0.4
        m2 = (1 - sstep(w2 - aa, w2 + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L * 0.8 - aa, L * 0.8 + aa, along))
        inner = np.maximum(inner, m2 * fade)
    layers.append((FIRE(0.58), outer))
    layers.append((FIRE(0.95), inner))
    core = np.exp(-(r / (0.12 * pulse)) ** 2) * 1.25
    layers.append((WHITE, np.clip(core, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.035, gain=0.75)
    a = a * (1 - sstep(0.9, 0.99, r))
    return rgb, a


def charge():
    return [to_img(*charge_frame(f)) for f in range(CHARGE_FRAMES)]


# ------------------------------------------------------------------ 불씨 · 칼 조각 (날아가는 작은 그림)

EMBER_FRAMES = 4


def ember():
    """불씨 (2판 — 1판 길쭉한 불티는 카메라를 보는 그림이라 늘 세로로 서서 촛불처럼 보였음 → 둥근 불씨): 흰 금빛 심 · 주황 빛 · 진홍 겉빛,
    네 칸 깜박임 (심 크기 · 밝기) — 1 장 128"""
    n = 128
    x, y = grid(n)
    r = np.hypot(x, y)
    out = []
    for f in range(EMBER_FRAMES):
        k = [1.0, 0.82, 0.94, 0.74][f]
        halo = np.exp(-(r / (0.62 * (0.9 + 0.1 * k))) ** 2) * 0.55
        mid = np.exp(-(r / (0.3 * k)) ** 2) * 0.95
        core = np.exp(-(r / (0.13 * k)) ** 2) * 1.3
        rgb, a = compose([(FIRE(0.5), halo), (FIRE(0.78), mid), (WHITE * 0.6 + H("#FFE7A0") * 0.4, np.clip(core, 0, 1))], n)
        a = a * (1 - sstep(0.88, 0.99, r))
        out.append(to_img(rgb, a))
    return out


SHARD_FRAMES = 8


def shard():
    """칼 조각 (2판 — 1판 3D 상자 조각은 1인칭에서 큰 회색 막대로 보였음): 납작한 강철 조각 (세모 · 네모가 섞인 꼴) 이 3D 로 구르는 모습을 여덟 칸으로 —
    빛 받는 면은 푸른 흰 강철, 어두운 면은 남색 강철, 깨진 가장자리는 진홍으로 달아오름 · 둘레 옅은 진홍 빛 (1 장 128 · 되풀이)"""
    n = 128
    x, y = grid(n)
    aa = 1.6 / n
    poly = np.array([[-0.62, -0.1, 0], [-0.1, 0.34, 0], [0.66, 0.12, 0], [0.32, -0.3, 0]])  # 조각 (평면)
    light = np.array([-0.4, 0.75, 0.55])
    light = light / np.linalg.norm(light)
    STEEL = ramp([(0.0, "#1B2240"), (0.35, "#3C4C7A"), (0.65, "#9DB4E0"), (0.9, "#E8F2FF"), (1.0, "#FFFFFF")])
    out = []
    for f in range(SHARD_FRAMES):
        a1 = f / SHARD_FRAMES * 2 * math.pi
        # 두 축으로 구름 (뒤집힘 + 돎)
        rx = np.array([[1, 0, 0], [0, math.cos(a1), -math.sin(a1)], [0, math.sin(a1), math.cos(a1)]])
        a2 = a1 * 0.5 + 0.6
        rz = np.array([[math.cos(a2), -math.sin(a2), 0], [math.sin(a2), math.cos(a2), 0], [0, 0, 1]])
        m = rz @ rx
        P = poly @ m.T
        nrm = m @ np.array([0, 0, 1.0])
        lit = abs(float(nrm @ light))
        facing = abs(float(nrm[2]))
        # 다각형 안 (화면에 비친 꼴)
        px, py = P[:, 0], P[:, 1]
        inside = np.ones_like(x)
        sd = np.full_like(x, 9.0)
        orient = np.sign((px[1] - px[0]) * (py[2] - py[0]) - (py[1] - py[0]) * (px[2] - px[0])) or 1.0
        for i in range(4):
            x0, y0, x1, y1 = px[i], py[i], px[(i + 1) % 4], py[(i + 1) % 4]
            ex, ey = x1 - x0, y1 - y0
            el = math.hypot(ex, ey) + 1e-9
            dist = ((x - x0) * ey - (y - y0) * ex) / el * -orient  # 안이 +
            sd = np.minimum(sd, dist)
        body = sstep(-aa, aa, sd) * (0.35 + 0.65 * min(1.0, facing * 1.6 + 0.25))
        edge = np.exp(-(np.clip(sd, 0, None) / 0.035) ** 2) * body
        shade = lit * 0.75 + 0.25 * (y * 0.5 + 0.5)
        col = STEEL(np.clip(shade, 0, 1))
        hot = FIRE(0.7)
        halo = np.exp(-(np.clip(-sd, 0, None) / 0.1) ** 2) * (sd < 0) * 0.5
        rgb, a = compose([(FIRE(0.5), halo), (col, body), (hot, edge * 0.9)], n)
        a = a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))
        out.append(to_img(rgb, a))
    return out


CRACK_FRAMES = 6


def crack_frame(f, n=512, seed=51):
    """그을린 땅 (위에서 봄 — 꽂힌 자리 · 터진 자리): 검붉게 탄 자국 + 가운데서 뻗은 금 (속이 주황으로 달아오름 → 식어 감) · 가장자리 재"""
    N = Noise3(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (CRACK_FRAMES - 1)
    burn = (1 - sstep(0.35, 0.8, r + (N.fbm(x * 4, y * 4, 1.0, 4) - 0.5) * 0.35)) * 0.85
    cracks = np.zeros_like(r)
    rng = np.random.default_rng(seed)
    for i in range(9):
        a = rng.uniform(0, 2 * math.pi)
        px, py = 0.0, 0.0
        L = rng.uniform(0.45, 0.85)
        steps = 7
        for s in range(steps):
            a += rng.uniform(-0.5, 0.5)
            qx, qy = px + math.cos(a) * L / steps, py + math.sin(a) * L / steps
            dx, dy = qx - px, qy - py
            ll = dx * dx + dy * dy
            ss = np.clip(((x - px) * dx + (y - py) * dy) / ll, 0, 1)
            dd = np.hypot(x - (px + ss * dx), y - (py + ss * dy))
            wid = 0.016 * (1 - s / steps) + 0.004
            cracks = np.maximum(cracks, 1 - sstep(wid * 0.5, wid, dd))
            px, py = qx, qy
    heat = (1 - u) ** 1.3
    layers = [(SMOKE(0.15) * 0.7 + H("#3A1208") * 0.3, burn * (0.9 - 0.4 * u)), (FIRE(0.5 + 0.4 * heat), cracks * (0.35 + 0.65 * heat))]
    rgb, a = compose(layers, n)
    if heat > 0.05:
        rgb, a = bloom(rgb, a, n, thresh=0.5, sigma=0.012, gain=0.7 * heat)
    a = a * (1 - sstep(0.9, 0.99, r))
    return rgb, a


def crack():
    return [to_img(*crack_frame(f)) for f in range(CRACK_FRAMES)]


# ------------------------------------------------------------------ 목록 (vfx_sprites 가 더함)

def _tiled(name, make, k=2):
    """한 그림 (k·256) 의 칸들을 조각 k × k 판으로: 이름_rc (r = 줄 · c = 칸)"""
    cache = {}

    def frames():
        if "f" not in cache:
            cache["f"] = make()
        return cache["f"]
    out = []
    for r in range(k):
        for c in range(k):
            def mk(r=r, c=c):
                return [im.crop((c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE)) for im in frames()]
            out.append(("%s_%d%d" % (name, r, c), mk, True))
    return out


def sprites():
    return (_tiled("pb_boom", boom) + _tiled("pb_shock", shock) + _tiled("pb_crack", crack)
            + [("pb_flash", flash, True), ("pb_charge", charge, True), ("pb_ember", ember, True), ("pb_shard", shard, True)])


def preview(out):
    os.makedirs(out, exist_ok=True)
    bgs = {"dark": (0.06, 0.06, 0.09), "grass": (0.36, 0.5, 0.26), "dusk": (0.42, 0.3, 0.38)}
    sets = {"boom": boom(), "shock": shock(), "flash": flash(), "charge": charge(), "crack": crack(), "small": ember() + shard()}
    for name, frames in sets.items():
        for bgn, bg in bgs.items():
            ims = []
            for im in frames:
                a = np.asarray(im, float) / 255
                c = a[..., :3] * a[..., 3:4] + np.array(bg) * (1 - a[..., 3:4])
                ims.append(Image.fromarray((c * 255).astype(np.uint8)).resize((256, 256), Image.LANCZOS))
            cols = 6
            rows = (len(ims) + cols - 1) // cols
            sheet = Image.new("RGB", (cols * 256, rows * 256), tuple(int(v * 255) for v in bg))
            for i, im in enumerate(ims):
                sheet.paste(im, ((i % cols) * 256, (i // cols) * 256))
            sheet.save(os.path.join(out, "%s_%s.png" % (name, bgn)))
    print(out)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
