#!/usr/bin/env python3
"""2026-11-14 기본 평타 참격 판 그림 (사용자 "근접 애들은 참격 나가게" · "참격 좀 큼직막하게" · "광역 애들은 광역 답게 넓게 참격 좀 두껍게 잘 보이게" ·
"날가롭게 얅은데 참격 앞을 보고 있으면 뒤에 참격 잔상이 두껍게 사진 같은 요런 느낌, 그렇다고 도트로 하지말고").

2판 (같은 날 — 사용자 "지금 퀄이 너무 낮아서 퀄을 좀 많이 높게" · 연출 방식 "고해상도 미리 렌더링" · 그림체 "섞음" (2D 는 고해상도 부드러운 그림, 3D 는 블록 픽셀) ·
첫 시범 "평타 (근접 무기 9종)"): 판마다 겹을 늘림 — 몸 (밝기 · 알파: 빛깔 TINT = 무기 색) 에 결 (휘두름 쪽으로 흐르는 가는 줄무늬) · 바깥 빛 무리 ·
꼬리부터 흩어지는 사라짐, 날 (알파: 속빛 GLOW = 흰빛에 가까운 무기 색) 에 바깥 날 · 안쪽 두 번째 날 · 칼끝 섬광 · 튀는 불티.

판 넷 (KINDS — 판 단위 = 칸, 그림 위 +Y · 오른쪽 +X):
  arc    몸 둘레 초승달 띠 (가운데 = 몸 가운데 · 바깥 반지름 1 — Fx3d scale = 칸 반지름): 오른쪽 뒤 (THETA_S) 에서 앞 (+Y) 을 지나 왼쪽 뒤 (THETA_E) 로 휩씀
         — 머리 (칼끝) 는 가늘고 날카롭게, 뒤로 두꺼워지는 잔상. mirror = 왼쪽 → 오른쪽, thick = 광역 무기의 두꺼운 띠. 세로 · X자 베기도 이 그림 (Fx3d 로 세움)
  thrust 창 찌르기 (원점 = 손 · +Y = 찌르는 쪽 · 길이 1 — scale = 찌르는 거리): 창끝이 뻗어 나가는 날카로운 빛줄기 + 두꺼운 잔상 · 창끝 앞 공기 고리
  bash   방패 밀치기 (원점 = 몸 · +Y = 앞 · 반지름 1): 앞 부채꼴 (±62°) 로 퍼지는 충격 띠 + 앞으로 뻗는 충격선
  spark  맞은 자리 섬광 (가운데 원점 · 반지름 1): 빛살 여덟 · 가운데 섬광 · 퍼지는 고리 (판이 보는 사람을 봄)
칸: arc · thrust · bash 10장 (0 ~ SWEEP-1 휘두름 · 나머지 거둬짐 — 판의 움직임 play: 휘두름 = 삶의 0.4) · spark 5장.

  python3 tools/basic_slash_art.py --preview 폴더   # 판마다 무기 색을 입혀 어두운 · 풀밭 바탕에 그림 (확인용)
"""
import math
import os
import sys

import numpy as np

# ------------------------------------------------------------ arc (예전 이름 그대로 — fx_models_card._basic_slash 가 씀)
W, H = 256, 192  # 칸 픽셀 (아틀라스 칸 16 × 12 · 모드 없는 클라이언트 글자 한 장 — 글꼴 판 256 안)
XL, XR = -1.04, 1.04  # 판 너비 (모델 칸 — 반지름 1 + 날 빛 여유)
YB, YT = -0.52, 1.04  # 판 높이 (가로세로비 = W / H)
THETA_S, THETA_E = -28.0, 208.0  # 휘두름 시작 · 끝 (도 — +X 에서 앞 (+Y) 쪽으로)
FRAMES = 10
SWEEP = 4  # 휘두름 칸 수 (u 0 ~ 0.4)

W_THICK = 0.7  # 광역 무기 (마검 · 낫 · 전투 망치 · 창 — slash_basic_w) 의 가장 두꺼운 폭
W_MAX = 0.5  # 잔상 띠의 가장 두꺼운 폭 (반지름 몫)
W_HEAD = 0.006  # 머리 (칼끝) 의 폭
PHI_W = 34.0  # 머리 뒤 이 각도 만큼에서 폭이 63% 까지 (도)
RIM = 0.016  # 날 (흰 줄) 굵기
RIM_HEAD = 0.036  # 머리 가까이 날 굵기
PHI_RIM = 95.0  # 날이 옅어지는 각도 (도)

