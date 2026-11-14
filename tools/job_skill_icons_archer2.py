#!/usr/bin/env python3
"""2026-11-14 궁수 계열 다시 만들기의 직업 스킬 아이콘 (사용자가 고른 컨셉 — 폭풍 궁수 · 천벌의 사수 · 그림자 사수): 32×32 메이플식 네모 그림 (tools/job_skill_icons.py 가
마지막에 불러 예전 그림 (늑대 · 사슴 · 부엉이 · 매 · 벌) 을 덮어씀). 색 = jobs.json 직업 빛 (A 첫째 · B 둘째)."""
import math
import os
import sys

from PIL import ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (WHITE, backdrop, disc, finish, glow, layer, line, mask, mix, poly, ring, stack, star)  # noqa: E402
from job_skill_icons_adv import big_arrow, sn_arrow, sn_arrow_layers  # noqa: E402

DARK = (0, 0, 0)


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def P(x, y):
    return (x * 8, y * 8)


# ------------------------------------------------------------ 모양

def zig(pts, w):
    """지그재그 번개 줄"""
    m = mask()
    line(m, pts, w)
    return m


def bolt_shape(m, x0, y0, s=1.0):
    """굵은 번개 모양 (위 x0 · y0 에서 아래로 — 크기 s)"""
    pts = [(0, 0), (-6.5, 12.5), (-1.2, 12.5), (-5.5, 26), (7.5, 9.5), (2.2, 9.5), (6.5, 0)]
    poly(m, [(x0 + x * s, y0 + y * s) for x, y in pts])
    return m


def cloud(m, cx, cy, s=1.0):
    """먹구름 (덩이 넷 + 밑판)"""
    for dx, dy, r in ((-7, 1.5, 4.2), (-2, -1.5, 5.6), (4.5, -0.5, 5.0), (8.5, 2.0, 3.6)):
        disc(m, cx + dx * s, cy + dy * s, r * s)
    ImageDraw.Draw(m).rectangle([P(cx - 9 * s, cy + 1.0 * s), P(cx + 10.5 * s, cy + 5.6 * s)], fill=255)
    return m


def arc_pts(cx, cy, r, a0, a1, n=24):
    return [(cx + math.cos(math.radians(a0 + (a1 - a0) * i / n)) * r, cy + math.sin(math.radians(a0 + (a1 - a0) * i / n)) * r) for i in range(n + 1)]


def wing(m, root, s, side, tilt=0.0, spread=88.0):
    """날개 하나 (뿌리 root · 크기 s · side +1 오른쪽 · −1 왼쪽 · tilt 도 — 가운데 깃이 tilt − 16 도 · spread = 깃 다섯이 펼친 각): 깃 다섯이 겹친 부채"""
    rx, ry = root
    for i in range(5):
        a = math.radians(tilt - 16 - spread / 2 + i * spread / 4)
        L = s * (1.0 - 0.1 * i)
        tipx = rx + side * math.cos(a) * L
        tipy = ry + math.sin(a) * L
        w = s * 0.17
        px, py = -math.sin(a) * w, math.cos(a) * w
        poly(m, [(rx, ry), (rx + side * (math.cos(a) * L * 0.5) + side * px, ry + math.sin(a) * L * 0.5 + py), (tipx, tipy),
                 (rx + side * (math.cos(a) * L * 0.5) - side * px, ry + math.sin(a) * L * 0.5 - py)])
    return m


def hooded(m, cx, base, h, bow=True):
    """두건을 쓴 그림자 궁수 (발 대신 연기 — 가운데 cx · 아래 base · 키 h): 뾰족한 두건 · 넓은 어깨 · 아래로 좁아지는 옷자락, 왼쪽에 활"""
    k = h / 16.0
    poly(m, [(cx, base - h), (cx + 2.4 * k, base - 13.0 * k), (cx + 3.1 * k, base - 10.6 * k), (cx + 5.2 * k, base - 9.2 * k), (cx + 5.4 * k, base - 7.0 * k),
             (cx + 3.8 * k, base - 6.0 * k), (cx + 3.0 * k, base - 2.0 * k), (cx + 1.4 * k, base), (cx - 1.4 * k, base), (cx - 3.0 * k, base - 2.0 * k),
             (cx - 3.8 * k, base - 6.0 * k), (cx - 5.4 * k, base - 7.0 * k), (cx - 5.2 * k, base - 9.2 * k), (cx - 3.1 * k, base - 10.6 * k),
             (cx - 2.4 * k, base - 13.0 * k)])
    b = mask()
    if bow:
        line(b, arc_pts(cx - 3.2 * k, base - 8.0 * k, 6.4 * k, 120, 240, 16), 1.1 * max(1, k))
    return b


