#!/usr/bin/env python3
"""0.51.0 2차 직업 스킬 아이콘 — 검사 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트) · 전사 각성 뵈드바르(워로드) · 반전 울프헤딘(라이칸) ·
마검사 각성 뵐룬드(룬 나이트) · 반전 투르스(헥스 블레이드) · 매지션 각성 오드레리르(아크메이지) · 반전 나글파르(네크로맨서) ·
궁수 각성 에길(스나이퍼) · 반전 미르크비드(섀도 헌터) (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음 — 룬 글자는 게임 그림 rfx_jglyph · jrunes24 · jthorn 과 같은 꼴).
색: 소드마스터 = 금빛 (첫째) · 은빛 (둘째), 블러드 나이트 = 짙은 피 (첫째) · 밝은 피 (둘째), 워로드 = 주황 (첫째) · 갈색 (둘째),
라이칸 = 달빛 은회 (첫째) · 붉은 눈빛 (둘째), 룬 나이트 = 보라 (첫째) · 금빛 룬 (둘째), 헥스 블레이드 = 호박빛 (첫째) · 먹빛 자주 (둘째), 아크메이지 = 별빛 파랑 (첫째) · 금빛 (둘째 — 밤하늘 자주 바탕),
네크로맨서 = 독초록 (첫째) · 뼈색 (둘째 — 짙은 녹색 안개 바탕), 스나이퍼 = 밝은 연두 (첫째) · 금빛 흰 섬광 (둘째 — 짙은 숲 바탕),
섀도 헌터 = 남보라 (첫째) · 은빛 (둘째 — 짙은 남색 바탕 · 검은 화살은 먹빛 살대 · 은빛 촉) — jobs.json advance."""
import math
import os
import sys

from PIL import ImageChops, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (GOLD, GOLD_HI, GOLD_LO, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix,  # noqa: E402
                             poly, ring, stack, star, sword_layers)
from job_skill_icons_more import axe_layers  # noqa: E402
from job_skill_icons import P, SS  # noqa: E402
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


# ------------------------------------------------------------ 오드레리르(아크메이지) (빛: 별빛 파랑 → 금빛 — 밤하늘 자주 바탕)

ARC_NIGHT = (10, 6, 34)


def arcmage_back(A, B):
    return backdrop(mix(A, ARC_NIGHT, 0.86), mix((70, 40, 150), A, 0.35))


def star5(m, cx, cy, r, inner=0.44, rot=0.0):
    """다섯 뿔 별"""
    pts = []
    for i in range(10):
        a = math.radians(rot) - math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    poly(m, pts)
    return m


def arcmage_bolts(A, B):
    # 별무리 마탄: 완드 (왼쪽 아래) 끝에서 지그재그로 꺾이며 날아가는 별빛 마탄 셋 (작은 별 머리 · 꼬리) → 오른쪽 위 과녁의 큰 별 터짐
    zig = [(9.0, 24.0), (15.0, 21.0), (12.0, 15.0), (19.0, 12.0), (17.0, 7.0), (25.0, 6.0)]
    trail = mask()
    line(trail, zig, 1.1)
    heads = mask()
    for (x, y, r) in ((15.0, 21.0, 2.4), (12.0, 15.0, 2.4), (19.0, 12.0, 2.6)):
        star5(heads, x, y, r)
    burst = mask()
    star(burst, 25.5, 6.0, 6.4, 0.18, 0)
    big = mask()
    star5(big, 25.5, 6.0, 4.0)
    wand = mask()
    line(wand, [(2.5, 31.0), (8.0, 25.0)], 2.0)
    tip = mask()
    star(tip, 8.6, 24.4, 3.6, 0.2, 45)
    subject = stack(layer(trail, mix(A, WHITE, 0.35), A), layer(wand, mix(A, WHITE, 0.2), mix(A, (0, 0, 0), 0.4)), layer(heads, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.25)),
                    layer(burst, WHITE, mix(B, WHITE, 0.3)), layer(big, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.25)), layer(tip, WHITE, B))
    return finish(subject, [glow(lighter(heads, big), B, 1.6, 1.0), glow(trail, A, 1.2, 0.8), glow(tip, B, 1.4, 0.9)], arcmage_back(A, B))


