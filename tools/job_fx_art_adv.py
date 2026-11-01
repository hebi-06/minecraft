#!/usr/bin/env python3
"""0.51.0 2차 직업 그림 — 블러드 나이트 (검사 반전) 의 피 → tools/java_fx (rpg:rfx_j*), 설치는 tools/java_fx_add.py.

  python3 tools/job_fx_art_adv.py                        # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/job_fx_art_adv.py --preview <폴더>       # 확인용 그림도 씀 (v2c 꼴 — 장면 줄 · 작은 크기 줄 · 한 장 확대 · 빛 / 심지 알파)
  python3 tools/java_fx_add.py rfx_jdrop rfx_jblood      # 게임에 넣음

꼴은 v2c (tools/job_fx_art_v2c_marks.py) 와 같음: 모두 흰색 (밝기 = 알파) 으로 4배 그림판에 그려 LANCZOS 로 줄임 · 칸 = 빛 장면 N 개 다음에 심지 장면 N 개.
피는 빛나는 그림이 아니라 몸이 있는 액체라 '빛' 칸 = 짙은 몸 (불투명에 가깝게 · 가장자리만 살짝 흐림), '심지' 칸 = 밝은 속 · 젖은 반짝임 (게임은 빛 칸에 짙은 피,
심지 칸에 밝은 피를 입힘 — JobTraits5.drop · blood). 고정 시드.

그림 (이름 — 보는 꼴 — 칸 — 장 수 — 열 수):
- rfx_jdrop   lookat_direction 128×64  ×1 (2 열)  핏방울: 오른쪽 (+X = 나아가는 쪽) 둥근 머리 · 왼쪽으로 가늘어지는 꼬리. 심지 = 머리 쪽 속 · 젖은 반짝임 점
- rfx_jblood  rotate_xyz       128×128 ×6 (6 열)  피 튀김: 0 가운데 덩이 · 짧은 가시 → 1 ~ 3 가시가 뻗다 끊겨 방울로 날아감 (나아가는 쪽으로 늘어남) · 가운데 왕관 꼴로 퍼짐 →
                                                   4 ~ 5 방울이 작아지고 흐려지며 가운데는 옅은 얼룩으로 사라짐. 칸 가운데 = 튄 자리

0.51.0 전사 2차 (뵈드바르(워로드) · 울프헤딘(라이칸)) — 빛 칸 = 빛나는 몸 (넓은 무리), 심지 칸 = 눈 · 이빨 · 발톱 · 털끝 · 테:
- rfx_jbear   rotate_xyz       256×256 ×8 (4 열)  곰의 혼 (앞모습 — 일어선 곰의 머리 · 어깨 · 양옆으로 든 앞발): 0 ~ 2 기운이 소용돌이쳐 모여 곰이 됨 (턱이 열리기 시작) →
                                                   3 ~ 6 포효 (이어 돌림 — 턱이 열렸다 닫혔다 · 털끝이 일렁임) → 7 위로 흩어짐. 머리 가운데 (128, 104) · 성난 눈 · 송곳니 · 귀 · 앞발 발톱
- rfx_jpaw    rotate_xyz       256×256 ×4 (4 열)  곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중). 발바닥 가운데 = 칸 가운데
- rfx_jclaw4  rotate_xyz       192×192 ×6 (6 열)  늑대 발톱 네 줄: 0 ~ 1 네 줄이 오른쪽 위에서 왼쪽 아래로 찢어짐 (가운데 굵고 양 끝 뾰족 · 조금 엇갈림) · 2 번쩍 →
                                                   3 ~ 5 흐려지며 튀는 불티 · 떨어지는 방울. 칸 가운데 = 맞은 자리
- rfx_jmoon   rotate_xyz       256×256 ×8 (4 열)  달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 둥근 빛무리) → 1 ~ 6 그림자가 오른쪽에서 달을 삼킴 (가려진 곳은 비움) →
                                                   7 남은 테 (코로나 — 게임은 붉게). 원판 반지름 79 (= 반 크기 × 0.62)
"""
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402
from job_fx_art_v2 import COLORS, TAU, Pad, Stack, bevel_light, bez, clamp01, gblur, obj_glow, rim_of, screen, smooth, taper_poly, to_sheet  # noqa: E402
from job_fx_art_v2b import cut_polyline, fbm, lens, polar, star4, win_border  # noqa: E402
import job_fx_art_v2c_marks as V2C  # noqa: E402 — 확인용 그림 (preview) 을 같은 꼴로

BLOOD = ((0.62, 0.02, 0.08), (0.95, 0.18, 0.2))
COLORS.setdefault("bloodknight", BLOOD)
# 확인용 색 (빛 · 심지 — 게임이 입히는 색과 같게): 곰 = 갈색 몸 · 주황 눈 / 발톱 = 붉은 눈빛 · 달빛 흰빛 / 달 = 달빛 은회 · 흰빛
COLORS.setdefault("warlord", ((0.66, 0.36, 0.14), (1.0, 0.7, 0.33)))
COLORS.setdefault("lycan", ((0.9, 0.14, 0.18), (0.9, 0.92, 0.97)))
COLORS.setdefault("moon", ((0.76, 0.8, 0.92), (1.0, 1.0, 1.0)))


