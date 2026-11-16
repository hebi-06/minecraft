"""2026-11-16 번개 · 빛 창 (남은 직업 위르드식 — 엘리멘탈 블레이드 · 성기사 · 썬더로드 · 타이런트 · 스톰 랜서가 함께 씀): 직업 묶음 빛으로 칠함.

예전 그림 (rfx_jthunder2 · jvolt2 · jspear2) 과 **같은 틀** (칸 수 · 가로세로 비 · 땅 · 머리 자리) — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT).

- thunder (jthunder2 · 1 : 4 · 6 — 세움, 아래 끝 = 땅): 0 하늘에서 내려오는 선도 줄기 → 1 땅에 닿아 번쩍 → 2 ~ 3 굵은 벼락 · 곁가지 깜빡임 → 4 옅어짐 → 5 남은 불똥
- volt (jvolt2 · 8 — 땅에 누움, 반지름 0.8): 0 ~ 1 고리가 생김 → 2 ~ 5 고리를 따라 튀는 번개 호 (되풀이) → 6 ~ 7 사그라짐
- spear (jspear2 · 6 : 1 · 4 되풀이 — 가는 쪽으로 누움, 창끝 x 0.98): 빛의 창 — 잎 모양 창끝 · 날밑 · 자루를 감는 번개 띠

  cd tools && python3 -m jobvfx.storm --preview 폴더 [--style element] [--only thunder,volt]
"""
import math
import sys

import numpy as np

from .base import WHITE, bloom, compose, preview, sstep, to_img
from .beast import disk, gridhw, taper

THUNDER_FRAMES = 6
VOLT_FRAMES = 8
SPEAR_FRAMES = 4


def bolt_path(rng, x0, y0, x1, y1, n=14, amp=0.06):
    """번개 꺾은 선 (x0, y0) → (x1, y1): 마디 n · 옆으로 amp 만큼 들쭉날쭉"""
    pts = []
    for i in range(n + 1):
        t = i / n
        jit = 0 if i in (0, n) else rng.uniform(-amp, amp)
        pts.append((x0 + (x1 - x0) * t + jit, y0 + (y1 - y0) * t + rng.uniform(-amp, amp) * 0.15 * (0 < i < n)))
    return pts


# ------------------------------------------------------------------ 벼락 (jthunder2 — 세움 · 1 : 4)

def thunder_frame(S, f, h=512, w=128, frames=THUNDER_FRAMES):
    x, y = gridhw(h, w)  # x −0.25 ~ 0.25 · y −1 ~ 1
    aa = 2.0 / h
    rng = np.random.default_rng(1700 + (f if f >= 2 else 0))
    main = bolt_path(rng, 0.0, 1.0, 0.0, -0.98, n=16, amp=0.09)
    if f == 0:
        cut = 7
        main = main[:cut]
    lw = [0.012, 0.03, 0.026, 0.022, 0.012, 0.0][f]
    bolt = taper(x, y, main, lw, lw * 0.7, aa) if lw > 0 else np.zeros_like(x)
    core = taper(x, y, main, lw * 0.45, lw * 0.3, aa) if lw > 0 else np.zeros_like(x)
    branches = np.zeros_like(x)
    if 1 <= f <= 3:
        for i in range(4):
            j = rng.integers(3, 12)
            bx, by = main[j]
            sgn = 1 if i % 2 == 0 else -1
            br = bolt_path(rng, bx, by, bx + sgn * rng.uniform(0.08, 0.18), by - rng.uniform(0.15, 0.35), n=5, amp=0.03)
            branches = np.maximum(branches, taper(x, y, br, lw * 0.5, 0.003, aa))
    glow = np.zeros_like(x)
    if lw > 0:
        d = np.full_like(x, 9.0)
        for (ax, ay), (bx, by) in zip(main[:-1], main[1:]):
            ex, ey = bx - ax, by - ay
            L2 = ex * ex + ey * ey + 1e-12
            t = np.clip(((x - ax) * ex + (y - ay) * ey) / L2, 0, 1)
            d = np.minimum(d, np.hypot(x - (ax + t * ex), y - (ay + t * ey)))
        glow = np.exp(-(d / (0.05 + 0.03 * (f == 1))) ** 2) * [0.5, 0.9, 0.75, 0.6, 0.3, 0][f]
    flash = np.zeros_like(x)
    if f in (1, 2):
        flash = disk(x, y, 0, -0.97, 0.22 if f == 1 else 0.16, 0.05, aa)
    sparks = np.zeros_like(x)
    if f >= 4:
        for i in range(8):
            sx, sy = rng.uniform(-0.2, 0.2), rng.uniform(-0.98, -0.7 + 0.5 * (f == 4))
            sparks = np.maximum(sparks, disk(x, y, sx, sy, 0.012, 0.012, aa))
    layers = [(S.main(0.5), np.clip(glow, 0, 1)), (S.main(0.75), np.clip(bolt + branches, 0, 1)), (S.accent(0.95), np.clip(flash, 0, 1)),
              (WHITE, np.clip(core + flash * 0.7, 0, 1)), (S.accent(0.9), sparks)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.01, gain=0.45)
    return rgb, a


