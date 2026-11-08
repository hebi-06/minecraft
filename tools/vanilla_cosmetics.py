#!/usr/bin/env python3
"""모드 없는 클라이언트의 장식 (2026-11-08 로드맵 3번 꾸미기) — 모드 장식 모델 (assets/mmorpg/cosmetic/<모델>.json — PieceModel, 자바 ModelPart
좌표: 아래 +y · 앞 -z · 사람의 왼쪽 +x) 을 바닐라 그림으로.

1. 투구 칸 아이템 (머리에 얹는 장식 — hideHelmet: 왕관 · 마법사 모자 · 고양이 귀 · 악마의 뿔 · 꽃 화관 · 얼음 왕관, 투구 스킨 — 성기사 · 빙룡):
   assets/rpg/items/vcos/<모델>.json = composite (뼈마다 모델 — 모델마다 display.head 변환). 바닐라 CustomHeadLayer 가 투구 칸 아이템을
   머리 움직임 그대로 그림 (투구 그림 — equippable asset 이 없을 때). 머리 기준: 아이템 공간 p = 1.6 · (j − (0, 0.25, 0)) (블록,
   j = 머리 뼈 가운데 (목) 에서 본 j 공간 — CustomHeadLayer.translateToHead: T(0, −0.25) · Ry(180) · S(0.625, −0.625, −0.625)).
2. 태우는 장식 (등 장식 — 망토 · 날개, 천사의 고리): 뼈마다 아이템 (assets/rpg/items/vcos/<모델>/<뼈>.json) — 서버 (VanillaCosmetics) 가
   플레이어에 태운 아이템 표시 엔티티로 몸 방향 · 웅크림 · 날갯짓을 줌. 목록 mmorpg/vanilla_cosmetics.json ("rigs").
3. 갑옷 스킨 (갑옷 · 바지 · 신발): 바닐라 갑옷 그림 (equipment asset rpg:<세트> — 그림 _1 · _2 의 위 반 64 × 32 = 바닐라 갑옷 칸 자리 그대로).
   덧붙은 상자 (어깨 받이 · 등 가시 · 발톱) 는 없음.

j 공간 (vanilla_models.py 와 같음 — 위 +y · 앞 -z · 사람의 오른쪽 +x, 픽셀): ModelPart 점 m → j = (−m.x, −m.y, m.z).
ModelPart 회전 (x, y, z) (rotationZYX) → j 공간 Rz(z) · Ry(−y) · Rx(−x). 뼈 모델 좌표 = k · (뼈 가운데에서 본 j) + c0 (vanilla_models.fit).

  python3 tools/vanilla_cosmetics.py           # 만들기
  python3 tools/vanilla_cosmetics.py --check   # 숫자만
"""
import argparse
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402
import vanilla_models as vm  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
COSMETICS = os.path.join(RES, "mmorpg", "cosmetics.json")
PIECES = os.path.join(RES, "assets", "mmorpg", "cosmetic")
TEX_SRC = os.path.join(RES, "assets", "mmorpg", "textures", "entity", "cosmetic")
RPG = os.path.join(RES, "assets", "rpg")
OUT_MODELS = os.path.join(RPG, "models", "item", "vcos")
OUT_ITEMS = os.path.join(RPG, "items", "vcos")
OUT_TEX = os.path.join(RPG, "textures", "item", "vcos")
OUT_EQUIP = os.path.join(RPG, "equipment")
OUT_EQUIP_TEX = os.path.join(RPG, "textures", "entity", "equipment")
OUT_MANIFEST = os.path.join(RES, "mmorpg", "vanilla_cosmetics.json")

HEAD_SCALE = 1.6    # 아이템 공간 1 블록 = 머리 0.625 블록 (CustomHeadLayer)
HEAD_LIFT = 0.25    # 머리 가운데 = 목 위 4px
GLOW_INFLATE = 0.02


# ------------------------------------------------------------------ 행렬

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def affine(lin=None, t=None):
    m = np.eye(4)
    if lin is not None:
        m[:3, :3] = lin
    if t is not None:
        m[:3, 3] = t
    return m


def part_rotation(rot):
    """ModelPart 회전 (도) → j 공간 회전: Rz(z) · Ry(−y) · Rx(−x)"""
    x, y, z = (math.radians(float(v)) for v in rot)
    return rot_z(z) @ rot_y(-y) @ rot_x(-x)


def euler_xyz(r):
    """R = Rx(a) · Ry(b) · Rz(c) 의 (a, b, c) 도 — 자바 ItemTransform 은 Quaternionf.rotationXYZ (Rx · Ry · Rz)"""
    sb = max(-1.0, min(1.0, float(r[0, 2])))
    b = math.asin(sb)
    if abs(sb) < 0.999999:
        a = math.atan2(-r[1, 2], r[2, 2])
        c = math.atan2(-r[0, 1], r[0, 0])
    else:  # 짐벌 잠김: c = 0
        a = math.atan2(r[2, 1], r[1, 1])
        c = 0.0
    return [round(math.degrees(v), 4) + 0.0 for v in (a, b, c)]


