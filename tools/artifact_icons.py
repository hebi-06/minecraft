#!/usr/bin/env python3
"""0.37.0 아티팩트 아이콘 (32×32 — 전리품 아이콘처럼: 둥근 빛 바탕 · 진한 테두리 · 왼쪽 위 빛 · 반짝임) + 아티팩트 창 그림.

종류 넷 (반지 · 목걸이 · 귀걸이 · 팔찌) × 등급 다섯 (은 · 청옥 · 자수정 · 황금 · 무스펠의) = 20:
- src/main/resources/assets/rpg/textures/item/artifact/<종류>_<등급>.png
- src/main/resources/assets/rpg/items/artifact_<종류>_<등급>.json · models/item/artifact_<종류>_<등급>.json (generated)
아티팩트 창 그림 (assets/mmorpg/textures/gui/sprites/artifact/):
- ghost_<종류>.png (16×16 — 빈 칸의 흐린 모양) · slot.png (24×24 — 보석 받침 칸) · stage.png (146×88 — 사람 그림 무대: 남빛 · 금테 · 마법진 · 바닥 빛)
등급 색은 규칙 GRADES 의 등급 보석 색과 같은 계열 (일반 회색 · 희귀 파랑 · 고귀 보라 · 영웅 주황 · 초월 빨강 — 초월은 수르트 재료라 업화 빛).

사용: python3 tools/artifact_icons.py [--preview 경로]  (--preview 는 20개를 크게 늘어놓은 그림도 저장)
"""
import importlib.util
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets")
_spec = importlib.util.spec_from_file_location("loot_icons", os.path.join(ROOT, "tools", "loot_icons.py"))
L = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(L)
Icon, clamp, mix, shade = L.Icon, L.clamp, L.mix, L.shade

TYPES = ["ring", "necklace", "earring", "bracelet"]
GRADES = ["common", "rare", "noble", "hero", "transcend"]

# 등급마다: 금속 · 금속 빛 · 보석 · 보석 빛 · 바탕 빛
PAL = {
    "common": dict(metal=(184, 190, 204), metal_hi=(240, 244, 252), gem=(206, 220, 236), gem_hi=(255, 255, 255), glow=(200, 200, 212)),
    "rare": dict(metal=(190, 198, 214), metal_hi=(244, 248, 255), gem=(52, 108, 236), gem_hi=(170, 210, 255), glow=(76, 139, 255)),
    "noble": dict(metal=(206, 186, 140), metal_hi=(252, 238, 196), gem=(150, 74, 236), gem_hi=(228, 184, 255), glow=(182, 108, 255)),
    "hero": dict(metal=(232, 176, 52), metal_hi=(255, 236, 150), gem=(236, 84, 34), gem_hi=(255, 196, 120), glow=(255, 154, 46)),
    "transcend": dict(metal=(104, 46, 44), metal_hi=(255, 150, 70), gem=(255, 72, 36), gem_hi=(255, 240, 196), glow=(255, 74, 61), vein=(255, 128, 40)),
}


def ellipse_ring(ic, box, inner):
    return ic.mask(lambda d: (d.ellipse(box, fill=255), d.ellipse(inner, fill=0)))


def gem(ic, cells, p, seed, facet=True):
    """보석: 칸들을 보석 색으로 · 가운데 아래쪽은 짙게, 왼쪽 위 빛 한 점 + 면 줄"""
    ic.fill(cells, p["gem"], light=0.45, dark=0.35, grad=0.3, noise=3, seed=seed)
    if not cells:
        return
    xs = [x for x, _ in cells]
    ys = [y for _, y in cells]
    cx = (min(xs) + max(xs)) / 2
    cy = (min(ys) + max(ys)) / 2
    if facet:
        for (x, y) in cells:
            if abs(x - cx) < 0.6 and y > cy:  # 세로 면 줄 (아래 반)
                ic.put(x, y, shade(p["gem"], 0.75))
    hx, hy = round(cx - (max(xs) - min(xs)) * 0.22), round(cy - (max(ys) - min(ys)) * 0.22)
    if (hx, hy) in cells:
        ic.put(hx, hy, p["gem_hi"])
    if (hx + 1, hy) in cells:
        ic.put(hx + 1, hy, mix(p["gem_hi"], p["gem"], 0.4))


def metal(ic, cells, p, seed, grad=0.22):
    ic.fill(cells, p["metal"], light=0.35, dark=0.35, grad=grad, noise=4, seed=seed)
    if "vein" in p:  # 초월: 흑요석 금속 속을 흐르는 업화 줄 (사선 무늬 — 밝은 주황)
        for (x, y) in cells:
            k = (x * 3 + y * 5 + seed) % 13
            if k == 0:
                ic.put(x, y, p["vein"])
            elif k == 1:
                ic.put(x, y, mix(p["vein"], p["metal"], 0.55))


