#!/usr/bin/env python3
"""0.38.0 동작 미리 보기 — assets/mmorpg/motion/poses.json 의 열쇠 자세를 자바 판과 같은 식으로 풀어 상자 사람으로 그림 (앞 · 오른쪽 · 위 · 3/4).

자바 판 (client/motion/MotionPoses · MotionRender) 과 같은 약속:
- 쓰는 방향 (r, u, f) = 사람 기준 오른쪽 · 위 · 앞 (뿌리 — 몸통 비틀기와 상관없음). 모델 좌표 (x, y, z) = (-r, -u, -f) (픽셀, +y 아래, +z 뒤).
- 팔: dir = 어깨 → 팔꿈치 방향, blade = 무기 끝 방향, bend = 팔꿈치 굽힘 (도, 앞으로 굽힘 +). 팔 회전은 dir 과 blade (팔에 수직인 몫) 로 정함.
- 몸통: [lean 앞으로 숙임, twist 오른쪽으로 비틂, side] — 엉덩이 (0, 12, 0) 를 축으로. 어깨 · 목 자리가 따라감.
- 다리: [x 뒤로 +, y, z] 뿌리 기준 Euler (ModelPart 와 같은 ZYX), 무릎 굽힘 rk · lk (뒤로 +).
- 뿌리: root [위 칸, 앞 칸], spin (도 — 위에서 볼 때 왼쪽으로 돎 +), flip (도 — 앞구르기 +, 축 높이 0.55칸).

쓰기:  python3 tools/motion_preview.py [동작 ...] [--out 폴더] [--t 0,0.25,0.5,0.75,1]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")

BLADE_TILT = math.radians(40)  # 팔을 내린 채 든 무기가 앞에서 위로 들린 각 (자바 MotionPoses.BLADE_TILT 와 같음)
HIP = np.array([0.0, 12.0, 0.0])
FLIP_PIVOT = 0.55


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def zyx(x, y, z):
    """ModelPart 회전 (라디안): Rz · Ry · Rx"""
    return rz(z) @ ry(y) @ rx(x)


def euler_of(R):
    """zyx 의 거꾸로 (x, y, z)"""
    y = math.asin(max(-1.0, min(1.0, -R[2][0])))
    x = math.atan2(R[2][1], R[2][2])
    z = math.atan2(R[1][0], R[0][0])
    return x, y, z


def unit(v):
    v = np.asarray(v, dtype=float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def to_model(a):
    return np.array([-a[0], -a[1], -a[2]], dtype=float)


def arm_rotation(arm):
    """{dir, blade, bend} → (팔 회전 행렬 R (모델), 굽힘 라디안 — 앞으로 굽힘은 로컬 x 축 음의 회전)"""
    Y = unit(to_model(arm.get("dir", [0, -1, 0])))
    b = unit(to_model(arm.get("blade", [0, 0, 1])))
    beta = math.radians(arm.get("bend", 0) + arm.get("wrist", 0))
    bl = rx(-beta) @ np.array([0.0, -math.sin(BLADE_TILT), -math.cos(BLADE_TILT)])
    s = 1.0 if bl[2] >= 0 else -1.0
    d = b - np.dot(b, Y) * Y
    if np.linalg.norm(d) < 1e-4:
        d = np.array([0.0, 0.0, -1.0]) - np.dot([0.0, 0.0, -1.0], Y) * Y
        if np.linalg.norm(d) < 1e-4:
            d = np.array([0.0, -1.0, 0.0])
    d = unit(d)
    Z = d * s
    X = np.cross(Y, Z)
    return np.column_stack([X, Y, Z]), -math.radians(arm.get("bend", 0))


# ------------------------------------------------------------ 열쇠 자세 풀기

NEUTRAL_ARM = {"dir": [0, -1, 0], "blade": [0, 0, 1], "bend": 0}


def ease(u, kind):
    u = max(0.0, min(1.0, u))
    if kind == "linear":
        return u
    if kind == "in":
        return u * u
    if kind == "out":
        return 1 - (1 - u) * (1 - u)
    if kind == "out3":
        return 1 - (1 - u) ** 3
    return u * u * (3 - 2 * u)


def quat_of(R):
    m = R
    tr = m[0][0] + m[1][1] + m[2][2]
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        w = 0.25 * s
        x = (m[2][1] - m[1][2]) / s
        y = (m[0][2] - m[2][0]) / s
        z = (m[1][0] - m[0][1]) / s
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2
        w = (m[2][1] - m[1][2]) / s
        x = 0.25 * s
        y = (m[0][1] + m[1][0]) / s
        z = (m[0][2] + m[2][0]) / s
    elif m[1][1] > m[2][2]:
        s = math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2
        w = (m[0][2] - m[2][0]) / s
        x = (m[0][1] + m[1][0]) / s
        y = 0.25 * s
        z = (m[1][2] + m[2][1]) / s
    else:
        s = math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2
        w = (m[1][0] - m[0][1]) / s
        x = (m[0][2] + m[2][0]) / s
        y = (m[1][2] + m[2][1]) / s
        z = 0.25 * s
    return np.array([w, x, y, z])


def mat_of(q):
    w, x, y, z = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def slerp(q0, q1, t):
    d = float(np.dot(q0, q1))
    if d < 0:
        q1 = -q1
        d = -d
    if d > 0.9995:
        q = q0 + (q1 - q0) * t
        return q / np.linalg.norm(q)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * q0 + math.sin(t * th) * q1) / math.sin(th)


def key_arm(k, name):
    return k.get(name, NEUTRAL_ARM)


def lerp(a, b, u):
    return a + (b - a) * u


def lerp_list(a, b, u):
    return [lerp(x, y, u) for x, y in zip(a, b)]


def evaluate(m, t):
    """동작 m 의 t (0~1) 자세 (자바 MotionPoses.evaluate 와 같음)"""
    keys = m["keys"]
    t = max(0.0, min(1.0, t))
    k0, k1 = keys[0], keys[-1]
    for i in range(len(keys) - 1):
        if keys[i]["t"] <= t <= keys[i + 1]["t"]:
            k0, k1 = keys[i], keys[i + 1]
            break
    span = k1["t"] - k0["t"]
    u = 0.0 if span <= 0 else (t - k0["t"]) / span
    u = ease(u, k1.get("ease", "inout"))
    out = {}
    for f, dflt in (("body", [0, 0, 0]), ("rl", [0, 0, 0]), ("ll", [0, 0, 0]), ("head", [0, 0]), ("root", [0, 0])):
        out[f] = lerp_list(k0.get(f, dflt), k1.get(f, dflt), u)
    for f in ("rk", "lk", "spin", "flip"):
        out[f] = lerp(k0.get(f, 0), k1.get(f, 0), u)
    for f in ("ra", "la"):
        a0, a1 = key_arm(k0, f), key_arm(k1, f)
        R0, b0 = arm_rotation(a0)
        R1, b1 = arm_rotation(a1)
        q = slerp(quat_of(R0), quat_of(R1), u)
        w0 = -math.radians(a0.get("wrist", 0))
        w1 = -math.radians(a1.get("wrist", 0))
        out[f] = (mat_of(q), lerp(b0, b1, u), lerp(w0, w1, u))
    fade = m.get("fade", [0.08, 0.2])
    w = 1.0
    if t < fade[0]:
        w = ease(t / fade[0], "inout")
    if t > 1 - fade[1]:
        w = min(w, ease((1 - t) / fade[1], "inout"))
    out["w"] = w
    return out


# ------------------------------------------------------------ 상자 사람

def box(x0, y0, z0, x1, y1, z1):
    c = [np.array([x, y, z], dtype=float) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    # (꼭짓점 번호: x 바깥 4 · y 2 · z 1)
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return c, faces


def transform(points, R, t):
    return [R @ p + t for p in points]


def build(pose, slim=False, weapon=True):
    """모델 좌표 (픽셀) 의 면 목록 [(꼭짓점 4, 색)]"""
    w = pose["w"]
    polys = []
    body = [math.radians(v) * w for v in pose["body"]]
    Rb = zyx(body[0], body[1], body[2])
    neck = HIP + Rb @ (np.zeros(3) - HIP)
    # 머리
    hx, hy = [math.radians(v) * w for v in pose["head"]]
    Rh = zyx(hx, hy, 0)
    c, f = box(-4, -8, -4, 4, 0, 4)
    polys += [(transform([c[i] for i in q], Rh, neck), (230, 190, 150)) for q in f]
    c, f = box(-4, 0, -2, 4, 12, 2)
    polys += [(transform([c[i] for i in q], Rb, neck), (60, 170, 200)) for q in f]
    aw = 3 if slim else 4
    for side, key, col in ((-1, "ra", (225, 180, 140)), (1, "la", (205, 160, 120))):
        R, bend, wrist = pose[key]
        Rn = np.eye(3)
        q = slerp(quat_of(Rn), quat_of(R), w)
        R = mat_of(q)
        bend *= w
        S0 = np.array([5.0 * side, 2.0, 0.0])
        sh = HIP + Rb @ (S0 - HIP)
        x0 = -3 if side < 0 else -1
        if slim:
            x0 = -2 if side < 0 else -1
        cx = x0 + aw / 2
        J = np.array([cx, 4.0, 0.0])
        cu, fu = box(x0, -2, -2, x0 + aw, 4, 2)
        polys += [(transform([cu[i] for i in qq], R, sh), col) for qq in fu]
        Rj = rx(bend)
        cl, fl = box(x0, 4, -2, x0 + aw, 10, 2)
        low = [Rj @ (p - J) + J for p in cl]
        polys += [(transform([low[i] for i in qq], R, sh), tuple(int(v * 0.9) for v in col)) for qq in fl]
        if weapon and key == "ra":
            grip = Rj @ (np.array([cx, 10.0, -2.0]) - J) + J
            bl = rx(bend + wrist * w) @ np.array([0.0, -math.sin(BLADE_TILT), -math.cos(BLADE_TILT)])
            tip = grip + bl * 20
            back = grip - bl * 4
            # 칼날: 굵기 1 · 너비 2 의 막대
            ax = unit(np.cross(bl, [1.0, 0.0, 0.0])) if abs(bl[0]) < 0.9 else unit(np.cross(bl, [0.0, 1.0, 0.0]))
            ay = unit(np.cross(bl, ax))
            pts = []
            for a in (back, tip):
                for sx in (-1, 1):
                    for sy in (-1, 1):
                        pts.append(a + ax * sx * 1.0 + ay * sy * 0.4)
            faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
            polys += [(transform([pts[i] for i in qq], R, sh), (235, 235, 245)) for qq in faces]
    for side, key, kk, col in ((-1, "rl", "rk", (50, 70, 160)), (1, "ll", "lk", (40, 60, 140))):
        e = [math.radians(v) * w for v in pose[key]]
        R = zyx(e[0], e[1], e[2])
        P = np.array([1.9 * side, 12.0, 0.0])
        J = np.array([0.0, 6.0, 0.0])
        cu, fu = box(-2, 0, -2, 2, 6, 2)
        polys += [(transform([cu[i] for i in qq], R, P), col) for qq in fu]
        Rk = rx(math.radians(pose[kk]) * w)
        cl, fl = box(-2, 6, -2, 2, 12, 2)
        low = [Rk @ (p - J) + J for p in cl]
        polys += [(transform([low[i] for i in qq], R, P), tuple(int(v * 0.85) for v in col)) for qq in fl]
    return polys


def to_ours(polys, pose):
    """모델 좌표 → 우리 공간 (블록: +x 오른쪽 · +y 위 · -z 앞) + 뿌리 (spin · flip · root)"""
    w = pose["w"]
    S = np.diag([-1.0, -1.0, 1.0])
    up, fwd = pose["root"][0] * w, pose["root"][1] * w
    spin = math.radians(pose["spin"])  # 회전 · 구르기는 무게를 곱하지 않음 (0 · 360° 로 시작 · 끝남 — 자바 MotionRender.rotate 와 같음)
    flip = math.radians(pose["flip"])
    out = []
    for pts, col in polys:
        q = []
        for p in pts:
            o = S @ (p / 16.0 + np.array([0.0, -1.501, 0.0]))
            o = o - np.array([0.0, FLIP_PIVOT, 0.0])
            o = rx(-flip) @ o
            o = o + np.array([0.0, FLIP_PIVOT, 0.0])
            o = ry(spin) @ o
            o = o + np.array([0.0, up, -fwd])
            q.append(o)
        out.append((q, col))
    return out


VIEWS = {
    # 이름: (화면 x 축, 화면 y 축, 깊이 축 — 멀수록 큼)
    "front": (np.array([-1.0, 0, 0]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0])),
    "right": (np.array([0, 0, -1.0]), np.array([0, 1.0, 0]), np.array([-1.0, 0, 0])),
    "top": (np.array([1.0, 0, 0]), np.array([0, 0, -1.0]), np.array([0, -1.0, 0])),
}


def three_quarter(a_deg=35, e_deg=18):
    """앞 오른쪽 위 (a 도 · e 도) 에서 보는 카메라: (화면 오른쪽, 화면 위, 보는 방향)"""
    a = math.radians(a_deg)
    e = math.radians(e_deg)
    fwd = unit(np.array([-math.sin(a), -math.sin(e), math.cos(a)]))
    right = unit(np.cross(fwd, [0, 1.0, 0]))
    upv = unit(np.cross(right, fwd))
    return (right, upv, fwd)


VIEWS["34"] = three_quarter()


def render(polys, view, size=220, scale=70.0):
    sx, sy, dz = VIEWS[view]
    img = Image.new("RGB", (size, size), (30, 32, 40))
    d = ImageDraw.Draw(img)
    cx, cy = size / 2, size * 0.62
    # 바닥 줄
    d.line([(0, cy), (size, cy)], fill=(70, 70, 80)) if view in ("front", "right", "34") else None
    items = []
    light = unit(np.array([0.4, 0.8, -0.5]))
    for pts, col in polys:
        depth = sum(float(np.dot(p, dz)) for p in pts) / len(pts)
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        nn = np.linalg.norm(n)
        shade = 0.55 + 0.45 * abs(float(np.dot(n / nn, light))) if nn > 1e-9 else 0.8
        items.append((depth, pts, tuple(int(c * shade) for c in col)))
    items.sort(key=lambda x: -x[0])
    for _, pts, col in items:
        xy = [(cx + float(np.dot(p, sx)) * scale, cy - float(np.dot(p, sy)) * scale) for p in pts]
        d.polygon(xy, fill=col, outline=(15, 15, 20))
    return img


def sheet(name, m, ts, views=("front", "right", "34", "top")):
    cell = 220
    img = Image.new("RGB", (cell * len(ts), cell * len(views) + 18), (20, 20, 26))
    d = ImageDraw.Draw(img)
    d.text((4, 2), "%s  (%d ticks)" % (name, m.get("ticks", 0)), fill=(230, 230, 230))
    for i, t in enumerate(ts):
        pose = evaluate(m, t)
        polys = to_ours(build(pose), pose)
        for j, v in enumerate(views):
            im = render(polys, v, cell)
            img.paste(im, (i * cell, 18 + j * cell))
        d.text((i * cell + 4, 18 + 2), "t=%.2f w=%.2f" % (t, pose["w"]), fill=(255, 220, 120))
    return img


def main(argv):
    out = os.path.join(ROOT, "build/motion_preview")
    ts = [0.0, 0.2, 0.3, 0.42, 0.55, 0.7, 0.85, 1.0]
    names = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--out":
            out = argv[i + 1]
            i += 2
            continue
        if a == "--t":
            ts = [float(x) for x in argv[i + 1].split(",")]
            i += 2
            continue
        names.append(a)
        i += 1
    with open(POSES, encoding="utf8") as f:
        data = json.load(f)
    motions = data["motions"]
    os.makedirs(out, exist_ok=True)
    for name in names or list(motions):
        if name.startswith("_"):
            continue
        img = sheet(name, motions[name], ts)
        p = os.path.join(out, name + ".png")
        img.save(p)
        print(p)


if __name__ == "__main__":
    main(sys.argv[1:])