def mage_bolt(hx, hy, ang, L, w=2.2):
    """마탄 (머리 마름모 · 꼬리) — job_skill_icons_more.bolt_mask 와 같은 꼴"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    tailm = mask()
    poly(tailm, [(hx + px * w * 0.55, hy + py * w * 0.55), (hx - ux * L, hy - uy * L), (hx - px * w * 0.55, hy - py * w * 0.55)])
    headm = mask()
    poly(headm, [(hx + ux * w * 1.6, hy + uy * w * 1.6), (hx + px * w, hy + py * w), (hx - ux * w * 0.9, hy - uy * w * 0.9), (hx - px * w, hy - py * w)])
    return tailm, headm


def arcmage_phase(A, B):
    # 서리 별 걸음: 얼음빛 두건 쓴 잔상 둘 (앞 큰 것 · 뒤 작은 것 — 가슴의 금빛 별) · 꺾여 뒤로 물러나는 두 걸음 화살 · 얼음 조각
    ICE = (150, 215, 255)
    ghosts = []
    for (cx, cy, s_) in ((22.0, 16.0, 1.0), (11.0, 12.0, 0.72)):
        g = mask()
        disc(g, cx, cy - 6.2 * s_, 2.8 * s_)
        poly(g, [(cx - 1.8 * s_, cy - 4.4 * s_), (cx + 1.8 * s_, cy - 4.4 * s_), (cx + 5.6 * s_, cy + 9.0 * s_), (cx - 5.6 * s_, cy + 9.0 * s_)])
        ghosts.append(g)
    stars = mask()
    star5(stars, 22.0, 13.0, 2.8)
    star5(stars, 11.0, 10.5, 2.1)
    path = mask()
    line(path, [(26.0, 28.5), (16.0, 24.5), (6.5, 27.5)], 1.5)
    poly(path, [(2.0, 28.5), (7.2, 24.4), (7.6, 30.4)])
    shards = mask()
    for (x, y, r) in ((29.0, 6.5, 2.0), (4.0, 4.5, 1.8), (17.0, 3.0, 1.5)):
        poly(shards, [(x, y - r * 1.6), (x + r, y), (x, y + r * 1.6), (x - r, y)])
    subject = stack(layer(ghosts[1], mix(ICE, WHITE, 0.15), mix(A, (0, 0, 0), 0.15), None, None), layer(ghosts[0], mix(ICE, WHITE, 0.45), mix(ICE, A, 0.4), WHITE,
                    mix(A, (0, 0, 0), 0.2)), layer(path, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3), axis=(-1, 0)), layer(stars, WHITE, B), layer(shards, WHITE, ICE))
    return finish(subject, [glow(lighter(*ghosts), ICE, 1.8, 0.8), glow(stars, B, 1.4, 1.0)], arcmage_back(A, B))


def arcmage_orb(A, B):
    # 쌍둥이 별: 서로 도는 별 둘 (금빛 · 별빛) · 둘을 잇는 빛줄기 · 도는 궤도 고리 (기울어짐) · 반짝이
    orbit = mask()
    oval(orbit, 16.0, 16.0, 12.5, 6.5, 1.0)
    beam = mask()
    line(beam, [(7.0, 20.5), (25.0, 11.5)], 1.6)
    beam_core = mask()
    line(beam_core, [(7.0, 20.5), (25.0, 11.5)], 0.6)
    s1, s2 = mask(), mask()
    star5(s1, 25.0, 11.5, 6.0, rot=8)
    star5(s2, 7.0, 20.5, 5.2, rot=-10)
    sp = mask()
    for x, y in ((16.0, 4.5), (27.5, 25.5), (4.0, 7.5)):
        star(sp, x, y, 2.2, 0.2, 0)
    subject = stack(layer(orbit, mix(A, WHITE, 0.3), A), layer(beam, mix(B, WHITE, 0.3), B), layer(beam_core, WHITE, WHITE),
                    layer(s2, mix(A, WHITE, 0.6), A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(s1, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.25)), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(s1, s2), B, 2.0, 1.0), glow(beam, B, 1.2, 0.8)], arcmage_back(A, B))


def arcmage_song(A, B):
    # 꿀술의 노래: 금빛 꿀술 뿔잔 (기울어 넘치는 꿀술 방울) · 떠오르는 음표 둘 · 위에서 떨어지는 작은 별똥 · 반짝이
    horn = mask()
    pts = [(4.0, 25.0), (9.0, 28.5), (17.0, 27.0), (23.0, 22.0)]
    line(horn, pts, 4.6)
    rim = mask()
    oval(rim, 22.5, 21.0, 3.6, 2.4, 1.0)
    mead = mask()
    for (x, y, r) in ((24.5, 17.5, 1.6), (26.5, 14.0, 1.2), (27.5, 10.8, 0.9)):
        disc(mead, x, y, r)
    notes = mask()
    for (x, y, sc) in ((8.0, 12.0, 1.0), (15.0, 6.0, 0.8)):
        disc(notes, x, y + 4.0 * sc, 2.0 * sc)
        line(notes, [(x + 1.7 * sc, y + 4.0 * sc), (x + 1.7 * sc, y - 3.5 * sc)], 1.0 * sc)
        line(notes, [(x + 1.7 * sc, y - 3.5 * sc), (x + 5.0 * sc, y - 2.0 * sc)], 1.1 * sc)
    fall = mask()
    line(fall, [(29.0, 2.0), (22.5, 8.0)], 0.9)
    st = mask()
    star5(st, 22.0, 8.5, 2.6)
    subject = stack(layer(horn, mix(B, WHITE, 0.25), mix(B, (60, 30, 0), 0.4), WHITE, mix(B, (0, 0, 0), 0.4), axis=(1, 0)), layer(rim, WHITE, B),
                    layer(mead, mix(B, WHITE, 0.4), B), layer(notes, WHITE, mix(A, WHITE, 0.3)), layer(fall, WHITE, B), layer(st, WHITE, B))
    return finish(subject, [glow(lighter(mead, st), B, 1.8, 1.0), glow(notes, A, 1.4, 0.8)], arcmage_back(A, B))


def arcmage_ult(A, B):
    # 별의 노래: 하늘의 별자리 (북두칠성 — 금빛 선 · 별 일곱) · 아래 V 자로 펼친 마도서 (두 쪽 · 글줄) · 책에서 별자리로 솟는 가는 빛 셋 · 떨어지는 별똥 둘
    pts = [(3.0, 9.5), (8.0, 7.0), (13.0, 8.5), (17.5, 6.0), (20.0, 2.5), (27.0, 3.5), (28.5, 8.5)]
    lines_ = mask()
    line(lines_, pts, 0.8)
    line(lines_, [pts[3], pts[6]], 0.8)
    stars = mask()
    for i, (x, y) in enumerate(pts):
        star5(stars, x, y, 2.4 if i in (0, 3, 6) else 1.9)
    left = mask()
    poly(left, [(15.4, 22.0), (3.0, 18.5), (3.5, 27.0), (15.4, 30.5)])
    right = mask()
    poly(right, [(16.6, 22.0), (29.0, 18.5), (28.5, 27.0), (16.6, 30.5)])
    text = mask()
    for k in range(3):
        y = 22.0 + k * 2.3
        line(text, [(6.0, y - 0.9), (13.0, y + 0.9)], 0.6)
        line(text, [(19.0, y + 0.9), (26.0, y - 0.9)], 0.6)
    rays = mask()
    for (x0, x1) in ((14.0, 9.0), (16.0, 16.5), (18.0, 23.0)):
        line(rays, [(x0, 21.0), (x1, 12.0)], 0.7)
    falls = mask()
    for (x0, y0, x1, y1) in ((10.0, 10.0, 6.5, 15.5), (24.0, 9.5, 27.0, 15.0)):
        line(falls, [(x0, y0), (x1, y1)], 0.9)
        star(falls, x1, y1, 2.0, 0.2, 0)
    subject = stack(layer(rays, mix(B, WHITE, 0.4), B), layer(lines_, mix(B, WHITE, 0.4), B), layer(falls, WHITE, B),
                    layer(left, WHITE, mix(B, WHITE, 0.4), WHITE, mix(A, (0, 0, 0), 0.3)), layer(right, mix(B, WHITE, 0.6), mix(B, WHITE, 0.2), WHITE, mix(A, (0, 0, 0), 0.3)),
                    layer(text, mix(A, (0, 0, 0), 0.2), mix(A, (0, 0, 0), 0.4)), layer(stars, WHITE, B))
    return finish(subject, [glow(stars, B, 1.8, 1.0), glow(lighter(rays, left, right), B, 1.4, 0.6)], arcmage_back(A, B))


# ------------------------------------------------------------ 나글파르(네크로맨서) (빛: 독초록 → 뼈색 — 짙은 녹색 안개 바탕)

NECRO_FOG = (4, 18, 10)


def necro_back(A, B):
    return backdrop(mix(A, NECRO_FOG, 0.86), mix((20, 70, 40), A, 0.3))


def skull_mask(cx, cy, s):
    """해골 (머리 · 턱 — 눈 · 코 구멍은 따로 eyes)"""
    m = mask()
    disc(m, cx, cy - 1.2 * s, 5.2 * s)
    poly(m, [(cx - 3.8 * s, cy + 1.0 * s), (cx + 3.8 * s, cy + 1.0 * s), (cx + 2.6 * s, cy + 5.6 * s), (cx - 2.6 * s, cy + 5.6 * s)])
    eyes = mask()
    disc(eyes, cx - 2.0 * s, cy, 1.4 * s)
    disc(eyes, cx + 2.0 * s, cy, 1.4 * s)
    poly(eyes, [(cx, cy + 1.6 * s), (cx - 0.8 * s, cy + 3.0 * s), (cx + 0.8 * s, cy + 3.0 * s)])
    return ImageChops.subtract(m, eyes), eyes


def bone_layers(m, B):
    return layer(m, mix(B, WHITE, 0.5), mix(B, (60, 50, 30), 0.35), WHITE, mix(B, (0, 0, 0), 0.45))


def necromancer_spear(A, B):
    # 뼈 창: 비스듬히 날아가는 등뼈 자루 · 이빨 같은 뼈 날 · 뒤로 휜 갈비 가시 · 꼬리의 녹색 혼불 연기 · 꿰뚫린 자리의 녹색 빛
    shaft = mask()
    for i in range(6):
        t = i / 6
        x, y = 4.0 + 16.0 * t, 28.0 - 16.0 * t
        disc(shaft, x, y, 1.6)
    blade = mask()
    poly(blade, [(19.0, 13.0), (23.0, 7.0), (25.0, 9.0), (27.5, 3.5), (30.5, 1.5), (28.5, 4.5), (25.0, 11.0), (22.0, 12.0), (20.5, 15.0)])
    hooks = mask()
    line(hooks, [(19.5, 14.0), (16.5, 12.5), (15.0, 9.5)], 1.0)
    line(hooks, [(20.0, 14.5), (21.0, 17.5), (23.5, 19.0)], 1.0)
    smoke = mask()
    for (x, y, r) in ((3.0, 30.0, 2.6), (6.0, 31.0, 1.8), (1.5, 27.0, 1.6)):
        disc(smoke, x, y, r)
    hit = mask()
    star(hit, 27.5, 4.5, 4.6, 0.2, 15)
    subject = stack(layer(smoke, mix(A, WHITE, 0.25), mix(A, (0, 0, 0), 0.4)), bone_layers(shaft, B), bone_layers(hooks, B), bone_layers(blade, B),
                    layer(hit, WHITE, A))
    return finish(subject, [glow(lighter(shaft, blade), A, 1.6, 0.9), glow(hit, A, 1.6, 1.0), glow(smoke, A, 1.4, 0.8)], necro_back(A, B))


def necromancer_swap(A, B):
    # 망자와 자리 바꾸기: 왼쪽 해골 망자 · 오른쪽 두건 쓴 네크로맨서 · 둘 사이 위 · 아래로 엇갈려 도는 녹색 화살 둘 · 아래 솟은 뼈 방벽
    sk, eyes = skull_mask(7.5, 12.0, 0.75)
    hood = mask()
    disc(hood, 24.5, 8.6, 2.8)
    poly(hood, [(22.6, 10.0), (26.4, 10.0), (29.5, 20.0), (19.5, 20.0)])
    arrows = mask()
    top = [(16.0 + math.cos(math.radians(a)) * 6.5, 11.0 + math.sin(math.radians(a)) * 5.0) for a in range(205, 336, 10)]
    bot = [(16.0 + math.cos(math.radians(a)) * 6.5, 13.0 + math.sin(math.radians(a)) * 5.0) for a in range(25, 156, 10)]
    line(arrows, top, 1.4)
    line(arrows, bot, 1.4)
    ex, ey = top[-1]
    poly(arrows, [(ex + 2.6, ey + 1.6), (ex - 1.6, ey - 1.2), (ex + 0.6, ey - 3.0)])
    ex, ey = bot[-1]
    poly(arrows, [(ex - 2.6, ey - 1.6), (ex + 1.6, ey + 1.2), (ex - 0.6, ey + 3.0)])
    wall = mask()
    for (x, h, w) in ((7.0, 7.0, 2.0), (12.0, 9.0, 2.2), (17.0, 7.5, 2.0), (22.0, 9.5, 2.2), (27.0, 7.0, 2.0)):
        poly(wall, [(x - w, 31.0), (x + w * 0.3, 31.0 - h), (x + w, 31.0)])
    subject = stack(layer(hood, mix(A, (0, 0, 0), 0.25), mix(A, (0, 0, 0), 0.6), mix(A, WHITE, 0.3), None), layer(arrows, mix(A, WHITE, 0.45), A, WHITE,
                    mix(A, (0, 0, 0), 0.3)), bone_layers(sk, B), layer(eyes, A, A), bone_layers(wall, B))
    return finish(subject, [glow(arrows, A, 1.6, 0.9), glow(wall, A, 1.2, 0.6), glow(eyes, A, 1.0, 1.0)], necro_back(A, B))


def necromancer_burst(A, B):
    # 시체 폭발: 가운데 터지는 해골 (녹색 눈) · 둘레로 튀는 뼈 조각 · 녹색 폭발 테 · 아래 독 구름
    blast = mask()
    star(blast, 16.0, 15.0, 15.0, 0.42, 0)
    star(blast, 16.0, 15.0, 11.0, 0.5, 22)
    cloud = mask()
    for (x, y, r) in ((7.0, 27.0, 4.0), (13.0, 28.5, 4.6), (20.0, 28.0, 4.4), (26.0, 26.5, 3.6)):
        disc(cloud, x, y, r)
    shards = mask()
    for (x, y, a) in ((4.0, 6.0, 30), (27.5, 5.0, -40), (29.0, 18.0, 10), (3.0, 18.0, -20)):
        r = math.radians(a)
        line(shards, [(x - math.cos(r) * 2.4, y - math.sin(r) * 2.4), (x + math.cos(r) * 2.4, y + math.sin(r) * 2.4)], 1.4)
    sk, eyes = skull_mask(16.0, 14.0, 1.0)
    subject = stack(layer(blast, mix(A, WHITE, 0.45), A, None, None), layer(cloud, mix(A, (0, 0, 0), 0.1), mix(A, (0, 0, 0), 0.55)), bone_layers(shards, B),
                    bone_layers(sk, B), layer(eyes, mix(A, WHITE, 0.3), A))
    return finish(subject, [glow(blast, A, 2.2, 0.9), glow(eyes, A, 1.0, 1.0)], necro_back(A, B))


def necromancer_raise(A, B):
    # 망자 일으키기: 땅에서 솟는 뼈 손 둘 (손가락을 벌림) · 가운데 녹색 혼불 · 갈라진 땅 · 위의 작은 해골 둘
    ground = mask()
    line(ground, [(1.0, 28.5), (31.0, 28.5)], 1.4)
    crack = mask()
    for pts in (((16.0, 28.5), (12.0, 30.5), (7.0, 29.4)), ((16.0, 28.5), (21.0, 30.6), (26.0, 29.6))):
        line(crack, list(pts), 0.8)
    hands = mask()
    for (x, sgn) in ((9.0, -1), (23.0, 1)):
        line(hands, [(x, 28.5), (x + sgn * 1.0, 20.0)], 2.2)
        for k, (dx, h) in enumerate(((-2.4, 5.0), (-0.8, 6.4), (0.8, 6.6), (2.4, 5.2))):
            line(hands, [(x + sgn * 1.0 + dx * 0.6, 20.0), (x + sgn * 1.0 + dx, 20.0 - h)], 0.9)
        line(hands, [(x + sgn * 1.0 - sgn * 2.0, 22.0), (x + sgn * 1.0 - sgn * 4.0, 19.5)], 0.9)
    fire = mask()
    poly(fire, [(13.0, 28.0), (16.0, 14.0), (19.0, 28.0)])
    poly(fire, [(14.5, 22.0), (17.5, 10.5), (19.5, 21.0)])
    sk1, e1 = skull_mask(8.0, 7.0, 0.55)
    sk2, e2 = skull_mask(24.0, 6.0, 0.55)
    subject = stack(layer(fire, mix(A, WHITE, 0.45), A, WHITE, None), layer(ground, mix(A, (0, 0, 0), 0.3), mix(A, (0, 0, 0), 0.6)), layer(crack, A, A),
                    bone_layers(hands, B), bone_layers(lighter(sk1, sk2), B), layer(lighter(e1, e2), A, A))
    return finish(subject, [glow(fire, A, 2.0, 1.0), glow(lighter(e1, e2), A, 1.0, 1.0)], necro_back(A, B))


def necromancer_ult(A, B):
    # 나글파르의 출항: 안개 위 손발톱 배 옆모습 (휜 뱃머리 해골 · 찢긴 돛 · 방패) · 배 밑 갈라진 땅에서 솟는 녹색 혼불 · 녹색 눈
    fog = mask()
    for (x, y, r) in ((5.0, 27.0, 4.6), (12.0, 28.5, 5.0), (20.0, 28.0, 5.0), (27.0, 27.0, 4.6)):
        disc(fog, x, y, r)
    hull = mask()
    poly(hull, [(4.0, 19.5), (28.0, 19.5), (25.0, 25.0), (8.0, 25.0)])
    prow = mask()
    line(prow, [(27.0, 20.0), (29.5, 15.0), (29.0, 10.5), (27.0, 9.0)], 1.6)
    line(prow, [(5.0, 20.0), (2.5, 15.5), (3.5, 12.5)], 1.4)
    head, eyes = skull_mask(26.6, 8.6, 0.42)
    mast = mask()
    line(mast, [(15.5, 19.5), (15.5, 3.0)], 1.0)
    line(mast, [(9.0, 4.5), (22.0, 4.5)], 0.9)
    sail = mask()
    poly(sail, [(9.5, 5.0), (21.5, 5.0), (20.5, 15.5), (18.0, 13.5), (16.0, 16.0), (13.5, 13.8), (10.5, 15.5)])
    shields = mask()
    for x in (8.5, 12.5, 16.5, 20.5, 24.0):
        disc(shields, x, 20.8, 1.3)
    flames = mask()
    for (x, h) in ((6.0, 6.0), (11.0, 8.0), (21.0, 8.5), (26.0, 6.0)):
        poly(flames, [(x - 1.6, 31.0), (x, 31.0 - h), (x + 1.6, 31.0)])
    subject = stack(layer(flames, mix(A, WHITE, 0.4), A), layer(sail, mix(A, (0, 0, 0), 0.5), mix(A, (0, 0, 0), 0.75), mix(A, (0, 0, 0), 0.3), None),
                    layer(mast, mix(B, (0, 0, 0), 0.3), mix(B, (0, 0, 0), 0.6)), bone_layers(hull, B), bone_layers(prow, B), bone_layers(head, B),
                    layer(shields, A, mix(A, (0, 0, 0), 0.3)), layer(eyes, A, A), layer(fog, mix(A, (0, 0, 0), 0.35), mix(A, (0, 0, 0), 0.7)))
    return finish(subject, [glow(flames, A, 2.0, 1.0), glow(eyes, A, 1.0, 1.0), glow(hull, A, 1.2, 0.5)], necro_back(A, B))


# ------------------------------------------------------------ 에길(스나이퍼) (빛: 밝은 연두 → 금빛 흰 섬광 — 짙은 숲 바탕)

SNIPE_WOOD = (4, 18, 6)


def sniper_back(A, B):
    return backdrop(mix(A, SNIPE_WOOD, 0.86), mix((40, 90, 24), A, 0.35))


def sn_arrow(tip, ang, L, head=3.2, w=1.4, fletch=True):
    """빛의 화살 (tip = 촉 끝 · ang = 촉이 향한 쪽, 도): 살대 · 촉 · 깃 마스크"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    tx, ty = tip
    shaft = mask()
    line(shaft, [(tx - ux * head * 0.8, ty - uy * head * 0.8), (tx - ux * L, ty - uy * L)], w)
    hd = mask()
    poly(hd, [(tx, ty), (tx - ux * head + px * head * 0.62, ty - uy * head + py * head * 0.62), (tx - ux * head * 0.68, ty - uy * head * 0.68),
              (tx - ux * head - px * head * 0.62, ty - uy * head - py * head * 0.62)])
    fl = mask()
    if fletch:
        for d in (0.0, 1.9):
            bx, by = tx - ux * (L - 1.4 - d), ty - uy * (L - 1.4 - d)
            line(fl, [(bx, by), (bx - ux * 2.6 + px * 2.1, by - uy * 2.6 + py * 2.1)], 1.0)
            line(fl, [(bx, by), (bx - ux * 2.6 - px * 2.1, by - uy * 2.6 - py * 2.1)], 1.0)
    return shaft, hd, fl


