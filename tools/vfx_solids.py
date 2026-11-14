#!/usr/bin/env python3
"""2026-11-14 모드 없는 클라이언트: 입체 효과 모델 (Fx3d — 모드 FxModels 의 면 모델: 무기 스킬의 까마귀 · 오딘의 눈 · 작은 태양 · 일식 · 세계수 · 불꽃 날개 ·
시계 · 뱀 … · 네크로맨서 나글파르) 을 자바 아이템 모델로 옮김.

- 부분 (part) 마다 아이템 모델 하나: 면 (사각형) 하나 = 두께 없는 상자 하나 (앞 south · 뒤 north 면 — 모드처럼 두 면 다 보임),
  상자를 그 면의 자리 · 방향에 세 축 회전 (26.x 모델 상자 rotation 의 x · y · z — 25w46a 부터, 각도 제한 없음 · X → Y → Z 차례).
  면의 가로 = 그림 u 가 커지는 쪽, 세로 = 그림 v 가 작아지는 쪽 (자바 south 면의 uv 방향), 크기 = 그 면의 uv 범위를 덮는 길이 (사다리꼴은 평균 — 모드는 꼭짓점 그대로).
- 재질 색 (모드 FxModels): 0 빛깔 · 1 속빛 · 2 흰색 · 3 짙은 빛깔 (빛깔 × 0.45) · 4 ~ 8 고정 색 · 9 짙은 속빛 (속빛 × 0.5) →
  물들임 (아이템 정의 tints — custom_model_data 색 0 = 빛깔 · 1 = 속빛, 서버가 띄울 때 넣음) + 그림 갈래 (짙게 · 고정 색을 미리 곱한 그림을 아래로 이어 붙임).
- 스스로 빛나는 모델 ("emissive" — 빛기둥 · 불꽃 날개 · 해 …): 면 그늘 없음 (shade false) · 빛 15. 그 밖은 엔티티 빛 (자바 아이템 그늘 — 모드와 같은 두 빛).
- 모델 칸: 부분의 가운데 c (모델 칸 · 블록) 가 (8, 8, 8), 1 블록 = k 칸 (상자가 −16 ~ 32 안에 들게 — 큰 부분은 k < 16, 서버가 16 / k 배로 키움).
- 서버 색인 mmorpg/vfx_solids.json: 모델마다 부분 (아이템 · c · k · 쥔 점 pivot) · 움직임 (tracks 그대로) · 빛 무리 (glow) · emissive · upright.
  서버 (vfx.PBridge.Solid) 가 부분마다 아이템 표시 하나를 띄워 모드 FxModels 와 같은 셈 (자리 · 방향 · 크기 · 늘임 · 쥔 곳 · 부분 움직임) 으로 옮김.

  python3 tools/vfx_solids.py                          # 씀 (assets/rpg/models/item/fx3d · textures/item/fx3d · items/fx3d · mmorpg/vfx_solids.json)
  python3 tools/vfx_solids.py --preview <폴더> [모델 …]  # 원래 면 모델 (위) / 옮긴 자바 모델 (아래) 을 네 방향에서 그려 견줌
"""
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fx_models as fx  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
MODEL_DIR = os.path.join(RES, "assets/mmorpg/fxmodel")
OUT_MODELS = os.path.join(RES, "assets/rpg/models/item/fx3d")
OUT_TEX = os.path.join(RES, "assets/rpg/textures/item/fx3d")
OUT_ITEMS = os.path.join(RES, "assets/rpg/items/fx3d")
OUT_INDEX = os.path.join(RES, "mmorpg/vfx_solids.json")

# 모드 FxModels.FIXED (재질 4 ~ 8)
FIXED = [(0.76, 0.79, 0.84), (1.0, 0.8, 0.36), (0.55, 0.38, 0.22), (0.92, 0.89, 0.8), (0.13, 0.1, 0.17)]
# 상자 자리 한계 (자바 모델 from · to) — 가장자리 여유 1칸
LIMIT_LO, LIMIT_HI = -15.0, 31.0
HALF = (LIMIT_HI - LIMIT_LO) / 2  # 23 — 가운데 8 에서 양쪽


def cards():
    """모드 없는 클라이언트가 이미 글꼴 카드로 보는 모델 (tools/vfx_particles.py CARDS · 구운 판)"""
    try:
        return set(json.load(open(os.path.join(RES, "mmorpg/vfx_particles.json")))["cards"])
    except (OSError, KeyError, ValueError):
        return set()


def targets():
    """옮길 모델: 면 모델 가운데 글꼴 카드가 없는 것 (참격 판 · 2D 물체 판 · 구운 판은 이미 카드)"""
    have = cards()
    out = []
    for f in sorted(os.listdir(MODEL_DIR)):
        if f.endswith(".json") and f[:-5] not in have:
            out.append(f[:-5])
    return out


def load(name):
    data = json.load(open(os.path.join(MODEL_DIR, name + ".json")))
    tex = Image.open(os.path.join(RES, "assets/mmorpg", data["tex"].split(":", 1)[1])).convert("RGBA")
    # 2D 판 모델 (만월 · 빛의 뱀 — 2048 칸 그림) 은 모드 없는 클라이언트 그림 판에 반 크기로 (아이템 그림 판이 큼 — uv 는 비율이라 그대로)
    while max(tex.size) > 1024:
        tex = tex.resize((tex.size[0] // 2, tex.size[1] // 2), Image.LANCZOS)
    return data, tex


# ------------------------------------------------------------------ 회전 (자바 모델 상자: X → Y → Z 차례 — R = Rz · Ry · Rx, 오른손 규칙)


def rot_x(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], float)


def rot_y(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], float)


