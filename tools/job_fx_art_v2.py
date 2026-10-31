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
멈춘 카드 (한 장 — 칸 0 빛 · 칸 1 심지, 꼬리 · 잔상 없음: 입체 연출이 카드를 직접 움직이고 돌림):
- rfx_jaxe_card     lookat_y 192×192 ×1   같은 도끼를 곧게 세움 (자루 = 가운데 세로줄 · 머리 위 · 날 오른쪽 · 가시 왼쪽)
- rfx_jdagger_card  lookat_y 128×128 ×1   같은 휜 단검을 곧게 세움 (날 끝 위 · 고리 아래 · 자루 축 = 가운데 세로줄)
- rfx_jbuckler_card lookat_y 128×128 ×1   둥근 방패 앞모습 (온전한 원 · 가운데 돌기 · 테 징)
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


def _bbox(m, pad=2):
    ys = np.flatnonzero(m.any(axis=1))
    if ys.size == 0:
        return None
    xs = np.flatnonzero(m.any(axis=0))
    h, w = m.shape
    return max(0, ys[0] - pad), min(h, ys[-1] + pad + 1), max(0, xs[0] - pad), min(w, xs[-1] + pad + 1)


def _edt(m):
    if _ndi is not None:
        return _ndi.distance_transform_edt(m).astype(np.float32)
    # scipy 가 없으면: 깎아 들어가며 셈 (거칠지만 같은 쓰임)
    d = np.zeros(m.shape, np.float32)
    cur = m.copy()
    k = 0
    while cur.any() and k < 600:
        d[cur] += 1
        nxt = cur.copy()
        nxt[1:, :] &= cur[:-1, :]
        nxt[:-1, :] &= cur[1:, :]
        nxt[:, 1:] &= cur[:, :-1]
        nxt[:, :-1] &= cur[:, 1:]
        cur = nxt
        k += 1
    return d


def inner_dist(mask, ss=SS):
    """모양 안쪽 깊이 (칸 점 단위 — 가장자리 0). 모양을 감싼 네모 안만 셈"""
    m = mask > 0.5
    out = np.zeros(mask.shape, np.float32)
    bb = _bbox(m)
    if bb is None:
        return out
    y0, y1, x0, x1 = bb
    out[y0:y1, x0:x1] = _edt(m[y0:y1, x0:x1]) / ss
    return out


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


def edge_fade(a, m=3.0):
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
    inside = mask_ss > 0.5
    lit = np.zeros(mask_ss.shape, np.float32)
    dark = np.zeros(mask_ss.shape, np.float32)
    hh = np.zeros(mask_ss.shape, np.float32)
    bb = _bbox(inside, 3)
    if bb is None:
        return lit, dark, hh
    y0, y1, x0, x1 = bb
    m = inside[y0:y1, x0:x1]
    d = _edt(m) / ss
    h = clamp01(d / max(1e-6, depth))
    h = h * h * (3 - 2 * h)
    gy, gx = np.gradient(h)
    n = np.sqrt(gx * gx + gy * gy) + 1e-6
    lx, ly = light
    dot = (-gx * lx - gy * ly) / n
    slope = clamp01(n * ss * depth / 1.6)
    lit[y0:y1, x0:x1] = clamp01(dot) * slope * m
    dark[y0:y1, x0:x1] = clamp01(-dot) * slope * m
    hh[y0:y1, x0:x1] = h * m
    return lit, dark, hh


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
    tip = W - 11.0
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
    g0, g1 = 25.5, 50.5
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
    px = 16.0
    g["px"] = px
    g["pommel"] = [(px - 6.8, cy), (px - 1.0, cy - 6.4), (px + 1.2, cy - 6.4), (px + 6.8, cy), (px + 1.2, cy + 6.4), (px - 1.0, cy + 6.4)]
    g["spike"] = [(px - 6.0, cy - 1.4), (px - 8.6, cy), (px - 6.0, cy + 1.4)]
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
    core = np.maximum(core, sparkle(pad, tip - 4, cy - 1.0, 5.0 + 3.5 * tw, 1.0, rot=0.18 * f))
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
    b0, tip = 128.0, W - 17.0
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
        w = [(gx0 + 2, cy + sd * 18), (gx0 - 5, cy + sd * 37), (gx0 + 6, cy + sd * 54), (gx0 + 15, cy + sd * 42), (gx1 + 3, cy + sd * 20)]
        wings.append(w)
    g["wings"] = wings
    # 손잡이 · 고리 둘 · 자루 끝
    g["grip"] = [(50, cy - 7.2), (101, cy - 8.2), (101, cy + 8.2), (50, cy + 7.2)]
    g["rings"] = [[(92, cy - 10), (100, cy - 10.5), (100, cy + 10.5), (92, cy + 10)], [(48, cy - 9.5), (56, cy - 9.2), (56, cy + 9.2), (48, cy + 9.5)]]
    px = 33.0
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
    x = 58.0
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
    core = np.maximum(core, sparkle(pad, tip - 5, cy, 8 + 4 * tw, 0.95, rot=0.3 + 0.2 * f))
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

def axe_local(bend=1.2):
    """도끼 (로컬: 자루가 +x 쪽 — 머리가 +x 끝, 날이 -y 쪽). 점 단위 대략 1 · bend = 자루가 휜 정도 (0 = 곧음)"""
    parts = {}
    top, bot = [], []
    for i in range(31):
        t = i / 30
        x = -60 + 112 * t
        yc = bend * math.sin(math.pi * t)
        w = 4.3 - 0.8 * t
        top.append((x, yc - w))
        bot.append((x, yc + w))
    parts["haft"] = top + bot[::-1]
    parts["haft_mid"] = [(-60 + 112 * i / 30, bend * math.sin(math.pi * i / 30)) for i in range(31)]
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


def axe_draw(pad, xf, light=(-0.55, -0.83), glint=0.0, gl_rot=0.0, bend=1.2):
    """도끼 한 자루 (xf: 로컬 → 칸). (빛 몸 4배, 심지 4배, 머리 모양 4배)"""
    P = axe_local(bend)
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
    sweep = radial * k ** 1.7 * 0.58
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
    sc = 1.08
    xf = Xf(234 - 71.5 * sc, 90, 0.0, sc, sc)
    body, core, head, sil = axe_draw(pad, xf, glint=[0.7, 0.3, 0.55, 0.2][f], gl_rot=0.25 * f)
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
    smear = np.maximum(smear, hsil * 0.6 * (1 - sil1)) * smooth(4, 52, xs)
    # 심지: 날 쪽에서 뒤로 뻗는 가는 바람 줄 (장마다 길이 · 밝기가 바뀜)
    lines = pad.zeros()
    P = axe_local()
    for i, idx in enumerate([2, 5, 8, 11, 14, 17, 20]):
        x0, y0 = xf.p(*P["edge"][idx])
        Ln = 40 + 60 * ((i * 37 + f * 23) % 100) / 100
        a = 0.35 + 0.5 * ((i * 53 + f * 31) % 100) / 100
        lines = np.maximum(lines, pad.stroke([(x0 - 4, y0 + 1.5), (x0 - 4 - Ln, y0 + 1.5)], 1.1, 0.2, a0=a, a1=0.0))
    Xg, _ = pad.grid()
    core = np.maximum(core, lines * (1 - sil) * smooth(6, 50, Xg))
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
            flash = np.maximum(flash, sparkle(pad, ccx, ccy, (52 if q == 0 else 40) * k + 8, 1.0 if q == 0 else 0.6, rot=0.0, thin=0.08, diag=0.45))
            rr = 34 if q == 0 else 56
            ringw = 3.0 if q == 0 else 1.6
            flash = np.maximum(flash, np.exp(-0.5 * ((np.sqrt((X - ccx) ** 2 + (Y - ccy) ** 2) - rr) / ringw) ** 2) * (0.9 if q == 0 else 0.45))
            glow_extra = np.maximum(glow_extra, pad.radial(ccx, ccy, 66 if q == 0 else 60, 1.6) * (0.75 if q == 0 else 0.35))
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
    tail0 = 13.0
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
    shaft = pad.poly([(16, cy - 1.8), (202, cy - 1.6), (202, cy + 1.6), (16, cy + 1.8)])
    st.over(shaft, 0.8, (shaft * clamp01((cy - Y + 1.8) / 2.0) * 0.7 + rim_of(shaft, 0.5) * 0.3) * shaft)
    # 가운데 깃 (앞)
    mv = pad.poly(bez((54, cy + 0.5), (40, cy - 6.5), (19, cy - 5.5), 12) + [(15, cy - 4.5), (19, cy + 1.5)])
    st.over(mv, 0.82, (rim_of(mv, 0.7) * 0.9 + mv * 0.35) * mv)
    # 오늬 (끝의 홈)
    nock = pad.poly([(10, cy - 3.6), (17, cy - 2.4), (17, cy + 2.4), (10, cy + 3.6), (13, cy)])
    st.over(nock, 0.85, rim_of(nock, 0.7) * 0.9 * nock)
    # 촉 받침 (고리 둘) · 잎 촉
    fer = pad.poly([(199, cy - 2.8), (210, cy - 2.6), (210, cy + 2.6), (199, cy + 2.8)])
    fcore = rim_of(fer, 0.6) * 0.7
    for xx in (202, 207):
        fcore = np.maximum(fcore, pad.stroke([(xx, cy - 3), (xx, cy + 3)], 0.8, a0=0.9) * fer)
    st.over(fer, 0.85, fcore * fer)
    lp, ltop, lbot = leaf_poly(205, 246, cy, 9.0)
    leaf = pad.poly(lp)
    v = (Y - cy) / 9.0
    lcore = rim_of(leaf, 1.0) * 0.9 + leaf * clamp01(-v) ** 1.2 * 0.55
    lcore = np.maximum(lcore, pad.stroke([(208, cy), (241, cy)], 0.8, 0.3, a0=0.9) * leaf)
    for sgn in (-1, 1):
        for k in range(3):
            x0 = 213 + k * 8
            lcore = np.maximum(lcore, pad.stroke([(x0, cy), (x0 + 6, cy + sgn * 5.5 * (1 - k * 0.22))], 0.55, a0=0.45) * leaf)
    st.over(leaf, 0.86 + 0.0 * v, lcore * leaf)
    # ---- 바람 띠 앞쪽 반 (살대 · 깃 위)
    for j in (0, 1):
        c_, g_ = ribbon_field(j, True)
        st.core = np.maximum(st.core, c_ * 0.95)
        st.body = np.maximum(st.body, g_ * 0.38)
    # 촉 끝 반짝임 · 살대를 따라 흐르는 빛
    tw = [1.0, 0.5, 0.8, 0.4][f]
    st.add_core(sparkle(pad, 243, cy, 4.5 + 3.5 * tw, 1.0, rot=0.2 * f, diag=0.0))
    sx = 60 + 140 * ((f + 0.5) / frames)
    st.add_core(np.exp(-0.5 * ((X - sx) / 9.0) ** 2) * shaft * 0.9)
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.2, 0.5), (3.0, 0.35), (6.5, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.22)))
    return glow1, core1


@sprite("rfx_jarrow2")
def make_arrow2():
    glows, cores = run_frames(arrow2_frame, 4)
    return Sprite("rfx_jarrow2", "lookat_direction", 256, 64, 4, "archer", glows, cores, cols=2)


# ------------------------------------------------------------ rfx_jbow2 (빛의 리커브 활 — 0 ~ 5 당김, 6 ~ 7 놓음)

BOW = dict(gx=86.0, gy=128.0, L=106.0)


def bow_limb(sgn, pull):
    """활대 중심선 (sgn -1 = 위 · 1 = 아래) — 손잡이 (u 0) → 끝 (u 1). 당길수록 더 휨"""
    gx, gy, L = BOW["gx"], BOW["gy"], BOW["L"]
    pts = []
    for i in range(41):
        u = i / 40
        x = gx + (40 + 16 * pull) * u ** 1.75 - 24 * smooth(0.76, 1.0, u) ** 1.4 * (1 - 0.25 * pull)
        y = gy + sgn * L * (1 - 0.07 * pull) * (u + 0.04 * math.sin(math.pi * u))
        pts.append((x, y))
    return pts


def bow2_frame(f, frames=8, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    gx, gy = BOW["gx"], BOW["gy"]
    pulls = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 0.0, 0.0]
    pull = pulls[f]
    st = Stack(pad)
    glow_extra = pad.zeros()
    fx = pad.zeros()
    limbs = [bow_limb(-1, pull), bow_limb(1, pull)]
    tips = [lb[-1] for lb in limbs]
    brace_x = (tips[0][0] + tips[1][0]) / 2 + 3.0
    nock_x = brace_x + 74 * pull
    # ---- 시위 (활대 뒤)
    if f <= 5:
        string = [tips[0], (nock_x, gy), tips[1]]
        st_core = pad.stroke(string, 1.1, a0=0.95)
        st_core = np.maximum(st_core, pad.stroke([(nock_x - 0.3 * (nock_x - tips[0][0]) * 0.12, gy - 9), (nock_x, gy), (nock_x - 0.3 * (nock_x - tips[1][0]) * 0.12, gy + 9)], 2.0, a0=0.9))
        st.body = np.maximum(st.body, pad.stroke(string, 3.0, a0=0.6, soft=1.0))
    else:
        amp = -13.0 if f == 6 else 6.0
        string = bez(tips[0], (brace_x + amp * 2, gy), tips[1], 24)
        st_core = pad.stroke(string, 1.1, a0=0.95)
        if f == 7:
            ghost = bez(tips[0], (brace_x - 8 * 2, gy), tips[1], 24)
            st_core = np.maximum(st_core, pad.stroke(ghost, 0.8, a0=0.35))
        st.body = np.maximum(st.body, pad.stroke(string, 3.0, a0=0.6, soft=1.0))
    st.core = np.maximum(st.core, st_core)
    # ---- 빛 화살 (당기는 동안: 오늬 → 손잡이 앞 촉)
    if 1 <= f <= 5:
        k = pull
        tipx = gx - 30
        shaft = pad.stroke([(nock_x - 2, gy), (tipx + 12, gy)], 1.6, a0=0.35 + 0.6 * k)
        head = pad.poly([(tipx, gy), (tipx + 14, gy - 5.5), (tipx + 11, gy), (tipx + 14, gy + 5.5)], 0.4 + 0.6 * k)
        fl = pad.zeros()
        for sg in (-1, 1):
            fl = np.maximum(fl, pad.stroke([(nock_x - 2, gy), (nock_x - 14, gy + sg * 6)], 1.2, a0=0.4 + 0.5 * k))
        st.add_core(np.maximum.reduce([shaft, head, fl]))
        st.body = np.maximum(st.body, pad.stroke([(nock_x, gy), (tipx, gy)], 5.0, a0=0.35 + 0.4 * k, soft=2.0))
    # ---- 활대 (리커브): 손잡이가 두껍고 끝으로 가늘어짐 · 끝이 바깥으로 휨
    for li, lb in enumerate(limbs):
        poly = taper_poly(lb, lambda t: 6.2 * (1 - t) ** 0.55 + 1.7)
        m = pad.poly(poly)
        lit, dark, _ = bevel_light(m, 2.4)
        core = rim_of(m, 0.8) * 0.85 + lit * 0.5
        core = np.maximum(core, pad.stroke(lb[4:36], 0.7, a0=0.55, taper=0.6) * m)
        st.over(m, 0.82 + 0.12 * lit - 0.2 * dark, core * m)
        # 활 끝: 초승달 귀 장식 + 시위 고리 점
        tx, ty = lb[-1]
        px, py = lb[-3]
        ear = pad.poly(star_pts(tx, ty, 5.5, 4, 0.3, math.atan2(ty - py, tx - px)))
        st.over(ear, 0.9, (ear * 0.6 + rim_of(ear, 0.7) * 0.4) * ear)
        st.add_core(pad.disc_field(tx, ty, 1.6))
        # 손잡이 쪽 잎 장식 (활대 뿌리)
        rx, ry = lb[9]
        sg = -1 if li == 0 else 1
        lp = [(rx - 1, ry), (rx - 9, ry + sg * 3), (rx - 13, ry + sg * 12), (rx - 4, ry + sg * 7)]
        lm = pad.poly(lp)
        st.over(lm, 0.85, (rim_of(lm, 0.7) * 0.9 + lm * 0.3) * lm)
    # 손잡이 (가운데 덩이) · 화살 받침 · 보석 · 감은 줄
    riser = [(gx - 6.5, gy - 22), (gx + 4.5, gy - 20), (gx + 7.5, gy - 6), (gx + 7.5, gy + 6), (gx + 4.5, gy + 20), (gx - 6.5, gy + 22), (gx - 8.5, gy)]
    rm = pad.poly(riser)
    lit, dark, _ = bevel_light(rm, 3.0)
    core = rim_of(rm, 0.9) * 0.9 + lit * 0.55
    for k in range(5):
        yy = gy - 10 + k * 5
        core = np.maximum(core, pad.stroke([(gx - 6.5, yy - 1.5), (gx + 6.5, yy + 1.5)], 0.8, a0=0.6) * rm)
    gem = pad.poly([(gx - 4, gy - 16), (gx - 1, gy - 20.5), (gx + 2, gy - 16), (gx - 1, gy - 11.5)])
    core = np.maximum(core, gem)
    st.over(rm, 0.86 + 0.1 * lit - 0.2 * dark, core * rm)
    # ---- 오늬 자리의 모이는 빛 (당기는 동안) / 터짐 (놓음)
    if f <= 5:
        k = pull
        r = 3.0 + 9.0 * k
        fx = np.maximum(fx, pad.disc_field(nock_x, gy, r * 0.42))
        fx = np.maximum(fx, sparkle(pad, nock_x, gy, 8 + 24 * k, 0.6 + 0.4 * k, rot=0.35 * f, thin=0.1, diag=0.4))
        glow_extra = np.maximum(glow_extra, pad.radial(nock_x, gy, 16 + 26 * k, 1.4) * (0.4 + 0.5 * k))
        rnd = random.Random(90)
        for i in range(10):
            a0 = rnd.uniform(0, TAU)
            r0 = rnd.uniform(18, 36)
            tt = (rnd.random() + f / 6.0) % 1.0  # 바깥 → 안으로 (나선)
            rr = r0 * (1 - tt) + 4
            a = a0 + tt * 2.4
            x, y = nock_x + math.cos(a) * rr, gy + math.sin(a) * rr
            al = math.sin(math.pi * tt) * (0.4 + 0.6 * k)
            tail = [(x, y), (nock_x + math.cos(a - 0.35) * (rr + 9), gy + math.sin(a - 0.35) * (rr + 9))]
            fx = np.maximum(fx, pad.stroke(tail, 1.4, 0.3, a0=al, a1=0.0))
            fx = np.maximum(fx, pad.disc_field(x, y, 1.3) * al)
    else:
        nx = brace_x + 74
        q = 0.0 if f == 6 else 1.0
        R = np.sqrt((X - nx) ** 2 + (Y - gy) ** 2)
        rr = 15 + 17 * q
        fx = np.maximum(fx, np.exp(-0.5 * ((R - rr) / (2.4 - 1.0 * q)) ** 2) * (1.0 - 0.45 * q))
        fx = np.maximum(fx, sparkle(pad, nx, gy, 30 - 12 * q, 1.0 - 0.45 * q, rot=0.0, thin=0.08, diag=0.45))
        glow_extra = np.maximum(glow_extra, pad.radial(nx, gy, 40 - 6 * q, 1.5) * (0.8 - 0.4 * q))
        # 쏜 쪽 (왼쪽) 으로 뻗는 빠른 줄
        rnd = random.Random(61)
        for i in range(7):
            yy = gy + rnd.uniform(-14, 14)
            x0 = nx - rnd.uniform(10, 30) - 50 * q
            Ln = rnd.uniform(40, 110) * (1 - 0.4 * q)
            fx = np.maximum(fx, pad.stroke([(x0, yy), (x0 - Ln, yy)], 1.6 - 0.5 * q, 0.3, a0=0.9 - 0.4 * q, a1=0.0))
        for i in range(9):
            a = rnd.uniform(0, TAU)
            d = rnd.uniform(14, 30) + 26 * q
            fx = np.maximum(fx, pad.disc_field(nx + math.cos(a) * d, gy + math.sin(a) * d, 1.5) * (1 - 0.5 * q))
    st.add_core(fx)
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.4, 0.45), (3.8, 0.34), (8.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.35), (4.5, 0.3)))
    glow1 = screen(glow1, pad.down(glow_extra))
    return glow1, core1


