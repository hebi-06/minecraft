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
    base = math.radians(-90.0 + 0.37 + 60.0 * f / frames)  # 화면 시계 방향으로 돎 (8 장 = 60° → 여섯 겹 대칭이라 장면 8 = 장면 0 · 0.37° = 꼭짓점이 점 경계에 딱 걸리지 않게)
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


# ------------------------------------------------------------ rfx_jquake (광전사 땅 내려찍기 — 땅에 눕는 판, 먼지 고리 바깥 끝 0.46 S = rfx_jsmash2 고리 끝)

QK_RING = [0.1 + 0.36 * ease_out(min(1.0, (f + 0.7) / 8.7), 2.2) for f in range(12)]  # 먼지 고리 바깥 끝 (× S)
QK_FLASH = [1.0, 0.62, 0.26, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
QK_CRACK_A = [0.9, 1.0, 1.0, 1.0, 0.95, 0.92, 0.9, 1.18, 1.0, 0.7, 0.44, 0.22]  # 금 밝기 (7 ~ 8 달아오름 → 식음)
QK_CRACK_W = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.45, 1.25, 1.0, 0.88, 0.76]  # 금 빛 무리 폭
QK_CRACK_CW = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.05, 1.4, 1.22, 0.95, 0.82, 0.7]  # 금 심지 폭 (달아오르면 굵어지고 식으며 가늘어짐)
QK_DUST = [0.0, 0.55, 0.85, 1.0, 1.0, 0.95, 0.85, 0.72, 0.58, 0.42, 0.27, 0.13]
_QK = {}


def jag_pts(rnd, p0, ang, L, nseg, dev, rough=0.05):
    """각진 금 한 줄: p0 에서 ang 쪽으로 길이 L — 마디 nseg 개 (마디마다 방향이 ±dev 안에서 꺾임) + 마디 안의 작은 거칠기"""
    pts = [p0]
    x, y = p0
    for i in range(nseg):
        a = ang + rnd.uniform(-dev, dev)
        sl = L / nseg * rnd.uniform(0.75, 1.25)
        nx_, ny_ = x + math.cos(a) * sl, y + math.sin(a) * sl
        mx, my = (x + nx_) / 2, (y + ny_) / 2
        j = rnd.uniform(-1, 1) * rough * sl
        pts.append((mx - math.sin(a) * j, my + math.cos(a) * j))
        pts.append((nx_, ny_))
        x, y = nx_, ny_
    return pts


def quake_cracks(S=192):
    """갈라지는 금 (곧은 마디가 날카롭게 꺾임): [(점들, 굵기, 자람 시작 몫 (어미 줄 기준), 어미 번호)] — 고정 시드"""
    if S in _QK:
        return _QK[S]
    rnd = random.Random(4242)
    c = S / 2
    out = []
    n = 8
    for i in range(n):
        ang = -math.pi / 2 + i * TAU / n + rnd.uniform(-0.2, 0.2)
        L = S * rnd.uniform(0.31, 0.39)
        p0 = (c + math.cos(ang) * 9, c + math.sin(ang) * 9)
        main = jag_pts(rnd, p0, ang, L - 9, rnd.choice((5, 6, 6, 7)), 0.42)
        mi = len(out)
        out.append((main, 4.2, 0.0, -1))
        kinks = list(range(2, len(main) - 3, 2))
        rnd.shuffle(kinks)
        for b, j in enumerate(sorted(kinks[:rnd.choice((2, 2, 3))])):
            ba = ang + rnd.choice((-1, 1)) * rnd.uniform(0.55, 1.0)
            bl = L * rnd.uniform(0.2, 0.38)
            br = jag_pts(rnd, main[j], ba, bl, rnd.choice((2, 3, 3)), 0.5)
            bi = len(out)
            out.append((br, 2.4 * (1 - j / len(main) * 0.5), j / (len(main) - 1), mi))
            if rnd.random() < 0.6:
                k = 2 if len(br) > 4 else len(br) - 1
                sa = ba + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.1)
                sb = jag_pts(rnd, br[k], sa, bl * rnd.uniform(0.35, 0.6), 2, 0.5)
                out.append((sb, 1.3, k / (len(br) - 1), bi))
    _QK[S] = out
    return out


