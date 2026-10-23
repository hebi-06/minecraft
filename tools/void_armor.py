#!/usr/bin/env python3
"""0.36.0 공허의 갑옷 (50렙 · 초월) — 입은 모습 · 아이콘 · 아이템 정의 (사용자가 견본 셋 중 ② 공허 갑각을 고름 — 클로드 디자인 캔버스 '공허의 갑옷 견본').

  python3 tools/void_armor.py              # tools/armor_art/void 의 원본 (.geo.json · .png · .glow.png) → 게임 자원
  python3 tools/void_armor.py --generate   # 원본을 이 파일의 디자인으로 다시 만든 뒤 위와 같이 (바꿔 넣은 원본을 덮어씀)

원본 (tools/armor_art/void — 클로드 디자인 · Blockbench 로 다듬을 때 같은 이름으로 바꿔 넣고 다시 돌리면 됨):
- void_<부위>.geo.json + void_<부위>.png (+ void_<부위>.glow.png — 빛나는 칸, 밤에도 밝음) — 베드락 모델, 뿌리 뼈 head · body · rightArm ·
  leftArm · rightLeg · leftLeg (갑옷 모델과 같음 — 상자 회전 없이 뼈 회전만, fw_cape 뼈는 걸으면 뒤로 날림 — 등 촉수)
만드는 것:
- assets/mmorpg/armor/void_<부위>.json (자바 사람 모델 조각 — ArmorModels) · assets/mmorpg/textures/entity/armor/void_<부위>.png · void_<부위>_glow.png
- 아이콘 (assets/rpg/textures/item/armor/<종류>_l50_transcend_<부위>.png): 같은 종류 초월 40렙 (황제) 아이콘의 갑옷 그림을 공허 갑각 색으로
  (오른쪽 아래 종류 배지는 그대로 — tools/armor_types_more.badge_mask) · 아이템 정의 · 아이콘 모델
"""
import argparse
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_items  # noqa: E402
import armor_types_more  # noqa: E402
from armor_art_kit import Piece, roots, art, plate, split, mirror_x, hexc, mix, shade, noise, flip_rows  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "armor_art", "void")
MM = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
SET = "void"
LEVEL = 50
GRADE = "transcend"
PIECES = ("helmet", "chest", "legs", "boots")

H0, H1, H2, H3, H4, H5 = (hexc(c) for c in ("0c0711", "1a0f24", "2b1839", "402554", "5c3576", "8352a8"))
M = hexc("0d0513")
V1, V2, V3 = (hexc(c) for c in ("ff3fa8", "ff8fd0", "ffd6ee"))
X0, X1, X2, X3, X4 = (hexc(c) for c in ("2e1060", "5f26c4", "9150ff", "cf96ff", "fbeaff"))

PAL = (H1, H2, H3, H4, H5)

LEG = {
    "E": (H5, None), "H": (H4, None), "P": (H3, None), "D": (H2, None), "k": (H1, None), "K": (H0, None), "m": (M, None),
    "v": (shade(V1, 0.75), V1), "V": (shade(V2, 0.8), V2), "W": (V3, V3),
    "x": (X1, None), "X": (X2, None), "c": (shade(X3, 0.85), X3), "C": (X4, X4),
    ".": (None, None),
}


def mirror_shader(s):
    swap = {"west": "east", "east": "west"}

    def m(face, x, y, w, h):
        return s(swap.get(face, face), w - 1 - x, y, w, h)
    return m


def add(p, bone, origin, size, shader, inflate=0.0):
    paint, glow = split(shader)
    p.cube(bone, origin, size, paint, glow, inflate)


def both(p, rb, lb, origin, size, shader, inflate=0.0):
    add(p, rb, origin, size, shader, inflate)
    add(p, lb, mirror_x(origin, size), size, mirror_shader(shader), inflate)


def segments(pal=PAL, seed=0, every=3, vein=0.18):
    """마디진 갑각: every 줄마다 어두운 틈 (그 틈의 일부는 분홍 핏줄로 빛남), 마디 위쪽은 밝은 테"""
    def s(face, x, y, w, h):
        if face in ("top", "bottom"):
            return (shade(pal[2], 1.1 if face == "top" else 0.6), None)
        r = y % every
        if r == every - 1 and y != h - 1:
            if noise(x, y, seed) < vein:
                return (shade(V1, 0.8), V1)
            return (M, None)
        c = pal[3] if r == 0 else (pal[2] if r < every - 2 else pal[1])
        if x == 0 or x == w - 1:
            c = mix(c, pal[1], 0.5)
        k = 0.92 + 0.14 * noise(x, y, seed + 7)
        return (shade(c, k), None)
    return s


