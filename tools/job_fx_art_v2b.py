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

그림 (이름 — 보는 꼴 — 칸 — 장 수 · 칸 뜻은 옛 그림과 같음):
- rfx_jslash2    rotate_xyz       256×128 ×8   큰 베기 초승달 (아래로 불룩 · 왼쪽 → 아래 → 오른쪽, 참격 판 slash_job 이 180° 돌려 붙임)
- rfx_jring2     direction_z      192×192 ×8   땅에 눕는 한 바퀴 베기 (12시에서 시계 방향 → 고리 전체) + 칸 16 ~ 31 좌우 뒤집음 (반시계)
- rfx_jburst2    rotate_xyz       192×192 ×8   터짐 (번쩍 · 충격 고리 둘 · 빛 갈래 · 불티)
- rfx_jsmash2    direction_z      192×192 ×8   내려찍기 (충격 고리 바깥 끝 0.46 S · 빛나는 금 · 돌조각 · 먼지)
- rfx_jwhirl2    direction_z      192×192 ×8   회전 칼날 셋 (이어 돌림 — 8 장 = 120°)
- rfx_jspin2     direction_z      192×192 ×8   도는 양날 창 (이어 돌림 — 8 장 = 180°)
- rfx_jsmoke2    rotate_xyz       128×128 ×8   그림자 연기 (퍼지며 갈라져 사라짐)
- rfx_jcross2    rotate_xyz       128×128 ×6   엇갈린 두 베기 + 가운데 X 번쩍
- rfx_jstab2     rotate_xyz       128×128 ×6   찌르기 번쩍 (위가 찌르는 쪽)
- rfx_jthunder2  lookat_y         128×512 ×6   내리치는 벼락 (아래 끝이 땅)
- rfx_jvolt2     direction_z      192×192 ×8   땅에 눕는 번개 고리 (반지름 0.4 S)
- rfx_jsoul2     rotate_xyz       64×64 ×6     혼불 (이어 돌림)
- rfx_jreaper2   lookat_y         192×256 ×8   사신 그림자 (0 ~ 3 흔들림 이어 돌림 · 4 ~ 7 낫 휘두름)
- rfx_jwind2     direction_z      192×192 ×8   땅에 눕는 나선 바람 고리
- rfx_jtarget2   direction_z      192×192 ×6   과녁 (0 ~ 3 조임 · 3 ~ 5 맞춘 채 깜빡임 — 바깥 고리 0.42 S)
- rfx_jdash2     lookat_direction 256×64 ×6    돌진 줄기 (오른쪽이 앞)
- rfx_jline2     lookat_direction 256×32 ×6    일섬
- rfx_jspeed2    rotate_xyz       256×256 ×4   1인칭 화면 가장자리 빠른 줄 (이어 돌림)
- rfx_jtwister2  lookat_y         128×256 ×8   솟는 회오리 (이어 돌림)
- rfx_jcomet2    lookat_direction 256×96 ×4    불꽃 꼬리 (머리 0.82 W · 이어 돌림)
- rfx_jshatter2  rotate_xyz       192×192 ×8   깨지는 거울
- rfx_jbeam2     lookat_direction 256×64 ×6    굵은 광선 (조각을 이어 붙임 — 양 끝 가늘어짐)
- rfx_jgate2     lookat_y         256×256 ×8   명계의 문 (0 ~ 2 솟음 · 3 ~ 5 열린 채 소용돌이 이어 돌림 · 6 ~ 7 닫힘)
- rfx_jhand2     lookat_y         64×128 ×6    망자의 손 (아래 끝이 땅)
- rfx_jwing2     direction_z      256×192 ×4   빛의 날개 (이어 돌림)
- rfx_jstun2     rotate_xyz       128×64 ×8    기절 별 다섯 (이어 돌림 — 8 장 = 1/5 바퀴)
- rfx_jjudge2    rotate_xyz       64×64 ×4     심판 번개 문장 (이어 돌림)
- rfx_jheal2     rotate_xyz       128×128 ×6   회복 십자
- rfx_jnote2     rotate_xyz       64×64 ×4     음표 (칸마다 다른 한 장: 4분 · 8분 · 이어진 8분 · 이어진 16분)
- rfx_jstigma2   rotate_xyz       128×128 ×4   성흔 (이어 돌림 — 4 장 = 90°)
- rfx_jchain2    lookat_direction 256×32 ×4    빛의 사슬 (이어 돌림 · 조각을 이어 붙임)
- rfx_jaura2     lookat_y         128×256 ×8   몸에서 솟는 불꽃 (이어 돌림)
- rfx_jpetal2    rotate_xyz       64×64 ×8     0 ~ 3 꽃 (이어 돌림) · 4 ~ 7 꽃잎 하나 (이어 돌림)
- rfx_jbreak2    rotate_xyz       128×128 ×6   방어 깨짐
- rfx_jpage2     rotate_xyz       64×64 ×4     날리는 책장 (이어 돌림)
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
from job_fx_art_v2 import (BG, COLORS, SS, TAU, Pad, Stack, Xf, bevel_light, bez, bolt_pts, clamp01, ease_out, edge_fade, gblur,  # noqa: E402
                           glyph_mask, inner_dist, label, obj_glow, rim_of, rot_pts, screen, smooth, sparkle, star_pts, taper_poly, to_sheet,
                           wing_feather)

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
            a = i * TAU / 8 + (math.pi / 8 if i % 2 else 0.0)
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
    Re = R + 4.0  # 날 끝 (바깥 날 — 옛 그림 띠의 바깥 끝과 같은 자리)
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
    return obj_glow(pad, body_ss, core1, halo=halo, body_k=body_k, core_bloom=core_bloom)


sprite("rfx_jspin2", "direction_z", 192, 192, 8, 4, job="lancer", zoom=0,
       desc="도는 양날 창 (이어 돌림): 자루 · 감은 손잡이 · 가운데 고리 · 가름쇠 · 능선이 있는 잎 창날 둘 + 쓸고 간 부채 · 날 끝 꼬리 · 반짝임 · 불티")(spin_frame)



# ------------------------------------------------------------ rfx_jsmoke2 (그림자 연기 — 뭉게뭉게 퍼지며 사라짐, 빛 = 짙은 연기 · 심지 = 밝은 결)

def smoke_frame(f, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    p = f / 7
    grow = 0.55 + 0.47 * ease_out(p, 1.8)
    rise = -6.0 * p
    rnd = random.Random(7)
    puffs = [(0.0, 0.02, 0.2, 1.0)]
    for i in range(8):
        a = i * TAU / 8 + rnd.uniform(-0.3, 0.3)
        dd = rnd.uniform(0.11, 0.19)
        puffs.append((math.cos(a) * dd, math.sin(a) * dd * 0.85, rnd.uniform(0.1, 0.15), rnd.uniform(0.7, 1.0)))
    for i in range(7):
        a = rnd.uniform(0, TAU)
        dd = rnd.uniform(0.19, 0.26)
        puffs.append((math.cos(a) * dd, math.sin(a) * dd * 0.8, rnd.uniform(0.06, 0.09), rnd.uniform(0.5, 0.8)))
    acc = pad.zeros()
    for k, (dx, dy, r, wgt) in enumerate(puffs):
        drift = 1.0 + 0.12 * p * (k % 3)
        x = c + dx * S * grow * 1.3 * drift
        y = c + rise * (1 + 0.5 * (dy < 0)) + dy * S * grow * 1.3 * drift
        sig = r * S * grow * 0.62
        acc += np.exp(-((X - x) ** 2 + (Y - y) ** 2) / (2 * sig * sig)) * wgt
    dens = 1 - np.exp(-acc * 1.9)
    # 결: 천천히 도는 낮은 노이즈 (도메인 비틀기) — 덩이 가장자리를 뭉게뭉게
    sc = 1.0 / 24.0
    wx = fbm(X * sc + 3.1, Y * sc - 1.7 + p * 0.5, seed=11, octaves=2)
    wy = fbm(X * sc - 5.3 + p * 0.35, Y * sc + 2.2, seed=12, octaves=2)
    n = fbm(X * sc * 1.5 + wx * 0.55, Y * sc * 1.5 + wy * 0.55 - p * 0.7, seed=13, octaves=3, gain=0.45)
    dn = clamp01(dens * (0.9 + 0.2 * n) + n * 0.55 * 4 * dens * (1 - dens))
    ero = 0.05 + 0.48 * p ** 1.2
    alpha = clamp01((dn - ero) / (1 - ero)) ** 0.85
    amp = [1.0, 0.97, 0.92, 0.84, 0.72, 0.58, 0.42, 0.26][f]
    a1 = pad.down(alpha)
    # 빛 받은 결 (위 왼쪽 빛 — 흐린 몸의 기울기)
    sm = gblur(a1, 2.2)
    gy, gx = np.gradient(sm)
    lit = clamp01(-(gx * -0.6 + gy * -0.8) * 9.0)
    rim = clamp01(gblur(a1, 0.8) - gblur(a1, 3.5)) * 1.6
    core1 = clamp01(a1 ** 1.5 * 0.55 + lit * 0.5 * a1 ** 0.5 + rim * 0.25) * amp * 0.95
    glow1 = clamp01(screen(a1 * 0.92, gblur(a1, 2.5) * 0.35)) * amp
    wb = win_border(a1.shape, 5.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jsmoke2", "rotate_xyz", 128, 128, 8, 4, job="assassin", zoom=3, colors=((0.39, 0.12, 0.43), (0.86, 0.26, 0.96)),
       desc="그림자 연기: 덩이 열다섯이 노이즈 결로 뭉게뭉게 퍼지며 (살짝 떠오름) 갈라져 사라짐 — 빛 = 연기 몸 · 심지 = 빛 받은 결")(smoke_frame)


# ------------------------------------------------------------ rfx_jcross2 (엇갈린 두 베기 — X 자로 차례로 그어지고 가운데 번쩍)

CROSS_ST = [(0.6, 0.0, 0.0, 0.0, 1.0, 1.0), (1.0, 0.0, 0.55, 0.0, 1.0, 1.0), (1.0, 0.05, 1.0, 0.0, 1.0, 1.0), (1.0, 0.13, 1.0, 0.09, 0.8, 0.94),
            (0.97, 0.22, 0.97, 0.18, 0.55, 0.74), (0.93, 0.32, 0.93, 0.28, 0.32, 0.48)]


def cut_curve(p0, p1, sag, n=40):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    return [(x0 + dx * t + nx * sag * math.sin(math.pi * t), y0 + dy * t + ny * sag * math.sin(math.pi * t)) for t in (i / n for i in range(n + 1))]


def thin_slash(pad, pts, head, tail, wmax, bright=1.0, side=1.0):
    """곧은 베기 띠 (pts 위의 tail → head): (심지, 빛). side = 밝은 날이 있는 쪽 (+1 / -1)"""
    if head - tail < 0.02:
        return pad.zeros(), pad.zeros()
    d, sfrac = pad.line_field(pts, wmax * 2.5 + 8)
    P = np.asarray(pts)
    # 줄의 어느 쪽인지 (부호): 가까운 마디의 수직 방향
    X, Y = pad.grid()
    i = np.clip((sfrac * (len(pts) - 1)).astype(int), 0, len(pts) - 2)
    tx = P[i + 1, 0] - P[i, 0]
    ty = P[i + 1, 1] - P[i, 1]
    sgn = np.sign((X - P[i, 0]) * (-ty) + (Y - P[i, 1]) * tx)
    sd = d * sgn * side  # + = 밝은 날 쪽
    srel = (sfrac - tail) / (head - tail)
    on = (srel > 0) & (srel < 1)
    prof = crescent_prof(srel, tip=0.2, rise=0.75) * on
    w = wmax * prof
    hw = w / 2
    band = smooth(hw + 1.0, hw - 1.6, np.abs(sd)) * (prof > 0.004)
    tt = np.clip(np.abs(sd) / np.maximum(hw, 0.3), 0, 1)
    core = band * (1 - smooth(0.2, 1.0, tt)) ** 1.1
    core = np.maximum(core, gauss(sd - hw * 0.55, 0.45 + 0.25 * prof) * band * 1.0)
    for off, s0, a in ((hw + 2.6, 0.25, 0.55), (-(hw + 3.2), 0.45, 0.35)):
        core = np.maximum(core, gauss(sd - off, 0.45) * swin(srel, s0, 0.92, 0.2, 0.05) * on * a)
    glow = smooth(hw * 1.5 + 3.5, hw * 1.5 - 2.0, np.abs(sd)) * (prof > 0.004)
    return core * bright, glow * bright


def cross_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    A = cut_curve((S * 0.13, S * 0.16), (S * 0.87, S * 0.84), S * 0.06)
    B = cut_curve((S * 0.87, S * 0.16), (S * 0.13, S * 0.84), -S * 0.06)
    ah, at, bh, bt, wk, br = CROSS_ST[f]
    core = pad.zeros()
    glow = pad.zeros()
    for pts, h, t, sd in ((A, ah, at, 1.0), (B, bh, bt, -1.0)):
        cc, gg = thin_slash(pad, pts, h, t, 11.5 * wk, br, sd)
        core = np.maximum(core, cc)
        glow = np.maximum(glow, gg)
        if h < 1.0 and h - t > 0.05 and f <= 1:
            k = int(h * (len(pts) - 1))
            hx, hy = pts[max(0, k - 1)]
            core = np.maximum(core, star4(pad, hx, hy, 11, 0.95, rot=0.3, thin=0.12, diag=0.0))
            glow = np.maximum(glow, pad.radial(hx, hy, 10, 1.4) * 0.8)
    # 가운데 번쩍 (X 꼴 별 + 퍼지는 고리)
    fk = [0.0, 0.0, 1.0, 0.62, 0.3, 0.12][f]
    if fk > 0:
        core = np.maximum(core, star4(pad, c, c, 34 * fk + 6, min(1.0, fk * 1.3), rot=math.pi / 4, thin=0.08, diag=0.42))
        core = np.maximum(core, pad.disc_field(c, c, 5.5 * fk + 1.0, soft=1.5))
        glow = np.maximum(glow, pad.radial(c, c, 26 * fk + 8, 1.3) * fk)
        rr, _ = polar(pad, c, c)
        rg = 10 + 30 * (1 - fk)
        core = np.maximum(core, gauss(rr - rg, 0.7) * fk * 0.8)
        glow = np.maximum(glow, gauss(rr - rg, 2.6) * fk * 0.6)
    # 튀는 불티 (가운데에서)
    if f >= 2:
        rnd = random.Random(55)
        q = (f - 2) / 3
        for i in range(10):
            a = rnd.uniform(0, TAU)
            dist = 10 + 36 * ease_out(min(1.0, q + 0.2)) * rnd.uniform(0.6, 1.0)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            al = (1 - q) ** 1.2 * rnd.uniform(0.6, 1.0)
            Ls = 4 + 6 * (1 - q)
            core = np.maximum(core, streak(pad, (x - math.cos(a) * Ls, y - math.sin(a) * Ls), (x, y), 1.2, al))
            glow = np.maximum(glow, pad.disc_field(x, y, 2.5, soft=1.2) * al * 0.6)
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.4), (6.5, 0.22)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jcross2", "rotate_xyz", 128, 128, 6, 6, job="assassin", zoom=2,
       desc="엇갈린 두 베기: 날 줄 · 곁 잔상 줄이 있는 띠가 X 자로 차례로 그어짐 → 가운데 X 별 번쩍 · 퍼지는 고리 · 불티 → 가늘어지며 사라짐")(cross_frame)


# ------------------------------------------------------------ rfx_jstab2 (찌르기 번쩍 — 위가 찌르는 쪽)

def stab_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    X, Y = pad.grid()
    k = [0.45, 0.85, 1.0, 0.92, 0.75, 0.5][f]
    br = [1.0, 1.0, 1.0, 0.88, 0.66, 0.42][f]
    core = pad.zeros()
    glow = pad.zeros()
    # ---- 창끝 빛 (세로로 긴 날 — 위로 뾰족 · 아래 1/3 이 가장 굵음)
    h = 56.0 * k
    top, bot = (c, c - h), (c, c + h * 0.5)
    ww = 18.0 * k
    core = np.maximum(core, lens(pad, top, bot, ww * 0.55, br, power=0.65, skew=0.72))
    core = np.maximum(core, pad.stroke([(c, c - h + 2), (c, c + h * 0.45)], 1.4, 0.6, a0=br, a1=br * 0.7))
    glow = np.maximum(glow, lens(pad, (c, c - h - 4), (c, c + h * 0.56), ww * 1.25, br, power=0.75, skew=0.72))
    # 위로 흐르는 바람 줄 (창끝 둘레 — 처음 셋)
    if f <= 3:
        rnd = random.Random(31)
        for i in range(6):
            x = c + rnd.choice((-1, 1)) * rnd.uniform(11, 27)
            y0 = c + rnd.uniform(-6, 40) - f * 9
            L = rnd.uniform(18, 34) * k
            al = [0.45, 0.75, 0.55, 0.28][f] * rnd.uniform(0.5, 1.0)
            core = np.maximum(core, pad.stroke([(x, y0), (x, y0 - L)], 0.3, 1.1, a0=0.0, a1=al))
            glow = np.maximum(glow, pad.stroke([(x, y0), (x, y0 - L)], 0.5, 3.0, a0=0.0, a1=al * 0.5, soft=1.0))
    # ---- 가로 충격 (가운데가 굵은 가는 렌즈)
    if f >= 1:
        lw = 54.0 * k
        lt = (4.2 - 0.5 * f)
        core = np.maximum(core, lens(pad, (c - lw, c), (c + lw, c), lt, br * 0.95, power=0.55))
        glow = np.maximum(glow, lens(pad, (c - lw - 4, c), (c + lw + 4, c), lt * 3 + 3, br * 0.8, power=0.7))
    # ---- 압력 고리 둘 (얇은 테 — 퍼지며 흐려짐)
    rr, th = polar(pad, c, c)
    for dl, kk in ((0, 1.0), (1, 0.65)):
        g = f - 1 - dl
        if g < 0 or g > 3:
            continue
        q = g / 3
        r = 16 + 30 * ease_out(q, 1.6)
        al = (1 - q) ** 1.2 * kk * br
        core = np.maximum(core, gauss(rr - r, 0.6 + 0.4 * (1 - q)) * al * 0.9)
        glow = np.maximum(glow, gauss(rr - r, 2.8) * al * 0.6)
    # ---- 가운데 번쩍 · 튀는 불티
    fk = [0.5, 1.0, 0.6, 0.3, 0.12, 0.0][f]
    if fk > 0:
        core = np.maximum(core, star4(pad, c, c, 16 * fk + 4, fk, rot=0.0, thin=0.12, diag=0.4))
        core = np.maximum(core, pad.disc_field(c, c, 4.5 * fk + 1, soft=1.2))
        glow = np.maximum(glow, pad.radial(c, c, 22 * fk + 6, 1.3) * fk)
    if f >= 2:
        rnd = random.Random(41)
        q = (f - 2) / 3
        for i in range(9):
            a = rnd.uniform(0, TAU)
            dist = (18 + 28 * ease_out(q)) * rnd.uniform(0.75, 1.1)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            al = (1 - q) ** 1.1 * br
            if i % 2:
                core = np.maximum(core, star4(pad, x, y, rnd.uniform(3.5, 6.0) * (1 - 0.4 * q), al, rot=rnd.uniform(0, 1), thin=0.16, diag=0.0))
            else:
                Ls = 5 + 4 * (1 - q)
                core = np.maximum(core, streak(pad, (x - math.cos(a) * Ls, y - math.sin(a) * Ls), (x, y), 1.2, al))
            glow = np.maximum(glow, pad.disc_field(x, y, 2.6, soft=1.2) * al * 0.6)
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.4), (6.5, 0.24)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.28)), win=4.0)


