"""자바 아이템 모델 (elements · faces uv · rotation · light_emission) 을 정사영으로 그리기 — 광선 추적 (numpy).

weapon_pack_import.py (가방 아이콘) · java_fx_art.py (스킬 그림의 날아가는 무기) 가 씀. 게임과 같은 규칙:
- 면 UV: 자바 FaceBakery 와 같은 방향 (north: 가로 = 동 → 서, south · up · down: 서 → 동, west: 북 → 남, east: 남 → 북, 세로 = 위 → 아래 ·
  up: 북 → 남, down: 남 → 북), 면 rotation (0 · 90 · 180 · 270) 은 꼭짓점이 UV 모서리를 rotation / 90 칸 밀어 받음
- 상자 회전: 오른손 규칙 (축의 + 쪽에서 볼 때 반시계), rescale 이면 회전 뒤 다른 두 축을 1 / cos 배
- 뒷면은 그리지 않음 (자바처럼): 광선이 들어가는 면만 — 그 면이 없거나 픽셀이 투명하면 뒤가 보임, 반투명 픽셀은 앞에서부터 겹침
좌표: 모델 좌표 그대로 (0 ~ 16 = 한 블록, 텍스처 UV 0 ~ 16).
"""
import math

import numpy as np

AXIS = {"x": 0, "y": 1, "z": 2}
MIN_FACE = ("west", "down", "north")
MAX_FACE = ("east", "up", "south")
FACE_DIR = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def axis_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], float)
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], float)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], float)


class Element:
    def __init__(self, e):
        self.name = e.get("name", "")
        self.f = np.array([min(e["from"][i], e["to"][i]) for i in range(3)], float)
        self.t = np.array([max(e["from"][i], e["to"][i]) for i in range(3)], float)
        self.glow = int(e.get("light_emission", 0) or 0)
        self.faces = {}
        for name, fc in (e.get("faces") or {}).items():
            uv = fc.get("uv") or auto_uv(name, self.f, self.t)
            self.faces[name] = (tuple(float(x) for x in uv), int(fc.get("rotation", 0) or 0) % 360)
        r = e.get("rotation")
        self.R = np.eye(3)
        self.o = np.zeros(3)
        self.sc = np.ones(3)
        if r and float(r.get("angle", 0)) != 0:
            ang = float(r["angle"])
            self.R = axis_matrix(r["axis"], ang)
            self.o = np.array(r.get("origin", [8, 8, 8]), float)
            if r.get("rescale"):
                k = 1 / math.cos(math.radians(ang))
                self.sc = np.array([1.0 if i == AXIS[r["axis"]] else k for i in range(3)])

    def corners(self):
        """회전 · rescale 을 반영한 꼭짓점 8개 (모델 좌표)"""
        out = []
        for i in range(8):
            p = np.array([self.t[0] if i & 1 else self.f[0], self.t[1] if i & 2 else self.f[1], self.t[2] if i & 4 else self.f[2]])
            out.append(self.o + self.sc * (self.R @ (p - self.o)))
        return np.array(out)

    def center(self):
        c = (self.f + self.t) / 2
        return self.o + self.sc * (self.R @ (c - self.o))


def auto_uv(name, f, t):
    """uv 가 없는 면 — 자바처럼 자리에서 (16 - … 방향 포함)"""
    if name in ("north",):
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if name == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if name == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    if name == "east":
        return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]
    if name == "up":
        return [f[0], f[2], t[0], t[2]]
    return [f[0], 16 - t[2], t[0], 16 - f[2]]  # down


def face_ab(name, H, f, t):
    """면 위 점 H (상자 좌표) → 면에서 본 (a 가로 0~1 왼쪽 → 오른쪽, b 세로 0~1 위 → 아래)"""
    size = np.maximum(t - f, 1e-9)
    x = (H[:, 0] - f[0]) / size[0]
    y = (H[:, 1] - f[1]) / size[1]
    z = (H[:, 2] - f[2]) / size[2]
    if name == "north":
        return 1 - x, 1 - y
    if name == "south":
        return x, 1 - y
    if name == "west":
        return z, 1 - y
    if name == "east":
        return 1 - z, 1 - y
    if name == "up":
        return x, z
    return x, 1 - z  # down


def rotate_st(a, b, rot):
    """면 rotation → 텍스처 사각형 안의 (s, t)"""
    if rot == 90:
        return b, 1 - a
    if rot == 180:
        return 1 - a, 1 - b
    if rot == 270:
        return 1 - b, a
    return a, b


