#!/usr/bin/env python3
"""2026-11-12 픽셀 효과 그림 (사용자 "지금 스킬들 확인했는데 다 너무 저퀄이야 … 이런 느낌으로 만들어" — 예시 사진 8장):
글자 표시 엔티티 (text_display) 에 쓰는 비트맵 글꼴 rpg:px. 모드 없는 클라이언트도 보는 효과 (서버 Vfx · vfx.Px).

예시 사진의 공통점을 그대로 옮김
- 블록 질감과 같은 밀도의 또렷한 픽셀 (1칸 = 16픽셀, 흐림 · 부드러운 빛 무리 없음 — 글꼴은 가장 가까운 픽셀로 그림)
- 띠 색: 어두운 외곽선 → 짙은 색 → 계열 색 → 흰 심지 (대비가 큼). 안쪽 가장자리는 계단 모양으로 들쭉날쭉 · 떨어져 나온 픽셀 조각
- 크고 많게 (참격 5 ~ 8칸 · 바닥 균열 수십 개 · 불티 수십 개) — 크기 · 수는 서버가 정함

층 둘 (계열 색으로 물들임 — 글자 색): 이름 = A 층 (회색 단계 OUT · DARK · MID · BASE × 빛 색), 이름.b = B 층 (심지 — 속빛 색, 거의 흰색).
두 층은 같은 칸 · 같은 자리에 겹쳐 띄우고 픽셀이 겹치지 않음 (B 가 있는 곳은 A 를 비움 → 같은 면이어도 깜빡이지 않음).
미리 칠한 그림 (불 · 연기) 은 colored — 흰 글자로 씀.

  python3 tools/vfx_pixel.py                    # 씀
  python3 tools/vfx_pixel.py --check            # 쓸 것이 없는지
  python3 tools/vfx_pixel.py --preview <png>    # 모든 판을 계열 색으로 칠해 한 장에 (확인용)

만드는 것
- src/main/resources/assets/rpg/textures/font/px/<이름>.png   그림판 (칸 하나 = 글자 하나 · 칸은 256 이하)
- src/main/resources/assets/rpg/font/px.json                   글꼴 (판마다 bitmap 제공자 — 높이 H · ascent = H / 2 − 2: 글자 가운데가 엔티티 자리)
- src/main/resources/mmorpg/px_sprites.json                    서버가 읽는 목록 (이름 → 글자들 · 칸 수 · 가로세로 비 · 미리 칠했는지 · 축 (pivot) · 칸 높이 (블록))
"""
import json
import math
import os
import sys
from io import BytesIO

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_TEX = os.path.join(ROOT, "src/main/resources/assets/rpg/textures/font/px")
OUT_FONT = os.path.join(ROOT, "src/main/resources/assets/rpg/font/px.json")
OUT_INDEX = os.path.join(ROOT, "src/main/resources/mmorpg/px_sprites.json")

H = 64  # 글자 높이 (글꼴 단위 — rpg:vfx 와 같음: 글자 표시 엔티티 1 단위 = 0.025칸, 크기는 변환으로)
FIRST = 0xE000
COLS = 8
PPB = 16  # 1칸 = 16픽셀 (블록 질감과 같은 밀도)

# A 층 회색 단계 (× 빛 색)
OUT, DARK, MID, BASE = 0.2, 0.42, 0.7, 1.0


# ------------------------------------------------------------ 도움

def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def vnoise(w, h, cell, seed):
    """값 잡음 0 ~ 1 (cell 픽셀 격자를 이중 선형으로)"""
    rng = np.random.default_rng(seed)
    gw, gh = w // cell + 2, h // cell + 2
    g = rng.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    fx, fy = xs / cell, ys / cell
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[y0, x0] * (1 - tx) + g[y0, x0 + 1] * tx
    b = g[y0 + 1, x0] * (1 - tx) + g[y0 + 1, x0 + 1] * tx
    return a * (1 - ty) + b * ty