def rot_z(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], float)


def euler_xyz(R):
    """R = Rz(z) · Ry(y) · Rx(x) 의 (x, y, z) 도"""
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(R[2, 1], R[2, 2])
        z = math.atan2(R[1, 0], R[0, 0])
    else:  # 짐벌 — x 를 0 으로
        x = 0.0
        z = math.atan2(-R[0, 1], R[1, 1])
    return math.degrees(x), math.degrees(y), math.degrees(z)


def euler_matrix(x, y, z):
    return rot_z(z) @ rot_y(y) @ rot_x(x)


# ------------------------------------------------------------------ 면 → 상자


def _span(poly, axis, at):
    """볼록 사각형 poly (2D 꼭짓점 넷) 를 axis (0 = 가로줄 y = at · 1 = 세로줄 x = at) 로 자른 범위 (lo, hi) 또는 None"""
    hits = []
    o = 1 - axis  # 자르는 줄이 정하는 좌표
    for i in range(4):
        a, b = poly[i], poly[(i + 1) % 4]
        da, db = a[o] - at, b[o] - at
        if abs(da) < 1e-12 and abs(db) < 1e-12:
            hits += [a[axis], b[axis]]
        elif (da <= 0 <= db) or (db <= 0 <= da):
            k = da / (da - db) if abs(da - db) > 1e-12 else 0.0
            hits.append(a[axis] + (b[axis] - a[axis]) * k)
    if len(hits) < 2:
        return None
    return min(hits), max(hits)


def _uv_at(L, UV, x, y):
    """사각형 안 (또는 가까이) 의 점 (x, y) 의 uv — 세모 둘 (0 1 2 · 0 2 3) 의 기울기 (바깥이면 가까운 세모로 늘림)"""
    best = None
    for a, b, d in ((0, 1, 2), (0, 2, 3)):
        (x0, y0), (x1, y1), (x2, y2) = L[a], L[b], L[d]
        det = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(det) < 1e-14:
            continue
        w1 = ((x - x0) * (y2 - y0) - (x2 - x0) * (y - y0)) / det
        w2 = ((x1 - x0) * (y - y0) - (x - x0) * (y1 - y0)) / det
        w0 = 1 - w1 - w2
        out = -min(0.0, w0, w1, w2)  # 세모 밖으로 얼마나
        uv = (w0 * UV[a][0] + w1 * UV[b][0] + w2 * UV[d][0], w0 * UV[a][1] + w1 * UV[b][1] + w2 * UV[d][1])
        if best is None or out < best[0]:
            best = (out, uv)
    return best[1] if best else UV[0]


