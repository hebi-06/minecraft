#!/usr/bin/env python3
"""2026-11-15 궁수 (팬텀 아처) 2차 직업 스킬 아이콘 — 아스널 킹 (금빛 문 · 보물) · 블레이드 레인저 (투영한 쌍검 · 단조 불빛): 32×32 메이플식 네모 그림.
색 = jobs.json 직업 빛 (A · B). tools/job_skill_icons.py 가 마지막에 불러 덮어씀."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (WHITE, disc, finish, glow, layer, line, mask, mix, ring, stack, star)  # noqa: E402
from job_skill_icons_phantom import DARK, back, lighter, sword_layers  # noqa: E402

GOLD_BLADE = (255, 226, 130)


def portal(cx, cy, r):
    """금빛 문: 겉 고리 · 안 고리 · 빛 판"""
    rim = mask()
    ring(rim, cx, cy, r, 1.1)
    inner = mask()
    ring(inner, cx, cy, r * 0.62, 0.6)
    fill = mask()
    disc(fill, cx, cy, r * 0.55)
    return rim, inner, fill


def portal_layers(A, B, cx, cy, r):
    rim, inner, fill = portal(cx, cy, r)
    return [layer(fill, mix(B, WHITE, 0.4), mix(A, B, 0.5)), layer(rim, mix(A, WHITE, 0.2), mix(A, DARK, 0.2)), layer(inner, WHITE, mix(B, WHITE, 0.5))], rim


def gold_sword(A, B, cx, cy, ang, L, w=1.0):
    """보물 (금빛 날 칼)"""
    return sword_layers(A, (255, 214, 110), cx, cy, ang, L, w)


def chain_mask(p0, p1, n):
    m = mask()
    for k in range(n):
        t = (k + 0.5) / n
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t
        ring(m, x, y, 1.5, 0.55)
    return m


# ------------------------------------------------------------ 아스널 킹


def arsenalking_volley(A, B):
    # 골든 볼리: 왼쪽 아래 문 넷 (부채꼴) 에서 오른쪽 위로 나오는 보물 넷
    layers, glows = [], []
    for (x, y) in ((5.5, 14.0), (9.0, 22.0), (14.5, 27.5), (4.0, 28.5)):
        pl, rim = portal_layers(A, B, x, y, 3.6)
        layers += pl
        glows.append(rim)
    swords = []
    for (x, y, L) in ((6.5, 13.0, 15.0), (10.0, 21.0, 15.0), (15.5, 26.5, 14.0), (5.0, 27.5, 13.0)):
        ls, bl, _ = gold_sword(A, B, x, y, 38, L, 0.8)
        layers += ls
        swords.append(bl)
    sp = mask()
    star(sp, 25.0, 6.5, 3.4, 0.22, 0)
    return finish(stack(*layers, layer(sp, WHITE, B)), [glow(lighter(*glows), A, 1.6, 0.9), glow(lighter(*swords), A, 1.2, 0.6)], back(A, B))


def arsenalking_chain(A, B):
    # 킹스 체인: 문 셋에서 뻗은 금빛 사슬이 가운데에서 묶음
    layers, rims = [], []
    pts = ((5.0, 7.0), (27.0, 8.0), (16.0, 28.5))
    for (x, y) in pts:
        pl, rim = portal_layers(A, B, x, y, 3.8)
        layers += pl
        rims.append(rim)
    chains = [chain_mask(p, (16.0, 16.5), 5) for p in pts]
    ch = lighter(*chains)
    core = mask()
    star(core, 16.0, 16.5, 4.0, 0.2, 45)
    return finish(stack(layer(ch, mix(A, WHITE, 0.35), mix(A, DARK, 0.25)), *layers, layer(core, WHITE, B)),
                  [glow(ch, A, 1.4, 0.8), glow(lighter(*rims), A, 1.6, 0.9)], back(A, B))


def arsenalking_rain(A, B):
    # 트레저 레인: 위의 문 넷에서 쏟아지는 보물 · 땅의 금빛 줄
    layers, rims, swords = [], [], []
    for (x, y) in ((6.0, 5.0), (13.5, 3.5), (21.0, 5.0), (27.5, 4.0)):
        pl, rim = portal_layers(A, B, x, y, 3.0)
        layers += pl
        rims.append(rim)
    for (x, y, L) in ((7.0, 8.0, 12.0), (14.5, 7.0, 14.0), (21.0, 9.0, 12.5), (27.0, 8.0, 11.0), (10.5, 14.0, 10.0), (24.0, 15.0, 10.0)):
        ls, bl, _ = gold_sword(A, B, x, y, -78, L, 0.7)
        layers += ls
        swords.append(bl)
    gnd = mask()
    line(gnd, [(2.0, 29.5), (30.0, 29.5)], 1.1)
    return finish(stack(layer(gnd, A, mix(A, DARK, 0.3)), *layers), [glow(lighter(*rims), A, 1.6, 0.9), glow(lighter(*swords), A, 1.2, 0.6)], back(A, B))


def arsenalking_judge(A, B):
    # 킹스 저지먼트: 위의 큰 문 · 내리꽂히는 거대한 보물 · 땅의 폭발
    pl, rim = portal_layers(A, B, 16.0, 6.0, 7.5)
    ls, bl, _ = gold_sword(A, B, 16.0, 4.0, -90, 24.0, 1.5)
    boom = mask()
    disc(boom, 16.0, 28.5, 5.0)
    core = mask()
    disc(core, 16.0, 28.5, 2.3)
    return finish(stack(*pl, layer(boom, mix(A, (255, 170, 80), 0.5), A), *ls, layer(core, WHITE, B)),
                  [glow(rim, A, 1.8, 1.0), glow(bl, A, 1.4, 0.7), glow(boom, A, 2.0, 1.0)], back(A, B))


def arsenalking_ult(A, B):
    # 아스널 게이트: 하늘을 덮은 문들 · 가운데 거대한 보물
    layers, rims = [], []
    for (x, y, r) in ((4.5, 5.0, 3.0), (11.0, 3.5, 3.0), (21.0, 3.5, 3.0), (27.5, 5.0, 3.0), (3.5, 13.0, 2.6), (28.5, 13.0, 2.6), (8.0, 20.0, 2.4), (24.0, 20.0, 2.4)):
        pl, rim = portal_layers(A, B, x, y, r)
        layers += pl
        rims.append(rim)
    big, brim = portal_layers(A, B, 16.0, 7.0, 6.0)
    ls, bl, _ = gold_sword(A, B, 16.0, 6.0, -90, 25.0, 1.6)
    return finish(stack(*layers, *big, *ls), [glow(lighter(*rims, brim), A, 1.8, 1.0), glow(bl, A, 1.4, 0.7)], back(A, B))


# ------------------------------------------------------------ 블레이드 레인저


def arc_mask(cx, cy, r, a0, a1, w, n=14):
    m = mask()
    for k in range(n):
        t0 = math.radians(a0 + (a1 - a0) * k / n)
        t1 = math.radians(a0 + (a1 - a0) * (k + 1) / n)
        line(m, [(cx + r * math.cos(t0), cy - r * math.sin(t0)), (cx + r * math.cos(t1), cy - r * math.sin(t1))], w)
    return m


def bladeranger_twin(A, B):
    # 트윈 슬래시: X 로 엇갈린 쌍검 · 두 베기 호
    a1 = arc_mask(16.0, 17.0, 12.0, 160, 20, 1.0)
    a2 = arc_mask(16.0, 15.0, 11.0, -160, -20, 0.8)
    l1, b1, _ = sword_layers(A, B, 5.0, 28.0, 50, 23.0, 1.0)
    l2, b2, _ = sword_layers(A, B, 27.0, 28.0, 130, 23.0, 1.0)
    sp = mask()
    star(sp, 16.0, 13.5, 4.0, 0.2, 45)
    arcs = lighter(a1, a2)
    return finish(stack(layer(arcs, mix(A, WHITE, 0.35), A), *l1, *l2, layer(sp, WHITE, B)), [glow(arcs, A, 1.6, 0.9), glow(lighter(b1, b2), A, 1.2, 0.6)], back(A, B))


def bladeranger_boomerang(A, B):
    # 블레이드 부메랑: 휘어 날아가 교차하는 쌍검 · 두 궤적
    t1 = arc_mask(16.0, 30.0, 14.0, 175, 95, 0.8)
    t2 = arc_mask(16.0, 30.0, 14.0, 5, 85, 0.8)
    l1, b1, _ = sword_layers(A, B, 9.0, 13.0, 20, 13.0, 0.85)
    l2, b2, _ = sword_layers(A, B, 23.0, 13.0, 160, 13.0, 0.85)
    sp = mask()
    star(sp, 16.0, 15.5, 3.8, 0.2, 0)
    tr = lighter(t1, t2)
    return finish(stack(layer(tr, mix(A, WHITE, 0.2), A), *l1, *l2, layer(sp, WHITE, B)), [glow(tr, A, 1.4, 0.8), glow(lighter(b1, b2), A, 1.2, 0.6)], back(A, B))


def bladeranger_storm(A, B):
    # 라이징 엣지 (2026-11-16 — 옛 블레이드 스톰 대신): 땅을 뚫고 솟는 불빛 칼 셋 · 달아오른 땅 · 위의 공중 X 베기
    boom = mask()
    disc(boom, 16.0, 31.0, 6.5)
    gnd = mask()
    line(gnd, [(1.5, 29.5), (30.5, 29.5)], 1.0)
    layers, blades = [], []
    for (x, a, L, w) in ((8.0, 104, 13.0, 0.75), (24.0, 76, 13.0, 0.75), (16.0, 90, 17.0, 0.95)):
        ls, bl, _ = sword_layers(A, B, x, 31.0, a, L, w)
        layers += ls
        blades.append(bl)
    x1 = mask()
    line(x1, [(19.0, 3.0), (29.5, 13.0)], 1.1)
    line(x1, [(29.5, 3.0), (19.0, 13.0)], 1.1)
    sp = mask()
    star(sp, 24.2, 8.0, 3.2, 0.22, 0)
    return finish(stack(layer(boom, mix(A, (255, 200, 90), 0.45), A), layer(gnd, mix(A, WHITE, 0.3), A), *layers, layer(x1, WHITE, mix(A, WHITE, 0.4)),
                        layer(sp, WHITE, B)), [glow(lighter(*blades), A, 1.3, 0.7), glow(lighter(boom, x1), A, 1.8, 1.0)], back(A, B))


def bladeranger_forge(A, B):
    # 포지 브레이커: 땅에 내려친 거대한 칼 · 솟는 불빛 칼
    ls, bl, _ = sword_layers(A, B, 25.0, 4.0, -125, 25.0, 1.6)
    layers, spikes = [], []
    for (x, L) in ((5.0, 8.0), (10.0, 10.0), (15.5, 8.0), (21.0, 7.0)):
        sl, sb, _ = sword_layers(A, B, x, 30.0, 82, L, 0.6)
        layers += sl
        spikes.append(sb)
    boom = mask()
    disc(boom, 12.0, 26.0, 4.0)
    return finish(stack(layer(boom, mix(A, (255, 200, 90), 0.5), A), *layers, *ls), [glow(bl, A, 1.4, 0.7), glow(lighter(*spikes, boom), A, 1.8, 1.0)], back(A, B))


def bladeranger_ult(A, B):
    # 블레이드 이클립스 (2026-11-16 — 옛 아이언 템페스트 대신): 검은 해 (일식) 의 빛무리 테 · 바깥을 향해 퍼지는 칼 열둘 · 테의 반짝임
    sun = mask()
    disc(sun, 16.0, 16.0, 5.6)
    rim = mask()
    ring(rim, 16.0, 16.0, 6.3, 1.0)
    layers, blades = [], []
    for k in range(12):
        a = k * 30 + 15
        x = 16.0 + 7.6 * math.cos(math.radians(a))
        y = 16.0 - 7.6 * math.sin(math.radians(a))
        ls, bl, _ = sword_layers(A, B, x, y, a, 7.6, 0.55)
        layers += ls
        blades.append(bl)
    sp = mask()
    star(sp, 20.5, 11.5, 3.0, 0.22, 0)
    return finish(stack(layer(sun, mix(A, DARK, 0.75), DARK), *layers, layer(rim, WHITE, mix(A, WHITE, 0.35)), layer(sp, WHITE, B)),
                  [glow(rim, A, 2.0, 1.1), glow(lighter(*blades), A, 1.2, 0.6)], back(A, B))


ICONS = {
    "arsenalking_volley": arsenalking_volley,
    "arsenalking_chain": arsenalking_chain,
    "arsenalking_rain": arsenalking_rain,
    "arsenalking_judge": arsenalking_judge,
    "arsenalking_ult": arsenalking_ult,
    "bladeranger_twin": bladeranger_twin,
    "bladeranger_boomerang": bladeranger_boomerang,
    "bladeranger_storm": bladeranger_storm,
    "bladeranger_forge": bladeranger_forge,
    "bladeranger_ult": bladeranger_ult,
}
