#!/usr/bin/env python3
"""0.46.0 룬 동작의 소품 (손에 들었다가 던지는 것) — 16×16 그림 · 아이템 모델 · 아이템 정의 (등록된 아이템이 아님: 동작의 hold · holdOff 가
막대기 아이템에 item_model 로 씌워 그림 — 자바 MotionRender.holdStack).

  python3 tools/prop_art.py            # 그림 · 모델 · 정의를 씀
  python3 tools/prop_art.py --preview 그림.png   # 소품을 크게 한 장에

소품 (rpg:prop_*):
  smoke_bomb   연막탄 (암살자 연막 — 주 손 swap) — 검은 공 · 보라 띠 · 심지 불꽃
  shuriken     독 표창 (암살자 독 표창) — 네 날 강철 별 · 초록 독
  steel_trap   강철 덫 (사냥꾼 강철 덫) — 이빨 달린 두 턱 · 사슬
  hunt_net     사냥 그물 (사냥꾼 사냥 그물) — 접은 밧줄 그물 · 추
  chain_hook   사슬 갈고리 (투사 사슬 갈고리) — 쇠 갈고리 · 사슬
  lava_bomb    화산탄 (화산 화산탄) — 검은 바위 · 빛나는 용암 금
  judge_hammer 심판의 망치 (성기사) — 금빛 망치
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/rpg")
S = 16


def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


def mix(a, b, k):
    return tuple(int(round(a[i] + (b[i] - a[i]) * k)) for i in range(3))


def ball(img, cx, cy, r, dark, mid, light, rim=None):
    """둥근 공 (왼쪽 위에서 빛)"""
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                lx = (-dx - dy) / (r * 1.4)
                k = max(0.0, min(1.0, 0.5 + lx * 0.7))
                c = mix(dark, mid, min(1, k * 1.6)) if k < 0.62 else mix(mid, light, (k - 0.62) / 0.38)
                if rim is not None and d > r - 0.9:
                    c = rim
                put(img, x, y, c)


def smoke_bomb():
    img = new()
    ball(img, 7.5, 9.0, 5.6, (18, 18, 24), (52, 50, 62), (120, 118, 138), rim=(10, 10, 14))
    for x in range(2, 14):  # 보라 띠
        for y in (8, 9):
            if img.getpixel((x, y))[3]:
                put(img, x, y, (120, 60, 170) if y == 8 else (86, 40, 128))
    for y in (2, 3):  # 심지 마개
        for x in (7, 8):
            put(img, x, y, (90, 80, 70))
    put(img, 9, 2, (150, 120, 80))
    put(img, 10, 1, (150, 120, 80))
    put(img, 11, 0, (255, 230, 120))  # 불꽃
    put(img, 12, 1, (255, 150, 40))
    put(img, 11, 1, (255, 200, 80))
    put(img, 4, 6, (190, 190, 210))  # 빛
    return img


def shuriken():
    img = new()
    c = (7.5, 7.5)
    steel_d, steel_m, steel_l = (70, 76, 90), (150, 158, 176), (225, 230, 240)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - c[0], y + 0.5 - c[1]
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            # 네 날 (45° 돌림) — 날 끝으로 갈수록 가늘게
            k = abs(math.cos(2 * (a - math.pi / 4)))
            edge = 2.2 + 5.6 * k ** 6
            if r <= edge and r <= 7.4:
                shade = steel_l if (dx - dy) < -1.5 else steel_m if (dx - dy) < 2 else steel_d
                if r > edge - 1.0 and r > 3.5:
                    shade = (70, 170, 70) if k > 0.9 else (50, 120, 50)  # 독 묻은 날
                put(img, x, y, shade)
    for y in (7, 8):
        for x in (7, 8):
            put(img, x, y, (24, 26, 30))  # 가운데 구멍
    return img


def steel_trap():
    """위에서 본 벌린 덫: 둥근 쇠 테 · 안쪽으로 난 이빨 · 가운데 발판 · 사슬"""
    img = new()
    dark, mid, light, tooth = (48, 50, 58), (118, 122, 134), (190, 194, 206), (236, 238, 242)
    c = (7.5, 7.0)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - c[0], y + 0.5 - c[1]
            r = math.hypot(dx / 1.0, dy / 0.82)
            if 5.4 <= r <= 6.6:
                put(img, x, y, light if dy < 0 else mid)
            elif 6.6 < r <= 7.3:
                put(img, x, y, dark)
            elif 4.2 <= r < 5.4:
                a = math.atan2(dy, dx)
                if int((a + math.pi) / (2 * math.pi) * 14) % 2 == 0:
                    put(img, x, y, tooth)  # 이빨
            elif r <= 2.0:
                put(img, x, y, (150, 110, 60) if r > 1.2 else (190, 150, 90))  # 발판
    for y in range(int(c[1]) - 5, int(c[1]) + 6):  # 경첩 막대 (가로질러)
        pass
    for x in range(1, 15):
        if img.getpixel((x, 7))[3] == 0 or abs(x - 7.5) > 2.2:
            continue
    for i, (x, y) in enumerate(((12, 12), (13, 13), (14, 14), (15, 15), (13, 14))):  # 사슬
        put(img, x, y, (150, 150, 160) if i % 2 == 0 else (96, 96, 106))
    return img


def hunt_net():
    img = new()
    rope, dark = (186, 150, 96), (120, 90, 52)
    for y in range(S):
        for x in range(S):
            dx, dy = (x + 0.5 - 8) / 6.8, (y + 0.5 - 8.5) / 6.0
            if dx * dx + dy * dy <= 1:
                if (x + y) % 3 == 0 or (x - y) % 3 == 0:
                    put(img, x, y, rope)
                elif dx * dx + dy * dy > 0.75:
                    put(img, x, y, dark)
    for x, y in ((2, 3), (13, 3), (2, 14), (13, 14)):  # 추
        put(img, x, y, (80, 80, 90))
    return img


def chain_hook():
    """쇠 갈고리 (갈퀴 셋 — 굵게) · 아래로 사슬"""
    img = new()
    steel_d, steel_m, steel_l = (56, 58, 68), (132, 136, 150), (214, 218, 228)
    for y in range(1, 9):  # 가운데 대
        put(img, 7, y, steel_m)
        put(img, 8, y, steel_l)
    for i in range(5):  # 왼쪽 · 오른쪽 갈퀴 (휘어 올라감)
        x0, y0 = 6 - i, 7 - (i if i < 3 else 2 + (i - 2) * 2)
        put(img, x0, y0, steel_m)
        put(img, x0, y0 - 1 if i == 4 else y0, steel_l)
        x1 = 9 + i
        put(img, x1, y0, steel_l)
        put(img, x1, y0 + 1 if i < 2 else y0, steel_m)
    for x, y in ((2, 1), (13, 1), (7, 0), (8, 0)):  # 갈퀴 끝 (뾰족)
        put(img, x, y, (240, 242, 248))
    for y in range(9, 11):
        put(img, 7, y, steel_d)
        put(img, 8, y, steel_m)
    for i in range(5):  # 사슬 고리 (오른쪽 아래로)
        x, y = 8 + i, 11 + i
        if y < S:
            put(img, x, y, steel_m if i % 2 == 0 else steel_d)
            put(img, x - 1, y, steel_d if i % 2 == 0 else steel_m)
    return img


def lava_bomb():
    img = new()
    ball(img, 8, 8.5, 6.2, (26, 18, 16), (70, 44, 34), (120, 80, 60), rim=(16, 10, 8))
    cracks = [(5, 6), (6, 7), (7, 7), (8, 8), (9, 8), (10, 9), (8, 9), (7, 10), (6, 11), (10, 6), (11, 5), (9, 11), (10, 12)]
    for x, y in cracks:
        put(img, x, y, (255, 150, 40))
    for x, y in ((7, 7), (9, 8)):
        put(img, x, y, (255, 230, 120))
    put(img, 4, 4, (255, 120, 30))  # 튀는 불씨
    put(img, 13, 3, (255, 200, 80))
    return img


def judge_hammer():
    """금빛 심판의 망치 (큰 머리 · 빛나는 면)"""
    img = new()
    gold_d, gold_m, gold_l = (150, 100, 20), (226, 176, 52), (255, 238, 150)
    for i in range(9):  # 자루 (왼쪽 아래 → 오른쪽 위)
        x, y = 1 + i, 14 - i
        put(img, x, y, (130, 86, 40) if i % 2 == 0 else (96, 62, 30))
        put(img, x + 1, y, (160, 110, 60))
    put(img, 1, 15, gold_m)
    put(img, 0, 15, gold_d)
    for y in range(0, 10):  # 머리 (돌린 네모 — 큼)
        for x in range(6, 16):
            dx, dy = x - 11, y - 4.5
            if abs(dx + dy) <= 5.0 and abs(dx - dy) <= 3.2:
                c = gold_l if dx - dy < -1.2 else gold_m if dx - dy < 1.4 else gold_d
                put(img, x, y, c)
    for x, y in ((9, 2), (10, 2), (9, 3)):
        put(img, x, y, (255, 255, 230))
    return img


PROPS = {
    "smoke_bomb": (smoke_bomb, "generated"),
    "shuriken": (shuriken, "generated"),
    "steel_trap": (steel_trap, "generated"),
    "hunt_net": (hunt_net, "generated"),
    "chain_hook": (chain_hook, "handheld"),
    "lava_bomb": (lava_bomb, "generated"),
    "judge_hammer": (judge_hammer, "handheld"),
}


def write():
    tex = os.path.join(ASSETS, "textures/item/fx")
    mod = os.path.join(ASSETS, "models/item/fx")
    itm = os.path.join(ASSETS, "items")
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    for name, (fn, parent) in PROPS.items():
        img = fn()
        img.save(os.path.join(tex, "prop_" + name + ".png"))
        with open(os.path.join(mod, "prop_" + name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/" + parent, "textures": {"layer0": "rpg:item/fx/prop_" + name}}, f, indent=1)
            f.write("\n")
        with open(os.path.join(itm, "prop_" + name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "rpg:item/fx/prop_" + name}}, f, indent=1)
            f.write("\n")
        print("prop_" + name)


def preview(path):
    cell = 128
    img = Image.new("RGBA", (cell * len(PROPS), cell), (40, 44, 52, 255))
    for i, (name, (fn, _)) in enumerate(PROPS.items()):
        img.alpha_composite(fn().resize((cell - 16, cell - 16), Image.NEAREST), (i * cell + 8, 8))
    img.save(path)
    print(path)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    else:
        write()
