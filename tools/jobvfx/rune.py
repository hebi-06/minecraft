"""2026-11-16 마검사 라인 (남은 직업 위르드식): 마검사 · 아케인 블레이드 (룬 — el_rune, 보랏빛 · 청록) 의 대표 그림.

예전 그림 (rfx_jrunes · jglyph · jninerunes · jpillar · jtear — 흰 그림에 빛 두 겹) 과 **같은 틀** (칸 수 · 가로세로 비 · 장면 차례) — 코드의 자리 · 방향 · 크기 ·
움직임은 그대로 그림만 바뀜 (PhantomFx.PAINT). 도트로 옮기므로 굵은 획 · 짙은 테 · 밝은 심 위주. 원작 룬 문자는 쓰지 않음 (지어낸 각진 글자).

- runes (jrunes · 8 : 1 · 4 되풀이 — 가는 쪽으로 누움): 양끝이 뾰족한 띠 안에 지어낸 각진 글자 여덟 — 장마다 다른 글자가 번쩍
- glyph (jglyph · 3 글자 × 6): 0 ~ 1 획이 그어짐 → 2 ~ 3 테 고리와 함께 빛남 (되풀이) → 4 번쩍 → 5 흩어짐
- ninerunes (jninerunes · 2 : 1 · 12): 위로 굽은 줄의 아홉 글자가 차례로 켜짐 (0 ~ 8) → 모두 번쩍이며 빛줄로 이어짐 (9 ~ 11 되풀이)
- rpillar (jpillar · 1 : 4 · 8 — 세움, 아래 끝 = 땅): 솟는 빛기둥 (0 ~ 1 가는 줄 → 2 ~ 5 굵은 기둥 · 감아 오르는 글자 띠 → 6 ~ 7 흩어짐)
- tear (jtear · 2 : 1 · 8): 허공이 찢어짐 — 가는 금 → 눈 모양으로 벌어지며 안쪽 짙은 보랏빛 · 테의 청록 빛 → 닫힘

  cd tools && python3 -m jobvfx.rune --preview 폴더 [--only runes,glyph]
"""
import math
import sys

import numpy as np

from .base import Noise3, WHITE, bloom, compose, preview, sstep, to_img
from .beast import disk, gridhw, outline, taper

RUNES_FRAMES = 4
GLYPH_FRAMES = 6
GLYPH_KINDS = 3
NINERUNES_FRAMES = 12
PILLAR_FRAMES = 8
TEAR_FRAMES = 8

# 지어낸 각진 글자 (선분 목록 — 칸 −1 ~ 1): 원작 룬 문자와 다르게 (꺾쇠 · 마름모 · 갈퀴)
LETTERS = [
    [((0, -1), (0, 1)), ((0, 0.6), (0.6, 0.2)), ((0, 0.1), (-0.6, -0.3))],
    [((-0.5, -1), (-0.5, 1)), ((0.5, -1), (0.5, 1)), ((-0.5, 0.5), (0.5, -0.5))],
    [((0, 1), (0.6, 0)), ((0.6, 0), (0, -1)), ((0, -1), (-0.6, 0)), ((-0.6, 0), (0, 1))],
    [((0, -1), (0, 1)), ((-0.6, 1), (0, 0.4)), ((0.6, 1), (0, 0.4))],
    [((-0.6, 1), (0.6, 1)), ((0, 1), (0, -1)), ((-0.5, -0.4), (0.5, -0.4))],
    [((-0.6, -1), (0, 1)), ((0, 1), (0.6, -1)), ((-0.35, -0.1), (0.35, -0.1))],
    [((0, -1), (0, 1)), ((0, 1), (0.55, 0.55)), ((0.55, 0.55), (0, 0.1))],
    [((-0.6, 0.8), (0.6, -0.8)), ((-0.6, -0.8), (0.6, 0.8)), ((0, -1), (0, 1))],
    [((-0.5, -1), (-0.5, 1)), ((-0.5, 1), (0.5, 0.3)), ((0.5, 0.3), (-0.5, -0.2)), ((-0.5, -0.2), (0.5, -1))],
]


def letter(x, y, idx, cx, cy, s, w, aa, prog=1.0):
    """글자 idx 를 (cx, cy) · 크기 s (반 높이) · 획 굵기 w 로 — prog (0 ~ 1) 만큼 그어짐"""
    segs = LETTERS[idx % len(LETTERS)]
    n = len(segs)
    m = np.zeros_like(x)
    for i, ((ax, ay), (bx, by)) in enumerate(segs):
        k = np.clip(prog * n - i, 0, 1)
        if k <= 0:
            continue
        p0 = (cx + ax * s * 0.75, cy + ay * s)
        p1 = (cx + (ax + (bx - ax) * k) * s * 0.75, cy + (ay + (by - ay) * k) * s)
        m = np.maximum(m, taper(x, y, [p0, p1], w, w, aa))
    return m


# ------------------------------------------------------------------ 룬 띠 (jrunes)