sprite("rfx_jstab2", "rotate_xyz", 128, 128, 6, 6, job="lancer", zoom=2,
       desc="찌르기 번쩍: 위로 뾰족한 창끝 빛 (가운데 줄) · 위로 흐르는 바람 줄 · 가로 충격 렌즈 · 압력 고리 둘 · 가운데 번쩍 · 불티")(stab_frame)


# ------------------------------------------------------------ rfx_jthunder2 (내리치는 벼락 — 아래 끝이 땅, 128 × 512)

def thunder_path(seed, W, H):
    rnd = random.Random(seed)
    pts = [(W * 0.5 + rnd.uniform(-0.08, 0.08) * W, -6.0), (W * 0.5, H - 9.0)]
    disp = W * 0.42
    for _ in range(7):
        new = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            mx = (a[0] + b[0]) / 2 + rnd.choice((-1, 1)) * rnd.uniform(0.3, 1.0) * disp
            my = (a[1] + b[1]) / 2 + rnd.uniform(-0.12, 0.12) * (b[1] - a[1])
            new += [(min(W * 0.84, max(W * 0.16, mx)), my), b]
        pts = new
        disp *= 0.6
    return pts


def thunder_branches(seed, path, W, H, n=6):
    rnd = random.Random(seed + 500)
    out = []
    N = len(path)
    for k in range(n):
        frac = 0.1 + 0.75 * (k + rnd.uniform(0.1, 0.9)) / n
        x, y = path[int(N * frac)]
        side = rnd.choice((-1, 1))
        ang = math.pi / 2 + side * rnd.uniform(0.45, 1.05)
        L = H * rnd.uniform(0.06, 0.13)
        p1 = (min(W * 0.92, max(W * 0.08, x + math.cos(ang) * L)), y + math.sin(ang) * L)
        br = bolt_pts(rnd, (x, y), p1, 0.22, 4, 0.55)
        subs = []
        if rnd.random() < 0.7:
            j = rnd.randint(len(br) // 3, len(br) * 2 // 3)
            sx, sy = br[j]
            a2 = ang + side * rnd.uniform(0.3, 0.7)
            L2 = L * rnd.uniform(0.3, 0.55)
            p2 = (min(W * 0.94, max(W * 0.06, sx + math.cos(a2) * L2)), sy + math.sin(a2) * L2)
            subs.append(bolt_pts(rnd, (sx, sy), p2, 0.25, 3, 0.6))
        out.append((br, subs, frac))
    return out


def thunder_frame(f, W=128, H=512):
    pad = Pad(W, H)
    X, Y = pad.grid()
    wk = [0.7, 1.15, 1.0, 0.85, 0.62, 0.4][f]
    br = [0.85, 1.0, 1.0, 0.92, 0.72, 0.42][f]
    main = thunder_path(11, W, H)
    alt = thunder_path(23, W, H)
    path = alt if f == 3 else main
    core = pad.zeros()
    glow = pad.zeros()
    if f == 0:
        vis = cut_polyline(path, 0.58)
        core = np.maximum(core, pad.stroke(vis, 1.5 * wk, 2.2 * wk, a0=0.5, a1=br))
        glow = np.maximum(glow, pad.stroke(vis, 6.0, 9.0, a0=0.4, a1=0.9, soft=3.0))
        tx, ty = vis[-1]
        core = np.maximum(core, star4(pad, tx, ty, 14, 1.0, thin=0.12, diag=0.0))
        glow = np.maximum(glow, pad.radial(tx, ty, 18, 1.4) * 0.9)
        # 앞서 내려가는 가는 갈래 (잠깐)
        rnd = random.Random(5)
        for i in range(3):
            j = int(len(vis) * rnd.uniform(0.55, 0.95))
            x, y = vis[j]
            pts = bolt_pts(rnd, (x, y), (x + rnd.uniform(-22, 22), y + rnd.uniform(14, 30)), 0.3, 3, 0.6)
            core = np.maximum(core, pad.stroke(pts, 1.0, 0.4, a0=0.7, a1=0.2))
            glow = np.maximum(glow, pad.stroke(pts, 3.5, a0=0.4, soft=1.5))
    else:
        core = np.maximum(core, pad.stroke(path, 2.6 * wk, 2.2 * wk, a0=br, a1=br))
        glow = np.maximum(glow, pad.stroke(path, 5.5 * wk + 1.5, a0=br, soft=2.5))
        glow = np.maximum(glow, pad.stroke(path, 18.0 * wk, a0=br * 0.2, soft=8.0))
        nb = {1: 4, 2: 8, 3: 8, 4: 5, 5: 3}[f]
        for brp, subs, frac in thunder_branches(11 if f != 3 else 23, path, W, H, 8)[:nb]:
            core = np.maximum(core, pad.stroke(brp, 1.8 * wk, 0.6, a0=br, a1=br * 0.55))
            glow = np.maximum(glow, pad.stroke(brp, 5.0 * wk, 2.0, a0=br * 0.8, a1=br * 0.3, soft=2.0))
            for sb in subs:
                core = np.maximum(core, pad.stroke(sb, 1.1 * wk, 0.4, a0=br * 0.9, a1=br * 0.3))
                glow = np.maximum(glow, pad.stroke(sb, 3.5 * wk, a0=br * 0.5, soft=1.5))
        # 땅에 닿은 번쩍 (위 반만 — 아래는 땅) · 땅을 따라 퍼지는 빛 · 튀는 불티
        gx, gy = path[-1]
        gk = [0, 1.0, 0.72, 0.55, 0.35, 0.18][f]
        R2 = np.sqrt(((X - gx) / 1.0) ** 2 + ((Y - gy) / 0.36) ** 2)
        core = np.maximum(core, smooth(22 * gk + 4, 3.0, R2) * gk)
        core = np.maximum(core, pad.disc_field(gx, gy - 3, 9 * gk + 2, soft=3.0) * gk)
        core = np.maximum(core, star4(pad, gx, gy - 5, 44 * gk + 8, gk, thin=0.06, diag=0.45))
        glow = np.maximum(glow, smooth(58 * gk + 10, 0.0, R2) * gk)
        glow = np.maximum(glow, pad.radial(gx, gy - 8, 40 * gk + 10, 1.1) * gk)
        if f in (1, 2, 3):
            rnd = random.Random(70)
            q = (f - 1) / 2
            for i in range(10):
                a = -math.pi / 2 + rnd.uniform(-1.35, 1.35)
                sp = rnd.uniform(14, 34)
                t = 0.6 + q * 1.4
                x = gx + math.cos(a) * sp * t
                y = gy + math.sin(a) * sp * t + 9 * t * t
                al = (1 - q * 0.8) * rnd.uniform(0.6, 1.0)
                vx, vy = math.cos(a) * sp, math.sin(a) * sp + 18 * t
                L = math.hypot(vx, vy) or 1
                core = np.maximum(core, streak(pad, (x - vx / L * 6, y - vy / L * 6), (x, y), 1.2, al))
        # 위의 구름 빛 (번쩍인 장)
        if f in (1, 2):
            tx = path[8][0]
            glow = np.maximum(glow, pad.radial(tx, 4, 46, 1.3) * (0.7 if f == 1 else 0.45))
    if f == 5:
        rnd = random.Random(9)
        for i in range(8):
            x = path[-1][0] + rnd.uniform(-26, 26)
            y = H - 10 - rnd.uniform(0, 22)
            core = np.maximum(core, pad.disc_field(x, y, rnd.uniform(0.7, 1.3)) * 0.7)
            glow = np.maximum(glow, pad.disc_field(x, y, 3.0, soft=1.5) * 0.4)
    # 위 끝은 하늘에서 흐리게 들어오고 아래 끝 (땅) 은 칸 끝에 닿지 않게
    vfade = smooth(0.0, 34.0, Y) * smooth(H - 0.5, H - 7.0, Y)
    core, glow = core * vfade, glow * vfade
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.42), (10.0, 0.26)), body_k=0.85, core_bloom=((1.5, 0.4), (4.0, 0.3)), win=3.5)


sprite("rfx_jthunder2", "lookat_y", 128, 512, 6, 6, job="paladin", zoom=1,
       desc="내리치는 벼락 (아래 끝이 땅): 0 앞서 내려가는 줄기 · 1 땅에 닿아 번쩍 (땅 빛 · 구름 빛 · 튀는 불티) · 2 ~ 3 가지 · 다른 길로 깜빡임 · 4 ~ 5 사그라짐")(thunder_frame)


# ------------------------------------------------------------ rfx_jvolt2 (땅에 눕는 번개 고리 — 고리 반지름 = 반 크기 × 0.8, 안쪽 무늬 없음)

VOLT_ENV = [0.55, 1.0, 1.0, 0.95, 0.9, 0.85, 0.6, 0.3]


def volt_loop(rnd, c, R, amp, n=60):
    ph1, ph2 = rnd.uniform(0, TAU), rnd.uniform(0, TAU)
    pts = []
    for i in range(n):
        a = i / n * TAU
        r = R + amp * (0.55 * math.sin(3 * a + ph1) + 0.45 * math.sin(7 * a + ph2)) + rnd.gauss(0, amp * 0.45)
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    pts.append(pts[0])
    out = [pts[0]]
    for a_, b_ in zip(pts, pts[1:]):
        out += bolt_pts(rnd, a_, b_, 0.28, 2, 0.6)[1:]
    return out


def volt_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    R = S * 0.4
    env = VOLT_ENV[f]
    rr, th = polar(pad, c, c)
    rnd = random.Random(300 + f)
    core = pad.zeros()
    glow = gauss(rr - R, 6.5) * 0.42 * env
    for k, (amp, w, a) in enumerate(((3.6, 1.7, 1.0), (4.6, 1.0, 0.75), (5.4, 0.8, 0.55))):
        pts = volt_loop(rnd, c, R, amp)
        core = np.maximum(core, pad.stroke(pts, w * (0.75 + 0.25 * env), a0=a * env))
        glow = np.maximum(glow, pad.stroke(pts, w * 4 + 2, a0=a * env * 0.7, soft=2.0))
    # 고리에서 튀는 짧은 번개 (안 · 밖) · 끝의 불꽃
    if f >= 1:
        for i in range(9):
            a0 = rnd.uniform(0, TAU)
            out_ = rnd.random() < 0.6
            x0, y0 = c + math.cos(a0) * R, c + math.sin(a0) * R
            L = rnd.uniform(12, 24) * (0.7 + 0.3 * env)
            ang = a0 + (0 if out_ else math.pi) + rnd.uniform(-0.6, 0.6)
            p1 = (x0 + math.cos(ang) * L, y0 + math.sin(ang) * L)
            pts = bolt_pts(rnd, (x0, y0), p1, 0.3, 3, 0.6)
            core = np.maximum(core, pad.stroke(pts, 1.5, 0.5, a0=env, a1=env * 0.6))
            glow = np.maximum(glow, pad.stroke(pts, 4.5, 1.5, a0=env * 0.7, a1=env * 0.25, soft=1.5))
            if rnd.random() < 0.5:
                core = np.maximum(core, star4(pad, p1[0], p1[1], rnd.uniform(3.5, 6.0), env, rot=rnd.uniform(0, 1), thin=0.16, diag=0.0))
        for i in range(6):
            a0 = rnd.uniform(0, TAU)
            x, y = c + math.cos(a0) * (R + rnd.uniform(-3, 3)), c + math.sin(a0) * (R + rnd.uniform(-3, 3))
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(5, 9) * env, env, rot=rnd.uniform(0, 1), thin=0.13, diag=0.35))
            glow = np.maximum(glow, pad.disc_field(x, y, 4.0, soft=2.0) * env * 0.8)
    return finish(pad, glow, core, halo=((1.3, 0.5), (3.2, 0.42), (7.0, 0.24)), body_k=0.85, core_bloom=((1.3, 0.4), (3.2, 0.3)), win=4.0)


sprite("rfx_jvolt2", "direction_z", 192, 192, 8, 4, job="paladin", zoom=2,
       desc="땅에 눕는 번개 고리 (반지름 0.4 S): 지직거리는 번개 줄 셋 (장마다 다름) · 안팎으로 튀는 짧은 번개 · 불꽃 · 옅은 띠 빛 — 나타나 지직거리다 사라짐")(volt_frame)


# ------------------------------------------------------------ rfx_jsoul2 (혼불 — 아래가 둥글고 위로 흔들리며 가늘어짐, 이어 돌림)

def flame_field(pad, cx, yb, ytip, r, ph, sway=3.0, wob=0.12, seed=0):
    """불꽃 모양 (0 ~ 1 덮임): 아래 원 (반지름 r · 바닥 yb) + 위로 가늘어지는 혀 (끝 ytip) — ph = 흔들림 위상 (라디안, 이어 돌림)"""
    X, Y = pad.grid()
    yc = yb - r
    t = np.clip((yc - Y) / max(1e-3, yc - ytip), 0, 1)  # 0 = 원 가운데 높이 → 1 = 끝
    xc = cx + sway * np.sin(ph + t * 2.6) * t ** 1.3 + 0.6 * sway * np.sin(2 * ph + t * 5.0 + seed) * t ** 2
    upper = r * (1 - t) ** 1.08 * (1 + wob * np.sin(t * 9 + 2 * ph + seed) * t)
    lower = np.sqrt(np.clip(r * r - (Y - yc) ** 2, 0, None))
    hw = np.where(Y > yc, lower, upper)
    dx = np.abs(X - np.where(Y > yc, cx, xc))
    inside = (hw - dx)
    return clamp01(inside * SS * 0.5 + 0.5) * (Y < yb + 1) * (Y > ytip - 1), t


def soul_frame(f, S=64):
    pad = Pad(S, S)
    c = S / 2
    ph = TAU * f / 6
    outer, t = flame_field(pad, c, 56.5, 7.0, 12.5, ph, sway=3.4, wob=0.14)
    tongues = pad.zeros()
    for sgn, off in ((-1, 0.0), (1, 2.3)):
        tg, _ = flame_field(pad, c + sgn * 7.0, 52.5, 16.0 + 3 * math.sin(ph + off), 5.8, ph + off, sway=2.6 * sgn, wob=0.2, seed=off)
        tongues = np.maximum(tongues, tg)
    body = np.maximum(outer, tongues * 0.85)
    inner, ti = flame_field(pad, c, 53.5, 22.0 + 2.0 * math.sin(ph * 2), 7.2, ph + 0.4, sway=2.2, wob=0.1, seed=1.0)
    X, Y = pad.grid()
    core = inner * (0.75 + 0.25 * clamp01((50 - Y) / 20))
    core = np.maximum(core, rim_of(body, 0.9) * 0.55 * (Y < 50))
    # 속의 소용돌이 줄 · 위로 오르는 빛 알갱이 (이어 돌림)
    swirl = [(c + 4.5 * math.cos(ph + k * 0.5) * (1 - k / 14), 50 - k * 2.2) for k in range(14)]
    core = np.maximum(core, pad.stroke(swirl, 0.9, 0.3, a0=0.55, a1=0.1) * body)
    rnd = random.Random(14)
    for i in range(4):
        q = (rnd.random() + f / 6) % 1.0
        x = c + rnd.uniform(-9, 9) + 2.5 * math.sin(q * 6 + i)
        y = 30 - 24 * q
        a = math.sin(math.pi * q) ** 1.2 * 0.9
        core = np.maximum(core, pad.disc_field(x, y, 1.0 + 0.4 * (1 - q)) * a)
        body = np.maximum(body, pad.disc_field(x, y, 2.2, soft=1.0) * a * 0.6)
    vfade = smooth(2.0, 9.0, Y)
    core1 = pad.down(core * vfade)
    g = pad.down(body * vfade)
    glow1 = screen(g * 0.9, gblur(g, 1.2) * 0.5, gblur(g, 3.0) * 0.4, gblur(core1, 1.0) * 0.3)
    wb = win_border(g.shape, 3.0)
    return clamp01(glow1) * wb, core1 * wb


sprite("rfx_jsoul2", "rotate_xyz", 64, 64, 6, 6, job="reaper", zoom=0,
       desc="혼불 (이어 돌림): 아래가 둥근 불꽃 · 흔들리는 곁 혀 둘 · 밝은 속불 · 속의 소용돌이 · 위로 오르는 빛 알갱이")(soul_frame)



# ------------------------------------------------------------ rfx_jreaper2 (사신 그림자 — 0 ~ 3 서서 흔들림 (이어 돌림), 4 ~ 7 낫을 크게 휘두름)

REAPER_SWING = [-60, -150, -215, -235]


def reaper_pose(f, W=192, H=256):
    if f < 4:
        ph = f * math.pi / 2
        return dict(dy=H * 0.012 * math.sin(ph), sway=W * 0.03 * math.sin(ph + 0.6), ph=ph, hand=(W * 0.7, H * 0.45 + H * 0.012 * math.sin(ph)),
                    ang=math.radians(-96), up=H * 0.4, down=H * 0.42, blade=W * 0.5, bw=W * 0.072, vis=1.0)
    g = f - 4
    return dict(dy=0.0, sway=W * 0.02 * (1 - g / 3), ph=g * 0.9 + 0.4, hand=(W * 0.6, H * 0.5), ang=math.radians(REAPER_SWING[g]), up=H * 0.3,
                down=H * 0.24, blade=W * 0.36, bw=W * 0.066, vis=1.0 if g < 3 else 0.6)


