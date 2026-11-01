#!/usr/bin/env python3
"""0.51.0 2차 직업 스킬 아이콘 — 검사 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트) (메이플식 네모 스킬 그림 32×32,
tools/job_skill_icons.py 와 같은 방식 — 그 스크립트가 함께 부름). 모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음).
색: 소드마스터 = 금빛 (첫째) · 은빛 (둘째), 블러드 나이트 = 짙은 피 (첫째) · 밝은 피 (둘째) — jobs.json advance."""
import math
import os
import sys

from PIL import ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (GOLD, GOLD_HI, GOLD_LO, STEEL, STEEL_HI, STEEL_LO, WHITE, backdrop, crescent, disc, finish, glow, layer, line, mask, mix,  # noqa: E402
                             poly, ring, stack, star, sword_layers)

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
}
