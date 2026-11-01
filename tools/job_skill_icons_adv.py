#!/usr/bin/env python3
"""0.51.0 2차 직업 스킬 아이콘 — 검사 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트) · 전사 각성 뵈드바르(워로드) · 반전 울프헤딘(라이칸) ·
마검사 각성 뵐룬드(룬 나이트) · 반전 투르스(헥스 블레이드) (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음 — 룬 글자는 게임 그림 rfx_jglyph · jrunes24 · jthorn 과 같은 꼴).
색: 소드마스터 = 금빛 (첫째) · 은빛 (둘째), 블러드 나이트 = 짙은 피 (첫째) · 밝은 피 (둘째), 워로드 = 주황 (첫째) · 갈색 (둘째),
라이칸 = 달빛 은회 (첫째) · 붉은 눈빛 (둘째), 룬 나이트 = 보라 (첫째) · 금빛 룬 (둘째), 헥스 블레이드 = 호박빛 (첫째) · 먹빛 자주 (둘째) — jobs.json advance."""
import math
import os
import sys

from PIL import ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (GOLD, GOLD_HI, GOLD_LO, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix,  # noqa: E402
                             poly, ring, stack, star, sword_layers)
from job_skill_icons_more import axe_layers  # noqa: E402
from job_skill_icons import SS  # noqa: E402
from job_fx_art_v2 import GLYPHS  # noqa: E402 — 룬 글자 꼴 (게임의 룬 그림과 같음)

