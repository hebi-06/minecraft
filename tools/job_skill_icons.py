#!/usr/bin/env python3
"""0.27.0 직업 스킬 아이콘 (메이플식 네모 스킬 그림 32×32 — 룬 그림 대신) → assets/mmorpg/textures/gui/sprites/job_skill/<스킬 id>.png

  python3 tools/job_skill_icons.py                 # 아이콘을 씀
  python3 tools/job_skill_icons.py --sheet a.png   # 한눈에 보기 (8배 · 어두운 칸 위)

그림 방식: 8배로 그린 모양 (칼 · 베기 초승달 · 고리 · 번개 …) 을 32×32 로 줄여 픽셀 경계를 또렷하게 (반 넘게 덮인 칸만), 모양마다 두 빛
(jobs.json color — 첫째 빛 · 둘째 빛) 사이 색 띠 + 위왼쪽 밝은 줄 · 아래오른쪽 어두운 줄, 바깥에 1픽셀 검은 테두리. 뒤에는 직업 빛의 은은한 둥근 빛
(가운데가 밝음 — 어두운 칸 위에서 스킬마다 다른 빛이 보이게). 모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음).
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "job_skill")
JOBS = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "jobs.json")

N = 32
SS = 8
W = N * SS
OUTLINE = (12, 13, 24, 255)


# ------------------------------------------------------------ 모양 (8배 L 마스크)

def mask():
    return Image.new("L", (W, W), 0)


def P(x, y):
    return (x * SS, y * SS)


def poly(m, pts):
    ImageDraw.Draw(m).polygon([P(x, y) for x, y in pts], fill=255)
    return m


def line(m, pts, w):
    d = ImageDraw.Draw(m)
    d.line([P(x, y) for x, y in pts], fill=255, width=max(1, int(w * SS)), joint="curve")
    r = w * SS / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x * SS - r, y * SS - r, x * SS + r, y * SS + r], fill=255)
    return m


def disc(m, cx, cy, r):
    ImageDraw.Draw(m).ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=255)
    return m


def ring(m, cx, cy, r, w):
    d = ImageDraw.Draw(m)
    d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=255)
    d.ellipse([(cx - r + w) * SS, (cy - r + w) * SS, (cx + r - w) * SS, (cy + r - w) * SS], fill=0)
    return m


def crescent(m, cx, cy, r, w, a0, a1, taper=True, steps=48):
    """베기 초승달: 반지름 r 의 바깥 호 (a0 → a1 도, 화면 좌표 — 0 = 오른쪽, 90 = 아래) 와 안쪽 호 (두께 w — 가운데가 가장 두껍고 끝이 뾰족)"""
    outer, inner = [], []
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0 + (a1 - a0) * t)
        k = math.sin(math.pi * t) if taper else 1.0
        outer.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        rr = r - w * k
        inner.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return poly(m, outer + inner[::-1])


def star(m, cx, cy, r, thin=0.22, rot=0.0):
    """네 갈래 반짝임"""
    pts = []
    for i in range(8):
        a = math.radians(rot + i * 45)
        rr = r if i % 2 == 0 else r * thin
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return poly(m, pts)


def sword(m, tip, end, blade=2.2, guard=6.0, grip=0.38, parts=None):
    """칼: tip (끝) → end (자루 끝). 날은 끝에서 뾰족 · 날 길이 = 전체의 0.72, 코등이 · 자루 · 자루 끝 구슬. parts 가 있으면 날 · 코등이 · 자루를 따로 그림"""
    tx, ty = tip
    ex, ey = end
    L = math.hypot(ex - tx, ey - ty)
    ux, uy = (ex - tx) / L, (ey - ty) / L  # 끝 → 자루
    px, py = -uy, ux
    g = 0.72 * L
    gx, gy = tx + ux * g, ty + uy * g
    hb = blade / 2
    b = parts["blade"] if parts else m
    poly(b, [(tx, ty), (tx + ux * hb * 2.2 + px * hb, ty + uy * hb * 2.2 + py * hb), (gx + px * hb, gy + py * hb), (gx - px * hb, gy - py * hb),
             (tx + ux * hb * 2.2 - px * hb, ty + uy * hb * 2.2 - py * hb)])
    gd = parts["guard"] if parts else m
    hg = guard / 2
    poly(gd, [(gx + px * hg - ux * 0.55, gy + py * hg - uy * 0.55), (gx + px * hg + ux * 0.75, gy + py * hg + uy * 0.75),
              (gx - px * hg + ux * 0.75, gy - py * hg + uy * 0.75), (gx - px * hg - ux * 0.55, gy - py * hg - uy * 0.55)])
    gr = parts["grip"] if parts else m
    s = g + 0.75
    hgp = max(0.6, blade * grip)
    poly(gr, [(tx + ux * s + px * hgp, ty + uy * s + py * hgp), (ex - ux * 1.0 + px * hgp, ey - uy * 1.0 + py * hgp),
              (ex - ux * 1.0 - px * hgp, ey - uy * 1.0 - py * hgp), (tx + ux * s - px * hgp, ty + uy * s - py * hgp)])
    disc(gd, ex - ux * 0.6, ey - uy * 0.6, max(0.9, blade * 0.5))
    return m


def bolt(m, pts, w):
    return line(m, pts, w)


# ------------------------------------------------------------ 32×32 로 줄이기 · 칠하기

def solid(mk, thr=110):
    """8배 마스크 → 32×32 또렷한 마스크 (반쯤 덮인 칸 기준 thr)"""
    return mk.resize((N, N), Image.BOX).point(lambda v: 255 if v >= thr else 0)


def soft(mk, blur=0.0):
    s = mk.resize((N, N), Image.BOX)
    return s.filter(ImageFilter.GaussianBlur(blur)) if blur > 0 else s


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rgb(c):
    return tuple(int(round(v * 255)) for v in c)


def paint(img, m, top, bottom, light=None, dark=None, axis=(0.0, 1.0)):
    """마스크 칸을 색 띠 (axis 쪽으로 top → bottom) 로 칠함. 위왼쪽이 바깥인 칸은 light, 아래오른쪽이 바깥인 칸은 dark"""
    px = img.load()
    mp = m.load()
    ax, ay = axis
    span = abs(ax) * N + abs(ay) * N
    for y in range(N):
        for x in range(N):
            if mp[x, y] < 128:
                continue
            t = ((x * ax + y * ay) - min(0, ax) * N - min(0, ay) * N) / max(1, span)
            c = mix(top, bottom, t)
            if light and ((x == 0 or mp[x - 1, y] < 128) or (y == 0 or mp[x, y - 1] < 128)):
                c = light
            elif dark and ((x == N - 1 or mp[x + 1, y] < 128) or (y == N - 1 or mp[x, y + 1] < 128)):
                c = dark
            px[x, y] = c + (255,)


def outline(img, color=OUTLINE):
    """불투명 칸 바깥 1픽셀 (상하좌우) 을 검은 테두리로"""
    a = img.split()[3].point(lambda v: 255 if v >= 200 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    # 대각선은 빼고 상하좌우만: MaxFilter(3) 는 대각선까지 — 십자로 다시 만듦
    ap = a.load()
    edge = Image.new("L", (N, N), 0)
    ep = edge.load()
    for y in range(N):
        for x in range(N):
            if ap[x, y]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < N and 0 <= yy < N and ap[xx, yy]:
                    ep[x, y] = 255
                    break
    del grown
    ol = Image.new("RGBA", (N, N), color)
    ol.putalpha(edge)
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    out.alpha_composite(ol)
    out.alpha_composite(img)
    return out


def glow(m, color, blur, alpha):
    """부드러운 빛 (마스크를 흐리게)"""
    s = soft(m, blur)
    g = Image.new("RGBA", (N, N), color + (0,))
    g.putalpha(s.point(lambda v: int(min(255, v * alpha))))
    return g


def backdrop(c0, c1, strength=1.0):
    """뒤의 둥근 빛 (가운데 c1 → 가장자리 c0, 가장자리는 투명)"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2)
            if d >= 1.0:
                continue
            t = 1 - d
            a = int(min(255, (40 + 150 * t * t) * strength))
            px[x, y] = mix(c0, c1, t) + (a,)
    return img


