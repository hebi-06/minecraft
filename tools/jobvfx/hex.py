"""2026-11-16 혈주 (워록 — 남은 직업 위르드식): 직업 묶음 el_hex 의 대표 그림 (진홍 피 · 금빛 — jobs.json warlock [0.88, 0.07, 0.24] · [1, 0.76, 0.3]).

예전 그림 (rfx_jlance · jpool · jgeyser · jheart · jpyre — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 땅 · 창끝 자리) — 코드의 자리 ·
방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT).

- lance (jlance · 320 × 96 · 4칸 되풀이 — 가는 쪽으로 누움, 창끝 x 0.94): 젖은 핏빛 창 — 가시 돋은 창날 · 흐르는 윤기 · 꼬리에서 떨어지는 핏방울
- pool (jpool · 256 · 8칸 — 땅에 누움, 반지름 = 반 크기 × 0.85): 0 튀며 퍼짐 · 1 고임 → 2 ~ 5 물결 · 거품 → 6 빨려 들며 줄어듦 · 7 흐려짐
- geyser (jgeyser · 192 × 384 · 8칸 — 세움, 아래 끝 = 땅): 0 땅이 부풀어 튐 · 1 ~ 2 솟구침 → 3 꼭대기 왕관 물보라 → 4 ~ 5 가늘어지며 방울 → 6 땅에 튀는 방울 · 7 흐려짐
- heart (jheart · 256 · 8칸): 0 핏물이 감겨 모임 → 1 ~ 4 고동 (핏줄 맥동 · 금빛 불티) → 5 크게 부풂 → 6 터짐 · 7 방울이 흩어짐
- pyre (jpyre · 192 × 448 · 8칸 — 세움, 아래 끝 = 땅): 0 불붙음 · 1 ~ 2 솟구침 → 3 ~ 5 타오름 (불의 혀 · 불티 · 재) → 6 무너짐 · 7 재 연기

  cd tools && python3 -m jobvfx.hex --preview 폴더 [--style hex] [--only heart,pyre]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper
from .element import drops_layer, star4, wet

LANCE_FRAMES = 4
POOL_FRAMES = 8
GEYSER_FRAMES = 8
HEART_FRAMES = 8
PYRE_FRAMES = 8


def _blood(S, body, n, base=0.42, gain=0.4):
    """젖은 피 덩이: 짙은 몸 · 위 왼쪽 윤기 · 흰 반짝 (알파 몸) → 층 목록"""
    lit = wet(body, n)
    return [(S.main(np.clip(base + gain * lit, 0, 1)), body), (S.main(0.86), body * sstep(0.45, 0.85, lit) * 0.8), (WHITE, body * sstep(0.8, 0.98, lit) * 0.85)]


# ------------------------------------------------------------------ 핏빛 창 (jlance — 320 × 96 · 창끝 x 0.94)

def lance_frame(S, f, h=96, w=320, frames=LANCE_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)
    aa = 2.0 / ww
    ph = f / frames
    shaft = taper(x, y, [(-0.86, 0.0), (0.5, 0.0)], 0.018, 0.03, aa)
    q = np.clip((x - 0.44) / 0.5, 0, 1)
    hw = 0.085 * np.sin(q * math.pi) ** 0.7 * (1 - 0.3 * q)
    blade = ((np.abs(y) < hw) & (x > 0.44) & (x < 0.95)).astype(float)
    barbs = np.zeros_like(x)
    for i, bx in enumerate((0.5, 0.62, 0.74)):
        for sgn in (1, -1):
            barbs = np.maximum(barbs, taper(x, y, [(bx, 0.0), (bx - 0.07, sgn * 0.1)], 0.018, 0.003, aa))
    body = np.clip(np.maximum.reduce([shaft, blade, barbs]), 0, 1)
    flow = body * np.exp(-((((x + 1) * 3 - ph * 3) % 1.0) - 0.5) ** 2 / 0.01) * (np.abs(y) < 0.03)
    rng = np.random.default_rng(2100 + f)
    items = []
    for i in range(6):
        px = -0.7 - 0.25 * ((i / 6 + ph) % 1.0)
        items.append((px + rng.uniform(-0.05, 0.05), -0.03 - 0.12 * ((i / 6 + ph) % 1.0), 0.016, 0.3, -1.0, 1.0 - 0.6 * ((i / 6 + ph) % 1.0)))
    dr, dhi = drops_layer(x, y, items, aa)
    trail = np.exp(-(y / (0.02 + 0.04 * np.clip(-x, 0, 1))) ** 2) * np.clip(-x / 0.9, 0, 1) * 0.35
    edge = blade * sstep(0.55, 0.95, np.abs(y) / np.maximum(hw, 1e-3))
    layers = [(S.main(0.4), trail)] + _blood(S, body, (hh, ww)) + [(S.accent(0.85), edge * 0.8), (WHITE, flow * 0.9), (S.main(0.5), dr), (WHITE, dhi * dr * 0.7)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.75, sigma=0.008, gain=0.35)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


# ------------------------------------------------------------------ 핏물 웅덩이 (jpool — 땅에 누움 · 반지름 = 반 크기 × 0.85)

def pool_frame(S, f, n=256, frames=POOL_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    N = Noise3(2200)
    R = [0.55, 0.85, 0.85, 0.85, 0.85, 0.85, 0.5, 0.42][f]
    k = [1, 1, 1, 1, 1, 1, 0.85, 0.4][f]
    edge_n = N.fbm(np.cos(th) * 2 + 3, np.sin(th) * 2, 0.3 * f, 3) - 0.5
    rim = R * (1 + 0.12 * edge_n)
    body = 1 - sstep(rim - aa * 2, rim + aa * 2, r)
    layers = []
    if f == 0:
        spikes = np.zeros_like(x)
        rng = np.random.default_rng(2201)
        for i in range(11):
            a = i * 2 * math.pi / 11 + rng.uniform(-0.2, 0.2)
            L = rng.uniform(0.25, 0.4)
            spikes = np.maximum(spikes, taper(x, y, [(0.45 * math.cos(a), 0.45 * math.sin(a)), ((0.45 + L) * math.cos(a), (0.45 + L) * math.sin(a))], 0.05, 0.01, aa))
            spikes = np.maximum(spikes, disk(x, y, (0.5 + L) * math.cos(a), (0.5 + L) * math.sin(a), 0.035, 0.035, aa))
        body = np.maximum(body, spikes)
    lit = wet(body, n)
    tone = 0.3 + 0.18 * (N.fbm(x * 3 + 7, y * 3, 0.15 * f, 3) - 0.5) + 0.35 * lit
    spec = np.exp(-(((x + 0.28) / 0.3) ** 2 + ((y - 0.3) / 0.12) ** 2)) * 0.55 + np.exp(-(((x + 0.18) / 0.1) ** 2 + ((y - 0.34) / 0.04) ** 2)) * 0.6
    layers += [(S.main(np.clip(tone, 0, 1)), body * k), (S.main(0.86), body * spec * k), (WHITE, body * np.clip(spec - 0.45, 0, 1) * 1.6 * k),
               (WHITE, body * sstep(0.8, 0.98, lit) * 0.7 * k)]
    if 2 <= f <= 5:
        for j in range(2):
            rr = ((f - 2) / 4 + j * 0.5) % 1.0 * R * 0.9
            layers.append((S.main(0.92), np.exp(-((r - rr) / 0.014) ** 2) * body * (1 - rr / R) * 0.85))
            layers.append((S.accent(0.8), np.exp(-((r - rr - 0.03) / 0.008) ** 2) * body * (1 - rr / R) * 0.5))
        rng = np.random.default_rng(2210 + f)
        for i in range(5):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.1, R * 0.75)
            bub = np.exp(-((np.hypot(x - rr * math.cos(a), y - rr * math.sin(a)) - 0.025) / 0.008) ** 2)
            layers.append((S.accent(0.85), bub * 0.8))
    if f == 6:
        sw = np.exp(-((r - 0.3) / 0.03) ** 2) * (np.sin(th * 3 + r * 12) * 0.5 + 0.5)
        layers.append((S.main(0.8), sw * body * 0.6))
    layers.insert(0, (S.main(0.3), np.exp(-(r / (R + 0.1)) ** 2) * 0.25 * k))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.78, sigma=0.02, gain=0.3)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 핏물 기둥 (jgeyser — 192 × 384 · 아래 끝 = 땅)

def geyser_frame(S, f, h=384, w=192, frames=GEYSER_FRAMES):
    x, y = gridhw(h, w)  # x −0.5 ~ 0.5 · y −1 ~ 1 (땅 −1)
    aa = 2.0 / h
    N = Noise3(2300)
    top = [-0.85, -0.15, 0.5, 0.62, 0.55, 0.4, -0.9, -0.95][f]
    wid = [0.0, 0.17, 0.2, 0.2, 0.13, 0.08, 0.0, 0.0][f]
    k = [1, 1, 1, 1, 0.95, 0.85, 0.8, 0.4][f]
    body = np.zeros_like(x)
    sheath = np.zeros_like(x)
    if wid > 0:
        v = np.clip((y + 1) / max(top + 1, 1e-3), 0, 1)
        wob = N.fbm(y * 2.2 + 0.5 * f, 0.7, 0.25 * f, 3) - 0.5
        ww_ = wid * (1.35 - 0.6 * v) * (1 + 0.75 * wob)
        cut = (y > -1.0) * (y < top)
        xs = x + 0.35 * wid * (N.fbm(y * 1.6, 3.1, 0.2 * f, 2) - 0.5)
        body = (1 - sstep(ww_ - aa, ww_ + aa, np.abs(xs))) * cut
        sheath = (1 - sstep(ww_ * 1.45 - aa, ww_ * 1.45 + aa, np.abs(xs))) * cut * 0.4
        if f in (2, 3):  # 꼭대기 버섯 물보라
            cap = disk(x, y, 0, top, wid * 1.5, wid * 0.75, aa) * sstep(-0.25, 0.25, N.fbm(x * 8, y * 8, 1.0 + f, 2) - 0.35)
            body = np.maximum(body, cap)
    if 1 <= f <= 5 or f in (0, 6):  # 땅의 튐 · 고임
        rx = [0.28, 0.32, 0.36, 0.36, 0.34, 0.3, 0.34, 0.3][f]
        body = np.maximum(body, disk(x, y, 0, -0.97, rx, 0.055, aa))
    rng = np.random.default_rng(2310 + f)
    items = []
    if f in (0, 1):
        for i in range(7):
            a = rng.uniform(0.25, math.pi - 0.25)
            items.append((0.3 * math.cos(a), -0.88 + 0.18 * math.sin(a), 0.022, math.cos(a) * 0.8, 1.0, 1.0))
    if f in (2, 3):
        for i in range(11):
            a = math.pi * (0.08 + 0.84 * i / 10)
            rr = 0.3 + 0.08 * (f - 2)
            items.append((rr * math.cos(a) * 1.2, top + 0.05 + rr * 0.7 * math.sin(a), 0.024, math.cos(a), math.sin(a) - 0.3, 1.0))
    if f in (4, 5, 6):
        for i in range(8):
            items.append((rng.uniform(-0.38, 0.38), rng.uniform(-0.85, max(-0.8, 0.45 - 0.45 * (f - 4))), 0.022, 0.0, -1.0, 0.9))
    dr, dhi = drops_layer(x, y, items, aa)
    lit = wet(body, (h, w))
    streak = body * (np.exp(-((x + 0.3 * wid + 0.06 * (N.fbm(y * 3, 5.0, 0.3 * f, 2) - 0.5)) / (0.18 * wid + 1e-3)) ** 2) * 0.55) * (wid > 0)
    layers = [(S.main(0.55), sheath * k), (S.main(np.clip(0.38 + 0.45 * lit + 0.1 * (N.fbm(x * 5, y * 5, 0.2 * f, 3) - 0.5), 0, 1)), body * k),
              (S.main(0.9), streak * k), (WHITE, body * sstep(0.8, 0.98, lit) * 0.8 * k), (S.main(0.55), dr * k), (WHITE, dhi * dr * 0.7 * k)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.78, sigma=0.02, gain=0.3)
    return rgb, a * (1 - sstep(0.47, 0.5, np.abs(x)))


# ------------------------------------------------------------------ 피의 심장 (jheart — 카메라를 봄 · 심장 반지름 약 반 크기 × 0.55)

def heart_mask(x, y, s, aa):
    X, Y = x / s, (y + 0.08 * s) / s * 1.05 - 0.05
    v = (X * X + Y * Y - 0.36) ** 3 - X * X * Y ** 3 * 0.35
    return 1 - sstep(-aa * 0.4, aa * 0.4, v * 40)


def heart_frame(S, f, n=256, frames=HEART_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    layers = []
    if f == 0:
        sw = np.zeros_like(x)
        for j in range(3):
            a0 = j * 2 * math.pi / 3
            pts = [((0.75 - 0.6 * t) * math.cos(a0 + t * 4.2), (0.75 - 0.6 * t) * math.sin(a0 + t * 4.2)) for t in np.linspace(0, 1, 30)]
            sw = np.maximum(sw, taper(x, y, pts, 0.012, 0.05, aa))
        layers += _blood(S, sw, n) + [(S.main(0.4), np.exp(-(r / 0.25) ** 2) * 0.6)]
    elif f <= 5:
        s = [0, 0.86, 1.0, 0.9, 1.04, 1.18][f]
        body = heart_mask(x, y, s, aa)
        vein = np.zeros_like(x)
        for sgn in (1, -1):
            for (c1, e) in (((0.18, 0.05), (0.42, -0.12)), ((0.1, -0.1), (0.2, -0.42)), ((0.3, 0.2), (0.5, 0.12))):
                p0 = (0.0, 0.2 * s)
                p1 = (sgn * c1[0] * s, c1[1] * s)
                p2 = (sgn * e[0] * s, e[1] * s)
                pts = [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
                       for t in np.linspace(0, 1, 12)]
                vein = np.maximum(vein, taper(x, y, pts, 0.02, 0.006, aa))
        beat = [0, 0.6, 1.0, 0.5, 0.9, 1.0][f]
        layers += [(S.main(0.45), np.exp(-(r / (0.62 * s)) ** 2) * (0.35 + 0.3 * beat))] + _blood(S, body, n, base=0.35, gain=0.45)
        # 핏줄 (부채꼴 줄은 잎맥처럼 보였음 — 영상 전 미리 보기) 대신 고동마다 몸 안에서 번지는 빛 · 테
        pulse = body * np.exp(-((np.hypot(x, y + 0.05 * s) - 0.28 * s * (0.6 + 0.4 * beat)) / (0.05 * s)) ** 2)
        layers += [(S.main(0.8), pulse * 0.45 * beat), (S.main(0.92), (heart_mask(x, y, s * 1.03, aa) - body).clip(0, 1) * 0.8)]
        rng = np.random.default_rng(2410 + f)
        for i in range(4):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.6, 0.85) * s
            layers.append((S.accent(0.95), star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.07, 0.012, a, aa) * beat))
    else:
        k = 1.0 if f == 6 else 0.55
        rng = np.random.default_rng(2420 + f)
        items = []
        for i in range(16):
            a = rng.uniform(0, 2 * math.pi)
            rr = (0.35 if f == 6 else 0.7) + rng.uniform(-0.08, 0.12)
            items.append((rr * math.cos(a), rr * math.sin(a), rng.uniform(0.025, 0.05), math.cos(a) * 2, math.sin(a) * 2, k))
        dr, dhi = drops_layer(x, y, items, aa)
        layers += [(S.main(0.55), dr), (WHITE, dhi * dr * 0.7)]
        if f == 6:
            layers += [(S.accent(0.9), np.exp(-(r / 0.3) ** 2)), (WHITE, star4(x, y, 0, 0, 0.7, 0.06, 0.3, aa)), (S.main(0.8), np.exp(-((r - 0.5) / 0.04) ** 2) * 0.8)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.03, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 핏빛 불기둥 (jpyre — 192 × 448 · 아래 끝 = 땅)

def pyre_frame(S, f, h=448, w=192, frames=PYRE_FRAMES):
    x, y = gridhw(h, w)  # x −0.43 ~ 0.43 · y −1 ~ 1
    aa = 2.0 / h
    N = Noise3(2500)
    top = [-0.6, 0.0, 0.6, 0.85, 0.8, 0.88, 0.1, -0.5][f]
    k = [1, 1, 1, 1, 1, 1, 0.8, 0.5][f]
    t = 0.37 * f
    v = np.clip((y + 1) / max(top + 1, 1e-3), 0, 1.3)
    wob = N.fbm(x * 3.0 + 1, y * 2.2 - t * 2.5, t, 4) - 0.5
    wid = 0.36 * (1 - v ** 1.4) * (0.75 + 0.5 * sstep(0.0, 0.25, v)) + 0.1 * wob
    flame = (1 - sstep(wid - aa * 2, wid + aa * 2, np.abs(x + 0.08 * wob))) * (y > -1.0) * (v < 1.0)
    heat = np.clip(1 - np.abs(x) / np.maximum(wid, 1e-3), 0, 1) * (1 - v * 0.7)
    rng = np.random.default_rng(2510 + f)
    sparks = np.zeros_like(x)
    for i in range(10 if 3 <= f <= 5 else 4):
        sx, sy = rng.uniform(-0.3, 0.3), rng.uniform(-0.95, max(top + 0.1, -0.85))
        sparks = np.maximum(sparks, disk(x, y, sx, sy, 0.012, 0.012, aa))
    smoke = np.zeros_like(x)
    if f >= 6:
        smoke = np.exp(-((x / 0.3) ** 2 + ((y - top + 0.1) / 0.5) ** 2)) * (0.45 if f == 6 else 0.6) * sstep(0.3, 0.6, N.fbm(x * 4, y * 3, t, 3))
    layers = [(S.smoke(0.6), smoke), (S.main(0.35), np.exp(-(x / 0.3) ** 2) * (y < top) * 0.25 * k), (S.main(np.clip(0.45 + 0.4 * heat, 0, 1)), flame * k),
              (S.accent(np.clip(0.55 + 0.4 * heat, 0, 1)), flame * sstep(0.35, 0.8, heat) * k), (WHITE, flame * sstep(0.75, 0.95, heat) * 0.7 * k),
              (S.accent(0.9), sparks * k)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.02, gain=0.4)
    return rgb, a * (1 - sstep(0.4, 0.43, np.abs(x)))


FRAMES = {
    "lance": (LANCE_FRAMES, lance_frame),
    "pool": (POOL_FRAMES, pool_frame),
    "geyser": (GEYSER_FRAMES, geyser_frame),
    "heart": (HEART_FRAMES, heart_frame),
    "pyre": (PYRE_FRAMES, pyre_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "hex"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
