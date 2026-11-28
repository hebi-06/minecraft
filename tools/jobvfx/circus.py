"""2026-11-15 서커스 (피에로 — 암살자의 2차 반전): 원소 공용 서커스 묶음 el_circus 의 피에로 대표 그림.

jobs.json 피에로 빛 (진홍 [0.95, 0.22, 0.32] · 크림 금빛 [1.0, 0.92, 0.62]) — 무지개 없이 진홍 · 금 · 크림 · 흰빛 (사용자 "무지개 빼").
예전 그림 (rfx_jconfetti · jballoon · jfirework · jspot) 과 **같은 틀**: 칸 · 칸 수 · 가로세로 비가 같음.

- confetti (256 · 6칸): 0 번쩍 · 1 ~ 3 네모 조각 · 리본이 돌며 사방으로 · 4 ~ 5 흩날리며 떨어짐
- balloon (192 · 8칸): 0 ~ 1 풍선 강아지 · 2 ~ 3 풍선 기린 (둥실) · 4 ~ 5 비틀린 풍선 고리 · 6 ~ 7 펑 (고리 · 고무 조각)
- firework (256 · 6칸): 0 번쩍 · 1 ~ 3 빛 구슬이 꼬리를 끌며 퍼짐 · 4 ~ 5 버들처럼 처지며 흐려짐
- spot (128 × 384 · 4칸 되풀이, 세로축): 위에서 내리비추는 빛 원뿔 · 바닥 웅덩이 · 떠도는 먼지
- mask (256 · 8칸, 2026-11-28): 웃는 광대 가면 — 0 튀어나옴 (번쩍) · 1 웃음 · 2 ~ 5 깔깔 (이어 돌림 — 입이 벌어졌다 닫힘 · 좌우로 기울며 들썩) · 6 윙크 · 7 금이 가며 흩어짐

  cd tools && python3 -m jobvfx.circus --preview 폴더 [--only confetti,balloon]
"""
import math
import sys

import numpy as np

from .base import WHITE, bloom, compose, grid, preview, ramp, sstep, to_img
from .element import STYLES, seg_dist, star4, wet

CONFETTI_FRAMES = 6
BALLOON_FRAMES = 8
FIREWORK_FRAMES = 6
SPOT_FRAMES = 4
GOLD = ramp([(0.0, "#2A1A06"), (0.35, "#8A5A12"), (0.6, "#D8A032"), (0.8, "#FFE28A"), (1.0, "#FFFBEA")])


def S0():
    return STYLES["circus"]


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


# ------------------------------------------------------------------ 색종이

def confetti_frame(S, f, n=256):
    rng = np.random.default_rng(1101)
    x, y = grid(n)
    aa = 1.6 / n
    layers = []
    if f == 0:
        r = np.hypot(x, y)
        layers += [(S.accent(0.8), np.exp(-(r / 0.18) ** 2)), (WHITE, star4(x, y, 0, 0, 0.35, 0.05, 0.3, aa)), (WHITE, np.exp(-(r / 0.07) ** 2))]
    else:
        k = (f - 1) / (CONFETTI_FRAMES - 2)
        cols = [S.main(0.62), S.main(0.78), GOLD(0.75), GOLD(0.9), S.accent(0.95), WHITE]
        for i in range(30):
            a = rng.uniform(0, 2 * math.pi)
            sp = rng.uniform(0.45, 0.95)
            r = sp * (1 - (1 - min(1.0, k * 1.6 + 0.25)) ** 2)
            cx = r * math.cos(a)
            cy = r * math.sin(a) - 0.35 * max(0.0, k - 0.4) ** 1.5 * rng.uniform(0.6, 1.2)
            ang = rng.uniform(0, 6.3) + k * rng.uniform(-6, 6)
            flip = abs(math.cos(rng.uniform(0, 6.3) + k * 9))
            c = cols[i % len(cols)]
            ca, sa = math.cos(ang), math.sin(ang)
            px = (x - cx) * ca + (y - cy) * sa
            py = -(x - cx) * sa + (y - cy) * ca
            vis = 1 - sstep(0.75, 1.0, k) * rng.uniform(0.6, 1.0)
            if i % 4 == 3:
                # 꼬인 리본 (사인 곡선 띠)
                L = 0.13
                wav = py - 0.03 * np.sin(px / L * 6 + k * 8)
                m = (1 - sstep(0.012, 0.018, np.abs(wav))) * (1 - sstep(L - 0.01, L, np.abs(px)))
            else:
                w, h = rng.uniform(0.025, 0.04), rng.uniform(0.04, 0.06) * (0.25 + 0.75 * flip)
                m = (1 - sstep(w - aa, w + aa, np.abs(px))) * (1 - sstep(h - aa, h + aa, np.abs(py)))
            shade = 0.75 + 0.25 * flip
            layers.append((c * shade, m * vis))
        for i in range(6):
            a = rng.uniform(0, 2 * math.pi)
            r = rng.uniform(0.3, 0.8) * (0.4 + 0.6 * k)
            layers.append((WHITE, star4(x, y, r * math.cos(a), r * math.sin(a), 0.06 * (1 - k), 0.01, a, aa) * (1 - k)))
    rgb_, a = compose(layers, n)
    return bloom(rgb_, a, n, thresh=0.7, sigma=0.02, gain=0.35)


