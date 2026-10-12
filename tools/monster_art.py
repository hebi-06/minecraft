#!/usr/bin/env python3
"""9단계 새 몬스터 그림 (모델 · 텍스처 · 애니메이션) — tools/monster_kit.py 로 짬.

tools/monster_art.py              tools/monster_art/<키>/ 에 <키>.geo.json · <키>.png · <키>.animation.json (7종 — 그렌델은 클로드 디자인 원본을 복사)
tools/monster_art.py --preview 그림.png [--pose attack --t 0.3 --walk]   (애드온 미리보기 그리기 — RPG_ADDON)

지역마다 둘 (rules_more.json): 농장 니세 · 광폭 멧돼지, 사막 모래 전갈 · 웜 유충, 바다 집게 게 · 그렌델, 북쪽 서리 늑대 · 서리 거인.
좌표는 베드락 그대로 (픽셀 · 몬스터는 -z 를 봄 · -x 가 오른쪽). 동작 이름: idle · walk (늘), attack (공격 — Monsters 가 틈),
특성 동작 hop (니세) · charge_prep · charge (멧돼지) · buried · emerge (웜 유충) · howl (서리 늑대).
"""
import json
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from monster_kit import Mat, Model, anim_file, closeup, kf, preview, write_json  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "monster_art")
MORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules_more.json")
ADDON = os.environ.get("RPG_ADDON", os.path.join(ROOT, "..", "rpg_addon"))

MS = "q.modified_move_speed"
DM = "q.modified_distance_moved"
LT = "q.life_time"
LOOK_X = "q.target_x_rotation"
LOOK_Y = "q.target_y_rotation"


def walk_amt(k=3):
    return f"math.min({MS} * {k}, 1)"


# ================================================================= 니세 (농장 요정)