def crystal(seed=0, glow_top=True):
    """공허 수정: 면을 대각선으로 나눠 밝은 · 어두운 면, 가장자리 밝게, 꼭대기 면은 빛남"""
    def s(face, x, y, w, h):
        if face == "top":
            return (X3, X4) if glow_top else (X3, None)
        if face == "bottom":
            return (X0, None)
        light = (x + (h - 1 - y) * (w / max(1, h))) < w * 0.9
        c = X2 if light else X1
        if face in ("east", "south"):
            c = shade(c, 0.8)
        if y == 0:
            c = X3
            return (c, X3 if glow_top else None)
        if x == 0 and face in ("north", "west"):
            c = mix(c, X3, 0.4)
        return (c, None)
    return s


def tip_glow():
    return lambda f, x, y, w, h: (shade(V2, 0.9), V2)


# ------------------------------------------------------------------ 투구

HELM_FRONT = [
    "HHHHHHHH",
    "PPPDDPPP",
    "WVDPPDVW",
    "DvVDDVvD",
    "PDDPPDDP",
    "mPPDDPPm",
    "DmPkkPmD",
    "kDmkkmDk",
]
HELM_SIDE_W = [  # 왼쪽 = 뒤
    "HHHHHHHH",
    "PPPPPPPP",
    "mmvmmmmD",
    "HHHHHPDD",
    "PPPPPPPD",
    "mmmmvmmm",
    "HHHHHPPP",
    "kDDDDDDk",
]
HELM_BACK = [
    "HHHHHHHH",
    "PPPPPPPP",
    "mmmvvmmm",
    "HHHHHHHH",
    "PPPPPPPP",
    "mmvmmvmm",
    "HHHHHHHH",
    "kDDDDDDk",
]
HELM_TOP = [
    "PPPPPPPP",
    "mmmvmmmm",
    "HHHHHHHH",
    "PPPPPPPP",
    "mmmmvmmm",
    "HHHHHHHH",
    "PPPPPPPP",
    "EEEEEEEE",
]


def helmet(p):
    roots(p, ["head"])
    sh = art({"north": HELM_FRONT, "west": HELM_SIDE_W, "east": flip_rows(HELM_SIDE_W), "south": HELM_BACK, "top": HELM_TOP}, LEG, segments(PAL, 1))
    add(p, "head", [-4, 24, -4], [8, 8, 8], sh, inflate=1.0)
    # 겹친 등딱지 판 (머리 위 → 뒤로)
    add(p, "head", [-3.5, 33, -3], [7, 1, 4], segments(PAL, 2, every=4))
    add(p, "head", [-3, 33, 1], [6, 1, 3], segments(PAL, 3, every=4))
    add(p, "head", [-3.5, 26, 5], [7, 6, 1], segments(PAL, 4))
    # 턱 집게 (뺨 → 앞으로 휨)
    both(p, "head", "head", [-5.2, 23, -4.5], [1, 3, 2], plate((H0, H1, H2, H3, H4), 5))
    both(p, "head", "head", [-4.7, 22, -6], [1, 2, 2], plate((H0, H1, H2, H3, H4), 6))
    both(p, "head", "head", [-4.2, 22, -7], [1, 1, 1], tip_glow())
    # 뒤로 누운 수정 가시 셋
    p.bone("hcrest", parent="head", pivot=(0, 33, 2), rotation=(-35, 0, 0))
    add(p, "hcrest", [-1, 33, 1], [2, 4, 2], crystal(7))
    both(p, "hcrest", "hcrest", [-3, 33, 2], [1, 3, 1], crystal(8))
    add(p, "hcrest", [-0.5, 37, 1.5], [1, 2, 1], crystal(9))


# ------------------------------------------------------------------ 갑옷

CHEST_FRONT = [
    "HHHHHHHH",
    "PPPHHPPP",
    "mvmPPmvm",
    "HHmHHmHH",
    "PPmXXmPP",
    "mmmCCmmm",
    "HHmXXmHH",
    "PPmPPmPP",
    "mvmmmmvm",
    "HHHHHHHH",
    "PPPPPPPP",
    "kkkkkkkk",
]


def tendril(seed):
    """촉수 한 가닥 (1×n): 위는 갑각색, 아래로 갈수록 보라 · 끝은 분홍 빛"""
    def s(face, x, y, w, h):
        if y >= h - 1:
            return (shade(V2, 0.9), V2)
        t = y / max(1, h - 1)
        c = mix(H3, X1, t * 0.8)
        if y % 2 == 1:
            c = shade(c, 0.8)
        return (c, None)
    return s


