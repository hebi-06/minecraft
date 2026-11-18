#!/usr/bin/env python3
"""2026-11-18 축복의 길 공용 스킬 16 아이콘 (스탯 트리 40 · 90 칸 — CommonSkills): 32×32 메이플식 네모 그림 (직업 스킬 아이콘과 같은 짜임 · 도움 함수).
→ assets/mmorpg/textures/gui/sprites/job_skill/common_<id>.png (직업 스킬 그림과 같은 곳 — 모드 없는 클라이언트 rpg:vjob/common_<id> · HUD 글꼴 job:common_<id>
· 모드 HUD 그림이 그대로 씀). 색 = 그 신의 원소 (포르티아 불 · 라에타 번개 · 니바리아 얼음 · 그라비스 바위).

  python3 tools/common_skill_icons.py                 # 아이콘을 씀
  python3 tools/common_skill_icons.py --sheet a.png   # 한눈에 보기 (8배 · 어두운 칸 위)
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (N, OUT, SS, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix, poly, ring, stack,  # noqa: E402
                             star)

DARK = (0, 0, 0)
# 원소: (빛 A, 짙은 빛 B, 가장 밝은 심지)
FIRE = ((255, 156, 64), (238, 72, 28), (255, 238, 176))
VOLT = ((255, 226, 96), (118, 186, 255), (255, 255, 226))
ICE = ((176, 232, 255), (70, 148, 250), (242, 252, 255))
ROCK = ((236, 188, 112), (146, 94, 44), (255, 226, 162))


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def back(el):
    A, B, H = el
    return backdrop(mix(B, (8, 10, 30), 0.86), mix(A, (18, 24, 54), 0.45))


def body(m, el, axis=(0.0, 1.0)):
    """원소 띠 (위 밝음 → 아래 짙음) + 위왼쪽 흰 줄 · 아래오른쪽 어두운 줄"""
    A, B, H = el
    return layer(m, H, B, WHITE, mix(B, DARK, 0.42), axis=axis)


def soft_body(m, el):
    A, B, H = el
    return layer(m, A, B, None, None)


def flame(m, cx, by, h, w):
    """불꽃 혀 (밑 by 가운데 cx · 높이 h · 너비 w — 끝이 뾰족)"""
    pts = []
    for i in range(17):
        t = i / 16
        a = math.pi * t
        r = math.sin(a) ** 0.8
        pts.append((cx - w / 2 * r, by - h * (1 - t) ** 0.25 * 0 - h * t * 0))
    poly(m, [(cx - w / 2, by), (cx - w * 0.42, by - h * 0.45), (cx - w * 0.12, by - h * 0.78), (cx, by - h), (cx + w * 0.2, by - h * 0.66),
             (cx + w * 0.45, by - h * 0.4), (cx + w / 2, by)])
    disc(m, cx, by - w * 0.32, w * 0.5)
    return m


def arrow_head(m, tip, d, size):
    """화살 머리 (tip 쪽 d 방향 — 단위 벡터)"""
    tx, ty = tip
    dx, dy = d
    px, py = -dy, dx
    poly(m, [(tx, ty), (tx - dx * size + px * size * 0.7, ty - dy * size + py * size * 0.7),
             (tx - dx * size * 0.55, ty - dy * size * 0.55), (tx - dx * size - px * size * 0.7, ty - dy * size - py * size * 0.7)])
    return m


def bolt_path(m, pts, w):
    return line(m, pts, w)


def snowflake(m, cx, cy, r, w=1.4, arms=6, rot=90):
    for i in range(arms):
        a = math.radians(rot + i * 360 / arms)
        ex, ey = cx + math.cos(a) * r, cy - math.sin(a) * r
        line(m, [(cx, cy), (ex, ey)], w)
        for k in (0.55, 0.8):
            bx, by = cx + math.cos(a) * r * k, cy - math.sin(a) * r * k
            for s in (-1, 1):
                b = a + s * math.radians(40)
                line(m, [(bx, by), (bx + math.cos(b) * r * 0.26, by - math.sin(b) * r * 0.26)], w * 0.8)
    return m


def ground(m, y, x0=1.5, x1=30.5, w=1.6):
    return line(m, [(x0, y), (x1, y)], w)


# ------------------------------------------------------------ 포르티아 (힘 · 불)

def blaze_leap():
    el = FIRE
    arc = mask()
    pts = []
    for i in range(21):
        t = i / 20
        pts.append((4.5 + 21.5 * t, 26.5 - 19.0 * math.sin(math.pi * t * 0.92)))
    line(arc, pts, 2.6)
    head = mask()
    arrow_head(head, (27.6, 22.0), (0.55, 0.83), 6.0)
    fl = mask()
    flame(fl, 5.5, 29.5, 9.0, 5.0)
    flame(fl, 9.5, 29.5, 6.0, 3.6)
    burst = mask()
    star(burst, 26.5, 27.5, 4.4, 0.25)
    subject = stack(body(lighter(arc, head), el, axis=(0.7, 0.7)), soft_body(fl, el), layer(burst, WHITE, el[0]))
    return finish(subject, [glow(lighter(arc, fl, burst), el[1], 1.7, 1.0)], back(el))


def steel_charge():
    el = FIRE
    shield = mask()
    poly(shield, [(15.5, 5.0), (27.5, 8.5), (27.0, 19.0), (21.5, 27.5), (16.0, 19.0)])
    rim = mask()
    poly(rim, [(17.5, 8.0), (25.5, 10.4), (25.1, 18.4), (21.4, 24.0), (17.8, 18.4)])
    lines = mask()
    for y, x0, x1 in ((9.5, 2.0, 12.0), (16.0, 1.0, 13.0), (22.5, 3.0, 13.0)):
        line(lines, [(x0, y), (x1, y)], 1.6)
    sp = mask()
    star(sp, 8.0, 27.0, 2.4)
    star(sp, 5.0, 5.0, 1.8)
    steel = ((236, 242, 255), (150, 160, 188), (255, 255, 255))
    subject = stack(layer(lines, el[2], el[1], None, None, axis=(1, 0)), body(shield, steel), soft_body(rim, el), layer(sp, WHITE, el[0]))
    return finish(subject, [glow(lighter(lines, rim), el[1], 1.6, 1.0)], back(el))


def blaze_cross():
    el = FIRE
    a = mask()
    crescent(a, 4.0, 28.0, 25.0, 4.4, -84, -6)
    b = mask()
    crescent(b, 28.0, 28.0, 25.0, 4.4, -174, -96)
    sp = mask()
    star(sp, 16.0, 13.5, 4.6, 0.22, 45)
    fl = mask()
    flame(fl, 16.0, 30.5, 7.0, 4.4)
    subject = stack(body(a, el, axis=(0.7, 0.7)), body(b, el, axis=(-0.7, 0.7)), soft_body(fl, el), layer(sp, WHITE, el[2]))
    return finish(subject, [glow(lighter(a, b, fl), el[1], 1.8, 1.0)], back(el))


def ember_nova():
    el = FIRE
    core = mask()
    disc(core, 16, 16, 5.2)
    rays = mask()
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        ex, ey = 16 + math.cos(a) * 14.5, 16 + math.sin(a) * 14.5
        sx, sy = 16 + math.cos(a) * 6.0, 16 + math.sin(a) * 6.0
        px, py = -math.sin(a), math.cos(a)
        poly(rays, [(sx + px * 2.4, sy + py * 2.4), (ex, ey), (sx - px * 2.4, sy - py * 2.4)])
    rg = mask()
    ring(rg, 16, 16, 12.0, 1.4)
    subject = stack(soft_body(rg, el), body(rays, el), layer(core, el[2], el[0], WHITE, None))
    return finish(subject, [glow(lighter(core, rays, rg), el[1], 2.0, 1.0)], back(el))


# ------------------------------------------------------------ 라에타 (민첩 · 번개)

def spark_blink():
    el = VOLT
    a = mask()
    ring(a, 7.5, 24.5, 5.2, 1.5)
    b = mask()
    ring(b, 24.5, 7.5, 5.2, 1.5)
    bl = mask()
    bolt_path(bl, [(10.5, 21.0), (15.0, 18.5), (13.5, 15.5), (18.5, 13.0), (17.0, 10.5), (21.5, 10.5)], 2.0)
    sp = mask()
    star(sp, 24.5, 7.5, 3.6)
    star(sp, 6.0, 8.0, 1.8)
    star(sp, 26.0, 26.0, 1.8)
    subject = stack(soft_body(a, el), body(b, el), layer(bl, el[2], el[0], WHITE, None, axis=(1, -1)), layer(sp, WHITE, el[2]))
    return finish(subject, [glow(lighter(a, b, bl), el[1], 1.8, 1.0)], back(el))


def lightning_sprint():
    el = VOLT
    bl = mask()
    poly(bl, [(13.0, 2.5), (5.5, 17.0), (11.5, 17.0), (7.5, 29.5), (19.5, 13.0), (13.0, 13.0), (18.0, 2.5)])
    ch = mask()
    for x in (19.0, 25.0):
        line(ch, [(x, 9.5), (x + 5.0, 16.0), (x, 22.5)], 2.2)
    subject = stack(body(bl, el, axis=(0.3, 1.0)), layer(ch, el[2], el[1], WHITE, None, axis=(1, 0)))
    return finish(subject, [glow(lighter(bl, ch), el[1], 1.8, 1.0)], back(el))


def volt_dagger():
    el = VOLT
    blade = mask()
    poly(blade, [(28.5, 3.5), (21.5, 13.0), (18.8, 10.3)])
    guard = mask()
    line(guard, [(16.5, 9.5), (22.5, 15.5)], 2.0)
    grip = mask()
    line(grip, [(19.5, 12.5), (15.0, 17.0)], 1.6)
    tail = mask()
    bolt_path(tail, [(14.5, 17.5), (10.5, 18.0), (11.5, 21.5), (7.0, 22.5), (8.0, 25.5), (3.0, 28.5)], 1.8)
    sp = mask()
    star(sp, 28.0, 4.0, 3.2)
    steel = ((244, 250, 255), (168, 188, 224), (255, 255, 255))
    subject = stack(layer(tail, el[2], el[0], None, None, axis=(-1, 1)), body(blade, steel, axis=(0.7, 0.7)), soft_body(grip, ((90, 70, 120), (50, 36, 70), None)),
                    body(guard, el), layer(sp, WHITE, el[2]))
    return finish(subject, [glow(lighter(tail, blade, sp), el[1], 1.7, 1.0)], back(el))


def flicker_shot():
    el = VOLT
    beam = mask()
    line(beam, [(11.0, 15.5), (30.0, 15.5)], 3.2)
    core = mask()
    line(core, [(12.0, 15.5), (29.5, 15.5)], 1.2)
    back_arrow = mask()
    line(back_arrow, [(9.0, 8.5), (3.0, 15.5), (9.0, 22.5)], 2.2)
    sp = mask()
    star(sp, 29.0, 15.5, 4.4, 0.2)
    zig = mask()
    bolt_path(zig, [(16.0, 12.5), (19.0, 9.0), (22.0, 12.0), (25.0, 8.0)], 1.1)
    bolt_path(zig, [(16.5, 18.5), (19.5, 22.0), (22.5, 19.0), (25.5, 23.0)], 1.1)
    subject = stack(soft_body(zig, el), body(beam, el, axis=(1, 0)), layer(core, WHITE, el[2]), layer(back_arrow, el[2], el[1], WHITE, None),
                    layer(sp, WHITE, el[2]))
    return finish(subject, [glow(lighter(beam, zig, sp), el[1], 1.8, 1.0)], back(el))


# ------------------------------------------------------------ 니바리아 (마력 · 얼음)

def ice_slide():
    el = ICE
    trail = mask()
    pts = [(2.0 + i * 1.4, 24.5 + math.sin(i * 0.55) * 1.6) for i in range(17)]
    line(trail, pts, 3.0)
    shine = mask()
    line(shine, [(3.0, 23.0), (24.0, 23.5)], 0.9)
    head = mask()
    line(head, [(21.0, 8.0), (29.0, 14.0), (21.0, 20.0)], 2.6)
    sp = mask()
    snowflake(sp, 9.0, 11.0, 5.0, 1.1)
    star(sp, 17.0, 5.0, 2.0)
    subject = stack(body(trail, el, axis=(1, 0)), layer(shine, WHITE, el[0]), layer(head, el[2], el[1], WHITE, None), soft_body(sp, el))
    return finish(subject, [glow(lighter(trail, head, sp), el[1], 1.8, 1.0)], back(el))


def crystal_shift():
    el = ICE
    cr = mask()
    poly(cr, [(21.0, 3.0), (27.5, 12.5), (21.0, 29.0), (14.5, 12.5)])
    facet = mask()
    poly(facet, [(21.0, 3.0), (21.0, 29.0), (14.5, 12.5)])
    arr = mask()
    pts = []
    for i in range(13):
        t = i / 12
        a = math.radians(200 + 140 * t)
        pts.append((10.0 + math.cos(a) * 7.0, 18.0 + math.sin(a) * 7.0))
    line(arr, pts, 2.0)
    arrow_head(arr, (4.2, 22.2), (-0.35, 0.94), 5.0)
    sp = mask()
    star(sp, 27.0, 5.0, 2.4)
    star(sp, 6.0, 6.0, 1.8)
    subject = stack(body(cr, el, axis=(0.7, 0.7)), layer(facet, WHITE, el[0], None, None), layer(arr, el[2], el[1], WHITE, None), layer(sp, WHITE, el[2]))
    return finish(subject, [glow(lighter(cr, arr), el[1], 1.8, 1.0)], back(el))


def cryo_pulse():
    el = ICE
    sf = mask()
    snowflake(sf, 16, 16, 9.0, 1.9)
    rg = mask()
    ring(rg, 16, 16, 14.0, 1.6)
    rg2 = mask()
    ring(rg2, 16, 16, 10.8, 0.9)
    subject = stack(soft_body(rg, el), soft_body(rg2, el), body(sf, el))
    return finish(subject, [glow(lighter(sf, rg), el[1], 2.0, 1.0)], back(el))


def hailstone():
    el = ICE
    lance = mask()
    poly(lance, [(13.0, 2.0), (19.0, 2.0), (17.4, 18.0), (16.0, 25.5), (14.6, 18.0)])
    facet = mask()
    poly(facet, [(16.0, 2.0), (19.0, 2.0), (17.4, 18.0), (16.0, 25.5)])
    imp = mask()
    d = ImageDraw.Draw(imp)
    d.ellipse([4.0 * SS, 24.0 * SS, 28.0 * SS, 31.0 * SS], outline=255, width=int(1.6 * SS))
    sh = mask()
    for x, y in ((7.0, 20.0), (25.0, 20.5), (9.5, 15.5), (23.0, 15.0)):
        poly(sh, [(x, y - 2.2), (x + 1.4, y), (x, y + 2.2), (x - 1.4, y)])
    subject = stack(soft_body(imp, el), body(lance, el, axis=(0.3, 1.0)), layer(facet, WHITE, el[0], None, None), soft_body(sh, el))
    return finish(subject, [glow(lighter(lance, imp), el[1], 1.8, 1.0)], back(el))


# ------------------------------------------------------------ 그라비스 (체력 · 바위)

def boulder_roll():
    el = ROCK
    bo = mask()
    disc(bo, 19.0, 17.0, 10.5)
    cracks = mask()
    line(cracks, [(14.5, 10.0), (18.0, 15.0), (16.5, 20.0)], 1.0)
    line(cracks, [(18.0, 15.0), (24.0, 13.5)], 0.9)
    line(cracks, [(21.0, 22.0), (25.0, 19.5)], 0.9)
    lines = mask()
    for y, x0, x1 in ((10.0, 1.5, 7.0), (17.0, 0.5, 6.5), (24.0, 1.5, 7.5)):
        line(lines, [(x0, y), (x1, y)], 1.5)
    dust = mask()
    disc(dust, 9.0, 28.5, 2.0)
    disc(dust, 13.5, 29.5, 1.5)
    subject = stack(layer(lines, el[2], el[0], None, None, axis=(1, 0)), body(bo, el, axis=(0.7, 0.7)), layer(cracks, (70, 44, 20), (50, 30, 14)), soft_body(dust, el))
    return finish(subject, [glow(lighter(bo, lines), el[1], 1.5, 0.8)], back(el))


def rock_launch():
    el = ROCK
    pil = mask()
    poly(pil, [(9.0, 31.0), (10.0, 17.0), (12.5, 15.5), (15.0, 17.0), (17.5, 14.5), (20.5, 16.5), (22.0, 15.5), (23.0, 31.0)])
    up = mask()
    line(up, [(16.0, 12.5), (16.0, 3.5)], 2.4)
    arrow_head(up, (16.0, 1.5), (0.0, -1.0), 6.0)
    rocks = mask()
    for x, y, r in ((6.0, 12.0, 1.8), (26.0, 10.0, 1.6), (4.5, 22.0, 1.3), (27.5, 21.0, 1.4)):
        disc(rocks, x, y, r)
    gr = mask()
    ground(gr, 30.8)
    subject = stack(soft_body(gr, el), body(pil, el, axis=(0.6, 0.8)), layer(up, el[2], el[0], WHITE, None, axis=(0, -1)), soft_body(rocks, el))
    return finish(subject, [glow(lighter(up, pil), el[1], 1.6, 0.9)], back(el))


def rock_spike():
    el = ROCK
    sp = mask()
    for cx, h, w in ((7.5, 13.0, 7.0), (16.5, 22.0, 8.0), (25.0, 16.0, 7.0)):
        poly(sp, [(cx - w / 2, 28.5), (cx - w * 0.12, 28.5 - h * 0.6), (cx, 28.5 - h), (cx + w * 0.18, 28.5 - h * 0.55), (cx + w / 2, 28.5)])
    gr = mask()
    ground(gr, 29.0, w=2.0)
    bits = mask()
    for x, y in ((12.0, 6.5), (21.5, 4.5), (3.5, 11.0)):
        poly(bits, [(x, y - 1.6), (x + 1.6, y), (x, y + 1.6), (x - 1.6, y)])
    subject = stack(soft_body(gr, el), body(sp, el, axis=(0.6, 0.8)), soft_body(bits, el))
    return finish(subject, [glow(sp, el[1], 1.5, 0.9)], back(el))


def aftershock():
    el = ROCK
    gr = mask()
    ground(gr, 23.0, w=1.8)
    crack = mask()
    line(crack, [(16.0, 23.0), (13.5, 26.0), (15.0, 28.0), (12.0, 31.0)], 1.4)
    line(crack, [(16.0, 23.0), (19.5, 25.5), (18.5, 28.5), (21.5, 31.0)], 1.2)
    waves = mask()
    d = ImageDraw.Draw(waves)
    for r, w in ((7.0, 1.8), (12.5, 1.6)):
        d.arc([(16 - r) * SS, (23 - r * 0.75) * SS, (16 + r) * SS, (23 + r * 0.75) * SS], 180, 360, fill=255, width=int(w * SS))
    hit = mask()
    star(hit, 16.0, 21.0, 6.5, 0.3)
    bits = mask()
    for x, y, r in ((5.0, 13.0, 1.6), (27.0, 12.5, 1.5), (10.0, 6.5, 1.3), (22.5, 5.5, 1.4), (16.0, 9.0, 1.1)):
        poly(bits, [(x, y - r * 1.3), (x + r, y), (x, y + r * 1.3), (x - r, y)])
    subject = stack(soft_body(waves, el), soft_body(gr, el), layer(crack, (255, 226, 150), (190, 120, 50)), layer(hit, WHITE, el[2]), body(bits, el))
    return finish(subject, [glow(lighter(waves, crack, hit), el[1], 1.7, 1.0)], back(el))


ICONS = {
    "blaze_leap": blaze_leap, "steel_charge": steel_charge, "blaze_cross": blaze_cross, "ember_nova": ember_nova,
    "spark_blink": spark_blink, "lightning_sprint": lightning_sprint, "volt_dagger": volt_dagger, "flicker_shot": flicker_shot,
    "ice_slide": ice_slide, "crystal_shift": crystal_shift, "cryo_pulse": cryo_pulse, "hailstone": hailstone,
    "boulder_roll": boulder_roll, "rock_launch": rock_launch, "rock_spike": rock_spike, "aftershock": aftershock,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    out = {}
    for sid, fn in ICONS.items():
        im = fn()
        im.save(os.path.join(OUT, "common_%s.png" % sid))
        out[sid] = im
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        cols = 8
        sc = 6
        rows = (len(out) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (N * sc + 8) + 8, rows * (N * sc + 8) + 8), (22, 24, 40, 255))
        for i, im in enumerate(out.values()):
            x = 8 + (i % cols) * (N * sc + 8)
            y = 8 + (i // cols) * (N * sc + 8)
            sheet.alpha_composite(im.resize((N * sc, N * sc), Image.NEAREST), (x, y))
        sheet.save(path)
    print("common skill icons", len(out))


if __name__ == "__main__":
    main()
