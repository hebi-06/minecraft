#!/usr/bin/env python3
"""0.46.0 룬 동작의 1인칭 손 미리 보기 — 맨팔 (주먹 · 손바닥) · 소품 · 무기를 1인칭 화면 (시야 70 · 16:9) 에 그림.

자바 MotionRender.firstPersonHands 와 같은 차례로 맨팔을 놓음:
  T(fp · w − 아래로 ARM_RISE · (1 − w)) · [어깨 ARM_BASE 축으로 fp 회전] · 바닐라 renderPlayerArm (equip 0 · swing 0:
  T(f·0.64, −0.6, −0.72) · RY(f·45) · T(f·−1, 3.6, 3.5) · RZ(f·120) · RX(200) · RY(f·−135) · T(f·5.6, 0, 0)) · 팔 부위 (축 (∓5, 2, 0) 픽셀 · 상자 4×12×4)
주 손 = 오른팔 (fp 열쇠), 왼손 = 왼팔 (fpo 열쇠). 소품 · 무기는 바닐라 1인칭 아이템 자리 (0.56, −0.52, −0.72) + fp 에 점 · 막대.

  python3 tools/hands_preview.py <동작 ...> [--out 폴더] [--t 0,0.2,...]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_preview as mp  # noqa: E402

ARM_BASE = np.array([0.49, -0.83, -0.53])  # 어깨 (맨팔 회전의 축 — 자바 MotionRender.ARM_BASE)
ARM_RISE = 0.55
SKIN = (222, 178, 140)
SLEEVE = (60, 150, 190)


def T(v):
    M = np.eye(4)
    M[:3, 3] = v
    return M


def R4(R):
    M = np.eye(4)
    M[:3, :3] = R
    return M


def deg(a):
    return math.radians(a)


def key_at(keys, t):
    """1인칭 열쇠들 (fp · fpo) 의 t 자리 · 회전 행렬 (무게 전)"""
    keys = keys or [{"t": 0.0}, {"t": 1.0}]
    t = max(0.0, min(1.0, t))
    a, b = keys[0], keys[-1]
    for i in range(len(keys) - 1):
        if keys[i]["t"] <= t <= keys[i + 1]["t"]:
            a, b = keys[i], keys[i + 1]
            break
    sp = b["t"] - a["t"]
    v = mp.ease(0.0 if sp <= 0 else (t - a["t"]) / sp, b.get("ease", "inout"))
    pa, pb = np.array(a.get("p", [0, 0, 0]), dtype=float), np.array(b.get("p", [0, 0, 0]), dtype=float)
    q = mp.slerp(mp.fp_key_rotation(a), mp.fp_key_rotation(b), v)
    return pa + (pb - pa) * v, q


def arm_polys(side, p, q, w):
    """맨팔 하나 (side 1 = 오른팔 · −1 = 왼팔) 의 면 (화면 공간 칸)"""
    f = float(side)
    qw = mp.slerp(np.array([1.0, 0.0, 0.0, 0.0]), q, w)
    base = np.array([f * ARM_BASE[0], ARM_BASE[1], ARM_BASE[2]])
    M = T(p * w + np.array([0.0, -ARM_RISE * (1 - w), 0.0]))
    M = M @ T(base) @ R4(mp.mat_of(qw)) @ T(-base)
    M = M @ T([f * 0.64, -0.6, -0.72]) @ R4(mp.ry(deg(f * 45))) @ T([f * -1.0, 3.6, 3.5])
    M = M @ R4(mp.rz(deg(f * 120))) @ R4(mp.rx(deg(200))) @ R4(mp.ry(deg(f * -135))) @ T([f * 5.6, 0.0, 0.0])
    M = M @ T(np.array([-5.0 * f, 2.0, 0.0]) / 16.0)
    x0 = -3 if f > 0 else -1
    c, faces = mp.box(x0, -2, -2, x0 + 4, 10, 2)
    out = []
    for qd in faces:
        pts = []
        for i in qd:
            v = np.append(c[i] / 16.0, 1.0)
            pts.append((M @ v)[:3])
        # 위쪽 1/2 은 소매 색 (어림)
        out.append((pts, SKIN))
    # 소매 (팔 위쪽 6 픽셀)
    c2, faces2 = mp.box(x0 - 0.25, -2.25, -2.25, x0 + 4.25, 4, 2.25)
    for qd in faces2:
        pts = [(M @ np.append(c2[i] / 16.0, 1.0))[:3] for i in qd]
        out.append((pts, SLEEVE))
    return out


def draw_polys(d, polys, W, H):
    items = []
    light = mp.unit(np.array([0.3, 0.8, 0.5]))
    for pts, col in polys:
        zs = [-p[2] for p in pts]
        if min(zs) < 0.05:
            continue
        depth = sum(zs) / len(zs)
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        nn = np.linalg.norm(n)
        shade = 0.55 + 0.45 * abs(float(np.dot(n / nn, light))) if nn > 1e-9 else 0.8
        items.append((depth, pts, tuple(int(c * shade) for c in col)))
    items.sort(key=lambda x: -x[0])
    for _, pts, col in items:
        xy = [mp.fp_project(p, W, H) for p in pts]
        if any(q is None for q in xy):
            continue
        d.polygon(xy, fill=col, outline=(20, 20, 20))


def in_window(m, key, t):
    a = m.get(key, [0, 1])
    return any(a[i] <= t <= a[i + 1] for i in range(0, len(a) - 1, 2))


def frame(m, t, W=320, H=180):
    panel = Image.new("RGB", (W, H), (44, 62, 44))
    d = ImageDraw.Draw(panel)
    d.line([(W / 2 - 4, H / 2), (W / 2 + 4, H / 2)], fill=(255, 255, 255))
    d.line([(W / 2, H / 2 - 4), (W / 2, H / 2 + 4)], fill=(255, 255, 255))
    w = mp.evaluate(m, t)["w"]
    hands = m.get("hands", {})
    main = hands.get("main", "weapon")
    off = hands.get("off", "weapon")
    polys = []
    p, q = key_at(m.get("fp"), t)
    po, qo = key_at(m.get("fpo"), t)
    hold_now = "hold" in m and in_window(m, "holdAt", t)
    hold_off_now = "holdOff" in m and in_window(m, "holdOffAt", t)
    hide = m.get("hide")
    hidden = bool(hide) and hide[0] <= t <= hide[1]
    main_bare = main == "fist" or (main == "prop" and not hold_now) or (main == "thrown" and hidden)
    off_bare = off == "fist" or (off == "prop" and not hold_off_now)
    if main_bare:
        polys += arm_polys(1, p, q, w)
    if off_bare:
        polys += arm_polys(-1, po, qo, w)
    draw_polys(d, polys, W, H)
    # 소품 · 무기 (바닐라 아이템 자리 + fp)
    for is_main, mode, now, pp, qq in ((True, main, hold_now, p, q), (False, off, hold_off_now, po, qo)):
        if mode in ("prop", "swap") and now:
            f = 1.0 if is_main else -1.0
            G = np.array([f * 0.56, -0.52, -0.72]) + pp * w
            g = mp.fp_project(G, W, H)
            if g:
                d.ellipse([g[0] - 9, g[1] - 9, g[0] + 9, g[1] + 9], fill=(250, 140, 60), outline=(40, 20, 0))
    if main == "weapon" or (main == "thrown" and not hidden) or (main == "swap" and not hold_now):
        pw, R = mp.fp_evaluate(m, t)
        G = mp.FP_BASE + mp.FP_GRIP + pw
        up = R @ mp.unit(mp.FP_REST_BLADE)
        pts = [qx for qx in (mp.fp_project(G + up * mp.FP_LEN * j / 24, W, H) for j in range(25)) if qx]
        if len(pts) > 1:
            d.line(pts, fill=(230, 230, 250), width=5)
    d.rectangle([0, 0, W - 1, H - 1], outline=(90, 90, 90))
    d.text((4, 3), "t=%.2f w=%.2f" % (t, w), fill=(255, 220, 120))
    return panel


def sheet(name, m, ts, W=320, H=180, cols=6):
    rows = (len(ts) + cols - 1) // cols
    img = Image.new("RGB", (W * min(cols, len(ts)), H * rows + 16), (20, 20, 26))
    dd = ImageDraw.Draw(img)
    dd.text((4, H * rows + 2), "%s  first person hands (%d ticks)" % (name, m.get("ticks", 0)), fill=(230, 230, 230))
    for i, t in enumerate(ts):
        img.paste(frame(m, t, W, H), ((i % cols) * W, (i // cols) * H))
    return img


def main(argv):
    out = os.path.join(mp.ROOT, "build/motion_preview")
    ts = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0]
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
    with open(mp.POSES, encoding="utf8") as f:
        motions = json.load(f)["motions"]
    os.makedirs(out, exist_ok=True)
    for name in names:
        if name == "_rest":
            m = {"ticks": 10, "keys": [{"t": 0}, {"t": 1}], "hands": {"main": "fist", "off": "fist"}, "fade": [0, 0]}
        else:
            m = motions[name]
        img = sheet(name, m, ts)
        p = os.path.join(out, name + "_hands.png")
        img.save(p)
        print(p)


if __name__ == "__main__":
    main(sys.argv[1:])
