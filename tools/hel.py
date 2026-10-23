#!/usr/bin/env python3
"""0.35.0 보스 헬 (Lv.58 — 헬헤임의 주인) 자원 만들기 (tools/more_monsters.py 가 끝에 부름 — 애드온 엔티티를 다시 옮겨도 남게).

tools/hel.py
- 모델 · 동작 · 텍스처: tools/boss_art/hel/ (사용자 디자인 — hel.geo.json · hel.animation.json (idle · walk · attack) · hel.png 128×128)
  ID 만 rpg 로 바꿈 (geometry.rpg.boss.hel · animation.rpg.boss.hel.<이름>), 새 동작은 tools/hel_anims.py (같은 뼈에 더함),
  stride = 사용자 walk 를 걸은 거리 대신 시간으로 (서버가 옮기는 보스 — 무게 = 속성 rpg:gait), hover = 늘 떠 있음
- 쓰는 곳 (있으면 바꿈 · 없으면 더함):
  · src/main/resources/mmorpg/entities.json — 행동 (rpg:boss_hel: 보스 막대 이름 · 체력 · 크기 · 중력 · 충돌 없음 · 속성 rpg:flash · rage · gait · phase)
  · assets/mmorpg/bedrock/entities.json — 그리기 (엔티티 · 모델 · 동작 · 렌더 컨트롤러 controller.render.rpg_boss_hel · 빛나는 텍스처)
  · assets/mmorpg/textures/entity/rpg/bosses/hel.png (+ _emissive — 알파 < 255 인 칸 (눈 · 안개) 이 빛남) · hel_rage.png (각성 「망자의 여왕」:
    창백한 쪽에 검은 핏줄이 번지고 빛이 셈)
  · 제단 블록 rpg:altar_hel (blockstates · models · items · textures/block/altar/hel_*)
  · 보스 재료 2종 (rules_more BOSS_MATERIALS hel_claw · dead_mist): 아이콘 32×32 · items · models · assets/mmorpg/materials.json 이름
  · assets/rpg/lang (엔티티 · 제단 이름)
수치는 core/src/main/resources/mmorpg/rules_more.json BOSSES.hel.
"""
import json
import math
import os
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hel_anims  # noqa: E402
import void_knight as vk  # noqa: E402  (covered · write_json · load 같은 도우미)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
RPG = os.path.join(RES, "assets", "rpg")
MM = os.path.join(RES, "assets", "mmorpg")
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
ART = os.path.join(ROOT, "tools", "boss_art", "hel")
KEY = "hel"
EID = "rpg:boss_hel"
GEO_ID = "geometry.rpg.boss.hel"
ANIM = "animation.rpg.boss.hel."
TEX = "textures/entity/rpg/bosses/hel"
TEX_RAGE = "textures/entity/rpg/bosses/hel_rage"
CONTROLLER = "controller.render.rpg_boss_hel"
# 늘 트는 동작 (나머지는 서버 BossHel 이 틀 rpg_atk · rpg_far · die 로)
ANIMATE = ["idle", "hover", {"stride": "q.property('rpg:gait')"}]


# ---------------------------------------------------------------- 수치 (행동)