def sn_arrow_layers(A, B, shaft, hd, fl):
    return [layer(shaft, mix(A, WHITE, 0.55), A), layer(fl, WHITE, A), layer(hd, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.35))]


def sniper_pierce(A, B):
    # 꿰뚫는 연사: 오른쪽 위로 나란히 나는 화살 둘 (위 · 아래) 과 가운데 흰 섬광의 큰 꿰뚫는 화살 — 꿰뚫린 과녁 고리 둘 (비스듬한 타원) · 뒤의 섬광 줄
    rings = mask()
    for (cx, cy, rx, ry) in ((10.5, 21.5, 3.0, 7.0), (19.0, 13.0, 3.0, 7.0)):
        pts = []
        for i in range(37):
            t = math.radians(i * 10)
            x, y = math.cos(t) * rx, math.sin(t) * ry
            a = math.radians(45)
            pts.append((cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)))
        line(rings, pts, 1.3)
    flash = mask()
    poly(flash, [(1.0, 30.5), (24.5, 7.0), (25.5, 8.0), (2.0, 31.5)])
    big = sn_arrow((30.5, 1.5), -45, 30.0, 5.4, 2.0)
    s1 = sn_arrow((21.5, 2.0), -45, 13.0, 3.2, 1.2)
    s2 = sn_arrow((30.0, 10.5), -45, 13.0, 3.2, 1.2)
    sp = mask()
    star(sp, 29.0, 3.0, 3.4, 0.2, 0)
    subject = stack(layer(flash, mix(B, WHITE, 0.5), mix(A, WHITE, 0.2), None, None), *sn_arrow_layers(A, B, *s1), *sn_arrow_layers(A, B, *s2),
                    layer(rings, mix(B, WHITE, 0.3), B), layer(big[0], WHITE, mix(B, WHITE, 0.4)), layer(big[2], WHITE, A),
                    layer(big[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(big[0], big[1]), B, 1.6, 1.0), glow(flash, A, 1.6, 0.7), glow(rings, B, 1.0, 0.6)], sniper_back(A, B))


