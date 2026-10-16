#!/usr/bin/env python3
"""장식 그림 · 모델 만들기 (0.13.0 — src/main/resources/mmorpg/cosmetics.json 의 항목마다).

  python3 tools/cosmetic_art.py              # tools/cosmetic_art · tools/weapon_art 의 원본 → 게임 자원 (모델 · 텍스처 · 아이콘 · 아이템 정의)
  python3 tools/cosmetic_art.py --samples    # 없는 임시 견본 원본을 만든 뒤 위와 같이 (있는 원본 — 바꿔 넣은 디자인 — 은 그대로)
  python3 tools/cosmetic_art.py --samples --force   # 임시 견본 원본을 모두 다시 (바꿔 넣은 디자인도 덮어씀)
  (무기 스킨을 더하거나 바꾼 뒤) python3 tools/weapon_models.py   # 무기 스킨 디자인 → 손에 든 3D 모델 · 빛 알갱이 (WeaponFxData.BY_SKIN)

원본 (tools/cosmetic_art — 클로드 디자인에서 만든 것으로 바꿀 때는 같은 이름으로 넣고 다시 돌리면 됨):
- <model>.geo.json + <model>.png   베드락 모델 (뿌리 뼈 head · body · rightArm · leftArm · rightLeg · leftLeg — 갑옷 모델과 같음, 상자 회전 없이 뼈 회전만)
  + <model>.glow.png (있으면 그 그림을 빛나게 한 번 더 그림 — 천사의 고리처럼 어두운 곳에서도 보이는 부분)
- <model>.model.json + 그 안의 "texture" 파일   자바 사람 모델 조각 그대로 (견본 갑옷 — 지금 갑옷 세트의 모델에 색만 바꾼 텍스처)
- <id>.icon.png   장식 창 아이콘 (32×32) — 없으면 모델 · 무기 디자인에서 그림 (오라 · 칭호는 색으로 그린 그림)
무기 스킨 원본은 무기 디자인과 같은 자리 (tools/weapon_art/<design>.geo.json · .png · .java.json · .java.png + designs.json 의 항목).

만드는 것:
- assets/mmorpg/cosmetic/<model>.json (자바 사람 모델 조각 — 클라이언트 CosmeticModels 가 읽음) · assets/mmorpg/textures/entity/cosmetic/*.png
- assets/rpg/textures/item/cosmetic/<id>.png · models/item/cosmetic/<id>.json · items/cosmetic_<id>.json (아이템 모델 rpg:cosmetic_<id> — 장식 창 아이콘,
  무기 스킨은 손에 든 모습도 이것: gui 는 아이콘, 나머지는 3D 디자인 rpg:item/model/<design>)
"""
import argparse
import colorsys
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_items  # noqa: E402
import cosmetic_view as cv  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "cosmetic_art")
WEAPON_ART = os.path.join(ROOT, "tools", "weapon_art")
COSMETICS = os.path.join(ROOT, "src", "main", "resources", "mmorpg", "cosmetics.json")
MM = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
ICON = 32


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


# ------------------------------------------------------------ 색 바꾸기 (견본 — 재질마다 밝기 → 새 색 띠)

