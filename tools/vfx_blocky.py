#!/usr/bin/env python3
"""2026-11-12 룬 3D 물체를 예시 사진처럼 (사용자 "잠만 픽셀 연출 그만해봐 다시 원래껄로 되돌리자 … 그냥 3d모델링만 내가 보낸 저 사진처럼 해줘").

예시 사진의 3D (솟는 바위 · 흰 돌 벽 · 불 폭발 덩어리 · 어두운 수정 상자) = 큰 상자 몇 개 + 면마다 그린 픽셀 그림 (블록과 같은 밀도 —
돌 하나하나의 어두운 테두리 · 밝은 윗모서리 · 어두운 아랫모서리, 마인크래프트 블록 · Blockbench 모델 같은 모습).
tools/vfx_props.py 의 물체 (작은 단색 복셀 덩어리 · 잡음 견본) 를 같은 이름 · 같은 치수 · 같은 쥔 곳 · 같은 물들임 번호로 대신함 (rune.RuneProps 그대로).

  python3 tools/vfx_blocky.py                          # 씀 (assets/rpg/models/item/vfx/<이름>.json · textures/item/vfx/<이름>.png (+ .mcmeta) · items/vfx/<이름>.json)
  python3 tools/vfx_blocky.py --check                  # 쓸 것이 없는지
  python3 tools/vfx_blocky.py --preview <폴더> [이름…]  # 예전 (지금 파일) · 새 모습 미리보기 (게임과 같은 면 UV · 빛 방향)

만드는 법
- 상자 (Part) 마다 면 크기 × 밀도 (d — 모델 1 픽셀당 텍셀, 게임에서 보이는 크기에 맞춰 1칸 ≈ 16 텍셀) 의 그림을 그려 (painter) 한 장에 모음 (atlas).
- 그림 = 돌 (보로노이 돌 · 테두리 · 밝은 · 어두운 모서리 · 이끼), 얼음 (얼룩 · 흰 금 · 모서리 빛), 마그마 (어두운 돌 + 빛나는 틈),
  구름 (흰 윗면 · 아래로 짙어지는 띠 — 회색이라 서버가 계열 색으로 물들임) …
- 빛 겹 (overlay): 그림이 겹 그림을 함께 내면 같은 상자를 0.08 픽셀 키운 상자를 하나 더 — 그 면은 물들임 (tintindex) · 빛 (light_emission 15).
  돌 틈의 빛 (계열 색) 이 돌 테두리를 따라감.
- 빛나는 재질은 6 프레임 맥동 (.png.mcmeta — 그림 전체가 프레임마다 이어 붙음).
"""
import argparse
import io
import json
import math
import os
import sys
import zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_model_view as jv  # noqa: E402

FRAMES = 6
FRAMETIME = 2
SIDES = ("north", "south", "east", "west")