# ------------------------------------------------------------------ 풍선

def _lobe(x, y, cx, cy, rx, ry, ang):
    ca, sa = math.cos(ang), math.sin(ang)
    px = (x - cx) * ca + (y - cy) * sa
    py = -(x - cx) * sa + (y - cy) * ca
    return np.clip(1 - (px / rx) ** 2 - (py / ry) ** 2, 0, 1)


def _balloon_body(S, x, y, lobes, n, hue):
    """풍선 마디들 (타원) → 윤기 · 마디 사이 매듭 · 짙은 테"""
    h = np.zeros_like(x)
    for cx, cy, rx, ry, ang in lobes:
        h = np.maximum(h, _lobe(x, y, cx, cy, rx, ry, ang))
    body = sstep(0.0, 0.08, h)
    lit = wet(body, n, light=(-0.55, 0.8))
    spec = np.zeros_like(x)
    for cx, cy, rx, ry, ang in lobes:
        spec = np.maximum(spec, np.exp(-(((x - (cx - rx * 0.35)) / (rx * 0.25)) ** 2 + ((y - (cy + ry * 0.35)) / (ry * 0.18)) ** 2)))
    tone = np.clip(0.35 + 0.35 * np.sqrt(h) + 0.25 * lit, 0, 1)
    return [(hue(0.1), np.clip(body * 1.2, 0, 1) * (1 - sstep(0.05, 0.25, h))), (hue(tone), body), (WHITE, spec * body * 0.85)]