def sniper_gale(A, B):
    # 순풍 공중 사격: 왼쪽 아래에서 위로 휘감아 오르는 바람 줄 둘 · 바람 위 깃털 · 위에서 오른쪽 아래로 내리꽂는 화살 셋
    gusts = []
    for (cx, cy, r, a0, sw) in ((10.0, 20.0, 8.5, 150, 260), (13.0, 25.0, 6.0, 170, 220)):
        m = mask()
        pts = []
        for i in range(25):
            t = i / 24
            a = math.radians(a0 + sw * t)
            rr = r * (1 - 0.45 * t)
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr - 6.0 * t))
        line(m, pts, 1.6)
        gusts.append(m)
    feather = mask()
    poly(feather, [(4.0, 10.0), (8.5, 3.5), (10.5, 4.5), (6.5, 11.0)])
    quill = mask()
    line(quill, [(3.5, 11.5), (9.8, 3.8)], 0.6)
    arrows = [sn_arrow((x, y), 40, 12.0, 3.2, 1.2) for (x, y) in ((22.0, 18.0), (26.0, 24.5), (30.0, 31.0))]
    hits = mask()
    for (x, y) in ((22.0, 18.0), (26.0, 24.5), (30.0, 31.0)):
        star(hits, x + 0.6, y + 0.4, 2.0, 0.25, 15)
    layers = []
    for a in arrows:
        layers += sn_arrow_layers(A, B, *a)
    subject = stack(*[layer(m, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3), axis=(1, 1)) for m in gusts], layer(feather, mix(A, WHITE, 0.6), A, WHITE, None),
                    layer(quill, mix(A, (0, 0, 0), 0.3), mix(A, (0, 0, 0), 0.3)), *layers, layer(hits, WHITE, B))
    return finish(subject, [glow(lighter(*gusts), A, 1.6, 0.9), glow(lighter(*[a[1] for a in arrows]), B, 1.2, 0.8)], sniper_back(A, B))


