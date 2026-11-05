#!/usr/bin/env python3
"""0.51.0 2차 직업 스킬 아이콘 — 검사 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트) · 전사 각성 뵈드바르(워로드) · 반전 스타르카드(글래디에이터) (재설계안 2판) ·
마검사 각성 뵐룬드(룬 스미스) · 반전 스키르니르(소드 콜러) (재설계안 2판) · 매지션 각성 오드레리르(아크메이지) · 반전 나글파르(네크로맨서) ·
궁수 각성 에길(스나이퍼) · 반전 미르크비드(섀도 헌터) · 암살자 각성 우트가르드(트릭스터) · 반전 료스알프(블레이드 댄서) (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음 — 룬 글자는 게임 그림 rfx_jglyph 와 같은 꼴, 룬 스미스의 모루 · 쇳물 · 숫양은 rfx_janvil · jmolten · jram 과 같은 꼴).
색: 소드마스터 = 금빛 (첫째) · 은빛 (둘째), 블러드 나이트 = 짙은 피 (첫째) · 밝은 피 (둘째), 워로드 = 주황 (첫째) · 갈색 (둘째),
글래디에이터 = 진홍 (첫째) · 모래 금빛 (둘째 — 결투장 황혼 바탕 · 말뚝은 나무빛), 룬 스미스 = 보라 (첫째) · 금빛 룬 (둘째 — 대장간 불빛 바탕 · 쇳물 주황),
소드 콜러 = 청록 (첫째) · 흰 청록 (둘째 — 짙은 청록 밤 바탕), 아크메이지 = 별빛 파랑 (첫째) · 금빛 (둘째 — 밤하늘 자주 바탕),
네크로맨서 = 독초록 (첫째) · 뼈색 (둘째 — 짙은 녹색 안개 바탕), 스나이퍼 = 밝은 연두 (첫째) · 금빛 흰 섬광 (둘째 — 짙은 숲 바탕),
섀도 헌터 = 남보라 (첫째) · 은빛 (둘째 — 짙은 남색 바탕 · 검은 화살은 먹빛 살대 · 은빛 촉), 트릭스터 = 자홍 (첫째) · 검보라 (둘째 — 짙은 보라 바탕 · 환영은 청록 테),
블레이드 댄서 = 흰 금빛 (첫째) · 연분홍 (둘째 — 연보라 황혼 바탕) — jobs.json advance."""
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
from job_skill_icons_three import blossom as _blossom, hood as _hood, petal as _petal  # noqa: E402 — 암살자 꽃 · 꽃잎 · 두건 (0.51.0 암살자 2차)

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


# ------------------------------------------------------------ 시구르드(소드마스터) (빛: 금빛 → 은빛) — 0.51.0 재설계안 2판

FIRE_C = (255, 112, 30)
FIRE_HI = (255, 232, 140)
LACQUER = (70, 22, 20)


def swordmaster_draw(A, B):
    # 칼집 섬광: 아래왼쪽의 검붉은 칼집 (금빛 어귀) 에서 막 뽑혀 위오른쪽으로 뻗은 칼 · 어귀의 섬광 · 뒤로 엇갈린 가는 칼자국 다발
    cuts = mask()
    for a, b, w in (((3.0, 9.0), (21.0, 3.5), 0.9), ((5.0, 15.5), (28.0, 9.0), 1.0), ((14.0, 27.5), (30.0, 14.5), 0.9), ((2.5, 20.0), (13.5, 2.5), 0.8),
                    ((19.0, 30.0), (29.0, 22.0), 0.8)):
        line(cuts, [a, b], w)
    sheath = mask()
    line(sheath, [(2.5, 29.5), (12.6, 19.4)], 3.4)
    fit = mask()
    disc(fit, 12.8, 19.2, 2.1)
    disc(fit, 4.4, 27.6, 1.5)
    sw = gold_sword((30.0, 2.0), (11.0, 21.0), blade=2.8, guard=6.6)
    sp = mask()
    star(sp, 13.4, 18.6, 6.0, 0.16, 45)
    star(sp, 25.0, 6.0, 2.4)
    subject = stack(layer(cuts, WHITE, mix(A, WHITE, 0.4), None, None, axis=(1, -1)), layer(sheath, mix(LACQUER, A, 0.25), LACQUER, mix(LACQUER, WHITE, 0.3),
                    mix(LACQUER, (0, 0, 0), 0.4)), layer(fit, GOLD_HI, GOLD, WHITE, GOLD_LO), *sw, layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(cuts, sp), A, 1.6, 1.0)], backdrop(mix(A, NIGHT, 0.86), mix(A, (60, 40, 10), 0.45)))


def swordmaster_parry(A, B):
    # 받아넘기기: 곧게 세운 칼 · 왼쪽 위에서 날아든 검붉은 공격이 칼날에 막혀 튀는 큰 불꽃 · 오른쪽으로 되돌아 휘감는 은빛 받아치기 호
    hit = mask()
    crescent(hit, -2.0, 18.0, 17.0, 2.6, -70, -24)
    counter = mask()
    crescent(counter, 15.0, 16.0, 13.5, 2.8, -50, 70)
    sw = gold_sword((14.0, 2.0), (14.0, 29.0), blade=3.0, guard=8.6)
    sp = mask()
    star(sp, 14.0, 10.6, 7.0, 0.15, 0)
    star(sp, 14.0, 10.6, 4.0, 0.2, 45)
    sparks = mask()
    for cx, cy, r in ((7.0, 6.0, 1.0), (21.5, 5.0, 1.0), (8.5, 15.0, 0.8), (20.5, 14.0, 0.9)):
        disc(sparks, cx, cy, r)
    subject = stack(layer(hit, mix((150, 30, 30), WHITE, 0.2), (110, 20, 24), None, None), layer(counter, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.4), axis=(0, 1)),
                    *sw, layer(sparks, GOLD_HI, A), layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(sp, sparks), A, 1.8, 1.1), glow(counter, B, 1.2, 0.8)], backdrop(mix(A, NIGHT, 0.86), mix(B, (20, 30, 60), 0.4)))


def swordmaster_ring(A, B):
    # 불의 울타리: 둘레에 솟은 불꽃 울타리 (불꽃 혀 열둘) · 안을 가로지른 금빛 별 (가로지르기 다섯) · 가운데 반짝임
    cx, cy, R = 16.0, 17.0, 12.6
    flames, cores = mask(), mask()
    for i in range(12):
        a = math.radians(i * 30 + 15)
        x, y = cx + math.cos(a) * R, cy + math.sin(a) * R * 0.86
        drop(flames, x, y, 1.9, -90)
        drop(cores, x, y + 0.5, 0.9, -90)
    pts = [(cx + math.cos(math.radians(-90 + 72 * i)) * 8.6, cy + math.sin(math.radians(-90 + 72 * i)) * 8.6) for i in range(5)]
    starm = mask()
    for i in range(5):
        line(starm, [pts[i], pts[(i + 2) % 5]], 1.3)
    core = mask()
    for i in range(5):
        line(core, [pts[i], pts[(i + 2) % 5]], 0.45)
    sp = mask()
    star(sp, cx, cy, 3.6, 0.2)
    subject = stack(layer(flames, FIRE_HI, FIRE_C, WHITE, mix(FIRE_C, (0, 0, 0), 0.35)), layer(cores, WHITE, FIRE_HI), layer(starm, GOLD_HI, A, WHITE, GOLD_LO),
                    layer(core, WHITE, WHITE), layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(flames, FIRE_C, 1.8, 1.0), glow(starm, A, 1.3, 0.9)], backdrop(mix(FIRE_C, NIGHT, 0.85), mix(A, (90, 40, 10), 0.45)))


def swordmaster_blood(A, B):
    # 용의 피: 금빛 용의 눈 (아몬드 꼴 · 세로로 갈라진 눈동자) · 눈 위로 솟는 금빛 불길 · 아래로 흐르는 금빛 핏방울
    eye = mask()
    up = [(4.0 + i * 1.2, 17.0 - math.sin(math.pi * i / 20) * 8.4) for i in range(21)]
    lo = [(28.0 - i * 1.2, 17.0 + math.sin(math.pi * i / 20) * 6.2) for i in range(21)]
    poly(eye, up + lo)
    iris = mask()
    disc(iris, 16.0, 16.4, 5.6)
    pupil = mask()
    poly(pupil, [(16.0, 10.4), (17.3, 16.4), (16.0, 22.4), (14.7, 16.4)])
    flames = mask()
    for x, y, r in ((8.0, 8.6, 1.7), (12.5, 6.0, 2.0), (16.0, 4.8, 2.2), (19.5, 6.0, 2.0), (24.0, 8.6, 1.7)):
        drop(flames, x, y, r, -90)
    drops = mask()
    drop(drops, 11.0, 27.4, 1.4, -90)
    drop(drops, 21.0, 28.4, 1.2, -90)
    sp = mask()
    star(sp, 13.6, 13.8, 2.0, 0.22)
    subject = stack(layer(flames, FIRE_HI, mix(A, FIRE_C, 0.4), WHITE, None), layer(eye, mix(A, (40, 20, 6), 0.55), mix(A, (20, 10, 4), 0.75), GOLD_HI, None),
                    layer(iris, GOLD_HI, mix(A, FIRE_C, 0.5), WHITE, GOLD_LO), layer(pupil, (24, 10, 6), (10, 4, 2)),
                    layer(drops, GOLD_HI, A, WHITE, GOLD_LO), layer(sp, WHITE, WHITE))
    return finish(subject, [glow(lighter(iris, flames), A, 2.0, 1.0)], backdrop(mix(A, NIGHT, 0.84), mix(A, (90, 40, 10), 0.45)))


def swordmaster_ult(A, B):
    # 다시 벼린 칼: 아래의 모루 · 그 위에 곧게 선 빛나는 칼 (벼림) · 튀는 불티 · 뒤를 크게 가로지르는 은빛 검풍
    wind = mask()
    crescent(wind, 16.0, 30.0, 25.0, 3.0, 198, 342)
    anvil = mask()
    poly(anvil, [(7.0, 22.0), (26.0, 22.0), (24.0, 25.0), (19.0, 25.0), (19.0, 27.0), (23.0, 30.0), (9.0, 30.0), (13.0, 27.0), (13.0, 25.0), (8.0, 25.0)])
    poly(anvil, [(2.0, 22.0), (7.5, 22.0), (7.5, 24.4)])
    sw = gold_sword((16.0, 1.4), (16.0, 21.6), blade=3.4, guard=9.0)
    sp = mask()
    for cx, cy, r in ((7.0, 15.0, 2.4), (25.0, 12.5, 2.6), (22.0, 19.5, 1.8), (10.5, 8.0, 1.6)):
        star(sp, cx, cy, r, 0.22)
    hot = mask()
    disc(hot, 16.0, 21.8, 3.0)
    subject = stack(layer(wind, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.35), axis=(1, 0)), layer(anvil, (120, 126, 140), (52, 56, 70), (170, 176, 190), (24, 26, 34)),
                    layer(hot, FIRE_HI, FIRE_C), *sw, layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(sp, hot), A, 1.8, 1.0), glow(wind, B, 1.2, 0.8)], backdrop(mix(A, NIGHT, 0.84), mix(A, (70, 50, 20), 0.4)))


# ------------------------------------------------------------ 다인슬레이프(블러드 나이트) (빛: 짙은 피 → 밝은 피) — 0.51.0 재설계안 2판

def bloodknight_cleave(A, B):
    # 핏빛 대검: 아래왼쪽을 가운데로 한 거대한 핏빛 휩쓸기 (바깥 끝 = 칼끝 자리가 가장 밝음) · 넓은 검붉은 대검 · 아래 세 핏방울 (세 번)
    sweep = mask()
    crescent(sweep, 3.0, 29.0, 26.0, 5.0, -90, 0)
    tip = mask()
    crescent(tip, 3.0, 29.0, 26.0, 1.6, -76, -14, taper=True)
    sw = blood_sword((27.5, 4.5), (6.0, 26.0), A, B, blade=4.0, guard=8.6)
    pips = mask()
    for x in (19.0, 23.5, 28.0):
        drop(pips, x, 28.6, 1.3, -90)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(sweep, mix(B, WHITE, 0.3), A, mix(B, WHITE, 0.6), dark, axis=(0.7, 0.7)), layer(tip, WHITE, mix(B, WHITE, 0.6)), *sw,
                    layer(pips, mix(B, WHITE, 0.25), A, mix(B, WHITE, 0.6), dark))
    return finish(subject, [glow(lighter(sweep, tip), B, 1.8, 1.0)], backdrop(mix(A, DUSK, 0.82), mix(A, (70, 12, 16), 0.4)))