def robe_outline(cx, dy, sway, ph):
    def P(x, y):
        return (cx + x, y + dy)
    peak = P(5, 13)
    pts = bez(peak, P(37, 19), P(41, 63), 14)
    pts += bez(P(41, 63), P(45, 84), P(51, 98), 8)[1:]
    pts += bez(P(51, 98), P(58, 170), P(64 + sway * 0.4, 229), 14)[1:]
    n = 11
    for i in range(1, n):
        t = i / n
        x = 64 - 128 * t + sway * math.sin(t * 6.5 + ph)
        y = 229 + 3.5 * math.sin(t * 5 + ph * 0.7) + (11.0 + 2.5 * math.sin(ph + i * 1.7)) * (i % 2)
        pts.append(P(x, y))
    pts += bez(P(-64 + sway * 0.4, 229), P(-58, 170), P(-48, 100), 14)
    pts += bez(P(-48, 100), P(-42, 86), P(-36, 64), 8)[1:]
    pts += bez(P(-36, 64), P(-33, 21), peak, 14)[1:-1]
    return pts


def reaper_frame(f, W=192, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    q = reaper_pose(f, W, H)
    dy, sway, ph, vis = q["dy"], q["sway"], q["ph"], q["vis"]
    cx = W * 0.45
    st = Stack(pad)
    glow_extra = np.zeros((H, W), np.float32)
    hx, hy = q["hand"]
    # ---- 옷 (두건 · 긴 옷 · 찢어진 자락) — 반쯤 비치는 몸 · 위 왼쪽 빛
    robe = pad.poly(robe_outline(cx, dy, sway, ph))
    lit, dark, _ = bevel_light(robe, 12.0)
    hood_zone = smooth(100 + dy, 70 + dy, Y)
    body_v = (0.4 + 0.16 * lit - 0.12 * dark + 0.12 * hood_zone) * vis
    rc = rim_of(robe, 1.3) * (0.55 + 0.35 * hood_zone) + lit * 0.32
    # 주름 (어깨 · 가슴에서 자락으로)
    for k, (x0, x1, bend) in enumerate(((-26, -38, -6), (-10, -16, 4), (8, 6, -3), (24, 30, 5), (38, 50, -2))):
        pts = bez((cx + x0, 104 + dy), (cx + (x0 + x1) / 2 + bend + sway * 0.3, 168 + dy), (cx + x1 + sway * 0.5, 226 + dy), 12)
        rc = np.maximum(rc, pad.stroke(pts, 1.0, 0.5, a0=0.55, a1=0.25) * robe)
    # 자락 끝 (찢어진 가닥이 밝음)
    rc = np.maximum(rc, robe * smooth(212 + dy, 240 + dy, Y) * 0.45)
    st.over(robe, body_v, rc * robe * vis)
    # 두건 속 어둠 · 빛나는 눈
    fx, fy0, fy1, fhw = cx + 4.5, 30 + dy, 75 + dy, 15.5
    right = []
    for i in range(17):
        t = i / 16
        y = fy0 + (fy1 - fy0) * t
        hw_ = fhw * math.sin(math.pi * min(1.0, t ** 0.8)) ** 0.62
        right.append((fx + hw_, y))
    hole_pts = right + [(2 * fx - x, y) for x, y in right[::-1]]
    hole = pad.poly(hole_pts)
    st.body = st.body * (1 - hole * 0.97)
    st.core = st.core * (1 - hole)
    st.add_core(rim_of(pad.poly([(fx + (x - fx) * 1.14, fy0 - 3 + (y - fy0) * 1.06) for x, y in hole_pts]), 1.2) * (1 - hole) * 0.75 * vis)
    ey = fy0 + (fy1 - fy0) * 0.5
    eyes_c = pad.zeros()
    for sg in (-1, 1):
        ex = fx + sg * 6.8
        p0 = (ex + sg * 4.6, ey - 2.4)
        p1 = (ex - sg * 3.4, ey + 1.6)
        eyes_c = np.maximum(eyes_c, lens(pad, p0, p1, 3.0, 1.0, power=0.6))
    st.add_core(eyes_c)
    glow_extra = np.maximum(glow_extra, gblur(pad.down(eyes_c), 2.5) * 1.6)
    # ---- 휘두른 자국 (낫 날 끝이 지난 길 — 몸 앞, 낫 뒤)
    if f in (5, 6, 7):
        g = f - 4
        a_prev, a_cur = math.radians(REAPER_SWING[g - 1]), math.radians(REAPER_SWING[g])
        ux, uy = math.cos(a_cur), math.sin(a_cur)
        tipx = hx + ux * q["up"] + uy * q["blade"] - ux * q["blade"] * 0.55
        tipy = hy + uy * q["up"] - ux * q["blade"] - uy * q["blade"] * 0.55
        head = math.atan2(tipy - hy, tipx - hx)
        length = (a_prev - a_cur) + 0.5
        rr, th = polar(pad, hx, hy)
        Rt = 82.0
        b = behind(th, head, -1.0)
        sdist = np.clip(1 - b / length, 0, 1)
        on = b < length
        k = 1.0 if f < 7 else 0.35
        prof = crescent_prof(sdist, tip=0.08, rise=1.3) * on
        w = 24.0 * prof * (1.0 if f < 7 else 0.6)
        d = Rt - rr
        outer = clamp01((d + 0.15) * SS + 0.5)
        band = outer * smooth(w + 1.2, w - 3.0, d) * (prof > 0.004)
        t = np.clip(d / np.maximum(w, 0.4), 0, 1)
        tc = np.maximum(outer * np.exp(-np.maximum(d, 0) / (0.8 + 2.0 * prof)) * band, band * (1 - smooth(0.3, 1.0, t)) ** 1.2)
        for dk, s0, a in ((0.6, 0.15, 0.7), (0.88, 0.35, 0.45)):
            tc = np.maximum(tc, gauss(d - dk * w, 0.5) * swin(sdist, s0, 0.92, 0.2, 0.06) * a * on)
        st.add_core(tc * k)
        st.body = np.maximum(st.body, smooth(-3.0, 0.5, d) * smooth(w * 1.3 + 3, w * 1.3 - 3, d) * (prof > 0.004) * k)
    # ---- 낫 (자루 · 손잡이 마디 · 날 · 날 뿌리 깃) — 손이 쥠
    ang, up, down, blade, bw = q["ang"], q["up"], q["down"], q["blade"], q["bw"]
    ux, uy = math.cos(ang), math.sin(ang)
    px_, py_ = uy, -ux  # 날이 뻗는 쪽
    top = (hx + ux * up, hy + uy * up)
    bot = (hx - ux * down, hy - uy * down)
    sw = 2.3
    shaft = pad.poly([(bot[0] + px_ * sw, bot[1] + py_ * sw), (top[0] + px_ * sw, top[1] + py_ * sw), (top[0] - px_ * sw, top[1] - py_ * sw),
                      (bot[0] - px_ * sw, bot[1] - py_ * sw)])
    sc = rim_of(shaft, 0.6) * 0.6 + pad.stroke([(bot[0] - px_ * 0.8, bot[1] - py_ * 0.8), (top[0] - px_ * 0.8, top[1] - py_ * 0.8)], 0.7, a0=0.55) * shaft
    st.over(shaft, 0.82 * vis, sc * shaft * vis)
    spike = pad.poly([(bot[0] + px_ * 3.0, bot[1] + py_ * 3.0), (bot[0] - ux * 11, bot[1] - uy * 11), (bot[0] - px_ * 3.0, bot[1] - py_ * 3.0)])
    st.over(spike, 0.9 * vis, (rim_of(spike, 0.6) * 0.9 + spike * 0.3) * spike * vis)
    for k_ in (0.42, 0.62):
        nx_, ny_ = bot[0] + (top[0] - bot[0]) * k_, bot[1] + (top[1] - bot[1]) * k_
        ring = pad.poly([(nx_ + px_ * 3.6 - ux * 2, ny_ + py_ * 3.6 - uy * 2), (nx_ + px_ * 3.6 + ux * 2, ny_ + py_ * 3.6 + uy * 2),
                         (nx_ - px_ * 3.6 + ux * 2, ny_ - py_ * 3.6 + uy * 2), (nx_ - px_ * 3.6 - ux * 2, ny_ - py_ * 3.6 - uy * 2)])
        st.over(ring, 0.88 * vis, rim_of(ring, 0.6) * 0.9 * ring * vis)
    # 날: 등 (바깥 줄) · 날 (안쪽 줄 — 손 쪽으로 오목), 끝이 뾰족
    outer_e, inner_e = [], []
    n = 30
    for i in range(n + 1):
        t = i / n
        bend = t * t * 0.55
        x = top[0] + px_ * blade * t - ux * blade * bend
        y = top[1] + py_ * blade * t - uy * blade * bend
        ww = bw * (1 - t) ** 0.72 + 0.3
        outer_e.append((x, y))
        inner_e.append((x - ux * ww, y - uy * ww))
    bl = pad.poly(outer_e + inner_e[::-1] + [(top[0] - ux * bw * 1.1, top[1] - uy * bw * 1.1)])
    dcut, _ = pad.line_field(inner_e, 10)
    blc = np.maximum(bl * np.exp(-dcut / 3.2), bl * 0.38)
    blc = np.maximum(blc, pad.stroke(inner_e[1:], 1.1, 0.4, a0=1.0, a1=0.85) * bl)
    blc = np.maximum(blc, pad.stroke(outer_e[1:-2], 0.8, 0.3, a0=0.7, a1=0.3) * bl)
    groove = [(x0 + (x1 - x0) * 0.45, y0 + (y1 - y0) * 0.45) for (x0, y0), (x1, y1) in zip(outer_e[2:22], inner_e[2:22])]
    blc = np.maximum(blc, pad.stroke(groove, 0.7, 0.3, a0=0.55, a1=0.2) * bl)
    st.over(bl, 0.88 * vis, blc * bl * vis)
    collar = pad.poly([(top[0] + px_ * 4.2 - ux * 1.0, top[1] + py_ * 4.2 - uy * 1.0), (top[0] + px_ * 4.2 - ux * 9, top[1] + py_ * 4.2 - uy * 9),
                       (top[0] - px_ * 4.2 - ux * 9, top[1] - py_ * 4.2 - uy * 9), (top[0] - px_ * 4.2 - ux * 1.0, top[1] - py_ * 4.2 - uy * 1.0)])
    lit_c, _, _ = bevel_light(collar, 1.5)
    st.over(collar, 0.9 * vis, (rim_of(collar, 0.6) * 0.9 + lit_c * 0.6) * collar * vis)
    tipx, tipy = outer_e[-1]
    if f == 2:
        st.add_core(star4(pad, tipx, tipy, 13, 1.0, rot=0.3, thin=0.12, diag=0.4))
    # ---- 소매 · 뼈 손 (자루를 쥠)
    if f < 4:
        sl = bez((cx + 30, 97 + dy), (cx + 52, 98 + dy), (hx - 3, hy - 9), 10) + [(hx + 4, hy + 8)] + bez((hx + 4, hy + 8), (hx - 10, hy + 30), (cx + 24, 134 + dy), 10)
    else:
        sl = bez((cx + 26, 98), (cx + 40, 100), (hx - 6, hy - 8), 10) + [(hx + 6, hy + 6)] + bez((hx + 6, hy + 6), (hx - 4, hy + 24), (cx + 18, 132), 10)
    sleeve = pad.poly(sl)
    sl_lit, sl_dark, _ = bevel_light(sleeve, 5.0)
    st.over(sleeve, (0.46 + 0.16 * sl_lit - 0.1 * sl_dark) * vis, (rim_of(sleeve, 1.0) * 0.6 + sl_lit * 0.3) * sleeve * vis)
    for k_, off in enumerate((-4.2, 0.0, 4.2)):
        bx, by = hx + ux * off, hy + uy * off
        bone = pad.poly([(bx + px_ * 5.2 - ux * 1.5, by + py_ * 5.2 - uy * 1.5), (bx + px_ * 5.2 + ux * 1.5, by + py_ * 5.2 + uy * 1.5),
                         (bx - px_ * 4.0 + ux * 1.5, by - py_ * 4.0 + uy * 1.5), (bx - px_ * 4.0 - ux * 1.5, by - py_ * 4.0 - uy * 1.5)])
        st.over(bone, 0.9 * vis, (rim_of(bone, 0.55) * 0.9 + bone * 0.35) * bone * vis)
    # ---- 자락에서 오르는 그림자 연기 (빛 장면 — 이어 돌림)
    rnd = random.Random(61)
    for i in range(6):
        qq = (rnd.random() + (f % 4) / 4) % 1.0
        x = cx + rnd.uniform(-52, 52) + 6 * math.sin(qq * 5 + i)
        y = 236 - 46 * qq
        a = math.sin(math.pi * qq) * 0.36 * vis
        rad = 7 + 6 * qq
        glow_extra = np.maximum(glow_extra, pad.down(pad.disc_field(x, y, rad, soft=rad * 0.9)) * a)
    bfade = smooth(H - 0.5, H - 8.0, Y)
    core1 = pad.down(st.core * bfade)
    glow1 = obj_glow(pad, st.body * bfade, core1, halo=((1.5, 0.42), (4.0, 0.3), (9.0, 0.18)), body_k=1.0, core_bloom=((1.3, 0.3), (3.5, 0.24)))
    glow1 = screen(glow1, glow_extra * pad.down(bfade))
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jreaper2", "lookat_y", 192, 256, 8, 4, job="reaper", zoom=5,
       desc="사신 그림자: 뾰족 두건 (속 어둠 · 빛나는 눈) · 반쯤 비치는 긴 옷 (주름 · 찢어진 자락) · 소매 · 뼈 손 · 큰 낫 (갈아낸 날 · 등줄 · 홈 · 깃 · 자루 마디) · 오르는 그림자 연기 — 0 ~ 3 흔들림, 4 ~ 7 휘두름 (날 끝 자국)")(reaper_frame)


# ------------------------------------------------------------ rfx_jwind2 (땅에 눕는 바람 고리 — 나선 바람 셋이 돌며 퍼지고 사라짐, 바깥 반지름 ≈ 반 크기)

def wind_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    p = f / 7
    base = math.radians(f * 24)
    grow = 0.86 + 0.2 * ease_out(p)
    v = (1 - p ** 1.6) * 0.8 + 0.2
    core = pad.zeros()
    glow = pad.zeros()
    for s_ in range(3):
        a0 = base + s_ * TAU / 3
        for k, (dr, ext, wk, al) in enumerate(((0.0, 1.0, 1.0, 1.0), (7.0, 0.72, 0.42, 0.6), (-6.5, 0.6, 0.35, 0.5), (13.0, 0.45, 0.25, 0.35))):
            pts = []
            n = 40
            t0 = (1 - ext) * 0.6
            for i in range(n + 1):
                t = (t0 + ext * i / n) if k else i / n
                a = a0 + t * math.radians(155)
                r = S * (0.19 + 0.235 * t) * grow + dr * (0.4 + 0.6 * t)
                pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
            wmax = 7.5 * wk * (0.75 + 0.25 * (1 - p))

            def wfn(tt, wmax=wmax):
                return wmax * math.sin(math.pi * min(1.0, tt * 1.04)) ** 0.9 * (0.5 + 0.5 * tt)
            poly = pad.poly(taper_poly(pts, lambda tt, wfn=wfn: wfn(tt) / 2))
            dd, sfr = pad.line_field(pts, wmax + 4)
            spine = gauss(dd, 0.5 + 0.35 * wk) * np.clip(np.sin(np.pi * np.clip(sfr, 0, 1)), 0, 1) ** 0.7
            core = np.maximum(core, np.maximum(poly * (0.72 if k == 0 else 0.38), spine * al) * v)
            glow = np.maximum(glow, pad.poly(taper_poly(pts, lambda tt, wfn=wfn: wfn(tt) / 2 * 2.2 + 1.5 * math.sin(math.pi * min(1.0, tt)) ** 0.6)) * al * v * 0.85)
    # 바람에 실린 알갱이
    rnd = random.Random(19)
    for i in range(14):
        t = rnd.uniform(0.15, 0.95)
        a = base + rnd.randrange(3) * TAU / 3 + t * math.radians(155) + rnd.uniform(-0.15, 0.15)
        r = S * (0.19 + 0.235 * t) * grow + rnd.uniform(-8, 10)
        x, y = c + math.cos(a) * r, c + math.sin(a) * r
        tx, ty = -math.sin(a), math.cos(a)
        al = v * rnd.uniform(0.5, 1.0)
        core = np.maximum(core, pad.stroke([(x - tx * 4, y - ty * 4), (x, y)], 0.3, 1.3, a0=0.0, a1=al))
        glow = np.maximum(glow, pad.disc_field(x, y, 2.2, soft=1.0) * al * 0.6)
    return finish(pad, glow, core, halo=((1.3, 0.48), (3.2, 0.38), (7.0, 0.2)), body_k=0.8, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jwind2", "direction_z", 192, 192, 8, 4, job="archer", zoom=2,
       desc="땅에 눕는 바람 고리: 나선 바람 셋 (굵은 줄기 + 곁 흐름 셋) 이 돌며 퍼지고 흐려짐 · 바람에 실린 알갱이")(wind_frame)


# ------------------------------------------------------------ rfx_jtarget2 (과녁 — 바깥 고리 반지름 = 반 크기 × 0.84, 0 ~ 3 조여 들어와 맞춤 · 3 ~ 5 맞춘 채 깜빡임 (이어 돌림))

TGT_K = [1.1, 1.06, 1.025, 1.0, 1.0, 1.0]