def face(m, cx, base, h):
    k = h / 16.0
    disc(m, cx, base - 11.0 * k, 1.9 * k)
    return m


def eyes(m, cx, base, h):
    k = h / 16.0
    disc(m, cx - 0.9 * k, base - 11.0 * k, 0.7 * max(1, k))
    disc(m, cx + 0.9 * k, base - 11.0 * k, 0.7 * max(1, k))
    return m


# ------------------------------------------------------------ 폭풍 궁수 (빛: 남빛 번개 → 흰 하늘빛 — 짙은 남색 바탕)

def storm_back(A, B):
    return backdrop(mix(A, (4, 6, 20), 0.86), mix((30, 44, 110), A, 0.4))


def archer_rapid(A, B):
    # 체인 볼트: 오른쪽 위로 나란히 날아가는 번개 화살 셋 · 촉끼리 이어진 번개 줄 · 끝의 섬광
    layers, glows = [], []
    tips = ((27.5, 6.0), (22.5, 14.0), (17.5, 22.0))
    for (x, y) in tips:
        sh, hd, fl = sn_arrow((x, y), -34, 15.5, 4.0, 1.4)
        layers += sn_arrow_layers(A, B, sh, hd, fl)
        glows.append(glow(lighter(sh, hd), A, 1.3, 0.8))
    chain = zig([(17.5, 22.0), (21.5, 19.5), (19.5, 16.5), (22.5, 14.0), (26.5, 11.5), (24.5, 8.5), (27.5, 6.0)], 1.1)
    sp = mask()
    star(sp, 28.0, 5.0, 3.6, 0.22, 10)
    return finish(stack(*layers, layer(chain, WHITE, B), layer(sp, WHITE, B)), glows + [glow(chain, A, 1.6, 1.0), glow(sp, B, 1.4, 1.0)], storm_back(A, B))


def archer_wind(A, B):
    # 썬더 스텝: 굵은 번개 (몸이 번개가 됨) · 지나온 쪽 빠른 줄 셋 · 떠난 자리의 작은 번개
    b = mask()
    bolt_shape(b, 18.0, 3.0, 1.0)
    st = mask()
    for (x, y, L) in ((5.0, 9.0, 7.0), (3.0, 15.0, 9.0), (6.0, 21.0, 6.0)):
        line(st, [(x, y), (x + L, y - 2.0)], 1.0)
    small = zig([(5.0, 25.0), (7.5, 27.5), (5.5, 28.5), (8.0, 31.0)], 0.9)
    return finish(stack(layer(st, mix(A, WHITE, 0.4), A), layer(small, WHITE, A), layer(b, WHITE, mix(A, WHITE, 0.25), WHITE, mix(A, DARK, 0.3), axis=(0.4, 1.0))),
                  [glow(b, A, 1.8, 1.0), glow(st, A, 1.0, 0.6)], storm_back(A, B))


def archer_rain(A, B):
    # 스톰 레인: 위의 먹구름 · 비스듬히 쏟아지는 번개 화살 · 가운데 벼락 · 바닥 과녁
    c = mask()
    cloud(c, 15.5, 8.0, 1.05)
    tgt = mask()
    d = ImageDraw.Draw(tgt)
    d.ellipse([P(3.0, 26.0), P(29.0, 31.4)], fill=255)
    d.ellipse([P(6.0, 27.2), P(26.0, 30.2)], fill=0)
    layers, glows = [], []
    for (x, y, L) in ((7.5, 26.5, 8.0), (13.5, 28.0, 8.5), (24.0, 26.5, 8.0), (27.0, 21.0, 6.0)):
        sh, hd, fl = sn_arrow((x, y), 104, L, 3.0, 1.2, fletch=False)
        layers += sn_arrow_layers(A, B, sh, hd, fl)
        glows.append(glow(lighter(sh, hd), A, 1.0, 0.6))
    bt = zig([(18.0, 13.5), (16.0, 18.0), (19.5, 19.5), (17.0, 25.0)], 1.3)
    return finish(stack(layer(tgt, mix(B, WHITE, 0.2), A, None, None), *layers, layer(bt, WHITE, B),
                        layer(c, mix(A, (70, 76, 110), 0.65), mix(A, (24, 26, 44), 0.75), mix(A, WHITE, 0.4), mix(A, DARK, 0.6))),
                  glows + [glow(bt, B, 1.6, 1.0), glow(c, A, 1.4, 0.6)], storm_back(A, B))


