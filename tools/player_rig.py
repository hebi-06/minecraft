#!/usr/bin/env python3
"""2026-11-13 모드 없는 클라이언트의 플레이어 모션 (사용자 "모든 모션을 추천대로 만들고 있어봐" — 1번: 뼈마다 아이템 표시 + 픽셀마다 색, 셰이더 없음).

싸우는 동안 진짜 몸 대신 보이는 뼈 몸 (서버 mmorpg.fabric.vanilla.VanillaRig): 뼈 10 (머리 · 몸통 · 오른팔 위 · 아래 · 왼팔 위 · 아래 · 오른다리 위 · 아래 ·
왼다리 위 · 아래 — 팔꿈치 · 무릎에서 꺾임) 마다 아이템 모델 하나. 스킨 픽셀 하나 = 면 하나 (흰 그림 · tintindex) — 색은 서버가 그 사람의 스킨에서 읽어
아이템의 custom_model_data colors 로 넣음 (아이템 정의의 tints = custom_model_data index i). 겉옷 층 (모자 · 재킷 · 소매 · 바지) 은 부풀린 상자 —
투명한 칸은 색의 알파 0. 팔꿈치 · 무릎의 자른 면은 둘레 줄의 평균 색 한 칸.

좌표: 자바 모델 공간 (ModelPart — 픽셀, +y 아래, 앞 −z) 의 뼈 자리 (pivot) 기준 점 v → 아이템 모델 m = F · v + 8 (F = diag(−1, −1, 1)).
서버의 표시 변환 = 몸 (Ry(180 − 몸 방향) · S(−1, −1, 1) · S(0.9375) · T(0, −1.501, 0)) · 부위 (T(pivot / 16) · Rzyx) · Rx(180°)
(F · 아이템 표시의 Y 180° = Rx(180°)).
면 · 픽셀: 바닐라 ModelPart.Cube 의 면 (DOWN = 위로 보이는 면 …) 과 UV 모서리 (꼭짓점 0 ~ 3 → (u2, v1) (u1, v1) (u1, v2) (u2, v2)) 그대로.

만드는 것 (src/main/resources — 손으로 고치지 않음):
- assets/rpg/models/item/vrig/<wide|slim>/<뼈>.json · assets/rpg/items/vrig/<wide|slim>/<뼈>.json · assets/rpg/textures/item/vrig/white.png
- mmorpg/vanilla_rig.json — 서버용 (뼈마다 아이템 · tint 순서의 픽셀 [u, v, 겉옷 층] · 평균 칸)
- assets/mmorpg/rig/default_skin.png — 스킨이 없을 때 (우리 그림 — 모험가)

  python3 tools/player_rig.py                 # 만들기
  python3 tools/player_rig.py --preview 폴더   # 확인용 그림 (기본 스킨 · 자세 몇)
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT_MODELS = os.path.join(RES, "assets", "rpg", "models", "item", "vrig")
OUT_ITEMS = os.path.join(RES, "assets", "rpg", "items", "vrig")
OUT_TEX = os.path.join(RES, "assets", "rpg", "textures", "item", "vrig")
OUT_MANIFEST = os.path.join(RES, "mmorpg", "vanilla_rig.json")
OUT_SKIN = os.path.join(RES, "assets", "mmorpg", "rig", "default_skin.png")

# 자바 Direction 의 법선 (모델 공간 — +y 아래)
NORMAL = {"down": (0, -1, 0), "up": (0, 1, 0), "west": (-1, 0, 0), "east": (1, 0, 0), "north": (0, 0, -1), "south": (0, 0, 1)}
F = np.array([-1.0, -1.0, 1.0])
# 아이템 모델 면 이름 (모델 공간: +y 위 · +x 동 · −z 북)
MODEL_FACE = {(0, 1, 0): "up", (0, -1, 0): "down", (1, 0, 0): "east", (-1, 0, 0): "west", (0, 0, -1): "north", (0, 0, 1): "south"}


def cube_faces(origin, size, uv, inflate):
    """바닐라 ModelPart.Cube: 면 이름 → (꼭짓점 넷 — 모델 공간 v (뼈 자리 기준), UV 사각형 (u1, v1, u2, v2))"""
    x, y, z = origin
    w, h, d = size
    u, v = uv
    x2, y2, z2 = x + w, y + h, z + d
    x, y, z = x - inflate, y - inflate, z - inflate
    x2, y2, z2 = x2 + inflate, y2 + inflate, z2 + inflate
    V = [(x, y, z), (x2, y, z), (x2, y2, z), (x, y2, z), (x, y, z2), (x2, y, z2), (x2, y2, z2), (x, y2, z2)]
    j, k, l, m, n, o = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    p, q, r = v, v + d, v + d + h
    return {
        "down": ([V[5], V[4], V[0], V[1]], (k, p, l, q)),
        "up": ([V[2], V[3], V[7], V[6]], (l, q, m, p)),
        "west": ([V[0], V[4], V[7], V[3]], (j, q, k, r)),
        "north": ([V[1], V[0], V[3], V[2]], (k, q, l, r)),
        "east": ([V[5], V[1], V[2], V[6]], (l, q, n, r)),
        "south": ([V[4], V[5], V[6], V[7]], (n, q, o, r)),
    }


def texels(face):
    """면의 텍셀마다 (u, v (텍스처 픽셀), 네 꼭짓점 — 모델 공간 v) — 꼭짓점 0 ~ 3 의 UV = (u2, v1) (u1, v1) (u1, v2) (u2, v2)"""
    verts, (u1, v1, u2, v2) = face
    P0, P1, P2 = (np.array(verts[i], float) for i in (0, 1, 2))
    du = (P0 - P1) / (u2 - u1) if u2 != u1 else np.zeros(3)
    dv = (P2 - P1) / (v2 - v1) if v2 != v1 else np.zeros(3)
    lo_u, hi_u = min(u1, u2), max(u1, u2)
    lo_v, hi_v = min(v1, v2), max(v1, v2)
    out = []
    for tv in range(int(lo_v), int(hi_v)):
        for tu in range(int(lo_u), int(hi_u)):
            a = P1 + (tu - u1) * du + (tv - v1) * dv
            b = P1 + (tu + 1 - u1) * du + (tv + 1 - v1) * dv
            out.append((tu, tv, a, b))
    return out


def model_box(a, b):
    """모델 공간 v 의 두 모서리 → 아이템 모델 좌표 (from, to)"""
    ma = F * a + 8
    mb = F * b + 8
    lo = np.minimum(ma, mb)
    hi = np.maximum(ma, mb)
    return [round(float(c), 4) for c in lo], [round(float(c), 4) for c in hi]


class Bone:
    """뼈 하나: 층 0 (몸 — 늘 그림) · 층 1 (겉옷 — 따로 모델 <뼈>_o, 아이템 정의에서 custom_model_data flag 0 이 참일 때만).
    2026-11-13 CI 43351661053: tint 색은 알파를 쓰지 않음 (바닐라 CustomModelDataSource 가 ARGB.opaque) — 겉옷 층의 투명 칸이 검게 칠해져 몸 전체가 검은
    껍데기로 보였음 → 겉옷 층은 따로 모델 · 투명 칸은 그 밑 몸 칸의 색 (src [u, v, 1, 밑 u, 밑 v]), 겉옷이 하나도 없으면 flag 거짓 (그리지 않음)"""

    def __init__(self, name):
        self.name = name
        self.layers = {0: ([], []), 1: ([], [])}

    @property
    def elements(self):
        return self.layers[0][0] + self.layers[1][0]

    @property
    def src(self):
        return self.layers[0][1] + self.layers[1][1]

    def pixel(self, a, b, face_name, u, v, layer, base=None):
        els, src = self.layers[layer]
        frm, to = model_box(a, b)
        n = tuple(int(c) for c in F * np.array(NORMAL[face_name]))
        els.append({"from": frm, "to": to, "faces": {MODEL_FACE[n]: {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": len(src)}}})
        src.append([int(u), int(v), layer] + ([int(base[0]), int(base[1])] if layer == 1 else []))

    def flat(self, a, b, face_name, avg):
        """자른 면 (한 칸 — 둘레 줄의 평균 색)"""
        els, src = self.layers[0]
        frm, to = model_box(a, b)
        n = tuple(int(c) for c in F * np.array(NORMAL[face_name]))
        els.append({"from": frm, "to": to, "faces": {MODEL_FACE[n]: {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": len(src)}}})
        src.append({"avg": [[int(u), int(v)] for u, v in avg]})


def add_box(bone, origin, size, uv, inflate, layer, rows=None, skip=(), base_uv=None):
    """상자 하나의 면 픽셀 (rows = 옆면의 위에서부터 줄 [from, to) — 팔다리를 위 · 아래로 자름, skip = 뺄 면, base_uv = 겉옷 층이면 그 밑 몸 층의 펼침 자리)"""
    faces = cube_faces(origin, size, uv, inflate)
    w, h, d = size
    for name, face in faces.items():
        if name in skip:
            continue
        for tu, tv, a, b in texels(face):
            if rows is not None and name in ("west", "north", "east", "south"):
                row = tv - (uv[1] + d)
                if not (rows[0] <= row < rows[1]):
                    continue
            base = None if base_uv is None else (tu - uv[0] + base_uv[0], tv - uv[1] + base_uv[1])
            bone.pixel(a, b, name, tu, tv, layer, base)


def side_row(uv, size, row):
    """옆면 넷의 그 줄 픽셀 (평균 색 — 자른 면)"""
    u, v = uv
    w, h, d = size
    y = v + d + row
    return [(x, y) for x in range(u, u + 2 * d + 2 * w)]


def limb(prefix, origin, size, uv, ov, out):
    """팔 · 다리 (높이 12): 위 (0 ~ 6 줄 · 윗면 · 아래 자른 면) · 아래 (6 ~ 12 줄 · 아랫면 · 위 자른 면), 겉옷 층 (ov — 부풀림 0.25) 도 같이 자름"""
    x, y, z = origin
    w, h, d = size
    half = h / 2
    up = Bone(prefix + "_u")
    add_box(up, origin, size, uv, 0, 0, rows=(0, half), skip=("up",))
    up.flat((x, y + half, z), (x + w, y + half, z + d), "up", side_row(uv, size, half - 1))
    add_box(up, origin, size, ov, 0.25, 1, rows=(0, half), skip=("up",), base_uv=uv)
    lo = Bone(prefix + "_l")
    add_box(lo, origin, size, uv, 0, 0, rows=(half, h), skip=("down",))
    lo.flat((x, y + half, z), (x + w, y + half, z + d), "down", side_row(uv, size, half))
    add_box(lo, origin, size, ov, 0.25, 1, rows=(half, h), skip=("down",), base_uv=uv)
    out += [up, lo]


def build(slim):
    bones = []
    head = Bone("head")
    add_box(head, (-4, -8, -4), (8, 8, 8), (0, 0), 0, 0)
    add_box(head, (-4, -8, -4), (8, 8, 8), (32, 0), 0.5, 1, base_uv=(0, 0))
    body = Bone("body")
    add_box(body, (-4, 0, -2), (8, 12, 4), (16, 16), 0, 0)
    add_box(body, (-4, 0, -2), (8, 12, 4), (16, 32), 0.25, 1, base_uv=(16, 16))
    bones += [head, body]
    aw = 3 if slim else 4
    limb("rarm", (-2 if slim else -3, -2, -2), (aw, 12, 4), (40, 16), (40, 32), bones)
    limb("larm", (-1, -2, -2), (aw, 12, 4), (32, 48), (48, 48), bones)
    limb("rleg", (-2, 0, -2), (4, 12, 4), (0, 16), (0, 32), bones)
    limb("lleg", (-2, 0, -2), (4, 12, 4), (16, 48), (0, 48), bones)
    return bones


# ------------------------------------------------------------------ 기본 스킨 (바닐라 그림을 그대로 — 2026-11-13)
#
# 사용자 "플레이어가 스킬을 쓰면 플레이어 스킨이 바뀌잖아 … 플레이어의 스킨을 그대로 유지시키고 싶은데": 스킨 그림을 받지 못한 사람 (오프라인 서버 ·
# 프로필에 textures 없음 · 받기 실패 — CI 영상도) 은 뼈 몸이 우리 모험가 그림이 되어, 클라이언트가 그린 진짜 몸 (기본 스킨 — DefaultPlayerSkin 이
# UUID 로 고름) 과 달랐음. → 기본 스킨 열여덟 (형 둘 × 아홉) 은 클라이언트에 있는 바닐라 그림 minecraft:entity/player/<형>/<이름> 을 그대로 쓰는 뼈 모델
# (아이템 아틀라스에 그 그림을 넣음 — VanillaPack.rigAtlas, 모장 그림을 우리 팩에 담지 않음). 서버는 UUID 로 같은 것을 고름 (VanillaRig.defaultFor).
DEFAULT_NAMES = ["alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"]
# DefaultPlayerSkin.DEFAULT_SKINS 차례 (1.20.2 ~ — 가는 팔 아홉 다음 넓은 팔 아홉, UUID.hashCode() 를 18 로 나눈 나머지): CI 사진기가 클라이언트 값을 기록함
DEFAULT_SKINS = ["slim/" + n for n in DEFAULT_NAMES] + ["wide/" + n for n in DEFAULT_NAMES]
# 아이템 모델 면 방향 → (uv 앞 두 값을 받는 꼭짓점, 뒤 두 값을 받는 꼭짓점) — FaceBakery 의 기본 UV (면 방향마다 u · v 가 어느 축으로 가는지) 와 같은 꼴
_AB = {
    "down": (lambda lo, hi: (lo[0], lo[1], hi[2]), lambda lo, hi: (hi[0], lo[1], lo[2])),
    "up": (lambda lo, hi: (lo[0], lo[1], lo[2]), lambda lo, hi: (hi[0], lo[1], hi[2])),
    "north": (lambda lo, hi: (hi[0], hi[1], lo[2]), lambda lo, hi: (lo[0], lo[1], lo[2])),
    "south": (lambda lo, hi: (lo[0], hi[1], lo[2]), lambda lo, hi: (hi[0], lo[1], lo[2])),
    "west": (lambda lo, hi: (lo[0], hi[1], lo[2]), lambda lo, hi: (lo[0], lo[1], hi[2])),
    "east": (lambda lo, hi: (lo[0], hi[1], hi[2]), lambda lo, hi: (lo[0], lo[1], lo[2])),
}


def tex_face(face_name, face, vrange=None):
    """바닐라 상자 면 하나 → 그림을 쓰는 얇은 요소 하나 (아이템 모델 좌표 · 면 방향 · uv = 64 픽셀 그림의 칸 ÷ 4). vrange = 옆면의 v 범위 [v0, v1) (팔다리를 자름)"""
    verts, (u1, v1, u2, v2) = face
    P0, P1, P2 = (np.array(verts[i], float) for i in (0, 1, 2))
    du = (P0 - P1) / (u2 - u1) if u2 != u1 else np.zeros(3)
    dv = (P2 - P1) / (v2 - v1) if v2 != v1 else np.zeros(3)
    lo_u, hi_u = min(u1, u2), max(u1, u2)
    lo_v, hi_v = (min(v1, v2), max(v1, v2)) if vrange is None else vrange

    def pos(u, v):
        return F * (P1 + (u - u1) * du + (v - v1) * dv) + 8

    corners = {(u, v): pos(u, v) for u in (lo_u, hi_u) for v in (lo_v, hi_v)}
    pts = np.array(list(corners.values()))
    lo, hi = pts.min(0), pts.max(0)
    d = MODEL_FACE[tuple(int(c) for c in F * np.array(NORMAL[face_name]))]
    uv = []
    for corner in _AB[d]:
        want = np.array(corner(lo, hi))
        hit = [k for k, p in corners.items() if np.abs(p - want).max() < 1e-6]
        if not hit:
            raise SystemExit("rig: face %s needs a rotated uv" % face_name)
        uv += [hit[0][0] / 4, hit[0][1] / 4]
    return {"from": [round(float(c), 4) for c in lo], "to": [round(float(c), 4) for c in hi], "faces": {d: {"uv": [round(x, 4) for x in uv], "texture": "#skin"}}}


def tex_box(els, origin, size, uv, inflate, rows=None, skip=()):
    """add_box 의 그림 판: 상자의 면마다 요소 하나 (rows = 옆면의 위에서부터 줄 [from, to))"""
    w, h, d = size
    for name, face in cube_faces(origin, size, uv, inflate).items():
        if name in skip:
            continue
        vr = None
        if rows is not None and name in ("west", "north", "east", "south"):
            vr = (uv[1] + d + rows[0], uv[1] + d + rows[1])
        els.append(tex_face(name, face, vr))


def tex_cut(els, a, b, face_name, uv, size, row):
    """팔꿈치 · 무릎의 자른 면: 앞면의 그 줄 (한 픽셀 줄을 늘림)"""
    u, v = uv
    w, h, d = size
    frm, to = model_box(np.array(a, float), np.array(b, float))
    n = tuple(int(c) for c in F * np.array(NORMAL[face_name]))
    els.append({"from": frm, "to": to, "faces": {MODEL_FACE[n]: {"uv": [(u + d) / 4, (v + d + row) / 4, (u + d + w) / 4, (v + d + row + 1) / 4],
                                                                    "texture": "#skin"}}})


def tex_build(slim):
    """그림을 쓰는 뼈 열 (build 와 같은 상자 · 자름 — 몸 층 + 겉옷 층을 한 모델에, 겉옷의 투명 칸은 그림의 알파 그대로)"""
    out = {}
    head = []
    tex_box(head, (-4, -8, -4), (8, 8, 8), (0, 0), 0)
    tex_box(head, (-4, -8, -4), (8, 8, 8), (32, 0), 0.5)
    body = []
    tex_box(body, (-4, 0, -2), (8, 12, 4), (16, 16), 0)
    tex_box(body, (-4, 0, -2), (8, 12, 4), (16, 32), 0.25)
    out["head"], out["body"] = head, body
    aw = 3 if slim else 4
    for prefix, origin, size, uv, ov in (("rarm", (-2 if slim else -3, -2, -2), (aw, 12, 4), (40, 16), (40, 32)),
                                         ("larm", (-1, -2, -2), (aw, 12, 4), (32, 48), (48, 48)),
                                         ("rleg", (-2, 0, -2), (4, 12, 4), (0, 16), (0, 32)),
                                         ("lleg", (-2, 0, -2), (4, 12, 4), (16, 48), (0, 48))):
        x, y, z = origin
        w, h, d = size
        half = h // 2
        up, lo = [], []
        tex_box(up, origin, size, uv, 0, rows=(0, half), skip=("up",))
        tex_cut(up, (x, y + half, z), (x + w, y + half, z + d), "up", uv, size, half - 1)
        tex_box(up, origin, size, ov, 0.25, rows=(0, half), skip=("up",))
        tex_box(lo, origin, size, uv, 0, rows=(half, h), skip=("down",))
        tex_cut(lo, (x, y + half, z), (x + w, y + half, z + d), "down", uv, size, half)
        tex_box(lo, origin, size, ov, 0.25, rows=(half, h), skip=("down",))
        out[prefix + "_u"], out[prefix + "_l"] = up, lo
    return out


def write_defaults():
    """기본 스킨 뼈: 모델 rpg:item/vrig/def/<형>/<뼈> (#skin) · 이름마다 자식 모델 · 아이템 rpg:vrig/def/<형>/<뼈> (custom_model_data 문자열 0 = 이름)"""
    for variant in ("wide", "slim"):
        bones = tex_build(variant == "slim")
        mdir = os.path.join(OUT_MODELS, "def", variant)
        idir = os.path.join(OUT_ITEMS, "def", variant)
        os.makedirs(idir, exist_ok=True)
        for bname, els in bones.items():
            os.makedirs(mdir, exist_ok=True)
            with open(os.path.join(mdir, bname + ".json"), "w", encoding="utf-8") as fp:
                json.dump({"textures": {"particle": "#skin"}, "elements": els}, fp, separators=(",", ":"))
            cases = []
            for name in DEFAULT_NAMES:
                cdir = os.path.join(mdir, name)
                os.makedirs(cdir, exist_ok=True)
                with open(os.path.join(cdir, bname + ".json"), "w", encoding="utf-8") as fp:
                    json.dump({"parent": "rpg:item/vrig/def/%s/%s" % (variant, bname), "textures": {"skin": "minecraft:entity/player/%s/%s" % (variant, name)}},
                              fp, separators=(",", ":"))
                cases.append({"when": name, "model": {"type": "minecraft:model", "model": "rpg:item/vrig/def/%s/%s/%s" % (variant, name, bname)}})
            item = {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
                              "fallback": {"type": "minecraft:model", "model": "rpg:item/vrig/def/%s/steve/%s" % (variant, bname)}}}
            with open(os.path.join(idir, bname + ".json"), "w", encoding="utf-8") as fp:
                json.dump(item, fp, separators=(",", ":"))