def target_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    k = TGT_K[f]
    lock = f >= 3
    pulse = [0.62, 0.8, 0.92, 1.0, 0.78, 0.9][f]
    core = pad.zeros()
    glow = pad.zeros()
    ro = 80.6 * k
    # 바깥 고리 (두 줄) · 눈금 (15° 마다 · 굵은 넷)
    core = np.maximum(core, gauss(rr - ro, 0.9) * pulse)
    core = np.maximum(core, gauss(rr - (ro - 5.0), 0.5) * 0.55 * pulse)
    glow = np.maximum(glow, gauss(rr - ro, 3.2) * 0.75 * pulse)
    tick = (0.5 + 0.5 * np.cos(th * 24)) ** 40
    core = np.maximum(core, tick * smooth(ro - 11, ro - 8, rr) * smooth(ro - 4.5, ro - 6, rr) * 0.8 * pulse)
    rot = [0.5, 0.3, 0.12, 0.0, 0.0, 0.0][f]
    for i in range(4):
        a = i * math.pi / 2 + rot
        r0, r1 = ro - 14, min(ro + 7, 89.0)
        p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
        p1 = (c + math.cos(a) * r1, c + math.sin(a) * r1)
        core = np.maximum(core, lens(pad, p0, p1, 4.2, pulse, power=0.5, skew=0.35))
        glow = np.maximum(glow, lens(pad, p0, p1, 9.0, pulse * 0.8, power=0.6, skew=0.35))
    # 가운데 고리 (돌아가는 토막 여덟)
    rm = 53.8 * (1 + (k - 1) * 0.6)
    seg_rot = [0.9, 0.55, 0.25, 0.0, 0.0, 0.0][f] + (0.0 if not lock else 0.04 * (f - 3))
    seg = smooth(0.25, 0.45, np.cos((th - seg_rot) * 8))
    core = np.maximum(core, gauss(rr - rm, 1.1) * seg * 0.9 * pulse)
    glow = np.maximum(glow, gauss(rr - rm, 3.0) * (0.35 + 0.4 * seg) * pulse)
    # 안쪽 고리 · 안을 가리키는 세모 넷 · 가운데 십자 · 점
    ri = 26.9
    core = np.maximum(core, gauss(rr - ri, 0.75) * pulse)
    glow = np.maximum(glow, gauss(rr - ri, 2.6) * 0.6 * pulse)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        x0, y0 = c + math.cos(a) * (ri + 9), c + math.sin(a) * (ri + 9)
        tri = [(c + math.cos(a) * (ri + 3), c + math.sin(a) * (ri + 3)), (x0 + math.cos(a + math.pi / 2) * 3.6, y0 + math.sin(a + math.pi / 2) * 3.6),
               (x0 - math.cos(a + math.pi / 2) * 3.6, y0 - math.sin(a + math.pi / 2) * 3.6)]
        core = np.maximum(core, pad.poly(tri, 0.9 * pulse))
    for a in (0, math.pi / 2, math.pi, 1.5 * math.pi):
        p0 = (c + math.cos(a) * 6, c + math.sin(a) * 6)
        p1 = (c + math.cos(a) * 16, c + math.sin(a) * 16)
        core = np.maximum(core, pad.stroke([p0, p1], 1.3, 0.5, a0=0.9 * pulse, a1=0.5 * pulse))
    core = np.maximum(core, pad.disc_field(c, c, 2.6))
    glow = np.maximum(glow, pad.disc_field(c, c, 6.0, soft=3.0))
    # 모서리 꺾쇠 넷 (밖에서 조여 들어옴)
    rb = [88.0, 80.0, 72.0, 66.0, 66.0, 66.0][f]
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        bx, by = c + math.cos(a) * rb, c + math.sin(a) * rb
        ax_, ay_ = math.cos(a + math.pi * 0.75), math.sin(a + math.pi * 0.75)
        cx_, cy_ = math.cos(a - math.pi * 0.75), math.sin(a - math.pi * 0.75)
        L = 13.0
        pts = [(bx + ax_ * L, by + ay_ * L), (bx, by), (bx + cx_ * L, by + cy_ * L)]
        core = np.maximum(core, pad.stroke(pts, 2.0, a0=pulse))
        glow = np.maximum(glow, pad.stroke(pts, 6.0, a0=pulse * 0.7, soft=2.0))
    # 맞춘 장: 안쪽에서 퍼지는 고리 (이어 돌림 3 장)
    if lock:
        qq = (f - 3) / 3
        rp = ri + (ro - ri) * qq
        core = np.maximum(core, gauss(rr - rp, 0.8) * (1 - qq) * 0.7)
        glow = np.maximum(glow, gauss(rr - rp, 3.0) * (1 - qq) * 0.5)
    if f == 3:
        core = np.maximum(core, star4(pad, c, c, 22, 0.9, rot=math.pi / 4, thin=0.1, diag=0.0))
    return finish(pad, glow, core, halo=((1.2, 0.45), (3.0, 0.35), (6.5, 0.2)), body_k=0.85, core_bloom=((1.2, 0.3), (3.0, 0.22)), win=4.0)


sprite("rfx_jtarget2", "direction_z", 192, 192, 6, 6, job="archer", zoom=3,
       desc="과녁: 두 줄 바깥 고리 (눈금 · 굵은 표시 넷) · 도는 토막 고리 · 안쪽 고리 · 안을 가리키는 세모 · 가운데 십자 · 조여 드는 꺾쇠 — 0 ~ 3 조임, 3 ~ 5 맞춘 채 깜빡임 (퍼지는 고리)")(target_frame)


# ------------------------------------------------------------ rfx_jdash2 (돌진 줄기 — 오른쪽이 앞, 굵기 = 길이의 1/4)

def dash_frame(f, W=256, H=64):
    pad = Pad(W, H)
    rnd = random.Random(5)
    lines = [(rnd.uniform(0.14, 0.86), rnd.uniform(0.45, 0.92), rnd.uniform(0.0, 0.12), rnd.uniform(0.55, 1.0)) for _ in range(12)]
    lines.append((0.5, 0.97, 0.0, 1.0))
    grow = ease_out(min(1.0, (f + 1) / 2.5))
    shrink = 0.0 if f < 2 else (f - 1) / 5
    core = pad.zeros()
    glow = pad.zeros()
    front0 = W - 9.0
    for k, (yy, length, off, bright) in enumerate(lines):
        main = k == len(lines) - 1
        x_front = front0 - off * W - W * length * shrink * 0.9
        x_back = front0 - off * W - W * length * grow
        if x_front - x_back < 3:
            continue
        y = H * yy + (0 if main else 0.0)
        th = (3.4 if main else 2.2) * (0.6 + 0.4 * bright) * (1 - 0.55 * shrink)
        a = bright * (1 - 0.6 * shrink)
        n = 24
        pts = [(x_back + (x_front - x_back) * i / n, y) for i in range(n + 1)]

        def wfn(t, th=th):
            return th * (t ** 1.25) * math.sqrt(min(1.0, (1 - t) / 0.07 + 1e-6))
        core = np.maximum(core, pad.poly(taper_poly(pts, lambda t, wfn=wfn: wfn(t) * 0.55), a))
        core = np.maximum(core, pad.stroke(pts, 0.4, 1.1 if main else 0.8, a0=0.0, a1=a))
        glow = np.maximum(glow, pad.poly(taper_poly(pts, lambda t, wfn=wfn: wfn(t) * 1.5 + 1.0 * t), a * 0.9))
        if main and f <= 3:
            core = np.maximum(core, star4(pad, x_front - 3, y, 9 * (1 - 0.3 * f), a, rot=0.0, thin=0.12, diag=0.0))
            glow = np.maximum(glow, pad.radial(x_front - 3, y, 12, 1.4) * a)
    # 앞의 공기 물결 (꺾쇠 꼴 호 — 처음 셋)
    if f <= 2:
        cy = H / 2
        for j, (dx, hh, al) in enumerate(((-6, 20, 0.7), (-20, 25, 0.45), (-36, 27, 0.3))):
            x0 = front0 - 4 + dx
            pts = bez((x0 - 16, cy - hh), (x0 + 6, cy), (x0 - 16, cy + hh), 20)
            ak = al * [0.75, 1.0, 0.55][f]
            core = np.maximum(core, pad.stroke(pts, 1.0, a0=ak, taper=0.8))
            glow = np.maximum(glow, pad.stroke(pts, 3.5, a0=ak * 0.6, taper=0.8, soft=1.5))
    # 뒤로 흩어지는 알갱이
    rnd = random.Random(15 + f)
    for i in range(8):
        x = rnd.uniform(30, 230) - 20 * shrink
        y = rnd.uniform(10, 54)
        al = rnd.uniform(0.4, 0.9) * (1 - 0.5 * shrink)
        core = np.maximum(core, pad.stroke([(x - 7, y), (x, y)], 0.3, 1.0, a0=0.0, a1=al))
    return finish(pad, glow, core, halo=((1.1, 0.5), (2.8, 0.38), (6.0, 0.2)), body_k=0.85, core_bloom=((1.1, 0.35), (2.8, 0.26)), win=3.5)


sprite("rfx_jdash2", "lookat_direction", 256, 64, 6, 4, job="swordsman", zoom=1,
       desc="돌진 줄기 (오른쪽이 앞): 앞이 뾰족하고 밝은 빠른 줄 열셋 (가운데 굵은 줄 · 머리 반짝임) 이 뒤로 뻗었다 앞부터 줄어듦 · 앞의 공기 물결 · 알갱이")(dash_frame)


# ------------------------------------------------------------ rfx_jline2 (일섬 — 가는 선이 한 번에 그어지고 가운데가 부풀었다 가늘어지며 사라짐)

def line_frame(f, W=256, H=32):
    pad = Pad(W, H)
    cy = H / 2
    th = [1.3, 5.2, 8.6, 6.4, 3.6, 1.7][f]
    br = [1.0, 1.0, 1.0, 1.0, 0.8, 0.5][f]
    core = pad.zeros()
    glow = pad.zeros()
    x0, x1 = 6.0, W - 6.0
    split = [0.0, 0.0, 0.0, 0.0, 2.2, 3.8][f]
    for sg in ((0.0,) if split == 0 else (-1.0, 1.0)):
        yy = cy + sg * split
        k = 1.0 if split == 0 else 0.7
        core = np.maximum(core, lens(pad, (x0, yy), (x1, yy), th * 0.9 * k, br, power=0.5))
        core = np.maximum(core, lens(pad, (x0 + 8, yy), (x1 - 8, yy), max(1.0, th * 0.25), br, power=0.4))
        glow = np.maximum(glow, lens(pad, (x0 - 2, yy), (x1 + 2, yy), th * 2.2 + 3.0, br * 0.95, power=0.6))
    if f == 0:
        core = np.maximum(core, star4(pad, x1 - 6, cy, 9, 1.0, thin=0.12, diag=0.0))
    if 1 <= f <= 3:
        core = np.maximum(core, star4(pad, W / 2, cy, [0, 13, 15, 10][f], 1.0, thin=0.1, diag=0.0))
    if f >= 2:
        rnd = random.Random(f * 7)
        for i in range(7):
            x = rnd.uniform(W * 0.12, W * 0.88)
            y = cy + rnd.uniform(-0.8, 0.8) * th
            al = br * rnd.uniform(0.6, 1.0)
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(3.5, 5.5), al, rot=0.0, thin=0.16, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(x, y, 2.2, soft=1.0) * al * 0.6)
    return finish(pad, glow, core, halo=((1.0, 0.5), (2.5, 0.38), (5.0, 0.2)), body_k=0.85, core_bloom=((1.0, 0.35), (2.5, 0.28)), win=3.0)


sprite("rfx_jline2", "lookat_direction", 256, 32, 6, 2, job="swordsman", zoom=2,
       desc="일섬: 머리 반짝임과 함께 한 번에 그어지는 가는 선 → 가운데가 부푼 렌즈 (흰 속줄 · 가운데 번쩍 · 반짝임) → 두 줄로 갈라지며 가늘어져 사라짐")(line_frame)


# ------------------------------------------------------------ rfx_jspeed2 (빠른 줄 — 1인칭 화면 가장자리, 가운데는 비움, 이어 돌림)

def speed_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    rnd = random.Random(100 + f)
    core = pad.zeros()
    glow = pad.zeros()
    for i in range(44):
        ang = rnd.uniform(0, TAU)
        r0 = c * rnd.uniform(0.5, 0.8)
        r1 = c * rnd.uniform(0.95, 0.985)
        w = rnd.uniform(0.9, 2.7)
        a = rnd.uniform(0.6, 1.0)
        ux, uy = math.cos(ang), math.sin(ang)
        n = 12
        pts = [(c + ux * (r0 + (r1 - r0) * t / n), c + uy * (r0 + (r1 - r0) * t / n)) for t in range(n + 1)]

        def afn(sv, a=a):
            return a * np.clip(sv / 0.3, 0, 1) ** 0.7 * np.clip((1 - sv) / 0.13, 0, 1)
        core = np.maximum(core, pad.stroke(pts, 0.1, w, afn=afn))
        glow = np.maximum(glow, pad.stroke(pts, 0.3, w * 3.0, afn=lambda sv, afn=afn: afn(sv) * 0.6, soft=1.2))
    return finish(pad, glow, core, halo=((1.0, 0.45), (2.5, 0.3)), body_k=0.8, core_bloom=((1.0, 0.3),), win=5.0)


sprite("rfx_jspeed2", "rotate_xyz", 256, 256, 4, 4, job="swordsman", zoom=0,
       desc="빠른 줄 (1인칭 화면 가장자리 · 이어 돌림): 안쪽 끝이 뾰족한 줄 마흔 개 (장마다 다름) — 바깥 끝은 칸 끝 앞에서 흐려짐, 가운데는 비움")(speed_frame)



# ------------------------------------------------------------ rfx_jtwister2 (솟는 회오리 — 아래가 좁고 위가 넓은 깔때기, 이어 돌림)