def layer(m, top, bottom, light=None, dark=None, axis=(0.0, 1.0)):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    paint(img, solid(m), top, bottom, light, dark, axis)
    return img


def stack(*layers):
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for l in layers:
        out.alpha_composite(l)
    return out


# ------------------------------------------------------------ 색

WHITE = (255, 255, 255)
STEEL_HI = (244, 250, 255)
STEEL = (196, 212, 232)
STEEL_LO = (112, 128, 156)
GOLD_HI = (255, 236, 150)
GOLD = (226, 170, 70)
GOLD_LO = (138, 92, 34)
GRIP = (70, 52, 92)
GRIP_LO = (40, 28, 56)


def sword_layers(tip, end, blade=2.4, guard=6.5, blade_cols=None, guard_cols=None, grip_cols=None):
    parts = {"blade": mask(), "guard": mask(), "grip": mask()}
    sword(None, tip, end, blade, guard, parts=parts)
    bc = blade_cols or (STEEL_HI, STEEL, WHITE, STEEL_LO)
    gc = guard_cols or (GOLD_HI, GOLD, GOLD_HI, GOLD_LO)
    pc = grip_cols or (GRIP, GRIP_LO, None, None)
    return [layer(parts["grip"], pc[0], pc[1], pc[2], pc[3]),
            layer(parts["blade"], bc[0], bc[1], bc[2], bc[3], axis=(0.7, 0.7)),
            layer(parts["guard"], gc[0], gc[1], gc[2], gc[3])]