def archer_focus(A, B):
    # 스톰 아이: 커다란 먹구름 한가운데 번개빛 눈 (눈매 · 별 눈동자) · 아래로 내리꽂히는 벼락
    c = mask()
    cloud(c, 16.0, 11.0, 1.35)
    eye = mask()
    poly(eye, [(9.0, 12.5), (12.5, 9.8), (16.0, 9.0), (19.5, 9.8), (23.0, 12.5), (19.5, 15.0), (16.0, 15.8), (12.5, 15.0)])
    pupil = mask()
    star(pupil, 16.0, 12.4, 3.0, 0.3, 0)
    bt = zig([(18.5, 18.0), (15.0, 23.5), (19.0, 25.0), (14.5, 31.0)], 1.6)
    return finish(stack(layer(c, mix(A, (70, 76, 110), 0.65), mix(A, (22, 24, 40), 0.75), mix(A, WHITE, 0.35), mix(A, DARK, 0.6)), layer(bt, WHITE, B),
                        layer(eye, mix(B, WHITE, 0.3), A, WHITE, None), layer(pupil, WHITE, B)),
                  [glow(eye, B, 1.6, 1.0), glow(bt, B, 1.6, 1.0), glow(c, A, 1.2, 0.5)], storm_back(A, B))


def archer_ult(A, B):
    # 헤븐 보우: 아래의 거대한 활 (위로 휜 활대 · 아래로 당겨진 시위) 에 걸린 번개 화살이 하늘을 겨눔 (지그재그 살대 · 큰 촉) · 위의 섬광 · 빛살
    bow = mask()
    line(bow, arc_pts(16.0, 32.5, 14.5, 205, 335, 28), 2.2)
    stg = mask()
    line(stg, [(2.9, 26.4), (16.0, 30.5), (29.1, 26.4)], 0.8)
    shaft = zig([(16.0, 30.5), (14.4, 24.0), (17.6, 18.5), (14.6, 13.5), (16.0, 9.5)], 1.5)
    head = mask()
    poly(head, [(16.0, 1.5), (20.2, 9.8), (16.0, 8.4), (11.8, 9.8)])
    rays = mask()
    for a in (-60, -30, 30, 60):
        r = math.radians(a - 90)
        line(rays, [(16.0 + math.cos(r) * 7.5, 5.5 + math.sin(r) * 7.5), (16.0 + math.cos(r) * 11.5, 5.5 + math.sin(r) * 11.5)], 0.8)
    sp = mask()
    star(sp, 16.0, 4.5, 4.4, 0.2, 0)
    return finish(stack(layer(rays, mix(B, WHITE, 0.4), A), layer(bow, mix(A, WHITE, 0.45), mix(A, DARK, 0.15), WHITE, mix(A, DARK, 0.4)), layer(stg, WHITE, B),
                        layer(shaft, WHITE, B), layer(head, WHITE, B, WHITE, mix(B, DARK, 0.3)), layer(sp, WHITE, B)),
                  [glow(lighter(shaft, head), B, 1.8, 1.0), glow(bow, A, 1.4, 0.8), glow(sp, B, 1.6, 1.0)], storm_back(A, B))


# ------------------------------------------------------------ 천벌의 사수 (빛: 백금빛 → 하늘빛 — 짙은 금갈색 바탕)

def holy_back(A, B):
    return backdrop(mix(A, (22, 16, 6), 0.86), mix((120, 96, 40), A, 0.35))


def gate(m, cx, cy, r):
    """하늘의 문 (테 둘 · 네 갈래 빛)"""
    ring(m, cx, cy, r, 1.2)
    ring(m, cx, cy, r * 0.55, 0.9)
    for a in range(0, 360, 45):
        rr = math.radians(a)
        line(m, [(cx + math.cos(rr) * r * 0.58, cy + math.sin(rr) * r * 0.58), (cx + math.cos(rr) * r * 0.95, cy + math.sin(rr) * r * 0.95)], 0.7)
    return m


