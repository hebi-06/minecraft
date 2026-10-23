#!/usr/bin/env python3
"""0.36.0 초월 무기 레벨 (45 · 50 · 55 · 60렙) 자원 만들기 — 40렙 초월 무기와 같은 디자인 · 아이콘.

  python3 tools/weapon_levels.py      # 아래를 만든 뒤 python3 tools/weapon_models.py (손에 든 모델 · 빛 알갱이 표 WeaponFxData)

규칙 (core/src/main/resources/mmorpg/rules_more.json — GRADE_LEVELS.transcend · TYPE_ONLY) 에 있는 초월 레벨 중 40 이 아닌 것마다:
- tools/weapon_art/designs.json 의 weapons 에 40렙과 같은 디자인
- assets/rpg/textures/item/<종류>/<id>.png (40렙 아이콘 그대로) · models/item/<id>.json (아이콘 모델) · items/<id>.json (weapon_models.py 가 다시 씀)
- assets/rpg/lang/*.json 의 item.rpg.<id> (40렙 이름의 [40렙] → [N렙] — 게임은 RpgWeaponItem.getName 을 쓰므로 표시용 예비)
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
DESIGNS = os.path.join(ROOT, "tools", "weapon_art", "designs.json")


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def transcend_levels():
    """종류 → 초월 레벨 (rules.json + rules_more.json 을 Rules.merge 처럼 겹침)"""
    base = json.load(open(os.path.join(RULES, "rules.json"), encoding="utf-8"))
    more = json.load(open(os.path.join(RULES, "rules_more.json"), encoding="utf-8"))
    grade_levels = dict(base["GRADE_LEVELS"])
    grade_levels.update({k: v for k, v in more.get("GRADE_LEVELS", {}).items() if not k.startswith("_")})
    type_only = dict(base["TYPE_ONLY"])
    type_only.update({k: v for k, v in more.get("TYPE_ONLY", {}).items() if not k.startswith("_")})
    types = list(base["TYPE_ORDER"]) + [t for t in more.get("TYPE_ORDER_MORE", []) if t not in base["TYPE_ORDER"]]
    for t in type_only:
        if t not in types:
            types.append(t)
    out = {}
    for t in types:
        levels = type_only[t].get("transcend", []) if t in type_only else grade_levels.get("transcend", [])
        if levels:
            out[t] = [int(l) for l in levels]
    return out


def main():
    meta = json.load(open(DESIGNS, encoding="utf-8"))
    langs = {}
    for name in ("en_us", "ko_kr"):
        p = os.path.join(ASSETS, "lang", f"{name}.json")
        langs[p] = json.load(open(p, encoding="utf-8"))
    n = 0
    for t, levels in transcend_levels().items():
        base = f"{t}_l40_transcend"
        design = meta["weapons"].get(f"rpg:{base}")
        if design is None:
            raise SystemExit(f"no design for rpg:{base}")
        for lv in levels:
            if lv == 40:
                continue
            wid = f"{t}_l{lv}_transcend"
            meta["weapons"][f"rpg:{wid}"] = design
            src_icon = os.path.join(ASSETS, "textures", "item", t, f"{base}.png")
            shutil.copyfile(src_icon, os.path.join(ASSETS, "textures", "item", t, f"{wid}.png"))
            write_json(os.path.join(ASSETS, "models", "item", f"{wid}.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"rpg:item/{t}/{wid}"}})
            item_path = os.path.join(ASSETS, "items", f"{wid}.json")
            if not os.path.exists(item_path):
                write_json(item_path, {"model": {"type": "minecraft:model", "model": f"rpg:item/{wid}"}})
            for p, lang in langs.items():
                key = f"item.rpg.{base}"
                if key in lang:
                    lang[f"item.rpg.{wid}"] = lang[key].replace("[40렙]", f"[{lv}렙]")
            n += 1
    meta["weapons"] = dict(sorted(meta["weapons"].items()))
    with open(DESIGNS, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=1)
    for p, lang in langs.items():
        write_json(p, lang)
    print(f"transcend weapon levels: {n} items")


if __name__ == "__main__":
    main()
