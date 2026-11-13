#!/usr/bin/env python3
"""2026-11-12 기울인 조각 물체 (사용자 "구지 마크 느낌살려서 각지게 할 필요가 없을거 같다 만년빙하도 잘나온거 보면 만년빙하처럼 파도 같은거 만들어도 됨").

만년빙하 (rune.RuneGlacier) 의 거대한 얼음 = 얼음 블록을 꼭짓점으로 세워 (대각선 → 위) 비스듬히 기울이고 길게 늘린 반투명 결정을 여러 개 겹친 것 —
자바 모델 상자 (한 축 22.5° 회전) 의 각진 계단 대신 표시 엔티티 변환 (옮김 · 왼쪽 회전 · 크기 · 오른쪽 회전) 으로 아무 방향 · 아무 비율.
여기서는 물체 하나 = 조각 (반투명 상자 · 꼭짓점으로 세운 결정) 여러 개: 파도 · 물기둥 · 기의 손바닥. 서버 (vfx.Vfx.Group) 가 조각마다 아이템 표시를 띄워
같이 옮김 · 돌림 · 키움 (쥔 곳 · 크기 단위는 예전 모델과 같음 — 모델 픽셀, 16 = 1칸).

  python3 tools/vfx_shapes.py                       # 씀 (mmorpg/vfx_shapes.json · 조각 모델 rpg:vfx/shard_<재질> · 그림 · 아이템 정의)
  python3 tools/vfx_shapes.py --check
  python3 tools/vfx_shapes.py --preview <폴더> [이름…]  # 미리보기 (조각마다 변환 · 반투명 겹침 · 마인크래프트 빛 두 방향)

조각 (vfx_shapes.json parts): m = 재질 (shard_<m>), c = 가운데 (모델 픽셀), q = 회전 (x, y, z, w — 모델 축에서), s = 크기 (모델 픽셀 — 상자: 가로 · 세로 · 깊이,
결정 (tip): 가장 굵은 지름 · 끝에서 끝 길이 · 지름), tip = 꼭짓점으로 세운 정육면체 (만년빙하 결정과 같은 꼴 — 위아래가 뾰족).
"""
import argparse
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "rpg")
OUT_JSON = os.path.join(RES, "mmorpg", "vfx_shapes.json")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_model_view as jv  # noqa: E402

FRAMES = 8
FRAMETIME = 2
TEX = 32
DIAG = math.sqrt(3.0)
WIDE = math.sqrt(2.0 / 3.0)