def sniper_heart(A, B):
    # 저지먼트: 하늘에서 내리꽂히는 빛기둥 · 오른쪽 위로 날아가는 큰 빛의 화살 · 맞는 자리의 섬광 고리
    pil = mask()
    poly(pil, [(18.0, 0.0), (24.0, 0.0), (23.0, 31.5), (19.0, 31.5)])
    core = mask()
    poly(core, [(20.2, 0.0), (21.8, 0.0), (21.4, 31.5), (20.6, 31.5)])
    sh, hd, fl = big_arrow((25.0, 9.0), -40, 24.0, 3.6, 1.8)
    rg = mask()
    ring(rg, 21.0, 13.0, 4.6, 0.9)
    sp = mask()
    star(sp, 21.0, 13.0, 3.4, 0.22, 0)
    return finish(stack(layer(pil, mix(B, WHITE, 0.5), mix(A, WHITE, 0.2)), layer(core, WHITE, WHITE), layer(rg, WHITE, B), *sn_arrow_layers(A, B, sh, hd, fl),
                        layer(sp, WHITE, B)),
                  [glow(pil, A, 1.8, 0.8), glow(lighter(sh, hd), A, 1.4, 0.9), glow(sp, B, 1.4, 1.0)], holy_back(A, B))


def sniper_snare(A, B):
    # 윙 스텝: 활짝 편 빛의 날개 한 쌍 · 가운데 빛의 고리 · 고리를 꿰뚫는 화살
    w = mask()
    wing(w, (14.0, 15.0), 13.0, -1, 0)
    wing(w, (18.0, 15.0), 13.0, 1, 0)
    rg = mask()
    ring(rg, 16.0, 20.0, 5.0, 1.2)
    sh, hd, fl = sn_arrow((28.5, 26.0), 20, 22.0, 3.4, 1.3)
    return finish(stack(layer(w, mix(A, WHITE, 0.55), mix(A, DARK, 0.1), WHITE, mix(A, DARK, 0.35), axis=(0, 1)), layer(rg, WHITE, B),
                        *sn_arrow_layers(B, A, sh, hd, fl)),
                  [glow(w, A, 1.6, 0.8), glow(rg, B, 1.4, 1.0)], holy_back(A, B))


def sniper_trick(A, B):
    # 헤븐즈 게이트: 위의 하늘의 문 · 문에서 비스듬히 내리꽂히는 하늘의 화살 둘 · 땅의 섬광
    g = mask()
    gate(g, 16.0, 8.0, 7.0)
    layers, glows = [], []
    for (x, y, a) in ((9.0, 29.0, 112), (24.0, 28.0, 70)):
        sh, hd, fl = sn_arrow((x, y), a, 15.0, 3.6, 1.5)
        layers += sn_arrow_layers(A, B, sh, hd, fl)
        glows.append(glow(lighter(sh, hd), B, 1.4, 0.9))
    sp = mask()
    star(sp, 9.0, 29.5, 2.8, 0.25, 0)
    star(sp, 24.0, 28.5, 2.8, 0.25, 0)
    return finish(stack(layer(g, WHITE, mix(A, WHITE, 0.2), WHITE, None), *layers, layer(sp, WHITE, B)), glows + [glow(g, A, 1.8, 1.0)], holy_back(A, B))


def sniper_mark(A, B):
    # 세라핌: 날개 여섯 (위 · 가운데 · 아래 세 쌍) · 가운데 빛의 고리 · 고리 속 섬광
    w = mask()
    for (y, s, tilt) in ((12.0, 12.5, -36), (17.0, 12.5, 14), (22.0, 10.0, 62)):
        wing(w, (14.4, y), s, -1, tilt, 34)
        wing(w, (17.6, y), s, 1, tilt, 34)
    rg = mask()
    ring(rg, 16.0, 6.0, 4.0, 1.1)
    sp = mask()
    star(sp, 16.0, 16.0, 4.0, 0.22, 0)
    return finish(stack(layer(w, mix(A, WHITE, 0.55), mix(A, DARK, 0.15), WHITE, mix(A, DARK, 0.35), axis=(0, 1)), layer(rg, WHITE, B), layer(sp, WHITE, B)),
                  [glow(w, A, 1.6, 0.8), glow(rg, B, 1.6, 1.0), glow(sp, B, 1.4, 1.0)], holy_back(A, B))


