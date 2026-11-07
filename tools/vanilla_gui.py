"""모드 없는 클라이언트의 창 그림 (1번 4b — 사용자 "텍스처랑 넣고 … 최대한 비슷하게"): 상자 메뉴 제목 글자로 그리는 바탕 · 글자.

바닐라 상자 메뉴는 제목 (글자) 을 칸 아이템보다 먼저 그림 → 리소스 팩 글꼴의 큰 그림 글자 하나를 제목 자리에 두면 창 전체의 바탕이 되고,
뒤로 가는 빈 글자 (space) 로 자리를 옮겨 그 위에 글자를 씀. 세로 자리는 글꼴마다 다른 ascent (줄 글꼴 rpg:gl<d>) 로.

만드는 것 (src/main/resources/mmorpg/vanilla/gui — 모드 클라이언트가 읽는 assets 밖, VanillaPack 이 팩에만 넣음):
  text.png · text_chars.json · text_widths.json — 갈무리9 (Galmuri9, SIL OFL 1.1 — quiple) 를 10px 로 그린 글자판
      (ASCII · 쓰는 기호 · 한글 KS X 1001 2350자 + 데이터에 나오는 글자). 칸 12 × 12, 기준선 10. 너비 = 오른쪽 끝 + 2 (마인크래프트 비트맵 글꼴 규칙)
  stats.png · skills.png — 스탯 창 · 스킬 칸 창 바탕 (모드 창과 같은 그림: 남색 바탕 · 금테 dialog_bg, 줄 띠, 칸 틀, 단추 dialog_btn)
  icons/*.png — 칸에 놓는 아이템 그림 (+ 단추 켜짐 · 꺼짐, 빈 그림)

python3 tools/vanilla_gui.py <Galmuri9.ttf>
"""
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "mmorpg", "vanilla", "gui")
SPRITES = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites")

CELL_W = 12
CELL_H = 12
BASE = 10  # 칸 안의 기준선 (위에서)
COLS = 64

GOLD = (200, 160, 80)
NAVY = (35, 38, 58)


def charset():
    chars = set(chr(c) for c in range(0x21, 0x7F))
    chars.update("°±²·»½×÷—…←→↔⇧−√∞≈≤≥②━▶◀◆◇◈◎★☆「」『』♥♡●○■□▲▼△▽")
    for b1 in range(0xB0, 0xC9):
        for b2 in range(0xA1, 0xFF):
            try:
                c = bytes([b1, b2]).decode("euc-kr")
            except UnicodeDecodeError:
                continue
            if "가" <= c <= "힣":
                chars.add(c)
    pat = re.compile("[가-힣]")
    for base in ["core/src/main/resources", "src/main/resources/mmorpg", "src/main/java", "core/src/main/java"]:
        for dp, _, fn in os.walk(os.path.join(ROOT, base)):
            for f in fn:
                if f.endswith((".json", ".java")):
                    try:
                        chars.update(pat.findall(open(os.path.join(dp, f), encoding="utf-8").read()))
                    except (OSError, UnicodeDecodeError):
                        pass
    return chars


def text_sheet(font_path):
    from fontTools.ttLib import TTFont
    cmap = TTFont(font_path).getBestCmap()
    font = ImageFont.truetype(font_path, 10)
    chars = sorted(c for c in charset() if ord(c) in cmap)
    rows = (len(chars) + COLS - 1) // COLS
    sheet = Image.new("RGBA", (COLS * CELL_W, rows * CELL_H), (0, 0, 0, 0))
    widths = {}
    lines = []
    for r in range(rows):
        row = chars[r * COLS:(r + 1) * COLS]
        row += ["\u0000"] * (COLS - len(row))
        lines.append("".join(row))
        for c_i, ch in enumerate(row):
            if ch == "\u0000":
                continue
            cell = Image.new("L", (CELL_W, CELL_H), 0)
            d = ImageDraw.Draw(cell)
            d.fontmode = "1"
            d.text((0, BASE), ch, font=font, fill=255, anchor="ls")
            px = cell.load()
            right = -1
            for x in range(CELL_W):
                if any(px[x, y] for y in range(CELL_H)):
                    right = x
            if right < 0:
                continue
            white = Image.new("RGBA", (CELL_W, CELL_H), (255, 255, 255, 0))
            white.putalpha(cell)
            sheet.paste(white, (c_i * CELL_W, r * CELL_H))
            widths[ch] = right + 2  # 마인크래프트: 그림 너비 (오른쪽 끝 + 1) + 글자 사이 1
    return sheet, lines, widths