def lum(r, g, b):
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def material(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h *= 360
    if s < 0.18 or v < 0.08:
        return "metal"
    if v < 0.3:
        return "dark"
    if h < 16 or h >= 335:
        return "red"
    if h < 48 and v < 0.5:
        return "wood"
    if h < 72:
        return "gold"
    if h < 170:
        return "green"
    if h < 255:
        return "blue"
    return "purple"


def ramp(stops, t):
    """stops = [(위치 0~1, (r, g, b)), …] → t 의 색"""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            k = 0 if p1 == p0 else (t - p0) / (p1 - p0)
            return tuple(int(round(c0[i] + (c1[i] - c0[i]) * k)) for i in range(3))
    return stops[-1][1]


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def R(*cols):
    """고르게 놓은 색 띠"""
    n = len(cols) - 1
    return [(i / n, hexc(c)) for i, c in enumerate(cols)]


# 견본 색 (재질 → 밝기 띠). 없는 재질은 그대로
PALETTES = {
    "sakura": {"gold": R("5a1f3c", "c2547f", "f6a5c6", "fff1f6"), "red": R("3a0f24", "8c2a55", "e26d9e"), "metal": R("3d2a36", "b9a2b0", "fff6fa"),
               "wood": R("2b1420", "6a3550", "a8607f")},
    "obsidian": {"blue": R("0c0716", "2a1648", "6d3fc0", "c9a6ff"), "metal": R("07050c", "211a33", "5b4b86", "b9a8ff"), "gold": R("2b1450", "8a5cff", "e2d4ff")},
    "lava": {"gold": R("2a0d06", "a32a0c", "ff7a1a", "ffe27a"), "red": R("140706", "3a1310", "6e2a1c"), "metal": R("120c0b", "3d302c", "8a756c"),
             "wood": R("120807", "36201a", "5e3a2c")},
    "jade": {"gold": R("0c2a22", "1f7a5a", "5fd7a0", "d6ffe9"), "red": R("10261f", "2d5c49", "5f9c7e"), "metal": R("1c2b27", "6f8c83", "e3f5ee"),
             "wood": R("0f1f1a", "27443a", "4d7a69")},
    "frost": {"gold": R("10284a", "3d7fc4", "9fd6ff", "ffffff"), "red": R("0c1a33", "26508a", "6aa8e6"), "metal": R("1a2433", "8fb2cc", "f2fbff"),
              "wood": R("0f1a2a", "2a4566", "5b85b0")},
    "starlight": {"gold": R("231a4a", "6f5fc8", "c8c0ff", "ffffff"), "red": R("140f2e", "3b2f7a", "8a7cf0"), "metal": R("1c1a2e", "8e88b8", "f4f2ff")},
    "abyss": {"red": R("0b0414", "3b0f6e", "8c3cff", "e9d2ff"), "purple": R("0a0612", "2c1a5a", "7d5cff", "e2d8ff"), "warm": R("1a0b2e", "7a3cff", "f0e0ff"),
              "gold": R("1a0b2e", "7a3cff", "f0e0ff"), "blue": R("0b0a1e", "3a2f8a", "a99cff"), "metal": R("08060c", "2b2338", "a69bc0")},
    "dusk": {"gold": R("3a0f2a", "c23f5a", "ff9a6a", "ffe4c4"), "red": R("1e0a24", "5a1f5a", "a0408a"), "metal": R("2a1a2a", "a07890", "ffe8ef"),
             "wood": R("1a0c18", "40203a", "6e3a60")},
    "wraith": {"gold": R("062421", "128a74", "5cf2cf", "e0fff7"), "red": R("071a1a", "164a46", "2f8a80"), "metal": R("0c1716", "52807a", "d8fff8"),
               "purple": R("06201c", "1c7466", "7cf5da"), "blue": R("06201c", "1c7466", "7cf5da"), "wood": R("0a1514", "1f3a37", "3d6863")},
    "coral": {"gold": R("3a1210", "d2584a", "ffae94", "fff0e6"), "red": R("0e2a2e", "1f6a70", "4bb3b0"), "metal": R("2a2524", "b8a39c", "fff5f0"),
              "wood": R("102326", "24484c", "3f7a7a")},
    "dawn": {"gold": R("3a1a1a", "b8665a", "f2b6a0", "fff2ea"), "metal": R("2a2222", "b89c98", "fff6f2")},
    "paladin": {"dark": R("101018", "2e2e3a", "4a4a5a"), "metal": R("3a3a44", "c8ccd6", "f4f6ff", "ffffff"), "blue": R("3a0a10", "9a1a24", "e0444c"), "gold": R("5a3a10", "d6a640", "ffe9a8"),
                "wood": R("3a2a1a", "8a6a40", "c8a070"), "red": R("3a0a10", "9a1a24", "e0444c")},
    "frost_dragon": {"red": R("163e6e", "3d8fd6", "9ddcff", "ffffff"), "dark": R("101c2c", "263e5c", "41668e"),
                     "metal": R("1c2c42", "4e6e94", "aacbe8", "f2fbff"), "gold": R("2a7ab0", "7fdcff", "e8fbff"),
                     "wood": R("0f1c2c", "27425f", "4a7298")},
}


def recolor(img, palette):
    """재질마다 밝기 → 새 색 띠 (알파 그대로)"""
    img = img.convert("RGBA")
    out = img.copy()
    src = img.load()
    dst = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            m = material(r, g, b)
            stops = palette.get(m)
            if stops is None and m == "dark":  # 어두운 칸: 따로 정한 띠가 없으면 원래 재질의 띠
                h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                stops = palette.get(material(min(255, r * 2.4), min(255, g * 2.4), min(255, b * 2.4)))
            if stops is None and m == "gold":
                stops = palette.get("warm")
            if stops is None:
                continue
            nr, ng, nb = ramp(stops, lum(r, g, b) * 1.08)
            dst[x, y] = (nr, ng, nb, a)
    return out


# ------------------------------------------------------------ 모델 짓기 (베드락 좌표 — 위 +y, 앞 -z, 사람의 오른쪽 -x, 픽셀)

FACES = ("top", "bottom", "west", "north", "east", "south")


def regions(u, v, dx, dy, dz):
    """상자 UV (texOffs u, v) 의 면 자리 (x, y, 가로, 세로) — 밖에서 본 그대로 (north: 왼쪽 = -x, south: 왼쪽 = +x, west: 왼쪽 = 뒤, east: 왼쪽 = 앞)"""
    return {
        "top": (u + dz, v, dx, dz), "bottom": (u + dz + dx, v, dx, dz),
        "west": (u, v + dz, dz, dy), "north": (u + dz, v + dz, dx, dy),
        "east": (u + dz + dx, v + dz, dz, dy), "south": (u + 2 * dz + dx, v + dz, dx, dy),
    }


class Design:
    """뼈 · 상자 · 면마다 칠하기 → 베드락 모델 (.geo.json) + 텍스처 (.png) (+ 빛 그림)"""

    def __init__(self, name, tw=64, th=64):
        self.name = name
        self.tw, self.th = tw, th
        self.bones = []
        self.cubes = []  # (뼈, origin, size, paint, glow)

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=(0, 0, 0)):
        b = {"name": name, "pivot": [float(x) for x in pivot]}
        if parent:
            b["parent"] = parent
        if any(rotation):
            b["rotation"] = [float(x) for x in rotation]
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, glow=None):
        """paint(면, x, y, 가로, 세로) → (r, g, b, a) 또는 None (투명). glow 도 같은 꼴 (빛나는 그림 — 없으면 None)"""
        assert all(float(s).is_integer() for s in size), (self.name, size)
        self.cubes.append((bone, [float(x) for x in origin], [int(s) for s in size], paint, glow))

    def build(self):
        # UV 자리: 높은 상자부터 줄마다 왼쪽에서 오른쪽으로
        order = sorted(range(len(self.cubes)), key=lambda i: -(self.cubes[i][2][2] + self.cubes[i][2][1]))
        uv = {}
        x = y = row_h = 0
        for i in order:
            dx, dy, dz = self.cubes[i][2]
            w, h = 2 * (dx + dz), dz + dy
            if x + w > self.tw:
                x, y, row_h = 0, y + row_h, 0
            if y + h > self.th:
                raise SystemExit(f"{self.name}: 텍스처 {self.tw}x{self.th} 에 상자가 다 들어가지 않음")
            uv[i] = (x, y)
            x += w
            row_h = max(row_h, h)
        tex = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        has_glow = False
        tp, gp = tex.load(), glow.load()
        by_bone = {}
        for i, (bone, origin, size, paint, gpaint) in enumerate(self.cubes):
            u, v = uv[i]
            dx, dy, dz = size
            for face, (fx, fy, fw, fh) in regions(u, v, dx, dy, dz).items():
                for yy in range(fh):
                    for xx in range(fw):
                        c = paint(face, xx, yy, fw, fh)
                        if c is not None:
                            tp[fx + xx, fy + yy] = tuple(int(max(0, min(255, round(k)))) for k in c)
                        if gpaint is not None:
                            g = gpaint(face, xx, yy, fw, fh)
                            if g is not None:
                                gp[fx + xx, fy + yy] = tuple(int(max(0, min(255, round(k)))) for k in g)
                                has_glow = True
            by_bone.setdefault(bone, []).append({"origin": origin, "size": size, "uv": [u, v]})
        bones = []
        for b in self.bones:
            b = dict(b)
            if b["name"] in by_bone:
                b["cubes"] = by_bone[b["name"]]
            bones.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.mmorpg.{self.name}", "texture_width": self.tw, "texture_height": self.th},
            "bones": bones}]}
        return geo, tex, (glow if has_glow else None)

    def save(self, folder):
        geo, tex, glow = self.build()
        os.makedirs(folder, exist_ok=True)
        write_json(os.path.join(folder, f"{self.name}.geo.json"), geo, indent=1)
        tex.save(os.path.join(folder, f"{self.name}.png"))
        gp = os.path.join(folder, f"{self.name}.glow.png")
        if glow is not None:
            glow.save(gp)
        elif os.path.exists(gp):
            os.remove(gp)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def shade(c, k):
    return tuple(min(255, max(0, x * k)) for x in c[:3]) + tuple(c[3:])


