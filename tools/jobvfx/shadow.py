"""2026-11-15 그림자 (암살자 — 사용자 "다음 직업으로 암살자 만들고 있어봐"): 원소 공용 그림자 묶음 el_shadow 의 암살자 대표 그림.

예전 그림 (rfx_jrose · jpetal2 · jsmoke2 · jcross2 · jeclipse · jdagger2 · jstab2 — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀**: 칸 [-1, 1] = 예전 사각
(반 크기 size), 칸 수 · 크기 비율 같음 (장미 지름 0.75 · 꽃 0.8 · 일식 0.8 · 연기 반지름 약 0.95 · 엇갈린 베기 끝 0.85) — 코드의 자리 · 방향 · 크기 그대로 그림만
바뀜 (검사 라인에서 그림을 바꾸며 방향이 달라졌던 것을 되풀이하지 않게).

- smoke (256 · 8칸): 뭉게뭉게 퍼지는 그림자 연기 — 짙은 보라 몸 · 위 왼쪽 윤곽 빛 · 처음 두 칸 속 자홍 빛 · 문턱이 오르며 흩어짐
- cross (256 · 6칸): 엇갈린 두 베기 — 0 첫 줄기 · 1 둘째 줄기 · 2 교차점 번쩍 · 고리 · 3 ~ 5 가늘어지며 사라짐 (짙은 테 · 보라 몸 · 자홍 날 · 흰 심)
- rose (256 · 8칸): 검은 장미 — 0 ~ 4 피어남 (겹꽃잎 · 윤기 · 자홍 테) → 5 ~ 7 꽃잎이 흩어짐
- blossom (128 · 4칸 되풀이): 표식 꽃 (다섯 꽃잎 · 자홍 속 · 반짝임)
- petal (128 · 4장): 꽃잎 하나 (팔랑임 — 넓이 1 · 0.6 · 0.25 · 0.75)
- eclipse (2 × 2 · 8칸): 검은 해 — 0 ~ 3 커지며 나타남 · 4 ~ 7 코로나 맥동 (되풀이)
- dagger (256 · 8칸): 도는 비수 (45° 씩 · 지나간 호)
- stab (256 · 6칸): 찌르기 번쩍 (세로 창끝 빛 · 가로 충격 줄 · 퍼지는 고리)

  cd tools && python3 -m jobvfx.shadow --preview 폴더 [--only rose,smoke]
"""
import math
import sys

import numpy as np

from .base import Noise3, WHITE, bloom, compose, grid, preview, ramp, sstep, tiled, to_img
from .element import STYLES, star4, wet, seg_dist

# 검은 장미 꽃잎 (짙은 진홍 보라 → 자홍 테 → 분홍 흰빛)
PETAL = ramp([(0.0, "#08020A"), (0.12, "#1A0418"), (0.26, "#340A30"), (0.4, "#561047"), (0.52, "#7A1863"), (0.64, "#A42A85"), (0.76, "#CF55AC"),
              (0.86, "#EE96D2"), (0.94, "#FFD6F0"), (1.0, "#FFFFFF")])
VOID = np.array([0.02, 0.008, 0.035])

SMOKE_FRAMES = 8
CROSS_FRAMES = 6
ROSE_FRAMES = 8
BLOSSOM_FRAMES = 4
ECLIPSE_FRAMES = 8
DAGGER_FRAMES = 8
STAB_FRAMES = 6


def S0():
    return STYLES["shadow"]


# ------------------------------------------------------------------ 연기

