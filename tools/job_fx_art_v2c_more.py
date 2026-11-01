#!/usr/bin/env python3
"""v2c 직업 스킬 연출 그림 (더) — 새 스킬 연출 아홉 → tools/java_fx (rpg:rfx_j*).

  python3 tools/job_fx_art_v2c_more.py                        # 그림 · 파티클 정의를 tools/java_fx 에 씀 (java_fx_art.save)
  python3 tools/job_fx_art_v2c_more.py --only rfx_jfireball   # 몇 개만 (쉼표로 여럿)
  python3 tools/job_fx_art_v2c_more.py --preview <폴더>       # 확인용 그림도 씀
  python3 tools/job_fx_art_v2c_more.py --check                # 쓴 그림 검사 (보는 꼴 · 칸 크기 · 그림 크기 · NaN · 흰색 · 칸 가장자리 알파 < 7% · 빈 칸)
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import OUT, save  # noqa: E402 — 같은 꼴의 파티클 정의 · tools/java_fx 에 씀
from job_fx_art_v2 import (BG, COLORS, SS, TAU, Pad, Stack, Xf, bevel_light, bez, bolt_pts, clamp01, ease_in_out, ease_out, edge_fade,  # noqa: E402
                           gblur, glyph_mask, inner_dist, label, lerp, rim_of, rot_pts, screen, smooth, sparkle, star_pts, taper_poly,
                           to_sheet)
from job_fx_art_v2b import (arc_pts, band_cov, cut_polyline, fbm, finish, gauss, lens, polar, star4, streak, win_border)  # noqa: E402

TEX = os.path.join(OUT, "textures", "particle", "rpg", "fx")
PART = os.path.join(OUT, "particles", "rpg", "fx")

# 확인용 다른 색 (게임에서 직업 색 대신 주문 색을 입힐 수 있어서 — 두 색으로 다 봄)
FIRE = ((1.0, 0.36, 0.08), (1.0, 0.86, 0.42))
ICE = ((0.34, 0.68, 1.0), (0.88, 0.97, 1.0))
VOLT = ((0.42, 0.46, 1.0), (0.84, 0.93, 1.0))
SUN = ((1.0, 0.6, 0.16), (1.0, 0.95, 0.72))
THUNDER = ((0.42, 0.58, 1.0), (0.92, 0.97, 1.0))
HOLY = ((1.0, 0.8, 0.36), (1.0, 0.98, 0.86))
RAINBOW = [(1.0, 0.24, 0.24), (1.0, 0.6, 0.14), (0.34, 1.0, 0.4), (0.3, 0.56, 1.0), (0.72, 0.36, 1.0)]


# ============================================================ 도움 (v2 · v2b 에 없는 것)

def wave_pts(p0, p1, n, amp_fn, ph, k=1.0):
    """p0 → p1 곧은 줄을 옆으로 흔든 점들 (amp_fn(s) · sin(2π k s − ph))"""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    out = []
    for i in range(n + 1):
        s = i / n
        o = amp_fn(s) * math.sin(TAU * k * s - ph)
        out.append((x0 + dx * s + nx * o, y0 + dy * s + ny * o))
    return out


def jag_path(rnd, pts, disp=0.2, levels=3, spread=0.55):
    """굽은 줄 pts 를 마디마다 번개처럼 꺾음 (bolt_pts 를 이어 붙임)"""
    out = [pts[0]]
    for a, b in zip(pts, pts[1:]):
        out += bolt_pts(rnd, a, b, disp, levels, spread)[1:]
    return out


def ellipse_pts(cx, cy, rx, ry, a0=0.0, a1=TAU, n=96):
    return [(cx + math.cos(a0 + (a1 - a0) * i / n) * rx, cy + math.sin(a0 + (a1 - a0) * i / n) * ry) for i in range(n + 1)]


def tri_wave(x):
    """0 ~ 1 톱니 → 0 → 1 → 0 세모 물결"""
    x = np.mod(x, 1.0)
    return 1 - np.abs(2 * x - 1)


# ============================================================ 그림 목록 · 쓰기 · 확인용 그림 · 검사

class Spec:
    def __init__(self, name, facing, cw, ch, frames, cols, job, fn, loop=None, zoom=0, alt=None, desc=""):
        self.name, self.facing, self.cw, self.ch, self.frames, self.cols = name, facing, cw, ch, frames, cols
        self.job, self.fn, self.loop, self.zoom, self.alt, self.desc = job, fn, loop, zoom, alt, desc


SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, job, loop=None, zoom=0, alt=None, desc=""):
    def deco(fn):
        SPECS[name] = Spec(name, facing, cw, ch, frames, cols, job, fn, loop, zoom, alt, desc)
        return fn
    return deco


def render(sp):
    glows, cores = [], []
    for f in range(sp.frames):
        g, c = sp.fn(f)
        assert g.shape == (sp.ch, sp.cw) and c.shape == (sp.ch, sp.cw), (sp.name, f, g.shape, c.shape)
        assert np.isfinite(g).all() and np.isfinite(c).all(), (sp.name, f, "NaN")
        glows.append(clamp01(g).astype(np.float32))
        cores.append(clamp01(c).astype(np.float32))
    return glows, cores


def write(sp, glows, cores):
    img = to_sheet(list(glows) + list(cores), sp.cols, sp.cw, sp.ch)
    assert img.size[0] <= 4096 and img.size[1] <= 4096, (sp.name, img.size)
    save(sp.name, img, sp.cw, sp.ch, sp.facing)
    return img


def tint(g, c, ca, cb, w=None, h=None, bg=BG):
    """어두운 바탕 위에 빛 (색 A) → 심지 (색 B) — 게임의 반투명 칠과 같음"""
    gg = edge_fade(g)[..., None]
    cc = edge_fade(c)[..., None]
    out = np.asarray(bg, np.float32)[None, None, :] * (1 - gg) + np.array(ca, np.float32)[None, None, :] * gg
    out = out * (1 - cc) + np.array(cb, np.float32)[None, None, :] * cc
    im = Image.fromarray((clamp01(out) * 255 + 0.5).astype(np.uint8), "RGB")
    if w and h and im.size != (w, h):
        im = im.resize((w, h), Image.BICUBIC if w > im.size[0] else Image.LANCZOS)
    return im


def strip(glows, cores, ca, cb, scale, gap, title, labels=True):
    ch, cw = glows[0].shape
    w, h = max(1, int(round(cw * scale))), max(1, int(round(ch * scale)))
    n = len(glows)
    out = Image.new("RGB", (n * (w + gap) + gap, h + gap + 14), tuple(int(v * 255) for v in BG))
    label(out, title, (gap, 1))
    for i in range(n):
        out.paste(tint(glows[i], cores[i], ca, cb, w, h), (gap + i * (w + gap), 13))
        if labels:
            label(out, str(i), (gap + i * (w + gap) + 2, 14))
    return out


def loop_report(sp, glows, cores):
    """이어 돌리는 장면: 이웃 장끼리 차이 (마지막 → 처음 포함) — 이음매가 다른 걸음보다 튀지 않는지"""
    if not sp.loop:
        return ""
    a, b = sp.loop
    idx = list(range(a, b + 1))
    diffs = []
    for i, j in zip(idx, idx[1:] + idx[:1]):
        d = float(np.mean(np.abs(glows[i] - glows[j])) + np.mean(np.abs(cores[i] - cores[j])))
        diffs.append(d)
    return "loop %d-%d steps: %s (last->first %.4f, mean %.4f)" % (a, b, " ".join("%.4f" % d for d in diffs), diffs[-1], sum(diffs) / len(diffs))


def preview(sp, glows, cores, folder):
    """직업 색 줄 · 다른 색 줄 · 작은 크기 줄 (휴대폰에서 멀리 볼 때) · 한 장 크게 · 빛 / 심지 알파"""
    ca, cb = COLORS[sp.job]
    cw, ch = sp.cw, sp.ch
    gap = 6
    n = sp.frames
    scale = min(1.0, 1500.0 / (n * (cw + gap)))
    bgc = tuple(int(v * 255) for v in BG)
    blocks = [strip(glows, cores, ca, cb, scale, gap, f"{sp.name}  {sp.facing} {cw}x{ch} x{n}  ({sp.job} colors)")]
    for k, (aa, bb) in enumerate(sp.alt or []):
        blocks.append(strip(glows, cores, aa, bb, scale, gap, f"alt colors {k}"))
    blocks.append(strip(glows, cores, ca, cb, 0.33, gap, "small (x0.33)", labels=False))
    zf = sp.zoom
    zs = 2 if max(cw, ch) <= 256 else 1
    zoom = tint(glows[zf], cores[zf], ca, cb, cw * zs, ch * zs)
    pal = (sp.alt or [None])[0]
    zoom2 = tint(glows[zf], cores[zf], pal[0], pal[1], cw * zs, ch * zs) if pal else None
    zw = zoom.size[0] + (zoom2.size[0] + gap if zoom2 else 0)
    zb = Image.new("RGB", (zw + 2 * gap, zoom.size[1] + 16), bgc)
    label(zb, f"frame {zf} x{zs}", (gap, 1))
    zb.paste(zoom, (gap, 14))
    if zoom2:
        zb.paste(zoom2, (gap * 2 + zoom.size[0], 14))
    blocks.append(zb)
    gl = Image.fromarray((clamp01(edge_fade(glows[zf])) * 255).astype(np.uint8), "L").convert("RGB")
    co = Image.fromarray((clamp01(edge_fade(cores[zf])) * 255).astype(np.uint8), "L").convert("RGB")
    sep = Image.new("RGB", (cw * 2 + gap * 3, ch + 16), (0, 0, 0))
    label(sep, "glow alpha | core alpha", (gap, 1))
    sep.paste(gl, (gap, 14))
    sep.paste(co, (gap * 2 + cw, 14))
    blocks.append(sep)
    W = max(b.size[0] for b in blocks)
    H = sum(b.size[1] for b in blocks)
    out = Image.new("RGB", (W, H), bgc)
    y = 0
    for b in blocks:
        out.paste(b, (0, y))
        y += b.size[1]
    path = os.path.join(folder, f"{sp.name}.png")
    out.save(path)
    return path


def check(names):
    """쓴 그림 (tools/java_fx) 검사. 문제가 없으면 True"""
    ok_all = True
    for name in names:
        sp = SPECS[name]
        msgs = []
        jn = json.load(open(os.path.join(PART, f"{name}.particle.json")))
        bb = jn["particle_effect"]["components"]["minecraft:particle_appearance_billboard"]
        if bb["facing_camera_mode"] != sp.facing:
            msgs.append("facing " + bb["facing_camera_mode"])
        cw, ch = bb["uv"]["uv_size"]
        if (cw, ch) != (sp.cw, sp.ch):
            msgs.append("uv_size")
        im = Image.open(os.path.join(TEX, f"{name}.png"))
        a = np.asarray(im.convert("RGBA"), np.float32)
        if (a[..., :3] != 255).any():
            msgs.append("RGB not white")
        al = a[..., 3] / 255.0
        cols = al.shape[1] // cw
        if cols != sp.cols:
            msgs.append(f"cols {cols} != {sp.cols}")
        worst = 0.0
        empty = []
        for i in range(sp.frames * 2):
            r, k = divmod(i, cols)
            c = al[r * ch:(r + 1) * ch, k * cw:(k + 1) * cw]
            worst = max(worst, c[0].max(), c[-1].max(), c[:, 0].max(), c[:, -1].max())
            if c.max() < 0.08:
                empty.append(i)
        if worst >= 0.07:
            msgs.append(f"border alpha {worst:.3f}")
        if empty:
            msgs.append(f"empty cells {empty}")
        ok_all &= not msgs
        print(f"{'OK ' if not msgs else 'BAD'} {name:16s} {sp.facing:16s} {cw}x{ch} x{sp.frames} tex {im.size[0]}x{im.size[1]} cols {cols} border {worst:.3f}"
              + ("" if not msgs else "  <- " + "; ".join(msgs)))
    return ok_all


# ============================================================ 그림들

# ------------------------------------------------------------ rfx_jfireball (불덩이 — 오른쪽이 앞, 머리 가운데 0.80 W · 이어 돌림: 4 장에 결 한 주기)

FB = dict(W=256, H=96, hx=205.0, R=16.5, tail=20.0)


def fireball_frame(f, frames=4):
    W, H, hx, R = FB["W"], FB["H"], FB["hx"], FB["R"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    U = hx - X                      # 머리 가운데에서 뒤로 (+)
    V = Y - cy
    L = hx - FB["tail"]
    t = np.clip(U / L, 0, 1)
    rr = np.sqrt((X - hx) ** 2 + V ** 2)
    # 뒤로 흐르는 결 (주기 64 점 — 4 장에 한 주기 · 잔결은 두 주기)
    flow = 16.0 * f
    n1 = fbm((U - flow) / 16.0, V / 6.0, seed=71, octaves=3, px=4)
    n2 = fbm((U - 2 * flow) / 8.0, V / 3.5 + 5.0, seed=72, octaves=2, px=8)
    glow = pad.zeros()
    core = pad.zeros()
    # ---- 불꼬리 몸: 머리 뒤에서 가늘어짐 (공보다 좁게 — 공이 또렷이) · 흔들리는 가운데 줄 · 가장자리를 따라 뒤로 흘러가는 뾰족한 불 혀
    #      (뒤를 보는 톱니 — 주기 64 점, 결과 함께 16 점/장 → 4 장에 한 주기) · 잔결로 너덜너덜
    yc = 2.4 * np.sin(t * 6.5 - ph) * t
    w = R * 0.8 * (1 - t) ** 0.85 + 0.6
    side = np.where(V - yc < 0, 0.0, 0.5)
    saw = np.mod((U - flow) / 64.0 + side + 0.17, 1.0)
    tooth = saw ** 3.0 * smooth(1.0, 0.9, saw)
    tamp = 0.95 * smooth(0.0, 0.1, t) * (1 - t) ** 0.4 * (0.7 + 0.6 * clamp01(0.5 + n1))
    dy = np.abs(V - yc) / w
    edge = 1 + tamp * tooth + 0.28 * n1 * (0.25 + 0.8 * t)
    plume = clamp01((edge - dy) / 0.28) * (U > 0) * (1 - t) ** 0.6 * smooth(1.0, 0.82, t)
    glow = np.maximum(glow, plume * (0.56 + 0.22 * clamp01(0.5 + n2)))
    streaks = fbm((U - 1.5 * flow) / 24.0, V / 2.6 + 3.0, seed=76, octaves=2, px=4)  # 주기 96 점 · 24 점/장 → 4 장에 한 주기
    hot = clamp01((0.4 + 0.26 * n1 + 0.1 * n2 - dy) / 0.22) * (U > 0) * (1 - t) ** 4.0
    core = np.maximum(core, hot * 0.95)
    core = np.maximum(core, clamp01((streaks - 0.16) / 0.2) * clamp01((0.7 - dy) / 0.3) * (U > 0) * (1 - t) ** 4.5 * 0.55)
    # ---- 불 혀 여섯: 공 옆구리 · 뒤에서 떨어져 나와 S 꼴로 휘며 뒤로 넘실거림 (밑동이 넓고 끝이 뾰족 · 가장자리는 잔결로 너덜너덜 ·
    #      길이와 흔들림이 장마다 돌아옴) — 흐린 꼬리 몸 위에서 밝게
    tongues = [(-1, 1.3, 78, 8.0, 0.0, True), (1, 1.22, 88, 8.5, 1.9, True), (-1, 0.86, 120, 7.5, 3.7, True),
               (1, 0.8, 132, 7.0, 5.1, False), (-1, 0.36, 150, 6.0, 2.6, False), (1, 0.3, 112, 6.5, 4.4, False)]
    for i, (sg, beta, Lt, w0, off, hotline) in enumerate(tongues):
        th = sg * (math.pi - beta)
        x0, y0 = hx + math.cos(th) * R * 0.78, cy + math.sin(th) * R * 0.78
        Lk = Lt * (1 + 0.12 * math.sin(ph + off))
        pts = []
        for k in range(29):
            s = k / 28
            lat = sg * R * 0.55 * math.sin(math.pi * min(1.0, s * 1.5)) * (1 - s) ** 0.6 - (y0 - cy) * 0.6 * s ** 1.3
            wob = (2.0 + 7.0 * s) * s * math.sin(TAU * 1.0 * s - ph + off)
            pts.append((x0 - Lk * s, y0 + lat + wob))
        d, s_ = pad.line_field(pts, w0 * 1.6 + 4)
        wl = w0 * (1 - s_) ** 1.35 * (1 + 0.45 * n2 * clamp01(s_ * 3))
        m = smooth(wl + 1.0, wl - 1.0, d) * (s_ < 0.995)
        glow = np.maximum(glow, m * 0.95 * (1 - 0.3 * s_))
        if hotline:
            core = np.maximum(core, pad.stroke(pts[:11], w0 * 0.5, 0.4, a0=0.75, a1=0.0))
    # ---- 공: 겉이 불꽃으로 일렁이는 둥근 머리 (앞은 매끈 · 옆과 뒤는 뾰족뾰족) · 흰 속불 (끓어오르는 얼룩 — 두 결을 번갈아 섞어 이어 돎)
    thb = np.arctan2(V, X - hx)
    q = np.abs(thb) / math.pi
    nq = np.where(V < 0, fbm(q * 8 - 2 * f, rr / 5.0, seed=74, octaves=2, px=8), fbm(q * 8 - 2 * f, rr / 5.0 + 9.0, seed=75, octaves=2, px=8))
    re = R * (1 + (0.03 + 0.22 * smooth(0.3, 0.95, q)) * nq)
    ball = clamp01((re - rr) * SS * 0.5 + 0.5)
    glow = np.maximum(glow, ball)
    rb = rr / np.maximum(re, 1.0)
    inner = smooth(1.0, 0.64, rb + 0.08 * nq)
    boil = (fbm((X - hx) / 5.5, V / 5.5, seed=77, octaves=2) * math.cos(ph) + fbm((X - hx) / 5.5 + 20, V / 5.5, seed=78, octaves=2) * math.sin(ph)) * 1.4
    mott = clamp01(boil - 0.05) * smooth(0.15, 0.55, rb) * smooth(1.0, 0.8, rb)
    core = np.maximum(core, clamp01(inner * (1 - 0.42 * mott)) * ball)
    # 앞 가장자리: 공 안쪽에 붙은 밝은 초승달 (날아가는 쪽이 눌려 가장 뜨거움)
    lead = arc_pts(hx, cy, R * 0.88, -1.05, 1.05, 32)
    core = np.maximum(core, pad.stroke(lead, 2.0, a0=1.0, taper=0.9) * ball)
    glow = np.maximum(glow, pad.radial(hx, cy, R * 2.4, 1.7) * 0.6)
    # ---- 불티: 꼬리 여기저기에서 뒤로 흘러가며 피었다 꺼짐 (q = 4 장에 한 바퀴)
    rnd = random.Random(707)
    for i in range(24):
        u0 = rnd.uniform(R + 2, L * 0.86)
        span = rnd.uniform(24, 46)
        v0 = rnd.uniform(-1, 1)
        b = rnd.random()
        r = rnd.uniform(0.7, 1.5)
        q = (b + f / frames) % 1.0
        u = u0 + span * q
        x = hx - u
        tt = u / L
        if x < FB["tail"] + 2:
            continue
        y = cy + v0 * (5 + 24 * tt) + 2.0 * math.sin(q * 6 + i)
        al = math.sin(math.pi * q) ** 0.8 * (1 - tt) ** 0.6 * rnd.uniform(0.6, 1.0)
        big = i % 5 == 0
        r = r * (1.5 if big else 1.0)
        core = np.maximum(core, pad.stroke([(x, y), (x + 3.5 + 2.5 * r, y)], r, 0.2, a0=al, a1=0.0))
        core = np.maximum(core, pad.disc_field(x, y, r * 0.75) * al)
        if big:
            core = np.maximum(core, star4(pad, x, y, 3.0 + 1.2 * math.sin(math.pi * q), al * 0.8, rot=0.0, thin=0.2, diag=0.0))
        glow = np.maximum(glow, pad.disc_field(x + 1, y, r * 2.4, soft=1.2) * al * 0.65)
    return finish(pad, glow, core, halo=((1.3, 0.5), (3.2, 0.4), (7.5, 0.22)), body_k=0.88, core_bloom=((1.3, 0.38), (3.5, 0.28)), win=3.5)


sprite("rfx_jfireball", "lookat_direction", 256, 96, 4, 4, job="mage", loop=(0, 3), zoom=0, alt=[FIRE],
       desc="불덩이 (오른쪽이 앞 · 이어 돌림)")(fireball_frame)


# ------------------------------------------------------------ rfx_jicelance (얼음 창 — 오른쪽이 끝 0.95 W, 이어 돌림: 4 장에 결 · 빛 한 주기)

IL = dict(W=256, H=64, tip=243.0, butt=84.0, b0=102.0, sh=182.0, hh=9.0, rg=4.6)


def icelance_frame(f, frames=4):
    W, H = IL["W"], IL["H"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    tip, butt, b0, sh, hh, rg = IL["tip"], IL["butt"], IL["b0"], IL["sh"], IL["hh"], IL["rg"]
    st = Stack(pad)
    # ---- 서리 안개 꼬리 (맨 뒤): 창 꽁무니에서 뒤로 퍼지며 옅어짐 · 뒤로 흐르는 결 (주기 48 점 · 12 점/장)
    U = butt + 4 - X
    t = np.clip(U / (butt - 6), 0, 1)
    flow = 12.0 * f
    nm = fbm((U - flow) / 12.0, (Y - cy) / 5.0, seed=81, octaves=3, px=4)
    nm2 = fbm((U - 2 * flow) / 6.0, (Y - cy) / 3.0 + 4.0, seed=82, octaves=2, px=8)
    mw = 5.5 + 13.0 * t ** 0.8
    dym = np.abs(Y - cy) / mw
    mist = clamp01((0.95 + 0.8 * nm - dym) / 0.45) * smooth(-10, 8, U) * (1 - t) ** 1.2 * (0.55 + 0.45 * clamp01(0.5 + 1.5 * nm2))
    st.body = np.maximum(st.body, mist * 0.62)
    st.core = np.maximum(st.core, clamp01((nm2 + 0.5 * nm - 0.22) / 0.28) * mist * 0.4)
    # 바람 줄 (안개 속을 뒤로 흐르는 가는 줄)
    rnd = random.Random(811)
    for i in range(7):
        y = cy + rnd.uniform(-1, 1) * 9
        b = rnd.random()
        q = (b + f / frames) % 1.0
        x1 = butt - 4 - 70 * q
        L = rnd.uniform(14, 26)
        al = math.sin(math.pi * q) * rnd.uniform(0.35, 0.6)
        st.core = np.maximum(st.core, pad.stroke([(x1 - L, y + (x1 - L - butt) * 0.04 * np.sign(y - cy)), (x1, y)], 0.25, 0.8, a0=0.0, a1=al))
    # ---- 뒤의 작은 결정들 (큰 결정 뒤 — 큰 결정이 가림): 꽁무니에 깨진 결정 뿌리처럼 뭉쳐 뒤 · 바깥으로
    shards = [((112, cy - 6.0), (76, cy - 19), 3.6, 0.95), ((104, cy - 2.0), (58, cy - 9), 3.0, 0.85), ((116, cy + 6.5), (88, cy + 18), 3.2, 0.7),
              ((106, cy + 2.5), (64, cy + 10), 2.8, 0.62), ((96, cy + 0.5), (62, cy + 1.5), 2.2, 0.75)]
    for (ax, ay), (bx, by), hw, lit in shards:
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        px_, py_ = -uy, ux
        mx, my = ax + dx * 0.68, ay + dy * 0.68
        top = [(ax + px_ * hw * 0.8, ay + py_ * hw * 0.8), (mx + px_ * hw, my + py_ * hw), (bx, by)]
        bot = [(ax - px_ * hw * 0.8, ay - py_ * hw * 0.8), (mx - px_ * hw, my - py_ * hw), (bx, by)]
        ridge = [(ax, ay), (mx + px_ * hw * 0.2, my + py_ * hw * 0.2), (bx, by)]
        fa = pad.poly(top + ridge[::-1])
        fb = pad.poly(ridge + bot[::-1])
        m = np.maximum(fa, fb)
        hi_a = (fa if py_ < 0 else fb)
        lo_a = (fb if py_ < 0 else fa)
        c_ = np.maximum(rim_of(m, 0.6) * 0.85, pad.stroke(ridge, 0.6, a0=0.8) * m)
        c_ = np.maximum(c_, hi_a * 0.42 * lit)
        st.over(m, (hi_a * 0.9 + lo_a * 0.58) * (0.8 + 0.2 * lit), c_ * m)
    # ---- 큰 결정 (창 몸): 고드름처럼 긴 육각 결정 옆모습 — 마디마다 꺾이는 겉 · 능선 둘 (위 띠 밝음 · 가운데 · 아래 어두움, 면마다 빛이 조금씩 다름)
    xs = [butt, b0, 150.0, 199.0, tip]
    ht = [0.0, hh * 1.04, hh * 0.92, hh * 0.6, 0.0]   # 위 겉
    hb = [0.0, hh * 0.98, hh * 0.86, hh * 0.56, 0.0]  # 아래 겉
    rt, rb_ = 0.5, 0.46                                 # 능선 (겉 높이에 대한 몫)
    yc0 = [cy + 0.5, cy, cy, cy, cy]
    vals_t = [(0.8, 0.36), (0.95, 0.5), (1.0, 0.62), (0.92, 0.55)]
    vals_m = [(0.62, 0.1), (0.72, 0.14), (0.8, 0.22), (0.74, 0.2)]
    vals_b = [(0.48, 0.02), (0.56, 0.04), (0.6, 0.08), (0.52, 0.05)]
    body_v = pad.zeros()
    face_c = pad.zeros()
    crystal = pad.zeros()
    for j in range(4):
        x0_, x1_ = xs[j], xs[j + 1]
        T0, T1 = (x0_, yc0[j] - ht[j]), (x1_, yc0[j + 1] - ht[j + 1])
        R0, R1 = (x0_, yc0[j] - ht[j] * rt), (x1_, yc0[j + 1] - ht[j + 1] * rt)
        Q0, Q1 = (x0_, yc0[j] + hb[j] * rb_), (x1_, yc0[j + 1] + hb[j + 1] * rb_)
        B0, B1 = (x0_, yc0[j] + hb[j]), (x1_, yc0[j + 1] + hb[j + 1])
        for pts_, (bv, cv) in (([T0, T1, R1, R0], vals_t[j]), ([R0, R1, Q1, Q0], vals_m[j]), ([Q0, Q1, B1, B0], vals_b[j])):
            m = pad.poly(pts_)
            crystal = np.maximum(crystal, m)
            body_v = np.maximum(body_v, m * bv)
            face_c = np.maximum(face_c, m * cv)
    # 결정 속: 비스듬한 금 몇 줄 · 가운데 띠를 지나는 빛 줄 (굴절)
    inner_c = pad.zeros()
    rnd = random.Random(83)
    for i in range(7):
        x0 = rnd.uniform(b0 + 4, 205)
        L = rnd.uniform(7, 15)
        a = rnd.uniform(-1.1, 1.1)
        y0 = cy + rnd.uniform(-4.5, 4.5)
        inner_c = np.maximum(inner_c, pad.stroke([(x0, y0), (x0 + L * math.cos(a), y0 + L * math.sin(a) * 0.45)], 0.5, a0=0.3))
    inner_c = np.maximum(inner_c, pad.stroke([(b0 + 4, cy + 1.3), (175, cy + 0.6), (tip - 10, cy + 0.1)], 1.0, 0.4, a0=0.4, a1=0.75))
    # 모서리 줄: 겉 · 능선 둘 · 마디의 비스듬한 꺾임 줄
    top_line = [(xs[j], yc0[j] - ht[j]) for j in range(5)]
    bot_line = [(xs[j], yc0[j] + hb[j]) for j in range(5)]
    edges = np.maximum(pad.stroke(top_line, 0.95, a0=1.0), pad.stroke(bot_line, 0.85, a0=0.8))
    edges = np.maximum(edges, pad.stroke([(xs[j], yc0[j] - ht[j] * rt) for j in range(5)], 0.75, a0=0.85))
    edges = np.maximum(edges, pad.stroke([(xs[j], yc0[j] + hb[j] * rb_) for j in range(5)], 0.7, a0=0.7))
    for j in (1, 2, 3):
        edges = np.maximum(edges, pad.stroke([(xs[j] + 2.0, cy - ht[j]), (xs[j] - 1.0, cy - ht[j] * rt)], 0.65, a0=0.75))
        edges = np.maximum(edges, pad.stroke([(xs[j] - 1.0, cy - ht[j] * rt), (xs[j] + 1.5, cy + hb[j] * rb_)], 0.5, a0=0.4))
        edges = np.maximum(edges, pad.stroke([(xs[j] + 1.5, cy + hb[j] * rb_), (xs[j] - 1.0, cy + hb[j])], 0.55, a0=0.55))
    edges = edges * clamp01(crystal * 2.0)
    # 빛이 결정을 따라 미끄러짐 (가는 비스듬한 빛 두 줄 · 주기 = 4 장)
    Ls = (tip + 24) - (butt - 24)
    sx = butt - 24 + Ls * (f + 0.25) / frames
    swp = pad.zeros()
    for kk in (-1, 0, 1):
        u = (X - (sx + kk * Ls)) + (Y - cy) * 0.7
        swp = np.maximum(swp, 0.7 * np.exp(-0.5 * (u / 2.4) ** 2) + 0.45 * np.exp(-0.5 * ((u + 9) / 1.2) ** 2))
    c_cr = np.maximum.reduce([face_c, inner_c * crystal, edges, clamp01(swp) * crystal * 0.6])
    st.over(crystal, body_v, c_cr)
    # ---- 김: 결정 겉에서 피어올라 뒤로 끌리는 찬 김 몇 가닥
    def edge_y(x0, sg):
        hs = ht if sg < 0 else hb
        return cy + sg * float(np.interp(x0, xs, hs))
    for i, (x0, sg, L, off) in enumerate(((178, -1, 62, 0.0), (146, 1, 66, 2.1), (124, -1, 50, 4.0), (204, 1, 44, 1.3))):
        y0 = edge_y(x0, sg)
        pts = wave_pts((x0, y0), (x0 - L, y0 + sg * 7), 20, lambda s: 2.6 * s, ph + off, 1.0)
        st.core = np.maximum(st.core, pad.stroke(pts, 0.9, 0.2, a0=0.5, a1=0.0) * (1 - crystal))
        st.body = np.maximum(st.body, pad.stroke(pts, 3.5, 1.0, a0=0.35, a1=0.0, soft=1.0) * (1 - crystal))
    # ---- 반짝임: 끝 (장마다 크기 · 기울기) · 마디 모서리 · 서리 알갱이 (뒤로 흘러가며 깜빡임)
    tw = [1.0, 0.55, 0.85, 0.4][f]
    st.add_core(sparkle(pad, tip - 3.5, cy, 6.5 + 4.5 * tw, 1.0, rot=0.12 * f, diag=0.45))
    st.add_core(sparkle(pad, 150, cy - ht[2], 3.0 + 3.5 * (1 - tw), 0.95, rot=0.0, diag=0.0))
    st.add_core(sparkle(pad, 199, cy - ht[3], 2.5 + 2.5 * tw, 0.85, rot=0.0, diag=0.0))
    rnd = random.Random(84)
    for i in range(20):
        u0 = rnd.uniform(4, 60)
        span = rnd.uniform(16, 30)
        b = rnd.random()
        q = (b + f / frames) % 1.0
        x = butt - u0 - span * q
        if x < 8:
            continue
        tt = clamp01((butt - x) / (butt - 6))
        y = cy + rnd.uniform(-1, 1) * (4 + 15 * tt)
        al = math.sin(math.pi * q) ** 0.9 * (1 - tt) ** 0.5 * rnd.uniform(0.6, 1.0)
        r = rnd.uniform(1.6, 3.6) * (1 - 0.4 * tt)
        if i % 3 == 0:
            st.add_core(star4(pad, x, y, r + 1.5, al, rot=math.pi / 4 * (i % 2), thin=0.2, diag=0.0))
        else:
            st.add_core(pad.disc_field(x, y, 0.55 + 0.2 * r) * al)
        st.body = np.maximum(st.body, pad.disc_field(x, y, 2.0, soft=1.0) * al * 0.5)
    return finish(pad, st.body, st.core, halo=((1.2, 0.5), (3.0, 0.38), (6.5, 0.22)), body_k=0.92, core_bloom=((1.2, 0.32), (3.0, 0.24)), win=3.0)


sprite("rfx_jicelance", "lookat_direction", 256, 64, 4, 2, job="mage", loop=(0, 3), zoom=0, alt=[ICE],
       desc="얼음 창 (오른쪽이 끝 · 이어 돌림)")(icelance_frame)


# ------------------------------------------------------------ rfx_jsparkorb (번개 구슬 — 오른쪽이 앞, 구슬 가운데 0.78 W · 이어 돌림: 번개가 장마다 바뀜)

SO = dict(W=192, H=96, hx=150.0, R=12.5)


def sparkorb_frame(f, frames=4):
    W, H, hx, R = SO["W"], SO["H"], SO["hx"], SO["R"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    rr = np.sqrt((X - hx) ** 2 + (Y - cy) ** 2)
    rnd = random.Random(900 + f * 31)
    glow = pad.zeros()
    core = pad.zeros()
    # ---- 이온 꼬리: 구슬 뒤로 옅게 퍼지며 가늘어지는 빛 (번개가 가늘어도 날아가는 꼴이 보이게)
    U = hx - X
    t = np.clip(U / (hx - 14), 0, 1)
    tw_ = 4.0 + 16.0 * t ** 0.9
    trail = np.exp(-0.5 * ((Y - cy) / tw_) ** 2) * (U > 0) * (1 - t) ** 1.6 * smooth(0, 14, U)
    glow = np.maximum(glow, trail * 0.42)
    # ---- 뒤로 끌리는 번개 줄기 셋 ~ 넷: 구슬 뒤 겉에서 나와 바깥으로 퍼지며 뒤로 (마디마다 꺾임 · 갈래 · 끝으로 가늘어짐)
    # 자리 다섯 (위 둘 · 가운데 · 아래 둘) — 장마다 다른 길이 (둘은 짧게) · 다른 굽이 (퍼진 부채꼴)
    slots = [(-2.45, -1), (-2.85, -1), (math.pi, 0), (2.85, 1), (2.45, 1)]
    order = list(range(5))
    rnd.shuffle(order)
    for n_, k in enumerate(order):
        a0s, sgn0 = slots[k]
        a0 = a0s + rnd.uniform(-0.1, 0.1)
        p0 = (hx + math.cos(a0) * R * 0.9, cy + math.sin(a0) * R * 0.9)
        x_end = rnd.uniform(14, 40) if n_ < 3 else rnd.uniform(62, 96)
        far = abs(k - 2) == 2
        spread = (rnd.uniform(24, 36) if far else rnd.uniform(10, 20)) if sgn0 else rnd.uniform(-9, 9)
        y_end = cy + (sgn0 * spread if sgn0 else spread)
        c1 = (p0[0] - rnd.uniform(22, 34), p0[1] + sgn0 * rnd.uniform(0, 4))
        mxp = (p0[0] + x_end) / 2
        c2 = (mxp, (p0[1] + y_end) / 2 + rnd.uniform(-12, 12))
        base = bez(p0, c1, c2, 7, p3=(x_end, y_end))
        pts = jag_path(rnd, base, 0.17, 3, 0.55)
        wk = 1.0 if n_ < 2 else 0.78
        core = np.maximum(core, pad.stroke(pts, 2.3 * wk, 0.5, a0=1.0, a1=0.35))
        glow = np.maximum(glow, pad.stroke(pts, 7.0 * wk, 2.0, a0=0.85, a1=0.25, soft=2.5))
        for b in range(2):
            j = rnd.randint(len(pts) // 5, len(pts) * 3 // 4)
            bx, by = pts[j]
            sgn = 1 if by >= cy else -1
            beta = rnd.uniform(0.35, 0.95)
            bl = rnd.uniform(10, 24) * (1 - j / len(pts) * 0.4)
            br = bolt_pts(rnd, (bx, by), (bx - math.cos(beta) * bl, by + sgn * math.sin(beta) * bl), 0.28, 3, 0.6)
            core = np.maximum(core, pad.stroke(br, 1.3 * wk, 0.35, a0=0.9, a1=0.25))
            glow = np.maximum(glow, pad.stroke(br, 4.0 * wk, 1.2, a0=0.55, a1=0.15, soft=1.5))
        # 꺾인 마디에 밝은 점 하나 · 끝의 불똥
        j = rnd.randint(2, len(pts) // 2)
        core = np.maximum(core, pad.disc_field(pts[j][0], pts[j][1], 1.2) * 0.8)
        ex, ey = pts[-1]
        core = np.maximum(core, pad.disc_field(ex, ey, 1.0) * 0.7)
        glow = np.maximum(glow, pad.disc_field(ex, ey, 3.0, soft=1.5) * 0.4)
    # ---- 구슬: 가운데 흰 빛 · 겉 테 · 속을 기는 실 번개 (플라스마 구) · 겉에서 튀는 짧은 가시 번개
    glow = np.maximum(glow, pad.disc_field(hx, cy, R + 2.5, soft=2.5))
    glow = np.maximum(glow, pad.radial(hx, cy, R * 2.9, 1.6) * 0.7)
    core = np.maximum(core, smooth(R * 0.8, R * 0.22, rr))
    core = np.maximum(core, gauss(rr - R * 0.98, 0.7) * 0.9)
    for k in range(4):
        a = rnd.uniform(0, TAU)
        pts = bolt_pts(rnd, (hx + rnd.uniform(-2, 2), cy + rnd.uniform(-2, 2)), (hx + math.cos(a) * R * 0.95, cy + math.sin(a) * R * 0.95), 0.3, 3, 0.6)
        core = np.maximum(core, pad.stroke(pts, 1.2, 0.7, a0=0.95, a1=0.85))
    for k in range(9):
        a = rnd.uniform(0, TAU)
        if math.cos(a) > 0.55 and rnd.random() < 0.6:
            a = math.pi - a  # 앞보다 뒤에 더
        x0, y0 = hx + math.cos(a) * R, cy + math.sin(a) * R
        L = rnd.uniform(5, 12)
        p1 = (x0 + math.cos(a + rnd.uniform(-0.5, 0.5)) * L, y0 + math.sin(a + rnd.uniform(-0.5, 0.5)) * L)
        pts = bolt_pts(rnd, (x0, y0), p1, 0.32, 2, 0.6)
        core = np.maximum(core, pad.stroke(pts, 1.4, 0.4, a0=0.95, a1=0.4))
        glow = np.maximum(glow, pad.stroke(pts, 4.0, 1.5, a0=0.6, a1=0.2, soft=1.5))
    # 앞의 반짝임 (장마다 크기 · 기울기)
    tw = [1.0, 0.6, 0.85, 0.5][f]
    core = np.maximum(core, sparkle(pad, hx + 1, cy, 12 + 5 * tw, 0.95, rot=0.2 * f + 0.1, thin=0.1, diag=0.4))
    # ---- 꼬리의 불똥 (뒤로 흘러가며 깜빡임)
    rs = random.Random(911)
    for i in range(14):
        u0 = rs.uniform(R + 4, 110)
        span = rs.uniform(14, 26)
        b = rs.random()
        q = (b + f / frames) % 1.0
        x = hx - u0 - span * q
        if x < 10:
            continue
        tt = clamp01((hx - x) / (hx - 14))
        y = cy + rs.uniform(-1, 1) * (5 + 26 * tt)
        al = math.sin(math.pi * q) * (1 - tt) ** 0.6 * rs.uniform(0.6, 1.0)
        core = np.maximum(core, pad.disc_field(x, y, rs.uniform(0.7, 1.2)) * al)
        glow = np.maximum(glow, pad.disc_field(x, y, 2.4, soft=1.2) * al * 0.6)
    return finish(pad, glow, core, halo=((1.3, 0.5), (3.2, 0.42), (7.0, 0.24)), body_k=0.88, core_bloom=((1.3, 0.4), (3.2, 0.3)), win=3.5)


sprite("rfx_jsparkorb", "lookat_direction", 192, 96, 4, 2, job="mage", loop=(0, 3), zoom=0, alt=[VOLT],
       desc="번개 구슬 (오른쪽이 앞 · 이어 돌림)")(sparkorb_frame)


# ------------------------------------------------------------ rfx_jsunarrow (솔의 화살 — 오른쪽이 촉: 촉 끝 0.953 W · 촉 가운데 0.90 W · 해 가운데 0.84 W, 이어 돌림)

SA = dict(W=512, H=128, tip=490.0, hb=420.0, xs=422.0, Rs=30.0, sock0=398.0, s0=60.0)


def flame_vane(pad, sg, cy, x_front, x_back, hgt, ph, seed, n=4):
    """불꽃 깃 하나 (sg −1 = 위 · 1 = 아래): 살대를 따라 붙은 밑동에서 뒤 · 바깥으로 넘실거리는 불 혀 n 개 — (빛 몸, 심지)"""
    rnd = random.Random(seed)
    body = pad.zeros()
    core = pad.zeros()
    for i in range(n):
        u = i / max(1, n - 1)
        x0 = x_front - (x_front - x_back) * 0.62 * u
        L = (x_front - x_back) * (0.62 + 0.5 * (1 - u)) * (1 + 0.1 * math.sin(ph + i * 1.7 + seed))
        h = hgt * (0.55 + 0.45 * math.sin(math.pi * (0.25 + 0.6 * u)))
        off = rnd.uniform(0, TAU)
        pts = []
        for k in range(25):
            s = k / 24
            y = cy + sg * (2.0 + h * (s ** 0.75)) + 2.2 * s * math.sin(TAU * 0.9 * s - ph + off)
            pts.append((x0 - L * s, y))
        d, s_ = pad.line_field(pts, 12)
        w0 = 5.5 + 2.0 * (1 - u)
        wl = w0 * (1 - s_) ** 1.25 + 0.3
        m = smooth(wl + 0.9, wl - 0.9, d) * (s_ < 0.995)
        body = np.maximum(body, m * (0.9 - 0.25 * s_))
        core = np.maximum(core, pad.stroke(pts[:12], w0 * 0.42, 0.3, a0=0.7, a1=0.0))
    return body, core


def sunarrow_frame(f, frames=4):
    W, H = SA["W"], SA["H"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    tip, hb, xs, Rs, sock0, s0 = SA["tip"], SA["hb"], SA["xs"], SA["Rs"], SA["sock0"], SA["s0"]
    st = Stack(pad)
    rr, th = polar(pad, xs, cy)
    # ---- 해 (맨 뒤): 가는 빛살 스물넷 (길게 · 짧게 · 중간 — 뒤쪽이 더 김, 길이가 물결처럼 둘레를 돎) · 옅은 원판 · 굵은 테 · 가는 안 테 · 도는 구슬 열둘
    rays_g = pad.zeros()
    rays_c = pad.zeros()
    kind = [1.0, 0.42, 0.7, 0.42]
    for i in range(24):
        a = i * TAU / 24 + TAU / 48
        k = kind[i % 4]
        back = max(0.0, -math.cos(a))
        r0 = Rs + 3.5
        shim = 1 + 0.16 * math.cos(2 * a - ph) + 0.05 * math.cos(ph * 2 + i)
        r1 = r0 + 25.0 * k * (1 + 0.9 * back ** 1.4) * shim
        s_ = abs(math.sin(a))
        if s_ > 0.05:
            r1 = min(r1, (cy - 6.5) / s_)
        ux, uy = math.cos(a), math.sin(a)
        p0_, p1_ = (xs + ux * r0, cy + uy * r0), (xs + ux * r1, cy + uy * r1)
        wb = 1.0 + 1.4 * k
        rays_c = np.maximum(rays_c, pad.stroke([p0_, p1_], wb, 0.15, a0=0.95, a1=0.0))
        rays_g = np.maximum(rays_g, pad.stroke([p0_, p1_], wb * 2.2 + 0.8, 0.4, a0=0.6 * (0.55 + 0.45 * k), a1=0.0, soft=1.4))
    st.body = np.maximum(st.body, rays_g)
    st.core = np.maximum(st.core, rays_c)
    pulse = 0.88 + 0.12 * math.cos(ph)
    disk = pad.disc_field(xs, cy, Rs + 1.0)
    st.body = np.maximum(st.body, disk * (0.34 + 0.3 * smooth(Rs, Rs * 0.2, rr)))
    ring_c = band_cov(rr - Rs, 1.25) * pulse
    ring_c = np.maximum(ring_c, gauss(rr - Rs * 0.78, 0.45) * 0.5)
    rot = (TAU / 12) * f / frames  # 구슬: 4 장에 한 칸
    for i in range(12):
        a = rot + i * TAU / 12
        ring_c = np.maximum(ring_c, pad.disc_field(xs + math.cos(a) * Rs * 0.89, cy + math.sin(a) * Rs * 0.89, 1.15) * 0.85)
    st.core = np.maximum(st.core, ring_c)
    st.body = np.maximum(st.body, gauss(rr - Rs, 3.2) * 0.85)
    # ---- 살대: 긴 빛 막대 (위가 밝음) · 가운데 밝은 줄 · 앞으로 흐르는 빛 덩이 (주기 96 점 · 24 점/장)
    shaft = pad.poly([(s0, cy - 2.6), (sock0 + 2, cy - 3.1), (sock0 + 2, cy + 3.1), (s0, cy + 2.6)])
    flow_x = 24.0 * f
    pul = np.zeros_like(X)
    for kk in range(-1, 6):
        c0 = 70 + kk * 96 + flow_x
        pul = np.maximum(pul, np.exp(-0.5 * ((X - c0) / 9.0) ** 2) + 0.5 * np.exp(-0.5 * ((X - c0 + 16) / 5.0) ** 2))
    pul = clamp01(pul) * smooth(s0, s0 + 30, X) * smooth(sock0 + 4, sock0 - 20, X)
    sc = np.maximum(gauss(Y - cy + 0.4, 0.7) * 0.85, shaft * clamp01((cy - Y + 2.6) / 2.6) * 0.45)
    sc = np.maximum(sc, pul * gauss(Y - cy, 1.6) * 1.0)
    st.over(shaft, 0.86, sc * shaft)
    st.body = np.maximum(st.body, pul * gauss(Y - cy, 5.0) * 0.75)
    # ---- 불꽃 깃: 위 · 아래 깃 (뒤 · 바깥으로 넘실거림) + 가운데 (앞을 보는) 좁은 불꽃 · 오늬
    for sg, seed in ((-1, 3), (1, 7)):
        vb, vc = flame_vane(pad, sg, cy, 132.0, 46.0, 25.0, ph, seed)
        st.body = np.maximum(st.body, vb)
        st.core = np.maximum(st.core, vc)
    mid_pts = [(128 - 80 * s, cy - 1.0 + 3.0 * s * math.sin(TAU * 1.1 * s - ph + 0.8)) for s in np.linspace(0, 1, 24)]
    mid = pad.poly(taper_poly(mid_pts, lambda s: 4.5 * (1 - s) ** 1.1 * (0.6 + 0.4 * min(1.0, s / 0.15))))
    st.over(mid, 0.92, np.maximum(rim_of(mid, 0.7) * 0.85, pad.stroke(mid_pts[:14], 1.6, 0.3, a0=0.9, a1=0.1)) * mid)
    nock = pad.poly([(s0 - 10, cy - 4.2), (s0 + 3, cy - 3.0), (s0 + 3, cy + 3.0), (s0 - 10, cy + 4.2), (s0 - 5, cy)])
    st.over(nock, 0.9, rim_of(nock, 0.8) * nock)
    st.add_core(pad.disc_field(s0 - 1, cy, 1.6))
    # 깃에서 떨어져 뒤로 흩날리는 불티 (q = 4 장에 한 바퀴)
    re_ = random.Random(437)
    for i in range(12):
        b = re_.random()
        q = (b + f / frames) % 1.0
        x = re_.uniform(70, 110) - 75 * q
        if x < 10:
            continue
        y = cy + re_.choice((-1, 1)) * re_.uniform(4, 26) * (0.7 + 0.5 * q)
        al = math.sin(math.pi * q) * re_.uniform(0.5, 0.95)
        r = re_.uniform(0.8, 1.5)
        st.add_core(pad.stroke([(x, y), (x + 3 + 2 * r, y)], r, 0.2, a0=al, a1=0.0))
        st.body = np.maximum(st.body, pad.disc_field(x + 1, y, r * 2.3, soft=1.2) * al * 0.6)
    # ---- 촉 받침 (살대 쪽이 가는 통 · 고리 하나)
    sock = pad.poly([(sock0, cy - 4.0), (hb + 3, cy - 6.0), (hb + 3, cy + 6.0), (sock0, cy + 4.0)])
    lit_s, dark_s, _ = bevel_light(sock, 2.0)
    soc_c = rim_of(sock, 0.7) * 0.8 + lit_s * 0.55
    soc_c = np.maximum(soc_c, pad.stroke([(sock0 + 6, cy - 6), (sock0 + 6, cy + 6)], 1.3, a0=0.95) * sock)
    st.over(sock, 0.92 + 0.08 * lit_s - 0.15 * dark_s, soc_c * sock)
    # ---- 촉: 넓은 잎 날 (뒤로 뻗은 미늘 · 가운데 등줄 · 위 면 밝고 아래 면 어두움 · 날카로운 날 비탈)
    top = [(hb, cy - 5.5), (hb - 9, cy - 19.5), (hb + 4, cy - 21.0)] + bez((hb + 4, cy - 21.0), (hb + 38, cy - 20.5), (tip, cy), 24)[1:]
    bot = [(x, 2 * cy - y) for x, y in top]
    head_pts = top + bot[::-1]
    head = pad.poly(head_pts)
    upper = head * (Y < cy)
    lower = head * (Y >= cy)
    hl, hd, _ = bevel_light(head, 5.0)
    hcore = rim_of(head, 1.2) * 0.95
    hcore = np.maximum(hcore, upper * (0.62 + 0.3 * clamp01((X - hb) / (tip - hb))))
    hcore = np.maximum(hcore, lower * 0.22 * clamp01((X - hb) / (tip - hb)))
    hcore = np.maximum(hcore, pad.stroke([(hb + 2, cy), (tip - 3, cy)], 2.0, 0.7, a0=1.0, a1=0.95) * head)
    for sg in (-1, 1):
        # 날 비탈 (겉에서 조금 안쪽 — 갈아 세운 날)
        bev = [(x, cy + sg * (abs(y - cy) - 3.6)) for x, y in top[3:-3]]
        hcore = np.maximum(hcore, pad.stroke(bev, 0.7, a0=0.8) * head)
    hcore = np.maximum(hcore, hl * 0.5)
    st.over(head, upper * 1.0 + lower * 0.8, hcore * head)
    # ---- 반짝임: 촉 끝 (장마다 크기) · 해 둘레 (쌍으로 깜빡) · 살대 위 몇
    tw = [1.0, 0.55, 0.85, 0.45][f]
    st.add_core(sparkle(pad, tip - 4, cy, 9 + 6 * tw, 1.0, rot=0.15 * f, thin=0.11, diag=0.45))
    rs = random.Random(431)
    for i in range(6):
        a = rs.uniform(0, TAU)
        r = Rs + rs.uniform(10, 22)
        x, y = xs + math.cos(a) * r, cy + math.sin(a) * r * 0.9
        twi = 0.5 + 0.5 * math.cos(ph + i * 2.1)
        if 6 < y < H - 6:
            st.add_core(star4(pad, x, y, 3.0 + 4.0 * twi, 0.5 + 0.5 * twi, rot=0.0, thin=0.14, diag=0.0))
    for i in range(7):
        x = rs.uniform(140, 400)
        y = cy + rs.uniform(-1, 1) * 8
        twi = 0.5 + 0.5 * math.cos(ph + i * 1.7)
        st.add_core(star4(pad, x, y, 2.0 + 3.0 * twi, 0.35 + 0.55 * twi, rot=0.0, thin=0.16, diag=0.0))
    # 머리 쪽 큰 빛 무리
    st.body = np.maximum(st.body, pad.radial(xs + 14, cy, 76, 1.7) * 0.62)
    return finish(pad, st.body, st.core, halo=((1.5, 0.5), (4.0, 0.4), (9.0, 0.24)), body_k=0.92, core_bloom=((1.5, 0.35), (4.0, 0.28)), win=4.0)


sprite("rfx_jsunarrow", "lookat_direction", 512, 128, 4, 2, job="archer", loop=(0, 3), zoom=0, alt=[SUN],
       desc="솔의 화살 (오른쪽이 촉 · 이어 돌림)")(sunarrow_frame)


# ------------------------------------------------------------ rfx_jpinned (땅에 박힌 화살 · 묶는 빛 고리 — 땅 자리 (64, 170), 0 ~ 1 박힘 번쩍 · 2 ~ 5 버팀 (이어 돌림))

PN = dict(W=128, H=192, gx=64.0, gy=170.0, lean=math.radians(10.0), L=122.0, rx=44.0, ry=11.0)


def pinned_arrow(pad, st, ang, alpha=1.0):
    """박힌 화살 (땅 자리에서 위로): 살대 · 받침 (촉은 땅속) · 깃 셋 · 오늬 — 앞에 덮어 그림 (Stack). 로컬 x = 살대를 따라 위로 · y = 옆"""
    gx, gy, L = PN["gx"], PN["gy"], PN["L"]
    xf = Xf(gx, gy, ang)
    X, Y = pad.grid()
    lx, ly = -math.cos(ang + math.pi / 2), -math.sin(ang + math.pi / 2)  # 로컬 −y 의 칸 방향 (왼쪽 · 빛 쪽)
    # 깃 (양옆 — 왼쪽이 빛 쪽이라 밝음)
    for sg in (-1, 1):
        vp = bez((84, sg * 1.4), (97, sg * 11.5), (114, sg * 11.0), 14) + [(119.5, sg * 10.0), (114, sg * 1.2)]
        vm = pad.poly(xf(vp), alpha)
        vc = rim_of(vm, 0.8) * 0.9
        for k in range(9):
            xx = 89 + k * 3.3
            vc = np.maximum(vc, pad.stroke(xf([(xx, sg * 1.6), (xx + 6, sg * 11.0)]), 0.55, a0=0.45) * vm)
        lit = 1.0 if sg < 0 else 0.72
        st.over(vm, 0.78 * lit * alpha, vc * vm * lit)
    # 살대 (위 끝은 오늬 앞까지 · 빛 쪽 비탈이 밝음 · 가운데 빛 줄)
    shaft = pad.poly(xf([(-1, -2.8), (L - 2, -2.4), (L - 2, 2.4), (-1, 2.8)]), alpha)
    side = clamp01(((X - gx) * lx + (Y - gy) * ly) / 2.6 * 0.5 + 0.5)
    st.over(shaft, 0.86 * alpha, (shaft * side * 0.7 + rim_of(shaft, 0.55) * 0.35) * shaft)
    # 가운데 깃 (앞을 봄 — 좁고 밝음)
    mv = pad.poly(xf(bez((86, 0.0), (100, -5.2), (116, -4.2), 10) + [(119.5, -3.0), (116, 1.2)]), alpha)
    st.over(mv, 0.88 * alpha, (rim_of(mv, 0.7) * 0.9 + mv * 0.38) * mv)
    # 오늬 · 땅 가까이 촉 받침 (촉은 땅속)
    nock = pad.poly(xf([(L + 4.5, -3.8), (L - 3, -2.7), (L - 3, 2.7), (L + 4.5, 3.8), (L + 1.5, 0)]), alpha)
    st.over(nock, 0.9 * alpha, rim_of(nock, 0.7) * 0.9 * nock)
    fer = pad.poly(xf([(-2, -3.8), (10, -3.2), (10, 3.2), (-2, 3.8)]), alpha)
    fc = rim_of(fer, 0.6) * 0.75
    for xx in (2.5, 7.0):
        fc = np.maximum(fc, pad.stroke(xf([(xx, -3.9), (xx, 3.9)]), 0.9, a0=0.9) * fer)
    st.over(fer, 0.9 * alpha, fc * fer)
    return np.maximum.reduce([shaft, fer, nock])


def pinned_frame(f, frames=6):
    W, H = PN["W"], PN["H"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    gx, gy, rx, ry = PN["gx"], PN["gy"], PN["rx"], PN["ry"]
    base_ang = -(math.pi / 2 - PN["lean"])
    st = Stack(pad)
    hold = f >= 2
    p = ((f - 2) % 4) / 4 if hold else 0.0
    pulse = 0.5 + 0.5 * math.cos(TAU * p) if hold else 0.0
    ring_k = [0.0, 0.55, 1.0, 1.0, 1.0, 1.0][f]
    # ---- 묶는 빛 고리 — 뒤 반 (화살 뒤): 납작한 타원 둘 · 사이를 도는 새김 열둘 · 안쪽 옅은 빛 바닥
    rot = TAU / 12 * p

    def ring_layer(front):
        a0, a1 = (0.0, math.pi) if front else (math.pi, TAU)
        k = (1.0 if front else 0.62) * ring_k
        c_ = pad.zeros()
        g_ = pad.zeros()
        if ring_k <= 0:
            return g_, c_
        wv = 1.0 + 0.35 * pulse
        outer = ellipse_pts(gx, gy, rx, ry, a0, a1, 48)
        inner = ellipse_pts(gx, gy, rx * 0.8, ry * 0.8, a0, a1, 48)
        c_ = np.maximum(c_, pad.stroke(outer, (2.2 if front else 1.4) * wv, a0=k * (0.85 + 0.15 * pulse)))
        c_ = np.maximum(c_, pad.stroke(inner, (1.0 if front else 0.7), a0=k * 0.7))
        g_ = np.maximum(g_, pad.stroke(outer, 5.5 * wv, a0=k * (0.5 + 0.22 * pulse), soft=2.2))
        g_ = np.maximum(g_, pad.stroke(inner, 3.0, a0=k * 0.3, soft=1.2))
        for i in range(12):
            a = rot + i * TAU / 12
            if (math.sin(a) >= 0) != front:
                continue
            ca_, sa_ = math.cos(a), math.sin(a)
            x0, y0 = gx + ca_ * rx * 0.8, gy + sa_ * ry * 0.8
            x1, y1 = gx + ca_ * rx, gy + sa_ * ry
            mx_, my_ = (x0 + x1) / 2, (y0 + y1) / 2
            # 타원을 따라가는 방향 (마름모를 옆으로 늘림)
            tx, ty = -sa_ * rx, ca_ * ry
            tl = math.hypot(tx, ty) or 1
            tx, ty = tx / tl, ty / tl
            if i % 3 == 0:
                c_ = np.maximum(c_, pad.poly([(x0, y0), (mx_ + tx * 2.6, my_ + ty * 2.6), (x1, y1), (mx_ - tx * 2.6, my_ - ty * 2.6)], k * 0.95))
                g_ = np.maximum(g_, pad.disc_field(mx_, my_, 3.0, soft=1.5) * k * 0.5)
            else:
                c_ = np.maximum(c_, pad.stroke([(mx_ - tx * 1.8, my_ - ty * 1.8), (x1, y1), (mx_ + tx * 1.8, my_ + ty * 1.8)], 0.9, a0=k * 0.8))
        return g_, c_

    if ring_k > 0:
        pool = clamp01(1 - ((X - gx) / (rx * 0.95)) ** 2 - ((Y - gy) / (ry * 0.95)) ** 2)
        st.body = np.maximum(st.body, pool ** 0.6 * (0.3 + 0.12 * pulse) * ring_k)
        # 빛 기둥: 고리 안에서 위로 옅어지며 솟음
        col = gauss(X - gx, rx * 0.42) * np.exp(-np.maximum(0, gy - Y) / 34.0) * smooth(gy + ry * 0.6, gy - 2, Y)
        st.body = np.maximum(st.body, col * (0.22 + 0.1 * pulse) * ring_k)
    gb, cb = ring_layer(False)
    st.body = np.maximum(st.body, gb)
    st.core = np.maximum(st.core, cb)
    # ---- 화살 (0 ~ 1 은 박힌 떨림 — 잔상 하나)
    if f == 0:
        pinned_arrow(pad, st, base_ang + math.radians(-5.0), 0.4)
        pinned_arrow(pad, st, base_ang + math.radians(3.5), 1.0)
    elif f == 1:
        pinned_arrow(pad, st, base_ang + math.radians(1.8), 0.4)
        pinned_arrow(pad, st, base_ang + math.radians(-1.6), 1.0)
    else:
        pinned_arrow(pad, st, base_ang, 1.0)
    # ---- 고리 앞 반 (화살 앞)
    gf_, cf_ = ring_layer(True)
    st.body = np.maximum(st.body, gf_)
    st.core = np.maximum(st.core, cf_)
    if hold:
        # 고리에서 솟는 빛 줄 · 오르는 빛 알갱이 (q = 4 장에 한 바퀴)
        rnd = random.Random(515)
        for i in range(6):
            a = rnd.uniform(0.15, math.pi - 0.15)
            x0, y0 = gx + math.cos(a) * rx * 0.9, gy + math.sin(a) * ry * 0.9
            L = rnd.uniform(26, 48)
            tw = 0.5 + 0.5 * math.cos(TAU * p + i * 1.9)
            st.add_core(pad.stroke([(x0, y0), (x0, y0 - L)], 1.0, 0.2, a0=0.55 * tw, a1=0.0))
            st.body = np.maximum(st.body, pad.stroke([(x0, y0), (x0, y0 - L)], 4.0, 1.0, a0=0.3 * tw, a1=0.0, soft=1.5))
        for i in range(9):
            b = rnd.random()
            q = (b + p) % 1.0
            a = rnd.uniform(0, TAU)
            x = gx + math.cos(a) * rx * rnd.uniform(0.3, 0.95)
            y = gy + math.sin(a) * ry * 0.9 - 64 * q
            al = math.sin(math.pi * q) * rnd.uniform(0.6, 1.0)
            st.add_core(star4(pad, x, y, 2.0 + 2.5 * math.sin(math.pi * q), al, rot=0.0, thin=0.18, diag=0.0))
            st.body = np.maximum(st.body, pad.disc_field(x, y, 2.4, soft=1.2) * al * 0.5)
    else:
        # ---- 박힘: 번쩍 · 땅을 따라 퍼지는 충격 고리 · 튀어 오르는 흙 · 불티
        k = 1.0 if f == 0 else 0.62
        st.add_core(sparkle(pad, gx, gy - 4, (40 if f == 0 else 26), k, rot=0.0, thin=0.07, diag=0.45))
        st.add_core(pad.disc_field(gx, gy - 2, 6.5 * k, soft=2.0) * k)
        st.body = np.maximum(st.body, pad.radial(gx, gy - 6, 40 * k + 8, 1.3) * k)
        srx, sry = (27.0, 6.8) if f == 0 else (50.0, 12.4)
        sh = ellipse_pts(gx, gy, srx, sry, 0, TAU, 72)
        st.add_core(pad.stroke(sh, 2.6 if f == 0 else 1.3, a0=0.95 if f == 0 else 0.6))
        st.body = np.maximum(st.body, pad.stroke(sh, 8.0 if f == 0 else 6.0, a0=0.75 if f == 0 else 0.4, soft=2.5))
        rnd = random.Random(717)
        q = 0.45 if f == 0 else 1.0
        for i in range(14):
            a = -math.pi / 2 + rnd.uniform(-1.45, 1.45)
            sp = rnd.uniform(22, 44)
            x = gx + math.cos(a) * sp * q
            y = gy - 3 + math.sin(a) * sp * q * 0.9 + 16 * q * q
            vx, vy = math.cos(a) * sp, math.sin(a) * sp * 0.9 + 32 * q
            Lv = math.hypot(vx, vy) or 1
            al = (1.0 if f == 0 else 0.7) * rnd.uniform(0.6, 1.0)
            Ls = 7 if f == 0 else 5
            st.add_core(streak(pad, (x - vx / Lv * Ls, y - vy / Lv * Ls), (x, y), rnd.uniform(1.0, 1.6), al))
            st.body = np.maximum(st.body, pad.disc_field(x, y, 2.6, soft=1.3) * al * 0.5)
        # 흙먼지 (땅에 붙은 옅은 덩이)
        for sg in (-1, 1):
            for j in range(3):
                x = gx + sg * (10 + j * 12) * (0.7 + 0.5 * q)
                st.body = np.maximum(st.body, pad.disc_field(x, gy - 2 - j, 7.0 - j, soft=4.0) * (0.45 if f == 0 else 0.3))
    vfade = smooth(H - 1.0, H - 7.0, Y)
    return finish(pad, st.body * vfade, st.core * vfade, halo=((1.2, 0.5), (3.0, 0.38), (7.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.34), (3.0, 0.26)), win=3.5)


sprite("rfx_jpinned", "lookat_y", 128, 192, 6, 6, job="archer", loop=(2, 5), zoom=3, alt=[SUN],
       desc="땅에 박힌 화살 · 묶는 빛 고리 (0 ~ 1 박힘 · 2 ~ 5 이어 돌림)")(pinned_frame)


# ------------------------------------------------------------ rfx_jmaulswing (토르의 망치 내려치기 — 0 ~ 3 오른쪽 위에서 호를 그리며 가운데 아래로 · 4 맞음 · 5 사그라짐)
# 망치 로컬: 머리 가운데 (0, 0) · x = 머리 긴 축 · −y = 자루 쪽 (위). 맞는 자리 (머리 아래 끝) = (192, 324)

MS = dict(S=384, bx=192.0, by=324.0)
MS_PATH = [(286.0, 106.0), (332.0, 186.0), (226.0, 222.0), (192.0, 291.0)]  # 머리 가운데가 지나는 3차 곡선
MS_ANG0 = math.radians(-112.0)
MS_T = [0.0, 0.34, 0.74, 1.0, 1.0, 1.0]  # 장마다 휘두른 몫
MS_WIN = [None, (0.0, 0.34), (0.16, 0.74), (0.42, 1.0), None, None]  # 잔상 구간


def ms_pose(t):
    """휘두른 몫 t (0 ~ 1) → (머리 가운데, 돌린 각) — 각은 조금 먼저 곧게 섬"""
    p0, p1, p2, p3 = MS_PATH
    u = 1 - t
    k0, k1, k2, k3 = u ** 3, 3 * u * u * t, 3 * u * t * t, t ** 3
    x = k0 * p0[0] + k1 * p1[0] + k2 * p2[0] + k3 * p3[0]
    y = k0 * p0[1] + k1 * p1[1] + k2 * p2[1] + k3 * p3[1]
    a = MS_ANG0 * (1 - ease_out(min(1.0, t * 1.08), 1.6))
    return (x, y), a


def hammer_parts():
    """망치 다각형들 (로컬)"""
    body = [(-49, -26), (49, -26), (49, 26), (-49, 26)]
    caps = []
    for sg in (-1, 1):
        cp = [(46, -31), (57, -33.5), (62.5, -29), (62.5, 29), (57, 33.5), (46, 31)]
        caps.append([(sg * x, y) for x, y in cp])
    collar = [(-15, -28), (-12, -32), (12, -32), (15, -28), (15, 28), (12, 32), (-12, 32), (-15, 28)]
    shaft = [(-7.5, -30), (7.5, -30), (6.4, -150), (-6.4, -150)]
    rings = [[(-9.5, yy - 3), (9.5, yy - 3), (9.5, yy + 3), (-9.5, yy + 3)] for yy in (-43.0, -141.0)]
    pommel = [(0, -163), (6.5, -160), (9.5, -153), (6.5, -146), (-6.5, -146), (-9.5, -153), (-6.5, -160)]
    loop = bez((0, -162), (-15, -178), (0, -192), 12) + bez((0, -192), (15, -178), (0, -162), 12)[1:]
    return dict(body=body, caps=caps, collar=collar, shaft=shaft, rings=rings, pommel=pommel, loop=loop)


def hammer_head_poly(xf):
    """잔상용 머리 겉 (머리 판 + 양끝 때리는 면)"""
    return xf([(-62.5, -29), (-57, -33.5), (57, -33.5), (62.5, -29), (62.5, 29), (57, 33.5), (-57, 33.5), (-62.5, 29)])


def hammer_draw(pad, st, xf, alpha=1.0, seed=0, arcs=1.0):
    """망치 (Stack 에 덮어 그림): 자루 · 감은 가죽 · 고리 · 자루 끝 · 끈 고리 → 머리 판 · 때리는 면 · 가운데 띠 · 새김 · 징 · 기어 다니는 번개. 겉 (모양) 을 돌려줌"""
    P = hammer_parts()
    X, Y = pad.grid()
    sil = pad.zeros()

    def put(pts, base, rimw=1.0, depth=3.0, extra=None, lit_k=0.5):
        m = pad.poly(xf(pts))
        lit, dark, _ = bevel_light(m, depth)
        c_ = rim_of(m, rimw) * 0.9 + lit * lit_k
        if extra is not None:
            c_ = np.maximum(c_, extra)
        st.over(m * alpha, (base + 0.1 * lit - 0.22 * dark) * alpha, c_ * m * alpha)
        return m

    # 끈 고리 (자루 끝에서)
    loop = xf(P["loop"])
    st.body = np.maximum(st.body, pad.stroke(loop, 3.2, a0=0.75 * alpha))
    st.core = np.maximum(st.core, pad.stroke(loop, 1.0, a0=0.7 * alpha))
    # 자루 · 감은 가죽 (비스듬한 줄)
    sh = pad.poly(xf(P["shaft"]))
    wrap = pad.zeros()
    yy = -50.0
    while yy > -137:
        wrap = np.maximum(wrap, pad.stroke(xf([(-8, yy), (8, yy - 6)]), 1.3, a0=0.85))
        yy -= 7.0
    m = put(P["shaft"], 0.74, 0.8, 3.0, wrap * sh * 0.8, lit_k=0.45)
    sil = np.maximum(sil, m)
    for rg in P["rings"]:
        sil = np.maximum(sil, put(rg, 0.9, 0.8, 1.8))
    pm = pad.poly(xf(P["pommel"]))
    gem = pad.poly(xf([(0, -159), (4, -153), (0, -149), (-4, -153)]))
    sil = np.maximum(sil, put(P["pommel"], 0.9, 0.9, 3.0, gem * 0.95))
    # 머리 판: 위 · 아래 모서리 깎은 면 (위 밝음 · 아래 어두움) · 양쪽 반에 새김 (지어낸 각진 글자 — 실제 룬이 아님) · 새김 둘레 마름모 점
    body = pad.poly(xf(P["body"]))
    deco = pad.zeros()
    top_bevel = pad.poly(xf([(-49, -26), (49, -26), (45, -19), (-45, -19)]))
    deco = np.maximum(deco, top_bevel * 0.5)
    for yy in (-19.0, 19.0):
        deco = np.maximum(deco, pad.stroke(xf([(-45, yy), (45, yy)]), 0.9, a0=0.6))
    for sg in (-1, 1):
        gx_, gy_ = xf.p(sg * 31, 0)
        deco = np.maximum(deco, glyph_mask(pad, 3 if sg < 0 else 8, gx_, gy_, 17, 25, 2.6, ang=xf.ang) * 0.95)
        for dx in (-13.0, 13.0):
            px_, py_ = xf.p(sg * 31 + dx, 0)
            deco = np.maximum(deco, pad.poly([(px_ + 2.2, py_), (px_, py_ - 2.2), (px_ - 2.2, py_), (px_, py_ + 2.2)], 0.8))
    sil = np.maximum(sil, put(P["body"], 0.82, 1.2, 7.0, deco * body, lit_k=0.5))
    for cp in P["caps"]:
        cm = pad.poly(xf(cp))
        x_out = max(x for x, _ in cp) if cp[2][0] > 0 else min(x for x, _ in cp)
        sg = 1 if x_out > 0 else -1
        face = xf([(sg * 56, -26), (sg * 59.5, -24), (sg * 59.5, 24), (sg * 56, 26)])
        fe = pad.stroke(face + [face[0]], 1.0, a0=0.75) * cm
        for yy in (-24.0, 24.0):
            px_, py_ = xf.p(sg * 51, yy)
            fe = np.maximum(fe, pad.disc_field(px_, py_, 2.0) * 0.95)
        sil = np.maximum(sil, put(cp, 0.86, 1.2, 6.0, fe, lit_k=0.6))
    col = pad.poly(xf(P["collar"]))
    cd = pad.zeros()
    for yy in (-21.0, -7.0, 7.0, 21.0):
        px_, py_ = xf.p(0, yy)
        cd = np.maximum(cd, pad.disc_field(px_, py_, 2.1) * 0.95)
    cd = np.maximum(cd, pad.stroke(xf([(-9, -30), (-9, 30)]), 0.8, a0=0.55))
    cd = np.maximum(cd, pad.stroke(xf([(9, -30), (9, 30)]), 0.8, a0=0.55))
    sil = np.maximum(sil, put(P["collar"], 0.92, 1.0, 4.0, cd * col, lit_k=0.6))
    # 번개: 머리 위를 기어 다니는 줄기 · 겉에서 튀는 짧은 갈래 (장마다 다름)
    if arcs > 0:
        rnd = random.Random(1200 + seed)
        ac, ag = pad.zeros(), pad.zeros()
        for i in range(3):
            p0 = xf.p(rnd.uniform(-56, 56), rnd.uniform(-28, 28))
            p1 = xf.p(rnd.uniform(-56, 56), rnd.uniform(-28, 28))
            pts = bolt_pts(rnd, p0, p1, 0.2, 4, 0.55)
            ac = np.maximum(ac, pad.stroke(pts, 2.0, a0=1.0))
            ag = np.maximum(ag, pad.stroke(pts, 6.5, a0=0.6, soft=2.5))
        for i in range(3):
            lx_, ly_ = rnd.choice(((rnd.uniform(-60, 60), -34), (rnd.uniform(-60, 60), 34), (-63, rnd.uniform(-26, 26)), (63, rnd.uniform(-26, 26))))
            p0 = xf.p(lx_, ly_)
            out = xf.vec(np.sign(lx_) if abs(lx_) > 62 else 0, np.sign(ly_) if abs(lx_) <= 62 else 0)
            ang = math.atan2(out[1], out[0]) + rnd.uniform(-0.6, 0.6)
            L = rnd.uniform(18, 32)
            pts = bolt_pts(rnd, p0, (p0[0] + math.cos(ang) * L, p0[1] + math.sin(ang) * L), 0.24, 4, 0.55)
            ac = np.maximum(ac, pad.stroke(pts, 1.6, 0.4, a0=1.0, a1=0.4))
            ag = np.maximum(ag, pad.stroke(pts, 5.0, 1.5, a0=0.45, a1=0.1, soft=2.0))
        st.add_core(ac * arcs * alpha)
        st.body = np.maximum(st.body, ag * arcs * alpha)
    return sil


def ms_fork(rnd, p0, ang, L, disp=0.2):
    p1 = (p0[0] + math.cos(ang) * L, p0[1] + math.sin(ang) * L)
    return bolt_pts(rnd, p0, p1, disp, 5, 0.55)


def maulswing_frame(f, frames=6):
    S = MS["S"]
    pad = Pad(S, S)
    X, Y = pad.grid()
    bx, by = MS["bx"], MS["by"]
    st = Stack(pad)
    t = MS_T[f]
    (hx, hy), ang = ms_pose(t)
    # ---- 잔상: 머리가 지나온 길 (머리 겉을 촘촘히 겹침 — 머리 가까이 밝음) · 자루가 쓸고 간 옅은 부채 · 빠른 줄 (머리 바깥 모서리 · 끝면이 지난 자리)
    win = MS_WIN[f]
    if win:
        t0, t1 = win
        n = 30
        sm = pad.zeros()
        fan = pad.zeros()
        trails = {k: [] for k in range(4)}
        for i in range(n + 1):
            u = i / n
            tt = t0 + (t1 - t0) * u
            (sx, sy), sa = ms_pose(tt)
            xf_ = Xf(sx, sy, sa)
            a = u ** 1.6
            sm = np.maximum(sm, pad.poly(hammer_head_poly(xf_), a))
            if i % 3 == 0:
                fan = np.maximum(fan, pad.poly(xf_([(-7, -30), (7, -30), (6, -150), (-6, -150)]), a * 0.35))
            for k, (lx_, ly_) in enumerate(((62.5, -26), (62.5, 26), (-62.5, -26), (-62.5, 26))):
                trails[k].append(xf_.p(lx_, ly_))
        st.body = np.maximum(st.body, gblur(sm, 2.0 * SS) * 0.95)
        st.body = np.maximum(st.body, fan)
        smc = np.maximum(sm - 0.25, 0) * 0.55
        st.core = np.maximum(st.core, smc)
        # 바깥 날 (곡선 바깥쪽 모서리 두 줄은 밝게 · 안쪽은 옅게)
        for k, pts in trails.items():
            if len(pts) < 2:
                continue
            outer = k in (0, 1)
            st.core = np.maximum(st.core, pad.stroke(pts, 0.4, 2.6 if outer else 1.4, a0=0.0, a1=0.95 if outer else 0.5))
            st.body = np.maximum(st.body, pad.stroke(pts, 1.0, 9.0 if outer else 5.0, a0=0.0, a1=0.6, soft=2.5))
    # ---- 망치 (0 ~ 4 또렷 · 5 옅어짐)
    xf = Xf(hx, hy, ang)
    alpha = [1.0, 1.0, 1.0, 1.0, 1.0, 0.72][f]
    hammer_draw(pad, st, xf, alpha, seed=f * 7, arcs=[1.0, 0.8, 0.8, 1.0, 1.0, 0.5][f])
    if f == 0:
        # 들어 올린 망치에 모이는 빛 (힘을 모음) · 둘레 불똥
        st.body = np.maximum(st.body, pad.radial(hx, hy, 84, 1.6) * 0.55)
        rnd = random.Random(31)
        for i in range(10):
            a = rnd.uniform(0, TAU)
            r = rnd.uniform(70, 100)
            x, y = hx + math.cos(a) * r, hy + math.sin(a) * r
            if 8 < x < S - 8 and 8 < y < S - 8:
                st.add_core(streak(pad, (x, y), (x - math.cos(a) * 9, y - math.sin(a) * 9), 1.3, 0.8))
    # ---- 맞음 (3: 닿는 순간 · 4: 터짐 · 5: 사그라짐): 번쩍 · 땅 충격 고리 (납작한 타원) · 사방으로 갈라지는 번개 · 빛나는 땅 금 · 튀는 돌 · 불티
    if f >= 3:
        q = {3: 0.0, 4: 1.0, 5: 1.8}[f]
        env = {3: 0.75, 4: 1.0, 5: 0.5}[f]
        under = smooth(by + 50, by + 14, Y)  # 땅 아래로는 빛이 덜 (칸 아래 끝에서 잘리지 않게)
        if f == 3:
            st.add_core(sparkle(pad, bx, by - 2, 46, 1.0, rot=0.0, thin=0.08, diag=0.4) * under)
            st.body = np.maximum(st.body, pad.radial(bx, by, 60, 1.4) * 0.8 * under)
        if f == 4:
            st.add_core(sparkle(pad, bx, by - 6, 130, 1.0, rot=0.0, thin=0.05, diag=0.42) * under)
            st.add_core(pad.disc_field(bx, by - 4, 22, soft=6.0))
            st.body = np.maximum(st.body, pad.radial(bx, by - 14, 150, 1.25) * under)
        if f == 5:
            st.add_core(sparkle(pad, bx, by - 4, 40, 0.6, rot=0.3, thin=0.08, diag=0.0) * under)
            st.body = np.maximum(st.body, pad.radial(bx, by - 10, 96, 1.5) * 0.45 * under)
        # 땅 충격 고리
        if f >= 4:
            srx, sry = (112.0, 27.0) if f == 4 else (160.0, 38.0)
            ring = ellipse_pts(bx, by, srx, sry, 0, TAU, 120)
            st.add_core(pad.stroke(ring, 3.2 if f == 4 else 1.4, a0=env))
            st.body = np.maximum(st.body, pad.stroke(ring, 12.0 if f == 4 else 8.0, a0=env * 0.75, soft=4.0))
            inner = ellipse_pts(bx, by, srx * 0.72, sry * 0.72, 0, TAU, 100)
            st.add_core(pad.stroke(inner, 1.2, a0=env * 0.55))
        # 땅 금 (가운데에서 양옆으로 · 땅 높이에 붙어 납작하게)
        rnd = random.Random(42)
        cr_k = {3: 0.45, 4: 1.0, 5: 0.7}[f]
        for i in range(8):
            sgn = -1 if i % 2 == 0 else 1
            a = rnd.uniform(0.0, 0.42) * (1 if rnd.random() < 0.5 else -1)
            L = rnd.uniform(60, 130) * (0.55 if f == 3 else 1.0)
            p1 = (bx + sgn * math.cos(a) * L, by + math.sin(a) * L * 0.3)
            pts = bolt_pts(rnd, (bx, by), p1, 0.14, 4, 0.55)
            st.add_core(pad.stroke(pts, 2.0 * cr_k, 0.5, a0=cr_k, a1=cr_k * 0.4))
            st.body = np.maximum(st.body, pad.stroke(pts, 6.0 * cr_k, 2.0, a0=cr_k * 0.6, a1=0.1, soft=2.0))
        # 번개 갈래 (4 가 가장 큼 · 5 는 몇 개만 가늘게)
        if f >= 4:
            rnd = random.Random(77 if f == 4 else 78)
            angs = [-172, -150, -128, -104, -76, -52, -30, -8]
            nb = 8 if f == 4 else 4
            for i in range(nb):
                a = math.radians(angs[i if f == 4 else i * 2] + rnd.uniform(-8, 8))
                L = rnd.uniform(110, 170) if f == 4 else rnd.uniform(70, 120)
                pts = ms_fork(rnd, (bx, by - 6), a, L)
                pts = [(min(S - 10, max(10, x)), min(S - 10, max(10, y))) for x, y in pts]
                wk = 1.0 if f == 4 else 0.55
                st.add_core(pad.stroke(pts, 3.0 * wk, 0.7, a0=env, a1=env * 0.5))
                st.body = np.maximum(st.body, pad.stroke(pts, 9.0 * wk, 2.5, a0=env * 0.85, a1=env * 0.3, soft=3.0))
                for b in range(2):
                    j = rnd.randint(len(pts) // 4, len(pts) * 3 // 4)
                    sx, sy = pts[j]
                    ba = a + rnd.choice((-1, 1)) * rnd.uniform(0.35, 0.8)
                    br = ms_fork(rnd, (sx, sy), ba, rnd.uniform(24, 50) * (1 if f == 4 else 0.7), 0.25)
                    br = [(min(S - 10, max(10, x)), min(S - 10, max(10, y))) for x, y in br]
                    st.add_core(pad.stroke(br, 1.6 * wk, 0.4, a0=env * 0.9, a1=env * 0.3))
                    st.body = np.maximum(st.body, pad.stroke(br, 5.0 * wk, 1.5, a0=env * 0.55, a1=0.1, soft=2.0))
                ex, ey = pts[-1]
                st.add_core(star4(pad, ex, ey, 5.0 * wk + 2, env * 0.85, rot=0.3, thin=0.16, diag=0.0))
        # 튀는 돌 조각 · 불티 (4 → 5 로 날아감)
        if f >= 4:
            rnd = random.Random(91)
            for i in range(16):
                a = -math.pi / 2 + rnd.uniform(-1.3, 1.3)
                sp = rnd.uniform(50, 110)
                x = bx + math.cos(a) * sp * (0.55 * q)
                y = by - 8 + math.sin(a) * sp * (0.55 * q) + 22 * q * q
                al = (1.0 if f == 4 else 0.55) * rnd.uniform(0.6, 1.0)
                if i % 2 == 0:
                    r = rnd.uniform(3.0, 5.5)
                    pts = rot_pts([(x - r, y - r * 0.6), (x + r * 0.8, y - r), (x + r, y + r * 0.5), (x - r * 0.6, y + r)], x, y, rnd.uniform(0, TAU) + q)
                    m = pad.poly(pts)
                    st.over(m * al, 0.8 * al, (rim_of(m, 0.6) * 0.85 + m * 0.3) * al)
                else:
                    vx, vy = math.cos(a) * sp, math.sin(a) * sp + 40 * q
                    Lv = math.hypot(vx, vy) or 1
                    st.add_core(streak(pad, (x - vx / Lv * 10, y - vy / Lv * 10), (x, y), 1.6, al))
                    st.body = np.maximum(st.body, pad.disc_field(x, y, 3.5, soft=1.8) * al * 0.5)
        # 흙먼지 (땅에 붙은 옅은 덩이 — 5 에서 퍼짐)
        if f >= 4:
            for sg in (-1, 1):
                for j in range(4):
                    x = bx + sg * (24 + j * 26) * (0.8 + 0.25 * q)
                    st.body = np.maximum(st.body, pad.disc_field(x, by - 6 - j * 2, 14 - j * 2, soft=8.0) * (0.45 if f == 4 else 0.32))
    return finish(pad, st.body, st.core, halo=((1.6, 0.5), (4.5, 0.38), (10.0, 0.22)), body_k=0.92, core_bloom=((1.5, 0.35), (4.5, 0.28)), win=5.0)


sprite("rfx_jmaulswing", "rotate_xyz", 384, 384, 6, 4, job="paladin", zoom=4, alt=[THUNDER],
       desc="토르의 망치 내려치기 (0 ~ 3 휘두름 · 4 맞음 · 5 사그라짐)")(maulswing_frame)


# ------------------------------------------------------------ rfx_jwheel (번개 수레 바퀴 자국 — 땅에 눕는 띠, 길이 = x · 자국 둘 = 가운데 ± 13 점, 양 끝 7% 가 옅어짐 (이어 붙임))

WH = dict(W=256, H=64, off=13.0, end=18.0)
WH_ENV = [1.0, 0.94, 0.74, 0.54, 0.34, 0.17]


def wheel_frame(f, frames=6):
    W, H = WH["W"], WH["H"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    env = WH_ENV[f]
    ef = smooth(0.0, WH["end"], X) * smooth(W, W - WH["end"], X)
    glow = pad.zeros()
    core = pad.zeros()
    hot = [1.0, 0.9, 0.6, 0.4, 0.25, 0.12][f]  # 갓 그은 자국의 흰 속불 → 식으며 끊긴 불씨 줄로
    for k, sg in enumerate((-1, 1)):
        yt = cy + sg * WH["off"] + 0.7 * np.sin(X * 0.031 + k * 2.0) + 0.35 * np.sin(X * 0.11 + k)
        n = fbm(X / 9.0 + k * 13.0, np.full_like(X, 0.5 + k * 3.0), seed=91 + k, octaves=3)
        n2 = fbm(X / 3.5 + k * 7.0, np.full_like(X, 2.5 + k), seed=93 + k, octaves=2)
        d = Y - yt
        # 그을린 홈: 넓은 빛 (얼룩) · 가운데 밝은 줄 (식으면 끊김) · 가장자리 가는 줄 둘 (바퀴 테가 판 자리)
        wg = 3.4 + 1.2 * hot
        groove = gauss(d, wg) * (0.42 + 0.58 * clamp01(0.5 + 1.4 * n)) * env
        glow = np.maximum(glow, groove)
        brk = clamp01((n + 0.55 - (1 - hot) * 0.9) / 0.18)
        line = band_cov(d, 0.55 + 0.6 * hot) * brk * (0.7 + 0.3 * clamp01(0.5 + n2)) * env
        core = np.maximum(core, line)
        for e in (-1, 1):
            core = np.maximum(core, gauss(d - e * (2.6 + 0.6 * hot), 0.42) * (0.35 + 0.25 * hot) * clamp01(0.4 + n2) * env)
        # 홈 속 불씨 (점)
        rnd = random.Random(950 + k * 7 + f)
        for i in range(int(10 + 8 * (1 - hot))):
            x = rnd.uniform(WH["end"], W - WH["end"])
            y = float(cy + sg * WH["off"] + 0.7 * math.sin(x * 0.031 + k * 2.0)) + rnd.uniform(-1.5, 1.5)
            al = rnd.uniform(0.5, 1.0) * env
            core = np.maximum(core, pad.disc_field(x, y, rnd.uniform(0.6, 1.1)) * al)
    # 두 자국 사이 옅은 기운 띠
    glow = np.maximum(glow, gauss(Y - cy, 9.0) * 0.14 * env)
    # ---- 번개: 두 자국 사이를 건너뛰는 줄기 · 자국을 따라 기는 줄기 · 튀는 불똥 (장마다 다름 — 처음 둘이 가장 많음)
    rnd = random.Random(970 + f * 13)
    n_cross = [5, 4, 3, 2, 1, 0][f]
    n_along = [4, 4, 3, 2, 1, 1][f]
    ak = [1.0, 1.0, 0.85, 0.7, 0.55, 0.45][f]
    y0t, y1t = cy - WH["off"], cy + WH["off"]
    span0, span1 = WH["end"] + 18, W - WH["end"] - 18
    for i in range(n_cross):
        # 띠를 고르게 나눈 칸마다 하나 (칸 안에서 흔듦 — 이어 붙여도 한쪽에 몰리지 않게)
        x0 = span0 + (span1 - span0) * (i + rnd.uniform(0.1, 0.9)) / n_cross
        x1 = x0 + rnd.choice((-1, 1)) * rnd.uniform(14, 34)
        if rnd.random() < 0.5:
            pa, pb = (x0, y0t), (x1, y1t)
        else:
            pa, pb = (x1, y1t), (x0, y0t)
        # 곧게 건너지 않고 휘어 건넘 (가운데가 옆으로 불룩)
        mid = ((pa[0] + pb[0]) / 2 + rnd.uniform(-14, 14), (pa[1] + pb[1]) / 2 + rnd.uniform(-4, 4))
        base = bez(pa, mid, pb, 4)
        pts = jag_path(rnd, base, 0.3, 3, 0.58)
        pts = [(x, min(H - 7.0, max(7.0, y))) for x, y in pts]
        core = np.maximum(core, pad.stroke(pts, 1.6 * ak, 1.0 * ak, a0=env * 1.05, a1=env))
        glow = np.maximum(glow, pad.stroke(pts, 5.5 * ak, a0=env * 0.72, soft=2.0))
        for q_ in (pa, pb):
            core = np.maximum(core, pad.disc_field(q_[0], q_[1], 1.6 * ak) * env)
            glow = np.maximum(glow, pad.disc_field(q_[0], q_[1], 5.0, soft=2.5) * env * 0.6)
        for b in range(rnd.randint(1, 2)):
            j = rnd.randint(len(pts) // 5, len(pts) * 4 // 5)
            bx_, by_ = pts[j]
            ba = rnd.uniform(0, TAU)
            bl = rnd.uniform(8, 16)
            br = bolt_pts(rnd, (bx_, by_), (bx_ + math.cos(ba) * bl, min(H - 8.0, max(8.0, by_ + math.sin(ba) * bl * 0.6))), 0.3, 3, 0.6)
            core = np.maximum(core, pad.stroke(br, 1.0 * ak, 0.3, a0=env * 0.9, a1=env * 0.3))
            glow = np.maximum(glow, pad.stroke(br, 3.0 * ak, a0=env * 0.35, soft=1.2))
    for i in range(n_along * 2):
        sg = -1 if i % 2 == 0 else 1
        yy = cy + sg * WH["off"]
        xa = rnd.uniform(WH["end"] + 6, W - WH["end"] - 60)
        L = rnd.uniform(34, 60)
        pts = bolt_pts(rnd, (xa, yy + rnd.uniform(-1, 1)), (xa + L, yy + rnd.uniform(-1, 1)), 0.09, 5, 0.62)
        pts = [(x, min(H - 7.0, max(7.0, y))) for x, y in pts]
        core = np.maximum(core, pad.stroke(pts, 1.3 * ak, 0.7, a0=env, a1=env * 0.7))
        glow = np.maximum(glow, pad.stroke(pts, 4.5 * ak, a0=env * 0.55, soft=1.5))
    for i in range(int(12 * env) + 2):
        sg = rnd.choice((-1, 1))
        x = rnd.uniform(WH["end"] + 4, W - WH["end"] - 4)
        y = cy + sg * (WH["off"] + rnd.uniform(-3, 9) * (1 if rnd.random() < 0.7 else -1))
        y = min(H - 7.0, max(7.0, y))
        a = rnd.uniform(0, TAU)
        L = rnd.uniform(2.5, 6.0)
        al = rnd.uniform(0.5, 1.0) * env
        core = np.maximum(core, streak(pad, (x - math.cos(a) * L, y - math.sin(a) * L), (x, y), 1.1, al))
    if f <= 1:
        # 갓 지나간 자리: 자국 위로 반짝임 몇
        for i in range(5):
            x = rnd.uniform(WH["end"] + 10, W - WH["end"] - 10)
            y = cy + rnd.choice((-1, 1)) * WH["off"]
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(4.0, 6.5), env, rot=0.0, thin=0.13, diag=0.0))
    glow, core = glow * ef, core * ef
    return finish(pad, glow, core, halo=((1.1, 0.5), (2.8, 0.38), (6.0, 0.2)), body_k=0.88, core_bloom=((1.1, 0.36), (2.8, 0.26)), win=3.0)


sprite("rfx_jwheel", "direction_z", 256, 64, 6, 4, job="paladin", zoom=0, alt=[THUNDER],
       desc="번개 수레 바퀴 자국 (땅에 눕는 띠 · 이어 붙임)")(wheel_frame)


# ------------------------------------------------------------ rfx_jhalo (빛 고리 — 땅에 눕는 고리, 반지름 = 칸 × 0.44 (장 0 — ring2 와 같음) → 장마다 조금씩 커지며 옅어짐)

HALO_ENV = [0.85, 1.0, 0.95, 0.84, 0.68, 0.5, 0.32, 0.15]
HALO_RAY = [0.8, 0.6, 0.47, 0.45, 0.45, 0.46, 0.49, 0.53]  # 안쪽 빛살이 닿는 곳 (반지름 몫) — 처음 두 장에 안으로 뻗음


def halo_frame(f, frames=8):
    S = 192
    pad = Pad(S, S)
    c = S / 2
    R = S * (0.44 + 0.0032 * f)
    env = HALO_ENV[f]
    rr, th = polar(pad, c, c)
    d = rr - R
    glow = pad.zeros()
    core = pad.zeros()
    # ---- 고리: 또렷한 밝은 줄 · 안쪽 가는 줄 둘 · 부드러운 빛 띠 (안쪽으로 더 넓게 — 바깥은 칸 끝이 가까움)
    wcore = 1.15 if f == 0 else 1.3 + 0.25 * min(1.0, f / 4)
    core = np.maximum(core, band_cov(d, wcore) * env)
    core = np.maximum(core, gauss(rr - R * 0.915, 0.5) * 0.5 * env)
    core = np.maximum(core, gauss(rr - R * 0.885, 0.4) * 0.3 * env)
    glow = np.maximum(glow, gauss(d, 4.2) * 0.9 * env)
    glow = np.maximum(glow, gauss(rr - (R - 7.0), 11.0) * 0.36 * env)
    glow = np.maximum(glow, 0.09 * smooth(R * 1.0, R * 0.25, rr) * env)
    # ---- 가는 빛살: 고리에서 안쪽으로 (길게 · 중간 · 짧게 번갈아) · 바깥으로 짧은 가시 (처음 몇 장 — 커지면 칸 끝에 닿아서)
    rot = 0.012 * f
    for i in range(72):
        a = rot + i * TAU / 72
        k = (1.0, 0.45, 0.72, 0.45)[i % 4]
        r_in = R * (1 - (1 - HALO_RAY[f]) * k)
        ux, uy = math.cos(a), math.sin(a)
        shim = 0.72 + 0.28 * math.cos(3 * a - 0.6 * f)
        p0_, p1_ = (c + ux * (R - 1.0), c + uy * (R - 1.0)), (c + ux * r_in, c + uy * r_in)
        core = np.maximum(core, pad.stroke([p0_, p1_], 0.9 * (0.7 + 0.3 * k), 0.15, a0=0.85 * env * shim, a1=0.0))
        if i % 2 == 0:
            glow = np.maximum(glow, pad.stroke([p0_, p1_], 3.0, 0.6, a0=0.4 * env * shim, a1=0.0, soft=1.0))
        if f <= 3 and i % 3 == 0:
            q0 = (c + ux * (R + 1.2), c + uy * (R + 1.2))
            q1 = (c + ux * (R + 4.5 + 2.0 * k), c + uy * (R + 4.5 + 2.0 * k))
            core = np.maximum(core, pad.stroke([q0, q1], 0.8, 0.2, a0=0.55 * env, a1=0.0))
    # ---- 반짝임: 고리 위를 천천히 돌며 깜빡임 · 안쪽으로 떠도는 빛 알갱이
    rnd = random.Random(1717)
    for i in range(11):
        a = rnd.uniform(0, TAU) + 0.03 * f
        rk = R + rnd.uniform(-3.0, 1.0)
        x, y = c + math.cos(a) * rk, c + math.sin(a) * rk
        tw = 0.5 + 0.5 * math.cos(f * 1.7 + i * 2.3)
        sz = (3.5 + 5.0 * tw) * (0.6 + 0.4 * env)
        core = np.maximum(core, star4(pad, x, y, sz, env * (0.5 + 0.5 * tw), rot=0.0, thin=0.13, diag=0.35))
        glow = np.maximum(glow, pad.disc_field(x, y, 3.5, soft=2.0) * env * 0.6 * tw)
    for i in range(16):
        a = rnd.uniform(0, TAU)
        r0 = rnd.uniform(0.35, 0.85) * R
        r_ = r0 + 1.6 * f * rnd.uniform(0.5, 1.0)
        x, y = c + math.cos(a) * r_, c + math.sin(a) * r_
        al = env * rnd.uniform(0.4, 0.9) * (0.4 if f == 0 else 1.0)
        core = np.maximum(core, pad.disc_field(x, y, rnd.uniform(0.6, 1.1)) * al)
        glow = np.maximum(glow, pad.disc_field(x, y, 2.4, soft=1.2) * al * 0.5)
    if f == 0:
        # 나타나는 순간: 고리 전체가 가늘고 하얗게 번쩍
        core = np.maximum(core, gauss(d, 0.6) * 1.0)
        glow = np.maximum(glow, gauss(d, 7.0) * 0.5)
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.38), (6.5, 0.2)), body_k=0.9, core_bloom=((1.2, 0.34), (3.0, 0.26)), win=3.0)


sprite("rfx_jhalo", "direction_z", 192, 192, 8, 4, job="priest", zoom=1, alt=[HOLY],
       desc="빛 고리 (땅에 눕는 고리 · 커지며 옅어짐)")(halo_frame)


# ------------------------------------------------------------ rfx_jbifrost (비프로스트 무지개 다리 줄기 — 오른쪽이 앞, 띠 두께 = 높이의 35%, 이어 돌림: 6 장에 빛 덩이 한 칸)
# 게임이 다섯 번 (빨강 · 주황 · 초록 · 파랑 · 보라) 조금씩 비켜 겹쳐 그림 — 띠가 가늘고 밝아야 함

BF = dict(W=512, H=64, hb=11.2, tail=74.0, front=34.0)


def bifrost_frame(f, frames=6):
    W, H, hb = BF["W"], BF["H"], BF["hb"]
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    ph = TAU * f / frames
    ends = smooth(2.0, BF["tail"], X) ** 1.2 * smooth(W - 2.0, W - BF["front"], X)
    # 띠가 리본처럼 살짝 물결침 (물결이 오른쪽으로 흐름 — 256 점에 한 물결)
    yc = cy + 1.3 * np.sin(X * TAU / 256.0 - ph)
    d = Y - yc
    ad = np.abs(d)
    # 반짝이는 결: 띠를 비스듬히 지나는 가는 빛 결 (주기 128 점 · 오른쪽으로 21.3 점/장 → 6 장에 한 주기)
    flow = 128.0 / frames * f
    n_f = fbm((X - flow) / 8.0, d / 3.0, seed=121, octaves=3, px=16)
    n_s = fbm((X - 2 * flow) / 16.0 + d * 0.05, d / 6.0 + 3.0, seed=122, octaves=2, px=8)
    shimmer = clamp01(0.5 + 0.9 * n_f)
    # 빛 줄기 (128 점마다 하나 — 오른쪽으로 흐름): 머리 앞은 부드럽게 · 뒤로 길게 끌리는 꼬리 · 머리 쪽이 굵은 혜성 꼴
    prof = np.zeros_like(X)
    prof_g = np.zeros_like(X)  # 빛 몸용 (머리 앞을 더 부드럽게)
    for k in range(-1, 6):
        xh = 20.0 + flow + 128.0 * k
        dist = X - xh
        tail = np.exp(np.maximum(dist, -400.0) / 40.0)
        prof = np.maximum(prof, np.where(dist > 0, np.exp(-(dist / 7.0) ** 2), tail))
        prof_g = np.maximum(prof_g, np.where(dist > 0, np.exp(-(dist / 16.0) ** 2), tail))
    # ---- 빛 몸: 가운데가 평평하고 가장자리가 부드러운 띠 · 결 · 줄기 (몸은 조금만 밝아짐)
    band = smooth(hb + 3.2, hb - 4.2, ad)
    glow = band * (0.7 + 0.18 * shimmer + 0.12 * clamp01(0.5 + n_s)) * ends
    glow = np.maximum(glow, band * clamp01(0.74 + 0.3 * prof_g) * ends)
    glow = np.maximum(glow, gauss(d, hb * 1.25) * 0.35 * ends)
    # ---- 심지: 가운데 밝은 속 (흐르는 결로 일렁임) · 가운데 가는 줄 · 가장자리 아주 가는 줄 둘 · 빛 줄기
    inner = smooth(hb * 0.7, hb * 0.12, ad)
    core = inner * (0.3 + 0.22 * shimmer) * ends
    core = np.maximum(core, gauss(d, 0.75) * (0.48 + 0.3 * shimmer) * ends)
    for e in (-1, 1):
        core = np.maximum(core, gauss(d - e * hb * 0.78, 0.45) * (0.26 + 0.34 * clamp01(0.5 + n_s)) * ends)
    hh = hb * (0.16 + 0.5 * prof)
    core = np.maximum(core, prof * smooth(hh + 1.6, hh - 1.6, ad) * 0.95 * ends)
    streaks = clamp01((n_f - 0.12) / 0.2) * inner * 0.6
    core = np.maximum(core, streaks * ends)
    # ---- 반짝임: 띠 가장자리 · 속을 따라 흐르며 피었다 짐 (q = 6 장에 한 바퀴)
    rnd = random.Random(1313)
    for i in range(18):
        b = rnd.random()
        q = (b + f / frames) % 1.0
        x0 = rnd.uniform(BF["tail"] * 0.6, W - BF["front"] - 40)
        x = x0 + 36 * q
        e = rnd.choice((-1, 1)) * rnd.uniform(0.2, 1.05)
        y = cy + 1.3 * math.sin(x * TAU / 256.0 - ph) + e * hb
        al = math.sin(math.pi * q) * rnd.uniform(0.6, 1.0) * float(np.interp(x, [0, BF["tail"], W - BF["front"], W], [0, 1, 1, 0]))
        r = rnd.uniform(3.0, 5.5) * (0.6 + 0.4 * math.sin(math.pi * q))
        core = np.maximum(core, star4(pad, x, y, r, al, rot=0.0, thin=0.14, diag=0.0))
        glow = np.maximum(glow, pad.disc_field(x, y, 2.6, soft=1.2) * al * 0.5)
    return finish(pad, glow, core, halo=((1.0, 0.45), (2.6, 0.34), (5.5, 0.18)), body_k=0.92, core_bloom=((1.0, 0.3), (2.6, 0.22)), win=3.0)


sprite("rfx_jbifrost", "lookat_direction", 512, 64, 6, 2, job="guardian", loop=(0, 5), zoom=0, alt=[(RAINBOW[3], (0.8, 0.9, 1.0))],
       desc="비프로스트 무지개 다리 줄기 (오른쪽이 앞 · 이어 돌림)")(bifrost_frame)


def bifrost_rainbow(glows, cores, folder, gaps=(0.34, 0.42)):
    """다섯 색으로 비켜 겹친 모습 (게임처럼 빛 → 심지 차례로 덮어 칠함) — 장 전부 (작게) · 장 0 (1배)"""
    ch, cw = glows[0].shape
    bgc = np.asarray(BG, np.float32)
    rows = []
    for gap in gaps:
        dy = int(round(ch * gap))
        Hc = ch + dy * 4
        for f in range(len(glows)):
            canvas = np.ones((Hc, cw, 3), np.float32) * bgc[None, None, :]
            for i, col in enumerate(RAINBOW):
                cb = tuple(0.5 * c + 0.5 for c in col)
                y0 = i * dy
                g = edge_fade(glows[f])[..., None]
                c = edge_fade(cores[f])[..., None]
                sub = canvas[y0:y0 + ch]
                sub = sub * (1 - g) + np.array(col, np.float32)[None, None, :] * g
                sub = sub * (1 - c) + np.array(cb, np.float32)[None, None, :] * c
                canvas[y0:y0 + ch] = sub
            rows.append((gap, f, Image.fromarray((clamp01(canvas) * 255 + 0.5).astype(np.uint8), "RGB")))
    gapx = 6
    small = [(g, f, im.resize((im.size[0] // 2, im.size[1] // 2), Image.LANCZOS)) for g, f, im in rows]
    sw, sh = small[0][2].size
    per = len(glows)
    big = [im for g, f, im in rows if f == 0]
    W = max(per * (sw + gapx) + gapx, max(im.size[0] for im in big) + 2 * gapx)
    H = 16 + len(gaps) * (sh + 18) + sum(im.size[1] + 18 for im in big)
    out = Image.new("RGB", (W, H), tuple(int(v * 255) for v in BG))
    label(out, "rfx_jbifrost x5 rainbow (offsets %s of height) — all frames x0.5, then frame 0 x1" % (", ".join(str(g) for g in gaps)), (gapx, 2))
    y = 16
    for gi, gap in enumerate(gaps):
        label(out, f"offset {gap}", (gapx, y))
        for g, f, im in small:
            if g == gap:
                out.paste(im, (gapx + f * (sw + gapx), y + 12))
        y += sh + 18
    for gi, im in enumerate(big):
        label(out, f"offset {gaps[gi]} frame 0", (gapx, y))
        out.paste(im, (gapx, y + 12))
        y += im.size[1] + 18
    path = os.path.join(folder, "rfx_jbifrost_rainbow.png")
    out.save(path)
    return path


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
        sys.exit(0 if check(names) else 1)
    for name in names:
        sp = SPECS[name]
        glows, cores = render(sp)
        write(sp, glows, cores)
        if folder:
            print("  preview:", preview(sp, glows, cores, folder))
            rep = loop_report(sp, glows, cores)
            if rep:
                print("  " + rep)
            if name == "rfx_jbifrost":
                print("  preview:", bifrost_rainbow(glows, cores, folder))


if __name__ == "__main__":
    main()
