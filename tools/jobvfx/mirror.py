"""2026-11-15 환영 (트릭스터 — 사용자 "다음 직업으로 암살자 만들고 있어봐" 의 2차 각성): 원소 공용 환영 묶음 el_illusion 의 트릭스터 대표 그림.

jobs.json 트릭스터 빛 (자홍 [0.96, 0.28, 0.8] · 짙은 보라 [0.32, 0.07, 0.46]) + 예전 거울 그림의 청록 유리빛 (JobFx.cross "트릭스터 거울 속 나의 X 자국 — 청록 환영빛").
예전 그림 (rfx_jmirror · jpane · jribbon · jmshatter — 흰 그림에 빛 두 겹) 과 **같은 틀**: 칸 · 칸 수 · 가로세로 비가 같음 (코드의 자리 · 방향 · 크기 그대로 그림만 바뀜).

- pane (128 × 224 · 4칸, 면이 dir): 아치 거울 — 장식 테 (꼭대기 마름모 · 옆 고리 · 밑 점) · 청록 유리 · 자홍 비침 · 1 · 2 칸에 빗긴 빛 줄 · 반짝임
- mshatter (256 × 320 · 8칸, 면이 dir): 아치 거울에 번쩍 → 금이 퍼짐 (0 ~ 3) → 조각으로 흩어짐 (4 ~ 7)
- mirror (192 · 6칸, 카메라를 봄): 타원 손거울 → 깨짐 번쩍 → 조각 고리로 퍼지며 사라짐
- ribbon (256 · 8칸 되풀이, 면이 dir): 꼬이며 도는 띠 고리 (자홍 · 분홍 날 · 청록 반짝임)

  cd tools && python3 -m jobvfx.mirror --preview 폴더 [--only pane,ribbon]
"""
import math
import sys

import numpy as np

from .base import Noise3, WHITE, bloom, compose, grid, preview, sstep, to_img
from .element import STYLES, star4, wet

PANE_FRAMES = 4
MSHATTER_FRAMES = 8
MIRROR_FRAMES = 6
RIBBON_FRAMES = 8


def S0():
    return STYLES["illusion"]


def rect_grid(w, h, ky):
    """가로 w · 세로 h 픽셀 — x 는 [-1, 1], y 는 [-ky, ky] (예전 파티클의 세로 몫)"""
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = ((np.arange(h) + 0.5) / h * 2 - 1) * ky
    x, y = np.meshgrid(xs, -ys)
    return x, y


def arch_sdf(x, y, hw, y0, y1):
    """아치 창 (밑 y0 · 곧은 옆 반폭 hw · 위는 반지름 hw 반원, 반원 가운데 높이 y1) 의 거리 (안 음수)"""
    lower = np.maximum(np.abs(x) - hw, y0 - y)
    upper = np.hypot(x, y - y1) - hw
    return np.where(y <= y1, lower, upper)


def poly(x, y, pts, aa):
    """볼록 다각형 (반시계 꼭짓점) 의 알파"""
    m = np.ones_like(x)
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        ex, ey = bx - ax, by - ay
        L = math.hypot(ex, ey) + 1e-9
        s = ((x - ax) * ey - (y - ay) * ex) / L  # 오른쪽 + (반시계면 안쪽이 −)
        m = m * (1 - sstep(-aa, aa, s))
    return m


def shard_pts(rng, cx, cy, r, ang):
    k = rng.integers(3, 5)
    a0 = rng.uniform(0, 2 * math.pi)
    pts = []
    for i in range(k):
        a = a0 + i * 2 * math.pi / k + rng.uniform(-0.35, 0.35)
        rr = r * rng.uniform(0.55, 1.0)
        pts.append((rr * math.cos(a), rr * math.sin(a)))
    ca, sa = math.cos(ang), math.sin(ang)
    return [(cx + px * ca - py * sa, cy + px * sa + py * ca) for px, py in pts]


def glass(S, x, y, body, t, n):
    """유리: 청록 바탕 (아래 짙고 위 옅음) · 자홍 비침 · 윤기"""
    base = S.accent(np.clip(0.28 + 0.22 * (y + 1.5) / 3.0, 0, 1))
    refl = np.exp(-((x * 0.8 + y * 0.45 - 0.2) / 0.35) ** 2)
    return [(base, body * 0.55), (S.main(0.62), body * refl * 0.32)]