class Spec(V2C.Spec):
    pass


SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, job, zoom=0, desc="", loop=False):
    def deco(fn):
        SPECS[name] = Spec(name, facing, cw, ch, frames, cols, job, fn, loop, zoom, desc)
        return fn
    return deco


def drop_shape(pad, hx, hy, r, tail, ang=0.0, n=20):
    """눈물 꼴 (머리 가운데 hx, hy · 반지름 r · 꼬리 길이 tail — 꼬리는 ang 의 반대쪽): 둥근 머리 + 가늘어지는 꼬리 (4배 그림판 배열)"""
    ux, uy = math.cos(ang), math.sin(ang)
    pts = [(hx - ux * tail * (1 - i / n), hy - uy * tail * (1 - i / n)) for i in range(n + 1)]
    body = pad.poly(taper_poly(pts, lambda t: r * (t ** 1.6) * 0.98)) if tail > 0.5 else pad.zeros()
    return np.maximum(body, pad.disc_field(hx, hy, r))


# ------------------------------------------------------------ rfx_jdrop (핏방울 — 오른쪽이 나아가는 쪽)

def drop_frame(f):
    cw, ch = 128, 64
    pad = Pad(cw, ch)
    hx, hy, r = 90.0, 32.0, 15.0
    body = drop_shape(pad, hx, hy, r, 66.0)
    inner = drop_shape(pad, hx - 1.5, hy + 1.0, r * 0.62, 40.0)
    shine = pad.disc_field(hx + 5.0, hy - 6.0, 3.6) + pad.disc_field(hx - 8.0, hy - 7.5, 1.8) * 0.6
    b1 = pad.down(body)
    glow = screen(b1 * 0.94, gblur(b1, 1.6) * 0.5)
    core = np.maximum(pad.down(inner) * 0.62, clamp01(pad.down(shine)) * 1.0)
    wb = win_border(glow.shape, 3.0)
    return clamp01(glow) * wb, clamp01(core) * wb


sprite("rfx_jdrop", "lookat_direction", 128, 64, 1, 2, "bloodknight",
       desc="핏방울: 오른쪽 (+X = 나아가는 쪽) 둥근 머리 · 왼쪽으로 가늘어지는 꼬리, 심지 = 머리 쪽 속 · 젖은 반짝임 점")(drop_frame)


# ------------------------------------------------------------ rfx_jblood (피 튀김 — 칸 가운데 = 튄 자리)

BL_N = 6
_R = random.Random(5106)
BL_SPIKES = [(_R.uniform(0, TAU), _R.uniform(0.75, 1.0), _R.uniform(0.8, 1.2)) for _ in range(9)]
BL_DROPS = [(_R.uniform(0, TAU), _R.uniform(0.55, 1.0), _R.uniform(2.6, 5.2)) for _ in range(16)]
# 가운데 덩이 반지름 · 가시 길이 · 방울이 간 몫 · 밝기 (장마다)
BL_CORE_R = [13.0, 17.0, 19.0, 19.5, 18.5, 17.0]
BL_SPIKE = [0.45, 1.0, 0.7, 0.3, 0.0, 0.0]
BL_FLY = [0.0, 0.32, 0.58, 0.78, 0.92, 1.0]
BL_A = [1.0, 1.0, 1.0, 0.86, 0.6, 0.32]


def blood_frame(f):
    cw = ch = 128
    pad = Pad(cw, ch)
    c = 64.0
    a = BL_A[f]
    # 가운데 덩이: 가장자리가 울퉁불퉁한 원 (노이즈 반지름) — 뒤 장면일수록 옅은 얼룩
    rr, th = polar(pad, c, c)
    wob = fbm(np.cos(th) * 1.6 + 3.0, np.sin(th) * 1.6 - 2.0 + f * 0.15, seed=33, octaves=3)
    R0 = BL_CORE_R[f]
    blob = clamp01((R0 * (1 + 0.28 * wob) - rr) * pad.ss / 2.0 + 0.5)
    body = blob * (1.0 if f < 4 else 0.7 if f == 4 else 0.45)
    inner = clamp01((R0 * 0.55 * (1 + 0.3 * wob) - rr) * pad.ss / 2.0 + 0.5) * (1.0 if f < 3 else 0.5)
    # 가시: 가운데에서 바깥으로 뻗는 뾰족한 혀 (0 ~ 3) — 끝이 둥근 방울
    for ang, ln, wd in BL_SPIKES:
        L = 44.0 * ln * BL_SPIKE[f]
        if L < 2:
            continue
        ux, uy = math.cos(ang), math.sin(ang)
        pts = [(c + ux * (R0 * 0.6 + L * i / 16), c + uy * (R0 * 0.6 + L * i / 16)) for i in range(17)]
        sp = pad.poly(taper_poly(pts, lambda t, w=wd: 4.2 * w * (1 - t) ** 1.2 + 0.6))
        tip = pad.disc_field(pts[-1][0], pts[-1][1], 2.4 * wd)
        body = np.maximum(body, np.maximum(sp, tip))
        inner = np.maximum(inner, pad.poly(taper_poly(pts[:10], lambda t, w=wd: 1.6 * w * (1 - t))) * 0.8)
    # 방울: 바깥으로 날아감 (나아가는 쪽으로 늘어난 눈물 꼴 — 꼬리는 가운데 쪽) · 뒤로 갈수록 작아짐
    for ang, sp_, r0 in BL_DROPS:
        t = BL_FLY[f] * sp_
        if t <= 0.02:
            continue
        dist = R0 * 0.8 + 40.0 * t
        x, y = c + math.cos(ang) * dist, c + math.sin(ang) * dist
        r = r0 * (1.0 - 0.45 * BL_FLY[f])
        tail = r * (3.2 if f <= 2 else 1.8 if f == 3 else 0.6)
        d = drop_shape(pad, x, y, r, tail, ang)
        body = np.maximum(body, d)
        inner = np.maximum(inner, pad.disc_field(x + 0.3 * r, y - 0.35 * r, r * 0.38))
    b1 = pad.down(body)
    glow = screen(b1 * 0.92, gblur(b1, 1.8) * 0.45) * a
    core = clamp01(pad.down(inner)) * a
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow) * wb, clamp01(core) * wb


