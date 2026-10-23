#!/usr/bin/env python3
"""0.32.0 공허 몬스터 5종 그림 (모델 · 텍스처 · 애니메이션) — tools/monster_kit.py 로 짬. tools/monster_art.py 의 MAKERS 에 들어감.

세계관: 신화의 세계에 공허 (긴눙가가프의 균열) 가 스며듦 — 검보라 갑각 · 분홍빛 발광 이음새 · 균열 · 촉수 · 포식. 특정 작품의 캐릭터를 옮기지 않은 오리지널.
  void_leaper   공허 도약충 Lv42 — 벼룩 · 진드기꼴 작은 벌레: 등의 빛나는 독주머니 · 뛰는 뒷다리 · 턱 · 네 눈 (동작 leap_prep · leap · screech)
  void_stalker  공허 추적자 Lv48 — 칼날 팔 두 발 사냥꾼: 갈비뼈 판 사이 빛나는 핵 · 세 줄 눈 틈 가면 · 등 지느러미 판 (동작 vanish · appear)
  void_jelly    공허 해파리 Lv52 — 떠다니는 종: 아래로 드러난 빛나는 핵 · 빛나는 테 · 촉수 여덟 · 굵은 팔 둘 (동작 cast)
  void_borer    공허 굴착자 Lv55 — 지네꼴 굴착 벌레: 세 갈래 드릴 턱 · 마디 사이 빛 · 등 수정 (동작 burrow · emerge)
  void_behemoth 공허 거수 Lv58 — 네 발 갑각 거수: 등의 공허 수정 숲 · 앞으로 뻗은 엄니 · 빛나는 아가리 (동작 slam · roar)

미리보기: python3 tools/monster_art.py --only void_leaper,... --preview 그림.png (빛나는 칸을 밝게 — preview_glow)
"""
from monster_kit import Mat, Model, kf

MS = "q.modified_move_speed"
DM = "q.modified_distance_moved"
LT = "q.life_time"
LOOK_X = "q.target_x_rotation"
LOOK_Y = "q.target_y_rotation"


def walk_amt(k=3):
    return f"math.min({MS} * {k}, 1)"


# ---------------------------------------------------------------- 공허 색
SEAM = "#ff4fd8"      # 분홍 발광 (이음새)
VIOLET = "#b46cff"    # 보라 발광
CORE = "#ffb0f2"      # 핵 (분홍빛 흰색)
EYE = (255, 225, 255)
EYE2 = (255, 120, 235)
DARK = "#1b1430"
SHELL = "#2c2149"
SHELL2 = "#3a2b60"
PLATE = "#4a3676"
BELLY = "#221a38"
BONE = "#d6cdee"
BONE2 = "#a796cf"


def shell(seed, base=SHELL, **kw):
    o = dict(seam=SEAM, seam_band=4, seam_glow=0.7, seam_gap=0.3)
    o.update(kw)
    return Mat(base, "chitin", noise=6, band=3, accent="#160f28", seed=seed, **o)


def plate(seed, base=PLATE, **kw):
    o = dict(rim=VIOLET, rim_glow=0.45)
    o.update(kw)
    return Mat(base, "plates", noise=5, band=3, accent="#241a40", edge=0.9, seed=seed, **o)


def glowsac(seed, base="#e44ad0", **kw):
    o = dict(veins=3, vein="#ffe0fb", vein_glow=1.0)
    o.update(kw)
    return Mat(base, "skin", noise=6, accent="#a02890", glow=0.72, edge=0.95, seed=seed, **o)


def core(seed, base="#ff6ee6", **kw):
    o = dict(spots=4, spot="#ffffff", spot_glow=1.0)
    o.update(kw)
    return Mat(base, "flat", noise=8, glow=1.0, edge=1.0, seed=seed, **o)


def bone(seed, base=BONE, **kw):
    return Mat(base, "bone", noise=5, edge=0.88, seed=seed, **kw)


def blade(seed, base=BONE, **kw):
    o = dict(rim=SEAM, rim_glow=0.8)
    o.update(kw)
    return Mat(base, "metal", noise=4, edge=0.9, seed=seed, **o)


def crystal(seed, base="#8e4cff", glow=0.5, **kw):
    """공허 수정: 깎은 면 (monster_kit pattern "crystal" — 위가 밝고 한쪽 모서리가 빛남 · 가운데 심, seam = 심 색)"""
    o = dict(seam="#f6d4ff")
    o.update(kw)
    return Mat(base, "crystal", noise=3, edge=1.0, glow=glow, seed=seed, **o)


def shard(m, name, parent, base, size, mat, tip, rot, seed):
    """수정 한 개 — 밑동 가운데 base [x, y, z] 를 축으로 rot 만큼 기울인 뼈: 몸 (키의 60%) → 좁은 몸 (폭 3 넘으면) → 빛나는 끝 (폭 1)"""
    w, h, d = size
    b = m.bone(name, list(base), parent, list(rot))
    oy = base[1]
    body_h = h if w == 1 else max(2, round(h * 0.6))
    b.box([base[0] - w / 2, oy, base[2] - d / 2], [w, body_h, d], mat.with_(seed=seed))
    y = oy + body_h
    if w >= 3:
        w2 = w - 2 if w >= 4 else w - 1
        d2 = d - 2 if d >= 4 else d - 1
        h2 = max(1, round(h * 0.25))
        b.box([base[0] - w2 / 2, y, base[2] - d2 / 2], [w2, h2, d2], mat.with_(seed=seed + 1))
        y += h2
    if w >= 2:
        b.box([base[0] - 0.5, y, base[2] - 0.5], [1, int(max(2, oy + h - y)), 1], tip.with_(seed=seed + 2))
    return b


def flat(seed, base=DARK, **kw):
    return Mat(base, "flat", noise=4, seed=seed, **kw)


def void_common(fn):
    """다섯 공통 동작 (뿌리 뼈마다):
    spawn — 균열에서 비집고 나옴 (VoidMonsters.spawnFx: 몸 뒤 0.5 × 크기 칸 = 8 픽셀에 균열): 0.22초는 보이지 않다가 좁고 작게 균열 자리에서 → 부풀며 앞으로 (0.8초)
    die — 구멍으로 빨려 듦: 살짝 눌렸다가 비틀리며 오그라들어 몸 가운데 (모델 높이의 절반) 로 (0.75초 · 끝 자세 유지 — 바닐라 쓰러짐 대신, BedrockRenderer)"""
    def make():
        m, anims = fn()
        roots = [b for b in m.bones if not b.parent]
        top = max(bx.origin[1] + bx.size[1] for bone in m.bones for bx in bone.boxes)
        spawn = {}
        die = {}
        for r in roots:
            rise = round(max(0.0, top * 0.5 - r.pivot[1]), 2)
            z = 0.001
            spawn[r.name] = {
                "scale": kf([(0, [z, z, z]), (0.22, [z, z, z]), (0.24, [0.15, 0.7, 0.35]), (0.5, [1.12, 1.05, 1.12]), (0.66, [0.97, 1.0, 0.97]), (0.8, [1, 1, 1])]),
                "position": kf([(0, [0, 0, 8]), (0.24, [0, 0, 8]), (0.5, [0, 0, 2]), (0.8, [0, 0, 0])]),
                "rotation": kf([(0, [-14, 0, 0]), (0.24, [-14, 0, 0]), (0.5, [5, 0, 0]), (0.8, [0, 0, 0])]),
            }
            die[r.name] = {
                "scale": kf([(0, [1, 1, 1]), (0.12, [1.12, 0.86, 1.12]), (0.75, [0.02, 0.02, 0.02])]),
                "position": kf([(0, [0, 0, 0]), (0.75, [0, rise, 0])]),
                "rotation": kf([(0, [0, 0, 0]), (0.75, [0, 260, 0])]),
            }
        anims["spawn"] = {"animation_length": 0.8, "bones": spawn}
        anims["die"] = {"animation_length": 0.75, "loop": "hold_on_last_frame", "bones": die}
        return m, anims

    make.__name__ = fn.__name__
    make.__doc__ = fn.__doc__
    return make


