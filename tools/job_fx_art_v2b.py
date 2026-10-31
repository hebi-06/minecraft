#!/usr/bin/env python3
"""v2b 직업 스킬 연출 그림 — v1 2D 그림 (tools/job_fx_art.py · job_fx_art_more.py · job_fx_art_three.py · job_fx_art_four.py) 을 v2 품질로 다시 그림
→ tools/java_fx (rpg:rfx_j*2 — 옛 이름 + "2"). 옛 그림은 건드리지 않음.

  python3 tools/job_fx_art_v2b.py                        # 그림 · 파티클 정의를 tools/java_fx 에 씀 (java_fx_art.save)
  python3 tools/job_fx_art_v2b.py --only rfx_jslash2     # 몇 개만 (쉼표로 여럿)
  python3 tools/job_fx_art_v2b.py --preview <폴더>       # 확인용 그림도 씀 (그림마다: 옛 그림 줄 · 새 그림 줄 (같은 배율) · 새 그림 한 장 2배)
  python3 tools/job_fx_art_v2b.py --check                # 쓴 그림을 옛 그림과 견줌 (보는 꼴 · 칸 크기 · 그림 크기 · 크기 식 · NaN · 칸 가장자리 알파 < 7% · 빈 칸)

꼴은 옛 그림과 똑같음 (게임 코드는 이름만 바꾸면 됨): 같은 facing_camera_mode · 같은 칸 크기 · 같은 칸 수 · 같은 열 수 (같은 그림 크기) · 칸마다 같은 뜻
(빛 장면 N 개 다음에 심지 장면 N 개 — rfx_jring2 는 그 뒤에 좌우를 뒤집은 장면 한 벌 (칸 2N ~ 4N-1)) · 같은 크기 식 · 칸 안의 같은 몸 범위
(게임이 크기를 거기서 셈 — 예: 고리 반지름 = 반 크기 × 0.88, 내려찍기 충격 고리 끝 = 반 크기 × 0.92).

그리기는 tools/job_fx_art_v2.py 와 같음 (그 도움 함수를 가져다 씀): 4배로 그려 LANCZOS 로 줄임 · 빛 (넓고 흐린 겉) 과 심지 (가늘고 또렷한 속) 를 같은 모양에서
그림 · 흰색 (밝기 = 알파) · 고정 시드 · 칸 가장자리 3 점 알파를 0 으로. 다른 그림을 쓰지 않음 (모두 이 스크립트가 처음부터 그림 · 글자 꼴은 지어낸 각진 글자).
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import OUT, save  # noqa: E402 — 같은 꼴의 파티클 정의 · tools/java_fx 에 씀
from job_fx_art_v2 import (BG, COLORS, SS, TAU, Pad, Stack, Xf, bevel_light, bez, bolt_pts, clamp01, ease_in_out, ease_out, edge_fade,  # noqa: E402
                           gblur, glyph_mask, inner_dist, label, lerp, rim_of, rot_pts, screen, smooth, sparkle, star_pts, taper_poly, to_sheet)

TEX = os.path.join(OUT, "textures", "particle", "rpg", "fx")
PART = os.path.join(OUT, "particles", "rpg", "fx")


# ============================================================ 도움 (v2 에 없는 것)

def polar(pad, cx, cy):
    """칸 점 단위 반지름 · 각 (라디안, 화면에서 시계 방향이 +)"""
    X, Y = pad.grid()
    return np.sqrt((X - cx) ** 2 + (Y - cy) ** 2), np.arctan2(Y - cy, X - cx)


def wrap(a):
    """각을 (-π, π] 로"""
    return (a + math.pi) % TAU - math.pi


def behind(th, head, direction=1.0):
    """각 th 가 머리 head 에서 (도는 쪽의) 뒤로 얼마나 떨어졌나 (0 ~ 2π) — direction 1 = 화면 시계 방향으로 돎"""
    return np.mod((head - th) * direction, TAU)


def gauss(x, s):
    return np.exp(-0.5 * (x / s) ** 2)


def band_cov(d, half, ss=SS):
    """|d| < half 인 띠 (4배에서 매끈한 가장자리)"""
    return clamp01((half - np.abs(d)) * ss + 0.5)


def win_border(shape, m=6.0, p=1.5):
    """칸 가장자리 m 점 안에서 0 → 1 (안전망 — 그림은 이보다 안에 그림)"""
    h, w = shape
    ys = np.arange(h) + 0.5
    xs = np.arange(w) + 0.5
    wy = smooth(0.0, m, np.minimum(ys, h - ys))
    wx = smooth(0.0, m, np.minimum(xs, w - xs))
    return (wy[:, None] * wx[None, :]).astype(np.float32) ** p


def finish(pad, glow_ss, core_ss, halo=((1.5, 0.5), (4.0, 0.4), (9.0, 0.25)), body_k=0.85, core_bloom=((1.5, 0.35), (4.0, 0.28)), win=5.0, glow1_extra=None):
    """연출 그림의 빛 · 심지 (1배): 빛 = 넓은 모양 + 여러 겹 무리 + 심지 둘레 번짐, 칸 가장자리 안전망"""
    core1 = pad.down(core_ss)
    g = pad.down(glow_ss)
    out = g * body_k
    for r, k in halo:
        out = screen(out, gblur(g, r) * k)
    for r, k in core_bloom:
        out = screen(out, gblur(core1, r) * k)
    if glow1_extra is not None:
        out = screen(out, glow1_extra)
    if win:
        wb = win_border(out.shape, win)
        out = out * wb
        core1 = core1 * wb
    return clamp01(out), clamp01(core1)


def streak(pad, p0, p1, w, a=1.0, head_w=None, tail_a=0.0):
    """불티 줄: p0 (꼬리 — 가늘고 흐림) → p1 (머리 — 굵고 밝음) + 머리 점"""
    hw = w if head_w is None else head_w
    out = pad.stroke([p0, p1], w * 0.15, w, a0=a * tail_a, a1=a)
    return np.maximum(out, pad.disc_field(p1[0], p1[1], hw * 0.55) * a)


def lens(pad, p0, p1, w, a=1.0, power=0.6, n=24, skew=0.5):
    """양 끝이 뾰족한 렌즈 (p0 → p1, 가운데 폭 w) — skew = 가장 굵은 자리 (0 ~ 1)"""
    pts = [(p0[0] + (p1[0] - p0[0]) * i / n, p0[1] + (p1[1] - p0[1]) * i / n) for i in range(n + 1)]

    def wf(t):
        if t <= skew:
            u = t / max(1e-6, skew)
        else:
            u = (1 - t) / max(1e-6, 1 - skew)
        return w / 2 * math.sin(math.pi / 2 * u) ** power
    return pad.poly(taper_poly(pts, wf), a)


def arc_pts(cx, cy, r, a0, a1, n=48, ry=None, tilt=0.0):
    ry = r if ry is None else ry
    ct, st = math.cos(tilt), math.sin(tilt)
    out = []
    for i in range(n + 1):
        a = a0 + (a1 - a0) * i / n
        x, y = math.cos(a) * r, math.sin(a) * ry
        out.append((cx + x * ct - y * st, cy + x * st + y * ct))
    return out


def star4(pad, x, y, r, a=1.0, rot=0.0, thin=0.12, diag=0.45, core=True):
    """반짝임 (v2 sparkle 와 같음 — 이름만 짧게)"""
    if r <= 0.3 or a <= 0:
        return pad.zeros()
    return sparkle(pad, x, y, r, a, rot=rot, thin=thin, diag=diag, core=core)


def star5_pts(cx, cy, r, inner=0.45, rot=0.0):
    pts = []
    for i in range(10):
        a = rot - math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


# ---- 값 노이즈 (주기를 줄 수 있음 — 이어 돌리는 그림의 흐름)

def _lattice(seed, n=256):
    rnd = np.random.RandomState(seed)
    ang = rnd.uniform(0, TAU, (n, n)).astype(np.float32)
    return np.cos(ang), np.sin(ang)


_LAT = {}


def perlin(X, Y, seed=0, px=None, py=None):
    """2D 그라디언트 노이즈 (-1 ~ 1 쯤). px · py = 그 축의 주기 (격자 칸 수 — 정수)"""
    if seed not in _LAT:
        _LAT[seed] = _lattice(seed)
    gx, gy = _LAT[seed]
    n = gx.shape[0]
    x0 = np.floor(X).astype(np.int64)
    y0 = np.floor(Y).astype(np.int64)
    fx = (X - x0).astype(np.float32)
    fy = (Y - y0).astype(np.float32)

    def idx(ix, iy):
        if px:
            ix = np.mod(ix, px)
        if py:
            iy = np.mod(iy, py)
        return np.mod(ix, n), np.mod(iy, n)

    def dot(ix, iy, dx, dy):
        a, b = idx(ix, iy)
        return gx[b, a] * dx + gy[b, a] * dy

    n00 = dot(x0, y0, fx, fy)
    n10 = dot(x0 + 1, y0, fx - 1, fy)
    n01 = dot(x0, y0 + 1, fx, fy - 1)
    n11 = dot(x0 + 1, y0 + 1, fx - 1, fy - 1)
    ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
    uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
    a = n00 + (n10 - n00) * ux
    b = n01 + (n11 - n01) * ux
    return (a + (b - a) * uy) * 1.41


def fbm(X, Y, seed=0, octaves=4, px=None, py=None, gain=0.5, lac=2.0):
    """여러 겹 노이즈 (주기 px · py 는 첫 겹의 격자 칸 수 — 겹마다 lac 배)"""
    out = np.zeros_like(X, dtype=np.float32)
    amp, f, tot = 1.0, 1.0, 0.0
    for o in range(octaves):
        out += amp * perlin(X * f, Y * f, seed + o * 17, None if px is None else int(round(px * f)), None if py is None else int(round(py * f)))
        tot += amp
        amp *= gain
        f *= lac
    return out / tot



def polyline_len(pts):
    return sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:]))


def cut_polyline(pts, frac):
    """줄의 앞 frac (0 ~ 1) 만 (길이 기준)"""
    if frac >= 1:
        return list(pts)
    total = polyline_len(pts)
    want = total * max(0.0, frac)
    out = [pts[0]]
    acc = 0.0
    for a, b in zip(pts, pts[1:]):
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        if acc + L >= want:
            k = (want - acc) / L if L > 0 else 0
            out.append((a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k))
            return out
        out.append(b)
        acc += L
    return out


def chunk_pts(rnd, x, y, r, n=None, rot=0.0):
    """울퉁불퉁한 돌 조각 (점 n 개)"""
    n = n or rnd.randint(5, 7)
    base = rnd.uniform(0, TAU)
    pts = []
    for i in range(n):
        a = base + i * TAU / n + rnd.uniform(-0.25, 0.25)
        rr = r * rnd.uniform(0.62, 1.0)
        pts.append((x + math.cos(a + rot) * rr, y + math.sin(a + rot) * rr))
    return pts


def faceted(pad, pts, light=(-0.55, -0.83), a=1.0, depth=None):
    """조각 (모양 · 빛 몸 · 심지): 비탈 빛으로 면이 보이게"""
    m = pad.poly(pts)
    if depth is None:
        xs = [p[0] for p in pts]
        depth = max(0.8, (max(xs) - min(xs)) * 0.28)
    lit, dark, _ = bevel_light(m, depth, light)
    body = m * (0.78 + 0.2 * lit - 0.25 * dark) * a
    core = (rim_of(m, 0.6) * 0.8 + lit * 0.75 + m * 0.15) * m * a
    return m, body, core


# ============================================================ 그림 목록 · 쓰기 · 확인용 그림 · 검사

class Spec:
    def __init__(self, name, facing, cw, ch, frames, cols, job, fn, mirror=False, zoom=0, colors=None, desc=""):
        self.name, self.facing, self.cw, self.ch, self.frames, self.cols = name, facing, cw, ch, frames, cols
        self.job, self.fn, self.mirror, self.zoom, self.colors, self.desc = job, fn, mirror, zoom, colors, desc

    @property
    def old(self):
        return self.name[:-1]


SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, job="swordsman", mirror=False, zoom=0, colors=None, desc=""):
    def deco(fn):
        SPECS[name] = Spec(name, facing, cw, ch, frames, cols, job, fn, mirror, zoom, colors, desc)
        return fn
    return deco


def render(sp):
    """(빛 칸들, 심지 칸들) 한 벌씩 — 뒤집은 벌이 있으면 [[빛, 심지], [빛 (뒤집음), 심지 (뒤집음)]]"""
    glows, cores = [], []
    for f in range(sp.frames):
        g, c = sp.fn(f)
        assert g.shape == (sp.ch, sp.cw) and c.shape == (sp.ch, sp.cw), (sp.name, f, g.shape, c.shape)
        assert np.isfinite(g).all() and np.isfinite(c).all(), (sp.name, f, "NaN")
        glows.append(clamp01(g).astype(np.float32))
        cores.append(clamp01(c).astype(np.float32))
    sets = [(glows, cores)]
    if sp.mirror:
        sets.append(([np.ascontiguousarray(g[:, ::-1]) for g in glows], [np.ascontiguousarray(c[:, ::-1]) for c in cores]))
    return sets


def write(sp, sets):
    cells = []
    for g, c in sets:
        cells += list(g) + list(c)
    img = to_sheet(cells, sp.cols, sp.cw, sp.ch)
    assert img.size[0] <= 4096 and img.size[1] <= 4096, (sp.name, img.size)
    save(sp.name, img, sp.cw, sp.ch, sp.facing)
    return img


def old_cells(name):
    p = os.path.join(TEX, f"{name}.png")
    j = os.path.join(PART, f"{name}.particle.json")
    if not (os.path.exists(p) and os.path.exists(j)):
        return None
    bb = json.load(open(j))["particle_effect"]["components"]["minecraft:particle_appearance_billboard"]
    cw, ch = bb["uv"]["uv_size"]
    a = np.asarray(Image.open(p).convert("RGBA"), np.float32)[..., 3] / 255.0
    cols, rows = a.shape[1] // cw, a.shape[0] // ch
    return [a[r * ch:(r + 1) * ch, k * cw:(k + 1) * cw] for r in range(rows) for k in range(cols)]


def _strip(cells_g, cells_c, ca, cb, scale, gap, title, labels=True):
    cw, ch = cells_g[0].shape[1], cells_g[0].shape[0]
    w, h = max(1, int(cw * scale)), max(1, int(ch * scale))
    n = len(cells_g)
    out = Image.new("RGB", (n * (w + gap) + gap, h + gap + 14), tuple(int(v * 255) for v in BG))
    label(out, title, (gap, 1))
    for i in range(n):
        im = tint_comp_scaled(cells_g[i], cells_c[i], ca, cb, w, h)
        out.paste(im, (gap + i * (w + gap), 13))
        if labels:
            label(out, str(i), (gap + i * (w + gap) + 2, 14))
    return out


def tint_comp_scaled(g, c, ca, cb, w, h):
    gg = edge_fade(g)[..., None]
    cc = edge_fade(c)[..., None]
    out = BG[None, None, :] * (1 - gg) + np.array(ca, np.float32)[None, None, :] * gg
    out = out * (1 - cc) + np.array(cb, np.float32)[None, None, :] * cc
    im = Image.fromarray((clamp01(out) * 255 + 0.5).astype(np.uint8), "RGB")
    if im.size != (w, h):
        im = im.resize((w, h), Image.BICUBIC if w > im.size[0] else Image.LANCZOS)
    return im


def preview(sp, sets, folder):
    """옛 그림 줄 (있으면) · 새 그림 줄 (같은 배율 — 몸 범위를 견줌) · 뒤집은 벌 · 새 그림 한 장 2배"""
    ca, cb = sp.colors or COLORS[sp.job]
    cw, ch = sp.cw, sp.ch
    gap = 6
    scale = min(1.0, 1500.0 / (sp.frames * (cw + gap)))
    blocks = []
    old = old_cells(sp.old)
    n = sp.frames
    if old is not None:
        for s in range(len(old) // (2 * n)):
            blocks.append(_strip(old[s * 2 * n: s * 2 * n + n], old[s * 2 * n + n: s * 2 * n + 2 * n], ca, cb, scale, gap, f"OLD {sp.old} set {s}"))
    for s, (g, c) in enumerate(sets):
        blocks.append(_strip(g, c, ca, cb, scale, gap, f"NEW {sp.name} set {s}  {sp.facing} {cw}x{ch} x{n}"))
    zf = sp.zoom
    zoom = tint_comp_scaled(sets[0][0][zf], sets[0][1][zf], ca, cb, cw * 2, ch * 2)
    zb = Image.new("RGB", (zoom.size[0] + 2 * gap, zoom.size[1] + 16), tuple(int(v * 255) for v in BG))
    label(zb, f"frame {zf} x2", (gap, 1))
    zb.paste(zoom, (gap, 14))
    # 빛 / 심지 따로 (알파를 흰색으로) — 한 장
    gl = Image.fromarray((clamp01(edge_fade(sets[0][0][zf])) * 255).astype(np.uint8), "L").convert("RGB")
    co = Image.fromarray((clamp01(edge_fade(sets[0][1][zf])) * 255).astype(np.uint8), "L").convert("RGB")
    sep = Image.new("RGB", (cw * 2 + gap * 3, ch + 16), (0, 0, 0))
    label(sep, "glow alpha | core alpha", (gap, 1))
    sep.paste(gl, (gap, 14))
    sep.paste(co, (gap * 2 + cw, 14))
    blocks.append(zb)
    blocks.append(sep)
    W = max(b.size[0] for b in blocks)
    H = sum(b.size[1] for b in blocks)
    out = Image.new("RGB", (W, H), tuple(int(v * 255) for v in BG))
    y = 0
    for b in blocks:
        out.paste(b, (0, y))
        y += b.size[1]
    path = os.path.join(folder, f"{sp.name}.png")
    out.save(path)
    return path


def check(names):
    """쓴 그림 (tools/java_fx) 을 옛 그림과 견줌. 문제가 없으면 True"""
    ok_all = True
    for name in names:
        sp = SPECS[name]
        msgs = []
        jn = json.load(open(os.path.join(PART, f"{name}.particle.json")))
        jo = json.load(open(os.path.join(PART, f"{sp.old}.particle.json")))
        s_new = json.dumps(jn, sort_keys=True).replace(f"rfx_{name[4:]}", "X")
        s_old = json.dumps(jo, sort_keys=True).replace(f"rfx_{sp.old[4:]}", "X")
        if s_new != s_old:
            msgs.append("particle json differs (beyond the name)")
        bn = jn["particle_effect"]["components"]["minecraft:particle_appearance_billboard"]
        bo = jo["particle_effect"]["components"]["minecraft:particle_appearance_billboard"]
        for k in ("facing_camera_mode", "size"):
            if bn[k] != bo[k]:
                msgs.append(f"{k} differs: {bn[k]} vs {bo[k]}")
        if bn["uv"]["uv_size"] != bo["uv"]["uv_size"]:
            msgs.append("uv_size differs")
        im_n = Image.open(os.path.join(TEX, f"{name}.png"))
        im_o = Image.open(os.path.join(TEX, f"{sp.old}.png"))
        if im_n.size != im_o.size:
            msgs.append(f"texture size {im_n.size} vs {im_o.size}")
        a = np.asarray(im_n.convert("RGBA"), np.float32)
        if not np.isfinite(a).all():
            msgs.append("NaN")
        if (a[..., :3] != 255).any():
            msgs.append("RGB not white")
        cw, ch = bn["uv"]["uv_size"]
        al = a[..., 3] / 255.0
        cols, rows = al.shape[1] // cw, al.shape[0] // ch
        n_used = sp.frames * 2 * (2 if sp.mirror else 1)
        worst_border = 0.0
        empty = []
        for i in range(n_used):
            r, k = divmod(i, cols)
            c = al[r * ch:(r + 1) * ch, k * cw:(k + 1) * cw]
            border = max(c[0].max(), c[-1].max(), c[:, 0].max(), c[:, -1].max())
            worst_border = max(worst_border, border)
            if c.max() < 0.08:
                empty.append(i)
        if worst_border >= 0.07:
            msgs.append(f"border alpha {worst_border:.3f} >= 0.07")
        if empty:
            msgs.append(f"empty cells {empty}")
        if n_used != len(old_cells(sp.old) or []) and n_used > cols * rows:
            msgs.append("cell count")
        status = "OK " if not msgs else "BAD"
        ok_all &= not msgs
        print(f"{status} {name:16s} {sp.facing:16s} {cw}x{ch} tex {im_n.size[0]}x{im_n.size[1]} cells {n_used} border {worst_border:.3f}" + ("" if not msgs else "  <- " + "; ".join(msgs)))
    return ok_all


# ============================================================ 그림들

# ------------------------------------------------------------ rfx_jslash2 (눈앞 큰 베기 — 초승달이 아래로 불룩, 왼쪽 → 아래 → 오른쪽으로 그어짐)
# 몸 범위는 옛 그림과 같음: 타원 (가운데 128, 34.56 · 반지름 116.48 × 71.68) 의 172° → 8° 호가 바깥 날, 띠는 가운데 쪽으로.
# 참격 판 (tools/fx_models_card.py slash_job) 이 이 그림을 180° 돌려 붙임 — 같은 방향을 지킴.

SLASH = dict(cx=128.0, cy=128 * 0.27, rx=256 * 0.455, ry=128 * 0.56, a0=172.0, a1=8.0)
SL_HEAD = [0.29, 0.58, 0.86, 1.0, 1.0, 1.0, 1.0, 1.0]
SL_TAIL = [0.0, 0.0, 0.03, 0.19, 0.37, 0.56, 0.74, 0.9]
SL_WK = [0.92, 1.0, 1.0, 0.97, 0.84, 0.7, 0.56, 0.42]
SL_FADE = [1.0, 1.0, 1.0, 1.0, 0.97, 0.9, 0.74, 0.5]


def slash_P(u, inward=0.0):
    g = SLASH
    a = math.radians(g["a0"] + (g["a1"] - g["a0"]) * u)
    x, y = g["cx"] + math.cos(a) * g["rx"], g["cy"] + math.sin(a) * g["ry"]
    if inward:
        nx, ny = math.cos(a) / g["rx"], math.sin(a) / g["ry"]
        L = math.hypot(nx, ny)
        x, y = x - nx / L * inward, y - ny / L * inward
    return x, y


def slash_T(u):
    """호를 따라 머리 쪽 (u 가 커지는 쪽) 단위 방향"""
    x0, y0 = slash_P(u - 0.002)
    x1, y1 = slash_P(u + 0.002)
    L = math.hypot(x1 - x0, y1 - y0) or 1
    return (x1 - x0) / L, (y1 - y0) / L


def slash_fields(pad):
    """(u — 호 위의 자리 0 ~ 1, d — 바깥 날에서 안쪽으로의 거리 · 칸 점)"""
    X, Y = pad.grid()
    g = SLASH
    x = (X - g["cx"]) / g["rx"]
    y = (Y - g["cy"]) / g["ry"]
    rho = np.sqrt(x * x + y * y) + 1e-6
    gx = x / (g["rx"] * rho)
    gy = y / (g["ry"] * rho)
    d = (1 - rho) / np.sqrt(gx * gx + gy * gy)
    th = np.degrees(np.arctan2(y, x))
    u = (g["a0"] - th) / (g["a0"] - g["a1"])
    return u, d


def crescent_prof(s, tip=0.2, rise=0.85):
    """초승달 굵기 (꼬리 0 → 머리 1): 꼬리에서 천천히 굵어져 머리 앞 tip 몫에서 날카롭게 모임 (가장 굵은 곳 = 1)"""
    s = np.clip(s, 0.0, 1.0)
    tipf = np.sin(np.pi / 2 * np.clip((1 - s) / tip, 0, 1)) ** 0.7
    v = s ** rise * tipf
    return v / ((1 - tip) ** rise)


def swin(s, s0, s1, e0=0.12, e1=0.05):
    """s 가 s0 ~ s1 일 때 1 (양 끝을 부드럽게)"""
    return smooth(s0, s0 + e0, s) * smooth(s1, s1 - e1, s)


def slash_frame(f):
    W, H = 256, 128
    pad = Pad(W, H)
    u, d = slash_fields(pad)
    head, tail = SL_HEAD[f], SL_TAIL[f]
    span = head - tail
    s = (u - tail) / span
    on = (s > 0) & (s < 1)
    prof = crescent_prof(s, tip=0.15, rise=0.62) * on
    fade = SL_FADE[f]
    wk = SL_WK[f]
    w = 29.0 * wk * prof
    wg = 39.0 * wk * prof
    # ---- 띠 (바깥 날은 또렷 · 안쪽은 부드럽게)
    outer = clamp01((d + 0.15) * SS + 0.5)
    band = outer * smooth(w + 1.5, w - 3.0, d) * (prof > 0.004)
    t = np.clip(d / np.maximum(w, 0.4), 0, 1)
    # ---- 심지: 날 줄 (머리 쪽이 굵고 밝음) · 날 쪽이 단단히 밝고 안으로 흐려지는 몸 · 뒤처진 가는 잔상 줄 둘
    edge_w = 0.8 + 2.6 * prof
    edge = outer * np.exp(-np.maximum(d, 0) / edge_w) * (0.55 + 0.45 * np.sqrt(prof)) * band
    edge = np.maximum(edge, outer * gauss(np.maximum(d, 0), 0.55 + 0.3 * prof) * smooth(-0.02, 0.03, s) * smooth(1.0, 0.985, s) * (prof > 0.004))
    body = band * (1 - smooth(0.36, 1.0, t)) ** 1.05
    # 결: 몸 안의 아주 옅은 어두운 줄 (빛을 쓸어낸 자국)
    streaks = np.zeros_like(d)
    for tk, sk in ((0.3, 0.55), (0.46, 0.5), (0.62, 0.6)):
        streaks = np.maximum(streaks, gauss(t - tk, 0.035) * sk)
    body = body * (1 - 0.3 * streaks * smooth(0.0, 0.3, s))
    after = np.zeros_like(d)
    for dk, s0, s1, a in ((0.62, 0.12, 0.9, 0.85), (0.86, 0.3, 0.78, 0.55)):
        after = np.maximum(after, gauss(d - dk * w, 0.45 + 0.25 * prof) * swin(s, s0, s1, 0.2, 0.06) * a)
    core = np.maximum.reduce([edge, body, after * outer * (prof > 0.004)])
    # ---- 빛: 넓은 띠 (바깥으로 살짝 번짐) · 안쪽으로 옅게 쓸린 자리
    tg = np.clip(d / np.maximum(wg, 0.4), 0, 1.5)
    gband = smooth(-4.0, 0.6, d) * smooth(wg + 3.0, wg - 5.5, d) * (prof > 0.004) * (1 - 0.3 * np.clip(tg, 0, 1) ** 1.5)
    wake = 0.18 * np.exp(-np.maximum(d - wg, 0) / 13.0) * (d > wg * 0.6) * prof ** 0.8 * smooth(1.0, 0.9, s)
    glow = np.maximum(gband, wake)
    # ---- 바깥 (불룩한 쪽) 바람 줄 둘 — 머리 뒤 일부
    wl = [0.55, 1.0, 1.0, 0.75, 0.42, 0.18, 0.0, 0.0][f]
    if wl > 0:
        for off, s0, a in ((-5.0, 0.42, 0.62), (-9.5, 0.62, 0.38), (-13.5, 0.78, 0.2)):
            m = gauss(d - off, 0.5) * swin(s, s0, 0.985, 0.18, 0.03) * on
            core = np.maximum(core, m * a * wl)
            glow = np.maximum(glow, gauss(d - off, 1.8) * swin(s, s0, 0.985, 0.18, 0.03) * on * a * 0.5 * wl)
    # ---- 머리 번쩍 (그어지는 동안)
    fl = [0.8, 1.0, 0.92, 0.5, 0.26, 0.0, 0.0, 0.0][f]
    if fl > 0:
        hu = head - (0.012 if head < 1 else 0.03)
        hx, hy = slash_P(hu, inward=2.5)
        rr = min(19 * fl + 4, (W - 5 - hx) * 1.1)
        core = np.maximum(core, star4(pad, hx, hy, rr, 1.0, rot=0.12 + 0.1 * f, thin=0.1, diag=0.42))
        glow = np.maximum(glow, pad.radial(hx, hy, 0.8 * rr + 4, 1.4) * 0.9)
    # ---- 날 위의 반짝임 (머리가 지난 뒤 잠깐)
    for k, (ut, f0) in enumerate(((0.3, 1), (0.52, 2), (0.7, 2), (0.84, 3))):
        age = f - f0
        if age < 0 or age > 2 or ut < tail + 0.04:
            continue
        tw = [1.0, 0.62, 0.3][age]
        x, y = slash_P(ut, inward=-1.0 + 2.0 * (k % 2))
        core = np.maximum(core, star4(pad, x, y, (7.5 + 2.5 * (k % 2)) * tw, tw * fade, rot=0.25 * k, thin=0.14, diag=0.0))
        glow = np.maximum(glow, pad.disc_field(x, y, 3.5 * tw, soft=2.0) * tw * 0.8)
    # ---- 불티: 머리가 지난 자리의 날에서 바깥 · 앞으로 튀는 짧은 줄
    rnd = random.Random(101)
    sparks_c = pad.zeros()
    sparks_g = pad.zeros()
    for i in range(20):
        fs = rnd.choice((0, 1, 1, 2, 2, 3))
        u0 = max(0.04, min(0.99, SL_HEAD[fs] - rnd.uniform(0.0, 0.12)))
        tx, ty = slash_T(u0)
        nx, ny = ty, -tx  # 바깥 (아래쪽) 노멀 — 머리 쪽 방향을 시계로 90°
        mix = rnd.uniform(0.25, 0.85)
        vx, vy = tx * mix + nx * (1 - mix), ty * mix + ny * (1 - mix)
        L = math.hypot(vx, vy)
        vx, vy = vx / L, vy / L
        sp = rnd.uniform(9, 19)
        life = rnd.uniform(2.6, 4.0)
        age = f - fs + rnd.uniform(0.35, 0.7)
        if age <= 0 or age >= life:
            continue
        x0, y0 = slash_P(u0, inward=-0.5)
        dist = sp * age * (1 - 0.1 * age)
        x, y = x0 + vx * dist, y0 + vy * dist
        a = (1 - age / life) ** 1.2 * fade
        a *= smooth(3, 12, min(x, W - x, y, H - y))
        if a <= 0.02:
            continue
        L2 = min(dist, sp * 0.9)
        sw = rnd.uniform(1.2, 2.0)
        sparks_c = np.maximum(sparks_c, streak(pad, (x - vx * L2, y - vy * L2), (x, y), sw, a))
        sparks_g = np.maximum(sparks_g, pad.stroke([(x - vx * L2 * 1.2, y - vy * L2 * 1.2), (x, y)], sw * 0.6, sw * 3.2, a0=0.0, a1=a * 0.7, soft=1.0))
    core = np.maximum(core, sparks_c)
    glow = np.maximum(glow, sparks_g)
    # ---- 꼬리가 흩어지는 빛 가루 (꼬리가 지나간 자리)
    if tail > 0:
        rnd = random.Random(202)
        for i in range(34):
            ui = rnd.uniform(0.0, 0.98)
            if ui >= tail:
                continue
            q = (tail - ui) / 0.28
            if q >= 1:
                continue
            di = rnd.uniform(1.0, 16.0) * wk + rnd.uniform(-2, 6) * q
            x, y = slash_P(ui, inward=di)
            a = (1 - q) ** 1.4 * fade * rnd.uniform(0.5, 1.0)
            r = rnd.uniform(0.6, 1.25) * (1 - 0.35 * q)
            tx, ty = slash_T(ui)
            if i % 3 == 0:
                core = np.maximum(core, pad.stroke([(x - tx * 4, y - ty * 4), (x + tx * 2, y + ty * 2)], r * 0.6, r * 1.2, a0=0.0, a1=a))
            else:
                core = np.maximum(core, pad.disc_field(x, y, r) * a)
            glow = np.maximum(glow, pad.disc_field(x, y, r * 2.4, soft=1.2) * a * 0.6)
    core = core * fade
    glow = glow * fade
    return finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.4), (8.0, 0.24)), body_k=0.9, core_bloom=((1.3, 0.35), (3.5, 0.26)), win=4.0)


sprite("rfx_jslash2", "rotate_xyz", 256, 128, 8, 4, job="swordsman", zoom=3,
       desc="큰 베기 초승달 (아래로 불룩 · 왼쪽 → 아래 → 오른쪽): 날 줄 · 결 · 머리 번쩍 · 불티 · 바람 줄, 꼬리부터 흩어짐")(slash_frame)



# ------------------------------------------------------------ rfx_jring2 (땅에 눕는 한 바퀴 베기 — 12시에서 시계 방향, 고리 반지름 = 반 크기 × 0.88)
# 칸 0 ~ 7 빛 · 8 ~ 15 심지 (시계 방향) + 16 ~ 31 좌우를 뒤집은 같은 장면 (반시계 — 면을 아래로 눕히면 위에서 보이지 않아서)

RING_SWEEP = [0.2, 0.4, 0.6, 0.8, 1.0]
RING_AFTER = [0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.68, 0.36]


def ring_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    R = S * 0.44
    rr, th = polar(pad, c, c)
    d = R - rr  # 안쪽 + (날 = 0)
    outer = clamp01((d + 0.15) * SS + 0.5)
    core = pad.zeros()
    glow = pad.zeros()
    after = RING_AFTER[f]
    if after == 0:
        sweep = RING_SWEEP[f]
        head = -math.pi / 2 + TAU * sweep
        length = TAU * min(0.62, sweep)
        b = behind(th, head)
        s = np.clip(1 - b / length, 0, 1)
        on = (b < length)
        prof = crescent_prof(s, tip=0.045, rise=1.0) * on
        w = 15.0 * prof
        wg = 22.0 * prof
        band = outer * smooth(w + 1.2, w - 2.4, d) * (prof > 0.004)
        t = np.clip(d / np.maximum(w, 0.4), 0, 1)
        edge = outer * np.exp(-np.maximum(d, 0) / (0.7 + 1.8 * prof)) * (0.5 + 0.5 * np.sqrt(prof)) * band
        edge = np.maximum(edge, outer * gauss(np.maximum(d, 0), 0.6) * smooth(0.0, 0.05, s) * on * smooth(1.0, 0.99, s))
        body = band * (1 - smooth(0.3, 1.0, t)) ** 1.1
        aft = np.zeros_like(d)
        for dk, s0, s1, a in ((0.6, 0.1, 0.93, 0.8), (0.88, 0.3, 0.82, 0.5)):
            aft = np.maximum(aft, gauss(d - dk * w, 0.45 + 0.2 * prof) * swin(s, s0, s1, 0.2, 0.05) * a)
        core = np.maximum.reduce([edge, body, aft * outer * (prof > 0.004)])
        tg = np.clip(d / np.maximum(wg, 0.4), 0, 1)
        glow = smooth(-3.5, 0.6, d) * smooth(wg + 3.0, wg - 4.5, d) * (prof > 0.004) * (1 - 0.3 * tg ** 1.5)
        # 쓸린 자리 (고리 안쪽 부채꼴 — 옅게)
        fan = 0.13 * smooth(R * 0.3, R * 0.92, rr) * (rr < R) * s ** 1.6 * on
        glow = np.maximum(glow, fan)
        # 바깥 · 안쪽 바람 줄 (머리 뒤 일부)
        for off, s0, a in ((-4.0, 0.55, 0.55), (20.0, 0.45, 0.4), (27.0, 0.7, 0.28)):
            m = gauss(d - off, 0.5) * swin(s, s0, 0.985, 0.2, 0.02) * on
            core = np.maximum(core, m * a)
            glow = np.maximum(glow, gauss(d - off, 1.6) * swin(s, s0, 0.985, 0.2, 0.02) * on * a * 0.45)
        # 머리 번쩍
        hx, hy = c + math.cos(head - 0.035) * (R - 3.0), c + math.sin(head - 0.035) * (R - 3.0)
        k = [0.8, 1.0, 1.0, 1.0, 0.85][f]
        core = np.maximum(core, star4(pad, hx, hy, 15 * k + 3, 1.0, rot=head, thin=0.1, diag=0.42))
        glow = np.maximum(glow, pad.radial(hx, hy, 13 * k + 4, 1.4) * 0.9)
        # 불티: 날에서 접선 쪽 (도는 쪽 앞) 으로 튐 — 고리 가까이에 머묾
        rnd = random.Random(303)
        for i in range(18):
            fs = rnd.randint(0, 4)
            age = f - fs + rnd.uniform(0.3, 0.8)
            life = rnd.uniform(2.0, 3.2)
            if age <= 0 or age >= life:
                continue
            a0 = -math.pi / 2 + TAU * RING_SWEEP[fs] - rnd.uniform(0.0, 0.5)
            px, py = c + math.cos(a0) * (R - rnd.uniform(0, 4)), c + math.sin(a0) * (R - rnd.uniform(0, 4))
            tx, ty = -math.sin(a0), math.cos(a0)
            inn = rnd.uniform(-0.25, 0.35)
            vx, vy = tx - math.cos(a0) * inn, ty - math.sin(a0) * inn
            L = math.hypot(vx, vy)
            vx, vy = vx / L, vy / L
            dist = rnd.uniform(9, 17) * age
            x, y = px + vx * dist, py + vy * dist
            a = (1 - age / life) ** 1.2 * smooth(3, 10, min(x, S - x, y, S - y))
            if a < 0.02:
                continue
            Ls = min(dist, 12.0)
            sw = rnd.uniform(1.1, 1.8)
            core = np.maximum(core, streak(pad, (x - vx * Ls, y - vy * Ls), (x, y), sw, a))
            glow = np.maximum(glow, pad.stroke([(x - vx * Ls, y - vy * Ls), (x, y)], 0.5, sw * 3.0, a0=0.0, a1=a * 0.6, soft=1.0))
    else:
        # 다 돈 뒤: 고리 전체가 빛나다 가늘어지며 사라짐 (12시 쪽 — 끝난 자리 — 이 조금 밝음) · 반짝임 · 흩어지는 빛 가루
        k = after
        top = 0.5 + 0.5 * np.cos(wrap(th + math.pi / 2)) ** 2 * (np.cos(wrap(th + math.pi / 2)) > 0)
        w = 7.5 * (0.45 + 0.55 * k)
        band = outer * smooth(w + 1.0, w - 2.0, d)
        t = np.clip(d / w, 0, 1)
        ripple = 0.85 + 0.15 * np.cos(th * 9 + f * 1.3)
        core = (outer * np.exp(-np.maximum(d, 0) / 1.4) * band * (0.75 + 0.25 * top) + band * (1 - smooth(0.2, 1.0, t)) * 0.7 * ripple) * k
        glow = smooth(-3.0, 0.5, d) * smooth(w + 7.0, w - 2.0, d) * (0.55 + 0.45 * top) * k
        glow = np.maximum(glow, 0.1 * smooth(R * 0.4, R * 0.95, rr) * (rr < R) * k)
        rnd = random.Random(404 + f)
        for i in range(10):
            a0 = rnd.uniform(0, TAU)
            x, y = c + math.cos(a0) * (R - 1.5), c + math.sin(a0) * (R - 1.5)
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(4.0, 8.5) * (0.5 + 0.5 * k), k, rot=rnd.uniform(0, 0.8), thin=0.14, diag=0.0))
        rnd = random.Random(505)
        for i in range(26):
            a0 = rnd.uniform(0, TAU)
            dr = rnd.uniform(-4, 10) + (f - 4) * rnd.uniform(1.5, 4.0)
            x, y = c + math.cos(a0) * (R - dr), c + math.sin(a0) * (R - dr)
            a = k * rnd.uniform(0.4, 1.0)
            core = np.maximum(core, pad.disc_field(x, y, rnd.uniform(0.6, 1.2)) * a)
            glow = np.maximum(glow, pad.disc_field(x, y, 2.6, soft=1.2) * a * 0.5)
    return finish(pad, glow, core, halo=((1.3, 0.5), (3.2, 0.4), (7.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jring2", "direction_z", 192, 192, 8, 8, job="swordsman", mirror=True, zoom=2,
       desc="땅에 눕는 한 바퀴 베기: 12시에서 시계 방향으로 도는 날 (머리 번쩍 · 잔상 줄 · 접선으로 튀는 불티) → 다 돌면 고리 전체가 빛나다 사라짐 · 칸 16 ~ 31 좌우 뒤집음")(ring_frame)



# ------------------------------------------------------------ rfx_jburst2 (터짐 — 카메라를 봄)

def burst_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    p = f / 7
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 충격 고리: 밝은 앞 테 + 안쪽으로 흐려지는 꼬리 (점점 가늘고 흐림)
    r = S * (0.1 + 0.34 * ease_out(p, 2.2))
    ra = (1 - p ** 1.35) * (0.25 if f == 7 else 1.0) + (0.12 if f == 7 else 0.0)
    w = 15.0 * (1 - 0.72 * p) + 2.0
    d = r - rr
    ring_in = smooth(w + 2.0, 0.0, d) * clamp01((d + 0.2) * SS + 0.5)
    rim = gauss(d - 0.8, 0.75 + 0.5 * (1 - p))
    ripple = 0.88 + 0.12 * np.cos(th * 11 + f * 0.9)
    core = np.maximum(core, (rim * 0.95 + ring_in ** 1.8 * 0.55 * ripple) * ra)
    glow = np.maximum(glow, (smooth(-4.0, 0.5, d) * smooth(w + 6.0, 0.0, d) * 0.9) * ra)
    # 둘째 고리 (조금 늦게 · 가늘게)
    if 1 <= f <= 6:
        r2 = S * (0.06 + 0.3 * ease_out((f - 1) / 6, 2.0))
        a2 = (1 - (f - 1) / 6) ** 1.4 * 0.7
        core = np.maximum(core, gauss(rr - r2, 0.6) * a2)
        glow = np.maximum(glow, gauss(rr - r2, 2.6) * a2 * 0.6)
    # ---- 가운데 번쩍 (처음 넷)
    fk = [1.0, 0.72, 0.42, 0.18, 0.0, 0.0, 0.0, 0.0][f]
    if fk > 0:
        core = np.maximum(core, pad.disc_field(c, c, 15 * fk + 3, soft=2.5 + 3 * (1 - fk)) * min(1.0, fk * 1.4))
        core = np.maximum(core, star4(pad, c, c, 70 * fk + 12, min(1.0, fk * 1.3), rot=0.0, thin=0.06, diag=0.5))
        glow = np.maximum(glow, pad.radial(c, c, 44 * fk + 16, 1.2) * fk)
    # ---- 빛 갈래 여덟 (가운데에서 뻗어 고리와 함께 나가며 가늘어짐 — 0 ~ 4)
    if f <= 4:
        q = f / 4
        for i in range(8):
            a = i * TAU / 8 + math.pi / 8 * (i % 2) * 0.0 + (math.pi / 8 if i % 2 else 0.0)
            long_ = i % 2 == 0
            r0 = S * (0.04 + 0.3 * ease_out(q, 1.8))
            L = S * (0.26 if long_ else 0.16) * (1 - 0.55 * q)
            r1 = min(r0 + L, S * 0.45)
            ww = (5.0 if long_ else 3.4) * (1 - 0.6 * q)
            al = 1 - 0.75 * q
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * r1, c + math.sin(a) * r1)
            core = np.maximum(core, lens(pad, p0, p1, ww, al, power=0.7, skew=0.3))
            glow = np.maximum(glow, lens(pad, p0, p1, ww * 2.6 + 3, al * 0.85, power=0.8, skew=0.3))
    # ---- 튀는 불티 (줄 꼬리) · 남는 빛 가루
    rnd = random.Random(23)
    for i in range(22):
        a = rnd.uniform(0, TAU)
        sp = rnd.uniform(0.55, 1.0)
        dist = S * (0.06 + 0.36 * sp * ease_out(min(1.0, (f + 0.7) / 6.5), 2.0))
        dist = min(dist, S * 0.44)
        prev = S * (0.06 + 0.36 * sp * ease_out(min(1.0, max(0.0, f - 0.3) / 6.5), 2.0))
        x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
        al = (1 - p) ** 1.1 * rnd.uniform(0.6, 1.0) + (0.15 if f == 7 else 0.0)
        tail = max(2.0, (dist - prev) * 1.3)
        sw = rnd.uniform(1.0, 1.9) * (1 - 0.4 * p)
        if i % 3:
            core = np.maximum(core, streak(pad, (x - math.cos(a) * tail, y - math.sin(a) * tail), (x, y), sw, al))
            glow = np.maximum(glow, pad.stroke([(x - math.cos(a) * tail, y - math.sin(a) * tail), (x, y)], 0.5, sw * 3, a0=0.0, a1=al * 0.6, soft=1.0))
        else:
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(3.5, 6.5) * (1 - 0.4 * p), al, rot=rnd.uniform(0, 1), thin=0.16, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(x, y, 3.0, soft=1.5) * al * 0.7)
    return finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.4), (8.0, 0.24)), body_k=0.85, core_bloom=((1.3, 0.35), (3.5, 0.28)), win=5.0)


sprite("rfx_jburst2", "rotate_xyz", 192, 192, 8, 4, job="warrior", zoom=1,
       desc="터짐: 가운데 번쩍 (긴 네 뿔 별) · 밝은 앞 테의 충격 고리 둘 · 빛 갈래 여덟 · 튀는 불티 → 빛 가루")(burst_frame)


# ------------------------------------------------------------ rfx_jsmash2 (땅에 눕는 내려찍기 — 충격 고리 끝 = 반 크기 × 0.92)

def smash_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    p = f / 7
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 충격 고리 (바깥 끝이 옛 그림과 같은 자리: 0.1 → 0.46 S)
    r_edge = S * (0.1 + 0.36 * ease_out(min(1.0, (f + 0.6) / 5.0)))
    ra = (1 - p ** 1.3) * (1.0 if f < 7 else 0.0)
    w = 20.0 * (1 - 0.7 * p) + 2.5
    d = r_edge - rr
    if ra > 0:
        rim = gauss(d - 1.2, 0.8 + 0.5 * (1 - p))
        dust = smooth(w + 3.0, 0.5, d) * clamp01((d + 0.2) * SS + 0.5)
        mott = 0.75 + 0.25 * np.cos(th * 17 + 3 * np.sin(th * 5)) * np.cos(rr * 0.35)
        core = np.maximum(core, (rim * 0.95 + dust ** 2.2 * 0.45 * mott) * ra)
        glow = np.maximum(glow, (smooth(-3.0, 0.8, d) * smooth(w + 7.0, 0.0, d) * (0.6 + 0.4 * mott)) * ra)
    # ---- 가운데: 맞은 자리 번쩍 · 움푹 팬 자리 (방사 결)
    fk = [1.0, 0.65, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0][f]
    if fk > 0:
        core = np.maximum(core, pad.disc_field(c, c, 20 * fk + 4, soft=3 + 4 * (1 - fk)) * fk)
        core = np.maximum(core, star4(pad, c, c, 48 * fk + 10, fk, rot=0.4, thin=0.08, diag=0.5))
        glow = np.maximum(glow, pad.radial(c, c, 46 * fk + 14, 1.2) * fk)
    crater = [0.9, 1.0, 0.92, 0.8, 0.66, 0.5, 0.34, 0.2][f]
    spokes = (0.5 + 0.5 * np.cos(th * 23 + 2.0 * np.sin(th * 7))) ** 3
    cr = smooth(22.0, 8.0, rr)
    core = np.maximum(core, cr * spokes * 0.5 * crater * smooth(2, 8, rr))
    core = np.maximum(core, gauss(rr - 15.0, 1.0) * 0.5 * crater)
    glow = np.maximum(glow, smooth(26.0, 4.0, rr) * 0.75 * crater)
    # ---- 갈라지는 금 일곱 (+ 잔가지): 가운데가 굵고 끝으로 가늘어짐, 처음 넷 장에 뻗음
    grow = ease_out(min(1.0, (f + 1) / 3.6), 2.0)
    fade = [1.0, 1.0, 1.0, 1.0, 0.92, 0.78, 0.6, 0.4][f]
    rnd = random.Random(42)
    cracks_c = pad.zeros()
    cracks_g = pad.zeros()
    for i in range(7):
        ang = i * TAU / 7 + rnd.uniform(-0.3, 0.3)
        L = S * rnd.uniform(0.3, 0.41)
        p1 = (c + math.cos(ang) * L, c + math.sin(ang) * L)
        main = bolt_pts(rnd, (c + math.cos(ang) * 7, c + math.sin(ang) * 7), p1, 0.12, 5, 0.55)
        vis = cut_polyline(main, grow)
        if len(vis) >= 2:
            k = polyline_len(vis) / max(1, polyline_len(main))
            cracks_c = np.maximum(cracks_c, pad.stroke(vis, 2.6, 2.6 - 2.0 * k, a0=1.0, a1=0.75))
            cracks_g = np.maximum(cracks_g, pad.stroke(vis, 8.0, 8.0 - 5.0 * k, a0=0.9, a1=0.5, soft=2.0))
        for b in range(2):
            j = rnd.randint(len(main) // 4, len(main) * 2 // 3)
            bx, by = main[j]
            ba = ang + rnd.choice((-1, 1)) * rnd.uniform(0.45, 0.85)
            bl = L * rnd.uniform(0.22, 0.42)
            br = bolt_pts(rnd, (bx, by), (bx + math.cos(ba) * bl, by + math.sin(ba) * bl), 0.18, 4, 0.55)
            tj = j / len(main)
            gb = clamp01((grow - tj) / max(0.05, 1 - tj))
            vb = cut_polyline(br, gb)
            if len(vb) >= 2 and gb > 0.02:
                cracks_c = np.maximum(cracks_c, pad.stroke(vb, 1.5, 0.5, a0=0.9, a1=0.5))
                cracks_g = np.maximum(cracks_g, pad.stroke(vb, 5.0, 2.0, a0=0.7, a1=0.25, soft=1.5))
    core = np.maximum(core, cracks_c * fade)
    glow = np.maximum(glow, cracks_g * fade)
    # ---- 튀는 돌조각 (돌며 바깥으로) · 먼지 덩이
    rnd = random.Random(77)
    for i in range(15):
        a = rnd.uniform(0, TAU)
        sp = rnd.uniform(0.6, 1.0)
        dist = S * (0.08 + 0.32 * sp * ease_out(min(1.0, (f + 0.5) / 6.0), 1.8))
        dist = min(dist, S * 0.42)
        x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
        sz = rnd.uniform(3.0, 6.0) * (1 - 0.35 * p)
        al = (1 - p) ** 1.2 + (0.12 if f == 7 else 0)
        pts = chunk_pts(random.Random(900 + i), x, y, sz, rot=rnd.uniform(0, TAU) + p * rnd.uniform(2, 5))
        m, body, cc = faceted(pad, pts, a=al)
        core = np.maximum(core, cc)
        glow = np.maximum(glow, body)
        # 지나온 자리의 옅은 줄
        tl = min(dist * 0.4, 14)
        glow = np.maximum(glow, pad.stroke([(x - math.cos(a) * tl, y - math.sin(a) * tl), (x, y)], 0.5, sz * 1.2, a0=0.0, a1=al * 0.4, soft=1.5))
    if 1 <= f <= 6:
        rnd = random.Random(88)
        dk = [0, 0.5, 0.75, 0.7, 0.55, 0.38, 0.22, 0][f]
        for i in range(14):
            a = rnd.uniform(0, TAU)
            dist = min(r_edge - rnd.uniform(4, 14), S * 0.4)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            rad = rnd.uniform(6, 11) * (0.8 + 0.4 * p)
            glow = np.maximum(glow, pad.disc_field(x, y, rad, soft=rad * 0.8) * dk * 0.55)
            core = np.maximum(core, pad.disc_field(x, y, rad * 0.6, soft=rad * 0.6) * dk * 0.12)
    return finish(pad, glow, core, halo=((1.4, 0.48), (3.5, 0.38), (8.0, 0.22)), body_k=0.85, core_bloom=((1.3, 0.35), (3.5, 0.26)), win=4.0)


sprite("rfx_jsmash2", "direction_z", 192, 192, 8, 4, job="warrior", zoom=2,
       desc="땅에 눕는 내려찍기: 번쩍 · 방사 결이 있는 팬 자리 · 바깥 끝 0.46 S 의 충격 고리 · 빛나는 금 일곱 + 잔가지 · 비탈 빛 돌조각 · 먼지")(smash_frame)


# ------------------------------------------------------------ rfx_jwhirl2 (땅에 눕는 회전 칼날 — 칼날 반지름 = 반 크기 × 0.8, 이어 돌림: 8 장 = 120°)

def whirl_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    R = S * 0.4
    Re = R + 6.0  # 날 끝 (바깥 날)
    base = TAU / 3 * f / 8
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 안쪽: 도는 판의 옅은 결 (세 겹 대칭으로 돎) · 가운데 고리
    rnd = random.Random(9)
    for i in range(4):
        r0 = rnd.uniform(0.34, 0.72) * R
        a0 = rnd.uniform(0, TAU / 3)
        ln = rnd.uniform(1.3, 1.9)
        al = rnd.uniform(0.18, 0.34)
        for k in range(3):
            head = base + a0 + k * TAU / 3
            b = behind(th, head)
            m = gauss(rr - r0, 0.55) * clamp01(1 - b / ln) ** 1.5 * (b < ln)
            core = np.maximum(core, m * al)
            glow = np.maximum(glow, gauss(rr - r0, 2.0) * clamp01(1 - b / ln) ** 1.2 * (b < ln) * al * 0.5)
    glow = np.maximum(glow, gauss(rr - R * 0.62, 3.0) * 0.18)
    core = np.maximum(core, gauss(rr - R * 0.62, 0.55) * 0.22)
    # ---- 칼날 셋: 머리 쪽 단단한 초승달 날 (비탈 · 갈아낸 날) + 뒤로 흐려지는 잔상
    trail = math.radians(118)
    solid = math.radians(40)
    for k in range(3):
        head = base + k * TAU / 3
        b = behind(th, head)
        s = np.clip(1 - b / trail, 0, 1)
        on = b < trail
        prof = crescent_prof(s, tip=0.2, rise=1.5) * on
        w = 20.0 * prof
        d = Re - rr
        outer = clamp01((d + 0.15) * SS + 0.5)
        band = outer * smooth(w + 1.2, w - 2.2, d) * (prof > 0.004)
        t = np.clip(d / np.maximum(w, 0.4), 0, 1)
        solid_k = smooth(solid * 1.25, solid * 0.7, b)  # 1 = 단단한 날, 0 = 잔상
        # 날: 바깥 갈아낸 띠 (밝음) · 등 쪽 (안) 은 빛 몸
        edge = outer * np.exp(-np.maximum(d, 0) / (0.8 + 2.4 * prof)) * band
        bevel = band * (1 - smooth(0.25, 0.42, t)) * 0.95
        spine = band * gauss(t - 0.78, 0.07) * 0.6 * solid_k
        smear = band * (1 - smooth(0.2, 1.0, t)) * 0.75
        core = np.maximum(core, np.maximum.reduce([edge, bevel * (0.35 + 0.65 * solid_k), spine, smear * (1 - solid_k)]))
        glow = np.maximum(glow, smooth(-3.0, 0.5, d) * smooth(w * 1.25 + 3.5, w * 1.25 - 3.0, d) * (prof > 0.004) * (0.55 + 0.45 * solid_k))
        # 잔상 결 (날과 나란한 줄)
        for dk, s0, a in ((0.55, 0.15, 0.6), (0.85, 0.35, 0.4)):
            core = np.maximum(core, gauss(d - dk * w, 0.5) * swin(s, s0, 0.9, 0.2, 0.08) * a * on)
        # 머리 끝 반짝임 · 접선으로 튀는 불티 (이어 돌림: 장마다 같은 모양이 120° 뒤 날로 넘어감)
        hx, hy = c + math.cos(head - 0.05) * (Re - 3), c + math.sin(head - 0.05) * (Re - 3)
        core = np.maximum(core, star4(pad, hx, hy, 10.0, 0.95, rot=head + 0.3, thin=0.12, diag=0.35))
        glow = np.maximum(glow, pad.radial(hx, hy, 11.0, 1.4) * 0.8)
        rs = random.Random(31)
        for i in range(5):
            ph = (rs.random() + f / 8) % 1.0
            a0 = head - 0.12 - ph * 0.25
            px_, py_ = c + math.cos(a0) * (Re - 1), c + math.sin(a0) * (Re - 1)
            tx, ty = -math.sin(a0), math.cos(a0)
            dist = 6 + 16 * ph
            x, y = px_ + tx * dist, py_ + ty * dist
            al = (1 - ph) ** 1.3 * 0.9 * smooth(3, 10, min(x, S - x, y, S - y))
            Ls = 5 + 5 * ph
            core = np.maximum(core, streak(pad, (x - tx * Ls, y - ty * Ls), (x, y), 1.3, al))
    return finish(pad, glow, core, halo=((1.3, 0.48), (3.2, 0.38), (7.0, 0.2)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jwhirl2", "direction_z", 192, 192, 8, 4, job="warrior", zoom=0,
       desc="땅에 눕는 회전 칼날 (이어 돌림): 갈아낸 날 · 등줄이 있는 초승달 날 셋 + 뒤로 흐려지는 잔상 · 도는 판의 결 · 날 끝 반짝임 · 접선 불티")(whirl_frame)


# ------------------------------------------------------------ rfx_jspin2 (도는 양날 창 — 날 끝 반지름 = 반 크기 × 0.86, 이어 돌림: 8 장 = 180°)

def spin_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    R = S * 0.43
    base = math.pi * f / 8
    st = Stack(pad)
    # ---- 휘두른 자리: 창날이 쓸고 간 부채 (안 0.55R ~ R) + 날 끝 꼬리 (가는 호)
    fan_g = pad.zeros()
    trail_c = pad.zeros()
    for side in (0.0, math.pi):
        head = base + side
        b = behind(th, head)
        k = clamp01(1 - b / math.radians(140))
        rad = smooth(R * 0.66, R * 0.86, rr) * smooth(R + 1.5, R - 2.5, rr)
        fan_g = np.maximum(fan_g, rad * k ** 3.2 * 0.42)
        trail_c = np.maximum(trail_c, gauss(rr - (R - 1.5), 0.55 + 1.0 * k) * k ** 1.6 * 0.95)
        trail_c = np.maximum(trail_c, gauss(rr - R * 0.8, 0.45) * k ** 3 * 0.4)
        trail_c = np.maximum(trail_c, rad * k ** 5 * 0.22)
    st.body = np.maximum(st.body, fan_g)
    st.add_core(trail_c)
    st.body = np.maximum(st.body, gauss(rr - (R - 1.5), 1.6) * 0.12)
    # ---- 창: 자루 · 감은 손잡이 · 가운데 고리 · 양끝 잎 창날 (가운데 능선) · 받침 · 작은 가름쇠
    ux, uy = math.cos(base), math.sin(base)
    px_, py_ = -uy, ux
    xf = Xf(c, c, base)
    shaft = pad.poly(xf([(-R * 0.6, -2.2), (R * 0.6, -2.2), (R * 0.6, 2.2), (-R * 0.6, 2.2)]))
    st.over(shaft, 0.8, (rim_of(shaft, 0.5) * 0.5 + shaft * 0.25) * shaft)
    grip = pad.poly(xf([(-15, -2.9), (15, -2.9), (15, 2.9), (-15, 2.9)]))
    gc = rim_of(grip, 0.5) * 0.6
    x = -14.0
    while x < 14:
        gc = np.maximum(gc, pad.stroke(xf([(x, -2.8), (x + 2.6, 2.8)]), 0.7, a0=0.85) * grip)
        x += 3.2
    st.over(grip, 0.7, gc * grip)
    hub = pad.disc_field(c, c, 5.2)
    st.over(hub, 0.9, (rim_of(hub, 0.8) * 0.9 + pad.disc_field(c - 1, c - 1, 1.8) * 0.9) * hub)
    for sgn in (1, -1):
        xs = Xf(c, c, base if sgn > 0 else base + math.pi)
        lug = pad.polys([xs([(R * 0.6, -2.0), (R * 0.62, -7.5), (R * 0.67, -8.5), (R * 0.655, -4.5), (R * 0.69, -2.0)]),
                         xs([(R * 0.6, 2.0), (R * 0.62, 7.5), (R * 0.67, 8.5), (R * 0.655, 4.5), (R * 0.69, 2.0)])])
        lit, dark, _ = bevel_light(lug, 1.2)
        st.over(lug, 0.85 + 0.1 * lit, (rim_of(lug, 0.6) * 0.85 + lit * 0.5) * lug)
        sock = pad.poly(xs([(R * 0.62, -3.0), (R * 0.72, -2.2), (R * 0.72, 2.2), (R * 0.62, 3.0)]))
        st.over(sock, 0.84, (rim_of(sock, 0.5) * 0.8 + sock * 0.3) * sock)
        n = 26
        top, bot = [], []
        for i in range(n + 1):
            t = i / n
            xx = R * 0.7 + (R - R * 0.7) * t
            ww = 7.4 * (math.sin(math.pi * (0.1 + 0.9 * t)) ** 0.8) * (1 - 0.3 * t) + 0.4 * (1 - t)
            top.append((xx, -ww))
            bot.append((xx, ww))
        leaf = pad.poly(xs(top + bot[::-1]))
        X, Y = pad.grid()
        LX, LY = xs.inv(X, Y)
        de = inner_dist(leaf)
        lc = clamp01(1 - de / 1.8) ** 1.3 * leaf * 0.9
        lc = np.maximum(lc, pad.stroke(xs([(R * 0.71, 0), (R - 2.0, 0)]), 0.9, 0.3, a0=1.0, a1=0.8) * leaf)
        lc = np.maximum(lc, leaf * clamp01(-LY / 6.0) * 0.55)
        st.over(leaf, 0.86 - 0.12 * clamp01(LY / 6.0), lc * leaf)
        # 날 끝 반짝임
        tx, ty = xs.p(R - 3.0, 0)
        st.add_core(star4(pad, tx, ty, 8.5, 0.95, rot=base + 0.4, thin=0.12, diag=0.0))
    # 끝에서 튀는 불티 (반 바퀴 대칭 · 이어 돌림)
    rs = random.Random(12)
    for i in range(6):
        ph = (rs.random() + f / 8) % 1.0
        for side in (0.0, math.pi):
            a0 = base + side - 0.1 - ph * 0.3
            px0, py0 = c + math.cos(a0) * (R - 2), c + math.sin(a0) * (R - 2)
            tx, ty = -math.sin(a0), math.cos(a0)
            dist = 5 + 15 * ph
            x, y = px0 + tx * dist, py0 + ty * dist
            al = (1 - ph) ** 1.3 * 0.85 * smooth(3, 10, min(x, S - x, y, S - y))
            Ls = 4 + 6 * ph
            st.add_core(streak(pad, (x - tx * Ls, y - ty * Ls), (x, y), 1.2, al))
    core1 = pad.down(st.core)
    glow1 = v2_obj_glow(pad, st.body, core1)
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


def v2_obj_glow(pad, body_ss, core1, halo=((1.3, 0.5), (3.2, 0.36), (7.0, 0.2)), body_k=1.0, core_bloom=((1.3, 0.32), (3.5, 0.24))):
    from job_fx_art_v2 import obj_glow
    return obj_glow(pad, body_ss, core1, halo=halo, body_k=body_k, core_bloom=core_bloom)


sprite("rfx_jspin2", "direction_z", 192, 192, 8, 4, job="lancer", zoom=0,
       desc="도는 양날 창 (이어 돌림): 자루 · 감은 손잡이 · 가운데 고리 · 가름쇠 · 능선이 있는 잎 창날 둘 + 쓸고 간 부채 · 날 끝 꼬리 · 반짝임 · 불티")(spin_frame)


# ============================================================ 실행

def main():
    args = sys.argv[1:]
    only = None
    folder = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
    names = [n for n in SPECS if not only or n in only]
    if "--check" in args:
        ok = check(names)
        sys.exit(0 if ok else 1)
    for name in names:
        sp = SPECS[name]
        sets = render(sp)
        write(sp, sets)
        if folder:
            print("  preview:", preview(sp, sets, folder))


if __name__ == "__main__":
    main()
