"""2026-11-16 전사 라인 (남은 직업 위르드식 — 사용자 "이거 다 하고 알아서 1,2,3번 진행해"): 전사 (분노 — el_rage) · 워로드 (곰 — el_bear) 의 대표 그림.

예전 그림 (rfx_jwolf · jtwister2 · jwhirl2 · jcomet2 · jbreak2 · jquake · jbear · jpaw · jspike — 흰 그림에 빛 두 겹) 과 **같은 틀** (칸 수 · 가로세로 비 ·
머리 자리 · 땅 자리 · 도는 쪽) — 코드의 자리 · 방향 · 크기 · 움직임은 그대로 그림만 바뀜 (PhantomFx.PAINT · painter). 도트로 옮기므로 (pixel.py) 굵은 실루엣 ·
짙은 테 · 밝은 심 위주.

- wolf (jwolf · 2 : 1 · 6 되풀이 — 가는 쪽 +x, 머리 x 0.6): 불꽃 갈기 늑대 머리 — 귀 · 이빨 · 흰 눈, 갈기가 뒤로 흩날림
- twister (jtwister2 · 1 : 2 · 8 되풀이 — 세움, 아래 끝 = 발): 아래가 좁은 불꽃 회오리 · 감아 오르는 띠 · 불티
- whirl (jwhirl2 · 8 되풀이 — 땅에 누움, 칼날 반지름 0.8 · 장마다 시계 방향 15°): 도끼날 셋이 돌며 남기는 잔상 호
- comet (jcomet2 · 256 : 96 · 4 되풀이 — 머리 x 0.64): 불덩이 머리 · 일렁이는 꼬리
- shieldbreak (jbreak2 · 6): 방패가 금 가 둘로 갈라져 흩어짐
- quake (jquake · 12 — 땅에 누움, 충격 고리 0.92): 가운데 번쩍 → 충격 고리 · 갈라지는 땅 (달아오른 금) → 식음
- bear (jbear · 8 — 0 ~ 2 모임 · 3 ~ 6 포효 (되풀이) · 7 흩어짐): 곰의 혼 — 둥근 머리 · 귀 · 벌린 입 · 흰 눈 · 털끝 불길
- paw (jpaw · 4 되풀이): 곰 앞발 — 발바닥 · 발가락 넷 · 아래로 뻗은 발톱 · 위로 끌리는 바람 줄
- spike (jspike · 8 — 아래 끝 = 땅: 0 ~ 2 솟음 · 3 ~ 5 서 있음 (되풀이) · 6 ~ 7 무너짐): 돌창 무리 · 갈라진 틈의 빛

  cd tools && python3 -m jobvfx.beast --preview 폴더 [--style rage|bear] [--only wolf,bear]
"""
import math
import sys

import numpy as np

from .base import Noise3, WHITE, bloom, compose, preview, sstep, to_img

WOLF_FRAMES = 6
TWISTER_FRAMES = 8
WHIRL_FRAMES = 8
COMET_FRAMES = 4
BREAK_FRAMES = 6
QUAKE_FRAMES = 12
BEAR_FRAMES = 8
PAW_FRAMES = 4
SPIKE_FRAMES = 8


def gridhw(h, w):
    """(높이, 너비) 칸의 좌표 — 긴 변이 −1 ~ 1, 같은 단위 (y 위로)"""
    s = max(h, w)
    xs = ((np.arange(w) + 0.5) - w / 2) / (s / 2)
    ys = -((np.arange(h) + 0.5) - h / 2) / (s / 2)
    return np.meshgrid(xs, ys)


def disk(x, y, cx, cy, rx, ry, aa):
    d = np.hypot((x - cx) / rx, (y - cy) / ry)
    return 1 - sstep(1 - aa / min(rx, ry), 1 + aa / min(rx, ry), d)


def tri(x, y, p0, p1, p2, aa):
    """삼각형 (꼭짓점 셋) 안 — 가장자리 부드럽게"""
    def edge(a, b, c):
        ex, ey = b[0] - a[0], b[1] - a[1]
        nx, ny = -ey, ex
        L = math.hypot(nx, ny) + 1e-12
        nx, ny = nx / L, ny / L
        # 셋째 점 c 가 + 쪽이 되게
        if (c[0] - a[0]) * nx + (c[1] - a[1]) * ny < 0:
            nx, ny = -nx, -ny
        return sstep(-aa, aa, (x - a[0]) * nx + (y - a[1]) * ny)
    return edge(p0, p1, p2) * edge(p1, p2, p0) * edge(p2, p0, p1)


def taper(x, y, pts, w0, w1, aa, p=1.0):
    """꺾은 선을 따라 굵기 w0 → w1 로 가늘어지는 띠 (알파 0 ~ 1)"""
    pts = np.asarray(pts, float)
    seglen = np.hypot(np.diff(pts[:, 0]), np.diff(pts[:, 1]))
    tot = seglen.sum() + 1e-12
    acc = np.concatenate([[0], np.cumsum(seglen)]) / tot
    m = np.zeros_like(x)
    for i in range(len(pts) - 1):
        ax, ay = pts[i]
        bx, by = pts[i + 1]
        ex, ey = bx - ax, by - ay
        L2 = ex * ex + ey * ey + 1e-12
        t = np.clip(((x - ax) * ex + (y - ay) * ey) / L2, 0, 1)
        d = np.hypot(x - (ax + t * ex), y - (ay + t * ey))
        q = acc[i] + (acc[i + 1] - acc[i]) * t
        w = w0 + (w1 - w0) * q ** p
        m = np.maximum(m, 1 - sstep(w - aa, w + aa, d))
    return m


