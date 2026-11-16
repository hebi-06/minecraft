"""2026-11-16 혼 (네크로맨서 — 사용자 "다음 직업으로 마법사 작업"): 원소 공용 혼 묶음 el_soul 의 네크로맨서 대표 그림.

jobs.json 네크로맨서 빛 (녹색 혼불 [0.3, 0.92, 0.45] · 뼈빛 [0.92, 0.88, 0.74]). 예전 그림 (rfx_jsoul2 · jskull · jrift · jbonespear · jhand2 · jsmoke2 ·
jhalo · jaura2 — 흰 그림에 빛 두 겹) 과 **같은 틀** (칸 수 · 가로세로 비 · 머리 자리 · 땅 자리) — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 그림만 바뀜.

- soul (jsoul2 · 128 · 6 되풀이): 혼불 — 녹색 불꽃 (아래 둥글고 위로 갈래 셋이 일렁임) · 뼈빛 속 · 오르는 불티
- skull (jskull · 256 · 6): 망자의 해골 — 0 연기가 모여 해골 · 1 ~ 3 눈구멍의 녹색 불이 맥동 (되풀이) · 4 턱이 벌어지며 비명 · 5 연기로 흩어짐, 둘레를 도는 혼 꼬리
- rift (jrift · 102 × 256 · 6 — 땅에 누움, 그림 위 = 갈라지는 쪽): 0 가는 금 · 1 ~ 2 벌어지며 돌조각 · 3 ~ 4 틈의 녹색 빛 맥동 (되풀이) · 5 닫히며 흐려짐
- bonespear (jbonespear · 256 × 64 · 4 되풀이 — 창끝 x 0.94): 들쭉날쭉한 뼈 창끝 · 갈비 날밑 · 등뼈 자루 · 녹색 혼불 꼬리
- hand (jhand2 · 128 × 256 · 6 — 아래 끝이 땅): 망자의 손 — 손끝이 솟음 → 손목까지 → 손을 펼쳐 뻗음 → 오므림 → 움켜쥠 → 가라앉음
- smoke (jsmoke2 · 256 · 8): 망자의 연기 (그림자 묶음의 뭉게 연기를 혼 빛으로 — shadow.smoke_frame)
- halo (jhalo · 2 × 2 · 512 · 8): 녹색 혼 고리 (arcane.halo_frame 을 혼 빛으로 — 반지름 0.88)
- aura (jaura2 · 128 × 256 · 8 되풀이): 몸에서 솟는 녹색 혼불 띠 (arcane.aura_frame 을 혼 빛으로)
맞은 자리 (원소 kind soul — element.frames_of 의 xslash): X 베기 대신 혼 터짐 (뼈빛 번쩍 → 녹색 고리 · 휘감겨 나가는 혼 꼬리)

  cd tools && python3 -m jobvfx.soul --preview 폴더 [--only soul,skull]
"""
import math
import sys

import numpy as np

from .arcane import aura_frame, halo_frame, rect, seg_glow, win
from .base import Noise3, WHITE, bloom, compose, grid, preview, ramp, sstep, tiled, to_img
from .element import STYLES, bolt_points, star4
from .shadow import smoke_frame

SOUL_FRAMES = 6
SKULL_FRAMES = 6
RIFT_FRAMES = 6
BONESPEAR_FRAMES = 4
HAND_FRAMES = 6
SMOKE_FRAMES = 8
HALO_FRAMES = 8
AURA_FRAMES = 8
SOULHIT_FRAMES = 8


def S0():
    return STYLES["soul"]