def sniper_ult(A, B):
    # 디바인 펀니시먼트: 위의 하늘의 문 (큰 테) · 문에서 곧장 내리꽂히는 거대한 심판의 화살 · 땅의 섬광
    g = mask()
    gate(g, 16.0, 6.5, 6.5)
    sh, hd, fl = big_arrow((16.0, 29.5), 90, 25.0, 4.2, 2.2)
    sp = mask()
    star(sp, 16.0, 29.5, 4.4, 0.2, 0)
    rays = mask()
    for x in (6.0, 26.0):
        line(rays, [(x, 4.0), (x + (16 - x) * 0.15, 26.0)], 0.7)
    return finish(stack(layer(rays, mix(B, WHITE, 0.3), A), layer(g, WHITE, mix(A, WHITE, 0.2), WHITE, None), *sn_arrow_layers(A, B, sh, hd, fl), layer(sp, WHITE, B)),
                  [glow(lighter(sh, hd), A, 1.8, 1.0), glow(g, A, 1.6, 0.9), glow(sp, B, 1.6, 1.0)], holy_back(A, B))


# ------------------------------------------------------------ 그림자 사수 (빛: 보랏빛 → 옅은 라일락 — 짙은 보라 바탕)

SHADOW = (26, 14, 44)
SHADOW_LO = (10, 4, 20)


def shadow_back(A, B):
    return backdrop(mix(A, (8, 2, 18), 0.86), mix((70, 36, 120), A, 0.35))


def figure_layers(A, B, cx, base, h, ghost=False):
    body = mask()
    bow = hooded(body, cx, base, h)
    fc = mask()
    face(fc, cx, base, h)
    ey = mask()
    eyes(ey, cx, base, h)
    top = mix(A, SHADOW, 0.25 if ghost else 0.45)
    low = mix(A, SHADOW_LO, 0.55 if ghost else 0.8)
    return [layer(body, top, low, mix(A, WHITE, 0.45), mix(A, SHADOW_LO, 0.5)), layer(bow, mix(A, WHITE, 0.5), A), layer(fc, SHADOW_LO, SHADOW_LO), layer(ey, WHITE, B)], body


def battlearcher_swarm(A, B):
    # 섀도 볼리: 그림자 분신 둘 (왼쪽 · 오른쪽) · 위로 쏘아 올려 휘어 내리꽂히는 그림자 화살 넷
    layers, glows = [], []
    for (cx, base, h) in ((8.5, 31.0, 14.0), (23.5, 31.0, 14.0)):
        ls, body = figure_layers(A, B, cx, base, h)
        layers += ls
        glows.append(glow(body, A, 1.2, 0.6))
    arcs = mask()
    for (x0, x1, top) in ((8.0, 22.0, 2.5), (10.0, 28.0, 4.5), (24.0, 6.0, 3.0), (22.0, 14.0, 6.5)):
        pts = []
        for i in range(13):
            t = i / 12
            pts.append((x0 + (x1 - x0) * t, 14.0 - (14.0 - top) * math.sin(math.pi * t) * 1.0 + 6.0 * t * t))
        line(arcs, pts, 1.0)
    return finish(stack(layer(arcs, mix(B, WHITE, 0.3), A), *layers), glows + [glow(arcs, A, 1.4, 0.9)], shadow_back(A, B))


def battlearcher_dive(A, B):
    # 섀도 스위치: 짙은 분신 (왼쪽 아래) 과 옅은 분신 (오른쪽 위) · 둘 사이를 도는 자리 바꿈 화살표 둘
    l1, b1 = figure_layers(A, B, 9.0, 31.0, 15.0)
    l2, b2 = figure_layers(A, B, 23.0, 21.0, 13.0, ghost=True)
    sw = mask()
    line(sw, arc_pts(16.0, 18.0, 9.0, 200, 300, 16), 1.2)
    line(sw, arc_pts(16.0, 18.0, 9.0, 20, 120, 16), 1.2)
    hd = mask()
    poly(hd, [(21.0, 8.6), (24.0, 11.4), (19.6, 12.0)])
    poly(hd, [(11.0, 27.4), (8.0, 24.6), (12.4, 24.0)])
    return finish(stack(*l2, *l1, layer(sw, WHITE, B), layer(hd, WHITE, B)), [glow(lighter(sw, hd), A, 1.6, 1.0), glow(b2, A, 1.4, 0.7)], shadow_back(A, B))