sprite("rfx_jblood", "rotate_xyz", 128, 128, BL_N, 6, "bloodknight", zoom=2,
       desc="피 튀김: 0 가운데 덩이 · 짧은 가시 → 1 ~ 3 가시가 뻗다 끊겨 방울로 날아감 · 가운데가 퍼짐 → 4 ~ 5 방울이 작아지고 흐려지며 옅은 얼룩으로 사라짐")(blood_frame)


# ============================================================ 0.51.0 전사 2차 — 곰의 혼 · 곰 앞발 · 늑대 발톱 · 달

# ------------------------------------------------------------ rfx_jclaw4 (늑대 발톱 네 줄 — 칸 가운데 = 맞은 자리)

CL_N = 6
CL_LEN = [0.55, 1.0, 1.0, 1.0, 1.0, 1.0]  # 그어진 몫 (오른쪽 위 → 왼쪽 아래)
CL_A = [1.0, 1.0, 0.95, 0.72, 0.44, 0.2]
CL_W = [0.85, 1.08, 1.0, 0.88, 0.74, 0.6]
CL_FLASH = [0.55, 1.0, 0.45, 0.12, 0.0, 0.0]
CL_FLY = [0.0, 0.0, 0.25, 0.55, 0.8, 1.0]
_RC = random.Random(4404)
CL_SPARKS = [(_RC.uniform(0.15, 0.85), _RC.choice((-1, 1)), _RC.uniform(0.6, 1.0), _RC.uniform(-0.4, 0.4)) for _ in range(10)]