def smoke_style(S):
    """망자의 연기 빛: 녹색 안개 (2026-11-16 영상 — 짙은 회색이면 1인칭에서 검은 얼룩처럼 보였음, 예전은 밝은 녹색 안개) · 녹색 솜뭉치가 되지 않게 테는 옅게"""
    from .element import Style
    return Style(S.lib, S.kind, main=ramp([(0.0, "#0A1C10"), (0.3, "#245A34"), (0.55, "#4E9A62"), (0.8, "#9AD8A8"), (1.0, "#E2FBE6")]), accent=S.accent,
                 smoke=ramp([(0.0, "#0C1E12"), (0.45, "#24503A"), (0.8, "#3E7C58"), (1.0, "#6EAA84")]), seed=S.seed)


def bone(S, t):
    """뼈 빛 (어두운 0 → 밝은 1): accent 램프"""
    return S.accent(0.45 + 0.53 * np.clip(t, 0, 1))


def stroke(x, y, pts, widths, gains=None):
    """부드러운 붓줄 (점이 촘촘한 꺾은 선 · 점마다 굵기 · 밝기 — 구슬처럼 끊겨 보이지 않게 굵기를 마디 안에서 이음)"""
    m = np.zeros_like(x)
    gains = gains if gains is not None else [1.0] * len(pts)
    for i in range(len(pts) - 1):
        (ax, ay), (bx, by) = pts[i], pts[i + 1]
        ex, ey = bx - ax, by - ay
        L2 = ex * ex + ey * ey + 1e-12
        tt = np.clip(((x - ax) * ex + (y - ay) * ey) / L2, 0, 1)
        dd = np.hypot(x - (ax + tt * ex), y - (ay + tt * ey))
        wv = widths[i] + (widths[i + 1] - widths[i]) * tt
        g = gains[i] + (gains[i + 1] - gains[i]) * tt
        m = np.maximum(m, np.exp(-(dd / np.maximum(wv, 1e-4)) ** 2) * g)
    return m


# ------------------------------------------------------------------ 혼불 (jsoul2)

def flame_sdf(x, y, base_y, R, tip_y, ph, sway=0.08, lobes=True):
    """불꽃 모양 거리 (안 −): 아래 원 (가운데 base_y · 반지름 R) + 위로 가늘어지는 몸 (끝 tip_y) · 일렁임 · 갈래 셋"""
    span = tip_y - base_y
    q = np.clip((y - base_y) / span, 0, 1)
    xs = x - sway * np.sin(y * 5.5 - ph) * q
    hw = R * (1 - q) ** 1.15
    if lobes:
        hw = hw * (1 + 0.28 * np.sin(q * math.pi * 2.6 - ph * 0.5) * q)
    body = np.where(y > base_y, np.abs(xs) - hw, np.hypot(xs, y - base_y) - R)
    return np.where(y > tip_y, 9.0, body)


def soul_frame(S, f, n=128):
    x, y = grid(n)
    aa = 2.0 / n
    ph = f / SOUL_FRAMES * 2 * math.pi
    tip = 0.82 + 0.06 * math.sin(ph)
    d = flame_sdf(x, y, -0.42, 0.4, tip, ph)
    d2 = flame_sdf(x, y, -0.48, 0.24, 0.3 + 0.05 * math.sin(ph + 1), ph + 0.7, sway=0.05, lobes=False)
    body = 1 - sstep(-aa, aa, d)
    inner = 1 - sstep(-aa * 2, aa * 2, d2)
    depth = np.clip(-d / 0.25, 0, 1)
    side = []
    for s in (-1, 1):
        ds = flame_sdf(x - s * 0.26, y + 0.1, -0.38, 0.14, 0.38 + 0.08 * math.sin(ph + s * 2), ph + s, sway=0.06, lobes=False)
        side.append(1 - sstep(-aa, aa, ds))
    sides = np.maximum(side[0], side[1])
    sparks = np.zeros_like(x)
    for i in range(3):
        u = (i / 3 + f / SOUL_FRAMES) % 1.0
        sx = 0.28 * math.sin(i * 2.4 + u * 4)
        sy = 0.3 + u * 0.65
        sparks = np.maximum(sparks, np.exp(-((x - sx) ** 2 + (y - sy) ** 2) / 0.0012) * math.sin(math.pi * u))
    layers = [(S.main(0.42), np.exp(-(x * x + (y + 0.15) ** 2) / 0.28) * 0.55 * (1 - sstep(0.75, 0.98, np.hypot(x, y)))),
              (S.main(0.5), sides * 0.85), (S.main(0.55 + 0.3 * depth), body), (S.main(0.9), body * depth ** 1.5), (S.accent(0.9), inner),
              (WHITE, inner * np.clip(1 - (y + 0.48) / 0.6, 0, 1) * 0.6), (S.main(0.92), sparks)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.04, gain=0.5)
    return rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 망자의 해골 (jskull)