def balloon_frame(S, f, n=192):
    rng = np.random.default_rng(1131)
    x, y = grid(n)
    aa = 1.8 / n
    layers = []
    bob = 0.03 * (1 if f % 2 == 0 else -1)
    hue = S.main
    if f in (0, 1):
        # 강아지: 머리 · 주둥이 · 귀 둘 · 몸 · 다리 넷 · 꼬리
        L = [(-0.38, 0.38 + bob, 0.2, 0.15, 0.2), (-0.62, 0.3 + bob, 0.15, 0.08, -0.3), (-0.3, 0.6 + bob, 0.1, 0.18, 0.5), (-0.48, 0.58 + bob, 0.1, 0.18, -0.4),
             (0.05, 0.12 + bob, 0.38, 0.14, 0.05), (-0.22, -0.25 + bob, 0.08, 0.22, 0.1), (-0.05, -0.27 + bob, 0.08, 0.22, -0.1),
             (0.3, -0.2 + bob, 0.08, 0.22, 0.12), (0.45, -0.18 + bob, 0.08, 0.22, -0.12), (0.55, 0.32 + bob, 0.07, 0.2, -0.6)]
        layers += _balloon_body(S, x, y, L, n, hue)
    elif f in (2, 3):
        # 기린: 긴 목 · 머리 · 뿔 · 몸 · 다리 넷
        L = [(0.1, -0.05 + bob, 0.32, 0.13, 0.0), (-0.18, 0.35 + bob, 0.08, 0.36, -0.25), (-0.33, 0.72 + bob, 0.16, 0.09, 0.15), (-0.3, 0.86 + bob, 0.035, 0.08, 0.0),
             (-0.22, 0.86 + bob, 0.035, 0.08, 0.0), (-0.12, -0.42 + bob, 0.07, 0.26, 0.05), (0.04, -0.44 + bob, 0.07, 0.26, -0.05),
             (0.25, -0.42 + bob, 0.07, 0.26, 0.05), (0.38, -0.4 + bob, 0.07, 0.26, -0.08)]
        layers += _balloon_body(S, x, y, L, n, lambda t: GOLD(0.25 + 0.75 * t))
    elif f in (4, 5):
        # 비틀린 풍선 고리 (마디 여덟 — 엇갈려 돎)
        L = []
        for i in range(8):
            a = i * math.pi / 4 + (0.2 if f == 5 else 0)
            L.append((0.62 * math.cos(a), 0.62 * math.sin(a) * 0.8, 0.2, 0.1, a + math.pi / 2))
        layers += _balloon_body(S, x, y, L, n, lambda t: (S.main(t) if True else None))
    else:
        k = f - 6
        r = np.hypot(x, y)
        R = 0.3 + 0.45 * k
        ring = np.exp(-((r - R) / (0.04 + 0.03 * k)) ** 2) * (1 - 0.6 * k)
        layers += [(S.accent(0.9), ring * 0.8), (WHITE, star4(x, y, 0, 0, 0.5 - 0.2 * k, 0.05, 0.3, aa) * (1 - 0.7 * k))]
        for i in range(12):
            a = rng.uniform(0, 2 * math.pi)
            rr = (0.25 + 0.5 * k) * rng.uniform(0.8, 1.2) + 0.1
            m = _lobe(x, y, rr * math.cos(a), rr * math.sin(a), 0.06, 0.03, a + rng.uniform(-1, 1))
            layers.append((S.main(0.65), sstep(0.0, 0.2, m) * (1 - 0.5 * k)))
    rgb_, a = compose(layers, n)
    rgb_, a = bloom(rgb_, a, n, thresh=0.72, sigma=0.03, gain=0.3)
    return rgb_, a * (1 - sstep(0.94, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 불꽃놀이

def firework_frame(S, f, n=256):
    rng = np.random.default_rng(1151)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    layers = []
    if f == 0:
        layers += [(GOLD(0.85), np.exp(-(r / 0.15) ** 2)), (WHITE, star4(x, y, 0, 0, 0.3, 0.04, 0.0, aa))]
    else:
        k = (f - 1) / (FIREWORK_FRAMES - 2)
        R = 0.25 + 0.6 * (1 - (1 - min(1.0, k * 1.5)) ** 2)
        droop = 0.45 * max(0.0, k - 0.5) ** 1.4
        trail = np.zeros_like(x)
        head = np.zeros_like(x)
        for i in range(28):
            a = i * 2 * math.pi / 28 + rng.uniform(-0.05, 0.05)
            rr = R * rng.uniform(0.85, 1.05)
            hx, hy = rr * math.cos(a), rr * math.sin(a) - droop
            tx, ty = 0.55 * rr * math.cos(a), 0.55 * rr * math.sin(a) - droop * 0.4
            d, s = seg_dist(x, y, tx, ty, hx, hy)
            trail = np.maximum(trail, np.exp(-(d / (0.008 + 0.006 * s)) ** 2) * s ** 1.5)
            head = np.maximum(head, np.exp(-((x - hx) ** 2 + (y - hy) ** 2) / 0.0006))
        fade = 1 - sstep(0.6, 1.0, k)
        layers += [(GOLD(0.8), np.exp(-(r / (R * 0.7)) ** 2) * 0.12 * fade), (GOLD(0.7), trail * fade), (S.main(0.8), trail * 0.35 * fade),
                   (WHITE, head * fade)]
        for i in range(8):
            a = rng.uniform(0, 2 * math.pi)
            rr = R * rng.uniform(0.6, 1.1)
            layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a) - droop, 0.05, 0.008, a, aa) * fade * (0.5 + 0.5 * math.cos(f * 3 + i))))
    rgb_, a = compose(layers, n)
    rgb_, a = bloom(rgb_, a, n, thresh=0.6, sigma=0.025, gain=0.6)
    return rgb_, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 조명

