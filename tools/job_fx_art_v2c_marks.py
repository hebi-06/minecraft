#!/usr/bin/env python3
"""v2c 직업 스킬 표식 그림 — v2 꼴 (tools/job_fx_art_v2.py) 의 새 그림 → tools/java_fx (rpg:rfx_j*).

  python3 tools/job_fx_art_v2c_marks.py                       # 그림 · 파티클 정의를 tools/java_fx 에 씀 (java_fx_art.save)
  python3 tools/job_fx_art_v2c_marks.py --only rfx_jglint     # 몇 개만 (쉼표로 여럿)
  python3 tools/job_fx_art_v2c_marks.py --preview <폴더>      # 확인용 그림도 씀 (그림마다: 장면 줄 · 작은 크기 줄 (모바일) · 이어 돌림 이음매 줄 · 한 장 확대 · 빛 / 심지 알파)

꼴은 v2 와 같음: 모두 흰색 (밝기 = 알파) 으로 4배 그림판에 그려 LANCZOS 로 줄임 · 칸 = 빛 (넓고 흐린 겉 · 몸 채움) 장면 N 개 다음에
심지 (가늘고 또렷한 속 — 날 · 빛 줄 · 보석 · 눈 · 반짝임) 장면 N 개 (빛 = 칸 f, 심지 = 칸 N + f), 두 겹은 같은 모양에서 그려 겹침.
게임은 빛 칸에 직업 빛 색, 심지 칸에 직업 심지 색을 입힘. 고정 시드 · 칸 가장자리 3 점 알파 0 · 다른 그림을 쓰지 않음 (글자는 지어낸 각진 글자 — glyph_mask).
보는 꼴: rotate_xyz = 카메라를 봄 (판 안에서 돌 수 있음) · lookat_direction = 나아가는 쪽으로 늘임 (그림 +X 오른쪽 = 나아가는 쪽) · direction_z = 눕는 판 (dir = 위 → 땅 표식).

그림 (이름 — 직업 — 보는 꼴 — 칸 — 장 수 — 열 수):
- rfx_jglint       검사   rotate_xyz       128×128 ×6  (6 열)  칼끝 반짝임: 네 뿔 별 · 긴 가로 렌즈 빛 · 옅은 작은 고리 — 0 ~ 1 번쩍 들어옴, 2 ~ 5 작아지며 사라짐 (별이 살짝 돎). 칸 가운데 = 반짝이는 점
- rfx_jbladering   검사   direction_z      256×256 ×8  (4 열)  이어 돌림: 가는 검 여섯이 둥글게 (자루 끝 0.35 · 코등이 0.47 · 칼끝 0.9 × 반 크기) · 코등이를 지나는 가는 빛 원 ·
                                                               장마다 7.5° 돎 (8 장 = 60° — 여섯 겹 대칭이라 이음매 없음) · 날을 따라 흐르는 빛 · 칼끝 꼬리
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402 — 같은 꼴의 파티클 정의 · tools/java_fx 에 씀
from job_fx_art_v2 import (BG, COLORS, SS, TAU, Pad, Stack, Xf, bevel_light, bez, bolt_pts, clamp01, ease_out, edge_fade, gblur,  # noqa: E402
                           glyph_lines, glyph_mask, inner_dist, label, obj_glow, rim_of, rot_pts, screen, smooth, sparkle, star_pts, taper_poly,
                           to_sheet)
from job_fx_art_v2b import (arc_pts, behind, chunk_pts, crescent_prof, cut_polyline, faceted, fbm, finish, gauss, lens, perlin, polar,  # noqa: E402
                            polyline_len, star4, streak, swin, tint_comp_scaled, win_border, wrap)


# ============================================================ 그림 목록 · 쓰기 · 확인용 그림

class Spec:
    def __init__(self, name, facing, cw, ch, frames, cols, job, fn, loop=False, zoom=0, desc=""):
        self.name, self.facing, self.cw, self.ch, self.frames, self.cols = name, facing, cw, ch, frames, cols
        self.job, self.fn, self.loop, self.zoom, self.desc = job, fn, loop, zoom, desc


SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, job, loop=False, zoom=0, desc=""):
    def deco(fn):
        SPECS[name] = Spec(name, facing, cw, ch, frames, cols, job, fn, loop, zoom, desc)
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


def _strip(gl, co, idx, ca, cb, scale, gap, title):
    cw, ch = gl[0].shape[1], gl[0].shape[0]
    w, h = max(1, int(round(cw * scale))), max(1, int(round(ch * scale)))
    out = Image.new("RGB", (len(idx) * (w + gap) + gap, h + gap + 14), tuple(int(v * 255) for v in BG))
    label(out, title, (gap, 1))
    for j, i in enumerate(idx):
        out.paste(tint_comp_scaled(gl[i], co[i], ca, cb, w, h), (gap + j * (w + gap), 13))
        if scale >= 0.3:
            label(out, str(i), (gap + j * (w + gap) + 2, 14))
    return out


def preview(sp, glows, cores, folder):
    """장면 줄 · 작은 크기 줄 (모바일 화면에서 보이는 크기쯤) · (이어 돌림) 이음매 줄 · 한 장 확대 · 빛 / 심지 알파"""
    ca, cb = COLORS[sp.job]
    cw, ch, n = sp.cw, sp.ch, sp.frames
    gap = 6
    bgc = tuple(int(v * 255) for v in BG)
    blocks = []
    scale = min(1.0, 1500.0 / (n * (cw + gap)))
    blocks.append(_strip(glows, cores, range(n), ca, cb, scale, gap, f"{sp.name}  {sp.facing} {cw}x{ch} x{n}  cols {sp.cols}  ({sp.job})"))
    small = 56.0 / max(cw, ch) * (2 if max(cw, ch) >= 384 else 1)
    blocks.append(_strip(glows, cores, range(n), ca, cb, small, gap, "small (mobile-ish)"))
    if sp.loop:
        seam = [n - 2, n - 1, 0, 1, 2]
        blocks.append(_strip(glows, cores, seam, ca, cb, scale, gap, f"loop seam: {seam}"))
    zf = sp.zoom
    zk = 2 if cw * 2 <= 1100 else 1
    zoom = tint_comp_scaled(glows[zf], cores[zf], ca, cb, cw * zk, ch * zk)
    zb = Image.new("RGB", (zoom.size[0] + 2 * gap, zoom.size[1] + 16), bgc)
    label(zb, f"frame {zf} x{zk}", (gap, 1))
    zb.paste(zoom, (gap, 14))
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


# ============================================================ 그림들

# ------------------------------------------------------------ rfx_jglint (칼끝 반짝임 — 칸 가운데 = 반짝이는 점)

GL_K = [0.9, 1.0, 0.82, 0.62, 0.42, 0.24]  # 밝기
GL_S = [0.62, 1.0, 0.84, 0.66, 0.48, 0.32]  # 별 크기
GL_F = [0.5, 1.0, 0.9, 0.74, 0.56, 0.38]  # 가로 렌즈 빛 길이
GL_ROT = [0.0, 0.0, 6.0, 11.0, 15.0, 18.0]  # 별 돎 (도)
GL_RING = [(9.0, 0.22), (14.0, 0.5), (17.5, 0.38), (20.0, 0.22), (22.0, 0.1), (23.5, 0.035)]  # 고리 (반지름 · 밝기)


def glint_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    X, Y = pad.grid()
    k, s, fl, rot = GL_K[f], GL_S[f], GL_F[f], math.radians(GL_ROT[f])
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 긴 가로 렌즈 빛 (가운데 굵고 밝음 → 양 끝 실처럼 가늘어짐 · 별과 달리 돌지 않음)
    Lf = 57.0 * fl
    flare_pts = [(c - Lf + 2 * Lf * i / 40, c) for i in range(41)]
    core = np.maximum(core, pad.stroke(flare_pts, 1.0, wfn=lambda u: 0.35 + 3.0 * s * (1 - abs(2 * u - 1)) ** 2.2,
                                       afn=lambda u: k * (0.25 + 0.75 * (1 - np.abs(2 * u - 1)) ** 1.4)))
    glow = np.maximum(glow, np.exp(-0.5 * (np.abs(Y - c) / (1.4 + 2.2 * s)) ** 2) * clamp01(1 - np.abs(X - c) / (Lf * 1.06)) ** 1.6 * k * 0.9)
    # ---- 네 뿔 별 (세로가 조금 김) + 짧은 대각 뿔 + 뜨거운 가운데
    R = 31.0 * s
    star = pad.poly(star_pts(c, c, R, 4, thin=0.085, rot=rot, r2=R * 0.8), k)
    core = np.maximum(core, star)
    core = np.maximum(core, pad.poly(star_pts(c, c, R * 0.4, 4, thin=0.17, rot=rot + math.pi / 4), k * 0.85))
    core = np.maximum(core, pad.disc_field(c, c, 2.4 + 2.2 * s, soft=1.0) * k)
    glow = np.maximum(glow, pad.poly(star_pts(c, c, R * 1.08, 4, thin=0.2, rot=rot, r2=R * 0.86), k * 0.75))
    glow = np.maximum(glow, pad.radial(c, c, 13 + 15 * s, 1.5) * k)
    # ---- 옅은 작은 고리 (번쩍과 함께 조금씩 퍼짐)
    r_ring, a_ring = GL_RING[f]
    core = np.maximum(core, gauss(rr - r_ring, 0.55) * a_ring * (0.8 + 0.2 * np.cos(th * 2)))
    glow = np.maximum(glow, gauss(rr - r_ring, 2.0) * a_ring * 0.55)
    # ---- 렌즈 잔상 점 (가로 줄 위 — 오른쪽에 둘 · 왼쪽에 하나, 번쩍 동안)
    if f <= 3:
        gk = [0.5, 1.0, 0.7, 0.35][f]
        for dx, r0, a0 in ((24.0, 2.0, 0.34), (38.0, 3.2, 0.2), (-31.0, 2.6, 0.24)):
            x = c + dx * (0.7 + 0.3 * fl)
            ring_g = gauss(np.sqrt((X - x) ** 2 + (Y - c) ** 2) - r0, 0.45) * a0 * gk
            core = np.maximum(core, ring_g + pad.disc_field(x, c, r0 * 0.6, soft=0.8) * a0 * 0.5 * gk)
            glow = np.maximum(glow, pad.disc_field(x, c, r0 + 2, soft=2.0) * a0 * 0.6 * gk)
    # ---- 둘레에 반짝 튀는 작은 별 둘 (2 ~ 4)
    for (dx, dy, f0, r0) in ((15, -12, 2, 5.5), (-12, 15, 3, 4.5)):
        age = f - f0
        if 0 <= age <= 1:
            tw = (1.0, 0.45)[age]
            core = np.maximum(core, star4(pad, c + dx * (1 + 0.2 * age), c + dy * (1 + 0.2 * age), r0 * tw, 0.85 * tw, rot=0.4, thin=0.14, diag=0.0))
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.38), (7.0, 0.22)), body_k=0.85, core_bloom=((1.2, 0.4), (3.0, 0.3)), win=4.0)


sprite("rfx_jglint", "rotate_xyz", 128, 128, 6, 6, "swordsman", zoom=1,
       desc="칼끝 반짝임: 네 뿔 별 · 긴 가로 렌즈 빛 · 옅은 고리 · 렌즈 잔상 점 — 0 ~ 1 번쩍, 2 ~ 5 작아지며 사라짐 (별이 살짝 돎)")(glint_frame)


# ------------------------------------------------------------ rfx_jbladering (가는 검 여섯이 둥글게 — 이어 돌림, 8 장 = 60°)
# 로컬 검 (자루 끝 x = 0 → 칼끝 x = L, 폭은 y): 반지름 BR_POM 에서 바깥으로 놓음 → 코등이 반지름 = BR_POM + BR_GX

BR_HALF = 128.0
BR_POM = 0.35 * BR_HALF  # 자루 끝
BR_TIP = 0.9 * BR_HALF  # 칼끝
BR_L = BR_TIP - BR_POM
BR_GX = 15.2  # 코등이 (로컬 x)
BR_GUARD_R = BR_POM + BR_GX  # 빛 원 반지름 (코등이를 지남)


def ring_sword_geom():
    L, gx = BR_L, BR_GX
    g = {}
    b0 = gx + 1.0
    top, bot = [], []
    n = 40
    for i in range(n + 1):
        t = i / n
        x = b0 + (L - b0) * t
        if t > 0.78:
            u = (t - 0.78) / 0.22
            w = 2.45 * (1 - u ** 1.4) ** 0.8
        else:
            w = 3.1 - 0.65 * t / 0.78
        top.append((x, -w))
        bot.append((x, w))
    g["blade"] = top + bot[::-1]
    g["top"], g["bot"], g["b0"] = top, bot, b0
    # 코등이: 가운데 덩이 + 칼끝 쪽으로 살짝 휜 날개 둘 + 끝 구슬
    g["block"] = [(gx - 1.7, -3.8), (gx + 1.9, -3.8), (gx + 3.1, 0), (gx + 1.9, 3.8), (gx - 1.7, 3.8)]
    wings = []
    ends = []
    for sd in (-1, 1):
        sp = [(gx + 0.2 + 5.2 * (i / 10) ** 2.0, sd * (3.2 + 7.6 * i / 10)) for i in range(11)]
        wings.append(taper_poly(sp, lambda t: 1.45 * (1 - 0.5 * t)))
        ends.append((sp[-1][0] + 0.3, sp[-1][1] + sd * 0.6))
    g["wings"], g["ends"] = wings, ends
    g["grip"] = [(4.6, -1.35), (gx - 1.6, -1.55), (gx - 1.6, 1.55), (4.6, 1.35)]
    g["grip_x"] = (4.6, gx - 1.6)
    g["pommel"] = [(0.0, 0.0), (2.4, -2.5), (5.0, 0.0), (2.4, 2.5)]
    return g


def ring_sword(pad, xf, flow=None):
    """검 하나 (xf: 로컬 → 칸). (빛 몸 4배, 심지 4배, 모양 4배) — flow = 날을 따라 흐르는 빛 자리 (로컬 x)"""
    g = ring_sword_geom()
    X, Y = pad.grid()
    LX, LY = xf.inv(X, Y)
    blade = pad.poly(xf(g["blade"]))
    block = pad.poly(xf(g["block"]))
    wings = pad.polys([xf(w) for w in g["wings"]])
    ends = np.maximum.reduce([pad.disc_field(*xf.p(*e), 1.25) for e in g["ends"]])
    guard = np.maximum.reduce([block, wings, ends])
    grip = pad.poly(xf(g["grip"]))
    pommel = pad.poly(xf(g["pommel"]))
    sil = np.maximum.reduce([blade, guard, grip, pommel])
    # ---- 빛 몸: 앞 날 (돎 쪽 = 로컬 +y) 이 밝고 뒤 날은 조금 어둡게
    half = np.maximum(0.6, np.interp(LX, [g["b0"], BR_L * 0.83, BR_L], [3.1, 2.45, 0.2]))
    v = clamp01(LY / half)
    body = blade * (0.7 + 0.24 * v)
    body = np.maximum.reduce([body, guard * 0.97, grip * 0.66, pommel * 0.88])
    # ---- 심지: 날 줄 둘 (앞 날이 더 밝음) · 가운데 홈 줄 · 코등이 테 · 보석 · 손잡이 끈 · 자루 끝
    core = pad.stroke(xf(g["bot"]), 0.75, 0.45, a0=1.0, a1=0.9) * blade
    core = np.maximum(core, pad.stroke(xf(g["top"]), 0.6, 0.35, a0=0.62, a1=0.55) * blade)
    core = np.maximum(core, blade * clamp01(LY / half) ** 1.6 * 0.6)
    core = np.maximum(core, pad.stroke(xf([(g["b0"] + 3, 0.0), (BR_L * 0.7, 0.0)]), 0.6, 0.25, a0=0.75, a1=0.35))
    core = np.maximum(core, pad.stroke(xf([(BR_L * 0.72, 0.0), (BR_L - 1.2, 0.0)]), 0.35, 0.2, a0=0.3, a1=0.7) * blade)
    core = np.maximum(core, rim_of(guard, 0.6) * 0.95)
    core = np.maximum(core, guard * 0.4)
    gem = pad.poly(xf([(BR_GX - 1.0, 0.0), (BR_GX + 0.7, -1.8), (BR_GX + 2.2, 0.0), (BR_GX + 0.7, 1.8)]))
    core = np.maximum(core, gem)
    for e in g["ends"]:
        core = np.maximum(core, pad.disc_field(*xf.p(*e), 0.7))
    g0, g1 = g["grip_x"]
    x = g0 + 0.5
    while x < g1 - 0.4:
        core = np.maximum(core, pad.stroke(xf([(x, -1.4), (x + 1.3, 1.4)]), 0.45, a0=0.75) * grip)
        x += 1.75
    core = np.maximum(core, rim_of(grip, 0.4) * 0.45)
    core = np.maximum(core, rim_of(pommel, 0.55) * 0.9)
    core = np.maximum(core, pad.disc_field(*xf.p(2.4, 0.0), 0.8) * 0.9)
    if flow is not None:
        # 날을 따라 흐르는 빛 (비스듬한 띠)
        u = (LX - flow) + LY * 0.6
        fl_ = np.exp(-0.5 * (u / 3.2) ** 2) * blade
        core = np.maximum(core, fl_ * 0.95)
        body = np.maximum(body, fl_ * 1.0)
        # 흐르는 빛이 칼끝에 닿으면 반짝
        tw = math.exp(-0.5 * ((flow - (BR_L - 3.0)) / 5.0) ** 2)
        if tw > 0.05:
            tx, ty = xf.p(BR_L - 1.0, 0.0)
            core = np.maximum(core, star4(pad, tx, ty, 7.5 * tw + 1.0, 0.95 * tw, rot=xf.ang + math.pi / 4, thin=0.14, diag=0.0))
    return body, core, sil


def bladering_frame(f, S=256, frames=8):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    base = math.radians(-90.0 + 60.0 * f / frames)  # 화면 시계 방향으로 돎 (8 장 = 60° → 여섯 겹 대칭이라 장면 8 = 장면 0)
    body = pad.zeros()
    core = pad.zeros()
    sil_all = pad.zeros()
    # 날을 따라 흐르는 빛: 코등이 → 칼끝, 8 장에 한 번 (여섯 자루 모두 같은 자리 — 이어 돌림)
    q = (f / frames) % 1.0
    flow = BR_GX + 2 - 6 + (BR_L - BR_GX + 10) * q
    for k in range(6):
        a = base + k * TAU / 6
        xf = Xf(c + math.cos(a) * BR_POM, c + math.sin(a) * BR_POM, a)
        b, cc, sil = ring_sword(pad, xf, flow=flow)
        body = np.maximum(body, b)
        core = np.maximum(core, cc)
        sil_all = np.maximum(sil_all, sil)
    # ---- 칼끝 꼬리 (돎 뒤쪽으로 흐려지는 가는 호) · 날이 쓸고 간 옅은 부채
    trail_c = pad.zeros()
    trail_g = pad.zeros()
    span = math.radians(13)
    for k in range(6):
        head = base + k * TAU / 6
        b = behind(th, head - math.radians(1.2))
        kk = clamp01(1 - b / span) * (b < span)
        trail_c = np.maximum(trail_c, gauss(rr - (BR_TIP - 1.5), 0.45 + 0.5 * kk) * kk ** 1.8 * 0.5)
        trail_g = np.maximum(trail_g, gauss(rr - (BR_TIP - 1.5), 1.2 + 1.2 * kk) * kk ** 1.4 * 0.32)
        fan = smooth(BR_GUARD_R + 4, BR_GUARD_R + 14, rr) * smooth(BR_TIP + 0.5, BR_TIP - 4, rr)
        trail_g = np.maximum(trail_g, fan * clamp01(1 - b / math.radians(11)) ** 2.2 * (b < math.radians(11)) * 0.1)
        trail_c = np.maximum(trail_c, gauss(rr - BR_TIP * 0.8, 0.4) * clamp01(1 - b / math.radians(12)) ** 2 * (b < math.radians(12)) * 0.25)
    core = np.maximum(core, trail_c * (1 - sil_all))
    body = np.maximum(body, trail_g * (1 - sil_all))
    # ---- 코등이를 지나는 가는 빛 원 · 검 사이 작은 마름모 (원 위 — 함께 돎) · 안쪽 옅은 원 (자루 끝)
    ring_c = gauss(rr - BR_GUARD_R, 0.5) * 0.92
    ring_g = gauss(rr - BR_GUARD_R, 2.0) * 0.42
    core = np.maximum(core, ring_c)
    body = np.maximum(body, ring_g)
    for k in range(6):
        a = base + (k + 0.5) * TAU / 6
        x, y = c + math.cos(a) * BR_GUARD_R, c + math.sin(a) * BR_GUARD_R
        ux, uy = math.cos(a), math.sin(a)
        tx, ty = -uy, ux
        dm = [(x + tx * 4.2, y + ty * 4.2), (x + ux * 2.2, y + uy * 2.2), (x - tx * 4.2, y - ty * 4.2), (x - ux * 2.2, y - uy * 2.2)]
        m = pad.poly(dm)
        core = np.maximum(core, rim_of(m, 0.5) * 0.9 + m * 0.35)
        body = np.maximum(body, m * 0.8)
    core = np.maximum(core, gauss(rr - (BR_POM - 3.0), 0.4) * 0.16)
    body = np.maximum(body, gauss(rr - (BR_POM - 3.0), 1.6) * 0.08)
    core1 = pad.down(core)
    glow1 = obj_glow(pad, body, core1, halo=((1.3, 0.5), (3.2, 0.36), (7.5, 0.2)), body_k=1.0, core_bloom=((1.3, 0.32), (3.5, 0.24)))
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jbladering", "direction_z", 256, 256, 8, 4, "swordsman", loop=True, zoom=0,
       desc="가는 검 여섯 고리 (이어 돌림 — 장마다 7.5°): 자루 끝 0.35 · 칼끝 0.9 × 반 크기, 코등이를 지나는 빛 원 · 날을 따라 흐르는 빛 · 칼끝 꼬리")(bladering_frame)


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
    for name in names:
        sp = SPECS[name]
        glows, cores = render(sp)
        write(sp, glows, cores)
        if folder:
            print("  preview:", preview(sp, glows, cores, folder))


if __name__ == "__main__":
    main()
