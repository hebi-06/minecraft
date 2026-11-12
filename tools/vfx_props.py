#!/usr/bin/env python3
"""2026-11-12 룬 연출의 3D 물체 (사용자 표시 — 룬 도감 메모: "검 3d", "망치 3d", "얼음 가시 3d", "바위 가시 3d", "구름 3d", "파도 연출 3d",
"물 연출 3d", "메테오 3d 모델 운석", "달 3d", "손 모양 3d", "거대한 주먹 고퀄", "그물 3d"). 도끼 (tools/rune_props.py) 와 같이 룬 전용 물체 하나씩 —
무기 디자인 (사용자 Claude Design) 과 따로. 서버가 아이템 표시 (vfx.Vfx.Prop) 로 띄움 → 모드 · 모드 없는 클라이언트 모두 같은 모습.

  python3 tools/vfx_props.py                      # 씀 (assets/rpg/models/item/vfx/<이름>.json · textures/item/vfx/<이름>.png (+ .mcmeta) · items/vfx/<이름>.json)
  python3 tools/vfx_props.py --check              # 쓸 것이 없는지
  python3 tools/vfx_props.py --preview <폴더> [이름…]   # 세 방향 그림

만드는 법
- 손으로 짠 상자 (검 · 망치 · 얼음 가시 · 바위 가시 · 손): 상자 + 한 축 회전 (22.5° 단위 — 자바 모델 규칙).
- 부피 조각 (구름 · 파도 · 운석 · 달 · 주먹 · 그물): 거리 함수 (SDF) 를 복셀로 → 재질마다 큰 상자로 합침 (greedy) → 밖으로 드러난 면만.
- 재질 = 그림판의 16 × 16 견본 (잡음 · 결 · 룬 글자 — 빛나는 재질은 프레임마다 맥동, .png.mcmeta). 물들임 (tintindex) 재질은 회색 견본 — 서버가 계열 색을
  아이템의 custom_model_data 색으로 줌 (아이템 정의 tints — 0 = 빛, 1 = 속빛).
"""
import argparse
import io
import json
import math
import os
import random
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
FRAMES = 6
FRAMETIME = 2
SW = 16  # 견본 한 변 (텍셀) — 모델 1 픽셀 = 1 텍셀


def clamp01(v):
    return max(0.0, min(1.0, v))


def mixc(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def rgba(c, a=1.0):
    return (int(round(clamp01(c[0]) * 255)), int(round(clamp01(c[1]) * 255)), int(round(clamp01(c[2]) * 255)), int(round(clamp01(a) * 255)))


# ------------------------------------------------------------------ 잡음

def hash3(x, y, z, seed=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 144269504) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, z, seed=0):
    """값 잡음 (부드럽게 — 0 ~ 1)"""
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    xf, yf, zf = x - xi, y - yi, z - zi
    u, v, w = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf), zf * zf * (3 - 2 * zf)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                k = (u if dx else 1 - u) * (v if dy else 1 - v) * (w if dz else 1 - w)
                out += k * hash3(xi + dx, yi + dy, zi + dz, seed)
    return out


def fbm(x, y, z, seed=0, oct=3):
    s, a, f, n = 0.0, 0.5, 1.0, 0.0
    for _ in range(oct):
        s += a * vnoise(x * f, y * f, z * f, seed)
        n += a
        a *= 0.5
        f *= 2.03
    return s / n


# ------------------------------------------------------------------ 재질 (견본 16 × 16)

class Mat:
    """gen(frame, rnd) → 16 × 16 RGBA · tint (None · 0 · 1) · glow (빛 상자 light_emission 15)"""

    def __init__(self, gen, tint=None, glow=False, animated=False):
        self.gen = gen
        self.tint = tint
        self.glow = glow
        self.animated = animated


def pulse(frame):
    return 0.5 + 0.5 * math.sin(frame / FRAMES * math.pi * 2)


def sw_noise(base, var=0.08, alpha=1.0, seed=1, streak=None):
    """잡음 견본: base 색 ± var (streak = 'v' 세로 결 · 'h' 가로 결)"""
    def gen(frame, rnd):
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                if streak == "v":
                    n = vnoise(x * 0.9, y * 0.12, 0, seed) - 0.5
                elif streak == "h":
                    n = vnoise(x * 0.12, y * 0.9, 0, seed) - 0.5
                else:
                    n = fbm(x * 0.35, y * 0.35, 0, seed) - 0.5
                c = tuple(v + n * var * 2 for v in base)
                px[x, y] = rgba(c, alpha)
        return im
    return gen


def sw_glow(c0, c1, alpha=1.0, seed=2, glyph=False, ring=False, bright=0.35):
    """빛 견본 (맥동): c0 (어둠) → c1 (밝음) 잡음 · 룬 글자 · 테두리"""
    def gen(frame, rnd):
        p = pulse(frame)
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                n = fbm(x * 0.3 + frame * 0.35, y * 0.3, 0, seed)
                t = clamp01(0.25 + n * 0.6 + p * bright)
                if ring:
                    e = min(x, y, SW - 1 - x, SW - 1 - y)
                    if e == 0:
                        t = 1.0
                px[x, y] = rgba(mixc(c0, c1, t), alpha)
        if glyph:
            d = ImageDraw.Draw(im)
            col = rgba(mixc(c1, (1, 1, 1), 0.4 + 0.5 * p), 1.0)
            k = rnd.randrange(3)
            if k == 0:
                d.line([(8, 2), (8, 13)], fill=col)
                d.line([(8, 5), (12, 8), (8, 11)], fill=col)
            elif k == 1:
                d.line([(8, 2), (8, 13)], fill=col)
                d.line([(4, 3), (8, 7), (12, 3)], fill=col)
            else:
                d.line([(8, 3), (12, 7), (8, 11), (4, 7), (8, 3)], fill=col)
                d.line([(8, 11), (5, 14)], fill=col)
                d.line([(8, 11), (11, 14)], fill=col)
        return im
    return gen


def sw_ice(alpha=0.78, seed=3):
    """얼음: 옅은 하늘빛 · 흰 금 · 속빛 (조금 맥동)"""
    def gen(frame, rnd):
        p = pulse(frame)
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                n = fbm(x * 0.28, y * 0.28, 0, seed)
                c = mixc((0.42, 0.72, 0.96), (0.86, 0.97, 1.0), clamp01(n * 1.2 - 0.1 + 0.08 * p))
                streak = abs(((x + y * 0.6) % 7) - 3.5) < 0.45
                if streak:
                    c = mixc(c, (1, 1, 1), 0.65)
                a = alpha + (0.18 if streak else 0)
                px[x, y] = rgba(c, a)
        return im
    return gen


def sw_rock(base=(0.42, 0.38, 0.34), seed=4, moss=False):
    def gen(frame, rnd):
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                n = fbm(x * 0.4, y * 0.4, 0, seed)
                c = tuple(v * (0.72 + 0.56 * n) for v in base)
                if hash3(x, y, 0, seed + 9) > 0.93:
                    c = tuple(v * 0.6 for v in c)
                if moss and n > 0.62:
                    c = mixc(c, (0.3, 0.45, 0.22), 0.5)
                px[x, y] = rgba(c)
        return im
    return gen


# ------------------------------------------------------------------ 물체