def finish(subject, glow_layers, back):
    img = stack(back, *glow_layers)
    s = outline(subject)
    img.alpha_composite(s)
    return img


# ------------------------------------------------------------ 검사 (빛: 흰 푸른빛 → 하늘색)

def swordsman_flurry(A, B):
    # 세 줄 베기: 위왼쪽을 가운데로 한 겹 호 셋 (안쪽부터 차례로 — 부채꼴로 휘두른 자국)
    ms = []
    for r, w in ((11.5, 2.6), (18.5, 3.2), (25.5, 3.6)):
        m = mask()
        crescent(m, 2.5, 2.5, r, w, 4, 86)
        ms.append(m)
    sp = mask()
    star(sp, 27.2, 6.0, 3.4)
    star(sp, 6.0, 27.2, 2.6)
    star(sp, 21.0, 21.0, 2.0)
    dark = mix(B, (0, 0, 0), 0.38)
    slashes = stack(*[layer(m, WHITE, B, WHITE, dark, axis=(0.7, 0.7)) for m in ms], layer(sp, WHITE, A, WHITE, None))
    allm = ImageChops.lighter(ImageChops.lighter(ms[0], ms[1]), ms[2])
    return finish(slashes, [glow(allm, B, 1.6, 0.95)], backdrop(mix(B, (8, 10, 30), 0.85), mix(B, (10, 20, 50), 0.35)))


