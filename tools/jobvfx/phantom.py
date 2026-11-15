"""2026-11-15 팬텀 아처 전용 이펙트 그림 (그림판 pa_* — 사용자 "직업마다 전용으로 그릴래"). 시범 팬텀 버스트 (예전 tools/vfx_pilot_art.py — pb_*) 를 옮기고
직업의 다른 스킬 · 특성 · 평타가 쓸 그림을 더함. 색: 진홍 불 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인) · 보랏빛 연기 · 투영의 푸른 흰빛 · 강철.

- 큰 그림 (2 × 2 조각 512): pa_boom (대폭발 20칸) · pa_shock (땅 충격파 — 속이 빈 고리 12칸) · pa_crack (그을린 땅 6칸) · pa_ring (땅 고리 — 예고 · 테두리, 반짝임 4칸)
- 한 장 256: pa_boom_s (작은 폭발 14칸) · pa_shock_s (작은 충격파 12칸) · pa_crack_s · pa_flash (섬광 8칸) · pa_charge (모으는 빛 — 되풀이 12칸)
- 작은 그림: pa_ember (불씨 128 — 깜박임 4칸) · pa_shard (칼 조각 128 — 구름 8칸) · pa_flame (불꽃 혀 128 × 256 — 세로로 서는 그림, 솟았다 사그라듦 10칸)

  cd tools && python3 -m jobvfx.phantom --preview 폴더
"""
import math
import sys

import numpy as np
from scipy import ndimage

from .base import (TILE, WHITE, H, Noise3, banded, bloom, compose, grid, preview, ramp, spike, sstep, star_shape, tiled, to_img)

# 진홍 폭발 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인 → 보랏빛 연기)
FIRE = ramp([(0.0, "#1B0E1C"), (0.12, "#3A0F24"), (0.26, "#7A0D2A"), (0.4, "#C8182F"), (0.52, "#F2412F"), (0.64, "#FF7A2E"),
             (0.76, "#FFB348"), (0.86, "#FFE08C"), (0.94, "#FFF6D6"), (1.0, "#FFFFFF")])
SMOKE = ramp([(0.0, "#160C14"), (0.45, "#33192A"), (0.8, "#5A3446"), (1.0, "#7A5060")])
CYAN = ramp([(0.0, "#2A5CFF"), (0.45, "#6FD4FF"), (0.8, "#D8F6FF"), (1.0, "#FFFFFF")])
LIB = "pa"


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
        # 2026-11-15 2판 다듬기: 겉빛을 작고 옅게 (1인칭에서 눈앞을 지나는 불씨가 검붉은 공처럼 보였음 — CI 43668850426)
        halo = np.exp(-(r / (0.46 * (0.9 + 0.1 * k))) ** 2) * 0.38
        mid = np.exp(-(r / (0.27 * k)) ** 2) * 0.95
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




# ------------------------------------------------------------------ 작은 판 (한 장 256 — 작은 폭발 · 충격파 · 그을린 땅)

def boom_s():
    """작은 폭발 (칼 하나의 터짐 · 특성 터짐 · 블레이드 레인 칼마다): 대폭발 칸을 14칸으로 줄여 256 으로"""
    return [to_img(*boom_frame(int(round(i * (BOOM_FRAMES - 1) / 13)), n=256, seed=13)) for i in range(14)]


def shock_s():
    return [to_img(*shock_frame(f, n=256, seed=23)) for f in range(SHOCK_FRAMES)]


def crack_s():
    return [to_img(*crack_frame(f, n=256, seed=53)) for f in range(CRACK_FRAMES)]


# ------------------------------------------------------------------ 불꽃 혀 (세로로 서는 그림 128 × 256)

FLAME_FRAMES = 10