def bloodknight_leap(A, B):
    # 피의 돌진: 아래왼쪽에서 솟았다 떨어지는 점선 궤적 · 끝에 거꾸로 내리박힌 검붉은 칼 · 박힌 자리에서 크게 튀는 피 · 충격 반짝임
    arc = mask()
    for i in range(7):
        a0 = math.radians(192 + i * 21)
        a1 = math.radians(192 + i * 21 + 9)
        line(arc, [(14.0 + math.cos(a0) * 11.0, 22.0 + math.sin(a0) * 13.0), (14.0 + math.cos(a1) * 11.0, 22.0 + math.sin(a1) * 13.0)], 1.3)
    sw = blood_sword((23.0, 28.0), (23.0, 6.0), A, B, blade=3.2, guard=8.0)
    splat = mask()
    pts = []
    for i in range(16):
        a = math.radians(i * 22.5 + 11)
        r = 7.2 if i % 2 == 0 else 3.6
        pts.append((23.0 + math.cos(a) * r, 27.4 + math.sin(a) * r * 0.62))
    poly(splat, pts)
    drops = mask()
    drop(drops, 14.5, 25.0, 1.2, 20)
    drop(drops, 30.0, 22.5, 1.1, 150)
    sp = mask()
    star(sp, 23.0, 27.0, 4.2, 0.2)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(arc, mix(B, WHITE, 0.35), B, None, None, axis=(1, 0)), *sw, layer(splat, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark),
                    layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark), layer(sp, WHITE, mix(B, WHITE, 0.6)))
    return finish(subject, [glow(lighter(arc, splat), B, 1.6, 0.95)], backdrop(mix(A, DUSK, 0.84), mix(A, (60, 10, 14), 0.35)))


def bloodknight_chains(A, B):
    # 피의 사슬: 가운데 핏빛 덩이에서 세 쪽으로 뻗은 굵은 사슬 고리 (셋씩) · 끝에서 안쪽을 가리키는 갈고리 화살촉 (끌어당김)
    links = mask()
    ends = ((4.5, 5.5), (28.0, 6.5), (16.0, 30.0))
    for ex, ey in ends:
        for k, t in enumerate((0.36, 0.58, 0.8)):
            x, y = 16.0 + (ex - 16.0) * t, 16.0 + (ey - 16.0) * t
            ring(links, x, y, 2.3 if k % 2 == 0 else 1.9, 0.95)
    hooks = mask()
    for ex, ey in ends:
        dx, dy = 16.0 - ex, 16.0 - ey
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        px, py = -uy, ux
        bx, by = ex - ux * 1.2, ey - uy * 1.2
        poly(hooks, [(bx + ux * 4.2, by + uy * 4.2), (bx + px * 3.0, by + py * 3.0), (bx + ux * 1.4, by + uy * 1.4), (bx - px * 3.0, by - py * 3.0)])
    heart = mask()
    disc(heart, 16.0, 16.0, 4.6)
    core = mask()
    disc(core, 15.2, 15.2, 1.8)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(links, mix(B, WHITE, 0.35), A, mix(B, WHITE, 0.65), dark), layer(hooks, mix(B, WHITE, 0.5), B, WHITE, dark),
                    layer(heart, mix(B, WHITE, 0.3), A, mix(B, WHITE, 0.7), dark), layer(core, mix(B, WHITE, 0.7), mix(B, WHITE, 0.45)))
    return finish(subject, [glow(lighter(links, heart), B, 1.7, 1.0)], backdrop(mix(A, DUSK, 0.82), mix(A, (70, 12, 16), 0.4)))


def bloodknight_frenzy(A, B):
    # 광란: 위오른쪽에서 아래왼쪽으로 그은 세 갈래 핏빛 할퀸 자국 · 뒤의 붉게 이글거리는 두 눈 · 튀는 핏방울
    claws = []
    for off in (-5.0, 0.0, 5.0):
        m = mask()
        crescent(m, 34.0 + off, 34.0 - off * 0.2, 27.0, 2.6, 196, 252)
        claws.append(m)
    eyes = mask()
    for cx in (10.0, 22.0):
        poly(eyes, [(cx - 3.4, 7.0), (cx, 5.0), (cx + 3.4, 7.4), (cx, 9.0)])
    drops = mask()
    drop(drops, 27.5, 26.0, 1.4, -135)
    drop(drops, 5.0, 27.0, 1.2, -60)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(eyes, (255, 220, 200), (255, 60, 50), WHITE, None),
                    *[layer(m, mix(B, WHITE, 0.4), B, mix(B, WHITE, 0.7), dark, axis=(-0.7, 0.7)) for m in claws],
                    layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark))
    return finish(subject, [glow(lighter(*claws), B, 1.6, 0.95), glow(eyes, (255, 40, 30), 1.4, 1.2)], backdrop(mix(A, DUSK, 0.82), mix(A, (80, 12, 16), 0.45)))


def bloodknight_ult(A, B):
    # 칼을 뽑다: 뒤의 핏빛 달 · 아래 검은 칼집에서 반쯤 뽑혀 나온 검붉은 칼 (자루가 위) · 칼집 어귀에서 뿜어지는 핏방울
    moon = mask()
    disc(moon, 16.0, 12.0, 10.0)
    rim = mask()
    ring(rim, 16.0, 12.0, 10.0, 1.2)
    sw = blood_sword((16.0, 27.0), (16.0, 3.2), A, B, blade=3.4, guard=9.4)
    sheath = mask()
    line(sheath, [(16.0, 19.4), (16.0, 30.6)], 4.2)
    fit = mask()
    poly(fit, [(13.2, 18.6), (18.8, 18.6), (18.8, 20.4), (13.2, 20.4)])
    drops = mask()
    for cx, cy, r, a in ((9.0, 18.0, 1.3, 200), (23.0, 17.5, 1.3, -20), (6.0, 23.5, 1.1, 170), (26.0, 23.0, 1.1, 10), (11.5, 14.0, 1.0, 230)):
        drop(drops, cx, cy, r, a + 180)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(moon, mix(A, B, 0.35), mix(A, (0, 0, 0), 0.35), None, None), layer(rim, mix(B, WHITE, 0.4), B), *sw,
                    layer(sheath, (46, 34, 40), (14, 10, 12), (90, 70, 80), (6, 4, 6)), layer(fit, (150, 140, 146), (60, 54, 58), WHITE, None),
                    layer(drops, mix(B, WHITE, 0.2), A, mix(B, WHITE, 0.55), dark))
    return finish(subject, [glow(lighter(rim, drops), B, 1.8, 1.0)], backdrop(mix(A, DUSK, 0.8), mix(A, (90, 12, 16), 0.45)))


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


ROCK = (92, 70, 52)
ROCK_HI = (150, 122, 96)
ROCK_LO = (40, 28, 20)


def rock_spike(m, bx, base, h, lean, w):
    """모난 돌창 (게임의 rfx_jspike 와 같은 꼴 — 밑동 bx · 높이 h · 끝 기울기 lean · 밑 너비 w)"""
    poly(m, [(bx - w / 2, base), (bx - w * 0.47 + lean * 0.22, base - h * 0.4), (bx + lean * 0.86 - w * 0.08, base - h * 0.88), (bx + lean, base - h),
             (bx + lean * 0.9 + w * 0.1, base - h * 0.82), (bx + w * 0.44 + lean * 0.3, base - h * 0.46), (bx + w / 2, base)])


def ribbon(m, cx, cy, rx, ry, a0, a1, w0, w1, steps=40):
    """누운 타원을 따라 굵기가 w0 → w1 로 바뀌는 띠 (휘두른 궤적 — 각도는 화면 기준 · 90° = 앞쪽 아래)"""
    outer, inner = [], []
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0 + (a1 - a0) * t)
        w = w0 + (w1 - w0) * t
        k = ry / rx
        outer.append((cx + (rx + w) * math.cos(a), cy + (ry + w * k) * math.sin(a)))
        inner.append((cx + (rx - w) * math.cos(a), cy + (ry - w * k) * math.sin(a)))
    poly(m, outer + inner[::-1])


def ground(y, x0=1, x1=31, y1=31):
    m = mask()
    poly(m, [(x0, y), (x1, y), (x1, y1), (x0, y1)])
    return m


def ground_layer(m, B):
    return layer(m, mix(B, (40, 22, 10), 0.45), mix(B, (10, 5, 2), 0.75))


def rock_layer(m, A):
    return layer(m, ROCK_HI, ROCK, mix(A, WHITE, 0.45), ROCK_LO, axis=(0.7, 0.7))


def warlord_rend(A, B):
    # 대지 가르기: 앞으로 길게 뻗은 갈라진 땅 (가까울수록 넓음 · 지평선에서 한 점) · 금 속의 녹은 빛 · 갈라짐 양옆에서 바깥으로 기울어 솟는 돌창 (가까운 것이 큼) · 튀는 돌
    gnd = ground(10.6)
    rift = mask()
    poly(rift, [(9.0, 31), (23.0, 31), (20.6, 27.0), (21.2, 23.6), (18.8, 20.2), (19.0, 17.0), (17.0, 14.0), (16.6, 11.2), (15.6, 11.2), (15.2, 13.8),
                (13.2, 16.8), (13.6, 20.0), (11.2, 23.4), (11.8, 27.0)])
    lava = mask()
    poly(lava, [(12.0, 31), (20.0, 31), (18.6, 27.0), (19.2, 23.8), (17.2, 20.4), (17.4, 17.2), (16.3, 14.2), (16.1, 12.0), (15.9, 14.2),
                (14.8, 17.0), (15.0, 20.2), (13.2, 23.6), (13.6, 27.0)])
    side = mask()
    for pts in (((10.0, 21.6), (5.6, 22.6), (1.6, 21.8)), ((21.6, 25.6), (26.0, 27.4), (30.4, 26.6)), ((14.0, 15.6), (10.4, 14.6)), ((18.6, 18.0), (22.6, 17.0))):
        line(side, list(pts), 0.6)
    spikes = mask()
    rock_spike(spikes, 6.0, 31.0, 18.0, -2.6, 7.6)
    rock_spike(spikes, 25.6, 28.6, 14.0, 2.4, 6.4)
    rock_spike(spikes, 11.8, 19.6, 8.4, -1.4, 4.0)
    rock_spike(spikes, 20.4, 16.6, 6.0, 1.0, 3.0)
    cracks = mask()
    line(cracks, [(6.0, 29.6), (5.4, 24.0), (6.4, 19.0)], 0.8)
    line(cracks, [(25.6, 27.6), (26.4, 22.0), (26.0, 18.6)], 0.7)
    line(cracks, [(11.8, 19.0), (11.4, 15.0)], 0.6)
    rocks = mask()
    for x, y, r in ((3.6, 6.6, 1.1), (28.0, 8.0, 1.2), (21.6, 3.6, 0.8), (10.4, 3.0, 0.8), (16.0, 6.4, 0.6)):
        disc(rocks, x, y, r)
    dark = mix(B, (0, 0, 0), 0.7)
    core = mask()
    line(core, [(16.0, 31.0), (16.2, 27.0), (16.6, 23.8), (16.0, 20.4), (16.3, 17.2), (16.1, 13.6)], 0.5)
    subject = stack(layer(gnd, mix(B, (0, 0, 0), 0.82), mix(B, (0, 0, 0), 0.52)), layer(side, mix(A, WHITE, 0.2), A), layer(rift, (26, 12, 6), dark),
                    layer(lava, mix(A, WHITE, 0.3), mix(A, (0, 0, 0), 0.1), None, None, axis=(0.0, -1.0)), layer(core, WHITE, mix(A, WHITE, 0.6)),
                    rock_layer(spikes, A), layer(cracks, mix(A, WHITE, 0.5), A),
                    layer(rocks, ROCK_HI, ROCK, None, ROCK_LO))
    return finish(subject, [glow(lighter(lava, side, cracks, core), A, 1.8, 1.0)], warlord_back(A, B))