class Model:
    def __init__(self, name, credit, tex=64):
        self.name = name
        self.credit = credit
        self.tex = tex
        self.mats = {}
        self.order = []
        self.els = []
        self.tints = 0

    def mat(self, name, m):
        self.mats[name] = m
        self.order.append(name)
        if m.tint is not None:
            self.tints = max(self.tints, m.tint + 1)

    def slot(self, name):
        i = self.order.index(name)
        per = self.tex // SW
        return (i % per) * SW, (i // per) * SW

    def box(self, mat, f, t, rot=None, faces=None, name=None):
        """상자 (모델 픽셀) — rot = (축, 각, 중심) · faces = 그릴 면 (None = 모두)"""
        self.els.append({"mat": mat, "from": [float(v) for v in f], "to": [float(v) for v in t], "rot": rot, "faces": faces, "name": name or mat})

    def element_json(self, e, k):
        m = self.mats[e["mat"]]
        sx, sy = self.slot(e["mat"])
        f, t = e["from"], e["to"]
        dims = {"north": (t[0] - f[0], t[1] - f[1]), "south": (t[0] - f[0], t[1] - f[1]), "east": (t[2] - f[2], t[1] - f[1]),
                "west": (t[2] - f[2], t[1] - f[1]), "up": (t[0] - f[0], t[2] - f[2]), "down": (t[0] - f[0], t[2] - f[2])}
        faces = {}
        uvk = 16.0 / self.tex
        for fn in ("north", "south", "east", "west", "up", "down"):
            if e["faces"] is not None and fn not in e["faces"]:
                continue
            w = min(float(SW), max(1.0, dims[fn][0]))
            h = min(float(SW), max(1.0, dims[fn][1]))
            # 견본 안의 자리 (상자마다 다르게 — 같은 무늬가 되풀이되지 않게)
            r = random.Random(k * 31 + len(fn))
            ox = r.uniform(0, SW - w)
            oy = r.uniform(0, SW - h)
            uv = [round((sx + ox) * uvk, 4), round((sy + oy) * uvk, 4), round((sx + ox + w) * uvk, 4), round((sy + oy + h) * uvk, 4)]
            face = {"uv": uv, "texture": "#0"}
            if m.tint is not None:
                face["tintindex"] = m.tint
            faces[fn] = face
        out = {"name": e["name"], "from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": faces}
        if m.glow:
            out["light_emission"] = 15
        if e["rot"]:
            ax, ang, org = e["rot"]
            out["rotation"] = {"angle": ang, "axis": ax, "origin": [round(v, 4) for v in org]}
        return out

    def texture(self):
        frames = []
        animated = any(m.animated for m in self.mats.values())
        nf = FRAMES if animated else 1
        for fi in range(nf):
            im = Image.new("RGBA", (self.tex, self.tex), (0, 0, 0, 0))
            for name in self.order:
                m = self.mats[name]
                cell = m.gen(fi if m.animated else 0, random.Random(zlib.crc32(name.encode()) & 0xFFFF))
                im.paste(cell, self.slot(name))
            frames.append(im)
        sheet = Image.new("RGBA", (self.tex, self.tex * nf))
        for i, im in enumerate(frames):
            sheet.paste(im, (0, i * self.tex))
        return sheet, animated

    def json(self):
        els = [self.element_json(e, k) for k, e in enumerate(self.els)]
        return {"credit": "tools/vfx_props.py — " + self.credit, "textures": {"0": "rpg:item/vfx/" + self.name, "particle": "rpg:item/vfx/" + self.name},
                "elements": els}

    def item_json(self):
        model = {"type": "minecraft:model", "model": "rpg:item/vfx/" + self.name}
        if self.tints:
            model["tints"] = [{"type": "minecraft:custom_model_data", "index": i, "default": 16777215} for i in range(self.tints)]
        return {"model": model}


# ------------------------------------------------------------------ 부피 조각 (SDF → 복셀 → 상자)

def carve(model, lo, hi, res, solid, matof, min_box=0):
    """lo · hi (모델 픽셀 범위) 를 res 픽셀 복셀로: solid(x, y, z) → 참이면 채움, matof(x, y, z) → 재질 이름. 재질마다 큰 상자로 합치고 드러난 면만"""
    nx, ny, nz = (int(math.ceil((hi[i] - lo[i]) / res)) for i in range(3))
    grid = np.zeros((nx, ny, nz), dtype=np.int16)  # 0 = 비어 있음, k = 재질 번호 + 1
    names = []
    for i in range(nx):
        x = lo[0] + (i + 0.5) * res
        for j in range(ny):
            y = lo[1] + (j + 0.5) * res
            for k in range(nz):
                z = lo[2] + (k + 0.5) * res
                if solid(x, y, z):
                    m = matof(x, y, z)
                    if m not in names:
                        names.append(m)
                    grid[i, j, k] = names.index(m) + 1
    filled = grid > 0
    used = np.zeros_like(filled)
    boxes = []
    for i in range(nx):
        for j in range(ny):
            for k in range(nz):
                if not filled[i, j, k] or used[i, j, k]:
                    continue
                m = grid[i, j, k]
                # z 로 늘림
                k1 = k
                while k1 + 1 < nz and grid[i, j, k1 + 1] == m and not used[i, j, k1 + 1]:
                    k1 += 1
                # y 로 늘림
                j1 = j
                while j1 + 1 < ny and all(grid[i, j1 + 1, kk] == m and not used[i, j1 + 1, kk] for kk in range(k, k1 + 1)):
                    j1 += 1
                # x 로 늘림
                i1 = i
                while i1 + 1 < nx and all(grid[i1 + 1, jj, kk] == m and not used[i1 + 1, jj, kk] for jj in range(j, j1 + 1) for kk in range(k, k1 + 1)):
                    i1 += 1
                used[i:i1 + 1, j:j1 + 1, k:k1 + 1] = True
                boxes.append((i, j, k, i1, j1, k1, names[m - 1]))

    def covered(sl):
        return sl.size > 0 and bool(sl.all())

    for (i, j, k, i1, j1, k1, mname) in boxes:
        faces = []
        if not (i1 + 1 < nx and covered(filled[i1 + 1, j:j1 + 1, k:k1 + 1])):
            faces.append("east")
        if not (i - 1 >= 0 and covered(filled[i - 1, j:j1 + 1, k:k1 + 1])):
            faces.append("west")
        if not (j1 + 1 < ny and covered(filled[i:i1 + 1, j1 + 1, k:k1 + 1])):
            faces.append("up")
        if not (j - 1 >= 0 and covered(filled[i:i1 + 1, j - 1, k:k1 + 1])):
            faces.append("down")
        if not (k1 + 1 < nz and covered(filled[i:i1 + 1, j:j1 + 1, k1 + 1])):
            faces.append("south")
        if not (k - 1 >= 0 and covered(filled[i:i1 + 1, j:j1 + 1, k - 1])):
            faces.append("north")
        if not faces:
            continue
        f = (lo[0] + i * res, lo[1] + j * res, lo[2] + k * res)
        t = (lo[0] + (i1 + 1) * res, lo[1] + (j1 + 1) * res, lo[2] + (k1 + 1) * res)
        model.box(mname, f, t, faces=faces)
    return len(boxes)


def sph(x, y, z, cx, cy, cz, r):
    return math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2) - r