def skull_layers(S, x, y, aa, jaw=0.0, eye=1.0, k=1.0, cy=0.08):
    """해골 (가운데 (0, cy)): 머리뼈 · 광대 · 눈구멍 (녹색 불) · 콧구멍 · 이 · 아래턱 (jaw 만큼 벌어짐)"""
    yy = y - cy
    cran = np.hypot(x / 0.44, (yy - 0.08) / 0.4)
    cheek = np.maximum(np.abs(x) - 0.3 * (1 - 0.35 * np.clip(-(yy + 0.12) / 0.2, 0, 1)), np.abs(yy + 0.14) - 0.14)
    head = np.minimum(cran - 1, cheek / 0.3)
    hm = 1 - sstep(-aa * 3, aa * 3, head)
    jy = yy + 0.36 + jaw
    jaw_d = np.maximum(np.abs(x) - 0.25 * (1 - 0.3 * np.clip(-(jy) / 0.1, 0, 1)), np.abs(jy) - 0.075)
    jm = 1 - sstep(-aa, aa, jaw_d)
    eyes = np.zeros_like(x)
    fire = np.zeros_like(x)
    for s in (-1, 1):
        e = np.hypot((x - s * 0.17) / 0.12, (yy + 0.0) / 0.105)
        eyes = np.maximum(eyes, 1 - sstep(1 - aa * 8, 1 + aa * 8, e))
        fire = np.maximum(fire, np.exp(-((x - s * 0.17) ** 2 + (yy - 0.01) ** 2) / (0.004 * eye)))
    nose = (1 - sstep(-aa, aa, np.abs(x) - 0.055 * np.clip((yy + 0.21) / 0.12, 0, 1))) * (yy < -0.08) * (yy > -0.21)
    teeth_row = (np.abs(yy + 0.26) < 0.05) * (np.abs(x) < 0.2)
    gaps = np.exp(-((np.mod(x + 0.025, 0.05) - 0.025) / 0.004) ** 2)
    lit = np.clip(0.55 - 0.45 * x - 0.0 + 0.5 * (yy - 0.1), 0, 1)
    crack = seg_glow(x, yy, [(-0.08, 0.46), (-0.02, 0.36), (-0.1, 0.27), (-0.05, 0.2)], 0.006)
    L = [(bone(S, 0.25 + 0.6 * lit), hm * k), (bone(S, 0.95), hm * np.exp(-((x + 0.15) ** 2 + (yy - 0.25) ** 2) / 0.02) * 0.6 * k),
         (S.smoke(0.1), (eyes + nose) * hm * k), (S.main(0.55), np.clip(eyes * 0.6 + fire * 1.4, 0, 1) * eye * hm * k), (S.main(0.95), fire * eye * k),
         (S.smoke(0.15), crack * hm * 0.7 * k), (bone(S, 0.85), teeth_row * hm * k), (S.smoke(0.2), teeth_row * gaps * hm * 0.8 * k),
         (bone(S, 0.3 + 0.5 * lit), jm * k), (S.smoke(0.2), jm * np.exp(-((jy - 0.06) / 0.012) ** 2) * k)]
    if jaw > 0.02:
        mouth = (np.abs(x) < 0.2) * (yy < -0.3) * (yy > -0.3 - jaw)
        L.insert(5, (S.main(0.85), mouth * k))
    return L


