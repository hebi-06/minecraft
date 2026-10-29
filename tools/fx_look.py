#!/usr/bin/env python3
"""효과 모델을 게임과 비슷하게 미리 봄 (tools/fx_models.py 의 --preview 는 불투명 · 겹침만): 비치는 면 (그림 알파) 을 먼 것부터 섞어 그림 · 스스로 빛나는
모델 (emissive — 그늘 없음) · 빛 무리 (glow — 더하는 빛) · 실제 스킬 색 (빛깔 color · 속빛 glow) · 낮 / 밤 배경.

    python3 tools/fx_look.py sun --color 1,0.7,0.2 --glow 1,0.94,0.72 [--anim spin --u 0.3] [--night] [--out build/fx_look/sun.png]
    python3 tools/fx_look.py fire_wings@flap:0.5 storm_cloud ...   (이름@움직임:자리 — 여러 개)

보는 쪽: 앞 (모델 +Z 가 보는 사람 쪽 — faceCam 과 같음) · 옆 · 3/4 · 아래에서 (하늘의 먹구름 · 머리 위 해처럼).
게임 (entityTranslucent — 뒷면을 버리지 않음) 처럼 뒷면도 그림: 두 면 (double) 으로 만든 비치는 막은 두 번 섞여 더 진하게 보임 — 스스로 빛나는 모델은
한 면만으로 충분 (빛 방향이 상관없음). --cull 은 앞면만 (예전 미리 보기)
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fx_models as fx  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "fx_look")


def rgb(s):
    return tuple(float(x) for x in s.split(","))


def mat_colors(color, glow):
    c, g = np.array(color), np.array(glow)
    return {fx.TINT: c, fx.GLOW: g, fx.WHITE: np.ones(3), fx.DARK: c * 0.45, fx.METAL: np.array([0.76, 0.79, 0.84]), fx.GOLD: np.array([1.0, 0.8, 0.36]),
            fx.WOOD: np.array([0.55, 0.38, 0.22]), fx.BONE: np.array([0.92, 0.89, 0.8]), fx.SHADOW: np.array([0.13, 0.1, 0.17]), fx.DEEP: g * 0.5}


def light(n):
    """마인크래프트 엔티티 빛 (두 빛 + 바탕 0.4)"""
    l0 = fx.unit(np.array([0.2, 1.0, -0.7]))
    l1 = fx.unit(np.array([-0.2, 1.0, 0.7]))
    return min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(n, l0))) + max(0.0, float(np.dot(n, l1)))))


def render(name, anim=None, u=0.5, color=(0.42, 0.78, 1.0), glow=(0.84, 0.95, 1.0), night=False, size=360, glow_alpha=0.55, out=None, cull=False):
    m = fx.MODELS[name]()
    data = m.data()
    emissive = bool(data.get("emissive"))
    tex = np.asarray(m.atlas.img.convert("RGBA"), dtype=float) / 255.0
    th, tw = tex.shape[:2]
    tracks = (data.get("anims", {}).get(anim, {}) or {}).get("tracks", {}) if anim else {}
    cols = mat_colors(color, glow)
    quads = []
    for p in data["parts"]:
        M, hidden = fx._part_matrix(p, tracks.get(p["name"]), u)
        if hidden:
            continue
        for q in p["q"]:
            vs = [M[:3, :3] @ np.array(q[i * 8:i * 8 + 3]) + M[:3, 3] for i in range(4)]
            uv = [(q[i * 8 + 3], q[i * 8 + 4]) for i in range(4)]
            ns = [fx.unit(M[:3, :3] @ np.array(q[i * 8 + 5:i * 8 + 8])) for i in range(4)]
            quads.append((vs, uv, ns, q[32]))
    views = [("front", fx.rot_x(0)), ("side", fx.rot_y(-90)), ("3/4", fx.rot_x(-20) @ fx.rot_y(-35)), ("below", fx.rot_x(-70) @ fx.rot_y(-20))]
    allp = np.array([v for qd in quads for v in qd[0]] + [np.array(g[:3]) for g in data["glow"]])
    lo, hi = allp.min(0), allp.max(0)
    cen = (lo + hi) / 2
    ext = max(hi - lo) * 0.62 + 0.05
    img = Image.new("RGB", (size * len(views), size + 18), (24, 26, 32))
    d = ImageDraw.Draw(img)
    for vi, (vn, R) in enumerate(views):
        ys = np.linspace(0, 1, size)[:, None, None]
        if night:
            buf = np.tile(np.array([0.03, 0.04, 0.08]), (size, size, 1)) * (1 - ys * 0.3)
        else:
            buf = np.tile(np.array([0.55, 0.72, 1.0]), (size, size, 1)) * (1 - ys * 0.25) + np.array([0.0, 0.0, 0.0])
        tris = []
        for vs, uv, ns, mat in quads:
            pv = [R @ (v - cen) for v in vs]
            fn = np.cross(pv[1] - pv[0], pv[2] - pv[0]) + np.cross(pv[2] - pv[0], pv[3] - pv[0])
            if cull and fn[2] <= 0:
                continue
            world_n = fx.unit(sum(ns))
            sh = 1.0 if emissive else light(world_n)
            base = cols[mat] * sh
            sp = [(size / 2 + p[0] / ext * size / 2, size / 2 - p[1] / ext * size / 2, p[2]) for p in pv]
            for tri in ((0, 1, 2), (0, 2, 3)):
                zmean = sum(sp[i][2] for i in tri) / 3
                tris.append((zmean, [sp[i] for i in tri], [uv[i] for i in tri], base))
        tris.sort(key=lambda t: t[0])  # 먼 것부터 (z 가 작을수록 멂)
        zbuf = np.full((size, size), -1e9)  # 거의 안 비치는 (알파 0.5 이상) 면의 가장 가까운 z — 빛 무리 가림 (게임: 번개 그리기도 깊이 시험)
        for _, ((x0, y0, z0), (x1, y1, z1), (x2, y2, z2)), ((u0_, v0_), (u1_, v1_), (u2_, v2_)), base in tris:
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            bx0 = max(0, int(math.floor(min(x0, x1, x2))))
            bx1 = min(size - 1, int(math.ceil(max(x0, x1, x2))))
            by0 = max(0, int(math.floor(min(y0, y1, y2))))
            by1 = min(size - 1, int(math.ceil(max(y0, y1, y2))))
            if bx1 < bx0 or by1 < by0:
                continue
            xs, ys_ = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
            w0 = ((x1 - xs) * (y2 - ys_) - (x2 - xs) * (y1 - ys_)) / area
            w1 = ((x2 - xs) * (y0 - ys_) - (x0 - xs) * (y2 - ys_)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            tu = np.clip(((w0 * u0_ + w1 * u1_ + w2 * u2_) * tw).astype(int), 0, tw - 1)
            tv = np.clip(((w0 * v0_ + w1 * v1_ + w2 * v2_) * th).astype(int), 0, th - 1)
            lum = tex[tv, tu, 0]
            a = tex[tv, tu, 3] * inside
            a = np.where(a < 0.1, 0.0, a)  # 엔티티 그림처럼 거의 투명한 곳은 버림
            col = np.clip(base[None, None, :] * lum[:, :, None], 0, 1)
            region = buf[by0:by1 + 1, bx0:bx1 + 1]
            region[:] = region * (1 - a[:, :, None]) + col * a[:, :, None]
            zr = zbuf[by0:by1 + 1, bx0:bx1 + 1]
            zp = w0 * z0 + w1 * z1 + w2 * z2
            solid = a >= 0.5
            zr[:] = np.where(solid & (zp > zr), zp, zr)
        # 빛 무리 (더하는 빛 — 가운데 속빛 glow_alpha, 가장자리 0)
        gcol = cols[fx.GLOW]
        yy, xx = np.mgrid[0:size, 0:size] + 0.5
        for gx, gy, gz, gr in data["glow"]:
            p = R @ (np.array([gx, gy, gz]) - cen)
            cx, cy = size / 2 + p[0] / ext * size / 2, size / 2 - p[1] / ext * size / 2
            rr = gr / ext * size / 2
            dd = np.hypot(xx - cx, yy - cy) / max(1e-6, rr)
            k = np.clip(1 - dd, 0, 1) * glow_alpha * (zbuf <= p[2])  # 빛 무리 (가운데 깊이) 보다 앞에 있는 면이 가림
            buf += gcol[None, None, :] * k[:, :, None]
        panel = Image.fromarray((np.clip(buf, 0, 1) * 255).astype(np.uint8), "RGB")
        img.paste(panel, (vi * size, 18))
        label = "%s %s%s%s" % (name, vn, "  %s@%.2f" % (anim, u) if anim else "", "  (emissive)" if emissive else "")
        d.text((vi * size + 4, 3), label, fill=(230, 230, 230))
    os.makedirs(OUT, exist_ok=True)
    out = out or os.path.join(OUT, "%s%s.png" % (name, "@%s_%.2f" % (anim, u) if anim else ""))
    img.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="+")
    ap.add_argument("--color", type=rgb, default=(0.42, 0.78, 1.0))
    ap.add_argument("--glow", type=rgb, default=(0.84, 0.95, 1.0))
    ap.add_argument("--night", action="store_true")
    ap.add_argument("--size", type=int, default=360)
    ap.add_argument("--glow-alpha", type=float, default=0.55)
    ap.add_argument("--cull", action="store_true")
    a = ap.parse_args()
    for n in a.names:
        anim, u = None, 0.5
        if "@" in n:
            n, rest = n.split("@", 1)
            anim, _, us = rest.partition(":")
            u = float(us) if us else 0.5
        print(render(n, anim, u, a.color, a.glow, a.night, a.size, a.glow_alpha, cull=a.cull))


if __name__ == "__main__":
    main()
