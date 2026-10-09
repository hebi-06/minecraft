#!/usr/bin/env python3
"""무기 3D 모델: 애드온 (베드락) 디자인 31종 → 자바 아이템 모델.

  python3 tools/weapon_models.py --import <애드온 폴더>   # 원본을 tools/weapon_art 로 가져옴 (geo · 팔레트 텍스처 · 1인칭 자세 · 무기 → 디자인)
  python3 tools/weapon_models.py                         # tools/weapon_art → src/main/resources/assets/rpg (모델 · 텍스처 · 아이템 정의)

만들어지는 것
- textures/item/model/<디자인>.png        16×16 팔레트 (빛나는 칸 = 애드온 알파 102 → 불투명, 그 칸을 쓰는 상자는 light_emission 15)
- models/item/model/<디자인>.json         상자 (애드온 weapon 뼈의 상자 그대로 — 모두 축 정렬 · 면마다 한 칸 색) + 손 자세 (display)
- items/<무기 ID>.json                    인벤토리 · 땅 · 액자 = 아이콘, 손 = 3D 모델

손 자세 (display) 는 애드온 화면과 같은 자리가 되도록 계산한다 (Blockbench 베드락 미리보기 규칙 — 애드온 generator/tools/attachable_emu.py 와 같은 값):
- 1인칭: 베드락 1인칭 (오른팔 회전 -95,45,115 · 위치 -13.5,-10,12 · 아이템 뼈 -7 · 카메라 (0, 27.41, 0) 에서 +z · 세로 시야 70)
  에서 무기 상자가 카메라 앞 어디에 있는지 → 자바 1인칭 (손 자리 0.56, -0.52, -0.72 · 세로 시야 70) 에 같은 자리
- 3인칭: 오른팔 기준 무기 자리 (베드락 rightItem → weapon 뼈 · 무기를 든 팔 18°) → 자바 ItemInHandLayer (팔 → X -90° · Y 180° · (1, 2, -10)/16)
좌표: 베드락 → three.js (x 반전, 오른손 좌표계) 를 모델 좌표로 쓴다. 자바 모델 상자는 -16 ~ 32 안이어야 해서 큰 무기는 줄여 넣고 display 배율로 키운다.
"""
import argparse
import glob
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")

GLOW_ALPHA = 102  # 애드온 텍스처에서 빛나는 칸
EXTENT_MIN, EXTENT_MAX = -16.0, 32.0  # 자바 모델 상자 범위 (CuboidModelElement)
MAX_TRANSLATION = 80.0  # display translation 한계 (5블록 = 80)
MAX_SCALE = 4.0

# 자바 1인칭 손 자리 (ItemInHandRenderer.applyItemArmTransform, 오른손, 들기 · 휘두르기 없음)
JAVA_FP_ARM = np.array([0.56, -0.52, -0.72])
# 자바 3인칭 (ItemInHandLayer): 팔 → X -90° → Y 180° → 이동 (1/16, 2/16, -10/16) (오른손)
JAVA_3P_OFFSET = np.array([1 / 16, 2 / 16, -10 / 16])

# 베드락 1인칭 카메라 (three.js 공간): (0, 27.41, 0) 에서 +z, 위 +y, 화면 오른쪽 = -x
BEDROCK_FP_CAMERA = np.array([0.0, 27.41, 0.0])


# ------------------------------------------------------------ 회전

def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def three_rot(deg):
    """three.js Euler (x, y, z) 도, 순서 ZYX → Rz · Ry · Rx (Blockbench 베드락 미리보기)"""
    x, y, z = (math.radians(v) for v in deg)
    return rz(z) @ ry(y) @ rx(x)


def euler_xyz_deg(R):
    """R = Rx(a) · Ry(b) · Rz(c) 의 (a, b, c) 도 — 자바 ItemTransform 은 Quaternionf.rotationXYZ (Rx · Ry · Rz)"""
    b = math.asin(max(-1.0, min(1.0, R[0, 2])))
    if abs(R[0, 2]) < 0.999999:
        a = math.atan2(-R[1, 2], R[2, 2])
        c = math.atan2(-R[0, 1], R[0, 0])
    else:  # 짐벌 잠금: c = 0
        a = math.atan2(R[2, 1], R[1, 1])
        c = 0.0
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def check_euler(R, deg):
    a, b, c = (math.radians(v) for v in deg)
    back = rx(a) @ ry(b) @ rz(c)
    assert np.allclose(back, R, atol=1e-6), (R, back)


