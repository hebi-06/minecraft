#!/usr/bin/env python3
"""0.50.0 무기 스킬의 2D 물체 판 그림 (사용자 "쌍뱀의 축복 뱀이 너무 저퀄임 2d 바꾸거나 해갸 할듯 아르테미스의 은활 궁에 있는 달이 별로임 차라리 2d"):
입체 뱀 (serpent) · 입체 만월 (moon) 대신 v2 2D 그림 (tools/job_fx_art_v2.py 의 그리기 도움 — 빛 · 심지 두 겹, 흰색 = 알파) → tools/java_fx
(판 그림 — tools/fx_models_card2d.py 가 card_serpent · card_moon 판으로 구움, 파티클로는 설치하지 않음).

  python3 tools/weapon_fx_art_v2.py                       # 그림을 씀 (tools/java_fx/textures/particle/rpg/fx/<이름>.png)
  python3 tools/weapon_fx_art_v2.py --only rfx_wserpent   # 몇 개만 (쉼표로 여럿)
  python3 tools/weapon_fx_art_v2.py --preview <폴더>      # 확인용 그림도 씀 (장면마다: 어두운 바탕 · 낮 하늘 바탕 — 게임 색)
  python3 tools/weapon_fx_art_v2.py && python3 tools/fx_models.py   # 판 모델 (card_serpent · card_moon) 까지

칸 = 빛 (몸 채움 · 빛 무리) 장면 N 개 다음에 심지 (무늬 · 눈 · 송곳니 · 테 · 반짝임) 장면 N 개 (빛 = 칸 f, 심지 = 칸 N + f) — v2 직업 그림과 같은 꼴.
고정 시드 — 돌릴 때마다 같은 그림. 다른 그림을 쓰지 않음 (모두 이 스크립트가 처음부터 그림).

- rfx_wserpent  192×512 ×16  빛의 뱀 (머리가 위 — 옆모습 머리 · S 자로 꿈틀거리는 몸 · 비늘 · 마름모 무늬 · 배 비늘 · 꼬리 끝이 빛으로 흩어짐):
                0 ~ 5 꿈틀거림 (이어 돌림 — 혀 날름), 6 ~ 8 덤빔 (입을 쩍 벌림 — 송곳니), 9 ~ 11 묾 (닫히며 번쩍 → 움츠림),
                12 ~ 15 쉭쉭거림 (반쯤 벌린 입 · 혀 · 몸을 사림, 이어 돌림).
                주둥이 끝 = (96, 30) · 곧게 편 꼬리 끝 = y 500 → 판 모델에서 주둥이 끝 y 0.45 · 꼬리 끝 y -0.85 (입체 뱀과 같은 자리 — 무는 자리가 그대로)
- rfx_wmoon     256×256 ×16  만월 (원판 · 바다 · 구덩이 · 빛살 구덩이 · 밝은 테 · 초승달 빛 · 달무리 · 반짝임):
                0 ~ 4 차오름 (초승달 → 보름달), 5 ~ 12 빛남 (이어 돌림 — 달무리 맥동 · 도는 빛살 · 반짝임), 13 ~ 15 번쩍 → 조각나 흩어짐.
                원판 반지름 82 = 판 모델 0.5 (입체 만월과 같은 크기)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_fx_art_v2 import (BG, SS, TAU, Pad, Sprite, bez, clamp01, edge_fade, gblur, label, obj_glow, save_sprite, screen, smooth,  # noqa: E402
                           sparkle, star_pts)

# 확인용 색 (게임과 같게): 빛 (A) · 심지 (B)
GOLD = ((1.0, 0.76, 0.24), (1.0, 0.95, 0.78))  # 쌍뱀의 축복 금빛 뱀 (HERO_SKILLS.scepter.color)
SILVER = ((0.4, 0.5, 0.84), (0.96, 0.98, 1.0))  # 은빛 뱀 (HERO_SKILLS.scepter.silver — 0.50.0 빛을 강철빛으로: 낮 하늘에서도 보이게)
MOON = ((0.42, 0.56, 1.0), (1.0, 1.0, 1.0))  # 만월 (Artemis.AR_MOON_DEEP · 흰빛)
SKY = np.array([0.67, 0.78, 0.98], np.float32)  # 낮 하늘 (사진첩 바탕)

SPRITES = {}


def sprite(name):
    def deco(fn):
        SPRITES[name] = fn
        return fn
    return deco


def sm(e0, e1, x):
    """smoothstep (수 하나)"""
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


# ============================================================ 등뼈 거리장 (몸을 따라 간 길이 s · 등 쪽 + 옆 거리)

def spine_field(pad, pts, reach):
    """줄 pts 둘레 reach 칸 점 안: 가까운 점까지 따라 간 길이 s (칸 점 — pts[0] 이 0) · 옆 거리 (부호 — 가는 쪽의 왼쪽 법선 (cos θ, -sin θ) 쪽이 +).
    밖은 s = -1 · 옆 = 큰 값"""
    P = np.asarray(pts, np.float64)
    seg = np.hypot(*(P[1:] - P[:-1]).T)
    cum = np.concatenate([[0.0], np.cumsum(seg)])
    X, Y = pad.grid()
    d = np.full((pad.H, pad.W), 1e9, np.float32)
    s = np.full((pad.H, pad.W), -1.0, np.float32)
    lat = np.full((pad.H, pad.W), 1e9, np.float32)
    ss = pad.ss
    for i in range(len(P) - 1):
        ax, ay = P[i]
        bx, by = P[i + 1]
        x0 = max(0, int((min(ax, bx) - reach) * ss))
        x1 = min(pad.W, int(math.ceil((max(ax, bx) + reach) * ss)) + 1)
        y0 = max(0, int((min(ay, by) - reach) * ss))
        y1 = min(pad.H, int(math.ceil((max(ay, by) + reach) * ss)) + 1)
        if x0 >= x1 or y0 >= y1:
            continue
        xs, ys = X[y0:y1, x0:x1], Y[y0:y1, x0:x1]
        vx, vy = bx - ax, by - ay
        L2 = vx * vx + vy * vy
        L = math.sqrt(L2) or 1.0
        t = np.clip(((xs - ax) * vx + (ys - ay) * vy) / max(L2, 1e-12), 0, 1)
        qx, qy = ax + t * vx, ay + t * vy
        dd = np.hypot(xs - qx, ys - qy)
        sd = ((xs - qx) * vy - (ys - qy) * vx) / L
        sub = d[y0:y1, x0:x1]
        m = dd < sub
        sub[m] = dd[m]
        s[y0:y1, x0:x1][m] = (cum[i] + t * seg[i])[m]
        lat[y0:y1, x0:x1][m] = sd[m]
    return s, lat, cum[-1]


# ============================================================ rfx_wserpent (빛의 뱀 — 머리가 위)

SW, SH = 192, 512
SNOUT = 30.0  # 주둥이 끝 (칸 y)
SLEN = 470.0  # 등뼈 길이 (주둥이 → 꼬리 끝, 곧게 펴면 y 500)
SCX = SW / 2.0
HK = 1.18  # 머리 크기 배 (머리 쪽 좌표 → 칸 점)
NECK = 64.0 * HK  # 머리 뒤 (주둥이에서, 칸 점)

# 옆모습 머리 (머리 쪽 좌표 (b, a) — b = 등 쪽 + (그림 오른쪽), a = 주둥이에서 꼬리 쪽 +): 독사 꼴 — 네모진 뭉툭한 주둥이 · 판판한 정수리 ·
# 눈 위로 튀어나온 눈썹 비늘 · 턱 뒤 독샘이 불룩한 각진 턱 (머리가 목보다 2배 넘게 높음) · 가는 아래턱
SNOUT_F = [(9.0, 1.6), (6.0, 0.0), (2.0, -0.6), (-1.5, 0.0), (-4.0, 2.4)]  # 주둥이 앞 (등 쪽 → 배 쪽)
DORS = [(9.0, 1.6), (12.0, 5), (14.0, 11), (15.0, 18), (15.6, 26), (15.8, 34), (15.3, 42), (13.6, 50), (10.6, 56), (8.5, 60), (8.1, 64)]
VENT = [(-4.0, 2.4), (-6.0, 8), (-8.8, 16), (-11.8, 24), (-14.6, 32), (-17.0, 40), (-17.8, 44), (-16.0, 50), (-11.0, 56), (-8.6, 60), (-8.1, 64)]
MOUTH = [(-1.5, 0.0), (-3.4, 8), (-5.4, 16), (-7.2, 24), (-8.8, 32), (-10.2, 38), (-9.8, 41.5)]
HINGE = (-12.0, 43.5)  # 턱 관절 (입꼬리 뒤 아래)
JAW_A = 47.0  # 아래턱 뒤 끝 (배 쪽 윤곽에서 — 머리뼈와 아래턱이 나뉘는 곳)
EYE = (6.6, 19.0)
EYE_R = 5.0


def chaikin(pts, it=3):
    """열린 줄을 부드럽게 (양 끝은 그대로)"""
    for _ in range(it):
        out = [pts[0]]
        for p, q in zip(pts, pts[1:]):
            out.append((0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]))
            out.append((0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]))
        out.append(pts[-1])
        pts = out
    return pts


def mouth_b(a):
    """입 줄의 b (닫힌 입 — a 에서)"""
    xs = [q[1] for q in MOUTH]
    ys = [q[0] for q in MOUTH]
    return float(np.interp(a, xs, ys))


def rot_ba(pts, center, ang):
    """머리 쪽 좌표 (b, a) 점들을 center 둘레로 ang (라디안 — + 이면 주둥이가 등 쪽 (+b) 으로 들림)"""
    cb, ca = center
    c, s = math.cos(ang), math.sin(ang)
    return [(cb + (b - cb) * c - (a - ca) * s, ca + (b - cb) * s + (a - ca) * c) for b, a in pts]


def head_parts(open_a):
    """머리 부분 (머리 쪽 좌표): 머리뼈 (위턱 · 눈 · 정수리 · 목덜미) · 아래턱 · 입 안 · 송곳니 · 이 · 입 줄 (위 · 아래).
    open_a = 입 벌림 (라디안 — 아래턱이 -α, 머리뼈가 +0.3α 로 턱 관절 둘레를 돎)"""
    # 둘레 한 줄 (주둥이 위 → 정수리 → 목 → 배 쪽 → 턱 → 주둥이 아래 → 앞) 을 부드럽게 한 뒤 나눔
    front = chaikin(SNOUT_F, 2)
    dors = chaikin(DORS)
    vent = chaikin(VENT)
    mouth = chaikin(MOUTH, 2)
    ja = (float(np.interp(JAW_A, [q[1] for q in vent], [q[0] for q in vent])), JAW_A)
    throat = [ja] + [p for p in vent if p[1] > JAW_A]
    chin = [p for p in vent if p[1] < JAW_A] + [ja]
    km = min(range(len(front)), key=lambda i: (front[i][0] - MOUTH[0][0]) ** 2 + (front[i][1] - MOUTH[0][1]) ** 2)
    skull = front[:km + 1] + mouth[1:] + [HINGE] + throat[::-1][::-1] + dors[::-1][:-1]
    jaw = front[km:] + chin[1:] + [HINGE] + mouth[::-1][:-1]
    up, dn = 0.3 * open_a, -open_a
    # 벌리면 턱 관절 뒤 (머리뼈의 아래턱 뒤 끝 ↔ 돈 아래턱의 뒤 끝) 가 벌어짐 → 목살로 채움
    neck_fill = [HINGE, rot_ba([ja], HINGE, up)[0], rot_ba([ja], HINGE, dn)[0]]
    m_up = rot_ba(mouth, HINGE, up)
    m_dn = rot_ba(mouth, HINGE, dn)
    # 송곳니 (위턱 앞 — 입 줄에서 아래턱 쪽으로 휘어 내려옴 · 벌릴수록 길게 드러남) · 뒤의 작은 송곳니
    fl = 2.5 + 13.0 * min(1.0, open_a / 1.0)
    fangs = []
    for a0, a1, k in ((4.6, 7.4, 1.0), (11.5, 13.3, 0.55)):
        b0, b1 = mouth_b(a0), mouth_b(a1)
        am = (a0 + a1) / 2
        bm = mouth_b(am)
        # 가늘고 뒤로 휜 갈고리 (바깥 줄 · 안 줄 — 끝이 뾰족)
        tip = (bm - fl * k, am + 0.5 * fl * k)
        outer = bez((b0 + 0.2, a0), (bm - fl * k * 0.7, a0 - 0.4 * k), tip, 8)
        inner = bez(tip, (bm - fl * k * 0.45, am + 0.32 * fl * k), (b1 + 0.2, a1), 8)
        fangs.append(rot_ba(outer + inner[1:], HINGE, up))
    teeth = []
    if open_a > 0.2:
        for a0 in (22.0, 30.0):  # 위턱 뒤 작은 이 둘 · 아래턱 이 셋 (송곳니가 돋보이게 적고 작게)
            b0 = mouth_b(a0)
            teeth.append(rot_ba([(b0 + 0.2, a0 - 1.0), (b0 - 2.2, a0 + 0.8), (b0 + 0.2, a0 + 1.1)], HINGE, up))
        for a0 in (9.0, 17.0, 25.0):
            b0 = mouth_b(a0)
            teeth.append(rot_ba([(b0 - 0.2, a0 - 1.0), (b0 + 2.2, a0 + 0.3), (b0 - 0.2, a0 + 1.1)], HINGE, dn))
    return dict(skull=rot_ba(skull, HINGE, up), jaw=rot_ba(jaw, HINGE, dn), cavity=m_up + [HINGE] + m_dn[::-1], fangs=fangs, teeth=teeth,
                m_up=m_up, m_dn=m_dn, up=up, dn=dn, dors=dors, vent=vent, neck_fill=neck_fill)


def head_to_cell(pts, cx=SCX, cy=SNOUT):
    """머리 쪽 좌표 → 칸 (머리는 곧게 위: b → +x, a → +y · HK 배)"""
    return [(cx + b * HK, cy + a * HK) for b, a in pts]


def body_w(u):
    """몸 반폭 (칸 점) — u = 등뼈를 따라 간 몫 (0 주둥이 → 1 꼬리 끝): 가는 목 (9.6) → 몸통 (16.4) → 뾰족한 꼬리"""
    w = 9.6 + 6.8 * sm(0.17, 0.36, u)
    return w * (1 - sm(0.5, 1.0, u)) ** 0.8


def body_w_arr(u):
    w = 9.6 + 6.8 * smooth(0.17, 0.36, u)
    return w * clamp01(1 - smooth(0.5, 1.0, u)) ** 0.8


def _spine(phase, amp, k, bend, ext, n):
    total = SLEN * (1 + ext)
    ds = total / n
    x, y = SCX, SNOUT
    pts = [(x, y)]
    for i in range(n):
        u = (i + 0.5) * ds / SLEN
        a = amp * 0.62 * sm(0.15, 0.55, u)
        th = a * math.sin(TAU * k * u - phase) + bend * sm(0.15, 0.36, u)
        x += math.sin(th) * ds
        y += math.cos(th) * ds
        pts.append((x, y))
    return pts


def spine(phase, amp=1.0, k=1.16, ext=0.0, n=260):
    """등뼈 점 (주둥이 → 꼬리 끝 [+ ext 몫 — 꼬리 뒤 빛 자국]): 머리 · 목은 곧게 위를 봄, 목 뒤부터 S 자 물결 (phase 가 늘면 물결이 꼬리 쪽으로 흐름).
    물결의 세기는 꼬리로 갈수록 커짐 — 머리는 늘 같은 자리 (무는 자리). 목을 살짝 굽혀 (bend) 몸의 가로 가운데가 칸 가운데에 머묾
    (몸 전체가 좌우로 쏠려 흔들리지 않게)"""
    def mean_dx(bend):
        p = _spine(phase, amp, k, bend, 0.0, 130)
        body = [x - SCX for i, (x, y) in enumerate(p) if i / 130 > 0.16]
        return sum(body) / len(body)
    m0, m1 = mean_dx(0.0), mean_dx(0.1)
    bend = -m0 * 0.1 / (m1 - m0) if abs(m1 - m0) > 1e-6 else 0.0
    return _spine(phase, amp, k, bend, ext, n)


def serpent_frame(phase, amp=1.0, k=1.16, open_a=0.0, tongue=0.0, flash=0.0, seed=0):
    pad = Pad(SW, SH)
    X, Y = pad.grid()
    rnd = random.Random(seed)
    pts = spine(phase, amp, k)
    ext = spine(phase, amp, k, ext=0.14)
    s_px, lat, total = spine_field(pad, pts, 26)
    u = np.where(s_px >= 0, s_px / SLEN, 0.0)
    w = body_w_arr(u)
    inside_d = np.where(s_px >= 0, w - np.abs(lat), -1e3)
    body = clamp01(inside_d * SS + 0.5) * smooth(NECK - 24, NECK - 14, s_px)  # 목 앞은 머리가 덮음
    v = np.where(w > 1e-3, lat / np.maximum(w, 1e-3), 0.0)
    tail_k = 1 - smooth(0.8, 1.0, u)  # 꼬리 끝으로 갈수록 무늬가 옅어짐
    # ---- 몸의 심지: 부피 (가운데 조금 밝게) · 등 쪽 테 빛 · 배 비늘 띠 · 옆구리 비늘 그물 · 등의 마름모 사슬 무늬
    vol = 0.05 + 0.1 * clamp01(1 - v * v)
    dors_rim = smooth(0.62, 0.8, v) * (1 - smooth(0.88, 0.98, v)) * 0.92
    belly = smooth(-0.95, -0.86, v) * (1 - smooth(-0.56, -0.46, v))
    scute = np.abs(((s_px / 5.8) % 1.0) - 0.5) * 2  # 0 = 비늘 사이 줄
    belly_c = belly * (0.06 + 0.5 * smooth(0.2, 0.42, scute))
    g1 = np.abs(((s_px / 6.4 + v / 0.3) % 1.0) - 0.5)
    g2 = np.abs(((s_px / 6.4 - v / 0.3) % 1.0) - 0.5)
    lattice = np.maximum(smooth(0.37, 0.48, g1), smooth(0.37, 0.48, g2)) * (1 - belly) * (1 - smooth(0.52, 0.66, v)) * 0.2
    blot = np.zeros_like(s_px)
    blot_rim = np.zeros_like(s_px)
    blot_eye = np.zeros_like(s_px)
    s0 = 84.0
    while s0 < SLEN * 0.88:
        dd = np.abs(s_px - s0) / (13.0 * (1 - 0.38 * sm(0.5, 0.9, s0 / SLEN))) + np.abs(v - 0.12) / 0.6
        blot = np.maximum(blot, clamp01((1 - dd) * 7))
        blot_rim = np.maximum(blot_rim, clamp01(1 - np.abs(dd - 0.84) * 8))
        blot_eye = np.maximum(blot_eye, clamp01((0.3 - dd) * 12))
        s0 += 29.0
    core_body = np.maximum.reduce([vol, dors_rim, belly_c, lattice * (1 - blot), blot * 0.12, blot_rim * 0.95, blot_eye * 0.8])
    core_body = core_body * body * (0.3 + 0.7 * tail_k)
    glow_body = body * (1 - 0.42 * smooth(0.76, 1.0, u))
    # ---- 꼬리 뒤 빛 자국 (꼬리를 이어 흐르는 가는 빛 · 흩어지는 빛 알갱이)
    tail_pts = [p for i, p in enumerate(ext) if i >= int(len(ext) * (0.8 / 1.14))]
    wake_g = pad.glowline(tail_pts, 2.6, afn=lambda t: 0.55 * (1 - t) ** 1.4)
    wake_c = pad.glowline(tail_pts, 0.9, afn=lambda t: 0.75 * (1 - t) ** 2)
    motes_c = pad.zeros()
    motes_g = pad.zeros()
    tx, ty = pts[-1]
    for i in range(7):
        t = (i / 7 + phase / TAU) % 1.0
        ang = rnd.uniform(0, TAU)
        r = 6 + 34 * t
        mx = tx + math.cos(ang) * r * 0.8 + rnd.uniform(-4, 4)
        my = ty - 16 + math.sin(ang) * r * 0.5 + 22 * t
        if 4 < mx < SW - 4 and 4 < my < SH - 4:
            al = math.sin(math.pi * t) * rnd.uniform(0.6, 1.0)
            motes_c = np.maximum(motes_c, sparkle(pad, mx, my, rnd.uniform(3.0, 5.5) * (0.6 + 0.4 * al), al, rot=rnd.uniform(0, 1), diag=0.0))
            motes_g = np.maximum(motes_g, pad.radial(mx, my, 7.0, 1.5) * al * 0.6)
    # ---- 머리 (옆모습 — 주둥이가 위)
    hp = head_parts(open_a)
    up, dn = hp["up"], hp["dn"]
    skull = pad.poly(head_to_cell(hp["skull"]))
    jaw = pad.poly(head_to_cell(hp["jaw"]))
    head = np.maximum.reduce([skull, jaw, pad.poly(head_to_cell(hp["neck_fill"])) if open_a > 0.05 else pad.zeros()])
    cavity = pad.poly(head_to_cell(hp["cavity"])) * (1 - head) if open_a > 0.05 else pad.zeros()

    def Ps(pts_, rot=None):
        """머리뼈 (rot = None) · 아래턱 (rot = dn) 에 붙은 점들 → 칸"""
        return head_to_cell(rot_ba(pts_, HINGE, up if rot is None else rot))

    def P(b, a, rot=None):
        return Ps([(b, a)], rot)[0]

    dors, vent = hp["dors"], hp["vent"]
    # 머리 심지: 바탕 (등 쪽이 조금 밝게) · 등 쪽 테 · 눈썹 비늘 · 눈 (세로 동공 · 반짝) · 콧구멍 · 열 구멍 · 입 줄 (위 · 아래) · 아래턱 비늘 몇 줄
    hb = head * (0.1 + 0.12 * clamp01((X - (SCX - 10)) / 28))
    rim_d = pad.stroke(Ps([(b - 1.5, a) for b, a in dors[4:-8]]), 2.3, a0=0.95, a1=0.55) * skull
    brow = pad.stroke(Ps(chaikin([(11.0, 11.0), (14.4, 17.0), (14.6, 23.5), (13.4, 28.5)], 2)), 3.2, 2.0, a0=1.0, a1=0.75) * skull
    canth = pad.stroke(Ps(chaikin([(8.2, 2.6), (10.4, 7.0), (11.4, 11.5)], 2)), 1.4, a0=0.55) * skull
    ec = P(*EYE)
    er = EYE_R * HK
    X0, Y0 = X - ec[0], Y - ec[1]
    ca_, sa_ = math.cos(up), math.sin(up)
    xr, yr = X0 * ca_ + Y0 * sa_, -X0 * sa_ + Y0 * ca_  # 머리 쪽 (b · a 방향) 으로 돌린 눈 둘레 좌표
    # 눈: 아몬드 (머리 길이 쪽으로 김) · 위 (등 쪽) 를 눈썹 비늘이 덮어 비스듬히 잘림 (사나운 눈) · 둘레의 어두운 홈 · 세로 동공
    almond = np.sqrt((xr / (er * 0.92)) ** 2 + (yr / (er * 1.12)) ** 2)
    lid = (er * 0.5 + 0.16 * yr) - xr  # + 이면 눈꺼풀 아래 (등 쪽 위가 잘림)
    eye = clamp01((1 - almond) * er * SS * 0.5 + 0.5) * clamp01(lid * SS * 0.5 + 0.5)
    eye_ring = clamp01((1.28 - almond) * er * SS * 0.25) * (1 - eye) * clamp01((lid + 2.2) * SS * 0.5)
    slit = clamp01((1 - np.sqrt((xr / (er * 0.62)) ** 2 + (yr / 0.85) ** 2)) * 5)  # 세로 동공 (머리 길이에 수직 — 뱀이 가로로 날 때 세로)
    eye_c = eye * (1 - slit * 0.95)
    glint = pad.disc_field(ec[0] - 1.6 * ca_ - 2.6 * sa_ * 0, ec[1] - 3.2, 1.4) * eye
    nos = P(4.6, 3.6)
    nostril = pad.disc_field(nos[0], nos[1], 1.5)
    pit = P(1.2, 10.0)
    pit_m = pad.disc_field(pit[0], pit[1], 1.2)
    chin_s = pad.zeros()
    for a0 in (12.0, 20.0, 28.0, 36.0):
        b0 = mouth_b(a0)
        chin_s = np.maximum(chin_s, pad.stroke(Ps([(b0 - 1.6, a0 - 1.0), (b0 - 4.4, a0 + 2.0)], dn), 1.0, a0=0.42))
    lip_up = pad.stroke(head_to_cell(hp["m_up"]), 1.8, 1.3, a0=0.95, a1=0.65)
    lip_dn = pad.stroke(head_to_cell(hp["m_dn"]), 1.5, a0=0.85, a1=0.55) if open_a > 0.05 else pad.zeros()
    head_c = np.maximum.reduce([hb, rim_d, brow, canth, eye_c, glint, chin_s * jaw])
    head_c = head_c * (1 - eye_ring) * (1 - nostril * 0.9) * (1 - pit_m * 0.75)
    # 입 줄: 어두운 홈 (빛도 옅게 — 위턱 · 아래턱이 갈라져 보임) + 바로 위의 밝은 입술 빛
    gap = pad.stroke(head_to_cell(hp["m_up"]), 1.5, 1.1, a0=1.0, a1=0.8) if open_a <= 0.05 else pad.zeros()
    lip_hi = pad.stroke(Ps([(b + 1.6, a) for b, a in chaikin(MOUTH, 2)][1:-1]), 1.3, 0.9, a0=0.85, a1=0.5) * skull
    head_c = np.maximum(head_c * (1 - gap), lip_hi * (1 - gap))
    head_c = np.maximum.reduce([head_c, lip_up * (open_a > 0.05), lip_dn])
    # 송곳니 · 이 (벌렸을 때) · 입 안 (흐린 빛 — 비어 보이게)
    fang = pad.polys([head_to_cell(f) for f in hp["fangs"]]) if open_a > 0.12 else pad.zeros()
    teeth = pad.polys([head_to_cell(t) for t in hp["teeth"]]) if hp["teeth"] else pad.zeros()
    venom = pad.zeros()
    if open_a > 0.9:
        f0 = head_to_cell(hp["fangs"][0])
        tip = max(f0, key=lambda q: -q[0])
        venom = sparkle(pad, tip[0] - 1.0, tip[1] + 1.5, 5.5, 1.0, rot=0.4, diag=0.0)
    # ---- 혀 (갈래 — 주둥이 앞으로 날름)
    tongue_c = pad.zeros()
    if tongue > 0.05:
        tl = 5 + 17 * tongue
        if open_a > 0.05:
            pu = rot_ba([(mouth_b(4.0), 4.0)], HINGE, up)[0]
            pd = rot_ba([(mouth_b(4.0), 4.0)], HINGE, dn)[0]
            base = ((pu[0] + pd[0]) / 2, (pu[1] + pd[1]) / 2 + 3.0)
            beta = (up + dn) / 2
        else:
            base, beta = (-0.6, 1.2), 0.0
        fwd = (math.sin(beta), -math.cos(beta))
        side = (-fwd[1], fwd[0])
        wob = math.sin(phase * 3 + seed) * 2.0 * tongue
        tp = bez(base, (base[0] + fwd[0] * tl * 0.55 + side[0] * wob, base[1] + fwd[1] * tl * 0.55 + side[1] * wob),
                 (base[0] + fwd[0] * tl, base[1] + fwd[1] * tl), 12)
        end = tp[-1]
        tc = pad.stroke(head_to_cell(tp), 1.8, 1.3, a0=1.0)
        for sg in (-1, 1):
            spread = 3.8 + 1.4 * tongue
            f_ = (end[0] + fwd[0] * 7.0 + side[0] * spread * sg, end[1] + fwd[1] * 7.0 + side[1] * spread * sg)
            mid = (end[0] + fwd[0] * 4.0, end[1] + fwd[1] * 4.0)
            tc = np.maximum(tc, pad.stroke(head_to_cell(bez(end, mid, f_, 8)), 1.3, 0.5, a0=1.0))
        tongue_c = tc
    # ---- 묾의 번쩍임 (입 앞)
    flash_c = pad.zeros()
    flash_g = pad.zeros()
    if flash > 0:
        fxp, fyp = SCX - 3.0, SNOUT + 4.0
        flash_c = sparkle(pad, fxp, fyp, 30 * flash, 1.0, rot=0.3, thin=0.1, diag=0.55)
        for i in range(6):
            a_ = -math.pi / 2 + (i - 2.5) * 0.5
            p0 = (fxp + math.cos(a_) * 8, fyp + math.sin(a_) * 8)
            p1 = (fxp + math.cos(a_) * (8 + 20 * flash), fyp + math.sin(a_) * (8 + 20 * flash))
            flash_c = np.maximum(flash_c, pad.stroke([p0, p1], 2.2, 0.4, a0=0.95, a1=0.2))
        flash_g = pad.radial(fxp, fyp, 34 * flash, 1.2) * 0.9
    # ---- 겹치기: 몸 → 머리 (머리가 몸 위) → 입 안 · 이 · 송곳니 · 혀
    glow = glow_body * (1 - head) + head * (1 - 0.55 * gap)
    if open_a > 0.05:
        # 입 안: 턱 관절 쪽 (깊은 곳) 이 짙고 입 앞으로 옅어짐
        hcx, hcy = head_to_cell([HINGE])[0]
        deep = clamp01(1 - np.sqrt((X - hcx) ** 2 + (Y - hcy) ** 2) / (40.0 * HK))
        glow = np.maximum(glow, cavity * (0.12 + 0.4 * deep))
    core = core_body * (1 - head) + head_c * head
    core = core * (1 - cavity)
    glow = np.maximum.reduce([glow, fang, teeth * 0.9, clamp01(tongue_c * 1.4), wake_g * (1 - body), motes_g, flash_g])
    core = np.maximum.reduce([core, fang, teeth * 0.85, tongue_c, venom, wake_c * (1 - body), motes_c, flash_c])
    core1 = pad.down(core)
    glow1 = obj_glow(pad, glow, core1, halo=((1.4, 0.5), (3.8, 0.36), (8.5, 0.22)), body_k=0.92, core_bloom=((1.4, 0.32), (4.0, 0.26)))
    return glow1, core1


# 장면: (물결 phase · 세기 amp · 물결 수 k · 입 벌림 · 혀 · 번쩍)
SERPENT_FRAMES = (
    # 0 ~ 5 꿈틀거림 (이어 돌림) — 1 ~ 3 혀 날름
    [dict(phase=TAU * f / 6, tongue=[0.0, 0.55, 1.0, 0.6, 0.0, 0.0][f]) for f in range(6)]
    # 6 ~ 8 덤빔 (몸을 조금 펴며 입을 쩍 벌림)
    + [dict(phase=TAU * f / 6, amp=0.85, open_a=a) for f, a in zip(range(3), (0.55, 1.0, 1.3))]
    # 9 ~ 11 묾: 닫힘 → 닫혀 번쩍 → 움츠림 (몸이 용수철처럼 굽음)
    + [dict(phase=TAU * 3 / 6, amp=0.9, open_a=0.4, flash=0.6), dict(phase=TAU * 3.5 / 6, amp=1.05, flash=1.0),
       dict(phase=TAU * 4 / 6, amp=1.3, k=1.24, flash=0.35)]
    # 12 ~ 15 쉭쉭거림 (사린 몸이 앞뒤로 흔들림 · 반쯤 벌린 입 · 혀)
    + [dict(phase=TAU * p, amp=1.32, k=1.3, open_a=a, tongue=t) for p, a, t in
       ((0.0, 0.45, 0.9), (0.06, 0.6, 0.6), (0.12, 0.5, 1.0), (0.06, 0.36, 0.7))]
)


@sprite("rfx_wserpent")
def make_serpent():
    glows, cores = [], []
    for i, fr in enumerate(SERPENT_FRAMES):
        g, c = serpent_frame(seed=11 + i, **fr)
        glows.append(g)
        cores.append(c)
    return Sprite("rfx_wserpent", "lookat_direction", SW, SH, len(SERPENT_FRAMES), "priest", glows, cores, cols=8, zoom=7)


# ============================================================ rfx_wmoon (만월)

MS = 256
MC = MS / 2.0
MR = 82.0  # 원판 반지름 (판 모델 0.5)

# 바다 (원판 반지름 몫 좌표 — 오른쪽 +x · 아래 +y): 가운데 · 반지름 (x, y) · 짙기
MARIA = [(-0.3, -0.34, 0.3, 0.26, 0.5), (0.13, -0.36, 0.17, 0.15, 0.46), (0.3, -0.06, 0.2, 0.17, 0.44), (0.52, 0.16, 0.13, 0.15, 0.38),
         (-0.16, 0.36, 0.2, 0.15, 0.36), (-0.55, 0.02, 0.24, 0.38, 0.4), (0.64, -0.24, 0.1, 0.08, 0.46), (0.06, 0.06, 0.12, 0.1, 0.3),
         (-0.36, 0.62, 0.14, 0.1, 0.3)]
# 구덩이: 가운데 · 반지름 (칸 점)
CRATERS = [(-0.12, 0.62, 7.5), (0.34, 0.42, 5.0), (-0.52, -0.42, 4.5), (0.58, -0.5, 4.0), (-0.68, 0.32, 5.5), (0.2, -0.62, 3.6), (0.7, 0.28, 3.2),
           (-0.3, -0.06, 3.0), (0.46, 0.66, 3.4), (-0.06, -0.2, 2.6), (0.1, 0.3, 2.8), (-0.42, 0.18, 2.4), (0.8, -0.04, 2.6), (-0.8, -0.18, 2.8),
           (0.28, 0.12, 2.2), (-0.2, 0.86, 2.4)]
TYCHO = (-0.12, 0.62)


def _noise(rnd, X, Y, scale, octaves=3):
    """부드러운 값 소음 (0 ~ 1 — 무작위 격자를 bicubic 으로 늘린 것, 몇 겹): scale = 격자 칸 (칸 점), 고정 시드"""
    H, W = X.shape
    px = W / max(1e-6, float(X[0, -1] - X[0, 0] + (X[0, 1] - X[0, 0])))  # 4배 그림판의 칸 점당 픽셀
    out = np.zeros_like(X)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cell = max(2.0, scale / (2 ** o) * px)
        gw, gh = int(W / cell) + 4, int(H / cell) + 4
        g = np.array([[rnd.random() for _ in range(gw)] for _ in range(gh)], np.float32)
        im = Image.fromarray(g, "F").resize((int(gw * cell), int(gh * cell)), Image.BICUBIC)
        a = np.asarray(im, np.float32)
        ox, oy = int(cell), int(cell)
        out += amp * a[oy:oy + H, ox:ox + W]
        tot += amp
        amp *= 0.5
    return np.clip(out / tot, 0.0, 1.0)


def moon_surface(pad):
    """보름달 겉 (심지 밝기 0 ~ 1 — 원판 안): 밝은 땅 · 바다 (짙음) · 구덩이 (테 밝음 · 속 그늘) · 빛살 구덩이 · 밝은 테 · 부피"""
    X, Y = pad.grid()
    rnd = random.Random(808)
    nx, ny = (X - MC) / MR, (Y - MC) / MR
    r = np.sqrt(nx * nx + ny * ny)
    n1 = _noise(rnd, X, Y, 26.0, 3)
    n2 = _noise(rnd, X, Y, 7.0, 2)
    surf = 0.9 + 0.08 * (n2 - 0.5)
    mare = np.zeros_like(X)
    n3 = _noise(rnd, X, Y, 9.0, 3)
    for mx, my, rx, ry, k in MARIA:
        dd = np.sqrt(((nx - mx) / rx) ** 2 + ((ny - my) / ry) ** 2)
        edge = 0.9 + 1.1 * (n1 - 0.5) + 0.5 * (n3 - 0.5)  # 바다 가장자리가 들쭉날쭉
        mare = np.maximum(mare, smooth(edge + 0.3, edge - 0.2, dd) * k)
    mare = gblur(mare, 1.3 * SS) * (0.72 + 0.56 * (n2 - 0.5))  # 부드럽게 · 속이 얼룩덜룩
    surf = surf - mare * 1.02  # 0.50.0 사진첩: 낮 하늘 앞에서 바다가 거의 안 보였음 → 짙게
    # 빛살 구덩이 (티코 꼴 — 지어낸 자리): 원판을 가로지르는 가는 밝은 빛살
    tx, ty = TYCHO
    ang = np.arctan2(ny - ty, nx - tx)
    dist = np.sqrt((nx - tx) ** 2 + (ny - ty) ** 2)
    rays = np.zeros_like(X)
    rr = random.Random(9)
    for i in range(14):
        a0 = rr.uniform(-math.pi, math.pi)
        wdt = rr.uniform(0.02, 0.05)
        ln = rr.uniform(0.45, 1.25)
        da = np.abs(((ang - a0 + math.pi) % TAU) - math.pi)
        rays = np.maximum(rays, clamp01(1 - da / wdt) * clamp01(1 - dist / ln) * smooth(0.06, 0.14, dist) * rr.uniform(0.5, 1.0))
    surf = surf + rays * 0.16
    # 구덩이: 속 그늘 (빛이 오른쪽 위에서 — 그늘은 속의 오른쪽 위 벽) · 테 (왼쪽 아래가 밝음)
    lx, ly = 0.62, -0.78
    for cx_, cy_, cr in CRATERS:
        px, py = MC + cx_ * MR, MC + cy_ * MR
        dx, dy = X - px, Y - py
        d = np.sqrt(dx * dx + dy * dy)
        bowl = clamp01((cr - d) * SS * 0.5 + 0.5)
        # 속: 빛 쪽 벽은 그늘 (빛이 들어오는 쪽 반대편 속이 밝음)
        facing = (dx * lx + dy * ly) / (d + 1e-6)
        inner = bowl * (0.2 + 0.18 * facing) * clamp01(d / cr * 1.4)
        ring = np.exp(-0.5 * ((d - cr) / max(0.6, cr * 0.16)) ** 2) * (0.5 - 0.5 * facing)
        surf = surf - inner + ring * 0.2
    surf = np.clip(surf, 0.0, 1.0)
    # 부피: 왼쪽 아래 가장자리가 조금 어둡고 · 테는 밝게 (빛나는 테두리)
    shade = 0.84 + 0.16 * clamp01(0.5 + 0.5 * (nx * 0.62 - ny * 0.78) / np.maximum(r, 1e-3) * r)
    surf = surf * shade
    rim = np.exp(-0.5 * ((r - 1.0) * MR / 1.6) ** 2)
    limb = smooth(0.84, 1.0, r) * 0.1
    surf = np.maximum(surf + limb, rim * 0.95)
    return surf, r


def moon_crescent(pad, r, nx, ny, k=1.0):
    """오른쪽 위 테를 따라 밝은 초승달 빛 (아르테미스의 초승달 — 원판 안, 두께 최대 7점)"""
    ox, oy = -0.075, 0.06
    r2 = np.sqrt((nx - ox) ** 2 + (ny - oy) ** 2)
    band = smooth(0.985, 1.03, r2) * clamp01((1.0 - r) * MR * SS * 0.5 + 0.5)
    return band * k


def moon_frame(f):
    pad = Pad(MS, MS)
    X, Y = pad.grid()
    nx, ny = (X - MC) / MR, (Y - MC) / MR
    surf, r = moon_surface(pad)
    disc = clamp01((1.0 - r) * MR * SS * 0.5 + 0.5)
    outside = 1 - disc
    TH = np.arctan2(ny, nx)
    rnd = random.Random(31)
    # 장면 값: 차오름 몫 (lit) · 빛 세기 (vis) · 빛남 돌림 (q — 0 ~ 1 이어 돌림) · 흩어짐 (burst)
    if f <= 4:
        lit = [0.12, 0.3, 0.56, 0.82, 1.0][f]
        vis = [0.45, 0.6, 0.78, 0.92, 1.0][f]
        q = 0.0
        burst = 0
    elif f <= 12:
        lit, vis, q, burst = 1.0, 1.0, (f - 5) / 8.0, 0
    else:
        lit, vis, q, burst = 1.0, 1.0, 0.0, f - 12
    # ---- 차오름 (오른쪽부터 — 경계 = 타원)
    yy = np.clip(ny, -1, 1)
    term = (1 - 2 * lit) * np.sqrt(np.maximum(0, 1 - yy * yy))
    lit_m = clamp01((nx - term) * MR / 2.2 + 0.5) if lit < 1 else np.ones_like(X)
    # ---- 원판의 심지 · 빛
    crescent = moon_crescent(pad, r, nx, ny)
    core = disc * np.maximum(surf, crescent) * lit_m
    glow = disc * (0.24 + 0.76 * lit_m)
    # ---- 달무리 (빛): 가까운 무리 · 넓은 무리 · 맥동
    pulse = 1.0 + 0.06 * math.sin(TAU * q)
    halo = (np.exp(-(r - 1.0) * MR / (15.0 * pulse)) * 0.85 + np.exp(-(r - 1.0) * MR / 42.0) * 0.3) * outside * vis
    # 둥근 달무리 고리 (반지름 1.36 — 빛이 고리를 따라 흐름)
    ring_r = 1.36 + 0.015 * math.sin(TAU * q)
    ring_flow = 0.65 + 0.35 * np.cos(TH * 3 - TAU * q)
    ring_g = np.exp(-0.5 * ((r - ring_r) * MR / 2.6) ** 2) * 0.24 * ring_flow * vis
    ring_c = np.exp(-0.5 * ((r - ring_r) * MR / 0.75) ** 2) * 0.2 * ring_flow * ring_flow * vis
    # 빛살 (테에서 바깥으로 — 빛남 동안 한 칸 (22.5°) 돎 · 길이가 일렁임)
    rays_g = pad.zeros()
    rays_c = pad.zeros()
    for i in range(16):
        a = i * TAU / 16 + q * TAU / 16 + rnd.uniform(-0.05, 0.05)
        base = rnd.uniform(0.5, 1.0) * (1.25 if i % 4 == 0 else 1.0)
        flick = 0.75 + 0.25 * math.sin(TAU * q * (1 if i % 2 else 2) + rnd.uniform(0, TAU))
        L = (16 + 26 * base) * flick * vis
        p0 = (MC + math.cos(a) * (MR + 1), MC + math.sin(a) * (MR + 1))
        p1 = (MC + math.cos(a) * (MR + 1 + L), MC + math.sin(a) * (MR + 1 + L))
        rays_g = np.maximum(rays_g, pad.stroke([p0, p1], 5.0, 1.0, a0=0.42 * vis, a1=0.0, soft=1.5))
        rays_c = np.maximum(rays_c, pad.stroke([p0, p1], 1.4, 0.2, a0=0.5 * vis, a1=0.0))
    # 반짝임 (달 둘레 다섯 — 천천히 돌며 차례로 반짝)
    spark_c = pad.zeros()
    spark_g = pad.zeros()
    for i in range(5):
        a = -1.1 + i * TAU / 5 + q * TAU / 5 * 0.35
        rad = MR * (1.18 + 0.12 * ((i * 37) % 5) / 4)
        tw = 0.5 + 0.5 * math.cos(TAU * (q - i / 5))
        sx, sy = MC + math.cos(a) * rad, MC + math.sin(a) * rad
        sz = (5.0 + 6.5 * tw) * vis
        spark_c = np.maximum(spark_c, sparkle(pad, sx, sy, sz, 0.55 + 0.45 * tw, rot=0.0, thin=0.12, diag=0.45))
        spark_g = np.maximum(spark_g, pad.radial(sx, sy, sz * 1.5, 1.4) * (0.35 + 0.4 * tw))
    if f <= 4:
        # 차오르는 동안: 밝은 쪽 테 위에 반짝임 하나 (차오르는 끝)
        ya = -math.acos(max(-1.0, min(1.0, 1 - 2 * lit))) * 0.0 - 0.95
        sx, sy = MC + math.cos(ya) * MR * 1.02, MC + math.sin(ya) * MR * 1.02
        spark_c = np.maximum(spark_c, sparkle(pad, sx, sy, 10 + 6 * lit, 1.0, rot=0.2, thin=0.1, diag=0.5))
    glow = np.maximum.reduce([glow, halo, ring_g, rays_g * outside, spark_g])
    core = np.maximum.reduce([core, ring_c * outside, rays_c * outside, spark_c])
    # ---- 번쩍 → 흩어짐
    if burst == 1:
        core = np.maximum(core, disc * 0.96)
        flare = sparkle(pad, MC, MC, MR * 1.42, 1.0, rot=0.0, thin=0.06, diag=0.6)
        core = np.maximum(core, flare)
        glow = np.maximum.reduce([glow, np.exp(-(r - 1.0) * MR / 30.0) * outside, disc])
    elif burst >= 2:
        # 흩어짐: 원판이 빛의 고리로 퍼지며 (가운데부터 비워짐) 조각 · 반짝임이 바깥으로 날아감
        rr = random.Random(77)
        k = burst - 1  # 1 · 2
        r_in, r_out = (0.55, 1.1) if k == 1 else (1.0, 1.36)
        ring = smooth(r_in - 0.16, r_in + 0.12, r) * (1 - smooth(r_out - 0.1, r_out + 0.05, r))
        wav = 0.72 + 0.28 * np.cos(TH * 5 + 0.7) * np.cos(TH * 3 - 1.1)
        core = np.maximum(core * (1 - disc) * (0.5 if k == 1 else 0.25), ring * wav * (0.9 if k == 1 else 0.42))
        glow = np.maximum(halo * (0.75 if k == 1 else 0.35), ring * (0.8 if k == 1 else 0.38))
        shards_c = pad.zeros()
        shards_g = pad.zeros()
        for i in range(22):
            a = rr.uniform(0, TAU)
            r0 = MR * rr.uniform(0.55, 1.0)
            fly = MR * (0.22 + 0.3 * k) * rr.uniform(0.6, 1.2)
            p0 = (MC + math.cos(a) * (r0 + fly * 0.45), MC + math.sin(a) * (r0 + fly * 0.45))
            p1 = (MC + math.cos(a) * (r0 + fly), MC + math.sin(a) * (r0 + fly))
            if not (8 < p1[0] < MS - 8 and 8 < p1[1] < MS - 8):
                continue
            al = rr.uniform(0.6, 1.0) * (1.0 if k == 1 else 0.65)
            shards_c = np.maximum(shards_c, pad.stroke([p0, p1], 0.6, 2.4, a0=0.0, a1=al))
            shards_c = np.maximum(shards_c, sparkle(pad, p1[0], p1[1], rr.uniform(3.5, 6.5) * (1.0 if k == 1 else 0.8), al, rot=rr.uniform(0, 1),
                                                    diag=0.0))
            shards_g = np.maximum(shards_g, pad.stroke([p0, p1], 2.0, 6.0, a0=0.0, a1=al * 0.55, soft=1.5))
        core = np.maximum(core, shards_c)
        glow = np.maximum(glow, shards_g)
    win = smooth(MS / 2 - 1, MS / 2 - 14, np.sqrt((X - MC) ** 2 + (Y - MC) ** 2))  # 칸 가장자리는 0
    core1 = pad.down(core * win)
    g1 = pad.down(glow * win)
    out = screen(g1 * 0.92, gblur(g1, 1.6) * 0.35, gblur(g1, 5.0) * 0.22, gblur(core1, 1.5) * 0.3, gblur(core1, 4.5) * 0.18)
    return clamp01(out), core1


@sprite("rfx_wmoon")
def make_moon():
    glows, cores = [], []
    for f in range(16):
        g, c = moon_frame(f)
        glows.append(g)
        cores.append(c)
    return Sprite("rfx_wmoon", "rotate_xyz", MS, MS, 16, "guardian", glows, cores, cols=8, zoom=6)


# ============================================================ 확인용 그림

def comp(glow, core, ca, cb, bg):
    g = edge_fade(glow)[..., None]
    c = edge_fade(core)[..., None]
    out = np.broadcast_to(bg, glow.shape + (3,)).astype(np.float32) * (1 - g) + np.array(ca, np.float32) * g
    out = out * (1 - c) + np.array(cb, np.float32) * c
    return Image.fromarray((clamp01(out) * 255 + 0.5).astype(np.uint8), "RGB")


def preview(sp, folder, palettes):
    gap = 6
    cw, ch = sp.cw, sp.ch
    n = sp.frames
    per = min(n, max(1, 1600 // (cw + gap)))
    rows = (n + per - 1) // per
    zoom_k = 2
    blocks = []
    for pname, (ca, cb) in palettes:
        for bname, bg in (("dark", BG), ("sky", SKY)):
            W = per * (cw + gap) + gap + cw * zoom_k + gap
            H = max(rows * (ch + gap + 12) + gap, ch * zoom_k + 18)
            im = Image.new("RGB", (W, H), tuple(int(x * 255) for x in bg))
            for i in range(n):
                r_, k_ = divmod(i, per)
                x, y = gap + k_ * (cw + gap), gap + r_ * (ch + gap + 12) + 12
                im.paste(comp(sp.glows[i], sp.cores[i], ca, cb, bg), (x, y))
                label(im, str(i), (x + 2, y - 11))
            z = comp(sp.glows[sp.zoom], sp.cores[sp.zoom], ca, cb, bg).resize((cw * zoom_k, ch * zoom_k), Image.BICUBIC)
            im.paste(z, (per * (cw + gap) + gap, 14))
            label(im, f"{sp.name} {pname} {bname} - frame {sp.zoom} x{zoom_k}", (per * (cw + gap) + gap, 1))
            blocks.append(im)
    W = max(b.size[0] for b in blocks)
    H = sum(b.size[1] for b in blocks)
    out = Image.new("RGB", (W, H), (0, 0, 0))
    y = 0
    for b in blocks:
        out.paste(b, (0, y))
        y += b.size[1]
    path = os.path.join(folder, sp.name + ".png")
    out.save(path)
    return path


PALETTES = {"rfx_wserpent": [("gold", GOLD), ("silver", SILVER)], "rfx_wmoon": [("moon", MOON)]}


def main():
    args = sys.argv[1:]
    only = None
    folder = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
    for name, fn in SPRITES.items():
        if only and name not in only:
            continue
        sp = fn()
        if "--no-save" not in args:
            save_sprite(sp)
        if folder:
            print("  preview:", preview(sp, folder, PALETTES.get(name, [("gold", GOLD)])))


if __name__ == "__main__":
    main()
