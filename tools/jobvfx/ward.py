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

from .base import WHITE, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw
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


FRAMES = {
    "buckler": (BUCKLER_FRAMES, buckler_frame),
    "bifrost": (BIFROST_FRAMES, bifrost_frame),
    "chain": (CHAIN_FRAMES, chain_frame),
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
