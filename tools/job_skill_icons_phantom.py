#!/usr/bin/env python3
"""2026-11-15 팬텀 아처 (궁수 리메이크 — 사용자 "아처 1차는 … 에미야 시로 같은 느낌으로") 직업 스킬 아이콘: 32×32 메이플식 네모 그림.
투영한 칼 (강철빛 날 · 진홍 코등이) · 칼끝 섬광 · 진홍 폭발. 색 = jobs.json 직업 빛 (A 진홍 · B 강철 흰빛). tools/job_skill_icons.py 가 마지막에 불러 덮어씀."""
import math
import os
import sys

from PIL import ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_skill_icons import (WHITE, backdrop, disc, finish, glow, layer, line, mask, mix, poly, ring, stack, star)  # noqa: E402

STEEL = (176, 196, 232)
DARK = (0, 0, 0)


def lighter(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def sword(cx, cy, ang, L, w=1.0):
    """칼 하나 (자루 끝 cx · cy 에서 ang 도 쪽으로 길이 L — 0 = 오른쪽, 90 = 위): 날 · 코등이 · 자루 마스크"""
    a = math.radians(ang)
    d = (math.cos(a), -math.sin(a))
    s = (-d[1], d[0])
    g = 0.24 * L  # 자루 길이
    gx, gy = cx + d[0] * g, cy + d[1] * g
    tx, ty = cx + d[0] * L, cy + d[1] * L
    bw = 1.25 * w
    blade = mask()
    poly(blade, [(gx + s[0] * bw, gy + s[1] * bw), (gx + d[0] * (L - g) * 0.8 + s[0] * bw * 0.9, gy + d[1] * (L - g) * 0.8 + s[1] * bw * 0.9), (tx, ty),
                 (gx + d[0] * (L - g) * 0.8 - s[0] * bw * 0.9, gy + d[1] * (L - g) * 0.8 - s[1] * bw * 0.9), (gx - s[0] * bw, gy - s[1] * bw)])
    guard = mask()
    line(guard, [(gx + s[0] * 3.0 * w, gy + s[1] * 3.0 * w), (gx - s[0] * 3.0 * w, gy - s[1] * 3.0 * w)], 1.3 * w)
    grip = mask()
    line(grip, [(cx, cy), (gx, gy)], 1.1 * w)
    return blade, guard, grip, (tx, ty)


def sword_layers(A, B, cx, cy, ang, L, w=1.0):
    blade, guard, grip, tip = sword(cx, cy, ang, L, w)
    return [layer(blade, mix(B, WHITE, 0.4), STEEL), layer(grip, mix(A, DARK, 0.5), mix(A, DARK, 0.7)), layer(guard, A, mix(A, DARK, 0.3))], blade, tip


def back(A, B):
    return backdrop(mix(A, DARK, 0.72), mix(A, (255, 140, 90), 0.25), 0.9)


def archer_volley(A, B):
    # 소드 볼트: 오른쪽 위로 날아가는 칼 셋 (셋째가 가장 큼) · 뒤의 빛줄
    layers, blades, streak = [], [], mask()
    for (x, y, L, w) in ((3.0, 15.0, 13.0, 0.8), (6.0, 24.0, 13.0, 0.8), (4.0, 31.0, 21.0, 1.2)):
        ls, bl, tip = sword_layers(A, B, x + 2.0, y - 2.0, 40, L, w)
        layers += ls
        blades.append(bl)
        line(streak, [(x - 3.0, y + 1.0), (x + 1.5, y - 2.5)], 0.7)
    sp = mask()
    star(sp, 22.5, 7.5, 3.6, 0.22, 0)
    return finish(stack(layer(streak, mix(A, WHITE, 0.3), A), *layers, layer(sp, WHITE, B)), [glow(lighter(*blades), A, 1.6, 0.9)], back(A, B))


def archer_cross(A, B):
    # 크로스 팽: X 자로 엇갈린 쌍검 · 휘어 도는 두 궤적 · 가운데 섬광
    arcs = mask()
    for k in range(14):
        t0, t1 = k / 14.0, (k + 1) / 14.0
        for sgn in (1, -1):
            p0 = (16 + sgn * 12 * math.cos(math.pi * t0), 17 - 10 * math.sin(math.pi * t0))
            p1 = (16 + sgn * 12 * math.cos(math.pi * t1), 17 - 10 * math.sin(math.pi * t1))
            line(arcs, [p0, p1], 0.7)
    l1, b1, _ = sword_layers(A, B, 6.0, 28.0, 52, 22.0, 0.95)
    l2, b2, _ = sword_layers(A, B, 26.0, 28.0, 128, 22.0, 0.95)
    sp = mask()
    star(sp, 16.0, 15.5, 4.2, 0.2, 45)
    return finish(stack(layer(arcs, mix(B, WHITE, 0.2), A), *l1, *l2, layer(sp, WHITE, B)), [glow(lighter(b1, b2), A, 1.6, 0.9), glow(sp, B, 1.2, 0.8)], back(A, B))


def archer_blades(A, B):
    # 블레이드 레인: 위에서 쏟아져 땅에 꽂히는 칼 다섯 · 땅의 진홍 빛줄
    layers, blades = [], []
    for (x, y, L) in ((6.0, 4.0, 12.0), (13.0, 9.0, 13.0), (20.0, 3.0, 12.0), (26.5, 10.0, 12.5), (10.0, 15.5, 12.0)):
        ls, bl, _ = sword_layers(A, B, x, y, -80, L, 0.75)
        layers += ls
        blades.append(bl)
    gnd = mask()
    line(gnd, [(2.0, 29.5), (30.0, 29.5)], 1.2)
    return finish(stack(layer(gnd, A, mix(A, DARK, 0.3)), *layers), [glow(lighter(*blades), A, 1.4, 0.8), glow(gnd, A, 1.6, 0.9)], back(A, B))


def archer_burst(A, B):
    # 팬텀 버스트: 오른쪽 위로 날아가는 거대한 칼 · 칼끝의 진홍 폭발
    ls, bl, tip = sword_layers(A, B, 3.0, 29.0, 45, 25.0, 1.5)
    boom = mask()
    disc(boom, 25.0, 7.0, 5.5)
    core = mask()
    disc(core, 25.0, 7.0, 2.6)
    rays = mask()
    for k in range(8):
        a = k * math.pi / 4 + 0.2
        line(rays, [(25.0 + 5.0 * math.cos(a), 7.0 + 5.0 * math.sin(a)), (25.0 + 7.5 * math.cos(a), 7.0 + 7.5 * math.sin(a))], 0.8)
    return finish(stack(*ls, layer(boom, mix(A, (255, 170, 80), 0.5), A), layer(rays, mix(A, WHITE, 0.4), A), layer(core, WHITE, mix(B, WHITE, 0.5))),
                  [glow(bl, A, 1.4, 0.7), glow(boom, A, 2.2, 1.0)], back(A, B))


def archer_ult(A, B):
    # 소드 생추어리: 붉은 노을 · 불꽃 고리 · 땅에 비스듬히 꽂힌 칼의 들판
    sky = mask()
    disc(sky, 16.0, 30.0, 15.0)
    rim = mask()
    ring(rim, 16.0, 26.0, 13.5, 1.2)
    layers, blades = [], []
    for (x, y, ang, L) in ((5.0, 13.0, -70, 14.0), (11.0, 9.0, -95, 15.0), (17.5, 11.0, -80, 14.0), (24.0, 8.0, -105, 15.5), (28.5, 15.0, -85, 11.0),
                           (14.0, 18.0, -75, 11.0), (21.0, 19.0, -100, 10.5)):
        ls, bl, _ = sword_layers(A, B, x, y, ang, L, 0.7)
        layers += ls
        blades.append(bl)
    return finish(stack(layer(sky, mix(A, (255, 150, 60), 0.35), mix(A, DARK, 0.4)), layer(rim, (255, 190, 110), A), *layers),
                  [glow(rim, (255, 150, 70), 1.8, 1.0), glow(lighter(*blades), A, 1.2, 0.6)], back(A, B))


ICONS = {
    "archer_volley": archer_volley,
    "archer_cross": archer_cross,
    "archer_blades": archer_blades,
    "archer_burst": archer_burst,
    "archer_ult": archer_ult,
}