# ------------------------------------------------------------------ PieceModel → 뼈

def part_cubes(part):
    """ModelPart 상자 → [(lo, hi, faces)] j 공간 · 뼈 가운데에서 (픽셀). 베드락 상자 (y 만 뒤집음 — x · z 같음) 로 바꿔 vanilla_models 와 같게"""
    out = []
    for c in part.get("cubes") or []:
        f = [float(v) for v in c["from"]]
        s = [float(v) for v in c["size"]]
        g = float(c.get("inflate", 0) or 0)
        u, v = (float(x) for x in c["uv"])
        ob = [f[0], -(f[1] + s[1]), f[2]]
        lo = [ob[i] - g for i in range(3)]
        hi = [ob[i] + s[i] + g for i in range(3)]
        a, z = vm.to3(lo), vm.to3(hi)
        out.append((np.minimum(a, z), np.maximum(a, z), geo_tex.box_faces(u, v, s, bool(c.get("mirror", False)))))
    return out


def walk(parts):
    """뼈 목록 (부모 먼저): name · parent · root (head · body …) · off (j 픽셀 — 부모 기준) · rot (ModelPart 도) · cubes"""
    bones = []

    def go(name, p, parent, root):
        pv = [float(x) for x in p.get("pivot") or [0, 0, 0]]
        bones.append({"name": name, "parent": parent, "root": root, "off": [-pv[0], -pv[1], pv[2]],
                      "rot": [float(x) for x in p.get("rot") or [0, 0, 0]], "cubes": part_cubes(p)})
        for child, cp in (p.get("children") or {}).items():
            go(child, cp, name, root)
    for name, p in parts.items():
        go(name, p, None, name)
    return bones


def chains(bones):
    """뼈마다 뿌리 (그 사람 몸 조각 — head · body) 기준 j 공간 변환 (블록, 4 × 4)"""
    world = {}
    for b in bones:
        m = affine(part_rotation(b["rot"]), np.array(b["off"]) / 16.0)
        world[b["name"]] = (world[b["parent"]] @ m) if b["parent"] else m
    return world


def anim_kind(name):
    for pre, kind in (("fw_flutter_low_left", "flutter_low_left"), ("fw_flutter_low_right", "flutter_low_right"),
                      ("fw_flutter_left", "flutter_left"), ("fw_flutter_right", "flutter_right"), ("fw_twitch_left", "twitch_left"),
                      ("fw_twitch_right", "twitch_right"), ("fw_cape", "cape"), ("fw_wing_left", "wing_left"),
                      ("fw_wing_right", "wing_right"), ("fw_spin", "spin"), ("fw_bob", "bob")):
        if name.startswith(pre):
            return kind
    return None


def tex_name(path):
    """mmorpg:textures/entity/cosmetic/paladin_1.png → paladin_1"""
    return os.path.splitext(path.split("/")[-1])[0]