def chest(p):
    roots(p, ["body", "rightArm", "leftArm"])
    body = art({"north": CHEST_FRONT}, LEG, segments(PAL, 11))
    add(p, "body", [-4, 12, -2], [8, 12, 4], body, inflate=1.01)
    # 목 둘레 갑각 고리
    add(p, "body", [-4.5, 24, -3.4], [9, 1, 7], segments(PAL, 12, every=5))
    # 등 수정 줄 (등뼈를 따라 셋 — 뒤로 기움)
    p.bone("spine", parent="body", pivot=(0, 18, 3.5), rotation=(-30, 0, 0))
    add(p, "spine", [-1, 20, 3], [2, 4, 2], crystal(13))
    add(p, "spine", [-0.5, 16, 3], [1, 3, 1], crystal(14))
    add(p, "spine", [-0.5, 23.5, 3.5], [1, 3, 1], crystal(15))
    # 등 촉수 넷 (걸으면 흔들림 — fw_cape)
    p.bone("fw_cape", parent="body", pivot=(0, 22, 3.2), rotation=(6, 0, 0))
    for i, (x, n) in enumerate(((-3.5, 9), (-1.5, 12), (0.5, 11), (2.5, 8))):
        add(p, "fw_cape", [x, 22 - n, 3.2], [1, n, 1], tendril(20 + i))
    # 팔 (마디 갑각)
    arm = segments(PAL, 16)
    both(p, "rightArm", "leftArm", [-8, 12, -2], [4, 12, 4], arm, inflate=1.0)
    # 어깨 갑각 (두 겹) + 수정 무리
    both(p, "rightArm", "leftArm", [-9.5, 22, -3.5], [6, 3, 7], segments(PAL, 17, every=3))
    both(p, "rightArm", "leftArm", [-9.5, 20, -3], [5, 2, 6], segments(PAL, 18, every=2, vein=0.4))
    p.bone("rx", parent="rightArm", pivot=(-7, 25, 0), rotation=(0, 0, 28))
    p.bone("lx", parent="leftArm", pivot=(7, 25, 0), rotation=(0, 0, -28))
    both(p, "rx", "lx", [-8.5, 25, -1.5], [2, 6, 2], crystal(19))
    both(p, "rx", "lx", [-9.5, 25, 1], [1, 4, 1], crystal(20))
    both(p, "rx", "lx", [-8, 25, -3], [1, 3, 1], crystal(21))
    both(p, "rx", "lx", [-8, 31, -1], [1, 2, 1], crystal(22))
    # 팔뚝 수정 칼날 (바깥)
    both(p, "rightArm", "leftArm", [-9.6, 13, -1], [1, 4, 2], crystal(23, glow_top=False))
    both(p, "rightArm", "leftArm", [-10.2, 14, -0.5], [1, 2, 1], crystal(24))


# ------------------------------------------------------------------ 바지 · 신발

WAIST = ["........"] * 7 + ["HHHHHHHH", "PPPPPPPP", "mvmmmvmm", "HHHHHHHH", "kkkkkkkk"]
WAIST_SIDE = ["...."] * 7 + ["HHHH", "PPPP", "mmvm", "HHHH", "kkkk"]


def legs(p):
    roots(p, ["body", "rightLeg", "leftLeg"])
    waist = art({"north": WAIST, "south": WAIST, "west": WAIST_SIDE, "east": WAIST_SIDE, "top": ["........"] * 4, "bottom": ["PPPPPPPP"] * 4}, LEG)
    add(p, "body", [-4, 12, -2], [8, 12, 4], waist, inflate=0.5)
    both(p, "rightLeg", "leftLeg", [-3.9, 0, -2], [4, 12, 4], segments(PAL, 31), inflate=0.5)
    # 무릎 수정
    both(p, "rightLeg", "leftLeg", [-2.4, 5, -3.2], [1, 2, 1], crystal(32))
    both(p, "rightLeg", "leftLeg", [-4.4, 4, -3], [5, 1, 1], segments(PAL, 33))


BOOT = ["...."] * 6 + ["HHHH", "PPPP", "mvmm", "HHHH", "PPPP", "kkkk"]