def warlord_slumber(A, B):
    # 동면: 돌창 굴 속에 웅크려 잠든 곰 (옆모습 — 둥근 등 · 왼쪽 아래로 묻은 머리 · 귀 · 주둥이 · 감은 눈) · 몸에 스며드는 주황 기운 (분노가 쌓임) · 위의 Z 숨
    den = mask()
    for bx, h, lean, w in ((3.2, 16.0, -1.2, 5.0), (8.0, 20.0, -0.6, 4.6), (24.2, 20.0, 0.6, 4.6), (29.0, 16.0, 1.2, 5.0)):
        rock_spike(den, bx, 28.6, h, lean, w)
    cave = mask()
    disc(cave, 16.0, 26.0, 12.0)
    gnd = ground(27.6)
    body = mask()
    disc(body, 18.6, 21.4, 7.4)
    poly(body, [(11.2, 21.4), (26.0, 21.4), (26.0, 28.2), (11.2, 28.2)])
    head = mask()
    disc(head, 10.6, 23.4, 4.0)
    poly(head, [(7.4, 22.0), (4.0, 23.4), (3.6, 25.6), (7.6, 27.0)])
    disc(head, 10.0, 19.4, 1.5)
    disc(head, 12.8, 19.6, 1.3)
    seam = mask()
    crescent(seam, 10.6, 23.4, 4.6, 0.8, -80, 60, taper=False)
    eye = mask()
    line(eye, [(8.4, 22.8), (9.6, 23.4), (10.8, 22.8)], 0.6)
    nose = mask()
    disc(nose, 3.8, 24.2, 0.9)
    fur = mask()
    for x0, y0 in ((15.4, 15.6), (19.0, 14.4), (22.6, 15.6)):
        line(fur, [(x0, y0), (x0 + 1.0, y0 + 2.0)], 0.6)
    aura = mask()
    for x, y in ((19.0, 9.6), (24.0, 12.0), (14.0, 11.0)):
        poly(aura, [(x, y - 1.6), (x + 0.7, y), (x, y + 1.6), (x - 0.7, y)])
    zz = mask()
    line(zz, [(24.6, 3.0), (28.2, 3.0), (24.6, 6.6), (28.2, 6.6)], 0.8)
    line(zz, [(20.6, 4.0), (22.6, 4.0), (20.6, 6.0), (22.6, 6.0)], 0.6)
    dark = mix(B, (0, 0, 0), 0.55)
    fur_c = mix(B, (0, 0, 0), 0.2)
    subject = stack(layer(cave, mix(B, (0, 0, 0), 0.62), mix(B, (0, 0, 0), 0.8)), ground_layer(gnd, B), rock_layer(den, A),
                    layer(body, mix(fur_c, WHITE, 0.3), fur_c, mix(A, WHITE, 0.3), dark, axis=(0.3, 0.95)), layer(fur, dark, dark),
                    layer(head, mix(fur_c, WHITE, 0.42), mix(fur_c, WHITE, 0.12), mix(A, WHITE, 0.4), dark), layer(seam, dark, dark), layer(eye, dark, dark),
                    layer(nose, (20, 12, 8), (20, 12, 8)), layer(aura, WHITE, A), layer(zz, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(body, A, 1.6, 0.55), glow(lighter(aura, zz), A, 1.2, 0.9)], warlord_back(A, B))


def warlord_hug(A, B):
    # 곰의 포옹: 위에서 으르렁대는 곰 얼굴 · 양옆 아래에서 가운데로 뻗어 오는 굵은 앞발 둘 (팔 · 발 · 발가락 셋 · 안쪽으로 뻗은 흰 발톱) · 바깥의 휩쓰는 바람 줄 · 가운데 부딪힌 섬광
    head, mouth_m, teeth, eyes = bear_face(16.0, 9.6, 0.72, 0.8)
    arms = mask()
    pads = mask()
    claws = mask()
    for sd in (-1, 1):
        px, py = 16.0 + sd * 6.6, 21.4
        poly(arms, [(16.0 + sd * 15.6, 31.0), (16.0 + sd * 9.0, 31.0), (px - sd * 1.4, py + 2.6), (px + sd * 2.0, py - 2.6)])
        disc(arms, px, py, 3.6)
        for dx, dy in ((-2.4, -2.6), (-3.2, 0.0), (-2.4, 2.6)):
            tx, ty = px + sd * dx, py + dy
            disc(arms, tx, ty, 1.3)
            poly(claws, [(tx - sd * 0.6, ty - 0.7), (tx - sd * 0.6, ty + 0.6), (tx - sd * 3.2, ty + 0.3)])
        disc(pads, px + sd * 0.4, py + 0.2, 1.4)
    sweep = mask()
    for sd in (-1, 1):
        for k, (y, l) in enumerate(((16.4, 3.4), (19.0, 4.4), (21.6, 3.4))):
            x = 16.0 + sd * (11.2 + k % 2 * 0.8)
            poly(sweep, [(x, y - 0.45), (x + sd * l, y), (x, y + 0.45)])
    clash = mask()
    star(clash, 16.0, 21.4, 4.4, 0.16, 0)
    dark = mix(B, (0, 0, 0), 0.5)
    fur_c = mix(B, (0, 0, 0), 0.15)
    subject = stack(layer(sweep, mix(A, WHITE, 0.45), A), layer(head, mix(fur_c, WHITE, 0.25), fur_c, mix(A, WHITE, 0.3), dark, axis=(0.5, 0.8)),
                    layer(mouth_m, mix(dark, (0, 0, 0), 0.3), dark), layer(teeth, WHITE, mix(A, WHITE, 0.5)), layer(eyes, WHITE, A),
                    layer(arms, mix(fur_c, WHITE, 0.3), fur_c, mix(A, WHITE, 0.35), dark, axis=(0.4, 0.9)), layer(pads, mix(dark, (0, 0, 0), 0.2), dark),
                    layer(claws, WHITE, mix(A, WHITE, 0.4)), layer(clash, WHITE, A))
    return finish(subject, [glow(lighter(clash, sweep, eyes), A, 1.8, 1.0), glow(head, A, 1.3, 0.4)], warlord_back(A, B))


def warlord_titan(A, B):
    # 거인의 무게: 크게 내리디딘 쇠 장화 (옆모습 · 발끝 오른쪽 · 정강이 판 · 굵은 밑창) · 위에서 내리누르는 무게 줄 · 발밑에서 부채꼴로 갈라지는 땅 · 양옆 흙먼지
    boot = mask()
    poly(boot, [(10.6, 2.6), (19.0, 2.6), (19.4, 14.0), (25.4, 15.6), (28.2, 18.0), (28.6, 22.4), (8.4, 22.4), (8.2, 17.4), (10.6, 15.0)])
    plates = mask()
    line(plates, [(10.8, 6.6), (19.0, 6.6)], 0.9)
    line(plates, [(10.8, 11.0), (19.2, 11.0)], 0.9)
    line(plates, [(19.6, 15.4), (21.0, 22.0)], 0.7)
    sole = mask()
    poly(sole, [(7.6, 21.6), (29.2, 21.6), (29.2, 24.0), (7.6, 24.0)])
    weight = mask()
    for x, y0, y1 in ((4.6, 2.0, 13.0), (6.8, 6.0, 15.0), (23.4, 2.0, 11.0), (26.2, 5.0, 13.0)):
        poly(weight, [(x - 0.55, y0), (x + 0.55, y0), (x, y1)])
    gnd = ground(23.6)
    cracks = mask()
    line(cracks, [(18.0, 24.0), (18.6, 27.4), (17.6, 31.0)], 1.0)
    line(cracks, [(12.0, 24.0), (8.0, 26.6), (3.0, 30.0)], 0.9)
    line(cracks, [(24.0, 24.0), (27.4, 26.6), (31.0, 28.6)], 0.9)
    dust = mask()
    for x, y, r in ((5.0, 21.4, 2.2), (2.6, 19.4, 1.4), (30.0, 21.0, 1.8), (31.0, 18.6, 1.0)):
        disc(dust, x, y, r)
    shock = mask()
    pts = [(18.0 + math.cos(math.radians(a)) * 15.0, 25.0 + math.sin(math.radians(a)) * 3.6) for a in range(0, 181, 6)]
    line(shock, pts, 0.9)
    iron = (120, 112, 104)
    subject = stack(ground_layer(gnd, B), layer(shock, mix(A, WHITE, 0.4), A), layer(cracks, WHITE, A), layer(dust, mix(ROCK_HI, WHITE, 0.2), ROCK, None, ROCK_LO),
                    layer(weight, WHITE, mix(A, WHITE, 0.2)), layer(boot, mix(iron, WHITE, 0.35), iron, mix(A, WHITE, 0.45), (44, 38, 34), axis=(0.7, 0.7)),
                    layer(plates, mix(A, WHITE, 0.35), A), layer(sole, (60, 40, 26), (30, 18, 10)))
    return finish(subject, [glow(lighter(cracks, shock), A, 1.6, 1.0), glow(lighter(plates, weight), A, 1.2, 0.6)], warlord_back(A, B))


def warlord_ult(A, B):
    # 마지막 휘두름: 몸을 둘러 크게 도는 도끼 궤적 (누운 타원 띠 — 뒤에서 가늘게 시작해 앞에서 굵고 밝아짐) · 띠 끝의 도끼 · 바깥 고리 땅에서 솟는 돌창 셋 · 가운데 쇠빛 몸
    gnd = mask()
    poly(gnd, [(16.0 + math.cos(math.radians(a)) * 15.0, 25.4 + math.sin(math.radians(a)) * 5.0) for a in range(0, 361, 6)])
    ring_l = mask()
    line(ring_l, [(16.0 + math.cos(math.radians(a)) * 12.6, 25.4 + math.sin(math.radians(a)) * 4.0) for a in range(0, 361, 6)], 0.8)
    spikes = mask()
    for ang, h in ((128, 7.0), (90, 8.6), (52, 7.0)):
        a = math.radians(ang)
        rock_spike(spikes, 16.0 + math.cos(a) * 12.6, 25.4 + math.sin(a) * 4.0 + 1.2, h, math.cos(a) * 2.0, 3.6)
    man = mask()
    disc(man, 16.0, 7.8, 2.3)
    poly(man, [(13.0, 10.6), (19.0, 10.6), (18.4, 18.0), (13.6, 18.0)])
    line(man, [(14.6, 17.6), (13.4, 24.6)], 1.7)
    line(man, [(17.4, 17.6), (18.6, 24.6)], 1.7)
    back = mask()
    ribbon(back, 16.0, 14.6, 13.4, 4.4, 186, 352, 0.4, 1.3)
    front = mask()
    ribbon(front, 16.0, 14.6, 13.4, 4.4, -8, 166, 1.3, 3.4)
    trail = mask()
    ribbon(trail, 16.0, 11.6, 11.0, 3.4, 10, 150, 0.2, 0.8)
    axe = axe_layers((3.4, 17.0), (11.6, 13.0), blade=6.0, tint=A, side=1)
    iron = (120, 104, 92)
    subject = stack(layer(back, mix(A, WHITE, 0.2), A), layer(gnd, mix(B, (40, 22, 10), 0.5), mix(B, (10, 5, 2), 0.8)), layer(ring_l, WHITE, A),
                    layer(man, mix(iron, WHITE, 0.25), iron, mix(A, WHITE, 0.4), (40, 32, 28), axis=(0.6, 0.8)), rock_layer(spikes, A),
                    layer(trail, mix(A, WHITE, 0.5), A), layer(front, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.2), axis=(1, 0)), *axe)
    return finish(subject, [glow(lighter(front, ring_l, trail), A, 1.8, 1.0), glow(back, A, 1.2, 0.6)], warlord_back(A, B))


# ------------------------------------------------------------ 스타르카드(글래디에이터) (빛: 진홍 → 모래 금빛 — 결투장 바탕)

ARENA_DUSK = (30, 10, 6)


def gladiator_back(A, B):
    return backdrop(mix(A, ARENA_DUSK, 0.86), mix(A, (90, 20, 8), 0.4))


def stake(m, x, base, h, w=2.4):
    """결투 말뚝 (뾰족한 머리 — 게임의 rfx_jstake 와 같은 꼴)"""
    poly(m, [(x - w / 2, base), (x - w / 2, base - h + 2.4), (x, base - h), (x + w / 2, base - h + 2.4), (x + w / 2, base)])


def slash(m, x0, y0, x1, y1, w, steps=14):
    """가운데가 굵고 양끝이 뾰족한 베기 자국"""
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / n, dx / n
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        hw = w * math.sin(math.pi * t) ** 0.8
        px, py = x0 + dx * t, y0 + dy * t
        left.append((px + nx * hw, py + ny * hw))
        right.append((px - nx * hw, py - ny * hw))
    poly(m, left + right[::-1])