@sprite("rfx_jbow2")
def make_bow2():
    glows, cores = run_frames(bow2_frame, 8)
    return Sprite("rfx_jbow2", "rotate_xyz", 256, 256, 8, "archer", glows, cores, cols=4, zoom=5)


# ------------------------------------------------------------ rfx_jdagger2 (휜 단검 — 한 바퀴)

def dagger_local():
    P = {}
    # 날: 가운데 줄이 위로 휨 · 등 (위) 은 얇고 날 (아래 — 볼록한 쪽) 은 넓게
    cl = [(3 + 50 * (i / 30), -15 * (i / 30) ** 2.1) for i in range(31)]
    top, bot = [], []
    for i, (x, y) in enumerate(cl):
        t = i / 30
        if i < 30:
            dx, dy = cl[i + 1][0] - x, cl[i + 1][1] - y
        else:
            dx, dy = x - cl[i - 1][0], y - cl[i - 1][1]
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L
        ws = 2.6 * (1 - t) ** 0.9
        we = 4.9 * (1 - t ** 1.6) ** 0.75
        top.append((x - nx * ws, y - ny * ws))
        bot.append((x + nx * we, y + ny * we))
    P["blade"] = top + bot[::-1]
    P["edge"] = bot
    P["spine"] = top
    P["cl"] = cl
    P["guard"] = [(-1.5, -9.5), (3.5, -8.0), (4.5, 0), (3.5, 8.0), (-1.5, 9.5), (-3.0, 0)]
    P["curls"] = [(0.5, -10.5), (0.5, 10.5)]
    hd = [(-2 - 21 * (i / 12), 0.03 * (21 * i / 12) ** 1.3) for i in range(13)]
    P["handle"] = taper_poly(hd, lambda t: 2.9 + 0.5 * math.sin(math.pi * t))
    P["hd"] = hd
    P["ring"] = (-27.5, 2.2)
    return P


def dagger_draw(pad, xf, glint=0.0):
    P = dagger_local()
    sc = abs(xf.sx)
    blade = pad.poly(xf(P["blade"]))
    guard = pad.poly(xf(P["guard"]))
    curls = np.maximum.reduce([pad.disc_field(*xf.p(*q), 2.4 * sc) for q in P["curls"]])
    guard = np.maximum(guard, curls)
    handle = pad.poly(xf(P["handle"]))
    rx, ry = xf.p(*P["ring"])
    ring = clamp01(pad.disc_field(rx, ry, 5.6 * sc) - pad.disc_field(rx, ry, 3.0 * sc))
    sil = np.maximum.reduce([blade, guard, handle, ring])
    lit_b, dark_b, _ = bevel_light(blade, 2.5 * sc)
    lit_o, dark_o, _ = bevel_light(np.maximum.reduce([guard, handle, ring]), 1.8 * sc)
    body = np.maximum.reduce([blade * (0.84 + 0.14 * lit_b - 0.26 * dark_b), guard * (0.86 + 0.1 * lit_o), handle * (0.7 + 0.15 * lit_o - 0.15 * dark_o), ring * 0.86])
    core = pad.zeros()
    de, _ = pad.line_field(xf(P["edge"]), 8 * sc)
    core = np.maximum(core, np.exp(-de / (2.2 * sc)) * blade * 0.9)
    core = np.maximum(core, pad.stroke(xf(P["edge"]), 0.75 * sc, a0=1.0) * blade)
    core = np.maximum(core, pad.stroke(xf(P["cl"][2:24]), 0.6 * sc, 0.2 * sc, a0=0.7, a1=0.3) * blade)
    core = np.maximum(core, rim_of(blade, 0.6 * sc) * 0.6)
    core = np.maximum(core, lit_b * 0.5)
    core = np.maximum(core, rim_of(guard, 0.7 * sc) * 0.9 + lit_o * guard * 0.4)
    core = np.maximum(core, pad.disc_field(*xf.p(1.0, 0.0), 1.6 * sc))
    hx = P["hd"]
    wr = pad.zeros()
    for i in range(1, 12, 2):
        (x0, y0), (x1, y1) = hx[i], hx[i + 1]
        wr = np.maximum(wr, pad.stroke(xf([(x0, y0 - 3.4), (x1, y1 + 3.4)]), 0.7 * sc, a0=0.75))
    core = np.maximum(core, wr * handle)
    core = np.maximum(core, rim_of(handle, 0.6 * sc) * 0.5)
    core = np.maximum(core, rim_of(ring, 0.7 * sc) * 0.9)
    if glint > 0:
        gx, gy = xf.p(*P["edge"][18])
        core = np.maximum(core, sparkle(pad, gx, gy, 7 * sc * glint, 1.0, rot=0.3))
    return body, core, sil


def dagger_bounds():
    P = dagger_local()
    pts = P["blade"] + P["guard"] + P["handle"] + [(P["ring"][0] - 5.6, P["ring"][1]), (P["ring"][0], P["ring"][1] + 5.6)]
    best = None
    for cx in np.linspace(-10, 25, 36):
        for cy in np.linspace(-12, 8, 21):
            r = max(math.hypot(x - cx, y - cy) for x, y in pts)
            if best is None or r < best[0]:
                best = (r, cx, cy)
    return best


def dagger2_frame(f, frames=8, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    r, pcx, pcy = dagger_bounds()
    sc = (S * 0.41) / r
    c = S / 2
    th = TAU * f / frames + math.radians(-30)
    xf0 = Xf(c, c, th, sc, sc)
    ox, oy = xf0.vec(-pcx, -pcy)
    xf = Xf(c + ox, c + oy, th, sc, sc)
    body, core, sil = dagger_draw(pad, xf, glint=[1.0, 0, 0, 0.6, 0, 0, 0.8, 0][f])
    # 날 끝이 그리는 가는 원 꼬리 + 날이 지난 옅은 쓸림
    P = dagger_local()
    tx, ty = xf.p(*P["cl"][-1])
    rt = math.hypot(tx - c, ty - c)
    at = math.atan2(ty - c, tx - c)
    R = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    TH = np.arctan2(Y - c, X - c)
    dl = np.mod(at - TH, TAU)
    k = clamp01(1 - dl / math.radians(200))
    trail_c = np.exp(-0.5 * ((R - rt) / (0.35 + 0.5 * k)) ** 2) * k ** 2.0 * 0.9
    trail_g = np.exp(-0.5 * ((R - rt) / (0.8 + 2.0 * k)) ** 2) * k ** 1.5 * 0.7
    sweep = clamp01((R - rt * 0.45) / (rt * 0.25)) * clamp01((rt + 1 - R) / 2) * k ** 3 * 0.35
    trail_g = np.maximum(trail_g, sweep)
    trail_g = np.maximum(trail_g, np.exp(-0.5 * ((R - rt) / 0.7) ** 2) * 0.08)
    core = np.maximum(core, trail_c * (1 - sil))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.0, 0.5), (2.6, 0.36), (5.5, 0.2)), body_k=1.0, core_bloom=((1.2, 0.3), (3.0, 0.2)))
    glow1 = screen(glow1, pad.down(trail_g * (1 - sil)))
    return glow1, core1


@sprite("rfx_jdagger2")
def make_dagger2():
    glows, cores = run_frames(dagger2_frame, 8)
    return Sprite("rfx_jdagger2", "rotate_xyz", 128, 128, 8, "assassin", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jspear2 (긴 창 — 오른쪽이 창끝)

def spear2_frame(f, frames=4, W=384, H=64):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    st = Stack(pad)
    # ---- 술 (창날 뿌리에서 뒤로 흩날림 — 자루 뒤에 먼저)
    tas = pad.zeros()
    tas_g = pad.zeros()
    for j, (dy0, amp, L, w) in enumerate(((2.5, 4.0, 58, 1.5), (4.0, 5.5, 66, 1.3), (5.5, 4.5, 50, 1.1))):
        pts = []
        for i in range(25):
            t = i / 24
            x = 304 - L * t
            y = cy + dy0 + 6 * t ** 1.3 + amp * t * math.sin(TAU * (t * 1.3) - ph - j * 0.9)
            pts.append((x, y))
        tas = np.maximum(tas, pad.stroke(pts, w, 0.5, a0=0.85, a1=0.25))
        tas_g = np.maximum(tas_g, pad.stroke(pts, w + 2.5, 1.5, a0=0.55, a1=0.1, soft=1.0))
    st.body = np.maximum(st.body, tas_g)
    st.core = np.maximum(st.core, tas)
    # ---- 자루 · 감은 손잡이 · 자루 끝
    shaft = pad.poly([(17, cy - 2.4), (302, cy - 2.7), (302, cy + 2.7), (17, cy + 2.4)])
    sc_ = shaft * clamp01((cy - Y + 2.6) / 2.6) * 0.45 + rim_of(shaft, 0.5) * 0.35
    st.over(shaft, 0.74, sc_ * shaft)
    gx0, gx1 = 128.0, 196.0
    grip = pad.poly([(gx0, cy - 3.3), (gx1, cy - 3.3), (gx1, cy + 3.3), (gx0, cy + 3.3)])
    gc = rim_of(grip, 0.6) * 0.6
    x = gx0 + 1.5
    while x < gx1 - 1:
        gc = np.maximum(gc, pad.stroke([(x, cy - 3.6), (x + 3.4, cy + 3.6)], 0.8, a0=0.85) * grip)
        x += 4.0
    st.over(grip, 0.66, gc * grip)
    butt = pad.poly([(18, cy - 3.6), (8, cy), (18, cy + 3.6), (22, cy + 3.0), (22, cy - 3.0)])
    st.over(butt, 0.85, (rim_of(butt, 0.7) * 0.9 + butt * clamp01((cy - Y) / 3.5) * 0.4) * butt)
    for xx in (25.0, 300.0):
        rg = pad.poly([(xx - 2.5, cy - 3.6), (xx + 2.5, cy - 3.6), (xx + 2.5, cy + 3.6), (xx - 2.5, cy + 3.6)])
        st.over(rg, 0.86, rim_of(rg, 0.6) * 0.9 * rg)
    # ---- 가름쇠 (앞으로 기운 작은 날개 둘) · 창날 받침 (원뿔)
    lug = []
    for sg in (-1, 1):
        lug.append([(301, cy + sg * 2.5), (304, cy + sg * 12.5), (309, cy + sg * 14.5), (308, cy + sg * 9.5), (311, cy + sg * 3.0)])
    lm = pad.polys(lug)
    lit, dark, _ = bevel_light(lm, 1.5)
    st.over(lm, 0.86 + 0.1 * lit, (rim_of(lm, 0.7) * 0.9 + lit * 0.5) * lm)
    sock = pad.poly([(303, cy - 4.4), (322, cy - 3.0), (322, cy + 3.0), (303, cy + 4.4)])
    sk = rim_of(sock, 0.6) * 0.8 + sock * clamp01((cy - Y + 1) / 4.0) * 0.5
    for xx in (308.0, 316.0):
        sk = np.maximum(sk, pad.stroke([(xx, cy - 4.2), (xx, cy + 4.2)], 0.9, a0=0.9) * sock)
    st.over(sock, 0.84, sk * sock)
    # ---- 잎 창날 (가운데 능선 · 위 비탈 밝음 · 날 줄)
    lp, ltop, lbot = leaf_poly(316, 372, cy, 10.5)
    leaf = pad.poly(lp)
    v = (Y - cy) / 10.5
    de = inner_dist(leaf)
    lc = clamp01(1 - de / 2.4) ** 1.4 * leaf * 0.85
    lc = np.maximum(lc, leaf * clamp01(-v) ** 1.2 * 0.5)
    lc = np.maximum(lc, pad.stroke([(318, cy), (369, cy)], 1.0, 0.3, a0=0.95) * leaf)
    for pts_ in (ltop, lbot):
        lc = np.maximum(lc, pad.stroke(pts_, 0.6, a0=1.0) * leaf)
    st.over(leaf, 0.82 + 0.12 * clamp01(-v) - 0.12 * clamp01(v), lc * leaf)
    # ---- 흐르는 빛 (비스듬한 띠 · 이어 돌림) · 창끝 반짝임
    sil = np.maximum.reduce([shaft, grip, butt, lm, sock, leaf])
    L = 372 + 60
    sx = -30 + L * (f + 0.5) / frames
    swp = pad.zeros()
    for kk in (-1, 0, 1):
        u = (X - (sx + kk * L)) + (Y - cy) * 1.2
        swp = np.maximum(swp, np.exp(-0.5 * (u / 9.0) ** 2))
    st.add_core(swp * 0.85 * sil)
    tw = [1.0, 0.45, 0.8, 0.35][f]
    st.add_core(sparkle(pad, 369, cy, 5 + 3.5 * tw, 1.0, rot=0.2 * f))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.2, 0.5), (3.0, 0.35), (6.5, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.22)))
    return glow1, core1


@sprite("rfx_jspear2")
def make_spear2():
    glows, cores = run_frames(spear2_frame, 4)
    return Sprite("rfx_jspear2", "lookat_direction", 384, 64, 4, "lancer", glows, cores, cols=2)


# ------------------------------------------------------------ rfx_jmaul2 (번개 대망치 — 머리가 아래)