def spot_frame(S, f, w=128, h=384):
    rng = np.random.default_rng(1171)
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = ((np.arange(h) + 0.5) / h * 2 - 1) * 3
    x, y = np.meshgrid(xs, -ys)
    u = (2.9 - y) / 5.6  # 위 0 → 바닥 1
    half = 0.08 + 0.82 * np.clip(u, 0, 1)
    cone = (1 - sstep(half * 0.85, half, np.abs(x))) * sstep(-0.05, 0.05, u) * (1 - sstep(0.98, 1.02, u))
    core = np.exp(-(x / (half * 0.45 + 1e-3)) ** 2) * cone
    ph = f / SPOT_FRAMES * 2 * math.pi
    pool = np.exp(-((x / 0.95) ** 2 + ((y + 2.75) / 0.12) ** 2))
    motes = np.zeros_like(x)
    for i in range(16):
        my = rng.uniform(-2.5, 2.4)
        mu = (2.9 - my) / 5.6
        mx = rng.uniform(-0.8, 0.8) * (0.08 + 0.82 * mu) + 0.05 * math.sin(ph + i)
        my = my + 0.12 * math.sin(ph + i * 1.3)
        motes = np.maximum(motes, np.exp(-((x - mx) ** 2 + (y - my) ** 2) / 0.0012) * (0.6 + 0.4 * math.cos(ph * 2 + i)))
    layers = [(GOLD(0.85), cone * (0.1 + 0.14 * u)), (GOLD(0.97), core * 0.28), (WHITE, core * 0.18), (GOLD(0.92), pool * 0.6), (WHITE, motes * cone)]
    rgb_, a = compose(layers, (h, w))
    return rgb_, a * (1 - sstep(0.93, 0.99, np.abs(x)))


# ------------------------------------------------------------------ 웃는 가면 (2026-11-28 — 피에로 펑! · 그랜드 피날레, 사용자 "스타레일의 아하 느낌": 느낌만 — 이름 · 고유 디자인은 쓰지 않음)

MASK_FRAMES = 8
# 장마다 (크기, 기울기 도, 입 벌림 0 ~ 1, 눈 찡그림 0 ~ 1, 윙크) — 0 튀어나옴 · 1 웃음 · 2 ~ 5 깔깔 (이어 돌림) · 6 윙크 · 7 터져 흩어짐
MASK_POSE = [(0.55, 0, 0.2, 0.0, False), (1.06, -4, 0.3, 0.2, False), (1.0, 7, 0.95, 0.75, False), (1.0, 2, 0.5, 0.45, False), (1.0, -7, 0.95, 0.75, False),
             (1.0, -2, 0.5, 0.45, False), (1.03, 5, 0.65, 0.4, True), (1.12, 0, 0.7, 0.4, False)]


def _arc_band(x, y, cx, cy, r, w, a0, a1, aa):
    """원호 띠 (가운데 cx, cy · 반지름 r · 굵기 w · 각 a0 ~ a1 라디안) — 끝이 둥긂"""
    d = np.hypot(x - cx, y - cy)
    ang = np.arctan2(y - cy, x - cx)
    inside = (ang >= a0) & (ang <= a1)
    band = np.where(inside, np.abs(d - r), 9.0)
    for a in (a0, a1):
        ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
        band = np.minimum(band, np.hypot(x - ex, y - ey))
    return 1 - sstep(w - aa, w + aa, band)


