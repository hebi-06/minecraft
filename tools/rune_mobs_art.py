#!/usr/bin/env python3
"""2026-11-12 룬 스킬의 3D 몹 (사용자 "스킬에 있는 몹도 지금" — 룬 도감 메모: 매 3d · 늑대 3d · 야수왕 (네크로맨서 몹처럼 3D 몹 · 할퀴는 모션) ·
운디네 · 실프 · 노움 · 정령왕 3D 몹). 불의 정령 샐러맨더 (tools/spirit_salamander_art.py) 와 같은 길: tools/monster_kit.py 로 모델 · 텍스처 · 동작을 짜고
행동 (mmorpg/entities.json — allies 무리) · 그리기 (assets/mmorpg/bedrock/entities.json) · 텍스처 (assets/mmorpg/textures/entity/rpg/allies) 에 넣음.
늑대 무리는 이미 있는 늑대 정령 (rpg:spirit_wolf — 서리 늑대 모델 · 정령빛) 을 씀.
모드 없는 클라이언트의 뼈 모델은 tools/vanilla_models.py 를 다시 돌려 만듦 (뼈마다 아이템 표시).

  python3 tools/rune_mobs_art.py                       # 모두 쓰고 게임 자원에 넣음
  python3 tools/rune_mobs_art.py --preview 폴더 [키…]   # 확인용 그림 (자세마다)

몹 (키 → 엔티티 rpg:<키>):
- rune_hawk       매 (사냥꾼 희귀 — 매의 습격): 영혼의 매 — 짙은 깃 · 흰 가슴 · 날개 끝 정령빛, 동작 fly · dive · summon · vanish
- rune_beast_king 야수왕 (사냥꾼 초월): 왕관 뿔 · 갈기 · 정령빛 핏줄의 거대한 늑대왕, 동작 idle · walk · claw (일어서며 앞발로 할큄) · roar · summon · vanish
- rune_undine     운디네 (정령 희귀): 물의 정령 — 물결 머리칼 · 아래는 소용돌이 물줄기 (비침), 동작 idle · attack (두 손을 내밀어 물줄기) · summon · vanish
- rune_sylph      실프 (정령 고귀): 바람의 요정 — 잠자리 날개 넷 (비침), 동작 idle · attack (돌진 자세) · summon · vanish
- rune_gnome      노움 (정령 영웅): 땅의 정령 — 돌 모자 · 수염 · 돌망치, 동작 idle · walk · attack (망치로 땅을 내리침 — 바위 가시) · summon · vanish
- rune_spirit_king 정령왕 (정령 초월): 왕관 · 긴 옷 · 네 원소 보석, 동작 idle · cast (두 팔을 들어 올림) · summon · vanish
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from monster_kit import Mat, Model, anim_file, kf, write_json  # noqa: E402

_MAT_WITH = Mat.with_


def _with_keep_alpha(self, **kw):
    """Mat.with_ 이 비치는 값 (alpha_k — 이 파일의 see) 도 옮기게"""
    m = _MAT_WITH(self, **kw)
    if hasattr(self, "alpha_k") and "alpha_k" not in kw:
        m.alpha_k = self.alpha_k
    return m


Mat.with_ = _with_keep_alpha

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "monster_art")
RES = os.path.join(ROOT, "src", "main", "resources")
BEHAVIOR = os.path.join(RES, "mmorpg", "entities.json")
CLIENT = os.path.join(RES, "assets", "mmorpg", "bedrock", "entities.json")
TEX = os.path.join(RES, "assets", "mmorpg", "textures", "entity", "rpg", "allies")

LT = "q.life_time"
MS = "q.modified_move_speed"
DM = "q.modified_distance_moved"


def walk_amt(k=3):
    return f"math.min({MS} * {k}, 1)"


def sinl(speed, amp, phase=0, base=0):
    """q.life_time 의 사인 (도/초 speed · 크기 amp)"""
    ph = f" + {phase}" if phase else ""
    b = f"{base} + " if base else ""
    return f"{b}math.sin({LT} * {speed}{ph}) * {amp}"


# ================================================================== 매 (사냥꾼 희귀)

HAWK_BACK = (92, 70, 40)
HAWK_WING = (76, 62, 36)
HAWK_CHEST = (236, 226, 204)
HAWK_SPIRIT = (150, 230, 90)
HAWK_GOLD = (255, 206, 72)


def rune_hawk():
    m = Model("rune_hawk", tex_w=128)
    back = Mat(HAWK_BACK, "plates", noise=6, edge=0.82, band=2, seed=401, accent=(52, 36, 22))
    wing = Mat(HAWK_WING, "plates", noise=5, edge=0.8, band=2, seed=402, accent=(40, 28, 18), seam=HAWK_SPIRIT, seam_glow=0.55, seam_band=3, seam_dir="v",
               seam_gap=0.55)
    prim = Mat((48, 34, 24), "bristle", noise=5, edge=0.8, seed=403, accent=(30, 22, 16), rim=HAWK_SPIRIT, rim_glow=0.95)
    chest = Mat(HAWK_CHEST, "bristle", noise=5, edge=0.9, seed=404, accent=(160, 120, 80))
    beak = Mat(HAWK_GOLD, "flat", noise=4, edge=0.85, seed=405, accent=(200, 140, 40))
    talon = Mat((40, 32, 26), "flat", noise=3, edge=0.9, seed=406)
    leg = Mat((222, 178, 70), "scales", noise=4, edge=0.85, seed=407)
    spirit = Mat(HAWK_SPIRIT, "flat", noise=8, edge=1.0, glow=0.85, seed=408)

    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 8, 0], "root")
    # 몸통 (앞 −z): 등 · 날개 어깨 (짙은 깃) · 가슴 (흰 바탕 · 갈색 줄)
    body.box([-2.5, 6, -4], [5, 4, 9], back, faces={"down": chest})
    body.box([-2, 5, -4.5], [4, 2, 5], chest)
    body.box([-1.5, 10, -3], [3, 1, 6], back.with_(seed=409))  # 등의 볏
    head = m.bone("head", [0, 9.5, -4], "body")
    head.box([-2, 8, -8], [4, 4, 4], back.with_(seed=410), faces={"down": chest.with_(seed=411)}, decals=[
        ("west", 1, 1, HAWK_GOLD, 1.0), ("east", 2, 1, HAWK_GOLD, 1.0), ("west", 2, 1, (20, 12, 6)), ("east", 1, 1, (20, 12, 6))])
    head.box([-2.5, 11, -7.5], [5, 1, 3], spirit)  # 눈썹 위 정령빛 (매섭게)
    head.box([-1, 8.5, -10], [2, 2, 2], beak)
    head.box([-0.5, 7.5, -10.5], [1, 1, 1], beak.with_(seed=412, base=(190, 130, 30)))  # 굽은 부리 끝
    # 날개 (오른쪽 = −x): 안쪽 · 바깥 (따로 접힘) · 끝 깃 셋 (정령빛 테두리)
    for side, sx in (("R", -1), ("L", 1)):
        w1 = m.bone("wing" + side, [2.5 * sx, 9, -1], "body")
        x0 = -8.5 if sx < 0 else 2.5
        w1.box([x0, 8.5, -2.5], [6, 1, 7], wing.with_(seed=413 + sx))
        w1.box([x0 + (0 if sx < 0 else 0), 8, 3.5], [6, 1, 2], prim.with_(seed=415 + sx))
        w2 = m.bone("wing" + side + "2", [8.5 * sx, 9, -1], "wing" + side)
        x1 = -14.5 if sx < 0 else 8.5
        w2.box([x1, 8.5, -2], [6, 1, 6], wing.with_(seed=417 + sx))
        for i in range(3):
            fx = (-16.5 + i * 2) if sx < 0 else (14.5 - i * 2)  # 끝 깃 (바깥부터 — 좌우 거울)
            w2.box([fx, 8.6, -1.5 + i], [2, 1, 6 - i], prim.with_(seed=419 + i + sx))
        w2.box([(x1 - 3) if sx < 0 else (x1 + 6), 8.6, -1.5], [3, 1, 4], prim.with_(seed=423 + sx))
    # 꼬리 (부채꼴 · 끝 정령빛)
    tail = m.bone("tail", [0, 8.5, 5], "body")
    tail.box([-2.5, 8, 5], [5, 1, 5], wing.with_(seed=425))
    tail.box([-3, 8, 10], [6, 1, 2], prim.with_(seed=426))
    # 다리 · 발톱 (덮칠 때 앞으로)
    for side, sx in (("R", -1), ("L", 1)):
        lg = m.bone("leg" + side, [1.2 * sx, 6, 0], "body")
        lg.box([1.2 * sx - 0.5, 3.5, -0.5], [1, 3, 1], leg)
        lg.box([1.2 * sx - 1, 2.5, -1.5], [2, 1, 2], talon)
    return m


def rune_hawk_anims():
    flap = f"math.sin({LT} * 720)"
    return {
        # 날갯짓 (늘): 안쪽 날개 ±32° · 바깥은 늦게 따라 · 몸이 위아래
        "fly": {"loop": True, "bones": {
            "body": {"position": [0, f"{flap} * -0.6", 0], "rotation": [f"{sinl(180, 3)} + 6", 0, 0]},
            "wingR": {"rotation": [0, 0, f"{flap} * -32"]},
            "wingL": {"rotation": [0, 0, f"{flap} * 32"]},
            "wingR2": {"rotation": [0, 0, f"math.sin({LT} * 720 - 50) * -22"]},
            "wingL2": {"rotation": [0, 0, f"math.sin({LT} * 720 - 50) * 22"]},
            "tail": {"rotation": [f"{sinl(360, 6)}", 0, 0]},
            "head": {"rotation": [f"{sinl(200, 4)}", f"{sinl(90, 10)}", 0]},
            "legR": {"rotation": [40, 0, 0]},
            "legL": {"rotation": [40, 0, 0]},
        }},
        # 내리꽂기 (0.5초): 날개를 뒤로 접고 (0.1) 발톱을 앞으로 (0.25 — 서버 HAWK_STRIKE_AT 5틱) → 날개를 펴며 솟음
        "dive": {"animation_length": 0.5, "bones": {
            "wingR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, -50, -12]), (0.25, [0, -55, -10]), (0.4, [0, 0, -40]), (0.5, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 50, 12]), (0.25, [0, 55, 10]), (0.4, [0, 0, 40]), (0.5, [0, 0, 0])])},
            "legR": {"rotation": kf([(0, [40, 0, 0]), (0.15, [-30, 0, 0]), (0.25, [-70, 0, 0]), (0.4, [20, 0, 0]), (0.5, [40, 0, 0])])},
            "legL": {"rotation": kf([(0, [40, 0, 0]), (0.15, [-30, 0, 0]), (0.25, [-70, 0, 0]), (0.4, [20, 0, 0]), (0.5, [40, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [18, 0, 0]), (0.5, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.2, [24, 0, 0]), (0.35, [-20, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        "summon": {"animation_length": 0.4, "bones": {
            "root": {"scale": kf([(0, [0.2, 0.2, 0.2]), (0.25, [1.1, 1.1, 1.1]), (0.4, [1, 1, 1])])},
            "wingR": {"rotation": kf([(0, [0, 0, -70]), (0.3, [0, 0, 10]), (0.4, [0, 0, 0])])},
            "wingL": {"rotation": kf([(0, [0, 0, 70]), (0.3, [0, 0, -10]), (0.4, [0, 0, 0])])},
        }},
        "vanish": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 6, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.15, 1.15, 1.15]), (0.4, [0.05, 0.05, 0.05])])},
        }},
    }


# ================================================================== 공통: 비치는 재질

def see(mat, k):
    """비치는 재질 (텍스처 알파 × k — 그리기 재질 entity_alphablend 인 몹만). 빛나는 칸은 비치지 않으므로 glow 없이"""
    mat.alpha_k = k
    return mat


# ================================================================== 야수왕 (사냥꾼 초월)

BK_FUR = (46, 52, 44)
BK_FUR_ACC = (28, 32, 28)
BK_MANE = (84, 92, 70)
BK_BELLY = (108, 110, 90)
BK_SPIRIT = (153, 219, 77)   # 사냥꾼 빛 (WILD)
BK_CORE = (236, 255, 190)
BK_GOLD = (236, 190, 80)
BK_IVORY = (226, 220, 196)
BK_EYE = (255, 226, 96)

# 일어선 자세 (앞발: 높이 든 발 · 반쯤 든 발) — 할퀴기 동작끼리 이어짐
BK_REAR = -32
BK_HIGH = [-150, 0, 0]
BK_MID = [-95, 0, 0]
BK_ARM_HIGH = [-25, 0, 0]
BK_ARM_MID = [-45, 0, 0]


def rune_beast_king():
    m = Model("rune_beast_king", tex_w=256)
    fur = Mat(BK_FUR, "fur", noise=8, accent=BK_FUR_ACC, seed=501, veins=2, vein=BK_SPIRIT, vein_glow=0.7)
    furp = Mat(BK_FUR, "fur", noise=8, accent=BK_FUR_ACC, seed=502)
    mane = Mat(BK_MANE, "fur", noise=10, accent=(58, 64, 50), seed=503, veins=2, vein=BK_SPIRIT, vein_glow=0.6)
    belly = Mat(BK_BELLY, "fur", noise=6, seed=504)
    spirit = Mat(BK_SPIRIT, "flat", noise=8, edge=1.0, glow=0.9, seed=505)
    core = Mat(BK_CORE, "flat", noise=5, edge=1.0, glow=1.0, seed=506)
    gold = Mat(BK_GOLD, "metal", noise=5, edge=0.88, seed=507)
    claw = Mat(BK_CORE, "flat", noise=4, edge=0.95, glow=0.85, seed=508)
    horn = Mat((178, 240, 120), "crystal", noise=4, edge=1.0, glow=0.55, seed=509)
    dark = Mat((22, 24, 22), "flat", noise=3, edge=0.9, seed=510)
    teeth = Mat((244, 240, 226), "flat", noise=2, edge=0.95, seed=511)

    root = m.bone("root", [0, 0, 0])
    hips = m.bone("hips", [0, 17, 9], "root")
    body = m.bone("body", [0, 18, 0], "hips")
    # 몸통 (앞 −z): 짙은 털 · 정령빛 핏줄, 엉덩이 근육, 등의 정령빛 볏
    body.box([-6, 12, -12], [12, 12, 22], fur, faces={"down": belly})
    body.box([-6.5, 11, 3], [13, 11, 8], furp.with_(seed=512))
    for i, z in enumerate((-8, -3, 2, 7)):
        body.box([-1, 24, z], [2, 2 + (i % 2), 3], spirit.with_(seed=513 + i))
    # 갈기 (어깨를 감싼 큰 갈기 — 끝이 정령빛)
    mane_b = m.bone("mane", [0, 22, -10], "body")
    mane_b.box([-8, 10, -17], [16, 17, 9], mane)
    mane_b.box([-7, 27, -15], [14, 3, 6], mane.with_(seed=514))
    mane_b.box([-9, 13, -15], [1, 11, 6], mane.with_(seed=515))
    mane_b.box([8, 13, -15], [1, 11, 6], mane.with_(seed=516))
    mane_b.box([-5, 8, -16], [10, 2, 6], mane.with_(seed=517))
    # 머리: 두개 · 주둥이 · 코 · 이마의 정령빛 (매섭게) · 금빛 눈 · 송곳니
    head = m.bone("head", [0, 23, -16], "body")
    eye = [("north", 1, 2, BK_EYE, 1.0), ("north", 2, 2, BK_EYE, 1.0), ("north", 7, 2, BK_EYE, 1.0), ("north", 8, 2, BK_EYE, 1.0),
           ("north", 1, 3, (40, 30, 10)), ("north", 8, 3, (40, 30, 10))]
    head.box([-5, 20, -25], [10, 9, 9], furp.with_(seed=520), decals=eye)
    head.box([-3, 20, -32], [6, 5, 7], furp.with_(seed=521, base=(58, 64, 52)), decals=[("up", 1, 1, BK_SPIRIT, 0.8), ("up", 4, 1, BK_SPIRIT, 0.8)])
    head.box([-1.5, 24, -33], [3, 2, 1], dark)
    head.box([-5.5, 27, -25.5], [11, 1, 2], spirit.with_(seed=522))
    head.box([-2.5, 18.5, -31.5], [1, 2, 1], teeth)
    head.box([1.5, 18.5, -31.5], [1, 2, 1], teeth.with_(seed=524))
    jaw = m.bone("jaw", [0, 20.5, -24], "head")
    jaw.box([-2.5, 18, -31], [5, 2, 7], furp.with_(seed=523, base=(52, 56, 48)))
    jaw.box([-2, 20, -30.5], [1, 1, 1], teeth.with_(seed=525))
    jaw.box([1, 20, -30.5], [1, 1, 1], teeth.with_(seed=526))
    for side, sx in (("R", -1), ("L", 1)):
        ear = m.bone("ear" + side, [3.5 * sx, 29, -19], "head", [-10, 0, 14 * sx])
        ear.box([3.5 * sx - 1.5, 29, -20], [3, 5, 2], furp.with_(seed=530 + sx), decals=[("north", 1, 1, BK_SPIRIT, 0.7), ("north", 1, 2, BK_SPIRIT, 0.7)])
    # 왕관 (금 테 · 가시 셋 · 정령빛 보석) + 정령빛 끝의 큰 뿔 둘 (가지 둘씩)
    crown = m.bone("crown", [0, 29, -20], "head")
    crown.box([-5, 29, -25.5], [10, 2, 1], gold)
    crown.box([-5, 29, -16.5], [10, 2, 1], gold.with_(seed=532))
    crown.box([-5.5, 29, -25], [1, 2, 9], gold.with_(seed=533))
    crown.box([4.5, 29, -25], [1, 2, 9], gold.with_(seed=534))
    crown.box([-4, 31, -25.5], [1, 2, 1], gold.with_(seed=535))
    crown.box([-0.5, 31, -25.5], [1, 3, 1], gold.with_(seed=536))
    crown.box([3, 31, -25.5], [1, 2, 1], gold.with_(seed=537))
    crown.box([-1, 29, -26.2], [2, 2, 1], core)
    for side, sx in (("R", -1), ("L", 1)):
        h = m.bone("horn" + side, [3.5 * sx, 30, -21], "crown", [22, 0, 24 * sx])
        h.box([3.5 * sx - 1, 30, -22], [2, 12, 2], horn.with_(seed=540 + sx))
        h.box([3.5 * sx - 0.5, 42, -21.5], [1, 3, 1], core.with_(seed=542 + sx))
        t1 = m.bone("tine" + side + "1", [3.5 * sx, 34, -21], "horn" + side, [-50, 0, 0])
        t1.box([3.5 * sx - 0.5, 34, -21.5], [1, 6, 1], horn.with_(seed=544 + sx))
        t1.box([3.5 * sx - 0.5, 40, -21.5], [1, 1, 1], core.with_(seed=546 + sx))
        t2 = m.bone("tine" + side + "2", [3.5 * sx, 38, -21], "horn" + side, [0, 0, 35 * sx])
        t2.box([3.5 * sx - 0.5, 38, -21.5], [1, 5, 1], horn.with_(seed=548 + sx))
        t2.box([3.5 * sx - 0.5, 43, -21.5], [1, 1, 1], core.with_(seed=550 + sx))
    # 앞다리 (몸을 따라 — 일어서 할큄): 윗다리 · 아랫다리 · 발 · 빛나는 발톱 셋
    for side, sx in (("R", -1), ("L", 1)):
        up = m.bone("legF" + side, [5 * sx, 20, -9], "body")
        x0 = -8 if sx < 0 else 3
        up.box([x0, 10, -12], [5, 11, 6], furp.with_(seed=552 + sx))
        lo = m.bone("armF" + side, [5.5 * sx, 11, -9], "legF" + side)
        x1 = -7.5 if sx < 0 else 3.5
        lo.box([x1, 3, -11], [4, 8, 4], furp.with_(seed=554 + sx))
        x2 = -8 if sx < 0 else 3
        lo.box([x2, 0, -13], [5, 3, 6], fur.with_(seed=556 + sx))
        for i in range(3):
            lo.box([x2 + 0.5 + i * 1.5, 0, -14.5], [1, 2, 2], claw.with_(seed=558 + i))
        # 뒷다리 (땅에 — 몸이 일어서도 그대로)
        th = m.bone("legB" + side, [5 * sx, 18, 8], "root")
        th.box([x0, 9, 4], [5, 11, 8], fur.with_(seed=562 + sx))
        sh = m.bone("shinB" + side, [5.5 * sx, 9, 9], "legB" + side)
        sh.box([x1, 3, 8], [4, 7, 4], furp.with_(seed=564 + sx))
        sh.box([x2, 0, 5], [5, 3, 6], fur.with_(seed=566 + sx))
        for i in range(3):
            sh.box([x2 + 0.5 + i * 1.5, 0, 3.5], [1, 2, 2], claw.with_(seed=568 + i))
    # 꼬리 세 마디 (끝 정령빛)
    t1 = m.bone("tail1", [0, 22, 10], "hips", [-30, 0, 0])
    t1.box([-2.5, 19.5, 10], [5, 5, 8], fur.with_(seed=572))
    t2 = m.bone("tail2", [0, 22, 18], "tail1", [-12, 0, 0])
    t2.box([-2, 20, 18], [4, 4, 7], mane.with_(seed=573))
    t3 = m.bone("tail3", [0, 22, 25], "tail2", [-8, 0, 0])
    t3.box([-1.5, 20.5, 25], [3, 3, 5], spirit.with_(seed=574))
    return m


def _bk_pose(high):
    """일어선 자세: high = 높이 든 앞발 ("R" · "L")"""
    other = "L" if high == "R" else "R"
    return {"legF" + high: BK_HIGH, "armF" + high: BK_ARM_HIGH, "legF" + other: BK_MID, "armF" + other: BK_ARM_MID}


def rune_beast_king_anims():
    br = f"math.sin({LT} * 120)"
    gal = f"math.cos({DM} * 40) * 34 * {MS}"

    def claw(hand):
        # 높이 든 발로 할큄 (0.1초 — 서버 BEAST_STRIKE_AT 2틱에 닿음) → 휘둘러 내림 → 반대 발을 높이 든 자세 (0.35초부터 그대로)
        other = "L" if hand == "R" else "R"
        sx = -1 if hand == "R" else 1
        p0 = _bk_pose(hand)
        p1 = _bk_pose(other)
        bones = {
            "hips": {"rotation": kf([(0, [BK_REAR, 0, 0]), (0.1, [BK_REAR + 12, 0, 0]), (0.2, [BK_REAR + 8, 0, 0]), (0.35, [BK_REAR, 0, 0]), (0.5, [BK_REAR, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 10 * sx, -6 * sx]), (0.25, [0, 6 * sx, -3 * sx]), (0.35, [0, 0, 0]), (0.5, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [-6, 0, 0]), (0.1, [18, -8 * sx, 0]), (0.25, [10, 0, 0]), (0.35, [-6, 0, 0]), (0.5, [-6, 0, 0])])},
            "jaw": {"rotation": kf([(0, [10, 0, 0]), (0.1, [32, 0, 0]), (0.25, [24, 0, 0]), (0.35, [10, 0, 0]), (0.5, [10, 0, 0])])},
            "legF" + hand: {"rotation": kf([(0, p0["legF" + hand]), (0.1, [-40, 0, 26 * sx]), (0.2, [-22, 0, 34 * sx]), (0.35, p1["legF" + hand]),
                                            (0.5, p1["legF" + hand])])},
            "armF" + hand: {"rotation": kf([(0, p0["armF" + hand]), (0.1, [-8, 0, 0]), (0.2, [-4, 0, 0]), (0.35, p1["armF" + hand]), (0.5, p1["armF" + hand])])},
            "legF" + other: {"rotation": kf([(0, p0["legF" + other]), (0.1, [-80, 0, 0]), (0.35, p1["legF" + other]), (0.5, p1["legF" + other])])},
            "armF" + other: {"rotation": kf([(0, p0["armF" + other]), (0.35, p1["armF" + other]), (0.5, p1["armF" + other])])},
            "tail1": {"rotation": kf([(0, [-10, 0, 0]), (0.1, [6, 18 * sx, 0]), (0.35, [-10, 0, 0])])},
        }
        return {"animation_length": 0.5, "bones": bones}

    stand = {b: {"rotation": v} for b, v in _bk_pose("R").items()}
    return {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"{br} * 0.35", 0]},
            "mane": {"scale": [f"1 + {br} * 0.02", f"1 + {br} * 0.025", 1]},
            "head": {"rotation": [f"math.sin({LT} * 70) * 3", f"math.sin({LT} * 45) * 8", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 50)) * 7", 0, 0]},
            "earR": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 130)) * -8"]},
            "earL": {"rotation": [0, 0, f"math.max(0, math.sin({LT} * 130 + 90)) * 8"]},
            "tail1": {"rotation": [f"math.sin({LT} * 60) * 4", f"math.sin({LT} * 100) * 12", 0]},
            "tail2": {"rotation": [0, f"math.sin({LT} * 100 - 40) * 14", 0]},
            "tail3": {"rotation": [0, f"math.sin({LT} * 100 - 80) * 16", 0]},
            "tineR1": {"scale": [1, f"1 + math.sin({LT} * 200) * 0.06", 1]},
            "tineL1": {"scale": [1, f"1 + math.sin({LT} * 200 + 90) * 0.06", 1]},
        }},
        "walk": {"loop": True, "bones": {
            "legFR": {"rotation": [gal, 0, 0]},
            "legBL": {"rotation": [gal, 0, 0]},
            "legFL": {"rotation": [f"-{gal}", 0, 0]},
            "legBR": {"rotation": [f"-{gal}", 0, 0]},
            "body": {"position": [0, f"math.abs(math.sin({DM} * 40)) * 1.2 * {walk_amt()}", 0]},
        }},
        # 하늘에서 내려옴 (서버가 몸을 옮김 — 0.5초): 다리를 접고 작게 맺혔다가 펴며 커짐
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"scale": kf([(0, [0.35, 0.35, 0.35]), (0.3, [1.06, 1.06, 1.06]), (0.5, [1, 1, 1])])},
            "legFR": {"rotation": kf([(0, [-60, 0, 0]), (0.4, [-20, 0, 0]), (0.5, [0, 0, 0])])},
            "legFL": {"rotation": kf([(0, [-60, 0, 0]), (0.4, [-20, 0, 0]), (0.5, [0, 0, 0])])},
            "legBR": {"rotation": kf([(0, [50, 0, 0]), (0.4, [20, 0, 0]), (0.5, [0, 0, 0])])},
            "legBL": {"rotation": kf([(0, [50, 0, 0]), (0.4, [20, 0, 0]), (0.5, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [-20, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        # 내려선 뒤 울부짖으며 일어섬 (0.4초 — 끝 자세 = 오른발을 높이 든 자세, 다음 동작까지 그대로)
        "rear": {"animation_length": 0.4, "bones": {
            "hips": {"rotation": kf([(0, [6, 0, 0]), (0.12, [-6, 0, 0]), (0.4, [BK_REAR, 0, 0])])},
            "body": {"position": kf([(0, [0, -2, 0]), (0.15, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [10, 0, 0]), (0.2, [-30, 0, 0]), (0.4, [-6, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.15, [34, 0, 0]), (0.32, [30, 0, 0]), (0.4, [10, 0, 0])])},
            "legFR": {"rotation": kf([(0, [0, 0, 0]), (0.4, BK_HIGH)])},
            "armFR": {"rotation": kf([(0, [0, 0, 0]), (0.4, BK_ARM_HIGH)])},
            "legFL": {"rotation": kf([(0, [0, 0, 0]), (0.4, BK_MID)])},
            "armFL": {"rotation": kf([(0, [0, 0, 0]), (0.4, BK_ARM_MID)])},
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-10, 0, 0])])},
        }},
        "claw_r": claw("R"),
        "claw_l": claw("L"),
        # 짓밟기 (0.8초): 더 높이 일어섰다가 (0.3) 두 앞발로 내리찍음 (0.45 — 서버 BEAST_STOMP_AT 9틱) → 돌아옴
        "stomp": {"animation_length": 0.8, "bones": {
            "hips": {"rotation": kf([(0, [BK_REAR, 0, 0]), (0.3, [-48, 0, 0]), (0.45, [6, 0, 0]), (0.6, [0, 0, 0]), (0.8, [0, 0, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.45, [0, -2.5, 0]), (0.7, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [-6, 0, 0]), (0.3, [8, 0, 0]), (0.45, [22, 0, 0]), (0.8, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [10, 0, 0]), (0.3, [36, 0, 0]), (0.5, [20, 0, 0]), (0.8, [0, 0, 0])])},
            "legFR": {"rotation": kf([(0, BK_HIGH), (0.3, [-165, 0, 0]), (0.45, [-12, 0, 0]), (0.8, [0, 0, 0])])},
            "legFL": {"rotation": kf([(0, BK_MID), (0.3, [-165, 0, 0]), (0.45, [-12, 0, 0]), (0.8, [0, 0, 0])])},
            "armFR": {"rotation": kf([(0, BK_ARM_HIGH), (0.3, [-20, 0, 0]), (0.45, [0, 0, 0])])},
            "armFL": {"rotation": kf([(0, BK_ARM_MID), (0.3, [-20, 0, 0]), (0.45, [0, 0, 0])])},
            "tail1": {"rotation": kf([(0, [-10, 0, 0]), (0.3, [-35, 0, 0]), (0.45, [15, 0, 0]), (0.8, [0, 0, 0])])},
        }},
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 10, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.08, 1.08, 1.08]), (0.5, [0.05, 0.05, 0.05])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-35, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [30, 0, 0])])},
        }},
    }


# ================================================================== 운디네 (정령 희귀)

UN_WATER = (64, 148, 255)
UN_DEEP = (28, 74, 180)
UN_SKIN = (176, 218, 255)
UN_HAIR = (86, 166, 255)
UN_CORE = (214, 242, 255)
UN_GEM = (120, 255, 240)


def rune_undine():
    m = Model("rune_undine", tex_w=128)
    skin = Mat(UN_SKIN, "skin", noise=4, edge=0.92, seed=601, accent=(140, 190, 240))
    dress = Mat(UN_DEEP, "plates", noise=6, edge=0.85, band=2, seed=602, seam=UN_CORE, seam_glow=0.6, seam_band=3, seam_dir="v", seam_gap=0.5)
    hair = Mat(UN_HAIR, "fur", noise=8, edge=0.88, seed=603, accent=(50, 120, 220), rim=UN_CORE, rim_glow=0.55)
    water = see(Mat(UN_WATER, "ice", noise=6, edge=0.9, seed=604, accent=(40, 110, 230)), 0.62)
    water2 = see(Mat((110, 186, 255), "ice", noise=6, edge=0.92, seed=605), 0.5)
    foam = Mat(UN_CORE, "flat", noise=6, edge=1.0, glow=0.8, seed=606)
    gem = Mat(UN_GEM, "flat", noise=4, edge=1.0, glow=1.0, seed=607)
    eye = (230, 255, 255)

    root = m.bone("root", [0, 0, 0])
    # 아래: 소용돌이치는 물줄기 (밑이 가늘게 — 비침) · 거품 고리 둘
    t0 = m.bone("tail0", [0, 4, 0], "root")
    t0.box([-1.5, 0, -1.5], [3, 4, 3], water)
    t1 = m.bone("tail1", [0, 8, 0], "root")
    t1.box([-2.5, 4, -2.5], [5, 4, 5], water.with_(seed=608))
    t2 = m.bone("tail2", [0, 12, 0], "root")
    t2.box([-3.5, 8, -3.5], [7, 4, 7], water2)
    for name, y, r in (("swirlA", 5, 4), ("swirlB", 10, 6)):
        sw = m.bone(name, [0, y, 0], "root")
        sw.box([-r, y, -r], [2 * r, 1, 1], foam.with_(seed=609 + y))
        sw.box([-r, y, r - 1], [2 * r, 1, 1], foam.with_(seed=610 + y))
        sw.box([-r, y, -r + 1], [1, 1, 2 * r - 2], foam.with_(seed=611 + y))
        sw.box([r - 1, y, -r + 1], [1, 1, 2 * r - 2], foam.with_(seed=612 + y))
    # 몸: 물빛 치마 · 몸통 · 가슴의 물 보석
    body = m.bone("body", [0, 13, 0], "root")
    body.box([-4.5, 11, -3], [9, 4, 6], dress)
    body.box([-3, 15, -2], [6, 7, 4], skin, faces={"north": dress.with_(seed=613), "south": dress.with_(seed=614)})
    body.box([-1, 18, -2.6], [2, 2, 1], gem)
    body.box([-3.5, 21, -2.5], [7, 1, 5], foam.with_(seed=615))  # 어깨 물결 테
    # 머리 · 물결 머리칼 (뒤로 흘러내림 — 아래쪽은 비침) · 지느러미 귀
    head = m.bone("head", [0, 22, 0], "body")
    head.box([-3, 22, -3], [6, 6, 6], skin.with_(seed=616), decals=[
        ("north", 1, 2, eye, 1.0), ("north", 4, 2, eye, 1.0), ("north", 1, 3, UN_WATER, 0.6), ("north", 4, 3, UN_WATER, 0.6)])
    head.box([-3.5, 27, -3.5], [7, 2, 7], hair)
    head.box([-3.5, 23, 2], [7, 5, 2], hair.with_(seed=617))
    head.box([-3.5, 25, -3.6], [1, 3, 2], hair.with_(seed=618))
    head.box([2.5, 25, -3.6], [1, 3, 2], hair.with_(seed=619))
    head.box([-1, 29, -2.5], [2, 1, 2], gem.with_(seed=620))  # 머리 위 물방울 보석
    hb = m.bone("hairB", [0, 24, 3.5], "head")
    hb.box([-3, 15, 3], [6, 9, 2], see(hair.with_(seed=621), 0.85))
    hb2 = m.bone("hairB2", [0, 15, 4], "hairB")
    hb2.box([-2.5, 8, 3.5], [5, 7, 2], see(Mat(UN_HAIR, "ice", noise=6, edge=0.92, seed=622), 0.6))
    for side, sx in (("R", -1), ("L", 1)):
        fin = m.bone("fin" + side, [3 * sx, 26, 0], "head", [0, -20 * sx, 0])
        fin.box([3 * sx - (2 if sx < 0 else 0), 24, -1], [2, 4, 3], see(water.with_(seed=623 + sx), 0.7))
        # 팔: 위팔 · 아래팔 (손목에 물 고리) · 손
        arm = m.bone("arm" + side, [3.6 * sx, 21, 0], "body", [0, 0, 6 * sx])
        ax = -5 if sx < 0 else 3
        arm.box([ax, 16, -1], [2, 5, 2], skin.with_(seed=625 + sx))
        fa = m.bone("forearm" + side, [4 * sx, 16, 0], "arm" + side)
        fa.box([ax, 11, -1], [2, 5, 2], skin.with_(seed=627 + sx))
        fa.box([ax - 0.5, 12, -1.5], [3, 1, 3], foam.with_(seed=629 + sx))
        fa.box([ax, 10, -1], [2, 1, 2], gem.with_(seed=631 + sx))
    return m


def rune_undine_anims():
    bob = f"math.sin({LT} * 110)"
    return {
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"{bob} * 0.8", 0]},
            "swirlA": {"rotation": [0, f"{LT} * 160", 0]},
            "swirlB": {"rotation": [0, f"{LT} * -120", 0]},
            "tail0": {"rotation": [f"math.sin({LT} * 140) * 10", f"{LT} * 90", 0]},
            "tail1": {"rotation": [f"math.sin({LT} * 140 + 40) * 6", f"{LT} * -60", 0]},
            "tail2": {"scale": [f"1 + math.sin({LT} * 200) * 0.05", 1, f"1 + math.sin({LT} * 200 + 90) * 0.05"]},
            "head": {"rotation": [f"math.sin({LT} * 70) * 4", f"math.sin({LT} * 45) * 10", 0]},
            "hairB": {"rotation": [f"10 + math.sin({LT} * 120) * 8", 0, f"math.sin({LT} * 80) * 4"]},
            "hairB2": {"rotation": [f"8 + math.sin({LT} * 120 - 50) * 12", 0, 0]},
            "armR": {"rotation": [f"math.sin({LT} * 90) * 6 - 6", 0, f"10 + math.sin({LT} * 70) * 4"]},
            "armL": {"rotation": [f"math.sin({LT} * 90 + 90) * 6 - 6", 0, f"-10 - math.sin({LT} * 70) * 4"]},
            "forearmR": {"rotation": [-20, 0, 0]},
            "forearmL": {"rotation": [-20, 0, 0]},
            "finR": {"rotation": [0, f"math.sin({LT} * 300) * 10", 0]},
            "finL": {"rotation": [0, f"math.sin({LT} * 300 + 90) * -10", 0]},
        }},
        # 물줄기 (0.5초): 두 손을 모아 뒤로 (0.1) → 앞으로 내밂 (0.2 — 서버 UNDINE_CAST_AT 4틱) → 내민 채 (0.35) → 돌아옴
        "attack": {"animation_length": 0.5, "bones": {
            "armR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [30, 0, 10]), (0.2, [-92, 0, -14]), (0.35, [-88, 0, -12]), (0.5, [0, 0, 0])])},
            "armL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [30, 0, -10]), (0.2, [-92, 0, 14]), (0.35, [-88, 0, 12]), (0.5, [0, 0, 0])])},
            "forearmR": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-40, 0, 0]), (0.2, [0, 0, 0]), (0.5, [0, 0, 0])])},
            "forearmL": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-40, 0, 0]), (0.2, [0, 0, 0]), (0.5, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-10, 0, 0]), (0.2, [10, 0, 0]), (0.5, [0, 0, 0])])},
            "hairB": {"rotation": kf([(0, [0, 0, 0]), (0.2, [30, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        # 물에서 솟음 (0.5초): 아래에서 올라오며 커짐 · 머리칼이 흩날림
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, -14, 0]), (0.35, [0, 1.5, 0]), (0.5, [0, 0, 0])]),
                     "scale": kf([(0, [0.4, 0.4, 0.4]), (0.35, [1.06, 1.06, 1.06]), (0.5, [1, 1, 1])])},
            "armR": {"rotation": kf([(0, [-150, 0, 20]), (0.4, [-20, 0, 10]), (0.5, [0, 0, 0])])},
            "armL": {"rotation": kf([(0, [-150, 0, -20]), (0.4, [-20, 0, -10]), (0.5, [0, 0, 0])])},
            "hairB": {"rotation": kf([(0, [60, 0, 0]), (0.5, [10, 0, 0])])},
        }},
        # 물로 흩어짐 (0.5초): 가라앉으며 작아짐
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, -12, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.1, 0.9, 1.1]), (0.5, [0.1, 0.1, 0.1])])},
        }},
    }


# ================================================================== 실프 (정령 고귀)

SY_SKIN = (222, 250, 230)
SY_HAIR = (96, 224, 140)
SY_DRESS = (52, 160, 100)
SY_LEAF = (120, 220, 120)
SY_WING = (190, 255, 220)
SY_GLOW = (115, 255, 158)
SY_CORE = (235, 255, 240)


def rune_sylph():
    m = Model("rune_sylph", tex_w=128)
    skin = Mat(SY_SKIN, "skin", noise=4, edge=0.92, seed=701, accent=(190, 230, 200))
    hair = Mat(SY_HAIR, "fur", noise=8, edge=0.88, seed=702, accent=(60, 170, 100), rim=SY_CORE, rim_glow=0.5)
    dress = Mat(SY_DRESS, "plates", noise=6, edge=0.85, band=2, seed=703, seam=SY_GLOW, seam_glow=0.6, seam_band=3, seam_dir="v", seam_gap=0.5)
    leaf = Mat(SY_LEAF, "flat", noise=8, edge=0.85, seed=704, accent=(70, 150, 80))
    glow = Mat(SY_GLOW, "flat", noise=6, edge=1.0, glow=0.9, seed=705)
    wing = see(Mat(SY_WING, "ice", noise=5, edge=0.95, seed=706, rim=SY_GLOW, rim_glow=0.9), 0.5)
    eye = (40, 255, 160)

    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 9, 0], "root")
    body.box([-2, 9, -1.5], [4, 6, 3], skin, faces={"north": dress, "south": dress.with_(seed=707)})
    body.box([-3, 6, -2], [6, 3, 4], dress.with_(seed=708))  # 잎 치마
    for i, (x, z) in enumerate(((-3, -2.5), (2, -2.5), (-3, 1.5), (2, 1.5), (-0.5, -2.5))):
        body.box([x, 4, z], [1, 2, 1], leaf.with_(seed=709 + i))  # 치마 끝 잎사귀
    body.box([-1, 12, -1.8], [2, 1, 1], glow)  # 가슴의 바람 보석
    head = m.bone("head", [0, 15, 0], "body")
    head.box([-2.5, 15, -2.5], [5, 5, 5], skin.with_(seed=715), decals=[("north", 1, 2, eye, 1.0), ("north", 3, 2, eye, 1.0)])
    head.box([-3, 19, -3], [6, 2, 6], hair)
    head.box([-3, 16, 1.5], [6, 3, 2], hair.with_(seed=716))
    for side, sx in (("R", -1), ("L", 1)):
        sprout = m.bone("sprout" + side, [1.5 * sx, 21, -1], "head", [-20, 0, 25 * sx])
        sprout.box([1.5 * sx - 0.5, 21, -1.5], [1, 3, 1], leaf.with_(seed=717 + sx))
        sprout.box([1.5 * sx - 0.5, 24, -1.5], [1, 1, 1], glow.with_(seed=719 + sx))
    ht = m.bone("hairT", [0, 19, 2.5], "head")
    ht.box([-2, 14, 2.5], [4, 5, 2], hair.with_(seed=721))
    ht2 = m.bone("hairT2", [0, 14, 3.5], "hairT")
    ht2.box([-1.5, 10, 3], [3, 4, 2], see(Mat(SY_HAIR, "ice", noise=6, edge=0.92, seed=722), 0.75))
    for side, sx in (("R", -1), ("L", 1)):
        arm = m.bone("arm" + side, [2.5 * sx, 14, 0], "body", [0, 0, 8 * sx])
        ax = -3.5 if sx < 0 else 2.5
        arm.box([ax, 9, -0.5], [1, 5, 1], skin.with_(seed=723 + sx))
        arm.box([ax, 9, -0.5], [1, 1, 1], glow.with_(seed=725 + sx), inflate=0.15)
        leg = m.bone("leg" + side, [1 * sx, 6, 0], "body")
        lx = -1.5 if sx < 0 else 0.5
        leg.box([lx, 1, -0.5], [1, 5, 1], skin.with_(seed=727 + sx))
        leg.box([lx, 0, -0.5], [1, 1, 1], leaf.with_(seed=729 + sx))
        # 날개 넷 (등 — 잠자리 날개: 위 · 아래, 비치는 막 · 빛나는 테두리)
        wu = m.bone("wingU" + side, [0.5 * sx, 13, 1.5], "body", [0, -18 * sx, 10 * sx])
        wu.box([0.5 * sx - (9 if sx < 0 else 0), 12, 1.5], [9, 5, 0], wing.with_(seed=731 + sx))
        wl = m.bone("wingD" + side, [0.5 * sx, 11, 1.5], "body", [0, -12 * sx, -16 * sx])
        wl.box([0.5 * sx - (7 if sx < 0 else 0), 8, 1.5], [7, 4, 0], wing.with_(seed=733 + sx))
    return m


def rune_sylph_anims():
    buzz = f"math.sin({LT} * 2200)"
    return {
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"math.sin({LT} * 160) * 1.0", 0]},
            "body": {"rotation": [f"8 + math.sin({LT} * 100) * 3", 0, f"math.sin({LT} * 70) * 3"]},
            "head": {"rotation": [f"math.sin({LT} * 80) * 5", f"math.sin({LT} * 55) * 12", 0]},
            "hairT": {"rotation": [f"25 + math.sin({LT} * 200) * 10", 0, 0]},
            "hairT2": {"rotation": [f"15 + math.sin({LT} * 200 - 60) * 14", 0, 0]},
            "wingUR": {"rotation": [0, f"{buzz} * 22", f"{buzz} * 6"]},
            "wingUL": {"rotation": [0, f"{buzz} * -22", f"{buzz} * -6"]},
            "wingDR": {"rotation": [0, f"math.sin({LT} * 2200 + 90) * 20", 0]},
            "wingDL": {"rotation": [0, f"math.sin({LT} * 2200 + 90) * -20", 0]},
            "armR": {"rotation": [f"math.sin({LT} * 90) * 10 - 10", 0, f"math.sin({LT} * 70) * 6"]},
            "armL": {"rotation": [f"math.sin({LT} * 90 + 90) * 10 - 10", 0, f"-math.sin({LT} * 70) * 6"]},
            "legR": {"rotation": [f"10 + math.sin({LT} * 120) * 8", 0, 0]},
            "legL": {"rotation": [f"15 + math.sin({LT} * 120 + 90) * 8", 0, 0]},
            "sproutR": {"rotation": [f"math.sin({LT} * 300) * 8", 0, 0]},
            "sproutL": {"rotation": [f"math.sin({LT} * 300 + 60) * 8", 0, 0]},
        }},
        # 돌진 (0.6초 — 서버가 몸을 옮김): 몸을 앞으로 눕혀 머리부터 (0.08) · 팔 · 다리 · 머리칼을 뒤로, 날개를 뒤로 접음 → 0.6 돌아옴
        "attack": {"animation_length": 0.6, "bones": {
            "body": {"rotation": kf([(0, [8, 0, 0]), (0.08, [78, 0, 0]), (0.45, [72, 0, 0]), (0.6, [8, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.08, [-55, 0, 0]), (0.45, [-50, 0, 0]), (0.6, [0, 0, 0])])},
            "armR": {"rotation": kf([(0, [0, 0, 0]), (0.08, [40, 0, 20]), (0.45, [40, 0, 20]), (0.6, [0, 0, 0])])},
            "armL": {"rotation": kf([(0, [0, 0, 0]), (0.08, [40, 0, -20]), (0.45, [40, 0, -20]), (0.6, [0, 0, 0])])},
            "legR": {"rotation": kf([(0, [10, 0, 0]), (0.08, [30, 0, 0]), (0.45, [30, 0, 0]), (0.6, [10, 0, 0])])},
            "legL": {"rotation": kf([(0, [15, 0, 0]), (0.08, [35, 0, 0]), (0.45, [35, 0, 0]), (0.6, [15, 0, 0])])},
            "hairT": {"rotation": kf([(0, [25, 0, 0]), (0.08, [70, 0, 0]), (0.45, [70, 0, 0]), (0.6, [25, 0, 0])])},
            "wingUR": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, 40, 0]), (0.45, [0, 40, 0]), (0.6, [0, 0, 0])])},
            "wingUL": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -40, 0]), (0.45, [0, -40, 0]), (0.6, [0, 0, 0])])},
            "wingDR": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, 35, 0]), (0.45, [0, 35, 0]), (0.6, [0, 0, 0])])},
            "wingDL": {"rotation": kf([(0, [0, 0, 0]), (0.08, [0, -35, 0]), (0.45, [0, -35, 0]), (0.6, [0, 0, 0])])},
        }},
        # 바람에서 맺힘 (0.5초): 돌며 커짐
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"scale": kf([(0, [0.2, 0.2, 0.2]), (0.35, [1.1, 1.1, 1.1]), (0.5, [1, 1, 1])]),
                     "rotation": kf([(0, [0, -360, 0]), (0.4, [0, 0, 0])])},
        }},
        # 바람으로 흩어짐 (0.5초): 돌며 위로 · 작아짐
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 8, 0])]), "rotation": kf([(0, [0, 0, 0]), (0.5, [0, 540, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.5, [0.05, 0.05, 0.05])])},
        }},
    }


# ================================================================== 노움 (정령 영웅)

GN_STONE = (150, 140, 124)
GN_MOSS = (92, 132, 62)
GN_BEARD = (214, 206, 190)
GN_SKIN = (198, 152, 114)
GN_TUNIC = (112, 80, 50)
GN_BOOT = (62, 46, 34)
GN_AMBER = (255, 176, 64)
GN_CORE = (255, 232, 168)


def rune_gnome():
    m = Model("rune_gnome", tex_w=128)
    stone = Mat(GN_STONE, "plates", noise=10, edge=0.82, band=3, seed=801, accent=(114, 106, 94), veins=2, vein=GN_AMBER, vein_glow=0.75)
    stone2 = Mat((136, 126, 112), "plates", noise=9, edge=0.8, band=2, seed=802, accent=(104, 96, 86))
    moss = Mat(GN_MOSS, "fur", noise=10, edge=0.85, seed=803, accent=(60, 96, 40))
    beard = Mat(GN_BEARD, "fur", noise=10, edge=0.88, seed=804, accent=(170, 162, 150))
    skin = Mat(GN_SKIN, "skin", noise=5, edge=0.9, seed=805, accent=(170, 120, 90))
    tunic = Mat(GN_TUNIC, "knit", noise=6, edge=0.86, seed=806, accent=(86, 60, 36))
    belt = Mat((70, 48, 30), "flat", noise=5, edge=0.88, seed=807)
    boot = Mat(GN_BOOT, "flat", noise=6, edge=0.86, seed=808)
    wood = Mat((120, 84, 50), "wood", noise=6, edge=0.88, seed=809)
    amber = Mat(GN_AMBER, "crystal", noise=4, edge=1.0, glow=0.9, seed=810)
    core = Mat(GN_CORE, "flat", noise=4, edge=1.0, glow=1.0, seed=811)
    nose = Mat((214, 140, 110), "skin", noise=4, edge=0.92, seed=812)

    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 5, 0], "root")
    body.box([-4, 4, -3], [8, 7, 6], tunic)
    body.box([-4, 5, -3], [8, 1, 6], belt, inflate=0.2, decals=[("north", 3, 0, GN_AMBER, 0.8), ("north", 4, 0, GN_AMBER, 0.8)])
    head = m.bone("head", [0, 11, 0], "body")
    head.box([-3.5, 11, -3.5], [7, 6, 7], skin, decals=[
        ("north", 1, 2, GN_AMBER, 1.0), ("north", 5, 2, GN_AMBER, 1.0), ("north", 1, 1, GN_BEARD), ("north", 2, 1, GN_BEARD),
        ("north", 4, 1, GN_BEARD), ("north", 5, 1, GN_BEARD)])
    head.box([-1, 12, -5], [2, 3, 2], nose)
    beard_b = m.bone("beard", [0, 12, -3.5], "head")
    beard_b.box([-4, 6, -4.5], [8, 7, 2], beard)
    beard_b.box([-3, 3, -4.5], [6, 3, 2], beard.with_(seed=813))
    beard_b.box([-1.5, 1, -4.2], [3, 2, 1], beard.with_(seed=814))
    # 돌 모자: 넓은 챙 (위는 이끼) · 세 마디 원뿔 (뒤로 굽음) · 끝의 호박빛 결정
    hat = m.bone("hat", [0, 17, 0], "head")
    hat.box([-5, 16, -5], [10, 2, 10], stone, faces={"up": moss})
    hat.box([-4, 18, -4], [8, 3, 8], stone.with_(seed=815))
    hat.box([-4.5, 17.5, 2], [3, 2, 3], moss.with_(seed=816))
    h2 = m.bone("hat2", [0, 21, 0.5], "hat", [-10, 0, 0])
    h2.box([-3, 21, -2.5], [6, 3, 6], stone2)
    h3 = m.bone("hat3", [0, 24, 1], "hat2", [-14, 0, 0])
    h3.box([-2, 24, -1], [4, 3, 4], stone.with_(seed=817))
    h4 = m.bone("hat4", [0, 27, 1.5], "hat3", [-12, 0, 0])
    h4.box([-1, 27, 0.5], [2, 3, 2], amber)
    h4.box([-0.5, 30, 1], [1, 1, 1], core)
    for side, sx in (("R", -1), ("L", 1)):
        arm = m.bone("arm" + side, [4.5 * sx, 10, 0], "body", [-62, 0, 14 * sx])
        ax = -6 if sx < 0 else 4
        arm.box([ax, 4, -1.5], [2, 6, 3], tunic.with_(seed=818 + sx))
        arm.box([ax, 3, -1.5], [2, 1, 3], skin.with_(seed=820 + sx))
        leg = m.bone("leg" + side, [2 * sx, 4, 0], "body")
        lx = -3.5 if sx < 0 else 0.5
        leg.box([lx, 1, -1.5], [3, 3, 3], tunic.with_(seed=822 + sx, base=(90, 64, 40)))
        leg.box([lx, 0, -2.5], [3, 1, 4], boot.with_(seed=824 + sx))
    # 돌망치 (오른손 — 자루가 팔을 이어 앞으로 뻗음, 머리 = 끝): 쉴 때 앞의 땅 가까이, 치기 = 팔과 함께 머리 위 뒤로 들었다 앞의 땅을 내리침
    hm = m.bone("hammer", [-5, 3.5, 0], "armR")
    hm.box([-5.5, -7, -0.5], [1, 10, 1], wood)
    hm.box([-8, -11, -2.5], [6, 4, 5], stone.with_(seed=826))
    hm.box([-8.5, -10, -2], [7, 2, 4], amber.with_(seed=827), inflate=-0.3)
    hm.box([-6, -12, -1], [2, 1, 2], core.with_(seed=828))
    return m


def rune_gnome_anims():
    br = f"math.sin({LT} * 140)"
    return {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"{br} * 0.3", 0], "rotation": [0, f"math.sin({LT} * 60) * 4", 0]},
            "head": {"rotation": [f"math.sin({LT} * 80) * 3", f"math.sin({LT} * 50) * 10", 0]},
            "beard": {"rotation": [f"math.sin({LT} * 110) * 4", 0, f"math.sin({LT} * 70) * 3"]},
            "hat2": {"rotation": [0, 0, f"math.sin({LT} * 90) * 3"]},
            "hat3": {"rotation": [0, 0, f"math.sin({LT} * 90 + 40) * 4"]},
            "hat4": {"scale": [f"1 + math.sin({LT} * 300) * 0.08", f"1 + math.sin({LT} * 300) * 0.08", f"1 + math.sin({LT} * 300) * 0.08"]},
        }},
        "walk": {"loop": True, "bones": {
            "legR": {"rotation": [f"math.cos({DM} * 90) * 40 * {MS}", 0, 0]},
            "legL": {"rotation": [f"-math.cos({DM} * 90) * 40 * {MS}", 0, 0]},
            "body": {"position": [0, f"math.abs(math.sin({DM} * 90)) * 0.8 * {walk_amt()}", 0], "rotation": [0, 0, f"math.sin({DM} * 90) * 5 * {walk_amt()}"]},
            "hat3": {"rotation": [f"math.abs(math.sin({DM} * 90)) * -6 * {walk_amt()}", 0, 0]},
        }},
        # 망치로 땅을 내리침 (0.55초): 머리 위로 들어 뒤로 (0.15) → 앞의 땅을 내리침 (0.25 — 서버 GNOME_SLAM_AT 5틱) → 돌아옴
        "attack": {"animation_length": 0.55, "bones": {
            "armR": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-138, 0, 0]), (0.25, [8, 0, 0]), (0.4, [4, 0, 0]), (0.55, [0, 0, 0])])},
            "armL": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-138, 0, 0]), (0.25, [8, 0, 0]), (0.4, [4, 0, 0]), (0.55, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-14, 0, 0]), (0.25, [18, 0, 0]), (0.4, [14, 0, 0]), (0.55, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.15, [0, 1.2, 0]), (0.25, [0, -0.6, 0]), (0.55, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-12, 0, 0]), (0.25, [10, 0, 0]), (0.55, [0, 0, 0])])},
            "beard": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-25, 0, 0]), (0.4, [8, 0, 0]), (0.55, [0, 0, 0])])},
        }},
        # 땅에서 솟음 (0.5초)
        "summon": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, -12, 0]), (0.3, [0, 1.2, 0]), (0.45, [0, 0, 0])]),
                     "scale": kf([(0, [0.5, 0.5, 0.5]), (0.3, [1.08, 1.08, 1.08]), (0.5, [1, 1, 1])])},
            "armR": {"rotation": kf([(0, [-60, 0, 0]), (0.3, [10, 0, 0]), (0.5, [0, 0, 0])])},
        }},
        # 땅으로 꺼짐 (0.5초)
        "vanish": {"animation_length": 0.5, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.15, [0, 1.5, 0]), (0.5, [0, -12, 0])]), "scale": kf([(0, [1, 1, 1]), (0.5, [0.3, 0.3, 0.3])])},
        }},
    }


# ================================================================== 정령왕 (정령 초월 — 정령왕 강림)

SK_ROBE = (246, 248, 252)
SK_INNER = (40, 120, 110)
SK_GOLD = (240, 200, 90)
SK_SPIRIT = (107, 255, 194)
SK_FACE = (226, 252, 246)
SK_FIRE = (255, 115, 31)
SK_WATER = (64, 148, 255)
SK_WIND = (107, 255, 153)
SK_EARTH = (224, 158, 77)
SK_WHITE = (255, 255, 255)


def rune_spirit_king():
    m = Model("rune_spirit_king", tex_w=256)
    robe = Mat(SK_ROBE, "knit", noise=3, edge=0.9, seed=901, accent=(226, 232, 242), seam=SK_SPIRIT, seam_glow=0.55, seam_band=4, seam_dir="v", seam_gap=0.55)
    robe2 = Mat(SK_ROBE, "knit", noise=3, edge=0.9, seed=902, accent=(226, 232, 242))
    inner = Mat(SK_INNER, "plates", noise=6, edge=0.85, band=2, seed=903, seam=SK_SPIRIT, seam_glow=0.75, seam_band=3, seam_dir="h", seam_gap=0.4)
    gold = Mat(SK_GOLD, "metal", noise=5, edge=0.86, seed=904)
    face = Mat(SK_FACE, "skin", noise=3, edge=0.94, seed=905, accent=(200, 236, 228))
    beard = Mat((246, 250, 252), "fur", noise=8, edge=0.9, seed=906, accent=(214, 224, 232))
    spirit = Mat(SK_SPIRIT, "flat", noise=6, edge=1.0, glow=0.9, seed=907)
    white = Mat(SK_WHITE, "flat", noise=3, edge=1.0, glow=1.0, seed=908)

    def orb(col, seed):
        return Mat(col, "crystal", noise=4, edge=1.0, glow=0.95, seed=seed)

    root = m.bone("root", [0, 0, 0])
    # 긴 옷 (두 단 — 금 단) · 몸통 · 금 띠 · 가슴의 정령 문장 · 금 어깨
    skirt = m.bone("skirt", [0, 16, 0], "root")
    skirt.box([-7, 0, -5], [14, 8, 10], robe)
    skirt.box([-7.5, 0, -5.5], [15, 2, 11], gold, inflate=-0.1)
    skirt.box([-6, 8, -4], [12, 8, 8], robe2)
    skirt.box([-2, 1, -5.3], [4, 14, 1], inner)  # 앞자락 안쪽 (정령빛 줄)
    body = m.bone("body", [0, 16, 0], "root")
    body.box([-5, 16, -3], [10, 12, 6], robe.with_(seed=909))
    body.box([-5.5, 16, -3.5], [11, 2, 7], gold.with_(seed=910))
    body.box([-1.5, 21, -3.6], [3, 3, 1], spirit)
    for side, sx in (("R", -1), ("L", 1)):
        body.box([-9 if sx < 0 else 5, 25, -3.5], [4, 3, 7], gold.with_(seed=911 + sx))
    # 망토 (등 — 두 마디, 안은 정령빛 줄)
    cape = m.bone("cape", [0, 28, 3.5], "body")
    cape.box([-6, 14, 3], [12, 14, 1], inner.with_(seed=913), faces={"north": robe2.with_(seed=914)})
    cape2 = m.bone("cape2", [0, 14, 3.5], "cape")
    cape2.box([-6.5, 2, 3], [13, 12, 1], inner.with_(seed=915), faces={"north": robe2.with_(seed=916)})
    # 머리: 빛나는 얼굴 · 흰 수염 · 금 왕관 (네 원소 보석)
    head = m.bone("head", [0, 28, 0], "body")
    head.box([-3.5, 28, -3.5], [7, 7, 7], face, decals=[("north", 1, 3, SK_WHITE, 1.0), ("north", 2, 3, SK_WHITE, 1.0), ("north", 4, 3, SK_WHITE, 1.0),
                                                     ("north", 5, 3, SK_WHITE, 1.0), ("north", 1, 2, (180, 230, 220)), ("north", 5, 2, (180, 230, 220))])
    head.box([-3.5, 24, -4.2], [7, 6, 2], beard)
    head.box([-2, 21, -4], [4, 3, 1], beard.with_(seed=917))
    crown = m.bone("crown", [0, 35, 0], "head")
    crown.box([-4, 34, -4], [8, 2, 8], gold.with_(seed=918))
    for i, (x, z) in enumerate(((-4, -4), (3, -4), (-4, 3), (3, 3))):
        crown.box([x, 36, z], [1, 3, 1], gold.with_(seed=919 + i))
    crown.box([-0.5, 36, -4.2], [1, 5, 1], gold.with_(seed=923))
    for i, (col, x, z) in enumerate(((SK_FIRE, -1, -4.6), (SK_WATER, 3.6, -1), (SK_WIND, -1, 3.6), (SK_EARTH, -4.6, -1))):
        crown.box([x, 34.5, z], [2, 1, 2] if i % 2 == 0 else [1, 1, 2], orb(col, 924 + i))
    # 팔 (넓은 소매 · 금 소맷부리 · 빛나는 손)
    for side, sx in (("R", -1), ("L", 1)):
        arm = m.bone("arm" + side, [6 * sx, 26, 0], "body", [0, 0, 6 * sx])
        ax = -9 if sx < 0 else 5
        arm.box([ax, 15, -2], [4, 11, 4], robe2.with_(seed=930 + sx))
        arm.box([ax - 0.5, 15, -2.5], [5, 2, 5], gold.with_(seed=932 + sx))
        arm.box([ax + 0.5, 13, -1.5], [3, 2, 3], face.with_(seed=934 + sx))
        arm.box([ax + 1, 12, -1], [2, 1, 2], spirit.with_(seed=936 + sx))
    # 등 뒤 금빛 후광 (팔각 — 두 겹, 머리 · 어깨를 두름) · 네 원소 구슬
    halo_gold = Mat(SK_GOLD, "metal", noise=4, edge=0.9, seed=939, rim=(255, 246, 200), rim_glow=0.7)
    halo = m.bone("halo", [0, 32, 7], "body")
    for j, rot in enumerate((0, 45)):
        hb = m.bone(f"halo{j}", [0, 32, 7], "halo", [0, 0, rot])
        hb.box([-5.5, 45, 6.5], [11, 1, 1], halo_gold.with_(seed=940 + j))
        hb.box([-5.5, 18, 6.5], [11, 1, 1], halo_gold.with_(seed=942 + j))
        hb.box([-14, 26.5, 6.5], [1, 11, 1], halo_gold.with_(seed=944 + j))
        hb.box([13, 26.5, 6.5], [1, 11, 1], halo_gold.with_(seed=946 + j))
    for i, (col, x, y) in enumerate(((SK_FIRE, -2, 44), (SK_WATER, 12, 30), (SK_WIND, -2, 16), (SK_EARTH, -16, 30))):
        halo.box([x, y, 5], [4, 4, 4], orb(col, 950 + i))
    # 몸 둘레를 도는 네 원소 구슬 (허리 높이)
    orbs = m.bone("orbs", [0, 14, 0], "root")
    for i, (col, name) in enumerate(((SK_FIRE, "orbFire"), (SK_WATER, "orbWater"), (SK_WIND, "orbWind"), (SK_EARTH, "orbEarth"))):
        a = i * 90
        ox = round(12 * math.cos(math.radians(a)), 1)
        oz = round(12 * math.sin(math.radians(a)), 1)
        ob = m.bone(name, [ox, 14, oz], "orbs")
        ob.box([ox - 2, 12, oz - 2], [4, 4, 4], orb(col, 960 + i), decals=[(f, 1, 1, SK_WHITE, 1.0) for f in ("north", "south", "east", "west")])
    return m


def rune_spirit_king_anims():
    return {
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"math.sin({LT} * 80) * 1.0", 0]},
            "head": {"rotation": [f"math.sin({LT} * 50) * 3", f"math.sin({LT} * 35) * 6", 0]},
            "skirt": {"rotation": [f"math.sin({LT} * 70) * 2", 0, f"math.sin({LT} * 55) * 2"]},
            "cape": {"rotation": [f"8 + math.sin({LT} * 90) * 5", 0, 0]},
            "cape2": {"rotation": [f"6 + math.sin({LT} * 90 - 50) * 8", 0, 0]},
            "halo": {"rotation": [0, 0, f"{LT} * 30"]},
            "orbs": {"rotation": [0, f"{LT} * 70", 0]},
            "orbFire": {"position": [0, f"math.sin({LT} * 150) * 1.2", 0]},
            "orbWater": {"position": [0, f"math.sin({LT} * 150 + 90) * 1.2", 0]},
            "orbWind": {"position": [0, f"math.sin({LT} * 150 + 180) * 1.2", 0]},
            "orbEarth": {"position": [0, f"math.sin({LT} * 150 + 270) * 1.2", 0]},
            "armR": {"rotation": [f"math.sin({LT} * 60) * 4 - 8", 0, f"8 + math.sin({LT} * 45) * 3"]},
            "armL": {"rotation": [f"math.sin({LT} * 60 + 90) * 4 - 8", 0, f"-8 - math.sin({LT} * 45) * 3"]},
        }},
        # 강림 (0.3초 — 서버가 몸을 내림 RuneSpiritKing.DESCEND 6틱): 작게 맺혀 커지며 두 팔을 들어 올림 (끝 자세 그대로 — 원소를 내리는 동작 cast 와 이어짐)
        "summon": {"animation_length": 0.3, "bones": {
            "root": {"scale": kf([(0, [0.3, 0.3, 0.3]), (0.2, [1.06, 1.06, 1.06]), (0.3, [1, 1, 1])])},
            "armR": {"rotation": kf([(0, [0, 0, 60]), (0.3, [-160, 0, 18])])},
            "armL": {"rotation": kf([(0, [0, 0, -60]), (0.3, [-160, 0, -18])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-12, 0, 0])])},
            "halo": {"scale": kf([(0, [0.4, 0.4, 0.4]), (0.3, [1.08, 1.08, 1.08])])},
        }},
        # 두 팔을 들어 올림 (0.5초 — 끝 자세 그대로: 다음 동작까지)
        "raise": {"animation_length": 0.5, "bones": {
            "armR": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-160, 0, 18])])},
            "armL": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-160, 0, -18])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-12, 0, 0])])},
            "halo": {"scale": kf([(0, [1, 1, 1]), (0.3, [1.15, 1.15, 1.15]), (0.5, [1.08, 1.08, 1.08])])},
        }},
        # 원소를 내림 (0.5초 — 든 팔을 앞으로 내리꽂음: 0.15 = 서버 KING_CAST_AT 3틱 → 다시 듦)
        "cast": {"animation_length": 0.5, "bones": {
            "armR": {"rotation": kf([(0, [-160, 0, 18]), (0.15, [-75, 0, 8]), (0.3, [-80, 0, 8]), (0.5, [-160, 0, 18])])},
            "armL": {"rotation": kf([(0, [-160, 0, -18]), (0.15, [-75, 0, -8]), (0.3, [-80, 0, -8]), (0.5, [-160, 0, -18])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [8, 0, 0]), (0.5, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [-12, 0, 0]), (0.15, [6, 0, 0]), (0.5, [-12, 0, 0])])},
            "halo": {"scale": kf([(0, [1.08, 1.08, 1.08]), (0.15, [1.3, 1.3, 1.3]), (0.5, [1.08, 1.08, 1.08])])},
        }},
        # 빛으로 흩어짐 (0.6초): 커졌다가 작아지며 위로
        "vanish": {"animation_length": 0.6, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.6, [0, 8, 0])]), "scale": kf([(0, [1, 1, 1]), (0.2, [1.12, 1.12, 1.12]), (0.6, [0.05, 0.05, 0.05])])},
            "armR": {"rotation": kf([(0, [-160, 0, 18]), (0.3, [-170, 0, 40])])},
            "armL": {"rotation": kf([(0, [-160, 0, -18]), (0.3, [-170, 0, -40])])},
        }},
    }


# ================================================================== 늑대 정령 덮치기 (rpg:spirit_wolf 에 덧붙이는 동작)

def spirit_wolf_extra():
    """늑대 무리 (사냥꾼 영웅): 뛰어올라 덮침 — 0.35초 (0.15 = 서버 WOLF_POUNCE_AT 3틱에 콱)"""
    return {
        "pounce": {"animation_length": 0.4, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.06, [-18, 0, 0]), (0.15, [10, 0, 0]), (0.3, [4, 0, 0]), (0.4, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.06, [-26, 0, 0]), (0.15, [22, 0, 0]), (0.4, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.06, [40, 0, 0]), (0.13, [38, 0, 0]), (0.16, [0, 0, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.06, [-70, 0, 0]), (0.15, [-30, 0, 0]), (0.4, [0, 0, 0])])},
            "frontLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.06, [-70, 0, 0]), (0.15, [-30, 0, 0]), (0.4, [0, 0, 0])])},
            "backRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.06, [50, 0, 0]), (0.15, [30, 0, 0]), (0.4, [0, 0, 0])])},
            "backLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.06, [50, 0, 0]), (0.15, [30, 0, 0]), (0.4, [0, 0, 0])])},
            "tail1": {"rotation": kf([(0, [0, 0, 0]), (0.06, [-30, 0, 0]), (0.4, [0, 0, 0])])},
        }},
    }


# ================================================================== 공통: 넣기 · 미리보기

BEHAVIORS = {}


# ================================================================== 그림자 분신 (암살자 영웅 · 초월 — 그림자 난도질 · 월광 암살, 2026-11-13)

SH_CLOTH = (36, 22, 48)
SH_CLOTH2 = (56, 32, 72)
SH_TRIM = (214, 46, 245)   # 암살자 빛 (자홍)
SH_CORE = (255, 214, 255)
SH_VOID = (8, 4, 12)
SH_STEEL = (70, 58, 92)


def rune_shade():
    """그림자 분신: 두건을 쓴 검보라 그림자 암살자 — 얼굴은 어둠 · 자홍 눈 둘, 자홍 테의 망토 · 목도리 두 가닥 (뒤로 날림), 두 손에 자홍 날의 단검 (앞으로),
    발밑은 그림자 연기 (비침). 키 약 1.9칸 (사람과 같음) · 앞 = −Z"""
    m = Model("rune_shade", tex_w=128)
    cloth = Mat(SH_CLOTH, "knit", noise=5, edge=0.8, seed=1101, accent=(26, 14, 34), seam=SH_TRIM, seam_glow=0.7, seam_band=6, seam_dir="v", seam_gap=0.6)
    cloth2 = Mat(SH_CLOTH2, "knit", noise=5, edge=0.82, seed=1102, accent=(40, 22, 52))
    trim = Mat(SH_TRIM, "flat", noise=4, edge=1.0, glow=0.85, seed=1103)
    void = Mat(SH_VOID, "flat", noise=2, edge=1.0, seed=1104)
    steel = Mat(SH_STEEL, "metal", noise=5, edge=0.86, seed=1105)
    edge = Mat(SH_CORE, "flat", noise=3, edge=1.0, glow=1.0, seed=1106)
    wrap = Mat((44, 28, 40), "knit", noise=4, edge=0.85, seed=1107)
    smoke = see(Mat((60, 30, 80), "ice", noise=8, edge=0.95, seed=1108, accent=(90, 40, 120)), 0.5)
    tail = see(Mat(SH_CLOTH2, "knit", noise=5, edge=0.85, seed=1109, rim=SH_TRIM, rim_glow=0.6), 0.9)
    eye = SH_CORE

    root = m.bone("root", [0, 0, 0])
    sm = m.bone("smoke", [0, 0, 0], "root")
    sm.box([-5, 0, -5], [10, 2, 10], smoke)
    sm.box([-3, 2, -3], [6, 2, 6], smoke.with_(seed=1110))
    body = m.bone("body", [0, 12, 0], "root")
    body.box([-4, 12, -2], [8, 12, 4], cloth, decals=[("north", 3, 5, SH_TRIM, 0.8), ("north", 4, 5, SH_TRIM, 0.8)])
    body.box([-4, 13, -2], [8, 1, 4], trim, inflate=0.2)
    cloak = m.bone("cloak", [0, 23, 2], "body")
    cloak.box([-4.5, 8, 2], [9, 15, 1], tail)
    cloak.box([-4.5, 8, 2.6], [9, 1, 1], trim.with_(seed=1111))
    head = m.bone("head", [0, 24, 0], "body")
    head.box([-4, 24, -4], [8, 8, 8], cloth2, faces={"north": void}, decals=[("north", 2, 4, eye, 1.0), ("north", 5, 4, eye, 1.0),
                                                                          ("north", 2, 3, SH_TRIM, 0.7), ("north", 5, 3, SH_TRIM, 0.7)])
    head.box([-4.5, 31, -4.5], [9, 1, 9], cloth.with_(seed=1112))
    hood = m.bone("hood", [0, 32, 2], "head", [-20, 0, 0])
    hood.box([-2, 32, 0], [4, 3, 4], cloth2.with_(seed=1113))
    hood.box([-1, 35, 1], [2, 2, 3], cloth.with_(seed=1114))
    for side, sx in (("R", -1), ("L", 1)):
        sc = m.bone("scarf" + side, [1.5 * sx, 23, 2.5], "body", [20, 0, 10 * sx])
        sc.box([1.5 * sx - 1, 16, 2.5], [2, 7, 1], see(cloth2.with_(seed=1115 + sx), 0.92))
        sc2 = m.bone("scarf2" + side, [1.5 * sx, 16, 3], "scarf" + side, [15, 0, 0])
        sc2.box([1.5 * sx - 1, 10, 2.5], [2, 6, 1], see(cloth2.with_(seed=1117 + sx), 0.75))
        sc2.box([1.5 * sx - 1, 9, 2.5], [2, 1, 1], trim.with_(seed=1118 + sx))
        arm = m.bone("arm" + side, [5 * sx, 22, 0], "body")
        ax = -7 if sx < 0 else 4
        arm.box([ax, 16, -1.5], [3, 7, 3], cloth.with_(seed=1119 + sx))
        fa = m.bone("forearm" + side, [5.5 * sx, 16, 0], "arm" + side)
        fa.box([ax, 10, -1.5], [3, 6, 3], wrap.with_(seed=1121 + sx))
        fa.box([ax - 0.2, 14, -1.7], [3, 1, 3], trim.with_(seed=1123 + sx), inflate=0.2)
        # 단검: 아래팔을 따라 손 너머로 (팔을 앞으로 뻗으면 칼끝도 앞) — 자루 · 자홍 코등이 · 날 (안쪽 날이 빛남)
        dg = m.bone("dagger" + side, [5.5 * sx, 10.5, 0], "forearm" + side)
        dx = -6 if sx < 0 else 5
        dg.box([dx, 8.5, -0.5], [1, 2, 1], wrap.with_(seed=1125 + sx))
        dg.box([dx - 0.5, 8, -1.5], [2, 1, 3], trim.with_(seed=1127 + sx))
        dg.box([dx, 1, -1], [1, 7, 2], steel.with_(seed=1129 + sx), faces={"north": edge})
        dg.box([dx, 0, -0.5], [1, 1, 1], edge.with_(seed=1131 + sx))
        leg = m.bone("leg" + side, [2 * sx, 12, 0], "body")
        lx = -4 if sx < 0 else 0
        leg.box([lx, 2, -2], [4, 10, 4], cloth2.with_(seed=1133 + sx))
        leg.box([lx, 0, -2.5], [4, 2, 5], wrap.with_(seed=1135 + sx))
    return m


# 쉬는 자세 (idle 이 늘 켜져 있고 동작은 더해짐 — 베드락 동작은 같은 뼈의 값을 더함): 단검을 앞으로 낮게 든 팔 · 조금 숙인 몸 · 벌린 다리
SH_READY = {
    "armR": {"rotation": [-40, 10, 8]}, "armL": {"rotation": [-40, -10, -8]},
    "forearmR": {"rotation": [-50, 0, 0]}, "forearmL": {"rotation": [-50, 0, 0]},
    "body": {"rotation": [8, 0, 0], "position": [0, -1, 0]}, "head": {"rotation": [-6, 0, 0]},
    "legR": {"rotation": [-18, 0, 4]}, "legL": {"rotation": [14, 0, -4]}, "cloak": {"rotation": [12, 0, 0]},
}


def sh_rel(bones):
    """동작 열쇠 (그 뼈의 실제 자세) → 쉬는 자세에서 더할 값 (idle 과 더해져 그 자세가 됨)"""
    out = {}
    for bone, chans in bones.items():
        out[bone] = {}
        for ch, frames in chans.items():
            base = SH_READY.get(bone, {}).get(ch)
            if base is None or ch == "scale":
                out[bone][ch] = kf(frames)
                continue
            out[bone][ch] = kf([(t, [v - b for v, b in zip(vec, base)]) for t, vec in frames])
    return out


def rune_shade_anims():
    sway = f"math.sin({LT} * 120)"
    R = SH_READY
    return {
        "idle": {"loop": True, "bones": {
            "armR": R["armR"], "armL": R["armL"], "forearmR": R["forearmR"], "forearmL": R["forearmL"],
            "body": {"rotation": [f"8 + {sway} * 2", f"math.sin({LT} * 70) * 6", 0], "position": [0, f"-1 + {sway} * 0.3", 0]},
            "head": {"rotation": [f"-6 + math.sin({LT} * 90) * 3", f"math.sin({LT} * 50) * 8", 0]},
            "cloak": {"rotation": [f"12 + math.sin({LT} * 160) * 8", 0, f"math.sin({LT} * 110) * 4"]},
            "scarfR": {"rotation": [f"35 + math.sin({LT} * 200) * 12", 0, f"-12 + math.sin({LT} * 150) * 6"]},
            "scarfL": {"rotation": [f"35 + math.sin({LT} * 200 + 60) * 12", 0, f"12 + math.sin({LT} * 150 + 40) * 6"]},
            "scarf2R": {"rotation": [f"20 + math.sin({LT} * 200 - 60) * 16", 0, 0]},
            "scarf2L": {"rotation": [f"20 + math.sin({LT} * 200) * 16", 0, 0]},
            "legR": R["legR"], "legL": R["legL"],
            "smoke": {"rotation": [0, f"{LT} * 90", 0], "scale": [f"1 + {sway} * 0.08", 1, f"1 + {sway} * 0.08"]},
        }},
        # 그림자에서 솟음 (0.3초)
        "appear": {"animation_length": 0.3, "bones": {
            "root": {"position": kf([(0, [0, -18, 0]), (0.2, [0, 1, 0]), (0.3, [0, 0, 0])]),
                     "scale": kf([(0, [0.6, 0.6, 0.6]), (0.2, [1.05, 1.05, 1.05]), (0.3, [1, 1, 1])])},
        }},
        # 오른 단검으로 크게 비스듬히 벰 (0.45초 — 0.1 머리 위 오른쪽으로 감음 · 0.2 왼쪽 아래로 벰 (서버 SHADE_STRIKE_AT 4틱) · 지나쳐 멈춤)
        "slash": {"animation_length": 0.45, "bones": sh_rel({
            "armR": {"rotation": [(0, [-40, 10, 8]), (0.1, [-165, -20, 40]), (0.2, [-70, 40, -30]), (0.32, [-40, 50, -40]), (0.45, [-40, 10, 8])]},
            "forearmR": {"rotation": [(0, [-50, 0, 0]), (0.1, [-20, 0, 0]), (0.2, [-10, 0, 0]), (0.45, [-50, 0, 0])]},
            "armL": {"rotation": [(0, [-40, -10, -8]), (0.1, [-20, -30, -20]), (0.2, [10, -20, -30]), (0.45, [-40, -10, -8])]},
            "body": {"rotation": [(0, [8, 0, 0]), (0.1, [-6, 30, 0]), (0.2, [22, -35, 0]), (0.32, [24, -40, 0]), (0.45, [8, 0, 0])],
                     "position": [(0, [0, -1, 0]), (0.2, [0, -2.5, -3]), (0.45, [0, -1, 0])]},
            "legR": {"rotation": [(0, [-18, 0, 4]), (0.2, [-40, 0, 6]), (0.45, [-18, 0, 4])]},
            "legL": {"rotation": [(0, [14, 0, -4]), (0.2, [30, 0, -6]), (0.45, [14, 0, -4])]},
            "cloak": {"rotation": [(0, [12, 0, 0]), (0.2, [45, 0, 10]), (0.45, [12, 0, 0])]},
        })},
        # 왼 단검으로 (거울)
        "slash2": {"animation_length": 0.45, "bones": sh_rel({
            "armL": {"rotation": [(0, [-40, -10, -8]), (0.1, [-165, 20, -40]), (0.2, [-70, -40, 30]), (0.32, [-40, -50, 40]), (0.45, [-40, -10, -8])]},
            "forearmL": {"rotation": [(0, [-50, 0, 0]), (0.1, [-20, 0, 0]), (0.2, [-10, 0, 0]), (0.45, [-50, 0, 0])]},
            "armR": {"rotation": [(0, [-40, 10, 8]), (0.1, [-20, 30, 20]), (0.2, [10, 20, 30]), (0.45, [-40, 10, 8])]},
            "body": {"rotation": [(0, [8, 0, 0]), (0.1, [-6, -30, 0]), (0.2, [22, 35, 0]), (0.32, [24, 40, 0]), (0.45, [8, 0, 0])],
                     "position": [(0, [0, -1, 0]), (0.2, [0, -2.5, -3]), (0.45, [0, -1, 0])]},
            "legL": {"rotation": [(0, [14, 0, -4]), (0.2, [-40, 0, -6]), (0.45, [14, 0, -4])]},
            "legR": {"rotation": [(0, [-18, 0, 4]), (0.2, [30, 0, 6]), (0.45, [-18, 0, 4])]},
            "cloak": {"rotation": [(0, [12, 0, 0]), (0.2, [45, 0, -10]), (0.45, [12, 0, 0])]},
        })},
        # 두 단검으로 깊이 찌름 (0.4초 — 0.08 당김 · 0.2 찌름 · 머묾)
        "stab": {"animation_length": 0.4, "bones": sh_rel({
            "armR": {"rotation": [(0, [-40, 10, 8]), (0.08, [-20, 20, 20]), (0.2, [-84, -6, 0]), (0.34, [-82, -6, 0]), (0.4, [-60, 0, 4])]},
            "armL": {"rotation": [(0, [-40, -10, -8]), (0.08, [-20, -20, -20]), (0.2, [-84, 6, 0]), (0.34, [-82, 6, 0]), (0.4, [-60, 0, -4])]},
            "forearmR": {"rotation": [(0, [-50, 0, 0]), (0.08, [-80, 0, 0]), (0.2, [-4, 0, 0]), (0.4, [-20, 0, 0])]},
            "forearmL": {"rotation": [(0, [-50, 0, 0]), (0.08, [-80, 0, 0]), (0.2, [-4, 0, 0]), (0.4, [-20, 0, 0])]},
            "body": {"rotation": [(0, [8, 0, 0]), (0.08, [-4, 0, 0]), (0.2, [26, 0, 0]), (0.4, [18, 0, 0])],
                     "position": [(0, [0, -1, 0]), (0.08, [0, 0, 2]), (0.2, [0, -3, -5]), (0.4, [0, -2, -4])]},
            "legR": {"rotation": [(0, [-18, 0, 4]), (0.2, [-50, 0, 6]), (0.4, [-40, 0, 6])]},
            "legL": {"rotation": [(0, [14, 0, -4]), (0.2, [40, 0, -6]), (0.4, [32, 0, -6])]},
            "cloak": {"rotation": [(0, [12, 0, 0]), (0.2, [55, 0, 0]), (0.4, [40, 0, 0])]},
        })},
        # X 베기 (0.5초 — 0.15 두 단검을 머리 위로 엇갈려 듦 · 0.25 바깥 아래로 크게 벰)
        "xcut": {"animation_length": 0.5, "bones": sh_rel({
            "armR": {"rotation": [(0, [-40, 10, 8]), (0.15, [-170, 30, -25]), (0.25, [-50, 20, 60]), (0.4, [-30, 10, 50]), (0.5, [-40, 10, 8])]},
            "armL": {"rotation": [(0, [-40, -10, -8]), (0.15, [-170, -30, 25]), (0.25, [-50, -20, -60]), (0.4, [-30, -10, -50]), (0.5, [-40, -10, -8])]},
            "forearmR": {"rotation": [(0, [-50, 0, 0]), (0.15, [-30, 0, 0]), (0.25, [-6, 0, 0]), (0.5, [-50, 0, 0])]},
            "forearmL": {"rotation": [(0, [-50, 0, 0]), (0.15, [-30, 0, 0]), (0.25, [-6, 0, 0]), (0.5, [-50, 0, 0])]},
            "body": {"rotation": [(0, [8, 0, 0]), (0.15, [-14, 0, 0]), (0.25, [28, 0, 0]), (0.5, [10, 0, 0])],
                     "position": [(0, [0, -1, 0]), (0.15, [0, 1, 1]), (0.25, [0, -3, -3]), (0.5, [0, -1, 0])]},
            "head": {"rotation": [(0, [-6, 0, 0]), (0.15, [-16, 0, 0]), (0.25, [12, 0, 0]), (0.5, [-6, 0, 0])]},
            "cloak": {"rotation": [(0, [12, 0, 0]), (0.25, [60, 0, 0]), (0.5, [14, 0, 0])]},
        })},
        # 그림자로 꺼짐 (0.35초)
        "vanish": {"animation_length": 0.35, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 1, 0]), (0.35, [0, -16, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.1, [1.06, 0.94, 1.06]), (0.35, [0.3, 0.3, 0.3])])},
        }},
    }


# ================================================================== 기의 화신 (무투 영웅 — 백열권, 2026-11-13)

KA_KI = (255, 190, 70)     # 무투 빛 (금빛 주황)
KA_KI2 = (255, 214, 120)
KA_CORE = (255, 248, 220)
KA_DARK = (190, 110, 30)
KA_WRAP = (250, 240, 214)


def rune_ki_avatar():
    """기의 화신: 쓴 사람 뒤에 솟는 반투명 금빛 무투가의 윗몸 (다리 대신 기의 소용돌이 꼬리) — 상투 · 붉은 머리띠 · 성난 눈썹 · 빛나는 눈 · 넓은 가슴 ·
    둥근 어깨 · 굵은 팔 · 빛나는 마디의 붕대 주먹, 머리 뒤의 기 고리 (후광). 키 약 2.2칸 (크기 1 — 스킬이 1.7배로 띄움) · 앞 = −Z"""
    m = Model("rune_ki_avatar", tex_w=128)
    ki = see(Mat(KA_KI, "skin", noise=6, edge=0.82, seed=1201, accent=(230, 150, 50), rim=KA_CORE, rim_glow=0.6), 0.8)
    ki2 = see(Mat(KA_KI2, "skin", noise=5, edge=0.86, seed=1202, accent=(240, 170, 70), rim=KA_CORE, rim_glow=0.45), 0.75)
    dark = see(Mat(KA_DARK, "fur", noise=8, edge=0.85, seed=1203), 0.9)
    wrap = Mat(KA_WRAP, "knit", noise=5, edge=0.86, seed=1204)
    core = Mat(KA_CORE, "flat", noise=3, edge=1.0, glow=1.0, seed=1205)
    swirl = see(Mat(KA_KI, "ice", noise=8, edge=0.95, seed=1206, accent=(255, 220, 140)), 0.55)
    band = Mat((200, 40, 40), "knit", noise=4, edge=0.9, seed=1207)
    brow = (120, 60, 20)

    root = m.bone("root", [0, 0, 0])
    sw = m.bone("swirl", [0, 6, 0], "root")
    sw.box([-2, -2, -2], [4, 4, 4], swirl)
    sw.box([-3.5, 2, -3.5], [7, 4, 7], swirl.with_(seed=1208))
    sw.box([-5, 5, -5], [10, 3, 10], swirl.with_(seed=1209))
    body = m.bone("body", [0, 8, 0], "root")
    body.box([-5, 8, -3], [10, 6, 6], ki2)
    body.box([-8, 14, -4.5], [16, 9, 9], ki, decals=[("north", 4, 5, KA_CORE, 0.5), ("north", 11, 5, KA_CORE, 0.5), ("north", 7, 2, KA_CORE, 0.4),
                                                     ("north", 8, 2, KA_CORE, 0.4)])
    ring = m.bone("ring", [0, 27, 6], "body")
    import math as _m
    for i in range(12):
        a = i / 12 * 2 * _m.pi
        ring.box([round(_m.cos(a) * 9 - 1, 1), round(27 + _m.sin(a) * 9 - 1, 1), 6], [2, 2, 1], core.with_(seed=1210 + i))
    head = m.bone("head", [0, 23, 0], "body")
    head.box([-3.5, 23, -3.5], [7, 7, 7], ki.with_(seed=1220), decals=[("north", 1, 3, KA_CORE, 1.0), ("north", 2, 3, KA_CORE, 1.0),
                                                                     ("north", 4, 3, KA_CORE, 1.0), ("north", 5, 3, KA_CORE, 1.0),
                                                                     ("north", 1, 2, brow), ("north", 2, 2, brow), ("north", 4, 2, brow),
                                                                     ("north", 5, 2, brow)])
    head.box([-3.7, 27.5, -3.7], [7, 1, 7], band, inflate=0.25)
    head.box([-1.5, 30, -0.5], [3, 2, 3], dark)
    head.box([-1, 32, 0], [2, 2, 2], dark.with_(seed=1221))
    for side, sx in (("R", -1), ("L", 1)):
        arm = m.bone("arm" + side, [8.5 * sx, 21, 0], "body")
        ax = -12 if sx < 0 else 8
        arm.box([ax, 18, -3], [4, 5, 6], ki2.with_(seed=1230 + sx))
        arm.box([ax, 13, -2.5], [4, 6, 5], ki.with_(seed=1232 + sx))
        fa = m.bone("forearm" + side, [10 * sx, 13, 0], "arm" + side)
        fa.box([ax, 6, -2.5], [4, 7, 5], ki.with_(seed=1234 + sx))
        fa.box([ax - 0.4, 6, -2.9], [4, 2, 5], wrap.with_(seed=1236 + sx), inflate=0.2)
        fist = m.bone("fist" + side, [10 * sx, 6, 0], "forearm" + side)
        fist.box([ax - 0.5, 1, -3], [5, 5, 6], wrap.with_(seed=1238 + sx), decals=[("down", 1, 1, KA_CORE, 1.0), ("down", 2, 1, KA_CORE, 1.0),
                                                                                   ("down", 3, 1, KA_CORE, 1.0), ("down", 1, 3, KA_CORE, 0.8),
                                                                                   ("down", 3, 3, KA_CORE, 0.8)])
    return m


KA_READY = {
    "armR": {"rotation": [-60, 15, 10]}, "armL": {"rotation": [-60, -15, -10]},
    "forearmR": {"rotation": [-70, 0, 0]}, "forearmL": {"rotation": [-70, 0, 0]},
    "body": {"rotation": [10, 0, 0]},
}


def ka_rel(bones):
    out = {}
    for bone, chans in bones.items():
        out[bone] = {}
        for ch, frames in chans.items():
            base = KA_READY.get(bone, {}).get(ch)
            if base is None or ch == "scale":
                out[bone][ch] = kf(frames)
                continue
            out[bone][ch] = kf([(t, [v - b for v, b in zip(vec, base)]) for t, vec in frames])
    return out


def rune_ki_avatar_anims():
    R = KA_READY
    return {
        "idle": {"loop": True, "bones": {
            "armR": R["armR"], "armL": R["armL"], "forearmR": R["forearmR"], "forearmL": R["forearmL"],
            "body": {"rotation": [f"10 + math.sin({LT} * 120) * 2", f"math.sin({LT} * 70) * 5", 0], "position": [0, f"math.sin({LT} * 120) * 0.6", 0]},
            "head": {"rotation": [f"math.sin({LT} * 90) * 3", f"math.sin({LT} * 50) * 6", 0]},
            "swirl": {"rotation": [0, f"{LT} * 300", 0], "scale": [f"1 + math.sin({LT} * 400) * 0.1", 1, f"1 + math.sin({LT} * 400) * 0.1"]},
            "ring": {"rotation": [0, 0, f"{LT} * 120"]},
        }},
        # 기에서 솟음 (0.35초)
        "appear": {"animation_length": 0.35, "bones": {
            "root": {"position": kf([(0, [0, -16, 0]), (0.25, [0, 1.5, 0]), (0.35, [0, 0, 0])]),
                     "scale": kf([(0, [0.4, 0.4, 0.4]), (0.25, [1.08, 1.08, 1.08]), (0.35, [1, 1, 1])])},
        }},
        # 주먹 비 (되풀이 0.2초 — 오른 주먹 0.05 · 왼 주먹 0.15 에 쭉 뻗음, 몸이 좌우로 비틀림)
        "barrage": {"loop": True, "animation_length": 0.2, "bones": ka_rel({
            "armR": {"rotation": [(0, [-60, 15, 10]), (0.05, [-92, -6, 0]), (0.1, [-60, 15, 10]), (0.2, [-60, 15, 10])]},
            "forearmR": {"rotation": [(0, [-70, 0, 0]), (0.05, [-4, 0, 0]), (0.1, [-70, 0, 0]), (0.2, [-70, 0, 0])]},
            "armL": {"rotation": [(0, [-60, -15, -10]), (0.1, [-60, -15, -10]), (0.15, [-92, 6, 0]), (0.2, [-60, -15, -10])]},
            "forearmL": {"rotation": [(0, [-70, 0, 0]), (0.1, [-70, 0, 0]), (0.15, [-4, 0, 0]), (0.2, [-70, 0, 0])]},
            "body": {"rotation": [(0, [10, 0, 0]), (0.05, [12, 14, 0]), (0.1, [10, 0, 0]), (0.15, [12, -14, 0]), (0.2, [10, 0, 0])]},
        })},
        # 마무리 일격 (0.5초 — 0.15 오른 주먹을 크게 당김 · 0.25 쭉 뻗어 침 · 머묾)
        "finisher": {"animation_length": 0.5, "bones": ka_rel({
            "armR": {"rotation": [(0, [-60, 15, 10]), (0.15, [-20, 40, 30]), (0.25, [-95, -4, 0]), (0.42, [-93, -4, 0]), (0.5, [-60, 15, 10])]},
            "forearmR": {"rotation": [(0, [-70, 0, 0]), (0.15, [-110, 0, 0]), (0.25, [0, 0, 0]), (0.42, [0, 0, 0]), (0.5, [-70, 0, 0])]},
            "armL": {"rotation": [(0, [-60, -15, -10]), (0.15, [-80, -10, -10]), (0.25, [-20, -20, -30]), (0.5, [-60, -15, -10])]},
            "body": {"rotation": [(0, [10, 0, 0]), (0.15, [4, 35, 0]), (0.25, [22, -30, 0]), (0.42, [20, -28, 0]), (0.5, [10, 0, 0])],
                     "position": [(0, [0, 0, 0]), (0.15, [0, 0, 2]), (0.25, [0, -1, -4]), (0.5, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (0.15, [-8, -20, 0]), (0.25, [6, 20, 0]), (0.5, [0, 0, 0])]},
        })},
        # 기로 흩어짐 (0.45초)
        "vanish": {"animation_length": 0.45, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.45, [0, 10, 0])]), "scale": kf([(0, [1, 1, 1]), (0.15, [1.1, 1.1, 1.1]), (0.45, [0.2, 0.2, 0.2])])},
        }},
    }


# ================================================================== 투신의 화신 (신체 영웅 — 투신)

WG_SKIN = (190, 38, 32)     # 투신의 기운 (핏빛)
WG_SKIN2 = (150, 26, 24)
WG_IRON = (52, 48, 56)
WG_GOLD = (222, 168, 64)
WG_EYE = (255, 196, 120)
WG_RIM = (255, 120, 80)


def rune_war_god():
    """투신의 화신: 쓴 사람 뒤에 솟는 반투명 핏빛 거인 전사의 윗몸 (다리 대신 핏빛 기의 소용돌이) — 굽은 뿔 둘의 검은 쇠 투구 (금 테 · 이마의 보석) ·
    빛나는 눈 · 넓은 가슴 · 갈라진 배 · 가시 달린 검은 어깨 갑옷 · 금 팔찌 · 굵은 주먹, 등 뒤 갈라진 망토 (비침). 키 약 2.6칸 (크기 1) · 앞 = −Z"""
    m = Model("rune_war_god", tex_w=128)
    skin = see(Mat(WG_SKIN, "skin", noise=6, edge=0.8, seed=1301, accent=(236, 92, 60), rim=WG_RIM, rim_glow=0.6), 0.8)
    skin2 = see(Mat(WG_SKIN2, "skin", noise=5, edge=0.84, seed=1302, accent=(206, 66, 46), rim=WG_RIM, rim_glow=0.45), 0.76)
    iron = Mat(WG_IRON, "metal", noise=5, edge=0.82, seed=1303, seam=WG_RIM, seam_glow=0.6, seam_band=5, seam_dir="h", seam_gap=0.5)
    gold = Mat(WG_GOLD, "metal", noise=4, edge=0.86, seed=1304)
    core = Mat(WG_EYE, "flat", noise=3, edge=1.0, glow=1.0, seed=1305)
    horn = Mat((236, 220, 190), "bone", noise=5, edge=0.82, seed=1306)
    swirl = see(Mat(WG_SKIN, "ice", noise=8, edge=0.95, seed=1307, accent=(255, 120, 80)), 0.55)
    cape = see(Mat((120, 16, 22), "knit", noise=5, edge=0.86, seed=1308, rim=WG_RIM, rim_glow=0.5), 0.85)

    root = m.bone("root", [0, 0, 0])
    sw = m.bone("swirl", [0, 6, 0], "root")
    sw.box([-2, -2, -2], [4, 4, 4], swirl)
    sw.box([-4, 2, -4], [8, 4, 8], swirl.with_(seed=1309))
    sw.box([-6, 5, -6], [12, 4, 12], swirl.with_(seed=1310))
    body = m.bone("body", [0, 9, 0], "root")
    # 허리 · 금 띠 (가운데 보석) · 갈라진 배 · 넓은 가슴
    body.box([-6, 9, -3.5], [12, 6, 7], skin2, decals=[("north", 5, 1, (110, 20, 18)), ("north", 6, 1, (110, 20, 18)), ("north", 5, 3, (110, 20, 18)),
                                                     ("north", 6, 3, (110, 20, 18))])
    body.box([-6.5, 9, -3.9], [13, 2, 8], gold, decals=[("north", 6, 0, WG_EYE, 1.0), ("north", 6, 1, WG_EYE, 1.0)])
    body.box([-9, 15, -5], [18, 10, 10], skin, decals=[("north", 4, 6, (120, 22, 20)), ("north", 13, 6, (120, 22, 20)), ("north", 8, 3, WG_RIM, 0.5),
                                                     ("north", 9, 3, WG_RIM, 0.5)])
    # 등 뒤 망토 (갈라진 두 자락)
    cp = m.bone("cape", [0, 24, 5], "body")
    cp.box([-8, 8, 5], [7, 16, 1], cape)
    cp.box([1, 8, 5], [7, 16, 1], cape.with_(seed=1311))
    # 머리 · 투구 (금 테 · 이마 보석 · 볼 가리개) · 빛나는 눈
    head = m.bone("head", [0, 25, 0], "body")
    head.box([-4, 25, -4], [8, 8, 8], skin.with_(seed=1320), decals=[("north", 1, 4, WG_EYE, 1.0), ("north", 2, 4, WG_EYE, 1.0), ("north", 5, 4, WG_EYE, 1.0),
                                                                     ("north", 6, 4, WG_EYE, 1.0), ("north", 2, 1, (90, 14, 14)), ("north", 3, 1, (90, 14, 14)),
                                                                     ("north", 4, 1, (90, 14, 14)), ("north", 5, 1, (90, 14, 14))])
    head.box([-4.5, 29, -4.5], [9, 5, 9], iron, inflate=0.2, faces={"north": None} if False else None)
    head.box([-4.6, 29, -4.7], [9, 1, 1], gold)
    head.box([-1, 30, -4.9], [2, 2, 1], core)
    head.box([-4.6, 25, -4.6], [2, 4, 3], iron.with_(seed=1321))
    head.box([2.6, 25, -4.6], [2, 4, 3], iron.with_(seed=1322))
    # 뿔 (마디 셋 — 밖 위로 굽음)
    for side, sx in (("R", -1), ("L", 1)):
        h1 = m.bone("horn1" + side, [4.5 * sx, 31.5, 0], "head", rotation=[0, 0, 60 * sx])
        h1.box([4.5 * sx - 1.5, 31.5, -1.5], [3, 5, 3], horn)
        h2 = m.bone("horn2" + side, [4.5 * sx, 36.0, 0], "horn1" + side, rotation=[0, 0, -32 * sx])
        h2.box([4.5 * sx - 1, 36.0, -1], [2, 5, 2], horn.with_(seed=1323))
        h3 = m.bone("horn3" + side, [4.5 * sx, 40.5, 0], "horn2" + side, rotation=[-20, 0, -28 * sx])
        h3.box([4.5 * sx - 0.5, 40.5, -0.5], [1, 4, 1], core)
    # 팔 (가시 달린 어깨 갑옷 · 굵은 팔 · 금 팔찌 · 주먹)
    for side, sx in (("R", -1), ("L", 1)):
        arm = m.bone("arm" + side, [9 * sx, 23, 0], "body")
        ax = -14 if sx < 0 else 9
        arm.box([ax - 0.5 * sx - 0.5, 21, -3.5], [6, 5, 7], iron.with_(seed=1330 + sx), inflate=0.3)
        arm.box([ax + (1 if sx > 0 else 2), 26, -0.5], [2, 3, 1], horn.with_(seed=1332 + sx))
        arm.box([ax, 15, -2.5], [5, 7, 5], skin.with_(seed=1334 + sx))
        fa = m.bone("forearm" + side, [11.5 * sx, 15, 0], "arm" + side)
        fa.box([ax, 7, -2.5], [5, 8, 5], skin2.with_(seed=1336 + sx))
        fa.box([ax - 0.4, 8, -2.9], [5, 3, 5], gold.with_(seed=1338 + sx), inflate=0.25)
        fist = m.bone("fist" + side, [11.5 * sx, 7, 0], "forearm" + side)
        fist.box([ax - 0.5, 1, -3], [6, 6, 6], skin.with_(seed=1340 + sx), decals=[("down", 1, 1, WG_RIM, 0.9), ("down", 3, 1, WG_RIM, 0.9),
                                                                                   ("down", 5, 1, WG_RIM, 0.9)])
    return m


WG_READY = {
    "armR": {"rotation": [-15, 0, 12]}, "armL": {"rotation": [-15, 0, -12]},
    "forearmR": {"rotation": [-30, 0, 0]}, "forearmL": {"rotation": [-30, 0, 0]},
    "body": {"rotation": [6, 0, 0]},
}


def wg_rel(bones):
    out = {}
    for bone, chans in bones.items():
        out[bone] = {}
        for ch, frames in chans.items():
            base = WG_READY.get(bone, {}).get(ch)
            if base is None or ch == "scale":
                out[bone][ch] = kf(frames)
                continue
            out[bone][ch] = kf([(t, [v - b for v, b in zip(vec, base)]) for t, vec in frames])
    return out


def rune_war_god_anims():
    R = WG_READY
    return {
        "idle": {"loop": True, "bones": {
            "armR": R["armR"], "armL": R["armL"], "forearmR": R["forearmR"], "forearmL": R["forearmL"],
            "body": {"rotation": [f"6 + math.sin({LT} * 100) * 2", f"math.sin({LT} * 60) * 4", 0], "position": [0, f"math.sin({LT} * 100) * 0.6", 0]},
            "head": {"rotation": [f"math.sin({LT} * 80) * 3", f"math.sin({LT} * 45) * 6", 0]},
            "swirl": {"rotation": [0, f"{LT} * -280", 0], "scale": [f"1 + math.sin({LT} * 380) * 0.1", 1, f"1 + math.sin({LT} * 380) * 0.1"]},
            "cape": {"rotation": [f"12 + math.sin({LT} * 200) * 6", 0, 0]},
        }},
        # 기에서 솟음 (0.4초)
        "appear": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, -18, 0]), (0.28, [0, 1.5, 0]), (0.4, [0, 0, 0])]),
                     "scale": kf([(0, [0.4, 0.4, 0.4]), (0.28, [1.08, 1.08, 1.08]), (0.4, [1, 1, 1])])},
        }},
        # 포효 (1.1초 — 0.15 두 팔을 가슴 앞에 모아 웅크림 · 0.3 두 팔을 위 바깥으로 활짝 · 고개를 젖힘 → 떨며 머묾)
        "roar": {"animation_length": 1.1, "bones": wg_rel({
            "armR": {"rotation": [(0, [-15, 0, 12]), (0.15, [-70, 30, -20]), (0.3, [-125, 0, 78]), (0.95, [-125, 0, 82]), (1.1, [-15, 0, 12])]},
            "armL": {"rotation": [(0, [-15, 0, -12]), (0.15, [-70, -30, 20]), (0.3, [-125, 0, -78]), (0.95, [-125, 0, -82]), (1.1, [-15, 0, -12])]},
            "forearmR": {"rotation": [(0, [-30, 0, 0]), (0.15, [-110, 0, 0]), (0.3, [-25, 0, 0]), (1.1, [-30, 0, 0])]},
            "forearmL": {"rotation": [(0, [-30, 0, 0]), (0.15, [-110, 0, 0]), (0.3, [-25, 0, 0]), (1.1, [-30, 0, 0])]},
            "body": {"rotation": [(0, [6, 0, 0]), (0.15, [22, 0, 0]), (0.3, [-14, 0, 0]), (0.5, [-12, 0, 2]), (0.7, [-14, 0, -2]), (0.95, [-12, 0, 0]),
                              (1.1, [6, 0, 0])],
                     "position": [(0, [0, 0, 0]), (0.15, [0, -2, 0]), (0.3, [0, 1.5, 0]), (1.1, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (0.15, [18, 0, 0]), (0.3, [-28, 0, 0]), (0.95, [-26, 0, 0]), (1.1, [0, 0, 0])]},
        })},
        # 내려찍기 (0.6초 — 0.2 두 주먹을 모아 머리 위로 · 0.3 앞 땅으로 내리찍음 · 머묾)
        "slam": {"animation_length": 0.6, "bones": wg_rel({
            "armR": {"rotation": [(0, [-15, 0, 12]), (0.2, [-170, -10, -12]), (0.3, [-60, -10, -10]), (0.5, [-58, -10, -10]), (0.6, [-15, 0, 12])]},
            "armL": {"rotation": [(0, [-15, 0, -12]), (0.2, [-170, 10, 12]), (0.3, [-60, 10, 10]), (0.5, [-58, 10, 10]), (0.6, [-15, 0, -12])]},
            "forearmR": {"rotation": [(0, [-30, 0, 0]), (0.2, [-60, 0, 0]), (0.3, [-5, 0, 0]), (0.6, [-30, 0, 0])]},
            "forearmL": {"rotation": [(0, [-30, 0, 0]), (0.2, [-60, 0, 0]), (0.3, [-5, 0, 0]), (0.6, [-30, 0, 0])]},
            "body": {"rotation": [(0, [6, 0, 0]), (0.2, [-18, 0, 0]), (0.3, [42, 0, 0]), (0.5, [40, 0, 0]), (0.6, [6, 0, 0])],
                     "position": [(0, [0, 0, 0]), (0.2, [0, 2, 1]), (0.3, [0, -4, -3]), (0.5, [0, -4, -3]), (0.6, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (0.2, [-14, 0, 0]), (0.3, [-20, 0, 0]), (0.6, [0, 0, 0])]},
        })},
        # 쓴 사람에게 스며듦 (0.45초 — 작아지며 앞 아래로)
        "vanish": {"animation_length": 0.45, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.45, [0, 4, -10])]), "scale": kf([(0, [1, 1, 1]), (0.12, [1.08, 1.08, 1.08]), (0.45, [0.15, 0.15, 0.15])])},
        }},
    }


def behavior(fam, health, move, box, fly=False):
    c = {
        "minecraft:type_family": {"family": ["rpg_ally", fam]},
        "minecraft:health": {"value": health, "max": health},
        "minecraft:movement": {"value": move},
        "minecraft:jump.static": {},
        "minecraft:collision_box": {"width": box[0], "height": box[1]},
        "minecraft:scale": {"value": 1},
        "minecraft:physics": {"has_gravity": not fly} if fly else {},
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


# 키 → (모델 함수, 동작 함수, 행동, 그리기 재질, 늘 트는 동작, 비치는 재질 알파)
MOBS = {
    "rune_hawk": (rune_hawk, rune_hawk_anims, dict(fam="rune_hawk", health=40, move=0.4, box=[0.6, 0.6], fly=True), "entity_emissive", ["fly"], None),
    "rune_beast_king": (rune_beast_king, rune_beast_king_anims, dict(fam="rune_beast_king", health=200, move=0.36, box=[1.4, 1.6]), "entity_emissive",
                        ["idle", "walk"], None),
    "rune_undine": (rune_undine, rune_undine_anims, dict(fam="rune_undine", health=40, move=0.32, box=[0.7, 1.7], fly=True), "entity_alphablend", ["idle"],
                    True),
    "rune_sylph": (rune_sylph, rune_sylph_anims, dict(fam="rune_sylph", health=40, move=0.4, box=[0.6, 1.2], fly=True), "entity_alphablend", ["idle"], True),
    "rune_gnome": (rune_gnome, rune_gnome_anims, dict(fam="rune_gnome", health=40, move=0.3, box=[0.7, 1.1]), "entity_emissive", ["idle", "walk"], None),
    "rune_spirit_king": (rune_spirit_king, rune_spirit_king_anims, dict(fam="rune_spirit_king", health=200, move=0.3, box=[1.0, 2.6], fly=True),
                         "entity_emissive", ["idle"], None),
    "rune_shade": (rune_shade, rune_shade_anims, dict(fam="rune_shade", health=40, move=0.4, box=[0.6, 1.8], fly=True), "entity_alphablend", ["idle"], True),
    "rune_ki_avatar": (rune_ki_avatar, rune_ki_avatar_anims, dict(fam="rune_ki_avatar", health=40, move=0.4, box=[0.9, 2.0], fly=True), "entity_alphablend",
                       ["idle"], True),
    "rune_war_god": (rune_war_god, rune_war_god_anims, dict(fam="rune_war_god", health=40, move=0.4, box=[1.0, 2.4], fly=True), "entity_alphablend",
                     ["idle"], True),
}


def build(key):
    fn, an, _, _, _, _ = MOBS[key]
    m = fn()
    m.pack()
    im = m.paint()
    a = an()
    d = os.path.join(ART, key)
    os.makedirs(d, exist_ok=True)
    geo = m.geo_json()
    write_json(os.path.join(d, key + ".geo.json"), geo)
    im.save(os.path.join(d, key + ".png"))
    write_json(os.path.join(d, key + ".animation.json"), anim_file(key, a))
    return m, geo, im, a


def install(key, m, geo, im, a):
    _, _, beh_def, material, animate, alpha = MOBS[key]
    beh = json.load(open(BEHAVIOR, encoding="utf-8"))
    beh["rpg:" + key] = behavior(**beh_def)
    with open(BEHAVIOR, "w", encoding="utf-8") as f:
        json.dump(beh, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    cl = json.load(open(CLIENT, encoding="utf-8"))
    os.makedirs(TEX, exist_ok=True)
    eid = "rpg:" + key
    tex = f"textures/entity/rpg/allies/{key}"
    cl["entities"][eid] = {"identifier": eid, "materials": {"default": material}, "textures": {"default": tex},
                           "geometry": {"default": f"geometry.rpg.monster.{key}"},
                           "animations": {n: f"animation.rpg.monster.{key}.{n}" for n in a},
                           "scripts": {"animate": animate}, "render_controllers": ["controller.render.default"]}
    g = geo["minecraft:geometry"][0]
    cl["models"][g["description"]["identifier"]] = {"description": g["description"], "bones": g["bones"]}
    for n, an in a.items():
        cl["animations"][f"animation.rpg.monster.{key}.{n}"] = an
    # 텍스처 · 빛나는 칸 (알파 255 보다 낮은 칸 — 베드락 entity_emissive) · 비치는 재질 (alpha — 칸 알파를 낮춤: entity_alphablend)
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
    if alpha:
        for bone in m.bones:
            for bx in bone.boxes:
                k = getattr(bx.mat, "alpha_k", None)
                if k is None:
                    continue
                u, v = bx.uv
                fw, fh = bx.footprint()
                for y in range(v, min(im.size[1], v + fh)):
                    for x in range(u, min(im.size[0], u + fw)):
                        c = bp[x, y]
                        if c[3] > 0:
                            bp[x, y] = (c[0], c[1], c[2], int(c[3] * k))
    base.save(os.path.join(TEX, key + ".png"))
    if glow:
        emi.save(os.path.join(TEX, key + "_emissive.png"))
        if tex not in cl["emissive"]:
            cl["emissive"].append(tex)
    with open(CLIENT, "w", encoding="utf-8") as f:
        json.dump(cl, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


# 이미 있는 몹에 덧붙이는 동작 (엔티티 → (모델 키, 동작 함수))
EXTRA = {"rpg:spirit_wolf": ("spirit_wolf", spirit_wolf_extra)}


def install_extra():
    cl = json.load(open(CLIENT, encoding="utf-8"))
    for eid, (key, fn) in EXTRA.items():
        ent = cl["entities"].get(eid)
        if ent is None:
            raise SystemExit(f"없는 엔티티: {eid}")
        for n, an in fn().items():
            full = f"animation.rpg.monster.{key}.{n}"
            cl["animations"][full] = an
            ent.setdefault("animations", {})[n] = full
    with open(CLIENT, "w", encoding="utf-8") as f:
        json.dump(cl, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


PREVIEW_POSES = {
    "rune_hawk": ((["fly"], 0.0), (["fly"], 0.12), (["fly", "dive"], 0.25), (["summon"], 0.15)),
    "rune_beast_king": ((["idle"], 0.0), (["idle", "rear"], 0.4), (["idle", "claw_r"], 0.1), (["idle", "claw_l"], 0.1), (["idle", "stomp"], 0.3),
                        (["idle", "stomp"], 0.45)),
    "rune_undine": ((["idle"], 0.0), (["idle", "attack"], 0.2), (["summon"], 0.2)),
    "rune_sylph": ((["idle"], 0.0), (["idle", "attack"], 0.2)),
    "rune_gnome": ((["idle"], 0.0), (["idle", "attack"], 0.15), (["idle", "attack"], 0.25)),
    "rune_spirit_king": ((["idle"], 0.0), (["idle", "raise"], 0.5), (["idle", "cast"], 0.15)),
    "rune_ki_avatar": ((["idle"], 0.0), (["idle", "barrage"], 0.05), (["idle", "barrage"], 0.15), (["idle", "finisher"], 0.15),
                       (["idle", "finisher"], 0.25)),
    "rune_war_god": ((["idle"], 0.0), (["idle", "roar"], 0.15), (["idle", "roar"], 0.5), (["idle", "slam"], 0.2), (["idle", "slam"], 0.35)),
    "rune_shade": ((["idle"], 0.0), (["idle", "slash"], 0.1), (["idle", "slash"], 0.2), (["idle", "stab"], 0.2), (["idle", "xcut"], 0.15),
                   (["idle", "xcut"], 0.25)),
}


def main():
    args = sys.argv[1:]
    folder = None
    if "--preview" in args:
        i = args.index("--preview")
        folder = args[i + 1]
        args = args[:i] + args[i + 2:]
    keys = [k for k in args if k in MOBS] or list(MOBS)
    install_extra()
    for key in keys:
        m, geo, im, a = build(key)
        install(key, m, geo, im, a)
        print("%s: bones %d texture %s" % (key, len(geo["minecraft:geometry"][0]["bones"]), im.size))
        if folder:
            os.makedirs(folder, exist_ok=True)
            import bedrock_preview as BP
            for names, t in PREVIEW_POSES.get(key, ((["idle"], 0.0),)):
                out = os.path.join(folder, f"{key}_{'_'.join(names)}_{t:.2f}.png")
                print("  preview:", BP.sheet(geo, im, a, names, t, out, label=key))


if __name__ == "__main__":
    main()
