#!/usr/bin/env python3
"""0.33.0 보스 공허 기사 (Lv.55 — 균열을 걷는 자) 자원 만들기 (tools/more_monsters.py 가 끝에 부름 — 애드온 엔티티를 다시 옮겨도 남게).

tools/void_knight.py
- 모델 · 동작 · 텍스처: tools/boss_art/void_knight/ (사용자 디자인 — void_knight.geo.json · void_knight.animation.json (idle · walk · attack) · void_knight.png)
  ID 만 rpg 로 바꿈 (geometry.rpg.boss.void_knight · animation.rpg.boss.void_knight.<이름>), 새 동작은 tools/void_knight_anims.py (같은 뼈에 더함),
  stride = 사용자 walk 를 걸은 거리 대신 시간으로 (서버가 옮기는 보스 — 무게 = 속성 rpg:gait)
- 쓰는 곳 (있으면 바꿈 · 없으면 더함):
  · src/main/resources/mmorpg/entities.json — 행동 (rpg:boss_void_knight: 보스 막대 이름 · 체력 · 크기 · 중력 · 충돌 없음 · 속성 rpg:flash · rage · gait · strafe · phase)
  · assets/mmorpg/bedrock/entities.json — 그리기 (엔티티 · 모델 · 동작 · 렌더 컨트롤러 controller.render.rpg_boss_void_knight · 빛나는 텍스처)
  · assets/mmorpg/textures/entity/rpg/bosses/void_knight.png (+ _emissive — 알파 < 255 인 칸이 빛남) · void_knight_rage.png (2페이즈: 빛을 더 세게)
  · 제단 블록 rpg:altar_void_knight (blockstates · models · items · textures/block/altar/void_knight_*)
  · 보스 재료 2종 (rules_more BOSS_MATERIALS void_heart · rift_shard): 아이콘 32×32 · items · models · assets/mmorpg/materials.json 이름
  · assets/rpg/lang (엔티티 · 제단 이름)
수치는 core/src/main/resources/mmorpg/rules_more.json BOSSES.void_knight.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import void_knight_anims  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
RPG = os.path.join(RES, "assets", "rpg")
MM = os.path.join(RES, "assets", "mmorpg")
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
ART = os.path.join(ROOT, "tools", "boss_art", "void_knight")
KEY = "void_knight"
EID = "rpg:boss_void_knight"
GEO_ID = "geometry.rpg.boss.void_knight"
ANIM = "animation.rpg.boss.void_knight."
TEX = "textures/entity/rpg/bosses/void_knight"
TEX_RAGE = "textures/entity/rpg/bosses/void_knight_rage"
CONTROLLER = "controller.render.rpg_boss_void_knight"
# 늘 트는 동작 (나머지는 서버 BossVoidKnight 가 틀 rpg_move · rpg_atk · die 로)
ANIMATE = ["idle", {"stride": "q.property('rpg:gait')"}, {"strafe": "math.abs(q.property('rpg:strafe'))"}]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data, indent=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent, separators=None if indent else (",", ":"))
        f.write("\n")


def covered(geo, tw, th):
    """모델이 쓰는 텍스처 칸 (상자 UV 의 펼친 여섯 면 — 소수 크기는 걸친 칸 모두)"""
    cells = set()

    def rect(u, v, w, h):
        for y in range(int(math.floor(v)), int(math.ceil(v + h - 1e-6))):
            for x in range(int(math.floor(u)), int(math.ceil(u + w - 1e-6))):
                if 0 <= x < tw and 0 <= y < th:
                    cells.add((x, y))

    for b in geo.get("bones", []):
        for c in b.get("cubes", []):
            u, v = c.get("uv", [0, 0])
            sx, sy, sz = c.get("size", [0, 0, 0])
            for (x0, y0, w, h) in ((u + sz, v, sx, sz), (u + sz + sx, v, sx, sz), (u, v + sz, sz, sy), (u + sz, v + sz, sx, sy),
                                   (u + sz + sx, v + sz, sz, sy), (u + 2 * sz + sx, v + sz, sx, sy)):
                rect(x0, y0, w, h)
    return cells


# ---------------------------------------------------------------- 수치 (행동)

def behavior(b):
    """행동 팩 엔티티 (스콜과 하티 · 수르트와 같은 모양: 중력 · 충돌 없음, 이동 0 — 모듈이 매 틱 옮김)"""
    hp = b["hp"]
    props = {
        "rpg:flash": {"type": "bool", "client_sync": True, "default": False},
        "rpg:rage": {"type": "bool", "client_sync": True, "default": False},
        # 걷는 빠르기 (stride 무게) · 옆걸음 (-1 왼쪽 · +1 오른쪽 — strafe) · 2페이즈 (텍스처: 빛이 세짐)
        "rpg:gait": {"type": "float", "range": [0, 1], "default": 0, "client_sync": True},
        "rpg:strafe": {"type": "float", "range": [-1, 1], "default": 0, "client_sync": True},
        "rpg:phase": {"type": "int", "range": [0, 1], "default": 0, "client_sync": True},
    }
    comps = {
        "minecraft:type_family": {"family": ["rpg_boss", KEY, "mob", "void"]},
        "minecraft:health": {"value": hp, "max": hp},
        "minecraft:attack": {"damage": 1},
        "minecraft:collision_box": {"width": b["box"][0], "height": b["box"][1]},
        "minecraft:scale": {"value": b["scale"]},
        "minecraft:physics": {"has_gravity": False, "has_collision": False},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:knockback_resistance": {"value": 1.0},
        "minecraft:fire_immune": {},
        "minecraft:breathable": {"breathes_air": True, "breathes_water": True, "suffocate_time": 0, "total_supply": 15},
        "minecraft:follow_range": {"value": 30, "max": 30},
        "minecraft:nameable": {"always_show": True, "allow_name_tag_renaming": False},
        "minecraft:persistent": {},
        "minecraft:boss": {"hud_range": 48, "should_darken_sky": False, "name": f"§e[Lv.{b['level']}] §6{b['title']} §c{b['name']}"},
        "minecraft:mob_effect_immunity": {"mob_effects": ["slowness", "weakness", "poison", "fatal_poison", "wither", "levitation", "blindness", "nausea", "darkness",
                                                         "mining_fatigue", "hunger", "infested", "oozing", "weaving", "wind_charged"]},
        "minecraft:experience_reward": {"on_death": "0"},
        "minecraft:damage_sensor": {"triggers": [{"cause": "fall", "deals_damage": "no"}, {"cause": "suffocation", "deals_damage": "no"},
                                                 {"cause": "drowning", "deals_damage": "no"}]},
        "minecraft:movement": {"value": 0},
    }
    groups = {"rpg:free": {"minecraft:movement": {"value": 0}}, "rpg:held": {"minecraft:movement": {"value": 0}},
              "rpg:free_fast": {"minecraft:movement": {"value": 0.0}}}
    events = {
        "minecraft:entity_spawned": {"add": {"component_groups": ["rpg:free"]}},
        "rpg:hold": {"remove": {"component_groups": ["rpg:free", "rpg:free_fast"]}, "add": {"component_groups": ["rpg:held"]}},
        "rpg:release": {"remove": {"component_groups": ["rpg:held", "rpg:free_fast"]}, "add": {"component_groups": ["rpg:free"]}},
        "rpg:release_fast": {"remove": {"component_groups": ["rpg:held", "rpg:free"]}, "add": {"component_groups": ["rpg:free_fast"]}},
    }
    return {"group": "bosses", "properties": props, "components": comps, "component_groups": groups, "events": events}


# ---------------------------------------------------------------- 그리기

def stride_of(walk):
    """사용자 walk (걸은 거리 q.modified_distance_moved · 빠르기 q.modified_move_speed) → stride (시간 — 무게는 rpg:gait)"""
    s = json.dumps(walk)
    s = s.replace("q.modified_distance_moved * 22", "q.life_time * 520").replace("q.modified_move_speed", "1")
    if "modified_" in s:
        raise SystemExit("walk 의 식을 stride 로 바꾸지 못함: " + s)
    return json.loads(s)


def textures(geo):
    """사용자 텍스처 → 바탕 (불투명) + 빛나는 칸 (알파 < 255) · 2페이즈 (빛을 더 세고 밝게)"""
    img = Image.open(os.path.join(ART, "void_knight.png")).convert("RGBA")
    tw, th = img.size
    gw = geo["description"].get("texture_width", tw)
    gh = geo["description"].get("texture_height", th)
    if (gw, gh) != (tw, th):
        raise SystemExit(f"텍스처 {tw}×{th} ≠ 모델 {gw}×{gh}")
    cells = covered(geo, tw, th)
    px = img.load()
    holes = sum(1 for (x, y) in cells if px[x, y][3] == 0)
    # 모델이 쓰는데 비어 있는 칸 (상자 UV 의 소수 크기 면이 걸친 투명 칸 — 그대로 두면 검은 점): 둘레의 칠한 칸 색으로 메움 (빛나지 않음)
    fill = {}
    todo = {(x, y) for (x, y) in cells if px[x, y][3] == 0}
    for _ in range(8):
        if not todo:
            break
        got = {}
        for (x, y) in todo:
            ns = []
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if not (0 <= q[0] < tw and 0 <= q[1] < th):
                    continue
                c = fill.get(q) or (px[q][:3] if px[q][3] > 0 else None)
                if c:
                    ns.append(c)
            if ns:
                got[(x, y)] = tuple(sum(c[i] for c in ns) // len(ns) for i in range(3))
        fill.update(got)
        todo -= set(got)
    out = {}
    for name, boost in ((TEX, 0.0), (TEX_RAGE, 1.0)):
        base = img.copy()
        bp = base.load()
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        gp = glow.load()
        n = 0
        for y in range(th):
            for x in range(tw):
                r, g, b, a = px[x, y]
                if a == 0:
                    bp[x, y] = (*fill.get((x, y), (r, g, b)), 255)
                    continue
                if boost and a < 255 and (x, y) in cells:
                    # 2페이즈: 빛나는 칸을 흰빛 쪽으로 · 거의 다 빛나게 (알파 102 → 26)
                    r, g, b = (int(c + (255 - c) * 0.3) for c in (r, g, b))
                    a = max(0, int(a * 0.25))
                bp[x, y] = (r, g, b, 255)
                if a < 255 and (x, y) in cells:
                    gp[x, y] = (r, g, b, 255 - a)
                    n += 1
        dst = os.path.join(MM, name + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        base.save(dst)
        glow.save(os.path.join(MM, name + "_emissive.png"))
        out[name] = n
    return out, holes, len(cells)


# ---------------------------------------------------------------- 제단 블록 (16×16 — 다른 제단과 같은 틀: 돌 테두리 · 금 고리 · 보스 무늬)

STONE = (0x56, 0x50, 0x5c)
DARK = (0x2e, 0x28, 0x34)
GOLD = (0xe8, 0xbe, 0x46)
VIOLET = (0x8e, 0x4c, 0xff)
PINK = (0xff, 0x5c, 0xde)
LIGHT = (0xf6, 0xd4, 0xff)
DEEP = (0x1a, 0x10, 0x2c)
STEEL = (0xa8, 0xb0, 0xc8)


def altar_textures():
    d = os.path.join(RPG, "textures", "block", "altar")
    os.makedirs(d, exist_ok=True)
    # 위: 테두리 · 금 고리 (다른 제단과 같은 자리) · 가운데 세로 균열 (분홍 → 흰 심지) 을 가르는 칼 (은빛)
    top = Image.new("RGBA", (16, 16), DARK + (255,))
    p = top.load()
    for i in range(16):
        for j in (0, 15):
            p[i, j] = STONE + (255,)
            p[j, i] = STONE + (255,)
    ring = [(6, 2), (7, 2), (8, 2), (9, 2), (4, 3), (5, 3), (10, 3), (11, 3), (3, 4), (12, 4), (3, 5), (12, 5), (2, 6), (13, 6), (2, 7), (13, 7), (2, 8), (13, 8),
            (2, 9), (13, 9), (3, 10), (12, 10), (3, 11), (12, 11), (4, 12), (5, 12), (10, 12), (11, 12), (6, 13), (7, 13), (8, 13), (9, 13)]
    for (x, y) in ring:
        p[x, y] = GOLD + (255,)
    for y in range(4, 12):
        for x in range(4, 12):
            if p[x, y][:3] == DARK:
                p[x, y] = DEEP + (255,)
    rift = {(7, 4): VIOLET, (8, 5): PINK, (7, 6): PINK, (8, 7): LIGHT, (7, 8): LIGHT, (8, 9): PINK, (7, 10): VIOLET, (8, 11): VIOLET,
            (8, 4): DEEP, (7, 5): VIOLET, (8, 6): VIOLET, (7, 7): PINK, (8, 8): PINK, (7, 9): VIOLET}
    for (x, y), c in rift.items():
        p[x, y] = c + (255,)
    for (x, y) in ((5, 10), (6, 9), (9, 6), (10, 5)):
        p[x, y] = STEEL + (255,)
    p[4, 11] = GOLD + (255,)
    p[11, 4] = LIGHT + (255,)
    top.save(os.path.join(d, f"{KEY}_top.png"))
    # 옆: 돌 벽돌 + 보라 띠 + 금 고리 (다른 제단과 같은 틀)
    side = Image.new("RGBA", (16, 16), DARK + (255,))
    s = side.load()
    for y in range(16):
        for x in range(16):
            if y in (0, 5, 10, 15) or x == 0 or x == 8 and (y < 5 or y > 10):
                c = DARK
            else:
                c = STONE
            if 6 <= y <= 9:
                c = VIOLET if (x + y) % 5 else PINK
            s[x, y] = c + (255,)
    for y in range(5, 11):
        s[6, y] = GOLD + (255,)
        s[9, y] = GOLD + (255,)
    for x in range(6, 10):
        s[x, 5] = GOLD + (255,)
        s[x, 10] = GOLD + (255,)
    side.save(os.path.join(d, f"{KEY}_side.png"))
    Image.new("RGBA", (16, 16), DARK + (255,)).save(os.path.join(d, f"{KEY}_bottom.png"))
    path = f"altar_{KEY}"
    write_json(os.path.join(RPG, "blockstates", path + ".json"), {"variants": {"": {"model": f"rpg:block/{path}"}}}, 2)
    write_json(os.path.join(RPG, "models", "block", path + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"rpg:block/altar/{KEY}_top", "bottom": f"rpg:block/altar/{KEY}_bottom", "side": f"rpg:block/altar/{KEY}_side"}}, 2)
    write_json(os.path.join(RPG, "items", path + ".json"), {"model": {"type": "minecraft:model", "model": f"rpg:block/{path}"}}, 2)


# ---------------------------------------------------------------- 보스 재료 아이콘 (32×32 — tools/loot_icons.py 의 틀)

def material_icons(mats):
    import loot_icons as li
    out = {}

    def void_heart(ic):
        # 공허의 심장: 검보라 갑옷 조각에 싸인 분홍 심장 (가운데 흰 빛) · 금 테 · 아래로 새는 균열 빛
        heart = ic.mask(lambda d: d.polygon([(16, 28), (5, 17), (4, 11), (7, 6), (12, 5), (16, 9), (20, 5), (25, 6), (28, 11), (27, 17)], fill=255))
        ic.fill(heart, (122, 40, 196), light=0.35, dark=0.35, noise=5, seed=200)
        inner = ic.mask(lambda d: d.polygon([(16, 24), (9, 16), (9, 12), (12, 9), (16, 13), (20, 9), (23, 12), (23, 16)], fill=255))
        ic.fill(inner, (240, 92, 222), light=0.3, dark=0.2, noise=4, seed=201)
        core = ic.mask(lambda d: d.ellipse((13, 12, 19, 19), fill=255))
        ic.fill(core, (255, 226, 252), light=0.1, dark=0.05, noise=2, seed=202)
        ic.line([(6, 12), (9, 8), (13, 7)], (232, 190, 70), heart)
        ic.line([(26, 12), (23, 8), (19, 7)], (232, 190, 70), heart)
        ic.line([(16, 26), (16, 29), (15, 31)], (200, 120, 255))
        return [(4, 4, (255, 214, 250)), (28, 24, (230, 190, 255)), (27, 4, (255, 236, 252))]

    def rift_shard(ic):
        # 균열 대검의 조각: 비스듬히 부러진 검푸른 칼날 (가운데 분홍 균열 빛) · 금 코등이 조각
        blade = ic.mask(lambda d: d.polygon([(6, 27), (9, 29), (24, 12), (27, 4), (22, 6), (20, 9), (17, 10), (16, 13)], fill=255))
        ic.fill(blade, (82, 90, 124), light=0.4, dark=0.3, noise=5, seed=210)
        ic.line([(8, 27), (13, 21), (18, 15), (23, 9), (26, 5)], (255, 92, 222), blade)
        ic.line([(9, 26), (14, 20)], (255, 210, 248), blade)
        guard = ic.mask(lambda d: d.polygon([(3, 23), (7, 21), (12, 26), (9, 29), (5, 28)], fill=255))
        ic.fill(guard, (232, 190, 70), light=0.35, dark=0.3, noise=4, seed=211)
        ic.put(6, 25, (122, 40, 196))
        for (x, y) in ((17, 10), (16, 12), (20, 9)):
            ic.put(x, y, (40, 30, 66))
        return [(27, 12, (255, 214, 250)), (5, 6, (230, 190, 255)), (24, 26, (255, 180, 245))]

    paint = {"void_heart": void_heart, "rift_shard": rift_shard}
    for k, painter in paint.items():
        m = mats[k]
        glow = (190, 110, 255)
        ic = li.Icon(glow)
        sparkles = painter(ic)
        name = m["item"].split(":")[1]
        ic.render(sparkles).save(os.path.join(RPG, "textures", "item", "material", f"{name}.png"))
        write_json(os.path.join(RPG, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}}, 2)
        write_json(os.path.join(RPG, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/material/{name}"}}, 2)
        out[m["item"]] = {"name": f"{m['color']}{m['name']}§r§7 (보스 재료)", "glint": True, "stack": 64}
    return out


# ---------------------------------------------------------------- 실행

def main():
    more = load(os.path.join(CORE, "rules_more.json"))
    b = more["BOSSES"][KEY]
    ent_path = os.path.join(RES, "mmorpg", "entities.json")
    rend_path = os.path.join(MM, "bedrock", "entities.json")
    ents = load(ent_path)
    rend = load(rend_path)
    # 모델 (사용자 디자인 — ID 만 바꿈)
    geo_file = load(os.path.join(ART, "void_knight.geo.json"))
    geo = geo_file["minecraft:geometry"][0]
    geo["description"]["identifier"] = GEO_ID
    # 동작: 사용자 것 (idle · walk · attack) + stride (walk 를 시간으로) + 새 동작
    user = load(os.path.join(ART, "void_knight.animation.json"))["animations"]
    anims = {}
    for full, a in user.items():
        anims[full.split(".")[-1]] = a
    for need in ("idle", "walk", "attack"):
        if need not in anims:
            raise SystemExit(f"사용자 동작 {need} 이 없음")
    anims["stride"] = stride_of(anims["walk"])
    for n, a in void_knight_anims.anims().items():
        if n in anims:
            raise SystemExit(f"동작 이름이 겹침: {n}")
        anims[n] = a
    bones = {bb["name"] for bb in geo["bones"]}
    for n, a in anims.items():
        for bn in a.get("bones", {}):
            if bn not in bones:
                raise SystemExit(f"{n}: 모델에 없는 뼈 {bn}")
    amap = {n: ANIM + n for n in anims}
    # 행동 · 그리기
    ents[EID] = behavior(b)
    rend["entities"][EID] = {
        "identifier": EID,
        "materials": {"default": "entity_emissive"},
        "textures": {"default": TEX, "rage": TEX_RAGE},
        "geometry": {"default": GEO_ID},
        "animations": amap,
        "scripts": {"animate": ANIMATE},
        "render_controllers": [CONTROLLER],
    }
    rend["models"][GEO_ID] = geo
    for old in [k for k in rend["animations"] if k.startswith(ANIM)]:
        del rend["animations"][old]
    for n, a in anims.items():
        rend["animations"][ANIM + n] = a
    # 렌더 컨트롤러: 2페이즈 (rpg:phase 1) 면 빛이 센 텍스처, 맞으면 번쩍임 (다른 보스와 같은 붉은빛)
    rend["render_controllers"][CONTROLLER] = {
        "arrays": {"textures": {"Array.skin": ["Texture.default", "Texture.rage"]}},
        "geometry": "Geometry.default",
        "materials": [{"*": "Material.default"}],
        "textures": ["Array.skin[q.property('rpg:phase')]"],
        "overlay_color": {"r": 1.0, "g": 0.22, "b": 0.16, "a": "q.property('rpg:flash') ? 0.45 : (q.property('rpg:rage') ? 0.22 : 0.0)"},
    }
    glow, holes, n_cells = textures(geo)
    emissive = rend.setdefault("emissive", [])
    for t in (TEX, TEX_RAGE):
        if t not in emissive:
            emissive.append(t)
    write_json(ent_path, ents)
    write_json(rend_path, rend)
    # 제단 · 재료 · 이름
    altar_textures()
    mats_path = os.path.join(MM, "materials.json")
    mats = load(mats_path)
    mats.update(material_icons(more["BOSS_MATERIALS"]))
    write_json(mats_path, mats, 2)
    for code in ("ko_kr", "en_us"):
        p = os.path.join(RPG, "lang", code + ".json")
        d = load(p)
        d["entity.rpg.boss_" + KEY] = b["name"]
        d["block.rpg.altar_" + KEY] = b["name"] + " 제단"
        write_json(p, d, 2)
    print(f"{EID}: bones {len(geo['bones'])}, cubes {sum(len(bb.get('cubes', [])) for bb in geo['bones'])}, texture cells {n_cells} (alpha 0 {holes}), "
          f"emissive {glow}, animations {len(anims)}")


if __name__ == "__main__":
    main()