# ================================================================= 공허 도약충 (Lv42)

def leg_steps(bone_, sx, x0, y0, z, out, rise, down, mat, foot=None):
    """마디 다리 (계단꼴): 몸 옆 x0 에서 밖으로 out 칸 가며 rise 칸 올라 (무릎) → 땅까지 내려감. sx = -1 오른쪽 · 1 왼쪽"""
    x = x0
    y = y0
    step = max(1, out // max(1, rise)) if rise else out
    seg = 0
    while seg < out:
        n = min(step, out - seg)
        bx = x - n if sx < 0 else x
        bone_.box([bx, y, z - 0.5], [n, 1, 1], mat.with_(seed=mat.seed + seg))
        x = x - n if sx < 0 else x + n
        seg += n
        if y < y0 + rise:
            y += 1
    # 무릎에서 땅으로
    kx = x - 1 if sx < 0 else x
    bone_.box([kx, 0, z - 0.5], [1, int(y) + 1, 1], mat.with_(seed=mat.seed + 50))
    if foot is not None:
        bone_.box([kx - (1 if sx < 0 else 0), 0, z - 1], [2, 1, 2], foot)
    return kx


@void_common
def void_leaper():
    m = Model("void_leaper")
    sh = shell(301)
    sh2 = shell(302, SHELL2, seam_band=3)
    pl = plate(303, rim=None, seam=VIOLET, seam_band=3, seam_glow=0.5, seam_gap=0.4)
    belly = flat(304, BELLY)
    sac = glowsac(305, base="#c4329e", veins=4)
    leg = Mat("#2e2350", "chitin", noise=5, band=2, accent="#1a1230", seed=306)
    jaw = bone(307, rim=SEAM, rim_glow=0.55)
    spike = Mat("#4a3676", "flat", noise=4, seed=320, rim=SEAM, rim_glow=0.6)

    body = m.bone("body", [0, 5, 0])
    body.box([-4, 3, -4], [8, 4, 8], sh, faces={"down": belly})
    body.box([-4.5, 7, -3.5], [9, 1, 6], pl)
    for x, z in ((-3, -2), (2, -2), (-1, 0)):
        body.box([x, 8, z], [1, 2 if x != -1 else 1, 1], spike)
    head = m.bone("head", [0, 5, -4], "body")
    eyes = [("north", 1, 1, EYE, 1.0), ("north", 4, 1, EYE, 1.0), ("north", 1, 2, EYE2, 0.9), ("north", 4, 2, EYE2, 0.9),
            ("north", 2, 1, EYE2, 0.7), ("north", 3, 1, EYE2, 0.7), ("north", 2, 3, "#0e0918"), ("north", 3, 3, "#0e0918")]
    head.box([-3, 3, -9], [6, 4, 5], sh2, decals=eyes)
    head.box([-2.5, 7, -8], [5, 1, 3], pl.with_(seed=308))
    for sx in (-1, 1):
        j = m.bone("rightJaw" if sx < 0 else "leftJaw", [2 * sx, 4, -9], "head", [0, -16 * sx, 0])
        jx = -3 if sx < 0 else 1
        j.box([jx, 3, -13], [2, 2, 4], jaw, decals=[("north", 0, 0, SEAM, 1.0), ("north", 1, 0, SEAM, 1.0)])
        j.box([jx + (1 if sx < 0 else 0), 3, -14], [1, 1, 1], jaw.with_(seed=321), decals=[("north", 0, 0, SEAM, 1.0)])
    # 등의 독주머니 (빛남 · 둥글게 쌓음) + 갑각 띠 셋
    sac_b = m.bone("sac", [0, 6, 4], "body", [-10, 0, 0])
    sac_b.box([-3.5, 4, 4], [7, 6, 6], sac)
    sac_b.box([-2.5, 10, 5], [5, 1, 4], sac.with_(seed=322))
    sac_b.box([-2.5, 5, 10], [5, 4, 1], sac.with_(seed=323))
    for i, z in enumerate((4.5, 7, 9.5)):
        sac_b.box([-4, 9.5 if i != 1 else 10.5, z], [8, 1, 1], sh.with_(seed=309 + i))
    sac_b.box([-1, 5, 11], [2, 2, 2], sh2.with_(seed=312))
    # 다리: 앞 · 가운데는 무릎이 등보다 높은 거미꼴, 뒤는 뛰는 다리 (무릎이 등 위 뒤쪽)
    for sx in (-1, 1):
        side = "right" if sx < 0 else "left"
        f = m.bone(f"{side}LegF", [4 * sx, 5, -3], "body")
        leg_steps(f, sx, -4 if sx < 0 else 4, 5, -3, 4, 3, 0, leg)
        mid = m.bone(f"{side}LegM", [4 * sx, 5, 0], "body")
        leg_steps(mid, sx, -4 if sx < 0 else 4, 5, 0.5, 5, 3, 0, leg.with_(seed=330))
        h = m.bone(f"{side}LegH", [4 * sx, 6, 3], "body")
        hx = -6 if sx < 0 else 4
        h.box([hx, 6, 2], [2, 6, 2], sh2.with_(seed=315))
        h.box([-6.5 if sx < 0 else 4.5, 11, 3], [2, 2, 4], sh2.with_(seed=316), decals=[("up", 0, 3, SEAM, 0.9), ("up", 1, 3, SEAM, 0.9)])
        h.box([-6.5 if sx < 0 else 5.5, 0, 6], [1, 12, 1], leg.with_(seed=317))
        h.box([-7 if sx < 0 else 5, 0, 5.5], [2, 1, 3], leg.with_(seed=318))

    tri = f"math.sin({DM} * 70) * 26 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 160) * 0.3", 0]},
            "head": {"rotation": [f"{LOOK_X} * 0.5", f"{LOOK_Y} * 0.5", 0]},
            "sac": {"scale": [f"1 + math.sin({LT} * 220) * 0.06", f"1 + math.sin({LT} * 220) * 0.08", f"1 + math.sin({LT} * 220) * 0.06"]},
            "rightJaw": {"rotation": [0, f"math.max(0, math.sin({LT} * 300)) * 14", 0]},
            "leftJaw": {"rotation": [0, f"-math.max(0, math.sin({LT} * 300)) * 14", 0]},
        }},
        "walk": {"loop": True, "bones": {
            # 세 다리씩 엇갈려 (앞 오른쪽 · 가운데 왼쪽 · 뒤 오른쪽 / 반대)
            "rightLegF": {"rotation": [0, tri, f"math.max(0, math.sin({DM} * 70)) * 18 * {MS}"]},
            "leftLegM": {"rotation": [0, tri, f"-math.max(0, math.sin({DM} * 70)) * 18 * {MS}"]},
            "rightLegH": {"rotation": [f"-{tri} * 0.5", 0, 0]},
            "leftLegF": {"rotation": [0, f"-{tri}", f"-math.max(0, -math.sin({DM} * 70)) * 18 * {MS}"]},
            "rightLegM": {"rotation": [0, f"-{tri}", f"math.max(0, -math.sin({DM} * 70)) * 18 * {MS}"]},
            "leftLegH": {"rotation": [f"{tri} * 0.5", 0, 0]},
            "body": {"position": [0, f"math.abs(math.sin({DM} * 70)) * 0.6 * {walk_amt()}", 0]},
        }},
        # 물기: 몸을 젖혔다가 (0.15) 턱을 벌리고 앞으로 콱 (0.4 = windup 8틱)
        "attack": {"animation_length": 0.6, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-16, 0, 0]), (0.4, [12, 0, 0]), (0.6, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.4, [0, 0, -2]), (0.6, [0, 0, 0])])},
            "rightJaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 38, 0]), (0.38, [0, 38, 0]), (0.42, [0, -6, 0]), (0.6, [0, 0, 0])])},
            "leftJaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, -38, 0]), (0.38, [0, -38, 0]), (0.42, [0, 6, 0]), (0.6, [0, 0, 0])])},
            "rightLegF": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, 0, 30]), (0.4, [0, 0, -10]), (0.6, [0, 0, 0])])},
            "leftLegF": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, 0, -30]), (0.4, [0, 0, 10]), (0.6, [0, 0, 0])])},
        }},
        # 덮치기 준비: 몸을 낮추고 뒷다리를 접음 (끝 자세 유지 — "leap" 컨트롤러)
        "leap_prep": {"animation_length": 0.5, "loop": "hold_on_last_frame", "bones": {
            "body": {"position": kf([(0, [0, 0, 0]), (0.4, [0, -2.5, 1]), (0.5, [0, -2.2, 1])]), "rotation": kf([(0, [0, 0, 0]), (0.4, [-8, 0, 0])])},
            "rightLegH": {"rotation": kf([(0, [0, 0, 0]), (0.4, [32, 0, 0])])},
            "leftLegH": {"rotation": kf([(0, [0, 0, 0]), (0.4, [32, 0, 0])])},
            "sac": {"scale": kf([(0, [1, 1, 1]), (0.25, [1.15, 1.2, 1.15]), (0.5, [1.1, 1.15, 1.1])])},
            "rightJaw": {"rotation": kf([(0, [0, 0, 0]), (0.4, [0, 30, 0])])},
            "leftJaw": {"rotation": kf([(0, [0, 0, 0]), (0.4, [0, -30, 0])])},
        }},
        # 공중: 다리를 뒤로 뻗고 턱을 벌림 (끝 자세 유지)
        "leap": {"animation_length": 0.3, "loop": "hold_on_last_frame", "bones": {
            "body": {"rotation": kf([(0, [-12, 0, 0]), (0.3, [6, 0, 0])])},
            "rightLegH": {"rotation": kf([(0, [-40, 0, 0]), (0.3, [-55, 0, 0])])},
            "leftLegH": {"rotation": kf([(0, [-40, 0, 0]), (0.3, [-55, 0, 0])])},
            "rightLegF": {"rotation": kf([(0, [0, -20, 20]), (0.3, [0, -30, 25])])},
            "leftLegF": {"rotation": kf([(0, [0, 20, -20]), (0.3, [0, 30, -25])])},
            "rightLegM": {"rotation": kf([(0, [0, 10, 15]), (0.3, [0, 15, 20])])},
            "leftLegM": {"rotation": kf([(0, [0, -10, -15]), (0.3, [0, -15, -20])])},
            "rightJaw": {"rotation": kf([(0, [0, 40, 0]), (0.3, [0, 45, 0])])},
            "leftJaw": {"rotation": kf([(0, [0, -40, 0]), (0.3, [0, -45, 0])])},
        }},
        # 무리 부르기: 몸을 세우고 턱을 크게 벌림 · 주머니가 부풂
        "screech": {"animation_length": 0.9, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-28, 0, 0]), (0.7, [-28, 0, 0]), (0.9, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-14, 0, 0]), (0.7, [-14, 0, 0]), (0.9, [0, 0, 0])])},
            "rightJaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 45, 0]), (0.7, [0, 45, 0]), (0.9, [0, 0, 0])])},
            "leftJaw": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, -45, 0]), (0.7, [0, -45, 0]), (0.9, [0, 0, 0])])},
            "sac": {"scale": kf([(0, [1, 1, 1]), (0.3, [1.25, 1.3, 1.25]), (0.6, [1.15, 1.2, 1.15]), (0.9, [1, 1, 1])])},
        }},
    }
    return m, anims


