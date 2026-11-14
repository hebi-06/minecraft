#!/usr/bin/env python3
"""2026-11-14 모드 없는 클라이언트: 직업 스킬의 입체 효과 모델 (Fx3d — 사신 황천문의 명계의 문 · 망자의 손) 을 앞에서 본 그림으로 구움 (움직임 칸마다 —
글꼴 카드, tools/vfx_particles.py 가 부름). 모드 클라이언트는 입체 그대로, 모드 없는 클라이언트는 이 그림 판을 보는 사람 쪽으로 돌려 띄움 (vfx.PBridge — bill).

그림 = 모드 FxModels 와 같은 셈: 부분 움직임 (pivot · rot · scale — smoothstep 열쇠), 재질 색 (빛깔 · 속빛 · 흰색 · 짙은 빛깔 · 고정 색) × 그림 색, 엔티티 빛
(두 빛 + 바탕 0.4 — 스스로 빛나는 모델은 그늘 없음), 비치는 면은 먼 것부터 섞음, 빛 무리 (glow — 가운데 속빛 · 가장자리 투명). 두 배로 그려 줄임 (가장자리 부드럽게).
색은 그 모델을 쓰는 직업의 색으로 구움 (JobFx3d.look — 겉빛 · 속빛): 판의 글자 색은 흰색 (mat 2).

  python3 tools/vfx_bake3d.py --preview <폴더>   # 칸 그림을 한 장에 (확인용)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fx_models as fx  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
MODEL_DIR = os.path.join(RES, "assets/mmorpg/fxmodel")


def job_colors(glow, core):
    """JobFx.outer · inner (겉빛 = 더 짙은 색, 속빛 = 다른 색을 흰색 쪽으로 0.55)"""

    def sat(c):
        mx, mn = max(c), min(c)
        return 0 if mx <= 0 else (mx - mn) / mx

    a, b = (glow, core) if sat(glow) >= sat(core) else (core, glow)
    return tuple(a), tuple(x + (1 - x) * 0.55 for x in b)


# 사신 (jobs.json reaper color — [겉빛, 심지])
REAPER = job_colors((0.22, 0.96, 0.86), (0.1, 0.36, 0.38))

# 구울 모델: 색 (빛깔 · 속빛 — JobFx3d.look), 긴 변 픽셀, 움직임 (이름 · 칸 수 · 되풀이), 빛 무리 짙기 (스킬의 glowAlpha), 그릴 높이 범위 (모델 칸 — 땅 속은 뺌)
BAKE = {
    # ReaperSkills.ult: 땅에서 솟으며 문짝이 열림 (open) → 소용돌이 (swirl — 되풀이) → 닫힘 (close)
    "gate": {"color": REAPER[0], "glow": REAPER[1], "cap": 192, "anims": [("open", 6, False), ("swirl", 8, True), ("close", 6, False)], "glowAlpha": 0.4},
    # ReaperSkills.ult: 적의 발밑에서 솟아 움켜쥠 (grasp) — 팔뚝 아래는 땅 속 (y −0.62 아래는 뺌: 손가락이 더 큰 그림으로). 빛 무리 없음 (섞는 빛에서는 손을 덮는
    # 회색 원판으로 보였음 — 모드는 더하는 빛)
    "bone_hand": {"color": REAPER[0], "glow": REAPER[1], "cap": 128, "anims": [("grasp", 6, False)], "glowAlpha": 0.0, "ymin": -0.62},
}

FIXED = [(0.76, 0.79, 0.84), (1.0, 0.8, 0.36), (0.55, 0.38, 0.22), (0.92, 0.89, 0.8), (0.13, 0.1, 0.17)]


def mat_colors(color, glow):
    """FxModels 재질 색 (0 빛깔 · 1 속빛 · 2 흰색 · 3 짙은 빛깔 · 4 ~ 8 고정 · 9 짙은 속빛)"""
    c, g = np.array(color, float), np.array(glow, float)
    return [c, g, np.ones(3), c * 0.45] + [np.array(f) for f in FIXED] + [g * 0.5]


def light(n):
    """엔티티 빛 (fx_look 과 같음 — 두 빛 + 바탕 0.4)"""
    l0 = fx.unit(np.array([0.2, 1.0, -0.7]))
    l1 = fx.unit(np.array([-0.2, 1.0, 0.7]))
    return min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(n, l0))) + max(0.0, float(np.dot(n, l1)))))


def load(name):
    data = json.load(open(os.path.join(MODEL_DIR, name + ".json")))
    tex = np.asarray(Image.open(os.path.join(RES, "assets/mmorpg", data["tex"].split(":", 1)[1])).convert("RGBA"), dtype=float) / 255.0
    return data, tex


def posed(data, anim, u):
    """움직임 anim 의 자리 u (0 ~ 1) 의 면들: [(꼭짓점 넷, uv 넷, 법선, 재질)]"""
    tracks = (data.get("anims", {}).get(anim, {}) or {}).get("tracks", {}) if anim else {}
    out = []
    for p in data["parts"]:
        M, hidden = fx._part_matrix(p, tracks.get(p["name"]), u)
        if hidden:
            continue
        R = M[:3, :3]
        for q in p["q"]:
            vs = [R @ np.array(q[i * 8:i * 8 + 3]) + M[:3, 3] for i in range(4)]
            uv = [(q[i * 8 + 3], q[i * 8 + 4]) for i in range(4)]
            n = sum(R @ np.array(q[i * 8 + 5:i * 8 + 8]) for i in range(4))
            out.append((vs, uv, fx.unit(n) if np.linalg.norm(n) > 1e-9 else np.array([0.0, 0.0, 1.0]), int(q[32])))
    return out


def frames_of(spec):
    """구울 칸: [(움직임, u)] — 되풀이는 0 ~ (n−1)/n (끝 = 처음), 한 번은 0 ~ 1"""
    out = []
    for anim, n, loop in spec["anims"]:
        for k in range(n):
            out.append((anim, k / n if loop else (k / max(1, n - 1))))
    return out


def bounds(data, spec):
    """모든 칸의 면을 합친 앞에서 본 범위 (x · y) — 칸마다 같은 판 (자리 · 크기가 같음)"""
    lo = np.array([1e9, 1e9])
    hi = -lo
    for anim, u in frames_of(spec):
        for vs, _, _, _ in posed(data, anim, u):
            for v in vs:
                lo = np.minimum(lo, v[:2])
                hi = np.maximum(hi, v[:2])
    for gx, gy, gz, gr in (data.get("glow", []) if spec["glowAlpha"] > 0 else []):
        lo = np.minimum(lo, [gx - gr, gy - gr])
        hi = np.maximum(hi, [gx + gr, gy + gr])
    if "ymin" in spec:
        lo[1] = max(lo[1], spec["ymin"])
    pad = 0.02 * max(hi - lo)
    return lo - pad, hi + pad


def render(data, tex, spec, anim, u, lo, hi, W, H):
    """앞 (+Z 쪽에서 −Z 를 봄) 그림 W × H (RGBA — 섞지 않은 색)"""
    th, tw = tex.shape[:2]
    cols = mat_colors(spec["color"], spec["glow"])
    emissive = bool(data.get("emissive"))
    sx = W / (hi[0] - lo[0])
    sy = H / (hi[1] - lo[1])
    rgb = np.zeros((H, W, 3))
    alpha = np.zeros((H, W))
    zbuf = np.full((H, W), -1e9)
    tris = []
    for vs, uv, n, mat in posed(data, anim, u):
        base = cols[max(0, min(9, mat))] * (1.0 if emissive else light(n))
        sp = [((v[0] - lo[0]) * sx, (hi[1] - v[1]) * sy, v[2]) for v in vs]
        for tri in ((0, 1, 2), (0, 2, 3)):
            tris.append((sum(sp[i][2] for i in tri) / 3, [sp[i] for i in tri], [uv[i] for i in tri], base))
    tris.sort(key=lambda t: t[0])  # 먼 것 (z 작음) 부터
    for _, ((x0, y0, z0), (x1, y1, z1), (x2, y2, z2)), ((u0, v0), (u1, v1), (u2, v2)), base in tris:
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        bx0 = max(0, int(math.floor(min(x0, x1, x2))))
        bx1 = min(W - 1, int(math.ceil(max(x0, x1, x2))))
        by0 = max(0, int(math.floor(min(y0, y1, y2))))
        by1 = min(H - 1, int(math.ceil(max(y0, y1, y2))))
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
        a = np.where(a < 0.1, 0.0, a)  # 엔티티 그림처럼 거의 투명한 곳은 버림
        col = np.clip(base[None, None, :] * t[..., :3], 0, 1)
        r = rgb[by0:by1 + 1, bx0:bx1 + 1]
        al = alpha[by0:by1 + 1, bx0:bx1 + 1]
        r[:] = r * (1 - a[..., None]) + col * a[..., None]
        al[:] = al * (1 - a) + a
        zr = zbuf[by0:by1 + 1, bx0:bx1 + 1]
        zp = w0 * z0 + w1 * z1 + w2 * z2
        zr[:] = np.where((a >= 0.5) & (zp > zr), zp, zr)
    # 빛 무리 (모드: 더하는 빛 — 여기는 섞는 빛: 가운데 속빛 glowAlpha, 가장자리 0 · 앞의 단단한 면이 가림)
    g = np.array(spec["glow"], float)
    yy, xx = np.mgrid[0:H, 0:W] + 0.5
    for gx, gy, gz, gr in (data.get("glow", []) if spec["glowAlpha"] > 0 else []):
        cx, cy = (gx - lo[0]) * sx, (hi[1] - gy) * sy
        d = np.hypot((xx - cx) / (gr * sx), (yy - cy) / (gr * sy))
        k = np.clip(1 - d, 0, 1) ** 1.5 * spec["glowAlpha"] * (zbuf <= gz)
        rgb[:] = rgb * (1 - k[..., None]) + g * k[..., None]
        alpha[:] = alpha * (1 - k) + k
    out = np.concatenate([rgb, alpha[..., None]], axis=-1)  # 섞은 색 (알파를 곱한 색)
    return out


def unpremul(img):
    a = img[..., 3:4]
    rgb = np.where(a > 1e-4, img[..., :3] / np.maximum(a, 1e-4), 0)
    return Image.fromarray((np.clip(np.concatenate([rgb, a], axis=-1), 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def bake(name):
    """모델 하나: (칸 그림 목록, 가운데 [x, y], 반 크기 [x, y], 움직임 {이름: {len, seq: [[u, 칸]]}})"""
    spec = BAKE[name]
    data, tex = load(name)
    lo, hi = bounds(data, spec)
    w, h = hi - lo
    cap = spec["cap"]
    if w >= h:
        W, H = cap, max(8, round(cap * h / w))
    else:
        W, H = max(8, round(cap * w / h)), cap
    cells = []
    anims = {}
    for anim, n, loop in spec["anims"]:
        first = len(cells)
        seq = []
        for k in range(n):
            u = k / n if loop else k / max(1, n - 1)
            big = render(data, tex, spec, anim, u, lo, hi, W * 2, H * 2)
            # 두 배 그림을 알파를 곱한 채 줄임 (가장자리 색이 번지지 않게)
            pm = Image.fromarray((np.clip(big, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").resize((W, H), Image.LANCZOS)
            cells.append(unpremul(np.asarray(pm, dtype=float) / 255.0))
            # 칸 k 가 보이는 u: 되풀이 = [k/n, (k+1)/n), 한 번 = 가까운 칸 (k/(n−1) 둘레)
            seq.append([round(k / n if loop else max(0.0, (k - 0.5) / max(1, n - 1)), 4), first + k])
        anims[anim] = {"len": float(data["anims"][anim]["len"]), "seq": seq}
    centre = [round(float(lo[0] + hi[0]) / 2, 4), round(float(lo[1] + hi[1]) / 2, 4)]
    half = [round(float(w) / 2, 4), round(float(h) / 2, 4)]
    return cells, centre, half, anims


def main():
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        for name in BAKE:
            cells, c, half, anims = bake(name)
            W, H = cells[0].size
            sheet = Image.new("RGBA", (W * len(cells), H), (40, 44, 52, 255))
            for i, cell in enumerate(cells):
                sheet.alpha_composite(cell, (i * W, 0))
            p = os.path.join(out, name + "_baked.png")
            sheet.save(p)
            print(p, len(cells), "cells", (W, H), "centre", c, "half", half, {k: v["seq"] for k, v in anims.items()})
        return 0
    print(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main())