# ------------------------------------------------------------ 베드락 뼈대 (attachable_emu 와 같은 규칙)

def to3(v):
    return np.array([-v[0], v[1], v[2]], float)


class Bone:
    def __init__(self, name, parent, pivot, rot):
        self.name = name
        self.parent = parent
        self.origin = to3(pivot or [0, 0, 0])
        r = rot or [0, 0, 0]
        self.rest_rot = np.array([-r[0], -r[1], r[2]], float)
        self.rot_set = None
        self.rot_add = np.zeros(3)
        self.pos_add = np.zeros(3)
        self.scale = 1.0


def skeleton(geo):
    bones = {}
    for b in geo["bones"]:
        bones[b["name"].lower()] = Bone(b["name"].lower(), (b.get("parent") or "").lower() or None, b.get("pivot"), b.get("rotation"))
    return bones


def world(bones, name, cache=None):
    cache = {} if cache is None else cache
    if name in cache:
        return cache[name]
    b = bones[name]
    if b.parent and b.parent in bones:
        pm = world(bones, b.parent, cache)
        p_origin = bones[b.parent].origin
    else:
        pm = np.eye(4)
        p_origin = np.zeros(3)
    rot = b.rot_set if b.rot_set is not None else b.rest_rot + b.rot_add
    m = np.eye(4)
    m[:3, :3] = three_rot(rot) * b.scale
    m[:3, 3] = b.origin - p_origin + b.pos_add
    w = pm @ m
    cache[name] = w
    return w


def pose_first_person(bones, anim):
    ra, ri = bones["rightarm"], bones["rightitem"]
    ra.rot_set = np.array([-95.0, 45, 115])
    ra.pos_add = np.array([-13.5, -10, 12])
    ri.pos_add = np.array([0, -7.0, 0])
    w = bones["weapon"]
    if "rotation" in anim:
        r = anim["rotation"]
        w.rot_add = np.array([-r[0], -r[1], r[2]], float)
    if "position" in anim:
        p = anim["position"]
        w.pos_add = np.array([-p[0], p[1], p[2]], float)
    if "scale" in anim:
        s = anim["scale"]
        assert abs(s[0] - s[1]) < 1e-9 and abs(s[1] - s[2]) < 1e-9, s
        w.scale = float(s[0])


def pose_third_person(bones):
    bones["rightarm"].rot_set = np.array([18.0, 0, 0])


# ------------------------------------------------------------ 변환

def split_affine(W):
    """4×4 → (회전, 균일 배율, 이동)"""
    M = W[:3, :3]
    s = np.cbrt(np.linalg.det(M))
    R = M / s
    assert np.allclose(R @ R.T, np.eye(3), atol=1e-6), "not a similarity"
    return R, s, W[:3, 3]


def display(R, s, t):
    """자바 display 항목 (translation 은 1/16 블록 단위)"""
    rot = euler_xyz_deg(R)
    check_euler(R, rot)
    tr = t * 16
    if np.any(np.abs(tr) > MAX_TRANSLATION) or s > MAX_SCALE:
        raise ValueError(f"display out of range: t={tr} s={s}")
    r6 = lambda v: [round(float(x), 4) for x in v]
    return {"rotation": r6(rot), "translation": r6(tr), "scale": r6([s, s, s])}


