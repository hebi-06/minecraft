"""2026-11-15 아스널 킹 전용 이펙트 그림 (그림판 ak_* — 사용자 "직업마다 전용으로 그릴래", 팬텀 아처 pa_* 다음). 금빛 문에서 보물을 퍼붓는 왕:
색은 흰빛 → 옅은 금 → 금 → 호박 → 청동 + 보석 반짝임 (루비 · 사파이어 · 에메랄드 · 자수정) — 진홍 불 (팬텀 아처) 과 다른 "빛 · 보물" 의 결.

- 문 ak_gate (256 · 되풀이 8칸): 허공에 열린 금빛 물결 — 반투명 녹은 금 판에 물결 고리가 바깥으로 퍼지고, 가운데 흰 금빛 심 · 테의 금 징 (보석 넷) · 반짝임.
  자바 (PhantomFx.gate) 가 앞 · 뒤 두 장 (글자는 한쪽 면) 으로 띄우고 열림 · 닫힘은 크기 · 옅어짐으로.
- 큰 그림 (2 × 2 조각 512): ak_boom (빛 폭발 16칸 — 빛살 열여섯 · 금빛 불덩이 · 금가루 · 옅은 청동 연기) · ak_shock (금빛 충격파 10칸 — 뒤따르는 물결 고리) ·
  ak_crack (금빛으로 달아오른 금 5칸) · ak_ring (땅 고리 — 금 징 · 보석, 되풀이 4칸)
- 한 장 256: ak_boom_s · ak_shock_s · ak_crack_s · ak_flash (빛살 별 + 보석 반짝임 8칸) · ak_charge (모으는 빛 되풀이 10칸) · ak_dust (보물이 꽂힌 자리 금가루 8칸)
- 128: ak_sparkle (네 갈래 반짝임 — 불씨 대신 4칸) · ak_shard (금 조각 — 구름 8칸)

  cd tools && python3 -m jobvfx.arsenal --preview 폴더 [--only gate,boom]
"""
import math
import sys

import numpy as np

from .base import (WHITE, H, Noise3, banded, bloom, compose, grid, preview, ramp, spike, sstep, star_shape, tiled, to_img)

LIB = "ak"

# 금 (흰빛 → 옅은 금 → 금 → 호박 → 청동 → 어두운 청동)
GOLD = ramp([(0.0, "#140C06"), (0.12, "#2E1A08"), (0.26, "#5E3608"), (0.4, "#9A5C0C"), (0.52, "#D08A14"), (0.64, "#F0B028"),
             (0.76, "#FFD24E"), (0.86, "#FFE896"), (0.94, "#FFF7DC"), (1.0, "#FFFFFF")])
# 옅은 청동 연기 (빛을 받은 쪽은 금빛)
BRONZE = ramp([(0.0, "#1A120C"), (0.45, "#3E2C1C"), (0.8, "#6E5236"), (1.0, "#A8875A")])
# 보석 반짝임
GEMS = [H("#FF4458"), H("#4A86FF"), H("#38E896"), H("#BE6CFF")]
SAND = ramp([(0.0, "#3A2A16"), (0.5, "#7A6038"), (0.8, "#C8A56A"), (1.0, "#F2DEA6")])


def star4(x, y, cx, cy, L, w, rot=0.0, aa=0.01, short=0.45):
    """네 갈래 반짝임 (긴 갈래 넷 + 짧은 사선 넷) — 가운데 (cx, cy) · 긴 갈래 길이 L · 밑동 반폭 w"""
    xs, ys = x - cx, y - cy
    m = np.zeros_like(x)
    for i in range(4):
        m = np.maximum(m, spike(xs, ys, rot + i * math.pi / 2, L, w, 1.6, aa))
        if short > 0:
            m = np.maximum(m, spike(xs, ys, rot + math.pi / 4 + i * math.pi / 2, L * short, w * 0.7, 1.6, aa))
    return m


def edge_fade(x, y, lo=0.93, hi=0.99):
    return 1 - sstep(lo, hi, np.maximum(np.abs(x), np.abs(y)))


# ------------------------------------------------------------------ 금빛 문 (256 · 되풀이)

GATE_FRAMES = 8