def wisps(S, x, y, f, frames, n=6, R=0.72, spin=0.9, k=1.0):
    """둘레를 도는 혼 꼬리 (쉼표 모양 — 머리가 밝음)"""
    m = np.zeros_like(x)
    for i in range(n):
        a0 = i * 2 * math.pi / n + f / frames * 2 * math.pi / n * 2 * spin
        q = np.linspace(0, 1, 20)
        pts = [((R - 0.3 * u) * math.cos(a0 - 0.95 * u), (R - 0.3 * u) * math.sin(a0 - 0.95 * u)) for u in q]
        m = np.maximum(m, stroke(x, y, pts, [0.045 * (1 - u) ** 0.8 + 0.006 for u in q], [(1 - u) ** 0.7 for u in q]))
    return [(S.main(0.5), np.clip(m * 1.3, 0, 1) * 0.7 * k), (S.main(0.8), np.clip(m * 1.6 - 0.6, 0, 1) * k)]


def skull_frame(S, f, n=256):
    N = Noise3(1501)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    layers = [(S.main(0.4), np.exp(-(r / 0.5) ** 2) * 0.45)]
    if f == 0:
        sm = np.clip(N.fbm(x * 3, y * 3, 1.0, 4) * 1.6 - 0.4, 0, 1) * np.exp(-(r / 0.55) ** 2)
        layers += [(S.smoke(0.6), sm * 0.8)] + skull_layers(S, x, y, aa, eye=0.4, k=0.55) + wisps(S, x, y, 0, SKULL_FRAMES, R=0.62)
    elif f <= 3:
        eye = [0, 0.8, 1.0, 0.85][f]
        layers += wisps(S, x, y, f, SKULL_FRAMES) + skull_layers(S, x, y, aa, eye=eye)
    elif f == 4:
        layers += wisps(S, x, y, f, SKULL_FRAMES, R=0.8) + skull_layers(S, x, y, aa, jaw=0.12, eye=1.3)
        scream = np.exp(-((x) ** 2 / 0.02 + (y + 0.42) ** 2 / 0.05)) * (y < -0.25)
        layers += [(S.main(0.75), scream * 0.8), (S.main(0.95), scream ** 2 * 0.8)]
    else:
        sm = np.clip(N.fbm(x * 3 + 2, y * 3 - 1, 3.0, 4) * 1.7 - 0.5, 0, 1) * np.exp(-(r / 0.7) ** 2)
        layers += [(S.smoke(0.55), sm * 0.75)] + skull_layers(S, x, y + 0.04, aa, eye=0.3, k=0.4) + wisps(S, x, y, f, SKULL_FRAMES, R=0.86, k=0.6)
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.66, sigma=0.022, gain=0.5)
    return rgb, a * (1 - sstep(0.92, 0.99, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 갈라진 땅 (jrift — 102 × 256, 땅에 누움)

def rift_frame(S, f, w=102, h=256):
    rng = np.random.default_rng(1521)
    N = Noise3(1522)
    x, y, ky = rect(w, h)
    aa = 2.5 / w
    pts = bolt_points(rng, 0.0, -ky * 0.92, 0.0, ky * 0.92, depth=5, jag=0.09)
    # 꺾은 선까지의 거리 · 선을 따른 자리 (끝으로 갈수록 좁게)
    dist = np.full_like(x, 9.0)
    along = np.zeros_like(x)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        ex, ey = bx - ax, by - ay
        L = math.hypot(ex, ey) + 1e-9
        tt = np.clip(((x - ax) * ex + (y - ay) * ey) / (L * L), 0, 1)
        dd = np.hypot(x - (ax + tt * ex), y - (ay + tt * ey))
        closer = dd < dist
        dist = np.where(closer, dd, dist)
        along = np.where(closer, ay + tt * ey, along)
    endk = np.clip(1 - np.abs(along) / (ky * 0.92), 0, 1) ** 0.6
    gw = [0.025, 0.1, 0.17, 0.19, 0.19, 0.07][f] * endk + 0.004
    gap = 1 - sstep(gw - aa, gw + aa, dist)
    rim = np.exp(-((dist - gw) / 0.035) ** 2) * (dist > gw * 0.6)
    branches = np.zeros_like(x)
    if f >= 1:
        for i in range(7):
            p0 = pts[rng.integers(2, len(pts) - 2)]
            s = rng.choice([-1, 1])
            bp = bolt_points(rng, p0[0], p0[1], p0[0] + s * rng.uniform(0.4, 0.8), p0[1] + rng.uniform(-0.5, 0.5), depth=3, jag=0.15)
            branches = np.maximum(branches, seg_glow(x, y, bp, 0.012))
    stones = np.zeros_like(x)
    if 1 <= f <= 3:
        for i in range(10):
            sy = rng.uniform(-ky * 0.8, ky * 0.8)
            sx = rng.choice([-1, 1]) * rng.uniform(0.25, 0.7) * (0.6 + 0.4 * f / 3)
            rr = rng.uniform(0.04, 0.08)
            stones = np.maximum(stones, 1 - sstep(rr - aa, rr + aa, np.hypot(x - sx, (y - sy) * 0.9)))
    glow_k = [0.45, 0.35, 0.55, 1.0, 0.8, 0.3][f]
    inner = np.exp(-(dist / (gw * 0.55 + 0.01)) ** 2) * gap
    haze = np.exp(-(dist / (gw * 3 + 0.08)) ** 2) * win(x, y, ky, 0.8, 0.9)
    k = 1.0 if f < 5 else 0.6
    tex = N.fbm(x * 6, y * 3, 1.0, 3)
    layers = [(S.smoke(0.35 + 0.2 * tex), np.clip(rim * 0.9 + branches * 0.6, 0, 1) * k), (S.main(0.45), haze * 0.5 * glow_k),
              (S.smoke(0.05), gap * k), (S.main(0.65), inner * glow_k), (S.main(0.92), inner ** 2 * glow_k), (WHITE, inner ** 4 * glow_k * 0.6),
              (S.main(0.6), branches * glow_k * 0.7), (S.accent(0.55 + 0.25 * tex), stones), (S.smoke(0.2), stones * 0.4 * (y < 0))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.64, sigma=0.03, gain=0.45)
    return rgb, a * (1 - sstep(0.92, 0.99, np.abs(x))) * (1 - sstep(ky * 0.96, ky * 0.995, np.abs(y)))


# ------------------------------------------------------------------ 뼈 창 (jbonespear — 256 × 64, 창끝 x 0.94)

def bonespear_frame(S, f, w=256, h=64, head=0.94):
    x, y, ky = rect(w, h)
    aa = 2.0 / w
    rng = np.random.default_rng(1541)
    ph = f / BONESPEAR_FRAMES * 2 * math.pi
    # 창끝: 들쭉날쭉한 뼈 날 (0.55 ~ head)
    q = np.clip((head - x) / (head - 0.55), 0, 1)
    notch = 0.03 * (np.exp(-((x - 0.66) / 0.018) ** 2) + np.exp(-((x - 0.78) / 0.016) ** 2))
    hw = (0.088 * np.sin(np.clip(q, 0, 1) * math.pi * 0.62) ** 0.85 - notch) * (x > 0.55)
    blade = (1 - sstep(np.maximum(hw, 0) - aa, np.maximum(hw, 0) + aa, np.abs(y))) * (x < head)
    # 날밑: 갈비 초승달 (가운데 x 0.52)
    gx = 0.52
    guard = (1 - sstep(0.018 - aa, 0.018 + aa, np.abs(np.hypot((x - gx + 0.1) / 0.12, y / 0.2) - 1))) * (x > gx - 0.04) * (np.abs(y) < 0.2)
    # 자루: 등뼈 마디 (−0.82 ~ 0.5)
    seg = 0.085
    sx = np.mod(x + 0.82, seg) / seg
    vert = (x > -0.82) * (x < 0.5)
    vr = 0.035 + 0.012 * np.cos((sx - 0.5) * math.pi * 2)
    shaft = (1 - sstep(vr - aa, vr + aa, np.abs(y))) * vert
    joints = np.exp(-((sx - 0.02) / 0.06) ** 2) * shaft
    spines = np.zeros_like(x)
    for i in range(6):
        px = -0.7 + i * 0.2
        for s in (-1, 1):
            spines = np.maximum(spines, seg_glow(x, y, [(px, s * 0.03), (px - 0.07, s * 0.1)], 0.01))
    # 꼬리: 녹색 혼불 (−0.98 ~ −0.6 — 일렁임)
    tq = np.clip((-0.6 - x) / 0.38, 0, 1)
    fl = np.abs(y - 0.03 * np.sin(x * 18 - ph) * tq) - 0.08 * (1 - tq) ** 0.7
    fire = (1 - sstep(-0.01, 0.02, fl)) * (x < -0.55) * (x > -0.98)
    lit = np.clip(0.5 + y * 6, 0, 1)
    layers = [(S.main(0.42), np.exp(-(y / 0.1) ** 2) * win(x, y, ky, 0.92, 0.7) * 0.45), (S.main(0.55 + 0.3 * (1 - tq)), fire),
              (S.main(0.92), fire * np.exp(-(y / 0.025) ** 2)), (bone(S, 0.3 + 0.5 * lit), shaft), (S.smoke(0.25), joints * 0.6),
              (bone(S, 0.35 + 0.4 * lit), spines * (np.abs(y) > 0.03)), (bone(S, 0.45 + 0.5 * lit), guard), (bone(S, 0.4 + 0.58 * lit), blade),
              (WHITE, blade * np.exp(-((y - 0.012) / 0.008) ** 2) * 0.7), (S.main(0.8), blade * np.exp(-((x - head + 0.1) / 0.1) ** 2) * 0.25)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.7, sigma=0.025, gain=0.4)
    return rgb, a * (1 - sstep(0.97, 1.0, np.abs(x))) * (1 - sstep(ky * 0.92, ky, np.abs(y)))


# ------------------------------------------------------------------ 망자의 손 (jhand2 — 128 × 256, 아래 끝이 땅)

def capsule(x, y, ax, ay, bx, by, r0, r1, aa):
    ex, ey = bx - ax, by - ay
    L2 = ex * ex + ey * ey + 1e-9
    tt = np.clip(((x - ax) * ex + (y - ay) * ey) / L2, 0, 1)
    dd = np.hypot(x - (ax + tt * ex), y - (ay + tt * ey))
    rr = r0 + (r1 - r0) * tt
    return 1 - sstep(rr - aa, rr + aa, dd)


def hand_frame(S, f, w=128, h=256):
    x, y, ky = rect(w, h)
    aa = 2.5 / w
    ground = -ky * 0.94
    rise = [0.25, 0.6, 1.0, 1.0, 0.97, 0.6][f]
    curl = [0.35, 0.15, 0.0, 0.5, 1.0, 1.0][f]
    k = 1.0 if f < 5 else 0.55
    wrist = ground - 0.35 + 2.3 * rise
    bones = np.zeros_like(x)
    joints = []
    for s_ in (-1, 1):  # 팔뚝 두 뼈
        bones = np.maximum(bones, capsule(x, y, s_ * 0.08, ground - 0.6, s_ * 0.065, wrist - 0.06, 0.04, 0.034, aa))
    bones = np.maximum(bones, capsule(x, y, -0.1, wrist + 0.02, 0.1, wrist + 0.02, 0.05, 0.05, aa))  # 손목
    palm_top = wrist + 0.42
    sc = 1.0 - 0.15 * curl
    fingers = [(-0.21, -0.38, 0.88), (-0.07, -0.12, 1.0), (0.07, 0.1, 0.96), (0.2, 0.34, 0.8)]
    for fx, ang, lk in fingers:
        by = palm_top - abs(fx) * 0.35
        bones = np.maximum(bones, capsule(x, y, fx * 0.45, wrist + 0.06, fx, by, 0.03, 0.026, aa))  # 손바닥 뼈
        px, py, a = fx, by, ang
        joints.append((px, py))
        for j, ln in enumerate((0.3, 0.22, 0.16)):
            a = a - math.copysign(1, fx) * curl * (0.85 + 0.25 * j) * 0.9  # 오므리면 손바닥 가운데 쪽 · 아래로
            L = ln * lk * sc * (1 - 0.35 * curl * j / 2)
            qx, qy = px + L * math.sin(a), py + L * math.cos(a)
            bones = np.maximum(bones, capsule(x, y, px, py, qx, qy, 0.026 - 0.004 * j, 0.022 - 0.004 * j, aa))
            joints.append((qx, qy))
            px, py = qx, qy
    # 엄지 (왼쪽 아래에서 위 · 바깥으로 → 오므리면 손바닥 위로)
    px, py, a = -0.17, wrist + 0.12, -0.95
    for j, ln in enumerate((0.2, 0.17, 0.13)):
        a = a + curl * 0.75
        qx, qy = px + ln * math.sin(a), py + ln * math.cos(a)
        bones = np.maximum(bones, capsule(x, y, px, py, qx, qy, 0.028 - 0.004 * j, 0.024 - 0.004 * j, aa))
        joints.append((qx, qy))
        px, py = qx, qy
    above = (y > ground).astype(float)
    bones = bones * above
    jm = np.zeros_like(x)
    for jx, jy in joints:
        jm = np.maximum(jm, np.exp(-((x - jx) ** 2 + (y - jy) ** 2) / 0.0011))
    jm *= above
    lit = np.clip(0.55 - 0.6 * x, 0, 1)
    aura = np.exp(-((x / 0.62) ** 2 + ((y - wrist - 0.3) / 1.0) ** 2)) * above * win(x, y, ky, 0.8, 0.9)
    soil = np.exp(-((y - ground) / 0.05) ** 2) * np.exp(-(x / 0.55) ** 2)
    rng = np.random.default_rng(1561 + f)
    clods = np.zeros_like(x)
    for i in range(6):
        cx, cy = rng.uniform(-0.6, 0.6), ground + rng.uniform(0.0, 0.3) * rise
        clods = np.maximum(clods, 1 - sstep(0.03, 0.04, np.hypot(x - cx, y - cy)))
    edge_glow = np.clip(np.exp(-(x / 0.3) ** 2) * aura, 0, 1)
    layers = [(S.main(0.45), aura * 0.55 * k), (S.main(0.7), edge_glow * 0.3 * k), (S.main(0.62), np.clip(bones * 1.0, 0, 1) * 0.0),
              (bone(S, 0.3 + 0.55 * lit), bones * k), (S.main(0.88), jm * 0.85 * k), (S.smoke(0.35), soil * 0.85), (S.smoke(0.5), clods * float(f <= 2))]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.66, sigma=0.025, gain=0.45)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x))) * (1 - sstep(ky * 0.97, ky, np.abs(y)))