def smoke_frame(S, f, n=256, frames=SMOKE_FRAMES, seed=0):
    """뭉게 연기: 가운데 큰 덩이 + 둘레 덩이 아홉 (덩이마다 공처럼 위 왼쪽이 밝음 — 합칠 때 높은 쪽) · 겉을 흔든 결 · 가장자리는 잘게 흩어짐"""
    rng = np.random.default_rng(611 + seed)
    N = Noise3(613 + seed)
    x, y = grid(n)
    t = f / (frames - 1)
    grow = 0.62 + 0.4 * (1 - (1 - t) ** 2)
    puffs = [(0.0, 0.0, 0.46)]
    for i in range(9):
        a = 2 * math.pi * i / 9 + rng.uniform(-0.3, 0.3)
        rr = rng.uniform(0.32, 0.56)
        puffs.append((rr * math.cos(a), rr * math.sin(a) * 0.85, rng.uniform(0.22, 0.36)))
    wx = x + 0.2 * (N.fbm(x * 1.7 + 3, y * 1.7, t * 1.2, 4) - 0.5)
    wy = y + 0.2 * (N.fbm(x * 1.7, y * 1.7 - 5, t * 1.2 + 2, 4) - 0.5)
    height = np.zeros_like(x)
    lsum = np.zeros_like(x)
    wsum = np.zeros_like(x) + 1e-6
    for cx, cy, r in puffs:
        cx, cy = cx * grow, cy * grow + 0.06 * t
        r = r * grow * (0.92 + 0.2 * t)
        dx, dy = (wx - cx) / r, (wy - cy) / r
        sph = np.sqrt(np.clip(1 - dx * dx - dy * dy, 0, 1))
        l = np.clip(-dx * 0.5 + dy * 0.6 + sph * 0.62, 0, 1)
        w = sph ** 6  # 덩이 사이 이음을 부드럽게 (높은 덩이 쪽으로 무게)
        lsum += l * w
        wsum += w
        height = np.maximum(height, sph)
    lit = lsum / wsum
    fine = N.fbm(wx * 5.5, wy * 5.5, t * 2 + 7, 4)
    dens = height * (0.7 + 0.6 * fine) - 0.14 * t
    body = sstep(0.0, 0.42 + 0.25 * t, dens) * sstep(0.25, 0.6, fine + height * 0.6)
    crev = 1 - sstep(0.0, 0.25, height)  # 덩이 사이 골
    tone = np.clip(0.18 + 0.62 * lit - 0.25 * crev - 0.2 * t, 0, 1)
    glow = np.exp(-(x * x + y * y) / (0.3 * grow) ** 2) * (1 - t) ** 2.4 * body
    alpha = body * (0.94 - 0.58 * t ** 1.3) * (0.78 + 0.22 * fine)
    rim = np.clip(lit - 0.55, 0, 1) * 2.2 * body * (1 - t * 0.6)
    layers = [(S.smoke(tone), alpha), (S.main(0.5 + 0.2 * lit), np.clip(rim * alpha * 0.8, 0, 1)), (S.accent(0.72), glow * 0.75),
              (S.main(0.95), glow ** 2 * 0.6)]
    rgb, a = compose(layers, n)
    return rgb, a * (1 - sstep(0.9, 0.99, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 엇갈린 두 베기

def _curve(ang, bow, L=0.85, k=28):
    d = (math.cos(ang), math.sin(ang))
    nrm = (-d[1], d[0])
    pts = []
    for i in range(k + 1):
        s = -1 + 2 * i / k
        pts.append((s * L * d[0] + bow * (1 - s * s) * nrm[0], s * L * d[1] + bow * (1 - s * s) * nrm[1], s))
    return pts


def _stroke(x, y, pts, prog, w0, aa):
    """곡선 줄기 (끝으로 가늘어짐) — prog (0 ~ 1) 만큼 그어짐: 몸 · 심 알파와 그어진 끝 (머리) 자리"""
    body = np.zeros_like(x)
    core = np.zeros_like(x)
    last = None
    upto = -1 + 2 * prog
    for (ax, ay, sa), (bx, by, sb) in zip(pts[:-1], pts[1:]):
        if sa > upto:
            break
        d, u = seg_dist(x, y, ax, ay, bx, by)
        s = sa + (sb - sa) * u
        w = w0 * (1 - np.abs(s) ** 1.6) + 0.004
        head = 1 - sstep(upto - 0.35, upto, s) * 0.5 if prog < 0.999 else 1
        body = np.maximum(body, (1 - sstep(w - aa, w + aa, d)) * head)
        core = np.maximum(core, (1 - sstep(w * 0.28 - aa, w * 0.28 + aa, d)) * head)
        last = (bx, by)
    return body, core, last


def cross_frame(S, f, n=256, frames=CROSS_FRAMES, seed=0):
    rng = np.random.default_rng(631 + seed)
    x, y = grid(n)
    aa = 1.6 / n
    A = _curve(math.radians(45), 0.1)
    B = _curve(math.radians(135), -0.1)
    pA = min(1.0, (f + 0.9) / 1.0)
    pB = 0.0 if f < 1 else min(1.0, f / 1.6)
    thin = 1.0 if f <= 2 else max(0.15, 1 - (f - 2) / (frames - 2.2))
    fade = 1.0 if f <= 2 else max(0.0, 1 - (f - 2) / (frames - 2)) ** 0.8
    layers = []
    for pts, p in ((A, pA), (B, pB)):
        if p <= 0:
            continue
        w0 = 0.085 * thin
        rim, _, _ = _stroke(x, y, pts, p, w0 * 1.55, aa)
        body, core, head = _stroke(x, y, pts, p, w0, aa)
        layers += [(S.main(0.14), rim * 0.9 * fade), (S.main(0.6), body * fade), (S.accent(0.82), np.clip(body - core, 0, 1) * 0.55 * fade),
                   (WHITE, core * fade)]
        if head is not None and p < 0.999:
            layers.append((WHITE, star4(x, y, head[0], head[1], 0.14, 0.02, 0.4, aa) * 0.9))
    if f >= 2:
        k = (f - 2) / (frames - 2)
        r = np.hypot(x, y)
        R = 0.22 + 0.55 * k
        ring = np.exp(-((r - R) / (0.025 + 0.02 * k)) ** 2) * (1 - k) ** 1.2
        layers += [(S.accent(0.7), ring * 0.8), (S.main(0.95), ring * 0.5)]
        if f == 2:
            layers += [(S.accent(0.85), star4(x, y, 0, 0, 0.55, 0.06, 0.0, aa)), (WHITE, star4(x, y, 0, 0, 0.42, 0.035, 0.0, aa)),
                       (WHITE, np.exp(-(r / 0.09) ** 2))]
        for i in range(10):
            a = rng.uniform(0, 2 * math.pi)
            rr = 0.2 + 0.6 * k * rng.uniform(0.6, 1.1)
            layers.append((S.accent(0.9), star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.05 * (1 - k), 0.01, a, aa) * (1 - k)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.55)
    return rgb, a


# ------------------------------------------------------------------ 검은 장미

def _rose(x, y, R, rot, n):
    """겹꽃잎 장미 (반지름 R): 몸 알파 · 결 (꽃잎마다 안쪽 어둠 → 겉 테) · 테 알파"""
    r = np.hypot(x, y) + 1e-9
    th = np.arctan2(y, x)
    body = np.zeros_like(x)
    tone = np.zeros_like(x)
    rim = np.zeros_like(x)
    aa = 1.8 / n
    layers = [(1.0, 5, 0.0), (0.82, 5, 0.6), (0.64, 4, 1.3), (0.47, 4, 2.1), (0.3, 3, 2.9)]
    for li, (k, m, ph) in enumerate(layers):
        Rl = R * k
        if Rl <= 0.01:
            continue
        a = (th - rot * (1 + 0.4 * li) - ph) * m
        prof = 0.8 + 0.2 * np.abs(np.cos(a / 2)) ** 0.6
        edge = Rl * prof
        inside = 1 - sstep(edge - aa, edge + aa, r)
        u = np.clip(r / np.maximum(edge, 1e-6), 0, 1)
        cupped = np.clip(1 - np.abs(np.sin(a / 2)) * 0.8, 0, 1)
        tl = 0.2 + 0.55 * u ** 1.6 * (0.6 + 0.4 * cupped) + 0.1 * li / len(layers)
        new = inside * (1 - body)
        tone = tone + tl * new
        body = np.maximum(body, inside)
        rim = np.maximum(rim, np.exp(-((r - edge) / (0.018 + 0.01 * k)) ** 2) * inside.clip(0, 1) * (0.55 + 0.45 * cupped))
    # 가운데 꼬인 심 (나선)
    spiral = np.exp(-((((np.log(r / max(R, 1e-6) + 1e-6) * 2.6 - th + rot * 3) % (2 * math.pi)) - math.pi) / 0.5) ** 2) * (1 - sstep(R * 0.1, R * 0.32, r))
    return body, np.clip(tone, 0, 1), rim, spiral * body


def rose_frame(S, f, n=256, frames=ROSE_FRAMES, seed=0):
    rng = np.random.default_rng(651 + seed)
    x, y = grid(n)
    aa = 1.6 / n
    layers = []
    if f <= 4:
        u = (f + 1) / 5
        e = 1 - (1 - u) ** 2.4
        R = 0.75 * e
        body, tone, rim, spiral = _rose(x, y, R, 0.15 * f, n)
        lit = wet(body, n, light=(-0.5, 0.8))
        layers += [(PETAL(0.05), np.clip(body * 1.15, 0, 1) * np.exp(-0.0 * x)),
                   (PETAL(0.18 + 0.5 * tone + 0.18 * lit), body), (PETAL(0.86), rim * 0.85), (WHITE, (lit ** 3) * body * 0.35),
                   (PETAL(0.1), spiral * 0.7)]
        if f >= 3:
            for i in range(5):
                a = rng.uniform(0, 2 * math.pi)
                rr = rng.uniform(0.3, 0.7) * R
                layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.06, 0.012, a, aa) * (0.6 if f == 3 else 1.0)))
        glow = np.exp(-(x * x + y * y) / (R * 1.25 + 0.05) ** 2) * (0.3 + 0.15 * f / 4)
        layers.insert(0, (S.accent(0.55), glow * 0.45))
    else:
        k = (f - 4) / (frames - 4)
        R = 0.75
        core_body, tone, rim, spiral = _rose(x, y, R * (1 - 0.65 * k), 0.6 + k, n)
        layers += [(PETAL(0.12 + 0.45 * tone), core_body * (1 - k) ** 1.3), (PETAL(0.86), rim * (1 - k) ** 1.5 * 0.8)]
        for i in range(14):
            a = 2 * math.pi * i / 14 + rng.uniform(-0.2, 0.2)
            rr = R * (0.55 + rng.uniform(0.2, 0.5) + 0.75 * k)
            cx, cy = rr * math.cos(a), rr * math.sin(a) - 0.25 * k * k
            ang = a + rng.uniform(-1, 1) + 2.4 * k
            ca, sa = math.cos(ang), math.sin(ang)
            px = (x - cx) * ca + (y - cy) * sa
            py = -(x - cx) * sa + (y - cy) * ca
            L = rng.uniform(0.09, 0.15) * (1 - 0.3 * k)
            W = L * rng.uniform(0.45, 0.7) * (0.35 + 0.65 * abs(math.cos(3 * k + i)))
            pet = 1 - sstep(1 - 0.1, 1 + 0.1, (px / L) ** 2 + (py / max(W, 1e-3)) ** 2)
            sh = np.clip(0.5 + 0.5 * px / L, 0, 1)
            layers += [(PETAL(0.25 + 0.5 * sh), pet * (1 - k) ** 0.9), (PETAL(0.8), pet * np.clip(px / L, 0, 1) ** 3 * (1 - k))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.66, sigma=0.03, gain=0.45)
    return rgb, a


