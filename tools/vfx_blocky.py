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


# ------------------------------------------------------------------ 무기 (치수는 tools/vfx_props.py 그대로 — RuneProps 의 길이 · 쥔 곳)

def weapon_mats(m, glow_tint=0, core_tint=1):
    m.mat("blade", Mat(glow_blade(), tint=glow_tint, glow=True, animated=True))
    m.mat("fuller", Mat(rune_flow(), tint=core_tint, glow=True, animated=True))
    m.mat("edge", Mat(edge_paint(), glow=True))
    m.mat("steel", Mat(bevel("#34343F", "#5A5A6A", "#1C1C23", speck=0.06)))
    m.mat("gold", Mat(gold_paint()))
    m.mat("grip", Mat(grip_paint()))
    m.mat("gem", Mat(gem_paint(), tint=glow_tint, glow=True, animated=True))


def rune_sword():
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
    m = BModel("palm", "기의 손바닥 (2026-11-12 사진 방식 — 반투명 기 · 밝은 테 · 흐르는 결 · 손바닥의 인, 빛 = 계열 색)", d=2.0)
    m.mat("ki", Mat(ki_paint(), tint=0, glow=True, animated=True))
    m.mat("line", Mat(gem_paint(0.8), tint=1, glow=True, animated=True))
    m.mat("seal", Mat(seal_paint(), tint=1, glow=True, animated=True))
    m.box("ki", (3.0, 0, 6.6), (13.0, 10.0, 9.4))
    m.box("seal", (4.6, 1.6, 9.4), (11.4, 8.4, 9.7), faces=["south"])
    m.box("ki", (4.4, -5.0, 6.9), (11.6, 0, 9.1))
    m.box("line", (4.2, -1.2, 6.7), (11.8, -0.4, 9.3))
    for x0, h1, h2, tilt in ((3.2, 5.5, 4.0, 22.5), (5.8, 6.8, 4.6, 0.0), (8.4, 6.5, 4.4, 0.0), (11.0, 5.0, 3.6, -22.5)):
        org = (x0 + 1.1, 10.0, 8.0)
        rot = ("z", tilt, org) if tilt else None
        m.box("ki", (x0, 10.0, 6.8), (x0 + 2.2, 10.0 + h1, 9.2), rot=rot)
        m.box("ki", (x0 + 0.15, 10.0 + h1, 6.95), (x0 + 2.05, 10.0 + h1 + h2, 9.05), rot=rot)
        m.box("line", (x0 + 0.1, 10.0 + h1 - 0.35, 6.75), (x0 + 2.1, 10.0 + h1 + 0.25, 9.25), rot=rot)
    m.box("ki", (-1.5, 2.0, 6.9), (3.0, 4.4, 9.1), rot=("z", 45.0, (3.0, 3.0, 8.0)))
    m.box("ki", (-5.0, 2.2, 7.05), (-1.5, 4.2, 8.95), rot=("z", 45.0, (3.0, 3.0, 8.0)))
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
    """파도: 밑 가운데 (8, 0, 1.5) · 너비 (x) 30 · 높이 18 · 앞 (말린 물마루) = +Z — 계단 비탈 · 물마루 · 앞으로 말려 내려오는 입술 · 거품"""
    m = BModel("wave", "파도 (2026-11-12 사진 방식 — 계단 비탈 · 말린 물마루 · 거품 · 흐르는 물결)", d=1.5)
    m.mat("deep", Mat(water_paint("#062E6A", "#145AA8", 0.9), glow=True, animated=True))
    m.mat("water", Mat(water_paint("#0C5CC4", "#3A9AF0", 0.86), glow=True, animated=True))
    m.mat("crest", Mat(water_paint("#1F7FE0", "#7CC4FF", 0.9, foam_top=True), glow=True, animated=True))
    m.mat("foam", Mat(foam_paint(), glow=True, animated=True))
    X0, X1 = -7.0, 23.0
    m.box("deep", (X0, 0, -14), (X1, 5.5, -4))
    m.box("deep", (X0 + 0.5, 0, -8), (X1 - 0.5, 10.5, 1))
    m.box("water", (X0, 0, -3), (X1, 15.0, 6.0))
    m.box("crest", (X0 + 0.5, 12.5, -1.0), (X1 - 0.5, 17.6, 7.5))
    m.box("crest", (X0 + 1.0, 13.0, 6.0), (X1 - 1.0, 17.0, 11.0), rot=("x", 22.5, (8, 15.0, 8.5)))
    m.box("water", (X0 + 1.5, 9.5, 10.0), (X1 - 1.5, 14.0, 14.0), rot=("x", 45.0, (8, 11.75, 12.0)))
    m.box("foam", (X0 + 2.0, 6.0, 12.8), (X1 - 2.0, 10.0, 16.0), rot=("x", 45.0, (8, 8.0, 14.4)))
    m.box("foam", (X0 + 0.5, 17.6, 0.0), (X1 - 0.5, 18.0, 7.0))
    m.box("water", (X0, 0, 6.0), (X1, 2.0, 16.0))
    m.box("foam", (X0 + 1.0, 2.0, 9.0), (X1 - 1.0, 2.6, 17.5))
    return m