def claw4_frame(f):
    cw = ch = 192
    pad = Pad(cw, ch)
    a = CL_A[f]
    p0, p1 = (148.0, 30.0), (42.0, 160.0)
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    ux, uy = (p1[0] - p0[0]) / L, (p1[1] - p0[1]) / L
    nx, ny = -uy, ux
    body = pad.zeros()
    core = pad.zeros()
    mids = []
    for o in (-1.5, -0.5, 0.5, 1.5):
        sp = 20.0 * o
        lenk = 1.0 - 0.14 * abs(o)
        start = (1 - lenk) * L * 0.5 + 7.0 * o
        a0 = (p0[0] + nx * sp + ux * start, p0[1] + ny * sp + uy * start)
        a1 = (a0[0] + ux * L * lenk, a0[1] + uy * L * lenk)
        bow = 9.0 + 2.0 * o
        mid = ((a0[0] + a1[0]) / 2 + nx * bow, (a0[1] + a1[1]) / 2 + ny * bow)
        pts = cut_polyline(bez(a0, mid, a1, 30), CL_LEN[f])
        if len(pts) < 3:
            continue
        mids.append(pts[len(pts) // 2])
        W = (11.5 - 1.6 * abs(o)) * CL_W[f]
        g = pad.poly(taper_poly(pts, lambda t, W=W: W * 0.5 * max(0.0, math.sin(math.pi * min(1.0, max(0.0, t)))) ** 0.6 + 0.25))
        c = pad.poly(taper_poly(pts, lambda t, W=W: W * 0.16 * max(0.0, math.sin(math.pi * min(1.0, max(0.0, t)))) ** 0.8 + 0.15))
        body = np.maximum(body, g)
        core = np.maximum(core, c)
        # 찢긴 가장자리: 줄 바깥쪽에 짧은 가시 몇 (같은 줄을 따라)
        for k in range(3):
            t = 0.28 + 0.22 * k
            if t > CL_LEN[f]:
                break
            q = bez(a0, mid, a1, 30)[int(t * 30)]
            tip = (q[0] + nx * (W * 0.9) + ux * 4, q[1] + ny * (W * 0.9) + uy * 4)
            body = np.maximum(body, pad.poly([(q[0] - ux * 3, q[1] - uy * 3), tip, (q[0] + ux * 5, q[1] + uy * 5)]) * 0.8)
    # 불티 · 방울: 줄 가운데에서 바깥으로 튐 (2 ~ 5)
    fly = CL_FLY[f]
    if fly > 0:
        for t, side, sp_, jit in CL_SPARKS:
            q = (p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t)
            d = 26.0 + 46.0 * fly * sp_
            dx, dy = nx * side + ux * jit, ny * side + uy * jit
            n_ = math.hypot(dx, dy) or 1
            dx, dy = dx / n_, dy / n_
            hx, hy = q[0] + dx * d, q[1] + dy * d + 14.0 * fly * fly
            r = 2.2 * (1.0 - 0.6 * fly)
            core = np.maximum(core, pad.stroke([(hx - dx * 7, hy - dy * 7), (hx, hy)], 0.4, r * 1.6, a0=0.0, a1=0.9))
            body = np.maximum(body, pad.disc_field(hx, hy, r * 1.4, soft=1.0) * 0.7)
    b1 = pad.down(body)
    glow = screen(b1 * 0.95, gblur(b1, 2.0) * 0.6, gblur(b1, 6.0) * 0.32)
    if CL_FLASH[f] > 0:
        cx, cy = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        glow = screen(glow, pad.down(pad.radial(cx, cy, 70.0, 1.6)) * 0.55 * CL_FLASH[f])
    core1 = pad.down(core)
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow * a) * wb, clamp01(core1 * a) * wb


sprite("rfx_jclaw4", "rotate_xyz", 192, 192, CL_N, 6, "lycan", zoom=1,
       desc="늑대 발톱 네 줄: 0 ~ 1 오른쪽 위에서 왼쪽 아래로 찢어짐 (엇갈린 네 줄 · 찢긴 가장자리) · 2 번쩍 → 3 ~ 5 흐려지며 불티 · 방울이 튐")(claw4_frame)


# ------------------------------------------------------------ rfx_jmoon (달 — 원판 반지름 79 · 칸 가운데)

MN_N = 8
MN_R = 79.0
_RM = random.Random(7070)
MN_CRATERS = [(_RM.uniform(-0.7, 0.7), _RM.uniform(-0.7, 0.7), _RM.uniform(4.0, 11.0)) for _ in range(14)]


def moon_frame(f):
    S = 256
    pad = Pad(S, S)
    c = S / 2
    R = MN_R
    X, Y = pad.grid()
    rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    disc = clamp01((R - rr) * pad.ss + 0.5)
    nz = fbm(X / 46.0 + 3.1, Y / 46.0 - 1.7, seed=77, octaves=4)
    maria = smooth(0.0, 0.32, nz)
    limb = 1.0 - 0.3 * np.clip(rr / R, 0, 1) ** 2.2
    surf = disc * (0.62 + 0.38 * (1 - maria)) * limb
    core = disc * smooth(R - 7.0, R - 1.0, rr) * 0.55  # 밝은 가장자리 (달의 테)
    core = np.maximum(core, disc * (1 - maria) * 0.32 * limb)
    for cxr, cyr, r in MN_CRATERS:
        x, y = c + cxr * R * 0.85, c + cyr * R * 0.85
        if math.hypot(x - c, y - c) + r > R - 3:
            continue
        d = np.sqrt((X - x) ** 2 + (Y - y) ** 2)
        ring = clamp01(1 - np.abs(d - r) / 1.4) * disc * smooth(-0.6, 0.6, (Y - y) / max(1.0, r))  # 아래쪽 테만 밝게 (빛이 위에서)
        bowl = clamp01((r - d) / r) * disc
        surf = surf * (1 - 0.22 * bowl)
        core = np.maximum(core, ring * 0.45)
    p = min(1.0, f / 6.0)
    halo_k = 1.0 - 0.75 * p
    if f <= 6:
        sx = c + 2.06 * R * (1 - p) + 2.0 * p
        sy = c - 10.0 * (1 - p)
        ds = np.sqrt((X - sx) ** 2 + (Y - sy) ** 2)
        shadow = clamp01((R * 1.03 - ds) * pad.ss / 3.0 + 0.5)
    else:
        shadow = disc
    lit = surf * (1 - shadow)
    core_l = core * (1 - shadow)
    # 그림자 가장자리의 옅은 테 (삼키는 이빨 자국처럼 들쭉날쭉)
    if 1 <= f <= 6:
        th = np.arctan2(Y - sy, X - sx)
        jag = 1.0 + 0.035 * np.sin(th * 26.0) + 0.02 * np.sin(th * 11.0 + 1.3)
        edge = clamp01(1 - np.abs(ds - R * 1.03 * jag) / 2.2) * disc
        core_l = np.maximum(core_l, edge * 0.45)
    # 코로나 (다 가려짐 6 · 붉은 테 7): 원판 둘레의 테 · 일렁이는 빛살
    if f >= 6:
        k = 1.0 if f == 7 else 0.6
        th = np.arctan2(Y - c, X - c)
        flare = 1.0 + 0.35 * np.sin(th * 7.0 + 0.6) * np.sin(th * 3.0 + 2.0)
        ring = clamp01(1 - np.abs(rr - (R + 2.0)) / (3.0 + 2.0 * k)) * k
        rays = clamp01(1 - (rr - R) / (26.0 * flare * k + 1e-6)) * (rr > R) * 0.55 * k
        core_l = np.maximum(core_l, ring)
        lit = np.maximum(lit, np.maximum(ring * 0.9, rays))
    b1 = pad.down(lit)
    halo = pad.down(clamp01(1 - (rr - R) / 46.0) * (rr > R - 2)) * 0.4 * halo_k
    glow = screen(b1, gblur(b1, 3.0) * 0.35, halo)
    core1 = pad.down(core_l)
    wb = win_border(glow.shape, 6.0)
    return clamp01(glow) * wb, clamp01(core1) * wb


sprite("rfx_jmoon", "rotate_xyz", 256, 256, MN_N, 4, "moon", zoom=0,
       desc="달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 빛무리) → 1 ~ 6 그림자가 오른쪽에서 삼킴 (들쭉날쭉한 그림자 테) → 7 붉은 테 (코로나 · 빛살)")(moon_frame)


# ------------------------------------------------------------ rfx_jpaw (곰 앞발 — 발바닥 가운데 = 칸 가운데, 이어 돌림)

PW_N = 4


def paw_frame(f, S=256, frames=PW_N):
    pad = Pad(S, S)
    c = S / 2
    st = Stack(pad)
    ph = TAU * f / frames
    # 위로 흐르는 바람 줄 (내려치는 중 — 줄이 위로 흘러감, 맨 뒤)
    rnd = random.Random(909)
    wind = pad.zeros()
    for i in range(9):
        x = c + rnd.uniform(-70, 70)
        q = (rnd.random() + f / frames) % 1.0
        y0 = 70 - 60 * q
        Ln = rnd.uniform(40, 80)
        wind = np.maximum(wind, pad.stroke([(x, y0 + Ln), (x, y0)], 2.6, 0.6, a0=0.55 * math.sin(math.pi * q), a1=0.0))
    # 팔 (위로 이어지며 흐려짐)
    arm = pad.poly([(c - 50, 0), (c + 50, 0), (c + 58, c - 10), (c - 58, c - 10)])
    X, Y = pad.grid()
    arm = arm * smooth(10.0, 70.0, Y)
    st.over(arm, 0.5, rim_of(arm, 0.8) * 0.4 * arm)
    # 발 (털 가장자리가 들쭉날쭉한 둥근 덩이)
    pts = []
    for i in range(72):
        t = TAU * i / 72
        r = 70 + 4.5 * math.sin(t * 9 + ph * 0.5) * (1 if math.sin(t) < 0.3 else 0.35)
        pts.append((c + math.cos(t) * r * 1.05, c + 2 + math.sin(t) * r * 0.78))
    pawm = pad.poly(pts)
    lit, dark, _ = bevel_light(pawm, 14.0)
    st.over(pawm, 0.58 + 0.2 * lit - 0.15 * dark, (rim_of(pawm, 1.4) * 0.8 + lit * 0.25) * pawm)
    # 털결 (짧은 줄)
    for i in range(14):
        x = c - 56 + 8 * i + rnd.uniform(-2, 2)
        y = c - 30 + rnd.uniform(-8, 8)
        st.add_core(pad.stroke([(x, y), (x - 2, y + 10)], 1.0, 0.3, a0=0.35, a1=0.05) * pawm)
    # 발바닥 볼록 (큰 것 하나 — 위 · 발가락 넷 — 아래로 둥글게 늘어섬: 발톱이 먼저 내려오는 앞발)
    main = pad.ellipse(c, c - 8, 38, 24)
    st.over(main, 0.82, (rim_of(main, 1.2) * 0.9 + 0.22) * main)
    toes = [(-50, 22), (-18, 32), (18, 32), (50, 22)]
    for tx, ty in toes:
        m = pad.ellipse(c + tx, c + ty, 13, 12)
        st.over(m, 0.85, (rim_of(m, 1.0) * 0.9 + 0.28) * m)
    # 발톱 넷: 발가락 끝에서 아래로 길게 휨 (빛나는 갈고리 — 가운데 둘이 더 김)
    for k, (tx, ty) in enumerate(toes):
        bx, by = c + tx, c + ty + 9
        inward = -1 if tx > 0 else 1
        tip = (bx + inward * 8, by + (64 if abs(tx) < 30 else 52))
        ctrl = (bx - inward * 6, by + 30)
        cl = bez((bx, by), ctrl, tip, 18)
        m = pad.poly(taper_poly(cl, lambda t: 8.0 * (1 - t) ** 0.85 + 0.4))
        st.over(m, 0.9, pad.poly(taper_poly(cl, lambda t: 3.2 * (1 - t) ** 0.9 + 0.3)) * (0.75 + 0.25 * math.sin(ph + k)))
    core1 = pad.down(st.core)
    glow = obj_glow(pad, np.maximum(st.body, wind * 0.6), core1, halo=((1.6, 0.5), (4.5, 0.36), (10.0, 0.2)), body_k=0.95)
    glow = screen(glow, pad.down(wind) * 0.5)
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow) * wb, clamp01(core1) * wb