def embers(ic, pts, p):
    """초월: 위로 오르는 불티 (밝은 주황 · 노랑)"""
    for i, (x, y) in enumerate(pts):
        ic.put(x, y, (255, 214, 120) if i % 2 == 0 else (255, 140, 52))


# ---------------------------------------------------------------- 모양


def ring(ic, p, g):
    # 고리: 비스듬한 타원 띠 (뒤쪽 위 반은 어둡게 — 보석 받침 뒤로 지나감) + 위 가운데 받침 · 보석
    band = ellipse_ring(ic, (4, 13, 28, 30), (8, 17, 24, 27))
    metal(ic, band, p, 1)
    back = {(x, y) for (x, y) in band if y < 18}
    for (x, y) in back:
        ic.put(x, y, shade(ic.px[y][x], 0.72))
    # 띠 앞쪽 빛 줄
    for x in range(9, 23):
        y = round(29.2 - 0.0 * x)
        if (x, 28) in band:
            ic.put(x, 28, mix(p["metal_hi"], p["metal"], 0.35))
    # 받침 (발톱 넷) + 보석
    setting = ic.mask(lambda d: d.polygon([(10, 12), (13, 7), (19, 7), (22, 12), (19, 16), (13, 16)], fill=255))
    metal(ic, setting, p, 2, grad=0.1)
    stone = ic.mask(lambda d: d.polygon([(16, 3), (21, 8), (19, 14), (13, 14), (11, 8)], fill=255))
    gem(ic, stone, p, 3)
    for (x, y) in ((11, 8), (21, 8), (13, 14), (19, 14)):  # 발톱 끝
        ic.put(x, y, p["metal_hi"])
    sp = [(26, 6, p["gem_hi"]), (6, 9, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 1), (14, 2), (19, 2), (12, 4), (21, 4)], p)
        sp.append((28, 3, (255, 220, 140)))
    return sp


def necklace(ic, p, g):
    # 사슬: 위 두 귀퉁이에서 가운데 아래로 내려오는 U (고리 줄 — 밝은 · 어두운 마디), 아래 가운데 펜던트 (받침 + 물방울 보석)
    pts = []
    for k in range(0, 41):
        t = k / 40
        x = 4 + 24 * t
        y = 3 + 15 * (1 - (2 * t - 1) ** 2) ** 0.9
        pts.append((round(x), round(y)))
    seen = set()
    for i, (x, y) in enumerate(pts):
        if (x, y) in seen:
            continue
        seen.add((x, y))
        c = p["metal_hi"] if (i // 2) % 2 == 0 else shade(p["metal"], 0.8)
        ic.put(x, y, c)
        ic.put(x, y + 1, shade(p["metal"], 0.62))
    # 고리 (사슬 → 펜던트)
    bail = ic.mask(lambda d: d.ellipse((14, 16, 18, 21), fill=255))
    metal(ic, bail, p, 4, grad=0.05)
    frame = ic.mask(lambda d: d.polygon([(16, 19), (22, 24), (21, 29), (16, 31), (11, 29), (10, 24)], fill=255))
    metal(ic, frame, p, 5, grad=0.12)
    stone = ic.mask(lambda d: d.polygon([(16, 20), (20, 24), (19, 28), (16, 29), (13, 28), (12, 24)], fill=255))
    gem(ic, stone, p, 6)
    sp = [(27, 13, p["gem_hi"]), (5, 14, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 17), (13, 19), (20, 19), (11, 22), (22, 22)], p)
        sp.append((25, 25, (255, 220, 140)))
    return sp


def earring(ic, p, g):
    # 고리 (가는 갈고리) → 작은 구슬 → 큰 물방울 보석 (받침 테), 옆에 작은 짝 귀걸이 (뒤 — 어둡게)
    hook = [(17, 2), (18, 2), (19, 3), (19, 4), (19, 5), (18, 6), (17, 7), (16, 8)]
    for (x, y) in hook:
        ic.put(x, y, p["metal_hi"])
        ic.put(x + 1, y + 1, shade(p["metal"], 0.65))
    bead = ic.mask(lambda d: d.ellipse((13, 7, 19, 13), fill=255))
    metal(ic, bead, p, 7, grad=0.1)
    frame = ic.mask(lambda d: d.polygon([(16, 12), (22, 19), (22, 25), (19, 29), (13, 29), (10, 25), (10, 19)], fill=255))
    metal(ic, frame, p, 8, grad=0.12)
    stone = ic.mask(lambda d: d.polygon([(16, 13), (21, 19), (21, 24), (18, 28), (14, 28), (11, 24), (11, 19)], fill=255))
    gem(ic, stone, p, 9)
    # 짝 (작게 · 왼쪽 뒤)
    small = ic.mask(lambda d: d.polygon([(6, 12), (9, 16), (9, 20), (6, 22), (3, 20), (3, 16)], fill=255))
    small = {c for c in small if ic.px[c[1]][c[0]] is None}
    ic.fill(small, shade(p["gem"], 0.78), light=0.3, dark=0.3, noise=3, seed=10)
    for (x, y) in ((6, 9), (6, 10), (6, 11)):
        ic.put(x, y, shade(p["metal"], 0.85))
    sp = [(26, 8, p["gem_hi"]), (27, 27, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 11), (23, 14), (9, 13), (24, 18), (8, 26)], p)
        sp.append((4, 4, (255, 220, 140)))
    return sp