def maul2_frame(f, frames=4, W=256, H=384):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    st = Stack(pad)
    # ---- 자루 (위) · 감은 손잡이 · 고리 · 자루 끝
    shaft = pad.poly([(cx - 8.5, 40), (cx + 8.5, 40), (cx + 9.5, 246), (cx - 9.5, 246)])
    lit_s, dark_s, _ = bevel_light(shaft, 4.0)
    st.over(shaft, 0.7 + 0.15 * lit_s - 0.15 * dark_s, (rim_of(shaft, 0.8) * 0.5 + lit_s * 0.45) * shaft)
    grip = pad.poly([(cx - 10, 62), (cx + 10, 62), (cx + 10.5, 150), (cx - 10.5, 150)])
    gc = rim_of(grip, 0.7) * 0.55
    y = 64.0
    while y < 148:
        gc = np.maximum(gc, pad.stroke([(cx - 11, y), (cx + 11, y + 5.5)], 1.2, a0=0.8) * grip)
        y += 6.0
    gc = gc * (0.6 + 0.4 * clamp01((cx + 2 - X) / 12))
    st.over(grip, 0.62, gc * grip)
    for yy in (56.0, 156.0, 196.0):
        rg = pad.poly([(cx - 12, yy - 4), (cx + 12, yy - 4), (cx + 12, yy + 4), (cx - 12, yy + 4)])
        rl, rd, _ = bevel_light(rg, 2.0)
        st.over(rg, 0.85, (rim_of(rg, 0.8) * 0.85 + rl * 0.5) * rg)
    pm = pad.poly([(cx - 13, 40), (cx - 15, 30), (cx - 7, 20), (cx, 6), (cx + 7, 20), (cx + 15, 30), (cx + 13, 40)])
    pl, pd, _ = bevel_light(pm, 3.0)
    pc = rim_of(pm, 1.0) * 0.9 + pl * 0.6
    pc = np.maximum(pc, pad.poly([(cx, 22), (cx + 5, 30), (cx, 37), (cx - 5, 30)]) * 0.95)
    st.over(pm, 0.86 + 0.1 * pl - 0.2 * pd, pc * pm)
    # 머리를 잇는 목 (깃 · 덧댄 쇠)
    neck = pad.poly([(cx - 17, 226), (cx + 17, 226), (cx + 21, 252), (cx - 21, 252)])
    nl, nd, _ = bevel_light(neck, 2.5)
    st.over(neck, 0.84 + 0.1 * nl - 0.2 * nd, (rim_of(neck, 0.9) * 0.9 + nl * 0.5) * neck)
    for sg in (-1, 1):
        lg = pad.poly([(cx + sg * 6, 200), (cx + sg * 11, 200), (cx + sg * 13, 228), (cx + sg * 4, 228)])
        st.over(lg, 0.82, rim_of(lg, 0.7) * 0.85 * lg)
    # ---- 머리: 가운데 덩이 + 양끝 머리판 (때리는 면)
    blk = [(62, 254), (194, 254), (202, 262), (202, 338), (194, 346), (62, 346), (54, 338), (54, 262)]
    bm = pad.poly(blk)
    bl, bd, _ = bevel_light(bm, 7.0)
    bc = rim_of(bm, 1.2) * 0.85 + bl * 0.6
    # 위 · 아래 면 줄 (모서리 깎은 면)
    for yy in (262.0, 338.0):
        bc = np.maximum(bc, pad.stroke([(62, yy), (194, yy)], 0.9, a0=0.45) * bm)
    vsh = 1.0 - 0.2 * clamp01((Y - 262) / 84)  # 머리: 위가 밝고 아래로 어두워짐
    st.over(bm, (0.8 + 0.12 * bl - 0.25 * bd) * vsh, np.maximum(bc, bm * clamp01((290 - Y) / 30) * 0.22) * bm)
    for sg in (-1, 1):
        x0 = cx + sg * 72
        x1 = cx + sg * 96
        cap = [(x0, 246), (x0 + sg * 6, 240), (x1 - sg * 4, 244), (x1, 254), (x1, 346), (x1 - sg * 4, 356), (x0 + sg * 6, 360), (x0, 354)]
        cm = pad.poly(cap)
        cl, cd, _ = bevel_light(cm, 6.0)
        cc = rim_of(cm, 1.2) * 0.9 + cl * 0.6
        # 때리는 면 (바깥 끝) 의 돋은 테 · 가운데 마름모
        face = [(x1 - sg * 9, 258), (x1 - sg * 2, 262), (x1 - sg * 2, 338), (x1 - sg * 9, 342)]
        cc = np.maximum(cc, pad.stroke(face + [face[0]], 1.0, a0=0.7) * cm)
        mx = (x0 + x1) / 2
        dm = pad.poly([(mx, 284), (mx + 8, 300), (mx, 316), (mx - 8, 300)])
        cc = np.maximum(cc, dm * 0.75 + rim_of(dm, 0.8) * 0.25)
        for yy in (252.0, 348.0):
            cc = np.maximum(cc, pad.disc_field(mx, yy, 2.2) * 0.95)
        st.over(cm, (0.84 + 0.12 * cl - 0.26 * cd) * (1.0 - 0.2 * clamp01((Y - 256) / 96)), cc * cm)
    # ---- 룬 띠 (가운데 덩이를 가로지름) · 글자 다섯 (빛이 차례로 지나감)
    band = pad.poly([(56, 286), (200, 286), (200, 314), (56, 314)])
    bandc = rim_of(band, 1.0) * 0.8
    gl_c = pad.zeros()
    for k in range(5):
        gxk = 72 + k * 28
        b = 0.62 + 0.38 * max(0.0, math.cos(TAU * (k / 5 - f / frames))) ** 1.5
        gm = glyph_mask(pad, [1, 10, 5, 3, 8][k], gxk, 300, 15, 18, 2.2)
        gl_c = np.maximum(gl_c, gm * b)
    for xx in (60.0, 196.0):
        bandc = np.maximum(bandc, pad.disc_field(xx, 300, 2.0))
    st.over(band, 0.42, np.maximum(bandc, gl_c) * band)
    st.body = np.maximum(st.body, gblur(gl_c, 1.5 * SS) * 1.4 * band)
    sil = np.maximum.reduce([shaft, grip, pm, neck, bm, band] + [pad.poly([(cx + sg * 72, 240), (cx + sg * 96, 240), (cx + sg * 96, 360), (cx + sg * 72, 360)]) for sg in (-1, 1)])
    # ---- 번개: 머리 위를 기어 다니는 줄기 · 가장자리에서 튀는 짧은 갈래 · 자루를 타고 오르는 실 번개 (장마다 다름)
    rnd = random.Random(400 + f * 17)
    arcs_c = pad.zeros()
    arcs_g = pad.zeros()

    def on_head():
        return (rnd.uniform(40, 216), rnd.uniform(250, 350))

    for i in range(4):
        p0 = on_head()
        p1 = (min(216, max(40, p0[0] + rnd.choice((-1, 1)) * rnd.uniform(50, 110))), rnd.uniform(252, 348))
        pts = bolt_pts(rnd, p0, p1, 0.18, 5, 0.55)
        arcs_c = np.maximum(arcs_c, pad.stroke(pts, 2.1, a0=1.0))
        arcs_g = np.maximum(arcs_g, pad.stroke(pts, 7.0, a0=0.75, soft=3.0))
        # 갈래 하나
        j = rnd.randint(len(pts) // 4, len(pts) * 3 // 4)
        bx, by = pts[j]
        ba = rnd.uniform(0, TAU)
        br = bolt_pts(rnd, (bx, by), (bx + math.cos(ba) * 26, by + math.sin(ba) * 18), 0.3, 3, 0.6)
        arcs_c = np.maximum(arcs_c, pad.stroke(br, 1.4, 0.5, a0=0.9, a1=0.4))
        for q in (p0, p1):
            arcs_c = np.maximum(arcs_c, pad.disc_field(q[0], q[1], 2.2))
    for i in range(5):
        side = rnd.choice(("l", "r", "b", "t"))
        if side == "l":
            p0 = (32, rnd.uniform(262, 340)); ang = math.pi + rnd.uniform(-0.9, 0.9)
        elif side == "r":
            p0 = (224, rnd.uniform(262, 340)); ang = rnd.uniform(-0.9, 0.9)
        elif side == "b":
            p0 = (rnd.uniform(40, 216), 358); ang = math.pi / 2 + rnd.uniform(-0.7, 0.7)
        else:
            p0 = (rnd.uniform(40, 216), 242); ang = -math.pi / 2 + rnd.uniform(-0.7, 0.7)
        L = rnd.uniform(10, 16)
        p1 = (p0[0] + math.cos(ang) * L, p0[1] + math.sin(ang) * L)
        pts = bolt_pts(rnd, p0, p1, 0.3, 3, 0.6)
        arcs_c = np.maximum(arcs_c, pad.stroke(pts, 1.9, 0.9, a0=1.0, a1=0.7))
        arcs_g = np.maximum(arcs_g, pad.stroke(pts, 4.0, a0=0.35, soft=2.0))
    p0 = (cx + rnd.uniform(-4, 4), 230)
    pts = bolt_pts(rnd, p0, (cx + rnd.uniform(-5, 5), 150 - rnd.uniform(0, 40)), 0.06, 5, 0.6)
    pts = [(min(cx + 9, max(cx - 9, x)), y) for x, y in pts]
    arcs_c = np.maximum(arcs_c, pad.stroke(pts, 1.0, 0.4, a0=0.9, a1=0.2))
    arcs_g = np.maximum(arcs_g, pad.stroke(pts, 4.0, a0=0.5, a1=0.1, soft=2.0))
    for i in range(6):
        a = rnd.uniform(0, TAU)
        r = rnd.uniform(60, 120)
        x, y = cx + math.cos(a) * r, 300 + math.sin(a) * r * 0.5
        if 0 < x < W and 230 < y < 378:
            arcs_c = np.maximum(arcs_c, sparkle(pad, x, y, rnd.uniform(3, 7), 0.9, rot=rnd.uniform(0, 1), diag=0.0))
    # 번개 둘레를 살짝 어둡게 (몸 위에서 번개가 또렷이)
    rim_dark = clamp01(gblur(arcs_c, 2.2 * SS) * 2.2) * (1 - arcs_c)
    st.body = st.body * (1 - 0.3 * rim_dark * sil)
    st.add_core(arcs_c)
    st.body = np.maximum(st.body, arcs_g * (1 - sil * 0.6))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.6, 0.48), (4.5, 0.36), (10.0, 0.22)), body_k=1.0, core_bloom=((1.5, 0.35), (4.5, 0.3)))
    return glow1, core1


@sprite("rfx_jmaul2")
def make_maul2():
    glows, cores = run_frames(maul2_frame, 4)
    return Sprite("rfx_jmaul2", "lookat_y", 256, 384, 4, "paladin", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jbell2 (장식 종 — 흔들림 · 소리 고리)

BELL_R = [(13, 14), (24, 20), (31, 29), (36, 42), (39, 58), (41, 76), (44, 94), (49, 108), (57, 120), (65, 129), (71, 135)]


def bell2_frame(f, frames=8, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    k = 0.92
    piv = (128.0, 24.0)
    angs = [12, 6, 0, -6, -12, -6, 0, 6]
    th = math.radians(angs[f])
    xf = Xf(piv[0], piv[1], th, k, k)
    st = Stack(pad)
    mouth_y = 137.0
    # ---- 소리 고리 (종 아래로 퍼짐 — 뒤, 4 장마다 되풀이) · 양옆 소리 물결
    waves_c = pad.zeros()
    waves_g = pad.zeros()
    oc = (128.0, 196.0)
    for i in range(3):
        q = (i + (f % 4) / 4) / 3
        rx = 32 + 78 * q
        ry = rx * 0.26
        a = (1 - q) ** 1.3 * min(1.0, q * 6 + 0.2)
        pts = [(oc[0] + math.cos(t) * rx, oc[1] + math.sin(t) * ry) for t in np.linspace(0, TAU, 97)]
        d, s_ = pad.line_field(pts, 8)
        front = np.sin(s_ * TAU)
        aa = a * (0.45 + 0.55 * clamp01(front * 2 + 0.5))
        waves_c = np.maximum(waves_c, clamp01((1.0 + 0.5 * (1 - q) - d) * SS + 0.5) * aa)
        waves_g = np.maximum(waves_g, np.exp(-0.5 * (d / (2.0 + 2 * q)) ** 2) * aa * 0.7)
        for sg in (-1, 1):
            r = 70 + 32 * q
            arc = [(128 + sg * math.cos(t) * r, 92 + math.sin(t) * r * 1.05) for t in np.linspace(-0.45, 0.45, 15)]
            waves_c = np.maximum(waves_c, pad.stroke(arc, 1.2, a0=a * 0.8, taper=0.8))
            waves_g = np.maximum(waves_g, pad.stroke(arc, 5.0, a0=a * 0.5, taper=0.8, soft=2.0))
    st.core = np.maximum(st.core, waves_c)
    st.body = np.maximum(st.body, waves_g)
    # ---- 추 (안쪽에 매달려 늦게 따라옴 — 입 아래로 조금 나옴)
    ca = th * 0.45
    cxp, cyp = xf.p(0, 34)
    kx_, ky_ = cxp - math.sin(ca) * 112 * k, cyp + math.cos(ca) * 112 * k
    clap = np.maximum(pad.stroke([(cxp, cyp), (kx_, ky_)], 3.0 * k), pad.disc_field(kx_, ky_, 7.5 * k))
    st.over(clap, 0.8, (rim_of(clap, 0.8) * 0.8 + pad.disc_field(kx_ - 2, ky_ - 2, 2.6 * k) * 0.8) * clap)
    # ---- 입 안쪽 (뒤 벽 — 어둡게) · 종 몸
    inner = pad.poly(xf([(math.cos(t) * 68, mouth_y + math.sin(t) * 9) for t in np.linspace(math.pi, TAU, 40)] + [(68, mouth_y), (-68, mouth_y)]))
    right = BELL_R
    left = [(-x, y) for x, y in right[::-1]]
    lip = [(math.cos(t) * 71, mouth_y - 2 + math.sin(t) * 10) for t in np.linspace(0, math.pi, 41)]
    body_pts = xf(left + [(x, y) for x, y in right] + lip)
    bm = pad.poly(body_pts)
    st.over(inner, 0.42, rim_of(inner, 0.8) * 0.5 * inner)
    LX, LY = xf.inv(X, Y)

    def hw(y):
        ys = np.array([p[1] for p in right], np.float32)
        xs = np.array([p[0] for p in right], np.float32)
        return np.interp(y, ys, xs)

    u = LX / np.maximum(4.0, hw(LY))  # -1 왼쪽 → 1 오른쪽
    shade = 0.62 + 0.3 * clamp01(-u * 0.8 + 0.4) - 0.12 * clamp01(u)
    bc = rim_of(bm, 1.1) * 0.85
    bc = np.maximum(bc, np.exp(-0.5 * ((u + 0.52) / 0.13) ** 2) * bm * 0.65)
    bc = np.maximum(bc, np.exp(-0.5 * ((u - 0.78) / 0.07) ** 2) * bm * 0.3)
    # 장식 띠: 어깨 둘 · 마름모 줄 · 가운데 별 메달 · 소리 띠 둘 + 점 줄 · 입술 테
    def band(y, a=0.8, w=1.0):
        x = float(hw(np.array([y]))[0])
        pts = xf([(-x + 1, y), (-x * 0.5, y + 2.2), (0, y + 3.0), (x * 0.5, y + 2.2), (x - 1, y)])
        return pad.stroke(bez(pts[0], pts[2], pts[4], 16), w * k, a0=a) * bm

    for y, a in ((30.0, 0.85), (36.0, 0.6), (106.0, 0.8), (114.0, 0.6), (127.0, 0.85)):
        bc = np.maximum(bc, band(y, a))
    for i in range(-3, 4):
        x = i * 10.5
        dx, dy = xf.p(x, 47 + 2.6 * (1 - (i / 3.5) ** 2))
        bc = np.maximum(bc, pad.poly([(dx, dy - 3.2), (dx + 2.4, dy), (dx, dy + 3.2), (dx - 2.4, dy)], 0.85))
    for i in range(-4, 5):
        x = i * 11.5
        dx, dy = xf.p(x, 121 + 2.8 * (1 - (i / 4.5) ** 2))
        bc = np.maximum(bc, pad.disc_field(dx, dy, 1.5 * k) * 0.9)
    mx, my = xf.p(0, 76)
    bc = np.maximum(bc, pad.stroke(ring_pts(mx, my, 14 * k, 14 * k, 0, 0, TAU, 48), 1.2 * k, a0=0.85))
    bc = np.maximum(bc, pad.poly(star_pts(mx, my, 11 * k, 4, 0.2, th), 0.95))
    bc = np.maximum(bc, pad.poly(star_pts(mx, my, 6.5 * k, 4, 0.25, th + math.pi / 4), 0.8))
    lipline = xf([(math.cos(t) * 70, mouth_y - 2 + math.sin(t) * 9.5) for t in np.linspace(0.05, math.pi - 0.05, 30)])
    bc = np.maximum(bc, pad.stroke(lipline, 1.4 * k, a0=1.0) * bm)
    st.over(bm, shade, bc * bm)
    # ---- 꼭대기: 머리 (둥근 지붕) · 매다는 고리
    crown = pad.poly(xf([(-15, 16), (-13, 8), (-6, 3), (6, 3), (13, 8), (15, 16)]))
    cl, cd, _ = bevel_light(crown, 2.5)
    st.over(crown, 0.85 + 0.1 * cl, (rim_of(crown, 0.8) * 0.9 + cl * 0.5) * crown)
    lx_, ly_ = xf.p(0, -3)
    loop = clamp01(pad.disc_field(lx_, ly_, 8.5 * k) - pad.disc_field(lx_, ly_, 4.6 * k))
    st.over(loop, 0.88, (rim_of(loop, 0.7) * 0.6 + loop * 0.4) * loop)
    # 울릴 때 (양 끝) 입술의 번쩍임
    if f in (0, 4):
        sx_, sy_ = xf.p(-62 if f == 4 else 62, mouth_y + 4)
        st.add_core(sparkle(pad, sx_, sy_, 13, 1.0, rot=0.2))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.5, 0.45), (4.0, 0.35), (9.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.25)))
    return glow1, core1