# ------------------------------------------------------------------ 번개 고리 (jvolt2 — 땅에 누움)

def volt_frame(S, f, n=256, frames=VOLT_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    R = 0.8
    k = [0.35, 0.75, 1, 1, 1, 1, 0.6, 0.25][f]
    ring = np.exp(-((r - R) / 0.025) ** 2) * k
    halo = np.exp(-((r - R) / 0.09) ** 2) * 0.5 * k
    arcs = np.zeros_like(x)
    rng = np.random.default_rng(1800 + f)
    if 2 <= f <= 5 or f == 1:
        for i in range(7):
            a0 = rng.uniform(0, 2 * math.pi)
            span = rng.uniform(0.35, 0.8)
            m = 8
            pts = []
            for s in range(m + 1):
                a = a0 + span * s / m
                rr = R + rng.uniform(-0.09, 0.09) * (0 < s < m)
                pts.append((rr * math.cos(a), rr * math.sin(a)))
            arcs = np.maximum(arcs, taper(x, y, pts, 0.014, 0.006, aa))
        for i in range(5):
            a0 = rng.uniform(0, 2 * math.pi)
            pts = [(R * math.cos(a0), R * math.sin(a0)), ((R + 0.12) * math.cos(a0 + 0.06), (R + 0.12) * math.sin(a0 + 0.06)),
                   ((R + 0.06) * math.cos(a0 + 0.12), (R + 0.06) * math.sin(a0 + 0.12))]
            arcs = np.maximum(arcs, taper(x, y, pts, 0.01, 0.003, aa))
    inner = np.exp(-(r / 0.6) ** 2) * 0.18 * k
    layers = [(S.main(0.35), inner), (S.main(0.5), halo), (S.main(0.8), ring), (S.accent(0.9), arcs), (WHITE, np.clip(arcs * 1.5 - 0.6, 0, 1))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.012, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 빛의 창 (jspear2 — 6 : 1 · 창끝 x 0.98)

def spear_frame(S, f, h=64, w=384, frames=SPEAR_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −1/6 ~ 1/6
    aa = 2.0 / ww
    ph = f / frames * 2 * math.pi
    shaft = taper(x, y, [(-0.95, 0.0), (0.66, 0.0)], 0.014, 0.018, aa)
    head = np.zeros_like(x)
    # 잎 모양 창끝 (0.68 ~ 0.98)
    q = np.clip((x - 0.66) / 0.32, 0, 1)
    hw = 0.075 * np.sin(q * math.pi) ** 0.8 * (1 - 0.25 * q)
    head = ((np.abs(y) < hw) * (x > 0.66) * (x < 0.985)).astype(float)
    guard = ((np.abs(x - 0.64) < 0.018) * (np.abs(y) < 0.07)).astype(float)
    wrap = np.zeros_like(x)
    for i in range(2):
        pts = [(xx, 0.035 * math.sin(xx * 18 + ph + i * math.pi)) for xx in np.linspace(-0.9, 0.6, 40)]
        wrap = np.maximum(wrap, taper(x, y, pts, 0.008, 0.008, aa))
    trail = np.exp(-(y / (0.02 + 0.03 * np.clip(-x, 0, 1))) ** 2) * np.clip((0.0 - x) / 0.95, 0, 1) * 0.4
    edge = head * sstep(0.6, 0.95, np.abs(y) / np.maximum(hw, 1e-3))
    layers = [(S.main(0.45), trail), (S.main(0.75), np.clip(shaft + guard, 0, 1)), (S.main(0.85), head), (WHITE, head * (1 - edge) * 0.8),
              (S.accent(0.9), wrap * 0.9), (WHITE, taper(x, y, [(-0.9, 0), (0.95, 0)], 0.005, 0.005, aa) * 0.8)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.72, sigma=0.008, gain=0.45)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


FRAMES = {
    "thunder": (THUNDER_FRAMES, thunder_frame),
    "volt": (VOLT_FRAMES, volt_frame),
    "spear": (SPEAR_FRAMES, spear_frame),
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
