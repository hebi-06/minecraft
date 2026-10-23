#!/usr/bin/env python3
"""9단계 새 몬스터 8종을 자바 모드 자원으로 (tools/bedrock_entities.py 가 끝에 부름 — 애드온 엔티티를 다시 옮겨도 남게).

tools/more_monsters.py
- 수치: core/src/main/resources/mmorpg/rules_more.json MONSTERS (region 이 있는 것 = 자바 판에만 있는 몬스터)
- 그림: tools/monster_art/<키>/ (<키>.geo.json · <키>.png · <키>.animation.json — tools/monster_art.py 가 만듦, 그렌델은 클로드 디자인 원본)
- 쓰는 곳 (있으면 바꿈 · 없으면 더함):
  · src/main/resources/mmorpg/entities.json — 행동 (애드온 monster_behavior 와 같은 모양: 체력 · 이동 · 충돌 상자 · 크기 · 맞으면 쫓아감 …)
  · assets/mmorpg/bedrock/entities.json — 그리기 (엔티티 설명 · 모델 · 동작 · 빛나는 텍스처 목록)
  · assets/mmorpg/textures/entity/rpg/monsters/<키>.png (+ _emissive.png — 알파가 255 보다 낮은 칸 = 빛남, 베드락 entity_emissive 와 같게)
  · assets/rpg/textures/item/<키>_spawn_egg.png · items · models (소환 알), assets/rpg/lang (이름)
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dummy_model import egg_icon  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
RPG = os.path.join(RES, "assets", "rpg")
MM = os.path.join(RES, "assets", "mmorpg")
ART = os.path.join(ROOT, "tools", "monster_art")
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")

# 소환 알 색 (바탕, 점)
EGGS = {
    "nisse": ("#c8322b", "#ecebe4"),
    "boar": ("#664632", "#eee4c8"),
    "sand_scorpion": ("#d6a45c", "#86f45a"),
    "crab": ("#d14e34", "#efd2a8"),
    "worm_larva": ("#e2cc94", "#9c8050"),
    "grendel": ("#7d8a78", "#3a4236"),
    "frost_wolf": ("#c4d8e6", "#9edcff"),
    "frost_giant": ("#7896ae", "#a8e2ff"),
    # 0.32.0 공허 몬스터
    "void_leaper": ("#2c2149", "#ff4fd8"),
    "void_stalker": ("#3a2b60", "#d6cdee"),
    "void_jelly": ("#2e2152", "#ff8ef0"),
    "void_borer": ("#2c2150", "#b46cff"),
    "void_behemoth": ("#1b1430", "#8e4cff"),
}
# 늘 트는 동작 (나머지 — attack · 특성 동작 — 는 Monsters 가 playAnimation)
ANIMATE = ["idle", "walk"]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data, indent=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent, separators=None if indent else (",", ":"))
        f.write("\n")


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def covered(geo, tw, th):
    """모델이 쓰는 텍스처 칸 (상자 UV 의 펼친 여섯 면)"""
    cells = set()
    for b in geo.get("bones", []):
        for c in b.get("cubes", []):
            u, v = c.get("uv", [0, 0])
            sx, sy, sz = (int(x // 1) for x in c.get("size", [0, 0, 0]))
            for (x0, y0, w, h) in ((u + sz, v, sx, sz), (u + sz + sx, v, sx, sz), (u, v + sz, sz, sy), (u + sz, v + sz, sx, sy),
                                   (u + sz + sx, v + sz, sz, sy), (u + 2 * sz + sx, v + sz, sx, sy)):
                for y in range(int(y0), int(y0 + h)):
                    for x in range(int(x0), int(x0 + w)):
                        if 0 <= x < tw and 0 <= y < th:
                            cells.add((x, y))
    return cells


def behavior(key, m, cfg):
    """행동 팩 엔티티 (애드온 build.py monster_behavior 와 같은 모양 — 맞기 전에는 돌아다니기만, 피해는 Monsters 가 넣음)"""
    leash = cfg["leash"]
    comps = {
        "minecraft:type_family": {"family": ["rpg_monster", key, "mob"] + (["undead"] if m.get("undead") else []) + (["void"] if m.get("void") else [])},
        "minecraft:health": {"value": m["hp"], "max": m["hp"]},
        "minecraft:attack": {"damage": 1},
        "minecraft:movement": {"value": m["speed"]},
        "minecraft:jump.static": {},
        "minecraft:collision_box": {"width": m["box"][0], "height": m["box"][1]},
        "minecraft:scale": {"value": m["scale"]},
        "minecraft:physics": {},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:knockback_resistance": {"value": m["knockResist"]},
        "minecraft:follow_range": {"value": leash, "max": leash},
        "minecraft:nameable": {"always_show": True, "allow_name_tag_renaming": False},
        "minecraft:persistent": {},
        "minecraft:loot": {"table": f"loot_tables/entities/rpg_{key}.json"},
        "minecraft:experience_reward": {"on_death": "0"},
        "minecraft:damage_sensor": {"triggers": [{"cause": "fall", "deals_damage": "no"}] + (
            # 0.32.0 공허 몬스터: 큰 몸이 사냥터 벽 · 나무에 걸려도 숨이 막히지 않게 (굴착자는 땅속도 지나감)
            [{"cause": "suffocation", "deals_damage": "no"}] if m.get("void") else [])},
        "minecraft:home": {"restriction_radius": cfg["homeRadius"], "restriction_type": "random_movement"},
        "minecraft:behavior.hurt_by_target": {"priority": 1, "entity_types": [{"filters": {"test": "is_family", "subject": "other", "value": "player"}, "max_dist": leash}]},
        "minecraft:behavior.melee_attack": {"priority": 2, "speed_multiplier": m["chase"], "track_target": True, "reach_multiplier": 1.2, "cooldown_time": 1},
        "minecraft:behavior.move_towards_home_restriction": {"priority": 5, "speed_multiplier": 1.0},
        "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.7, "interval": 120},
        "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 8},
        "minecraft:behavior.random_look_around": {"priority": 8},
        "minecraft:movement.basic": {},
        "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True, "avoid_damage_blocks": True},
        "minecraft:behavior.float": {"priority": 0},
    }
    props = {}
    if m.get("burrow") or m.get("tunnel"):
        # 웜 유충 매복 · 0.32.0 공허 굴착자 땅굴: 숨었는지 (클라이언트도 앎 — 충돌 상자 · 그리기)
        props["rpg:buried"] = {"type": "bool", "client_sync": True, "default": False}
    return {"group": "monsters", "properties": props, "components": comps, "component_groups": {}, "events": {}}


def main():
    more = load(os.path.join(CORE, "rules_more.json"))
    rules = load(os.path.join(CORE, "rules.json"))
    cfg = rules["MONSTER"]
    mons = {k: v for k, v in more["MONSTERS"].items() if not k.startswith("_") and v.get("region")}
    ent_path = os.path.join(RES, "mmorpg", "entities.json")
    rend_path = os.path.join(MM, "bedrock", "entities.json")
    ents = load(ent_path)
    rend = load(rend_path)
    lang = {}
    for key, m in mons.items():
        eid = f"rpg:{key}"
        d = os.path.join(ART, key)
        geo_file = load(os.path.join(d, f"{key}.geo.json"))
        geo = geo_file["minecraft:geometry"][0]
        gid = f"geometry.rpg.monster.{key}"
        geo["description"]["identifier"] = gid
        anims = load(os.path.join(d, f"{key}.animation.json"))["animations"]
        prefix = f"animation.rpg.monster.{key}."
        amap = {}
        for full in anims:
            if not full.startswith(prefix):
                raise SystemExit(f"{key}: 동작 이름이 {prefix}… 가 아님: {full}")
            amap[full[len(prefix):]] = full
        for need in ("idle", "walk", "attack"):
            if need not in amap:
                raise SystemExit(f"{key}: {need} 동작이 없음")
        # 행동
        ents[eid] = behavior(key, m, cfg)
        # 그리기
        tex_rel = f"textures/entity/rpg/monsters/{key}"
        base_c, over_c = EGGS[key]
        rend["entities"][eid] = {
            "identifier": eid,
            "materials": {"default": "entity_emissive"},
            "textures": {"default": tex_rel},
            "geometry": {"default": gid},
            "animations": amap,
            "scripts": {"animate": [a for a in ANIMATE if a in amap]},
            "render_controllers": ["controller.render.default"],
            "spawn_egg": {"base_color": base_c, "overlay_color": over_c},
        }
        rend["models"][gid] = geo
        for full, a in anims.items():
            rend["animations"][full] = a
        # 텍스처 (+ 빛나는 칸)
        img = Image.open(os.path.join(d, f"{key}.png")).convert("RGBA")
        tw, th = img.size
        gw = geo["description"].get("texture_width", tw)
        gh = geo["description"].get("texture_height", th)
        if (gw, gh) != (tw, th):
            raise SystemExit(f"{key}: 텍스처 {tw}×{th} ≠ 모델 {gw}×{gh}")
        cells = covered(geo, tw, th)
        px = img.load()
        base = img.copy()
        bp = base.load()
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        gp = glow.load()
        n = 0
        for y in range(th):
            for x in range(tw):
                r, g, b, a = px[x, y]
                bp[x, y] = (r, g, b, 255)
                if a < 255 and (x, y) in cells:
                    gp[x, y] = (r, g, b, 255 - a)
                    n += 1
        out = os.path.join(MM, "textures", "entity", "rpg", "monsters", f"{key}.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        base.save(out)
        emissive = rend.setdefault("emissive", [])
        glow_path = os.path.join(MM, "textures", "entity", "rpg", "monsters", f"{key}_emissive.png")
        if n:
            glow.save(glow_path)
            if tex_rel not in emissive:
                emissive.append(tex_rel)
        else:
            if os.path.exists(glow_path):
                os.remove(glow_path)
            if tex_rel in emissive:
                emissive.remove(tex_rel)
        # 소환 알
        over = rgb(over_c)
        spots = {}
        for (x, y) in [(5, 5), (9, 4), (11, 7), (4, 9), (8, 9), (10, 12), (6, 12), (7, 6)]:
            spots[(x, y)] = over
        for (x, y) in [(6, 5), (10, 4), (5, 9), (9, 9), (11, 12)]:
            spots[(x, y)] = tuple(int(c * 0.8) for c in over)
        egg_icon(os.path.join(RPG, "textures", "item", f"{key}_spawn_egg.png"), rgb(base_c), spots)
        write_json(os.path.join(RPG, "items", f"{key}_spawn_egg.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{key}_spawn_egg"}}, 2)
        write_json(os.path.join(RPG, "models", "item", f"{key}_spawn_egg.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/{key}_spawn_egg"}}, 2)
        lang["entity.rpg." + key] = m["name"]
        print(f"{eid}: bones {len(geo['bones'])}, cubes {sum(len(b.get('cubes', [])) for b in geo['bones'])}, texture {tw}×{th}, "
              f"emissive {n}, animations {sorted(amap)}")
    write_json(ent_path, ents)
    write_json(rend_path, rend)
    for code in ("ko_kr", "en_us"):
        p = os.path.join(RPG, "lang", code + ".json")
        d = load(p)
        d.update(lang)
        write_json(p, d, 2)
    print(f"more monsters: {len(mons)}")
    # 0.33.0 보스 공허 기사 (자바 판에만 — 사용자 모델 · 동작 · 텍스처 + 제단 · 재료 그림)
    import void_knight  # noqa: E402

    void_knight.main()


if __name__ == "__main__":
    main()