def nisse():
    m = Model("nisse")
    red = Mat("#c8322b", "knit", noise=6, accent="#8e1f1c", seed=1)
    white = Mat("#ecebe4", "fur", noise=8, edge=0.9, seed=2)
    beard = Mat("#e4e2da", "fur", noise=10, edge=0.88, seed=3)
    skin = Mat("#eebc9c", "skin", noise=5, accent="#d89a80", seed=4)
    tunic = Mat("#5b6f86", "knit", noise=6, accent="#465669", seed=5)
    belt = Mat("#6e4428", "flat", noise=5, seed=6)
    pants = Mat("#4a5164", "knit", noise=5, seed=7)
    boots = Mat("#463024", "flat", noise=6, seed=8)
    wood = Mat("#9a6a3c", "wood", noise=6, seed=9)
    metal = Mat("#aeb3b8", "metal", noise=5, edge=0.9, seed=10)
    nose = Mat("#e88c80", "flat", noise=4, edge=0.95, seed=11)

    body = m.bone("body", [0, 3, 0])
    body.box([-3, 3, -2], [6, 5, 4], tunic)
    body.box([-3, 4, -2], [6, 1, 4], belt, inflate=0.2, decals=[("north", 2, 0, "#e3c25a"), ("north", 3, 0, "#e3c25a")])
    head = m.bone("head", [0, 8, 0], "body")
    eye = (255, 214, 64)
    head.box([-3, 8, -3], [6, 6, 6], skin, decals=[
        ("north", 1, 2, eye, 0.9), ("north", 4, 2, eye, 0.9),  # 반짝이는 노란 눈
        ("north", 0, 1, "#f2f0ea"), ("north", 1, 1, "#f2f0ea"), ("north", 4, 1, "#f2f0ea"), ("north", 5, 1, "#f2f0ea"),  # 하얀 눈썹
        ("north", 2, 3, "#d89a80"), ("north", 3, 3, "#d89a80")])
    head.box([-3.5, 5, -4], [7, 5, 2], beard, faces={"up": beard.with_(base="#f4f2ec")})
    head.box([-1, 9, -5], [2, 2, 1], nose)
    hat = m.bone("hat", [0, 14, 0], "head")
    hat.box([-3.5, 13, -3.5], [7, 2, 7], red.with_(base="#b42a25", seed=12))
    hat2 = m.bone("hat2", [0, 15, 0.5], "hat", [-12, 0, 0])
    hat2.box([-3, 15, -2.5], [6, 3, 6], red)
    hat3 = m.bone("hat3", [0, 18, 1], "hat2", [-16, 0, 0])
    hat3.box([-2, 18, -1], [4, 3, 4], red.with_(seed=13))
    hat4 = m.bone("hat4", [0, 21, 1.5], "hat3", [-22, 0, 0])
    hat4.box([-1, 21, 0.5], [2, 2, 2], red.with_(seed=14))
    pom = m.bone("pompom", [0, 23, 1.5], "hat4", [-10, 0, 0])
    pom.box([-1.5, 22.5, 0], [3, 3, 3], white)
    for side, sx in (("right", -1), ("left", 1)):
        arm = m.bone(f"{side}Arm", [3.5 * sx, 7.5, 0], "body")
        x0 = -5 if sx < 0 else 3
        arm.box([x0, 4, -1], [2, 4, 2], tunic.with_(seed=15 + sx))
        arm.box([x0, 3, -1], [2, 1, 2], skin)
        leg = m.bone(f"{side}Leg", [1.5 * sx, 3, 0])
        lx = -2.5 if sx < 0 else 0.5
        leg.box([lx, 1, -1], [2, 2, 2], pants)
        leg.box([lx, 0, -2], [2, 1, 3], boots)
    fork = m.bone("fork", [-4, 4, -1.5], "rightArm")
    fork.box([-4.5, 2, -2.5], [1, 11, 1], wood)
    fork.box([-6.5, 13, -2.5], [5, 1, 1], metal)
    for x in (-6.5, -4.5, -2.5):
        fork.box([x, 14, -2.5], [1, 3, 1], metal)

    look = {"rotation": [f"{LOOK_X} * 0.6 + math.sin({LT} * 70) * 3", f"{LOOK_Y} * 0.6", 0]}
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 160) * 0.3", 0]},
            "head": look,
            "hat2": {"rotation": [0, 0, f"math.sin({LT} * 90) * 4"]},
            "hat3": {"rotation": [0, 0, f"math.sin({LT} * 90 + 40) * 6"]},
            "hat4": {"rotation": [0, 0, f"math.sin({LT} * 90 + 80) * 9"]},
            "pompom": {"rotation": [0, 0, f"math.sin({LT} * 90 + 120) * 10"]},
            "leftArm": {"rotation": [f"math.sin({LT} * 80) * 6", 0, -4]},
            "rightArm": {"rotation": [f"math.sin({LT} * 80 + 90) * 3", 0, 4]},
        }},
        "walk": {"loop": True, "bones": {
            "rightLeg": {"rotation": [f"math.cos({DM} * 75) * 45 * {MS}", 0, 0]},
            "leftLeg": {"rotation": [f"-math.cos({DM} * 75) * 45 * {MS}", 0, 0]},
            "leftArm": {"rotation": [f"math.cos({DM} * 75) * 35 * {MS}", 0, 0]},
            "rightArm": {"rotation": [f"-math.cos({DM} * 75) * 12 * {MS}", 0, 0]},
            "body": {"position": [0, f"math.abs(math.sin({DM} * 75)) * 1 * {walk_amt()}", 0], "rotation": [0, 0, f"math.sin({DM} * 75) * 4 * {walk_amt()}"]},
            "hat3": {"rotation": [f"math.abs(math.sin({DM} * 75)) * -8 * {walk_amt()}", 0, 0]},
        }},
        # 쇠스랑으로 찌르기: 0.15 뒤로 → 0.3 (windup 6틱) 앞으로 찌름
        "attack": {"animation_length": 0.55, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [35, 0, 6]), (0.3, [-92, 0, 0]), (0.42, [-80, 0, 0]), (0.55, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.3, [14, 0, 0]), (0.55, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-10, 0, 0]), (0.3, [8, 0, 0]), (0.55, [0, 0, 0])])},
        }},
        # 뒤로 깡충 (때린 뒤 — Monsters 의 hop): 몸을 뒤로 젖히며 다리를 접음
        "hop": {"animation_length": 0.5, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-28, 0, 0]), (0.35, [-10, 0, 0]), (0.5, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.2, [0, 2, 0]), (0.5, [0, 0, 0])])},
            "rightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-50, 0, 0]), (0.4, [0, 0, 0])])},
            "leftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-50, 0, 0]), (0.4, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-120, 0, -20]), (0.45, [0, 0, 0])])},
            "hat3": {"rotation": kf([(0, [0, 0, 0]), (0.2, [25, 0, 0]), (0.5, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 광폭 멧돼지

def boar():
    m = Model("boar")
    fur = Mat("#664632", "bristle", noise=8, accent="#3a271c", seed=21)
    mane = Mat("#2e2018", "bristle", noise=8, accent="#1b130e", seed=22)
    belly = Mat("#7a5a40", "fur", noise=7, seed=23)
    snout = Mat("#a8706a", "skin", noise=5, accent="#8a5550", seed=24)
    tusk = Mat("#eee4c8", "bone", noise=4, edge=0.92, seed=25)
    hoof = Mat("#2f2a26", "flat", noise=4, seed=26)
    eye = (255, 70, 40)

    body = m.bone("body", [0, 10, 0])
    body.box([-5, 6, -8], [10, 9, 16], fur, faces={"down": belly})
    body.box([-4, 15, -8], [8, 2, 9], fur.with_(seed=27))
    body.box([-1, 16, -9], [2, 3, 13], mane)
    head = m.bone("head", [0, 12, -8], "body")
    head.box([-4, 7, -15], [8, 7, 7], fur.with_(seed=28), decals=[("north", 1, 1, eye, 0.7), ("north", 6, 1, eye, 0.7),
                                                                 ("north", 1, 0, "#1e140f"), ("north", 6, 0, "#1e140f")])
    head.box([-2.5, 7, -18], [5, 4, 3], snout, decals=[("north", 1, 1, "#5a3030"), ("north", 3, 1, "#5a3030"), ("north", 1, 2, "#5a3030"),
                                                       ("north", 3, 2, "#5a3030")])
    for sx in (-1, 1):
        tx = -4 if sx < 0 else 3
        head.box([tx, 8, -17], [1, 3, 1], tusk)
        head.box([tx + sx, 10, -17], [1, 3, 1], tusk.with_(seed=29))
        ear = m.bone("rightEar" if sx < 0 else "leftEar", [3 * sx, 14, -10], "head", [0, 0, 25 * sx])
        ex = -4 if sx < 0 else 2
        ear.box([ex, 14, -11], [2, 3, 1], fur.with_(base="#4a3222", seed=30))
    for name, px, pz in (("frontRightLeg", -3, -5), ("frontLeftLeg", 3, -5), ("backRightLeg", -3, 5), ("backLeftLeg", 3, 5)):
        leg = m.bone(name, [px, 7, pz])
        x0 = px - 1.5
        leg.box([x0, 1, pz - 1.5], [3, 6, 3], fur.with_(seed=31 + int(px) + int(pz)))
        leg.box([x0, 0, pz - 1.5], [3, 1, 3], hoof)
    tail = m.bone("tail", [0, 13, 8], "body", [20, 0, 0])
    tail.box([-0.5, 9, 8], [1, 4, 1], fur.with_(seed=35))
    tail.box([-1, 8, 7.5], [2, 2, 2], mane.with_(seed=36))

    look = {"rotation": [f"{LOOK_X} * 0.5 + math.sin({LT} * 60) * 3", f"{LOOK_Y} * 0.5", 0]}
    gal = f"math.cos({DM} * 45) * 34 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 110) * 0.25", 0]},
            "head": look,
            "rightEar": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 160)) * 12"]},
            "leftEar": {"rotation": [0, 0, f"-math.max(0, math.sin({LT} * 160 + 70)) * 12"]},
            "tail": {"rotation": [0, 0, f"math.sin({LT} * 400) * 25"]},
        }},
        "walk": {"loop": True, "bones": {
            "frontRightLeg": {"rotation": [gal, 0, 0]},
            "backLeftLeg": {"rotation": [gal, 0, 0]},
            "frontLeftLeg": {"rotation": [f"-{gal}", 0, 0]},
            "backRightLeg": {"rotation": [f"-{gal}", 0, 0]},
            "body": {"rotation": [0, 0, f"math.cos({DM} * 45) * 3 * {walk_amt()}"]},
            "head": {"rotation": [f"math.sin({DM} * 90) * 3 * {walk_amt()}", 0, 0]},
        }},
        # 머리로 들이받아 올려 치기 (windup 9틱 = 0.45 초)
        "attack": {"animation_length": 0.7, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [28, 0, 0]), (0.45, [-38, 0, 0]), (0.7, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [6, 0, 0]), (0.45, [-8, 0, 0]), (0.7, [0, 0, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-20, 0, 0]), (0.45, [25, 0, 0]), (0.7, [0, 0, 0])])},
            "frontLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-20, 0, 0]), (0.45, [25, 0, 0]), (0.7, [0, 0, 0])])},
        }},
        # 돌진 준비: 고개를 숙이고 앞발로 땅을 두 번 긁음 (prep 14틱 = 0.7초)
        "charge_prep": {"animation_length": 0.7, "loop": "hold_on_last_frame", "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [26, 0, 0]), (0.7, [26, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [7, 0, 0]), (0.7, [7, 0, 0])]), "position": kf([(0, [0, 0, 0]), (0.15, [0, -1, 0]), (0.7, [0, -1, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.12, [-55, 0, 0]), (0.25, [30, 0, 0]), (0.37, [-55, 0, 0]), (0.5, [30, 0, 0]), (0.62, [0, 0, 0])])},
            "tail": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-40, 0, 0]), (0.7, [-40, 0, 0])])},
        }},
        # 돌진: 고개를 숙인 채 네 다리로 빠르게 (Monsters 가 dash 동안 틂)
        "charge": {"loop": True, "bones": {
            "head": {"rotation": [30, 0, 0]},
            "body": {"rotation": [5, 0, 0]},
            "frontRightLeg": {"rotation": ["math.sin(q.anim_time * 1400) * 60", 0, 0]},
            "frontLeftLeg": {"rotation": ["math.sin(q.anim_time * 1400 + 40) * 60", 0, 0]},
            "backRightLeg": {"rotation": ["-math.sin(q.anim_time * 1400) * 55", 0, 0]},
            "backLeftLeg": {"rotation": ["-math.sin(q.anim_time * 1400 + 40) * 55", 0, 0]},
            "tail": {"rotation": [-60, 0, 0]},
        }},
    }
    return m, anims