NIGHT = (8, 10, 30)
DUSK = (28, 8, 10)


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def drop(m, cx, cy, r, ang=-90.0):
    """핏방울: 둥근 머리 (cx, cy) + ang 쪽 (도 — 화면 좌표, -90 = 위) 으로 가늘어지는 꼬리"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    poly(m, [(cx + px * r * 0.92, cy + py * r * 0.92), (cx + ux * r * 2.7, cy + uy * r * 2.7), (cx - px * r * 0.92, cy - py * r * 0.92)])
    disc(m, cx, cy, r)
    return m


def gold_sword(tip, end, blade=2.6, guard=7.0):
    """금빛 코등이 · 은빛 날 (소드마스터)"""
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(STEEL_HI, STEEL, WHITE, STEEL_LO), guard_cols=(GOLD_HI, GOLD, GOLD_HI, GOLD_LO))


def blood_sword(tip, end, A, B, blade=2.6, guard=7.0):
    """검붉은 날 · 검은 코등이 (블러드 나이트 — 다인슬레이프)"""
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(mix(B, WHITE, 0.35), mix(A, B, 0.45), mix(B, WHITE, 0.6), mix(A, (0, 0, 0), 0.4)),
                        guard_cols=((96, 86, 92), (46, 40, 46), (140, 130, 136), (20, 16, 20)), grip_cols=(mix(A, (0, 0, 0), 0.5), mix(A, (0, 0, 0), 0.75), None, None))


# ------------------------------------------------------------ 시구르드(소드마스터) (빛: 금빛 → 은빛)

def swordmaster_flurry(A, B):
    # 금빛 연격: 크게 엇갈린 X자 초승달 둘 (금빛 · 은빛) + 작은 연격 자국 둘 · 가운데 금빛 반짝임
    x1, x2 = mask(), mask()
    crescent(x1, 2.0, 30.0, 26.0, 3.8, -84, -6)
    crescent(x2, 30.0, 30.0, 26.0, 3.8, 186, 264)
    small = mask()
    crescent(small, 16.0, 16.0, 13.0, 1.6, 200, 250)
    crescent(small, 16.0, 16.0, 13.0, 1.6, 20, 70)
    sp = mask()
    star(sp, 16.0, 14.4, 5.2, 0.18)
    star(sp, 27.0, 5.4, 2.2)
    star(sp, 5.0, 6.0, 1.8)
    subject = stack(layer(small, mix(B, WHITE, 0.4), B, None, None), layer(x1, GOLD_HI, A, WHITE, GOLD_LO, axis=(0.7, -0.7)),
                    layer(x2, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.4), axis=(-0.7, -0.7)), layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(x1, x2), A, 1.7, 1.0), glow(sp, GOLD_HI, 1.2, 0.9)], backdrop(mix(A, NIGHT, 0.86), mix(A, (60, 40, 10), 0.45)))


def swordmaster_grani(A, B):
    # 그라니의 질주: 아래왼쪽에서 Z자로 꺾여 위오른쪽으로 내달린 금빛 길 (두 마디) · 끝에 누운 칼 · 말발굽 자국 둘
    path = mask()
    line(path, [(2.5, 27.5), (14.5, 19.0)], 2.4)
    line(path, [(14.5, 19.0), (10.5, 12.5)], 2.0)
    line(path, [(10.5, 12.5), (23.0, 6.5)], 2.4)
    core = mask()
    line(core, [(3.5, 27.0), (14.5, 19.0), (10.5, 12.5), (22.0, 7.0)], 0.7)
    shoe = mask()
    for cx, cy in ((22.5, 25.0), (27.0, 19.5)):
        crescent(shoe, cx, cy, 2.6, 1.1, 200, 340, taper=False)
    sw = gold_sword((30.5, 3.0), (18.0, 9.4), blade=2.6, guard=6.2)
    sp = mask()
    star(sp, 30.0, 3.4, 3.4, 0.2)
    subject = stack(layer(path, GOLD_HI, A, WHITE, GOLD_LO, axis=(1, -1)), layer(core, WHITE, WHITE), layer(shoe, B, mix(B, (0, 0, 0), 0.3)), *sw,
                    layer(sp, WHITE, WHITE))
    return finish(subject, [glow(lighter(path, sp), A, 1.6, 1.0)], backdrop(mix(A, NIGHT, 0.86), mix(B, (20, 30, 60), 0.4)))


def swordmaster_moon(A, B):
    # 만월 강하: 뒤의 큰 금빛 보름달 (얇은 고리 · 옅은 속) · 그 앞을 지나 아래로 내리꽂히는 칼 · 땅에서 퍼지는 충격 초승달
    moon = mask()
    ring(moon, 16.0, 12.0, 10.4, 1.8)
    full = mask()
    disc(full, 16.0, 12.0, 8.8)
    sw = gold_sword((16.0, 29.0), (16.0, 2.2), blade=3.2, guard=8.6)
    arcs = mask()
    crescent(arcs, 16.0, 31.5, 11.5, 2.2, 196, 252)
    crescent(arcs, 16.0, 31.5, 11.5, 2.2, 288, 344)
    sp = mask()
    star(sp, 16.0, 28.4, 4.0, 0.18)
    star(sp, 25.4, 4.6, 1.8)
    subject = stack(layer(full, mix(A, NIGHT, 0.35), mix(A, NIGHT, 0.6)), layer(moon, GOLD_HI, A, WHITE, GOLD_LO), layer(arcs, WHITE, B, WHITE, None), *sw,
                    layer(sp, WHITE, WHITE))
    return finish(subject, [glow(moon, A, 2.0, 0.9), glow(arcs, B, 1.2, 0.8)], backdrop(mix(A, NIGHT, 0.86), mix(B, (20, 30, 70), 0.45)))


def swordmaster_heart(A, B):
    # 용의 심장: 땅에 곧게 꽂힌 칼 · 코등이 뒤에서 타오르는 금빛 심장 (용의 비늘 무늬) · 사방으로 솟는 빛
    heart = mask()
    disc(heart, 12.6, 11.0, 4.6)
    disc(heart, 19.4, 11.0, 4.6)
    poly(heart, [(8.2, 12.6), (23.8, 12.6), (16.0, 22.6)])
    rays = mask()
    for a in range(0, 360, 45):
        r0, r1 = 11.0, 14.6
        x0, y0 = 16 + math.cos(math.radians(a)) * r0, 13 + math.sin(math.radians(a)) * r0
        x1, y1 = 16 + math.cos(math.radians(a)) * r1, 13 + math.sin(math.radians(a)) * r1
        line(rays, [(x0, y0), (x1, y1)], 1.2)
    scale = mask()
    for cx, cy in ((13.0, 12.0), (19.0, 12.0), (16.0, 16.6)):
        crescent(scale, cx, cy, 2.4, 0.9, 20, 160, taper=False)
    sw = gold_sword((16.0, 30.5), (16.0, 3.0), blade=3.0, guard=9.0)
    ground = mask()
    poly(ground, [(5, 29.2), (27, 29.2), (27, 30.6), (5, 30.6)])
    subject = stack(layer(rays, GOLD_HI, A, None, None), layer(heart, GOLD_HI, mix(A, (160, 40, 10), 0.35), WHITE, GOLD_LO),
                    layer(scale, mix(A, (120, 50, 10), 0.4), mix(A, (90, 40, 10), 0.6)), layer(ground, B, mix(B, (0, 0, 0), 0.35)), *sw)
    return finish(subject, [glow(lighter(heart, rays), A, 2.0, 1.0)], backdrop(mix(A, NIGHT, 0.84), mix(A, (80, 40, 10), 0.4)))


def swordmaster_ult(A, B):
    # 신들의 황혼: 땅에 내리꽂힌 큰 칼 · 칼끝에서 앞 (아래) 세 갈래로 갈라지는 빛나는 금 · 양옆 충격 초승달 · 하늘의 금빛 띠
    crack = mask()
    tip = (16.0, 20.5)
    for end, mid in (((16.0, 31.5), (15.0, 26.0)), ((4.0, 31.5), (10.0, 25.0)), ((28.0, 31.5), (22.0, 25.4))):
        line(crack, [tip, mid, end], 1.7)
    core = mask()
    for end, mid in (((16.0, 31.5), (15.0, 26.0)), ((4.0, 31.5), (10.0, 25.0)), ((28.0, 31.5), (22.0, 25.4))):
        line(core, [tip, mid, end], 0.55)
    arcs = mask()
    crescent(arcs, 16.0, 22.0, 13.4, 2.4, 192, 240)
    crescent(arcs, 16.0, 22.0, 13.4, 2.4, 300, 348)
    sky = mask()
    crescent(sky, 16.0, 30.0, 27.0, 2.0, 222, 318, taper=True)
    sw = gold_sword((16.0, 21.0), (16.0, 1.6), blade=4.0, guard=10.4)
    sp = mask()
    star(sp, 16.0, 20.4, 4.6, 0.18)
    dark = mix(A, (0, 0, 0), 0.65)
    subject = stack(layer(sky, mix(A, WHITE, 0.3), A, None, None), layer(arcs, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.35)),
                    layer(crack, mix(A, WHITE, 0.3), dark, GOLD_HI, mix(dark, (0, 0, 0), 0.3)), layer(core, WHITE, GOLD_HI), *sw, layer(sp, WHITE, WHITE))
    return finish(subject, [glow(lighter(crack, sp), A, 1.8, 1.0), glow(lighter(arcs, sky), B, 1.2, 0.8)],
                  backdrop(mix(A, NIGHT, 0.84), mix(A, (70, 50, 20), 0.4)))


# ------------------------------------------------------------ 다인슬레이프(블러드 나이트) (빛: 짙은 피 → 밝은 피)

def bloodknight_flurry(A, B):
    # 핏빛 연격: 위왼쪽을 가운데로 핏빛 호 셋 (찌르듯 · 되돌려 · 내려) · 끝에서 튀는 핏방울
    ms = []
    for r, w in ((11.0, 2.6), (18.0, 3.2), (25.0, 3.6)):
        m = mask()
        crescent(m, 2.5, 2.5, r, w, 4, 86)
        ms.append(m)
    drops = mask()
    drop(drops, 27.0, 9.0, 1.6, -150)
    drop(drops, 9.5, 27.5, 1.4, -120)
    drop(drops, 22.5, 23.0, 1.9, -135)
    dark = mix(A, (0, 0, 0), 0.45)
    slashes = stack(*[layer(m, mix(B, WHITE, 0.45), B, mix(B, WHITE, 0.7), dark, axis=(0.7, 0.7)) for m in ms],
                    layer(drops, mix(B, WHITE, 0.25), A, mix(B, WHITE, 0.6), dark))
    return finish(slashes, [glow(lighter(*ms), B, 1.6, 0.9)], backdrop(mix(A, DUSK, 0.82), mix(A, (60, 10, 14), 0.35)))


def bloodknight_rush(A, B):
    # 피의 질주: 오른쪽으로 내달리는 누운 칼 · 뒤로 끌리는 핏빛 줄 셋 · 떨어지는 핏방울
    lines = mask()
    for y, x0, x1, w in ((11.0, 2.0, 12.0, 1.3), (16.0, 1.0, 9.0, 1.5), (21.0, 2.5, 13.0, 1.3)):
        line(lines, [(x0, y), (x1, y)], w)
    drops = mask()
    for cx, cy, r in ((6.0, 25.5, 1.4), (11.5, 28.0, 1.2), (16.5, 25.0, 1.5)):
        drop(drops, cx, cy, r, -90)
    sw = blood_sword((30.0, 16.0), (13.5, 16.0), A, B, blade=3.0, guard=7.0)
    sp = mask()
    star(sp, 29.0, 16.0, 3.8, 0.2)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(lines, mix(B, WHITE, 0.3), A, None, None, axis=(1, 0)), layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.6), dark), *sw,
                    layer(sp, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(lighter(lines, sp), B, 1.6, 0.95)], backdrop(mix(A, DUSK, 0.84), mix(A, (60, 10, 14), 0.35)))


def bloodknight_burst(A, B):
    # 상처 터뜨리기: 가운데 터지는 피 (울퉁불퉁한 덩이 · 가시 여덟) · 바깥으로 튀는 핏방울 · 핏빛 고리
    splat = mask()
    pts = []
    for i in range(16):
        a = math.radians(i * 22.5 + 8)
        r = 6.4 if i % 2 == 0 else 4.2
        pts.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    poly(splat, pts)
    for i in range(8):
        a = math.radians(i * 45 + 20)
        line(splat, [(16 + math.cos(a) * 5.0, 16 + math.sin(a) * 5.0), (16 + math.cos(a) * 10.0, 16 + math.sin(a) * 10.0)], 1.3)
    drops = mask()
    for i in range(8):
        a = i * 45 + 42
        rr = 12.6
        drop(drops, 16 + math.cos(math.radians(a)) * rr, 16 + math.sin(math.radians(a)) * rr, 1.3, a + 180)
    rim = mask()
    ring(rim, 16.0, 16.0, 15.0, 1.1)
    core = mask()
    disc(core, 15.0, 15.0, 2.4)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(rim, B, A, None, None), layer(splat, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark),
                    layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark), layer(core, mix(B, WHITE, 0.6), mix(B, WHITE, 0.35)))
    return finish(subject, [glow(lighter(splat, rim), B, 1.8, 1.0)], backdrop(mix(A, DUSK, 0.82), mix(A, (70, 12, 16), 0.4)))


def bloodknight_oath(A, B):
    # 회그니의 맹세: 얼굴 앞에 곧게 세운 검붉은 칼 · 날을 따라 흘러내리는 피 · 아래로 떨어지는 핏방울 · 둘레의 검붉은 기운
    sw = blood_sword((16.0, 2.0), (16.0, 24.0), A, B, blade=3.0, guard=8.4)
    flow = mask()
    line(flow, [(16.6, 5.0), (16.8, 9.0), (16.2, 12.0), (16.6, 15.0)], 0.9)
    drops = mask()
    drop(drops, 12.0, 19.0, 1.3, -90)
    drop(drops, 20.5, 21.5, 1.5, -90)
    drop(drops, 16.0, 29.0, 1.6, -90)
    aura = mask()
    crescent(aura, 16.0, 16.0, 14.0, 2.0, 120, 240)
    crescent(aura, 16.0, 16.0, 14.0, 2.0, -60, 60)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(aura, B, A, None, None), *sw, layer(flow, mix(B, WHITE, 0.2), B), layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark))
    return finish(subject, [glow(lighter(aura, drops), B, 1.6, 0.9)], backdrop(mix(A, DUSK, 0.84), mix(A, (50, 8, 12), 0.35)))


def bloodknight_ult(A, B):
    # 끝나지 않는 전투: 뒤에서 솟는 핏빛 기둥 · 앞의 X자 베기 둘 · 땅에 꽂힌 검붉은 칼 · 튀는 핏방울
    pillar = mask()
    poly(pillar, [(12.0, 30.0), (20.0, 30.0), (18.6, 1.0), (13.4, 1.0)])
    pcore = mask()
    poly(pcore, [(14.6, 30.0), (17.4, 30.0), (16.6, 1.0), (15.4, 1.0)])
    x1, x2 = mask(), mask()
    crescent(x1, 2.0, 30.0, 25.0, 3.0, -84, -8)
    crescent(x2, 30.0, 30.0, 25.0, 3.0, 188, 264)
    sw = blood_sword((16.0, 29.0), (16.0, 12.0), A, B, blade=3.2, guard=8.0)
    drops = mask()
    for cx, cy, r, a in ((6.0, 10.0, 1.3, 140), (26.0, 9.0, 1.3, 40), (8.5, 22.0, 1.1, 160), (24.0, 21.0, 1.1, 20)):
        drop(drops, cx, cy, r, a + 180)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(pillar, mix(A, B, 0.5), A, None, None), layer(pcore, mix(B, WHITE, 0.55), mix(B, WHITE, 0.3)),
                    layer(x1, mix(B, WHITE, 0.4), B, mix(B, WHITE, 0.7), dark, axis=(0.7, -0.7)), layer(x2, mix(B, WHITE, 0.4), B, mix(B, WHITE, 0.7), dark, axis=(-0.7, -0.7)),
                    *sw, layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark))
    return finish(subject, [glow(lighter(pillar, x1, x2), B, 1.8, 1.0)], backdrop(mix(A, DUSK, 0.82), mix(A, (80, 12, 16), 0.4)))


# ------------------------------------------------------------ 뵈드바르(워로드) (빛: 주황 → 갈색 곰 기운)

EMBER = (34, 14, 6)


def warlord_back(A, B):
    return backdrop(mix(B, EMBER, 0.82), mix(A, (70, 30, 8), 0.45))


def bear_face(cx, cy, s=1.0, mouth=1.0):
    """곰 얼굴 앞모습 (머리 · 귀 · 주둥이) 과 입 · 이빨 · 눈 (마스크 넷)"""
    head = mask()
    disc(head, cx, cy, 8.2 * s)
    poly(head, [(cx - 9.0 * s, cy + 1.0 * s), (cx + 9.0 * s, cy + 1.0 * s), (cx + 6.5 * s, cy + 7.6 * s), (cx - 6.5 * s, cy + 7.6 * s)])
    for side in (-1, 1):
        disc(head, cx + side * 6.6 * s, cy - 6.4 * s, 2.8 * s)
    mouth_m = mask()
    if mouth > 0:
        poly(mouth_m, [(cx - 3.6 * s, cy + 2.6 * s), (cx + 3.6 * s, cy + 2.6 * s), (cx + 2.4 * s, cy + (4.6 + 3.0 * mouth) * s),
                       (cx - 2.4 * s, cy + (4.6 + 3.0 * mouth) * s)])
    teeth = mask()
    for side in (-1, 1):
        poly(teeth, [(cx + side * 2.9 * s, cy + 2.6 * s), (cx + side * 1.6 * s, cy + 2.6 * s), (cx + side * 2.3 * s, cy + 4.9 * s)])
    eyes = mask()
    for side in (-1, 1):
        poly(eyes, [(cx + side * 5.2 * s, cy - 2.6 * s), (cx + side * 1.6 * s, cy - 1.0 * s), (cx + side * 4.4 * s, cy - 0.6 * s)])
    return head, mouth_m, teeth, eyes


def warlord_crush(A, B):
    # 투구 깨부수기: 뿔 달린 투구 (둥근 머리 · 코 가리개 · 양옆 뿔) 를 위에서 내려찍은 도끼 날 · 투구를 가르는 흰 금 · 땅의 갈라짐 · 터짐
    helm = mask()
    crescent(helm, 15.0, 24.0, 6.4, 6.4, 180, 360, taper=False)
    poly(helm, [(8.0, 23.4), (22.0, 23.4), (22.0, 25.2), (8.0, 25.2)])
    poly(helm, [(14.0, 24.0), (16.0, 24.0), (15.6, 28.0), (14.4, 28.0)])
    horn = mask()
    for side in (-1, 1):
        x0 = 15.0 + side * 6.6
        line(horn, [(x0, 21.0), (x0 + side * 3.2, 18.4), (x0 + side * 4.0, 14.2)], 1.6)
    split = mask()
    line(split, [(15.2, 16.6), (14.4, 19.4), (15.8, 21.6), (14.8, 24.6)], 1.1)
    crack = mask()
    for pts in (((15, 29.0), (21, 29.8), (27, 28.8), (31, 30.2)), ((15, 29.0), (9, 29.8), (3, 28.8))):
        line(crack, list(pts), 0.9)
    ground = mask()
    poly(ground, [(1, 28.4), (31, 28.4), (31, 29.4), (1, 29.4)])
    burst = mask()
    star(burst, 15.2, 16.2, 4.6, 0.18, 0)
    axe = axe_layers((15.4, 15.0), (29.2, 1.6), blade=8.2, tint=A, side=-1)
    dark = mix(B, (0, 0, 0), 0.55)
    steel = (150, 150, 166)
    subject = stack(layer(ground, mix(B, (40, 20, 10), 0.4), mix(B, (10, 4, 2), 0.6)), layer(crack, mix(A, WHITE, 0.5), A),
                    layer(horn, mix(B, WHITE, 0.55), mix(B, WHITE, 0.2), WHITE, dark), layer(helm, mix(steel, WHITE, 0.35), steel, WHITE, (60, 60, 74), axis=(0.6, 0.8)),
                    layer(split, WHITE, A), *axe, layer(burst, WHITE, A))
    return finish(subject, [glow(lighter(split, burst), A, 1.6, 1.0)], warlord_back(A, B))


def warlord_throw(A, B):
    # 곰의 메치기: 왼쪽 아래에서 머리 위를 넘어 오른쪽 땅으로 내리꽂히는 굵은 호 · 거꾸로 메쳐진 적 (머리가 땅) · 땅 울림 고리 · 터짐
    arc = mask()
    crescent(arc, 15.0, 21.0, 11.0, 3.4, 176, 338)
    tip = mask()
    poly(tip, [(23.6, 13.6), (28.6, 15.2), (24.0, 19.6)])
    enemy = mask()
    disc(enemy, 24.0, 24.4, 2.4)
    poly(enemy, [(22.2, 22.4), (25.8, 22.4), (25.8, 16.2), (22.2, 16.2)])
    line(enemy, [(22.8, 16.6), (20.2, 11.4)], 1.5)
    line(enemy, [(25.2, 16.6), (27.8, 11.4)], 1.5)
    line(enemy, [(22.2, 20.6), (19.6, 23.4)], 1.2)
    line(enemy, [(25.8, 20.6), (28.4, 23.4)], 1.2)
    quake = mask()
    crescent(quake, 24.0, 28.0, 6.4, 1.5, 192, 348)
    crescent(quake, 24.0, 28.0, 9.6, 1.1, 200, 340)
    burst = mask()
    star(burst, 24.0, 26.6, 5.4, 0.18, 0)
    ground = mask()
    poly(ground, [(1, 28.0), (31, 28.0), (31, 29.0), (1, 29.0)])
    dark = mix(B, (0, 0, 0), 0.55)
    subject = stack(layer(arc, mix(A, WHITE, 0.45), A, WHITE, None, axis=(1, 0)), layer(tip, mix(A, WHITE, 0.45), A),
                    layer(ground, mix(B, (40, 20, 10), 0.4), mix(B, (10, 4, 2), 0.6)), layer(burst, WHITE, A), layer(quake, WHITE, A),
                    layer(enemy, mix((150, 150, 160), WHITE, 0.2), (90, 90, 104), (220, 220, 230), dark))
    return finish(subject, [glow(lighter(arc, tip), A, 1.6, 0.9), glow(lighter(quake, burst), A, 1.8, 1.0)], warlord_back(A, B))


def warlord_storm(A, B):
    # 도끼 태풍: 아래로 좁아지는 회오리 (누운 도끼 궤적 고리 넷 — 앞쪽이 밝음) · 아래 내려찍은 자리의 터짐 · 흩날리는 불티
    back_m, front_m = mask(), mask()
    for cy, rx, w in ((6.0, 12.6, 1.9), (11.2, 10.2, 1.9), (16.2, 7.8, 1.8), (20.8, 5.4, 1.6)):
        ry = rx * 0.32
        back = [(16 + math.cos(math.radians(a)) * rx, cy + math.sin(math.radians(a)) * ry) for a in range(180, 361, 12)]
        front = [(16 + math.cos(math.radians(a)) * rx, cy + math.sin(math.radians(a)) * ry) for a in range(0, 181, 12)]
        line(back_m, back, w * 0.6)
        line(front_m, front, w)
    burst = mask()
    star(burst, 16.0, 26.4, 6.4, 0.16, 0)
    ground = mask()
    poly(ground, [(2, 27.4), (30, 27.4), (30, 28.4), (2, 28.4)])
    sp = mask()
    for x, y, r in ((28.0, 4.5, 1.0), (3.5, 9.0, 0.8), (27.5, 17.5, 0.9), (4.5, 19.0, 0.9)):
        disc(sp, x, y, r)
    subject = stack(layer(back_m, mix(A, B, 0.5), B), layer(ground, mix(B, (40, 20, 10), 0.4), mix(B, (10, 4, 2), 0.6)),
                    layer(front_m, WHITE, A, WHITE, mix(B, (0, 0, 0), 0.3), axis=(0, 1)), layer(burst, WHITE, A), layer(sp, WHITE, A))
    return finish(subject, [glow(front_m, A, 1.6, 0.9), glow(burst, A, 1.8, 1.0)], warlord_back(A, B))


def warlord_roar(A, B):
    # 곰의 포효: 포효하는 곰 얼굴 (귀 · 벌린 입 · 송곳니 · 성난 눈) · 양옆으로 퍼지는 소리 호
    head, mouth_m, teeth, eyes = bear_face(16.0, 15.0, 1.15, 1.0)
    waves = mask()
    for r in (12.5, 15.0):
        crescent(waves, 16.0, 16.0, r, 1.3, 150, 210)
        crescent(waves, 16.0, 16.0, r, 1.3, -30, 30)
    muzzle = mask()
    disc(muzzle, 16.0, 19.0, 4.0)
    dark = mix(B, (0, 0, 0), 0.5)
    subject = stack(layer(waves, WHITE, A), layer(head, mix(B, WHITE, 0.2), B, mix(A, WHITE, 0.25), dark, axis=(0.5, 0.8)),
                    layer(muzzle, mix(B, WHITE, 0.4), mix(B, WHITE, 0.15)), layer(mouth_m, mix(dark, (0, 0, 0), 0.3), dark), layer(teeth, WHITE, mix(A, WHITE, 0.5)),
                    layer(eyes, WHITE, A))
    return finish(subject, [glow(lighter(waves, eyes), A, 1.6, 1.0), glow(head, A, 1.4, 0.5)], warlord_back(A, B))


def warlord_ult(A, B):
    # 뵈드바르의 앞발: 위에서 내리치는 거대한 곰 앞발 (털 덮인 발등 · 아래 가장자리의 발가락 넷 · 날카롭게 휜 발톱) · 위의 바람 줄 · 땅의 구덩이 · 발톱 자국 넷
    paw = mask()
    disc(paw, 11.0, 11.0, 6.4)
    disc(paw, 21.0, 11.0, 6.4)
    poly(paw, [(10.0, 4.8), (22.0, 4.8), (25.6, 13.0), (6.4, 13.0)])
    toes = [(-6.6, 16.0), (-2.3, 17.2), (2.3, 17.2), (6.6, 16.0)]
    for dx, dy in toes:
        disc(paw, 16.0 + dx, dy, 2.6)
    fur = mask()
    for x0, y0 in ((10.0, 7.0), (14.0, 6.0), (18.0, 6.0), (22.0, 7.0)):
        line(fur, [(x0, y0), (x0 - 0.6, y0 + 3.0)], 0.7)
    claws = mask()
    for dx, dy in toes:
        sx, by = 16.0 + dx, dy + 1.6
        poly(claws, [(sx - 1.3, by), (sx + 1.3, by), (sx + dx * 0.1, by + 5.0)])
    wind = mask()
    for x0 in (9.0, 16.0, 23.0):
        line(wind, [(x0, 0.0), (x0, 3.4)], 1.0)
    marks = mask()
    for dx in (-6.0, -2.0, 2.0, 6.0):
        line(marks, [(16.0 + dx * 1.2, 27.4), (16.0 + dx * 1.45, 30.8)], 1.1)
    crater = mask()
    crescent(crater, 16.0, 27.4, 12.6, 1.8, 196, 344)
    dark = mix(B, (0, 0, 0), 0.5)
    subject = stack(layer(wind, mix(A, WHITE, 0.4), A), layer(crater, mix(A, WHITE, 0.35), A), layer(marks, WHITE, A),
                    layer(paw, mix(B, WHITE, 0.3), B, mix(A, WHITE, 0.4), dark, axis=(0.4, 0.9)), layer(fur, mix(B, (0, 0, 0), 0.25), mix(B, (0, 0, 0), 0.4)),
                    layer(claws, WHITE, mix(A, WHITE, 0.35)))
    return finish(subject, [glow(lighter(marks, crater), A, 1.8, 1.0), glow(lighter(paw, claws), A, 1.4, 0.6)], warlord_back(A, B))


# ------------------------------------------------------------ 울프헤딘(라이칸) (빛: 달빛 은회 → 붉은 눈빛)

MOONLIT = (10, 12, 26)


def lycan_back(A, B):
    return backdrop(mix(A, MOONLIT, 0.86), mix(B, (30, 6, 10), 0.45))


def claw_lines(m, x0, y0, x1, y1, n=4, gap=2.6, w=1.4):
    """발톱 줄 n 개 (x0, y0 → x1, y1 방향 · 옆으로 gap 칸씩 · 가운데 굵게)"""
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    px, py = -uy, ux
    for i in range(n):
        o = (i - (n - 1) / 2) * gap
        a = (x0 + px * o, y0 + py * o)
        b = (x1 + px * o, y1 + py * o)
        mid = ((a[0] + b[0]) / 2 + px * 0.8, (a[1] + b[1]) / 2 + py * 0.8)
        line(m, [a, mid, b], w)
    return m


def wolf_side(m, cx, cy, s=1.0, up=False):
    """늑대 머리 옆모습 (오른쪽을 봄 — 긴 주둥이 · 선 뾰족 귀 · 목). up 이면 머리를 70° 들어 위를 보며 울부짖음 (목이 아래로 이어짐)"""
    pts = [(-4.0, 6.4), (-6.2, 1.4), (-5.4, -2.6), (-5.0, -5.0), (-3.4, -9.6), (-1.8, -5.0), (0.8, -4.2), (3.0, -3.2), (8.6, -1.4), (9.6, -0.2), (9.2, 0.9),
           (4.6, 1.6), (6.2, 2.4), (1.8, 3.4), (-0.6, 6.4)]
    a = math.radians(-70.0 if up else 0.0)
    ca, sa = math.cos(a), math.sin(a)
    out = [(cx + (x * ca - y * sa) * s, cy + (x * sa + y * ca) * s) for x, y in pts]
    poly(m, out)
    if up:  # 목 · 가슴 (아래로)
        poly(m, [(cx - 5.0 * s, cy + 1.0 * s), (cx + 2.4 * s, cy + 3.0 * s), (cx + 3.2 * s, cy + 12.0 * s), (cx - 6.6 * s, cy + 12.0 * s)])
    return m


def lycan_fang(A, B):
    # 송곳니 베기: 은빛 X자 베기 둘 · 그 위를 가로지르는 붉은 발톱 네 줄
    x1, x2 = mask(), mask()
    crescent(x1, 2.0, 30.0, 25.0, 2.8, -84, -8)
    crescent(x2, 30.0, 30.0, 25.0, 2.8, 188, 264)
    claws = claw_lines(mask(), 25.5, 6.5, 8.0, 25.5, 4, 2.8, 1.5)
    core = claw_lines(mask(), 25.5, 6.5, 8.0, 25.5, 4, 2.8, 0.5)
    dark = mix(A, (0, 0, 0), 0.5)
    subject = stack(layer(x1, WHITE, A, WHITE, dark, axis=(0.7, -0.7)), layer(x2, WHITE, A, WHITE, dark, axis=(-0.7, -0.7)),
                    layer(claws, mix(B, WHITE, 0.3), B, mix(B, WHITE, 0.6), mix(B, (0, 0, 0), 0.4)), layer(core, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(lighter(x1, x2), A, 1.4, 0.8), glow(claws, B, 1.6, 1.0)], lycan_back(A, B))


def lycan_pounce(A, B):
    # 사냥감 덮치기: 왼쪽 아래에서 튀어 오른 호 (덮치는 길) · 끝의 늑대 머리 (오른쪽 아래를 봄) · 넘어진 적 위의 기절 별
    path = mask()
    crescent(path, 14.0, 24.0, 11.0, 2.4, 190, 300)
    wolf = wolf_side(mask(), 18.0, 13.0, 1.1)
    eye = mask()
    disc(eye, 20.6, 10.6, 0.8)
    hit = mask()
    star(hit, 27.0, 23.0, 4.6, 0.18, 0)
    stars = mask()
    for x, y in ((22.0, 27.6), (26.0, 28.8), (30.0, 27.4)):
        star(stars, x, y, 1.6, 0.3)
    dark = mix(A, (0, 0, 0), 0.5)
    subject = stack(layer(path, WHITE, A, None, None, axis=(1, -1)), layer(wolf, mix(A, WHITE, 0.3), A, WHITE, dark, axis=(0.6, 0.8)), layer(eye, WHITE, B),
                    layer(hit, WHITE, mix(B, WHITE, 0.4)), layer(stars, mix(B, WHITE, 0.6), B))
    return finish(subject, [glow(path, A, 1.4, 0.8), glow(lighter(eye, hit), B, 1.6, 1.0)], lycan_back(A, B))


def lycan_howl(A, B):
    # 달밤의 포효: 뒤의 큰 보름달 · 그 앞에서 위를 보며 울부짖는 늑대 · 퍼지는 소리 호
    moon = mask()
    disc(moon, 18.0, 11.0, 8.6)
    rim = mask()
    ring(rim, 18.0, 11.0, 8.6, 1.0)
    wolf = wolf_side(mask(), 11.0, 18.0, 1.05, up=True)
    waves = mask()
    for r in (4.0, 6.8):
        crescent(waves, 15.6, 6.4, r, 1.1, -160, -70)
    eye = mask()
    disc(eye, 12.4, 15.2, 0.75)
    dark = mix(A, (0, 0, 0), 0.6)
    subject = stack(layer(moon, mix(A, WHITE, 0.6), mix(A, WHITE, 0.15)), layer(rim, WHITE, mix(A, WHITE, 0.4)), layer(waves, WHITE, B),
                    layer(wolf, mix(A, (0, 0, 0), 0.55), mix(A, (0, 0, 0), 0.75), mix(A, WHITE, 0.2), None), layer(eye, WHITE, B))
    return finish(subject, [glow(moon, A, 2.0, 0.9), glow(lighter(waves, eye), B, 1.4, 0.9)], lycan_back(A, B))


def lycan_night(A, B):
    # 늑대의 밤: 어둠 속 늑대 머리 앞모습 (선 귀 · 좁은 주둥이 — 은빛 테) · 붉게 빛나는 두 눈 · 뒤의 초승달
    moon = mask()
    crescent(moon, 22.0, 8.0, 5.4, 2.4, 40, 230, taper=True)
    head = mask()
    poly(head, [(16.0, 27.0), (10.0, 19.0), (8.0, 11.0), (7.0, 3.0), (12.0, 8.0), (16.0, 7.0), (20.0, 8.0), (25.0, 3.0), (24.0, 11.0), (22.0, 19.0)])
    rim = mask()
    line(rim, [(16.0, 27.0), (10.0, 19.0), (8.0, 11.0), (7.0, 3.0), (12.0, 8.0), (16.0, 7.0), (20.0, 8.0), (25.0, 3.0), (24.0, 11.0), (22.0, 19.0), (16.0, 27.0)], 0.9)
    eyes = mask()
    for side in (-1, 1):
        poly(eyes, [(16.0 + side * 5.6, 13.0), (16.0 + side * 1.8, 15.0), (16.0 + side * 4.6, 15.6)])
    nose = mask()
    disc(nose, 16.0, 24.4, 1.4)
    dark = mix(A, (0, 0, 0), 0.75)
    subject = stack(layer(moon, WHITE, A), layer(head, mix(dark, A, 0.25), dark), layer(rim, WHITE, A), layer(nose, mix(A, WHITE, 0.3), A), layer(eyes, WHITE, B))
    return finish(subject, [glow(eyes, B, 1.6, 1.0), glow(lighter(rim, moon), A, 1.2, 0.7)], lycan_back(A, B))


def lycan_ult(A, B):
    # 달 삼키기: 그림자에 삼켜진 달 (어두운 원판 · 붉은 테) · 그 위를 찢는 붉은 발톱 네 줄 · 남은 은빛 초승달
    shadow = mask()
    disc(shadow, 16.0, 15.0, 10.0)
    corona = mask()
    ring(corona, 16.0, 15.0, 10.6, 1.4)
    sliver = mask()
    crescent(sliver, 16.0, 15.0, 9.6, 2.2, 120, 220, taper=True)
    claws = claw_lines(mask(), 27.0, 4.0, 6.0, 27.0, 4, 3.0, 1.6)
    core = claw_lines(mask(), 27.0, 4.0, 6.0, 27.0, 4, 3.0, 0.5)
    dark = mix(B, (0, 0, 0), 0.82)
    subject = stack(layer(corona, mix(B, WHITE, 0.4), B), layer(shadow, mix(dark, B, 0.15), dark), layer(sliver, WHITE, A),
                    layer(claws, mix(B, WHITE, 0.3), B, mix(B, WHITE, 0.6), mix(B, (0, 0, 0), 0.4)), layer(core, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(corona, B, 2.0, 1.0), glow(lighter(claws, sliver), B, 1.4, 0.9)], lycan_back(A, B))


# ------------------------------------------------------------ 뵐룬드(룬 나이트) (빛: 보라 → 금빛 룬)

RUNE_NIGHT = (10, 6, 30)


def runeknight_back(A, B):
    return backdrop(mix(A, RUNE_NIGHT, 0.84), mix(A, (40, 14, 84), 0.45))


def glyph(m, k, cx, cy, w, h, lw, ang=0.0):
    """룬 글자 k (job_fx_art_v2.GLYPHS — 게임의 rfx_jglyph · jrunes24 와 같은 꼴): (cx, cy) 가운데 · 폭 w · 높이 h · 굵기 lw · ang 도 돌림"""
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))

    def xf(x, y):
        dx, dy = (x - 0.5) * w, (y - 0.5) * h
        return (cx + dx * ca - dy * sa, cy + dx * sa + dy * ca)

    for st in GLYPHS[k % len(GLYPHS)]:
        if isinstance(st, tuple) and st and st[0] == "d":
            x, y = xf(st[1], st[2])
            disc(m, x, y, lw * 0.75)
        else:
            line(m, [xf(x, y) for x, y in st], lw)
    return m


def oval(m, cx, cy, rx, ry, w):
    """납작한 고리 (땅의 룬 원)"""
    d = ImageDraw.Draw(m)
    d.ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=255)
    d.ellipse([(cx - rx + w) * SS, (cy - ry + w * 0.6) * SS, (cx + rx - w) * SS, (cy + ry - w * 0.6) * SS], fill=0)
    return m


def rune_sword(tip, end, A, B, blade=3.0, guard=7.0):
    """룬 대검: 흰 보랏빛 날 · 금빛 코등이 (룬 나이트)"""
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(WHITE, mix(A, WHITE, 0.55), WHITE, mix(A, (0, 0, 0), 0.35)),
                        guard_cols=(mix(B, WHITE, 0.4), B, WHITE, mix(B, (0, 0, 0), 0.45)))


def runeknight_carve(A, B):
    # 룬 새김 베기: 앞 공중에 새긴 금빛 룬 셋 (왼쪽 · 가운데 위 · 오른쪽) · 아래왼쪽에서 셋의 가운데로 찌르는 룬 대검 · 칼끝의 터짐 · 글자 뒤 벤 자국 (옅은 보라 호)
    cut = mask()
    crescent(cut, 16.0, 30.0, 17.0, 1.8, 206, 334)
    g = mask()
    glyph(g, 2, 7.6, 13.0, 6.0, 8.6, 1.4)
    glyph(g, 7, 16.6, 6.0, 6.0, 8.6, 1.4)
    glyph(g, 4, 25.6, 13.0, 6.0, 8.6, 1.4)
    sw = rune_sword((16.6, 15.6), (2.6, 30.0), A, B, blade=3.0, guard=6.6)
    sp = mask()
    star(sp, 16.8, 15.0, 3.8, 0.2, 45)
    subject = stack(layer(cut, mix(A, (0, 0, 0), 0.1), mix(A, (0, 0, 0), 0.35)), layer(g, mix(B, WHITE, 0.55), B, WHITE, mix(B, (0, 0, 0), 0.4)), *sw,
                    layer(sp, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(lighter(g, sp), B, 1.6, 1.0), glow(cut, A, 1.4, 0.7)], runeknight_back(A, B))


def runeknight_seid(A, B):
    # 세이드 섬광: 가까운 룬 원 (아래) · 먼 룬 원 (위) 을 잇는 보랏빛 번개 (두 끝 화살 — 갔다가 되돌아옴) · 원마다 금빛 룬 점
    near, far = mask(), mask()
    oval(near, 16.0, 26.0, 11.4, 3.8, 1.4)
    oval(far, 16.0, 6.6, 7.6, 2.6, 1.2)
    dots = mask()
    for i in range(8):
        a = math.radians(i * 45 + 22)
        disc(dots, 16.0 + math.cos(a) * 9.2, 26.0 + math.sin(a) * 2.7, 0.8)
    bolt_m = mask()
    line(bolt_m, [(15.0, 23.4), (18.6, 18.6), (13.6, 15.4), (17.0, 10.2)], 1.7)
    heads = mask()
    poly(heads, [(17.4, 6.8), (19.8, 11.6), (14.4, 10.4)])
    poly(heads, [(14.4, 27.0), (12.4, 22.0), (17.6, 23.6)])
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(far, mix(B, WHITE, 0.3), B, None, mix(B, (0, 0, 0), 0.4)), layer(near, mix(B, WHITE, 0.35), B, WHITE, mix(B, (0, 0, 0), 0.4)),
                    layer(dots, WHITE, mix(B, WHITE, 0.5)), layer(bolt_m, WHITE, A, WHITE, dark, axis=(0, -1)), layer(heads, WHITE, A, WHITE, dark))
    return finish(subject, [glow(lighter(near, far), B, 1.6, 0.9), glow(lighter(bolt_m, heads), A, 1.6, 1.0)], runeknight_back(A, B))


def runeknight_futhark(A, B):
    # 푸타르크 대검무: 가운데 금빛 룬 고리 (반짝임) 둘레를 도는 마력 검 여섯 (도는 쪽으로 누움) · 흐린 궤도
    orbit = mask()
    ring(orbit, 16.0, 16.0, 13.4, 0.8)
    rr = mask()
    ring(rr, 16.0, 16.0, 5.8, 1.2)
    g = mask()
    star(g, 16.0, 16.0, 3.8, 0.24, 0)
    swords = []
    for i in range(6):
        a = math.radians(-90 + i * 60)
        cx, cy = 16 + math.cos(a) * 10.6, 16 + math.sin(a) * 10.6
        ta = a + math.radians(90)
        tip = (cx + math.cos(ta) * 5.2, cy + math.sin(ta) * 5.2)
        end = (cx - math.cos(ta) * 4.6, cy - math.sin(ta) * 4.6)
        swords += rune_sword(tip, end, A, B, blade=2.4, guard=4.6)
    subject = stack(layer(orbit, mix(A, (0, 0, 0), 0.1), mix(A, (0, 0, 0), 0.3)), *swords, layer(rr, mix(B, WHITE, 0.4), B, WHITE, None),
                    layer(g, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(lighter(rr, g), B, 2.0, 1.0), glow(orbit, A, 1.2, 0.7)], runeknight_back(A, B))


def runeknight_temper(A, B):
    # 룬 담금질: 가운데 곧게 선 룬 대검 (날이 금빛으로 달아오름 — 아래가 더 뜨거움) · 발밑 보랏빛 룬 불 고리 · 솟는 불꽃 · 망치 불티
    flames = mask()
    for x, h in ((7.0, 6.0), (11.0, 8.0), (21.0, 8.0), (25.0, 6.0)):
        poly(flames, [(x - 1.8, 27.0), (x, 27.0 - h), (x + 1.8, 27.0)])
    fire = mask()
    oval(fire, 16.0, 27.0, 12.0, 3.4, 1.5)
    parts = {"blade": mask(), "guard": mask(), "grip": mask()}
    from job_skill_icons import sword
    sword(None, (16.0, 1.6), (16.0, 27.5), 5.2, 12.0, parts=parts)
    hot = layer(parts["blade"], WHITE, mix(B, WHITE, 0.15), WHITE, mix(B, (0, 0, 0), 0.35))
    guard = layer(parts["guard"], mix(A, WHITE, 0.35), A, WHITE, mix(A, (0, 0, 0), 0.45))
    grip = layer(parts["grip"], mix(A, (0, 0, 0), 0.5), mix(A, (0, 0, 0), 0.75))
    vein = mask()
    line(vein, [(16.0, 5.0), (16.0, 19.0)], 0.8)
    sp = mask()
    star(sp, 6.5, 9.0, 3.0, 0.2, 0)
    star(sp, 26.0, 6.0, 2.6, 0.2, 0)
    star(sp, 25.0, 15.5, 2.0, 0.22, 0)
    subject = stack(layer(flames, mix(A, WHITE, 0.4), A, None, None), layer(fire, mix(A, WHITE, 0.4), A, WHITE, mix(A, (0, 0, 0), 0.4)), grip, hot, guard,
                    layer(vein, mix(B, (255, 140, 40), 0.4), mix(B, (255, 120, 30), 0.5)), layer(sp, WHITE, B))
    return finish(subject, [glow(parts["blade"], B, 2.2, 1.0), glow(lighter(fire, flames), A, 1.6, 0.9)], runeknight_back(A, B))


def runeknight_ult(A, B):
    # 스물네 룬의 대검: 뒤 하늘의 금빛 룬 바퀴 (스물넷 눈금 · 여덟 살) · 그 가운데로 내리꽂히는 거대한 룬 대검 · 땅의 터짐 (호 둘)
    wheel = mask()
    ring(wheel, 16.0, 12.0, 11.6, 1.2)
    ticks = mask()
    for i in range(24):
        a = math.radians(i * 15)
        r0, r1 = (8.4, 10.2) if i % 3 else (7.6, 10.4)
        line(ticks, [(16.0 + math.cos(a) * r0, 12.0 + math.sin(a) * r0), (16.0 + math.cos(a) * r1, 12.0 + math.sin(a) * r1)], 0.8)
    spokes = mask()
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        line(spokes, [(16.0 + math.cos(a) * 2.6, 12.0 + math.sin(a) * 2.6), (16.0 + math.cos(a) * 6.6, 12.0 + math.sin(a) * 6.6)], 0.7)
    big = rune_sword((16.0, 30.0), (16.0, 2.0), A, B, blade=6.0, guard=13.0)
    imp = mask()
    crescent(imp, 16.0, 31.0, 13.0, 2.2, 192, 246)
    crescent(imp, 16.0, 31.0, 13.0, 2.2, 294, 348)
    subject = stack(layer(wheel, mix(B, WHITE, 0.3), B, None, None), layer(ticks, mix(B, WHITE, 0.45), B), layer(spokes, mix(B, (0, 0, 0), 0.1), mix(B, (0, 0, 0), 0.3)),
                    *big, layer(imp, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.4)))
    return finish(subject, [glow(lighter(wheel, ticks), B, 1.8, 0.9), glow(imp, A, 1.4, 0.9)], runeknight_back(A, B))


# ------------------------------------------------------------ 투르스(헥스 블레이드) (빛: 호박빛 → 먹빛 자주)

HEX_INK = (18, 6, 14)


def hexblade_back(A, B):
    return backdrop(mix(B, HEX_INK, 0.7), mix(B, (70, 34, 14), 0.5))


def thorn(m, cx, cy, h, lw, spikes=True):
    """ᚦ (게임의 rfx_jthorn 과 같은 꼴 — 줄기 · 오른쪽 가시 (줄기 0.28 ~ 0.72 에서 0.36 h) · 줄기 왼쪽의 작은 거꾸로 선 가시)"""
    top, bot = cy - h / 2, cy + h / 2
    sx = cx - h * 0.12
    up_y, lo_y = top + h * 0.28, top + h * 0.72
    tip = (sx + h * 0.36, cy)
    line(m, [(sx, top), (sx, bot)], lw)
    line(m, [(sx, up_y), tip, (sx, lo_y)], lw)
    if spikes:
        for k in range(3):
            y = top + h * (0.16 + 0.3 * k)
            poly(m, [(sx - lw * 0.4, y - h * 0.04), (sx - lw * 0.4 - h * 0.1, y + h * 0.07), (sx - lw * 0.4, y + h * 0.04)])
    return m


def hex_sword(tip, end, A, B, blade=2.8, guard=6.6):
    """저주받은 칼: 먹빛 날 (호박빛 날끝) · 검은 코등이"""
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(mix(A, WHITE, 0.25), mix(B, A, 0.35), mix(A, WHITE, 0.5), mix(B, (0, 0, 0), 0.5)),
                        guard_cols=((92, 76, 70), (40, 30, 30), (150, 120, 96), (18, 12, 12)), grip_cols=(mix(B, (0, 0, 0), 0.3), mix(B, (0, 0, 0), 0.6), None, None))


def hexblade_thorn(A, B):
    # 가시 룬 베기: 오른쪽 아래에서 왼쪽 위로 올려 벤 옅은 호 · 왼쪽 위에서 오른쪽 아래로 크게 내려 벤 밝은 호 · 벤 자리에 박힌 큰 가시 룬 (호박빛 ᚦ)
    up, down = mask(), mask()
    crescent(up, 16.0, 38.0, 20.0, 2.0, 225, 297)
    crescent(down, 0.0, 2.0, 27.0, 3.4, 10, 82)
    t = mask()
    thorn(t, 10.0, 10.6, 14.0, 1.8)
    dark = mix(B, (0, 0, 0), 0.4)
    subject = stack(layer(up, mix(A, (0, 0, 0), 0.15), mix(A, (0, 0, 0), 0.45)), layer(down, WHITE, A, WHITE, dark, axis=(0.6, 0.8)),
                    layer(t, mix(A, WHITE, 0.6), A, WHITE, mix(B, (0, 0, 0), 0.2)))
    return finish(subject, [glow(t, A, 1.8, 1.0), glow(lighter(up, down), A, 1.2, 0.7)], hexblade_back(A, B))


def hexblade_backstep(A, B):
    # 뒤집힌 걸음: 앞의 저주 룬 덫 (땅의 고리 · 그 위에 선 ᚦ) · 덫에서 뒤 (왼쪽 위) 로 물러나는 먹빛 그림자 줄과 화살
    trap = mask()
    oval(trap, 21.0, 25.5, 9.6, 3.2, 1.3)
    t = mask()
    thorn(t, 22.0, 16.0, 13.0, 1.6)
    trail = mask()
    for y0, x0, x1, w in ((9.0, 12.0, 4.5, 1.2), (13.0, 14.0, 5.5, 1.5), (17.0, 12.0, 6.0, 1.2)):
        line(trail, [(x0, y0 + 2.0), (x1, y0)], w)
    arrow = mask()
    poly(arrow, [(1.6, 11.0), (6.4, 8.0), (6.6, 14.6)])
    dark = mix(B, (0, 0, 0), 0.3)
    subject = stack(layer(trap, mix(A, WHITE, 0.3), A, None, mix(B, (0, 0, 0), 0.3)), layer(trail, mix(B, WHITE, 0.35), B, None, None, axis=(-1, 0)),
                    layer(arrow, mix(B, WHITE, 0.45), B, mix(B, WHITE, 0.6), dark), layer(t, mix(A, WHITE, 0.6), A, WHITE, mix(B, (0, 0, 0), 0.2)))
    return finish(subject, [glow(lighter(trap, t), A, 1.8, 1.0)], hexblade_back(A, B))


def hexblade_hail(A, B):
    # 우박의 룬: 땅에 꽂힌 칼 · 둘레 땅의 룬 고리 (호박빛) · 하늘에서 비스듬히 떨어지는 검은 우박 조각 넷 (호박빛 테)
    ringm = mask()
    oval(ringm, 16.0, 26.0, 13.0, 3.8, 1.3)
    sw = hex_sword((16.0, 27.5), (16.0, 9.0), A, B, blade=3.0, guard=7.4)
    shards = mask()
    for cx, cy, L in ((6.0, 7.0, 7.0), (12.0, 3.4, 5.6), (24.0, 5.0, 7.0), (27.5, 15.0, 5.6)):
        ux, uy = -0.42, 0.91
        px, py = -uy, ux
        poly(shards, [(cx + ux * L * 0.55, cy + uy * L * 0.55), (cx + px * 1.3, cy + py * 1.3), (cx - ux * L * 0.45, cy - uy * L * 0.45), (cx - px * 1.3, cy - py * 1.3)])
    crack = mask()
    for pts in (((16.0, 27.0), (11.0, 28.5), (6.0, 27.6)), ((16.0, 27.0), (21.5, 29.0), (26.0, 27.8))):
        line(crack, list(pts), 0.9)
    subject = stack(layer(ringm, mix(A, WHITE, 0.3), A, None, mix(B, (0, 0, 0), 0.3)), layer(crack, mix(A, WHITE, 0.4), A), *sw,
                    layer(shards, mix(B, (0, 0, 0), 0.2), mix(B, (0, 0, 0), 0.55), mix(A, WHITE, 0.3), A))
    return finish(subject, [glow(lighter(ringm, crack), A, 1.8, 1.0), glow(shards, A, 1.0, 0.6)], hexblade_back(A, B))


def hexblade_curse(A, B):
    # 저주의 칼: 비스듬히 든 칼 · 날에서 솟는 먹빛 불꽃 혀 (호박빛 테) · 칼날 위에 떠오른 ᚦ · 불티
    fl = mask()
    for i, (t, h) in enumerate(((0.22, 6.0), (0.42, 8.0), (0.62, 7.0), (0.8, 5.4))):
        x, y = 5.0 + 20.0 * t, 27.0 - 21.0 * t
        poly(fl, [(x - 2.4, y + 1.2), (x - 0.6 + (1.2 if i % 2 else -1.2), y - h), (x + 2.6, y + 1.6)])
    sw = hex_sword((26.0, 5.0), (4.0, 28.0), A, B, blade=3.0, guard=7.0)
    t = mask()
    thorn(t, 9.4, 8.6, 11.0, 1.5)
    sp = mask()
    star(sp, 27.0, 15.0, 2.4, 0.22, 0)
    star(sp, 19.0, 3.6, 2.0, 0.22, 0)
    subject = stack(layer(fl, mix(B, A, 0.35), mix(B, (0, 0, 0), 0.2), mix(A, WHITE, 0.25), None), *sw, layer(t, mix(A, WHITE, 0.6), A, WHITE, mix(B, (0, 0, 0), 0.2)),
                    layer(sp, WHITE, A))
    return finish(subject, [glow(fl, A, 1.6, 0.8), glow(t, A, 1.6, 1.0)], hexblade_back(A, B))


def hexblade_ult(A, B):
    # 재앙의 룬: 땅에 새긴 거대한 ᚦ (호박빛) · 줄기를 세로로 가른 흰 빛 · 아래에서 솟구치는 먹빛 저주 줄기 (호박빛 테) · 땅의 금
    burst = mask()
    for x, h, w in ((5.0, 12.0, 2.0), (9.0, 17.0, 2.2), (24.0, 18.0, 2.2), (28.0, 11.0, 1.8)):
        poly(burst, [(x - w, 31.0), (x, 31.0 - h), (x + w, 31.0)])
    t = mask()
    thorn(t, 17.0, 15.6, 25.0, 2.6)
    cut = mask()
    line(cut, [(14.0, 1.0), (14.0, 30.5)], 0.9)
    crack = mask()
    for pts in (((14.0, 29.0), (9.0, 30.6), (3.0, 29.4)), ((14.0, 29.0), (20.5, 30.8), (29.0, 29.6))):
        line(crack, list(pts), 0.9)
    subject = stack(layer(burst, mix(B, A, 0.25), mix(B, (0, 0, 0), 0.3), mix(A, WHITE, 0.2), None), layer(crack, mix(A, WHITE, 0.4), A),
                    layer(t, mix(A, WHITE, 0.55), A, WHITE, mix(B, (0, 0, 0), 0.2)), layer(cut, WHITE, mix(A, WHITE, 0.7)))
    return finish(subject, [glow(lighter(t, cut), A, 2.0, 1.0), glow(burst, A, 1.2, 0.6)], hexblade_back(A, B))


ICONS = {
    "swordmaster_flurry": swordmaster_flurry,
    "swordmaster_grani": swordmaster_grani,
    "swordmaster_moon": swordmaster_moon,
    "swordmaster_heart": swordmaster_heart,
    "swordmaster_ult": swordmaster_ult,
    "bloodknight_flurry": bloodknight_flurry,
    "bloodknight_rush": bloodknight_rush,
    "bloodknight_burst": bloodknight_burst,
    "bloodknight_oath": bloodknight_oath,
    "bloodknight_ult": bloodknight_ult,
    "warlord_crush": warlord_crush,
    "warlord_throw": warlord_throw,
    "warlord_storm": warlord_storm,
    "warlord_roar": warlord_roar,
    "warlord_ult": warlord_ult,
    "lycan_fang": lycan_fang,
    "lycan_pounce": lycan_pounce,
    "lycan_howl": lycan_howl,
    "lycan_night": lycan_night,
    "lycan_ult": lycan_ult,
    "runeknight_carve": runeknight_carve,
    "runeknight_seid": runeknight_seid,
    "runeknight_futhark": runeknight_futhark,
    "runeknight_temper": runeknight_temper,
    "runeknight_ult": runeknight_ult,
    "hexblade_thorn": hexblade_thorn,
    "hexblade_backstep": hexblade_backstep,
    "hexblade_hail": hexblade_hail,
    "hexblade_curse": hexblade_curse,
    "hexblade_ult": hexblade_ult,
}
