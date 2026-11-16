"""2026-11-16 뇌광 (성기사 · 썬더로드 · 타이런트 — 남은 직업 위르드식): 직업 묶음 el_thunder (금빛 · 하늘빛) · el_tyrant (보랏빛 · 핏빛) 의 대표 그림.

예전 그림 (rfx_jarc · jcloud · jstone · jjudge2 · jwheel · jmaulswing · jgrip · jpillar — 흰 그림에 빛 두 겹을 물들임) 과 **같은 틀** (칸 수 · 가로세로 비 ·
땅 · 줄 끝 자리) — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 (PhantomFx.PAINT). 벼락 · 번개 고리 · 빛의 창은 tools/jobvfx/storm.py.

- arc (jarc · 512 × 128 · 8칸 — 가는 쪽으로 누움, 줄 끝 = 칸 가로 끝): 0 ~ 3 지직이는 번개 줄 (되풀이) · 4 번쩍 → 5 가지 → 6 ~ 7 사그라짐
- cloud (jcloud · 384 × 192 · 8칸): 먹구름 — 0 ~ 1 모여 듦 · 2 ~ 5 일렁임 (속에서 번쩍이는 번개) · 6 ~ 7 흩어짐
- stone (jstone · 192 × 256 · 8칸 — 세움, 아래 끝 = 발): 돌 껍데기 — 0 발부터 차오름 · 1 가슴까지 → 2 ~ 3 굳음 (테의 새벽빛) → 4 금 · 금빛 → 5 터져 나오는 빛 → 6 ~ 7 조각
- judge (jjudge2 · 64 · 4칸 되풀이): 작은 번개 문장 (깜빡임)
- wheel (jwheel · 256 × 64 · 6칸 — 땅에 누움, 길을 따라): 그을린 두 줄 · 지직거리는 번개 (밝음 → 사라짐)
- maulswing (jmaulswing · 384 · 6칸): 번개 망치 — 0 ~ 3 오른쪽 위에서 휘두름 → 4 ~ 5 내려친 자리 (칸 아래 가운데) 에서 번개가 터짐
- grip (jgrip · 256 · 8칸): 거인 왕의 손 — 0 편 손 · 1 오므림 → 2 움켜쥠 → 3 ~ 4 쥐어짬 (번개 핏줄) → 5 내리꽂음 → 6 ~ 7 연기로 흩어짐
- pillar (jpillar · 160 × 640 · 8칸 — 세움, 아래 끝 = 땅): 번개 기둥 — 0 길잡이 · 1 내리꽂힘 · 2 땅에 튀는 빛 → 3 ~ 5 지직임 → 6 끊어짐 · 7 불티

  cd tools && python3 -m jobvfx.thunder --preview 폴더 [--style thunder] [--only arc,grip]
"""
import math
import sys

import numpy as np

from .base import WHITE, Noise3, bloom, compose, grid, preview, sstep, to_img
from .beast import disk, gridhw, taper, tri
from .element import star4, wet
from .storm import bolt_path

ARC_FRAMES = 8
CLOUD_FRAMES = 8
STONE_FRAMES = 8
JUDGE_FRAMES = 4
WHEEL_FRAMES = 6
MAULSWING_FRAMES = 6
GRIP_FRAMES = 8
PILLAR_FRAMES = 8


