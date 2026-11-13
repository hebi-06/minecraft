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


R.MOBS["job_dragon"] = (job_dragon, job_dragon_anims, dict(fam="job_dragon", health=40, move=0.4, box=[1.6, 1.8], fly=True), "entity_emissive", ["idle"], None)
R.PREVIEW_POSES["job_dragon"] = ((["idle"], 0.0), (["idle", "fly"], 0.15), (["idle", "fly"], 0.45), (["idle", "roar"], 0.6), (["idle", "inhale"], 0.4),
                                 (["idle", "breathe"], 0.2), (["idle", "dive"], 0.25), (["idle", "crash"], 0.5), (["summon"], 0.2),
                                 (["idle", "launch"], 0.1), (["idle", "launch"], 0.18), (["idle", "spin"], 0.2), (["idle", "bite"], 0.1),
                                 (["idle", "bite"], 0.3), (["idle", "rear"], 0.5), (["idle", "slam"], 0.15), (["idle", "stomp"], 0.1), (["idle", "stomp"], 0.2))
JOB_MOBS = ("job_dragon",)


def main():
    args = sys.argv[1:]
    keys = [k for k in args if k in JOB_MOBS] or list(JOB_MOBS)
    rest = [a for a in args if a not in R.MOBS]
    sys.argv = [sys.argv[0]] + rest + keys
    R.main()


if __name__ == "__main__":
    main()