def twister_frame(f, W=128, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    top, bottom = H * 0.06, H * 0.95
    ph = f / 8

    def radius(u):
        return W * (0.08 + 0.34 * u ** 0.85)

    def sway(u):
        return math.sin(u * TAU * 0.9 + 0.6) * W * 0.05

    core = pad.zeros()
    glow = pad.zeros()
    # ---- 깔때기 몸 (가장자리가 밝은 옅은 채움)
    uu = np.clip((bottom - Y) / (bottom - top), 0, 1)
    rad_u = W * (0.08 + 0.34 * uu ** 0.85)
    sw_u = np.sin(uu * TAU * 0.9 + 0.6) * W * 0.05
    xn = (X - cx - sw_u) / np.maximum(rad_u * 0.95, 1.0)
    inside = clamp01((1 - np.abs(xn)) * rad_u * SS * 0.25)
    vfade = smooth(bottom + 2, bottom - 14, Y) * smooth(top - 2, top + 26, Y)
    limb = np.clip(np.abs(xn), 0, 1) ** 3
    glow = np.maximum(glow, inside * (0.16 + 0.32 * limb) * vfade)
    # 깔때기 속 결 (위로 흐르는 비스듬한 줄 — 이어 돌림)
    streak_f = 0.5 + 0.5 * np.cos(TAU * (xn * 2.0 + uu * 6.0 - 2 * ph))
    core = np.maximum(core, inside * streak_f ** 12 * 0.13 * vfade * (1 - limb))
    # ---- 기운 고리 (위로 흐름): 앞 반은 밝고 머리가 굵음 · 뒤 반은 흐림
    n = 9
    for k in range(n):
        u = ((k + ph) / n) % 1.0
        y = bottom - u * (bottom - top)
        rr = radius(u)
        v = math.sin(math.pi * min(1.0, u * 1.03)) ** 0.7
        wd = 1.0 + 2.2 * u
        xc = cx + sway(u)
        back = arc_pts(xc, y, rr, math.pi * 1.04, math.pi * 1.96, 28, ry=rr * 0.26)
        front = arc_pts(xc, y, rr, -math.pi * 0.04, math.pi * 1.0, 40, ry=rr * 0.26)
        core = np.maximum(core, pad.stroke(back, 0.5 * wd, a0=0.3 * v))
        glow = np.maximum(glow, pad.stroke(back, 2.0 * wd, a0=0.25 * v, soft=1.0))
        core = np.maximum(core, pad.stroke(front, 0.35 * wd, 1.0 * wd, a0=0.35 * v, a1=0.95 * v))
        glow = np.maximum(glow, pad.stroke(front, 1.2 * wd, 3.6 * wd, a0=0.25 * v, a1=0.8 * v, soft=1.5))
    # ---- 나선 줄기 둘 (돌며 오름)
    for j in range(2):
        pts_f, pts_b = [], []
        for i in range(81):
            t = i / 80
            y = bottom - t * (bottom - top)
            a = t * TAU * 1.5 + ph * TAU + j * math.pi
            x = cx + sway(t) + math.cos(a) * radius(t)
            yy = y + math.sin(a) * radius(t) * 0.26
            (pts_f if math.sin(a) > 0 else pts_b).append((x, yy, t))
        for pts, front in ((pts_f, True), (pts_b, False)):
            if len(pts) < 2:
                continue
            for seg in _runs(pts):
                if len(seg) < 2:
                    continue
                xy = [(x, y) for x, y, _ in seg]
                tm = seg[len(seg) // 2][2]
                v = math.sin(math.pi * min(1.0, tm * 1.03)) ** 0.8
                core = np.maximum(core, pad.stroke(xy, 0.9 if front else 0.5, a0=(0.75 if front else 0.28) * v))
                glow = np.maximum(glow, pad.stroke(xy, 3.0 if front else 1.6, a0=(0.5 if front else 0.2) * v, soft=1.0))
    # ---- 돌며 오르는 알갱이 · 발밑 먼지
    rnd = random.Random(27)
    for i in range(12):
        u = (rnd.random() + ph) % 1.0
        a = rnd.uniform(0, TAU) + u * TAU * 2.0 + ph * TAU
        x = cx + sway(u) + math.cos(a) * radius(u) * rnd.uniform(0.85, 1.15)
        y = bottom - u * (bottom - top) + math.sin(a) * radius(u) * 0.26
        al = math.sin(math.pi * u) * (0.9 if math.sin(a) > 0 else 0.4)
        core = np.maximum(core, pad.disc_field(x, y, 1.1) * al)
        glow = np.maximum(glow, pad.disc_field(x, y, 2.6, soft=1.2) * al * 0.6)
    Xg, Yg = pad.grid()
    gd = clamp01(1 - ((Xg - cx - sway(0)) / 36.0) ** 2 - ((Yg - bottom + 2) / 6.5) ** 2) ** 1.5
    glow = np.maximum(glow, gd * 0.35)
    core = np.maximum(core, gd ** 2 * 0.12)
    core, glow = core * vfade, glow * smooth(H - 1, H - 9, Y) * smooth(1, 14, Y)
    return finish(pad, glow, core, halo=((1.2, 0.45), (3.0, 0.32), (7.0, 0.18)), body_k=0.9, core_bloom=((1.2, 0.3), (3.0, 0.22)), win=3.5)


def _runs(pts):
    """(x, y, t) 점들을 t 가 이어지는 토막들로 (앞 · 뒤로 나눈 나선의 끊긴 자리에서 자름)"""
    out, cur = [], [pts[0]]
    for a, b in zip(pts, pts[1:]):
        if b[2] - a[2] > 1.5 / 80:
            out.append(cur)
            cur = [b]
        else:
            cur.append(b)
    out.append(cur)
    return out


sprite("rfx_jtwister2", "lookat_y", 128, 256, 8, 8, job="warrior", zoom=0,
       desc="솟는 회오리 (이어 돌림): 가장자리가 밝은 깔때기 · 위로 흐르는 결 · 앞이 밝은 기운 고리 아홉 · 앞뒤가 다른 나선 줄기 둘 · 돌며 오르는 알갱이 · 발밑 먼지")(twister_frame)


# ------------------------------------------------------------ rfx_jcomet2 (불꽃 꼬리 — 오른쪽이 앞, 머리 = 0.82 W, 이어 돌림: 4 장에 결 한 주기)

def comet_frame(f, W=256, H=96):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    hx = W * 0.82
    tail_end = 14.0
    ph = TAU * f / 4
    scroll = 16.0 * f
    t = np.clip((hx - X) / (hx - tail_end), 0, 1)
    ahead = X > hx
    yc = cy + 4.0 * np.sin(t * 7.0 - ph) * t
    hw = 22.0 * (1 - t) ** 0.8 + 1.0
    n1 = fbm((X + scroll) / 32.0, Y / 7.0, seed=31, octaves=3, px=2)
    n2 = fbm((X + scroll * 0.5) / 16.0, Y / 4.0 + 7.0, seed=32, octaves=2, px=2)  # 주기 32 점 · 한 장에 8 점
    dy = np.abs(Y - yc) / hw
    edge = 1 + 0.38 * n1 * (0.35 + t)
    body = clamp01((edge - dy) / 0.3) * (~ahead) * (1 - t) ** 0.3 * smooth(1.0, 0.8, t)
    flame = clamp01(body * (0.8 + 0.35 * n2))
    hot = clamp01((0.5 + 0.22 * n1 + 0.12 * n2 - dy) / 0.22) * (~ahead) * (1 - t) ** 1.1
    licks = clamp01(0.5 + 0.9 * n2) ** 2 * clamp01((0.95 - dy) / 0.3) * (~ahead) * (1 - t) ** 1.6
    core = np.maximum(hot * 0.95, licks * 0.5)
    glow = flame
    # 갈라지는 혀 둘 (위 · 아래)
    for sg in (-1, 1):
        pts = []
        for i in range(25):
            tt = i / 24
            x = hx - 10 - tt * W * 0.42
            y = cy + sg * (5 + 12 * tt) + 3.5 * math.sin(tt * 6 + ph + sg)
            pts.append((x, y))
        glow = np.maximum(glow, pad.stroke(pts, 5.0, 0.6, a0=0.8, a1=0.0, soft=1.5))
        core = np.maximum(core, pad.stroke(pts, 1.6, 0.3, a0=0.7, a1=0.0))
    # 머리: 밝은 덩이 · 앞쪽 눌린 테 · 도는 네 뿔 별
    core = np.maximum(core, pad.disc_field(hx, cy, 13.0, soft=2.0))
    glow = np.maximum(glow, pad.disc_field(hx, cy, 22.0, soft=6.0))
    bow = arc_pts(hx + 2, cy, 17.0, -1.1, 1.1, 24)
    core = np.maximum(core, pad.stroke(bow, 1.2, a0=0.7, taper=0.8))
    core = np.maximum(core, star4(pad, hx + 2, cy, 26.0, 1.0, rot=ph / 4, thin=0.1, diag=0.45))
    # 불티 (뒤로 흐름 — 4 장에 한 주기)
    rnd = random.Random(300)
    for i in range(14):
        base_x = rnd.uniform(0, 128)
        x = hx - 18 - ((base_x + scroll * 2) % 128) * 1.45
        if x < tail_end + 4:
            continue
        tt = (hx - x) / (hx - tail_end)
        y = cy + rnd.uniform(-1, 1) * (8 + 26 * tt)
        al = (1 - tt) ** 0.8 * rnd.uniform(0.6, 1.0)
        r = rnd.uniform(0.8, 1.6) * (1 - 0.4 * tt)
        core = np.maximum(core, pad.stroke([(x, y), (x + 5, y)], r * 0.5, r, a0=0.0, a1=al))
        glow = np.maximum(glow, pad.disc_field(x + 4, y, r * 2.2, soft=1.2) * al * 0.6)
    return finish(pad, glow, core, halo=((1.3, 0.48), (3.2, 0.38), (7.0, 0.22)), body_k=0.85, core_bloom=((1.3, 0.38), (3.5, 0.3)), win=3.5)


sprite("rfx_jcomet2", "lookat_direction", 256, 96, 4, 4, job="warrior", zoom=0,
       desc="불꽃 꼬리 (이어 돌림 — 결이 4 장에 한 주기로 흐름): 밝은 머리 · 앞쪽 눌린 테 · 도는 네 뿔 별 · 일렁이는 불길 · 뜨거운 속불 · 갈라지는 혀 둘 · 뒤로 흐르는 불티")(comet_frame)


# ------------------------------------------------------------ rfx_jshatter2 (깨지는 거울 — 금이 퍼지고 조각이 돌며 흩어짐, 반지름 ≈ 반 크기 × 0.88)

def shard_cells(S, R):
    c = S / 2
    rnd = random.Random(19)
    n = 9
    angs = [i * TAU / n + rnd.uniform(-0.16, 0.16) for i in range(n)]
    rings = [R * 0.14, R * 0.42, R * 0.72, R]
    pts = [[(c + math.cos(a) * r * (rnd.uniform(0.9, 1.08) if r < R else 1.0), c + math.sin(a) * r * (rnd.uniform(0.9, 1.08) if r < R else 1.0)) for a in angs]
           for r in rings]
    polys = []
    for ri in range(len(rings) - 1):
        for i in range(n):
            j = (i + 1) % n
            q = [pts[ri][i], pts[ri][j], pts[ri + 1][j], pts[ri + 1][i]]
            if ri == len(rings) - 2:
                # 바깥 테두리는 둥글게 (거울 테)
                a0, a1 = angs[i], angs[j] + (TAU if j == 0 else 0)
                arc = [(c + math.cos(a0 + (a1 - a0) * k / 6) * R, c + math.sin(a0 + (a1 - a0) * k / 6) * R) for k in range(7)]
                q = [pts[ri][i], pts[ri][j]] + arc[::-1]
            polys.append(q)
    return polys, pts[0]


def shatter_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    R = S * 0.42
    polys, inner = shard_cells(S, R)
    rr, th = polar(pad, c, c)
    core = pad.zeros()
    glow = pad.zeros()
    rnd = random.Random(5)
    if f <= 1:
        pane = pad.disc_field(c, c, R)
        sheen = clamp01(1 - ((pad.grid()[0] - c * 0.7) + (pad.grid()[1] - c * 0.7)) / (S * 0.9))
        glow = np.maximum(glow, pane * (0.38 + 0.25 * sheen + 0.15 * f))
        cracks = pad.zeros()
        for q in polys:
            cracks = np.maximum(cracks, pad.stroke(q + [q[0]], 1.1 if f == 1 else 0.8, a0=1.0 if f == 1 else 0.75))
        if f == 0:
            cracks = cracks * smooth(R * 0.95, R * 0.4, rr)
        core = np.maximum(core, cracks)
        core = np.maximum(core, gauss(rr - R, 0.9) * 0.9)
        glow = np.maximum(glow, gauss(rr - R, 3.0) * 0.6)
        fk = 1.0 if f == 0 else 0.7
        core = np.maximum(core, star4(pad, c, c, 40 * fk + 8, fk, rot=0.3, thin=0.08, diag=0.45))
        core = np.maximum(core, pad.disc_field(c, c, 7 * fk, soft=2.0))
        glow = np.maximum(glow, pad.radial(c, c, 34 * fk, 1.3) * fk)
    else:
        q_ = (f - 1) / 6
        al = (1 - q_) ** 1.15 + (0.12 if f == 7 else 0)
        for k, poly_ in enumerate(polys):
            mx = sum(x for x, _ in poly_) / len(poly_)
            my = sum(y for _, y in poly_) / len(poly_)
            dd = math.hypot(mx - c, my - c) or 1
            ox, oy = (mx - c) / dd, (my - c) / dd
            dn = dd / R
            push = S * (0.17 - 0.13 * dn) * ease_out(q_, 1.7) + S * 0.02 * q_
            sh = 1 - 0.5 * q_
            spin = rnd.uniform(-1, 1) * q_ * 2.6
            moved = [(mx + ox * push + (x - mx) * sh, my + oy * push + (y - my) * sh) for x, y in poly_]
            moved = rot_pts(moved, mx + ox * push, my + oy * push, spin)
            m = pad.poly(moved)
            X, Y = pad.grid()
            facet = 0.5 + 0.5 * math.cos(spin * 2 + k)
            grad = clamp01(((X - (mx + ox * push)) * math.cos(spin + k) + (Y - (my + oy * push)) * math.sin(spin + k)) / 12 + 0.5)
            glow = np.maximum(glow, m * (0.5 + 0.35 * grad) * al)
            core = np.maximum(core, (rim_of(m, 0.8) * 0.95 + m * grad ** 3 * 0.55 * facet) * al)
        # 반짝이는 가루
        rs = random.Random(66)
        for i in range(16):
            a = rs.uniform(0, TAU)
            dist = R * rs.uniform(0.2, 0.9) + S * 0.12 * ease_out(q_)
            dist = min(dist, S * 0.43)
            x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
            tw = 0.5 + 0.5 * math.cos(f * 2.1 + i)
            core = np.maximum(core, star4(pad, x, y, (2.5 + 3.5 * tw) * (1 - 0.4 * q_), al * (0.4 + 0.6 * tw), rot=0.4, thin=0.18, diag=0.0))
    return finish(pad, glow, core, halo=((1.2, 0.45), (3.0, 0.35), (6.5, 0.2)), body_k=0.85, core_bloom=((1.2, 0.32), (3.0, 0.24)), win=4.0)


sprite("rfx_jshatter2", "rotate_xyz", 192, 192, 8, 4, job="mage", zoom=1,
       desc="깨지는 거울: 0 맞은 자리 번쩍 · 퍼지는 금, 1 금 그물 · 둥근 테 → 2 ~ 7 테두리째 조각 스물일곱이 돌며 흩어짐 (밝은 테 · 기우는 면 빛) · 반짝이는 가루")(shatter_frame)


# ------------------------------------------------------------ rfx_jbeam2 (굵은 광선 — 방향으로 누움, 조각을 이어 붙임: 양 끝 6% 가 가늘어짐)

BEAM_TH = [0.06, 0.3, 0.42, 0.38, 0.22, 0.07]


def beam_frame(f, W=256, H=64):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    th = BEAM_TH[f] * H * 0.98
    br = [1.0, 1.0, 1.0, 1.0, 0.92, 0.7][f]
    tx = np.clip(np.minimum((X - 2) / (W * 0.065), (W - 2 - X) / (W * 0.065)), 0, 1)
    taper = np.sin(np.pi / 2 * tx) ** 0.8
    ph = f * 1.3
    wob = 1 + 0.1 * np.sin(X * 0.11 - ph * 2) + 0.06 * np.sin(X * 0.27 + ph * 3)
    hh = th * taper * wob + 0.01
    dyy = np.abs(Y - cy)
    body = smooth(hh + 1.0, hh - 2.0, dyy)
    n = fbm(X / 14.0 - ph, Y / 7.0, seed=41, octaves=3)
    inner = clamp01((hh * (0.48 + 0.08 * n) - dyy) / max(1.0, th * 0.2))
    core = inner * (0.85 + 0.15 * n) * br
    core = np.maximum(core, gauss(Y - cy, 0.6 + th * 0.05) * taper * br)
    # 감아 도는 가는 빛 줄 둘 (앞 반만 밝게)
    if th > 4:
        for j in (0, 1):
            phase = np.sin(X * 0.075 - ph * 2.2 + j * math.pi)
            yy = cy + phase * hh * 0.82
            front = clamp01(np.cos(X * 0.075 - ph * 2.2 + j * math.pi) * 2 + 0.5)
            core = np.maximum(core, gauss(Y - yy, 0.55 + 0.02 * th) * taper * (0.35 + 0.6 * front) * br)
    glow = np.maximum(body * (0.85 + 0.15 * n), smooth(hh * 1.35 + 4.0, hh, dyy) * 0.55 * taper) * br
    # 가장자리에서 튀는 반짝임
    if 1 <= f <= 4:
        rnd = random.Random(f * 13)
        for i in range(9):
            x = rnd.uniform(W * 0.08, W * 0.92)
            sg = rnd.choice((-1, 1))
            y = cy + sg * (th * rnd.uniform(0.75, 1.15))
            y = min(H - 7, max(7, y))
            core = np.maximum(core, star4(pad, x, y, rnd.uniform(3.5, 6.0), br, rot=0.0, thin=0.16, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(x, y, 2.4, soft=1.2) * br * 0.6)
    vf = smooth(0.5, 5.0, np.minimum(Y, H - Y))
    return finish(pad, glow * vf, core * vf, halo=((1.0, 0.45), (2.5, 0.35), (5.0, 0.2)), body_k=0.9, core_bloom=((1.0, 0.3), (2.5, 0.22)), win=2.5)


sprite("rfx_jbeam2", "lookat_direction", 256, 64, 6, 2, job="mage", zoom=2,
       desc="굵은 광선 (조각을 이어 붙임 — 양 끝이 가늘어짐): 가늘게 그어져 굵어지고 (일렁이는 가장자리 · 흐르는 결 · 흰 속줄 · 감아 도는 빛 줄 둘 · 반짝임) 다시 가늘어짐")(beam_frame)



# ------------------------------------------------------------ rfx_jgate2 (명계의 문 — 0 ~ 2 땅에서 솟음, 3 ~ 5 열린 채 소용돌이 (이어 돌림), 6 ~ 7 닫힘)

GATE_RISE = [0.35, 0.7, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]
GATE_OPEN = [0.0, 0.0, 0.0, 0.8, 1.0, 1.0, 0.45, 0.0]


def gate_frame(f, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    rise = GATE_RISE[f]
    dy = (1 - rise) * S
    opening = GATE_OPEN[f]
    gy = S * 0.975
    st = Stack(pad)
    above = smooth(gy + 0.5, gy - 2.0, Y)  # 땅 위만
    glow_extra = np.zeros((S, S), np.float32)
    # ---- 문 안 (열린 동안): 어두운 속 · 도는 소용돌이 팔 셋 · 빨려 드는 알갱이 · 가운데 눈
    x_in0, x_in1 = S * 0.29, S * 0.71
    y_in0 = S * 0.36 + dy
    if opening > 0:
        cxv, cyv = S * 0.5, S * 0.64 + dy
        inner_w = (x_in1 - x_in0) / 2 * opening
        rr, th = polar(pad, cxv, cyv)
        ell = clamp01(1 - ((X - cxv) / max(1.0, inner_w)) ** 2 - ((Y - (cyv - 6)) / (S * 0.33)) ** 2)
        st.body = np.maximum(st.body, ell ** 0.5 * 0.42)
        rot = (f - 3) * TAU / 9 if f <= 5 else 0.5
        arms_c = pad.zeros()
        for k in range(3):
            pts = []
            for i in range(40):
                t = i / 39
                a = rot + k * TAU / 3 + t * 4.4
                r = 4 + t * S * 0.3
                pts.append((cxv + math.cos(a) * min(r, inner_w), cyv + math.sin(a) * r * 1.12))
            arms_c = np.maximum(arms_c, pad.stroke(pts, 1.0, 5.0, a0=0.35, a1=1.0))
            st.body = np.maximum(st.body, pad.stroke(pts, 3.0, 13.0, a0=0.35, a1=0.85, soft=3.0) * ell ** 0.3)
        arms_c = arms_c * ell ** 0.25
        rs = random.Random(44)
        for i in range(16):
            qq = (rs.random() + (f - 3) / 3) % 1.0
            a0 = rs.uniform(0, TAU) + qq * 2.5 + rot
            r = (1 - qq) * S * 0.3 + 6
            x, y = cxv + math.cos(a0) * min(r, inner_w), cyv + math.sin(a0) * r * 1.12
            arms_c = np.maximum(arms_c, pad.disc_field(x, y, 1.4) * math.sin(math.pi * qq) * ell.max())
        st.add_core(arms_c * opening)
        eye = pad.disc_field(cxv, cyv, 7.5)
        st.body = st.body * (1 - eye * 0.85)
        st.add_core(gauss(rr - 8.5, 0.9) * opening)
        glow_extra = np.maximum(glow_extra, pad.down(pad.radial(cxv, cyv, S * 0.3, 1.5)) * 0.5 * opening)
    # ---- 기둥 둘: 쌓은 돌 (비탈 빛 · 이음 줄) · 새김 홈 (지어낸 글자 넷이 빛남) · 뿔 머리
    for x0, x1 in ((S * 0.14, S * 0.29), (S * 0.71, S * 0.86)):
        y_top = S * 0.27 + dy
        pil = pad.poly([(x0, gy + 4), (x1, gy + 4), (x1, y_top), (x0, y_top)]) * above
        lit, dark, _ = bevel_light(pil, 4.0)
        pc = rim_of(pil, 1.0) * 0.8 + lit * 0.45
        yy = y_top + 24
        while yy < gy:
            pc = np.maximum(pc, pad.stroke([(x0 + 1, yy), (x1 - 1, yy)], 0.9, a0=0.6) * pil)
            yy += 24
        mid = (x0 + x1) / 2
        groove = pad.poly([(mid - 5, y_top + 12), (mid + 5, y_top + 12), (mid + 5, gy - 8), (mid - 5, gy - 8)]) * pil
        gl = pad.zeros()
        for k in range(4):
            gyk = y_top + 30 + k * ((gy - y_top - 50) / 3.6)
            if gyk < gy - 14:
                gl = np.maximum(gl, glyph_mask(pad, [3, 7, 10, 1][k] + (0 if x0 < S / 2 else 2), mid, gyk, 7.0, 11.0, 1.3))
        st.over(pil, (0.62 + 0.14 * lit - 0.16 * dark) * (1 - 0.35 * groove), np.maximum(pc, gl) * pil)
        glow_extra = np.maximum(glow_extra, gblur(pad.down(gl * above), 2.0) * 0.8)
        cap = pad.poly([(x0 - 5, y_top + 2), (x1 + 5, y_top + 2), (x1 + 5, y_top - 7), (x0 - 5, y_top - 7)]) * above
        cl, cd, _ = bevel_light(cap, 2.0)
        st.over(cap, 0.85 + 0.1 * cl - 0.15 * cd, (rim_of(cap, 0.8) * 0.9 + cl * 0.5) * cap)
        horn = pad.poly(bez((x0 - 3, y_top - 6), (mid - 6, y_top - 18), (mid + 2, S * 0.12 + dy), 12) + bez((mid + 2, S * 0.12 + dy), (mid + 4, y_top - 16), (x1 + 3, y_top - 6), 12)[1:]) * above
        hl, hd, _ = bevel_light(horn, 3.0)
        st.over(horn, 0.84 + 0.12 * hl - 0.2 * hd, (rim_of(horn, 0.9) * 0.9 + hl * 0.55) * horn)
    # ---- 아치 (돌 띠 · 쐐기 이음) · 머릿돌 뿔
    pts_o, pts_i = [], []
    for i in range(33):
        t = i / 32
        x = S * 0.22 + S * 0.56 * t
        hh = math.sin(math.pi * t)
        pts_o.append((x, S * (0.3 - 0.19 * hh) + dy))
        pts_i.append((x, S * (0.37 - 0.16 * hh) + dy))
    arch = pad.poly(pts_o + pts_i[::-1]) * above
    al_, ad_, _ = bevel_light(arch, 3.5)
    ac = rim_of(arch, 1.0) * 0.85 + al_ * 0.5
    for k in range(1, 8):
        i = k * 4
        ac = np.maximum(ac, pad.stroke([pts_o[i], pts_i[i]], 0.9, a0=0.6) * arch)
    st.over(arch, 0.7 + 0.15 * al_ - 0.15 * ad_, ac * arch)
    key = pad.poly([(S * 0.46, S * 0.13 + dy), (S * 0.54, S * 0.13 + dy), (S * 0.56, S * 0.2 + dy), (S * 0.5, S * 0.22 + dy), (S * 0.44, S * 0.2 + dy)]) * above
    kl, _, _ = bevel_light(key, 2.0)
    st.over(key, 0.9, (rim_of(key, 0.8) * 0.9 + kl * 0.5) * key)
    spike = pad.poly([(S * 0.475, S * 0.135 + dy), (S * 0.5, S * 0.045 + dy), (S * 0.525, S * 0.135 + dy)]) * above
    st.over(spike, 0.9, (rim_of(spike, 0.7) * 0.9 + spike * 0.35) * spike)
    st.add_core(pad.poly(star_pts(S * 0.5, S * 0.17 + dy, 4.0, 4, 0.3), 1.0) * above)
    # ---- 문짝 둘 (열리면 기둥 쪽으로 좁아짐): 널빤지 줄 · 쇠 띠 셋 · 징 · 고리 손잡이
    dw = S * 0.21 * (1 - opening)
    if dw > S * 0.01:
        for side in (-1, 1):
            xa = S * 0.5 + side * S * 0.21
            xb = xa - side * dw
            lo, hi = min(xa, xb), max(xa, xb)
            door = pad.poly([(lo, y_in0 + 2), (hi, y_in0 + 2), (hi, gy + 4), (lo, gy + 4)]) * above
            dc = rim_of(door, 0.9) * 0.7
            nplank = 4
            for k in range(1, nplank):
                xk = lo + (hi - lo) * k / nplank
                dc = np.maximum(dc, pad.stroke([(xk, y_in0 + 4), (xk, gy)], 0.6, a0=0.35) * door)
            for k in range(3):
                yk = y_in0 + 22 + k * (gy - y_in0 - 40) / 2
                band = pad.poly([(lo, yk - 3), (hi, yk - 3), (hi, yk + 3), (lo, yk + 3)]) * door
                dc = np.maximum(dc, rim_of(band, 0.6) * 0.8 * band + band * 0.25)
                for xs in np.linspace(lo + 5, hi - 5, max(2, int((hi - lo) / 14))):
                    dc = np.maximum(dc, pad.disc_field(xs, yk, 1.4) * door)
            if hi - lo > 18:
                hxr = hi - 8 if side < 0 else lo + 8
                ring_c = gauss(np.sqrt((X - hxr) ** 2 + (Y - (S * 0.66 + dy)) ** 2) - 5.0, 0.7)
                dc = np.maximum(dc, ring_c * door)
            st.over(door, 0.46, dc * door)
        if f == 7:
            st.add_core(pad.stroke([(S * 0.5, y_in0 + 4), (S * 0.5, gy - 2)], 1.6, a0=1.0) * above)
            st.add_core(star4(pad, S * 0.5, S * 0.64 + dy, 30, 1.0, thin=0.08, diag=0.45))
            glow_extra = np.maximum(glow_extra, pad.down(pad.radial(S * 0.5, S * 0.64 + dy, 46, 1.4)) * 0.7)
    # ---- 솟는 동안: 땅의 먼지 · 튀는 돌 · 금
    if f <= 2:
        k = [1.0, 0.8, 0.45][f]
        rnd = random.Random(70 + f)
        dust = pad.zeros()
        for i in range(14):
            x = rnd.uniform(26, 230)
            r = rnd.uniform(9, 16) * (0.7 + 0.5 * k)
            dust = np.maximum(dust, pad.disc_field(x, gy - r * 0.3 - rnd.uniform(0, 8), r, soft=r * 0.85))
        st.body = np.maximum(st.body, dust * 0.45 * k * above)
        for i in range(10):
            x = rnd.uniform(20, 236)
            y = gy - rnd.uniform(8, 60) * (1.2 - 0.3 * f)
            pts = chunk_pts(rnd, x, y, rnd.uniform(2.2, 4.2), rot=rnd.uniform(0, TAU))
            m, body, cc = faceted(pad, pts, a=k)
            st.body = np.maximum(st.body, body)
            st.add_core(cc)
    st.add_core(pad.stroke([(14, gy), (242, gy)], 1.0, a0=0.45, taper=0.4) * smooth(gy + 3, gy, Y))
    bf = smooth(S - 0.5, S - 6.0, Y)
    core1 = pad.down(st.core * bf)
    glow1 = obj_glow(pad, st.body * bf, core1, halo=((1.5, 0.42), (4.0, 0.3), (9.0, 0.18)), body_k=1.0, core_bloom=((1.3, 0.3), (3.5, 0.24)))
    glow1 = screen(glow1, glow_extra * pad.down(bf))
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jgate2", "lookat_y", 256, 256, 8, 4, job="reaper", zoom=4,
       desc="명계의 문: 쌓은 돌 기둥 둘 (빛나는 새김 홈 · 뿔 머리) · 쐐기 이음 아치 · 머릿돌 뿔 · 쇠 띠 문짝 — 0 ~ 2 먼지 · 튀는 돌과 함께 솟음, 3 ~ 5 열림 (도는 소용돌이 팔 셋 · 빨려 드는 알갱이), 6 ~ 7 닫힘 (이음 번쩍)")(gate_frame)


# ------------------------------------------------------------ rfx_jhand2 (망자의 손 — 땅에서 솟아 손가락을 펴고 → 움켜쥐고 → 가라앉음, 아래 끝이 땅)

HAND_RISE = [0.3, 0.65, 1.0, 1.0, 1.0, 0.75]
HAND_CURL = [0.0, 0.05, 0.0, 0.5, 1.0, 1.0]
HAND_VIS = [1.0, 1.0, 1.0, 1.0, 0.94, 0.58]


def hand_frame(f, W=64, H=128):
    pad = Pad(W, H)
    X, Y = pad.grid()
    rise, curl, vis = HAND_RISE[f], HAND_CURL[f], HAND_VIS[f]
    dy = (1 - rise) * H * 0.75
    ground = H - 3.0
    above = smooth(ground + 0.5, ground - 3.0, Y)
    cx = W * 0.5
    st = Stack(pad)
    pc = (cx, H * 0.43 + dy)
    # ---- 넋의 빛 (팔 둘레 연기 — 빛 장면)
    aura = pad.zeros()
    rnd = random.Random(12 + f)
    for i in range(9):
        yy = pc[1] - 10 + (H - pc[1] + 10) * (i + 0.5) / 9
        aura = np.maximum(aura, pad.disc_field(cx + rnd.uniform(-3, 3), yy, rnd.uniform(8, 11), soft=7))
    st.body = np.maximum(st.body, aura * 0.3 * above)
    # ---- 팔뚝 뼈 둘 (아래로 땅에 박힘)
    for x0, x1, w in ((cx - 3.6, cx - 2.2, 2.4), (cx + 3.4, cx + 2.4, 2.0)):
        bone = pad.stroke([(x0, ground + 6), (x1, pc[1] + 9)], w * 2, w * 1.7) * above
        st.over(bone, 0.75 * vis, (rim_of(bone, 0.6) * 0.85 + bone * 0.3) * bone * vis)
    wrist = pad.disc_field(cx, pc[1] + 9, 4.2) * above
    st.over(wrist, 0.82 * vis, (rim_of(wrist, 0.6) * 0.9 + wrist * 0.3) * wrist * vis)
    # ---- 손바닥 (손허리뼈 넷이 부채꼴) · 손가락 (세 마디 — curl 만큼 안으로 굽음) · 엄지
    palm = pad.poly([(cx - 9.5, pc[1] - 6), (cx + 9.5, pc[1] - 6), (cx + 7.0, pc[1] + 8), (cx - 7.0, pc[1] + 8)]) * above
    st.over(palm, 0.55 * vis, rim_of(palm, 0.6) * 0.55 * palm * vis)
    fingers = ((-0.4, 0.25, -7.2), (-0.13, 0.29, -2.5), (0.13, 0.28, 2.5), (0.4, 0.23, 7.2))
    for i, (spread, L, bx) in enumerate(fingers):
        a = -math.pi / 2 + spread * (1 - 0.6 * curl)
        mc_end = (cx + bx, pc[1] - 6)
        mc = pad.stroke([(cx + bx * 0.35, pc[1] + 6), mc_end], 2.4, 2.0) * above
        st.over(mc, 0.8 * vis, (rim_of(mc, 0.5) * 0.7 + mc * 0.3) * mc * vis)
        # 마디마다 가운데 쪽으로 굽고 (움켜쥠) 앞으로 접혀 짧아 보임
        sgn_in = 1 if spread < 0 else -1
        x, y = mc_end
        cur = a
        joints = [(x, y)]
        for j, sl in enumerate((L * 0.42, L * 0.33, L * 0.25)):
            cur = cur + sgn_in * curl * math.radians([22, 48, 55][j]) * (0.55 if abs(spread) < 0.2 else 1.0)
            ln = sl * H * (1 - curl * [0.1, 0.42, 0.5][j])
            if abs(spread) < 0.2 and curl > 0:
                # 가운데 손가락 둘: 앞으로 접힘 — 아래 (손바닥) 쪽으로 돌아옴
                cur = -math.pi / 2 + spread + curl * (math.radians([0, 150, 200][j]) * (1 if spread < 0 else -1))
            x2, y2 = x + math.cos(cur) * ln, y + math.sin(cur) * ln
            joints.append((x2, y2))
            x, y = x2, y2
        for j in range(3):
            seg = pad.stroke([joints[j], joints[j + 1]], 3.0 - 0.45 * j, 2.6 - 0.45 * j) * above
            st.over(seg, 0.86 * vis, (rim_of(seg, 0.5) * 0.85 + seg * 0.35) * seg * vis)
            st.add_core(pad.disc_field(joints[j][0], joints[j][1], 1.5 - 0.2 * j) * above * vis)
        tx, ty = joints[-1]
        claw = pad.poly([(tx - 1.1, ty), (tx + math.cos(cur) * 3.0, ty + math.sin(cur) * 3.0), (tx + 1.1, ty)]) * above
        st.add_core(claw * vis)
    # 엄지 (옆으로 · curl 이면 손바닥 위로)
    th_a = math.radians(-160) + curl * math.radians(70)
    x, y = cx - 7.5, pc[1] + 2
    pts = [(x, y)]
    for j, sl in enumerate((7.5, 6.0)):
        th_a = th_a + curl * math.radians(30) * j
        x, y = x + math.cos(th_a) * sl, y + math.sin(th_a) * sl
        pts.append((x, y))
    thumb = pad.stroke(pts, 2.6, 1.8) * above
    st.over(thumb, 0.85 * vis, (rim_of(thumb, 0.5) * 0.85 + thumb * 0.35) * thumb * vis)
    # ---- 땅: 금 · 솟을 때 흙 부스러기
    gc = pad.zeros()
    rnd = random.Random(5)
    for sg in (-1, 1):
        for j in range(2):
            x0 = cx + sg * (6 + 7 * j)
            pts = bolt_pts(rnd, (x0, ground), (x0 + sg * rnd.uniform(8, 13), ground + rnd.uniform(-1.5, 1.0)), 0.3, 3, 0.6)
            gc = np.maximum(gc, pad.stroke(pts, 1.0, 0.3, a0=0.8, a1=0.2))
    st.add_core(gc * [0.8, 1.0, 0.9, 0.7, 0.6, 0.45][f])
    if f <= 1:
        for i in range(6):
            x = cx + rnd.uniform(-20, 20)
            y = ground - rnd.uniform(3, 26) * (1 + f)
            pts = chunk_pts(rnd, x, y, rnd.uniform(1.3, 2.4), rot=rnd.uniform(0, TAU))
            m, body, cc = faceted(pad, pts, a=1.0 - 0.4 * f, depth=0.9)
            st.body = np.maximum(st.body, body)
            st.add_core(cc)
    bf = smooth(H - 0.5, H - 4.0, Y) * smooth(0.5, 5.0, Y)
    core1 = pad.down(st.core * bf)
    glow1 = obj_glow(pad, st.body * bf, core1, halo=((1.0, 0.45), (2.6, 0.34), (5.5, 0.2)), body_k=1.0, core_bloom=((1.0, 0.3), (2.6, 0.24)))
    wb = win_border(core1.shape, 3.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jhand2", "lookat_y", 64, 128, 6, 6, job="reaper", zoom=3,
       desc="망자의 손 (아래 끝이 땅): 팔뚝 뼈 둘 · 손목 · 손허리뼈 · 세 마디 손가락 넷 (마디 점 · 손톱) · 엄지 · 넋의 연기 · 땅의 금 · 흙 부스러기 — 솟아 펴고 → 움켜쥐고 → 가라앉음")(hand_frame)



# ------------------------------------------------------------ rfx_jwing2 (빛의 날개 — 면이 방향 쪽, 이어 돌림: 4 장 날갯짓)

def wing2_frame(f, W=256, H=192):
    pad = Pad(W, H)
    lift = 0.5 - 0.5 * math.cos(TAU * f / 4)
    st = Stack(pad)
    glow_extra = np.zeros((H, W), np.float32)
    for sg in (-1, 1):
        R = (W / 2 + sg * 9, H * 0.6)
        E = (W / 2 + sg * W * 0.22, H * (0.35 - 0.05 * lift))
        T = (W / 2 + sg * W * 0.445, H * (0.15 - 0.06 * lift))
        lead = bez(R, (R[0] + sg * 18, R[1] - 40 - 6 * lift), E, 20) + bez(E, ((E[0] + T[0]) / 2, E[1] - 16 + 4 * lift), T, 20)[1:]
        Q0 = (R[0] + sg * 4, H * 0.78)
        Q3 = (T[0] + sg * 6, T[1] + 14)
        trail = bez(Q0, (R[0] + sg * W * 0.2, H * (0.95 - 0.12 * lift)), (T[0] + sg * 2, H * (0.62 - 0.2 * lift)), 40, p3=Q3)
        nL = len(lead)
        N = 16
        for i in range(N - 1, -1, -1):
            u = (i + 0.5) / N
            B = lead[int(u * (nL - 1))]
            P = trail[int(u * 40)]
            ang = math.atan2(P[1] - B[1], P[0] - B[0])
            L = math.hypot(P[0] - B[0], P[1] - B[1])
            m, spine = wing_feather(pad, B, ang, L, 4.6 + 2.0 * u, curve=0.06 * sg)
            core = rim_of(m, 0.75) * 0.8
            core = np.maximum(core, pad.stroke(spine[:13], 0.8, 0.3, a0=0.8, a1=0.25) * m)
            tx_, ty_ = spine[-1]
            core = np.maximum(core, pad.disc_field(tx_, ty_, 3.2, soft=2.0) * m * 0.6)
            st.over(m, 0.58 + 0.28 * u, core * m)
        for row, (Lk, Wk, sh) in enumerate(((0.44, 4.4, 0.8), (0.23, 3.6, 0.9))):
            for i in range(12, -1, -1):
                u = (i + 0.5) / 13
                B = lead[int(u * (nL - 1))]
                P = trail[int(u * 40)]
                ang = math.atan2(P[1] - B[1], P[0] - B[0])
                L = math.hypot(P[0] - B[0], P[1] - B[1]) * Lk + 4
                m, spine = wing_feather(pad, (B[0], B[1] + 1), ang, L, Wk + 1.1 * u, curve=0.05 * sg)
                st.over(m, sh, (rim_of(m, 0.7) * 0.8 + m * (0.28 if row == 1 else 0.12)) * m)
        st.add_core(pad.stroke(lead, 2.2, 0.9, a0=1.0, a1=0.75))
        st.body = np.maximum(st.body, pad.stroke(lead, 6.0, 3.0, a0=0.8, a1=0.5, soft=2.0))
        st.add_core(star4(pad, T[0], T[1], 8 + 3 * lift, 0.95, rot=0.3, thin=0.12, diag=0.0))
        glow_extra = np.maximum(glow_extra, pad.down(pad.radial(R[0], R[1], 22, 1.4)) * 0.7)
    st.add_core(pad.disc_field(W / 2, H * 0.6, 3.5))
    st.add_core(star4(pad, W / 2, H * 0.6, 12, 0.85, rot=0.0, diag=0.0))
    rnd = random.Random(12)
    for i in range(8):
        x0 = W / 2 + rnd.choice((-1, 1)) * rnd.uniform(30, 100)
        t = (rnd.random() + f / 4) % 1.0
        y = H * 0.66 + 40 * t
        st.add_core(star4(pad, x0 + 4 * math.sin(t * 6 + i), y, 2.5 + 2 * rnd.random(), math.sin(math.pi * t) * 0.8, diag=0.0))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.5, 0.45), (4.0, 0.34), (9.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.3), (4.0, 0.25)))
    glow1 = screen(glow1, glow_extra)
    wb = win_border(core1.shape, 4.0)
    return glow1 * wb, core1 * wb