def swordsman_flash(A, B):
    # 옆으로 누운 칼이 오른쪽으로 내달림: 뒤에 흐린 잔상 칼 · 빠른 줄 셋, 칼끝 반짝임
    lines = mask()
    for y, x0, x1, w in ((10.0, 2.0, 11.5, 1.2), (15.5, 1.0, 8.0, 1.2), (21.0, 2.5, 12.5, 1.2)):
        line(lines, [(x0, y), (x1, y)], w)
    ghost = sword_layers((22.0, 15.5), (6.0, 15.5), blade=2.8, guard=6.4,
                         blade_cols=(mix(B, (10, 20, 50), 0.45), mix(B, (10, 20, 50), 0.6), None, None),
                         guard_cols=(mix(B, (10, 20, 50), 0.55), mix(B, (10, 20, 50), 0.7), None, None),
                         grip_cols=(mix(B, (10, 20, 50), 0.75), mix(B, (10, 20, 50), 0.8), None, None))
    sw = sword_layers((30.0, 15.5), (13.5, 15.5), blade=3.0, guard=7.0)
    sp = mask()
    star(sp, 29.0, 15.5, 4.2, 0.2)
    subject = stack(layer(lines, WHITE, A, None, None, axis=(1, 0)), *sw, layer(sp, WHITE, WHITE))
    gl = ImageChops.lighter(lines, sp)
    back = backdrop(mix(B, (8, 10, 30), 0.85), mix(A, (20, 30, 60), 0.4))
    back.alpha_composite(stack(*ghost))
    return finish(subject, [glow(gl, B, 1.6, 1.0)], back)


def swordsman_moon(A, B):
    arc = mask()
    crescent(arc, 16, 16, 13.2, 4.4, -60, 250)
    tipm = mask()
    star(tipm, 22.6, 4.6, 3.4)
    sw = sword_layers((22.0, 9.5), (9.5, 22.0), blade=2.4, guard=5.6)
    moon = layer(arc, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.4), axis=(-0.7, 0.7))
    subject = stack(moon, *sw, layer(tipm, WHITE, WHITE))
    return finish(subject, [glow(arc, B, 1.8, 1.0)], backdrop(mix(B, (8, 10, 30), 0.88), mix(B, (12, 24, 60), 0.45)))


def swordsman_mirror(A, B):
    water = mask()
    poly(water, [(2, 20.2), (30, 20.2), (30, 21.6), (2, 21.6)])
    rip = mask()
    for r, w in ((6.0, 1.1), (10.5, 1.0), (14.5, 0.9)):
        d = ImageDraw.Draw(rip)
        d.ellipse([(16 - r) * SS, (21 - r * 0.32) * SS, (16 + r) * SS, (21 + r * 0.32) * SS], outline=255, width=int(w * SS))
    sw = sword_layers((16.0, 2.0), (16.0, 19.0), blade=2.8, guard=7.0)
    refl = mask()
    poly(refl, [(14.8, 22.6), (17.2, 22.6), (16.6, 29.0), (16.0, 30.5), (15.4, 29.0)])
    drops = mask()
    star(drops, 24.5, 9.0, 2.6)
    star(drops, 8.0, 12.0, 1.9)
    calm = stack(layer(rip, mix(A, B, 0.3), B, None, None), layer(water, WHITE, A, WHITE, None),
                 layer(refl, mix(A, B, 0.5), mix(B, (0, 0, 0), 0.25)), layer(drops, WHITE, A))
    subject = stack(calm, *sw)
    return finish(subject, [glow(ImageChops.lighter(water, rip), B, 1.5, 0.8)],
                  backdrop(mix(B, (6, 12, 26), 0.82), mix(B, (20, 50, 80), 0.4)))


def swordsman_ult(A, B):
    # 하늘에서 쏟아지는 칼날: 가운데 큰 칼 (끝이 아래) + 비스듬히 떨어지는 칼날 다섯 (흰 쐐기 · 뒤에 짧은 빛줄기), 아래에 닿는 빛
    blades = mask()
    trails = mask()
    for (x, y, L) in ((6.5, 13.0, 9.0), (25.5, 11.0, 9.0), (5.0, 27.5, 8.0), (27.5, 26.5, 8.0), (11.0, 6.5, 6.0)):
        ux, uy = 0.42, -0.91  # 끝 (아래왼쪽) → 뒤 (위오른쪽)
        px, py = -uy, ux
        poly(blades, [(x, y), (x + ux * L + px * 1.1, y + uy * L + py * 1.1), (x + ux * L - px * 1.1, y + uy * L - py * 1.1)])
        line(trails, [(x + ux * (L + 0.8), y + uy * (L + 0.8)), (x + ux * (L + 4.5), y + uy * (L + 4.5))], 0.8)
    big = sword_layers((16.0, 29.5), (16.0, 3.0), blade=4.0, guard=10.0)
    spark = mask()
    star(spark, 16.0, 29.0, 4.6, 0.2)
    burst = mask()
    disc(burst, 16, 29, 6.5)
    subject = stack(layer(trails, mix(A, B, 0.5), B), layer(blades, WHITE, A, WHITE, mix(B, (0, 0, 0), 0.3), axis=(0, -1)), *big,
                    layer(spark, WHITE, WHITE))
    return finish(subject, [glow(burst, B, 2.0, 0.8), glow(blades, B, 1.0, 0.8)],
                  backdrop(mix(B, (8, 10, 30), 0.84), mix(A, (30, 40, 70), 0.35)))