# ------------------------------------------------------------------ 표식 꽃 · 꽃잎

def blossom_frame(S, f, n=128, frames=BLOSSOM_FRAMES):
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y) + 1e-9
    th = np.arctan2(y, x)
    rot = 0.08 * math.sin(f / frames * 2 * math.pi)
    a = (th - math.pi / 2 - rot) * 5
    R = 0.38 + 0.42 * np.abs(np.cos(a / 2)) ** 0.75
    body = 1 - sstep(R - aa * 2, R + aa * 2, r)
    u = np.clip(r / R, 0, 1)
    vein = np.exp(-(np.sin(a / 2) / 0.06) ** 2) * sstep(0.15, 0.4, r) * (1 - u) * 0.6
    tone = 0.38 + 0.5 * u ** 1.3
    lit = wet(body, n, light=(-0.5, 0.8))
    centre = np.exp(-(r / 0.16) ** 2)
    pulse = 0.75 + 0.25 * math.cos(f / frames * 2 * math.pi)
    layers = [(S.accent(0.6), np.exp(-(r / 0.62) ** 2) * (1 - sstep(0.8, 0.97, r)) * 0.35 * pulse), (PETAL(0.08), np.clip(body * 1.2, 0, 1) * (1 - sstep(R, R + 0.06, r))),
              (S.main(tone * 0.9 + 0.08 + 0.12 * lit), body), (S.main(0.35), vein * body), (S.accent(0.85), centre * 0.9), (WHITE, centre ** 3 * pulse)]
    for i in range(5):
        ang = math.pi / 2 + i * 2 * math.pi / 5 + rot + math.pi / 5
        layers.append((WHITE, np.exp(-((x - 0.2 * math.cos(ang)) ** 2 + (y - 0.2 * math.sin(ang)) ** 2) / 0.0012) * 0.9))
    sa = f / frames * math.pi / 2
    layers.append((WHITE, star4(x, y, 0.42, 0.42, 0.22 * pulse, 0.03, sa, aa) * 0.85))
    rgb, al = compose(layers, n)
    return bloom(rgb, al, n, thresh=0.7, sigma=0.04, gain=0.4)