def gladiator_flurry(A, B):
    # 도끼 난타: 나란히 내리친 도끼 자국 셋 (오른쪽 위 → 왼쪽 아래 — 셋째가 가장 굵고 밝음: 셋째 찍기) · 셋째 자국의 맞은 섬광 · 오른쪽 위 도끼와 그 뒤 휘두른 잔상 호 둘 · 튀는 불티
    cuts = mask()
    slash(cuts, 17.0, 2.0, 3.0, 16.0, 1.4)
    slash(cuts, 21.4, 5.0, 5.4, 21.0, 1.7)
    heavy = mask()
    slash(heavy, 27.0, 8.0, 8.0, 27.0, 2.6)
    hit = mask()
    star(hit, 17.0, 18.0, 5.2, 0.16, 45)
    trail = mask()
    crescent(trail, 21.0, 12.0, 8.6, 1.0, -80, -20)
    crescent(trail, 21.0, 12.0, 6.2, 0.8, -80, -30)
    axe = axe_layers((25.6, 4.4), (31.0, 0.0), blade=5.0, tint=A, side=-1)
    sp = mask()
    for x, y, r in ((5.0, 26.0, 0.9), (12.0, 29.0, 0.8), (27.6, 20.0, 0.9), (24.0, 26.6, 0.7)):
        disc(sp, x, y, r)
    subject = stack(layer(trail, mix(B, WHITE, 0.3), B), layer(cuts, mix(A, WHITE, 0.55), A, WHITE, mix(A, (0, 0, 0), 0.3), axis=(1, 0)),
                    layer(heavy, WHITE, mix(A, WHITE, 0.2)), layer(hit, WHITE, B), *axe, layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(cuts, heavy), A, 1.6, 1.0), glow(hit, B, 1.8, 0.8)], gladiator_back(A, B))


def gladiator_hook(A, B):
    # 걸어 당기기: 오른쪽 위에서 휘두른 도끼의 걸린 날 (갈고리 꼴 초승달) · 끌려오는 적 (왼쪽 → 오른쪽으로 · 뒤에 흙먼지 줄) · 당기는 화살표 꼴 줄
    hook = mask()
    crescent(hook, 13.0, 15.0, 9.6, 2.6, 200, 330)
    tip = mask()
    poly(tip, [(5.2, 12.0), (3.0, 16.4), (6.8, 15.0)])
    enemy = mask()
    disc(enemy, 9.0, 18.6, 2.4)
    poly(enemy, [(7.0, 21.0), (11.0, 21.0), (11.6, 27.4), (6.4, 27.4)])
    drag = mask()
    for y, x0 in ((22.0, 1.0), (25.0, 2.0), (28.0, 1.4)):
        line(drag, [(x0, y), (x0 + 4.0, y)], 0.8)
    pull = mask()
    line(pull, [(13.0, 24.0), (25.0, 24.0)], 1.2)
    poly(pull, [(25.0, 21.6), (29.6, 24.0), (25.0, 26.4)])
    axe = axe_layers((24.0, 8.4), (29.6, 18.6), blade=5.6, tint=A, side=1)
    ground = mask()
    poly(ground, [(1, 28.0), (31, 28.0), (31, 29.0), (1, 29.0)])
    dark = mix(A, (0, 0, 0), 0.6)
    subject = stack(layer(ground, mix(A, (40, 14, 8), 0.6), mix(A, (10, 4, 2), 0.8)), layer(drag, mix(B, WHITE, 0.2), B),
                    layer(hook, WHITE, A, WHITE, dark, axis=(1, 0)), layer(tip, WHITE, A), layer(enemy, (150, 150, 162), (90, 90, 104), (220, 220, 230), dark),
                    layer(pull, mix(B, WHITE, 0.4), B), *axe)
    return finish(subject, [glow(lighter(hook, tip), A, 1.6, 1.0), glow(pull, B, 1.4, 0.7)], gladiator_back(A, B))


def gladiator_eight(A, B):
    # 여덟 팔: 어두운 결투자 (머리 · 빛나는 두 눈 · 어깨) 의 어깨에서 위로 부채꼴로 뻗은 팔 여덟 — 끝마다 도끼날 (짝수는 짙고 홀수는 잔상처럼 옅음)
    arms = mask()
    faint = mask()
    heads = mask()
    ox, oy = 16.0, 21.0
    for i in range(8):
        a = math.radians(-170 + i * 22.86)
        x0, y0 = ox + math.cos(a) * 4.0, oy + math.sin(a) * 4.0
        x1, y1 = ox + math.cos(a) * 13.0, oy + math.sin(a) * 13.0
        tgt = arms if i % 2 == 0 else faint
        line(tgt, [(x0, y0), (x1, y1)], 1.4 if i % 2 == 0 else 1.0)
        nx, ny = -math.sin(a), math.cos(a)
        sd = 1 if i < 4 else -1
        hx, hy = x1 - math.cos(a) * 0.4, y1 - math.sin(a) * 0.4
        poly(heads, [(hx + math.cos(a) * 0.8, hy + math.sin(a) * 0.8), (hx + nx * sd * 3.2 + math.cos(a) * 1.8, hy + ny * sd * 3.2 + math.sin(a) * 1.8),
                     (hx + nx * sd * 3.2 - math.cos(a) * 1.4, hy + ny * sd * 3.2 - math.sin(a) * 1.4), (hx - math.cos(a) * 0.8, hy - math.sin(a) * 0.8)])
    body = mask()
    disc(body, 16.0, 20.2, 2.8)
    poly(body, [(11.0, 24.0), (21.0, 24.0), (19.6, 31.0), (12.4, 31.0)])
    disc(body, 12.0, 24.4, 1.6)
    disc(body, 20.0, 24.4, 1.6)
    eyes = mask()
    disc(eyes, 15.0, 20.2, 0.6)
    disc(eyes, 17.0, 20.2, 0.6)
    subject = stack(layer(faint, mix(A, B, 0.4), mix(A, (0, 0, 0), 0.3)), layer(arms, mix(A, WHITE, 0.25), A, WHITE, mix(A, (0, 0, 0), 0.4)),
                    layer(heads, WHITE, mix(B, WHITE, 0.2), WHITE, mix(B, (0, 0, 0), 0.4)), layer(body, mix(A, (0, 0, 0), 0.55), mix(A, (0, 0, 0), 0.75)),
                    layer(eyes, WHITE, B))
    return finish(subject, [glow(lighter(arms, heads), A, 1.6, 0.9), glow(eyes, B, 1.4, 1.0)], gladiator_back(A, B))


def gladiator_fury(A, B):
    # 불사의 분노: 붉은 불길에 싸인 사람 (몸 · 머리 · 팔을 벌림) · 위로 솟는 불길 · 가슴의 꺼지지 않는 불씨 (체력 1) · 둘레 고리
    fire = mask()
    for x, h in ((9.0, 14.0), (13.0, 20.0), (16.0, 24.0), (19.0, 20.0), (23.0, 14.0)):
        poly(fire, [(x - 3.0, 28.0), (x + 3.0, 28.0), (x + 0.6, 28.0 - h), (x - 0.6, 28.0 - h + 2.0)])
    man = mask()
    disc(man, 16.0, 10.6, 2.6)
    poly(man, [(13.2, 13.6), (18.8, 13.6), (18.2, 22.0), (13.8, 22.0)])
    line(man, [(13.6, 14.4), (8.4, 10.0)], 1.6)
    line(man, [(18.4, 14.4), (23.6, 10.0)], 1.6)
    line(man, [(14.6, 21.6), (13.0, 27.0)], 1.7)
    line(man, [(17.4, 21.6), (19.0, 27.0)], 1.7)
    ember = mask()
    disc(ember, 16.0, 16.4, 1.6)
    rim = mask()
    crescent(rim, 16.0, 28.0, 12.0, 1.4, 196, 344)
    dark = mix(A, (0, 0, 0), 0.6)
    subject = stack(layer(fire, mix(A, WHITE, 0.35), A, mix(B, WHITE, 0.4), dark, axis=(0, 1)), layer(rim, mix(B, WHITE, 0.3), B),
                    layer(man, mix(A, (0, 0, 0), 0.55), mix(A, (0, 0, 0), 0.75)), layer(ember, WHITE, B))
    return finish(subject, [glow(fire, A, 1.8, 0.9), glow(ember, B, 2.0, 1.0)], gladiator_back(A, B))


def gladiator_ult(A, B):
    # 홀름강: 둘레에 선 결투 말뚝 (앞쪽 다섯 — 밧줄로 이어짐) 이 둘러싼 결투장 바닥 (누운 고리) · 가운데 엇갈린 두 도끼
    floor = mask()
    for r, w in ((13.0, 1.4), (11.4, 0.6)):
        pts_o = [(16 + math.cos(math.radians(a)) * r, 22 + math.sin(math.radians(a)) * r * 0.36) for a in range(0, 361, 8)]
        line(floor, pts_o, w)
    stakes = mask()
    for x, base, h in ((4.0, 24.0, 9.0), (9.4, 26.4, 10.0), (16.0, 27.4, 10.6), (22.6, 26.4, 10.0), (28.0, 24.0, 9.0)):
        stake(stakes, x, base, h)
    rope = mask()
    line(rope, [(4.0, 18.4), (9.4, 19.6), (16.0, 20.2), (22.6, 19.6), (28.0, 18.4)], 0.8)
    axes = []
    for (head, tail, side) in (((10.4, 6.0), (21.6, 17.6), -1), ((21.6, 6.0), (10.4, 17.6), 1)):
        axes += axe_layers(head, tail, blade=5.0, tint=A, side=side)
    subject = stack(layer(floor, mix(A, WHITE, 0.3), A), *axes, layer(stakes, (150, 104, 68), (92, 58, 34), (200, 160, 120), (40, 24, 12)),
                    layer(rope, mix(A, WHITE, 0.2), A))
    return finish(subject, [glow(floor, A, 1.8, 0.9), glow(rope, A, 1.2, 0.6)], gladiator_back(A, B))


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


# 0.51.0 재설계안 2판 — 뵐룬드(룬 스미스) 의 대장간: 쇳물 (주황) · 흰 열 · 어두운 쇠 (룬은 금빛 — 게임의 rfx_janvil · jmolten · jram 과 같은 꼴)
MOLT = (255, 110, 20)
MOLT_HI = (255, 226, 128)
IRON = (70, 62, 74)
IRON_HI = (132, 122, 138)
IRON_LO = (22, 18, 26)
FORGE_DARK = (18, 6, 8)


def forge_back(A, B):
    return backdrop(mix(A, FORGE_DARK, 0.86), mix(MOLT, (60, 16, 6), 0.55))


def tapered(m, pts, w0, w1):
    """가운데 줄 pts 를 따라 굵기 w0 → w1 로 가늘어지는 띠 (뿔 · 불길)"""
    n = len(pts)
    left, right = [], []
    for i, (x, y) in enumerate(pts):
        a, b = pts[max(0, i - 1)], pts[min(n - 1, i + 1)]
        tx, ty = b[0] - a[0], b[1] - a[1]
        L = math.hypot(tx, ty) or 1.0
        nx, ny = -ty / L, tx / L
        w = (w0 + (w1 - w0) * i / max(1, n - 1)) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    poly(m, left + right[::-1])
    return m


def anvil_mask(cx, top, s=1.0):
    """모루 (옆모습 — 왼쪽 뿔 · 윗면 · 오른쪽 굽 · 오목한 허리 · 발): 게임의 rfx_janvil 과 같은 꼴"""
    m = mask()
    pts = [(-13.4, 1.6), (-10.6, 0.6), (-7.6, 0.0), (11.6, 0.0), (12.4, 0.6), (12.4, 3.6), (9.6, 4.4), (5.0, 5.4), (3.2, 8.2), (3.2, 10.6), (4.8, 12.4),
           (8.2, 13.6), (9.0, 15.6), (-9.0, 15.6), (-8.2, 13.6), (-4.8, 12.4), (-3.2, 10.6), (-3.2, 8.2), (-5.0, 5.4), (-8.4, 4.8), (-11.6, 3.2)]
    poly(m, [(cx + x * s, top + y * s) for x, y in pts])
    return m


def molten_layer(m):
    return layer(m, MOLT_HI, MOLT, WHITE, mix(MOLT, (0, 0, 0), 0.35))


def iron_layer(m, A):
    return layer(m, IRON_HI, IRON, mix(A, WHITE, 0.5), IRON_LO, axis=(0.6, 0.8))