# ------------------------------------------------------------------ 혼 터짐 (X 베기 대신 — element.frames_of 가 씀, 2 × 2)

def soulhit_frame(S, f, n=512, frames=SOULHIT_FRAMES):
    rng = np.random.default_rng(1581)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    k = f / (frames - 1)
    fade = 1.0 if f < 2 else max(0.0, 1 - (f - 2) / (frames - 2.5))
    layers = [(S.main(0.45), np.exp(-(r / (0.3 + 0.35 * k)) ** 2) * 0.5 * fade)]
    if f <= 2:
        kk = 1 - f / 3
        layers += [(S.accent(0.9), np.exp(-(r / (0.16 + 0.06 * f)) ** 2) * kk), (WHITE, np.exp(-(r / (0.07 + 0.03 * f)) ** 2) * kk),
                   (S.main(0.85), star4(x, y, 0, 0, 0.55 * (0.7 + 0.3 * f / 2), 0.04, 0.4, aa) * kk)]
    R = 0.18 + 0.6 * (1 - (1 - k) ** 1.6)
    ring = np.exp(-((r - R) / (0.025 + 0.03 * k)) ** 2) * (1 - k) ** 0.8 * (f >= 1)
    layers += [(S.main(0.6), ring * 0.8), (S.main(0.9), np.exp(-((r - R) / 0.008) ** 2) * (1 - k) * (f >= 1))]
    m = np.zeros_like(x)
    for i in range(7):
        a0 = i * 2 * math.pi / 7 + rng.uniform(-0.2, 0.2)
        L = (0.25 + 0.6 * k) * rng.uniform(0.8, 1.1)
        qs = np.linspace(0, 1, 24)
        pts = [(L * q * math.cos(a0 + 0.9 * q * q), L * q * math.sin(a0 + 0.9 * q * q) + 0.2 * k * q) for q in qs]
        m = np.maximum(m, stroke(x, y, pts, [0.012 + 0.03 * q for q in qs], [0.3 + 0.7 * q for q in qs]))
    m *= (f >= 1) * fade
    layers += [(S.main(0.5), np.clip(m * 1.3, 0, 1) * 0.75), (S.main(0.82), np.clip(m * 1.8 - 0.7, 0, 1)), (S.accent(0.92), np.clip(m * 2.2 - 1.4, 0, 1))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.02, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


def mist(S, f):
    """망자의 연기 = 뭉게 연기를 조금 비치게 (안개)"""
    rgb, a = smoke_frame(S, f, seed=5)
    return rgb, a * 0.66


# ------------------------------------------------------------------ 묶음

FRAMES = {
    "soul": (SOUL_FRAMES, lambda S, f: soul_frame(S, f)),
    "skull": (SKULL_FRAMES, lambda S, f: skull_frame(S, f)),
    "rift": (RIFT_FRAMES, lambda S, f: rift_frame(S, f)),
    "bonespear": (BONESPEAR_FRAMES, lambda S, f: bonespear_frame(S, f)),
    "hand": (HAND_FRAMES, lambda S, f: hand_frame(S, f)),
    "smoke": (SMOKE_FRAMES, lambda S, f: mist(smoke_style(S), f)),
    "halo": (HALO_FRAMES, lambda S, f: halo_frame(S, f, rays=False)),
    "aura": (AURA_FRAMES, lambda S, f: aura_frame(S, f)),
    "soulhit": (SOULHIT_FRAMES, lambda S, f: soulhit_frame(S, f)),
}


def frames_of(name, S=None):
    S = S or S0()
    n, fn = FRAMES[name]
    return [to_img(*fn(S, f)) for f in range(n)]


SINGLE = ("soul", "skull", "rift", "bonespear", "hand", "smoke", "aura")
TILED = ("halo",)


def sprites():
    """그림판 목록 [(이름, 만들기, colored)] — element.sets_for 가 더함 (el_soul_ 접두 · halo 는 2 × 2 조각)"""
    S = S0()
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    out = [(p + k, fr(k), True) for k in SINGLE]
    for k in TILED:
        out += tiled(p + k, fr(k))
    return out


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k) for k in names}, out)