def bracelet(ic, p, g):
    # 넓은 띠 (비스듬한 굵은 타원) + 앞쪽 보석 셋 · 띠의 무늬 줄
    band = ellipse_ring(ic, (2, 8, 30, 28), (7, 12, 25, 23))
    metal(ic, band, p, 11, grad=0.25)
    for (x, y) in band:
        if y < 13:
            ic.put(x, y, shade(ic.px[y][x], 0.7))
    # 무늬 줄 (띠 가운데 — 앞쪽)
    for k in range(0, 31):
        t = k / 30 * math.pi
        x = round(16 - 12.2 * math.cos(t))
        y = round(18 + 7.6 * math.sin(t))
        if (x, y) in band:
            ic.put(x, y, mix(p["metal_hi"], p["metal"], 0.25))
    stones = [(8, 21), (16, 24), (24, 21)]
    for i, (cx, cy) in enumerate(stones):
        sm = ic.mask(lambda d, cx=cx, cy=cy: d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=255))
        gem(ic, sm, p, 12 + i, facet=False)
        ic.put(cx - 1, cy - 1, p["gem_hi"])
    sp = [(28, 6, p["gem_hi"]), (3, 30, mix(p["gem_hi"], (255, 255, 255), 0.5))]
    if g == "transcend":
        embers(ic, [(16, 7), (12, 6), (20, 6), (9, 8), (23, 8), (16, 4)], p)
        sp.append((5, 4, (255, 220, 140)))
    return sp


PAINT = {"ring": ring, "necklace": necklace, "earring": earring, "bracelet": bracelet}


def icon(t, g):
    p = PAL[g]
    ic = Icon(p["glow"])
    sparkles = PAINT[t](ic, p, g)
    im = ic.render(sparkles)
    if g == "transcend":  # 업화: 바깥 테두리 바로 밖에 옅은 붉은 불빛
        px = im.load()
        for y in range(32):
            for x in range(32):
                if px[x, y][3] == 255 and px[x, y][:3] == L.OUT_DARK[:3]:
                    px[x, y] = (70, 16, 14, 255)
    return im


# ---------------------------------------------------------------- 창 그림