def noise(x, y, seed=0):
    """작은 무늬 (0~1)"""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def solid(base, top=1.12, side=0.92, bottom=0.7, edge=0.78, rough=0.06, seed=0):
    """한 색 재질: 면마다 밝기 · 가장자리 어둡게 · 잔무늬"""
    base = tuple(base) + ((255,) if len(base) == 3 else ())

    def f(face, x, y, w, h):
        k = {"top": top, "bottom": bottom}.get(face, side if face in ("west", "east") else 1.0)
        if (x == 0 or y == 0 or x == w - 1 or y == h - 1) and w > 2 and h > 2:
            k *= edge
        k *= 1 - rough + 2 * rough * noise(x, y, seed + FACES.index(face))
        return shade(base, k)
    return f


def none_paint(face, x, y, w, h):
    return None


# ---- 왕관

GOLD = (236, 188, 64)
GOLD_D = (168, 112, 30)
GOLD_L = (255, 232, 140)
RUBY = (220, 30, 60)
SAPPHIRE = (40, 110, 230)
EMERALD = (40, 200, 110)
PEARL = (250, 246, 236)


def gold_paint(gem=None, gem_at=None, seed=0):
    def f(face, x, y, w, h):
        c = GOLD
        if y == 0:
            c = GOLD_L
        elif y == h - 1:
            c = GOLD_D
        if face == "top":
            c = GOLD_L
        if face == "bottom":
            c = GOLD_D
        c = shade(c, 0.94 + 0.12 * noise(x, y, seed))
        if gem and face in ("north", "south", "west", "east") and gem_at and gem_at(face, x, y, w, h):
            return gem + (255,)
        return c + (255,)
    return f


