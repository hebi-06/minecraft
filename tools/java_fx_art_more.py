#!/usr/bin/env python3
"""세계수의 도끼 · 스발린 (0.12.4 — 도끼 · 방패 초월 새 컨셉, 0.12.8 무기 디자인 색) 연출 그림 → tools/java_fx (java_fx_art.py 가 함께 부름).

그림 (점 그림 — 작은 판에 그려 4배로 키움, 마인크래프트 느낌)
- rfx_yggdrasil  세계수 (512×512 × 4장: 싹 → 어린나무 → 다 자람 → 빛남). lookat_y (세로로 섬), 밑동 = 그림 위에서 118/128
- rfx_thorn      가시뿌리 (128×128 × 3모양 + 빛줄기 3 — rfx_rock 과 같은 칸 · 같은 정의: 밑동 고정, 나무 색 o.rock · 빛 glow)
- rfx_leaf       잎 4모양 (64×64 — 흰 잎, 색을 입힘: 초록 · 금빛 · 꽃잎 분홍). rotate_xyz (돎)
- rfx_bloom      빛의 꽃 (256×256 × 4장: 봉오리 → 핌 → 활짝 → 빛남) — 보는 사람을 향함 (rotate_xyz)
- rfx_bloomflat  같은 꽃을 땅에 눕혀 (direction_z — dir 이 위)
- svalinn_shield 스발린 앞모습 (256×256 × 12장 — 애드온 aegis_shield 와 같은 칸 · 같은 정의: 0~3 빛이 지나감 · 4~7 가운데 보석이 번쩍 · 8~11 흩어짐).
                 0.12.8: 무기 디자인 (0.12.7 사용자 리워크 — tools/weapon_art/shield_transcend_lv0.java.*) 의 앞모습을 광선 추적으로 그대로 (남빛 강철 · 하늘빛 룬)
- svalinn_hex    스발린 돔의 육각 칸 (애드온 aegis_hex 를 흰빛으로 — 색을 입힘, 같은 정의: 접혀 들어감)
- rfx_eclipse    일식 (256×256 × 4장: 해를 가린 스발린 둘 (코로나가 일렁임) · 다이아몬드 고리 · 드러난 해) — 보는 사람을 향함 (rotate_xyz).
                 0.12.8: 코로나 · 드러난 해를 스발린 룬의 하늘빛 · 흰빛으로 (예전 불꽃빛), 원판에 무기 문장 (마름모 · 네 갈래 별)
- (0.12.8) 세계수 · 빛의 꽃: 결 · 열매 · 꽃잎을 세계수의 도끼 날의 민트 수정빛 · 연둣빛으로 (예전 금빛 · 분홍)
- svalinn_ice    서리 돌진의 얼음 조각 (32×32 × 4모양 — 애드온 aegis_stone 과 같은 칸 · 같은 정의, 미리 칠한 얼음빛)
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tools", "java_fx")
TEX = os.path.join(OUT, "textures", "particle", "rpg", "fx")
PART = os.path.join(OUT, "particles", "rpg", "fx")
# 애드온 파티클 정의 (같은 꼴을 빌려 쓰는 그림 — 없으면 tools/java_fx 에 있는 것을 그대로 둠)
ADDON = os.environ.get("RPG_ADDON", "/home/claude/rpg_addon")
ADDON_FX = os.path.join(ADDON, "RPG_RP", "particles", "rpg", "fx")
REPO_FX = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "particle", "addon", "rpg", "fx")


# ------------------------------------------------------------ 도움

def up4(img, k=4):
    return img.resize((img.size[0] * k, img.size[1] * k), Image.NEAREST)


def glow_of(img, radius, color, strength=1.0):
    a = img.split()[3].filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        a = a.point(lambda v: min(255, int(v * strength)))
    g = Image.new("RGBA", img.size, (int(color[0]), int(color[1]), int(color[2]), 0))
    g.putalpha(a)
    return g


def over(*layers):
    out = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for l in layers:
        out.alpha_composite(l)
    return out


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def outline(img, color, alpha_min=1):
    """알파가 있는 점 둘레 (4방향) 에 테두리 색"""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] >= alpha_min:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] >= 128:
                    dst[x, y] = color
                    break
    return out


def save_tex(name, img):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, f"{name}.png"))


def save_def(name, data):
    os.makedirs(PART, exist_ok=True)
    with open(os.path.join(PART, f"{name}.particle.json"), "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


def borrow_def(src, name, tw, th):
    """애드온 파티클 정의를 이름 · 그림만 바꿔 씀 (식 · 칸 계산은 그대로). 그림 크기가 같아야 함"""
    path = os.path.join(ADDON_FX, f"{src}.particle.json")
    if not os.path.exists(path):
        print(f"  {name}: 애드온 정의 {path} 없음 — tools/java_fx 의 것을 그대로 둠")
        return
    data = json.load(open(path, encoding="utf-8"))
    pe = data["particle_effect"]
    pe["description"]["identifier"] = f"rpg:{name}"
    pe["description"]["basic_render_parameters"]["texture"] = f"textures/particle/rpg/fx/{name}"
    uv = pe["components"]["minecraft:particle_appearance_billboard"]["uv"]
    assert (uv["texture_width"], uv["texture_height"]) == (tw, th), (name, uv["texture_width"], uv["texture_height"], tw, th)
    save_def(name, data)


# ------------------------------------------------------------ 세계수

BARK_D = (52, 32, 20, 255)
BARK = (98, 66, 36, 255)
BARK_L = (146, 102, 54, 255)
VEIN = (140, 236, 176, 255)  # 결 · 열매: 도끼 날의 민트 수정빛 (0.12.8 — 예전 금빛)
VEIN_C = (222, 255, 232, 255)
LEAF_D = (18, 80, 52, 255)
LEAF = (40, 138, 82, 255)
LEAF_L = (98, 198, 118, 255)
LEAF_G = (196, 222, 92, 255)  # 연둣빛 잎 무리 (도끼 자루의 잎 장식)
LEAF_W = (232, 255, 214, 255)
OUT_D = (14, 34, 22, 255)


def _branch(d, x0, y0, ang, length, w0, w1, col):
    """가지 하나: (x0, y0) 에서 각 ang (위 = -90°) 으로 length, 굵기 w0 → w1 (사다리꼴). 끝 자리"""
    a = math.radians(ang)
    x1, y1 = x0 + math.cos(a) * length, y0 + math.sin(a) * length
    nx, ny = -math.sin(a), math.cos(a)
    d.polygon([(x0 + nx * w0 / 2, y0 + ny * w0 / 2), (x1 + nx * w1 / 2, y1 + ny * w1 / 2), (x1 - nx * w1 / 2, y1 - ny * w1 / 2),
               (x0 - nx * w0 / 2, y0 - ny * w0 / 2)], fill=col)
    return x1, y1


def tree_frame(g, shine, seed=7):
    """세계수 한 장 (128 × 128 판 → 512): g = 자람 (0 ~ 1), shine = 빛 (0 ~ 1). 밑동 = 판 위에서 118.
    넓게 퍼진 우산 모양 잎 (위가 밝음), 굵은 뿌리, 줄기를 따라 흐르는 금빛 결, 잎 아래 매달린 빛 열매"""
    rnd = random.Random(seed)
    S = 128
    base_y = 118
    cx = 64
    H = 14 + 52 * g  # 줄기 (잎 밑까지)
    w0 = 5 + 13 * g
    w1 = 3 + 5 * g
    top = base_y - H

    def trunk_x(t):
        return cx + math.sin(t * 2.4 + 0.5) * 2.0 * g

    body = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    veins = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dv = ImageDraw.Draw(veins)
    # 뿌리: 양옆으로 굵게 뻗어 땅을 움켜쥠 (밑동 판 + 갈래)
    root_tips = []
    if g > 0.2:
        for s in (-1, 1):
            for k, (ang, L, w) in enumerate(((8, 16 + 22 * g, 4 + 4 * g), (26, 10 + 14 * g, 3 + 2 * g))):
                a0 = (180 - ang) if s < 0 else ang
                tip = _branch(d, cx + s * w0 * 0.25, base_y - 2 - 2 * k, a0, L, w, 1, BARK)
                root_tips.append(tip)
                dv.line([(cx, base_y - 3), (tip[0] * 0.85 + cx * 0.15, tip[1])], fill=VEIN, width=1)
    # 줄기 (밑동이 넓게 퍼짐)
    steps = 14
    pl, pr = [], []
    for i in range(steps + 1):
        t = i / steps
        y = base_y - H * t
        w = w0 + (w1 - w0) * (t ** 0.7)
        flare = 1 + 0.9 * max(0.0, 0.18 - t) / 0.18
        pl.append((trunk_x(t) - w * flare / 2, y))
        pr.append((trunk_x(t) + w * flare / 2, y))
    d.polygon(pl + pr[::-1], fill=BARK)
    # 가지 (잎 속으로 — 갈래마다 끝에 잎 덩어리)
    tips = [(trunk_x(1), top)]
    if g > 0.3:
        for s in (-1, 1):
            for k, (t, ang, L) in enumerate(((0.72, 50, 16 + 22 * g), (0.9, 30, 12 + 16 * g))):
                tip = _branch(d, trunk_x(t), base_y - H * t, -90 + s * ang, L, max(2, w1 * 0.8), 1.5, BARK)
                tips.append(tip)
                dv.line([(trunk_x(t), base_y - H * t), tip], fill=VEIN, width=1)
    # 껍질 그늘 (왼쪽 밝게 · 오른쪽 어둡게)
    px = body.load()
    for y in range(S):
        row = [x for x in range(S) if px[x, y][3] > 0]
        if not row:
            continue
        x0, x1 = min(row), max(row)
        for x in row:
            t = (x - x0) / max(1, x1 - x0)
            px[x, y] = BARK_L if t < 0.2 else (BARK_D if t > 0.75 else BARK)
    # 금빛 결: 줄기 가운데를 따라 굽이치는 한 줄 + 가는 곁줄
    main = [(trunk_x(i / steps) + math.sin(i * 1.3) * w0 * 0.12, base_y - 2 - H * i / steps) for i in range(steps + 1) if i / steps <= 0.95]
    if len(main) > 1:
        dv.line(main, fill=VEIN, width=2 if g > 0.6 else 1)
        side = [(x + (w0 * 0.22 if i % 2 else -w0 * 0.18), y) for i, (x, y) in enumerate(main[: max(2, len(main) // 2)])]
        dv.line(side, fill=VEIN, width=1)
    # 잎: 우산 모양 (위 큰 돔 + 둘레 덩어리), 위가 밝음
    leaves = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dl = ImageDraw.Draw(leaves)
    Rx = 6 + 42 * g
    Ry = 5 + 20 * g
    ccx, ccy = trunk_x(1), top - Ry * 0.35
    lobes = [(ccx, ccy - Ry * 0.15, Rx * 0.62, Ry * 0.95)]
    n = 5 + int(6 * g)
    for i in range(n):
        a = math.radians(180 + 180 * (i + 0.5) / n)
        lobes.append((ccx + math.cos(a) * Rx * 0.62, ccy + math.sin(a) * Ry * 0.45 + Ry * 0.25, Rx * rnd.uniform(0.3, 0.42), Ry * rnd.uniform(0.6, 0.8)))
    for (tx, ty) in tips[1:]:
        lobes.append((tx, ty - 1, Rx * 0.28, Ry * 0.5))
    for (x, y, rx, ry) in lobes:
        dl.ellipse([x - rx, y - ry, x + rx, y + ry], fill=LEAF)
    lp = leaves.load()
    top_y = ccy - Ry * 1.1
    bot_y = ccy + Ry * 0.9
    for y in range(S):
        for x in range(S):
            if lp[x, y][3] == 0:
                continue
            # 밝기: 위 · 왼쪽이 밝음 + 덩어리마다 둥근 그늘 (가장 가까운 덩어리의 위쪽이 밝음)
            hy = (y - top_y) / max(1, bot_y - top_y)
            local = 1.0
            for (lx, ly, rx, ry) in lobes:
                dd = ((x - lx) / max(1, rx)) ** 2 + ((y - ly) / max(1, ry)) ** 2
                if dd <= 1:
                    local = min(local, (y - ly) / max(1, ry) * 0.6 + dd * 0.4)
            v = hy * 0.55 + local * 0.45 - (x - ccx) / max(1, Rx) * -0.08 + rnd.uniform(-0.07, 0.07)
            col = LEAF_L if v < 0.18 else (LEAF if v < 0.55 else LEAF_D)
            # 잎결: 띄엄띄엄 한 단계 어둡거나 밝은 점 (잎 뭉치 느낌)
            r = rnd.random()
            if r < 0.07:
                col = LEAF_D if col != LEAF_D else LEAF
            elif r < 0.11 and col != LEAF_L:
                col = LEAF_L if col == LEAF else LEAF
            lp[x, y] = col
    # 금빛 잎 무리 (위쪽 · 2 × 2) — 빛날수록 많음
    for i in range(int(6 + 18 * g * (0.5 + shine))):
        a = math.radians(rnd.uniform(195, 345))
        rr = rnd.uniform(0.25, 0.95)
        x = int(ccx + math.cos(a) * Rx * 0.75 * rr)
        y = int(ccy + math.sin(a) * Ry * 0.9 * rr)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if 0 <= x + dx < S and 0 <= y + dy < S and lp[x + dx, y + dy][3]:
                lp[x + dx, y + dy] = LEAF_W if (dx, dy) == (0, 0) else LEAF_G
    # 결은 줄기 · 뿌리 위에만 (잎이 덮음 — 잎 속 가지의 결은 보이지 않게)
    tree = over(body, veins, leaves)
    tree = outline(tree, OUT_D)
    # 잎 아래 매달린 빛 열매 + 떠다니는 빛 알갱이
    motes = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dm = ImageDraw.Draw(motes)
    if g > 0.5:
        for i in range(5 + int(5 * shine)):
            x = int(ccx + rnd.uniform(-0.85, 0.85) * Rx)
            yb = None
            for y in range(S - 1, -1, -1):
                if tree.getpixel((max(0, min(S - 1, x)), y))[3] and y < base_y - 8:
                    yb = y
                    break
            if yb is None:
                continue
            L = rnd.randint(2, 6)
            dm.line([(x, yb + 1), (x, yb + L)], fill=(30, 60, 30, 255))
            dm.rectangle([x - 1, yb + L, x, yb + L + 1], fill=VEIN)
            motes.putpixel((x - 1, yb + L), VEIN_C)
    mp = motes.load()
    for i in range(int(8 + 30 * shine * g)):
        x = int(cx + rnd.gauss(0, 18 + 20 * g))
        y = int(base_y - (H + Ry) * rnd.uniform(0.05, 1.3))
        if 0 <= x < S and 0 <= y < S and tree.getpixel((x, y))[3] == 0:
            mp[x, y] = LEAF_W if rnd.random() < 0.5 else VEIN
    big = up4(over(tree, motes))
    # 빛: 결 · 금빛 잎 · 열매 · 알갱이가 빛남 + 나무 둘레 은은한 초록빛 (빛날수록 큼)
    gold = Image.new("RGBA", big.size, (0, 0, 0, 0))
    gp, bp = gold.load(), big.load()
    hot = {LEAF_G[:3], LEAF_W[:3], VEIN[:3], VEIN_C[:3]}
    for y in range(big.size[1]):
        for x in range(big.size[0]):
            p = bp[x, y]
            if p[3] and p[:3] in hot:
                gp[x, y] = p
    halo = glow_of(gold, 7, (150, 245, 190), 1.0 + 1.4 * shine)
    aura = glow_of(big, 20, (110, 235, 165), 0.2 + 0.55 * shine * g)
    return over(aura, halo, big)


def yggdrasil_sheet():
    frames = [tree_frame(0.22, 0.2), tree_frame(0.55, 0.4), tree_frame(1.0, 0.6), tree_frame(1.0, 1.0)]
    sheet = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, ((i % 2) * 512, (i // 2) * 512))
    return sheet


# ------------------------------------------------------------ 가시뿌리 (rfx_rock 과 같은 칸: 128 × 128, 위 줄 = 나무 (회색 — 색을 입힘) · 아래 줄 = 빛줄기)

def thorn_cell(v, core):
    """가시뿌리 한 칸 (32 × 32 판 → 128): v = 모양 (0 하나 · 1 둘 · 2 셋), core = 빛줄기 장. 밑동 y 29 (rfx_rock 정의의 밑동 자리)"""
    S = 32
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = 29
    # (밑동 x, 끝 높이, 밑동 굵기, 휨) — 휨은 끝이 옆으로 갈고리처럼 굽는 정도
    spikes = [[(16, 3, 11, 5)], [(12, 6, 9, -4), (21, 12, 7, 4)], [(9, 11, 7, -3), (17, 3, 9, 3), (25, 14, 6, 4)]][v]
    for (x, tip_y, w, bend) in spikes:
        n = 14
        pl, pr, mid = [], [], []
        for i in range(n + 1):
            t = i / n
            y = base - (base - tip_y) * t
            xx = x + bend * (t ** 2.2)
            ww = w * (1 - t) ** 1.25 + 0.3
            pl.append((xx - ww / 2, y))
            pr.append((xx + ww / 2, y))
            mid.append((xx, y))
        if core:
            d.line(mid[1:-1], fill=(255, 255, 255, 255), width=1)
            continue
        d.polygon(pl + pr[::-1], fill=(170, 170, 170, 255))
        # 곁가시: 양옆으로 위를 향한 갈고리 (밑동 쪽이 큼)
        for k, t in enumerate((0.28, 0.5, 0.7)):
            if t * (base - tip_y) < 4:
                continue
            y = base - (base - tip_y) * t
            xx = x + bend * (t ** 2.2)
            ww = w * (1 - t) ** 1.25
            sg = 1 if (k + v) % 2 == 0 else -1
            L = 3.5 * (1 - t) + 1.5
            d.polygon([(xx + sg * ww * 0.35, y + 1.5), (xx + sg * (ww * 0.5 + L), y - L * 0.9), (xx + sg * ww * 0.35, y - 1)], fill=(170, 170, 170, 255))
    if not core:
        p = img.load()
        for y in range(S):
            row = [x for x in range(S) if p[x, y][3]]
            if not row:
                continue
            for x in row:
                left = x == 0 or p[x - 1, y][3] == 0
                right = x == S - 1 or p[x + 1, y][3] == 0
                p[x, y] = (222, 222, 222, 255) if left else ((105, 105, 105, 255) if right else (160, 160, 160, 255))
        # 나뭇결: 세로로 어두운 점선
        for (x, tip_y, w, bend) in spikes:
            for y in range(int(tip_y) + 3, base, 3):
                t = (base - y) / (base - tip_y)
                xx = int(round(x + bend * (t ** 2.2) + 1))
                if 0 <= xx < S and p[xx, y][3]:
                    p[xx, y] = (128, 128, 128, 255)
        img = outline(img, (36, 36, 36, 255))
    big = up4(img)
    if core:
        big = over(glow_of(big, 5, (255, 255, 255), 1.5), big)
    return big


def thorn_sheet():
    sheet = Image.new("RGBA", (384, 256), (0, 0, 0, 0))
    for v in range(3):
        sheet.alpha_composite(thorn_cell(v, False), (v * 128, 0))
        sheet.alpha_composite(thorn_cell(v, True), (v * 128, 128))
    return sheet


# ------------------------------------------------------------ 잎 (흰 잎 — 색을 입힘)

def leaf_cell(v):
    S = 16
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if v == 0:  # 버들잎
        d.polygon([(2, 13), (6, 6), (13, 2), (10, 9)], fill=(235, 235, 235, 255))
        d.line([(3, 12), (12, 3)], fill=(180, 180, 180, 255))
    elif v == 1:  # 둥근 잎
        d.ellipse([3, 3, 13, 12], fill=(235, 235, 235, 255))
        d.line([(4, 12), (11, 5)], fill=(180, 180, 180, 255))
    elif v == 2:  # 꽃잎
        d.ellipse([5, 2, 11, 14], fill=(240, 240, 240, 255))
        d.line([(8, 4), (8, 12)], fill=(205, 205, 205, 255))
    else:  # 작은 세 갈래 잎
        for a in (-35, 0, 35):
            r = math.radians(a - 90)
            x, y = 8 + math.cos(r) * 4, 9 + math.sin(r) * 4
            d.ellipse([x - 2.5, y - 2.5, x + 2.5, y + 2.5], fill=(235, 235, 235, 255))
        d.line([(8, 14), (8, 9)], fill=(190, 190, 190, 255))
    img = outline(img, (120, 120, 120, 255))
    return up4(img)


def leaf_sheet():
    sheet = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    for v in range(4):
        sheet.alpha_composite(leaf_cell(v), (v * 64, 0))
    return sheet


# ------------------------------------------------------------ 빛의 꽃 (위에서 본 꽃)

def bloom_frame(open_t, shine):
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    petals = 8
    # 꽃받침 (초록)
    for i in range(petals):
        a = math.radians(i * 360 / petals + 22.5)
        L = 8 + 10 * min(1, open_t * 1.3)
        x, y = c + math.cos(a) * L, c + math.sin(a) * L
        d.polygon([(c, c), (x + math.cos(a + 1.6) * 3, y + math.sin(a + 1.6) * 3), (x, y), (x + math.cos(a - 1.6) * 3, y + math.sin(a - 1.6) * 3)],
                  fill=(46, 140, 86, 255))
    # 꽃잎 (민트빛 바깥 줄 · 흰빛 안 줄 — 0.12.8 세계수의 도끼 날의 수정빛, 예전 분홍)
    for ring, (Rl, Rw, off) in enumerate(((26, 9, 0), (16, 7, 22.5))):
        if ring == 0 and open_t < 0.25:
            continue
        L = Rl * (0.35 + 0.65 * open_t)
        for i in range(petals):
            a = math.radians(i * 360 / petals + off)
            x, y = c + math.cos(a) * L * 0.55, c + math.sin(a) * L * 0.55
            w = Rw * (0.5 + 0.5 * open_t)
            pts = []
            for k in range(16):
                t = k / 15 * math.pi
                px = math.cos(t) * L * 0.5
                py = math.sin(t) * w * 0.5
                pts.append((x + px * math.cos(a) - py * math.sin(a), y + px * math.sin(a) + py * math.cos(a)))
            for k in range(16):
                t = math.pi + k / 15 * math.pi
                px = math.cos(t) * L * 0.5
                py = math.sin(t) * w * 0.5
                pts.append((x + px * math.cos(a) - py * math.sin(a), y + px * math.sin(a) + py * math.cos(a)))
            d.polygon(pts, fill=(236, 255, 242, 255) if ring else (206, 250, 222, 255))
    # 가운데 (연둣빛 꽃술)
    r = 4 + 3 * open_t
    d.ellipse([c - r, c - r, c + r, c + r], fill=(190, 222, 90, 255))
    d.ellipse([c - r * 0.5, c - r * 0.5, c + r * 0.5, c + r * 0.5], fill=(240, 255, 206, 255))
    img = outline(img, (34, 96, 64, 255))
    big = up4(img)
    halo = glow_of(big, 10, (170, 255, 205), 0.6 + 1.4 * shine)
    return over(halo, big)


def bloom_sheet():
    frames = [bloom_frame(0.05, 0.1), bloom_frame(0.45, 0.3), bloom_frame(1.0, 0.6), bloom_frame(1.0, 1.0)]
    sheet = Image.new("RGBA", (1024, 256), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, (i * 256, 0))
    return sheet


# ------------------------------------------------------------ 스발린 (0.12.8 — 무기 디자인 (0.12.7 사용자 리워크) 의 앞모습을 모델에서 그대로: 남빛 강철 · 하늘빛 룬)

ICE = [(16, 40, 92, 255), (34, 86, 160, 255), (80, 150, 220, 255), (150, 210, 250, 255), (225, 248, 255, 255)]
SVALINN = "shield_transcend_lv0"  # tools/weapon_art 의 스발린 자바 모델 (.java.json · .java.png — 룬이 맥동하는 16장 텍스처)
SV_PPU = 2.0  # 64 × 64 판에 모델 1칸 = 2점 (텍스처 32 점 / 16칸 과 같은 밀도 — 텍스처 한 점 = 판 한 점)
SV_GLOW = (60, 160, 255)  # 룬 빛 (무기 텍스처의 하늘빛 (40,149,240) · (90,184,255))
SV_LIGHT = (155, 217, 255)
SV_CORE = (225, 245, 255)
SV_AURA = (40, 110, 220)  # 방패 둘레 은은한 빛
_SV_CACHE = {}


def _sv_model():
    """스발린 모델 앞모습 (모델 -x 쪽 — 가방 아이콘 'front' 와 같은 방향, 오른쪽 = +z) 을 64 × 64 판에 SV_PPU 로 그릴 광선 · 텍스처 프레임 · 보석 자리"""
    if "m" in _SV_CACHE:
        return _SV_CACHE["m"]
    import numpy as np
    import java_model_view as jmv
    art = os.path.join(ROOT, "tools", "weapon_art")
    jm = json.load(open(os.path.join(art, f"{SVALINN}.java.json"), encoding="utf-8"))
    strip = np.asarray(Image.open(os.path.join(art, f"{SVALINN}.java.png")).convert("RGBA"))
    w = strip.shape[1]
    frames = [strip[i * w:(i + 1) * w] for i in range(strip.shape[0] // w)]
    view = jmv.View(jm["elements"], frames[0])
    lo, hi = view.bounds()
    ym, zm = (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2
    ss = 4
    u, v = np.meshgrid((np.arange(64 * ss) + 0.5) / ss - 32, (np.arange(64 * ss) + 0.5) / ss - 32)
    Y = ym - v.ravel() / SV_PPU
    Z = zm + u.ravel() / SV_PPU
    gem = next(e for e in view.els if e.name == "med_gem").center()
    # 보석 자리 (판 가운데 기준, 반 크기 = 1 — 위가 +, Aegis.AE_EYES)
    eye = ((gem[2] - zm) * SV_PPU / 32, (gem[1] - ym) * SV_PPU / 32)
    _SV_CACHE["m"] = (jm, frames, Y, Z, ss, eye)
    return _SV_CACHE["m"]


def svalinn_eye():
    return _sv_model()[5]


def svalinn_render(frame_i):
    """텍스처 프레임 frame_i 의 앞모습 (64 × 64 점 그림 — 표본 16개 중 가장 많은 색, 반 넘게 덮인 점만) + 빛나는 점 마스크 (룬 · 보석)"""
    import numpy as np
    import java_model_view as jmv
    jm, frames, Y, Z, ss, _ = _sv_model()
    view = jmv.View(jm["elements"], frames[frame_i % len(frames)])
    col, glow, _, _ = jmv.grid_trace(view, Y, Z, from_plus_x=False, with_element=True)
    col = col.reshape(64, ss, 64, ss, 4).transpose(0, 2, 1, 3, 4).reshape(64, 64, ss * ss, 4)
    glow = glow.reshape(64, ss, 64, ss).transpose(0, 2, 1, 3).reshape(64, 64, ss * ss)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    lit = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    p, lp = img.load(), lit.load()
    for y in range(64):
        for x in range(64):
            smp = col[y, x]
            ok = smp[:, 3] >= 128
            if ok.sum() * 2 < ss * ss:
                continue
            keys, counts = np.unique(np.round(smp[ok, :3]).astype(int), axis=0, return_counts=True)
            c = tuple(int(v) for v in keys[counts.argmax()])
            p[x, y] = c + (255,)
            if glow[y, x][ok].mean() > 0.3 and max(c) > 90:
                lp[x, y] = c + (255,)
    return img, lit


def svalinn_face(frame_i, sweep=None, flash=0.0):
    """스발린 한 장 (256 × 256): 모델 앞모습 (텍스처 프레임 frame_i — 룬이 맥동) + 룬 빛 + 둘레 빛, sweep = 지나가는 빛줄기 자리 (-1 ~ 1),
    flash = 가운데 보석 (마름모 문장 · 네 갈래 별) 번쩍임 (0 ~ 1)"""
    img, lit = svalinn_render(frame_i)
    S = 64
    c = (S - 1) / 2
    img = outline(img, (8, 12, 22, 255))
    if sweep is not None:
        q = img.load()
        for y in range(S):
            for x in range(S):
                if q[x, y][3] == 0:
                    continue
                uu = (x - c + (y - c) * 0.6) / 40
                if abs(uu - sweep) < 0.09:
                    q[x, y] = mix(q[x, y], (190, 230, 255, 255), 0.6)
    big = up4(img)
    lit_big = up4(lit)
    layers = [glow_of(big, 14, SV_AURA, 0.45), glow_of(lit_big, 7, SV_GLOW, 1.5), big, glow_of(lit_big, 3, SV_LIGHT, 0.6)]
    if flash > 0:
        ex, ey = svalinn_eye()
        gx, gy = 128 + ex * 128, 128 - ey * 128
        core = Image.new("RGBA", big.size, (0, 0, 0, 0))
        rr = 18 + 34 * flash
        ImageDraw.Draw(core).ellipse([gx - rr, gy - rr, gx + rr, gy + rr], fill=SV_LIGHT + (255,))
        rays = Image.new("RGBA", big.size, (0, 0, 0, 0))
        dr = ImageDraw.Draw(rays)
        for k in range(8):
            a = math.radians(k * 45)
            L = min(116.0, 60 + 56 * flash) * (1.0 if k % 2 == 0 else 0.5)  # 문장의 네 갈래 별 — 위아래 · 옆이 길고 대각선은 짧게 (칸 안에서)
            dr.line([(gx, gy), (gx + math.cos(a) * L, gy + math.sin(a) * L)], fill=SV_CORE + (255,), width=6 if k % 2 == 0 else 4)
        dot = Image.new("RGBA", big.size, (0, 0, 0, 0))
        r0 = 6 + 10 * flash
        ImageDraw.Draw(dot).ellipse([gx - r0, gy - r0, gx + r0, gy + r0], fill=(255, 255, 255, 255))
        layers += [glow_of(core, 16, SV_GLOW, 1.6 * flash), glow_of(rays, 4, SV_LIGHT, 1.6 * flash), glow_of(dot, 6, SV_CORE, 2.0 * flash), dot]
    return over(*layers)


def svalinn_dissolve(base, k):
    """흩어짐 (k = 0 ~ 3): 조각이 바깥으로 밀려나며 사라짐"""
    rnd = random.Random(77 + k)
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    t = (k + 1) / 4
    for by in range(0, 256, 16):
        for bx in range(0, 256, 16):
            if rnd.random() < t * 0.85:
                continue
            tile = base.crop((bx, by, bx + 16, by + 16))
            if tile.getbbox() is None:
                continue
            dx, dy = bx + 8 - 128, by + 8 - 128
            dist = math.hypot(dx, dy) or 1
            push = 40 * t
            nx, ny = int(bx + dx / dist * push), int(by + dy / dist * push)
            a = tile.split()[3].point(lambda v: int(v * (1 - 0.6 * t)))
            tile.putalpha(a)
            out.alpha_composite(tile, (max(0, min(240, nx)), max(0, min(240, ny))))
    return out


def svalinn_sheet():
    """12장 (애드온 aegis_shield 와 같은 칸): 0 ~ 3 빛이 지나감 (텍스처 프레임 1 · 4 · 8 · 12 — 룬이 일렁임), 4 ~ 7 가운데 보석이 번쩍, 8 ~ 11 흩어짐"""
    sheet = Image.new("RGBA", (1024, 768), (0, 0, 0, 0))
    for i, fi in enumerate((1, 4, 8, 12)):
        sheet.alpha_composite(svalinn_face(fi, sweep=-1.2 + i * 0.8), (i * 256, 0))
    for i in range(4):
        sheet.alpha_composite(svalinn_face(1, flash=(i + 1) / 4), (i * 256, 256))
    base = svalinn_face(1)
    for i in range(4):
        sheet.alpha_composite(svalinn_dissolve(base, i), (i * 256, 512))
    return sheet


# 일식 코로나 (어두운 → 밝은): 스발린 룬의 하늘빛 → 흰빛 (0.12.8 — 예전 불꽃 코로나)
CORONA = [(14, 36, 104, 255), (34, 96, 206, 255), (76, 166, 255, 255), (168, 224, 255, 255), (238, 250, 255, 255)]


def eclipse_cell(kind, seed):
    """일식 한 장 (64 × 64 판 → 256): kind 0 = 해를 가린 스발린 (남빛 강철 원판 · 하늘빛 테 · 문장의 마름모 · 네 갈래 별 + 해의 테두리 · 푸른 흰빛 코로나),
    1 = 다이아몬드 고리 (테 한쪽이 번쩍 — 해가 드러나기 직전), 2 = 드러난 해 (흰빛 원판 + 큰 코로나). seed = 코로나 줄기 (장마다 일렁임)"""
    S = 64
    c = (S - 1) / 2
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    p = img.load()
    rnd = random.Random(300 + seed)
    rays = []
    n = 16
    for i in range(n):
        a0 = (i + rnd.uniform(-0.3, 0.3)) / n * 2 * math.pi
        L = rnd.uniform(4.0, 10.5) * (1.25 if kind == 2 else 1.0)
        rays.append((a0, L, rnd.uniform(0.09, 0.17)))
    disc = 16.5
    limb = 18.2
    for y in range(S):
        for x in range(S):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            ext = limb + 1.6
            for a0, L, wd in rays:
                da = (a - a0 + math.pi) % (2 * math.pi) - math.pi
                if abs(da) < wd:
                    ext = max(ext, limb + 1.6 + L * (1 - (da / wd) ** 2))
            if r <= disc:
                if kind == 2:
                    t = r / disc
                    p[x, y] = CORONA[4] if t < 0.45 else (CORONA[3] if t < 0.8 else CORONA[2])
                    continue
                # 해를 가린 방패: 남빛 강철 + 가장자리 하늘빛 테 + 문장 (마름모 테 · 네 갈래 별 — 무기 디자인의 가운데 문장, 흐리게)
                col = (12, 16, 28, 255)
                diamond = abs(dx) + abs(dy)
                if 8.6 < diamond < 10.4:
                    col = (40, 64, 104, 255)
                if (abs(dx) < 0.9 and abs(dy) < 13) or (abs(dy) < 0.9 and abs(dx) < 13):
                    col = (34, 70, 130, 255)
                if r < 2.2:
                    col = (60, 150, 240, 255)
                if r > disc - 1.6:
                    col = (40, 110, 210, 255)
                p[x, y] = col
            elif r <= limb:
                p[x, y] = CORONA[4] if kind != 2 else (250, 253, 255, 255)
            elif r <= ext:
                t = (r - limb) / max(0.5, ext - limb)
                idx = 4 if t < 0.12 else (3 if t < 0.35 else (2 if t < 0.7 else 1))
                p[x, y] = CORONA[idx]
    big = up4(img)
    corona = Image.new("RGBA", big.size, (0, 0, 0, 0))
    bp, cp = big.load(), corona.load()
    for y in range(big.size[1]):
        for x in range(big.size[0]):
            col = bp[x, y]
            if col[3] and col in (CORONA[2], CORONA[3], CORONA[4], (250, 253, 255, 255)):
                cp[x, y] = col
    layers = [glow_of(corona, 14, (50, 130, 255), 1.5), glow_of(corona, 5, (170, 225, 255), 1.0), big]
    if kind == 1:
        # 다이아몬드 고리: 테 오른쪽 위에 흰 빛방울 + 네 갈래 빛살
        fl = Image.new("RGBA", big.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(fl)
        ang = math.radians(-50)
        fx, fy = 128 + math.cos(ang) * 4 * (limb - 0.6), 128 + math.sin(ang) * 4 * (limb - 0.6)
        d.ellipse([fx - 14, fy - 14, fx + 14, fy + 14], fill=(255, 255, 255, 255))
        for k2, L in ((0, 66), (90, 66), (45, 30), (135, 30)):
            aa = math.radians(k2)
            d.line([(fx - math.cos(aa) * L, fy - math.sin(aa) * L), (fx + math.cos(aa) * L, fy + math.sin(aa) * L)], fill=(255, 255, 255, 255), width=5)
        layers += [glow_of(fl, 18, (190, 232, 255), 2.0), fl]
    if kind == 2:
        core = Image.new("RGBA", big.size, (0, 0, 0, 0))
        ImageDraw.Draw(core).ellipse([128 - 64, 128 - 64, 128 + 64, 128 + 64], fill=(236, 248, 255, 255))
        layers.insert(0, glow_of(core, 26, (90, 170, 255), 1.8))
    return over(*layers)


def eclipse_sheet():
    sheet = Image.new("RGBA", (1024, 256), (0, 0, 0, 0))
    for i, (kind, seed) in enumerate(((0, 1), (0, 2), (1, 1), (2, 3))):
        sheet.alpha_composite(eclipse_cell(kind, seed), (i * 256, 0))
    return sheet


def ice_shard(v):
    """얼음 조각 하나 (32 × 32 — 애드온 aegis_stone 과 같은 칸): 모난 결정 (밝은 면 · 어두운 면 · 흰 날 · 남빛 테), v = 모양 0 ~ 3"""
    rnd = random.Random(900 + v)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (15.5, 15.5)
    k = rnd.randint(5, 7)
    pts = []
    rot = rnd.uniform(0, math.pi)
    for i in range(k):
        a = rot + i / k * 2 * math.pi + rnd.uniform(-0.25, 0.25)
        long = 1.0 if i % 2 == 0 else 0.55
        rr = rnd.uniform(9, 14) * (long if v != 3 else 1.0)
        if i == 0:
            rr = 14.5
        pts.append((c[0] + math.cos(a) * rr, c[1] + math.sin(a) * rr * (0.75 if v == 2 else 1.0)))
    d.polygon(pts, fill=ICE[2])
    # 밝은 면: 가운데 → 앞 꼭짓점 셋 (빛을 받는 면)
    for j in range(0, len(pts) - 1, 2):
        d.polygon([c, pts[j], pts[j + 1]], fill=ICE[3])
    d.polygon([c, pts[0], pts[1]], fill=ICE[4])
    # 흰 날: 가운데에서 긴 꼭짓점으로
    for j in range(0, len(pts), 2):
        d.line([c, pts[j]], fill=(255, 255, 255, 255), width=1)
    img = outline(img, ICE[0])
    return img


def ice_sheet():
    sheet = Image.new("RGBA", (128, 32), (0, 0, 0, 0))
    for v in range(4):
        sheet.alpha_composite(ice_shard(v), (v * 32, 0))
    return sheet


def svalinn_hex():
    """애드온 aegis_hex (금빛) 를 밝기만 남긴 흰빛으로 — 색을 입힘"""
    src = Image.open(os.path.join(REPO_FX, "aegis_hex.png")).convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = sp[x, y]
            l = max(r, g, b)
            op[x, y] = (l, l, l, a)
    return out


# ------------------------------------------------------------ 저장

def generic(name, tw, th, cw, ch, facing):
    """java_fx_art.particle 과 같은 꼴 (rfx 변수) — 순환 import 를 피해 그대로 씀"""
    import java_fx_art
    return java_fx_art.particle(name, tw, th, cw, ch, facing)


def main():
    sheet = yggdrasil_sheet()
    save_tex("rfx_yggdrasil", sheet)
    save_def("rfx_yggdrasil", generic("rfx_yggdrasil", 1024, 1024, 512, 512, "lookat_y"))
    print("rfx_yggdrasil: 1024x1024 (512x512 cells, lookat_y)")
    sheet = thorn_sheet()
    save_tex("rfx_thorn", sheet)
    borrow_def("rfx_rock", "rfx_thorn", 384, 256)
    print("rfx_thorn: 384x256 (128x128 cells — rfx_rock 정의)")
    sheet = leaf_sheet()
    save_tex("rfx_leaf", sheet)
    save_def("rfx_leaf", generic("rfx_leaf", 256, 64, 64, 64, "rotate_xyz"))
    print("rfx_leaf: 256x64 (64x64 cells, rotate_xyz)")
    sheet = bloom_sheet()
    save_tex("rfx_bloom", sheet)
    save_def("rfx_bloom", generic("rfx_bloom", 1024, 256, 256, 256, "rotate_xyz"))
    save_tex("rfx_bloomflat", sheet)
    save_def("rfx_bloomflat", generic("rfx_bloomflat", 1024, 256, 256, 256, "direction_z"))
    print("rfx_bloom · rfx_bloomflat: 1024x256 (256x256 cells, rotate_xyz · direction_z)")
    sheet = svalinn_sheet()
    save_tex("svalinn_shield", sheet)
    borrow_def("aegis_shield", "svalinn_shield", 1024, 768)
    print("svalinn_shield: 1024x768 (aegis_shield 정의)")
    hexes = svalinn_hex()
    save_tex("svalinn_hex", hexes)
    borrow_def("aegis_hex", "svalinn_hex", 128, 64)
    print("svalinn_hex: 128x64 (aegis_hex 정의)")
    sheet = eclipse_sheet()
    save_tex("rfx_eclipse", sheet)
    save_def("rfx_eclipse", generic("rfx_eclipse", 1024, 256, 256, 256, "rotate_xyz"))
    print("rfx_eclipse: 1024x256 (256x256 cells, rotate_xyz)")
    sheet = ice_sheet()
    save_tex("svalinn_ice", sheet)
    borrow_def("aegis_stone", "svalinn_ice", 128, 32)
    print("svalinn_ice: 128x32 (aegis_stone 정의)")


if __name__ == "__main__":
    main()