def quake_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    X, Y = pad.grid()
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 먼지 고리 (뭉게뭉게 — 노이즈 결 · 앞 테가 밝음, 바깥 끝 = QK_RING × S)
    r_edge = S * QK_RING[f]
    dk = QK_DUST[f]
    if dk > 0:
        p = f / 11
        w = 20.0 * (1 - 0.5 * p) + 5
        d = r_edge - rr
        n1 = fbm(X / 13.0 + 3.7, Y / 13.0 - 1.3, seed=31, octaves=3)
        n2 = fbm(X / 5.5 - 2.1 + f * 0.13, Y / 5.5 + 4.4, seed=37, octaves=2)
        puff = clamp01(0.62 + 0.75 * n1 + 0.3 * n2)
        edge_in = 3.0 * (0.5 + 0.5 * n1)  # 바깥 끝은 안쪽으로만 울퉁불퉁 (0.46 S 를 넘지 않음)
        band = smooth(-1.5, 2.5, d - edge_in) * smooth(w + 8 + 6 * n2, w * 0.2, d)
        dust = band * puff * (rr < S * 0.465 + 2)
        glow = np.maximum(glow, dust * 0.46 * dk)
        core = np.maximum(core, dust ** 1.6 * 0.18 * dk)
        # 앞 테 (충격파 — 처음에 밝고 가늘어지며 사라짐)
        fr = [0.0, 1.0, 0.95, 0.8, 0.62, 0.45, 0.3, 0.16, 0.08, 0.0, 0.0, 0.0][f]
        if fr > 0:
            ripple = 0.8 + 0.2 * np.cos(th * 13 + 2.5 * np.sin(th * 4))
            core = np.maximum(core, gauss(d - 1.6, 0.7 + 0.6 * (1 - p)) * fr * ripple)
            glow = np.maximum(glow, gauss(d - 2.0, 3.2) * fr * 0.7)
        # 큰 먼지 뭉치 (앞 테 안쪽)
        rnd = random.Random(88)
        for i in range(18):
            a = rnd.uniform(0, TAU)
            dist = min(r_edge - rnd.uniform(5, 15), S * 0.43)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            rad = rnd.uniform(5, 9) * (0.75 + 0.5 * p)
            glow = np.maximum(glow, pad.disc_field(x, y, rad, soft=rad * 0.85) * dk * 0.42)
            core = np.maximum(core, pad.disc_field(x, y, rad * 0.55, soft=rad * 0.6) * dk * 0.1)
    # ---- 가운데: 맞은 자리 번쩍 (0 ~ 1) · 팬 자리 (각진 테)
    fk = QK_FLASH[f]
    if fk > 0:
        core = np.maximum(core, pad.disc_field(c, c, 22 * fk + 3, soft=3 + 5 * (1 - fk)) * min(1.0, fk * 1.3))
        core = np.maximum(core, star4(pad, c, c, 72 * fk + 8, min(1.0, fk * 1.2), rot=0.0, thin=0.065, diag=0.55))
        core = np.maximum(core, star4(pad, c, c, 40 * fk + 6, min(1.0, fk * 1.1), rot=math.pi / 8, thin=0.08, diag=0.0))
        glow = np.maximum(glow, pad.radial(c, c, 64 * fk + 12, 1.1) * fk)
    crk = [0.7, 1.0, 1.0, 0.95, 0.9, 0.85, 0.8, 0.85, 0.75, 0.55, 0.38, 0.22][f]
    rnd = random.Random(515)
    rim = []
    for i in range(11):
        a = i * TAU / 11 + rnd.uniform(-0.12, 0.12)
        r0 = rnd.uniform(13.0, 18.5)
        rim.append((c + math.cos(a) * r0, c + math.sin(a) * r0))
    crater = pad.poly(rim)
    core = np.maximum(core, pad.stroke(rim + [rim[0]], 1.5, a0=0.85 * crk))
    core = np.maximum(core, crater * 0.18 * crk)
    glow = np.maximum(glow, pad.disc_field(c, c, 20, soft=6) * 0.8 * crk)
    spokes = (0.5 + 0.5 * np.cos(th * 19 + 1.7 * np.sin(th * 6))) ** 4
    core = np.maximum(core, crater * spokes * smooth(3, 9, rr) * 0.5 * crk)
    # ---- 갈라지는 금 (1 ~ 6 뻗음 · 7 ~ 8 달아오름 · 9 ~ 11 식음): 가운데 굵고 끝으로 가늘어짐, 가지 · 잔가지
    grow = ease_out(clamp01((f - 0.6) / 5.4), 1.6)
    ca, cw, ccw = QK_CRACK_A[f], QK_CRACK_W[f], QK_CRACK_CW[f]
    cracks = quake_cracks(S)
    vis_frac = {}
    cr_c = pad.zeros()
    cr_g = pad.zeros()
    hot = pad.zeros()
    for i, (pts, wd, t0, parent) in enumerate(cracks):
        if parent < 0:
            g = grow
        else:
            pg = vis_frac.get(parent, 0.0)
            g = clamp01((pg - t0) / max(0.05, 1 - t0)) ** 0.9
        vis_frac[i] = g
        if g < 0.02:
            continue
        vis = cut_polyline(pts, g)
        if len(vis) < 2:
            continue
        Lfull = polyline_len(pts)
        k = polyline_len(vis) / max(1e-6, Lfull)
        wf = lambda u, wd=wd, k=k: wd * (1 - 0.82 * u * k) + 0.3
        cr_c = np.maximum(cr_c, pad.stroke(vis, 1.0, wfn=lambda u, wf=wf: wf(u) * 0.6 * ccw, afn=lambda u, k=k: 1.0 - 0.25 * u * k))
        cr_g = np.maximum(cr_g, pad.stroke(vis, 1.0, wfn=lambda u, wf=wf: wf(u) * 1.7 * cw + 1.6, afn=lambda u, k=k: 0.95 - 0.35 * u * k, soft=1.4))
        if parent < 0 and f >= 7:
            hot = np.maximum(hot, pad.stroke(vis, 1.0, wfn=lambda u, wf=wf: wf(u) * 1.15, afn=lambda u: 1 - 0.6 * u))
    core = np.maximum(core, cr_c * min(1.0, ca))
    glow = np.maximum(glow, cr_g * min(1.0, ca))
    if f in (7, 8):
        # 달아오름: 금이 넓게 끓어오름 (빛 무리) · 금 줄을 따라 튀는 불씨
        surge = 1.0 if f == 7 else 0.6
        glow = np.maximum(glow, gblur(hot, 2.5 * SS) * 1.4 * surge)
        core = np.maximum(core, hot * 0.9 * surge)
    if 6 <= f <= 10:
        rnd = random.Random(616)
        ek = [0.5, 1.0, 0.9, 0.6, 0.3][f - 6]
        mains = [pts for pts, wd, t0, parent in cracks if parent < 0]
        for i in range(26):
            pts = mains[i % len(mains)]
            j = rnd.randint(2, len(pts) - 2)
            x, y = pts[j]
            tw = 0.5 + 0.5 * math.sin(f * 2.1 + i * 1.7)
            jx, jy = rnd.uniform(-3, 3), rnd.uniform(-3, 3)
            a = ek * tw * rnd.uniform(0.6, 1.0)
            if a < 0.08:
                continue
            core = np.maximum(core, star4(pad, x + jx, y + jy, rnd.uniform(2.5, 4.5) * (0.6 + 0.4 * tw), a, rot=rnd.uniform(0, 1), thin=0.18, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(x + jx, y + jy, 3.0, soft=2.0) * a * 0.6)
    # ---- 튀는 각진 돌덩이 (가운데에서 바깥으로 — 솟았다 떨어지며 작아지고 흐려짐 · 떨어진 자리에 작은 먼지)
    rnd = random.Random(707)
    for i in range(17):
        a = rnd.uniform(0, TAU)
        d0 = rnd.uniform(6, 16)
        d1 = S * rnd.uniform(0.25, 0.41)
        f_l = rnd.uniform(0.0, 1.3)
        life = rnd.uniform(4.2, 6.8)
        sz = rnd.uniform(4.8, 9.0)
        spin = rnd.uniform(-1, 1) * rnd.uniform(1.5, 3.5)
        seed = rnd.randint(0, 10 ** 6)
        nv = rnd.choice((4, 5, 5, 6))
        t = (f - f_l) / life
        if t < 0:
            continue
        if t <= 1.0:
            dist = d0 + (d1 - d0) * ease_out(t, 1.5)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            hgt = math.sin(math.pi * t)
            size = sz * (0.8 + 0.75 * hgt) * (1 - 0.45 * t)
            al = 1.0 if t < 0.55 else (1 - (t - 0.55) / 0.45) ** 1.2 * 0.85 + 0.15
            pts = chunk_pts(random.Random(seed), x, y, size, n=nv, rot=spin * t)
            m, body, cc = faceted(pad, pts, a=al)
            core = np.maximum(core, np.maximum(cc, rim_of(m, 0.7) * al))
            glow = np.maximum(glow, body)
            # 지나온 자리의 옅은 줄 (솟는 동안)
            tl = min(dist - d0, 16) * (1 - t)
            if tl > 1.5:
                glow = np.maximum(glow, pad.stroke([(x - math.cos(a) * tl, y - math.sin(a) * tl), (x, y)], 0.5, size * 1.3, a0=0.0, a1=al * 0.45, soft=1.5))
        elif t <= 1.6:
            # 떨어진 자리: 작은 먼지 고리
            q = (t - 1.0) / 0.6
            x, y = c + math.cos(a) * d1, c + math.sin(a) * d1
            rad = sz * (0.8 + 1.2 * q)
            glow = np.maximum(glow, pad.disc_field(x, y, rad, soft=rad * 0.9) * (1 - q) * 0.4)
            rs2 = random.Random(seed + 1)
            for j in range(4):
                ja = rs2.uniform(0, TAU)
                jd = rad * rs2.uniform(0.3, 0.9)
                core = np.maximum(core, pad.disc_field(x + math.cos(ja) * jd, y + math.sin(ja) * jd, rs2.uniform(0.6, 1.1)) * (1 - q) ** 1.3 * 0.7)
    # ---- 자갈 가루 (먼지 고리 안 — 작은 점)
    if 2 <= f <= 10:
        rnd = random.Random(818)
        gk = [0, 0, 0.6, 0.9, 1.0, 0.95, 0.85, 0.7, 0.5, 0.32, 0.16][f]
        for i in range(30):
            a = rnd.uniform(0, TAU)
            dist = min(r_edge - rnd.uniform(2, 24), S * 0.44)
            if dist < 22:
                continue
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            core = np.maximum(core, pad.disc_field(x, y, rnd.uniform(0.6, 1.3)) * gk * rnd.uniform(0.5, 1.0))
    return finish(pad, glow, core, halo=((1.4, 0.48), (3.5, 0.38), (8.0, 0.22)), body_k=0.85, core_bloom=((1.3, 0.35), (3.5, 0.26)), win=4.0)


sprite("rfx_jquake", "direction_z", 192, 192, 12, 6, "warrior", zoom=4,
       desc="광전사 땅 내려찍기: 0 ~ 1 번쩍 · 1 ~ 6 각진 금이 가지를 치며 뻗음 · 각진 돌덩이가 튀어 솟았다 떨어지며 작아짐 · 먼지 고리 (바깥 끝 0.46 S) · 7 ~ 8 금이 달아오름 → 9 ~ 11 식음")(quake_frame)


# ------------------------------------------------------------ rfx_jwolf (펜리르 — 혼령 늑대 머리 옆모습, 오른쪽 = 나아가는 쪽 · 갈기가 왼쪽으로 흩날림, 이어 돌림 6 장)
# 머리는 x 0.63 ~ 0.95 W (코끝 0.95 W) · 귀 끝 y ≈ 7 · 턱 끝 y ≈ 88. 아래턱이 조금씩 여닫힘 (6 장 한 바퀴) · 갈기 물결이 왼쪽으로 흘러감.

WF_DY = 3.0  # 머리 전체를 조금 내림 (귀 끝이 칸 위 가장자리에서 떨어지게)
WF_HINGE = (195.0, 61.0 + WF_DY)  # 아래턱 돌쩌귀


def sh(pts, dy=WF_DY):
    return [(x, y + dy) for x, y in pts]


def wolf_upper():
    """위 머리 (귀 · 이마 · 주둥이 · 윗입술 줄) 외곽 — 시계 방향 (코끝부터)"""
    pts = []
    pts += bez((238.5, 41.5), (244.5, 42.5), (244.0, 49.0), 6)  # 코 앞 (둥긂)
    pts += bez((244.0, 49.0), (243.5, 53.5), (237.5, 54.5), 5)[1:]  # 코 아래
    pts += bez((237.5, 54.5), (220.0, 55.5), (200.0, 59.5), 10)[1:]  # 윗입술 줄 → 입꼬리
    pts += bez((200.0, 59.5), (196.0, 63.0), (190.0, 67.0), 4)[1:]  # 입꼬리 → 턱 뒤
    pts += bez((190.0, 67.0), (176.0, 72.0), (168.0, 62.0), 8)[1:]  # 볼 아래 → 뒤
    pts += bez((168.0, 62.0), (161.0, 46.0), (166.5, 28.0), 8)[1:]  # 뒤통수
    pts += bez((166.5, 28.0), (168.5, 14.0), (175.5, 4.0), 7)[1:]  # 귀 뒤 날
    pts += bez((175.5, 4.0), (183.0, 12.0), (189.5, 24.5), 7)[1:]  # 귀 앞 날
    pts += bez((189.5, 24.5), (197.0, 27.0), (205.0, 33.0), 6)[1:]  # 이마 → 콧등 꺾임
    pts += bez((205.0, 33.0), (224.0, 36.0), (238.5, 41.5), 10)[1:-1]  # 콧등
    return sh(pts)


def wolf_lower_closed():
    """아래턱 (닫힌 자리 — 돌쩌귀 둘레로 돌려 엶) 외곽"""
    pts = [(193.0, 59.5)]
    pts += bez((197.0, 61.0), (216.0, 61.8), (233.5, 61.5), 8)  # 이 줄
    pts += bez((233.5, 61.5), (237.5, 62.3), (235.5, 65.8), 4)[1:]  # 턱 끝
    pts += bez((235.5, 65.8), (222.0, 69.8), (201.0, 72.5), 8)[1:]  # 턱 밑
    pts += bez((201.0, 72.5), (191.0, 73.0), (186.0, 68.0), 5)[1:]
    return sh(pts)


def wolf_tufts(ph):
    """목 둘레 털 덩이: 머리 뒤 줄에서 뒤로 뻗는 짧은 뾰족 털들 [(줄 점들, 뿌리 반폭)] — 끝이 살짝 흔들림"""
    rnd = random.Random(2323)
    roots = sh(bez((178.0, 13.0), (152.0, 48.0), (188.0, 82.0), 40))
    out = []
    n = 14
    for i in range(n):
        u = i / (n - 1)
        x, y = roots[int(u * 40)]
        x += rnd.uniform(2, 7)
        ang = math.radians(201 - 32 * u + rnd.uniform(-6, 6))  # 위 털은 왼쪽 위 · 목 털은 왼쪽 (조금 아래)
        L = rnd.uniform(20, 32) * (0.85 + 0.35 * math.sin(math.pi * u)) * (1 - 0.35 * u ** 3)
        wd = rnd.uniform(3.2, 4.6)
        sway = 1.6 * math.sin(ph + u * 4.0 + rnd.uniform(0, 1))
        ux, uy = math.cos(ang), math.sin(ang)
        tip = (x + ux * L, y + uy * L + sway)
        mid = (x + ux * L * 0.5 - uy * 2.5, y + uy * L * 0.5 + ux * 2.5)
        out.append((bez((x, y), mid, tip, 10), wd))
    return out


def wolf_mane(f, frames=6):
    """긴 갈기 가닥들: [(가운데 줄 점들, 뿌리 반폭, 밝기)] — 물결이 뿌리에서 꼬리 쪽 (왼쪽) 으로 흐름 (6 장 한 바퀴)"""
    rnd = random.Random(1717)
    roots = sh(bez((166.0, 18.0), (138.0, 52.0), (182.0, 92.0), 40))
    out = []
    n = 13
    for i in range(n):
        u = (i + 0.5) / n + rnd.uniform(-0.025, 0.025)
        xr, yr = roots[int(u * 40)]
        xr += rnd.uniform(-6, 6)
        L = rnd.uniform(96, 156) * (0.8 + 0.3 * math.sin(math.pi * u))
        y_end = 66 + (u - 0.5) * rnd.uniform(46, 70) + rnd.uniform(-7, 7)
        amp = rnd.uniform(6.5, 11.5)
        lam = rnd.uniform(46, 74)
        curl = rnd.uniform(-10, 6)
        ph0 = rnd.uniform(0, TAU)
        w0 = rnd.uniform(1.8, 4.2) * (0.75 + 0.4 * math.sin(math.pi * u))
        al = rnd.uniform(0.55, 1.0)
        pts = []
        m = 30
        for j in range(m + 1):
            t = j / m
            x = xr - L * t
            yb = yr + (y_end - yr) * (3 * t * t - 2 * t * t * t) ** 0.75 + curl * t ** 3
            y = yb + amp * t ** 1.15 * math.sin(TAU * (L * t) / lam - TAU * f / frames + ph0)
            pts.append((x, y))
        out.append((pts, w0, al))
    return out


def wolf_frame(f, W=256, H=128, frames=6):
    pad = Pad(W, H)
    X, Y = pad.grid()
    ph = TAU * f / frames
    st = Stack(pad)
    glow_extra = np.zeros((H, W), np.float32)
    # ---- 긴 갈기 (맨 뒤): 흐르는 가닥 (끝으로 가늘고 흐려짐) · 가닥 위 가장자리의 가는 빛 줄
    for pts, w0, al in wolf_mane(f, frames):
        st.body = np.maximum(st.body, pad.stroke(pts, 1.0, wfn=lambda t, w0=w0: 2 * w0 * (1 - t) ** 0.9 + 0.6,
                                                  afn=lambda t, al=al: al * (0.5 * (1 - t) ** 1.35 + 0.03), soft=1.2))
        st.add_core(pad.stroke(pts[:25], 1.0, wfn=lambda t, w0=w0: (0.35 + 0.18 * w0) * (1 - t) + 0.2, afn=lambda t, al=al: al * 0.55 * (1 - t) ** 1.8))
    # ---- 목 둘레 털 덩이 (뾰족 털 — 겹쳐 그림: 뒤 털부터)
    for pts, wd in wolf_tufts(ph)[:-1]:
        m = pad.poly(taper_poly(pts, lambda t, wd=wd: wd * (1 - t) ** 0.85 + 0.15))
        st.over(m, 0.5, (rim_of(m, 0.7) * 0.5 + pad.stroke(pts[:8], 0.7, 0.2, a0=0.4, a1=0.05)) * m)
    # ---- 입 속 (어두운 틈) — 아래턱 · 위 머리가 덮음
    alpha = math.radians(24.0 + 4.5 * math.sin(ph))
    hx, hy = WF_HINGE
    lower = rot_pts(wolf_lower_closed(), hx, hy, alpha)
    teeth_line_lo = rot_pts(sh(bez((197.0, 61.0), (216.0, 61.8), (233.5, 61.5), 8)), hx, hy, alpha)
    lip_up = sh(bez((200.0, 59.5), (220.0, 55.5), (237.5, 54.5), 8))
    mouth = pad.poly(lip_up + teeth_line_lo[::-1])
    st.over(mouth, 0.16, mouth * 0.04)
    # 혀 (아래턱 위 — 턱과 함께 돎, 조금 늘어짐)
    tongue = rot_pts(sh(bez((201.0, 63.5), (215.0, 66.5), (229.0, 63.8), 10)), hx, hy, alpha * 0.93)
    tg = pad.poly(taper_poly(tongue, lambda t: 2.7 * math.sin(math.pi * min(1.0, 0.12 + t * 0.92)) ** 0.6))
    st.over(tg * mouth, 0.44, (rim_of(tg, 0.6) * 0.45 + pad.stroke(tongue[2:-2], 0.5, a0=0.3)) * tg * mouth)
    # ---- 아래턱 (윗면 잇몸 줄이 밝음)
    lo_m = pad.poly(lower)
    lit_l, dark_l, _ = bevel_light(lo_m, 3.0)
    st.over(lo_m, 0.6 + 0.14 * lit_l - 0.12 * dark_l, (rim_of(lo_m, 0.9) * 0.85 + lit_l * 0.3) * lo_m)
    st.add_core(pad.stroke(teeth_line_lo, 0.8, a0=0.7) * lo_m)
    # ---- 위 머리: 비탈 빛 (위 왼쪽) · 위쪽 (이마 · 콧등) 이 조금 밝음
    up_m = pad.poly(wolf_upper())
    lit_u, dark_u, _ = bevel_light(up_m, 6.5)
    top_k = clamp01((63 - Y) / 30.0)
    back = smooth(179.0, 165.0, X) * smooth(27.0 + WF_DY, 35.0 + WF_DY, Y)  # 뒤통수 가장자리 (털에 묻힘 — 테를 옅게)
    st.over(up_m, 0.6 + 0.16 * lit_u - 0.12 * dark_u + 0.08 * top_k, (rim_of(up_m, 1.0) * 0.92 * (1 - 0.85 * back) + lit_u * 0.5) * up_m)
    st.add_core(pad.stroke(sh(bez((206.0, 36.5), (222.0, 38.5), (236.0, 43.0), 10)), 1.0, wfn=lambda t: 3.0 * np.clip(np.sin(np.pi * t), 0, 1) ** 0.7 + 0.3,
                           afn=lambda t: 0.28 * np.clip(np.sin(np.pi * t), 0, 1), soft=1.2) * up_m)  # 콧등 빛
    # 뒤통수를 덮는 털 (머리 가장자리를 털 덩이에 이어 줌)
    rnd_t = random.Random(4545)
    for i in range(7):
        u = (i + 0.5) / 7
        x0, y0 = 170.0 + 4.0 * math.sin(math.pi * u) + rnd_t.uniform(-1.5, 1.5), 32.0 + WF_DY + 36.0 * u
        ang = math.radians(193 - 22 * u + rnd_t.uniform(-6, 6))
        L = rnd_t.uniform(15, 23)
        sway = 1.2 * math.sin(ph + u * 5.0)
        tp = (x0 + math.cos(ang) * L, y0 + math.sin(ang) * L + sway)
        pts = bez((x0, y0), (x0 + math.cos(ang) * L * 0.5 - math.sin(ang) * 2.0, y0 + math.sin(ang) * L * 0.5 + math.cos(ang) * 2.0), tp, 10)
        m = pad.poly(taper_poly(pts, lambda t: 3.3 * (1 - t) ** 0.85 + 0.15))
        st.over(m, 0.56, (rim_of(m, 0.6) * 0.5 + pad.stroke(pts[:7], 0.6, 0.2, a0=0.35, a1=0.05)) * m)
    st.add_core(pad.stroke(lip_up, 0.8, a0=0.65) * up_m)
    # 귀 속 (안쪽 세모 줄 · 어두운 속)
    st.add_core(pad.stroke(sh([(171.5, 26.0), (176.0, 10.5), (184.0, 24.0)]), 0.9, a0=0.7) * up_m)
    st.body = st.body * (1 - pad.poly(sh([(172.6, 25.0), (176.2, 13.0), (182.5, 24.5)])) * 0.38)
    # 이마 · 볼 털결 (짧은 줄 — 뒤로 누움)
    for (x0, y0, x1, y1, a) in ((197, 30.5, 186, 31.0, 0.32), (199, 34.5, 186, 36.0, 0.28), (192, 41.5, 178, 42.0, 0.3), (189, 47.0, 174, 48.5, 0.26),
                                (182, 36.0, 170, 37.5, 0.24)):
        st.add_core(pad.stroke(sh([(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2 - 1.0), (x1, y1)]), 0.7, 0.2, a0=a, a1=0.05) * up_m)
    # 눈썹 뼈 (성난 눈매 — 코 쪽으로 내려옴) · 콧등 주름 (으르렁) · 주둥이 옆 줄 · 볼 털 (들쭉날쭉)
    st.add_core(pad.stroke(sh(bez((200.5, 35.0), (209.0, 34.5), (219.5, 39.8), 8)), 1.5, 0.6, a0=0.95, a1=0.7))
    for k, (x0, y0) in enumerate(((223.0, 39.0), (228.0, 40.0), (233.0, 41.5))):
        st.add_core(pad.stroke(sh(bez((x0 - 1.5, y0 - 0.5), (x0 + 0.5, y0 + 2.0), (x0 - 0.5, y0 + 4.5), 5)), 0.8, 0.3, a0=0.55, a1=0.2) * up_m)
    st.add_core(pad.stroke(sh(bez((214.0, 47.5), (226.0, 48.0), (237.0, 47.0), 8)), 0.8, 0.4, a0=0.4, a1=0.25) * up_m)
    cheek = sh([(201.0, 58.0), (195.0, 57.0), (197.0, 62.0), (189.5, 61.5), (192.0, 66.5), (184.0, 66.0), (186.5, 70.5), (178.5, 69.0)])
    st.add_core(pad.stroke(cheek, 0.9, 0.4, a0=0.65, a1=0.3))
    cheek2 = sh([(192.0, 50.0), (186.0, 49.0), (188.5, 54.5), (181.0, 54.0), (183.5, 59.0), (175.0, 58.0)])
    st.add_core(pad.stroke(cheek2, 0.8, 0.35, a0=0.45, a1=0.2) * up_m)
    # 코 (코끝 판 · 빛 점)
    nose = pad.poly(sh(bez((238.0, 42.0), (245.0, 43.0), (244.2, 49.5), 6) + [(240.0, 50.0), (237.0, 46.0)]))
    st.over(nose * up_m, 0.75, (rim_of(nose, 0.6) * 0.95 + pad.disc_field(241.5, 44.5 + WF_DY, 0.9) * 0.9) * nose)
    # ---- 이빨: 위 송곳니 (길게 아래로 · 조금 휨) · 작은 앞니 · 어금니 / 아래 송곳니 (위로 — 턱과 함께 돎)
    teeth = pad.zeros()
    fang = bez((225.3, 55.7), (227.0, 62.5), (229.2, 68.0), 6) + bez((229.2, 68.0), (230.0, 61.0), (230.7, 55.4), 6)[1:]
    teeth = np.maximum(teeth, pad.poly(sh(fang)))
    up_teeth = [((235.0, 54.8), (238.5, 54.4), (236.6, 60.0)), ((220.0, 56.2), (223.0, 56.0), (221.6, 60.2)),
                ((212.5, 57.0), (216.0, 56.8), (214.3, 61.0)), ((205.5, 58.3), (209.0, 57.9), (207.2, 61.6))]
    for tri in up_teeth:
        teeth = np.maximum(teeth, pad.poly(sh(list(tri))))
    lo_fang = bez((223.3, 61.7), (224.0, 56.0), (225.3, 50.6), 6) + bez((225.3, 50.6), (227.4, 56.0), (228.2, 61.6), 6)[1:]
    lo_teeth = [lo_fang, [(230.2, 61.5), (233.0, 61.4), (231.8, 56.5)], [(214.0, 61.8), (217.0, 61.8), (215.6, 57.5)], [(206.5, 61.6), (209.5, 61.6), (208.0, 57.8)]]
    for tri in lo_teeth:
        teeth = np.maximum(teeth, pad.poly(rot_pts(sh(list(tri)), hx, hy, alpha)))
    st.over(teeth, 0.95, teeth)
    # ---- 눈: 빛나는 아몬드 (뒤 끝이 높음) · 반짝임 · 눈에서 뒤로 끌리는 빛 꼬리 (머리 위에서는 옅게 · 갈기와 같은 물결)
    eye = lens(pad, (204.0, 40.2 + WF_DY), (218.4, 44.6 + WF_DY), 5.6, 1.0, power=0.6, skew=0.55)
    socket = gblur(lens(pad, (201.5, 39.0 + WF_DY), (220.5, 45.5 + WF_DY), 9.5, 1.0, power=0.7), 1.2 * SS)
    st.body = st.body * (1 - 0.45 * socket * (1 - eye))  # 눈 둘레 그늘 (눈이 돋보이게)
    st.core = st.core * (1 - 0.6 * socket * (1 - eye))
    st.over(eye, 1.0, eye)
    tw = 0.75 + 0.25 * math.cos(ph)
    st.add_core(star4(pad, 212.0, 42.6 + WF_DY, 9.5 + 3.0 * tw, 1.0, rot=0.25 + 0.15 * math.sin(ph), thin=0.11, diag=0.42))
    glow_extra = np.maximum(glow_extra, pad.down(pad.radial(212.0, 42.5 + WF_DY, 20.0, 1.25)) * 1.0)
    over_head = 1 - 0.8 * up_m * (1 - eye)
    for k, (dy0, a0, L) in enumerate(((0.0, 1.0, 132.0), (5.0, 0.55, 96.0))):
        trail = []
        for j in range(31):
            t = j / 30
            x = 205.0 - L * t
            yb = 40.5 + WF_DY + dy0 * t - 7.0 * math.sin(math.pi * min(1.0, t * 1.25)) * (1 - 0.45 * t)
            y = yb + 4.5 * t ** 1.2 * math.sin(TAU * (L * t) / 60.0 - ph + 1.1 + k)
            trail.append((x, y))
        st.add_core(pad.stroke(trail, 1.0, wfn=lambda t: 1.6 * (1 - t) + 0.3, afn=lambda t, a0=a0: a0 * (1 - t) ** 1.3) * over_head)
        st.body = np.maximum(st.body, pad.stroke(trail, 1.0, wfn=lambda t: 5.0 * (1 - t) + 1.0, afn=lambda t, a0=a0: a0 * 0.55 * (1 - t) ** 1.2, soft=1.5) * (1 - 0.5 * up_m))
    # ---- 혼령 알갱이 (갈기에서 뒤로 흘러감 · 이어 돌림)
    rnd = random.Random(31)
    for i in range(12):
        q = (rnd.random() + f / frames) % 1.0
        x0 = rnd.uniform(70, 150)
        y0 = rnd.uniform(28, 104)
        x = x0 - 60 * q
        y = y0 + 4 * math.sin(q * 5 + i)
        a = math.sin(math.pi * q) * rnd.uniform(0.5, 0.95)
        st.add_core(pad.disc_field(x, y, 0.9 + 0.5 * (1 - q)) * a)
        st.body = np.maximum(st.body, pad.disc_field(x, y, 2.4, soft=1.2) * a * 0.6)
    # 뒤 (왼쪽) 끝 · 칸 가장자리로 흐려짐
    fade = smooth(4.0, 44.0, X)
    core1 = pad.down(st.core * fade)
    glow1 = obj_glow(pad, st.body * fade, core1, halo=((1.3, 0.45), (3.5, 0.32), (8.0, 0.18)), body_k=1.0, core_bloom=((1.2, 0.32), (3.0, 0.24)))
    glow1 = screen(glow1, glow_extra)
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jwolf", "lookat_direction", 256, 128, 6, 4, "warrior", loop=True, zoom=0,
       desc="펜리르 혼령 늑대 머리 (이어 돌림): 오른쪽을 보는 옆모습 — 벌린 턱 (송곳니 · 혀) · 선 귀 · 빛나는 눈 (뒤로 끌리는 빛 꼬리) · 성난 눈썹 · 콧등 주름 · 목 둘레 뾰족 털 · 왼쪽으로 흩날리는 갈기 가닥 (물결이 흘러감)")(wolf_frame)