sprite("rfx_jwing2", "direction_z", 256, 192, 4, 4, job="paladin", zoom=2,
       desc="빛의 날개 (이어 돌림 — 4 장 날갯짓): 등에서 양옆 위로 펼친 깃털 날개 한 쌍 (날개깃 열여섯 · 덮깃 두 줄 · 깃대 · 밝은 앞 가장자리 · 끝 반짝임) · 등 빛 · 떨어지는 빛 알갱이")(wing2_frame)


# ------------------------------------------------------------ rfx_jstun2 (기절 별 — 머리 위 타원을 따라 도는 별 다섯, 이어 돌림: 8 장 = 1/5 바퀴)

def star5_facets(pad, x, y, r, rot, inner=0.46, a=1.0):
    """비탈 면이 있는 다섯 뿔 별: (모양, 면 빛) — 위 왼쪽 빛"""
    pts = star5_pts(x, y, r, inner, rot)
    m = pad.poly(pts, a)
    lit = pad.zeros()
    for i in range(10):
        p0, p1 = pts[i], pts[(i + 1) % 10]
        tri = [(x, y), p0, p1]
        mx, my = (p0[0] + p1[0]) / 2 - x, (p0[1] + p1[1]) / 2 - y
        L = math.hypot(mx, my) or 1
        nx, ny = mx / L, my / L
        side = 1 if i % 2 == 0 else -1
        # 뿔마다 두 면: 빛 쪽 면이 밝음
        k = 0.5 + 0.5 * (-(nx * 0.6 + ny * 0.8))
        lit = np.maximum(lit, pad.poly(tri, a * (0.35 + 0.65 * k) * (1.0 if side > 0 else 0.7)))
    return m, lit