def quad_rects(q, tex=None):
    """면 하나 → 상자 판 목록 [(가운데, X (u 증가), Y (그림 위 — v 감소), Z (법선), 너비, 높이, u0, u1, v0, v1)].
    판의 틀은 그림의 기울기 (dP/du · −dP/dv). 사다리꼴 · 세모 (끝이 모인 면) 은 좁아지는 쪽으로 띠 2 ~ 4 개로 나눔 — 띠마다 그 가운데 줄의 너비
    (사각형 하나로 덮으면 넓은 끝만큼 바깥으로 삐져나옴 — 미리보기: 나글파르 뱃머리 · 까마귀 날개 뿌리)"""
    P = [np.array(q[i * 8:i * 8 + 3], float) for i in range(4)]
    UV = [(float(q[i * 8 + 3]), float(q[i * 8 + 4])) for i in range(4)]
    c0 = sum(P) / 4
    T = np.zeros(3)
    B = np.zeros(3)
    nsum = np.zeros(3)
    for a, b, d in ((0, 1, 2), (0, 2, 3)):
        e1, e2 = P[b] - P[a], P[d] - P[a]
        du1, dv1 = UV[b][0] - UV[a][0], UV[b][1] - UV[a][1]
        du2, dv2 = UV[d][0] - UV[a][0], UV[d][1] - UV[a][1]
        det = du1 * dv2 - du2 * dv1
        nsum += np.cross(e1, e2)
        if abs(det) < 1e-12:
            continue
        T += (e1 * dv2 - e2 * dv1) / det
        B += (e2 * du1 - e1 * du2) / det
    if np.linalg.norm(nsum) / 2 < 1e-9:
        return []
    if np.linalg.norm(T) < 1e-9 or np.linalg.norm(B) < 1e-9:
        X = fx.unit(P[1] - P[0])  # uv 가 한 점 (단색 면) — 모서리로 틀
        Z = fx.unit(nsum)
        Y = fx.unit(np.cross(Z, X))
    else:
        X = fx.unit(T)
        Z = np.cross(X, -B)
        if np.linalg.norm(Z) < 1e-9:
            return []
        Z = fx.unit(Z)
        Y = fx.unit(np.cross(Z, X))
    L = [(float(np.dot(p - c0, X)), float(np.dot(p - c0, Y))) for p in P]
    xs, ys = [l[0] for l in L], [l[1] for l in L]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    if x1 - x0 < 1e-9 or y1 - y0 < 1e-9:
        return []
    # 좁아지는 쪽: 위 · 아래 끝의 너비 차 (세로로 띠) 와 양 끝의 높이 차 (가로로 띠) 중 큰 쪽
    eps_y, eps_x = (y1 - y0) * 0.02, (x1 - x0) * 0.02
    wb, wt = _span(L, 0, y0 + eps_y), _span(L, 0, y1 - eps_y)
    hl, hr = _span(L, 1, x0 + eps_x), _span(L, 1, x1 - eps_x)
    tw = 1.0 if not (wb and wt) else min(wb[1] - wb[0], wt[1] - wt[0]) / max(1e-9, max(wb[1] - wb[0], wt[1] - wt[0]))
    th = 1.0 if not (hl and hr) else min(hl[1] - hl[0], hr[1] - hr[0]) / max(1e-9, max(hl[1] - hl[0], hr[1] - hr[0]))
    split_y = tw <= th  # 너비가 위아래로 달라짐 → 가로 띠 (y 로 나눔)
    r = min(tw, th)
    if r <= 0.85 and tex is not None:
        # 오려 낸 그림 (잎 · 깃털 · 불꽃 — 그림 칸의 가장자리가 비었음): 띠로 나누지 않고 사각형 하나 (모양은 그림의 투명이 만듦 — 띠로 나누면 띠마다
        # 그림이 잘려 판처럼 보였음, 미리보기: 실프 날개)
        th_, tw_ = tex.shape[:2]
        us = [UV[i][0] for i in range(4)]
        vs = [UV[i][1] for i in range(4)]
        a0, a1 = max(0, int(min(us) * tw_)), min(tw_, max(int(min(us) * tw_) + 1, int(math.ceil(max(us) * tw_))))
        b0, b1 = max(0, int(min(vs) * th_)), min(th_, max(int(min(vs) * th_) + 1, int(math.ceil(max(vs) * th_))))
        reg = tex[b0:b1, a0:a1, 3]
        if reg.size and reg.shape[0] >= 3 and reg.shape[1] >= 3:
            edge = np.concatenate([reg[0, :], reg[-1, :], reg[:, 0], reg[:, -1]])
            if float(edge.mean()) < 0.12:
                ul, _ = _uv_at(L, UV, x0, (y0 + y1) / 2)
                ur, _ = _uv_at(L, UV, x1, (y0 + y1) / 2)
                _, vt = _uv_at(L, UV, (x0 + x1) / 2, y1)
                _, vb = _uv_at(L, UV, (x0 + x1) / 2, y0)
                return [(c0 + X * (x0 + x1) / 2 + Y * (y0 + y1) / 2, X, Y, Z, x1 - x0, y1 - y0, min(us), max(us), min(vs), max(vs))]
    n = 1 if r > 0.85 else 2 if r > 0.6 else 3 if r > 0.4 else 4 if r > 0.2 else 6
    # 띠 사이 · 계단 끝의 틈이 보이지 않게 조금 겹침 (나눈 쪽 2% · 너비 4% — 미리보기: 시계 판의 부채꼴 세모 사이 틈)
    grow_w, grow_s = (1.04, 1.02) if n > 1 else (1.0, 1.0)
    if tex is not None and n > 1:
        # 비치는 그림 (빛 · 연기 — 알파 평균 0.9 아래) 은 겹치면 겹친 줄이 짙게 보임 (미리보기: 일식 원판 · 달의 줄무늬) → 겹치지 않음
        th, tw = tex.shape[:2]
        us = [UV[i][0] for i in range(4)]
        vs = [UV[i][1] for i in range(4)]
        a = tex[max(0, int(min(vs) * th)):max(1, int(math.ceil(max(vs) * th))), max(0, int(min(us) * tw)):max(1, int(math.ceil(max(us) * tw))), 3]
        if a.size and float(a.mean()) < 0.9:
            grow_w, grow_s = 1.0, 1.0
    out = []
    for j in range(n):
        if split_y:
            ya, yb = y0 + (y1 - y0) * j / n, y0 + (y1 - y0) * (j + 1) / n
            sp = _span(L, 0, (ya + yb) / 2)
            if not sp:
                continue
            xa, xb = sp
        else:
            xa, xb = x0 + (x1 - x0) * j / n, x0 + (x1 - x0) * (j + 1) / n
            sp = _span(L, 1, (xa + xb) / 2)
            if not sp:
                continue
            ya, yb = sp
        if xb - xa < 1e-9 or yb - ya < 1e-9:
            continue
        xm, ym = (xa + xb) / 2, (ya + yb) / 2
        ul, _ = _uv_at(L, UV, xa, ym)
        ur, _ = _uv_at(L, UV, xb, ym)
        _, vt = _uv_at(L, UV, xm, yb)  # 위 (y 큼) = 그림 위 (v 작음)
        _, vb = _uv_at(L, UV, xm, ya)
        c = c0 + X * xm + Y * ym
        w, h = xb - xa, yb - ya
        if split_y:
            w, h = w * grow_w, h * grow_s
        else:
            w, h = w * grow_s, h * grow_w
        out.append((c, X, Y, Z, w, h, ul, ur, vt, vb))
    return out


def _affine(q):
    """면 하나의 평면 · uv 기울기: (법선 n, 평면 거리 d, T = dP/du, B = dP/dv, 첫 꼭짓점 p0 · uv0) — 넷째 꼭짓점이 같은 기울기에 맞지 않으면 None"""
    P = [np.array(q[i * 8:i * 8 + 3], float) for i in range(4)]
    UV = [np.array([q[i * 8 + 3], q[i * 8 + 4]], float) for i in range(4)]
    best = None
    for a, b, d in ((0, 1, 2), (0, 2, 3), (0, 1, 3)):
        e1, e2 = P[b] - P[a], P[d] - P[a]
        n = np.cross(e1, e2)
        if np.linalg.norm(n) < 1e-10:
            continue
        du1, dv1 = UV[b] - UV[a]
        du2, dv2 = UV[d] - UV[a]
        det = du1 * dv2 - du2 * dv1
        if abs(det) < 1e-12:
            continue
        T = (e1 * dv2 - e2 * dv1) / det
        B = (e2 * du1 - e1 * du2) / det
        best = (fx.unit(n), P[a], UV[a], T, B)
        break
    if best is None:
        return None
    n, p0, uv0, T, B = best
    # 모든 꼭짓점이 같은 평면 · 같은 uv 기울기 (P = p0 + T (u − u0) + B (v − v0))
    for i in range(4):
        du, dv = UV[i] - uv0
        if np.linalg.norm(p0 + T * du + B * dv - P[i]) > 1e-4 * (1 + np.linalg.norm(P[i])):
            return None
    return n, float(np.dot(n, p0)), T, B, p0, uv0


