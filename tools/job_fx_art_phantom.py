#!/usr/bin/env python3
"""2026-11-15 팬텀 아처 전용 2D 그림 (사용자 "아니 2d 그리라고" — 공용 그림을 바꿔 끼우지 말고 새로 그림, claude/archer-remake.md) → tools/java_fx (rpg:rfx_jp*).

  python3 tools/job_fx_art_phantom.py                     # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/job_fx_art_phantom.py --preview <폴더>    # 확인용 그림도
  python3 tools/job_fx_art_phantom.py --only rfx_jpblast

꼴은 v2c 와 같음 (tools/job_fx_art_v2c_marks.py): 흰색 (밝기 = 알파) · 4배 판에 그려 줄임 · 빛 칸 N 다음 심지 칸 N, 게임이 빛 칸에 진홍 · 심지 칸에 흰 강철빛을 입힘.

그림 (이름 — 보는 꼴 — 칸 — 장 수 — 열 수):
- rfx_jptrace  lookat_direction 256×64  ×8 (4 열)  투영 선: 칼 윤곽 (자루 → 코등이 → 날 어깨 → 칼끝) 이 손잡이부터 그어짐 (0 ~ 5 — 긋는 끝에 빛 점) → 6 번쩍 → 7 사라짐
- rfx_jpstreak lookat_direction 256×48  ×6 (6 열)  칼 궤적: 오른쪽 (나아가는 쪽) 이 굵고 밝은 리본 · 가는 흰 심지 · 끝으로 갈수록 가늘고 옅어짐 → 짧아지며 사라짐
- rfx_jpimpact rotate_xyz       192×192 ×8 (4 열)  착탄 섬광: 0 번쩍 (네 뿔 별 · 원) → 1 ~ 4 고리가 퍼지고 칼 조각 여덟이 바깥으로 튐 → 5 ~ 7 사라짐
- rfx_jpcross  rotate_xyz       256×256 ×8 (4 열)  X 베기: 0 ~ 2 초승달 베기 둘이 엇갈려 그어짐 → 3 가득 · 가운데 별 → 4 ~ 7 가늘어지며 사라짐
- rfx_jpblast  rotate_xyz       256×256 ×10 (5 열) 폭발: 0 ~ 1 번쩍 → 2 ~ 6 불덩이가 퍼짐 (구름 결) · 충격파 고리 · 칼 조각 → 7 ~ 9 흩어짐
- rfx_jpflame  rotate_xyz       128×192 ×8 (8 열)  불꽃 (이어 돌림): 아래가 넓고 위로 갈라지는 불길 · 속 밝은 심지 · 위로 흐르는 결
- rfx_jpdusk   rotate_xyz       512×256 ×1         노을 배경판: 아래 (지평선) 가 짙은 띠 · 가운데 아래 지는 해 · 가로 구름 띠
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_fx_art_v2 import COLORS, Pad, clamp01  # noqa: E402
from job_fx_art_v2b import arc_pts, fbm, finish, gauss, polar, star4, streak  # noqa: E402
import job_fx_art_v2c_marks as M  # noqa: E402 — Spec · render · write · preview

COLORS["phantom"] = ((1.0, 0.34, 0.24), (0.92, 0.96, 1.0))
SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, loop=False, zoom=0, desc=""):
    def deco(fn):
        SPECS[name] = M.Spec(name, facing, cw, ch, frames, cols, "phantom", fn, loop, zoom, desc)
        return fn
    return deco


def ease(u):
    return 1 - (1 - u) ** 3


# ------------------------------------------------------------ rfx_jptrace (투영 선)

def trace_frame(f, W=256, H=64):
    pad = Pad(W, H)
    c = H / 2
    hx, gx, sx, ex, tx = 18.0, 66.0, 74.0, 204.0, 240.0
    # 윤곽 (긋는 차례): 자루 → 코등이 아래 → 코등이 위 → 날 어깨 위 → 칼끝 → 날 어깨 아래 → 코등이 쪽
    path = [(hx, c), (gx, c), (gx, c + 15), (gx, c - 15), (gx, c), (sx, c - 7), (ex, c - 6), (tx, c), (ex, c + 6), (sx, c + 7), (gx, c)]
    seg = [math.hypot(path[i + 1][0] - path[i][0], path[i + 1][1] - path[i][1]) for i in range(len(path) - 1)]
    total = sum(seg)
    prog = min(1.0, (f + 1) / 6.0) if f <= 5 else 1.0
    fade = 1.0 if f <= 6 else 0.45
    draw = prog * total
    pts = [path[0]]
    head = path[0]
    acc = 0.0
    for i, L in enumerate(seg):
        if acc + L <= draw:
            pts.append(path[i + 1])
            acc += L
            head = path[i + 1]
        else:
            u = (draw - acc) / L
            head = (path[i][0] + (path[i + 1][0] - path[i][0]) * u, path[i][1] + (path[i + 1][1] - path[i][1]) * u)
            pts.append(head)
            break
    core = pad.zeros()
    glow = pad.zeros()
    k = fade * (1.25 if f == 6 else 1.0)
    for i in range(len(pts) - 1):
        core = np.maximum(core, pad.stroke([pts[i], pts[i + 1]], 1.1, a0=k))
        glow = np.maximum(glow, pad.stroke([pts[i], pts[i + 1]], 4.5, a0=0.55 * k, soft=2.0))
    # 꼭짓점 빛 점 · 긋는 끝 (밝은 점)
    for p in pts[:-1]:
        core = np.maximum(core, pad.disc_field(p[0], p[1], 1.6, soft=0.8) * 0.8 * k)
    if f <= 5:
        core = np.maximum(core, star4(pad, head[0], head[1], 7.0, 1.0, rot=0.3, thin=0.14, diag=0.4))
        glow = np.maximum(glow, pad.radial(head[0], head[1], 14.0, 1.6))
    if f == 6:  # 번쩍 — 날 안쪽이 옅게 차오름
        glow = np.maximum(glow, pad.poly([(sx, c - 7), (ex, c - 6), (tx, c), (ex, c + 6), (sx, c + 7)], 0.35))
    return finish(pad, glow, core, halo=((1.0, 0.45), (2.5, 0.3), (5.0, 0.18)), body_k=0.8, core_bloom=((1.0, 0.35), (2.5, 0.25)), win=3.0)


sprite("rfx_jptrace", "lookat_direction", 256, 64, 8, 4, desc="투영 선: 칼 윤곽이 손잡이부터 그어짐 → 번쩍 → 사라짐")(trace_frame)


# ------------------------------------------------------------ rfx_jpstreak (칼 궤적)

def streak_frame(f, W=256, H=48):
    pad = Pad(W, H)
    c = H / 2
    L = [1.0, 0.92, 0.78, 0.6, 0.4, 0.22][f]
    k = [1.0, 0.95, 0.85, 0.65, 0.42, 0.22][f]
    x1 = W - 10
    x0 = x1 - (W - 22) * L
    core = streak(pad, (x0, c), (x1, c), 3.2, a=k, head_w=4.0, tail_a=0.0)
    glow = streak(pad, (x0 - 8, c), (x1, c), 15.0, a=0.75 * k, head_w=17.0, tail_a=0.0)
    # 리본 결 (위아래로 흐르는 가는 줄 둘)
    for dy, a in ((-5.5, 0.4), (5.5, 0.32)):
        core = np.maximum(core, streak(pad, (x0 + (x1 - x0) * 0.35, c + dy), (x1 - 6, c + dy * 0.4), 1.0, a=a * k, head_w=1.4, tail_a=0.0))
    core = np.maximum(core, pad.disc_field(x1 - 3, c, 3.0, soft=1.2) * k)
    return finish(pad, glow, core, halo=((1.2, 0.45), (3.0, 0.3), (6.0, 0.18)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.25)), win=3.0)


sprite("rfx_jpstreak", "lookat_direction", 256, 48, 6, 6, desc="칼 궤적 리본: 앞이 굵고 밝음 · 흰 심지 → 짧아지며 사라짐")(streak_frame)


# ------------------------------------------------------------ rfx_jpimpact (착탄 섬광)

def shard(pad, cx, cy, ang, r0, r1, w, a):
    ca, sa = math.cos(ang), math.sin(ang)
    px, py = -sa, ca
    pts = [(cx + ca * r0 + px * w, cy + sa * r0 + py * w), (cx + ca * r1, cy + sa * r1), (cx + ca * r0 - px * w, cy + sa * r0 - py * w)]
    return pad.poly(pts, a)


def impact_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    core = pad.zeros()
    glow = pad.zeros()
    k = [1.0, 1.0, 0.9, 0.75, 0.58, 0.4, 0.24, 0.1][f]
    if f <= 2:
        s = [1.0, 0.85, 0.6][f]
        core = np.maximum(core, star4(pad, c, c, 70 * s, k, rot=0.0, thin=0.075, diag=0.5))
        core = np.maximum(core, pad.disc_field(c, c, 10 + 6 * s, soft=3.0) * k)
        glow = np.maximum(glow, pad.radial(c, c, 60 + 20 * s, 1.4) * k)
    ring_r = [14, 30, 44, 56, 66, 74, 80, 84][f]
    core = np.maximum(core, gauss(rr - ring_r, 1.4 + f * 0.3) * k * 0.9)
    glow = np.maximum(glow, gauss(rr - ring_r, 5.0 + f) * k * 0.6)
    # 칼 조각 여덟 (바깥으로 튐)
    if f >= 1:
        d0 = [0, 18, 30, 42, 52, 60, 66, 70][f]
        for i in range(8):
            ang = i * math.pi / 4 + 0.25 + (i % 2) * 0.12
            ln = 16 - f * 1.2
            core = np.maximum(core, shard(pad, c, c, ang, d0, d0 + ln, 2.4, k))
            glow = np.maximum(glow, shard(pad, c, c, ang, d0 - 4, d0 + ln + 3, 5.0, 0.5 * k))
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.35), (9.0, 0.22)), body_k=0.85, core_bloom=((1.5, 0.35), (4.0, 0.28)), win=4.0)


sprite("rfx_jpimpact", "rotate_xyz", 192, 192, 8, 4, zoom=1, desc="착탄 섬광: 번쩍 → 고리가 퍼지고 칼 조각 여덟이 튐 → 사라짐")(impact_frame)


# ------------------------------------------------------------ rfx_jpcross (X 베기)

def slash(pad, p0, ctl, p2, w, a, prog=1.0, n=48):
    """휜 베기 하나 (2차 곡선 p0 → p2 · 가운데가 굵고 양끝이 가는 초승달) — prog 만큼 그어짐"""
    pts = []
    for i in range(n + 1):
        u = prog * i / n
        v = 1 - u
        pts.append((p0[0] * v * v + ctl[0] * 2 * u * v + p2[0] * u * u, p0[1] * v * v + ctl[1] * 2 * u * v + p2[1] * u * u))
    return pad.stroke(pts, w, wfn=lambda t: w * (0.18 + 0.82 * np.maximum(0.0, np.sin(np.pi * np.clip(t * prog, 0.0, 1.0))) ** 0.7),
                      afn=lambda t: a * (0.4 + 0.6 * t))


def cross_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    core = pad.zeros()
    glow = pad.zeros()
    prog = min(1.0, (f + 1) / 3.0)
    k = [1.0, 1.0, 1.0, 1.0, 0.8, 0.6, 0.38, 0.18][f]
    thin = [1.0, 1.0, 1.0, 1.0, 0.8, 0.62, 0.46, 0.32][f]
    d = 100.0
    # 엇갈린 두 베기: 왼쪽 위 → 오른쪽 아래 · 오른쪽 위 → 왼쪽 아래 (가운데에서 X — 바깥으로 조금 휨)
    for (p0, ctl, p2) in (((c - d, c - d), (c + 28, c - 28), (c + d, c + d)), ((c + d, c - d), (c - 28, c - 28), (c - d, c + d))):
        core = np.maximum(core, slash(pad, p0, ctl, p2, 8.0 * thin, k, prog))
        glow = np.maximum(glow, slash(pad, p0, ctl, p2, 22.0 * thin, 0.6 * k, prog))
    if f >= 2:
        sk = [0, 0, 0.6, 1.0, 0.7, 0.45, 0.25, 0.1][f]
        cy = c - 14
        core = np.maximum(core, star4(pad, c, cy, 46 * sk + 8, sk, rot=0.785, thin=0.09, diag=0.35))
        glow = np.maximum(glow, pad.radial(c, cy, 50 * sk + 10, 1.5) * sk)
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.38), (9.0, 0.24)), body_k=0.85, core_bloom=((1.5, 0.4), (4.0, 0.3)), win=4.0)


sprite("rfx_jpcross", "rotate_xyz", 256, 256, 8, 4, zoom=3, desc="X 베기: 초승달 둘이 엇갈려 그어짐 → 가운데 별 → 가늘어지며 사라짐")(cross_frame)


# ------------------------------------------------------------ rfx_jpblast (폭발)

def blast_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    X, Y = pad.grid()
    rr, th = polar(pad, c, c)
    core = pad.zeros()
    glow = pad.zeros()
    R = [26, 44, 62, 76, 86, 94, 100, 104, 107, 109][f]
    k = [1.0, 1.0, 1.0, 0.95, 0.85, 0.72, 0.58, 0.42, 0.26, 0.12][f]
    n = fbm(X / 34.0, Y / 34.0 - f * 0.18, seed=7, octaves=4)
    body = clamp01((R * (0.78 + 0.35 * n) - rr) / (R * 0.22))
    hollow = clamp01((rr - R * (0.25 + 0.06 * f)) / (R * 0.3)) if f >= 4 else 1.0
    glow = np.maximum(glow, body * k * (0.75 + 0.25 * n) * hollow)
    hot = clamp01((R * (0.5 + 0.25 * n) - rr) / (R * 0.25)) * max(0.0, 1.0 - f * 0.13)
    core = np.maximum(core, hot * k)
    if f <= 1:
        core = np.maximum(core, star4(pad, c, c, 90 - f * 20, 1.0, rot=0.2, thin=0.06, diag=0.45))
    # 충격파 고리
    ring_r = R * 1.12
    core = np.maximum(core, gauss(rr - ring_r, 1.6 + f * 0.4) * k * 0.75)
    glow = np.maximum(glow, gauss(rr - ring_r, 6.0 + f) * k * 0.5)
    # 칼 조각 (튀는 조각 열)
    if 1 <= f <= 8:
        for i in range(10):
            ang = i * 0.628 + 0.3 + (i % 3) * 0.1
            d0 = R * (0.9 + 0.1 * (i % 2)) + f * 2
            core = np.maximum(core, shard(pad, c, c, ang, d0, d0 + 14, 2.2, k * 0.95))
    return finish(pad, glow, core, halo=((2.0, 0.5), (5.0, 0.38), (11.0, 0.24)), body_k=0.9, core_bloom=((2.0, 0.4), (5.0, 0.3)), win=4.0)


sprite("rfx_jpblast", "rotate_xyz", 256, 256, 10, 5, zoom=3, desc="폭발: 번쩍 → 불덩이 · 충격파 고리 · 칼 조각 → 흩어짐")(blast_frame)


# ------------------------------------------------------------ rfx_jpflame (불꽃 — 이어 돌림)

def flame_frame(f, W=128, H=192, n=8):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    ph = f / n
    v = 1 - Y / H  # 0 아래 → 1 위
    width = (W * 0.42) * (1 - v) ** 0.7 * (0.6 + 0.4 * (1 - v))
    noise = fbm(X / 26.0, (Y / 26.0) + ph * 2.0 * 2, seed=3, octaves=4)
    noise2 = fbm(X / 26.0, (Y / 26.0) + (ph - 1) * 4.0, seed=3, octaves=4)
    nz = noise * (1 - ph) + noise2 * ph  # 이어 돌림 (8 장 = 한 바퀴)
    dx = np.abs(X - cx - (nz - 0.5) * 22 * v)
    m = clamp01((width * (0.75 + 0.5 * nz) - dx) / 6.0) * clamp01((0.98 - v - (nz - 0.5) * 0.3) / 0.12) * clamp01(v / 0.05 + 0.2)
    glow = m * (0.7 + 0.3 * nz)
    corew = width * 0.45
    core = clamp01((corew * (0.7 + 0.6 * nz) - dx) / 4.0) * clamp01((0.62 - v - (nz - 0.5) * 0.25) / 0.12) * clamp01(v / 0.04 + 0.3)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.32), (8.0, 0.2)), body_k=0.9, core_bloom=((1.5, 0.35), (4.0, 0.25)), win=3.0)


sprite("rfx_jpflame", "rotate_xyz", 128, 192, 8, 8, loop=True, desc="불꽃 (이어 돌림): 아래가 넓고 위로 갈라지는 불길 · 속 밝은 심지")(flame_frame)


# ------------------------------------------------------------ rfx_jpdusk (노을 배경판)

def dusk_frame(f, W=512, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    v = Y / H  # 0 위 → 1 아래 (지평선)
    side = clamp01(1 - np.abs(X - W / 2) / (W * 0.5)) ** 0.6
    glow = clamp01((v - 0.05) / 0.9) ** 1.4 * side * 0.95
    n = fbm(X / 60.0, Y / 14.0, seed=11, octaves=4)
    bands = clamp01((np.sin(v * 38.0 + n * 3.0) - 0.55) / 0.45) * clamp01((v - 0.25) / 0.3) * clamp01((0.92 - v) / 0.1) * side
    glow = np.maximum(glow, bands * 0.55)
    sx, sy, sr = W / 2, H * 0.78, H * 0.2
    sun = clamp01((sr - np.sqrt((X - sx) ** 2 + (Y - sy) ** 2)) / 3.0)
    core = sun * clamp01((H * 0.93 - Y) / 6.0)
    core = np.maximum(core, pad.radial(sx, sy, sr * 2.6, 1.8) * 0.55 * clamp01((H * 0.95 - Y) / 10.0))
    core = np.maximum(core, bands * clamp01(1 - np.abs(X - sx) / (W * 0.3)) * 0.45)
    return finish(pad, glow, core, halo=((2.0, 0.4), (6.0, 0.3)), body_k=0.95, core_bloom=((2.0, 0.35), (6.0, 0.25)), win=6.0)


sprite("rfx_jpdusk", "rotate_xyz", 512, 256, 1, 1, desc="노을 배경판: 지평선 띠 · 지는 해 · 가로 구름 띠")(dusk_frame)


def main():
    args = sys.argv[1:]
    only = set(args[args.index("--only") + 1].split(",")) if "--only" in args else None
    folder = args[args.index("--preview") + 1] if "--preview" in args else None
    if folder:
        os.makedirs(folder, exist_ok=True)
    for name, sp in SPECS.items():
        if only and name not in only:
            continue
        glows, cores = M.render(sp)
        M.write(sp, glows, cores)
        if folder:
            print("  preview:", M.preview(sp, glows, cores, folder))


if __name__ == "__main__":
    main()