def ghost(t):
    """빈 칸의 흐린 모양 (16×16 — 흰 선 · 투명 바탕, 창이 회색 · 반투명으로 칠함)"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    w = (255, 255, 255, 255)
    if t == "ring":
        d.ellipse((2, 6, 13, 15), outline=w)
        d.ellipse((3, 7, 12, 14), outline=(255, 255, 255, 110))
        d.polygon([(7, 1), (10, 4), (7, 7), (4, 4)], outline=w)
    elif t == "necklace":
        pts = [(1 + 14 * k / 12, 1 + 7 * (1 - (2 * k / 12 - 1) ** 2)) for k in range(13)]
        d.line([(round(x), round(y)) for x, y in pts], fill=w)
        d.polygon([(7, 9), (10, 12), (7, 15), (4, 12)], outline=w)
        d.point((7, 8), fill=w)
    elif t == "earring":
        d.arc((6, 0, 10, 6), 180, 360, fill=w)
        d.line([(10, 3), (8, 6)], fill=w)
        d.ellipse((6, 6, 9, 8), outline=w)
        d.polygon([(7, 8), (11, 12), (9, 15), (6, 15), (4, 12)], outline=w)
    else:
        d.ellipse((0, 3, 15, 13), outline=w)
        d.ellipse((3, 5, 12, 11), outline=(255, 255, 255, 110))
        for x in (3, 7, 11):
            d.rectangle((x, 11, x + 1, 12), fill=w)
    return im


def slot_sprite():
    """보석 받침 칸 (24×24): 어두운 안쪽 · 금테 · 네 모서리 작은 못"""
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    px = im.load()
    for y in range(24):
        for x in range(24):
            edge = min(x, y, 23 - x, 23 - y)
            if edge == 0:
                c = (20, 16, 10, 255)
            elif edge == 1:
                c = (214, 170, 80, 255) if (x + y) % 23 else (255, 222, 140, 255)
                if x == 1 or y == 1:
                    c = (236, 196, 104, 255)
                else:
                    c = (150, 110, 46, 255)
            elif edge == 2:
                c = (40, 34, 26, 255)
            else:
                d = math.hypot(x - 11.5, y - 11.5) / 13
                c = (*mix((34, 38, 66), (12, 13, 24), min(1, d)), 255)
            px[x, y] = c
    for (x, y) in ((2, 2), (21, 2), (2, 21), (21, 21)):
        px[x, y] = (255, 230, 160, 255)
    return im


def stage_sprite(w=146, h=88):
    """사람 그림 무대: 남빛 둥근 빛 (가운데 밝게) · 금테 · 가운데 뒤 마법진 (옅은 금빛 원 둘 + 룬 점) · 아래 바닥 빛 (타원)"""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    cx, cy = w / 2, h * 0.46
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / (w * 0.62), (y - cy) / (h * 0.75))
            c = mix((44, 50, 104), (12, 13, 26), min(1, d))
            px[x, y] = (*c, 255)
    # 마법진 (가운데 뒤)
    rnd = random.Random(7)
    for r, a in ((36, 70), (31, 46)):
        for k in range(720):
            t = k / 720 * math.tau
            x = round(cx + r * math.cos(t))
            y = round(cy + r * 0.92 * math.sin(t))
            if 0 <= x < w and 0 <= y < h:
                o = px[x, y]
                px[x, y] = (*mix(o[:3], (236, 196, 104), a / 255), 255)
    for k in range(12):  # 룬 점
        t = k / 12 * math.tau + 0.13
        x = round(cx + 33.5 * math.cos(t))
        y = round(cy + 33.5 * 0.92 * math.sin(t))
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            if 0 <= x + dx < w and 0 <= y + dy < h:
                o = px[x + dx, y + dy]
                px[x + dx, y + dy] = (*mix(o[:3], (255, 226, 150), 0.55 if rnd.random() < 0.5 else 0.4), 255)
    # 바닥 빛
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / 30, (y - (h - 9)) / 4.2)
            if d < 1:
                o = px[x, y]
                px[x, y] = (*mix(o[:3], (236, 190, 110), (1 - d) ** 1.5 * 0.55), 255)
    # 금테
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                px[x, y] = (150, 110, 46, 255) if (x == w - 1 or y == h - 1) else (236, 196, 104, 255)
            elif edge == 1:
                px[x, y] = (24, 20, 14, 255)
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    tex = os.path.join(RES, "rpg", "textures", "item", "artifact")
    os.makedirs(tex, exist_ok=True)
    icons = {}
    for t in TYPES:
        for g in GRADES:
            im = icon(t, g)
            icons[(t, g)] = im
            im.save(os.path.join(tex, f"{t}_{g}.png"))
            name = f"artifact_{t}_{g}"
            write_json(os.path.join(RES, "rpg", "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"rpg:item/{name}"}})
            write_json(os.path.join(RES, "rpg", "models", "item", f"{name}.json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/artifact/{t}_{g}"}})
    gui = os.path.join(RES, "mmorpg", "textures", "gui", "sprites", "artifact")
    os.makedirs(gui, exist_ok=True)
    for t in TYPES:
        ghost(t).save(os.path.join(gui, f"ghost_{t}.png"))
    slot_sprite().save(os.path.join(gui, "slot.png"))
    stage_sprite().save(os.path.join(gui, "stage.png"))
    if preview:
        k = 6
        sheet = Image.new("RGBA", (len(GRADES) * (32 * k + 12) + 12, len(TYPES) * (32 * k + 12) + 12 + 120), (46, 46, 52, 255))
        for i, t in enumerate(TYPES):
            for j, g in enumerate(GRADES):
                im = icons[(t, g)].resize((32 * k, 32 * k), Image.NEAREST)
                sheet.paste(im, (12 + j * (32 * k + 12), 12 + i * (32 * k + 12)), im)
        y0 = 12 + len(TYPES) * (32 * k + 12)
        for i, t in enumerate(TYPES):
            g = ghost(t).resize((16 * 6, 16 * 6), Image.NEAREST)
            sheet.paste(g, (12 + i * 110, y0), g)
        s = slot_sprite().resize((24 * 4, 24 * 4), Image.NEAREST)
        sheet.paste(s, (12 + 4 * 110, y0), s)
        sheet.save(preview)
        st = stage_sprite()
        st.resize((st.width * 4, st.height * 4), Image.NEAREST).save(preview.replace(".png", "_stage.png"))
    print("artifact icons:", len(icons), "· gui sprites: ghost ×4 · slot · stage")


if __name__ == "__main__":
    main()