def capsule(x, y, z, a, b, r):
    pa = np.array([x - a[0], y - a[1], z - a[2]])
    ba = np.array([b[0] - a[0], b[1] - a[1], b[2] - a[2]])
    h = clamp01(float(pa @ ba) / float(ba @ ba))
    return float(np.linalg.norm(pa - ba * h)) - r


def rbox(x, y, z, c, half, r):
    q = [abs(x - c[0]) - half[0] + r, abs(y - c[1]) - half[1] + r, abs(z - c[2]) - half[2] + r]
    out = math.sqrt(sum(max(v, 0) ** 2 for v in q))
    return out + min(max(q[0], max(q[1], q[2])), 0) - r


def smin(a, b, k):
    h = clamp01(0.5 + 0.5 * (b - a) / k)
    return b * (1 - h) + a * h - k * h * (1 - h)


# ------------------------------------------------------------------ 검 (룬 전용 — 빛 = 계열 색)

def rune_sword():
    m = Model("rune_sword", "룬 전용 검 (2026-11-12 — 사용자 \"검 3d\", 룬 전용 무기 하나 · 계열 색은 서버가 물들임)")
    m.mat("blade", Mat(sw_glow((0.55, 0.55, 0.55), (0.95, 0.95, 0.95), alpha=0.92, seed=11), tint=0, glow=True, animated=True))
    m.mat("fuller", Mat(sw_glow((0.7, 0.7, 0.7), (1, 1, 1), seed=12, glyph=True, bright=0.5), tint=1, glow=True, animated=True))
    m.mat("edge", Mat(sw_noise((0.97, 0.98, 1.0), var=0.02, seed=13), glow=True))
    m.mat("steel", Mat(sw_noise((0.16, 0.16, 0.2), var=0.05, seed=14)))
    m.mat("gold", Mat(sw_noise((0.92, 0.72, 0.3), var=0.07, seed=15, streak="h")))
    m.mat("grip", Mat(sw_noise((0.22, 0.12, 0.1), var=0.06, seed=16, streak="h")))
    m.mat("gem", Mat(sw_glow((0.6, 0.6, 0.6), (1, 1, 1), seed=17, ring=True, bright=0.6), tint=0, glow=True, animated=True))
    # 손잡이 · 고리 · 자루 끝
    m.box("grip", (7.3, -7, 7.3), (8.7, 0, 8.7))
    for y in (-6.2, -3.6, -1.0):
        m.box("gold", (7.12, y, 7.12), (8.88, y + 0.6, 8.88))
    m.box("gold", (6.9, -9, 6.9), (9.1, -7, 9.1))
    m.box("gem", (7.35, -10.2, 7.35), (8.65, -9, 8.65))
    # 가드 (가운데 보석 · 양 팔 · 위로 꺾인 끝)
    m.box("steel", (6.8, 0, 5.2), (9.2, 1.8, 10.8))
    m.box("gold", (6.7, 0.25, 4.6), (9.3, 1.55, 5.2))
    m.box("gold", (6.7, 0.25, 10.8), (9.3, 1.55, 11.4))
    m.box("steel", (7.05, 0.35, 1.2), (8.95, 1.45, 4.6))
    m.box("steel", (7.05, 0.35, 11.4), (8.95, 1.45, 14.8))
    m.box("gold", (7.0, 0.8, -0.2), (9.0, 2.8, 1.2))
    m.box("gold", (7.0, 0.8, 14.8), (9.0, 2.8, 16.2))
    m.box("gem", (6.55, 0.35, 7.25), (9.45, 1.45, 8.75))
    # 날 (밑동 → 몸 → 줄어드는 끝) · 날 끝 흰 빛 · 가운데 홈 (룬 글자 — 속빛)
    m.box("steel", (7.35, 1.8, 6.0), (8.65, 4.0, 10.0))
    m.box("blade", (7.45, 4.0, 5.6), (8.55, 22.0, 10.4))
    m.box("edge", (7.7, 4.0, 4.9), (8.3, 22.0, 5.6))
    m.box("edge", (7.7, 4.0, 10.4), (8.3, 22.0, 11.1))
    m.box("blade", (7.5, 22.0, 6.0), (8.5, 25.0, 10.0))
    m.box("edge", (7.72, 22.0, 5.35), (8.28, 25.0, 6.0))
    m.box("edge", (7.72, 22.0, 10.0), (8.28, 25.0, 10.65))
    m.box("blade", (7.55, 25.0, 6.6), (8.45, 27.5, 9.4))
    m.box("edge", (7.75, 25.0, 6.0), (8.25, 27.5, 6.6))
    m.box("edge", (7.75, 25.0, 9.4), (8.25, 27.5, 10.0))
    m.box("edge", (7.62, 27.5, 7.0), (8.38, 29.5, 9.0))
    m.box("edge", (7.72, 29.5, 7.55), (8.28, 31.0, 8.45))
    m.box("fuller", (7.38, 5.0, 7.45), (8.62, 21.0, 8.55))
    return m


# ------------------------------------------------------------------ 망치 (룬 전용 — 빛 = 계열 색)

def rune_hammer():
    m = Model("rune_hammer", "룬 전용 망치 (2026-11-12 — 사용자 \"망치 3d\", 룬 전용 무기 하나 · 계열 색은 서버가 물들임)")
    m.mat("head", Mat(sw_noise((0.2, 0.2, 0.24), var=0.06, seed=21)))
    m.mat("rune", Mat(sw_glow((0.55, 0.55, 0.55), (1, 1, 1), seed=22, glyph=True, bright=0.55), tint=0, glow=True, animated=True))
    m.mat("face", Mat(sw_glow((0.6, 0.6, 0.6), (1, 1, 1), seed=23, glyph=True, bright=0.5), tint=0, glow=True, animated=True))
    m.mat("gold", Mat(sw_noise((0.92, 0.72, 0.3), var=0.07, seed=24, streak="h")))
    m.mat("haft", Mat(sw_noise((0.18, 0.12, 0.1), var=0.05, seed=25, streak="v")))
    m.mat("band", Mat(sw_glow((0.6, 0.6, 0.6), (1, 1, 1), seed=26, bright=0.5), tint=0, glow=True, animated=True))
    m.mat("edge", Mat(sw_noise((0.95, 0.96, 1.0), var=0.02, seed=27), glow=True))
    # 자루 · 빛 띠 · 자루 끝
    m.box("haft", (7.2, -8, 7.2), (8.8, 17, 8.8))
    for y in (-6.0, -0.5, 5.0, 10.5):
        m.box("band", (7.0, y, 7.0), (9.0, y + 0.8, 9.0))
    m.box("gold", (6.8, -10, 6.8), (9.2, -8, 9.2))
    m.box("band", (7.3, -11, 7.3), (8.7, -10, 8.7))
    # 머리 (검은 쇠 · 금 테 · 룬 판 · 빛나는 때리는 면 · 위 가시)
    # 머리: 가운데 몸 + 모서리를 깎은 판 (팔각 단면 — 앞뒤 · 위아래가 한 칸 좁음)
    m.box("head", (4.5, 17.8, 1.6), (11.5, 25.2, 14.4))
    m.box("head", (5.3, 17.0, 1.6), (10.7, 26.0, 14.4))
    m.box("gold", (4.2, 16.6, 3.8), (11.8, 26.4, 4.8))
    m.box("gold", (4.2, 16.6, 11.2), (11.8, 26.4, 12.2))
    m.box("gold", (6.6, 16.4, 6.6), (9.4, 17.0, 9.4))
    # 때리는 면: 금 테 안의 빛 (계열 색)
    m.box("gold", (4.8, 17.3, 0.9), (11.2, 25.7, 1.6))
    m.box("gold", (4.8, 17.3, 14.4), (11.2, 25.7, 15.1))
    m.box("face", (5.6, 18.1, 0.5), (10.4, 24.9, 0.9))
    m.box("face", (5.6, 18.1, 15.1), (10.4, 24.9, 15.5))
    m.box("rune", (4.25, 18.6, 5.6), (4.5, 24.4, 10.4))
    m.box("rune", (11.5, 18.6, 5.6), (11.75, 24.4, 10.4))
    m.box("rune", (5.6, 26.0, 2.6), (10.4, 26.25, 13.4))
    m.box("edge", (7.0, 26.25, 7.0), (9.0, 28.5, 9.0))
    m.box("edge", (7.5, 28.5, 7.5), (8.5, 30.5, 8.5))
    return m


