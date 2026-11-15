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
    s_alpha = cloud * puff * (0.8 - 0.32 * u) * (1 - sstep(0.86, 1.0, u))
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
    f_alpha = fire * np.clip(T * 2.2 + 0.2, 0, 1) * (1 - sstep(0.8, 1.0, u))
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


# ------------------------------------------------------------------ 땅 충격파 (2 × 2 — 땅에 눕혀 씀)

SHOCK_FRAMES = 12


def shock_frame(f, n=512, seed=21):
    """땅의 충격파 (위에서 봄): 푸른 흰빛 앞 날 + 진홍 · 주황 뒤 빛이 0.12 → 0.96 으로 번지며 얇아지고 옅어짐 · 앞 날 둘레 흙먼지 결 · 바깥으로 긁힌 줄"""
    N = Noise3(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (SHOCK_FRAMES - 1)
    R = 0.12 + 0.84 * (1 - (1 - u) ** 2.4)
    w = 0.05 * (1 - u) + 0.012
    jag = (N.fbm(np.cos(th) * 3 + 2, np.sin(th) * 3, f * 0.3, 3) - 0.5) * 0.04
    d = r - (R + jag)
    front = np.exp(-(d / (w * 0.45)) ** 2) * (1 - u) ** 0.7
    trail = np.exp(-(np.clip(-d, 0, None) / (w * 3.2 + 0.05)) ** 1.4) * (d < 0) * (1 - u) ** 1.2 * 0.75
    dust = (N.fbm(x * 6 + 5, y * 6, f * 0.4, 4) - 0.35) * np.exp(-(d / (w * 2.5)) ** 2) * (1 - u) * 0.9
    lines = np.zeros_like(r)
    rng = np.random.default_rng(seed)
    for i in range(28):
        a0 = rng.uniform(0, 2 * math.pi)
        dth = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi) * r
        lines = np.maximum(lines, (1 - sstep(0.002, 0.007, dth)) * sstep(R * 0.55, R * 0.8, r) * (1 - sstep(R * 0.95, R * 1.05, r)))
    lines *= (1 - u) * 0.7
    layers = [(FIRE(0.45 + 0.35 * trail), trail), (SMOKE(0.8) * 0.6 + H("#7A3A2A") * 0.4, np.clip(dust, 0, 1) * 0.6),
              (FIRE(0.8), lines), (CYAN(0.6 + 0.4 * front), front)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.02, gain=0.8 * (1 - u))
    a = a * (1 - sstep(0.93, 0.99, r))
    return rgb, a


def shock():
    return [to_img(*shock_frame(f)) for f in range(SHOCK_FRAMES)]


# ------------------------------------------------------------------ 섬광 (1 장 256)

FLASH_FRAMES = 8