def coarse(rng, h, w, k=2):
    """k 픽셀 덩어리로 된 0 ~ 1 무작위 (조각이 한 픽셀보다 크게)"""
    g = rng.random(((h + k - 1) // k, (w + k - 1) // k))
    return np.kron(g, np.ones((k, k)))[:h, :w]


def dilate(m, n=1):
    out = m.copy()
    for _ in range(n):
        o = out.copy()
        o[1:, :] |= out[:-1, :]
        o[:-1, :] |= out[1:, :]
        o[:, 1:] |= out[:, :-1]
        o[:, :-1] |= out[:, 1:]
        out = o
    return out


def dilate8(m):
    o = dilate(m)
    o[1:, 1:] |= m[:-1, :-1]
    o[1:, :-1] |= m[:-1, 1:]
    o[:-1, 1:] |= m[1:, :-1]
    o[:-1, :-1] |= m[1:, 1:]
    return o


class Cv:
    """두 층 칸: A (단계 lvl · 알파 a) · B (심지 알파 b)"""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.lvl = np.zeros((h, w))
        self.a = np.zeros((h, w))
        self.b = np.zeros((h, w))

    def A(self, mask, tone, alpha=1.0):
        self.lvl[mask] = tone
        self.a[mask] = alpha
        self.b[mask] = 0

    def B(self, mask, alpha=1.0):
        self.b[mask] = alpha
        self.a[mask] = 0

    def clear(self, mask):
        self.a[mask] = 0
        self.b[mask] = 0

    def line(self, pts, fn, width=1):
        """꺾은선 (픽셀 자리 목록) 을 따라 fn(mask)"""
        m = np.zeros((self.h, self.w), bool)
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
            for i in range(n + 1):
                t = i / n
                x = int(round(x0 + (x1 - x0) * t - 0.5))
                y = int(round(y0 + (y1 - y0) * t - 0.5))
                for oy in range(width):
                    for ox in range(width):
                        xx, yy = x + ox - (width - 1) // 2, y + oy - (width - 1) // 2
                        if 0 <= xx < self.w and 0 <= yy < self.h:
                            m[yy, xx] = True
        fn(m)
        return m

    def images(self):
        a = np.where(self.b > 0, 0, self.a)
        A = np.zeros((self.h, self.w, 4), np.uint8)
        g = (np.clip(self.lvl, 0, 1) * 255).round().astype(np.uint8)
        A[..., 0] = g
        A[..., 1] = g
        A[..., 2] = g
        A[..., 3] = (np.clip(a, 0, 1) * 255).round().astype(np.uint8)
        B = np.zeros((self.h, self.w, 4), np.uint8)
        B[..., 0:3] = 255
        B[..., 3] = (np.clip(self.b, 0, 1) * 255).round().astype(np.uint8)
        return Image.fromarray(A, "RGBA"), Image.fromarray(B, "RGBA")


def grid(w, h):
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    return xs + 0.5, ys + 0.5


def mirror(frames):
    return [(a.transpose(Image.FLIP_LEFT_RIGHT), b.transpose(Image.FLIP_LEFT_RIGHT)) for a, b in frames]


# ------------------------------------------------------------ 참격 · 검기

def swing():
    """휩쓰는 초승달 (앞 반원 — 땅과 나란히 눕히거나 세워 씀): 축 (pivot) = 휘두르는 사람 (칸 아래 가운데).
    오른쪽 → 왼쪽 (0 ~ 7) · 왼쪽 → 오른쪽 (8 ~ 15, 거울). 0 ~ 3 드러남 (휘두르는 끝이 가장 굵고 밝음) · 4 다 드러남 · 5 ~ 7 픽셀로 부서짐.
    바깥 날 = 어두운 외곽선 + 흰 심지, 안으로 계열 색 → 짙은 색, 안쪽 가장자리는 계단 · 가시 모양, 떨어진 조각"""
    W, Hc = 176, 112
    cx, cy = 88.0, 100.0
    R = 80.0
    x, y = grid(W, Hc)
    dx, dy = x - cx, cy - y
    r = np.hypot(dx, dy)
    th = np.degrees(np.arctan2(dy, dx))
    th = np.where(th < -90, th + 360, th)
    T0, T1 = -10.0, 190.0
    s = (th - T0) / (T1 - T0)
    rng = np.random.default_rng(31)
    jag = np.kron(rng.integers(0, 7, (Hc // 2 + 1, W // 2 + 1)), np.ones((2, 2)))[:Hc, :W]
    spike_phase = rng.random() * 6
    reveal = [0.36, 0.6, 0.82, 1.0, 1.0, 1.0, 1.0, 1.0]
    breakk = [0, 0, 0, 0, 0, 0.3, 0.58, 0.84]
    dis = coarse(np.random.default_rng(32), Hc, W, 2)
    spark = coarse(np.random.default_rng(33), Hc, W, 2)
    frames = []
    for f in range(8):
        sh = reveal[f]
        u = s / sh
        inside = (s >= 0) & (u <= 1.0)
        prof = 0.16 + 0.84 * sstep(0.0, 0.82, u)
        tip = np.clip((1.0 - u) / 0.1, 0, 1) ** 0.6
        thick = 26.0 * prof * tip
        if f >= 5:
            thick = thick * (1 - 0.12 * (f - 4))
        rout = R + 2.0 * sstep(0.5, 1.0, u)
        d = rout - r
        # 안쪽 가장자리의 가시 (계단): 원 둘레를 따라 굵기가 들쭉날쭉
        ang = np.radians(th)
        spikes = np.maximum(0, np.sin(ang * 23 + spike_phase)) ** 3 * 9 + np.maximum(0, np.sin(ang * 9 + 1.3)) ** 4 * 6
        inner = thick + (spikes + jag) * np.clip(thick / 14, 0, 1)
        c = Cv(W, Hc)
        band = inside & (d >= 0) & (d < inner)
        core_w = np.clip(thick * 0.16, 0, 4.5) + 3.0 * sstep(0.82, 1.0, u) * (f < 5)
        c.A(band & (d < inner), DARK)
        c.A(band & (d < thick * 0.78), MID)
        c.A(band & (d < thick * 0.52), BASE)
        c.B(band & (d >= 1.6) & (d < 1.6 + core_w) & (thick > 7))
        c.A(band & (d < 1.6), OUT)
        # 끝 (휘두르는 칼끝) 은 흰빛
        c.B(inside & (d >= 0) & (d < thick * 0.6) & (u > 0.965) & (thick > 2) & (f < 5))
        # 떨어져 나온 조각 (안쪽 · 바깥쪽)
        out_band = inside & (d >= inner) & (d < inner + 10) & (thick > 5)
        dens = 0.86 + 0.1 * (d - inner) / 10
        c.A(out_band & (spark > dens), BASE)
        c.B(out_band & (spark > 0.985))
        front = inside & (d < 0) & (d > -4) & (u > 0.55) & (spark > 0.93)
        c.A(front, MID)
        # 속도 선 (안쪽의 가는 호 둘 — 휘두른 자취)
        if 1 <= f <= 4:
            for k_, (gap, tone, lo) in enumerate(((6, BASE, 0.35), (12, MID, 0.5))):
                rr = rout - inner - gap
                dash = ((th * 1.7 + k_ * 7) % 11) < 8
                c.A(inside & (np.abs(r - rr) < 0.75) & (u > lo) & (u < 0.97) & dash & (thick > 6), tone)
        if breakk[f] > 0:
            k = breakk[f]
            depth = np.clip(d / np.maximum(inner, 1), 0, 1)
            kill = dis < (k * (0.55 + 0.75 * depth))
            c.clear(kill & (c.a + c.b > 0))
        frames.append(c.images())
    return frames + mirror(frames)


def wave():
    """날아가는 검기 (가로 초승달 — 볼록한 앞이 그림 위쪽 = 날아가는 쪽): 앞 날 = 외곽선 + 흰 심지, 뒤로 계열 색 → 짙은 색,
    뒤 가장자리는 뒤로 뻗은 뾰족한 가시 · 떨어진 흰 번개 조각. 0 ~ 2 깜빡임 (모양이 조금씩 다름) · 3 ~ 5 부서짐"""
    W, Hc = 96, 48
    x, y = grid(W, Hc)
    frames = []
    for f in range(6):
        rng = np.random.default_rng(100 + (f if f < 3 else 2))
        cxw = W / 2
        u = (x - cxw) / (W / 2 - 4)  # −1 ~ 1
        front = 7 + 22 * u ** 2
        thick = 17 * np.clip(1 - u ** 2, 0, 1) ** 0.7
        d = y - front
        ph1, ph2 = rng.uniform(0, 6, 2)
        spikes = np.maximum(0, np.sin(x * 0.55 + ph1)) ** 3 * 9 + np.maximum(0, np.sin(x * 0.23 + ph2)) ** 4 * 7
        inner = thick + spikes * np.clip(thick / 9, 0, 1)
        live = (np.abs(u) <= 1) & (d >= 0)
        c = Cv(W, Hc)
        c.A(live & (d < inner), DARK)
        c.A(live & (d < thick * 0.8), MID)
        c.A(live & (d < thick * 0.55), BASE)
        cw = np.clip(thick * 0.3, 0, 5)
        c.B(live & (d >= 1.5) & (d < 1.5 + cw) & (thick > 4))
        c.A(live & (d < 1.5), OUT)
        # 흰 번개 조각 (뒤 · 두 끝 — 가로로 꺾인 1픽셀 흰 선, 끝만 계열 색)
        for _ in range(3):
            sx = rng.uniform(14, W - 14)
            su = (sx - cxw) / (W / 2 - 4)
            sy = 7 + 22 * su ** 2 + 17 * max(0, 1 - su * su) ** 0.7 + rng.uniform(4, 10)
            sgn = 1 if rng.random() < 0.5 else -1
            pts = [(sx, sy)]
            for _k in range(4):
                sx += sgn * rng.uniform(3, 6)
                sy += rng.uniform(-2.5, 2.5)
                pts.append((sx, min(Hc - 2, sy)))
            m = c.line(pts, lambda mm: None)
            c.B(m)
            ex, ey = pts[-1]
            c.A((np.abs(x - ex) < 1.5) & (np.abs(y - ey) < 1.5) & ~m, BASE)
        if f >= 3:
            k = (f - 2) / 3.2
            dis = coarse(np.random.default_rng(140 + f), Hc, W, 2)
            depth = np.clip(d / np.maximum(inner, 1), 0, 1)
            c.clear((dis < k * (0.5 + 0.8 * depth)) & (c.a + c.b > 0))
        frames.append(c.images())
    return frames


def streak():
    """지나간 자리의 빛줄기 (가로로 길게 — 서버가 길이만큼 늘림): 가운데 흰 심지 (끊긴 곳) · 계열 색 · 외곽선. 0 · 1 깜빡임 · 2 ~ 4 가늘어지며 끊어짐"""
    W, Hc = 128, 16
    x, y = grid(W, Hc)
    frames = []
    for f in range(5):
        rng = np.random.default_rng(200 + min(f, 1))
        prof = np.clip(x / 30, 0, 1) ** 0.8 * np.clip((W - x) / 10, 0, 1)
        hw = 6.0 * prof * (1 - 0.22 * max(0, f - 1))
        notch = np.kron(rng.integers(0, 2, (1, W // 4 + 1)), np.ones((1, 4)))[:, :W]
        hw = hw - notch * (hw > 3)
        d = np.abs(y - Hc / 2)
        c = Cv(W, Hc)
        c.A(d < hw, OUT)
        c.A(d < hw - 1, MID)
        c.A(d < hw - 2, BASE)
        gap = np.kron(rng.random((1, W // 6 + 1)), np.ones((1, 6)))[:, :W] > (0.15 + 0.2 * f)
        c.B((d < 1.01) & (hw > 3.5) & gap)
        if f >= 2:
            dis = coarse(np.random.default_rng(220 + f), Hc, W, 2)
            c.clear((dis < 0.22 * (f - 1)) & (c.a + c.b > 0))
        frames.append(c.images())
    return frames


def shard():
    """흰 번개 조각 (떨어져 나온 빛 — 카메라를 봄): 1픽셀 흰 꺾은선 + 계열 색 테두리. 네 모양"""
    W = Hc = 24
    frames = []
    for f in range(4):
        rng = np.random.default_rng(300 + f)
        c = Cv(W, Hc)
        pts = [(rng.uniform(4, 8), rng.uniform(3, 7))]
        for _ in range(4):
            px, py = pts[-1]
            pts.append((min(W - 3, px + rng.uniform(2, 5)), min(Hc - 3, py + rng.uniform(2, 5)) if rng.random() < 0.7 else max(3, py - rng.uniform(1, 3))))
        m = c.line(pts, lambda mm: None)
        if rng.random() < 0.7:
            bx, by = pts[2]
            m |= c.line([(bx, by), (bx + rng.uniform(-6, -2), by + rng.uniform(2, 6))], lambda mm: None)
        c.A(dilate8(m) & ~m, BASE)
        c.B(m)
        frames.append(c.images())
    return frames


def cutmark():
    """맞은 자리의 베인 자국 (짧은 렌즈 모양 — 카메라를 보고 기울여 씀): 0 그어짐 · 1 다 · 2 가늘어짐 · 3 조각"""
    W, Hc = 64, 16
    x, y = grid(W, Hc)
    frames = []
    for f, (grow, k) in enumerate([(0.55, 1.0), (1.0, 1.0), (1.0, 0.6), (1.0, 0.35)]):
        u = (x - 2) / (W - 4)
        live = (u >= 0) & (u <= grow)
        hw = 6.5 * np.sin(np.clip(u, 0, 1) * math.pi) ** 0.8 * k
        d = np.abs(y - Hc / 2 + (u - 0.5) * 2.0)
        c = Cv(W, Hc)
        c.A(live & (d < hw), OUT)
        c.A(live & (d < hw - 1), BASE)
        c.B(live & (d < hw - 2.2))
        if f == 3:
            dis = coarse(np.random.default_rng(330), Hc, W, 2)
            c.clear((dis < 0.45) & (c.a + c.b > 0))
        frames.append(c.images())
    return frames


# ------------------------------------------------------------ 터짐 · 불티 · 고리

def star():
    """번쩍임 (네 갈래 별 — 카메라를 봄): 0 큼 → 3 작음. 가운데 · 갈래 = 흰 심지, 둘레 = 계열 색 · 짙은 테"""
    W = Hc = 32
    x, y = grid(W, Hc)
    dx, dy = x - W / 2, y - Hc / 2
    frames = []
    for f, s in enumerate([1.0, 0.72, 0.48, 0.28]):
        L = 15 * s
        ray = (np.abs(dx) < 1.01) & (np.abs(dy) < L) | (np.abs(dy) < 1.01) & (np.abs(dx) < L)
        diag = (np.abs(np.abs(dx) - np.abs(dy)) < 1.0) & (np.hypot(dx, dy) < L * 0.42)
        body = np.abs(dx) + np.abs(dy) < 5.5 * s + 1
        c = Cv(W, Hc)
        core = ray | body | diag
        c.A(dilate8(core), BASE)
        c.A(dilate8(dilate8(core)) & ~dilate8(core), DARK)
        thin = (np.abs(dx) < 0.51) & (np.abs(dy) < L - 1) | (np.abs(dy) < 0.51) & (np.abs(dx) < L - 1) | (np.abs(dx) + np.abs(dy) < 4.0 * s)
        c.B(thin)
        frames.append(c.images())
    return frames


def ember():
    """불티 (네모 — 카메라를 봄, 한 층 회색 × 계열 색): 0 큼 (3×3 심지 · 테 · 옅은 무리) → 3 작음"""
    W = Hc = 12
    frames = []
    for f in range(4):
        lvl = np.zeros((Hc, W))
        a = np.zeros((Hc, W))
        cxy = 6
        sizes = [(3, 5, 7), (2, 4, 6), (1, 3, 5), (1, 2, 0)][f]
        core, rim, halo = sizes
        def sq(n):
            m = np.zeros((Hc, W), bool)
            if n <= 0:
                return m
            o = cxy - n // 2 - (n % 2 == 0) * 0
            o = cxy - (n + 1) // 2
            m[o:o + n, o:o + n] = True
            return m
        h = sq(halo)
        if halo:
            h[[cxy - (halo + 1) // 2, cxy - (halo + 1) // 2, cxy - (halo + 1) // 2 + halo - 1, cxy - (halo + 1) // 2 + halo - 1],
              [cxy - (halo + 1) // 2, cxy - (halo + 1) // 2 + halo - 1, cxy - (halo + 1) // 2, cxy - (halo + 1) // 2 + halo - 1]] = False
        lvl[h] = 0.62
        a[h] = 0.45
        lvl[sq(rim)] = 0.82
        a[sq(rim)] = 1
        lvl[sq(core)] = 1.0
        a[sq(core)] = 1
        frames.append(gray_img(lvl, a))
    return frames


def gray_img(lvl, a):
    im = np.zeros(lvl.shape + (4,), np.uint8)
    g = (np.clip(lvl, 0, 1) * 255).round().astype(np.uint8)
    im[..., 0] = g
    im[..., 1] = g
    im[..., 2] = g
    im[..., 3] = (np.clip(a, 0, 1) * 255).round().astype(np.uint8)
    return Image.fromarray(im, "RGBA")


def shock():
    """땅의 충격파 고리 (땅에 눕힘): 0 ~ 5 커지며 가늘어짐 · 부서짐. 바깥 = 외곽선 + 흰 심지, 안쪽 = 계열 색 → 짙은 색 (계단 가장자리)"""
    S = 128
    x, y = grid(S, S)
    r = np.hypot(x - S / 2, y - S / 2)
    ang = np.arctan2(y - S / 2, x - S / 2)
    frames = []
    for f in range(6):
        t = f / 5
        R = 18 + 44 * (1 - (1 - t) ** 1.6)
        th = 11 * (1 - t) + 3
        rng = np.random.default_rng(400 + f)
        jag = np.kron(rng.integers(0, 4, (S // 2, S // 2)), np.ones((2, 2)))
        spikes = np.maximum(0, np.sin(ang * 17 + f)) ** 3 * 4 * (1 - t)
        d = R - r
        inner = th + jag * (1 - t) + spikes
        c = Cv(S, S)
        band = (d >= 0) & (d < inner)
        c.A(band, DARK)
        c.A(band & (d < th * 0.7), MID)
        c.A(band & (d < th * 0.45), BASE)
        c.B(band & (d >= 1.2) & (d < 1.2 + max(1.0, th * 0.18)) & (f < 4))
        c.A(band & (d < 1.2), OUT)
        if f >= 3:
            dis = coarse(np.random.default_rng(420 + f), S, S, 2)
            c.clear((dis < 0.25 * (f - 2)) & (c.a + c.b > 0))
        frames.append(c.images())
    return frames


def dotring():
    """점 고리 (땅에 눕힘 — 반짝이는 금빛 점의 원, 한 층): 0 · 1 반짝임이 옮겨 감"""
    S = 128
    frames = []
    rng = np.random.default_rng(500)
    n = 96
    base_r = 58
    pts = []
    for i in range(n):
        a = i / n * math.pi * 2 + rng.uniform(-0.02, 0.02)
        rr = base_r + rng.uniform(-2.5, 2.5)
        pts.append((S / 2 + math.cos(a) * rr, S / 2 + math.sin(a) * rr, rng.integers(0, 3)))
    for f in range(2):
        lvl = np.zeros((S, S))
        a = np.zeros((S, S))
        for i, (px, py, kind) in enumerate(pts):
            xi, yi = int(px), int(py)
            bright = (i + f) % 3 == 0
            sz = 2
            lvl[yi:yi + sz, xi:xi + sz] = 1.0 if bright else 0.74
            a[yi:yi + sz, xi:xi + sz] = 1
            if bright and kind == 2:
                for ox, oy in ((-1, 0), (sz, 0), (0, -1), (0, sz)):
                    if 0 <= yi + oy < S and 0 <= xi + ox < S:
                        lvl[yi + oy, xi + ox] = 0.6
                        a[yi + oy, xi + ox] = 1
        frames.append(gray_img(lvl, a))
    return frames


def octagon():
    """팔각형 (땅에 눕힘 — 투기장 · 영역 테두리): 굵은 빛 선 (외곽선 · 계열 색 · 흰 심지) · 꼭짓점 마름모. 0 ~ 7 한 변씩 그려짐 (그리는 끝 반짝),
    8 다 · 9 맥동 (심지가 굵음)"""
    S = 192
    x, y = grid(S, S)
    cx = cy = S / 2
    Rv = 88.0
    verts = [(cx + Rv * math.cos(math.radians(22.5 + 45 * i)), cy + Rv * math.sin(math.radians(22.5 + 45 * i))) for i in range(8)]
    frames = []
    for f in range(10):
        c = Cv(S, S)
        nedge = min(8, f + 1)
        dist = np.full((S, S), 1e9)
        for i in range(nedge):
            (x0, y0), (x1, y1) = verts[i], verts[(i + 1) % 8]
            vx, vy = x1 - x0, y1 - y0
            L2 = vx * vx + vy * vy
            t = np.clip(((x - x0) * vx + (y - y0) * vy) / L2, 0, 1)
            d = np.hypot(x - (x0 + vx * t), y - (y0 + vy * t))
            dist = np.minimum(dist, d)
        w = 3.6 if f < 9 else 4.2
        c.A(dist < w + 1.0, OUT)
        c.A(dist < w, BASE)
        c.B(dist < (1.2 if f < 9 else 1.8))
        # 꼭짓점 마름모
        for i in range(nedge + (1 if f < 8 else 0)):
            vx_, vy_ = verts[i % 8]
            dd = np.abs(x - vx_) + np.abs(y - vy_)
            c.A(dd < 8.5, OUT)
            c.A(dd < 7.0, BASE)
            c.B(dd < 3.5)
        if f < 8:
            hx, hy = verts[(f + 1) % 8]
            dd = np.abs(x - hx) + np.abs(y - hy)
            ray = ((np.abs(x - hx) < 1) & (np.abs(y - hy) < 12)) | ((np.abs(y - hy) < 1) & (np.abs(x - hx) < 12))
            c.A(dilate8(ray) & ~ray, BASE)
            c.B(ray | (dd < 4.5))
        frames.append(c.images())
    return frames


def darkring():
    """어두운 고리 (공중에 눕힘 — 감옥 뚜껑 · 영역 위): 굵은 짙은 띠 + 안쪽 빛 선 + 끊긴 곳. 한 층 회색 × 계열 색"""
    S = 128
    x, y = grid(S, S)
    r = np.hypot(x - S / 2, y - S / 2)
    ang = np.degrees(np.arctan2(y - S / 2, x - S / 2)) % 360
    frames = []
    for f in range(2):
        lvl = np.zeros((S, S))
        a = np.zeros((S, S))
        gaps = ((ang + f * 11) % 60) < 7
        band = (r > 48) & (r < 58) & ~gaps
        lvl[band] = 0.13
        a[band] = 1
        line = (np.abs(r - 50.5) < 0.9) & ~gaps
        lvl[line] = 1.0
        rim = (np.abs(r - 57.4) < 0.7) & ~gaps
        lvl[rim] = 0.5
        ticks = ((ang + f * 11) % 30 < 2) & (r > 58) & (r < 62)
        lvl[ticks] = 0.75
        a[ticks] = 1
        frames.append(gray_img(lvl, a))
    return frames


# ------------------------------------------------------------ 번개 · 빛기둥 · 감옥

def bolt():
    """번개 (세로 — 세로축으로만 카메라를 봄): 흰 심지 2픽셀 · 계열 색 테 · 짙은 겉 테, 날카롭게 꺾임 · 짧은 가지 · 아래 갈래. 네 모양. 축 = 아래 가운데"""
    W, Hc = 32, 160
    frames = []
    for f in range(4):
        rng = np.random.default_rng(600 + f)
        c = Cv(W, Hc)
        pts = [(W / 2 + rng.uniform(-3, 3), 0.0)]
        yy = 0.0
        while yy < Hc - 6:
            yy = min(Hc - 4, yy + rng.uniform(7, 15))
            px = np.clip(W / 2 + rng.uniform(-9, 9), 5, W - 5)
            pts.append((px, yy))
        main = c.line(pts, lambda m: None, width=2)
        branches = np.zeros_like(main)
        for _ in range(3):
            i = rng.integers(1, len(pts) - 2)
            bx, by = pts[i]
            sgn = 1 if rng.random() < 0.5 else -1
            bp = [(bx, by)]
            for _k in range(3):
                bx = np.clip(bx + sgn * rng.uniform(2, 5), 1, W - 2)
                by = by + rng.uniform(3, 7)
                bp.append((bx, by))
            branches |= c.line(bp, lambda m: None)
        fx, fy = pts[-1]
        for sgn in (-1, 1):
            branches |= c.line([(fx, fy), (fx + sgn * 5, Hc - 1)], lambda m: None)
        core = main | branches
        g1 = dilate8(core)
        g2 = dilate(g1)
        c.A(g2, DARK)
        c.A(g1, BASE)
        c.B(core)
        frames.append(c.images())
    return frames


def chain():
    """사슬 (세로 — 세로축으로 카메라를 봄, 하늘에서 내리침): 고리가 앞 · 옆으로 번갈아 이어짐 (A = 짙은 테 · 계열 색 쇠, B = 흰 빛 반사), 아래 끝은 뾰족한 추.
    두 모양 (고리 차례가 반 칸 어긋남 — 번갈아 쓰면 흘러내리는 것처럼). 축 = 아래 가운데"""
    W, Hc = 16, 160
    x, y = grid(W, Hc)
    frames = []
    for f in range(2):
        c = Cv(W, Hc)
        off = f * 7
        cx = W / 2
        for k in range(-1, Hc // 14 + 2):
            cy = k * 14 + off
            if cy > Hc - 22:
                break
            if k % 2 == 0:
                # 앞을 보는 고리 (타원 테)
                d = np.hypot((x - cx) / 5.5, (y - cy) / 8.0)
                ring = (d < 1.0) & (d > 0.55)
                c.A(dilate8(ring) & ~ring & (c.a == 0), OUT)
                c.A(ring, BASE)
                c.A(ring & (x > cx + 1.5), MID)
                c.B(ring & (x < cx - 2.5) & (y < cy))
            else:
                # 옆을 보는 고리 (세로 막대)
                bar = (np.abs(x - cx) < 1.6) & (np.abs(y - cy) < 8.5)
                c.A(dilate8(bar) & ~bar & (c.a == 0), OUT)
                c.A(bar, BASE)
                c.B(bar & (x < cx) & (np.abs(y - cy) < 5))
        # 끝의 추 (마름모 날)
        ty = Hc - 14
        tip = (np.abs(x - cx) * 1.4 + np.abs(y - ty) < 9) & (y > ty - 8)
        c.A(dilate8(tip) & ~tip, OUT)
        c.A(tip, BASE)
        c.A(tip & (x > cx), MID)
        c.B(tip & (np.abs(x - cx) < 1.01) & (y > ty - 6) & (y < ty + 5))
        frames.append(c.images())
    return frames


def pillar():
    """빛기둥 (세로 — 하늘에서 내리꽂힘, 세로축으로 카메라를 봄): 흰 심지 · 계열 색 · 짙은 가장자리 (세로 결), 가로 고리가 아래로 흐름 (0 ~ 3),
    4 ~ 6 가늘어지며 끊어짐. 위는 성기게 (하늘로 사라짐). 축 = 아래 가운데"""
    W, Hc = 64, 192
    x, y = grid(W, Hc)
    dx = np.abs(x - W / 2)
    frames = []
    for f in range(7):
        rng = np.random.default_rng(700 + min(f, 3))
        k = 1.0 if f < 4 else (1 - 0.25 * (f - 3))
        hw = 26 * k
        streaks = np.kron(rng.integers(0, 4, (1, W // 2)), np.ones((1, 2)))[:, :W]
        streaks = np.repeat(streaks, Hc, axis=0)
        edge = hw - streaks
        c = Cv(W, Hc)
        c.A(dx < edge, DARK)
        c.A(dx < edge * 0.78, MID)
        c.A(dx < edge * 0.56, BASE)
        c.B(dx < edge * 0.3)
        # 흐르는 빛 알갱이 (가장자리 띠 안 — 칸마다 아래로 내려감) · 바깥의 날름거리는 가시
        spk = coarse(np.random.default_rng(730), Hc + 64, W, 2)[(64 - f * 8) % 64:(64 - f * 8) % 64 + Hc]
        c.B((spk > 0.93) & (dx < edge * 0.78) & (dx >= edge * 0.3))
        flick = np.kron(np.random.default_rng(740 + f % 4).integers(0, 4, (Hc // 4 + 1, 1)), np.ones((4, 1)))[:Hc]
        flick = np.repeat(flick, W, axis=1)
        c.A((dx >= edge) & (dx < edge + flick) & ((y.astype(int) // 2) % 3 != 0), MID)
        # 위 1/3 은 성기게 (하늘로)
        thin = coarse(np.random.default_rng(710 + f), Hc, W, 2)
        top = np.clip((50 - y) / 50, 0, 1)
        c.clear((thin < top * 0.9) & (c.a + c.b > 0))
        # 바닥 번쩍임 (넓게)
        # 바닥에 튀는 빛 (양옆으로 뻗은 가시)
        flare = (y > Hc - 12) & (dx < edge + (y - (Hc - 12)) * 0.6) & (dx >= edge)
        c.A(flare & ((x.astype(int) // 2) % 2 == 0), MID)
        c.A((y > Hc - 3) & (dx < edge + 4), BASE)
        if f >= 4:
            dis = coarse(np.random.default_rng(720 + f), Hc, W, 2)
            c.clear((dis < 0.2 * (f - 3)) & (c.a + c.b > 0))
        frames.append(c.images())
    return frames


def panel():
    """감옥 벽 한 장 (원통을 이루는 판 — 땅에 세움): A = 반투명 계열 색 몸 (알파 0.56) · 세로 테 · 위아래 밝은 테두리, B = 흰 번개 무늬 (모양 넷)"""
    W, Hc = 48, 80
    x, y = grid(W, Hc)
    frames = []
    for f in range(4):
        rng = np.random.default_rng(800 + f)
        c = Cv(W, Hc)
        body = np.ones((Hc, W), bool)
        c.A(body, BASE, 0.56)
        fade = coarse(np.random.default_rng(810 + f), Hc, W, 2)
        c.A(body & (fade < 0.25) & (y > 10), MID, 0.66)
        c.A((x < 1.5) | (x > W - 1.5), MID, 0.75)
        c.A(y > Hc - 3, BASE, 1.0)
        c.A((y > Hc - 5) & (y <= Hc - 3), MID, 1.0)
        c.A(y < 2, BASE, 0.9)
        for _ in range(2):
            px = rng.uniform(6, W - 6)
            pts = [(px, rng.uniform(4, 14))]
            while pts[-1][1] < Hc - 8:
                px = float(np.clip(pts[-1][0] + rng.uniform(-9, 9), 2, W - 2))
                pts.append((px, pts[-1][1] + rng.uniform(5, 11)))
            m = c.line(pts, lambda mm: None)
            c.A(dilate8(m) & ~m, BASE, 1.0)
            c.B(m)
        for _ in range(5):
            sx, sy = int(rng.uniform(2, W - 3)), int(rng.uniform(4, Hc - 6))
            c.B((np.abs(x - sx - 0.5) < 1) & (np.abs(y - sy - 0.5) < 1))
        frames.append(c.images())
    return frames


# ------------------------------------------------------------ 불 · 연기 · 먼지 (미리 칠함)

FIRE = [(1.0, 0.99, 0.82), (1.0, 0.88, 0.3), (1.0, 0.6, 0.12), (0.9, 0.32, 0.06), (0.5, 0.1, 0.04)]
SMOKE = [(0.62, 0.52, 0.46), (0.45, 0.37, 0.33), (0.3, 0.24, 0.22), (0.18, 0.14, 0.13)]


def blob_field(x, y, blobs):
    """덩이 여럿 (원) 의 합 — 안쪽이면 양수 (가장자리까지 픽셀), 빛 방향 (왼쪽 위) 의 밝기"""
    inside = np.full(x.shape, -1e9)
    shade = np.zeros(x.shape)
    for bx, by, br in blobs:
        d = br - np.hypot(x - bx, y - by)
        better = d > inside
        nx, ny = (x - bx) / br, (y - by) / br
        lit = np.clip(-(nx * 0.6 + ny * 0.8), -1, 1)
        shade = np.where(better, lit, shade)
        inside = np.maximum(inside, d)
    return inside, shade


def firepuff():
    """불 폭발 덩어리 (카메라를 봄 — 미리 칠함): 둥근 덩이 여럿 · 왼쪽 위에서 비춘 띠 색 (흰 노랑 → 노랑 → 주황 → 짙은 주황 → 검붉은 테).
    0 ~ 2 부풀며 터짐 (뜨거움) · 3 · 4 식음 (밝은 띠가 줄고 바깥부터 짙은 주황 · 덩이 모양의 그을음) · 5 ~ 7 연기로 흩어짐 (남은 불씨)"""
    S = 48
    x, y = grid(S, S)
    rng = np.random.default_rng(900)
    blobs0 = [(S / 2 + rng.uniform(-9, 9), S / 2 + rng.uniform(-8, 8), rng.uniform(7, 11)) for _ in range(6)] + [(S / 2, S / 2, 12)]
    soot = vnoise(S, S, 7, 931)
    frames = []
    for f in range(8):
        grow = [0.45, 0.75, 1.0, 1.06, 1.1, 1.12, 1.14, 1.16][f]
        blobs = [(S / 2 + (bx - S / 2) * grow, S / 2 + (by - S / 2) * grow - f * 0.6, br * grow) for bx, by, br in blobs0]
        inside, lit = blob_field(x, y, blobs)
        im = np.zeros((S, S, 4))
        live = inside > 0
        if f <= 4:
            heat = [1.0, 0.95, 0.8, 0.5, 0.25][f]
            v = (lit * 0.5 + 0.5) * 0.6 + np.clip(inside / 9, 0, 1) * 0.5 * heat + heat * 0.25
            idx = np.where(v > 1.0, 0, np.where(v > 0.78, 1, np.where(v > 0.55, 2, 3)))
            idx = np.where(inside < 1.6, 4, idx)
            col = np.array(FIRE)[idx]
            if f >= 3:
                # 그을음 (덩이 모양 — 잡음이 문턱보다 낮은 곳) · 그 테두리는 짙은 주황
                s_th = 0.3 if f == 3 else 0.48
                shadow = 0.5 - lit * 0.5  # 0 (빛 쪽) ~ 1 (그늘 쪽)
                sv_ = soot * 0.55 + shadow * 0.45
                sm = live & (sv_ < s_th) & (inside > 2)
                edge = live & (sv_ < s_th + 0.06) & ~sm & (inside > 2)
                sv = np.where(lit > 0.15, 1, 2)
                col = np.where(sm[..., None], np.array(SMOKE)[sv], col)
                col = np.where(edge[..., None], np.array(FIRE[3]), col)
            im[..., :3] = col
            im[..., 3] = live
        else:
            v = lit * 0.5 + 0.5
            idx = np.where(v > 0.7, 0, np.where(v > 0.45, 1, 2))
            idx = np.where(inside < 1.5, 3, idx)
            im[..., :3] = np.array(SMOKE)[idx]
            holes = vnoise(S, S, 5, 950 + f) < [0.22, 0.4, 0.58][f - 5]
            im[..., 3] = live & ~holes
            if f <= 6:
                emb = live & ~holes & (coarse(np.random.default_rng(960 + f), S, S, 2) > (0.95 if f == 5 else 0.975))
                im[emb, :3] = FIRE[1]
        frames.append(Image.fromarray((np.clip(im, 0, 1) * 255).round().astype(np.uint8), "RGBA"))
    return frames


def dust():
    """흙먼지 덩어리 (카메라를 봄 — 한 층 회색, 흙빛으로 물들임): 0 ~ 5 부풀며 흩어짐"""
    S = 32
    x, y = grid(S, S)
    rng = np.random.default_rng(1000)
    blobs0 = [(S / 2 + rng.uniform(-6, 6), S / 2 + rng.uniform(-4, 5), rng.uniform(5, 8)) for _ in range(5)]
    frames = []
    for f in range(6):
        g = 0.6 + 0.12 * f
        blobs = [(S / 2 + (bx - S / 2) * g, S / 2 + (by - S / 2) * g - f * 0.8, br * g) for bx, by, br in blobs0]
        inside, lit = blob_field(x, y, blobs)
        live = inside > 0
        v = lit * 0.5 + 0.5
        lvl = np.where(v > 0.66, 1.0, np.where(v > 0.4, 0.8, 0.62))
        lvl = np.where(inside < 1.4, 0.48, lvl)
        holes = coarse(np.random.default_rng(1010 + f), S, S, 2) < max(0, (f - 1) * 0.16)
        frames.append(gray_img(lvl, live & ~holes))
    return frames


# ------------------------------------------------------------ 바닥 균열

def crack():
    """바닥 균열 (땅에 눕힘 — 한 층 회색 × 계열 색): 검은 갈래 금 (1 ~ 2픽셀) + 가운데 달아오른 빛 (계열 색 그대로) · 금 안쪽의 녹은 빛. 여섯 모양"""
    S = 48
    frames = []
    for f in range(6):
        rng = np.random.default_rng(1100 + f)
        lvl = np.zeros((S, S))
        a = np.zeros((S, S))
        cx, cy = S / 2 + rng.uniform(-2, 2), S / 2 + rng.uniform(-2, 2)
        nb = rng.integers(4, 7)
        hot = np.zeros((S, S), bool)
        dark = np.zeros((S, S), bool)

        def walk(px, py, ang, length, width, depth, glow_len):
            for i in range(int(length)):
                ang += rng.uniform(-0.45, 0.45)
                px += math.cos(ang)
                py += math.sin(ang)
                xi, yi = int(px), int(py)
                if not (1 <= xi < S - 1 and 1 <= yi < S - 1):
                    return
                w = (width + 1 if i < length * 0.2 else width) if i < length * 0.7 else 1
                for oy in range(w):
                    for ox in range(w):
                        if 0 <= yi + oy < S and 0 <= xi + ox < S:
                            dark[yi + oy, xi + ox] = True
                            if i < glow_len:
                                hot[yi + oy, xi + ox] = True
                if depth < 2 and rng.random() < 0.09:
                    walk(px, py, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.0), length * rng.uniform(0.3, 0.55), 1, depth + 1, 0)

        for i in range(nb):
            ang = i / nb * math.pi * 2 + rng.uniform(-0.4, 0.4)
            walk(cx, cy, ang, rng.uniform(12, 22), 2, 0, rng.integers(5, 10))
        x, y = grid(S, S)
        blob = np.hypot(x - cx, y - cy) < rng.uniform(2.6, 3.8)
        lvl[dark] = 0.06
        a[dark] = 1
        lvl[dark & ~hot & (coarse(rng, S, S, 1) > 0.85)] = 0.22
        rim = dilate8(blob | hot) & ~(blob | hot)
        lvl[rim & (a > 0)] = 0.55
        lvl[hot] = 0.92
        a[hot] = 1
        lvl[blob] = 1.0
        a[blob] = 1
        brim = dilate8(blob) & ~blob
        lvl[brim] = np.maximum(lvl[brim], 0.62)
        a[brim] = 1
        # 떨어진 조각 (검은 점)
        chips = (coarse(rng, S, S, 1) > 0.9) & ~dark & dilate8(dilate8(dark)) & ~dilate8(dark)
        lvl[chips] = 0.08
        a[chips] = 1
        frames.append(gray_img(lvl, a))
    return frames


# ------------------------------------------------------------ 3D: 투기장 벽 (진짜 블록 질감)

RES = os.path.join(ROOT, "src/main/resources/assets/rpg")


def arena_wall():
    """투기장 벽 한 칸 (아이템 모델 rpg:vfx/arena_wall — 예시 사진의 흰 돌 지그재그 벽): 폭 3칸 (x −16 ~ 32) · 두께 5픽셀 · 몸 높이 10픽셀 위에
    계단 모양 이빨 여섯 (꼭대기 20 — 낮게: 안이 가려지지 않게). 석영 벽돌 질감 (바닐라 블록 그림 그대로), 아래 검은 돌 띠 · 그 위의 빛 띠 (물들임 0 — 계열 색, 빛 15).
    쥔 곳 = 아래 가운데 (8, 0, 8)"""
    els = []

    def box(f, t, tex, tint=None, glow=False, name="wall"):
        faces = {}
        for fn in ("north", "south", "east", "west", "up", "down"):
            if fn in ("north", "south"):
                w0, w1, h0, h1 = f[0], t[0], f[1], t[1]
            elif fn in ("east", "west"):
                w0, w1, h0, h1 = f[2], t[2], f[1], t[1]
            else:
                w0, w1, h0, h1 = f[0], t[0], f[2], t[2]
            w, h = min(16.0, w1 - w0), min(16.0, h1 - h0)
            u0 = w0 % 16
            if u0 + w > 16:
                u0 = 16 - w
            if fn in ("up", "down"):
                v0 = h0 % 16
                if v0 + h > 16:
                    v0 = 16 - h
            else:
                v0 = (16 - (h1 % 16 or 16)) % 16
                if v0 + h > 16:
                    v0 = 16 - h
            face = {"uv": [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)], "texture": "#" + tex}
            if tint is not None:
                face["tintindex"] = tint
            faces[fn] = face
        e = {"name": name, "from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": faces}
        if glow:
            e["light_emission"] = 15
        els.append(e)

    z0, z1 = 5.5, 10.5
    for x0 in (-16, 0, 16):
        box((x0, 2, z0), (x0 + 16, 10, z1), "wall")
        box((x0, 0, z0 - 0.5), (x0 + 16, 2, z1 + 0.5), "dark", name="base")
    box((-16, 2, z0 - 0.3), (32, 3, z1 + 0.3), "glow", tint=0, glow=True, name="seam")
    for k, cx in enumerate((-12, -4, 4, 12, 20, 28)):
        steps = [(10, 14, 8), (14, 17, 6), (17, 19, 4), (19, 20, 2)] if k % 2 == 0 else [(10, 13, 8), (13, 15, 6), (15, 16.5, 4)]
        for y0, y1, w in steps:
            box((cx - w / 2, y0, z0 + 0.5), (cx + w / 2, y1, z1 - 0.5), "top")
    for e in els:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (e["name"], v)
    model = {"credit": "tools/vfx_pixel.py — 투기장 벽 (바닐라 블록 그림)",
             "textures": {"wall": "minecraft:block/quartz_bricks", "top": "minecraft:block/quartz_block_bottom",
                          "dark": "minecraft:block/polished_blackstone", "glow": "minecraft:block/white_concrete", "particle": "minecraft:block/quartz_bricks"},
             "elements": els}
    item = {"model": {"type": "minecraft:model", "model": "rpg:item/vfx/arena_wall",
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}}
    return {os.path.join(RES, "models/item/vfx/arena_wall.json"): (json.dumps(model, ensure_ascii=False, indent=1) + "\n").encode("utf-8"),
            os.path.join(RES, "items/vfx/arena_wall.json"): (json.dumps(item, indent=2) + "\n").encode("utf-8")}


# ------------------------------------------------------------ 목록

# (이름, 만들기, 층: 2 = A + B · 1 = 회색 한 층 · 0 = 미리 칠함, 축 (칸 안 0 ~ 1 — 왼쪽 위에서))
SHEETS = [
    ("swing", swing, 2, (0.5, 100 / 112)),
    ("wave", wave, 2, (0.5, 0.5)),
    ("streak", streak, 2, (0.0, 0.5)),
    ("shard", shard, 2, (0.5, 0.5)),
    ("cutmark", cutmark, 2, (0.5, 0.5)),
    ("star", star, 2, (0.5, 0.5)),
    ("shock", shock, 2, (0.5, 0.5)),
    ("octagon", octagon, 2, (0.5, 0.5)),
    ("bolt", bolt, 2, (0.5, 1.0)),
    ("chain", chain, 2, (0.5, 1.0)),
    ("pillar", pillar, 2, (0.5, 1.0)),
    ("panel", panel, 2, (0.5, 1.0)),
    ("ember", ember, 1, (0.5, 0.5)),
    ("dotring", dotring, 1, (0.5, 0.5)),
    ("darkring", darkring, 1, (0.5, 0.5)),
    ("crack", crack, 1, (0.5, 0.5)),
    ("dust", dust, 1, (0.5, 0.5)),
    ("firepuff", firepuff, 0, (0.5, 0.5)),
]


def sheets():
    """→ [(판 이름, 칸 그림 목록, 미리 칠했는지, 축, 층 이름 (A 판이면 B 판 이름))]"""
    out = []
    for name, make, layers, pivot in SHEETS:
        cells = make()
        if layers == 2:
            out.append((name, [a for a, b in cells], False, pivot, name + ".b"))
            out.append((name + ".b", [b for a, b in cells], False, pivot, None))
        else:
            out.append((name, cells, layers == 0, pivot, None))
    return out


def build():
    files = {}
    providers = []
    index = {}
    code = FIRST
    for name, cells, colored, pivot, other in sheets():
        cw, ch = cells[0].size
        assert cw <= 256 and ch <= 256, (name, cw, ch)
        assert all(c.size == (cw, ch) for c in cells), name
        cols = min(COLS, len(cells))
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cw, rows * ch), (0, 0, 0, 0))
        chars = []
        lines = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(cells):
                    cell = cells[i].copy()
                    px = cell.load()
                    if px[cw - 1, 0][3] == 0:
                        px[cw - 1, 0] = (255, 255, 255, 1)  # 너비가 줄지 않게 (보이지 않음 — 셰이더가 0.1 아래를 버림)
                    if px[0, 0][3] == 0:
                        px[0, 0] = (255, 255, 255, 1)
                    sheet.paste(cell, (c * cw, r * ch))
                    ch_ = chr(code)
                    code += 1
                    chars.append(ch_)
                    line += ch_
                else:
                    line += "\u0000"
            lines.append(line)
        buf = BytesIO()
        sheet.save(buf, "PNG", optimize=True)
        fname = name.replace(".", "_")
        files[os.path.join(OUT_TEX, fname + ".png")] = buf.getvalue()
        providers.append({"type": "bitmap", "file": "rpg:font/px/%s.png" % fname, "height": H, "ascent": H // 2 - 2, "chars": lines})
        entry = {"chars": "".join(chars), "frames": len(chars), "aspect": round(cw / ch, 4), "colored": colored,
                 "pivot": [round(pivot[0], 4), round(pivot[1], 4)], "hb": round(ch / PPB, 4)}
        if other:
            entry["b"] = other
        index[name] = entry
    font = {"providers": providers}
    idx = {"_doc": "tools/vfx_pixel.py 가 만듦 (손으로 고치지 않음) — 글꼴 rpg:px 의 픽셀 효과 그림: 이름 → 글자 (칸 차례) · 칸 수 · 가로세로 비 · 미리 칠했는지 · "
                   "축 (칸 안 0 ~ 1, 왼쪽 위에서) · 칸 높이 (블록, 1칸 = 16픽셀) · b (같이 띄우는 심지 층). 글자 높이 h 글꼴 단위",
           "font": "rpg:px", "h": H, "sprites": index}
    files[OUT_FONT] = (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_INDEX] = (json.dumps(idx, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files.update(arena_wall())
    return files, index


# ------------------------------------------------------------ 확인용 그림

TINTS = {
    "blade": ((0.3, 0.92, 0.92), (0.9, 1, 1)),
    "reaper": ((0.86, 0.06, 0.12), (1, 0.72, 0.68)),
    "holy": ((1, 0.82, 0.32), (1, 0.98, 0.86)),
    "storm": ((0.62, 0.48, 1), (0.9, 0.92, 1)),
}


def colorize(a, b, glow, core):
    A = np.asarray(a).astype(float) / 255
    out = np.zeros_like(A)
    out[..., :3] = A[..., :3] * np.array(glow)
    out[..., 3] = A[..., 3]
    if b is not None:
        B = np.asarray(b).astype(float) / 255
        m = B[..., 3] > 0.1
        out[m, :3] = np.array(core)
        out[m, 3] = B[m, 3]
    out[out[..., 3] < 0.1, 3] = 0
    return out


def preview(path, only=None, scale=3):
    rows = []
    for name, make, layers, pivot in SHEETS:
        if only and name not in only:
            continue
        cells = make()
        fam = list(TINTS.items())
        for fi, (fname, (glow, core)) in enumerate(fam if layers else fam[:1]):
            row = []
            for cell in cells:
                if layers == 2:
                    a, b = cell
                    arr = colorize(a, b, glow, core)
                elif layers == 1:
                    arr = colorize(cell, None, glow, core)
                else:
                    arr = np.asarray(cell).astype(float) / 255
                row.append(arr)
            rows.append((name + " · " + fname, row))
            if layers == 2 and fi >= 1 and name in ("streak", "shard", "cutmark", "star"):
                break
    # 배경: 돌 벽돌 비슷한 회색
    pad = 6
    width = max(sum(c.shape[1] * scale + pad for c in row) for _, row in rows) + pad
    height = sum(max(c.shape[0] for c in row) * scale + pad + 12 for _, row in rows) + pad
    bg = np.zeros((height, width, 3))
    yy, xx = np.mgrid[0:height, 0:width]
    tile = (((yy // (16 * scale)) + (xx // (16 * scale))) % 2) * 0.03
    bg[...] = (0.33 + tile)[..., None] * np.array([0.92, 0.95, 1.0])
    from PIL import ImageDraw
    img = Image.fromarray((bg * 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img)
    oy = pad
    for label, row in rows:
        d.text((pad, oy), label, fill=(255, 255, 255))
        oy += 12
        ox = pad
        hmax = 0
        for arr in row:
            h, w = arr.shape[:2]
            big = np.kron(arr, np.ones((scale, scale, 1)))
            rgb = (big[..., :3] * 255).astype(np.uint8)
            alpha = (big[..., 3] * 255).astype(np.uint8)
            tile_im = Image.fromarray(np.dstack([rgb, alpha]), "RGBA")
            img.paste(tile_im, (ox, oy), tile_im)
            d.rectangle([ox - 1, oy - 1, ox + w * scale, oy + h * scale], outline=(70, 70, 80))
            ox += w * scale + pad
            hmax = max(hmax, h * scale)
        oy += hmax + pad
    img.save(path)
    print("preview", path, img.size)


def main():
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        only = sys.argv[i + 2:] if len(sys.argv) > i + 2 else None
        preview(sys.argv[i + 1], only)
        return 0
    check = "--check" in sys.argv
    files, index = build()
    bad = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if check:
        if bad:
            print("px sprites differ: " + ", ".join(bad) + " — python3 tools/vfx_pixel.py")
            return 1
        print("px sprites ok (%d)" % len(index))
        return 0
    total = sum(len(v) for k, v in files.items() if k.endswith(".png"))
    print("px sprites %d sheets, %d bytes png" % (len(index), total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