def C(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


# ------------------------------------------------------------------ 회전 (사원수 x, y, z, w)

def q_from_basis(x, y, z):
    """열 = 새 축 x · y · z (정규 직교) 인 회전 → 사원수"""
    m = np.column_stack([x, y, z])
    t = np.trace(m)
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        w = 0.25 * s
        qx = (m[2, 1] - m[1, 2]) / s
        qy = (m[0, 2] - m[2, 0]) / s
        qz = (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w = (m[2, 1] - m[1, 2]) / s
        qx = 0.25 * s
        qy = (m[0, 1] + m[1, 0]) / s
        qz = (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w = (m[0, 2] - m[2, 0]) / s
        qx = (m[0, 1] + m[1, 0]) / s
        qy = 0.25 * s
        qz = (m[1, 2] + m[2, 1]) / s
    else:
        s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w = (m[1, 0] - m[0, 1]) / s
        qx = (m[0, 2] + m[2, 0]) / s
        qy = (m[1, 2] + m[2, 1]) / s
        qz = 0.25 * s
    q = np.array([qx, qy, qz, w])
    return q / np.linalg.norm(q)


def q_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def frame_along(axis, side_hint):
    """조각의 y 축 = axis, x 축 = side_hint 를 axis 에 수직으로 맞춘 것 → 사원수"""
    y = unit(axis)
    x = np.asarray(side_hint, float) - y * float(np.dot(side_hint, y))
    if np.linalg.norm(x) < 1e-6:
        x = np.cross(y, [0, 0, 1.0]) if abs(y[2]) < 0.9 else np.cross(y, [1.0, 0, 0])
    x = unit(x)
    z = np.cross(x, y)
    return q_from_basis(x, y, z)


# 꼭짓점으로 세움: 정육면체의 대각선 (1, 1, 1) → +Y (만년빙하 TIP_UP 과 같음)
def _tip_up():
    a = unit([1.0, 1.0, 1.0])
    b = np.array([0.0, 1.0, 0.0])
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * (1 / (1 + c))


TIP_UP = _tip_up()


class Shape:
    def __init__(self, name, grip, credit):
        self.name = name
        self.grip = list(grip)
        self.credit = credit
        self.parts = []

    def box(self, m, c, q, s):
        """상자 조각: 가운데 c · 회전 q (조각 축) · 크기 s (가로 x · 세로 y · 깊이 z, 모델 픽셀)"""
        self.parts.append({"m": m, "c": [round(float(v), 3) for v in c], "q": [round(float(v), 5) for v in q], "s": [round(float(v), 3) for v in s]})

    def tip(self, m, base, axis, length, width, spin=0.0, bury=0.0):
        """결정 조각 (꼭짓점으로 세운 정육면체 — 끝에서 끝 length · 가장 굵은 지름 width): base 에서 axis 쪽으로 (bury = 밑에 묻히는 몫 0 ~ 0.5)"""
        ax = unit(axis)
        c = np.asarray(base, float) + ax * length * (0.5 - bury)
        side = np.cross(ax, [0, 0, 1.0]) if abs(ax[2]) < 0.9 else np.cross(ax, [1.0, 0, 0])
        ang = math.radians(spin)
        side = unit(side) * math.cos(ang) + np.cross(ax, unit(side)) * math.sin(ang)
        q = frame_along(ax, side)
        self.parts.append({"m": m, "c": [round(float(v), 3) for v in c], "q": [round(float(v), 5) for v in q], "s": [round(float(width), 3),
                           round(float(length), 3), round(float(width), 3)], "tip": True})

    def json(self):
        return {"grip": self.grip, "credit": self.credit, "parts": self.parts}


def part_matrix(p):
    """조각 꼭짓점 (정육면체 −0.5 ~ 0.5) → 모델 픽셀 (가운데 기준): A = Lr · S · Rr"""
    Lr = q_mat(p["q"])
    if p.get("tip"):
        w, h = p["s"][0], p["s"][1]
        S = np.diag([(w / 2) / WIDE, h / DIAG, (w / 2) / WIDE])
        return Lr @ S @ TIP_UP
    return Lr @ np.diag(p["s"])


# ------------------------------------------------------------------ 조각 재질 (정육면체 모델 + 32 × 32 그림, 8 프레임 흐름)

def tex_water(lo, hi, alpha, streak="#E6F6FF", s_alpha=0.75, n_streak=5, dark_edge=None, seed=1, flat=False):
    LO, HI, ST = C(lo), C(hi), C(streak)

    def gen(frame):
        rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:TEX, 0:TEX]
        t = np.full((TEX, TEX), 0.5) if flat else 1.0 - ys / (TEX - 1)
        img = LO * (1 - t[..., None]) + HI * t[..., None]
        a = np.full((TEX, TEX), alpha)
        ph = frame / FRAMES * math.pi * 2
        # 부드러운 물결 (밝기만)
        wv = 0.06 * np.sin(xs * 0.4 + ys * 0.25 + ph) + 0.04 * np.sin(xs * 0.17 - ys * 0.33 - ph)
        img = img * (1 + wv[..., None])
        # 흐르는 흰 줄 (사선 물결 — 프레임마다 위로)
        for k in range(n_streak):
            y0 = rng.uniform(0, TEX)
            amp = rng.uniform(1.0, 2.5)
            fq = rng.uniform(0.18, 0.35)
            p0 = rng.uniform(0, 6.3)
            x0 = int(rng.integers(0, TEX))
            L = int(rng.integers(TEX // 3, TEX))
            for i in range(L):
                x = (x0 + i) % TEX
                y = y0 + amp * math.sin(x * fq + p0) - frame * TEX / FRAMES
                yi = int(math.floor(y)) % TEX
                img[yi, x] = img[yi, x] * (1 - s_alpha) + ST * s_alpha
                a[yi, x] = max(a[yi, x], min(1.0, alpha + 0.25))
        # 반짝임
        r2 = np.random.default_rng(seed + 7)
        for i in range(5):
            x, y = int(r2.integers(0, TEX)), int(r2.integers(0, TEX))
            if math.sin(frame / FRAMES * math.pi * 2 + i * 1.7) > 0.7:
                img[y, x] = ST
                a[y, x] = 1.0
        if dark_edge is not None:
            E = C(dark_edge)
            e = (xs == 0) | (ys == 0) | (xs == TEX - 1) | (ys == TEX - 1)
            img[e] = img[e] * 0.4 + E * 0.6
        return np.concatenate([np.clip(img, 0, 1), a[..., None]], -1)
    return gen


def tex_foam(alpha=0.94, seed=3):
    W_, B_ = C("#F6FCFF"), C("#C6E7FB")

    def gen(frame):
        rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:TEX, 0:TEX]
        img = np.tile(W_, (TEX, TEX, 1)).astype(float)
        a = np.full((TEX, TEX), alpha)
        for i in range(14):
            cx = (rng.uniform(0, TEX) + frame * 0.5) % TEX
            cy = (rng.uniform(0, TEX) - frame * 0.8) % TEX
            r = rng.uniform(1.0, 2.8)
            d = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2)
            ring = np.abs(d - r) < 0.55
            img[ring] = img[ring] * 0.5 + B_ * 0.5
        return np.concatenate([img, a[..., None]], -1)
    return gen


def tex_ki(alpha=0.55, rim=0.92, seed=5, bright=1.0):
    """기운 (회색 — 계열 색 물들임): 가운데 옅게 · 테두리 밝게 · 위로 오르는 흰 줄 (프레임마다)"""
    def gen(frame):
        rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:TEX, 0:TEX]
        cx = cy = (TEX - 1) / 2
        r = np.maximum(np.abs(xs - cx), np.abs(ys - cy)) / cx
        v = (0.72 + 0.22 * r) * bright
        a = alpha * (0.6 + 0.4 * r)
        for k in range(4):
            x = int(rng.integers(1, TEX - 1))
            y0 = rng.uniform(0, TEX)
            L = int(rng.integers(4, 10))
            for i in range(L):
                y = int(math.floor(y0 - i - frame * TEX / FRAMES)) % TEX
                v[y, x] = 1.0
                a[y, x] = max(a[y, x], 0.85)
        e = (xs <= 0) | (ys <= 0) | (xs >= TEX - 1) | (ys >= TEX - 1)
        v[e] = 1.0
        a[e] = rim
        img = np.repeat(np.clip(v, 0, 1)[..., None], 3, -1)
        return np.concatenate([img, np.clip(a, 0, 1)[..., None]], -1)
    return gen


def tex_feather(seed=31, glow=False):
    """2026-11-13 깃털 (대천사 강림의 날개 — 회색, 계열 색 물들임): 끝 (위) 이 뾰족하고 아래 깃대 (깃 없음) 로 좁아지는 깃 · 가운데 깃대 (밝게) ·
    바깥 깃이 조금 좁음 · 깃대에서 위 바깥으로 비스듬한 깃가지 줄 · 가장자리 밝은 테 · 깃대를 따라 위로 오르는 빛 (프레임마다). 깃 밖은 투명"""
    def gen(frame):
        ys, xs = np.mgrid[0:TEX, 0:TEX]
        u = 1.0 - (ys + 0.5) / TEX  # 0 아래 ~ 1 위 (끝)
        cx = (TEX - 1) / 2
        dx = xs - cx
        k = np.clip((u - 0.1) / 0.9, 0, 1)
        prof = np.where(k < 0.7, np.sin(np.pi * np.minimum(k, 0.5)) ** 0.5, np.sin(np.pi * 0.5 * np.clip((1 - k) / 0.3, 0, 1)) ** 0.75)  # 넓게 · 끝 30% 에서 뾰족
        hw = np.where(dx < 0, 0.78, 1.0) * (TEX / 2 - 0.6) * prof
        inside = (np.abs(dx) <= hw + 0.35) & (u > 0.06)
        shaft = (np.abs(dx) <= 0.6) & (u > 0.0)
        r = np.where(hw > 0.01, np.abs(dx) / np.maximum(hw, 0.01), 1.0)
        v = 0.97 - 0.2 * r
        # 깃가지 (위 바깥으로 비스듬한 줄)
        barb = ((np.abs(dx) * 0.7 + ys) % 3.2) < 0.9
        v = np.where(barb & inside & ~shaft, v - 0.1, v)
        rim = inside & (np.abs(dx) >= hw - 0.9)
        v = np.where(rim, 1.0, v)
        v = np.where(shaft, 1.0, v)
        a = np.where(inside, 0.86 if not glow else 0.9, 0.0)
        a = np.where(rim, 0.95, a)
        a = np.where(shaft, 1.0, a)
        # 깃대를 따라 오르는 빛 (프레임마다)
        band = (u * 8 - frame / FRAMES * 8) % 8
        lit = (band < 1.2) & inside
        v = np.where(lit, np.minimum(1.0, v + 0.12), v)
        if glow:
            v = np.minimum(1.0, v + 0.06)
        img = np.repeat(np.clip(v, 0, 1)[..., None], 3, -1)
        return np.concatenate([img, np.clip(a, 0, 1)[..., None]], -1)
    return gen


# 이름: (그림, 물들임 번호 또는 None, 빛)
SHARDS = {
    "water": (tex_water("#1E62C8", "#5DB4FA", 0.8, s_alpha=0.45, n_streak=3, seed=11, flat=True), None, True),
    "deep": (tex_water("#0A3488", "#1C5DC0", 0.88, s_alpha=0.3, n_streak=2, seed=12, flat=True), None, True),
    "light": (tex_water("#7CCBFF", "#D6F2FF", 0.72, streak="#FFFFFF", s_alpha=0.5, n_streak=3, seed=13, flat=True), None, True),
    "glass": (tex_water("#2A7FE6", "#9AD6FF", 0.42, streak="#FFFFFF", s_alpha=0.8, n_streak=4, seed=14), None, True),
    "foam": (tex_foam(0.86), None, True),
    "ki": (tex_ki(0.8, 0.9, seed=21), 0, True),
    "ki_core": (tex_ki(0.85, 1.0, seed=23, bright=1.0), 1, True),
    "feather": (tex_feather(31), 0, True),
    "feather_glow": (tex_feather(37, glow=True), 1, True),
}


def shard_files():
    files = {}
    for name, (gen, tint, glow) in SHARDS.items():
        frames = [gen(f) for f in range(FRAMES)]
        sheet = np.concatenate(frames, 0)
        img = Image.fromarray((np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        tid = "rpg:item/vfx/shard_" + name
        face = {"uv": [0, 0, 16, 16], "texture": "#0"}
        if tint is not None:
            face["tintindex"] = tint
        el = {"name": name, "from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}
        if glow:
            el["light_emission"] = 15
        model = {"credit": "tools/vfx_shapes.py — 조각 " + name, "textures": {"0": tid, "particle": tid}, "elements": [el]}
        item = {"type": "minecraft:model", "model": "rpg:item/vfx/shard_" + name}
        if tint is not None:
            item["tints"] = [{"type": "minecraft:custom_model_data", "index": i, "default": 16777215} for i in range(tint + 1)]
        files[os.path.join(ASSETS, "textures", "item", "vfx", "shard_" + name + ".png")] = buf.getvalue()
        files[os.path.join(ASSETS, "textures", "item", "vfx", "shard_" + name + ".png.mcmeta")] = (json.dumps(
            {"animation": {"frametime": FRAMETIME, "interpolate": True}}, indent=2) + "\n").encode()
        files[os.path.join(ASSETS, "models", "item", "vfx", "shard_" + name + ".json")] = (json.dumps(model, ensure_ascii=False, indent=1) + "\n").encode()
        files[os.path.join(ASSETS, "items", "vfx", "shard_" + name + ".json")] = (json.dumps({"model": item}, indent=2) + "\n").encode()
    return files


# ------------------------------------------------------------------ 물체

def wave():
    """파도: 쥔 곳 (8, 0, 1.5) · 너비 x −7 ~ 23 · 높이 18 · 앞 (말린 물마루) = +Z. 쓴 사람은 뒤에서 봄 — 곡선을 따라 기울인 물 판 (온 너비 한 장 + 엇갈린 좁은 판
    하나 — 칸 무늬 없이 깎은 면처럼), 아래는 짙고 위로 갈수록 밝고 옅게, 물마루 · 입술에 뾰족한 거품 결정 (만년빙하처럼), 밑의 거품"""
    sh = Shape("wave", (8, 0, 1.5), "파도 (2026-11-12 만년빙하처럼 2판 — 곡선을 따라 기울인 물 판 · 엇갈린 면 · 뾰족한 거품 결정)")
    curve = [(-14.0, 0.0), (-8.5, 2.6), (-3.5, 6.6), (0.5, 11.0), (3.8, 14.8), (7.4, 17.2), (10.8, 17.4), (13.6, 15.4), (15.0, 12.2), (14.4, 8.8), (12.6, 6.6)]
    mats = ["deep", "deep", "water", "water", "water", "light", "light", "light", "light", "foam"]
    rng = np.random.default_rng(11)
    pts = [np.array([0.0, y, z]) for (z, y) in curve]
    X0, X1 = -7.0, 23.0

    def slab(m, xc, w, a, b, thick, roll, tilt, lift=0.0):
        t = b - a
        zt = unit(t)
        xa = np.array([1.0, 0, 0])
        ya = np.cross(zt, xa)
        rl, tl = math.radians(roll), math.radians(tilt)
        xa2 = unit(xa * math.cos(rl) + ya * math.sin(rl))
        ya2 = unit(np.cross(zt, xa2))
        zt2 = unit(zt * math.cos(tl) + ya2 * math.sin(tl))
        ya2 = unit(np.cross(zt2, xa2))
        xa2 = unit(np.cross(ya2, zt2))
        mid = (a + b) / 2 + ya2 * lift
        sh.box(m, (xc, mid[1], mid[2]), q_from_basis(xa2, ya2, zt2), (w, thick, float(np.linalg.norm(t)) + 1.6))
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        thick = 2.6 if i < 5 else 2.2
        slab(mats[i], 8.0, X1 - X0 + 1.0, a, b, thick, rng.uniform(-2, 2), rng.uniform(-2, 2))
        # 엇갈린 좁은 면 (조금 바깥 — 깎은 면처럼)
        if i < 8:
            w = rng.uniform(8, 14)
            xc = rng.uniform(X0 + w / 2, X1 - w / 2)
            slab(mats[i] if i > 1 else "water", xc, w, a, b, thick * 0.7, rng.uniform(-7, 7), rng.uniform(-5, 5), lift=0.7)
        if i < 5:
            ya = np.cross(unit(b - a), [1.0, 0, 0])
            inner = (a + b) / 2 - ya * (thick * 0.5 + 2.2)
            sh.box("deep", (8.0, max(1.6, inner[1]), inner[2]), q_from_basis(np.array([1.0, 0, 0]), ya, unit(b - a)), (X1 - X0 - 0.6, 4.4,
                   float(np.linalg.norm(b - a)) * 0.92))
    # 물마루 · 입술의 뾰족한 거품 결정 (위 · 앞으로, 크고 작게)
    for k in range(14):
        px = X0 + 1.0 + (X1 - X0 - 2.0) * (k + rng.uniform(0.1, 0.9)) / 14
        i = int(rng.integers(4, 8))
        u = rng.uniform(0, 1)
        base = pts[i] * (1 - u) + pts[i + 1] * u
        nrm = unit(np.cross(unit(pts[i + 1] - pts[i]), [1.0, 0, 0]))
        ax = unit(nrm * 0.8 + np.array([0, 0.5, 0.5]) + np.array([rng.uniform(-0.25, 0.25), 0, 0]))
        sh.tip("foam" if k % 3 else "light", (px, base[1], base[2]), ax, rng.uniform(3.5, 7.0), rng.uniform(1.4, 2.4), spin=rng.uniform(0, 90), bury=0.3)
    # 밑의 물 · 거품 (앞으로 퍼짐) · 작은 물보라
    sh.box("water", (8.0, 0.9, 11.0), np.array([0, 0, 0, 1.0]), (30.0, 1.8, 10.0))
    sh.box("foam", (8.0, 1.9, 13.0), np.array([0, 0, 0, 1.0]), (28.5, 0.6, 9.0))
    for k in range(6):
        px = -5 + k * 5.4 + rng.uniform(-1, 1)
        sh.tip("foam", (px, 1.6, 16.5 + rng.uniform(-1, 1)), unit([rng.uniform(-0.3, 0.3), 1.0, rng.uniform(0.4, 1.0)]), rng.uniform(2.5, 4.0), 1.4,
               spin=rng.uniform(0, 90), bury=0.3)
    return sh


def water_column():
    """물기둥 (물 회오리): 쥔 곳 (8, 0, 7.5) · 높이 28 — 만년빙하처럼 기울인 반투명 조각: 가운데 물 결정 기둥 둘 + 나선으로 감아 오르는 긴 물 띠
    (조각마다 소용돌이 쪽으로 35° 기울임 — 밑은 좁게 · 위는 넓게) · 밑 거품 · 꼭대기 물보라 (밖 · 위로 뾰족하게). 서버가 기둥 전체를 돌림"""
    sh = Shape("water_column", (8, 0, 7.5), "물기둥 (2026-11-12 만년빙하처럼 — 물 결정 기둥 · 나선 물 띠 · 물보라 결정)")
    cx, cz = 8.0, 7.5
    sh.tip("deep", (cx, 0.0, cz), (0, 1.0, 0), 34.0, 7.2, spin=0, bury=0.22)
    sh.tip("light", (cx, 0.0, cz), unit([0.03, 1.0, 0.02]), 37.0, 3.6, spin=30, bury=0.2)
    # 나선 물 띠 (세 줄 · 줄마다 일곱 조각)
    n_band, n_seg = 3, 7
    for band in range(n_band):
        a0 = band * 2 * math.pi / n_band
        for k in range(n_seg):
            ym = 2.0 + k * 3.5
            ang = a0 + ym * 0.24
            r = 3.4 + 2.9 * (ym / 24.0)
            pos = np.array([cx + math.cos(ang) * r, ym, cz + math.sin(ang) * r])
            tang = np.array([-math.sin(ang), 0.0, math.cos(ang)])
            outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
            along = unit(tang * math.cos(math.radians(35)) + np.array([0, 1.0, 0]) * math.sin(math.radians(35)))
            xa = unit(np.cross(along, outw))
            m = ("water", "light", "glass")[(band + k) % 3]
            sh.box(m, pos, q_from_basis(xa, along, unit(np.cross(xa, along))), (2.6 + 0.08 * ym, 8.0, 1.0))
    # 밑 거품 (납작한 결정 다섯 — 밖으로 퍼짐)
    for k in range(5):
        ang = k * 2 * math.pi / 5 + 0.4
        outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
        sh.tip("foam", np.array([cx, 0.0, cz]) + outw * 4.4, unit(np.array([0.0, 0.3, 0.0]) + outw), 7.5, 2.8, spin=20, bury=0.45)
    # 꼭대기 물보라 (밖 · 위로 뾰족하게)
    for k in range(9):
        ang = k * 2 * math.pi / 9 + 0.2
        outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
        base = np.array([cx, 24.0, cz]) + outw * 5.4
        ax = unit(np.array([0.0, 1.0, 0.0]) + outw * (0.55 + 0.3 * (k % 2)))
        sh.tip("foam" if k % 3 == 0 else "light", base, ax, 6.0 + 2.0 * (k % 3), 2.4, spin=k * 20, bury=0.3)
    return sh


def palm():
    """기의 손바닥 (붕권): 쥔 곳 (8, 5, 8) · 앞 (장풍을 미는 면) = +Z · 손가락 = +Y. 손바닥 (팔각) · 손목 · 세 마디 손가락 넷 (조금 벌림 · 뒤로 젖힘) ·
    엄지 — 조각마다 반투명 기운 (계열 색) + 겉의 옅은 기운 껍질, 손바닥 가운데 빛 (속빛 — 앞 · 뒤)"""
    sh = Shape("palm", (8, 5, 8), "기의 손바닥 (2026-11-12 만년빙하처럼 — 기울인 반투명 기운 조각 · 겉 껍질 · 가운데 빛)")
    I = np.array([0, 0, 0, 1.0])

    def with_aura(m, c, q, s, k=1.22):
        sh.box(m, c, q, s)

    def rot_z(deg):
        a = math.radians(deg)
        return np.array([0, 0, math.sin(a / 2), math.cos(a / 2)])
    # 손바닥 (네모 + 45° 네모 = 팔각) · 손목
    with_aura("ki", (8, 5, 8), I, (10.0, 10.4, 2.6))
    sh.box("ki", (8, 5, 8), rot_z(45), (8.2, 8.2, 2.4))
    with_aura("ki", (8, -2.2, 8), I, (6.6, 5.6, 2.2))
    # 손가락 넷 (세 마디 — 마디마다 조금 더 뒤로 젖힘 · 벌림)
    fingers = ((4.2, 3.0, 2.6, 2.0, 14.0), (6.9, 3.6, 3.0, 2.3, 5.0), (9.5, 3.5, 2.9, 2.2, -4.0), (12.0, 2.8, 2.3, 1.8, -13.0))
    for (x, l1, l2, l3, fan) in fingers:
        p = np.array([x, 10.0, 8.0])
        bend = 0.0
        for j, L in enumerate((l1, l2, l3)):
            fa, ba = math.radians(fan), math.radians(bend)
            d = unit([-math.sin(fa), math.cos(fa) * math.cos(ba), -math.cos(fa) * math.sin(ba)])
            side = np.array([math.cos(fa), math.sin(fa), 0.0])
            q = q_from_basis(unit(side - d * float(np.dot(side, d))), d, unit(np.cross(unit(side - d * float(np.dot(side, d))), d)))
            w = 2.2 - 0.25 * j
            with_aura("ki", p + d * L / 2, q, (w, L + 0.5, 2.0 - 0.2 * j), k=1.25)
            p = p + d * L
            bend += 8.0
        sh.tip("ki", p - d * 0.4, d, 1.8, 1.7, bury=0.0)
    # 엄지 (옆 · 위로 50°)
    p = np.array([3.4, 3.2, 8.0])
    for j, L in enumerate((3.8, 3.0)):
        a = math.radians(52 - 14 * j)
        d = unit([-math.sin(a), math.cos(a), 0.0])
        side = np.array([math.cos(a), math.sin(a), 0.0])
        q = q_from_basis(side, d, np.cross(side, d))
        with_aura("ki", p + d * L / 2, q, (2.3 - 0.2 * j, L + 0.4, 2.0), k=1.25)
        p = p + d * L
    # 손바닥 가운데 빛 (앞 · 뒤 — 쓴 사람은 손등 쪽을 봄)
    for zc in (9.45, 6.55):
        sh.box("ki_core", (8, 5, zc), I, (5.6, 5.6, 0.25))
        sh.box("ki_core", (8, 5, zc), rot_z(45), (4.6, 4.6, 0.22))
    return sh


def angel_wing():
    """2026-11-13 대천사의 날개 (오른쪽 — 왼쪽은 서버가 세로축 둘레로 뒤집어 씀): 쥔 곳 = 날개 뿌리 (0, 0, 0) · 바깥 = +Z · 위 = +Y · 두께 = X.
    앞 가장자리 (뼈 — 위 바깥으로 솟았다가 끝이 아래로 휨) 의 빛 깃 · 바깥 끝의 긴 첫째 깃 다섯 (아래 바깥으로 펼침) · 안쪽 둘째 깃 다섯 (아래로) ·
    덮깃 다섯 (짧게 — 겹쳐서). 깃마다 계열 색 (빛 깃은 속빛)"""
    sh = Shape("angel_wing", (0, 0, 0), "대천사의 날개 (2026-11-13 — 깃털 조각 스물넷 · 뼈의 빛 깃 · 첫째 · 둘째 깃 · 덮깃)")
    N = np.array([1.0, 0, 0])
    BONE = ((72, 9.0), (40, 10.0), (8, 9.0), (-28, 8.0))

    def d_of(deg):
        a = math.radians(deg)
        return np.array([0.0, math.sin(a), math.cos(a)])

    def feather(m, base, deg, L, w, t=0.4, x=0.0):
        d = d_of(deg)
        xa = unit(np.cross(d, N))
        q = q_from_basis(xa, d, unit(np.cross(xa, d)))
        c = np.array([x, base[1], base[2]]) + d * (L / 2)
        sh.box(m, c, q, (w, L, t))
    # 뼈 (z, y)
    bone = [(0.0, 0.0)]
    for deg, L in BONE:
        z, y = bone[-1]
        a = math.radians(deg)
        bone.append((z + L * math.cos(a), y + L * math.sin(a)))

    def on_bone(i, u):
        (z0, y0), (z1, y1) = bone[i], bone[i + 1]
        return (z0 + (z1 - z0) * u, y0 + (y1 - y0) * u)

    def pt(zy):
        return np.array([0.0, zy[1], zy[0]])
    # 첫째 깃 (바깥 — 길게, 아래 바깥으로 펼침)
    for (i, u, deg, L, x) in ((3, 0.95, -20, 24.0, 0.3), (3, 0.5, -34, 25.0, -0.3), (2, 0.98, -47, 24.5, 0.3), (2, 0.66, -58, 23.5, -0.3),
                              (2, 0.33, -68, 22.0, 0.3)):
        feather("feather", pt(on_bone(i, u)), deg, L, 6.0, x=x)
    # 둘째 깃 (안쪽 — 아래로, 넓게 겹침)
    for (i, u, deg, L, x) in ((2, 0.02, -77, 21.0, -0.3), (1, 0.7, -84, 20.0, 0.3), (1, 0.4, -90, 19.0, -0.3), (1, 0.1, -96, 17.5, 0.3),
                              (0, 0.65, -103, 14.5, -0.3), (0, 0.3, -112, 10.0, 0.3)):
        feather("feather", pt(on_bone(i, u)), deg, L, 6.6, x=x)
    # 덮깃 (짧게 — 앞)
    for (i, u, deg, L) in ((3, 0.3, -40, 11.0), (2, 0.7, -57, 11.5), (2, 0.25, -72, 11.5), (1, 0.6, -86, 10.5), (1, 0.15, -98, 9.0)):
        feather("feather", pt(on_bone(i, u)) + np.array([0, -0.5, 0]), deg, L, 6.2, t=0.35, x=0.75)
    # 작은 덮깃 (뼈 가까이 — 뒤)
    for (i, u, deg, L) in ((3, 0.4, -42, 6.5), (2, 0.55, -64, 7.0), (1, 0.75, -82, 6.5), (1, 0.25, -94, 6.0)):
        feather("feather_glow", pt(on_bone(i, u)) + np.array([0, -0.3, 0]), deg, L, 5.6, t=0.35, x=-0.75)
    # 앞 가장자리 빛 깃 (뼈를 따라 — 바깥으로 누운 깃)
    for i, (deg, L) in enumerate(BONE):
        z0, y0 = bone[i]
        d = d_of(deg)
        feather("feather_glow", np.array([0.0, y0, z0]) - d * 1.0, deg, L + 2.0, 3.6, t=1.1)
    return sh


def water_lance():
    """2026-11-13 물의 창 (해류 일반 — 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 쥔 곳 = 밑 가운데 (8, 0, 8) · 촉 +Y (끝 y 약 50) —
    만년빙하처럼 기울인 반투명 조각: 짙은 물 결정 대 (굵게) 를 밝은 물 결정이 감싸고, 나선으로 감아 오르는 물 띠 셋 (촉 쪽으로 가늘어짐) ·
    뾰족한 촉 (유리 결정 + 밝은 속 결정) · 촉 아래 물마루 (뒤로 젖혀진 거품 결정 넷) · 꼬리의 물보라 (뒤 · 바깥으로)"""
    sh = Shape("water_lance", (8, 0, 8), "물의 창 (2026-11-13 — 물 결정 대 · 나선 물 띠 · 뾰족한 유리 촉 · 물마루 · 꼬리 물보라)")
    cx, cz = 8.0, 8.0
    sh.tip("deep", (cx, 2.0, cz), (0, 1.0, 0), 40.0, 4.6, spin=0, bury=0.0)
    sh.tip("light", (cx, 0.0, cz), (0, 1.0, 0), 44.0, 2.4, spin=45, bury=0.0)
    # 나선 물 띠 (셋 · 띠마다 다섯 조각 — 촉 쪽으로 반지름 · 굵기가 줄어듦)
    for band in range(3):
        a0 = band * 2 * math.pi / 3
        for k in range(5):
            ym = 6.0 + k * 6.4
            ang = a0 + ym * 0.21
            r = 3.6 - 2.0 * (ym / 34.0)
            pos = np.array([cx + math.cos(ang) * r, ym, cz + math.sin(ang) * r])
            tang = np.array([-math.sin(ang), 0.0, math.cos(ang)])
            outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
            along = unit(tang * math.cos(math.radians(55)) + np.array([0, 1.0, 0]) * math.sin(math.radians(55)))
            xa = unit(np.cross(along, outw))
            m = ("water", "light", "glass")[(band + k) % 3]
            sh.box(m, pos, q_from_basis(xa, along, unit(np.cross(xa, along))), (1.8 - 0.18 * k, 7.0, 0.8))
    # 촉 (유리 결정 + 밝은 속) · 촉 아래 물마루 (뒤로 젖혀진 거품 결정 넷)
    sh.tip("glass", (cx, 36.0, cz), (0, 1.0, 0), 16.0, 6.4, spin=10, bury=0.0)
    sh.tip("light", (cx, 37.5, cz), (0, 1.0, 0), 13.0, 3.2, spin=55, bury=0.0)
    for k in range(4):
        ang = k * math.pi / 2 + 0.4
        outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
        sh.tip("foam", np.array([cx, 37.0, cz]) + outw * 2.2, unit(np.array([0.0, -1.0, 0.0]) + outw * 0.9), 6.5, 2.2, spin=k * 25, bury=0.15)
    # 꼬리 물보라 (뒤 · 바깥으로 — 날아가며 끄는 물)
    for k in range(6):
        ang = k * 2 * math.pi / 6 + 0.25
        outw = np.array([math.cos(ang), 0.0, math.sin(ang)])
        sh.tip("foam" if k % 2 == 0 else "water", np.array([cx, 1.5, cz]) + outw * 1.6, unit(np.array([0.0, -1.0, 0.0]) + outw * 0.7), 7.0 + 1.5 * (k % 2), 2.0,
               spin=k * 30, bury=0.2)
    return sh


BUILD = {"wave": wave, "water_column": water_column, "palm": palm, "angel_wing": angel_wing, "water_lance": water_lance}


# ------------------------------------------------------------------ 미리보기 (광선 추적 — 조각마다 아핀 변환)

L0 = unit([0.2, 1.0, -0.7])
L1 = unit([-0.2, 1.0, 0.7])


def render(shape, texs, tints, view, W=380, H=380, ss=2, bg=(92, 98, 112), half=None, center=None):
    parts = shape["parts"]
    D = -unit(view)
    right = np.cross(D, [0, 1, 0])
    right = unit(right) if np.linalg.norm(right) > 1e-6 else np.array([1.0, 0, 0])
    up = np.cross(right, D)
    mats = [part_matrix(p) for p in parts]
    cs = [np.asarray(p["c"], float) for p in parts]
    corners = []
    for A, c in zip(mats, cs):
        for i in range(8):
            v = np.array([(i & 1) - 0.5, ((i >> 1) & 1) - 0.5, ((i >> 2) & 1) - 0.5])
            corners.append(c + A @ v)
    corners = np.array(corners)
    ctr = (corners.min(0) + corners.max(0)) / 2 if center is None else np.asarray(center, float)
    if half is None:
        rel = corners - ctr
        half = max(np.abs(rel @ right).max(), np.abs(rel @ up).max()) * 1.1
    Wn, Hn = W * ss, H * ss
    xs = ((np.arange(Wn) + 0.5) / Wn * 2 - 1) * half * (W / H)
    ys = (1 - (np.arange(Hn) + 0.5) / Hn * 2) * half
    gx, gy = np.meshgrid(xs, ys)
    P = (ctr[None, None, :] + gx[..., None] * right + gy[..., None] * up - D * 1000.0).reshape(-1, 3)
    N = len(P)
    R_, T_, Cc = [], [], []
    for p, A, c in zip(parts, mats, cs):
        Ai = np.linalg.inv(A)
        lp = (Ai @ (P - c).T).T
        ld = Ai @ D
        n = N
        tmin = np.full(n, -np.inf)
        tmax = np.full(n, np.inf)
        ea = np.full(n, -1)
        em = np.zeros(n, bool)
        ok = np.ones(n, bool)
        for i in range(3):
            if abs(ld[i]) < 1e-12:
                ok &= (lp[:, i] >= -0.5) & (lp[:, i] <= 0.5)
                continue
            t1 = (-0.5 - lp[:, i]) / ld[i]
            t2 = (0.5 - lp[:, i]) / ld[i]
            lo = np.minimum(t1, t2)
            hi = np.maximum(t1, t2)
            u = lo > tmin
            tmin = np.where(u, lo, tmin)
            ea = np.where(u, i, ea)
            em = np.where(u, ld[i] > 0, em)
            tmax = np.minimum(tmax, hi)
        ok &= (tmin <= tmax) & (tmax > 0) & (ea >= 0)
        if not ok.any():
            continue
        hit = np.nonzero(ok)[0]
        Hh = lp[hit] + np.outer(tmin[hit], ld) + 0.5  # 0 ~ 1
        tex = texs[p["m"]]
        th, tw = tex.shape[:2]
        for name in ("north", "south", "east", "west", "up", "down"):
            ax, sign = jv.FACE_DIR[name]
            msk = (ea[hit] == ax) & (em[hit] == (sign < 0))
            if not msk.any():
                continue
            a, b = jv.face_ab(name, Hh[msk] * 16, np.zeros(3), np.full(3, 16.0))
            px = np.clip((np.clip(a, 0, 0.9999) * tw).astype(int), 0, tw - 1)
            py = np.clip((np.clip(b, 0, 0.9999) * th).astype(int), 0, th - 1)
            col = tex[py, px].astype(float).copy()
            tint = SHARDS[p["m"]][1]
            if tint is not None:
                col[:, :3] *= np.asarray(tints[min(tint, len(tints) - 1)])
            nl = np.zeros(3)
            nl[ax] = sign
            nw = unit(Ai.T @ nl)
            k = min(1.0, (max(0.0, float(nw @ L0)) + max(0.0, float(nw @ L1))) * 0.6 + 0.4)
            col[:, :3] *= k
            R_.append(hit[msk])
            T_.append(tmin[hit[msk]])
            Cc.append(col)
    out = np.zeros((N, 3))
    remain = np.ones(N)
    if R_:
        R = np.concatenate(R_)
        T = np.concatenate(T_)
        Ccat = np.concatenate(Cc)
        order = np.lexsort((T, R))
        R, T, Ccat = R[order], T[order], Ccat[order]
        first = np.r_[True, R[1:] != R[:-1]]
        start = np.maximum.accumulate(np.where(first, np.arange(len(R)), 0))
        rank = np.arange(len(R)) - start
        for kk in range(int(rank.max()) + 1):
            sel = rank == kk
            r = R[sel]
            col = Ccat[sel]
            a = col[:, 3] / 255.0
            a = np.where(a < 0.1, 0.0, a)
            w = remain[r] * a
            out[r] += w[:, None] * col[:, :3]
            remain[r] *= 1 - a
    img = out + remain[:, None] * np.asarray(bg, float)[None, :]
    im = Image.fromarray(np.clip(img.reshape(Hn, Wn, 3), 0, 255).astype(np.uint8), "RGB")
    return im.resize((W, H), Image.LANCZOS) if ss > 1 else im


TINTS = {"palm": [(1.0, 0.78, 0.3), (1.0, 0.95, 0.75)], "angel_wing": [(1.0, 0.9, 0.62), (1.0, 1.0, 1.0)]}
VIEWS = {"wave": [(0.6, 0.45, -1.0), (1.0, 0.15, 0.05), (-0.5, 0.35, 1.0)], "water_column": [(1.0, 0.35, -0.7), (0.2, 1.0, -0.2), (-0.8, 0.1, 0.6)],
         "palm": [(0.25, 0.25, -1.0), (0.3, 0.2, 1.0), (1.0, 0.25, 0.1)], "angel_wing": [(1.0, 0.1, 0.0), (1.0, 0.3, 0.6), (-1.0, 0.2, 0.3)],
         "water_lance": [(1.0, 0.2, 0.0), (1.0, 0.6, 0.6), (0.2, -0.3, 1.0)]}


def textures_frame0():
    out = {}
    for name, (gen, tint, glow) in SHARDS.items():
        out[name] = (np.clip(gen(0), 0, 1) * 255 + 0.5).astype(np.uint8)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()
    shapes = {n: f().json() for n, f in BUILD.items()}
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        texs = textures_frame0()
        for n in (a.names or list(shapes)):
            ims = [render(shapes[n], texs, TINTS.get(n, [(1, 1, 1), (1, 1, 1)]), v) for v in VIEWS.get(n, [(1, 0.4, -0.7)])]
            s = Image.new("RGB", (sum(i.size[0] for i in ims) + 8 * (len(ims) - 1), ims[0].size[1]), (30, 32, 38))
            x = 0
            for i in ims:
                s.paste(i, (x, 0))
                x += i.size[0] + 8
            p = os.path.join(a.preview, n + ".png")
            s.save(p)
            print(p, len(shapes[n]["parts"]), "조각")
        return
    files = shard_files()
    files[OUT_JSON] = (json.dumps(shapes, ensure_ascii=False, indent=1) + "\n").encode()
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
            print("vfx_shapes: 다시 만들어야 함:", ", ".join(stale))
            sys.exit(1)
        print("vfx_shapes: ok")
    else:
        for n, s in shapes.items():
            print("vfx_shapes: %-12s %3d 조각" % (n, len(s["parts"])))
        print("vfx_shapes: 씀", len(stale))


if __name__ == "__main__":
    main()
