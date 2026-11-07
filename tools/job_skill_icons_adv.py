#!/usr/bin/env python3
"""0.51.0 2차 직업 스킬 아이콘 — 검사 각성 소드마스터 · 반전 블러드 나이트 · 전사 각성 워로드 · 반전 프로스트 레이더 (2026-11-07 재설계안 3판 — 글래디에이터 대신) ·
마검사 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신) · 반전 소드 콜러 (재설계안 2판) · 매지션 각성 아크메이지 · 반전 네크로맨서 ·
궁수 각성 스나이퍼 · 반전 배틀 아처 (재설계안 2판 — 섀도 헌터 대신) · 암살자 각성 트릭스터 (2026-11-06 2판) · 반전 피에로 (블레이드 댄서 대신) · 창술사 각성 스톰 랜서 · 반전 드라군 · 사신 각성 소울 리퍼 · 반전 엑시큐셔너 (메이플식 네모 스킬 그림 32×32, tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름).
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음 — 룬 글자는 게임 그림 rfx_jglyph 와 같은 꼴, 아케인 블레이드의 검기는 rfx_jwave 와 같은 꼴).
색: 소드마스터 = 금빛 (첫째) · 은빛 (둘째), 블러드 나이트 = 짙은 피 (첫째) · 밝은 피 (둘째), 워로드 = 주황 (첫째) · 갈색 (둘째),
프로스트 레이더 = 얼음빛 (첫째) · 서리 흰빛 (둘째 — 얼어붙은 극야 바탕 · 도끼 자루는 짙은 남빛 쇠), 아케인 블레이드 = 보라 (첫째) · 청록 (둘째 — 짙은 남보라 밤 바탕),
소드 콜러 = 청록 (첫째) · 흰 청록 (둘째 — 짙은 청록 밤 바탕), 아크메이지 = 별빛 파랑 (첫째) · 금빛 (둘째 — 밤하늘 자주 바탕),
네크로맨서 = 독초록 (첫째) · 뼈색 (둘째 — 짙은 녹색 안개 바탕), 스나이퍼 = 밝은 연두 (첫째) · 금빛 흰 섬광 (둘째 — 짙은 숲 바탕),
배틀 아처 = 폭풍 하늘빛 (첫째) · 번개 노랑 (둘째 — 짙은 폭풍 남청 바탕), 트릭스터 = 자홍 (첫째) · 검보라 (둘째 — 짙은 보라 바탕 · 환영은 청록 테 · 들불은 분홍),
피에로 = 빨강 (첫째) · 크림 금빛 (둘째 — 서커스 밤 바탕 · 할리퀸 마름모 무늬 · 금방울), 스톰 랜서 = 옥빛 바람 (첫째) · 흰빛 (둘째 — 짙은 옥빛 폭풍 바탕),
드라군 = 붉은 용 진홍 (첫째) · 불꽃 주황 (둘째 — 2026-11-06 사용자 "빨간 레드 드레곤 느낌에 화염" · 짙은 숯불 밤 바탕 · 불은 주홍),
소울 리퍼 = 영혼 하늘빛 (첫째) · 짙은 남청 (둘째 — 짙은 남청 밤 바탕 · 혼불은 흰 하늘빛), 엑시큐셔너 = 해 질 녘 금빛 (첫째) · 짙은 보라 (둘째 — 해 진 뒤의
자줏빛 바탕) — jobs.json advance."""
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
from job_skill_icons_three import hood as _hood  # noqa: E402 — 암살자 두건 (0.51.0 암살자 2차)
from job_skill_icons_three import spear as _spear  # noqa: E402 — 창 (0.51.0 창술사 2차)
from job_skill_icons_three import scythe as _scythe, soul as _soul  # noqa: E402 — 낫 · 혼불 (0.51.0 사신 2차)

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


# ------------------------------------------------------------ 소드마스터 (빛: 금빛 → 은빛) — 0.51.0 재설계안 2판

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


def swordmaster_echo(A, B):
    # 블레이드 에코 (2026-11-07 3판 — 인페르노 링 대신): 가운데 아래 칼집에 막 넣은 칼 (금빛 코등이 · 어귀의 딸깍 섬광) · 둘레 허공에 걸린 가는 칼자국 일곱
    # (여러 쪽 — 가운데가 갈라지며 빛남) · 칼자국마다 작은 반짝임
    cuts, cuts_hi = mask(), mask()
    for (x0, y0), (x1, y1) in (((3.0, 5.5), (13.5, 10.0)), ((18.5, 3.0), (29.0, 9.5)), ((2.5, 17.0), (10.5, 12.0)), ((22.0, 13.5), (30.0, 18.5)),
                               ((4.0, 24.5), (11.5, 28.5)), ((21.0, 27.5), (29.5, 22.5)), ((12.0, 2.5), (20.0, 2.0))):
        line(cuts, [(x0, y0), (x1, y1)], 1.0)
        line(cuts_hi, [((x0 * 2 + x1) / 3, (y0 * 2 + y1) / 3), ((x0 + x1 * 2) / 3, (y0 + y1 * 2) / 3)], 0.5)
    sheath = mask()
    line(sheath, [(9.0, 30.0), (17.6, 17.4)], 3.4)
    fit = mask()
    disc(fit, 17.8, 17.1, 2.0)
    sw = gold_sword((9.4, 29.4), (21.6, 11.6), blade=2.2, guard=7.0)
    sp = mask()
    star(sp, 18.0, 16.8, 5.6, 0.15, 45)
    for x, y in ((8.2, 7.8), (23.8, 6.2), (6.5, 14.5), (26.0, 16.0), (7.8, 26.5), (25.2, 25.0), (16.0, 2.2)):
        star(sp, x, y, 1.5, 0.24)
    subject = stack(layer(cuts, WHITE, mix(A, WHITE, 0.35), None, None), layer(cuts_hi, WHITE, WHITE),
                    layer(sheath, mix(LACQUER, A, 0.25), LACQUER, mix(LACQUER, WHITE, 0.3), mix(LACQUER, (0, 0, 0), 0.4)), layer(fit, GOLD_HI, GOLD, WHITE, GOLD_LO),
                    *sw, layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(cuts, sp), A, 1.7, 1.0)], backdrop(mix(A, NIGHT, 0.86), mix(A, (60, 40, 10), 0.45)))


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
    # 엑스칼리버 (2026-11-07 3판 — 스타폴 블레이드 대신: 사용자 "엑스칼리버 같은 느낌으로 내리꽂는 커다란 검격"): 아래 가운데 금빛 코등이에서 위로 하늘을 찌르는
    # 거대한 빛의 칼날 (흰 속 · 금빛 테 · 넓은 빛무리) · 칼날에서 퍼지는 빛살 · 칼끝의 큰 별 · 아래로 깔리는 빛의 검격 호
    blade = mask()
    poly(blade, [(13.6, 25.0), (13.9, 6.0), (16.0, 1.4), (18.1, 6.0), (18.4, 25.0)])
    core = mask()
    poly(core, [(15.2, 24.6), (15.4, 7.0), (16.0, 3.6), (16.6, 7.0), (16.8, 24.6)])
    rays = mask()
    for ang in (-62, -38, -16, 16, 38, 62, 200, 340):
        a = math.radians(ang - 90)
        line(rays, [(16.0 + math.cos(a) * 5.0, 12.0 + math.sin(a) * 5.0), (16.0 + math.cos(a) * 14.0, 12.0 + math.sin(a) * 14.0)], 0.6)
    arc = mask()
    crescent(arc, 16.0, 36.0, 13.0, 2.4, 205, 335)
    guard = mask()
    line(guard, [(9.6, 25.6), (22.4, 25.6)], 2.0)
    grip = mask()
    line(grip, [(16.0, 26.4), (16.0, 30.4)], 2.0)
    pommel = mask()
    disc(pommel, 16.0, 30.6, 1.3)
    sp = mask()
    star(sp, 16.0, 2.6, 5.2, 0.14, 0)
    star(sp, 16.0, 25.4, 3.4, 0.2, 45)
    for x, y in ((6.0, 6.0), (26.4, 8.0), (4.4, 19.0), (27.6, 18.4)):
        star(sp, x, y, 1.3, 0.24)
    subject = stack(layer(arc, GOLD_HI, A, WHITE, None), layer(rays, mix(A, WHITE, 0.5), A), layer(blade, mix(A, WHITE, 0.55), A, WHITE, GOLD_LO, axis=(1, 0)),
                    layer(core, WHITE, WHITE), layer(guard, GOLD_HI, GOLD, WHITE, GOLD_LO), layer(grip, (110, 70, 30), (60, 34, 14)),
                    layer(pommel, GOLD_HI, GOLD, WHITE, GOLD_LO), layer(sp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(blade, rays), A, 2.4, 1.0), glow(arc, A, 1.4, 0.8)], backdrop(mix(A, (14, 18, 52), 0.8), mix(A, (90, 70, 20), 0.5)))


# ------------------------------------------------------------ 블러드 나이트 (빛: 짙은 피 → 밝은 피) — 0.51.0 재설계안 2판

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
    # 블러드 문: 위의 거대한 핏빛 달 (어두운 바다 · 붉게 타는 테 · 둥근 빛무리) · 아래 둘레의 적에게서 달로 빨려 오르는 핏줄기 셋 (위로 오르는 핏방울) · 땅을
    # 가로지르는 거대한 핏빛 초승달 검기 (뛰어올라 내려찍음 — 2026-11-05 사용자 "검사 궁 2갸 다른걸로 바꾸자 멋이 안난다")
    halo = mask()
    disc(halo, 16.0, 11.0, 11.6)
    moon = mask()
    disc(moon, 16.0, 11.0, 8.6)
    maria = mask()
    for cx, cy, r in ((13.0, 8.6, 2.6), (18.8, 13.6, 2.2), (19.4, 7.4, 1.4), (12.4, 14.2, 1.2)):
        disc(maria, cx, cy, r)
    rim = mask()
    ring(rim, 16.0, 11.0, 8.8, 1.3)
    streams = mask()
    line(streams, [(3.6, 29.0), (7.0, 23.6), (10.4, 18.6)], 0.8)
    line(streams, [(28.4, 29.0), (25.0, 23.6), (21.6, 18.6)], 0.8)
    line(streams, [(16.0, 30.6), (16.0, 20.4)], 0.8)
    drops = mask()
    drop(drops, 6.4, 24.6, 1.3, 122)
    drop(drops, 25.6, 24.6, 1.3, 58)
    drop(drops, 16.0, 23.6, 1.3, 90)
    wave = mask()
    crescent(wave, 16.0, 40.0, 15.0, 4.2, 214, 326)
    wave_hi = mask()
    crescent(wave_hi, 16.0, 40.0, 15.2, 1.3, 222, 318)
    dark = mix(A, (0, 0, 0), 0.45)
    subject = stack(layer(moon, mix(A, B, 0.5), mix(A, (0, 0, 0), 0.2), None, None, axis=(0.6, 0.8)),
                    layer(maria, mix(A, (0, 0, 0), 0.3), mix(A, (0, 0, 0), 0.5)), layer(rim, mix(B, WHITE, 0.5), B),
                    layer(streams, mix(B, WHITE, 0.2), A), layer(drops, mix(B, WHITE, 0.25), A, mix(B, WHITE, 0.6), dark),
                    layer(wave, mix(B, WHITE, 0.35), A, mix(B, WHITE, 0.65), dark, axis=(1, 0)), layer(wave_hi, WHITE, mix(B, WHITE, 0.5)))
    return finish(subject, [glow(halo, B, 2.6, 0.75), glow(rim, B, 1.4, 1.0), glow(lighter(wave, streams), B, 1.6, 0.9)],
                  backdrop(mix(A, DUSK, 0.8), mix(A, (90, 12, 16), 0.45)))


# ------------------------------------------------------------ 워로드 (빛: 주황 → 갈색 곰 기운)

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


def warlord_stomp(A, B):
    # 베어 스톰프: 하늘에서 내리찍는 거대한 곰 발자국 (빛나는 곰의 혼 — 발바닥 볼록 · 발가락 넷 · 흰 발톱) · 위로 흐르는 바람 줄 (내려치는 중) · 발밑 땅의 번쩍임 ·
    # 부채꼴로 갈라지는 땅 · 양옆에서 기울어 솟는 돌창 · 땅의 충격 고리 (롤 볼리베어 R — 2026-11-05 거인의 무게 대신)
    pad = mask()
    poly(pad, [(9.6, 16.2), (12.0, 12.4), (16.0, 11.0), (20.0, 12.4), (22.4, 16.2), (21.6, 19.6), (18.6, 21.0), (16.0, 19.8), (13.4, 21.0), (10.4, 19.6)])
    toes = mask()
    claws = mask()
    for cx, cy, rx, ry, ang in ((8.4, 9.4, 2.2, 2.8, -28), (13.0, 5.8, 2.3, 3.0, -9), (19.0, 5.8, 2.3, 3.0, 9), (23.6, 9.4, 2.2, 2.8, 28)):
        pts = []
        for i in range(20):
            t = math.radians(i * 18)
            x, y = math.cos(t) * rx, math.sin(t) * ry
            a = math.radians(ang)
            pts.append((cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)))
        poly(toes, pts)
        a = math.radians(ang - 90)
        bx, by = cx + math.cos(a) * (ry - 0.2), cy + math.sin(a) * (ry - 0.2)
        px, py = -math.sin(a), math.cos(a)
        poly(claws, [(bx + px * 1.0, by + py * 1.0), (bx + math.cos(a) * 3.4, by + math.sin(a) * 3.4), (bx - px * 1.0, by - py * 1.0)])
    wind = mask()
    for x, y0, y1 in ((4.0, 1.0, 7.0), (2.4, 12.0, 18.0), (28.0, 1.0, 7.0), (29.6, 12.0, 18.0), (16.0, 0.0, 3.4)):
        poly(wind, [(x - 0.55, y0), (x + 0.55, y0), (x, y1)])
    gnd = ground(25.4)
    cracks = mask()
    line(cracks, [(16.0, 25.4), (10.4, 27.6), (4.0, 30.6)], 1.0)
    line(cracks, [(16.0, 25.4), (21.6, 27.6), (28.0, 30.6)], 1.0)
    line(cracks, [(16.0, 25.4), (16.8, 28.2), (15.6, 31.0)], 0.9)
    spikes = mask()
    for bx, h, lean, w in ((4.0, 8.0, -2.0, 4.0), (28.0, 8.0, 2.0, 4.0)):
        rock_spike(spikes, bx, 26.0, h, lean, w)
    shock = mask()
    oval(shock, 16.0, 25.8, 13.6, 3.0, 0.9)
    clash = mask()
    star(clash, 16.0, 24.6, 5.4, 0.16, 0)
    deep = mix(A, (60, 20, 0), 0.35)
    subject = stack(ground_layer(gnd, B), layer(shock, mix(A, WHITE, 0.35), A), layer(cracks, WHITE, A), rock_layer(spikes, A),
                    layer(wind, mix(A, WHITE, 0.4), A), layer(pad, mix(A, WHITE, 0.45), deep, WHITE, mix(deep, (0, 0, 0), 0.4), axis=(0.3, 0.95)),
                    layer(toes, mix(A, WHITE, 0.45), deep, WHITE, mix(deep, (0, 0, 0), 0.4), axis=(0.3, 0.95)), layer(claws, WHITE, mix(A, WHITE, 0.5)),
                    layer(clash, WHITE, A))
    return finish(subject, [glow(lighter(pad, toes), A, 2.0, 1.0), glow(lighter(clash, cracks, shock), A, 1.6, 0.9)], warlord_back(A, B))


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


# ------------------------------------------------------------ 프로스트 레이더 (빛: 얼음빛 → 서리 흰빛 — 얼어붙은 극야 바탕, 2026-11-07 재설계안 3판 · 글래디에이터 대신)

FROST_NIGHT = (6, 14, 34)
ICE_DEEP = (40, 92, 170)


def frost_back(A, B):
    return backdrop(mix(A, FROST_NIGHT, 0.86), mix(A, (24, 70, 140), 0.45))


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


def ice_axe_masks(head, tail, blade=7.0, side=1):
    """서리 도끼 (턱수염 도끼 — 아이콘 크기에서 날이 또렷하게): tail (자루 끝) → head (자루 위끝). 날 = 자루 위끝 한쪽 (side) 의 큰 초승달 날 (위 뿔 · 아래로 길게
    늘어진 턱 · 바깥으로 불룩한 날 끝), 반대쪽 짧은 가시. 자루 · 날 · 날 끝 테 · 날의 얼음 결 (가운데 가는 줄)"""
    hx, hy = head
    tx, ty = tail
    L = math.hypot(hx - tx, hy - ty)
    ux, uy = (hx - tx) / L, (hy - ty) / L
    px, py = -uy * side, ux * side
    k = blade / 8.0

    def at(u, p):
        return (hx + ux * u * k + px * p * k, hy + uy * u * k + py * p * k)

    handle = mask()
    line(handle, [tail, at(1.4, 0.0)], 1.7)
    bl = mask()
    outer = [at(3.8, 4.6), at(2.6, 5.5), at(0.8, 6.0), at(-1.2, 5.9), at(-3.0, 5.3), at(-4.8, 4.2)]
    poly(bl, [at(1.3, 0.7), at(2.4, 2.0)] + outer + [at(-3.0, 3.0), at(-1.6, 1.6), at(-1.3, 0.7)])
    poly(bl, [at(0.9, -0.5), at(0.0, -2.4), at(-0.9, -0.5)])
    poly(bl, [at(1.4, 0.8), at(1.4, -0.8), at(-1.4, -0.8), at(-1.4, 0.8)])
    edge = mask()
    line(edge, outer, 0.9)
    vein = mask()
    line(vein, [at(2.0, 2.6), at(0.2, 4.2), at(-2.6, 3.8)], 0.5)
    return handle, bl, edge, vein


def ice_axe(head, tail, A, blade=7.0, side=1, ghost=0.0):
    """서리 도끼 (자루 = 짙은 남빛 쇠 · 날 = 얼음빛 — ghost 만큼 깊은 얼음빛 쪽으로 옅게: 거인의 도끼 환영)"""
    handle, bl, edge, vein = ice_axe_masks(head, tail, blade, side)
    fade = (lambda c: mix(c, (34, 78, 150), ghost)) if ghost > 0 else (lambda c: c)
    return [layer(handle, fade((92, 110, 140)), fade((44, 54, 76)), fade((150, 170, 200)), None),
            layer(bl, fade(mix(A, WHITE, 0.45)), fade(mix(A, ICE_DEEP, 0.45)), fade(WHITE), fade(mix(ICE_DEEP, (0, 0, 0), 0.3)), axis=(0.7, 0.7)),
            layer(vein, fade(WHITE), fade(mix(A, WHITE, 0.5))), layer(edge, fade(WHITE), fade(mix(A, WHITE, 0.6)))]


def ice_spike(m, x, base, h, w, lean=0.0):
    """얼음 가시 (밑 너비 w · 높이 h · 끝이 lean 만큼 기욺)"""
    poly(m, [(x - w / 2, base), (x - w * 0.18 + lean * 0.6, base - h * 0.62), (x + lean, base - h), (x + w * 0.22 + lean * 0.6, base - h * 0.6), (x + w / 2, base)])


def snowfall(m, pts):
    for x, y, r in pts:
        star(m, x, y, r, 0.24)


def frostraider_throw(A, B):
    # 프로스트 바이트: 가운데에서 돌며 날아가는 서리 도끼 · 도끼를 둘러싼 회전 궤적 (휜 띠 셋 — 끝이 밝음) · 왼쪽 아래에서 날아온 점선 · 아래로 되돌아오는 화살표 (되부름) ·
    # 눈꽃
    spin = mask()
    for a0 in (-40, 80, 200):
        crescent(spin, 16.6, 13.0, 11.6, 3.0, a0, a0 + 92)
    spin_hi = mask()
    for a0 in (-40, 80, 200):
        crescent(spin_hi, 16.6, 13.0, 11.8, 1.0, a0 + 40, a0 + 92)
    trail = mask()
    for i in range(3):
        t0, t1 = 0.05 + i * 0.3, 0.05 + i * 0.3 + 0.15
        x0, y0 = 1.0 + (6.4 - 1.0) * t0, 31.0 + (25.0 - 31.0) * t0
        x1, y1 = 1.0 + (6.4 - 1.0) * t1, 31.0 + (25.0 - 31.0) * t1
        line(trail, [(x0, y0), (x1, y1)], 1.3)
    back = mask()
    pts = [(16.0 + math.cos(math.radians(a)) * 12.4, 20.6 + math.sin(math.radians(a)) * 7.4) for a in range(20, 161, 6)]
    line(back, pts, 0.9)
    ex, ey = pts[-1]
    poly(back, [(ex - 0.4, ey - 2.4), (ex - 2.6, ey + 0.6), (ex + 1.4, ey + 1.4)])
    axe = ice_axe((21.8, 6.4), (11.0, 19.6), A, blade=8.2, side=1)
    sp = mask()
    snowfall(sp, ((28.6, 4.0, 2.0), (5.0, 4.4, 1.6), (27.4, 28.4, 1.4)))
    subject = stack(layer(spin, mix(B, WHITE, 0.25), mix(A, ICE_DEEP, 0.3), WHITE, mix(ICE_DEEP, (0, 0, 0), 0.3), axis=(1, 0)), layer(spin_hi, WHITE, B),
                    layer(trail, mix(A, WHITE, 0.4), A), layer(back, mix(A, WHITE, 0.5), A), *axe, layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(spin, spin_hi), A, 1.8, 1.0), glow(back, A, 1.2, 0.6), glow(sp, B, 1.2, 0.8)], frost_back(A, B))


def frostraider_leap(A, B):
    # 아이스 바운드: 왼쪽 아래에서 솟은 점선 포물선 (서리 바람을 타고 날아감) · 가운데로 내리찍는 서리 도끼 (날이 아래) · 맞은 자리 섬광 · 둘레로 솟은 얼음 가시 · 땅의 얼음 고리
    arc = mask()
    for i in range(6):
        t0, t1 = i / 6 + 0.02, i / 6 + 0.1
        def pt(t):
            return (2.0 + 12.0 * t, 27.0 - 22.0 * math.sin(math.pi * 0.5 * t))
        line(arc, [pt(t0), pt(t1)], 1.0)
    halo = mask()
    pts = [(16.0 + math.cos(math.radians(a)) * 12.6, 26.6 + math.sin(math.radians(a)) * 3.0) for a in range(0, 361, 8)]
    line(halo, pts, 1.0)
    spikes = mask()
    for x, h, w, lean in ((4.0, 8.0, 4.0, -1.4), (9.2, 11.0, 4.4, -0.8), (23.0, 11.6, 4.6, 0.8), (28.2, 8.6, 4.0, 1.4)):
        ice_spike(spikes, x, 28.0, h, w, lean)
    hit = mask()
    star(hit, 15.0, 25.0, 6.4, 0.16)
    axe = ice_axe((15.4, 16.6), (19.8, 1.6), A, blade=8.6, side=1)
    subject = stack(layer(halo, mix(A, WHITE, 0.4), A), layer(arc, mix(B, WHITE, 0.3), A), layer(hit, WHITE, B),
                    layer(spikes, mix(A, ICE_DEEP, 0.35), mix(ICE_DEEP, (0, 0, 0), 0.35), mix(A, WHITE, 0.4), mix(ICE_DEEP, (0, 0, 0), 0.5), axis=(1, 0)), *axe)
    return finish(subject, [glow(lighter(spikes, halo), A, 1.6, 0.9), glow(hit, B, 2.0, 1.0)], frost_back(A, B))


def frostraider_shatter(A, B):
    # 샤터: 가운데 큰 육각 얼음 결정 (왼 면 짙게 · 오른 면 밝게) 에 금이 감 · 오른쪽에서 가로로 후려친 서리 도끼 · 맞은 자리 섬광 · 왼쪽으로 튀는 얼음 조각
    left_f = mask()
    poly(left_f, [(13.0, 4.0), (13.0, 30.0), (7.0, 25.0), (7.0, 9.0)])
    right_f = mask()
    poly(right_f, [(13.0, 4.0), (19.0, 9.0), (19.0, 25.0), (13.0, 30.0)])
    edge = mask()
    line(edge, [(13.0, 4.0), (19.0, 9.0), (19.0, 25.0), (13.0, 30.0), (7.0, 25.0), (7.0, 9.0), (13.0, 4.0)], 0.8)
    line(edge, [(13.0, 4.0), (13.0, 30.0)], 0.6)
    crack = mask()
    line(crack, [(19.0, 15.0), (16.0, 14.0), (14.4, 11.0), (12.0, 10.0)], 0.7)
    line(crack, [(16.0, 14.0), (14.0, 17.4), (10.6, 18.0), (8.4, 21.0)], 0.7)
    line(crack, [(14.0, 17.4), (15.0, 22.0), (13.4, 26.0)], 0.6)
    shards = mask()
    for x, y, r, a in ((3.0, 8.0, 1.8, 20), (2.4, 15.0, 2.2, -30), (3.6, 22.0, 1.6, 60), (5.6, 29.0, 1.4, 10)):
        poly(shards, [(x + math.cos(math.radians(a)) * r, y + math.sin(math.radians(a)) * r),
                      (x + math.cos(math.radians(a + 140)) * r * 0.8, y + math.sin(math.radians(a + 140)) * r * 0.8),
                      (x + math.cos(math.radians(a + 230)) * r, y + math.sin(math.radians(a + 230)) * r)])
    hit = mask()
    star(hit, 19.6, 15.0, 5.6, 0.16)
    cut = mask()
    slash(cut, 31.0, 13.0, 15.0, 17.0, 1.4)
    axe = ice_axe((22.4, 16.6), (30.6, 28.4), A, blade=7.2, side=-1)
    subject = stack(layer(left_f, mix(A, ICE_DEEP, 0.55), mix(ICE_DEEP, (0, 0, 0), 0.25)), layer(right_f, mix(A, WHITE, 0.45), mix(A, ICE_DEEP, 0.2)),
                    layer(edge, WHITE, mix(A, WHITE, 0.5)), layer(crack, WHITE, WHITE), layer(shards, mix(A, WHITE, 0.6), A, WHITE, ICE_DEEP),
                    layer(cut, WHITE, mix(A, WHITE, 0.4)), *axe, layer(hit, WHITE, B))
    return finish(subject, [glow(lighter(edge, crack), A, 1.6, 0.9), glow(hit, B, 2.0, 1.0), glow(shards, A, 1.0, 0.6)], frost_back(A, B))


def frostraider_jotunn(A, B):
    # 요툰 그립: 뒤에 크게 솟은 서리 거인의 도끼 환영 (옅게 · 크게) 과 같은 꼴로 앞에 든 서리 도끼 (또렷하게) · 발밑 얼음 고리 둘 · 거인의 숨 (눈꽃)
    halo = mask()
    for r, w in ((12.6, 1.0), (8.6, 0.7)):
        pts = [(16.0 + math.cos(math.radians(a)) * r, 27.0 + math.sin(math.radians(a)) * r * 0.24) for a in range(0, 361, 8)]
        line(halo, pts, w)
    ghost = ice_axe((20.6, 6.6), (3.0, 30.0), A, blade=12.0, side=1, ghost=0.55)
    axe = ice_axe((25.4, 15.4), (17.0, 27.6), A, blade=6.6, side=1)
    sp = mask()
    snowfall(sp, ((5.0, 5.0, 1.8), (29.0, 7.0, 1.6), (12.0, 13.0, 1.0)))
    gm = mask()
    for mm in ice_axe_masks((20.6, 6.6), (3.0, 30.0), 12.0, 1):
        gm = lighter(gm, mm)
    subject = stack(*ghost, layer(halo, mix(A, WHITE, 0.4), A), *axe, layer(sp, WHITE, B))
    return finish(subject, [glow(gm, A, 2.6, 0.7), glow(halo, A, 1.4, 0.8), glow(sp, B, 1.2, 0.8)], frost_back(A, B))


def frostraider_ult(A, B):
    # 핌불윈터: 하늘에서 거꾸로 내리꽂히는 거대한 빙하 도끼 (날이 아래 — 가운데 위) · 아래 솟아 가둔 빙하 (얼음 기둥 다섯) · 비스듬히 내리는 눈보라 줄 · 맞은 자리 섬광
    storm = mask()
    for x, y in ((3.0, 3.0), (8.0, 9.0), (26.0, 2.0), (29.0, 10.0), (4.0, 15.0), (27.0, 17.0)):
        line(storm, [(x, y), (x - 2.2, y + 3.4)], 0.6)
    glacier = mask()
    for x, h, w, lean in ((3.4, 9.0, 5.0, -1.2), (8.0, 6.6, 4.6, -0.6), (24.0, 7.0, 4.6, 0.6), (28.6, 9.6, 5.0, 1.2)):
        ice_spike(glacier, x, 31.0, h, w, lean)
    hit = mask()
    star(hit, 16.0, 26.0, 7.0, 0.14)
    axe = ice_axe((16.6, 19.6), (21.0, 0.4), A, blade=10.6, side=1)
    subject = stack(layer(storm, mix(B, WHITE, 0.3), A), layer(hit, WHITE, B),
                    layer(glacier, mix(A, ICE_DEEP, 0.35), mix(ICE_DEEP, (0, 0, 0), 0.35), mix(A, WHITE, 0.4), mix(ICE_DEEP, (0, 0, 0), 0.5), axis=(1, 0)), *axe)
    return finish(subject, [glow(glacier, A, 1.8, 0.9), glow(hit, B, 2.4, 1.0), glow(storm, B, 1.0, 0.5)], frost_back(A, B))


# ------------------------------------------------------------ 아케인 블레이드 (빛: 보라 → 청록 — 짙은 남보라 밤 바탕, 2026-11-05 룬 스미스 대신)

RUNE_NIGHT = (10, 6, 30)


