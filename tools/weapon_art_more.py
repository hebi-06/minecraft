#!/usr/bin/env python3
"""12단계 새 무기 4종 (창 · 낫 · 전투 망치 · 성스러운 홀) × 일반 · 희귀 · 고귀 · 영웅 · 초월 = 임시 디자인 20개 + 아이콘 + 아이템 그림 연결.

  python3 tools/weapon_art_more.py            # 없는 디자인만 그림 (클로드 디자인 파일로 바꿔 넣은 것은 그대로 둠)
  python3 tools/weapon_art_more.py --force    # 임시 디자인을 다시 그림 (바꿔 넣은 파일도 덮어씀 — 조심)
  python3 tools/weapon_models.py              # 그다음 자바 3D 모델 · 아이템 정의

만들어지는 것
- tools/weapon_art/<디자인>.geo.json · .png (16×16 팔레트, 알파 102 = 빛나는 칸) — 기존 무기 디자인과 같은 형식
  (베드락 Attachable 뼈대: 무기 뼈 weapon 이 rightItem 아래 pivot (-6, 13, 0) · 회전 60°, 손잡이 = y 13, 날 · 때리는 면 = -z 쪽, fx 위치)
- tools/weapon_art/<디자인>.icon.png   32×32 인벤토리 아이콘 (없을 때만 — 모델 옆모습, 끝이 왼쪽 위: 애드온 generator/tools/geo_icon.py 와 같은 방법)
- tools/weapon_art/designs.json         designs (1인칭 자세 fp · 빛 알갱이 fx) · weapons (무기 ID → 디자인) 에 새 무기를 더함
- src/main/resources/assets/rpg/textures/item/<종류>/<무기 ID>.png · models/item/<무기 ID>.json · items/<무기 ID>.json (아이콘 — weapon_models.py 가
  손에 든 모습을 3D 모델로 다시 씀)

디자인 이름: <종류>_<등급>_lv0 (lance · scythe · hammer · scepter × rare · noble · hero · transcend), 일반은 <종류>_lv0 (애드온과 같은 규칙). 클로드 디자인에서 받은 파일로 바꾸려면 같은 이름으로
.geo.json · .png (애드온 .entity.png 를 이름만 바꿔서) · .icon.png 를 넣고 이 스크립트 (아이콘 · 연결) → weapon_models.py → tools/java_fx_art.py
(스킬 그림 — 던지는 창 · 망치 등은 모델에서 그림) → tools/addon_particles.py 순서로 돌림.
"""
import argparse
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")

CX = -6.0  # 무기 뼈 가운데 x (손)
GLOW_ALPHA = 102
TYPES = ["lance", "scythe", "hammer", "scepter"]
BASIC_LEVELS = [0, 5, 10, 15, 20, 30, 40]
GRADES = {"common": BASIC_LEVELS, "rare": BASIC_LEVELS, "noble": [10, 20], "hero": [30, 35, 40], "transcend": [40]}
# 1인칭 자세: 칼 · 도끼 · 궁니르와 같은 손잡이 자리 (애드온 FP — 긴 무기는 1인칭에서만 줄임)
FP = {"rotation": [64.9, -2.4, 3.0], "position": [3.14, 3.87, 0.89]}
FP_SCALE = {"lance": 0.68, "scythe": 0.72, "hammer": 0.9, "scepter": 0.85}
# 머리가 큰 디자인은 1인칭에서 화면 오른쪽을 덜 가리게 작게 (사진첩 weapons12 로 봄)
FP_SCALE_DESIGN = {"hammer_hero_lv0": 0.8, "hammer_transcend_lv0": 0.74}




def design_of(t, g):
    """디자인 이름: 일반은 <종류>_lv0, 그 밖은 <종류>_<등급>_lv0 (애드온 무기 디자인과 같은 규칙 — sword_lv0 · sword_rare_lv0)"""
    return f"{t}_lv0" if g == "common" else f"{t}_{g}_lv0"


# ------------------------------------------------------------ 모델 만들기

