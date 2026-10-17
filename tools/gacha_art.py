#!/usr/bin/env python3
"""룬 뽑기 그림 (0.14.0): 한정 룬 뽑기권 아이템 · 뽑기 창 / 연출 GUI 그림.

tools/gacha_art.py

- assets/rpg/textures/item/rune/limited_rune_ticket.png (룬 뽑기권 그림의 종이를 진홍으로 · 보라 고리를 금빛으로 · 반짝임)
  + assets/rpg/items/limited_rune_ticket.json · assets/rpg/models/item/limited_rune_ticket.json
- assets/mmorpg/textures/gui/sprites/gacha/ (GUI 그림 — 흰색 그림은 그릴 때 등급 색으로 칠함):
  circle_0 ~ 9 (마법진이 그려지는 차례 — 파티클 fx/magic_circle 그대로), glow (둥근 빛), rays (빛살), ring (빛 고리), star (네 갈래 반짝임),
  card (결과 카드 앞 — 흰 테두리 + 어두운 안쪽), card_back (카드 뒤 — 룬 문양 · 노란 선, 10회 영웅 이상), card_back_white (같은 그림 · 흰 선 — 0.14.1, 10회 영웅 미만),
  banner_limited · banner_normal · banner_advanced · banner_stat
  (뽑기 창 왼쪽 그림 판 — 성운 · 별 · 마법진), shine (카드 위를 지나가는 빛줄기)
같은 씨앗으로 늘 같은 그림 (다시 돌려도 바뀌지 않음).
"""
import json
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RPG = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
MM = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
OUT = os.path.join(MM, "textures", "gui", "sprites", "gacha")
FX = os.path.join(MM, "textures", "particle", "fx")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"), optimize=True)


# ---------------------------------------------------------------- 한정 룬 뽑기권


