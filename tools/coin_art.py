"""0.21.0 금화 · 은화 아이템 그림 (32×32 픽셀 — 기존 재료 아이템처럼 짙은 테두리 · 밝은 테 · 두께 · 새김 무늬 · 반짝임).
금화 = 네 갈래 별, 은화 = 초승달. 화면 왼쪽 아래 지갑 그림 (22×22 — HUD 에 11칸으로 그림) 도 같은 식으로.
사용: python3 tools/coin_art.py src/main/resources/assets/rpg/textures/item/material [--preview]
      python3 tools/coin_art.py --hud src/main/resources/assets/mmorpg/textures/gui/sprites/hud"""
import math, sys
from PIL import Image

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))

PAL = {
    # outline, rim dark, rim light, face dark, face mid, face light, emblem dark, emblem light, spark
    "gold": [(58, 34, 6), (150, 92, 18), (255, 214, 92), (196, 128, 24), (232, 170, 40), (255, 222, 110), (150, 90, 14), (255, 246, 196), (255, 255, 236)],
    "silver": [(34, 40, 56), (104, 116, 140), (236, 242, 252), (140, 152, 176), (184, 196, 216), (226, 234, 246), (96, 108, 134), (255, 255, 255), (240, 248, 255)],
}

def star_mask(cx, cy, r_out, r_in, pts=4, rot=-math.pi / 2):
    """pts 갈래 별 (다각형 안쪽 판정)"""
    poly = []
    for i in range(pts * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / pts
        poly.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    def inside(x, y):
        c = False
        n = len(poly)
        for i in range(n):
            x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-9) + x1:
                c = not c
        return c
    return inside

def moon_mask(cx, cy, r, off):
    def inside(x, y):
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r and (x - cx - off) ** 2 + (y - cy + off * 0.35) ** 2 > (r * 0.86) ** 2
    return inside

def coin(kind, emblem, S=32):
    o, rd, rl, fd, fm, fl, ed, el, sp = PAL[kind]
    k = S / 32.0
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    cx, cy = 15.5 * k, 15.0 * k
    R = 13.0 * k   # 바깥 (테두리 포함)
    RIM = 10.6 * k  # 테 안쪽
    TH = max(1.0, 2.2 * k)   # 동전 두께 (아래로 보이는 옆면)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5 + TH)
            d = math.hypot(dx, dy)
            if d <= R + 0.35:
                px[x, y] = (o if d > R - 1.0 else lerp(rd, o, 0.35)) + (255,)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
            d = math.hypot(dx, dy)
            if d > R + 0.35:
                continue
            # 빛은 왼쪽 위에서: t = 0 (밝음) ~ 1 (어두움)
            t = max(0.0, min(1.0, 0.5 + (dx + dy) / (2.2 * R)))
            if d > R - 1.0:
                c = o
            elif d > RIM:
                # 테: 왼쪽 위 밝고 오른쪽 아래 어두움 (볼록)
                c = lerp(rl, rd, t ** 1.2)
            elif d > RIM - 1.0:
                # 테 안쪽 홈 (오목 — 빛이 반대)
                c = lerp(rd, rl, t * 0.8)
            else:
                c = lerp(fl, fd, t ** 1.1)
                c = lerp(c, fm, 0.35)
            px[x, y] = c + (255,)
    # 새김 무늬 (얕은 돋을새김: 왼쪽 위 가장자리 밝게 · 오른쪽 아래 어둡게)
    m = emblem(cx + 0.5, cy + 0.5)
    for y in range(S):
        for x in range(S):
            if m(x + 0.5, y + 0.5):
                up = not m(x - 0.5, y - 0.5)
                down = not m(x + 1.5, y + 1.5)
                if up:
                    c = el
                elif down:
                    c = ed
                else:
                    c = lerp(fm, fl, 0.55)
                px[x, y] = c + (255,)
    # 반짝임: 테 왼쪽 위 하이라이트 · 오른쪽 위 별빛
    for (x, y) in sorted({(int(round(a * k)), int(round(b * k))) for (a, b) in [(8, 7), (9, 6), (7, 8), (10, 5)]}):
        px[x, y] = sp + (255,)
    if S >= 32:
        for (x, y, a) in [(26, 3, 255), (25, 3, 150), (27, 3, 150), (26, 2, 150), (26, 4, 150)]:
            px[x, y] = sp + (a,)
    else:
        sx, sy = int(round(26 * k)), int(round(3 * k))
        px[sx, sy] = sp + (255,)
    return im

def hud(out_dir):
    """화면 왼쪽 아래 지갑의 금화 · 은화 (22×22 — 11칸에 2배로)"""
    S = 22
    k = S / 32.0
    g = coin("gold", lambda cx, cy: star_mask(cx, cy, 7.0 * k, 2.7 * k, 4), S)
    s = coin("silver", lambda cx, cy: moon_mask(cx - 0.6 * k, cy, 6.2 * k, 2.9 * k), S)
    g.save(out_dir + "/coin_gold.png")
    s.save(out_dir + "/coin_silver.png")

def main(out_dir, preview=False):
    g = coin("gold", lambda cx, cy: star_mask(cx, cy, 7.0, 2.7, 4))
    s = coin("silver", lambda cx, cy: moon_mask(cx - 0.6, cy, 6.2, 2.9))
    g.save(out_dir + "/gold_coin.png")
    s.save(out_dir + "/silver_coin.png")
    if not preview:
        return
    prev = Image.new("RGBA", (32 * 8 * 2 + 40, 32 * 8 + 20), (40, 40, 48, 255))
    prev.paste(g.resize((256, 256), Image.NEAREST), (10, 10), g.resize((256, 256), Image.NEAREST))
    prev.paste(s.resize((256, 256), Image.NEAREST), (286, 10), s.resize((256, 256), Image.NEAREST))
    prev.save(out_dir + "/preview.png")

if __name__ == "__main__":
    if "--hud" in sys.argv:
        hud([a for a in sys.argv[1:] if not a.startswith("--")][0])
    else:
        main(sys.argv[1], "--preview" in sys.argv)