sprite("rfx_jpaw", "rotate_xyz", 256, 256, PW_N, 4, "warlord", zoom=0, loop=True,
       desc="곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중)")(paw_frame)


# ------------------------------------------------------------ rfx_jbear (곰의 혼 — 앞모습, 머리 가운데 (128, 104))

BR_N = 8
BR_JAW = [0.0, 0.22, 0.55, 0.82, 1.0, 0.9, 0.8, 0.6]
BR_A = [0.38, 0.66, 0.9, 1.0, 1.0, 1.0, 1.0, 0.42]
BR_SCALE = [0.82, 0.91, 0.97, 1.0, 1.025, 1.012, 1.0, 1.05]
BR_HX, BR_HY = 128.0, 104.0


def bear_head_pts(ph, n=96):
    """머리 둘레 (넓적한 머리 · 아래 볼 털이 양옆으로 뻗침 — 털끝이 조금 일렁임)"""
    pts = []
    for i in range(n):
        t = TAU * i / n
        s, co = math.sin(t), math.cos(t)
        rx, ry = 58.0, 50.0 if s < 0 else 54.0
        r = 1.0
        if s > 0.05:  # 아래 반 (볼 · 턱): 양옆으로 볼록 · 털 가시
            r += 0.2 * max(0.0, abs(co)) ** 1.4 * s ** 0.5
            r += 0.08 * max(0.0, math.sin(t * 15 + ph)) ** 3
        else:  # 위 (이마): 조금 납작 · 짧은 털
            r += 0.025 * max(0.0, math.sin(t * 18 + ph * 0.7)) ** 3
        pts.append((BR_HX + co * rx * r, BR_HY + s * ry * r))
    return pts