# ------------------------------------------------------------------ 얼음 가시 · 얼음 칼 · 바위 가시

def faceted(m, mat, x0, x1, y0, y1, c=8.0, rot45=True, name=None):
    """가운데 c 둘레의 네모 기둥 + 45° 돌린 같은 기둥 (팔각 — 깎은 결정)"""
    m.box(mat, (x0, y0, x0), (x1, y1, x1), name=name)
    if rot45:
        m.box(mat, (x0, y0, x0), (x1, y1, x1), rot=("y", 45.0, (c, (y0 + y1) / 2, c)), name=name)


def ice_spike():
    m = Model("ice_spike", "얼음 가시 (2026-11-12 — 사용자 \"얼음 가시 3d\")")
    m.mat("ice", Mat(sw_ice(0.74, seed=31), glow=True, animated=True))
    m.mat("core", Mat(sw_ice(0.92, seed=32), glow=True, animated=True))
    m.mat("tip", Mat(sw_noise((0.9, 0.98, 1.0), var=0.03, alpha=0.95, seed=33), glow=True))
    faceted(m, "ice", 5.6, 10.4, 0, 13)
    faceted(m, "ice", 6.3, 9.7, 13, 18.5)
    faceted(m, "core", 7.0, 9.0, 18.5, 22.5)
    faceted(m, "tip", 7.55, 8.45, 22.5, 25.5)
    m.box("tip", (7.85, 25.5, 7.85), (8.15, 27, 8.15))
    # 옆 작은 결정 둘 (기울임)
    m.box("ice", (9.6, 0, 7.0), (11.6, 8, 9.0), rot=("z", -22.5, (10.6, 0, 8)))
    m.box("core", (10.0, 8, 7.4), (11.2, 11, 8.6), rot=("z", -22.5, (10.6, 0, 8)))
    m.box("ice", (7.0, 0, 4.4), (9.0, 6.5, 6.4), rot=("x", -22.5, (8, 0, 5.4)))
    m.box("core", (7.4, 6.5, 4.8), (8.6, 9, 6.0), rot=("x", -22.5, (8, 0, 5.4)))
    return m


def ice_blade():
    m = Model("ice_blade", "얼음 칼 (2026-11-12 — 사용자 \"얼음 칼 · 얼음 가시 3d로 연출\")")
    m.mat("ice", Mat(sw_ice(0.72, seed=41), glow=True, animated=True))
    m.mat("core", Mat(sw_ice(0.9, seed=42), glow=True, animated=True))
    m.mat("edge", Mat(sw_noise((0.92, 0.99, 1.0), var=0.02, alpha=0.96, seed=43), glow=True))
    m.mat("frost", Mat(sw_noise((0.62, 0.8, 0.95), var=0.06, alpha=1.0, seed=44)))
    # 서리 손잡이 · 얼음 가드 (가시 넷) · 날
    m.box("frost", (7.3, -6, 7.3), (8.7, 0, 8.7))
    faceted(m, "core", 7.0, 9.0, -8.5, -6)
    m.box("ice", (6.8, 0, 4.0), (9.2, 1.8, 12.0))
    m.box("core", (7.1, 0.4, 0.8), (8.9, 1.4, 4.0), rot=("x", 22.5, (8, 1, 4)))
    m.box("core", (7.1, 0.4, 12.0), (8.9, 1.4, 15.2), rot=("x", -22.5, (8, 1, 12)))
    m.box("ice", (7.45, 1.8, 5.8), (8.55, 21.0, 10.2))
    m.box("edge", (7.72, 1.8, 5.0), (8.28, 21.0, 5.8))
    m.box("edge", (7.72, 1.8, 10.2), (8.28, 21.0, 11.0))
    m.box("core", (7.35, 3.0, 7.4), (8.65, 20.0, 8.6))
    m.box("ice", (7.5, 21.0, 6.3), (8.5, 24.5, 9.7))
    m.box("edge", (7.75, 21.0, 5.6), (8.25, 24.5, 6.3))
    m.box("edge", (7.75, 21.0, 9.7), (8.25, 24.5, 10.4))
    m.box("edge", (7.62, 24.5, 6.9), (8.38, 27.0, 9.1))
    m.box("edge", (7.75, 27.0, 7.5), (8.25, 29.5, 8.5))
    return m


def rock_spike():
    m = Model("rock_spike", "바위 가시 (2026-11-12 — 사용자 \"바위 가시 3d\" — 골렘 · 노움: 금 사이 빛은 서버가 계열 색으로)")
    m.mat("rock", Mat(sw_rock((0.46, 0.41, 0.36), seed=51)))
    m.mat("dark", Mat(sw_rock((0.3, 0.27, 0.25), seed=52)))
    m.mat("moss", Mat(sw_rock((0.42, 0.4, 0.33), seed=53, moss=True)))
    m.mat("crack", Mat(sw_glow((0.5, 0.5, 0.5), (1, 1, 1), seed=54, bright=0.6), tint=0, glow=True, animated=True))
    faceted(m, "dark", 4.2, 11.8, 0, 6)
    faceted(m, "rock", 5.0, 11.0, 6, 12)
    faceted(m, "moss", 5.8, 10.2, 12, 17)
    faceted(m, "rock", 6.6, 9.4, 17, 21.5)
    m.box("rock", (7.3, 21.5, 7.3), (8.7, 24.5, 8.7))
    m.box("dark", (7.7, 24.5, 7.7), (8.3, 26.5, 8.3))
    # 금 사이 빛 (네 면에 세로 줄)
    m.box("crack", (4.1, 1.0, 7.5), (4.3, 9.0, 8.3))
    m.box("crack", (11.7, 2.0, 7.7), (11.9, 10.0, 8.5))
    m.box("crack", (7.6, 3.0, 4.1), (8.4, 11.0, 4.3))
    m.box("crack", (7.4, 1.5, 11.7), (8.2, 8.5, 11.9))
    m.box("crack", (4.9, 7.0, 8.0), (5.1, 15.0, 8.6))
    m.box("crack", (10.9, 9.0, 7.2), (11.1, 16.0, 7.8))
    # 밑동의 깨진 돌
    m.box("dark", (1.5, 0, 6.5), (4.0, 2.2, 9.0), rot=("y", 22.5, (2.75, 1, 7.75)))
    m.box("rock", (12.0, 0, 5.0), (14.2, 1.8, 7.4), rot=("y", -22.5, (13.1, 1, 6.2)))
    m.box("dark", (6.0, 0, 12.2), (8.6, 2.0, 14.6), rot=("y", 45.0, (7.3, 1, 13.4)))
    m.box("rock", (8.0, 0, 1.4), (10.2, 1.6, 3.6))
    return m