def convert(design, meta, out_models, out_textures):
    geo = json.load(open(os.path.join(ART, f"{design}.geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
    tex = Image.open(os.path.join(ART, f"{design}.png")).convert("RGBA")
    wb = next(b for b in geo["bones"] if b["name"].lower() == "weapon")
    P = np.array(wb.get("pivot") or [0, 0, 0], float)
    cubes = wb.get("cubes", [])

    # 모델 좌표: local = to3(p) - to3(P) (three.js, 오른손 좌표계), m = k · local + C0 (상자 범위 안으로)
    lo = np.full(3, 1e9)
    hi = np.full(3, -1e9)
    boxes = []
    for c in cubes:
        o = np.array(c["origin"], float)
        sz = np.array(c["size"], float)
        a = to3(o) - to3(P)
        b = to3(o + sz) - to3(P)
        f, t = np.minimum(a, b), np.maximum(a, b)
        lo, hi = np.minimum(lo, f), np.maximum(hi, t)
        boxes.append((f, t, c["uv"]))
    extent = float(np.max(hi - lo))
    room = EXTENT_MAX - EXTENT_MIN - 0.5
    k = min(1.0, room / extent)
    C0 = 8.0 - k * (lo + hi) / 2  # 상자 묶음 가운데를 모델 가운데 (8, 8, 8) 에

    # 텍스처: 쓰는 칸만 불투명 (빛나는 칸도 색 그대로 불투명)
    used = set()
    glow = set()
    for _, _, uv in boxes:
        for face in uv.values():
            u, v = (int(x) for x in face["uv"])
            used.add((u, v))
            if tex.getpixel((u, v))[3] == GLOW_ALPHA:
                glow.add((u, v))
    out_tex = Image.new("RGBA", tex.size, (0, 0, 0, 0))
    for (u, v) in used:
        r, g, b, a = tex.getpixel((u, v))
        out_tex.putpixel((u, v), (r, g, b, 255))
    out_tex.save(os.path.join(out_textures, f"{design}.png"))

    elements = []
    for f, t, uv in boxes:
        mf = k * f + C0
        mt = k * t + C0
        assert np.all(mf >= EXTENT_MIN - 1e-6) and np.all(mt <= EXTENT_MAX + 1e-6), (design, mf, mt)
        faces = {}
        emits = False
        for name, face in uv.items():
            u, v = (int(x) for x in face["uv"])
            if (u, v) in glow:
                emits = True
            faces[name] = {"uv": [u + 0.25, v + 0.25, u + 0.75, v + 0.75], "texture": "#0"}
        el = {"from": [round(float(x), 4) for x in mf], "to": [round(float(x), 4) for x in mt], "faces": faces}
        if emits:
            el["light_emission"] = 15
        elements.append(el)

    # 1인칭: 베드락 화면 자리 그대로
    bones = skeleton(geo)
    pose_first_person(bones, meta["fp"])
    R_w, sig, Wt = split_affine(world(bones, "weapon"))
    D = np.diag([-1.0, 1.0, -1.0])  # three.js 카메라 공간 → 자바 카메라 공간 (오른쪽 = -x · 앞 = +z → 오른쪽 +x · 앞 -z)
    R_fp = D @ R_w
    s_fp = sig / k
    J_P = D @ (Wt - BEDROCK_FP_CAMERA) / 16
    t_fp = J_P - JAVA_FP_ARM - R_fp @ (s_fp * (C0 / 16 - 0.5))

    # 3인칭: 오른팔 기준 무기 자리
    bones = skeleton(geo)
    pose_third_person(bones)
    cache = {}
    rel = np.linalg.inv(world(bones, "rightarm", cache)) @ world(bones, "weapon", cache)
    R_rel, sig3, rel_t = split_affine(rel)
    T3J = np.diag([-1.0, -1.0, 1.0])  # three.js (x 오른쪽 · y 위 · z 뒤) → 자바 모델 (x 왼쪽 · y 아래 · z 뒤)
    Q = rx(math.radians(-90)) @ ry(math.radians(180))
    R_3p = Q.T @ T3J @ R_rel
    s_3p = sig3 / k
    t_3p = Q.T @ T3J @ rel_t / 16 - JAVA_3P_OFFSET - R_3p @ (s_3p * (C0 / 16 - 0.5))

    model = {
        "credit": f"MMO RPG — 애드온 디자인 {design} (tools/weapon_models.py 가 만듦, 손으로 고치지 않음)",
        "texture_size": [16, 16],
        "textures": {"0": f"rpg:item/model/{design}", "particle": f"rpg:item/model/{design}"},
        "elements": elements,
        "display": {
            "firstperson_righthand": display(R_fp, s_fp, t_fp),
            "thirdperson_righthand": display(R_3p, s_3p, t_3p),
        },
    }
    with open(os.path.join(out_models, f"{design}.json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, ensure_ascii=False, indent=1)
    return {"cubes": len(elements), "glow": sum(1 for e in elements if "light_emission" in e), "k": round(k, 3),
            "fp": model["display"]["firstperson_righthand"], "tp": model["display"]["thirdperson_righthand"]}


def item_definition(weapon_id, design):
    path = weapon_id.split(":", 1)[1]
    icon = {"type": "minecraft:model", "model": f"rpg:item/{path}"}
    return {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "head", "on_shelf"], "model": icon}],
            "fallback": {"type": "minecraft:model", "model": f"rpg:item/model/{design}"},
        }
    }


# ------------------------------------------------------------ 가져오기 · 만들기

def do_import(addon):
    rp = os.path.join(addon, "RPG_RP")
    os.makedirs(ART, exist_ok=True)
    anims = json.load(open(os.path.join(rp, "animations", "rpg_weapons.animation.json"), encoding="utf-8"))["animations"]
    mapping = {}
    for f in glob.glob(os.path.join(rp, "attachables", "rpg", "*", "*.json")):
        d = json.load(open(f, encoding="utf-8"))["minecraft:attachable"]["description"]
        mapping[d["identifier"]] = d["geometry"]["default"].replace("geometry.rpg.art.", "")
    designs = {}
    for design in sorted(set(mapping.values())):
        shutil.copy(os.path.join(rp, "models", "entity", "rpg_weapons", f"{design}.geo.json"), os.path.join(ART, f"{design}.geo.json"))
        shutil.copy(os.path.join(rp, "textures", "entity", "rpg", "weapons", f"{design}.png"), os.path.join(ART, f"{design}.png"))
        fp = anims[f"animation.rpg.art.{design}.first_person"]["bones"]["weapon"]
        designs[design] = {"fp": fp}
    meta = {"source": "MMO RPG 애드온 RPG_RP (models/entity/rpg_weapons · textures/entity/rpg/weapons · attachables · animations/rpg_weapons)",
            "designs": designs, "weapons": dict(sorted(mapping.items()))}
    with open(os.path.join(ART, "designs.json"), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=1)
    print(f"imported {len(designs)} designs, {len(mapping)} weapon ids → {ART}")


def do_build():
    meta = json.load(open(os.path.join(ART, "designs.json"), encoding="utf-8"))
    out_models = os.path.join(ASSETS, "models", "item", "model")
    out_textures = os.path.join(ASSETS, "textures", "item", "model")
    for d in (out_models, out_textures):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    report = {}
    for design, m in sorted(meta["designs"].items()):
        report[design] = convert(design, m, out_models, out_textures)
    # 아이템 정의: 모드에 있는 무기만 (items/*.json 이 있는 것)
    n = 0
    for f in sorted(glob.glob(os.path.join(ASSETS, "items", "*.json"))):
        wid = "rpg:" + os.path.basename(f)[:-5]
        design = meta["weapons"].get(wid)
        if design is None:
            continue
        with open(f, "w", encoding="utf-8") as fh:
            json.dump(item_definition(wid, design), fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        n += 1
    for design, r in report.items():
        print(f"{design:28s} cubes {r['cubes']:3d} glow {r['glow']:2d} k {r['k']:.3f}  fp {r['fp']['rotation']} {r['fp']['translation']} {r['fp']['scale'][0]}  3p {r['tp']['rotation']} {r['tp']['translation']} {r['tp']['scale'][0]}")
    print(f"{len(report)} models, {n} item definitions")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--import", dest="addon", help="애드온 폴더 (RPG_RP 가 있는 곳) 에서 원본을 가져옴")
    a = ap.parse_args()
    if a.addon:
        do_import(a.addon)
    do_build()