def flash_frame(f, n=256, seed=31):
    """쏠 때 · 꽂힐 때 섬광: 푸른 흰 긴 빛살 넷 + 진홍 짧은 빛살 넷 (45°) + 흰 심 + 퍼지는 고리 + 튀는 불티 — 칸마다 커졌다 가늘어짐"""
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (FLASH_FRAMES - 1)
    k = (1 - u) ** 1.2
    core = np.exp(-(r / (0.12 + 0.1 * u)) ** 2) * k
    rays_c = np.zeros_like(r)
    rays_r = np.zeros_like(r)
    for i in range(8):
        a0 = i * math.pi / 4
        long_ = i % 2 == 0
        L = (0.98 if long_ else 0.62) * (0.55 + 0.45 * min(1.0, u * 3))
        d_ang = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        w = (0.01 + 0.035 * np.clip(1 - r / L, 0, 1)) * (1 - 0.5 * u)
        ray = (1 - sstep(0.0, w, d_ang * np.maximum(r, 1e-3))) * (1 - sstep(L * 0.55, L, r))
        if long_:
            rays_c = np.maximum(rays_c, ray)
        else:
            rays_r = np.maximum(rays_r, ray)
    ring_r = 0.18 + 0.7 * u
    ring = np.exp(-((r - ring_r) / (0.025 + 0.02 * u)) ** 2) * (1 - u) ** 1.5
    layers = [(FIRE(0.55 + 0.3 * rays_r), rays_r * k), (CYAN(0.55 + 0.45 * rays_c), rays_c * k), (FIRE(0.8), ring * 0.8),
              (np.ones((n, n, 3)) * H("#FFFBEA"), core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.045, gain=0.9 * k)
    a = a * (1 - sstep(0.92, 0.99, np.maximum(np.abs(x), np.abs(y))))
    return rgb, a


def flash():
    return [to_img(*flash_frame(f)) for f in range(FLASH_FRAMES)]


# ------------------------------------------------------------------ 모으는 빛 (1 장 256 · 되풀이)

CHARGE_FRAMES = 12


def charge_frame(f, n=256, seed=41):
    """시위를 당기는 동안 모이는 빛 (되풀이): 진홍 · 금빛 나선 줄기 넷이 가운데로 감겨 들어옴 · 안으로 빨려 드는 빛 점 · 맥박치는 흰 금빛 심 · 바깥 옅은 고리"""
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / CHARGE_FRAMES
    arms = np.zeros_like(r)
    for i in range(4):
        a = th - (i * math.pi / 2) + 2 * math.pi * ph + np.log(np.maximum(r, 0.02)) * 2.6
        d = np.abs((a + math.pi) % (2 * math.pi) - math.pi) * r
        arms = np.maximum(arms, (1 - sstep(0.004, 0.03 * r + 0.006, d)) * sstep(0.05, 0.2, r) * (1 - sstep(0.6, 0.95, r)))
    dots = np.zeros_like(r)
    rng = np.random.default_rng(seed)
    for i in range(30):
        a0 = rng.uniform(0, 2 * math.pi)
        s0 = rng.uniform(0, 1)
        rr = 0.92 * (1 - ((s0 + ph) % 1.0)) + 0.06
        px, py = rr * math.cos(a0 + rr * 2.0), rr * math.sin(a0 + rr * 2.0)
        dd = np.hypot(x - px, y - py)
        dots = np.maximum(dots, np.exp(-(dd / 0.012) ** 2) * (0.3 + 0.7 * (1 - rr)))
    pulse = 0.85 + 0.15 * math.sin(ph * 2 * math.pi * 2)
    core = np.exp(-(r / (0.16 * pulse)) ** 2)
    halo = np.exp(-(r / 0.42) ** 2) * 0.35
    ring = np.exp(-((r - 0.82) / 0.02) ** 2) * (0.35 + 0.25 * math.sin(ph * 2 * math.pi))
    layers = [(FIRE(0.42), halo), (FIRE(0.5 + 0.4 * arms), arms * 0.95), (CYAN(0.75), ring * 0.7), (FIRE(0.92), dots),
              (np.ones((n, n, 3)) * H("#FFF4D0"), core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.04, gain=0.8)
    a = a * (1 - sstep(0.9, 0.99, r))
    return rgb, a


def charge():
    return [to_img(*charge_frame(f)) for f in range(CHARGE_FRAMES)]


# ------------------------------------------------------------------ 빛 무리 · 불티 · 그을린 땅

def glow():
    """빛 무리 (3D 칼 · 보물 둘레 — 블룸 흉내, 1 장): 흰 금빛 심 → 진홍 → 투명 (두 겹 가우스)"""
    n = TILE
    x, y = grid(n)
    r = np.hypot(x, y)
    a = np.clip(np.exp(-(r / 0.5) ** 2) * 0.55 + np.exp(-(r / 0.2) ** 2) * 0.55, 0, 1)
    t = np.clip(1 - r / 0.8, 0, 1)
    col = FIRE(0.45 + 0.5 * t ** 1.4)
    a = a * (1 - sstep(0.85, 0.99, r))
    return [to_img(col, a)]


def glow_cyan():
    """푸른 흰 빛 무리 (투영의 빛 — 칼이 나타날 때 · 투영 선 둘레)"""
    n = TILE
    x, y = grid(n)
    r = np.hypot(x, y)
    a = np.clip(np.exp(-(r / 0.48) ** 2) * 0.5 + np.exp(-(r / 0.16) ** 2) * 0.6, 0, 1)
    col = CYAN(0.35 + 0.65 * np.clip(1 - r / 0.7, 0, 1))
    a = a * (1 - sstep(0.85, 0.99, r))
    return [to_img(col, a)]


def spark():
    """불티 하나 (길쭉한 빛 — 1 장 128): 흰 금빛 심 · 주황 꼬리"""
    n = 128
    x, y = grid(n)
    d = np.hypot(x * 4.0, np.where(y > 0, y * 1.2, y * 0.55))
    a = np.clip(np.exp(-(d / 0.5) ** 2) * 1.1, 0, 1)
    col = FIRE(0.62 + 0.38 * np.clip(1 - d, 0, 1))
    return [to_img(col, a)]


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
            + [("pb_flash", flash, True), ("pb_charge", charge, True), ("pb_glow", glow, True), ("pb_glow_cyan", glow_cyan, True), ("pb_spark", spark, True)])


def preview(out):
    os.makedirs(out, exist_ok=True)
    bgs = {"dark": (0.06, 0.06, 0.09), "grass": (0.36, 0.5, 0.26), "dusk": (0.42, 0.3, 0.38)}
    sets = {"boom": boom(), "shock": shock(), "flash": flash(), "charge": charge(), "crack": crack(), "glow": glow() + glow_cyan() + spark()}
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