def planar_groups(quads, tex):
    """한 평면 · 한 uv 기울기 · 한 재질의 면 셋 이상 (부채꼴 원판 — 시계 판 · 일식 원판 …) 을 사각형 하나로:
    {면 번호: 무리 번호}, [무리 사각형]. 사각형 = 무리의 꼭짓점 범위 (그림 칸도 같은 기울기로) — 범위 안 · 무리 밖의 그림이 비어 있을 때만
    (그림 칸에 다른 것이 그려져 있으면 띠로)"""
    th, tw = tex.shape[:2]
    # 무리 짓기: 같은 재질 · 평면 (법선 · 거리) · uv 기울기 (T · B — 꼭짓점 uv 가 소수 셋째 자리로 반올림돼 면마다 1 ~ 2% 다름) · uv 0 의 자리 (p0 − T u0 − B v0)
    clusters = []  # [(재질, n, d, T, B, o, [면 번호])]
    for i, q in enumerate(quads):
        a = _affine(q)
        if a is None:
            continue
        n, d, T, B, p0, uv0 = a
        o = p0 - T * uv0[0] - B * uv0[1]
        m = int(q[32])
        for cl in clusters:
            if cl[0] != m or np.linalg.norm(cl[1] - n) > 0.02 or abs(cl[2] - d) > 0.01:
                continue
            if np.linalg.norm(cl[3] - T) > 0.04 * np.linalg.norm(cl[3]) or np.linalg.norm(cl[4] - B) > 0.04 * np.linalg.norm(cl[4]):
                continue
            if np.linalg.norm(cl[5] - o) > 0.04 * max(np.linalg.norm(cl[3]), np.linalg.norm(cl[4])):
                continue
            cl[6].append(i)
            break
        else:
            clusters.append((m, n, d, T, B, o, [i]))
    keyed = {k: cl[6] for k, cl in enumerate(clusters)}
    member = {}
    rects = []
    for key, idx in keyed.items():
        if len(idx) < 3:
            continue
        q0 = quads[idx[0]]
        n, d, T, B, p0, uv0 = _affine(q0)
        if abs(float(np.dot(fx.unit(T), fx.unit(B)))) > 0.05:
            continue  # 비뚤어진 uv (사각형 하나로는 그림이 늘어남)
        X = fx.unit(T)
        Z = np.cross(X, -B)
        if np.linalg.norm(Z) < 1e-9:
            continue
        Z = fx.unit(Z)
        Y = fx.unit(np.cross(Z, X))
        pts = [np.array(quads[i][k * 8:k * 8 + 3], float) for i in idx for k in range(4)]
        uvs = [np.array([quads[i][k * 8 + 3], quads[i][k * 8 + 4]], float) for i in idx for k in range(4)]
        c0 = sum(pts) / len(pts)
        lx = [float(np.dot(p - c0, X)) for p in pts]
        ly = [float(np.dot(p - c0, Y)) for p in pts]
        x0, x1, y0, y1 = min(lx), max(lx), min(ly), max(ly)
        us = [uv[0] for uv in uvs]
        vs = [uv[1] for uv in uvs]
        u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
        # 그림 칸 안 · 무리 밖 (부채꼴 세모들이 덮지 않은 곳) 의 그림이 비었는지
        px0, px1 = max(0, int(math.floor(u0 * tw))), min(tw, int(math.ceil(u1 * tw)))
        py0, py1 = max(0, int(math.floor(v0 * th))), min(th, int(math.ceil(v1 * th)))
        if px1 <= px0 or py1 <= py0:
            continue
        yy, xx = np.mgrid[py0:py1, px0:px1]
        cu, cv = (xx + 0.5) / tw, (yy + 0.5) / th
        covered = np.zeros(cu.shape, bool)
        for i in idx:
            U = [(quads[i][k * 8 + 3], quads[i][k * 8 + 4]) for k in range(4)]
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                (ax, ay), (bx, by), (cx, cy) = U[a], U[b], U[c]
                det = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
                if abs(det) < 1e-14:
                    continue
                w1 = ((cu - ax) * (cy - ay) - (cx - ax) * (cv - ay)) / det
                w2 = ((bx - ax) * (cv - ay) - (cu - ax) * (by - ay)) / det
                covered |= (w1 >= -0.06) & (w2 >= -0.06) & (w1 + w2 <= 1.06)  # 무리 둘레를 조금 넓힘 (그려진 원이 다각형보다 조금 큼)
        alpha = tex[py0:py1, px0:px1, 3]
        stray = float(((alpha > 0.1) & ~covered).sum()) / max(1, alpha.size)
        if stray <= 0.01:
            # 사각형 하나 (꼭짓점의 uv — 같은 기울기: 왼쪽 = u 작음, 위 = v 작음)
            rects.append(("rects", [(c0 + X * (x0 + x1) / 2 + Y * (y0 + y1) / 2, X, Y, Z, x1 - x0, y1 - y0, u0, u1, v0, v1)]))
        else:
            # 그림 칸에 무리 밖의 그림이 있음 (원판을 채운 그림 — 일식 원판): 무리 전체를 판 하나로 구움 (무리 밖은 투명 — 세모마다 띠로 나누면 띠 끝이
            # 동심원 계단 줄로 보였음)
            tris = []
            for i in idx:
                L = [(float(np.dot(np.array(quads[i][k * 8:k * 8 + 3], float) - c0, X)), float(np.dot(np.array(quads[i][k * 8:k * 8 + 3], float) - c0, Y)))
                     for k in range(4)]
                UVq = [(float(quads[i][k * 8 + 3]), float(quads[i][k * 8 + 4])) for k in range(4)]
                tris += [((L[0], L[1], L[2]), (UVq[0], UVq[1], UVq[2])), ((L[0], L[2], L[3]), (UVq[0], UVq[2], UVq[3]))]
            density = max(tw / max(1e-9, np.linalg.norm(T)), th / max(1e-9, np.linalg.norm(B)))
            rects.append(("bake", tris, (c0, X, Y, Z, x0, x1, y0, y1), density))
        for i in idx:
            member[i] = len(rects) - 1
    return member, rects


