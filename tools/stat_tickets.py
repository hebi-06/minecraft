#!/usr/bin/env python3
"""스탯 룬 뽑기권 그림 · 아이템 정의 · 모델 만들기 (7단계 스탯 트리).

tools/stat_tickets.py

룬 뽑기권 그림 (rune_ticket.png) 의 보라 룬 고리를 그 나무의 색 (stat_tree.json trees.<스탯>.color) 으로 바꾸고 가운데에 스탯 아이콘
(gui/sprites/ui/icon_<스탯>.png — 12×12) 을 얹음.
- assets/rpg/textures/item/rune/<스탯>_rune_ticket.png
- assets/rpg/items/<스탯>_rune_ticket.json · assets/rpg/models/item/<스탯>_rune_ticket.json
뽑기권 목록은 core/src/main/resources/mmorpg/stat_tree.json 의 tickets.
"""
import colorsys
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
UI = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "ui")
TREE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "stat_tree.json")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def ticket_image(base, color, icon):
    """보라 고리 → color, 가운데에 icon (12×12)"""
    t = base.copy()
    th, _, _ = colorsys.rgb_to_hsv(*color)
    w, h = t.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = t.getpixel((x, y))
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if 0.66 < hh < 0.86 and s > 0.18:
                nr, ng, nb = colorsys.hsv_to_rgb(th, min(1, s * 1.05), v)
                t.putpixel((x, y), (int(nr * 255), int(ng * 255), int(nb * 255), a))
    t.alpha_composite(icon.resize((12, 12), Image.NEAREST), (13, 10))
    return t


def main():
    data = json.load(open(TREE, encoding="utf-8"))
    base = Image.open(os.path.join(RES, "textures", "item", "rune", "rune_ticket.png")).convert("RGBA")
    out_dir = os.path.join(RES, "textures", "item", "rune")
    n = 0
    for stat, tk in data["tickets"].items():
        name = tk["item"].split(":", 1)[1]
        color = data["trees"][stat]["color"]
        icon = Image.open(os.path.join(UI, f"icon_{stat}.png")).convert("RGBA")
        ticket_image(base, color, icon).save(os.path.join(out_dir, f"{name}.png"))
        write_json(os.path.join(RES, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
        write_json(os.path.join(RES, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/{name}"}})
        n += 1
    print(f"stat tickets: {n}")


if __name__ == "__main__":
    main()