def head_display(world, k, c0):
    """머리에 쓴 아이템의 뼈 모델 변환 (display.head): D = S(1.6) · T(0, −0.25, 0) · M · S(1 / k) · T((8 − c0) / 16)"""
    d = affine(np.eye(3) * HEAD_SCALE) @ affine(t=np.array([0, -HEAD_LIFT, 0])) @ world \
        @ affine(np.eye(3) / k) @ affine(t=(8.0 - np.array(c0)) / 16.0)
    lin = d[:3, :3]
    s = float(np.cbrt(np.linalg.det(lin)))
    r = lin / s
    t = d[:3, 3] * 16.0
    assert 0 < s <= 4.0 + 1e-6, s
    assert np.all(np.abs(t) <= 80 + 1e-6), t
    return {"rotation": euler_xyz(r), "translation": [round(float(x), 4) + 0.0 for x in t], "scale": [round(s, 5)] * 3}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    defs = json.load(open(COSMETICS, encoding="utf-8"))["items"]

    head_models, rig_models, sets = [], [], {}
    for key, d in defs.items():
        if key.startswith("_"):
            continue
        slot, model = d.get("slot"), d.get("model")
        if slot == "head" and model:
            (head_models if d.get("hideHelmet") else rig_models).append(model)
        elif slot == "back" and model:
            rig_models.append(model)
        elif slot == "helmet" and model:
            head_models.append(model)
        if slot in ("chest", "legs", "boots") and d.get("set"):
            sets.setdefault(d["set"], set()).add(slot)
    head_models = sorted(set(head_models))
    rig_models = sorted(set(rig_models))

    if not args.check:
        for p in (OUT_MODELS, OUT_ITEMS, OUT_TEX):
            if os.path.isdir(p):
                shutil.rmtree(p)
        for layer in ("humanoid", "humanoid_leggings"):
            for s in sets:
                p = os.path.join(OUT_EQUIP_TEX, layer, s + ".png")
                if os.path.exists(p):
                    os.remove(p)

    manifest = {"head": {}, "rigs": {}, "equipment": {}}
    used_tex = {}
    n_bones = n_glow = 0
    for model in head_models + rig_models:
        piece = json.load(open(os.path.join(PIECES, model + ".json"), encoding="utf-8"))
        tw, th = float(piece["tw"]), float(piece["th"])
        tex = tex_name(piece["texture"])
        glow_tex = tex_name(piece["emissive"]) if piece.get("emissive") else None
        used_tex[tex] = os.path.join(TEX_SRC, tex + ".png")
        mask = None
        if glow_tex:
            used_tex[glow_tex] = os.path.join(TEX_SRC, glow_tex + ".png")
            mask = vm.glow_checker(Image.open(used_tex[glow_tex]), tw, th)
        bones = walk(piece["parts"])
        world = chains(bones)
        is_head = model in head_models
        names, rig, head_list = set(), [], []
        for b in bones:
            bn = vm.slug(b["name"])
            while bn in names:
                bn += "_"
            names.add(bn)
            entry = {"name": b["name"], "parent": b["parent"], "root": b["root"], "off": [round(x, 4) for x in b["off"]],
                     "rot": [round(x, 4) for x in b["rot"]], "anim": anim_kind(b["name"])}
            if b["cubes"]:
                k, c0 = vm.fit(b["cubes"])
                rel = f"{model}/{bn}"
                base = {"textures": {"0": f"rpg:item/vcos/{tex}", "particle": f"rpg:item/vcos/{tex}"},
                        "elements": vm.elements(b["cubes"], k, c0, tw, th)}
                if is_head:
                    assert b["root"] == "head", (model, b["name"])
                    base["display"] = {"head": head_display(world[b["name"]], k, c0)}
                glow = None
                if mask is not None:
                    gels = vm.elements(b["cubes"], k, c0, tw, th, glow_mask=mask, inflate=GLOW_INFLATE / max(k, 1e-6), light=15)
                    if gels:
                        glow = {"textures": {"0": f"rpg:item/vcos/{glow_tex}", "particle": f"rpg:item/vcos/{glow_tex}"}, "elements": gels}
                        if "display" in base:
                            glow["display"] = base["display"]
                        n_glow += 1
                if not args.check:
                    vm.write_json(os.path.join(OUT_MODELS, rel + ".json"), base)
                    if not is_head:
                        vm.item_def(os.path.join(OUT_ITEMS, rel + ".json"), f"rpg:item/vcos/{rel}")
                    if glow:
                        vm.write_json(os.path.join(OUT_MODELS, rel + "_glow.json"), glow)
                        if not is_head:
                            vm.item_def(os.path.join(OUT_ITEMS, rel + "_glow.json"), f"rpg:item/vcos/{rel}_glow")
                head_list.append(f"rpg:item/vcos/{rel}")
                if glow:
                    head_list.append(f"rpg:item/vcos/{rel}_glow")
                entry.update({"item": f"rpg:vcos/{rel}", "glow": f"rpg:vcos/{rel}_glow" if glow else None,
                              "k": round(k, 6), "c0": [round(float(x), 5) for x in c0]})
                n_bones += 1
            rig.append(entry)
        if is_head:
            if not args.check:
                vm.write_json(os.path.join(OUT_ITEMS, model + ".json"),
                              {"model": {"type": "minecraft:composite", "models": [{"type": "minecraft:model", "model": m} for m in head_list]}})
            manifest["head"][model] = {"item": f"rpg:vcos/{model}", "models": head_list}
        else:
            roots = sorted({b["root"] for b in bones})
            assert len(roots) == 1, (model, roots)
            manifest["rigs"][model] = {"root": roots[0], "translucent": bool(piece.get("translucent")), "bones": rig}

    for s, slots in sorted(sets.items()):
        layers = {}
        for layer, src in (("humanoid", s + "_1.png"), ("humanoid_leggings", s + "_2.png")):
            if layer == "humanoid_leggings" and "legs" not in slots:
                continue
            if layer == "humanoid" and not (slots & {"chest", "boots"}):
                continue
            layers[layer] = [{"texture": f"rpg:{s}"}]
            if not args.check:
                img = Image.open(os.path.join(TEX_SRC, src)).convert("RGBA")
                os.makedirs(os.path.join(OUT_EQUIP_TEX, layer), exist_ok=True)
                img.crop((0, 0, 64, 32)).save(os.path.join(OUT_EQUIP_TEX, layer, s + ".png"))
        if not args.check:
            vm.write_json(os.path.join(OUT_EQUIP, s + ".json"), {"layers": layers})
        manifest["equipment"][s] = f"rpg:{s}"

    if not args.check:
        os.makedirs(OUT_TEX, exist_ok=True)
        for name, src in sorted(used_tex.items()):
            shutil.copy(src, os.path.join(OUT_TEX, name + ".png"))
        vm.write_json(OUT_MANIFEST, manifest)
    print(f"head {len(manifest['head'])}, rigs {len(manifest['rigs'])}, bones {n_bones} (glow {n_glow}), "
          f"textures {len(used_tex)}, equipment {len(manifest['equipment'])}")


if __name__ == "__main__":
    main()