def mask_frame(S, f, n=256):
    """광대 가면 (카메라를 봄): 흰 자기 얼굴 · 금빛 테 · 웃는 초승달 눈 · 크게 웃는 입 (이 · 혀) · 눈 아래 진홍 마름모 · 볼 · 이마의 금빛 별"""
    rng = np.random.default_rng(1191 + f)
    X, Y = grid(n)
    aa = 1.8 / n
    s, tilt, opn, squint, wink = MASK_POSE[f]
    t = math.radians(tilt)
    bob = 0.03 * math.sin(f * 1.7) if 2 <= f <= 5 else 0.0
    # 가면 좌표 (기울이고 · 크기 · 살짝 들썩임)
    x = (X * math.cos(t) + (Y - bob) * math.sin(t)) / s
    y = (-X * math.sin(t) + (Y - bob) * math.cos(t)) / s
    layers = []
    # 얼굴: 위는 둥글고 아래는 턱으로 좁아지는 달걀
    cy = 0.08
    ry = np.where(y > cy, 0.6, 0.8)
    rx = 0.6 * (1 - 0.3 * np.clip((cy - y) / 0.8, 0, 1))
    h = 1 - (x / rx) ** 2 - ((y - cy) / ry) ** 2
    face = sstep(-0.02, 0.03, h)
    rim = face * (1 - sstep(0.1, 0.16, h))
    lit = wet(face, n, light=(-0.5, 0.85))
    shade = np.clip(0.78 + 0.22 * np.sqrt(np.clip(h, 0, 1)) + 0.18 * lit, 0, 1)
    porcelain = np.array([1.0, 0.965, 0.92])
    layers.append((porcelain * 0.62, face))  # 짙은 밑칠 (테두리 그늘)
    layers.append((porcelain, face * shade))
    layers.append((WHITE, face * np.exp(-(((x + 0.22) / 0.2) ** 2 + ((y - 0.38) / 0.12) ** 2)) * 0.55))  # 윤기
    # 볼 (분홍) · 눈 아래 진홍 마름모
    for sx in (-1, 1):
        layers.append((S.main(0.88), face * np.exp(-(((x - sx * 0.33) / 0.13) ** 2 + ((y + 0.12) / 0.08) ** 2)) * 0.45))
        dm = 1 - (np.abs(x - sx * 0.3) / 0.065 + np.abs(y - 0.0) / 0.13)
        dmask = sstep(-0.04, 0.04, dm) * face
        layers.append((S.main(0.55), dmask))
        layers.append((S.main(0.8), dmask * np.clip(1 - np.abs(x - sx * 0.3 + 0.02) / 0.03, 0, 1) * 0.6))
    # 눈: 웃는 초승달 (위로 볼록한 굵은 호 — 찡그릴수록 납작) · 윙크면 오른눈은 꺾인 줄
    for sx in (-1, 1):
        ex, ey = sx * 0.25, 0.2
        if wink and sx > 0:
            d1, k1 = seg_dist(x, y, ex - 0.12, ey + 0.02, ex, ey - 0.04)
            d2, k2 = seg_dist(x, y, ex, ey - 0.04, ex + 0.12, ey + 0.02)
            eye = 1 - sstep(0.025 - aa, 0.025 + aa, np.minimum(d1, d2))
        else:
            r = 0.13
            yy = ey + (y - ey) / (1 - 0.45 * squint)
            eye = _arc_band(x, yy, ex, ey - 0.06, r, 0.042 * (1 - 0.3 * squint), math.radians(25), math.radians(155), aa)
        layers.append((GOLD(0.62), np.clip(ndimage_blur(eye, n, 0.012) * 1.6, 0, 1) * face))
        layers.append((np.array([0.1, 0.02, 0.05]), eye * face))
    # 입: 양끝이 올라간 큰 웃음 — 벌릴수록 아래로 깊어짐 (속 = 짙은 진홍 · 혀 · 위 이)
    half = 0.36
    u = np.clip(x / half, -1, 1)
    yt = -0.2 + 0.13 * u ** 2
    depth = (0.045 + 0.22 * opn) * (1 - u ** 2)
    yb = yt - depth
    inside = (np.abs(x) < half) & (y < yt) & (y > yb)
    mouth = inside.astype(float)
    mouth = np.clip(ndimage_blur(mouth, n, 0.004) * 1.15, 0, 1) * face
    layers.append((GOLD(0.66), np.clip(ndimage_blur(mouth, n, 0.014) * 1.5, 0, 1) * face))
    layers.append((S.main(0.12), mouth))
    tongue = mouth * np.exp(-((x / 0.15) ** 2 + ((y - (yb + 0.05 * opn)) / (0.05 + 0.06 * opn)) ** 2)) * min(1.0, opn * 1.4)
    layers.append((S.main(0.66), tongue))
    teeth = mouth * (1 - sstep(0.0, 0.035 + 0.02 * opn, yt - y)) * (np.abs(x) < half * 0.82)
    layers.append((WHITE, teeth * 0.95))
    # 금빛 테 (안쪽 가는 줄 · 바깥 굵은 테) · 이마의 금빛 별
    layers.append((GOLD(0.55), rim))
    layers.append((GOLD(0.88), rim * (1 - sstep(0.03, 0.07, np.abs(h - 0.05))) * 0.9))
    layers.append((GOLD(0.9), star4(x, y, 0, 0.53, 0.11, 0.03, 0.0, aa / s) * face))
    layers.append((WHITE, star4(x, y, 0, 0.53, 0.05, 0.012, 0.0, aa / s) * face))
    # 깔깔 웃음 · 윙크 반짝임 (가면 둘레 — 그림 좌표)
    if 2 <= f <= 6:
        for i in range(5):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.72, 0.88)
            layers.append((GOLD(0.95), star4(X, Y, rr * math.cos(a), rr * math.sin(a), 0.07, 0.012, a, aa)))
        if wink:
            layers.append((WHITE, star4(X, Y, 0.47, 0.42, 0.12, 0.018, 0.3, aa)))
    if f == 0:
        r0 = np.hypot(X, Y)
        layers = [(GOLD(0.85), np.exp(-(r0 / 0.5) ** 2) * 0.6)] + layers + [(WHITE, star4(X, Y, 0, 0, 0.75, 0.05, 0.25, aa)),
                                                                             (WHITE, np.exp(-(r0 / 0.12) ** 2))]
    rgb_, a = compose(layers, n)
    if f == 7:
        # 터짐: 가면에 금빛 금이 가며 흐려짐 (가장자리부터) + 색종이 조각 · 빛 고리
        r0 = np.hypot(X, Y)
        a = a * 0.6 * (1 - sstep(0.35, 0.95, r0) * 0.7)
        crack = np.zeros_like(X)
        for i in range(9):
            ang = i * 2 * math.pi / 9 + rng.uniform(-0.2, 0.2)
            px, py = 0.05 * math.cos(ang), 0.1 + 0.05 * math.sin(ang)
            for _ in range(3):
                ang2 = ang + rng.uniform(-0.5, 0.5)
                qx, qy = px + 0.22 * math.cos(ang2), py + 0.22 * math.sin(ang2)
                d, _k = seg_dist(X, Y, px, py, qx, qy)
                crack = np.maximum(crack, 1 - sstep(0.008, 0.016, d))
                px, py = qx, qy
        ring = np.exp(-((r0 - 0.86) / 0.05) ** 2) * 0.8
        extra = [(GOLD(0.95), crack * (a > 0.05) * 0.95), (WHITE, crack * (a > 0.05) * 0.4), (GOLD(0.9), ring)]
        for i in range(18):
            ang = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(0.6, 0.92)
            c = (S.main(0.62), GOLD(0.85), WHITE)[i % 3]
            px, py = rr * math.cos(ang), rr * math.sin(ang)
            m = (1 - sstep(0.025, 0.035, np.abs(X - px))) * (1 - sstep(0.04, 0.05, np.abs(Y - py)))
            extra.append((c, m))
        rgb_, a = compose([(rgb_, a)] + extra, n)
    rgb_, a = bloom(rgb_, a, n, thresh=0.8, sigma=0.02, gain=0.25)
    return rgb_, a * (1 - sstep(0.94, 0.995, np.maximum(np.abs(X), np.abs(Y))))


def ndimage_blur(m, n, sigma):
    from scipy import ndimage
    return ndimage.gaussian_filter(m, max(0.5, n * sigma))


# ------------------------------------------------------------------ 묶음

def frames_of(name, S=None):
    S = S or S0()
    if name == "mask":
        return [to_img(*mask_frame(S, f)) for f in range(MASK_FRAMES)]
    if name == "confetti":
        return [to_img(*confetti_frame(S, f)) for f in range(CONFETTI_FRAMES)]
    if name == "balloon":
        return [to_img(*balloon_frame(S, f)) for f in range(BALLOON_FRAMES)]
    if name == "firework":
        return [to_img(*firework_frame(S, f)) for f in range(FIREWORK_FRAMES)]
    if name == "spot":
        return [to_img(*spot_frame(S, f)) for f in range(SPOT_FRAMES)]
    raise KeyError(name)


def sprites():
    """그림판 목록 [(이름, 만들기, colored)] — element.sets_for 가 더함 (el_circus_ 접두)"""
    S = S0()
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    return [(p + k, fr(k), True) for k in ("confetti", "balloon", "firework", "spot", "mask")]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else ["confetti", "balloon", "firework", "spot", "mask"]
        preview({k: frames_of(k) for k in names}, out)
