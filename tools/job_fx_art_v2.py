#!/usr/bin/env python3
"""v2 직업 스킬 연출 그림 — 입체 모델로 바꿨던 물체들을 다시 2D 로 (훨씬 곱게 다듬은 그림) + 직업별 맞은 자리 · 덧붙임 그림 → tools/java_fx (rpg:rfx_j*).

  python3 tools/job_fx_art_v2.py                       # 그림 · 파티클 정의를 tools/java_fx 에 씀 (java_fx_art.save)
  python3 tools/job_fx_art_v2.py --only rfx_jsword2    # 몇 개만 (쉼표로 여럿)
  python3 tools/job_fx_art_v2.py --preview <폴더>      # 확인용 그림도 씀 (그림마다: 장면 전부 = 빛 (색 A) 위에 심지 (색 B) · 어두운 바탕 + 한 장면 2배)

꼴은 v1 (tools/job_fx_art*.py) 과 같음: 모두 흰색 (밝기 = 알파) 으로 그려 게임에서 색을 입힘. 칸 = 빛 (넓고 흐린 겉 · 몸 채움) 장면 N 개 다음에
심지 (가늘고 또렷한 속 — 날 · 빛 줄 · 보석 · 새김 · 반짝임) 장면 N 개 (빛 = 칸 f, 심지 = 칸 N + f). 두 겹은 같은 모양에서 그려 점 하나까지 겹침.
rfx_jhits 만 직업 11 줄: 직업 i 의 빛 = 칸 i*12 + 0..5, 심지 = 칸 i*12 + 6..11 (12 열 × 11 줄).

그리기: 4배로 그려 (다각형 · 거리장 줄 · numpy 그러데이션) LANCZOS 로 줄임 → 1배에서 흐림 (빛 무리). 고정 시드 — 돌릴 때마다 같은 그림.
다른 그림을 쓰지 않음 (모두 이 스크립트가 처음부터 그림 · 룬 꼴은 지어낸 각진 글자 — 실제 룬 문자가 아님).

그림 (이름 — 보는 꼴 — 칸 — 장 수):
- rfx_jsword2      lookat_direction 256×64 ×4 (이어 돌림)  환영 장검: 날 · 가운데 홈 · 코등이 · 손잡이 · 자루 끝, 날을 따라 흐르는 빛
- rfx_jgreatsword  lookat_direction 512×128 ×4 (이어 돌림) 거대한 대검: 날 가운데 각진 새김 9 개가 차례로 맥동 · 강한 날 빛
- rfx_jaxe         rotate_xyz 192×192 ×8 (한 바퀴)        외날 전투 도끼가 가운데를 축으로 돎 + 원 꼬리
- rfx_jaxefly      lookat_direction 256×128 ×4            같은 도끼가 날아가는 쪽으로 누움 (머리가 오른쪽) + 뒤로 끌리는 묵직한 잔상
- rfx_jtome        rotate_xyz 256×256 ×12                 마도서: 0 ~ 2 열림, 3 ~ 8 책장 넘김 + 떠오르는 별 (이어 돌림), 9 ~ 11 닫히며 번쩍
- rfx_jstar        rotate_xyz 192×192 ×8 (이어 돌림)      떠도는 별: 구 안의 빛나는 별 · 서로 다른 빠르기로 도는 기운 고리 둘 · 도는 반짝임
- rfx_jbolt2       lookat_direction 192×64 ×4 (이어 돌림) 별 마탄: 도는 네 뿔 별 머리 · 가늘어지는 꼬리 · 반짝임
- rfx_jarrow2      lookat_direction 256×64 ×4 (이어 돌림) 바람 화살: 잎 촉 · 살대 · 깃 셋 · 살대를 감아 도는 바람 띠 둘
- rfx_jbow2        rotate_xyz 256×256 ×8                  빛의 리커브 활: 0 ~ 5 시위를 당기며 빛이 모임, 6 ~ 7 놓음 (시위가 튀고 오늬 자리 터짐)
- rfx_jdagger2     rotate_xyz 128×128 ×8 (한 바퀴)        휜 단검이 돎 + 가는 원 꼬리
- rfx_jspear2      lookat_direction 384×64 ×4 (이어 돌림) 긴 창: 잎 창날 · 작은 가름쇠 · 감은 손잡이 · 흐르는 빛
- rfx_jmaul2       lookat_y 256×384 ×4 (이어 돌림)        머리가 아래인 대망치: 머리를 기어 다니는 번개 · 빛나는 룬 띠
- rfx_jbell2       lookat_y 256×256 ×8                    장식 종: 왼쪽 → 가운데 → 오른쪽 → 가운데로 흔들림 · 아래로 퍼지는 소리 고리
- rfx_jbuckler2    rotate_xyz 128×128 ×8 (한 바퀴)        둥근 방패: 돌기 · 테 징 · 앞면이 타원으로 눌렸다 펴지며 돎 + 꼬리
- rfx_jaegis2      lookat_y 256×320 ×6                    연 모양 문장 방패: 0 ~ 2 빛 줄에서 나타남, 3 ~ 5 버팀 (빛이 지나감) — 날개 달린 별 문장
- rfx_jhits        rotate_xyz 128×128 ×6 × 직업 11        직업별 맞은 자리 (처음 두 장 번쩍 → 흩어짐)
- rfx_jvalwing     lookat_y 256×192 ×6 (이어 돌림)        등 뒤의 빛 깃털 날개 한 쌍 (날갯짓)
- rfx_jeclipse     rotate_xyz 256×256 ×8                  검은 해: 비운 원판 · 가는 밝은 테 · 일렁이는 코로나 (커짐 → 맥동)
- rfx_jrose        rotate_xyz 128×128 ×8                  장미: 0 ~ 4 핌, 5 ~ 7 꽃잎이 흩어짐
- rfx_jfrost       rotate_xyz 128×128 ×6                  얼음 결정 표시: 자라남 → 금이 가며 깨짐 (빙결)
- rfx_jrunes       lookat_direction 512×64 ×4 (이어 돌림) 각진 글자 10 개의 띠가 차례로 깜빡임 (마검사)
- rfx_jfeather     rotate_xyz 64×64 ×8 (이어 돌림)        떠다니며 뒤집히는 깃털 하나
- rfx_jwall        lookat_y 256×256 ×4                    땅에서 솟는 거친 돌 성벽 (낮음 → 다 솟음) · 금은 빛 줄
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402 — 같은 꼴의 파티클 정의 · tools/java_fx 에 씀

try:
    from scipy import ndimage as _ndi
except ImportError:  # scipy 가 없으면 numpy 로 (느리지만 같은 결과)
    _ndi = None

SS = 4  # 슈퍼샘플 (4배로 그려 LANCZOS 로 줄임)
TAU = math.tau

# 확인용 색 (A = 빛 · B = 심지)
JOBS = ["swordsman", "warrior", "spellblade", "mage", "archer", "assassin", "lancer", "reaper", "paladin", "priest", "guardian"]
COLORS = {
    "swordsman": ((0.42, 0.78, 1.0), (0.84, 0.9, 1.0)),
    "warrior": ((0.9, 0.14, 0.12), (1.0, 0.56, 0.16)),
    "spellblade": ((0.62, 0.34, 1.0), (0.34, 0.96, 0.95)),
    "mage": ((0.3, 0.36, 0.95), (1.0, 0.84, 0.36)),
    "archer": ((0.62, 0.95, 0.3), (1.0, 0.86, 0.36)),
    "assassin": ((0.42, 0.1, 0.52), (0.86, 0.26, 0.96)),
    "lancer": ((1.0, 0.82, 0.3), (1.0, 0.98, 0.9)),
    "reaper": ((0.1, 0.36, 0.38), (0.22, 0.96, 0.86)),
    "paladin": ((1.0, 0.8, 0.26), (0.5, 0.85, 1.0)),
    "priest": ((0.5, 0.95, 0.56), (1.0, 0.93, 0.62)),
    "guardian": ((0.46, 0.62, 0.84), (0.95, 0.97, 1.0)),
}


# ============================================================ 수학 도움

def clamp01(a):
    return np.clip(a, 0.0, 1.0)


def smooth(e0, e1, x):
    """smoothstep (배열 · 수)"""
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def ease_out(t, p=2.0):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** p


def ease_in_out(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def screen(*arrs):
    """빛 겹치기: 1 - Π(1 - a)"""
    out = np.zeros_like(arrs[0])
    for a in arrs:
        out = 1 - (1 - out) * (1 - clamp01(a))
    return out


def gblur(a, sigma):
    """가우스 흐림 (밖은 0)"""
    if sigma <= 0:
        return a
    if _ndi is not None:
        return _ndi.gaussian_filter(a, sigma, mode="constant", truncate=3.5)
    r = int(math.ceil(sigma * 3.5))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(v, k, mode="same"), 0, a)
    return np.apply_along_axis(lambda v: np.convolve(v, k, mode="same"), 1, a)


def inner_dist(mask, ss=SS):
    """모양 안쪽 깊이 (칸 점 단위 — 가장자리 0)"""
    m = mask > 0.5
    if _ndi is not None:
        return _ndi.distance_transform_edt(m).astype(np.float32) / ss
    # scipy 가 없으면: 깎아 들어가며 셈 (거칠지만 같은 쓰임)
    d = np.zeros(mask.shape, np.float32)
    cur = m.copy()
    k = 0
    while cur.any() and k < 400:
        d[cur] += 1
        nxt = cur.copy()
        nxt[1:, :] &= cur[:-1, :]
        nxt[:-1, :] &= cur[1:, :]
        nxt[:, 1:] &= cur[:, :-1]
        nxt[:, :-1] &= cur[:, 1:]
        cur = nxt
        k += 1
    return d / ss


def rot_pts(pts, cx, cy, a):
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


class Xf:
    """그림 자리 바꿈: 로컬 (x, y) → 칸 (cx + R·(sx·x, sy·y))"""

    def __init__(self, cx, cy, ang=0.0, sx=1.0, sy=1.0):
        self.cx, self.cy, self.ang, self.sx, self.sy = cx, cy, ang, sx, sy
        self.c, self.s = math.cos(ang), math.sin(ang)

    def __call__(self, pts):
        return [(self.cx + self.c * x * self.sx - self.s * y * self.sy, self.cy + self.s * x * self.sx + self.c * y * self.sy) for x, y in pts]

    def p(self, x, y):
        return self([(x, y)])[0]

    def inv(self, X, Y):
        dx, dy = X - self.cx, Y - self.cy
        return (self.c * dx + self.s * dy) / self.sx, (-self.s * dx + self.c * dy) / self.sy

    def vec(self, x, y):
        """로컬 방향 → 칸 방향 (자리 옮김 없이)"""
        return self.c * x * self.sx - self.s * y * self.sy, self.s * x * self.sx + self.c * y * self.sy


# ============================================================ 4배 그림판 (흰색 = 알파 0 ~ 1)

_GRID = {}


class Pad:
    """칸 하나의 4배 그림판. 좌표는 모두 칸 점 단위 (0 ~ w)."""

    def __init__(self, w, h, ss=SS):
        self.w, self.h, self.ss = w, h, ss
        self.W, self.H = w * ss, h * ss

    def zeros(self):
        return np.zeros((self.H, self.W), np.float32)

    def grid(self):
        key = (self.W, self.H, self.ss)
        if key not in _GRID:
            xs = (np.arange(self.W, dtype=np.float32) + 0.5) / self.ss
            ys = (np.arange(self.H, dtype=np.float32) + 0.5) / self.ss
            _GRID[key] = np.meshgrid(xs, ys)
        return _GRID[key]

    # ---- 채운 모양 (PIL 다각형 · 타원 — 4배라 줄이면 매끈)
    def polys(self, polys, v=1.0):
        im = Image.new("L", (self.W, self.H), 0)
        d = ImageDraw.Draw(im)
        for pts in polys:
            if len(pts) >= 3:
                d.polygon([(x * self.ss, y * self.ss) for x, y in pts], fill=255)
        return np.asarray(im, np.float32) * (v / 255.0)

    def poly(self, pts, v=1.0):
        return self.polys([pts], v)

    def ellipse(self, cx, cy, rx, ry=None, v=1.0):
        ry = rx if ry is None else ry
        im = Image.new("L", (self.W, self.H), 0)
        ImageDraw.Draw(im).ellipse([(cx - rx) * self.ss, (cy - ry) * self.ss, (cx + rx) * self.ss, (cy + ry) * self.ss], fill=255)
        return np.asarray(im, np.float32) * (v / 255.0)

    def disc_field(self, cx, cy, r, soft=0.0):
        """둥근 원 (거리장 — 가장자리 soft 만큼 부드럽게)"""
        X, Y = self.grid()
        d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        e = max(0.5 / self.ss, soft)
        return clamp01((r - d) / e + 0.5) if soft <= 0 else smooth(r + e, r - e, d)

    def radial(self, cx, cy, r, power=1.0, sy=1.0):
        """가운데 1 → r 에서 0 (부드러운 빛 덩이)"""
        X, Y = self.grid()
        d = np.sqrt((X - cx) ** 2 + ((Y - cy) / sy) ** 2) / max(1e-6, r)
        return clamp01(1 - d) ** power

    # ---- 거리장 줄 (가늘어짐 · 밝기 변화 · 부드러운 줄)
    def line_field(self, pts, pad):
        """pts 줄까지의 거리 d · 줄을 따라 간 몫 s (0 ~ 1) — 줄 둘레 pad 안만 셈 (나머지 d = 큰 값)"""
        P = np.asarray(pts, np.float64)
        d = np.full((self.H, self.W), 1e9, np.float32)
        s = np.zeros((self.H, self.W), np.float32)
        if len(P) < 2:
            return d, s
        seg = np.hypot(*(P[1:] - P[:-1]).T)
        cum = np.concatenate([[0.0], np.cumsum(seg)])
        total = cum[-1] if cum[-1] > 0 else 1.0
        X, Y = self.grid()
        ss = self.ss
        for i in range(len(P) - 1):
            ax, ay = P[i]
            bx, by = P[i + 1]
            x0 = max(0, int((min(ax, bx) - pad) * ss))
            x1 = min(self.W, int(math.ceil((max(ax, bx) + pad) * ss)) + 1)
            y0 = max(0, int((min(ay, by) - pad) * ss))
            y1 = min(self.H, int(math.ceil((max(ay, by) + pad) * ss)) + 1)
            if x0 >= x1 or y0 >= y1:
                continue
            xs, ys = X[y0:y1, x0:x1], Y[y0:y1, x0:x1]
            vx, vy = bx - ax, by - ay
            L2 = vx * vx + vy * vy
            if L2 < 1e-12:
                t = np.zeros_like(xs)
            else:
                t = np.clip(((xs - ax) * vx + (ys - ay) * vy) / L2, 0, 1)
            dd = np.sqrt((xs - (ax + t * vx)) ** 2 + (ys - (ay + t * vy)) ** 2)
            sub_d = d[y0:y1, x0:x1]
            sub_s = s[y0:y1, x0:x1]
            m = dd < sub_d
            sub_d[m] = dd[m]
            sub_s[m] = ((cum[i] + t * seg[i]) / total)[m]
        return d, s

    def stroke(self, pts, w0, w1=None, a0=1.0, a1=None, taper=0.0, soft=0.0, cap=True, wfn=None, afn=None):
        """줄 (굵기 w0 → w1 · 밝기 a0 → a1). taper > 0 이면 양 끝이 sin^taper 로 뾰족. soft > 0 이면 가장자리를 그만큼 흐리게.
        wfn(s) · afn(s) 를 주면 그 함수로 굵기 · 밝기 (s = 줄을 따라 간 몫)."""
        w1 = w0 if w1 is None else w1
        a1 = a0 if a1 is None else a1
        wmax = max(w0, w1) if wfn is None else max(wfn(i / 32) for i in range(33))
        d, s = self.line_field(pts, wmax / 2 + soft * 2 + 2)
        w = (w0 + (w1 - w0) * s) if wfn is None else wfn(s)
        if taper > 0:
            w = w * np.clip(np.sin(np.pi * np.clip(s, 0, 1)), 0, 1) ** taper
        a = (a0 + (a1 - a0) * s) if afn is None else afn(s)
        half = w / 2
        if soft > 0:
            cov = smooth(half + soft, half - soft * 0.25, d)
        else:
            cov = clamp01((half - d) * self.ss + 0.5)
        if not cap:
            # 끝을 자름 (끝 점 바깥으로 둥글게 나가지 않음)
            P = np.asarray(pts, np.float64)
            for (px, py), (qx, qy) in ((P[0], P[1]), (P[-1], P[-2])):
                X, Y = self.grid()
                vx, vy = qx - px, qy - py
                L = math.hypot(vx, vy) or 1
                cov = cov * clamp01(((X - px) * vx + (Y - py) * vy) / L * self.ss + 0.5)
        return cov * a

    def glowline(self, pts, sigma, a=1.0, afn=None):
        """부드러운 빛 줄 (가우스 단면)"""
        d, s = self.line_field(pts, sigma * 3.5)
        aa = a if afn is None else afn(s)
        return np.exp(-0.5 * (d / sigma) ** 2) * aa

    def down(self, a):
        """4배 → 1배 (LANCZOS)"""
        im = Image.fromarray(np.ascontiguousarray(a, np.float32), "F").resize((self.w, self.h), Image.LANCZOS)
        return clamp01(np.asarray(im, np.float32))


def bez(p0, p1, p2, n=16, p3=None):
    """2차 (p3 가 있으면 3차) 베지어 곡선 점들"""
    out = []
    for i in range(n + 1):
        t = i / n
        if p3 is None:
            a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
            out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
        else:
            a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
            out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def taper_poly(pts, w_fn):
    """줄 pts 를 따라 굵기 w_fn(t) (반쪽 폭) 인 다각형"""
    n = len(pts)
    left, right = [], []
    for i in range(n):
        x, y = pts[i]
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n - 1:
            dx, dy = x - pts[i - 1][0], y - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        L = math.hypot(dx, dy) or 1
        px, py = -dy / L, dx / L
        w = w_fn(i / (n - 1))
        left.append((x + px * w, y + py * w))
        right.append((x - px * w, y - py * w))
    return left + right[::-1]


def star_pts(cx, cy, r, n=4, thin=0.18, rot=0.0, r2=None):
    """n 뿔 별 (rot 라디안). r2 를 주면 뿔이 번갈아 r / r2"""
    pts = []
    for i in range(n * 2):
        a = rot + i * math.pi / n - math.pi / 2
        if i % 2 == 0:
            rr = r if (r2 is None or (i // 2) % 2 == 0) else r2
        else:
            rr = r * thin
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


def sparkle(pad, cx, cy, r, a=1.0, rot=0.0, thin=0.13, diag=0.5, core=True):
    """반짝임: 가는 네 뿔 별 + 대각선 짧은 뿔 + 가운데 점 (4배 그림판 배열)"""
    out = pad.poly(star_pts(cx, cy, r, 4, thin, rot), a)
    if diag > 0:
        out = np.maximum(out, pad.poly(star_pts(cx, cy, r * diag, 4, thin * 1.4, rot + math.pi / 4), a * 0.8))
    out = np.maximum(out, pad.disc_field(cx, cy, r * (0.16 if core else 0.22)) * a)
    return out


# ============================================================ 빛 · 심지 마무리 (1배)

def bloom(body, radii, gain=1.0):
    """몸 + 여러 반지름의 빛 무리 (빛 장면)"""
    out = body.copy()
    for r, k in radii:
        out = screen(out, gblur(body, r) * k * gain)
    return out


def edge_fade(a, m=1.5):
    """칸 가장자리 m 점을 0 으로 (흐림이 칸 끝에서 잘려 보이지 않게)"""
    h, w = a.shape
    ys = (np.arange(h) + 0.5)
    xs = (np.arange(w) + 0.5)
    wy = np.clip(np.minimum(ys, h - ys) / m, 0, 1)
    wx = np.clip(np.minimum(xs, w - xs) / m, 0, 1)
    return a * (wy[:, None] * wx[None, :]).astype(np.float32)


class Sprite:
    def __init__(self, name, facing, cw, ch, frames, job, glows, cores, cols=None, zoom=0, layout=None):
        self.name, self.facing, self.cw, self.ch, self.frames, self.job = name, facing, cw, ch, frames, job
        self.glows, self.cores = glows, cores
        self.cols = cols
        self.zoom = zoom
        self.layout = layout  # rfx_jhits 처럼 직업 줄이 있으면 [(job, glow cells, core cells)]


def to_sheet(cells, cols, cw, ch):
    rows = (len(cells) + cols - 1) // cols
    a = np.zeros((rows * ch, cols * cw), np.float32)
    for i, c in enumerate(cells):
        r, k = divmod(i, cols)
        a[r * ch:(r + 1) * ch, k * cw:(k + 1) * cw] = edge_fade(np.nan_to_num(c))
    alpha = Image.fromarray((clamp01(a) * 255 + 0.5).astype(np.uint8), "L")
    white = Image.new("L", alpha.size, 255)
    return Image.merge("RGBA", (white, white, white, alpha))


def auto_cols(n2, cw, ch):
    """glow + core 칸 n2 개를 4096 점 안에서 되도록 네모나게"""
    best = None
    for cols in range(1, n2 + 1):
        rows = (n2 + cols - 1) // cols
        if cols * cw > 4096 or rows * ch > 4096:
            continue
        score = abs(cols * cw - rows * ch) + (cols * rows - n2) * min(cw, ch)
        if best is None or score < best[0]:
            best = (score, cols)
    return best[1]


def save_sprite(sp):
    n = sp.frames
    if sp.layout is None:
        cells = list(sp.glows) + list(sp.cores)
        cols = sp.cols or auto_cols(len(cells), sp.cw, sp.ch)
    else:
        cells = []
        for g, c in zip(sp.glows, sp.cores):
            cells += list(g) + list(c)
        cols = sp.cols
    for c in cells:
        assert c.shape == (sp.ch, sp.cw), (sp.name, c.shape)
    img = to_sheet(cells, cols, sp.cw, sp.ch)
    assert img.size[0] <= 4096 and img.size[1] <= 4096, (sp.name, img.size)
    save(sp.name, img, sp.cw, sp.ch, sp.facing)
    return img


# ============================================================ 확인용 그림

BG = np.array([16, 20, 24], np.float32) / 255.0


def tint_comp(glow, core, ca, cb):
    """어두운 바탕 위에 빛 (색 A) → 심지 (색 B) 를 섞어 올림 (게임의 반투명 칠과 같음)"""
    g = edge_fade(glow)[..., None]
    c = edge_fade(core)[..., None]
    out = BG[None, None, :] * (1 - g) + np.array(ca, np.float32)[None, None, :] * g
    out = out * (1 - c) + np.array(cb, np.float32)[None, None, :] * c
    return Image.fromarray((clamp01(out) * 255 + 0.5).astype(np.uint8), "RGB")


def label(img, text, xy):
    d = ImageDraw.Draw(img)
    d.text(xy, text, fill=(120, 130, 140))


def preview_sprite(sp, folder):
    """그림마다 한 장: 장면 전부 (빛 = 색 A · 심지 = 색 B · 어두운 바탕) + 한 장면 2배. 직업 줄이 있으면 (rfx_jhits) 줄마다 그 직업 색"""
    gap = 6
    bgc = tuple(int(v * 255) for v in BG)
    rows_src = [(sp.job, sp.glows, sp.cores)] if sp.layout is None else list(zip(JOBS, sp.glows, sp.cores))
    cw, ch = sp.cw, sp.ch
    zf = sp.zoom or 0
    blocks = []
    for job, gl, co in rows_src:
        ca, cb = COLORS[job]
        n = len(gl)
        if sp.layout is not None:
            per_row = n
        else:
            per_row = max(1, min(n, 1100 // (cw + gap)))
        rr = (n + per_row - 1) // per_row
        gw = per_row * (cw + gap) + gap
        gh = rr * (ch + gap + 12) + gap
        zoom = tint_comp(gl[zf], co[zf], ca, cb).resize((cw * 2, ch * 2), Image.BICUBIC)
        side = sp.layout is not None
        if side:
            bw, bh = gw + zoom.size[0] + gap * 2, max(gh, zoom.size[1] + gap * 2 + 12)
        else:
            bw, bh = max(gw, zoom.size[0] + gap * 2), gh + zoom.size[1] + gap + 14
        block = Image.new("RGB", (bw, bh), bgc)
        for i in range(n):
            r, k = divmod(i, per_row)
            x, y = gap + k * (cw + gap), gap + r * (ch + gap + 12) + 12
            block.paste(tint_comp(gl[i], co[i], ca, cb), (x, y))
            label(block, f"{i}", (x + 2, y - 11))
        if side:
            block.paste(zoom, (gw + gap, gap + 12))
            label(block, f"{job} - frame {zf} x2", (gw + gap, 1))
        else:
            block.paste(zoom, (gap, gh + 12))
            label(block, f"{job} - frame {zf} x2", (gap, gh))
        blocks.append(block)
    W = max(b.size[0] for b in blocks)
    H = sum(b.size[1] for b in blocks) + 16
    out = Image.new("RGB", (W, H), bgc)
    label(out, f"{sp.name}  {sp.facing}  {cw}x{ch}  x{sp.frames}", (4, 2))
    y = 16
    for b in blocks:
        out.paste(b, (0, y))
        y += b.size[1]
    path = os.path.join(folder, f"{sp.name}.png")
    out.save(path)
    return path


# ============================================================ 그림들

SPRITES = {}


def sprite(name):
    def deco(fn):
        SPRITES[name] = fn
        return fn
    return deco


def run_frames(fn, frames):
    glows, cores = [], []
    for f in range(frames):
        g, c = fn(f)
        glows.append(g)
        cores.append(c)
    return glows, cores


# ------------------------------------------------------------ 공용 그림 도움

def obj_glow(pad, body_ss, core1, halo=((1.5, 0.55), (4.0, 0.42), (9.0, 0.28)), body_k=0.88, core_bloom=((2.0, 0.5), (5.0, 0.3))):
    """물체의 빛 장면: 몸 (4배 → 1배) · 여러 겹 빛 무리 · 심지의 밝은 것 둘레 번짐"""
    body = pad.down(body_ss)
    g = bloom(body * body_k, ())
    sil = pad.down(clamp01(body_ss * 4))
    for r, k in halo:
        g = screen(g, gblur(sil, r) * k)
    for r, k in core_bloom:
        g = screen(g, gblur(core1, r) * k)
    return g


def rim_of(mask_ss, width, ss=SS):
    """모양 안쪽 가장자리 띠 (칸 점 width) — 1 (가장자리) → 0"""
    d = inner_dist(mask_ss, ss)
    return clamp01(1 - d / max(1e-6, width)) * (mask_ss > 0.5)


# ------------------------------------------------------------ 모양 빛 (거리장 비탈 · 번개 · 지어낸 글자)

def bevel_light(mask_ss, depth, light=(-0.55, -0.83), ss=SS):
    """모양의 비탈 빛: 가장자리에서 depth 점 안쪽까지 비탈 — 빛 쪽 (light = 빛이 오는 화면 방향) 비탈은 lit, 반대는 dark (0 ~ 1)"""
    d = inner_dist(mask_ss, ss)
    h = clamp01(d / max(1e-6, depth))
    h = h * h * (3 - 2 * h)
    gy, gx = np.gradient(h)
    n = np.sqrt(gx * gx + gy * gy) + 1e-6
    # 비탈 면의 바깥 방향 = -grad
    lx, ly = light
    dot = (-gx * lx - gy * ly) / n
    slope = clamp01(n * ss * depth / 1.6)
    inside = mask_ss > 0.5
    return clamp01(dot) * slope * inside, clamp01(-dot) * slope * inside, h * inside


def bolt_pts(rnd, p0, p1, disp=0.22, levels=5, spread=0.55):
    """p0 → p1 번개 (가운데 점 밀기)"""
    pts = [p0, p1]
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    amp = L * disp
    for _ in range(levels):
        new = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            dx, dy = b[0] - a[0], b[1] - a[1]
            ll = math.hypot(dx, dy) or 1
            off = rnd.uniform(-1, 1) * amp
            new += [(mx - dy / ll * off, my + dx / ll * off), b]
        pts = new
        amp *= spread
    return pts


# 지어낸 각진 글자 (실제 룬 문자가 아님): 단위 상자 (0 ~ 1) 안의 꺾은 줄들 · ("d", x, y) = 작은 마름모 점
GLYPHS = [
    [[(0.5, 0.0), (0.5, 1.0)], [(0.5, 0.16), (0.82, 0.44), (0.5, 0.72), (0.18, 0.44), (0.5, 0.16)], [(0.28, 0.9), (0.72, 0.9)]],
    [[(0.18, 0.1), (0.64, 0.5), (0.18, 0.9)], [(0.18, 0.33), (0.4, 0.5), (0.18, 0.67)], ("d", 0.86, 0.5)],
    [[(0.12, 0.08), (0.88, 0.08), (0.5, 0.58), (0.12, 0.08)], [(0.5, 0.58), (0.5, 1.0)], [(0.5, 0.8), (0.78, 0.94)]],
    [[(0.14, 0.92), (0.14, 0.08), (0.86, 0.08), (0.86, 0.74), (0.4, 0.74), (0.4, 0.34), (0.63, 0.34), (0.63, 0.54)]],
    [[(0.2, 0.06), (0.8, 0.06), (0.5, 0.5), (0.2, 0.06)], [(0.5, 0.5), (0.8, 0.94), (0.2, 0.94), (0.5, 0.5)], [(0.0, 0.5), (0.24, 0.5)], [(0.76, 0.5), (1.0, 0.5)]],
    [[(0.5, 0.1), (0.88, 0.5), (0.5, 0.9), (0.12, 0.5), (0.5, 0.1)], [(0.5, 0.3), (0.5, 0.7)]],
    [[(0.28, 0.06), (0.72, 0.06), (0.72, 0.38), (0.28, 0.38), (0.28, 0.06)], [(0.5, 0.38), (0.5, 1.0)], [(0.5, 0.68), (0.78, 0.68)], [(0.5, 0.88), (0.7, 0.88)]],
    [[(0.18, 0.04), (0.18, 0.96)], [(0.82, 0.04), (0.82, 0.96)], [(0.18, 0.22), (0.82, 0.42), (0.18, 0.62), (0.82, 0.82)]],
    [[(0.82, 0.08), (0.36, 0.08), (0.14, 0.5), (0.36, 0.92), (0.82, 0.92)], [(0.6, 0.32), (0.76, 0.5), (0.6, 0.68), (0.44, 0.5), (0.6, 0.32)]],
    [[(0.06, 0.5), (0.94, 0.5)], [(0.24, 0.12), (0.44, 0.34)], [(0.56, 0.66), (0.76, 0.88)], ("d", 0.78, 0.18), ("d", 0.22, 0.82)],
    [[(0.14, 1.0), (0.14, 0.32), (0.5, 0.04), (0.86, 0.32), (0.86, 1.0)], [(0.5, 0.38), (0.5, 0.76)]],
    [[(0.5, 0.0), (0.76, 0.22), (0.5, 0.44), (0.24, 0.22), (0.5, 0.0)], [(0.5, 0.56), (0.76, 0.78), (0.5, 1.0), (0.24, 0.78), (0.5, 0.56)], [(0.5, 0.44), (0.5, 0.56)]],
]


def glyph_lines(k, cx, cy, w, h, ang=0.0):
    """글자 k 의 줄들 (칸 좌표) — (cx, cy) 가운데 · 폭 w · 높이 h · ang 만큼 돌림"""
    xf = Xf(cx, cy, ang)
    lines, dots = [], []
    for st in GLYPHS[k % len(GLYPHS)]:
        if isinstance(st, tuple) and st and st[0] == "d":
            dots.append(xf.p((st[1] - 0.5) * w, (st[2] - 0.5) * h))
        else:
            lines.append(xf([((x - 0.5) * w, (y - 0.5) * h) for x, y in st]))
    return lines, dots


def glyph_mask(pad, k, cx, cy, w, h, lw, ang=0.0, soft=0.0):
    lines, dots = glyph_lines(k, cx, cy, w, h, ang)
    out = pad.zeros()
    for ln in lines:
        out = np.maximum(out, pad.stroke(ln, lw, soft=soft))
    for x, y in dots:
        r = lw * 1.25
        out = np.maximum(out, pad.poly([(x, y - r), (x + r, y), (x, y + r), (x - r, y)]))
    return out


# ------------------------------------------------------------ rfx_jsword2 (환영 장검 — 오른쪽이 칼끝)

def sword_geom(W=256, H=64):
    cy = H / 2
    g = {"cy": cy}
    tip = W - 7.0
    b0 = 60.0
    bl = tip - b0
    top, bot = [], []
    n = 90
    for i in range(n + 1):
        t = i / n
        x = b0 + bl * t
        if t > 0.8:
            u = (t - 0.8) / 0.2
            w = (6.4 - 1.4 * 0.8) * (1 - u ** 1.35) ** 0.85
        else:
            w = 6.4 - 1.4 * t
        top.append((x, cy - w))
        bot.append((x, cy + w))
    g["blade"] = top + bot[::-1]
    g["blade_top"], g["blade_bot"] = top, bot
    g["b0"], g["tip"] = b0, tip
    gx = 54.5
    g["gx"] = gx
    # 코등이 가운데 덩이 (둥근 네모) · 날 위로 덮는 뾰족 판 (랑겟)
    blk = []
    for i in range(24):
        a = i / 24 * TAU
        blk.append((gx + 4.2 * np.sign(math.cos(a)) * abs(math.cos(a)) ** 0.5, cy + 7.2 * np.sign(math.sin(a)) * abs(math.sin(a)) ** 0.5))
    g["block"] = blk
    g["langet"] = [(gx + 2, cy - 5.6), (gx + 9.5, cy - 3.4), (gx + 15.5, cy), (gx + 9.5, cy + 3.4), (gx + 2, cy + 5.6)]
    # 코등이 날개 (가운데에서 바깥 · 칼끝 쪽으로 휘며 가늘어짐) + 끝 구슬
    quil = []
    fin = []
    for side in (-1, 1):
        pts = []
        for i in range(16):
            t = i / 15
            pts.append((gx + 1.0 + 7.5 * t ** 2.4, cy + side * (5.5 + 16.0 * t)))
        quil.append(taper_poly(pts, lambda t: 2.9 * (1 - 0.62 * t)))
        fin.append((pts[-1][0] + 0.6, pts[-1][1] + side * 1.6))
        g["qspine%d" % (side + 1)] = pts
    g["quil"], g["fin"] = quil, fin
    # 손잡이 (가운데가 조금 부푼 원통)
    g0, g1 = 21.0, 50.5
    gt, gb = [], []
    for i in range(21):
        t = i / 20
        x = g0 + (g1 - g0) * t
        w = 3.0 + 0.45 * math.sin(math.pi * t)
        gt.append((x, cy - w))
        gb.append((x, cy + w))
    g["grip"] = gt + gb[::-1]
    g["grip_x"] = (g0, g1)
    g["collar"] = [(g0 - 3.5, cy - 4.4), (g0 + 0.8, cy - 4.4), (g0 + 0.8, cy + 4.4), (g0 - 3.5, cy + 4.4)]
    g["collar2"] = [(g1 - 0.5, cy - 4.0), (g1 + 2.5, cy - 4.6), (g1 + 2.5, cy + 4.6), (g1 - 0.5, cy + 4.0)]
    px = 11.0
    g["px"] = px
    g["pommel"] = [(px - 6.8, cy), (px - 1.0, cy - 6.4), (px + 1.2, cy - 6.4), (px + 6.8, cy), (px + 1.2, cy + 6.4), (px - 1.0, cy + 6.4)]
    g["spike"] = [(px - 6.0, cy - 1.4), (px - 9.8, cy), (px - 6.0, cy + 1.4)]
    return g


def blade_shade(pad, mask, cy, x0, x1, half, groove=True, light=0.0):
    """날 (가로) 의 빛 몸 · 심지: 위 비탈은 밝고 아래 비탈은 어둡게, 가운데 홈, 날 줄"""
    X, Y = pad.grid()
    v = (Y - cy) / np.maximum(0.5, half(X))  # -1 (위 날) ~ 1 (아래 날)
    upper = clamp01(-v)
    body = mask * (0.66 + 0.3 * upper ** 0.7 - 0.1 * clamp01(v))
    core = np.zeros_like(mask)
    if groove:
        gl = (X > x0 + 2) * (X < x0 + (x1 - x0) * 0.68)
        gw = 1.15
        gz = np.exp(-0.5 * ((Y - cy) / gw) ** 2) * gl
        body = body * (1 - 0.42 * gz)
    # 위 비탈: 부드러운 빛 (심지 쪽 색으로 기욺)
    core = np.maximum(core, mask * upper ** 1.4 * (0.34 + light))
    return body, core


def sword_frame(f, frames=4, W=256, H=64):
    pad = Pad(W, H)
    g = sword_geom(W, H)
    X, Y = pad.grid()
    cy = g["cy"]
    b0, tip = g["b0"], g["tip"]
    gx = g["gx"]
    blade = pad.poly(g["blade"])
    block = pad.poly(g["block"])
    langet = pad.poly(g["langet"])
    quil = pad.polys(g["quil"])
    fins = np.maximum.reduce([pad.disc_field(x, y, 1.9) for x, y in g["fin"]])
    guard = np.maximum.reduce([block, quil, fins])
    grip = pad.poly(g["grip"])
    collar = np.maximum(pad.poly(g["collar"]), pad.poly(g["collar2"]))
    pommel = np.maximum(pad.poly(g["pommel"]), pad.poly(g["spike"]))

    def half(x):
        t = np.clip((x - b0) / (tip - b0), 0, 1)
        return np.where(t > 0.8, (6.4 - 1.12) * (1 - np.clip((t - 0.8) / 0.2, 0, 1) ** 1.35) ** 0.85, 6.4 - 1.4 * t)

    bbody, bcore = blade_shade(pad, blade, cy, b0, tip, half)
    # ---- 빛 몸
    body = np.maximum.reduce([bbody, guard * 0.86, langet * 0.92, grip * 0.66, collar * 0.84, pommel * 0.86])
    body = np.where(langet > 0.5, langet * 0.92, body)
    # ---- 심지
    core = bcore * (1 - langet)
    d_in = inner_dist(blade)
    edge = clamp01(1 - d_in / 2.2) ** 1.5 * (blade > 0.5)
    core = np.maximum(core, edge * 0.82 * (1 - langet))
    for pts in (g["blade_top"], g["blade_bot"]):
        core = np.maximum(core, pad.stroke(pts, 0.5, a0=1.0) * blade)
    # 홈: 아래 벽에 빛 줄 · 홈 끝에서 칼끝까지 가는 능선
    fl_end = b0 + (tip - b0) * 0.68
    core = np.maximum(core, pad.stroke([(b0 + 12, cy + 1.25), (fl_end, cy + 0.9)], 0.75, 0.3, a0=0.95, a1=0.6))
    core = np.maximum(core, pad.stroke([(b0 + 12, cy - 1.35), (fl_end, cy - 1.0)], 0.45, 0.2, a0=0.5, a1=0.25))
    core = np.maximum(core, pad.stroke([(fl_end - 4, cy), (tip - 2, cy)], 0.55, 0.2, a0=0.3, a1=0.8))
    # 코등이: 테 · 날개 등줄 · 랑겟 테 · 보석
    core = np.maximum(core, rim_of(guard, 1.0) * 0.9)
    for k in (0, 2):
        core = np.maximum(core, pad.stroke(g["qspine%d" % k], 0.6, 0.3, a0=0.65, a1=0.3) * quil)
    core = np.maximum(core, rim_of(langet, 0.9) * 0.95)
    core = np.maximum(core, langet * clamp01((cy - Y) / 5) * 0.45)
    gem = pad.poly([(gx - 2.8, cy), (gx, cy - 4.2), (gx + 2.8, cy), (gx, cy + 4.2)])
    core = np.maximum(core, gem * (0.5 + 0.45 * clamp01((cy - Y + 1) / 4)))
    core = np.maximum(core, rim_of(gem, 0.7))
    for x, y in g["fin"]:
        core = np.maximum(core, pad.disc_field(x - 0.4, y - 0.5, 0.9) * 0.95)
    # 손잡이: 감은 끈 · 테
    g0, g1 = g["grip_x"]
    wraps = np.zeros_like(blade)
    x = g0 + 1.2
    while x < g1 - 1:
        wraps = np.maximum(wraps, pad.stroke([(x, cy - 4.0), (x + 3.0, cy + 4.0)], 0.75, a0=0.8))
        x += 3.4
    core = np.maximum(core, wraps * grip * (0.55 + 0.45 * clamp01((cy - Y + 3) / 6)))
    core = np.maximum(core, rim_of(grip, 0.7) * 0.5)
    core = np.maximum(core, rim_of(collar, 0.9) * 0.85)
    # 자루 끝 보석: 면 (위 왼쪽 면이 밝음) · 테
    px = g["px"]
    face = pad.poly([(px - 6.8, cy), (px - 1.0, cy - 6.4), (px + 0.2, cy - 0.5)]) * 0.7
    face = np.maximum(face, pad.poly([(px - 1.0, cy - 6.4), (px + 1.2, cy - 6.4), (px + 6.8, cy), (px + 0.2, cy - 0.5)]) * 0.42)
    face = np.maximum(face, pad.poly([(px - 6.8, cy), (px + 0.2, cy - 0.5), (px - 1.0, cy + 6.4)]) * 0.18)
    core = np.maximum(core, face * pommel)
    core = np.maximum(core, rim_of(pommel, 0.85) * 0.95)
    # ---- 날을 따라 흐르는 빛 (비스듬한 띠 · 되풀이 → 이어 돌림)
    L = tip - b0 + 48
    sx = b0 - 24 + L * (f + 0.5) / frames
    swp = np.zeros_like(blade)
    for kk in (-1, 0, 1):
        u = (X - (sx + kk * L)) + (Y - cy) * 0.85
        swp = np.maximum(swp, np.exp(-0.5 * (u / 6.5) ** 2) + 0.35 * np.exp(-0.5 * ((u + 14) / 2.2) ** 2))
    core = np.maximum(core, clamp01(swp) * 0.9 * np.maximum(blade, np.maximum(guard, langet) * 0.6))
    # 반짝임: 칼끝 (장마다 크기 · 돌기가 다름) · 코등이 보석
    tw = [1.0, 0.5, 0.78, 0.35][f]
    core = np.maximum(core, sparkle(pad, tip - 5, cy - 1.0, 5.5 + 4.5 * tw, 1.0, rot=0.18 * f))
    core = np.maximum(core, sparkle(pad, gx + 0.3, cy - 1.2, 3.5 + 3.0 * (1 - tw), 0.9, diag=0.0))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.2, 0.5), (3.0, 0.36), (6.5, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.22)))
    return glow1, core1


@sprite("rfx_jsword2")
def make_sword2():
    glows, cores = run_frames(sword_frame, 4)
    return Sprite("rfx_jsword2", "lookat_direction", 256, 64, 4, "swordsman", glows, cores, cols=2)


# ------------------------------------------------------------ rfx_jgreatsword (거대한 대검 — 오른쪽이 칼끝, 날 가운데 새김 9 개)

def gs_geom(W=512, H=128):
    cy = H / 2
    g = {"cy": cy}
    b0, tip = 128.0, W - 9.0
    hw0, hw1 = 27.0, 23.5
    xa = 430.0
    top, bot = [], []
    n = 60
    for i in range(n + 1):
        t = i / n
        x = b0 + (xa - b0) * t
        w = hw0 + (hw1 - hw0) * t
        top.append((x, cy - w))
        bot.append((x, cy + w))
    # 각진 칼끝: 꺾인 두 마디
    k1 = (xa + 40, cy - hw1 * 0.62)
    top += [k1, (tip, cy)]
    bot += [(xa + 40, cy + hw1 * 0.62), (tip, cy)]
    g["blade"] = top + bot[::-1][1:]
    g["top"], g["bot"] = top, bot
    g["b0"], g["tip"], g["xa"], g["hw0"], g["hw1"] = b0, tip, xa, hw0, hw1
    # 가운데 홈 (새김 자리)
    c0, c1, ch = 146.0, 424.0, 10.5
    g["chan"] = [(c0, cy), (c0 + 9, cy - ch), (c1 - 9, cy - ch), (c1, cy), (c1 - 9, cy + ch), (c0 + 9, cy + ch)]
    g["chan_x"] = (c0, c1)
    # 코등이: 가운데 덩이 + 칼끝 쪽으로 기운 각진 날개 둘
    gx0, gx1 = 103.0, 129.0
    g["block"] = [(gx0, cy - 17), (gx0 + 6, cy - 22), (gx1 - 2, cy - 22), (gx1 + 6, cy - 12), (gx1 + 6, cy + 12), (gx1 - 2, cy + 22), (gx0 + 6, cy + 22), (gx0, cy + 17)]
    wings = []
    for sd in (-1, 1):
        w = [(gx0 + 2, cy + sd * 18), (gx0 - 5, cy + sd * 38), (gx0 + 6, cy + sd * 57), (gx0 + 15, cy + sd * 44), (gx1 + 3, cy + sd * 20)]
        wings.append(w)
    g["wings"] = wings
    # 손잡이 · 고리 둘 · 자루 끝
    g["grip"] = [(44, cy - 7.2), (101, cy - 8.2), (101, cy + 8.2), (44, cy + 7.2)]
    g["rings"] = [[(92, cy - 10), (100, cy - 10.5), (100, cy + 10.5), (92, cy + 10)], [(42, cy - 9.5), (50, cy - 9.2), (50, cy + 9.2), (42, cy + 9.5)]]
    px = 27.0
    g["px"] = px
    g["pommel"] = [(px - 14, cy - 6), (px - 6, cy - 14), (px + 7, cy - 14), (px + 15, cy - 7), (px + 15, cy + 7), (px + 7, cy + 14), (px - 6, cy + 14), (px - 14, cy + 6)]
    g["spike"] = [(px - 13, cy - 5), (px - 21, cy), (px - 13, cy + 5)]
    return g


def gs_frame(f, frames=4, W=512, H=128):
    pad = Pad(W, H)
    g = gs_geom(W, H)
    X, Y = pad.grid()
    cy, b0, tip, xa = g["cy"], g["b0"], g["tip"], g["xa"]
    blade = pad.poly(g["blade"])
    chan = pad.poly(g["chan"])
    block = pad.poly(g["block"])
    wings = pad.polys(g["wings"])
    guard = np.maximum(block, wings)
    grip = pad.poly(g["grip"])
    rings = pad.polys(g["rings"])
    pommel = np.maximum(pad.poly(g["pommel"]), pad.poly(g["spike"]))

    def half(x):
        t = np.clip((x - b0) / (xa - b0), 0, 1)
        w = g["hw0"] + (g["hw1"] - g["hw0"]) * t
        u = np.clip((x - xa) / (tip - xa), 0, 1)
        return np.where(x > xa, g["hw1"] * (1 - u), w)

    v = (Y - cy) / np.maximum(1.0, half(X))
    upper = clamp01(-v)
    # ---- 빛 몸: 위 비탈 밝게 · 아래 어둡게 · 홈은 가라앉음 (새김이 돋보이게)
    body = blade * (0.6 + 0.32 * upper ** 0.7 - 0.08 * clamp01(v))
    body = body * (1 - 0.38 * chan)
    lit_g, dark_g, _ = bevel_light(guard, 3.0)
    body = np.maximum(body, guard * (0.8 + 0.15 * lit_g - 0.2 * dark_g))
    body = np.maximum.reduce([body, grip * 0.62, rings * 0.85, pommel * 0.86])
    # ---- 심지
    core = blade * upper ** 1.5 * 0.3 * (1 - chan)
    d_in = inner_dist(blade)
    # 강한 날 빛: 날 비탈 (바깥 6 점) 그러데이션 + 또렷한 날 줄 + 비탈 경계 줄
    edge = clamp01(1 - d_in / 7.0) ** 1.25 * (blade > 0.5)
    edge_top = edge * (Y < cy)
    core = np.maximum(core, edge * 0.7)
    core = np.maximum(core, edge_top * 0.98)
    core = np.maximum(core, pad.stroke(g["top"], 1.3, a0=1.0) * blade)
    core = np.maximum(core, pad.stroke(g["bot"], 1.0, a0=0.9) * blade)
    bev = np.exp(-0.5 * ((d_in - 7.0) / 0.45) ** 2) * (blade > 0.5) * (X < tip - 30)
    core = np.maximum(core, bev * 0.42)
    # 칼끝 꺾인 능선 (가운데 줄)
    core = np.maximum(core, pad.stroke([(g["chan_x"][1] + 4, cy), (tip - 3, cy)], 1.0, 0.4, a0=0.75, a1=0.95))
    # 홈 테
    core = np.maximum(core, rim_of(chan, 1.0) * 0.55)
    # 새김 9 개: 차례로 맥동 (빛 띠가 칼끝 쪽으로 흐름 · 4 장마다 되풀이)
    c0, c1 = g["chan_x"]
    glyph_core = pad.zeros()
    glyph_glow = pad.zeros()
    for k in range(9):
        gxk = c0 + 22 + (c1 - c0 - 44) * k / 8
        ph = math.cos(TAU * (k / 4.5 - f / frames))
        b = 0.32 + 0.68 * max(0.0, ph) ** 1.6
        m = glyph_mask(pad, [0, 2, 5, 7, 3, 9, 11, 4, 8][k], gxk, cy, 15.0, 15.5, 1.55)
        glyph_core = np.maximum(glyph_core, m * b)
        glyph_glow = np.maximum(glyph_glow, m * b ** 1.5)
    core = np.maximum(core, glyph_core)
    body = np.maximum(body, glyph_glow * 0.95)
    # 코등이: 테 · 비탈 빛 · 보석
    core = np.maximum(core, rim_of(guard, 1.3) * 0.9)
    core = np.maximum(core, lit_g * 0.75)
    gcx = (103.0 + 129.0) / 2 + 2
    gem = pad.poly([(gcx - 7, cy), (gcx, cy - 11), (gcx + 7, cy), (gcx, cy + 11)])
    core = np.maximum(core, gem * (0.45 + 0.5 * clamp01((cy - Y + 2) / 9)))
    core = np.maximum(core, rim_of(gem, 1.0))
    core = np.maximum(core, pad.stroke([(gcx, cy - 11), (gcx, cy + 11)], 0.8, a0=0.6) * gem)
    for wv in g["wings"]:
        core = np.maximum(core, pad.stroke([((wv[0][0] + wv[4][0]) / 2, (wv[0][1] + wv[4][1]) / 2), wv[2]], 1.0, a0=0.6, taper=0.4) * wings)
    # 손잡이: 감은 끈 · 고리 · 자루 끝
    x = 52.0
    wraps = pad.zeros()
    while x < 92:
        wraps = np.maximum(wraps, pad.stroke([(x, cy - 9), (x + 6, cy + 9)], 1.4, a0=0.75))
        x += 6.5
    core = np.maximum(core, wraps * grip * (0.55 + 0.45 * clamp01((cy - Y + 6) / 12)))
    core = np.maximum(core, rim_of(grip, 1.0) * 0.45)
    core = np.maximum(core, rim_of(rings, 1.1) * 0.9)
    lit_p, _, _ = bevel_light(pommel, 4.0)
    core = np.maximum(core, rim_of(pommel, 1.2) * 0.9)
    core = np.maximum(core, lit_p * 0.7)
    pgx = g["px"] + 1
    pgem = pad.poly([(pgx - 5, cy), (pgx, cy - 6), (pgx + 5, cy), (pgx, cy + 6)])
    core = np.maximum(core, pgem * 0.95)
    # ---- 날 줄을 따라 달리는 빛 (위 날 · 장마다 앞으로)
    L = tip - b0
    gxp = b0 + 30 + (L - 40) * ((f + 0.25) / frames)
    run = np.exp(-0.5 * ((X - gxp) / 16.0) ** 2) * edge * 1.0
    core = np.maximum(core, run)
    core = np.maximum(core, sparkle(pad, gxp + 6, cy - half(np.array([gxp]))[0] + 1.5, 11 + 3 * (f % 2), 1.0, rot=0.15 * f))
    # 칼끝 반짝임
    tw = [0.55, 1.0, 0.7, 0.85][f]
    core = np.maximum(core, sparkle(pad, tip - 7, cy, 9 + 6 * tw, 0.95, rot=0.3 + 0.2 * f))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.8, 0.5), (5.0, 0.36), (11.0, 0.22)), body_k=1.0, core_bloom=((2.0, 0.3), (6.0, 0.22)))
    # 새김이 밝을 때 빛 무리
    glow1 = screen(glow1, gblur(pad.down(glyph_glow), 3.0) * 0.6)
    return glow1, core1


@sprite("rfx_jgreatsword")
def make_greatsword():
    glows, cores = run_frames(gs_frame, 4)
    return Sprite("rfx_jgreatsword", "lookat_direction", 512, 128, 4, "swordsman", glows, cores, cols=2)


# ------------------------------------------------------------ 외날 전투 도끼 (rfx_jaxe 돎 · rfx_jaxefly 날아감)

def axe_local():
    """도끼 (로컬: 자루가 +x 쪽 — 머리가 +x 끝, 날이 -y 쪽). 점 단위 대략 1"""
    parts = {}
    top, bot = [], []
    for i in range(31):
        t = i / 30
        x = -60 + 112 * t
        yc = 1.2 * math.sin(math.pi * t)
        w = 4.3 - 0.8 * t
        top.append((x, yc - w))
        bot.append((x, yc + w))
    parts["haft"] = top + bot[::-1]
    parts["haft_mid"] = [(-60 + 112 * i / 30, 1.2 * math.sin(math.pi * i / 30)) for i in range(31)]
    parts["butt"] = [(-68, -4.2), (-65, -6.6), (-57, -6.0), (-55, 0), (-57, 6.0), (-65, 6.6), (-68, 4.2)]
    parts["wrap"] = (-52.0, -22.0)
    parts["langet"] = [(16, -5.0), (31, -5.4), (31, 5.6), (16, 5.2)]
    parts["eye"] = [(30, -10), (51, -10), (54, -6.5), (54, 7.5), (51, 11), (30, 11), (27, 7.5), (27, -6.5)]
    # 날: 눈 → (오목한 목) → 수염 끝 → (볼록한 날) → 앞 끝 → (오목한 목) → 눈
    beard, toe = (6.0, -49.0), (71.0, -57.0)
    throat_b = bez((33, -9.5), (31, -36), beard, 18)
    edge = bez(beard, (34, -86), toe, 30)
    throat_t = bez(toe, (51, -34), (49, -9.5), 18)
    parts["bit"] = throat_b + edge[1:-1] + throat_t
    parts["edge"] = edge
    parts["engr"] = bez((15, -43), (35, -66), (60, -48), 20)
    parts["spike"] = bez((34, 10), (40, 18), (47, 27), 8) + bez((47, 27), (46, 18), (48, 10), 8)[1:]
    parts["rivets"] = [(35, 0.5), (47, 0.5)]
    return parts


def axe_bounds(parts):
    pts = parts["bit"] + parts["haft"] + parts["butt"] + parts["spike"] + parts["eye"]
    # 모든 점을 감싸는 원 (대략 — 격자 찾기)
    best = None
    for cx in np.linspace(-20, 30, 51):
        for cy in np.linspace(-35, 5, 41):
            r = max(math.hypot(x - cx, y - cy) for x, y in pts)
            if best is None or r < best[0]:
                best = (r, cx, cy)
    return best


def axe_draw(pad, xf, light=(-0.55, -0.83), glint=0.0, gl_rot=0.0):
    """도끼 한 자루 (xf: 로컬 → 칸). (빛 몸 4배, 심지 4배, 머리 모양 4배)"""
    P = axe_local()
    X, Y = pad.grid()
    haft = pad.poly(xf(P["haft"]))
    butt = pad.poly(xf(P["butt"]))
    langet = pad.poly(xf(P["langet"]))
    eye = pad.poly(xf(P["eye"]))
    bit = pad.poly(xf(P["bit"]))
    spike = pad.poly(xf(P["spike"]))
    head = np.maximum.reduce([eye, bit, spike])
    sil = np.maximum.reduce([haft, butt, langet, head])
    lx, ly = light
    sc = abs(xf.sx)
    # ---- 빛 몸
    lit_h, dark_h, hh = bevel_light(head, 3.2 * sc)
    lit_s, dark_s, _ = bevel_light(np.maximum(haft, butt), 2.2 * sc)
    body = np.maximum(haft, butt) * (0.66 + 0.2 * lit_s - 0.18 * dark_s)
    body = np.maximum(body, langet * 0.8)
    body = np.where(head > 0.5, head * (0.86 + 0.12 * lit_h - 0.3 * dark_h), body)
    # ---- 심지
    core = pad.zeros()
    # 날 (갈아 낸 날 띠: 날 줄에서 안쪽으로 밝은 그러데이션) · 또렷한 날 줄
    ed = xf(P["edge"])
    de, _ = pad.line_field(ed, 14 * sc)
    band = np.exp(-de / (5.5 * sc)) * bit
    core = np.maximum(core, band * 0.9)
    core = np.maximum(core, pad.stroke(ed, 1.1 * sc, a0=1.0) * bit)
    bevel_line = np.exp(-0.5 * ((de - 8.5 * sc) / (0.5 * sc)) ** 2) * bit
    core = np.maximum(core, bevel_line * 0.45)
    # 볼의 새김: 날 줄과 나란한 가는 호 + 마름모 + 작은 점 둘
    core = np.maximum(core, pad.stroke(xf(P["engr"]), 0.9 * sc, a0=0.6, taper=0.5) * bit)
    dm = xf([(41, -36), (45, -30.5), (41, -25), (37, -30.5)])
    dmm = pad.poly(dm)
    core = np.maximum(core, dmm * 0.95)
    for q in ((41, -19), (41, -42)):
        qx, qy = xf.p(*q)
        core = np.maximum(core, pad.disc_field(qx, qy, 1.1 * sc) * 0.8)
    core = np.maximum(core, rim_of(head, 1.0 * sc) * 0.9)
    core = np.maximum(core, lit_h * 0.7)
    # 눈의 못 · 띠 줄
    for x, y in P["rivets"]:
        cxp, cyp = xf.p(x, y)
        core = np.maximum(core, pad.disc_field(cxp, cyp, 1.7 * sc) * 0.95)
    core = np.maximum(core, pad.stroke(xf([(30, -4), (53, -4)]), 0.7 * sc, a0=0.5) * eye)
    core = np.maximum(core, pad.stroke(xf([(30, 5), (53, 5)]), 0.7 * sc, a0=0.5) * eye)
    # 자루: 감은 끈 · 나뭇결 줄 · 끝 테
    w0, w1 = P["wrap"]
    x = w0
    wraps = pad.zeros()
    while x < w1:
        wraps = np.maximum(wraps, pad.stroke(xf([(x, -5), (x + 3.2, 5)]), 0.9 * sc, a0=0.85))
        x += 3.6
    core = np.maximum(core, wraps * haft)
    core = np.maximum(core, pad.stroke(xf([(p[0], p[1] - 1.2) for p in P["haft_mid"][10:29]]), 0.55 * sc, a0=0.35) * haft)
    core = np.maximum(core, lit_s * 0.55)
    core = np.maximum(core, rim_of(langet, 0.8 * sc) * 0.8)
    core = np.maximum(core, rim_of(butt, 0.9 * sc) * 0.85)
    if glint > 0:
        gx, gy = xf.p(*P["edge"][19])
        core = np.maximum(core, sparkle(pad, gx, gy, 9 * sc * glint, 1.0, rot=gl_rot))
    return body, core, head, sil


def axe_spin_frame(f, frames=8, S=192):
    pad = Pad(S, S)
    r, pcx, pcy = axe_bounds(axe_local())
    sc = (S * 0.43) / r
    c = S / 2
    th = TAU * f / frames  # 화면에서 시계 방향
    base = math.radians(-40)
    xf = Xf(c, c, base + th, sc, sc)
    # 피벗을 둘레 원의 가운데로
    ox, oy = xf.vec(-pcx, -pcy)
    xf = Xf(c + ox, c + oy, base + th, sc, sc)
    body, core, head, sil = axe_draw(pad, xf, glint=[1.0, 0.0, 0.0, 0.55, 0.0, 0.0, 0.8, 0.0][f], gl_rot=0.3 * f)
    # ---- 도는 꼬리: 날이 지나간 넓은 쓸림 (날 안쪽 ~ 바깥 반지름, 뒤로 흐려짐) + 앞 끝 · 수염 끝의 가는 호
    X, Y = pad.grid()
    R = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    TH = np.arctan2(Y - c, X - c)
    P = axe_local()
    pts = [xf.p(*q) for q in P["edge"]]
    rads = [math.hypot(x - c, y - c) for x, y in pts]
    angs = [math.atan2(y - c, x - c) for x, y in pts]
    r_out = max(rads)
    i_out = rads.index(r_out)
    r_in = min(math.hypot(x - c, y - c) for x, y in [xf.p(*q) for q in P["bit"]])
    a_lead = max(angs, key=lambda a: math.cos(a - angs[i_out]) * 0 + ((a - angs[i_out] + math.pi) % TAU))
    a_head = angs[i_out]
    span = math.radians(150)
    dl = np.mod(a_head - TH, TAU)
    k = clamp01(1 - dl / span)
    radial = clamp01((R - r_in) / max(1.0, (r_out - r_in) * 0.6)) * clamp01((r_out + 1.5 - R) / 2.5)
    sweep = radial * k ** 2.0 * 0.5
    trail_g = sweep
    trail_c = pad.zeros()
    for idx, wgt in ((i_out, 1.0),):
        rt, at = rads[idx], angs[idx]
        dl2 = np.mod(at - TH, TAU)
        k2 = clamp01(1 - dl2 / span)
        trail_g = np.maximum(trail_g, np.exp(-0.5 * ((R - rt) / (1.0 + 2.2 * k2)) ** 2) * k2 ** 1.5 * 0.8 * wgt)
        trail_c = np.maximum(trail_c, np.exp(-0.5 * ((R - rt) / (0.4 + 0.55 * k2)) ** 2) * k2 ** 2.0 * 0.9 * wgt)
    trail_g = np.maximum(trail_g, np.exp(-0.5 * ((R - r_out) / 0.9) ** 2) * 0.07)
    trail_g = trail_g * (1 - sil)
    trail_c = trail_c * (1 - sil)
    core = np.maximum(core, trail_c)
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.4, 0.5), (3.8, 0.36), (8.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.2)))
    glow1 = screen(glow1, gblur(pad.down(trail_g), 1.0))
    return glow1, core1


@sprite("rfx_jaxe")
def make_axe():
    glows, cores = run_frames(axe_spin_frame, 8)
    return Sprite("rfx_jaxe", "rotate_xyz", 192, 192, 8, "warrior", glows, cores, cols=4)


def axefly_frame(f, frames=4, W=256, H=128):
    pad = Pad(W, H)
    sc = 1.13
    xf = Xf(232 - 64 * sc, 86, 0.0, sc, sc)
    body, core, head, sil = axe_draw(pad, xf, glint=[0.9, 0.35, 0.65, 0.2][f], gl_rot=0.25 * f)
    # ---- 묵직한 잔상: 머리 모양을 뒤 (왼쪽) 로 끌어 늘임 (1배에서) + 줄무늬
    head1 = pad.down(head)
    sil1 = pad.down(sil)
    Lsm = 120
    sm = np.zeros_like(head1)
    for o in range(1, Lsm):
        k = (1 - o / Lsm) ** 1.7
        sh = np.zeros_like(head1)
        sh[:, :-o] = head1[:, o:]
        sm = np.maximum(sm, sh * k)
    hsil = np.zeros_like(sil1)
    for o in range(1, 70):
        k = (1 - o / 70) ** 2.2 * 0.5
        sh = np.zeros_like(sil1)
        sh[:, :-o] = sil1[:, o:]
        hsil = np.maximum(hsil, sh * k)
    rnd = random.Random(808)
    ys = np.arange(H)[:, None].astype(np.float32)
    xs = np.arange(W)[None, :].astype(np.float32)
    stripe = np.ones((H, W), np.float32) * 0.55
    for i in range(16):
        yy = rnd.uniform(8, 100)
        ph = rnd.uniform(0, TAU)
        amp = rnd.uniform(0.25, 0.6)
        stripe += amp * np.exp(-0.5 * ((ys - yy) / rnd.uniform(0.8, 2.2)) ** 2) * (0.6 + 0.4 * np.cos(xs * 0.05 + ph + TAU * f / frames))
    smear = clamp01(sm * clamp01(stripe)) * (1 - sil1)
    smear = np.maximum(smear, hsil * 0.6 * (1 - sil1))
    # 심지: 날 쪽에서 뒤로 뻗는 가는 바람 줄 (장마다 길이 · 밝기가 바뀜)
    lines = pad.zeros()
    P = axe_local()
    for i, idx in enumerate([2, 5, 8, 11, 14, 17, 20]):
        x0, y0 = xf.p(*P["edge"][idx])
        Ln = 40 + 60 * ((i * 37 + f * 23) % 100) / 100
        a = 0.35 + 0.5 * ((i * 53 + f * 31) % 100) / 100
        lines = np.maximum(lines, pad.stroke([(x0 - 4, y0 + 1.5), (x0 - 4 - Ln, y0 + 1.5)], 1.1, 0.2, a0=a, a1=0.0))
    core = np.maximum(core, lines * (1 - sil))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.4, 0.5), (3.6, 0.36), (7.5, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.2)))
    glow1 = screen(glow1, gblur(smear, 0.8) * 0.9)
    return glow1, core1


@sprite("rfx_jaxefly")
def make_axefly():
    glows, cores = run_frames(axefly_frame, 4)
    return Sprite("rfx_jaxefly", "lookat_direction", 256, 128, 4, "warrior", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jtome (마도서 — 열림 · 책장 넘김 · 닫힘)

class Stack:
    """앞에 그린 것이 뒤를 가림: 빛 몸 (body) · 심지 (core) 를 함께 덮어 그림 (4배)"""

    def __init__(self, pad):
        self.pad = pad
        self.body = pad.zeros()
        self.core = pad.zeros()

    def over(self, mask, body_val, core_arr=None):
        self.body = self.body * (1 - mask) + mask * body_val
        self.core = self.core * (1 - mask)
        if core_arr is not None:
            self.core = np.maximum(self.core, core_arr)

    def add_core(self, arr):
        self.core = np.maximum(self.core, arr)


TOME = dict(cx=128.0, top=78.0, ph=112.0, pw=96.0)


def page_fn(side, ws=1.0, lift=0.0, dx=0.0, dy=0.0, grow=0.0):
    """책장 면의 (t: 등 0 → 바깥 1, v: 위 0 → 아래 1) → 칸 좌표"""
    cx, top, ph, pw = TOME["cx"], TOME["top"], TOME["ph"], TOME["pw"]

    def f(t, v):
        x = cx + side * ((pw + grow) * t * ws + dx * t)
        yt = top - grow * 0.6 + 9.0 * (1 - t) ** 4 - 5.0 * math.sin(math.pi * t) - lift * math.sin(math.pi * 0.5 * t) ** 1.3
        yb = top + ph + grow * 0.6 + 5.0 * (1 - t) ** 4 - 3.0 * math.sin(math.pi * t) - lift * 0.55 * math.sin(math.pi * 0.5 * t) ** 1.3
        return x, yt + (yb - yt) * v + dy * t
    return f


def page_outline(fn, n=24):
    pts = [fn(i / n, 0.0) for i in range(n + 1)]
    pts += [fn(1.0, i / 8) for i in range(1, 9)]
    pts += [fn(1 - i / n, 1.0) for i in range(1, n + 1)]
    pts += [fn(0.0, 1 - i / 8) for i in range(1, 8)]
    return pts


def page_rect(fn, t0, t1, v0, v1, n=16):
    pts = [fn(t0 + (t1 - t0) * i / n, v0) for i in range(n + 1)]
    pts += [fn(t1, v0 + (v1 - v0) * i / 6) for i in range(1, 7)]
    pts += [fn(t1 - (t1 - t0) * i / n, v1) for i in range(1, n + 1)]
    pts += [fn(t0, v1 - (v1 - v0) * i / 6) for i in range(1, 7)]
    return pts


def page_text(pad, fn, seed, v0=0.2, v1=0.9, t0=0.13, t1=0.87, rows=10, lw=1.2, a=0.55, fold=None):
    rnd = random.Random(seed)
    out = pad.zeros()
    for r in range(rows):
        v = v0 + (v1 - v0) * r / max(1, rows - 1)
        t = t0 + (rnd.uniform(0.0, 0.08) if r == 0 else 0.0)
        end = t1 - (rnd.uniform(0.1, 0.4) if r % 4 == 3 else rnd.uniform(0.0, 0.05))
        while t < end - 0.04:
            L = rnd.uniform(0.06, 0.2)
            e = min(end, t + L)
            pts = [fn(t + (e - t) * i / 3, v) for i in range(4)]
            out = np.maximum(out, pad.stroke(pts, lw, a0=a))
            t = e + rnd.uniform(0.025, 0.05)
    return out


def tome_page(pad, st, side, fn, seed, kind, bright=1.0):
    """책장 하나 (앞면): 몸 · 테 · 안쪽 테두리 · 글줄 · (왼쪽) 별자리 그림 / (오른쪽) 첫 글자"""
    m = pad.poly(page_outline(fn))
    core = rim_of(m, 0.9) * 0.75 * bright
    inner = page_rect(fn, 0.08, 0.93, 0.07, 0.94)
    core = np.maximum(core, pad.stroke(inner + [inner[0]], 0.8, a0=0.5 * bright) * m)
    if kind == "chart":
        # 별자리: 큰 네 뿔 별 + 작은 별들을 잇는 가는 줄
        sx, sy = fn(0.5, 0.25)
        stars = [fn(0.22, 0.14), fn(0.36, 0.36), fn(0.66, 0.4), fn(0.8, 0.17), fn(0.6, 0.1)]
        core = np.maximum(core, pad.stroke([stars[0], stars[1], (sx, sy), stars[2], stars[3], stars[4], (sx, sy)], 0.6, a0=0.45 * bright) * m)
        for x, y in stars:
            core = np.maximum(core, pad.poly(star_pts(x, y, 3.2, 4, 0.25), 0.85 * bright))
        core = np.maximum(core, pad.poly(star_pts(sx, sy, 11.0 * min(1, abs(fn(1, 0)[0] - fn(0, 0)[0]) / TOME["pw"] + 0.2), 4, 0.17), 1.0 * bright))
        core = np.maximum(core, pad.stroke(page_rect(fn, 0.15, 0.85, 0.47, 0.47, 6)[:7], 0.7, a0=0.5 * bright))
        core = np.maximum(core, page_text(pad, fn, seed, 0.56, 0.88, rows=6, a=0.5 * bright))
    elif kind == "text":
        # 첫 글자 (네모 안 글자) + 글줄
        q = page_rect(fn, 0.13, 0.33, 0.12, 0.3)
        core = np.maximum(core, pad.stroke(q + [q[0]], 0.8, a0=0.7 * bright))
        gx, gy = fn(0.23, 0.21)
        core = np.maximum(core, glyph_mask(pad, 6, gx, gy, 9, 11, 1.3) * 0.9 * bright)
        core = np.maximum(core, page_text(pad, fn, seed, 0.14, 0.3, t0=0.4, rows=3, a=0.5 * bright))
        core = np.maximum(core, page_text(pad, fn, seed + 1, 0.38, 0.88, rows=8, a=0.5 * bright))
    else:
        core = np.maximum(core, page_text(pad, fn, seed, 0.14, 0.88, rows=11, a=0.48 * bright))
    # 등 쪽이 조금 어두운 종이 몸 (빛 몸) · 심지에 아주 옅은 면 빛
    X, Y = pad.grid()
    gut = clamp01(np.abs(X - TOME["cx"]) / 22.0)
    st.over(m, (0.62 + 0.2 * gut) * bright, core * m + m * 0.1 * gut * bright)
    return m


def tome_cover_front(pad, st, x0, y0, x1, y1, bright=1.0):
    """닫힌 책 앞 표지 (등 = 왼쪽 x0)"""
    w, h = x1 - x0, y1 - y0
    r = 6.0
    pts = []
    for (cx_, cy_, a0) in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r * 0.4, y1 - r * 0.4, 90), (x0 + r * 0.4, y0 + r * 0.4, 180)):
        for i in range(7):
            a = math.radians(a0 + 15 * i)
            rr = r if cx_ > x0 + 10 else r * 0.4
            pts.append((cx_ + math.cos(a) * rr, cy_ + math.sin(a) * rr))
    m = pad.poly(pts)
    lit, dark, _ = bevel_light(m, 4.0)
    core = rim_of(m, 1.2) * 0.9
    core = np.maximum(core, lit * 0.55)
    # 등 띠 · 등의 가로 띠 셋
    spine = pad.poly([(x0, y0 + 1), (x0 + 11, y0 + 1), (x0 + 11, y1 - 1), (x0, y1 - 1)])
    core = np.maximum(core, pad.stroke([(x0 + 11, y0 + 3), (x0 + 11, y1 - 3)], 1.0, a0=0.8))
    for k in range(3):
        yy = y0 + h * (0.22 + 0.28 * k)
        core = np.maximum(core, pad.stroke([(x0 + 1, yy), (x0 + 10, yy)], 2.0, a0=0.75) * m)
    # 겹 테두리
    for ins, a in ((7, 0.75), (11, 0.45)):
        q = [(x0 + 11 + ins - 4, y0 + ins), (x1 - ins, y0 + ins), (x1 - ins, y1 - ins), (x0 + 11 + ins - 4, y1 - ins)]
        core = np.maximum(core, pad.stroke(q + [q[0]], 0.9, a0=a))
    # 모서리 쇠 장식 (바깥 두 모서리)
    for sy in (-1, 1):
        cy_ = y0 if sy < 0 else y1
        tri = [(x1 + 1, cy_), (x1 - 17, cy_), (x1 + 1, cy_ - sy * 17)]
        tm = pad.poly(tri) * m
        core = np.maximum(core, tm * 0.55 + rim_of(tm, 0.9) * 0.4)
        core = np.maximum(core, pad.disc_field(x1 - 5.5, cy_ - sy * 5.5, 1.6))
    # 가운데 문장: 마름모 틀 + 겹친 별 (여덟 뿔) + 마름모 끝 점
    ccx, ccy = x0 + 11 + (w - 11) / 2, y0 + h * 0.47
    dia = [(ccx, ccy - 30), (ccx + 23, ccy), (ccx, ccy + 30), (ccx - 23, ccy)]
    core = np.maximum(core, pad.stroke(dia + [dia[0]], 1.2, a0=0.85))
    core = np.maximum(core, pad.poly(star_pts(ccx, ccy, 19, 4, 0.16), 0.95))
    core = np.maximum(core, pad.poly(star_pts(ccx, ccy, 11, 4, 0.22, math.pi / 4), 0.8))
    core = np.maximum(core, pad.disc_field(ccx, ccy, 2.6))
    for x, y in dia:
        core = np.maximum(core, pad.disc_field(x, y, 2.0) * 0.9)
    # 걸쇠 (앞 가장자리 가운데)
    strap = pad.poly([(x1 - 14, ccy - 6), (x1 + 3, ccy - 6), (x1 + 3, ccy + 6), (x1 - 14, ccy + 6)])
    core = np.maximum(core, rim_of(strap, 0.9) * 0.9)
    core = np.maximum(core, pad.disc_field(x1 - 6, ccy, 2.4) * 0.95)
    sil = np.maximum(m, strap)
    st.over(sil, (0.6 + 0.2 * lit - 0.16 * dark) * bright, core * sil * bright)
    return sil


def tome_open(pad, st, cover_ws=(1.0, 1.0), left_ws=1.0, bright=1.0, lifts=(0.0, 0.0)):
    """펼친 책: 표지 (책장보다 조금 큼) · 책장 묶음 (두께 줄) · 양쪽 책장"""
    for side, cws, lift in ((-1, cover_ws[0], lifts[0]), (1, cover_ws[1], lifts[1])):
        if cws <= 0.02:
            continue
        cf = page_fn(side, cws, lift, dy=3.0, grow=7.0)
        cm = pad.poly(page_outline(cf))
        lit, dark, _ = bevel_light(cm, 3.0)
        core = rim_of(cm, 1.1) * 0.85 + lit * 0.3
        # 표지 모서리 장식
        for v in (0.0, 1.0):
            x, y = cf(1.0, v)
            core = np.maximum(core, pad.disc_field(x - side * 4 * cws, y + (4 if v == 0 else -4), 1.6) * 0.9)
        st.over(cm, (0.5 + 0.15 * lit) * bright, core * cm * bright)
    for side, ws, lift in ((-1, left_ws * cover_ws[0], lifts[0]), (1, cover_ws[1], lifts[1])):
        if ws <= 0.02:
            continue
        for k in (3, 2, 1):
            fn = page_fn(side, ws, lift, dx=k * 1.0, dy=k * 1.5)
            mk = pad.poly(page_outline(fn))
            st.over(mk, 0.55 * bright, rim_of(mk, 0.7) * 0.55 * mk * bright)


def tome_sparks(pad, f, n, seed, area=(60, 196, 70, 150), rise=78, bright=1.0, phase_frames=6):
    rnd = random.Random(seed)
    core = pad.zeros()
    for i in range(n):
        ph = rnd.random()
        x0 = rnd.uniform(area[0], area[1])
        y0 = rnd.uniform(area[2], area[3])
        sway = rnd.uniform(4, 12)
        spd = rnd.choice((1, 1, 2))
        t = (ph + spd * f / phase_frames) % 1.0
        x = x0 + math.sin(t * math.pi * 1.5 + i) * sway
        y = y0 - rise * t
        a = math.sin(math.pi * t) ** 1.2 * bright
        r = rnd.uniform(3.0, 7.0) * (0.7 + 0.5 * math.sin(math.pi * t))
        if i % 3 == 0:
            core = np.maximum(core, pad.disc_field(x, y, r * 0.35) * a)
        else:
            core = np.maximum(core, pad.poly(star_pts(x, y, r, 4, 0.2, t * 2.0), a))
    return core


def tome_frame(f, frames=12, S=256):
    pad = Pad(S, S)
    st = Stack(pad)
    X, Y = pad.grid()
    cx, top, ph, pw = TOME["cx"], TOME["top"], TOME["ph"], TOME["pw"]
    glow_extra = pad.zeros()
    flash = pad.zeros()
    sparks = pad.zeros()
    ribbon = None
    if f == 0 or f >= 10:
        # 닫힌 책 (등 = 가운데 x, 표지가 오른쪽)
        tome_cover_front(pad, st, cx - 2, top - 8, cx + pw + 6, top + ph + 12)
        if f == 0:
            # 앞 가장자리 틈으로 새는 빛 · 걸쇠가 풀림
            leak = pad.glowline([(cx + pw + 7, top + 2), (cx + pw + 7, top + ph + 2)], 1.6, 0.9)
            st.add_core(leak)
            glow_extra = np.maximum(glow_extra, pad.glowline([(cx + pw + 8, top - 4), (cx + pw + 8, top + ph + 8)], 6.0, 0.5))
        else:
            q = (f - 10)
            ccx, ccy = cx + 4 + (pw + 2) / 2, top + (ph + 20) * 0.45
            k = 1.0 if q == 0 else 0.45
            flash = np.maximum(flash, sparkle(pad, ccx, ccy, (70 if q == 0 else 52) * k + 10, 1.0 if q == 0 else 0.6, rot=0.0, thin=0.08, diag=0.45))
            rr = 34 if q == 0 else 62
            ringw = 3.0 if q == 0 else 1.6
            flash = np.maximum(flash, np.exp(-0.5 * ((np.sqrt((X - ccx) ** 2 + (Y - ccy) ** 2) - rr) / ringw) ** 2) * (0.9 if q == 0 else 0.45))
            glow_extra = np.maximum(glow_extra, pad.radial(ccx, ccy, 95 if q == 0 else 80, 1.6) * (0.75 if q == 0 else 0.35))
            sparks = tome_sparks(pad, f, 9, 77, area=(cx + 10, cx + pw, top, top + ph), rise=40, bright=1.0 if q == 0 else 0.7)
    elif f == 1:
        # 표지가 열리는 중 (등을 축으로 왼쪽으로 넘어감 — 거의 세워짐) · 오른쪽 책장이 드러남
        tome_open(pad, st, cover_ws=(0.0, 1.0))
        tome_page(pad, st, 1, page_fn(1), 11, "text", 1.0)
        cf = page_fn(-1, 0.16, 26.0, dy=3.0, grow=7.0)
        cm = pad.poly(page_outline(cf))
        st.over(cm, 0.62, (rim_of(cm, 1.1) * 0.95 + cm * 0.25) * cm)
        glow_extra = np.maximum(glow_extra, pad.radial(cx + 40, top + 30, 110, 1.8) * 0.45)
        sparks = tome_sparks(pad, f, 8, 31, area=(cx, cx + pw, top + 10, top + ph - 10), rise=55)
    elif f == 9:
        # 닫히는 중: 왼쪽 반 (표지 + 책장) 이 등 쪽으로 좁아지며 들림
        tome_open(pad, st, cover_ws=(0.42, 1.0), lifts=(22.0, 0.0))
        tome_page(pad, st, 1, page_fn(1), 11, "text", 0.9)
        tome_page(pad, st, -1, page_fn(-1, 0.42, 22.0), 5, "plain", 0.8)
        sparks = tome_sparks(pad, f, 8, 41, area=(cx - 40, cx + pw, top, top + ph), rise=50, bright=0.7)
    else:
        # 펼친 책 (2) · 책장 넘김 (3 ~ 8 이어 돌림)
        tome_open(pad, st)
        tome_page(pad, st, -1, page_fn(-1), 5, "chart", 1.0)
        tome_page(pad, st, 1, page_fn(1), 11, "text", 1.0)
        if f >= 3:
            th = math.pi * ((f - 3) + 0.5) / 6
            k = math.cos(th)
            side = 1 if k >= 0 else -1
            fn = page_fn(side, abs(k) * 0.97 + 0.03, 30.0 * math.sin(th))
            tm = pad.poly(page_outline(fn))
            core = rim_of(tm, 0.9) * 0.9
            core = np.maximum(core, page_text(pad, fn, 100 + f, 0.14, 0.88, rows=11, a=0.4) * tm)
            core = np.maximum(core, tm * 0.18 * math.sin(th))
            st.over(tm, 0.78, core * tm)
            sparks = tome_sparks(pad, f - 3, 18, 5, area=(cx - pw + 16, cx + pw - 16, top - 5, top + ph * 0.55), rise=72)
            glow_extra = np.maximum(glow_extra, pad.radial(cx, top + 20, 120, 1.8) * 0.3)
        else:
            sparks = tome_sparks(pad, f, 10, 23, area=(cx - pw + 10, cx + pw - 10, top + 10, top + ph - 20), rise=70)
            glow_extra = np.maximum(glow_extra, pad.radial(cx, top + 40, 130, 1.6) * 0.5)
            flash = np.maximum(flash, sparkle(pad, cx, top + 36, 46, 0.85, thin=0.07, diag=0.35))
        # 등에서 내려오는 책갈피 끈 (갈라진 끝)
        rb = [(cx + 2, top + ph + 2), (cx + 4, top + ph + 14), (cx + 1, top + ph + 26), (cx + 4, top + ph + 38)]
        ribbon = pad.stroke(rb, 3.2, 2.6)
        ribbon = np.maximum(ribbon, pad.poly([(cx + 1.5, top + ph + 37), (cx + 6.5, top + ph + 37), (cx + 6, top + ph + 45), (cx + 4, top + ph + 41), (cx + 2, top + ph + 45)]))
    if ribbon is not None:
        st.over(ribbon, 0.7, rim_of(ribbon, 0.8) * 0.8 * ribbon)
    # 책에서 오르는 빛 (뒤) — 책 몸이 가리지 않게 빛 장면에만
    core = np.maximum(st.core, np.maximum(sparks, flash))
    body = st.body
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.5, 0.45), (4.0, 0.32), (9.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.35), (4.5, 0.3)))
    glow1 = screen(glow1, pad.down(glow_extra), gblur(pad.down(sparks), 2.5) * 0.8)
    return glow1, core1


@sprite("rfx_jtome")
def make_tome():
    glows, cores = run_frames(tome_frame, 12)
    sp = Sprite("rfx_jtome", "rotate_xyz", 256, 256, 12, "mage", glows, cores, cols=6, zoom=5)
    return sp


# ------------------------------------------------------------ rfx_jstar (떠도는 별 — 구 · 별 · 기운 고리 둘)

def ring_pts(cx, cy, rx, ry, tilt, a0=0.0, a1=TAU, n=96):
    xf = Xf(cx, cy, tilt)
    return [xf.p(math.cos(a0 + (a1 - a0) * i / n) * rx, math.sin(a0 + (a1 - a0) * i / n) * ry) for i in range(n + 1)]


def star_frame(f, frames=8, S=192):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    R = 30.0
    pulse = 0.5 + 0.5 * math.cos(TAU * f / frames)
    Rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    sphere = pad.disc_field(c, c, R)
    # ---- 구: 가장자리가 밝은 (프레넬) 빛 몸 + 위 왼쪽 반사
    fres = clamp01(Rr / R) ** 2.4
    body = sphere * (0.42 + 0.45 * fres)
    core = sphere * fres ** 3 * 0.5
    core = np.maximum(core, np.exp(-0.5 * ((Rr - R + 0.8) / 0.6) ** 2) * 0.75)
    spec = pad.ellipse(c - 11, c - 13, 9, 5) * 0.0
    sx, sy = X - (c - 10), Y - (c - 14)
    ca, sa = math.cos(-0.7), math.sin(-0.7)
    u, v = (ca * sx + sa * sy) / 10.0, (-sa * sx + ca * sy) / 4.2
    spec = clamp01(1 - (u * u + v * v)) ** 1.5 * sphere
    core = np.maximum(core, spec * 0.6)
    # ---- 별 (가운데): 긴 네 뿔 + 짧은 대각선 넷 + 둥근 심
    rot = (math.pi / 2) * f / frames
    Ls = 54 + 8 * pulse
    st_core = pad.poly(star_pts(c, c, Ls, 4, 0.085, rot))
    st_core = np.maximum(st_core, pad.poly(star_pts(c, c, Ls * 0.46, 4, 0.16, rot + math.pi / 4), 0.9))
    st_core = np.maximum(st_core, pad.disc_field(c, c, 7.5 + 1.5 * pulse))
    st_glow = pad.poly(star_pts(c, c, Ls * 1.06, 4, 0.16, rot))
    st_glow = np.maximum(st_glow, pad.disc_field(c, c, 14 + 2 * pulse))
    core = np.maximum(core, st_core)
    body = np.maximum(body, st_glow)
    # ---- 기운 고리 둘: 뒤 반은 흐리고 구에 가려짐 · 앞 반은 밝음, 구슬이 돎 (A: 한 바퀴 / B: 구슬 둘이 반 바퀴)
    rings = [dict(rx=76, ry=20, tilt=math.radians(-22), beads=[TAU * f / frames], w=1.3),
             dict(rx=66, ry=27, tilt=math.radians(38), beads=[-math.pi * f / frames + 0.6, -math.pi * f / frames + 0.6 + math.pi], w=1.1)]
    ring_g = pad.zeros()
    ring_c = pad.zeros()
    for rg in rings:
        n = 160
        pts = ring_pts(c, c, rg["rx"], rg["ry"], rg["tilt"], 0, TAU, n)
        d, s = pad.line_field(pts, 8)
        ang = s * TAU
        front = (np.sin(ang) > 0).astype(np.float32)
        frontk = clamp01(0.5 + 0.5 * np.sin(ang) * 3)
        occl = 1 - sphere * (1 - frontk) * 0.85
        a = (0.35 + 0.65 * frontk) * occl
        cov = clamp01((rg["w"] * (0.6 + 0.4 * frontk) - d) * SS + 0.5)
        ring_c = np.maximum(ring_c, cov * a * 0.9)
        ring_g = np.maximum(ring_g, np.exp(-0.5 * (d / 2.2) ** 2) * a * 0.55)
        # 구슬 + 뒤로 끌리는 꼬리
        for b in rg["beads"]:
            for kk in range(14):
                aa = b - kk * 0.045 * (1 if rg is rings[0] else -1)
                xf = Xf(c, c, rg["tilt"])
                bx, by = xf.p(math.cos(aa) * rg["rx"], math.sin(aa) * rg["ry"])
                fk = clamp01(0.5 + 0.5 * math.sin(aa) * 3)
                vis = 1.0 if (fk > 0.5 or math.hypot(bx - c, by - c) > R) else 0.25
                k = (1 - kk / 14) ** 1.5
                if kk == 0:
                    ring_c = np.maximum(ring_c, sparkle(pad, bx, by, (7.5 + 3 * fk) * vis, 1.0 * vis, rot=0.3, diag=0.0))
                    ring_g = np.maximum(ring_g, pad.disc_field(bx, by, 5.5, soft=2.5) * vis)
                ring_c = np.maximum(ring_c, pad.disc_field(bx, by, (2.2 - 1.5 * kk / 14) * (0.7 + 0.3 * fk)) * k * vis * 0.9)
    core = np.maximum(core, ring_c)
    body = np.maximum(body, ring_g)
    # ---- 둘레를 도는 반짝임 (쌍으로 — 반 바퀴마다 같은 모습)
    rnd = random.Random(55)
    for i in range(3):
        base = rnd.uniform(0, TAU)
        rr = rnd.uniform(48, 82)
        for half in (0.0, math.pi):
            a = base + half + math.pi * f / frames * (1 if i % 2 == 0 else -1)
            x, y = c + math.cos(a) * rr, c + math.sin(a) * rr * 0.8
            tw = 0.5 + 0.5 * math.cos(TAU * (f / frames) * 2 + i * 2.1 + half)
            core = np.maximum(core, sparkle(pad, x, y, 3.5 + 4.5 * tw, 0.5 + 0.5 * tw, rot=0.4, diag=0.0))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((2.0, 0.45), (6.0, 0.35), (14.0, 0.22)), body_k=1.0, core_bloom=((1.5, 0.35), (4.0, 0.3)))
    return glow1, core1


@sprite("rfx_jstar")
def make_star():
    glows, cores = run_frames(star_frame, 8)
    return Sprite("rfx_jstar", "rotate_xyz", 192, 192, 8, "mage", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jbolt2 (별 마탄 — 오른쪽이 앞)

def bolt2_frame(f, frames=4, W=192, H=64):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    hx = 152.0
    tail0 = 8.0
    ph = TAU * f / frames
    # ---- 꼬리: 가늘어지는 몸 + 흔들리는 가닥 둘 (빛) · 가운데 밝은 가닥 (심지)
    t = clamp01((hx - X) / (hx - tail0))
    on = (X < hx + 2) & (X > tail0)
    wid = 10.5 * (1 - t) ** 0.85
    body = smooth(wid + 1.0, wid - 2.0, np.abs(Y - cy)) * on * (1 - t) ** 0.6 * 0.85
    core = np.exp(-0.5 * ((Y - cy) / (0.6 + 1.4 * (1 - t))) ** 2) * (1 - t) ** 1.3 * on * 0.95
    for sgn in (-1, 1):
        yy = cy + sgn * (2.5 + 6.5 * t) * np.sin(TAU * (t * 1.6) - ph * sgn + 0.5)
        strand = np.exp(-0.5 * ((Y - yy) / (0.5 + 0.9 * (1 - t))) ** 2) * (1 - t) ** 1.1 * on
        core = np.maximum(core, strand * 0.65)
        body = np.maximum(body, np.exp(-0.5 * ((Y - yy) / 2.5) ** 2) * (1 - t) ** 0.9 * on * 0.6)
    # ---- 반짝임 (꼬리를 따라 뒤로 흐름 — 4 장에 한 칸)
    sp = 23.0
    for i in range(-1, 7):
        x = hx - 18 - (i + f / frames) * sp
        if x < tail0 + 4 or x > hx - 10:
            continue
        tt = (hx - x) / (hx - tail0)
        y = cy + 7.0 * math.sin(x * 0.21)
        a = (1 - tt) ** 0.9
        core = np.maximum(core, sparkle(pad, x, y, (2.5 + 4.0 * (1 - tt)) * (0.7 + 0.3 * math.cos(x * 0.5)), a, rot=x * 0.05, diag=0.0))
    # ---- 머리: 도는 네 뿔 별 (4 장에 90°) + 작은 별 · 둥근 심 · 앞의 충격 호
    rot = (math.pi / 2) * f / frames
    head_c = pad.poly(star_pts(hx, cy, 25.0, 4, 0.12, rot))
    head_c = np.maximum(head_c, pad.poly(star_pts(hx, cy, 12.0, 4, 0.2, rot + math.pi / 4), 0.9))
    head_c = np.maximum(head_c, pad.disc_field(hx, cy, 6.0))
    head_g = pad.poly(star_pts(hx, cy, 27.0, 4, 0.22, rot))
    head_g = np.maximum(head_g, pad.disc_field(hx, cy, 11.5, soft=2.0))
    core = np.maximum(core, head_c)
    body = np.maximum(body, head_g)
    bow = [(hx + 9 + 0.0, cy - 13), (hx + 15, cy - 6), (hx + 16.5, cy), (hx + 15, cy + 6), (hx + 9, cy + 13)]
    core = np.maximum(core, pad.stroke(bow, 1.3, a0=0.55, taper=0.9))
    body = np.maximum(body, pad.stroke(bow, 4.0, a0=0.4, taper=0.9, soft=1.5))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.5, 0.45), (4.0, 0.35), (8.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.35), (4.0, 0.3)))
    return glow1, core1


@sprite("rfx_jbolt2")
def make_bolt2():
    glows, cores = run_frames(bolt2_frame, 4)
    return Sprite("rfx_jbolt2", "lookat_direction", 192, 64, 4, "mage", glows, cores, cols=2)


# ------------------------------------------------------------ rfx_jarrow2 (바람 화살 — 오른쪽이 촉)

def leaf_poly(x0, x1, cy, hw, n=32, back=0.05, belly=0.5, sharp=0.75):
    """잎 모양 (x0 뿌리 → x1 끝): 뿌리 쪽 1/4 이 가장 넓고 끝이 날카롭게 뾰족"""
    top, bot = [], []
    peak = max((4 * u * (1 - u)) ** belly * (1 - u) ** sharp for u in [i / 200 for i in range(201)])
    for i in range(n + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        tt = back + (1 - back) * t
        w = hw / peak * (4 * tt * (1 - tt)) ** belly * (1 - tt) ** sharp
        top.append((x, cy - w))
        bot.append((x, cy + w))
    return top + bot[::-1], top, bot


def arrow2_frame(f, frames=4, W=256, H=64):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    st = Stack(pad)
    # ---- 바람 띠 둘: 뒤쪽 반 (살대 뒤 — 먼저, 살대가 가림). 촉 뒤에서 가늘게 시작해 꼬리 쪽으로 넓게 풀리며 흐려짐
    kx = TAU / 92.0

    def ribbon_field(j, front):
        def amp(x):
            u = clamp01((200 - x) / 190)
            return 3.0 + 9.0 * u ** 0.8

        pts = []
        for i in range(161):
            x = 200 - 200 * i / 160
            pts.append((x, cy + amp(x) * math.sin(kx * x + ph + j * math.pi)))
        d, s_ = pad.line_field(pts, 6)
        phase = kx * X + ph + j * math.pi
        depth = np.cos(phase)  # > 0 = 앞
        frontk = clamp01(0.5 + depth * 2.5) if front else clamp01(0.5 - depth * 2.5)
        fade = smooth(0, 90, X) * smooth(202, 180, X) * (0.55 + 0.45 * np.cos(X * 0.045 + j * 2.0 - ph) ** 2)
        w = (0.45 + 0.55 * clamp01(depth)) if front else 0.4
        cov = clamp01((w - d) * SS + 0.5)
        return cov * frontk * fade, np.exp(-0.5 * (d / 1.8) ** 2) * frontk * fade

    back_c = pad.zeros()
    back_g = pad.zeros()
    for j in (0, 1):
        c_, g_ = ribbon_field(j, False)
        back_c = np.maximum(back_c, c_ * 0.5)
        back_g = np.maximum(back_g, g_ * 0.25)
    st.body = np.maximum(st.body, back_g)
    st.core = np.maximum(st.core, back_c)
    # ---- 깃 (위 · 아래 · 가운데 — 가운데 깃은 앞을 봄: 좁고 밝음)
    def vane(sgn, hgt, x_front=58.0, x_back=13.0):
        pts = bez((x_front, cy - sgn * 1.2), (x_front - 14, cy - sgn * hgt * 1.05), (x_back + 8, cy - sgn * hgt), 14)
        pts += [(x_back, cy - sgn * hgt * 0.92), (x_back + 6, cy - sgn * 1.0)]
        return pts
    for sgn in (1, -1):
        vp = vane(sgn, 12.5)
        vm = pad.poly(vp)
        core = rim_of(vm, 0.8) * 0.8
        for k in range(10):
            xx = 20 + k * 3.8
            core = np.maximum(core, pad.stroke([(xx, cy - sgn * 1.5), (xx - 6, cy - sgn * 13)], 0.55, a0=0.45) * vm)
        st.over(vm, 0.72, core * vm)
    # 살대
    shaft = pad.poly([(14, cy - 1.8), (206, cy - 1.6), (206, cy + 1.6), (14, cy + 1.8)])
    st.over(shaft, 0.8, (shaft * clamp01((cy - Y + 1.8) / 2.0) * 0.7 + rim_of(shaft, 0.5) * 0.3) * shaft)
    # 가운데 깃 (앞)
    mv = pad.poly(bez((54, cy + 0.5), (40, cy - 6.5), (19, cy - 5.5), 12) + [(15, cy - 4.5), (19, cy + 1.5)])
    st.over(mv, 0.82, (rim_of(mv, 0.7) * 0.9 + mv * 0.35) * mv)
    # 오늬 (끝의 홈)
    nock = pad.poly([(7, cy - 3.6), (15, cy - 2.4), (15, cy + 2.4), (7, cy + 3.6), (10, cy)])
    st.over(nock, 0.85, rim_of(nock, 0.7) * 0.9 * nock)
    # 촉 받침 (고리 둘) · 잎 촉
    fer = pad.poly([(203, cy - 2.8), (215, cy - 2.6), (215, cy + 2.6), (203, cy + 2.8)])
    fcore = rim_of(fer, 0.6) * 0.7
    for xx in (206, 211):
        fcore = np.maximum(fcore, pad.stroke([(xx, cy - 3), (xx, cy + 3)], 0.8, a0=0.9) * fer)
    st.over(fer, 0.85, fcore * fer)
    lp, ltop, lbot = leaf_poly(209, 253, cy, 9.0)
    leaf = pad.poly(lp)
    v = (Y - cy) / 9.0
    lcore = rim_of(leaf, 1.0) * 0.9 + leaf * clamp01(-v) ** 1.2 * 0.55
    lcore = np.maximum(lcore, pad.stroke([(214, cy), (248, cy)], 0.8, 0.3, a0=0.9) * leaf)
    for sgn in (-1, 1):
        for k in range(3):
            x0 = 219 + k * 8
            lcore = np.maximum(lcore, pad.stroke([(x0, cy), (x0 + 6, cy + sgn * 5.5 * (1 - k * 0.22))], 0.55, a0=0.45) * leaf)
    st.over(leaf, 0.86 + 0.0 * v, lcore * leaf)
    # ---- 바람 띠 앞쪽 반 (살대 · 깃 위)
    for j in (0, 1):
        c_, g_ = ribbon_field(j, True)
        st.core = np.maximum(st.core, c_ * 0.95)
        st.body = np.maximum(st.body, g_ * 0.38)
    # 촉 끝 반짝임 · 살대를 따라 흐르는 빛
    tw = [1.0, 0.5, 0.8, 0.4][f]
    st.add_core(sparkle(pad, 249, cy, 5 + 4 * tw, 1.0, rot=0.2 * f, diag=0.0))
    sx = 60 + 140 * ((f + 0.5) / frames)
    st.add_core(np.exp(-0.5 * ((X - sx) / 9.0) ** 2) * shaft * 0.9)
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.2, 0.5), (3.0, 0.35), (6.5, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.22)))
    return glow1, core1


@sprite("rfx_jarrow2")
def make_arrow2():
    glows, cores = run_frames(arrow2_frame, 4)
    return Sprite("rfx_jarrow2", "lookat_direction", 256, 64, 4, "archer", glows, cores, cols=2)


# ============================================================ 실행

def main():
    args = sys.argv[1:]
    only = None
    folder = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
    for name, fn in SPRITES.items():
        if only and name not in only:
            continue
        sp = fn()
        save_sprite(sp)
        if folder:
            print("  preview:", preview_sprite(sp, folder))


if __name__ == "__main__":
    main()