def runeknight_anvil(A, B):
    # 룬 모루: 땅을 가르며 다가온 쇳물빛 금 (왼쪽 아래 → 모루 밑) · 갈라진 땅에서 솟은 룬 모루 (허리의 금빛 룬 · 달아오른 윗면 테) · 모루 둘레의 흰 열 금 · 터지기 직전 위의 불티 별
    gnd = ground(24.0)
    crack = mask()
    tapered(crack, [(2.0, 31.6), (5.6, 29.0), (8.2, 29.6), (11.4, 26.6), (13.6, 25.0)], 3.2, 1.2)
    lava = mask()
    tapered(lava, [(2.6, 31.4), (5.8, 29.2), (8.4, 29.4), (11.6, 26.8), (13.6, 25.2)], 1.4, 0.5)
    anv = anvil_mask(16.6, 8.6, 1.0)
    rune = mask()
    glyph(rune, 0, 16.6, 19.0, 3.4, 5.0, 1.0)
    top = mask()
    line(top, [(5.0, 10.0), (9.0, 8.7), (28.0, 8.7)], 0.8)
    hot = mask()
    line(hot, [(13.4, 13.2), (12.6, 16.2), (13.8, 19.4)], 0.6)
    line(hot, [(20.4, 13.6), (21.4, 17.0), (20.2, 20.6)], 0.6)
    sp = mask()
    star(sp, 22.0, 4.2, 3.6, 0.2, 10)
    star(sp, 9.0, 4.0, 2.0, 0.24, 0)
    pool = mask()
    poly(pool, [(16.6 + math.cos(math.radians(a)) * 10.6, 24.8 + math.sin(math.radians(a)) * 2.0) for a in range(0, 361, 10)])
    subject = stack(ground_layer(gnd, MOLT), molten_layer(pool), layer(crack, (34, 12, 6), (10, 4, 2)), layer(lava, MOLT_HI, MOLT), iron_layer(anv, A),
                    layer(top, MOLT_HI, MOLT), layer(hot, MOLT_HI, MOLT), layer(rune, mix(B, WHITE, 0.5), B, WHITE, mix(B, (0, 0, 0), 0.4)), layer(sp, WHITE, MOLT_HI))
    return finish(subject, [glow(lighter(rune, sp), B, 1.8, 1.0), glow(lighter(lava, pool, top, hot), MOLT, 1.6, 0.9)], forge_back(A, B))


def runeknight_bellows(A, B):
    # 풀무 숨: 왼쪽에서 곧게 겨눈 룬 대검 (날의 금빛 룬 셋) · 칼끝에서 오른쪽으로 부채꼴로 뿜어지는 불길 (겹친 불 혀 — 끝이 넓어짐) · 칼 뒤의 보랏빛 보호막 호
    cone = mask()
    poly(cone, [(15.0, 14.4), (31.0, 3.0), (31.0, 28.0)])
    tongues = mask()
    for pts, w0 in (([(15.6, 14.4), (21.0, 9.6), (27.0, 6.6), (30.6, 5.6)], 3.0), ([(15.6, 14.6), (22.0, 14.0), (28.0, 13.6), (31.0, 14.6)], 3.6),
                    ([(15.6, 14.8), (21.0, 19.4), (26.6, 22.6), (30.4, 25.6)], 3.0)):
        tapered(tongues, pts, w0 * 0.4, w0 * 1.6)
    core = mask()
    tapered(core, [(15.6, 14.6), (20.6, 14.2), (25.0, 14.0)], 0.6, 2.6)
    sw = rune_sword((15.6, 14.6), (1.4, 22.0), A, B, blade=3.0, guard=6.6)
    runes = mask()
    for i, (x, y) in enumerate(((11.8, 16.6), (8.6, 18.2))):
        glyph(runes, i + 1, x, y, 1.8, 2.4, 0.5, ang=-28)
    shield = mask()
    crescent(shield, 9.0, 18.0, 9.6, 1.6, 120, 250)
    sparks = mask()
    for x, y, r in ((24.0, 4.0, 1.4), (29.0, 18.6, 1.0), (26.6, 27.4, 1.2)):
        star(sparks, x, y, r * 1.8, 0.24, 15)
    subject = stack(layer(shield, mix(A, WHITE, 0.4), A, None, mix(A, (0, 0, 0), 0.3)), layer(cone, mix(MOLT, (0, 0, 0), 0.25), mix(MOLT, (0, 0, 0), 0.55)),
                    layer(tongues, MOLT_HI, MOLT, WHITE, mix(MOLT, (0, 0, 0), 0.3), axis=(1, 0)), layer(core, WHITE, MOLT_HI), *sw, layer(runes, WHITE, B),
                    layer(sparks, WHITE, MOLT_HI))
    return finish(subject, [glow(lighter(tongues, core), MOLT, 2.0, 1.0), glow(shield, A, 1.4, 0.7), glow(runes, B, 1.0, 0.8)], forge_back(A, B))


def runeknight_furnace(A, B):
    # 용광로: 땅의 룬 원 (금빛 테 · 룬 점) 안의 끓는 쇳물 웅덩이 · 가운데에서 하늘로 솟구치는 쇳물 기둥 (위가 넓은 왕관 · 흰 열 심) · 튀어 오른 쇳물 방울
    pool = mask()
    poly(pool, [(16.0 + math.cos(math.radians(a)) * 12.6, 25.0 + math.sin(math.radians(a)) * 4.2) for a in range(0, 361, 8)])
    rim = mask()
    oval(rim, 16.0, 25.0, 13.8, 4.8, 1.2)
    dots = mask()
    for i in range(10):
        a = math.radians(i * 36 + 18)
        disc(dots, 16.0 + math.cos(a) * 13.1, 25.0 + math.sin(a) * 4.5, 0.55)
    col = mask()
    poly(col, [(13.0, 25.6), (14.2, 14.0), (12.4, 7.6), (9.6, 4.0), (13.0, 5.4), (14.6, 2.4), (16.0, 4.6), (17.4, 2.4), (19.0, 5.4), (22.4, 4.0),
               (19.6, 7.6), (17.8, 14.0), (19.0, 25.6)])
    heart = mask()
    tapered(heart, [(16.0, 25.0), (16.0, 16.0), (16.0, 7.0)], 2.4, 0.8)
    drops = mask()
    for x, y, r in ((7.0, 9.0, 1.2), (25.4, 8.4, 1.3), (5.0, 16.4, 0.9), (27.4, 15.0, 1.0), (10.6, 2.6, 0.8), (21.8, 1.8, 0.8)):
        drop(drops, x, y, r, ang=-90 if y < 10 else 100)
    plates = mask()
    for x0, x1, y in ((6.0, 10.4, 25.6), (21.6, 26.0, 25.0), (9.4, 12.0, 23.2)):
        line(plates, [(x0, y), (x1, y + 0.4)], 1.1)
    subject = stack(molten_layer(pool), layer(plates, mix(MOLT, (0, 0, 0), 0.6), mix(MOLT, (0, 0, 0), 0.75)), layer(rim, mix(B, WHITE, 0.4), B, WHITE, mix(B, (0, 0, 0), 0.4)),
                    layer(dots, WHITE, B), layer(col, MOLT_HI, MOLT, WHITE, mix(MOLT, (0, 0, 0), 0.3), axis=(0, -1)), layer(heart, WHITE, MOLT_HI), molten_layer(drops))
    return finish(subject, [glow(lighter(col, heart, drops), MOLT, 2.2, 1.0), glow(lighter(rim, dots), B, 1.4, 0.8)], forge_back(A, B))


def runeknight_temper(A, B):
    # 담금질: 가로로 누운 달군 대검 (날이 쇳물빛 → 흰 열 — 칼끝 쪽이 더 뜨거움) · 오른쪽 위에서 칼날을 내려치는 룬 망치 (금빛 머리 · 룬) · 맞은 자리의 불티 별 · 위로 솟는 김
    parts = {"blade": mask(), "guard": mask(), "grip": mask()}
    from job_skill_icons import sword
    sword(None, (30.0, 21.4), (2.0, 21.4), 4.6, 9.0, parts=parts)
    blade = layer(parts["blade"], WHITE, MOLT, MOLT_HI, mix(MOLT, (0, 0, 0), 0.35), axis=(1, 0))
    guard = layer(parts["guard"], mix(A, WHITE, 0.35), A, WHITE, mix(A, (0, 0, 0), 0.45))
    grip = layer(parts["grip"], mix(A, (0, 0, 0), 0.5), mix(A, (0, 0, 0), 0.75))
    # 망치: 칼날 위 맞은 자리로 비스듬히 내려친 큰 쇠망치 (머리 = 기운 네모 · 때리는 면이 밝음) · 오른쪽 위 구석으로 뻗은 자루
    ang = math.radians(-34)
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    hx, hy = 17.4, 12.2
    head = mask()
    poly(head, [(hx - ux * 5.2 - px * 3.0, hy - uy * 5.2 - py * 3.0), (hx + ux * 5.2 - px * 3.0, hy + uy * 5.2 - py * 3.0),
                (hx + ux * 5.2 + px * 3.0, hy + uy * 5.2 + py * 3.0), (hx - ux * 5.2 + px * 3.0, hy - uy * 5.2 + py * 3.0)])
    face = mask()
    line(face, [(hx - ux * 5.0 + px * 2.6, hy - uy * 5.0 + py * 2.6), (hx + ux * 5.0 + px * 2.6, hy + uy * 5.0 + py * 2.6)], 0.9)
    handle = mask()
    line(handle, [(hx - px * 2.0, hy - py * 2.0), (hx - px * 13.0, hy - py * 13.0)], 1.8)
    hrune = mask()
    glyph(hrune, 1, hx, hy - 0.2, 2.6, 3.4, 0.6, ang=-34)
    hit = mask()
    star(hit, 15.8, 18.6, 5.4, 0.18, 8)
    sparks = mask()
    for x, y in ((8.6, 15.0), (24.6, 16.4), (11.4, 13.0), (5.6, 18.0)):
        disc(sparks, x, y, 0.6)
    steam = mask()
    for x, y, r in ((27.0, 17.0, 2.0), (28.4, 13.6, 1.6), (27.0, 10.6, 1.2)):
        disc(steam, x, y, r)
    subject = stack(layer(steam, (244, 246, 250), (196, 200, 212), None, (150, 152, 164)), grip, blade, guard, layer(handle, (130, 90, 54), (70, 44, 24)),
                    layer(head, mix(B, WHITE, 0.35), B, WHITE, mix(B, (0, 0, 0), 0.55), axis=(0.6, 0.8)), layer(face, WHITE, MOLT_HI),
                    layer(hrune, WHITE, mix(A, WHITE, 0.3)), layer(hit, WHITE, MOLT_HI), layer(sparks, WHITE, MOLT_HI))
    return finish(subject, [glow(parts["blade"], MOLT, 2.0, 1.0), glow(lighter(hit, sparks), MOLT_HI, 1.6, 1.0), glow(head, B, 1.2, 0.5)], forge_back(A, B))


def ram_horn(m, side, cx, cy, s=1.0):
    """숫양 뿔 (앞모습 — 머리 위 뿌리에서 바깥 · 뒤 · 아래로 감겨 앞으로 끝남): 게임의 rfx_jram 과 같은 꼴"""
    pts = []
    for i in range(31):
        t = i / 30
        th = -2.25 + t * 4.5
        r = (6.4 - 3.8 * t) * s
        pts.append((cx + side * (r * math.cos(th) + 0.6 * t), cy + r * math.sin(th)))
    tapered(m, pts, 4.6 * s, 1.2 * s)
    return pts


