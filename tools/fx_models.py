#!/usr/bin/env python3
"""0.47.0 입체 효과 물체 (사용자 "다른 룬들도 다 고퀄리티의 모델링을 가지면 좋겠음 … 효과 물체를 입체 모델로"): 직업 스킬의 효과 물체
(환영 검 · 창 · 비수 · 빛의 화살 · 방패 …) 를 사각형 면들로 짠 입체 모델 + 그림으로 만듦.

  python3 tools/fx_models.py            # 씀 (assets/mmorpg/fxmodel/<이름>.json · textures/fx/model/<이름>.png)
  python3 tools/fx_models.py --check    # 쓸 것이 없는지
  python3 tools/fx_models.py --preview [이름 ...]   # build/fx_preview/<이름>.png (여러 방향에서 본 그림 — 깊이 버퍼 · 그림 · 재질 색)
  python3 tools/fx_models.py --preview gate@open:0.6 # 움직임 (이름@움직임:자리 0 ~ 1) 의 그 순간

모델 (자바 client FxModels 가 읽음):
  {"tex": 그림, "glow": [[x, y, z, 반지름] …] (빛 무리 — 카메라를 보는 둥근 빛, 클라이언트가 부채꼴로 더함), "parts": [{"name", "pivot", "q": [사각형 …]}], "anims": {…}}
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
import zlib

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
        per = size // cell
        self.used = [[False] * per for _ in range(per)]
        self.named = {}

    def swatch(self, name, painter, w=1, h=1, **kw):
        """칸 w × h 개 크기의 무늬 (같은 이름은 한 번만): 정규 UV (u0, v0, u1, v1) — 가장자리 반 픽셀 안쪽"""
        if name in self.named:
            return self.named[name]
        per = self.size // self.cell
        # 빈 자리 찾기 (줄마다 왼쪽부터 — w × h 칸이 모두 비어 있는 첫 자리. 여러 줄 칸이 아래 줄을 차지한 것도 셈)
        spot = None
        for row in range(per - h + 1):
            for col in range(per - w + 1):
                if all(not self.used[row + j][col + i] for j in range(h) for i in range(w)):
                    spot = (col, row)
                    break
            if spot:
                break
        if spot is None:
            raise RuntimeError("atlas full (%s)" % name)
        col, row = spot
        for j in range(h):
            for i in range(w):
                self.used[row + j][col + i] = True
        x0, y0 = col * self.cell, row * self.cell
        pw, ph = w * self.cell, h * self.cell
        rnd = random.Random(zlib.crc32(name.encode()) & 0xFFFF)  # 이름마다 같은 무늬 (hash 는 실행마다 바뀜)
        for y in range(ph):
            for x in range(pw):
                lum, a = painter(x / (pw - 1), y / (ph - 1), rnd, **kw)
                g = int(max(0, min(1, lum)) * 255 + 0.5)
                self.px[x0 + x, y0 + y] = (g, g, g, int(max(0, min(1, a)) * 255 + 0.5))
        self._bleed(x0, y0, pw, ph)
        s = float(self.size)
        uv = ((x0 + 0.5) / s, (y0 + 0.5) / s, (x0 + pw - 0.5) / s, (y0 + ph - 0.5) / s)
        self.named[name] = uv
        return uv


    def _bleed(self, x0, y0, pw, ph, passes=8):
        """비친 곳 (알파 0) 의 밝기를 이웃 그림의 밝기로 채움 (칸 안에서만): 게임은 그림을 부드럽게 (blur) 읽어 가장자리에서 이웃 칸과 섞음 — 비친 곳이
        검으면 반투명 가장자리가 검게 물듦 (0.48.1 사진첩: 불꽃 · 빛살 테두리가 갈색 · 회색). 알파는 그대로"""
        reg = np.array(self.img.crop((x0, y0, x0 + pw, y0 + ph)), dtype=float)
        lum, a = reg[:, :, 0], reg[:, :, 3]
        filled = a > 0
        if filled.all() or not filled.any():
            return
        lum = np.where(filled, lum, 0.0)
        for _ in range(passes):
            if filled.all():
                break
            pad_l = np.pad(lum * filled, 1)
            pad_f = np.pad(filled.astype(float), 1)
            acc = np.zeros_like(lum)
            cnt = np.zeros_like(lum)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dy == 0 and dx == 0:
                        continue
                    acc += pad_l[1 + dy:1 + dy + ph, 1 + dx:1 + dx + pw]
                    cnt += pad_f[1 + dy:1 + dy + ph, 1 + dx:1 + dx + pw]
            new = (~filled) & (cnt > 0)
            lum[new] = acc[new] / cnt[new]
            filled = filled | new
        if not filled.all():
            lum[~filled] = lum[filled].mean()
        g = np.clip(lum + 0.5, 0, 255).astype(np.uint8)
        out = np.dstack([g, g, g, reg[:, :, 3].astype(np.uint8)])
        self.img.paste(Image.fromarray(out, "RGBA"), (x0, y0))


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
        self.emissive = False  # 0.48.0 스스로 빛남 (빛기둥 · 불기둥 · 검기 · 불꽃 날개 — 모든 면을 고르게 가장 밝게)
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
        if self.emissive:
            out["emissive"] = True
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


# ------------------------------------------------------------ 0.47.0 더 (마탄 · 구체 · 금서 · 책장 · 대망치 · 종 · 명계의 문 · 망자의 손 · 환영 도끼 · 연 방패)


def p_rune_band(u, v, rnd):
    """룬 띠: 가로 줄 사이에 짧은 획 글자"""
    if v < 0.12 or v > 0.88:
        return 1.0, 1
    cu = (u * 10) % 1
    k = int(u * 10)
    stroke = (k * 7 + 3) % 4
    on = (stroke == 0 and abs(cu - 0.5) < 0.12) or (stroke == 1 and abs(v - 0.5) < 0.1 and 0.2 < cu < 0.8) or \
         (stroke == 2 and abs(cu - (v - 0.12) / 0.76) < 0.12) or (stroke == 3 and abs(cu - 0.3) < 0.1 and v > 0.3)
    return (1.0 if on else 0.62) + noise(rnd, 0.03), 1


def p_crystal(u, v, rnd):
    """결정 면: 비스듬한 결 · 가장자리 밝음"""
    edge = min(u, 1 - u)
    return 0.72 + 0.28 * (1 - min(1, edge * 6)) + 0.08 * math.sin((u + v) * 18) + noise(rnd, 0.02), 1


def p_page(u, v, rnd):
    """책장: 글줄 (밝은 바탕에 어두운 줄) · 가장자리 금빛 테"""
    if u < 0.06 or u > 0.94 or v < 0.05 or v > 0.95:
        return 0.75, 1
    line = (v * 16) % 1
    if 0.1 < u < 0.9 and 0.12 < v < 0.9 and line < 0.28 and rnd.random() > 0.15:
        return 0.55, 1
    return 0.97 + noise(rnd, 0.02), 1


def p_cover(u, v, rnd):
    """책 표지: 가장자리 테 · 가운데 마름모 문장 · 가죽 결"""
    x, y = abs(u - 0.5) * 2, abs(v - 0.5) * 2
    if x > 0.84 or y > 0.88:
        return 0.95, 1
    if x + y < 0.55 and x + y > 0.4:
        return 1.0, 1
    if x + y < 0.18:
        return 1.0, 1
    return 0.58 + 0.06 * math.sin(u * 40) * math.sin(v * 33) + noise(rnd, 0.03), 1


def p_stone(u, v, rnd, lum=0.72):
    """돌: 벽돌 줄눈 · 얼룩"""
    row = int(v * 4)
    off = 0.5 if row % 2 else 0.0
    bu = (u * 2 + off) % 1
    bv = (v * 4) % 1
    if bu < 0.05 or bv < 0.07:
        return lum * 0.55, 1
    return lum + 0.08 * math.sin(u * 23 + v * 17) + noise(rnd, 0.05), 1


def p_swirl(u, v, rnd):
    """소용돌이 (문 안): 가운데로 감기는 밝은 줄"""
    x, y = u - 0.5, v - 0.5
    r = math.hypot(x, y) * 2
    a = math.atan2(y, x)
    k = (a / (2 * math.pi) + r * 1.6) % 1
    lum = 0.45 + 0.55 * (1 - abs(k - 0.5) * 2) ** 2
    lum = lum * (1 - 0.35 * r) + (0.5 if r < 0.12 else 0)
    return min(1.0, lum) + noise(rnd, 0.02), 1


def p_vortex(u, v, rnd):
    """소용돌이 팔 셋 (밝은 줄 — 사이는 비침): 가운데 밝은 눈 · 가장자리로 옅어짐"""
    x, y = u - 0.5, v - 0.5
    r = math.hypot(x, y) * 2
    if r > 1:
        return 0.0, 0
    a = math.atan2(y, x)
    k = (a / (2 * math.pi) * 3 + r * 1.5) % 1
    streak = max(0.0, 1 - abs(k - 0.5) * 2) ** 3
    alpha = min(1.0, streak * 1.3 + (0.9 if r < 0.14 else 0.0) + 0.12) * (1 - r ** 4)
    return 0.72 + 0.28 * streak + noise(rnd, 0.02), alpha


def p_bone(u, v, rnd):
    return 0.86 + 0.08 * math.sin(v * 9 + u * 3) + noise(rnd, 0.03), 1


def extrude(m, poly, z0, z1, mat_front, mat_back, mat_side, uv_face, uv_side, center=None, bevel=0.0):
    """볼록한 다각형 (x, y) 를 z0 → z1 (두께) 로 밀어낸 판: 앞 (z1 · +Z) · 뒤 (z0) 는 가운데에서 부채꼴, 옆은 사각형. bevel = 앞면을 그만큼 안쪽으로 줄여 비스듬히"""
    n = len(poly)
    cx = sum(p[0] for p in poly) / n
    cy = sum(p[1] for p in poly) / n
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    u0, v0, u1, v1 = uv_face

    def fuv(x, y):
        return (u0 + (u1 - u0) * (x - x0) / max(1e-6, x1 - x0), v1 - (v1 - v0) * (y - y0) / max(1e-6, y1 - y0))

    inner = [(cx + (x - cx) * (1 - bevel), cy + (y - cy) * (1 - bevel)) for x, y in poly] if bevel else poly
    zc = (z0 + z1) / 2
    inside = (cx, cy, zc)
    for i in range(n):
        a, b = inner[i], inner[(i + 1) % n]
        m.tri((cx, cy, z1), (a[0], a[1], z1), (b[0], b[1], z1), [fuv(cx, cy), fuv(*a), fuv(*b)], mat_front, inside=inside)
        a2, b2 = poly[i], poly[(i + 1) % n]
        m.tri((cx, cy, z0), (b2[0], b2[1], z0), (a2[0], a2[1], z0), [fuv(cx, cy), fuv(*b2), fuv(*a2)], mat_back, inside=inside)
    su0, sv0, su1, sv1 = uv_side
    zb = z1 - (z1 - z0) * 0.35 if bevel else z1
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        m.quad([(a[0], a[1], z0), (b[0], b[1], z0), (b[0], b[1], zb), (a[0], a[1], zb)], [(su0, sv1), (su1, sv1), (su1, sv0), (su0, sv0)], mat_side,
               inside=inside)
        if bevel:
            ai, bi = inner[i], inner[(i + 1) % n]
            m.quad([(a[0], a[1], zb), (b[0], b[1], zb), (bi[0], bi[1], z1), (ai[0], ai[1], z1)], [(su0, sv1), (su1, sv1), (su1, sv0), (su0, sv0)],
                   mat_side, inside=inside)


def arc_band(m, r1, r2, a0, a1, segs, z0, z1, mat_face, mat_edge, uv_face, uv_edge, cx=0.0, cy=0.0, tip=0.0):
    """초승달 띠 (도끼날): 안 반지름 r1 · 바깥 r2, 각 a0 → a1 (도 — x 축에서 y 쪽으로), 두께 z0 → z1. tip = 양끝을 뾰족하게 (바깥 반지름을 그만큼 줄임)"""
    pts_in, pts_out = [], []
    for k in range(segs + 1):
        q = k / segs
        a = math.radians(a0 + (a1 - a0) * q)
        taper = 1 - tip * (abs(q - 0.5) * 2) ** 3
        ro = r1 + (r2 - r1) * taper
        pts_in.append((cx + r1 * math.cos(a), cy + r1 * math.sin(a)))
        pts_out.append((cx + ro * math.cos(a), cy + ro * math.sin(a)))
    u0, v0, u1, v1 = uv_face
    zc = (z0 + z1) / 2
    for k in range(segs):
        i0, i1, o0, o1 = pts_in[k], pts_in[k + 1], pts_out[k], pts_out[k + 1]
        mid = ((i0[0] + o1[0]) / 2, (i0[1] + o1[1]) / 2, zc)
        uvs = [(u0 + (u1 - u0) * k / segs, v1), (u0 + (u1 - u0) * (k + 1) / segs, v1), (u0 + (u1 - u0) * (k + 1) / segs, v0), (u0 + (u1 - u0) * k / segs, v0)]
        m.quad([(i0[0], i0[1], z1), (i1[0], i1[1], z1), (o1[0], o1[1], z1), (o0[0], o0[1], z1)], uvs, mat_face, inside=mid)
        m.quad([(i0[0], i0[1], z0), (i1[0], i1[1], z0), (o1[0], o1[1], z0), (o0[0], o0[1], z0)], uvs, mat_face, inside=mid)
        e0, e1, ev0, ev1 = uv_edge
        # 바깥 날 (흰빛) · 안쪽 테
        m.quad([(o0[0], o0[1], z0), (o1[0], o1[1], z0), (o1[0], o1[1], z1), (o0[0], o0[1], z1)], [(e0, ev1), (e1, ev1), (e1, ev0), (e0, ev0)], mat_edge, inside=mid)
        m.quad([(i0[0], i0[1], z0), (i1[0], i1[1], z0), (i1[0], i1[1], z1), (i0[0], i0[1], z1)], [(e0, ev1), (e1, ev1), (e1, ev0), (e0, ev0)], mat_face, inside=mid)
    # 양끝
    for i, o in ((pts_in[0], pts_out[0]), (pts_in[-1], pts_out[-1])):
        m.quad([(i[0], i[1], z0), (o[0], o[1], z0), (o[0], o[1], z1), (i[0], i[1], z1)], [(e0, ev1), (e1, ev1), (e1, ev0), (e0, ev0)], mat_edge,
               inside=(0, 0, zc))


@model
def mana_bolt():
    """마탄: 길쭉한 팔면 결정 (가운데가 원점 · 끝 +Y 0.34 · 꼬리 -0.36) + 둘레 작은 결정 셋"""
    m = Model("mana_bolt")
    a = m.atlas
    cry = a.swatch("crystal", p_crystal)
    gem = a.swatch("gem", p_gem)
    hexa = lambda r: [(r * math.cos(math.pi / 3 * k), r * math.sin(math.pi / 3 * k)) for k in range(6)]
    m.loft([(-0.36, [(0, 0)] * 6), (-0.08, hexa(0.05)), (0.02, hexa(0.075)), (0.12, hexa(0.06)), (0.34, [(0, 0)] * 6)], [GLOW, TINT, WHITE, GLOW, TINT, WHITE],
           cry, cap0=False, cap1=False)
    for k in range(3):
        R = rot_y(k * 120) @ rot_z(18)
        c = R @ v3(0.11, -0.12, 0)
        m.loft([[c + R @ v3(x, y, z) for x, y, z in ring] for ring in
                [[(0, -0.08, 0)] * 4, [(0.025, 0, 0), (0, 0, 0.025), (-0.025, 0, 0), (0, 0, -0.025)], [(0, 0.09, 0)] * 4]], [WHITE, GLOW, WHITE, GLOW], gem,
               cap0=False, cap1=False)
    m.add_glow(0, 0.02, 0, 0.32).add_glow(0, -0.2, 0, 0.18).add_glow(0, 0.22, 0, 0.16)
    return m


@model
def arcane_orb():
    """비전 구체: 가운데 구 (반지름 0.32 — 룬 띠) · 기울어 도는 고리 둘 (ring_a · ring_b — 움직임 spin) · 속의 밝은 핵"""
    m = Model("arcane_orb")
    a = m.atlas
    band = a.swatch("band", p_rune_band, 2, 1)
    gem = a.swatch("gem", p_gem)
    rim = a.swatch("rim", p_rim)
    prof = [(0.0, -0.32)] + [(0.32 * math.sin(math.pi * k / 10), -0.32 * math.cos(math.pi * k / 10)) for k in range(1, 10)] + [(0.0, 0.32)]
    m.lathe(prof, 14, TINT, band)
    m.lathe([(0.0, -0.16), (0.12, -0.1), (0.16, 0.0), (0.12, 0.1), (0.0, 0.16)], 8, WHITE, gem)
    m.part("ring_a")
    m.torus(0.48, 0.022, 28, 5, GLOW, rim, axis_rot=rot_x(70))
    m.part("ring_b")
    m.torus(0.42, 0.018, 24, 5, WHITE, rim, axis_rot=rot_z(65))
    m.anims["spin"] = {"len": 2.0, "tracks": {"ring_a": {"rot": [[0, 0, 0, 0], [0.5, 0, 180, 0], [1, 0, 360, 0]]},
                                              "ring_b": {"rot": [[0, 0, 0, 0], [0.5, 180, 0, 0], [1, 360, 0, 0]]}}}
    m.add_glow(0, 0, 0, 0.62).add_glow(0, 0, 0, 0.3)
    return m


@model
def grimoire():
    """금서 (펼친 책 — 등이 원점, 책장이 +Z 쪽을 봄, 위 +Y): 왼 · 오른 표지 (cover_l · cover_r — 등을 축으로 닫힘), 책장 뭉치 둘, 넘어가는 책장 (leaf — flip 되풀이),
    움직임 flip (책장이 오른쪽에서 왼쪽으로 넘어감) · close (두 표지가 닫힘)"""
    m = Model("grimoire", tex=128, cell=32)
    a = m.atlas
    cover = a.swatch("cover", p_cover, 2, 1)
    page = a.swatch("page", p_page, 1, 1)
    gold = a.swatch("gold", p_metal, lum=0.9)
    W, H, T = 0.58, 0.8, 0.035
    # 표지 (등 x=0 을 축으로 — 펼치면 조금 V 자)
    for side, name in ((-1, "cover_l"), (1, "cover_r")):
        m.part(name, (0, 0, 0))
        R = rot_y(-side * 8)
        pts = [R @ v3(side * x, y, 0) for x, y in ((0.0, -H / 2), (W, -H / 2), (W, H / 2), (0.0, H / 2))]
        pts_b = [p - v3(0, 0, T) for p in pts]
        u0, v0, u1, v1 = cover
        um = (u0 + u1) / 2
        cu = (u0, um) if side < 0 else (um, u1)
        quads = [(pts_b, cu, TINT), (pts, (page[0], page[2]), GLOW)]
        m.quad(pts if side > 0 else pts[::-1], [(page[0], page[3]), (page[2], page[3]), (page[2], page[1]), (page[0], page[1])] if side > 0 else
               [(page[0], page[1]), (page[2], page[1]), (page[2], page[3]), (page[0], page[3])], GLOW)
        m.quad(pts_b[::-1] if side > 0 else pts_b, [(cu[0], v1), (cu[1], v1), (cu[1], v0), (cu[0], v0)], TINT)
        # 표지 가장자리 (금빛 테)
        for i in range(4):
            p0, p1 = pts[i], pts[(i + 1) % 4]
            q0, q1 = pts_b[i], pts_b[(i + 1) % 4]
            m.quad([q0, q1, p1, p0], [(gold[0], gold[3]), (gold[2], gold[3]), (gold[2], gold[1]), (gold[0], gold[1])], GOLD, inside=sum(pts) / 4 - v3(0, 0, T / 2),
                   double=True)
        # 모서리 쇠 (표지 바깥 — 바깥 두 귀퉁이에 금빛 세모)
        for cy_ in (-1, 1):
            c0 = R @ v3(side * W, cy_ * H / 2, -T - 0.006)
            c1 = R @ v3(side * (W - 0.13), cy_ * H / 2, -T - 0.006)
            c2 = R @ v3(side * W, cy_ * (H / 2 - 0.13), -T - 0.006)
            m.tri(c0, c1, c2, [(gold[0], gold[1]), (gold[2], gold[1]), (gold[0], gold[3])], GOLD, inside=R @ v3(side * W * 0.8, 0, 0))
        # 가운데 보석 (표지 바깥)
        m.box(R @ v3(side * W * 0.5, 0, -T - 0.012), (0.09, 0.09, 0.02), WHITE, gold, rot=R @ rot_z(45))
        # 책장 뭉치 (표지 위 얇은 상자 — 흰 종이)
        m.box(R @ v3(side * W * 0.48, 0, 0.02), (W * 0.9, H * 0.92, 0.04), WHITE, page, rot=R)
    # 넘어가는 책장 (등을 축으로 오른쪽 → 왼쪽)
    m.part("leaf", (0, 0, 0.045))
    pts = [v3(0.0, -H * 0.44, 0.045), v3(W * 0.88, -H * 0.44, 0.045), v3(W * 0.88, H * 0.44, 0.045), v3(0.0, H * 0.44, 0.045)]
    m.quad(pts, [(page[0], page[3]), (page[2], page[3]), (page[2], page[1]), (page[0], page[1])], WHITE, double=True)
    m.part("main")
    # 등 (금빛 막대)
    m.loft([(-H / 2 - 0.02, octagon(0.03)), (H / 2 + 0.02, octagon(0.03))], GOLD, gold)
    m.anims["flip"] = {"len": 0.9, "tracks": {"leaf": {"rot": [[0, 0, -10, 0], [0.15, 0, -10, 0], [0.75, 0, -170, 0], [1, 0, -170, 0]],
                                                       "scale": [[0, 1], [0.9, 1], [0.95, 0.01], [1, 0.01]]}}}
    m.anims["close"] = {"len": 0.4, "tracks": {"cover_l": {"rot": [[0, 0, 0, 0], [1, 0, -82, 0]]}, "cover_r": {"rot": [[0, 0, 0, 0], [1, 0, 82, 0]]},
                                                "leaf": {"scale": [[0, 0.01], [1, 0.01]]}}}
    m.add_glow(0, 0, 0.12, 0.6).add_glow(-0.3, 0.1, 0.12, 0.25).add_glow(0.3, -0.1, 0.12, 0.25)
    return m


@model
def page():
    """책장 하나 (가운데가 원점, 넓은 면 ±Z, 위 +Y — 조금 휜 두 면)"""
    m = Model("page", tex=64, cell=32)
    a = m.atlas
    pg = a.swatch("page", p_page)
    u0, v0, u1, v1 = pg
    um = (u0 + u1) / 2
    w, h = 0.16, 0.22
    pts = [v3(-w, -h, 0.0), v3(0, -h, 0.03), v3(0, h, 0.03), v3(-w, h, 0.0)]
    m.quad(pts, [(u0, v1), (um, v1), (um, v0), (u0, v0)], WHITE, double=True)
    pts = [v3(0, -h, 0.03), v3(w, -h, 0.0), v3(w, h, 0.0), v3(0, h, 0.03)]
    m.quad(pts, [(um, v1), (u1, v1), (u1, v0), (um, v0)], WHITE, double=True)
    m.add_glow(0, 0, 0, 0.3)
    return m


@model
def maul():
    """번개 대망치: 머리 (+Y 쪽 — 떨어질 때 dir 아래) 가운데 원점 · 자루 -Y 로 2칸. 머리 = 깎은 상자 + 띠 + 양옆 번개 문장, 자루 끝 보석"""
    m = Model("maul")
    a = m.atlas
    head = a.swatch("head", p_metal, lum=0.86)
    band = a.swatch("band", p_band)
    grip = a.swatch("grip", p_grip)
    gem = a.swatch("gem", p_gem)
    rune = a.swatch("rune", p_rune_band, 2, 1)
    # 머리: 가운데 큰 상자 + 양끝 넓은 면 (8각 기둥 — 가로로 눕힘)
    Rh = rot_z(90)
    m.loft([[Rh @ v3(x, y, z) for x, y, z in [(px, -0.42, pz) for px, pz in octagon(0.24)]],
            [Rh @ v3(x, y, z) for x, y, z in [(px, -0.34, pz) for px, pz in octagon(0.3)]],
            [Rh @ v3(x, y, z) for x, y, z in [(px, 0.34, pz) for px, pz in octagon(0.3)]],
            [Rh @ v3(x, y, z) for x, y, z in [(px, 0.42, pz) for px, pz in octagon(0.24)]]], TINT, rune, cap0=True, cap1=True, uv_cap=head, mat_cap=WHITE)
    for x in (-0.36, 0.36):
        m.loft([[Rh @ v3(px, x - 0.03, pz) for px, pz in octagon(0.31)], [Rh @ v3(px, x + 0.03, pz) for px, pz in octagon(0.31)]], GLOW, band,
               cap0=False, cap1=False)
    # 자루
    m.loft([(-2.0, octagon(0.05)), (-0.28, octagon(0.055))], DARK, grip)
    for y in (-1.9, -1.1, -0.36):
        m.loft([(y - 0.05, octagon(0.07)), (y + 0.05, octagon(0.07))], GLOW, band)
    m.lathe([(0.0, -2.2), (0.08, -2.15), (0.1, -2.06), (0.06, -1.98)], 8, WHITE, gem)
    m.add_glow(0, 0, 0, 0.75).add_glow(0.3, 0, 0, 0.4).add_glow(-0.3, 0, 0, 0.4).add_glow(0, -1.0, 0, 0.25)
    return m


@model
def bell():
    """큰 종 (꼭대기 고리가 원점 · 종 입구가 -Y 1.3칸 아래 · 너비 1.3): 몸 (bell — 움직임 swing 은 꼭대기를 축으로 흔들림) · 추 (clapper — 늦게 흔들림)"""
    m = Model("bell")
    a = m.atlas
    body = a.swatch("body", p_metal, lum=0.84)
    band = a.swatch("band", p_band)
    rim = a.swatch("rim", p_rim)
    gem = a.swatch("gem", p_gem)
    m.part("bell", (0, 0, 0))
    prof = [(0.0, -0.12), (0.28, -0.14), (0.36, -0.22), (0.4, -0.45), (0.44, -0.75), (0.54, -1.02), (0.66, -1.2), (0.7, -1.28), (0.66, -1.3)]
    m.lathe(prof[::-1], 24, TINT, body)  # lathe 는 y 오름 (바깥 법선)
    m.lathe([(0.0, -0.19), (0.34, -0.19)], 24, DARK, body)  # 안쪽 천장 (아래를 봄)
    # 안쪽 (어두운 면 — 바깥 면을 거꾸로)
    inner = [(r * 0.94, y + 0.03) for r, y in prof[2:]]
    for j in range(len(inner) - 1):
        (r0, y0), (r1, y1) = inner[j], inner[j + 1]
        for k in range(24):
            a0, a1 = 2 * math.pi * k / 24, 2 * math.pi * (k + 1) / 24
            ps = [v3(r0 * math.cos(a0), y0, r0 * math.sin(a0)), v3(r0 * math.cos(a1), y0, r0 * math.sin(a1)), v3(r1 * math.cos(a1), y1, r1 * math.sin(a1)),
                  v3(r1 * math.cos(a0), y1, r1 * math.sin(a0))]
            m.quad(ps, [(body[0], body[1]), (body[2], body[1]), (body[2], body[3]), (body[0], body[3])], DARK, inside=v3(r0 * 2 * math.cos(a0), y0, r0 * 2 * math.sin(a0)))
    # 띠 둘 (속빛)
    m.torus(0.42, 0.03, 24, 5, GLOW, band, center=(0, -0.62, 0))
    m.torus(0.66, 0.035, 28, 5, GLOW, rim, center=(0, -1.2, 0))
    # 꼭대기 고리
    m.torus(0.1, 0.03, 12, 5, WHITE, rim, center=(0, 0.02, 0), axis_rot=rot_x(90))
    m.part("clapper", (0, -0.2, 0))
    m.loft([(-1.0, octagon(0.03)), (-0.2, octagon(0.03))], DARK, band)
    m.lathe([(0.0, -1.22), (0.08, -1.17), (0.1, -1.08), (0.07, -1.0), (0.0, -0.98)], 10, WHITE, gem)
    m.anims["swing"] = {"len": 0.8, "tracks": {"bell": {"rot": [[0, 0, 0, 0], [0.25, 22, 0, 0], [0.6, -16, 0, 0], [1, 0, 0, 0]]},
                                               "clapper": {"rot": [[0, 0, 0, 0], [0.3, 4, 0, 0], [0.48, 32, 0, 0], [0.75, -20, 0, 0], [1, 0, 0, 0]]}}}
    m.add_glow(0, -0.7, 0, 0.95).add_glow(0, -1.25, 0, 0.6)
    return m


@model
def gate():
    """명계의 문 (가운데 아래 (땅) 가 원점, 높이 3.4 · 너비 2.6 — 앞 +Z): 돌기둥 둘 · 아치 · 안의 소용돌이 판 (portal) · 문짝 둘 (door_l · door_r — 움직임 open · close)"""
    m = Model("gate", tex=128, cell=32)
    a = m.atlas
    stone = a.swatch("stone", p_stone)
    stone2 = a.swatch("stone2", p_stone, lum=0.6)
    swirl = a.swatch("swirl", p_swirl, 2, 2)
    vortex = a.swatch("vortex", p_vortex, 2, 2)
    rune = a.swatch("rune", p_rune_band, 2, 1)
    door = a.swatch("door", p_cover)
    for x in (-1.15, 1.15):
        m.box((x, 1.3, 0), (0.42, 2.6, 0.48), DARK, stone)
        m.box((x, 0.12, 0), (0.6, 0.24, 0.62), DARK, stone2)
        m.box((x, 2.66, 0), (0.56, 0.14, 0.6), GLOW, rune)
        m.box((x, 1.3, 0.25), (0.14, 1.9, 0.03), GLOW, rune)
    # 아치 (반원을 상자 조각으로)
    segs = 9
    for k in range(segs):
        q0, q1 = k / segs, (k + 1) / segs
        a0, a1 = math.pi * q0, math.pi * q1
        am = (a0 + a1) / 2
        c = v3(1.15 * math.cos(am), 2.7 + 0.75 * math.sin(am), 0)
        m.box(c, (0.42, 1.15 * (a1 - a0) * 1.05 + 0.02, 0.48), DARK, stone, rot=rot_z(math.degrees(am)))
    m.box((0, 3.48, 0), (0.36, 0.36, 0.56), WHITE, rune)  # 쐐기돌 (빛나는 룬)
    # 문 안 (아치 안쪽 모양 — 어두운 명계 · 두 면) + 그 앞의 도는 소용돌이 (팔 사이는 비침)
    m.part("portal", (0, 1.6, 0))
    u0, v0, u1, v1 = swirl
    rim_pts = [(-0.94, 0.05), (0.94, 0.05)] + [(0.94 * math.cos(math.pi * k / 12), 2.7 + 0.54 * math.sin(math.pi * k / 12)) for k in range(13)]
    rim_pts.append((-0.94, 0.05))
    ymin, ymax = 0.05, 3.24
    cy = (ymin + ymax) / 2

    def puv(x, y):
        return (u0 + (u1 - u0) * (x + 0.94) / 1.88, v1 - (v1 - v0) * (y - ymin) / (ymax - ymin))

    for (xa, ya), (xb, yb) in zip(rim_pts, rim_pts[1:]):
        if abs(xa - xb) < 1e-9 and abs(ya - yb) < 1e-9:
            continue
        tri = [v3(0, cy, -0.02), v3(xa, ya, -0.02), v3(xb, yb, -0.02)]
        uvs = [puv(0, cy), puv(xa, ya), puv(xb, yb)]
        m.quad(tri + [tri[-1]], uvs + [uvs[-1]], SHADOW, double=True)
    vx0, vy0, vx1, vy1 = vortex
    vc = (0.0, 1.75)
    vr = 0.86
    m.part("vortex", (vc[0], vc[1], 0.0))
    seg = 24
    for k in range(seg):
        a0, a1 = 2 * math.pi * k / seg, 2 * math.pi * (k + 1) / seg
        pa = (vc[0] + vr * math.cos(a0), vc[1] + vr * math.sin(a0))
        pb = (vc[0] + vr * math.cos(a1), vc[1] + vr * math.sin(a1))

        def vuv(x, y):
            return (vx0 + (vx1 - vx0) * (0.5 + (x - vc[0]) / (2 * vr)), vy0 + (vy1 - vy0) * (0.5 - (y - vc[1]) / (2 * vr)))

        tri = [v3(vc[0], vc[1], 0.0), v3(pa[0], pa[1], 0.0), v3(pb[0], pb[1], 0.0)]
        uvs = [vuv(*vc), vuv(*pa), vuv(*pb)]
        m.quad(tri + [tri[-1]], uvs + [uvs[-1]], GLOW, double=True)
    # 문짝 (기둥 안쪽을 축으로)
    for side, name in ((-1, "door_l"), (1, "door_r")):
        m.part(name, (side * 0.94, 0, 0.05))
        m.box((side * 0.47, 1.62, 0.05), (0.92, 3.1, 0.08), SHADOW, door)
        m.box((side * 0.47, 1.62, 0.1), (0.06, 2.6, 0.02), GLOW, rune)
    m.anims["open"] = {"len": 0.5, "tracks": {"door_l": {"rot": [[0, 0, 0, 0], [1, 0, 100, 0]]}, "door_r": {"rot": [[0, 0, 0, 0], [1, 0, -100, 0]]},
                                               "portal": {"scale": [[0, 0.01], [0.3, 0.3], [1, 1]]},
                                               "vortex": {"rot": [[0, 0, 0, 0], [1, 0, 0, -200]], "scale": [[0, 0.01], [0.4, 0.2], [1, 1]]}}}
    m.anims["close"] = {"len": 0.5, "tracks": {"door_l": {"rot": [[0, 0, 100, 0], [1, 0, 0, 0]]}, "door_r": {"rot": [[0, 0, -100, 0], [1, 0, 0, 0]]},
                                                "portal": {"scale": [[0, 1], [0.8, 0.2], [1, 0.01]]},
                                                "vortex": {"rot": [[0, 0, 0, 0], [1, 0, 0, -300]], "scale": [[0, 1], [0.6, 0.15], [1, 0.01]]}}}
    m.anims["swirl"] = {"len": 1.2, "tracks": {"door_l": {"rot": [[0, 0, 100, 0], [1, 0, 100, 0]]}, "door_r": {"rot": [[0, 0, -100, 0], [1, 0, -100, 0]]},
                                                "vortex": {"rot": [[0, 0, 0, 0], [0.5, 0, 0, -180], [1, 0, 0, -360]], "scale": [[0, 1], [0.5, 0.92], [1, 1]]}}}
    m.add_glow(0, 1.75, 0.1, 0.95).add_glow(-1.15, 2.66, 0.2, 0.3).add_glow(1.15, 2.66, 0.2, 0.3).add_glow(0, 3.48, 0.2, 0.35)
    return m


@model
def bone_hand():
    """망자의 손 (손목이 원점 · 손가락 끝 +Y — 땅에서 솟음, 손바닥이 +Z): 팔뚝 뼈 둘 · 손목뼈 · 손허리뼈 넷 · 갈고리처럼 굽은 손가락 넷 (f1 ~ f4 — 마디 셋)
    · 엄지 (thumb). 움직임 grasp = 손가락을 오므려 움켜쥠"""
    m = Model("bone_hand")
    a = m.atlas
    bone = a.swatch("bone", p_bone)
    joint = a.swatch("joint", p_gem)

    def rod(p0, p1, r0, r1, mat=TINT):
        """p0 → p1 뼈 (끝이 조금 굵은 팔각 — 마디 혹)"""
        p0, p1 = v3(p0), v3(p1)
        ax = unit(p1 - p0)
        side = unit(np.cross(ax, v3(0, 0, 1))) if abs(ax[2]) < 0.95 else v3(1, 0, 0)
        up = np.cross(side, ax)
        rings = []
        for t, r in ((0.0, r0 * 1.25), (0.15, r0), (0.85, r1), (1.0, r1 * 1.2)):
            c = p0 + (p1 - p0) * t
            rings.append([c + side * (r * math.cos(math.pi / 4 * k)) + up * (r * math.sin(math.pi / 4 * k)) for k in range(8)])
        m.loft(rings, mat, bone)

    # 팔뚝 (요골 · 척골 — 아래는 땅 속)
    rod((0.035, -1.2, 0.0), (0.04, -0.06, 0.0), 0.045, 0.04)
    rod((-0.045, -1.2, -0.01), (-0.05, -0.06, -0.005), 0.038, 0.034)
    # 손목뼈 (작은 덩이)
    m.lathe([(0.0, -0.06), (0.07, -0.04), (0.08, 0.0), (0.06, 0.04), (0.0, 0.05)], 8, TINT, bone, axis_rot=rot_z(90) @ np.diag([1, 1, 0.6]))
    # 손허리뼈 넷 → 손가락 (마디 셋 — 손바닥 쪽 +Z 로 굽음)
    for i in range(4):
        x = -0.075 + i * 0.05
        name = "f%d" % (i + 1)
        k0 = v3(x * 0.6, 0.03, 0.0)
        k1 = v3(x, 0.2 - abs(i - 1.5) * 0.015, 0.012)
        rod(k0, k1, 0.014, 0.016, GLOW)
        m.part(name, tuple(k1))
        ln = 0.1 + 0.02 * (1 - abs(i - 1.5) / 1.5)
        d1 = unit(v3(0, 1, 0.25))
        d2 = unit(v3(0, 0.75, 0.75))
        d3 = unit(v3(0, 0.25, 1))
        j1 = k1 + d1 * ln
        j2 = j1 + d2 * ln * 0.8
        j3 = j2 + d3 * ln * 0.7
        rod(k1, j1, 0.014, 0.012, GLOW)
        rod(j1, j2, 0.012, 0.011, GLOW)
        # 끝마디: 뾰족한 손톱
        m.loft([[j2 + v3(dx, dy, dz) for dx, dy, dz in ring] for ring in
                [[(0.011 * math.cos(math.pi / 2 * k), 0.011 * math.sin(math.pi / 2 * k), 0) for k in range(4)],
                 [tuple(d3 * ln * 0.7 * 0.5 + v3(0.009 * math.cos(math.pi / 2 * k), 0.009 * math.sin(math.pi / 2 * k), 0)) for k in range(4)],
                 [tuple(d3 * ln * 0.7 + v3(0, -0.01, 0.0))] * 4]], WHITE, joint, cap0=True, cap1=False)
        m.part("main")
    # 엄지 (옆에서 안쪽으로)
    m.part("thumb", (0.09, 0.04, 0.01))
    t0 = v3(0.09, 0.04, 0.01)
    t1 = t0 + unit(v3(0.6, 0.7, 0.3)) * 0.1
    t2 = t1 + unit(v3(0.1, 0.8, 0.6)) * 0.08
    rod(t0, t1, 0.016, 0.014, GLOW)
    rod(t1, t2, 0.013, 0.01, WHITE)
    m.part("main")
    m.anims["grasp"] = {"len": 0.6, "tracks": {**{"f%d" % (i + 1): {"rot": [[0, 0, 0, 0], [0.45, -15, 0, 0], [1, 70, 0, 0]]} for i in range(4)},
                                                "thumb": {"rot": [[0, 0, 0, 0], [1, 30, -25, -30]]}}}
    m.add_glow(0, 0.18, 0.05, 0.32).add_glow(0, -0.5, 0, 0.2)
    return m


@model
def phantom_axe():
    """환영 도끼 (자루 가운데가 원점, 위 +Y · 날은 +X 쪽 · 넓은 면 ±Z): 자루 · 초승달 날 (흰 날끝) · 뒤 갈고리"""
    m = Model("phantom_axe")
    a = m.atlas
    blade = a.swatch("blade", p_blade, 1, 2, runes=True)
    edge = a.swatch("edge", p_edge)
    grip = a.swatch("grip", p_grip)
    band = a.swatch("band", p_band)
    m.loft([(-0.75, octagon(0.03)), (0.62, octagon(0.03))], DARK, grip)
    for y in (-0.7, 0.32, 0.6):
        m.loft([(y - 0.03, octagon(0.042)), (y + 0.03, octagon(0.042))], GLOW, band)
    arc_band(m, 0.18, 0.46, -62, 62, 12, -0.025, 0.025, TINT, WHITE, blade, edge, cx=-0.05, cy=0.45, tip=0.35)
    m.box((0.06, 0.45, 0), (0.16, 0.2, 0.06), GLOW, band)
    m.box((-0.13, 0.45, 0), (0.14, 0.07, 0.04), WHITE, edge, rot=rot_z(-20))
    m.add_glow(0.3, 0.45, 0, 0.38).add_glow(0, 0, 0, 0.16)
    return m


@model
def aegis():
    """연 방패 (가운데가 원점, 위 +Y 0.75 · 아래 끝 -0.95, 앞 +Z): 연 모양 판 (조금 깎은 앞면) · 테 · 가운데 십자 보석"""
    m = Model("aegis")
    a = m.atlas
    face = a.swatch("face", p_emblem, 2, 2)
    rim = a.swatch("rim", p_rim)
    gem = a.swatch("gem", p_gem)
    poly = [(0, 0.78), (0.42, 0.66), (0.56, 0.3), (0.5, -0.2), (0.28, -0.62), (0, -0.98), (-0.28, -0.62), (-0.5, -0.2), (-0.56, 0.3), (-0.42, 0.66)]
    extrude(m, poly, -0.05, 0.05, TINT, DARK, GLOW, face, rim, bevel=0.08)
    m.box((0, 0.0, 0.07), (0.08, 0.9, 0.04), WHITE, gem)
    m.box((0, 0.18, 0.07), (0.6, 0.08, 0.04), WHITE, gem)
    m.lathe([(0.07, 0.04), (0.09, 0.07), (0.07, 0.12), (0.0, 0.15)], 10, WHITE, gem, center=(0, 0.18, 0.02), axis_rot=rot_x(90))
    m.add_glow(0, 0, 0.1, 0.85).add_glow(0, 0.18, 0.15, 0.3)
    return m


@model
def light_bow():
    """빛의 활 (손잡이가 원점, 위 +Y · 쏘는 쪽 +Z — 시위는 −Z 쪽): 앞으로 휜 팔 둘 (끝이 조금 되휨) · 금빛 손잡이 · 시위 (string — 쉴 때 곧게),
    당긴 시위 둘 (drawn_half · drawn — 깍지 자리로 V 자), 걸린 빛의 화살 (arrow). 움직임 draw (쉼 → 반 → 끝까지 · 화살이 나타남) · loose (놓음 — 시위가 돌아옴) ·
    rest (쉬는 시위만 — 움직임 없이 띄우면 시위 셋이 다 보이므로 늘 움직임과 함께)"""
    m = Model("light_bow")
    a = m.atlas
    limb = a.swatch("limb", p_rune_band, 2, 1)
    grip = a.swatch("grip", p_grip)
    gold = a.swatch("gold", p_metal, lum=0.9)
    gem = a.swatch("gem", p_gem)
    edge = a.swatch("edge", p_edge)
    TIP_Y, TIP_Z = 1.1, -0.22

    def limb_pt(t, sgn):
        y = sgn * (0.12 + 0.98 * t)
        z = -0.3 * t * t
        if t > 0.86:
            q = (t - 0.86) / 0.14
            z += 0.08 * q * q * (3 - 2 * q)
        return v3(0, y, z)

    for sgn in (1, -1):
        rings = []
        N = 12
        for k in range(N + 1):
            t = k / N
            p = limb_pt(t, sgn)
            p2 = limb_pt(min(1, t + 0.02), sgn) - limb_pt(max(0, t - 0.02), sgn)
            tan = unit(p2)
            nrm = unit(np.cross(tan, v3(1, 0, 0)))
            w = 0.07 - 0.035 * t
            th = 0.04 - 0.012 * t
            ring = [p + v3(w, 0, 0) + nrm * th, p + v3(-w, 0, 0) + nrm * th, p + v3(-w, 0, 0) - nrm * th, p + v3(w, 0, 0) - nrm * th]
            rings.append(ring if sgn > 0 else ring[::-1])
        m.loft(rings, [TINT, GLOW, TINT, GLOW], limb, cap0=False, cap1=True, mat_cap=WHITE)
        # 끝의 빛 보석
        tip = limb_pt(1.0, sgn)
        m.lathe([(0.0, -0.05), (0.04, -0.02), (0.05, 0.0), (0.04, 0.02), (0.0, 0.05)], 8, WHITE, gem, center=tuple(tip))
    # 손잡이 (금빛 감개 · 가운데 보석)
    m.loft([(-0.16, octagon(0.055)), (0.16, octagon(0.055))], GOLD, grip)
    for y in (-0.17, 0.17):
        m.loft([(y - 0.025, octagon(0.07)), (y + 0.025, octagon(0.07))], WHITE, gold)
    m.lathe([(0.0, -0.06), (0.05, -0.03), (0.06, 0.0), (0.05, 0.03), (0.0, 0.06)], 8, GLOW, gem, center=(0, 0, 0.06), axis_rot=rot_x(90))

    def cord(p0, p1, r=0.012, mat=WHITE):
        p0, p1 = v3(p0), v3(p1)
        ax = unit(p1 - p0)
        side = unit(np.cross(ax, v3(1, 0, 0))) if abs(ax[0]) < 0.95 else v3(0, 1, 0)
        up = np.cross(side, ax)
        rings = []
        for p in (p0, p1):
            rings.append([p + side * (r * math.cos(math.pi / 2 * k)) + up * (r * math.sin(math.pi / 2 * k)) for k in range(4)])
        m.loft(rings, mat, edge, cap0=False, cap1=False)

    top = v3(0, TIP_Y, TIP_Z)
    bot = v3(0, -TIP_Y, TIP_Z)
    m.part("string", (0, 0, TIP_Z))
    cord(top, bot)
    for name, nz in (("drawn_half", -0.55), ("drawn", -0.88)):
        m.part(name, (0, 0, nz))
        cord(top, (0, 0, nz))
        cord((0, 0, nz), bot)
    # 걸린 빛의 화살 (깍지 −0.88 → 촉 0.42)
    m.part("arrow", (0, 0, -0.88))
    m.loft([[v3(px, pz, z) for px, pz in octagon(0.018)] for z in (-0.88, 0.3)], GLOW, edge, cap0=True, cap1=False)
    m.loft([[v3(px * 0.06 / 0.05, pz * 0.06 / 0.05, 0.3) for px, pz in octagon(0.05)], [v3(px * 0.03 / 0.05, pz * 0.03 / 0.05, 0.38) for px, pz in octagon(0.05)],
            [v3(0, 0, 0.46)] * 8], WHITE, gem, cap0=True, cap1=False)
    m.part("main")
    m.anims["draw"] = {"len": 1.0, "tracks": {
        "string": {"scale": [[0, 1], [0.3, 1], [0.31, 0.01], [1, 0.01]]},
        "drawn_half": {"scale": [[0, 0.01], [0.3, 0.01], [0.31, 1], [0.62, 1], [0.63, 0.01], [1, 0.01]]},
        "drawn": {"scale": [[0, 0.01], [0.62, 0.01], [0.63, 1], [1, 1]]},
        "arrow": {"scale": [[0, 0.01], [0.3, 0.01], [0.45, 1], [1, 1]], "pos": [[0, 0, 0, 0.33], [0.62, 0, 0, 0.33], [0.63, 0, 0, 0], [1, 0, 0, 0]]}}}
    m.anims["rest"] = {"len": 1.0, "tracks": {"string": {"scale": [[0, 1], [1, 1]]}, "drawn_half": {"scale": [[0, 0.01], [1, 0.01]]},
                                               "drawn": {"scale": [[0, 0.01], [1, 0.01]]}, "arrow": {"scale": [[0, 0.01], [1, 0.01]]}}}
    m.anims["loose"] = {"len": 0.35, "tracks": {
        "string": {"scale": [[0, 0.01], [0.05, 1], [1, 1]], "rot": [[0, 0, 0, 0], [0.15, 9, 0, 0], [0.3, -6, 0, 0], [0.5, 3, 0, 0], [1, 0, 0, 0]]},
        "drawn_half": {"scale": [[0, 0.01], [1, 0.01]]},
        "drawn": {"scale": [[0, 1], [0.04, 0.01], [1, 0.01]]},
        "arrow": {"scale": [[0, 0.01], [1, 0.01]]}}}
    m.add_glow(0, 0.75, -0.15, 0.3).add_glow(0, -0.75, -0.15, 0.3).add_glow(0, 0, 0, 0.35).add_glow(0, 1.1, -0.22, 0.18).add_glow(0, -1.1, -0.22, 0.18)
    return m


# ------------------------------------------------------------ 미리 보기 (소프트웨어 래스터 — 면마다 그림 가운데 색 · 빛 방향 그늘)


def _anim_sample(keys, u, n):
    """FxModels.sample 과 같음 (열쇠 사이 smoothstep)"""
    if not keys:
        return None
    if u <= keys[0][0]:
        return keys[0][1:1 + n]
    for a, b in zip(keys, keys[1:]):
        if u <= b[0]:
            k = (u - a[0]) / max(1e-5, b[0] - a[0])
            k = k * k * (3 - 2 * k)
            return [a[j + 1] + (b[j + 1] - a[j + 1]) * k for j in range(n)]
    return keys[-1][1:1 + n]


def _part_matrix(part, track, u):
    """FxModels 의 부분 움직임: T(pivot + pos) · Rz · Ry · Rx · S · T(-pivot) — 4×4"""
    piv = v3(part["pivot"])
    M = np.eye(4)
    if not track:
        return M, False
    rot = _anim_sample(track.get("rot"), u, 3)
    pos = _anim_sample(track.get("pos"), u, 3)
    sc = _anim_sample(track.get("scale"), u, 1)
    if sc is not None and sc[0] <= 0.002:
        return M, True
    R = np.eye(3)
    if rot is not None:
        R = rot_z(rot[2]) @ rot_y(rot[1]) @ rot_x(rot[0])
    S = max(0.001, sc[0]) if sc is not None else 1.0
    t = piv + (v3(pos) if pos is not None else v3(0, 0, 0))
    M[:3, :3] = R * S
    M[:3, 3] = t - (R * S) @ piv
    return M, False


def preview(m, path, size=360, anim=None, u=1.0):
    """여러 방향에서 본 그림: 깊이 버퍼 · 그림의 밝기 (uv 를 꼭짓점 사이로 이어서) · 빛 방향 그늘. anim = 움직임 이름 (u = 0 ~ 1 의 자리)"""
    views = [("front", rot_x(0)), ("side", rot_y(-90)), ("3/4", rot_x(-20) @ rot_y(-35)), ("top", rot_x(-90))]
    data = m.data()
    tex = np.asarray(m.atlas.img.convert("RGBA"), dtype=float) / 255.0
    th, tw = tex.shape[:2]
    tracks = (data.get("anims", {}).get(anim, {}) or {}).get("tracks", {}) if anim else {}
    quads = []
    for p in data["parts"]:
        M, hidden = _part_matrix(p, tracks.get(p["name"]), u)
        if hidden:
            continue
        for q in p["q"]:
            vs = [M[:3, :3] @ np.array(q[i * 8:i * 8 + 3]) + M[:3, 3] for i in range(4)]
            uv = [(q[i * 8 + 3], q[i * 8 + 4]) for i in range(4)]
            ns = [unit(M[:3, :3] @ np.array(q[i * 8 + 5:i * 8 + 8])) for i in range(4)]
            quads.append((vs, uv, ns, q[32]))
    allp = np.array([v for qd in quads for v in qd[0]])
    lo, hi = allp.min(0), allp.max(0)
    cen = (lo + hi) / 2
    ext = max(hi - lo) * 0.62
    bg = np.array([24, 26, 32], dtype=float) / 255.0
    img = Image.new("RGB", (size * len(views), size + 18), (24, 26, 32))
    d = ImageDraw.Draw(img)
    light = unit(v3(0.35, 0.8, 0.5))
    for vi, (vn, R) in enumerate(views):
        zbuf = np.full((size, size), -1e9)
        cbuf = np.tile(bg, (size, size, 1))
        for vs, uv, ns, mat in quads:
            pv = [R @ (v - cen) for v in vs]
            fn = np.cross(pv[1] - pv[0], pv[2] - pv[0]) + np.cross(pv[2] - pv[0], pv[3] - pv[0])
            if fn[2] <= 0:
                continue
            nn = unit(R @ unit(sum(ns)))
            sh = 0.45 + 0.55 * max(0.0, float(np.dot(light, nn)))
            base = np.array(MAT_RGB[mat], dtype=float)
            sp = [(size / 2 + p[0] / ext * size / 2, size / 2 - p[1] / ext * size / 2, p[2]) for p in pv]
            for tri in ((0, 1, 2), (0, 2, 3)):
                (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = (sp[i] for i in tri)
                area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
                if abs(area) < 1e-9:
                    continue
                bx0 = max(0, int(math.floor(min(x0, x1, x2))))
                bx1 = min(size - 1, int(math.ceil(max(x0, x1, x2))))
                by0 = max(0, int(math.floor(min(y0, y1, y2))))
                by1 = min(size - 1, int(math.ceil(max(y0, y1, y2))))
                if bx1 < bx0 or by1 < by0:
                    continue
                xs, ys = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
                w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
                w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not inside.any():
                    continue
                z = w0 * z0 + w1 * z1 + w2 * z2
                zb = zbuf[by0:by1 + 1, bx0:bx1 + 1]
                win = inside & (z > zb)
                if not win.any():
                    continue
                (u0_, v0_), (u1_, v1_), (u2_, v2_) = (uv[i] for i in tri)
                tu = np.clip(((w0 * u0_ + w1 * u1_ + w2 * u2_) * tw).astype(int), 0, tw - 1)
                tv = np.clip(((w0 * v0_ + w1 * v1_ + w2 * v2_) * th).astype(int), 0, th - 1)
                lum = tex[tv, tu, 0]
                win = win & (tex[tv, tu, 3] > 0.35)  # 비치는 곳 (소용돌이 팔 사이) 은 그리지 않음
                col = np.clip(base[None, None, :] * (lum * sh)[:, :, None], 0, 1)
                cb = cbuf[by0:by1 + 1, bx0:bx1 + 1]
                cb[win] = col[win]
                zb[win] = z[win]
        panel = Image.fromarray((cbuf * 255).astype(np.uint8), "RGB")
        img.paste(panel, (vi * size, 18))
        label = "%s  %s (%d quads)" % (m.name, vn, m.quads()) + ("  %s @ %.2f" % (anim, u) if anim else "")
        d.text((vi * size + 4, 3), label, fill=(230, 230, 230))
    img.save(path)
    return path


# ------------------------------------------------------------ 0.48.0 무기 스킬의 효과 물체 (tools/fx_models_weapon.py)

import fx_models_weapon  # noqa: E402

fx_models_weapon.add(sys.modules[__name__])

# ------------------------------------------------------------ 0.48.2 2D 그림 판 (tools/fx_models_card.py — 참격 판)

import fx_models_card  # noqa: E402

fx_models_card.add(sys.modules[__name__])


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
        want = {}
        for a in previews:
            nm, _, rest = a.partition("@")
            want.setdefault(nm, []).append(rest)
        for name, m in models.items():
            if previews and name not in want:
                continue
            for rest in want.get(name, [""]):
                if not rest:
                    print(preview(m, os.path.join(outdir, name + ".png")))
                    continue
                an, _, uu = rest.partition(":")
                uu = float(uu or 1)
                print(preview(m, os.path.join(outdir, "%s@%s_%.2f.png" % (name, an, uu)), anim=an, u=uu))
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
