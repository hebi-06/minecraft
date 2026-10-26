#!/usr/bin/env python3
"""0.47.0 입체 효과 물체 (사용자 "다른 룬들도 다 고퀄리티의 모델링을 가지면 좋겠음 … 효과 물체를 입체 모델로"): 직업 스킬의 효과 물체
(환영 검 · 창 · 비수 · 빛의 화살 · 방패 …) 를 사각형 면들로 짠 입체 모델 + 그림으로 만듦.

  python3 tools/fx_models.py            # 씀 (assets/mmorpg/fxmodel/<이름>.json · textures/fx/model/<이름>.png · halo.png)
  python3 tools/fx_models.py --check    # 쓸 것이 없는지
  python3 tools/fx_models.py --preview [이름 ...]   # build/fx_preview/<이름>.png (여러 방향에서 본 그림 — 그림자 · 재질 색)

모델 (자바 client FxModels 가 읽음):
  {"tex": 그림, "glow": [[x, y, z, 반지름] …] (빛 무리 — 카메라를 보는 둥근 빛), "parts": [{"name", "pivot", "q": [사각형 …]}], "anims": {…}}
  사각형 = 꼭짓점 넷 × (x, y, z, u, v, nx, ny, nz) + 재질 — 바깥에서 볼 때 반시계 (앞면), 칸 단위 (1 = 한 칸).
  재질 (자바 FxModels.MAT): 0 빛깔 (부른 색) · 1 속빛 (둘째 색) · 2 흰빛 · 3 짙은 빛깔 · 4 쇠 · 5 금 · 6 나무 · 7 뼈 · 8 그림자 · 9 짙은 속빛.
  그림은 밝기 (회색) — 재질 색을 곱함. 축: +Y = 길이 (칼끝 · 창끝 · 화살촉), +Z = 넓은 면 (칼의 옆면 · 방패 앞면), +X = 날.
  anims: {"이름": {"len": 초, "tracks": {"부분": {"rot": [[t, x, y, z] …] (도), "pos": [[t, x, y, z] …], "scale": [[t, s] …]}}}} — t 는 0 ~ 1
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_MODEL = os.path.join(ROOT, "src/main/resources/assets/mmorpg/fxmodel")
OUT_TEX = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/fx/model")

TINT, GLOW, WHITE, DARK, METAL, GOLD, WOOD, BONE, SHADOW, DEEP = range(10)
# 미리 보기 색 (자바 FxModels.MAT 과 같은 뜻 — 빛깔 · 속빛은 하늘색 · 흰빛으로)
PREVIEW_TINT = (0.42, 0.78, 1.0)
PREVIEW_GLOW = (0.84, 0.95, 1.0)
MAT_RGB = {
    TINT: PREVIEW_TINT, GLOW: PREVIEW_GLOW, WHITE: (1, 1, 1), DARK: tuple(c * 0.45 for c in PREVIEW_TINT), METAL: (0.76, 0.79, 0.84),
    GOLD: (1.0, 0.8, 0.36), WOOD: (0.55, 0.38, 0.22), BONE: (0.92, 0.89, 0.8), SHADOW: (0.13, 0.1, 0.17), DEEP: tuple(c * 0.5 for c in PREVIEW_GLOW),
}


def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def rot_x(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# ------------------------------------------------------------ 그림 (밝기 — 칸마다 무늬)


class Atlas:
    """모델 하나의 그림: size × size, 칸 (swatch) 을 차례로 잘라 씀"""

    def __init__(self, size=128, cell=32):
        self.size = size
        self.cell = cell
        self.img = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        self.px = self.img.load()
        self.next = 0
        self.named = {}

    def swatch(self, name, painter, w=1, h=1, **kw):
        """칸 w × h 개 크기의 무늬 (같은 이름은 한 번만): 정규 UV (u0, v0, u1, v1) — 가장자리 반 픽셀 안쪽"""
        if name in self.named:
            return self.named[name]
        per = self.size // self.cell
        # 빈 자리 찾기 (줄마다 왼쪽부터)
        while True:
            col, row = self.next % per, self.next // per
            if col + w <= per:
                break
            self.next += per - col
        if row + h > per:
            raise RuntimeError("atlas full")
        x0, y0 = col * self.cell, row * self.cell
        pw, ph = w * self.cell, h * self.cell
        rnd = random.Random(hash(name) & 0xFFFF)
        for y in range(ph):
            for x in range(pw):
                lum, a = painter(x / (pw - 1), y / (ph - 1), rnd, **kw)
                g = int(max(0, min(1, lum)) * 255 + 0.5)
                self.px[x0 + x, y0 + y] = (g, g, g, int(max(0, min(1, a)) * 255 + 0.5))
        self.next += w
        if h > 1:
            # 아래 줄의 같은 자리도 씀 (단순히: 다음 줄 첫 칸부터 이어감)
            self.next = (row + h) * per if w == per else self.next
        s = float(self.size)
        uv = ((x0 + 0.5) / s, (y0 + 0.5) / s, (x0 + pw - 0.5) / s, (y0 + ph - 0.5) / s)
        self.named[name] = uv
        return uv


def noise(rnd, k=0.06):
    return (rnd.random() - 0.5) * 2 * k


def p_flat(u, v, rnd, lum=0.9, k=0.04):
    return lum + noise(rnd, k), 1


def p_blade(u, v, rnd, runes=True):
    """칼 옆면: u = 날 쪽 (0) → 반대 날 (1), v = 칼끝 (0) → 밑동 (1). 가운데 홈 (밝은 줄) · 끝으로 갈수록 밝음 · 밑동 가까이 룬 글자"""
    c = abs(u - 0.5) * 2  # 0 = 가운데
    lum = 0.74 + 0.16 * (1 - v)
    if c < 0.16:
        lum = 0.98 - c * 0.6
    elif c < 0.26:
        lum -= 0.16  # 홈 가장자리 그늘
    if runes and 0.62 < v < 0.9 and 0.3 < c < 0.62:
        # 룬 글자: 짧은 획 (세로 · 가로) 이 엇갈림
        cell_v = int((v - 0.62) / 0.056)
        h = (cell_v * 7 + (1 if u > 0.5 else 0) * 3) % 5
        fv = ((v - 0.62) / 0.056) % 1
        fu = ((c - 0.3) / 0.32)
        if (h in (0, 2) and 0.4 < fu < 0.6) or (h in (1, 3) and 0.35 < fv < 0.55) or (h == 4 and abs(fu - fv) < 0.12):
            lum = 1.0
    return lum + noise(rnd, 0.025), 1


def p_edge(u, v, rnd):
    """날: 밝음 (가운데로 갈수록 조금 어둡게 — 날 선)"""
    return 0.94 + 0.06 * (1 - abs(u - 0.5) * 2) + noise(rnd, 0.02), 1


def p_grip(u, v, rnd):
    """손잡이: 비스듬히 감은 끈"""
    s = (u * 3 + v * 6) % 1
    lum = 0.55 + 0.35 * (1 - abs(s - 0.5) * 2) ** 0.6
    return lum + noise(rnd, 0.04), 1


def p_gem(u, v, rnd):
    """보석: 가운데 밝은 둥근 빛"""
    d = math.hypot(u - 0.38, v - 0.36)
    return 1.0 - 0.45 * min(1, d * 1.6) + noise(rnd, 0.02), 1


def p_metal(u, v, rnd, lum=0.82):
    """쇠: 길이 방향으로 긁힌 결"""
    streak = math.sin(u * 47.0 + math.sin(u * 13.0) * 2) * 0.05
    return lum + streak + noise(rnd, 0.03), 1


def p_band(u, v, rnd):
    """띠: 위 · 아래 밝은 선 + 가운데 마름모 무늬"""
    if v < 0.15 or v > 0.85:
        return 1.0, 1
    d = abs((u * 8) % 1 - 0.5) * 2
    return (0.95 if abs(d - abs(v - 0.5) * 2) < 0.22 else 0.68) + noise(rnd, 0.03), 1


def p_feather(u, v, rnd):
    """깃: 가운데 깃대 · 비스듬한 결 · 끝으로 옅어짐"""
    c = abs(u - 0.5) * 2
    if c < 0.08:
        return 1.0, 1
    barb = 0.75 + 0.2 * math.sin((v * 22 + c * 6))
    return barb + noise(rnd, 0.03), 1


def p_emblem(u, v, rnd):
    """방패 앞면 (원판 — u, v 는 둥근 무늬의 자리): 가운데 십자 빛 · 둘레 고리 · 바깥으로 어두워짐"""
    x, y = u - 0.5, v - 0.5
    r = math.hypot(x, y) * 2
    lum = 0.8 - 0.18 * r
    if abs(x) < 0.07 and r < 0.8 or abs(y) < 0.07 and r < 0.8:
        lum = 1.0
    if 0.82 < r < 0.9:
        lum = 0.98
    if r < 0.18:
        lum = 1.0
    return lum + noise(rnd, 0.02), 1


def p_rim(u, v, rnd):
    """테두리: 리벳 점"""
    lum = 0.86
    if abs((u * 16) % 1 - 0.5) < 0.18 and abs(v - 0.5) < 0.25:
        lum = 1.0
    return lum + noise(rnd, 0.02), 1


# ------------------------------------------------------------ 모델


class Model:
    def __init__(self, name, tex=128, cell=32):
        self.name = name
        self.atlas = Atlas(tex, cell)
        self.parts = []
        self.cur = None
        self.glow = []
        self.anims = {}
        self.part("main")

    def part(self, name, pivot=(0, 0, 0)):
        for p in self.parts:
            if p["name"] == name:
                self.cur = p
                return self
        p = {"name": name, "pivot": [round(float(x), 4) for x in pivot], "q": []}
        self.parts.append(p)
        self.cur = p
        return self

    # 사각형 하나: 꼭짓점 넷 (반시계 — 바깥에서), uv 넷, 법선 넷 (없으면 면 법선), inside = 몸 안쪽 점 (주면 바깥을 보게 차례를 고침)
    def quad(self, ps, uvs, mat, ns=None, inside=None, double=False):
        ps = [v3(p) for p in ps]
        fn = unit(np.cross(ps[1] - ps[0], ps[2] - ps[0]) + np.cross(ps[2] - ps[0], ps[3] - ps[0]))
        if inside is not None:
            c = sum(ps) / 4
            if np.dot(fn, c - v3(inside)) < 0:
                ps = ps[::-1]
                uvs = uvs[::-1]
                ns = ns[::-1] if ns is not None else None
                fn = -fn
        if ns is None:
            ns = [fn] * 4
        q = []
        for p, uv, n in zip(ps, uvs, ns):
            n = unit(v3(n))
            q += [round(float(p[0]), 4), round(float(p[1]), 4), round(float(p[2]), 4), round(float(uv[0]), 5), round(float(uv[1]), 5),
                  round(float(n[0]), 3), round(float(n[1]), 3), round(float(n[2]), 3)]
        q.append(mat)
        self.cur["q"].append(q)
        if double:
            self.quad(ps[::-1], uvs[::-1], mat, [-v3(n) for n in ns[::-1]])
        return self

    def tri(self, a, b, c, uvs, mat, ns=None, inside=None):
        """세모 (사각형의 마지막 꼭짓점을 겹침)"""
        return self.quad([a, b, c, c], list(uvs) + [uvs[-1]], mat, None if ns is None else list(ns) + [ns[-1]], inside)

    def add_glow(self, x, y, z, r):
        self.glow.append([round(x, 3), round(y, 3), round(z, 3), round(r, 3)])
        return self

    # ------------------------------------------------------------ 모양

    def box(self, center, size, mat, uv, rot=None, faces="all"):
        """상자 (rot = 3×3 회전 — center 둘레): 면마다 uv 칸 전체"""
        c = v3(center)
        hx, hy, hz = (s / 2 for s in size)
        R = rot if rot is not None else np.eye(3)
        corners = {}
        for sx in (-1, 1):
            for sy in (-1, 1):
                for sz in (-1, 1):
                    corners[(sx, sy, sz)] = c + R @ v3(sx * hx, sy * hy, sz * hz)
        u0, v0, u1, v1 = uv
        uvq = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        quads = {
            "+x": [(1, -1, 1), (1, -1, -1), (1, 1, -1), (1, 1, 1)], "-x": [(-1, -1, -1), (-1, -1, 1), (-1, 1, 1), (-1, 1, -1)],
            "+y": [(-1, 1, 1), (1, 1, 1), (1, 1, -1), (-1, 1, -1)], "-y": [(-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1)],
            "+z": [(-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)], "-z": [(1, -1, -1), (-1, -1, -1), (-1, 1, -1), (1, 1, -1)],
        }
        for k, idx in quads.items():
            if faces != "all" and k not in faces:
                continue
            self.quad([corners[i] for i in idx], uvq, mat, inside=c)
        return self

    def loft(self, sections, mats, uv, close=True, cap0=True, cap1=True, smooth=False, uv_cap=None, mat_cap=None):
        """단면을 이어 붙인 몸: sections = [(y, [(x, z) …]) …] (단면의 꼭짓점 수가 같음, y 가 오름) 또는 [(점 목록 3D) …].
        mats = 옆면 재질 (단면의 변마다 — 하나면 모두), uv = 칸 (u 는 둘레, v 는 길이 — 끝 단면 v0). smooth = 꼭짓점 법선을 둘레로 고르게"""
        rings = []
        for s in sections:
            if isinstance(s[0], (int, float)):
                y, poly = s
                rings.append([v3(x, y, z) for x, z in poly])
            else:
                rings.append([v3(p) for p in s])
        n = len(rings[0])
        edges = n if close else n - 1
        if not isinstance(mats, (list, tuple)):
            mats = [mats] * edges
        u0, v0, u1, v1 = uv
        # 둘레 길이 비율로 u
        per = [0.0]
        for i in range(edges):
            a, b = rings[0][i], rings[0][(i + 1) % n]
            mid = [rings[len(rings) // 2][i], rings[len(rings) // 2][(i + 1) % n]]
            per.append(per[-1] + max(1e-6, np.linalg.norm(a - b) + np.linalg.norm(mid[0] - mid[1])))
        per = [p / per[-1] for p in per]
        # 길이 비율로 v
        cen = [sum(r) / n for r in rings]
        acc = [0.0]
        for i in range(1, len(rings)):
            acc.append(acc[-1] + max(1e-6, np.linalg.norm(cen[i] - cen[i - 1])))
        acc = [a / acc[-1] for a in acc]
        inside_pts = cen
        for j in range(len(rings) - 1):
            ra, rb = rings[j], rings[j + 1]
            inside = (cen[j] + cen[j + 1]) / 2
            for i in range(edges):
                i2 = (i + 1) % n
                ps = [ra[i], ra[i2], rb[i2], rb[i]]
                uvs = [(u0 + (u1 - u0) * per[i], v1 - (v1 - v0) * acc[j]), (u0 + (u1 - u0) * per[i + 1], v1 - (v1 - v0) * acc[j]),
                       (u0 + (u1 - u0) * per[i + 1], v1 - (v1 - v0) * acc[j + 1]), (u0 + (u1 - u0) * per[i], v1 - (v1 - v0) * acc[j + 1])]
                ns = None
                if smooth:
                    ns = [unit(ra[i] - cen[j]), unit(ra[i2] - cen[j]), unit(rb[i2] - cen[j + 1]), unit(rb[i] - cen[j + 1])]
                    ns = [n_ if np.linalg.norm(n_) > 0 else v3(0, 1, 0) for n_ in ns]
                # 끝이 한 점으로 모인 단면 (칼끝): 세모
                if np.linalg.norm(rb[i] - rb[i2]) < 1e-7 and np.linalg.norm(ra[i] - ra[i2]) < 1e-7:
                    continue
                self.quad(ps, uvs, mats[i], ns, inside=inside if not smooth else None)
        cu = uv_cap or uv
        cm = mat_cap if mat_cap is not None else mats[0]
        for cap, ring, other in ((cap0, rings[0], cen[1]), (cap1, rings[-1], cen[-2])):
            if not cap or not close:
                continue
            c = sum(ring) / n
            if max(np.linalg.norm(p - c) for p in ring) < 1e-7:
                continue
            for i in range(n):
                a, b = ring[i], ring[(i + 1) % n]
                cu0, cv0, cu1, cv1 = cu
                self.tri(c, a, b, [((cu0 + cu1) / 2, (cv0 + cv1) / 2), (cu0, cv1), (cu1, cv1)], cm, inside=other)
        return self

    def lathe(self, profile, segs, mat, uv, center=(0, 0, 0), axis_rot=None, smooth=True, mats=None):
        """돌린 몸 (종 · 구 · 고리): profile = [(반지름, y) …] (y 오름), segs = 둘레 조각. axis_rot = 3×3 (축 +Y 를 돌림)"""
        c = v3(center)
        R = axis_rot if axis_rot is not None else np.eye(3)
        rings = []
        for r, y in profile:
            ring = []
            for k in range(segs):
                a = 2 * math.pi * k / segs
                ring.append(c + R @ v3(r * math.cos(a), y, r * math.sin(a)))
            rings.append(ring)
        u0, v0, u1, v1 = uv
        # 법선: 단면의 기울기 (prof 의 이웃으로)
        prof = [v3(r, y, 0) for r, y in profile]
        for j in range(len(rings) - 1):
            m = mats[j] if mats else mat
            for k in range(segs):
                k2 = (k + 1) % segs
                ps = [rings[j][k], rings[j + 1][k], rings[j + 1][k2], rings[j][k2]]
                if np.linalg.norm(ps[0] - ps[3]) < 1e-7 and np.linalg.norm(ps[1] - ps[2]) < 1e-7:
                    continue
                uvs = [(u0 + (u1 - u0) * k / segs, v1 - (v1 - v0) * j / (len(rings) - 1)), (u0 + (u1 - u0) * k / segs, v1 - (v1 - v0) * (j + 1) / (len(rings) - 1)),
                       (u0 + (u1 - u0) * (k + 1) / segs, v1 - (v1 - v0) * (j + 1) / (len(rings) - 1)),
                       (u0 + (u1 - u0) * (k + 1) / segs, v1 - (v1 - v0) * j / (len(rings) - 1))]
                ns = None
                if smooth:
                    ns = []
                    for jj, kk in ((j, k), (j + 1, k), (j + 1, k2), (j, k2)):
                        a = 2 * math.pi * kk / segs
                        lo, hi = max(0, jj - 1), min(len(prof) - 1, jj + 1)
                        d = prof[hi] - prof[lo]
                        nr, ny = d[1], -d[0]  # 단면의 바깥 법선 (r, y)
                        nn = R @ v3(nr * math.cos(a), ny, nr * math.sin(a))
                        ns.append(unit(nn) if np.linalg.norm(nn) > 1e-9 else R @ v3(0, 1 if jj > 0 else -1, 0))
                # 차례: 바깥에서 반시계가 되게 (법선과 견줌)
                fn = np.cross(ps[1] - ps[0], ps[2] - ps[0]) + np.cross(ps[2] - ps[0], ps[3] - ps[0])
                ref = ns[0] + ns[1] + ns[2] + ns[3] if ns else (sum(ps) / 4 - c)
                if np.dot(fn, ref) < 0:
                    ps, uvs = ps[::-1], uvs[::-1]
                    ns = ns[::-1] if ns else None
                self.quad(ps, uvs, m, ns)
        return self

    def torus(self, R0, r, segs, sides, mat, uv, center=(0, 0, 0), axis_rot=None):
        """고리 (축 +Y — axis_rot 로 돌림)"""
        c = v3(center)
        Rm = axis_rot if axis_rot is not None else np.eye(3)
        u0, v0, u1, v1 = uv
        for i in range(segs):
            for j in range(sides):
                pts, ns, uvs = [], [], []
                for di, dj in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    a = 2 * math.pi * (i + di) / segs
                    b = 2 * math.pi * (j + dj) / sides
                    cc = v3(R0 * math.cos(a), 0, R0 * math.sin(a))
                    n_ = v3(math.cos(b) * math.cos(a), math.sin(b), math.cos(b) * math.sin(a))
                    pts.append(c + Rm @ (cc + n_ * r))
                    ns.append(Rm @ n_)
                    uvs.append((u0 + (u1 - u0) * (i + di) / segs, v0 + (v1 - v0) * (j + dj) / sides))
                fn = np.cross(pts[1] - pts[0], pts[2] - pts[0])
                if np.dot(fn, ns[0] + ns[2]) < 0:
                    pts, ns, uvs = pts[::-1], ns[::-1], uvs[::-1]
                self.quad(pts, uvs, mat, ns)
        return self

    # ------------------------------------------------------------ 쓰기

    def data(self):
        out = {"tex": "mmorpg:textures/fx/model/%s.png" % self.name, "glow": self.glow, "parts": self.parts}
        if self.anims:
            out["anims"] = self.anims
        return out

    def quads(self):
        return sum(len(p["q"]) for p in self.parts)


MODELS = {}


def model(fn):
    MODELS[fn.__name__] = fn
    return fn


# ------------------------------------------------------------ 모델들


def blade_sections(y0, y1, w0, w1, t0, t1, tip, n=5, bevel=0.68):
    """칼날 단면 (육각 — 날 · 비스듬한 날 면 · 넓은 옆면): y0 → y1 (너비 w0 → w1, 두께 t0 → t1) → 칼끝 tip (한 점)"""
    out = []
    for i in range(n):
        k = i / (n - 1)
        y = y0 + (y1 - y0) * k
        w = w0 + (w1 - w0) * k
        t = t0 + (t1 - t0) * k
        out.append((y, [(w, 0), (w * bevel, t), (-w * bevel, t), (-w, 0), (-w * bevel, -t), (w * bevel, -t)]))
    out.append((tip, [(0, 0)] * 6))
    return out


# 칼날 단면의 변 재질: 날 쪽 비스듬한 면 = 흰빛, 넓은 옆면 = 빛깔
BLADE_MATS = [WHITE, TINT, WHITE, WHITE, TINT, WHITE]


def octagon(r, rz=None):
    rz = r if rz is None else rz
    return [(r * math.cos(2 * math.pi * k / 8 + math.pi / 8), rz * math.sin(2 * math.pi * k / 8 + math.pi / 8)) for k in range(8)]


@model
def phantom_sword():
    """환영 검: 코등이 가운데가 원점, 칼끝 +Y (1.3칸), 넓은 옆면 ±Z. 날 = 흰빛 · 옆면 = 빛깔 (홈 · 룬) · 코등이 = 속빛 · 손잡이 = 짙은 빛깔 · 끝 보석 = 흰빛"""
    m = Model("phantom_sword")
    a = m.atlas
    blade = a.swatch("blade", p_blade, 1, 2)
    edge = a.swatch("edge", p_edge)
    grip = a.swatch("grip", p_grip)
    gem = a.swatch("gem", p_gem)
    band = a.swatch("band", p_band)
    flat = a.swatch("flat", p_flat, lum=0.92)
    # 칼날: 밑동 너비 0.085 → 0.07 (0.95) → 0.05 (1.12) → 끝 1.32
    secs = blade_sections(0.03, 0.95, 0.085, 0.068, 0.02, 0.016, 1.12, n=4)[:-1]
    secs.append((1.12, [(0.05, 0), (0.05 * 0.68, 0.013), (-0.05 * 0.68, 0.013), (-0.05, 0), (-0.05 * 0.68, -0.013), (0.05 * 0.68, -0.013)]))
    secs.append((1.32, [(0, 0)] * 6))
    m.loft(secs, BLADE_MATS, blade, cap0=True, cap1=False, uv_cap=flat, mat_cap=TINT)
    # 코등이: 가운데 덩이 + 양쪽으로 위로 휜 날개 (상자 셋씩)
    m.box((0, -0.005, 0), (0.12, 0.07, 0.07), GLOW, band)
    for s in (-1, 1):
        m.box((s * 0.11, 0.0, 0), (0.12, 0.045, 0.05), GLOW, band, rot=rot_z(s * 8))
        m.box((s * 0.19, 0.03, 0), (0.07, 0.04, 0.045), GLOW, band, rot=rot_z(s * 28))
        m.box((s * 0.235, 0.065, 0), (0.035, 0.05, 0.04), WHITE, gem, rot=rot_z(s * 40))
    # 손잡이: 팔각 (감은 끈)
    m.loft([(-0.04, octagon(0.027)), (-0.27, octagon(0.025))], DARK, grip)
    # 끝: 보석 (둥근 몸)
    m.lathe([(0.0, -0.355), (0.03, -0.34), (0.044, -0.31), (0.03, -0.28), (0.0, -0.268)], 8, WHITE, gem)
    m.add_glow(0, 0.25, 0, 0.26).add_glow(0, 0.7, 0, 0.24).add_glow(0, 1.1, 0, 0.18).add_glow(0, -0.02, 0, 0.2)
    return m


@model
def spear():
    """빛의 창: 자루 가운데가 원점, 창끝 +Y (1.45), 자루 끝 -1.25. 창날 (잎 모양 — 넓은 면 ±Z) · 날개 (양옆 갈고리) · 고리 띠 · 끝 쇠"""
    m = Model("spear")
    a = m.atlas
    blade = a.swatch("blade", p_blade, 1, 2, runes=False)
    edge = a.swatch("edge", p_edge)
    shaft = a.swatch("shaft", p_metal, lum=0.78)
    band = a.swatch("band", p_band)
    gem = a.swatch("gem", p_gem)
    flat = a.swatch("flat", p_flat, lum=0.92)
    # 자루 (팔각, 조금 가늘어짐)
    m.loft([(-1.25, octagon(0.03)), (0.82, octagon(0.026))], DARK, shaft)
    # 띠 셋
    for y in (-1.2, -0.25, 0.8):
        m.loft([(y - 0.04, octagon(0.04)), (y + 0.04, octagon(0.04))], GLOW, band)
    # 창날 밑 (소켓) — 짧은 원뿔
    m.lathe([(0.036, 0.84), (0.046, 0.9), (0.03, 0.97)], 8, GLOW, band, smooth=False)
    # 창날: 잎 모양 (넓어졌다 끝으로)
    secs = []
    for y, w in ((0.95, 0.035), (1.05, 0.085), (1.16, 0.095), (1.28, 0.07)):
        t = 0.018
        secs.append((y, [(w, 0), (w * 0.62, t), (-w * 0.62, t), (-w, 0), (-w * 0.62, -t), (w * 0.62, -t)]))
    secs.append((1.47, [(0, 0)] * 6))
    m.loft(secs, BLADE_MATS, blade, cap0=True, cap1=False, uv_cap=flat, mat_cap=TINT)
    # 날개 (옆으로 뻗은 작은 갈고리)
    for s in (-1, 1):
        m.box((s * 0.075, 0.93, 0), (0.1, 0.03, 0.03), WHITE, edge, rot=rot_z(s * -35))
        m.box((s * 0.115, 0.97, 0), (0.03, 0.07, 0.025), WHITE, edge, rot=rot_z(s * 20))
    # 자루 끝 쇠
    m.lathe([(0.0, -1.36), (0.03, -1.33), (0.04, -1.27), (0.03, -1.24)], 8, GLOW, gem)
    m.add_glow(0, 1.18, 0, 0.26).add_glow(0, 0.6, 0, 0.16).add_glow(0, -0.2, 0, 0.14).add_glow(0, -0.9, 0, 0.12)
    return m


@model
def dagger():
    """비수: 날 밑동이 원점 근처 (가운데 무게), 끝 +Y (0.5), 손잡이 -0.18"""
    m = Model("dagger")
    a = m.atlas
    blade = a.swatch("blade", p_blade, 1, 2, runes=False)
    grip = a.swatch("grip", p_grip)
    band = a.swatch("band", p_band)
    gem = a.swatch("gem", p_gem)
    flat = a.swatch("flat", p_flat, lum=0.92)
    secs = [(0.02, [(0.05, 0), (0.034, 0.013), (-0.034, 0.013), (-0.05, 0), (-0.034, -0.013), (0.034, -0.013)]),
            (0.3, [(0.042, 0), (0.028, 0.011), (-0.028, 0.011), (-0.042, 0), (-0.028, -0.011), (0.028, -0.011)]),
            (0.5, [(0, 0)] * 6)]
    m.loft(secs, BLADE_MATS, blade, cap1=False, uv_cap=flat, mat_cap=TINT)
    m.box((0, -0.005, 0), (0.17, 0.035, 0.045), GLOW, band)
    for s in (-1, 1):
        m.box((s * 0.1, -0.02, 0), (0.05, 0.03, 0.035), GLOW, band, rot=rot_z(s * -30))
    m.loft([(-0.025, octagon(0.019)), (-0.17, octagon(0.017))], DARK, grip)
    m.lathe([(0.0, -0.215), (0.024, -0.2), (0.026, -0.18), (0.0, -0.165)], 8, WHITE, gem)
    m.add_glow(0, 0.22, 0, 0.17).add_glow(0, -0.05, 0, 0.12)
    return m


@model
def light_arrow():
    """빛의 화살: 가운데가 원점, 촉 +Y (0.62), 깃 -0.55. 촉 (사각뿔 — 날개 둘) · 자루 · 깃 셋 (두 면)"""
    m = Model("light_arrow")
    a = m.atlas
    shaft = a.swatch("shaft", p_metal, lum=0.85)
    head = a.swatch("edge", p_edge)
    feather = a.swatch("feather", p_feather)
    band = a.swatch("band", p_band)
    m.loft([(-0.56, octagon(0.014)), (0.46, octagon(0.013))], GLOW, shaft)
    # 촉: 넓은 다이아몬드 (두 날개)
    secs = [(0.45, [(0.022, 0), (0.0, 0.02), (-0.022, 0), (0.0, -0.02)]), (0.5, [(0.06, 0), (0.0, 0.022), (-0.06, 0), (0.0, -0.022)]),
            (0.64, [(0, 0)] * 4)]
    m.loft(secs, [WHITE, TINT, WHITE, TINT], head, cap1=False)
    m.loft([(0.42, octagon(0.022)), (0.46, octagon(0.022))], WHITE, band)
    # 깃 셋 (120° 마다 — 두 면 판)
    for k in range(3):
        R = rot_y(k * 120)
        ps = [R @ v3(0.012, -0.55, 0), R @ v3(0.012, -0.3, 0), R @ v3(0.07, -0.36, 0), R @ v3(0.075, -0.56, 0)]
        u0, v0, u1, v1 = feather
        m.quad(ps, [(u0, v1), (u0, v0), (u1, v0 + (v1 - v0) * 0.25), (u1, v1)], TINT, double=True)
    m.add_glow(0, 0.52, 0, 0.17).add_glow(0, 0.0, 0, 0.1).add_glow(0, -0.45, 0, 0.13)
    return m


@model
def buckler():
    """둥근 방패: 가운데가 원점, 앞면 +Z (불룩), 반지름 0.48. 앞 = 십자 문장 (빛깔) · 테두리 (속빛 리벳) · 가운데 볼록 (흰빛) · 뒤 = 짙은 빛깔 + 손잡이"""
    m = Model("buckler")
    a = m.atlas
    emblem = a.swatch("emblem", p_emblem, 2, 2)
    rim = a.swatch("rim", p_rim)
    gem = a.swatch("gem", p_gem)
    back = a.swatch("back", p_metal, lum=0.7)
    flat = a.swatch("flat", p_flat, lum=0.9)
    Rz = rot_x(90)  # 축 +Y → +Z (앞)
    # 앞면: 낮은 둥근 판 (가운데가 조금 솟음) — 둘레 24 조각, 무늬 uv 는 원판 좌표로
    segs = 24
    rings = [(0.0, 0.075), (0.12, 0.07), (0.26, 0.055), (0.38, 0.035), (0.43, 0.025)]
    u0, v0, u1, v1 = emblem
    for j in range(len(rings) - 1):
        (r0, z0), (r1, z1) = rings[j], rings[j + 1]
        for k in range(segs):
            a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
            ps = [v3(r0 * math.cos(a0), r0 * math.sin(a0), z0), v3(r1 * math.cos(a0), r1 * math.sin(a0), z1),
                  v3(r1 * math.cos(a1), r1 * math.sin(a1), z1), v3(r0 * math.cos(a1), r0 * math.sin(a1), z0)]
            uvs = [(u0 + (u1 - u0) * (0.5 + 0.5 * p[0] / 0.48), v0 + (v1 - v0) * (0.5 - 0.5 * p[1] / 0.48)) for p in ps]
            ns = [unit(v3(p[0] * 0.5, p[1] * 0.5, 1.0)) for p in ps]
            fn = np.cross(ps[1] - ps[0], ps[2] - ps[0])
            if np.dot(fn, v3(0, 0, 1)) < 0:
                ps, uvs, ns = ps[::-1], uvs[::-1], ns[::-1]
            if np.linalg.norm(ps[0] - ps[3]) < 1e-9:
                ps, uvs, ns = [ps[0], ps[1], ps[2], ps[2]], [uvs[0], uvs[1], uvs[2], uvs[2]], [ns[0], ns[1], ns[2], ns[2]]
            m.quad(ps, uvs, TINT, ns)
    # 테두리 고리 (속빛)
    m.torus(0.45, 0.035, 24, 6, GLOW, rim, center=(0, 0, 0.02), axis_rot=Rz)
    # 뒷면 (평평한 원판 — 뒤를 봄)
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        r = 0.45
        ps = [v3(0, 0, -0.005), v3(r * math.cos(a1), r * math.sin(a1), 0.0), v3(r * math.cos(a0), r * math.sin(a0), 0.0)]
        m.tri(ps[0], ps[1], ps[2], [(back[0], back[1]), (back[2], back[1]), (back[2], back[3])], DARK, inside=(0, 0, 0.05))
    # 가운데 볼록 (흰빛)
    m.lathe([(0.0, 0.13), (0.045, 0.115), (0.07, 0.085), (0.075, 0.06)], 12, WHITE, gem, axis_rot=Rz)
    # 뒤 손잡이
    m.box((0, 0, -0.04), (0.2, 0.04, 0.03), DARK, flat)
    m.add_glow(0, 0, 0.05, 0.42).add_glow(0, 0, 0.1, 0.2)
    return m


# ------------------------------------------------------------ 빛 무리 그림 (모든 모델이 같이 씀)


def halo_png():
    """64 × 64 둥근 빛 (가운데 흰빛 → 가장자리 검정 · 투명 — 더하기 섞기에서 색이 됨)"""
    s = 64
    img = Image.new("RGBA", (s, s))
    px = img.load()
    for y in range(s):
        for x in range(s):
            r = math.hypot(x + 0.5 - s / 2, y + 0.5 - s / 2) / (s / 2)
            k = max(0.0, 1 - r) ** 2.2
            k = min(1.0, k * 1.15)
            g = int(k * 255 + 0.5)
            px[x, y] = (g, g, g, g)
    return img


# ------------------------------------------------------------ 미리 보기 (소프트웨어 래스터 — 면마다 그림 가운데 색 · 빛 방향 그늘)


def preview(m, path, size=360):
    views = [("front", rot_x(0)), ("side", rot_y(-90)), ("3/4", rot_x(-20) @ rot_y(-35)), ("top", rot_x(-90))]
    data = m.data()
    tex = m.atlas.img.convert("RGB")
    tw, th = tex.size
    tpx = tex.load()
    quads = []
    for p in data["parts"]:
        for q in p["q"]:
            vs = [np.array(q[i * 8:i * 8 + 3]) for i in range(4)]
            uv = [(q[i * 8 + 3], q[i * 8 + 4]) for i in range(4)]
            ns = [np.array(q[i * 8 + 5:i * 8 + 8]) for i in range(4)]
            quads.append((vs, uv, ns, q[32]))
    allp = np.array([v for qd in quads for v in qd[0]])
    lo, hi = allp.min(0), allp.max(0)
    cen = (lo + hi) / 2
    ext = max(hi - lo) * 0.62
    img = Image.new("RGB", (size * len(views), size + 18), (24, 26, 32))
    d = ImageDraw.Draw(img)
    light = unit(v3(0.35, 0.8, 0.5))
    for vi, (vn, R) in enumerate(views):
        panel = Image.new("RGB", (size, size), (24, 26, 32))
        pd = ImageDraw.Draw(panel)
        items = []
        for vs, uv, ns, mat in quads:
            pv = [R @ (v - cen) for v in vs]
            nn = R @ unit(sum(ns))
            # 뒷면 거르기 (카메라는 +Z 에서 -Z 를 봄)
            e1 = pv[1] - pv[0]
            e2 = pv[2] - pv[0]
            fn = np.cross(e1, e2)
            if np.linalg.norm(fn) < 1e-12:
                fn = np.cross(pv[2] - pv[0], pv[3] - pv[0])
            if fn[2] <= 0:
                continue
            cu = sum(u for u, _ in uv) / 4
            cv = sum(v for _, v in uv) / 4
            t = tpx[min(tw - 1, int(cu * tw)), min(th - 1, int(cv * th))]
            lum = t[0] / 255
            base = MAT_RGB[mat]
            sh = 0.45 + 0.55 * max(0, float(np.dot(R @ light, nn)) if False else max(0.0, float(np.dot(light, nn))))
            col = tuple(int(255 * min(1, c * lum * sh)) for c in base)
            z = sum(p[2] for p in pv) / 4
            pts = [(size / 2 + p[0] / ext * size / 2, size / 2 - p[1] / ext * size / 2) for p in pv]
            items.append((z, pts, col))
        items.sort(key=lambda x: x[0])
        for z, pts, col in items:
            pd.polygon(pts, fill=col)
        img.paste(panel, (vi * size, 18))
        d.text((vi * size + 4, 3), "%s  %s (%d quads)" % (m.name, vn, m.quads()), fill=(230, 230, 230))
    img.save(path)
    return path


# ------------------------------------------------------------ 쓰기


def build():
    out = {}
    for name, fn in MODELS.items():
        m = fn()
        out[name] = m
    return out


def write(check=False, previews=None):
    models = build()
    bad = []
    os.makedirs(OUT_MODEL, exist_ok=True)
    os.makedirs(OUT_TEX, exist_ok=True)
    files = {}
    for name, m in models.items():
        files[os.path.join(OUT_MODEL, name + ".json")] = json.dumps(m.data(), separators=(",", ":"), ensure_ascii=False)
    for path, text in files.items():
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old != text:
            bad.append(os.path.relpath(path, ROOT))
            if not check:
                open(path, "w", encoding="utf-8").write(text)
    pngs = {os.path.join(OUT_TEX, name + ".png"): m.atlas.img for name, m in models.items()}
    pngs[os.path.join(OUT_TEX, "halo.png")] = halo_png()
    for path, img in pngs.items():
        same = False
        if os.path.exists(path):
            old = Image.open(path).convert("RGBA")
            same = old.size == img.size and old.tobytes() == img.convert("RGBA").tobytes()
        if not same:
            bad.append(os.path.relpath(path, ROOT))
            if not check:
                img.save(path)
        meta = path + ".mcmeta"
        want = '{"texture": {"blur": true, "clamp": true}}\n'
        if not os.path.exists(meta) or open(meta).read() != want:
            bad.append(os.path.relpath(meta, ROOT))
            if not check:
                open(meta, "w").write(want)
    if check:
        if bad:
            print("fx models differ: " + ", ".join(bad) + " — python3 tools/fx_models.py")
            return 1
        print("fx models ok (%d models, %d quads)" % (len(models), sum(m.quads() for m in models.values())))
        return 0
    if previews is not None:
        outdir = os.path.join(ROOT, "build/fx_preview")
        os.makedirs(outdir, exist_ok=True)
        for name, m in models.items():
            if previews and name not in previews:
                continue
            print(preview(m, os.path.join(outdir, name + ".png")))
    print("wrote %d fx models (%d quads)%s" % (len(models), sum(m.quads() for m in models.values()), "" if not bad else " — changed: " + ", ".join(bad)))
    return 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--check" in args:
        sys.exit(write(check=True))
    pv = None
    if "--preview" in args:
        pv = [a for a in args if not a.startswith("--")]
    sys.exit(write(previews=pv))
