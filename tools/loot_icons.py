#!/usr/bin/env python3
"""9단계 새 몬스터 전리품 아이콘 (32×32 — 애드온 전리품 아이콘처럼: 둥근 빛 바탕 · 진한 테두리 · 왼쪽 위 빛 · 반짝임).

tools/loot_icons.py
- src/main/resources/assets/rpg/textures/item/material/loot_<키>.png
- src/main/resources/assets/rpg/items/loot_<키>.json · models/item/loot_<키>.json (generated)
- src/main/resources/assets/mmorpg/materials.json 에 이름 (색 + "(전리품)") 더함
키 · 이름 · 색은 규칙 덧붙임 (core/src/main/resources/mmorpg/rules_more.json MONSTER_DROPS).
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets")
MORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules_more.json")
N = 32
OUT_DARK = (24, 18, 26, 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, k):
    return tuple(clamp(x * k) for x in c[:3])


class Icon:
    def __init__(self, glow):
        self.px = [[None] * N for _ in range(N)]  # 칸마다 (색, 층)
        self.glow = glow

    def mask(self, draw_fn):
        """그리는 함수 (ImageDraw) 로 32×32 가림 — 칠한 칸 집합"""
        im = Image.new("L", (N, N), 0)
        draw_fn(ImageDraw.Draw(im))
        p = im.load()
        return {(x, y) for y in range(N) for x in range(N) if p[x, y] > 127}

    def fill(self, cells, base, light=0.28, dark=0.32, grad=0.18, noise=6, seed=1):
        """칸들을 칠함: 아래로 갈수록 어둡게, 왼쪽 위 가장자리는 밝게 · 오른쪽 아래 가장자리는 어둡게, 약한 얼룩"""
        rnd = random.Random(seed)
        if not cells:
            return
        ys = [y for _, y in cells]
        y0, y1 = min(ys), max(ys)
        for (x, y) in cells:
            t = (y - y0) / max(1, y1 - y0)
            c = shade(base, 1 + grad * (0.5 - t))
            ul = (x - 1, y) not in cells or (x, y - 1) not in cells
            dr = (x + 1, y) not in cells or (x, y + 1) not in cells
            if ul and not dr:
                c = mix(c, (255, 255, 255), light)
            elif dr and not ul:
                c = shade(c, 1 - dark)
            n = rnd.randint(-noise, noise)
            c = tuple(clamp(v + n) for v in c)
            self.px[y][x] = c

    def put(self, x, y, c):
        if 0 <= x < N and 0 <= y < N:
            self.px[y][x] = c[:3]

    def line(self, pts, c, cells=None):
        """점들을 잇는 1칸 선 (cells 가 있으면 그 안에서만)"""
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            n = max(abs(xb - xa), abs(yb - ya), 1)
            for i in range(n + 1):
                x = round(xa + (xb - xa) * i / n)
                y = round(ya + (yb - ya) * i / n)
                if cells is None or (x, y) in cells:
                    self.put(x, y, c)

    def render(self, sparkles=()):
        im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        p = im.load()
        # 빛 바탕 (둥근, 가운데 진하게)
        g = self.glow
        for y in range(N):
            for x in range(N):
                d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 15.5
                if d < 1:
                    a = (1 - d) ** 1.6 * 120
                    p[x, y] = (*mix((40, 36, 48), g, 0.55), clamp(a))
        filled = {(x, y) for y in range(N) for x in range(N) if self.px[y][x] is not None}
        # 바깥 테두리
        for (x, y) in list(filled):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < N and 0 <= q[1] < N:
                    p[q[0], q[1]] = OUT_DARK
        for (x, y) in filled:
            p[x, y] = (*self.px[y][x], 255)
        for (x, y, c) in sparkles:
            p[x, y] = (*c, 255)
        return im


def nisse(ic):
    # 빨간 뜨개 모자: 휜 원뿔 + 흰 털 테 + 끝의 방울
    cone = ic.mask(lambda d: d.polygon([(7, 24), (25, 24), (20, 13), (15, 6), (9, 3), (12, 10), (10, 17)], fill=255))
    ic.fill(cone, (205, 45, 40), seed=2)
    for y in range(6, 24, 3):  # 뜨개 줄
        ic.line([(8, y + 1), (24, y + 1)], (165, 30, 30), cone)
    brim = ic.mask(lambda d: d.rounded_rectangle((5, 22, 27, 27), radius=2, fill=255))
    ic.fill(brim, (236, 232, 222), light=0.2, dark=0.25, noise=10, seed=3)
    bob = ic.mask(lambda d: d.ellipse((5, 1, 11, 7), fill=255))
    ic.fill(bob, (240, 236, 228), noise=8, seed=4)
    return [(26, 7, (255, 220, 200)), (4, 15, (255, 200, 190))]


def boar(ic):
    # 휜 엄니 (상아) + 뿌리 (갈색)
    tusk = ic.mask(lambda d: d.polygon([(9, 26), (14, 27), (17, 21), (22, 14), (25, 6), (26, 3), (23, 6), (18, 12), (12, 18)], fill=255))
    ic.fill(tusk, (238, 226, 196), seed=5)
    ic.line([(13, 23), (18, 17), (22, 11), (24, 6)], (255, 250, 236), tusk)
    root = ic.mask(lambda d: d.polygon([(6, 24), (14, 22), (16, 27), (9, 30), (5, 28)], fill=255))
    ic.fill(root, (122, 78, 46), noise=10, seed=6)
    return [(27, 10, (255, 245, 220)), (5, 18, (255, 240, 210))]


def sand_scorpion(ic):
    # 마디진 꼬리 독침 (호박색) + 끝의 초록 독 방울
    segs = [(8, 27, 13, 22), (11, 21, 16, 16), (15, 15, 20, 11), (19, 11, 24, 8)]
    for i, (a, b, c, e) in enumerate(segs):
        m = ic.mask(lambda d, a=a, b=b, c=c, e=e: d.ellipse((min(a, c) - 1, min(b, e) - 1, max(a, c) + 2, max(b, e) + 2), fill=255))
        ic.fill(m, (214, 160, 72), seed=10 + i)
    sting = ic.mask(lambda d: d.polygon([(22, 9), (26, 6), (27, 2), (28, 9), (25, 12)], fill=255))
    ic.fill(sting, (92, 60, 32), seed=20)
    drop = ic.mask(lambda d: d.ellipse((24, 11, 29, 17), fill=255))
    ic.fill(drop, (120, 235, 80), light=0.45, dark=0.2, noise=4, seed=21)
    ic.put(25, 12, (230, 255, 210))
    return [(6, 12, (230, 255, 190)), (29, 22, (200, 255, 170))]


def crab(ic):
    # 집게발: 굵은 손 + 위 · 아래 집게 (끝이 밝음)
    hand = ic.mask(lambda d: d.ellipse((5, 13, 21, 28), fill=255))
    ic.fill(hand, (214, 78, 52), seed=30)
    top = ic.mask(lambda d: d.polygon([(16, 15), (21, 9), (27, 5), (29, 8), (25, 13), (20, 18)], fill=255))
    ic.fill(top, (226, 92, 60), seed=31)
    low = ic.mask(lambda d: d.polygon([(18, 21), (24, 18), (29, 17), (28, 20), (22, 24)], fill=255))
    ic.fill(low, (200, 70, 48), seed=32)
    for (x, y) in ((27, 6), (28, 7), (28, 18), (27, 19)):
        ic.put(x, y, (255, 214, 170))
    for (x, y) in ((9, 18), (12, 16), (10, 22), (14, 20)):  # 돌기
        ic.put(x, y, (246, 150, 110))
    return [(4, 8, (255, 220, 200)), (29, 26, (255, 210, 190))]


def worm_larva(ic):
    # 둥글게 말린 허물: 마디 (밝은 크림 · 반투명 느낌 — 안쪽이 밝음)
    ring = ic.mask(lambda d: (d.ellipse((4, 6, 28, 28), fill=255), d.ellipse((12, 13, 21, 22), fill=0)))
    ic.fill(ring, (232, 210, 150), light=0.35, dark=0.25, noise=8, seed=40)
    cx, cy = 16, 17
    for k in range(10):  # 마디 줄
        a = k / 10 * math.tau
        ic.line([(round(cx + math.cos(a) * 5), round(cy + math.sin(a) * 5)), (round(cx + math.cos(a) * 11), round(cy + math.sin(a) * 11))], (196, 168, 104), ring)
    head = ic.mask(lambda d: d.ellipse((20, 3, 28, 11), fill=255))
    ic.fill(head, (240, 222, 168), seed=41)
    ic.put(25, 6, (90, 60, 40))
    return [(4, 4, (255, 245, 210)), (28, 26, (255, 240, 200))]


def grendel(ic):
    # 갈고리 발톱: 검푸른 회색, 젖은 빛 (가는 밝은 줄)
    claw = ic.mask(lambda d: d.polygon([(8, 28), (13, 28), (16, 22), (21, 15), (25, 10), (27, 5), (24, 2), (25, 6), (20, 11), (13, 18), (9, 23)], fill=255))
    ic.fill(claw, (74, 80, 86), light=0.3, dark=0.35, seed=50)
    ic.line([(12, 24), (17, 18), (22, 12), (24, 7)], (170, 200, 205), claw)
    base = ic.mask(lambda d: d.ellipse((4, 22, 15, 31), fill=255))
    ic.fill(base, (104, 124, 98), noise=10, seed=51)
    return [(27, 13, (190, 230, 220)), (5, 14, (180, 220, 210))]


def frost_wolf(ic):
    # 은빛 가죽 (굴론 털가죽 모양 — 네 귀퉁이) + 서리 반짝임
    pelt = ic.mask(lambda d: d.polygon([(6, 7), (11, 9), (16, 6), (21, 9), (26, 7), (24, 14), (27, 20), (24, 25), (21, 23), (16, 27), (11, 23), (8, 25), (5, 20), (8, 14)], fill=255))
    ic.fill(pelt, (176, 200, 220), light=0.35, dark=0.3, noise=10, seed=60)
    mid = ic.mask(lambda d: d.ellipse((11, 11, 21, 22), fill=255))
    ic.fill({c for c in mid if c in pelt}, (214, 232, 246), light=0.2, dark=0.15, noise=8, seed=61)
    for (x, y) in ((9, 12), (14, 9), (22, 12), (19, 20), (12, 20), (24, 18)):
        ic.put(x, y, (240, 250, 255))
    return [(4, 4, (220, 245, 255)), (28, 28, (200, 240, 255)), (27, 4, (230, 250, 255))]


def frost_giant(ic):
    # 빛나는 얼음 심장 (깎은 보석): 바깥 진한 파랑 → 가운데 밝은 빛
    gem = ic.mask(lambda d: d.polygon([(16, 3), (25, 10), (25, 20), (16, 29), (7, 20), (7, 10)], fill=255))
    ic.fill(gem, (60, 150, 225), light=0.4, dark=0.35, noise=4, seed=70)
    core = ic.mask(lambda d: d.polygon([(16, 9), (21, 13), (21, 18), (16, 23), (11, 18), (11, 13)], fill=255))
    ic.fill(core, (170, 235, 255), light=0.3, dark=0.2, noise=3, seed=71)
    ic.line([(16, 3), (16, 9)], (150, 220, 255), gem)
    ic.line([(7, 10), (11, 13)], (110, 190, 245), gem)
    ic.line([(25, 10), (21, 13)], (40, 110, 190), gem)
    ic.line([(16, 29), (16, 23)], (36, 100, 180), gem)
    ic.put(15, 12, (255, 255, 255))
    ic.put(14, 13, (240, 252, 255))
    return [(4, 6, (210, 245, 255)), (28, 24, (200, 240, 255)), (27, 5, (230, 250, 255))]


# ---------------------------------------------------------------- 0.32.0 공허 몬스터 전리품 (검보라 · 분홍 발광)

VOID_DARK = (44, 32, 74)
VOID_PINK = (255, 92, 222)
VOID_LIGHT = (255, 214, 250)


def void_leaper(ic):
    # 공허 독주머니: 둥근 분홍 주머니 (안쪽이 밝음 · 빛 핏줄) + 위의 검보라 갑각 마개
    sac = ic.mask(lambda d: d.ellipse((6, 8, 26, 29), fill=255))
    ic.fill(sac, (214, 64, 190), light=0.35, dark=0.3, noise=6, seed=80)
    inner = ic.mask(lambda d: d.ellipse((11, 13, 21, 24), fill=255))
    ic.fill(inner, (255, 140, 236), light=0.25, dark=0.1, noise=4, seed=81)
    for pts in (((9, 18), (12, 15), (15, 16)), ((22, 14), (20, 18), (23, 22)), ((13, 26), (16, 23), (19, 26))):
        ic.line(list(pts), VOID_LIGHT, sac)
    cap = ic.mask(lambda d: d.polygon([(10, 9), (14, 4), (18, 4), (22, 9), (19, 11), (13, 11)], fill=255))
    ic.fill(cap, VOID_DARK, light=0.3, dark=0.35, noise=5, seed=82)
    ic.put(16, 5, (180, 110, 255))
    ic.put(14, 15, (255, 255, 255))
    return [(4, 6, VOID_LIGHT), (28, 20, (255, 170, 240)), (27, 4, (255, 220, 250))]


def void_stalker(ic):
    # 균열 칼날: 휘어진 연보라 뼈 칼날 · 분홍 빛 날 · 아래 검보라 손잡이 마디
    blade = ic.mask(lambda d: d.polygon([(9, 26), (12, 27), (16, 21), (21, 14), (25, 8), (27, 3), (24, 5), (19, 10), (13, 17), (9, 22)], fill=255))
    ic.fill(blade, (214, 205, 238), light=0.3, dark=0.3, noise=5, seed=90)
    ic.line([(10, 23), (14, 18), (19, 12), (23, 7), (26, 4)], VOID_PINK, blade)
    ic.line([(11, 24), (15, 19)], (255, 170, 240), blade)
    base = ic.mask(lambda d: d.polygon([(5, 25), (11, 22), (14, 27), (9, 31), (4, 29)], fill=255))
    ic.fill(base, VOID_DARK, noise=6, seed=91)
    ic.put(8, 27, VOID_PINK)
    return [(27, 12, VOID_LIGHT), (5, 13, (230, 190, 255))]


def void_jelly(ic):
    # 공허 핵: 빛나는 분홍 구슬 (가운데 흰 빛) · 둘레를 도는 검보라 고리 조각
    ring = ic.mask(lambda d: (d.ellipse((3, 9, 29, 23), fill=255), d.ellipse((6, 11, 26, 21), fill=0)))
    ic.fill(ring, VOID_DARK, light=0.35, dark=0.3, noise=5, seed=100)
    orb = ic.mask(lambda d: d.ellipse((9, 6, 23, 26), fill=255))
    ic.fill(orb, (238, 74, 214), light=0.4, dark=0.3, noise=5, seed=101)
    mid = ic.mask(lambda d: d.ellipse((12, 10, 20, 21), fill=255))
    ic.fill(mid, (255, 160, 240), light=0.2, dark=0.1, noise=3, seed=102)
    core = ic.mask(lambda d: d.ellipse((14, 13, 18, 18), fill=255))
    ic.fill(core, (255, 238, 252), light=0.1, dark=0.05, noise=2, seed=103)
    # 고리 앞쪽 (구슬 아래를 지남)
    front = ic.mask(lambda d: (d.ellipse((3, 9, 29, 23), fill=255), d.ellipse((6, 11, 26, 21), fill=0), d.rectangle((0, 0, 31, 16), fill=0)))
    ic.fill(front, (70, 50, 112), light=0.3, dark=0.3, noise=4, seed=104)
    return [(5, 5, VOID_LIGHT), (27, 26, (255, 180, 245)), (26, 4, (255, 230, 252))]


def void_borer(ic):
    # 굴착자의 턱: 세 갈래 뾰족한 연보라 턱 (드릴 끝) · 분홍 빛 안쪽 · 검보라 뿌리
    root = ic.mask(lambda d: d.ellipse((5, 18, 19, 30), fill=255))
    ic.fill(root, VOID_DARK, noise=6, seed=110)
    for i, poly in enumerate(([(10, 20), (13, 18), (18, 9), (21, 3), (16, 8), (11, 15)],
                              [(13, 22), (16, 21), (23, 14), (29, 10), (22, 12), (15, 18)],
                              [(9, 21), (11, 18), (8, 11), (6, 4), (5, 10), (6, 17)])):
        m = ic.mask(lambda d, p=poly: d.polygon(p, fill=255))
        ic.fill(m, (206, 196, 234), light=0.3, dark=0.32, noise=5, seed=111 + i)
    ic.line([(14, 16), (18, 9), (20, 5)], VOID_PINK)
    ic.line([(16, 19), (22, 14), (27, 11)], VOID_PINK)
    ic.line([(9, 17), (7, 10)], VOID_PINK)
    ic.put(12, 23, (255, 150, 240))
    return [(27, 4, VOID_LIGHT), (28, 22, (230, 190, 255))]


def void_behemoth(ic):
    # 공허 수정: 검보라 수정 다발 (가운데 큰 것 · 양옆) · 안쪽 분홍 빛 줄 · 위 끝이 밝음
    for i, (poly, base) in enumerate((([(5, 28), (9, 28), (10, 16), (7, 11), (4, 17)], (110, 64, 200)),
                                      ([(21, 28), (26, 28), (28, 18), (25, 12), (21, 18)], (120, 70, 210)),
                                      ([(10, 29), (21, 29), (21, 12), (16, 2), (11, 12)], (142, 82, 236)))):
        m = ic.mask(lambda d, p=poly: d.polygon(p, fill=255))
        ic.fill(m, base, light=0.4, dark=0.35, noise=5, seed=120 + i)
    ic.line([(16, 5), (16, 26)], (220, 170, 255))
    ic.line([(13, 14), (14, 26)], VOID_PINK)
    ic.line([(19, 14), (18, 25)], (200, 110, 255))
    ic.line([(25, 15), (24, 26)], (200, 120, 255))
    ic.line([(7, 14), (7, 26)], (190, 110, 250))
    ic.put(16, 3, (255, 255, 255))
    ic.put(15, 4, (250, 230, 255))
    base = ic.mask(lambda d: d.ellipse((4, 26, 28, 31), fill=255))
    ic.fill(base, (40, 28, 66), noise=4, seed=124)
    return [(4, 6, VOID_LIGHT), (28, 8, (230, 200, 255)), (27, 27, (255, 180, 245))]


PAINT = {"nisse": nisse, "boar": boar, "sand_scorpion": sand_scorpion, "crab": crab, "worm_larva": worm_larva, "grendel": grendel,
         "frost_wolf": frost_wolf, "frost_giant": frost_giant, "void_leaper": void_leaper, "void_stalker": void_stalker, "void_jelly": void_jelly,
         "void_borer": void_borer, "void_behemoth": void_behemoth}


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def main():
    more = json.load(open(MORE, encoding="utf-8"))
    drops = more["MONSTER_DROPS"]
    mats_path = os.path.join(RES, "mmorpg", "materials.json")
    mats = json.load(open(mats_path, encoding="utf-8"))
    for key, d in drops.items():
        if key.startswith("_"):
            continue
        glow = tuple(clamp(v * 255) for v in d["rgb"])
        ic = Icon(glow)
        sparkles = PAINT[key](ic)
        name = d["item"].split(":")[1]
        ic.render(sparkles).save(os.path.join(RES, "rpg", "textures", "item", "material", f"{name}.png"))
        write_json(os.path.join(RES, "rpg", "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
        write_json(os.path.join(RES, "rpg", "models", "item", f"{name}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/material/{name}"}})
        mats[d["item"]] = {"name": f"{d['color']}{d['name']}§r§7 (전리품)", "glint": False, "stack": 64}
    write_json(mats_path, mats)
    print("loot icons:", len([k for k in drops if not k.startswith('_')]))


if __name__ == "__main__":
    main()