def crown():
    d = Design("crown", 64, 32)
    d.bone("head", pivot=(0, 24, 0))
    d.bone("crown", "head", pivot=(0, 32, 0))
    band_y = 31.0

    def gems(face, x, y, w, h):
        if y != 1:
            return False
        if face in ("north", "south"):
            return x in (w // 2 - 1, w // 2) or x in (1, w - 2)
        return x == w // 2

    def gem_color(face):
        return {"north": RUBY, "south": SAPPHIRE, "west": EMERALD, "east": EMERALD}[face]

    def band(seed):
        def f(face, x, y, w, h):
            if face in ("north", "south", "west", "east") and gems(face, x, y, w, h):
                return gem_color(face) + (255,)
            return gold_paint(seed=seed)(face, x, y, w, h)
        return f
    d.cube("crown", (-4.5, band_y, -4.5), (9, 3, 1), band(1))
    d.cube("crown", (-4.5, band_y, 3.5), (9, 3, 1), band(2))
    d.cube("crown", (-4.5, band_y, -3.5), (1, 3, 7), band(3))
    d.cube("crown", (3.5, band_y, -3.5), (1, 3, 7), band(4))
    top = band_y + 3
    # 뾰족한 끝: 모서리 넷 (2) · 면 가운데 (3, 끝에 진주) · 그 사이 (1)
    for sx in (-4.5, 3.5):
        for sz in (-4.5, 3.5):
            d.cube("crown", (sx, top, sz), (1, 2, 1), gold_paint(seed=5))
    for (ox, oz) in ((-0.5, -4.5), (-0.5, 3.5), (-4.5, -0.5), (3.5, -0.5)):
        d.cube("crown", (ox, top, oz), (1, 3, 1), gold_paint(seed=6))
        d.cube("crown", (ox, top + 3, oz), (1, 1, 1), solid(PEARL, rough=0.02))
    for (ox, oz) in ((-2.5, -4.5), (1.5, -4.5), (-2.5, 3.5), (1.5, 3.5), (-4.5, -2.5), (-4.5, 1.5), (3.5, -2.5), (3.5, 1.5)):
        d.cube("crown", (ox, top, oz), (1, 1, 1), gold_paint(seed=7))
    return d


# ---- 천사의 고리 (짧은 막대 12 개를 둥글게 — 옆에서 봐도 두께가 보임 · 빛 그림)

HALO_GOLD = (255, 214, 110)
HALO_LIGHT = (255, 246, 205)


def halo_paint(face, x, y, w, h):
    c = HALO_LIGHT if face in ("top", "north") else HALO_GOLD
    return shade(c, 0.95 + 0.08 * noise(x, y, 21)) + (255,)


def halo_glow(face, x, y, w, h):
    return (230, 180, 70, 255) if face != "top" else (255, 236, 170, 255)


def halo():
    d = Design("halo", 32, 32)
    d.bone("head", pivot=(0, 24, 0))
    d.bone("fw_bob", "head", pivot=(0, 35, 0), rotation=(-8, 0, 0))
    d.bone("fw_spin", "fw_bob", pivot=(0, 35, 0))
    n, r = 12, 5.0
    for i in range(n):
        seg = d.bone(f"halo_{i}", "fw_spin", pivot=(0, 35, 0), rotation=(0, i * 360 / n, 0))
        d.cube(seg, (-1.5, 34.5, -r - 0.5), (3, 1, 1), halo_paint, halo_glow)
    return d


# ---- 마법사 모자

PURPLE = (92, 52, 168)
PURPLE_D = (58, 30, 112)
STAR = (255, 222, 90)


def hat_paint(seed=0, band=False, stars=True):
    def f(face, x, y, w, h):
        if band:  # 금띠
            c = (200, 150, 40) if y == h - 1 else (240, 196, 70)
            return shade(c, 0.95 + 0.1 * noise(x, y, seed)) + (255,)
        c = PURPLE_D if face == "bottom" else PURPLE
        k = 0.92 + 0.14 * noise(x, y, seed)
        if face in ("west", "east"):
            k *= 0.9
        if stars and face in ("north", "south", "west", "east", "top") and noise(x, y, seed + 9) > 0.9:
            return STAR + (255,)
        return shade(c, k) + (255,)
    return f


def wizard_hat():
    d = Design("wizard_hat", 64, 64)
    d.bone("head", pivot=(0, 24, 0))
    # 뼈 이름 hat 은 쓰지 않음 (사람 모델의 겉 머리 층 이름 — 같은 이름이면 그 자리로 옮겨짐)
    d.bone("wizhat", "head", pivot=(0, 32, 0))
    d.cube("wizhat", (-7, 31, -7), (14, 1, 14), hat_paint(1, stars=False))
    d.cube("wizhat", (-4.5, 32, -4.5), (9, 2, 9), hat_paint(2, band=True))
    d.cube("wizhat", (-4, 34, -4), (8, 3, 8), hat_paint(3))
    d.bone("wizhat_mid", "wizhat", pivot=(0, 37, 0), rotation=(-8, 0, 0))
    d.cube("wizhat_mid", (-3, 37, -3), (6, 3, 6), hat_paint(4))
    d.bone("wizhat_top", "wizhat_mid", pivot=(0, 40, 0), rotation=(-12, 0, 0))
    d.cube("wizhat_top", (-2, 40, -2), (4, 3, 4), hat_paint(5))
    d.bone("wizhat_tip", "wizhat_top", pivot=(0, 43, 0), rotation=(-18, 0, 0))
    d.cube("wizhat_tip", (-1, 43, -1), (2, 3, 2), hat_paint(6))
    d.cube("wizhat_tip", (-0.5, 46, -0.5), (1, 1, 1), solid(STAR, rough=0.02), lambda *a: (180, 150, 40, 255))
    return d


# ---- 진홍 망토

CRIMSON = (170, 22, 38)
CRIMSON_D = (96, 10, 22)


def cape_paint(face, x, y, w, h):
    if face in ("west", "east", "top", "bottom"):
        return shade(GOLD, 0.9) + (255,)
    if x == 0 or x == w - 1 or y == h - 1:
        return shade(GOLD, 0.95 + 0.1 * noise(x, y, 3)) + (255,)
    if face == "north":  # 안쪽 (몸 쪽)
        return shade(CRIMSON_D, 0.9 + 0.12 * noise(x, y, 4)) + (255,)
    # 뒤쪽: 금색 마름모 문장 (가운데 홍옥) · 아래로 갈수록 어둡게 · 세로 주름
    cx, cy = (w - 1) / 2, 5
    dd = abs(x - cx) + abs(y - cy)
    if dd <= 1:
        return RUBY + (255,)
    if dd <= 2.5:
        return GOLD + (255,)
    k = 0.9 + 0.1 * noise(x, y, 5) - 0.16 * (y / h) - (0.08 if x % 3 == 1 else 0)
    return shade(CRIMSON, k) + (255,)


def crimson_cape():
    d = Design("crimson_cape", 64, 32)
    d.bone("body", pivot=(0, 24, 0))
    d.bone("fw_cape", "body", pivot=(0, 24, 3.2), rotation=(6, 0, 0))
    d.cube("fw_cape", (-5, 8, 3.2), (10, 16, 1), cape_paint)
    # 어깨 고리 (금)
    d.cube("body", (-5, 22.5, 2.4), (2, 2, 1), gold_paint(seed=8))
    d.cube("body", (3, 22.5, 2.4), (2, 2, 1), gold_paint(seed=9))
    return d


# ---- 날개 (판 하나에 날개 모양을 그림 — 옆면은 투명, 앞 · 뒤 면에 같은 모양)

def _lerp_curve(points, u):
    """(u, v) 점들을 잇는 꺾은선의 u 자리 v"""
    for (u0, v0), (u1, v1) in zip(points, points[1:]):
        if u <= u1:
            t = 0 if u1 == u0 else (u - u0) / (u1 - u0)
            return v0 + (v1 - v0) * t
    return points[-1][1]


def _seg_dist(p, a, b):
    ax, ay = a
    bx, by = b
    px, py = p
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / max(1e-9, dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


ANGEL_TOP = [(0.0, 0.42), (0.25, 0.12), (0.5, 0.0), (0.75, 0.04), (1.0, 0.16)]
ANGEL_BOTTOM = [(0.0, 0.7), (0.3, 0.8), (0.6, 0.94), (0.82, 1.0), (1.0, 0.62)]
BAT_WRIST = (0.42, 0.02)
BAT_TIPS = [(0.0, 0.62), (0.4, 0.98), (0.7, 0.88), (1.0, 0.42)]


def angel_pixel(u, v, x, y, w, h):
    top = _lerp_curve(ANGEL_TOP, u)
    bottom = _lerp_curve(ANGEL_BOTTOM, u)
    # 깃털 끝: 2.5 칸마다 둥근 끝 (가운데가 가장 긺)
    fw = 2.5 / (w - 1)
    k = (u % fw) / fw
    bottom -= (1 - math.sin(math.pi * k)) * 1.6 / h
    if not (top <= v <= bottom):
        return None
    depth = (v - top) / max(1e-6, bottom - top)
    if v - top < 1.4 / h:
        return shade(GOLD_L, 0.98) + (255,)  # 앞 가장자리 금빛
    if depth < 0.32:  # 덮깃: 비늘 무늬
        row = int((v - top) * h / 2)
        col = int((u * (w - 1) + (row % 2)) / 2)
        c = (252, 251, 246) if noise(col, row, 31) > 0.45 else (238, 238, 236)
        if (u * (w - 1) + (row % 2)) % 2 < 0.6:
            c = shade(c, 0.9)
        return c + (255,)
    # 긴 깃: 깃마다 세로 띠 · 사이 선
    fx = (u * (w - 1)) % 2.5
    c = (248, 247, 244) if int(u * (w - 1) / 2.5) % 2 == 0 else (234, 236, 240)
    if fx < 0.5:
        c = (200, 204, 214)
    kk = 0.96 - 0.14 * depth + 0.05 * noise(x, y, 33)
    return shade(c, kk) + (255,)


def bat_pixel(u, v, x, y, w, h):
    top = _lerp_curve([(0.0, 0.4), BAT_WRIST, (1.0, 0.34)], u)
    # 아래 가장자리: 손가락 끝 사이가 오목하게
    bottom = BAT_TIPS[-1][1]
    for (u0, v0), (u1, v1) in zip(BAT_TIPS, BAT_TIPS[1:]):
        if u <= u1:
            t = 0 if u1 == u0 else (u - u0) / (u1 - u0)
            bottom = v0 + (v1 - v0) * t - 0.16 * math.sin(math.pi * t)
            break
    if not (top <= v <= bottom):
        return None
    p = (u * (w - 1), v * (h - 1))

    def px(q):
        return (q[0] * (w - 1), q[1] * (h - 1))
    bones = [((0.0, 0.4), BAT_WRIST)] + [(BAT_WRIST, t) for t in BAT_TIPS[1:]]
    if any(_seg_dist(p, px(a), px(b)) < 0.75 for a, b in bones) or v - top < 1.2 / h:
        return (54, 16, 22, 255)
    c = mix((168, 24, 40), (70, 8, 18), (v - top) / max(1e-6, bottom - top))
    c = shade(c, 0.86 + 0.18 * noise(x, y, 41))
    return tuple(c) + (255,)


def wing_paint(right, demon=False):
    """right = 오른쪽 날개 (사람의 오른쪽 -x 로 뻗음). north (앞에서 보면 왼쪽 = -x) · south (뒤에서 보면 왼쪽 = +x)"""
    def f(face, x, y, w, h):
        if face not in ("north", "south"):
            return None
        outer_left = (face == "north") == right
        u = (w - 1 - x if outer_left else x) / (w - 1)  # 0 = 등, 1 = 바깥 끝
        v = y / (h - 1)
        return (bat_pixel if demon else angel_pixel)(u, v, x, y, w, h)
    return f


def wings(name, demon=False):
    d = Design(name, 64, 64)
    d.bone("body", pivot=(0, 24, 0))
    span, height = (15, 20) if not demon else (15, 17)
    for side, sign in (("right", -1), ("left", 1)):
        bone = f"fw_wing_{side}"
        # 등 가운데 옆에서 시작 · 바깥 끝이 조금 뒤로 (y 회전) · 살짝 들어 올림 (z 회전)
        d.bone(bone, "body", pivot=(sign * 1.0, 22, 2.8), rotation=(0, sign * -24, sign * -6))
        x0 = sign * 1.0 - span if sign < 0 else sign * 1.0
        top = 22 + 6
        d.cube(bone, (x0, top - height, 2.8), (span, height, 1), wing_paint(side == "right", demon))
    return d


SAMPLE_DESIGNS = {"crown": crown, "halo": halo, "wizard_hat": wizard_hat, "crimson_cape": crimson_cape,
                  "angel_wings": lambda: wings("angel_wings"), "demon_wings": lambda: wings("demon_wings", demon=True)}


# ------------------------------------------------------------ 견본 만들기

def sample_weapon(item_id, item):
    s = item["sample"]
    base, design = s["from"], item["design"]
    pal = PALETTES[s["palette"]]
    for ext in (".geo.json", ".java.json", ".java.png.mcmeta"):
        p = os.path.join(WEAPON_ART, base + ext)
        if os.path.exists(p):
            shutil.copyfile(p, os.path.join(WEAPON_ART, design + ext))
        elif os.path.exists(os.path.join(WEAPON_ART, design + ext)):
            os.remove(os.path.join(WEAPON_ART, design + ext))
    for ext in (".png", ".java.png"):
        p = os.path.join(WEAPON_ART, base + ext)
        if os.path.exists(p):
            recolor(Image.open(p), pal).save(os.path.join(WEAPON_ART, design + ext))
    # 디자인 목록: 손 자리 (fp) 는 원본 그대로, 빛 알갱이 색은 새 색
    meta_path = os.path.join(WEAPON_ART, "designs.json")
    meta = json.load(open(meta_path, encoding="utf-8"))
    d = json.loads(json.dumps(meta["designs"][base]))
    if "fx" in d:
        cols = []
        for c in d["fx"]["colors"]:
            r, g, b = (int(round(x * 255)) for x in c)
            m = material(r, g, b)
            stops = pal.get(m) or pal.get("gold") or next(iter(pal.values()))
            nr, ng, nb = ramp(stops, lum(r, g, b) * 1.08)
            cols.append([round(nr / 255, 3), round(ng / 255, 3), round(nb / 255, 3)])
        d["fx"]["colors"] = cols
    meta["designs"][design] = d
    meta["designs"] = dict(sorted(meta["designs"].items()))
    with open(meta_path, "w", encoding="utf-8") as f:  # 원래 모양 그대로 (들여쓰기 1, 끝 줄바꿈 없음)
        json.dump(meta, f, ensure_ascii=False, indent=1)


def sample_armor(item_id, item):
    s = item["sample"]
    base_set, pal = s["from"], PALETTES[s["palette"]]
    piece = item["slot"]
    m = json.load(open(os.path.join(MM, "armor", f"{base_set}_{piece}.json"), encoding="utf-8"))
    tex = m["texture"].split("/")[-1]  # <세트>_1.png
    new_tex = tex.replace(base_set, item["set"], 1)
    recolor(Image.open(os.path.join(MM, "textures", "entity", "armor", tex)), pal).save(os.path.join(SRC, new_tex))
    m["texture"] = new_tex
    write_json(os.path.join(SRC, f"{item['model']}.model.json"), m, indent=1)


def make_samples(defs, force=False):
    """임시 견본 원본 만들기 — 원본이 이미 있으면 (바꿔 넣은 디자인) 그대로 둠, force 면 모두 다시"""
    os.makedirs(SRC, exist_ok=True)
    made = []
    for name, fn in SAMPLE_DESIGNS.items():
        if force or not os.path.exists(os.path.join(SRC, f"{name}.geo.json")):
            fn().save(SRC)
            made.append(name)
    for iid, it in defs["items"].items():
        s = it.get("sample")
        if not s:
            continue
        if it["slot"] == "weapon":
            if force or not os.path.exists(os.path.join(WEAPON_ART, f"{it['design']}.geo.json")):
                sample_weapon(iid, it)
                made.append(it["design"])
        elif force or not any(os.path.exists(os.path.join(SRC, f"{it['model']}{ext}")) for ext in (".geo.json", ".model.json")):
            sample_armor(iid, it)
            made.append(it["model"])
    print(f"samples: {len(made)} made" + (f" ({', '.join(made)})" if made else " (all sources exist — --force to remake)"))


# ------------------------------------------------------------ 게임 자원

# 사람 모델 (PlayerModel) 의 뼈 이름 — 장식 모델의 아래 뼈가 같은 이름이면 그리기 (TransformCopyingModel) 가 그 뼈의 자리 · 회전으로 바꿔 버림
RESERVED_BONES = {"hat", "jacket", "left_sleeve", "right_sleeve", "left_pants", "right_pants", "ear", "left_ear", "right_ear", "cloak"}


def check_bones(model, m):
    def walk(part, path):
        for name, child in part.get("children", {}).items():
            if name in RESERVED_BONES:
                raise SystemExit(f"{model}: 뼈 이름 '{name}' ({'/'.join(path + [name])}) 은 사람 모델의 뼈 이름이라 쓸 수 없음 — 다른 이름으로 "
                                 f"(쓰지 않는 이름: {', '.join(sorted(RESERVED_BONES))})")
            walk(child, path + [name])
    for name, part in m["parts"].items():
        walk(part, [name])


def load_model(model):
    """원본 → (자바 조각 JSON, 텍스처 그림, 빛 그림 또는 None)"""
    geo = os.path.join(SRC, f"{model}.geo.json")
    if os.path.exists(geo):
        m = armor_items.convert_geo(geo)
        tex = Image.open(os.path.join(SRC, f"{model}.png")).convert("RGBA")
        gp = os.path.join(SRC, f"{model}.glow.png")
        glow = Image.open(gp).convert("RGBA") if os.path.exists(gp) else None
        return m, tex, glow, f"{model}.png"
    mj = os.path.join(SRC, f"{model}.model.json")
    if os.path.exists(mj):
        m = json.load(open(mj, encoding="utf-8"))
        tname = m.pop("texture")
        tex = Image.open(os.path.join(SRC, tname)).convert("RGBA")
        return m, tex, None, tname
    raise SystemExit(f"장식 모델 {model} 의 원본이 없음 (tools/cosmetic_art/{model}.geo.json 또는 .model.json)")


def outline(img, color=(20, 14, 24, 255)):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3] < 40 and any(0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] >= 40
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), color)
    out.alpha_composite(img)
    return out