# ------------------------------------------------------------------ 구름 (빛 = 서버가 물들임 — 번개 구름 · 눈보라 · 치유의 비)

def cloud():
    m = Model("cloud", "구름 (2026-11-12 — 사용자 \"구름 3d\" — 색은 서버가 물들임: 폭풍 · 눈보라 · 치유의 비)", tex=64)
    m.mat("top", Mat(sw_noise((0.98, 0.98, 0.98), var=0.03, seed=61), tint=0))
    m.mat("mid", Mat(sw_noise((0.82, 0.82, 0.84), var=0.05, seed=62), tint=0))
    m.mat("low", Mat(sw_noise((0.6, 0.6, 0.64), var=0.06, seed=63), tint=0))
    m.mat("core", Mat(sw_glow((0.6, 0.6, 0.6), (1, 1, 1), seed=64, bright=0.7), tint=1, glow=True, animated=True))
    puffs = [(8, 7, 8, 6.5), (2.5, 6, 9, 5.0), (13.5, 6, 7, 5.2), (6, 9.5, 4.5, 4.4), (10.5, 9.5, 12, 4.6), (-2.5, 4.5, 7.5, 3.6), (18.5, 4.5, 9, 3.8),
             (4, 4.5, 13.5, 4.0), (12, 4.5, 2.5, 4.0), (9, 11.5, 8, 3.6), (-0.5, 4, 2.5, 3.0), (16.5, 4, 14, 3.2)]

    def d(x, y, z):
        v = min(sph(x, y, z, *p) for p in puffs)
        v += (fbm(x * 0.25, y * 0.25, z * 0.25, 5) - 0.5) * 1.6
        return max(v, 1.6 - y)  # 납작한 밑

    def solid(x, y, z):
        return d(x, y, z) <= 0

    def matof(x, y, z):
        if y < 3.4:
            # 밑의 빛 (폭풍 속 번개 · 치유의 빛 — 가운데 몇 곳)
            if math.hypot(x - 8, z - 8) < 5.2 and hash3(int(x), 0, int(z), 7) > 0.55:
                return "core"
            return "low"
        return "top" if y > 8.5 else "mid"

    n = carve(m, (-7, 1.6, -3), (23, 16, 19), 1.0, solid, matof)
    return m


# ------------------------------------------------------------------ 파도 · 물기둥 (해류 계열)

def sw_water(c0, c1, alpha=0.86, seed=121):
    """물: 깊은 색 → 밝은 색 물결 (조금 흐름 — 프레임마다 잡음이 밀림)"""
    def gen(frame, rnd):
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                n = fbm(x * 0.22, y * 0.22 + frame * 0.6, 0, seed)
                band = 0.5 + 0.5 * math.sin((y * 0.7 + frame * 1.1) + n * 4)
                c = mixc(c0, c1, clamp01(n * 0.9 + band * 0.25))
                px[x, y] = rgba(c, alpha)
        return im
    return gen


def wave():
    m = Model("wave", "파도 (2026-11-12 — 사용자 \"파도 연출 3d\" — 말려 넘어오는 물마루, 앞 = +Z)", tex=64)
    m.mat("deep", Mat(sw_water((0.02, 0.18, 0.42), (0.08, 0.36, 0.7), alpha=0.9, seed=121), glow=True, animated=True))
    m.mat("water", Mat(sw_water((0.05, 0.38, 0.82), (0.25, 0.66, 1.0), alpha=0.84, seed=122), glow=True, animated=True))
    m.mat("foam", Mat(sw_water((0.82, 0.93, 1.0), (1.0, 1.0, 1.0), alpha=0.96, seed=123), glow=True, animated=True))
    BZ, BY, BR = 11.0, 6.5, 5.0

    def prof(z, y, k):
        """옆모습 (z, y) — k = 이 자리의 크기 (양 끝으로 낮아짐)"""
        z = (z - 6) / k + 6
        y = y / k
        if y < 0:
            return None
        top = 17.5 * (1 - ((z - 6) / 20.0) ** 2) if z < 6 else -1
        dz, dy = z - BZ, y - BY
        r = math.hypot(dz, dy)
        a = math.degrees(math.atan2(dy, dz))
        thick = 3.4 - max(0.0, (40 - a) / 45.0) * 1.6 if a < 40 else 3.4
        if r < BR:
            return None
        if z >= -14 and z < 6 and y < top:
            return "body", top - y, 0
        if BR <= r <= BR + thick and -8 <= a <= 180:
            return "lip", BR + thick - r, a
        if math.hypot(z - 6, y - 12.2) < 5.4:
            return "body", 5.4 - math.hypot(z - 6, y - 12.2), 0
        if y < 2.0 and z < 18:
            return "foot", 2.0 - y, 0
        return None

    def kx(x):
        # 거의 같은 옆모습 (말린 안이 옆에서 보이게) — 양 끝 두 칸만 조금 낮게
        u = abs(x - 8) / 15.0
        return (1.0 if u < 0.8 else 1.0 - (u - 0.8) * 1.2) * (0.97 + 0.03 * math.sin(x * 0.9))

    def solid(x, y, z):
        return prof(z, y, kx(x)) is not None

    def matof(x, y, z):
        k = kx(x)
        kind, depth, a = prof(z, y, k)
        if kind == "foot":
            return "foam" if z > 9 else "water"
        if kind == "lip":
            # 물마루 끝 (앞 · 아래로 말린 끝) 만 흰 거품
            return "foam" if a < 38 else "water"
        if y > 15.2 * k:
            return "foam"
        if y < 5 * k and z < 3:
            return "deep"
        return "water"

    carve(m, (-7, 0, -15), (23, 20, 20), 1.0, solid, matof)
    return m


def water_column():
    m = Model("water_column", "물기둥 (2026-11-12 — 사용자 \"파도 · 물 연출 3d\" — 솟구치는 물, 꼭대기 물보라)", tex=64)
    m.mat("water", Mat(sw_water((0.1, 0.45, 0.85), (0.35, 0.75, 1.0), alpha=0.82, seed=131), glow=True, animated=True))
    m.mat("deep", Mat(sw_water((0.03, 0.22, 0.5), (0.1, 0.42, 0.8), alpha=0.88, seed=132), glow=True, animated=True))
    m.mat("foam", Mat(sw_water((0.82, 0.93, 1.0), (1.0, 1.0, 1.0), alpha=0.96, seed=133), glow=True, animated=True))

    def radius(y, a):
        # 밑 넓게 → 허리 → 꼭대기 물보라 (꽃처럼 벌어짐), 소용돌이 홈 (a — 각)
        base = 5.6 - 1.6 * clamp01(y / 10.0) + 3.4 * clamp01((y - 22) / 6.0)
        groove = 0.55 * math.sin(a * 3 + y * 0.45)
        return base + groove

    def solid(x, y, z):
        dx, dz = x - 8, z - 8
        r = math.hypot(dx, dz)
        a = math.atan2(dz, dx)
        if y > 29:
            return False
        if y > 22:
            # 꼭대기: 벌어진 물보라 (가운데는 비고 가장자리가 위로 튐)
            rr = radius(y, a)
            spike = 1.6 * (0.5 + 0.5 * math.sin(a * 7))
            return rr - 2.2 < r < rr and y < 26 + spike
        return r < radius(y, a)

    def matof(x, y, z):
        dx, dz = x - 8, z - 8
        a = math.atan2(dz, dx)
        if y > 21:
            return "foam"
        if 0.5 + 0.5 * math.sin(a * 3 + y * 0.45) > 0.82:
            return "foam"
        return "deep" if y < 4 else "water"

    carve(m, (-2, 0, -2), (18, 29, 18), 1.0, solid, matof)
    return m