KINDS = {
    "arc": {"W": W, "H": H, "XL": XL, "XR": XR, "YB": YB, "YT": YT, "frames": FRAMES},
    "thrust": {"W": 96, "H": 256, "XL": -0.2025, "XR": 0.2025, "YB": -0.04, "YT": 1.04, "frames": FRAMES},
    "bash": {"W": 256, "H": 160, "XL": -0.96, "XR": 0.96, "YB": -0.1, "YT": 1.1, "frames": FRAMES},
    "spark": {"W": 128, "H": 128, "XL": -1.0, "XR": 1.0, "YB": -1.0, "YT": 1.0, "frames": 5},
}


def _ss(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def _grid(kind):
    k = KINDS[kind]
    xs = k["XL"] + (np.arange(k["W"]) + 0.5) / k["W"] * (k["XR"] - k["XL"])
    ys = k["YT"] - (np.arange(k["H"]) + 0.5) / k["H"] * (k["YT"] - k["YB"])
    x, y = np.meshgrid(xs, ys)
    return x, y, (k["XR"] - k["XL"]) / k["W"]


_NOISE = {}


def _noise(u, v, seed, octaves=3):
    """값 잡음 (0 ~ 1, 부드러운 이음 — 같은 씨앗이면 같은 그림)"""
    out = np.zeros_like(u, dtype=float)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        key = (seed, o)
        if key not in _NOISE:
            _NOISE[key] = np.random.RandomState(seed * 131 + o).rand(64, 64)
        g = _NOISE[key]
        uu, vv = u * (2 ** o), v * (2 ** o)
        i0, j0 = np.floor(uu).astype(int), np.floor(vv).astype(int)
        fu, fv = uu - i0, vv - j0
        fu, fv = fu * fu * (3 - 2 * fu), fv * fv * (3 - 2 * fv)
        i0, j0 = i0 % 64, j0 % 64
        i1, j1 = (i0 + 1) % 64, (j0 + 1) % 64
        n = g[i0, j0] * (1 - fu) * (1 - fv) + g[i1, j0] * fu * (1 - fv) + g[i0, j1] * (1 - fu) * fv + g[i1, j1] * fu * fv
        out += amp * n
        tot += amp
        amp *= 0.5
    return out / tot


def _segments(x, y, segs, px):
    """가는 줄 (x0, y0, x1, y1, 굵기, 알파) 들의 알파 (가장자리 반 픽셀 안티에일리어스)"""
    a = np.zeros_like(x)
    for x0, y0, x1, y1, t, al in segs:
        if al <= 0:
            continue
        dx, dy = x1 - x0, y1 - y0
        ll = max(1e-9, dx * dx + dy * dy)
        s = np.clip(((x - x0) * dx + (y - y0) * dy) / ll, 0, 1)
        d = np.hypot(x - (x0 + s * dx), y - (y0 + s * dy))
        a = np.maximum(a, al * (1 - _ss(t - 0.5 * px, t + 0.5 * px, d)))
    return a


def _merge(body, lum, halo, halo_lum=1.0):
    """몸 위에 빛 무리 (몸 밖 — 같은 겹): 알파는 겹침, 밝기는 알파로 섞음"""
    a = body + halo * (1 - body)
    l = (lum * body + halo_lum * halo * (1 - body)) / np.maximum(a, 1e-6)
    return np.clip(l, 0, 1), np.clip(a, 0, 1)


# ------------------------------------------------------------ arc


def frame_params(k):
    """칸 k → (머리 각, 꼬리 각, 폭 배율, 어둡게, 전체 알파, 날 알파, 흩어짐)"""
    span = THETA_E - THETA_S
    if k < SWEEP:
        p = (k + 1) / SWEEP
        p = 1 - (1 - p) ** 1.35  # 끝으로 갈수록 느려짐 (휘두름이 멈춤)
        return THETA_S + span * p, THETA_S, 1.0, 0.0, 1.0, 1.0, 0.0
    q = (k - SWEEP + 1) / (FRAMES - SWEEP)  # 1/6 ~ 1
    head = THETA_E + 7.0 * q
    tail = THETA_S + span * 0.9 * q ** 1.15
    return head, tail, 1.0 + 0.28 * q, 0.25 * q, (1 - q) ** 0.6 if q < 1 else 0.05, (1 - q) ** 1.5, q


def _arc_sparks(k, x, y, px):
    """휘두를 때 칼끝에서 튀는 불티 (머리가 지나간 자리 바로 바깥에서 휘두름 쪽으로 날아가는 가는 빛줄 — 3칸 동안)"""
    rng = np.random.RandomState(4242)
    span = THETA_E - THETA_S
    segs = []
    for i in range(20):
        b = int(rng.randint(0, SWEEP))
        p0 = 1 - (1 - (b + 0.3 + rng.rand() * 0.7) / SWEEP) ** 1.35
        th0 = THETA_S + span * min(1.0, p0) - rng.rand() * 10
        r0 = 1.02 + rng.rand() * 0.05
        vr = 0.02 + rng.rand() * 0.035
        vt = 6.0 + rng.rand() * 8.0
        ln = 0.06 + rng.rand() * 0.08
        age = k - b
        if age < 0 or age > 3:
            continue
        r = r0 + vr * age
        th = math.radians(th0 + vt * age)
        cx, cy = r * math.cos(th), r * math.sin(th)
        tx, ty = -math.sin(th), math.cos(th)  # 휘두름 쪽 (반시계) — 거의 둘레를 따라
        ox, oy = math.cos(th), math.sin(th)
        dx, dy = tx + ox * 0.25, ty + oy * 0.25
        dl = math.hypot(dx, dy)
        dx, dy = dx / dl, dy / dl
        segs.append((cx - dx * ln, cy - dy * ln, cx, cy, 0.0045, (1 - age / 4.0) ** 1.3))
    return _segments(x, y, segs, px)


def render(k, thick=False):
    """arc 칸 k 의 (몸 밝기, 몸 알파, 날 알파) — H × W. thick = 광역 무기의 두꺼운 띠.
    2판: 몸은 거의 불투명하고 가장자리만 짧게 번짐 (섞는 빛에서 반투명 넓은 번짐은 풀밭 · 하늘 위에서 흙빛이 됨), 밝기는 바깥 날 1.0 → 안쪽 0.6,
    결 (휘두름 쪽 줄무늬) · 꼬리는 결을 따라 가닥으로 흩어짐, 날 = 굵은 흰 날 (머리 가까이) · 안쪽 두 줄 · 칼끝 섬광 · 불티"""
    x, y, px = _grid("arc")
    r = np.hypot(x, y)
    th = np.degrees(np.arctan2(y, x))
    th = np.where(th < -90.0, th + 360.0, th)  # [-90, 270) — 띠 (−28 ~ 208) 가 끊기지 않게
    head, tail, kw, dark, ga, ra, dis = frame_params(k)
    w_max = W_THICK if thick else W_MAX
    L = max(1e-3, head - tail)
    phi = head - th  # 머리 뒤 각도
    inside = ((phi >= -1.0) & (th >= tail - 2.0)).astype(float)
    phic = np.clip(phi, 0.0, None)
    w = (W_HEAD + (w_max - W_HEAD) * (1 - np.exp(-phic / PHI_W))) * kw
    r_out = 1.0 - 0.035 * np.clip(phic / 240.0, 0, 1) * kw
    d = r_out - r  # 바깥 가장자리에서 안쪽으로
    rho = d / np.maximum(w, 1e-4)  # 0 = 바깥 날 · 1 = 안쪽
    rc = np.clip(rho, 0, 1.2)
    a_out = _ss(-0.5 * px, 0.5 * px, d)
    a_in = 1 - _ss(0.8, 1.0, rho)
    a_head = _ss(-0.6, 1.2, phi)
    a_tail = (1 - _ss(0.55 * L, 1.0 * L, phic)) * _ss(tail - 2.0, tail + 3.0, th)
    along = _ss(0.0, 1.0, phic / max(L, 70.0))
    # 결: 휘두름 쪽으로 흐르는 가는 줄무늬 (쪽을 따라 느리게 · 가로질러 빠르게 바뀌는 잡음)
    streak = 0.6 * _noise(phic / 38.0, rc * 9.0, 11) + 0.4 * _noise(phic / 22.0 + 3.1, rc * 17.0, 23)
    fine = _noise(phic / 30.0 + 9.0, rc * 24.0, 29)
    # 꼬리 쪽은 결을 따라 가닥으로 갈라짐
    strands = _ss(0.25, 0.55, fine + 0.6 * (1 - along))
    body = a_out * a_in * a_head * a_tail * ga * inside * 0.95 * (0.86 + 0.14 * _ss(0.3, 0.7, streak)) * strands
    halo = 0.26 * np.exp(-np.clip(-d, 0, None) / 0.024) * (d < 0) * np.exp(-phic / 50.0) * a_head * _ss(tail, tail + 6.0, th) * ga * inside
    if dis > 0:  # 거둬질 때 꼬리부터 결을 따라 가닥으로 흩어짐
        keep = 0.6 * fine + 0.4 * (1 - along)
        t = dis * 1.1
        m = _ss(t - 0.05, t + 0.05, keep)
        body, halo = body * m, halo * m
    # 밝기: 바깥 날 1.0 → 안쪽 0.6, 꼬리 쪽 조금 짙게 · 결
    lum = (0.6 + 0.4 * (1 - np.clip(rho, 0, 1)) ** 1.4) * (1 - 0.22 * along) * (1 - 0.5 * dark) * (0.9 + 0.2 * (streak - 0.5))
    lum = np.clip(lum + 0.2 * np.exp(-phic / 12.0) * (1 - np.clip(rho, 0, 1)), 0.5, 1.0)
    lum, body = _merge(body, lum, halo)
    # 날: 바깥 가장자리를 따라 — 머리 가까이 굵고 (띠 폭의 몫) 밝음, 뒤로 가늘고 옅어짐
    t_rim = RIM + (w * 0.22) * np.exp(-phic / 26.0) + (RIM_HEAD - RIM) * np.exp(-phic / 12.0)
    rim = _ss(-0.5 * px, 0.5 * px, d) * (1 - _ss(t_rim - 0.6 * px, t_rim + 0.6 * px, d))
    rim = rim * np.clip(np.exp(-phic / PHI_RIM) * 1.15, 0, 1) * a_head * _ss(tail, tail + 6.0, th) * ra * inside
    # 안쪽 두 줄 (잔상 안의 가는 빛줄 — 여러 겹 날)
    echo = np.zeros_like(x)
    for fr, al, fall in ((0.36, 0.55, 48.0), (0.66, 0.35, 36.0)):
        de = d - w * fr
        echo = np.maximum(echo, (1 - _ss(0.4 * px, 1.3 * px, np.abs(de))) * al * np.exp(-phic / fall) * (phic > 4))
    echo = echo * a_head * ra * inside * a_tail
    # 칼끝 섬광 (날 안쪽으로 조금 더 넓게 번쩍)
    flare = np.exp(-(phic / 5.0) ** 2) * np.exp(-(d / 0.06) ** 2) * _ss(-1.5 * px, 0.5 * px, d) * a_head * ra * inside
    core = np.clip(rim + echo + 0.9 * flare + _arc_sparks(k, x, y, px), 0, 1)
    return lum, np.clip(body, 0, 1), core


def frames(mirror=False, thick=False):
    out = []
    for k in range(FRAMES):
        lum, a, c = render(k, thick)
        if mirror:
            lum, a, c = lum[:, ::-1], a[:, ::-1], c[:, ::-1]
        out.append((lum, a, c))
    return out


# ------------------------------------------------------------ thrust

T_HEAD = 0.003  # 창끝 폭 (반 폭 — 길이 몫)
T_MAX = 0.11  # 잔상의 가장 두꺼운 반 폭 (창 찌르기 범위 굵기 1.2 칸 / 4 칸 ≈ 0.15)


def thrust_params(k):
    """칸 k → (창끝 y, 꼬리 y, 폭 배율, 어둡게, 전체 알파, 날 알파, 흩어짐)"""
    if k < SWEEP:
        return (0.34, 0.66, 0.88, 1.0)[k], 0.0, 1.0, 0.0, 1.0, 1.0, 0.0
    q = (k - SWEEP + 1) / (FRAMES - SWEEP)
    return 1.0 + 0.02 * q, 0.85 * q ** 1.1, 1.0 + 0.25 * q, 0.25 * q, (1 - q) ** 0.6 if q < 1 else 0.05, (1 - q) ** 1.5, q


def thrust(k):
    """창 찌르기: 바늘처럼 가는 창끝에서 뒤로 길게 두꺼워지는 빛줄기 (가장자리는 짧게 번짐) · 가운데 흰 줄 · 감아 도는 바람 두 줄 · 창끝 앞 공기 고리 · 옆 불티"""
    x, y, px = _grid("thrust")
    yh, yt, kw, dark, ga, ra, dis = thrust_params(k)
    d = yh - y  # 창끝 뒤
    dc = np.clip(d, 0, None)
    L = max(1e-3, yh - yt)
    tail_t = _ss(yt, yt + 0.3 * L + 0.02, y)  # 꼬리 끝 0 → 1
    w = (T_HEAD + (T_MAX - T_HEAD) * (1 - np.exp(-dc / 0.3))) * kw * (0.3 + 0.7 * tail_t)
    ax = np.abs(x)
    rho = ax / np.maximum(w, 1e-4)
    a_side = 1 - _ss(0.72, 1.0, rho)
    a_head = _ss(-0.004, 0.012, d)
    a_tail = tail_t * _ss(yt - 0.01, yt + 0.03, y)
    along = np.clip(dc / L, 0, 1)
    streak = 0.6 * _noise(y * 2.4, x * 46.0 + 5.0, 5) + 0.4 * _noise(y * 4.0 + 2.0, x * 80.0, 6)
    fine = _noise(y * 3.0 + 4.0, x * 120.0, 31)
    strands = _ss(0.25, 0.55, fine + 0.6 * (1 - along))
    body = a_side * a_head * a_tail * ga * 0.95 * (0.86 + 0.14 * _ss(0.3, 0.7, streak)) * strands
    halo = 0.24 * np.exp(-np.clip(ax - w, 0, None) / 0.016) * (rho > 1) * np.exp(-dc / 0.25) * a_head * a_tail * ga
    if dis > 0:
        keep = 0.6 * fine + 0.4 * (1 - along)
        t = dis * 1.1
        m = _ss(t - 0.05, t + 0.05, keep)
        body, halo = body * m, halo * m
    lum = (0.62 + 0.38 * (1 - np.clip(rho, 0, 1)) ** 1.2) * (1 - 0.22 * along) * (1 - 0.5 * dark) * (0.9 + 0.2 * (streak - 0.5))
    lum = np.clip(lum + 0.2 * np.exp(-dc / 0.05), 0.5, 1.0)
    lum, body = _merge(body, lum, halo)
    # 날: 가운데 흰 줄 (창끝 가까이 굵고 밝음) · 감아 도는 바람 두 줄 (칸마다 돎) · 창끝 앞 날카로운 끝 · 공기 고리 · 옆으로 튀는 불티
    t_c = 0.003 + 0.012 * np.exp(-dc / 0.1)
    line = (1 - _ss(t_c - 0.6 * px, t_c + 0.6 * px, ax)) * np.exp(-dc / 0.4) * a_head * a_tail * ra
    helix = np.zeros_like(x)
    for side in (0.0, math.pi):
        hx = w * 0.62 * np.sin(2 * math.pi * (y * 5.0 - k * 0.22) + side)
        helix = np.maximum(helix, (1 - _ss(0.4 * px, 1.4 * px, np.abs(x - hx))) * 0.6 * np.exp(-dc / 0.45) * a_head * a_tail * ra * (dc > 0.02))
    s = (y - yh) / 0.075
    tipw = 0.022 * np.clip(1 - s, 0, 1)
    tip = (1 - _ss(tipw - 0.5 * px, tipw + 0.5 * px, ax)) * (s >= -0.1) * (s <= 1.0) * ra
    ring = np.zeros_like(x)
    if 1 <= k <= 6:
        sr = (k - 1) / 5.0
        rx = 0.07 + 0.11 * sr
        ry = rx * 0.28
        cy = yh - 0.035 - 0.05 * sr
        e = np.hypot(x / rx, (y - cy) / ry)
        ring = (1 - _ss(0.5 * px / rx, 1.6 * px / rx, np.abs(e - 1))) * 0.9 * (1 - sr) ** 1.2
    rng = np.random.RandomState(777)
    segs = []
    for i in range(12):
        b = int(rng.randint(1, SWEEP + 1))
        age = k - b
        if age < 0 or age > 3:
            continue
        side = 1 if i % 2 else -1
        y0 = (0.66, 0.88, 1.0, 1.0)[min(3, b - 1)] - 0.04 - rng.rand() * 0.12
        cx = side * (0.03 + 0.03 * age + rng.rand() * 0.02)
        cy = y0 - 0.06 * age
        ln = 0.06 + rng.rand() * 0.06
        segs.append((cx, cy + ln / 2, cx + side * 0.01, cy - ln / 2, 0.0032, (1 - age / 4.0) ** 1.3))
    core = np.clip(line + helix + tip + ring + _segments(x, y, segs, px), 0, 1)
    return lum, np.clip(body, 0, 1), core


# ------------------------------------------------------------ bash

def bash_params(k):
    """칸 k → (앞 반지름, 폭 배율, 어둡게, 전체 알파, 날 알파, 흩어짐)"""
    if k < SWEEP:
        return (0.38, 0.66, 0.87, 1.0)[k], 1.0, 0.0, 1.0, 1.0, 0.0
    q = (k - SWEEP + 1) / (FRAMES - SWEEP)
    return 1.0 + 0.06 * q, 1.0 + 0.35 * q, 0.25 * q, (1 - q) ** 0.6 if q < 1 else 0.05, (1 - q) ** 1.6, q


BASH_SPAN = 62.0


def bash(k):
    """방패 밀치기: 앞 부채꼴로 퍼지는 두꺼운 충격 띠 (앞 가장자리 또렷 · 거의 불투명) · 앞 날 · 뒤따르는 두 줄 · 앞으로 튀는 작은 조각"""
    x, y, px = _grid("bash")
    R, kw, dark, ga, ra, dis = bash_params(k)
    r = np.hypot(x, y)
    ang = np.degrees(np.arctan2(x, y))  # 0 = 앞 (+Y)
    aa = np.abs(ang)
    a_ang = 1 - _ss(BASH_SPAN - 14, BASH_SPAN, aa)
    cosw = np.cos(np.radians(np.clip(aa, 0, BASH_SPAN)) * 90.0 / BASH_SPAN)
    t = (0.1 + 0.22 * R) * kw * (0.65 + 0.35 * cosw)
    d = R - r
    rho = d / np.maximum(t, 1e-4)
    a_out = _ss(-0.5 * px, 0.5 * px, d)
    a_in = 1 - _ss(0.75, 1.0, rho)
    streak = _noise(ang / 2.6 + 50.0, r * 3.0, 9)
    fine = _noise(ang / 1.6 + 70.0, r * 2.0, 13)
    body = a_out * a_in * a_ang * ga * 0.95 * (0.85 + 0.15 * _ss(0.3, 0.7, streak)) * _ss(0.2, 0.5, fine + 0.6 * (1 - rho)) * (r > 0.05)
    halo = 0.26 * np.exp(-np.clip(-d, 0, None) / 0.026) * (d < 0) * a_ang * ga
    if dis > 0:
        keep = 0.6 * fine + 0.4 * np.clip(1 - rho, 0, 1)
        tt = dis * 1.1
        m = _ss(tt - 0.05, tt + 0.05, keep)
        body, halo = body * m, halo * m
    lum = (0.62 + 0.38 * (1 - np.clip(rho, 0, 1)) ** 1.3) * (1 - 0.18 * aa / BASH_SPAN) * (1 - 0.5 * dark) * (0.9 + 0.2 * (streak - 0.5))
    lum, body = _merge(body, np.clip(lum, 0.5, 1.0), halo)
    t_rim = (0.014 + 0.02 * cosw)
    rim = _ss(-0.5 * px, 0.5 * px, d) * (1 - _ss(t_rim - 0.6 * px, t_rim + 0.6 * px, d)) * a_ang * ra
    echo = np.zeros_like(x)
    for fr, al in ((0.4, 0.5), (0.72, 0.3)):
        echo = np.maximum(echo, (1 - _ss(0.4 * px, 1.3 * px, np.abs(d - t * fr))) * al)
    echo = echo * a_ang * ra * (r > 0.05)
    rng = np.random.RandomState(99)
    segs = []
    if 1 <= k <= 6:
        s = (k - 1) / 5.0
        for i in range(14):
            a = math.radians(rng.uniform(-50, 50))
            rr = R + 0.02 + (0.04 + rng.rand() * 0.08) * (0.5 + s)
            ln = 0.02 + rng.rand() * 0.025
            segs.append((rr * math.sin(a), rr * math.cos(a), (rr + ln) * math.sin(a), (rr + ln) * math.cos(a), 0.005, (1 - s) ** 1.4))
    core = np.clip(rim + echo + _segments(x, y, segs, px), 0, 1)
    return lum, np.clip(body, 0, 1), core


# ------------------------------------------------------------ spark

def spark(k):
    x, y, px = _grid("spark")
    r = np.hypot(x, y)
    Lm = (1.0, 0.85, 0.62, 0.38, 0.18)[k]
    w0 = (0.075, 0.06, 0.045, 0.034, 0.024)[k]
    rays_b = np.zeros_like(x)
    rays_c = np.zeros_like(x)
    for i in range(8):
        a = math.radians(15 + i * 45)
        main = i % 2 == 0
        L = Lm * (1.0 if main else 0.55)
        ux, uy = math.cos(a), math.sin(a)
        t = x * ux + y * uy
        perp = np.abs(-x * uy + y * ux)
        u = np.clip(t / max(L, 1e-4), 0, 1)
        ww = w0 * (1.0 if main else 0.7) * (1 - u) ** 1.3
        m = (t >= -0.02) & (t <= L)
        rays_b = np.maximum(rays_b, (1 - _ss(ww - 0.5 * px, ww + 0.5 * px, perp)) * m)
        rays_c = np.maximum(rays_c, (1 - _ss(ww * 0.4 - 0.5 * px, ww * 0.4 + 0.5 * px, perp)) * m)
    disc_r = (0.22, 0.16, 0.1, 0.06, 0.03)[k]
    disc = 1 - _ss(disc_r * 0.6, disc_r, r)
    rr = (0.0, 0.42, 0.62, 0.78, 0.9)[k]
    ra = (0.0, 0.75, 0.45, 0.2, 0.06)[k]
    ring = (1 - _ss(0.012, 0.022, np.abs(r - rr))) * ra
    body = np.clip(np.maximum(np.maximum(rays_b, disc), ring), 0, 1)
    lum = np.clip(1 - 0.15 * np.clip(r / max(Lm, 1e-3), 0, 1), 0.7, 1.0)
    core = np.clip(np.maximum(rays_c, disc * 0.85), 0, 1)
    return lum, body, core


def kind_frames(kind):
    """판 kind 의 칸들 [(몸 밝기, 몸 알파, 날 알파)] (arc 는 frames())"""
    if kind == "arc":
        return frames()
    fn = {"thrust": thrust, "bash": bash, "spark": spark}[kind]
    return [fn(k) for k in range(KINDS[kind]["frames"])]


def painter(arr):
    """아틀라스 칸 그리기 (fx_models Atlas.swatch — u · v 0 ~ 1 → 그 픽셀)"""
    h, w = arr[0].shape

    def paint(u, v, rnd):
        x = min(w - 1, max(0, int(round(u * (w - 1)))))
        y = min(h - 1, max(0, int(round(v * (h - 1)))))
        return float(arr[0][y, x]), float(arr[1][y, x])

    return paint


def tint(lum, a, c, glow, core, bg):
    """한 칸을 바탕 위에 (모드 없는 클라이언트와 같이 섞는 빛 — 몸 다음 날)"""
    img = np.ones(lum.shape + (3,)) * bg
    img = img * (1 - a[..., None]) + (lum[..., None] * glow) * a[..., None]
    return img * (1 - c[..., None]) + core * c[..., None]


def preview(outdir):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    looks = {"sword": (0.47, 0.84, 1.0), "greatsword": (0.75, 0.35, 1.0), "lance": (1.0, 0.86, 0.45), "shield": (0.62, 0.8, 1.0), "axe": (1.0, 0.55, 0.2)}
    sets = (("arc_r", frames(), "sword"), ("arc_w", frames(thick=True), "greatsword"), ("thrust", kind_frames("thrust"), "lance"),
            ("bash", kind_frames("bash"), "shield"), ("spark", kind_frames("spark"), "axe"))
    for name, fr, look in sets:
        glow = np.array(looks[look])
        core = glow + (1 - glow) * 0.75
        for bgname, bg in (("dark", np.array([0.08, 0.09, 0.12])), ("grass", np.array([0.42, 0.55, 0.3])), ("sky", np.array([0.62, 0.74, 0.98]))):
            row = [tint(lum, a, c, glow, core, bg) for lum, a, c in fr]
            half = (len(row) + 1) // 2
            while len(row) < half * 2:
                row.append(np.ones_like(row[0]) * bg)
            sheet = np.concatenate([np.concatenate(row[:half], axis=1), np.concatenate(row[half:], axis=1)], axis=0)
            Image.fromarray((np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8)).save(os.path.join(outdir, "bslash2_%s_%s.png" % (name, bgname)))
    print(outdir)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