def stun_frame(f, W=128, H=64):
    pad = Pad(W, H)
    cx, cy = W / 2, H / 2
    rx, ry = W * 0.37, H * 0.28
    core = pad.zeros()
    glow = pad.zeros()
    ring = arc_pts(cx, cy, rx, 0, TAU, 96, ry=ry)
    d, sfr = pad.line_field(ring, 6)
    front = np.sin(sfr * TAU)
    ringa = 0.25 + 0.4 * clamp01(front * 1.5 + 0.5)
    core = np.maximum(core, gauss(d, 0.5) * ringa * 0.7)
    glow = np.maximum(glow, gauss(d, 1.8) * ringa * 0.45)
    stars = []
    for i in range(5):
        a = TAU * i / 5 + f * (TAU / 5) / 8
        depth = (math.sin(a) + 1) / 2
        stars.append((depth, a))
    for depth, a in sorted(stars):
        x, y = cx + math.cos(a) * rx, cy + math.sin(a) * ry
        r = 11.2 * (0.62 + 0.38 * depth)
        # 지나온 자리 (도는 쪽 뒤로 흐려지는 가는 호)
        tr = arc_pts(cx, cy, rx, a - 0.75, a - 0.08, 16, ry=ry)
        core = np.maximum(core, pad.stroke(tr, 0.3, 1.4 * (0.5 + 0.5 * depth), a0=0.0, a1=0.8 * (0.4 + 0.6 * depth)))
        glow = np.maximum(glow, pad.stroke(tr, 0.5, 3.5, a0=0.0, a1=0.5 * (0.4 + 0.6 * depth), soft=1.0))
        m, lit = star5_facets(pad, x, y, r, a * 0.9, a=1.0)
        occl = 1 - m
        core = core * occl + lit * (0.55 + 0.45 * depth)
        core = np.maximum(core, rim_of(m, 0.6) * (0.6 + 0.4 * depth))
        glow = np.maximum(glow * occl, m * (0.75 + 0.25 * depth))
    # 반짝임 (자리 · 크기가 장마다 바뀜 — 8 장 이어 돌림)
    rnd = random.Random(8)
    for i in range(4):
        a = rnd.uniform(0, TAU)
        x, y = cx + math.cos(a) * rx * rnd.uniform(0.6, 1.1), cy + math.sin(a) * ry * rnd.uniform(0.4, 1.3) - 3
        tw = 0.5 + 0.5 * math.cos(TAU * f / 8 * 2 + i * 1.7)
        core = np.maximum(core, star4(pad, x, y, 2.5 + 3 * tw, tw, rot=0.0, thin=0.16, diag=0.0))
    return finish(pad, glow, core, halo=((1.0, 0.45), (2.4, 0.32), (5.0, 0.18)), body_k=0.9, core_bloom=((1.0, 0.3), (2.4, 0.22)), win=3.0)


sprite("rfx_jstun2", "rotate_xyz", 128, 64, 8, 4, job="paladin", zoom=0, colors=((1.0, 0.86, 0.3), (1.0, 1.0, 0.82)),
       desc="기절 별 (이어 돌림): 머리 위 타원 (앞이 밝음) 을 따라 도는 비탈 면 다섯 뿔 별 다섯 (앞은 크고 밝게) · 별 뒤의 가는 호 · 반짝임")(stun_frame)


# ------------------------------------------------------------ rfx_jjudge2 (심판 표시 — 작은 번개 문장이 깜빡임, 이어 돌림)

JUDGE_BOLT = [(0.62, 0.06), (0.25, 0.55), (0.47, 0.55), (0.37, 0.94), (0.77, 0.41), (0.55, 0.41), (0.69, 0.06)]


def judge_frame(f, S=64):
    pad = Pad(S, S)
    k = [1.0, 0.94, 1.05, 1.0][f]
    bx = sum(x for x, _ in JUDGE_BOLT) / len(JUDGE_BOLT)
    by = sum(y for _, y in JUDGE_BOLT) / len(JUDGE_BOLT)
    pts = [((bx + (x - bx) * k * 0.92) * S, (by + (y - by) * k * 0.92) * S) for x, y in JUDGE_BOLT]
    m = pad.poly(pts)
    lit, dark, _ = bevel_light(m, 3.0)
    X, Y = pad.grid()
    # 가운데 꺾인 줄을 기준으로 왼쪽 면이 밝음 (두 면)
    mid = [((pts[0][0] + pts[6][0]) / 2, pts[0][1] + 2), ((pts[1][0] + pts[5][0]) / 2 + 2, (pts[1][1] + pts[5][1]) / 2), ((pts[2][0] + pts[4][0]) / 2, (pts[2][1] + pts[4][1]) / 2 + 1), (pts[3][0] + 1, pts[3][1] - 2)]
    pin = [((bx + (x - bx) * k * 0.56) * S, (by + (y - by) * k * 0.56) * S) for x, y in JUDGE_BOLT]
    inner = pad.poly(pin)
    core = np.maximum(inner, rim_of(m, 0.7) * 0.9)
    core = np.maximum(core, lit * m * 0.5)
    core = np.maximum(core, pad.stroke(mid, 0.6, 0.3, a0=0.5, a1=0.3) * m * (1 - inner))
    body = m * (0.92 + 0.08 * lit - 0.15 * dark)
    rr, th = polar(pad, S / 2, S / 2)
    ringa = [0.55, 0.35, 0.75, 0.45][f]
    core = np.maximum(core, gauss(rr - 27.5, 0.6) * ringa * (0.6 + 0.4 * np.cos(th * 6 + f)))
    body = np.maximum(body, gauss(rr - 27.5, 2.0) * ringa * 0.5)
    rnd = random.Random(90 + f)
    if f in (1, 3):
        for i in range(2):
            x, y = rnd.uniform(0.18, 0.82) * S, rnd.uniform(0.18, 0.82) * S
            core = np.maximum(core, star4(pad, x, y, 7.0, 0.95, rot=0.0, thin=0.14, diag=0.0))
    for i in range(2):
        a0 = rnd.uniform(0, TAU)
        p0 = (S / 2 + math.cos(a0) * 27.5, S / 2 + math.sin(a0) * 27.5)
        p1 = (S / 2 + math.cos(a0 + 0.5) * 27.5, S / 2 + math.sin(a0 + 0.5) * 27.5)
        arc = bolt_pts(rnd, p0, p1, 0.25, 3, 0.6)
        core = np.maximum(core, pad.stroke(arc, 0.8, a0=0.75))
    return finish(pad, body, core, halo=((0.9, 0.5), (2.2, 0.36), (4.5, 0.2)), body_k=0.9, core_bloom=((0.9, 0.3), (2.0, 0.22)), win=2.5)


sprite("rfx_jjudge2", "rotate_xyz", 64, 64, 4, 4, job="paladin", zoom=0,
       desc="심판 표시 (이어 돌림): 비탈 두 면의 번개 문장 (테 · 가운데 꺾인 줄) · 지직이는 둥근 테 · 깜빡이는 반짝임")(judge_frame)


# ------------------------------------------------------------ rfx_jheal2 (회복 십자 — 피어나 반짝이며 조금 떠오르고 사라짐)

def heal_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    k = [0.45, 1.0, 1.0, 0.96, 0.92, 0.88][f]
    up = [0.0, 0.0, 0.0, 0.04, 0.08, 0.12][f] * S
    v = [0.85, 1.0, 1.0, 0.9, 0.64, 0.34][f]
    cy = c - up
    L = S * 0.3 * k
    w = S * 0.15 * k
    arm_v = pad.stroke([(c, cy - L), (c, cy + L)], w)
    arm_h = pad.stroke([(c - L, cy), (c + L, cy)], w)
    cross = np.maximum(arm_v, arm_h)
    lit, dark, _ = bevel_light(cross, w * 0.3)
    inner = np.maximum(pad.stroke([(c, cy - L + w * 0.1), (c, cy + L - w * 0.1)], w * 0.45), pad.stroke([(c - L + w * 0.1, cy), (c + L - w * 0.1, cy)], w * 0.45))
    core = np.maximum(inner * 0.95, rim_of(cross, 0.9) * 0.85 + lit * 0.55) * v
    core = np.maximum(core, pad.poly([(c, cy - w * 0.42), (c + w * 0.42, cy), (c, cy + w * 0.42), (c - w * 0.42, cy)], v))
    glow = cross * (0.88 + 0.12 * lit) * v
    rr, th = polar(pad, c, cy)
    if 1 <= f <= 4:
        q = (f - 1) / 3
        rp = L * 0.9 + 22 * ease_out(q)
        core = np.maximum(core, gauss(rr - rp, 0.7) * (1 - q) * 0.8)
        glow = np.maximum(glow, gauss(rr - rp, 2.6) * (1 - q) * 0.6)
    if f <= 1:
        core = np.maximum(core, star4(pad, c, cy, S * (0.42 if f == 1 else 0.3), 1.0, rot=math.pi / 4, thin=0.08, diag=0.0))
        glow = np.maximum(glow, pad.radial(c, cy, S * 0.36, 1.3) * (0.8 if f == 1 else 0.5))
    rnd = random.Random(140)
    for i in range(9):
        x0 = c + rnd.uniform(-46, 46)
        y0 = c + rnd.uniform(-10, 40)
        sp = rnd.uniform(8, 13)
        y = y0 - sp * f
        a = (math.sin(math.pi * min(1.0, f / 5 + rnd.uniform(0, 0.3)))) * (f >= 1) * v
        if y < 8 or a <= 0:
            continue
        if i % 3 == 0:
            r_ = rnd.uniform(3.0, 4.5)
            core = np.maximum(core, np.maximum(pad.stroke([(x0, y - r_), (x0, y + r_)], 1.3), pad.stroke([(x0 - r_, y), (x0 + r_, y)], 1.3)) * a)
        else:
            core = np.maximum(core, star4(pad, x0, y, rnd.uniform(3.0, 5.0), a, rot=0.0, thin=0.16, diag=0.0))
        glow = np.maximum(glow, pad.disc_field(x0, y, 2.6, soft=1.2) * a * 0.6)
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.4), (6.5, 0.24)), body_k=0.85, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jheal2", "rotate_xyz", 128, 128, 6, 6, job="priest", zoom=1,
       desc="회복 십자: 둥근 팔의 비탈 십자 (밝은 속 십자 · 가운데 마름모) 가 반짝임과 함께 피어나 퍼지는 고리 · 떠오르는 십자 · 반짝임 알갱이 속에 조금 떠오르며 사라짐")(heal_frame)


# ------------------------------------------------------------ rfx_jnote2 (음표 — 칸마다 다른 음표 한 장: 4분 · 8분 · 이어진 8분 둘 · 이어진 16분 둘)

def note_head(pad, x, y, rx, ry, rot=math.radians(-22)):
    pts = [(x + math.cos(a) * rx * math.cos(rot) - math.sin(a) * ry * math.sin(rot), y + math.cos(a) * rx * math.sin(rot) + math.sin(a) * ry * math.cos(rot))
           for a in np.linspace(0, TAU, 40, endpoint=False)]
    m = pad.poly(pts)
    lit, dark, _ = bevel_light(m, ry * 0.7)
    hl = pad.ellipse(x - rx * 0.32, y - ry * 0.35, rx * 0.36, ry * 0.22)
    return m, lit, hl


def note_frame(f, S=64):
    pad = Pad(S, S)
    body = pad.zeros()
    core = pad.zeros()
    sw = 3.2

    def add_head(x, y, rx, ry):
        nonlocal body, core
        m, lit, hl = note_head(pad, x, y, rx, ry)
        body = np.maximum(body, m)
        core = np.maximum(core, (rim_of(m, 0.8) * 0.9 + lit * 0.6 + hl * 0.95) * m)

    def add_stem(x, y0, y1):
        nonlocal body, core
        st_ = pad.stroke([(x, y0), (x, y1)], sw)
        body = np.maximum(body, st_)
        core = np.maximum(core, pad.stroke([(x - 0.5, y0), (x - 0.5, y1)], 1.0, a0=0.9))

    if f in (0, 1):
        hx, hy = S * 0.37, S * 0.75
        add_head(hx, hy, S * 0.155, S * 0.105)
        sx = hx + S * 0.13
        add_stem(sx, hy - 2, S * 0.13)
        if f == 1:
            fl = bez((sx, S * 0.13), (sx + S * 0.2, S * 0.24), (sx + S * 0.12, S * 0.5), 20, p3=None)
            flag = pad.poly(taper_poly(fl, lambda t: 2.6 * (1 - t) ** 0.6 + 0.3))
            body = np.maximum(body, flag)
            core = np.maximum(core, pad.stroke(fl[:16], 1.0, 0.4, a0=0.9, a1=0.3))
    else:
        h1 = (S * 0.27, S * 0.79)
        h2 = (S * 0.69, S * 0.71)
        for hx, hy in (h1, h2):
            add_head(hx, hy, S * 0.13, S * 0.09)
        s1, s2 = h1[0] + S * 0.11, h2[0] + S * 0.11
        y1t, y2t = S * 0.23, S * 0.15
        add_stem(s1, h1[1] - 2, y1t)
        add_stem(s2, h2[1] - 2, y2t)
        for kb in range(1 if f == 2 else 2):
            o = kb * S * 0.12
            beam = pad.poly([(s1 - sw / 2, y1t + o - 2.6), (s2 + sw / 2, y2t + o - 2.6), (s2 + sw / 2, y2t + o + 2.6), (s1 - sw / 2, y1t + o + 2.6)])
            body = np.maximum(body, beam)
            core = np.maximum(core, pad.stroke([(s1, y1t + o - 1.2), (s2, y2t + o - 1.2)], 0.9, a0=0.9) * beam + rim_of(beam, 0.6) * 0.6 * beam)
    # 반짝임 하나 (칸마다 다른 자리)
    sx_, sy_ = [(0.72, 0.3), (0.2, 0.25), (0.5, 0.42), (0.15, 0.45)][f]
    core = np.maximum(core, star4(pad, S * sx_, S * sy_, 5.0, 0.85, rot=0.0, thin=0.16, diag=0.0))
    return finish(pad, body, core, halo=((0.9, 0.5), (2.2, 0.36), (4.5, 0.2)), body_k=0.9, core_bloom=((0.9, 0.3), (2.0, 0.22)), win=2.5)


sprite("rfx_jnote2", "rotate_xyz", 64, 64, 4, 4, job="priest", zoom=3,
       desc="음표 (칸마다 다른 한 장): 4분 · 8분 (휜 꼬리) · 이어진 8분 둘 · 이어진 16분 둘 — 비탈 빛 · 반사가 있는 머리 · 기둥 빛 줄 · 반짝임")(note_frame)


# ------------------------------------------------------------ rfx_jstigma2 (성흔 — 빛나는 십자가와 뒤에서 천천히 도는 빛살, 이어 돌림: 4 장 = 90°)

def stigma_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    cyx = S * 0.34
    core = pad.zeros()
    glow = pad.zeros()
    rr, th = polar(pad, c, S * 0.38)
    rot = (math.pi / 2) * f / 4
    # 뒤의 빛살 여덟 (긴 것 · 짧은 것이 번갈아 — 90° 마다 같은 꼴이라 4 장에 이어짐)
    for i in range(8):
        a = rot + i * math.pi / 4
        L = S * (0.46 if i % 2 == 0 else 0.3)
        p0 = (c, S * 0.38)
        p1 = (c + math.cos(a) * L, S * 0.38 + math.sin(a) * L)
        glow = np.maximum(glow, lens(pad, p0, p1, 10.0 if i % 2 == 0 else 7.0, 0.55, power=0.8, skew=0.15))
        core = np.maximum(core, lens(pad, p0, p1, 2.6 if i % 2 == 0 else 1.8, 0.5, power=0.7, skew=0.15))
    core = np.maximum(core, gauss(rr - S * 0.27, 0.6) * 0.55)
    glow = np.maximum(glow, gauss(rr - S * 0.27, 2.4) * 0.45)
    # 십자가 (세로가 긺): 비탈 빛 · 밝은 속 줄 · 팔 끝이 조금 넓어짐
    w = S * 0.105
    vbar = pad.poly(taper_poly([(c, S * 0.12), (c, S * 0.86)], lambda t: w / 2 * (1.0 + 0.25 * max(0.0, 1 - t * 8) + 0.18 * max(0.0, t * 6 - 5))))
    hbar = pad.poly(taper_poly([(S * 0.27, cyx), (S * 0.73, cyx)], lambda t: w / 2 * (1.0 + 0.25 * max(0.0, 1 - t * 6) + 0.25 * max(0.0, t * 6 - 5))))
    cross = np.maximum(vbar, hbar)
    lit, dark, _ = bevel_light(cross, w * 0.35)
    occl = 1 - cross
    core = core * occl
    glow = np.maximum(glow * occl, cross * (0.9 + 0.1 * lit))
    inner = np.maximum(pad.stroke([(c, S * 0.16), (c, S * 0.82)], w * 0.42), pad.stroke([(S * 0.31, cyx), (S * 0.69, cyx)], w * 0.42))
    core = np.maximum(core, np.maximum(inner, rim_of(cross, 0.8) * 0.85 + lit * 0.4) * cross)
    gem = pad.poly([(c, cyx - 7), (c + 6, cyx), (c, cyx + 7), (c - 6, cyx)])
    core = np.maximum(core, gem)
    tw = [1.0, 0.7, 0.85, 0.6][f]
    core = np.maximum(core, star4(pad, c, cyx, 13 + 6 * tw, 0.95, rot=math.pi / 4 * 0, thin=0.1, diag=0.4))
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.4), (6.5, 0.24)), body_k=0.88, core_bloom=((1.2, 0.35), (3.0, 0.26)), win=4.0)


sprite("rfx_jstigma2", "rotate_xyz", 128, 128, 4, 4, job="priest", zoom=0,
       desc="성흔 (이어 돌림 — 4 장에 90°): 팔 끝이 넓어지는 비탈 십자가 (밝은 속 줄 · 가운데 보석 · 반짝임) · 뒤에서 도는 긴 · 짧은 빛살 여덟 · 둥근 빛 테")(stigma_frame)



