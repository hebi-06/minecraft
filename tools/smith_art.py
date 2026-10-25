#!/usr/bin/env python3
"""0.44.0 대장장이 전용 창의 그림 — 대장장이 망치 (3D 아이템 모델 · 텍스처 · 아이템 정의) · 창 패널 그림 (대장간 배경 · 모루).

  python3 tools/smith_art.py            # 모두 씀
  python3 tools/smith_art.py --preview  # 패널 (배경 + 모루) 미리 보기를 scratch 그림으로 (확인용 — 경로를 인자로)

만들어지는 것
- src/main/resources/assets/rpg/items/smith_hammer.json             아이템 정의 (rpg:smith_hammer — 동작 smith_hammer 의 hold, ItemStack 의 item_model)
- src/main/resources/assets/rpg/models/item/fx/smith_hammer.json    모델 (elements — 나무 자루 · 가죽 손잡이 · 쇠 머리 · 강철 치는 면 · 뾰족한 뒤)
- src/main/resources/assets/rpg/textures/item/fx/smith_hammer.png   텍스처 64×64 (UV 0 ~ 16 = 그림 전체 — 1 UV = 4 픽셀)
- src/main/resources/assets/mmorpg/textures/gui/sprites/smith/backdrop.png   창 왼쪽 패널 배경 112×194 (돌벽 · 화덕 · 연장 걸이 · 바닥)
- src/main/resources/assets/mmorpg/textures/gui/sprites/smith/anvil.png      모루 48×40 (옆모습 · 나무 받침)

망치 모델 좌표 (자바 아이템 모델 — 한 블록 = 16): 자루 = +y (손 → 머리), 머리의 긴 쪽 = z, 치는 면 = -z (북쪽 면 — 동작 smith_hammer 가 내려칠 때
아래를 봄: tools/motion_preview.py 로 잰 손 회전), 3인칭 표시는 무기 망치 (hammer_lv0) 와 같음 (기울기 30° — 동작의 "tilt": 30).
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
GUI = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "smith")
TEX = 64
TEXEL = TEX / 16  # 1 UV = 4 픽셀

# 텍스처 영역 (픽셀 x0, y0, x1, y1)
REG = {
    "wood": (0, 0, 16, 64),       # 자루 (세로 나뭇결)
    "leather": (16, 0, 32, 32),   # 가죽 손잡이 (비스듬히 감은 줄)
    "wood_end": (16, 32, 32, 48),  # 자루 끝 (나이테)
    "iron": (32, 0, 64, 32),      # 망치 머리 (두드린 쇠)
    "steel": (32, 32, 48, 48),    # 치는 면 (닦은 강철)
    "iron_end": (48, 32, 64, 48),  # 쇠 마개 · 띠
    "dark": (16, 48, 32, 64),     # 어두운 쇠 (뒤 끝)
}


def noise(rng, w, h, cell):
    gw, gh = w // cell + 2, h // cell + 2
    g = rng.random((gh, gw))
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


def fbm(rng, w, h, cells=(8, 4, 2), weights=(0.55, 0.3, 0.15)):
    out = np.zeros((h, w))
    for c, k in zip(cells, weights):
        out += noise(rng, w, h, max(1, c)) * k
    return out / sum(weights)


def mix(c0, c1, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(c0, float) * (1 - t) + np.array(c1, float) * t


# ------------------------------------------------------------ 망치 텍스처

def hammer_texture():
    rng = np.random.default_rng(44)
    img = np.zeros((TEX, TEX, 4))
    img[..., 3] = 255

    def put(name, rgb):
        x0, y0, x1, y1 = REG[name]
        img[y0:y1, x0:x1, :3] = rgb

    # 자루: 세로 나뭇결 (밝은 · 어두운 줄 + 옹이)
    x0, y0, x1, y1 = REG["wood"]
    w, h = x1 - x0, y1 - y0
    xs = np.arange(w)[None, :].repeat(h, 0)
    grain = 0.5 + 0.5 * np.sin(xs * 1.9 + fbm(rng, w, h, (8, 4, 2)) * 7.0)
    t = 0.35 * grain + 0.65 * fbm(rng, w, h, (16, 4, 2))
    put("wood", mix((92, 60, 36), (156, 110, 66), t))
    # 가죽: 비스듬히 감은 띠 (어두운 홈)
    x0, y0, x1, y1 = REG["leather"]
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w]
    band = ((yy + xx * 0.5) % 6) / 6.0
    shade = np.where(band < 0.18, 0.15, 0.55 + 0.35 * np.sin(band * math.pi))
    t = shade * 0.8 + fbm(rng, w, h, (4, 2, 1)) * 0.2
    put("leather", mix((48, 28, 18), (120, 74, 44), t))
    # 자루 끝: 나이테
    x0, y0, x1, y1 = REG["wood_end"]
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot(xx - w / 2 + 0.5, yy - h / 2 + 0.5)
    rings = 0.5 + 0.5 * np.cos(r * 1.6)
    put("wood_end", mix((110, 74, 44), (176, 132, 84), rings * 0.7 + 0.3 * fbm(rng, w, h, (4, 2, 1))))
    # 쇠 머리: 두드린 자국 (둥근 얼룩) + 가장자리 밝게
    x0, y0, x1, y1 = REG["iron"]
    w, h = x1 - x0, y1 - y0
    base = fbm(rng, w, h, (8, 4, 2))
    dents = np.zeros((h, w))
    for _ in range(26):
        cx, cy, rr = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(1.5, 3.5)
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx - cx, yy - cy) / rr
        dents += np.clip(1 - d, 0, 1) ** 2 * rng.uniform(0.3, 0.7)
    t = 0.45 * base + 0.35 * np.clip(dents, 0, 1) + 0.15
    put("iron", mix((46, 48, 56), (128, 134, 148), t))
    # 강철 면: 밝고 고르게 (가운데 더 밝음)
    x0, y0, x1, y1 = REG["steel"]
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w]
    c = 1 - np.hypot(xx - w / 2 + 0.5, yy - h / 2 + 0.5) / (w * 0.75)
    put("steel", mix((120, 128, 142), (222, 228, 238), np.clip(c, 0, 1) * 0.8 + 0.2 * fbm(rng, w, h, (4, 2, 1))))
    # 쇠 마개 · 띠: 어두운 쇠 + 못 머리
    x0, y0, x1, y1 = REG["iron_end"]
    w, h = x1 - x0, y1 - y0
    put("iron_end", mix((34, 36, 42), (96, 100, 112), fbm(rng, w, h, (4, 2, 1))))
    x0, y0, x1, y1 = REG["dark"]
    w, h = x1 - x0, y1 - y0
    put("dark", mix((26, 27, 32), (70, 74, 84), fbm(rng, w, h, (4, 2, 1))))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 망치 모델

# 상자: (이름, from, to, 면마다 영역 — side 는 옆 넷, end 는 위 · 아래 (y 면))
ELEMENTS = [
    ("pommel", [7.0, 4.4, 7.0], [9.0, 5.4, 9.0], "iron_end", "iron_end"),
    ("handle", [7.3, 5.4, 7.3], [8.7, 17.6, 8.7], "wood", "wood_end"),
    ("grip", [7.05, 6.4, 7.05], [8.95, 11.8, 8.95], "leather", "leather"),
    ("collar", [7.0, 16.9, 7.0], [9.0, 17.7, 9.0], "iron_end", "iron_end"),
    ("head", [6.6, 17.6, 4.6], [9.4, 20.8, 11.6], "iron", "iron"),
    ("face", [6.3, 17.3, 3.6], [9.7, 21.1, 4.6], "iron_end", "iron_end"),
    ("peen", [7.1, 18.4, 11.6], [8.9, 20.0, 13.4], "iron", "dark"),
    ("peen_tip", [7.4, 18.7, 13.4], [8.6, 19.7, 14.2], "dark", "dark"),
]


def uv_in(region, fw, fh, seed):
    """영역 안에서 면 크기 (모델 px) 에 맞는 UV 사각형 (1 모델 px = 1 텍스처 px · 영역보다 크면 늘림)"""
    x0, y0, x1, y1 = REG[region]
    rw, rh = x1 - x0, y1 - y0
    pw = min(rw, max(1.0, fw * 2.0))
    ph = min(rh, max(1.0, fh * 2.0))
    r = random.Random(seed)
    ox = x0 + r.uniform(0, rw - pw)
    oy = y0 + r.uniform(0, rh - ph)
    return [round(ox / TEXEL, 4), round(oy / TEXEL, 4), round((ox + pw) / TEXEL, 4), round((oy + ph) / TEXEL, 4)]


def hammer_model():
    elements = []
    for i, (name, f, t, side, end) in enumerate(ELEMENTS):
        dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
        faces = {}
        for k, face in enumerate(("north", "south", "east", "west", "up", "down")):
            if face in ("north", "south"):
                fw, fh, reg = dx, dy, side
            elif face in ("east", "west"):
                fw, fh, reg = dz, dy, side
            else:
                fw, fh, reg = dx, dz, end
            # 치는 면 (face 상자의 북쪽) 은 닦은 강철
            if name == "face" and face == "north":
                reg = "steel"
            faces[face] = {"uv": uv_in(reg, fw, fh, i * 13 + k), "texture": "#0"}
        elements.append({"name": name, "from": f, "to": t, "faces": faces})
    return {
        "credit": "MMO RPG — 0.44.0 대장장이 망치 (tools/smith_art.py 가 만듦, 손으로 고치지 않음)",
        "texture_size": [TEX, TEX],
        "textures": {"0": "rpg:item/fx/smith_hammer", "particle": "rpg:item/fx/smith_hammer"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": {"rotation": [30.0, 0.0, 0.0], "translation": [0.0, -3.1172, 0.355], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_lefthand": {"rotation": [30.0, 0.0, 0.0], "translation": [0.0, -3.1172, 0.355], "scale": [1.0, 1.0, 1.0]},
            "firstperson_righthand": {"rotation": [-15.0, 21.0, 6.0], "translation": [4.37, -2.7, -10.2], "scale": [0.9, 0.9, 0.9]},
            "gui": {"rotation": [25.0, -135.0, -30.0], "translation": [0.0, -2.5, 0.0], "scale": [0.62, 0.62, 0.62]},
            "ground": {"rotation": [0.0, 0.0, 0.0], "translation": [0.0, 0.0, 0.0], "scale": [0.5, 0.5, 0.5]},
        },
    }


# ------------------------------------------------------------ 창 패널: 대장간 배경

BW, BH = 112, 194
FLOOR = 150  # 바닥 선 (y)
FORGE_CX = 80  # 화덕 입 가운데 (x)
FORGE_MOUTH = (64, 92, 97, 120)  # 화덕 입 (x0, y0, x1, y1)


def backdrop():
    rng = random.Random(4401)
    nrng = np.random.default_rng(4402)
    img = np.zeros((BH, BW, 3))
    # 돌벽 (벽돌 12 × 6, 줄마다 반 칸 어긋남 · 벽돌마다 색 조금씩)
    mortar = np.array((26, 21, 20), float)
    for row in range(0, FLOOR // 6 + 1):
        y0 = row * 6
        off = 0 if row % 2 == 0 else 6
        for col in range(-1, BW // 12 + 2):
            x0 = col * 12 - off
            base = np.array((62, 50, 45), float) * rng.uniform(0.82, 1.12)
            for y in range(y0, min(FLOOR, y0 + 6)):
                for x in range(max(0, x0), min(BW, x0 + 12)):
                    edge = y == y0 or x == x0
                    if edge:
                        img[y, x] = mortar
                    else:
                        hl = 1.12 if y == y0 + 1 else (0.86 if y == y0 + 5 else 1.0)
                        img[y, x] = base * hl
    tex = fbm(nrng, BW, BH, (8, 4, 2))
    img[:FLOOR] *= (0.85 + 0.3 * tex[:FLOOR])[..., None]
    # 바닥: 넓은 돌판 (위 선 밝게 · 아래로 어둡게)
    for y in range(FLOOR, BH):
        k = (y - FLOOR) / (BH - FLOOR)
        for x in range(BW):
            slab = ((x + (8 if (y - FLOOR) // 14 % 2 else 0)) // 22) % 2
            c = np.array((58, 48, 42), float) * (1.05 if slab else 0.93) * (1 - 0.35 * k)
            if (y - FLOOR) % 14 == 0 or (x + (8 if (y - FLOOR) // 14 % 2 else 0)) % 22 == 0:
                c *= 0.72
            img[y, x] = c
    img[FLOOR] = (96, 78, 64)
    img[FLOOR + 1] = (70, 57, 48)
    img[FLOOR:] *= (0.9 + 0.2 * tex[FLOOR:])[..., None]

    # 화덕: 위로 좁아지는 쇠 덮개 · 굴뚝, 벽돌 둔덕, 둥근 화덕 입
    fx0, fy0, fx1, fy1 = FORGE_MOUTH
    hood_top, hood_bot = 10, 78
    for y in range(hood_top, hood_bot):
        k = (y - hood_top) / (hood_bot - hood_top)
        half = 9 + k * k * 26
        for x in range(BW):
            if abs(x - FORGE_CX + 0.5) <= half:
                c = np.array((44, 44, 50), float) * (0.8 + 0.25 * k)
                if abs(abs(x - FORGE_CX + 0.5) - half) < 1.0:
                    c = np.array((84, 84, 94), float)
                img[y, x] = c
    # 덮개 띠 · 못
    for y in (hood_bot - 6, hood_bot - 5):
        half = 9 + ((y - hood_top) / (hood_bot - hood_top)) ** 2 * 26
        for x in range(int(FORGE_CX - half), int(FORGE_CX + half) + 1):
            if 0 <= x < BW:
                img[y, x] = (96, 92, 100) if y == hood_bot - 6 else (30, 30, 34)
    for x in range(FORGE_CX - 28, FORGE_CX + 29, 7):
        if 0 <= x < BW:
            img[hood_bot - 9, x] = (150, 146, 156)
    # 둔덕 (벽돌 — 더 붉음)
    mx0, mx1 = FORGE_CX - 34, FORGE_CX + 33
    for y in range(hood_bot, FLOOR):
        for x in range(max(0, mx0), min(BW, mx1)):
            row = (y - hood_bot) // 5
            off = 0 if row % 2 == 0 else 4
            edge = (y - hood_bot) % 5 == 0 or (x - mx0 + off) % 9 == 0
            c = np.array((104, 56, 40), float) * (0.85 + 0.25 * rng.random())
            img[y, x] = (40, 24, 20) if edge else c
    img[hood_bot, max(0, mx0):min(BW, mx1)] = (130, 84, 62)
    # 화덕 입: 둥근 위 · 안은 뜨거운 빛 (가운데 노랑 → 주황 → 빨강 → 어두움) · 아래 숯
    for y in range(fy0 - 6, fy1):
        for x in range(fx0, fx1):
            cx = (fx0 + fx1 - 1) / 2
            arch_y = fy0 + 6 - math.sqrt(max(0.0, 1 - ((x - cx) / ((fx1 - fx0) / 2)) ** 2)) * 12
            if y < arch_y:
                continue
            d = math.hypot((x - cx) / ((fx1 - fx0) / 2), (y - fy1 + 3) / (fy1 - fy0 + 6))
            heat = max(0.0, 1.0 - d)
            c = np.array(mix((60, 12, 8), (255, 120, 30), np.array(min(1, heat * 1.6))), float)
            if heat > 0.55:
                c = np.array(mix((255, 140, 40), (255, 236, 150), np.array((heat - 0.55) / 0.45)), float)
            img[y, x] = c
    # 숯 · 불씨 (입 아래쪽)
    for _ in range(60):
        x = rng.randint(fx0 + 1, fx1 - 2)
        y = rng.randint(fy1 - 6, fy1 - 1)
        img[y, x] = rng.choice([(30, 18, 16), (60, 24, 14), (255, 200, 90), (255, 140, 40)])
    # 입 둘레 아치 벽돌 테
    cx = (fx0 + fx1 - 1) / 2
    for a in np.linspace(math.pi, 2 * math.pi, 40):
        for rr in (17.5, 18.5):
            x = int(round(cx + math.cos(a) * rr))
            y = int(round(fy0 + 6 + math.sin(a) * 13.5 * rr / 18))
            if 0 <= x < BW and 0 <= y < BH:
                img[y, x] = (140, 80, 56) if rr < 18 else (44, 26, 22)
    for y in range(fy0 + 6, fy1):
        for x in (fx0 - 1, fx1):
            img[y, x] = (140, 80, 56)

    # 연장 걸이 (왼쪽 벽): 나무 판 · 집게 · 망치 · 편자 그림자
    for x in range(5, 50):
        img[38, x] = (120, 84, 52)
        img[39, x] = (88, 60, 36)
        img[40, x] = (54, 36, 22)
    for x in (9, 46):
        img[41, x] = (150, 146, 156)

    def line(x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            x = int(round(x0 + (x1 - x0) * t))
            y = int(round(y0 + (y1 - y0) * t))
            if 0 <= x < BW and 0 <= y < BH:
                img[y, x] = c

    iron = (34, 35, 40)
    hi = (92, 96, 108)
    # 집게
    line(14, 41, 11, 64, iron)
    line(15, 41, 18, 64, iron)
    line(13, 41, 16, 41, hi)
    img[50, 14] = hi
    # 망치
    line(26, 41, 26, 62, (96, 66, 40))
    for y in range(58, 64):
        for x in range(23, 30):
            img[y, x] = iron if y > 58 else hi
    # 편자
    for a in np.linspace(0.15 * math.pi, 1.85 * math.pi, 22):
        x = int(round(38 + math.sin(a) * 4))
        y = int(round(50 - math.cos(a) * 5))
        img[y, x] = iron
    img[45, 38] = hi
    # 물통 (왼쪽 아래 — 바닥 위)
    for y in range(FLOOR - 16, FLOOR + 2):
        for x in range(4, 20):
            c = np.array((84, 58, 36), float) * (0.9 + 0.2 * ((x - 4) % 5 == 0))
            if y in (FLOOR - 13, FLOOR - 3):
                c = np.array((46, 46, 52), float)
            img[y, x] = c
    img[FLOOR - 16, 4:20] = (40, 60, 74)

    # 빛: 화덕 입에서 퍼지는 주황 빛 (더함) · 가장자리 어둡게
    yy, xx = np.mgrid[0:BH, 0:BW]
    fcy = (fy0 + fy1) / 2
    d = np.hypot((xx - FORGE_CX) / 1.0, (yy - fcy) / 1.15)
    glow = np.clip(1 - d / 78, 0, 1) ** 2
    img += glow[..., None] * np.array((150, 70, 18))
    v = np.hypot((xx - BW / 2) / (BW / 2), (yy - BH * 0.55) / (BH * 0.62))
    img *= np.clip(1.15 - 0.55 * v ** 2, 0.35, 1.0)[..., None]
    out = np.zeros((BH, BW, 4))
    out[..., :3] = np.clip(img, 0, 255)
    out[..., 3] = 255
    return Image.fromarray(out.astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 창 패널: 모루

AW, AH = 48, 40


def anvil():
    img = np.zeros((AH, AW, 4))
    rng = random.Random(4403)

    def px(x, y, c, a=255):
        if 0 <= x < AW and 0 <= y < AH:
            img[y, x, :3] = c
            img[y, x, 3] = a

    dark = np.array((26, 27, 32), float)
    mid = np.array((52, 54, 62), float)
    lite = np.array((92, 96, 108), float)
    top = np.array((150, 156, 170), float)
    warm = np.array((255, 150, 70), float)

    def shade(x, y, x0, x1, y0, y1):
        # 위는 밝게 · 아래 어둡게 · 오른쪽 (화덕 쪽) 가장자리에 따뜻한 빛
        k = (y - y0) / max(1, y1 - y0)
        c = mid * (1.15 - 0.45 * k)
        if x >= x1 - 2:
            c = c * 0.7 + warm * 0.3
        if x <= x0 + 1:
            c = c * 0.8
        return c

    # 윗면 (판 + 오른쪽으로 뾰족한 뿔 · 왼쪽 네모 뒤꿈치)
    y0, y1 = 6, 13
    for y in range(y0, y1):
        for x in range(4, 47):
            if x > 36:
                # 뿔: 아래가 둥글게 깎여 끝으로 좁아짐
                tip = (x - 36) / 11
                bottom = y0 + (y1 - y0) * (1 - tip ** 0.8) * 1.0
                if y > bottom or y < y0 + tip * 2.5:
                    continue
            px(x, y, shade(x, y, 4, 47, y0, y1))
    for x in range(4, 37):
        px(x, y0, top)
        px(x, y0 + 1, lite)
    for x in range(37, 47):
        tip = (x - 36) / 11
        yt = y0 + tip * 2.5
        if yt <= y0 + (y1 - y0) * (1 - tip ** 0.8):
            px(x, int(math.ceil(yt)), top)
    # 판의 구멍 (네모 · 둥근)
    px(10, y0, dark)
    px(11, y0, dark)
    px(14, y0, dark)
    # 허리 (안으로 들어감)
    for y in range(13, 25):
        k = (y - 13) / 12
        inset = 7 * math.sin(k * math.pi) ** 0.7
        xa, xb = int(round(13 + inset)), int(round(36 - inset))
        for x in range(xa, xb):
            px(x, y, shade(x, y, xa, xb, 13, 25) * 0.92)
        px(xa, y, dark)
    # 받침 (아래로 퍼짐)
    for y in range(25, 31):
        k = (y - 25) / 5
        xa, xb = int(round(13 - 4 * k)), int(round(36 + 4 * k))
        for x in range(xa, xb):
            px(x, y, shade(x, y, xa, xb, 25, 31))
        px(xa, y, dark)
    for x in range(9, 41):
        px(x, 30, dark)
    # 나무 받침 (그루터기 — 나무껍질 · 위 나이테 선)
    bark = [0.8 + 0.25 * rng.random() for _ in range(AW)]
    for y in range(31, 40):
        for x in range(11, 39):
            # 세로 나무껍질 결 (기둥마다 밝기 · 사이사이 어두운 홈) · 아래로 어둡게
            c = np.array((104, 70, 42), float) * bark[x] * (1 - 0.04 * (y - 31))
            if x % 4 == 0 or (x % 4 == 1 and rng.random() < 0.3):
                c *= 0.66
            if x in (11, 38):
                c = np.array((54, 36, 22), float)
            px(x, y, c)
    for x in range(11, 39):
        px(x, 31, (150, 112, 70))
    # 그림자 (바닥)
    for x in range(8, 42):
        a = int(110 * (1 - abs(x - 25) / 18))
        if img[39, x, 3] == 0:
            px(x, 39, (0, 0, 0), max(0, a))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 쓰기

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main(argv):
    if "--preview" in argv:
        out = argv[argv.index("--preview") + 1] if argv.index("--preview") + 1 < len(argv) else "smith_preview.png"
        bg = backdrop().convert("RGBA")
        an = anvil()
        bg.alpha_composite(an, (64, 126))
        bg = bg.resize((BW * 4, BH * 4), Image.NEAREST)
        tex = hammer_texture().resize((TEX * 4, TEX * 4), Image.NEAREST)
        sheet = Image.new("RGBA", (BW * 4 + TEX * 4 + 16, BH * 4), (20, 20, 24, 255))
        sheet.paste(bg, (0, 0))
        sheet.paste(tex, (BW * 4 + 16, 0))
        sheet.save(out)
        print("preview →", out)
        return
    os.makedirs(os.path.join(RPG, "textures", "item", "fx"), exist_ok=True)
    hammer_texture().save(os.path.join(RPG, "textures", "item", "fx", "smith_hammer.png"))
    write_json(os.path.join(RPG, "models", "item", "fx", "smith_hammer.json"), hammer_model())
    write_json(os.path.join(RPG, "items", "smith_hammer.json"), {"model": {"type": "minecraft:model", "model": "rpg:item/fx/smith_hammer"}})
    os.makedirs(GUI, exist_ok=True)
    backdrop().save(os.path.join(GUI, "backdrop.png"))
    anvil().save(os.path.join(GUI, "anvil.png"))
    print("smith art written")


if __name__ == "__main__":
    main(sys.argv[1:])
