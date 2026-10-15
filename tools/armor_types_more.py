#!/usr/bin/env python3
"""두 스탯 무기용 갑옷 아이콘 · 아이템 정의 · 모델 (0.12.5 — 자바 전용 갑옷 종류).

tools/armor_types_more.py

rules.json 위에 rules_more.json 을 덧붙인 ARMOR_TYPES · ARMOR_TYPE_ORDER 에서 애드온에 없던 종류 (마력+힘 · 힘+민첩 · 민첩+마력 ·
체력+힘 · 마력+체력) 마다 갑옷 24개 (일반 0 · 10 · 20 · 30 · 40렙 + 초월 40렙 × 부위 4):

- 아이콘: 애드온이 만든 힘+체력 아이콘 (assets/rpg/textures/item/armor/strvit_*.png — 갑옷 그림 + 오른쪽 아래 반반 배지) 의 배지만
  두 스탯 색으로 바꿈. 배지 자리 = 한 스탯 아이콘 넷 (힘 · 민첩 · 체력 · 마력) 과 힘+체력이 서로 다른 칸 (갑옷 그림은 같음 — 12 넘게
  다른 칸만, 그 밖은 아이콘 만들 때의 잡음). 배지 칸의 색은 배지 색 · 흰 빛 · 어두운 테두리 셋을 섞은 것으로 풀어 (3 × 3 연립),
  배지 색만 새 종류의 색 (ARMOR_TYPES badge — 위 왼쪽 반, badge2 — 아래 오른쪽 반) 으로 바꿔 다시 섞음. 어두운 테두리 · 흰 점은 그대로.
- 아이템 정의 · 모델: tools/armor_items.py 와 같은 모양 (assets/rpg/items · models/item — layer0 = rpg:item/armor/<키>)
- 입은 모습은 세트 (수련생 · 순찰대 · 강철 기사 · 흑요석 수호자 · 적룡 · 황제) 의 3D 모델을 그대로 씀 (종류와 상관없음)
"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
TEX = os.path.join(RPG, "textures", "item", "armor")
TEMPLATE = "strvit"
SINGLE = ("str", "dex", "vit", "int")
WHITE = np.array([255.0, 255.0, 255.0])
DARK = np.array([27.0, 20.0, 38.0])  # 배지 테두리 색 (힘+체력 아이콘의 어두운 칸)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def item(name, tex):
    write_json(os.path.join(RPG, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
    write_json(os.path.join(RPG, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/{tex}"}})


def rules():
    r = json.load(open(os.path.join(RES, "rules.json"), encoding="utf-8"))
    m = json.load(open(os.path.join(RES, "rules_more.json"), encoding="utf-8"))
    types = dict(r["ARMOR_TYPES"])
    for k, v in m.get("ARMOR_TYPES", {}).items():
        if not k.startswith("_"):
            types[k] = v
    order = m.get("ARMOR_TYPE_ORDER", r["ARMOR_TYPE_ORDER"])
    return r, types, order, r["ARMOR_TYPE_ORDER"]


def load(key):
    return np.array(Image.open(os.path.join(TEX, f"{key}.png")).convert("RGBA")).astype(float)


def badge_mask(key):
    ims = [load(f"{t}_{key}") for t in SINGLE + (TEMPLATE,)]
    m = np.zeros(ims[0].shape[:2], bool)
    for a in ims[1:]:
        m |= np.max(np.abs(a - ims[0]), axis=2) > 12
    return m


def colored(p):
    """배지 색이 섞인 칸인지 (어두운 테두리 · 흰 점 · 빈 칸이 아닌)"""
    r, g, b, a = p
    if a == 0:
        return False
    if 0.299 * r + 0.587 * g + 0.114 * b < 70:
        return False
    return min(r, g, b) <= 220


def second_half(p):
    """힘+체력 배지의 아래 오른쪽 반 (체력 분홍 — 파랑이 초록보다 확실히 큼)"""
    r, g, b = p[:3]
    return b > 100 and b > g + 40


def recolor(p, old, new):
    a = np.stack([np.array(old, float), WHITE, DARK], axis=1)
    k = np.linalg.solve(a, p[:3])
    return a[:, 1:] @ k[1:] + np.array(new, float) * k[0]


def make_icon(key, top, bottom, old_top, old_bottom):
    tpl = load(f"{TEMPLATE}_{key}")
    out = tpl.copy()
    m = badge_mask(key)
    for y, x in zip(*np.nonzero(m)):
        p = tpl[y, x]
        if not colored(p):
            continue
        if second_half(p):
            out[y, x, :3] = np.clip(recolor(p, old_bottom, bottom), 0, 255)
        else:
            out[y, x, :3] = np.clip(recolor(p, old_top, top), 0, 255)
    return Image.fromarray(np.round(out).astype(np.uint8), "RGBA")


def main():
    r, types, order, addon_order = rules()
    tpl = types[TEMPLATE]
    n = 0
    made = []
    for t in order:
        if t in addon_order:
            continue
        d = types[t]
        if "badge2" not in d:
            raise SystemExit(f"{t}: 두 스탯 종류만 (badge · badge2)")
        for grade, levels in r["ARMOR_GRADE_LEVELS"].items():
            for lv in levels:
                for piece in r["ARMOR_PIECE_ORDER"]:
                    key = f"l{lv}_{grade}_{piece}"
                    icon = make_icon(key, d["badge"], d["badge2"], tpl["badge"], tpl["badge2"])
                    icon.save(os.path.join(TEX, f"{t}_{key}.png"))
                    item(f"armor_{t}_{key}", f"armor/{t}_{key}")
                    n += 1
        made.append(t)
    print(f"armor items: {n} ({', '.join(made)})")


if __name__ == "__main__":
    main()