# ------------------------------------------------------------ 마검사 (빛: 보라 → 청록)

def spellblade_arcslash(A, B):
    # 보라 초승달 베기 (아래왼쪽으로 불룩) + 뒤의 칼 · 위오른쪽 마력 각인 (청록 마름모)
    arc = mask()
    crescent(arc, 27.0, 5.0, 21.5, 5.0, 92, 178)
    mark = mask()
    poly(mark, [(25.5, 3.0), (30.0, 7.5), (25.5, 12.0), (21.0, 7.5)])
    hole = mask()
    poly(hole, [(25.5, 5.6), (27.4, 7.5), (25.5, 9.4), (23.6, 7.5)])
    mark = ImageChops.subtract(mark, hole)
    sp = mask()
    star(sp, 9.0, 6.0, 2.6)
    star(sp, 28.0, 25.0, 2.6)
    sw = sword_layers((26.0, 22.0), (12.0, 8.0), blade=2.4, guard=5.6, blade_cols=(WHITE, mix(A, WHITE, 0.55), WHITE, mix(A, (0, 0, 0), 0.3)))
    subject = stack(*sw, layer(arc, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.4), axis=(1, -1)), layer(mark, B, mix(B, WHITE, 0.5), WHITE, None),
                    layer(sp, WHITE, B))
    return finish(subject, [glow(arc, A, 1.8, 1.0), glow(mark, B, 1.2, 0.9)], backdrop(mix(A, (10, 4, 26), 0.85), mix(A, (30, 10, 60), 0.5)))


def spellblade_blink(A, B):
    # 아래왼쪽 납작한 보라 고리 (떠난 자리의 문) → 꺾인 번개 → 위오른쪽 청록 터짐 (닿은 자리)
    zig = mask()
    bolt(zig, [(9.0, 23.0), (15.0, 18.0), (12.5, 15.5), (20.0, 10.5), (17.5, 8.5), (24.0, 5.5)], 1.8)
    ghost = mask()
    d = ImageDraw.Draw(ghost)
    d.ellipse([1.0 * SS, 23.5 * SS, 15.0 * SS, 29.5 * SS], fill=255)
    d.ellipse([3.0 * SS, 25.0 * SS, 13.0 * SS, 28.0 * SS], fill=0)
    burst = mask()
    star(burst, 25.0, 6.5, 6.5, 0.18, 45)
    star(burst, 25.0, 6.5, 4.0, 0.25, 0)
    subject = stack(layer(ghost, mix(A, WHITE, 0.2), mix(A, (0, 0, 0), 0.2), mix(A, WHITE, 0.5), None),
                    layer(zig, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.4), axis=(1, -1)), layer(burst, WHITE, B))
    return finish(subject, [glow(zig, A, 1.6, 1.0), glow(burst, B, 1.6, 1.0), glow(ghost, A, 1.4, 0.8)],
                  backdrop(mix(A, (10, 4, 26), 0.85), mix(A, (30, 10, 60), 0.5)))