def outline(mask, px):
    """실루엣 바깥 테 (px 픽셀 두께 — 도트로 옮길 때 짙은 테가 되게)"""
    from scipy import ndimage
    grown = ndimage.maximum_filter(mask, size=2 * px + 1)
    return np.clip(grown - mask, 0, 1)


def style(kind):
    from .element import STYLES
    return STYLES[kind]


# ------------------------------------------------------------------ 늑대 머리 (jwolf)

def wolf_frame(S, f, h=256, w=512, frames=WOLF_FRAMES):
    x, y = gridhw(h, w)
    aa = 2.0 / w
    ph = f / frames * 2 * math.pi
    nz = Noise3(S.seed + 41)
    snarl = 0.5 + 0.5 * math.sin(ph)  # 0 ~ 1 (입이 벌어짐)
    # 머리 (크게 — 칸 높이의 절반 넘게): 두개 · 이마 · 주둥이 · 아래턱 · 귀 둘 · 목덜미 갈기 뿌리
    skull = disk(x, y, 0.47, 0.06, 0.2, 0.19, aa)
    brow = tri(x, y, (0.36, 0.2), (0.66, 0.13), (0.4, 0.02), aa)
    snout = tri(x, y, (0.5, 0.16), (0.97, 0.02), (0.55, -0.06), aa)
    jaw_a = math.radians(8 + 14 * snarl)
    jx, jy = 0.56, -0.06
    tip = (jx + 0.34 * math.cos(-jaw_a), jy + 0.34 * math.sin(-jaw_a))
    jaw = tri(x, y, (0.42, -0.02), tip, (0.46, -0.17), aa)
    ear1 = tri(x, y, (0.3, 0.16), (0.46, 0.22), (0.22, 0.47), aa)
    ear2 = tri(x, y, (0.42, 0.2), (0.56, 0.17), (0.4, 0.46), aa)
    neck = disk(x, y, 0.3, -0.01, 0.2, 0.21, aa)
    head = np.clip(skull + brow + snout + jaw + ear1 + ear2 + neck, 0, 1)
    # 입 안 (어둠) · 이빨 · 눈 (흰빛 — 이마 아래 짙은 눈두덩)
    mouth = tri(x, y, (0.5, -0.05), (0.96, 0.0), tip, aa) * (snarl > 0.05)
    fangs = np.zeros_like(x)
    for fx_, fy_, L in ((0.9, 0.0, 0.07), (0.76, -0.02, 0.08), (0.62, -0.04, 0.06)):
        fangs = np.maximum(fangs, tri(x, y, (fx_ - 0.018, fy_), (fx_ + 0.018, fy_), (fx_, fy_ - L), aa))
    lowfang = tri(x, y, (tip[0] - 0.09, tip[1] + 0.03), (tip[0] - 0.055, tip[1] + 0.03), (tip[0] - 0.072, tip[1] + 0.09), aa)
    fangs = np.clip(fangs + lowfang, 0, 1) * mouth.max()
    socket = disk(x, y, 0.6, 0.085, 0.07, 0.035, aa)
    eye = disk(x, y, 0.61, 0.085, 0.045, 0.018, aa)
    # 갈기: 머리 뒤에서 뒤로 흩날리는 불꽃 혀 (되풀이 — 물결이 뒤로 흐름)
    mane = np.zeros_like(x)
    rng = np.random.default_rng(S.seed + 7)
    for i in range(11):
        y0 = -0.24 + 0.5 * i / 10 + rng.uniform(-0.02, 0.02)
        L = rng.uniform(0.95, 1.3)
        amp = rng.uniform(0.03, 0.065)
        k = rng.uniform(5.0, 7.5)
        pts = []
        for q in np.linspace(0, 1, 28):
            xx = 0.3 - L * q
            yy = y0 * (1 + 0.35 * q) + amp * q * math.sin(k * q * math.pi + ph + i)
            pts.append((xx, yy))
        mane = np.maximum(mane, taper(x, y, pts, rng.uniform(0.06, 0.09), 0.004, aa, p=0.8) * (0.75 + 0.25 * math.sin(ph * 2 + i)))
    grain = nz.fbm(x * 6 + f * 0.7, y * 6, f * 0.4, oct=3)
    mane = np.clip(mane * (0.75 + 0.5 * grain), 0, 1)
    rim = outline((head > 0.5).astype(float), 3) * 0.95
    # 빛: 위 (이마 · 등) 밝게 · 아래턱 · 목 아래 짙게
    shade = np.clip(0.5 + 0.9 * (y + 0.05) + 0.15 * (x - 0.4), 0, 1)
    top = head * sstep(0.1, 0.22, y + 0.25 * (x - 0.5)) * sstep(0.35, 0.6, x)
    layers = [(S.main(0.3), rim * 0.9),
              (S.main(0.42 + 0.4 * mane), np.clip(mane * 1.2, 0, 1) * (1 - head)),
              (S.main(0.5 + 0.38 * shade), head),
              (S.main(0.9), top * 0.6),
              (S.main(0.15), mouth * 0.95),
              (S.main(0.22), socket * head),
              (S.accent(0.97), fangs),
              (WHITE, eye)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.012, gain=0.45)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x)))


# ------------------------------------------------------------------ 회오리 (jtwister2)