def water_column():
    """물기둥: 밑 가운데 (8, 0, 7.5) · 높이 28 — 팔각 기둥 마디 (넓은 밑 → 허리 → 벌어진 꼭대기) · 거품 왕관 · 물보라 가시"""
    m = BModel("water_column", "물기둥 (2026-11-12 사진 방식 — 팔각 마디 · 흐르는 물결 · 거품 왕관)", d=1.5)
    m.mat("deep", Mat(water_paint("#0A3C86", "#1E6CC8", 0.88), glow=True, animated=True))
    m.mat("water", Mat(water_paint("#1460C8", "#52AEF6", 0.84), glow=True, animated=True))
    m.mat("foam", Mat(foam_paint(), glow=True, animated=True))
    cx, cz = 8.0, 7.5
    segs = (("deep", 0.0, 4.0, 5.8), ("water", 4.0, 10.0, 4.8), ("water", 10.0, 16.0, 4.2), ("water", 16.0, 21.0, 4.6), ("water", 21.0, 24.0, 5.8),
            ("foam", 24.0, 25.6, 6.8))
    for (mat, y0, y1, r) in segs:
        m.box(mat, (cx - r, y0, cz - r), (cx + r, y1, cz + r))
        m.box(mat, (cx - r * 0.95, y0, cz - r * 0.95), (cx + r * 0.95, y1, cz + r * 0.95), rot=("y", 45.0, (cx, (y0 + y1) / 2, cz)))
    for i in range(8):
        a = i / 8 * math.pi * 2 + math.pi / 8
        px, pz = cx + math.cos(a) * 5.6, cz + math.sin(a) * 5.6
        hgt = 27.0 + (1.6 if i % 2 == 0 else 0.4)
        m.box("foam", (px - 0.9, 25.0, pz - 0.9), (px + 0.9, hgt, pz + 0.9), rot=("y", 45.0, (px, 26, pz)))
    return m


BUILD = {"rock_spike": rock_spike, "ice_spike": ice_spike, "meteor": meteor, "cloud": cloud, "rune_sword": rune_sword, "rune_hammer": rune_hammer,
         "ice_blade": ice_blade, "moon": moon, "palm": palm, "fist": fist, "wave": wave, "water_column": water_column}
# 그물은 예전 (tools/vfx_props.py — 꼰 줄 격자 덮개) 그대로: 판을 쌓은 모양은 쟁반처럼 보여 덮개 모양이 나지 않음 (2026-11-12 미리보기)


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
         "rune_hammer": [(1.0, 0.82, 0.3), (1.0, 0.95, 0.7)], "palm": [(1.0, 0.8, 0.35), (1.0, 0.95, 0.75)], "fist": [(1.0, 0.75, 0.3), (1.0, 0.95, 0.75)],
         "net": [(0.5, 1.0, 0.5)]}
VIEWS = [(1.0, 0.45, -0.7), (-0.6, 0.25, -1.0)]
VIEWS_OF = {"fist": [(0.35, -0.35, -1.0), (0.6, 0.3, 1.0)], "wave": [(1.0, 0.12, 0.0), (0.7, 0.3, 1.0)], "palm": [(0.3, 0.2, 1.0), (1.0, 0.3, -0.4)]}


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
            old_els, old_tex = load_current(n)
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
                if vi == 0:
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