# ------------------------------------------------------------------ 아치 거울 판

def pane_frame(S, f, w=128, h=224):
    x, y = rect_grid(w, h, 1.75)
    aa = 2.2 / w
    hw, y0, y1 = 0.68, -1.42, 0.72
    d = arch_sdf(x, y, hw, y0, y1)
    inner = 1 - sstep(-0.13 - aa, -0.13 + aa, d)
    frame = (1 - sstep(-aa, aa, d)) * (1 - inner)
    line = np.exp(-((d + 0.2) / 0.012) ** 2) * (d < -0.13)
    lit = wet(np.clip(frame, 0, 1), (h, w), light=(-0.5, 0.8))
    layers = [(S.main(0.25), np.exp(-np.clip(d, 0, None) / 0.1) * (d > 0) * 0.5)]
    layers += glass(S, x, y, inner, f / (PANE_FRAMES - 1), (h, w))
    # 빛 줄 (1 · 2 칸): 빗긴 띠 둘
    if f in (1, 2):
        c = -0.45 if f == 1 else 0.35
        u = x * 0.8 + y * 0.55 - c
        band = (np.exp(-(u / 0.12) ** 2) + 0.6 * np.exp(-((u - 0.3) / 0.05) ** 2)) * inner
        layers += [(S.accent(0.92), np.clip(band, 0, 1) * 0.8), (WHITE, np.clip(band - 0.4, 0, 1) * 0.9)]
    layers += [(S.main(0.12), frame), (S.main(0.55 + 0.35 * lit), frame * 0.95), (S.accent(0.85), line * 0.8), (WHITE, frame * lit ** 3 * 0.6)]
    # 장식: 꼭대기 마름모 · 옆 고리 · 밑 점
    top_y = y1 + hw + 0.12
    gem = 1 - sstep(0.09 - aa, 0.09 + aa, np.abs(x) + np.abs(y - top_y) * 0.8)
    knob = sum(1 - sstep(0.06 - aa, 0.06 + aa, np.hypot(x - sx, y + 0.15)) for sx in (-hw - 0.08, hw + 0.08))
    dot = 1 - sstep(0.05 - aa, 0.05 + aa, np.hypot(x, y - (y0 - 0.14)))
    layers += [(S.main(0.88), np.clip(gem + knob + dot, 0, 1)), (WHITE, np.clip(gem, 0, 1) * 0.6)]
    if f in (1, 2):
        sx, sy = (0.45, 1.05) if f == 1 else (-0.5, -0.9)
        layers.append((WHITE, star4(x, y, sx, sy, 0.22, 0.03, 0.3, aa)))
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.62, sigma=0.03, gain=0.5)
    return rgb, a * (1 - sstep(0.93, 0.99, np.abs(x))) * (1 - sstep(1.7, 1.745, np.abs(y)))


# ------------------------------------------------------------------ 깨지는 거울