def gate_frame(f, n=256, seed=101):
    """허공에 열린 금빛 문 (위에서 본 물결 면 — 자바가 쏘는 쪽을 보게 고정): 반투명 녹은 금 판 (가운데가 밝음 · 소용돌이 결) 위로 물결 고리 넷이 바깥으로
    퍼짐 (8칸에 한 고리 — 되풀이 이음매 없음) · 테 (금 · 안쪽 가는 줄 · 바깥 빛) · 테의 금 징 열둘 (셋째마다 보석) · 흰 금빛 심 (숨쉼) · 반짝임 다섯"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / GATE_FRAMES
    aa = 1.6 / n
    Rr = 0.8
    layers = []
    disc = 1 - sstep(Rr - 0.02, Rr + 0.02, r)
    wob = 0.018 * np.sin(5 * th + 2 * math.pi * ph) + 0.012 * np.sin(9 * th - 4 * math.pi * ph + 1.3)
    swirl = 0.5 + 0.5 * np.cos(6 * th - 12 * r + 2 * math.pi * ph)
    rr = np.clip(r / Rr, 0, 1)
    # 녹은 금 판 (가운데 밝은 금 → 테 쪽 짙은 호박 · 2판: 1판은 옅어 바탕과 섞여 누런 회색이었음)
    layers.append((GOLD(0.6 + 0.22 * (1 - rr) ** 1.5 + 0.08 * swirl), disc * (0.42 + 0.4 * (1 - rr) ** 1.2)))
    # 소용돌이 빛 줄 (옅게)
    arms = np.clip((swirl - 0.78) / 0.22, 0, 1) * disc * (1 - rr) ** 0.6
    layers.append((GOLD(0.86), arms * 0.45))
    # 물결 고리 (바깥으로 퍼짐)
    rip = np.zeros_like(r)
    for k in range(4):
        rho = (k + ph) / 4.0 * Rr
        w = 0.012 + 0.02 * rho
        inten = math.sin(math.pi * min(1.0, rho / Rr)) ** 0.7
        rip = np.maximum(rip, np.exp(-((r - rho * (1 + wob)) / w) ** 2) * inten)
    layers.append((GOLD(0.8), rip * disc))
    layers.append((GOLD(0.95), np.clip((rip - 0.45) * 2.0, 0, 1) * disc))
    layers.append((WHITE, np.clip((rip - 0.75) * 4.0, 0, 1) * disc * 0.8))
    # 테
    layers.append((GOLD(0.62), np.exp(-((r - Rr) / 0.07) ** 2) * 0.7))
    layers.append((GOLD(0.82), np.exp(-((r - Rr) / 0.024) ** 2)))
    layers.append((GOLD(0.96), np.exp(-((r - Rr) / 0.009) ** 2)))
    layers.append((GOLD(0.78), np.exp(-((r - Rr * 0.92) / 0.006) ** 2) * 0.7))
    # 금 징 열둘 (셋째마다 보석 — 루비 · 사파이어 · 에메랄드 · 자수정)
    for i in range(12):
        a0 = i * 2 * math.pi / 12 + math.pi / 12
        cx, cy = Rr * math.cos(a0), Rr * math.sin(a0)
        du = (x - cx) * -math.sin(a0) + (y - cy) * math.cos(a0)
        dv = (x - cx) * math.cos(a0) + (y - cy) * math.sin(a0)
        gem = i % 3 == 0
        s1, s2 = (0.05, 0.075) if gem else (0.03, 0.05)
        d = np.abs(du) / s1 + np.abs(dv) / s2
        body = 1 - sstep(1 - aa * 30, 1 + aa * 30, d)
        layers.append((GOLD(0.95), body))
        if gem:
            g = GEMS[(i // 3) % 4]
            layers.append((g * 0.75 + 0.25, np.clip(1 - d * 1.35, 0, 1) * 1.4))
            layers.append((WHITE, np.exp(-((du + 0.012) ** 2 + (dv - 0.02) ** 2) / 0.00015)))
    # 흰 금빛 심
    pulse = 0.9 + 0.1 * math.sin(2 * math.pi * ph)
    layers.append((GOLD(0.92), np.exp(-(r / (0.24 * pulse)) ** 2) * 0.75))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.1 * pulse)) ** 2) * 1.3, 0, 1)))
    # 반짝임 (자리 고정 · 차례로 깜박임)
    tw = np.zeros_like(r)
    for i in range(5):
        a0, rad, o = rng.uniform(0, 2 * math.pi), rng.uniform(0.25, 0.68), i / 5.0
        b = max(0.0, math.sin(2 * math.pi * (ph + o))) ** 3
        if b > 0.02:
            tw = np.maximum(tw, star4(x, y, rad * math.cos(a0), rad * math.sin(a0), 0.04 + 0.1 * b, 0.012, 0.2, aa) * b)
    layers.append((WHITE, tw))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.03, gain=0.55)
    a = a * (1 - sstep(0.95, 0.995, r))
    return rgb, a


def gate():
    return [to_img(*gate_frame(f)) for f in range(GATE_FRAMES)]


# ------------------------------------------------------------------ 섬광 (256)

FLASH_FRAMES = 8


def flash_frame(f, n=256, seed=131):
    """빛살 별 (보물이 나옴 · 꽂힘 · 문이 열림): 0 ~ 2 칸 단단한 금빛 별 (꼭짓점 여덟) → 긴 빛살 넷 (흰 심 · 금 겉) · 짧은 사선 넷 (호박) 이 가늘어지며 ·
    퍼지는 금 고리 · 둘레에 보석 반짝임 넷 (루비 · 사파이어 · 에메랄드 · 자수정)"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (FLASH_FRAMES - 1)
    aa = 1.6 / n
    grow = min(1.0, (f + 1) / 2.0)
    k = (1 - u) ** 1.1
    layers = [(GOLD(0.7 + 0.2 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.32 + 0.16 * u)) ** 2) * 0.75 * k)]
    ring_r = 0.18 + 0.7 * (1 - (1 - u) ** 1.6)
    layers.append((GOLD(0.88), np.exp(-((r - ring_r) / (0.022 + 0.02 * u)) ** 2) * (1 - u) ** 1.3 * 0.85))
    thin = 1 - 0.6 * sstep(0.15, 1.0, u)
    L1 = (0.66 + 0.32 * grow) * (1 - 0.2 * u)
    L2 = (0.36 + 0.18 * grow) * (1 - 0.3 * u)
    o1, i1, o2, i2 = (np.zeros_like(r) for _ in range(4))
    for i in range(4):
        a0 = math.pi / 2 + i * math.pi / 2
        o1 = np.maximum(o1, spike(x, y, a0, L1, 0.06 * thin, 1.5, aa))
        i1 = np.maximum(i1, spike(x, y, a0, L1 * 0.85, 0.022 * thin, 1.5, aa))
        a1 = a0 + math.pi / 4
        o2 = np.maximum(o2, spike(x, y, a1, L2, 0.05 * thin, 1.3, aa))
        i2 = np.maximum(i2, spike(x, y, a1, L2 * 0.7, 0.018 * thin, 1.3, aa))
    layers += [(GOLD(0.6), o2 * k), (GOLD(0.82), o1 * k), (GOLD(0.92), i2 * k), (WHITE, i1 * k)]
    if f <= 2:
        K = 8
        outs = rng.uniform(0.22, 0.36, K)
        R = star_shape(r, th, K, 0.11, outs * grow, 0.0, 2.2) * (1 - 0.3 * f / 2)
        shape = 1 - sstep(R - aa, R + aa, r)
        layers.append((GOLD(0.9), shape))
        layers.append((WHITE, shape * (1 - sstep(R * 0.45, R * 0.9, r))))
    if 1 <= f <= 6:
        for j in range(4):
            a0 = math.pi / 4 + j * math.pi / 2 + rng.uniform(-0.3, 0.3)
            rad = 0.42 + 0.18 * u + rng.uniform(-0.05, 0.05)
            b = math.sin(math.pi * (f - 1) / 6.0) ** 1.2
            m = star4(x, y, rad * math.cos(a0), rad * math.sin(a0), 0.11 * b + 0.02, 0.016, 0.0, aa, short=0.0)
            layers.append((GEMS[j] * 0.7 + 0.3, m * b))
            layers.append((WHITE, np.exp(-((x - rad * math.cos(a0)) ** 2 + (y - rad * math.sin(a0)) ** 2) / 0.0004) * b))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.08 + 0.05 * grow)) ** 2) * k * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.03, gain=0.7 * k)
    return rgb, a * edge_fade(x, y)