def spellblade_runedance(A, B):
    # 가운데 빛 구슬 둘레를 도는 칼 셋 (도는 쪽으로 누움) + 흐린 궤도
    core = mask()
    disc(core, 16, 16, 2.6)
    orbit = mask()
    ring(orbit, 16, 16, 12.5, 0.8)
    layers = []
    for i in range(3):
        a = math.radians(-90 + i * 120)
        cx, cy = 16 + math.cos(a) * 9.0, 16 + math.sin(a) * 9.0
        ta = a + math.radians(90)  # 도는 쪽으로 누운 칼
        tip = (cx + math.cos(ta) * 7.0, cy + math.sin(ta) * 7.0)
        end = (cx - math.cos(ta) * 6.0, cy - math.sin(ta) * 6.0)
        layers += sword_layers(tip, end, blade=3.0, guard=5.6, blade_cols=(WHITE, mix(A, WHITE, 0.6), WHITE, mix(A, (0, 0, 0), 0.3)),
                               guard_cols=(B, mix(B, (0, 0, 0), 0.35), WHITE, None))
    orb = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    paint(orb, solid(orbit), mix(A, (0, 0, 0), 0.15), mix(A, (0, 0, 0), 0.3))
    orb.putalpha(orb.split()[3].point(lambda v: int(v * 0.75)))
    subject = stack(*layers, layer(core, WHITE, B, WHITE, None))
    back = backdrop(mix(A, (10, 4, 26), 0.85), mix(B, (20, 30, 60), 0.5))
    back.alpha_composite(orb)
    return finish(subject, [glow(core, B, 2.4, 1.0)], back)


def spellblade_awaken(A, B):
    flame = mask()
    pts = []
    for i in range(25):
        t = i / 24
        a = math.pi * 2 * t
        r = 11.0 + 2.6 * math.sin(a * 5)
        pts.append((16 + math.sin(a) * r * 0.62, 17 - math.cos(a) * r * 1.05))
    poly(flame, pts)
    inner = mask()
    pts = []
    for i in range(25):
        t = i / 24
        a = math.pi * 2 * t
        r = 7.5 + 1.6 * math.sin(a * 4 + 1)
        pts.append((16 + math.sin(a) * r * 0.55, 18 - math.cos(a) * r * 1.0))
    poly(inner, pts)
    ring_m = ImageChops.subtract(flame, inner)
    sw = sword_layers((16.0, 2.5), (16.0, 29.5), blade=3.0, guard=8.0, blade_cols=(WHITE, mix(B, WHITE, 0.5), WHITE, mix(B, (0, 0, 0), 0.3)))
    sp = mask()
    star(sp, 25.0, 6.0, 2.6)
    star(sp, 7.0, 9.0, 2.0)
    subject = stack(layer(ring_m, mix(A, WHITE, 0.25), A, mix(A, WHITE, 0.6), mix(A, (0, 0, 0), 0.35)), *sw, layer(sp, WHITE, B))
    return finish(subject, [glow(flame, A, 1.8, 0.9)], backdrop(mix(A, (10, 4, 26), 0.82), mix(A, (40, 12, 80), 0.45)))


def spellblade_ult(A, B):
    big = sword_layers((16.0, 30.0), (16.0, 1.5), blade=7.6, guard=15.0, blade_cols=(WHITE, mix(A, WHITE, 0.55), WHITE, mix(A, (0, 0, 0), 0.35)),
                       guard_cols=(B, mix(B, (0, 0, 0), 0.35), WHITE, mix(B, (0, 0, 0), 0.55)))
    core = mask()
    poly(core, [(16.0, 26.0), (17.0, 22.0), (17.0, 9.5), (15.0, 9.5), (15.0, 22.0)])
    crack = mask()
    for pts in (((16, 30.5), (10, 28.5), (5.5, 30.5)), ((16, 30.5), (22.5, 28.0), (27.0, 30.0)), ((10, 28.5), (8.0, 26.0))):
        line(crack, list(pts), 0.9)
    aura = mask()
    disc(aura, 16, 16, 15)
    subject = stack(*big, layer(core, B, mix(B, WHITE, 0.6)), layer(crack, B, WHITE))
    return finish(subject, [glow(aura, A, 3.0, 0.55), glow(crack, B, 1.2, 0.9)], backdrop(mix(A, (10, 4, 26), 0.8), mix(A, (50, 16, 90), 0.4)))