def nine(path, w, h, border):
    src = Image.open(path).convert("RGBA")
    sw, sh = src.size
    out = Image.new("RGBA", (w, h))
    b = border
    parts = {
        "tl": (0, 0, b, b), "t": (b, 0, sw - b, b), "tr": (sw - b, 0, sw, b),
        "l": (0, b, b, sh - b), "c": (b, b, sw - b, sh - b), "r": (sw - b, b, sw, sh - b),
        "bl": (0, sh - b, b, sh), "b": (b, sh - b, sw - b, sh), "br": (sw - b, sh - b, sw, sh),
    }
    def put(key, x, y, ww, hh):
        if ww <= 0 or hh <= 0:
            return
        tile = src.crop(parts[key]).resize((ww, hh), Image.NEAREST)
        out.paste(tile, (x, y), tile)
    put("tl", 0, 0, b, b)
    put("t", b, 0, w - 2 * b, b)
    put("tr", w - b, 0, b, b)
    put("l", 0, b, b, h - 2 * b)
    put("c", b, b, w - 2 * b, h - 2 * b)
    put("r", w - b, b, b, h - 2 * b)
    put("bl", 0, h - b, b, b)
    put("b", b, h - b, w - 2 * b, b)
    put("br", w - b, h - b, b, b)
    return out


def blend(img, box, rgba):
    over = Image.new("RGBA", (box[2] - box[0], box[3] - box[1]), rgba)
    img.alpha_composite(over, (box[0], box[1]))


def slot_frame(img, x, y, dark=True):
    """칸 틀 (18 × 18, x · y = 틀 왼쪽 위): 어두운 안 + 위 · 왼쪽 그림자 · 아래 · 오른쪽 밝은 선 (바닐라 칸 모양을 어두운 바탕에 맞춤)"""
    d = ImageDraw.Draw(img)
    inner = (24, 26, 40, 255) if dark else (139, 139, 139, 255)
    d.rectangle((x, y, x + 17, y + 17), fill=inner)
    d.line((x, y, x + 16, y), fill=(14, 15, 24, 255))
    d.line((x, y, x, y + 16), fill=(14, 15, 24, 255))
    d.line((x + 1, y + 17, x + 17, y + 17), fill=(78, 82, 110, 255))
    d.line((x + 17, y + 1, x + 17, y + 17), fill=(78, 82, 110, 255))


def button(img, x, y, w, h, gold=False):
    """단추 (모드 창의 dialog_btn 을 어두운 바탕에서도 보이게 — 밝은 테 · 옅은 안, gold = 주 단추 (금테))"""
    d = ImageDraw.Draw(img)
    blend(img, (x, y, x + w, y + h), (255, 255, 255, 0x16))
    edge = GOLD + (0xC0,) if gold else (255, 255, 255, 0x50)
    over = Image.new("RGBA", img.size, (0, 0, 0, 0))
    o = ImageDraw.Draw(over)
    o.rectangle((x, y, x + w - 1, y + h - 1), outline=edge)
    o.line((x + 1, y + 1, x + w - 2, y + 1), fill=(255, 255, 255, 0x28))
    img.alpha_composite(over)


def gold_line(img, y, x0=6, x1=170, alpha=0x90):
    blend(img, (x0, y, x1, y + 1), GOLD + (alpha,))