def petal_cell(S, i, n=128):
    x, y = grid(n)
    widths = [1.0, 0.6, 0.25, 0.75]
    rots = [0.5, -0.3, 1.1, -1.0]
    w = widths[i]
    ca, sa = math.cos(rots[i]), math.sin(rots[i])
    px = x * ca + y * sa
    py = -x * sa + y * ca
    L = 0.82
    W = 0.42 * w
    # 눈물 꼴 (밑동 좁고 끝 둥금)
    sx = px / L
    prof = np.where(sx < 0, np.sqrt(np.clip(1 - sx ** 2, 0, 1)) * (0.15 + 0.85 * np.clip(1 + sx, 0, 1) ** 1.3), np.sqrt(np.clip(1 - sx ** 2, 0, 1)))
    body = 1 - sstep(0.9, 1.0, np.abs(py) / np.maximum(W * prof, 1e-3))
    body = body * (1 - sstep(0.96, 1.0, np.abs(sx)))
    sh = np.clip(0.5 + 0.5 * sx, 0, 1)
    lit = wet(body, n, light=(-0.5, 0.8))
    vein = np.exp(-(py / 0.012) ** 2) * (1 - np.abs(sx)) * 0.5
    layers = [(PETAL(0.06), np.clip(body * 1.1, 0, 1)), (PETAL(0.3 + 0.45 * sh + 0.15 * lit), body), (PETAL(0.18), vein * body),
              (PETAL(0.9), body * np.clip(sx, 0, 1) ** 4 * 0.7), (WHITE, lit ** 3 * body * 0.4)]
    rgb, a = compose(layers, n)
    return rgb, a