@sprite("rfx_jbell2")
def make_bell2():
    glows, cores = run_frames(bell2_frame, 8)
    return Sprite("rfx_jbell2", "lookat_y", 256, 256, 8, "priest", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jbuckler2 (둥근 방패 — 앞면이 눌렸다 펴지며 돎)

def buckler2_frame(f, frames=8, S=128, card=False):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    R = 47.0
    phi = TAU * f / frames
    sq = 0.36 + 0.64 * abs(math.cos(phi))
    psi = (math.pi / 2) * f / frames * 2  # 면의 무늬가 돎 (4 겹 대칭 — 8 장에 180°)
    tilt = math.radians(18)
    if card:
        phi, sq, psi, tilt = 0.0, 1.0, 0.0, 0.0
    xf = Xf(c, c, tilt, sq, 1.0)

    def T(pts):
        cs, sn = math.cos(psi), math.sin(psi)
        return xf([(x * cs - y * sn, x * sn + y * cs) for x, y in pts])

    def circ(r, n=72):
        return [(math.cos(t) * r, math.sin(t) * r) for t in np.linspace(0, TAU, n + 1)[:-1]]

    st = Stack(pad)
    # ---- 테 두께 (눌렸을 때 옆에 보이는 둘레 면) — 뒤에 먼저
    thick = 6.0 * math.sin(phi) if abs(math.sin(phi)) > 0.05 else 0.0
    if thick != 0.0:
        dx, dy = xf.vec(thick, 0)
        side = pad.poly([(x + dx, y + dy) for x, y in T(circ(R))])
        st.over(side, 0.6, rim_of(side, 0.8) * 0.7 * side)
    face = pad.poly(T(circ(R)))
    lit, dark, _ = bevel_light(face, 5.0)
    LX, LY = xf.inv(X, Y)
    rr = np.sqrt(LX ** 2 + LY ** 2) / R
    # 면: 테 띠 (밝음) · 안쪽 판 (조금 어둡게) · 돌기 (밝은 돔)
    shade = np.where(rr > 0.84, 0.86, 0.66) + 0.12 * lit - 0.22 * dark
    core = rim_of(face, 0.9) * 0.9 + lit * 0.45
    core = np.maximum(core, pad.stroke(T(circ(R * 0.84)) + [T(circ(R * 0.84))[0]], 0.9, a0=0.7) * face)
    core = np.maximum(core, pad.stroke(T(circ(R * 0.55)) + [T(circ(R * 0.55))[0]], 0.7, a0=0.45) * face)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        rib = T([(math.cos(a) * R * 0.3, math.sin(a) * R * 0.3), (math.cos(a) * R * 0.84, math.sin(a) * R * 0.84)])
        core = np.maximum(core, pad.stroke(rib, 2.4, a0=0.6) * face)
        core = np.maximum(core, pad.stroke(rib, 0.8, a0=0.9) * face)
    for i in range(8):
        a = i * math.pi / 4
        sx_, sy_ = T([(math.cos(a) * R * 0.92, math.sin(a) * R * 0.92)])[0]
        stud = pad.disc_field(sx_, sy_, 2.6)
        core = np.maximum(core, stud * 0.75 + pad.disc_field(sx_ - 0.7, sy_ - 0.8, 1.0) * 0.25)
    # 돌기
    boss = pad.poly(T(circ(R * 0.27)))
    bl, bd, _ = bevel_light(boss, 6.0)
    bx, by = xf.p(-R * 0.08, -R * 0.08)
    spec = pad.disc_field(bx - 2, by - 3, 3.2, soft=1.5)
    core = np.maximum(core, rim_of(boss, 0.9) * 0.9 + bl * 0.6 + spec * boss)
    core = np.maximum(core, pad.stroke(T(circ(R * 0.33)) + [T(circ(R * 0.33))[0]], 1.0, a0=0.8) * face)
    shade = np.where(boss > 0.5, 0.92 + 0.08 * bl - 0.3 * bd, shade)
    st.over(face, shade, core * face)
    # ---- 꼬리: 테 밖을 도는 호 둘 (도는 쪽 뒤로 흐려짐) + 옅은 잔상 고리 (카드는 없음)
    trail_c = pad.zeros()
    trail_g = pad.zeros()
    for j in (range(2) if not card else ()):
        a_head = psi + j * math.pi + 0.4
        pts = []
        for i in range(25):
            t = i / 24
            a = a_head - t * 1.5
            pts.append((math.cos(a) * R * 1.12, math.sin(a) * R * 1.12))
        P_ = xf(pts)
        trail_c = np.maximum(trail_c, pad.stroke(P_, 1.3, 0.2, a0=0.85, a1=0.0))
        trail_g = np.maximum(trail_g, pad.stroke(P_, 5.0, 1.0, a0=0.5, a1=0.0, soft=2.0))
    ghost = pad.stroke(xf(circ(R * 1.04)) + [xf(circ(R * 1.04))[0]], 3.0, a0=0.12, soft=1.5) * (0.0 if card else 1.0)
    st.core = np.maximum(st.core, trail_c * (1 - face))
    body = np.maximum(st.body, np.maximum(trail_g, ghost) * (1 - face))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, body, core1, halo=((1.2, 0.48), (3.0, 0.35), (6.5, 0.2)), body_k=1.0, core_bloom=((1.2, 0.3), (3.0, 0.22)))
    return glow1, core1


@sprite("rfx_jbuckler2")
def make_buckler2():
    glows, cores = run_frames(buckler2_frame, 8)
    return Sprite("rfx_jbuckler2", "rotate_xyz", 128, 128, 8, "guardian", glows, cores, cols=4)


# ------------------------------------------------------------ rfx_jaegis2 (연 모양 문장 방패 — 나타남 · 버팀)

def aegis_pts():
    top = bez((38, 60), (128, 26), (218, 60), 24)
    rs = bez((218, 60), (232, 150), (128, 298), 30, p3=None)
    side_r = bez((218, 60), (226, 170), (128, 298), 30)
    side_l = [(256 - x, y) for x, y in side_r[::-1]]
    return top + side_r[1:] + side_l[1:-1]


def aegis_inset(pts, d):
    cx, cy = 128.0, 150.0
    out = []
    for x, y in pts:
        dx, dy = x - cx, y - cy
        L = math.hypot(dx, dy) or 1
        out.append((x - dx / L * d, y - dy / L * d * 1.05))
    return out


def herald_wing(sg, cx, cy, k):
    """문장 날개 하나 (sg 1 = 오른쪽): (윤곽 점들, 깃 나눔 줄들, 덮깃 줄)"""
    def P(x, y):
        return (cx + sg * (10 + x) * k, cy + y * k)
    top = bez(P(0, -2), P(26, -50), P(86, -60), 18)
    tips = [(86, -60), (82, -38), (72, -18), (56, -2), (38, 8)]
    edge = []
    for i in range(len(tips) - 1):
        a, b = tips[i], tips[i + 1]
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        # 깃 끝 사이 홈 (뿌리 쪽으로 당김)
        vx, vy = 12 - mx, -6 - my
        L = math.hypot(vx, vy) or 1
        notch = (mx + vx / L * 10, my + vy / L * 10)
        edge += bez(P(*a), P(a[0] - 2, a[1] + 8), P(*notch), 5)[:-1]
        edge += bez(P(*notch), P(b[0] + 3, b[1] - 3), P(*b), 5)[:-1]
    edge.append(P(*tips[-1]))
    outline = top + edge[1:] + [P(14, 10), P(2, 6)]
    feathers = []
    for i in range(1, len(tips)):
        a, b = tips[i - 1], tips[i]
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        vx, vy = 12 - mx, -6 - my
        L = math.hypot(vx, vy) or 1
        nx_, ny_ = mx + vx / L * 10, my + vy / L * 10
        feathers.append([P(nx_, ny_), P(nx_ + vx / L * 24, ny_ + vy / L * 24)])
    covert = bez(P(8, -8), P(30, -36), P(70, -46), 14)
    return outline, feathers, covert


def winged_star(pad, cx, cy, k=1.0, prog=1.0):
    """문장: 세로가 긴 네 뿔 별 + 양옆 위로 펼친 날개 + 위의 작은 별 셋 (prog 0 ~ 1 = 그려진 몫)"""
    core = pad.zeros()
    fill = pad.zeros()
    for sg in (-1, 1):
        outline, feathers, covert = herald_wing(sg, cx, cy - 6 * k, 0.8 * k)
        m = pad.poly(outline)
        X, Y = pad.grid()
        fill = np.maximum(fill, m * (0.72 + 0.25 * clamp01((cy - Y) / 50)))
        ol = outline + [outline[0]]
        n = max(2, int(len(ol) * prog))
        core = np.maximum(core, pad.stroke(ol[:n], 1.2 * k, a0=0.95))
        if prog > 0.9:
            for fl in feathers:
                core = np.maximum(core, pad.stroke(fl, 0.9 * k, a0=0.75, taper=0.5) * m)
            core = np.maximum(core, pad.stroke(covert, 0.9 * k, a0=0.6, taper=0.4) * m)
            core = np.maximum(core, m * clamp01((cy - 20 - Y) / 40) * 0.35)
    sp = star_pts(cx, cy, 50 * k, 4, 0.15, 0.0)
    sp = [(x, cy + (y - cy) * (1.0 if y < cy else 1.3)) for x, y in sp]
    sm = pad.poly(sp)
    fill = np.maximum(fill, sm)
    core = core * (1 - sm)
    outline = sp + [sp[0]]
    m_ = max(2, int(len(outline) * prog))
    core = np.maximum(core, pad.stroke(outline[:m_], 1.3 * k, a0=1.0))
    if prog > 0.9:
        core = np.maximum(core, sm * 0.35)
    core = np.maximum(core, pad.poly([(cx, cy - 13 * k), (cx + 8 * k, cy), (cx, cy + 16 * k), (cx - 8 * k, cy)]) * prog)
    for i, (dx, dy) in enumerate(((-30, -56), (0, -68), (30, -56))):
        core = np.maximum(core, pad.poly(star_pts(cx + dx * k, cy + dy * k, 5.5 * k, 4, 0.25)) * (prog > 0.5))
    return fill, core


def aegis2_frame(f, frames=6, W=256, H=320):
    pad = Pad(W, H)
    X, Y = pad.grid()
    outline = aegis_pts()
    shield = pad.poly(outline)
    inner = aegis_inset(outline, 11)
    inner2 = aegis_inset(outline, 16)
    im = pad.poly(inner)
    ecx, ecy = 128.0, 150.0
    fill_e, core_e = winged_star(pad, ecx, ecy, 1.0, [0.0, 0.55, 1.0, 1.0, 1.0, 1.0][f])
    body = pad.zeros()
    core = pad.zeros()
    glow_extra = pad.zeros()
    # 테 그리기 (위 가운데에서 양쪽 아래로)
    top_i = 12
    ring = outline[top_i:] + outline[:top_i + 1]
    half_r = ring[: len(ring) // 2 + 1]
    half_l = (outline[:top_i + 1][::-1] + outline[top_i:][::-1])[: len(ring) // 2 + 1]
    prog = [0.55, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    rim_core = pad.zeros()
    for hp in (half_r, half_l):
        n = max(2, int(len(hp) * prog))
        rim_core = np.maximum(rim_core, pad.stroke(hp[:n], 1.6, a0=1.0))
        if prog < 1.0:
            hx, hy = hp[n - 1]
            core = np.maximum(core, sparkle(pad, hx, hy, 15, 1.0, rot=0.3))
            glow_extra = np.maximum(glow_extra, pad.radial(hx, hy, 22, 1.5) * 0.7)
    core = np.maximum(core, rim_core)
    body = np.maximum(body, pad.stroke(ring, 6.0, a0=0.5 * prog, soft=2.0) * (prog >= 1.0) + rim_core * 0.8)
    if f == 0:
        # 빛 줄: 가운데 세로 · 가로 안내 줄이 그어짐 (모양이 잡히기 전)
        core = np.maximum(core, pad.stroke([(128, 44), (128, 250)], 1.0, a0=0.7, taper=0.6))
        core = np.maximum(core, pad.stroke([(58, 150), (198, 150)], 1.0, a0=0.6, taper=0.6))
        core = np.maximum(core, sparkle(pad, 128, 150, 16, 0.9, thin=0.12, diag=0.0))
        body = np.maximum(body, pad.stroke([(128, 44), (128, 250)], 5.0, a0=0.4, taper=0.6, soft=2.0))
        body = np.maximum(body, pad.stroke([(58, 150), (198, 150)], 5.0, a0=0.35, taper=0.6, soft=2.0))
    else:
        lit, dark, hgt = bevel_light(shield, 10.0)
        fieldk = [0.0, 0.28, 0.95, 0.66, 0.66, 0.66][f]
        tl = clamp01(1 - ((X - 60) / 220 + (Y - 40) / 300))
        field = shield * (fieldk * (0.75 + 0.25 * tl) - 0.12 * dark * (f >= 2))
        rimband = clamp01(shield - im) * (0.25 + 0.6 * min(1.0, fieldk + 0.2))
        body = np.maximum(body, np.maximum(field, rimband))
        body = np.maximum(body, fill_e * (0.35 + 0.6 * min(1.0, fieldk + 0.15)))
        k2 = 1.0 if f >= 2 else 0.6
        core = np.maximum(core, pad.stroke(inner + [inner[0]], 1.0, a0=0.8 * k2))
        core = np.maximum(core, pad.stroke(inner2 + [inner2[0]], 0.7, a0=0.5 * k2))
        core = np.maximum(core, core_e * k2)
        core = np.maximum(core, lit * 0.55 * (f >= 2))
        # 테의 징 (위 모서리 · 옆 · 아래 끝)
        if f >= 2:
            for idx in (2, 12, 22, 34, 46, 58, 70, 82):
                if idx < len(outline):
                    x, y = aegis_inset([outline[idx]], 5.5)[0]
                    core = np.maximum(core, pad.disc_field(x, y, 2.0) * 0.95)
        if f == 1:
            core = np.maximum(core, sparkle(pad, ecx, ecy, 28, 0.8, rot=0.0, thin=0.1))
        if f == 2:
            # 나타남 번쩍
            core = np.maximum(core, sparkle(pad, ecx, ecy, 95, 1.0, thin=0.06, diag=0.5))
            glow_extra = np.maximum(glow_extra, pad.radial(ecx, ecy, 140, 1.3) * 0.75)
        if f >= 3:
            # 비스듬히 지나가는 빛 (방패 안만)
            p = [0.18, 0.5, 0.82][f - 3]
            u = (X - (40 + 200 * p)) + (Y - 160) * 0.55
            swp = (np.exp(-0.5 * (u / 13) ** 2) + 0.5 * np.exp(-0.5 * ((u + 30) / 4) ** 2)) * shield
            core = np.maximum(core, swp * 0.7)
            tw = [0.6, 1.0, 0.7][f - 3]
            core = np.maximum(core, sparkle(pad, ecx, ecy - 64 * 1.0, 8 + 6 * tw, 0.9, rot=0.2 * f, diag=0.0))
            core = np.maximum(core, sparkle(pad, ecx + 2, ecy + 2, 16 + 10 * tw, 0.7 + 0.3 * tw, thin=0.1, diag=0.0))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.6, 0.45), (4.5, 0.33), (10.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.5, 0.25)))
    glow1 = screen(glow1, pad.down(glow_extra))
    return glow1, core1


@sprite("rfx_jaegis2")
def make_aegis2():
    glows, cores = run_frames(aegis2_frame, 6)
    return Sprite("rfx_jaegis2", "lookat_y", 256, 320, 6, "guardian", glows, cores, cols=6, zoom=4)


# ------------------------------------------------------------ rfx_jhits (직업별 맞은 자리 — 128×128 × 6장 × 직업 11)

def fx_finish(pad, glow_ss, core_ss, radii=((1.5, 0.5), (4.0, 0.42), (9.0, 0.28)), body_k=0.8, core_bloom=((1.5, 0.35), (4.0, 0.3))):
    """연출 그림의 빛 · 심지 (1배): 빛 = 넓은 모양 + 여러 겹 무리 + 심지 둘레 번짐"""
    core1 = pad.down(core_ss)
    g = pad.down(glow_ss)
    out = g * body_k
    for r, k in radii:
        out = screen(out, gblur(g, r) * k)
    for r, k in core_bloom:
        out = screen(out, gblur(core1, r) * k)
    return out, core1


def slash_band(pad, p0, p1, sag, wmax, a=1.0, head=1.0, tail=0.0, n=40, power=0.7):
    """초승달 베기 띠: p0 → p1 (sag = 가운데가 옆으로 휜 정도), 양끝 뾰족 · 가운데 wmax. head/tail = 보이는 몫"""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    pts = []
    for i in range(n + 1):
        t = tail + (head - tail) * i / n
        pts.append((x0 + dx * t + nx * sag * math.sin(math.pi * t), y0 + dy * t + ny * sag * math.sin(math.pi * t)))
    if head - tail < 0.02:
        return pad.zeros()
    return pad.poly(taper_poly(pts, lambda u: wmax * math.sin(math.pi * u) ** power), a)