# ================================================================= 모래 전갈

def sand_scorpion():
    m = Model("sand_scorpion")
    shell = Mat("#d6a45c", "chitin", noise=6, band=3, seed=41)
    dark = Mat("#9c6a34", "chitin", noise=6, band=2, seed=42)
    leg = Mat("#b4823e", "flat", noise=6, seed=43)
    tip = Mat("#4a301a", "flat", noise=4, seed=44)
    venom = Mat("#86f45a", "flat", noise=3, edge=1, glow=0.85, seed=45)
    eye = (255, 176, 40)

    body = m.bone("body", [0, 4, 0])
    body.box([-4, 1, -6], [8, 4, 7], shell)
    body.box([-3.5, 1.5, 1], [7, 4, 6], shell.with_(seed=46))
    body.box([-3, 1.5, -8], [6, 3, 2], dark, decals=[("north", 1, 0, eye, 0.7), ("north", 4, 0, eye, 0.7), ("north", 2, 0, eye, 0.5), ("north", 3, 0, eye, 0.5),
                                                     ("north", 2, 2, "#3a2414"), ("north", 3, 2, "#3a2414")])
    # 꼬리: 등 뒤에서 위로 · 앞으로 말림 (뼈마다 +x 회전이 쌓임)
    prev = "body"
    segs = [(7, 4, [36, 0, 0]), (11, 4, [38, 0, 0]), (15, 3, [38, 0, 0]), (18, 3, [34, 0, 0])]
    for i, (z0, ln, rot) in enumerate(segs):
        b = m.bone(f"tail{i + 1}", [0, 5, z0], prev, rot)
        w = 3 if i < 2 else 2
        b.box([-w / 2, 3.5 if i < 2 else 4, z0], [w, 3 if i < 2 else 2, ln], (shell if i % 2 == 0 else dark).with_(seed=47 + i))
        prev = b.name
    st = m.bone("stinger", [0, 5, 21], prev, [40, 0, 0])
    st.box([-1.5, 3.5, 21], [3, 3, 3], shell.with_(base="#e0b06a", seed=52))
    st.box([-0.5, 4.5, 24], [1, 1, 3], tip)
    st.box([-0.5, 3.5, 26], [1, 1, 1], venom)
    for side, sx in (("right", -1), ("left", 1)):
        for i, z in enumerate((-5, -2, 1, 4)):
            lg = m.bone(f"{side}Leg{i + 1}", [4 * sx, 3, z])
            ux = -8 if sx < 0 else 4
            lg.box([ux, 3, z], [4, 1, 1], leg.with_(seed=53 + i))
            lg.box([ux if sx < 0 else ux + 3, 0, z], [1, 3, 1], leg.with_(base="#9c6c30", seed=57 + i))
        arm = m.bone(f"{side}Arm", [3 * sx, 3, -7], "body", [0, -18 * sx, 0])
        ax = -5 if sx < 0 else 3
        arm.box([ax, 2, -11], [2, 2, 4], dark.with_(seed=61))
        claw = m.bone(f"{side}Claw", [4 * sx, 3, -11], f"{side}Arm")
        cx = -6 if sx < 0 else 2
        claw.box([cx, 1, -14], [4, 3, 3], shell.with_(seed=62))
        claw.box([cx + (0 if sx < 0 else 2), 3, -17], [2, 1, 3], shell.with_(seed=63), faces={"north": tip})
        pin = m.bone(f"{side}Pincer", [4 * sx, 1.5, -14], f"{side}Claw")
        pin.box([cx + (0 if sx < 0 else 2), 1, -17], [2, 1, 3], dark.with_(seed=64), faces={"north": tip})

    leg_sw = []
    anims_walk = {}
    for side, sx in (("right", -1), ("left", 1)):
        for i in range(4):
            ph = (i % 2) * 180 + (0 if sx < 0 else 90)
            anims_walk[f"{side}Leg{i + 1}"] = {"rotation": [0, f"math.sin({DM} * 90 + {ph}) * 22 * {MS}", f"math.max(0, math.cos({DM} * 90 + {ph})) * 18 * {sx} * {MS}"]}
    del leg_sw
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 120) * 0.2", 0]},
            "tail1": {"rotation": [f"math.sin({LT} * 90) * 3", 0, f"math.sin({LT} * 50) * 4"]},
            "tail3": {"rotation": [f"math.sin({LT} * 90 + 60) * 4", 0, 0]},
            "stinger": {"rotation": [f"math.sin({LT} * 90 + 120) * 6", 0, 0]},
            "rightPincer": {"rotation": [f"math.max(0, math.sin({LT} * 140)) * 18", 0, 0]},
            "leftPincer": {"rotation": [f"math.max(0, math.sin({LT} * 140 + 90)) * 18", 0, 0]},
            "rightArm": {"rotation": [0, f"math.sin({LT} * 60) * 5", 0]},
            "leftArm": {"rotation": [0, f"-math.sin({LT} * 60) * 5", 0]},
        }},
        "walk": {"loop": True, "bones": anims_walk},
        # 꼬리 독침: 0.25 더 말았다가 0.45 (windup 9틱) 앞으로 내리꽂음, 집게도 벌림
        "attack": {"animation_length": 0.7, "bones": {
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.25, [14, 0, 0]), (0.45, [-34, 0, 0]), (0.55, [-30, 0, 0]), (0.7, [0, 0, 0])])},
            "tail3": {"rotation": kf([(0, [0, 0, 0]), (0.25, [10, 0, 0]), (0.45, [-12, 0, 0]), (0.7, [0, 0, 0])])},
            "stinger": {"rotation": kf([(0, [0, 0, 0]), (0.25, [12, 0, 0]), (0.45, [38, 0, 0]), (0.7, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-5, 0, 0]), (0.45, [6, 0, 0]), (0.7, [0, 0, 0])])},
            "rightPincer": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0]), (0.45, [0, 0, 0])])},
            "leftPincer": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0]), (0.45, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 집게 게

def crab():
    m = Model("crab")
    shell = Mat("#d14e34", "plates", noise=6, band=3, accent="#97301f", seed=71)
    top = Mat("#b8402a", "bristle", noise=7, accent="#7d2618", seed=72)
    under = Mat("#efd2a8", "flat", noise=5, seed=73)
    claw = Mat("#d8573a", "plates", noise=6, band=4, accent="#9a3420", seed=74)
    tipm = Mat("#2c2222", "flat", noise=4, seed=75)
    stalk = Mat("#e8c89a", "flat", noise=4, seed=76)
    eyem = Mat("#1a1414", "flat", noise=3, edge=1, seed=77)
    leg = Mat("#c4482f", "flat", noise=6, seed=78)
    glow = (255, 230, 90)

    body = m.bone("body", [0, 5, 0])
    body.box([-7, 4, -5], [14, 4, 10], shell, faces={"down": under})
    body.box([-5, 8, -3], [10, 1, 6], top)
    body.box([-6, 3, -4], [12, 1, 8], under)
    body.box([-3, 4.5, -6], [6, 2, 1], under.with_(base="#e8bfa0", seed=79), decals=[("north", 1, 0, "#7a3a2a"), ("north", 4, 0, "#7a3a2a")])
    for sx in (-1, 1):
        name = "rightEye" if sx < 0 else "leftEye"
        e = m.bone(name, [2 * sx, 8, -4], "body", [0, 0, -10 * sx])
        x0 = -2.5 if sx < 0 else 1.5
        e.box([x0, 8, -4.5], [1, 3, 1], stalk)
        e.box([x0 - 0.5, 11, -5], [2, 2, 2], eyem, decals=[("north", 0, 0, glow, 0.8), ("north", 1, 0, glow, 0.6)])
    # 큰 집게 (오른쪽) · 작은 집게 (왼쪽)
    for sx, big in ((-1, True), (1, False)):
        side = "right" if sx < 0 else "left"
        arm = m.bone(f"{side}Arm", [6 * sx, 6, -4], "body", [0, 12 * sx, 0])
        ax = -9 if sx < 0 else 6
        arm.box([ax, 5, -8], [3, 3, 4], claw.with_(seed=80))
        hand = m.bone(f"{side}Hand", [7.5 * sx, 6, -8], f"{side}Arm", [0, 10 * sx, 0])
        if big:
            hand.box([-10, 4, -14], [5, 5, 6], claw.with_(seed=81))
            hand.box([-10, 7, -18], [3, 2, 4], claw.with_(seed=82), faces={"north": tipm})
            fing = m.bone(f"{side}Finger", [-8.5, 5, -14], f"{side}Hand")
            fing.box([-10, 4, -18], [3, 2, 4], claw.with_(base="#c44a30", seed=83), faces={"north": tipm})
        else:
            hand.box([6, 4.5, -12], [4, 4, 4], claw.with_(seed=84))
            hand.box([8, 7, -15], [2, 1, 3], claw.with_(seed=85), faces={"north": tipm})
            fing = m.bone(f"{side}Finger", [8, 5, -12], f"{side}Hand")
            fing.box([8, 4.5, -15], [2, 1, 3], claw.with_(base="#c44a30", seed=86), faces={"north": tipm})
        for i, z in enumerate((-2, 1, 4)):
            lg = m.bone(f"{side}Leg{i + 1}", [7 * sx, 5, z])
            ux = -11 if sx < 0 else 7
            lg.box([ux, 4, z], [4, 1, 1], leg.with_(seed=87 + i))
            lg.box([ux if sx < 0 else ux + 3, 0, z], [1, 4, 1], leg.with_(base="#a83c26", seed=90 + i))

    walk = {}
    for side, sx in (("right", -1), ("left", 1)):
        for i in range(3):
            ph = (i % 2) * 180 + (0 if sx < 0 else 90)
            walk[f"{side}Leg{i + 1}"] = {"rotation": [0, f"math.sin({DM} * 100 + {ph}) * 20 * {MS}", f"math.max(0, math.cos({DM} * 100 + {ph})) * 20 * {sx} * {MS}"]}
    walk["body"] = {"rotation": [0, 0, f"math.sin({DM} * 100) * 3 * {walk_amt()}"]}
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 90) * 0.25", 0]},
            "rightFinger": {"rotation": [f"math.max(0, math.sin({LT} * 120)) * 22", 0, 0]},
            "leftFinger": {"rotation": [f"math.max(0, math.sin({LT} * 120 + 120)) * 22", 0, 0]},
            "rightEye": {"rotation": [0, 0, f"math.sin({LT} * 70) * 8"]},
            "leftEye": {"rotation": [0, 0, f"math.sin({LT} * 70 + 50) * 8"]},
            "rightArm": {"rotation": [f"math.sin({LT} * 50) * 4", 0, 0]},
        }},
        "walk": {"loop": True, "bones": walk},
        # 큰 집게를 들어 (0.25) 내려 집음 (0.5 = windup 10틱)
        "attack": {"animation_length": 0.75, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-45, 0, 10]), (0.5, [22, 0, 0]), (0.75, [0, 0, 0])])},
            "rightFinger": {"rotation": kf([(0, [0, 0, 0]), (0.25, [38, 0, 0]), (0.45, [38, 0, 0]), (0.5, [-4, 0, 0]), (0.75, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-15, 0, 0]), (0.5, [10, 0, 0]), (0.75, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-5, 0, 0]), (0.5, [6, 0, 0]), (0.75, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 웜 유충

BURY = -22.5  # 숨은 자세: 몸 전체를 땅속으로 (머리 꼭대기 1.5칸만 땅 위 — 모래 둔덕)


def worm_larva():
    m = Model("worm_larva")
    seg = Mat("#e2cc94", "chitin", noise=6, band=3, seed=101)
    seg2 = Mat("#d4b97e", "chitin", noise=6, band=3, seed=102)
    back = Mat("#9c8050", "flat", noise=6, seed=103)
    jaw = Mat("#4a3020", "flat", noise=4, seed=104)
    maw_dark = (70, 22, 22)
    teeth = (246, 240, 226)
    inner = (255, 150, 60)

    base = m.bone("base", [0, 0, 0])
    base.box([-3.5, 0, -1], [7, 3, 8], seg2)
    base.box([-2.5, 0, 7], [5, 2, 4], seg.with_(seed=105))
    s1 = m.bone("seg1", [0, 3, 0], "base")
    s1.box([-4, 3, -4], [8, 5, 8], seg)
    s1.box([-0.5, 6, 4], [1, 2, 1], back)
    s2 = m.bone("seg2", [0, 8, 0], "seg1", [7, 0, 0])
    s2.box([-3.5, 8, -3.5], [7, 5, 7], seg2.with_(seed=106))
    s2.box([-0.5, 11, 3.5], [1, 2, 1], back)
    s3 = m.bone("seg3", [0, 13, 0], "seg2", [7, 0, 0])
    s3.box([-3, 13, -3], [6, 5, 6], seg.with_(seed=107))
    s3.box([-0.5, 16, 3], [1, 2, 1], back)
    head = m.bone("head", [0, 18, 0], "seg3", [-12, 0, 0])
    maw = []
    for (x, y) in ((2, 2), (3, 2), (4, 2), (5, 2), (2, 3), (5, 3), (2, 4), (3, 4), (4, 4), (5, 4)):
        maw.append(("north", x, y, maw_dark))
    for (x, y) in ((3, 3), (4, 3)):
        maw.append(("north", x, y, inner, 0.8))
    for (x, y) in ((1, 1), (3, 1), (4, 1), (6, 1), (1, 3), (6, 3), (1, 5), (3, 5), (4, 5), (6, 5)):
        maw.append(("north", x, y, teeth))
    head.box([-4, 18, -4], [8, 6, 8], seg.with_(base="#e8d4a0", seed=108), decals=maw)
    head.box([-3, 24, -3], [6, 1, 6], seg2.with_(seed=109))
    for name, pos, size in (("jawTop", [-1, 23, -6], [2, 1, 2]), ("jawBottom", [-1, 17, -6], [2, 1, 2]), ("jawRight", [-5, 20, -6], [1, 2, 2]),
                            ("jawLeft", [4, 20, -6], [1, 2, 2])):
        j = m.bone(name, [pos[0] + size[0] / 2, pos[1] + size[1] / 2, -4], "head")
        j.box(pos, size, jaw)

    def sway(i):
        return {"rotation": [f"math.sin({LT} * 80 + {i * 40}) * 3", 0, f"math.sin({LT} * 60 + {i * 45}) * {3 + i}"]}

    anims = {
        "idle": {"loop": True, "bones": {
            "seg1": sway(0), "seg2": sway(1), "seg3": sway(2),
            "head": {"rotation": [f"{LOOK_X} * 0.4 + math.sin({LT} * 80 + 160) * 4", f"{LOOK_Y} * 0.5", f"math.sin({LT} * 60 + 180) * 5"]},
            "jawTop": {"rotation": [f"-math.max(0, math.sin({LT} * 150)) * 20", 0, 0]},
            "jawBottom": {"rotation": [f"math.max(0, math.sin({LT} * 150)) * 20", 0, 0]},
            "jawRight": {"rotation": [0, f"math.max(0, math.sin({LT} * 150)) * 20", 0]},
            "jawLeft": {"rotation": [0, f"-math.max(0, math.sin({LT} * 150)) * 20", 0]},
        }},
        "walk": {"loop": True, "bones": {
            "seg1": {"rotation": [f"math.sin({DM} * 60) * 7 * {MS}", 0, 0]},
            "seg2": {"rotation": [f"math.sin({DM} * 60 + 70) * 7 * {MS}", 0, 0]},
            "seg3": {"rotation": [f"math.sin({DM} * 60 + 140) * 7 * {MS}", 0, 0]},
            "base": {"rotation": [0, f"math.sin({DM} * 60) * 6 * {walk_amt()}", 0]},
        }},
        # 뒤로 젖혔다가 (0.25) 앞으로 물어뜯음 (0.45 = windup 9틱)
        "attack": {"animation_length": 0.7, "bones": {
            "seg2": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-22, 0, 0]), (0.45, [30, 0, 0]), (0.7, [0, 0, 0])])},
            "seg3": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-14, 0, 0]), (0.45, [22, 0, 0]), (0.7, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-12, 0, 0]), (0.45, [16, 0, 0]), (0.7, [0, 0, 0])])},
            "jawTop": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-40, 0, 0]), (0.45, [0, 0, 0])])},
            "jawBottom": {"rotation": kf([(0, [0, 0, 0]), (0.3, [40, 0, 0]), (0.45, [0, 0, 0])])},
            "jawRight": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 40, 0]), (0.45, [0, 0, 0])])},
            "jawLeft": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, -40, 0]), (0.45, [0, 0, 0])])},
        }},
        # 숨기 (땅속으로 — 끝 자세 유지): Monsters 의 burrow 가 "burrow" 컨트롤러에 멈춤 식 "0" 으로 틂
        "buried": {"animation_length": 0.6, "loop": "hold_on_last_frame", "bones": {
            "base": {"position": kf([(0, [0, 0, 0]), (0.6, [0, BURY, 0])]), "rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 8]), (0.6, [0, 0, 0])])},
            "seg2": {"rotation": kf([(0, [0, 0, 0]), (0.3, [12, 0, 0]), (0.6, [0, 0, 0])])},
        }},
        # 튀어나오기: 땅속에서 솟구쳐 (조금 넘쳤다가) 섬, 턱을 벌림
        "emerge": {"animation_length": 0.6, "bones": {
            "base": {"position": kf([(0, [0, BURY, 0]), (0.3, [0, 3, 0]), (0.45, [0, -1, 0]), (0.6, [0, 0, 0])])},
            "seg2": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-18, 0, 0]), (0.6, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-20, 0, 0]), (0.6, [0, 0, 0])])},
            "jawTop": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-45, 0, 0]), (0.6, [0, 0, 0])])},
            "jawBottom": {"rotation": kf([(0, [0, 0, 0]), (0.3, [45, 0, 0]), (0.6, [0, 0, 0])])},
            "jawRight": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 45, 0]), (0.6, [0, 0, 0])])},
            "jawLeft": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, -45, 0]), (0.6, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 서리 늑대

def frost_wolf():
    m = Model("frost_wolf")
    fur = Mat("#c4d8e6", "fur", noise=8, accent="#8ea6bc", seed=121)
    mane = Mat("#e2edf5", "fur", noise=9, seed=122)
    muzzle = Mat("#7d8ea2", "fur", noise=6, seed=123)
    dark = Mat("#4c5a6c", "flat", noise=5, seed=124)
    ice = Mat("#9edcff", "ice", noise=5, edge=0.9, glow=0.45, seed=125)
    paw = Mat("#a8bccc", "fur", noise=6, seed=126)
    eye = (150, 230, 255)

    body = m.bone("body", [0, 11, 0])
    body.box([-4, 8, -7], [8, 7, 14], fur, faces={"down": fur.with_(base="#dce8f0", seed=127)})
    body.box([-5, 8, -9], [10, 8, 6], mane)
    for (x, z, h) in ((-1, -5, 3), (-1, -1, 4), (-1, 3, 2)):
        body.box([x, 15, z], [2, h, 2], ice.with_(seed=128 + z))
    body.box([-5, 15, -7], [1, 2, 1], ice.with_(seed=131))
    body.box([4, 15, -7], [1, 2, 1], ice.with_(seed=132))
    head = m.bone("head", [0, 13, -8], "body")
    head.box([-3.5, 11, -13], [7, 6, 5], fur.with_(seed=133), decals=[("north", 1, 2, eye, 0.9), ("north", 5, 2, eye, 0.9),
                                                                    ("north", 1, 1, "#3c4a5a"), ("north", 5, 1, "#3c4a5a")])
    head.box([-2, 12, -17], [4, 3, 4], muzzle, decals=[("up", 1, 0, "#1c2028"), ("up", 2, 0, "#1c2028")])
    head.box([-1, 14, -18], [2, 1, 1], dark)
    jaw = m.bone("jaw", [0, 12, -13], "head")
    jaw.box([-1.5, 11, -17], [3, 1, 4], muzzle.with_(base="#6e7e90", seed=134), decals=[("up", 0, 0, "#f4f4f0"), ("up", 2, 0, "#f4f4f0")])
    for sx in (-1, 1):
        ear = m.bone("rightEar" if sx < 0 else "leftEar", [2.5 * sx, 17, -10], "head", [0, 0, 10 * sx])
        ex = -3.5 if sx < 0 else 1.5
        ear.box([ex, 17, -11], [2, 3, 1], fur.with_(base="#9fb6c8", seed=135))
    for name, px, pz, top in (("frontRightLeg", -2.5, -5, 8), ("frontLeftLeg", 2.5, -5, 8), ("backRightLeg", -2.5, 5, 9), ("backLeftLeg", 2.5, 5, 9)):
        leg = m.bone(name, [px, top, pz])
        leg.box([px - 1, 1, pz - 1], [2, top - 1, 2], fur.with_(seed=136 + int(pz)))
        leg.box([px - 1, 0, pz - 1.5], [2, 1, 3], paw)
    t1 = m.bone("tail1", [0, 14, 7], "body", [40, 0, 0])
    t1.box([-1.5, 12.5, 7], [3, 3, 5], fur.with_(seed=140))
    t2 = m.bone("tail2", [0, 14, 12], "tail1", [15, 0, 0])
    t2.box([-1, 13, 12], [2, 2, 4], mane.with_(seed=141))
    t2.box([-0.5, 13.5, 16], [1, 1, 1], ice.with_(seed=142))

    gal = f"math.cos({DM} * 50) * 38 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 100) * 0.25", 0]},
            "head": {"rotation": [f"{LOOK_X} * 0.6 + math.sin({LT} * 70) * 2", f"{LOOK_Y} * 0.6", 0]},
            "rightEar": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 130)) * -10"]},
            "leftEar": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 130 + 90)) * 10"]},
            "tail1": {"rotation": [f"math.sin({LT} * 60) * 4", f"math.sin({LT} * 110) * 14", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 45)) * 6", 0, 0]},
        }},
        "walk": {"loop": True, "bones": {
            "frontRightLeg": {"rotation": [gal, 0, 0]},
            "backLeftLeg": {"rotation": [gal, 0, 0]},
            "frontLeftLeg": {"rotation": [f"-{gal}", 0, 0]},
            "backRightLeg": {"rotation": [f"-{gal}", 0, 0]},
            "body": {"position": [0, f"math.abs(math.sin({DM} * 50)) * 0.8 * {walk_amt()}", 0]},
            "tail1": {"rotation": [f"-10 * {walk_amt()}", f"math.sin({DM} * 50) * 18 * {MS}", 0]},
        }},
        # 물기: 고개를 젖히고 입을 벌렸다가 (0.15) 콱 (0.35 = windup 7틱)
        "attack": {"animation_length": 0.55, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-22, 0, 0]), (0.35, [18, 0, 0]), (0.55, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.15, [38, 0, 0]), (0.33, [36, 0, 0]), (0.36, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-6, 0, 0]), (0.35, [6, 0, 0]), (0.55, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.35, [0, 0, -1.5]), (0.55, [0, 0, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.35, [20, 0, 0]), (0.55, [0, 0, 0])])},
            "frontLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.35, [20, 0, 0]), (0.55, [0, 0, 0])])},
        }},
        # 울부짖기 (무리를 부름): 고개를 들고 입을 벌림
        "howl": {"animation_length": 1.2, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-50, 0, 0]), (0.95, [-50, 0, 0]), (1.2, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.25, [28, 0, 0]), (0.95, [28, 0, 0]), (1.2, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-14, 0, 0]), (0.95, [-14, 0, 0]), (1.2, [0, 0, 0])])},
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-25, 0, 0]), (0.95, [-25, 0, 0]), (1.2, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 서리 거인

def frost_giant():
    m = Model("frost_giant")
    skin = Mat("#7896ae", "skin", noise=7, accent="#5a748c", seed=151)
    skin2 = Mat("#6c88a0", "skin", noise=7, accent="#50687e", seed=152)
    fur = Mat("#6e5236", "fur", noise=8, accent="#4a3624", seed=153)
    belt = Mat("#3e3024", "flat", noise=5, seed=154)
    ice = Mat("#a8e2ff", "ice", noise=5, edge=0.9, glow=0.38, seed=155)
    icefist = Mat("#c0ecff", "ice", noise=5, edge=0.88, glow=0.3, seed=156)
    beard = Mat("#d4f0ff", "ice", noise=4, edge=0.9, glow=0.25, seed=157)
    brow = Mat("#4c6278", "flat", noise=5, seed=158)
    eye = (210, 245, 255)

    body = m.bone("body", [0, 14, 0])
    body.box([-7, 14, -4], [14, 8, 8], fur)
    body.box([-7, 20, -4], [14, 1, 8], belt, inflate=0.2, decals=[("north", 6, 0, "#a8b0b8"), ("north", 7, 0, "#a8b0b8")])
    chest = m.bone("chest", [0, 22, 0], "body")
    chest.box([-8, 22, -4.5], [16, 9, 9], skin, decals=[("north", 7, 3, "#5a748c"), ("north", 8, 4, "#5a748c"), ("north", 7, 5, "#5a748c"),
                                                        ("north", 3, 2, "#5a748c"), ("north", 12, 2, "#5a748c")])
    for x, z, w, h, d in ((-9, -2, 3, 4, 3), (6, -2, 3, 4, 3), (-3, 4.5, 2, 5, 2), (1, 4.5, 2, 4, 2), (-6, 4.5, 2, 3, 2), (4, 4.5, 2, 3, 2)):
        chest.box([x, 30 if z < 0 else 25, z], [w, h, d], ice.with_(seed=160 + x))
    head = m.bone("head", [0, 31, -1], "chest")
    head.box([-4.5, 31, -6.5], [9, 9, 8], skin2, decals=[("north", 2, 4, eye, 1.0), ("north", 6, 4, eye, 1.0), ("north", 1, 4, eye, 0.5), ("north", 7, 4, eye, 0.5),
                                                         ("north", 4, 6, "#3a4a5a"), ("north", 3, 7, "#3a4a5a"), ("north", 5, 7, "#3a4a5a")])
    head.box([-5, 35, -7], [10, 1, 2], brow)
    head.box([-4, 27, -7], [8, 5, 2], beard)
    for x, y, h in ((-3, 25, 2), (-1, 24, 3), (1, 25, 2), (2.5, 26, 1)):
        head.box([x, y, -6.5], [1, h, 1], beard.with_(seed=170 + y))
    for x, h in ((-4, 3), (3, 3), (-1, 2)):
        head.box([x, 39, -4 if x != -1 else -3], [1 if x != -1 else 2, h, 1 if x != -1 else 2], ice.with_(seed=175 + x))
    for sx in (-1, 1):
        side = "right" if sx < 0 else "left"
        arm = m.bone(f"{side}Arm", [10 * sx, 29, 0], "chest")
        ax = -12 if sx < 0 else 8
        arm.box([ax, 19, -2.5], [4, 11, 5], skin.with_(seed=180 + sx))
        arm.box([ax - 0.5, 12, -3], [5, 7, 6], icefist.with_(seed=182 + sx))
        leg = m.bone(f"{side}Leg", [3.5 * sx, 14, 0])
        lx = -6 if sx < 0 else 1
        leg.box([lx, 4, -2.5], [5, 10, 5], skin2.with_(seed=184 + sx))
        leg.box([lx - 0.5, 0, -3], [6, 4, 6], fur.with_(seed=186 + sx))

    anims = {
        "idle": {"loop": True, "bones": {
            "chest": {"rotation": [f"math.sin({LT} * 60) * 2", 0, 0]},
            "head": {"rotation": [f"{LOOK_X} * 0.5", f"{LOOK_Y} * 0.5", 0]},
            "rightArm": {"rotation": [f"math.sin({LT} * 60) * 3", 0, f"4 + math.sin({LT} * 60) * 2"]},
            "leftArm": {"rotation": [f"math.sin({LT} * 60 + 40) * 3", 0, f"-4 - math.sin({LT} * 60) * 2"]},
        }},
        "walk": {"loop": True, "bones": {
            "rightLeg": {"rotation": [f"math.cos({DM} * 28) * 26 * {MS}", 0, 0]},
            "leftLeg": {"rotation": [f"-math.cos({DM} * 28) * 26 * {MS}", 0, 0]},
            "rightArm": {"rotation": [f"-math.cos({DM} * 28) * 20 * {MS}", 0, 0]},
            "leftArm": {"rotation": [f"math.cos({DM} * 28) * 20 * {MS}", 0, 0]},
            "body": {"rotation": [0, 0, f"math.cos({DM} * 28) * 3 * {walk_amt()}"], "position": [0, f"math.abs(math.sin({DM} * 28)) * 1 * {walk_amt()}", 0]},
        }},
        # 두 주먹을 머리 위로 (0.4) → 내려침 (0.6 = windup 12틱)
        "attack": {"animation_length": 0.95, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-165, 0, -12]), (0.6, [-18, 0, 0]), (0.72, [6, 0, 0]), (0.95, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-165, 0, 12]), (0.6, [-18, 0, 0]), (0.72, [6, 0, 0]), (0.95, [0, 0, 0])])},
            "chest": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (0.6, [24, 0, 0]), (0.95, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.6, [10, 0, 0]), (0.95, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.6, [0, -1.5, 0]), (0.95, [0, 0, 0])])},
        }},
    }
    return m, anims