class Model:
    def __init__(self, name):
        self.name = name
        self.cubes = []
        self.palette = []  # (r, g, b, glow)
        self.fx = [CX, 30, 0]

    def col(self, rgb, glow=False):
        key = (int(rgb[0]), int(rgb[1]), int(rgb[2]), bool(glow))
        if key not in self.palette:
            assert len(self.palette) < 256, self.name
            self.palette.append(key)
        return self.palette.index(key)

    def box(self, x0, x1, y0, y1, z0, z1, c):
        """축에 맞춘 상자 (양 끝 좌표) — c = 팔레트 번호"""
        x0, x1 = min(x0, x1), max(x0, x1)
        y0, y1 = min(y0, y1), max(y0, y1)
        z0, z1 = min(z0, z1), max(z0, z1)
        if x1 - x0 < 1e-6 or y1 - y0 < 1e-6 or z1 - z0 < 1e-6:
            return
        self.cubes.append(([round(x0, 4), round(y0, 4), round(z0, 4)], [round(x1 - x0, 4), round(y1 - y0, 4), round(z1 - z0, 4)], c))

    def rod(self, y0, y1, t, c, cz=0.0, tz=None, cx=CX):
        """세로 막대 (x 폭 t, z 폭 tz — 없으면 t) 가운데 (cx, cz)"""
        tz = t if tz is None else tz
        self.box(cx - t / 2, cx + t / 2, y0, y1, cz - tz / 2, cz + tz / 2, c)

    def plate(self, y0, y1, z0, z1, t, c, cx=CX):
        """날 · 깃발처럼 x 로 얇은 판 (두께 t)"""
        self.box(cx - t / 2, cx + t / 2, y0, y1, z0, z1, c)

    def disk(self, cy, cz, r, t, c, step=1.0, cx=CX):
        """y-z 평면의 원판 (옆에서 보이는 원 — 두께 t): 줄마다 상자"""
        y = -r
        while y < r - 1e-9:
            y1 = min(r, y + step)
            m = (y + y1) / 2
            h = math.sqrt(max(0.0, r * r - m * m))
            if h > 0.15:
                self.plate(cy + y, cy + y1, cz - h, cz + h, t, c, cx)
            y = y1

    def ball(self, cy, r, c, step=1.0, cx=CX, cz=0.0):
        """공 (층마다 정사각 상자 — x · z 폭이 같음)"""
        y = -r
        while y < r - 1e-9:
            y1 = min(r, y + step)
            m = (y + y1) / 2
            h = math.sqrt(max(0.0, r * r - m * m)) * 0.92
            if h > 0.2:
                self.box(cx - h, cx + h, cy + y, cy + y1, cz - h, cz + h, c)
            y = y1

    # ------------------------------------------------------------ 내보내기

    def geo(self, with_fx=True):
        uv_of = {}
        cubes = []
        for origin, size, c in self.cubes:
            u, v = c % 16, c // 16
            face = {"uv": [u, v], "uv_size": [1, 1]}
            cubes.append({"origin": origin, "size": size, "uv": {k: dict(face) for k in ("north", "east", "south", "west", "up", "down")}})
            uv_of[c] = (u, v)
        bones = [
            {"name": "root", "pivot": [0, 0, 0]},
            {"name": "waist", "parent": "root", "pivot": [0, 12, 0]},
            {"name": "body", "parent": "waist", "pivot": [0, 24, 0]},
            {"name": "head", "parent": "body", "pivot": [0, 24, 0]},
            {"name": "leftArm", "parent": "body", "pivot": [5, 22, 0]},
            {"name": "leftItem", "parent": "leftArm", "pivot": [6, 15, 1]},
            {"name": "rightArm", "parent": "body", "pivot": [-5, 22, 0]},
            {"name": "rightItem", "parent": "rightArm", "pivot": [-6, 15, 1]},
            {"name": "leftLeg", "parent": "root", "pivot": [1.9, 12, 0]},
            {"name": "rightLeg", "parent": "root", "pivot": [-1.9, 12, 0]},
            {"name": "weapon", "parent": "rightItem", "pivot": [-6, 13, 0], "rotation": [60, 0, 0], "cubes": cubes},
        ]
        if with_fx:  # 빛 알갱이 자리 (영웅 · 초월만 — 고귀는 애드온처럼 없음)
            bones[-1]["locators"] = {"fx": [round(x, 3) for x in self.fx]}
        return {"format_version": "1.16.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.rpg.art.{self.name}", "texture_width": 16, "texture_height": 16, "visible_bounds_width": 6,
                            "visible_bounds_height": 6, "visible_bounds_offset": [0, 1, 0]},
            "bones": bones}]}

    def texture(self):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for i, (r, g, b, glow) in enumerate(self.palette):
            img.putpixel((i % 16, i // 16), (r, g, b, GLOW_ALPHA if glow else 255))
        return img


def segments(m, segs, body, edge=None, spine=None, t=1.0, edge_t=1.2):
    """초승달 · 갈고리 날: segs = [(z0, z1, y0, y1), ...] — 몸통 body, 아래 가장자리 1칸 edge (날), 위 가장자리 1칸 spine"""
    for z0, z1, y0, y1 in segs:
        m.plate(y0, y1, z0, z1, t, body)
        if edge is not None:
            m.plate(y0, y0 + 0.8, z0, z1, edge_t, edge)
        if spine is not None:
            m.plate(y1 - 0.7, y1, z0, z1, t + 0.1, spine)


# ------------------------------------------------------------ 창

def lance_noble():
    m = Model("lance_noble_lv0")
    ivory, ivory_d = m.col((232, 224, 200)), m.col((196, 184, 156))
    gold, gold_l, gold_d = m.col((232, 184, 64)), m.col((255, 228, 128)), m.col((172, 122, 36))
    red, red_d = m.col((178, 38, 42)), m.col((118, 22, 28))
    gem = m.col((255, 90, 80), True)
    m.rod(-8, -6, 1, gold_d)
    m.rod(-6, -4, 2.5, gold)
    m.rod(-4, 42, 1.5, ivory)
    for y in (2, 24, 31, 38):
        m.rod(y, y + 1, 2, gold)
    m.rod(8, 18, 2.1, red)
    for y in (9.5, 12, 14.5, 17):
        m.rod(y, y + 0.5, 2.2, red_d)
    m.rod(40, 42, 3, gold)
    m.rod(40.5, 41.5, 1, gem, tz=3.4)
    # 잎 모양 날 (x 두께 1) + 가운데 등줄
    for y0, y1, h in ((42, 44, 1.5), (44, 47, 2.5), (47, 51, 2.6), (51, 54, 2.0), (54, 56, 1.5), (56, 58, 1.0), (58, 59.5, 0.5)):
        m.plate(y0, y1, -h, h, 1.0, gold_l)
    m.plate(43, 57, -0.5, 0.5, 1.4, gold)
    # 깃발 (뒤 +z 쪽)
    m.plate(31.5, 38, 1, 7, 0.5, red)
    m.plate(31.5, 32.3, 1, 7, 0.6, gold_d)
    m.plate(37.2, 38, 1, 7, 0.6, gold_d)
    m.plate(33.6, 35.9, 3.2, 5.4, 0.7, gold)
    m.plate(28.5, 31.5, 4.5, 7, 0.5, red_d)
    m.fx = [CX, 50, 0]
    return m


def lance_hero():
    m = Model("lance_hero_lv0")
    dark, mid = m.col((58, 24, 28)), m.col((92, 38, 42))
    steel, steel_d, barb = m.col((150, 30, 36)), m.col((92, 18, 24)), m.col((205, 44, 52))
    bone = m.col((232, 222, 204))
    rune = m.col((255, 60, 60), True)
    core = m.col((255, 140, 120), True)
    m.rod(-7, -4, 1, bone)
    m.rod(-4.5, -3, 2, steel_d)
    m.rod(-3, 42, 1.5, dark)
    for y in (0, 24, 31, 37):
        m.rod(y, y + 1.2, 1.7, rune)
    m.rod(8, 18, 2.1, mid)
    for y in (9, 11.5, 14, 16.5):
        m.rod(y, y + 0.6, 2.2, dark)
    m.rod(40, 42, 2.8, barb)
    m.rod(42, 45, 2.5, steel_d)
    # 가운데 날 + 붉게 빛나는 심지
    m.plate(45, 60, -1.1, 1.1, 1.0, steel)
    m.plate(46, 58, -0.35, 0.35, 1.25, core)
    m.plate(60, 62.5, -0.5, 0.5, 0.9, bone)
    # 거꾸로 난 가시 (양쪽 — 날에서 비스듬히 아래로 꺾인 갈고리, 꽂히면 빠지지 않는 창)
    for y in (48, 52.5, 57):
        for s in (-1, 1):
            m.plate(y, y + 1.0, s * 1.1, s * 2.1, 0.9, barb)
            m.plate(y - 1.0, y + 0.2, s * 2.0, s * 2.9, 0.9, barb)
            m.plate(y - 2.0, y - 0.8, s * 2.8, s * 3.5, 0.9, barb)
            m.plate(y - 2.8, y - 1.8, s * 3.3, s * 3.8, 0.85, steel)
    # 붉은 술
    m.plate(36, 40, -2.2, -1.4, 0.6, barb)
    m.plate(35, 40, 1.4, 2.2, 0.6, barb)
    m.fx = [CX, 52, 0]
    return m


def lance_transcend():
    m = Model("lance_transcend_lv0")
    white, gold, gold_d, blade = m.col((240, 240, 250)), m.col((240, 196, 70)), m.col((186, 136, 40)), m.col((255, 222, 112))
    light = m.col((255, 248, 204), True)
    cyan = m.col((150, 240, 255), True)
    m.rod(-9, -6, 1, gold)
    m.rod(-6, -4, 2.5, gold_d)
    m.rod(-4, 42, 1.5, white)
    for y in (4, 22, 32):
        m.rod(y, y + 1, 2, gold)
    for y0 in (24, 34):
        m.rod(y0, y0 + 4, 1.6, cyan)
    m.rod(8, 18, 2.1, gold_d)
    for y in (9.5, 12, 14.5, 17):
        m.rod(y, y + 0.5, 2.2, white)
    m.rod(42, 45, 3, gold)
    # 빛 고리 (목 둘레 — 수평 네모 고리)
    m.box(CX - 4, CX + 4, 39, 40, 3.5, 4.5, light)
    m.box(CX - 4, CX + 4, 39, 40, -4.5, -3.5, light)
    m.box(CX - 4.5, CX - 3.5, 39, 40, -4.5, 4.5, light)
    m.box(CX + 3.5, CX + 4.5, 39, 40, -4.5, 4.5, light)
    # 다섯 갈래 창날: 가로대 + 가운데 긴 날 + 안쪽 둘 + 바깥 둘
    m.plate(45, 47, -7.5, 7.5, 1.5, gold)
    for y0, y1, h in ((47, 58, 1.5), (58, 63, 1.0), (63, 66, 0.5)):
        m.plate(y0, y1, -h, h, 1.0, blade)
    m.plate(48, 63, -0.5, 0.5, 1.3, light)
    for s in (-1, 1):
        m.plate(47, 58, s * 3.0, s * 4.0, 1.0, blade)
        m.plate(58, 60.5, s * 3.2, s * 3.8, 1.1, light)
        m.plate(47, 49, s * 6.0, s * 7.5, 1.0, blade)
        m.plate(49, 54, s * 6.0, s * 7.0, 1.0, blade)
        m.plate(54, 56.5, s * 6.2, s * 6.8, 1.1, light)
    m.fx = [CX, 56, 0]
    return m


# ------------------------------------------------------------ 낫

def scythe_noble():
    m = Model("scythe_noble_lv0")
    black, black_d = m.col((40, 36, 44)), m.col((24, 22, 28))
    gold, gold_d, blade, edge = m.col((236, 190, 70)), m.col((178, 128, 40)), m.col((255, 226, 130)), m.col((255, 250, 232))
    moon = m.col((255, 240, 170), True)
    m.rod(-8, -6, 1, gold_d)
    m.rod(-6, -3, 2.5, gold)
    m.rod(-3, 46, 1.5, black)
    for y in (6, 22, 33, 43):
        m.rod(y, y + 1, 2, gold)
    m.rod(8, 18, 2.1, black_d)
    m.rod(44, 48.5, 3, gold)
    m.rod(45.5, 46.7, 3.2, moon)
    segments(m, [(-1.5, -5, 43, 48), (-5, -9, 43.5, 47.5), (-9, -12.5, 43, 47), (-12.5, -15.5, 42, 46), (-15.5, -18, 40.5, 44.5), (-18, -20, 38.5, 42.5),
                 (-20, -21.5, 36.5, 40.5), (-21.5, -22.5, 35, 38)], blade, edge, gold_d)
    m.plate(45, 47, 1.5, 4.5, 1.0, gold)
    m.plate(45.5, 46.5, 4.5, 5.5, 0.9, gold_d)
    m.fx = [CX, 44, -10]
    return m


def scythe_hero():
    m = Model("scythe_hero_lv0")
    shaft, wrap = m.col((52, 48, 70)), m.col((92, 72, 122))
    silver, silver_d = m.col((200, 206, 222)), m.col((128, 134, 156))
    edge = m.col((214, 192, 255), True)
    stone = m.col((176, 222, 255), True)
    m.rod(-4, -2, 2.2, silver_d)
    m.rod(-2, 36, 1.5, shaft)
    for y in (4, 24, 30):
        m.rod(y, y + 1, 2, silver)
    m.rod(8, 18, 2.1, wrap)
    m.rod(34, 38, 3, silver_d)
    m.rod(35.4, 36.6, 3.3, stone)
    # 하르페: 곧게 오른 날이 앞 (-z) 으로 꺾여 내려오는 갈고리
    m.plate(38, 47, -1.2, 1.2, 1.0, silver)
    m.plate(44, 48, -6.2, -1.2, 1.0, silver)
    m.plate(39, 46, -8.6, -6.2, 1.0, silver)
    m.plate(37, 41, -9.6, -8.6, 0.9, silver)
    m.plate(44, 44.8, -6.2, -1.2, 1.2, edge)
    m.plate(39.5, 44, -6.8, -6.2, 1.2, edge)
    m.plate(38, 46.5, 1.2, 1.7, 1.1, silver_d)
    m.plate(42, 43, 1.7, 3.2, 0.9, silver_d)
    m.fx = [CX, 44, -4]
    return m


def scythe_transcend():
    m = Model("scythe_transcend_lv0")
    night, night_d = m.col((30, 42, 84)), m.col((18, 24, 54))
    gold, gold_d = m.col((240, 196, 80)), m.col((178, 128, 48))
    face, hand = m.col((250, 242, 214)), m.col((44, 32, 22))
    spectral, light = m.col((110, 226, 255), True), m.col((214, 250, 255), True)
    sand = m.col((255, 210, 120), True)
    # 모래시계 손잡이 끝
    m.rod(-10, -9, 3, gold)
    m.rod(-9, -7, 2, sand)
    m.rod(-7, -6.5, 1, gold_d)
    m.rod(-6.5, -4.5, 2, sand)
    m.rod(-4.5, -4, 3, gold)
    m.rod(-4, 48, 1.6, night)
    for y in (4, 22, 34, 42):
        m.rod(y, y + 1, 2.1, gold)
    m.rod(8, 18, 2.1, night_d)
    for y in (9.5, 12.5, 15.5):
        m.rod(y, y + 0.6, 2.2, gold_d)
    # 시계판 (옆에서 보이는 원) + 금테 + 바늘
    m.disk(52, 0, 5.2, 2.2, gold)
    m.disk(52, 0, 4.3, 2.4, face)
    m.plate(52, 55.6, -0.35, 0.35, 2.7, hand)
    m.plate(51.65, 52.35, -3.6, 0, 2.7, hand)
    m.rod(51.5, 52.5, 1, gold, tz=1)
    m.rod(56.5, 60.5, 1, gold)
    # 거대한 날 (앞 -z) — 푸른 유령빛 몸통 · 흰빛 날 · 금빛 등
    segments(m, [(-4, -8, 52, 58), (-8, -12, 52.5, 57.5), (-12, -16, 52, 56.5), (-16, -19.5, 50.5, 55), (-19.5, -22.5, 48.5, 53), (-22.5, -25, 46, 50.5),
                 (-25, -26.5, 43.5, 47.5), (-26.5, -27.5, 42, 45)], spectral, light, gold)
    # 뒤쪽 작은 날
    m.plate(53, 56, 4.5, 8, 1.0, gold)
    m.plate(52, 54.5, 8, 10.5, 1.0, gold_d)
    m.fx = [CX, 52, -12]
    return m


# ------------------------------------------------------------ 전투 망치

def hammer_noble():
    m = Model("hammer_noble_lv0")
    wood, wood_d, leather = m.col((122, 82, 46)), m.col((84, 54, 28)), m.col((92, 42, 32))
    gold, gold_d, gold_l, silver = m.col((236, 190, 70)), m.col((178, 128, 40)), m.col((255, 228, 132)), m.col((212, 216, 224))
    gem = m.col((120, 210, 255), True)
    m.rod(0, 2, 1, gold_d)
    m.rod(2, 4, 2.5, gold)
    m.rod(4, 29, 1.8, wood)
    m.rod(8, 18, 2.2, leather)
    for y in (9.5, 12.5, 15.5):
        m.rod(y, y + 0.5, 2.3, wood_d)
    for y in (20, 25):
        m.rod(y, y + 1, 2.4, gold)
    m.rod(27, 29, 3, gold_d)
    m.box(CX - 3.5, CX + 3.5, 29, 37, -4.5, 4.5, gold)
    m.box(CX - 3, CX + 3, 29.5, 36.5, -6, -4.5, gold_l)
    m.box(CX - 3, CX + 3, 29.5, 36.5, 4.5, 6, gold_l)
    for y in (31, 34):
        m.box(CX - 3.6, CX + 3.6, y, y + 1, -4.6, 4.6, silver)
    m.box(CX - 3.7, CX + 3.7, 32.5, 33.5, -1, 1, gem)
    m.rod(37, 40, 1.5, gold_l)
    m.fx = [CX, 33, 0]
    return m


def hammer_hero():
    m = Model("hammer_hero_lv0")
    wood, wood_d, bark = m.col((112, 80, 50)), m.col((70, 48, 30)), m.col((92, 64, 40))
    bronze, bronze_d = m.col((192, 132, 62)), m.col((140, 92, 40))
    life, death = m.col((110, 255, 120), True), m.col((176, 96, 255), True)
    moss = m.col((72, 122, 52))
    m.rod(0, 2, 2.6, bronze_d)
    m.rod(2, 23, 2, wood)
    m.rod(8, 18, 2.5, bark)
    for y in (9.5, 13, 16.5):
        m.rod(y, y + 0.6, 2.6, wood_d)
    m.rod(20, 22, 3, bronze)
    for y0, y1, w, d, c in ((22, 26, 4, 5, wood_d), (26, 34, 6, 8, wood), (34, 40, 6.5, 9, wood), (40, 42.5, 5, 7, wood_d)):
        m.box(CX - w / 2, CX + w / 2, y0, y1, -d / 2, d / 2, c)
    for y, w, d in ((30, 6.6, 8.6), (37, 7.1, 9.6)):
        m.box(CX - w / 2, CX + w / 2, y, y + 1, -d / 2, d / 2, bronze)
    # 앞 (-z) 생명의 문양 · 뒤 (+z) 죽음의 문양
    m.box(CX - 0.6, CX + 0.6, 27, 36, -4.75, -4.4, life)
    for y in (28.5, 31.5, 34.5):
        m.box(CX - 2, CX + 2, y, y + 0.7, -4.75, -4.4, life)
    m.box(CX - 0.6, CX + 0.6, 27, 36, 4.4, 4.75, death)
    for y in (29, 33):
        m.box(CX - 2.2, CX + 2.2, y, y + 0.7, 4.4, 4.75, death)
    for y, z in ((28, -2.5), (28, 2.5), (35.5, -3), (35.5, 3), (32, 0)):
        m.box(CX - 3.4, CX + 3.4, y, y + 1.2, z - 0.6, z + 0.6, bronze_d)
    m.box(CX - 2.8, CX + 2.8, 42.5, 44, -2, 2, bronze)
    m.box(CX - 3.2, CX - 2.6, 35, 39, -3, 1, moss)
    m.box(CX + 2.6, CX + 3.2, 26, 29, 0, 3.5, moss)
    m.fx = [CX, 34, 0]
    return m


def hammer_transcend():
    m = Model("hammer_transcend_lv0")
    steel, steel_d, steel_l = m.col((176, 182, 198)), m.col((108, 114, 132)), m.col((222, 228, 238))
    leather, leather_d, gold = m.col((94, 58, 38)), m.col((60, 36, 24)), m.col((236, 190, 70))
    rune, spark = m.col((150, 210, 255), True), m.col((226, 242, 255), True)
    # 손목 끈 고리
    m.plate(0, 0.8, -2, 2, 0.8, leather_d)
    m.plate(0, 4, -2, -1.2, 0.8, leather_d)
    m.plate(0, 4, 1.2, 2, 0.8, leather_d)
    m.rod(4, 6, 2.6, gold)
    m.rod(6, 20, 2.0, leather)
    for y in (7, 9.5, 12, 14.5, 17):
        m.rod(y, y + 0.7, 2.2, leather_d)
    m.rod(19.5, 22, 3, gold)
    # 머리: 큰 쇠 덩이 (앞뒤로 긴 — 때리는 면 ±z)
    m.box(CX - 4, CX + 4, 22, 34, -8, 8, steel)
    m.box(CX - 3.6, CX + 3.6, 21.6, 22, -7.6, 7.6, steel_d)
    m.box(CX - 3.6, CX + 3.6, 34, 34.4, -7.6, 7.6, steel_d)
    m.box(CX - 3.4, CX + 3.4, 22.6, 33.4, -8.5, -8, steel_l)
    m.box(CX - 3.4, CX + 3.4, 22.6, 33.4, 8, 8.5, steel_l)
    # 옆면의 매듭 룬 (빛남): 가운데 마름모 + 가로줄
    for x0, x1 in ((CX - 4.4, CX - 4), (CX + 4, CX + 4.4)):
        m.box(x0, x1, 27, 29, -1, 1, rune)
        m.box(x0, x1, 26, 27, -0.5, 0.5, rune)
        m.box(x0, x1, 29, 30, -0.5, 0.5, rune)
        m.box(x0, x1, 27.6, 28.4, -6, -2, rune)
        m.box(x0, x1, 27.6, 28.4, 2, 6, rune)
        m.box(x0, x1, 24, 25, -5, -4, rune)
        m.box(x0, x1, 31, 32, 4, 5, rune)
    m.box(CX - 4.2, CX - 3.8, 33.5, 34.6, -8.2, -6.5, spark)
    m.box(CX + 3.8, CX + 4.2, 21.4, 22.5, 6.5, 8.2, spark)
    m.fx = [CX, 28, 0]
    return m


# ------------------------------------------------------------ 성스러운 홀

def scepter_noble():
    m = Model("scepter_noble_lv0")
    gold, gold_d, gold_l, white = m.col((236, 190, 70)), m.col((178, 128, 40)), m.col((255, 228, 132)), m.col((240, 240, 250))
    orb, gem = m.col((255, 250, 222), True), m.col((120, 200, 255), True)
    m.rod(0, 2, 1, gold_d)
    m.rod(2, 4, 2.5, gold)
    m.rod(4, 33, 1.4, gold)
    m.rod(8, 18, 2.0, white)
    for y in (20, 26):
        m.rod(y, y + 1, 2.0, gold_d)
    m.rod(31, 34, 2.4, gold_d)
    # 날개 (앞뒤 ±z) 세 겹
    for y0, y1, h, c in ((30, 32, 4.0, gold), (32, 34, 5.5, white), (34, 36, 6.5, white)):
        m.plate(y0, y1, 1.4, h, 0.8, c)
        m.plate(y0, y1, -h, -1.4, 0.8, c)
    m.plate(35.4, 36, 1.4, 6.5, 0.9, gold_l)
    m.plate(35.4, 36, -6.5, -1.4, 0.9, gold_l)
    # 빛나는 구슬 + 금빛 살
    m.ball(37.5, 3.4, orb)
    for dx, dz in ((3.0, 0), (-3.0, 0), (0, 3.0), (0, -3.0)):
        m.box(CX + dx - 0.4, CX + dx + 0.4, 35, 40.5, dz - 0.4, dz + 0.4, gold)
    m.rod(40.5, 41.5, 2.5, gold_d)
    m.rod(41.5, 46.5, 1, gold_l)
    m.plate(43.3, 44.3, -1.6, 1.6, 1.0, gold_l)
    m.rod(43.5, 44.1, 1.2, gem, tz=1.2)
    m.fx = [CX, 38, 0]
    return m


def scepter_hero():
    m = Model("scepter_hero_lv0")
    gold, gold_d, white = m.col((236, 190, 70)), m.col((178, 128, 40)), m.col((246, 246, 250))
    snake_g, snake_s, wing_t = m.col((230, 176, 58)), m.col((198, 210, 232)), m.col((206, 214, 232))
    eye, orb = m.col((255, 84, 84), True), m.col((255, 240, 180), True)
    m.rod(0, 2, 2.2, gold_d)
    m.rod(2, 38, 1.4, gold)
    m.rod(8, 18, 2.0, white)
    # 두 뱀이 엇갈려 감고 오름 (금빛 · 은빛)
    y = 12.0
    a = 0.0
    while y < 34:
        for c, ph in ((snake_g, 0.0), (snake_s, math.pi)):
            x = CX + 1.9 * math.cos(a + ph)
            z = 1.9 * math.sin(a + ph)
            m.box(x - 0.65, x + 0.65, y, y + 1.6, z - 0.65, z + 0.65, c)
        y += 1.4
        a += math.radians(52)
    for s, c in ((-1, snake_g), (1, snake_s)):
        m.box(CX - 0.8, CX + 0.8, 34, 36, s * 1.0, s * 3.0, c)
        m.box(CX - 0.9, CX + 0.9, 35, 35.6, s * 1.4, s * 1.9, eye)
    # 날개
    for y0, y1, h, c in ((36, 38, 5.0, white), (38, 40, 6.5, white), (40, 41.5, 7.2, wing_t)):
        m.plate(y0, y1, 1.0, h, 0.8, c)
        m.plate(y0, y1, -h, -1.0, 0.8, c)
    m.ball(42.5, 1.8, orb, step=0.9)
    m.fx = [CX, 41, 0]
    return m


def scepter_transcend():
    m = Model("scepter_transcend_lv0")
    gold, gold_d, gold_l, red = m.col((240, 192, 70)), m.col((178, 126, 40)), m.col((255, 230, 140)), m.col((200, 62, 40))
    sun, core = m.col((255, 186, 60), True), m.col((255, 248, 204), True)
    laurel = m.col((92, 162, 72))
    m.rod(-2, 0, 1, gold_l)
    m.rod(0, 3, 3, gold)
    m.rod(3, 35, 1.5, gold)
    for y in (6, 21, 28):
        m.rod(y, y + 1, 2.1, red)
    m.rod(8, 18, 2.2, red)
    for y in (10, 13, 16):
        m.rod(y, y + 0.5, 2.3, gold_d)
    # 월계관
    for dx, dz in ((1.6, 0), (-1.6, 0), (0, 1.6), (0, -1.6)):
        m.box(CX + dx - 0.6, CX + dx + 0.6, 32, 35, dz - 0.6, dz + 0.6, laurel)
    m.rod(34.5, 36, 2.6, gold_d)
    # 해 원판 (옆에서 보이는 원): 금테 · 빛나는 해 · 흰 심지 + 빛살
    m.disk(42, 0, 6.0, 2.0, gold)
    m.disk(42, 0, 4.9, 2.2, sun)
    m.disk(42, 0, 2.4, 2.5, core)
    m.plate(48, 52, -0.6, 0.6, 1.4, gold_l)
    m.plate(52, 53.5, -0.3, 0.3, 1.2, sun)
    for s in (-1, 1):
        m.plate(41.4, 42.6, s * 6, s * 10, 1.4, gold_l)
        m.plate(41.7, 42.3, s * 10, s * 11.3, 1.2, sun)
        m.plate(46, 48, s * 4.2, s * 6.2, 1.2, gold_l)
        m.plate(48, 49.2, s * 5.6, s * 6.8, 1.0, sun)
        m.plate(36, 38, s * 4.2, s * 6.2, 1.2, gold_l)
    m.fx = [CX, 42, 0]
    return m



# ------------------------------------------------------------ 일반 · 희귀 (애드온 일반 · 희귀 무기와 같은 색: 철 + 나무 / 푸른 강철 + 푸른 장식, 빛나는 칸 없음)

IRON_D, IRON, IRON_L = (98, 103, 109), (168, 173, 179), (223, 226, 229)
WOOD, WOOD_D, WOOD_DD = (124, 84, 49), (92, 60, 37), (78, 53, 32)
BLUE_D, BLUE, STEEL, STEEL_L, GEM = (38, 56, 95), (52, 86, 143), (157, 180, 207), (215, 230, 246), (63, 140, 255)


def grip(m, wrap, band, y0=8, y1=18, t=2.1):
    """손잡이 감개 (y0 ~ y1) + 띠"""
    m.rod(y0, y1, t, wrap)
    for y in range(y0 + 2, y1, 3):
        m.rod(y, y + 0.5, t + 0.1, band)


def lance_common():
    m = Model("lance_lv0")
    iron_d, iron, iron_l = m.col(IRON_D), m.col(IRON), m.col(IRON_L)
    wood, wood_d, wood_dd = m.col(WOOD), m.col(WOOD_D), m.col(WOOD_DD)
    m.rod(-6, -4, 2, iron_d)
    m.rod(-4, 42, 1.5, wood)
    grip(m, wood_dd, wood_d)
    m.rod(30, 31, 1.8, wood_d)
    m.rod(40, 42, 2.4, iron_d)
    for y0, y1, h in ((42, 44, 1.2), (44, 48, 2.0), (48, 52, 1.8), (52, 55, 1.2), (55, 57, 0.7), (57, 58, 0.35)):
        m.plate(y0, y1, -h, h, 1.0, iron)
    m.plate(43, 56.5, -0.4, 0.4, 1.3, iron_l)
    m.fx = [CX, 50, 0]
    return m


def lance_rare():
    m = Model("lance_rare_lv0")
    blue_d, blue, steel, steel_l, gem = m.col(BLUE_D), m.col(BLUE), m.col(STEEL), m.col(STEEL_L), m.col(GEM)
    wood_dd = m.col(WOOD_DD)
    m.rod(-7, -5, 1, steel)
    m.rod(-5, -3, 2.3, blue)
    m.rod(-3, 42, 1.5, blue_d)
    grip(m, wood_dd, blue)
    for y in (24, 32):
        m.rod(y, y + 1, 1.9, steel)
    m.rod(40, 42.5, 2.6, steel)
    m.rod(40.8, 41.7, 1, gem, tz=2.9)
    # 날개 달린 창날 (밑동 양옆의 작은 날개 + 긴 잎날)
    for s in (-1, 1):
        m.plate(42.5, 44, s * 1.2, s * 3.2, 0.9, steel)
        m.plate(43, 44.6, s * 3.2, s * 3.9, 0.8, steel_l)
    for y0, y1, h in ((42.5, 45, 1.3), (45, 50, 2.1), (50, 54, 1.8), (54, 57, 1.2), (57, 59, 0.7), (59, 60, 0.35)):
        m.plate(y0, y1, -h, h, 1.0, steel)
    m.plate(43.5, 58.5, -0.45, 0.45, 1.3, steel_l)
    # 푸른 술
    m.plate(36, 40, -1.9, -1.2, 0.6, blue)
    m.plate(35.2, 40, 1.2, 1.9, 0.6, blue)
    m.fx = [CX, 52, 0]
    return m


def scythe_common():
    m = Model("scythe_lv0")
    iron_d, iron, iron_l = m.col(IRON_D), m.col(IRON), m.col(IRON_L)
    wood, wood_d, wood_dd = m.col(WOOD), m.col(WOOD_D), m.col(WOOD_DD)
    m.rod(-3, -1, 2, iron_d)
    m.rod(-1, 44, 1.6, wood)
    grip(m, wood_dd, wood_d)
    # 옆 손잡이 (+z)
    m.plate(26, 27.2, 0.8, 4.2, 1.2, wood_d)
    m.plate(25, 28.2, 4.2, 5.0, 1.3, wood_dd)
    m.rod(42, 46, 2.6, iron_d)
    segments(m, [(-1.3, -5, 42, 45.5), (-5, -9, 42.4, 45.2), (-9, -12.5, 42, 44.6), (-12.5, -15.5, 41, 43.8), (-15.5, -17.5, 39.6, 42.6),
                 (-17.5, -19, 38.2, 41)], iron, iron_l, iron_d)
    m.fx = [CX, 43, -9]
    return m


def scythe_rare():
    m = Model("scythe_rare_lv0")
    blue_d, blue, steel, steel_l, gem = m.col(BLUE_D), m.col(BLUE), m.col(STEEL), m.col(STEEL_L), m.col(GEM)
    wood_dd = m.col(WOOD_DD)
    m.rod(-4, -2, 2.4, steel)
    m.rod(-2, 46, 1.6, blue_d)
    grip(m, wood_dd, blue)
    for y in (24, 34):
        m.rod(y, y + 1, 2, steel)
    m.rod(44, 48, 2.8, steel)
    m.rod(45.4, 46.6, 3.1, gem)
    segments(m, [(-1.4, -5, 43.5, 48), (-5, -9, 44, 47.5), (-9, -12.5, 43.5, 47), (-12.5, -15.5, 42.5, 46), (-15.5, -18, 41, 44.5), (-18, -20, 39.2, 42.6),
                 (-20, -21.2, 37.6, 40.4)], steel, steel_l, blue)
    # 뒤쪽 작은 가시
    m.plate(45, 46.6, 1.4, 4.2, 1.0, steel)
    m.plate(45.4, 46.2, 4.2, 5.2, 0.9, blue)
    m.fx = [CX, 44, -10]
    return m


def hammer_common():
    m = Model("hammer_lv0")
    iron_d, iron, iron_l = m.col(IRON_D), m.col(IRON), m.col(IRON_L)
    wood, wood_d, wood_dd = m.col(WOOD), m.col(WOOD_D), m.col(WOOD_DD)
    m.rod(0, 2, 2.4, iron_d)
    m.rod(2, 29, 1.8, wood)
    grip(m, wood_dd, wood_d, t=2.2)
    m.rod(27, 29, 2.8, iron_d)
    m.box(CX - 3, CX + 3, 29, 36, -4.5, 4.5, iron)
    m.box(CX - 2.6, CX + 2.6, 29.4, 35.6, -5.3, -4.5, iron_d)
    m.box(CX - 2.6, CX + 2.6, 29.4, 35.6, 4.5, 5.3, iron_d)
    for y in (30.4, 34):
        m.box(CX - 3.1, CX + 3.1, y, y + 0.8, -4.6, 4.6, iron_d)
    m.box(CX - 3.1, CX + 3.1, 32.1, 32.9, -2, 2, iron_l)
    m.rod(36, 37.5, 1.6, iron_d)
    m.fx = [CX, 33, 0]
    return m


def hammer_rare():
    m = Model("hammer_rare_lv0")
    blue_d, blue, steel, steel_l, gem = m.col(BLUE_D), m.col(BLUE), m.col(STEEL), m.col(STEEL_L), m.col(GEM)
    wood_dd = m.col(WOOD_DD)
    m.rod(0, 2, 2.5, steel)
    m.rod(2, 29, 1.8, blue_d)
    grip(m, wood_dd, blue, t=2.2)
    m.rod(26.5, 29, 3, steel)
    m.box(CX - 3.5, CX + 3.5, 29, 37, -5, 5, steel)
    m.box(CX - 3, CX + 3, 29.5, 36.5, -5.8, -5, steel_l)
    m.box(CX - 3, CX + 3, 29.5, 36.5, 5, 5.8, steel_l)
    for y in (30.5, 35):
        m.box(CX - 3.6, CX + 3.6, y, y + 0.9, -5.1, 5.1, blue)
    m.box(CX - 3.7, CX + 3.7, 32.4, 33.6, -1.2, 1.2, gem)
    m.rod(37, 39.5, 1.4, steel_l)
    m.fx = [CX, 33, 0]
    return m


def scepter_common():
    m = Model("scepter_lv0")
    iron_d, iron, iron_l = m.col(IRON_D), m.col(IRON), m.col(IRON_L)
    wood, wood_d, wood_dd = m.col(WOOD), m.col(WOOD_D), m.col(WOOD_DD)
    m.rod(0, 2, 2, iron_d)
    m.rod(2, 32, 1.4, wood)
    grip(m, wood_dd, wood_d, t=2.0)
    m.rod(29, 32, 2.4, iron_d)
    # 사제의 십자 머리 (철) + 가운데 흰 돌
    m.rod(32, 41, 1.4, iron)
    m.plate(35.6, 37, -3, 3, 1.4, iron)
    m.box(CX - 0.9, CX + 0.9, 35.4, 37.2, -0.9, 0.9, iron_l)
    m.rod(41, 42, 1, iron_d)
    for s in (-1, 1):
        m.plate(35.4, 37.2, s * 3, s * 3.6, 1.0, iron_d)
    m.fx = [CX, 37, 0]
    return m


def scepter_rare():
    m = Model("scepter_rare_lv0")
    blue_d, blue, steel, steel_l, gem = m.col(BLUE_D), m.col(BLUE), m.col(STEEL), m.col(STEEL_L), m.col(GEM)
    wood_dd = m.col(WOOD_DD)
    m.rod(0, 2, 2, steel)
    m.rod(2, 33, 1.4, blue_d)
    grip(m, wood_dd, blue, t=2.0)
    m.rod(31, 33.5, 2.4, steel)
    # 작은 날개 (앞뒤 ±z) + 청수정 (층층이 마름모)
    for y0, y1, h in ((32.5, 34, 3.4), (34, 35.5, 4.4)):
        m.plate(y0, y1, 1.2, h, 0.8, steel)
        m.plate(y0, y1, -h, -1.2, 0.8, steel)
    m.plate(35, 35.5, 1.2, 4.4, 0.9, steel_l)
    m.plate(35, 35.5, -4.4, -1.2, 0.9, steel_l)
    for y0, y1, w in ((34, 35, 1.2), (35, 36.5, 2.2), (36.5, 38.5, 2.8), (38.5, 40, 2.0), (40, 41, 1.0)):
        m.box(CX - w / 2, CX + w / 2, y0, y1, -w / 2, w / 2, gem)
    m.rod(41, 42.5, 0.8, steel_l)
    for dx, dz in ((1.7, 0), (-1.7, 0), (0, 1.7), (0, -1.7)):
        m.box(CX + dx - 0.3, CX + dx + 0.3, 35.5, 39.5, dz - 0.3, dz + 0.3, steel)
    m.fx = [CX, 38, 0]
    return m


DESIGNS = {
    "lance_lv0": lance_common, "lance_rare_lv0": lance_rare, "scythe_lv0": scythe_common, "scythe_rare_lv0": scythe_rare,
    "hammer_lv0": hammer_common, "hammer_rare_lv0": hammer_rare, "scepter_lv0": scepter_common, "scepter_rare_lv0": scepter_rare,
    "lance_noble_lv0": lance_noble, "lance_hero_lv0": lance_hero, "lance_transcend_lv0": lance_transcend,
    "scythe_noble_lv0": scythe_noble, "scythe_hero_lv0": scythe_hero, "scythe_transcend_lv0": scythe_transcend,
    "hammer_noble_lv0": hammer_noble, "hammer_hero_lv0": hammer_hero, "hammer_transcend_lv0": hammer_transcend,
    "scepter_noble_lv0": scepter_noble, "scepter_hero_lv0": scepter_hero, "scepter_transcend_lv0": scepter_transcend,
}
# 영웅 · 초월 무기 빛 알갱이 색 (애드온 weapon_fx 와 같은 값 — 들고 있는 동안 fx 자리에서)
FX_COLORS = {
    "lance_hero_lv0": [[1.0, 0.25, 0.25]], "lance_transcend_lv0": [[1.0, 0.92, 0.55], [0.65, 0.95, 1.0]],
    "scythe_hero_lv0": [[0.8, 0.7, 1.0]], "scythe_transcend_lv0": [[0.45, 0.9, 1.0], [1.0, 0.85, 0.45]],
    "hammer_hero_lv0": [[0.45, 1.0, 0.45], [0.7, 0.4, 1.0]], "hammer_transcend_lv0": [[0.6, 0.82, 1.0], [0.92, 0.97, 1.0]],
    "scepter_hero_lv0": [[1.0, 0.85, 0.4], [0.8, 0.88, 1.0]], "scepter_transcend_lv0": [[1.0, 0.72, 0.25], [1.0, 0.95, 0.75]],
}


# ------------------------------------------------------------ 아이콘 (애드온 generator/tools/geo_icon.py 와 같은 방법)

ICON = 32
SS = 5
EDGE = (24, 14, 22, 255)


def weapon_cubes(geo):
    return [c for b in geo["minecraft:geometry"][0]["bones"] if b["name"].lower() == "weapon" for c in b.get("cubes", [])]


def icon(geo, tex, glow=1.7):
    """옆모습 (x 축으로 본 — 가로 z, 세로 y), 끝 (+y) 이 왼쪽 위 · 날 (-z) 이 오른쪽 위로 눕힘. 크기는 대각선 길이에 맞춤"""
    cubes = weapon_cubes(geo)
    ys = [c["origin"][1] for c in cubes] + [c["origin"][1] + c["size"][1] for c in cubes]
    zs = [c["origin"][2] for c in cubes] + [c["origin"][2] + c["size"][2] for c in cubes]
    ym, zm = (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2
    length, width = max(ys) - min(ys), max(zs) - min(zs)
    scale = min(41.0 / length, 30.0 / max(1.0, width), 1.6)
    order = sorted(cubes, key=lambda c: c["origin"][0] + c["size"][0])
    cols = []
    for c in order:
        u, v = c["uv"]["north"]["uv"]
        cols.append(tex.getpixel((int(u), int(v))))

    def sample(y, z):
        hit = None
        for c, col in zip(order, cols):
            o, s = c["origin"], c["size"]
            if o[1] <= y < o[1] + s[1] and o[2] <= z < o[2] + s[2]:
                hit = col
        return hit

    r2 = 1 / math.sqrt(2)
    ax = (-r2, -r2)
    az = (r2, -r2)
    img = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    for py in range(ICON):
        for px in range(ICON):
            votes = {}
            for sy in range(SS):
                for sx in range(SS):
                    u = px + (sx + 0.5) / SS - ICON / 2
                    v = py + (sy + 0.5) / SS - ICON / 2
                    col = sample(ym + (u * ax[0] + v * ax[1]) / scale, zm + (u * az[0] + v * az[1]) / scale)
                    if col is None:
                        continue
                    votes[col] = votes.get(col, 0) + (glow if col[3] < 255 else 1.0)
            if sum(votes.values()) < 0.3 * SS * SS:
                continue
            r, g, b, a = max(votes.items(), key=lambda kv: kv[1])[0]
            if a < 255:
                r, g, b = (min(255, int(x * 1.12 + 18)) for x in (r, g, b))
            img.putpixel((px, py), (r, g, b, 255))
    out = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    src = img.load()
    for py in range(ICON):
        for px in range(ICON):
            if not src[px, py][3] and any(0 <= px + dx < ICON and 0 <= py + dy < ICON and src[px + dx, py + dy][3]
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((px, py), EDGE)
    out.alpha_composite(img)
    return out


# ------------------------------------------------------------ 연결

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="이미 있는 디자인 · 아이콘도 임시 디자인으로 다시 씀")
    a = ap.parse_args()
    meta_path = os.path.join(ART, "designs.json")
    meta = json.load(open(meta_path, encoding="utf-8"))
    drawn = []
    for design, fn in DESIGNS.items():
        geo_path = os.path.join(ART, f"{design}.geo.json")
        tex_path = os.path.join(ART, f"{design}.png")
        if a.force or not (os.path.exists(geo_path) and os.path.exists(tex_path)):
            m = fn()
            with open(geo_path, "w", encoding="utf-8") as fh:
                json.dump(m.geo(design in FX_COLORS), fh, ensure_ascii=False, indent=1)
                fh.write("\n")
            m.texture().save(tex_path)
            drawn.append(f"{design} ({len(m.cubes)} cubes, {len(m.palette)} colors)")
        geo = json.load(open(geo_path, encoding="utf-8"))
        tex = Image.open(tex_path).convert("RGBA")
        icon_path = os.path.join(ART, f"{design}.icon.png")
        if a.force or not os.path.exists(icon_path):
            icon(geo, tex).save(icon_path)
        # designs.json: 1인칭 자세 · 빛 알갱이
        type_ = design.split("_")[0]
        d = {"fp": dict(FP, scale=[FP_SCALE_DESIGN.get(design, FP_SCALE[type_])] * 3)}
        if design in FX_COLORS:
            colors = FX_COLORS[design]
            d["fx"] = {"rate": 10.0, "max": 24, "radius": 0.18, "speed": 0.15, "life": 0.9, "accel": 1.4, "drag": 1.0, "size": 0.05,
                       "texture": "minecraft:generic_0", "cuts": [0.5] if len(colors) == 2 else [], "colors": colors}
        old = meta["designs"].get(design)
        if a.force or old is None:
            meta["designs"][design] = d
    # 무기 ID → 디자인 (고귀 · 영웅 · 초월 레벨마다 같은 디자인)
    for t in TYPES:
        for g, levels in GRADES.items():
            for lv in levels:
                meta["weapons"][f"rpg:{t}_l{lv}_{g}"] = design_of(t, g)
    meta["designs"] = dict(sorted(meta["designs"].items()))
    meta["weapons"] = dict(sorted(meta["weapons"].items()))
    with open(meta_path, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=1)
    # 아이콘 · 아이콘 모델 · 아이템 정의 (weapon_models.py 가 손 모습을 다시 씀)
    n = 0
    for t in TYPES:
        tex_dir = os.path.join(ASSETS, "textures", "item", t)
        os.makedirs(tex_dir, exist_ok=True)
        for g, levels in GRADES.items():
            design = design_of(t, g)
            ic = Image.open(os.path.join(ART, f"{design}.icon.png")).convert("RGBA")
            for lv in levels:
                wid = f"{t}_l{lv}_{g}"
                ic.save(os.path.join(tex_dir, f"{wid}.png"))
                with open(os.path.join(ASSETS, "models", "item", f"{wid}.json"), "w", encoding="utf-8") as fh:
                    json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"rpg:item/{t}/{wid}"}}, fh, indent=2)
                item_path = os.path.join(ASSETS, "items", f"{wid}.json")
                if not os.path.exists(item_path):
                    with open(item_path, "w", encoding="utf-8") as fh:
                        json.dump({"model": {"type": "minecraft:model", "model": f"rpg:item/{wid}"}}, fh, indent=2)
                        fh.write("\n")
                n += 1
    for line in drawn:
        print("drew", line)
    print(f"{n} weapon ids linked (icons · icon models · item definitions)")


if __name__ == "__main__":
    main()