def flame_frame(f, w=128, h=256, seed=61):
    """불꽃 혀 하나 (땅에서 솟아 흔들리다 사그라듦 — 소드 생추어리 불꽃 고리): 아래 흰 금빛 심 → 주황 → 진홍 겉 → 와인 끝, 큰 혀 하나 + 곁 혀 둘 (잡음으로
    흔들림 · 끝이 갈라져 떨어짐). 셀 띠 색 (손으로 칠한 띠) · 둘레 빛 무리"""
    N = Noise3(seed)
    u = f / (FLAME_FRAMES - 1)
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = (np.arange(h) + 0.5) / h * 2 - 1
    x, y = np.meshgrid(xs, -ys)          # y 위로 +
    x = x * 0.5                           # 가로 · 세로 같은 단위 (판이 세로로 두 배)
    base_y = -0.9
    grow = 1 - (1 - min(1.0, (f + 1) / 4.0)) ** 2
    shrink = sstep(0.55, 1.0, u)
    Hm = (0.55 + 1.15 * grow) * (1 - 0.55 * shrink)
    t = f * 0.35
    layers = []
    total = np.zeros_like(x)
    temp = np.zeros_like(x)
    tongues = [(0.0, 1.0, 0.24, 0.0), (-0.16, 0.62, 0.15, 1.7), (0.17, 0.7, 0.14, 3.1)]
    for (ox, hk, wk, ph) in tongues:
        Hh = Hm * hk
        yy = (y - base_y) / max(Hh, 1e-3)        # 0 (밑) → 1 (끝)
        sway = 0.09 * np.sin(yy * 4.2 + t * 2.0 + ph) * yy + (N.fbm(yy * 2.5 + ph, t * 0.8, ph, 3) - 0.5) * 0.22 * yy
        cx = ox + sway
        # 물방울 꼴 (밑은 둥글고 0.25 둘레가 가장 넓고 끝은 뾰족)
        wid = wk * 1.55 * (1 - 0.15 * shrink) * np.clip(yy + 0.06, 0, 1) ** 0.32 * np.clip(1 - yy, 0, 1) ** 0.95
        wid = wid * (0.85 + 0.3 * (N.fbm(yy * 6 + ph, x * 3, t, 3) - 0.5))
        d = np.abs(x - cx) / np.maximum(wid, 1e-3)
        inside = (yy > -0.08) & (yy < 1.0)
        m = np.where(inside, 1 - sstep(0.82, 1.02, d), 0.0) * sstep(-0.08, 0.04, yy)
        # 끝이 갈라져 떨어짐 (잡음 문턱이 위에서 올라옴)
        cut = N.fbm(x * 5 + ph, y * 4 - t * 2.2, t, 4)
        m = m * (1 - sstep(0.56, 0.68, cut) * sstep(0.6, 0.92, yy))
        # 온도 = 밑 가운데의 물방울 꼴 심에서 바깥으로 (겹친 물방울 띠 — 세로 줄무늬가 아니게)
        tt = np.clip(1 - np.sqrt((d * 0.95) ** 2 + ((yy - 0.16) / 0.92) ** 2), 0, 1) * 1.05 + 0.04
        total = np.maximum(total, m)
        temp = np.maximum(temp, m * np.clip(tt, 0, 1))
    T = banded(np.clip(temp * 1.1 + 0.08, 0, 1), [0.22, 0.38, 0.52, 0.66, 0.8, 0.9])
    fade = (1 - sstep(0.8, 1.0, u))
    # 둘레 빛 (판 가장자리 전에 0 으로 — 1판 미리보기에서 칸 네모가 비쳤음)
    glow = np.exp(-((x * 3.4) ** 2 + ((y - base_y - Hm * 0.3) / (Hm * 0.55 + 0.15)) ** 2) * 1.6) * 0.42 * fade
    layers.append((FIRE(0.42), glow))
    layers.append((FIRE(0.26 + 0.68 * T), total * fade))
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.03, gain=0.7 * fade)
    a = a * (1 - sstep(0.94, 0.99, np.abs(x) * 2)) * (1 - sstep(0.94, 0.99, np.abs(y)))
    return rgb, a


def flame():
    return [to_img(*flame_frame(f)) for f in range(FLAME_FRAMES)]


# ------------------------------------------------------------------ 땅 고리 (예고 · 테두리 — 2 × 2)

RING_FRAMES = 4


