#!/usr/bin/env python3
"""2026-11-06 피에로 (암살자 반전 — 광대 소환물 탱커) 의 소환물 그림 (모델 · 텍스처 · 동작) — tools/monster_kit.py 로 짬.

  python3 tools/pierrot_art.py                  # tools/monster_art/<키>/ 에 쓰고 게임 자원에 넣음 (아래)
  python3 tools/pierrot_art.py --preview 폴더    # 확인용 그림 (tools/bedrock_preview.py — 자세마다 앞 · 옆 · 뒤)

- rpg:pierrot_box  깜짝 상자 (잭 인 더 박스): 금테 두른 빨강 · 금빛 마름모 무늬 상자 (14 × 12 × 14) · 뒤쪽 경첩 뚜껑 (별 무늬) · 옆 손잡이 ·
  상자 안 용수철 넷 · 용수철 광대 머리 (흰 얼굴 · 빨간 코 · 웃는 입 · 청록 눈 · 주름 깃 · 두 뿔 모자 끝에 금방울 — 빛남).
  동작: idle (상자 흔들림 · 머리 · 뿔 · 방울 흔들림 — 닫혀 있으면 머리는 상자 안) · drop (위에서 떨어져 통통 튐) · pop (뚜껑이 뒤로 열리고 용수철이 튀어 올라
  머리가 솟음 — 끝 자세 유지: 서버가 멈춤 식 "0" 으로 틂) · throw (칼 던지기 — 고개를 젖혔다 앞으로) · fire (불꽃놀이 — 상자가 움찔 · 머리 끄덕).
  피날레의 거대한 상자는 같은 몹 × 크기 2.2 (서버 SCALE).
- rpg:pierrot_doll 광대 인형 (할루시네이트 — 서버 SCALE 2): 줄 인형 광대 (마름모 무늬 옷 — 몸 · 팔 · 다리가 빨강 / 금빛 엇갈림 · 흰 방울 단추 · 주름 깃 ·
  흰 얼굴 · 빨간 코 · 웃는 입 · 두 뿔 모자 · 흰 장갑 · 끝이 말린 빨간 구두) · 오른손 뿅망치 (빨강 · 노랑 줄 머리 · 흰 자루) · 머리 · 두 손 위로 뻗은 금빛 줄과 십자
  손잡이 (빛남 — 꼭두각시). 동작: idle (꼭두각시처럼 들썩 · 팔 늘어짐) · walk (뻣뻣한 줄 인형 걸음) · attack (뿅망치를 머리 위로 → 0.45초에 땅을 내리침 —
  서버 HAMMER_AT 9틱) · drop (하늘에서 떨어져 내려섬) · taunt (팔을 흔들며 놀림 · 깡충).

게임에 넣는 곳 (있으면 바꿈 · 없으면 더함): src/main/resources/mmorpg/entities.json (행동 — allies 무리) · assets/mmorpg/bedrock/entities.json (그리기 · 모델 · 동작 ·
빛나는 텍스처 목록) · assets/mmorpg/textures/entity/rpg/allies/<키>(_emissive).png.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from monster_kit import Mat, Model, anim_file, face_rects, kf, write_json  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "monster_art")
RES = os.path.join(ROOT, "src", "main", "resources")
BEHAVIOR = os.path.join(RES, "mmorpg", "entities.json")
CLIENT = os.path.join(RES, "assets", "mmorpg", "bedrock", "entities.json")
TEX = os.path.join(RES, "assets", "mmorpg", "textures", "entity", "rpg", "allies")

LT = "q.life_time"
MS = "q.modified_move_speed"
DM = "q.modified_distance_moved"

RED = (214, 40, 58)
RED_D = (150, 22, 40)
GOLD = (246, 196, 58)
GOLD_D = (196, 140, 30)
TEAL = (40, 196, 184)
WHITE = (246, 242, 234)
INK = (34, 22, 40)


def walk_amt(k=3):
    return f"math.min({MS} * {k}, 1)"


# ---------------------------------------------------------------- 무늬 (칠한 뒤 면에 덧칠)

PATTERNS = []  # (상자, 면, 함수(x, y, w, h) → 색 | None)


def pattern(box, faces, fn):
    for f in faces:
        PATTERNS.append((box, f, fn))


def harlequin(a, b, dw, dh, line=None):
    """할리퀸 마름모 (폭 dw · 높이 dh — 면 가운데 줄에 a 마름모, 그 사이는 b) · 마름모 테두리 가는 줄 (line)"""
    def fn(x, y, w, h):
        cx = x + 0.5 - (w / 2 - dw / 2) % dw
        cy = y + 0.5 - h / 2
        s = cx / dw + cy / dh
        t = cx / dw - cy / dh
        fs, ft = s - int(s // 1), t - int(t // 1)
        c = a if (int(s // 1) + int(t // 1)) % 2 == 0 else b
        if line is not None and (min(fs, 1 - fs) * dw < 0.55 or min(ft, 1 - ft) * dw < 0.55):
            c = line
        k2 = 1.06 - 0.14 * (y / max(1, h - 1))  # 아래로 갈수록 조금 어둡게 (입체감)
        return tuple(min(255, int(v * k2)) for v in c)
    return fn


def stripes(a, b, band=2, vertical=False):
    def fn(x, y, w, h):
        t = x if vertical else y
        return a if (t // band) % 2 == 0 else b
    return fn


def apply_patterns(im):
    px = im.load()
    for box, face, fn in PATTERNS:
        u, v, w, h = face_rects(box)[face]
        for y in range(h):
            for x in range(w):
                c = fn(x, y, w, h)
                if c is None:
                    continue
                a = px[u + x, v + y][3]
                px[u + x, v + y] = (c[0], c[1], c[2], a if len(c) < 4 else c[3])


# ================================================================= 깜짝 상자

def box():
    m = Model("pierrot_box", tex_w=128)
    red = Mat(RED, "flat", noise=4, edge=0.86, seed=101)
    gold = Mat(GOLD, "metal", noise=5, edge=0.82, seed=102)
    goldglow = Mat(GOLD, "metal", noise=3, edge=0.9, glow=0.55, seed=103)
    face = Mat(WHITE, "skin", noise=3, accent=(232, 220, 214), edge=0.95, seed=104)
    teal = Mat(TEAL, "flat", noise=4, edge=0.86, seed=105)
    redhat = Mat(RED, "knit", noise=4, accent=RED_D, seed=106)
    wood = Mat((120, 70, 40), "wood", noise=5, seed=107)
    spring = Mat((214, 206, 196), "metal", noise=4, edge=0.8, seed=108)

    root = m.bone("root", [0, 0, 0])
    bx = m.bone("box", [0, 0, 0], "root")
    body = bx.box([-7, 0, -7], [14, 11, 14], red, faces={"up": Mat(INK, "flat", noise=2, seed=109), "down": wood})
    pattern(body, ("north", "south", "east", "west"), harlequin(RED, GOLD, 7, 11, line=GOLD_D))
    # 금테: 위 · 아래 둘레
    bx.box([-7, 10, -7], [14, 1, 14], gold, inflate=0.3)
    bx.box([-7, 0, -7], [14, 1, 14], gold, inflate=0.3)
    for sx in (-1, 1):
        for sz in (-1, 1):
            bx.box([6.5 * sx - 0.5, 0, 6.5 * sz - 0.5], [1, 11, 1], gold, inflate=0.35)
    # 옆 손잡이 (왼쪽 +x): 축 · 팔 · 손잡이 공
    crank = m.bone("crank", [7.5, 6, 0], "box")
    crank.box([7.5, 5.5, -0.5], [2, 1, 1], gold)
    crank.box([9, 2.5, -0.5], [1, 4, 1], gold)
    crank.box([9.5, 1.5, -1], [2, 2, 2], Mat(RED_D, "flat", noise=3, seed=110))
    # 뚜껑 (뒤쪽 위 모서리가 경첩 — 열리면 뒤로 젖혀짐)
    lid = m.bone("lid", [0, 11, 7], "box")
    lid_top = lid.box([-7.5, 11, -7.5], [15, 2, 15], red, faces={"down": Mat(RED_D, "flat", noise=3, seed=111)})
    pattern(lid_top, ("north", "south", "east", "west"), stripes(GOLD, GOLD_D, 1))

    def star(x, y, w, h):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        dx, dy = abs(x - cx), abs(y - cy)
        if dx + dy <= 2.6 or (dx < 0.6 and dy <= 5.2) or (dy < 0.6 and dx <= 5.2) or (abs(dx - dy) < 0.6 and dx <= 3.4):
            return GOLD
        return RED if (x + y) % 2 else tuple(int(v * 0.92) for v in RED)
    pattern(lid_top, ("up",), star)
    lid.box([-1.5, 11.5, -8.5], [3, 1, 1], gold)  # 걸쇠
    # 용수철 여덟 고리 (닫히면 상자 안 — 열리면 2칸씩 벌어져 늘어남, 고리마다 좌우로 조금 어긋나 감긴 꼴 · 영상 1: 넷이면 4칸씩 떨어진 판으로 보였음)
    SPRINGS = 8
    for i in range(SPRINGS):
        sp = m.bone(f"spring{i}", [0, 8 + i * 0.25, 0], "box")
        sp.box([-2.5 + (0.45 if i % 2 else -0.45), 7 + i * 0.3, -2.5], [5, 1, 5], spring, inflate=-0.1)
    # 용수철 광대 머리 (닫히면 상자 안: 머리 밑 y 2)
    head = m.bone("head", [0, 2, 0], "box")
    ruff = head.box([-5, 2, -5], [10, 2, 10], Mat(WHITE, "flat", noise=3, seed=112), inflate=0.2)
    pattern(ruff, ("north", "south", "east", "west"), lambda x, y, w, h: GOLD if (x + y) % 3 == 0 else WHITE)
    eye = TEAL
    hd = head.box([-4, 4, -4], [8, 8, 8], face, decals=[
        # 눈 (마름모 — 청록 빛) · 눈 위 검은 선 · 볼 · 웃는 입 (빨강 · 검은 테)
        ("north", 1, 2, INK), ("north", 2, 1, INK), ("north", 2, 3, eye, 0.7), ("north", 2, 2, eye, 0.9), ("north", 3, 2, INK),
        ("north", 4, 2, INK), ("north", 5, 1, INK), ("north", 5, 3, eye, 0.7), ("north", 5, 2, eye, 0.9), ("north", 6, 2, INK),
        ("north", 1, 4, (236, 150, 160)), ("north", 6, 4, (236, 150, 160)),
        ("north", 1, 5, INK), ("north", 2, 6, RED), ("north", 3, 6, RED), ("north", 4, 6, RED), ("north", 5, 6, RED), ("north", 6, 5, INK),
        ("north", 2, 7, INK), ("north", 3, 7, RED_D), ("north", 4, 7, RED_D), ("north", 5, 7, INK)])
    head.box([-1, 6.5, -5], [2, 2, 1], Mat((230, 30, 44), "flat", noise=2, edge=0.95, seed=113), inflate=0.15)  # 빨간 코
    # 모자: 바탕 (반은 빨강 · 반은 청록) · 두 뿔 (바깥으로 휘어 처짐) · 끝 금방울
    head.box([-4.5, 12, -4.5], [9, 2, 9], redhat, faces={"east": teal, "south": teal})
    for side, sx in (("hornR", -1), ("hornL", 1)):
        mat = redhat if sx < 0 else teal
        h1 = m.bone(side, [2.5 * sx, 14, 0], "head", [0, 0, 28 * sx])
        h1.box([2.5 * sx - 1.5, 14, -1.5], [3, 4, 3], mat)
        h2 = m.bone(side + "2", [2.5 * sx, 18, 0], side, [0, 0, 32 * sx])
        h2.box([2.5 * sx - 1, 18, -1], [2, 4, 2], mat.with_(seed=114 + sx))
        bell = m.bone(side + "Bell", [2.5 * sx, 22, 0], side + "2", [0, 0, 18 * sx])
        bell.box([2.5 * sx - 1, 21.5, -1], [2, 2, 2], goldglow)

    sway = f"math.sin({LT} * 120)"
    anims = {
        # 닫힌 상자: 머리 (모자 뿔까지) 를 0.01 배로 숨김 — 열림 (pop) 이 머리 크기를 100 배로 곱해 되돌림 (뼈 크기는 동작끼리 곱함)
        "idle": {"loop": True, "bones": {
            "box": {"rotation": [0, 0, f"{sway} * 1.5"]},
            "head": {"rotation": [f"math.sin({LT} * 95) * 5", f"math.sin({LT} * 60) * 8", f"math.sin({LT} * 140) * 7"], "scale": [0.01, 0.01, 0.01]},
            "hornR": {"rotation": [0, 0, f"math.sin({LT} * 150) * 6"]},
            "hornL": {"rotation": [0, 0, f"math.sin({LT} * 150 + 60) * 6"]},
            "hornRBell": {"rotation": [f"math.sin({LT} * 300) * 12", 0, 0]},
            "hornLBell": {"rotation": [f"math.sin({LT} * 300 + 90) * 12", 0, 0]},
            "crank": {"rotation": [f"{LT} * 90", 0, 0]},
        }},
        # 떨어져 섬: 위에서 떨어져 (0.15초) 통통 튀고 납작해졌다 펴짐
        "drop": {"animation_length": 0.4, "bones": {
            "root": {"position": kf([(0, [0, 26, 0]), (0.15, [0, 0, 0]), (0.24, [0, 3, 0]), (0.32, [0, 0, 0])]),
                     "rotation": kf([(0, [0, 0, 18]), (0.15, [0, 0, 0]), (0.24, [-4, 0, 0]), (0.32, [0, 0, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.15, [1.15, 0.8, 1.15]), (0.22, [0.94, 1.08, 0.94]), (0.32, [1.03, 0.97, 1.03]), (0.4, [1, 1, 1])])},
        }},
        # 열림 (끝 자세 유지): 뚜껑이 뒤로 젖혀지고 (−115°) 용수철이 늘어나며 머리가 솟음 (넘쳤다 돌아옴)
        "pop": {"loop": "hold_on_last_frame", "animation_length": 0.45, "bones": {
            "lid": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-125, 0, 0]), (0.18, [-108, 0, 0]), (0.26, [-117, 0, 0]), (0.45, [-115, 0, 0])])},
            **{f"spring{i}": {"position": kf([(0, [0, 0, 0]), (0.12, [0, round(2.0 * (i + 1) * 1.156, 2), 0]), (0.22, [0, round(2.0 * (i + 1) * 0.9, 2), 0]),
                                                (0.45, [0, 2.0 * (i + 1), 0])])} for i in range(SPRINGS)},
            "head": {"position": kf([(0, [0, 0, 0]), (0.12, [0, 21, 0]), (0.22, [0, 16.5, 0]), (0.32, [0, 18.6, 0]), (0.45, [0, 18, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.03, [40, 40, 40]), (0.12, [112, 112, 112]), (0.25, [97, 97, 97]), (0.45, [100, 100, 100])])},
        }},
        # 칼 던지기: 고개를 젖혔다 (0.1) 앞으로 (0.2 — 칼이 나감) 돌아옴
        "throw": {"animation_length": 0.32, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-24, 0, 0]), (0.2, [30, 0, 0]), (0.32, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.1, [0, 0, 1.5]), (0.2, [0, 0, -2.5]), (0.32, [0, 0, 0])])},
        }},
        # 불꽃놀이: 상자가 움찔 (납작 → 늘어남) · 머리를 크게 끄덕
        "fire": {"animation_length": 0.32, "bones": {
            "box": {"scale": kf([(0, [1, 1, 1]), (0.08, [1.08, 0.88, 1.08]), (0.18, [0.96, 1.07, 0.96]), (0.32, [1, 1, 1])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.08, [-20, 0, 0]), (0.18, [16, 0, 0]), (0.32, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 광대 인형

def doll():
    m = Model("pierrot_doll", tex_w=128)
    red = Mat(RED, "flat", noise=4, edge=0.86, seed=201)
    gold = Mat(GOLD, "flat", noise=4, edge=0.86, seed=202)
    face = Mat(WHITE, "skin", noise=3, accent=(232, 220, 214), edge=0.95, seed=203)
    white = Mat(WHITE, "flat", noise=3, edge=0.9, seed=204)
    teal = Mat(TEAL, "flat", noise=4, edge=0.86, seed=205)
    shoe = Mat(RED_D, "flat", noise=3, edge=0.85, seed=206)
    goldglow = Mat(GOLD, "metal", noise=3, edge=0.9, glow=0.55, seed=207)
    string = Mat((255, 236, 170), "flat", noise=2, edge=1.0, glow=0.85, seed=208)
    hammer_red = Mat((236, 44, 52), "flat", noise=3, edge=0.88, seed=209)
    handle = Mat((246, 240, 228), "flat", noise=2, edge=0.9, seed=210)

    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 10, 0], "root")
    torso = body.box([-4, 10, -2.5], [8, 8, 5], red)
    pattern(torso, ("north", "south"), harlequin(RED, GOLD, 4, 8, line=GOLD_D))
    pattern(torso, ("east", "west"), harlequin(GOLD, RED, 5, 8, line=GOLD_D))
    # 흰 방울 단추 셋 · 허리띠
    for y in (11.5, 13.5, 15.5):
        body.box([-1, y, -3.4], [2, 2, 1], white, inflate=-0.1)
    body.box([-4, 10, -2.5], [8, 1, 5], Mat(INK, "flat", noise=2, seed=211), inflate=0.15)
    # 주름 깃 (흰 · 금 톱니)
    ruff = body.box([-5.5, 17.5, -3.5], [11, 2, 7], white, inflate=0.1)
    pattern(ruff, ("north", "south", "east", "west"), lambda x, y, w, h: GOLD if (x % 2 == 0) == (y % 2 == 0) else WHITE)
    head = m.bone("head", [0, 19.5, 0], "body")
    eye = TEAL
    head.box([-3.5, 19.5, -3.5], [7, 7, 7], face, decals=[
        ("north", 1, 2, eye, 0.9), ("north", 1, 1, INK), ("north", 0, 2, INK), ("north", 2, 2, INK), ("north", 1, 3, INK),
        ("north", 5, 2, eye, 0.9), ("north", 5, 1, INK), ("north", 4, 2, INK), ("north", 6, 2, INK), ("north", 5, 3, INK),
        ("north", 0, 4, (236, 150, 160)), ("north", 6, 4, (236, 150, 160)),
        ("north", 1, 5, INK), ("north", 2, 6, RED), ("north", 3, 6, RED), ("north", 4, 6, RED), ("north", 5, 5, INK)])
    head.box([-1, 21.5, -4.6], [2, 2, 1], Mat((230, 30, 44), "flat", noise=2, edge=0.95, seed=212), inflate=0.2)  # 빨간 코
    # 모자 (반 빨강 · 반 청록) · 두 뿔 · 금방울
    head.box([-4, 26.5, -4], [8, 2, 8], red, faces={"west": teal, "south": teal})
    for side, sx in (("hornR", -1), ("hornL", 1)):
        mat = red if sx < 0 else teal
        h1 = m.bone(side, [2 * sx, 28.5, 0], "head", [0, 0, 30 * sx])
        h1.box([2 * sx - 1.5, 28.5, -1.5], [3, 4, 3], mat)
        h2 = m.bone(side + "2", [2 * sx, 32.5, 0], side, [0, 0, 34 * sx])
        h2.box([2 * sx - 1, 32.5, -1], [2, 4, 2], mat.with_(seed=213 + sx))
        bell = m.bone(side + "Bell", [2 * sx, 36.5, 0], side + "2", [0, 0, 16 * sx])
        bell.box([2 * sx - 1, 36, -1], [2, 2, 2], goldglow)
    # 팔 (오른팔 금빛 · 왼팔 빨강 — 몸과 엇갈림) · 흰 장갑
    for side, sx in (("rightArm", -1), ("leftArm", 1)):
        arm = m.bone(side, [4.5 * sx, 17.5, 0], "body")
        x0 = -7 if sx < 0 else 4
        sleeve = arm.box([x0, 12, -1.5], [3, 6, 3], gold if sx < 0 else red)
        pattern(sleeve, ("north", "south", "east", "west"), stripes(GOLD if sx < 0 else RED, GOLD_D if sx < 0 else RED_D, 2))
        arm.box([x0 - 0.5, 16, -2], [4, 2, 4], white, inflate=0.1)  # 어깨 주름
        arm.box([x0, 9, -1.5], [3, 3, 3], white)  # 장갑
    # 다리 (오른다리 빨강 · 왼다리 금빛) · 끝이 말린 구두
    for side, sx in (("rightLeg", -1), ("leftLeg", 1)):
        leg = m.bone(side, [2 * sx, 10, 0], "body")
        x0 = -3.5 if sx < 0 else 0.5
        lg = leg.box([x0, 2, -1.5], [3, 8, 3], red if sx < 0 else gold)
        pattern(lg, ("north", "south", "east", "west"), stripes(RED if sx < 0 else GOLD, RED_D if sx < 0 else GOLD_D, 2))
        leg.box([x0 - 0.5, 0, -3.5], [4, 2, 5], shoe)
        leg.box([x0 + 0.5, 1.5, -4.5], [2, 1, 2], shoe.with_(seed=214 + sx))  # 말린 앞코
        leg.box([x0 + 0.5, 2, -4.5], [2, 1, 1], white)  # 앞코 방울
    # 뿅망치 (오른손 — 자루가 손에서 아래로, 앞으로 70° 기울여 쥠): 흰 자루 · 빨강 머리 (앞뒤로 긺) · 양끝 노랑 띠
    ham = m.bone("hammer", [-5.5, 10.5, 0], "rightArm", [-70, 0, 0])
    ham.box([-6, -1.5, -0.5], [1, 12, 1], handle)
    hh = ham.box([-7.5, -5.5, -3.5], [4, 4, 7], hammer_red)
    pattern(hh, ("east", "west", "up", "down"), lambda x, y, w, h: GOLD if (x if True else y) in (1, w - 2) else None)
    ham.box([-7.5, -5.5, -4.5], [4, 4, 1], Mat(GOLD, "flat", noise=2, edge=0.9, seed=215), inflate=0.2)
    ham.box([-7.5, -5.5, 3.5], [4, 4, 1], Mat(GOLD, "flat", noise=2, edge=0.9, seed=216), inflate=0.2)
    # 꼭두각시 줄 (금빛 — 빛남) · 십자 손잡이 (머리 위 높이)
    ctl = m.bone("control", [0, 44, 0], "root")
    ctl.box([-7, 44, -0.5], [14, 1, 1], Mat((120, 70, 40), "wood", noise=4, seed=217))
    ctl.box([-0.5, 44, -5], [1, 1, 10], Mat((120, 70, 40), "wood", noise=4, seed=218))
    for x, z, y0, y1 in ((-5.5, 0, 18, 44), (5.5, 0, 18, 44), (0, -4.5, 29, 44)):
        ctl.box([x - 0.5, y0, z - 0.5], [1, y1 - y0, 1], string, inflate=-0.38)

    puppet = f"math.sin({LT} * 160)"
    anims = {
        "idle": {"loop": True, "bones": {
            "root": {"position": [0, f"math.abs({puppet}) * 0.8", 0]},
            "body": {"rotation": [0, 0, f"math.sin({LT} * 80) * 3"]},
            "head": {"rotation": [f"math.sin({LT} * 70) * 6", f"math.sin({LT} * 45) * 10", f"math.sin({LT} * 110) * 8"]},
            "rightArm": {"rotation": [f"math.sin({LT} * 160) * 10 - 8", 0, f"8 + math.sin({LT} * 90) * 6"]},
            "leftArm": {"rotation": [f"math.sin({LT} * 160 + 90) * 10", 0, f"-12 - math.sin({LT} * 90) * 8"]},
            "hornR": {"rotation": [0, 0, f"math.sin({LT} * 150) * 7"]},
            "hornL": {"rotation": [0, 0, f"math.sin({LT} * 150 + 70) * 7"]},
            "hornRBell": {"rotation": [f"math.sin({LT} * 280) * 14", 0, 0]},
            "hornLBell": {"rotation": [f"math.sin({LT} * 280 + 90) * 14", 0, 0]},
            "control": {"rotation": [f"math.sin({LT} * 60) * 6", f"math.sin({LT} * 40) * 10", f"math.sin({LT} * 80) * 5"]},
        }},
        "walk": {"loop": True, "bones": {
            "rightLeg": {"rotation": [f"math.cos({DM} * 60) * 40 * {MS}", 0, 0]},
            "leftLeg": {"rotation": [f"-math.cos({DM} * 60) * 40 * {MS}", 0, 0]},
            "leftArm": {"rotation": [f"math.cos({DM} * 60) * 30 * {MS}", 0, 0]},
            "rightArm": {"rotation": [f"-math.cos({DM} * 60) * 14 * {MS}", 0, 0]},
            "root": {"position": [0, f"math.abs(math.sin({DM} * 60)) * 1.6 * {walk_amt()}", 0]},
            "body": {"rotation": [0, 0, f"math.sin({DM} * 60) * 6 * {walk_amt()}"]},
        }},
        # 뿅망치 내려치기: 0.25 머리 위로 크게 감음 → 0.45 땅을 내리침 (HAMMER_AT 9틱) → 0.75 돌아옴
        "attack": {"animation_length": 0.75, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-175, 0, -10]), (0.36, [-185, 0, -8]), (0.45, [-25, 0, 6]), (0.6, [-35, 0, 4]),
                                          (0.75, [0, 0, 0])])},
            "hammer": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-30, 0, 0]), (0.36, [-40, 0, 0]), (0.45, [40, 0, 0]), (0.6, [30, 0, 0]), (0.75, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-14, 0, 0]), (0.45, [20, 0, 0]), (0.6, [14, 0, 0]), (0.75, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-16, 0, 0]), (0.45, [12, 0, 0]), (0.75, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-40, 0, -30]), (0.45, [20, 0, -20]), (0.75, [0, 0, 0])])},
            "root": {"position": kf([(0, [0, 0, 0]), (0.25, [0, 2, 0]), (0.45, [0, -0.5, 0]), (0.6, [0, 0, 0])])},
        }},
        # 하늘에서 떨어져 내려섬 (줄이 내려오는 꼴 — 팔다리가 흔들림)
        "drop": {"animation_length": 0.55, "bones": {
            "root": {"position": kf([(0, [0, 48, 0]), (0.3, [0, 0, 0]), (0.4, [0, 4, 0]), (0.5, [0, 0, 0])]),
                     "scale": kf([(0, [1, 1, 1]), (0.3, [1.12, 0.85, 1.12]), (0.4, [0.96, 1.06, 0.96]), (0.55, [1, 1, 1])])},
            "rightArm": {"rotation": kf([(0, [0, 0, 60]), (0.3, [0, 0, 20]), (0.45, [0, 0, 70]), (0.55, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, -60]), (0.3, [0, 0, -20]), (0.45, [0, 0, -70]), (0.55, [0, 0, 0])])},
            "rightLeg": {"rotation": kf([(0, [30, 0, 0]), (0.3, [0, 0, 0])])},
            "leftLeg": {"rotation": kf([(0, [-30, 0, 0]), (0.3, [0, 0, 0])])},
        }},
        # 놀림: 두 팔을 번갈아 흔들며 깡충 · 고개를 갸웃
        "taunt": {"animation_length": 1.0, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-150, 0, -20]), (0.4, [-110, 0, -40]), (0.6, [-150, 0, -20]), (0.8, [-110, 0, -40]), (1.0, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-110, 0, 40]), (0.4, [-150, 0, 20]), (0.6, [-110, 0, 40]), (0.8, [-150, 0, 20]), (1.0, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [0, 0, 18]), (0.5, [0, 0, -18]), (0.75, [0, 0, 18]), (1.0, [0, 0, 0])])},
            "root": {"position": kf([(0, [0, 0, 0]), (0.15, [0, 3, 0]), (0.3, [0, 0, 0]), (0.65, [0, 3, 0]), (0.8, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 쓰기

BEHAVIOR_DEFS = {
    "rpg:pierrot_box": {"family": ["rpg_ally", "pierrot_summon", "pierrot_box"], "health": 60, "movement": 0.0, "box": [0.85, 0.75], "walk": False},
    "rpg:pierrot_doll": {"family": ["rpg_ally", "pierrot_summon", "pierrot_doll"], "health": 120, "movement": 0.3, "box": [0.7, 1.55], "walk": True},
}
ANIMATE = {"pierrot_box": ["idle"], "pierrot_doll": ["idle", "walk"]}


def behavior(eid, d):
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
        "minecraft:damage_sensor": {"triggers": [{"cause": "fall", "deals_damage": "no"}, {"cause": "suffocation", "deals_damage": "no"},
                                                 {"cause": "drowning", "deals_damage": "no"}]},
        "minecraft:experience_reward": {"on_death": "0"},
    }
    if d["walk"]:
        c["minecraft:movement.basic"] = {}
        c["minecraft:navigation.walk"] = {"can_path_over_water": True, "avoid_water": True, "avoid_damage_blocks": True}
    return {"group": "allies", "properties": {}, "components": c, "component_groups": {}, "events": {}}


def build():
    out = {}
    for fn in (box, doll):
        PATTERNS.clear()
        m, anims = fn()
        m.pack()
        im = m.paint()
        apply_patterns(im)
        key = m.key
        d = os.path.join(ART, key)
        os.makedirs(d, exist_ok=True)
        geo = m.geo_json()
        write_json(os.path.join(d, key + ".geo.json"), geo)
        im.save(os.path.join(d, key + ".png"))
        write_json(os.path.join(d, key + ".animation.json"), anim_file(key, anims))
        out[key] = (geo, im, anims)
    return out


def install(art):
    beh = json.load(open(BEHAVIOR, encoding="utf-8"))
    for eid, d in BEHAVIOR_DEFS.items():
        beh[eid] = behavior(eid, d)
    with open(BEHAVIOR, "w", encoding="utf-8") as f:
        json.dump(beh, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    cl = json.load(open(CLIENT, encoding="utf-8"))
    os.makedirs(TEX, exist_ok=True)
    for key, (geo, im, anims) in art.items():
        eid = "rpg:" + key
        tex = f"textures/entity/rpg/allies/{key}"
        cl["entities"][eid] = {"identifier": eid, "materials": {"default": "entity_emissive"}, "textures": {"default": tex},
                               "geometry": {"default": f"geometry.rpg.monster.{key}"},
                               "animations": {n: f"animation.rpg.monster.{key}.{n}" for n in anims},
                               "scripts": {"animate": ANIMATE[key]}, "render_controllers": ["controller.render.default"]}
        g = geo["minecraft:geometry"][0]
        cl["models"][g["description"]["identifier"]] = {"description": g["description"], "bones": g["bones"]}
        for n, a in anims.items():
            cl["animations"][f"animation.rpg.monster.{key}.{n}"] = a
        # 텍스처 · 빛나는 칸 (알파 255 보다 낮은 칸 — 베드락 entity_emissive)
        base = im.copy()
        emi = Image.new("RGBA", im.size, (0, 0, 0, 0))
        bp, ep = base.load(), emi.load()
        glow = False
        for y in range(im.size[1]):
            for x in range(im.size[0]):
                r, g2, b, a = bp[x, y]
                if 0 < a < 255:
                    ep[x, y] = (r, g2, b, 255)
                    bp[x, y] = (r, g2, b, 255)
                    glow = True
        base.save(os.path.join(TEX, key + ".png"))
        if glow:
            emi.save(os.path.join(TEX, key + "_emissive.png"))
            if tex not in cl["emissive"]:
                cl["emissive"].append(tex)
    with open(CLIENT, "w", encoding="utf-8") as f:
        json.dump(cl, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


def main():
    args = sys.argv[1:]
    art = build()
    install(art)
    print("pierrot art:", ", ".join(art))
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
        import bedrock_preview as BP
        for key, poses in (("pierrot_box", [(["idle"], 0.0), (["idle", "pop"], 0.45), (["idle", "pop", "throw"], 0.2), (["drop"], 0.1)]),
                           ("pierrot_doll", [(["idle"], 0.0), (["idle", "attack"], 0.25), (["idle", "attack"], 0.45), (["idle", "taunt"], 0.2)])):
            geo, im, anims = art[key]
            for names, t in poses:
                out = os.path.join(folder, f"{key}_{'_'.join(names)}_{t:.2f}.png")
                print("  preview:", BP.sheet(geo, im, anims, names, t, out, label=key))


if __name__ == "__main__":
    main()