def twister_frame(S, f, h=512, w=256, frames=TWISTER_FRAMES):
    x, y = gridhw(h, w)  # x −0.5 ~ 0.5 · y −1 ~ 1
    aa = 2.0 / h
    ph = f / frames * 2 * math.pi
    nz = Noise3(S.seed + 23)
    q = np.clip((y + 0.98) / 1.88, 0, 1)  # 0 아래 → 1 위
    half = 0.05 + 0.34 * q ** 1.2
    sway = 0.035 * np.sin(q * 5 - ph)
    u = (x - sway) / np.maximum(half, 1e-3)
    grain = nz.fbm(x * 7, y * 4 - f * 0.5, f * 0.5, oct=4)
    # 위 끝은 잡음으로 흩어짐 (평평하게 잘리지 않게)
    top = 1 - sstep(0.78, 0.98, q + (grain - 0.5) * 0.35)
    inside = (1 - sstep(0.9, 1.02, np.abs(u) + (grain - 0.5) * 0.18)) * sstep(-0.99, -0.95, y) * top
    th = np.arcsin(np.clip(u, -1, 1))
    # 몸: 반쯤 비치는 불길 (안쪽 짙게) + 감아 오르는 밝은 줄기 (앞) · 짙은 줄기 (뒤)
    band_f = 0.5 + 0.5 * np.cos(th * 2.0 + q * 15 - ph * 2)
    band_b = 0.5 + 0.5 * np.cos(-th * 2.0 + q * 15 - ph * 2 + 1.3)
    streak = sstep(0.62, 0.86, band_f) * (0.7 + 0.6 * (grain - 0.5))
    backs = sstep(0.62, 0.86, band_b)
    edge = sstep(0.6, 0.95, np.abs(u))
    fill = inside * (0.55 + 0.3 * q)
    core = np.exp(-(u / 0.3) ** 2) * (0.3 + 0.5 * q) * inside
    rng = np.random.default_rng(S.seed + 13 + f)
    sparks = np.zeros_like(x)
    for i in range(16):
        qq = rng.uniform(0.1, 1.0)
        yy = qq * 1.88 - 0.98
        hh = 0.05 + 0.34 * qq ** 1.2
        xx = rng.choice([-1, 1]) * hh * rng.uniform(1.05, 1.3)
        sparks = np.maximum(sparks, disk(x, y, xx, yy, 0.016, 0.016, aa))
    dust = disk(x, y, 0, -0.94, 0.32, 0.06, aa) * 0.6
    layers = [(S.smoke(0.6), dust),
              (S.main(0.32), fill),
              (S.main(0.22), backs * inside * 0.8),
              (S.main(0.55), edge * inside * 0.9),
              (S.main(0.72 + 0.2 * q), np.clip(streak, 0, 1) * inside),
              (S.main(0.95), np.clip(core, 0, 1) * 0.6),
              (S.accent(0.95), sparks)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.012, gain=0.4)
    return rgb, a


# ------------------------------------------------------------------ 도는 도끼날 (jwhirl2 — 땅에 누움)

def axe_head(x, y, ang, R, aa):
    """반지름 R 자리 (각 ang) 의 도끼 머리 — 바깥으로 넓어지는 날 (수염 도끼), 날 끝이 시계 방향 (도는 쪽) 앞"""
    ca, sa = math.cos(ang), math.sin(ang)
    lr = x * ca + y * sa  # 바깥
    lt = x * sa - y * ca  # 시계 방향 접선 (+ = 도는 앞)
    # 날: 안쪽 좁고 바깥 넓은 사다리꼴 + 바깥 호 (날)
    inner, outer = R - 0.2, R + 0.06
    q = np.clip((lr - inner) / (outer - inner), 0, 1)
    hw = 0.05 + 0.13 * q ** 1.3
    body = (lr > inner) * (lr < outer + 0.04 - 0.12 * (lt / 0.2) ** 2) * (np.abs(lt - 0.03 * q) < hw)
    edge = body * (lr > outer - 0.05 - 0.12 * (lt / 0.2) ** 2)
    haft = (np.abs(lt) < 0.022) * (lr > R - 0.48) * (lr < inner + 0.02)
    return body.astype(float), edge.astype(float), haft.astype(float)