# ------------------------------------------------------------ rfx_jninerunes (지어낸 각진 글자 아홉이 위로 굽은 호 (왕관) 로 — 차례로 켜지고 마지막에 이어짐)
# 호: 가운데 (256, NR_YC) · 반지름 NR_R — 글자 k (0 = 왼쪽 → 8 = 오른쪽) 는 각 -90° + (k - 4) × NR_STEP, 글자의 위 = 호의 바깥 (반지름 쪽).
# 꼭대기 글자 (k = 4) 가운데 y = 70 · 양 끝 글자 가운데 (58, 158) / (454, 158).

NR_W, NR_H = 512, 256
NR_TOP, NR_END_X, NR_END_Y = 70.0, 198.0, 158.0
NR_R = (NR_END_X ** 2 + (NR_END_Y - NR_TOP) ** 2) / (2 * (NR_END_Y - NR_TOP))
NR_YC = NR_TOP + NR_R
NR_STEP = 2 * math.asin(NR_END_X / NR_R) / 8
NR_GW, NR_GH, NR_LW = 33.0, 48.0, 3.5  # 글자 폭 · 높이 · 획 굵기
NR_FLARE = [1.0, 1.25, 1.12]  # 9 ~ 11 (모두 더 밝게)
NR_LINE = [0.55, 1.0, 1.0]  # 9 ~ 11 이음 줄이 가운데에서 양쪽으로 뻗은 몫