# ------------------------------------------------------------------ 운석 · 달

def meteor():
    m = Model("meteor", "운석 (2026-11-12 — 사용자 \"메테오 3d 모델 운석으로 수정\")", tex=64)
    m.mat("rock", Mat(sw_rock((0.3, 0.24, 0.22), seed=71)))
    m.mat("dark", Mat(sw_rock((0.18, 0.15, 0.14), seed=72)))
    m.mat("lava", Mat(sw_glow((1.0, 0.32, 0.04), (1.0, 0.9, 0.45), seed=73, bright=0.6), glow=True, animated=True))
    m.mat("hot", Mat(sw_glow((1.0, 0.5, 0.1), (1.0, 0.97, 0.75), seed=74, bright=0.7), glow=True, animated=True))

    def d(x, y, z):
        r = 9.0 + (fbm(x * 0.18, y * 0.18, z * 0.18, 8) - 0.5) * 5.0
        return math.sqrt((x - 8) ** 2 + (y - 8) ** 2 + (z - 8) ** 2) - r

    def matof(x, y, z):
        # 금 (낮은 주파수 잡음의 0.5 등고선 — 가는 줄) · 떨어지는 쪽 (-y) 이 달아오름
        n = fbm(x * 0.16 + 3, y * 0.16, z * 0.16, 9, oct=2)
        if abs(n - 0.5) < 0.022:
            return "lava"
        hotness = (8 - y) / 9.0
        if hotness > 0.78:
            return "hot"
        if hotness > 0.6 and hash3(int(x * 2), int(y * 2), int(z * 2), 3) > 0.7:
            return "lava"
        n2 = fbm(x * 0.35, y * 0.35, z * 0.35, 10)
        return "dark" if n2 < 0.45 else "rock"

    carve(m, (-3, -3, -3), (19, 19, 19), 1.0, lambda x, y, z: d(x, y, z) <= 0, matof)
    return m


def moon():
    m = Model("moon", "핏빛 달 (2026-11-12 — 사용자 \"달 3d로 수정\" — 적월 강림)", tex=64)
    m.mat("high", Mat(sw_noise((0.92, 0.32, 0.3), var=0.05, seed=81), glow=True))
    m.mat("mare", Mat(sw_noise((0.62, 0.12, 0.14), var=0.05, seed=82), glow=True))
    m.mat("rim", Mat(sw_noise((1.0, 0.55, 0.5), var=0.04, seed=83), glow=True))
    m.mat("pit", Mat(sw_noise((0.42, 0.06, 0.08), var=0.04, seed=84), glow=True))
    craters = [((0.6, 0.5, -0.62), 0.32), ((-0.5, 0.2, -0.84), 0.22), ((0.1, -0.55, -0.83), 0.26), ((-0.8, -0.4, -0.45), 0.2), ((0.85, -0.1, 0.5), 0.28),
               ((-0.2, 0.9, 0.3), 0.24), ((0.3, 0.2, 0.93), 0.3), ((-0.7, 0.5, 0.5), 0.18), ((0.2, -0.95, 0.2), 0.22)]
    craters = [(np.array(c) / np.linalg.norm(c), r) for c, r in craters]
    R = 11.0

    def info(x, y, z):
        p = np.array([x - 8, y - 8, z - 8], float)
        ln = float(np.linalg.norm(p))
        if ln < 1e-6:
            return ln, None
        u = p / ln
        best = None
        for c, r in craters:
            ang = math.acos(max(-1, min(1, float(u @ c))))
            k = ang / r
            if k < 1.25 and (best is None or k < best):
                best = k
        return ln, best

    def solid(x, y, z):
        ln, k = info(x, y, z)
        rr = R
        if k is not None:
            if k < 1.0:
                rr -= 1.4 * (1 - k * k)  # 패인 곳
            elif k < 1.25:
                rr += 0.7  # 테
        return ln <= rr

    def matof(x, y, z):
        ln, k = info(x, y, z)
        if k is not None:
            if k < 0.75:
                return "pit"
            if k < 1.25:
                return "rim"
        n = fbm((x - 8) * 0.16, (y - 8) * 0.16, (z - 8) * 0.16, 85)
        return "mare" if n < 0.43 else "high"

    carve(m, (-4, -4, -4), (20, 20, 20), 1.0, solid, matof)
    return m


# ------------------------------------------------------------------ 손 (장풍) · 거대한 주먹

def palm():
    m = Model("palm", "기의 손바닥 (2026-11-12 — 사용자 \"손 모양 3d로 연출\" — 장풍, 빛 = 계열 색)", tex=64)
    m.mat("ki", Mat(sw_glow((0.55, 0.55, 0.55), (0.95, 0.95, 0.95), alpha=0.8, seed=91, bright=0.45), tint=0, glow=True, animated=True))
    m.mat("line", Mat(sw_glow((0.8, 0.8, 0.8), (1, 1, 1), seed=92, bright=0.5), tint=1, glow=True, animated=True))
    m.mat("seal", Mat(sw_glow((0.8, 0.8, 0.8), (1, 1, 1), seed=93, glyph=True, ring=True, bright=0.6), tint=1, glow=True, animated=True))
    # 손바닥 (+Z 가 앞 — 장풍을 미는 면) · 손목
    m.box("ki", (3.0, 0, 6.6), (13.0, 10.0, 9.4))
    m.box("seal", (4.6, 1.6, 9.4), (11.4, 8.4, 9.7))
    m.box("ki", (4.4, -5.0, 6.9), (11.6, 0, 9.1))
    m.box("line", (4.2, -1.2, 6.7), (11.8, -0.4, 9.3))
    # 손가락 넷 (두 마디 — 조금 벌림)
    for x0, h1, h2, tilt in ((3.2, 5.5, 4.0, 22.5), (5.8, 6.8, 4.6, 0.0), (8.4, 6.5, 4.4, 0.0), (11.0, 5.0, 3.6, -22.5)):
        org = (x0 + 1.1, 10.0, 8.0)
        rot = ("z", tilt, org) if tilt else None
        m.box("ki", (x0, 10.0, 6.8), (x0 + 2.2, 10.0 + h1, 9.2), rot=rot)
        m.box("ki", (x0 + 0.15, 10.0 + h1, 6.95), (x0 + 2.05, 10.0 + h1 + h2, 9.05), rot=rot)
        m.box("line", (x0 + 0.1, 10.0 + h1 - 0.35, 6.75), (x0 + 2.1, 10.0 + h1 + 0.25, 9.25), rot=rot)
    # 엄지 (옆으로 45°)
    m.box("ki", (-1.5, 2.0, 6.9), (3.0, 4.4, 9.1), rot=("z", 45.0, (3.0, 3.0, 8.0)))
    m.box("ki", (-5.0, 2.2, 7.05), (-1.5, 4.2, 8.95), rot=("z", 45.0, (3.0, 3.0, 8.0)))
    return m