def mshatter_frame(S, f, w=256, h=320):
    rng = np.random.default_rng(911)
    x, y = rect_grid(w, h, 1.25)
    aa = 2.0 / w
    hw, y0, y1 = 0.5, -0.95, 0.45
    d = arch_sdf(x, y, hw, y0, y1)
    layers = []
    if f <= 3:
        inner = 1 - sstep(-0.09 - aa, -0.09 + aa, d)
        frame = (1 - sstep(-aa, aa, d)) * (1 - inner)
        layers += glass(S, x, y, inner, 0, (h, w))
        # 금: 가운데 (0, 0) 에서 퍼지는 꺾인 줄 (칸마다 늘어남)
        cracks = np.zeros_like(x)
        grow = (f + 0.2) / 3.2
        for i in range(9):
            a = i * 2 * math.pi / 9 + rng.uniform(-0.25, 0.25)
            px, py = 0.0, 0.0
            L = 0.0
            for seg in range(5):
                a2 = a + rng.uniform(-0.5, 0.5)
                ln = rng.uniform(0.12, 0.22)
                if L + ln > 1.2 * grow:
                    break
                qx, qy = px + ln * math.cos(a2), py + ln * math.sin(a2) * 1.2
                dd = np.abs((x - px) * (qy - py) - (y - py) * (qx - px)) / (math.hypot(qx - px, qy - py) + 1e-9)
                along = ((x - px) * (qx - px) + (y - py) * (qy - py)) / ((qx - px) ** 2 + (qy - py) ** 2 + 1e-9)
                cracks = np.maximum(cracks, np.exp(-(dd / 0.008) ** 2) * (along > 0) * (along < 1))
                px, py, L = qx, qy, L + ln
        cracks = cracks * inner
        flash = np.exp(-(x * x + y * y) / (0.05 + 0.04 * f)) * (1.0 if f < 3 else 0.6)
        layers += [(S.main(0.15), frame), (S.main(0.75), frame * 0.9), (S.accent(0.9), cracks), (WHITE, cracks * 0.8),
                   (S.accent(0.85), flash * 0.7), (WHITE, star4(x, y, 0, 0, 0.32 + 0.1 * f, 0.04, 0.4, aa) * (1 - 0.2 * f))]
    else:
        k = (f - 4) / 3.0
        # 조각 (거울 자리에서 바깥으로 · 돌며 · 조금 떨어짐): 큰 조각 열넷 + 작은 조각 스물 — 유리 (청록 · 비침) · 테 조각 (자홍) · 흰 날
        for i in range(34):
            big = i < 14
            a = rng.uniform(0, 2 * math.pi)
            r0 = rng.uniform(0.0, 0.45) if big else rng.uniform(0.1, 0.6)
            r = r0 + (0.3 + rng.uniform(0, 0.6)) * (k * 1.2 + 0.2)
            cx, cy = r * math.cos(a), r * math.sin(a) * 1.25 - 0.3 * k * k
            size = (rng.uniform(0.11, 0.2) if big else rng.uniform(0.04, 0.09)) * (1 - 0.3 * k)
            pts = shard_pts(rng, cx, cy, size, rng.uniform(0, 6.3) + k * rng.uniform(-3, 3))
            m = poly(x, y, pts, aa)
            fr = rng.uniform() < 0.18
            tone = rng.uniform(0.3, 0.75)
            sh = np.clip(0.5 + (x - cx) * 3 + (y - cy) * 3, 0, 1)
            vis = (1 - k ** 1.6)
            if fr:
                layers += [(S.main(0.15), m * vis), (S.main(0.7), m * 0.9 * vis)]
            else:
                layers += [(S.accent(tone * (0.7 + 0.5 * sh)), m * 0.82 * vis), (S.main(0.75), m * np.exp(-((x - cx) - (y - cy) * 0.6) ** 2 / 0.002) * 0.4 * vis)]
            layers.append((WHITE, m * (1 - sstep(0.0, 0.012, np.abs((x - cx) * 0.7 + (y - cy) * 0.7 - size * 0.3))) * 0.6 * vis))
        for i in range(10):
            a = rng.uniform(0, 2 * math.pi)
            r = rng.uniform(0.3, 1.0) * (0.5 + k)
            layers.append((WHITE, star4(x, y, r * math.cos(a), r * math.sin(a), 0.06 * (1 - k), 0.01, a, aa) * (1 - k)))
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.6, sigma=0.025, gain=0.55)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x))) * (1 - sstep(1.2, 1.245, np.abs(y)))


# ------------------------------------------------------------------ 손거울

