#!/usr/bin/env python3
"""2026-11-07 정령 궁수 (궁수 1차 재설계안 3판) 와일드 헌트의 늑대 정령 (rpg:spirit_wolf — 진짜 몹, 곁에서 달리다 활 평타가 맞힌 적에게 달려들어 물어뜯음):
북쪽 몬스터 서리 늑대 (rpg:frost_wolf — 몸 · 머리 · 턱 · 귀 · 다리 넷 · 꼬리 · 등의 얼음 결정 · 동작 idle · walk · attack · howl) 의 텍스처를 숲의 정령빛으로 다시 칠함
→ assets/mmorpg/textures/entity/rpg/allies/spirit_wolf(_emissive).png · 그리기 (bedrock/entities.json — 모델 · 동작은 서리 늑대 그대로, 비치는 몸
entity_alphablend + 온몸 빛남) · 행동 (mmorpg/entities.json — allies 무리 · 맞지 않음).

  python3 tools/spirit_wolf_tex.py              # 텍스처 두 장 · 엔티티 정의를 씀
  python3 tools/spirit_wolf_tex.py --preview D  # 확인용 그림 (tools/bedrock_preview.py — 서 있음 · 달림 · 물어뜯음)

칠하기 (밝기 그대로 · 같은 무늬): 회색 털 → 어두운 곳 짙은 숲 초록 · 밝은 곳 옥빛 연두 (밝을수록 금빛 연두), 청록 얼음 결정 · 눈 → 금빛.
바탕은 반쯤 비침 (알파 0.62 — 결정 · 눈 0.95), 빛나는 칸 = 온몸 (털 0.5 · 결정 · 눈 1.0) — 낮에도 정령빛으로 보이게.
"""
import colorsys
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
SRC = os.path.join(RES, "assets/mmorpg/textures/entity/rpg/monsters/frost_wolf")
TEX = os.path.join(RES, "assets/mmorpg/textures/entity/rpg/allies")
BEHAVIOR = os.path.join(RES, "mmorpg", "entities.json")
CLIENT = os.path.join(RES, "assets", "mmorpg", "bedrock", "entities.json")
EID = "rpg:spirit_wolf"
KEY = "spirit_wolf"

DEEP = np.array([0.08, 0.30, 0.12])
JADE = np.array([0.36, 0.86, 0.34])
PALE = np.array([0.86, 1.0, 0.56])
GOLD = np.array([1.0, 0.84, 0.32])


def recolor():
    im = np.asarray(Image.open(SRC + ".png").convert("RGBA")).astype(np.float64) / 255.0
    em = np.asarray(Image.open(SRC + "_emissive.png").convert("RGBA")).astype(np.float64) / 255.0
    base = np.zeros_like(im)
    glow = np.zeros_like(im)
    h, w = im.shape[:2]
    for y in range(h):
        for x in range(w):
            r, g, b, a = im[y, x]
            if a <= 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r, g, b)
            deg = hh * 360
            crystal = (s > 0.25 and 170 <= deg <= 215) or em[y, x, 3] > 0
            if crystal:
                c = GOLD * (0.75 + 0.35 * v)
                base[y, x] = [*np.clip(c, 0, 1), 0.95]
                glow[y, x] = [*np.clip(c * 1.05, 0, 1), 1.0]
                continue
            k = np.clip((v - 0.3) / 0.6, 0, 1)
            c = DEEP + (JADE - DEEP) * min(1.0, k * 1.6) if k < 0.62 else JADE + (PALE - JADE) * ((k - 0.62) / 0.38)
            base[y, x] = [*np.clip(c, 0, 1), 0.62]
            glow[y, x] = [*np.clip(c * 1.1, 0, 1), 0.42 + 0.3 * k]
    return Image.fromarray((base * 255).round().astype(np.uint8), "RGBA"), Image.fromarray((glow * 255).round().astype(np.uint8), "RGBA")


def install():
    beh = json.load(open(BEHAVIOR, encoding="utf-8"))
    beh[EID] = {"group": "allies", "properties": {}, "components": {
        "minecraft:type_family": {"family": ["rpg_ally", KEY]},
        "minecraft:health": {"value": 100, "max": 100},
        "minecraft:movement": {"value": 0.42},
        "minecraft:jump.static": {},
        "minecraft:collision_box": {"width": 0.8, "height": 1.0},
        "minecraft:scale": {"value": 1.05},
        "minecraft:physics": {},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:knockback_resistance": {"value": 1},
        "minecraft:follow_range": {"value": 32, "max": 32},
        "minecraft:fire_immune": {},
        "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
        "minecraft:experience_reward": {"on_death": "0"},
        "minecraft:movement.basic": {},
        "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True, "avoid_damage_blocks": True},
    }, "component_groups": {}, "events": {}}
    with open(BEHAVIOR, "w", encoding="utf-8") as f:
        json.dump(beh, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    cl = json.load(open(CLIENT, encoding="utf-8"))
    tex = f"textures/entity/rpg/allies/{KEY}"
    src = cl["entities"]["rpg:frost_wolf"]
    cl["entities"][EID] = {"identifier": EID, "materials": {"default": "entity_alphablend"}, "textures": {"default": tex},
                           "geometry": dict(src["geometry"]), "animations": dict(src["animations"]), "scripts": {"animate": ["idle", "walk"]},
                           "render_controllers": ["controller.render.default"]}
    if tex not in cl["emissive"]:
        cl["emissive"].append(tex)
    with open(CLIENT, "w", encoding="utf-8") as f:
        json.dump(cl, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


def main():
    base, glow = recolor()
    os.makedirs(TEX, exist_ok=True)
    base.save(os.path.join(TEX, KEY + ".png"))
    glow.save(os.path.join(TEX, KEY + "_emissive.png"))
    install()
    print("spirit wolf:", os.path.join(TEX, KEY + ".png"))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        art = os.path.join(ROOT, "tools", "monster_art", "frost_wolf")
        flat = os.path.join(out, KEY + "_flat.png")
        # 확인용: 바탕 + 빛을 겹친 색 (비침 없이)
        b = np.asarray(base).astype(np.float64) / 255.0
        g = np.asarray(glow).astype(np.float64) / 255.0
        c = b.copy()
        c[..., :3] = np.clip(b[..., :3] * 0.6 + g[..., :3] * g[..., 3:4] * 0.6, 0, 1)
        c[..., 3] = (b[..., 3] > 0).astype(np.float64)
        Image.fromarray((c * 255).round().astype(np.uint8), "RGBA").save(flat)
        for anim, t in (("idle", 0.3), ("walk", 0.25), ("attack", 0.28), ("howl", 0.6)):
            subprocess.run([sys.executable, os.path.join(ROOT, "tools", "bedrock_preview.py"), os.path.join(art, "frost_wolf.geo.json"), flat,
                            os.path.join(art, "frost_wolf.animation.json"), os.path.join(out, f"{KEY}_{anim}.png"), "--anims", anim, "--t", str(t)], check=True)


if __name__ == "__main__":
    main()