def limited_ticket():
    import colorsys
    base = Image.open(os.path.join(RPG, "textures", "item", "rune", "rune_ticket.png")).convert("RGBA")
    t = base.copy()
    w, h = t.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = t.getpixel((x, y))
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if 0.66 < hh < 0.86 and s > 0.18:  # 보라 고리 · 룬 글자 → 금빛
                nr, ng, nb = colorsys.hsv_to_rgb(0.12, min(1, s * 0.95 + 0.15), min(1, v * 1.1 + 0.05))
            elif s < 0.45 and v > 0.55:  # 크림색 종이 → 진홍 (밝은 곳은 장밋빛)
                nr, ng, nb = colorsys.hsv_to_rgb(0.965, 0.62 + 0.2 * (1 - v), 0.38 + 0.5 * (v - 0.55) / 0.45)
            elif 0.02 < hh < 0.16:  # 갈색 테두리 → 짙은 금
                nr, ng, nb = colorsys.hsv_to_rgb(0.1, min(1, s + 0.2), min(1, v * 1.05))
            else:
                nr, ng, nb = r / 255, g / 255, b / 255
            t.putpixel((x, y), (int(nr * 255), int(ng * 255), int(nb * 255), a))
    d = ImageDraw.Draw(t)
    # 반짝임 셋 (종이 위)
    for (sx, sy) in ((7, 11), (9, 20), (25, 9)):
        d.point((sx, sy), fill=(255, 250, 220, 255))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d.point((sx + dx, sy + dy), fill=(255, 214, 120, 190))
    path = os.path.join(RPG, "textures", "item", "rune", "limited_rune_ticket.png")
    t.save(path, optimize=True)
    write_json(os.path.join(RPG, "items", "limited_rune_ticket.json"), {"model": {"type": "minecraft:model", "model": "rpg:item/limited_rune_ticket"}})
    write_json(os.path.join(RPG, "models", "item", "limited_rune_ticket.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "rpg:item/rune/limited_rune_ticket"}})


# ---------------------------------------------------------------- 빛 그림 (흰색 — 칠해서 씀)


def radial(size, fn):
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / (size / 2)
            a = max(0.0, min(1.0, fn(r, math.atan2(y - c, x - c))))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    return img


def glow():
    save(radial(64, lambda r, t: (1 - r) ** 2.2 if r < 1 else 0), "glow")


def rays():
    n = 14

    def f(r, t):
        if r >= 1 or r < 0.08:
            return 0 if r >= 1 else 0.0
        k = (math.cos(t * n) + 1) / 2
        beam = k ** 6
        fade = (1 - r) ** 1.3 * min(1, (r - 0.08) / 0.12)
        return beam * fade

    save(radial(128, f), "rays")


def ring():
    save(radial(128, lambda r, t: math.exp(-((r - 0.82) / 0.06) ** 2) + 0.35 * math.exp(-((r - 0.82) / 0.16) ** 2)), "ring")


def star():
    def f(r, t):
        if r >= 1:
            return 0
        arms = max(abs(math.cos(t)), abs(math.sin(t)))
        arm = math.exp(-((1 - arms) / 0.035)) * (1 - r) ** 1.5
        core = (1 - min(1, r / 0.35)) ** 2
        return min(1, arm * 1.4 + core)

    save(radial(32, f), "star")


def shine():
    """카드 위를 비스듬히 지나가는 빛줄기 (흰색, 가운데가 밝음)"""
    w, h = 32, 128
    img = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            k = 1 - abs(x - (w - 1) / 2) / (w / 2)
            px[x, y] = (255, 255, 255, int(255 * max(0, k) ** 2.5 * 0.9))
    save(img, "shine")


def circles():
    for i in range(10):
        src = os.path.join(FX, f"magic_circle_{i}.png")
        Image.open(src).convert("RGBA").save(os.path.join(OUT, f"circle_{i}.png"), optimize=True)


# ---------------------------------------------------------------- 카드


def rounded(w, h, r, fill):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=r, fill=fill)
    return img


def card():
    w, h = 64, 88
    img = rounded(w, h, 6, (255, 255, 255, 255))
    inner = Image.new("RGBA", (w - 6, h - 6), (0, 0, 0, 0))
    ip = inner.load()
    for y in range(h - 6):
        for x in range(w - 6):
            t = y / (h - 7)
            v = int(28 + 26 * (1 - t))  # 위가 조금 밝음
            ip[x, y] = (v, v, v + 6, 255)
    mask = rounded(w - 6, h - 6, 4, (255, 255, 255, 255)).split()[3]
    img.paste(inner, (3, 3), mask)
    d = ImageDraw.Draw(img)
    # 안쪽 가는 금 · 마름모 무늬
    d.rounded_rectangle((5, 5, w - 6, h - 6), radius=3, outline=(190, 190, 190, 255))
    cx, cy = w // 2, h // 2 - 4
    for k in range(3):
        rr = 14 + k * 7
        d.polygon([(cx, cy - rr), (cx + rr, cy), (cx, cy + rr), (cx - rr, cy)], outline=(70 + k * 8, 70 + k * 8, 78 + k * 8, 255))
    # 위 반사광
    for y in range(4, 16):
        a = int(70 * (1 - (y - 4) / 12))
        d.line((6, y, w - 7, y), fill=(255, 255, 255, a))
    img = Image.alpha_composite(img, Image.new("RGBA", img.size, (0, 0, 0, 0)))
    save(img, "card")


def card_back(name="card_back", frame=(214, 176, 92), lines=(236, 196, 110), edge=(160, 128, 64)):
    """10회 카드 뒤 — 남보라 몸통 · 테두리 · 가운데 마법진 · 안쪽 테. 선 색만 다름 (0.14.1 — 사용자: 영웅 미만 흰 선 · 영웅 이상 노란 선):
    card_back = 노란 선 (영웅 이상 — 0.14.0 그대로), card_back_white = 흰 선 (영웅 미만). frame 테두리 · lines 마법진 · edge 안쪽 테"""
    w, h = 64, 88
    img = rounded(w, h, 6, frame + (255,))  # 테두리
    inner = Image.new("RGBA", (w - 6, h - 6), (0, 0, 0, 0))
    ip = inner.load()
    for y in range(h - 6):
        for x in range(w - 6):
            t = (x + y) / (w + h - 12)
            r = int(30 + 22 * t)
            g = int(18 + 10 * t)
            b = int(64 + 40 * (1 - t))
            ip[x, y] = (r, g, b, 255)
    mask = rounded(w - 6, h - 6, 4, (255, 255, 255, 255)).split()[3]
    img.paste(inner, (3, 3), mask)
    # 가운데 마법진 (선 색 — 파티클 그림을 줄여서)
    circ = Image.open(os.path.join(FX, "magic_circle_9.png")).convert("RGBA").resize((50, 50), Image.LANCZOS)
    tint = Image.new("RGBA", circ.size, lines + (0,))
    tint.putalpha(circ.split()[3].point(lambda a: int(a * 0.85)))
    img.alpha_composite(tint, ((w - 50) // 2, (h - 50) // 2))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((5, 5, w - 6, h - 6), radius=3, outline=edge + (255,))
    save(img, name)


# ---------------------------------------------------------------- 그림 판 (성운 · 별)


def nebula(w, h, c0, c1, c2, seed, circle_alpha=70):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.6 + (1 - y / h) * 0.4)
            r = int(c0[0] + (c1[0] - c0[0]) * t)
            g = int(c0[1] + (c1[1] - c0[1]) * t)
            b = int(c0[2] + (c1[2] - c0[2]) * t)
            px[x, y] = (r, g, b, 255)
    # 구름 (흐린 덩어리)
    cloud = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cloud)
    for _ in range(26):
        cx = rnd.uniform(0, w)
        cy = rnd.uniform(0, h)
        rr = rnd.uniform(14, 44)
        a = rnd.randint(18, 46)
        col = c2 if rnd.random() < 0.6 else c1
        cd.ellipse((cx - rr * 1.6, cy - rr, cx + rr * 1.6, cy + rr), fill=(col[0], col[1], col[2], a))
    cloud = cloud.filter(ImageFilter.GaussianBlur(9))
    img = Image.alpha_composite(img, cloud)
    # 오른쪽의 큰 마법진 (흐리게)
    if circle_alpha > 0:
        size = int(h * 1.15)
        circ = Image.open(os.path.join(FX, "magic_circle_9.png")).convert("RGBA").resize((size, size), Image.LANCZOS)
        tint = Image.new("RGBA", circ.size, (c2[0], c2[1], c2[2], 0))
        tint.putalpha(circ.split()[3].point(lambda a: int(a * circle_alpha / 255)))
        img.alpha_composite(tint, (w - size + size // 5, (h - size) // 2))
    # 빛줄기 (비스듬히)
    beams = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beams)
    for k in range(4):
        x0 = rnd.uniform(w * 0.2, w * 0.95)
        wd = rnd.uniform(6, 16)
        bd.polygon([(x0, -2), (x0 + wd, -2), (x0 - h * 0.5 + wd, h + 2), (x0 - h * 0.5, h + 2)], fill=(255, 240, 220, rnd.randint(10, 22)))
    beams = beams.filter(ImageFilter.GaussianBlur(3))
    img = Image.alpha_composite(img, beams)
    # 별
    d = ImageDraw.Draw(img)
    for _ in range(90):
        x = rnd.randrange(w)
        y = rnd.randrange(h)
        v = rnd.randint(120, 255)
        d.point((x, y), fill=(v, v, min(255, v + 10), 255))
    for _ in range(9):
        x = rnd.randrange(4, w - 4)
        y = rnd.randrange(4, h - 4)
        d.point((x, y), fill=(255, 255, 255, 255))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d.point((x + dx, y + dy), fill=(255, 255, 255, 140))
    # 가장자리를 조금 어둡게 (비네트)
    vig = radial(max(w, h), lambda r, t: min(1, max(0, (r - 0.55) / 0.6)) * 0.55)
    vig = vig.resize((w, h), Image.LANCZOS)
    dark = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dark.putalpha(vig.split()[3])
    img = Image.alpha_composite(img, dark)
    return img


def banners():
    w, h = 240, 150
    save(nebula(w, h, (58, 6, 26), (40, 12, 70), (255, 120, 90), 11), "banner_limited")
    save(nebula(w, h, (8, 16, 46), (22, 52, 104), (110, 170, 255), 12), "banner_normal")
    save(nebula(w, h, (30, 10, 46), (70, 40, 10), (255, 190, 90), 13), "banner_advanced")
    save(nebula(w, h, (6, 34, 34), (20, 64, 52), (120, 255, 200), 14, 50), "banner_stat")


def main():
    os.makedirs(OUT, exist_ok=True)
    limited_ticket()
    circles()
    glow()
    rays()
    ring()
    star()
    shine()
    card()
    card_back()
    card_back("card_back_white", (236, 238, 246), (248, 250, 255), (150, 156, 178))
    banners()
    print("gacha art:", len([f for f in os.listdir(OUT) if f.endswith(".png")]), "sprites + limited ticket")


if __name__ == "__main__":
    main()
