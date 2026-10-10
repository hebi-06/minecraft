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
  에서 무기 상자가 카메라 앞 어디에 있는지 → 자바 1인칭 (손 자리 0.56, -0.52, -0.72 · 세로 시야 70) 에 같은 자리.
  다만 칼 · 단검 · 도끼 · 창 · 마검 · 완드 (UPRIGHT) 는 손잡이 (weapon 뼈 축) 자리는 그대로 두고 날이 UPRIGHT_DIR 쪽 (거의 수직) 으로
  서도록 돌림 (가장 작은 회전). 애드온 자세는 날이 화면 가운데 쪽으로 약 35° 기울어 있었음 (2026-10-10 요청).
  날이 있는 무기 (EDGE_FORWARD — 완드 빼고) 는 세운 다음 날 축 둘레로 돌려 베는 날이 앞 (적 쪽) 을 향하게 함 (넓은 면은 옆, 같은 날 요청):
  세운 자세에서 화면 오른쪽을 향하던 날 (모델 -z 쪽 — 외날 도끼 · 단검 모두) 을 앞으로. 활 · 방패는 애드온 자세가 이미 서 있어 그대로.
- 3인칭: 오른팔 기준 무기 자리 (베드락 rightItem → weapon 뼈 · 무기를 든 팔 18°) → 자바 ItemInHandLayer (팔 → X -90° · Y 180° · (1, 2, -10)/16)
좌표: 베드락 → three.js (x 반전, 오른손 좌표계) 를 모델 좌표로 쓴다. 자바 모델 상자는 -16 ~ 32 안이어야 해서 큰 무기는 줄여 넣고 display 배율로 키운다.
"""
import argparse
import glob
import json
import math
import os
import re
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
# 1인칭에서 세워 드는 무기 (디자인 이름 앞부분) 와 날 방향 (자바 카메라 공간: 오른쪽 +x · 위 +y · 앞 -z) — 왼쪽으로 약 6° · 끝이 앞으로 약 13°
UPRIGHT = ("sword_", "dagger_", "axe_", "spear_", "greatsword_", "staff_")
UPRIGHT_DIR = np.array([-0.10, 0.97, -0.22]) / np.linalg.norm([-0.10, 0.97, -0.22])
# 세운 뒤 날 축 둘레로 돌려 날이 앞을 향하게 하는 무기 (완드는 날이 없어 장식 면이 화면 쪽 그대로)
EDGE_FORWARD = ("sword_", "dagger_", "axe_", "spear_", "greatsword_")
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

def min_rot(a, b):
    """a 를 b 로 돌리는 가장 작은 회전 (로드리게스)"""
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(a @ b)
    if np.linalg.norm(v) < 1e-12:
        assert c > 0, "opposite directions"
        return np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * (1 / (1 + c))


def axis_rot(axis, ang):
    """axis 둘레로 ang 라디안 (오른손 규칙)"""
    a = axis / np.linalg.norm(axis)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(ang) * K + (1 - math.cos(ang)) * K @ K


def edge_forward():
    """UPRIGHT_DIR 둘레의 회전: 화면 오른쪽 (+x) 을 날 축에 수직인 면에 투영한 것 → 앞 (-z) 의 투영"""
    d = UPRIGHT_DIR

    def proj(v):
        p = v - d * (v @ d)
        return p / np.linalg.norm(p)

    a, b = proj(np.array([1.0, 0, 0])), proj(np.array([0, 0, -1.0]))
    return axis_rot(d, math.atan2(np.cross(a, b) @ d, a @ b))


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
    if design.startswith(UPRIGHT):
        # 세워 들기: 날 (모델 +y) 이 UPRIGHT_DIR 를 향하게, 손잡이 (J_P) 를 축으로
        R_fp = min_rot(R_fp @ np.array([0.0, 1.0, 0.0]), UPRIGHT_DIR) @ R_fp
        if design.startswith(EDGE_FORWARD):
            # 베는 날이 앞으로 (날 축 둘레 — 모델 -z 쪽 날이 화면 오른쪽에서 앞으로)
            R_fp = edge_forward() @ R_fp
            assert (R_fp @ np.array([0, 0, -1.0]))[2] < -0.9, design
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

    # 빛 알갱이 자리 (weapon 뼈의 fx 위치) — 상자와 같은 규칙으로 옮김
    fx = None
    loc = (wb.get("locators") or {}).get("fx")
    if loc is not None:
        assert isinstance(loc, list) and len(loc) == 3, design
        local_l = to3(np.array(loc, float)) - to3(P)
        m_l = k * local_l + C0
        # 1인칭: 자바 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z), 오른손 · 휘두르기 없음 — 자바가 그리는 순서 (손 자리 → display → -0.5 → 모델 / 16)
        fx_fp = JAVA_FP_ARM + t_fp + R_fp @ (s_fp * (m_l / 16 - 0.5))
        if not design.startswith(UPRIGHT):
            # 세워 들기를 하지 않은 디자인은 베드락 화면 자리와 같아야 함
            assert np.allclose(fx_fp, D @ (Wt + sig * (R_w @ local_l) - BEDROCK_FP_CAMERA) / 16, atol=1e-9), design
        # 3인칭: 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준, 팔 회전 전)
        fx_arm = T3J @ (rel_t + sig3 * (R_rel @ local_l)) / 16
        # 확인: 자바가 모델을 그리는 순서로 계산해도 같은 자리
        via_arm = Q @ (JAVA_3P_OFFSET + t_3p + R_3p @ (s_3p * (m_l / 16 - 0.5)))
        assert np.allclose(via_arm, fx_arm, atol=1e-9), design
        fx = {"fp": [round(float(x), 5) for x in fx_fp], "arm": [round(float(x), 5) for x in fx_arm]}

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
            "fp": model["display"]["firstperson_righthand"], "tp": model["display"]["thirdperson_righthand"], "fx": fx}


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

# 빛 알갱이 (애드온 RPG_RP/particles/rpg/weapon_fx): 바닐라 particles.png 의 칸 → 자바 파티클 그림
VANILLA_PARTICLE_CELL = {(0, 0): "minecraft:generic_0", (0, 24): "minecraft:flame"}
RANDOM_COLOR = re.compile(r"^v\.particle_random_1 < ([\d.]+) \? ([\d.]+) : \(v\.particle_random_1 < ([\d.]+) \? ([\d.]+) : ([\d.]+)\)$")


def read_fx(rp, identifier, anim):
    """무기를 들고 있는 동안 fx 위치에서 계속 나오는 빛 알갱이 — 애드온 값 그대로 (움직임 · 크기 · 색 · 그림)"""
    assert anim == {"loop": True, "animation_length": 1, "particle_effects": {"0.0": {"effect": "fx", "locator": "fx"}}}, (identifier, anim)
    name = identifier.split(":", 1)[1].replace("_fx", "")
    pe = json.load(open(os.path.join(rp, "particles", "rpg", "weapon_fx", f"{name}.particle.json"), encoding="utf-8"))["particle_effect"]
    assert pe["description"]["identifier"] == identifier, identifier
    assert pe["description"]["basic_render_parameters"]["material"] == "particles_add", identifier
    assert pe["description"]["basic_render_parameters"]["texture"] == "textures/particle/particles", identifier
    c = pe["components"]
    # 1초짜리 발생기를 1초마다 다시 (애니메이션 loop 1초) = 들고 있는 동안 초당 spawn_rate 개
    assert c["minecraft:emitter_lifetime_once"] == {"active_time": 1}, identifier
    rate = c["minecraft:emitter_rate_steady"]
    shape = c["minecraft:emitter_shape_sphere"]
    assert shape.get("direction") == "outwards" and not shape.get("surface_only"), identifier
    motion = c["minecraft:particle_motion_dynamic"]
    acc = motion["linear_acceleration"]
    assert acc[0] == 0 and acc[2] == 0, identifier
    bb = c["minecraft:particle_appearance_billboard"]
    assert bb["facing_camera_mode"] == "lookat_xyz", identifier
    size_expr = "{} * (1 - v.particle_age / v.particle_lifetime)"
    m = re.match(r"^([\d.]+) \* \(1 - v\.particle_age / v\.particle_lifetime\)$", bb["size"][0])
    assert m and bb["size"][0] == bb["size"][1] == size_expr.format(m.group(1)), identifier
    uv = bb["uv"]
    assert (uv["texture_width"], uv["texture_height"], tuple(uv["uv_size"])) == (128, 128, (8, 8)), identifier
    texture = VANILLA_PARTICLE_CELL[tuple(uv["uv"])]
    color = c["minecraft:particle_appearance_tinting"]["color"]
    assert color[3] == 1, identifier
    if all(isinstance(v, (int, float)) for v in color[:3]):
        cuts, colors = [], [[float(v) for v in color[:3]]]
    else:  # 알갱이마다 무작위로 색 (v.particle_random_1 < a ? x : (… < b ? y : z))
        parts = [RANDOM_COLOR.match(v) for v in color[:3]]
        assert all(parts), identifier
        cuts = [float(parts[0].group(1)), float(parts[0].group(3))]
        assert all([float(p.group(1)), float(p.group(3))] == cuts for p in parts), identifier
        colors = [[float(p.group(i)) for p in parts] for i in (2, 4, 5)]
    return {
        "rate": float(rate["spawn_rate"]), "max": int(rate["max_particles"]),
        "radius": float(shape["radius"]), "speed": float(c["minecraft:particle_initial_speed"]),
        "life": float(c["minecraft:particle_lifetime_expression"]["max_lifetime"]),
        "accel": float(acc[1]), "drag": float(motion["linear_drag_coefficient"]),
        "size": float(m.group(1)), "texture": texture, "cuts": cuts, "colors": colors,
    }


def do_import(addon):
    rp = os.path.join(addon, "RPG_RP")
    os.makedirs(ART, exist_ok=True)
    anims = json.load(open(os.path.join(rp, "animations", "rpg_weapons.animation.json"), encoding="utf-8"))["animations"]
    mapping = {}
    fx_of = {}  # 디자인 → 빛 알갱이 파티클 이름 (영웅 · 초월 디자인의 attachable particle_effects.fx)
    for f in glob.glob(os.path.join(rp, "attachables", "rpg", "*", "*.json")):
        d = json.load(open(f, encoding="utf-8"))["minecraft:attachable"]["description"]
        design = d["geometry"]["default"].replace("geometry.rpg.art.", "")
        mapping[d["identifier"]] = design
        effects = d.get("particle_effects") or {}
        if effects:
            assert set(effects) == {"fx"} and "fx" in d["scripts"]["animate"], f
            assert fx_of.get(design, effects["fx"]) == effects["fx"], f
            fx_of[design] = effects["fx"]
    designs = {}
    for design in sorted(set(mapping.values())):
        shutil.copy(os.path.join(rp, "models", "entity", "rpg_weapons", f"{design}.geo.json"), os.path.join(ART, f"{design}.geo.json"))
        shutil.copy(os.path.join(rp, "textures", "entity", "rpg", "weapons", f"{design}.png"), os.path.join(ART, f"{design}.png"))
        fp = anims[f"animation.rpg.art.{design}.first_person"]["bones"]["weapon"]
        designs[design] = {"fp": fp}
        if design in fx_of:
            designs[design]["fx"] = read_fx(rp, fx_of[design], anims.get(f"animation.rpg.art.{design}.fx"))
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
    items = set()
    for f in sorted(glob.glob(os.path.join(ASSETS, "items", "*.json"))):
        wid = "rpg:" + os.path.basename(f)[:-5]
        design = meta["weapons"].get(wid)
        if design is None:
            continue
        items.add(wid)
        with open(f, "w", encoding="utf-8") as fh:
            json.dump(item_definition(wid, design), fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        n += 1
    for design, r in report.items():
        print(f"{design:28s} cubes {r['cubes']:3d} glow {r['glow']:2d} k {r['k']:.3f}  fp {r['fp']['rotation']} {r['fp']['translation']} {r['fp']['scale'][0]}  3p {r['tp']['rotation']} {r['tp']['translation']} {r['tp']['scale'][0]}")
    print(f"{len(report)} models, {n} item definitions")
    write_fx_java(meta, report, items)


def jf(v):
    return f"{float(v)!r}F"


def rgb(c):
    return "0x" + "".join(f"{round(max(0.0, min(1.0, x)) * 255):02X}" for x in c)


def write_fx_java(meta, report, items):
    """영웅 · 초월 무기의 빛 알갱이 → WeaponFxData.java (클라이언트가 들고 있는 무기마다 fx 자리에서 뿌림)"""
    specs = []
    for design, r in sorted(report.items()):
        fx = meta["designs"][design].get("fx")
        if fx is None:
            assert r["fx"] is None, design
            continue
        assert r["fx"] is not None, f"{design}: 파티클은 있는데 fx 위치가 없음"
        fp, arm = r["fx"]["fp"], r["fx"]["arm"]
        cuts = "new float[] {" + ", ".join(jf(c) for c in fx["cuts"]) + "}"
        colors = "new int[] {" + ", ".join(rgb(c) for c in fx["colors"]) + "}"
        tex = fx["texture"].split(":", 1)[1]
        specs.append((design, f'    private static final Spec {design.upper()} = new Spec("{design}", {jf(fp[0])}, {jf(fp[1])}, {jf(fp[2])}, '
                              f'{jf(arm[0])}, {jf(arm[1])}, {jf(arm[2])},\n            {jf(fx["rate"])}, {jf(fx["radius"])}, {jf(fx["speed"])}, '
                              f'{jf(fx["accel"])}, {jf(fx["drag"])}, {jf(fx["life"])}, {jf(fx["size"])}, "{tex}", {cuts}, {colors});'))
    with_fx = {d for d, _ in specs}
    entries = [f'            Map.entry("{wid.split(":", 1)[1]}", {design.upper()})' for wid, design in sorted(meta["weapons"].items())
               if design in with_fx and wid in items]
    body = "\n".join(line for _, line in specs)
    table = ",\n".join(entries)
    java = f'''package mmorpg.fabric.client;

import java.util.Map;

/**
 * 무기 빛 알갱이 (애드온 영웅 · 초월 무기 attachable 의 fx 파티클 — RPG_RP/particles/rpg/weapon_fx) — tools/weapon_models.py 가 만듦, 손으로 고치지 않음.
 * 무기를 들고 있는 동안 무기의 fx 자리에서 초당 rate 개: 반지름 radius 공 안 아무 곳에서 바깥쪽으로 speed, 위로 accel 가속 · drag 끌림,
 * 반 크기 size 에서 수명 life 동안 줄어들어 사라짐. 색은 무작위 r 이 cuts[i] 보다 작으면 colors[i] (없으면 마지막 색).
 *
 * @param fpX  1인칭 fx 자리: 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z), 오른손 · 휘두르기 없음 · 손 시야 70
 * @param armX 3인칭 fx 자리: 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤)
 */
public final class WeaponFxData {{
    private WeaponFxData() {{}}

    public record Spec(String design, float fpX, float fpY, float fpZ, float armX, float armY, float armZ, float rate, float radius, float speed,
            float accel, float drag, float life, float size, String texture, float[] cuts, int[] colors) {{}}

{body}

    /** 무기 ID (rpg: 뒤) → 빛 알갱이 */
    public static final Map<String, Spec> BY_ITEM = Map.ofEntries(
{table});
}}
'''
    out = os.path.join(ROOT, "src", "client", "java", "mmorpg", "fabric", "client", "WeaponFxData.java")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(java)
    print(f"weapon fx: {len(specs)} designs, {len(entries)} weapons → {os.path.relpath(out, ROOT)}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--import", dest="addon", help="애드온 폴더 (RPG_RP 가 있는 곳) 에서 원본을 가져옴")
    a = ap.parse_args()
    if a.addon:
        do_import(a.addon)
    do_build()