def runeknight_ult(A, B):
    # 쇳물 숫양: 앞을 보고 달려드는 쇳물 숫양의 머리 (식은 껍질 · 쇳물 금 · 흰 열 눈 · 이마의 금빛 룬) · 양옆의 큰 나선 뿔 (마디 줄) · 뒤에서 솟는 불길 · 아래의 쇳물 튐
    flames = mask()
    for x, h in ((5.0, 9.0), (9.0, 13.0), (23.0, 13.0), (27.0, 9.0), (16.0, 6.0)):
        tapered(flames, [(x, 31.0), (x + 0.6, 31.0 - h * 0.5), (x - 0.4, 31.0 - h)], 4.0, 0.4)
    head = mask()
    poly(head, [(11.6, 8.8), (13.6, 6.6), (18.4, 6.6), (20.4, 8.8), (20.8, 14.0), (19.6, 20.6), (18.0, 25.0), (16.0, 26.2), (14.0, 25.0), (12.4, 20.6),
                (11.2, 14.0)])
    horns = mask()
    hl = ram_horn(horns, -1, 9.2, 12.6, 1.0)
    hr = ram_horn(horns, 1, 22.8, 12.6, 1.0)
    ridges = mask()
    for pts in (hl, hr):
        for q in range(4, 27, 5):
            (x0, y0), (x1, y1) = pts[q - 1], pts[q + 1]
            L = math.hypot(x1 - x0, y1 - y0) or 1
            nx, ny = -(y1 - y0) / L, (x1 - x0) / L
            w = (4.6 - 3.4 * q / 30) * 0.5
            line(ridges, [(pts[q][0] - nx * w, pts[q][1] - ny * w), (pts[q][0] + nx * w, pts[q][1] + ny * w)], 0.35)
    veins = mask()
    line(veins, [(13.0, 10.0), (14.4, 13.4), (13.2, 17.0)], 0.5)
    line(veins, [(19.0, 10.4), (17.8, 14.0), (18.8, 17.6)], 0.5)
    line(veins, [(16.0, 19.6), (15.2, 22.6)], 0.5)
    eyes = mask()
    for sd in (-1, 1):
        poly(eyes, [(16.0 + sd * 4.4, 13.6), (16.0 + sd * 1.4, 14.8), (16.0 + sd * 3.6, 15.4)])
    rune = mask()
    glyph(rune, 0, 16.0, 10.0, 2.4, 3.2, 0.6)
    nose = mask()
    for sd in (-1, 1):
        disc(nose, 16.0 + sd * 1.3, 23.4, 0.55)
    splash = mask()
    for x, y, r in ((6.0, 27.0, 1.0), (26.0, 27.6, 1.1), (10.6, 29.6, 0.8), (21.6, 29.8, 0.8)):
        drop(splash, x, y, r, ang=-100 if x < 16 else -80)
    crust = mix(MOLT, (60, 20, 10), 0.72)
    subject = stack(layer(flames, MOLT_HI, MOLT, None, mix(MOLT, (0, 0, 0), 0.3), axis=(0, -1)), layer(horns, mix(MOLT, WHITE, 0.25), mix(crust, MOLT, 0.35),
                    MOLT_HI, mix(crust, (0, 0, 0), 0.5), axis=(0.5, 0.8)), layer(ridges, MOLT_HI, MOLT), layer(head, mix(crust, MOLT, 0.45), crust, MOLT,
                    mix(crust, (0, 0, 0), 0.5), axis=(0.4, 0.9)), layer(veins, MOLT_HI, MOLT), layer(eyes, WHITE, MOLT_HI), layer(rune, WHITE, B),
                    layer(nose, MOLT_HI, MOLT), molten_layer(splash))
    return finish(subject, [glow(lighter(flames, veins, eyes, ridges), MOLT, 2.0, 1.0), glow(rune, B, 1.0, 0.9)], forge_back(A, B))


# ------------------------------------------------------------ 스키르니르(소드 콜러) (빛: 청록 → 흰 청록 — 칼이 손을 떠나 스스로 싸움 · 재설계안 2판)

CALL_NIGHT = (4, 14, 20)


def caller_back(A, B):
    return backdrop(mix(A, CALL_NIGHT, 0.86), mix(A, (10, 54, 64), 0.5))


def call_sword(tip, end, A, B, blade=2.6, guard=6.0, fade=0.0):
    """마력 칼 (흰 청록 날 · 청록 코등이 — 게임의 phantom_sword 판). fade = 뒤 잔상 (바탕 쪽으로 어둡게)"""
    def f(c):
        return None if c is None else mix(c, CALL_NIGHT, fade)
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(f(WHITE), f(mix(A, WHITE, 0.55)), f(WHITE), f(mix(A, (0, 0, 0), 0.35))),
                        guard_cols=(f(mix(A, WHITE, 0.35)), f(A), f(WHITE), f(mix(A, (0, 0, 0), 0.5))),
                        grip_cols=(f(mix(A, (0, 0, 0), 0.45)), f(mix(A, (0, 0, 0), 0.7)), None, None))


def swordcaller_strike(A, B):
    # 베어라: 왼쪽 위에서 날아드는 칼 (뒤의 바람 줄 · 옅은 잔상) · 오른쪽 아래 겨눈 자리의 고리 (땅) · 그 자리를 휘감아 도는 칼빛 두 줄 (원을 이룸)
    target = mask()
    oval(target, 21.0, 26.0, 8.6, 3.0, 1.0)
    whirl = mask()
    crescent(whirl, 21.0, 21.4, 8.0, 2.0, 200, 340)
    crescent(whirl, 21.0, 21.4, 8.0, 2.0, 20, 160)
    ghost = call_sword((15.4, 15.4), (2.0, 2.0), A, B, blade=2.4, guard=5.2, fade=0.55)
    sw = call_sword((21.4, 21.4), (8.0, 8.0), A, B, blade=2.8, guard=6.0)
    wind = mask()
    for (x0, y0), (x1, y1) in (((2.0, 10.4), (8.6, 17.0)), ((10.4, 2.0), (17.0, 8.6)), ((3.4, 14.6), (7.0, 18.2))):
        line(wind, [(x0, y0), (x1, y1)], 0.6)
    flash = mask()
    star(flash, 21.4, 21.4, 3.2, 0.2, 0)
    subject = stack(layer(target, mix(A, WHITE, 0.3), A, None, mix(A, (0, 0, 0), 0.4)), layer(wind, mix(A, WHITE, 0.4), mix(A, CALL_NIGHT, 0.3)), *ghost,
                    layer(whirl, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3)), *sw, layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(whirl, target, flash), A, 1.8, 1.0)], caller_back(A, B))


def swordcaller_shift(A, B):
    # 데려가라: 오른쪽 땅에 꽂혀 기다리는 칼 (발밑 룬 고리) · 칼에서 뻗어 나를 끌어당기는 청록 줄 · 왼쪽에서 칼 쪽으로 날아가는 몸의 잔상 (갈매기 셋 — 앞이 밝음) · 바람 줄
    gnd = ground(26.4)
    rring = mask()
    oval(rring, 24.0, 26.6, 5.0, 1.8, 0.8)
    stuck = call_sword((24.0, 28.6), (24.0, 6.0), A, B, blade=3.0, guard=6.6)
    tether = mask()
    line(tether, [(23.0, 10.0), (17.0, 13.6), (9.0, 16.6)], 0.8)
    chev = []
    for i, x in enumerate((4.4, 9.4, 14.6)):
        m = mask()
        poly(m, [(x, 12.6), (x + 4.6, 17.0), (x, 21.4), (x + 1.8, 17.0)])
        chev.append((m, 0.62 - 0.3 * i))
    wind = mask()
    for y, x0, x1 in ((11.0, 2.0, 7.6), (23.0, 2.6, 8.6), (9.0, 12.0, 15.6)):
        line(wind, [(x0, y), (x1, y)], 0.5)
    layers_ = [ground_layer(gnd, A), layer(rring, mix(A, WHITE, 0.4), A), layer(wind, mix(A, WHITE, 0.3), mix(A, CALL_NIGHT, 0.4))]
    for m, fade in chev:
        layers_.append(layer(m, mix(WHITE, CALL_NIGHT, fade), mix(A, CALL_NIGHT, fade), None, mix(A, (0, 0, 0), 0.4)))
    subject = stack(*layers_, layer(tether, WHITE, A), *stuck)
    return finish(subject, [glow(lighter(tether, chev[2][0], rring), A, 1.8, 1.0)], caller_back(A, B))


def swordcaller_orbit(A, B):
    # 꿰뚫고 맴돌아라: 가운데 꿰뚫린 과녁 (마름모 표식 · 꿰뚫은 빛줄기) · 둘레를 비스듬히 도는 궤도 · 궤도 앞쪽을 도는 칼 (가는 쪽으로 누움 · 뒤로 끌리는 칼빛) · 벤 자국 셋
    orbit_back = mask()
    ribbon(orbit_back, 16.0, 16.0, 12.4, 5.4, 190, 350, 0.3, 0.6)
    orbit_front = mask()
    ribbon(orbit_front, 16.0, 16.0, 12.4, 5.4, 10, 170, 1.6, 0.4)
    mark = mask()
    poly(mark, [(16.0, 9.6), (20.0, 16.0), (16.0, 22.4), (12.0, 16.0)])
    inner = mask()
    poly(inner, [(16.0, 12.6), (18.0, 16.0), (16.0, 19.4), (14.0, 16.0)])
    pierce = mask()
    tapered(pierce, [(2.6, 5.0), (16.0, 16.0), (29.4, 27.0)], 0.4, 1.6)
    a = math.radians(70)
    cx, cy = 16.0 + math.cos(a) * 12.4, 16.0 + math.sin(a) * 5.4
    tx, ty = -math.sin(a) * 12.4, math.cos(a) * 5.4
    L = math.hypot(tx, ty)
    tx, ty = tx / L, ty / L
    sw = call_sword((cx + tx * 8.0, cy + ty * 8.0 - 1.0), (cx - tx * 6.6, cy - ty * 6.6 + 1.0), A, B, blade=3.0, guard=6.0)
    subject = stack(layer(orbit_back, mix(A, WHITE, 0.2), mix(A, CALL_NIGHT, 0.4)), layer(pierce, WHITE, A), layer(mark, mix(B, A, 0.4), A, WHITE, mix(A, (0, 0, 0), 0.5)),
                    layer(inner, WHITE, B), layer(orbit_front, WHITE, A), *sw)
    return finish(subject, [glow(lighter(orbit_front, pierce), A, 1.8, 1.0), glow(inner, B, 1.0, 0.8)], caller_back(A, B))


def swordcaller_twin(A, B):
    # 둘로 나뉘어라: 가운데 아래에서 하나였던 칼이 둘로 갈라져 V 자로 벌어짐 (왼쪽 칼은 거울 · 오른쪽 칼 — 둘 다 칼끝 위) · 가운데를 가르는 세로 빛 · 갈라진 자리의 번쩍임
    split = mask()
    tapered(split, [(16.0, 30.0), (16.0, 16.0), (16.0, 2.0)], 1.4, 0.2)
    left = call_sword((5.4, 4.0), (14.6, 27.4), A, B, blade=2.8, guard=6.0)
    right = call_sword((26.6, 4.0), (17.4, 27.4), A, B, blade=2.8, guard=6.0)
    arcs = mask()
    crescent(arcs, 16.0, 26.0, 12.0, 1.4, 196, 250)
    crescent(arcs, 16.0, 26.0, 12.0, 1.4, 290, 344)
    flash = mask()
    star(flash, 16.0, 23.4, 4.6, 0.18, 0)
    subject = stack(layer(split, WHITE, mix(A, WHITE, 0.4)), layer(arcs, mix(A, WHITE, 0.3), A), *left, *right, layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(split, arcs, flash), A, 1.8, 1.0)], caller_back(A, B))


def swordcaller_ult(A, B):
    # 칼날 감옥: 가운데 갇힌 과녁 (마름모 표식) 을 둘러싸 칼끝을 겨눈 칼 여섯 (둥글게) · 칼 사이를 잇는 감옥 고리 · 가운데로 모이는 벤 자국 · 가운데 번쩍임
    cage = mask()
    ring(cage, 16.0, 16.0, 9.0, 0.9)
    swords = []
    for i in range(6):
        a = math.radians(-90 + i * 60)
        ux, uy = math.cos(a), math.sin(a)
        swords += call_sword((16.0 + ux * 4.6, 16.0 + uy * 4.6), (16.0 + ux * 14.6, 16.0 + uy * 14.6), A, B, blade=2.2, guard=4.8)
    mark = mask()
    poly(mark, [(16.0, 12.6), (19.0, 16.0), (16.0, 19.4), (13.0, 16.0)])
    cuts = mask()
    for a in (20, 80, 140):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        line(cuts, [(16.0 - ca * 6.0, 16.0 - sa * 6.0), (16.0 + ca * 6.0, 16.0 + sa * 6.0)], 0.5)
    flash = mask()
    star(flash, 16.0, 16.0, 4.0, 0.2, 45)
    subject = stack(layer(cage, mix(A, WHITE, 0.3), A, None, mix(A, (0, 0, 0), 0.4)), layer(cuts, WHITE, mix(A, WHITE, 0.5)), *swords,
                    layer(mark, mix(B, A, 0.4), A, WHITE, mix(A, (0, 0, 0), 0.5)), layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(cage, cuts, flash), A, 1.8, 1.0)], caller_back(A, B))


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


# ------------------------------------------------------------ 우트가르드(트릭스터) (빛: 자홍 → 검보라 — 짙은 보라 바탕 · 청록 환영 테두리)

TEAL = (77, 242, 230)
TEAL_LO = (20, 110, 112)
VENOM, VENOM_HI = (88, 214, 64), (196, 255, 150)


def trick_back(A, B):
    return backdrop(mix(B, (10, 2, 16), 0.8), mix(A, (44, 8, 56), 0.55))