def runes_frame(S, f, h=64, w=512, frames=RUNES_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.125 ~ 0.125
    aa = 2.0 / ww
    # 띠: 양끝 뾰족 (육각) — 반 높이 0.1
    band = (np.abs(y) < 0.1 - np.clip(np.abs(x) - 0.9, 0, None) * 1.0) * (np.abs(x) < 0.99)
    band = band.astype(float)
    rim = np.clip(band - (np.abs(y) < 0.075 - np.clip(np.abs(x) - 0.9, 0, None)) * (np.abs(x) < 0.965), 0, 1)
    letters = np.zeros_like(x)
    lit = np.zeros_like(x)
    for i in range(8):
        cx = -0.79 + i * (1.58 / 7)
        m = letter(x, y, i + 1, cx, 0.0, 0.05, 0.009, aa)
        letters = np.maximum(letters, m)
        if (i + f) % 4 == 0 or (i * 3 + f) % 7 == 0:
            lit = np.maximum(lit, m)
            lit = np.maximum(lit, np.exp(-((x - cx) ** 2 + y ** 2) / 0.0016) * 0.6)
    layers = [(S.main(0.2), outline((band > 0.5).astype(float), 3) * 0.9), (S.main(0.35), band * 0.95), (S.main(0.78), rim),
              (S.accent(0.7), letters), (S.accent(0.95), lit), (WHITE, np.clip(lit * 1.5 - 0.9, 0, 1))]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.7, sigma=0.008, gain=0.45)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


# ------------------------------------------------------------------ 공중에 새긴 글자 (jglyph — 글자 셋 × 6)

def glyph_frame(S, f, kind=0, n=256):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    idx = [0, 1, 2][kind % 3]
    prog = [0.45, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    g = letter(x, y, idx, 0, 0, 0.55, 0.06, aa, prog)
    layers = []
    if f in (2, 3):
        ring = np.exp(-((r - 0.8) / 0.03) ** 2) * (0.85 if f == 2 else 1.0)
        ticks = np.zeros_like(x)
        th = np.arctan2(y, x)
        for i in range(8):
            a0 = i * math.pi / 4 + (0.2 if f == 3 else 0.0)
            ticks = np.maximum(ticks, np.exp(-((th - a0 + math.pi) % (2 * math.pi) - math.pi) ** 2 / 0.004) * np.exp(-((r - 0.8) / 0.07) ** 2))
        glow = np.exp(-(r / 0.55) ** 2) * (0.35 if f == 2 else 0.5)
        layers += [(S.main(0.4), glow), (S.main(0.7), ring), (S.accent(0.9), ticks)]
    if f == 4:
        layers += [(WHITE, np.exp(-(r / 0.3) ** 2)), (S.accent(0.9), np.exp(-((r - 0.5) / 0.05) ** 2) * 0.9)]
    if f == 5:
        rng = np.random.default_rng(311 + kind)
        bits = np.zeros_like(x)
        for i in range(10):
            a0 = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.35, 0.85)
            bits = np.maximum(bits, disk(x, y, rr * math.cos(a0), rr * math.sin(a0), 0.035, 0.035, aa))
        g = g * 0.0
        layers.append((S.accent(0.85), bits))
    hot = 0.75 if f <= 1 else 0.95
    layers += [(S.main(0.25), outline((g > 0.5).astype(float), 3) * 0.9), (S.main(hot), g), (WHITE, g * (0.6 if f in (3, 4) else 0.2))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.7, sigma=0.015, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 하늘의 아홉 글자 (jninerunes — 2 : 1 · 12)

def ninerunes_frame(S, f, h=256, w=512, frames=NINERUNES_FRAMES):
    x, y = gridhw(h, w)  # x −1 ~ 1 · y −0.5 ~ 0.5
    aa = 2.0 / w
    lit_n = min(9, f + 1)
    letters = np.zeros_like(x)
    hot = np.zeros_like(x)
    pts = []
    for i in range(9):
        t = i / 8
        cx = -0.82 + 1.64 * t
        cy = -0.12 + 0.3 * (1 - (2 * t - 1) ** 2)
        pts.append((cx, cy))
        m = letter(x, y, i, cx, cy, 0.09, 0.016, aa)
        on = i < lit_n
        letters = np.maximum(letters, m * (1.0 if on else 0.25))
        if on and (i == lit_n - 1 or f >= 9):
            hot = np.maximum(hot, m)
            hot = np.maximum(hot, np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / 0.004) * (0.7 if f < 9 else 0.5))
    link = np.zeros_like(x)
    if f >= 9:
        link = taper(x, y, pts, 0.012, 0.012, aa) * (0.7 + 0.3 * ((f - 9) % 2))
    layers = [(S.main(0.25), outline((letters > 0.5).astype(float), 2) * 0.8), (S.main(0.55), letters), (S.accent(0.85), link),
              (S.accent(0.95), np.clip(hot, 0, 1)), (WHITE, np.clip(hot * 1.4 - 0.8, 0, 1))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.7, sigma=0.01, gain=0.45)
    return rgb, a


# ------------------------------------------------------------------ 솟는 빛기둥 (jpillar — 1 : 4 · 아래 끝 = 땅)