# ================================================================= 공허 추적자 (Lv48)

@void_common
def void_stalker():
    m = Model("void_stalker")
    sh = shell(401)
    sh2 = shell(402, SHELL2, seam_band=3)
    pl = plate(403, rim=None, seam=VIOLET, seam_band=3, seam_glow=0.55, seam_gap=0.35)
    rib = Mat("#3b2c62", "chitin", noise=5, band=2, accent="#1c1432", seed=404, rim="#c050b0", rim_glow=0.15)
    cor = core(405)
    bl = blade(406)
    bn = bone(407)
    fin = Mat("#241a3e", "chitin", noise=5, band=3, accent="#120c20", seed=408, rim=SEAM, rim_glow=0.55)
    dark = flat(409, "#120c22")

    hips = m.bone("hips", [0, 16, 0])
    hips.box([-3, 14, -2], [6, 3, 4], sh)
    # 다리 (뒤로 꺾인 무릎): 넓적다리 (앞으로) → 정강이 (뒤로) → 발 (발톱)
    for sx in (-1, 1):
        side = "right" if sx < 0 else "left"
        lx = -4 if sx < 0 else 2
        th = m.bone(f"{side}Thigh", [lx + 1, 15, 0], "hips", [-28, 0, 0])
        th.box([lx, 8, -1.5], [2, 7, 3], sh2.with_(seed=410 + sx))
        sn = m.bone(f"{side}Shin", [lx + 1, 8, 0], f"{side}Thigh", [58, 0, 0])
        sn.box([lx + 0.5, 1, -1], [1, 7, 2], sh.with_(seed=412 + sx), decals=[("north", 0, 2, SEAM, 0.8), ("north", 0, 4, SEAM, 0.8)])
        ft = m.bone(f"{side}Foot", [lx + 1, 1, 0], f"{side}Shin", [-30, 0, 0])
        ft.box([lx, -1, -3], [2, 2, 4], dark)
        ft.box([lx, -1, -4], [1, 1, 1], bn.with_(seed=414 + sx))
        ft.box([lx + 1, -1, -4], [1, 1, 1], bn.with_(seed=416 + sx))
    # 몸통 (앞으로 숙임): 등뼈 · 갈비 판 셋 사이로 빛나는 핵 · 가슴 판
    torso = m.bone("torso", [0, 17, 0], "hips", [16, 0, 0])
    torso.box([-1, 17, 1], [2, 10, 2], sh2.with_(seed=420))
    torso.box([-2, 19, -2], [4, 5, 4], cor)
    for i, y in enumerate((18.5, 21, 23.5)):
        torso.box([-3.5, y, -2.5], [7, 1, 4], rib.with_(seed=421 + i))
    torso.box([-3, 25, -2.5], [6, 3, 4], sh.with_(seed=425))
    torso.box([-5, 27, -2], [10, 2, 4], pl)
    # 등 지느러미 판 셋 (뒤로 늘어짐 — 망토 같은 실루엣)
    for i, (x, rz) in enumerate(((-3, -6), (-0.5, 0), (2, 6))):
        f = m.bone(f"fin{i}", [x + 0.5, 28, 1.5], "torso", [22, 0, rz])
        f.box([x, 17, 1], [1, 11, 3], fin.with_(seed=430 + i))
        f.box([x, 15, 2], [1, 2, 1], fin.with_(seed=433 + i), decals=[("west", 0, 1, SEAM, 1.0), ("east", 0, 1, SEAM, 1.0)])
    # 머리: 앞으로 길쭉한 가면 — 세 줄 눈 틈 (빛남) · 갈라진 아래턱
    head = m.bone("head", [0, 29, -1], "torso", [-14, 0, 0])
    head.box([-1, 28, -2], [2, 2, 2], dark)
    slits = [("north", 0, 0, EYE2, 0.95), ("north", 0, 1, EYE, 1.0), ("north", 0, 2, EYE2, 0.9),
             ("north", 2, 1, EYE, 1.0), ("north", 2, 2, EYE, 1.0), ("north", 2, 3, EYE2, 0.9),
             ("north", 4, 0, EYE2, 0.95), ("north", 4, 1, EYE, 1.0), ("north", 4, 2, EYE2, 0.9)]
    head.box([-2.5, 28, -9], [5, 4, 7], sh2.with_(seed=440, seam_band=2), decals=slits)
    head.box([-2, 32, -7], [4, 1, 5], pl.with_(seed=441))
    for sx in (-1, 1):
        hs = m.bone("rightHorn" if sx < 0 else "leftHorn", [2 * sx, 31, -3], "head", [20, 0, -18 * sx])
        hs.box([-3 if sx < 0 else 2, 30, -3], [1, 1, 5], bn.with_(seed=442 + sx), decals=[("south", 0, 0, SEAM, 0.9)])
        jw = m.bone("rightMandible" if sx < 0 else "leftMandible", [1.5 * sx, 28, -3], "head")
        jw.box([-2.5 if sx < 0 else 0.5, 27, -9], [2, 1, 6], bn.with_(seed=444 + sx), decals=[("north", 0, 0, SEAM, 1.0)])
    # 팔: 위팔 → 아래팔 (앞으로 굽음) → 긴 칼날 (빛나는 날)
    for sx in (-1, 1):
        side = "right" if sx < 0 else "left"
        ax = -7 if sx < 0 else 5
        up = m.bone(f"{side}Arm", [6 * sx, 27, 0], "torso", [8, 0, 10 * sx])
        up.box([ax, 20, -1.5], [2, 8, 3], sh.with_(seed=450 + sx))
        up.box([ax - 0.5, 26, -2], [3, 2, 4], pl.with_(seed=452 + sx))
        fa = m.bone(f"{side}Forearm", [ax + 1, 20, 0], f"{side}Arm", [-72, 0, 0])
        fa.box([ax + 0.5, 14, -1], [1, 6, 2], sh2.with_(seed=454 + sx))
        bd = m.bone(f"{side}Blade", [ax + 1, 14, 0], f"{side}Forearm", [-28, 0, 0])
        bd.box([ax + 0.5, 2, -2], [1, 13, 3], bl.with_(seed=456 + sx))
        bd.box([ax + 0.5, 0, -1], [1, 2, 2], bl.with_(seed=458 + sx))

    walk = f"math.cos({DM} * 34) * 30 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "torso": {"rotation": [f"math.sin({LT} * 70) * 2.5", 0, f"math.sin({LT} * 45) * 1.5"]},
            "head": {"rotation": [f"{LOOK_X} * 0.5", f"{LOOK_Y} * 0.6", f"math.sin({LT} * 30) * 4"]},
            "rightArm": {"rotation": [f"math.sin({LT} * 70) * 4", 0, 0]},
            "leftArm": {"rotation": [f"math.sin({LT} * 70 + 60) * 4", 0, 0]},
            "rightBlade": {"rotation": [f"math.sin({LT} * 110) * 5", 0, 0]},
            "leftBlade": {"rotation": [f"math.sin({LT} * 110 + 90) * 5", 0, 0]},
            "fin0": {"rotation": [f"math.sin({LT} * 90) * 6", 0, 0]},
            "fin1": {"rotation": [f"math.sin({LT} * 90 + 50) * 6", 0, 0]},
            "fin2": {"rotation": [f"math.sin({LT} * 90 + 100) * 6", 0, 0]},
            "rightMandible": {"rotation": [0, f"math.max(0, math.sin({LT} * 160)) * 10", 0]},
            "leftMandible": {"rotation": [0, f"-math.max(0, math.sin({LT} * 160)) * 10", 0]},
        }},
        "walk": {"loop": True, "bones": {
            "rightThigh": {"rotation": [walk, 0, 0]},
            "leftThigh": {"rotation": [f"-{walk}", 0, 0]},
            "rightShin": {"rotation": [f"math.max(0, -math.cos({DM} * 34)) * 25 * {MS}", 0, 0]},
            "leftShin": {"rotation": [f"math.max(0, math.cos({DM} * 34)) * 25 * {MS}", 0, 0]},
            "rightArm": {"rotation": [f"-{walk} * 0.4", 0, 0]},
            "leftArm": {"rotation": [f"{walk} * 0.4", 0, 0]},
            "hips": {"position": [0, f"math.abs(math.sin({DM} * 34)) * 1 * {walk_amt()}", 0]},
            "torso": {"rotation": [f"6 * {walk_amt()}", f"math.cos({DM} * 34) * 5 * {MS}", 0]},
        }},
        # 두 번 베기: 오른 칼날 (0.3) → 왼 칼날 (0.5 = windup 10틱)
        "attack": {"animation_length": 0.8, "bones": {
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.18, [-95, 25, 0]), (0.3, [10, -30, 0]), (0.55, [0, 0, 0])])},
            "rightForearm": {"rotation": kf([(0, [0, 0, 0]), (0.18, [30, 0, 0]), (0.3, [-10, 0, 0]), (0.55, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-100, -25, 0]), (0.38, [-95, -25, 0]), (0.5, [12, 30, 0]), (0.8, [0, 0, 0])])},
            "leftForearm": {"rotation": kf([(0, [0, 0, 0]), (0.38, [30, 0, 0]), (0.5, [-10, 0, 0]), (0.8, [0, 0, 0])])},
            "torso": {"rotation": kf([(0, [0, 0, 0]), (0.18, [-6, -14, 0]), (0.3, [8, 14, 0]), (0.5, [8, -14, 0]), (0.8, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [6, 0, 0]), (0.8, [0, 0, 0])])},
        }},
        # 균열 속으로: 웅크려 칼날을 가슴에 X 로 모음 (끝 자세 유지 — 그 뒤 서버가 몸을 숨김)
        "vanish": {"animation_length": 0.5, "loop": "hold_on_last_frame", "bones": {
            "hips": {"position": kf([(0, [0, 0, 0]), (0.4, [0, -4, 0])])},
            "torso": {"rotation": kf([(0, [0, 0, 0]), (0.4, [24, 0, 0])])},
            "rightArm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-40, 0, 40])])},
            "leftArm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-40, 0, -40])])},
            "rightThigh": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-30, 0, 0])])},
            "leftThigh": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-30, 0, 0])])},
            "rightShin": {"rotation": kf([(0, [0, 0, 0]), (0.4, [40, 0, 0])])},
            "leftShin": {"rotation": kf([(0, [0, 0, 0]), (0.4, [40, 0, 0])])},
        }},
        # 균열에서 나와 십자 베기: 칼날을 머리 위로 X (0.15) → 바깥으로 내리 벰 (0.3 = 6틱) → 섬
        "appear": {"animation_length": 0.75, "bones": {
            "hips": {"position": kf([(0, [0, -3, 0]), (0.15, [0, 1, 0]), (0.3, [0, -1.5, 0]), (0.75, [0, 0, 0])])},
            "torso": {"rotation": kf([(0, [10, 0, 0]), (0.15, [-14, 0, 0]), (0.3, [20, 0, 0]), (0.75, [0, 0, 0])])},
            "rightArm": {"rotation": kf([(0, [-60, 0, 30]), (0.15, [-160, 0, -20]), (0.3, [-20, 0, 40]), (0.75, [0, 0, 0])])},
            "leftArm": {"rotation": kf([(0, [-60, 0, -30]), (0.15, [-160, 0, 20]), (0.3, [-20, 0, -40]), (0.75, [0, 0, 0])])},
            "rightForearm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [40, 0, 0]), (0.3, [-10, 0, 0]), (0.75, [0, 0, 0])])},
            "leftForearm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [40, 0, 0]), (0.3, [-10, 0, 0]), (0.75, [0, 0, 0])])},
            "fin0": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-20, 0, -10]), (0.4, [0, 0, 0])])},
            "fin2": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-20, 0, 10]), (0.4, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 공허 해파리 (Lv52)

TENDRILS = [(6, 0), (4, 4), (0, 6), (-4, 4), (-6, 0), (-4, -4), (0, -6), (4, -4)]


@void_common
def void_jelly():
    m = Model("void_jelly")
    bell = Mat("#2e2152", "skin", noise=6, accent="#1c1236", seed=501, seam=VIOLET, seam_band=3, seam_dir="v", seam_glow=0.6, seam_gap=0.25,
               spots=2, spot="#ff8ef0", spot_glow=0.95)
    cap = Mat("#3b2a66", "skin", noise=6, accent="#22163e", seed=502, spots=3, spot="#ffb8f6", spot_glow=1.0)
    rim = Mat("#5a2f86", "flat", noise=5, seed=503, rim=SEAM, rim_glow=1.0, glow=0.3)
    cor = core(504, veins=3, vein="#ffffff", vein_glow=1.0)
    tent = Mat("#2a1d48", "chitin", noise=5, band=2, accent="#140c26", seed=505, seam=SEAM, seam_band=3, seam_glow=0.8, seam_gap=0.2)
    tip = Mat("#ff7ae8", "flat", noise=6, glow=1.0, edge=1.0, seed=506)
    arm = glowsac(507, base="#8a2a9a", veins=3, vein="#ffc8f8")
    spike = Mat("#4a3676", "flat", noise=4, seed=508, rim=SEAM, rim_glow=0.8)

    body = m.bone("body", [0, 22, 0])
    b = m.bone("bell", [0, 21, 0], "body")
    b.box([-7, 20, -7], [14, 5, 14], bell)
    b.box([-6, 25, -6], [12, 3, 12], bell.with_(seed=509))
    b.box([-5, 28, -5], [10, 2, 10], cap)
    b.box([-3, 30, -3], [6, 1, 6], cap.with_(seed=510))
    for x, z in ((-3, -3), (2, -3), (-3, 2), (2, 2)):
        b.box([x, 30, z], [1, 3, 1], spike.with_(seed=511 + x + z), decals=[("up", 0, 0, SEAM, 1.0)])
    r = m.bone("rim", [0, 20, 0], "bell")
    r.box([-8, 19, -8], [16, 1, 16], rim)
    for x, z in ((-9, -2), (8, -2), (-2, -9), (-2, 8)):
        w, d = (1, 4) if z == -2 else (4, 1)
        r.box([x, 19, z], [w, 1, d], rim.with_(seed=512 + x))
    c = m.bone("core", [0, 18, 0], "body")
    c.box([-3.5, 13, -3.5], [7, 7, 7], cor)
    c.box([-2.5, 12, -2.5], [5, 1, 5], cor.with_(seed=513))
    c.box([-1.5, 11, -1.5], [3, 1, 3], cor.with_(seed=514))
    # 촉수 여덟 (두 마디 · 빛나는 끝) + 굵은 팔 둘
    for i, (x, z) in enumerate(TENDRILS):
        up = m.bone(f"t{i}", [x, 19, z], "body")
        up.box([x - 0.5, 11, z - 0.5], [1, 8, 1], tent.with_(seed=520 + i))
        lo = m.bone(f"t{i}b", [x, 11, z], f"t{i}")
        lo.box([x - 0.5, 4, z - 0.5], [1, 7, 1], tent.with_(seed=530 + i))
        lo.box([x - 0.5, 3, z - 0.5], [1, 1, 1], tip.with_(seed=540 + i))
    for sx in (-1, 1):
        a = m.bone("rightArm" if sx < 0 else "leftArm", [2 * sx, 14, 0], "body")
        a.box([-3 if sx < 0 else 1, 5, -1], [2, 9, 2], arm.with_(seed=550 + sx))
        a.box([-3 if sx < 0 else 1, 4, -1], [2, 1, 2], tip.with_(seed=552 + sx))

    def sway(i, amp=8):
        ph = i * 45
        return {"rotation": [f"math.sin({LT} * 90 + {ph}) * {amp}", 0, f"math.cos({LT} * 75 + {ph}) * {amp}"]}

    idle = {"body": {"position": [0, f"math.sin({LT} * 70) * 1.5", 0]},
            "bell": {"scale": [f"1 + math.sin({LT} * 140) * 0.05", f"1 - math.sin({LT} * 140) * 0.06", f"1 + math.sin({LT} * 140) * 0.05"]},
            "rim": {"scale": [f"1 + math.sin({LT} * 140 - 40) * 0.08", 1, f"1 + math.sin({LT} * 140 - 40) * 0.08"]},
            "core": {"scale": [f"1 + math.sin({LT} * 200) * 0.07", f"1 + math.sin({LT} * 200) * 0.07", f"1 + math.sin({LT} * 200) * 0.07"]},
            "rightArm": {"rotation": [f"math.sin({LT} * 80) * 10", 0, f"math.sin({LT} * 60) * 6"]},
            "leftArm": {"rotation": [f"math.sin({LT} * 80 + 90) * 10", 0, f"-math.sin({LT} * 60) * 6"]}}
    for i in range(8):
        idle[f"t{i}"] = sway(i)
        idle[f"t{i}b"] = sway(i + 2, 12)
    walk = {}
    for i, (x, z) in enumerate(TENDRILS):
        walk[f"t{i}"] = {"rotation": [f"16 * {walk_amt()}", 0, 0]}
        walk[f"t{i}b"] = {"rotation": [f"14 * {walk_amt()}", 0, 0]}
    walk["body"] = {"rotation": [f"-6 * {walk_amt()}", 0, 0]}
    # 촉수 휘두르기: 앞 촉수 셋 (z < 0) 을 뒤로 젖혔다가 (0.25) 앞으로 내리침 (0.45 = windup 9틱)
    lash = {"body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.45, [14, 0, 0]), (0.7, [0, 0, 0])])}}
    for i, (x, z) in enumerate(TENDRILS):
        if z < 0 or (z == 0 and False):
            lash[f"t{i}"] = {"rotation": kf([(0, [0, 0, 0]), (0.25, [40, 0, 0]), (0.45, [-70, 0, 0]), (0.7, [0, 0, 0])])}
            lash[f"t{i}b"] = {"rotation": kf([(0, [0, 0, 0]), (0.25, [30, 0, 0]), (0.45, [-40, 0, 0]), (0.7, [0, 0, 0])])}
    lash["rightArm"] = {"rotation": kf([(0, [0, 0, 0]), (0.25, [35, 0, 0]), (0.45, [-60, 0, 0]), (0.7, [0, 0, 0])])}
    lash["leftArm"] = {"rotation": kf([(0, [0, 0, 0]), (0.25, [35, 0, 0]), (0.45, [-60, 0, 0]), (0.7, [0, 0, 0])])}
    # 구체 모으기: 종이 오므라들고 촉수가 바깥으로 펼쳐짐 · 핵이 커짐 (0.7 = 14틱 — 그때 쏨)
    cast = {"bell": {"scale": kf([(0, [1, 1, 1]), (0.5, [0.88, 1.12, 0.88]), (0.7, [1.12, 0.86, 1.12]), (0.95, [1, 1, 1])])},
            "core": {"scale": kf([(0, [1, 1, 1]), (0.6, [1.35, 1.35, 1.35]), (0.72, [0.85, 0.85, 0.85]), (0.95, [1, 1, 1])])},
            "body": {"position": kf([(0, [0, 0, 0]), (0.6, [0, 2, 0]), (0.75, [0, -1, 0]), (0.95, [0, 0, 0])])}}
    for i, (x, z) in enumerate(TENDRILS):
        out_x = -z / 6 * 35
        out_z = x / 6 * 35
        cast[f"t{i}"] = {"rotation": kf([(0, [0, 0, 0]), (0.6, [out_x, 0, out_z]), (0.75, [out_x * 0.3, 0, out_z * 0.3]), (0.95, [0, 0, 0])])}
    anims = {
        "idle": {"loop": True, "bones": idle},
        "walk": {"loop": True, "bones": walk},
        "attack": {"animation_length": 0.7, "bones": lash},
        "cast": {"animation_length": 0.95, "bones": cast},
    }
    return m, anims


# ================================================================= 공허 굴착자 (Lv55)

BORE = -30  # 땅속 자세: 몸 전체를 땅 밑으로 (등 수정 끝만 조금 — 실제로는 서버가 이름표를 치우고 금 간 자국을 그림)


@void_common
def void_borer():
    m = Model("void_borer")
    sh = shell(601, seam_band=3, seam_glow=0.8, seam_gap=0.2)
    sh2 = shell(602, SHELL2, seam_band=3, seam_glow=0.8, seam_gap=0.2)
    pl = plate(603, rim=None, seam=VIOLET, seam_band=2, seam_dir="v", seam_glow=0.5, seam_gap=0.5)
    belly = flat(604, BELLY)
    jaw = bone(605, rim=SEAM, rim_glow=0.5)
    maw = core(606, base="#ff52da", spots=6)
    leg = Mat("#2c2150", "chitin", noise=5, band=2, accent="#170f2c", seed=607)
    cry = crystal(608)
    tip = crystal(616, base="#dcb2ff", glow=0.95)

    base = m.bone("base", [0, 0, 0])
    s1 = m.bone("seg1", [0, 6, -4], "base")
    s1.box([-5, 1, -8], [10, 9, 8], sh, faces={"down": belly})
    s1.box([-4.5, 10, -7], [9, 1, 6], pl)
    shard(m, "crystal0", "seg1", [0, 11, -5], [2, 6, 2], cry, tip, [-14, 0, 0], 609)
    shard(m, "crystal1", "seg1", [2.5, 10, -3.5], [1, 3, 1], cry, tip, [-8, 0, -24], 612)
    shard(m, "crystal2", "seg1", [-2.5, 10, -3.5], [1, 3, 1], cry, tip, [-8, 0, 24], 615)
    head = m.bone("head", [0, 6, -8], "seg1", [6, 0, 0])
    eyes = [("east", 1, 1, EYE, 1.0), ("east", 2, 1, EYE2, 0.9), ("west", 2, 1, EYE, 1.0), ("west", 1, 1, EYE2, 0.9)]
    head.box([-4, 2, -13], [8, 7, 5], sh2, decals=eyes)
    head.box([-2, 4, -14], [4, 3, 1], maw)
    # 세 갈래 드릴 턱 (위 · 오른 · 왼 — 닫으면 뾰족한 끝)
    jt = m.bone("jawTop", [0, 8, -13], "head")
    jt.box([-2.5, 7, -18], [5, 2, 5], jaw)
    jt.box([-1.5, 6, -20], [3, 2, 2], jaw.with_(seed=611), decals=[("north", 1, 1, SEAM, 1.0)])
    for sx in (-1, 1):
        j = m.bone("jawRight" if sx < 0 else "jawLeft", [3 * sx, 4, -13], "head")
        j.box([-4 if sx < 0 else 1, 2, -18], [3, 5, 5], jaw.with_(seed=612 + sx))
        j.box([-2.5 if sx < 0 else 0.5, 3, -20], [2, 3, 2], jaw.with_(seed=614 + sx), decals=[("north", 0, 1, SEAM, 1.0), ("north", 1, 1, SEAM, 1.0)])
    s2 = m.bone("seg2", [0, 6, 0], "seg1")
    s2.box([-4.5, 1, 0], [9, 8, 7], sh.with_(seed=620), faces={"down": belly})
    s2.box([-4, 9, 1], [8, 1, 5], pl.with_(seed=621))
    shard(m, "crystal3", "seg2", [0, 10, 3], [2, 7, 2], cry, tip, [-18, 0, 6], 622)
    shard(m, "crystal4", "seg2", [2.5, 9, 4.5], [1, 3, 1], cry, tip, [-10, 0, -22], 625)
    s3 = m.bone("seg3", [0, 5, 7], "seg2")
    s3.box([-4, 1, 7], [8, 7, 6], sh2.with_(seed=630), faces={"down": belly})
    s3.box([-3.5, 8, 8], [7, 1, 4], pl.with_(seed=631))
    shard(m, "crystal5", "seg3", [0, 9, 10], [2, 5, 2], cry, tip, [-20, 0, -8], 632)
    shard(m, "crystal6", "seg3", [-2.5, 8, 11], [1, 3, 1], cry, tip, [-12, 0, 22], 635)
    s4 = m.bone("seg4", [0, 4, 13], "seg3")
    s4.box([-3, 1, 13], [6, 6, 5], sh.with_(seed=640), faces={"down": belly})
    shard(m, "crystal7", "seg4", [0, 7, 15], [1, 4, 1], cry, tip, [-24, 0, 0], 641)
    tail = m.bone("tail", [0, 4, 18], "seg4")
    tail.box([-2, 2, 18], [4, 4, 4], sh2.with_(seed=650))
    tail.box([-0.5, 3, 22], [1, 2, 3], cry.with_(seed=651))
    # 다리: 마디마다 한 쌍 (짧은 갈고리)
    for si, (seg, z) in enumerate((("seg1", -4), ("seg2", 3), ("seg3", 10))):
        for sx in (-1, 1):
            side = "R" if sx < 0 else "L"
            lg = m.bone(f"leg{side}{si}", [(4.5 - si * 0.5) * sx, 4, z], seg)
            x0 = -(4.5 - si * 0.5) if sx < 0 else 4.5 - si * 0.5
            lg.box([x0 - 3 if sx < 0 else x0, 3, z - 0.5], [3, 1, 1], leg.with_(seed=660 + si * 2 + sx))
            lg.box([x0 - 3 if sx < 0 else x0 + 2, 0, z - 0.5], [1, 3, 1], leg.with_(seed=670 + si * 2 + sx))

    wave = lambda i: f"math.sin({DM} * 45 - {i * 50}) * 9 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "seg2": {"rotation": [0, f"math.sin({LT} * 50) * 5", 0]},
            "seg3": {"rotation": [0, f"math.sin({LT} * 50 - 50) * 6", 0]},
            "seg4": {"rotation": [0, f"math.sin({LT} * 50 - 100) * 7", 0]},
            "tail": {"rotation": [f"math.sin({LT} * 70) * 6", f"math.sin({LT} * 50 - 150) * 9", 0]},
            "head": {"rotation": [f"{LOOK_X} * 0.4", f"{LOOK_Y} * 0.5", 0]},
            "jawTop": {"rotation": [f"-math.max(0, math.sin({LT} * 170)) * 14", 0, 0]},
            "jawRight": {"rotation": [0, f"math.max(0, math.sin({LT} * 170)) * 14", 0]},
            "jawLeft": {"rotation": [0, f"-math.max(0, math.sin({LT} * 170)) * 14", 0]},
        }},
        "walk": {"loop": True, "bones": {
            "seg1": {"rotation": [0, wave(0), 0]},
            "seg2": {"rotation": [0, wave(1), 0]},
            "seg3": {"rotation": [0, wave(2), 0]},
            "seg4": {"rotation": [0, wave(3), 0]},
            "tail": {"rotation": [0, wave(4), 0]},
            **{f"leg{s}{i}": {"rotation": [0, f"math.sin({DM} * 90 - {i * 60 + (0 if s == 'R' else 180)}) * 22 * {MS}", 0]} for s in "RL" for i in range(3)},
        }},
        # 물어뜯기: 앞 마디를 세웠다가 (턱 활짝 0.35) 내리찍음 (0.55 = windup 11틱)
        "attack": {"animation_length": 0.8, "bones": {
            "seg1": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-26, 0, 0]), (0.55, [12, 0, 0]), (0.8, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-12, 0, 0]), (0.55, [18, 0, 0]), (0.8, [0, 0, 0])])},
            "jawTop": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-45, 0, 0]), (0.5, [-45, 0, 0]), (0.56, [0, 0, 0])])},
            "jawRight": {"rotation": kf([(0, [0, 0, 0]), (0.35, [0, 45, 0]), (0.5, [0, 45, 0]), (0.56, [0, 0, 0])])},
            "jawLeft": {"rotation": kf([(0, [0, 0, 0]), (0.35, [0, -45, 0]), (0.5, [0, -45, 0]), (0.56, [0, 0, 0])])},
        }},
        # 파고들기: 머리부터 땅으로 (끝 자세 유지 — "tunnel" 컨트롤러)
        "burrow": {"animation_length": 0.6, "loop": "hold_on_last_frame", "bones": {
            "base": {"position": kf([(0, [0, 0, 0]), (0.25, [0, -6, -2]), (0.6, [0, BORE, -2])]),
                     "rotation": kf([(0, [0, 0, 0]), (0.25, [35, 0, 0]), (0.6, [10, 0, 0])])},
            "jawTop": {"rotation": kf([(0, [0, 0, 0]), (0.2, [-35, 0, 0]), (0.45, [0, 0, 0])])},
            "jawRight": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 35, 0]), (0.45, [0, 0, 0])])},
            "jawLeft": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, -35, 0]), (0.45, [0, 0, 0])])},
        }},
        # 솟구치기: 땅 밑에서 머리를 들고 튀어 올라 (0.3 — 넘침) 섬, 턱을 활짝
        "emerge": {"animation_length": 0.9, "bones": {
            "base": {"position": kf([(0, [0, BORE, 0]), (0.3, [0, 7, 0]), (0.5, [0, -1, 0]), (0.9, [0, 0, 0])]),
                     "rotation": kf([(0, [-40, 0, 0]), (0.3, [-38, 0, 0]), (0.6, [4, 0, 0]), (0.9, [0, 0, 0])])},
            "jawTop": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-50, 0, 0]), (0.6, [-50, 0, 0]), (0.9, [0, 0, 0])])},
            "jawRight": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 50, 0]), (0.6, [0, 50, 0]), (0.9, [0, 0, 0])])},
            "jawLeft": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, -50, 0]), (0.6, [0, -50, 0]), (0.9, [0, 0, 0])])},
        }},
    }
    return m, anims