def sniper_storm(A, B):
    # 울르의 폭우: 위의 납작한 잿빛 비구름 (아래가 어두움) · 구름 밑에서 비스듬히 쏟아지는 빛의 화살 다섯 (촉이 아래 · 흰 빗줄기) · 아래 금빛 과녁 · 꽂힌 자리 반짝임
    cloud = mask()
    for (x, y, r) in ((6.5, 5.5, 3.4), (12.0, 3.8, 4.0), (18.5, 4.2, 3.8), (24.5, 5.6, 3.3), (15.5, 6.6, 3.4), (9.5, 7.0, 2.8), (21.5, 7.0, 2.8)):
        disc(cloud, x, y, r)
    target = mask()
    d = ImageDraw.Draw(target)
    d.ellipse([P(3.0, 24.0), P(29.0, 31.0)], fill=255)
    d.ellipse([P(6.0, 25.5), P(26.0, 29.5)], fill=0)
    d.ellipse([P(12.0, 26.4), P(20.0, 28.6)], fill=255)
    spots = ((7.5, 26.5, 13.0), (13.0, 23.0, 10.5), (18.0, 28.0, 15.0), (23.0, 22.5, 10.0), (27.5, 26.0, 12.0))
    arrows = [sn_arrow((x, y), 104, L, 3.4, 1.2, fletch=False) for (x, y, L) in spots]
    rain = mask()
    for (x, y, L) in spots:
        a = math.radians(104)
        line(rain, [(x - math.cos(a) * (L + 0.5), y - math.sin(a) * (L + 0.5)), (x - math.cos(a) * (L + 4.0), y - math.sin(a) * (L + 4.0))], 0.6)
    layers = []
    for a in arrows:
        layers += [layer(a[0], WHITE, mix(A, WHITE, 0.35)), layer(a[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3))]
    sp = mask()
    for (x, y, _) in spots[::2]:
        star(sp, x + 0.4, y + 0.8, 2.2, 0.25, 0)
    grey = (70, 78, 84)
    subject = stack(layer(target, mix(B, WHITE, 0.25), B, None, None), layer(rain, mix(A, WHITE, 0.5), A), *layers,
                    layer(cloud, mix(grey, WHITE, 0.35), mix(grey, (0, 0, 0), 0.45), mix(grey, WHITE, 0.6), None), layer(sp, WHITE, B))
    return finish(subject, [glow(target, B, 1.4, 0.7), glow(lighter(*[a[0] for a in arrows]), A, 1.2, 0.8)], sniper_back(A, B))