def mirror_frame(S, f, n=192):
    rng = np.random.default_rng(931)
    x, y = grid(n)
    aa = 1.8 / n
    layers = []
    if f == 0:
        e = np.hypot(x / 0.42, y / 0.74)
        inner = 1 - sstep(0.86 - 0.01, 0.86 + 0.01, e)
        rim = (1 - sstep(1.0 - 0.012, 1.0 + 0.012, e)) * (1 - inner)
        layers += glass(S, x, y * 1.5, inner, 0, n)
        layers += [(S.main(0.15), rim), (S.main(0.75), rim * 0.95), (WHITE, star4(x, y, 0.0, 0.1, 0.4, 0.05, 0.0, aa) * 0.9),
                   (S.accent(0.9), np.exp(-((x * 0.9 - y * 0.4 + 0.15) / 0.06) ** 2) * inner * 0.6)]
    else:
        k = (f - 1) / (MIRROR_FRAMES - 2)
        if f == 1:
            layers += [(S.accent(0.85), np.exp(-(x * x + y * y) / 0.06)), (WHITE, star4(x, y, 0, 0, 0.55, 0.06, 0.2, aa))]
        for i in range(18):
            a = i * 2 * math.pi / 18 + rng.uniform(-0.15, 0.15)
            r = 0.12 + 0.75 * (1 - (1 - k) ** 1.6) * rng.uniform(0.8, 1.15)
            cx, cy = r * math.cos(a), r * math.sin(a)
            pts = shard_pts(rng, cx, cy, rng.uniform(0.05, 0.09) * (1 - 0.4 * k), rng.uniform(0, 6.3) + 4 * k)
            m = poly(x, y, pts, aa)
            layers += [(S.accent(rng.uniform(0.3, 0.75)), m * 0.85 * (1 - k ** 1.6)), (WHITE, m * (i % 3 == 0) * 0.6 * (1 - k))]
        ring = np.exp(-((np.hypot(x, y) - (0.2 + 0.65 * k)) / (0.02 + 0.03 * k)) ** 2) * (1 - k) ** 1.2
        layers.append((S.main(0.8), ring * 0.6))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.6, sigma=0.03, gain=0.55)
    return rgb, a * (1 - sstep(0.94, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 꼬이며 도는 띠 고리

def ribbon_frame(S, f, n=256):
    N = Noise3(951)
    x, y = grid(n)
    aa = 1.6 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ph = f / RIBBON_FRAMES * 2 * math.pi
    R = 0.74 + 0.08 * np.sin(4 * th + ph)
    twist = np.cos(2 * th - ph * 2)
    width = 0.02 + 0.075 * np.abs(twist)
    d = np.abs(r - R)
    band = 1 - sstep(width - aa, width + aa, d)
    face = (twist > 0).astype(float)
    shade = 0.45 + 0.4 * np.abs(twist) + 0.15 * np.cos(th + ph)
    edge = np.exp(-((d - width) / 0.008) ** 2) * (width > 0.03)
    glow = np.exp(-(d / (width + 0.06)) ** 2) * 0.35
    layers = [(S.main(0.4), glow), (S.main(0.25 + 0.55 * shade), band * (0.75 + 0.25 * face)), (S.accent(0.55 + 0.3 * shade), band * (1 - face) * 0.55),
              (S.main(0.95), edge * 0.9), (WHITE, edge * np.clip(np.cos(th * 2 + ph), 0, 1) * 0.7)]
    for i in range(4):
        a = ph * 0.5 + i * math.pi / 2 + 0.4
        rr = 0.74 + 0.08 * math.sin(4 * a + ph)
        layers.append((WHITE, star4(x, y, rr * math.cos(a), rr * math.sin(a), 0.08, 0.012, a, aa) * (0.6 + 0.4 * math.cos(ph + i))))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.62, sigma=0.025, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 묶음

def frames_of(name, S=None):
    S = S or S0()
    if name == "pane":
        return [to_img(*pane_frame(S, f)) for f in range(PANE_FRAMES)]
    if name == "mshatter":
        return [to_img(*mshatter_frame(S, f)) for f in range(MSHATTER_FRAMES)]
    if name == "mirror":
        return [to_img(*mirror_frame(S, f)) for f in range(MIRROR_FRAMES)]
    if name == "ribbon":
        return [to_img(*ribbon_frame(S, f)) for f in range(RIBBON_FRAMES)]
    raise KeyError(name)


def sprites():
    """그림판 목록 [(이름, 만들기, colored)] — element.sets_for 가 더함 (el_illusion_ 접두)"""
    S = S0()
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    return [(p + k, fr(k), True) for k in ("pane", "mshatter", "mirror", "ribbon")]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else ["pane", "mshatter", "mirror", "ribbon"]
        preview({k: frames_of(k) for k in names}, out)