def bear_frame(f, S=256):
    pad = Pad(S, S)
    st = Stack(pad)
    X, Y = pad.grid()
    loop_ph = TAU * ((f - 3) % 4) / 4 if 3 <= f <= 6 else 0.0
    J = BR_JAW[f]
    sc = BR_SCALE[f]
    rnd = random.Random(3131)

    def T(x, y):  # 가운데 (128, 128) 둘레로 크기
        return (128 + (x - 128) * sc, 128 + (y - 128) * sc)

    def TP(pts):
        return [T(x, y) for x, y in pts]

    # ---- 어깨 · 몸 (맨 뒤 — 둥근 어깨 · 털 가시 · 아래로 흐려짐)
    sh = []
    for i in range(49):
        u = i / 48
        x = 30 + 196 * u
        top = 172 - 30 * math.sin(math.pi * u) ** 0.6 + 7 * max(0.0, math.sin(u * 34 + loop_ph)) ** 3
        sh.append((x, top))
    sh += [(214, 214), (196, 252), (60, 252), (42, 214)]
    shm0 = pad.poly(TP(sh))
    fur = fbm(X / 14.0, Y / 9.0 + loop_ph * 0.15, seed=12, octaves=3)
    fade = smooth(246.0, 182.0, Y)
    shm = shm0 * fade
    lit, dark, _ = bevel_light(shm0, 18.0)
    st.over(shm, (0.42 + 0.14 * lit - 0.1 * dark) * (0.8 + 0.3 * fur), rim_of(shm0, 1.2) * 0.35 * shm)
    for i in range(9):  # 가슴 털결
        x0 = 70 + 14 * i + rnd.uniform(-3, 3)
        y0 = 182 + rnd.uniform(-4, 8)
        st.add_core(pad.stroke(TP([(x0, y0), (x0 + rnd.uniform(-3, 3), y0 + 14)]), 1.2, 0.3, a0=0.28, a1=0.0) * shm)
    # ---- 앞발 둘 (양옆으로 들어 올림 — 굵은 팔 · 털 가시 · 발 · 발톱)
    for side in (-1, 1):
        sx = 128 + side * 86
        arm = TP([(128 + side * 50, 190), (128 + side * 92, 180), (sx + side * 26, 134), (sx + side * 8, 112), (sx - side * 20, 120), (128 + side * 44, 158)])
        am = pad.poly(arm)
        lit, dark, _ = bevel_light(am, 10.0)
        st.over(am, (0.52 + 0.16 * lit - 0.1 * dark) * (0.85 + 0.2 * fur), rim_of(am, 1.0) * 0.4 * am)
        for k in range(5):  # 팔 바깥 털 가시
            u = k / 4
            bx, by = T(128 + side * (88 - 6 * u) + side * (sx - 128 - side * 88 + side * 26) * 0 , 178 - 46 * u)
            bx, by = T(128 + side * (92 - 6 * u) - (92 - 6 * u) * 0 + side * (u * (86 + 26 - 92)), 180 - 46 * u)
            tip = (bx + side * 9, by - 4 + 2 * math.sin(loop_ph + k))
            m = pad.poly([(bx, by - 4), tip, (bx, by + 5)])
            st.over(m, 0.5, rim_of(m, 0.6) * 0.3 * m)
        px, py = T(sx + side * 4, 114)
        pm = pad.ellipse(px, py, 25 * sc, 21 * sc)
        lit, dark, _ = bevel_light(pm, 8.0)
        st.over(pm, 0.6 + 0.15 * lit - 0.1 * dark, (rim_of(pm, 1.0) * 0.6 + lit * 0.2) * pm)
        for k in range(4):
            a0 = math.radians(-150 + 30 * k) if side < 0 else math.radians(-30 - 30 * k)
            bx, by = px + math.cos(a0) * 19 * sc, py + math.sin(a0) * 16 * sc
            tip = (bx + math.cos(a0 + side * 0.5) * 24 * sc, by + math.sin(a0 + side * 0.5) * 24 * sc - 4)
            cl = bez((bx, by), (bx + math.cos(a0) * 15 * sc, by + math.sin(a0) * 15 * sc - 4), tip, 12)
            m = pad.poly(taper_poly(cl, lambda t: 3.8 * sc * (1 - t) ** 0.9 + 0.3))
            st.over(m, 0.85, pad.poly(taper_poly(cl, lambda t: 1.6 * sc * (1 - t) + 0.25)))
    # ---- 귀 (머리 뒤)
    for side in (-1, 1):
        ex, ey = T(128 + side * 49, 62)
        em = pad.poly(TP([(128 + side * 34, 72), (128 + side * 40, 50), (128 + side * 52, 44), (128 + side * 64, 52), (128 + side * 66, 72)]))
        st.over(em, 0.58, (rim_of(em, 1.0) * 0.55) * em)
        inner = pad.poly(TP([(128 + side * 42, 68), (128 + side * 46, 54), (128 + side * 54, 51), (128 + side * 60, 58), (128 + side * 60, 70)]))
        st.add_core(inner * 0.3)
        st.body = st.body * (1 - inner * 0.4)
    # ---- 머리
    hm = pad.poly(TP(bear_head_pts(loop_ph)))
    lit, dark, hh = bevel_light(hm, 18.0)
    head_fur = fbm(X / 10.0, Y / 10.0, seed=41, octaves=3)
    st.over(hm, (0.6 + 0.16 * lit - 0.12 * dark) * (0.88 + 0.18 * head_fur), (rim_of(hm, 1.2) * 0.5 + lit * 0.14) * hm)
    # 이마 · 볼 털결
    for i in range(16):
        a = rnd.uniform(-2.6, -0.5)
        r0 = rnd.uniform(18, 46)
        x0, y0 = 128 + math.cos(a) * r0, 104 + math.sin(a) * r0 * 0.8
        x1, y1 = x0 + math.cos(a) * 9, y0 + math.sin(a) * 7
        st.add_core(pad.stroke(TP([(x0, y0), (x1, y1)]), 1.1 * sc, 0.3, a0=0.32, a1=0.05) * hm)
    for side in (-1, 1):
        for k in range(4):
            x0, y0 = 128 + side * (34 + 6 * k), 124 + 7 * k
            st.add_core(pad.stroke(TP([(x0, y0), (x0 + side * 12, y0 + 4)]), 1.2 * sc, 0.3, a0=0.4, a1=0.05) * hm)
    # ---- 주둥이 (조금 밝은 볼록) · 입 (턱이 J 만큼 열림)
    mx, my = T(128, 131)
    muz = pad.ellipse(mx, my, 28 * sc, 25 * sc)
    lit_m, dark_m, _ = bevel_light(muz, 8.0)
    st.over(muz, 0.74 + 0.12 * lit_m - 0.08 * dark_m, (rim_of(muz, 0.9) * 0.5) * muz)
    drop = 30.0 * J
    lip = 138.0
    mouth_pts = TP(bez((106, lip), (128, lip - 4), (150, lip), 10) + bez((150, lip), (146, lip + 8 + drop), (128, lip + 10 + drop), 8)[1:] +
                   bez((128, lip + 10 + drop), (110, lip + 8 + drop), (106, lip), 8)[1:])
    mouth = pad.poly(mouth_pts) if J > 0.05 else pad.zeros()
    st.over(mouth, 0.14, mouth * 0.05)
    if J > 0.3:  # 혀
        tg = pad.ellipse(*T(128, lip + 6 + drop * 0.85), 13 * sc, 6 * sc) * mouth
        st.over(tg, 0.36, rim_of(clamp01(tg * 1.5), 0.6) * 0.4 * tg)
    # 아래턱 (열린 만큼 내려감 — 털 덮인 턱 끝)
    chin = pad.poly(TP(bez((108, lip + 6 + drop), (128, lip + 24 + drop), (148, lip + 6 + drop), 12)))
    st.over(chin, 0.66, rim_of(chin, 0.8) * 0.5 * chin)
    # 이빨: 위 송곳니 둘 (아래로) · 아래 송곳니 둘 (위로) · 작은 앞니
    teeth = pad.zeros()
    for side in (-1, 1):
        x = 128 + side * 14
        teeth = np.maximum(teeth, pad.poly(TP([(x - 4, lip - 1), (x + 4, lip - 1), (x + side * 1.5, lip + 9 + 9 * J)])))
        if J > 0.2:
            yb = lip + 8 + drop
            teeth = np.maximum(teeth, pad.poly(TP([(x - 3.5, yb), (x + 3.5, yb), (x - side * 1.0, yb - 7 - 5 * J)])))
    for k in range(3):
        x = 122 + 6 * k
        teeth = np.maximum(teeth, pad.poly(TP([(x - 2.5, lip - 1), (x + 2.5, lip - 1), (x, lip + 4)])))
    st.over(teeth, 0.95, teeth)
    # ---- 코 (어둡게 · 빛 점)
    nose = pad.poly(TP(bez((116, 118), (128, 112), (140, 118), 8) + bez((140, 118), (136, 128), (128, 130), 6)[1:] + bez((128, 130), (120, 128), (116, 118), 6)[1:]))
    st.over(nose, 0.22, nose * 0.05)
    st.add_core(pad.disc_field(*T(124, 118), 2.2 * sc) * 0.9)
    # ---- 눈썹 뼈 (성난 — 코 쪽으로 내려옴) · 눈 (빛나는 아몬드 · 반짝임)
    for side in (-1, 1):
        bx0, by0 = T(128 + side * 44, 82)
        bx1, by1 = T(128 + side * 12, 92)
        st.body = st.body * (1 - pad.stroke([(bx0, by0), (bx1, by1)], 6.0 * sc, 4.0 * sc) * 0.45)
        st.add_core(pad.stroke([(bx0, by0 - 2), (bx1, by1 - 2)], 1.4 * sc, 0.8 * sc, a0=0.5, a1=0.8))
        e0, e1 = T(128 + side * 38, 92), T(128 + side * 16, 100)
        eye = lens(pad, e0 if side < 0 else e1, e1 if side < 0 else e0, 7.0 * sc, 1.0, power=0.6, skew=0.55)
        st.over(eye, 1.0, eye)
        ex, ey = T(128 + side * 26, 96)
        st.add_core(star4(pad, ex, ey, 9.0 * sc, 0.95, rot=0.2, thin=0.12, diag=0.4))
    # ---- 모여듦 (0 ~ 2): 바깥에서 안으로 소용돌이치는 기운 / 흩어짐 (7): 위로 흩어지는 가닥
    wisp = pad.zeros()
    if f <= 2:
        k = 1.0 - f / 3.0
        for i in range(10):
            a0 = TAU * i / 10 + 0.3
            pts = []
            for jj in range(20):
                u = jj / 19
                r = (120 - 70 * u) * (0.8 + 0.4 * k)
                a = a0 + 1.6 * u
                pts.append((128 + math.cos(a) * r, 120 + math.sin(a) * r * 0.85))
            wisp = np.maximum(wisp, pad.stroke(pts, 1.0, wfn=lambda t: 5.0 * (1 - t) + 1.0, afn=lambda t, k=k: k * 0.6 * np.sin(np.pi * t), soft=1.5))
        st.body = st.body * (0.55 + 0.45 * (f / 2.0))
    if f == 7:
        nz = fbm(X / 22.0, Y / 22.0 - 2.0, seed=31, octaves=3)
        keep = smooth(-0.05, 0.25, nz + (Y - 40) / 260.0)
        st.body = st.body * keep
        st.core = st.core * keep
        for i in range(12):
            x0 = 40 + 16 * i + rnd.uniform(-4, 4)
            y0 = rnd.uniform(60, 200)
            pts = [(x0 + 6 * math.sin(u * 3 + i), y0 - 50 * u) for u in np.linspace(0, 1, 12)]
            wisp = np.maximum(wisp, pad.stroke(pts, 1.0, wfn=lambda t: 4.0 * (1 - t) + 0.8, afn=lambda t: 0.5 * (1 - t), soft=1.2))
    core1 = pad.down(st.core)
    glow = obj_glow(pad, np.maximum(st.body, wisp), core1, halo=((1.5, 0.5), (4.0, 0.38), (9.0, 0.22)), body_k=0.92)
    a = BR_A[f]
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow * a) * wb, clamp01(core1 * a) * wb


sprite("rfx_jbear", "rotate_xyz", 256, 256, BR_N, 4, "warlord", zoom=4,
       desc="곰의 혼 (앞모습 · 일어선 곰): 0 ~ 2 기운이 소용돌이쳐 모임 → 3 ~ 6 포효 (이어 돌림 — 턱 · 털끝) → 7 위로 흩어짐. 성난 눈 · 송곳니 · 귀 · 양옆 앞발 발톱")(bear_frame)


# ============================================================ 실행

def main():
    args = sys.argv[1:]
    folder = None
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
    only = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    for name, sp in SPECS.items():
        if only and name not in only:
            continue
        glows, cores = V2C.render(sp)
        img = to_sheet(list(glows) + list(cores), sp.cols, sp.cw, sp.ch)
        assert img.size[0] <= 4096 and img.size[1] <= 4096, (sp.name, img.size)
        save(sp.name, img, sp.cw, sp.ch, sp.facing)
        if folder:
            print("  preview:", V2C.preview(sp, glows, cores, folder))


if __name__ == "__main__":
    main()