def opaque(img):
    """바탕은 완전히 불투명하게 (반투명이면 밑의 바닐라 상자 그림이 비침 · 오른쪽 끝 열이 비면 마인크래프트가 글자 너비를 줄여 잼):
    어두운 바탕 위에 겹침 (모드 창은 어둡게 덮인 화면 위에 반투명 남색 — 같은 색이 나오게)"""
    base = Image.new("RGBA", img.size, (10, 11, 18, 255))
    base.alpha_composite(img)
    return base


def cell_x(c):
    return 7 + 18 * c


def cell_y(r):
    return 17 + 18 * r if r < 6 else (139 + 18 * (r - 6) if r < 9 else 197)


def stats_bg():
    """스탯 창 (VanillaStats 와 같은 자리): 줄 0 ~ 3 스탯 (그림 · 글 · 트리 · +), 줄 4 ~ 5 기준 무기 (그림 · 글 2줄) + 작은 단추 넷,
    줄 6 ~ 9 (가방 자리) 넓은 단추 여덟 (칸 0 ~ 3 · 5 ~ 8)"""
    img = nine(os.path.join(SPRITES, "stats", "dialog_bg.png"), 176, 222, 6)
    gold_line(img, 15)
    for r in range(4):
        y = cell_y(r)
        blend(img, (5, y, 171, y + 18), (255, 255, 255, 0x0A) if r % 2 == 0 else (0, 0, 0, 0x30))
        slot_frame(img, cell_x(0), y)
        button(img, cell_x(7) + 1, y + 1, 16, 16)
        button(img, cell_x(8) + 1, y + 1, 16, 16, gold=True)
    gold_line(img, cell_y(4) - 1, alpha=0x60)
    slot_frame(img, cell_x(0), cell_y(4))  # 기준 무기
    for r in (4, 5):
        for c in (7, 8):
            button(img, cell_x(c) + 1, cell_y(r) + 1, 16, 16)
    gold_line(img, 133, alpha=0x60)
    for r in range(6, 10):
        y = cell_y(r)
        for c0 in (0, 5):
            button(img, cell_x(c0) + 1, y + 1, 70, 16)
    return opaque(img)


def skills_bg():
    img = nine(os.path.join(SPRITES, "stats", "dialog_bg.png"), 176, 222, 6)
    gold_line(img, 15)
    for c in range(8):
        slot_frame(img, cell_x(c), cell_y(1))
    button(img, cell_x(8) + 1, cell_y(1) + 1, 16, 16, gold=True)  # 빼기
    gold_line(img, cell_y(2) + 17, alpha=0x40)
    for c in range(5):
        slot_frame(img, cell_x(c), cell_y(3))
    button(img, cell_x(8) + 1, cell_y(3) + 1, 16, 16)  # 스탯 창으로
    gold_line(img, 133, alpha=0x60)
    for r in range(6, 10):  # 가방 (진짜 칸 — 아이템은 가방 그대로)
        for c in range(9):
            slot_frame(img, cell_x(c), cell_y(r))
    return opaque(img)


def icon_plus(on):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col = (124, 255, 138, 255) if on else (90, 90, 100, 255)
    shade = (40, 110, 52, 255) if on else (40, 40, 48, 255)
    d.rectangle((7, 4, 9, 12), fill=shade)
    d.rectangle((4, 7, 12, 9), fill=shade)
    d.rectangle((6, 3, 8, 11), fill=col)
    d.rectangle((3, 6, 11, 8), fill=col)
    return img


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
    sheet, lines, widths = text_sheet(sys.argv[1])
    sheet.save(os.path.join(OUT, "text.png"))
    json.dump(lines, open(os.path.join(OUT, "text_chars.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump({"space": 4, "default": 6, "widths": widths}, open(os.path.join(OUT, "text_widths.json"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    stats_bg().save(os.path.join(OUT, "stats.png"))
    skills_bg().save(os.path.join(OUT, "skills.png"))
    icon_plus(True).save(os.path.join(OUT, "icons", "plus_on.png"))
    icon_plus(False).save(os.path.join(OUT, "icons", "plus_off.png"))
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(os.path.join(OUT, "icons", "blank.png"))
    print("glyphs", len(widths), "sheet", sheet.size, "rows", len(lines))


if __name__ == "__main__":
    main()