def pillar_frame(S, f, h=512, w=128, frames=PILLAR_FRAMES):
    x, y = gridhw(h, w)  # x −0.25 ~ 0.25 · y −1 ~ 1
    aa = 2.0 / h
    nz = Noise3(S.seed + 53)
    if f <= 1:
        width = [0.025, 0.06][f]
        top = [-0.2, 0.9][f]
        fade = 1.0
    elif f <= 5:
        width = 0.13 + 0.015 * math.sin(f * 1.7)
        top = 1.0
        fade = 1.0
    else:
        width = [0.09, 0.05][f - 6]
        top = 1.0
        fade = [0.7, 0.4][f - 6]
    q = np.clip((y + 1) / 2, 0, 1)
    wv = width * (1 - 0.35 * q)
    wob = 0.01 * np.sin(y * 18 - f * 2.3)
    core = (np.abs(x - wob) < wv * 0.45) * (y < top)
    body = (np.abs(x - wob) < wv) * (y < top)
    glow = np.exp(-((x - wob) / (wv * 1.8)) ** 2) * (y < top + 0.05)
    grain = nz.fbm(x * 10, y * 3 - f * 0.6, f * 0.5, oct=3)
    band = np.zeros_like(x)
    if 2 <= f <= 5:
        # 감아 오르는 글자 띠 (사선 줄 — 장마다 올라감)
        u = y * 6 - f * 0.9 + np.arcsin(np.clip(x / (wv * 1.6 + 1e-6), -1, 1)) * 0.6
        band = sstep(0.75, 0.95, np.cos(u * math.pi)) * (np.abs(x) < wv * 1.6) * (y < top)
    base = disk(x, y, 0, -0.97, 0.22, 0.04, aa) * (1.0 if f >= 1 else 0.5)
    layers = [(S.main(0.45), np.clip(glow * 0.8 * fade, 0, 1)), (S.main(0.72), body.astype(float) * fade * (0.8 + 0.2 * grain)),
              (S.accent(0.9), band * fade), (WHITE, core.astype(float) * fade), (S.main(0.6), base * fade)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.01, gain=0.4)
    return rgb, a


# ------------------------------------------------------------------ 허공의 찢어짐 (jtear — 2 : 1 · 8)

def tear_frame(S, f, h=256, w=512, frames=TEAR_FRAMES):
    x, y = gridhw(h, w)  # x −1 ~ 1 · y −0.5 ~ 0.5
    aa = 2.0 / w
    nz = Noise3(S.seed + 71)
    open_ = [0.02, 0.08, 0.18, 0.26, 0.3, 0.28, 0.15, 0.04][f]
    L = [0.5, 0.75, 0.88, 0.92, 0.92, 0.9, 0.8, 0.6][f]
    q = np.clip(1 - (x / L) ** 2, 0, 1)
    jag = 0.02 * np.sin(x * 37 + 1.3) + 0.012 * np.sin(x * 83)
    half = open_ * q ** 0.8
    d = np.abs(y - jag * q) - half
    inner = 1 - sstep(-aa, aa, d)
    rim = np.exp(-(d / 0.018) ** 2) * (np.abs(x) < L)  # 테 (금 양쪽 — 안쪽은 짙은 틈)
    swirl = nz.fbm(x * 4 + f * 0.3, y * 6, f * 0.4, oct=3)
    stars = np.zeros_like(x)
    rng = np.random.default_rng(907)
    for i in range(12):
        sx, sy = rng.uniform(-0.8, 0.8), rng.uniform(-0.2, 0.2)
        stars = np.maximum(stars, disk(x, y, sx, sy, 0.01, 0.01, aa))
    cracks = np.zeros_like(x)
    if f >= 1:
        for i in range(6):
            sx = -L * 0.8 + i * L * 0.32
            sgn = 1 if i % 2 == 0 else -1
            pts = [(sx, sgn * 0.02), (sx + 0.06, sgn * (0.12 + 0.04 * (i % 3))), (sx + 0.03, sgn * (0.22 + 0.04 * (i % 2)))]
            cracks = np.maximum(cracks, taper(x, y, pts, 0.008, 0.002, aa))
    layers = [(S.main(0.4), np.exp(-(np.clip(d, 0, None) / 0.08) ** 2) * (np.abs(x) < L) * 0.6),
              (S.smoke(0.2 + 0.35 * swirl), inner), (S.accent(0.9), stars * inner),
              (S.accent(0.8), rim), (WHITE, rim * (rim > 0.85)), (S.main(0.8), cracks * 0.8)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.7, sigma=0.01, gain=0.45)
    return rgb, a


# ------------------------------------------------------------------ 묶음

FRAMES = {
    "runes": (RUNES_FRAMES, runes_frame),
    "glyph": (GLYPH_FRAMES * GLYPH_KINDS, lambda S, f: glyph_frame(S, f % GLYPH_FRAMES, f // GLYPH_FRAMES)),
    "ninerunes": (NINERUNES_FRAMES, ninerunes_frame),
    "rpillar": (PILLAR_FRAMES, pillar_frame),  # 공용 pillar (원소 빛기둥 · 물기둥) 와 이름이 겹치지 않게
    "tear": (TEAR_FRAMES, tear_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "rune"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