# ================================================================= 공허 거수 (Lv58)

@void_common
def void_behemoth():
    m = Model("void_behemoth")
    sh = shell(701, seam_band=4, seam_glow=0.75, seam_gap=0.25)
    sh2 = shell(702, SHELL2, seam_band=3, seam_glow=0.7, seam_gap=0.3)
    pl = plate(703, rim=None, seam=VIOLET, seam_band=3, seam_glow=0.55, seam_gap=0.35)
    belly = flat(704, BELLY)
    tusk = bone(705, rim=SEAM, rim_glow=0.4)
    maw = core(706, base="#ff4fd8", spots=6)
    cry = crystal(707)
    cry2 = crystal(708, base="#b36cff")
    tip = crystal(711, base="#e4bcff", glow=0.95)
    claw = bone(709, base=BONE2)
    dark = flat(710, "#140e24")

    body = m.bone("body", [0, 14, 2])
    body.box([-10, 8, -9], [20, 12, 22], sh, faces={"down": belly})
    body.box([-9, 20, -7], [18, 2, 18], pl)
    body.box([-8, 10, 13], [16, 8, 3], sh2.with_(seed=711))
    # 등의 공허 수정 숲 (뼈마다 조금씩 기울임)
    # (밑동 가운데, 크기, 기울기) — 가운데 큰 것 하나 · 둘레로 벌어지게
    crys = [((0, 22, 0), (4, 13, 4), (0, 0, 0)), ((5, 22, 4), (3, 9, 3), (10, 0, -20)), ((-5, 22, 3), (3, 10, 3), (-6, 0, 22)),
            ((5, 22, -4), (2, 7, 2), (-14, 0, -22)), ((-5.5, 22, -4), (2, 7, 2), (-12, 0, 24)), ((0, 22, 8), (3, 8, 3), (22, 0, 4)),
            ((-4.5, 22, 9), (2, 6, 2), (20, 0, 18)), ((4.5, 22, 9.5), (2, 6, 2), (20, 0, -18)), ((0, 22, -6), (2, 5, 2), (-24, 0, 0))]
    for i, (c, sz, rot) in enumerate(crys):
        shard(m, f"crystal{i}", "body", list(c), sz, cry if i % 2 == 0 else cry2, tip, rot, 720 + i * 3)
    # 머리: 이마 판 · 눈 무리 (빛남) · 아래턱 (속이 빛남) · 앞으로 뻗은 엄니 둘
    head = m.bone("head", [0, 14, -9], "body")
    eyes = [("north", 3, 2, EYE, 1.0), ("north", 5, 1, EYE2, 0.9), ("north", 8, 1, EYE2, 0.9), ("north", 10, 2, EYE, 1.0),
            ("north", 4, 3, EYE2, 0.8), ("north", 9, 3, EYE2, 0.8), ("north", 6, 2, "#0b0716"), ("north", 7, 2, "#0b0716")]
    head.box([-7, 8, -18], [14, 9, 9], sh2.with_(seed=750), decals=eyes)
    head.box([-7.5, 15, -18.5], [15, 2, 5], pl.with_(seed=751))
    head.box([-5, 17, -16], [10, 1, 5], pl.with_(seed=752))
    head.box([-5, 8, -18.5], [10, 1, 1], maw)
    jaw = m.bone("jaw", [0, 9, -11], "head")
    jaw.box([-6, 5, -19], [12, 3, 9], sh.with_(seed=753), faces={"up": maw})
    jaw.box([-5, 8, -19], [1, 1, 1], tusk.with_(seed=754))
    jaw.box([4, 8, -19], [1, 1, 1], tusk.with_(seed=755))
    for sx in (-1, 1):
        t = m.bone("rightTusk" if sx < 0 else "leftTusk", [5 * sx, 10, -17], "head", [-14, 12 * sx, 0])
        tx = -7 if sx < 0 else 5
        t.box([tx, 9, -25], [2, 2, 8], tusk.with_(seed=756 + sx))
        t.box([tx, 11, -26], [2, 2, 2], tusk.with_(seed=758 + sx), decals=[("north", 0, 0, SEAM, 1.0), ("north", 1, 0, SEAM, 1.0)])
    # 다리 넷 (굵은 기둥 · 발톱)
    for name, x, z in (("frontRightLeg", -8, -5), ("frontLeftLeg", 8, -5), ("backRightLeg", -8, 9), ("backLeftLeg", 8, 9)):
        lg = m.bone(name, [x, 12, z], "body")
        lx = x - 3 if x < 0 else x - 2
        lg.box([lx, 3, z - 2.5], [5, 10, 5], sh2.with_(seed=760 + x + z))
        lg.box([lx - 0.5, 0, z - 3], [6, 3, 6], dark.with_(seed=770 + x + z))
        for cx in (0, 2, 4):
            lg.box([lx + cx, 0, z - 4], [1, 1, 1], claw.with_(seed=780 + cx + x), decals=[("north", 0, 0, SEAM, 0.8)])

    gait = f"math.cos({DM} * 24) * 22 * {MS}"
    anims = {
        "idle": {"loop": True, "bones": {
            "body": {"position": [0, f"math.sin({LT} * 50) * 0.5", 0], "rotation": [f"math.sin({LT} * 50) * 1", 0, 0]},
            "head": {"rotation": [f"{LOOK_X} * 0.35 + math.sin({LT} * 50 + 40) * 2", f"{LOOK_Y} * 0.4", 0]},
            "jaw": {"rotation": [f"math.max(0, math.sin({LT} * 60)) * 6", 0, 0]},
        }},
        "walk": {"loop": True, "bones": {
            "frontRightLeg": {"rotation": [gait, 0, 0]},
            "backLeftLeg": {"rotation": [gait, 0, 0]},
            "frontLeftLeg": {"rotation": [f"-{gait}", 0, 0]},
            "backRightLeg": {"rotation": [f"-{gait}", 0, 0]},
            "body": {"rotation": [0, 0, f"math.cos({DM} * 24) * 2.5 * {walk_amt()}"],
                     "position": [0, f"math.abs(math.sin({DM} * 24)) * 0.8 * {walk_amt()}", 0]},
            "head": {"rotation": [0, f"math.cos({DM} * 24) * 4 * {MS}", 0]},
        }},
        # 앞발 내려찍기: 앞을 들었다가 (0.5) 쾅 (0.8 = windup 16틱)
        "attack": {"animation_length": 1.15, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-18, 0, 0]), (0.8, [7, 0, 0]), (1.15, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.5, [0, 2, 1]), (0.8, [0, -1, 0]), (1.15, [0, 0, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-42, 0, 0]), (0.8, [12, 0, 0]), (1.15, [0, 0, 0])])},
            "frontLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-42, 0, 0]), (0.8, [12, 0, 0]), (1.15, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.8, [16, 0, 0]), (1.15, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.5, [24, 0, 0]), (0.78, [24, 0, 0]), (0.85, [0, 0, 0])])},
        }},
        # 수정 가시 부르기: 크게 일어섰다가 (0.6) 머리를 박으며 내려찍고 (0.9 = 18틱) 아가리를 벌린 채 버팀
        "slam": {"animation_length": 1.5, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-28, 0, 0]), (0.9, [10, 0, 0]), (1.25, [8, 0, 0]), (1.5, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.6, [0, 3, 2]), (0.9, [0, -2, 0]), (1.5, [0, 0, 0])])},
            "frontRightLeg": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-60, 0, 0]), (0.9, [16, 0, 0]), (1.5, [0, 0, 0])])},
            "frontLeftLeg": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-60, 0, 0]), (0.9, [16, 0, 0]), (1.5, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-22, 0, 0]), (0.9, [22, 0, 0]), (1.25, [18, 0, 0]), (1.5, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.6, [30, 0, 0]), (1.25, [34, 0, 0]), (1.5, [0, 0, 0])])},
        }},
        # 포효 (싸움 시작): 머리를 들고 아가리를 활짝
        "roar": {"animation_length": 1.3, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-30, 0, 0]), (1.0, [-30, 0, 0]), (1.3, [0, 0, 0])])},
            "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.3, [38, 0, 0]), (1.0, [38, 0, 0]), (1.3, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-8, 0, 0]), (1.0, [-8, 0, 0]), (1.3, [0, 0, 0])])},
        }},
    }
    return m, anims