def C(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def pulse(frame):
    return 0.5 + 0.5 * math.sin(frame / FRAMES * math.pi * 2)


def seed_of(*parts):
    return zlib.crc32("|".join(str(p) for p in parts).encode()) & 0x7FFFFFFF


# ------------------------------------------------------------------ 보로노이 (돌 · 얼룩 · 틈)

def voronoi(w, h, cell, rng):
    """(돌 번호 h×w, 테두리 거리 h×w — 두 가까운 점 거리 차, 1 아래 = 테두리 한 픽셀, 점 수)"""
    n = max(2, int(round(w * h / cell)))
    pts = np.column_stack([rng.uniform(-1.5, w + 1.5, n), rng.uniform(-1.5, h + 1.5, n)])
    ys, xs = np.mgrid[0:h, 0:w]
    p = np.stack([xs.ravel() + 0.5, ys.ravel() + 0.5], -1)
    cid = np.empty(len(p), int)
    edge = np.empty(len(p))
    step = max(1, int(3_000_000 // max(1, n)))
    for s in range(0, len(p), step):
        q = p[s:s + step]
        d = np.sqrt(((q[:, None, :] - pts[None, :, :]) ** 2).sum(-1))
        two = np.argpartition(d, 1, axis=1)[:, :2]
        dd = np.take_along_axis(d, two, 1)
        o = np.argsort(dd, 1)
        cid[s:s + step] = np.take_along_axis(two, o[:, :1], 1)[:, 0]
        dd.sort(1)
        edge[s:s + step] = dd[:, 1] - dd[:, 0]
    return cid.reshape(h, w), edge.reshape(h, w), n


def shift(m, dy, dx):
    """m 을 (dy, dx) 만큼 민 것 (밖은 거짓) — out[y, x] = m[y - dy, x - dx]"""
    out = np.zeros_like(m)
    h, w = m.shape
    ys = slice(max(0, dy), h + min(0, dy))
    xs = slice(max(0, dx), w + min(0, dx))
    ys0 = slice(max(0, -dy), h + min(0, -dy))
    xs0 = slice(max(0, -dx), w + min(0, -dx))
    out[ys, xs] = m[ys0, xs0]
    return out


def near(m):
    """m 의 네 이웃 (자기 빼고)"""
    return (shift(m, 1, 0) | shift(m, -1, 0) | shift(m, 0, 1) | shift(m, 0, -1)) & ~m


def stones(w, h, cell, rng, tones=(1, 2, 2, 3)):
    """돌 무늬: (단계 h×w — 0 테두리 · 1 어둠 · 2 가운데 · 3 밝음 · 4 빛, 테두리, 돌 번호, 점 수)"""
    cid, edge, n = voronoi(w, h, cell, rng)
    tone = rng.choice(np.array(tones), n)
    t = tone[cid].copy()
    out = edge < 1.0
    hi = (shift(out, 1, 0) | shift(out, 0, 1)) & ~out       # 위 · 왼쪽이 테두리 → 밝은 모서리
    sh = (shift(out, -1, 0) | shift(out, 0, -1)) & ~out & ~hi  # 아래 · 오른쪽 → 어두운 모서리
    t = np.where(hi, np.minimum(t + 1, 4), t)
    t = np.where(sh, np.maximum(t - 1, 1), t)
    t[out] = 0
    return t, out, cid, n


def fault_stones(cid, n, h, w, rng, chains):
    """아래에서 위로 달리는 금 (chains 줄) 이 지나는 돌 번호들"""
    hit = set()
    for _ in range(chains):
        x = rng.uniform(0, w)
        y = h - 0.5
        top = rng.uniform(-0.5, h * 0.45)
        while y > top:
            xi = int(min(w - 1, max(0, x)))
            yi = int(min(h - 1, max(0, y)))
            hit.add(int(cid[yi, xi]))
            y -= 0.7
            x += rng.normal(0, 0.55)
    return hit


def crack_walk(out, rng, k, lo=0.1, hi=0.6, branch=0.5):
    """아래에서 위로 기어오르는 금 k 줄 (돌 테두리를 따라가려 함 — 테두리 칸 4배): h×w 참거짓"""
    h, w = out.shape
    m = np.zeros((h, w), bool)

    def walk(y, x, stop, budget, up=True):
        steps = 0
        while 0 <= y < h and 0 <= x < w and y > stop and steps < budget:
            m[y, x] = True
            cands = ((y - 1, x), (y - 1, x - 1), (y - 1, x + 1), (y, x - 1), (y, x + 1)) if up else \
                    ((y, x - 1), (y, x + 1), (y - 1, x), (y + 1, x), (y - 1, x - 1))
            ws = np.zeros(len(cands))
            for i, (yy, xx) in enumerate(cands):
                if 0 <= yy < h and 0 <= xx < w and not m[yy, xx]:
                    ws[i] = (3.0 if out[yy, xx] else 0.6) * ((1.6 if xx == x else 1.0) if (up and yy < y) else 0.3)
            if ws.sum() <= 0:
                break
            y, x = cands[int(rng.choice(len(cands), p=ws / ws.sum()))]
            steps += 1
        if 0 <= y < h and 0 <= x < w:
            m[y, x] = True

    for _ in range(k):
        x = int(rng.integers(0, w))
        stop = int(h * rng.uniform(lo, hi))
        walk(h - 1, x, stop, h * 4)
        if rng.random() < branch and m.any():
            ys, xs = np.nonzero(m)
            j = int(rng.integers(0, len(ys)))
            walk(int(ys[j]), int(xs[j]), int(ys[j]) - int(h * 0.3) - 1, max(3, h // 3), up=False)
    return m


# ------------------------------------------------------------------ 그림 (painter) — (w, h, 면, rng, 프레임) → (바탕 RGBA h×w×4 0~1, 빛 겹 또는 None)

def cobble(pal, cell=22.0, moss=None, speck=0.05, glow_chains=0, glow_top=1.0):
    """돌 (사진의 솟는 바위): pal = 테두리 · 어둠 · 가운데 · 밝음 · 빛. moss = (어둠 · 가운데 · 밝음) — 윗면 · 옆면 윗줄의 돌을 덮음.
    glow_chains > 0 이면 빛 겹 (금이 지나는 돌의 테두리 = 1, 그 돌 안쪽 테두리 옆 = 0.45 — 맥동)"""
    P = np.stack([C(c) for c in pal])
    M = np.stack([C(c) for c in moss]) if moss else None

    def paint(w, h, face, rng, frame):
        t, out, cid, n = stones(w, h, cell, rng)
        sp = (rng.random((h, w)) < speck) & ~out
        t = np.where(sp, np.maximum(t - 1, 1), t)
        img = P[t]
        if M is not None:
            ys = np.arange(h)[:, None] + np.zeros((1, w))
            if face == "up":
                p = np.full((h, w), 0.8)
            elif face == "down":
                p = np.zeros((h, w))
            else:
                p = np.clip(0.95 - ys * 0.32, 0, 1)
            sv = rng.random(n)
            cover = (sv[cid] < p) & ~out
            mt = np.clip(t - 1, 0, 2)
            img = np.where(cover[..., None], M[mt], img)
        a = np.ones((h, w, 1))
        base = np.concatenate([img, a], -1)
        ov = None
        if glow_chains:
            k = max(1, int(round(glow_chains * w / 16)))
            crack = crack_walk(out, rng, k)
            halo = near(crack)
            pl = pulse(frame)
            ov = np.zeros((h, w, 4))
            ov[halo] = (0.8, 0.8, 0.8, (0.16 + 0.12 * pl) * glow_top)
            ov[crack] = (0.9 + 0.1 * pl, 0.9 + 0.1 * pl, 0.9 + 0.1 * pl, 1.0)
            # 금의 바탕은 어둡게 (빛이 깊은 데서 나옴)
            base[crack, :3] *= 0.4
        return base, ov
    return paint


def magma(pal, hot, cell=18.0):
    """마그마 돌 (운석): 어두운 돌 + 빛나는 틈 (안 = 노랑 · 둘레 = 주황 · 붉은 돌). hot(face) → 0 ~ 1 (아래 · 떨어지는 쪽이 뜨거움)"""
    P = np.stack([C(c) for c in pal])
    Y, O, R = C("#FFE27A"), C("#FF9A2E"), C("#B8401A")

    def paint(w, h, face, rng, frame):
        t, out, cid, n = stones(w, h, cell, rng)
        img = P[t]
        heat = hot(face)
        ys = np.arange(h)[:, None] / max(1, h - 1) + np.zeros((1, w))
        hrow = np.clip(heat + (ys - 0.5) * 0.6, 0, 1) if face in SIDES else np.full((h, w), heat)
        k = max(1, int(round((0.8 + heat * 2.0) * w / 16)))
        crack = crack_walk(out, rng, k, lo=0.05 if heat > 0.6 else 0.25, hi=0.5 if heat > 0.6 else 0.8)
        halo = near(crack) & ~out
        pl = pulse(frame)
        img = img.copy()
        # 뜨거운 곳의 돌은 붉게 · 틈은 노랑 → 주황
        warm = (hrow > 0.6) & ~out & (rng.random((h, w)) < (hrow - 0.6) * 1.2)
        img[warm] = img[warm] * 0.55 + R * 0.45
        core = crack & (rng.random((h, w)) < 0.3 + 0.5 * hrow)
        img[crack] = O * (0.85 + 0.15 * pl)
        img[core] = Y * (0.9 + 0.1 * pl)
        img[halo] = img[halo] * 0.55 + R * (0.35 + 0.1 * pl)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def ice(alpha=0.8, light=False, cell=46.0):
    """얼음 (사진의 결정처럼): 하늘빛 얼룩 · 흰 금 (사선) · 위 · 왼쪽 모서리 밝게, 아래 · 오른쪽 어둡게 · 반짝임 (맥동)"""
    base = [C("#6E9BE0"), C("#86B2EE"), C("#A3C8F6")] if not light else [C("#9CC4F5"), C("#B8D8FB"), C("#D4EAFF")]
    W_, HI, SH = C("#F2FAFF"), C("#D6ECFF"), C("#4F7FCB")

    def paint(w, h, face, rng, frame):
        cid, edge, n = voronoi(w, h, cell, rng)
        tone = rng.choice([0, 1, 1, 2], n)
        img = np.stack(base)[tone[cid]].copy()
        a = np.full((h, w), alpha)
        # 얼룩 테두리 (옅게)
        e = edge < 1.0
        img[e] = img[e] * 0.92
        # 흰 금 (사선)
        for _ in range(max(1, int(round(w * h / 45)))):
            x, y = rng.uniform(0, w), rng.uniform(0, h)
            L = rng.integers(2, max(3, min(8, h)))
            dx = rng.choice([-1, 1]) * rng.uniform(0.5, 1.0)
            for i in range(int(L)):
                xi, yi = int(x + dx * i), int(y - i)
                if 0 <= xi < w and 0 <= yi < h:
                    img[yi, xi] = W_
                    a[yi, xi] = min(1.0, alpha + 0.15)
        # 모서리
        img[0, :] = HI
        img[:, 0] = HI
        if h > 2:
            img[-1, :] = SH
        if w > 2:
            img[:, -1] = img[:, -1] * 0.75 + SH * 0.25
        a[0, :] = a[:, 0] = min(1.0, alpha + 0.12)
        # 반짝임 (몇 점이 프레임마다)
        r2 = np.random.default_rng(int(rng.integers(1 << 30)))
        for i in range(max(1, int(round(w * h / 120)))):
            x, y = int(r2.integers(0, w)), int(r2.integers(0, h))
            on = pulse(frame + i * 1.7)
            if on > 0.7:
                img[y, x] = W_
                a[y, x] = 1.0
        return np.concatenate([img, a[..., None]], -1), None
    return paint


def cloud_paint(shade=1.0):
    """구름 (회색 — 서버가 계열 색으로 물들임): 윗면 흰색 · 옆면은 위 흰 → 아래로 짙어지는 띠 (두 단) · 밑면 회색, 흐린 점"""
    def paint(w, h, face, rng, frame):
        img = np.ones((h, w, 3))
        if face == "up":
            img[:] = 1.0
            sp = rng.random((h, w)) < 0.08
            img[sp] = 0.93
        elif face == "down":
            img[:] = 0.7 * shade
            sp = rng.random((h, w)) < 0.12
            img[sp] = 0.64 * shade
        else:
            ys = np.arange(h)
            v = np.ones(h)
            b1 = int(round(h * 0.5))
            b2 = int(round(h * 0.78))
            v[b1:] = 0.9
            v[b2:] = 0.8
            v[-1:] = 0.72
            v[0] = 1.0
            img[:] = (v * shade if shade < 1 else v)[:, None, None]
            # 띠 경계는 들쭉날쭉 (구름 결)
            for x in range(w):
                for b, lo, hiv in ((b1, 0.9, 1.0), (b2, 0.8, 0.9)):
                    j = int(rng.integers(-1, 2))
                    yy = b + j
                    if 0 <= yy < h and j < 0:
                        img[yy, x] = lo
                    elif 0 <= yy < h and j > 0:
                        img[b, x] = hiv
            sp = (rng.random((h, w)) < 0.05) & (ys[:, None] < b1)
            img[sp] = 0.94
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def flat_glow(lo=0.7, hi=1.0, ring=True):
    """빛 판 (물들임 · 맥동): 가운데 밝게 · 테두리 한 픽셀"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.sqrt(((xs - cx) / max(1, w / 2)) ** 2 + ((ys - cy) / max(1, h / 2)) ** 2)
        v = np.clip(hi - r * (hi - lo), lo, hi) * (0.85 + 0.15 * pl)
        if ring and w > 2 and h > 2:
            e = (xs == 0) | (ys == 0) | (xs == w - 1) | (ys == h - 1)
            v[e] = hi
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


# ------------------------------------------------------------------ 모델

class Mat:
    def __init__(self, paint, tint=None, glow=False, animated=False, overlay_tint=0, alpha=False):
        self.paint = paint
        self.tint = tint
        self.glow = glow
        self.animated = animated
        self.overlay_tint = overlay_tint


class BModel:
    def __init__(self, name, credit, d=2.0):
        self.name = name
        self.credit = credit
        self.d = d
        self.mats = {}
        self.parts = []

    def mat(self, name, m):
        self.mats[name] = m

    def box(self, mat, f, t, rot=None, faces=None, name=None):
        self.parts.append({"mat": mat, "from": [float(v) for v in f], "to": [float(v) for v in t], "rot": rot, "faces": faces,
                           "name": name or mat})

    # 면의 크기 (모델 픽셀 — 가로 · 세로)
    @staticmethod
    def dims(f, t):
        dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
        return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}

    def build(self):
        """(elements JSON, 그림 (프레임을 위아래로 — RGBA uint8), 프레임 수)"""
        animated = any(m.animated for m in self.mats.values())
        nf = FRAMES if animated else 1
        rects = []  # (요소 번호, 면, 겹?, 프레임별 그림)
        elems = []
        for k, p in enumerate(self.parts):
            m = self.mats[p["mat"]]
            dims = self.dims(p["from"], p["to"])
            fl = [fn for fn in ("north", "south", "east", "west", "up", "down") if p["faces"] is None or fn in p["faces"]]
            base_faces, ov_faces = {}, {}
            for fn in fl:
                w = max(1, int(round(dims[fn][0] * self.d)))
                h = max(1, int(round(dims[fn][1] * self.d)))
                frames_b, frames_o = [], []
                for fi in range(nf):
                    rng = np.random.default_rng(seed_of(self.name, k, fn, p["mat"]))
                    b, o = m.paint(w, h, fn, rng, fi if m.animated else 0)
                    frames_b.append(b)
                    frames_o.append(o)
                base_faces[fn] = frames_b
                if frames_o[0] is not None and frames_o[0][..., 3].max() > 0.05:
                    ov_faces[fn] = frames_o
            elems.append((k, p, m, base_faces, ov_faces))
        # 그림판에 모음 (가장자리 한 픽셀 늘림 — 번짐 막기)
        items = []
        for k, p, m, bf, of in elems:
            for fn, fr in bf.items():
                items.append(((k, fn, False), fr))
            for fn, fr in of.items():
                items.append(((k, fn, True), fr))
        sizes = {key: (fr[0].shape[1] + 2, fr[0].shape[0] + 2) for key, fr in items}
        order = sorted(items, key=lambda it: -sizes[it[0]][1])
        T = 16
        while True:
            pos, x, y, row = {}, 0, 0, 0
            ok = True
            for key, fr in order:
                w, h = sizes[key]
                if w > T:
                    ok = False
                    break
                if x + w > T:
                    x, y, row = 0, y + row, 0
                if y + h > T:
                    ok = False
                    break
                pos[key] = (x, y)
                x += w
                row = max(row, h)
            if ok:
                break
            T *= 2
        sheet = np.zeros((T * nf, T, 4))
        for key, fr in items:
            x0, y0 = pos[key]
            for fi in range(nf):
                im = fr[fi]
                pad = np.pad(im, ((1, 1), (1, 1), (0, 0)), mode="edge")
                sheet[fi * T + y0: fi * T + y0 + pad.shape[0], x0: x0 + pad.shape[1]] = pad
        uvk = 16.0 / T
        out = []
        for k, p, m, bf, of in elems:
            for overlay in (False, True):
                faces_src = of if overlay else bf
                if not faces_src:
                    continue
                f = list(p["from"])
                t = list(p["to"])
                if overlay:
                    f = [v - 0.08 for v in f]
                    t = [v + 0.08 for v in t]
                faces = {}
                for fn, fr in faces_src.items():
                    x0, y0 = pos[(k, fn, overlay)]
                    h, w = fr[0].shape[:2]
                    face = {"uv": [round((x0 + 1) * uvk, 5), round((y0 + 1) * uvk, 5), round((x0 + 1 + w) * uvk, 5), round((y0 + 1 + h) * uvk, 5)],
                            "texture": "#0"}
                    ti = m.overlay_tint if overlay else m.tint
                    if ti is not None:
                        face["tintindex"] = ti
                    faces[fn] = face
                e = {"name": p["name"] + ("_glow" if overlay else ""), "from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": faces}
                if m.glow or overlay:
                    e["light_emission"] = 15
                if p["rot"]:
                    ax, ang, org = p["rot"]
                    e["rotation"] = {"angle": ang, "axis": ax, "origin": [round(v, 4) for v in org]}
                out.append(e)
        img = (np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8)
        return out, img, nf, T

    def tints(self):
        n = 0
        for m in self.mats.values():
            if m.tint is not None:
                n = max(n, m.tint + 1)
        for k, p in enumerate(self.parts):
            pass
        return n

    def files(self):
        els, img, nf, T = self.build()
        for e in els:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (self.name, e["name"], v)
        js = {"credit": "tools/vfx_blocky.py — " + self.credit, "textures": {"0": "rpg:item/vfx/" + self.name, "particle": "rpg:item/vfx/" + self.name},
              "elements": els}
        nt = 0
        for e in els:
            for fc in e["faces"].values():
                if "tintindex" in fc:
                    nt = max(nt, fc["tintindex"] + 1)
        model = {"type": "minecraft:model", "model": "rpg:item/vfx/" + self.name}
        if nt:
            model["tints"] = [{"type": "minecraft:custom_model_data", "index": i, "default": 16777215} for i in range(nt)]
        buf = io.BytesIO()
        Image.fromarray(img, "RGBA").save(buf, "PNG", optimize=True)
        out = {
            os.path.join(RES, "models", "item", "vfx", self.name + ".json"): (json.dumps(js, ensure_ascii=False, indent=1) + "\n").encode(),
            os.path.join(RES, "textures", "item", "vfx", self.name + ".png"): buf.getvalue(),
            os.path.join(RES, "items", "vfx", self.name + ".json"): (json.dumps({"model": model}, indent=2) + "\n").encode(),
        }
        mc = os.path.join(RES, "textures", "item", "vfx", self.name + ".png.mcmeta")
        out[mc] = (json.dumps({"animation": {"frametime": FRAMETIME, "interpolate": True}}, indent=2) + "\n").encode() if nf > 1 else None
        return out, js, img, T


# ------------------------------------------------------------------ 물체

STONE = ("#2E2622", "#564A42", "#6F6158", "#8A7A6E", "#A39282")  # 바위 가시 (흙빛 회색 — 골렘 · 노움)
MOSS = ("#3F5A2A", "#557535", "#6E9142")


def rock_spike():
    """바위 가시 (골렘 · 노움 · 정령왕의 땅 …): 밑 가운데 (8, 0, 8) · 높이 26.5 — 돌 덩이 다섯을 엇갈려 쌓은 가시 + 밑동의 깨진 돌, 금 사이 빛 = 계열 색 (tint 0)"""
    m = BModel("rock_spike", "바위 가시 (2026-11-12 사진 방식 — 돌 덩이 · 돌 테두리 · 이끼 · 틈의 빛 = 계열 색)", d=2.0)
    m.mat("rock", Mat(cobble(STONE, cell=20, glow_chains=1.4), animated=True, overlay_tint=0))
    m.mat("mossy", Mat(cobble(STONE, cell=20, moss=MOSS, glow_chains=0.9), animated=True, overlay_tint=0))
    m.mat("dark", Mat(cobble(("#221C19", "#3F3631", "#51463F", "#665850", "#7A6A5F"), cell=18)))
    m.mat("chip", Mat(cobble(STONE, cell=14)))
    m.box("dark", (3.0, 0, 3.0), (13.0, 6.5, 13.0), rot=("y", 22.5, (8, 3, 8)))
    m.box("rock", (4.0, 5.5, 4.4), (12.0, 12.5, 12.4), rot=("y", -22.5, (8, 9, 8.4)))
    m.box("mossy", (5.2, 11.5, 4.9), (11.0, 17.5, 10.7))
    m.box("rock", (6.0, 16.8, 6.0), (10.0, 21.6, 10.0), rot=("y", 45.0, (8, 19, 8)))
    m.box("rock", (6.9, 21.0, 6.9), (9.1, 24.4, 9.1), rot=("y", 22.5, (8, 22.7, 8)))
    m.box("chip", (7.5, 24.0, 7.5), (8.5, 26.5, 8.5))
    # 밑동의 깨진 돌
    m.box("chip", (0.6, 0, 6.0), (3.6, 2.6, 9.0), rot=("y", 22.5, (2.1, 1.3, 7.5)))
    m.box("chip", (12.4, 0, 4.4), (15.0, 2.2, 7.0), rot=("z", 22.5, (13.7, 0, 5.7)))
    m.box("dark", (5.6, 0, 12.6), (8.8, 2.4, 15.4), rot=("y", 45.0, (7.2, 1.2, 14.0)))
    m.box("chip", (8.4, 0, 0.8), (11.0, 1.8, 3.4), rot=("x", -22.5, (9.7, 0, 2.1)))
    return m


def ice_spike():
    """얼음 가시: 밑 가운데 (8, 0, 8) · 높이 27 — 팔각 결정 (네모 + 45° 네모) 셋을 쌓고 옆 결정 셋, 얼룩 · 흰 금 · 모서리 빛"""
    m = BModel("ice_spike", "얼음 가시 (2026-11-12 사진 방식 — 결정 덩이 · 얼룩 · 흰 금 · 모서리 빛)", d=2.0)
    m.mat("ice", Mat(ice(0.8), glow=True, animated=True))
    m.mat("hi", Mat(ice(0.9, light=True), glow=True, animated=True))

    def oct_(mat, x0, x1, y0, y1, c=8.0):
        m.box(mat, (x0, y0, x0), (x1, y1, x1))
        m.box(mat, (x0 + 0.3, y0, x0 + 0.3), (x1 - 0.3, y1, x1 - 0.3), rot=("y", 45.0, (c, (y0 + y1) / 2, c)))
    oct_("ice", 5.2, 10.8, 0, 13.5)
    oct_("ice", 6.0, 10.0, 13.0, 19.5)
    oct_("hi", 6.9, 9.1, 19.0, 23.5)
    m.box("hi", (7.5, 23.0, 7.5), (8.5, 27.0, 8.5))
    # 옆 결정 (기울임)
    m.box("ice", (9.8, 0, 6.6), (12.6, 9.0, 9.4), rot=("z", -22.5, (11.2, 0, 8)))
    m.box("hi", (10.4, 9.0, 7.2), (12.0, 12.0, 8.8), rot=("z", -22.5, (11.2, 0, 8)))
    m.box("ice", (3.6, 0, 7.0), (6.0, 7.0, 9.4), rot=("z", 22.5, (4.8, 0, 8.2)))
    m.box("hi", (4.1, 7.0, 7.5), (5.5, 9.4, 8.9), rot=("z", 22.5, (4.8, 0, 8.2)))
    m.box("ice", (7.0, 0, 3.6), (9.4, 6.5, 6.0), rot=("x", -22.5, (8.2, 0, 4.8)))
    m.box("hi", (7.5, 6.5, 4.1), (8.9, 8.8, 5.5), rot=("x", -22.5, (8.2, 0, 4.8)))
    return m


METEOR_STONE = ("#120C0A", "#2A1F1A", "#3A2B23", "#4C392E", "#5E4739")


def meteor():
    """운석: 가운데 (8, 8, 8) · 지름 약 19 — 네모 덩이 넷 (그대로 · y · x · z 축 45°) 이 겹친 둥근 바위 + 튀어나온 조각, 아래 (떨어지는 쪽) 가 달아오름"""
    m = BModel("meteor", "운석 (2026-11-12 사진 방식 — 둥근 바위 덩이 · 빛나는 틈 · 아래가 달아오름)", d=2.5)

    def hot(face):
        return {"down": 1.0, "up": 0.3}.get(face, 0.55)
    m.mat("rock", Mat(magma(METEOR_STONE, hot), glow=True, animated=True))
    # 계단 공 — 축마다 긴 막대 셋 (반지름 9.5) · 판 셋 (7 · 7 · 5) · 가운데 덩이 (6.2): 어느 쪽에서 봐도 세 단으로 깎인 둥근 바위, 지름 19
    R, A, B, Cc, K = 9.5, 5.0, 7.0, 5.0, 6.2

    def cb(hx, hy, hz):
        m.box("rock", (8 - hx, 8 - hy, 8 - hz), (8 + hx, 8 + hy, 8 + hz))
    cb(R, A, A)
    cb(A, R, A)
    cb(A, A, R)
    cb(B, B, Cc)
    cb(B, Cc, B)
    cb(Cc, B, B)
    cb(K, K, K)
    # 튀어나온 조각 (모양이 고르지 않게)
    m.box("rock", (12.5, 9.5, 2.5), (17.0, 14.0, 7.0), rot=("y", 22.5, (14.75, 11.75, 4.75)))
    m.box("rock", (-1.0, 3.0, 9.5), (3.5, 7.5, 14.0), rot=("z", 22.5, (1.25, 5.25, 11.75)))
    m.box("rock", (5.0, 15.0, 8.5), (9.5, 18.5, 13.0), rot=("x", 22.5, (7.25, 16.75, 10.75)))
    m.box("rock", (8.0, -1.0, 1.0), (12.5, 2.5, 5.5), rot=("y", 45.0, (10.25, 0.75, 3.25)))
    return m


def cloud():
    """구름: 가운데 (8, 8.6, 8) · 너비 28 · 높이 14 — 네모 덩이 여덟 (사진의 덩어리처럼) · 흰 윗면 · 아래로 짙어지는 띠, 밑의 빛 (tint 1)"""
    m = BModel("cloud", "구름 (2026-11-12 사진 방식 — 네모 덩이 · 흰 윗면 · 짙어지는 띠 · 색은 서버가 물들임)", d=2.0)
    m.mat("puff", Mat(cloud_paint(), tint=0))
    m.mat("core", Mat(flat_glow(0.75, 1.0), tint=1, glow=True, animated=True))
    puffs = [(8, 8.0, 8, 6.5), (2.5, 6.2, 9, 5.0), (13.5, 6.2, 7, 5.2), (6, 9.6, 4.5, 4.4), (10.5, 9.6, 12, 4.6), (-2.5, 4.6, 7.5, 3.6),
             (18.5, 4.6, 9, 3.8), (4, 4.6, 13.5, 4.0), (12, 4.6, 2.5, 4.0)]
    for (cx, cy, cz, r) in puffs:
        y0 = max(1.6, cy - 0.55 * r)
        y1 = cy + 0.5 * r
        m.box("puff", (cx - r, y0, cz - 0.62 * r), (cx + r, y1, cz + 0.62 * r))
        m.box("puff", (cx - 0.62 * r, y0, cz - r), (cx + 0.62 * r, y1, cz + r))
        m.box("puff", (cx - 0.6 * r, y1 - 0.2, cz - 0.6 * r), (cx + 0.6 * r, min(15.6, cy + 0.88 * r), cz + 0.6 * r))
    # 밑의 빛 (번개 · 얼음 · 치유의 빛 — 물들임 1)
    for (x, z, s) in ((6.0, 6.0, 2.6), (10.2, 9.0, 2.2), (4.2, 10.4, 1.8), (12.0, 4.6, 1.6), (8.2, 12.2, 1.6)):
        m.box("core", (x - s / 2, 1.45, z - s / 2), (x + s / 2, 1.55, z + s / 2), faces=["down"])
    return m


# ------------------------------------------------------------------ 무기 · 손 · 달 · 그물 · 물 그림

def bevel(base, hi=None, sh=None, grain=None, speck=0.0, alpha=1.0, gray=False):
    """평평한 재질 + 모서리 (위 · 왼쪽 밝게, 아래 · 오른쪽 어둡게) · 결 (grain 'v' 세로 · 'h' 가로) · 점"""
    B = np.full(3, base) if gray else C(base)
    H = (np.full(3, hi) if gray else C(hi)) if hi is not None else np.minimum(1.0, B * 1.22 + 0.05)
    S = (np.full(3, sh) if gray else C(sh)) if sh is not None else B * 0.62

    def paint(w, h, face, rng, frame):
        img = np.tile(B, (h, w, 1)).astype(float)
        if grain == "v":
            img *= (0.9 + 0.2 * rng.random(w))[None, :, None]
        elif grain == "h":
            img *= (0.9 + 0.2 * rng.random(h))[:, None, None]
        if speck:
            sp = rng.random((h, w)) < speck
            img[sp] *= 0.82
        if h >= 3:
            img[0, :] = H
            img[-1, :] = S
        if w >= 3:
            img[1:, 0] = img[1:, 0] * 0.4 + H * 0.6
            img[:-1, -1] = img[:-1, -1] * 0.4 + S * 0.6
        return np.concatenate([np.clip(img, 0, 1), np.full((h, w, 1), alpha)], -1), None
    return paint


def gold_paint():
    B, H, S, E = C("#E2B04A"), C("#FFE7A0"), C("#9A6A24"), C("#B8862E")

    def paint(w, h, face, rng, frame):
        img = np.tile(B, (h, w, 1)).astype(float)
        if h >= 3:
            img[0, :] = H
            img[-1, :] = S
            mid = h // 2
            img[mid, ::3] = E
        else:
            img[0, :] = H if h == 2 else B
            if h == 2:
                img[1, :] = S
        if w >= 3:
            img[:, 0] = img[:, 0] * 0.5 + H * 0.5
            img[:, -1] = img[:, -1] * 0.5 + S * 0.5
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def grip_paint():
    D_, A_, B_ = C("#24140F"), C("#4A2A1E"), C("#61392A")

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        k = (xs + ys) % 4
        img = np.where((k == 0)[..., None], D_, np.where((k == 1)[..., None], B_, A_))
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def glow_blade():
    """빛나는 날 (회색 — 계열 색 물들임): 날 끝 쪽 밝게 → 가운데 등 어둡게, 사선 반짝임 (맥동)"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        if w >= 3:
            xs = np.arange(w)
            c = (w - 1) / 2
            prof = 1.0 - 0.34 * (1 - np.abs(xs - c) / max(c, 1))
            img = np.tile(prof[None, :], (h, 1))
            for y0 in range(int(rng.integers(0, 6)), h, 9):
                for i in range(min(w, 3)):
                    yy = y0 + i
                    if 0 <= yy < h:
                        img[yy, min(w - 1, i + 1)] = 1.0
        else:
            img = np.full((h, w), 0.84)
        img = img * (0.88 + 0.12 * pl)
        img = np.repeat(img[..., None], 3, -1)
        return np.concatenate([img, np.full((h, w, 1), 0.96)], -1), None
    return paint


def rune_flow(base=0.42):
    """룬이 흐르는 홈 (회색 — 속빛 물들임): 어두운 바탕 + 룬 점들, 밝은 띠가 프레임마다 위로"""
    def paint(w, h, face, rng, frame):
        img = np.full((h, w), base)
        on = rng.random((h, w)) < 0.45
        runs = np.zeros(h, bool)
        y = 0
        while y < h:
            L = int(rng.integers(1, 4))
            runs[y:y + L] = True
            y += L + int(rng.integers(1, 3))
        mask = on | runs[:, None]
        img[mask] = 0.85
        band = (np.arange(h)[:, None] + frame * max(1, h // FRAMES)) % max(4, h) < max(2, h // 5)
        img[mask & band] = 1.0
        img = np.repeat(img[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def gem_paint(lo=0.55):
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.maximum(np.abs(xs - cx) / max(cx, 0.5), np.abs(ys - cy) / max(cy, 0.5))
        v = 1.0 - (1.0 - lo) * r
        v = v * (0.85 + 0.15 * pl)
        if w >= 2 and h >= 2:
            v[0, 0] = 1.0
            v[-1, -1] = lo * 0.8
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def edge_paint():
    W_, L_ = C("#F4FAFF"), C("#CFE0F0")

    def paint(w, h, face, rng, frame):
        img = np.tile(W_, (h, w, 1)).astype(float)
        if w >= 2:
            img[1::2, -1] = L_
        else:
            img[2::3, 0] = L_
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def ki_paint(alpha=0.78):
    """기 (손바닥 — 회색, 계열 색 물들임 · 반투명): 가장자리 밝은 테 · 위로 흐르는 결"""
    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        v = np.full((h, w), 0.78)
        a = np.full((h, w), alpha)
        cols = rng.random(w) < 0.35
        flow = ((ys + frame * 2 + (xs * 3) % 5) % 7) < 2
        v[flow & cols[None, :]] = 0.95
        e = (xs == 0) | (ys == 0) | (xs == w - 1) | (ys == h - 1)
        v[e] = 1.0
        a[e] = min(1.0, alpha + 0.18)
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, a[..., None]], -1), None
    return paint


def seal_paint():
    """손바닥의 인 (회색 — 속빛 물들임): 둥근 테 + 가운데 룬"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.sqrt(((xs - cx) / max(cx, 1)) ** 2 + ((ys - cy) / max(cy, 1)) ** 2)
        v = np.full((h, w), 0.5)
        a = np.full((h, w), 0.0)
        ring = np.abs(r - 0.85) < 0.16
        v[ring] = 1.0
        a[ring] = 1.0
        cross = ((np.abs(xs - cx) < 0.6) | (np.abs(ys - cy) < 0.6) | (np.abs(np.abs(xs - cx) - np.abs(ys - cy)) < 0.6)) & (r < 0.62)
        v[cross] = 0.9 + 0.1 * pl
        a[cross] = 1.0
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, a[..., None]], -1), None
    return paint


def skin_glow():
    """기의 주먹 피부 (회색 — 계열 색 물들임): 부드러운 띠 · 흐르는 결 · 모서리 (맥동)"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        v = 0.74 + 0.08 * np.sin((xs * 0.9 + ys * 0.35 + frame * 1.0)) * (rng.random() * 0.5 + 0.5)
        if h >= 3:
            v[0, :] = 0.92
            v[-1, :] = 0.56
        if w >= 3:
            v[:, 0] = np.maximum(v[:, 0], 0.86)
            v[:, -1] = np.minimum(v[:, -1], 0.6)
        v = v * (0.92 + 0.08 * pl)
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def wrap_paint():
    """붕대: 흰 천 가로 띠 · 겹친 그늘 · 사선 주름"""
    B, S, D_ = C("#EAE2D2"), C("#C3B8A4"), C("#A39883")

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        img = np.tile(B, (h, w, 1)).astype(float)
        band = (ys % 4) == 3
        img[band] = S
        fold = ((xs + ys * 2) % 9) == 0
        img[fold] = D_
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def stitched(base, stitch):
    B, St = C(base), C(stitch)

    def paint(w, h, face, rng, frame):
        img = np.tile(B, (h, w, 1)).astype(float)
        img *= (0.92 + 0.12 * rng.random((h, w)))[..., None]
        if h >= 5:
            img[1, ::2] = St
            img[-2, ::2] = St
        if h >= 3:
            img[0, :] = img[0, :] * 0.5 + 0.5
            img[-1, :] *= 0.6
        return np.concatenate([np.clip(img, 0, 1), np.ones((h, w, 1))], -1), None
    return paint


def rune_plate():
    """룬 판 (회색 — 속빛 물들임): 테 + 룬 글자 (세로 획 + 가지)"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        v = np.full((h, w), 0.45)
        a = np.ones((h, w))
        v[0, :] = v[-1, :] = 0.9
        v[:, 0] = v[:, -1] = 0.9
        cx = w // 2
        if h > 4:
            v[1:-1, cx] = 1.0
            k = int(rng.integers(0, 3))
            for i in range(1, min(w // 2, h // 2)):
                yy = 1 + i if k != 1 else h - 2 - i
                if 0 < yy < h - 1:
                    if cx + i < w - 1:
                        v[yy, cx + i] = 1.0
                    if k == 2 and cx - i > 0:
                        v[yy, cx - i] = 1.0
        v = v * (0.85 + 0.15 * pl)
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, a[..., None]], -1), None
    return paint


def moon_paint():
    """핏빛 달: 붉은 바탕 · 짙은 바다 얼룩 · 운석 구덩이 (안 어둡게 · 위 왼쪽 테 밝게 · 아래 오른쪽 테 어둡게)"""
    T = [C("#8E2428"), C("#B8343A"), C("#D04A48"), C("#E8706A")]
    PIT, RH, RS = C("#6A181E"), C("#F6988A"), C("#5A1218")

    def paint(w, h, face, rng, frame):
        cid, edge, n = voronoi(w, h, 70.0, rng)
        tone = rng.choice([0, 1, 2, 2, 3], n)
        img = np.stack(T)[tone[cid]].copy()
        img *= (0.94 + 0.08 * rng.random((h, w)))[..., None]
        ys, xs = np.mgrid[0:h, 0:w]
        for _ in range(max(1, int(round(w * h / 700)))):
            r = rng.uniform(1.8, max(2.2, min(w, h) / 6.5))
            cx, cy = rng.uniform(0, w), rng.uniform(0, h)
            d = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2)
            inside = d < r - 0.8
            rim = (d >= r - 0.8) & (d < r + 0.4)
            ang = np.arctan2(ys + 0.5 - cy, xs + 0.5 - cx)
            img[inside] = PIT
            upleft = np.cos(ang - math.radians(-135)) > 0.2
            img[rim & upleft] = RS
            img[rim & ~upleft] = RH
        return np.concatenate([np.clip(img, 0, 1), np.ones((h, w, 1))], -1), None
    return paint


def net_paint(S=6, magic=False):
    """그물 (구멍은 투명): 꼰 줄 격자 · 매듭. magic 이면 빛 겹 — 몇 줄 (계열 색)"""
    R1, R2, K = C("#9C7A4E"), C("#6E5434"), C("#4A3822")

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        ox, oy = int(rng.integers(0, S)), int(rng.integers(0, S))
        gx = (xs + ox) % S == 0
        gy = (ys + oy) % S == 0
        rope = gx | gy
        img = np.where((((xs + ys) % 2) == 0)[..., None], R1, R2)
        img = np.where((gx & gy)[..., None], K, img)
        a = rope.astype(float)
        # 가장자리 줄 (테두리)
        e = (xs == 0) | (ys == 0) | (xs == w - 1) | (ys == h - 1)
        a[e] = 1.0
        base = np.concatenate([img, a[..., None]], -1)
        ov = None
        if magic:
            pl = pulse(frame)
            lx = ((xs + ox) // S) % 2 == 0
            ly = ((ys + oy) // S) % 3 == 0
            mg = (gx & lx & ~gy) | (gy & ly & ~gx) | e
            ov = np.zeros((h, w, 4))
            ov[mg] = (0.9 + 0.1 * pl, 0.9 + 0.1 * pl, 0.9 + 0.1 * pl, 1.0)
        return base, ov
    return paint


def water_paint(c0, c1, alpha=0.86, foam_top=False):
    """물: 깊은 색 → 밝은 색 띠 · 프레임마다 흐르는 밝은 사선 · (foam_top) 윗줄 거품"""
    A_, B_ = C(c0), C(c1)
    W_ = C("#EEF8FF")

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        t = 0.5 + 0.5 * np.sin((ys * 0.55 + xs * 0.18) + frame * (math.pi * 2 / FRAMES))
        tt = np.clip(0.25 + 0.5 * t + 0.25 * rng.random((h, w)) * 0.4, 0, 1)
        img = A_ * (1 - tt[..., None]) + B_ * tt[..., None]
        streak = ((xs * 2 + ys + frame * 2) % 11) == 0
        img[streak] = img[streak] * 0.4 + W_ * 0.6
        a = np.full((h, w), alpha)
        if foam_top and h >= 2:
            k = max(1, h // 5)
            ragged = rng.integers(0, 2, w)
            for x in range(w):
                img[:k + ragged[x], x] = W_
                a[:k + ragged[x], x] = 0.97
        return np.concatenate([np.clip(img, 0, 1), a[..., None]], -1), None
    return paint


def foam_paint():
    W_, L_ = C("#F4FBFF"), C("#B8DCF8")

    def paint(w, h, face, rng, frame):
        img = np.tile(W_, (h, w, 1)).astype(float)
        sp = rng.random((h, w)) < 0.12
        sh = np.roll(sp, frame % 3, 1)
        img[sh] = L_
        return np.concatenate([img, np.full((h, w, 1), 0.96)], -1), None
    return paint


def glass(c_lo, c_hi, alpha=0.55, edge="all", edge_col="#EAF8FF", edge_alpha=0.95, streak="h", streak_col="#FFFFFF", streak_alpha=0.9,
          density=1.0, speed=1.0, gray=False, up_t=1.0, down_t=0.15):
    """반투명 기운 · 물 (사진 6 의 반투명 원통처럼): 아래 짙은 색 → 위 밝은 색 · 흐르는 흰 줄 (h 가로 물결 · d 사선 · v 세로 — 프레임마다 흐름) ·
    테두리 한 픽셀 (edge: all · top · None) · 반짝임. gray 면 색이 회색 값 (계열 색 물들임)"""
    lo = np.full(3, c_lo) if gray else C(c_lo)
    hi = np.full(3, c_hi) if gray else C(c_hi)
    E = np.full(3, 1.0) if gray else C(edge_col)
    S = np.full(3, 1.0) if gray else C(streak_col)

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        if face in SIDES:
            t = 1.0 - ys / max(1, h - 1)
        elif face == "up":
            t = np.full((h, w), up_t)
        else:
            t = np.full((h, w), down_t)
        img = lo * (1 - t[..., None]) + hi * t[..., None]
        a = np.full((h, w), alpha)
        ph = frame / FRAMES * math.pi * 2
        if streak and face in SIDES:
            n = max(1, int(round(density * (w * h) ** 0.5 / 5)))
            for k in range(n):
                y0 = rng.uniform(0, h)
                amp = rng.uniform(0.6, 1.8)
                fq = rng.uniform(0.25, 0.6)
                p0 = rng.uniform(0, 6.3)
                x0 = int(rng.integers(0, w))
                L = int(rng.integers(max(2, w // 3), max(3, w + 1)))
                for i in range(L):
                    x = (x0 + i) % w
                    if streak == "h":
                        y = y0 + amp * math.sin(x * fq + p0 + ph) - frame * speed * h / FRAMES / 3
                    elif streak == "d":
                        y = y0 - x * 0.8 - frame * speed * h / FRAMES / 2
                    else:  # v — 세로로 오르는 줄 (짧게)
                        if i > max(2, h // 4):
                            break
                        x = x0
                        y = y0 - i - frame * speed * h / FRAMES / 2
                    yi = int(math.floor(y)) % h
                    img[yi, x] = img[yi, x] * (1 - streak_alpha) + S * streak_alpha
                    a[yi, x] = max(a[yi, x], min(1.0, alpha + 0.35))
        # 반짝임
        r2 = np.random.default_rng(int(rng.integers(1 << 30)))
        for i in range(max(1, int(round(w * h / 160)))):
            x, y = int(r2.integers(0, w)), int(r2.integers(0, h))
            if pulse(frame + i * 1.3) > 0.75:
                img[y, x] = S
                a[y, x] = 1.0
        if edge == "all" and w >= 2 and h >= 2:
            e = (xs == 0) | (ys == 0) | (xs == w - 1) | (ys == h - 1)
            img[e] = img[e] * 0.3 + E * 0.7
            a[e] = edge_alpha
        elif edge == "top" and face in SIDES:
            img[0, :] = E
            a[0, :] = edge_alpha
        return np.concatenate([np.clip(img, 0, 1), a[..., None]], -1), None
    return paint


def foam2(alpha=0.96):
    """거품: 흰 바탕 · 옅은 하늘빛 거품 고리 (작은 원) · 옆면 아랫줄은 하늘빛 그늘 (프레임마다 조금 움직임)"""
    W_, B_, S_ = C("#F6FCFF"), C("#BFE3FA"), C("#8CC6EE")

    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        img = np.tile(W_, (h, w, 1)).astype(float)
        for i in range(max(1, int(round(w * h / 22)))):
            cx = rng.uniform(0, w) + (frame % 3) * 0.3
            cy = rng.uniform(0, h)
            r = rng.uniform(0.9, 2.0)
            d = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2)
            ring = np.abs(d - r) < 0.5
            img[ring] = B_
        if face in SIDES and h >= 3:
            img[-1, :] = S_
            img[-2, :] = img[-2, :] * 0.5 + B_ * 0.5
        return np.concatenate([img, np.full((h, w, 1), alpha)], -1), None
    return paint


# ------------------------------------------------------------------ 무기 (치수는 tools/vfx_props.py 그대로 — RuneProps 의 길이 · 쥔 곳)

def weapon_mats(m, glow_tint=0, core_tint=1):
    m.mat("blade", Mat(glow_blade(), tint=glow_tint, glow=True, animated=True))
    m.mat("fuller", Mat(rune_flow(), tint=core_tint, glow=True, animated=True))
    m.mat("edge", Mat(edge_paint(), glow=True))
    m.mat("steel", Mat(bevel("#34343F", "#5A5A6A", "#1C1C23", speck=0.06)))
    m.mat("gold", Mat(gold_paint()))
    m.mat("grip", Mat(grip_paint()))
    m.mat("gem", Mat(gem_paint(), tint=glow_tint, glow=True, animated=True))


def light_blade(alpha=0.8):
    """2026-11-13 룬 검의 빛 날 (회색 — 계열 색 물들임 · 반투명): 넓은 면은 날 끝 쪽이 밝고 가운데로 옅어짐 + 위로 흐르는 사선 빛 (프레임마다),
    좁은 면 (날 · 위아래) 은 밝게"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        if face in ("east", "west") and w >= 4:
            c = (w - 1) / 2
            r = np.abs(xs - c) / max(c, 1)
            v = 0.62 + 0.36 * r ** 1.6
            a = alpha * (0.72 + 0.28 * r)
            band = ((ys * 2 - xs + frame * max(2, h // FRAMES)) % max(10, h // 2)) < 2
            v = np.where(band, np.minimum(1.0, v + 0.28), v)
            a = np.where(band, np.minimum(1.0, a + 0.15), a)
            sp = rng.random((h, w)) < 0.025
            v[sp] = 1.0
        else:
            v = np.full((h, w), 0.95)
            a = np.full((h, w), min(1.0, alpha + 0.12))
        v = v * (0.9 + 0.1 * pl)
        img = np.repeat(np.clip(v, 0, 1)[..., None], 3, -1)
        return np.concatenate([img, np.clip(a, 0, 1)[..., None]], -1), None
    return paint


def edge_light(alpha=0.92):
    """빛 날의 날 (흰 · 조금 비침): 가운데 줄 밝게, 가장자리 옅게"""
    W_, L_ = C("#FFFFFF"), C("#DCEBFF")

    def paint(w, h, face, rng, frame):
        img = np.tile(W_, (h, w, 1)).astype(float)
        a = np.full((h, w), alpha)
        if w >= 3:
            img[:, 0] = L_
            img[:, -1] = L_
            a[:, 0] = a[:, -1] = alpha * 0.75
        return np.concatenate([img, a[..., None]], -1), None
    return paint


def rune_channel():
    """룬 검의 솟은 홈 (회색 — 속빛 물들임): 밝은 바탕에 일정한 간격의 룬 글자 (어둡게 새김), 밝은 띠가 프레임마다 위로"""
    GLYPHS = [["010", "111", "010", "010"], ["101", "010", "101", "010"], ["110", "011", "010", "010"], ["010", "011", "110", "010"],
              ["111", "010", "010", "111"], ["100", "110", "011", "001"]]

    def paint(w, h, face, rng, frame):
        v = np.full((h, w), 0.96)
        if face in ("east", "west") and w >= 3 and h >= 8:
            x0 = (w - 3) // 2
            k = int(rng.integers(0, len(GLYPHS)))
            y = 2
            while y + 4 < h - 1:
                g = GLYPHS[k % len(GLYPHS)]
                for gy, row in enumerate(g):
                    for gx, ch in enumerate(row):
                        if ch == "1" and x0 + gx < w:
                            v[h - 1 - (y + gy), x0 + gx] = 0.5
                k += 1
                y += 6
            band = ((np.arange(h)[::-1, None] - frame * max(1, h // FRAMES)) % max(8, h // 3)) < 2
            v = np.where(band & (v > 0.9), 1.0, v)
            v = np.where(band & (v <= 0.9), 0.72, v)
            if w >= 4:
                v[:, 0] = np.minimum(v[:, 0], 0.8)
                v[:, -1] = np.minimum(v[:, -1], 0.8)
        img = np.repeat(np.clip(v, 0, 1)[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def steel_rune():
    """칼 목 (어두운 쇠 + 룬 홈 — 홈은 겹 그림이 아니라 같은 면의 밝은 획, 계열 속빛은 물들이지 않음)"""
    B, H, S, R_ = C("#2B2C38"), C("#555A6E"), C("#15151C"), C("#9FB4D8")

    def paint(w, h, face, rng, frame):
        img = np.tile(B, (h, w, 1)).astype(float)
        img *= (0.9 + 0.15 * rng.random((h, w)))[..., None]
        if h >= 3:
            img[0, :] = H
            img[-1, :] = S
        if w >= 3:
            img[:, 0] = img[:, 0] * 0.4 + H * 0.6
            img[:, -1] = img[:, -1] * 0.4 + S * 0.6
        if w >= 5 and h >= 5:
            cx = w // 2
            img[1:-1, cx] = R_
            for yy in range(2, h - 1, 3):
                img[yy, max(1, cx - 1)] = R_
                img[yy, min(w - 2, cx + 1)] = R_
        return np.concatenate([np.clip(img, 0, 1), np.ones((h, w, 1))], -1), None
    return paint


def rune_sword():
    """2026-11-13 룬 전용 검 2판 (초월 · 영웅 룬 연출 — 사용자 메모 "검 퀄이 너무 낮음 더 코퀄로"): 같은 길이 · 쥔 곳 (칼끝 y 31 · 자루 끝 y −10.2 ·
    손잡이 (8, −3.5, 8)) 에서 — 넓은 반투명 빛 날 (계열 색 · 날 끝 밝게 · 흐르는 사선 빛) · 흰 날 · 가운데 솟은 룬 홈 (속빛) · 마름모 칼끝 ·
    위로 젖힌 날개 코등이 (금 · 쇠 두 겹 · 끝의 보석) · 가운데 보석 · 룬 새긴 칼 목 · 가죽 손잡이 · 마름모 보석 자루 끝.
    요소 차례: 불투명 (자루 · 코등이 · 홈) 을 먼저, 반투명 날 · 날을 뒤에 (아이템은 면을 거르지 않고 차례로 그림 — 날 뒤의 홈이 비쳐 보이게)"""
    m = BModel("rune_sword", "룬 전용 검 2판 (2026-11-13 — 반투명 빛 날 · 솟은 룬 홈 · 마름모 칼끝 · 날개 코등이 · 보석, 계열 색은 서버가 물들임)", d=2.5)
    weapon_mats(m)
    m.mat("light", Mat(light_blade(0.78), tint=0, glow=True, animated=True))
    m.mat("edge2", Mat(edge_light(0.9), glow=True))
    m.mat("collar", Mat(steel_rune()))
    m.mat("core", Mat(gem_paint(0.62), tint=1, glow=True, animated=True))
    m.mat("channel", Mat(rune_channel(), tint=1, glow=True, animated=True))
    # 자루 끝: 금 받침 · 마름모 보석 (Y 둘레 45°) · 금 테
    m.box("gold", (7.3, -10.2, 7.3), (8.7, -9.5, 8.7))
    m.box("gem", (7.05, -9.9, 7.05), (8.95, -7.9, 8.95), rot=("y", 45, (8, -8.9, 8)))
    m.box("gold", (6.9, -8.0, 6.9), (9.1, -7.1, 9.1))
    # 손잡이: 가죽 · 금 고리 둘
    m.box("grip", (7.28, -7.1, 7.28), (8.72, 0.0, 8.72))
    for y in (-5.2, -2.7):
        m.box("gold", (7.1, y, 7.1), (8.9, y + 0.55, 8.9))
    # 코등이 가운데: 쇠 덩이 · 금 테 · 양면의 보석 (X 둘레 45° — 마름모) · 손잡이로 내려온 금 판
    m.box("steel", (6.75, 0.0, 5.5), (9.25, 3.0, 10.5))
    m.box("gold", (6.6, 0.25, 6.2), (9.4, 2.75, 9.8))
    m.box("core", (6.35, 0.6, 7.1), (9.65, 2.4, 8.9), rot=("x", 45, (8, 1.5, 8)))
    m.box("gold", (6.95, -1.5, 7.45), (9.05, 0.0, 8.55))
    # 날개 (양쪽 — 위로 22.5° 젖힘): 아래 쇠 · 위 금 · 끝의 보석 (마름모)
    for s in (-1, 1):
        z0, z1 = (-1.2, 5.6) if s < 0 else (10.4, 17.2)
        oz = 5.6 if s < 0 else 10.4
        ang = 22.5 if s < 0 else -22.5
        m.box("steel", (7.2, 0.2, z0), (8.8, 1.5, z1), rot=("x", ang, (8, 1.5, oz)))
        m.box("gold", (7.05, 1.3, z0 + (0.6 if s < 0 else 0)), (8.95, 2.5, z1 - (0 if s < 0 else 0.6)), rot=("x", ang, (8, 1.5, oz)))
        d = 6.2  # 날개 끝 (안쪽 끝에서) — 돌린 자리 (y = 1.9 + d · sin 22.5 · z = d · cos 22.5)
        tz = oz + s * d * math.cos(math.radians(22.5))
        ty = 1.9 + d * math.sin(math.radians(22.5))
        m.box("gem", (7.1, ty - 1.0, tz - 1.0), (8.9, ty + 1.0, tz + 1.0), rot=("x", 45, (8, ty, tz)))
    # 칼 목 (룬 새긴 쇠 — 날보다 조금 넓게)
    m.box("collar", (7.05, 3.0, 4.7), (8.95, 5.4, 11.3))
    m.box("gold", (6.95, 4.9, 4.5), (9.05, 5.5, 11.5))
    # 솟은 룬 홈 (불투명 · 속빛) — 날보다 두꺼워 양면에 솟음
    m.box("channel", (7.3, 5.5, 7.2), (8.7, 25.0, 8.8))
    # 반투명 빛 날: 아래 (넓게) · 가운데 · 위 · 마름모 칼끝 (X 둘레 45°)
    m.box("light", (7.45, 5.5, 4.4), (8.55, 11.0, 11.6))
    m.box("light", (7.45, 11.0, 4.6), (8.55, 17.0, 11.4))
    m.box("light", (7.46, 17.0, 4.9), (8.54, 23.0, 11.1))
    m.box("light", (7.47, 23.0, 5.3), (8.53, 27.6, 10.7))
    m.box("light", (7.47, 25.2, 5.6), (8.53, 30.0, 10.4), rot=("x", 45, (8, 27.6, 8)))
    # 흰 날 (양쪽 — 단마다) · 칼끝 테 (흰 마름모 — 빛 마름모보다 0.7 크게, 얇게)
    for (y0, y1, zi) in ((5.5, 11.0, 4.4), (11.0, 17.0, 4.6), (17.0, 23.0, 4.9), (23.0, 27.6, 5.3)):
        m.box("edge2", (7.62, y0, zi - 0.7), (8.38, y1, zi))
        m.box("edge2", (7.62, y0, 16 - zi), (8.38, y1, 16 - zi + 0.7))
    m.box("edge2", (7.62, 24.7, 5.1), (8.38, 30.5, 10.9), rot=("x", 45, (8, 27.6, 8)))
    return m


def rune_sword_v1():
    m = BModel("rune_sword", "룬 전용 검 (2026-11-12 사진 방식 — 빛나는 날 · 룬이 흐르는 홈 · 금 테 · 가죽 손잡이, 계열 색은 서버가 물들임)", d=1.5)
    weapon_mats(m)
    m.box("grip", (7.3, -7, 7.3), (8.7, 0, 8.7))
    for y in (-6.2, -3.6, -1.0):
        m.box("gold", (7.12, y, 7.12), (8.88, y + 0.6, 8.88))
    m.box("gold", (6.9, -9, 6.9), (9.1, -7, 9.1))
    m.box("gem", (7.35, -10.2, 7.35), (8.65, -9, 8.65))
    m.box("steel", (6.8, 0, 5.2), (9.2, 1.8, 10.8))
    m.box("gold", (6.7, 0.25, 4.6), (9.3, 1.55, 5.2))
    m.box("gold", (6.7, 0.25, 10.8), (9.3, 1.55, 11.4))
    m.box("steel", (7.05, 0.35, 1.2), (8.95, 1.45, 4.6))
    m.box("steel", (7.05, 0.35, 11.4), (8.95, 1.45, 14.8))
    m.box("gold", (7.0, 0.8, -0.2), (9.0, 2.8, 1.2))
    m.box("gold", (7.0, 0.8, 14.8), (9.0, 2.8, 16.2))
    m.box("gem", (6.55, 0.35, 7.25), (9.45, 1.45, 8.75))
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


def rune_hammer():
    m = BModel("rune_hammer", "룬 전용 망치 (2026-11-12 사진 방식 — 검은 쇠 머리 · 금 테 · 빛나는 때리는 면 · 룬 판, 계열 색은 서버가 물들임)", d=1.5)
    m.mat("head", Mat(bevel("#2C2C35", "#4E4E5E", "#16161C", speck=0.07)))
    m.mat("rune", Mat(rune_plate(), tint=0, glow=True, animated=True))
    m.mat("face", Mat(rune_plate(), tint=0, glow=True, animated=True))
    m.mat("gold", Mat(gold_paint()))
    m.mat("haft", Mat(bevel("#2A1C16", "#3E2A20", "#160E0B", grain="v")))
    m.mat("band", Mat(gem_paint(0.7), tint=0, glow=True, animated=True))
    m.mat("edge", Mat(edge_paint(), glow=True))
    m.box("haft", (7.2, -8, 7.2), (8.8, 17, 8.8))
    for y in (-6.0, -0.5, 5.0, 10.5):
        m.box("band", (7.0, y, 7.0), (9.0, y + 0.8, 9.0))
    m.box("gold", (6.8, -10, 6.8), (9.2, -8, 9.2))
    m.box("band", (7.3, -11, 7.3), (8.7, -10, 8.7))
    m.box("head", (4.5, 17.8, 1.6), (11.5, 25.2, 14.4))
    m.box("head", (5.3, 17.0, 1.6), (10.7, 26.0, 14.4))
    m.box("gold", (4.2, 16.6, 3.8), (11.8, 26.4, 4.8))
    m.box("gold", (4.2, 16.6, 11.2), (11.8, 26.4, 12.2))
    m.box("gold", (6.6, 16.4, 6.6), (9.4, 17.0, 9.4))
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


def ice_blade():
    m = BModel("ice_blade", "얼음 칼 (2026-11-12 사진 방식 — 얼음 날 · 흰 금 · 서리 손잡이)", d=1.5)
    m.mat("ice", Mat(ice(0.8), glow=True, animated=True))
    m.mat("core", Mat(ice(0.92, light=True, cell=30.0), glow=True, animated=True))
    m.mat("edge", Mat(edge_paint(), glow=True))
    m.mat("frost", Mat(bevel("#8FB4DA", "#C8E2F8", "#5E84B0", speck=0.12)))
    m.box("frost", (7.3, -6, 7.3), (8.7, 0, 8.7))
    m.box("core", (7.0, -8.5, 7.0), (9.0, -6, 9.0))
    m.box("core", (7.0, -8.5, 7.0), (9.0, -6, 9.0), rot=("y", 45.0, (8, -7.25, 8)))
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


# ------------------------------------------------------------------ 달 · 손바닥 · 주먹 · 그물 · 파도 · 물기둥

def stepped_ball(m, mat, c, R, A, B, Cc, K):
    """축마다 긴 막대 셋 (R · A · A) · 판 셋 (B · B · Cc) · 가운데 덩이 (K): 어느 쪽에서 봐도 세 단으로 깎인 공"""
    def cb(hx, hy, hz):
        m.box(mat, (c[0] - hx, c[1] - hy, c[2] - hz), (c[0] + hx, c[1] + hy, c[2] + hz))
    cb(R, A, A)
    cb(A, R, A)
    cb(A, A, R)
    cb(B, B, Cc)
    cb(B, Cc, B)
    cb(Cc, B, B)
    cb(K, K, K)


def round_ball(m, mat, c, R):
    """더 둥근 계단 공 (상자 16 — 모서리가 반지름의 1.1 ~ 1.16 배): 긴 막대 셋 · 판 여섯 · 덩이 여섯 · 가운데 하나"""
    from itertools import permutations
    seen = set()
    for t in ((1.0, 0.35, 0.35), (0.92, 0.6, 0.35), (0.8, 0.6, 0.6), (0.75, 0.75, 0.4), (0.65, 0.65, 0.65)):
        for q in permutations(t):
            if q in seen:
                continue
            seen.add(q)
            hx, hy, hz = (v * R for v in q)
            m.box(mat, (c[0] - hx, c[1] - hy, c[2] - hz), (c[0] + hx, c[1] + hy, c[2] + hz))


def moon():
    m = BModel("moon", "핏빛 달 (2026-11-12 사진 방식 — 계단 공 · 붉은 바다 얼룩 · 운석 구덩이)", d=2.5)
    m.mat("moon", Mat(moon_paint(), glow=True))
    round_ball(m, "moon", (8, 8, 8), 12.0)
    return m


def palm():
    """기의 손바닥 (붕권): 손바닥 가운데 (8, 5, 8) · 손목 ~ 손끝 약 26 · 앞 (장풍을 미는 면) = +Z — 빛나는 속 (기) + 반투명 겉 (기운 껍질 — 밝은 테),
    마디마다 빛 띠, 앞 · 뒤 (쓴 사람이 보는 손등) 모두 인"""
    m = BModel("palm", "기의 손바닥 (2026-11-12 사진 방식 2판 — 빛나는 속 · 반투명 기운 껍질 · 밝은 테 · 마디 빛 띠 · 앞뒤 인, 빛 = 계열 색)", d=2.0)
    m.mat("ki", Mat(glass(0.62, 0.98, alpha=0.9, edge="all", edge_alpha=1.0, streak="v", streak_alpha=0.8, density=0.8, gray=True, up_t=0.95, down_t=0.6),
                    tint=0, glow=True, animated=True))
    m.mat("aura", Mat(glass(0.7, 1.0, alpha=0.22, edge="all", edge_alpha=0.75, streak="v", streak_alpha=0.5, density=0.5, gray=True, up_t=0.9, down_t=0.7),
                      tint=0, glow=True, animated=True))
    m.mat("line", Mat(gem_paint(0.8), tint=1, glow=True, animated=True))
    m.mat("seal", Mat(seal_paint(), tint=1, glow=True, animated=True))

    def part(f, t, rot=None, shell=0.55):
        m.box("ki", f, t, rot=rot)
        m.box("aura", tuple(v - shell for v in f), tuple(v + shell for v in t), rot=rot)
    # 손바닥 (모서리를 한 단 깎음) · 손목 · 손목 빛 띠
    part((3.0, 0.4, 6.6), (13.0, 10.0, 9.4))
    m.box("ki", (3.6, -0.3, 6.9), (12.4, 10.6, 9.1))
    part((4.6, -5.0, 6.9), (11.4, 0.4, 9.1))
    m.box("line", (4.2, -1.4, 6.6), (11.8, -0.5, 9.4))
    # 손가락 넷 (세 마디 · 조금 벌림) — 마디마다 빛 띠
    for x0, hs, tilt in ((3.2, (3.0, 2.6, 2.0), 22.5), (5.8, (3.6, 3.0, 2.3), 0.0), (8.4, (3.5, 2.9, 2.2), 0.0), (11.0, (2.8, 2.3, 1.8), -22.5)):
        org = (x0 + 1.1, 10.0, 8.0)
        rot = ("z", tilt, org) if tilt else None
        y = 10.0
        for j, hh in enumerate(hs):
            inset = 0.12 * j
            part((x0 + inset, y, 6.8 + inset), (x0 + 2.2 - inset, y + hh, 9.2 - inset), rot=rot, shell=0.45)
            if j < 2:
                m.box("line", (x0 + inset - 0.1, y + hh - 0.3, 6.7 + inset), (x0 + 2.3 - inset, y + hh + 0.3, 9.3 - inset), rot=rot)
            y += hh
    # 엄지 (옆으로 45°)
    part((-1.5, 2.0, 6.9), (3.0, 4.4, 9.1), rot=("z", 45.0, (3.0, 3.0, 8.0)), shell=0.45)
    part((-5.0, 2.2, 7.05), (-1.5, 4.2, 8.95), rot=("z", 45.0, (3.0, 3.0, 8.0)), shell=0.45)
    # 인: 앞 (밀어내는 면) · 뒤 (쓴 사람이 보는 손등)
    m.box("seal", (3.9, 0.9, 9.95), (12.1, 9.1, 10.05), faces=["south"])
    m.box("seal", (4.6, 1.6, 5.95), (11.4, 8.4, 6.05), faces=["north"])
    return m


def fist():
    """거대한 주먹: 아래 (-Y) 로 내리찍음 — 치는 면 = 접은 손가락 첫 마디의 등 (가운데 (8, 0.5, 0.6)) · 손등 = +Z · 팔 위 끝 y 24.5"""
    m = BModel("fist", "거대한 주먹 (2026-11-12 사진 방식 — 접은 손가락 넷 · 엄지 · 손등 · 붕대 · 팔 보호대 · 룬 판, 빛 = 계열 색)", d=2.0)
    m.mat("skin", Mat(skin_glow(), tint=0, glow=True, animated=True))
    m.mat("knuckle", Mat(gem_paint(0.75), tint=1, glow=True, animated=True))
    m.mat("wrap", Mat(wrap_paint()))
    m.mat("bracer", Mat(stitched("#3A2E28", "#7A6656")))
    m.mat("gold", Mat(gold_paint()))
    m.mat("rune", Mat(rune_plate(), tint=1, glow=True, animated=True))
    xs = [(2.1, 4.7), (4.9, 7.6), (7.8, 10.5), (10.7, 13.1)]
    for i, (x0, x1) in enumerate(xs):
        lift = 0.4 if i == 3 else 0.0
        m.box("knuckle", (x0, 0.5 + lift, -1.6), (x1, 1.2 + lift, 2.9))           # 치는 면
        m.box("skin", (x0, 1.2 + lift, -1.6), (x1, 3.4 + lift, 2.9))              # 첫 마디
        m.box("skin", (x0 + 0.1, 1.4 + lift, -3.8), (x1 - 0.1, 5.8 + lift, -1.6))  # 접힌 둘째 마디 (앞 아래)
        m.box("skin", (x0 + 0.2, 4.6 + lift, -2.4), (x1 - 0.2, 6.6 + lift, -0.6))  # 손끝
        m.box("knuckle", (x0 + 0.2, 3.2 + lift, 1.4), (x1 - 0.2, 4.9 + lift, 3.5))  # 손등 마디뼈
    m.box("skin", (1.8, 3.4, -0.2), (13.4, 12.6, 4.2))   # 손등 · 손바닥
    m.box("skin", (2.2, 5.6, -2.6), (13.0, 12.2, -0.2))
    m.box("skin", (0.4, 5.2, -2.6), (3.0, 11.0, 1.4), rot=("z", -22.5, (1.7, 8.1, -0.6)))  # 엄지 뿌리
    m.box("skin", (1.6, 4.6, -5.0), (8.6, 7.0, -2.8))     # 엄지 (접힌 손가락 앞을 가로지름)
    m.box("wrap", (2.6, 11.8, -2.2), (13.0, 16.4, 3.8))
    m.box("gold", (2.4, 16.0, -2.5), (13.6, 16.9, 4.5))
    m.box("bracer", (2.7, 16.9, -2.3), (13.3, 22.6, 4.3))
    m.box("gold", (2.4, 22.6, -2.5), (13.6, 23.4, 4.5))
    m.box("bracer", (2.9, 23.4, -2.1), (13.1, 24.5, 4.1))
    m.box("rune", (6.6, 17.6, 4.3), (9.4, 21.9, 4.55), faces=["south"])
    m.box("rune", (6.6, 17.6, -2.55), (9.4, 21.9, -2.3), faces=["north"])
    return m


def net():
    """사냥 그물: 가운데 바닥 (8, 0, 8) · 지름 30 · 높이 12 — 엇갈려 돌린 층 넷 (구멍 뚫린 그물 판) · 빛 줄 (계열 색) · 추 여덟"""
    m = BModel("net", "사냥 그물 (2026-11-12 사진 방식 — 그물 판 층 · 꼰 줄 · 매듭 · 빛 줄 = 계열 색 · 추)", d=1.5)
    m.mat("net", Mat(net_paint(S=6, magic=True), animated=True, overlay_tint=0))
    m.mat("weight", Mat(bevel("#5A5C66", "#8A8C98", "#2E3036")))
    # 둥근 덮개 (반지름 15 → 꼭대기 4.8) 를 얇은 층 여섯으로 — 층마다 22.5° 씩 더 돌려 엇갈림
    for i, (hw, y0, y1) in enumerate(((15.0, 0.0, 2.2), (14.2, 2.2, 4.4), (12.8, 4.4, 6.6), (10.8, 6.6, 8.6), (8.0, 8.6, 10.4), (4.8, 10.4, 12.0))):
        rot = (0.0, 22.5, 45.0, -22.5, 0.0, 22.5)[i]
        r = ("y", rot, (8, (y0 + y1) / 2, 8)) if rot else None
        m.box("net", (8 - hw, y0, 8 - hw), (8 + hw, y1, 8 + hw), rot=r, faces=["north", "south", "east", "west", "up"])
    for i in range(8):
        a = i / 8 * math.pi * 2
        cx, cz = 8 + math.cos(a) * 14.2, 8 + math.sin(a) * 14.2
        m.box("weight", (cx - 0.9, 0.0, cz - 0.9), (cx + 0.9, 1.6, cz + 0.9))
    return m


def wave():
    """파도 (해일 · 심해의 군주): 밑 가운데 (8, 0, 1.5) · 너비 (x) 30 · 높이 18 · 앞 (말린 물마루) = +Z. 쓴 사람은 뒤 (-Z) 에서 봄 —
    뒤 비탈은 기울인 판 둘 (22.5° · 45°) 로 매끄럽게, 물마루 거품 · 앞으로 말려 내려오는 입술 (22.5° 씩 넷) · 밑의 거품. 반투명 물 + 흐르는 흰 물결 줄"""
    m = BModel("wave", "파도 (2026-11-12 사진 방식 2판 — 반투명 물 · 흐르는 물결 줄 · 기울인 뒤 비탈 · 말린 물마루 · 거품)", d=1.5)
    m.mat("deep", Mat(glass("#05306E", "#1663C4", alpha=0.82, edge=None, streak="h", streak_col="#9FD4FF", streak_alpha=0.7, density=1.0),
                      glow=True, animated=True))
    m.mat("water", Mat(glass("#0D5BC2", "#47A8F5", alpha=0.72, edge="top", edge_col="#D8F1FF", streak="h", streak_alpha=0.85, density=1.2),
                       glow=True, animated=True))
    m.mat("foam", Mat(foam2(), glow=True, animated=True))
    X0, X1 = -7.0, 23.0
    # 몸 (속 — 뒤에서 앞으로 높아짐)
    m.box("deep", (X0 + 0.4, 0, -12.5), (X1 - 0.4, 4.5, -3.0))
    m.box("deep", (X0 + 0.4, 0, -6.0), (X1 - 0.4, 9.5, 2.0))
    m.box("water", (X0, 0, -1.0), (X1, 14.5, 6.5))
    # 뒤 비탈: 기울인 판 (아래 22.5° · 위 45°) — 쓴 사람이 보는 면
    m.box("water", (X0, 0.0, -14.0), (X1, 2.4, 0.0), rot=("x", -22.5, (8, 1.2, -7.0)))
    m.box("water", (X0 + 0.2, 4.6, -4.6), (X1 - 0.2, 7.0, 6.6), rot=("x", -45.0, (8, 5.8, 1.0)))
    # 물마루 · 거품 꼭대기
    m.box("water", (X0 + 0.3, 12.4, 0.0), (X1 - 0.3, 17.4, 8.0))
    m.box("foam", (X0 + 0.5, 17.0, 0.6), (X1 - 0.5, 18.0, 7.6))
    # 앞으로 말려 내려오는 입술 (22.5° 씩)
    m.box("water", (X0 + 0.8, 13.6, 6.8), (X1 - 0.8, 17.0, 11.4), rot=("x", 22.5, (8, 15.3, 9.1)))
    m.box("water", (X0 + 1.2, 10.6, 10.0), (X1 - 1.2, 14.4, 13.6), rot=("x", 45.0, (8, 12.5, 11.8)))
    m.box("foam", (X0 + 1.6, 7.0, 12.4), (X1 - 1.6, 11.0, 15.4), rot=("x", -22.5, (8, 9.0, 13.9)))
    m.box("foam", (X0 + 2.0, 4.6, 12.0), (X1 - 2.0, 7.2, 14.6))
    # 밑의 물 · 거품 (앞으로 퍼짐)
    m.box("water", (X0, 0, 6.5), (X1, 1.8, 15.5))
    m.box("foam", (X0 + 0.8, 1.6, 8.5), (X1 - 0.8, 2.3, 17.5))
    return m


def water_column():
    """물기둥 (소용돌이 감옥 · 심해의 군주): 밑 가운데 (8, 0, 7.5) · 높이 28 — 마디마다 22.5° 씩 비튼 반투명 물 (사선으로 오르는 흰 줄) ·
    가운데 밝은 물줄기 · 밑 거품 고리 · 꼭대기 거품 왕관 · 밖으로 튀는 물보라"""
    m = BModel("water_column", "물기둥 (2026-11-12 사진 방식 2판 — 비튼 반투명 물 · 오르는 흰 줄 · 밝은 속 물줄기 · 거품 왕관)", d=1.5)
    m.mat("water", Mat(glass("#0B4FAE", "#4FB0F6", alpha=0.6, edge=None, streak="d", streak_alpha=0.9, density=1.4, speed=1.5),
                       glow=True, animated=True))
    m.mat("core", Mat(glass("#5CC0FF", "#D6F1FF", alpha=0.85, edge=None, streak="v", streak_alpha=0.9, density=1.0, speed=2.0),
                      glow=True, animated=True))
    m.mat("foam", Mat(foam2(), glow=True, animated=True))
    cx, cz = 8.0, 7.5
    segs = ((0.0, 3.2, 6.2), (3.0, 6.6, 5.2), (6.4, 10.0, 4.5), (9.8, 13.4, 4.1), (13.2, 16.8, 4.1), (16.6, 20.2, 4.5), (20.0, 23.4, 5.3), (23.2, 25.4, 6.3))
    for i, (y0, y1, r) in enumerate(segs):
        ang = (0.0, 22.5, 45.0, -22.5)[i % 4]
        rot = ("y", ang, (cx, (y0 + y1) / 2, cz)) if ang else None
        m.box("water", (cx - r, y0, cz - r), (cx + r, y1, cz + r), rot=rot)
    m.box("core", (cx - 1.8, 0.0, cz - 1.8), (cx + 1.8, 26.0, cz + 1.8), rot=("y", 45.0, (cx, 13.0, cz)))
    # 밑 거품 고리 (낮은 판 넷 — 엇갈려 돌림)
    for ang in (0.0, 22.5, 45.0, -22.5):
        rot = ("y", ang, (cx, 0.5, cz)) if ang else None
        m.box("foam", (cx - 7.2, 0.0, cz - 2.2), (cx + 7.2, 1.0, cz + 2.2), rot=rot)
    # 꼭대기 거품 왕관 · 밖으로 튀는 물보라
    m.box("foam", (cx - 5.6, 25.2, cz - 5.6), (cx + 5.6, 26.4, cz + 5.6), rot=("y", 22.5, (cx, 25.8, cz)))
    for (dx, dz, ax, sg) in ((1, 0, "z", -1), (-1, 0, "z", 1), (0, 1, "x", 1), (0, -1, "x", -1)):
        px, pz = cx + dx * 5.4, cz + dz * 5.4
        m.box("foam", (px - 1.0, 25.0, pz - 1.0), (px + 1.0, 29.5, pz + 1.0), rot=(ax, 22.5 * sg, (px, 25.0, pz)))
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        px, pz = cx + dx * 3.9, cz + dz * 3.9
        m.box("foam", (px - 0.9, 25.4, pz - 0.9), (px + 0.9, 28.0, pz + 0.9), rot=("y", 45.0, (px, 26.7, pz)))
    return m


# ------------------------------------------------------------------ 2026-11-12 직업 스킬: 하늘의 단두대 (엑시큐셔너 길로틴 오브 헤븐)

def lunette_paint():
    """목판 (단두대 아래 — 짙은 나무 · 가운데 둥근 목 구멍): 구멍 안은 검게, 테두리는 빛 겹 (물들임 1 — 해 질 녘 빛)"""
    wood = bevel("#3A2620", "#5A3E32", "#1C120E", grain="h", speck=0.05)

    def paint(w, h, face, rng, frame):
        base, _ = wood(w, h, face, rng, frame)
        if face not in ("north", "south") or w < 6 or h < 4:
            return base, None
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.sqrt((xs - cx) ** 2 + ((ys - cy) * 1.0) ** 2)
        R = min(w, h) * 0.36
        hole = r <= R
        rim = (r > R) & (r <= R + 1.2)
        base[hole, :3] = C("#0A0608")
        ov = np.zeros((h, w, 4))
        ov[rim] = (1.0, 1.0, 1.0, 0.85 + 0.15 * pl)
        return base, ov
    return paint


def groove_paint():
    """칼날이 미끄러지는 홈 (회색 — 물들임 1): 위아래로 흐르는 빛 띠 (맥동)"""
    def paint(w, h, face, rng, frame):
        ys = np.arange(h)[:, None] * np.ones((1, w))
        band = ((ys - frame * max(1, h // FRAMES)) % max(5, h // 3)) < max(2, h // 8)
        v = np.where(band, 1.0, 0.7)
        img = np.repeat(v[..., None], 3, -1)
        return np.concatenate([img, np.ones((h, w, 1))], -1), None
    return paint


def steel_paint():
    """단두대 칼날 (짙은 강철): 위 밝은 띠 · 아래로 짙어짐 · 사선 반짝임"""
    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        t = ys / max(1, h - 1)
        base = C("#8E99AA") * (1.08 - 0.32 * t)[..., None]
        glint = ((xs + ys + int(rng.integers(0, 7))) % 11) == 0
        base[glint] = np.minimum(1.0, base[glint] * 1.35)
        if h >= 3:
            base[0, :] = C("#D6E0EC")
        if w >= 3:
            base[:, 0] = base[:, 0] * 0.6 + C("#C4CEDB") * 0.4
            base[:, -1] = base[:, -1] * 0.6 + C("#4A5260") * 0.4
        return np.concatenate([np.clip(base, 0, 1), np.ones((h, w, 1))], -1), None
    return paint


def guillotine():
    """하늘의 단두대 틀 (밑 가운데 (8, −16, 8) · 높이 48): 짙은 나무 기둥 둘 (앞뒤에 빛나는 룬 판 · 안쪽 홈) · 금 테를 두른 위 들보 · 해 무늬 · 목판 (둥근 목 구멍) ·
    밑 받침. 칼날 (guillotine_blade) 은 따로 — 같은 자리 · 크기에 두고 아래로 GUILLOTINE_DROP 만큼 옮기면 목판에 닿음.
    물들임 0 = 룬 · 해 (해 질 녘 빛), 1 = 홈 · 목 구멍 테 (보랏빛)"""
    m = BModel("guillotine", "하늘의 단두대 틀 (2026-11-12 — 짙은 나무 · 금 테 · 해 질 녘 룬, 칼날은 guillotine_blade)", d=1.5)
    m.mat("wood", Mat(bevel("#3A2620", "#5A3E32", "#1C120E", grain="v", speck=0.05)))
    m.mat("beam", Mat(bevel("#33211B", "#54392D", "#180F0C", grain="h", speck=0.05)))
    m.mat("gold", Mat(gold_paint()))
    m.mat("iron", Mat(bevel("#2C2C35", "#4E4E5E", "#16161C", speck=0.07)))
    m.mat("rune", Mat(rune_plate(), tint=0, glow=True, animated=True))
    m.mat("sun", Mat(gem_paint(0.62), tint=0, glow=True, animated=True))
    m.mat("groove", Mat(groove_paint(), tint=1, glow=True, animated=True))
    m.mat("lunette", Mat(lunette_paint(), animated=True, overlay_tint=1))
    # 밑 받침 (계단 둘) · 금 띠
    m.box("beam", (-6, -16, 2.5), (22, -14, 13.5))
    m.box("beam", (-4, -14, 4), (20, -13, 12))
    m.box("gold", (-6.2, -14.4, 2.3), (22.2, -13.9, 2.8))
    m.box("gold", (-6.2, -14.4, 13.2), (22.2, -13.9, 13.7))
    # 기둥 둘 (안쪽 x 1 ~ 15 — 칼날이 지나는 틈) · 쇠 받침 · 앞뒤 룬 판 · 안쪽 빛 홈
    for x0, x1 in ((-3.0, 1.0), (15.0, 19.0)):
        m.box("wood", (x0, -13, 6), (x1, 26, 10))
        m.box("iron", (x0 - 0.5, -13, 5.5), (x1 + 0.5, -10, 10.5))
        m.box("iron", (x0 - 0.3, 12, 5.7), (x1 + 0.3, 13.2, 10.3))
        m.box("rune", (x0 + 0.6, -8, 5.7), (x1 - 0.6, 11, 6.0))
        m.box("rune", (x0 + 0.6, 14, 5.7), (x1 - 0.6, 24, 6.0))
        m.box("rune", (x0 + 0.6, -8, 10.0), (x1 - 0.6, 11, 10.3))
        m.box("rune", (x0 + 0.6, 14, 10.0), (x1 - 0.6, 24, 10.3))
    m.box("groove", (0.95, -9, 7.4), (1.3, 25.5, 8.6))
    m.box("groove", (14.7, -9, 7.4), (15.05, 25.5, 8.6))
    # 위 들보 · 금 끝 · 가운데 해 무늬 (위로 솟은 빛살)
    m.box("beam", (-5, 26, 5), (21, 29.5, 11))
    m.box("gold", (-5.6, 25.6, 4.6), (-3.4, 29.9, 11.4))
    m.box("gold", (19.4, 25.6, 4.6), (21.6, 29.9, 11.4))
    m.box("gold", (-5, 29.5, 7.2), (21, 30.0, 8.8))
    m.box("sun", (5.5, 29.6, 7.4), (10.5, 32.0, 8.6))
    m.box("sun", (3.0, 29.8, 7.7), (4.6, 31.2, 8.3))
    m.box("sun", (11.4, 29.8, 7.7), (13.0, 31.2, 8.3))
    # 목판 (아래 판 — 둥근 목 구멍 · 위 판) · 쇠 띠
    m.box("lunette", (1, -9, 6.8), (15, -1, 9.2))
    m.box("iron", (1, -1, 6.6), (15, -0.2, 9.4))
    m.box("iron", (1, -9.8, 6.6), (15, -9, 9.4))
    return m


def guillotine_blade():
    """단두대 칼날 (틀과 같은 자리 · 크기 — 올라간 자리): 쇠 추 (금 리벳) · 강철 날 (오른쪽이 높은 비스듬한 날 — 계단 넷 위에 22.5° 빛나는 날 끝) ·
    날 끝 = 물들임 0 (해 질 녘 빛), 추의 룬 = 물들임 1"""
    m = BModel("guillotine_blade", "단두대 칼날 (2026-11-12 — 쇠 추 · 강철 날 · 빛나는 비스듬한 날 끝)", d=1.5)
    m.mat("iron", Mat(bevel("#2C2C35", "#4E4E5E", "#16161C", speck=0.07)))
    m.mat("gold", Mat(gold_paint()))
    m.mat("steel", Mat(steel_paint()))
    m.mat("edge", Mat(glow_blade(), tint=0, glow=True, animated=True))
    m.mat("rune", Mat(rune_plate(), tint=1, glow=True, animated=True))
    # 쇠 추 (홈 사이 — 틀 기둥 안쪽 1 ~ 15)
    m.box("iron", (1.4, 20.5, 6.9), (14.6, 25.0, 9.1))
    m.box("gold", (1.2, 24.6, 6.7), (14.8, 25.2, 9.3))
    m.box("rune", (4.0, 21.3, 6.6), (12.0, 24.2, 6.9))
    m.box("rune", (4.0, 21.3, 9.1), (12.0, 24.2, 9.4))
    for x in (2.2, 13.0):
        m.box("gold", (x, 22.0, 6.6), (x + 0.8, 22.8, 9.4))
    # 날 (계단 여덟 — 오른쪽으로 갈수록 날 끝이 높음: 계단 밑 = 날 끝 줄의 그 칸 오른쪽 높이 − 0.2 → 날 끝 막대가 덮음)
    k = math.tan(math.radians(22.5))
    for i in range(8):
        x0 = 1.6 + i * 1.6
        x1 = x0 + 1.6
        m.box("steel", (x0, 10.1 + (x1 - 1.6) * k - 0.2, 7.6), (x1, 20.5, 8.4))
    # 비스듬한 날 끝 (22.5° — 왼쪽 아래 (1.6, 10.1) 에서 오른쪽 위로): 길이 12.8 / cos 22.5° · 두께 1.4
    L = 12.8 / math.cos(math.radians(22.5))
    m.box("edge", (1.6, 9.4, 7.45), (1.6 + L, 10.8, 8.55), rot=("z", 22.5, (1.6, 10.1, 8.0)))
    return m


def _seg(m, mat, a, angle, L, t, h, side, x0=8.0):
    """2026-11-13 활 · 낫의 마디 하나: a = (z, y) 에서 바깥 (side +1 = +Z, −1 = −Z) 으로 수평에서 angle 도 (− = 아래 · 22.5 단위) 기운 길이 L ·
    두께 t (x) · 높이 h (y) 상자 → 끝 (z, y)"""
    az, ay = a
    if side > 0:
        f, t2 = (x0 - t / 2, ay - h / 2, az), (x0 + t / 2, ay + h / 2, az + L)
        rho = -angle
    else:
        f, t2 = (x0 - t / 2, ay - h / 2, az - L), (x0 + t / 2, ay + h / 2, az)
        rho = angle
    m.box(mat, f, t2, rot=("x", rho, (x0, ay, az)) if rho else None)
    r = math.radians(angle)
    return (az + side * L * math.cos(r), ay + L * math.sin(r))


def rune_arrow():
    """2026-11-13 룬의 거대한 빛 화살 (저격 · 천궁 — 칼끝 +Y): 빛나는 속대 (속빛) 를 감싼 반투명 빛 대 · 안으로 기운 두 판의 화살촉 (빛) + 가운데 마름모 (속빛) ·
    흰 날 · 뒤로 젖힌 가시 둘 · 꼬리의 빛 깃 넷 (+ 자 — 뒤로 젖힘) · 보석 오늬. 촉 끝 y 31 · 오늬 끝 y −14 (길이 45)"""
    m = BModel("rune_arrow", "룬 빛 화살 (2026-11-13 — 빛나는 속대 · 기운 두 판의 촉 · 빛 깃 넷 · 보석 오늬, 계열 색은 서버가 물들임)", d=2.5)
    weapon_mats(m)
    m.mat("light", Mat(light_blade(0.72), tint=0, glow=True, animated=True))
    m.mat("edge2", Mat(edge_light(0.9), glow=True))
    m.mat("core", Mat(gem_paint(0.7), tint=1, glow=True, animated=True))
    m.mat("channel", Mat(rune_channel(), tint=1, glow=True, animated=True))
    # 오늬 (보석) · 금 고리
    m.box("gem", (7.1, -14.0, 7.1), (8.9, -12.2, 8.9), rot=("y", 45, (8, -13.1, 8)))
    m.box("gold", (7.25, -12.4, 7.25), (8.75, -11.4, 8.75))
    # 속대 (불투명 · 속빛) — 촉 안까지
    m.box("channel", (7.55, -11.4, 7.55), (8.45, 26.0, 8.45))
    for y in (-3.0, 17.2):
        m.box("gold", (7.3, y, 7.3), (8.7, y + 0.8, 8.7))
    # 가운데 마름모 (촉의 속빛)
    m.box("core", (7.45, 22.2, 5.8), (8.55, 26.6, 10.2), rot=("x", 45, (8, 24.4, 8)))
    # 반투명 빛 대
    m.box("light", (7.3, -11.4, 7.3), (8.7, 21.5, 8.7))
    # 촉: 안으로 기운 두 판 (위에서 만나 뾰족) · 흰 날 · 뒤로 젖힌 가시
    for side in (-1, 1):
        zc = 8 + side * 3.1
        rho = 22.5 if side < 0 else -22.5  # 위 끝이 가운데로 (X 둘레 + = 위가 +Z 쪽)
        m.box("light", (7.5, 20.8, zc - 1.3), (8.5, 31.0, zc + 1.3), rot=("x", rho, (8, 20.8, zc)))
        ze = zc + side * 1.3
        m.box("edge2", (7.62, 20.8, ze - 0.35), (8.38, 31.2, ze + 0.35), rot=("x", rho, (8, 20.8, zc)))
        bz = 8 + side * 4.4
        m.box("edge2", (7.6, 18.6, bz - 0.5), (8.4, 22.0, bz + 0.5), rot=("x", 45 if side < 0 else -45, (8, 21.0, bz)))
    # 빛 깃 넷 (+ 자 — 꼬리 쪽 끝이 바깥으로 22.5° 젖힘)
    for side in (-1, 1):
        zc = 8 + side * 2.2
        rz = 22.5 if side < 0 else -22.5
        m.box("light", (7.75, -11.0, zc - 1.6), (8.25, -2.5, zc + 1.6), rot=("x", rz, (8, -2.5, 8 + side * 0.8)))
        m.box("edge2", (7.82, -11.6, zc + side * 1.4 - 0.25), (8.18, -2.5, zc + side * 1.4 + 0.25), rot=("x", rz, (8, -2.5, 8 + side * 0.8)))
        xc = 8 + side * 2.2
        m.box("light", (xc - 1.6, -11.0, 7.75), (xc + 1.6, -2.5, 8.25), rot=("z", -rz, (8 + side * 0.8, -2.5, 8)))
    return m


def sky_bow():
    """2026-11-13 천궁의 하늘의 활 (쏘는 쪽 +Y · 활 면 = YZ · 앞 (보는 사람 쪽) = ±X): 금 활대 (마디 셋 — 뒤로 휘다가 끝이 앞으로 젖혀짐) · 활대의 룬 홈 (빛) ·
    손잡이의 해 원반 (빛) 과 보석 (속빛) · 활대 위의 빛 깃털 날개 · 끝의 빛 결정. 시위는 그리지 않음 (서버가 2D 빛줄로). 가운데 (8, 8, 8) · 끝 z −14 / 30"""
    m = BModel("sky_bow", "하늘의 활 (2026-11-13 — 금 활대 · 룬 홈 · 해 원반 · 빛 깃털 날개 · 끝의 결정, 계열 색은 서버가 물들임)", d=2.0)
    weapon_mats(m)
    m.mat("light", Mat(light_blade(0.75), tint=0, glow=True, animated=True))
    m.mat("edge2", Mat(edge_light(0.9), glow=True))
    m.mat("core", Mat(gem_paint(0.62), tint=1, glow=True, animated=True))
    m.mat("rune", Mat(rune_plate(), tint=0, glow=True, animated=True))
    # 손잡이: 가죽 · 금 테 · 해 원반 (빛 — 뒤) · 가운데 보석 (속빛 — 앞뒤로 솟음)
    m.box("grip", (7.0, 4.0, 6.6), (9.0, 12.0, 9.4))
    m.box("gold", (6.8, 11.2, 6.2), (9.2, 12.6, 9.8))
    m.box("gold", (6.8, 3.4, 6.2), (9.2, 4.8, 9.8))
    m.box("light", (7.4, 3.0, 3.6), (8.6, 13.0, 12.4), rot=("x", 45, (8, 8, 8)))
    m.box("core", (6.4, 6.8, 6.8), (9.6, 9.2, 9.2), rot=("x", 45, (8, 8, 8)))
    for side in (-1, 1):
        # 활대: 손잡이 위에서 바깥으로 — 평평 → 뒤로 (아래) 휨 → 더 휨 → 끝이 앞 (위) 으로 젖혀짐 (∩ 모양 · 시위는 아래)
        a0 = (8 + side * 1.4, 11.2)
        segs = ((0.0, 4.5, 2.0, 2.6), (-22.5, 6.0, 1.9, 2.4), (-45.0, 5.4, 1.8, 2.2), (-22.5, 3.2, 1.7, 2.0), (22.5, 3.6, 1.6, 1.8))
        a = a0
        pts = [a]
        for ang, L, t, h in segs:
            a = _seg(m, "gold", a, ang, L, t, h, side)
            pts.append(a)
        # 룬 홈 (활대 앞뒤의 빛 띠 — 처음 넷)
        for (ang, L, t, h), p0 in zip(segs[:4], pts[:4]):
            _seg(m, "rune", p0, ang, L, t + 0.3, h * 0.38, side)
        # 끝의 빛 결정 (마름모) · 흰 가시
        tz, ty = a
        m.box("core", (7.3, ty - 1.1, tz - 1.1), (8.7, ty + 1.1, tz + 1.1), rot=("x", 45, (8, ty, tz)))
        m.box("edge2", (7.55, ty, tz - 0.25), (8.45, ty + 3.4, tz + 0.25), rot=("x", -22.5 if side > 0 else 22.5, (8, ty, tz)))
        # 빛 깃털 날개 (손잡이 둘레에서 위 바깥으로 펼침 — 바깥 깃이 길게)
        for bz, by, L, ang in ((8 + side * 2.6, 12.2, 5.5, 22.5), (8 + side * 4.4, 12.0, 7.5, 45.0), (8 + side * 6.4, 11.4, 8.5, 45.0),
                               (8 + side * 8.6, 10.6, 7.0, 45.0)):
            rot = ang if side > 0 else -ang  # 위 끝이 바깥으로 (X 둘레 + = 위가 +Z 쪽)
            m.box("light", (7.7, by, bz - 0.95), (8.3, by + L, bz + 0.95), rot=("x", rot, (8, by, bz)))
            m.box("edge2", (7.78, by + 0.3, bz + side * 0.95 - 0.2), (8.22, by + L, bz + side * 0.95 + 0.2), rot=("x", rot, (8, by, bz)))
    return m


def blood_steel():
    """낫 날 (검붉은 강철): 등 쪽 어둡게 · 날 쪽 밝게 · 핏빛 룬 줄 (빛이 아님 — 날 끝 빛은 따로)"""
    def paint(w, h, face, rng, frame):
        ys, xs = np.mgrid[0:h, 0:w]
        t = ys / max(1, h - 1)
        base = C("#3A2A30") * (0.85 + 0.35 * t)[..., None]
        glint = ((xs * 2 + ys + int(rng.integers(0, 5))) % 13) == 0
        base[glint] = np.minimum(1.0, base[glint] * 1.5)
        if h >= 4:
            base[0, :] = C("#1C1216")
            mid = h // 2
            base[mid, ::2] = C("#9A1C24")
        if w >= 3:
            base[:, 0] = base[:, 0] * 0.7
        return np.concatenate([np.clip(base, 0, 1), np.ones((h, w, 1))], -1), None
    return paint


def rune_scythe():
    """2026-11-13 피의 낙인의 핏빛 낫 (자루 +Y — 자루 가운데 x 8 · z 12, 날은 자루 끝에서 −Z 로 뻗어 아래로 휨): 검은 나무 자루 · 핏빛 띠 (빛) · 금 고리 ·
    날 이음의 속빛 보석 · 마디 넷의 검붉은 날 (등은 어둡게) · 날 아래쪽의 빛나는 날 (빛) · 뾰족한 날 끝. 자루 끝 y −15 · 날 위 y ~31 · 날 끝 z ~−12"""
    m = BModel("rune_scythe", "핏빛 낫 (2026-11-13 — 검은 자루 · 핏빛 띠 · 금 고리 · 마디 넷의 검붉은 날 · 빛나는 날, 계열 색은 서버가 물들임)", d=2.0)
    weapon_mats(m)
    m.mat("haft", Mat(bevel("#22181A", "#3A2A2C", "#100A0B", grain="v")))
    m.mat("band", Mat(gem_paint(0.7), tint=0, glow=True, animated=True))
    m.mat("bsteel", Mat(blood_steel()))
    m.mat("edge2", Mat(edge_light(0.95), tint=0, glow=True))
    m.mat("core", Mat(gem_paint(0.62), tint=1, glow=True, animated=True))
    zc = 12.0
    m.box("haft", (7.25, -14.0, zc - 0.75), (8.75, 25.0, zc + 0.75))
    for y in (-11.0, -2.0, 7.0, 15.0):
        m.box("band", (7.05, y, zc - 0.95), (8.95, y + 0.9, zc + 0.95))
    m.box("gold", (6.9, -15.0, zc - 1.1), (9.1, -13.6, zc + 1.1))
    m.box("core", (7.2, -16.0, zc - 0.8), (8.8, -14.8, zc + 0.8), rot=("y", 45, (8, -15.4, zc)))
    # 날 이음: 금 덩이 · 속빛 보석 (앞뒤)
    m.box("gold", (6.7, 23.0, zc - 1.6), (9.3, 27.0, zc + 1.6))
    m.box("core", (6.4, 23.8, zc - 0.8), (9.6, 26.2, zc + 0.8), rot=("x", 45, (8, 25.0, zc)))
    # 날: 마디 넷 (−Z 로 뻗어 아래로 휨 — 높이가 줄어듦) · 아래쪽 빛나는 날
    a = (zc - 1.4, 26.6)
    for ang, L, h in ((22.5, 6.0, 6.4), (0.0, 7.0, 5.6), (-22.5, 7.0, 4.4), (-45.0, 5.5, 3.2)):
        a0 = a
        a = _seg(m, "bsteel", a, ang, L, 1.2, h, -1)
        _seg(m, "edge2", (a0[0], a0[1] - h / 2 + 0.3), ang, L, 1.5, 0.7, -1)
    tz, ty = a
    m.box("edge2", (7.4, ty - 1.3, tz - 1.3), (8.6, ty + 1.3, tz + 1.3), rot=("x", 45, (8, ty, tz)))
    return m


def boulder():
    """2026-11-13 떠오르는 바위 덩이 (중력 역전 · 블랙홀 · 초인 각성): 가운데 (8, 8, 8) · 지름 약 16 — 크기가 다른 돌 덩이 넷을 엇갈려 겹친 울퉁불퉁한 바위,
    금 사이 빛 = 계열 색 (tint 0 — 바위 가시와 같은 돌 · 빛 겹)"""
    m = BModel("boulder", "떠오르는 바위 (2026-11-13 — 엇갈린 돌 덩이 넷 · 틈의 빛 = 계열 색)", d=2.0)
    m.mat("rock", Mat(cobble(STONE, cell=18, glow_chains=1.6), animated=True, overlay_tint=0))
    m.mat("dark", Mat(cobble(("#221C19", "#3F3631", "#51463F", "#665850", "#7A6A5F"), cell=16, glow_chains=1.0), animated=True, overlay_tint=0))
    m.mat("chip", Mat(cobble(STONE, cell=12)))
    m.box("rock", (1.5, 2.5, 2.0), (14.5, 13.0, 14.0), rot=("y", 22.5, (8, 8, 8)))
    m.box("dark", (3.0, 0.5, 3.5), (12.5, 9.0, 13.0), rot=("x", 22.5, (8, 5, 8)))
    m.box("rock", (4.0, 8.0, 3.0), (12.0, 15.5, 11.0), rot=("z", -22.5, (8, 12, 7)))
    m.box("chip", (10.0, 4.0, 10.0), (15.5, 9.5, 15.5), rot=("y", 45.0, (12.75, 6.75, 12.75)))
    m.box("chip", (0.5, 6.0, 5.0), (4.5, 10.0, 9.0), rot=("x", -22.5, (2.5, 8, 7)))
    return m


def void_paint():
    """공허의 핵 (거의 검정 · 보랏빛 반짝임 · 테두리만 옅게 빛남 — 빛 겹은 계열 색)"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        img = np.zeros((h, w, 3)) + np.array([0.03, 0.01, 0.05])
        sp = rng.random((h, w)) < 0.04
        img[sp] = np.array([0.35, 0.18, 0.5]) * (0.6 + 0.4 * pl)
        base = np.concatenate([img, np.ones((h, w, 1))], -1)
        ov = np.zeros((h, w, 4))
        e = (xs == 0) | (ys == 0) | (xs == w - 1) | (ys == h - 1)
        ov[e] = (0.9, 0.9, 0.9, 0.55 + 0.25 * pl)
        return base, ov
    return paint


def void_orb():
    """2026-11-13 블랙홀의 공허 핵: 가운데 (8, 8, 8) · 지름 약 19 — 계단 공 (운석과 같은 꼴) 의 검은 핵 · 면 테두리의 옅은 빛 (tint 0 — 계열 색)"""
    m = BModel("void_orb", "공허의 핵 (2026-11-13 — 검은 계단 공 · 면 테두리 빛 = 계열 색)", d=1.5)
    m.mat("void", Mat(void_paint(), animated=True, overlay_tint=0))
    R, A, B, Cc, K = 9.5, 5.0, 7.0, 5.0, 6.2

    def cb(hx, hy, hz):
        m.box("void", (8 - hx, 8 - hy, 8 - hz), (8 + hx, 8 + hy, 8 + hz))
    cb(R, A, A)
    cb(A, R, A)
    cb(A, A, R)
    cb(B, B, Cc)
    cb(B, Cc, B)
    cb(Cc, B, B)
    return m


def ring_segs(m, mat, c, R, w, h, n=16, faces=None):
    """2026-11-13 가로 고리 (XZ 면): 가운데 c · 반지름 R — 접선으로 누운 상자 n 개 (22.5° 단위: 90° 를 넘는 접선은 가로 · 세로를 바꿔 같은 꼴)"""
    cx, cy, cz = c
    L = 2 * math.pi * R / n * 1.08
    for k in range(n):
        th = k * 360.0 / n
        r = math.radians(th)
        px, pz = cx + R * math.cos(r), cz + R * math.sin(r)
        tang = (th + 90.0) % 180.0  # 접선 방향 (X 에서)
        if tang > 90.0 + 1e-6:
            tang -= 180.0
        if abs(tang) <= 45.0 + 1e-6:
            f, t = (px - L / 2, cy - h / 2, pz - w / 2), (px + L / 2, cy + h / 2, pz + w / 2)
            ang = -tang
        else:
            f, t = (px - w / 2, cy - h / 2, pz - L / 2), (px + w / 2, cy + h / 2, pz + L / 2)
            ang = -(tang - 90.0 if tang > 0 else tang + 90.0)
        m.box(mat, f, t, rot=("y", ang, (px, cy, pz)) if abs(ang) > 1e-6 else None, faces=faces)


def halo():
    """2026-11-13 머리 위 빛 고리 (대천사 강림 · 초인 각성): 가운데 (8, 8, 8) · 지름 약 18 — 금빛 고리 (빛 — 계열 색) 를 감싼 반투명 빛 테 · 안쪽 흰 고리 (속빛) ·
    금 고리 위의 작은 빛 보석 넷"""
    m = BModel("halo", "머리 위 빛 고리 (2026-11-13 — 금 고리 · 반투명 빛 테 · 흰 속 고리 · 빛 보석 넷, 계열 색은 서버가 물들임)", d=2.0)
    weapon_mats(m)
    m.mat("light", Mat(light_blade(0.6), tint=0, glow=True, animated=True))
    m.mat("core", Mat(gem_paint(0.75), tint=1, glow=True, animated=True))
    ring_segs(m, "core", (8, 8, 8), 7.6, 0.9, 0.9)
    ring_segs(m, "light", (8, 8, 8), 7.6, 2.0, 1.6)
    ring_segs(m, "gold", (8, 8, 8), 9.0, 0.7, 0.7)
    for k in range(4):
        r = math.radians(k * 90 + 45)
        px, pz = 8 + 9.0 * math.cos(r), 8 + 9.0 * math.sin(r)
        m.box("core", (px - 0.9, 7.1, pz - 0.9), (px + 0.9, 8.9, pz + 0.9), rot=("y", 45, (px, 8, pz)))
    return m


def hex_paint(fill=0.2):
    """2026-11-13 빛의 육각 판 (회색 — 계열 색 물들임 · 거의 투명): 꼭짓점이 위아래인 육각 · 밝은 테 (두 겹) · 안쪽 옅은 육각 테 둘 · 꼭짓점으로 뻗는 줄 ·
    위로 흐르는 빛 띠 (프레임마다)"""
    def paint(w, h, face, rng, frame):
        pl = pulse(frame)
        ys, xs = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        R = min(h / 2, w / 2 / 0.866) - 0.3
        dx, dy = np.abs(xs - cx), np.abs(ys - cy)
        # 육각까지의 거리 (안쪽 +)
        e1 = R * 0.866 - dx
        e2 = (R - dy) * 0.866 - dx * 0.5
        dist = np.minimum(e1, e2)
        inside = dist >= 0
        v = np.full((h, w), 0.86)
        a = np.where(inside, fill, 0.0)
        # 안쪽 육각 테 둘 (옅게) · 가운데에서 꼭짓점 여섯으로 뻗는 옅은 줄
        inner = inside & (((dist >= R * 0.3) & (dist < R * 0.3 + 0.7)) | ((dist >= R * 0.55) & (dist < R * 0.55 + 0.7)))
        a = np.where(inner, fill + 0.2, a)
        ang = np.degrees(np.arctan2(ys - cy, xs - cx)) % 60.0
        spoke = inside & (np.abs(ang - 30.0) < 2.2 * 16.0 / np.maximum(4.0, np.hypot(xs - cx, ys - cy) + 4.0))
        a = np.where(spoke & (dist > 1.6), np.maximum(a, fill + 0.12), a)
        # 흐르는 빛 띠
        band = ((ys / h + frame / FRAMES) % 1.0) < 0.14
        v = np.where(inside & band, 1.0, v)
        a = np.where(inside & band, np.maximum(a, fill + 0.25), a)
        # 테 두 겹
        rim = inside & (dist < 1.6)
        rim2 = inside & (dist >= 3.0) & (dist < 3.8)
        v = np.where(rim | rim2, 1.0, v)
        a = np.where(rim, 0.98, a)
        a = np.where(rim2, 0.6 + 0.2 * pl, a)
        img = np.repeat(np.clip(v, 0, 1)[..., None], 3, -1)
        return np.concatenate([img, np.clip(a, 0, 1)[..., None]], -1), None
    return paint


def hex_panel():
    """2026-11-13 불굴의 방벽의 빛의 육각 판: 가운데 (8, 8, 8) · 높이 16 (꼭짓점 위아래) · 너비 약 13.9 · 두께 0.4 — 앞뒤 면만 (육각 그림),
    가운데 금 테 보석 (속빛) 이 앞뒤로 솟음"""
    m = BModel("hex_panel", "빛의 육각 판 (2026-11-13 — 거의 투명한 육각 · 두 겹 테 · 안쪽 테 · 흐르는 빛, 가운데 금 테 보석)", d=3.0)
    weapon_mats(m)
    m.mat("hex", Mat(hex_paint(0.16), tint=0, glow=True, animated=True))
    m.mat("core", Mat(gem_paint(0.7), tint=1, glow=True, animated=True))
    m.box("core", (6.6, 6.6, 7.1), (9.4, 9.4, 8.9), rot=("z", 45, (8, 8, 8)))
    m.box("gold", (6.1, 6.1, 7.3), (9.9, 9.9, 8.7), rot=("z", 45, (8, 8, 8)))
    m.box("hex", (0.0, 0.0, 7.8), (16.0, 16.0, 8.2), faces=("north", "south"))
    return m


def _zig(m, mat, a, lean, L, w, t=0.8, x0=8.0):
    """2026-11-13 YZ 면의 곧은 마디: a = (z, y) 에서 위로 길이 L, 위 끝이 +Z 쪽으로 lean 도 (22.5 단위) 기움 · 너비 w (z) · 두께 t (x) → 끝 (z, y)"""
    az, ay = a
    m.box(mat, (x0 - t / 2, ay, az - w / 2), (x0 + t / 2, ay + L, az + w / 2), rot=("x", lean, (x0, ay, az)) if lean else None)
    r = math.radians(lean)
    return (az + L * math.sin(r), ay + L * math.cos(r))


def thunder_spear():
    """2026-11-13 뇌신의 창 (뇌신의 창 — 촉 +Y): 빛나는 속대 (속빛) 를 감싼 반투명 번개 대 (빛) · 금 고리 셋 · 가죽 손잡이 · 번개 꼴로 꺾인 커다란 촉 (빛 · 흰 날) ·
    촉 밑의 마름모 보석 (속빛) · 아래로 꺾인 옆 가지 둘 · 꼬리의 보석. 촉 끝 y 약 32 · 꼬리 끝 y −14 (길이 46), 쥔 곳 (8, 4, 8)"""
    m = BModel("thunder_spear", "뇌신의 창 (2026-11-13 — 번개 대 · 금 고리 · 번개 꼴 촉 · 옆 가지 · 보석, 계열 색은 서버가 물들임)", d=2.5)
    weapon_mats(m)
    m.mat("light", Mat(light_blade(0.74), tint=0, glow=True, animated=True))
    m.mat("edge2", Mat(edge_light(0.92), glow=True))
    m.mat("core", Mat(gem_paint(0.7), tint=1, glow=True, animated=True))
    m.mat("channel", Mat(rune_channel(), tint=1, glow=True, animated=True))
    # 꼬리 보석 · 속대 · 반투명 번개 대
    m.box("gem", (7.0, -14.0, 7.0), (9.0, -11.6, 9.0), rot=("y", 45, (8, -12.8, 8)))
    m.box("channel", (7.55, -12.0, 7.55), (8.45, 21.0, 8.45))
    m.box("light", (7.2, -11.0, 7.2), (8.8, 19.5, 8.8))
    # 손잡이 · 금 고리
    m.box("grip", (7.05, 1.8, 7.05), (8.95, 7.2, 8.95))
    for y in (1.2, 7.2, 18.6):
        m.box("gold", (6.85, y, 6.85), (9.15, y + 0.9, 9.15))
    # 촉 밑 마름모 보석
    m.box("core", (7.3, 18.8, 6.0), (8.7, 22.8, 10.0), rot=("x", 45, (8, 20.8, 8)))
    # 번개 꼴 촉 (꺾인 마디 셋 — 빛) + 흰 날
    a = (8.0, 20.0)
    for lean, L, w in ((45.0, 5.0, 4.0), (-67.5, 6.0, 3.6), (22.5, 5.4, 3.2)):
        b = _zig(m, "light", a, lean, L, w, t=1.3)
        _zig(m, "edge2", a, lean, L + 0.4, 0.9, t=1.5)
        a = b
    # 옆 가지 (촉 밑에서 아래 바깥으로 — 위 끝을 축으로 아래 끝이 바깥으로 45°)
    for side in (-1, 1):
        zc = 8 + side * 1.0
        m.box("light", (7.6, 15.6, zc - 0.8), (8.4, 19.8, zc + 0.8), rot=("x", -45.0 * side, (8, 19.8, zc)))
        m.box("edge2", (7.7, 15.2, zc - 0.3), (8.3, 19.8, zc + 0.3), rot=("x", -45.0 * side, (8, 19.8, zc)))
    return m


BUILD = {"rock_spike": rock_spike, "ice_spike": ice_spike, "meteor": meteor, "cloud": cloud, "rune_sword": rune_sword, "rune_hammer": rune_hammer,
         "rune_arrow": rune_arrow, "sky_bow": sky_bow, "rune_scythe": rune_scythe, "boulder": boulder, "void_orb": void_orb,
         "ice_blade": ice_blade, "fist": fist, "guillotine": guillotine, "guillotine_blade": guillotine_blade, "halo": halo, "hex_panel": hex_panel, "thunder_spear": thunder_spear}
# 손바닥 · 파도 · 물기둥은 뺌: 사용자 "구지 마크 느낌살려서 각지게 할 필요가 없을거 같다 만년빙하처럼" — tools/vfx_shapes.py (기울인 조각 물체)
# 그물은 예전 (tools/vfx_props.py — 꼰 줄 격자 덮개) 그대로: 판을 쌓은 모양은 쟁반처럼 보여 덮개 모양이 나지 않음 (2026-11-12 미리보기)
# 핏빛 달 (moon) 은 뺌: 사용자 "적월 그냥 다시 2d로 바꾸고 초승달 떨어지는 연출을 없애자" — 적월 강림은 2D 달 (rpg:rfx_moon)


# ------------------------------------------------------------------ 미리보기 (정사영 광선 추적 — 자바 면 UV · 마인크래프트 빛 두 방향)

L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def render(elements, tex, tints, view, W=360, H=360, ss=2, bg=(92, 98, 112), half=None, center=None):
    """elements (자바 모델) + 그림 한 프레임 (H×W×4 uint8) → RGB 그림. tints[i] = tintindex i 의 색 (0~1)"""
    els = [jv.Element(e) for e in elements]
    tex = np.asarray(tex, dtype=np.float64)
    th, tw = tex.shape[:2]
    D = -np.asarray(view, float)
    D /= np.linalg.norm(D)
    right = np.cross(D, [0, 1, 0])
    if np.linalg.norm(right) < 1e-6:
        right = np.array([1.0, 0, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, D)
    pts = np.concatenate([e.corners() for e in els])
    c = (pts.min(0) + pts.max(0)) / 2 if center is None else np.asarray(center, float)
    if half is None:
        rel = pts - c
        half = max(np.abs(rel @ right).max(), np.abs(rel @ up).max()) * 1.12
    Wn, Hn = W * ss, H * ss
    xs = (np.arange(Wn) + 0.5) / Wn * 2 - 1
    ys = 1 - (np.arange(Hn) + 0.5) / Hn * 2
    gx, gy = np.meshgrid(xs * half * (W / H), ys * half)
    P = c[None, None, :] + gx[..., None] * right + gy[..., None] * up - D * 1000.0
    P = P.reshape(-1, 3)
    N = len(P)
    R_, T_, Cc, A_ = [], [], [], []
    for ei, (el, ej) in enumerate(zip(els, elements)):
        # 화면에서 이 상자가 덮는 곳만
        cs = el.corners() - c
        sx, sy = cs @ right, cs @ up
        x0 = int(np.floor(((sx.min() / (half * W / H)) + 1) / 2 * Wn)) - 1
        x1 = int(np.ceil(((sx.max() / (half * W / H)) + 1) / 2 * Wn)) + 1
        y0 = int(np.floor((1 - sy.max() / half) / 2 * Hn)) - 1
        y1 = int(np.ceil((1 - sy.min() / half) / 2 * Hn)) + 1
        x0, x1, y0, y1 = max(0, x0), min(Wn, x1), max(0, y0), min(Hn, y1)
        if x0 >= x1 or y0 >= y1:
            continue
        idx = (np.arange(y0, y1)[:, None] * Wn + np.arange(x0, x1)[None, :]).ravel()
        Pp = P[idx]
        Rt = el.R.T
        lp = el.o + (Rt @ ((Pp - el.o) / el.sc).T).T
        ld = Rt @ (D / el.sc)
        n = len(Pp)
        tmin = np.full(n, -np.inf)
        tmax = np.full(n, np.inf)
        ea = np.full(n, -1)
        em = np.zeros(n, bool)
        ok = np.ones(n, bool)
        for i in range(3):
            if abs(ld[i]) < 1e-12:
                ok &= (lp[:, i] >= el.f[i]) & (lp[:, i] <= el.t[i])
                continue
            t1 = (el.f[i] - lp[:, i]) / ld[i]
            t2 = (el.t[i] - lp[:, i]) / ld[i]
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
        Hh = lp[hit] + np.outer(tmin[hit], ld)
        for name, (uv, rot) in el.faces.items():
            ax, sign = jv.FACE_DIR[name]
            msk = (ea[hit] == ax) & (em[hit] == (sign < 0))
            if not msk.any():
                continue
            a, b = jv.face_ab(name, Hh[msk], el.f, el.t)
            s, t = jv.rotate_st(np.clip(a, 0, 1), np.clip(b, 0, 1), rot)
            u = uv[0] + (uv[2] - uv[0]) * s
            v = uv[1] + (uv[3] - uv[1]) * t
            u0, u1 = sorted((uv[0], uv[2]))
            v0, v1 = sorted((uv[1], uv[3]))
            u = np.maximum(np.minimum(u, u1 - 1e-6), u0)
            v = np.maximum(np.minimum(v, v1 - 1e-6), v0)
            px = np.clip(np.floor(u / 16 * tw).astype(int), 0, tw - 1)
            py = np.clip(np.floor(v / 16 * th).astype(int), 0, th - 1)
            col = tex[py, px].copy()
            ti = (ej.get("faces", {}).get(name) or {}).get("tintindex")
            if ti is not None:
                col[:, :3] *= np.asarray(tints[min(ti, len(tints) - 1)])
            nl = np.zeros(3)
            nl[ax] = sign
            nw = el.R @ nl
            nw /= np.linalg.norm(nw)
            k = min(1.0, (max(0.0, float(nw @ L0)) + max(0.0, float(nw @ L1))) * 0.6 + 0.4)
            col[:, :3] *= k
            rays = idx[hit[msk]]
            R_.append(rays)
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
    img = img.reshape(Hn, Wn, 3)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    if ss > 1:
        im = im.resize((W, H), Image.LANCZOS)
    return im


def load_current(name):
    """지금 파일 (예전 모습): (elements, 첫 프레임 그림)"""
    js = json.load(open(os.path.join(RES, "models", "item", "vfx", name + ".json"), encoding="utf-8"))
    im = Image.open(os.path.join(RES, "textures", "item", "vfx", name + ".png")).convert("RGBA")
    w = im.size[0]
    return js["elements"], np.asarray(im.crop((0, 0, w, w)))


TINTS = {"rock_spike": [(1.0, 0.62, 0.2)], "ice_spike": [(0.6, 0.85, 1.0)], "meteor": [(1, 1, 1)],
         "cloud": [(0.42, 0.36, 0.62), (0.78, 0.62, 1.0)], "rune_sword": [(0.45, 0.62, 1.0), (0.86, 0.92, 1.0)],
         "rune_hammer": [(1.0, 0.82, 0.3), (1.0, 0.95, 0.7)],
         "rune_arrow": [(0.66, 1.0, 0.28), (0.95, 1.0, 0.82)], "sky_bow": [(1.0, 0.86, 0.35), (1.0, 0.98, 0.8)], "rune_scythe": [(0.86, 0.06, 0.12), (1.0, 0.72, 0.68)],
         "boulder": [(0.6, 0.24, 0.9)], "void_orb": [(0.6, 0.24, 0.9)], "palm": [(1.0, 0.8, 0.35), (1.0, 0.95, 0.75)], "fist": [(1.0, 0.75, 0.3), (1.0, 0.95, 0.75)],
         "net": [(0.5, 1.0, 0.5)], "guillotine": [(1.0, 0.62, 0.3), (0.78, 0.45, 1.0)], "guillotine_blade": [(1.0, 0.78, 0.4), (0.78, 0.45, 1.0)],
         "halo": [(1.0, 0.86, 0.45), (1.0, 1.0, 0.9)], "hex_panel": [(1.0, 0.78, 0.35), (1.0, 0.95, 0.8)],
         "thunder_spear": [(0.62, 0.42, 1.0), (0.85, 0.95, 1.0)]}
VIEWS = [(1.0, 0.45, -0.7), (-0.6, 0.25, -1.0)]
VIEWS_OF = {"thunder_spear": [(1.0, 0.2, 0.0), (0.6, 0.3, -1.0)], "halo": [(1.0, 0.45, -0.7), (0.0, 1.0, 0.01)], "hex_panel": [(0.0, 0.1, -1.0), (1.0, 0.3, -0.6)], "fist": [(0.35, -0.35, -1.0), (0.6, 0.3, 1.0)], "wave": [(0.6, 0.35, -1.0), (1.0, 0.12, 0.0)], "palm": [(0.25, 0.25, -1.0), (0.3, 0.2, 1.0)]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()
    names = a.names or list(BUILD)
    built = {}
    for n in names:
        built[n] = BUILD[n]().files()
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        for n, (files, js, img, T) in built.items():
            tints = TINTS.get(n, [(1, 1, 1)])
            try:
                old_els, old_tex = load_current(n)
            except FileNotFoundError:
                old_els, old_tex = [], None
            frame = img[:T]
            row = []
            for vi, vw in enumerate(VIEWS_OF.get(n, VIEWS)):
                pts = np.concatenate([jv.Element(e).corners() for e in js["elements"] + old_els])
                ctr = (pts.min(0) + pts.max(0)) / 2
                D = -np.asarray(vw, float) / np.linalg.norm(vw)
                rt = np.cross(D, [0, 1, 0])
                rt /= np.linalg.norm(rt)
                upv = np.cross(rt, D)
                rel = pts - ctr
                half = max(np.abs(rel @ rt).max(), np.abs(rel @ upv).max()) * 1.1
                if vi == 0 and old_els:
                    row.append(render(old_els, old_tex, tints, vw, half=half, center=ctr))
                row.append(render(js["elements"], frame, tints, vw, half=half, center=ctr))
            sheet = Image.new("RGB", (sum(i.size[0] for i in row) + 8 * (len(row) - 1), row[0].size[1]), (30, 32, 38))
            x = 0
            for i in row:
                sheet.paste(i, (x, 0))
                x += i.size[0] + 8
            p = os.path.join(a.preview, n + ".png")
            sheet.save(p)
            print(p, len(js["elements"]), "elements · 그림", T, "×", img.shape[0])
        return
    stale = []
    for n, (files, js, img, T) in built.items():
        for path, data in files.items():
            old = open(path, "rb").read() if os.path.exists(path) else None
            if data is None:
                if old is not None:
                    stale.append(os.path.relpath(path, ROOT))
                    if not a.check:
                        os.remove(path)
                continue
            if old != data:
                stale.append(os.path.relpath(path, ROOT))
                if not a.check:
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    open(path, "wb").write(data)
    if a.check:
        if stale:
            print("vfx_blocky: 다시 만들어야 함:", ", ".join(stale))
            sys.exit(1)
        print("vfx_blocky: ok (%d)" % len(built))
    else:
        for n, (files, js, img, T) in built.items():
            print("vfx_blocky: %-12s %4d elements · 그림 %d × %d" % (n, len(js["elements"]), T, img.shape[0]))
        print("vfx_blocky: 씀", len(stale))


if __name__ == "__main__":
    main()