def behavior(b):
    """행동 팩 엔티티 (공허 기사와 같은 모양: 중력 · 충돌 없음, 이동 0 — 모듈이 매 틱 옮김)"""
    hp = b["hp"]
    props = {
        "rpg:flash": {"type": "bool", "client_sync": True, "default": False},
        "rpg:rage": {"type": "bool", "client_sync": True, "default": False},
        # 떠서 나아가는 빠르기 (stride 무게) · 각성 (텍스처: 검은 핏줄 · 빛이 셈)
        "rpg:gait": {"type": "float", "range": [0, 1], "default": 0, "client_sync": True},
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
        "minecraft:follow_range": {"value": 32, "max": 32},
        "minecraft:nameable": {"always_show": True, "allow_name_tag_renaming": False},
        "minecraft:persistent": {},
        "minecraft:boss": {"hud_range": 52, "should_darken_sky": False, "name": f"§e[Lv.{b['level']}] §6{b['title']} §c{b['name']}"},
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
    """사용자 walk (걸은 거리 q.modified_distance_moved × k · 빠르기 q.modified_move_speed) → stride (시간 × 12k — 무게는 rpg:gait)"""
    s = json.dumps(walk)
    s = re.sub(r"q\.modified_distance_moved \* ([0-9.]+)", lambda m: f"q.life_time * {float(m.group(1)) * 12:g}", s)
    s = s.replace("q.modified_move_speed", "1")
    if "modified_" in s:
        raise SystemExit("walk 의 식을 stride 로 바꾸지 못함: " + s)
    return json.loads(s)


def hash01(x, y, k=0):
    h = (x * 374761393 + y * 668265263 + k * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def textures(geo):
    """사용자 텍스처 → 바탕 (불투명) + 빛나는 칸 (알파 < 255 — 눈 · 안개) · 각성 (창백한 칸에 검은 핏줄이 번짐 · 빛을 더 세고 밝게)"""
    img = Image.open(os.path.join(ART, "hel.png")).convert("RGBA")
    tw, th = img.size
    gw = geo["description"].get("texture_width", tw)
    gh = geo["description"].get("texture_height", th)
    if (gw, gh) != (tw, th):
        raise SystemExit(f"텍스처 {tw}×{th} ≠ 모델 {gw}×{gh}")
    cells = vk.covered(geo, tw, th)
    px = img.load()
    holes = sum(1 for (x, y) in cells if px[x, y][3] == 0)
    # 모델이 쓰는데 비어 있는 칸: 둘레의 칠한 칸 색으로 메움 (빛나지 않음 — 공허 기사와 같음)
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
    veins = 0
    for name, rage in ((TEX, False), (TEX_RAGE, True)):
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
                lit = a < 255 and (x, y) in cells
                if rage:
                    if lit:
                        # 빛나는 칸 (눈 · 안개): 흰빛 쪽으로 · 거의 다 빛나게 (알파 102 → 26)
                        r, g, b = (int(c + (255 - c) * 0.3) for c in (r, g, b))
                        a = max(0, int(a * 0.25))
                    elif (r + g + b) / 3 > 150 and (x, y) in cells:
                        # 창백한 칸: 검은 반쪽이 번짐 — 잿빛으로 가라앉고, 이어지는 검은 금 (부드러운 물결 두 개가 겹치는 곳) 이 퍼짐
                        v = math.sin(x * 0.42 + y * 0.18 + hash01(x // 8, y // 8, 7) * 2.2) + math.sin(y * 0.37 - x * 0.12 + hash01(x // 8, y // 8, 9) * 2.2)
                        if abs(v) < 0.16:
                            r, g, b = 30, 28, 38
                            veins += 1
                        elif abs(v) < 0.32:
                            r, g, b = int(r * 0.42), int(g * 0.42), int(b * 0.48)
                            veins += 1
                        else:
                            r, g, b = int(r * 0.74), int(g * 0.76), int(min(255, b * 0.84 + 12))
                bp[x, y] = (r, g, b, 255)
                if lit:
                    gp[x, y] = (r, g, b, 255 - a)
                    n += 1
        dst = os.path.join(MM, name + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        base.save(dst)
        glow.save(os.path.join(MM, name + "_emissive.png"))
        out[name] = n
    return out, holes, len(cells), veins


# ---------------------------------------------------------------- 제단 블록 (16×16 — 다른 제단과 같은 틀: 돌 테두리 · 고리 · 보스 무늬)

STONE = (0x4a, 0x4e, 0x58)
DARK = (0x1e, 0x20, 0x28)
SOUL = (0x5e, 0xe6, 0xff)
PALE = (0xec, 0xf0, 0xf6)
BONE = (0xd8, 0xd2, 0xbc)
INK = (0x0c, 0x0b, 0x10)
GOLD = (0xe8, 0xbe, 0x46)


def altar_textures():
    d = os.path.join(RPG, "textures", "block", "altar")
    os.makedirs(d, exist_ok=True)
    # 위: 테두리 · 고리 (다른 제단과 같은 자리 — 영혼 빛) · 가운데 반쪽 얼굴 (왼쪽 검게 · 오른쪽 창백하게) · 눈 둘 (금 · 영혼 빛)
    top = Image.new("RGBA", (16, 16), DARK + (255,))
    p = top.load()
    for i in range(16):
        for j in (0, 15):
            p[i, j] = STONE + (255,)
            p[j, i] = STONE + (255,)
    ring = [(6, 2), (7, 2), (8, 2), (9, 2), (4, 3), (5, 3), (10, 3), (11, 3), (3, 4), (12, 4), (3, 5), (12, 5), (2, 6), (13, 6), (2, 7), (13, 7), (2, 8), (13, 8),
            (2, 9), (13, 9), (3, 10), (12, 10), (3, 11), (12, 11), (4, 12), (5, 12), (10, 12), (11, 12), (6, 13), (7, 13), (8, 13), (9, 13)]
    for (x, y) in ring:
        p[x, y] = SOUL + (255,)
    for y in range(4, 12):
        for x in range(4, 12):
            if p[x, y][:3] == DARK:
                p[x, y] = (INK if x < 8 else PALE) + (255,)
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        p[x, y] = DARK + (255,)
    p[6, 7] = GOLD + (255,)
    p[9, 7] = SOUL + (255,)
    for x in range(5, 11):
        p[x, 10] = (BONE if x >= 8 else STONE) + (255,)
    top.save(os.path.join(d, f"{KEY}_top.png"))
    # 옆: 어두운 돌 벽돌 + 뼈 띠 + 영혼 빛 고리
    side = Image.new("RGBA", (16, 16), DARK + (255,))
    s = side.load()
    for y in range(16):
        for x in range(16):
            if y in (0, 5, 10, 15) or x == 0 or x == 8 and (y < 5 or y > 10):
                c = DARK
            else:
                c = STONE
            if 6 <= y <= 9:
                c = BONE if (x + y) % 4 else PALE
            s[x, y] = c + (255,)
    for y in range(5, 11):
        s[6, y] = SOUL + (255,)
        s[9, y] = SOUL + (255,)
    for x in range(6, 10):
        s[x, 5] = SOUL + (255,)
        s[x, 10] = SOUL + (255,)
    side.save(os.path.join(d, f"{KEY}_side.png"))
    Image.new("RGBA", (16, 16), DARK + (255,)).save(os.path.join(d, f"{KEY}_bottom.png"))
    path = f"altar_{KEY}"
    vk.write_json(os.path.join(RPG, "blockstates", path + ".json"), {"variants": {"": {"model": f"rpg:block/{path}"}}}, 2)
    vk.write_json(os.path.join(RPG, "models", "block", path + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"rpg:block/altar/{KEY}_top", "bottom": f"rpg:block/altar/{KEY}_bottom", "side": f"rpg:block/altar/{KEY}_side"}}, 2)
    vk.write_json(os.path.join(RPG, "items", path + ".json"), {"model": {"type": "minecraft:model", "model": f"rpg:block/{path}"}}, 2)


# ---------------------------------------------------------------- 보스 재료 아이콘 (32×32 — tools/loot_icons.py 의 틀)

def material_icons(mats):
    import loot_icons as li
    out = {}

    def hel_claw(ic):
        # 헬의 손톱: 휘어진 창백한 손톱 (끝으로 갈수록 가늘게) · 뿌리는 검은 손가락 마디 · 안쪽 영혼 빛 줄
        claw = ic.mask(lambda d: d.polygon([(7, 28), (12, 29), (17, 24), (22, 17), (26, 9), (28, 3), (24, 7), (19, 13), (14, 18), (9, 22)], fill=255))
        ic.fill(claw, (226, 232, 242), light=0.25, dark=0.35, noise=4, seed=300)
        root = ic.mask(lambda d: d.polygon([(4, 26), (8, 22), (13, 27), (11, 31), (6, 31)], fill=255))
        ic.fill(root, (40, 38, 50), light=0.3, dark=0.3, noise=4, seed=301)
        ic.line([(11, 25), (16, 20), (21, 14), (25, 8)], (110, 230, 255), claw)
        ic.line([(13, 26), (18, 21)], (200, 244, 255), claw)
        ic.put(7, 27, (120, 116, 132))
        return [(27, 3, (220, 248, 255)), (5, 6, (180, 236, 255)), (26, 24, (200, 244, 255))]

    def dead_mist(ic):
        # 망자의 안개: 작은 유리병 (검은 마개 · 뼈 끈) 안에서 도는 창백한 안개 · 영혼 빛 얼굴 무늬
        glass = ic.mask(lambda d: d.ellipse((7, 10, 25, 29), fill=255))
        ic.fill(glass, (120, 168, 196), light=0.35, dark=0.25, noise=3, seed=310)
        neck = ic.mask(lambda d: d.rectangle((13, 5, 19, 11), fill=255))
        ic.fill(neck, (130, 176, 204), light=0.3, dark=0.2, noise=2, seed=311)
        cork = ic.mask(lambda d: d.rectangle((12, 2, 20, 5), fill=255))
        ic.fill(cork, (36, 32, 44), light=0.3, dark=0.3, noise=3, seed=312)
        mist = ic.mask(lambda d: d.polygon([(10, 22), (13, 16), (18, 14), (22, 17), (21, 23), (16, 26)], fill=255))
        ic.fill(mist, (232, 240, 248), light=0.15, dark=0.2, noise=5, seed=313)
        ic.line([(11, 20), (15, 18), (19, 19), (22, 21)], (190, 236, 255), glass)
        ic.put(14, 20, (60, 200, 255))
        ic.put(18, 20, (60, 200, 255))
        ic.line([(15, 23), (17, 23)], (60, 200, 255))
        ic.line([(12, 11), (20, 11)], (216, 208, 188))
        return [(25, 9, (220, 248, 255)), (6, 26, (180, 236, 255)), (27, 27, (210, 244, 255))]

    paint = {"hel_claw": hel_claw, "dead_mist": dead_mist}
    for k, painter in paint.items():
        m = mats[k]
        glow = (120, 220, 255)
        ic = li.Icon(glow)
        sparkles = painter(ic)
        name = m["item"].split(":")[1]
        ic.render(sparkles).save(os.path.join(RPG, "textures", "item", "material", f"{name}.png"))
        vk.write_json(os.path.join(RPG, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}}, 2)
        vk.write_json(os.path.join(RPG, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/material/{name}"}}, 2)
        out[m["item"]] = {"name": f"{m['color']}{m['name']}§r§7 (보스 재료)", "glint": True, "stack": 64}
    return out


# ---------------------------------------------------------------- 실행

def main():
    more = vk.load(os.path.join(CORE, "rules_more.json"))
    b = more["BOSSES"][KEY]
    ent_path = os.path.join(RES, "mmorpg", "entities.json")
    rend_path = os.path.join(MM, "bedrock", "entities.json")
    ents = vk.load(ent_path)
    rend = vk.load(rend_path)
    # 모델 (사용자 디자인 — ID 만 바꿈)
    geo_file = vk.load(os.path.join(ART, "hel.geo.json"))
    geo = geo_file["minecraft:geometry"][0]
    geo["description"]["identifier"] = GEO_ID
    # 동작: 사용자 것 (idle · walk · attack) + stride (walk 를 시간으로) + 새 동작
    user = vk.load(os.path.join(ART, "hel.animation.json"))["animations"]
    anims = {}
    for full, a in user.items():
        anims[full.split(".")[-1]] = a
    for need in ("idle", "walk", "attack"):
        if need not in anims:
            raise SystemExit(f"사용자 동작 {need} 이 없음")
    anims["stride"] = stride_of(anims["walk"])
    for n, a in hel_anims.anims().items():
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
    # 렌더 컨트롤러: 각성 (rpg:phase 1) 이면 검은 핏줄 텍스처, 맞으면 번쩍임 (다른 보스와 같은 붉은빛)
    rend["render_controllers"][CONTROLLER] = {
        "arrays": {"textures": {"Array.skin": ["Texture.default", "Texture.rage"]}},
        "geometry": "Geometry.default",
        "materials": [{"*": "Material.default"}],
        "textures": ["Array.skin[q.property('rpg:phase')]"],
        "overlay_color": {"r": 1.0, "g": 0.22, "b": 0.16, "a": "q.property('rpg:flash') ? 0.45 : (q.property('rpg:rage') ? 0.22 : 0.0)"},
    }
    glow, holes, n_cells, veins = textures(geo)
    emissive = rend.setdefault("emissive", [])
    for t in (TEX, TEX_RAGE):
        if t not in emissive:
            emissive.append(t)
    vk.write_json(ent_path, ents)
    vk.write_json(rend_path, rend)
    # 제단 · 재료 · 이름
    altar_textures()
    mats_path = os.path.join(MM, "materials.json")
    mats = vk.load(mats_path)
    mats.update(material_icons(more["BOSS_MATERIALS"]))
    vk.write_json(mats_path, mats, 2)
    for code in ("ko_kr", "en_us"):
        p = os.path.join(RPG, "lang", code + ".json")
        d = vk.load(p)
        d["entity.rpg.boss_" + KEY] = b["name"]
        d["block.rpg.altar_" + KEY] = b["name"] + " 제단"
        vk.write_json(p, d, 2)
    print(f"{EID}: bones {len(geo['bones'])}, cubes {sum(len(bb.get('cubes', [])) for bb in geo['bones'])}, texture cells {n_cells} (alpha 0 {holes}), "
          f"emissive {glow}, rage veins {veins}, animations {len(anims)}")


if __name__ == "__main__":
    main()