def ring_frame(f, n=512, seed=71):
    """땅에 머무는 고리 (블레이드 레인 과녁 · 소드 생추어리 테두리 · 하늘 고리): 반지름 0.9 — 흰 분홍 심 줄 + 진홍 빛 (안 · 밖) + 안쪽 짧은 칼날 눈금 (15° 마다 ·
    긴 것 · 짧은 것 번갈아) + 바깥 옅은 빛. 칸마다 빛이 조금씩 숨쉼 (되풀이 4칸)"""
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / RING_FRAMES * 2 * math.pi
    R = 0.88
    pulse = 0.85 + 0.15 * math.sin(ph)
    core = np.exp(-((r - R) / 0.006) ** 2)
    glow_i = np.exp(-(np.clip(R - r, 0, None) / 0.05) ** 1.3) * (r < R) * 0.55 * pulse
    glow_o = np.exp(-(np.clip(r - R, 0, None) / 0.028) ** 1.3) * (r >= R) * 0.6 * pulse
    ticks = np.zeros_like(r)
    for i in range(24):
        a0 = i * 2 * math.pi / 24 + 0.13
        L = 0.07 if i % 2 == 0 else 0.04
        ca, sa = math.cos(a0), math.sin(a0)
        along = x * ca + y * sa
        perp = np.abs(-x * sa + y * ca)
        m = (1 - sstep(0.003, 0.007, perp * (1 + 2.5 * np.clip((R - 0.025 - along) / L, 0, 1)))) * sstep(R - 0.025 - L, R - 0.025 - L + 0.01, along) * (1 - sstep(R - 0.03, R - 0.02, along))
        ticks = np.maximum(ticks, m)
    inner = np.exp(-((r - R * 0.93) / 0.004) ** 2) * 0.55
    layers = [(FIRE(0.42), glow_i), (FIRE(0.5), glow_o), (FIRE(0.82), ticks * 0.9 * pulse), (FIRE(0.7), inner), (FIRE(0.9), np.exp(-((r - R) / 0.016) ** 2) * 0.9),
              (WHITE, core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.012, gain=0.6)
    a = a * (1 - sstep(0.95, 0.995, r))
    return rgb, a


def ring():
    return [to_img(*ring_frame(f)) for f in range(RING_FRAMES)]


# ------------------------------------------------------------------ X 베기 (크로스 팽 — 2 × 2)

XSLASH_FRAMES = 10


def _stroke(x, y, ang, bow, half, w0, head, tail, aa):
    """대각선 한 줄기 (가운데를 지나는 각 ang · 반 길이 half · 바깥으로 휜 정도 bow · 밑 반폭 w0) — 줄기를 따라 s ∈ [-1, 1] 중 tail < s < head 만 보임.
    반환: (몸 알파, 머리 쪽 뜨거움 0 ~ 1, 가운데 줄에서 떨어진 비)"""
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


def xslash_frame(f, n=512, seed=61):
    """X 베기 (크로스 팽): 0 ~ 2 칸 두 줄기가 차례로 그어짐 (첫째 왼쪽 위 → 오른쪽 아래, 둘째 1칸 늦게 오른쪽 위 → 왼쪽 아래 — 머리는 흰 금빛 · 몸은 진홍 ·
    가운데 줄은 흰 심) → 2 ~ 5 칸 교차점 섬광 · 푸른 흰 빛살 · 튀는 불티 → 4 ~ 9 칸 꼬리부터 거둬지며 진홍 잔상만 남았다 사라짐"""
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    layers = []
    body_all = np.zeros_like(r)
    core_all = np.zeros_like(r)
    hot_all = np.zeros_like(r)
    for i, ang in enumerate((-math.pi / 4, math.pi / 4)):
        g = f - i
        if g < 0:
            continue
        head = -1.05 + 2.2 * min(1.0, (g + 1) / 2.5)
        tail = -1.05 + 2.1 * sstep(2.5, 8.5, g)
        w0 = 0.085 * (1 - 0.55 * sstep(2, 9, g)) + 0.01
        body, hot, mid = _stroke(x, y, ang + math.pi, 0.07 * (1 if i == 0 else -1), 0.86, w0, head, tail, aa)
        k = 1 - sstep(6, 9.5, g)
        body_all = np.maximum(body_all, body * k)
        core_all = np.maximum(core_all, body * np.clip(1 - mid * 1.9, 0, 1) * k)
        hot_all = np.maximum(hot_all, body * hot * (1 - sstep(2.5, 4.5, g)))
    glow = ndimage.gaussian_filter(body_all, n * 0.02)
    layers.append((FIRE(0.42), np.clip(glow * 1.6, 0, 1) * 0.75))
    layers.append((FIRE(0.5 + 0.2 * core_all), body_all))
    layers.append((FIRE(0.86), core_all))
    layers.append((WHITE, np.clip(hot_all * 1.2 + core_all * 0.35, 0, 1)))
    if 2 <= f <= 5:
        k = 1 - (f - 2) / 4.0
        flash_ = np.exp(-(r / (0.1 + 0.04 * (f - 2))) ** 2) * k * 1.3
        rays = np.zeros_like(r)
        for j in range(4):
            rays = np.maximum(rays, spike(x, y, j * math.pi / 2 + 0.1, 0.42 + 0.1 * (f - 2), 0.045 * k + 0.008, 1.2, aa))
        layers.append((CYAN(0.75), rays * k))
        layers.append((WHITE, np.clip(flash_, 0, 1)))
    if 2 <= f <= 8:
        rng = np.random.default_rng(seed)
        sp = np.zeros_like(r)
        q = (f - 2) / 6.0
        for j in range(22):
            a0 = rng.uniform(0, 2 * math.pi)
            v0 = rng.uniform(0.4, 1.0)
            d0 = 0.08 + 0.7 * v0 * (1 - (1 - q) ** 1.8)
            px, py = d0 * math.cos(a0), d0 * math.sin(a0) - 0.12 * q * q
            dd = np.hypot(x - px, y - py)
            sp = np.maximum(sp, np.exp(-(dd / (0.012 * (1 - 0.5 * q) + 0.004)) ** 2) * (1 - q))
        layers.append((FIRE(0.88), np.clip(sp * 1.4, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.022, gain=0.8)
    a = a * (1 - sstep(0.93, 0.99, np.maximum(np.abs(x), np.abs(y))))
    return rgb, a


def xslash():
    return [to_img(*xslash_frame(f)) for f in range(XSLASH_FRAMES)]


# ------------------------------------------------------------------ 흙먼지 (칼이 땅에 꽂힘 · 솟음 — 한 장 256)

DUST_FRAMES = 8


def dust_frame(f, n=256, seed=81):
    """칼이 땅에 꽂힘 · 솟음 (블레이드 레인 · 소드 생추어리): 밑에서 진홍 빛이 번쩍 → 흙먼지 덩이가 옆 · 위로 퍼지며 (빛 받은 윗면 · 진홍 빛을 받은 아랫면) 옅어짐 ·
    튀는 흙 알갱이. 땅 자리 = 그림 아래쪽 1/4 (자바 PhantomFx.dust 가 그만큼 올려 띄움)"""
    N = Noise3(seed)
    x, y = grid(n)
    u = f / (DUST_FRAMES - 1)
    gy = -0.5
    rng = np.random.default_rng(seed)
    dens = np.zeros_like(x)
    grow = 0.15 + 0.85 * (1 - (1 - u) ** 2)
    for j in range(4):
        a0 = rng.uniform(0.2, math.pi / 2 - 0.1)
        sp = rng.uniform(0.35, 0.8)
        rr = 0.12 + 0.2 * u + rng.uniform(0, 0.06)
        for a in (a0, math.pi - a0 + rng.uniform(-0.15, 0.15)):   # 좌우 짝 (한쪽으로 쏠리지 않게)
            cx = math.cos(a) * sp * grow
            cy = gy + 0.08 + math.sin(a) * sp * 0.55 * grow
            dens = np.maximum(dens, np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (rr * rr)))
    nz = N.fbm(x * 4 + 3, y * 4, f * 0.4, 4)
    dmask = np.clip((dens + (nz - 0.5) * 0.6 - 0.35) * 2.2, 0, 1) * (1 - sstep(0.5, 1.0, u)) * (y > gy - 0.05)
    light = np.clip(0.5 + (y - gy) * 0.8 + (nz - 0.5) * 0.4, 0, 1)
    DUST = ramp([(0.0, "#2A1A1E"), (0.5, "#5C4038"), (0.8, "#8A6A58"), (1.0, "#B89A80")])
    low = np.exp(-((y - gy) / 0.15) ** 2)
    col = DUST(light) * (1 - 0.35 * low)[..., None] + FIRE(0.45) * (0.35 * low)[..., None]
    layers = [(col, dmask * 0.85)]
    if f <= 3:
        k = 1 - f / 4.0
        fl = np.exp(-((x / 0.35) ** 2 + ((y - gy) / 0.12) ** 2)) * k
        layers.insert(0, (FIRE(0.6), fl * 0.8))
        layers.append((WHITE, np.exp(-((x / 0.12) ** 2 + ((y - gy) / 0.05) ** 2)) * k))
    sp = np.zeros_like(x)
    for j in range(14):
        a = rng.uniform(0.2, math.pi - 0.2)
        v = rng.uniform(0.5, 1.0)
        px = math.cos(a) * v * 0.9 * u
        py = gy + math.sin(a) * v * 1.0 * u - 0.9 * u * u
        dd = np.hypot(x - px, y - py)
        sp = np.maximum(sp, np.exp(-(dd / 0.018) ** 2) * (1 - u) * (py > gy - 0.02) * sstep(0.08, 0.2, u))
    layers.append((DUST(0.3), np.clip(sp * 1.2, 0, 1)))
    rgb, a = compose(layers, n)
    a = a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))
    return rgb, a


def dust():
    return [to_img(*dust_frame(f)) for f in range(DUST_FRAMES)]


# ------------------------------------------------------------------ 목록 (tools/vfx_sprites.py 가 더함)

def sprites():
    p = LIB + "_"
    return (tiled(p + "boom", boom) + tiled(p + "shock", shock) + tiled(p + "crack", crack) + tiled(p + "ring", ring) + tiled(p + "xslash", xslash)
            + [(p + "boom_s", boom_s, True), (p + "shock_s", shock_s, True), (p + "crack_s", crack_s, True), (p + "flash", flash, True),
               (p + "charge", charge, True), (p + "ember", ember, True), (p + "shard", shard, True), (p + "flame", flame, True), (p + "dust", dust, True)])


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
        allsets = {"boom": boom, "shock": shock, "crack": crack, "ring": ring, "boom_s": boom_s, "shock_s": shock_s, "flash": flash, "charge": charge,
                   "small": lambda: ember() + shard(), "flame": flame, "xslash": xslash, "dust": dust}
        preview({k: v() for k, v in allsets.items() if only is None or k in only}, out)