def boots(p):
    roots(p, ["rightLeg", "leftLeg"])
    boot = art({"north": BOOT, "south": BOOT, "west": BOOT, "east": BOOT, "top": ["...."] * 4, "bottom": ["kkkk"] * 4}, LEG)
    both(p, "rightLeg", "leftLeg", [-3.9, 0, -2], [4, 12, 4], boot, inflate=1.0)
    # 발톱 셋 (앞) · 뒤꿈치 가시
    claw = plate((H0, H1, H2, H3, H4), 41)
    for i, x in enumerate((-4.4, -2.4, -0.4)):
        both(p, "rightLeg", "leftLeg", [x, -1, -4.4], [1, 1, 2], claw)
        both(p, "rightLeg", "leftLeg", [x, -1, -5.4], [1, 1, 1], tip_glow())
    both(p, "rightLeg", "leftLeg", [-2.4, 0, 3], [1, 2, 2], crystal(42))




def generate():
    """이 파일의 디자인 → 원본 (베드락 모델 · 그림 · 빛 그림)"""
    for name, fn in (("helmet", helmet), ("chest", chest), ("legs", legs), ("boots", boots)):
        p = Piece(f"{SET}_{name}", 128, 128)
        fn(p)
        p.save(SRC)
    print(f"generated {SRC}")


def convert():
    """원본 → 자바 조각 JSON · 몸 그림 · 빛 그림"""
    tex_dir = os.path.join(MM, "textures", "entity", "armor")
    os.makedirs(tex_dir, exist_ok=True)
    for piece in PIECES:
        name = f"{SET}_{piece}"
        m = armor_items.convert_geo(os.path.join(SRC, f"{name}.geo.json"))
        shutil.copyfile(os.path.join(SRC, f"{name}.png"), os.path.join(tex_dir, f"{name}.png"))
        m["texture"] = f"mmorpg:textures/entity/armor/{name}.png"
        glow = os.path.join(SRC, f"{name}.glow.png")
        out_glow = os.path.join(tex_dir, f"{name}_glow.png")
        if os.path.exists(glow):
            shutil.copyfile(glow, out_glow)
            m["emissive"] = f"mmorpg:textures/entity/armor/{name}_glow.png"
        elif os.path.exists(out_glow):
            os.remove(out_glow)
        armor_items.write_json(os.path.join(MM, "armor", f"{name}.json"), m)
    print("converted", [f"{SET}_{p}" for p in PIECES])


# 아이콘: 황제 (초월 40렙) 아이콘의 금빛 갑옷 그림 → 공허 갑각 색 (가까운 색으로)
ICON_MAP = [
    ((126, 84, 19), hexc("1a0f24")),    # 테두리
    ((104, 70, 23), hexc("1a0f24")),
    ((212, 162, 58), hexc("4a2b63")),   # 판
    ((242, 210, 122), hexc("8352a8")),  # 윤
    ((163, 22, 31), hexc("ff3fa8")),    # 붉은 줄 → 분홍 핏줄
]


def icon_color(c):
    """가까운 금빛 · 붉은 칸의 새 색 (먼 색 — 배지의 흰 빛 · 테두리 — 은 None: 그대로)"""
    if max(c) - min(c) < 40:  # 회색 · 흰 빛 (배지의 빛 점)
        return None
    best = min(ICON_MAP, key=lambda e: sum((c[i] - e[0][i]) ** 2 for i in range(3)))
    if sum((c[i] - best[0][i]) ** 2 for i in range(3)) > 90 ** 2:
        return None
    return best[1]


def icons():
    r, types, order, _ = armor_types_more.rules()
    tex = os.path.join(RPG, "textures", "item", "armor")
    n = 0
    for piece in PIECES:
        mask = armor_types_more.badge_mask(f"l40_{GRADE}_{piece}")
        for t in order:
            src = np.array(Image.open(os.path.join(tex, f"{t}_l40_{GRADE}_{piece}.png")).convert("RGBA"))
            out = src.copy()
            for y in range(src.shape[0]):
                for x in range(src.shape[1]):
                    if mask[y, x] or src[y, x, 3] < 200:
                        continue
                    c = icon_color(tuple(int(v) for v in src[y, x, :3]))
                    if c is not None:
                        out[y, x, :3] = c
            key = f"{t}_l{LEVEL}_{GRADE}_{piece}"
            Image.fromarray(out, "RGBA").save(os.path.join(tex, f"{key}.png"))
            armor_types_more.item(f"armor_{key}", f"armor/{key}")
            n += 1
    print(f"icons {n}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--generate", action="store_true", help="원본을 이 파일의 디자인으로 다시 만듦")
    a = ap.parse_args()
    if a.generate or not os.path.exists(os.path.join(SRC, f"{SET}_chest.geo.json")):
        generate()
    convert()
    icons()


if __name__ == "__main__":
    main()