def hit_flash(pad, c, f, r0=12.0, star_r=44.0, rot=0.0, thin=0.09, k=1.0, cy=None, gk=1.0):
    """처음 두 장의 번쩍 (심지 · 빛 공용 모양) — 가운데 (c, cy)"""
    if f > 1:
        return pad.zeros(), pad.zeros()
    cy = c if cy is None else cy
    kk = (1.0 if f == 0 else 0.75) * k
    core = np.maximum(pad.disc_field(c, cy, r0 * (0.8 if f == 0 else 0.55) * kk), sparkle(pad, c, cy, star_r * (0.8 if f == 0 else 1.0) * kk, 1.0, rot=rot, thin=thin, diag=0.4))
    glow = pad.disc_field(c, cy, r0 * 2.0 * kk, soft=r0 * 0.8) * (1.0 if f == 0 else 0.7) * gk
    return glow, core


def hit_swordsman(f, pad, c, X, Y):
    rnd = random.Random(1)
    lines = [(-28, 104, 0.0), (24, 96, 3.0), (80, 70, -4.0)]
    fade = [1.0, 1.0, 0.85, 0.62, 0.38, 0.16][f]
    wk = [1.25, 1.0, 0.75, 0.6, 0.45, 0.32][f]
    shrink = [0.0, 0.0, 0.06, 0.14, 0.24, 0.34][f]
    split = [0.0, 0.0, 1.6, 2.6, 3.4, 4.0][f]
    g, cc = pad.zeros(), pad.zeros()
    for i, (ang, L, off) in enumerate(lines):
        a = math.radians(ang)
        ux, uy = math.cos(a), math.sin(a)
        px, py = -uy, ux
        Lh = L / 2 * (1 - shrink) * (1 if i < 2 else 0.85)
        for sp in ((0.0,) if split == 0 else (-split / 2, split / 2)):
            cx_, cy_ = c + px * (off + sp), c + py * (off + sp)
            p0, p1 = (cx_ - ux * Lh, cy_ - uy * Lh), (cx_ + ux * Lh, cy_ + uy * Lh)
            w = (2.6 if i < 2 else 1.8) * wk * (0.75 if split else 1.0)
            cc = np.maximum(cc, pad.poly(taper_poly([p0, ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2), p1], lambda u: w * math.sin(math.pi * u) ** 0.6), fade))
            g = np.maximum(g, pad.poly(taper_poly([p0, ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2), p1], lambda u: (w * 2.6 + 2) * math.sin(math.pi * u) ** 0.8), fade * 0.8))
    gf, cf = hit_flash(pad, c, f, 10, 50, rot=0.3, thin=0.06)
    g, cc = np.maximum(g, gf), np.maximum(cc, cf)
    if f == 2:
        cc = np.maximum(cc, sparkle(pad, c + 3, c - 2, 24, 0.9, rot=0.3, thin=0.08))
    # 은빛 부스러기 (작은 마름모) — 줄의 수직 쪽으로 흩어짐
    if f >= 1:
        q = (f - 1) / 4
        for i in range(12):
            ang, L, off = lines[i % 3]
            a = math.radians(ang)
            t = rnd.uniform(-0.4, 0.4)
            sgn = rnd.choice((-1, 1))
            d = 4 + 20 * ease_out(q) * rnd.uniform(0.5, 1.0)
            x = c + math.cos(a) * L * t - math.sin(a) * sgn * d
            y = c + math.sin(a) * L * t + math.cos(a) * sgn * d
            r = rnd.uniform(1.5, 2.8) * (1 - 0.5 * q)
            cc = np.maximum(cc, pad.poly([(x, y - r * 1.6), (x + r, y), (x, y + r * 1.6), (x - r, y)], (1 - q) ** 1.2))
    return g, cc


