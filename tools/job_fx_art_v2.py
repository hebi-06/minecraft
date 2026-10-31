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
            w = w * np.sin(np.pi * np.clip(s, 0, 1)) ** taper
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
        a[r * ch:(r + 1) * ch, k * cw:(k + 1) * cw] = edge_fade(c)
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
    gap = 6
    rows_src = []
    if sp.layout is None:
        rows_src.append((sp.job, sp.glows, sp.cores))
    else:
        for job, g, c in zip(JOBS, sp.glows, sp.cores):
            rows_src.append((job, g, c))
    cw, ch = sp.cw, sp.ch
    per_row = sp.frames if sp.layout is not None else max(1, min(sp.frames, (2048 // (cw + gap))))
    zf = sp.zoom if sp.zoom is not None else 0
    blocks = []
    for job, gl, co in rows_src:
        ca, cb = COLORS[job]
        n = len(gl)
        rr = (n + per_row - 1) // per_row
        W = per_row * (cw + gap) + gap
        H = rr * (ch + gap + 12) + gap
        zoom = tint_comp(gl[zf], co[zf], ca, cb).resize((cw * 2, ch * 2), Image.NEAREST if cw <= 64 else Image.BICUBIC)
        block = Image.new("RGB", (W + zoom.size[0] + gap * 2, max(H, zoom.size[1] + gap * 2 + 12)), tuple(int(v * 255) for v in BG))
        for i in range(n):
            r, k = divmod(i, per_row)
            x, y = gap + k * (cw + gap), gap + r * (ch + gap + 12) + 12
            block.paste(tint_comp(gl[i], co[i], ca, cb), (x, y))
            label(block, f"{i}", (x + 2, y - 11))
        block.paste(zoom, (W + gap, gap + 12))
        label(block, f"{job}  zoom 2x of frame {zf}", (W + gap, 1))
        blocks.append(block)
    W = max(b.size[0] for b in blocks)
    H = sum(b.size[1] for b in blocks) + 16
    out = Image.new("RGB", (W, H), tuple(int(v * 255) for v in BG))
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


# ------------------------------------------------------------ rfx_jsword2 (환영 장검 — 오른쪽이 칼끝)

def sword_geom(W=256, H=64):
    cy = H / 2
    g = {}
    tip = W - 7.0
    b0 = 61.0
    bl = tip - b0

    def hw(x):  # 날 반폭
        t = (x - b0) / bl
        base = 6.6 - 1.5 * t
        k = smooth(0.80, 1.0, t)
        return base * (1 - k) + 0.0 * k if t < 1 else 0.0

    top, bot = [], []
    n = 90
    for i in range(n + 1):
        t = i / n
        x = b0 + bl * t
        if t > 0.8:
            # 끝: 바깥으로 살짝 볼록하게 뾰족
            u = (t - 0.8) / 0.2
            w = (6.6 - 1.5 * 0.8) * (1 - u ** 1.35) ** 0.85
        else:
            w = 6.6 - 1.5 * t
        top.append((x, cy - w))
        bot.append((x, cy + w))
    g["blade"] = top + bot[::-1]
    g["blade_top"], g["blade_bot"] = top, bot
    g["b0"], g["tip"], g["cy"] = b0, tip, cy
    # 날 뿌리 (리카소 — 조금 좁은 목)
    g["ricasso"] = [(b0 - 3, cy - 5.0), (b0 + 6, cy - 6.4), (b0 + 6, cy + 6.4), (b0 - 3, cy + 5.0)]
    # 코등이: 가운데가 두툼하고 양끝이 칼끝 쪽으로 휘며 뾰족
    gx = 55.0
    guard = []
    for side in (-1, 1):
        pts = []
        for i in range(15):
            t = i / 14
            y = cy + side * (4 + 17 * t)
            x = gx + 5.5 * t ** 2.2
            w = 3.6 * (1 - 0.65 * t)
            pts.append((x, y, w))
        guard.append(pts)
    up, dn = guard[0], guard[1]
    left = [(x - w, y) for x, y, w in up[::-1]] + [(gx - 3.6, cy)] + [(x - w, y) for x, y, w in dn]
    right = [(x + w, y) for x, y, w in dn[::-1]] + [(gx + 3.6, cy)] + [(x + w, y) for x, y, w in up]
    tip_dn = (dn[-1][0] + 3.2, dn[-1][1] + 2.2)
    tip_up = (up[-1][0] + 3.2, up[-1][1] - 2.2)
    g["guard"] = [tip_up] + left + [tip_dn] + right
    g["guard_spine"] = [(x, y) for x, y, _ in up[::-1]] + [(gx, cy)] + [(x, y) for x, y, _ in dn]
    g["guard_tips"] = [tip_up, tip_dn]
    g["gx"] = gx
    # 손잡이 (가운데가 조금 부푼 원통)
    g0, g1 = 21.0, 51.5
    gt, gb = [], []
    for i in range(21):
        t = i / 20
        x = g0 + (g1 - g0) * t
        w = 3.0 + 0.45 * math.sin(math.pi * t)
        gt.append((x, cy - w))
        gb.append((x, cy + w))
    g["grip"] = gt + gb[::-1]
    g["grip_x"] = (g0, g1)
    # 자루 끝: 목 고리 + 다이아몬드 보석 자루 끝
    g["collar"] = [(g0 - 3.5, cy - 4.2), (g0 + 1.0, cy - 4.2), (g0 + 1.0, cy + 4.2), (g0 - 3.5, cy + 4.2)]
    px = 11.5
    g["pommel"] = [(px - 7.0, cy), (px, cy - 6.6), (px + 6.5, cy), (px, cy + 6.6)]
    g["px"] = px
    return g


def sword_frame(f, frames=4, W=256, H=64):
    pad = Pad(W, H)
    g = sword_geom(W, H)
    X, Y = pad.grid()
    cy = g["cy"]
    b0, tip = g["b0"], g["tip"]
    blade = pad.poly(g["blade"])
    ric = pad.poly(g["ricasso"])
    guard = pad.poly(g["guard"])
    grip = pad.poly(g["grip"])
    collar = pad.poly(g["collar"])
    pommel = pad.poly(g["pommel"])
    sil = np.maximum.reduce([blade, ric, guard, grip, collar, pommel])
    # ---- 빛 몸: 날은 밝게 · 손잡이는 조금 어둡게, 날 가운데 홈 둘레가 살짝 꺼짐 (입체감)
    nd = np.abs(Y - cy)
    groove = 1 - 0.28 * np.exp(-0.5 * ((nd - 1.9) / 0.9) ** 2) * (X > b0 + 4) * (X < b0 + (tip - b0) * 0.66)
    body = np.maximum.reduce([blade * 0.92 * groove, ric * 0.9, guard * 0.9, grip * 0.72, collar * 0.86, pommel * 0.9])
    # ---- 심지
    core = np.zeros_like(sil)
    # 날: 가장자리 빛 (안쪽으로 그러데이션) + 또렷한 날 줄
    bl_mask = np.maximum(blade, ric)
    d_in = inner_dist(bl_mask)
    edge = clamp01(1 - d_in / 2.6) ** 1.6 * (bl_mask > 0.5)
    core = np.maximum(core, edge * 0.78)
    for pts in (g["blade_top"], g["blade_bot"]):
        core = np.maximum(core, pad.stroke(pts, 0.55, a0=0.95, a1=1.0) * bl_mask)
    # 가운데 홈 (빛 줄 — 칼끝 쪽으로 가늘어지며 사라짐)
    fl_end = b0 + (tip - b0) * 0.68
    core = np.maximum(core, pad.stroke([(b0 + 3, cy), (fl_end, cy)], 1.25, 0.5, a0=0.95, a1=0.55))
    # 날 끝 쪽 능선 (가운데 줄이 칼끝까지 가늘게)
    core = np.maximum(core, pad.stroke([(fl_end - 6, cy), (tip - 1.5, cy)], 0.5, 0.25, a0=0.35, a1=0.75))
    # 코등이: 테 · 가운데 줄 · 보석
    core = np.maximum(core, rim_of(guard, 1.1) * 0.85)
    core = np.maximum(core, pad.stroke(g["guard_spine"], 0.7, a0=0.55) * guard)
    gx = g["gx"]
    gem = pad.poly([(gx - 3.4, cy), (gx, cy - 4.4), (gx + 3.4, cy), (gx, cy + 4.4)])
    core = np.maximum(core, gem * 0.55 + pad.poly([(gx - 1.6, cy - 0.4), (gx, cy - 3.2), (gx + 0.9, cy - 0.6)]) * 0.45)
    core = np.maximum(core, rim_of(gem, 0.8) * 1.0)
    for tx, ty in g["guard_tips"]:
        core = np.maximum(core, pad.disc_field(tx, ty, 1.1))
    # 손잡이: 감은 끈 (비스듬한 줄) · 테
    g0, g1 = g["grip_x"]
    wraps = np.zeros_like(sil)
    k = 0
    x = g0 + 1.5
    while x < g1 - 1:
        wraps = np.maximum(wraps, pad.stroke([(x, cy - 4.0), (x + 3.2, cy + 4.0)], 0.7, a0=0.75))
        x += 3.6
        k += 1
    core = np.maximum(core, wraps * grip)
    core = np.maximum(core, rim_of(grip, 0.8) * 0.6)
    core = np.maximum(core, rim_of(collar, 0.9) * 0.85)
    # 자루 끝 보석: 면 (위쪽 면이 밝음) · 테
    px = g["px"]
    pm = pommel
    face = pad.poly([(px - 7.0, cy), (px, cy - 6.6), (px + 1.0, cy - 0.4)]) * 0.62
    face = np.maximum(face, pad.poly([(px, cy - 6.6), (px + 6.5, cy), (px + 1.0, cy - 0.4)]) * 0.4)
    core = np.maximum(core, face * pm)
    core = np.maximum(core, rim_of(pm, 0.9) * 0.95)
    # ---- 날을 따라 흐르는 빛 (비스듬한 띠 · 날 길이마다 되풀이 → 이어 돌림)
    L = tip - b0 + 40
    sx = b0 - 20 + L * (f + 0.5) / frames
    swp = np.zeros_like(sil)
    for kk in (-1, 0, 1):
        c0 = sx + kk * L
        u = (X - c0) + (Y - cy) * 0.9
        swp = np.maximum(swp, np.exp(-0.5 * (u / 7.5) ** 2))
    core = np.maximum(core, swp * 0.88 * np.maximum(bl_mask, guard * 0.7))
    # 반짝임: 칼끝 (장마다 크기 · 돌기가 다름) · 코등이 보석
    tw = [1.0, 0.55, 0.8, 0.4][f]
    core = np.maximum(core, sparkle(pad, tip - 6, cy - 1.5, 6.5 + 4.0 * tw, 0.95, rot=0.2 * f))
    core = np.maximum(core, sparkle(pad, gx, cy - 1.0, 4.0 + 3.0 * (1 - tw), 0.85, diag=0.0))
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.2, 0.5), (3.0, 0.38), (6.5, 0.22)), body_k=1.0, core_bloom=((1.5, 0.35), (4.0, 0.25)))
    return glow1, core1


@sprite("rfx_jsword2")
def make_sword2():
    glows, cores = run_frames(sword_frame, 4)
    return Sprite("rfx_jsword2", "lookat_direction", 256, 64, 4, "swordsman", glows, cores, cols=2)


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