# ------------------------------------------------------------------ 검은 해

def eclipse_frame(S, f, n=512, frames=ECLIPSE_FRAMES):
    N = Noise3(671)
    x, y = grid(n)
    r = np.hypot(x, y) + 1e-9
    th = np.arctan2(y, x)
    if f < 4:
        e = 1 - (1 - (f + 1) / 4) ** 2
        loop = 0.0
    else:
        e = 1.0
        loop = (f - 4) / 4
    Rd = 0.42 * (0.35 + 0.65 * e)
    ph = loop * 2 * math.pi
    flick = N.fbm(np.cos(th) * 2.2 + 3 + 0.6 * math.cos(ph), np.sin(th) * 2.2 + 0.6 * math.sin(ph), 1.5 + loop * 2, 4)
    rays = N.fbm(th * 7 / math.pi, r * 1.5 - loop * 1.2, 4 + math.sin(ph) * 0.5, 3)
    out = np.clip((r - Rd) / (0.8 - Rd), 0, 1)
    corona = np.exp(-out * (2.6 - 1.2 * flick)) * (r > Rd) * (0.5 + 0.5 * rays) * e
    streak = np.clip(rays - 0.45, 0, 1) * 2.2 * np.exp(-out * 1.6) * (r > Rd) * e
    rim = np.exp(-((r - Rd) / 0.012) ** 2) * e
    halo = np.exp(-((r - Rd * 1.05) / 0.08) ** 2) * e
    prom = np.zeros_like(x)
    for i in range(6):
        a0 = i * math.pi / 3 + 0.4 + 0.25 * math.sin(ph + i)
        d = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        hgt = 0.06 + 0.04 * math.sin(ph * 2 + i * 1.7)
        prom = np.maximum(prom, np.exp(-(d / 0.05) ** 2) * np.exp(-((r - Rd - hgt * 0.5) / (hgt * 0.6)) ** 2))
    disk = 1 - sstep(Rd - 0.004, Rd + 0.004, r)
    layers = [(S.main(0.35), np.clip(corona * 0.65, 0, 1)), (S.accent(0.62), np.clip(halo * 0.75, 0, 1)), (S.main(0.75), np.clip(streak * 0.55, 0, 1)),
              (S.accent(0.85), np.clip(prom * e, 0, 1)), (VOID, disk), (S.main(0.98), rim * 0.95), (WHITE, rim ** 3 * 0.8)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.02, gain=0.5)
    return rgb, a * (1 - sstep(0.93, 0.99, r))