def fist():
    m = Model("fist", "거대한 주먹 (2026-11-12 — 사용자 \"거대한 주먹 고퀄로 만든 후 마지막에 하늘에서 메테오처럼\" — 빛 = 계열 색)", tex=64)
    m.mat("skin", Mat(sw_glow((0.6, 0.6, 0.6), (0.9, 0.9, 0.9), seed=101, bright=0.28), tint=0, glow=True, animated=True))
    m.mat("shade", Mat(sw_glow((0.42, 0.42, 0.42), (0.66, 0.66, 0.66), seed=107, bright=0.2), tint=0, glow=True, animated=True))
    m.mat("knuckle", Mat(sw_glow((0.86, 0.86, 0.86), (1, 1, 1), seed=102, bright=0.45), tint=1, glow=True, animated=True))
    m.mat("wrap", Mat(sw_noise((0.9, 0.86, 0.78), var=0.05, seed=103, streak="h")))
    m.mat("bracer", Mat(sw_noise((0.24, 0.2, 0.18), var=0.05, seed=104)))
    m.mat("gold", Mat(sw_noise((0.95, 0.74, 0.3), var=0.07, seed=105, streak="h")))
    m.mat("rune", Mat(sw_glow((0.7, 0.7, 0.7), (1, 1, 1), seed=106, glyph=True, bright=0.6), tint=1, glow=True, animated=True))
    # 모델 축: 주먹이 -Y 로 내리찍음 — 아래 = 첫 마디의 평평한 등 (치는 면), 손등 = +Z, 손바닥 · 접힌 손가락 · 엄지 = -Z, 팔 = +Y
    xs = [3.4, 6.5, 9.6, 12.6]
    hw = [1.25, 1.3, 1.3, 1.15]

    def finger(x, y, z, i):
        c = xs[i]
        h = hw[i]
        lift = 0.5 if i == 3 else 0.0  # 새끼손가락은 조금 위
        prox = rbox(x, y, z, (c, 2.0 + lift, 0.6), (h, 1.45, 2.1), 0.8)
        mid = rbox(x, y, z, (c, 4.7 + lift, -1.75), (h - 0.05, 2.15, 1.2), 0.75)
        dist = rbox(x, y, z, (c, 6.7 + lift, -0.3), (h - 0.1, 0.95, 1.35), 0.65)
        return smin(smin(prox, mid, 0.45), dist, 0.4)

    def hand(x, y, z):
        back = rbox(x, y, z, (8.0, 8.6, 2.2), (6.1, 4.3, 2.0), 1.4)
        palm = rbox(x, y, z, (8.0, 9.4, -0.7), (5.7, 3.0, 1.6), 1.2)
        v = smin(back, palm, 1.0)
        for i, c in enumerate(xs):
            v = smin(v, sph(x, y, z, c, 3.7 + (0.5 if i == 3 else 0), 2.5, 1.55 if i < 3 else 1.4), 0.6)
        return v

    def thumb(x, y, z):
        a = capsule(x, y, z, (1.6, 10.2, 0.8), (2.2, 7.0, -2.2), 1.6)
        b = capsule(x, y, z, (2.2, 7.0, -2.2), (7.6, 6.1, -3.7), 1.3)
        return smin(a, b, 0.6)

    def arm(x, y, z):
        wrist = rbox(x, y, z, (8.0, 14.6, 1.0), (4.9, 2.6, 3.1), 2.0)
        fore = capsule(x, y, z, (8.0, 15.0, 1.0), (8.0, 23.5, 1.3), 4.7)
        return smin(wrist, fore, 1.2)

    def d(x, y, z):
        v = hand(x, y, z)
        f = min(finger(x, y, z, i) for i in range(4))  # 손가락 사이는 그대로 (홈)
        v = smin(v, f, 0.35)
        v = smin(v, thumb(x, y, z), 0.7)
        v = smin(v, arm(x, y, z), 1.0)
        return v

    def matof(x, y, z):
        if y > 15.6:
            if 16.0 < y < 16.9 or y > 22.6:
                return "gold"
            for zc in (-3.4, 5.8):
                if abs(z - zc) < 1.4 and 17.8 <= y <= 21.6 and abs(x - 8) < 1.6:
                    return "rune"
            return "bracer"
        if y > 11.9:
            return "wrap"
        # 치는 면 (첫 마디의 등 — 아래) · 손등 마디뼈
        if y < 1.15:
            return "knuckle"
        for i, c in enumerate(xs):
            if sph(x, y, z, c, 3.7 + (0.5 if i == 3 else 0), 2.5, 1.9) < 0 and z > 2.8:
                return "knuckle"
        if thumb(x, y, z) < 0.25:
            return "skin"
        # 손가락 사이 홈 (손바닥 쪽 · 아래) 그늘
        f = min(finger(x, y, z, i) for i in range(4))
        if f > -0.6 and min(abs(x - (xs[i] + xs[i + 1]) / 2) for i in range(3)) < 0.55 and y < 8 and z < 1.5:
            return "shade"
        return "skin"

    carve(m, (-1.0, -0.5, -5.5), (17.0, 24.5, 7.0), 0.5, lambda x, y, z: d(x, y, z) <= 0, matof)
    return m


# ------------------------------------------------------------------ 그물

def net():
    m = Model("net", "사냥 그물 (2026-11-12 — 사용자 \"그물 3d\" — 매듭 · 추, 빛 줄은 서버가 계열 색)", tex=64)
    m.mat("rope", Mat(sw_noise((0.62, 0.48, 0.3), var=0.07, seed=111, streak="v")))
    m.mat("magic", Mat(sw_glow((0.6, 0.6, 0.6), (1, 1, 1), seed=112, bright=0.55), tint=0, glow=True, animated=True))
    m.mat("knot", Mat(sw_noise((0.45, 0.33, 0.2), var=0.05, seed=113)))
    m.mat("weight", Mat(sw_noise((0.35, 0.36, 0.4), var=0.05, seed=114)))
    R = 15.0
    S = 3.4  # 그물 칸

    def surf(x, z):
        r2 = (x - 8) ** 2 + (z - 8) ** 2
        return 1.0 + 11.0 * max(0.0, 1 - r2 / (R * R))  # 위로 불룩한 덮개 (가운데 높이 12)

    def solid(x, y, z):
        r = math.hypot(x - 8, z - 8)
        if r > R:
            return False
        h = surf(x, z)
        if abs(y - h) > 0.5:
            return False
        gx = abs(((x - 8) / S + 0.5) % 1 - 0.5) * S
        gz = abs(((z - 8) / S + 0.5) % 1 - 0.5) * S
        rim = abs(r - (R - 0.3)) < 0.45
        return gx < 0.38 or gz < 0.38 or rim

    def matof(x, y, z):
        gx = abs(((x - 8) / S + 0.5) % 1 - 0.5) * S
        gz = abs(((z - 8) / S + 0.5) % 1 - 0.5) * S
        if gx < 0.5 and gz < 0.5:
            return "knot"
        r = math.hypot(x - 8, z - 8)
        if abs(r - (R - 0.3)) < 0.45:
            return "magic"
        # 몇 줄은 마력 (빛 — 계열 색)
        lx = round((x - 8) / S)
        lz = round((z - 8) / S)
        if (gx < 0.38 and lx % 2 == 0) or (gz < 0.38 and lz % 3 == 0):
            return "magic"
        return "rope"

    carve(m, (-7.5, 0.5, -7.5), (23.5, 13.0, 23.5), 0.5, solid, matof)
    # 가장자리 추 여덟
    for i in range(8):
        a = i / 8 * math.pi * 2
        cx, cz = 8 + math.cos(a) * (R - 0.3), 8 + math.sin(a) * (R - 0.3)
        m.box("weight", (cx - 0.9, 0.0, cz - 0.9), (cx + 0.9, 1.6, cz + 0.9))
    return m


