#!/usr/bin/env python3
"""0.23.0 RPG 창 그림 (어둠의 금테 — 스탯 창 · 대화창과 같은 남색 + 금테): 인벤토리 · 상자 창 테두리 · 칸 · 모델 상자 · 화살표 · 단추 · 단추 그림.

tools/rpg_ui_art.py  →  src/main/resources/assets/mmorpg/textures/gui/sprites/rpgui/<이름>.png (+ .png.mcmeta: nine_slice)

- frame (32 × 32, 테두리 8): 검은 바깥선 · 짙은 금 · 금 (위 · 왼쪽 밝게, 아래 · 오른쪽 어둡게) · 안쪽 짙은 선 · 옅은 안쪽 선, 네 모서리 금 마름모 (가운데 붉은 보석).
  가운데는 비움 — 창 바탕 (남색 세로 그라데이션) 은 코드가 칠함 (RpgUi.panel).
- frame_small (16 × 16, 테두리 5 — 그림의 테는 4줄, 5번째 투명 줄까지 테두리로 잡아 타일 이음매가 보이지 않게): 모서리 장식 없는 금테 (상자 탭 · 작은 창).
- slot (18 × 18): 어두운 파인 칸 (위 · 왼쪽 짙게, 아래 · 오른쪽 밝게).
- model_bg (49 × 70): 인벤토리 사람 그림 칸 — 가운데가 밝은 남빛 · 금 테 · 발밑 금빛.
- arrow (16 × 13): 제작 화살표 (금).
- button · button_hover · button_press (16 × 16, 테두리 4): 남색 바탕 금테 단추.
- icon_sort · icon_deposit · icon_stack (16 × 16): 상자 정렬 · 모두 넣기 · 같은 종류 넣기.
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "rpgui")

BLACK = (0, 0, 0, 255)
GOLD_DARK = (120, 88, 34, 255)
GOLD = (214, 170, 80, 255)
GOLD_LIGHT = (246, 216, 140, 255)
GOLD_LEFT = (240, 206, 128, 255)
GOLD_SHADE = (176, 134, 58, 255)
INNER_DARK = (70, 52, 22, 255)
INNER_LINE = (56, 52, 74, 255)
GEM_EDGE = (60, 40, 10, 255)
GEM_RED = (255, 90, 70, 255)
GEM_HI = (255, 236, 180, 255)
CLEAR = (0, 0, 0, 0)


def save(img, name, nine=None):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    meta = path + ".mcmeta"
    if nine:
        w, h = img.size
        with open(meta, "w", encoding="utf-8") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": nine}}}, f)
            f.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)


def bevel_frame(n, inner, gems):
    """n × n 금테 (e = 가장자리에서 떨어진 칸): 0 검정 · 1 짙은 금 · 2 금 (빛) · 3 안쪽 짙은 선 · inner 옅은 선"""
    img = Image.new("RGBA", (n, n), CLEAR)
    px = img.load()
    last = n - 1
    for y in range(n):
        for x in range(n):
            e = min(x, y, last - x, last - y)
            if e == 0:
                px[x, y] = BLACK
            elif e == 1:
                px[x, y] = GOLD_DARK
            elif e == 2:
                if y == 2 and x < last - 2:
                    px[x, y] = GOLD_LIGHT
                elif x == 2 and y < last - 2:
                    px[x, y] = GOLD_LEFT
                elif y == last - 2 or x == last - 2:
                    px[x, y] = GOLD_SHADE
                else:
                    px[x, y] = GOLD
            elif e == 3:
                px[x, y] = INNER_DARK
            elif inner and e == inner:
                px[x, y] = INNER_LINE
    if gems:
        for cx, cy in ((3, 3), (last - 3, 3), (3, last - 3), (last - 3, last - 3)):
            for dy in range(-3, 4):
                for dx in range(-3, 4):
                    d = abs(dx) + abs(dy)
                    if d == 3:
                        px[cx + dx, cy + dy] = GEM_EDGE
                    elif d < 3:
                        px[cx + dx, cy + dy] = GOLD if d == 2 else GOLD_LIGHT
            px[cx, cy] = GEM_RED
            px[cx - 1, cy] = (255, 150, 120, 255)
            px[cx, cy - 1] = GEM_HI
    return img


def slot():
    img = Image.new("RGBA", (18, 18), (18, 19, 31, 255))
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (17, 0)], fill=(6, 6, 10, 255))
    d.line([(0, 0), (0, 17)], fill=(6, 6, 10, 255))
    d.line([(1, 17), (17, 17)], fill=(70, 66, 92, 255))
    d.line([(17, 1), (17, 17)], fill=(70, 66, 92, 255))
    d.line([(1, 1), (16, 1)], fill=(12, 12, 20, 255))
    d.line([(1, 1), (1, 16)], fill=(14, 14, 23, 255))
    img.putpixel((17, 0), (38, 36, 50, 255))
    img.putpixel((0, 17), (38, 36, 50, 255))
    return img


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def model_bg():
    w, h = 49, 70
    img = Image.new("RGBA", (w, h), BLACK)
    px = img.load()
    cx, cy = (w - 1) / 2, h * 0.42
    for y in range(h):
        for x in range(w):
            t = min(1.0, (((x - cx) / (w * 0.78)) ** 2 + ((y - cy) / (h * 0.72)) ** 2) ** 0.5)
            px[x, y] = mix((52, 58, 104), (10, 11, 20), t) + (255,)
    # 발밑 금빛 (타원)
    for y in range(h - 9, h - 1):
        for x in range(1, w - 1):
            t = ((x - cx) / 17.5) ** 2 + ((y - (h - 5)) / 3.2) ** 2
            if t < 1:
                a = 0.32 * (1 - t)
                r, g, b, _ = px[x, y]
                px[x, y] = mix((r, g, b), (214, 170, 80), a) + (255,)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(150, 116, 52, 255))
    d.point((0, 0), fill=(70, 52, 22, 255))
    d.point((w - 1, 0), fill=(70, 52, 22, 255))
    d.point((0, h - 1), fill=(70, 52, 22, 255))
    d.point((w - 1, h - 1), fill=(70, 52, 22, 255))
    return img


def arrow():
    img = Image.new("RGBA", (16, 13), CLEAR)
    d = ImageDraw.Draw(img)
    pts = [(0, 4), (8, 4), (8, 0), (14, 6), (8, 12), (8, 8), (0, 8)]
    d.polygon(pts, fill=GOLD, outline=BLACK)
    d.line([(1, 5), (8, 5)], fill=GOLD_LIGHT)
    d.line([(9, 2), (12, 5)], fill=GOLD_LIGHT)
    d.line([(1, 7), (8, 7)], fill=GOLD_SHADE)
    d.line([(9, 10), (12, 7)], fill=GOLD_SHADE)
    return img


def button(body, edge_light, edge, border_dark):
    n = 16
    img = Image.new("RGBA", (n, n), body + (255,))
    px = img.load()
    for y in range(n):
        for x in range(n):
            e = min(x, y, n - 1 - x, n - 1 - y)
            if e == 0:
                px[x, y] = BLACK
            elif e == 1:
                if y == 1 or x == 1:
                    px[x, y] = edge_light + (255,)
                else:
                    px[x, y] = edge + (255,)
            elif e == 2:
                px[x, y] = border_dark + (255,)
    # 위쪽 빛
    for x in range(3, n - 3):
        r, g, b, a = px[x, 3]
        px[x, 3] = mix((r, g, b), (255, 255, 255), 0.12) + (255,)
    return img


def outlined(img):
    """그림 둘레에 검은 1칸 테 (어두운 단추 위에서도 보이게)"""
    w, h = img.size
    src = img.load()
    out = Image.new("RGBA", (w, h), CLEAR)
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                    o[x, y] = (0, 0, 0, 220)
                    break
    out.alpha_composite(img)
    return out


def chest(d, top):
    """작은 상자 (y = top 부터 6칸)"""
    d.rectangle([2, top, 13, top + 5], fill=(150, 98, 40, 255))
    d.line([(2, top), (13, top)], fill=(196, 136, 64, 255))
    d.line([(2, top + 2), (13, top + 2)], fill=(92, 58, 22, 255))
    d.rectangle([7, top + 2, 8, top + 3], fill=GOLD_LIGHT)
    d.line([(2, top + 5), (13, top + 5)], fill=(92, 58, 22, 255))


def icon_sort():
    img = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(img)
    for i, ln in enumerate((9, 6, 3)):
        y = 2 + i * 4
        d.rectangle([1, y, 1 + ln, y + 1], fill=GOLD_LIGHT)
    d.rectangle([12, 1, 13, 10], fill=(255, 255, 255, 255))
    d.polygon([(10, 10), (15, 10), (12.5, 14)], fill=(255, 255, 255, 255))
    d.line([(12, 13), (13, 13)], fill=(255, 255, 255, 255))
    return outlined(img)


def icon_deposit():
    img = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(img)
    chest(d, 9)
    d.rectangle([7, 1, 8, 5], fill=(124, 230, 110, 255))
    d.line([(5, 5), (10, 5)], fill=(124, 230, 110, 255))
    d.line([(6, 6), (9, 6)], fill=(124, 230, 110, 255))
    d.line([(7, 7), (8, 7)], fill=(124, 230, 110, 255))
    return outlined(img)


def icon_stack():
    img = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(img)
    chest(d, 9)
    gem = (110, 220, 255, 255)
    for cx in (4, 11):
        d.polygon([(cx, 1), (cx + 2, 3), (cx, 5), (cx - 2, 3)], fill=gem)
        d.point((cx, 2), fill=(230, 250, 255, 255))
    d.line([(7, 4), (8, 4)], fill=(255, 255, 255, 255))
    d.line([(7, 5), (8, 5)], fill=(255, 255, 255, 255))
    d.line([(6, 6), (9, 6)], fill=(255, 255, 255, 255))
    d.line([(7, 7), (8, 7)], fill=(255, 255, 255, 255))
    return outlined(img)


def main():
    save(bevel_frame(32, 5, True), "frame", nine=8)
    # 테두리 5: 안쪽 금 테 (4번째 줄) 다음 줄 (투명) 까지 테두리로 — 가운데를 타일로 늘어놓을 때 이음매에서 안쪽 테 색이 새어 세로 · 가로 줄로 보이지 않게
    # (0.44.0 — 큰 창 (대장장이 상세) 에서 8칸마다 금빛 줄이 보였음)
    save(bevel_frame(16, 0, False), "frame_small", nine=5)
    save(slot(), "slot")
    save(model_bg(), "model_bg")
    save(arrow(), "arrow")
    save(button((28, 30, 48), (240, 206, 128), (176, 134, 58), (44, 34, 18)), "button", nine=4)
    save(button((50, 54, 86), (255, 230, 160), (214, 170, 80), (70, 52, 22)), "button_hover", nine=4)
    save(button((18, 19, 31), (150, 116, 52), (120, 88, 34), (30, 24, 12)), "button_press", nine=4)
    save(icon_sort(), "icon_sort")
    save(icon_deposit(), "icon_deposit")
    save(icon_stack(), "icon_stack")
    print("rpgui sprites →", OUT)


if __name__ == "__main__":
    main()
