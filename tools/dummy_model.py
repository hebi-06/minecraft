#!/usr/bin/env python3
"""훈련용 허수아비 자원 만들기 (4단계): 애드온의 모델 · 텍스처를 자바 모드 자원으로.

tools/dummy_model.py <애드온 폴더 (rpg_addon)>

- 모델 RPG_RP/models/entity/rpg_training_dummy.geo.json → assets/mmorpg/entity/training_dummy.json (자바 모델 좌표,
  뼈 root > body > arms · head — 클라이언트 DummyRenderer 가 읽어 ModelPart 를 만듦)
- 텍스처 RPG_RP/textures/entity/rpg/training_dummy.png (16×16 색 팔레트) → assets/mmorpg/textures/entity/training_dummy.png
- 소환 알 그림 (애드온 spawn_egg 색: 바탕 #c9aa7a · 무늬 #c4382c) → assets/rpg/textures/item/training_dummy_spawn_egg.png, 아이템 정의 · 모델

베드락 → 자바 좌표 (tools/armor_items.py 와 같음): x · z 그대로, y = 24 - y (자바 모델은 아래가 +y). 뼈 자리 (pivot) = (px, 24 - py, pz)
(자식 뼈는 부모 뼈 자리에서 본 차이), 상자 = (ox - px, py - oy - sy, oz - pz) 크기 그대로, 뼈 회전 (도) = (rx, -ry, -rz).
애드온 모델은 상자마다 여섯 면이 모두 같은 텍셀 하나 (팔레트 한 칸 — 면마다 UV) 를 씀: 자바 상자는 펼친 UV (box UV) 라서
상자마다 그 텍셀 (u, v) 만 적고, 클라이언트가 UV 분모를 키워 펼친 UV 전체가 그 텍셀 안에 들게 함 (DummyRenderer.CELL).
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
MM = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
# 클라이언트가 텍셀 한 칸을 CELL 칸으로 나눠 펼친 UV 를 넣음 (DummyRenderer.CELL 과 같게): 상자의 펼친 UV 폭 2·(깊이 + 너비) 와
# 높이 (깊이 + 높이) 가 CELL - 2 이하여야 이웃 텍셀에 번지지 않음
CELL = 64


def write_json(path, data, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def face_texel(path, cube):
    """상자의 여섯 면이 모두 같은 텍셀 하나 (uv_size 1×1) 인지 확인하고 그 텍셀 (u, v)"""
    uv = cube["uv"]
    if not isinstance(uv, dict):
        raise SystemExit(f"{path}: box UV not expected")
    cells = {(tuple(f["uv"]), tuple(f.get("uv_size", [1, 1]))) for f in uv.values()}
    if len(uv) != 6 or len(cells) != 1:
        raise SystemExit(f"{path}: cube {cube['origin']} faces differ: {uv}")
    (u, v), size = next(iter(cells))
    if tuple(size) != (1, 1):
        raise SystemExit(f"{path}: cube {cube['origin']} uv_size {size}")
    return [int(u), int(v)]


def convert_geo(path):
    d = json.load(open(path, encoding="utf-8"))
    g = d["minecraft:geometry"][0]
    desc = g["description"]
    bones = g["bones"]

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
            # 펼친 UV 가 한 텍셀 안에 드는지 (가로 2·(깊이 + 너비), 세로 깊이 + 높이)
            if 2 * (s[2] + s[0]) > CELL - 2 or s[2] + s[1] > CELL - 2:
                raise SystemExit(f"{path}: cube {o} too big for one texel cell")
            cubes.append({
                "from": [o[0] - p[0], p[1] - o[1] - s[1], o[2] - p[2]],
                "size": s,
                "tex": face_texel(path, c),
                "inflate": c.get("inflate", 0),
                "mirror": bool(c.get("mirror", False)),
            })
        jp = pivot_of(b)
        rel = [jp[0] - parent_pivot[0], jp[1] - parent_pivot[1], jp[2] - parent_pivot[2]] if parent_pivot else jp
        r = b.get("rotation", [0, 0, 0])
        out = {"pivot": rel, "rot": [r[0], -r[1], -r[2]], "cubes": cubes, "children": {}}
        for child in bones:
            if child.get("parent") == b["name"]:
                out["children"][child["name"]] = convert(child, jp)
        return out

    roots = {}
    names = {b["name"] for b in bones}
    for b in bones:
        if b.get("parent"):
            if b["parent"] not in names:
                raise SystemExit(f"{path}: unknown parent {b['parent']}")
            continue
        roots[b["name"]] = convert(b, None)
    return {"tw": desc["texture_width"], "th": desc["texture_height"], "cell": CELL, "bones": roots}


# 알 모양: 줄 (y) 마다 채우는 x 범위 — 위가 좁고 아래가 넓은 알 (가로 12 · 세로 15)
EGG_ROWS = {1: (6, 9), 2: (5, 10), 3: (5, 10), 4: (4, 11), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (2, 13), 10: (2, 13),
            11: (2, 13), 12: (2, 13), 13: (3, 12), 14: (4, 11), 15: (5, 10)}


def egg_icon(out_path, base, spots):
    """16×16 소환 알 그림 (알 + 어두운 테두리 · 왼쪽 위 빛 · 오른쪽 아래 그늘 + 무늬)"""
    from PIL import Image

    def shade(c, k):
        return tuple(max(0, min(255, int(round(x * k)))) for x in c) + (255,)

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    inside = {(x, y) for y, (a, b) in EGG_ROWS.items() for x in range(a, b + 1)}
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            img.putpixel((x, y), shade(base, 0.5))
            continue
        a, b = EGG_ROWS[y]
        k = 1.0
        if x <= a + 2 and y <= 8:  # 왼쪽 위 빛
            k = 1.12
        elif x >= b - 2 or y >= 13:  # 오른쪽 · 아래 그늘
            k = 0.82
        img.putpixel((x, y), shade(base, k))
    for (x, y), c in spots.items():
        if (x, y) in inside:
            img.putpixel((x, y), shade(c, 1.0))
    img.putpixel((6, 3), shade(base, 1.3))  # 빛 한 점
    img.save(out_path)


def main():
    addon = sys.argv[1]
    rp = os.path.join(addon, "RPG_RP")
    model = convert_geo(os.path.join(rp, "models", "entity", "rpg_training_dummy.geo.json"))
    model["texture"] = "mmorpg:textures/entity/training_dummy.png"
    write_json(os.path.join(MM, "entity", "training_dummy.json"), model)
    os.makedirs(os.path.join(MM, "textures", "entity"), exist_ok=True)
    shutil.copyfile(os.path.join(rp, "textures", "entity", "rpg", "training_dummy.png"), os.path.join(MM, "textures", "entity", "training_dummy.png"))

    client = json.load(open(os.path.join(rp, "entity", "rpg_training_dummy.entity.json"), encoding="utf-8"))
    egg = client["minecraft:client_entity"]["description"]["spawn_egg"]

    def rgb(h):
        h = h.lstrip("#")
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

    base, over = rgb(egg["base_color"]), rgb(egg["overlay_color"])
    dark = tuple(int(c * 0.75) for c in over)
    # 무늬: 과녁 (빨간 고리 + 가운데 점) — 훈련용 허수아비의 표적
    spots = {}
    for (x, y) in [(6, 8), (7, 7), (8, 7), (9, 8), (9, 9), (8, 10), (7, 10), (6, 9)]:  # 고리
        spots[(x, y)] = over
    for (x, y) in [(7, 8), (8, 9), (8, 8), (7, 9)]:  # 고리 안 (가운데 점은 어둡게)
        spots[(x, y)] = base
    spots[(7, 8)] = dark
    spots[(8, 9)] = dark
    for (x, y) in [(4, 11), (10, 4), (11, 12), (5, 5)]:  # 작은 점
        spots[(x, y)] = over
    os.makedirs(os.path.join(RPG, "textures", "item"), exist_ok=True)
    egg_icon(os.path.join(RPG, "textures", "item", "training_dummy_spawn_egg.png"), base, spots)
    write_json(os.path.join(RPG, "items", "training_dummy_spawn_egg.json"),
               {"model": {"type": "minecraft:model", "model": "rpg:item/training_dummy_spawn_egg"}}, 2)
    write_json(os.path.join(RPG, "models", "item", "training_dummy_spawn_egg.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "rpg:item/training_dummy_spawn_egg"}}, 2)

    def count(b):
        return len(b["cubes"]) + sum(count(c) for c in b["children"].values())

    print(f"training dummy: cubes {sum(count(b) for b in model['bones'].values())}, egg {egg}")


if __name__ == "__main__":
    main()