def whirl_frame(S, f, n=256, frames=WHIRL_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    R = 0.8
    rot = -math.radians(15 * f)  # 장마다 시계 방향 15° (예전 그림과 같은 쪽 · 셋이라 8장이면 120° — 이어짐)
    heads = np.zeros_like(x)
    edges = np.zeros_like(x)
    hafts = np.zeros_like(x)
    sweep = np.zeros_like(x)
    sweep_hi = np.zeros_like(x)
    for k in range(3):
        ang = rot + k * 2 * math.pi / 3
        b, e, hft = axe_head(x, y, ang, R, aa)
        heads = np.maximum(heads, b)
        edges = np.maximum(edges, e)
        hafts = np.maximum(hafts, hft)
        # 휩쓴 호: 날 뒤 (반시계 — 지나온 쪽) 로 0 ~ 110° · 날 쪽이 굵고 밝음 (초승달 띠)
        d = (th - ang) % (2 * math.pi)
        qd = np.clip(d / 1.9, 0, 1)
        width = 0.12 * (1 - qd) ** 0.8 + 0.008
        band = np.exp(-((r - (R - 0.06)) / np.maximum(width, 1e-3)) ** 2) * (d < 1.9)
        sweep = np.maximum(sweep, band * (1 - qd) ** 0.7)
        sweep_hi = np.maximum(sweep_hi, np.exp(-((r - (R - 0.0)) / np.maximum(width * 0.35, 1e-3)) ** 2) * (d < 1.2) * (1 - qd * 1.6))
    out = outline((heads + hafts > 0.5).astype(float), 2)
    layers = [(S.main(0.38), np.clip(sweep * 1.3, 0, 1) * 0.85),
              (S.main(0.72), np.clip(sweep * 2.0 - 0.8, 0, 1)),
              (S.main(0.92), np.clip(sweep_hi, 0, 1) * 0.8),
              (S.main(0.2), out * 0.9),
              (S.smoke(0.9), hafts),
              (S.main(0.58), heads),
              (S.main(0.97), edges)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.012, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 불꽃 꼬리 (jcomet2)

def comet_frame(S, f, h=96, w=256, frames=COMET_FRAMES):
    hh, ww = h * 2, w * 2
    x, y = gridhw(hh, ww)  # x −1 ~ 1 · y −0.375 ~ 0.375
    aa = 2.0 / ww
    ph = f / frames * 2 * math.pi
    hx = 0.64
    head = disk(x, y, hx, 0, 0.17, 0.17, aa)
    q = np.clip((hx - x) / (hx + 0.98), 0, 1)  # 0 머리 → 1 꼬리 끝
    half = 0.17 * (1 - q) ** 0.7 + 0.012
    wav = 0.035 * np.sin(q * 11 - ph * 2) * q
    tail = (1 - sstep(half * 0.85, half, np.abs(y - wav))) * (x < hx) * (x > -0.98)
    nz = Noise3(S.seed + 3)
    grain = nz.fbm(x * 5 + f * 0.9, y * 5, f * 0.5, oct=3)
    tail = tail * np.clip(1.15 - q * 0.6 + (grain - 0.5) * 0.6, 0, 1)
    tongues = np.zeros_like(x)
    for i in range(3):
        off = (i - 1) * 0.06
        pts = [(hx - 0.1 - 1.4 * s, off * (1 + s) + 0.05 * math.sin(s * 9 + ph * 2 + i * 2)) for s in np.linspace(0, 1, 18)]
        tongues = np.maximum(tongues, taper(x, y, pts, 0.04, 0.003, aa))
    temp = np.clip(1 - q * 1.1, 0, 1)
    core = disk(x, y, hx + 0.02, 0, 0.09, 0.09, aa)
    rim = outline((head > 0.5).astype(float), 3)
    rgb, a = compose([(S.main(0.3 + 0.55 * temp), np.clip(np.maximum(tail, tongues * 0.8), 0, 1)), (S.main(0.4), rim * 0.8),
                      (S.main(0.82), head), (S.main(0.95), disk(x, y, hx + 0.03, 0.02, 0.13, 0.13, aa) * 0.8), (WHITE, core)], (hh, ww))
    rgb, a = bloom(rgb, a, (hh, ww), thresh=0.7, sigma=0.015, gain=0.5)
    from PIL import Image
    im = to_img(rgb, a).resize((w, h), Image.LANCZOS)
    arr = np.asarray(im, float) / 255
    return arr[..., :3], arr[..., 3]


# ------------------------------------------------------------------ 방패 깨짐 (jbreak2)

def shield_sdf(x, y):
    """방패 (위 평평 · 아래 뾰족) — 안 −"""
    top = y - 0.42
    side = np.abs(x) - 0.36
    # 아래: 두 호가 만나는 뾰족 끝 (y −0.5)
    bottom = np.hypot(np.abs(x) + 0.36, y - 0.25) - 0.82
    return np.maximum(np.maximum(top, side), bottom)


def shieldbreak_frame(S, f, n=256, frames=BREAK_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    out = np.zeros_like(x)
    layers = []
    if f <= 1:
        d = shield_sdf(x, y)
        body = 1 - sstep(-aa, aa, d)
        rim = (1 - sstep(-aa, aa, d)) * sstep(-0.07, -0.05, d)
        boss = disk(x, y, 0, 0.02, 0.09, 0.09, aa)
        crack = np.zeros_like(x)
        if f == 1:
            pts = [(0.0, 0.42), (0.04, 0.25), (-0.03, 0.1), (0.03, -0.05), (-0.02, -0.2), (0.0, -0.45)]
            crack = taper(x, y, pts, 0.03, 0.012, aa)
        layers = [(S.main(0.25), outline((body > 0.5).astype(float), 3)), (S.main(0.55), body), (S.main(0.8), rim), (S.main(0.9), boss),
                  (WHITE, crack)]
        flash = np.exp(-(np.hypot(x, y) / 0.35) ** 2) * (0.5 if f == 1 else 0.0)
        layers.append((WHITE, flash))
    else:
        k = (f - 2) / (frames - 3)
        fade = 1.0 if f < frames - 1 else 0.45
        for side in (-1, 1):
            dx = side * (0.06 + 0.32 * k)
            dy = -0.05 - 0.35 * k * k
            rot = side * (0.12 + 0.5 * k)
            c, s = math.cos(rot), math.sin(rot)
            xs = (x - dx) * c + (y - dy) * s
            ys = -(x - dx) * s + (y - dy) * c
            d = shield_sdf(xs, ys)
            # 반쪽: 지그재그 금을 경계로
            zig = 0.03 * np.sign(np.sin(ys * 18))
            halfm = (side * (xs - zig) > 0).astype(float)
            body = (1 - sstep(-aa, aa, d)) * halfm
            rim = body * sstep(-0.07, -0.05, d)
            edge = body * (1 - sstep(0.0, 0.05, np.abs(xs - zig)))
            layers += [(S.main(0.25), outline((body > 0.5).astype(float), 3) * fade), (S.main(0.5), body * fade), (S.main(0.8), rim * fade),
                       (S.main(0.95), edge * fade)]
        rng = np.random.default_rng(S.seed + 29)
        bits = np.zeros_like(x)
        for i in range(9):
            a0 = rng.uniform(0, 2 * math.pi)
            rr = 0.1 + 0.55 * k * rng.uniform(0.7, 1.1)
            bits = np.maximum(bits, disk(x, y, rr * math.cos(a0), rr * math.sin(a0) - 0.1 * k, 0.025, 0.025, aa))
        layers.append((S.main(0.85), bits * fade))
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.015, gain=0.4)
    return rgb, a


# ------------------------------------------------------------------ 갈라지는 땅 (jquake — 땅에 누움)

def quake_frame(S, f, n=256, frames=QUAKE_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    rng = np.random.default_rng(S.seed + 61)
    k = f / (frames - 1)
    # 금: 가운데에서 뻗는 들쭉날쭉한 줄 아홉 (자람 0 ~ 4 장 · 달아오름 → 식음)
    grow = min(1.0, (f + 1) / 4.0)
    cracks = np.zeros_like(x)
    for i in range(9):
        a0 = i * 2 * math.pi / 9 + rng.uniform(-0.25, 0.25)
        L = rng.uniform(0.62, 0.9) * grow
        pts = [(0.0, 0.0)]
        a = a0
        for s in range(1, 7):
            a += rng.uniform(-0.35, 0.35)
            rr = L * s / 6
            pts.append((rr * math.cos(a), rr * math.sin(a)))
        cracks = np.maximum(cracks, taper(x, y, pts, 0.03, 0.006, aa))
        # 곁가지
        if rng.random() < 0.7:
            j = rng.integers(2, 5)
            bx, by = pts[j]
            b = a0 + rng.choice([-1, 1]) * rng.uniform(0.5, 0.9)
            pts2 = [(bx, by), (bx + 0.18 * grow * math.cos(b), by + 0.18 * grow * math.sin(b))]
            cracks = np.maximum(cracks, taper(x, y, pts2, 0.018, 0.004, aa))
    heat = 1.0 if f <= 5 else max(0.0, 1 - (f - 5) / 6)
    fade = 1.0 if f <= 8 else max(0.0, 1 - (f - 8) / 3.5)
    # 충격 고리 (1 ~ 5 장 — 0.92 까지)
    R = 0.15 + 0.77 * min(1.0, f / 5.0)
    ring = np.exp(-((r - R) / (0.03 + 0.03 * k)) ** 2) * (1 if 1 <= f <= 6 else 0) * max(0.0, 1 - f / 7)
    flash = np.exp(-(r / (0.18 + 0.1 * f)) ** 2) * max(0.0, 1 - f / 2.5)
    # 그을린 땅 (금 둘레의 짙은 자국)
    scorch = np.exp(-(r / 0.55) ** 2) * 0.45 * fade * min(1.0, max(0.0, (f - 1) / 2.0))  # 금이 난 뒤부터 (첫 장은 번쩍임만)
    # 튀는 돌조각 (2 ~ 6 장)
    rocks = np.zeros_like(x)
    if 1 <= f <= 7:
        for i in range(12):
            a0 = rng.uniform(0, 2 * math.pi)
            rr = (0.2 + 0.7 * k) * rng.uniform(0.6, 1.1)
            rocks = np.maximum(rocks, disk(x, y, rr * math.cos(a0), rr * math.sin(a0), 0.028, 0.022, aa))
    layers = [(S.smoke(0.35), scorch),
              (S.main(0.25), np.clip(cracks * 1.4, 0, 1) * fade),
              (S.main(0.45 + 0.45 * heat), cracks * fade * (0.6 + 0.4 * heat)),
              (S.main(0.75), ring),
              (S.accent(0.6), rocks),
              (WHITE, np.clip(flash, 0, 1))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.7, sigma=0.014, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, r))


# ------------------------------------------------------------------ 곰의 혼 (jbear)

def bear_head(x, y, jaw, aa):
    """포효하는 곰 머리 (앞을 봄 · 가운데 0, 0.1): 넓은 볼 (털 뭉치) · 작은 귀 · 짙은 눈썹 · 앞으로 나온 주둥이 · 크게 벌린 입 (jaw 0 ~ 1) · 큰 송곳니"""
    head = disk(x, y, 0, 0.2, 0.46, 0.4, aa)
    cheeks = disk(x, y, -0.36, 0.0, 0.2, 0.2, aa) + disk(x, y, 0.36, 0.0, 0.2, 0.2, aa)
    tufts = np.zeros_like(x)
    for sx in (-1, 1):
        for k in range(3):
            by = 0.1 - 0.12 * k
            tufts = np.maximum(tufts, tri(x, y, (sx * 0.42, by + 0.08), (sx * 0.42, by - 0.06), (sx * (0.66 + 0.04 * (k == 1)), by - 0.02), aa))
    ears = disk(x, y, -0.36, 0.54, 0.13, 0.12, aa) + disk(x, y, 0.36, 0.54, 0.13, 0.12, aa)
    inner_ears = disk(x, y, -0.36, 0.54, 0.065, 0.06, aa) + disk(x, y, 0.36, 0.54, 0.065, 0.06, aa)
    muzzle = disk(x, y, 0, -0.08 - 0.05 * jaw, 0.27, 0.22 + 0.1 * jaw, aa)
    mouth = disk(x, y, 0, -0.17 - 0.07 * jaw, 0.18 + 0.03 * jaw, 0.03 + 0.15 * jaw, aa)
    nose = tri(x, y, (-0.1, 0.08), (0.1, 0.08), (0.0, -0.01), aa)
    brow = np.zeros_like(x)
    for sx in (-1, 1):
        brow = np.maximum(brow, tri(x, y, (sx * 0.05, 0.22), (sx * 0.34, 0.33), (sx * 0.3, 0.22), aa))
    eyes = np.zeros_like(x)
    for sx in (-1, 1):
        eyes = np.maximum(eyes, tri(x, y, (sx * 0.1, 0.2), (sx * 0.28, 0.25), (sx * 0.24, 0.16), aa))
    fangs = np.zeros_like(x)
    if jaw > 0.2:
        top_y = -0.17 - 0.07 * jaw + 0.03 + 0.15 * jaw
        bot_y = -0.17 - 0.07 * jaw - 0.03 - 0.15 * jaw
        for sx in (-1, 1):
            fangs = np.maximum(fangs, tri(x, y, (sx * 0.14, top_y), (sx * 0.07, top_y), (sx * 0.11, top_y - 0.1 * jaw - 0.03), aa))
            fangs = np.maximum(fangs, tri(x, y, (sx * 0.12, bot_y), (sx * 0.06, bot_y), (sx * 0.09, bot_y + 0.08 * jaw + 0.02), aa))
    return (np.clip(head + cheeks + tufts + ears, 0, 1), np.clip(inner_ears, 0, 1), muzzle, mouth, nose, np.clip(brow, 0, 1), np.clip(eyes, 0, 1), fangs)


def bear_frame(S, f, n=256, frames=BEAR_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    nz = Noise3(S.seed + 17)
    if f <= 2:
        form = (f + 1) / 3.0  # 0.33 ~ 1
        jaw = 0.15 * f
    elif f <= 6:
        form = 1.0
        jaw = [0.7, 1.0, 0.85, 1.0][f - 3]
    else:
        form = 1.0
        jaw = 0.5
    head, ie, muzzle, mouth, nose, brow, eyes, fangs = bear_head(x, y - 0.02, jaw, aa)
    # 털끝 불길 (머리 둘레에서 위 · 옆으로 솟음 — 맥동)
    fur = np.zeros_like(x)
    for i in range(18):
        a0 = math.pi * (-0.15 + 1.3 * i / 17)
        cx, cy = 0.5 * math.cos(a0), 0.2 + 0.44 * math.sin(a0)
        L = 0.14 + 0.07 * math.sin(i * 1.7 + f * 1.3)
        dx, dy = math.cos(a0) * 0.45, math.sin(a0) * 0.6 + 0.5
        dl = math.hypot(dx, dy)
        pts = [(cx, cy), (cx + dx / dl * L * 0.6 + 0.02 * math.sin(f + i), cy + dy / dl * L * 0.6), (cx + dx / dl * L, cy + dy / dl * L + 0.03)]
        fur = np.maximum(fur, taper(x, y, pts, 0.055, 0.004, aa))
    # 목 털 (턱 아래 들쭉날쭉한 털 테 — 아래로 뻗음)
    collar = np.zeros_like(x)
    for i in range(11):
        cx = -0.5 + 1.0 * i / 10
        top_y = -0.18 - 0.28 * (1 - (cx / 0.55) ** 2)
        collar = np.maximum(collar, tri(x, y, (cx - 0.09, top_y + 0.1), (cx + 0.09, top_y + 0.1), (cx + 0.03 * math.sin(i * 2.1 + f), top_y - 0.16), aa))
    body = np.clip(head + muzzle + collar, 0, 1)
    grain = nz.fbm(x * 5, y * 5 + f * 0.4, f * 0.6, oct=3)
    if f <= 2:
        swirl = 0.5 + 0.5 * np.sin(th * 3 + r * 9 - f * 1.4)
        wisps = sstep(0.75, 0.95, swirl) * np.exp(-((r - (0.95 - 0.25 * f)) / 0.18) ** 2)
        vis = sstep(0.45 - 0.4 * form, 0.75 - 0.4 * form, grain + 0.25 * form)
        body, fur, eyes, muzzle, mouth, fangs = body * vis, fur * vis, eyes * form, muzzle * vis, mouth * vis, fangs * vis
    else:
        wisps = np.zeros_like(x)
    if f == 7:
        vis = sstep(0.55, 0.62, grain + 0.1)
        body, fur, mouth, fangs, muzzle = body * vis * 0.85, fur * 0.5, mouth * vis, fangs * vis, muzzle * vis * 0.85
    shade = np.clip(0.45 + 0.6 * (y + 0.25), 0, 1)
    out = outline((body > 0.5).astype(float), 3)
    layers = [(S.main(0.55), np.clip(wisps, 0, 1)),
              (S.main(0.62), np.clip(fur * 1.3, 0, 1)),
              (S.main(0.18), out * 0.9),
              (S.main(0.38 + 0.32 * shade), body),
              (S.main(0.28), ie * body),
              (S.main(0.6), muzzle),
              (S.main(0.16), brow * body),
              (S.main(0.08), mouth),
              (S.main(0.12), nose * body),
              (S.accent(0.97), fangs),
              (WHITE, eyes)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.015, gain=0.5)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 곰 앞발 (jpaw)

def paw_frame(S, f, n=256, frames=PAW_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    ph = f / frames * 2 * math.pi
    # 털 덮인 앞발 (위 = 팔뚝 · 아래 = 발가락 · 발톱이 아래로)
    arm = taper(x, y, [(0.0, 0.62), (0.0, 0.25)], 0.3, 0.42, aa)
    palm = disk(x, y, 0, 0.08, 0.48, 0.34, aa)
    rough = np.zeros_like(x)
    for sx in (-1, 1):
        for k in range(4):
            by = 0.52 - 0.17 * k
            rough = np.maximum(rough, tri(x, y, (sx * (0.3 + 0.04 * k), by + 0.07), (sx * (0.32 + 0.04 * k), by - 0.07), (sx * (0.47 + 0.04 * k), by - 0.03), aa))
    for i in range(6):
        cx = -0.25 + 0.1 * i
        rough = np.maximum(rough, tri(x, y, (cx - 0.06, 0.6), (cx + 0.06, 0.6), (cx + 0.01, 0.74), aa))
    toes = np.zeros_like(x)
    claws = np.zeros_like(x)
    xs = (-0.36, -0.12, 0.12, 0.36)
    for i, sx in enumerate(xs):
        ty = -0.24 - (0.05 if i in (1, 2) else 0.0)
        toes = np.maximum(toes, disk(x, y, sx, ty, 0.13, 0.13, aa))
        pts = [(sx, ty - 0.08), (sx * 1.08, ty - 0.32), (sx * 1.0 - 0.05 * (1 if sx > 0 else -1), ty - 0.6)]
        claws = np.maximum(claws, taper(x, y, pts, 0.05, 0.004, aa, p=1.2))
    beans = np.zeros_like(x)
    for i, sx in enumerate(xs):
        ty = -0.24 - (0.05 if i in (1, 2) else 0.0)
        beans = np.maximum(beans, disk(x, y, sx, ty - 0.01, 0.07, 0.07, aa))
    pad = disk(x, y, 0, 0.04, 0.26, 0.17, aa)
    body = np.clip(arm + palm + rough + toes, 0, 1)
    # 바람 줄 (위로 끌림 — 아래로 내리치는 앞발)
    wind = np.zeros_like(x)
    for i in range(8):
        sx = -0.42 + 0.12 * i
        L = 0.3 + 0.12 * math.sin(ph + i * 1.3)
        pts = [(sx, 0.55 + 0.05 * (i % 2)), (sx + 0.02 * math.sin(i + ph), 0.55 + L)]
        wind = np.maximum(wind, taper(x, y, pts, 0.03, 0.004, aa) * (0.6 + 0.4 * math.sin(ph * 2 + i)) * (abs(sx) > 0.26))
    out = outline((np.clip(body + claws, 0, 1) > 0.5).astype(float), 3)
    shade = np.clip(0.5 + 0.6 * (y + 0.1), 0, 1)
    layers = [(S.main(0.55), np.clip(wind, 0, 1) * 0.85),
              (S.main(0.18), out * 0.9),
              (S.main(0.4 + 0.32 * shade), body),
              (S.main(0.72), np.clip(pad + beans, 0, 1) * body),
              (S.accent(0.95), claws)]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.72, sigma=0.015, gain=0.45)
    return rgb, a * (1 - sstep(0.95, 0.995, np.maximum(np.abs(x), np.abs(y))))


# ------------------------------------------------------------------ 돌창 무리 (jspike — 아래 끝 = 땅)

SPIKES = [(-0.52, 0.85, 0.15), (-0.2, 1.35, 0.2), (0.12, 1.7, 0.23), (0.42, 1.2, 0.18), (0.66, 0.75, 0.13)]


def spike_frame(S, f, n=256, frames=SPIKE_FRAMES):
    x, y = gridhw(n, n)
    aa = 2.0 / n
    g = -0.98  # 땅
    if f <= 2:
        rise = [0.25, 0.65, 1.0][f]
        crumble = 0.0
    elif f <= 5:
        rise, crumble = 1.0, 0.0
    else:
        rise, crumble = 1.0, [0.45, 0.85][f - 6]
    rocks = np.zeros_like(x)
    lit = np.zeros_like(x)
    seams = np.zeros_like(x)
    rng = np.random.default_rng(S.seed + 83)
    for i, (cx, hgt, wb) in enumerate(SPIKES):
        H_ = hgt * rise * (1 - 0.75 * crumble * rng.uniform(0.8, 1.2))
        tipx = cx + rng.uniform(-0.06, 0.06)
        left = (cx - wb, g)
        right = (cx + wb, g)
        top = (tipx, g + H_)
        sp = tri(x, y, left, right, top, aa)
        # 왼쪽 면 (그늘) · 오른쪽 면 (빛)
        face = sstep(-0.01, 0.01, x - (cx + (tipx - cx) * np.clip((y - g) / max(H_, 1e-3), 0, 1)))
        rocks = np.maximum(rocks, sp)
        lit = np.maximum(lit, sp * face)
        # 갈라진 틈 (빛): 창 가운데를 지그재그로
        pts = [(cx + 0.02, g + 0.05)]
        for s in range(1, 5):
            pts.append((cx + (tipx - cx) * s / 5 + rng.uniform(-0.04, 0.04), g + H_ * s / 5.5))
        seams = np.maximum(seams, taper(x, y, pts, 0.022, 0.006, aa) * sp)
    pulse = 1.0 if f < 3 else (0.75 + 0.25 * math.sin((f - 3) / 3 * 2 * math.pi) if f <= 5 else 0.5)
    dirt = np.zeros_like(x)
    if f <= 2 or f >= 6:
        k = 0.4 if f <= 2 else 0.8
        for i in range(10):
            dx = rng.uniform(-0.85, 0.85)
            dy = g + rng.uniform(0.02, 0.35) * (1.2 if f <= 2 else 0.6)
            dirt = np.maximum(dirt, disk(x, y, dx, dy, 0.035, 0.03, aa) * k)
    out = outline((rocks > 0.5).astype(float), 3)
    layers = [(S.smoke(0.5), dirt),
              (S.accent(0.25), out * 0.95),
              (S.accent(0.45), rocks),
              (S.accent(0.65), lit),
              (S.main(0.55 + 0.4 * pulse), np.clip(seams * 1.4, 0, 1) * (1 - 0.6 * crumble))]
    rgb, a = compose(layers, n)
    rgb, a = bloom(rgb, a, n, thresh=0.75, sigma=0.012, gain=0.4)
    return rgb, a * (1 - sstep(0.95, 0.995, np.abs(x)))


# ------------------------------------------------------------------ 몸의 불꽃 (jaura2 — 세움, 128 × 256 · 8 되풀이)

AURA_FRAMES = 8


def aura_frame(S, f, h=256, w=128, frames=AURA_FRAMES):
    """몸 둘레에서 솟는 불길 (가운데 몸 자리는 비움 · 양옆 혀가 머리 위까지) — x −1 ~ 1 = 몸 반 너비 0.3 × 높이 · y −2 ~ 2"""
    from .soul import flame_sdf
    ky = h / w
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = ((np.arange(h) + 0.5) / h * 2 - 1) * ky
    x, y = np.meshgrid(xs, -ys)
    aa = 2.0 / w
    ph = f / frames * 2 * math.pi
    body = np.zeros_like(x)
    hot = np.zeros_like(x)
    rng = np.random.default_rng(S.seed + 97)
    for i in range(7):
        side = -1 if i % 2 == 0 else 1
        cx = side * rng.uniform(0.38, 0.72)
        base = -1.95 + rng.uniform(0.0, 0.5)
        tip = base + rng.uniform(1.6, 2.8) * (0.85 + 0.15 * math.sin(ph + i * 1.9))
        R = rng.uniform(0.18, 0.28)
        d = flame_sdf(x - cx, y, base, R, min(tip, 1.9), ph + i * 0.8, sway=0.1)
        body = np.maximum(body, 1 - sstep(-aa, aa, d))
        hot = np.maximum(hot, np.clip(-d / 0.3, 0, 1))
    glow = np.exp(-((x / 0.75) ** 2 + ((y + 1.75) / 0.5) ** 2)) * 0.55
    embers = np.zeros_like(x)
    period = 2 * ky
    for i in range(10):
        sx = rng.uniform(-0.85, 0.85)
        y0 = rng.uniform(0, period)
        sy = (y0 + f / frames * period) % period - ky
        life = 1 - abs((sy + ky) / period * 2 - 1)
        embers = np.maximum(embers, disk(x, y, sx, sy, 0.05, 0.05, aa) * life)
    layers = [(S.main(0.5), glow), (S.main(0.4), body * 0.9), (S.main(0.6 + 0.28 * hot), body * np.clip(hot * 1.6, 0, 1)),
              (S.main(0.94), np.clip(hot * 2.4 - 1.6, 0, 1) * 0.8), (S.accent(0.92), embers)]
    rgb, a = compose(layers, (h, w))
    rgb, a = bloom(rgb, a, (h, w), thresh=0.72, sigma=0.015, gain=0.4)
    return rgb, a * (1 - sstep(0.94, 0.995, np.abs(x)))


def dash_frame(S, f):
    """돌진 줄기 (jdash2 — arcane.dash_frame 을 이 묶음 빛으로: 가는 쪽으로 누운 빠른 줄 · 6장)"""
    from .arcane import dash_frame as _dash
    return _dash(S, f)


# ------------------------------------------------------------------ 묶음

FRAMES = {
    "wolf": (WOLF_FRAMES, wolf_frame),
    "twister": (TWISTER_FRAMES, twister_frame),
    "whirl": (WHIRL_FRAMES, whirl_frame),
    "comet": (COMET_FRAMES, comet_frame),
    "shieldbreak": (BREAK_FRAMES, shieldbreak_frame),
    "quake": (QUAKE_FRAMES, quake_frame),
    "bear": (BEAR_FRAMES, bear_frame),
    "paw": (PAW_FRAMES, paw_frame),
    "spike": (SPIKE_FRAMES, spike_frame),
    "aura": (AURA_FRAMES, aura_frame),
    "dash": (6, dash_frame),
}

# 직업 묶음마다 쓰는 그림 (글꼴 무게 — 쓰지 않는 그림은 넣지 않음)
USE = {
    "rage": ("wolf", "twister", "whirl", "comet", "shieldbreak", "quake", "aura", "dash"),
    "bear": ("bear", "paw", "quake", "aura", "dash", "spike"),  # 워로드 돌창은 3D (RuneProps.rockSpike — 사용자 "곰과 땅 연출을 3d로") · spike 는 엘리멘탈 블레이드의 바위 (곰 묶음을 빌림)
}


def frames_of(name, S):
    n, fn = FRAMES[name]
    return [to_img(*fn(S, f)) for f in range(n)]


def sprites(S):
    """그림판 목록 [(이름, 만들기, colored)] — element.sets_for 가 더함 (S.lib + '_' 접두)"""
    p = S.lib + "_"
    cache = {}

    def fr(name):
        def mk():
            if name not in cache:
                cache[name] = frames_of(name, S)
            return cache[name]
        return mk
    return [(p + k, fr(k), True) for k in USE.get(S.kind, ())]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        kind = sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "rage"
        S = style(kind)
        names = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(USE.get(kind, FRAMES))
        preview({k: frames_of(k, S) for k in names}, out)