def arcane_back(A, B):
    return backdrop(mix(A, RUNE_NIGHT, 0.84), mix(A, (40, 14, 84), 0.45))


def glyph(m, k, cx, cy, w, h, lw, ang=0.0):
    """룬 글자 k (job_fx_art_v2.GLYPHS — 게임의 rfx_jglyph 와 같은 꼴): (cx, cy) 가운데 · 폭 w · 높이 h · 굵기 lw · ang 도 돌림"""
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
    """마력 칼: 흰 보랏빛 날 · 청록 코등이 (아케인 블레이드)"""
    return sword_layers(tip, end, blade=blade, guard=guard, blade_cols=(WHITE, mix(A, WHITE, 0.55), WHITE, mix(A, (0, 0, 0), 0.35)),
                        guard_cols=(mix(B, WHITE, 0.4), B, WHITE, mix(B, (0, 0, 0), 0.45)))


def tapered(m, pts, w0, w1):
    """가운데 줄 pts 를 따라 굵기 w0 → w1 로 가늘어지는 띠 (검기 · 빛줄기)"""
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


def wave_layers(m_outer, m_edge, A, B, fade=0.0):
    """큰 검기 (게임의 rfx_jwave 와 같은 꼴): 넓은 보랏빛 몸 · 흰 날 (청록 테)"""
    def f(c):
        return mix(c, RUNE_NIGHT, fade)
    return [layer(m_outer, f(mix(A, WHITE, 0.35)), f(A), None, f(mix(A, (0, 0, 0), 0.35))), layer(m_edge, f(WHITE), f(mix(B, WHITE, 0.3)))]


def arcaneblade_crescent(A, B):
    # 크레센트 엣지: 위를 가로지르는 거대한 초승달 검기 (흰 날) · 그 뒤 옅게 선 세로 검기 (둘째) · 왼쪽 아래에서 휘두른 칼 · 검기 앞 끝의 반짝임 · 바람 줄
    back = mask()
    crescent(back, 13.0, 16.0, 12.6, 3.4, -58, 58)
    big = mask()
    crescent(big, 16.0, 30.0, 21.0, 6.4, 222, 318)
    edge = mask()
    crescent(edge, 16.0, 30.0, 21.0, 1.6, 226, 314)
    sw = rune_sword((14.6, 19.4), (3.0, 29.6), A, B, blade=3.0, guard=6.6)
    wind = mask()
    for (x0, y0), (x1, y1) in (((3.0, 16.0), (8.0, 13.0)), ((24.0, 13.0), (29.4, 16.4)), ((19.0, 20.6), (24.6, 21.8))):
        line(wind, [(x0, y0), (x1, y1)], 0.5)
    flash = mask()
    star(flash, 28.6, 14.6, 3.2, 0.2, 10)
    subject = stack(*wave_layers(back, mask(), A, B, fade=0.5), layer(wind, mix(A, WHITE, 0.3), mix(A, RUNE_NIGHT, 0.4)), *wave_layers(big, edge, A, B), *sw,
                    layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(big, flash), A, 1.8, 1.0), glow(edge, B, 1.0, 0.7)], arcane_back(A, B))


def arcaneblade_cross(A, B):
    # 팬텀 크로스: 가운데를 가르는 거대한 X 검기 (두 획 — 흰 날) · 가운데 번쩍임 · 왼쪽 아래에서 꿰뚫고 온 잔상 (갈매기 셋 — 앞이 밝음) · 둘레 룬 고리
    xa = mask()
    tapered(xa, [(4.0, 4.0), (16.0, 16.0), (28.0, 28.0)], 1.0, 1.0)
    tapered(xa, [(28.0, 4.0), (16.0, 16.0), (4.0, 28.0)], 1.0, 1.0)
    xb = mask()
    for (x0, y0), (x1, y1) in (((4.6, 4.6), (27.4, 27.4)), ((27.4, 4.6), (4.6, 27.4))):
        slash(xb, x0, y0, x1, y1, 2.8)
    rring = mask()
    ring(rring, 16.0, 16.0, 9.6, 0.8)
    chev = []
    for i, (x, y) in enumerate(((3.0, 24.0), (6.6, 20.4), (10.2, 16.8))):
        m = mask()
        poly(m, [(x, y + 3.6), (x + 3.4, y + 0.2), (x + 6.8, y + 3.6), (x + 3.4, y + 1.8)])
        chev.append((m, 0.62 - 0.3 * i))
    flash = mask()
    star(flash, 16.0, 16.0, 5.0, 0.2, 45)
    layers_ = [layer(rring, mix(A, WHITE, 0.3), A, None, mix(A, (0, 0, 0), 0.4))]
    for m, fade in chev:
        layers_.append(layer(m, mix(WHITE, RUNE_NIGHT, fade), mix(A, RUNE_NIGHT, fade), None, mix(A, (0, 0, 0), 0.4)))
    subject = stack(*layers_, layer(xb, mix(A, WHITE, 0.35), A, None, mix(A, (0, 0, 0), 0.35)), layer(xa, WHITE, mix(B, WHITE, 0.3)), layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(xb, flash), A, 1.8, 1.0), glow(xa, B, 1.0, 0.7)], arcane_back(A, B))


def arcaneblade_tempest(A, B):
    # 아케인 템페스트: 하늘로 세운 칼 둘레를 휘감아 오르는 초승달 검기 셋 (아래가 크고 위가 작음 — 회오리) · 발밑 룬 고리 · 칼끝의 반짝임
    gnd = mask()
    oval(gnd, 16.0, 27.4, 12.6, 3.4, 1.0)
    swirl = mask()
    ribbon(swirl, 16.0, 21.6, 13.6, 4.6, 20, 200, 2.6, 0.4)
    ribbon(swirl, 16.0, 13.6, 9.6, 3.4, 200, 380, 2.0, 0.3)
    ribbon(swirl, 16.0, 7.4, 6.0, 2.2, 20, 200, 1.4, 0.3)
    back = mask()
    ribbon(back, 16.0, 21.6, 13.6, 4.6, 200, 340, 0.4, 1.0)
    ribbon(back, 16.0, 13.6, 9.6, 3.4, 20, 160, 0.3, 0.8)
    sw = rune_sword((16.0, 2.4), (16.0, 26.0), A, B, blade=3.0, guard=7.0)
    flash = mask()
    star(flash, 16.0, 3.0, 3.4, 0.2, 0)
    subject = stack(layer(gnd, mix(A, WHITE, 0.3), A, None, mix(A, (0, 0, 0), 0.4)), layer(back, mix(A, WHITE, 0.2), mix(A, RUNE_NIGHT, 0.45)), *sw,
                    layer(swirl, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(swirl, flash), A, 1.8, 1.0), glow(gnd, A, 1.2, 0.6)], arcane_back(A, B))


def arcaneblade_rift(A, B):
    # 디멘션 슬래시: 비스듬히 가로지르는 차원의 틈 (찢긴 들쭉날쭉한 청록 테 · 끝이 뾰족 — 검은 속의 보라 소용돌이 · 별빛) · 틈을 가르고 양끝 너머로 뻗은 흰 검기 ·
    # 네 귀퉁이에서 틈으로 빨려 드는 빛줄기 (끌어당김 — 2026-11-05 스펠 오버드라이브 대신, 게임의 rfx_jtear 와 같은 꼴)
    import random as _r
    rnd = _r.Random(4117)
    ang = math.radians(-10)
    ca, sa = math.cos(ang), math.sin(ang)

    def rot(x, y):
        dx, dy = x - 16.0, y - 16.0
        return (16.0 + dx * ca - dy * sa, 16.0 + dx * sa + dy * ca)

    top, bot = [], []
    n = 16
    for i in range(n + 1):
        x = 2.0 + 28.0 * i / n
        u = abs(x - 16.0) / 14.0
        env = max(0.0, 1 - u ** 2.0) ** 0.85
        jt = 0.7 + rnd.random() * 0.5
        jb = 0.7 + rnd.random() * 0.5
        top.append(rot(x + rnd.uniform(-0.5, 0.5), 16.0 - 5.0 * env * jt))
        bot.append(rot(x + rnd.uniform(-0.5, 0.5), 16.0 + 4.6 * env * jb))
    tear = mask()
    poly(tear, top + bot[::-1])
    edge = mask()
    line(edge, top, 1.1)
    line(edge, bot, 1.1)
    swirl = mask()
    ribbon(swirl, 16.0, 16.0, 7.6, 2.2, 170, 370, 0.8, 0.15)
    ribbon(swirl, 16.0, 16.0, 3.8, 1.2, -10, 190, 0.6, 0.1)
    swirl = ImageChops.multiply(swirl, tear)
    stars = mask()
    for x, y, r in ((9.6, 16.8, 0.6), (22.0, 14.8, 0.7), (12.6, 18.0, 0.5), (24.4, 13.2, 0.5)):
        disc(stars, x, y, r)
    cut = mask()
    p0, p1 = rot(-0.6, 16.0), rot(32.6, 16.0)
    slash(cut, p0[0], p0[1], p1[0], p1[1], 1.8)
    pulls = mask()
    for x0, y0 in ((2.0, 3.0), (30.0, 4.0), (3.0, 29.0), (29.6, 28.0)):
        x1, y1 = x0 + (16.0 - x0) * 0.42, y0 + (16.0 - y0) * 0.42
        tapered(pulls, [(x0, y0), (x1, y1)], 0.4, 1.6)
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        px, py = -uy, ux
        poly(pulls, [(x1 + ux * 2.2, y1 + uy * 2.2), (x1 + px * 1.6, y1 + py * 1.6), (x1 - px * 1.6, y1 - py * 1.6)])
    dark = mix(A, (0, 0, 0), 0.4)
    subject = stack(layer(pulls, mix(A, WHITE, 0.45), A, WHITE, dark), layer(tear, (16, 6, 36), (4, 2, 10)), layer(swirl, mix(A, WHITE, 0.25), A),
                    layer(stars, WHITE, mix(B, WHITE, 0.5)), layer(edge, mix(B, WHITE, 0.55), B), layer(cut, WHITE, mix(B, WHITE, 0.6)))
    return finish(subject, [glow(lighter(edge, cut), B, 1.8, 1.0), glow(lighter(tear, pulls), A, 2.0, 0.8)], arcane_back(A, B))


def arcaneblade_ult(A, B):
    # 월드 클리버: 하늘에서 내리꽂힌 거대한 마력 대검 (넓은 날 · 넓은 코등이 — 칼끝이 땅에) · 칼끝에서 좌우로 갈라진 땅 · 갈라진 땅을 따라 솟구치는 세로 검기 둘 · 칼끝 번쩍임
    gnd = ground(24.6)
    crack = mask()
    tapered(crack, [(16.0, 25.0), (11.0, 26.6), (6.4, 26.0), (1.2, 27.6)], 2.0, 0.6)
    tapered(crack, [(16.0, 25.0), (21.0, 26.4), (25.6, 25.8), (30.8, 27.2)], 2.0, 0.6)
    pillars = mask()
    crescent(pillars, 0.6, 17.0, 8.6, 2.6, -62, 62)
    crescent(pillars, 31.4, 17.0, 8.6, 2.6, 118, 242)
    sw = rune_sword((16.0, 25.2), (16.0, -1.0), A, B, blade=6.4, guard=12.6)
    flash = mask()
    star(flash, 16.0, 25.0, 5.6, 0.18, 0)
    subject = stack(ground_layer(gnd, A), layer(crack, WHITE, B), layer(pillars, mix(A, WHITE, 0.4), A, WHITE, mix(A, (0, 0, 0), 0.3)), *sw,
                    layer(flash, WHITE, B))
    return finish(subject, [glow(lighter(crack, flash), B, 1.6, 1.0), glow(pillars, A, 1.8, 0.9)], arcane_back(A, B))


# ------------------------------------------------------------ 소드 콜러 (빛: 청록 → 흰 청록 — 칼이 손을 떠나 스스로 싸움 · 재설계안 2판)

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


# ------------------------------------------------------------ 아크메이지 (빛: 별빛 파랑 → 금빛 — 밤하늘 자주 바탕)

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


def zigzag(m, p0, p1, n, amp, w, seed=0):
    """두 점 사이 번개 (n 마디 · 옆으로 amp 만큼 흔들림)"""
    pts = [p0]
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy) or 1.0
    px, py = -dy / L, dx / L
    for i in range(1, n):
        t = i / n
        k = amp * (1 if (i + seed) % 2 else -1) * (0.6 + 0.4 * math.sin(i * 2.3 + seed))
        pts.append((p0[0] + dx * t + px * k, p0[1] + dy * t + py * k))
    pts.append(p1)
    line(m, pts, w)
    return m


def arcmage_chain(A, B):
    # 스타 체인: 완드 끝 (왼쪽 아래) 에서 나간 별빛 번개가 별 셋 (적) 을 차례로 꺾어 튐 — 마디마다 금빛 별 · 별을 잇는 옅은 금빛 별자리 선
    nodes = [(13.0, 17.0), (24.5, 8.5), (26.5, 22.5)]
    cons = mask()
    line(cons, [nodes[0], nodes[1], nodes[2], nodes[0]], 0.6)
    bolt = mask()
    zigzag(bolt, (6.5, 26.0), nodes[0], 4, 1.6, 1.9, 1)
    zigzag(bolt, nodes[0], nodes[1], 4, 1.4, 1.7, 2)
    zigzag(bolt, nodes[1], nodes[2], 4, 1.4, 1.5, 3)
    core = mask()
    zigzag(core, (6.5, 26.0), nodes[0], 4, 1.6, 0.6, 1)
    zigzag(core, nodes[0], nodes[1], 4, 1.4, 0.6, 2)
    zigzag(core, nodes[1], nodes[2], 4, 1.4, 0.5, 3)
    stars = mask()
    for i, (x, y) in enumerate(nodes):
        star5(stars, x, y, 3.4 - 0.4 * i)
    flash = mask()
    star(flash, nodes[0][0], nodes[0][1], 6.0, 0.16, 0)
    wand = mask()
    line(wand, [(1.5, 31.0), (6.0, 26.5)], 2.0)
    subject = stack(layer(cons, mix(B, WHITE, 0.2), B), layer(bolt, mix(A, WHITE, 0.35), A), layer(core, WHITE, WHITE), layer(wand, mix(A, WHITE, 0.2),
                    mix(A, (0, 0, 0), 0.4)), layer(flash, WHITE, mix(B, WHITE, 0.3)), layer(stars, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.25)))
    return finish(subject, [glow(bolt, A, 1.6, 1.0), glow(stars, B, 1.6, 1.0)], arcmage_back(A, B))


def arcmage_halo(A, B):
    # 헤일로 배리어: 앞으로 날아가는 기운 별빛 고리 (가운데 금빛 별) · 고리가 돌아오는 휜 화살 (나갔다 되돌아옴) · 아래 금빛 방패 테 (보호막)
    shield = mask()
    poly(shield, [(9.0, 19.0), (16.0, 17.0), (23.0, 19.0), (22.0, 26.0), (16.0, 30.5), (10.0, 26.0)])
    shield_in = mask()
    poly(shield_in, [(11.0, 20.4), (16.0, 19.0), (21.0, 20.4), (20.2, 25.4), (16.0, 28.6), (11.8, 25.4)])
    shield_ring = ImageChops.subtract(shield, shield_in)
    path = mask()
    arc_pts = [(16.0 + math.cos(math.radians(a)) * 11.0, 13.0 + math.sin(math.radians(a)) * 7.0) for a in range(200, 341, 10)]
    line(path, arc_pts, 1.2)
    ex, ey = arc_pts[0]
    poly(path, [(ex - 2.6, ey + 0.8), (ex + 1.4, ey - 2.4), (ex + 1.8, ey + 1.8)])
    halo = mask()
    oval(halo, 21.5, 9.0, 7.5, 4.6, 1.6)
    st = mask()
    star5(st, 21.5, 9.0, 3.4)
    sp = mask()
    for x, y in ((4.0, 6.0), (29.0, 18.5), (6.0, 14.5)):
        star(sp, x, y, 1.9, 0.2, 0)
    subject = stack(layer(shield_ring, mix(B, WHITE, 0.3), B, WHITE, mix(B, (0, 0, 0), 0.35)), layer(shield_in, mix(A, (0, 0, 0), 0.4), mix(A, (0, 0, 0), 0.65)),
                    layer(path, mix(A, WHITE, 0.4), A), layer(halo, WHITE, mix(A, WHITE, 0.35), WHITE, mix(A, (0, 0, 0), 0.2)), layer(st, WHITE, B), layer(sp, WHITE, B))
    return finish(subject, [glow(halo, A, 1.8, 1.0), glow(lighter(st, shield_ring), B, 1.4, 0.8)], arcmage_back(A, B))


def arcmage_vortex(A, B):
    # 스타 볼텍스: 두 갈래 나선 팔의 별의 소용돌이 (가운데 밝은 금빛 · 팔을 따라 별) · 바깥에서 빨려 드는 빛 줄 셋
    arms = mask()
    core_line = mask()
    for k in range(2):
        pts = []
        for i in range(40):
            t = i / 39
            r = 1.5 + 12.0 * t
            a = k * math.pi + t * 3.6
            pts.append((16.0 + math.cos(a) * r, 16.0 + math.sin(a) * r * 0.86))
        tapered(arms, pts, 4.2, 1.2)
        line(core_line, pts[:30], 0.6)
    bulge = mask()
    disc(bulge, 16.0, 16.0, 3.4)
    stars = mask()
    for i in range(7):
        a = i * 0.9
        r = 4.5 + i * 1.4
        star(stars, 16.0 + math.cos(a + 0.6) * r, 16.0 + math.sin(a + 0.6) * r * 0.86, 1.6, 0.2, 0)
    pull = mask()
    for (x0, y0, x1, y1) in ((2.0, 4.0, 6.5, 8.0), (30.0, 6.0, 26.0, 9.5), (28.5, 29.0, 24.5, 25.0)):
        line(pull, [(x0, y0), (x1, y1)], 0.9)
    subject = stack(layer(pull, mix(A, WHITE, 0.3), A), layer(arms, mix(A, WHITE, 0.25), mix(A, (60, 20, 120), 0.4)), layer(core_line, mix(B, WHITE, 0.3), B),
                    layer(stars, WHITE, B), layer(bulge, WHITE, B))
    return finish(subject, [glow(arms, A, 1.8, 0.9), glow(lighter(bulge, stars), B, 1.8, 1.0)], arcmage_back(A, B))


def arcmage_surge(A, B):
    # 스펠 서지: 곧게 세운 완드 끝의 큰 별 · 별을 감아 도는 둥근 화살 (재사용 대기가 돌아옴) · 완드를 타고 오르는 위 화살 셋
    wand = mask()
    line(wand, [(16.0, 31.0), (16.0, 14.0)], 2.4)
    st = mask()
    star5(st, 16.0, 10.0, 5.6)
    burst = mask()
    star(burst, 16.0, 10.0, 9.5, 0.14, 0)
    loop = mask()
    pts = [(16.0 + math.cos(math.radians(a)) * 9.0, 10.0 + math.sin(math.radians(a)) * 8.0) for a in range(-60, 241, 10)]
    line(loop, pts, 1.3)
    ex, ey = pts[-1]
    poly(loop, [(ex - 3.0, ey - 1.2), (ex + 1.6, ey - 2.4), (ex + 0.2, ey + 2.2)])
    ups = mask()
    for y in (28.0, 23.0, 18.0):
        line(ups, [(21.0, y + 1.6), (24.0, y - 1.0), (27.0, y + 1.6)], 1.1)
        line(ups, [(5.0, y + 1.6), (8.0, y - 1.0), (11.0, y + 1.6)], 1.1)
    subject = stack(layer(ups, mix(B, WHITE, 0.3), B), layer(wand, mix(A, WHITE, 0.25), mix(A, (0, 0, 0), 0.4), WHITE, mix(A, (0, 0, 0), 0.5), axis=(1, 0)),
                    layer(loop, mix(A, WHITE, 0.45), A), layer(burst, WHITE, mix(B, WHITE, 0.3)), layer(st, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.25)))
    return finish(subject, [glow(lighter(st, burst), B, 2.0, 1.0), glow(loop, A, 1.4, 0.8), glow(ups, B, 1.2, 0.6)], arcmage_back(A, B))


