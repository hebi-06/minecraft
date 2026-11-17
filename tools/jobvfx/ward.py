"""2026-11-16 수호 (수호기사 — 남은 직업 위르드식): 직업 묶음 el_ward 의 대표 그림 (강철 푸른빛 · 흰 빛 — jobs.json guardian [0.46, 0.62, 0.84] · [0.95, 0.97, 1]).

예전 그림 (rfx_jbuckler2 · jbifrost · jchain2 — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비) — 코드의 자리 · 방향 · 크기 · 움직임은
그대로 (PhantomFx.PAINT). 빛의 방패 (jaegis2) 는 arcane.aegis_frame 을 수호 빛으로.

- buckler (jbuckler2 · 4칸 되풀이 — 카메라를 봄): 도는 둥근 방패 (세로축으로 돌아 너비 1 · 0.7 · 0.25 · 0.7) — 강철 테 · 흰 빛 별 문장 · 테의 징
- bifrost (jbifrost · 512 × 64 · 6칸 되풀이 — 가는 쪽으로 누움): 빛의 다리 띠 — 흰 · 푸른 빛줄이 흘러감 (무지개 없이)
- chain (jchain2 · 256 × 32 · 4칸 되풀이 — 가는 쪽으로 누움): 빛의 사슬 고리 넷 (빛이 고리를 따라 지나감)

  cd tools && python3 -m jobvfx.ward --preview 폴더 [--style ward]
"""
import math
import sys

import numpy as np

from .base import Noise3, WHITE, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, outline, taper, tri
from .element import star4, wet

BUCKLER_FRAMES = 4
BIFROST_FRAMES = 6
CHAIN_FRAMES = 4
SQUASH = [1.0, 0.7, 0.25, 0.7]


def buckler_frame(S, f, n=256):
    x, y = grid(n)
    aa = 2.0 / n
    sx = SQUASH[f]
    X = x / max(sx, 0.05)
    r = np.hypot(X, y)
    R = 0.78
    disc = 1 - sstep(R - aa / sx, R + aa / sx, r)
    rim = disc * sstep(R - 0.16, R - 0.12, r)
    face = disc * (1 - sstep(R - 0.16, R - 0.12, r))
    th = np.arctan2(y, X)
    star = np.zeros_like(x)
    from .base import star_shape
    prof = star_shape(r, th, 8, 0.1, [0.48, 0.24, 0.48, 0.24, 0.48, 0.24, 0.48, 0.24], math.pi / 2, sharp=2.4)
    star = (1 - sstep(prof - aa * 2, prof + aa * 2, r)) * face
    studs = np.zeros_like(x)
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        studs = np.maximum(studs, disk(X, y, (R - 0.07) * math.cos(a), (R - 0.07) * math.sin(a), 0.035, 0.035, aa / max(sx, 0.2)))
    lit = wet(disc, n)
    side = 0.25 + 0.75 * sx  # 옆으로 돌수록 어둡게
    layers = [(S.main(0.5), np.exp(-(np.hypot(x, y) / 0.85) ** 2) * 0.25), (S.main(np.clip((0.35 + 0.35 * lit) * side + 0.1, 0, 1)), rim),
              (S.main(np.clip((0.55 + 0.3 * lit - 0.15 * r) * side + 0.15, 0, 1)), face), (S.accent(0.92), star * (0.6 + 0.4 * sx)), (WHITE, studs * rim * 0.8),
              (WHITE, disc * sstep(0.75, 0.95, lit) * 0.7)]
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.72, sigma=0.03, gain=0.4)