def sniper_apple(A, B):
    # 사과 겨누기: 가운데 금빛 사과 (잎 · 꼭지 · 반짝임) · 둘레 네 토막 과녁 테 · 사과를 겨눈 눈금 · 오른쪽에서 꿰뚫는 화살
    apple = mask()
    disc(apple, 14.4, 17.0, 5.6)
    disc(apple, 18.4, 17.0, 5.6)
    disc(apple, 16.4, 20.0, 5.4)
    notch = mask()
    disc(notch, 16.4, 10.8, 1.8)
    apple = ImageChops.subtract(apple, notch)
    leaf = mask()
    poly(leaf, [(17.0, 11.5), (19.5, 8.2), (23.0, 8.0), (20.5, 11.2)])
    stem = mask()
    line(stem, [(16.4, 12.0), (15.6, 8.6)], 1.0)
    shine = mask()
    disc(shine, 13.2, 15.0, 1.5)
    reticle = mask()
    for q in range(4):
        a0, a1 = q * 90 + 14, (q + 1) * 90 - 14
        pts = [(16.0 + math.cos(math.radians(a)) * 13.5, 16.5 + math.sin(math.radians(a)) * 13.5) for a in range(a0, a1 + 1, 6)]
        line(reticle, pts, 1.3)
    ticks = mask()
    for (a, b) in (((16, 0.8), (16, 5.0)), ((16, 28.0), (16, 31.4)), ((0.6, 16.5), (4.8, 16.5)), ((27.2, 16.5), (31.4, 16.5))):
        line(ticks, [a, b], 1.2)
    arr = sn_arrow((9.0, 18.5), 180, 18.0, 3.4, 1.3)
    subject = stack(layer(reticle, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(ticks, WHITE, A), layer(apple, mix(B, WHITE, 0.25), mix(B, (120, 60, 0), 0.45), WHITE,
                    mix(B, (0, 0, 0), 0.45)), layer(leaf, mix(A, WHITE, 0.2), mix(A, (0, 0, 0), 0.3)), layer(stem, mix(B, (60, 30, 0), 0.6), mix(B, (60, 30, 0), 0.6)),
                    *sn_arrow_layers(A, B, *arr), layer(shine, WHITE, WHITE))
    return finish(subject, [glow(apple, B, 1.8, 0.9), glow(reticle, A, 1.2, 0.7)], sniper_back(A, B))


def sniper_ult(A, B):
    # 세 번째 화살: 오른쪽 위로 나는 거대한 넓은 잎 촉 화살 · 살대를 감아 도는 바람 띠 둘 · 살대의 금 새김 셋 (III) · 촉 끝 흰 섬광
    a = math.radians(-38)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    tip = (30.5, 4.0)

    def at(s, o=0.0):
        return (tip[0] - ux * s + px * o, tip[1] - uy * s + py * o)
    shaft = mask()
    line(shaft, [at(9.0), at(31.0)], 2.2)
    head = mask()
    poly(head, [at(0), at(5.0, 4.6), at(9.5, 5.0), at(10.5, 2.0), at(8.6, 0), at(10.5, -2.0), at(9.5, -5.0), at(5.0, -4.6)])
    fl = mask()
    for d0 in (0.0, 2.6):
        line(fl, [at(28.5 - d0), at(31.5 - d0, 3.6)], 1.2)
        line(fl, [at(28.5 - d0), at(31.5 - d0, -3.6)], 1.2)
    bands = []
    for ph in (0.0, math.pi):
        m = mask()
        pts = [at(s, (1.5 + 3.6 * (s - 10) / 18.0) * math.sin(s * 0.62 + ph)) for s in [10.0 + 0.5 * i for i in range(37)]]
        line(m, pts, 1.0)
        bands.append(m)
    marks = mask()
    for s in (13.5, 15.5, 17.5):
        line(marks, [at(s, 1.3), at(s, -1.3)], 0.7)
    sp = mask()
    star(sp, 29.0, 5.2, 4.6, 0.18, 8)
    subject = stack(layer(bands[1], mix(A, WHITE, 0.2), mix(A, (0, 0, 0), 0.2)), layer(shaft, WHITE, mix(B, WHITE, 0.3)), layer(fl, WHITE, A), layer(marks, B, mix(B, (0, 0, 0), 0.35)),
                    layer(head, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(bands[0], WHITE, A), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(shaft, head), B, 1.8, 1.0), glow(lighter(*bands), A, 1.6, 0.9)], sniper_back(A, B))


