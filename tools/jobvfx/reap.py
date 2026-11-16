"""2026-11-16 사신 라인 (사신 · 소울 리퍼 · 엑시큐셔너 — 남은 직업 위르드식): 직업 묶음 el_reap (청록 혼) · el_soulreap (하늘빛 · 남빛) · el_exec (금빛 · 자줏빛) 의 대표 그림.

예전 그림 (rfx_jwail · jreaper2 · jxmark — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 가운데) — 코드의 자리 · 방향 · 크기 · 움직임은
그대로 (PhantomFx.PAINT). 혼불 · 해골 · 망자의 손 · 연기는 tools/jobvfx/soul.py 를 묶음 빛으로.

- wail (jwail · 192 · 8칸): 망자의 비명 — 0 ~ 4 연기가 비명 지르는 얼굴이 됨 → 5 ~ 7 찢겨 흩어짐
- reaper (jreaper2 · 192 × 256 · 8칸 — 세움): 사신 그림자 — 0 ~ 3 서서 흔들림 (되풀이) · 4 ~ 7 낫을 휘두름
- xmark (jxmark · 384 · 8칸 — 땅에 누움, 획 끝 반지름 = 반 크기 × 0.9): 처형 표식 — 0 ~ 2 붓 획 둘이 그어짐 → 3 ~ 5 맥동 → 6 번쩍 → 7 타 들어가며 흐려짐

  cd tools && python3 -m jobvfx.reap --preview 폴더 [--style reap]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper, tri
from .element import star4, wet

WAIL_FRAMES = 8
REAPER_FRAMES = 8
XMARK_FRAMES = 8


# ------------------------------------------------------------------ 망자의 비명 (jwail — 192)

def wail_frame(S, f, n=192, frames=WAIL_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    N = Noise3(4000)
    t = f / (frames - 1)
    form = [0.25, 0.5, 0.8, 1.0, 1.0, 0.8, 0.5, 0.25][f]
    stretch = [1.0, 1.0, 1.0, 1.05, 1.2, 1.25, 1.3, 1.3][f]
    tear = [0, 0, 0, 0, 0, 0.25, 0.4, 0.55][f]
    X, Y = x, y / stretch
    swirl = N.fbm(X * 2.5 + math.cos(t * 3), Y * 2.5 + math.sin(t * 3), t * 2, 4)
    head = np.exp(-((X / 0.48) ** 2 + ((Y - 0.05) / 0.6) ** 2) * 2.2) * form
    dens = head * (0.75 + 0.5 * swirl) - tear * (0.35 + 0.4 * (np.abs(x) < 0.12 + 0.3 * tear) * sstep(0.3, 0.7, swirl))
    body = sstep(0.18, 0.42, dens)
    eyes = (disk(X, Y, -0.17, 0.2, 0.09, 0.12, aa) + disk(X, Y, 0.17, 0.2, 0.09, 0.12, aa)) * (f >= 2) * (f <= 5)
    mouth = disk(X, Y, 0, -0.22, 0.12 * (0.6 + 0.4 * form), 0.2 * (0.5 + 0.8 * min(1, f / 4)), aa) * (f >= 2) * (f <= 5)
    holes = np.clip(eyes + mouth, 0, 1) * body
    lit = wet(body, n)
    layers = [(S.main(0.4), sstep(0.05, 0.3, dens) * 0.45), (S.main(np.clip(0.45 + 0.35 * lit + 0.15 * swirl, 0, 1)), body), (S.smoke(0.15), holes),
              (S.accent(0.85), np.clip(eyes * 0.0 + holes * 0, 0, 1))]
    glow_eyes = (disk(X, Y, -0.17, 0.2, 0.035, 0.045, aa) + disk(X, Y, 0.17, 0.2, 0.035, 0.045, aa)) * (f >= 3) * (f <= 5)
    layers.append((S.accent(0.95), glow_eyes))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.35)
    return rgb, a * (1 - sstep(0.94, 0.99, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 사신 그림자 (jreaper2 — 192 × 256 · 세움)

SCY = [None, None, None, None, 100, 140, 200, 240]


def reaper_frame(S, f, h=256, w=192, frames=REAPER_FRAMES):
    x, y = gridhw(h, w)  # x −0.75 ~ 0.75 · y −1 ~ 1
    aa = 2.0 / h
    N = Noise3(4100)
    sway = 0.03 * math.sin(f / 4 * 2 * math.pi) if f < 4 else 0.0
    xs = x - sway * (y + 1) / 2
    # 두건 · 겉옷 (아래로 넓어짐 · 해진 자락)
    top = 0.62
    wid = 0.16 + 0.32 * np.clip((top - y) / 1.55, 0, 1) ** 0.8
    hem = -0.92 + 0.06 * np.sin(x * 14 + f * 1.3) + 0.04 * (N.fbm(x * 6, 1.0, f * 0.3, 2) - 0.5)
    robe = (1 - sstep(wid - aa, wid + aa, np.abs(xs))) * (y < top) * (y > hem)
    hood = disk(xs, y, 0, top + 0.02, 0.2, 0.2, aa)
    body = np.clip(robe + hood, 0, 1)
    face = disk(xs, y, 0, top - 0.02, 0.11, 0.13, aa)
    eyes = disk(xs, y, -0.045, top, 0.022, 0.016, aa) + disk(xs, y, 0.045, top, 0.022, 0.016, aa)
    lit = wet(body, (h, w))
    rimg = np.clip(body - (1 - sstep(wid - 0.05 - aa, wid - 0.05 + aa, np.abs(xs))) * (y < top - 0.05) * (y > hem + 0.05), 0, 1) * (y < top)
    # 낫 (서 있을 때는 몸 오른쪽에 세움 · 휘두르면 날이 돌아 내려옴)
    hx, hy = (0.32, -0.2) if SCY[f] is None else (0.12, -0.1)
    ang = math.radians(75 if SCY[f] is None else SCY[f])
    L = 1.15 if SCY[f] is None else 0.8
    tx, ty = hx + L * math.cos(ang) * (0.55 if SCY[f] is None else 0.7), hy + L * math.sin(ang)
    handle = taper(x, y, [(hx - L * math.cos(ang) * 0.25, hy - L * math.sin(ang) * 0.45), (tx, ty)], 0.022, 0.018, aa)
    ca, sa = math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2)
    blade_pts = [(tx, ty)] + [(tx - 0.42 * math.cos(ang - 0.25 * k) * 0.0 + 0.5 * k * ca - 0.12 * k * k * math.cos(ang), ty + 0.5 * k * sa - 0.12 * k * k * math.sin(ang))
                              for k in np.linspace(0.1, 1.0, 8)]
    blade = taper(x, y, blade_pts, 0.05, 0.006, aa)
    trail = np.zeros_like(x)
    if f in (5, 6):
        r = np.hypot(x - hx, y - hy)
        th = np.arctan2(y - hy, x - hx)
        a0 = ang - math.radians(60)
        thw = np.angle(np.exp(1j * (th - a0)))
        trail = np.exp(-((r - 0.75) / 0.1) ** 2) * ((thw > 0) & (thw < math.radians(60))) * 0.8
    layers = [(S.main(0.45), trail), (S.smoke(np.clip(0.3 + 0.35 * lit, 0, 1)), body), (S.main(0.55), rimg * 0.8), (S.smoke(0.05), face),
              (S.accent(0.95), np.clip(eyes, 0, 1)), (S.smoke(0.6), handle), (S.main(0.85), blade), (WHITE, blade * 0.4)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.75, sigma=0.02, gain=0.35)
    return rgb, a * (1 - sstep(0.72, 0.75, np.abs(x)))


# ------------------------------------------------------------------ 처형 표식 (jxmark — 384 · 땅에 누움)

def _brush(x, y, a, L, prog, w, aa):
    ca, sa = math.cos(a), math.sin(a)
    u = x * ca + y * sa
    v = -x * sa + y * ca
    t = (u + L) / (2 * L)
    ww = w * (0.35 + 0.65 * np.sin(np.pi * np.clip(t, 0, 1)) ** 0.6)
    return (1 - sstep(ww - aa, ww + aa, np.abs(v))) * (t > 0) * (t < prog), t


def xmark_frame(S, f, n=384, frames=XMARK_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    L = 0.9
    p1 = [0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    p2 = [0.0, 0.3, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    k = [1, 1, 1, 1, 1, 1, 1, 0.45][f]
    pulse = [0, 0, 0, 0.6, 1.0, 0.6, 1.2, 0.2][f]
    s1, _ = _brush(x, y, math.radians(45), L, p1, 0.1, aa)
    s2, _ = _brush(x, y, math.radians(-45), L, p2, 0.1, aa)
    strokes = np.clip(s1 + s2, 0, 1)
    from scipy import ndimage
    rim = np.clip(ndimage.maximum_filter(strokes, size=9) - strokes, 0, 1)
    ring = np.exp(-((r - 0.96) / 0.012) ** 2) * (np.sin(th * 24) > -0.6)
    ticks = np.zeros_like(x)
    for i in range(12):
        a = i * math.pi / 6
        ticks = np.maximum(ticks, taper(x, y, [(0.88 * math.cos(a), 0.88 * math.sin(a)), (0.96 * math.cos(a), 0.96 * math.sin(a))], 0.01, 0.01, aa))
    layers = [(S.accent(0.6), np.exp(-(r / 0.8) ** 2) * 0.2 * (0.5 + 0.5 * pulse) * k), (S.accent(0.7), np.clip(ring + ticks, 0, 1) * 0.8 * k),
              (S.accent(0.85), rim * (0.4 + 0.5 * pulse) * k), (S.main(0.75), strokes * k), (WHITE, strokes * sstep(0.6, 1.2, pulse) * 0.5)]
    if f == 6:
        layers += [(WHITE, star4(x, y, 0, 0, 0.9, 0.05, math.pi / 4, aa)), (S.accent(0.9), np.exp(-(r / 0.35) ** 2) * 0.7)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.45)
    return rgb, a * (1 - sstep(0.97, 0.995, r))


FRAMES = {
    "wail": (WAIL_FRAMES, wail_frame),
    "reaper": (REAPER_FRAMES, reaper_frame),
    "xmark": (XMARK_FRAMES, xmark_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "reap"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