def bifrost_frame(S, f, h=64, w=512, frames=BIFROST_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.125 ~ 0.125
    ph = f / frames
    env = np.clip(1 - np.abs(x) / 1.0, 0, 1) ** 0.4
    band = np.exp(-(y / 0.07) ** 2) * env
    core = np.exp(-(y / 0.018) ** 2) * env
    flow = np.zeros_like(x)
    for j in range(3):
        c = ((x * 0.5 + 0.5) * 3 - ph * 3 + j / 3) % 1.0
        flow = np.maximum(flow, np.exp(-((c - 0.5) / 0.06) ** 2) * np.exp(-((y - (j - 1) * 0.035) / 0.012) ** 2))
    flow = flow * env
    layers = [(S.main(0.55), band * 0.6), (S.main(0.85), np.exp(-(y / 0.035) ** 2) * env * 0.8), (S.accent(0.95), flow), (WHITE, core * 0.8)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.75, sigma=0.01, gain=0.35)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


def chain_frame(S, f, h=32, w=256, frames=CHAIN_FRAMES):
    hh, ww = h * 4, w * 4
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.125 ~ 0.125
    aa = 2.0 / ww
    links = np.zeros_like(x)
    side = np.zeros_like(x)
    for i in range(4):
        cx = -0.75 + i * 0.5
        u = (x - cx) / 0.3
        if i % 2 == 0:  # 정면 고리 (도넛)
            e = np.hypot(u, y / 0.09)
            ring = np.exp(-((e - 0.75) / 0.17) ** 2)
            links = np.maximum(links, ring * (np.abs(u) < 1.2))
        else:  # 옆 고리 (납작한 막대)
            bar = (1 - sstep(0.85, 1.0, np.abs(u))) * (1 - sstep(0.025, 0.035, np.abs(y)))
            side = np.maximum(side, bar)
    body = np.clip(links + side, 0, 1)
    ph = f / frames
    glint = np.exp(-((((x * 0.5 + 0.5) - ph) % 1.0 - 0.5) / 0.05) ** 2) * body
    layers = [(S.main(0.45), np.exp(-(y / 0.08) ** 2) * 0.35), (S.main(0.7), body), (S.main(0.92), body * (y > 0) * 0.5), (WHITE, glint * 0.9)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.75, sigma=0.008, gain=0.35)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


# ------------------------------------------------------------------ 들소 영혼 (오록스 차지 — 2026-11-17 룬 설명 "들소 영혼과 함께 앞으로 돌진")

BISON_FRAMES = 6


def _strands(x, y, aa, rng, items, ph):
    """흩날리는 털 가닥 [(시작 x, y, 길이 L, 방향 각 (도), 굵기, 물결 세기)] — 끝으로 가늘어지는 띠 · 물결이 뒤로 흐름"""
    out = np.zeros_like(x)
    for i, (x0, y0, L, ang, w0, amp) in enumerate(items):
        r = math.radians(ang)
        ux, uy = math.cos(r), math.sin(r)
        nx, ny = -uy, ux
        k = rng.uniform(4.0, 6.5)
        pts = []
        for q in np.linspace(0, 1, 22):
            wv = amp * q * math.sin(k * q * math.pi + ph + i * 1.7)
            pts.append((x0 + ux * L * q + nx * wv, y0 + uy * L * q + ny * wv))
        out = np.maximum(out, taper(x, y, pts, w0, 0.004, aa, p=0.8) * (0.8 + 0.2 * math.sin(ph * 2 + i)))
    return out


def bison_frame(S, f, h=256, w=512, frames=BISON_FRAMES):
    """옆모습 (머리 = 오른쪽 · 가는 쪽 +x — jwolf 와 같은 틀 2 : 1): 높이 솟은 어깨 혹 · 낮게 숙인 큰 머리 (곱슬 털 이마) · 위로 솟아 앞으로 굽은 뿔 · 흰 눈 ·
    턱수염 · 앞다리의 긴 털, 몸 뒤쪽은 뒤로 흩날리는 빛 가닥으로 풀림 (다리 없음 — 정령). 도트 (세로 35 텍셀 안팎) 로 옮기므로 실루엣 · 띠 · 짙은 테 위주.
    6장 되풀이 (몸 들썩 · 머리 끄덕 · 가닥 물결)"""
    x, y = gridhw(h, w)
    aa = 2.0 / w
    ph = f / frames * 2 * math.pi
    nz = Noise3(S.seed + 91)
    rng = np.random.default_rng(S.seed + 93)
    by = 0.016 * math.sin(ph)  # 몸 들썩
    hy = by + 0.022 * math.sin(ph + 0.9)  # 머리 끄덕 (몸보다 늦게)
    # 몸: 어깨 혹 (가장 높음) · 앞어깨 · 뒤 몸 (가장자리가 털 결로 들쭉날쭉 — 가닥이 여기서 풀려 나감)
    hump = disk(x, y, 0.05, 0.08 + by, 0.38, 0.32, aa)
    shoulder = disk(x, y, 0.3, -0.01 + by, 0.22, 0.3, aa)
    rear = disk(x, y, -0.3, 0.0 + by, 0.24, 0.2, aa)
    ragged = sstep(-0.6, -0.36, x + 0.12 * nz.fbm(x * 5, y * 9, 1.0 + f * 0.3, oct=2))
    tufts = np.zeros_like(x)  # 혹 윗테의 털 뭉치 (실루엣이 들쭉날쭉)
    for i in range(6):
        cx = -0.24 + i * 0.1
        cy = 0.08 + by + 0.32 * math.sqrt(max(0.0, 1 - ((cx - 0.05) / 0.38) ** 2))
        tufts = np.maximum(tufts, disk(x, y, cx + 0.02 * math.sin(ph + i), cy - 0.01, 0.05, 0.045, aa))
    body = np.clip(hump + shoulder + tufts + rear * ragged, 0, 1)
    # 머리: 크게 · 낮게 · 앞이마가 거의 곧게 섬 (곱슬 털) · 아래로 숙인 주둥이
    skull = disk(x, y, 0.58, -0.1 + hy, 0.18, 0.2, aa)
    mop = disk(x, y, 0.52, 0.02 + hy, 0.18, 0.16, aa)
    muzzle = disk(x, y, 0.72, -0.22 + hy, 0.11, 0.1, aa)
    head = np.clip(skull + mop + muzzle, 0, 1)
    beard = tri(x, y, (0.55, -0.25 + hy), (0.74, -0.3 + hy), (0.6, -0.49 + hy), aa)
    solid = np.clip(body + head + beard, 0, 1)
    # 뿔: 이마 옆에서 위로 솟아 앞으로 굽음 (가까운 뿔 밝게 · 먼 뿔 짙게)
    horn = taper(x, y, [(0.58, 0.1 + hy), (0.62, 0.22 + hy), (0.7, 0.28 + hy), (0.78, 0.25 + hy)], 0.052, 0.012, aa, p=0.9)
    horn_far = taper(x, y, [(0.46, 0.14 + hy), (0.48, 0.26 + hy), (0.55, 0.32 + hy), (0.62, 0.31 + hy)], 0.042, 0.01, aa, p=0.9) * (1 - horn)
    # 앞다리의 긴 털 · 턱수염 끝 (아래로 늘어지며 뒤로 날림)
    hang = _strands(x, y, aa, rng, [(0.6, -0.42 + hy, 0.08, -110, 0.04, 0.012), (0.44, -0.24 + by, 0.21, -116, 0.06, 0.02),
                                     (0.31, -0.26 + by, 0.21, -120, 0.065, 0.02), (0.18, -0.25 + by, 0.19, -126, 0.06, 0.02),
                                     (0.04, -0.2 + by, 0.17, -138, 0.05, 0.025)], ph)
    # 몸 뒤: 혹 · 등 · 배에서 뒤로 흩날리는 빛 가닥 (정령 몸이 풀림)
    trail = []
    for i in range(7):
        t = i / 6
        y0 = -0.16 + 0.42 * t + rng.uniform(-0.015, 0.015)
        trail.append((-0.42 + 0.1 * math.sin(t * math.pi), y0 + by, rng.uniform(0.42, 0.58), 180 + (y0 - 0.04) * 22, rng.uniform(0.065, 0.085),
                      rng.uniform(0.03, 0.05)))
    mane = _strands(x, y, aa, rng, trail, ph)
    grain = nz.fbm(x * 7 + f * 0.6, y * 7, f * 0.3, oct=3)
    mane = np.clip(mane * (0.85 + 0.3 * grain), 0, 1)
    curls = np.clip(nz.fbm(x * 14, y * 14, 3.0, oct=2) * 1.8 - 0.5, 0, 1)
    socket = disk(x, y, 0.67, -0.07 + hy, 0.08, 0.055, aa) * head
    eye = disk(x, y, 0.675, -0.07 + hy, 0.06, 0.036, aa)
    nostril = disk(x, y, 0.79, -0.25 + hy, 0.025, 0.02, aa) * head
    rim = outline((np.clip(solid + horn + horn_far + hang + mane, 0, 1) > 0.5).astype(float), 9) * 0.95
    # 띠: 혹 · 등 위 (가장 밝은 털) · 몸 가운데 · 배 (짙음) — 머리는 짙게 (뿔 · 눈이 돋보이게), 이마 털은 가운데 밝기
    fur = 0.05 * nz.fbm(x * 9, y * 4, 7.0 + f * 0.2, oct=2)  # 띠 경계를 털 결로 (곧은 줄 대신)
    band_top = body * sstep(0.2, 0.24, y - by + 0.16 * (x - 0.05) + fur) * (1 - head)
    band_low = solid * (1 - sstep(-0.1, -0.06, y - by + fur)) * (1 - head)
    # 2026-11-17 영상 (CI 44046252216): 몸이 하늘빛 덩이로 보였음 → 몸 · 배를 조금 짙게 (위 띠와 차이). 짙은 테는 도트로 옮기면 빛 번짐 띠 (밝은 색) 가 됨
    layers = [(S.main(0.2), rim),
              (S.main(0.5 + 0.38 * mane), np.clip(mane * 1.3, 0, 1) * (1 - solid)),
              (S.main(0.42), np.clip(hang * 1.3, 0, 1) * (1 - solid)),
              (S.main(0.6), solid),
              (S.main(0.44), band_low),
              (S.main(0.92), band_top),
              (S.main(0.38), head),
              (S.main(0.6 + 0.2 * curls), mop * head * (1 - sstep(0.62, 0.7, x))),
              (S.main(0.44), beard),
              (S.main(0.3), horn_far),
              (S.accent(0.95), horn),
              (S.main(0.12), socket),
              (S.main(0.12), nostril),
              (WHITE, eye)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.8, sigma=0.012, gain=0.35)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x)))


FRAMES = {
    "buckler": (BUCKLER_FRAMES, buckler_frame),
    "bifrost": (BIFROST_FRAMES, bifrost_frame),
    "chain": (CHAIN_FRAMES, chain_frame),
    "bison": (BISON_FRAMES, bison_frame),
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "ward"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