# 뼈 자리 (자바 모델 — PlayerModel / HumanoidModel) · 팔꿈치 · 무릎 (뼈 자리 기준 — 모드 MotionRender 의 굽힘 자리)
PIVOTS = {
    "wide": {"head": [0, 0, 0], "body": [0, 0, 0], "rarm": [-5, 2, 0], "larm": [5, 2, 0], "rleg": [-1.9, 12, 0], "lleg": [1.9, 12, 0]},
    "slim": {"head": [0, 0, 0], "body": [0, 0, 0], "rarm": [-5, 2.5, 0], "larm": [5, 2.5, 0], "rleg": [-1.9, 12, 0], "lleg": [1.9, 12, 0]},
}
JOINTS = {"rarm": [-1, 4, 0], "larm": [1, 4, 0], "rleg": [0, 6, 0], "lleg": [0, 6, 0]}


def default_skin():
    """스킨이 없을 때의 모험가 (우리 그림): 갈색 머리 · 남색 튜닉 · 가죽 띠 · 회색 바지 · 갈색 장화"""
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    SKIN, SKIN_D = (205, 152, 112, 255), (176, 124, 88, 255)
    HAIR, HAIR_D = (88, 56, 34, 255), (64, 40, 24, 255)
    TUNIC, TUNIC_D = (54, 84, 140, 255), (40, 62, 108, 255)
    BELT, BUCKLE = (96, 62, 36, 255), (212, 172, 72, 255)
    PANTS, PANTS_D = (70, 70, 82, 255), (52, 52, 62, 255)
    BOOT = (78, 50, 30, 255)

    def rect(x0, y0, w, h, c):
        for yy in range(y0, y0 + h):
            for xx in range(x0, x0 + w):
                px[xx, yy] = c

    def box(u, v, w, h, d, fill):
        """상자 펼침 전체를 fill(face, x, y) 로 (face: up · down · right · front · left · back — None 이면 투명 그대로)"""
        regions = {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                   "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        for f, (x0, y0, ww, hh) in regions.items():
            for yy in range(hh):
                for xx in range(ww):
                    c = fill(f, xx, yy, ww, hh)
                    if c is not None:
                        px[x0 + xx, y0 + yy] = c

    def head(f, x, y, w, h):
        if f in ("up",):
            return HAIR if (x + y) % 3 else HAIR_D
        if f == "down":
            return SKIN_D
        if f == "back":
            return HAIR if y < 7 or (x + y) % 4 else HAIR_D
        if f in ("right", "left"):
            return HAIR if y < 3 or (y < 5 and (x < 2 if f == "right" else x > 5)) else SKIN
        # 앞: 앞머리 두 줄 · 눈 (흰자 · 눈동자) · 입
        if y < 2 or (y == 2 and x in (0, 1, 6, 7)):
            return HAIR
        if y == 4 and x in (1, 2, 5, 6):
            return (245, 245, 240, 255) if x in (1, 6) else (52, 72, 120, 255)
        if y == 6 and x in (3, 4):
            return (150, 92, 76, 255)
        return SKIN

    box(0, 0, 8, 8, 8, head)

    def body(f, x, y, w, h):
        if f in ("up", "down"):
            return TUNIC_D
        if f in ("front", "back") and y == 8:
            return BUCKLE if f == "front" and x in (3, 4) else BELT
        if f in ("right", "left") and y == 8:
            return BELT
        if f == "front" and y < 2 and 2 <= x <= 5:
            return SKIN if y == 0 else TUNIC_D
        return TUNIC if (x * 7 + y * 3) % 11 else TUNIC_D

    box(16, 16, 8, 12, 4, body)

    def arm(f, x, y, w, h):
        if f == "down":
            return SKIN_D
        if f == "up":
            return TUNIC_D
        if y < 5:
            return TUNIC if (x + y) % 5 else TUNIC_D
        if y == 5:
            return TUNIC_D
        return SKIN if y < 11 else SKIN_D

    box(40, 16, 4, 12, 4, arm)
    box(32, 48, 4, 12, 4, arm)

    def leg(f, x, y, w, h):
        if f == "up":
            return PANTS_D
        if f == "down":
            return BOOT
        if y >= 9:
            return BOOT
        return PANTS if (x + 2 * y) % 7 else PANTS_D

    box(0, 16, 4, 12, 4, leg)
    box(16, 48, 4, 12, 4, leg)

    # 겉옷 층 (2026-11-13 — 투명 칸은 밑 몸 색 · 칠한 칸만 겉옷 색 · 다리는 겉옷 없음): 붉은 머리띠 · 가죽 어깨띠 · 팔 보호대
    BAND, BAND_D = (168, 44, 40, 255), (122, 30, 28, 255)
    STRAP, STRAP_D = (120, 78, 44, 255), (92, 58, 32, 255)

    def hat(f, x, y, w, h):
        if f in ("front", "back", "right", "left") and y == 2:
            return BAND if (x + (f == "back")) % 3 else BAND_D
        if f == "back" and y in (3, 4) and x in (3, 4):
            return BAND_D  # 매듭 꼬리
        return None

    box(32, 0, 8, 8, 8, hat)

    def jacket(f, x, y, w, h):
        # 왼쪽 어깨 → 오른쪽 허리로 비스듬한 가죽 띠 (앞 · 뒤)
        if f == "front" and x == 7 - (y * 7) // 7 and y < 8:
            return STRAP if y % 3 else STRAP_D
        if f == "back" and x == (y * 7) // 7 and y < 8:
            return STRAP_D
        return None

    box(16, 32, 8, 12, 4, jacket)

    def bracer(f, x, y, w, h):
        if f in ("front", "back", "right", "left") and 7 <= y <= 9:
            return STRAP if y != 8 else STRAP_D
        return None

    box(40, 32, 4, 12, 4, bracer)
    box(48, 48, 4, 12, 4, bracer)
    return im


def write(args):
    os.makedirs(OUT_TEX, exist_ok=True)
    Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(os.path.join(OUT_TEX, "white.png"))
    manifest = {"_comment": "tools/player_rig.py 가 만듦 — 뼈마다 아이템 (rpg:vrig/<형>/<뼈>) · custom_model_data colors 순서의 픽셀: 몸 층 [u, v, 0] · 평균 칸 {avg} 다음 겉옷 층 [u, v, 1, 밑 u, 밑 v] (투명이면 밑 몸 칸의 색 — tint 는 알파를 쓰지 않음), flag 0 = 겉옷 층을 그림",
                "pivots": PIVOTS, "joints": JOINTS, "variants": {},
                "defaults": DEFAULT_SKINS,
                "_defaults": "스킨 그림이 없는 사람: UUID.hashCode() 를 defaults 수로 나눈 나머지 → 바닐라 기본 스킨 (클라이언트와 같은 것) · 아이템 rpg:vrig/def/<형>/<뼈> + custom_model_data 문자열 0 = 이름"}
    total = 0
    for variant in ("wide", "slim"):
        bones = build(variant == "slim")
        vb = []
        for b in bones:
            mdir = os.path.join(OUT_MODELS, variant)
            idir = os.path.join(OUT_ITEMS, variant)
            os.makedirs(mdir, exist_ok=True)
            os.makedirs(idir, exist_ok=True)
            nb = len(b.layers[0][1])
            no = len(b.layers[1][1])
            for suffix, layer in (("", 0), ("_o", 1)):
                model = {"textures": {"0": "rpg:item/vrig/white", "particle": "rpg:item/vrig/white"}, "elements": b.layers[layer][0]}
                with open(os.path.join(mdir, b.name + suffix + ".json"), "w", encoding="utf-8") as fp:
                    json.dump(model, fp, separators=(",", ":"))
            # 몸 층 (색 0 ~ nb − 1) + 겉옷 층 (flag 0 이 참일 때 — 색 nb ~ nb + no − 1)
            base = {"type": "minecraft:model", "model": "rpg:item/vrig/%s/%s" % (variant, b.name),
                    "tints": [{"type": "minecraft:custom_model_data", "index": i, "default": -1} for i in range(nb)]}
            outer = {"type": "minecraft:model", "model": "rpg:item/vrig/%s/%s_o" % (variant, b.name),
                     "tints": [{"type": "minecraft:custom_model_data", "index": nb + i, "default": -1} for i in range(no)]}
            item = {"model": {"type": "minecraft:composite", "models": [
                base, {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0, "on_true": outer,
                       "on_false": {"type": "minecraft:empty"}}]}}
            with open(os.path.join(idir, b.name + ".json"), "w", encoding="utf-8") as fp:
                json.dump(item, fp, separators=(",", ":"))
            vb.append({"name": b.name, "item": "vrig/%s/%s" % (variant, b.name), "src": b.src})
            total += len(b.src)
        manifest["variants"][variant] = {"bones": vb}
    write_defaults()
    os.makedirs(os.path.dirname(OUT_MANIFEST), exist_ok=True)
    with open(OUT_MANIFEST, "w", encoding="utf-8") as fp:
        json.dump(manifest, fp, ensure_ascii=False, separators=(",", ":"))
    os.makedirs(os.path.dirname(OUT_SKIN), exist_ok=True)
    default_skin().save(OUT_SKIN)
    size = 0
    for d in (OUT_MODELS, OUT_ITEMS):
        for r, _, fs in os.walk(d):
            size += sum(os.path.getsize(os.path.join(r, f)) for f in fs)
    print("rig: faces %d (wide + slim), models + items %.0f KB, manifest %.0f KB" % (total, size / 1024, os.path.getsize(OUT_MANIFEST) / 1024))


# ------------------------------------------------------------------ 미리보기 (서버와 같은 변환으로 그림 — 면 · UV 확인)

def rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def preview(out_dir, skin_path=None):
    skin = Image.open(skin_path).convert("RGBA") if skin_path else default_skin()
    spx = skin.load()
    os.makedirs(out_dir, exist_ok=True)
    poses = {
        "rest": {},
        "swing": {"rarm": (-2.2, 0.0, 0.3), "rarm_bend": -0.9, "larm": (0.4, 0, -0.2), "head": (0.2, 0.3, 0), "rleg": (0.4, 0, 0), "lleg": (-0.4, 0, 0),
                  "lleg_bend": 0.6},
    }
    for variant in ("wide", "slim"):
        bones = {b.name: b for b in build(variant == "slim")}
        for pname, pose in poses.items():
            quads = []
            for name, b in bones.items():
                part = name.split("_")[0]
                piv = np.array(PIVOTS[variant][part], float)
                rot = pose.get(part, (0, 0, 0))
                R = rot_zyx(rot[0], rot[1], rot[2])
                bend = pose.get(part + "_bend", 0.0) if name.endswith("_l") else 0.0
                jt = np.array(JOINTS.get(part, [0, 0, 0]), float)
                Rb = rot_zyx(bend, 0, 0)
                for e, s in zip(b.elements, b.src):
                    lo = np.array(e["from"])
                    hi = np.array(e["to"])
                    # 아이템 모델 → 모델 공간 v (뼈 자리 기준)
                    a = (lo - 8) / F
                    c = (hi - 8) / F
                    corners = []
                    face = list(e["faces"].keys())[0]
                    pts = box_face_points(a, c, face)
                    for p in pts:
                        q = p.copy()
                        if bend:
                            q = Rb @ (q - jt) + jt
                        q = R @ q + piv
                        # 몸 (S(−1, −1, 1) · 0.9375 · 1.501) → 세상 (위 = +y, 앞 = −z → 보는 쪽 +z 로 돌림)
                        wv = np.array([-q[0], -q[1], q[2]]) / 16 * 0.9375 + np.array([0, 1.501 * 0.9375, 0])
                        corners.append(np.array([-wv[0], wv[1], -wv[2]]))
                    if isinstance(s, dict):
                        cs = [spx[u, v] for u, v in s["avg"]]
                        col = tuple(int(sum(c[i] for c in cs) / len(cs)) for i in range(3)) + (255,)
                    else:
                        col = spx[s[0], s[1]]
                        if s[2] == 0:
                            col = col[:3] + (255,)
                    if col[3] == 0:
                        continue
                    quads.append((np.array(corners), col))
            sheet = []
            for dname, dv in (("front", (0.0, 0.25, 1.0)), ("back", (0.2, 0.3, -1.0)), ("side", (1.0, 0.2, 0.3))):
                sheet.append(render(quads, np.array(dv), label="%s %s %s" % (variant, pname, dname)))
            im = Image.new("RGB", (sum(s.size[0] for s in sheet), sheet[0].size[1]))
            x = 0
            for s in sheet:
                im.paste(s, (x, 0))
                x += s.size[0]
            path = os.path.join(out_dir, "rig_%s_%s.png" % (variant, pname))
            im.save(path)
            print(path)


def box_face_points(a, c, face):
    """아이템 모델의 면 (한쪽 두께 0) → 모델 공간 v 의 네 꼭짓점"""
    lo = np.minimum(a, c)
    hi = np.maximum(a, c)
    xs, ys, zs = (lo[0], hi[0]), (lo[1], hi[1]), (lo[2], hi[2])
    pts = []
    for x in xs:
        for y in ys:
            for z in zs:
                pts.append(np.array([x, y, z]))
    # 두께 0 인 축을 뺀 네 점 (중복 제거) — 그리기 순서
    uniq = []
    for p in pts:
        if not any(np.allclose(p, q) for q in uniq):
            uniq.append(p)
    if len(uniq) != 4:
        return uniq[:4]
    c0 = np.mean(uniq, axis=0)
    n = np.cross(uniq[1] - uniq[0], uniq[2] - uniq[0])
    ax = np.argmax(np.abs(n))
    other = [i for i in range(3) if i != ax]
    uniq.sort(key=lambda p: math.atan2(p[other[1]] - c0[other[1]], p[other[0]] - c0[other[0]]))
    return uniq


def render(quads, view, W=360, H=480, label=""):
    view = view / np.linalg.norm(view)
    up = np.array([0.0, 1.0, 0.0])
    right = np.cross(up, view)
    right /= np.linalg.norm(right)
    up2 = np.cross(view, right)
    center = np.array([0, 0.95, 0])
    scale = 220
    im = Image.new("RGB", (W, H), (150, 186, 232))
    d = ImageDraw.Draw(im)
    items = []
    for corners, col in quads:
        cen = corners.mean(axis=0)
        n = np.cross(corners[1] - corners[0], corners[2] - corners[0])
        depth = float(np.dot(cen - center, view))
        pts = [(W / 2 + float(np.dot(p - center, right)) * scale, H / 2 - float(np.dot(p - center, up2)) * scale) for p in corners]
        items.append((depth, pts, col))
    items.sort(key=lambda t: t[0])
    for _, pts, col in items:
        d.polygon(pts, fill=tuple(col[:3]))
    d.text((6, 4), label, fill=(20, 20, 30))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", default=None)
    ap.add_argument("--skin", default=None)
    a = ap.parse_args()
    if a.preview:
        preview(a.preview, a.skin)
        return
    write(a)


if __name__ == "__main__":
    main()