def flash():
    return [to_img(*flash_frame(f)) for f in range(FLASH_FRAMES)]


# ------------------------------------------------------------------ 빛 폭발 (2 × 2)

BOOM_FRAMES = 16


def boom_frame(f, n=512, seed=111):
    """금빛 빛 폭발 (킹스 저지먼트 · 트레저 레인 · 아스널 게이트 끝): 0 ~ 3 흰 섬광 원판 → 0 ~ 9 빛살 열여섯 (길고 가는 — 흰 심 · 금 겉) · 퍼지는 금 고리 →
    1 ~ 9 셀 띠 금빛 불덩이 (흰빛 → 옅은 금 → 금 → 호박) → 4 ~ 15 불덩이는 식어 줄고 금가루 반짝임이 바깥으로 흩어짐 · 옅은 청동 연기 (빛 받은 윗면 금빛)"""
    N = Noise3(seed)
    N2 = Noise3(seed + 1)
    rng = np.random.default_rng(seed + 5)
    x, y = grid(n)
    u = f / (BOOM_FRAMES - 1)
    aa = 1.6 / n
    R = 0.16 + 0.54 * (1 - (1 - min(1.0, f / 5.5)) ** 2.2) + 0.06 * max(0.0, (f - 6) / 9.0)
    rise = 0.07 * max(0.0, (f - 4) / 11.0) ** 1.2
    yy = y - rise
    r = np.hypot(x, yy)
    th = np.arctan2(yy, x)
    t = f * 0.22
    wx = N.fbm(x * 2.4 + 3, yy * 2.4, t, 4)
    wy = N.fbm(x * 2.4 - 7, yy * 2.4 + 2, t + 9, 4)
    q = N2.fbm(x * 3.4 + wx * 1.6, yy * 3.4 + wy * 1.6, t * 1.2, 5)
    lick = N.fbm(np.cos(th) * 2.2 + 11, np.sin(th) * 2.2 - 4, t * 1.3, 3)
    layers = []
    # 옅은 청동 연기 (바깥 · 위로) — 빛을 받은 윗면은 금빛
    lick2 = N2.fbm(np.cos(th) * 1.5 - 3, np.sin(th) * 1.5 + 6, t * 0.8, 3)
    q2 = N.fbm(x * 2.0 + wx * 0.9 + 21, yy * 2.0 + wy * 0.9, t * 0.7, 4)
    Rs = R * (1.0 + 0.25 * u) * (0.72 + 0.5 * lick2 + 0.12 * (q2 - 0.5))
    cloud = 1 - sstep(Rs * 0.82, Rs * 1.02, r)
    thr = 0.16 + 0.36 * sstep(0.3, 1.0, u)
    puff = sstep(thr, thr + 0.22, q2 + 0.18 * (1 - r / np.maximum(Rs, 1e-3)))
    # 2판: 연기는 옅고 밝게 (1판은 풀밭에서 끝 칸이 올리브빛 얼룩이었음)
    s_alpha = cloud * puff * (0.24 - 0.1 * u) * (1 - sstep(0.55, 0.95, u)) * sstep(0.12, 0.3, u)
    shade = np.clip(0.45 + 0.5 * (yy / np.maximum(Rs, 1e-3)) + 0.35 * (q2 - 0.5), 0, 1)
    layers.append((SAND(0.55 + 0.45 * shade) * 0.6 + GOLD(0.72) * 0.4, s_alpha))
    # 빛살 (0 ~ 9)
    if f <= 9:
        k = (1 - f / 10.0) ** 1.2
        grow = 0.7 + 0.3 * min(1.0, f / 3.0)
        ro = np.zeros_like(r)
        ri = np.zeros_like(r)
        for i in range(16):
            a0 = i * 2 * math.pi / 16 + rng.uniform(-0.09, 0.09)
            L = (0.62 + 0.36 * rng.uniform()) * grow * (1.0 if i % 2 == 0 else 0.72)
            w = (0.024 if i % 2 == 0 else 0.016) * (1 - 0.45 * u)
            ro = np.maximum(ro, spike(x, y, a0, L, w, 1.5, aa))
            ri = np.maximum(ri, spike(x, y, a0, L * 0.82, w * 0.38, 1.5, aa))
        layers.append((GOLD(0.8), ro * k * 0.9))
        layers.append((WHITE, ri * k))
    # 퍼지는 금 고리
    if 1 <= f <= 8:
        rr = 0.22 + 0.72 * (1 - (1 - (f - 1) / 7.0) ** 1.5)
        layers.append((GOLD(0.88), np.exp(-((r - rr) / 0.018) ** 2) * (1 - (f - 1) / 8.0) ** 1.2))
    # 금빛 불덩이 (식으며 줄어듦)
    Rf = R * (1.0 - 0.7 * sstep(0.25, 0.85, u)) * (0.84 + 0.3 * lick + 0.1 * (q - 0.5))
    fire = 1 - sstep(Rf * 0.86, Rf * 1.02, r)
    cool = sstep(0.15, 1.0, u) * 0.45
    T = (1 - np.clip(r / np.maximum(Rf, 1e-3), 0, 1)) ** 0.7 * 0.78 + (q - 0.5) * 0.5 + 0.38 - cool
    T = T + 0.5 * (1 - sstep(0.0, 0.18, u)) * np.exp(-(r / (R * 0.6)) ** 2)
    Tb = banded(np.clip(T, 0, 1), [0.3, 0.44, 0.56, 0.68, 0.8, 0.9])
    # 2판: 식어도 금빛 (낮은 온도도 호박 — 1판은 옅어지며 올리브빛) · 줄어들며 사라짐
    f_alpha = fire * np.clip(T * 2.2 + 0.2, 0, 1) * np.clip((T - 0.14) * 4.0, 0, 1) * (1 - sstep(0.5, 0.82, u))
    layers.append((GOLD(0.46 + 0.54 * Tb), f_alpha))
    # 흰 섬광 원판 (0 ~ 3)
    if f <= 3:
        kk = 1 - f / 4.0
        layers.append((WHITE, np.clip(np.exp(-(r / (0.22 + 0.12 * f / 3)) ** 2) * kk * 1.2, 0, 1)))
    # 금가루 반짝임 (바깥으로 흩어짐 · 깜박임)
    if 2 <= f:
        pr = np.random.default_rng(seed + 9)
        dust = np.zeros_like(r)
        glint = np.zeros_like(r)
        for i in range(46):
            a0 = pr.uniform(0, 2 * math.pi)
            v = pr.uniform(0.35, 1.0)
            ph0 = pr.uniform(0, 1)
            prog = 1 - (1 - min(1.0, (f - 2) / 13.0)) ** 1.6
            d0 = 0.15 + v * 0.78 * prog
            px, py = d0 * math.cos(a0), d0 * math.sin(a0) + rise - 0.06 * prog ** 2
            life = (1 - sstep(0.55 + 0.4 * v, 1.0, prog))
            tw = 0.55 + 0.45 * math.sin(2 * math.pi * (ph0 + f * 0.23)) ** 2
            if i % 4 == 0:
                glint = np.maximum(glint, star4(x, y, px, py, 0.05 * tw + 0.012, 0.006, ph0, aa) * life * tw)
            else:
                dust = np.maximum(dust, np.exp(-((x - px) ** 2 + (y - py) ** 2) / (0.006 * (0.6 + 0.4 * tw)) ** 2) * life * tw)
        layers.append((GOLD(0.86), np.clip(dust * 1.3, 0, 1)))
        layers.append((WHITE, np.clip(glint * 1.2, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=1.0 - 0.5 * u)
    return rgb, a * edge_fade(x, y, 0.9, 0.98)


def boom():
    return [to_img(*boom_frame(f)) for f in range(BOOM_FRAMES)]


def boom_s():
    """작은 빛 폭발 (보물 하나 · 특성 터짐 · 꽂힌 보물): 큰 폭발 칸을 12칸으로 256"""
    return [to_img(*boom_frame(int(round(i * (BOOM_FRAMES - 1) / 11)), n=256, seed=113)) for i in range(12)]


# ------------------------------------------------------------------ 금빛 충격파 (2 × 2 — 땅에 눕혀 씀)

SHOCK_FRAMES = 10


def shock_frame(f, n=512, seed=121):
    """땅의 금빛 충격파 (위에서 봄): 흰 심 앞 날 + 금 겉 · 뒤따르는 옅은 물결 고리 둘 (문 물결과 같은 결) · 앞 날 안쪽 짧은 금빛 (속은 비움) ·
    앞 날 바깥 금가루 알갱이 · 바깥으로 긁힌 빛 줄"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    u = f / (SHOCK_FRAMES - 1)
    R = 0.14 + 0.8 * (1 - (1 - u) ** 2.2)
    jag = (N.fbm(np.cos(th) * 3 + 2, np.sin(th) * 3, f * 0.3, 3) - 0.5) * 0.03
    d = r - (R + jag)
    k = (1 - u) ** 0.9
    w = 0.026 * (1 - 0.5 * u) + 0.01
    core = np.exp(-(d / (w * 0.5)) ** 2) * k
    edge = np.exp(-(d / (w * 1.5)) ** 2) * k
    trail = np.exp(-(np.clip(-d, 0, None) / (0.07 + 0.04 * u)) ** 1.5) * (d < 0) * k * 0.6
    rip = np.zeros_like(r)
    for j, fr in enumerate((0.84, 0.7)):
        rip = np.maximum(rip, np.exp(-((r - R * fr) / (0.008 + 0.006 * u)) ** 2) * (0.55 - 0.2 * j) * k)
    lines = np.zeros_like(r)
    for i in range(30):
        a0 = rng.uniform(0, 2 * math.pi)
        L = rng.uniform(0.04, 0.1)
        dth = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi) * r
        lines = np.maximum(lines, (1 - sstep(0.002, 0.006, dth)) * sstep(R - 0.005, R + 0.01, r) * (1 - sstep(R + L * 0.6, R + L, r)))
    lines *= (1 - u) ** 1.2 * 0.8
    specks = np.zeros_like(r)
    for i in range(40):
        a0 = rng.uniform(0, 2 * math.pi)
        dr = rng.uniform(0.0, 0.08)
        px, py = (R + dr) * math.cos(a0), (R + dr) * math.sin(a0)
        specks = np.maximum(specks, np.exp(-((x - px) ** 2 + (y - py) ** 2) / 0.00004) * (1 - u) * rng.uniform(0.4, 1.0))
    layers = [(GOLD(0.62), trail), (GOLD(0.82), rip), (GOLD(0.9), lines), (GOLD(0.88), edge * 0.9), (GOLD(0.95), np.clip(specks * 1.2, 0, 1)),
              (WHITE, core)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.016, gain=0.8 * k)
    return rgb, a * (1 - sstep(0.93, 0.99, r))


def shock():
    return [to_img(*shock_frame(f)) for f in range(SHOCK_FRAMES)]


def shock_s():
    return [to_img(*shock_frame(f, n=256, seed=123)) for f in range(SHOCK_FRAMES)]


# ------------------------------------------------------------------ 달아오른 금 (2 × 2 — 땅)

CRACK_FRAMES = 5


def crack_frame(f, n=512, seed=141):
    """보물이 꽂힌 · 터진 자리 (위에서 봄): 청동빛으로 탄 자국 + 가운데서 뻗어 갈라지는 금 (속이 흰 금빛 → 호박으로 식음) · 가운데 빛 · 금가루"""
    N = Noise3(seed)
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    u = f / (CRACK_FRAMES - 1)
    burn = (1 - sstep(0.3, 0.78, r + (N.fbm(x * 4, y * 4, 1.0, 4) - 0.5) * 0.35)) * 0.7
    cracks = np.zeros_like(r)
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        px, py = 0.0, 0.0
        L = rng.uniform(0.45, 0.85)
        steps = 7
        for s in range(steps):
            a += rng.uniform(-0.45, 0.45)
            qx, qy = px + math.cos(a) * L / steps, py + math.sin(a) * L / steps
            dx, dy = qx - px, qy - py
            ll = dx * dx + dy * dy
            ss = np.clip(((x - px) * dx + (y - py) * dy) / ll, 0, 1)
            dd = np.hypot(x - (px + ss * dx), y - (py + ss * dy))
            wid = 0.015 * (1 - s / steps) + 0.004
            cracks = np.maximum(cracks, 1 - sstep(wid * 0.5, wid, dd))
            if s == 3 and rng.uniform() < 0.6:  # 곁가지
                ba = a + rng.choice([-1, 1]) * rng.uniform(0.6, 1.0)
                bx, by = qx + math.cos(ba) * L * 0.25, qy + math.sin(ba) * L * 0.25
                ex, ey = bx - qx, by - qy
                ss2 = np.clip(((x - qx) * ex + (y - qy) * ey) / (ex * ex + ey * ey), 0, 1)
                dd2 = np.hypot(x - (qx + ss2 * ex), y - (qy + ss2 * ey))
                cracks = np.maximum(cracks, 1 - sstep(0.003, 0.007, dd2))
            px, py = qx, qy
    heat = (1 - u) ** 1.2
    glow = np.exp(-(r / 0.18) ** 2) * heat
    layers = [(BRONZE(0.25) * 0.6 + GOLD(0.18) * 0.4, burn * (0.85 - 0.35 * u)), (GOLD(0.58 + 0.36 * heat), cracks * (0.4 + 0.6 * heat)),
              (GOLD(0.9), glow * 0.8)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.55, sigma=0.012, gain=0.75 * heat + 0.1)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


def crack():
    return [to_img(*crack_frame(f)) for f in range(CRACK_FRAMES)]


def crack_s():
    return [to_img(*crack_frame(f, n=256, seed=143)) for f in range(CRACK_FRAMES)]


# ------------------------------------------------------------------ 땅 고리 (2 × 2 · 되풀이)

RING_FRAMES = 4


def ring_frame(f, n=512, seed=151):
    """땅에 머무는 금빛 고리 (트레저 레인 과녁 · 킹스 저지먼트 · 아스널 게이트 발밑): 반지름 0.88 — 흰 심 줄 + 금 빛 (안 · 밖) · 안쪽 금 징 스물넷 (마름모 —
    여섯째마다 보석) · 안쪽 가는 점선 고리 · 숨쉼 (되풀이 4칸)"""
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / RING_FRAMES * 2 * math.pi
    aa = 1.6 / n
    R = 0.88
    pulse = 0.85 + 0.15 * math.sin(ph)
    layers = [(GOLD(0.55), np.exp(-(np.clip(R - r, 0, None) / 0.05) ** 1.3) * (r < R) * 0.5 * pulse),
              (GOLD(0.62), np.exp(-(np.clip(r - R, 0, None) / 0.03) ** 1.3) * (r >= R) * 0.55 * pulse)]
    studs = np.zeros_like(r)
    gem_l = []
    Rs = R * 0.94
    for i in range(24):
        a0 = i * 2 * math.pi / 24 + 0.13
        cx, cy = Rs * math.cos(a0), Rs * math.sin(a0)
        du = (x - cx) * -math.sin(a0) + (y - cy) * math.cos(a0)
        dv = (x - cx) * math.cos(a0) + (y - cy) * math.sin(a0)
        big = i % 2 == 0
        s1, s2 = (0.012, 0.022) if big else (0.007, 0.013)
        d = np.abs(du) / s1 + np.abs(dv) / s2
        m = 1 - sstep(1 - aa * 40, 1 + aa * 40, d)
        studs = np.maximum(studs, m)
        if i % 6 == 0:
            gem_l.append((GEMS[(i // 6) % 4] * 0.7 + 0.3, np.clip(1 - d * 1.4, 0, 1) * 1.5 * pulse))
    dash = (0.5 + 0.5 * np.cos(th * 48)) > 0.35
    inner = np.exp(-((r - R * 0.86) / 0.004) ** 2) * dash * 0.6
    layers += [(GOLD(0.9), studs * 0.95), *gem_l, (GOLD(0.8), inner), (GOLD(0.92), np.exp(-((r - R) / 0.016) ** 2) * 0.9),
               (WHITE, np.exp(-((r - R) / 0.006) ** 2))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.012, gain=0.6)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


def ring():
    return [to_img(*ring_frame(f)) for f in range(RING_FRAMES)]


# ------------------------------------------------------------------ 모으는 빛 (256 · 되풀이)

CHARGE_FRAMES = 10


def charge_frame(f, n=256, seed=161):
    """거대한 보물 끝 · 가장 큰 문에 모이는 빛 (되풀이): 숨쉬는 흰 금빛 심 + 금 빛 무리 · 바깥에서 안으로 빨려 드는 빛 줄기 (흰 심 · 금 겉 — 뾰족한 끝) ·
    천천히 도는 끊긴 고리 둘 · 보석 빛이 도는 작은 반짝임"""
    rng = np.random.default_rng(seed)
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / CHARGE_FRAMES
    aa = 1.6 / n
    layers = []
    pulse = 0.88 + 0.12 * math.sin(ph * 2 * math.pi * 2)
    layers.append((GOLD(0.62 + 0.3 * np.exp(-(r / 0.2) ** 2)), np.exp(-(r / (0.36 * pulse)) ** 2) * 0.7))
    for j, (rr, nseg) in enumerate(((0.66, 3), (0.5, 4))):
        ring = np.exp(-((r - rr) / 0.016) ** 2)
        gaps = np.zeros_like(r)
        for i in range(nseg):
            a0 = i * 2 * math.pi / nseg + (1 if j == 0 else -1) * ph * 2 * math.pi / nseg
            dd = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
            gaps = np.maximum(gaps, 1 - sstep(0.5, 0.66, dd))
        layers.append((GOLD(0.86), ring * gaps * (0.85 - 0.25 * j)))
    outer = np.zeros_like(r)
    inner = np.zeros_like(r)
    for i in range(12):
        a0 = rng.uniform(0, 2 * math.pi)
        s0 = rng.uniform(0, 1)
        q = (s0 + ph) % 1.0
        head = 0.95 - 0.8 * q
        L = 0.15 + 0.12 * (1 - q)
        ca, sa = math.cos(a0), math.sin(a0)
        along = x * ca + y * sa - head
        perp = np.abs(-x * sa + y * ca)
        tt = np.clip(along / L, 0, 1)
        w = 0.022 * (1 - tt) ** 0.8 * (0.6 + 0.4 * q)
        m = (1 - sstep(w - aa, w + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L - aa, L + aa, along))
        fade = math.sin(math.pi * min(1.0, q * 1.15)) ** 0.6
        outer = np.maximum(outer, m * fade)
        w2 = w * 0.4
        m2 = (1 - sstep(w2 - aa, w2 + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L * 0.8 - aa, L * 0.8 + aa, along))
        inner = np.maximum(inner, m2 * fade)
    layers += [(GOLD(0.76), outer), (WHITE, inner)]
    for j in range(4):
        a0 = j * math.pi / 2 + ph * 2 * math.pi / 4 + 0.4
        b = 0.5 + 0.5 * math.sin(2 * math.pi * (ph + j / 4.0))
        m = star4(x, y, 0.42 * math.cos(a0), 0.42 * math.sin(a0), 0.05 + 0.05 * b, 0.012, 0.0, aa, short=0.0)
        layers.append((GEMS[j] * 0.65 + 0.35, m * b))
    layers.append((WHITE, np.clip(np.exp(-(r / (0.12 * pulse)) ** 2) * 1.25, 0, 1)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.035, gain=0.75)
    return rgb, a * (1 - sstep(0.9, 0.99, r))


def charge():
    return [to_img(*charge_frame(f)) for f in range(CHARGE_FRAMES)]


# ------------------------------------------------------------------ 반짝임 · 금 조각 (날아가는 작은 그림 128)

SPARKLE_FRAMES = 4


def sparkle():
    """금가루 반짝임 (불씨 대신 — 카메라를 보는 네 갈래 별이라 어느 쪽에서도 같은 꼴): 흰 심 · 금 갈래 · 옅은 금 빛, 네 칸 깜박임 (크기 · 돌림)"""
    n = 128
    x, y = grid(n)
    r = np.hypot(x, y)
    aa = 1.6 / n
    out = []
    for f in range(SPARKLE_FRAMES):
        k = [1.0, 0.7, 0.9, 0.55][f]
        rot = [0.0, 0.12, 0.05, 0.18][f]
        halo = np.exp(-(r / (0.32 * (0.85 + 0.15 * k))) ** 2) * 0.35
        body = star4(x, y, 0, 0, 0.92 * k, 0.07, rot, aa, short=0.42)
        core_l = star4(x, y, 0, 0, 0.7 * k, 0.025, rot, aa, short=0.3)
        rgb, a = compose([(GOLD(0.66), halo), (GOLD(0.86), body), (WHITE, core_l), (WHITE, np.clip(np.exp(-(r / (0.12 * k)) ** 2) * 1.3, 0, 1))], n)
        out.append(to_img(rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))))
    return out


SHARD_FRAMES = 8


def shard():
    """금 조각 (보물이 부서짐): 납작한 금 조각이 구르는 여덟 칸 — 빛 받는 면은 흰 금, 어두운 면은 호박 · 청동, 가장자리 흰 금빛 · 둘레 옅은 금 빛 (128 · 되풀이)"""
    n = 128
    x, y = grid(n)
    aa = 1.6 / n
    poly = np.array([[-0.6, -0.16, 0], [-0.2, 0.36, 0], [0.62, 0.18, 0], [0.42, -0.32, 0], [-0.1, -0.4, 0]])
    light = np.array([-0.4, 0.75, 0.55])
    light = light / np.linalg.norm(light)
    out = []
    for f in range(SHARD_FRAMES):
        a1 = f / SHARD_FRAMES * 2 * math.pi
        rx = np.array([[1, 0, 0], [0, math.cos(a1), -math.sin(a1)], [0, math.sin(a1), math.cos(a1)]])
        a2 = a1 * 0.5 + 0.9
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
        edge = np.exp(-(np.clip(sd, 0, None) / 0.03) ** 2) * body
        shade = lit * 0.7 + 0.2 * (y * 0.5 + 0.5) + 0.1
        grad = (x * 0.6 + y * 0.8) * 0.18  # 면 위의 빛 기울기 (밋밋한 판이 아니게)
        col = GOLD(np.clip(0.42 + 0.5 * shade + grad, 0, 1))
        halo = np.exp(-(np.clip(-sd, 0, None) / 0.1) ** 2) * (sd < 0) * 0.4
        rgb, a = compose([(GOLD(0.66), halo), (col, body), (GOLD(0.96), edge * 0.9)], n)
        out.append(to_img(rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))))
    return out


# ------------------------------------------------------------------ 금가루 (보물이 꽂힘 · 256)

DUST_FRAMES = 8


def dust_frame(f, n=256, seed=171):
    """보물이 땅에 꽂힘 (트레저 레인 · 아스널 게이트): 밑에서 금빛 번쩍 → 옅은 모래 · 금가루 덩이가 옆 · 위로 퍼지며 옅어짐 (윗면 빛) · 튀는 금 반짝임.
    땅 자리 = 그림 아래쪽 1/4 (자바 PhantomFx.dust 가 그만큼 올려 띄움)"""
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
            cx = math.cos(a) * sp * grow
            cy = gy + 0.08 + math.sin(a) * sp * 0.55 * grow
            dens = np.maximum(dens, np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (rr * rr)))
    nz = N.fbm(x * 4 + 3, y * 4, f * 0.4, 4)
    dmask = np.clip((dens + (nz - 0.5) * 0.6 - 0.35) * 2.2, 0, 1) * (1 - sstep(0.5, 1.0, u)) * (y > gy - 0.05)
    light = np.clip(0.55 + (y - gy) * 0.8 + (nz - 0.5) * 0.4, 0, 1)
    low = np.exp(-((y - gy) / 0.15) ** 2)
    col = SAND(light) * (1 - 0.35 * low)[..., None] + GOLD(0.7) * (0.35 * low)[..., None]
    layers = [(col, dmask * 0.7)]
    if f <= 3:
        k = 1 - f / 4.0
        layers.insert(0, (GOLD(0.72), np.exp(-((x / 0.35) ** 2 + ((y - gy) / 0.12) ** 2)) * k * 0.8))
        layers.append((WHITE, np.exp(-((x / 0.12) ** 2 + ((y - gy) / 0.05) ** 2)) * k))
    gl = np.zeros_like(x)
    for j in range(12):
        a = rng.uniform(0.25, math.pi - 0.25)
        v = rng.uniform(0.5, 1.0)
        px = math.cos(a) * v * 0.85 * u
        py = gy + math.sin(a) * v * 0.95 * u - 0.85 * u * u
        tw = 0.6 + 0.4 * math.sin(f * 1.9 + j) ** 2
        if py > gy - 0.02 and u > 0.08:
            gl = np.maximum(gl, star4(x, y, px, py, 0.05 * tw, 0.009, 0.0, aa, short=0.0) * (1 - u) * tw)
    layers.append((GOLD(0.92), np.clip(gl * 1.3, 0, 1)))
    rgb, a = compose(layers, n)
    return rgb, a * edge_fade(x, y, 0.9, 0.99)


def dust():
    return [to_img(*dust_frame(f)) for f in range(DUST_FRAMES)]


# ------------------------------------------------------------------ 목록 (tools/vfx_sprites.py 가 더함)

def sprites():
    p = LIB + "_"
    return (tiled(p + "boom", boom) + tiled(p + "shock", shock) + tiled(p + "crack", crack) + tiled(p + "ring", ring)
            + [(p + "gate", gate, True), (p + "flash", flash, True), (p + "boom_s", boom_s, True), (p + "shock_s", shock_s, True), (p + "crack_s", crack_s, True),
               (p + "charge", charge, True), (p + "sparkle", sparkle, True), (p + "shard", shard, True), (p + "dust", dust, True)])


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
        allsets = {"gate": gate, "flash": flash, "boom": boom, "boom_s": boom_s, "shock": shock, "crack": crack, "ring": ring, "charge": charge,
                   "small": lambda: sparkle() + shard(), "dust": dust}
        preview({k: v() for k, v in allsets.items() if only is None or k in only}, out)