# ------------------------------------------------------------ rfx_jchain2 (빛의 사슬 — 방향으로 누움, 조각을 이어 붙임 · 이어 돌림)
# 고리 자리: 32 점마다 — 짝수 자리 = 모서리로 본 고리 (가로 막대 — 칸 양 끝은 반쪽 막대라 이어 붙인 조각끼리 한 줄로 겹침), 홀수 자리 = 옆에서 본 고리 (타원 테)

def chain_frame(f, W=256, H=32):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    st = Stack(pad)
    for k in range(0, 9, 2):
        x = 32.0 * k
        bar = pad.stroke([(x - 18, cy), (x + 18, cy)], 5.2)
        lit, dark, _ = bevel_light(bar, 1.6)
        bc = rim_of(bar, 0.6) * 0.8 + lit * 0.7
        bc = np.maximum(bc, pad.stroke([(x - 16, cy - 0.9), (x + 16, cy - 0.9)], 0.8, a0=0.9) * bar)
        st.over(bar, 0.78 + 0.15 * lit - 0.2 * dark, bc * bar)
    for k in range(1, 9, 2):
        x = 32.0 * k
        rx, ry = 19.0, 8.6
        R = np.sqrt(((X - x) / rx) ** 2 + ((Y - cy) / ry) ** 2)
        dist = (R - 1) * np.minimum(rx, ry * (1 + 0.6 * np.abs((X - x) / rx)))
        ring = band_cov(dist, 1.65)
        lit = clamp01(-(X - x) / rx * 0.5 - (Y - cy) / ry * 0.7) * ring
        hl = gauss(dist + 0.6, 0.45) * clamp01(-(Y - cy) / ry + 0.2) * ring
        st.over(ring, 0.8 + 0.15 * lit, np.maximum(rim_of(ring, 0.5) * 0.6, np.maximum(lit * 0.8, hl)) * ring)
    # 따라 흐르는 빛 (한 장에 64 점 — 4 장 이어 돌림)
    x = (f / 4 + 0.125) * W
    sweep = gauss(X - x, 9.0) * clamp01(st.body * 1.4)
    st.add_core(sweep * 0.95)
    st.add_core(star4(pad, x, cy, 11.0, 1.0, rot=0.0, thin=0.12, diag=0.0))
    st.body = np.maximum(st.body, pad.radial(x, cy, 15, 1.4) * 0.9)
    vf = smooth(0.5, 4.5, np.minimum(Y, H - Y))
    core1 = pad.down(st.core * vf)
    glow1 = obj_glow(pad, st.body * vf, core1, halo=((0.9, 0.45), (2.0, 0.3)), body_k=1.0, core_bloom=((0.9, 0.3), (2.2, 0.22)))
    hf = win_border((H, W), 2.5)
    return glow1 * hf, core1 * hf


sprite("rfx_jchain2", "lookat_direction", 256, 32, 4, 2, job="paladin", zoom=0,
       desc="빛의 사슬 (이어 돌림 — 조각을 이어 붙임): 비탈 빛 쇠고리 (옆에서 본 타원 테 넷 · 모서리로 본 막대 다섯, 양 끝은 반쪽이라 이어짐) · 따라 흐르는 빛과 반짝임")(chain_frame)


# ------------------------------------------------------------ rfx_jaura2 (몸에서 솟는 빛 — 발밑이 넓고 위로 가는 불꽃 줄기가 위로 흐름, 이어 돌림)

def aura_frame(f, W=128, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    ph = f / 8
    scroll = 8.0 * f  # 결이 위로 흐름 (주기 64 점 · 8 장)
    ground = H - 10.0
    core = pad.zeros()
    glow = pad.zeros()
    n_big = fbm(X / 16.0, (Y + scroll) / 16.0, seed=61, octaves=3, py=4)
    n_fine = fbm(X / 8.0 + 3.0, (Y + scroll * 2) / 8.0, seed=62, octaves=2, py=8)
    rnd = random.Random(8)
    for k in range(7):
        base = (k - 3) * W * 0.11 + rnd.uniform(-3, 3)
        hgt = H * rnd.uniform(0.5, 0.86)
        wd = W * rnd.uniform(0.14, 0.2) * (1.0 - abs(k - 3) * 0.08)
        off = rnd.uniform(0, TAU)
        t = np.clip((ground - Y) / hgt, 0, 1.2)
        sway = np.sin((t * 2.0 - ph) * TAU + off) * W * 0.05 * t + n_big * 6 * t
        xc = cx + base * (1 - 0.45 * t) + sway
        hw = wd * (1 - np.clip(t, 0, 1)) ** 0.7 * (0.78 + 0.22 * np.sin((t * 3 - ph) * TAU + off * 2)) * (1 + 0.3 * n_fine * t)
        dx = np.abs(X - xc)
        tongue = clamp01((hw - dx) / np.maximum(hw * 0.4, 0.8)) * (t <= 1) * (Y < ground + 4)
        a = 0.9 if k % 2 == 0 else 0.72
        glow = np.maximum(glow, tongue * a)
        inner = clamp01((hw * 0.58 - dx) / np.maximum(hw * 0.3, 0.6)) * (t <= 1) * (1 - np.clip(t, 0, 1)) ** 0.4
        core = np.maximum(core, inner * (0.78 + 0.22 * n_fine) * a)
    # 발밑 빛 고리 (납작한 타원) · 오르는 불티
    ell = clamp01(1 - ((X - cx) / (W * 0.42)) ** 2 - ((Y - ground) / 7.0) ** 2)
    glow = np.maximum(glow, ell ** 0.8 * 0.8)
    core = np.maximum(core, gauss(np.sqrt(((X - cx) / (W * 0.4)) ** 2 + ((Y - ground) / 6.0) ** 2) - 1.0, 0.08) * 0.7)
    rs = random.Random(18)
    for i in range(10):
        q = (rs.random() + ph) % 1.0
        x = cx + rs.uniform(-40, 40) + 5 * math.sin(q * 7 + i)
        y = ground - 10 - (H * 0.75) * q
        al = math.sin(math.pi * q) ** 1.2 * 0.9
        core = np.maximum(core, pad.stroke([(x, y + 4), (x, y)], 0.3, 1.4, a0=0.0, a1=al))
        glow = np.maximum(glow, pad.disc_field(x, y, 2.4, soft=1.2) * al * 0.6)
    vg = clamp01(0.38 + 0.8 * (Y / H)) * smooth(2, 30, Y) * smooth(H - 0.5, H - 6, Y)
    return finish(pad, glow * vg, core * vg, halo=((1.3, 0.45), (3.5, 0.34), (8.0, 0.2)), body_k=0.85, core_bloom=((1.2, 0.3), (3.0, 0.22)), win=3.0)


sprite("rfx_jaura2", "lookat_y", 128, 256, 8, 8, job="warrior", zoom=0,
       desc="몸에서 솟는 빛 (이어 돌림 — 결이 8 장에 한 주기로 오름): 발밑이 넓고 위로 가늘어지는 불꽃 혀 일곱 (흐르는 결 · 밝은 속불) · 발밑 빛 고리 · 오르는 불티")(aura_frame)


# ------------------------------------------------------------ rfx_jpetal2 (꽃 · 꽃잎 — 0 ~ 3 다섯 꽃잎 꽃이 돌며 숨 쉼 (이어 돌림: 4 장 = 72°), 4 ~ 7 꽃잎 하나가 팔랑임 (이어 돌림))

def petal_pts(cx, cy, ang, L, W, base=0.0, notch=0.12):
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    left, right = [], []
    n = 18
    for i in range(n + 1):
        t = i / n
        w = W * (math.sin(math.pi * min(1.0, 0.06 + t * 0.94)) ** 0.62) * (0.42 + 0.58 * t ** 0.8)
        d = base + L * t
        left.append((cx + ux * d + px * w, cy + uy * d + py * w))
        right.append((cx + ux * d - px * w, cy + uy * d - py * w))
    tip = (cx + ux * (base + L * (1 - notch)), cy + uy * (base + L * (1 - notch)))
    return left + [tip] + right[::-1]


def petal_frame(f, S=64):
    pad = Pad(S, S)
    c = S / 2
    body = pad.zeros()
    core = pad.zeros()
    if f < 4:
        rot = math.radians(f * 18) - math.pi / 2
        k = 1 + 0.05 * math.sin(f * math.pi / 2)
        R = S * 0.355 * k
        for i in range(5):
            a = rot + i * TAU / 5
            m = pad.poly(petal_pts(c, c, a, R, S * 0.15, S * 0.03))
            vein = pad.stroke([(c + math.cos(a) * 5, c + math.sin(a) * 5), (c + math.cos(a) * R * 0.75, c + math.sin(a) * R * 0.75)], 0.9, 0.3, a0=0.8, a1=0.3) * m
            tipl = m * clamp01((np.sqrt((pad.grid()[0] - c) ** 2 + (pad.grid()[1] - c) ** 2) - R * 0.55) / (R * 0.45))
            body = np.maximum(body, m)
            core = np.maximum(core, np.maximum(rim_of(m, 0.7) * 0.85, np.maximum(vein, tipl * 0.55)))
        core = np.maximum(core, pad.disc_field(c, c, S * 0.085))
        body = np.maximum(body, pad.disc_field(c, c, S * 0.12))
        for i in range(5):
            a = rot + (i + 0.5) * TAU / 5
            x, y = c + math.cos(a) * S * 0.15, c + math.sin(a) * S * 0.15
            core = np.maximum(core, pad.stroke([(c + math.cos(a) * 3, c + math.sin(a) * 3), (x, y)], 0.6, a0=0.7))
            core = np.maximum(core, pad.disc_field(x, y, 1.5))
    else:
        g = f - 4
        flip = math.cos(g * math.pi / 2)
        wk = max(0.2, abs(flip))
        a = math.radians(-58 + 22 * math.sin(g * math.pi / 2))
        L = S * 0.7
        x0, y0 = c - math.cos(a) * L / 2, c - math.sin(a) * L / 2
        m = pad.poly(petal_pts(x0, y0, a, L, S * 0.17 * wk, 0.0, notch=0.1))
        vein = pad.stroke([(x0 + math.cos(a) * 3, y0 + math.sin(a) * 3), (x0 + math.cos(a) * L * 0.78, y0 + math.sin(a) * L * 0.78)], 1.0, 0.3, a0=0.85, a1=0.3) * m
        body = m * (0.75 + 0.25 * wk)
        core = np.maximum(rim_of(m, 0.7) * 0.85, vein)
        core = np.maximum(core, m * (0.45 if flip > 0 else 0.2))
    return finish(pad, body, core, halo=((0.8, 0.45), (2.0, 0.34), (4.0, 0.2)), body_k=0.9, core_bloom=((0.8, 0.3), (2.0, 0.22)), win=2.5)


sprite("rfx_jpetal2", "rotate_xyz", 64, 64, 8, 8, job="assassin", zoom=0,
       desc="꽃 · 꽃잎: 0 ~ 3 끝이 패인 다섯 꽃잎 꽃 (잎맥 · 밝은 끝 · 꽃술) 이 72° 돌며 숨 쉼 (이어 돌림), 4 ~ 7 꽃잎 하나가 뒤집히며 팔랑임 (이어 돌림)")(petal_frame)


# ------------------------------------------------------------ rfx_jbreak2 (방어 깨짐 — 방패가 금이 가 두 쪽으로 갈라지며 조각이 흩어짐)

def shield_outline2(S):
    c = S / 2
    top = [(S * 0.27, S * 0.2), (c, S * 0.17), (S * 0.73, S * 0.2)]
    right = bez((S * 0.73, S * 0.2), (S * 0.74, S * 0.62), (c, S * 0.87), 16)
    left = [(S - x, y) for x, y in right[::-1]]
    return [top[0], top[1]] + right + left[1:-1]


def break_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    core = pad.zeros()
    glow = pad.zeros()
    full = shield_outline2(S)
    crack = [(c, S * 0.17), (c - 5, S * 0.33), (c + 6, S * 0.46), (c - 5, S * 0.61), (c + 3, S * 0.74), (c, S * 0.87)]
    crack_path = list(crack)
    if f <= 1:
        m = pad.poly(full)
        lit, dark, _ = bevel_light(m, 5.0)
        inner = pad.poly([(c + (x - c) * 0.8, S * 0.5 + (y - S * 0.5) * 0.8) for x, y in full])
        glow = np.maximum(glow, m * (0.62 + 0.15 * lit - 0.15 * dark + 0.12 * f))
        core = np.maximum(core, rim_of(m, 1.1) * 0.95 + lit * 0.4)
        core = np.maximum(core, rim_of(inner, 0.7) * 0.6)
        core = np.maximum(core, pad.poly([(c, S * 0.36), (c + 8, S * 0.48), (c, S * 0.6), (c - 8, S * 0.48)]) * 0.8)
        n = 3 + 3 * f
        cr = crack_path[:n]
        rnd = random.Random(3)
        if len(cr) >= 2:
            jag = []
            for a_, b_ in zip(cr, cr[1:]):
                jag += bolt_pts(rnd, a_, b_, 0.12, 2, 0.6)[:-1]
            jag.append(cr[-1])
            core = np.maximum(core, pad.stroke(jag, 1.8, 0.8, a0=1.0, a1=0.8))
            glow = np.maximum(glow, pad.stroke(jag, 5.0, 2.5, a0=0.8, a1=0.5, soft=1.5))
        if f == 1:
            core = np.maximum(core, star4(pad, c + 2, S * 0.47, S * 0.24, 1.0, thin=0.1, diag=0.45))
            glow = np.maximum(glow, pad.radial(c, S * 0.47, S * 0.26, 1.3) * 0.8)
    else:
        q = (f - 1) / 4
        dx = S * 0.12 * ease_out(q)
        dyy = S * 0.07 * q * q
        ang = 0.32 * q
        al = (1 - q ** 1.3) * 0.95 + (0.2 if f == 5 else 0)
        for sgn in (-1, 1):
            if sgn > 0:
                half = [crack_path[0]] + [p for p in full if p[0] > c + 0.01] + [crack_path[-1]] + crack_path[-2:0:-1]
            else:
                half = [crack_path[0]] + [p for p in reversed(full) if p[0] < c - 0.01] + [crack_path[-1]] + crack_path[-2:0:-1]
            moved = [(x + sgn * dx, y + dyy) for x, y in half]
            mx = sum(x for x, _ in moved) / len(moved)
            my = sum(y for _, y in moved) / len(moved)
            moved = rot_pts(moved, mx, my, sgn * ang)
            m = pad.poly(moved)
            lit, dark, _ = bevel_light(m, 4.0)
            glow = np.maximum(glow, m * (0.6 + 0.15 * lit - 0.15 * dark) * al)
            core = np.maximum(core, (rim_of(m, 1.0) * 0.95 + lit * 0.35) * al)
        rnd = random.Random(7)
        for i in range(12):
            a = rnd.uniform(0, TAU)
            dist = S * (0.06 + 0.3 * ease_out(q) * rnd.uniform(0.6, 1.0))
            x, y = c + math.cos(a) * dist, S * 0.5 + math.sin(a) * dist + 6 * q * q
            sz = rnd.uniform(2.2, 4.0) * (1 - 0.4 * q)
            pts = rot_pts([(x, y - sz), (x + sz * 0.8, y + sz * 0.6), (x - sz * 0.8, y + sz * 0.6)], x, y, rnd.uniform(0, TAU) + q * 3)
            m, b_, cc = faceted(pad, pts, a=al)
            glow = np.maximum(glow, b_)
            core = np.maximum(core, cc)
    return finish(pad, glow, core, halo=((1.1, 0.45), (2.8, 0.34), (6.0, 0.2)), body_k=0.88, core_bloom=((1.1, 0.3), (2.8, 0.22)), win=3.5)


sprite("rfx_jbreak2", "rotate_xyz", 128, 128, 6, 6, job="warrior", zoom=1,
       desc="방어 깨짐: 테 · 안쪽 테 · 가운데 마름모가 있는 비탈 빛 방패에 금이 가고 (1 번쩍) → 두 쪽으로 갈라져 돌며 처지고 · 비탈 조각이 흩어지며 사라짐")(break_frame)


# ------------------------------------------------------------ rfx_jpage2 (날리는 책장 — 펄럭이는 종이, 이어 돌림)

def page_frame(f, S=64):
    pad = Pad(S, S)
    c = S / 2
    k = 0.3 + 0.7 * abs(math.cos(f / 4 * math.pi))
    w, h = S * 0.25 * k, S * 0.33
    skew = math.sin(f / 4 * TAU) * S * 0.07
    curl = math.sin(f / 4 * TAU + 0.8) * 3.0

    def P(u, v):
        """종이 위의 자리 (u: -1 왼쪽 → 1 오른쪽, v: -1 위 → 1 아래) — 비틀림 · 말림"""
        x = c + u * w + curl * (v * v) * u
        y = c + v * h - u * skew + curl * 0.6 * u * u
        return x, y

    outline = [P(-1 + 2 * i / 12, -1) for i in range(13)] + [P(1, -1 + 2 * i / 12) for i in range(1, 13)] + \
              [P(1 - 2 * i / 12, 1) for i in range(1, 13)] + [P(-1, 1 - 2 * i / 12) for i in range(1, 12)]
    m = pad.poly(outline)
    X, Y = pad.grid()
    shade = 0.7 + 0.25 * (math.cos(f / 4 * math.pi) > 0)
    body = m * shade
    core = rim_of(m, 0.8) * 0.9
    for i in range(5):
        v = -0.42 + i * 0.27
        L = 0.75 if i % 3 != 2 else 0.45
        line = [P(-0.7 + 1.4 * L * j / 6, v) for j in range(7)]
        core = np.maximum(core, pad.stroke(line, 1.0 if k > 0.5 else 0.7, a0=0.7) * m)
    gx, gy = P(-0.45, -0.72)
    core = np.maximum(core, glyph_mask(pad, 5, gx, gy, 5.0 * k + 1, 6.0, 0.9) * m)
    # 말린 귀퉁이 (오른쪽 아래) 의 밝은 접힘
    core = np.maximum(core, pad.stroke([P(0.55, 1.0), P(1.0, 0.62)], 1.0, a0=0.8) * m)
    return finish(pad, body, core, halo=((0.8, 0.45), (2.0, 0.34), (4.0, 0.2)), body_k=0.9, core_bloom=((0.8, 0.3), (2.0, 0.22)), win=2.5)


sprite("rfx_jpage2", "rotate_xyz", 64, 64, 4, 4, job="mage", zoom=0,
       desc="날리는 책장 (이어 돌림): 비틀리며 말리는 종이 (테 · 글줄 · 첫 글자 (지어낸 각진 글자) · 접힌 귀퉁이) 가 뒤집히며 펄럭임")(page_frame)


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