# ------------------------------------------------------------------ 도는 비수

def dagger_frame(S, f, n=256, frames=DAGGER_FRAMES):
    """도는 비수 (가운데 둘레로 — 예전 rfx_jdagger2 처럼 칸마다 시계 방향 45°): 끝 0.88 · 코등이 −0.36 · 손잡이 · 끝 보석 −0.72 (길이 약 1.6 — 예전 반 크기 × 1.6) ·
    휜 날 (짙은 등 · 밝은 날 · 흰 날끝 줄) · 날끝 뒤 지나간 호"""
    x, y = grid(n)
    aa = 1.6 / n
    ang = -f / frames * 2 * math.pi + 0.6
    ca, sa = math.cos(ang), math.sin(ang)
    px = x * ca + y * sa
    py = -x * sa + y * ca
    u = np.clip((px + 0.34) / 1.22, 0, 1)  # 날의 몫 (코등이 0 → 끝 1)
    bend = 0.09 * u ** 2
    q = py - bend
    bw = 0.085 * np.clip(1 - u, 0, 1) ** 0.75 + 0.004
    blade = (1 - sstep(bw - aa, bw + aa, np.abs(q))) * sstep(-0.35, -0.32, px) * (1 - sstep(0.86, 0.885, px))
    edge = blade * sstep(0.0, bw * 0.6 + 1e-3, -q) * 0.9
    spine = blade * sstep(bw * 0.2, bw * 0.9, q)
    guard = (1 - sstep(0.018, 0.026, np.abs(px + 0.36))) * (1 - sstep(0.12, 0.14, np.abs(py)))
    grip = (1 - sstep(0.032, 0.04, np.abs(py))) * sstep(-0.67, -0.65, px) * (1 - sstep(-0.39, -0.37, px))
    wrap = grip * (0.5 + 0.5 * np.cos(px * 70))
    gem = 1 - sstep(0.045, 0.054, np.hypot(px + 0.72, py))
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    trail_d = (th - ang) % (2 * math.pi)  # 끝이 지나온 쪽 (시계 방향으로 돎 → 반시계 쪽이 지나온 곳)
    arc = np.exp(-((r - 0.8) / 0.07) ** 2) * np.clip(1 - trail_d / 2.0, 0, 1) ** 1.6 * (trail_d > 0.06)
    thin = np.exp(-((r - 0.84) / 0.014) ** 2) * np.clip(1 - trail_d / 1.5, 0, 1) ** 2 * (trail_d > 0.06)
    layers = [(S.main(0.45), arc * 0.5), (S.accent(0.82), thin * 0.75),
              (S.main(0.08), np.clip(blade * 1.25, 0, 1)), (S.main(0.4 + 0.35 * u), blade), (S.main(0.18), spine * 0.6), (S.main(0.92), edge),
              (WHITE, edge * np.clip(u * 1.4, 0, 1) * 0.55), (S.main(0.22), guard), (S.accent(0.55), guard * 0.4), (S.smoke(0.55), grip),
              (S.smoke(0.85), wrap * grip * 0.5), (S.accent(0.85), gem), (WHITE, gem * np.exp(-((px + 0.735) ** 2 + (py - 0.012) ** 2) / 0.0004))]
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.66, sigma=0.03, gain=0.45)


