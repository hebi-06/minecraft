"""2026-11-16 푸른 불사조 (피닉스 가드 — 남은 직업 위르드식): 직업 묶음 el_phoenix (하늘빛 불꽃 · 흰빛 — jobs.json phoenix [0.26, 0.62, 1] · [0.9, 0.97, 1]) 의 대표 그림.

예전 그림 (rfx_jfwings · jphoenix · jfury — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 등 · 가슴 · 꼭짓점 자리) — 코드의 자리 · 방향 · 크기 ·
움직임은 그대로 (PhantomFx.PAINT). 깃털 · 불씨 기둥은 lance.py · maw.py (hex.pyre) 를 피닉스 빛으로.

- fwings (jfwings · 512 × 416 · 8칸 — 등 = 칸 가운데에서 반 너비 × 0.21 아래): 0 작은 날개 → 1 펼쳐짐 → 2 ~ 5 날갯짓 (깃 끝의 불의 혀) → 6 감쌈 → 7 불꽃 깃털로 흩어짐
- phoenix (jphoenix · 512 · 8칸 — 가슴 = 칸 가운데): 0 감겨 드는 불길 속에 생겨남 → 1 날개를 활짝 → 2 ~ 4 날갯짓 → 5 내리꽂음 (머리가 아래 · 날개 V) → 6 터짐 → 7 흩어짐
- fury (jfury · 384 · 8칸 — 땅에 누움, 꼭짓점 = 칸 왼쪽 가운데 · 부채 반각 40°): 0 번쩍 → 1 반쯤 퍼짐 → 2 다 퍼짐 → 3 달아오름 → 4 ~ 5 사그라짐 → 6 ~ 7 재 연기

  cd tools && python3 -m jobvfx.phoenix --preview 폴더 [--style phoenix]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper
from .element import star4, wet

FWINGS_FRAMES = 8
PHOENIX_FRAMES = 8
FURY_FRAMES = 8


def _wing(x, y, root, span, lift, curl, aa, N, t):
    """한쪽 날개 (root 에서 바깥으로 span · 위로 lift · 감김 curl) — 깃 일곱 (끝으로 길어짐) · 깃 끝의 불의 혀 알파"""
    sgn = 1 if span > 0 else -1
    S = abs(span)
    body = np.zeros_like(x)
    tips = np.zeros_like(x)
    ends = []
    for i in range(7):
        q = i / 6
        bx = root[0] + sgn * S * (0.15 + 0.55 * q)
        by = root[1] + lift * (0.6 + 0.4 * q) * S - curl * q * q * S * 0.8
        L = S * (0.35 + 0.35 * q)
        ang = math.radians(90 + sgn * (-20 - 60 * q)) - sgn * curl * 1.2
        ex, ey = bx + L * math.cos(ang) * 0.6 * sgn * sgn, by + L * math.sin(ang)
        ex = bx + sgn * abs(L * math.cos(ang)) * 0.9
        feather = taper(x, y, [(root[0] + sgn * S * 0.1 * q, root[1]), (bx, by), (ex, ey)], 0.06 * S + 0.02, 0.012, aa)
        body = np.maximum(body, feather)
        ends.append((ex, ey))
        wob = 0.03 * math.sin(i * 1.7 + t * 3)
        tips = np.maximum(tips, taper(x, y, [(ex, ey), (ex + sgn * 0.02, ey + 0.08 * S + wob)], 0.03 * S, 0.004, aa))
    # 깃 사이를 메운 날개 막 (뿌리 → 깃 끝들 → 뿌리 아래) — 깃만 있으면 빗처럼 보였음 (미리 보기)
    from matplotlib.path import Path
    poly = [root] + ends[::-1] + [(root[0] + sgn * S * 0.25, root[1] - 0.12 * S)]
    inside = Path(poly).contains_points(np.stack([x.ravel(), y.ravel()], -1)).reshape(x.shape).astype(float)
    body = np.maximum(body * 1.0, inside * 0.85)
    return body, tips


def fwings_frame(S, f, h=416, w=512, frames=FWINGS_FRAMES):
    x, y = gridhw(h, w)  # x −1 ~ 1 · y −0.8125 ~ 0.8125
    aa = 2.0 / w
    N = Noise3(6000)
    root = (0.0, -0.21)
    span = [0.35, 0.8, 0.95, 0.9, 0.85, 0.9, 0.6, 0.9][f]
    lift = [0.2, 0.5, 0.65, 0.45, 0.25, 0.45, 0.1, 0.4][f]
    curl = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0][f]
    k = [0.7, 1, 1, 1, 1, 1, 1, 0.45][f]
    bl, tl = _wing(x, y, root, -span, lift, curl, aa, N, f * 0.3)
    br, tr = _wing(x, y, root, span, lift, curl, aa, N, f * 0.3)
    body = np.clip(bl + br, 0, 1)
    tips = np.clip(tl + tr, 0, 1)
    if f == 7:
        body = body * sstep(0.45, 0.6, N.fbm(x * 8, y * 8, 2.0, 2))
    lit = wet(body, (h, w))
    grad = np.clip(0.45 + 0.4 * np.abs(x) / max(span, 0.1) + 0.2 * lit, 0, 1)
    layers = [(S.main(0.45), np.exp(-(np.hypot(x, y - root[1]) / 0.9) ** 2) * 0.25 * k), (S.main(grad), body * k), (S.accent(0.9), tips * k), (WHITE, tips * 0.4 * k),
              (S.accent(0.85), np.exp(-(np.hypot(x, y - root[1]) / 0.08) ** 2) * k)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.02, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 1.0, np.abs(x))) * (1 - sstep(0.77, 0.81, np.abs(y)))


def phoenix_frame(S, f, n=512, frames=PHOENIX_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    N = Noise3(6100)
    k = [0.6, 1, 1, 1, 1, 1, 1, 0.45][f]
    dive = f == 5
    yy = -y if dive else y
    layers = []
    if f <= 5:
        flap = [0.5, 0.9, 0.95, 0.5, 0.15, 0.9][f]
        span = 0.82
        bl, tl = _wing(x, yy, (0, 0.05), -span, 0.3 + 0.5 * (flap - 0.5), 0, aa, N, f * 0.3)
        br, tr = _wing(x, yy, (0, 0.05), span, 0.3 + 0.5 * (flap - 0.5), 0, aa, N, f * 0.3)
        wings = np.clip(bl + br, 0, 1)
        tips = np.clip(tl + tr, 0, 1)
        bodyb = np.maximum(taper(x, yy, [(0, 0.42), (0, 0.2), (0, -0.05), (0, -0.25)], 0.07, 0.04, aa), disk(x, yy, 0, 0.08, 0.13, 0.22, aa))
        head = disk(x, yy, 0, 0.45, 0.07, 0.07, aa)
        crest = taper(x, yy, [(0, 0.5), (-0.04, 0.6), (-0.1, 0.66)], 0.03, 0.006, aa)
        tail = np.zeros_like(x)
        for j in range(5):
            a = math.radians(-90 + (j - 2) * 14)
            tail = np.maximum(tail, taper(x, yy, [(0, -0.22), (0.55 * math.cos(a), -0.22 + 0.55 * math.sin(a))], 0.05, 0.01, aa))
        bird = np.clip(wings + bodyb + head + crest + tail, 0, 1)
        lit = wet(bird, n)
        layers += [(S.main(0.4), np.exp(-(np.hypot(x, yy) / 0.8) ** 2) * 0.3 * k), (S.main(np.clip(0.45 + 0.35 * lit + 0.15 * np.abs(x), 0, 1)), bird * k),
                   (S.accent(0.9), np.clip(tips + crest, 0, 1) * k), (WHITE, (bodyb + head).clip(0, 1) * 0.35 * k), (WHITE, disk(x, yy, 0.025, 0.47, 0.012, 0.01, aa))]
        if f == 0:
            sw = np.zeros_like(x)
            for j in range(3):
                pts = [((0.8 - 0.6 * t) * math.cos(j * 2.1 + t * 4), (0.8 - 0.6 * t) * math.sin(j * 2.1 + t * 4)) for t in np.linspace(0, 1, 24)]
                sw = np.maximum(sw, taper(x, y, pts, 0.01, 0.04, aa))
            layers.insert(0, (S.accent(0.8), sw))
    else:
        r = np.hypot(x, y + 0.3)
        kk = 1.0 if f == 6 else 0.5
        rng = np.random.default_rng(6110 + f)
        streaks = np.zeros_like(x)
        for j in range(12):
            a = rng.uniform(0.1, math.pi - 0.1)
            L = rng.uniform(0.4, 0.8) * (1.0 if f == 6 else 1.2)
            streaks = np.maximum(streaks, taper(x, y, [(0, -0.3), (L * math.cos(a), -0.3 + L * math.sin(a))], 0.03, 0.004, aa))
        layers += [(S.main(0.7), np.exp(-((r - (0.5 if f == 6 else 0.8)) / 0.05) ** 2) * kk), (S.accent(0.9), streaks * kk), (WHITE, np.exp(-(r / 0.25) ** 2) * kk * (f == 6))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


def fury_frame(S, f, n=384, frames=FURY_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    N = Noise3(6200)
    ax = -0.917
    X = x - ax
    r = np.hypot(X, y)
    th = np.arctan2(y, X)
    reach = [0.15, 0.85, 1.54, 1.54, 1.54, 1.54, 1.54, 1.54][f]
    k = [1, 1, 1, 1, 0.8, 0.55, 0.35, 0.2][f]
    half = math.radians(40)
    fan = (np.abs(th) < half) * (r < reach)
    edge = 1 - sstep(half - 0.12, half, np.abs(th))
    n1 = N.fbm(r * 4 - f * 0.6, th * 3, f * 0.3, 4)
    dens = fan * edge * (0.6 + 0.6 * n1) * (1 - sstep(reach * 0.75, reach, r))
    heat = np.clip(dens * (1.2 - r / 1.6), 0, 1)
    layers = [(S.main(np.clip(0.4 + 0.5 * heat, 0, 1)), sstep(0.1, 0.4, dens) * k), (S.accent(0.9), sstep(0.55, 0.85, heat) * k), (WHITE, sstep(0.85, 1.0, heat) * 0.6 * k)]
    if f == 0:
        layers.append((WHITE, star4(x, y, ax + 0.05, 0, 0.3, 0.04, 0.0, aa)))
    if f == 3:
        layers.append((S.accent(0.95), fan * edge * np.exp(-((r - 1.1) / 0.06) ** 2) * 0.8))
    if f >= 6:
        layers.insert(0, (S.smoke(0.6), sstep(0.4, 0.7, N.fbm(x * 3, y * 3, f * 0.5, 3)) * fan * 0.5))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


FRAMES = {
    "fwings": (FWINGS_FRAMES, fwings_frame),
    "phoenix": (PHOENIX_FRAMES, phoenix_frame),
    "fury": (FURY_FRAMES, fury_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "phoenix"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