# ------------------------------------------------------------ 미르크비드(섀도 헌터) (빛: 남보라 → 은빛 — 짙은 남색 어둠숲 바탕 · 검은 화살)

SHADE_NIGHT = (5, 4, 18)
INK = (22, 18, 44)


def shadow_back(A, B):
    return backdrop(mix(A, SHADE_NIGHT, 0.86), mix((60, 52, 150), A, 0.3))


def black_arrow_layers(A, B, shaft, hd, fl):
    """검은 화살: 먹빛 살대 · 깃 (은빛 테) · 은빛 촉"""
    return [layer(shaft, mix(INK, A, 0.25), INK, mix(B, INK, 0.45), None), layer(fl, mix(INK, A, 0.35), INK, mix(B, INK, 0.3), None),
            layer(hd, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.45))]


def smoke_puffs(pts):
    m = mask()
    for (x, y, r) in pts:
        disc(m, x, y, r)
    return m


def shadowhunter_black(A, B):
    # 검은 화살: 오른쪽으로 나란히 나는 검은 화살 셋 (은빛 촉 · 검은 깃) · 뒤로 끌리는 그림자 연기 · 은빛 줄
    arrows = [sn_arrow((x, y), -12, 17.0, 3.4, 1.4) for (x, y) in ((30.0, 7.0), (27.0, 16.5), (30.5, 25.5))]
    smoke = smoke_puffs([(x - 15.0 - k * 2.6, y + 3.2 + k * 0.6, 2.2 - k * 0.35) for (x, y) in ((30.0, 7.0), (27.0, 16.5), (30.5, 25.5)) for k in range(4)])
    streak = mask()
    for (x, y) in ((30.0, 7.0), (27.0, 16.5), (30.5, 25.5)):
        line(streak, [(x - 19.0, y + 4.2), (x - 26.0, y + 5.7)], 0.6)
    layers = []
    for a in arrows:
        layers += black_arrow_layers(A, B, *a)
    subject = stack(layer(smoke, mix(A, (0, 0, 0), 0.35), mix(A, (0, 0, 0), 0.7), None, None), layer(streak, mix(B, A, 0.4), A), *layers)
    return finish(subject, [glow(lighter(*[a[1] for a in arrows]), B, 1.4, 0.9), glow(smoke, A, 1.6, 0.7)], shadow_back(A, B))


def shadowhunter_roll(A, B):
    # 그림자 구르기: 가운데 둥글게 감긴 그림자 연기 고리 (구르는 몸 — 끝이 가늘어짐) · 고리에서 오른쪽으로 튀어나가는 검은 화살 · 떠난 자리의 흐린 잔상 · 은빛 반짝임
    swirl = mask()
    pts = []
    for i in range(33):
        t = i / 32
        a = math.radians(200 + 300 * t)
        rr = 8.5 - 2.5 * t
        pts.append((13.0 + math.cos(a) * rr, 16.5 + math.sin(a) * rr))
    for i in range(len(pts) - 1):
        w = 3.6 * (1 - i / len(pts)) + 0.8
        line(swirl, [pts[i], pts[i + 1]], w)
    ghost = smoke_puffs([(4.0, 24.0, 2.6), (6.5, 26.5, 2.0), (3.0, 20.5, 1.6)])
    arr = sn_arrow((31.0, 15.0), -6, 16.0, 3.6, 1.5)
    sp = mask()
    star(sp, 13.0, 16.5, 3.2, 0.22, 20)
    subject = stack(layer(ghost, mix(A, (0, 0, 0), 0.4), mix(A, (0, 0, 0), 0.75), None, None), layer(swirl, mix(A, WHITE, 0.15), mix(A, INK, 0.5), mix(B, A, 0.4),
                    mix(A, (0, 0, 0), 0.5), axis=(1, 1)), *black_arrow_layers(A, B, *arr), layer(sp, WHITE, B))
    return finish(subject, [glow(swirl, A, 1.8, 0.8), glow(arr[1], B, 1.2, 0.9)], shadow_back(A, B))


def curse_mark(m, cx, cy, r, w=1.1, thorns=8):
    """가시 고리 (둘레 바깥 가시)"""
    ring(m, cx, cy, r, w)
    for i in range(thorns):
        a = math.radians(i * 360 / thorns + 10)
        b0 = (cx + math.cos(a - 0.2) * (r - 0.3), cy + math.sin(a - 0.2) * (r - 0.3))
        b1 = (cx + math.cos(a + 0.2) * (r - 0.3), cy + math.sin(a + 0.2) * (r - 0.3))
        tp = (cx + math.cos(a + 0.1) * (r + r * 0.45), cy + math.sin(a + 0.1) * (r + r * 0.45))
        poly(m, [b0, tp, b1])
    return m


def closed_eye(m, cx, cy, s, w=0.9):
    pts = [(cx - 3.6 * s + 7.2 * s * t, cy + 2.2 * s * math.sin(math.pi * t)) for t in [i / 12 for i in range(13)]]
    line(m, pts, w)
    for t in (0.25, 0.5, 0.75):
        x = cx - 3.6 * s + 7.2 * s * t
        y = cy + 2.2 * s * math.sin(math.pi * t)
        line(m, [(x, y), (x + (t - 0.5) * 1.6 * s, y + 1.6 * s)], 0.6)
    return m