class View:
    """elements (자바 모델 elements) + 텍스처 한 장 (RGBA numpy, UV 0~16 이 그림 전체)"""

    def __init__(self, elements, tex):
        self.els = [Element(e) for e in elements]
        self.tex = np.asarray(tex, dtype=np.float64)
        self.th, self.tw = self.tex.shape[:2]

    def bounds(self):
        pts = np.concatenate([e.corners() for e in self.els])
        return pts.min(0), pts.max(0)

    def trace(self, P, D, with_element=False, chunk=65536):
        """광선 (P: N×3 시작, D: 3 방향 — 모두 같음) → (N×4 색 0~255 앞에서부터 겹친 것, N 빛 (빛나는 상자 비율), N 앞 거리)
        with_element 면 넷째로 N 앞 상자 번호 (알파 0.35 이상인 첫 상자, 없으면 -1). 광선이 많으면 chunk 개씩 (메모리)"""
        P = np.asarray(P, float)
        if len(P) > chunk:
            parts = [self.trace(P[i:i + chunk], D, with_element, chunk) for i in range(0, len(P), chunk)]
            return tuple(np.concatenate([p[j] for p in parts]) for j in range(len(parts[0])))
        n = len(P)
        D = np.asarray(D, float)
        # 맞은 것만 모음 (광선 번호 · 거리 · 색 · 빛 · 상자 번호)
        R_, T_, C_, G_, E_ = [], [], [], [], []
        for ei, el in enumerate(self.els):
            Rt = el.R.T
            lp = el.o + (Rt @ ((P - el.o) / el.sc).T).T
            ld = Rt @ (D / el.sc)
            tmin = np.full(n, -np.inf)
            tmax = np.full(n, np.inf)
            enter_axis = np.full(n, -1)
            enter_min = np.zeros(n, bool)
            ok = np.ones(n, bool)
            for i in range(3):
                if abs(ld[i]) < 1e-12:
                    ok &= (lp[:, i] >= el.f[i]) & (lp[:, i] <= el.t[i])
                    continue
                t1 = (el.f[i] - lp[:, i]) / ld[i]
                t2 = (el.t[i] - lp[:, i]) / ld[i]
                lo = np.minimum(t1, t2)
                hi = np.maximum(t1, t2)
                upd = lo > tmin
                tmin = np.where(upd, lo, tmin)
                enter_axis = np.where(upd, i, enter_axis)
                enter_min = np.where(upd, ld[i] > 0, enter_min)
                tmax = np.minimum(tmax, hi)
            ok &= (tmin <= tmax) & (tmax > 0) & (enter_axis >= 0)
            if not ok.any():
                continue
            hit = np.nonzero(ok)[0]
            H = lp[hit] + np.outer(tmin[hit], ld)
            ea, em = enter_axis[hit], enter_min[hit]
            for name, (uv, rot) in el.faces.items():
                ax, sign = FACE_DIR[name]
                m = (ea == ax) & (em == (sign < 0))
                if not m.any():
                    continue
                a, b = face_ab(name, H[m], el.f, el.t)
                s, t = rotate_st(np.clip(a, 0, 1), np.clip(b, 0, 1), rot)
                u = uv[0] + (uv[2] - uv[0]) * s
                v = uv[1] + (uv[3] - uv[1]) * t
                # 사각형 안의 픽셀만 (오른쪽 · 아래 끝은 그 앞 픽셀)
                u0, u1 = sorted((uv[0], uv[2]))
                v0, v1 = sorted((uv[1], uv[3]))
                u = np.maximum(np.minimum(u, u1 - 1e-6), u0)
                v = np.maximum(np.minimum(v, v1 - 1e-6), v0)
                px = np.clip(np.floor(u / 16 * self.tw).astype(int), 0, self.tw - 1)
                py = np.clip(np.floor(v / 16 * self.th).astype(int), 0, self.th - 1)
                rays = hit[m]
                R_.append(rays)
                T_.append(tmin[rays])
                C_.append(self.tex[py, px])
                G_.append(np.full(len(rays), el.glow / 15.0))
                E_.append(np.full(len(rays), ei))
        out = np.zeros((n, 3))
        glow = np.zeros(n)
        remain = np.ones(n)
        front = np.full(n, np.inf)
        front_el = np.full(n, -1)
        if R_:
            R = np.concatenate(R_)
            T = np.concatenate(T_)
            C = np.concatenate(C_).astype(np.float64)
            G = np.concatenate(G_)
            E = np.concatenate(E_)
            order = np.lexsort((T, R))  # 광선마다 가까운 것부터
            R, T, C, G, E = R[order], T[order], C[order], G[order], E[order]
            first = np.r_[True, R[1:] != R[:-1]]
            start = np.maximum.accumulate(np.where(first, np.arange(len(R)), 0))
            rank = np.arange(len(R)) - start
            for k in range(int(rank.max()) + 1):
                sel = rank == k
                r = R[sel]
                c = C[sel]
                a = c[:, 3] / 255.0
                w = remain[r] * a
                out[r] += w[:, None] * c[:, :3]
                glow[r] += w * G[sel]
                newf = (front[r] == np.inf) & (a > 0)
                front[r[newf]] = T[sel][newf]
                newe = (front_el[r] < 0) & (a >= 0.35)
                front_el[r[newe]] = E[sel][newe]
                remain[r] *= 1 - a
        alpha = 1 - remain
        col = np.where(alpha[:, None] > 1e-9, out / np.maximum(alpha, 1e-9)[:, None], 0)
        glow = np.where(alpha > 1e-9, glow / np.maximum(alpha, 1e-9), 0)
        res = (np.concatenate([col, (alpha * 255)[:, None]], axis=1), glow, front)
        return res + (front_el,) if with_element else res


# ------------------------------------------------------------ 보기 방향

def grid_trace(view, centers_y, centers_z, from_plus_x=True, with_element=False):
    """(y, z) 자리마다 x 축 광선 — from_plus_x 면 +x 에서 -x 로 (보이는 면 east), 아니면 -x 에서 +x 로 (west)"""
    yy = np.asarray(centers_y, float).ravel()
    zz = np.asarray(centers_z, float).ravel()
    x0 = 1000.0 if from_plus_x else -1000.0
    P = np.stack([np.full(yy.shape, x0), yy, zz], axis=1)
    D = np.array([-1.0 if from_plus_x else 1.0, 0.0, 0.0])
    return view.trace(P, D, with_element)