# ------------------------------------------------------------------ 찌르기 번쩍

def stab_frame(S, f, n=256, frames=STAB_FRAMES):
    x, y = grid(n)
    aa = 1.6 / n
    k = f / (frames - 1)
    grow = min(1.0, (f + 1) / 2)
    fade = 1.0 if f <= 2 else max(0.0, 1 - (f - 2) / (frames - 2.5))
    H = 0.82 * grow
    W = 0.12 * (1 - 0.5 * k)
    lens = 1 - sstep(0.85, 1.0, (np.abs(x) / W) + (y / max(H, 1e-3)) ** 2)
    core = 1 - sstep(0.5, 0.75, (np.abs(x) / W) + (y / max(H, 1e-3)) ** 2)
    bar = np.exp(-(y / 0.02) ** 2) * (1 - sstep(0.5 * grow, 0.9 * grow, np.abs(x)))
    r = np.hypot(x, y)
    R = 0.15 + 0.5 * k
    ring = np.exp(-((r - R) / (0.02 + 0.02 * k)) ** 2) * (1 - k) ** 0.8 * (f >= 1)
    layers = [(S.main(0.35), np.exp(-(r / 0.5) ** 2) * 0.35 * fade), (S.main(0.6), lens * fade), (S.accent(0.8), np.clip(lens - core, 0, 1) * 0.6 * fade),
              (WHITE, core * fade), (S.accent(0.85), bar * 0.8 * fade), (WHITE, bar ** 2 * fade), (S.main(0.85), ring * 0.8)]
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.55)


# ------------------------------------------------------------------ 묶음

def frames_of(name, S=None):
    S = S or S0()
    if name == "smoke":
        return [to_img(*smoke_frame(S, f)) for f in range(SMOKE_FRAMES)]
    if name == "cross":
        return [to_img(*cross_frame(S, f)) for f in range(CROSS_FRAMES)]
    if name == "rose":
        return [to_img(*rose_frame(S, f)) for f in range(ROSE_FRAMES)]
    if name == "blossom":
        return [to_img(*blossom_frame(S, f)) for f in range(BLOSSOM_FRAMES)]
    if name == "petal":
        return [to_img(*petal_cell(S, i)) for i in range(4)]
    if name == "eclipse":
        return [to_img(*eclipse_frame(S, f)) for f in range(ECLIPSE_FRAMES)]
    if name == "dagger":
        return [to_img(*dagger_frame(S, f)) for f in range(DAGGER_FRAMES)]
    if name == "stab":
        return [to_img(*stab_frame(S, f)) for f in range(STAB_FRAMES)]
    raise KeyError(name)


def sprites():
    """그림판 목록 [(이름, 만들기, colored)] — tools/vfx_sprites.py 가 더함 (el_shadow_ 접두)"""
    S = S0()
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    return ([(p + k, fr(k), True) for k in ("smoke", "cross", "rose", "blossom", "petal", "dagger", "stab")] + tiled(p + "eclipse", fr("eclipse")))


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else ["smoke", "cross", "rose", "blossom", "petal", "eclipse", "dagger",
                                                                                               "stab"]
        preview({k: frames_of(k) for k in names}, out)
