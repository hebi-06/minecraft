#!/usr/bin/env python3
"""2026-11-12 직업 스킬의 3D 몹 (사용자 직업 도감 메모 — 드라군 "용을 3d 모델링으로 하고 싶음" · "용 모델을 3d로" · "용을 3d모델로 하면 좋을거 같음").
룬 스킬의 3D 몹 (tools/rune_mobs_art.py) 과 같은 길: tools/monster_kit.py 로 모델 · 텍스처 · 동작을 짜고 그 파일의 build · install 로
행동 (mmorpg/entities.json — allies 무리) · 그리기 (assets/mmorpg/bedrock/entities.json) · 텍스처 (assets/mmorpg/textures/entity/rpg/allies) 에 넣음.
모드 없는 클라이언트의 뼈 모델은 tools/vanilla_models.py 를 다시 돌려 만듦 (뼈마다 아이템 표시). 움직임은 rune.RuneMob 을 쓰는 스킬 (job.DragonSpirit).

  python3 tools/job_mobs_art.py                       # 모두 쓰고 게임 자원에 넣음
  python3 tools/job_mobs_art.py --preview 폴더 [키…]   # 확인용 그림 (자세마다)

몹 (키 → 엔티티 rpg:<키>):
- job_dragon  드라군의 붉은 용 (하이 점프 · 임페일 · 드래곤 브레스 · 드래곤폴): 진홍 비늘 · 금빛 배 · 불씨처럼 빛나는 비늘 틈과 날개 막 끝 · 흑요석 뿔 (끝이 달아오름) ·
              목구멍의 불빛. 날개 끝 사이 약 5.4칸 (크기 1). 늘 트는 동작 idle (숨 · 목 · 꼬리 — 작게), 스킬이 트는 동작 fly (날갯짓 — 이어 돎) · summon (불티에서
              커지며 날개를 폄) · roar (고개를 젖혀 포효) · inhale (숨을 들이켬) · breathe (목을 내밀어 불을 토함) · dive (머리를 아래로 날개를 접고 내리꽂음) ·
              crash (땅에 닿으며 날개를 활짝) · vanish (불티로 흩어짐).
              2026-11-13 드라군 변신 (사람 대신 용이 스킬을 함 — job.DragonForm): launch (웅크렸다 날개를 내려치며 뛰어오름) · spin (꼬리로 한 바퀴 휩쓸기) ·
              bite (목을 내뻗어 묾) · rear (문 채로 뒷발로 일어섬) · slam (일어선 자세에서 앞으로 내리꽂음) · stomp (앞을 들었다 온몸으로 짓밟기)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rune_mobs_art as R  # noqa: E402
from monster_kit import Mat, Model, kf  # noqa: E402

LT = R.LT
see = R.see

# ================================================================== 드라군의 붉은 용

DR_RED = (168, 30, 24)
DR_RED_DARK = (104, 16, 14)
DR_BELLY = (242, 164, 74)
DR_MEMB = (150, 28, 22)
DR_EMBER = (255, 150, 46)
DR_FIRE = (255, 206, 96)
DR_HORN = (44, 24, 22)
DR_IVORY = (238, 220, 180)
DR_EYE = (255, 236, 120)


def job_dragon():
    m = Model("job_dragon", tex_w=128)
    scale = Mat(DR_RED, "scales", noise=8, edge=0.8, accent=DR_RED_DARK, seed=701, veins=1, vein=DR_EMBER, vein_glow=0.6)
    back = Mat(DR_RED_DARK, "scales", noise=7, edge=0.78, accent=(70, 10, 10), seed=702, veins=1, vein=DR_EMBER, vein_glow=0.8)
    belly = Mat(DR_BELLY, "plates", noise=5, edge=0.9, band=3, accent=(214, 124, 50), seed=703)
    memb = Mat(DR_MEMB, "skin", noise=7, edge=0.92, accent=(96, 14, 12), seed=704, seam=(255, 110, 36), seam_glow=0.5, seam_band=4, seam_dir="v",
               seam_gap=0.55)
    spar = Mat((86, 16, 14), "scales", noise=5, edge=0.82, accent=(54, 8, 8), seed=705)
    horn = Mat(DR_HORN, "bone", noise=5, edge=0.85, accent=(20, 10, 10), seed=706, rim=DR_EMBER, rim_glow=0.55)
    spike = Mat(DR_HORN, "bone", noise=4, edge=0.85, seed=707)
    ivory = Mat(DR_IVORY, "bone", noise=4, edge=0.88, seed=708)
    mouth = Mat((255, 120, 36), "flat", noise=10, edge=0.9, glow=0.9, seed=709)
    ember = Mat(DR_EMBER, "flat", noise=12, edge=0.9, glow=0.95, seed=710)
    fire = Mat(DR_FIRE, "flat", noise=10, edge=1.0, glow=1.0, seed=711)

    m.bone("root", [0, 20, 0])
    body = m.bone("body", [0, 20, 0], "root")
    # 몸통 (앞 −z): 가슴 (앞판 금빛) · 배 · 어깨 (날개 뿌리) · 등의 가시 (불씨 끝)
    body.box([-7, 13, -13], [14, 13, 13], scale, faces={"down": belly, "up": back})
    body.box([-6, 13.5, 0], [12, 11, 11], scale.with_(seed=713), faces={"down": belly.with_(seed=714), "up": back.with_(seed=715)})
    body.box([-8, 21.5, -12], [16, 5, 9], back.with_(seed=716))
    for i, (z, h) in enumerate(((-11, 4), (-6, 4), (-1, 3), (4, 3), (8, 2))):
        body.box([-1, 26 - (1 if i > 2 else 0), z], [2, h, 3], spike.with_(seed=717 + i), faces={"up": ember})
    # 목 (셋) · 머리 · 턱 — 쉴 때 목이 앞 위로 솟고 머리는 거의 수평
    n1 = m.bone("neck1", [0, 22, -12], "body", rotation=[-38, 0, 0])
    n1.box([-4, 18.5, -21], [8, 8, 10], scale.with_(seed=720), faces={"down": belly.with_(seed=721), "up": back.with_(seed=722)})
    n1.box([-1, 26.5, -18], [2, 2, 3], spike.with_(seed=723), faces={"up": ember})
    n2 = m.bone("neck2", [0, 22.5, -20], "neck1", rotation=[-10, 0, 0])
    n2.box([-3.5, 19, -28], [7, 7, 9], scale.with_(seed=724), faces={"down": belly.with_(seed=725), "up": back.with_(seed=726)})
    n2.box([-1, 26, -26], [2, 2, 3], spike.with_(seed=727), faces={"up": ember})
    head = m.bone("head", [0, 23, -27], "neck2", rotation=[43, 0, 0])
    head.box([-4.5, 19.5, -36], [9, 7, 10], scale.with_(seed=730), faces={"up": back.with_(seed=731), "down": belly.with_(seed=732)}, decals=[
        ("west", 1, 2, DR_EYE, 1.0), ("west", 2, 2, DR_EYE, 1.0), ("east", 7, 2, DR_EYE, 1.0), ("east", 8, 2, DR_EYE, 1.0),
        ("west", 1, 3, (60, 10, 0)), ("east", 8, 3, (60, 10, 0))])
    head.box([-5, 25.5, -35], [10, 2, 4], back.with_(seed=733))  # 눈 위 볏
    head.box([-3, 19.5, -44], [6, 5, 8], scale.with_(seed=734), faces={"up": back.with_(seed=735), "down": mouth}, decals=[
        ("north", 1, 1, (30, 6, 4)), ("north", 4, 1, (30, 6, 4)), ("north", 1, 2, DR_EMBER, 0.6), ("north", 4, 2, DR_EMBER, 0.6)])
    head.box([-3, 18.5, -44], [1, 1, 7], ivory)  # 윗니 (오른쪽 · 왼쪽)
    head.box([2, 18.5, -44], [1, 1, 7], ivory.with_(seed=736))
    head.box([-1, 24.5, -43], [2, 2, 3], spike.with_(seed=737), faces={"up": ember})  # 콧등 뿔
    jaw = m.bone("jaw", [0, 21, -34], "head", rotation=[8, 0, 0])
    jaw.box([-3, 17.5, -44], [6, 3, 10], scale.with_(seed=740), faces={"up": mouth.with_(seed=741), "down": belly.with_(seed=742)})
    jaw.box([-3, 20.5, -44], [1, 1, 6], ivory.with_(seed=743))  # 아랫니
    jaw.box([2, 20.5, -44], [1, 1, 6], ivory.with_(seed=744))
    for side, sx in (("R", -1), ("L", 1)):
        hb = m.bone("horn" + side, [3 * sx, 25.5, -30], "head", rotation=[28, 16 * sx, 0])
        x0 = 3 * sx - 1
        hb.box([x0, 24.5, -30], [2, 2, 8], horn.with_(seed=750 + sx))
        hb.box([x0 + 0.5, 25, -22], [1, 1, 4], spike.with_(seed=752 + sx), faces={"up": ember})
        hb.box([x0 - 0.5 * sx + (0 if sx < 0 else 0), 25, -32], [2, 1, 2], horn.with_(seed=754 + sx))  # 뿔 뿌리 · 뺨 가시
    # 날개 (오른쪽 = −x): 위팔 (자루 · 안쪽 막) · 아래팔 (자루 · 손가락 셋 · 막 넷 — 뒤 끝이 물결) · 엄지 발톱
    for side, sx in (("R", -1), ("L", 1)):
        w1 = m.bone("wing" + side, [6 * sx, 24, -8], "body", rotation=[0, 10 * sx, 20 * -sx])
        w1.box([-22 if sx < 0 else 6, 23.5, -9.5], [16, 3, 3], spar.with_(seed=760 + sx))
        w1.box([-22 if sx < 0 else 6, 23.6, -7], [16, 1, 16], memb.with_(seed=762 + sx))
        w1.box([-22 if sx < 0 else 6, 23.6, 9], [16, 1, 1], ember.with_(seed=763 + sx))  # 막 뒤 끝 (불씨)
        w2 = m.bone("wing" + side + "2", [22 * sx, 24, -8], "wing" + side, rotation=[0, 0, 14 * sx])
        w2.box([-42 if sx < 0 else 22, 24, -9], [20, 2, 2], spar.with_(seed=764 + sx))
        for i, (x, ln) in enumerate(((27, 15), (33, 13), (39, 10))):
            w2.box([(-x - 0.5) if sx < 0 else (x - 0.5), 24.4, -7], [1, 1, ln], spar.with_(seed=766 + i + sx))
        for i, (xa, xb, d) in enumerate(((22, 28, 16), (28, 34, 13), (34, 40, 9), (40, 44, 5))):
            w2.box([-xb if sx < 0 else xa, 23.7, -7], [xb - xa, 1, d - 1], memb.with_(seed=770 + i + sx))
            w2.box([-xb if sx < 0 else xa, 23.7, -8 + d], [xb - xa, 1, 1], ember.with_(seed=774 + i + sx))
        w2.box([(-24.5) if sx < 0 else 22.5, 24.5, -12], [2, 2, 3], ivory.with_(seed=776 + sx))  # 엄지 발톱
        w2.box([(-46) if sx < 0 else 43, 24.2, -9], [3, 1, 1], spike.with_(seed=778 + sx), faces={"up": ember})  # 날개 끝 가시
    # 꼬리 (넷 · 끝 — 불꽃 지느러미)
    prev = "body"
    for i, (z, w, h) in enumerate(((10, 8, 7), (18.5, 6, 6), (27, 4, 5), (35, 3, 4))):
        t = m.bone("tail%d" % (i + 1), [0, 19, z], prev)
        t.box([-w / 2, 19 - h / 2, z], [w, h, 9], scale.with_(seed=780 + i), faces={"down": belly.with_(seed=784 + i), "up": back.with_(seed=788 + i)})
        if i < 3:
            t.box([-1, 19 + h / 2, z + 2], [2, 2 if i < 2 else 1, 3], spike.with_(seed=792 + i), faces={"up": ember})
        prev = t.name
    tip = m.bone("tailTip", [0, 19, 43.5], prev)
    tip.box([-4, 18.5, 43.5], [8, 1, 4], ember)
    tip.box([-3, 18.5, 47.5], [6, 1, 2], fire)
    tip.box([-1.5, 18.5, 49.5], [3, 1, 2], fire.with_(seed=796))
    tip.box([-0.5, 19.5, 44], [1, 2, 4], spike.with_(seed=797), faces={"up": ember})
    # 다리 (날 때 접음): 앞발 · 뒷발 (발톱 상아)
    for side, sx in (("R", -1), ("L", 1)):
        lf = m.bone("legF" + side, [5 * sx, 16, -8], "body", rotation=[35, 0, 0])
        lf.box([5 * sx - 2, 9, -10], [4, 8, 4], scale.with_(seed=800 + sx))
        lf.box([5 * sx - 2.5, 7, -11], [5, 2, 5], spar.with_(seed=802 + sx), decals=[("north", 0, 1, DR_IVORY), ("north", 2, 1, DR_IVORY), ("north", 4, 1, DR_IVORY)])
        lb = m.bone("legB" + side, [5 * sx, 16, 6], "body", rotation=[50, 0, 0])
        lb.box([5 * sx - 2.5, 8, 3], [5, 9, 6], scale.with_(seed=804 + sx))
        lb.box([5 * sx - 3, 6, 1], [6, 2, 7], spar.with_(seed=806 + sx), decals=[("north", 0, 1, DR_IVORY), ("north", 2, 1, DR_IVORY), ("north", 5, 1, DR_IVORY)])
    return m


def job_dragon_anims():
    flap = f"math.sin({LT} * 600)"          # 0.6초에 한 번
    flap2 = f"math.sin({LT} * 600 - 60)"    # 바깥 날개는 늦게
    tail = lambda ph, amp: f"math.sin({LT} * 160 - {ph}) * {amp}"  # noqa: E731
    still = {
        # 늘 (작게): 숨 · 목 · 머리 · 꼬리 물결
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 120) * 0.5", 0]},
            "neck1": {"rotation": [f"math.sin({LT} * 120 + 40) * 3", f"math.sin({LT} * 70) * 4", 0]},
            "head": {"rotation": [f"math.sin({LT} * 120 + 80) * 3", f"math.sin({LT} * 70 + 40) * 5", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 90)) * 4", 0, 0]},
            "tail1": {"rotation": [0, tail(0, 6), 0]},
            "tail2": {"rotation": [0, tail(40, 8), 0]},
            "tail3": {"rotation": [0, tail(80, 10), 0]},
            "tail4": {"rotation": [0, tail(120, 12), 0]},
            "tailTip": {"rotation": [0, tail(160, 14), 0]},
        }},
    }
    run = {
        # 날갯짓 (이어 돎): 위팔 ±34° · 아래팔이 늦게 · 몸이 거꾸로 위아래 · 꼬리 위아래
        "fly": {"loop": True, "bones": {
            "body": {"position": [0, f"{flap} * -1.4", 0], "rotation": [f"{flap} * 3", 0, 0]},
            "wingR": {"rotation": [0, 0, f"{flap} * 34"]},
            "wingL": {"rotation": [0, 0, f"{flap} * -34"]},
            "wingR2": {"rotation": [0, 0, f"{flap2} * 24"]},
            "wingL2": {"rotation": [0, 0, f"{flap2} * -24"]},
            "neck1": {"rotation": [f"{flap} * -4", 0, 0]},
            "head": {"rotation": [f"{flap} * 4", 0, 0]},
            "tail1": {"rotation": [f"{flap2} * 5", 0, 0]},
            "tail2": {"rotation": [f"{flap2} * 6", 0, 0]},
            "tail3": {"rotation": [f"{flap2} * 7", 0, 0]},
        }},
        # 생겨남 (0.35초): 작게 웅크린 불티에서 커지며 (1.08 → 1) 접은 날개를 펴고 고개를 듦 (날개 — 오른쪽: y + = 뒤로 · z + = 위로, 왼쪽은 거울)
        "summon": {"animation_length": 0.35, "bones": {
            "root": {"scale": kf([(0, [0.15, 0.15, 0.15]), (0.22, [1.08, 1.08, 1.08]), (0.35, [1, 1, 1])])},
            "wingR": {"rotation": kf([(0, [0, 70, -40]), (0.25, [0, 0, 10]), (0.35, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, -70, 40]), (0.25, [0, 0, -10]), (0.35, [0, 0, 0])])},
            "wingR2": {"rotation": kf([(0, [0, 0, -80]), (0.28, [0, 0, 6]), (0.35, [0, 0, 0])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 80]), (0.28, [0, 0, -6]), (0.35, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [30, 0, 0]), (0.25, [-8, 0, 0]), (0.35, [0, 0, 0])])},
        }},
        # 포효 (0.6초 — 끝 자세를 지킴): 고개를 젖혀 입을 크게 · 날개를 위 앞으로 활짝
        "roar": {"animation_length": 0.6, "bones": {
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.15, [6, 0, 0]), (0.35, [-16, 0, 0]), (0.6, [-14, 0, 0])])},
            "neck2": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-8, 0, 0]), (0.6, [-8, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [10, 0, 0]), (0.35, [-26, 0, 0]), (0.6, [-24, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.15, [4, 0, 0]), (0.3, [38, 0, 0]), (0.6, [34, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, -10, 30]), (0.6, [0, -8, 26])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 10, -30]), (0.6, [0, 8, -26])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 16]), (0.6, [0, 0, 12])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, -16]), (0.6, [0, 0, -12])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-8, 0, 0]), (0.6, [-6, 0, 0])])},
        }},
        # 숨을 들이켬 (0.4초 — 끝 자세를 지킴): 목을 뒤 위로 당기고 가슴을 부풀림 · 입은 조금 · 날개를 들어 뒤로
        "inhale": {"animation_length": 0.4, "bones": {
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-14, 0, 0])])},
            "neck2": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-10, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-8, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.4, [12, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-10, 0, 0])]), "scale": kf([(0, [1, 1, 1]), (0.4, [1.07, 1.1, 1.05])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.4, [0, 12, 18])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.4, [0, -12, -18])])},
        }},
        # 불을 토함 (0.2초 — 끝 자세를 지킴): 목을 앞 아래로 내밀고 (머리 약 20° 아래) 입을 활짝 · 날개를 뒤 위로 젖힘
        "breathe": {"animation_length": 0.2, "bones": {
            "neck1": {"rotation": kf([(0, [-14, 0, 0]), (0.2, [12, 0, 0])])},
            "neck2": {"rotation": kf([(0, [-10, 0, 0]), (0.2, [6, 0, 0])])},
            "head": {"rotation": kf([(0, [-8, 0, 0]), (0.2, [4, 0, 0])])},
            "jaw": {"rotation": kf([(0, [12, 0, 0]), (0.12, [46, 0, 0]), (0.2, [42, 0, 0])])},
            "body": {"rotation": kf([(0, [-10, 0, 0]), (0.2, [4, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 12, 18]), (0.2, [0, 30, 24])])},
            "wingL": {"rotation": kf([(0, [0, -12, -18]), (0.2, [0, -30, -24])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 0, 12])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 0, -12])])},
        }},
        # 내리꽂음 (0.25초 — 끝 자세를 지킴): 머리를 아래로 (몸 62°) · 날개를 뒤로 접음 · 목을 곧게 · 입을 벌림 · 발을 뒤로
        "dive": {"animation_length": 0.25, "bones": {
            "root": {"rotation": kf([(0, [0, 0, 0]), (0.25, [62, 0, 0])])},
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0])])},
            "neck2": {"rotation": kf([(0, [0, 0, 0]), (0.25, [8, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-22, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.25, [26, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.25, [0, 64, -6])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.25, [0, -64, 6])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.25, [0, 34, -4])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.25, [0, -34, 4])])},
            "legFR": {"rotation": kf([(0, [0, 0, 0]), (0.25, [40, 0, 0])])},
            "legFL": {"rotation": kf([(0, [0, 0, 0]), (0.25, [40, 0, 0])])},
            "legBR": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0])])},
            "legBL": {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0])])},
        }},
        # 땅에 닿음 (0.5초 — 끝 자세를 지킴): 내리꽂은 자세에서 몸을 세우며 날개를 활짝 (위 · 앞) · 고개를 들어 포효
        "crash": {"animation_length": 0.5, "bones": {
            "root": {"rotation": kf([(0, [62, 0, 0]), (0.12, [10, 0, 0]), (0.3, [-6, 0, 0]), (0.5, [-4, 0, 0])])},
            "body": {"position": kf([(0, [0, -2, 0]), (0.15, [0, -3, 0]), (0.5, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [30, 0, 0]), (0.15, [10, 0, 0]), (0.35, [-14, 0, 0]), (0.5, [-12, 0, 0])])},
            "head": {"rotation": kf([(0, [-22, 0, 0]), (0.35, [-22, 0, 0]), (0.5, [-20, 0, 0])])},
            "jaw": {"rotation": kf([(0, [26, 0, 0]), (0.3, [42, 0, 0]), (0.5, [36, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 64, -6]), (0.15, [0, -6, 8]), (0.35, [0, -14, 36]), (0.5, [0, -12, 32])])},
            "wingL": {"rotation": kf([(0, [0, -64, 6]), (0.15, [0, 6, -8]), (0.35, [0, 14, -36]), (0.5, [0, 12, -32])])},
            "wingR2": {"rotation": kf([(0, [0, 34, -4]), (0.2, [0, 0, -8]), (0.5, [0, 0, 12])])},
            "wingL2": {"rotation": kf([(0, [0, -34, 4]), (0.2, [0, 0, 8]), (0.5, [0, 0, -12])])},
        }},
        # 흩어짐 (0.5초): 부풀었다가 불티로 작아지며 솟음 · 날개를 위로
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 10, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.15, [1.12, 1.12, 1.12]), (0.5, [0.04, 0.04, 0.04])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.5, [0, 0, 40])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.5, [0, 0, -40])])},
        }},
        # ---- 2026-11-13 드라군 변신 (사용자 "사람 빼고 드레곤으로 변해서 공격하는걸로") — 용이 스킬을 직접 함 (job.DragonForm)
        # 뛰어오름 (0.3초 — 끝 = 쉬는 자세 · 이어 fly): 웅크려 날개를 위 뒤로 들었다가 (0.1) 날개를 세게 내려치며 몸을 폄 (0.18)
        "launch": {"animation_length": 0.3, "bones": {
            "root": {"rotation": kf([(0, [0, 0, 0]), (0.1, [6, 0, 0]), (0.18, [-10, 0, 0]), (0.3, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.1, [0, -3, 0]), (0.18, [0, 2, 0]), (0.3, [0, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 10, 45]), (0.18, [0, -5, -35]), (0.3, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, -10, -45]), (0.18, [0, 5, 35]), (0.3, [0, 0, 0])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, 20]), (0.18, [0, 0, -20]), (0.3, [0, 0, 0])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, -20]), (0.18, [0, 0, 20]), (0.3, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.1, [12, 0, 0]), (0.18, [-10, 0, 0]), (0.3, [0, 0, 0])])},
            "legFR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [20, 0, 0]), (0.18, [-10, 0, 0]), (0.3, [0, 0, 0])])},
            "legFL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [20, 0, 0]), (0.18, [-10, 0, 0]), (0.3, [0, 0, 0])])},
            "legBR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [20, 0, 0]), (0.18, [-20, 0, 0]), (0.3, [0, 0, 0])])},
            "legBL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [20, 0, 0]), (0.18, [-20, 0, 0]), (0.3, [0, 0, 0])])},
        }},
        # 꼬리 휩쓸기 (0.55초 — 끝 = 쉬는 자세): 몸을 낮추고 반대로 감았다가 (0.08) 한 바퀴 (0.45 — 꼬리가 늦게 따라와 채찍처럼) · 날개를 접음.
        # 모드 없는 클라이언트는 뼈 변환을 틱마다 받아 그 사이를 곧게 이음 (멀리 도는 꼬리가 안으로 줄어 보이지 않게 한 틱에 약 40°)
        "spin": {"animation_length": 0.55, "bones": {
            "root": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -25, 0]), (0.45, [0, 360, 0]), (0.55, [0, 360, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.08, [0, -2, 0]), (0.45, [0, -2, 0]), (0.55, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.08, [14, 0, 0]), (0.45, [12, 0, 0]), (0.55, [0, 0, 0])])},
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -10, 0]), (0.3, [0, 14, 0]), (0.47, [0, -8, 0]), (0.55, [0, 0, 0])])},
            "tail2": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -10, 0]), (0.3, [0, 16, 0]), (0.47, [0, -10, 0]), (0.55, [0, 0, 0])])},
            "tail3": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -10, 0]), (0.3, [0, 18, 0]), (0.47, [0, -12, 0]), (0.55, [0, 0, 0])])},
            "tail4": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -10, 0]), (0.3, [0, 20, 0]), (0.47, [0, -14, 0]), (0.55, [0, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, 30, -10]), (0.45, [0, 30, -10]), (0.55, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -30, 10]), (0.45, [0, -30, 10]), (0.55, [0, 0, 0])])},
        }},
        # 물기 (0.3초 — 끝 자세를 지킴): 목을 앞 아래로 내뻗으며 입을 벌렸다가 (0.1) 꽉 다묾 (0.22) · 몸이 앞으로 · 날개를 펼침
        "bite": {"animation_length": 0.3, "bones": {
            "root": {"rotation": kf([(0, [0, 0, 0]), (0.12, [12, 0, 0]), (0.3, [10, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.12, [0, 0, -4]), (0.3, [0, 0, -3])])},
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.3, [30, 0, 0])])},
            "neck2": {"rotation": kf([(0, [0, 0, 0]), (0.15, [10, 0, 0]), (0.3, [10, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-22, 0, 0]), (0.3, [-20, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.1, [40, 0, 0]), (0.2, [0, 0, 0]), (0.3, [2, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, -10, 15]), (0.3, [0, -10, 15])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, 10, -15]), (0.3, [0, 10, -15])])},
        }},
        # 들어 올림 (0.5초 — 끝 자세를 지킴): 문 채로 뒷발로 일어섬 (앞이 35° 위) · 목 · 머리를 높이 · 날개를 위로 활짝 · 앞발을 듦
        "rear": {"animation_length": 0.5, "bones": {
            "root": {"rotation": kf([(0, [10, 0, 0]), (0.35, [-32, 0, 0]), (0.5, [-35, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.35, [0, 5, 0]), (0.5, [0, 5, 0])])},
            "body": {"position": kf([(0, [0, 0, -3]), (0.35, [0, 1, 0]), (0.5, [0, 1, 0])])},
            "neck1": {"rotation": kf([(0, [30, 0, 0]), (0.35, [-8, 0, 0]), (0.5, [-10, 0, 0])])},
            "neck2": {"rotation": kf([(0, [10, 0, 0]), (0.35, [-4, 0, 0]), (0.5, [-5, 0, 0])])},
            "head": {"rotation": kf([(0, [-20, 0, 0]), (0.35, [14, 0, 0]), (0.5, [16, 0, 0])])},
            "jaw": {"rotation": kf([(0, [2, 0, 0]), (0.5, [2, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, -10, 15]), (0.35, [0, -16, 40]), (0.5, [0, -15, 38])])},
            "wingL": {"rotation": kf([(0, [0, 10, -15]), (0.35, [0, 16, -40]), (0.5, [0, 15, -38])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.35, [0, 0, 14]), (0.5, [0, 0, 12])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.35, [0, 0, -14]), (0.5, [0, 0, -12])])},
            "legFR": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-30, 0, 0]), (0.5, [-28, 0, 0])])},
            "legFL": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-30, 0, 0]), (0.5, [-28, 0, 0])])},
        }},
        # 내리꽂기 (0.6초 — 끝 = 쉬는 자세): 일어선 자세에서 몸 · 목을 앞 아래로 내려쳐 (0.15 — 문 것을 땅에 박음 · 입을 벌림) → 일어나 쉬는 자세로
        "slam": {"animation_length": 0.6, "bones": {
            "root": {"rotation": kf([(0, [-35, 0, 0]), (0.15, [12, 0, 0]), (0.3, [10, 0, 0]), (0.6, [0, 0, 0])]),
                     "position": kf([(0, [0, 5, 0]), (0.15, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 1, 0]), (0.15, [0, -2, -2]), (0.3, [0, -2, -2]), (0.6, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [-10, 0, 0]), (0.15, [26, 0, 0]), (0.3, [24, 0, 0]), (0.6, [0, 0, 0])])},
            "neck2": {"rotation": kf([(0, [-5, 0, 0]), (0.15, [8, 0, 0]), (0.3, [7, 0, 0]), (0.6, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [16, 0, 0]), (0.15, [-6, 0, 0]), (0.3, [-7, 0, 0]), (0.6, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [2, 0, 0]), (0.15, [24, 0, 0]), (0.3, [30, 0, 0]), (0.6, [0, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, -15, 38]), (0.15, [0, 20, -20]), (0.3, [0, 18, -14]), (0.6, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 15, -38]), (0.15, [0, -20, 20]), (0.3, [0, -18, 14]), (0.6, [0, 0, 0])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 12]), (0.15, [0, 0, -10]), (0.6, [0, 0, 0])])},
            "wingL2": {"rotation": kf([(0, [0, 0, -12]), (0.15, [0, 0, 10]), (0.6, [0, 0, 0])])},
            "legFR": {"rotation": kf([(0, [-28, 0, 0]), (0.15, [18, 0, 0]), (0.6, [0, 0, 0])])},
            "legFL": {"rotation": kf([(0, [-28, 0, 0]), (0.15, [18, 0, 0]), (0.6, [0, 0, 0])])},
        }},
        # 짓밟기 (0.55초 — 끝 = 쉬는 자세): 앞을 들고 날개를 위로 (0.1) → 0.2초에 온몸으로 내려찍음 (날개를 내려침 · 앞발 · 입) → 쉬는 자세로
        "stomp": {"animation_length": 0.55, "bones": {
            "root": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-15, 0, 0]), (0.2, [10, 0, 0]), (0.55, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 3, 0]), (0.2, [0, -3, 0]), (0.55, [0, 0, 0])])},
            "neck1": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-15, 0, 0]), (0.2, [18, 0, 0]), (0.55, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.1, [10, 0, 0]), (0.2, [30, 0, 0]), (0.55, [0, 0, 0])])},
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, 45]), (0.2, [0, 0, -25]), (0.55, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, -45]), (0.2, [0, 0, 25]), (0.55, [0, 0, 0])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, 20]), (0.2, [0, 0, -15]), (0.55, [0, 0, 0])])},
            "wingL2": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, -20]), (0.2, [0, 0, 15]), (0.55, [0, 0, 0])])},
            "legFR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-45, 0, 0]), (0.2, [15, 0, 0]), (0.55, [0, 0, 0])])},
            "legFL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-45, 0, 0]), (0.2, [15, 0, 0]), (0.55, [0, 0, 0])])},
        }},
    }
    out = dict(still)
    out.update(run)
    return out


# ================================================================== 워로드의 곰의 혼 (2026-11-13 — 직업 도감 메모 하이버네이트 · 그리즐리 크러시 · 베어 스톰프 ·
# 카타클리즘 "곰과 땅 연출을 3d로"): 짙은 갈색 털 · 호박빛 정령 핏줄 · 등과 어깨의 돌 판 (호박빛 금) · 호박빛 눈 · 상아 발톱 (빛 테) · 달아오른 입.
# 네 발로 섰을 때 어깨 약 1.9칸 · 코에서 꼬리 약 3칸 (크기 1). 앞 = −z

BR_FUR = (82, 52, 30)
BR_FUR_DARK = (48, 29, 17)
BR_BELLY = (122, 86, 54)
BR_GLOW = (255, 148, 40)     # 워로드 빛 (1.0, 0.58, 0.16)
BR_CORE = (255, 214, 140)
BR_STONE = (96, 84, 70)
BR_STONE_DARK = (60, 52, 44)
BR_IVORY = (242, 228, 198)
BR_EYE = (255, 206, 80)

# 일어선 자세 (뒷발로 — 엉덩이 hips 를 X 로 젖힘) · 두 팔을 치켜든 자세 (오른쪽 R = −x)
BR_REAR = -62


def job_bear():
    m = Model("job_bear", tex_w=256)
    fur = Mat(BR_FUR, "fur", noise=9, accent=BR_FUR_DARK, seed=901, veins=1, vein=BR_GLOW, vein_glow=0.75)
    furp = Mat(BR_FUR, "fur", noise=9, accent=BR_FUR_DARK, seed=902)
    belly = Mat(BR_BELLY, "fur", noise=6, accent=(92, 62, 38), seed=903)
    stone = Mat(BR_STONE, "plates", noise=6, edge=0.8, accent=BR_STONE_DARK, seed=904, seam=BR_GLOW, seam_glow=0.9, seam_band=3, seam_dir="grid",
                seam_gap=0.3)
    glow = Mat(BR_GLOW, "flat", noise=8, edge=1.0, glow=0.9, seed=905)
    claw = Mat(BR_IVORY, "bone", noise=3, edge=0.9, seed=907, rim=BR_GLOW, rim_glow=0.65)
    dark = Mat((26, 16, 10), "flat", noise=3, edge=0.9, seed=908)
    teeth = Mat((246, 238, 218), "flat", noise=2, edge=0.95, seed=909)
    mouth = Mat((255, 118, 36), "flat", noise=8, edge=0.9, glow=0.85, seed=910)

    root = m.bone("root", [0, 0, 0])
    hips = m.bone("hips", [0, 18, 8], "root")
    body = m.bone("body", [0, 20, 0], "hips")
    # 몸통 (앞 −z): 배 · 어깨 혹 (정령 핏줄) · 엉덩이 · 등의 돌 판 넷 (호박빛 금) · 어깨 돌 판
    body.box([-8, 11, -13], [16, 14, 25], furp.with_(seed=910), faces={"down": belly})
    body.box([-8.5, 19, -15], [17, 10, 12], fur.with_(seed=911), faces={"down": belly.with_(seed=912)})
    body.box([-7.5, 12, 6], [15, 14, 9], furp.with_(seed=913), faces={"down": belly.with_(seed=914)})
    body.box([-5, 29, -14], [10, 2, 8], stone)
    body.box([-4.5, 26.5, -5], [9, 2, 7], stone.with_(seed=915))
    body.box([-4, 25.5, 3], [8, 2, 6], stone.with_(seed=916))
    body.box([-3, 25.5, 10], [6, 2, 4], stone.with_(seed=917))
    for i, z in enumerate((-12, -4, 4)):
        body.box([-1, 31 - (2.5 if i == 1 else 0) - (3.5 if i == 2 else 0), z], [2, 2, 2], glow.with_(seed=918 + i))  # 판 위 빛 돌기
    body.box([-10, 22, -13], [3, 6, 8], stone.with_(seed=921))
    body.box([7, 22, -13], [3, 6, 8], stone.with_(seed=922))
    # 머리: 두개 · 주둥이 (옅은 털) · 코 · 눈썹 돌 판 · 이마의 빛 · 송곳니 · 귀
    head = m.bone("head", [0, 24, -14], "body")
    eyes = [("north", 2, 4, BR_EYE, 1.0), ("north", 3, 4, BR_EYE, 1.0), ("north", 7, 4, BR_EYE, 1.0), ("north", 8, 4, BR_EYE, 1.0),
            ("north", 2, 5, (60, 24, 6)), ("north", 8, 5, (60, 24, 6)), ("north", 5, 1, BR_GLOW, 0.9), ("north", 5, 2, BR_GLOW, 0.9)]
    head.box([-5.5, 17, -24], [11, 11, 10], furp.with_(seed=930), decals=eyes)
    head.box([-3.5, 17, -30], [7, 6, 6], belly.with_(seed=931))
    head.box([-1.5, 21.5, -31], [3, 2, 1], dark)
    head.box([-5.5, 27, -24.5], [11, 2, 5], stone.with_(seed=932))
    head.box([-3, 15.5, -29.5], [1, 2, 1], teeth)
    head.box([2, 15.5, -29.5], [1, 2, 1], teeth.with_(seed=933))
    jaw = m.bone("jaw", [0, 18, -24], "head")
    jaw.box([-3, 15, -30], [6, 2, 6], belly.with_(seed=934), faces={"up": mouth})
    jaw.box([-2.5, 17, -29.5], [1, 1, 1], teeth.with_(seed=935))
    jaw.box([1.5, 17, -29.5], [1, 1, 1], teeth.with_(seed=936))
    for side, sx in (("R", -1), ("L", 1)):
        ear = m.bone("ear" + side, [4 * sx, 28, -18], "head")
        ear.box([4 * sx - 1.5, 28, -19], [3, 3, 2], furp.with_(seed=937 + sx), decals=[("north", 1, 1, BR_GLOW, 0.7)])
    # 앞다리 (몸을 따라 — 일어서 치켜듦): 윗다리 · 아랫다리 · 큰 발 · 상아 발톱 넷 (빛 테)
    for side, sx in (("R", -1), ("L", 1)):
        x0 = -10.5 if sx < 0 else 4.5
        up = m.bone("legF" + side, [7.5 * sx, 21, -8], "body")
        up.box([x0, 8, -12], [6, 14, 8], furp.with_(seed=940 + sx))
        lo = m.bone("armF" + side, [7.5 * sx, 9, -8], "legF" + side)
        lo.box([x0 + 0.5, 2, -11.5], [5, 8, 6], furp.with_(seed=942 + sx))
        lo.box([x0 - 0.5, 0, -14], [7, 3, 8], furp.with_(seed=944 + sx), faces={"down": dark})
        for i in range(4):
            lo.box([x0 - 0.25 + i * 1.75, 0.5, -17], [1, 1, 3], claw.with_(seed=946 + i))
        # 뒷다리 (땅에 — 몸이 일어서도 그대로)
        xb = -9.5 if sx < 0 else 3.5
        th = m.bone("legB" + side, [6.5 * sx, 17, 9], "root")
        th.box([xb, 7, 4], [6, 12, 10], furp.with_(seed=950 + sx))
        th.box([xb + 0.5, 2, 6], [5, 6, 6], furp.with_(seed=952 + sx))
        th.box([xb, 0, 2], [6, 2, 8], furp.with_(seed=954 + sx), faces={"down": dark})
        for i in range(3):
            th.box([xb + 0.5 + i * 2, 0.25, 0.5], [1, 1, 2], claw.with_(seed=956 + i))
    tail = m.bone("tail", [0, 23, 15], "body")
    tail.box([-1.5, 21, 14.5], [3, 3, 3], furp.with_(seed=960))
    return m


def _br_pose(kind):
    """곰의 자세 (뼈 → 회전): rear = 일어서 두 팔을 치켜듦 · wide = 일어서 두 팔을 양옆으로 활짝 · hug = 일어서 두 팔을 앞 가운데로 모음 ·
    wind = 내려치기 직전 (더 젖혀 두 팔을 머리 위로) · hit = 두 앞발이 땅을 침 · curl = 엎드려 웅크림 (뿌리를 낮춤 — 다리는 땅 아래)"""
    if kind == "rear":
        return {"hips": [BR_REAR, 0, 0], "head": [48, 0, 0], "jaw": [26, 0, 0], "legFR": [-105, 32, 0], "legFL": [-105, -32, 0],
                "armFR": [-25, 0, 0], "armFL": [-25, 0, 0], "tail": [20, 0, 0]}
    if kind == "wide":
        return {"hips": [BR_REAR, 0, 0], "head": [44, 0, 0], "jaw": [32, 0, 0], "legFR": [-98, 72, 0], "legFL": [-98, -72, 0],
                "armFR": [-15, 0, 0], "armFL": [-15, 0, 0], "tail": [20, 0, 0]}
    if kind == "hug":
        return {"hips": [BR_REAR + 6, 0, 0], "head": [52, 0, 0], "jaw": [36, 0, 0], "legFR": [-50, 0, -24], "legFL": [-50, 0, 24],
                "armFR": [-45, 0, 0], "armFL": [-45, 0, 0], "tail": [20, 0, 0]}
    if kind == "wind":
        return {"hips": [BR_REAR - 10, 0, 0], "head": [58, 0, 0], "jaw": [38, 0, 0], "legFR": [-128, 12, 0], "legFL": [-128, -12, 0],
                "armFR": [-20, 0, 0], "armFL": [-20, 0, 0], "tail": [24, 0, 0]}
    if kind == "hit":
        return {"hips": [10, 0, 0], "head": [18, 0, 0], "jaw": [34, 0, 0], "legFR": [-28, 0, 6], "legFL": [-28, 0, -6],
                "armFR": [8, 0, 0], "armFL": [8, 0, 0], "tail": [-6, 0, 0], "body": [0, 0, 0]}
    if kind == "roared":
        return {"hips": [BR_REAR - 8, 0, 0], "head": [20, 0, 0], "jaw": [44, 0, 0], "legFR": [-93, 47, 0], "legFL": [-93, -47, 0],
                "armFR": [-17, 0, 0], "armFL": [-17, 0, 0], "tail": [24, 0, 0]}
    if kind == "curl":
        return {"hips": [6, 0, 0], "head": [24, -30, 8], "jaw": [0, 0, 0], "legFR": [-70, 20, 0], "legFL": [-70, 30, 0],
                "armFR": [60, 0, 0], "armFL": [60, 0, 0], "tail": [0, 0, 0]}
    return {}


BR_BONES = ("hips", "head", "jaw", "legFR", "legFL", "armFR", "armFL", "tail")


def _br_move(times, poses, extra=None, length=None):
    """자세 차례 → 뼈마다 회전 키 (times[i] 초에 poses[i] — 없는 뼈는 0)"""
    bones = {}
    for b in BR_BONES:
        keys = [(t, _br_pose(p).get(b, [0, 0, 0]) if isinstance(p, str) else p.get(b, [0, 0, 0])) for t, p in zip(times, poses)]
        if any(v != [0, 0, 0] for _, v in keys):
            bones[b] = {"rotation": kf(keys)}
    for b, v in (extra or {}).items():
        bones.setdefault(b, {}).update(v)
    out = {"bones": bones}
    if length is not None:
        out["animation_length"] = length
    return out


def job_bear_anims():
    br = f"math.sin({LT} * 110)"
    still = {
        # 늘 (작게): 숨 · 머리 · 입 · 귀
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"{br} * 0.45", 0], "scale": [f"1 + {br} * 0.015", f"1 + {br} * 0.02", 1]},
            "head": {"rotation": [f"math.sin({LT} * 70) * 3", f"math.sin({LT} * 45) * 6", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 55)) * 6", 0, 0]},
            "earR": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 140)) * -10"]},
            "earL": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 140 + 90)) * 10"]},
        }},
    }
    curl_extra = {"root": {"position": [0, -9, 0]}, "body": {"scale": [f"1 + {br} * 0.03", f"1 + {br} * 0.04", 1]}}
    run = {
        # 땅에서 솟음 (0.5초): 작게 땅속에서 커지며 올라옴 · 고개를 듦
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"scale": kf([(0, [0.2, 0.2, 0.2]), (0.3, [1.08, 1.08, 1.08]), (0.5, [1, 1, 1])]),
                     "position": kf([(0, [0, -12, 0]), (0.3, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [24, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        # 땅에서 솟으며 일어서 두 팔을 활짝 (0.25초 — 끝 자세를 지킴): 그리즐리 크러시
        "rise_wide": dict(_br_move([0, 0.25], [{}, "wide"], {"root": {"scale": kf([(0, [0.3, 0.3, 0.3]), (0.18, [1.06, 1.06, 1.06]), (0.25, [1, 1, 1])]),
                                                                       "position": kf([(0, [0, -14, 0]), (0.18, [0, 0, 0])])}}, 0.25)),
        # 일어섬 (0.45초 — 끝 자세를 지킴): 뒷발로 서며 두 팔을 치켜듦 · 포효
        "rear": _br_move([0, 0.2, 0.45], [{}, {"hips": [BR_REAR + 14, 0, 0], "head": [10, 0, 0], "jaw": [20, 0, 0]}, "rear"], length=0.45),
        # 포효 (0.9초 — 끝 자세를 지킴): 네 발에서 일어서며 (0.3) 고개를 하늘로 젖혀 입을 크게 · 두 팔을 활짝 (0.55)
        "roar": _br_move([0, 0.3, 0.55, 0.9], [{}, {"hips": [BR_REAR - 4, 0, 0], "head": [66, 0, 0], "jaw": [12, 0, 0], "legFR": [-100, 22, 0],
                                                  "legFL": [-100, -22, 0], "armFR": [-30, 0, 0], "armFL": [-30, 0, 0], "tail": [20, 0, 0]}, "roared", "roared"],
                         length=0.9),
        # 포효한 채 내려침 (0.55초 — 닿는 때 0.25초): 활짝 편 팔을 머리 위로 → 내리찍음 (카타클리즘)
        "roarslam": _br_move([0, 0.12, 0.25, 0.38, 0.55], ["roared", "wind", "hit", "hit", {}],
                             {"body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, -2.5, -1]), (0.38, [0, -2, -1]), (0.55, [0, 0, 0])])}}, 0.55),
        # 으르렁 (0.7초 — 네 발로): 앞을 들썩이며 고개를 들어 입을 크게 → 낮춤 (베어 스톰프의 끝)
        "growl": _br_move([0, 0.2, 0.5, 0.7], [{}, {"hips": [-14, 0, 0], "head": [-26, 0, 0], "jaw": [44, 0, 0], "legFR": [-30, 0, 0], "legFL": [-30, 0, 0]},
                                               {"hips": [-10, 0, 0], "head": [-20, 0, 0], "jaw": [40, 0, 0], "legFR": [-20, 0, 0], "legFL": [-20, 0, 0]}, {}],
                          length=0.7),
        # 내려침 (0.55초 — 두 앞발이 땅에 닿는 때 0.25초 = 서버 BEAR_SLAM_AT 5틱): 일어선 자세에서 더 젖혀 머리 위로 (0.12) → 온몸으로 내리찍음 → 네 발로
        "slam": _br_move([0, 0.12, 0.25, 0.38, 0.55], ["rear", "wind", "hit", "hit", {}],
                         {"body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, -2.5, -1]), (0.38, [0, -2, -1]), (0.55, [0, 0, 0])])}}, 0.55),
        # 껴안기 (0.35초 — 끝 자세를 지킴): 활짝 편 두 팔을 앞 가운데로 휩쓸어 모음
        "hug": _br_move([0, 0.35], ["wide", "hug"], length=0.35),
        # 껴안은 채 내려침 (0.55초 — 닿는 때 0.25초): 모은 팔을 머리 위로 → 내리찍음
        "hugslam": _br_move([0, 0.12, 0.25, 0.38, 0.55], ["hug", "wind", "hit", "hit", {}],
                            {"body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, -2.5, -1]), (0.38, [0, -2, -1]), (0.55, [0, 0, 0])])}}, 0.55),
        # 엎드려 웅크림 (이어 돎 — 하이버네이트): 몸을 낮추고 고개를 옆으로 묻음 · 깊은 숨
        "curl": dict(_br_move([0], ["curl"], curl_extra), loop=True),
        # 깨어나 일어섬 (0.5초 — 끝 = 일어선 자세): 웅크림에서 몸을 들어 뒷발로 서며 두 팔을 치켜듦 · 포효
        "wake": _br_move([0, 0.2, 0.5], ["curl", {"hips": [BR_REAR + 20, 0, 0], "head": [20, 0, 0], "jaw": [40, 0, 0], "legFR": [-80, 30, 0], "legFL": [-80, -30, 0],
                                                  "armFR": [-20, 0, 0], "armFL": [-20, 0, 0]}, "rear"],
                         {"root": {"position": kf([(0, [0, -9, 0]), (0.2, [0, 0, 0])])}}, 0.5),
        # 흩어짐 (0.5초): 부풀었다가 작아지며 솟음
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 8, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.1, 1.1, 1.1]), (0.5, [0.04, 0.04, 0.04])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-30, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [34, 0, 0])])},
        }},
    }
    out = dict(still)
    out.update(run)
    return out


# ================================================================== 망령 사신 (2026-11-13 — 직업 도감 메모 데스 아바타 "사신 그림자가 3d몹처럼 나와서 휘두르는거" ·
# 페일 하베스트 "사신을 3d몹 처럼 만들어서 직접 낫을 휩쓸고 베게"): 떠 있는 두건 쓴 사신 — 짙은 남빛 옷 (청록 테 · 룬 줄) · 두건 속 해골 (청록 눈) ·
# 뼈 손 · 어깨의 뼈 · 아래는 해진 옷자락이 영혼 연기로 (비침), 오른손에 큰 낫 (검은 자루 · 청록 고리 · 휘어진 영혼빛 날). 키 약 2.6칸 (크기 1) · 앞 = −Z

RP_ROBE = (24, 28, 44)
RP_ROBE2 = (36, 42, 62)
RP_TRIM = (64, 238, 216)      # 사신 빛 (0.22, 0.96, 0.86)
RP_CORE = (206, 255, 248)
RP_BONE = (228, 222, 202)
RP_VOID = (6, 8, 14)
RP_SHAFT = (38, 30, 34)
RP_BLADE = (96, 214, 236)


def job_reaper():
    m = Model("job_reaper", tex_w=128)
    robe = see(Mat(RP_ROBE, "knit", noise=5, edge=0.8, seed=1201, accent=(16, 18, 30)), 0.9)
    robe2 = see(Mat(RP_ROBE2, "knit", noise=5, edge=0.82, seed=1202, accent=(24, 28, 44)), 0.92)
    trim = Mat(RP_TRIM, "flat", noise=4, edge=1.0, glow=0.9, seed=1203)
    core = Mat(RP_CORE, "flat", noise=3, edge=1.0, glow=1.0, seed=1204)
    bone = Mat(RP_BONE, "bone", noise=4, edge=0.86, seed=1205)
    void = Mat(RP_VOID, "flat", noise=2, edge=1.0, seed=1206)
    shaft = Mat(RP_SHAFT, "wood", noise=5, edge=0.85, seed=1207)
    blade = Mat(RP_BLADE, "crystal", noise=4, edge=1.0, glow=0.6, seed=1208)
    smoke = see(Mat((40, 70, 84), "ice", noise=8, edge=0.95, seed=1209, accent=(60, 150, 160)), 0.55)
    tatter = see(Mat(RP_ROBE2, "knit", noise=5, edge=0.85, seed=1210, accent=(20, 24, 38)), 0.88)
    eyes = [("north", 1, 3, RP_CORE, 1.0), ("north", 2, 3, RP_TRIM, 1.0), ("north", 4, 3, RP_TRIM, 1.0), ("north", 5, 3, RP_CORE, 1.0),
            ("north", 3, 4, RP_VOID), ("north", 1, 5, RP_VOID), ("north", 3, 5, RP_VOID), ("north", 5, 5, RP_VOID)]

    root = m.bone("root", [0, 0, 0])
    # 아래: 해진 옷자락 · 영혼 연기 (비침 — 떠 있음)
    tail = m.bone("tail", [0, 12, 0], "root")
    tail.box([-6, 3, -4], [12, 9, 8], tatter)
    tail.box([-5, 0, -3.5], [3, 3, 2], tatter.with_(seed=1211))
    tail.box([1, -1, -3.5], [3, 4, 2], tatter.with_(seed=1212))
    tail.box([-4, 0, 1.5], [3, 3, 2], tatter.with_(seed=1213))
    tail.box([2, 1, 1.5], [3, 2, 2], tatter.with_(seed=1219))
    tail.box([-4.5, -2, -3], [9, 5, 6], smoke)
    body = m.bone("body", [0, 12, 0], "root")
    # 옷: 아랫단 (청록 테) · 가슴 · 앞섶의 빛 줄 · 허리띠 · 가슴뼈
    body.box([-6.5, 12, -4], [13, 11, 8], robe)
    body.box([-6.5, 12, -4], [13, 1, 8], trim.with_(seed=1214), inflate=0.15)
    body.box([-5.5, 23, -3.5], [11, 8, 7], robe.with_(seed=1215), decals=[("north", 3, 2, RP_BONE), ("north", 7, 2, RP_BONE), ("north", 3, 4, RP_BONE),
                                                                           ("north", 7, 4, RP_BONE), ("north", 5, 1, RP_TRIM, 0.9)])
    body.box([-1, 12, -4.4], [2, 19, 1], trim.with_(seed=1216))
    body.box([-6, 22, -4.2], [12, 1, 8], trim.with_(seed=1217), inflate=0.1)
    # 어깨 옷 · 어깨뼈 (가시)
    mantle = m.bone("mantle", [0, 31, 0], "body")
    mantle.box([-8, 28, -4.5], [16, 3, 9], robe2, faces={"down": robe2})
    mantle.box([-8, 28, -4.5], [16, 1, 9], trim.with_(seed=1218), inflate=0.1)
    mantle.box([-9.5, 30, -2.5], [3, 3, 5], bone.with_(seed=1219))
    mantle.box([6.5, 30, -2.5], [3, 3, 5], bone.with_(seed=1220))
    mantle.box([-9, 33, -1.5], [1, 2, 1], bone.with_(seed=1221))
    mantle.box([8, 33, -1.5], [1, 2, 1], bone.with_(seed=1222))
    # 머리: 두건 (앞이 트임) 속 해골 · 두건 끝 테 · 뒤로 늘어진 두건 끝
    head = m.bone("head", [0, 31, 0], "body")
    head.box([-3.5, 31, -3], [7, 7, 7], bone.with_(seed=1223), decals=eyes)
    head.box([-5, 38, -4.5], [10, 2, 10], robe2.with_(seed=1224))
    head.box([-5, 30, -4.5], [1, 8, 10], robe2.with_(seed=1225))
    head.box([4, 30, -4.5], [1, 8, 10], robe2.with_(seed=1226))
    head.box([-5, 30, 4.5], [10, 8, 1], robe2.with_(seed=1227))
    head.box([-5, 39, -5], [10, 1, 1], trim.with_(seed=1228))
    head.box([-5, 30, -5], [1, 9, 1], trim.with_(seed=1229))
    head.box([4, 30, -5], [1, 9, 1], trim.with_(seed=1230))
    tip = m.bone("hoodTip", [0, 39, 4], "head", [-32, 0, 0])
    tip.box([-2, 37, 4], [4, 3, 6], robe2.with_(seed=1231))
    # 팔: 넓은 소매 (청록 테 끝) · 뼈 팔 · 뼈 손
    for side, sx in (("R", -1), ("L", 1)):
        ax = -10 if sx < 0 else 6
        arm = m.bone("arm" + side, [7 * sx, 29, 0], "body")
        arm.box([ax, 20, -2.5], [4, 9, 5], robe2.with_(seed=1232 + sx))
        arm.box([ax, 20, -2.5], [4, 1, 5], trim.with_(seed=1234 + sx), inflate=0.15)
        fa = m.bone("forearm" + side, [8 * sx, 21, 0], "arm" + side)
        fx = -9 if sx < 0 else 7
        fa.box([fx, 14, -1], [2, 7, 2], bone.with_(seed=1236 + sx))
        fa.box([fx - 0.5, 11, -1.5], [3, 3, 3], bone.with_(seed=1238 + sx))
    # 낫 (오른손 — 아래팔을 따라 자루가 손을 지나 위아래로): 검은 자루 · 청록 고리 셋 · 밑 가시 · 위 끝에서 앞으로 뻗어 아래로 휘는 영혼빛 날 (세 마디)
    sc = m.bone("scythe", [-8, 12.5, 0], "forearmR")
    sc.box([-8.5, -12, -0.5], [1, 46, 1], shaft)
    for y in (-2, 9, 31):
        sc.box([-9, y, -1], [2, 1, 2], trim.with_(seed=1240 + y))
    sc.box([-8.5, -15, -0.5], [1, 3, 1], bone.with_(seed=1244))
    sc.box([-9, 32, -1.5], [2, 3, 3], bone.with_(seed=1245))
    b1 = m.bone("blade1", [-8, 33.5, -1], "scythe")
    b1.box([-8.5, 32, -11], [1, 3, 10], blade, faces={"down": core})
    b2 = m.bone("blade2", [-8, 33.5, -11], "blade1", [24, 0, 0])
    b2.box([-8.5, 32.5, -18], [1, 2, 7], blade.with_(seed=1246), faces={"down": core})
    b3 = m.bone("blade3", [-8, 33.5, -18], "blade2", [26, 0, 0])
    b3.box([-8.5, 33, -22], [1, 1, 4], core.with_(seed=1247))
    return m


# 쉬는 자세 (idle 이 늘 켜져 있고 동작은 더해짐 — rp_rel 로 뺌): 낫을 오른쪽에 세워 듦 · 왼손은 자루 쪽으로 · 조금 숙인 몸
RP_READY = {
    "armR": {"rotation": [-28, 0, 6]}, "forearmR": {"rotation": [-40, 0, 0]}, "scythe": {"rotation": [68, 0, 0]},
    "armL": {"rotation": [-36, -24, -6]}, "forearmL": {"rotation": [-48, 0, 0]},
    "body": {"rotation": [6, 0, 0]}, "head": {"rotation": [-4, 0, 0]}, "tail": {"rotation": [10, 0, 0]},
}


def rp_rel(bones):
    """동작 열쇠 (그 뼈의 실제 자세) → 쉬는 자세에서 더할 값"""
    out = {}
    for b, chans in bones.items():
        out[b] = {}
        for ch, frames in chans.items():
            base = RP_READY.get(b, {}).get(ch)
            if base is None or ch == "scale":
                out[b][ch] = kf(frames)
                continue
            out[b][ch] = kf([(t, [v - q for v, q in zip(vec, base)]) for t, vec in frames])
    return out


def job_reaper_anims():
    bob = f"math.sin({LT} * 100)"
    R0 = RP_READY

    def pose(t, **kw):
        return t, kw

    def seq(*keys):
        """keys = [(t, {뼈: 회전})] → 뼈마다 열쇠 (없는 뼈는 쉬는 자세)"""
        bones = {}
        names = set()
        for _, d in keys:
            names.update(d)
        for b in names:
            base = R0.get(b, {}).get("rotation", [0, 0, 0])
            bones[b] = {"rotation": [(t, d.get(b, base)) for t, d in keys]}
        return bones

    return {
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"{bob} * 0.8 + 1", 0]},
            "armR": R0["armR"], "forearmR": R0["forearmR"], "scythe": R0["scythe"], "armL": R0["armL"], "forearmL": R0["forearmL"],
            "body": {"rotation": [f"6 + {bob} * 2", f"math.sin({LT} * 60) * 5", 0]},
            "head": {"rotation": [f"-4 + math.sin({LT} * 80) * 3", f"math.sin({LT} * 45) * 8", 0]},
            "tail": {"rotation": [f"10 + math.sin({LT} * 150) * 6", 0, f"math.sin({LT} * 110) * 5"], "scale": [1, f"1 + {bob} * 0.05", 1]},
            "hoodTip": {"rotation": [f"math.sin({LT} * 130) * 6", 0, 0]},
        }},
        # 땅에서 솟음 (0.5초): 작게 땅 아래에서 커지며 올라옴
        "rise": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, -34, 0]), (0.35, [0, 1, 0]), (0.5, [0, 0, 0])]),
                     "scale": kf([(0, [0.45, 0.45, 0.45]), (0.35, [1.05, 1.05, 1.05]), (0.5, [1, 1, 1])])},
            "head": {"rotation": kf([(0, [20, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        # 비스듬히 베기 (0.35초 — 낫 날이 지나는 때 0.15초 = 서버 REAPER_SWING_AT 3틱): 오른쪽 위로 감았다가 왼쪽 아래로
        "swing_a": {"animation_length": 0.35, "bones": rp_rel(seq(
            pose(0, **{}), pose(0.08, armR=[-150, -30, 20], forearmR=[-20, 0, 0], scythe=[40, 0, -30], body=[-6, 30, 0], armL=[-120, 20, -10]),
            pose(0.15, armR=[-60, 40, -30], forearmR=[-10, 0, 0], scythe=[20, 0, 40], body=[18, -35, 0], armL=[-60, 10, -20]),
            pose(0.25, armR=[-40, 50, -40], forearmR=[-20, 0, 0], scythe=[30, 0, 50], body=[20, -40, 0], armL=[-40, 0, -20]),
            pose(0.35, **{})))},
        # 거꾸로 베어 올림 (0.35초 — 0.15초): 왼쪽 아래에서 오른쪽 위로
        "swing_b": {"animation_length": 0.35, "bones": rp_rel(seq(
            pose(0, **{}), pose(0.08, armR=[-30, 50, -40], forearmR=[-20, 0, 0], scythe=[10, 0, 60], body=[10, -35, 0], armL=[-30, 10, -20]),
            pose(0.15, armR=[-130, -20, 30], forearmR=[-10, 0, 0], scythe=[40, 0, -20], body=[-10, 30, 0], armL=[-110, 20, -10]),
            pose(0.25, armR=[-150, -30, 40], forearmR=[-20, 0, 0], scythe=[50, 0, -30], body=[-12, 35, 0], armL=[-120, 20, -10]),
            pose(0.35, **{})))},
        # 휩쓸기 두 바퀴 (1.2초 — 오른팔을 옆으로 뻗어 낫을 수평으로 (날이 앞) → 0.15 ~ 1.05초에 뿌리가 720° 돎 (한 틱 40° — 모드 없는 클라이언트가 뼈 변환 사이를
        # 곧게 이으므로) · 날이 한 바퀴 도는 가운데 = 0.35초 · 0.85초 (서버 REAPER_SPIN_AT 7틱 · 다음 바퀴 +10틱)
        "spin2": {"animation_length": 1.2, "bones": dict(rp_rel(seq(
            pose(0, **{}), pose(0.12, armR=[-25, 0, 80], forearmR=[-10, 0, 0], scythe=[-26, 2, -164], armL=[-70, 0, -30], forearmL=[-20, 0, 0], body=[12, 0, 0]),
            pose(1.05, armR=[-25, 0, 80], forearmR=[-10, 0, 0], scythe=[-26, 2, -164], armL=[-70, 0, -30], forearmL=[-20, 0, 0], body=[12, 0, 0]),
            pose(1.2, **{}))), root={"rotation": kf([(0, [0, 0, 0]), (0.12, [0, 30, 0]), (0.15, [0, 30, 0])]
                                                    + [(round(0.15 + i * 0.05, 2), [0, 30 - i * 40, 0]) for i in range(1, 19)] + [(1.2, [0, -720, 0])])})},
        # 거두기 (0.7초 — 날이 땅에 박히는 때 0.4초 = REAPER_REAP_AT 8틱): 두 손으로 낫을 머리 위 뒤로 치켜들었다가 (0.25) 앞 땅으로 내리찍음 (날이 땅에 걸림)
        "reap": {"animation_length": 0.7, "bones": rp_rel(seq(
            pose(0, **{}), pose(0.25, armR=[-170, 0, 8], forearmR=[-10, 0, 0], scythe=[151, 0, 8], body=[-16, 0, 0], head=[-14, 0, 0], armL=[-165, 0, -8],
                                forearmL=[-10, 0, 0]),
            pose(0.33, armR=[-110, 0, 4], forearmR=[-5, 0, 0], scythe=[137, 4, 2], body=[10, 0, 0], head=[4, 0, 0], armL=[-105, 0, -4], forearmL=[-5, 0, 0]),
            pose(0.4, armR=[-45, 0, 0], forearmR=[-5, 0, 0], scythe=[128, 0, 0], body=[30, 0, 0], head=[16, 0, 0], armL=[-42, 0, 0], forearmL=[-5, 0, 0]),
            pose(0.55, armR=[-45, 0, 0], forearmR=[-5, 0, 0], scythe=[128, 0, 0], body=[30, 0, 0], head=[16, 0, 0], armL=[-42, 0, 0], forearmL=[-5, 0, 0]),
            pose(0.7, **{})))},
        # 흩어짐 (0.5초): 부풀었다가 영혼으로 작아지며 솟음
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 10, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.08, 1.08, 1.08]), (0.5, [0.04, 0.04, 0.04])])},
        }},
    }


# ================================================================== 왕의 손 (2026-11-13 — 직업 도감 메모 타이런트 킹스 그립 "왕의 손을 3d로"): 보랏빛 망령 거인 왕의
# 갑주 손 (비침) — 금 테 · 손등의 핏빛 왕관 룬 · 손가락 넷 (두 마디 + 핏빛 발톱) · 엄지 · 팔목 갑주. 손바닥이 아래 · 손가락이 앞 (−Z) — 쥐면 아래로 감김.
# 손바닥 높이 약 20 ~ 28 픽셀 (크기 1) — 몹 자리 = 쥘 적의 발

HD_METAL = (86, 52, 132)
HD_DARK = (40, 22, 64)
HD_GOLD = (236, 190, 92)
HD_GLOW = (196, 120, 255)
HD_CRIMSON = (232, 44, 72)
HD_CORE = (255, 214, 238)
HD_FINGERS = (-6.0, -2.0, 2.0, 6.0)


def job_hand():
    m = Model("job_hand", tex_w=128)
    metal = see(Mat(HD_METAL, "metal", noise=6, edge=0.8, seed=1301, accent=HD_DARK, seam=HD_GLOW, seam_glow=0.0, seam_band=4, seam_dir="grid", seam_gap=0.4), 0.88)
    plate = see(Mat(HD_METAL, "plates", noise=5, edge=0.82, seed=1302, accent=HD_DARK, band=3), 0.9)
    dark = see(Mat(HD_DARK, "metal", noise=4, edge=0.86, seed=1303), 0.85)
    gold = Mat(HD_GOLD, "metal", noise=4, edge=0.9, seed=1304)
    glow = Mat(HD_GLOW, "flat", noise=5, edge=1.0, glow=0.9, seed=1305)
    claw = Mat(HD_CRIMSON, "crystal", noise=4, edge=1.0, glow=0.75, seed=1306)
    core = Mat(HD_CORE, "flat", noise=2, edge=1.0, glow=1.0, seed=1307)
    root = m.bone("root", [0, 0, 0])
    arm = m.bone("arm", [0, 24, 8], "root")
    # 팔목 갑주 (뒤 +Z — 거인의 팔이 이어짐): 통 · 금 테 둘 · 빛 줄
    arm.box([-6, 19.5, 8], [12, 9, 13], metal.with_(seed=1360))
    arm.box([-6.5, 19, 9], [13, 10, 1.5], gold)
    arm.box([-6.5, 19, 18], [13, 10, 1.5], gold.with_(seed=1308))
    arm.box([-1, 28.4, 10.5], [2, 0.6, 7.5], glow)
    arm.box([-6.4, 23, 11], [0.6, 2, 6], glow.with_(seed=1361))
    arm.box([5.8, 23, 11], [0.6, 2, 6], glow.with_(seed=1362))
    palm = m.bone("palm", [0, 24, 7], "arm")
    # 손등 · 손바닥 (두툼하게) · 손등 갑주 판 · 왕관 룬 (핏빛 · 빛)
    palm.box([-8, 20, -6], [16, 8, 14], metal)
    palm.box([-8.5, 27.5, -6.5], [17, 2, 12], plate.with_(seed=1309))
    palm.box([-3, 29.3, -2], [6, 0.6, 5], claw.with_(seed=1310))
    palm.box([-1, 29.3, -3.5], [2, 0.6, 1.5], core)
    palm.box([-3.5, 29.3, 3.2], [7, 0.6, 1], gold.with_(seed=1311))
    for x in (-3.5, 0, 3.5):
        palm.box([x - 0.5, 29.6, 2.4], [1, 1.2, 1], gold.with_(seed=1312 + int(x)))
    # 손가락 넷: 뿌리 마디 (금 마디 테) · 끝 마디 · 핏빛 발톱 (빛)
    for i, x in enumerate(HD_FINGERS):
        f1 = m.bone(f"f{i}a", [x, 24, -6], "palm")
        f1.box([x - 1.75, 21, -14.5], [3.5, 6, 8.5], metal.with_(seed=1320 + i))
        f1.box([x - 2, 26.5, -13.5], [4, 1, 4], plate.with_(seed=1325 + i))
        f1.box([x - 2, 20.8, -7], [4, 6.4, 1], gold.with_(seed=1330 + i), inflate=0.05)
        f2 = m.bone(f"f{i}b", [x, 24, -14.5], f"f{i}a")
        f2.box([x - 1.5, 21.5, -22.5], [3, 5, 8], metal.with_(seed=1335 + i))
        f2.box([x - 1.5, 21.5, -15.5], [3, 5, 1], dark.with_(seed=1340 + i), inflate=0.05)
        f2.box([x - 1, 21.5, -26.5], [2, 3, 4], claw.with_(seed=1345 + i))
        f2.box([x - 0.5, 21.5, -28], [1, 1.5, 1.5], core.with_(seed=1350 + i))
    # 엄지 (−X 옆 — 앞 아래로 비스듬히)
    t1 = m.bone("thumbA", [-8, 23, 1], "palm", [0, -28, 0])
    t1.box([-14, 20.5, -1.5], [6, 5, 5], metal.with_(seed=1355))
    t1.box([-14.2, 25, -1.5], [6.4, 1, 5], plate.with_(seed=1356))
    t2 = m.bone("thumbB", [-14, 23, 1], "thumbA")
    t2.box([-19.5, 21, -1], [5.5, 4, 4], metal.with_(seed=1357))
    t2.box([-22.5, 21.5, -0.5], [3, 3, 3], claw.with_(seed=1358))
    return m


HD_BONES = ("root", "arm", "palm", "thumbA", "thumbB") + tuple(f"f{i}{p}" for i in range(4) for p in "ab")


def _hd_pose(kind):
    """손의 자세: spread = 활짝 (손가락이 조금 젖혀짐 · 벌어짐) · grip = 쥠 (손가락이 아래로 감김 — 뿌리 70° · 끝 75°) · crush = 꽉 쥠 (더 감김 · 손바닥이 내려앉음) ·
    slam = 쥔 채 앞으로 내리꽂음 (팔목이 앞으로 숙음)"""
    if kind == "spread":
        p = {"palm": [-6, 0, 0], "thumbA": [0, -18, -10], "thumbB": [0, -10, 0]}
        for i in range(4):
            p[f"f{i}a"] = [-14, (i - 1.5) * 7, 0]
            p[f"f{i}b"] = [-6, 0, 0]
        return p
    if kind in ("grip", "crush"):
        k = 1.0 if kind == "grip" else 1.18
        p = {"palm": [8 * k, 0, 0], "thumbA": [0, 26 * k, 34 * k], "thumbB": [0, 30 * k, 0]}
        for i in range(4):
            p[f"f{i}a"] = [70 * k, (i - 1.5) * -2, 0]
            p[f"f{i}b"] = [75 * k, 0, 0]
        if kind == "crush":
            p["root"] = [0, 0, 0]
        return p
    if kind == "slam":
        p = _hd_pose("crush")
        p["arm"] = [28, 0, 0]
        return p
    return {}


def _hd_move(times, poses, extra=None, length=None):
    bones = {}
    for b in HD_BONES:
        keys = [(t, (_hd_pose(p) if isinstance(p, str) else p).get(b, [0, 0, 0])) for t, p in zip(times, poses)]
        if any(v != [0, 0, 0] for _, v in keys):
            bones[b] = {"rotation": kf(keys)}
    for b, v in (extra or {}).items():
        bones.setdefault(b, {}).update(v)
    out = {"bones": bones}
    if length is not None:
        out["animation_length"] = length
    return out


def job_hand_anims():
    wob = f"math.sin({LT} * 90)"
    out = {
        # 늘 (작게): 손가락이 꿈틀 · 손이 떠 흔들림
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"{wob} * 0.6", 0]},
            "f0b": {"rotation": [f"math.sin({LT} * 120) * 4", 0, 0]},
            "f2b": {"rotation": [f"math.sin({LT} * 120 + 90) * 4", 0, 0]},
            "thumbB": {"rotation": [0, f"math.sin({LT} * 80) * 5", 0]},
        }},
        # 나타남 (0.3초 — 끝 = 활짝): 작게 커지며 손가락을 펼침
        "summon": dict(_hd_move([0, 0.3], [{}, "spread"], {"root": {"scale": kf([(0, [0.3, 0.3, 0.3]), (0.22, [1.06, 1.06, 1.06]), (0.3, [1, 1, 1])])}}, 0.3)),
        # 활짝 (이어 돎 — 손가락이 조금씩 오므렸다 폄)
        "spread": dict(_hd_move([0], ["spread"]), loop=True),
        # 움켜쥠 (0.18초 — 끝 자세를 지킴)
        "grab": _hd_move([0, 0.1, 0.18], ["spread", {k: [v[0] * 1.1, v[1], v[2]] for k, v in _hd_pose("grip").items()}, "grip"], length=0.18),
        # 쥔 채 (이어 돎 — 맥박)
        "hold": dict(_hd_move([0], ["grip"]), loop=True),
        # 쥐어짬 (0.3초 — 끝 = 쥔 자세): 꽉 쥐었다 놓음
        "squeeze": _hd_move([0, 0.08, 0.3], ["grip", "crush", "grip"], {"root": {"scale": kf([(0, [1, 1, 1]), (0.08, [1.05, 0.95, 1.05]), (0.3, [1, 1, 1])])}},
                            0.3),
        # 내리꽂음 (0.25초 — 끝 자세를 지킴): 팔목이 앞으로 숙으며 꽉 쥠
        "slam": _hd_move([0, 0.25], ["grip", "slam"], length=0.25),
        # 놓음 (0.3초): 손가락을 펼치며 흐려짐
        "release": _hd_move([0, 0.3], ["slam", "spread"], length=0.3),
        # 흩어짐 (0.4초): 위로 솟으며 작아짐
        "vanish": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 10, 0])]), "scale": kf([(0, [1, 1, 1]), (0.12, [1.08, 1.08, 1.08]), (0.4, [0.04, 0.04, 0.04])])},
        }},
    }
    return out


# ================================================================== 심연의 아가리 (2026-11-13 — 직업 도감 메모 다크 나이트 피스트 "방패 아가리 3d로" · 어비설 메이
# "아가리 3d로"): 저주받은 방패의 얼굴 — 검붉은 쇠 판 (핏빛 금) · 굽은 뿔 둘 · 붉은 눈 · 위아래 송곳니 · 붉게 빛나는 목구멍. 아래턱이 벌어짐 (뒤 경첩).
# 앞 = −Z (몹이 보는 쪽 — 물 적), 크기 1 에서 너비 약 1.4칸 · 높이 약 1.9칸 (뿔 포함). pit 동작은 뿌리를 젖혀 아가리가 하늘을 봄 (땅에서 솟는 아가리)

MW_IRON = (52, 20, 26)
MW_DARK = (22, 8, 12)
MW_BLOOD = (255, 56, 72)
MW_CORE = (255, 170, 160)
MW_BONE = (236, 226, 204)
MW_HORN = (30, 14, 16)


def job_maw():
    m = Model("job_maw", tex_w=128)
    iron = Mat(MW_IRON, "metal", noise=6, edge=0.82, seed=1401, accent=MW_DARK)
    iron2 = Mat((66, 26, 32), "metal", noise=5, edge=0.86, seed=1402, accent=MW_DARK)
    dark = Mat(MW_DARK, "flat", noise=3, edge=0.9, seed=1403)
    horn = Mat(MW_HORN, "chitin", noise=5, edge=0.85, seed=1404, band=2)
    crack = Mat(MW_BLOOD, "flat", noise=6, edge=1.0, glow=0.9, seed=1450)
    fang = Mat(MW_BONE, "bone", noise=3, edge=0.9, seed=1405)
    throat = Mat(MW_BLOOD, "skin", noise=10, edge=1.0, glow=0.95, seed=1406, accent=(150, 10, 30))
    core = Mat(MW_CORE, "flat", noise=2, edge=1.0, glow=1.0, seed=1407)
    eyes = [("north", x, y, MW_CORE if (x in (5, 16)) else MW_BLOOD, 1.0) for x in (3, 4, 5, 6, 15, 16, 17, 18) for y in (1, 2)]
    root = m.bone("root", [0, 0, 0])
    head = m.bone("head", [0, 14, 4], "root")
    # 위 얼굴 판 (눈썹 판 · 이마 · 볼 판) · 코 홈
    head.box([-10, 14, -4], [20, 13, 9], iron)
    head.box([-11, 25, -6], [22, 4, 6], iron2, decals=eyes)
    head.box([-12, 15, -5], [3, 10, 8], iron.with_(seed=1408))
    head.box([9, 15, -5], [3, 10, 8], iron.with_(seed=1409))
    head.box([-2, 20, -5], [4, 5, 1], dark)
    head.box([-1, 29, -5], [2, 2, 3], horn.with_(seed=1410))
    # 핏빛 금 (얼굴 판의 갈라진 틈 — 빛)
    head.box([-6.5, 17, -4.3], [0.6, 7, 0.4], crack)
    head.box([-6.5, 17, -4.3], [3, 0.6, 0.4], crack.with_(seed=1451))
    head.box([5.5, 15.5, -4.3], [0.6, 6, 0.4], crack.with_(seed=1452))
    head.box([3, 21, -4.3], [3, 0.6, 0.4], crack.with_(seed=1453))
    head.box([-11.6, 19, -5.3], [0.4, 5, 0.4], crack.with_(seed=1454))
    head.box([11.2, 17, -5.3], [0.4, 5, 0.4], crack.with_(seed=1455))
    # 위 송곳니 (앞 가장자리) · 입천장 (빛)
    for i, x in enumerate((-8, -5, -2, 1.5, 4.5, 7.5)):
        big = i in (0, 5)
        head.box([x - 0.5, 11 - (2 if big else 0), -4.5], [2 if big else 1.5, 3 + (2 if big else 0), 1.5], fang.with_(seed=1411 + i))
    head.box([-8.5, 13.4, -3.5], [17, 0.8, 7], throat)
    # 뿔 (굽은 셋 마디 — 옆 위로 뻗어 앞으로 휨)
    for side, sx in (("R", -1), ("L", 1)):
        h1 = m.bone("horn" + side + "1", [10 * sx, 26, 0], "head", [0, 0, -28 * sx])
        h1.box([10 * sx - 2 if sx > 0 else 10 * sx - 3, 26, -2], [5, 5, 4], horn.with_(seed=1420 + sx))
        h2 = m.bone("horn" + side + "2", [12 * sx, 30, 0], "horn" + side + "1", [-20, 0, 18 * sx])
        h2.box([12 * sx - 1.5 if sx > 0 else 12 * sx - 2.5, 30, -1.5], [4, 5, 3], horn.with_(seed=1422 + sx))
        h3 = m.bone("horn" + side + "3", [13 * sx, 34.5, -0.5], "horn" + side + "2", [-30, 0, 16 * sx])
        h3.box([13 * sx - 1 if sx > 0 else 13 * sx - 2, 34.5, -1], [3, 4, 2], horn.with_(seed=1424 + sx), faces={"up": core})
    # 아래턱 (뒤 경첩): 턱 판 · 아래 송곳니 · 혀 · 목구멍 (빛)
    jaw = m.bone("jaw", [0, 13, 4], "head")
    jaw.box([-9, 6, -4], [18, 6, 8], iron.with_(seed=1430), faces={"up": throat.with_(seed=1431)})
    jaw.box([-10, 7, -3], [2, 5, 6], iron2.with_(seed=1432))
    jaw.box([8, 7, -3], [2, 5, 6], iron2.with_(seed=1433))
    for i, x in enumerate((-7.5, -4.5, -1.5, 1.5, 4.5, 7)):
        big = i in (0, 5)
        jaw.box([x - 0.5, 12, -4.5], [2 if big else 1.5, 2 + (2 if big else 0), 1.5], fang.with_(seed=1434 + i))
    jaw.box([-4, 12, -2], [8, 1.2, 6], throat.with_(seed=1440))
    gul = m.bone("throat", [0, 12, 2], "head")
    gul.box([-7, 10, -1], [14, 4, 6], throat.with_(seed=1441))
    gul.box([-3, 11, 0], [6, 2, 4], core)
    return m


MW_BONES = ("root", "head", "jaw")


def _mw_move(times, poses, extra=None, length=None):
    bones = {}
    for b in MW_BONES:
        keys = [(t, p.get(b, [0, 0, 0])) for t, p in zip(times, poses)]
        if any(v != [0, 0, 0] for _, v in keys):
            bones[b] = {"rotation": kf(keys)}
    for b, v in (extra or {}).items():
        bones.setdefault(b, {}).update(v)
    out = {"bones": bones}
    if length is not None:
        out["animation_length"] = length
    return out


MW_SHUT = {}
MW_OPEN = {"head": [-14, 0, 0], "jaw": [52, 0, 0]}
MW_WIDE = {"head": [-24, 0, 0], "jaw": [72, 0, 0]}
MW_SNAP = {"head": [6, 0, 0], "jaw": [-4, 0, 0]}
# 땅에서 솟는 아가리 (뿌리를 젖혀 하늘을 봄 — 뿌리 둘레로 돎: 얼굴이 뿌리 위로 오게 뿌리를 옮김)
PIT = [-90, 0, 0]


def _pit(pose):
    p = dict(pose)
    p["root"] = PIT
    return p


def job_maw_anims():
    br = f"math.sin({LT} * 120)"
    out = {
        "idle": {"loop": True, "bones": {
            "head": {"rotation": [f"{br} * 1.5", 0, 0]},
            "jaw": {"rotation": [f"6 + math.max(0, math.sin({LT} * 90)) * 6", 0, 0]},
            "throat": {"scale": [f"1 + {br} * 0.05", f"1 + {br} * 0.08", 1]},
        }},
        # 나타남 (0.3초 — 끝 = 다묾): 그림자에서 커짐
        "summon": {"animation_length": 0.3, "bones": {"root": {"scale": kf([(0, [0.2, 0.2, 0.2]), (0.22, [1.08, 1.08, 1.08]), (0.3, [1, 1, 1])])}}},
        # 벌림 (0.25초 — 끝 자세를 지킴) · 크게 벌림 (0.3초)
        "open": _mw_move([0, 0.25], [MW_SHUT, MW_OPEN], length=0.25),
        "wide": _mw_move([0, 0.3], [MW_OPEN, MW_WIDE], length=0.3),
        # 콱 다묾 (0.12초 — 끝 자세를 지킴)
        "snap": _mw_move([0, 0.06, 0.12], [MW_WIDE, MW_SNAP, MW_SHUT], {"root": {"scale": kf([(0, [1, 1, 1]), (0.06, [1.08, 0.94, 1.08]), (0.12, [1, 1, 1])])}}, 0.12),
        # 짓씹음 (이어 돎 0.35초)
        "chew": dict(_mw_move([0, 0.17, 0.35], [MW_SHUT, {"head": [-6, 0, 0], "jaw": [26, 0, 0]}, MW_SHUT]), loop=True),
        # 포효 (0.5초): 크게 벌리고 흔듦
        "roar": _mw_move([0, 0.15, 0.4, 0.5], [MW_SHUT, MW_WIDE, {"head": [-30, 6, 0], "jaw": [78, 0, 0]}, MW_OPEN], length=0.5),
        # 땅에서 솟는 아가리 (하늘을 봄): 솟으며 크게 벌림 (0.4초) · 콱 다묾 · 짓씹음 (이어 돎)
        "pit_rise": _mw_move([0, 0.4], [_pit(MW_SHUT), _pit(MW_WIDE)], {"root": {"position": kf([(0, [0, -26, 6]), (0.4, [0, 0, 6])])}}, 0.4),
        "pit_wide": dict(_mw_move([0], [_pit(MW_WIDE)], {"root": {"position": [0, 0, 6]}}), loop=True),
        "pit_snap": _mw_move([0, 0.06, 0.12], [_pit(MW_WIDE), _pit(MW_SNAP), _pit(MW_SHUT)], {"root": {"position": [0, 0, 6]}}, 0.12),
        "pit_chew": dict(_mw_move([0, 0.15, 0.3], [_pit(MW_SHUT), _pit({"head": [-6, 0, 0], "jaw": [24, 0, 0]}), _pit(MW_SHUT)], {"root": {"position": [0, 0, 6]}}),
                         loop=True),
        "pit_sink": {"animation_length": 0.5, "bones": {
            "root": {"rotation": [-90, 0, 0], "position": kf([(0, [0, 0, 6]), (0.5, [0, -30, 6])]),
                     "scale": kf([(0, [1, 1, 1]), (0.1, [1.1, 1.1, 1.1]), (0.5, [0.6, 0.6, 0.6])])}}},
        # 흩어짐 (0.35초): 작아지며 가라앉음
        "vanish": {"animation_length": 0.35, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.35, [0, -6, 0])]), "scale": kf([(0, [1, 1, 1]), (0.1, [1.06, 1.06, 1.06]), (0.35, [0.04, 0.04, 0.04])])}}},
    }
    return out


# ================================================================== 금서 (2026-11-13 — 직업 도감 메모 매지션 포비든 그리모어 "책 연출을 3d로"): 떠 있는 거대한 마도서 —
# 남색 가죽 표지 (금 모서리 · 겉의 마력의 눈 보석) · 금 등 · 하얀 책장 (빛나는 룬 글자 줄) · 넘어가는 책장 셋. 등은 세로 (x 0), 펼치면 책장이 앞 (−Z) 을 봄.
# 크기 1 에서 펼친 너비 약 2칸 · 높이 1.25칸 (가운데 높이 16 픽셀)

TM_LEATHER = (30, 34, 92)
TM_DARK = (16, 18, 52)
TM_GOLD = (236, 196, 96)
TM_PAGE = (246, 238, 212)
TM_RUNE = (120, 200, 255)
TM_CORE = (226, 244, 255)


def job_tome():
    m = Model("job_tome", tex_w=128)
    leather = Mat(TM_LEATHER, "skin", noise=6, edge=0.84, seed=1501, accent=TM_DARK)
    gold = Mat(TM_GOLD, "metal", noise=4, edge=0.9, seed=1502)
    page = Mat(TM_PAGE, "flat", noise=4, edge=0.95, seed=1503)
    edges = Mat(TM_PAGE, "chitin", noise=3, edge=0.92, seed=1508, band=2)
    rune = Mat(TM_RUNE, "flat", noise=6, edge=1.0, glow=0.9, seed=1504)
    core = Mat(TM_CORE, "flat", noise=2, edge=1.0, glow=1.0, seed=1505)
    gem = Mat((150, 90, 255), "crystal", noise=4, edge=1.0, glow=0.8, seed=1506)
    root = m.bone("root", [0, 0, 0])
    book = m.bone("book", [0, 16, 0], "root")
    book.box([-1.5, 5.5, -0.5], [3, 21, 3], gold)
    book.box([-1, 6, -1.2], [2, 20, 0.8], leather.with_(seed=1507))
    for side, sx in (("L", -1), ("R", 1)):
        half = m.bone("half" + side, [0, 16, 0], "book")
        x0 = -16 if sx < 0 else 0
        # 표지 (뒤) · 금 모서리 · 겉의 보석 · 책장 덩이 (앞) · 룬 줄 (빛)
        half.box([x0, 5.5, 0.5], [16, 21, 1.5], leather.with_(seed=1510 + sx))
        for cy in (5.3, 25.2):
            half.box([x0 + (13 if sx > 0 else 0), cy, 0.3], [3, 1.5, 1.9], gold.with_(seed=1512 + sx + int(cy)))
        half.box([x0 + (0 if sx > 0 else 15.5), 5.5, 0.3], [0.5, 21, 1.9], gold.with_(seed=1515 + sx))
        half.box([x0 + 5.5, 13, 2.0], [5, 5, 0.6], gem.with_(seed=1517 + sx))
        half.box([x0 + 6.5, 14, 2.4], [3, 3, 0.4], core.with_(seed=1519 + sx))
        px = -15 if sx < 0 else 0.5
        half.box([px, 6.5, -1.5], [14.5, 19, 2], page.with_(seed=1520 + sx), faces={"east": edges, "west": edges, "up": edges, "down": edges})
        for i, ry in enumerate((8.5, 11.5, 14.5, 17.5, 20.5)):
            half.box([px + 2, ry, -1.8], [10.5 - (i % 2) * 3, 1.3, 0.4], rune.with_(seed=1522 + i + sx * 10))
        half.box([px + 4.5, 22.6, -1.75], [5.5, 2, 0.35], core.with_(seed=1527 + sx))
    # 넘어가는 책장 셋 (오른쪽 반에 붙어 — 등 앞 경첩에서 왼쪽으로 넘어감, 닫히면 함께 접힘)
    for i in range(3):
        pg = m.bone(f"page{i}", [0, 16, -1.6 - i * 0.15], "halfR")
        z = -1.75 - i * 0.15
        pg.box([0.3, 7, z], [14, 18, 0.3], page.with_(seed=1530 + i))
        for q, ry in enumerate((10, 14, 18)):
            pg.box([2.3, ry, z - 0.15], [9.5 - q * 2, 0.7, 0.2], rune.with_(seed=1535 + i * 3 + q))
    return m


TM_OPEN = {"halfL": [0, 0, 0], "halfR": [0, 0, 0]}
TM_SHUT = {"halfL": [0, -86, 0], "halfR": [0, 86, 0]}


def job_tome_anims():
    bob = f"math.sin({LT} * 80)"
    flip = {}
    for i in range(3):
        t0 = i * 0.2
        flip[f"page{i}"] = {"rotation": kf([(t0, [0, 0, 0]), (t0 + 0.3, [0, 178, 0]), (0.9, [0, 178, 0])])}
    out = {
        # 늘 (작게): 떠서 흔들림 · 앞으로 조금 젖힘 (책장이 위를 봄)
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"{bob} * 0.8", 0]},
            "book": {"rotation": [f"-18 + {bob} * 2", f"math.sin({LT} * 50) * 4", 0]},
        }},
        # 닫힌 채 (이어 돎)
        "shut": {"loop": True, "bones": {b: {"rotation": v} for b, v in TM_SHUT.items()}},
        # 펼침 (0.5초 — 끝 자세를 지킴): 닫힌 책이 커지며 활짝
        "open": {"animation_length": 0.5, "bones": {
            "halfL": {"rotation": kf([(0, TM_SHUT["halfL"]), (0.35, [0, 8, 0]), (0.5, [0, 0, 0])])},
            "halfR": {"rotation": kf([(0, TM_SHUT["halfR"]), (0.35, [0, -8, 0]), (0.5, [0, 0, 0])])},
            "root": {"scale": kf([(0, [0.3, 0.3, 0.3]), (0.35, [1.06, 1.06, 1.06]), (0.5, [1, 1, 1])])},
        }},
        # 책장이 넘어감 (이어 돎 0.9초 — 셋이 차례로)
        "flip": {"loop": True, "animation_length": 0.9, "bones": flip},
        # 탁 닫힘 (0.2초 — 끝 자세를 지킴)
        "close": {"animation_length": 0.2, "bones": {
            "halfL": {"rotation": kf([(0, [0, 0, 0]), (0.14, [0, -92, 0]), (0.2, TM_SHUT["halfL"])])},
            "halfR": {"rotation": kf([(0, [0, 0, 0]), (0.14, [0, 92, 0]), (0.2, TM_SHUT["halfR"])])},
            "root": {"scale": kf([(0, [1, 1, 1]), (0.14, [1.15, 1.15, 1.15]), (0.2, [1.05, 1.05, 1.05])])},
        }},
        # 흩어짐 (0.4초)
        "vanish": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 6, 0])]), "scale": kf([(0, [1.05, 1.05, 1.05]), (0.12, [1.15, 1.15, 1.15]), (0.4, [0.04, 0.04, 0.04])])},
        }},
    }
    return out


# ================================================================== 푸른 불사조 (2026-11-13 — 직업 도감 메모 피닉스 가드 피닉스 디센트 "피닉스 3d로 연출"): 푸른 불의 새 —
# 하늘빛 불꽃 깃 (빛) · 흰 심지의 가슴 · 볏 셋 · 금빛 부리 · 넓은 날개 (속 날개 · 겉 날개 · 끝 깃 넷) · 부채꼴 꼬리 깃 다섯. 앞 = −Z, 크기 1 에서 날개 끝 사이 약 5.6칸

PX_FIRE = (70, 158, 255)
PX_DEEP = (34, 92, 220)
PX_CORE = (226, 244, 255)
PX_GOLD = (255, 214, 120)
PX_DARK = (18, 40, 110)


def job_phoenix():
    m = Model("job_phoenix", tex_w=128)
    fire = Mat(PX_FIRE, "scales", noise=10, edge=0.95, glow=0.85, seed=1601, accent=PX_DEEP)
    deep = Mat(PX_DEEP, "scales", noise=8, edge=0.92, glow=0.7, seed=1602, accent=PX_DARK)
    core = Mat(PX_CORE, "flat", noise=4, edge=1.0, glow=1.0, seed=1603)
    gold = Mat(PX_GOLD, "metal", noise=4, edge=0.95, glow=0.6, seed=1604)
    eye = [("north", 1, 1, PX_GOLD, 1.0), ("north", 4, 1, PX_GOLD, 1.0)]
    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 20, 0], "root")
    # 몸 (가슴이 앞) · 가슴의 흰 불 · 등
    body.box([-4.5, 16, -6], [9, 8, 13], fire)
    body.box([-3.5, 15.5, -7], [7, 6, 6], core.with_(seed=1605))
    body.box([-3, 23.5, -4], [6, 1.5, 10], deep.with_(seed=1606))
    neck = m.bone("neck", [0, 22, -6], "body")
    neck.box([-2.5, 21, -11], [5, 5, 6], fire.with_(seed=1607))
    head = m.bone("head", [0, 24, -11], "neck")
    head.box([-3, 22.5, -16], [6, 5, 5.5], fire.with_(seed=1608), decals=eye)
    head.box([-1, 23, -19.5], [2, 2.5, 3.5], gold)
    head.box([-0.5, 22.6, -20.8], [1, 1.5, 1.3], gold.with_(seed=1609))
    # 볏 셋 (뒤로 휘어 솟음)
    for i, (dx, h) in enumerate(((-1.5, 6), (0, 8), (1.5, 6))):
        cr = m.bone(f"crest{i}", [dx, 27.5, -13], "head", [-35 - i * 5, 0, (i - 1) * 10])
        cr.box([dx - 0.5, 27.5, -13.5], [1, h, 1.5], core.with_(seed=1610 + i))
    # 날개 (속 · 겉 · 끝 깃 넷 — 뒤 (+Z) 로 비스듬히)
    for side, sx in (("R", -1), ("L", 1)):
        w1 = m.bone("wing" + side + "1", [4 * sx, 23, -2], "body")
        x1 = -22 if sx < 0 else 4
        w1.box([x1, 22, -4], [18, 2, 11], fire.with_(seed=1620 + sx))
        w1.box([x1, 21.5, 5], [18, 1, 4], deep.with_(seed=1622 + sx))
        w2 = m.bone("wing" + side + "2", [22 * sx, 23, -2], "wing" + side + "1")
        x2 = -40 if sx < 0 else 22
        w2.box([x2, 22.3, -3], [18, 1.5, 9], fire.with_(seed=1624 + sx))
        w2.box([x2, 22, 4], [18, 1, 3], deep.with_(seed=1626 + sx))
        for q in range(4):
            fx = (-40 - q * 1.5) if sx < 0 else (40 + q * 1.5 - 12)
            fw = m.bone(f"feather{side}{q}", [(40 if sx > 0 else -40) + 0 * q, 23, -1 + q * 2.5], "wing" + side + "2", [0, sx * (8 + q * 9), 0])
            fw.box([fx if sx > 0 else fx - 0, 22.6, -2 + q * 2.5], [12, 0.8, 2.4], (core if q == 0 else fire).with_(seed=1630 + q + sx * 5))
    # 꼬리 깃 다섯 (부채꼴 — 뒤로)
    tail = m.bone("tail", [0, 19, 7], "body")
    for q in range(5):
        tq = m.bone(f"tail{q}", [0, 19, 7], "tail", [0, (q - 2) * 16, 0])
        tq.box([-1.5, 18.5, 7], [3, 1, 16 + (2 - abs(q - 2)) * 3], (core if q == 2 else fire).with_(seed=1640 + q))
        tq.box([-2, 18.3, 19 + (2 - abs(q - 2)) * 3], [4, 1.2, 4], deep.with_(seed=1646 + q))
    return m


PX_WINGS = ("wingR1", "wingR2", "wingL1", "wingL2")


def _px_wing(r1, r2):
    """날개 각 (속 · 겉 — Z 둘레, 오른쪽은 반대 부호)"""
    return {"wingR1": [0, 0, -r1], "wingR2": [0, 0, -r2], "wingL1": [0, 0, r1], "wingL2": [0, 0, r2]}


def job_phoenix_anims():
    flap1 = f"math.sin({LT} * 720) * 34"
    flap2 = f"math.sin({LT} * 720 - 50) * 22"
    out = {
        # 늘 (작게): 몸이 오르내림 · 꼬리 깃이 흔들림 · 볏
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 120) * 0.8", 0]},
            "tail": {"rotation": [f"math.sin({LT} * 160) * 6", f"math.sin({LT} * 90) * 8", 0]},
            "head": {"rotation": [f"math.sin({LT} * 70) * 4", 0, 0]},
        }},
        # 날갯짓 (이어 돎 — 0.5초)
        "fly": {"loop": True, "bones": {
            "wingR1": {"rotation": [0, 0, f"-({flap1})"]}, "wingL1": {"rotation": [0, 0, flap1]},
            "wingR2": {"rotation": [0, 0, f"-({flap2})"]}, "wingL2": {"rotation": [0, 0, flap2]},
            "body": {"position": [0, f"math.sin({LT} * 720 + 90) * 1.2", 0]},
        }},
        # 미끄러짐 (이어 돎): 날개를 펴고 조금 기울임
        "glide": {"loop": True, "bones": dict({b: {"rotation": v} for b, v in _px_wing(-6, 4).items()},
                                              **{"root": {"rotation": [0, 0, f"math.sin({LT} * 60) * 10"]}})},
        # 깨어남 (0.6초 — 끝 자세를 지킴): 불길 속에서 커지며 날개를 활짝 · 고개를 젖혀 울음
        "summon": {"animation_length": 0.6, "bones": {
            "root": {"scale": kf([(0, [0.2, 0.2, 0.2]), (0.4, [1.08, 1.08, 1.08]), (0.6, [1, 1, 1])])},
            "wingR1": {"rotation": kf([(0, [0, 0, 70]), (0.4, [0, 0, -30]), (0.6, [0, 0, -14])])},
            "wingL1": {"rotation": kf([(0, [0, 0, -70]), (0.4, [0, 0, 30]), (0.6, [0, 0, 14])])},
            "wingR2": {"rotation": kf([(0, [0, 0, 60]), (0.4, [0, 0, -10]), (0.6, [0, 0, -6])])},
            "wingL2": {"rotation": kf([(0, [0, 0, -60]), (0.4, [0, 0, 10]), (0.6, [0, 0, 6])])},
            "neck": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.6, [-20, 0, 0])])},
        }},
        # 내리꽂음 (0.25초 — 끝 자세를 지킴): 머리를 아래로 · 날개를 위 뒤로 모아 V
        "dive": {"animation_length": 0.25, "bones": dict({b: {"rotation": kf([(0, [0, 0, 0]), (0.25, v)])} for b, v in
                                                          {"wingR1": [0, -40, -60], "wingL1": [0, 40, 60], "wingR2": [0, 0, -30], "wingL2": [0, 0, 30]}.items()},
                                                         **{"root": {"rotation": kf([(0, [0, 0, 0]), (0.25, [68, 0, 0])])},
                                                            "neck": {"rotation": kf([(0, [0, 0, 0]), (0.25, [10, 0, 0])])}})},
        # 땅에 닿아 날개를 활짝 펴며 터짐 (0.5초): 커지며 흩어짐
        "burst": {"animation_length": 0.5, "bones": {
            "root": {"rotation": kf([(0, [40, 0, 0]), (0.15, [0, 0, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.15, [1.35, 1.35, 1.35]), (0.5, [0.05, 0.05, 0.05])])},
            "wingR1": {"rotation": kf([(0, [0, 0, -50]), (0.15, [0, 0, 20])])}, "wingL1": {"rotation": kf([(0, [0, 0, 50]), (0.15, [0, 0, -20])])},
        }},
        "vanish": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 10, 0])]), "scale": kf([(0, [1, 1, 1]), (0.12, [1.1, 1.1, 1.1]), (0.4, [0.04, 0.04, 0.04])])}}},
    }
    return out


R.MOBS["job_dragon"] = (job_dragon, job_dragon_anims, dict(fam="job_dragon", health=40, move=0.4, box=[1.6, 1.8], fly=True), "entity_emissive", ["idle"], None)
R.MOBS["job_reaper"] = (job_reaper, job_reaper_anims, dict(fam="job_reaper", health=40, move=0.4, box=[1.0, 2.6], fly=True), "entity_alphablend", ["idle"], True)
R.PREVIEW_POSES["job_reaper"] = ((["idle"], 0.0), (["rise"], 0.3), (["idle", "swing_a"], 0.08), (["idle", "swing_a"], 0.15), (["idle", "swing_b"], 0.15),
                                 (["idle", "spin2"], 0.15), (["idle", "spin2"], 0.35), (["idle", "reap"], 0.25), (["idle", "reap"], 0.33),
                                 (["idle", "reap"], 0.4), (["idle", "vanish"], 0.3))
R.MOBS["job_bear"] = (job_bear, job_bear_anims, dict(fam="job_bear", health=40, move=0.3, box=[1.8, 2.0]), "entity_emissive", ["idle"], None)
R.PREVIEW_POSES["job_bear"] = ((["idle"], 0.0), (["idle", "rear"], 0.45), (["idle", "roar"], 0.55), (["idle", "slam"], 0.12), (["idle", "slam"], 0.25),
                               (["idle", "roarslam"], 0.12), (["idle", "growl"], 0.3),
                               (["idle", "rise_wide"], 0.25), (["idle", "hug"], 0.35), (["idle", "hugslam"], 0.25), (["curl"], 0.0), (["idle", "wake"], 0.25),
                               (["summon"], 0.25), (["idle", "vanish"], 0.3))
R.PREVIEW_POSES["job_dragon"] = ((["idle"], 0.0), (["idle", "fly"], 0.15), (["idle", "fly"], 0.45), (["idle", "roar"], 0.6), (["idle", "inhale"], 0.4),
                                 (["idle", "breathe"], 0.2), (["idle", "dive"], 0.25), (["idle", "crash"], 0.5), (["summon"], 0.2),
                                 (["idle", "launch"], 0.1), (["idle", "launch"], 0.18), (["idle", "spin"], 0.2), (["idle", "bite"], 0.1),
                                 (["idle", "bite"], 0.3), (["idle", "rear"], 0.5), (["idle", "slam"], 0.15), (["idle", "stomp"], 0.1), (["idle", "stomp"], 0.2))
R.MOBS["job_hand"] = (job_hand, job_hand_anims, dict(fam="job_hand", health=40, move=0.4, box=[1.6, 1.8], fly=True), "entity_alphablend", ["idle"], True)
R.PREVIEW_POSES["job_hand"] = ((["idle"], 0.0), (["idle", "spread"], 0.0), (["idle", "grab"], 0.1), (["idle", "grab"], 0.18), (["idle", "squeeze"], 0.08),
                               (["idle", "slam"], 0.25), (["idle", "release"], 0.3), (["summon"], 0.15), (["idle", "vanish"], 0.2))
R.MOBS["job_maw"] = (job_maw, job_maw_anims, dict(fam="job_maw", health=40, move=0.4, box=[1.4, 1.8], fly=True), "entity_emissive", ["idle"], None)
R.PREVIEW_POSES["job_maw"] = ((["idle"], 0.0), (["idle", "open"], 0.25), (["idle", "wide"], 0.3), (["idle", "snap"], 0.12), (["idle", "roar"], 0.4),
                              (["idle", "pit_rise"], 0.2), (["idle", "pit_wide"], 0.0), (["idle", "pit_snap"], 0.12), (["idle", "pit_chew"], 0.15))
R.MOBS["job_tome"] = (job_tome, job_tome_anims, dict(fam="job_tome", health=40, move=0.4, box=[1.6, 1.4], fly=True), "entity_emissive", ["idle"], None)
R.PREVIEW_POSES["job_tome"] = ((["idle"], 0.0), (["idle", "shut"], 0.0), (["idle", "open"], 0.3), (["idle", "flip"], 0.15), (["idle", "flip"], 0.45),
                               (["idle", "close"], 0.2))
R.MOBS["job_phoenix"] = (job_phoenix, job_phoenix_anims, dict(fam="job_phoenix", health=40, move=0.4, box=[1.6, 1.6], fly=True), "entity_emissive", ["idle"], None)
R.PREVIEW_POSES["job_phoenix"] = ((["idle"], 0.0), (["idle", "fly"], 0.1), (["idle", "fly"], 0.3), (["idle", "glide"], 0.0), (["summon"], 0.5),
                                  (["idle", "dive"], 0.25), (["idle", "burst"], 0.12))
JOB_MOBS = ("job_dragon", "job_bear", "job_reaper", "job_hand", "job_maw", "job_tome", "job_phoenix")


def main():
    args = sys.argv[1:]
    keys = [k for k in args if k in JOB_MOBS] or list(JOB_MOBS)
    rest = [a for a in args if a not in R.MOBS]
    sys.argv = [sys.argv[0]] + rest + keys
    R.main()


if __name__ == "__main__":
    main()
