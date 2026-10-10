#!/usr/bin/env python3
"""룬 아이템 자원 만들기: 애드온의 룬 그림 (32×32) 을 복사하고 아이템 정의 · 모델을 씀.

tools/rune_items.py <애드온 폴더 (rpg_addon)>

- assets/rpg/textures/item/rune/rune_<계열>_<등급>.png (80) · rune_ticket.png · high_rune_ticket.png
- assets/rpg/items/<id>.json (아이템 정의) · assets/rpg/models/item/<id>.json (generated 모델)
계열 · 등급 목록은 규칙 코어 (rules.json RUNE_FAMILY_ORDER) 에서 읽음.
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
GRADES = ["common", "rare", "noble", "hero", "transcend"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def item(name, tex):
    write_json(os.path.join(RES, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
    write_json(os.path.join(RES, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/{tex}"}})


def main():
    addon = sys.argv[1]
    rules = json.load(open(os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules.json"), encoding="utf-8"))
    families = rules["RUNE_FAMILY_ORDER"]
    src = os.path.join(addon, "RPG_RP", "textures", "items", "rpg")
    out = os.path.join(RES, "textures", "item", "rune")
    os.makedirs(out, exist_ok=True)
    n = 0
    for fam in families:
        for g in GRADES:
            name = f"rune_{fam}_{g}"
            shutil.copyfile(os.path.join(src, "runes", f"{name}.png"), os.path.join(out, f"{name}.png"))
            item(name, name)
            n += 1
    for name in ("rune_ticket", "high_rune_ticket"):
        shutil.copyfile(os.path.join(src, f"{name}.png"), os.path.join(out, f"{name}.png"))
        item(name, name)
        n += 1
    print(f"rune items: {n}")


if __name__ == "__main__":
    main()
