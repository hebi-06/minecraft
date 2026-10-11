#!/usr/bin/env python3
"""룬 돌파 배지 (8단계): 룬 아이콘 오른쪽 아래에 돌파 단계 숫자 (1 ~ 6) 를 얹음.

tools/rune_break_badges.py

- assets/rpg/textures/item/rune/break_<n>.png (32×32, 오른쪽 아래 둥근 배지 + 숫자 — 1~2 하늘색 · 3~5 보라 · 6 금빛)
- assets/rpg/models/item/rune/break_<n>.json (generated 모델 — 배지만)
- assets/rpg/items/rune_<계열>_<등급>.json (아이템 정의): minecraft:custom_model_data 첫 수 (= 돌파 단계, Breakthrough.write) 로
  range_dispatch → 1 이상이면 원래 룬 모델 + 그 단계 배지 모델 (composite), 0 이면 원래 모델 그대로
계열 · 등급 목록은 규칙 코어 (rules.json RUNE_FAMILY_ORDER), 단계 수는 rune_break.json max.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
GRADES = ["common", "rare", "noble", "hero", "transcend"]

# 5×7 숫자
DIGITS = {
    1: ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    2: [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    3: [".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."],
    4: ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    5: ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    6: ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
}
# 단계 색 (배지 바탕 · 테두리)
COLORS = {
    "low": ((40, 120, 200), (150, 225, 255)),
    "mid": ((110, 40, 175), (225, 160, 255)),
    "max": ((170, 110, 10), (255, 225, 110)),
}


def tier(n, tiers):
    if n >= tiers[-1]:
        return "max"
    if n >= tiers[0]:
        return "mid"
    return "low"


def badge(n, tiers):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    fill, rim = COLORS[tier(n, tiers)]
    cx, cy, r = 25.5, 25.5, 6.2
    for y in range(18, 32):
        for x in range(18, 32):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= r - 1.2:
                # 바탕: 위가 조금 밝게
                k = 1.0 + 0.25 * (cy - (y + 0.5)) / r
                px[x, y] = tuple(max(0, min(255, int(c * k))) for c in fill) + (255,)
            elif d <= r:
                px[x, y] = rim + (255,)
            elif d <= r + 1.0:
                px[x, y] = (20, 16, 28, 255)  # 바깥 어두운 테두리 (밝은 룬 위에서도 보이게)
    rows = DIGITS[n]
    ox, oy = 23, 22
    digit = {(ox + i, oy + j) for j, row in enumerate(rows) for i, ch in enumerate(row) if ch == "#"}
    for x, y in digit:
        s = (x + 1, y + 1)  # 오른쪽 아래 그림자
        if s not in digit and s[0] < 32 and s[1] < 32 and px[s][3]:
            px[s] = (25, 18, 30, 255)
    for x, y in digit:
        px[x, y] = (255, 255, 255, 255)
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    rules = json.load(open(os.path.join(CORE, "rules.json"), encoding="utf-8"))
    rb = json.load(open(os.path.join(CORE, "rune_break.json"), encoding="utf-8"))
    levels = int(rb["max"])
    tiers = sorted(int(t) for t in rb["tiers"])
    tex = os.path.join(RES, "textures", "item", "rune")
    os.makedirs(tex, exist_ok=True)
    for n in range(1, levels + 1):
        badge(n, tiers).save(os.path.join(tex, f"break_{n}.png"))
        write_json(os.path.join(RES, "models", "item", "rune", f"break_{n}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/break_{n}"}})
    count = 0
    for fam in rules["RUNE_FAMILY_ORDER"]:
        for g in GRADES:
            name = f"rune_{fam}_{g}"
            base = {"type": "minecraft:model", "model": f"rpg:item/{name}"}
            entries = [{"threshold": n, "model": {"type": "minecraft:composite", "models": [
                base, {"type": "minecraft:model", "model": f"rpg:item/rune/break_{n}"}]}} for n in range(1, levels + 1)]
            write_json(os.path.join(RES, "items", f"{name}.json"), {"model": {
                "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                "fallback": base, "entries": entries}})
            count += 1
    print(f"badges: {levels} · rune item definitions: {count}")


if __name__ == "__main__":
    main()
