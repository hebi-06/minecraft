#!/usr/bin/env python3
"""모드 없는 클라이언트의 HUD 그림 (VanillaHud — 액션바 글자로 그림, 2026-11-08 로드맵 2번):
모드 HUD (RpgHud) 와 같은 그림 — 하트 · 체력 틀 · 채움 · 25% 아래 빨간 테 · 방어 배지 · Lv 배지 · 포인트.

python3 tools/vanilla_hud.py   → src/main/resources/mmorpg/vanilla/gui/hud/*.png + hud.json

액션바 글자 줄의 위 = 화면 아래 − 72 (바닐라 Gui: 높이 − 68 에서 −4). 비트맵 글자의 위 = 글자 줄 위 + 7 − ascent
→ 화면 아래에서 k 위에 그림 위를 두려면 ascent = k − 65 (음수 — 글자 줄보다 아래).
그림 위치 (화면 가운데 cx · 아래 h 기준, 모드 RpgHud 와 같음):
  하트 (cx − 123, h − 44) 11 × 11 · 틀 (cx − 109, h − 46) 96 × 14 · 채움 (cx − 108, h − 45) ≤ 94 × 12 · 빨간 테 (cx − 111, h − 48) 100 × 18
  방어 배지 (cx + 12, h − 45) 29 × 13 · 방어 그림 (cx + 15, h − 43) 9 × 9 · Lv 배지 (cx + 44, h − 45) 너비 34 ~ 46 × 13 · 포인트 (·, h − 47) 16 × 16
hud.json: providers (글꼴 rpg:hud 의 제공자 목록 그대로) · glyphs {이름: [글자, 나아감]} · strips (조각 너비 → 이름)
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "hud")
OUT = os.path.join(ROOT, "src", "main", "resources", "mmorpg", "vanilla", "gui", "hud")
NS = "rpg"

FILL_W = 94          # 채움 최대 너비
STRIPS = [1, 2, 4, 8, 16, 32, 64]
LV_WIDTHS = [34, 38, 42, 46]

# 화면 아래에서 그림 위까지 (k) — ascent = k − 65
TOP = {
    "hp_icon": 44, "hp_frame": 46, "hp_warn": 48, "fill": 45, "trail": 45, "absorb": 35,
    "armor_badge": 45, "armor_icon": 43, "lv_badge": 45, "point": 47,
}


def ascent(name):
    return TOP[name] - 65


def advance(img):
    """비트맵 글자의 나아감 = 가장 오른쪽 불투명 열 + 1 + 1 (마인크래프트 규칙 — 크기 1배)"""
    a = img.getchannel("A")
    bb = a.getbbox()
    return 1 if not bb else bb[2] + 1


def nine_slice(src, w, h, border):
    """nine_slice 늘리기 (모서리 그대로, 가장자리 · 가운데는 늘림)"""
    sw, sh = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    b = border

    def part(x0, y0, x1, y1, dx0, dy0, dx1, dy1):
        if x1 <= x0 or y1 <= y0 or dx1 <= dx0 or dy1 <= dy0:
            return
        piece = src.crop((x0, y0, x1, y1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST)
        out.alpha_composite(piece, (dx0, dy0))

    xs = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
    ys = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
    for (sx0, sx1, dx0, dx1) in xs:
        for (sy0, sy1, dy0, dy1) in ys:
            part(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1)
    return out


class Chars:
    """글자 차례 (U+E400 부터)"""

    def __init__(self, start=0xE400):
        self.next = start

    def take(self, n=1):
        out = "".join(chr(self.next + i) for i in range(n))
        self.next += n
        return out


def sheet(images, cell_w, cell_h, cols):
    rows = (len(images) + cols - 1) // cols
    im = Image.new("RGBA", (cell_w * cols, cell_h * rows), (0, 0, 0, 0))
    for i, g in enumerate(images):
        im.alpha_composite(g, ((i % cols) * cell_w, (i // cols) * cell_h))
    return im, rows


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    chars = Chars()
    providers = []
    glyphs = {}

    def single(name, img, top_key, file=None):
        file = file or name
        img.save(os.path.join(OUT, file + ".png"))
        ch = chars.take()
        providers.append({"type": "bitmap", "file": f"{NS}:gui/hud/{file}.png", "height": img.size[1], "ascent": ascent(top_key), "chars": [ch]})
        glyphs[name] = [ch, advance(img)]

    def src(name):
        return Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")

    single("hp_icon", src("hp_icon"), "hp_icon")
    single("hp_frame", src("hp_frame"), "hp_frame")
    single("hp_warn", src("hp_warn"), "hp_warn")
    single("armor_icon", src("armor_icon"), "armor_icon")
    single("point", src("icon_point"), "point")
    single("armor_badge", nine_slice(src("armor_badge"), 29, 13, 3), "armor_badge")
    for w in LV_WIDTHS:
        single("lv_badge_%d" % w, nine_slice(src("lv_badge"), w, 13, 3), "lv_badge")

    # 채움: 왼쪽부터 너비 1 ~ 94 (마디 줄이 있어서 조각으로 잇지 않고 너비마다 한 글자)
    fill = src("hp_fill")
    crops = []
    for w in range(1, FILL_W + 1):
        g = Image.new("RGBA", (FILL_W, 12), (0, 0, 0, 0))
        g.alpha_composite(fill.crop((0, 0, w, 12)), (0, 0))
        crops.append(g)
    im, rows = sheet(crops, FILL_W, 12, 10)
    im.save(os.path.join(OUT, "hud_fill.png"))
    cs = chars.take(len(crops))
    lines = []
    for r in range(rows):
        row = cs[r * 10:(r + 1) * 10]
        lines.append(row + "\u0000" * (10 - len(row)))
    providers.append({"type": "bitmap", "file": f"{NS}:gui/hud/hud_fill.png", "height": 12, "ascent": ascent("fill"), "chars": lines})
    for i, ch in enumerate(cs):
        glyphs["fill_%d" % (i + 1)] = [ch, i + 2]

    # 흰 조각 (글자 색으로 물들임): 깎인 부분 (12 높이) · 흡수 (아래 3 높이)
    for key, h in (("trail", 12), ("absorb", 3)):
        pieces = []
        for w in STRIPS:
            g = Image.new("RGBA", (max(STRIPS), h), (0, 0, 0, 0))
            for y in range(h):
                for x in range(w):
                    shade = 255 if (key == "absorb" or y > 0) else 235
                    g.putpixel((x, y), (shade, shade, shade, 255))
            pieces.append(g)
        im, _ = sheet(pieces, max(STRIPS), h, len(STRIPS))
        im.save(os.path.join(OUT, "hud_%s.png" % key))
        cs = chars.take(len(pieces))
        providers.append({"type": "bitmap", "file": f"{NS}:gui/hud/hud_{key}.png", "height": h, "ascent": ascent(key), "chars": [cs]})
        for w, ch in zip(STRIPS, cs):
            glyphs["%s_%d" % (key, w)] = [ch, w + 1]

    # 바닐라 하트 · 갑옷 · 배고픔 그림을 덮을 투명 그림 (VanillaPack — minecraft:hud/heart/* · hud/armor_* · hud/food_*)
    Image.new("RGBA", (9, 9), (0, 0, 0, 0)).save(os.path.join(OUT, "blank9.png"))
    meta = {"providers": providers, "glyphs": glyphs, "strips": STRIPS, "lv_widths": LV_WIDTHS, "fill_w": FILL_W}
    with open(os.path.join(OUT, "hud.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
    print("hud glyphs", len(glyphs), "providers", len(providers))


if __name__ == "__main__":
    main()