def nr_pos(k):
    a = -math.pi / 2 + (k - 4) * NR_STEP
    return 256.0 + math.cos(a) * NR_R, NR_YC + math.sin(a) * NR_R, a


def ninerunes_frame(f, W=NR_W, H=NR_H):
    pad = Pad(W, H)
    rr, th = polar(pad, 256.0, NR_YC)
    core = pad.zeros()
    glow = pad.zeros()
    final = f >= 9
    fk = NR_FLARE[f - 9] if final else 0.0
    for k in range(9):
        x, y, a = nr_pos(k)
        ang = a + math.pi / 2
        if final:
            state, sc, cb, gb = "lit", 1.0 + 0.04 * (fk - 1) / 0.25, 1.0, 0.85 + 0.15 * (fk - 1) / 0.25
        elif k == f:
            state, sc, cb, gb = "flash", 1.14, 1.0, 1.0
        elif k < f:
            age = f - k
            shimmer = 0.9 + 0.1 * math.cos(age * 1.9 + k)
            state, sc, cb, gb = "lit", 1.0, 0.86 * shimmer, 0.72 * shimmer
        else:
            state, sc, cb, gb = "off", 1.0, 0.0, 0.1
        m = glyph_mask(pad, k, x, y, NR_GW * sc, NR_GH * sc, NR_LW * sc, ang=ang)
        if state == "off":
            glow = np.maximum(glow, m * gb)
            continue
        # 글자 몸 (빛) · 날카로운 획 (심지 — 획 가운데가 가장 밝음)
        mi = glyph_mask(pad, k, x, y, NR_GW * sc, NR_GH * sc, NR_LW * sc * 0.45, ang=ang)
        core = np.maximum(core, np.maximum(m * 0.78 * cb, mi * cb))
        glow = np.maximum(glow, m * gb)
        glow = np.maximum(glow, pad.radial(x, y, 30 + 6 * (state == "flash") + 6 * fk, 1.6) * (0.35 * gb + 0.15 * fk))
        if state == "flash":
            # 켜지는 번쩍: 큰 네 뿔 별 · 둥근 빛 · 튀는 불티
            core = np.maximum(core, star4(pad, x, y, 44, 0.8, rot=ang, thin=0.045, diag=0.38, core=False))
            glow = np.maximum(glow, pad.radial(x, y, 50, 1.2) * 0.9)
            core = np.maximum(core, gauss(np.sqrt((pad.grid()[0] - x) ** 2 + (pad.grid()[1] - y) ** 2) - 33.0, 0.8) * 0.5)
            rs = random.Random(900 + k)
            for i in range(9):
                sa = rs.uniform(0, TAU)
                d0, d1 = rs.uniform(24, 29), rs.uniform(34, 46)
                p0 = (x + math.cos(sa) * d0, y + math.sin(sa) * d0)
                p1 = (x + math.cos(sa) * d1, y + math.sin(sa) * d1)
                core = np.maximum(core, streak(pad, p0, p1, rs.uniform(1.1, 1.8), rs.uniform(0.6, 1.0)))
        elif state == "lit" and not final and f - k == 1:
            # 막 켜진 다음 장: 남은 작은 반짝임
            core = np.maximum(core, star4(pad, x + 12 * math.cos(ang - 0.9), y + 12 * math.sin(ang - 0.9), 9, 0.8, rot=0.3, thin=0.12, diag=0.0))
    if final:
        # ---- 이음 줄: 호를 따라 글자 사이를 잇는 가는 빛 줄 (가운데에서 양쪽으로) · 사이마다 작은 마름모
        reach = NR_LINE[f - 9]
        gap = (NR_GW * 0.5 + 7.0) / NR_R
        line_c = pad.zeros()
        line_g = pad.zeros()
        for k in range(8):
            a0 = -math.pi / 2 + (k - 4) * NR_STEP + gap
            a1 = -math.pi / 2 + (k + 1 - 4) * NR_STEP - gap
            # 가운데 (k = 3.5) 에서 먼 마디일수록 늦게 그어짐
            dist = abs(k + 0.5 - 4) / 4.0
            g = clamp01((reach - dist * 0.85) / 0.3)
            if g <= 0:
                continue
            if k + 0.5 < 4:
                a0 = a1 - (a1 - a0) * g
            else:
                a1 = a0 + (a1 - a0) * g
            pts = arc_pts(256.0, NR_YC, NR_R, a0, a1, n=24)
            line_c = np.maximum(line_c, pad.stroke(pts, 1.3, a0=0.95))
            line_g = np.maximum(line_g, pad.stroke(pts, 5.0, a0=0.6, soft=2.0))
            if g >= 1:
                am = -math.pi / 2 + (k + 0.5 - 4) * NR_STEP
                mx, my = 256.0 + math.cos(am) * NR_R, NR_YC + math.sin(am) * NR_R
                ux, uy = math.cos(am), math.sin(am)
                tx, ty = -uy, ux
                dm = pad.poly([(mx + ux * 5.0, my + uy * 5.0), (mx + tx * 3.0, my + ty * 3.0), (mx - ux * 5.0, my - uy * 5.0), (mx - tx * 3.0, my - ty * 3.0)])
                line_c = np.maximum(line_c, dm)
                line_g = np.maximum(line_g, dm * 0.9)
        # 바깥 · 안쪽 옅은 호 (왕관 띠)
        for dr, a_ in ((-31.0, 0.32), (31.0, 0.22)):
            pts = arc_pts(256.0, NR_YC, NR_R + dr, -math.pi / 2 - 4 * NR_STEP * reach, -math.pi / 2 + 4 * NR_STEP * reach, n=80)
            line_c = np.maximum(line_c, pad.stroke(pts, 0.8, a0=a_, taper=0.6))
            line_g = np.maximum(line_g, pad.stroke(pts, 4.0, a0=a_ * 0.6, taper=0.6, soft=1.5))
        core = np.maximum(core, line_c * min(1.0, fk))
        glow = np.maximum(glow, line_g * fk)
        # ---- 작은 불티 (글자 둘레 · 줄 위 — 장마다 다른 자리에서 반짝)
        rs = random.Random(70 + f)
        for i in range(22):
            k = rs.randint(0, 8)
            x, y, a = nr_pos(k)
            da = rs.uniform(-0.5, 0.5) * NR_STEP
            dr = rs.uniform(-34, 34)
            sx, sy = 256.0 + math.cos(a + da) * (NR_R + dr), NR_YC + math.sin(a + da) * (NR_R + dr)
            core = np.maximum(core, star4(pad, sx, sy, rs.uniform(2.5, 6.0), rs.uniform(0.55, 1.0), rot=rs.uniform(0, 1), thin=0.15, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(sx, sy, 2.5, soft=1.5) * 0.5)
    return finish(pad, glow, core, halo=((1.4, 0.48), (3.6, 0.38), (8.5, 0.24)), body_k=0.85, core_bloom=((1.3, 0.35), (3.5, 0.3)), win=5.0)


sprite("rfx_jninerunes", "rotate_xyz", 512, 256, 12, 4, "spellblade", zoom=10,
       desc="지어낸 각진 글자 아홉의 왕관 호: 0 ~ 8 글자 k 가 장 k 에 번쩍 켜져 (먼저 켜진 글자는 계속 빛남), 9 ~ 11 모두 더 밝아지고 호를 따라 가는 빛 줄이 이음 · 작은 불티")(ninerunes_frame)


# ------------------------------------------------------------ rfx_jwail (연기 유령 얼굴의 비명 — 칸 가운데 = 얼굴 가운데)
# 얼굴은 얼굴 자리 (가운데 기준 u, v — v 아래) 에서 짙기 (연기 몸) 와 또렷한 줄 (눈구멍 테 · 입 테 · 광대 · 눈썹) 로 그린 뒤,
# 장마다 자리를 비틀어 (0 ~ 2 소용돌이 · 3 ~ 4 커짐 · 5 ~ 7 바깥 · 위로 찢겨 가닥으로 끌림) 노이즈 결을 입힘.

WL_SCALE = [1.0, 1.0, 1.0, 1.05, 1.09, 1.1, 1.1, 1.1]
WL_MOUTH = [(8.0, 18.5), (8.4, 19.0), (9.0, 20.5), (10.8, 25.5), (12.2, 28.5), (11.8, 28.0), (11.4, 27.5), (11.0, 27.0)]
WL_EYE_RY = [11.0, 11.0, 11.5, 12.6, 13.4, 13.4, 13.4, 13.4]
WL_CHIN = [55.0, 55.0, 56.0, 60.0, 63.0, 63.0, 63.0, 63.0]
WL_AMP = [0.62, 0.85, 0.97, 1.0, 1.0, 0.84, 0.58, 0.32]  # 연기 짙기
WL_ERO = [0.3, 0.17, 0.07, 0.05, 0.05, 0.2, 0.36, 0.52]  # 노이즈로 깎임 (클수록 가닥으로 갈라짐)
WL_TWIST = [2.5, 1.25, 0.32, 0.0, 0.0, 0.0, 0.0, 0.0]  # 0 ~ 2 소용돌이 (가장자리에서 라디안)
WL_SPREAD = [1.32, 1.13, 1.02, 1.0, 1.0, 1.0, 1.0, 1.0]  # 0 ~ 2 퍼진 연기가 모여듦
WL_TEAR = [0.0, 0.0, 0.0, 0.0, 0.0, 0.34, 0.67, 1.0]  # 5 ~ 7 찢김


def wail_face(pad, f, c=96.0):
    """얼굴 짙기 (4배) · 또렷한 줄 (4배) — 이 장의 크기 · 입 · 눈 · 턱"""
    s = WL_SCALE[f]
    P = lambda u, v: (c + u * s, c + v * s)
    PP = lambda pts: [P(u, v) for u, v in pts]
    chin = WL_CHIN[f]
    # 머리: 위는 둥근 머리뼈 · 광대에서 가장 넓고 · 홀쭉한 볼을 지나 길게 늘어난 턱으로 좁아짐
    right = []
    for i in range(29):
        t = i / 28
        v = -50.0 + (chin + 50.0) * t
        if v < -6:
            hw = 30.0 * math.sqrt(max(0.0, 1 - ((v + 6) / 44.0) ** 2))
        else:
            q = (v + 6) / (chin + 6)
            hw = 30.0 * (1 - q ** 1.5) ** 0.5 * (1 - 0.5 * q) - 3.0 * math.sin(math.pi * min(1.0, q * 1.6)) + 1.5 * q
        right.append((max(0.5, hw), v))
    head_pts = PP(right + [(-u, v) for u, v in right[::-1]])
    head = gblur(pad.poly(head_pts), 3.0 * SS)
    # 연기 꼬리: 턱 아래로 흘러내려 모이는 가닥 셋 · 턱 옆으로 흘러내리는 옅은 가닥
    wisps = pad.zeros()
    ph = f * 0.7
    for k, (u0, L, bend, w0) in enumerate(((-6.0, 50.0, -7.0, 6.0), (2.0, 60.0, 4.0, 7.0), (8.0, 46.0, 10.0, 5.0))):
        pts = []
        for i in range(19):
            t = i / 18
            u = u0 * (1 - 0.6 * t) + bend * t * t + 3.5 * math.sin(ph + k * 1.7 + t * 3.4) * t
            v = chin - 14 + L * t
            pts.append(P(u, v))
        wisps = np.maximum(wisps, pad.stroke(pts, 1.0, wfn=lambda t, w0=w0: w0 * s * (1 - t) ** 0.7 + 0.6, afn=lambda t: 0.8 * (1 - t) ** 1.1, soft=4.0))
    for sg in (-1, 1):
        pts = []
        for i in range(15):
            t = i / 14
            pts.append(P(sg * (27.0 + 9.0 * t + 2.0 * math.sin(ph + t * 3)), 4.0 + 44.0 * t))
        wisps = np.maximum(wisps, pad.stroke(pts, 1.0, wfn=lambda t: 5.0 * s * (1 - t) + 0.5, afn=lambda t: 0.55 * (1 - t) ** 1.2, soft=3.0))
    # 구멍: 눈 (위가 가운데 쪽으로 기울고 아래 바깥으로 처진 눈구멍) · 길게 벌린 입 · 코 구멍 · 홀쭉한 볼 (파임)
    ery = WL_EYE_RY[f]
    eyes = pad.zeros()
    eye_top = []
    eye_bot = []
    for sg in (-1, 1):
        cx_, cy_ = 14.0 * sg, -15.0
        pts = []
        for i in range(48):
            a = i / 48 * TAU  # 0 = 바깥 옆 기준 각 (아래 반 = sin > 0)
            lo = max(0.0, math.sin(a))
            x0 = 8.4 * math.cos(a) + sg * 3.2 * lo * lo
            y0 = ery * math.sin(a) * (1 + 0.22 * lo)
            ang = -sg * 0.34
            pts.append((cx_ + x0 * math.cos(ang) - y0 * math.sin(ang), cy_ + x0 * math.sin(ang) + y0 * math.cos(ang)))
        eyes = np.maximum(eyes, pad.poly(PP(pts)))
        eye_top.append(PP([pts[(i + 24) % 48] for i in range(25)]))  # 위 반 (빛 받는 눈두덩)
        eye_bot.append(PP([pts[i] for i in range(25)]))
    mrx, mry = WL_MOUTH[f]
    mpts_l = []
    for i in range(56):
        a = i / 56 * TAU
        wob = 1 + 0.06 * math.sin(5 * a + 0.5) + 0.015 * math.sin(11 * a + 1.0)
        rx_ = mrx * (1 + 0.2 * max(0.0, math.sin(a)))  # 아래가 조금 넓음 (늘어진 턱)
        mpts_l.append((rx_ * math.cos(a) * wob, 23.0 + mry * math.sin(a) * wob + (mry - 19.0) * 0.35))
    mpts = PP(mpts_l)
    mouth = pad.poly(mpts)
    nose = pad.polys([PP([(-1.0, -2.0), (-4.5, 6.0), (-1.2, 5.0)]), PP([(1.0, -2.0), (4.5, 6.0), (1.2, 5.0)])])
    cheeks = pad.zeros()
    for sg in (-1, 1):
        cheeks = np.maximum(cheeks, pad.poly(PP([(sg * 14.0, 4.0), (sg * 24.5, 5.0), (sg * 22.0, 28.0), (sg * 16.0, 37.0), (sg * 13.0, 22.0)])))
    socket = gblur(eyes, 2.4 * SS)  # 눈구멍 둘레의 옅은 그늘
    dens = head * (1 - gblur(eyes, 1.0 * SS)) * (1 - 0.35 * socket) * (1 - gblur(mouth, 1.1 * SS)) * (1 - 0.8 * gblur(nose, 0.8 * SS)) * (1 - 0.62 * gblur(cheeks, 3.2 * SS))
    dens = np.maximum(dens, wisps * (1 - gblur(mouth, 1.2 * SS)) * (1 - gblur(eyes, 1.0 * SS)))
    # 또렷한 줄 (심지): 눈두덩 (위 테 밝음 · 아래 옅음) · 입 테 (윗입술 쪽이 밝음) · 눈썹 (가운데가 들림) · 광대 · 볼 파임
    lines = pad.zeros()
    for pts in eye_top:
        lines = np.maximum(lines, pad.stroke(pts, 1.0, wfn=lambda t: 1.5 * s * np.clip(np.sin(np.pi * t), 0, 1) ** 0.5 + 0.3, afn=lambda t: 0.95 * np.clip(np.sin(np.pi * t), 0, 1) ** 0.6))
    for pts in eye_bot:
        lines = np.maximum(lines, pad.stroke(pts, 0.7 * s, a0=0.32))
    top_k = lambda t: 0.4 + 0.6 * np.clip(-np.sin(t * TAU), 0, 1)  # 입 윗쪽 (sin < 0) 이 밝음
    lines = np.maximum(lines, pad.stroke(mpts + [mpts[0]], 1.0, wfn=lambda t: 0.6 + 0.8 * s * np.clip(-np.sin(t * TAU), 0, 1), afn=lambda t: 0.95 * top_k(t)))
    for sg in (-1, 1):
        lines = np.maximum(lines, pad.stroke(PP(bez((sg * 3.0, -28.0), (sg * 11.0, -35.5 - (ery - 11.0)), (sg * 24.0, -27.0), 10)), 1.0,
                                             wfn=lambda t: 1.6 * s * (1 - t) + 0.4, afn=lambda t: 0.9 - 0.45 * t))  # 눈썹 (가운데가 들림)
        lines = np.maximum(lines, pad.stroke(PP(bez((sg * 9.0, -0.5), (sg * 20.0, -3.5), (sg * 28.0, 5.0), 10)), 1.0 * s, 0.4, a0=0.65, a1=0.25))  # 광대
        lines = np.maximum(lines, pad.stroke(PP(bez((sg * 22.0, 9.0), (sg * 24.5, 22.0), (sg * 17.0, 36.0), 10)), 0.9 * s, 0.3, a0=0.45, a1=0.12))  # 볼 파임
    lines = np.maximum(lines, rim_of(nose, 0.5) * 0.5)
    return dens, lines


def wail_frame(f, S=192):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    dens0, lines0 = wail_face(pad, f, c)
    # ---- 자리 비틀기: 이 장의 화면 점 (X, Y) 이 얼굴 자리의 어디를 보나 (Xs, Ys)
    dx, dy = X - c, Y - c
    r = np.sqrt(dx * dx + dy * dy)
    tw = WL_TWIST[f] * clamp01(r / 80.0) ** 0.9
    sp = WL_SPREAD[f]
    ca_, sa_ = np.cos(-tw), np.sin(-tw)
    Xs = c + (dx * ca_ - dy * sa_) / sp
    Ys = c + (dx * sa_ + dy * ca_) / sp
    q = WL_TEAR[f]
    if q > 0:
        # 찢김: 바깥으로 퍼지고 위로 떠오름 · 노이즈 소용돌이
        n1 = fbm(X / 24.0 + 5.1, Y / 24.0 - 2.3, seed=51, octaves=3)
        n2 = fbm(X / 24.0 - 7.7, Y / 24.0 + 4.9, seed=52, octaves=3)
        ex = 1 + 0.3 * q
        Xs = c + (Xs - c) / ex - n1 * 11.0 * q
        Ys = c + (Ys - c) / ex + 8.0 * q - n2 * 11.0 * q
    from scipy import ndimage as ndi
    dens = np.zeros_like(X)
    lines = np.zeros_like(X)
    # 찢길 때는 바깥 · 위로 끌린 자국 (같은 얼굴을 안쪽에서 여러 번 집어 옴 — 바깥으로 갈수록 옅게)
    taps = [(0.0, 1.0)] if q == 0 else [(k / 6.0, (1 - k / 7.0) ** 1.4) for k in range(7)]
    rad_u = dx / np.maximum(r, 1e-3)
    rad_v = dy / np.maximum(r, 1e-3)
    for k, wgt in taps:
        L = 17.0 * q * k
        xs_ = Xs - (rad_u * 0.8) * L
        ys_ = Ys - (rad_v * 0.8 - 0.45) * L
        coords = [ys_ * SS - 0.5, xs_ * SS - 0.5]
        dens = np.maximum(dens, ndi.map_coordinates(dens0, coords, order=1, mode="constant", cval=0.0).astype(np.float32) * wgt)
        if k == 0:
            lines = ndi.map_coordinates(lines0, coords, order=1, mode="constant", cval=0.0).astype(np.float32)
    # ---- 연기 결 (비튼 자리를 따라 흐름 — 소용돌이 · 찢김과 함께 움직임) · 찢길 때는 바깥으로 늘어진 가닥 결
    sc = 1.0 / 20.0
    wx = fbm(Xs * sc + 3.1, Ys * sc - 1.7, seed=61, octaves=2)
    wy = fbm(Xs * sc - 5.3, Ys * sc + 2.2, seed=62, octaves=2)
    n = fbm(Xs * sc * 1.6 + wx * 0.6, Ys * sc * 1.6 + wy * 0.6 - f * 0.12, seed=63, octaves=4, gain=0.5)
    if q > 0:
        th = np.arctan2(dy, dx)
        strands = fbm(np.cos(th) * 6.0 + r / 70.0, np.sin(th) * 6.0 - r / 70.0, seed=64, octaves=3)
        n = n * (1 - 0.6 * q) + strands * 0.9 * q
    dn = clamp01(dens * (0.82 + 0.3 * n) + n * 0.55 * 4 * dens * (1 - dens))
    ero = WL_ERO[f]
    alpha = clamp01((dn - ero) / (1 - ero)) ** 0.85
    amp = WL_AMP[f]
    a1 = pad.down(alpha)
    sm = gblur(a1, 2.0)
    gy, gx = np.gradient(sm)
    lit = clamp01(-(gx * -0.6 + gy * -0.8) * 9.0)
    rim = clamp01(gblur(a1, 0.8) - gblur(a1, 3.2)) * 1.6
    l1 = pad.down(lines * clamp01(dn * 1.6 + 0.15))
    core1 = clamp01(np.maximum(l1 * (1.0 - 0.35 * q), a1 ** 1.6 * 0.32 + lit * 0.4 * a1 ** 0.5 + rim * 0.22)) * amp
    glow1 = clamp01(screen(a1 * 0.9, gblur(a1, 2.5) * 0.35, gblur(l1, 1.5) * 0.3)) * amp
    # ---- 눈구멍 깊은 곳의 작은 불빛 (조금 아래 — 찢기면 꺼짐)
    ek = [0.0, 0.3, 0.7, 0.9, 1.0, 0.5, 0.18, 0.0][f]
    if ek > 0:
        s = WL_SCALE[f]
        pin = pad.zeros()
        for sg in (-1, 1):
            px_, py_ = c + sg * 13.0 * s, c - 12.0 * s
            pin = np.maximum(pin, pad.disc_field(px_, py_, 1.15, soft=0.6))
        p1 = pad.down(pin)
        core1 = np.maximum(core1, p1 * 0.9 * ek)
        glow1 = screen(glow1, gblur(p1, 2.2) * 1.8 * ek)
    # ---- 비명 소리 고리 (3 ~ 5 — 얼굴에서 퍼지는 끊기고 일렁이는 가는 고리)
    rings = {3: [(60.0, 0.9)], 4: [(72.0, 0.8), (53.0, 0.55)], 5: [(83.0, 0.45), (65.0, 0.4)]}.get(f, [])
    if rings:
        rr, th = polar(pad, c, c + 6.0)
        brk = clamp01(0.5 + 1.0 * fbm(np.cos(th) * 2.2 + 9.0 + f, np.sin(th) * 2.2 - 4.0, seed=71, octaves=2))
        rc = pad.zeros()
        rg = pad.zeros()
        for rad, a in rings:
            wob = 2.2 * np.sin(th * 5 + f * 1.3 + rad * 0.1) + 1.2 * np.sin(th * 9 - f)
            rc = np.maximum(rc, gauss(rr - rad - wob, 0.6) * a * brk * 0.55)
            rg = np.maximum(rg, gauss(rr - rad - wob, 2.4) * a * brk * 0.35)
        core1 = np.maximum(core1, pad.down(rc))
        glow1 = screen(glow1, pad.down(rg))
    wb = win_border(core1.shape, 5.0)
    return clamp01(glow1) * wb, clamp01(core1) * wb


sprite("rfx_jwail", "rotate_xyz", 192, 192, 8, 4, "reaper", zoom=4,
       desc="연기 유령 얼굴의 비명: 0 ~ 2 연기가 소용돌이치며 얼굴로 모임 (처진 빈 눈구멍 · 길게 벌린 입 · 홀쭉한 볼), 3 ~ 4 비명 (입이 가장 크고 살짝 커짐 · 일렁이는 소리 고리), 5 ~ 7 바깥 · 위로 끌린 가닥으로 찢겨 사라짐")(wail_frame)


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