def _down(img_rgb, a, h, w):
    from PIL import Image
    im = to_img(img_rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


def _seg_glow(x, y, pts, sig):
    d = np.full_like(x, 9.0)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        ex, ey = bx - ax, by - ay
        L2 = ex * ex + ey * ey + 1e-12
        t = np.clip(((x - ax) * ex + (y - ay) * ey) / L2, 0, 1)
        d = np.minimum(d, np.hypot(x - (ax + t * ex), y - (ay + t * ey)))
    return np.exp(-(d / sig) ** 2)


# ------------------------------------------------------------------ 번개 줄 (jarc — 512 × 128)

def arc_frame(S, f, h=128, w=512, frames=ARC_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.25 ~ 0.25
    aa = 2.0 / ww
    rng = np.random.default_rng(3000 + f)
    lw = [0.012, 0.012, 0.012, 0.012, 0.024, 0.018, 0.01, 0.005][f]
    k = [1, 1, 1, 1, 1, 0.9, 0.6, 0.3][f]
    main = bolt_path(rng, -1.0, 0.0, 1.0, 0.0, n=18, amp=0.1)
    bolt = taper(x, y, main, lw, lw * 0.8, aa)
    core = taper(x, y, main, lw * 0.4, lw * 0.35, aa)
    branches = np.zeros_like(x)
    if f in (4, 5):
        for i in range(5):
            j = rng.integers(2, 16)
            bx, by = main[j]
            sgn = 1 if i % 2 else -1
            br = bolt_path(rng, bx, by, bx + rng.uniform(0.1, 0.25), by + sgn * rng.uniform(0.08, 0.18), n=5, amp=0.03)
            branches = np.maximum(branches, taper(x, y, br, lw * 0.5, 0.002, aa))
    glow = _seg_glow(x, y, main, 0.05 + 0.04 * (f == 4)) * [0.6, 0.55, 0.6, 0.55, 1.0, 0.8, 0.45, 0.2][f]
    layers = [(S.accent(0.6), glow), (S.accent(0.85), np.clip(bolt + branches, 0, 1) * k), (WHITE, core * k)]
    if f == 4:
        layers.append((WHITE, np.exp(-((x / 0.5) ** 2 + (y / 0.05) ** 2)) * 0.5))
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.72, sigma=0.008, gain=0.45)
    return _down(rgb, a * (1 - sstep(0.94, 1.0, np.abs(x))), h, w)


# ------------------------------------------------------------------ 먹구름 (jcloud — 384 × 192 · 너비 = 반 크기 × 2)

def cloud_frame(S, f, h=192, w=384, frames=CLOUD_FRAMES):
    x, y = gridhw(h, w)  # x −1 ~ 1 · y −0.5 ~ 0.5
    aa = 2.0 / w
    N = Noise3(3100)
    grow = [0.55, 0.8, 1.0, 1.0, 1.0, 1.0, 1.05, 1.1][f]
    k = [0.7, 0.9, 1, 1, 1, 1, 0.6, 0.3][f]
    puffs = [(-0.55, -0.02, 0.32), (-0.2, 0.08, 0.38), (0.2, 0.06, 0.36), (0.55, -0.02, 0.3), (-0.75, -0.12, 0.2), (0.78, -0.12, 0.2), (0.0, -0.1, 0.34)]
    body = np.zeros_like(x)
    lit = np.zeros_like(x)
    for cx, cy, r in puffs:
        cx, cy, r = cx * grow, cy, r * grow * 0.95
        d = np.hypot((x - cx) / r, (y - cy) / (r * 0.8))
        sph = np.sqrt(np.clip(1 - d * d, 0, 1))
        body = np.maximum(body, sph)
        lit = np.maximum(lit, sph * np.clip(0.5 + 0.5 * ((y - cy) / r) - 0.2 * ((x - cx) / r), 0, 1))
    fine = N.fbm(x * 4 + 0.3 * f, y * 4, 0.2 * f, 4)
    dens = body * (0.8 + 0.4 * fine) - 0.25 * (f >= 6) * (f - 5) / 2
    alpha = sstep(0.05, 0.3, dens) * k
    tone = np.clip(0.2 + 0.45 * lit + 0.1 * fine, 0, 1)
    layers = [(S.smoke(tone), alpha)]
    if 2 <= f <= 5:
        rng = np.random.default_rng(3110 + f)
        x0 = rng.uniform(-0.5, 0.5)
        pts = bolt_path(rng, x0, 0.05, x0 + rng.uniform(-0.2, 0.2), -0.35, n=6, amp=0.06)
        bolt = taper(x, y, pts, 0.012, 0.006, aa) * (f % 2 == 0)
        flashg = np.exp(-(((x - x0) / 0.3) ** 2 + ((y + 0.05) / 0.2) ** 2)) * alpha * (0.5 if f % 2 == 0 else 0.25)
        layers += [(S.accent(0.75), flashg), (S.accent(0.9), bolt), (WHITE, bolt * 0.6)]
    rgb, a = compose(layers, (h, w))
    return rgb, a * (1 - sstep(0.94, 1.0, np.abs(x))) * (1 - sstep(0.46, 0.5, np.abs(y)))


# ------------------------------------------------------------------ 돌 껍데기 (jstone — 192 × 256 · 아래 끝 = 발 · 높이 = 칸 높이)

def stone_frame(S, f, h=256, w=192, frames=STONE_FRAMES):
    x, y = gridhw(h, w)  # x −0.75 ~ 0.75 · y −1 ~ 1
    aa = 2.0 / h
    fill = [-0.5, 0.25, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    N = Noise3(3200)
    shell = disk(x, y, 0, -0.05, 0.5, 0.95, aa) * (y < fill)
    shell = np.maximum(shell, disk(x, y, 0, 0.72, 0.28, 0.26, aa) * (y < fill))
    cells = N.fbm(x * 6, y * 6, 0.0, 2)
    seams = np.abs(np.sin(cells * 18)) < 0.12
    layers = []
    if f <= 5:
        lit = wet(shell, (h, w))
        tone = np.clip(0.5 + 0.4 * lit + 0.12 * (cells - 0.5), 0, 1)
        rimg = np.exp(-((np.hypot(x / 0.5, (y + 0.05) / 0.95) - 1) / 0.04) ** 2) * shell
        layers += [(S.smoke(tone), shell), (S.smoke(0.35), shell * seams * 0.6), (S.main(0.85), rimg * 0.6 * (f in (2, 3)))]
        if f >= 4:
            cr = np.zeros_like(x)
            rng = np.random.default_rng(3201)
            for i in range(6 if f == 4 else 10):
                a = rng.uniform(0, 2 * math.pi)
                pts = bolt_path(rng, 0, 0.1, 0.45 * math.cos(a), 0.1 + 0.9 * math.sin(a), n=5, amp=0.05)
                cr = np.maximum(cr, taper(x, y, pts, 0.02 if f == 5 else 0.012, 0.004, aa))
            layers += [(S.main(0.9), cr * shell), (WHITE, cr * shell * 0.6)]
            if f == 5:
                layers += [(S.main(0.95), np.exp(-(np.hypot(x, y - 0.1) / 0.35) ** 2) * 0.8), (WHITE, star4(x, y, 0, 0.1, 0.7, 0.05, 0.0, aa))]
    else:
        rng = np.random.default_rng(3210 + f)
        frag = np.zeros_like(x)
        for i in range(14):
            a = rng.uniform(0, 2 * math.pi)
            rr = (0.5 if f == 6 else 0.75) * rng.uniform(0.6, 1.0)
            cx, cy = rr * math.cos(a) * 0.9, 0.1 + rr * math.sin(a)
            s = rng.uniform(0.05, 0.09)
            frag = np.maximum(frag, tri(x, y, (cx - s, cy - s), (cx + s, cy - s * 0.5), (cx, cy + s), aa))
        layers += [(S.smoke(0.55), frag * (1.0 if f == 6 else 0.6)), (S.main(0.85), frag * 0.3)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.75, sigma=0.02, gain=0.4)
    return rgb, a * (1 - sstep(0.72, 0.75, np.abs(x)))


# ------------------------------------------------------------------ 번개 문장 (jjudge2 — 64 · 되풀이)

def judge_frame(S, f, n=128, frames=JUDGE_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    k = [1.0, 0.7, 1.0, 0.55][f]
    ring = np.exp(-((r - 0.78) / 0.05) ** 2)
    bolt = np.maximum(tri(x, y, (0.12, 0.62), (-0.32, -0.02), (0.06, 0.02), aa), tri(x, y, (-0.1, -0.02), (0.3, 0.04), (-0.14, -0.62), aa))
    layers = [(S.main(0.55), ring * 0.8 * k), (S.main(0.9), bolt * k), (WHITE, bolt * 0.5 * k), (S.accent(0.8), np.exp(-(r / 0.6) ** 2) * 0.3 * k)]
    rgb, a = compose(layers, n)
    return bloom(rgb, a, n, thresh=0.72, sigma=0.04, gain=0.4)


# ------------------------------------------------------------------ 천둥 바퀴 자국 (jwheel — 256 × 64 · 땅에 누움)

def wheel_frame(S, f, h=64, w=256, frames=WHEEL_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.25 ~ 0.25
    aa = 2.0 / ww
    k = [1.0, 0.95, 0.8, 0.6, 0.4, 0.2][f]
    scorch = (np.exp(-((y - 0.12) / 0.025) ** 2) + np.exp(-((y + 0.12) / 0.025) ** 2)) * (1 - sstep(0.85, 1.0, np.abs(x)))
    rng = np.random.default_rng(3300 + f)
    zz = np.zeros_like(x)
    if f <= 3:
        for sgn in (1, -1):
            pts = bolt_path(rng, -0.95, sgn * 0.12, 0.95, sgn * 0.12, n=14, amp=0.05)
            zz = np.maximum(zz, taper(x, y, pts, 0.01, 0.008, aa))
    layers = [(S.smoke(0.3), scorch * 0.8 * (0.5 + 0.5 * k)), (S.accent(0.85), zz * k), (WHITE, zz * 0.5 * k), (S.main(0.6), scorch * 0.3 * k)]
    rgb, a = compose(layers, (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.75, sigma=0.01, gain=0.35)
    return _down(rgb, a, h, w)


# ------------------------------------------------------------------ 번개 망치 (jmaulswing — 384 · 내려친 자리 = 칸 아래 가운데)

def maulswing_frame(S, f, n=384, frames=MAULSWING_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    layers = []
    imp = (0.0, -0.78)
    if f <= 3:
        ang = math.radians([60, 25, -10, -40][f])
        px, py = imp[0] + 0.15, imp[1] + 0.2
        hx, hy = px + 1.05 * math.cos(math.pi / 2 + ang), py + 1.05 * math.sin(math.pi / 2 + ang)
        handle = taper(x, y, [(px, py), (hx, hy)], 0.03, 0.03, aa)
        ca, sa = math.cos(ang), math.sin(ang)
        u = (x - hx) * ca + (y - hy) * sa
        v = -(x - hx) * sa + (y - hy) * ca
        head = (np.abs(u) < 0.22) * (np.abs(v) < 0.12)
        head = head.astype(float)
        trail = np.zeros_like(x)
        if f >= 1:
            r = np.hypot(x - px, y - py)
            th = np.arctan2(y - py, x - px)
            a1 = math.pi / 2 + ang
            a0 = a1 + math.radians(35)
            trail = np.exp(-((r - 1.0) / 0.1) ** 2) * ((th > a1) & (th < a0)) * 0.7
        lit = wet(head, n)
        layers += [(S.main(0.6), trail), (S.smoke(0.5), handle), (S.main(np.clip(0.45 + 0.4 * lit, 0, 1)), head), (S.accent(0.85), head * sstep(0.7, 0.9, lit))]
    else:
        r = np.hypot(x - imp[0], (y - imp[1]) * 2.2)
        rng = np.random.default_rng(3400 + f)
        bolts = np.zeros_like(x)
        for i in range(7):
            a = math.pi * (0.1 + 0.8 * i / 6)
            pts = bolt_path(rng, imp[0], imp[1], imp[0] + 0.9 * math.cos(a), imp[1] + 0.9 * math.sin(a), n=6, amp=0.05)
            bolts = np.maximum(bolts, taper(x, y, pts, 0.02 if f == 4 else 0.012, 0.004, aa))
        k = 1.0 if f == 4 else 0.6
        layers += [(S.accent(0.6), np.exp(-(r / 0.6) ** 2) * k), (S.main(0.85), np.exp(-((r - (0.4 if f == 4 else 0.7)) / 0.05) ** 2) * k),
                   (S.accent(0.9), bolts * k), (WHITE, bolts * 0.6 * k), (WHITE, star4(x, y, imp[0], imp[1] + 0.05, 0.6, 0.05, 0.0, aa) * (f == 4))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.025, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 거인 왕의 손 (jgrip — 256 · 너비 = 반 크기 × 2)

def grip_frame(S, f, n=256, frames=GRIP_FRAMES):
    x, y = grid(n)
    aa = 2.0 / n
    curl = [0.0, 0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    squeeze = [0, 0, 0, 0.04, 0.0, 0, 0, 0][f]
    drop = [0.0, 0.0, 0.0, 0.0, 0.0, 0.22, 0.28, 0.28][f]
    k = [1, 1, 1, 1, 1, 1, 0.6, 0.3][f]
    yy = y + drop
    # 손등 (위) · 손가락 넷이 아래로 — 오므리면 끝이 안쪽 · 위로 말려 주먹 (가운데가 빔)
    palm = np.maximum(disk(x, yy, 0, 0.32, 0.5 - squeeze, 0.3, aa), disk(x, yy, 0, 0.12, 0.46 - squeeze, 0.22, aa))
    wrist = taper(x, yy, [(0, 0.55), (0, 0.95)], 0.26, 0.22, aa)
    fingers = np.zeros_like(x)
    knuck = np.zeros_like(x)
    for i, fx in enumerate((-0.36, -0.12, 0.12, 0.36)):
        L = 0.62 - 0.08 * abs(i - 1.5)
        b = (fx * (1 - squeeze * 3), -0.02)
        m = (fx * (1 - 0.25 * curl), -0.02 - L * 0.5 * (1 - 0.35 * curl))
        tip = (fx * (1 - 0.7 * curl), -0.02 - L * (1 - curl) - 0.32 * curl + 0.25 * curl)
        fingers = np.maximum(fingers, taper(x, yy, [b, m, tip], 0.1, 0.075, aa))
        knuck = np.maximum(knuck, disk(x, yy, b[0], b[1] + 0.02, 0.09, 0.06, aa))
    thumb = taper(x, yy, [(0.46, 0.22), (0.62 - 0.25 * curl, 0.0), (0.5 - 0.35 * curl, -0.2 + 0.12 * curl)], 0.1, 0.075, aa)
    hand = np.clip(np.maximum.reduce([palm, wrist, fingers, thumb]), 0, 1)
    from scipy import ndimage
    edge = np.clip(ndimage.maximum_filter(hand, size=5) - hand, 0, 1)
    lit = wet(hand, n)
    veins = np.zeros_like(x)
    if 3 <= f <= 4:
        rng = np.random.default_rng(3500 + f)
        for i in range(5):
            pts = bolt_path(rng, rng.uniform(-0.3, 0.3), 0.55, rng.uniform(-0.3, 0.3), 0.0, n=5, amp=0.05)
            veins = np.maximum(veins, taper(x, yy, pts, 0.016, 0.006, aa))
    layers = [(S.main(0.4), np.exp(-(np.hypot(x, yy - 0.15) / 0.75) ** 2) * 0.3 * k), (S.main(0.25), edge * k),
              (S.main(np.clip(0.38 + 0.42 * lit, 0, 1)), hand * k), (S.main(0.75), knuck * hand * 0.5 * k), (S.accent(0.85), veins * hand),
              (WHITE, veins * hand * 0.5), (WHITE, hand * sstep(0.82, 0.96, lit) * 0.45 * k)]
    if f == 5:
        layers.insert(0, (S.main(0.6), np.exp(-(x / 0.4) ** 2) * sstep(-0.1, 0.6, y) * 0.5))
    if f >= 6:
        N = Noise3(3510)
        sm = sstep(0.4, 0.7, N.fbm(x * 3, y * 3, f * 0.4, 3)) * np.exp(-(np.hypot(x, y) / 0.75) ** 2)
        layers.insert(0, (S.smoke(0.6), sm * (0.6 if f == 6 else 0.4)))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.03, gain=0.35)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 번개 기둥 (jpillar — 160 × 640 · 아래 끝 = 땅)

def pillar_frame(S, f, h=512, w=128, frames=PILLAR_FRAMES):
    x, y = gridhw(h, w)  # x −0.25 ~ 0.25 · y −1 ~ 1
    aa = 2.0 / h
    rng = np.random.default_rng(3600 + (f if f >= 3 else 0))
    main = bolt_path(rng, 0.0, 1.0, 0.0, -0.98, n=16, amp=0.06)
    if f == 0:
        main = main[:8]
    lw = [0.01, 0.045, 0.04, 0.03, 0.026, 0.03, 0.012, 0.0][f]
    body = taper(x, y, main, lw, lw * 0.8, aa) if lw > 0 else np.zeros_like(x)
    core = taper(x, y, main, lw * 0.4, lw * 0.35, aa) if lw > 0 else np.zeros_like(x)
    if f == 6:
        body = body * (np.sin(y * 9) > -0.2)
        core = core * (np.sin(y * 9) > -0.2)
    glow = _seg_glow(x, y, main, 0.07) * [0.4, 1.0, 0.85, 0.65, 0.6, 0.7, 0.3, 0][f]
    flash = disk(x, y, 0, -0.97, 0.24, 0.06, aa) * (f in (1, 2))
    sparks = np.zeros_like(x)
    if f >= 6:
        for i in range(9):
            sparks = np.maximum(sparks, disk(x, y, rng.uniform(-0.2, 0.2), rng.uniform(-0.95, -0.5), 0.012, 0.012, aa))
    layers = [(S.accent(0.55), glow), (S.accent(0.85), body), (S.main(0.9), flash), (WHITE, np.clip(core + flash * 0.6, 0, 1)), (S.main(0.9), sparks)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.01, gain=0.45)
    return rgb, a


FRAMES = {
    "arc": (ARC_FRAMES, arc_frame),
    "cloud": (CLOUD_FRAMES, cloud_frame),
    "stone": (STONE_FRAMES, stone_frame),
    "judge": (JUDGE_FRAMES, judge_frame),
    "wheel": (WHEEL_FRAMES, wheel_frame),
    "maulswing": (MAULSWING_FRAMES, maulswing_frame),
    "grip": (GRIP_FRAMES, grip_frame),
    "rpillar": (PILLAR_FRAMES, pillar_frame),  # 이름은 PhantomFx.PAINT jpillar → rpillar (룬 빛기둥과 같은 자리)
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
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "thunder"]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(FRAMES)
        preview({k: frames_of(k, S) for k in names}, out)