def hit_warrior(f, pad, c, X, Y):
    rnd = random.Random(2)
    ang = math.radians(-38)
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    head = [0.88, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    tail = [0.0, 0.0, 0.1, 0.24, 0.42, 0.62][f]
    wk = [1.0, 1.0, 0.85, 0.66, 0.46, 0.28][f]
    fade = [1.0, 1.0, 0.9, 0.75, 0.55, 0.32][f]
    g, cc = pad.zeros(), pad.zeros()
    for k, off in enumerate((-22, 0, 22)):
        L = 104 - abs(off) * 0.9
        cx_, cy_ = c + px * off, c + py * off
        p0 = (cx_ - ux * L / 2, cy_ - uy * L / 2)
        p1 = (cx_ + ux * L / 2, cy_ + uy * L / 2)
        cc = np.maximum(cc, slash_band(pad, p0, p1, 9, 4.6 * wk, fade, head, tail))
        g = np.maximum(g, slash_band(pad, p0, p1, 9, 10.5 * wk + 2, fade * 0.9, head, tail, power=0.9))
    gf, cf = hit_flash(pad, c, f, 10, 44, rot=0.2, thin=0.08, gk=0.55)
    g, cc = np.maximum(g, gf), np.maximum(cc, cf)
    # 불티: 짧은 줄이 바깥으로 튀며 아래로 처짐
    if f >= 1:
        q = (f - 1) / 4
        for i in range(14):
            a = rnd.uniform(0, TAU)
            sp = rnd.uniform(0.6, 1.0)
            r0 = 10 + 50 * ease_out(q) * sp
            r1 = r0 - 9 * (1 - q) * sp
            drop = 14 * q * q * sp
            x0, y0 = c + math.cos(a) * r1, c + math.sin(a) * r1 + drop * 0.6
            x1, y1 = c + math.cos(a) * r0, c + math.sin(a) * r0 + drop
            al = (1 - q) ** 1.1
            cc = np.maximum(cc, pad.stroke([(x0, y0), (x1, y1)], 0.5, 1.8, a0=al * 0.5, a1=al))
            g = np.maximum(g, pad.stroke([(x0, y0), (x1, y1)], 1.5, 4.5, a0=al * 0.3, a1=al * 0.6, soft=1.0))
    return g, cc


def crack_lines(rnd, lines, jit=1.6):
    out = []
    for ln in lines:
        pts = []
        for a, b in zip(ln, ln[1:]):
            seg = bolt_pts(rnd, a, b, 0.07, 3, 0.6)
            pts += seg[:-1]
        pts.append(ln[-1])
        out.append(pts)
    return out


def hit_spellblade(f, pad, c, X, Y):
    rnd = random.Random(3)
    lines, dots = glyph_lines(4, c, c, 46, 58)
    frame_l = [[(c, c - 44), (c + 38, c), (c, c + 44), (c - 38, c), (c, c - 44)]]
    cr = crack_lines(random.Random(33), lines + frame_l)
    fade = [1.0, 1.0, 0.85, 0.62, 0.4, 0.18][f]
    wk = [1.4, 1.15, 1.0, 0.85, 0.7, 0.55][f]
    gap = [0.0, 0.0, 0.08, 0.18, 0.3, 0.42][f]
    g, cc = pad.zeros(), pad.zeros()
    for li, ln in enumerate(cr):
        n = len(ln)
        # 금이 끊어지며 조각남 (구간마다 사이가 벌어짐)
        segs = 5
        for k in range(segs):
            a0 = k / segs + gap / segs / 2
            a1 = (k + 1) / segs - gap / segs / 2
            sub = ln[int(a0 * (n - 1)): max(int(a0 * (n - 1)) + 2, int(a1 * (n - 1)) + 1)]
            w = (1.9 if li < len(lines) else 1.3) * wk
            cc = np.maximum(cc, pad.stroke(sub, w, a0=fade))
            g = np.maximum(g, pad.stroke(sub, w * 3.2 + 2, a0=fade * 0.75, soft=1.5))
    for x, y in dots:
        cc = np.maximum(cc, pad.poly([(x, y - 3.5), (x + 3.5, y), (x, y + 3.5), (x - 3.5, y)], fade))
    gf, cf = hit_flash(pad, c, f, 12, 46, rot=0.0, thin=0.08)
    g, cc = np.maximum(g, gf), np.maximum(cc, cf)
    # 조각 (결정 세모) 이 돌며 흩어짐
    if f >= 1:
        q = (f - 1) / 4
        for i in range(11):
            a = i * TAU / 11 + rnd.uniform(-0.2, 0.2)
            d = 18 + 44 * ease_out(q) * rnd.uniform(0.7, 1.0)
            x, y = c + math.cos(a) * d, c + math.sin(a) * d
            sz = rnd.uniform(3.0, 5.5) * (1 - 0.4 * q)
            tri = rot_pts([(x, y - sz * 1.5), (x + sz * 0.7, y + sz * 0.6), (x - sz * 0.7, y + sz * 0.6)], x, y, rnd.uniform(0, TAU) + q * 4)
            m = pad.poly(tri)
            cc = np.maximum(cc, rim_of(m, 0.6) * (1 - q) + m * 0.4 * (1 - q))
            g = np.maximum(g, m * (1 - q) * 0.9)
    return g, cc


def hit_mage(f, pad, c, X, Y):
    rnd = random.Random(4)
    rs = [50, 46, 32, 21, 13, 7][f]
    fade = [1.0, 1.0, 0.9, 0.75, 0.55, 0.35][f]
    g, cc = pad.zeros(), pad.zeros()
    rot = 0.12 * f
    cc = np.maximum(cc, pad.poly(star_pts(c, c, rs, 4, 0.09, rot), fade))
    cc = np.maximum(cc, pad.poly(star_pts(c, c, rs * 0.55, 4, 0.14, rot + math.pi / 4), fade * 0.9))
    g = np.maximum(g, pad.poly(star_pts(c, c, rs * 1.08, 4, 0.2, rot), fade))
    g = np.maximum(g, pad.poly(star_pts(c, c, rs * 0.62, 4, 0.26, rot + math.pi / 4), fade))
    cc = np.maximum(cc, pad.disc_field(c, c, [9, 7, 5, 3.5, 2.5, 1.5][f]) * fade)
    g = np.maximum(g, pad.disc_field(c, c, [20, 17, 12, 8, 5, 3][f], soft=4) * fade)
    if f >= 1:
        R = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
        rr = [0, 24, 34, 42, 48, 52][f]
        a = [0, 0.9, 0.75, 0.5, 0.3, 0.14][f]
        cc = np.maximum(cc, np.exp(-0.5 * ((R - rr) / 0.8) ** 2) * a)
        g = np.maximum(g, np.exp(-0.5 * ((R - rr) / 3.0) ** 2) * a * 0.8)
    # 반짝임: 바깥으로 떠가며 깜빡임
    for i in range(9):
        a0 = rnd.uniform(0, TAU)
        d0 = rnd.uniform(14, 30)
        sp = rnd.uniform(18, 30)
        q = f / 5
        d = d0 + sp * q
        x, y = c + math.cos(a0) * d, c + math.sin(a0) * d
        tw = 0.5 + 0.5 * math.cos(f * 1.9 + i * 1.3)
        al = (0.4 + 0.6 * tw) * (1 - q ** 1.5) * (0.4 if f == 0 else 1.0)
        r = rnd.uniform(3.5, 7.0) * (0.6 + 0.6 * tw)
        cc = np.maximum(cc, sparkle(pad, x, y, r, al, rot=rnd.uniform(0, 0.8), diag=0.0))
        g = np.maximum(g, pad.disc_field(x, y, r * 0.5, soft=1.5) * al)
    return g, cc


def small_feather(pad, x, y, ang, L, W, a=1.0):
    """작은 깃털 (깃대 · 깃 · 끝이 둥긂) — (빛 모양, 심지)"""
    ux, uy = math.cos(ang), math.sin(ang)
    spine = [(x - ux * L / 2, y - uy * L / 2), (x + ux * L / 2 + (-uy) * L * 0.06, y + uy * L / 2 + ux * L * 0.06)]
    pts = [(spine[0][0] + (spine[1][0] - spine[0][0]) * t, spine[0][1] + (spine[1][1] - spine[0][1]) * t + 0 * t) for t in np.linspace(0, 1, 12)]
    poly = taper_poly(pts, lambda t: W * math.sin(math.pi * min(1.0, 0.1 + t * 0.95)) ** 0.6 * (0.6 + 0.4 * t))
    m = pad.poly(poly, a)
    core = np.maximum(rim_of(m, 0.6) * a * 0.9, pad.stroke(spine, 0.7, 0.3, a0=a, a1=a * 0.6))
    return m, core


def hit_archer(f, pad, c, X, Y):
    rnd = random.Random(5)
    q = f / 5
    g, cc = pad.zeros(), pad.zeros()
    # 바람 소용돌이 (가는 나선 셋 — 돌며 퍼짐)
    for k in range(3):
        a0 = k * TAU / 3 + 0.5 * f
        pts = []
        for i in range(30):
            t = i / 29
            a = a0 + t * 2.2
            r = (8 + 34 * t) * (0.7 + 0.5 * ease_out(q))
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        al = [0.9, 1.0, 0.85, 0.6, 0.38, 0.16][f]
        cc = np.maximum(cc, pad.stroke(pts, 0.4, 2.2, a0=0.2 * al, a1=al))
        g = np.maximum(g, pad.stroke(pts, 1.5, 6.0, a0=0.1 * al, a1=al * 0.7, soft=1.5))
    # 깃털이 터지며 날아감 (돌며)
    for i in range(7):
        a = i * TAU / 7 + rnd.uniform(-0.25, 0.25)
        d = 8 + 33 * ease_out(min(1.0, (f + 0.6) / 4.5)) * rnd.uniform(0.75, 1.0)
        x, y = c + math.cos(a) * d, c + math.sin(a) * d
        spin = a + rnd.uniform(-0.6, 0.6) + q * rnd.uniform(1.5, 3.0)
        al = [0.85, 1.0, 1.0, 0.8, 0.55, 0.28][f]
        m, co = small_feather(pad, x, y, spin, 19 * (0.8 + 0.3 * min(1, f / 2)), 4.2, al)
        g = np.maximum(g, m)
        cc = np.maximum(cc, co)
    gf, cf = hit_flash(pad, c, f, 11, 40, rot=0.4, thin=0.1)
    return np.maximum(g, gf), np.maximum(cc, cf)


def petal_shape(pad, x, y, ang, L, W, a=1.0):
    """꽃잎 하나 (끝이 살짝 패인 둥근 잎) — (빛 모양, 심지)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    left, right = [], []
    for i in range(15):
        t = i / 14
        w = W * math.sin(math.pi * min(1.0, 0.06 + t * 0.94)) ** 0.7 * (0.45 + 0.55 * t)
        d = -L / 2 + L * t
        left.append((x + ux * d + px * w, y + uy * d + py * w))
        right.append((x + ux * d - px * w, y + uy * d - py * w))
    notch = (x + ux * L * 0.4, y + uy * L * 0.4)
    poly = left + [notch] + right[::-1]
    m = pad.poly(poly, a)
    core = np.maximum(rim_of(m, 0.7) * a * 0.85, pad.stroke([(x - ux * L * 0.4, y - uy * L * 0.4), (x + ux * L * 0.2, y + uy * L * 0.2)], 0.6, a0=a * 0.6))
    return m, core


def hit_assassin(f, pad, c, X, Y):
    rnd = random.Random(6)
    g, cc = pad.zeros(), pad.zeros()
    fade = [1.0, 1.0, 0.82, 0.6, 0.38, 0.16][f]
    wk = [1.0, 0.9, 0.7, 0.55, 0.42, 0.3][f]
    for k, (p0, p1, sag) in enumerate((((10, 16), (118, 112), 6), ((116, 14), (12, 114), -6))):
        cc = np.maximum(cc, slash_band(pad, p0, p1, sag, 3.4 * wk, fade))
        g = np.maximum(g, slash_band(pad, p0, p1, sag, 8.0 * wk + 1.5, fade * 0.9, power=0.9))
    gf, cf = hit_flash(pad, c, f, 10, 42, rot=math.pi / 4, thin=0.08)
    g, cc = np.maximum(g, gf), np.maximum(cc, cf)
    # 꽃잎이 돌며 흩날림
    for i in range(8):
        a = i * TAU / 8 + rnd.uniform(-0.3, 0.3)
        q = min(1.0, (f + 0.4) / 5)
        d = 10 + 32 * ease_out(q) * rnd.uniform(0.7, 1.0)
        x, y = c + math.cos(a) * d, c + math.sin(a) * d + 5 * q * q
        al = [0.5, 0.95, 1.0, 0.85, 0.6, 0.3][f]
        m, co = petal_shape(pad, x, y, a + rnd.uniform(0, TAU) + f * 0.9, 12, 4.6 * (0.6 + 0.4 * abs(math.cos(f * 1.1 + i))), al)
        g = np.maximum(g, m)
        cc = np.maximum(cc, co)
    return g, cc


def hit_lancer(f, pad, c, X, Y):
    g, cc = pad.zeros(), pad.zeros()
    ax = c - 16  # 꿰뚫은 자리
    fade = [1.0, 1.0, 0.85, 0.65, 0.42, 0.2][f]
    # 꿰뚫는 줄 (가운데가 굵고 앞으로 길게)
    lw = [3.2, 2.6, 1.9, 1.4, 1.0, 0.7][f]
    line = [(10, c), (ax, c), (118, c)]
    cc = np.maximum(cc, pad.poly(taper_poly(line, lambda u: lw * math.sin(math.pi * u) ** 0.5), fade))
    g = np.maximum(g, pad.poly(taper_poly(line, lambda u: (lw * 2.8 + 2) * math.sin(math.pi * u) ** 0.6), fade * 0.85))
    # 원뿔 충격 (앞으로 벌어지는 두 줄)
    if f >= 1:
        q = (f - 1) / 4
        spread = 22 + 16 * ease_out(q)
        L = 46 + 20 * ease_out(q)
        for sg in (-1, 1):
            pts = [(ax + 4, c + sg * 2), (ax + 4 + L * 0.5, c + sg * spread * 0.55), (ax + 4 + L, c + sg * spread)]
            al = (1 - q) ** 1.2
            cc = np.maximum(cc, pad.stroke(pts, 1.6, 0.4, a0=al, a1=al * 0.3))
            g = np.maximum(g, pad.stroke(pts, 5.0, 2.0, a0=al * 0.8, a1=al * 0.2, soft=1.5))
    # 고리 (줄을 감싸는 충격파 — 비스듬히 보여 납작한 타원 · 앞으로 가며 커짐)
    for k, (dly, kk) in enumerate(((0, 1.0), (1, 0.7))):
        ff = f - dly
        if ff < 0:
            continue
        q = ff / 4
        rx = 4 + 7 * q
        ry = 12 + 30 * ease_out(min(1, q))
        x = ax + 8 + 40 * ease_out(min(1, q))
        pts = [(x + math.cos(t) * rx, c + math.sin(t) * ry) for t in np.linspace(0, TAU, 49)]
        al = max(0.0, 1 - q) ** 1.1 * kk
        cc = np.maximum(cc, pad.stroke(pts, 1.8 * (1 - 0.5 * q), a0=al))
        g = np.maximum(g, pad.stroke(pts, 6.0, a0=al * 0.8, soft=2.0))
    gf, cf = hit_flash(pad, ax, f, 11, 40, rot=0.0, thin=0.08, cy=c, gk=0.8)
    return np.maximum(g, gf), np.maximum(cc, cf)


def hit_reaper(f, pad, c, X, Y):
    rnd = random.Random(8)
    g, cc = pad.zeros(), pad.zeros()
    # 연기 (부드러운 덩이 — 빛 장면 · 심지에는 아주 옅게)
    q = f / 5
    smoke = pad.zeros()
    for i in range(10):
        a = rnd.uniform(0, TAU)
        d = rnd.uniform(4, 14) + 16 * ease_out(q)
        x = c + math.cos(a) * d
        y = c + 10 + math.sin(a) * d * 0.6 - 26 * q * rnd.uniform(0.3, 1.0)
        r = rnd.uniform(8, 12) * (0.6 + 0.7 * ease_out(q))
        smoke = np.maximum(smoke, pad.disc_field(x, y, r, soft=r * 0.9))
    sa = [0.5, 0.7, 0.62, 0.48, 0.3, 0.14][f]
    g = np.maximum(g, smoke * sa)
    cc = np.maximum(cc, smoke * sa * 0.18)
    # 번쩍 고리 (처음 둘)
    if f <= 1:
        R = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
        rr = 12 if f == 0 else 20
        cc = np.maximum(cc, np.exp(-0.5 * ((R - rr) / 1.0) ** 2) * (1.0 if f == 0 else 0.55))
        g = np.maximum(g, np.exp(-0.5 * ((R - rr) / 3.5) ** 2) * 0.8)
    # 혼불: 둥근 머리 · 흔들리는 꼬리 — 위로 빠져나감
    if f >= 1:
        rise = [0, 0, 10, 22, 34, 44][f]
        hy = c - 2 - rise
        sway = 4.0 * math.sin(f * 1.7)
        al = [0, 0.9, 1.0, 0.92, 0.7, 0.4][f]
        hr = [0, 10.5, 12.5, 12.5, 12.0, 11.0][f]
        tail = []
        for i in range(24):
            t = i / 23
            tail.append((c + sway * t + 7 * math.sin(t * 5 + f * 1.3) * t, hy + hr * 0.4 + 42 * t))
        wisp = pad.poly(taper_poly(tail, lambda t: hr * 0.95 * (1 - t) ** 0.85 + 0.4)) * al
        head = pad.disc_field(c, hy, hr) * al
        wm = np.maximum(wisp, head)
        g = np.maximum(g, wm)
        inner = np.maximum(pad.disc_field(c, hy + 1, hr * 0.58) * al, pad.poly(taper_poly(tail[:14], lambda t: hr * 0.42 * (1 - t))) * al * 0.85)
        cc = np.maximum(cc, inner)
        cc = np.maximum(cc, rim_of(np.maximum(wm, 0), 0.8) * 0.75 * al)
        # 눈 (비움 — 어둡게 보임)
        for sg in (-1, 1):
            ex = c + sg * hr * 0.36
            eye = pad.poly(rot_pts([(ex - hr * 0.17, hy - 0.8), (ex + hr * 0.17, hy - 0.8), (ex, hy + hr * 0.3)], ex, hy, sg * 0.3))
            cc = cc * (1 - eye)
            g = g * (1 - eye * 0.85)
    gf, cf = hit_flash(pad, c, f, 9, 30, rot=0.0, thin=0.1, k=0.8)
    return np.maximum(g, gf * 0.8), np.maximum(cc, cf * 0.85)


def hit_paladin(f, pad, c, X, Y):
    g, cc = pad.zeros(), pad.zeros()
    seed = [9, 19, 19, 29, 29, 29][f]
    rnd = random.Random(seed)
    fade = [1.0, 1.0, 0.85, 0.6, 0.38, 0.18][f]
    wk = [1.0, 1.15, 0.85, 0.65, 0.5, 0.38][f]
    reach = [0.75, 1.0, 1.0, 0.95, 0.9, 0.85][f]
    for i in range(6):
        a = i * TAU / 6 + rnd.uniform(-0.35, 0.35)
        L = rnd.uniform(40, 50) * reach
        p1 = (c + math.cos(a) * L, c + math.sin(a) * L)
        pts = bolt_pts(rnd, (c, c), p1, 0.2, 5, 0.55)
        cc = np.maximum(cc, pad.stroke(pts, 2.4 * wk, 0.6 * wk, a0=fade, a1=fade * 0.6))
        g = np.maximum(g, pad.stroke(pts, 7.0 * wk, 2.0, a0=fade * 0.85, a1=fade * 0.4, soft=2.0))
        for b in range(2):
            j = rnd.randint(len(pts) // 3, len(pts) - 4)
            bx, by = pts[j]
            ba = a + rnd.choice((-1, 1)) * rnd.uniform(0.4, 0.9)
            bl = rnd.uniform(10, 20) * reach
            br = bolt_pts(rnd, (bx, by), (bx + math.cos(ba) * bl, by + math.sin(ba) * bl), 0.25, 3, 0.6)
            cc = np.maximum(cc, pad.stroke(br, 1.3 * wk, 0.4, a0=fade * 0.85, a1=fade * 0.3))
            g = np.maximum(g, pad.stroke(br, 4.0 * wk, a0=fade * 0.6, soft=1.5))
        if f >= 2:
            ex, ey = pts[-1]
            cc = np.maximum(cc, pad.disc_field(ex, ey, 1.8) * fade)
    gf, cf = hit_flash(pad, c, f, 13, 46, rot=0.0, thin=0.08)
    return np.maximum(g, gf), np.maximum(cc, cf)


def hit_priest(f, pad, c, X, Y):
    rnd = random.Random(10)
    g, cc = pad.zeros(), pad.zeros()
    L1 = [18, 38, 41, 43, 45, 46][f]
    W1 = [5.5, 9.5, 10.5, 11.5, 12.0, 12.5][f]
    al = [0.9, 1.0, 0.95, 0.75, 0.5, 0.25][f]
    rot = 0.06 * f
    layers = [(8, L1, W1, rot, al)]
    if f >= 1:
        layers.append((8, L1 * 0.62, W1 * 0.7, rot + math.pi / 8, al * 0.95))
    for n, L, W, r0, a in layers:
        for i in range(n):
            ang = r0 + i * TAU / n - math.pi / 2
            ux, uy = math.cos(ang), math.sin(ang)
            px, py = -uy, ux
            left, right = [], []
            for k in range(17):
                t = k / 16
                w = W * math.sin(math.pi * min(1.0, t * 1.0)) ** 0.75 * (1 - 0.35 * t)
                d = 4 + L * t
                left.append((c + ux * d + px * w, c + uy * d + py * w))
                right.append((c + ux * d - px * w, c + uy * d - py * w))
            m = pad.poly(left + right[::-1])
            g = np.maximum(g, m * a * 0.85)
            cc = np.maximum(cc, rim_of(m, 0.7) * a * 0.9)
            cc = np.maximum(cc, pad.stroke([(c + ux * 6, c + uy * 6), (c + ux * (4 + L * 0.8), c + uy * (4 + L * 0.8))], 0.8, 0.2, a0=a * 0.7, a1=0.1))
    cc = np.maximum(cc, pad.disc_field(c, c, [7, 8, 7, 6, 5, 4][f]) * al)
    if f >= 1:
        R = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
        rr = [0, 30, 38, 44, 48, 51][f]
        cc = np.maximum(cc, np.exp(-0.5 * ((R - rr) / 0.8) ** 2) * al * 0.7)
        g = np.maximum(g, np.exp(-0.5 * ((R - rr) / 3) ** 2) * al * 0.5)
    # 오르는 빛 알갱이
    for i in range(7):
        x = c + rnd.uniform(-44, 44)
        y0 = c + rnd.uniform(-10, 30)
        y = y0 - 12 * f * rnd.uniform(0.6, 1.0)
        a = (0.3 + 0.7 * math.sin(math.pi * min(1, f / 5 + rnd.uniform(0, 0.3)))) * (f >= 1)
        cc = np.maximum(cc, sparkle(pad, x, y, rnd.uniform(3, 5.5), a, diag=0.0))
    gf, cf = hit_flash(pad, c, f, 12, 44, rot=math.pi / 8, thin=0.08)
    return np.maximum(g, gf), np.maximum(cc, cf)


def hit_guardian(f, pad, c, X, Y):
    rnd = random.Random(11)
    g, cc = pad.zeros(), pad.zeros()

    def hexp(r, rot=math.pi / 6):
        return [(c + math.cos(rot + i * TAU / 6) * r, c + math.sin(rot + i * TAU / 6) * r) for i in range(6)]

    ro = [30, 38, 38, 38, 38, 38][f]
    ri = ro * 0.7
    q = 0.0 if f <= 1 else (f - 1) / 4
    push = 13 * ease_out(q)
    al = [1.0, 1.0, 0.88, 0.66, 0.42, 0.2][f]
    outer, inner = hexp(ro), hexp(ri)
    for i in range(6):
        # 육각 테를 이루는 쇠 판 여섯 (깨진 뒤 바깥으로 밀리며 돎)
        a0, a1 = outer[i], outer[(i + 1) % 6]
        b0, b1 = inner[i], inner[(i + 1) % 6]
        mx, my = (a0[0] + a1[0] + b0[0] + b1[0]) / 4, (a0[1] + a1[1] + b0[1] + b1[1]) / 4
        dx, dy = mx - c, my - c
        L = math.hypot(dx, dy)
        ox, oy = dx / L * push, dy / L * push
        gapk = 0.0 if f <= 1 else 1.6
        quad = [a0, a1, b1, b0]
        # 판 사이 틈
        quad = [(x + (mx - x) * gapk / 12, y + (my - y) * gapk / 12) for x, y in quad]
        quad = [(x + ox, y + oy) for x, y in quad]
        quad = rot_pts(quad, mx + ox, my + oy, q * 0.9 * (1 if i % 2 else -1))
        shrink = 1 - 0.3 * q
        quad = [(mx + ox + (x - mx - ox) * shrink, my + oy + (y - my - oy) * shrink) for x, y in quad]
        m = pad.poly(quad)
        lit, dark, _ = bevel_light(m, 2.2)
        g = np.maximum(g, m * al * (0.8 + 0.2 * lit - 0.2 * dark))
        cc = np.maximum(cc, (rim_of(m, 0.8) * 0.95 + lit * 0.6) * al)
    if f <= 1:
        # 안쪽 판 (흐린 육각) · 가운데 번쩍 · 모서리 못
        im = pad.poly(hexp(ri - 1))
        g = np.maximum(g, im * (0.45 if f == 0 else 0.35))
        cc = np.maximum(cc, pad.stroke(hexp(ri * 0.55) + [hexp(ri * 0.55)[0]], 1.0, a0=0.7))
        for x, y in outer:
            cc = np.maximum(cc, pad.disc_field(x, y, 2.0))
    # 쇠 부스러기 · 불티
    if f >= 2:
        for i in range(11):
            a = rnd.uniform(0, TAU)
            d = 14 + 36 * ease_out(q) * rnd.uniform(0.6, 1.0)
            x, y = c + math.cos(a) * d, c + math.sin(a) * d
            sz = rnd.uniform(1.8, 3.2) * (1 - 0.4 * q)
            quad = rot_pts([(x - sz, y - sz * 0.6), (x + sz * 0.8, y - sz), (x + sz, y + sz * 0.5), (x - sz * 0.6, y + sz)], x, y, rnd.uniform(0, TAU) + q * 3)
            m = pad.poly(quad)
            a2 = (1 - q) ** 1.1
            cc = np.maximum(cc, (rim_of(m, 0.5) * 0.8 + m * 0.5) * a2)
            g = np.maximum(g, m * a2 * 0.8)
    gf, cf = hit_flash(pad, c, f, 12, 46, rot=0.0, thin=0.08)
    return np.maximum(g, gf), np.maximum(cc, cf)


HITS = {"swordsman": hit_swordsman, "warrior": hit_warrior, "spellblade": hit_spellblade, "mage": hit_mage, "archer": hit_archer, "assassin": hit_assassin,
        "lancer": hit_lancer, "reaper": hit_reaper, "paladin": hit_paladin, "priest": hit_priest, "guardian": hit_guardian}


def hit_frame(job, f, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    g, cc = HITS[job](f, pad, c, X, Y)
    return fx_finish(pad, g, cc, radii=((1.2, 0.5), (3.2, 0.4), (7.0, 0.26)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.28)))


@sprite("rfx_jhits")
def make_hits():
    glows, cores = [], []
    for job in JOBS:
        gl, co = run_frames(lambda f, j=job: hit_frame(j, f), 6)
        glows.append(gl)
        cores.append(co)
    return Sprite("rfx_jhits", "rotate_xyz", 128, 128, 6, "swordsman", glows, cores, cols=12, zoom=1, layout="jobs")


# ------------------------------------------------------------ rfx_jvalwing (등 뒤의 빛 깃털 날개 — 날갯짓 이어 돌림)

def wing_feather(pad, base, ang, L, W, curve=0.08):
    """깃털 하나 (base 에서 ang 쪽으로 L · 폭 W, 조금 휨): (모양, 깃대 점들, 끝 점)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    spine = []
    for i in range(16):
        t = i / 15
        bend = curve * L * math.sin(math.pi * t * 0.9)
        spine.append((base[0] + ux * L * t + px * bend, base[1] + uy * L * t + py * bend))
    poly = taper_poly(spine, lambda t: W * (math.sin(math.pi * min(1.0, 0.1 + t * 0.92)) ** 0.5) * (0.55 + 0.45 * t ** 0.6))
    return pad.poly(poly), spine


def valwing_frame(f, frames=6, W=256, H=192):
    pad = Pad(W, H)
    lift = 0.5 - 0.5 * math.cos(TAU * f / frames)  # 0 = 내림 (넓게 펼침) → 1 = 올림
    st = Stack(pad)
    glow_extra = pad.zeros()
    for sg in (-1, 1):
        R = (128 + sg * 8, 112)
        tx, ty = lerp(90, 76, lift), lerp(-30, -76, lift)
        T = (R[0] + sg * tx, R[1] + ty)
        # 앞 가장자리 (뼈): 등에서 위로 솟았다가 바깥 끝으로
        C1 = (R[0] + sg * 14, R[1] - 42 - 10 * lift)
        C2 = (R[0] + sg * tx * 0.55, R[1] + ty - 16 + 6 * lift)
        lead = bez(R, C1, C2, 40, p3=T)
        # 뒤 가장자리 (깃 끝): 등 아래에서 바깥 끝 너머로 쓸려 올라감
        Q0 = (R[0] + sg * 3, R[1] + 54)
        Q1 = (R[0] + sg * tx * 0.5, R[1] + 60 - 26 * lift)
        Q2 = (R[0] + sg * tx * 0.98, R[1] + ty * 0.35 + 36 - 16 * lift)
        Q3 = (T[0] + sg * 11, T[1] + 9 + 3 * lift)
        trail = bez(Q0, Q1, Q2, 40, p3=Q3)
        # 날개깃 (바깥 → 안: 안쪽 깃이 위에 겹침)
        N = 15
        for i in range(N - 1, -1, -1):
            u = (i + 0.5) / N
            B = lead[int(u * 40)]
            P = trail[int(u * 40)]
            ang = math.atan2(P[1] - B[1], P[0] - B[0])
            L = math.hypot(P[0] - B[0], P[1] - B[1])
            m, spine = wing_feather(pad, B, ang, L, 5.0 + 2.2 * u, curve=0.06 * sg)
            core = rim_of(m, 0.8) * 0.8
            core = np.maximum(core, pad.stroke(spine[:13], 0.8, 0.3, a0=0.8, a1=0.25) * m)
            tx_, ty_ = spine[-1]
            core = np.maximum(core, pad.disc_field(tx_, ty_, 3.5, soft=2.0) * m * 0.55)
            st.over(m, 0.6 + 0.26 * u, core * m)
        # 덮깃 두 줄 (짧은 깃 — 앞 가장자리에서)
        for row, (Lk, Wk, sh) in enumerate(((0.42, 4.8, 0.8), (0.22, 3.9, 0.9))):
            for i in range(12, -1, -1):
                u = (i + 0.5) / 13
                B = lead[int(u * 40)]
                P = trail[int(u * 40)]
                ang = math.atan2(P[1] - B[1], P[0] - B[0])
                L = math.hypot(P[0] - B[0], P[1] - B[1]) * Lk + 5
                m, spine = wing_feather(pad, (B[0], B[1] + 1), ang, L, Wk + 1.2 * u, curve=0.05 * sg)
                core = rim_of(m, 0.7) * 0.8 + m * (0.28 if row == 1 else 0.12)
                st.over(m, sh, core * m)
        # 앞 가장자리 빛 줄 · 끝 반짝임 · 뿌리 빛
        st.add_core(pad.stroke(lead, 2.2, 0.9, a0=1.0, a1=0.75))
        st.body = np.maximum(st.body, pad.stroke(lead, 6.5, 3.0, a0=0.8, a1=0.5, soft=2.0))
        st.add_core(sparkle(pad, T[0], T[1], 7 + 3 * lift, 0.9, rot=0.3, diag=0.0))
        glow_extra = np.maximum(glow_extra, pad.radial(R[0], R[1], 26, 1.4) * 0.7)
    st.add_core(pad.disc_field(128, 112, 4.0))
    st.add_core(sparkle(pad, 128, 112, 13, 0.85, rot=0.0, diag=0.0))
    # 떨어지는 빛 알갱이 (날갯짓마다 — 이어 돌림)
    rnd = random.Random(12)
    for i in range(8):
        x0 = 128 + rnd.choice((-1, 1)) * rnd.uniform(36, 96)
        t = (rnd.random() + f / frames) % 1.0
        y = 124 + 52 * t
        st.add_core(sparkle(pad, x0 + 4 * math.sin(t * 6 + i), y, 2.5 + 2 * rnd.random(), math.sin(math.pi * t) * 0.8, diag=0.0))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.5, 0.45), (4.0, 0.34), (9.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.25)))
    glow1 = screen(glow1, pad.down(glow_extra))
    return glow1, core1


