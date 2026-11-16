"""2026-11-16 창술사 라인 (창술사 · 스톰 랜서 · 드라군 — 남은 직업 위르드식): 직업 묶음 el_lance (금빛 · 흰빛) · el_gale (박하빛 · 흰빛) · el_dragon (주홍 · 주황) 의
대표 그림.

예전 그림 (rfx_jfeather · jwind2 · jspin2 · jswirl · jfire · jdragon — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 · 반지름 몫) —
코드의 자리 · 방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT). 찌르기 번쩍 · 빛의 창 · 회오리 · 불꽃 꼬리는 shadow.py · storm.py · beast.py, 서 있는 불꽃은 forge.py.

- feather (jfeather · 64 · 8칸 되풀이): 떠다니는 깃털 — 깃대 둘레로 뒤집히며 돎
- wind (jwind2 · 192 · 8칸 — 땅에 누움, 바깥 반지름 = 반 크기): 바람 고리 — 도는 초승달 바람 셋
- spin (jspin2 · 192 · 8칸 — 면이 normal 쪽, 날 끝 반지름 = 반 크기 × 0.86): 도는 창 — 양끝 창날 · 지나간 원판
- swirl (jswirl · 256 · 8칸): 바람 소용돌이 — 0 번쩍 → 1 펼쳐짐 → 2 ~ 5 세 갈래 바람 팔 → 6 풀림 → 7 흩어짐 (팔 끝 반지름 = 반 크기 × 0.86)
- fire (jfire · 256 · 8칸): 불꽃 덩이 — 0 불붙음 → 1 ~ 3 부풀어 오름 → 4 ~ 6 흩어짐 → 7 불티 (지름 = 반 크기 × 1.6)
- dragon (jdragon · 448 · 8칸): 용의 혼 — 0 생겨남 · 1 다 생김 · 2 ~ 5 날갯짓 · 6 포효 (머리 위 불) · 7 내리꽂음 (날개 끝 사이 = 반 크기 × 1.7)

  cd tools && python3 -m jobvfx.lance --preview 폴더 [--style lance]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, taper, tri
from .element import star4, wet

FEATHER_FRAMES = 8
WIND_FRAMES = 8
SPIN_FRAMES = 8
SWIRL_FRAMES = 8
FIRE_FRAMES = 8
DRAGON_FRAMES = 8


def feather_frame(S, f, n=128, frames=FEATHER_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    a = f / frames * 2 * math.pi
    flip = math.cos(a)
    rot = math.radians(25) + 0.2 * math.sin(a)
    ca, sa = math.cos(rot), math.sin(rot)
    u = x * ca + y * sa
    v = -x * sa + y * ca
    t = np.clip((u + 0.75) / 1.5, 0, 1)
    w = 0.22 * np.sin(np.pi * t) ** 0.7 * (1 - 0.3 * t) * max(abs(flip), 0.12)
    vane = (1 - sstep(w - aa, w + aa, np.abs(v - 0.02 * np.sin(t * 6)))) * (u > -0.6) * (u < 0.75)
    barbs = vane * (0.5 + 0.5 * np.sin((u * 30 + np.sign(v) * v * 40)))
    quill = taper(x, y, [(-0.8 * ca, -0.8 * sa), (0.78 * ca, 0.78 * sa)], 0.018, 0.008, aa)
    side = 0.55 + 0.45 * flip
    layers = [(S.main(np.clip(0.45 + 0.35 * side, 0, 1)), vane), (S.main(0.85), barbs * 0.35), (WHITE, quill * 0.8), (S.accent(0.9), vane * sstep(0.7, 1.0, np.abs(v) / np.maximum(w, 1e-3)) * 0.6)]
    rgb, a2 = compose(layers, n)
    return bloom(rgb, a2, n, thresh=0.75, sigma=0.03, gain=0.3)


def wind_frame(S, f, n=192, frames=WIND_FRAMES):
    x, y = grid(n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    rot = f / frames * 2 * math.pi / 3
    arms = np.zeros_like(x)
    for j in range(3):
        a0 = rot + j * 2 * math.pi / 3
        d = (th - a0) % (2 * math.pi)
        span = 2.0
        prog = np.clip(1 - d / span, 0, 1)
        R = 0.55 + 0.4 * (1 - prog)
        arms = np.maximum(arms, np.exp(-((r - R) / (0.03 + 0.07 * prog)) ** 2) * prog ** 1.5)
    layers = [(S.main(0.6), arms * 0.85), (S.accent(0.9), np.clip(arms - 0.55, 0, 1) * 1.8), (WHITE, np.clip(arms - 0.8, 0, 1) * 3)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.3)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


def spin_frame(S, f, n=192, frames=SPIN_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    a = -f / frames * math.pi
    ca, sa = math.cos(a), math.sin(a)
    u = x * ca + y * sa
    v = -x * sa + y * ca
    shaft = (np.abs(v) < 0.022) * (np.abs(u) < 0.66)
    blades = np.zeros_like(x)
    for sgn in (1, -1):
        blades = np.maximum(blades, tri(u * sgn, v, (0.6, -0.07), (0.6, 0.07), (0.86, 0.0), aa))
    trail_d = (th - a) % math.pi
    disc = np.exp(-((r - 0.78) / 0.07) ** 2) * np.clip(1 - trail_d / 1.6, 0, 1) ** 1.5
    layers = [(S.main(0.55), disc * 0.7), (S.main(0.4), shaft.astype(float)), (S.main(0.85), blades), (WHITE, blades * 0.5), (S.accent(0.9), np.exp(-(r / 0.08) ** 2) * 0.8)]
    rgb, a2 = compose(layers, n)
    rgb, a2 = bloom(rgb, a2, n, thresh=0.75, sigma=0.03, gain=0.35)
    return rgb, a2 * (1 - sstep(0.95, 0.995, r))


def swirl_frame(S, f, n=256, frames=SWIRL_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    k = [1, 1, 1, 1, 1, 1, 0.7, 0.35][f]
    reach = [0.2, 0.55, 0.86, 0.86, 0.86, 0.86, 0.9, 0.95][f]
    twist = [0, 1.0, 2.2, 2.2, 2.2, 2.2, 1.4, 0.8][f]
    arms = np.zeros_like(x)
    for j in range(3):
        a0 = j * 2 * math.pi / 3 + f * 0.6
        phase = th - a0 - twist * (r / max(reach, 1e-3))
        d = np.abs(np.angle(np.exp(1j * phase)))
        arms = np.maximum(arms, np.exp(-(d / (0.2 + 0.15 * np.clip(1 - r, 0, 1))) ** 2) * np.clip(1 - r / max(reach, 1e-3), 0, 1) ** 0.6)
    layers = [(S.main(0.55), arms * 0.8 * k), (S.accent(0.85), np.clip(arms - 0.5, 0, 1) * 1.6 * k), (WHITE, np.clip(arms - 0.8, 0, 1) * 3 * k)]
    if f == 0:
        layers += [(WHITE, star4(x, y, 0, 0, 0.5, 0.05, 0.0, aa)), (S.accent(0.9), np.exp(-(r / 0.2) ** 2))]
    if f == 7:
        rng = np.random.default_rng(5000)
        for i in range(12):
            aa_ = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.4, 0.9)
            layers.append((S.accent(0.9), disk(x, y, rr * math.cos(aa_), rr * math.sin(aa_), 0.015, 0.015, aa) * 0.8))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.3)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


def fire_frame(S, f, n=256, frames=FIRE_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    N = Noise3(5100)
    t = f / (frames - 1)
    R = [0.2, 0.45, 0.65, 0.78, 0.78, 0.7, 0.55, 0.0][f]
    rise = 0.15 * t
    k = [1, 1, 1, 1, 0.9, 0.7, 0.45, 0][f]
    r = np.hypot(x, y - rise)
    n1 = N.fbm(x * 3, (y - rise) * 3 - t * 3, t * 2, 4)
    dens = (1 - r / max(R, 1e-3)) + 0.35 * (n1 - 0.5) + 0.25 * np.clip((y - rise) / max(R, 1e-3), -1, 1)
    body = sstep(0.0, 0.25, dens) * (R > 0)
    heat = np.clip(dens * 1.2, 0, 1)
    layers = [(S.main(np.clip(0.4 + 0.45 * heat, 0, 1)), body * k), (S.accent(np.clip(0.5 + 0.45 * heat, 0, 1)), body * sstep(0.45, 0.85, heat) * k),
              (WHITE, body * sstep(0.85, 1.0, heat) * 0.6 * k)]
    if f == 0:
        layers.append((WHITE, star4(x, y, 0, 0, 0.35, 0.04, 0.0, aa)))
    if f >= 5:
        rng = np.random.default_rng(5110 + f)
        for i in range(10):
            aa_ = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.4, 0.9) * (0.7 + 0.15 * (f - 5))
            layers.append((S.accent(0.9), disk(x, y, rr * math.cos(aa_), rr * math.sin(aa_) + 0.1, 0.018, 0.018, aa)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.03, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.hypot(x, y)))


def dragon_frame(S, f, n=448, frames=DRAGON_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    k = [0.5, 1, 1, 1, 1, 1, 1, 1][f]
    flap = [0.6, 0.8, 1.0, 0.55, 0.15, 0.55, 0.9, 0.3][f]
    dive = f == 7
    yy = -y if dive else y
    # 몸 (목 · 가슴 · 꼬리) · 머리
    spine = [(0.0, 0.55), (0.0, 0.38), (0.0, 0.12), (0.02, -0.18), (0.08, -0.42), (0.0, -0.62), (-0.1, -0.78)]
    body = taper(x, yy, spine, 0.085, 0.012, aa)
    head = disk(x, yy, 0, 0.6, 0.075, 0.1, aa)
    horns = taper(x, yy, [(-0.04, 0.66), (-0.1, 0.78)], 0.018, 0.004, aa) + taper(x, yy, [(0.04, 0.66), (0.1, 0.78)], 0.018, 0.004, aa)
    wings = np.zeros_like(x)
    ribs = np.zeros_like(x)
    for sgn in (1, -1):
        sh = (0.06 * sgn, 0.25)
        tipy = 0.25 + 0.55 * (flap - 0.5)
        tip = (0.85 * sgn, tipy)
        elbow = (0.45 * sgn, 0.25 + 0.4 * (flap - 0.5) + 0.12)
        for j, end in enumerate([tip, (0.78 * sgn, tipy - 0.25), (0.62 * sgn, tipy - 0.42), (0.42 * sgn, 0.25 - 0.45)]):
            ribs = np.maximum(ribs, taper(x, yy, [elbow, end], 0.014, 0.004, aa))
        mem_pts = [sh, elbow, tip, (0.78 * sgn, tipy - 0.25), (0.62 * sgn, tipy - 0.42), (0.42 * sgn, -0.2), (0.12 * sgn, 0.0)]
        from matplotlib.path import Path
        P = Path(mem_pts)
        inside = P.contains_points(np.stack([x.ravel(), yy.ravel()], -1)).reshape(x.shape).astype(float)
        wings = np.maximum(wings, inside)
        ribs = np.maximum(ribs, taper(x, yy, [sh, elbow], 0.022, 0.016, aa))
    lit = wet(np.clip(body + head, 0, 1), n)
    layers = [(S.main(0.4), np.exp(-(np.hypot(x, yy) / 0.8) ** 2) * 0.25 * k), (S.main(0.55), wings * 0.75 * k), (S.main(0.8), ribs * k),
              (S.main(np.clip(0.55 + 0.35 * lit, 0, 1)), np.clip(body + head, 0, 1) * k), (S.accent(0.85), np.clip(horns, 0, 1) * k),
              (WHITE, disk(x, yy, 0.03, 0.62, 0.014, 0.01, aa) * k)]
    if f == 6:
        layers.append((S.accent(0.9), np.exp(-(((x) / 0.12) ** 2 + ((yy - 0.82) / 0.14) ** 2))))
        layers.append((WHITE, np.exp(-(((x) / 0.05) ** 2 + ((yy - 0.8) / 0.07) ** 2))))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.025, gain=0.35)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


FRAMES = {
    "feather": (FEATHER_FRAMES, feather_frame),
    "wind": (WIND_FRAMES, wind_frame),
    "spin": (SPIN_FRAMES, spin_frame),
    "swirl": (SWIRL_FRAMES, swirl_frame),
    "fire": (FIRE_FRAMES, fire_frame),
    "dragon": (DRAGON_FRAMES, dragon_frame),
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
    return [(p + ("lspin" if k == "spin" else k), fr(k), True) for k in names]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        from .element import STYLES
        out = sys.argv[sys.argv.index("--preview") + 1]
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "lance"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