def battlearcher_splinter(A, B):
    # 섀도 트랩: 왼쪽의 분신이 터지며 오른쪽으로 부채꼴 그림자 화살 조각 (일곱) · 가운데 섬광
    ls, body = figure_layers(A, B, 8.0, 30.0, 15.0)
    sh = mask()
    for i in range(7):
        a = math.radians(-36 + i * 12)
        line(sh, [(12.5 + math.cos(a) * 3.0, 18.0 + math.sin(a) * 3.0), (12.5 + math.cos(a) * 17.0, 18.0 + math.sin(a) * 17.0)], 0.9 if i % 2 else 1.3)
    tips = mask()
    for i in range(7):
        a = math.radians(-36 + i * 12)
        disc(tips, 12.5 + math.cos(a) * 17.5, 18.0 + math.sin(a) * 17.5, 1.0)
    sp = mask()
    star(sp, 12.5, 18.0, 4.2, 0.25, 15)
    return finish(stack(*ls, layer(sh, mix(B, WHITE, 0.3), A), layer(tips, WHITE, B), layer(sp, WHITE, B)),
                  [glow(sh, A, 1.4, 0.9), glow(sp, B, 1.6, 1.0), glow(body, A, 1.0, 0.5)], shadow_back(A, B))


def battlearcher_stride(A, B):
    # 섀도 댄스: 가운데 사수 · 양옆 뒤의 옅은 분신 둘 · 발밑 그림자 연기 줄
    l1, b1 = figure_layers(A, B, 6.5, 28.0, 12.0, ghost=True)
    l2, b2 = figure_layers(A, B, 25.5, 28.0, 12.0, ghost=True)
    l3, b3 = figure_layers(A, B, 16.0, 31.0, 17.0)
    smoke = mask()
    for (x, y, L) in ((2.0, 30.5, 8.0), (22.0, 30.5, 8.0), (9.0, 26.5, 5.0)):
        line(smoke, [(x, y), (x + L, y - 0.5)], 0.9)
    return finish(stack(layer(smoke, mix(A, WHITE, 0.2), A), *l1, *l2, *l3), [glow(lighter(b1, b2), A, 1.6, 0.8), glow(b3, A, 1.0, 0.5)], shadow_back(A, B))


def battlearcher_ult(A, B):
    # 섀도 레기온: 아래에 늘어선 분신 다섯 · 위에서 한 점에 모여 내리꽂히는 거대한 그림자 화살 · 모이는 빛줄
    layers, glows = [], []
    for (cx, h) in ((4.0, 10.0), (10.0, 11.5), (16.0, 12.5), (22.0, 11.5), (28.0, 10.0)):
        ls, body = figure_layers(A, B, cx, 31.5, h, ghost=cx != 16.0)
        layers += ls
        glows.append(glow(body, A, 1.0, 0.4))
    con = mask()
    for x in (4.0, 10.0, 22.0, 28.0):
        line(con, [(x, 20.0), (16.0, 3.0)], 0.7)
    sh, hd, fl = big_arrow((16.0, 18.5), 90, 16.0, 3.6, 1.9)
    sp = mask()
    star(sp, 16.0, 3.0, 3.4, 0.22, 0)
    return finish(stack(layer(con, mix(B, WHITE, 0.3), A), *layers, *sn_arrow_layers(A, B, sh, hd, fl), layer(sp, WHITE, B)),
                  glows + [glow(lighter(sh, hd), A, 1.8, 1.0), glow(con, A, 1.2, 0.7)], shadow_back(A, B))


ICONS = {
    "archer_rapid": archer_rapid,
    "archer_wind": archer_wind,
    "archer_rain": archer_rain,
    "archer_focus": archer_focus,
    "archer_ult": archer_ult,
    "sniper_heart": sniper_heart,
    "sniper_snare": sniper_snare,
    "sniper_trick": sniper_trick,
    "sniper_mark": sniper_mark,
    "sniper_ult": sniper_ult,
    "battlearcher_swarm": battlearcher_swarm,
    "battlearcher_dive": battlearcher_dive,
    "battlearcher_splinter": battlearcher_splinter,
    "battlearcher_stride": battlearcher_stride,
    "battlearcher_ult": battlearcher_ult,
}