def rose_mask(cx, cy, R, rot=0.0):
    """겹장미 (바깥 꽃잎 다섯 · 안 꽃잎 다섯 · 가운데 감긴 봉오리) — (꽃잎, 가운데)"""
    m = mask()
    for i in range(5):
        _petal(m, cx, cy, rot - 90 + i * 72, R, R * 0.5)
    inner = mask()
    for i in range(5):
        _petal(inner, cx, cy, rot - 54 + i * 72, R * 0.62, R * 0.38)
    bud = mask()
    disc(bud, cx, cy, R * 0.26)
    return m, inner, bud


def trickster_rose(A, B):
    # 검은 장미 덤불: 가운데 큰 검은 장미 (자홍 테 · 겹꽃잎) · 아래 양옆 작은 장미 둘 · 가시 줄기 · 위왼쪽에서 내리꽂는 단검 · 왼쪽 위의 청록 환영 베기 자국
    big, big_in, bud = rose_mask(18.5, 15.5, 8.0, 8)
    s1, s1_in, _ = rose_mask(7.0, 25.0, 4.2, -20)
    s2, s2_in, _ = rose_mask(27.5, 26.0, 3.8, 30)
    stems = mask()
    line(stems, [(18.5, 21.0), (16.5, 26.0), (11.0, 28.5), (7.0, 28.0)], 1.0)
    line(stems, [(18.0, 22.5), (22.5, 27.5), (27.5, 28.5)], 1.0)
    for (x, y, a) in ((14.5, 27.0, -60), (21.0, 26.0, -120), (10.0, 28.6, -100)):
        poly(stems, [(x - 0.6, y), (x + math.cos(math.radians(a)) * 2.0, y + math.sin(math.radians(a)) * 2.0), (x + 0.6, y)])
    dag = sword_layers((15.0, 13.5), (3.0, 1.5), blade=2.4, guard=5.0, blade_cols=(STEEL_HI, mix(STEEL, A, 0.25), WHITE, STEEL_LO))
    ill = mask()
    crescent(ill, 4.0, 18.0, 12.5, 2.6, -95, -20)
    pt = mask()
    for (x, y, a) in ((28.5, 8.0, 40), (25.0, 3.5, 110), (30.0, 16.5, 160)):
        _petal(pt, x, y, a, 3.0, 1.2)
    dark = mix(B, (0, 0, 0), 0.55)
    subject = stack(layer(ill, mix(TEAL, WHITE, 0.4), TEAL, WHITE, TEAL_LO, axis=(1, -1)), layer(stems, mix(B, A, 0.25), dark),
                    layer(s1, mix(A, B, 0.45), dark, mix(A, WHITE, 0.3), None), layer(s1_in, mix(B, (0, 0, 0), 0.3), dark),
                    layer(s2, mix(A, B, 0.45), dark, mix(A, WHITE, 0.3), None), layer(s2_in, mix(B, (0, 0, 0), 0.3), dark),
                    layer(big, mix(A, B, 0.35), dark, mix(A, WHITE, 0.35), None), layer(big_in, mix(B, A, 0.25), dark, A, None),
                    layer(bud, mix(A, WHITE, 0.3), B), *dag, layer(pt, mix(A, WHITE, 0.4), A))
    return finish(subject, [glow(big, A, 2.0, 0.9), glow(ill, TEAL, 1.4, 0.8), glow(lighter(s1, s2), A, 1.3, 0.6)], trick_back(A, B))


def ghost_hood(cx, top, w, h):
    body, face = _hood(cx, top, w, h)
    return body, face


def trickster_mirage(A, B):
    # 우트가르드의 환술: 양옆의 환영 둘 (청록 테두리 · 비치는 검보라 두건) · 가운데 사라지는 몸 (흩어지는 자홍 연기) · 깨지는 거울 조각 · 앞으로 뻗은 빠른 줄
    lb, lf = ghost_hood(7.5, 7.0, 10.5, 19.0)
    rb, rf = ghost_hood(24.5, 7.0, 10.5, 19.0)
    smoke = mask()
    for (x, y, r) in ((16.0, 14.0, 3.6), (14.5, 19.0, 3.0), (17.8, 22.0, 2.6), (16.2, 9.0, 2.4), (13.0, 25.5, 2.0), (19.5, 26.5, 1.8)):
        disc(smoke, x, y, r)
    shards = mask()
    for (x, y, a) in ((10.5, 5.0, 20), (21.5, 4.0, -30), (5.0, 28.5, 60), (27.5, 28.0, -50), (16.0, 30.0, 10)):
        r = math.radians(a)
        poly(shards, [(x + math.cos(r) * 2.0, y + math.sin(r) * 2.0), (x + math.cos(r + 2.3) * 1.4, y + math.sin(r + 2.3) * 1.4),
                      (x + math.cos(r + 4.0) * 1.6, y + math.sin(r + 4.0) * 1.6)])
    eyes = mask()
    for cx in (7.5, 24.5):
        disc(eyes, cx - 1.1, 11.6, 0.7)
        disc(eyes, cx + 1.2, 11.6, 0.7)
    sp = mask()
    star(sp, 16.0, 14.0, 4.6, 0.18, 30)
    subject = stack(layer(lb, mix(B, TEAL, 0.25), mix(B, (0, 0, 0), 0.35), TEAL, None), layer(lf, mix(B, (0, 0, 0), 0.65), mix(B, (0, 0, 0), 0.8)),
                    layer(rb, mix(B, TEAL, 0.25), mix(B, (0, 0, 0), 0.35), TEAL, None), layer(rf, mix(B, (0, 0, 0), 0.65), mix(B, (0, 0, 0), 0.8)),
                    layer(eyes, WHITE, TEAL), layer(smoke, mix(A, WHITE, 0.25), mix(A, B, 0.5)), layer(shards, mix(TEAL, WHITE, 0.5), TEAL, WHITE, TEAL_LO),
                    layer(sp, WHITE, A))
    return finish(subject, [glow(lighter(lb, rb), TEAL, 1.6, 0.8), glow(smoke, A, 1.8, 0.9)], trick_back(A, B))


def trickster_coil(A, B):
    # 요르문간드의 똬리: 세로로 선 8 자로 감긴 뱀 (자홍 비늘 · 위쪽 머리가 아래 꼬리를 노림) · 머리 곁 독 단검 · 떨어지는 초록 독 방울
    pts = []
    for i in range(97):
        th = -math.pi / 2 + 2 * math.pi * i / 96 * 0.94
        pts.append((15.0 + 2 * 6.2 * math.sin(th) * math.cos(th), 16.5 - 12.5 * math.sin(th)))
    body = mask()
    for i in range(len(pts) - 1):
        w = 1.4 + 1.6 * min(1.0, i / 20.0)
        line(body, [pts[i], pts[i + 1]], w)
    scales = mask()
    for i in range(6, len(pts) - 4, 5):
        x, y = pts[i]
        disc(scales, x, y, 0.55)
    hx, hy = pts[-1]
    px, py = pts[-4]
    a = math.atan2(hy - py, hx - px)
    ux, uy = math.cos(a), math.sin(a)
    head = mask()
    poly(head, [(hx + ux * 3.4, hy + uy * 3.4), (hx - uy * 2.2, hy + ux * 2.2), (hx - ux * 1.6, hy - uy * 1.6), (hx + uy * 2.2, hy - ux * 2.2)])
    eye = mask()
    disc(eye, hx + ux * 0.8 - uy * 0.9, hy + uy * 0.8 + ux * 0.9, 0.6)
    dag = sword_layers((30.5, 2.0), (21.0, 11.5), blade=2.6, guard=5.4, blade_cols=(STEEL_HI, mix(STEEL, VENOM, 0.35), WHITE, STEEL_LO))
    drops = mask()
    for (cx, cy, r) in ((27.5, 13.5, 1.3), (24.5, 16.0, 1.05), (29.5, 18.0, 0.9)):
        disc(drops, cx, cy + r * 0.35, r)
        poly(drops, [(cx - r * 0.82, cy + r * 0.1), (cx, cy - r * 1.8), (cx + r * 0.82, cy + r * 0.1)])
    subject = stack(layer(body, mix(A, WHITE, 0.25), mix(B, A, 0.35), WHITE, mix(B, (0, 0, 0), 0.4), axis=(0.6, 0.8)), layer(scales, mix(A, WHITE, 0.55), A),
                    layer(head, mix(A, WHITE, 0.3), mix(B, A, 0.4), WHITE, mix(B, (0, 0, 0), 0.4)), layer(eye, VENOM_HI, VENOM), *dag,
                    layer(drops, VENOM_HI, VENOM, WHITE, mix(VENOM, (0, 0, 0), 0.4)))
    return finish(subject, [glow(body, A, 1.6, 0.9), glow(drops, VENOM, 1.2, 0.9)], trick_back(A, B))


def trickster_night(A, B):
    # 끝없는 밤: 두건 쓴 몸을 휘감아 도는 망토 소용돌이 (자홍 테 — 끝이 가늘어짐) · 빛나는 눈 · 둘레를 도는 꽃잎 · 위 작은 초승달 (밤)
    body, face = _hood(16.0, 6.0, 14.0, 21.0)
    swirl = mask()
    pts = []
    for i in range(49):
        t = i / 48
        a = math.radians(150 + 400 * t)
        rr = 13.0 - 4.5 * t
        pts.append((16.0 + math.cos(a) * rr, 17.5 + math.sin(a) * rr * 0.8))
    for i in range(len(pts) - 1):
        line(swirl, [pts[i], pts[i + 1]], 3.4 * (1 - i / len(pts)) + 0.6)
    eyes = mask()
    disc(eyes, 14.4, 12.6, 1.0)
    disc(eyes, 17.8, 12.6, 1.0)
    moon = mask()
    disc(moon, 26.5, 5.5, 3.6)
    cut = mask()
    disc(cut, 28.0, 4.4, 3.2)
    moon = ImageChops.subtract(moon, cut)
    pt = mask()
    for (x, y, a) in ((4.5, 9.0, 60), (27.5, 22.0, 150), (6.0, 26.5, 20)):
        _petal(pt, x, y, a, 2.8, 1.1)
    subject = stack(layer(moon, mix(A, WHITE, 0.55), A), layer(body, mix(B, A, 0.25), mix(B, (0, 0, 0), 0.5), mix(B, A, 0.55), None),
                    layer(face, mix(B, (0, 0, 0), 0.75), mix(B, (0, 0, 0), 0.85)), layer(swirl, mix(A, WHITE, 0.3), mix(B, A, 0.45), WHITE, mix(B, (0, 0, 0), 0.4),
                    axis=(1, 1)), layer(eyes, WHITE, mix(A, WHITE, 0.5)), layer(pt, mix(A, WHITE, 0.4), A))
    return finish(subject, [glow(swirl, A, 1.8, 0.9), glow(eyes, A, 1.2, 1.0), glow(moon, A, 1.4, 0.7)], trick_back(A, B))


def trickster_ult(A, B):
    # 개기일식: 위 가운데 검은 해 (자홍 코로나 테 · 오른쪽 위 다이아몬드 섬광) · 아래 가운데로 네 쪽에서 날아드는 비수 넷 (청록 테 셋 = 환영) · 맞는 자리 꽃
    sun = mask()
    disc(sun, 16.0, 10.0, 6.2)
    corona = mask()
    ring(corona, 16.0, 10.0, 7.6, 1.6)
    flash = mask()
    star(flash, 21.0, 5.2, 4.4, 0.16, 8)
    knives = []
    tgt = (16.0, 24.5)
    for k, (sx, sy) in enumerate(((3.0, 17.0), (29.0, 17.0), (5.0, 31.0), (27.0, 31.5))):
        a = math.atan2(tgt[1] - sy, tgt[0] - sx)
        tip = (tgt[0] - math.cos(a) * 3.0, tgt[1] - math.sin(a) * 3.0)
        end = (tip[0] - math.cos(a) * 9.0, tip[1] - math.sin(a) * 9.0)
        cols = (STEEL_HI, mix(STEEL, A, 0.25), WHITE, STEEL_LO) if k == 0 else (mix(TEAL, WHITE, 0.5), TEAL, WHITE, TEAL_LO)
        knives += sword_layers(tip, end, blade=1.8, guard=3.6, blade_cols=cols)
    bloom = mask()
    _blossom(bloom, 16.0, 24.5, 3.6, 0)
    ctr = mask()
    disc(ctr, 16.0, 24.5, 1.0)
    subject = stack(layer(corona, mix(A, WHITE, 0.4), A, WHITE, mix(B, (0, 0, 0), 0.3)), layer(sun, mix(B, (0, 0, 0), 0.55), mix(B, (0, 0, 0), 0.8), None, None),
                    *knives, layer(bloom, mix(A, WHITE, 0.45), A, WHITE, mix(B, (0, 0, 0), 0.2)), layer(ctr, WHITE, A), layer(flash, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(corona, A, 2.0, 1.0), glow(flash, WHITE, 1.4, 0.9), glow(bloom, A, 1.4, 0.8)], trick_back(A, B))