@sprite("rfx_jvalwing")
def make_valwing():
    glows, cores = run_frames(valwing_frame, 6)
    return Sprite("rfx_jvalwing", "lookat_y", 256, 192, 6, "paladin", glows, cores, cols=6, zoom=0)


# ------------------------------------------------------------ rfx_jeclipse (검은 해 — 비운 원판 · 밝은 테 · 일렁이는 코로나)

def eclipse_frame(f, frames=8, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    grow = [0.4, 0.66, 0.88, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    pulse = [1.0, 1.0, 1.0, 1.0, 1.0, 1.035, 0.99, 1.025][f]
    vis = [0.35, 0.62, 0.86, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    R = 60.0 * grow * pulse
    Rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    TH = np.arctan2(Y - c, X - c)
    outside = clamp01((Rr - R + 0.6) * SS)
    # ---- 코로나 빛살 (길이가 장마다 일렁임)
    rnd = random.Random(66)
    rays_c = pad.zeros()
    rays_g = pad.zeros()
    for i in range(64):
        a = i * TAU / 64 + rnd.uniform(-0.05, 0.05)
        base = rnd.uniform(0.25, 0.75) * (1.0 if i % 4 else 1.35)
        ph = rnd.uniform(0, TAU)
        w_ = rnd.uniform(1.5, 3.2)
        flick = 0.72 + 0.28 * math.sin(ph + f * rnd.uniform(1.2, 2.6))
        L = (12 + 40 * base) * flick * grow
        curl = rnd.uniform(-0.12, 0.12)
        pts = []
        for k in range(10):
            t = k / 9
            aa = a + curl * t
            rr = R + 1 + L * t
            pts.append((c + math.cos(aa) * rr, c + math.sin(aa) * rr))
        al = rnd.uniform(0.45, 0.9) * vis
        rays_c = np.maximum(rays_c, pad.stroke(pts, w_ * 0.45, 0.15, a0=al, a1=0.0))
        rays_g = np.maximum(rays_g, pad.stroke(pts, w_ * 1.6, 0.5, a0=al * 0.8, a1=0.0, soft=1.0))
    # 부드러운 코로나 · 테
    halo_g = np.exp(-(Rr - R) / (20 * grow + 4)) * 0.75 * vis * outside
    halo_c = np.exp(-(Rr - R) / (4.5 * grow + 1.5)) * 0.65 * vis * outside
    rim_c = np.exp(-0.5 * ((Rr - R - 0.4) / 0.85) ** 2)
    rim_g = np.exp(-0.5 * ((Rr - R - 0.5) / 2.6) ** 2)
    inner_c = np.exp(-0.5 * ((Rr - R + 3.2) / 0.6) ** 2) * 0.28 * (Rr < R)
    # 다이아몬드 반지 번쩍임 (테 위 한 점 — 맥동)
    fa = math.radians(-42)
    fk = [0.5, 0.7, 0.9, 1.0, 0.85, 1.0, 0.75, 0.95][f]
    fx, fy = c + math.cos(fa) * R, c + math.sin(fa) * R
    flare_c = sparkle(pad, fx, fy, (22 + 12 * fk) * grow, 1.0, rot=0.2, thin=0.08, diag=0.45)
    flare_g = pad.radial(fx, fy, 26 * grow, 1.3) * fk
    core = np.maximum.reduce([rays_c * outside, halo_c, rim_c, inner_c, flare_c])
    glow = np.maximum.reduce([rays_g * outside, halo_g, rim_g, flare_g])
    # 원판 안은 비움 (번쩍임 별만 원판에 조금 걸침)
    disc = clamp01((R - 1.0 - Rr) * SS)
    core = core * (1 - disc * (1 - clamp01(flare_c * 2) * (Rr > R - 10)))
    glow = glow * (1 - disc)
    win = smooth(124, 102, Rr)
    core1 = pad.down(core * win)
    g1 = pad.down(glow * win)
    out = screen(g1 * 0.9, gblur(g1, 2.0) * 0.45, gblur(g1, 6.0) * 0.3, gblur(core1, 1.5) * 0.3)
    # 원판 안은 빛 무리도 비움 (가장자리만 살짝 번짐) · 칸 가장자리는 0
    d1 = pad.down(clamp01((R - 2.5 - Rr) * SS))
    out = out * (1 - d1) * pad.down(smooth(127, 108, Rr))
    return out, core1 * (1 - d1)


@sprite("rfx_jeclipse")
def make_eclipse():
    glows, cores = run_frames(eclipse_frame, 8)
    return Sprite("rfx_jeclipse", "rotate_xyz", 256, 256, 8, "reaper", glows, cores, cols=4, zoom=4)


# ------------------------------------------------------------ rfx_jrose (장미 — 0 ~ 4 핌, 5 ~ 7 꽃잎이 흩어짐)

def rose_petal(pad, c, ang, r0, r1, half_ang, wav=0.0):
    """위에서 본 장미 꽃잎 (가운데에서 ang 쪽 r0 → r1, 바깥 가장자리가 둥글게 · 가장자리 물결)"""
    pts = []
    n = 18
    for i in range(n + 1):
        t = i / n
        a = ang - half_ang + 2 * half_ang * t
        rr = r1 * (1 - 0.18 * (2 * t - 1) ** 2) + wav * math.sin(t * math.pi * 3)
        pts.append((c[0] + math.cos(a) * rr, c[1] + math.sin(a) * rr))
    for i in range(n, -1, -1):
        t = i / n
        a = ang - half_ang * 0.55 + 1.1 * half_ang * t
        pts.append((c[0] + math.cos(a) * r0, c[1] + math.sin(a) * r0))
    return pts


def rose_frame(f, frames=8, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = (S / 2, S / 2)
    st = Stack(pad)
    bloom_k = [0.42, 0.6, 0.76, 0.9, 1.0, 1.0, 1.0, 1.0][min(f, 7)]
    q = [0.0, 0.0, 0.0, 0.0, 0.0, 0.35, 0.65, 0.92][min(f, 7)]
    rnd = random.Random(14)
    golden = math.radians(137.5)
    # 꽃잎 층: (층 수, 각 크기, 안 반지름, 바깥 반지름, 피는 순서)
    layers = [(5, 0.95, 0.42, 1.0, 3), (5, 0.85, 0.3, 0.78, 2), (4, 0.8, 0.2, 0.58, 1), (3, 0.9, 0.1, 0.4, 0), (3, 1.2, 0.0, 0.24, 0)]
    Rmax = 46.0
    petals_out = []
    for li, (n, ha, r0k, r1k, order) in enumerate(layers):
        if order > [0, 1, 2, 3, 3, 3, 3, 3][min(f, 7)]:
            continue
        open_k = 1.0 if order < [0, 1, 2, 3, 3, 3, 3, 3][min(f, 7)] else [0.55, 0.65, 0.7, 0.8, 1.0, 1.0, 1.0, 1.0][min(f, 7)]
        for i in range(n):
            ang = i * TAU / n + li * golden + 0.25 * f * (li == 4)
            r1 = Rmax * r1k * bloom_k * (0.75 + 0.25 * open_k)
            r0 = Rmax * r0k * bloom_k * 0.8
            half = ha * math.pi / n * (0.8 + 0.4 * open_k)
            if q > 0 and li <= 1:
                # 흩어짐: 바깥 두 층의 꽃잎이 떨어져 바깥으로 돌며 날아감
                d = (4 + 9 * rnd.uniform(0.7, 1.0)) * ease_out(q) + (li == 1) * 3 * q
                ox, oy = math.cos(ang) * d, math.sin(ang) * d + 5 * q * q
                spin = rnd.uniform(-1.6, 1.6) * q
                pts = rose_petal(pad, c, ang, r0 + 6 * q, r1, half * (1 - 0.25 * q), wav=1.2)
                cx_, cy_ = c[0] + math.cos(ang) * (r0 + r1) / 2, c[1] + math.sin(ang) * (r0 + r1) / 2
                shr = 1 - 0.42 * q
                pts = [(cx_ + (x - cx_) * shr, cy_ + (y - cy_) * shr) for x, y in pts]
                pts = rot_pts([(x + ox, y + oy) for x, y in pts], cx_ + ox, cy_ + oy, spin)
                al = 1 - 0.72 * q
                petals_out.append((pts, al))
                continue
            if q > 0 and li > 1:
                al = (1 - q) ** 1.5
            else:
                al = 1.0
            pts = rose_petal(pad, c, ang, r0, r1, half, wav=1.0 if li <= 1 else 0.4)
            m = pad.poly(pts)
            R_ = np.sqrt((X - c[0]) ** 2 + (Y - c[1]) ** 2)
            shade = (0.5 + 0.38 * clamp01((R_ - r0) / max(1.0, r1 - r0))) * al
            core = rim_of(m, 0.9) * 0.55 * al
            # 말린 가장자리 (바깥 테가 밝음)
            edge = [(c[0] + math.cos(ang - half + 2 * half * t) * (r1 * (1 - 0.18 * (2 * t - 1) ** 2) - 1.0), c[1] + math.sin(ang - half + 2 * half * t) * (r1 * (1 - 0.18 * (2 * t - 1) ** 2) - 1.0)) for t in np.linspace(0.05, 0.95, 14)]
            core = np.maximum(core, pad.stroke(edge, 1.3, a0=0.95 * al, taper=0.6) * m)
            st.over(m, shade, core * m)
    # 가운데 소용돌이 (봉오리 속)
    spiral = []
    for i in range(40):
        t = i / 39
        a = t * TAU * 1.6 + 0.4
        r = (1.5 + 9 * t) * bloom_k
        spiral.append((c[0] + math.cos(a) * r, c[1] + math.sin(a) * r))
    sal = 1.0 if q == 0 else (1 - q) ** 1.5
    st.add_core(pad.stroke(spiral, 1.2, a0=0.9 * sal))
    st.body = np.maximum(st.body, pad.disc_field(c[0], c[1], 10 * bloom_k, soft=3) * 0.8 * sal)
    # 떨어진 꽃잎 (맨 위)
    for pts, al in petals_out:
        m = pad.poly(pts)
        core = rim_of(m, 0.8) * 0.85 * al
        st.over(m, 0.72 * al, core * m)
    if f == 4:
        st.add_core(sparkle(pad, c[0] + 14, c[1] - 16, 12, 0.9, rot=0.3, diag=0.0))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.2, 0.45), (3.0, 0.32), (6.5, 0.18)), body_k=1.0, core_bloom=((1.2, 0.3), (3.0, 0.22)))
    return glow1, core1


@sprite("rfx_jrose")
def make_rose():
    glows, cores = run_frames(rose_frame, 8)
    return Sprite("rfx_jrose", "rotate_xyz", 128, 128, 8, "assassin", glows, cores, cols=4, zoom=4)


# ------------------------------------------------------------ rfx_jfrost (얼음 결정 표시 — 자라남 → 금 → 깨짐)

def rhomb(p0, p1, w, at=0.38):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    mx, my = x0 + dx * at, y0 + dy * at
    return [(x0, y0), (mx + nx * w, my + ny * w), (x1, y1), (mx - nx * w, my - ny * w)]


def frost_frame(f, frames=6, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    grow = [0.32, 0.7, 1.0, 1.0, 1.0, 1.0][f]
    br = [0.0, 0.55, 1.0, 1.0, 1.0, 1.0][f]
    q = 0.0 if f < 5 else 1.0
    crackk = 1.0 if f >= 4 else 0.0
    rnd = random.Random(15)
    body = pad.zeros()
    core = pad.zeros()
    L = 50.0
    pieces = []
    for k in range(6):
        a = -math.pi / 2 + k * TAU / 6
        ux, uy = math.cos(a), math.sin(a)
        tipL = L * grow
        arm = rhomb((c + ux * 6, c + uy * 6), (c + ux * tipL, c + uy * tipL), 4.6 * (0.6 + 0.4 * grow))
        parts = [(arm, (c + ux * tipL * 0.5, c + uy * tipL * 0.5))]
        # 가지 (짝 · 바깥쪽으로 비스듬히)
        for j, (t, bl) in enumerate(((0.38, 0.32), (0.62, 0.26), (0.82, 0.17))):
            if br < (j + 1) / 3.2 or t * L > tipL:
                continue
            bx, by = c + ux * L * t, c + uy * L * t
            for sg in (-1, 1):
                ba = a + sg * math.radians(58)
                bL = L * bl * min(1.0, br * 1.2)
                b = rhomb((bx, by), (bx + math.cos(ba) * bL, by + math.sin(ba) * bL), 2.6)
                parts.append((b, (bx + math.cos(ba) * bL / 2, by + math.sin(ba) * bL / 2)))
        for poly_, mid in parts:
            pieces.append((poly_, mid, a))
    # 가운데 육각 판
    hexo = [(c + math.cos(-math.pi / 2 + i * TAU / 6 + math.pi / 6) * 11 * (0.7 + 0.3 * grow), c + math.sin(-math.pi / 2 + i * TAU / 6 + math.pi / 6) * 11 * (0.7 + 0.3 * grow)) for i in range(6)]
    pieces.append((hexo, (c, c), None))
    for poly_, mid, a in pieces:
        if q > 0:
            # 깨짐: 조각이 바깥으로 밀리며 돌고 흐려짐
            dx, dy = mid[0] - c, mid[1] - c
            d = math.hypot(dx, dy) or 1
            push = 3 + 0.16 * d
            ox, oy = dx / d * push, dy / d * push
            poly_ = [(mid[0] + (x - mid[0]) * 0.82, mid[1] + (y - mid[1]) * 0.82) for x, y in poly_]
            poly_ = rot_pts([(x + ox, y + oy) for x, y in poly_], mid[0] + ox, mid[1] + oy, rnd.uniform(-0.6, 0.6))
            al = 0.55
        else:
            al = 1.0
        m = pad.poly(poly_)
        lit, dark, _ = bevel_light(m, 1.6)
        body = np.maximum(body, m * (0.7 + 0.25 * lit) * al)
        core = np.maximum(core, (rim_of(m, 0.6) * 0.9 + lit * 0.7) * al)
        if len(poly_) == 4:
            core = np.maximum(core, pad.stroke([poly_[0], poly_[2]], 0.6, a0=0.7 * al) * m)
    core = np.maximum(core, pad.stroke(hexo + [hexo[0]], 1.0, a0=0.9 * (1 - 0.45 * q)))
    if f >= 2 and q == 0:
        hexf = [(c + math.cos(i * TAU / 6) * 52, c + math.sin(i * TAU / 6) * 52) for i in range(6)]
        core = np.maximum(core, pad.stroke(hexf + [hexf[0]], 0.8, a0=0.35 if f != 4 else 0.25))
        body = np.maximum(body, pad.stroke(hexf + [hexf[0]], 3.5, a0=0.25, soft=1.5))
    # 금 (지그재그 빛 줄 — 4 장째)
    if crackk and q == 0:
        for i in range(4):
            a = rnd.uniform(0, TAU)
            p0 = (c + math.cos(a) * 4, c + math.sin(a) * 4)
            p1 = (c + math.cos(a) * 46, c + math.sin(a) * 46)
            pts = bolt_pts(rnd, p0, p1, 0.12, 4, 0.55)
            core = np.maximum(core, pad.stroke(pts, 1.3, 0.5, a0=1.0, a1=0.6))
            body = np.maximum(body, pad.stroke(pts, 4.0, a0=0.6, soft=1.5))
    # 번쩍 (다 자란 장) · 끝의 반짝임
    if f == 2:
        core = np.maximum(core, sparkle(pad, c, c, 40, 1.0, thin=0.08, diag=0.4))
        body = np.maximum(body, pad.disc_field(c, c, 18, soft=8) * 0.8)
    if f in (2, 3):
        for k in (0, 2, 4) if f == 2 else (1, 3, 5):
            a = -math.pi / 2 + k * TAU / 6
            core = np.maximum(core, sparkle(pad, c + math.cos(a) * L, c + math.sin(a) * L, 8, 0.95, rot=0.3, diag=0.0))
    if f == 0:
        core = np.maximum(core, sparkle(pad, c, c, 22, 0.9, thin=0.1))
    if q > 0:
        for i in range(10):
            a = rnd.uniform(0, TAU)
            d = rnd.uniform(26, 52)
            core = np.maximum(core, pad.disc_field(c + math.cos(a) * d, c + math.sin(a) * d, 1.3) * 0.7)
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.2, 0.45), (3.0, 0.35), (6.5, 0.2)), body_k=1.0, core_bloom=((1.2, 0.3), (3.0, 0.25)))
    return glow1, core1