# ------------------------------------------------------------------ 목록 · 쓰기 · 미리보기

# 미리보기 방향 (물체마다 — 기본은 앞 3/4 · 앞 · 옆 · 위)
VIEWS = {"fist": ((0.35, -0.35, -1.0), (0.25, 0.3, 1.0), (1.0, -0.15, -0.35), (0.25, -1.0, -0.35)),
         "wave": ((1.0, 0.12, 0.0), (0.7, 0.3, 1.0), (0.5, 0.35, -1.0), (-1.0, 0.12, 0.25))}

BUILD = {"rune_sword": rune_sword, "rune_hammer": rune_hammer, "ice_spike": ice_spike, "ice_blade": ice_blade, "rock_spike": rock_spike, "cloud": cloud,
         "meteor": meteor, "moon": moon, "palm": palm, "fist": fist, "net": net,
         "wave": wave, "water_column": water_column}
# 2026-11-12 사진 방식으로 다시 만든 물체 (사용자 "그냥 3d모델링만 내가 보낸 저 사진처럼") 는 tools/vfx_blocky.py 가 씀 — 여기서는 건너뜀 (그물만 여기)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vfx_blocky  # noqa: E402

# 2026-11-12: 핏빛 달 없음 (적월 강림은 2D 달) · 손바닥 · 파도 · 물기둥은 tools/vfx_shapes.py (조각 물체) — 여기서는 그물만
BUILD = {k: v for k, v in BUILD.items() if k == "net"}


def outputs(names=None):
    files = {}
    built = {}
    for name, fn in BUILD.items():
        if names and name not in names:
            continue
        m = fn()
        tex, animated = m.texture()
        buf = io.BytesIO()
        tex.save(buf, "PNG", optimize=True)
        js = m.json()
        for e in js["elements"]:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (name, e["name"], v)
        files[os.path.join(RES, "textures", "item", "vfx", name + ".png")] = buf.getvalue()
        if animated:
            files[os.path.join(RES, "textures", "item", "vfx", name + ".png.mcmeta")] = (json.dumps({"animation": {"frametime": FRAMETIME, "interpolate": True}},
                                                                                                    indent=2) + "\n").encode()
        files[os.path.join(RES, "models", "item", "vfx", name + ".json")] = (json.dumps(js, ensure_ascii=False, indent=1) + "\n").encode()
        files[os.path.join(RES, "items", "vfx", name + ".json")] = (json.dumps(m.item_json(), indent=2) + "\n").encode()
        built[name] = (m, js, tex)
    return files, built


def preview(out_dir, built, tints=None):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import bedrock_preview as bp
    import java_model_view as jv
    os.makedirs(out_dir, exist_ok=True)
    paths = []
    for name, (m, js, tex) in built.items():
        T = m.tex
        frame = np.asarray(tex.crop((0, 0, T, T)).convert("RGBA"), dtype=np.float64)
        tint = (tints or {}).get(name, [(0.45, 0.62, 1.0), (0.86, 0.92, 1.0)])
        qs = []
        for e in js["elements"]:
            el = jv.Element(e)
            C = el.corners()

            def P(ix, iy, iz):
                return C[(1 if ix else 0) | (2 if iy else 0) | (4 if iz else 0)]
            quads = {
                "north": (P(1, 1, 0), P(0, 1, 0), P(1, 0, 0)), "south": (P(0, 1, 1), P(1, 1, 1), P(0, 0, 1)),
                "east": (P(1, 1, 1), P(1, 1, 0), P(1, 0, 1)), "west": (P(0, 1, 0), P(0, 1, 1), P(0, 0, 0)),
                "up": (P(0, 1, 0), P(1, 1, 0), P(0, 1, 1)), "down": (P(0, 0, 1), P(1, 0, 1), P(0, 0, 0)),
            }
            for fn, fc in e["faces"].items():
                a, b, c = (np.array(p, float) / 16 for p in quads[fn])
                u0, v0, u1, v1 = (x / 16 * T for x in fc["uv"])
                nu = max(1, min(6, int(round(abs(u1 - u0)))))
                nv = max(1, min(6, int(round(abs(v1 - v0)))))
                du, dv = (b - a) / nu, (c - a) / nv
                ti = fc.get("tintindex")
                for j in range(nv):
                    for i in range(nu):
                        tu = u0 + (u1 - u0) * (i + 0.5) / nu
                        tv = v0 + (v1 - v0) * (j + 0.5) / nv
                        col = frame[min(T - 1, int(tv)), min(T - 1, int(tu))]
                        if col[3] < 20:
                            continue
                        rgbv = col[:3]
                        if ti is not None:
                            rgbv = rgbv * np.array(tint[min(ti, len(tint) - 1)])
                        p0 = a + du * i + dv * j
                        pts = np.array([p0, p0 + du, p0 + du + dv, p0 + dv])
                        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
                        ln = np.linalg.norm(n)
                        if ln < 1e-12:
                            continue
                        glow = e.get("light_emission", 0) > 0
                        qs.append((pts, (int(rgbv[0]), int(rgbv[1]), int(rgbv[2]), 254 if glow else 255), -n / ln))
        allp = np.array([p for q in qs for p in q[0]])
        cc = (allp.min(0) + allp.max(0)) / 2
        ext = float(max(allp.max(0) - allp.min(0))) * 1.5
        ims = []
        for dv in VIEWS.get(name, ((1.0, 0.45, -0.7), (0.05, 0.25, -1.0), (1.0, 0.1, 0.05), (0.4, 1.0, -0.3))):
            dv = np.array(dv, float)
            dv /= np.linalg.norm(dv)
            ims.append(bp.render(qs, cc + dv * ext * 1.15, cc, W=340, H=340, bg=(92, 98, 112)))
        sh = Image.new("RGB", (sum(i.size[0] for i in ims), ims[0].size[1]))
        x = 0
        for i in ims:
            sh.paste(i, (x, 0))
            x += i.size[0]
        p = os.path.join(out_dir, name + ".png")
        sh.save(p)
        paths.append((p, len(js["elements"])))
    return paths


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()
    files, built = outputs(a.names or None)
    if a.preview:
        for p, n in preview(a.preview, built):
            print(p, n, "elements")
        return
    stale = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            stale.append(os.path.relpath(path, ROOT))
            if not a.check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if a.check:
        if stale:
            print("vfx_props: 다시 만들어야 함:", ", ".join(stale))
            sys.exit(1)
        print("vfx_props: ok (%d)" % len(built))
    else:
        for name, (m, js, tex) in built.items():
            print("vfx_props: %-12s %4d elements · tints %d" % (name, len(js["elements"]), m.tints))
        print("vfx_props: 씀", len(stale))


if __name__ == "__main__":
    main()
