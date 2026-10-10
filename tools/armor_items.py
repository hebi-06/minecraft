#!/usr/bin/env python3
"""갑옷 · 재료 아이템 자원 만들기 (4단계): 애드온의 그림 · 모델을 자바 모드 자원으로.

tools/armor_items.py <애드온 폴더 (rpg_addon)>

- 갑옷 120: 아이콘 (애드온 빌드가 만든 32×32 — 종류 색 동그라미 포함) → assets/rpg/textures/item/armor/, 아이템 정의 · 모델
- 갑옷 입은 모델 (art/armor/<세트>/<세트>_<부위>.geo.json) → assets/mmorpg/armor/<세트>_<부위>.json (자바 사람 모델 좌표,
  뼈 head · body · right_arm · left_arm · right_leg · left_leg + 그 아래 뼈), 몸 텍스처 → assets/mmorpg/textures/entity/armor/
- 재료: 레이드 재료 4 · 보스 재료 12 · 몬스터 전리품 6 · 룬 조각 · NPC 설정 도구 → 아이콘 · 아이템 정의 · 모델

베드락 → 자바 좌표: x · z 그대로, y = 24 - y (자바 모델은 아래가 +y). 뼈 자리 (pivot) = (px, 24 - py, pz),
상자 = (ox - px, py - oy - sy, oz - pz) 크기 그대로, UV · 거울 · 부풀림 그대로. 뼈 회전 (도) = (rx, -ry, -rz) — 베드락은 x · y 회전이 반대.
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
MM = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
BONES = {"head": "head", "body": "body", "rightArm": "right_arm", "leftArm": "left_arm", "rightLeg": "right_leg", "leftLeg": "left_leg"}
PIECE_FILE = {"helmet": "helmet", "chest": "chestplate", "legs": "leggings", "boots": "boots"}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1 if path.endswith("geo.json") else 2)
        f.write("\n")


def item(name, tex):
    write_json(os.path.join(RPG, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
    write_json(os.path.join(RPG, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/{tex}"}})


def convert_geo(path):
    d = json.load(open(path, encoding="utf-8"))
    g = d["minecraft:geometry"][0]
    desc = g["description"]
    bones = g["bones"]
    by_name = {b["name"]: b for b in bones}
    parts = {}

    def pivot_of(b):
        p = b.get("pivot", [0, 0, 0])
        return [p[0], 24 - p[1], p[2]]

    def convert(b, parent_pivot):
        p = b.get("pivot", [0, 0, 0])
        cubes = []
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            if "rotation" in c:
                raise SystemExit(f"{path}: cube rotation not supported")
            cubes.append({
                "from": [o[0] - p[0], p[1] - o[1] - s[1], o[2] - p[2]],
                "size": s,
                "uv": c["uv"],
                "inflate": c.get("inflate", 0),
                "mirror": bool(c.get("mirror", False)),
            })
        jp = pivot_of(b)
        # 자식 뼈의 자리는 부모 뼈 자리에서 본 차이 (자바 PartPose 는 부모 기준)
        rel = [jp[0] - parent_pivot[0], jp[1] - parent_pivot[1], jp[2] - parent_pivot[2]] if parent_pivot else jp
        r = b.get("rotation", [0, 0, 0])
        out = {"pivot": rel, "rot": [r[0], -r[1], -r[2]], "cubes": cubes, "children": {}}
        for child in bones:
            if child.get("parent") == b["name"]:
                out["children"][child["name"]] = convert(child, jp)
        return out

    for b in bones:
        if b.get("parent"):
            if b["parent"] not in by_name:
                raise SystemExit(f"{path}: unknown parent {b['parent']}")
            continue
        if b["name"] not in BONES:
            raise SystemExit(f"{path}: unknown root bone {b['name']}")
        parts[BONES[b["name"]]] = convert(b, None)
    return {"tw": desc["texture_width"], "th": desc["texture_height"], "parts": parts}


def main():
    addon = sys.argv[1]
    rules = json.load(open(os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules.json"), encoding="utf-8"))
    tex_items = os.path.join(addon, "RPG_RP", "textures", "items", "rpg")
    art = os.path.join(addon, "art", "armor")
    n = 0
    # 갑옷 아이템 120 (그 등급에 있는 레벨만)
    for t in rules["ARMOR_TYPE_ORDER"]:
        for grade, levels in rules["ARMOR_GRADE_LEVELS"].items():
            for lv in levels:
                for piece in rules["ARMOR_PIECE_ORDER"]:
                    key = f"{t}_l{lv}_{grade}_{piece}"
                    os.makedirs(os.path.join(RPG, "textures", "item", "armor"), exist_ok=True)
                    shutil.copyfile(os.path.join(tex_items, "armor", f"{key}.png"), os.path.join(RPG, "textures", "item", "armor", f"{key}.png"))
                    item(f"armor_{key}", f"armor/{key}")
                    n += 1
    # 입은 모델 · 몸 텍스처 (세트마다)
    sets = sorted({tier["set"] for tier in rules["ARMOR_TIERS"].values()})
    for s in sets:
        for piece, fname in PIECE_FILE.items():
            m = convert_geo(os.path.join(art, s, f"{s}_{fname}.geo.json"))
            m["texture"] = f"mmorpg:textures/entity/armor/{s}_{2 if piece == 'legs' else 1}.png"
            write_json(os.path.join(MM, "armor", f"{s}_{piece}.json"), m)
        for i in (1, 2):
            os.makedirs(os.path.join(MM, "textures", "entity", "armor"), exist_ok=True)
            shutil.copyfile(os.path.join(art, s, f"{s}_{i}.png"), os.path.join(MM, "textures", "entity", "armor", f"{s}_{i}.png"))
    # 재료 (아이콘 이름 = 아이템 이름 뒤쪽)
    mats = []
    for m in rules["RAID_MATERIALS"].values():
        mats.append(m["item"])
    for m in rules["BOSS_MATERIALS"].values():
        mats.append(m["item"])
    for m in rules["MONSTER_DROPS"].values():
        mats.append(m["item"])
    mats.append(rules["RUNE_SMITH"]["shardItem"])
    mats.append("rpg:npc_tool")
    table = {}
    bp_items = os.path.join(addon, "RPG_BP", "items")
    for mid in mats:
        name = mid.split(":", 1)[1]
        for sub in ("materials", "loot", ""):
            src_json = os.path.join(bp_items, sub, f"{name}.json")
            if os.path.exists(src_json):
                comp = json.load(open(src_json, encoding="utf-8"))["minecraft:item"]["components"]
                table[mid] = {"name": comp["minecraft:display_name"]["value"], "glint": bool(comp.get("minecraft:glint", False)),
                              "stack": comp.get("minecraft:max_stack_size", 64)}
                break
        else:
            raise SystemExit(f"no BP item for {mid}")
        src = os.path.join(tex_items, f"{name}.png")
        if name.startswith("loot_"):
            src = os.path.join(tex_items, "loot", f"{name[5:]}.png")
        os.makedirs(os.path.join(RPG, "textures", "item", "material"), exist_ok=True)
        shutil.copyfile(src, os.path.join(RPG, "textures", "item", "material", f"{name}.png"))
        item(name, f"material/{name}")
        n += 1
    write_json(os.path.join(MM, "materials.json"), table)
    print(f"armor/material items: {n}, armor sets: {sets}")


if __name__ == "__main__":
    main()
