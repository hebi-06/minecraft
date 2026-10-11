#!/usr/bin/env python3
"""탈것 아이템 자원 만들기 (6단계): src/main/resources/mmorpg/mounts.json 의 탈것마다

tools/mount_items.py

- 그림: assets/rpg/textures/item/mount/<키>.png (32×32) — 탈것 초상 (mounts.json 의 portrait: 자원 폴더 안 경로, 64×64 정도의 탈것 그림) 을
  줄이고, 둘레에 탈것 색 (color) 의 은은한 빛을 깖. 초상이 없으면 탈것 색의 고리 (빈 소환 문양)
- 아이템 정의 · 모델: assets/rpg/items/<아이템 이름>.json · assets/rpg/models/item/<아이템 이름>.json
- 이름: assets/rpg/lang/ko_kr.json · en_us.json 의 item.rpg.<아이템 이름> = "<이름> 탈것" (게임 안 이름은 MountItem 이 정함 — 목록 · 명령어 피드백용)

새 탈것: mounts.json 에 항목 (+ portrait) 을 더하고 이 도구와 tools/bedrock_entities.py 를 다시 돌림.
"""
import json
import math
import os

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
RPG = os.path.join(RES, "assets", "rpg")
SIZE = 32


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def save_json(p, d):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")


def rgb(c):
    return tuple(max(0, min(255, round(v * 255))) for v in c[:3])


def glow_under(img, color, radius):
    """그림의 불투명한 곳 둘레에 color 빛 (흐린 테두리) 을 깔고 그 위에 그림"""
    a = img.getchannel("A").point(lambda v: 255 if v > 160 else 0)
    g = a.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(radius))
    g = g.point(lambda v: min(255, int(v * 1.6)))
    layer = Image.new("RGBA", img.size, color + (0,))
    layer.putalpha(g)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(layer)
    out.alpha_composite(img)
    return out


def ring_icon(color):
    """초상이 없을 때: 탈것 색의 소환 고리 + 가운데 반짝임"""
    s = SIZE * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = img.load()
    c = s / 2
    for y in range(s):
        for x in range(s):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c) / (s / 2)
            v = max(0.0, 1 - abs(r - 0.72) / 0.08) + max(0.0, 1 - abs(r - 0.5) / 0.04) * 0.6 + max(0.0, 1 - r / 0.22) * 0.9
            if v > 0:
                px[x, y] = color + (min(255, int(255 * v)),)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def icon(m):
    color = rgb(m.get("color", [1, 1, 1]))
    portrait = m.get("portrait")
    if portrait:
        src = Image.open(os.path.join(RES, portrait)).convert("RGBA")
        small = src.resize((SIZE, SIZE), Image.LANCZOS)
        return glow_under(small, color, 1.2)
    return ring_icon(color)


def main():
    mounts = load(os.path.join(RES, "mmorpg", "mounts.json"))["mounts"]
    names = {}
    for key, m in mounts.items():
        item = m.get("item", "")
        if not item.startswith("rpg:"):
            continue
        path = item.split(":", 1)[1]
        out = os.path.join(RPG, "textures", "item", "mount", key + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        icon(m).save(out)
        save_json(os.path.join(RPG, "models", "item", path + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "rpg:item/mount/" + key}})
        save_json(os.path.join(RPG, "items", path + ".json"), {"model": {"type": "minecraft:model", "model": "rpg:item/" + path}})
        names["item.rpg." + path] = m.get("name", key) + " 탈것"
        print("mount item", item, "->", os.path.relpath(out, ROOT))
    for lang in ("ko_kr", "en_us"):
        p = os.path.join(RPG, "lang", lang + ".json")
        d = load(p)
        d.update(names)
        save_json(p, d)


if __name__ == "__main__":
    main()