ICONS = {
    "swordsman_flurry": swordsman_flurry,
    "swordsman_flash": swordsman_flash,
    "swordsman_moon": swordsman_moon,
    "swordsman_mirror": swordsman_mirror,
    "swordsman_ult": swordsman_ult,
    "spellblade_arcslash": spellblade_arcslash,
    "spellblade_blink": spellblade_blink,
    "spellblade_runedance": spellblade_runedance,
    "spellblade_awaken": spellblade_awaken,
    "spellblade_ult": spellblade_ult,
}


def job_colors():
    jobs = json.load(open(JOBS, encoding="utf-8"))["jobs"]
    out = {}
    for jid, j in jobs.items():
        for s in j.get("skills", []):
            if s.get("id"):
                out[s["id"]] = (rgb(j["color"][0]), rgb(j["color"][1]))
    return out


HUD = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "hud")


def save_nine(name, im, border):
    im.save(os.path.join(HUD, name + ".png"))
    with open(os.path.join(HUD, name + ".png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": im.size[0], "height": im.size[1], "border": border}}}, fh, indent=2)
        fh.write("\n")


def slot_sprites():
    """직업 칸 (룬 칸과 다른 퀵슬롯 모양 — 20×20 9조각): job_slot = 네모 모서리 검은 바깥 줄 · 짙은 회청 줄 · 어두운 속 (위가 조금 밝음),
    job_edge = 흰 안쪽 줄 (흐림) + 네 모서리 ㄱ자 (진함) — 게임에서 직업 첫째 빛을 곱함"""
    n = 20
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            d = min(x, y, n - 1 - x, n - 1 - y)
            if d == 0:
                px[x, y] = (4, 5, 9, 235)
            elif d == 1:
                px[x, y] = (46, 54, 72, 235) if (x == 1 or y == 1) else (24, 28, 40, 235)
            else:
                t = (y - 2) / (n - 5)
                c = mix((30, 36, 52), (14, 16, 24), t)
                px[x, y] = c + (228,)
    save_nine("job_slot", im, 3)
    e = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    ep = e.load()
    for y in range(n):
        for x in range(n):
            d = min(x, y, n - 1 - x, n - 1 - y)
            if d != 1:
                continue
            corner = (x <= 4 or x >= n - 5) and (y <= 4 or y >= n - 5)
            ep[x, y] = (255, 255, 255, 255 if corner else 120)
    save_nine("job_edge", e, 5)


def main():
    slot_sprites()
    cols = job_colors()
    os.makedirs(OUT, exist_ok=True)
    made = {}
    for sid, fn in ICONS.items():
        A, B = cols[sid]
        img = fn(A, B)
        img.save(os.path.join(OUT, f"{sid}.png"))
        made[sid] = img
    missing = sorted(set(cols) - set(ICONS))
    print(f"job skill icons: {len(made)}" + (f" (no icon yet: {', '.join(missing)})" if missing else ""))
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        k = 8
        cell = N * k + 16
        sheet = Image.new("RGBA", (cell * 5, cell * ((len(made) + 4) // 5)), (24, 26, 34, 255))
        for i, (sid, img) in enumerate(made.items()):
            slot = Image.new("RGBA", (N + 4, N + 4), (16, 18, 26, 255))
            slot.alpha_composite(img, (2, 2))
            big = slot.resize(((N + 4) * k, (N + 4) * k), Image.NEAREST)
            sheet.alpha_composite(big.crop((0, 0, cell - 8, cell - 8)), ((i % 5) * cell + 4, (i // 5) * cell + 4))
        sheet.save(path)


if __name__ == "__main__":
    main()