@sprite("rfx_jfrost")
def make_frost():
    glows, cores = run_frames(frost_frame, 6)
    return Sprite("rfx_jfrost", "rotate_xyz", 128, 128, 6, "mage", glows, cores, cols=6, zoom=2)


# ------------------------------------------------------------ rfx_jrunes (각진 글자 10 개의 띠 — 차례로 깜빡임)

def runes_frame(f, frames=4, W=512, H=64):
    pad = Pad(W, H)
    cy = H / 2
    body = pad.zeros()
    core = pad.zeros()
    # 띠 테 (양 끝이 뾰족한 긴 육각)
    band = [(16, cy), (35, 12), (477, 12), (496, cy), (477, 52), (35, 52), (16, cy)]
    core = np.maximum(core, pad.stroke(band, 1.0, a0=0.6))
    body = np.maximum(body, pad.stroke(band, 4.0, a0=0.35, soft=1.5))
    inner = [(27, cy), (40, 16), (472, 16), (485, cy), (472, 48), (40, 48), (27, cy)]
    core = np.maximum(core, pad.stroke(inner, 0.6, a0=0.3))
    order = [0, 1, 2, 3, 5, 6, 7, 8, 9, 11]
    for k in range(10):
        x = 55 + k * 44.7
        ph = (k - f) % 4
        b = [1.0, 0.62, 0.42, 0.42][ph]
        m = glyph_mask(pad, order[k], x, cy, 20, 27, 2.1)
        core = np.maximum(core, m * b)
        body = np.maximum(body, m * (0.55 + 0.45 * (ph == 0)))
        if ph == 0:
            body = np.maximum(body, pad.radial(x, cy, 26, 1.5) * 0.55)
            core = np.maximum(core, sparkle(pad, x + 9, cy - 13, 5.5, 0.85, rot=0.3 * k, diag=0.0))
        if k < 9:
            dx = x + 22.35
            core = np.maximum(core, pad.poly([(dx, cy - 2.6), (dx + 2.2, cy), (dx, cy + 2.6), (dx - 2.2, cy)], 0.65))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.2, 0.45), (3.0, 0.35), (7.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.35), (3.5, 0.3)))
    return glow1, core1


@sprite("rfx_jrunes")
def make_runes():
    glows, cores = run_frames(runes_frame, 4)
    return Sprite("rfx_jrunes", "lookat_direction", 512, 64, 4, "spellblade", glows, cores, cols=2)


# ------------------------------------------------------------ rfx_jfeather (떠다니며 뒤집히는 깃털 하나 — 이어 돌림)

def feather1_frame(f, frames=8, S=64):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    ph = TAU * f / frames
    ang = math.radians(-50 + 24 * math.sin(ph))
    flip = math.cos(ph)
    wk = max(0.14, abs(flip))
    mir = 1 if flip >= 0 else -1
    L = 50.0
    xf = Xf(c, c, ang, 1.0, wk * mir)
    # 깃대 (로컬: x = -L/2 → L/2, 끝이 +x · 살짝 휨)
    spine = [(-L / 2 + L * t, -2.0 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 20)]
    # 깃 (앞쪽 좁고 뒤쪽 넓음 · 중간에 갈라진 틈)
    top, bot = [], []
    for i, (x, y) in enumerate(spine):
        t = i / 19
        env = math.sin(math.pi * min(1.0, 0.12 + t * 0.95)) ** 0.55 * (0.5 + 0.5 * t ** 0.5)
        top.append((x, y - 4.2 * env))
        bot.append((x, y + 7.0 * env))
    vane = top[3:] + bot[3:][::-1]
    split_t = 0.55
    vm = pad.poly(xf(vane))
    gap = pad.stroke(xf([(-L / 2 + L * split_t, 1.0), (-L / 2 + L * split_t - 7, 7.5)]), 1.1)
    vm = vm * (1 - gap)
    core = rim_of(vm, 0.6) * 0.8
    # 깃가지 (끝 쪽으로 기운 가는 줄)
    for i in range(4, 19, 2):
        x, y = spine[i]
        t = i / 19
        env = math.sin(math.pi * min(1.0, 0.12 + t * 0.95)) ** 0.55 * (0.5 + 0.5 * t ** 0.5)
        core = np.maximum(core, pad.stroke(xf([(x, y), (x + 4, y + 6.5 * env)]), 0.5, a0=0.5) * vm)
        core = np.maximum(core, pad.stroke(xf([(x, y), (x + 3, y - 4.0 * env)]), 0.5, a0=0.4) * vm)
    rach = pad.stroke(xf(spine), 1.1, 0.4, a0=1.0, a1=0.8)
    core = np.maximum(core, rach)
    # 밑동 솜털
    fluff = pad.zeros()
    for k in range(5):
        a = math.radians(140 + k * 20)
        bx, by = spine[2]
        fluff = np.maximum(fluff, pad.stroke(xf([(bx, by), (bx + math.cos(a) * 6, by + math.sin(a) * 5)]), 0.9, 0.3, a0=0.5, a1=0.1))
    body = np.maximum.reduce([vm * (0.62 + 0.3 * wk), rach * 0.9, fluff * 0.8])
    core = np.maximum(core, fluff * 0.6)
    core = np.maximum(core, vm * 0.25 * clamp01(flip))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((0.8, 0.45), (2.0, 0.35), (4.5, 0.2)), body_k=1.0, core_bloom=((1.0, 0.3), (2.5, 0.2)))
    return glow1, core1


@sprite("rfx_jfeather")
def make_feather():
    glows, cores = run_frames(feather1_frame, 8)
    return Sprite("rfx_jfeather", "rotate_xyz", 64, 64, 8, "archer", glows, cores, cols=8, zoom=0)


# ------------------------------------------------------------ rfx_jwall (땅에서 솟는 거친 돌 성벽 — 낮음 → 다 솟음)

def rough_block(rnd, xa, ya, xe, ye, g=1.5):
    """거친 돌 하나: 모서리를 아무렇게나 깎고 가장자리를 울퉁불퉁하게 (점 여럿)"""
    xa, ya, xe, ye = xa + g, ya + g, xe - g, ye - g
    cs = [rnd.uniform(1.5, 5.0) for _ in range(4)]
    if rnd.random() < 0.3:
        cs[rnd.randint(0, 3)] = rnd.uniform(6, 9)
    j = lambda a=1.4: rnd.uniform(-a, a)
    pts = []
    pts += [(xa + cs[0], ya + j(0.6)), ((xa + xe) / 2 + j(3), ya + j(1.2)), (xe - cs[1], ya + j(0.6))]
    pts += [(xe + j(0.6), ya + cs[1]), (xe + j(1.2), (ya + ye) / 2 + j(2)), (xe + j(0.6), ye - cs[2])]
    pts += [(xe - cs[2], ye + j(0.6)), ((xa + xe) / 2 + j(3), ye + j(1.0)), (xa + cs[3], ye + j(0.6))]
    pts += [(xa + j(0.6), ye - cs[3]), (xa + j(1.2), (ya + ye) / 2 + j(2)), (xa + j(0.6), ya + cs[0])]
    return pts


def wall_stones():
    rnd = random.Random(16)
    stones = []
    x0, x1 = 20.0, 236.0
    y = 238.0
    top_main = 78.0
    row = 0
    while y > top_main + 4:
        h = rnd.uniform(21, 31)
        yt = max(top_main, y - h)
        if yt - top_main < 16:
            yt = top_main  # 남는 얇은 줄은 이 줄에 붙임
        x = x0 - (rnd.uniform(0, 26) if row % 2 else 0)
        while x < x1 - 2:
            w = rnd.uniform(26, 54)
            xe = min(x1, x + w)
            if x1 - xe < 14:
                xe = x1
            xa = max(x0, x)
            if xe - xa > 8:
                ytt = yt + (rnd.uniform(-2.5, 2.5) if yt > top_main + 2 else 0)
                stones.append((rough_block(rnd, xa, ytt, xe, y), rnd.uniform(0.6, 0.84), rnd.random() < 0.32, rnd.randint(0, 10 ** 6)))
            x = xe
        y = yt
        row += 1
    # 성가퀴 (위의 이빨 넷 — 돌 두 장씩)
    n = 4
    mw = (x1 - x0) / (n * 2 - 1)
    for i in range(n):
        xa = x0 + i * 2 * mw
        xe = xa + mw
        mid = rnd.uniform(58, 64)
        stones.append((rough_block(rnd, xa, 42 + rnd.uniform(0, 3), xe, mid), rnd.uniform(0.66, 0.84), rnd.random() < 0.4, rnd.randint(0, 10 ** 6)))
        stones.append((rough_block(rnd, xa, mid, xe, top_main), rnd.uniform(0.62, 0.8), rnd.random() < 0.3, rnd.randint(0, 10 ** 6)))
    return stones


def wall_frame(f, frames=4, W=256, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    ground = 240.0
    hk = [0.34, 0.64, 0.88, 1.0][f]
    full = ground - 44
    dy = (1 - hk) * full
    shake = [2.0, -1.5, 1.0, 0.0][f]
    body = pad.zeros()
    core = pad.zeros()
    above = clamp01((ground - Y) * SS)
    for poly, shade, cracked, seed in wall_stones():
        pts = [(x + shake, y + dy) for x, y in poly]
        if min(y for _, y in pts) > ground:
            continue
        m = pad.poly(pts) * above
        lit, dark, _ = bevel_light(m, 3.5)
        body = np.maximum(body, m * (0.2 + 0.85 * shade + 0.1 * lit - 0.2 * dark))
        core = np.maximum(core, (rim_of(m, 0.7) * 0.4 + lit * 0.7) * m)
        if cracked:
            r2 = random.Random(seed)
            xs = [x for x, _ in pts]
            ys = [y for _, y in pts]
            p0 = (r2.uniform(min(xs) + 3, max(xs) - 3), min(ys) + 2)
            p1 = (r2.uniform(min(xs) + 3, max(xs) - 3), max(ys) - 2)
            cr = bolt_pts(r2, p0, p1, 0.18, 4, 0.55)
            core = np.maximum(core, pad.stroke(cr, 1.1, 0.4, a0=0.95, a1=0.6) * m)
            body = np.maximum(body, pad.stroke(cr, 3.0, a0=0.45, soft=1.0) * m)
    # 솟는 동안: 땅의 금 · 먼지 · 튀는 돌
    rnd = random.Random(70 + f)
    if f < 3:
        k = [1.0, 0.8, 0.5][f]
        dust = pad.zeros()
        for i in range(16):
            x = rnd.uniform(34, 222)
            r = rnd.uniform(9, 17) * (0.7 + 0.5 * k)
            dust = np.maximum(dust, pad.disc_field(x, ground - r * 0.35 - rnd.uniform(0, 8), r, soft=r * 0.8))
        body = np.maximum(body, dust * 0.5 * k)
        core = np.maximum(core, dust * 0.1 * k)
        for i in range(9):
            x = rnd.uniform(20, 236)
            y = ground - rnd.uniform(10, 70) * (1.2 - 0.3 * f)
            sz = rnd.uniform(2.0, 4.0)
            quad = rot_pts([(x - sz, y - sz * 0.7), (x + sz * 0.8, y - sz), (x + sz, y + sz * 0.6), (x - sz * 0.6, y + sz)], x, y, rnd.uniform(0, TAU))
            mm = pad.poly(quad)
            body = np.maximum(body, mm * 0.8 * k)
            core = np.maximum(core, (rim_of(mm, 0.6) * 0.8 + mm * 0.3) * k)
        for sg in (-1, 1):
            for j in range(3):
                x0 = 128 + sg * (30 + 26 * j + rnd.uniform(-6, 6))
                pts = bolt_pts(rnd, (x0, ground), (x0 + sg * rnd.uniform(10, 18), ground + rnd.uniform(3, 9)), 0.25, 3, 0.6)
                core = np.maximum(core, pad.stroke(pts, 1.2, 0.3, a0=0.8 * k, a1=0.1))
    # 땅 줄 (밑동이 땅에 묻힌 자리)
    core = np.maximum(core, pad.stroke([(16, ground), (240, ground)], 1.0, a0=0.45, taper=0.4))
    body = np.maximum(body, pad.stroke([(14, ground), (242, ground)], 4.0, a0=0.35, taper=0.5, soft=2.0))
    below = smooth(249, 241, Y)  # 땅 아래는 흐리게 사라짐 (칸 아래 끝에 닿지 않게)
    core, body = core * below, body * below
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.3, 0.4), (3.5, 0.28), (8.0, 0.16)), body_k=1.0, core_bloom=((1.2, 0.3), (3.5, 0.2)))
    return glow1, core1


@sprite("rfx_jwall")
def make_wall():
    glows, cores = run_frames(wall_frame, 4)
    return Sprite("rfx_jwall", "lookat_y", 256, 256, 4, "guardian", glows, cores, cols=4, zoom=3)


# ------------------------------------------------------------ 멈춘 카드 그림 (꼬리 · 잔상 없음 — 입체 연출이 카드를 직접 움직이고 돌림)

def axe_card_frame(S=192):
    """rfx_jaxe_card: 도끼를 곧게 세움 — 자루가 가운데 세로줄 (아래), 머리가 위 · 날이 오른쪽 · 가시가 왼쪽"""
    pad = Pad(S, S)
    lo, hi = -68.0, 71.5  # 로컬 x (자루 끝 → 날 앞 끝)
    sc = (S - 26) / (hi - lo)
    mid = (lo + hi) / 2
    # 로컬 +x → 위, 로컬 -y (날) → 오른쪽 (y 뒤집음)
    xf = Xf(S / 2, S / 2 + mid * sc, -math.pi / 2, sc, -sc)
    body, core, head, sil = axe_draw(pad, xf, glint=0.0, bend=0.0)
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.4, 0.5), (3.8, 0.36), (8.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.2)))
    return glow1, core1


def dagger_card_frame(S=128):
    """rfx_jdagger_card: 휜 단검을 곧게 세움 — 날 끝이 위, 자루 끝 고리가 아래, 자루 축이 가운데 세로줄"""
    pad = Pad(S, S)
    lo, hi = -33.2, 53.0
    sc = (S - 22) / (hi - lo)
    mid = (lo + hi) / 2
    xf = Xf(S / 2, S / 2 + mid * sc, -math.pi / 2, sc, sc)
    body, core, sil = dagger_draw(pad, xf, glint=0.0)
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.0, 0.5), (2.6, 0.36), (5.5, 0.2)), body_k=1.0, core_bloom=((1.2, 0.3), (3.0, 0.2)))
    return glow1, core1


@sprite("rfx_jaxe_card")
def make_axe_card():
    g, c = axe_card_frame()
    return Sprite("rfx_jaxe_card", "lookat_y", 192, 192, 1, "warrior", [g], [c], cols=2)


@sprite("rfx_jdagger_card")
def make_dagger_card():
    g, c = dagger_card_frame()
    return Sprite("rfx_jdagger_card", "lookat_y", 128, 128, 1, "assassin", [g], [c], cols=2)


@sprite("rfx_jbuckler_card")
def make_buckler_card():
    g, c = buckler2_frame(0, 8, 128, card=True)
    return Sprite("rfx_jbuckler_card", "lookat_y", 128, 128, 1, "guardian", [g], [c], cols=2)


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