def arcmage_ult(A, B):
    # 셀레스티얼 이글: 날개를 활짝 편 별빛 독수리 (머리가 아래 — 내리꽂음) · 날개 끝 깃 · 마디 별 · 아래 터지는 금빛 별 · 쏟아지는 깃털 별빛
    body = mask()
    poly(body, [(16.0, 6.0), (18.6, 12.0), (17.8, 20.0), (16.0, 24.0), (14.2, 20.0), (13.4, 12.0)])
    tail = mask()
    poly(tail, [(13.4, 7.0), (18.6, 7.0), (20.5, 2.0), (16.0, 3.5), (11.5, 2.0)])
    wings = mask()
    for sgn in (-1, 1):
        lead = [(16.0 + sgn * 2.0, 12.0), (16.0 + sgn * 8.0, 9.0), (16.0 + sgn * 14.5, 5.0)]
        fingers = [(16.0 + sgn * 15.0, 7.5), (16.0 + sgn * 14.6, 10.0), (16.0 + sgn * 13.6, 12.5), (16.0 + sgn * 12.0, 14.5)]
        poly(wings, lead + [(16.0 + sgn * 11.0, 14.0), (16.0 + sgn * 7.0, 15.0), (16.0 + sgn * 2.0, 17.0)])
        for k, (fx, fy) in enumerate(fingers):
            line(wings, [(16.0 + sgn * (10.5 - k * 0.6), 11.0 + k * 0.8), (fx, fy)], 1.1)
    edges = mask()
    for sgn in (-1, 1):
        line(edges, [(16.0 + sgn * 2.0, 12.0), (16.0 + sgn * 8.0, 9.0), (16.0 + sgn * 14.5, 5.0)], 0.7)
    head = mask()
    disc(head, 16.0, 23.5, 2.2)
    poly(head, [(14.9, 25.0), (17.1, 25.0), (16.0, 27.6)])
    nodes = mask()
    for (x, y, r) in ((1.5, 5.0, 2.2), (30.5, 5.0, 2.2), (8.0, 9.0, 1.6), (24.0, 9.0, 1.6), (16.0, 4.0, 1.8)):
        star(nodes, x, y, r * 1.4, 0.2, 0)
    blast = mask()
    star(blast, 16.0, 29.5, 6.0, 0.16, 0)
    feathers = mask()
    for (x, y) in ((6.0, 20.0), (26.0, 21.0), (9.0, 26.0), (23.5, 27.0)):
        line(feathers, [(x, y - 1.6), (x, y + 1.6)], 0.8)
    subject = stack(layer(wings, mix(A, WHITE, 0.35), mix(A, (40, 20, 110), 0.45)), layer(edges, mix(B, WHITE, 0.3), B), layer(tail, mix(A, WHITE, 0.3),
                    mix(A, (40, 20, 110), 0.4)), layer(body, mix(A, WHITE, 0.5), A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(head, WHITE, mix(B, WHITE, 0.3)),
                    layer(feathers, mix(B, WHITE, 0.4), B), layer(blast, WHITE, B), layer(nodes, WHITE, B))
    return finish(subject, [glow(lighter(wings, body), A, 1.8, 0.9), glow(lighter(nodes, blast), B, 1.6, 1.0)], arcmage_back(A, B))


# ------------------------------------------------------------ 네크로맨서 (빛: 독초록 → 뼈색 — 짙은 녹색 안개 바탕)

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


def tombstone(m, cx, base, w, h):
    """아치 묘비 (아래 base · 너비 w · 높이 h)"""
    poly(m, [(cx - w / 2, base), (cx - w / 2, base - h + w / 2), (cx + w / 2, base - h + w / 2), (cx + w / 2, base)])
    disc(m, cx, base - h + w / 2, w / 2)
    return m


def flame(m, cx, base, h, w):
    poly(m, [(cx - w / 2, base), (cx - w * 0.15, base - h * 0.6), (cx, base - h), (cx + w * 0.2, base - h * 0.55), (cx + w / 2, base)])
    return m


def necromancer_call(A, B):
    # 그레이브 콜: 위에서 내리꽂히는 거대한 뼈 창 (창끝이 아래 — 등뼈 자루 · 이빨 날) · 꽂힌 자리의 녹색 혼불 기둥 · 갈라진 땅 · 그 위 비명 해골
    shaft = mask()
    for i in range(6):
        t = i / 6
        disc(shaft, 23.0 - 6.0 * t, 1.5 + 13.5 * t, 1.6)
    blade = mask()
    poly(blade, [(17.0, 15.0), (19.5, 16.0), (17.6, 21.0), (16.0, 26.0), (15.0, 21.0), (14.2, 17.5)])
    hooks = mask()
    line(hooks, [(18.8, 14.5), (21.5, 16.0), (22.5, 18.8)], 0.9)
    line(hooks, [(15.0, 14.0), (12.4, 15.0), (11.2, 17.5)], 0.9)
    pillar = mask()
    flame(pillar, 16.0, 30.5, 13.0, 9.0)
    ground = mask()
    line(ground, [(2.0, 30.0), (30.0, 30.0)], 1.4)
    crack = mask()
    for pts in (((16.0, 30.0), (10.0, 31.4), (5.0, 30.6)), ((16.0, 30.0), (22.0, 31.5), (27.0, 30.6))):
        line(crack, list(pts), 0.8)
    sk, eyes = skull_mask(7.0, 9.0, 0.6)
    subject = stack(layer(pillar, mix(A, WHITE, 0.45), A, WHITE, None), layer(ground, mix(A, (0, 0, 0), 0.3), mix(A, (0, 0, 0), 0.6)), layer(crack, A, A),
                    bone_layers(shaft, B), bone_layers(hooks, B), bone_layers(blade, B), bone_layers(sk, B), layer(eyes, A, A))
    return finish(subject, [glow(pillar, A, 2.0, 1.0), glow(lighter(shaft, blade), A, 1.4, 0.7), glow(eyes, A, 1.0, 1.0)], necro_back(A, B))


def necromancer_yard(A, B):
    # 그레이브야드: 둥글게 둘러 솟은 묘비 다섯 (뒤 셋은 작게) · 묘비 꼭대기의 녹색 혼불 · 가운데 땅의 녹색 고리 · 가운데 갇힌 적의 눈 둘
    ringm = mask()
    oval(ringm, 16.0, 23.0, 13.0, 5.6, 1.2)
    back = mask()
    for (x, y, w, h) in ((8.0, 20.0, 4.6, 7.0), (16.0, 18.5, 4.8, 7.6), (24.0, 20.0, 4.6, 7.0)):
        tombstone(back, x, y, w, h)
    front = mask()
    for (x, y, w, h) in ((4.5, 28.0, 6.0, 10.0), (27.5, 28.0, 6.0, 10.0)):
        tombstone(front, x, y, w, h)
    flames = mask()
    for (x, y, h) in ((8.0, 11.6, 3.4), (16.0, 9.4, 4.0), (24.0, 11.6, 3.4), (4.5, 16.4, 4.2), (27.5, 16.4, 4.2)):
        flame(flames, x, y, h, 2.4)
    crosses = mask()
    for (x, y, k) in ((8.0, 16.5, 0.7), (16.0, 14.8, 0.75), (24.0, 16.5, 0.7), (4.5, 22.5, 1.0), (27.5, 22.5, 1.0)):
        line(crosses, [(x, y - 2.0 * k), (x, y + 2.4 * k)], 0.8 * k)
        line(crosses, [(x - 1.4 * k, y - 0.6 * k), (x + 1.4 * k, y - 0.6 * k)], 0.8 * k)
    eyes = mask()
    disc(eyes, 14.2, 24.0, 1.1)
    disc(eyes, 17.8, 24.0, 1.1)
    subject = stack(layer(ringm, mix(A, WHITE, 0.3), A), layer(flames, mix(A, WHITE, 0.45), A), bone_layers(back, mix(B, (0, 0, 0), 0.15)), bone_layers(front, B),
                    layer(crosses, mix(A, (0, 0, 0), 0.35), mix(A, (0, 0, 0), 0.6)), layer(eyes, (255, 80, 60), (200, 40, 30)))
    return finish(subject, [glow(lighter(ringm, flames), A, 1.8, 1.0)], necro_back(A, B))


def necromancer_mist(A, B):
    # 소울 미스트: 날아가는 혼령 구슬 (해골 얼굴 · 녹색 꼬리) → 아래 넓게 퍼진 녹색 안개 · 안개 위 표식 (녹색 해골 테)
    fog = mask()
    for (x, y, r) in ((6.0, 25.5, 4.6), (12.5, 27.0, 5.2), (20.0, 26.5, 5.4), (26.5, 25.0, 4.4), (16.0, 23.0, 4.6)):
        disc(fog, x, y, r)
    tail = mask()
    tapered(tail, [(3.0, 4.0), (8.0, 6.5), (13.0, 9.5)], 1.0, 6.0)
    orb = mask()
    disc(orb, 16.0, 11.5, 5.4)
    sk, eyes = skull_mask(16.0, 11.6, 0.62)
    mark = mask()
    ring(mark, 23.5, 19.0, 3.6, 0.9)
    wisps = mask()
    for (x0, y0, x1, y1) in ((8.0, 21.0, 6.0, 16.0), (24.0, 21.5, 27.0, 15.0)):
        line(wisps, [(x0, y0), ((x0 + x1) / 2 + 1.5, (y0 + y1) / 2), (x1, y1)], 1.0)
    subject = stack(layer(fog, mix(A, (0, 0, 0), 0.1), mix(A, (0, 0, 0), 0.55)), layer(wisps, mix(A, WHITE, 0.35), A), layer(tail, mix(A, WHITE, 0.2), A),
                    layer(orb, mix(A, WHITE, 0.5), A), bone_layers(sk, B), layer(eyes, A, mix(A, (0, 0, 0), 0.3)), layer(mark, mix(A, WHITE, 0.5), A))
    return finish(subject, [glow(lighter(orb, tail), A, 2.0, 1.0), glow(fog, A, 1.4, 0.6), glow(mark, A, 1.2, 0.9)], necro_back(A, B))


def necromancer_blight(A, B):
    # 블라이트: 발밑에 넓게 썩어 든 땅 (녹색 고리 · 사방으로 갈라진 금) · 가장자리에서 솟는 혼불 셋 · 가운데 시든 해골 · 떨어지는 독 방울
    rot = mask()
    ImageDraw.Draw(rot).ellipse([2.0 * SS, 17.0 * SS, 30.0 * SS, 31.0 * SS], fill=255)
    ringm = mask()
    oval(ringm, 16.0, 24.0, 14.0, 7.0, 1.3)
    cracks = mask()
    for a in range(0, 360, 45):
        r = math.radians(a)
        line(cracks, [(16.0, 24.0), (16.0 + math.cos(r) * 7.0, 24.0 + math.sin(r) * 3.5), (16.0 + math.cos(r + 0.25) * 12.0, 24.0 + math.sin(r + 0.25) * 6.0)], 0.7)
    flames = mask()
    for (x, y, h) in ((5.0, 24.0, 9.0), (27.0, 24.0, 9.0), (16.0, 19.0, 12.0)):
        flame(flames, x, y, h, 4.2)
    sk, eyes = skull_mask(16.0, 25.0, 0.5)
    drops = mask()
    for (x, y) in ((9.0, 8.0), (23.0, 6.5), (12.5, 3.5)):
        poly(drops, [(x, y - 2.0), (x + 1.2, y + 0.6), (x, y + 1.6), (x - 1.2, y + 0.6)])
    subject = stack(layer(rot, mix(A, (0, 0, 0), 0.45), mix(A, (0, 0, 0), 0.75)), layer(cracks, mix(A, WHITE, 0.2), A), layer(ringm, mix(A, WHITE, 0.35), A),
                    layer(flames, mix(A, WHITE, 0.45), A), bone_layers(sk, B), layer(eyes, A, A), layer(drops, mix(A, WHITE, 0.4), A))
    return finish(subject, [glow(lighter(flames, ringm), A, 1.8, 1.0), glow(cracks, A, 1.0, 0.7)], necro_back(A, B))


def necromancer_ult(A, B):
    # 팬텀 갤리온: 안개 위 망자의 배 옆모습 (휜 뱃머리 해골 · 찢긴 돛의 해골 무늬 · 방패) · 배에서 뛰어내리는 거대한 선장 (뿔 투구 · 도끼) · 배 밑 녹색 혼불
    fog = mask()
    for (x, y, r) in ((5.0, 27.0, 4.6), (12.0, 28.5, 5.0), (20.0, 28.0, 5.0), (27.0, 27.0, 4.6)):
        disc(fog, x, y, r)
    hull = mask()
    poly(hull, [(2.0, 20.5), (22.0, 20.5), (19.5, 25.5), (5.0, 25.5)])
    prow = mask()
    line(prow, [(21.0, 21.0), (23.0, 16.5), (22.5, 12.5)], 1.5)
    line(prow, [(3.0, 21.0), (1.0, 17.0), (2.0, 14.0)], 1.3)
    mast = mask()
    line(mast, [(11.5, 20.5), (11.5, 4.0)], 1.0)
    sail = mask()
    poly(sail, [(5.5, 6.0), (17.5, 6.0), (16.5, 16.0), (14.0, 14.4), (12.0, 16.6), (9.5, 14.4), (6.5, 16.0)])
    emb, emb_eyes = skull_mask(11.5, 10.0, 0.45)
    shields = mask()
    for x in (6.0, 10.0, 14.0, 18.0):
        disc(shields, x, 21.8, 1.2)
    # 선장: 오른쪽 위에서 뛰어내리는 큰 해골 (뿔 투구) · 든 도끼
    cap, cap_eyes = skull_mask(26.0, 7.5, 0.72)
    horns = mask()
    line(horns, [(22.6, 5.0), (21.0, 2.0), (21.6, 0.8)], 1.0)
    line(horns, [(29.4, 5.0), (31.0, 2.0), (30.4, 0.8)], 1.0)
    axe = mask()
    line(axe, [(28.0, 12.0), (30.5, 19.0)], 1.0)
    poly(axe, [(29.0, 17.0), (31.8, 15.5), (31.8, 21.5), (30.0, 21.0)])
    flames = mask()
    for (x, h) in ((5.0, 6.0), (10.0, 7.5), (16.0, 7.5), (21.0, 5.5)):
        flame(flames, x, 31.0, h, 3.2)
    subject = stack(layer(flames, mix(A, WHITE, 0.4), A), layer(sail, mix(A, (0, 0, 0), 0.5), mix(A, (0, 0, 0), 0.75), mix(A, (0, 0, 0), 0.3), None),
                    layer(emb, mix(A, WHITE, 0.2), A), layer(emb_eyes, mix(A, (0, 0, 0), 0.6), mix(A, (0, 0, 0), 0.7)),
                    layer(mast, mix(B, (0, 0, 0), 0.3), mix(B, (0, 0, 0), 0.6)), bone_layers(hull, B), bone_layers(prow, B),
                    layer(shields, A, mix(A, (0, 0, 0), 0.3)), bone_layers(horns, B), bone_layers(cap, B), layer(cap_eyes, A, A),
                    layer(axe, mix(B, (0, 0, 0), 0.15), mix(B, (0, 0, 0), 0.5)), layer(fog, mix(A, (0, 0, 0), 0.35), mix(A, (0, 0, 0), 0.7)))
    return finish(subject, [glow(flames, A, 2.0, 1.0), glow(lighter(emb, cap_eyes), A, 1.2, 1.0), glow(hull, A, 1.2, 0.5)], necro_back(A, B))


# ------------------------------------------------------------ 스나이퍼 (빛: 밝은 연두 → 금빛 흰 섬광 — 짙은 숲 바탕)

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


def reticle4(m, cx, cy, r, w, gap=14):
    """네 토막 과녁 테 (토막 사이 gap 도)"""
    for q in range(4):
        a0, a1 = q * 90 + gap, (q + 1) * 90 - gap
        pts = [(cx + math.cos(math.radians(t)) * r, cy + math.sin(math.radians(t)) * r) for t in range(a0, a1 + 1, 6)]
        line(m, pts, w)
    return m


def big_arrow(tip, ang, L, hw=4.6, sw=2.0):
    """넓은 잎 촉 큰 화살 (tip · ang 도 · 길이 L): 살대 · 촉 · 깃 마스크"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux

    def at(s, o=0.0):
        return (tip[0] - ux * s + px * o, tip[1] - uy * s + py * o)
    shaft = mask()
    line(shaft, [at(hw * 1.6), at(L)], sw)
    head = mask()
    poly(head, [at(0), at(hw, hw), at(hw * 2.0, hw * 1.05), at(hw * 2.2, hw * 0.4), at(hw * 1.8, 0), at(hw * 2.2, -hw * 0.4), at(hw * 2.0, -hw * 1.05), at(hw, -hw)])
    fl = mask()
    for d0 in (0.0, 2.6):
        line(fl, [at(L - 2.5 - d0), at(L + 0.5 - d0, 3.4)], 1.1)
        line(fl, [at(L - 2.5 - d0), at(L + 0.5 - d0, -3.4)], 1.1)
    return shaft, head, fl


def sniper_heart(A, B):
    # 하트시커: 왼쪽 아래 끝까지 당긴 활 (휜 활대 · 당겨진 시위) 에 걸린 큰 화살이 오른쪽 위 과녁 (네 토막 테) 을 겨눔 · 촉 끝 흰 섬광 · 과녁 가운데 금빛 점
    bow = mask()
    crescent(bow, 13.0, 19.0, 11.0, 2.2, 135, 315)
    string = mask()
    line(string, [(5.2, 11.2), (9.0, 23.0), (20.8, 26.8)], 0.8)
    arr = big_arrow((26.5, 5.5), -45, 24.0, 4.0, 1.9)
    tgt = mask()
    reticle4(tgt, 25.0, 7.0, 5.6, 1.2)
    dot = mask()
    disc(dot, 25.0, 7.0, 1.3)
    sp = mask()
    star(sp, 26.8, 5.2, 4.0, 0.2, 10)
    subject = stack(layer(tgt, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(bow, mix(A, WHITE, 0.4), mix(A, (0, 0, 0), 0.35), WHITE, None),
                    layer(string, WHITE, mix(A, WHITE, 0.5)), layer(arr[0], WHITE, mix(B, WHITE, 0.3)), layer(arr[2], WHITE, A),
                    layer(arr[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(dot, WHITE, B), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(arr[0], arr[1]), B, 1.8, 1.0), glow(bow, A, 1.4, 0.8), glow(tgt, B, 1.2, 0.7)], sniper_back(A, B))


def sniper_snare(A, B):
    # 스네어 샷: 오른쪽 위로 펼쳐지는 사각 그물 (팔각 테 · 그물코 · 매듭 · 추) 을 끄는 화살 · 왼쪽 아래 뒤로 물러나는 바람 줄 셋
    net = mask()
    cx, cy, R = 20.5, 11.5, 9.0
    rim = []
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        rim.append((cx + math.cos(a) * R, cy + math.sin(a) * R))
    line(net, rim + [rim[0]], 1.3)
    for g in (-0.5, 0.0, 0.5):
        line(net, [(cx + g * R, cy - R * 0.9), (cx + g * R, cy + R * 0.9)], 0.8)
        line(net, [(cx - R * 0.9, cy + g * R), (cx + R * 0.9, cy + g * R)], 0.8)
    knots = mask()
    for gx in (-0.5, 0.0, 0.5):
        for gy in (-0.5, 0.0, 0.5):
            disc(knots, cx + gx * R, cy + gy * R, 0.9)
    weights = mask()
    for (x, y) in rim[::2]:
        disc(weights, x + (x - cx) * 0.18, y + (y - cy) * 0.18, 1.3)
    arr = sn_arrow((14.0, 18.0), -40, 11.0, 3.0, 1.2)
    wind = mask()
    for (x0, y0, L) in ((2.0, 22.0, 9.0), (4.0, 26.5, 8.0), (1.5, 30.0, 7.0)):
        line(wind, [(x0 + L, y0 - L * 0.35), (x0, y0)], 1.0)
    subject = stack(layer(wind, WHITE, A, WHITE, None), layer(net, mix(A, WHITE, 0.45), A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(knots, WHITE, B),
                    layer(weights, mix(B, WHITE, 0.3), mix(B, (0, 0, 0), 0.4)), *sn_arrow_layers(A, B, *arr))
    return finish(subject, [glow(net, A, 1.6, 0.9), glow(knots, B, 1.0, 0.8), glow(wind, A, 1.2, 0.6)], sniper_back(A, B))


def sniper_trick(A, B):
    # 트릭 샷: 왼쪽 아래 과녁에 꽂힌 화살 (섬광) · 거기서 튀어 올라 높이 휘어 오른쪽 아래 과녁으로 내리꽂는 금빛 화살 (점선 호) · 두 과녁 반짝임
    t1 = mask()
    reticle4(t1, 8.5, 23.0, 4.6, 1.0, 18)
    t2 = mask()
    reticle4(t2, 25.5, 24.0, 4.6, 1.0, 18)
    arc = mask()
    pts = []
    for i in range(21):
        t = i / 20
        x = 8.5 + (25.5 - 8.5) * t
        y = 22.0 - 17.0 * math.sin(math.pi * t) * (1.0 - 0.15 * t)
        pts.append((x, y))
    for i in range(0, 20, 3):
        line(arc, pts[i:i + 2], 1.0)
    first = sn_arrow((8.5, 23.0), 20, 9.0, 2.8, 1.1)
    bounce = sn_arrow((25.0, 23.0), 72, 9.0, 3.2, 1.2, fletch=False)
    sp = mask()
    star(sp, 8.5, 23.0, 3.2, 0.22, 0)
    star(sp, 25.5, 24.0, 3.8, 0.22, 20)
    top = mask()
    star(top, pts[10][0], pts[10][1], 2.0, 0.25, 0)
    subject = stack(layer(t1, WHITE, A), layer(t2, WHITE, B), layer(arc, WHITE, B), *sn_arrow_layers(A, B, *first), layer(bounce[0], WHITE, B),
                    layer(bounce[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(sp, WHITE, B), layer(top, WHITE, B))
    return finish(subject, [glow(lighter(arc, bounce[0], bounce[1]), B, 1.6, 1.0), glow(lighter(t1, t2), A, 1.2, 0.7)], sniper_back(A, B))


SILVER_C = (205, 225, 255)


def sniper_mark(A, B):
    # 헌터스 마크: 가운데 과녁 (네 토막 테) 을 비스듬히 꿰뚫는 은빛 큰 화살 · 과녁 위 표식 셋 (마름모 — 셋째만 은빛으로 밝게) · 은빛 섬광
    tgt = mask()
    reticle4(tgt, 16.0, 18.5, 8.5, 1.3)
    arr = big_arrow((27.5, 8.0), -38, 25.0, 3.8, 1.8)
    pips = []
    for i, x in enumerate((9.0, 16.0, 23.0)):
        m = mask()
        poly(m, [(x, 2.0), (x + 2.2, 4.6), (x, 7.2), (x - 2.2, 4.6)])
        pips.append(m)
    sp = mask()
    star(sp, 16.0, 18.5, 4.6, 0.18, 0)
    subject = stack(layer(tgt, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(pips[0], mix(A, (0, 0, 0), 0.2), mix(A, (0, 0, 0), 0.5)),
                    layer(pips[1], mix(A, (0, 0, 0), 0.2), mix(A, (0, 0, 0), 0.5)), layer(pips[2], WHITE, SILVER_C, WHITE, mix(SILVER_C, (0, 0, 0), 0.35)),
                    layer(arr[0], WHITE, SILVER_C), layer(arr[2], WHITE, mix(SILVER_C, (0, 0, 0), 0.2)), layer(arr[1], WHITE, SILVER_C, WHITE, mix(SILVER_C, (0, 0, 0), 0.4)),
                    layer(sp, WHITE, SILVER_C))
    return finish(subject, [glow(lighter(arr[0], arr[1], pips[2]), SILVER_C, 1.8, 1.0), glow(tgt, A, 1.2, 0.7)], sniper_back(A, B))


def sniper_ult(A, B):
    # 데드아이: 큰 조준경 (둥근 테 · 십자선 · 가운데 점) 을 가로지르는 세 화살 줄 — 둘은 연두 · 셋째는 굵은 금빛 · 십자 가운데 금빛 섬광
    scope = mask()
    ring(scope, 16.0, 16.0, 12.5, 1.6)
    cross = mask()
    for (p0, p1) in (((16.0, 3.0), (16.0, 11.5)), ((16.0, 20.5), (16.0, 29.0)), ((3.0, 16.0), (11.5, 16.0)), ((20.5, 16.0), (29.0, 16.0))):
        line(cross, [p0, p1], 1.0)
    shots = []
    for (y0, y1, w) in ((26.0, 9.0, 1.1), (30.0, 13.0, 1.1)):
        m = mask()
        line(m, [(2.0, y0), (26.0, y1 - 6.0)], w)
        shots.append(m)
    gold = big_arrow((29.0, 4.0), -40, 30.0, 4.4, 2.2)
    sp = mask()
    star(sp, 16.0, 16.0, 5.0, 0.18, 0)
    subject = stack(layer(scope, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.35)), layer(cross, WHITE, A), *[layer(m, mix(A, WHITE, 0.4), A) for m in shots],
                    layer(gold[0], WHITE, B), layer(gold[2], WHITE, B), layer(gold[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(gold[0], gold[1]), B, 1.8, 1.0), glow(lighter(scope, *shots), A, 1.4, 0.8)], sniper_back(A, B))


# ------------------------------------------------------------ 배틀 아처 (빛: 폭풍 하늘빛 → 번개 노랑 — 짙은 폭풍 남청 바탕)

STORM_NIGHT = (4, 10, 22)


def battle_back(A, B):
    return backdrop(mix(A, STORM_NIGHT, 0.86), mix((40, 90, 150), A, 0.3))


def bolt_mask(pts, w):
    m = mask()
    line(m, pts, w)
    return m


def battlearcher_swarm(A, B):
    # 호넷 스웜: 가운데 빛에서 사방으로 휘어 나는 화살 여덟 (끝이 바깥으로 휨 · 작은 촉) · 가운데 번개 섬광
    arrows = []
    for i in range(8):
        a0 = math.radians(i * 45 + 10)
        pts = []
        for k in range(9):
            t = k / 8
            r = 3.0 + 10.5 * t
            a = a0 + 0.55 * t
            pts.append((16.0 + math.cos(a) * r, 16.0 + math.sin(a) * r))
        sh = mask()
        line(sh, pts[:-1], 1.0)
        ax, ay = pts[-1]
        bx, by = pts[-2]
        ang = math.degrees(math.atan2(ay - by, ax - bx))
        hd = sn_arrow((ax, ay), ang, 2.0, 2.6, 0.9, fletch=False)[1]
        arrows.append((sh, hd))
    core = mask()
    disc(core, 16.0, 16.0, 2.8)
    sp = mask()
    star(sp, 16.0, 16.0, 4.6, 0.2, 0)
    layers = []
    for sh, hd in arrows:
        layers += [layer(sh, mix(A, WHITE, 0.35), A), layer(hd, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3))]
    subject = stack(*layers, layer(core, WHITE, B), layer(sp, WHITE, B))
    return finish(subject, [glow(lighter(*[s for s, _ in arrows]), A, 1.6, 0.9), glow(lighter(core, sp), B, 2.0, 1.0)], battle_back(A, B))


def battlearcher_dive(A, B):
    # 팰컨 다이브: 왼쪽 위에서 오른쪽 아래로 내리꽂는 폭풍 매 (V 자로 뒤로 젖힌 큰 날개 · 번개 결 · 부리) · 뒤로 끌리는 바람 줄 · 아래 내려앉은 자리의 고리와 사방 화살
    a = math.radians(40)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    hx, hy = 20.5, 18.5

    def at(s, o=0.0):
        return (hx - ux * s + px * o, hy - uy * s + py * o)
    body = mask()
    poly(body, [at(-2.6), at(0.5, 1.8), at(7.0, 2.2), at(12.0, 1.0), at(15.0, 2.2), at(15.0, -2.2), at(12.0, -1.0), at(7.0, -2.2), at(0.5, -1.8)])
    wings = mask()
    for sd in (1, -1):
        poly(wings, [at(2.5, sd * 1.6), at(9.0, sd * 8.0), at(15.5, sd * 12.5), at(13.0, sd * 7.0), at(10.5, sd * 2.0)])
    trail = mask()
    for o in (-5.0, 0.0, 5.0):
        line(trail, [at(17.0, o * 0.9), at(25.0, o * 1.3)], 0.8)
    ring_m = mask()
    d = ImageDraw.Draw(ring_m)
    d.ellipse([P(14.0, 26.0), P(31.0, 31.4)], fill=255)
    d.ellipse([P(16.0, 27.2), P(29.0, 30.2)], fill=0)
    spray = mask()
    for ang in (190, 230, 310, 350):
        aa = math.radians(ang)
        line(spray, [(22.5 + math.cos(aa) * 4.0, 28.7 + math.sin(aa) * 2.0), (22.5 + math.cos(aa) * 8.0, 28.7 + math.sin(aa) * 4.0)], 0.9)
    bolt = mask()
    line(bolt, [at(4.0, 2.4), at(7.0, 5.6), at(8.5, 3.6), at(12.0, 8.6)], 0.7)
    line(bolt, [at(4.0, -2.4), at(7.0, -5.6), at(8.5, -3.6), at(12.0, -8.6)], 0.7)
    eye = mask()
    disc(eye, hx - ux * 0.4 + px * 0.7, hy - uy * 0.4 + py * 0.7, 0.6)
    subject = stack(layer(trail, WHITE, A), layer(ring_m, mix(A, WHITE, 0.3), A), layer(spray, WHITE, B), layer(wings, mix(A, WHITE, 0.35), A, WHITE, mix(A, (0, 0, 0), 0.35)),
                    layer(body, mix(A, WHITE, 0.5), A, WHITE, mix(A, (0, 0, 0), 0.3)), layer(bolt, WHITE, B), layer(eye, WHITE, B))
    return finish(subject, [glow(lighter(body, wings), A, 1.8, 1.0), glow(lighter(bolt, spray), B, 1.2, 0.8), glow(ring_m, A, 1.2, 0.6)], battle_back(A, B))


def battlearcher_splinter(A, B):
    # 스플린터 샷: 왼쪽에 놓은 활 (휜 활대) · 활 앞 번쩍임 · 그 앞에서 오른쪽으로 부채꼴로 퍼지는 화살 조각 일곱 (가운데가 길고 굵음 · 조각 사이가 벌어짐) · 부채 끝 불티
    bow = mask()
    crescent(bow, 7.0, 16.0, 8.0, 2.0, 100, 260)
    shards = []
    for i in range(7):
        ang = -39 + i * 13
        a = math.radians(ang)
        r0 = 7.5
        L = 12.5 + (3.0 if i == 3 else 0.0) - abs(i - 3) * 0.9
        tip = (5.0 + math.cos(a) * (r0 + L), 16.0 + math.sin(a) * (r0 + L))
        shards.append(sn_arrow(tip, ang, L, 2.3, 1.3 if i == 3 else 0.85, fletch=False))
    flash = mask()
    star(flash, 7.6, 16.0, 4.4, 0.22, 0)
    sparks = mask()
    for i in (1, 5):
        a = math.radians(-39 + i * 13)
        star(sparks, 5.0 + math.cos(a) * 24.5, 16.0 + math.sin(a) * 24.5, 1.5, 0.25, 0)
    layers = []
    for sh in shards:
        layers += [layer(sh[0], mix(A, WHITE, 0.35), A), layer(sh[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3))]
    subject = stack(layer(bow, mix(A, WHITE, 0.4), mix(A, (0, 0, 0), 0.35), WHITE, None), *layers, layer(flash, WHITE, B), layer(sparks, WHITE, B))
    return finish(subject, [glow(lighter(*[sh[0] for sh in shards]), A, 1.4, 0.9), glow(flash, B, 2.0, 1.0)], battle_back(A, B))


def battlearcher_stride(A, B):
    # 스톰 스트라이드: 왼쪽에서 날아와 세 갈래로 갈라지는 화살 (가르는 자리 번쩍 · 위 · 가운데 · 아래로 벌어짐) · 그 밑 폭풍 바람 줄 셋 · 왼쪽 번개
    bolt = mask()
    line(bolt, [(7.5, 1.5), (4.5, 10.0), (9.0, 10.5), (5.0, 19.5)], 1.8)
    split = (15.0, 9.5)
    lead = mask()
    line(lead, [(9.0, 9.5), split], 1.3)
    fl = mask()
    for d0 in (0.0, 2.0):
        line(fl, [(10.0 + d0, 9.5), (8.0 + d0, 7.4)], 0.9)
        line(fl, [(10.0 + d0, 9.5), (8.0 + d0, 11.6)], 0.9)
    arrows = []
    for tip in ((30.0, 2.0), (31.0, 9.5), (30.0, 17.0)):
        ang = math.degrees(math.atan2(tip[1] - split[1], tip[0] - split[0]))
        L = math.hypot(tip[0] - split[0], tip[1] - split[1]) - 0.5
        arrows.append(sn_arrow(tip, ang, L, 3.0, 1.1, fletch=False))
    fx = mask()
    star(fx, split[0], split[1], 2.6, 0.25, 0)
    wind = mask()
    for (y, x0, L) in ((22.5, 10.0, 19.0), (26.5, 7.0, 22.0), (30.0, 11.0, 17.0)):
        pts = [(x0 + L * t, y + math.sin(t * math.pi * 2) * 1.1) for t in [i / 12 for i in range(13)]]
        line(wind, pts, 1.2)
    layers = []
    for arr in arrows:
        layers += [layer(arr[0], mix(A, WHITE, 0.45), A), layer(arr[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3))]
    subject = stack(layer(bolt, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.35)), layer(wind, WHITE, A, WHITE, None), layer(lead, mix(A, WHITE, 0.45), A), layer(fl, WHITE, A), *layers,
                    layer(fx, WHITE, B))
    return finish(subject, [glow(bolt, B, 1.8, 1.0), glow(wind, A, 1.4, 0.8), glow(lighter(*[a[1] for a in arrows], fx), B, 1.2, 0.8)], battle_back(A, B))


def battlearcher_ult(A, B):
    # 퀴버 스톰: 오른쪽 위 먹구름 · 구름에서 내리치는 번개 · 왼쪽 아래 한 점에서 오른쪽으로 부채꼴로 쏟아지는 화살 아홉 (물결 둘)
    cloud = mask()
    for (x, y, r) in ((17.0, 5.5, 3.6), (22.5, 4.0, 4.0), (28.0, 5.5, 3.4), (20.0, 7.6, 3.0), (25.5, 7.6, 3.0)):
        disc(cloud, x, y, r)
    bolt = bolt_mask([(24.0, 8.5), (21.5, 13.5), (25.0, 13.8), (22.0, 19.5)], 1.2)
    arrows = []
    for i in range(9):
        ang = -38 + i * 8.0
        a = math.radians(ang)
        L = 7.0 + (i % 2) * 3.0
        r = 13.0 + (i % 3) * 4.5
        tip = (3.0 + math.cos(a) * r, 27.0 + math.sin(a) * r)
        arrows.append(sn_arrow(tip, ang, L, 2.6, 0.9, fletch=False))
    grey = (60, 70, 92)
    layers = []
    for arr in arrows:
        layers += [layer(arr[0], mix(A, WHITE, 0.35), A), layer(arr[1], WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3))]
    subject = stack(layer(cloud, mix(grey, WHITE, 0.3), mix(grey, (0, 0, 0), 0.45), mix(grey, WHITE, 0.55), None), layer(bolt, WHITE, B, WHITE, mix(B, (0, 0, 0), 0.3)),
                    *layers)
    return finish(subject, [glow(bolt, B, 1.8, 1.0), glow(lighter(*[a[0] for a in arrows]), A, 1.4, 0.9)], battle_back(A, B))


# ------------------------------------------------------------ 트릭스터 (빛: 자홍 → 검보라 — 짙은 보라 바탕 · 청록 환영 테두리 · 들불은 분홍) — 2026-11-06 2판

TEAL = (77, 242, 230)
TEAL_LO = (20, 110, 112)
WILDFIRE = (255, 77, 184)  # 들불 (TricksterSkills.FIRE)
WILDFIRE_CORE = (255, 224, 158)
BLACK = (0, 0, 0)


def trick_back(A, B):
    return backdrop(mix(B, (10, 2, 16), 0.8), mix(A, (44, 8, 56), 0.55))


def clip(m, region):
    return ImageChops.multiply(m, region)


def minus(m, cut):
    return ImageChops.subtract(m, cut)


def ellipse(m, cx, cy, rx, ry):
    ImageDraw.Draw(m).ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=255)
    return m


def tongue(m, p0, c, p2, w0, w1=0.0, steps=20):
    """p0 → p2 로 휘는 띠 (2차 곡선 — 조절점 c) · 굵기 w0 → w1 (불꽃 혀 · 모자 뿔 · 자국)"""
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p2[1]
        dx = 2 * (1 - t) * (c[0] - p0[0]) + 2 * t * (p2[0] - c[0])
        dy = 2 * (1 - t) * (c[1] - p0[1]) + 2 * t * (p2[1] - c[1])
        L = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / L, dx / L
        w = (w0 + (w1 - w0) * t) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return poly(m, left + right[::-1])


def arc_arrow(m, cx, cy, r, a0, a1, w0, w1, head=2.4, steps=40, tip=True):
    """(cx, cy) 둘레 반지름 r 의 호 (a0 → a1 도 — 화면 좌표 · 0 = 오른쪽 · 90 = 아래) · 굵기 w0 → w1 · 끝에 화살촉"""
    outer, inner = [], []
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0 + (a1 - a0) * t)
        w = (w0 + (w1 - w0) * t) / 2
        outer.append((cx + math.cos(a) * (r + w), cy + math.sin(a) * (r + w)))
        inner.append((cx + math.cos(a) * (r - w), cy + math.sin(a) * (r - w)))
    poly(m, outer + inner[::-1])
    if tip:
        a = math.radians(a1)
        ex, ey = cx + math.cos(a) * r, cy + math.sin(a) * r
        s = 1 if a1 > a0 else -1
        tx, ty = -math.sin(a) * s, math.cos(a) * s
        nx, ny = math.cos(a), math.sin(a)
        poly(m, [(ex + tx * head * 1.15, ey + ty * head * 1.15), (ex + nx * head * 0.9 - tx * 0.4, ey + ny * head * 0.9 - ty * 0.4),
                 (ex - nx * head * 0.9 - tx * 0.4, ey - ny * head * 0.9 - ty * 0.4)])
    return m


def shard(m, x, y, s, rot):
    """깨진 거울 조각 (뾰족한 세모)"""
    r = math.radians(rot)
    poly(m, [(x + math.cos(r) * s * 1.3, y + math.sin(r) * s * 1.3), (x + math.cos(r + 2.4) * s * 0.8, y + math.sin(r + 2.4) * s * 0.8),
             (x + math.cos(r + 3.9) * s * 0.9, y + math.sin(r + 3.9) * s * 0.9)])
    return m


def hood_layers(body, face, eyes, A, B, ghost=False):
    """두건 — 나 (검보라 몸 · 자홍 테) / 환영 (청록 테 · 비치는 몸)"""
    if ghost:
        return [layer(body, mix(TEAL, B, 0.5), mix(TEAL_LO, B, 0.55), mix(TEAL, WHITE, 0.3), None), layer(face, mix(B, BLACK, 0.65), mix(B, BLACK, 0.8)),
                layer(eyes, WHITE, TEAL)]
    return [layer(body, mix(B, A, 0.25), mix(B, BLACK, 0.5), mix(B, A, 0.6), None), layer(face, mix(B, BLACK, 0.75), mix(B, BLACK, 0.85)),
            layer(eyes, WHITE, mix(A, WHITE, 0.5))]


def hood_eyes(cx, top, w, h):
    m = mask()
    y = top + h * 0.235
    disc(m, cx - w * 0.07, y, max(0.6, w * 0.065))
    disc(m, cx + w * 0.11, y, max(0.6, w * 0.065))
    return m


def trickster_swap(A, B):
    # 섀도 스와프: 아래왼쪽의 나 (검보라 두건 · 자홍 테) 와 위오른쪽의 환영 (청록 테 · 비치는 두건) — 둘을 도는 두 화살 (자홍 = 나 → 환영 · 청록 = 환영 → 나) · 가운데 자리 바꿈 반짝임
    body, face = _hood(10.0, 13.0, 10.0, 17.0)
    ib, ifc = _hood(22.0, 2.5, 10.0, 17.0)
    fwd = mask()
    arc_arrow(fwd, 16.0, 16.0, 12.0, 198, 280, 0.9, 2.4, 3.2)
    back = mask()
    arc_arrow(back, 16.0, 16.0, 12.0, 18, 100, 0.9, 2.4, 3.2)
    sp = mask()
    star(sp, 16.0, 16.0, 3.6, 0.2, 45)
    subject = stack(*hood_layers(ib, ifc, hood_eyes(22.0, 2.5, 10.0, 17.0), A, B, ghost=True), *hood_layers(body, face, hood_eyes(10.0, 13.0, 10.0, 17.0), A, B),
                    layer(fwd, mix(A, WHITE, 0.35), A, WHITE, mix(B, BLACK, 0.3), axis=(1, 0)), layer(back, mix(TEAL, WHITE, 0.4), TEAL, WHITE, TEAL_LO, axis=(-1, 0)),
                    layer(sp, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(ib, TEAL, 1.6, 0.8), glow(fwd, A, 1.4, 0.9), glow(back, TEAL, 1.4, 0.9), glow(sp, A, 1.2, 0.9)], trick_back(A, B))


def trickster_dash(A, B):
    # 미라주 대시: 왼쪽에 남겨 둔 환영 (청록 테 두건) → 오른쪽으로 뻗은 빠른 줄 (자홍 → 흰) → 내려선 자리를 둘러 깨지는 거울 베기 고리 (거울 조각이 튐) ·
    # 위의 점선 되돌아오기 화살 (청록 — 다시 누르면 환영 자리로)
    ib, ifc = _hood(7.0, 14.0, 9.0, 15.0)
    streaks = mask()
    for (y, x0, x1, w) in ((19.5, 10.5, 19.0, 1.7), (23.5, 11.5, 18.5, 1.2), (15.8, 12.0, 17.5, 1.0), (27.0, 12.0, 17.0, 0.9)):
        tapered(streaks, [(x0, y), (x1, y)], 0.2, w)
    ring_m = mask()
    crescent(ring_m, 23.0, 20.0, 6.6, 2.4, -150, 160, taper=False)
    shards = mask()
    for (a, s, rot) in ((-105, 2.0, -100), (-48, 1.9, -40), (8, 1.7, 20), (58, 1.9, 70), (112, 1.6, 120)):
        r = math.radians(a)
        shard(shards, 23.0 + math.cos(r) * 9.4, 20.0 + math.sin(r) * 9.4, s, rot)
    ret = mask()
    n = 4
    for k in range(n):
        a0 = -28 - k * 30.0
        arc_arrow(ret, 15.0, 15.0, 9.0, a0, a0 - 18.0, 1.0, 1.0, 2.0, steps=10, tip=(k == n - 1))
    sp = mask()
    star(sp, 23.0, 20.0, 3.4, 0.2, 0)
    subject = stack(layer(ret, mix(TEAL, WHITE, 0.3), TEAL), *hood_layers(ib, ifc, hood_eyes(7.0, 14.0, 9.0, 15.0), A, B, ghost=True),
                    layer(streaks, WHITE, mix(A, WHITE, 0.2), None, None, axis=(-1, 0)),
                    layer(ring_m, mix(A, WHITE, 0.4), mix(B, A, 0.5), WHITE, mix(B, BLACK, 0.3), axis=(1, 1)), layer(shards, mix(TEAL, WHITE, 0.55), TEAL, WHITE, TEAL_LO),
                    layer(sp, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(ib, TEAL, 1.4, 0.7), glow(ring_m, A, 1.6, 0.9), glow(shards, TEAL, 1.2, 0.8), glow(streaks, A, 1.2, 0.7)], trick_back(A, B))


def trickster_phantom(A, B):
    # 사우전드 팬텀: 가운데 적을 둘러선 환영 여섯 (청록 테 두건) · 둘씩 엇갈려 꿰뚫은 자국이 그린 육망성 (자홍) · 가운데 거대한 X 베기 (흰 → 자홍)
    hexm = mask()
    R = 11.4
    spots = [(16.0 + math.cos(math.radians(-90 + i * 60)) * R, 16.0 + math.sin(math.radians(-90 + i * 60)) * R) for i in range(6)]
    for k in range(2):
        pts = [spots[k + 2 * i] for i in range(3)]
        line(hexm, pts + [pts[0]], 1.3)
    hoods = []
    glow_m = mask()
    for (x, y) in spots:
        body, face = _hood(x, y - 3.9, 5.4, 8.0)
        hoods += hood_layers(body, face, mask(), A, B, ghost=True)
        glow_m = lighter(glow_m, body)
    xm = mask()
    slash(xm, 9.0, 9.0, 23.0, 23.0, 1.8)
    slash(xm, 23.0, 9.0, 9.0, 23.0, 1.8)
    xc = mask()
    slash(xc, 10.5, 10.5, 21.5, 21.5, 0.6)
    slash(xc, 21.5, 10.5, 10.5, 21.5, 0.6)
    subject = stack(layer(hexm, mix(A, WHITE, 0.45), A, WHITE, None), *hoods, layer(xm, mix(A, WHITE, 0.45), A, WHITE, mix(B, BLACK, 0.2), axis=(1, 1)), layer(xc, WHITE, WHITE))
    return finish(subject, [glow(hexm, A, 1.6, 1.0), glow(glow_m, TEAL, 1.4, 0.7), glow(xm, A, 1.8, 0.9)], trick_back(A, B))


def mirror(cx, top, w, h, frame):
    """아치 거울 — (테, 유리)"""
    outer = mask()
    tombstone(outer, cx, top + h, w, h)
    glass = mask()
    tombstone(glass, cx, top + h - frame, w - frame * 2, h - frame * 2)
    return minus(outer, glass), glass


def trickster_palace(A, B):
    # 미러 팰리스: 가운데 큰 아치 거울 (자홍 테 · 청록 유리 · 비친 두건 그림자 · 흰 빛줄) · 양옆 조금 작은 아치 거울 둘 · 위 반짝임
    cf, cg = mirror(16.0, 4.0, 12.5, 25.0, 1.6)
    lf, lg = mirror(4.6, 11.0, 7.4, 16.5, 1.3)
    rf, rg = mirror(27.4, 11.0, 7.4, 16.5, 1.3)
    rb, rfc = _hood(16.0, 12.5, 9.0, 15.5)
    rb = clip(rb, cg)
    rfc = clip(rfc, cg)
    re = clip(hood_eyes(16.0, 12.5, 9.0, 15.5), cg)
    glint = mask()
    line(glint, [(11.2, 15.0), (14.6, 9.0)], 0.9)
    line(glint, [(11.2, 19.5), (18.0, 8.2)], 0.7)
    line(glint, [(2.4, 17.5), (5.6, 13.6)], 0.7)
    line(glint, [(25.3, 17.5), (28.4, 13.6)], 0.7)
    glint = clip(glint, lighter(cg, lg, rg))
    base = mask()
    poly(base, [(8.5, 29.0), (23.5, 29.0), (24.5, 31.0), (7.5, 31.0)])
    sp = mask()
    star(sp, 23.5, 4.5, 2.8, 0.2, 0)
    star(sp, 6.5, 6.0, 2.0, 0.22, 0)
    frame_cols = (mix(A, WHITE, 0.15), mix(B, A, 0.6), mix(A, WHITE, 0.55), mix(B, BLACK, 0.35))
    glass_side = (mix(TEAL, WHITE, 0.35), mix(TEAL_LO, B, 0.4))
    subject = stack(layer(lg, glass_side[0], glass_side[1], None, None, axis=(0.6, 0.8)), layer(rg, glass_side[0], glass_side[1], None, None, axis=(-0.6, 0.8)),
                    layer(lf, *frame_cols), layer(rf, *frame_cols),
                    layer(cg, mix(TEAL, WHITE, 0.5), mix(TEAL_LO, B, 0.25), None, None, axis=(0.6, 0.8)),
                    layer(rb, mix(B, A, 0.3), mix(B, BLACK, 0.45), mix(B, A, 0.6), None), layer(rfc, mix(B, BLACK, 0.75), mix(B, BLACK, 0.85)), layer(re, WHITE, A),
                    layer(glint, WHITE, mix(TEAL, WHITE, 0.6)), layer(cf, *frame_cols), layer(base, mix(B, A, 0.45), mix(B, BLACK, 0.4)), layer(sp, WHITE, mix(A, WHITE, 0.5)))
    return finish(subject, [glow(lighter(cg, lg, rg), TEAL, 1.8, 0.6), glow(cf, A, 1.6, 0.9)], trick_back(A, B))


def wildfire(m, cx, by, w, h):
    """들불 (아래 둥근 몸 + 위로 일렁이는 혀 다섯 — 가운데가 가장 김)"""
    ellipse(m, cx, by - w * 0.3, w * 0.5, w * 0.32)
    tongue(m, (cx, by - w * 0.3), (cx + w * 0.3, by - h * 0.55), (cx - w * 0.06, by - h), w * 0.8, 0.0)
    tongue(m, (cx - w * 0.2, by - w * 0.3), (cx - w * 0.52, by - h * 0.42), (cx - w * 0.36, by - h * 0.74), w * 0.52, 0.0)
    tongue(m, (cx + w * 0.2, by - w * 0.3), (cx + w * 0.56, by - h * 0.38), (cx + w * 0.42, by - h * 0.68), w * 0.52, 0.0)
    tongue(m, (cx - w * 0.36, by - w * 0.2), (cx - w * 0.72, by - h * 0.22), (cx - w * 0.6, by - h * 0.44), w * 0.32, 0.0)
    tongue(m, (cx + w * 0.36, by - w * 0.2), (cx + w * 0.74, by - h * 0.2), (cx + w * 0.62, by - h * 0.42), w * 0.32, 0.0)
    return m


def trickster_ult(A, B):
    # 와일드파이어 피스트: 가운데 크게 타오르는 분홍 들불 (흰 금빛 속불) · 앞에 엇갈린 단검 둘 (X — 자홍 코등이) · 튀는 불티
    fl = mask()
    wildfire(fl, 16.0, 30.5, 19.0, 28.0)
    core = mask()
    wildfire(core, 16.0, 29.5, 8.5, 17.0)
    cols = dict(blade_cols=(STEEL_HI, mix(STEEL, A, 0.3), WHITE, STEEL_LO), guard_cols=(mix(A, WHITE, 0.3), A, WHITE, mix(B, BLACK, 0.3)),
                grip_cols=(mix(B, A, 0.3), mix(B, BLACK, 0.4), None, None))
    d1 = sword_layers((25.0, 4.5), (8.0, 25.5), blade=2.8, guard=6.0, **cols)
    d2 = sword_layers((7.0, 4.5), (24.0, 25.5), blade=2.8, guard=6.0, **cols)
    em = mask()
    for (x, y, r) in ((4.0, 10.0, 1.6), (28.0, 13.0, 1.8), (27.0, 4.0, 1.3), (5.0, 21.5, 1.2)):
        star(em, x, y, r * 1.5, 0.3, 0)
    subject = stack(layer(fl, mix(WILDFIRE, WHITE, 0.3), mix(B, WILDFIRE, 0.45), mix(WILDFIRE, WHITE, 0.55), mix(B, BLACK, 0.3)),
                    layer(core, WHITE, WILDFIRE_CORE), *d2, *d1, layer(em, WHITE, WILDFIRE))
    return finish(subject, [glow(fl, WILDFIRE, 2.2, 1.0), glow(em, WILDFIRE, 1.2, 0.9)], trick_back(A, B))


# ------------------------------------------------------------ 피에로 (빛: 빨강 → 크림 금빛 — 서커스 밤 바탕 · 할리퀸 마름모 무늬 · 금방울) — 2026-11-06 블레이드 댄서 대신

CIRCUS_TEAL = (64, 214, 214)
SPRING = (GOLD_HI, GOLD, GOLD_HI, GOLD_LO)


def circus_back(A, B):
    return backdrop(mix(A, (12, 8, 34), 0.84), mix(A, (34, 16, 70), 0.5))


def harlequin(region, x0, y0, a, b):
    """할리퀸 마름모 무늬: region 을 두 벌 (첫째 · 둘째 마름모) 로 나눔 (가로 반폭 a · 세로 반높이 b, (x0, y0) = 첫째 마름모 가운데)"""
    m1, m2 = mask(), mask()
    for i in range(-12, 13):
        for j in range(-12, 13):
            for (m, ox, oy) in ((m1, 0.0, 0.0), (m2, a, b)):
                cx, cy = x0 + 2 * a * i + ox, y0 + 2 * b * j + oy
                if -4 < cx < 36 and -4 < cy < 36:
                    poly(m, [(cx - a, cy), (cx, cy - b), (cx + a, cy), (cx, cy + b)])
    return clip(m1, region), clip(m2, region)


def clown(cx, cy, r, tilt=0.0):
    """광대 머리 (가운데 cx, cy · 반지름 r · tilt 도 기울임): 흰 얼굴 · 빨간 코 · 마름모 눈 · 웃는 입 · 두 갈래 고깔모자 (끝에 금방울) — 마스크 dict"""
    t = math.radians(tilt)
    ct, st = math.cos(t), math.sin(t)

    def at(dx, dy):
        return (cx + (dx * ct - dy * st) * r, cy + (dx * st + dy * ct) * r)

    head = mask()
    disc(head, cx, cy, r)
    half = mask()
    poly(half, [at(-1.6, -0.28), at(1.6, -0.28), at(1.6, -1.6), at(-1.6, -1.6)])
    cap = clip(head, half)
    face = minus(head, cap)
    hat_l = mask()
    tongue(hat_l, at(-0.3, -0.62), at(-1.25, -1.9), at(-2.05, -0.55), r * 1.0, r * 0.22)
    hat_r = mask()
    tongue(hat_r, at(0.3, -0.62), at(1.25, -1.9), at(2.05, -0.55), r * 1.0, r * 0.22)
    bells = mask()
    for sx in (-1, 1):
        x, y = at(sx * 2.08, -0.3)
        disc(bells, x, y, max(0.75, r * 0.3))
    nose = mask()
    x, y = at(0.0, 0.22)
    disc(nose, x, y, max(0.7, r * 0.24))
    eyes = mask()
    for sx in (-0.45, 0.45):
        x, y = at(sx, -0.02)
        poly(eyes, [at(sx - 0.15, -0.02), at(sx, -0.3), at(sx + 0.15, -0.02), at(sx, 0.26)])
    mouth = mask()
    x, y = at(0.0, 0.05)
    crescent(mouth, x, y, r * 0.7, r * 0.22, 35 + tilt, 145 + tilt)
    return {"face": face, "cap": cap, "hat_l": hat_l, "hat_r": hat_r, "bells": bells, "nose": nose, "eyes": eyes, "mouth": mouth}


def clown_layers(c, A, B):
    return [layer(c["hat_r"], mix(B, WHITE, 0.3), mix(B, GOLD_LO, 0.35), WHITE, mix(B, GOLD_LO, 0.6)),
            layer(c["hat_l"], mix(A, WHITE, 0.25), mix(A, BLACK, 0.3), mix(A, WHITE, 0.5), mix(A, BLACK, 0.5)),
            layer(c["cap"], mix(A, WHITE, 0.2), mix(A, BLACK, 0.25), mix(A, WHITE, 0.45), None),
            layer(c["face"], WHITE, (232, 226, 240), None, (180, 170, 196)), layer(c["eyes"], (40, 24, 60), (20, 12, 36)),
            layer(c["mouth"], A, mix(A, BLACK, 0.3)), layer(c["nose"], mix(A, WHITE, 0.3), A), layer(c["bells"], *SPRING)]


def harlequin_layers(h1, h2, A, B):
    return [layer(h1, mix(A, WHITE, 0.15), mix(A, BLACK, 0.25)), layer(h2, mix(B, WHITE, 0.3), mix(B, GOLD_LO, 0.3))]


def coil(m, x, y0, y1, w, turns, lw):
    """용수철 (x 를 가운데로 y0 → y1 지그재그)"""
    pts = [(x, y0)]
    n = turns * 2
    for i in range(1, n):
        pts.append((x + (w / 2 if i % 2 else -w / 2), y0 + (y1 - y0) * i / n))
    pts.append((x, y1))
    line(m, pts, lw)
    return m


def jack_box(x0, y0, x1, y1, lid_side, A, B, lid_len=None):
    """깜짝 상자: 할리퀸 앞면 · 금빛 위 · 아래 테 · 열린 뚜껑 (lid_side = -1 왼쪽 · 1 오른쪽으로 젖힘) — 레이어 목록, 앞면 마스크"""
    front = mask()
    poly(front, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)])
    w = x1 - x0
    h1, h2 = harlequin(front, x0, y0, max(1.6, w / 8.0), max(2.0, (y1 - y0) / 4.0))
    trim = mask()
    poly(trim, [(x0 - 0.6, y0 - 1.0), (x1 + 0.6, y0 - 1.0), (x1 + 0.6, y0 + 0.6), (x0 - 0.6, y0 + 0.6)])
    poly(trim, [(x0 - 0.6, y1 - 0.6), (x1 + 0.6, y1 - 0.6), (x1 + 0.6, y1 + 0.8), (x0 - 0.6, y1 + 0.8)])
    lid = mask()
    hx = x0 - 0.4 if lid_side < 0 else x1 + 0.4
    a = math.radians(-118 if lid_side < 0 else -62)
    L = lid_len or w * 0.62
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy * -lid_side, ux * -lid_side
    th = 2.2
    poly(lid, [(hx, y0 - 0.2), (hx + ux * L, y0 - 0.2 + uy * L), (hx + ux * L + px * th, y0 - 0.2 + uy * L + py * th), (hx + px * th, y0 - 0.2 + py * th)])
    return [layer(lid, mix(A, WHITE, 0.2), mix(A, BLACK, 0.35), *SPRING[2:]), *harlequin_layers(h1, h2, A, B), layer(trim, *SPRING)], front


def pierrot_box(A, B):
    # 잭 인 더 박스: 아래 할리퀸 마름모 상자 (금빛 테 · 왼쪽으로 젖혀진 뚜껑) → 금빛 용수철 → 튀어나온 광대 머리 (두 갈래 모자 · 금방울) · 오른쪽으로 날아가는 칼 (빠른 줄)
    box, front = jack_box(8.0, 21.0, 24.0, 30.0, -1, A, B, lid_len=9.0)
    spring = mask()
    coil(spring, 16.0, 20.5, 13.0, 5.0, 3, 1.1)
    c = clown(16.0, 10.0, 3.9, -6)
    knife = sword_layers((31.0, 13.0), (23.0, 16.6), blade=1.9, guard=3.6, blade_cols=(STEEL_HI, STEEL, WHITE, STEEL_LO), guard_cols=SPRING,
                         grip_cols=(mix(A, BLACK, 0.2), mix(A, BLACK, 0.5), None, None))
    sl = mask()
    tapered(sl, [(18.5, 18.6), (22.5, 17.0)], 0.2, 0.9)
    tapered(sl, [(20.0, 15.6), (23.0, 14.4)], 0.2, 0.8)
    subject = stack(*box, layer(spring, *SPRING), *clown_layers(c, A, B), layer(sl, WHITE, mix(B, WHITE, 0.4)), *knife)
    return finish(subject, [glow(lighter(c["face"], c["cap"]), B, 1.6, 0.7), glow(spring, GOLD, 1.2, 0.8)], circus_back(A, B))


def pierrot_spring(A, B):
    # 핸드스프링: 두 손으로 땅을 짚고 거꾸로 넘는 광대 (다리를 크게 벌린 재주넘기 — 왼다리 빨강 · 오른다리 크림 · 할리퀸 몸 · 거꾸로 늘어진 모자) ·
    # 발끝이 그린 재주넘기 자국 (위 — 크림 → 빨강) · 짚은 자리의 땅 울림 (금빛 고리 · 별)
    th = math.radians(166)
    ct, st = math.cos(th), math.sin(th)

    def at(x, y):
        return (16.0 + x * ct - y * st, 16.5 + x * st + y * ct)

    head, sh, hip = at(0, -6.6), at(0, -3.6), at(0, 3.0)
    hands = (at(-5.6, -10.4), at(5.6, -10.4))
    feet = (at(-6.8, 11.2), at(6.8, 11.2))
    trail = mask()
    for f, back in ((feet[1], -38), (feet[0], -40)):
        a = math.degrees(math.atan2(f[1] - hip[1], f[0] - hip[0]))
        rr = math.hypot(f[0] - hip[0], f[1] - hip[1]) + 1.6
        crescent(trail, hip[0], hip[1], rr, 2.4, a + back, a - 6)
    ringm = mask()
    oval(ringm, 18.0, 28.6, 8.6, 2.4, 1.2)
    sp = mask()
    star(sp, 18.0, 27.6, 3.4, 0.2, 0)
    leg_l, leg_r = mask(), mask()
    line(leg_l, [hip, feet[0]], 2.5)
    line(leg_r, [hip, feet[1]], 2.5)
    shoes = mask()
    for f, sgn in ((feet[0], 1), (feet[1], -1)):
        tongue(shoes, (f[0], f[1] + 0.4), (f[0] + sgn * 2.6, f[1] + 0.2), (f[0] + sgn * 2.8, f[1] - 1.8), 1.9, 0.7)
    torso = mask()
    tapered(torso, [sh, hip], 4.6, 3.6)
    h1, h2 = harlequin(torso, hip[0], hip[1], 1.6, 2.0)
    arms = mask()
    line(arms, [at(-1.4, -3.6), hands[0]], 1.9)
    line(arms, [at(1.4, -3.6), hands[1]], 1.9)
    gloves = mask()
    for h in hands:
        disc(gloves, h[0], h[1], 1.35)
    ruff = mask()
    pts = []
    for i in range(7):
        pts.append(at(-3.0 + 6.0 * i / 6, -4.2 if i % 2 == 0 else -2.8))
    line(ruff, pts, 1.1)
    c = clown(head[0], head[1], 2.9, 166)
    sleeve = (mix(A, WHITE, 0.15), mix(A, BLACK, 0.3))
    cream = (mix(B, WHITE, 0.3), mix(B, GOLD_LO, 0.35))
    subject = stack(layer(trail, mix(B, WHITE, 0.35), mix(A, BLACK, 0.1), WHITE, mix(A, BLACK, 0.4), axis=(1, 0)), layer(ringm, *SPRING),
                    layer(leg_l, *sleeve), layer(leg_r, *cream), layer(shoes, *SPRING), *harlequin_layers(h1, h2, A, B), layer(arms, *sleeve),
                    *clown_layers(c, A, B), layer(ruff, WHITE, mix(B, WHITE, 0.3)), layer(gloves, WHITE, (220, 214, 230)), layer(sp, WHITE, mix(B, WHITE, 0.3)))
    return finish(subject, [glow(trail, A, 1.6, 0.8), glow(lighter(ringm, sp), GOLD, 1.6, 0.9)], circus_back(A, B))


def balloon_layers(m, col, light=None):
    return layer(m, mix(col, WHITE, 0.25), mix(col, BLACK, 0.3), light or mix(col, WHITE, 0.55), mix(col, BLACK, 0.5), axis=(0.6, 0.8))


def pierrot_balloon(A, B):
    # 벌룬 퍼레이드: 가운데 빨간 풍선 강아지 (마디마다 꼬인 풍선 · 흰 반짝임) · 위오른쪽 둥근 풍선 둘 (금빛 · 청록 — 줄) · 위왼쪽에서 펑 터지는 풍선 (색종이)
    segs = [((11.0, 19.0), (19.5, 19.0), 4.0), ((10.6, 18.0), (8.6, 12.8), 3.4), ((8.4, 12.0), (4.0, 13.4), 3.2), ((9.2, 11.4), (11.2, 7.2), 2.6),
            ((10.8, 20.0), (9.8, 26.4), 2.8), ((13.2, 20.0), (13.8, 26.4), 2.8), ((18.0, 20.0), (17.6, 26.4), 2.8), ((20.2, 20.0), (21.4, 26.2), 2.8),
            ((20.4, 17.6), (23.2, 13.0), 2.6)]
    dog = mask()
    twists = mask()
    for (p0, p1, w) in segs:
        dx, dy = p1[0] - p0[0], p1[1] - p0[1]
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        line(dog, [(p0[0] + ux * w * 0.3, p0[1] + uy * w * 0.3), (p1[0] - ux * w * 0.1, p1[1] - uy * w * 0.1)], w)
    gl = mask()
    for (x, y, r) in ((13.0, 17.9, 0.7), (16.0, 17.9, 0.6), (8.4, 15.0, 0.6), (5.6, 12.5, 0.6)):
        disc(gl, x, y, r)
    eye = mask()
    disc(eye, 7.0, 12.4, 0.65)
    b1 = mask()
    ellipse(b1, 24.5, 6.8, 3.6, 4.4)
    poly(b1, [(23.7, 11.4), (25.3, 11.4), (24.5, 10.6)])
    b2 = mask()
    ellipse(b2, 28.4, 13.2, 2.8, 3.4)
    poly(b2, [(27.8, 16.9), (29.0, 16.9), (28.4, 16.2)])
    strings = mask()
    line(strings, [(24.5, 11.8), (23.6, 14.0), (25.0, 16.0), (24.0, 18.5)], 0.7)
    line(strings, [(28.4, 17.2), (27.6, 19.5), (28.6, 21.6)], 0.7)
    shine = mask()
    ellipse(shine, 23.2, 5.2, 0.9, 1.3)
    ellipse(shine, 27.5, 12.0, 0.7, 1.0)
    pop = mask()
    star(pop, 5.5, 5.0, 4.4, 0.22, 20)
    conf = [mask(), mask(), mask()]
    for k, (x, y, a) in enumerate(((11.0, 3.0, 20), (2.5, 11.0, -30), (12.0, 8.5, 60), (2.0, 2.5, 10), (9.0, 1.6, -50), (15.5, 4.0, 40))):
        r = math.radians(a)
        poly(conf[k % 3], [(x + math.cos(r) * 1.1 - math.sin(r) * 0.5, y + math.sin(r) * 1.1 + math.cos(r) * 0.5),
                           (x - math.cos(r) * 1.1 - math.sin(r) * 0.5, y - math.sin(r) * 1.1 + math.cos(r) * 0.5),
                           (x - math.cos(r) * 1.1 + math.sin(r) * 0.5, y - math.sin(r) * 1.1 - math.cos(r) * 0.5),
                           (x + math.cos(r) * 1.1 + math.sin(r) * 0.5, y + math.sin(r) * 1.1 - math.cos(r) * 0.5)])
    subject = stack(layer(strings, mix(B, WHITE, 0.3), mix(B, GOLD_LO, 0.3)), balloon_layers(dog, A), layer(gl, WHITE, WHITE), layer(eye, (40, 24, 60), (20, 12, 36)),
                    balloon_layers(b2, CIRCUS_TEAL), balloon_layers(b1, GOLD), layer(shine, WHITE, WHITE), layer(pop, WHITE, mix(B, WHITE, 0.3)),
                    layer(conf[0], mix(A, WHITE, 0.2), A), layer(conf[1], GOLD_HI, GOLD), layer(conf[2], mix(CIRCUS_TEAL, WHITE, 0.3), CIRCUS_TEAL))
    del twists
    return finish(subject, [glow(dog, A, 1.6, 0.8), glow(lighter(b1, b2), GOLD, 1.4, 0.7), glow(pop, B, 1.4, 1.0)], circus_back(A, B))


def pierrot_doll(A, B):
    # 할루시네이트: 위의 나무 십자 손잡이에서 내려온 줄에 매달린 커다란 광대 인형 (할리퀸 몸 · 주름 깃 · 흰 장갑 · 말린 신) · 오른손에 높이 든 커다란 뿅망치 (빨강 · 금빛 띠)
    bar = mask()
    line(bar, [(4.0, 2.6), (18.0, 2.6)], 1.5)
    line(bar, [(11.0, 0.8), (11.0, 6.0)], 1.3)
    strings = mask()
    line(strings, [(4.6, 3.2), (6.2, 20.0)], 0.7)
    line(strings, [(17.4, 3.2), (18.6, 15.0)], 0.7)
    line(strings, [(11.0, 6.0), (11.6, 8.6)], 0.7)
    c = clown(11.8, 12.2, 3.5, 4)
    torso = mask()
    poly(torso, [(8.6, 16.4), (15.4, 16.4), (16.8, 25.4), (7.4, 25.4)])
    h1, h2 = harlequin(torso, 12.0, 16.4, 1.7, 2.25)
    ruff = mask()
    pts = []
    for i in range(9):
        x = 8.0 + 8.0 * i / 8
        pts.append((x, 15.6 if i % 2 == 0 else 17.6))
    line(ruff, pts, 1.2)
    arms = mask()
    line(arms, [(9.0, 17.6), (6.2, 20.6)], 2.0)
    line(arms, [(15.0, 17.6), (18.8, 15.4)], 2.0)
    gloves = mask()
    disc(gloves, 6.0, 21.0, 1.35)
    disc(gloves, 19.4, 15.0, 1.35)
    legs = mask()
    line(legs, [(9.6, 25.0), (8.8, 29.2)], 2.3)
    line(legs, [(14.6, 25.0), (15.6, 29.2)], 2.3)
    shoes = mask()
    tongue(shoes, (9.6, 29.8), (6.0, 30.6), (5.4, 28.6), 1.8, 0.7)
    tongue(shoes, (14.8, 29.8), (18.6, 30.6), (19.2, 28.6), 1.8, 0.7)
    handle = mask()
    line(handle, [(19.4, 15.0), (24.6, 8.4)], 1.2)
    hd = (24.8, 8.0)
    ux, uy = 0.62, -0.78
    px, py = 0.78, 0.62
    head = mask()
    L, T = 4.6, 2.6
    poly(head, [(hd[0] + px * L + ux * T, hd[1] + py * L + uy * T), (hd[0] - px * L + ux * T, hd[1] - py * L + uy * T),
                (hd[0] - px * L - ux * T, hd[1] - py * L - uy * T), (hd[0] + px * L - ux * T, hd[1] + py * L - uy * T)])
    bands = mask()
    for s in (-3.0, 3.0):
        poly(bands, [(hd[0] + px * (s + 0.6) + ux * (T + 0.3), hd[1] + py * (s + 0.6) + uy * (T + 0.3)), (hd[0] + px * (s - 0.6) + ux * (T + 0.3), hd[1] + py * (s - 0.6) + uy * (T + 0.3)),
                     (hd[0] + px * (s - 0.6) - ux * (T + 0.3), hd[1] + py * (s - 0.6) - uy * (T + 0.3)), (hd[0] + px * (s + 0.6) - ux * (T + 0.3), hd[1] + py * (s + 0.6) - uy * (T + 0.3))])
    sp = mask()
    star(sp, 29.0, 15.0, 2.6, 0.22, 0)
    sleeve = (mix(A, WHITE, 0.15), mix(A, BLACK, 0.3))
    subject = stack(layer(strings, mix(B, WHITE, 0.5), mix(B, WHITE, 0.2)), layer(bar, (196, 132, 70), (120, 74, 36), (232, 180, 110), (84, 50, 24)),
                    layer(legs, *sleeve), layer(shoes, mix(A, BLACK, 0.2), mix(A, BLACK, 0.5), mix(A, WHITE, 0.3), None), *harlequin_layers(h1, h2, A, B),
                    layer(arms, *sleeve), layer(gloves, WHITE, (220, 214, 230)), layer(ruff, WHITE, mix(B, WHITE, 0.3)), *clown_layers(c, A, B),
                    layer(handle, mix(B, WHITE, 0.2), mix(B, GOLD_LO, 0.4)), layer(head, mix(A, WHITE, 0.3), mix(A, BLACK, 0.3), mix(A, WHITE, 0.55), mix(A, BLACK, 0.5),
                    axis=(0.6, 0.8)), layer(bands, *SPRING), layer(sp, WHITE, mix(B, WHITE, 0.3)))
    return finish(subject, [glow(head, A, 1.6, 0.8), glow(strings, B, 1.0, 0.6), glow(c["face"], B, 1.4, 0.5)], circus_back(A, B))


def firework(cx, cy, r, n, w0, w1, bead, rot=0.0):
    """불꽃놀이 한 송이 — (살 둘 (번갈아), 끝 구슬 둘)"""
    spokes, beads = [mask(), mask()], [mask(), mask()]
    for i in range(n):
        a = math.radians(rot + i * 360.0 / n)
        k = i % 2
        tapered(spokes[k], [(cx + math.cos(a) * r * 0.3, cy + math.sin(a) * r * 0.3), (cx + math.cos(a) * r * 0.82, cy + math.sin(a) * r * 0.82)], w0, w1)
        if bead:
            disc(beads[k], cx + math.cos(a) * r, cy + math.sin(a) * r, bead)
    return spokes, beads


def pierrot_ult(A, B):
    # 그랜드 피날레: 아래 양쪽의 커다란 깜짝 상자 둘 (할리퀸 · 바깥으로 젖힌 뚜껑) · 상자에서 솟는 불꽃 줄기 (금빛) → 위 가운데에서 크게 터지는 불꽃놀이 (빨강 · 금빛 살 · 끝 구슬) ·
    # 양옆 작은 불꽃 (청록 · 금빛)
    lbox, _ = jack_box(1.5, 23.5, 11.5, 31.0, -1, A, B, lid_len=5.5)
    rbox, _ = jack_box(20.5, 23.5, 30.5, 31.0, 1, A, B, lid_len=5.5)
    trails = mask()
    tongue(trails, (6.5, 22.0), (6.8, 18.5), (8.6, 15.4), 0.3, 1.5)
    tongue(trails, (25.5, 22.0), (25.2, 18.5), (23.4, 15.4), 0.3, 1.5)
    heads = mask()
    star(heads, 8.9, 14.9, 2.2, 0.26, 0)
    star(heads, 23.1, 14.9, 2.2, 0.26, 0)
    spokes, beads = firework(16.0, 8.6, 8.0, 8, 0.8, 2.0, 1.15, 22.5)
    ctr = mask()
    star(ctr, 16.0, 8.6, 3.8, 0.24, 0)
    tsp, _ = firework(4.4, 5.0, 4.0, 6, 0.5, 1.3, 0)
    gsp = mask()
    star(gsp, 28.0, 4.6, 2.8, 0.24, 0)
    subject = stack(*lbox, *rbox, layer(trails, WHITE, GOLD_HI), layer(heads, WHITE, GOLD_HI), layer(spokes[0], mix(A, WHITE, 0.25), A), layer(spokes[1], GOLD_HI, GOLD),
                    layer(beads[0], mix(A, WHITE, 0.4), A), layer(beads[1], WHITE, GOLD_HI), layer(lighter(*tsp), mix(CIRCUS_TEAL, WHITE, 0.4), CIRCUS_TEAL),
                    layer(ctr, WHITE, mix(B, WHITE, 0.4)), layer(gsp, WHITE, GOLD_HI))
    return finish(subject, [glow(lighter(*spokes, *beads), A, 2.2, 1.0), glow(lighter(trails, heads), GOLD, 1.2, 0.8), glow(lighter(*tsp), CIRCUS_TEAL, 1.2, 0.8)],
                  circus_back(A, B))


# ------------------------------------------------------------ 스톰 랜서 (빛: 옥빛 바람 → 흰빛 — 짙은 옥빛 폭풍 바탕) — 2026-11-06

GALE_NIGHT = (2, 16, 16)


def gale_back(A, B):
    return backdrop(mix(A, GALE_NIGHT, 0.86), mix((20, 90, 80), A, 0.3))


def gale_spear(tail, tip, A, B, head=5.0, hw=1.8, shaft=1.2):
    """옥빛 자루 · 흰 창날 (스톰 랜서)"""
    sh, bl = _spear(tail, tip, head, hw, shaft)
    return [layer(sh, mix(A, WHITE, 0.2), mix(A, (0, 0, 0), 0.45)), layer(bl, WHITE, mix(A, WHITE, 0.45), WHITE, mix(A, (0, 0, 0), 0.3), axis=(0.7, 0.7))], bl


def swirl_mask(cx, cy, R, arms=3, w=1.4, turn=1.6, rot=0.0):
    """바람 소용돌이: 가운데로 감기는 팔 arms 개 (바깥이 굵고 눈 쪽으로 가늚)"""
    m = mask()
    for k in range(arms):
        pts = []
        for i in range(14):
            t = i / 13
            r = R * (1 - t) + 1.0 * t
            a = rot + k * 2 * math.pi / arms + turn * t * math.pi
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        for i in range(len(pts) - 1):
            line(m, [pts[i], pts[i + 1]], w * (1 - i / 14) + 0.4)
    return m


def wind_lines(pts_list, w=0.9):
    m = mask()
    for pts in pts_list:
        line(m, pts, w)
    return m


def stormlancer_cross(A, B):
    # 크로스 게일: 왼쪽 위 · 왼쪽 아래에서 비껴 찌른 바람 창 둘이 오른쪽 가운데에서 엇갈림 · 엇갈린 자리의 소용돌이 · X 번쩍 · 지나간 바람 줄
    fp = (22.5, 16.0)
    layers, glows = [], []
    for (tail, tip) in (((1.5, 4.0), (27.5, 22.5)), ((1.5, 28.0), (27.5, 9.5))):
        ls, bl = gale_spear(tail, tip, A, B, head=5.0, hw=1.7, shaft=1.1)
        layers += ls
        glows.append(glow(bl, B, 1.2, 0.8))
    trail = wind_lines([[(0.5, 9.0), (8.0, 12.0)], [(0.5, 23.0), (8.0, 20.0)], [(3.0, 16.0), (10.0, 16.0)]], 0.8)
    sw = swirl_mask(fp[0], fp[1], 7.0, arms=3, w=1.2, turn=1.4, rot=0.4)
    xf = mask()
    star(xf, fp[0], fp[1], 5.0, 0.16, 45)
    star(xf, fp[0], fp[1], 2.6, 0.22, 0)
    subject = stack(layer(trail, mix(A, WHITE, 0.4), A, None, None, axis=(1, 0)), layer(sw, mix(A, WHITE, 0.4), A, WHITE, None), *layers, layer(xf, WHITE, B))
    return finish(subject, glows + [glow(sw, A, 1.6, 0.9), glow(xf, A, 1.8, 1.0)], gale_back(A, B))


def stormlancer_drive(A, B):
    # 게일 드라이브: 왼쪽에서 오른쪽으로 꿰뚫고 지나간 창 (오른쪽 끝) · 지나온 길의 바람 줄기 · 길 위의 적 셋 (어두운 덩이 — 바람이 감김) · 위로 휘어 되돌아오는 화살 (다시 누름)
    ls, bl = gale_spear((15.0, 17.5), (31.0, 17.5), A, B, head=5.0, hw=1.9, shaft=1.3)
    trail = wind_lines([[(1.0, 14.5), (16.0, 15.5)], [(2.5, 18.0), (17.0, 18.0)], [(1.0, 21.5), (16.0, 20.0)]], 1.0)
    foes = mask()
    for (x, y) in ((6.0, 18.0), (11.5, 18.0)):
        disc(foes, x, y - 2.0, 2.0)
        poly(foes, [(x - 2.4, y + 4.0), (x - 1.6, y - 0.5), (x + 1.6, y - 0.5), (x + 2.4, y + 4.0)])
    marks = mask()
    for (x, y) in ((6.0, 17.0), (11.5, 17.0)):
        crescent(marks, x, y, 3.6, 0.9, 200, 340)
    back = mask()
    crescent(back, 16.0, 13.0, 10.5, 1.8, 200, 340)
    head = mask()
    poly(head, [(5.0, 9.0), (8.6, 6.0), (9.4, 11.0)])
    subject = stack(layer(trail, mix(A, WHITE, 0.4), A, None, None, axis=(1, 0)), layer(foes, (40, 56, 60), (14, 22, 26)), layer(marks, WHITE, A),
                    layer(back, WHITE, mix(A, WHITE, 0.3)), layer(head, WHITE, mix(A, WHITE, 0.3)), *ls)
    return finish(subject, [glow(bl, B, 1.2, 0.8), glow(lighter(trail, marks), A, 1.4, 0.8), glow(lighter(back, head), A, 1.2, 0.7)], gale_back(A, B))


def stormlancer_drill(A, B):
    # 템페스트 드릴: 왼쪽 아래에서 오른쪽 위로 겨눈 창 · 창을 감싸며 끝으로 갈수록 가늘어지는 바람 드릴 (감긴 띠 다섯) · 창끝 번쩍
    ox, oy, tx, ty = 2.5, 27.5, 29.5, 4.5
    L = math.hypot(tx - ox, ty - oy)
    ux, uy = (tx - ox) / L, (ty - oy) / L
    px, py = -uy, ux
    drill = mask()
    for k in range(6):
        t0 = 0.18 + k * 0.13
        t1 = t0 + 0.1
        w0 = 7.0 * (1 - t0) + 1.0
        w1 = 7.0 * (1 - t1) + 1.0
        a = (ox + ux * L * t0 + px * w0, oy + uy * L * t0 + py * w0)
        b = (ox + ux * L * t1 - px * w1, oy + uy * L * t1 - py * w1)
        c = (ox + ux * L * (t0 + t1) / 2 + px * 0.3, oy + uy * L * (t0 + t1) / 2 + py * 0.3)
        line(drill, [a, c, b], 1.5)
    edge = mask()
    for s in (1, -1):
        line(edge, [(ox + ux * L * 0.15 + px * s * 7.2, oy + uy * L * 0.15 + py * s * 7.2), (tx - ux * 1.0, ty - uy * 1.0)], 0.7)
    ls, bl = gale_spear((ox, oy), (tx, ty), A, B, head=5.5, hw=1.8, shaft=1.2)
    sp = mask()
    star(sp, tx - 0.5, ty + 0.5, 4.0, 0.18, 30)
    subject = stack(layer(edge, mix(A, WHITE, 0.3), A, None, None), layer(drill, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.3)), *ls, layer(sp, WHITE, B))
    return finish(subject, [glow(drill, A, 1.8, 1.0), glow(bl, B, 1.2, 0.8), glow(sp, A, 1.6, 1.0)], gale_back(A, B))


def stormlancer_rend(A, B):
    # 렌딩 게일: 가운데 어두운 적 몸에 비스듬히 박힌 바람 창 다섯 · 창마다 바깥으로 뽑히는 줄 · 몸 둘레 바람 소용돌이 · 아래 번쩍
    foe = mask()
    disc(foe, 16.0, 11.0, 3.6)
    poly(foe, [(10.5, 27.5), (12.0, 15.0), (20.0, 15.0), (21.5, 27.5)])
    sw = swirl_mask(16.0, 19.0, 11.5, arms=2, w=1.1, turn=1.0, rot=0.6)
    layers, glows = [], []
    pulls = mask()
    for ang in (-150, -110, -60, -20, 20):
        a = math.radians(ang)
        tip = (16.0 + math.cos(a) * 2.0, 19.0 + math.sin(a) * 4.0)
        tail = (16.0 + math.cos(a) * 13.0, 19.0 + math.sin(a) * 13.0)
        ls, bl = gale_spear(tail, tip, A, B, head=3.6, hw=1.3, shaft=0.9)
        layers += ls
        glows.append(glow(bl, B, 1.0, 0.7))
        line(pulls, [(16.0 + math.cos(a) * 14.0, 19.0 + math.sin(a) * 14.0), (16.0 + math.cos(a) * 16.5, 19.0 + math.sin(a) * 16.5)], 0.9)
    subject = stack(layer(sw, mix(A, WHITE, 0.3), A, None, None), layer(foe, (44, 60, 64), (12, 18, 22)), *layers, layer(pulls, WHITE, A))
    return finish(subject, glows + [glow(sw, A, 1.4, 0.8), glow(pulls, A, 1.2, 0.8)], gale_back(A, B))


def stormlancer_ult(A, B):
    # 아이 오브 더 스톰: 오른쪽에 솟은 거대한 회오리 (위로 넓어지는 띠 일곱 · 가운데 눈) · 왼쪽 아래에서 회오리 한가운데로 내지른 큰 창 · 눈의 번쩍 · 위 먹구름
    cx = 20.0
    tw = mask()
    for i in range(7):
        y = 28.5 - i * 3.7
        rx = 2.0 + i * 1.5
        crescent(tw, cx + math.sin(i * 1.1) * 0.8, y, rx, 1.1, 200 if i % 2 else 180, 340 if i % 2 else 360)
    cloud = mask()
    for (x, y, r) in ((11.0, 3.0, 3.4), (17.0, 2.0, 3.8), (24.0, 2.5, 3.6), (29.5, 3.5, 3.0)):
        disc(cloud, x, y, r)
    eye = swirl_mask(cx, 15.5, 4.6, arms=3, w=1.0, turn=1.2, rot=0.2)
    ls, bl = gale_spear((1.0, 30.0), (cx - 0.5, 16.0), A, B, head=6.0, hw=2.4, shaft=1.6)
    sp = mask()
    star(sp, cx, 15.5, 5.4, 0.16, 20)
    grey = (40, 70, 72)
    subject = stack(layer(cloud, mix(grey, WHITE, 0.25), mix(grey, (0, 0, 0), 0.4), mix(grey, WHITE, 0.45), None), layer(tw, mix(A, WHITE, 0.45), A, WHITE, None),
                    layer(eye, WHITE, A), *ls, layer(sp, WHITE, B))
    return finish(subject, [glow(tw, A, 1.8, 1.0), glow(bl, B, 1.2, 0.8), glow(sp, A, 1.8, 1.0)], gale_back(A, B))


# ------------------------------------------------------------ 드라군 (빛: 붉은 용 진홍 → 불꽃 주황 — 짙은 숯불 밤 바탕 · 불은 주홍) — 2026-11-06

WYRM_NIGHT = (22, 8, 5)
EMBER = (255, 92, 26)
EMBER_HI = (255, 220, 120)


def wyrm_back(A, B):
    return backdrop(mix(A, WYRM_NIGHT, 0.86), mix((70, 22, 10), A, 0.3))


def wyrm_spear(tail, tip, A, B, head=5.0, hw=1.9, shaft=1.3):
    """검붉은 자루 · 불꽃 창날 (드라군)"""
    sh, bl = _spear(tail, tip, head, hw, shaft)
    return [layer(sh, mix(A, (40, 10, 6), 0.35), mix(A, (0, 0, 0), 0.6)), layer(bl, WHITE, B, WHITE, mix(A, (0, 0, 0), 0.25), axis=(0.7, 0.7))], bl


def flame_mask(cx, base, h, w):
    """서 있는 불꽃 (아래가 둥글고 위로 뾰족)"""
    m = mask()
    pts = []
    for i in range(13):
        t = i / 12
        y = base - h * t
        ww = w * (math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) ** 0.8) * (1 - t * 0.85)
        pts.append((cx + ww, y))
    pts2 = [(cx - (x - cx), y) for (x, y) in pts[::-1]]
    poly(m, pts + pts2)
    return m


def dragon_mask(cx, cy, s, dive=False):
    """용의 혼 (앞모습 — 날개를 편 용): 몸 · 목 · 머리 · 날개 둘 · 꼬리 (s = 크기)"""
    m = mask()
    sg = -1 if dive else 1
    disc(m, cx, cy, 1.6 * s)
    line(m, [(cx, cy), (cx, cy - sg * 3.6 * s)], 1.6 * s)
    poly(m, [(cx - 1.2 * s, cy - sg * 3.4 * s), (cx, cy - sg * 5.6 * s), (cx + 1.2 * s, cy - sg * 3.4 * s)])
    for hs in (-1, 1):
        line(m, [(cx + hs * 0.7 * s, cy - sg * 3.8 * s), (cx + hs * 1.8 * s, cy - sg * 4.6 * s)], 0.5 * s)
    line(m, [(cx, cy + sg * 1.0 * s), (cx + 1.2 * s, cy + sg * 3.4 * s), (cx - 0.6 * s, cy + sg * 5.0 * s), (cx + 0.6 * s, cy + sg * 6.2 * s)], 0.9 * s)
    for side in (-1, 1):
        sx, sy = cx + side * 1.0 * s, cy - sg * 1.0 * s
        lift = -1.0 if not dive else 1.0
        wrist = (sx + side * 3.4 * s, sy + lift * 3.0 * s)
        tips = [(sx + side * 7.4 * s, sy + lift * 4.6 * s), (sx + side * 7.8 * s, sy + lift * 1.0 * s), (sx + side * 6.4 * s, sy - lift * 2.2 * s)]
        hip = (cx + side * 0.6 * s, cy + sg * 1.8 * s)
        poly(m, [(sx, sy), wrist, tips[0], (sx + side * 5.6 * s, sy + lift * 2.0 * s), tips[1], (sx + side * 5.0 * s, sy - lift * 0.4 * s), tips[2],
                 (sx + side * 3.0 * s, sy - lift * 1.8 * s), hip])
    return m


def dragon_bones(cx, cy, s, dive=False):
    m = mask()
    sg = -1 if dive else 1
    for side in (-1, 1):
        sx, sy = cx + side * 1.0 * s, cy - sg * 1.0 * s
        lift = -1.0 if not dive else 1.0
        wrist = (sx + side * 3.4 * s, sy + lift * 3.0 * s)
        line(m, [(sx, sy), wrist], 0.45 * s)
        for tp in ((sx + side * 7.4 * s, sy + lift * 4.6 * s), (sx + side * 7.8 * s, sy + lift * 1.0 * s), (sx + side * 6.4 * s, sy - lift * 2.2 * s)):
            line(m, [wrist, tp], 0.3 * s)
    line(m, [(cx, cy - sg * 4.6 * s), (cx, cy + sg * 2.0 * s)], 0.35 * s)
    return m


def dragoon_tail(A, B):
    # 웜테일: 아래 둘레를 휩쓴 큰 고리 호 (자루 자국) · 위에서 거꾸로 내리꽂는 창 · 꽂힌 자리 번쩍 · 위로 띄워진 적 둘 (작은 덩이)
    sweep = mask()
    crescent(sweep, 16.0, 26.0, 13.5, 2.6, 160, 380)
    foes = mask()
    for (x, y) in ((6.0, 12.0), (26.0, 11.0)):
        disc(foes, x, y, 1.9)
        poly(foes, [(x - 2.0, y + 4.6), (x - 1.4, y + 0.6), (x + 1.4, y + 0.6), (x + 2.0, y + 4.6)])
    lifts = wind_lines([[(6.0, 22.0), (6.0, 18.0)], [(26.0, 21.0), (26.0, 17.0)]], 0.8)
    ls, bl = wyrm_spear((16.0, 1.5), (16.0, 25.5), A, B, head=6.0, hw=2.2, shaft=1.4)
    hit = mask()
    star(hit, 16.0, 26.5, 5.6, 0.16, 0)
    subject = stack(layer(sweep, mix(A, WHITE, 0.35), mix(A, B, 0.4), WHITE, None, axis=(1, 0)), layer(foes, (60, 44, 60), (20, 12, 22)), layer(lifts, WHITE, A),
                    *ls, layer(hit, WHITE, A))
    return finish(subject, [glow(sweep, A, 1.6, 0.9), glow(bl, A, 1.2, 0.8), glow(hit, EMBER, 1.8, 1.0)], wyrm_back(A, B))


def dragoon_jump(A, B):
    # 하이 점프: 왼쪽 아래에서 곧장 솟구친 금빛 자취 (위로) · 하늘의 작은 용의 혼 · 오른쪽 아래 겨눈 고리로 비스듬히 내리꽂는 창 (불꽃 꼬리)
    rise = wind_lines([[(5.0, 30.0), (5.0, 11.0)], [(2.6, 28.0), (2.6, 17.0)], [(7.4, 28.0), (7.4, 17.0)]], 0.55)
    dg = dragon_mask(7.0, 6.0, 0.8)
    target = mask()
    d = ImageDraw.Draw(target)
    d.ellipse([P(17.0, 26.0), P(31.0, 31.0)], fill=255)
    d.ellipse([P(19.0, 27.2), P(29.0, 29.8)], fill=0)
    trail = mask()
    line(trail, [(9.0, 6.0), (16.0, 14.0)], 2.6)
    line(trail, [(10.0, 9.0), (17.0, 16.5)], 1.2)
    ls, bl = wyrm_spear((12.0, 9.5), (23.5, 27.5), A, B, head=6.0, hw=2.2, shaft=1.4)
    hit = mask()
    star(hit, 24.0, 28.5, 4.4, 0.18, 0)
    subject = stack(layer(rise, WHITE, A, None, None), layer(dg, mix(A, WHITE, 0.4), A, WHITE, None), layer(trail, EMBER_HI, EMBER), layer(target, mix(A, WHITE, 0.3), A),
                    *ls, layer(hit, WHITE, A))
    return finish(subject, [glow(rise, A, 1.4, 0.8), glow(dg, A, 1.4, 0.9), glow(trail, EMBER, 1.6, 0.9), glow(hit, EMBER, 1.6, 1.0)], wyrm_back(A, B))


def dragoon_impale(A, B):
    # 임페일: 왼쪽 아래에서 위로 높이 치켜든 창 · 창끝에 꿰인 어두운 적 · 오른쪽으로 크게 휘어 땅으로 내리꽂는 화살 호 · 오른쪽 아래 땅의 충돌 (번쩍 · 금)
    ls, bl = wyrm_spear((6.0, 30.0), (12.0, 5.0), A, B, head=5.4, hw=2.0, shaft=1.3)
    foe = mask()
    disc(foe, 12.6, 4.0, 2.4)
    poly(foe, [(9.0, 12.0), (10.4, 6.0), (15.0, 6.0), (16.4, 12.0)])
    arc = mask()
    crescent(arc, 20.0, 16.0, 10.5, 2.0, 230, 395)
    head = mask()
    poly(head, [(27.0, 24.0), (31.0, 21.0), (31.0, 27.5)])
    ground = mask()
    poly(ground, [(14.0, 29.5), (31.5, 29.5), (31.5, 31.0), (14.0, 31.0)])
    hit = mask()
    star(hit, 26.0, 27.5, 5.6, 0.16, 0)
    crack = wind_lines([[(26.0, 29.5), (21.5, 31.5)], [(26.0, 29.5), (30.5, 31.5)]], 0.8)
    subject = stack(layer(ground, mix(B, (40, 20, 20), 0.5), mix(B, (0, 0, 0), 0.6)), layer(arc, WHITE, A, WHITE, None), layer(head, WHITE, A), *ls,
                    layer(foe, (70, 50, 70), (24, 14, 26)), layer(crack, WHITE, EMBER), layer(hit, WHITE, EMBER))
    return finish(subject, [glow(arc, A, 1.4, 0.8), glow(bl, A, 1.2, 0.8), glow(hit, EMBER, 2.0, 1.0)], wyrm_back(A, B))


def dragoon_breath(A, B):
    # 드래곤 브레스: 왼쪽 위 용의 머리 (옆모습 — 뿔 · 벌린 턱) · 입에서 오른쪽 아래로 부채꼴로 퍼지는 불길 (불꽃 혀 · 덩이) · 아래 땅의 작은 불꽃
    head = mask()
    poly(head, [(1.0, 6.0), (5.0, 3.0), (10.5, 4.0), (14.0, 6.6), (10.0, 8.0), (14.0, 10.0), (9.0, 11.6), (4.0, 12.0), (1.0, 10.0)])
    horn = mask()
    line(horn, [(5.5, 3.6), (2.0, 0.5)], 1.2)
    line(horn, [(8.0, 3.6), (6.0, 0.4)], 1.0)
    eye = mask()
    disc(eye, 8.4, 5.6, 0.7)
    fire = mask()
    for i in range(6):
        a = math.radians(14 + i * 9)
        r0, r1 = 3.0, 17.0 + (i % 2) * 4.0
        x0, y0 = 13.5 + math.cos(a) * r0, 8.6 + math.sin(a) * r0
        x1, y1 = 13.5 + math.cos(a) * r1, 8.6 + math.sin(a) * r1
        line(fire, [(x0, y0), (x1, y1)], 1.6 + i * 0.3)
    blobs = mask()
    for (x, y, r) in ((22.0, 17.0, 3.2), (27.0, 22.0, 3.6), (19.0, 22.5, 2.8), (28.0, 14.5, 2.4)):
        disc(blobs, x, y, r)
    hot = mask()
    for (x, y, r) in ((21.5, 17.0, 1.6), (26.5, 22.0, 1.8)):
        disc(hot, x, y, r)
    small = lighter(flame_mask(10.0, 31.0, 6.0, 2.2), flame_mask(16.0, 31.0, 4.5, 1.8))
    subject = stack(layer(fire, EMBER_HI, EMBER), layer(blobs, mix(EMBER, A, 0.5), EMBER, EMBER_HI, None), layer(hot, WHITE, EMBER_HI),
                    layer(small, EMBER_HI, EMBER), layer(head, mix(A, WHITE, 0.3), mix(A, B, 0.45), WHITE, mix(B, (0, 0, 0), 0.4)), layer(horn, WHITE, A), layer(eye, WHITE, B))
    return finish(subject, [glow(lighter(fire, blobs), EMBER, 2.0, 1.0), glow(head, A, 1.2, 0.7)], wyrm_back(A, B))


def dragoon_ult(A, B):
    # 드래곤폴: 위에서 내리꽂는 거대한 금빛 용의 혼 (머리가 아래 · 날개를 위로) · 뒤로 끌리는 불꽃 줄 · 아래 큰 충돌 (번쩍) · 양옆 불기둥
    dg = dragon_mask(16.0, 12.0, 1.55, dive=True)
    bones = dragon_bones(16.0, 12.0, 1.55, dive=True)
    streaks = wind_lines([[(10.0, 0.5), (11.0, 5.0)], [(16.0, 0.0), (16.0, 3.5)], [(22.0, 0.5), (21.0, 5.0)]], 0.9)
    ground = mask()
    poly(ground, [(0.5, 29.5), (31.5, 29.5), (31.5, 31.5), (0.5, 31.5)])
    hit = mask()
    star(hit, 16.0, 26.5, 7.6, 0.14, 0)
    star(hit, 16.0, 26.5, 4.0, 0.2, 45)
    pillars = lighter(flame_mask(4.0, 30.0, 11.0, 2.4), flame_mask(28.0, 30.0, 11.0, 2.4), flame_mask(9.0, 30.0, 6.0, 1.8), flame_mask(23.0, 30.0, 6.0, 1.8))
    subject = stack(layer(ground, mix(B, (40, 20, 20), 0.5), mix(B, (0, 0, 0), 0.6)), layer(pillars, EMBER_HI, EMBER, WHITE, None), layer(streaks, EMBER_HI, EMBER),
                    layer(dg, mix(A, WHITE, 0.35), A, WHITE, mix(A, B, 0.6)), layer(bones, WHITE, mix(A, WHITE, 0.5)), layer(hit, WHITE, A))
    return finish(subject, [glow(dg, A, 1.8, 1.0), glow(pillars, EMBER, 1.8, 1.0), glow(hit, EMBER, 2.2, 1.0)], wyrm_back(A, B))


# ------------------------------------------------------------ 소울 리퍼 (빛: 영혼 하늘빛 → 짙은 남청 — 짙은 남청 밤 바탕 · 혼불은 흰 하늘빛) — 2026-11-06

SOUL_NIGHT = (3, 8, 18)


def soul_back(A, B):
    return backdrop(mix(B, SOUL_NIGHT, 0.72), mix(B, (24, 70, 120), 0.4))


def soul_scythe(hand_lo, top, A, B, blade=12.0, bw=3.2, side=1, shaft=1.5):
    """검푸른 자루 · 영혼빛 날 (소울 리퍼)"""
    sh, bl, edge = _scythe(hand_lo, top, blade=blade, bw=bw, side=side, shaft=shaft)
    return [layer(sh, mix(B, (60, 80, 110), 0.4), mix(B, (0, 0, 0), 0.55), mix(B, WHITE, 0.3), None),
            layer(bl, mix(A, WHITE, 0.55), mix(A, B, 0.35), WHITE, mix(B, (0, 0, 0), 0.3), axis=(1, 1)), layer(edge, WHITE, WHITE)], bl


def soulreaper_crescent(A, B):
    # 리핑 크레센트: 가운데 낫 · 몸 둘레를 두 바퀴 휘감는 초승달 고리 둘 · 왼쪽의 내달린 줄 · 빨려 드는 혼불 둘
    rings = mask()
    crescent(rings, 17.0, 17.0, 13.5, 2.8, -150, 120)
    crescent(rings, 17.0, 17.0, 9.5, 2.0, 30, 290)
    dash = wind_lines([[(1.0, 12.0), (7.0, 12.0)], [(0.5, 17.0), (5.5, 17.0)], [(1.5, 22.0), (6.5, 22.0)]], 0.8)
    ss, bl = soul_scythe((22.5, 28.5), (14.0, 5.0), A, B, blade=10.0, bw=3.0, side=1, shaft=1.4)
    s1 = _soul(28.0, 9.0, 3.0, 5.0, -1.0)
    s2 = _soul(5.5, 30.0, 2.8, 4.6, 1.0)
    subject = stack(layer(dash, WHITE, A, None, None, axis=(1, 0)), layer(rings, mix(A, WHITE, 0.45), A, WHITE, None, axis=(1, -1)), *ss,
                    layer(lighter(s1, s2), WHITE, A))
    return finish(subject, [glow(rings, A, 1.8, 1.0), glow(lighter(s1, s2), A, 1.4, 0.9)], soul_back(A, B))


def soulreaper_trespass(A, B):
    # 엄브럴 트레스패스: 검은 적 그림자 (빛 금이 쩍쩍 갈라짐) · 그 몸을 왼쪽 아래에서 오른쪽 위로 찢고 나가는 큰 베어 올림 · 등 뒤로 흩어지는 혼불
    foe = mask()
    disc(foe, 13.0, 10.0, 4.4)
    poly(foe, [(5.0, 30.0), (7.0, 15.0), (19.0, 15.0), (21.0, 30.0)])
    cracks = mask()
    for pts in (((13.0, 18.0), (10.0, 22.0), (11.5, 26.0)), ((13.0, 18.0), (17.0, 21.0), (16.0, 25.5)), ((13.0, 18.0), (12.0, 13.5)), ((13.0, 10.0), (15.5, 8.0))):
        line(cracks, list(pts), 0.8)
    slash = mask()
    crescent(slash, 9.0, 26.0, 20.0, 3.6, -80, -10)
    sp = mask()
    star(sp, 13.0, 18.0, 4.6, 0.18, 20)
    s1 = _soul(27.5, 13.0, 3.0, 5.0, 1.0)
    s2 = _soul(24.0, 6.0, 2.4, 4.0, 1.4)
    subject = stack(layer(foe, (34, 36, 58), (8, 8, 20), (70, 80, 110), None), layer(cracks, WHITE, A), layer(slash, WHITE, A, WHITE, None, axis=(1, -1)),
                    layer(sp, WHITE, A), layer(lighter(s1, s2), WHITE, A))
    return finish(subject, [glow(cracks, A, 1.4, 1.0), glow(slash, A, 1.8, 1.0), glow(lighter(s1, s2), A, 1.2, 0.8)], soul_back(A, B))


def soulreaper_harvest(A, B):
    # 바운티풀 하베스트: 가운데 땅에 꽂은 낫 (날이 위에서 휨) · 날 끝에서 사방의 혼불 넷으로 뻗은 영혼 줄 · 땅의 금
    ground = mask()
    poly(ground, [(0.5, 28.5), (31.5, 28.5), (31.5, 31.5), (0.5, 31.5)])
    ss, bl = soul_scythe((16.0, 29.5), (16.0, 5.0), A, B, blade=11.0, bw=3.2, side=1, shaft=1.6)
    tethers = mask()
    knot = (16.0, 9.0)
    souls = []
    for (x, y, lean) in ((4.0, 8.0, 1.0), (28.0, 14.0, -1.0), (4.5, 24.0, 1.0), (27.5, 26.0, -1.0)):
        line(tethers, [knot, (x, y - 2.0)], 0.7)
        souls.append(_soul(x, y + 1.5, 3.0, 5.0, lean))
    sm = lighter(*souls)
    crack = wind_lines([[(16.0, 29.0), (11.0, 31.5)], [(16.0, 29.0), (21.5, 31.5)]], 0.7)
    hit = mask()
    star(hit, 16.0, 9.0, 3.6, 0.2, 0)
    subject = stack(layer(ground, mix(B, (30, 30, 40), 0.5), mix(B, (0, 0, 0), 0.6)), layer(tethers, WHITE, A), layer(crack, WHITE, A), *ss, layer(sm, WHITE, A),
                    layer(hit, WHITE, A))
    return finish(subject, [glow(tethers, A, 1.2, 0.9), glow(sm, A, 1.6, 1.0)], soul_back(A, B))


def soulreaper_lantern(A, B):
    # 랜턴 오브 소울즈: 왼쪽 위에 매달린 등불 (넓은 지붕 · 띠 · 기둥 틀 · 눈 둘의 영혼 불꽃) · 오른쪽 아래로 던진 사슬 끝의 갈고리
    frame = mask()
    ring(frame, 10.0, 2.2, 1.5, 0.6)
    line(frame, [(10.0, 3.6), (10.0, 5.2)], 0.7)
    poly(frame, [(3.4, 9.6), (10.0, 4.8), (16.6, 9.6)])
    poly(frame, [(4.4, 9.4), (15.6, 9.4), (15.6, 10.9), (4.4, 10.9)])
    poly(frame, [(4.4, 19.4), (15.6, 19.4), (15.6, 20.9), (4.4, 20.9)])
    poly(frame, [(6.0, 20.9), (14.0, 20.9), (12.6, 22.6), (7.4, 22.6)])
    line(frame, [(5.6, 10.9), (5.6, 19.4)], 1.0)
    line(frame, [(14.4, 10.9), (14.4, 19.4)], 1.0)
    bars = mask()
    line(bars, [(10.0, 10.9), (10.0, 13.0)], 0.5)
    glass = mask()
    poly(glass, [(6.1, 10.9), (13.9, 10.9), (13.9, 19.4), (6.1, 19.4)])
    fl = _soul(10.0, 18.8, 3.8, 6.6, 0.3)
    eyes = mask()
    disc(eyes, 9.2, 16.2, 0.5)
    disc(eyes, 10.8, 16.2, 0.5)
    chain = mask()
    pts = [(15.0, 17.0), (19.0, 19.5), (23.0, 22.5), (26.0, 25.5)]
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        ring(chain, (x0 + x1) / 2, (y0 + y1) / 2, 1.5, 0.6)
    hook = mask()
    crescent(hook, 26.0, 26.5, 3.6, 1.6, -30, 200)
    subject = stack(layer(glass, mix(B, (0, 0, 0), 0.35), mix(B, (0, 0, 0), 0.7)), layer(fl, WHITE, A), layer(eyes, mix(B, (0, 0, 0), 0.4), B),
                    layer(bars, mix(A, B, 0.3), B), layer(frame, mix(A, WHITE, 0.55), mix(A, B, 0.2), WHITE, None), layer(chain, mix(A, WHITE, 0.4), mix(A, B, 0.4)),
                    layer(hook, WHITE, A, WHITE, None))
    return finish(subject, [glow(fl, A, 2.2, 1.0), glow(hook, A, 1.0, 0.7)], soul_back(A, B))


def soulreaper_ult(A, B):
    # 페일 하베스트: 뒤의 커다란 두건 망령 사신 (빛나는 눈) · 그 낫이 앞을 크게 휩쓴 큰 고리 · 아래에서 솟는 혼불
    body, face = _hood(16.0, 2.0, 24.0, 26.0)
    face = mask()
    d = ImageDraw.Draw(face)
    d.ellipse([P(12.4, 6.4), P(19.6, 12.4)], fill=255)
    eyes = mask()
    disc(eyes, 14.5, 9.6, 1.0)
    disc(eyes, 17.5, 9.6, 1.0)
    sweep = mask()
    crescent(sweep, 16.0, 20.0, 14.5, 3.4, 10, 170)
    ss, bl = soul_scythe((5.0, 28.0), (6.0, 6.0), A, B, blade=9.0, bw=2.6, side=-1, shaft=1.3)
    souls = lighter(_soul(9.0, 31.0, 2.8, 4.6, 1.0), _soul(23.0, 31.0, 2.8, 4.6, -1.0), _soul(16.0, 31.5, 2.2, 3.6, 0.6))
    subject = stack(layer(body, mix(B, A, 0.3), mix(B, (0, 0, 0), 0.5), mix(A, WHITE, 0.2), None), layer(face, mix(B, (0, 0, 0), 0.75), mix(B, (0, 0, 0), 0.85)),
                    layer(eyes, WHITE, A), *ss, layer(sweep, WHITE, A, WHITE, None, axis=(1, 1)), layer(souls, WHITE, A))
    return finish(subject, [glow(body, A, 2.0, 0.7), glow(sweep, A, 1.8, 1.0), glow(eyes, A, 1.0, 1.0), glow(souls, A, 1.4, 0.9)], soul_back(A, B))


# ------------------------------------------------------------ 엑시큐셔너 (빛: 해 질 녘 금빛 → 짙은 보라 — 해 진 뒤의 자줏빛 바탕) — 2026-11-06

DUSK_SKY = (16, 4, 16)
DUSK_GOLD = (255, 196, 92)


def dusk_back(A, B):
    return backdrop(mix(B, DUSK_SKY, 0.72), mix((110, 44, 40), B, 0.45))


def dusk_scythe(hand_lo, top, A, B, blade=12.0, bw=3.2, side=1, shaft=1.5):
    """검보라 자루 · 금빛 날 (엑시큐셔너)"""
    sh, bl, edge = _scythe(hand_lo, top, blade=blade, bw=bw, side=side, shaft=shaft)
    return [layer(sh, mix(B, (90, 60, 80), 0.3), mix(B, (0, 0, 0), 0.6), mix(B, WHITE, 0.25), None),
            layer(bl, mix(A, WHITE, 0.45), mix(A, B, 0.3), WHITE, mix(B, (0, 0, 0), 0.3), axis=(1, 1)), layer(edge, WHITE, WHITE)], bl


def executioner_condemn(A, B):
    # 컨뎀: 오른쪽 위에서 왼쪽 아래로 크게 비스듬히 벤 금빛 자국 · 아래 적 그림자 위의 선고 해골 · 날 끝 반짝임
    foe = mask()
    disc(foe, 21.0, 18.0, 3.6)
    poly(foe, [(14.5, 31.5), (16.0, 22.0), (26.0, 22.0), (27.5, 31.5)])
    sk, sk_eyes = skull_mask(21.0, 7.0, 0.62)
    slash = mask()
    crescent(slash, 26.0, 26.0, 22.0, 3.8, -170, -100)
    sp = mask()
    star(sp, 14.0, 17.0, 4.0, 0.18, 30)
    subject = stack(layer(foe, (58, 30, 56), (16, 6, 18), (100, 60, 96), None), layer(sk, WHITE, A, WHITE, None), layer(sk_eyes, B, B),
                    layer(slash, WHITE, A, WHITE, None, axis=(-1, 1)), layer(sp, WHITE, A))
    return finish(subject, [glow(slash, A, 1.8, 1.0), glow(sk, A, 1.4, 0.9)], dusk_back(A, B))


def executioner_stalk(A, B):
    # 섀도 스토크: 왼쪽 아래에서 적 머리 위를 넘는 보랏빛 그림자 호 · 오른쪽에서 내려서며 낫으로 등을 벤 금빛 자국 · 가운데 적 그림자
    arc = mask()
    crescent(arc, 15.0, 22.0, 12.5, 2.4, -175, -20)
    shades = mask()
    for (x, y, r) in ((4.0, 19.0, 2.0), (8.0, 11.5, 2.2), (15.0, 9.0, 2.3)):
        disc(shades, x, y, r)
    foe = mask()
    disc(foe, 14.0, 17.5, 3.2)
    poly(foe, [(8.5, 31.5), (10.0, 22.5), (18.0, 22.5), (19.5, 31.5)])
    ss, bl = dusk_scythe((29.0, 22.0), (24.0, 6.0), A, B, blade=8.0, bw=2.6, side=1, shaft=1.3)
    cut = mask()
    crescent(cut, 26.0, 20.0, 9.5, 2.4, 150, 220)
    subject = stack(layer(arc, mix(B, WHITE, 0.4), B, None, None, axis=(1, 0)), layer(shades, mix(B, WHITE, 0.3), B), layer(foe, (58, 30, 56), (16, 6, 18), (100, 60, 96), None),
                    *ss, layer(cut, WHITE, A, WHITE, None))
    return finish(subject, [glow(arc, B, 1.6, 0.9), glow(cut, A, 1.6, 1.0)], dusk_back(A, B))


def executioner_guillotine(A, B):
    # 길로틴: 위에서 떨어지는 거대한 단두대 날 (쇳덩이 · 해골 무늬 · 비스듬한 금빛 날) · 위로 끌리는 줄 · 아래 적 그림자 · 날 끝 섬광
    bar = mask()
    poly(bar, [(5.0, 4.0), (27.0, 4.0), (27.0, 8.0), (5.0, 8.0)])
    blade = mask()
    poly(blade, [(6.0, 8.0), (26.0, 8.0), (26.0, 11.0), (6.0, 20.0)])
    edge = mask()
    line(edge, [(26.0, 11.0), (6.0, 20.0)], 1.5)
    streaks = wind_lines([[(8.0, 0.0), (8.0, 3.0)], [(16.0, 0.0), (16.0, 3.0)], [(24.0, 0.0), (24.0, 3.0)]], 0.8)
    foe = mask()
    disc(foe, 16.0, 23.0, 3.2)
    poly(foe, [(10.0, 31.5), (11.5, 26.6), (20.5, 26.6), (22.0, 31.5)])
    sk, sk_eyes = skull_mask(16.0, 5.8, 0.32)
    hit = mask()
    star(hit, 16.0, 17.0, 5.0, 0.16, 0)
    subject = stack(layer(streaks, WHITE, A), layer(foe, (58, 30, 56), (16, 6, 18), (100, 60, 96), None), layer(blade, mix(B, WHITE, 0.35), B, mix(B, WHITE, 0.6), None, axis=(1, 1)),
                    layer(bar, mix(B, (60, 40, 60), 0.4), mix(B, (0, 0, 0), 0.5), mix(B, WHITE, 0.4), None), layer(sk, A, A), layer(edge, WHITE, A), layer(hit, WHITE, A))
    return finish(subject, [glow(edge, A, 1.6, 1.0), glow(blade, B, 1.4, 0.8), glow(hit, A, 1.6, 1.0)], dusk_back(A, B))


def executioner_lastday(A, B):
    # 원 라스트 데이: 지평선에 반쯤 진 금빛 해 (빛살) · 그 앞에 세워 꽂은 낫 그림자 · 위의 작은 해골
    sun = mask()
    disc(sun, 16.0, 24.0, 9.0)
    poly_cut = mask()
    poly(poly_cut, [(0.0, 24.0), (32.0, 24.0), (32.0, 32.0), (0.0, 32.0)])
    sun = ImageChops.subtract(sun, poly_cut)
    rays = mask()
    for i in range(7):
        a = math.radians(-170 + i * 26.6)
        line(rays, [(16.0 + math.cos(a) * 11.0, 24.0 + math.sin(a) * 11.0), (16.0 + math.cos(a) * 14.5, 24.0 + math.sin(a) * 14.5)], 1.0)
    horizon = mask()
    poly(horizon, [(0.5, 24.0), (31.5, 24.0), (31.5, 25.2), (0.5, 25.2)])
    ss, bl = dusk_scythe((21.0, 31.0), (21.0, 7.0), A, B, blade=9.0, bw=2.6, side=1, shaft=1.4)
    sk, sk_eyes = skull_mask(7.0, 6.0, 0.5)
    subject = stack(layer(rays, DUSK_GOLD, A), layer(sun, WHITE, A, WHITE, None), layer(horizon, mix(B, WHITE, 0.3), B), *ss, layer(sk, WHITE, A), layer(sk_eyes, B, B))
    return finish(subject, [glow(sun, A, 2.4, 1.0), glow(rays, A, 1.4, 0.8)], dusk_back(A, B))


def executioner_ult(A, B):
    # 비포 선셋: 땅의 거대한 금빛 X 자 처형 표식 (거친 붓 획 둘 · 둘레 원) · 위의 지는 해 · 가운데 섬광
    sun = mask()
    disc(sun, 16.0, 19.8, 8.0)
    xm = mask()
    for a, b in (((5.0, 9.5), (27.0, 30.0)), ((27.0, 9.5), (5.0, 30.0))):
        pts = [(a[0] + (b[0] - a[0]) * t / 12, a[1] + (b[1] - a[1]) * t / 12) for t in range(13)]
        for i in range(12):
            w = 1.2 + 2.6 * math.sin(math.pi * (i + 0.5) / 12) ** 0.6
            line(xm, [pts[i], pts[i + 1]], w)
    circ = mask()
    ring(circ, 16.0, 19.8, 12.2, 0.8)
    hit = mask()
    star(hit, 16.0, 19.8, 6.4, 0.14, 0)
    subject = stack(layer(sun, mix(B, A, 0.45), mix(B, (0, 0, 0), 0.2)), layer(circ, mix(A, WHITE, 0.3), mix(A, B, 0.4)),
                    layer(xm, WHITE, A, WHITE, mix(A, B, 0.4), axis=(1, 1)), layer(hit, WHITE, A))
    return finish(subject, [glow(xm, A, 2.0, 1.0), glow(sun, B, 2.4, 0.8), glow(hit, A, 1.6, 1.0)], dusk_back(A, B))


# ------------------------------------------------------------ 썬더로드 (빛: 금빛 → 하늘빛 — 짙은 폭풍 남청 바탕 · 가운데 금빛) — 2026-11-06

from job_skill_icons_four import CLOUD, CLOUD_LO, STONE, STONE_HI, STONE_LO, bolt_pts, hammer_layers, person  # noqa: E402 — 망치 · 번개 · 먹구름 (1차 성기사)

THUNDER_NIGHT = (5, 7, 22)


def thunder_back(A, B):
    return backdrop(mix(B, THUNDER_NIGHT, 0.88), mix(A, (80, 64, 20), 0.55))


def cloud_mask(blobs):
    m = mask()
    for x, y, r in blobs:
        disc(m, x, y, r)
    return m


def foe_mask(cx, feet, h=10.0):
    """적 그림자 (머리 · 어깨가 넓은 몸)"""
    m = mask()
    r = h * 0.2
    disc(m, cx, feet - h + r, r)
    poly(m, [(cx - h * 0.34, feet), (cx - h * 0.28, feet - h + r * 2.2), (cx + h * 0.28, feet - h + r * 2.2), (cx + h * 0.34, feet)])
    return m


FOE = ((92, 84, 120), (34, 30, 56), (140, 132, 170), None)  # 적 그림자 (어두운 바탕 위에서 보이게)


def thunderlord_arc(A, B):
    # 아크 스매시: 왼쪽 아래에서 휩쓴 망치 · 가운데를 가로지르는 큰 금빛 휩쓸기 · 오른쪽 끝에서 두 적에게 갈라져 튀는 번개
    sweep = mask()
    crescent(sweep, 16.0, 36.0, 24.0, 4.2, 214, 318)
    forks = mask()
    for (x1, y1, sd) in ((29.5, 5.0, 1), (30.0, 27.0, 2)):
        line(forks, bolt_pts(24.5, 16.5, x1, y1, 4, 1.4, sd), 1.0)
    f1 = foe_mask(29.5, 9.5, 7.0)
    f2 = foe_mask(29.0, 31.5, 7.0)
    sparks = mask()
    star(sparks, 29.5, 5.6, 3.0, 0.2, 0)
    star(sparks, 29.6, 26.5, 3.0, 0.2, 0)
    ham, hd = hammer_layers(A, B, 7.5, 18.5, 120, head_w=11.0, head_h=6.0, handle=10.5, shaft=1.7)
    subject = stack(layer(lighter(f1, f2), *FOE), layer(sweep, WHITE, A, WHITE, None, axis=(1, 0)), layer(forks, WHITE, mix(B, WHITE, 0.3)), *ham,
                    layer(sparks, WHITE, B))
    return finish(subject, [glow(sweep, A, 2.0, 1.0), glow(forks, B, 1.4, 1.0), glow(sparks, B, 1.2, 1.0)], thunder_back(A, B))


def thunderlord_step(A, B):
    # 썬더스텝: 왼쪽 위 하늘에서 내리꽂힌 굵은 벼락 · 그 자리에 꽂힌 망치 (머리가 아래) · 둘레를 감싼 하늘빛 보호막 반구 · 땅의 번쩍
    bolt = mask()
    line(bolt, bolt_pts(4.0, 0.0, 17.0, 22.0, 5, 2.4, 1), 2.4)
    dome = mask()
    crescent(dome, 17.0, 29.5, 12.5, 1.6, 180, 360, taper=False)
    ground = mask()
    poly(ground, [(1.0, 29.5), (31.0, 29.5), (31.0, 30.8), (1.0, 30.8)])
    ham, hd = hammer_layers(A, B, 17.0, 24.5, -70, head_w=10.0, head_h=5.5, handle=9.0, shaft=1.6)
    hit = mask()
    star(hit, 17.0, 28.0, 6.0, 0.14, 0)
    subject = stack(layer(ground, mix(A, (60, 50, 30), 0.6), mix(A, (0, 0, 0), 0.6)), layer(dome, mix(B, WHITE, 0.5), B, WHITE, None),
                    layer(bolt, WHITE, A, WHITE, None), *ham, layer(hit, WHITE, A))
    return finish(subject, [glow(bolt, A, 2.0, 1.0), glow(dome, B, 1.6, 0.9), glow(hit, A, 2.0, 1.0)], thunder_back(A, B))


def thunderlord_spire(A, B):
    # 스톰 스파이어: 위의 먹구름 · 가운데로 떨어지는 굵은 번개 기둥 (흰 심 · 들쭉날쭉한 가장자리) · 땅에 퍼지는 금빛 고리 둘 · 튀는 작은 벼락
    cloud = cloud_mask(((5.0, 4.5, 4.4), (11.0, 3.4, 5.0), (17.5, 3.6, 5.4), (24.0, 3.4, 5.0), (29.0, 5.0, 3.8)))
    pillar = mask()
    left = bolt_pts(13.0, 6.0, 13.6, 26.5, 6, 1.0, 1)
    right = bolt_pts(19.0, 6.0, 18.4, 26.5, 6, 1.0, 2)
    poly(pillar, left + right[::-1])
    core = mask()
    line(core, bolt_pts(16.0, 6.0, 16.0, 26.5, 6, 0.8, 1), 1.4)
    rings_ = mask()
    d = ImageDraw.Draw(rings_)
    for rx, ry in ((8.5, 2.6), (13.5, 4.0)):
        d.ellipse([P(16 - rx, 27 - ry), P(16 + rx, 27 + ry)], outline=255, width=int(1.0 * SS))
    sparks = mask()
    for (x0, y0, x1, y1) in ((11.0, 20.0, 5.0, 16.0), (21.0, 18.0, 27.5, 14.0)):
        line(sparks, bolt_pts(x0, y0, x1, y1, 3, 0.9), 0.8)
    subject = stack(layer(cloud, CLOUD, CLOUD_LO, mix(CLOUD, WHITE, 0.3), None), layer(rings_, mix(A, WHITE, 0.3), A), layer(sparks, WHITE, B),
                    layer(pillar, mix(A, WHITE, 0.5), A, WHITE, None, axis=(1, 0)), layer(core, WHITE, WHITE))
    return finish(subject, [glow(pillar, A, 2.4, 1.0), glow(rings_, A, 1.4, 0.9), glow(sparks, B, 1.0, 0.8)], thunder_back(A, B))


def thunderlord_head(A, B):
    # 썬더헤드: 위의 먹구름 · 그 아래 세워 든 망치 (머리가 위) · 구름에서 망치 양옆으로 떨어지는 벼락 둘 · 아래의 하늘빛 보호 고리
    cloud = cloud_mask(((6.0, 5.0, 4.4), (12.0, 3.8, 5.2), (19.0, 3.8, 5.4), (25.5, 4.6, 4.6)))
    bolts = mask()
    line(bolts, bolt_pts(7.0, 8.0, 5.0, 22.0, 4, 1.4, 1), 1.3)
    line(bolts, bolt_pts(25.0, 8.0, 27.0, 22.0, 4, 1.4, 2), 1.3)
    ring_ = mask()
    d = ImageDraw.Draw(ring_)
    d.ellipse([P(4.0, 24.0), P(28.0, 30.5)], outline=255, width=int(1.2 * SS))
    ham, hd = hammer_layers(A, B, 16.0, 13.5, 90, head_w=11.5, head_h=6.0, handle=11.5, shaft=1.8)
    sparks = mask()
    star(sparks, 16.0, 13.5, 3.2, 0.2, 45)
    subject = stack(layer(ring_, mix(B, WHITE, 0.4), B), layer(cloud, CLOUD, CLOUD_LO, mix(CLOUD, WHITE, 0.3), None), layer(bolts, WHITE, A), *ham,
                    layer(sparks, WHITE, B))
    return finish(subject, [glow(bolts, A, 1.6, 1.0), glow(ring_, B, 1.6, 0.9), glow(hd, A, 1.8, 0.8)], thunder_back(A, B))


def thunderlord_ult(A, B):
    # 던브레이커: 뒤에서 떠오르는 새벽 (금빛 빛살 · 테) · 가운데 돌로 굳은 적 (모난 잿빛 덩이) 이 금빛 금으로 쩍 갈라짐 · 튀는 돌 조각
    rays = mask()
    for i in range(9):
        a = math.radians(-180 + i * 22.5)
        poly(rays, [(16.0, 20.0), (16.0 + math.cos(a - 0.07) * 17, 20.0 + math.sin(a - 0.07) * 17), (16.0 + math.cos(a + 0.07) * 17, 20.0 + math.sin(a + 0.07) * 17)])
    sun = mask()
    ring(sun, 16.0, 20.0, 12.0, 1.2)
    stone = mask()
    poly(stone, [(16.5, 4.5), (20.5, 6.0), (21.0, 10.0), (24.5, 12.5), (25.5, 19.0), (23.5, 26.5), (9.0, 26.5), (7.0, 19.5), (8.5, 12.5), (12.0, 10.0),
                 (12.5, 6.0)])
    cracks = mask()
    for pts in (((16.0, 15.5), (13.5, 11.5), (14.5, 7.5)), ((16.0, 15.5), (20.0, 13.0), (23.5, 14.5)), ((16.0, 15.5), (11.5, 19.0), (9.0, 23.5)),
                ((16.0, 15.5), (19.0, 20.0), (18.0, 26.0)), ((16.0, 15.5), (22.0, 21.0))):
        line(cracks, list(pts), 0.9)
    burst = mask()
    star(burst, 16.0, 15.5, 5.5, 0.16, 0)
    ground = mask()
    poly(ground, [(1.0, 26.5), (31.0, 26.5), (31.0, 28.2), (1.0, 28.2)])
    shards = mask()
    for (x, y, r, a) in ((5.0, 9.0, 1.6, 20), (27.5, 8.0, 1.5, 70), (4.0, 22.0, 1.3, 40), (28.5, 21.5, 1.4, 10)):
        poly(shards, [(x + math.cos(math.radians(a + k * 120)) * r, y + math.sin(math.radians(a + k * 120)) * r) for k in range(3)])
    subject = stack(layer(rays, mix(A, WHITE, 0.2), mix(A, (120, 60, 30), 0.4)), layer(sun, WHITE, A), layer(ground, mix(A, (60, 50, 30), 0.6), mix(A, (0, 0, 0), 0.6)),
                    layer(stone, STONE_HI, STONE_LO, WHITE, mix(STONE_LO, (0, 0, 0), 0.4), axis=(0.6, 0.8)), layer(cracks, WHITE, A), layer(burst, WHITE, A),
                    layer(shards, STONE_HI, STONE_LO))
    return finish(subject, [glow(rays, A, 1.6, 0.7), glow(sun, A, 2.0, 0.9), glow(cracks, A, 1.4, 1.0), glow(burst, A, 1.8, 1.0)], thunder_back(A, B))


# ------------------------------------------------------------ 타이런트 (빛: 보랏빛 번개 → 핏빛 — 짙은 보랏빛 밤 바탕 · 빼앗는 기운은 핏빛) — 2026-11-06

TYRANT_NIGHT = (8, 2, 16)


def tyrant_back(A, B):
    return backdrop(mix(A, TYRANT_NIGHT, 0.86), mix(B, (70, 10, 34), 0.5))


def tyrant_hammer(A, B, cx, cy, ang, head_w=11.0, head_h=6.0, handle=12.0, shaft=1.7):
    """검보라 망치 (머리에 보랏빛 · 띠는 핏빛)"""
    return hammer_layers(A, (40, 20, 50), cx, cy, ang, head_w, head_h, handle, shaft)


def tyrant_pillage(A, B):
    # 필리지: 위에서 크게 휘둘러 (보랏빛 길) 내려친 망치 (머리가 오른쪽 아래) · 맞은 적 그림자 · 맞은 자리 섬광 · 적에게서 빨려 나오는 핏빛 방울
    foe = foe_mask(23.0, 31.5, 12.0)
    ham, hd = tyrant_hammer(A, B, 18.5, 15.5, -140, head_w=12.0, head_h=6.5, handle=11.0)
    hit = mask()
    star(hit, 22.5, 20.5, 6.0, 0.14, 20)
    drops = mask()
    for (x, y, r) in ((11.0, 24.0, 1.4), (7.0, 20.5, 1.2), (5.0, 27.0, 1.1)):
        drop(drops, x, y, r, ang=20)
    trail = mask()
    crescent(trail, 7.0, 25.0, 19.0, 2.6, -88, -22)
    subject = stack(layer(foe, *FOE), layer(trail, mix(A, WHITE, 0.3), A, None, None, axis=(1, 1)), *ham, layer(hit, WHITE, A), layer(drops, mix(B, WHITE, 0.3), B))
    return finish(subject, [glow(hit, A, 2.2, 1.0), glow(hd, A, 1.6, 0.8), glow(drops, B, 1.2, 0.9)], tyrant_back(A, B))


def tyrant_shackle(A, B):
    # 섀클 볼트: 왼쪽 아래 주먹에서 오른쪽 위 적으로 뻗은 보랏빛 번개 사슬 (고리 · 옆에서 본 마디가 번갈아 · 곁의 지그재그 번개) · 적을 감은 사슬 고리 둘
    p0, p1 = (6.5, 25.5), (20.5, 12.0)
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    ux, uy = (p1[0] - p0[0]) / L, (p1[1] - p0[1]) / L
    chain = mask()
    d = ImageDraw.Draw(chain)
    n = 6
    for i in range(n):
        t = (i + 0.5) / n
        cx, cy = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
        if i % 2 == 0:
            pts = [(cx + ux * 2.2 * math.cos(a) - uy * 1.3 * math.sin(a), cy + uy * 2.2 * math.cos(a) + ux * 1.3 * math.sin(a)) for a in [k * math.pi / 12 for k in range(24)]]
            d.polygon([P(x, y) for x, y in pts], outline=255, width=int(0.8 * SS))
        else:
            line(chain, [(cx - ux * 2.0, cy - uy * 2.0), (cx + ux * 2.0, cy + uy * 2.0)], 1.0)
    zig = mask()
    line(zig, bolt_pts(5.0, 23.0, 19.0, 9.5, 6, 1.3, 1), 0.7)
    fist = mask()
    disc(fist, 5.0, 27.0, 3.2)
    poly(fist, [(0.5, 31.5), (2.0, 28.0), (6.5, 29.0), (5.5, 31.5)])
    foe = foe_mask(25.0, 24.0, 15.0)
    wraps = mask()
    d = ImageDraw.Draw(wraps)
    for cy in (15.0, 19.5):
        d.ellipse([P(19.0, cy - 1.7), P(31.0, cy + 1.7)], outline=255, width=int(1.1 * SS))
    subject = stack(layer(foe, *FOE), layer(wraps, mix(A, WHITE, 0.45), A), layer(chain, mix(A, WHITE, 0.4), mix(A, (40, 20, 60), 0.3)), layer(zig, WHITE, A),
                    layer(fist, mix(A, (70, 50, 90), 0.5), mix(A, (0, 0, 0), 0.6), mix(A, WHITE, 0.3), None))
    return finish(subject, [glow(lighter(chain, zig), A, 1.6, 1.0), glow(wraps, A, 1.4, 0.9)], tyrant_back(A, B))


def tyrant_grip(A, B):
    # 킹스 그립: 오른쪽에서 뻗은 거대한 보랏빛 망령 주먹이 적을 꽉 쥠 (주먹 위로 적의 머리 · 아래로 버둥대는 다리) · 손가락 마디 줄 · 엄지 · 튀는 핏빛 방울
    arm = mask()
    poly(arm, [(31.5, 9.5), (31.5, 21.5), (22.0, 21.0), (22.0, 10.5)])
    fist = mask()
    poly(fist, [(9.0, 10.0), (23.5, 9.0), (25.0, 12.0), (25.0, 20.5), (23.0, 23.0), (9.5, 23.0), (7.0, 20.5), (7.0, 12.5)])
    knuckles = mask()
    for y in (13.4, 16.6, 19.8):
        line(knuckles, [(7.8, y), (16.0, y)], 0.7)
    thumb = mask()
    poly(thumb, [(10.0, 9.0), (21.0, 8.0), (22.5, 10.5), (21.0, 12.0), (11.0, 12.5), (9.0, 11.0)])
    head = mask()
    disc(head, 15.0, 5.5, 2.8)
    legs = mask()
    line(legs, [(13.0, 23.0), (11.0, 28.5), (12.0, 30.5)], 1.6)
    line(legs, [(17.0, 23.0), (18.5, 28.0), (20.5, 29.0)], 1.6)
    cuff = mask()
    line(cuff, bolt_pts(26.5, 9.5, 27.0, 21.5, 4, 1.2), 0.8)
    drops = mask()
    for (x, y, r, a) in ((4.0, 15.0, 1.2, 0), (5.0, 25.0, 1.1, -30), (4.0, 7.0, 1.0, 30), (26.0, 27.0, 1.1, -150)):
        drop(drops, x, y, r, ang=a)
    hand_cols = (mix(A, WHITE, 0.35), mix(A, (30, 10, 50), 0.35), WHITE, mix(A, (0, 0, 0), 0.5))
    subject = stack(layer(lighter(head, legs), *FOE), layer(arm, mix(A, (30, 10, 50), 0.2), mix(A, (0, 0, 0), 0.5), mix(A, WHITE, 0.2), None, axis=(1, 0)),
                    layer(fist, *hand_cols, axis=(0.3, 1)), layer(knuckles, mix(A, (0, 0, 0), 0.45), mix(A, (0, 0, 0), 0.6)), layer(thumb, *hand_cols, axis=(0.3, 1)),
                    layer(cuff, WHITE, A), layer(drops, mix(B, WHITE, 0.3), B))
    return finish(subject, [glow(lighter(fist, arm), A, 2.0, 0.9), glow(drops, B, 1.2, 0.9)], tyrant_back(A, B))


def tyrant_colossus(A, B):
    # 콜로서스: 머리 위의 보랏빛 번개 왕관 (뾰족 다섯 · 보석) · 아래 거대한 어깨 · 머리 그림자 (핏빛 눈) · 양옆 위로 자라는 화살
    crown = mask()
    pts = [(6.0, 12.0), (6.0, 4.5), (9.5, 8.5), (12.5, 2.0), (16.0, 7.5), (19.5, 2.0), (22.5, 8.5), (26.0, 4.5), (26.0, 12.0)]
    poly(crown, pts)
    gems = mask()
    for x in (11.0, 16.0, 21.0):
        disc(gems, x, 10.0, 1.1)
    body = mask()
    disc(body, 16.0, 18.5, 5.0)
    poly(body, [(2.0, 31.5), (4.0, 25.0), (10.0, 22.0), (22.0, 22.0), (28.0, 25.0), (30.0, 31.5)])
    eyes = mask()
    poly(eyes, [(12.8, 18.0), (15.2, 18.6), (15.0, 19.6), (13.0, 19.2)])
    poly(eyes, [(19.2, 18.0), (16.8, 18.6), (17.0, 19.6), (19.0, 19.2)])
    arrows = mask()
    for x in (3.5, 28.5):
        line(arrows, [(x, 21.0), (x, 14.5)], 1.0)
        poly(arrows, [(x - 2.0, 15.0), (x + 2.0, 15.0), (x, 12.0)])
    sparks = mask()
    for (x0, y0, x1, y1) in ((12.5, 2.0, 10.5, -1.0), (19.5, 2.0, 21.5, -1.0)):
        line(sparks, [(x0, y0), (x1, y1)], 0.7)
    subject = stack(layer(body, (46, 30, 60), (14, 6, 22), (90, 70, 110), None), layer(eyes, WHITE, B), layer(arrows, mix(B, WHITE, 0.3), B),
                    layer(crown, mix(A, WHITE, 0.45), A, WHITE, mix(A, (0, 0, 0), 0.4)), layer(gems, WHITE, B), layer(sparks, WHITE, A))
    return finish(subject, [glow(crown, A, 2.0, 1.0), glow(eyes, B, 1.0, 1.0), glow(arrows, B, 1.0, 0.7)], tyrant_back(A, B))


def tyrant_ult(A, B):
    # 스톨른 썬더: 가운데 세운 거대한 보랏빛 번개 망치 (머리가 위) · 둘레 가장자리의 핏빛 혼에서 망치 머리로 빨려 드는 번개 여섯
    ham, hd = tyrant_hammer(A, B, 16.0, 11.0, 90, head_w=16.0, head_h=8.5, handle=13.0, shaft=2.2)
    edge = mask()
    line(edge, bolt_pts(9.0, 9.0, 23.0, 13.0, 4, 1.0), 0.8)
    lines_ = mask()
    orbs = mask()
    for (x, y) in ((2.5, 4.0), (29.5, 4.0), (2.0, 17.0), (30.0, 17.0), (4.0, 28.5), (28.0, 28.5)):
        tx = 16.0 + (6.0 if x > 16 else -6.0)
        line(lines_, bolt_pts(x, y, tx, 11.0, 4, 1.1, 1 if x < 16 else 2), 0.8)
        disc(orbs, x, y, 1.6)
    subject = stack(layer(lines_, WHITE, A), layer(orbs, mix(B, WHITE, 0.3), B), *ham, layer(edge, WHITE, A))
    return finish(subject, [glow(lines_, A, 1.4, 1.0), glow(hd, A, 2.4, 1.0), glow(orbs, B, 1.2, 1.0)], tyrant_back(A, B))


# ------------------------------------------------------------ 세인트 (빛: 옅은 금빛 → 연둣빛 — 짙은 초록 밤 바탕 · 꽃잎은 분홍빛) — 2026-11-06

SAINT_NIGHT = (3, 14, 8)
PETAL_C = (255, 158, 204)
PETAL_HI = (255, 238, 246)
PETAL_LO = (190, 64, 126)


def saint_back(A, B):
    return backdrop(mix(B, SAINT_NIGHT, 0.88), mix(A, (60, 70, 30), 0.55))


def heart_pts(cx, cy, s, ang=0.0, steps=48):
    """하트 (가운데 cx, cy · 너비 2s · 높이 약 1.8s — 위가 갈라지고 아래가 뾰족, ang 도 기울임)"""
    k = s / 16.0
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    out = []
    for i in range(steps):
        t = i / steps * 2 * math.pi
        x = 16 * math.sin(t) ** 3 * k
        y = (-(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)) - 2.5) * k
        out.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
    return out


def heart_mask(cx, cy, s, ang=0.0):
    m = mask()
    poly(m, heart_pts(cx, cy, s, ang))
    return m


def petal(m, bx, by, length, width, ang):
    """꽃잎 하나: 뿌리 (bx, by) 에서 ang 쪽 (도 — 화면 좌표, -90 = 위) 으로 — 뿌리는 좁고 가운데가 넓고 끝이 뾰족"""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    left, right = [], []
    for i in range(13):
        t = i / 12
        w = width / 2 * (math.sin(math.pi * t) ** 0.85) * (1.0 - 0.2 * t)
        x, y = bx + ux * length * t, by + uy * length * t
        left.append((x + px * w, y + py * w))
        right.append((x - px * w, y - py * w))
    return poly(m, left + right[::-1])


def sparkles(pts):
    m = mask()
    for x, y, r in pts:
        star(m, x, y, r)
    return m


def saint_accord(A, B):
    # 하트 어코드: 아래의 금빛 연꽃 (세 겹) 에서 피어난 큰 빛의 심장 · 양옆 아군의 작은 심장 둘과 빛줄기로 이어짐 (체력을 고르게 나눔) · 반짝임
    back_p, side_p, mid_p = mask(), mask(), mask()
    for ang in (-160, -20):
        petal(back_p, 16.0, 30.5, 7.0, 3.4, ang)
    for ang in (-128, -52):
        petal(side_p, 16.0, 30.5, 8.0, 3.8, ang)
    petal(mid_p, 16.0, 30.5, 9.0, 4.4, -90)
    big = heart_mask(16.0, 13.0, 6.4)
    small = lighter(heart_mask(5.0, 20.5, 2.9, -14), heart_mask(27.0, 20.5, 2.9, 14))
    beams = mask()
    line(beams, [(12.0, 16.0), (6.5, 19.5)], 1.1)
    line(beams, [(20.0, 16.0), (25.5, 19.5)], 1.1)
    sp = sparkles(((4.5, 6.0, 2.0), (27.5, 5.0, 1.7), (26.0, 28.0, 1.2), (6.0, 28.5, 1.2)))
    subject = stack(layer(back_p, mix(A, (150, 110, 40), 0.35), mix(A, (90, 60, 20), 0.6)), layer(side_p, mix(A, WHITE, 0.1), mix(A, (150, 110, 40), 0.5), WHITE, None),
                    layer(mid_p, WHITE, A, WHITE, None), layer(beams, WHITE, mix(A, WHITE, 0.4)),
                    layer(small, mix(B, WHITE, 0.45), B, WHITE, mix(B, (0, 0, 0), 0.45)), layer(big, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.45)), layer(sp, WHITE, A))
    lotus = lighter(back_p, side_p, mid_p)
    return finish(subject, [glow(big, A, 2.4, 1.0), glow(beams, A, 1.2, 0.9), glow(small, B, 1.4, 0.9), glow(lotus, A, 1.2, 0.6)], saint_back(A, B))

def saint_veil(A, B):
    # 페탈 베일: 가운데 옅은 연둣빛 빛의 방패 (연 모양 — 아군을 감싼 보호막) · 그 둘레를 기울어진 고리로 감아 도는 분홍 꽃잎 여덟 (뒤 넷 · 앞 넷) · 반짝임
    from job_skill_icons_four import kite
    shield = kite(16.0, 16.0, 12.5, 17.0)
    inner = kite(16.0, 15.2, 6.5, 9.0)
    back_p, front_p = mask(), mask()
    for i in range(8):
        th = math.radians(i * 45 + 10)
        x, y = 16.0 + math.cos(th) * 13.0, 16.5 + math.sin(th) * 5.2 - math.cos(th) * 3.0
        tang = math.degrees(math.atan2(math.cos(th) * 5.2 + math.sin(th) * 3.0, -math.sin(th) * 13.0))
        petal(front_p if math.sin(th) > 0 else back_p, x, y, 5.6, 2.9, tang)
    sp = sparkles(((27.0, 4.5, 1.6), (5.0, 5.5, 1.3), (16.0, 2.5, 1.2), (28.0, 28.0, 1.1)))
    subject = stack(layer(back_p, mix(PETAL_C, (90, 30, 60), 0.35), PETAL_LO), layer(shield, mix(B, WHITE, 0.5), mix(B, (20, 80, 40), 0.35), WHITE, mix(B, (0, 0, 0), 0.4)),
                    layer(inner, WHITE, mix(B, WHITE, 0.6)), layer(front_p, PETAL_HI, PETAL_C, WHITE, PETAL_LO, axis=(0.6, 0.8)), layer(sp, WHITE, A))
    return finish(subject, [glow(front_p, PETAL_C, 1.6, 1.0), glow(shield, B, 2.0, 0.9), glow(inner, B, 1.4, 0.8)], saint_back(A, B))

def butterfly(cx, cy, s):
    """빛나는 나비 (앞모습 — 위 날개 둘 · 아래 날개 둘 · 몸 · 더듬이), s = 크기 (날개 끝까지 약 7s/7)"""
    k = s / 7.0
    up, low = mask(), mask()
    petal(up, cx - 0.4 * k, cy - 0.2 * k, 7.4 * k, 6.4 * k, -148)
    petal(up, cx + 0.4 * k, cy - 0.2 * k, 7.4 * k, 6.4 * k, -32)
    petal(low, cx - 0.3 * k, cy + 0.6 * k, 5.0 * k, 4.4 * k, 128)
    petal(low, cx + 0.3 * k, cy + 0.6 * k, 5.0 * k, 4.4 * k, 52)
    body = mask()
    line(body, [(cx, cy - 3.6 * k), (cx, cy + 3.8 * k)], 1.3 * k)
    line(body, [(cx - 0.2 * k, cy - 3.4 * k), (cx - 2.2 * k, cy - 6.8 * k)], 0.6 * k)
    line(body, [(cx + 0.2 * k, cy - 3.4 * k), (cx + 2.2 * k, cy - 6.8 * k)], 0.6 * k)
    spots = mask()
    disc(spots, cx - 3.8 * k, cy - 2.6 * k, 1.1 * k)
    disc(spots, cx + 3.8 * k, cy - 2.6 * k, 1.1 * k)
    return up, low, body, spots


def saint_wing(A, B):
    # 글리머윙: 오른쪽 위의 빛나는 나비 · 왼쪽 아래에서 적 (금빛) 과 아군 (연둣빛) 을 번갈아 튀어 오른 세 번의 빛 자국 · 튄 자리마다 반짝임
    up, low, body, spots = butterfly(20.5, 12.0, 9.0)
    path = mask()
    hops = []
    seq = [(2.5, 30.0), (7.5, 27.0), (12.0, 23.5), (16.5, 18.5)]
    for (x0, y0), (x1, y1) in zip(seq, seq[1:]):
        mx, my = (x0 + x1) / 2, min(y0, y1) - 3.2
        arc = [((1 - t) ** 2 * x0 + 2 * (1 - t) * t * mx + t * t * x1, (1 - t) ** 2 * y0 + 2 * (1 - t) * t * my + t * t * y1) for t in [i / 10 for i in range(11)]]
        line(path, arc, 0.8)
        hops.append((x1, y1))
    foe_sp = sparkles(((7.5, 27.0, 2.2), (16.5, 18.5, 1.2)))
    ally_sp = sparkles(((12.0, 23.5, 2.0), (2.5, 30.0, 1.2)))
    glints = sparkles(((28.5, 26.0, 1.4), (5.0, 6.0, 1.5), (27.5, 29.5, 1.0)))
    subject = stack(layer(path, mix(A, WHITE, 0.3), mix(B, WHITE, 0.2)), layer(foe_sp, WHITE, A), layer(ally_sp, WHITE, B), layer(glints, WHITE, A),
                    layer(low, mix(PETAL_C, WHITE, 0.3), PETAL_C, WHITE, PETAL_LO), layer(up, WHITE, A, WHITE, mix(A, (120, 80, 20), 0.5), axis=(0.4, 1)),
                    layer(spots, mix(B, WHITE, 0.5), B), layer(body, mix(A, (90, 60, 20), 0.5), mix(A, (40, 24, 8), 0.7)))
    return finish(subject, [glow(lighter(up, low), A, 2.0, 1.0), glow(path, A, 1.0, 0.8), glow(lighter(foe_sp, ally_sp), B, 1.2, 0.9)], saint_back(A, B))


def lemniscate(cx, cy, a, steps=72):
    """∞ 꼴 (가운데 cx, cy · 반 너비 a)"""
    out = []
    for i in range(steps + 1):
        t = i / steps * 2 * math.pi
        dn = 1 + math.sin(t) ** 2
        out.append((cx + a * math.cos(t) / dn, cy + a * math.sin(t) * math.cos(t) / dn * 1.25))
    return out


def saint_bond(A, B):
    # 소울본드: 두 심장 (나 — 금빛 · 아군 — 연둣빛) 을 ∞ 꼴로 감아 잇는 빛의 리본 · 가운데 매듭의 반짝임 · 둘레 꽃잎
    ribbon = mask()
    line(ribbon, lemniscate(16.0, 16.5, 14.0), 1.5)
    h1 = heart_mask(8.6, 16.5, 4.4, -10)
    h2 = heart_mask(23.4, 16.5, 4.4, 10)
    knot = sparkles(((16.0, 16.5, 3.2),))
    petals = mask()
    petal(petals, 5.0, 5.0, 3.6, 2.2, -40)
    petal(petals, 27.0, 28.0, 3.6, 2.2, 140)
    petal(petals, 26.0, 5.5, 3.0, 1.9, -120)
    sp = sparkles(((5.5, 28.0, 1.4), (16.0, 4.5, 1.2), (16.0, 28.5, 1.2)))
    subject = stack(layer(petals, PETAL_HI, PETAL_C, None, PETAL_LO), layer(ribbon, WHITE, mix(A, WHITE, 0.35)), layer(h1, WHITE, A, WHITE, mix(A, (0, 0, 0), 0.45)),
                    layer(h2, mix(B, WHITE, 0.45), B, WHITE, mix(B, (0, 0, 0), 0.45)), layer(knot, WHITE, A), layer(sp, WHITE, A))
    return finish(subject, [glow(ribbon, A, 1.4, 1.0), glow(h1, A, 1.8, 0.9), glow(h2, B, 1.8, 0.9), glow(knot, A, 1.4, 1.0)], saint_back(A, B))


def saint_ult(A, B):
    # 에버블룸: 활짝 핀 큰 빛의 꽃 (분홍 바깥 꽃잎 여덟 · 금빛 안 꽃잎 여덟 · 흰 꽃술) · 흩날리는 꽃잎 · 반짝임
    outer = mask()
    for i in range(8):
        petal(outer, 16.0, 16.5, 13.0, 7.0, -90 + i * 45)
    inner = mask()
    for i in range(8):
        petal(inner, 16.0, 16.5, 8.2, 4.6, -67.5 + i * 45)
    center = mask()
    disc(center, 16.0, 16.5, 3.0)
    core = mask()
    disc(core, 16.0, 16.5, 1.5)
    veins = mask()
    for i in range(8):
        a = math.radians(-90 + i * 45)
        line(veins, [(16.0 + math.cos(a) * 4.0, 16.5 + math.sin(a) * 4.0), (16.0 + math.cos(a) * 10.5, 16.5 + math.sin(a) * 10.5)], 0.5)
    sp = sparkles(((4.0, 4.0, 1.8), (28.5, 28.0, 1.6), (28.0, 4.5, 1.2)))
    subject = stack(layer(outer, PETAL_HI, PETAL_C, WHITE, PETAL_LO, axis=(0.6, 0.8)), layer(veins, mix(PETAL_C, WHITE, 0.6), PETAL_HI),
                    layer(inner, WHITE, A, WHITE, mix(A, (130, 90, 30), 0.5)), layer(center, mix(A, WHITE, 0.4), A, WHITE, None), layer(core, WHITE, WHITE),
                    layer(sp, WHITE, A))
    return finish(subject, [glow(outer, PETAL_C, 2.0, 0.9), glow(inner, A, 1.8, 1.0), glow(center, A, 2.4, 1.0)], saint_back(A, B))


# ------------------------------------------------------------ 워록 (빛: 핏빛 → 금빛 — 검붉은 밤 바탕 · 피는 핏빛, 불티 · 테는 금빛) — 2026-11-06

WARLOCK_NIGHT = (14, 2, 6)
BLOOD_HI = (255, 112, 112)
BLOOD_C = (198, 14, 40)
BLOOD_LO = (86, 2, 14)


def warlock_back(A, B):
    return backdrop(mix(A, WARLOCK_NIGHT, 0.88), mix(A, (96, 10, 26), 0.5))


def warlock_lance(A, B):
    # 생귄 랜스: 왼쪽 아래에서 오른쪽 위로 날아가는 핏빛 창 (가시 둘 · 흰 맥) · 창으로 빨려 드는 핏방울 · 창끝의 금빛 반짝임
    tail, tip = (3.5, 28.5), (28.5, 3.5)
    L = math.hypot(tip[0] - tail[0], tip[1] - tail[1])
    ux, uy = (tip[0] - tail[0]) / L, (tip[1] - tail[1]) / L
    px, py = -uy, ux
    prof = [(0.0, 0.5), (0.4, 1.3), (0.6, 1.6), (0.64, 3.2), (0.68, 1.7), (0.74, 2.0), (0.77, 3.0), (0.82, 2.4), (1.0, 0.0)]
    left = [(tail[0] + ux * L * t + px * w, tail[1] + uy * L * t + py * w) for t, w in prof]
    right = [(tail[0] + ux * L * t - px * w, tail[1] + uy * L * t - py * w) for t, w in prof]
    lance = mask()
    poly(lance, left + right[::-1])
    vein = mask()
    line(vein, [(tail[0] + ux * L * 0.2, tail[1] + uy * L * 0.2), (tail[0] + ux * L * 0.9, tail[1] + uy * L * 0.9)], 0.55)
    drops = mask()
    for (x, y, r, a) in ((8.0, 16.5, 1.3, 50), (15.5, 26.5, 1.3, -130), (4.5, 21.0, 1.1, 30), (21.5, 29.0, 1.0, -150), (12.0, 9.5, 1.0, 70)):
        drop(drops, x, y, r, ang=a + 180)
    glint = sparkles(((27.0, 5.0, 3.4),))
    subject = stack(layer(drops, mix(A, WHITE, 0.3), A), layer(lance, BLOOD_HI, BLOOD_C, WHITE, BLOOD_LO, axis=(0.7, 0.7)), layer(vein, (255, 196, 196), BLOOD_HI),
                    layer(glint, WHITE, B))
    return finish(subject, [glow(lance, A, 2.0, 1.0), glow(drops, A, 1.2, 0.8), glow(glint, B, 1.4, 1.0)], warlock_back(A, B))


def warlock_mire(A, B):
    # 크림슨 마이어: 아래에 넓게 번진 핏물 웅덩이 (물결 고리 둘) · 가운데에서 솟구치는 가는 핏물 줄기 (꼭대기 방울) · 둘레로 튀어 오른 왕관 물보라 · 튀는 핏방울
    pool = mask()
    d = ImageDraw.Draw(pool)
    d.ellipse([P(2.5, 20.5), P(29.5, 29.5)], fill=255)
    for (x, y, r) in ((4.0, 27.0, 2.2), (28.0, 24.0, 2.0), (9.0, 29.2, 1.6), (24.0, 29.4, 1.5)):
        disc(pool, x, y, r)
    ripples = mask()
    d = ImageDraw.Draw(ripples)
    d.ellipse([P(6.5, 22.5), P(25.5, 28.5)], outline=255, width=int(0.8 * SS))
    jet = mask()
    poly(jet, [(14.2, 24.5), (14.9, 10.0), (17.1, 10.0), (17.8, 24.5)])
    disc(jet, 16.0, 7.6, 2.3)
    crown = mask()
    for k in range(7):
        a = math.radians(180 + 15 + k * 25)
        bx, by = 16.0 + math.cos(a) * 5.0, 23.0 + math.sin(a) * 1.6
        tx, ty = 16.0 + math.cos(a) * 9.0, 23.0 + math.sin(a) * 1.6 - (5.5 - abs(k - 3) * 0.9)
        pa = math.atan2(ty - by, tx - bx) + math.pi / 2
        poly(crown, [(bx + math.cos(pa) * 1.5, by + math.sin(pa) * 1.5), (tx, ty), (bx - math.cos(pa) * 1.5, by - math.sin(pa) * 1.5)])
        disc(crown, tx, ty - 0.6, 1.1)
    drops = mask()
    for (x, y, r, a) in ((7.0, 9.0, 1.2, -60), (25.0, 7.5, 1.2, -120), (4.5, 16.0, 1.0, -30), (27.5, 15.5, 1.0, -150), (11.5, 3.5, 0.9, -80), (21.0, 3.0, 0.9, -100)):
        drop(drops, x, y, r, ang=a + 180)
    subject = stack(layer(pool, BLOOD_C, BLOOD_LO, BLOOD_HI, None, axis=(0, 1)), layer(ripples, mix(A, WHITE, 0.35), A),
                    layer(crown, BLOOD_HI, BLOOD_C, None, BLOOD_LO), layer(jet, BLOOD_HI, BLOOD_C, mix(BLOOD_HI, WHITE, 0.4), BLOOD_LO, axis=(1, 0)),
                    layer(drops, mix(A, WHITE, 0.3), A))
    return finish(subject, [glow(lighter(jet, crown), A, 1.8, 1.0), glow(pool, A, 1.4, 0.8), glow(drops, A, 1.0, 0.8)], warlock_back(A, B))

def warlock_nova(A, B):
    # 블러드 노바: 가운데 고동치는 피의 심장 · 둘레 여섯 곳에서 심장으로 빨려 드는 핏방울 (꼬리가 바깥) · 그 바깥으로 터져 나가는 금빛 충격 고리 (네 조각)
    heart = heart_mask(16.0, 16.0, 7.0)
    shine = mask()
    disc(shine, 12.8, 12.4, 1.5)
    drops = mask()
    for i in range(6):
        a = math.radians(-90 + i * 60)
        x, y = 16.0 + math.cos(a) * 10.8, 16.0 + math.sin(a) * 10.8
        drop(drops, x, y, 1.4, ang=math.degrees(a))
    arcs = mask()
    for i in range(4):
        crescent(arcs, 16.0, 16.0, 14.8, 1.3, 15 + i * 90, 75 + i * 90, taper=True)
    subject = stack(layer(arcs, mix(B, WHITE, 0.45), B), layer(drops, mix(A, WHITE, 0.25), A), layer(heart, BLOOD_HI, BLOOD_C, WHITE, BLOOD_LO, axis=(0.5, 1)),
                    layer(shine, WHITE, mix(A, WHITE, 0.6)))
    return finish(subject, [glow(heart, A, 2.6, 1.0), glow(arcs, B, 1.2, 0.9), glow(drops, A, 1.0, 0.8)], warlock_back(A, B))

def warlock_ash(A, B):
    # 애시본: 세 갈래로 치솟는 핏빛 불기둥 (속은 금빛) · 그 안에서 두 팔을 V 자로 펼치고 되살아나는 그림자 · 흩날리는 불티
    f_out = lighter(flame_mask(16.0, 31.0, 28.0, 9.0), flame_mask(8.5, 31.0, 16.0, 5.0), flame_mask(23.5, 31.0, 18.0, 5.0))
    f_in = lighter(flame_mask(16.0, 31.0, 24.0, 7.0), flame_mask(9.0, 31.0, 9.0, 2.6), flame_mask(23.0, 31.0, 10.0, 2.6))
    sil = mask()
    disc(sil, 16.0, 14.0, 2.2)
    poly(sil, [(13.9, 16.8), (18.1, 16.8), (18.8, 27.5), (13.2, 27.5)])
    line(sil, [(14.4, 17.4), (10.2, 11.0)], 1.6)
    line(sil, [(17.6, 17.4), (21.8, 11.0)], 1.6)
    embers = mask()
    for (x, y, r) in ((5.0, 9.0, 1.0), (27.0, 7.0, 1.1), (7.5, 4.0, 0.8), (25.0, 14.0, 0.8), (21.0, 3.0, 0.9)):
        star(embers, x, y, r * 1.6, 0.3, 45)
    subject = stack(layer(f_out, mix(A, B, 0.35), A, mix(B, WHITE, 0.3), mix(A, (0, 0, 0), 0.4)), layer(f_in, WHITE, B), layer(sil, (46, 18, 22), (14, 4, 8)),
                    layer(embers, WHITE, B))
    return finish(subject, [glow(f_out, A, 2.2, 1.0), glow(f_in, B, 1.6, 0.9), glow(embers, B, 1.0, 0.9)], warlock_back(A, B))


def warlock_ult(A, B):
    # 블러드 뱅큇: 금빛 잔 (잔치) 에 차오른 핏물이 심장으로 솟고 · 잔 가장자리로 넘쳐 흐르는 핏물 · 둘레 금빛 반짝임
    cup = mask()
    bowl = [(7.5, 11.0), (24.5, 11.0)] + [(16.0 + 8.5 * math.cos(math.radians(a)), 11.0 + 8.0 * math.sin(math.radians(a))) for a in range(0, 181, 15)]
    poly(cup, bowl)
    poly(cup, [(15.0, 18.5), (17.0, 18.5), (17.4, 25.0), (14.6, 25.0)])
    poly(cup, [(13.5, 24.5), (18.5, 24.5), (22.0, 28.5), (10.0, 28.5)])
    rim = mask()
    d = ImageDraw.Draw(rim)
    d.ellipse([P(7.5, 9.6), P(24.5, 12.4)], fill=255)
    heart = heart_mask(16.0, 6.4, 4.6)
    spill = mask()
    for (x, y, r, a) in ((8.6, 14.5, 1.2, 90), (23.4, 15.5, 1.2, 90), (11.0, 17.5, 0.9, 90)):
        drop(spill, x, y, r, ang=a + 180)
    line(spill, [(8.4, 11.5), (8.6, 13.5)], 1.2)
    line(spill, [(23.6, 11.5), (23.4, 14.5)], 1.2)
    sp = sparkles(((4.0, 5.0, 1.7), (28.0, 4.0, 1.5), (27.5, 22.0, 1.2), (4.5, 23.0, 1.2)))
    gold_hi, gold_c, gold_lo = mix(B, WHITE, 0.45), B, mix(B, (110, 50, 10), 0.55)
    subject = stack(layer(cup, gold_hi, gold_c, WHITE, gold_lo, axis=(1, 0)), layer(rim, BLOOD_C, BLOOD_LO, BLOOD_HI, None), layer(spill, BLOOD_HI, BLOOD_C),
                    layer(heart, BLOOD_HI, BLOOD_C, WHITE, BLOOD_LO), layer(sp, WHITE, B))
    return finish(subject, [glow(heart, A, 2.4, 1.0), glow(cup, B, 1.4, 0.7), glow(sp, B, 1.0, 0.8)], warlock_back(A, B))


# ------------------------------------------------------------ 피닉스 가드 (빛: 푸른 불꽃 → 흰 불 — 깊은 남빛 밤 바탕) — 2026-11-06 (다시 — 푸른 불사조)

PH_NIGHT = (3, 8, 28)
FIRE_HI = (226, 246, 255)
FIRE_C = (84, 172, 255)
FIRE_LO = (22, 66, 176)


def phoenix_back(A, B):
    return backdrop(mix(A, PH_NIGHT, 0.86), mix(A, (40, 90, 200), 0.5))


def kite_shield(cx, cy, w, h, ang=0.0):
    from job_skill_icons_four import kite
    return kite(cx, cy, w, h, ang), kite(cx, cy, w, h, ang, 0.7)


def fire_wing(m, sx, sy, side, scale=1.0, lift=0.0, fold=0.0):
    """불꽃 날개 하나 (어깨 sx, sy 에서 깃 다섯 — side +1 = 오른쪽 · lift = 위로 더 (도) · fold = 아래 안쪽으로 접음 (도))"""
    for ang, L, w in ((-82, 10.5, 2.6), (-62, 12.0, 3.0), (-42, 11.6, 3.0), (-24, 10.2, 2.8), (-8, 8.2, 2.4)):
        a = ang - lift + fold * (1 + (ang + 82) / 74)
        if side < 0:
            a = 180 - a
        petal(m, sx, sy, L * scale, w * scale, a)
    return m


def steel_shield(A, B, cx, cy, w, h, ang=0.0):
    """푸른 강철 방패 (몸 · 테 · 가운데 불꽃 보석)"""
    outer, inner = kite_shield(cx, cy, w, h, ang)
    rim = ImageChops.subtract(outer, inner)
    gem = mask()
    d = ImageDraw.Draw(gem)
    d.ellipse([P(cx - w * 0.13, cy - h * 0.16), P(cx + w * 0.13, cy + h * 0.06)], fill=255)
    return outer, [layer(inner, mix(A, (30, 50, 110), 0.35), mix(A, (8, 16, 48), 0.6), mix(A, WHITE, 0.4), None, axis=(0.5, 1)),
                   layer(rim, mix(B, WHITE, 0.4), mix(A, B, 0.45), WHITE, mix(A, (0, 0, 0), 0.4)), layer(gem, WHITE, FIRE_C)]


def phoenix_bash(A, B):
    # 피닉스 배시: 머리 위로 치켜든 방패 양옆으로 펼친 불꽃 날개 · 아래로 내리쳐 땅에 번지는 푸른 충격 (금 · 섬광)
    wings = mask()
    fire_wing(wings, 18.0, 11.0, 1, 0.92, lift=8)
    fire_wing(wings, 14.0, 11.0, -1, 0.92, lift=8)
    outer, shield_layers = steel_shield(A, B, 16.0, 11.5, 9.0, 12.0)
    hit = mask()
    star(hit, 16.0, 27.0, 5.6, thin=0.16)
    crack = mask()
    line(crack, [(4.0, 28.5), (9.0, 27.6), (12.0, 28.4), (16.0, 27.4), (20.0, 28.4), (23.5, 27.6), (28.5, 28.6)], 0.9)
    down = mask()
    for x in (11.5, 16.0, 20.5):
        line(down, [(x, 19.5), (x + (16.0 - x) * 0.2, 24.0)], 0.9)
    subject = stack(layer(wings, FIRE_HI, FIRE_C, WHITE, FIRE_LO, axis=(0, 1)), *shield_layers, layer(down, FIRE_HI, FIRE_C), layer(crack, FIRE_HI, FIRE_C),
                    layer(hit, WHITE, FIRE_C))
    return finish(subject, [glow(wings, A, 2.2, 0.9), glow(hit, B, 2.0, 1.0)], phoenix_back(A, B))


def phoenix_wing(A, B):
    # 윙 커버: 작은 방패 (감싸인 아군) 를 앞에서 감싸 안는 두 불꽃 날개 (깃 끝이 아래 안쪽으로 접힘) · 왼쪽에서 날아든 불꽃 줄
    trail = mask()
    for i, y in enumerate((9.0, 13.0, 17.0)):
        line(trail, [(1.5 + i, y + 1.5), (7.0, y)], 1.1 - i * 0.15)
    wings = mask()
    fire_wing(wings, 19.5, 12.0, 1, 0.95, lift=-6, fold=26)
    fire_wing(wings, 12.5, 12.0, -1, 0.95, lift=-6, fold=26)
    outer, shield_layers = steel_shield(A, B, 16.0, 18.5, 7.0, 9.5)
    aura = mask()
    ring(aura, 16.0, 18.0, 8.5, 0.8)
    sp = sparkles(((27.5, 5.0, 1.6), (5.0, 26.5, 1.3), (28.0, 27.0, 1.2)))
    subject = stack(layer(trail, FIRE_HI, FIRE_C), layer(aura, mix(B, WHITE, 0.3), A), *shield_layers, layer(wings, FIRE_HI, FIRE_C, WHITE, FIRE_LO, axis=(0, 1)),
                    layer(sp, WHITE, B))
    return finish(subject, [glow(wings, A, 2.0, 0.9), glow(aura, B, 1.4, 0.8)], phoenix_back(A, B))


def phoenix_inferno(A, B):
    # 블루 인페르노: 땅의 푸른 불꽃 고리 (타원) 위로 솟는 불의 혀 (뒤쪽 · 앞쪽 — 방패를 사이에 둠) · 가운데 박힌 방패
    ringm = mask()
    d = ImageDraw.Draw(ringm)
    d.ellipse([P(2.0, 20.0), P(30.0, 29.5)], outline=255, width=int(1.5 * SS))
    back_t, front_t = mask(), mask()
    for k in range(10):
        a = math.radians(k * 36 + 18)
        x, y = 16.0 + math.cos(a) * 13.2, 24.75 + math.sin(a) * 4.5
        petal(back_t if math.sin(a) < 0 else front_t, x, y, 6.5 + (k % 3) * 1.6, 2.6, -90 + math.cos(a) * 14)
    outer, shield_layers = steel_shield(A, B, 16.0, 16.0, 8.0, 11.0)
    sp = sparkles(((4.5, 6.0, 1.4), (27.5, 5.0, 1.6)))
    fire = (FIRE_HI, FIRE_C, WHITE, FIRE_LO)
    subject = stack(layer(back_t, *fire, axis=(0, -1)), layer(ringm, FIRE_HI, FIRE_C), *shield_layers, layer(front_t, *fire, axis=(0, -1)), layer(sp, WHITE, B))
    both = ImageChops.lighter(back_t, front_t)
    return finish(subject, [glow(both, A, 2.2, 0.9), glow(ringm, B, 1.6, 0.9)], phoenix_back(A, B))


def phoenix_ignition(A, B):
    # 이그니션: 방패 뒤로 활짝 펼친 큰 불꽃 날개 · 방패 둘레로 솟는 불길 · 가슴의 흰 불
    wings = mask()
    fire_wing(wings, 18.5, 16.0, 1, 1.15, lift=-4)
    fire_wing(wings, 13.5, 16.0, -1, 1.15, lift=-4)
    flames = mask()
    for x, L in ((11.0, 6.0), (16.0, 8.5), (21.0, 6.0)):
        petal(flames, x, 16.5, L, 3.0, -90)
    outer, shield_layers = steel_shield(A, B, 16.0, 20.0, 9.0, 12.0)
    core = mask()
    star(core, 16.0, 18.5, 3.6, thin=0.2)
    subject = stack(layer(wings, FIRE_HI, FIRE_C, WHITE, FIRE_LO, axis=(0, 1)), layer(flames, FIRE_HI, FIRE_C, WHITE, FIRE_LO, axis=(0, -1)), *shield_layers,
                    layer(core, WHITE, FIRE_HI))
    return finish(subject, [glow(wings, A, 2.4, 0.9), glow(core, B, 1.6, 1.0)], phoenix_back(A, B))


def phoenix_ult(A, B):
    # 피닉스 디센트: 머리를 아래로 내리꽂는 푸른 불사조 (날개를 위로 넓게 펼친 V · 위로 끌리는 꼬리 불길 셋) · 땅에 닿는 섬광 · 고리
    wings = mask()
    for side in (-1, 1):
        for ang, L, w in ((-14, 12.0, 3.0), (-28, 13.5, 3.2), (-43, 13.0, 3.1), (-58, 10.5, 2.7)):
            a = ang if side > 0 else 180 - ang
            petal(wings, 16.0 + side * 1.2, 16.5, L, w, a)
    trail = mask()
    for x, L, ang in ((13.0, 7.0, -104), (16.0, 8.5, -90), (19.0, 7.0, -76)):
        petal(trail, x, 13.5, L, 2.4, ang)
    body = mask()
    petal(body, 16.0, 9.5, 12.0, 5.6, 90)
    head = mask()
    d = ImageDraw.Draw(head)
    d.ellipse([P(13.8, 18.6), P(18.2, 23.0)], fill=255)
    poly(head, [(14.8, 22.2), (17.2, 22.2), (16.0, 25.0)])
    hit = mask()
    star(hit, 16.0, 28.2, 4.6, thin=0.16)
    groundring = mask()
    d = ImageDraw.Draw(groundring)
    d.ellipse([P(4.0, 26.2), P(28.0, 30.6)], outline=255, width=int(1.0 * SS))
    fire = (FIRE_HI, FIRE_C, WHITE, FIRE_LO)
    subject = stack(layer(trail, *fire, axis=(0, -1)), layer(groundring, FIRE_HI, FIRE_C), layer(wings, *fire, axis=(0, -1)), layer(body, *fire),
                    layer(head, WHITE, FIRE_HI), layer(hit, WHITE, FIRE_C))
    return finish(subject, [glow(wings, A, 2.4, 0.9), glow(hit, B, 2.0, 1.0)], phoenix_back(A, B))


# ------------------------------------------------------------ 다크 나이트 (빛: 핏빛 → 검정 — 검붉은 밤 바탕 · 아가리의 핏빛 목구멍) — 2026-11-06 (다시 — 타락한 흑기사)

DK_NIGHT = (14, 2, 6)
DK_RED_HI = (255, 130, 130)
DK_RED = (226, 34, 56)
DK_RED_LO = (96, 6, 20)
DK_IRON_HI = (120, 96, 110)
DK_IRON = (46, 34, 42)
DK_IRON_LO = (12, 8, 12)
FANG = (250, 236, 226)


def darkknight_back(A, B):
    return backdrop(mix(A, DK_NIGHT, 0.9), mix(A, (90, 10, 24), 0.55))


def maw_shield(cx, cy, w, h, gap, eyes=True):
    """굶주린 방패의 아가리: 연 방패를 가로로 벌린 틈 (gap) · 그 속 핏빛 목구멍 · 위아래 송곳니 · 비스듬한 붉은 눈 · 테. 바깥 마스크와 층들"""
    from job_skill_icons_four import kite
    outer, inner = kite(cx, cy, w, h), kite(cx, cy, w, h, 0.0, 0.84)
    my = cy + h * 0.02
    band = mask()
    poly(band, [(cx - w, my - gap / 2), (cx + w, my - gap / 2), (cx + w, my + gap / 2), (cx - w, my + gap / 2)])
    body = ImageChops.subtract(outer, band)
    rim = ImageChops.subtract(ImageChops.subtract(outer, inner), band)
    throat = mask()
    d = ImageDraw.Draw(throat)
    d.ellipse([P(cx - w * 0.36, my - gap / 2 - 0.6), P(cx + w * 0.36, my + gap / 2 + 0.6)], fill=255)
    throat = ImageChops.multiply(throat, outer)
    fangs = mask()
    n = 5
    for i in range(n):
        x = cx - w * 0.3 + i * (w * 0.6) / (n - 1)
        big = i in (0, n - 1)
        L = (gap * 0.55 + 1.2) if big else (gap * 0.32 + 0.6)
        poly(fangs, [(x - 0.75, my - gap / 2), (x, my - gap / 2 + L), (x + 0.75, my - gap / 2)])
        poly(fangs, [(x - 0.75, my + gap / 2), (x, my + gap / 2 - L * 0.85), (x + 0.75, my + gap / 2)])
    fangs = ImageChops.multiply(fangs, throat)
    eye = mask()
    if eyes:
        for s in (-1, 1):
            ex, ey = cx + s * w * 0.19, cy - h * 0.22
            # 비스듬한 성난 눈 (안쪽 끝이 아래로)
            poly(eye, [(ex - s * w * 0.13, ey - 1.4), (ex + s * w * 0.12, ey + 0.2), (ex + s * w * 0.02, ey + 1.2), (ex - s * w * 0.12, ey + 0.2)])
    layers = [layer(body, (86, 74, 82), (28, 20, 26), (138, 122, 132), None, axis=(0.5, 1)), layer(throat, DK_RED, DK_RED_LO, DK_RED_HI, None),
              layer(fangs, FANG, mix(FANG, (120, 90, 80), 0.4)), layer(rim, (128, 36, 48), (42, 10, 16), (206, 84, 94), None), layer(eye, (255, 226, 210), DK_RED)]
    return outer, layers, throat, eye


def darkknight_crunch(A, B):
    # 크런치: 앞으로 내민 아가리 방패가 크게 벌어져 무는 순간 (송곳니 · 핏빛 목구멍 · 붉은 눈) · 앞의 물린 자국 섬광 · 튀는 핏방울
    outer, layers, throat, eye = maw_shield(14.5, 16.0, 17.0, 20.0, 6.5)
    snap = mask()
    star(snap, 27.0, 15.5, 4.6, thin=0.16)
    drops = mask()
    for (x, y, r, a) in ((26.0, 23.5, 1.2, 70), (29.0, 21.0, 0.9, 50), (24.0, 8.5, 1.0, -60)):
        drop(drops, x, y, r, ang=a)
    subject = stack(*layers, layer(snap, WHITE, DK_RED), layer(drops, DK_RED_HI, DK_RED, None, DK_RED_LO))
    return finish(subject, [glow(throat, A, 2.0, 0.9), glow(eye, A, 1.4, 1.0), glow(snap, A, 1.8, 1.0)], darkknight_back(A, B))


def darkknight_lunge(A, B):
    # 헝거 런지: 오른쪽으로 뛰어드는 아가리 방패 (반쯤 벌림) · 뒤로 끌리는 검붉은 줄 · 앞의 과녁을 문 섬광
    trail = mask()
    for i, y in enumerate((10.0, 14.5, 19.0, 23.5)):
        line(trail, [(1.5 + (i % 2) * 1.5, y + 1.0), (10.5, y)], 1.3 - i * 0.15)
    outer, layers, throat, eye = maw_shield(18.0, 16.5, 14.0, 17.0, 4.5)
    tgt = mask()
    ring(tgt, 28.0, 16.0, 3.2, 0.9)
    subject = stack(layer(trail, DK_RED_HI, DK_RED, None, DK_RED_LO), *layers, layer(tgt, DK_RED_HI, DK_RED))
    return finish(subject, [glow(trail, A, 1.6, 0.8), glow(throat, A, 1.8, 0.9), glow(eye, A, 1.2, 1.0)], darkknight_back(A, B))


def darkknight_feast(A, B):
    # 피스트: 아래 가운데의 크게 벌린 아가리 방패 · 거기서 둘레로 꿈틀대며 뻗는 검은 혀 넷 (붉은 핏줄 · 끝의 갈고리) · 감긴 과녁 고리
    tongues = mask()
    veins = mask()
    hooks = mask()
    for (ex, ey, bend) in ((4.0, 5.0, 1), (28.0, 5.0, -1), (2.5, 15.5, -1), (29.5, 15.5, 1)):
        pts = []
        for i in range(13):
            t_ = i / 12
            x = 16.0 + (ex - 16.0) * t_
            y = 19.5 + (ey - 19.5) * t_
            dx, dy = ex - 16.0, ey - 19.5
            L = math.hypot(dx, dy) or 1.0
            nx, ny = -dy / L, dx / L
            o = bend * math.sin(t_ * math.pi * 2.0) * 1.8 * (1 - t_ * 0.4)
            pts.append((x + nx * o, y + ny * o))
        line(tongues, pts, 2.0)
        line(veins, pts[2:10], 0.55)
        # 갈고리 끝: 끝에서 안쪽으로 휜 짧은 가시
        hx, hy = pts[-1]
        px, py = pts[-3]
        ux, uy = (hx - px), (hy - py)
        ul = math.hypot(ux, uy) or 1.0
        ux, uy = ux / ul, uy / ul
        line(hooks, [(hx, hy), (hx + ux * 1.2 - uy * bend * 1.6, hy + uy * 1.2 + ux * bend * 1.6)], 1.0)
    outer, layers, throat, eye = maw_shield(16.0, 22.5, 14.0, 16.0, 5.5, eyes=True)
    subject = stack(layer(tongues, (70, 52, 62), (22, 14, 20), (120, 100, 112), None), layer(hooks, (150, 120, 130), (60, 40, 50)), layer(veins, DK_RED_HI, DK_RED),
                    *layers)
    return finish(subject, [glow(tongues, A, 1.6, 0.7), glow(throat, A, 2.0, 0.9), glow(veins, A, 1.0, 0.9)], darkknight_back(A, B))


def darkknight_frenzy(A, B):
    # 언홀리 프렌지: 붉은 눈을 뜨고 침을 흘리며 벌어진 아가리 방패 · 둘레의 검붉은 불길 · 양옆 갈퀴 자국 셋
    flames = mask()
    for x, L in ((7.0, 9.0), (11.0, 11.0), (16.0, 13.0), (21.0, 11.0), (25.0, 9.0)):
        petal(flames, x, 20.0, L, 3.6, -90 + (x - 16.0) * 1.5)
    outer, layers, throat, eye = maw_shield(16.0, 18.0, 15.0, 18.0, 5.0)
    claws = mask()
    for i in range(3):
        line(claws, [(2.5 + i * 1.6, 24.0 - i * 0.4), (6.0 + i * 1.6, 30.0 - i * 0.4)], 0.8)
        line(claws, [(26.0 + i * 1.6, 24.0 + i * 0.4), (29.5 + i * 1.2, 30.0 + i * 0.4)], 0.8)
    drool = mask()
    drop(drool, 14.2, 22.8, 0.9, ang=90)
    drop(drool, 18.0, 23.2, 0.8, ang=90)
    subject = stack(layer(flames, DK_RED, DK_RED_LO, DK_RED_HI, None, axis=(0, -1)), *layers, layer(claws, DK_RED_HI, DK_RED), layer(drool, DK_RED_HI, DK_RED))
    return finish(subject, [glow(flames, A, 2.2, 0.85), glow(eye, A, 1.6, 1.0), glow(claws, A, 1.2, 0.9)], darkknight_back(A, B))


def darkknight_ult(A, B):
    # 어비설 메이: 갈라진 땅에서 솟은 거대한 아가리 (위아래 갈고리 턱 · 송곳니 · 붉은 목구멍) · 아래 땅의 붉은 금
    upper = mask()
    poly(upper, [(2.5, 15.0), (5.0, 7.0), (10.0, 3.0), (16.0, 2.0), (22.0, 3.0), (27.0, 7.0), (29.5, 15.0), (25.5, 12.0), (16.0, 10.5), (6.5, 12.0)])
    lower = mask()
    poly(lower, [(3.5, 19.0), (7.0, 21.5), (16.0, 22.5), (25.0, 21.5), (28.5, 19.0), (26.0, 25.5), (16.0, 27.5), (6.0, 25.5)])
    throat = mask()
    d = ImageDraw.Draw(throat)
    d.ellipse([P(6.0, 10.0), P(26.0, 23.0)], fill=255)
    fangs = mask()
    for x, big in ((8.0, True), (11.5, False), (14.5, False), (17.5, False), (20.5, False), (24.0, True)):
        L = 5.0 if big else 2.8
        top = 11.0 + abs(x - 16.0) * 0.08
        poly(fangs, [(x - 1.0, top), (x, top + L), (x + 1.0, top)])
        bot = 21.6 - abs(x - 16.0) * 0.05
        poly(fangs, [(x - 1.0, bot), (x, bot - L * 0.8), (x + 1.0, bot)])
    crack = mask()
    line(crack, [(1.5, 29.5), (6.0, 28.0), (10.0, 29.6), (16.0, 28.2), (22.0, 29.6), (26.0, 28.0), (30.5, 29.5)], 1.0)
    line(crack, [(16.0, 28.2), (15.4, 31.0)], 0.7)
    subject = stack(layer(throat, DK_RED, DK_RED_LO, DK_RED_HI, None), layer(upper, DK_IRON_HI, DK_IRON, mix(DK_IRON_HI, WHITE, 0.2), DK_IRON_LO),
                    layer(lower, DK_IRON_HI, DK_IRON, mix(DK_IRON_HI, WHITE, 0.2), DK_IRON_LO), layer(fangs, FANG, mix(FANG, (120, 90, 80), 0.4)),
                    layer(crack, DK_RED_HI, DK_RED))
    return finish(subject, [glow(throat, A, 2.4, 1.0), glow(crack, A, 1.4, 0.9)], darkknight_back(A, B))


ICONS = {
    "swordmaster_draw": swordmaster_draw,
    "swordmaster_parry": swordmaster_parry,
    "swordmaster_echo": swordmaster_echo,
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
    "warlord_stomp": warlord_stomp,
    "warlord_ult": warlord_ult,
    "frostraider_throw": frostraider_throw,
    "frostraider_leap": frostraider_leap,
    "frostraider_shatter": frostraider_shatter,
    "frostraider_jotunn": frostraider_jotunn,
    "frostraider_ult": frostraider_ult,
    "arcaneblade_crescent": arcaneblade_crescent,
    "arcaneblade_cross": arcaneblade_cross,
    "arcaneblade_tempest": arcaneblade_tempest,
    "arcaneblade_rift": arcaneblade_rift,
    "arcaneblade_ult": arcaneblade_ult,
    "swordcaller_strike": swordcaller_strike,
    "swordcaller_shift": swordcaller_shift,
    "swordcaller_orbit": swordcaller_orbit,
    "swordcaller_twin": swordcaller_twin,
    "swordcaller_ult": swordcaller_ult,
    "arcmage_chain": arcmage_chain,
    "arcmage_halo": arcmage_halo,
    "arcmage_vortex": arcmage_vortex,
    "arcmage_surge": arcmage_surge,
    "arcmage_ult": arcmage_ult,
    "necromancer_call": necromancer_call,
    "necromancer_yard": necromancer_yard,
    "necromancer_mist": necromancer_mist,
    "necromancer_blight": necromancer_blight,
    "necromancer_ult": necromancer_ult,
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
    "trickster_swap": trickster_swap,
    "trickster_dash": trickster_dash,
    "trickster_phantom": trickster_phantom,
    "trickster_palace": trickster_palace,
    "trickster_ult": trickster_ult,
    "pierrot_box": pierrot_box,
    "pierrot_spring": pierrot_spring,
    "pierrot_balloon": pierrot_balloon,
    "pierrot_doll": pierrot_doll,
    "pierrot_ult": pierrot_ult,
    "stormlancer_cross": stormlancer_cross,
    "stormlancer_drive": stormlancer_drive,
    "stormlancer_drill": stormlancer_drill,
    "stormlancer_rend": stormlancer_rend,
    "stormlancer_ult": stormlancer_ult,
    "dragoon_tail": dragoon_tail,
    "dragoon_jump": dragoon_jump,
    "dragoon_impale": dragoon_impale,
    "dragoon_breath": dragoon_breath,
    "dragoon_ult": dragoon_ult,
    "soulreaper_crescent": soulreaper_crescent,
    "soulreaper_trespass": soulreaper_trespass,
    "soulreaper_harvest": soulreaper_harvest,
    "soulreaper_lantern": soulreaper_lantern,
    "soulreaper_ult": soulreaper_ult,
    "executioner_condemn": executioner_condemn,
    "executioner_stalk": executioner_stalk,
    "executioner_guillotine": executioner_guillotine,
    "executioner_lastday": executioner_lastday,
    "executioner_ult": executioner_ult,
    "thunderlord_arc": thunderlord_arc,
    "thunderlord_step": thunderlord_step,
    "thunderlord_spire": thunderlord_spire,
    "thunderlord_head": thunderlord_head,
    "thunderlord_ult": thunderlord_ult,
    "tyrant_pillage": tyrant_pillage,
    "tyrant_shackle": tyrant_shackle,
    "tyrant_grip": tyrant_grip,
    "tyrant_colossus": tyrant_colossus,
    "tyrant_ult": tyrant_ult,
    "saint_accord": saint_accord,
    "saint_veil": saint_veil,
    "saint_wing": saint_wing,
    "saint_bond": saint_bond,
    "saint_ult": saint_ult,
    "warlock_lance": warlock_lance,
    "warlock_mire": warlock_mire,
    "warlock_nova": warlock_nova,
    "warlock_ash": warlock_ash,
    "warlock_ult": warlock_ult,
    "phoenix_bash": phoenix_bash,
    "phoenix_wing": phoenix_wing,
    "phoenix_inferno": phoenix_inferno,
    "phoenix_ignition": phoenix_ignition,
    "phoenix_ult": phoenix_ult,
    "darkknight_crunch": darkknight_crunch,
    "darkknight_lunge": darkknight_lunge,
    "darkknight_feast": darkknight_feast,
    "darkknight_frenzy": darkknight_frenzy,
    "darkknight_ult": darkknight_ult,
}