# ------------------------------------------------------------ 료스알프(블레이드 댄서) (빛: 흰 금빛 → 연분홍 — 연보라 황혼 바탕)

def dancer_back(A, B):
    return backdrop((24, 12, 38), (112, 70, 122))


def dancer_dagger(tip, end, A, B, blade=2.2, guard=4.6):
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(WHITE, mix(A, WHITE, 0.4), WHITE, mix(B, (90, 60, 90), 0.5)),
                        guard_cols=(mix(A, WHITE, 0.3), A, WHITE, mix(A, (90, 60, 30), 0.5)))


def bladedancer_ray(A, B):
    # 빛살 베기: 몸을 돌며 그은 둥근 빛 소용돌이 (두 초승달 — 연분홍 · 흰 금빛) · 앞 (오른쪽 위) 으로 크게 그은 초승달 · 흩날리는 빛 꽃잎 · 반짝임
    c1 = mask()
    crescent(c1, 13.0, 18.0, 9.5, 2.6, 160, 330)
    c2 = mask()
    crescent(c2, 13.0, 18.0, 9.5, 2.6, -20, 150)
    big = mask()
    crescent(big, 6.0, 30.0, 24.0, 3.8, -82, -8)
    pt = mask()
    for (x, y, a) in ((26.0, 25.5, 30), (29.0, 18.0, 100), (22.0, 30.0, 60), (4.5, 5.0, 200)):
        _petal(pt, x, y, a, 3.2, 1.3)
    sp = mask()
    star(sp, 27.5, 5.5, 3.6, 0.18, 10)
    star(sp, 13.0, 18.0, 2.6, 0.2, 0)
    subject = stack(layer(c1, mix(B, WHITE, 0.3), B, WHITE, mix(B, (80, 30, 70), 0.4), axis=(-1, 1)), layer(c2, mix(A, WHITE, 0.4), A, WHITE, mix(A, (90, 60, 30), 0.4),
                    axis=(1, -1)), layer(big, WHITE, mix(B, A, 0.4), WHITE, mix(B, (80, 30, 70), 0.3), axis=(1, 1)), layer(pt, mix(B, WHITE, 0.45), B), layer(sp, WHITE, A))
    return finish(subject, [glow(lighter(c1, c2), B, 1.6, 0.9), glow(big, A, 1.8, 1.0)], dancer_back(A, B))


def slash_mark(m, x, y, r, w, mid, span=50):
    """(x, y) 를 지나는 베기 자국 (가운데 각 mid 도 쪽으로 불룩 — 화면 좌표, 0 = 오른쪽 · 90 = 아래)"""
    a = math.radians(mid)
    crescent(m, x - r * math.cos(a), y - r * math.sin(a), r, w, mid - span, mid + span)
    return m


def bladedancer_step(A, B):
    # 요정의 걸음: 왼쪽 · 오른쪽 · 가운데로 디딘 세 걸음 — 걸음마다 번갈아 기운 빛 베기 자국 (뒤 둘은 흐리고 앞 가운데는 밝게) · 걸음을 잇는 점선 길 · 디딘 자리 반짝임
    layers = []
    glows = []
    for k, (x, y, r, w, mid, al) in enumerate(((8.0, 11.5, 8.0, 2.2, -35, 0.45), (24.0, 11.0, 8.0, 2.2, 215, 0.7), (16.0, 18.5, 10.5, 3.2, -62, 1.0))):
        cut = mask()
        slash_mark(cut, x, y, r, w, mid)
        top = mix(WHITE, (70, 46, 80), 1 - al)
        bot = mix(mix(B, A, 0.35 * k), (70, 46, 80), 1 - al)
        layers.append(layer(cut, top, bot, WHITE if k == 2 else None, None, axis=(1, -1) if k != 1 else (-1, -1)))
        if k == 2:
            glows.append(glow(cut, B, 1.8, 1.0))
    path = mask()
    pts = [(8.0, 27.5), (12.0, 25.5), (16.0, 27.5), (20.0, 25.5), (24.0, 27.5)]
    for a, b in zip(pts, pts[1:]):
        for t in (0.1, 0.55):
            line(path, [(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), (a[0] + (b[0] - a[0]) * (t + 0.3), a[1] + (b[1] - a[1]) * (t + 0.3))], 0.9)
    sp = mask()
    for (x, y, r) in ((8.0, 27.5, 1.8), (24.0, 27.5, 2.2), (16.0, 27.5, 3.0)):
        star(sp, x, y, r, 0.22, 0)
    pt = mask()
    for (x, y, a) in ((3.5, 22.0, 200), (29.0, 21.5, -20)):
        _petal(pt, x, y, a, 2.8, 1.1)
    subject = stack(layer(path, mix(B, WHITE, 0.3), B), *layers, layer(pt, mix(B, WHITE, 0.45), B), layer(sp, WHITE, A))
    glows.append(glow(sp, A, 1.4, 0.9))
    return finish(subject, glows, dancer_back(A, B))


def bladedancer_waltz(A, B):
    # 칼날 원무: 가운데를 둘러 바깥을 겨눈 빛 단검 여섯 (도는 고리) · 그 사이를 잇는 빛 고리 · 가운데 반짝임 · 둘레 빛 꽃잎
    ring_m = mask()
    ring(ring_m, 16.0, 16.0, 7.6, 1.3)
    layers = []
    for i in range(6):
        a = math.radians(i * 60 - 75)
        tip = (16.0 + math.cos(a) * 14.5, 16.0 + math.sin(a) * 14.5)
        end = (16.0 + math.cos(a) * 5.0, 16.0 + math.sin(a) * 5.0)
        layers += dancer_dagger(tip, end, A, B, blade=2.0, guard=3.8)
    sp = mask()
    star(sp, 16.0, 16.0, 4.8, 0.18, 15)
    pt = mask()
    for (x, y, a) in ((4.0, 4.0, 45), (28.0, 28.0, 225), (28.5, 5.0, 135)):
        _petal(pt, x, y, a, 2.8, 1.1)
    subject = stack(layer(ring_m, mix(B, WHITE, 0.4), B, WHITE, None), *layers, layer(pt, mix(B, WHITE, 0.45), B), layer(sp, WHITE, A))
    return finish(subject, [glow(ring_m, B, 1.8, 0.9), glow(sp, A, 1.6, 1.0)], dancer_back(A, B))


def bladedancer_noon(A, B):
    # 눈부신 한낮: 등 뒤로 펼친 요정 날개 두 쌍 (비치는 연분홍 막 · 흰 테 · 날개맥) · 가운데 한낮의 해 (흰 금빛 네 뿔 별) · 떨어지는 빛 가루
    wings = mask()
    veins = mask()
    for sg in (-1, 1):
        up = [(16.0, 17.0), (16.0 + sg * 4.0, 9.0), (16.0 + sg * 10.0, 3.0), (16.0 + sg * 14.5, 2.5), (16.0 + sg * 14.0, 7.5), (16.0 + sg * 8.0, 14.0)]
        lo = [(16.0, 17.5), (16.0 + sg * 7.0, 20.0), (16.0 + sg * 11.5, 26.0), (16.0 + sg * 9.0, 28.5), (16.0 + sg * 4.0, 23.0)]
        poly(wings, up)
        poly(wings, lo)
        line(veins, [(16.0, 17.0), (16.0 + sg * 12.5, 4.5)], 0.6)
        line(veins, [(16.0, 17.0), (16.0 + sg * 10.5, 9.5)], 0.6)
        line(veins, [(16.0, 17.5), (16.0 + sg * 9.5, 26.0)], 0.6)
    sun = mask()
    disc(sun, 16.0, 16.5, 3.4)
    sp = mask()
    star(sp, 16.0, 16.5, 7.5, 0.14, 0)
    dust = mask()
    for (x, y, r) in ((7.0, 29.0, 1.2), (25.0, 30.0, 1.4), (12.0, 30.5, 0.9), (21.0, 28.0, 1.0)):
        star(dust, x, y, r * 1.6, 0.25, 0)
    subject = stack(layer(wings, mix(B, WHITE, 0.45), mix(B, (90, 50, 100), 0.35), WHITE, None, axis=(0, 1)), layer(veins, WHITE, mix(B, WHITE, 0.5)),
                    layer(sun, WHITE, A), layer(sp, WHITE, A), layer(dust, WHITE, A))
    return finish(subject, [glow(wings, B, 1.8, 0.8), glow(lighter(sun, sp), A, 2.2, 1.0)], dancer_back(A, B))


def bladedancer_ult(A, B):
    # 빛무리 춤: 가운데 활짝 핀 큰 빛 꽃 (겹꽃잎 — 연분홍 · 흰 금빛 가운데) · 꽃을 향해 여덟 쪽에서 모여드는 빛 줄 (오가며 벤 자국) · 반짝임
    lines_m = mask()
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        line(lines_m, [(16.0 + math.cos(a) * 15.5, 16.0 + math.sin(a) * 15.5), (16.0 + math.cos(a) * 9.5, 16.0 + math.sin(a) * 9.5)], 1.2)
    outer = mask()
    for i in range(6):
        _petal(outer, 16.0, 16.0, i * 60 - 90, 9.0, 3.8)
    inner = mask()
    for i in range(6):
        _petal(inner, 16.0, 16.0, i * 60 - 60, 5.8, 2.6)
    ctr = mask()
    disc(ctr, 16.0, 16.0, 1.8)
    sp = mask()
    star(sp, 16.0, 16.0, 5.0, 0.16, 0)
    star(sp, 4.5, 5.0, 1.8, 0.22, 0)
    star(sp, 27.5, 27.0, 2.0, 0.22, 0)
    subject = stack(layer(lines_m, WHITE, A, None, None), layer(outer, mix(B, WHITE, 0.35), B, WHITE, mix(B, (80, 30, 70), 0.35)),
                    layer(inner, mix(A, WHITE, 0.45), mix(A, B, 0.4), WHITE, None), layer(ctr, WHITE, A), layer(sp, WHITE, A))
    return finish(subject, [glow(outer, B, 2.0, 1.0), glow(lines_m, A, 1.4, 0.8), glow(sp, A, 1.6, 1.0)], dancer_back(A, B))


ICONS = {
    "swordmaster_draw": swordmaster_draw,
    "swordmaster_parry": swordmaster_parry,
    "swordmaster_ring": swordmaster_ring,
    "swordmaster_blood": swordmaster_blood,
    "swordmaster_ult": swordmaster_ult,
    "bloodknight_cleave": bloodknight_cleave,
    "bloodknight_leap": bloodknight_leap,
    "bloodknight_chains": bloodknight_chains,
    "bloodknight_frenzy": bloodknight_frenzy,
    "bloodknight_ult": bloodknight_ult,
    "warlord_rend": warlord_rend,
    "warlord_slumber": warlord_slumber,
    "warlord_hug": warlord_hug,
    "warlord_titan": warlord_titan,
    "warlord_ult": warlord_ult,
    "gladiator_flurry": gladiator_flurry,
    "gladiator_hook": gladiator_hook,
    "gladiator_eight": gladiator_eight,
    "gladiator_fury": gladiator_fury,
    "gladiator_ult": gladiator_ult,
    "runeknight_anvil": runeknight_anvil,
    "runeknight_bellows": runeknight_bellows,
    "runeknight_furnace": runeknight_furnace,
    "runeknight_temper": runeknight_temper,
    "runeknight_ult": runeknight_ult,
    "swordcaller_strike": swordcaller_strike,
    "swordcaller_shift": swordcaller_shift,
    "swordcaller_orbit": swordcaller_orbit,
    "swordcaller_twin": swordcaller_twin,
    "swordcaller_ult": swordcaller_ult,
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
    "trickster_rose": trickster_rose,
    "trickster_mirage": trickster_mirage,
    "trickster_coil": trickster_coil,
    "trickster_night": trickster_night,
    "trickster_ult": trickster_ult,
    "bladedancer_ray": bladedancer_ray,
    "bladedancer_step": bladedancer_step,
    "bladedancer_waltz": bladedancer_waltz,
    "bladedancer_noon": bladedancer_noon,
    "bladedancer_ult": bladedancer_ult,
}