MAKERS = {"nisse": nisse, "boar": boar, "sand_scorpion": sand_scorpion, "crab": crab, "worm_larva": worm_larva, "frost_wolf": frost_wolf,
          "frost_giant": frost_giant}


def grendel_copy():
    """그렌델: 클로드 디자인 원본 (애드온 art/monsters/grendel) — ID 만 rpg 로"""
    src = os.path.join(ADDON, "art", "monsters", "grendel")
    dst = os.path.join(ART, "grendel")
    if not os.path.isdir(src):
        if os.path.isdir(dst):
            return
        raise SystemExit(f"그렌델 원본이 없음: {src}")
    os.makedirs(dst, exist_ok=True)
    geo = json.load(open(os.path.join(src, "grendel.geo.json"), encoding="utf-8"))
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.rpg.monster.grendel"
    write_json(os.path.join(dst, "grendel.geo.json"), geo)
    a = json.load(open(os.path.join(src, "grendel.animation.json"), encoding="utf-8"))
    out = {"format_version": a.get("format_version", "1.8.0"), "animations": {}}
    for k, v in a["animations"].items():
        out["animations"][k.replace("animation.fw.grendel.", "animation.rpg.monster.grendel.")] = v
    write_json(os.path.join(dst, "grendel.animation.json"), out)
    shutil.copyfile(os.path.join(src, "grendel.png"), os.path.join(dst, "grendel.png"))
    with open(os.path.join(dst, "README.md"), "w", encoding="utf-8") as f:
        f.write("그렌델: 클로드 디자인 FantasyMonsters 팩의 원본 (애드온 art/monsters/grendel) — 모델 ID · 애니메이션 ID 만 rpg 로 바꿈.\n")


