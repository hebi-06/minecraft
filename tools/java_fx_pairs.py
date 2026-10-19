#!/usr/bin/env python3
"""0.25.0 두 스탯 룬 계열 여섯 (해류 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사) 연출 그림 (rpg:rfx_* — 애드온 rfx 파티클과 같은 형식) → tools/java_fx.

  python3 tools/java_fx_pairs.py        # (tools/java_fx_art.py 가 함께 부름) 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/addon_particles.py <애드온 폴더>   # 그다음 자바 파티클 정의 · 그림 · 이름표 (AddonFxTable) 를 다시 만듦

그림 (모두 흰색 — 빛 · 심지 두 칸에 색을 입힘, RuneKit.rfx2)
- rfx_fist    무투의 주먹 (256×256 × 4칸, rotate_xyz — 보는 사람을 향함): 0 · 1 = 앞에서 본 주먹 (마디 넷 · 엄지 · 둘레 기의 고리) 빛 · 심지,
              2 · 3 = 펼친 손바닥 (장풍 — 손바닥 · 손가락 다섯 · 둘레로 터지는 빛살) 빛 · 심지
- rfx_punch   무투의 날아가는 주먹 (512×256 × 2칸, lookat_direction — 그림의 오른쪽 = 나아가는 쪽): 0 빛 (옆에서 본 주먹 + 뒤로 끌리는 기의 꼬리 · 속도선) · 1 심지
- rfx_beast   사냥꾼의 영혼 짐승 (256×256 × 6칸, rotate_xyz): 0 · 1 = 날개를 편 매 (앞모습), 2 · 3 = 이빨을 드러낸 늑대 머리 (앞모습), 4 · 5 = 발톱 자국 셋
- rfx_splash  해류의 물 (256×256 × 4칸, lookat_y — 서 있는 그림, 세로축으로 돎): 0 · 1 = 솟구치는 물보라 왕관, 2 · 3 = 말려 부서지는 파도 마루
- rfx_net     사냥꾼의 그물 (256×256 × 2칸, direction_z — dir 쪽을 보는 판, 땅에 눕힘): 0 빛 (굵은 그물코 · 둘레 테) · 1 심지 (가는 그물 줄 · 매듭)
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_fx_art  # noqa: E402 — 같은 파티클 정의 꼴 (particle) · 저장

SS = 2  # 겹쳐 그리기 (매끈한 가장자리)


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (255, 255, 255, 0))


def done(img, w, h, blur=0.0):
    if blur > 0:
        img = img.filter(ImageFilter.GaussianBlur(blur * SS))
    return img.resize((w, h), Image.LANCZOS)


def soft(img, radius, gain=1.0):
    """알파를 흐리게 (빛무리)"""
    a = img.split()[3].filter(ImageFilter.GaussianBlur(radius * SS))
    if gain != 1.0:
        a = a.point(lambda v: min(255, int(v * gain)))
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(a)
    return out


def layer(*imgs):
    out = Image.new("RGBA", imgs[0].size, (255, 255, 255, 0))
    for i in imgs:
        out.alpha_composite(i)
    return out


def alpha_scale(img, k):
    a = img.split()[3].point(lambda v: int(min(255, v * k)))
    out = img.copy()
    out.putalpha(a)
    return out


def radial(w, h, cx, cy, r, inner, outer=0, power=1.6):
    img = canvas(w, h)
    px = img.load()
    W, H = w * SS, h * SS
    for y in range(H):
        for x in range(W):
            d = math.hypot(x + 0.5 - cx * SS, y + 0.5 - cy * SS) / (r * SS)
            if d >= 1:
                continue
            t = (1 - d) ** power
            px[x, y] = (255, 255, 255, int(outer + (inner - outer) * t))
    return img


def S(v):
    return v * SS


def pts(seq):
    return [(S(x), S(y)) for x, y in seq]


# ------------------------------------------------------------ 주먹 (앞모습 · 손바닥)

def fist_shape(d, fill, cx=128, cy=132, k=1.0, fingers=True):
    """앞에서 본 주먹: 손가락 마디 넷 (둥근 사각) + 손등 + 엄지 (아래를 가로지름)"""
    w = 30 * k
    top = cy - 52 * k
    for i in range(4):
        x0 = cx - 2 * w + i * w
        d.rounded_rectangle((S(x0 + 2 * k), S(top + (6 if i in (0, 3) else 0) * k), S(x0 + w - 2 * k), S(top + 44 * k)), radius=S(13 * k), fill=fill)
    d.rounded_rectangle((S(cx - 2 * w + 2 * k), S(top + 24 * k), S(cx + 2 * w - 2 * k), S(cy + 34 * k)), radius=S(16 * k), fill=fill)
    # 엄지 (왼쪽에서 가운데로 가로지름)
    d.rounded_rectangle((S(cx - 2 * w - 6 * k), S(cy + 2 * k), S(cx + 16 * k), S(cy + 30 * k)), radius=S(14 * k), fill=fill)


def fist_cell(glow):
    img = canvas(256, 256)
    d = ImageDraw.Draw(img)
    if glow:
        fist_shape(d, (255, 255, 255, 230), k=1.06)
        body = soft(img, 9, 1.4)
        ring = canvas(256, 256)
        rd = ImageDraw.Draw(ring)
        rd.ellipse((S(18), S(22), S(238), S(242)), outline=(255, 255, 255, 120), width=S(7))
        ring = soft(ring, 4, 1.5)
        halo = radial(256, 256, 128, 132, 124, 120, 0, 1.4)
        return done(layer(halo, ring, body, alpha_scale(img, 0.55)), 256, 256)
    fist_shape(d, (255, 255, 255, 255), k=1.0)
    # 마디 사이 · 엄지 테두리 (어둡게 비움)
    cut = canvas(256, 256)
    cd = ImageDraw.Draw(cut)
    w = 30
    for i in range(1, 4):
        x = 128 - 2 * w + i * w
        cd.line(pts([(x, 84), (x, 118)]), fill=(255, 255, 255, 255), width=S(3))
    cd.arc((S(70), S(130), S(150), S(166)), 200, 340, fill=(255, 255, 255, 255), width=S(3))
    a = img.split()[3]
    ca = cut.split()[3]
    a = Image.composite(a.point(lambda v: int(v * 0.35)), a, ca)
    img.putalpha(a)
    return done(img, 256, 256, 0.4)


def palm_cell(glow):
    """펼친 손바닥 (장풍): 손바닥 · 손가락 다섯 + 둘레로 터지는 빛살"""
    img = canvas(256, 256)
    d = ImageDraw.Draw(img)
    cx, cy = 128, 140
    a = 255 if not glow else 220
    d.rounded_rectangle((S(cx - 40), S(cy - 30), S(cx + 40), S(cy + 46)), radius=S(26), fill=(255, 255, 255, a))
    for i, (dx, h) in enumerate(((-30, 46), (-10, 60), (10, 62), (30, 52))):
        d.rounded_rectangle((S(cx + dx - 9), S(cy - 30 - h), S(cx + dx + 9), S(cy - 6)), radius=S(9), fill=(255, 255, 255, a))
    d.rounded_rectangle((S(cx - 74), S(cy - 6), S(cx - 30), S(cy + 14)), radius=S(10), fill=(255, 255, 255, a))
    rays = canvas(256, 256)
    rd = ImageDraw.Draw(rays)
    rng = random.Random(7)
    for k in range(18):
        ang = k / 18 * math.tau + rng.uniform(-0.08, 0.08)
        r0 = 92 + rng.uniform(-6, 6)
        r1 = 124 if k % 2 == 0 else 110
        w = 7 if k % 2 == 0 else 4
        p0 = (128 + math.cos(ang) * r0, 132 + math.sin(ang) * r0)
        p1 = (128 + math.cos(ang) * r1, 132 + math.sin(ang) * r1)
        nx, ny = -math.sin(ang) * w / 2, math.cos(ang) * w / 2
        rd.polygon(pts([(p0[0] + nx, p0[1] + ny), (p1[0], p1[1]), (p0[0] - nx, p0[1] - ny)]), fill=(255, 255, 255, 220 if glow else 255))
    if glow:
        halo = radial(256, 256, 128, 132, 120, 150, 0, 1.2)
        return done(layer(halo, soft(rays, 3, 1.3), soft(img, 8, 1.3), alpha_scale(img, 0.5)), 256, 256)
    return done(layer(alpha_scale(rays, 0.9), img), 256, 256, 0.4)


def fist_sheet():
    sheet = Image.new("RGBA", (512, 512), (255, 255, 255, 0))
    sheet.paste(fist_cell(True), (0, 0))
    sheet.paste(fist_cell(False), (256, 0))
    sheet.paste(palm_cell(True), (0, 256))
    sheet.paste(palm_cell(False), (256, 256))
    return sheet


def punch_cell(glow):
    """옆에서 본 주먹 (오른쪽을 침): 주먹 (오른쪽 끝) + 손목 + 뒤로 끌리는 기의 꼬리 · 속도선"""
    W, H = 512, 256
    img = canvas(W, H)
    d = ImageDraw.Draw(img)
    cy = 128
    a = 255 if not glow else 230
    # 주먹 (옆) — 둥근 덩어리 + 손가락 마디 줄 + 엄지
    d.rounded_rectangle((S(368), S(cy - 46), S(470), S(cy + 46)), radius=S(30), fill=(255, 255, 255, a))
    d.rounded_rectangle((S(330), S(cy - 34), S(392), S(cy + 34)), radius=S(16), fill=(255, 255, 255, a))
    tail = canvas(W, H)
    td = ImageDraw.Draw(tail)
    # 기의 꼬리 (뒤로 가늘어짐)
    td.polygon(pts([(340, cy - 40), (40, cy - 6), (20, cy), (40, cy + 6), (340, cy + 40)]), fill=(255, 255, 255, 170 if glow else 120))
    for k, (y0, x0, w) in enumerate(((cy - 66, 150, 4), (cy + 66, 170, 4), (cy - 92, 230, 3), (cy + 90, 250, 3), (cy - 30, 90, 3), (cy + 34, 110, 3))):
        td.line(pts([(x0, y0), (x0 + 180, y0 + (cy - y0) * 0.25)]), fill=(255, 255, 255, 200), width=S(w))
    if glow:
        return done(layer(soft(tail, 6, 1.1), soft(img, 9, 1.3), alpha_scale(img, 0.5)), W, H)
    # 심지: 마디 줄 (어둡게) · 흰 주먹
    cut = canvas(W, H)
    cd = ImageDraw.Draw(cut)
    for y in (cy - 20, cy + 2, cy + 24):
        cd.line(pts([(440, y), (466, y)]), fill=(255, 255, 255, 255), width=S(3))
    a2 = img.split()[3]
    a2 = Image.composite(a2.point(lambda v: int(v * 0.4)), a2, cut.split()[3])
    img.putalpha(a2)
    return done(layer(alpha_scale(soft(tail, 2, 1.0), 0.8), img), W, H, 0.3)


def punch_sheet():
    sheet = Image.new("RGBA", (512, 512), (255, 255, 255, 0))
    sheet.paste(punch_cell(True), (0, 0))
    sheet.paste(punch_cell(False), (0, 256))
    return sheet


# ------------------------------------------------------------ 영혼 짐승 (매 · 늑대 · 발톱 자국)

def falcon_shape(d, fill, k=1.0):
    """날개를 편 매 (앞모습): 몸통 · 머리 · 부리 · 큰 날개 둘 (깃 끝이 갈라짐) · 꼬리"""
    cx, cy = 128, 120
    d.ellipse((S(cx - 16 * k), S(cy - 30 * k), S(cx + 16 * k), S(cy + 36 * k)), fill=fill)
    d.ellipse((S(cx - 14 * k), S(cy - 52 * k), S(cx + 14 * k), S(cy - 22 * k)), fill=fill)
    d.polygon(pts([(cx - 6 * k, cy - 34 * k), (cx + 6 * k, cy - 34 * k), (cx, cy - 18 * k)]), fill=fill)
    for side in (-1, 1):
        wing = [(cx + side * 12 * k, cy - 16 * k), (cx + side * 60 * k, cy - 44 * k), (cx + side * 112 * k, cy - 36 * k)]
        # 깃 끝 (톱니)
        for j in range(5):
            t = j / 4
            x = cx + side * (112 - 22 * t) * k
            y = cy + (-36 + 46 * t) * k
            wing.append((x, y))
            wing.append((x - side * 10 * k, y + 2 * k))
        wing += [(cx + side * 30 * k, cy + 16 * k), (cx + side * 12 * k, cy + 10 * k)]
        d.polygon(pts(wing), fill=fill)
    d.polygon(pts([(cx - 14 * k, cy + 28 * k), (cx + 14 * k, cy + 28 * k), (cx + 22 * k, cy + 62 * k), (cx, cy + 54 * k), (cx - 22 * k, cy + 62 * k)]), fill=fill)


def wolf_shape(d, fill, k=1.0):
    """이빨을 드러낸 늑대 머리 (앞모습): 쫑긋한 귀 둘 · 넓은 볼 · 주둥이 · 벌린 입 (송곳니)"""
    cx, cy = 128, 128
    d.polygon(pts([(cx - 70 * k, cy - 20 * k), (cx - 58 * k, cy - 92 * k), (cx - 22 * k, cy - 52 * k), (cx + 22 * k, cy - 52 * k), (cx + 58 * k, cy - 92 * k),
                   (cx + 70 * k, cy - 20 * k), (cx + 86 * k, cy + 10 * k), (cx + 44 * k, cy + 36 * k), (cx + 24 * k, cy + 82 * k), (cx - 24 * k, cy + 82 * k),
                   (cx - 44 * k, cy + 36 * k), (cx - 86 * k, cy + 10 * k)]), fill=fill)


def wolf_details(d, cut):
    """늑대 심지의 비운 곳: 눈 (매서운 사선) · 콧등 줄 · 벌린 입 (송곳니는 남김)"""
    cx, cy = 128, 128
    for side in (-1, 1):
        d.polygon(pts([(cx + side * 18, cy - 14), (cx + side * 46, cy - 26), (cx + side * 40, cy - 8)]), fill=cut)
    d.line(pts([(cx, cy - 46), (cx, cy + 14)]), fill=cut, width=S(3))
    d.polygon(pts([(cx - 26, cy + 40), (cx + 26, cy + 40), (cx + 16, cy + 72), (cx - 16, cy + 72)]), fill=cut)


def claw_shape(d, fill, w=1.0):
    for i, (x0, y0) in enumerate(((60, 40), (100, 30), (140, 44))):
        path = []
        for j in range(21):
            t = j / 20
            x = x0 + 70 * t + 18 * math.sin(math.pi * t)
            y = y0 + 170 * t
            path.append((x, y, (0.15 + 0.85 * math.sin(math.pi * t)) * 16 * w))
        for (xa, ya, wa), (xb, yb, wb) in zip(path, path[1:]):
            d.line(pts([(xa, ya), (xb, yb)]), fill=fill, width=max(1, int(S((wa + wb) / 2))))
            d.ellipse((S(xb - wb / 2), S(yb - wb / 2), S(xb + wb / 2), S(yb + wb / 2)), fill=fill)


def beast_sheet():
    sheet = Image.new("RGBA", (512, 768), (255, 255, 255, 0))
    # 매
    g = canvas(256, 256)
    falcon_shape(ImageDraw.Draw(g), (255, 255, 255, 230), 1.04)
    sheet.paste(done(layer(soft(g, 8, 1.3), alpha_scale(g, 0.45)), 256, 256), (0, 0))
    c = canvas(256, 256)
    cd = ImageDraw.Draw(c)
    falcon_shape(cd, (255, 255, 255, 255), 0.96)
    # 깃 결 (날개에 가는 비운 줄)
    cut = canvas(256, 256)
    kd = ImageDraw.Draw(cut)
    for side in (-1, 1):
        for j in range(4):
            x0 = 128 + side * (34 + j * 18)
            kd.line(pts([(x0, 86 + j * 2), (x0 + side * 14, 118 + j * 3)]), fill=(255, 255, 255, 255), width=S(2))
    kd.ellipse((S(121), S(76), S(128), S(83)), fill=(255, 255, 255, 255))
    kd.ellipse((S(128), S(76), S(135), S(83)), fill=(255, 255, 255, 255))
    a = c.split()[3]
    c.putalpha(Image.composite(a.point(lambda v: int(v * 0.3)), a, cut.split()[3]))
    sheet.paste(done(c, 256, 256, 0.3), (256, 0))
    # 늑대
    g = canvas(256, 256)
    wolf_shape(ImageDraw.Draw(g), (255, 255, 255, 230), 1.05)
    sheet.paste(done(layer(soft(g, 8, 1.3), alpha_scale(g, 0.45)), 256, 256), (0, 256))
    c = canvas(256, 256)
    wolf_shape(ImageDraw.Draw(c), (255, 255, 255, 255), 0.95)
    cut = canvas(256, 256)
    wolf_details(ImageDraw.Draw(cut), (255, 255, 255, 255))
    # 송곳니 (입 안에 남김)
    fang = canvas(256, 256)
    fd = ImageDraw.Draw(fang)
    for side in (-1, 1):
        fd.polygon(pts([(128 + side * 22, 168), (128 + side * 12, 168), (128 + side * 16, 190)]), fill=(255, 255, 255, 255))
    a = c.split()[3]
    a = Image.composite(a.point(lambda v: int(v * 0.15)), a, cut.split()[3])
    c.putalpha(a)
    c = layer(c, fang)
    sheet.paste(done(c, 256, 256, 0.3), (256, 256))
    # 발톱 자국
    g = canvas(256, 256)
    claw_shape(ImageDraw.Draw(g), (255, 255, 255, 235), 1.5)
    sheet.paste(done(layer(soft(g, 7, 1.3), alpha_scale(g, 0.5)), 256, 256), (0, 512))
    c = canvas(256, 256)
    claw_shape(ImageDraw.Draw(c), (255, 255, 255, 255), 0.8)
    sheet.paste(done(c, 256, 256, 0.3), (256, 512))
    return sheet


# ------------------------------------------------------------ 물 (물보라 왕관 · 파도 마루)

def splash_crown(glow):
    """솟구치는 물보라 왕관 (서 있는 그림 — 아래가 물 위): 가운데 물기둥 + 둘레로 휘어 솟는 물줄기 · 튀는 물방울"""
    img = canvas(256, 256)
    d = ImageDraw.Draw(img)
    rng = random.Random(11 if glow else 12)
    base = 236
    a = 200 if glow else 255
    d.polygon(pts([(110, base), (118, 70), (128, 46), (138, 70), (146, base)]), fill=(255, 255, 255, a))
    for k in range(9):
        t = (k - 4) / 4
        x0 = 128 + t * 30
        top = 70 + abs(t) * 70 + rng.uniform(-8, 8)
        x1 = 128 + t * 110
        path = [(x0 + (x1 - x0) * u * u, base - (base - top) * (1 - (1 - u) ** 2)) for u in [i / 12 for i in range(13)]]
        for (xa, ya), (xb, yb) in zip(path, path[1:]):
            w = 10 * (1 - abs(t) * 0.4)
            d.line(pts([(xa, ya), (xb, yb)]), fill=(255, 255, 255, a), width=max(2, int(S(w))))
        ex, ey = path[-1]
        d.ellipse((S(ex - 6), S(ey - 6), S(ex + 6), S(ey + 6)), fill=(255, 255, 255, a))
    for _ in range(26):
        x = 128 + rng.uniform(-120, 120)
        y = rng.uniform(30, 200)
        r = rng.uniform(2.5, 6)
        d.ellipse((S(x - r), S(y - r * 1.3), S(x + r), S(y + r * 1.3)), fill=(255, 255, 255, a))
    d.ellipse((S(30), S(base - 14), S(226), S(base + 14)), fill=(255, 255, 255, a))
    if glow:
        return done(layer(soft(img, 7, 1.4), alpha_scale(img, 0.45)), 256, 256)
    return done(img, 256, 256, 0.5)


def wave_crest(glow):
    """말려 부서지는 파도 마루 (서 있는 그림): 아래는 넓은 물 몸, 위는 앞으로 말린 마루 · 마루 끝의 물보라"""
    img = canvas(256, 256)
    d = ImageDraw.Draw(img)
    a = 210 if glow else 255
    body = [(0, 256), (0, 170), (40, 140), (90, 104), (140, 70), (178, 52), (210, 54), (232, 70), (240, 92), (226, 110), (204, 104), (192, 92), (176, 96),
            (168, 120), (190, 160), (256, 200), (256, 256)]
    d.polygon(pts(body), fill=(255, 255, 255, a))
    rng = random.Random(21)
    for _ in range(30):
        ang = rng.uniform(-2.6, -0.2)
        r = rng.uniform(10, 46)
        x = 214 + math.cos(ang) * r
        y = 62 + math.sin(ang) * r
        rr = rng.uniform(2, 5.5)
        d.ellipse((S(x - rr), S(y - rr), S(x + rr), S(y + rr)), fill=(255, 255, 255, a))
    if glow:
        return done(layer(soft(img, 8, 1.3), alpha_scale(img, 0.5)), 256, 256)
    # 심지: 마루의 흰 거품 줄 · 몸의 물결 줄만 (몸은 옅게)
    lines = canvas(256, 256)
    ld = ImageDraw.Draw(lines)
    ld.line(pts([(30, 160), (80, 120), (130, 86), (170, 62), (206, 58), (228, 74), (234, 92)]), fill=(255, 255, 255, 255), width=S(9))
    for y0 in (196, 226):
        ld.line(pts([(10, y0), (80, y0 - 22), (150, y0 - 12), (230, y0 + 8)]), fill=(255, 255, 255, 200), width=S(4))
    return done(layer(alpha_scale(img, 0.35), lines), 256, 256, 0.5)


def splash_sheet():
    sheet = Image.new("RGBA", (512, 512), (255, 255, 255, 0))
    sheet.paste(splash_crown(True), (0, 0))
    sheet.paste(splash_crown(False), (256, 0))
    sheet.paste(wave_crest(True), (0, 256))
    sheet.paste(wave_crest(False), (256, 256))
    return sheet


# ------------------------------------------------------------ 그물

def net_cell(glow):
    """둥근 그물 (위에서 본 판): 사선으로 엇갈린 그물코 (원 안) · 매듭 · 둘레 테"""
    img = canvas(256, 256)
    d = ImageDraw.Draw(img)
    cx = cy = 128
    R = 116
    w = int(S(6 if glow else 2.6))
    a = 220 if glow else 255
    step = 24
    for sgn in (-1, 1):
        for k in range(-12, 13):
            off = k * step
            # x + sgn*y = off (원 안만)
            pts_line = []
            for t in [i / 60 for i in range(-60, 61)]:
                x = t * R
                y = (off - x) * sgn
                if (x) ** 2 + (y) ** 2 <= R * R:
                    pts_line.append((cx + x, cy + y + 2.5 * math.sin(x / 9.0)))
            if len(pts_line) > 1:
                d.line(pts(pts_line), fill=(255, 255, 255, a), width=w)
    if not glow:
        for i in range(-12, 13):
            for j in range(-12, 13):
                # 교점: x + y = i*step, x - y = j*step
                x = (i + j) * step / 2
                y = (i - j) * step / 2
                if x * x + y * y < (R - 4) ** 2 and (i + j) % 1 == 0:
                    yy = cy + y + 2.5 * math.sin(x / 9.0)
                    d.ellipse((S(cx + x - 3.4), S(yy - 3.4), S(cx + x + 3.4), S(yy + 3.4)), fill=(255, 255, 255, 255))
    d.ellipse((S(cx - R), S(cy - R), S(cx + R), S(cy + R)), outline=(255, 255, 255, a), width=S(9 if glow else 4))
    if glow:
        return done(layer(soft(img, 5, 1.2), alpha_scale(img, 0.6)), 256, 256)
    return done(img, 256, 256, 0.35)


def net_sheet():
    sheet = Image.new("RGBA", (512, 256), (255, 255, 255, 0))
    sheet.paste(net_cell(True), (0, 0))
    sheet.paste(net_cell(False), (256, 0))
    return sheet


def main():
    java_fx_art.save("rfx_fist", fist_sheet(), 256, 256, "rotate_xyz")
    java_fx_art.save("rfx_punch", punch_sheet(), 512, 256, "lookat_direction")
    java_fx_art.save("rfx_beast", beast_sheet(), 256, 256, "rotate_xyz")
    java_fx_art.save("rfx_splash", splash_sheet(), 256, 256, "lookat_y")
    java_fx_art.save("rfx_net", net_sheet(), 256, 256, "direction_z")


if __name__ == "__main__":
    main()