def fit_icon(img, pad=2):
    """그린 것의 보이는 부분을 32×32 가운데에"""
    bb = img.getbbox()
    if bb is None:
        return Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    crop = img.crop(bb)
    k = min((ICON - 2 * pad) / crop.size[0], (ICON - 2 * pad) / crop.size[1])
    size = (max(1, round(crop.size[0] * k)), max(1, round(crop.size[1] * k)))
    crop = crop.resize(size, Image.LANCZOS)
    # 반투명 가장자리를 또렷하게
    a = np.array(crop)
    a[..., 3] = np.where(a[..., 3] >= 110, 255, 0)
    crop = Image.fromarray(a, "RGBA")
    out = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    out.alpha_composite(crop, ((ICON - size[0]) // 2, (ICON - size[1]) // 2))
    return outline(out)


def model_icon(model_json, tex, glow, yaw, pitch):
    mesh = cv.Mesh()
    mesh.add_model(model_json, tex, glow_img=glow)
    lo = np.min([q[0].min(0) for q in mesh.quads], axis=0)
    hi = np.max([q[0].max(0) for q in mesh.quads], axis=0)
    center = (lo + hi) / 2
    img = cv.render(mesh, (128, 128), 128 / (np.max(hi - lo) * 1.25), center, yaw=yaw, pitch=pitch, ss=2)
    return fit_icon(img)


def aura_icon(colors):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cols = [tuple(int(c * 255) for c in col) for col in colors]
    # 빛 고리 + 반짝임 넷
    for r, a in ((52, 70), (44, 120), (38, 200)):
        d.ellipse((64 - r, 64 - r * 0.45 + 18, 64 + r, 64 + r * 0.45 + 18), outline=cols[0] + (a,), width=6)
    for i, (x, y, s) in enumerate(((40, 34, 14), (86, 28, 18), (64, 60, 22), (30, 70, 10), (96, 66, 12))):
        c = cols[i % len(cols)]
        d.polygon([(x, y - s), (x + s * 0.3, y - s * 0.3), (x + s, y), (x + s * 0.3, y + s * 0.3), (x, y + s), (x - s * 0.3, y + s * 0.3),
                   (x - s, y), (x - s * 0.3, y - s * 0.3)], fill=c + (255,))
    return fit_icon(img, pad=1)


def title_icon(color):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = tuple(int(x * 255) for x in color)
    dark = tuple(int(x * 0.55) for x in c)
    # 두루마리: 가운데 띠 + 양쪽 말린 끝
    d.rectangle((6, 10, 25, 21), fill=(246, 236, 210, 255), outline=(120, 90, 50, 255))
    d.rectangle((3, 9, 7, 22), fill=c + (255,), outline=dark + (255,))
    d.rectangle((24, 9, 28, 22), fill=c + (255,), outline=dark + (255,))
    for x in range(9, 23, 2):
        d.point((x, 14), fill=(150, 120, 80, 255))
        d.point((x + 1, 17), fill=(150, 120, 80, 255))
    return outline(img)


def weapon_icon(design):
    import weapon_pack_import as wpi  # noqa: E402 (모델 → 아이콘: 무기 아이콘과 같은 방향)
    p = os.path.join(WEAPON_ART, f"{design}.java.json")
    if os.path.exists(p):
        return wpi.icon_of(design)
    ip = os.path.join(WEAPON_ART, f"{design}.icon.png")
    if os.path.exists(ip):
        return Image.open(ip).convert("RGBA")
    raise SystemExit(f"무기 스킨 디자인 {design} 의 아이콘을 그릴 수 없음 (.java.json 도 .icon.png 도 없음)")


ICON_VIEW = {"helmet": (-28, 12), "chest": (-24, 6), "legs": (-24, 6), "boots": (-24, 10), "head": (-28, 14), "back": (150, 8)}


def build(defs):
    out_models = os.path.join(MM, "cosmetic")
    out_tex = os.path.join(MM, "textures", "entity", "cosmetic")
    out_icons = os.path.join(RPG, "textures", "item", "cosmetic")
    out_icon_models = os.path.join(RPG, "models", "item", "cosmetic")
    for d in (out_models, out_tex, out_icons, out_icon_models):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    for f in os.listdir(os.path.join(RPG, "items")):
        if f.startswith("cosmetic_"):
            os.remove(os.path.join(RPG, "items", f))
    models_done = {}
    n = 0
    for iid, it in defs["items"].items():
        slot = it["slot"]
        icon_src = os.path.join(SRC, f"{iid}.icon.png")
        icon = Image.open(icon_src).convert("RGBA") if os.path.exists(icon_src) else None
        if slot == "weapon":
            design = it["design"]
            if not os.path.exists(os.path.join(WEAPON_ART, f"{design}.geo.json")):
                raise SystemExit(f"{iid}: 무기 디자인 tools/weapon_art/{design}.geo.json 이 없음 (--samples 또는 디자인 넣기)")
            if icon is None:
                icon = weapon_icon(design)
            item_def = {"model": {
                "type": "minecraft:select", "property": "minecraft:display_context",
                "cases": [{"when": ["gui", "ground", "fixed", "head", "on_shelf"], "model": {"type": "minecraft:model", "model": f"rpg:item/cosmetic/{iid}"}}],
                "fallback": {"type": "minecraft:model", "model": f"rpg:item/model/{design}"}}}
        else:
            model = it.get("model")
            if model:
                if model not in models_done:
                    m, tex, glow, tname = load_model(model)
                    check_bones(model, m)
                    tpath = f"mmorpg:textures/entity/cosmetic/{tname}"
                    m = {"tw": m["tw"], "th": m["th"], "texture": tpath, **({"emissive": f"mmorpg:textures/entity/cosmetic/{model}.glow.png"} if glow else {}),
                         "parts": m["parts"]}
                    write_json(os.path.join(out_models, f"{model}.json"), m, indent=1)
                    tex.save(os.path.join(out_tex, tname))
                    if glow is not None:
                        glow.save(os.path.join(out_tex, f"{model}.glow.png"))
                    models_done[model] = (m, tex, glow)
                m, tex, glow = models_done[model]
                if icon is None:
                    yaw, pitch = ICON_VIEW.get(slot, (-28, 10))
                    icon = model_icon(m, tex, glow, yaw, pitch)
            elif slot == "aura":
                if icon is None:
                    icon = aura_icon(it["aura"]["colors"])
            elif slot == "title":
                if icon is None:
                    icon = title_icon(it.get("color", [1, 1, 1]))
            else:
                raise SystemExit(f"{iid}: {slot} 칸에는 model 이 있어야 함")
            item_def = {"model": {"type": "minecraft:model", "model": f"rpg:item/cosmetic/{iid}"}}
        icon.save(os.path.join(out_icons, f"{iid}.png"))
        write_json(os.path.join(out_icon_models, f"{iid}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/cosmetic/{iid}"}})
        write_json(os.path.join(RPG, "items", f"cosmetic_{iid}.json"), item_def)
        n += 1
    print(f"cosmetics: {n} items, {len(models_done)} models → assets/mmorpg/cosmetic · assets/rpg/items/cosmetic_*")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", action="store_true", help="없는 임시 견본 원본을 만듦")
    ap.add_argument("--force", action="store_true", help="--samples 와 함께: 있는 원본도 모두 다시")
    a = ap.parse_args()
    defs = json.load(open(COSMETICS, encoding="utf-8"))
    if a.samples:
        make_samples(defs, force=a.force)
    build(defs)


if __name__ == "__main__":
    main()
