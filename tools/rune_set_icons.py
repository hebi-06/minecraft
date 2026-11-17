#!/usr/bin/env python3
"""룬 재개발 (2026-11-17) 새 룬 30 의 아이템 그림 · 정의 · 모델 — 등급 테 (옛 힘 룬 그림의 테 그대로) + 룬마다 룬 글자 (엘더 푸타르크 · 묶음 글자) · 룬 색.

계열 · 스탯 표시는 없음 (사용자 "표시를 없애"): 글자 색은 그 룬의 색 (원소 · 컨셉), 테는 등급 색.

tools/rune_set_icons.py            그림 · items/*.json · models/item/*.json 을 씀
tools/rune_set_icons.py --sheet P   대조용 큰 그림 (P.png)
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
TEX = os.path.join(RES, "textures", "item", "rune")
GRADES = ["common", "rare", "noble", "hero", "transcend"]

# 글자: 9 × 15 칸의 선 (x0, y0, x1, y1 …  — 꺾인 선은 이어진 점)
G = {
    "dagaz": [[(0, 0), (0, 14)], [(8, 0), (8, 14)], [(0, 0), (8, 14)], [(8, 0), (0, 14)]],
    "jera": [[(4, 1), (1, 4), (4, 7)], [(4, 7), (7, 10), (4, 13)]],
    "raidho": [[(1, 0), (1, 14)], [(1, 0), (6, 3), (1, 7)], [(1, 7), (7, 14)]],
    "isa": [[(4, 0), (4, 14)]],
    "kenaz": [[(7, 1), (1, 7), (7, 13)]],
    "algiz": [[(4, 2), (4, 14)], [(4, 7), (0, 2)], [(4, 7), (8, 2)]],
    "sowilo": [[(7, 0), (2, 5), (7, 9), (2, 14)]],
    "naudiz": [[(4, 0), (4, 14)], [(1, 4), (7, 10)]],
    "perthro": [[(1, 0), (1, 14)], [(1, 0), (4, 3), (7, 0)], [(1, 14), (4, 11), (7, 14)]],
    "laguz": [[(2, 0), (2, 14)], [(2, 0), (7, 4)]],
    "uruz": [[(1, 14), (1, 1), (7, 4), (7, 14)]],
    "berkano": [[(1, 0), (1, 14)], [(1, 0), (6, 3), (1, 7), (6, 10), (1, 14)]],
    "fehu": [[(1, 0), (1, 14)], [(1, 5), (7, 0)], [(1, 9), (7, 4)]],
    "hagalaz": [[(1, 0), (1, 14)], [(7, 0), (7, 14)], [(1, 4), (7, 10)]],
    "ehwaz": [[(1, 14), (1, 0), (4, 4), (7, 0), (7, 14)]],
    "frost": [[(4, 0), (4, 14)], [(4, 6), (0, 2)], [(4, 6), (8, 2)], [(4, 8), (0, 12)], [(4, 8), (8, 12)]],
    "firestorm": [[(1, 0), (1, 14)], [(7, 0), (7, 14)], [(7, 2), (2, 7), (7, 12)]],
    "othala": [[(4, 0), (8, 5), (4, 10), (0, 5), (4, 0)], [(6, 8), (1, 14)], [(2, 8), (7, 14)]],
    "thurisaz": [[(2, 0), (2, 14)], [(2, 4), (7, 7), (2, 10)]],
    "ansuz": [[(1, 0), (1, 14)], [(1, 0), (7, 4)], [(1, 4), (7, 8)]],
    "eihwaz": [[(4, 0), (4, 14)], [(4, 0), (7, 3)], [(4, 14), (1, 11)]],
    "blizzard": [[(1, 0), (1, 14)], [(4, 0), (4, 14)], [(7, 0), (7, 14)], [(1, 4), (7, 10)]],
    "ingwaz": [[(4, 2), (8, 7), (4, 12), (0, 7), (4, 2)]],
    "mannaz": [[(1, 0), (1, 14)], [(7, 0), (7, 14)], [(1, 0), (7, 6)], [(7, 0), (1, 6)]],
    "tiwaz_down": [[(4, 0), (4, 14)], [(0, 9), (4, 14), (8, 9)]],
    "gebo": [[(0, 1), (8, 13)], [(8, 1), (0, 13)]],
    "moon": [[(7, 0), (4, 2), (3, 5), (3, 9), (4, 12), (7, 14)], [(5, 5), (8, 9)], [(8, 5), (5, 9)]],
    "meteor": [[(8, 0), (3, 10)], [(0, 7), (3, 10), (6, 13)]],
    "star": [[(4, 0), (4, 14)], [(0, 7), (8, 7)], [(1, 2), (7, 12)], [(7, 2), (1, 12)]],
    "wunjo": [[(2, 0), (2, 14)], [(2, 0), (7, 3), (2, 6)]],
}

# 룬 → (글자, 색) — 색은 그 룬의 원소 · 컨셉 (무지개 없음)
RUNES = {
    "earthbreaker": ("common", "dagaz", (232, 150, 70)),
    "boomerangaxe": ("common", "jera", (238, 92, 62)),
    "galerush": ("common", "raidho", (120, 236, 192)),
    "icelance": ("common", "isa", (150, 216, 255)),
    "salamander": ("common", "kenaz", (255, 132, 52)),
    "rampart": ("common", "algiz", (242, 214, 120)),
    "solarwave": ("rare", "sowilo", (255, 214, 92)),
    "chainarrow": ("rare", "naudiz", (190, 230, 112)),
    "shadowstrike": ("rare", "perthro", (222, 92, 232)),
    "undine": ("rare", "laguz", (92, 182, 255)),
    "aurochs": ("rare", "uruz", (226, 176, 92)),
    "grove": ("rare", "berkano", (152, 240, 122)),
    "fissureslam": ("noble", "fehu", (212, 122, 62)),
    "bladehail": ("noble", "hagalaz", (204, 226, 255)),
    "thundervolley": ("noble", "ehwaz", (255, 236, 112)),
    "frostburst": ("noble", "frost", (172, 240, 255)),
    "firestorm": ("noble", "firestorm", (255, 112, 62)),
    "fortress": ("noble", "othala", (208, 196, 172)),
    "titanhammer": ("hero", "thurisaz", (255, 172, 82)),
    "runejavelin": ("hero", "ansuz", (255, 240, 182)),
    "shadowblitz": ("hero", "eihwaz", (202, 82, 255)),
    "blizzard": ("hero", "blizzard", (202, 236, 255)),
    "gnome": ("hero", "ingwaz", (192, 152, 92)),
    "bastion": ("hero", "mannaz", (255, 226, 142)),
    "skyfall": ("transcend", "tiwaz_down", (255, 216, 122)),
    "berserker": ("transcend", "gebo", (255, 72, 62)),
    "lunaexec": ("transcend", "moon", (216, 226, 255)),
    "meteor": ("transcend", "meteor", (255, 122, 52)),
    "spiritking": ("transcend", "star", (255, 240, 202)),
    "immortaloath": ("transcend", "wunjo", (255, 210, 112)),
}

OX, OY = 11, 8  # 글자 칸의 왼쪽 위 (32 × 32 그림 — 옛 글자 자리)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def line(p0, p1):
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]


def glyph_pixels(name):
    pts = set()
    for poly in G[name]:
        for a, b in zip(poly, poly[1:]):
            for x, y in line(a, b):
                pts.add((x, y))
                pts.add((x + 1, y))  # 두 칸 굵기
    return pts


def frame(grade):
    """옛 힘 룬 그림에서 글자를 지운 등급 테 (안쪽은 줄마다 양 끝의 바탕 색을 이어 칠함)"""
    im = Image.open(os.path.join(TEX, f"rune_str_{grade}.png")).convert("RGBA")
    px = im.load()
    out = im.copy()
    po = out.load()
    for y in range(3, 25):
        left = px[9, y]
        right = px[22, y]
        for x in range(10, 22):
            t = (x - 9) / 13.0
            c = mix(left[:3], right[:3], t)
            po[x, y] = c + (255,)
    return out


def draw(name):
    grade, g, col = RUNES[name]
    im = frame(grade)
    po = im.load()
    pts = glyph_pixels(g)
    light = mix(col, (255, 255, 255), 0.45)
    dark = tuple(int(c * 0.32) for c in col)
    # 빛 무리 (글자 둘레 한 칸 — 바탕에 색을 조금)
    halo = set()
    for (x, y) in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in pts:
                    halo.add(q)
    for (x, y) in halo:
        X, Y = OX + x, OY + y
        if 0 <= X < 32 and 0 <= Y < 32:
            r, g2, b, a = po[X, Y]
            po[X, Y] = mix((r, g2, b), col, 0.28) + (a,)
    # 그림자 (오른쪽 아래 한 칸)
    for (x, y) in pts:
        q = (x + 1, y + 1)
        if q not in pts:
            X, Y = OX + q[0], OY + q[1]
            if 0 <= X < 32 and 0 <= Y < 32:
                po[X, Y] = dark + (255,)
    for (x, y) in pts:
        X, Y = OX + x, OY + y
        # 왼쪽 위 가장자리는 밝게 (빛을 받은 면)
        edge = (x - 1, y) not in pts or (x, y - 1) not in pts
        po[X, Y] = (light if edge else col) + (255,)
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def item_json(name):
    base = {"type": "minecraft:model", "model": f"rpg:item/{name}"}
    entries = [{"threshold": i, "model": {"type": "minecraft:composite", "models": [dict(base), {"type": "minecraft:model", "model": f"rpg:item/rune/break_{i}"}]}}
               for i in range(1, 7)]
    return {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0, "fallback": base, "entries": entries}}


def main():
    if "--sheet" in sys.argv:
        out = sys.argv[sys.argv.index("--sheet") + 1]
        names = list(RUNES)
        S = 8
        sheet = Image.new("RGBA", (6 * (32 * S + 16), 5 * (32 * S + 16)), (34, 34, 42, 255))
        for i, n in enumerate(names):
            im = draw(n).resize((32 * S, 32 * S), Image.NEAREST)
            gi = GRADES.index(RUNES[n][0])
            col = [k for k in names if RUNES[k][0] == RUNES[n][0]].index(n)
            sheet.alpha_composite(im, (col * (32 * S + 16), gi * (32 * S + 16)))
        sheet.save(out)
        print("sheet", out)
        return
    for n, (grade, _, _) in RUNES.items():
        rid = f"rune_{n}_{grade}"
        draw(n).save(os.path.join(TEX, rid + ".png"))
        write_json(os.path.join(RES, "items", rid + ".json"), item_json(rid))
        write_json(os.path.join(RES, "models", "item", rid + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/{rid}"}})
    print("wrote", len(RUNES), "rune icons")


if __name__ == "__main__":
    main()