def shadowhunter_spread(A, B):
    # 저주 번짐: 가운데 큰 저주 문양 (가시 고리 · 감긴 눈) · 문양에서 셋으로 번지는 검은 줄 · 줄 끝의 작은 문양 셋
    big = mask()
    curse_mark(big, 16.0, 15.5, 6.6, 1.3, 9)
    disc_in = mask()
    disc(disc_in, 16.0, 15.5, 5.0)
    eye = mask()
    closed_eye(eye, 16.0, 14.8, 1.0)
    veins = mask()
    for (x, y) in ((4.5, 5.0), (28.0, 6.5), (16.0, 29.0)):
        line(veins, [(16.0, 15.5), ((16.0 + x) / 2 + 1.5, (15.5 + y) / 2 - 1.0), (x, y)], 1.0)
    smalls = mask()
    for (x, y) in ((4.5, 5.0), (28.0, 6.5), (16.0, 29.0)):
        curse_mark(smalls, x, y, 2.4, 0.9, 6)
    subject = stack(layer(veins, mix(A, INK, 0.3), mix(A, INK, 0.6)), layer(disc_in, mix(A, INK, 0.55), mix(INK, (0, 0, 0), 0.3), None, None),
                    layer(big, mix(B, WHITE, 0.2), mix(B, A, 0.45), WHITE, mix(A, (0, 0, 0), 0.3)), layer(eye, WHITE, B), layer(smalls, mix(B, A, 0.25), mix(A, B, 0.2)))
    return finish(subject, [glow(big, A, 1.8, 0.9), glow(lighter(smalls, veins), A, 1.4, 0.7), glow(eye, B, 1.0, 0.9)], shadow_back(A, B))


def shadowhunter_moonless(A, B):
    # 그믐밤: 하늘의 가는 그믐달 (은빛 테 — 가려진 쪽은 먹빛) · 아래 어둠숲의 뾰족한 나무 그림자 · 숲 속 은빛 눈 둘 · 별 둘
    moon = mask()
    disc(moon, 19.0, 10.0, 7.0)
    cover = mask()
    disc(cover, 16.6, 9.0, 6.6)
    rim = ImageChops.subtract(moon, cover)
    shadow = ImageChops.multiply(moon, cover)
    trees = mask()
    for (x, h, w) in ((3.0, 12.0, 3.6), (9.0, 15.0, 4.2), (15.5, 11.0, 3.6), (22.0, 14.0, 4.0), (28.5, 12.5, 3.6)):
        poly(trees, [(x - w, 32.0), (x, 32.0 - h), (x + w, 32.0)])
        poly(trees, [(x - w * 0.8, 32.0 - h * 0.45), (x, 32.0 - h * 1.08), (x + w * 0.8, 32.0 - h * 0.45)])
    eyes = mask()
    for x in (12.0, 15.0):
        poly(eyes, [(x - 1.3, 25.0), (x + 1.3, 24.3), (x + 0.9, 25.5), (x - 1.0, 25.8)])
    st = mask()
    star(st, 6.0, 6.0, 2.2, 0.22, 0)
    star(st, 27.5, 19.5, 1.8, 0.22, 0)
    subject = stack(layer(shadow, mix(A, INK, 0.5), mix(INK, (0, 0, 0), 0.4), None, None), layer(rim, WHITE, B, WHITE, mix(B, A, 0.4)),
                    layer(trees, mix(INK, A, 0.3), mix(INK, (0, 0, 0), 0.4), mix(A, INK, 0.3), None), layer(eyes, WHITE, B), layer(st, WHITE, B))
    return finish(subject, [glow(rim, B, 1.8, 0.9), glow(eyes, B, 0.8, 1.0), glow(trees, A, 1.4, 0.5)], shadow_back(A, B))


def shadowhunter_ult(A, B):
    # 어둠숲의 사냥: 위에서 곧게 내리꽂는 거대한 검은 화살 (은빛 촉) · 양옆에서 내리는 작은 검은 화살 둘 · 아래 가시덤불 고리 (뾰족한 가시) · 꽂힌 자리 은빛 섬광
    thorns = mask()
    for (x, h, lean) in ((3.0, 9.0, -1.5), (8.0, 7.0, 1.0), (24.0, 7.5, -1.0), (29.0, 9.5, 1.5), (12.0, 5.0, -0.5), (20.0, 5.5, 0.5)):
        line(thorns, [(x, 32.0), (x + lean, 32.0 - h)], 1.6)
        line(thorns, [(x + lean * 0.5, 32.0 - h * 0.55), (x + lean * 0.5 + (2.0 if lean >= 0 else -2.0), 32.0 - h * 0.75)], 0.9)
    big = sn_arrow((16.0, 27.0), 90, 25.0, 6.0, 2.4)
    s1 = sn_arrow((7.0, 21.0), 96, 12.0, 3.0, 1.1, fletch=False)
    s2 = sn_arrow((25.5, 20.0), 84, 12.0, 3.0, 1.1, fletch=False)
    flash = mask()
    star(flash, 16.0, 27.5, 5.2, 0.2, 0)
    smoke = smoke_puffs([(10.0, 29.0, 2.6), (22.0, 29.0, 2.6), (16.0, 30.5, 3.0)])
    subject = stack(layer(smoke, mix(A, (0, 0, 0), 0.3), mix(A, (0, 0, 0), 0.7), None, None), layer(thorns, mix(INK, A, 0.35), INK, mix(B, INK, 0.45), None),
                    *black_arrow_layers(A, B, *s1), *black_arrow_layers(A, B, *s2), *black_arrow_layers(A, B, *big), layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(big[1], flash), B, 1.6, 1.0), glow(thorns, A, 1.4, 0.7), glow(lighter(big[0], s1[0], s2[0]), A, 1.4, 0.6)], shadow_back(A, B))


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
    "arcmage_bolts": arcmage_bolts,
    "arcmage_phase": arcmage_phase,
    "arcmage_orb": arcmage_orb,
    "arcmage_song": arcmage_song,
    "arcmage_ult": arcmage_ult,
    "necromancer_spear": necromancer_spear,
    "necromancer_swap": necromancer_swap,
    "necromancer_burst": necromancer_burst,
    "necromancer_raise": necromancer_raise,
    "necromancer_ult": necromancer_ult,
    "sniper_pierce": sniper_pierce,
    "sniper_gale": sniper_gale,
    "sniper_storm": sniper_storm,
    "sniper_apple": sniper_apple,
    "sniper_ult": sniper_ult,
    "shadowhunter_black": shadowhunter_black,
    "shadowhunter_roll": shadowhunter_roll,
    "shadowhunter_spread": shadowhunter_spread,
    "shadowhunter_moonless": shadowhunter_moonless,
    "shadowhunter_ult": shadowhunter_ult,
}