def build(keys=None):
    out = {}
    for key, fn in MAKERS.items():
        if keys and key not in keys:
            continue
        m, anims = fn()
        m.pack()
        tex = m.paint()
        d = os.path.join(ART, key)
        os.makedirs(d, exist_ok=True)
        write_json(os.path.join(d, f"{key}.geo.json"), m.geo_json())
        tex.save(os.path.join(d, f"{key}.png"))
        write_json(os.path.join(d, f"{key}.animation.json"), anim_file(key, anims))
        out[key] = (m, anims, tex)
    grendel_copy()
    return out


def load_art(key):
    d = os.path.join(ART, key)
    geo = json.load(open(os.path.join(d, f"{key}.geo.json"), encoding="utf-8"))
    tex = Image.open(os.path.join(d, f"{key}.png")).convert("RGBA")
    a = json.load(open(os.path.join(d, f"{key}.animation.json"), encoding="utf-8"))["animations"]
    amap = {k.split(".")[-1]: v for k, v in a.items()}
    return geo, tex, amap


def main():
    args = sys.argv[1:]
    keys = None
    if "--only" in args:
        keys = args[args.index("--only") + 1].split(",")
    build(keys)
    if "--preview" in args:
        out = args[args.index("--preview") + 1]
        more = json.load(open(MORE, encoding="utf-8"))["MONSTERS"]
        pose = args[args.index("--pose") + 1] if "--pose" in args else "idle"
        t = float(args[args.index("--t") + 1]) if "--t" in args else None
        entries = []
        for key in more:
            if key.startswith("_") or (keys and key not in keys):
                continue
            mc = more[key]
            geo, tex, amap = load_art(key)
            entries.append((key, mc["name"], geo, tex, amap, mc["scale"], (mc["box"][0] * mc["scale"], mc["box"][1] * mc["scale"]), mc["level"]))
        poses = [(pose, t)] if pose != "idle" else [("idle", None)]
        if "--close" in args:
            closeup(entries, out, poses, walk="--walk" in args)
        else:
            preview(entries, out, poses, walk="--walk" in args)
        print("preview", out)
    print("monster art:", ", ".join(sorted(os.listdir(ART))))


if __name__ == "__main__":
    main()
