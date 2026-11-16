"""2026-11-16 아가리 (다크 나이트 — 남은 직업 위르드식): 직업 묶음 el_maw 의 대표 그림 (진홍 · 검은 공허 — jobs.json darkknight [0.9, 0.12, 0.2] · [0.2, 0.1, 0.14]).

예전 그림 (rfx_jdmaw · jtendril · jabyss · jcinder — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 가운데 · 땅 · 뿌리 자리) — 코드의
자리 · 방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT).

- dmaw (jdmaw · 256 · 8칸 — 카메라를 봄, 방패 너비 = 반 크기 × 1.07): 저주받은 방패의 아가리 — 0 다문 방패 (붉은 눈 · 금) → 1 벌어짐 → 2 크게 벌어짐 (붉은 목구멍 ·
  이빨) → 3 콱 다묾 (번쩍) → 4 물어 찢음 (핏빛 튐) → 5 삼킴 → 6 포효 (고리) → 7 연기로 흐려짐
- tendril (jtendril · 512 × 96 · 4칸 되풀이 — 가는 쪽으로 누움, 뿌리 = 왼쪽 끝 · 갈고리 = 오른쪽): 꿈틀거리는 검은 혀 · 흐르는 붉은 핏줄 · 뼈 갈고리
- abyss (jabyss · 384 · 8칸 — 세움, 아래 끝 = 땅 · 턱 반폭 = 반 크기 × 0.92): 땅의 거대한 아가리 — 0 땅이 갈라짐 → 1 턱 끝이 솟음 → 2 크게 벌어짐 (목구멍 빛 · 송곳니)
  → 3 콱 다묾 (번쩍) → 4 ~ 5 짓씹음 (이음매 빛) → 6 물어 터뜨림 (빛살) → 7 가라앉음
- cinder (jcinder · 192 × 384 · 8칸 — 세움, 아래 끝 = 땅): 검붉은 불씨 기둥 (tools/jobvfx/hex.py 의 불기둥 틀을 아가리 빛으로)

  cd tools && python3 -m jobvfx.maw --preview 폴더 [--style maw]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper, tri
from .element import drops_layer, star4, wet

DMAW_FRAMES = 8
TENDRIL_FRAMES = 4
ABYSS_FRAMES = 8
CINDER_FRAMES = 8


def _teeth(x, y, xs, y0, h, w, down, aa):
    """이빨 줄 (가운데 xs · 밑동 y0 · 높이 h · 반폭 w — down 이면 아래를 겨눔)"""
    m = np.zeros_like(x)
    for cx in xs:
        tip = (cx, y0 - h if down else y0 + h)
        m = np.maximum(m, tri(x, y, (cx - w, y0), (cx + w, y0), tip, aa))
    return m


# ------------------------------------------------------------------ 방패의 아가리 (jdmaw — 256 · 방패 반폭 0.54)

OPEN = [0.0, 0.45, 1.0, 0.0, 0.25, 0.0, 0.85, 0.3]


def dmaw_frame(S, f, n=256):
    x, y = grid(n)
    aa = 2.0 / n
    k = 1.0 if f < 7 else 0.5
    hw = 0.5
    top = 0.5
    w = np.where(y > 0.05, hw, hw * np.sqrt(np.clip(1 - ((0.05 - y) / 0.68) ** 2, 0, 1)))
    shield = (1 - sstep(w - aa, w + aa, np.abs(x))) * (y < top) * (y > -0.64)
    horns = np.zeros_like(x)
    for sgn in (1, -1):
        horns = np.maximum(horns, taper(x, y, [(sgn * 0.4, top - 0.04), (sgn * 0.52, top + 0.14), (sgn * 0.5, top + 0.3)], 0.07, 0.006, aa))
    o = OPEN[f]
    ym = -0.02
    gap = o * 0.2 * np.clip(1 - (x / 0.46) ** 2, 0, 1)
    mouth = (np.abs(y - ym) < gap) * shield
    body = np.clip(shield - mouth, 0, 1)
    rim = np.clip(shield - (1 - sstep(w - 0.05 - aa, w - 0.05 + aa, np.abs(x))) * (y < top - 0.05) * (y > -0.58), 0, 1)
    lit = wet(shield, n)
    eyes = np.zeros_like(x)
    for sgn in (1, -1):
        ca, sa = math.cos(sgn * 0.35), math.sin(sgn * 0.35)
        u = (x - sgn * 0.2) * ca + (y - 0.24) * sa
        v = -(x - sgn * 0.2) * sa + (y - 0.24) * ca
        eyes = np.maximum(eyes, 1 - sstep(0.9, 1.1, np.hypot(u / 0.1, v / 0.04)))
    crack = taper(x, y, [(0.0, 0.48), (0.04, 0.36), (-0.03, 0.2), (0.02, 0.08)], 0.012, 0.004, aa) * (1 - o)
    xs = np.linspace(-0.36, 0.36, 7)
    teeth_u = _teeth(x, y, xs, ym + gap.max() if o > 0 else ym, 0.09 * o, 0.04, True, aa) * shield * (o > 0)
    teeth_l = _teeth(x, y, xs + 0.06, ym - (gap.max() if o > 0 else 0), 0.08 * o, 0.035, False, aa) * shield * (o > 0)
    layers = [(S.main(0.35), np.exp(-(np.hypot(x, y) / 0.7) ** 2) * 0.3 * k),
              (S.smoke(np.clip(0.45 + 0.4 * lit, 0, 1)), body * k), (S.main(0.55), rim * body * 0.9 * k), (S.accent(0.55), horns * k),
              (S.main(0.8), mouth * k), (S.main(0.95), mouth * np.exp(-((y - ym) / (0.6 * gap + 1e-3)) ** 2) * 0.7 * k),
              (S.accent(0.9), np.clip(teeth_u + teeth_l, 0, 1) * k), (S.main(0.92), eyes * k), (WHITE, eyes * 0.6 * k), (S.main(0.85), crack * k)]
    if f == 3:
        layers += [(WHITE, star4(x, y, 0, ym, 0.75, 0.05, 0.0, aa)), (S.main(0.9), np.exp(-((y - ym) / 0.02) ** 2) * shield)]
    if f == 4:
        rng = np.random.default_rng(2600)
        items = [(rng.uniform(-0.5, 0.5), ym + rng.uniform(-0.1, 0.2), rng.uniform(0.02, 0.04), rng.uniform(-1, 1), rng.uniform(0, 1.5), 1.0) for i in range(9)]
        dr, dhi = drops_layer(x, y, items, aa)
        layers += [(S.main(0.6), dr), (WHITE, dhi * dr * 0.6)]
    if f == 6:
        r = np.hypot(x, y - ym)
        layers.insert(0, (S.main(0.75), np.exp(-((r - 0.78) / 0.03) ** 2) * 0.8))
    if f == 7:
        N = Noise3(2610)
        layers.insert(0, (S.smoke(0.7), sstep(0.35, 0.65, N.fbm(x * 3, y * 3, 1.0, 3)) * np.exp(-(np.hypot(x, y) / 0.7) ** 2) * 0.6))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 검은 혀 (jtendril — 512 × 96 · 뿌리 x −0.96 · 갈고리 오른쪽)

def tendril_frame(S, f, h=96, w=512, frames=TENDRIL_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.1875 ~ 0.1875
    aa = 2.0 / ww
    ph = f / frames * 2 * math.pi
    xs = np.linspace(-0.96, 0.86, 48)
    amp = 0.06 * (0.2 + 0.8 * (xs + 0.96) / 1.82)
    ys = amp * np.sin(xs * 7 - ph)
    pts = list(zip(xs, ys))
    body = taper(x, y, pts, 0.075, 0.018, aa)
    vein = taper(x, y, pts, 0.016, 0.004, aa)
    flow = vein * (0.5 + 0.5 * np.sin(x * 22 - ph * 2))
    ex, ey = xs[-1], ys[-1]
    hook = taper(x, y, [(ex - 0.02, ey), (ex + 0.08, ey + 0.02), (ex + 0.12, ey + 0.08), (ex + 0.08, ey + 0.12)], 0.03, 0.004, aa)
    lit = wet(body, (hh, ww))
    layers = [(S.main(0.3), np.exp(-((y - np.interp(x, xs, ys)) / 0.12) ** 2) * (x > -0.96) * (x < 0.9) * 0.25), (S.smoke(np.clip(0.4 + 0.45 * lit, 0, 1)), body),
              (S.main(0.75), flow * 0.9), (S.accent(0.85), hook)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.78, sigma=0.008, gain=0.3)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


# ------------------------------------------------------------------ 땅의 아가리 (jabyss — 384 · 아래 끝 = 땅 y −0.95 · 턱 반폭 0.92)

AOPEN = [0.0, 0.35, 1.0, 0.0, 0.0, 0.0, 0.2, 0.0]
ARISE = [0.0, 0.45, 1.0, 1.0, 0.95, 1.0, 1.0, 0.45]


def abyss_frame(S, f, n=384):
    x, y = grid(n)
    aa = 2.0 / n
    g = -0.95
    R = 0.92
    H = 0.95 * ARISE[f]
    o = AOPEN[f]
    k = [1, 1, 1, 1, 1, 1, 1, 0.6][f]
    N = Noise3(2700)
    layers = []
    cracks = np.zeros_like(x)
    if f in (0, 1, 7):
        rng = np.random.default_rng(2701)
        for i in range(7):
            a = rng.uniform(0, math.pi)
            L = rng.uniform(0.3, 0.8)
            cracks = np.maximum(cracks, taper(x, y, [(0, g + 0.02), (L * math.cos(a) * 0.9, g + 0.02 + 0.04 * math.sin(a))], 0.02, 0.004, aa))
        layers += [(S.smoke(0.4), disk(x, y, 0, g, 0.85, 0.06, aa) * k), (S.main(0.85), cracks * k)]
    if H > 0.02:
        ey = (y - g) / H
        dome = ((np.hypot(x / R, ey) < 1) & (y > g)).astype(float)
        split = (np.abs(x) < o * 0.3 * np.clip(1 - ey, 0, 1) ** 0.6 + 0.004).astype(float) * dome
        jaw = np.clip(dome - split, 0, 1)
        spikes = np.zeros_like(x)
        for i in range(9):
            a = math.pi * (0.1 + 0.8 * i / 8)
            px, py = R * math.cos(a), g + H * math.sin(a)
            spikes = np.maximum(spikes, taper(x, y, [(px, py), (px * 1.12, g + H * math.sin(a) * 1.12 + 0.06)], 0.035, 0.004, aa))
        lit = wet(jaw, n)
        tex = N.fbm(x * 5, y * 5, 0.2 * f, 3)
        fangs = np.zeros_like(x)
        if o > 0:
            for j in range(4):
                fy = g + H * (0.2 + 0.18 * j)
                ew = o * 0.3 * np.clip(1 - (fy - g) / H, 0, 1) ** 0.6
                for sgn in (1, -1):
                    fangs = np.maximum(fangs, tri(x, y, (sgn * ew, fy - 0.05), (sgn * ew, fy + 0.05), (sgn * (ew - 0.1 * o), fy), aa))
        throat = split * np.exp(-((y - g - H * 0.3) / (H * 0.5)) ** 2)
        seam = np.exp(-(x / 0.012) ** 2) * dome * (o == 0) * (f in (3, 4, 5))
        layers += [(S.main(0.3), np.exp(-(np.hypot(x / R, (y - g) / (H + 0.2))) ** 2) * 0.35 * k), (S.smoke(np.clip(0.4 + 0.35 * lit + 0.2 * (tex - 0.5), 0, 1)), jaw * k),
                   (S.main(0.55), jaw * sstep(0.6, 0.95, lit) * 0.5 * k), (S.smoke(0.55), spikes * k), (S.main(0.85), throat * k), (WHITE, throat * 0.35 * k),
                   (S.accent(0.88), fangs * k), (S.main(0.95), seam * (0.6 + 0.4 * (f == 3)) * k)]
    if f == 3:
        layers.append((WHITE, star4(x, y, 0, g + H * 0.5, 0.6, 0.05, 0.0, aa)))
    if f == 6:
        from .base import spike
        rays = np.zeros_like(x)
        for i in range(10):
            rays = np.maximum(rays, spike(x, y - g - 0.3, math.pi * (0.05 + 0.9 * i / 9), 1.2, 0.04, 1.3, aa))
        layers.insert(0, (S.main(0.85), rays * 0.7))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.025, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


def cinder_frame(S, f):
    from .hex import pyre_frame
    rgb, a = pyre_frame(S, f, h=384, w=192)
    return rgb, a


FRAMES = {
    "dmaw": (DMAW_FRAMES, dmaw_frame),
    "tendril": (TENDRIL_FRAMES, tendril_frame),
    "abyss": (ABYSS_FRAMES, abyss_frame),
    "cinder": (CINDER_FRAMES, cinder_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "maw"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