def material_groups(data):
    """그림 갈래: 쓰인 재질마다 (갈래 번호, 물들임 번호 또는 None). 갈래 0 = 그림 그대로 (재질 0 · 1 · 2)"""
    used = sorted({int(q[32]) for p in data["parts"] for q in p["q"]})
    var = {}
    order = [None]  # 갈래 0
    for m in used:
        m = max(0, min(9, m))
        if m in (0, 1, 2):
            var[m] = (0, 0 if m == 0 else 1 if m == 1 else None)
            continue
        key = ("dark45" if m == 3 else "dark50" if m == 9 else "fixed%d" % m)
        if key not in order:
            order.append(key)
        var[m] = (order.index(key), 0 if m == 3 else 1 if m == 9 else None)
    return var, order


def variant_image(tex, key):
    a = np.asarray(tex, dtype=float) / 255.0
    if key is None:
        return tex
    if key == "dark45":
        k = np.array([0.45, 0.45, 0.45])
    elif key == "dark50":
        k = np.array([0.5, 0.5, 0.5])
    else:
        k = np.array(FIXED[int(key[5:]) - 4])
    b = a.copy()
    b[..., :3] *= k
    return Image.fromarray((np.clip(b, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def atlas(tex, order):
    """갈래 그림을 아래로 이어 붙인 그림"""
    W, H = tex.size
    img = Image.new("RGBA", (W, H * len(order)), (0, 0, 0, 0))
    for i, key in enumerate(order):
        img.paste(variant_image(tex, key), (0, H * i))
    return img


def part_scale(frames, centre):
    """1 블록 = k 칸: 모든 상자 (회전 전 from · to) 가 −15 ~ 31 안에 들게, 16 을 넘지 않게"""
    k = 16.0
    for f in frames:
        c, X, Y, Z, w, h = f[:6]
        off = np.abs(c - centre).max()
        need = off + max(w, h) / 2
        if need > 1e-9:
            k = min(k, HALF / need)
    return k


def r4(x):
    return round(float(x), 3)


MAT_FACTOR = {3: (0.45, 0.45, 0.45), 9: (0.5, 0.5, 0.5)}


def mat_factor(m):
    """구울 때 그림에 곱하는 재질 몫 (물들임 0 · 1 은 서버 색이 곱함)"""
    if m in MAT_FACTOR:
        return np.array(MAT_FACTOR[m])
    if 4 <= m <= 8:
        return np.array(FIXED[m - 4])
    return None


def _bake(tris_uv, frame, tex, mat, density):
    """세모들 (지역 2D 꼭짓점 셋 · uv 셋) 을 덮는 판 그림 (가장자리 바깥은 투명): (그림 RGBA uint8, 틀 (가운데 · X · Y · Z · w · h))"""
    c0, X, Y, Z, x0, x1, y0, y1 = frame
    w, h = x1 - x0, y1 - y0
    pw = int(max(2, min(96, math.ceil(w * density))))
    ph = int(max(2, min(96, math.ceil(h * density))))
    th, tw = tex.shape[:2]
    xs = x0 + (np.arange(pw) + 0.5) / pw * w
    ys = y1 - (np.arange(ph) + 0.5) / ph * h  # 위 줄 = y 큼
    gx, gy = np.meshgrid(xs, ys)
    best = np.full(gx.shape, -1e9)
    U = np.zeros(gx.shape)
    V = np.zeros(gx.shape)
    pix = max(w / pw, h / ph)
    for (a, b, c), (ua, ub, uc) in tris_uv:
        (ax, ay), (bx, by), (cx, cy) = a, b, c
        det = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if abs(det) < 1e-14:
            continue
        w1 = ((gx - ax) * (cy - ay) - (cx - ax) * (gy - ay)) / det
        w2 = ((bx - ax) * (gy - ay) - (gx - ax) * (by - ay)) / det
        w0 = 1 - w1 - w2
        # 세모 안쪽 정도 (꼭짓점에서 맞은편 변까지의 높이로 나눈 몫 → 칸 단위 거리)
        e = np.stack([w0, w1, w2])
        lens = [abs(det) / max(1e-12, math.hypot(cx - bx, cy - by)), abs(det) / max(1e-12, math.hypot(ax - cx, ay - cy)),
                abs(det) / max(1e-12, math.hypot(bx - ax, by - ay))]
        d = np.min(e * np.array(lens)[:, None, None], axis=0)  # 가장 가까운 변까지 거리 (안 +)
        take = d > best
        best = np.where(take, d, best)
        U = np.where(take, w0 * ua[0] + w1 * ub[0] + w2 * uc[0], U)
        V = np.where(take, w0 * ua[1] + w1 * ub[1] + w2 * uc[1], V)
    inside = best > -0.6 * pix  # 칸 반쯤 넓혀 이웃 면과 틈이 없게
    tu = np.clip((U * tw).astype(int), 0, tw - 1)
    tv = np.clip((V * th).astype(int), 0, th - 1)
    col = tex[tv, tu].copy()
    f = mat_factor(mat)
    if f is not None:
        col[..., :3] *= f
    col[..., 3] = np.where(inside, col[..., 3], 0.0)
    img = (np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8)
    return img, (c0 + X * (x0 + x1) / 2 + Y * (y0 + y1) / 2, X, Y, Z, w, h)


def bake_quad(q, tex):
    """사다리꼴 · 세모 · 비뚤어진 uv 의 면 하나를 제 모양 그대로 판 하나로 (그 면의 모서리 틀 — 그림을 다시 그림)"""
    P = [np.array(q[i * 8:i * 8 + 3], float) for i in range(4)]
    UV = [(float(q[i * 8 + 3]), float(q[i * 8 + 4])) for i in range(4)]
    nsum = np.cross(P[1] - P[0], P[2] - P[0]) + np.cross(P[2] - P[0], P[3] - P[0])
    if np.linalg.norm(nsum) < 1e-12:
        return None
    Z = fx.unit(nsum)
    e = P[1] - P[0] if np.linalg.norm(P[1] - P[0]) > 1e-9 else P[2] - P[1]
    X = fx.unit(e - Z * np.dot(e, Z))
    Y = fx.unit(np.cross(Z, X))
    c0 = sum(P) / 4
    L = [(float(np.dot(p - c0, X)), float(np.dot(p - c0, Y))) for p in P]
    xs, ys = [l[0] for l in L], [l[1] for l in L]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    if x1 - x0 < 1e-9 or y1 - y0 < 1e-9:
        return None
    th, tw = tex.shape[:2]
    area = np.linalg.norm(nsum) / 2
    uarea = abs((UV[1][0] - UV[0][0]) * (UV[2][1] - UV[0][1]) - (UV[2][0] - UV[0][0]) * (UV[1][1] - UV[0][1])) / 2
    uarea += abs((UV[2][0] - UV[0][0]) * (UV[3][1] - UV[0][1]) - (UV[3][0] - UV[0][0]) * (UV[2][1] - UV[0][1])) / 2
    density = math.sqrt(max(1e-12, uarea * tw * th) / max(1e-12, area))  # 원래 그림의 칸 / 블록
    density = max(density, 6.0 / max(x1 - x0, y1 - y0))
    tris = [((L[0], L[1], L[2]), (UV[0], UV[1], UV[2])), ((L[0], L[2], L[3]), (UV[0], UV[2], UV[3]))]
    return tris, (c0, X, Y, Z, x0, x1, y0, y1), density


def pack(patches, width):
    """판 그림들을 width 너비의 줄에 쌓음 (가장자리 1칸 늘림 — 밉맵 번짐): [(x, y)], 높이"""
    out = []
    x = y = rowh = 0
    for img in patches:
        h, w = img.shape[:2]
        w2, h2 = w + 2, h + 2
        if x + w2 > width:
            x, y, rowh = 0, y + rowh, 0
        out.append((x + 1, y + 1))
        x += w2
        rowh = max(rowh, h2)
    return out, y + rowh


def convert(name):
    """모델 하나 → (부분 모델 {부분 이름: 모델 JSON}, 그림, 색인 항목)"""
    data, tex = load(name)
    var, order = material_groups(data)
    texf = np.asarray(tex, dtype=float) / 255.0
    emissive = bool(data.get("emissive"))
    patches = []  # 구운 판 그림 (RGBA uint8)
    parts_frames = []
    for pi, p in enumerate(data["parts"]):
        frames = []  # (가운데, X, Y, Z, w, h, ("tex", u0, u1, v0, v1) 또는 ("patch", 번호), 재질)
        member, rects = planar_groups(p["q"], texf)
        done = set()
        for qi, q in enumerate(p["q"]):
            m = max(0, min(9, int(q[32])))
            g = member.get(qi)
            if g is not None:
                if g not in done:
                    done.add(g)
                    grp = rects[g]
                    if grp[0] == "bake":
                        img, fr = _bake(grp[1], grp[2], texf, m, grp[3])
                        patches.append(img)
                        frames.append((*fr, ("patch", len(patches) - 1), m))
                    else:
                        for r in grp[1]:
                            frames.append((*r[:6], ("tex",) + tuple(r[6:10]), m))
                continue
            rs = quad_rects(q, texf)
            if len(rs) == 1:
                frames.append((*rs[0][:6], ("tex",) + tuple(rs[0][6:10]), m))
                continue
            b = bake_quad(q, texf)
            if b is None:
                continue
            img, fr = _bake(b[0], b[1], texf, m, b[2])
            patches.append(img)
            frames.append((*fr, ("patch", len(patches) - 1), m))
        parts_frames.append(frames)
    # 그림: 갈래 (그림 그대로 · 짙게 · 고정 색) 를 아래로 + 구운 판들
    base = atlas(tex, order)
    W = max(base.size[0], 256 if patches else base.size[0])
    pos, ph = pack(patches, W)
    H = base.size[1] + ph
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img.paste(base, (0, 0))
    canvas = np.asarray(img).copy()
    for (x, y), pimg in zip(pos, patches):
        yy = base.size[1] + y
        h, w = pimg.shape[:2]
        canvas[yy:yy + h, x:x + w] = pimg
        # 가장자리 한 칸 늘림
        canvas[yy - 1, x:x + w] = pimg[0]
        canvas[yy + h, x:x + w] = pimg[-1]
        canvas[yy - 1:yy + h + 1, x - 1] = canvas[yy - 1:yy + h + 1, x]
        canvas[yy - 1:yy + h + 1, x + w] = canvas[yy - 1:yy + h + 1, x + w - 1]
    img = Image.fromarray(canvas, "RGBA")
    bw, bh = base.size
    nvar = len(order)
    models = {}
    parts_ix = []
    for pi, p in enumerate(data["parts"]):
        frames = parts_frames[pi]
        if not frames:
            parts_ix.append({"name": p["name"], "pivot": [r4(x) for x in p["pivot"]]})
            continue
        pts = np.array([np.array(q[i * 8:i * 8 + 3], float) for q in p["q"] for i in range(4)])
        centre = (pts.min(0) + pts.max(0)) / 2
        k = part_scale(frames, centre)
        els = []
        for c, X, Y, Z, w, h, uvs, mat in frames:
            vi, tint = var[mat]
            cm = (c - centre) * k + 8.0
            hw, hh = w * k / 2, h * k / 2
            ex, ey, ez = euler_xyz(np.column_stack([X, Y, Z]))
            if uvs[0] == "tex":
                u0, u1, v0, v1 = (min(1.0, max(0.0, x)) for x in uvs[1:])
                U0, U1 = u0 * bw / W * 16, u1 * bw / W * 16
                hv = bh / nvar
                V0, V1 = (v0 * hv + vi * hv) / H * 16, (v1 * hv + vi * hv) / H * 16
            else:
                (x, y), pimg = pos[uvs[1]], patches[uvs[1]]
                ph_, pw_ = pimg.shape[:2]
                U0, U1 = x / W * 16, (x + pw_) / W * 16
                V0, V1 = (bh + y) / H * 16, (bh + y + ph_) / H * 16
            south = {"uv": [r4(U0), r4(V0), r4(U1), r4(V1)], "texture": "#0"}
            north = {"uv": [r4(U1), r4(V0), r4(U0), r4(V1)], "texture": "#0"}  # 뒤에서 보면 좌우가 뒤집힌 그림 (모드의 두 면 사각형과 같게)
            if tint is not None:
                south["tintindex"] = tint
                north["tintindex"] = tint
            el = {
                "from": [r4(cm[0] - hw), r4(cm[1] - hh), r4(cm[2])],
                "to": [r4(cm[0] + hw), r4(cm[1] + hh), r4(cm[2])],
                "rotation": {"origin": [r4(cm[0]), r4(cm[1]), r4(cm[2])], "x": r4(ex), "y": r4(ey), "z": r4(ez)},
                "faces": {"south": south, "north": north},
            }
            if emissive:
                el["shade"] = False
                el["light_emission"] = 15
            els.append(el)
        key = "%s_%d" % (name, pi)
        models[key] = {
            "credit": "tools/vfx_solids.py — 모드 효과 모델 %s 의 부분 %s (면 %d)" % (name, p["name"], len(els)),
            "textures": {"0": "rpg:item/fx3d/" + name, "particle": "rpg:item/fx3d/" + name},
            "elements": els,
        }
        parts_ix.append({"name": p["name"], "item": "rpg:fx3d/" + key, "c": [r4(x) for x in centre], "k": r4(k), "pivot": [r4(x) for x in p["pivot"]],
                         "quads": len(els)})
    entry = {"parts": parts_ix, "emissive": emissive, "upright": bool(data.get("upright"))}
    if data.get("glow"):
        entry["glow"] = [[r4(x) for x in g] for g in data["glow"]]
    if data.get("anims"):
        entry["anims"] = data["anims"]
    return models, img, entry


def write():
    names = targets()
    for d in (OUT_MODELS, OUT_TEX, OUT_ITEMS):
        if os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d)
    index = {}
    total = 0
    for name in names:
        models, img, entry = convert(name)
        img.save(os.path.join(OUT_TEX, name + ".png"))
        for key, m in models.items():
            json.dump(m, open(os.path.join(OUT_MODELS, key + ".json"), "w"), separators=(",", ":"))
            item = {"model": {"type": "minecraft:model", "model": "rpg:item/fx3d/" + key,
                              "tints": [{"type": "minecraft:custom_model_data", "index": i, "default": 16777215} for i in range(2)]}}
            json.dump(item, open(os.path.join(OUT_ITEMS, key + ".json"), "w"), indent=1)
            total += len(m["elements"])
        index[name] = entry
        print(name, "parts", len(models), "quads", sum(len(m["elements"]) for m in models.values()), "k", [p.get("k") for p in entry["parts"]])
    json.dump({"note": "tools/vfx_solids.py — 모드 없는 클라이언트의 입체 효과 모델 (부분마다 아이템 모델)", "models": index},
              open(OUT_INDEX, "w"), ensure_ascii=False, separators=(",", ":"))
    print("models", len(names), "elements", total, "index", os.path.getsize(OUT_INDEX))


# ------------------------------------------------------------------ 미리보기 (원래 면 모델 / 옮긴 자바 모델)


def element_quads(el, tw, th):
    """옮긴 상자 하나 → 앞 (south) 면의 꼭짓점 넷 · uv 넷 (세계 칸 = 모델 칸)"""
    f, t = np.array(el["from"], float), np.array(el["to"], float)
    r = el["rotation"]
    o = np.array(r["origin"], float)
    R = euler_matrix(r["x"], r["y"], r["z"])
    z = f[2]
    corners = [np.array([f[0], t[1], z]), np.array([t[0], t[1], z]), np.array([t[0], f[1], z]), np.array([f[0], f[1], z])]  # 왼 위 · 오른 위 · 오른 아래 · 왼 아래
    P = [o + R @ (cc - o) for cc in corners]
    u0, v0, u1, v1 = el["faces"]["south"]["uv"]
    UV = [(u0 / 16, v0 / 16), (u1 / 16, v0 / 16), (u1 / 16, v1 / 16), (u0 / 16, v1 / 16)]
    return P, UV


def raster(quads, tex, view, W=300, H=300, lo=None, hi=None):
    """면 목록 [(꼭짓점 넷, uv 넷, 색)] 을 view 방향에서 정사영 (그림 RGBA, 섞음 · 먼 것부터)"""
    th, tw = tex.shape[:2]
    D = fx.unit(np.asarray(view, float))
    right = np.cross(D, [0, 1, 0])
    if np.linalg.norm(right) < 1e-6:
        right = np.array([1.0, 0, 0])
    right = fx.unit(right)
    up = np.cross(right, D)
    pts = np.array([p for qd in quads for p in qd[0]])
    sx = pts @ right
    sy = pts @ up
    if lo is None:
        cx, cy = (sx.min() + sx.max()) / 2, (sy.min() + sy.max()) / 2
        half = max(sx.max() - sx.min(), sy.max() - sy.min()) / 2 * 1.1 + 1e-6
    else:
        cx, cy, half = lo
    rgb = np.zeros((H, W, 3))
    rgb[:] = (0.36, 0.38, 0.44)
    tris = []
    for P, UV, col in quads:
        sp = [((float(p @ right) - cx) / half * W / 2 + W / 2, H / 2 - (float(p @ up) - cy) / half * H / 2, -float(p @ D)) for p in P]
        for tri in ((0, 1, 2), (0, 2, 3)):
            tris.append((sum(sp[i][2] for i in tri) / 3, [sp[i] for i in tri], [UV[i] for i in tri], col))
    tris.sort(key=lambda t: -t[0])  # 먼 것부터 (깊이 = −D 쪽이 가까움)
    for _, ((x0, y0, _a), (x1, y1, _b), (x2, y2, _c)), ((u0, v0), (u1, v1), (u2, v2)), col in tris:
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        bx0, bx1 = max(0, int(math.floor(min(x0, x1, x2)))), min(W - 1, int(math.ceil(max(x0, x1, x2))))
        by0, by1 = max(0, int(math.floor(min(y0, y1, y2)))), min(H - 1, int(math.ceil(max(y0, y1, y2))))
        if bx1 < bx0 or by1 < by0:
            continue
        xs, ys = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
        w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
        w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        tu = np.clip(((w0 * u0 + w1 * u1 + w2 * u2) * tw).astype(int), 0, tw - 1)
        tv = np.clip(((w0 * v0 + w1 * v1 + w2 * v2) * th).astype(int), 0, th - 1)
        t = tex[tv, tu]
        a = t[..., 3] * inside
        a = np.where(a < 0.1, 0.0, a)
        c = np.clip(np.asarray(col)[None, None, :] * t[..., :3], 0, 1)
        r = rgb[by0:by1 + 1, bx0:bx1 + 1]
        r[:] = r * (1 - a[..., None]) + c * a[..., None]
    return Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB"), (cx, cy, half)


COLOR = (0.95, 0.55, 0.25)
GLOW = (1.0, 0.92, 0.6)


def mat_color(m):
    c, g = np.array(COLOR), np.array(GLOW)
    cols = [c, g, np.ones(3), c * 0.45] + [np.array(f) for f in FIXED] + [g * 0.5]
    return cols[max(0, min(9, m))]


def preview(out, names):
    os.makedirs(out, exist_ok=True)
    views = [(0, 0, -1), (-1, 0, 0), (-0.6, -0.45, -0.66), (0, -1, -0.0001)]
    for name in names:
        data, tex = load(name)
        texa = np.asarray(tex, dtype=float) / 255.0
        models, img, entry = convert(name)
        imga = np.asarray(img, dtype=float) / 255.0
        nvar = img.size[1] // tex.size[1]
        orig, conv = [], []
        for pi, p in enumerate(data["parts"]):
            for q in p["q"]:
                P = [np.array(q[i * 8:i * 8 + 3], float) for i in range(4)]
                UV = [(q[i * 8 + 3], q[i * 8 + 4]) for i in range(4)]
                orig.append((P, UV, mat_color(int(q[32]))))
            ix = entry["parts"][pi]
            if "item" not in ix:
                continue
            m = models["%s_%d" % (name, pi)]
            centre, k = np.array(ix["c"]), ix["k"]
            for el in m["elements"]:
                P, UV = element_quads(el, *img.size)
                P = [(pp - 8.0) / k + centre for pp in P]
                ti = el["faces"]["south"].get("tintindex")
                col = np.array(COLOR) if ti == 0 else np.array(GLOW) if ti == 1 else np.ones(3)
                conv.append((P, UV, col))
        row_o, row_c = [], []
        for v in views:
            a, frame = raster(orig, texa, v)
            b, _ = raster(conv, imga, v, lo=frame)
            row_o.append(a)
            row_c.append(b)
        W, H = row_o[0].size
        sheet = Image.new("RGB", (W * len(views), H * 2 + 4), (20, 20, 20))
        for i in range(len(views)):
            sheet.paste(row_o[i], (i * W, 0))
            sheet.paste(row_c[i], (i * W, H + 4))
        p = os.path.join(out, name + ".png")
        sheet.save(p)
        print(p, "variants", nvar, "parts", [(x["name"], x.get("quads", 0), x.get("k")) for x in entry["parts"]])


def main():
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        out = sys.argv[i + 1]
        names = sys.argv[i + 2:] or targets()
        preview(out, names)
        return 0
    write()
    return 0


if __name__ == "__main__":
    sys.exit(main())
