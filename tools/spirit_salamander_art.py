#!/usr/bin/env python3
"""2026-11-11 불의 정령 샐러맨더 (정령 룬 일반 — 불의 정령) 의 3D 몹 그림 (모델 · 텍스처 · 동작) — tools/monster_kit.py 로 짬.

사용자 "골렘 룬과 같이 3d 몹 + 2d 연출" · 시범 "불의 정령 (샐러맨더를 3D 몹으로 — 네크로맨서 소환물처럼)": 예전에는 눈앞에 떠 있는 빛 (rpg:rfx_spirit 그림) 이었음.
불도마뱀: 붉은 비늘 몸 (등은 짙은 진홍 · 배는 주황) · 몸과 꼬리에 빛나는 용암 금 · 등을 따라 일렁이는 불꽃 볏 넷 · 머리 뒤 불꽃 뿔 · 빛나는 노란 눈 ·
벌릴 수 있는 턱 (불덩이를 뱉음) · 네 다리가 옆으로 벌어진 도마뱀 걸음 · 세 마디 꼬리 끝에 타오르는 불꽃.

  python3 tools/spirit_salamander_art.py                  # tools/monster_art/spirit_salamander/ 에 쓰고 게임 자원에 넣음 (아래)
  python3 tools/spirit_salamander_art.py --preview 폴더    # 확인용 그림 (tools/bedrock_preview.py — 자세마다 앞 · 옆 · 뒤)

동작: idle (숨 · 고개 · 꼬리 흔들림 · 볏 불꽃 일렁임 — 늘) · walk (대각선 다리 · 몸 · 꼬리 흔듦 — 늘, 걸을 때만 세기) ·
summon (땅의 불길에서 솟음 0.5초) · attack (고개를 젖혔다 턱을 벌리며 앞으로 — 0.2초에 불덩이: 서버 SALAMANDER_SPIT_AT 4틱) · vanish (작아지며 불꽃으로 흩어짐 0.4초).

게임에 넣는 곳 (있으면 바꿈 · 없으면 더함): src/main/resources/mmorpg/entities.json (행동 — allies 무리) · assets/mmorpg/bedrock/entities.json (그리기 · 모델 ·
동작 · 빛나는 텍스처 목록) · assets/mmorpg/textures/entity/rpg/allies/spirit_salamander(_emissive).png.
모드 없는 클라이언트의 뼈 모델은 tools/vanilla_models.py 를 다시 돌려 만듦.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from monster_kit import Mat, Model, anim_file, kf, write_json  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "monster_art")
RES = os.path.join(ROOT, "src", "main", "resources")
BEHAVIOR = os.path.join(RES, "mmorpg", "entities.json")
CLIENT = os.path.join(RES, "assets", "mmorpg", "bedrock", "entities.json")
TEX = os.path.join(RES, "assets", "mmorpg", "textures", "entity", "rpg", "allies")

KEY = "spirit_salamander"
LT = "q.life_time"
MS = "q.modified_move_speed"
DM = "q.modified_distance_moved"

BODY = (196, 52, 20)
BACK = (120, 24, 12)
BELLY = (244, 150, 52)
LAVA = (255, 196, 64)
EMBER = (255, 112, 24)
EYE = (255, 244, 150)


def walk_amt(k=3):
    return f"math.min({MS} * {k}, 1)"


def salamander():
    m = Model(KEY, tex_w=128)
    scale = Mat(BODY, "scales", noise=6, edge=0.84, seed=301, veins=3, vein=LAVA, vein_glow=0.8)
    back = Mat(BACK, "scales", noise=5, edge=0.82, seed=302, seam=EMBER, seam_glow=0.75, seam_band=3, seam_dir="h", seam_gap=0.35)
    belly = Mat(BELLY, "plates", noise=4, edge=0.9, band=2, seed=303)
    skin = Mat(BODY, "scales", noise=5, edge=0.86, seed=304)
    claw = Mat((60, 20, 14), "flat", noise=3, edge=0.9, seed=305)
    flame = Mat(EMBER, "flat", noise=10, edge=1.0, glow=0.9, seed=306)
    flame_hot = Mat(LAVA, "flat", noise=8, edge=1.0, glow=1.0, seed=307)
    mouth = Mat((90, 10, 8), "flat", noise=3, edge=0.9, glow=0.4, seed=308)

    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 6.5, 0], "root")
    # 몸통 (앞 −z 가 머리 쪽): 위 등 (짙은 진홍 · 빛나는 용암 줄) · 옆 비늘 (용암 금) · 배 (주황 판)
    body.box([-4, 4, -6], [8, 5, 13], scale, faces={"up": back, "down": belly})
    body.box([-3, 3.5, -5], [6, 1, 11], belly)  # 배 아래 (둥글게)
    # 등 불꽃 볏 넷 (뼈마다 — 따로 일렁임)
    for i, (z, h) in enumerate(((-4.5, 3), (-1.5, 4), (1.5, 4), (4.5, 3))):
        c = m.bone(f"crest{i}", [0, 9, z + 1], "body")
        c.box([-1, 9, z], [2, h, 2], flame)
        c.box([-0.5, 9 + h - 1, z + 0.5], [1, 2, 1], flame_hot)
    # 머리: 목에서 앞으로 · 위턱 (주둥이) · 아래턱 (따로 — 벌림) · 눈 (옆면 빛남) · 머리 뒤 불꽃 뿔 둘
    head = m.bone("head", [0, 7.5, -6], "body")
    head.box([-3, 5, -11], [6, 5, 5], skin, faces={"up": back}, decals=[
        ("west", 1, 1, EYE, 1.0), ("west", 2, 1, EYE, 1.0), ("east", 2, 1, EYE, 1.0), ("east", 3, 1, EYE, 1.0),
        ("west", 1, 2, (30, 8, 6)), ("east", 3, 2, (30, 8, 6))])
    head.box([-2.5, 6, -15], [5, 3, 4], skin, faces={"up": back}, decals=[("north", 1, 1, (40, 10, 8)), ("north", 3, 1, (40, 10, 8))])
    head.box([-2, 5, -14.5], [4, 1, 5], flame_hot)  # 입 속 (타오르는 빛 — 턱을 벌리면 보임)
    jaw = m.bone("jaw", [0, 5, -9.5], "head")
    jaw.box([-2.5, 4, -14.5], [5, 1, 5], skin.with_(seed=309), faces={"down": belly.with_(seed=316)})
    jaw.box([-2, 5, -14], [4, 1, 1], Mat((250, 240, 220), "flat", noise=2, edge=0.95, seed=317), inflate=-0.2)  # 아랫니
    for side, sx in (("hornR", -1), ("hornL", 1)):
        hb = m.bone(side, [2 * sx, 9.5, -7], "head", [-35, 0, 18 * sx])
        hb.box([2 * sx - 0.5, 9.5, -7.5], [1, 3, 1], flame)
        hb.box([2 * sx - 0.5, 12.5, -7.5], [1, 1, 1], flame_hot)
    # 꼬리 세 마디 + 끝 불꽃
    t1 = m.bone("tail1", [0, 7, 7], "body")
    t1.box([-3, 4.5, 7], [6, 4, 6], scale.with_(seed=310), faces={"up": back.with_(seed=311), "down": belly})
    t2 = m.bone("tail2", [0, 7, 13], "tail1")
    t2.box([-2, 5, 13], [4, 3, 6], scale.with_(seed=312), faces={"up": back.with_(seed=313), "down": belly})
    t3 = m.bone("tail3", [0, 7, 19], "tail2")
    t3.box([-1.5, 5.5, 19], [3, 2, 5], scale.with_(seed=314), faces={"up": back.with_(seed=315)})
    tf = m.bone("tailFlame", [0, 6.5, 24], "tail3")
    tf.box([-1.5, 5.5, 23.5], [3, 3, 3], flame)
    tf.box([-1, 8, 24], [2, 3, 2], flame_hot)
    tf.box([-0.5, 10.5, 24.5], [1, 2, 1], flame)
    # 네 다리 (옆으로 벌어진 도마뱀 다리 — 윗다리는 옆으로 · 아랫다리는 아래 · 발가락 발톱)
    for name, sx, z in (("legFR", -1, -4), ("legFL", 1, -4), ("legBR", -1, 4), ("legBL", 1, 4)):
        lb = m.bone(name, [4 * sx, 6.5, z], "body")
        x_up = -7 if sx < 0 else 4
        lb.box([x_up, 5, z - 1], [3, 2, 2], skin.with_(seed=320 + len(name) + int(z)))
        x_lo = -7 if sx < 0 else 5
        lb.box([x_lo, 1, z - 1], [2, 4, 2], skin.with_(seed=330 + int(z) + sx))
        lb.box([x_lo - (1 if sx < 0 else 0), 0, z - 2], [3, 1, 3], claw)
    return m


def anims():
    breathe = f"math.sin({LT} * 160)"
    return {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"{breathe} * 0.25", 0], "rotation": [0, f"math.sin({LT} * 70) * 3", 0]},
            "head": {"rotation": [f"math.sin({LT} * 90) * 4 - 4", f"math.sin({LT} * 55) * 12", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 120)) * 6", 0, 0]},
            "tail1": {"rotation": [0, f"math.sin({LT} * 110) * 10", 0]},
            "tail2": {"rotation": [f"-6", f"math.sin({LT} * 110 - 40) * 14", 0]},
            "tail3": {"rotation": [f"-8", f"math.sin({LT} * 110 - 80) * 18", 0]},
            "tailFlame": {"scale": [f"1 + math.sin({LT} * 900) * 0.12", f"1 + math.sin({LT} * 700 + 40) * 0.25", f"1 + math.sin({LT} * 800) * 0.12"]},
            "crest0": {"scale": [1, f"1 + math.sin({LT} * 780) * 0.22", 1]},
            "crest1": {"scale": [1, f"1 + math.sin({LT} * 700 + 60) * 0.25", 1]},
            "crest2": {"scale": [1, f"1 + math.sin({LT} * 820 + 120) * 0.25", 1]},
            "crest3": {"scale": [1, f"1 + math.sin({LT} * 760 + 200) * 0.22", 1]},
            "hornR": {"scale": [1, f"1 + math.sin({LT} * 900) * 0.2", 1]},
            "hornL": {"scale": [1, f"1 + math.sin({LT} * 900 + 90) * 0.2", 1]},
        }},
        "walk": {"loop": True, "bones": {
            "legFR": {"rotation": [f"math.cos({DM} * 70) * 30 * {MS}", f"math.sin({DM} * 70) * 20 * {walk_amt()}", 0]},
            "legBL": {"rotation": [f"math.cos({DM} * 70) * 30 * {MS}", f"-math.sin({DM} * 70) * 20 * {walk_amt()}", 0]},
            "legFL": {"rotation": [f"-math.cos({DM} * 70) * 30 * {MS}", f"-math.sin({DM} * 70) * 20 * {walk_amt()}", 0]},
            "legBR": {"rotation": [f"-math.cos({DM} * 70) * 30 * {MS}", f"math.sin({DM} * 70) * 20 * {walk_amt()}", 0]},
            "body": {"rotation": [0, f"math.sin({DM} * 70) * 8 * {walk_amt()}", 0]},
            "tail1": {"rotation": [0, f"-math.sin({DM} * 70) * 12 * {walk_amt()}", 0]},
            "tail2": {"rotation": [0, f"-math.sin({DM} * 70 - 40) * 14 * {walk_amt()}", 0]},
        }},
        # 땅의 불길에서 솟음: 아래 10 픽셀에서 올라오며 커짐 · 머리를 치켜듦
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, -10, 0]), (0.3, [0, 1.5, 0]), (0.4, [0, 0, 0])]),
                     "scale": kf([(0, [0.5, 0.5, 0.5]), (0.3, [1.08, 1.08, 1.08]), (0.5, [1, 1, 1])])},
            "head": {"rotation": kf([(0, [30, 0, 0]), (0.3, [-28, 0, 0]), (0.5, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0]), (0.45, [0, 0, 0])])},
        }},
        # 불덩이 뱉기: 0.12 고개를 젖힘 · 몸을 뒤로 → 0.2 앞으로 내밀며 턱을 크게 벌림 (불덩이 — 서버 SALAMANDER_SPIT_AT 4틱) → 0.5 돌아옴
        "attack": {"animation_length": 0.5, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.12, [-30, 0, 0]), (0.2, [14, 0, 0]), (0.32, [8, 0, 0]), (0.5, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.12, [8, 0, 0]), (0.2, [42, 0, 0]), (0.32, [30, 0, 0]), (0.5, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.12, [0, 0.6, 1.2]), (0.2, [0, -0.2, -1.4]), (0.5, [0, 0, 0])]),
                     "rotation": kf([(0, [0, 0, 0]), (0.12, [-8, 0, 0]), (0.2, [5, 0, 0]), (0.5, [0, 0, 0])])},
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.12, [10, 0, 0]), (0.2, [-8, 0, 0]), (0.5, [0, 0, 0])])},
            "crest1": {"scale": kf([(0, [1, 1, 1]), (0.2, [1.2, 1.7, 1.2]), (0.5, [1, 1, 1])])},
            "crest2": {"scale": kf([(0, [1, 1, 1]), (0.2, [1.2, 1.6, 1.2]), (0.5, [1, 1, 1])])},
        }},
        # 흩어짐: 작아지며 위로 · 볏 불꽃이 커졌다 사라짐
        "vanish": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 5, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.1, 1.1, 1.1]), (0.4, [0.1, 0.1, 0.1])])},
        }},
    }


BEHAVIOR_DEF = {"family": ["rpg_ally", "spirit_salamander"], "health": 40, "movement": 0.32, "box": [0.9, 0.8]}


def behavior(d):
    c = {
        "minecraft:type_family": {"family": d["family"]},
        "minecraft:health": {"value": d["health"], "max": d["health"]},
        "minecraft:movement": {"value": d["movement"]},
        "minecraft:jump.static": {},
        "minecraft:collision_box": {"width": d["box"][0], "height": d["box"][1]},
        "minecraft:scale": {"value": 1},
        "minecraft:physics": {},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:knockback_resistance": {"value": 1},
        "minecraft:follow_range": {"value": 24, "max": 24},
        "minecraft:fire_immune": {},
        "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
        "minecraft:experience_reward": {"on_death": "0"},
        "minecraft:movement.basic": {},
        "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True, "avoid_damage_blocks": True},
    }
    return {"group": "allies", "properties": {}, "components": c, "component_groups": {}, "events": {}}


def build():
    m = salamander()
    m.pack()
    im = m.paint()
    d = os.path.join(ART, KEY)
    os.makedirs(d, exist_ok=True)
    geo = m.geo_json()
    a = anims()
    write_json(os.path.join(d, KEY + ".geo.json"), geo)
    im.save(os.path.join(d, KEY + ".png"))
    write_json(os.path.join(d, KEY + ".animation.json"), anim_file(KEY, a))
    return geo, im, a


def install(geo, im, a):
    beh = json.load(open(BEHAVIOR, encoding="utf-8"))
    beh["rpg:" + KEY] = behavior(BEHAVIOR_DEF)
    with open(BEHAVIOR, "w", encoding="utf-8") as f:
        json.dump(beh, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    cl = json.load(open(CLIENT, encoding="utf-8"))
    os.makedirs(TEX, exist_ok=True)
    eid = "rpg:" + KEY
    tex = f"textures/entity/rpg/allies/{KEY}"
    cl["entities"][eid] = {"identifier": eid, "materials": {"default": "entity_emissive"}, "textures": {"default": tex},
                           "geometry": {"default": f"geometry.rpg.monster.{KEY}"},
                           "animations": {n: f"animation.rpg.monster.{KEY}.{n}" for n in a},
                           "scripts": {"animate": ["idle", "walk"]}, "render_controllers": ["controller.render.default"]}
    g = geo["minecraft:geometry"][0]
    cl["models"][g["description"]["identifier"]] = {"description": g["description"], "bones": g["bones"]}
    for n, an in a.items():
        cl["animations"][f"animation.rpg.monster.{KEY}.{n}"] = an
    # 텍스처 · 빛나는 칸 (알파 255 보다 낮은 칸 — 베드락 entity_emissive)
    base = im.copy()
    emi = Image.new("RGBA", im.size, (0, 0, 0, 0))
    bp, ep = base.load(), emi.load()
    glow = False
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g2, b, al = bp[x, y]
            if 0 < al < 255:
                ep[x, y] = (r, g2, b, 255)
                bp[x, y] = (r, g2, b, 255)
                glow = True
    base.save(os.path.join(TEX, KEY + ".png"))
    if glow:
        emi.save(os.path.join(TEX, KEY + "_emissive.png"))
        if tex not in cl["emissive"]:
            cl["emissive"].append(tex)
    with open(CLIENT, "w", encoding="utf-8") as f:
        json.dump(cl, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


def main():
    args = sys.argv[1:]
    geo, im, a = build()
    install(geo, im, a)
    print("salamander art: bones", len(geo["minecraft:geometry"][0]["bones"]), "texture", im.size)
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
        import bedrock_preview as BP
        for names, t in ((["idle"], 0.0), (["idle", "attack"], 0.3), (["summon"], 0.15), (["idle", "walk"], 0.3)):
            out = os.path.join(folder, f"{KEY}_{'_'.join(names)}_{t:.2f}.png")
            print("  preview:", BP.sheet(geo, im, a, names, t, out, label=KEY))


if __name__ == "__main__":
    main()
