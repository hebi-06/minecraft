#!/usr/bin/env python3
"""v2c 직업 큰 베기 초승달 넷 — rfx_jslash2 (tools/job_fx_art_v2b.py) 를 직업마다 따로 그림 → tools/java_fx (rpg:rfx_jslash_*).

  python3 tools/job_fx_art_v2c_slash.py                         # 그림 · 파티클 정의를 tools/java_fx 에 씀 (java_fx_art.save)
  python3 tools/job_fx_art_v2c_slash.py --only rfx_jslash_wa    # 몇 개만 (쉼표로 여럿)
  python3 tools/job_fx_art_v2c_slash.py --preview <폴더>        # 확인용 그림도 씀 (그림마다 한 장 + slash_compare.png — rfx_jslash2 와 같은 배율로 견줌)

꼴은 rfx_jslash2 와 같음 (게임 코드는 이름 · 장 수만 바꾸면 됨): rotate_xyz · 256×128 칸 · 4 열 · 빛 장면 N 개 다음에 심지 장면 N 개 (빛 = 칸 f,
심지 = 칸 N + f — 빛은 직업 겉빛, 심지는 직업 속빛으로 칠함). 몸 범위도 같음: 바깥 날 = 같은 타원 (가운데 128, 34.56 · 반지름 116.48 × 71.68) 의
172° → 8° 호 (아래로 불룩), 띠는 가운데 쪽으로 · 왼쪽 → 아래 → 오른쪽으로 그어지고 꼬리부터 흩어짐. 타원 · 8 장의 머리 · 꼬리 자리 (SL_HEAD · SL_TAIL) 는
v2b 의 것을 그대로 가져다 씀 (rfx_jslash2 를 바꾸면 따라 바뀜). 게임 크기 식도 같음 (가로 = 2 × size, 세로 = size).
그리기도 같음 (v2 · v2b 도움 함수): 4배로 그려 LANCZOS 로 줄임 · 흰색 (밝기 = 알파) · 빛 · 심지를 같은 모양에서 그림 · 고정 시드 · 칸 가장자리 안전망.
다른 그림을 쓰지 않음 (모두 이 스크립트가 처음부터 그림 · 룬 꼴은 지어낸 각진 글자 — 실제 룬 문자가 아님).

그림 (이름 — 직업 — 장 수 · 모두 rotate_xyz 256×128 · 4 열):
- rfx_jslash_sw  검사 (발키리 — 하늘색 빛 · 은빛 심지)   12 장  가늘고 긴 초승달: 빛 세 겹 (겉 안개 · 가운데 띠 = 빛 칸, 면도날처럼 가는 밝은 날 줄 = 심지 칸)
                 · 바늘 같은 머리의 작은 반짝임. 0 ~ 6 그어짐 (머리 0.2 → 1, 처음이 빠름), 7 ~ 11 바깥으로 살짝 흘러가며 꼬리부터 옅어짐
- rfx_jslash_wa  전사 (광전사 — 핏빛 빛 · 주황 심지)      8 장  굵고 묵직한 도끼 자국: 뒤로 찢긴 안쪽 가장자리 · 가닥으로 풀리는 꼬리 · 바깥 날에서 튀는
                 핏방울 · 조각 · 뜨거운 머리. 0 ~ 3 그어짐, 4 ~ 7 꼬리부터 흩어짐 (rfx_jslash2 와 같은 때)
- rfx_jslash_sb  마검사 (룬 — 보라 빛 · 청록 심지)        8 장  칼빛 둘: 빛 칸 = 바깥의 넓은 초승달, 심지 칸 = 틈을 두고 안쪽의 가는 초승달 + 그 둘레에
                 떠오르는 각진 룬 불티 (지어낸 글자) · 각진 머리 번쩍. 0 ~ 3 그어짐, 4 ~ 7 꼬리부터 흩어짐
- rfx_jslash_rp  사신 (니플헤임 — 짙은 청록 빛 · 청록 심지) 8 장  큰 낫 초승달: 깊게 휜 굵은 띠 · 휘두름 끝에서 안으로 갈고리처럼 휜 날 끝 (호를 0.18 더 감)
                 · 바깥 날에서 떨어져 뒤로 끌리는 혼불 혀. 0 ~ 3 그어짐 (3 에서 갈고리 끝까지), 4 ~ 7 꼬리부터 흩어짐
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_fx_art_v2 import (BG, COLORS, SS, TAU, Pad, Sprite, clamp01, glyph_mask, label, preview_sprite, run_frames,  # noqa: E402
                           save_sprite, smooth, star_pts)
from job_fx_art_v2b import (SL_FADE, SL_HEAD, SL_TAIL, SL_WK, crescent_prof, fbm, finish, gauss, old_cells, slash_fields,  # noqa: E402
                            slash_P, slash_T, star4, streak, swin, tint_comp_scaled)

try:
    from scipy import ndimage as _ndi
except ImportError:
    _ndi = None

W, H = 256, 128


# ============================================================ 호 도움 (v2b rfx_jslash2 와 같은 타원)

_UT = np.linspace(-0.25, 1.35, 1601)
_PT = np.array([slash_P(float(x)) for x in _UT])
_AT = np.concatenate([[0.0], np.cumsum(np.hypot(np.diff(_PT[:, 0]), np.diff(_PT[:, 1])))])
_AT -= np.interp(0.0, _UT, _AT)


def arc_px(u):
    """호 위의 자리 u → 호를 따라 간 길이 (칸 점 · u = 0 에서 0)"""
    return np.interp(u, _UT, _AT)


_F = {}


def fields(pad):
    """(u — 바깥 날 호 위의 자리 0 ~ 1, d — 바깥 날에서 안쪽으로의 거리 · 칸 점, A — 호를 따라 간 길이 · 칸 점) — 4배 그림판"""
    key = (pad.W, pad.H)
    if key not in _F:
        u, d = slash_fields(pad)
        _F[key] = (u.astype(np.float32), d.astype(np.float32), arc_px(u).astype(np.float32))
    return _F[key]


def out_n(u):
    """바깥 (불룩한 쪽) 노멀 — 머리 쪽 방향을 반시계로 90° (화면 y 는 아래)"""
    tx, ty = slash_T(u)
    return -ty, tx


def head_frame(u, heads, lead=0.0):
    """머리가 u (+ lead) 를 처음 지나는 장"""
    for f, h in enumerate(heads):
        if h >= u + lead:
            return f
    return len(heads)


_JAG = {}


def jag1d(x, seed, octaves=3, gain=0.55, lac=2.3):
    """꺾인 1차원 노이즈 (-1 ~ 1 쯤 · 격자 점 사이가 곧은 줄 — 찢긴 가장자리). x = 격자 단위"""
    out = np.zeros(np.shape(x), np.float32)
    amp, fr, tot = 1.0, 1.0, 0.0
    for o in range(octaves):
        key = (seed, o)
        if key not in _JAG:
            _JAG[key] = np.random.RandomState(seed * 7 + o * 131).uniform(-1, 1, 12000).astype(np.float32)
        kn = _JAG[key]
        xs = np.clip(np.asarray(x, np.float64) * fr + 400.0, 0, len(kn) - 2)
        i0 = np.floor(xs).astype(np.int64)
        t = (xs - i0).astype(np.float32)
        out = out + amp * (kn[i0] * (1 - t) + kn[i0 + 1] * t)
        tot += amp
        amp *= gain
        fr *= lac
    return out / tot


def inside_k(x, y, m=8.0):
    """칸 가장자리에 다가가면 0 (불티가 잘려 보이지 않게)"""
    return float(smooth(3.0, m, min(x, W - x, y, H - y)))


_XS = np.arange(W, dtype=np.float32) + 0.5
SIDE = smooth(1.0, 11.0, np.minimum(_XS, W - _XS)).astype(np.float32)[None, :]


def side_soft(g):
    """빛 칸의 무리가 칸 왼쪽 · 오른쪽 끝 (호 끝 x 13 · 243 보다 바깥) 에서 잘리지 않고 스르르 사라지게"""
    return g * SIDE


def ang_of(vx, vy):
    return math.atan2(vy, vx)


def unit(vx, vy):
    L = math.hypot(vx, vy) or 1.0
    return vx / L, vy / L


# ============================================================ rfx_jslash_sw — 검사 (가늘고 긴 초승달 · 빛 세 겹 · 머리의 작은 반짝임, 12 장)

SW_N = 12
SW_HEAD = [0.2, 0.4, 0.57, 0.72, 0.85, 0.95, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]
SW_TAIL = [0.0, 0.0, 0.0, 0.0, 0.02, 0.07, 0.14, 0.24, 0.36, 0.5, 0.64, 0.79]
SW_WK = [0.84, 0.94, 1.0, 1.0, 1.0, 1.0, 1.0, 0.95, 0.88, 0.8, 0.7, 0.58]
SW_FADE = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.94, 0.84, 0.68, 0.48, 0.28]
SW_DRIFT = [0.0] * 7 + [0.9, 1.8, 2.7, 3.6, 4.5]   # 사라지며 바깥으로 흐르는 거리 (칸 점)


SW_PA, SW_PB = 1.3, 0.75
SW_PN = (SW_PA / (SW_PA + SW_PB)) ** SW_PA * (SW_PB / (SW_PA + SW_PB)) ** SW_PB


def sw_prof(s):
    """가늘고 긴 초승달 굵기 (꺾임 없는 매끈한 꼴): 꼬리에서 천천히 굵어져 0.63 에서 가장 굵고, 머리 쪽으로 바늘 끝처럼 모임"""
    s = np.clip(s, 0.0, 1.0)
    return s ** SW_PA * (1 - s) ** SW_PB / SW_PN


def sw_frame(f):
    pad = Pad(W, H)
    u, d0, A = fields(pad)
    head, tail, wk, fade, dr = SW_HEAD[f], SW_TAIL[f], SW_WK[f], SW_FADE[f], SW_DRIFT[f]
    d = d0 + dr
    s = (u - tail) / (head - tail)
    on = (s > 0) & (s < 1)
    prof = sw_prof(s) * on
    live = prof > 0.004
    wc, wg = 10.0 * wk * prof, 17.0 * wk * prof
    outer = clamp01((d + 0.1) * SS + 0.5)
    ends = smooth(-0.015, 0.05, s) * smooth(1.0, 0.99, s)
    # ---- 심지: 면도날 줄 (바깥 날) · 날에서 안으로 흐려지는 은빛 몸 · 가는 속 줄
    razor = gauss(d - 0.2, 0.38 + 0.5 * prof) * ends * (0.62 + 0.38 * np.sqrt(prof))
    tc = np.clip(d / np.maximum(wc, 0.3), 0, 1)
    body = outer * smooth(wc + 0.8, wc - 2.2, d) * live * (1 - tc) ** 1.8 * 0.66
    inner = gauss(d - 0.6 * wc, 0.3 + 0.12 * prof) * swin(s, 0.18, 0.9, 0.18, 0.08) * live * 0.42
    core = np.maximum.reduce([razor, body, inner])
    # ---- 빛: 가운데 띠 (안쪽으로 옅어짐) · 겉 안개
    tg = np.clip(d / np.maximum(wg, 0.3), 0, 1)
    band = smooth(-2.2, 0.5, d) * smooth(wg + 2.0, wg - 4.0, d) * live * (0.86 - 0.42 * tg ** 1.25)
    haze = 0.26 * np.exp(-np.abs(d - wg * 0.3) / (5.0 + 0.3 * wg)) * prof ** 0.55 * ends
    glow = np.maximum(band, haze)
    # ---- 바람 머리카락 줄 (날 바깥 3 점 · 머리 뒤 반쯤)
    ek = [0.5, 0.75, 0.75, 0.65, 0.5, 0.35, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0][f]
    if ek > 0:
        m = swin(s, 0.5, 0.97, 0.22, 0.04) * on * ek
        core = np.maximum(core, gauss(d + 3.4, 0.33) * m * 0.42)
        glow = np.maximum(glow, gauss(d + 3.4, 1.3) * m * 0.22)
    # ---- 머리의 반짝임 (그어지는 동안): 날 끝의 큰 별 (날 방향 십자) + 머리 뒤에서 깜빡이는 작은 별
    if f <= 6:
        hu = head - (0.006 if head < 1 else 0.016)
        hx, hy = slash_P(hu, inward=0.9 + dr)
        tx, ty = slash_T(hu)
        rr = min([9.0, 11.0, 11.5, 11.0, 10.5, 10.0, 12.5][f], (W - 6 - hx) * 1.3)
        core = np.maximum(core, star4(pad, hx, hy, rr, 1.0, rot=ang_of(tx, ty), thin=0.07, diag=0.36))
        glow = np.maximum(glow, pad.radial(hx, hy, 0.7 * rr + 3, 1.6) * 0.85)
        rnd = random.Random(500 + f)
        for i in range(3):
            uu = head - rnd.uniform(0.012, 0.075)
            if uu <= tail + 0.02:
                continue
            x, y = slash_P(uu, inward=rnd.uniform(-3.0, 5.0) + dr)
            r = rnd.uniform(2.4, 4.2)
            core = np.maximum(core, star4(pad, x, y, r, rnd.uniform(0.7, 1.0), rot=rnd.uniform(0, 0.8), thin=0.14, diag=0.0))
            glow = np.maximum(glow, pad.disc_field(x, y, r * 0.6, soft=1.5) * 0.6)
    # ---- 꼬리가 흩어지는 빛 가루 (고운 점 · 가끔 작은 반짝임)
    if tail > 0:
        rnd = random.Random(520)
        for i in range(60):
            ui = rnd.uniform(0.0, 0.99)
            di = rnd.uniform(-1.0, 7.0)
            a = rnd.uniform(0.4, 1.0)
            fall = rnd.uniform(1.5, 5.0)
            if ui >= tail:
                continue
            q = (tail - ui) / 0.36
            if q >= 1:
                continue
            x, y = slash_P(ui, inward=di * wk - dr - fall * q)
            a *= (1 - q) ** 1.6
            if i % 8 == 0:
                core = np.maximum(core, star4(pad, x, y, 2.8 * (1 - 0.4 * q), a, rot=0.3 * i, thin=0.16, diag=0.0))
            else:
                core = np.maximum(core, pad.disc_field(x, y, 0.55 + 0.3 * (i % 3) / 2) * a)
            glow = np.maximum(glow, pad.radial(x, y, 2.6, 1.6) * a * 0.5)
    core = core * fade
    glow = glow * fade
    g1, c1 = finish(pad, glow, core, halo=((1.6, 0.5), (4.5, 0.42), (11.0, 0.3)), body_k=0.9, core_bloom=((1.2, 0.4), (3.5, 0.3)), win=4.0)
    return side_soft(g1), c1


# ============================================================ rfx_jslash_wa — 전사 (굵고 묵직한 도끼 자국 · 찢긴 가장자리 · 핏방울, 8 장)

WA_WK = [0.95, 1.0, 1.0, 1.0, 0.94, 0.85, 0.74, 0.6]
WA_FADE = [1.0, 1.0, 1.0, 1.0, 0.97, 0.9, 0.76, 0.52]
WA_WC, WA_WG = 33.0, 41.0
WA_LEAN = 0.6   # 찢긴 톱니가 안쪽으로 갈수록 꼬리 쪽으로 기우는 정도 (호 길이 / 깊이)
_AG = np.arange(-80.0, 460.0, 0.25)


def _wa_rag():
    """안쪽 가장자리 몫 (호 길이 _AG 위) — 꺾인 노이즈 + 꼬리 쪽이 뾰족한 톱니 (찢긴 천)"""
    rnd = random.Random(41)
    base = 0.78 + 0.1 * jag1d(_AG / 34.0, 41, octaves=2) + 0.05 * jag1d(_AG / 6.0, 43, octaves=2)
    teeth = np.zeros_like(_AG)
    a = -70.0
    while a < 450:
        if rnd.random() < 0.2:    # 가늘고 긴 가닥
            ln, dep, pw = rnd.uniform(16, 28), rnd.uniform(0.32, 0.5), 2.2
        else:                      # 뭉툭하게 찢긴 덩이
            ln, dep, pw = rnd.uniform(8, 18), rnd.uniform(0.12, 0.32), rnd.uniform(1.0, 1.4)
        x = (_AG - a) / ln
        teeth = np.maximum(teeth, np.where((x >= 0) & (x < 1), dep * (1 - np.clip(x, 0, 1)) ** pw, 0))
        a += ln * rnd.uniform(0.7, 1.1)
    # 물어뜯긴 듯 오목하게 빠진 자리
    bites = np.zeros_like(_AG)
    a = rnd.uniform(20, 50)
    while a < 450:
        w = rnd.uniform(5, 10)
        bites = np.maximum(bites, rnd.uniform(0.12, 0.22) * np.clip(1 - ((_AG - a) / w) ** 2, 0, 1) ** 0.5)
        a += rnd.uniform(35, 70)
    return (base + teeth - bites).astype(np.float32)


WA_RAG = _wa_rag()
_r = random.Random(42)
WA_NOTCH = []   # 찢긴 홈 (호 길이, 꼭지 깊이 몫, 안쪽 끝 반폭 (칸 점), 기울기)
_a = _r.uniform(30, 50)
while _a < 280:
    WA_NOTCH.append((_a, _r.uniform(0.3, 0.55), _r.uniform(2.0, 3.4), _r.uniform(0.6, 1.0)))
    _a += _r.uniform(40, 70)


def wa_prof(s):
    return crescent_prof(s, tip=0.085, rise=0.42)


def wa_frame(f):
    pad = Pad(W, H)
    u, d, A = fields(pad)
    head, tail, wk, fade = SL_HEAD[f], SL_TAIL[f], WA_WK[f], WA_FADE[f]
    s = (u - tail) / (head - tail)
    on = (s > 0) & (s < 1)
    prof = wa_prof(s) * on
    live = prof > 0.004
    lean = WA_LEAN * (0.6 + 0.8 * (0.5 + 0.5 * jag1d(A / 45.0, 61, octaves=1)))   # 자리마다 다르게 기욺
    rag = np.interp(A + lean * np.maximum(d, 0), _AG, WA_RAG).astype(np.float32)
    Es = WA_WC * wk * prof               # 찢기지 않은 띠 굵기 (속 결 · 틈 · 밝기는 이것을 따라 매끈하게)
    E = Es * rag                         # 심지 띠 안쪽 끝 (찢김 — 윤곽만)
    Eg = WA_WG * wk * prof * (0.2 + 0.8 * rag)
    outer = clamp01((d + 0.12) * SS + 0.5)
    t = d / np.maximum(E, 0.3)
    ts = d / np.maximum(Es, 0.3)
    # ---- 찢긴 홈 (안쪽에서 바깥 날 쪽으로 · 뒤로 기욺 — V 꼴)
    cut = np.zeros_like(d)
    for ak, tk, hwk, lean in WA_NOTCH:
        dd = (t - tk) / (1 - tk)
        ac = ak - lean * (d - tk * E)
        hw = hwk * np.clip(dd, 0, 1.5) ** 1.2
        cut = np.maximum(cut, clamp01((hw - np.abs(A - ac)) * SS + 0.5) * (dd > 0))
    # ---- 꼬리가 가닥으로 풀림 (꼬리 쪽에서 띠를 가로지르는 틈이 벌어짐)
    fray = smooth(0.42, 0.04, s)
    for tk, sd in ((0.3, 3), (0.55, 5), (0.78, 7)):
        tj = tk + 0.035 * fbm(A / 22.0, np.float32(sd), seed=sd, octaves=1)
        gw = 0.075 * fray * Es
        cut = np.maximum(cut, clamp01((gw - np.abs(ts - tj) * Es) * SS + 0.5) * (fray > 0.01))
    band = outer * clamp01((E - d) * SS + 0.5) * live * (1 - cut)
    # ---- 심지: 뜨거운 날 (굵고 밝음) · 바깥 날 또렷한 줄 · 거친 결 (호를 따라 늘어진 노이즈) · 어두운 홈 줄 — 안쪽 반은 빛 (핏빛) 만
    grit = fbm(A / 26.0, d / 3.2, seed=91, octaves=3)
    hot = outer * np.exp(-np.maximum(d, 0) / (1.4 + 2.8 * prof)) * band
    crisp = outer * gauss(np.maximum(d, 0), 0.8 + 0.5 * prof) * smooth(-0.01, 0.04, s) * smooth(1.0, 0.985, s) * live
    body = band * (1 - smooth(0.08, 0.72, ts)) ** 0.9 * clamp01(0.78 + 0.45 * grit)
    groove = np.zeros_like(d)
    for tk, sd in ((0.36, 13), (0.55, 17)):
        groove = np.maximum(groove, gauss(ts - tk - 0.03 * fbm(A / 26.0, np.float32(sd), seed=sd, octaves=2), 0.03) * clamp01(0.5 + 0.9 * jag1d(A / 11.0, sd + 1)))
    body = body * (1 - 0.5 * groove)
    core = np.maximum.reduce([hot, crisp, body * 0.9])
    # ---- 빛: 넓은 띠 (찢긴 끝 그대로 · 안쪽으로 조금 옅음) · 안쪽 옅은 자국
    tg = np.clip(d / np.maximum(Eg, 0.3), 0, 1)
    gband = smooth(-5.0, 0.8, d) * clamp01((Eg - d) * SS * 0.6 + 0.5) * live * (1 - 0.85 * cut) * (0.97 - 0.3 * tg ** 1.4) * clamp01(0.85 + 0.3 * grit)
    wake = 0.15 * np.exp(-np.maximum(d - Eg, 0) / 10.0) * (d > Eg * 0.5) * prof ** 0.8 * smooth(1.0, 0.9, s)
    glow = np.maximum(gband, wake)
    # ---- 바깥 공기 눌림 줄 (머리 뒤 · 굵고 흐림)
    wl = [0.6, 1.0, 1.0, 0.7, 0.35, 0.12, 0.0, 0.0][f]
    if wl > 0:
        for off, s0, a in ((-6.0, 0.5, 0.5), (-11.5, 0.68, 0.28)):
            m = swin(s, s0, 0.98, 0.2, 0.03) * on
            core = np.maximum(core, gauss(d - off, 0.9) * m * a * wl)
            glow = np.maximum(glow, gauss(d - off, 2.6) * m * a * 0.6 * wl)
    # ---- 머리: 도끼 머리가 지나간 앞 1/4 은 띠 전체가 달아오름 (흰빛에 가까운 심지) · 뜨거운 덩이 · 앞으로 튀는 불똥
    hk = [1.0, 1.0, 1.0, 0.7, 0.4, 0.2, 0.08, 0.0][f]
    if hk > 0:
        heat = band * smooth(0.7, 0.93, s) * (1 - smooth(0.35, 1.05, ts)) * hk
        core = np.maximum(core, heat * 0.95)
    fl = [0.85, 1.0, 0.9, 0.5, 0.25, 0.0, 0.0, 0.0][f]
    if fl > 0:
        hu = head - (0.02 if head < 1 else 0.045)
        hx, hy = slash_P(hu, inward=7.0)
        tx, ty = slash_T(hu)
        rr = min(15.0 * fl + 5.0, (W - 7 - hx) * 1.1)
        core = np.maximum(core, pad.radial(hx, hy, rr * 0.7, 0.8) * 0.95)
        glow = np.maximum(glow, pad.radial(hx, hy, rr * 1.2 + 6, 1.1))
        rnd = random.Random(310 + f)
        nx, ny = out_n(hu)
        for i in range(4):
            mix = rnd.uniform(0.15, 0.6)
            vx, vy = unit(tx * (1 - mix) + nx * mix, ty * (1 - mix) + ny * mix)
            r0 = rr * 0.35 + rnd.uniform(0, 3)
            r1 = r0 + rnd.uniform(6, 12) * fl
            p0, p1 = (hx + vx * r0, hy + vy * r0), (hx + vx * r1, hy + vy * r1)
            ak = fl * inside_k(*p1)
            core = np.maximum(core, streak(pad, p0, p1, rnd.uniform(1.4, 2.2), ak))
            glow = np.maximum(glow, pad.stroke([p0, p1], 1.5, 4.5, a0=0.0, a1=ak * 0.6, soft=1.0))
    # ---- 핏방울 · 조각: 머리가 지난 바깥 날에서 앞 · 바깥으로 튀고 아래로 처짐
    rnd = random.Random(303)
    for i in range(26):
        fs = rnd.choice((0, 0, 1, 1, 2, 2, 3))
        u0 = max(0.06, min(0.97, SL_HEAD[fs] - rnd.uniform(0.02, 0.2)))
        mix = rnd.uniform(0.4, 0.85)
        sp = rnd.uniform(5.0, 14.0)
        grav = rnd.uniform(1.2, 2.8)
        life = rnd.uniform(2.4, 3.6)
        age = f - fs + rnd.uniform(0.2, 0.8)
        kind = i % 3   # 0 = 조각, 1 · 2 = 핏방울
        r = rnd.uniform(1.8, 3.0) if kind else rnd.uniform(2.0, 3.3)
        spin = rnd.uniform(-1.3, 1.3)
        base_a = rnd.uniform(0, TAU)
        jit = [rnd.uniform(-0.25, 0.25) for _ in range(5)]
        rad = [rnd.uniform(0.6, 1.0) for _ in range(5)]
        if age <= 0 or age >= life:
            continue
        tx, ty = slash_T(u0)
        nx, ny = out_n(u0)
        vx, vy = unit(tx * mix + nx * (1 - mix), ty * mix + ny * (1 - mix))
        x0, y0 = slash_P(u0, inward=-1.0)
        x = x0 + vx * sp * age
        y = y0 + vy * sp * age + 0.5 * grav * age * age
        wx, wy = unit(vx * sp, vy * sp + grav * age)
        a = (1 - age / life) ** 1.1 * fade * inside_k(x, y, 7.0)
        if a <= 0.02:
            continue
        rk = r * (1 - 0.3 * age / life)
        if kind:   # 핏방울: 둥근 머리 + 뒤로 가늘어지는 꼬리
            tl = rk * 3.2 + 2.0
            p0 = (x - wx * tl, y - wy * tl)
            core = np.maximum(core, streak(pad, p0, (x, y), rk * 1.9, a))
            glow = np.maximum(glow, pad.stroke([p0, (x, y)], rk * 0.8, rk * 3.0, a0=0.0, a1=a * 0.7, soft=0.8))
        else:      # 조각: 각진 돌 · 쇳조각 (돌며 날아감)
            n = 4 + (i % 2)
            pts = []
            for k in range(n):
                aa = base_a + spin * age + (k + jit[k]) * TAU / n
                pts.append((x + math.cos(aa) * rk * rad[k], y + math.sin(aa) * rk * rad[k]))
            core = np.maximum(core, pad.poly(pts, a))
            glow = np.maximum(glow, pad.disc_field(x, y, rk * 1.7, soft=1.0) * a * 0.7)
    core = core * fade
    glow = glow * fade
    g1, c1 = finish(pad, glow, core, halo=((1.4, 0.45), (3.5, 0.36), (8.0, 0.22)), body_k=0.95, core_bloom=((1.3, 0.32), (3.5, 0.24)), win=4.0)
    return side_soft(g1), c1


# ============================================================ rfx_jslash_sb — 마검사 (칼빛 둘: 바깥 넓은 초승달 = 빛 · 틈 · 안쪽 가는 초승달 = 심지 + 룬 불티, 8 장)

SB_W1, SB_W2 = 15.5, 7.0
SB_GLYPHS = (0, 1, 2, 3, 4, 5, 7, 9, 10)   # 글자처럼 읽히는 꼴 (8 · C 같은 것) 은 뺌


def sb_geom(u, head, tail, wk):
    """(p1, W1, D2, p2, W2) — 바깥 띠 굵기 W1 · 안쪽 띠 시작 D2 (= W1 + 틈) · 안쪽 띠 굵기 W2 (배열 · 수 모두)"""
    s = (u - tail) / (head - tail)
    p1 = crescent_prof(s, tip=0.14, rise=0.6) * ((s > 0) & (s < 1))
    W1 = SB_W1 * wk * p1
    gap = (2.0 + 3.2 * np.sqrt(p1)) * wk
    h2 = head - 0.035
    t2 = min(tail + 0.05, h2 - 0.02)
    s2 = (u - t2) / (h2 - t2)
    p2 = crescent_prof(s2, tip=0.2, rise=0.75) * ((s2 > 0) & (s2 < 1))
    return p1, W1, W1 + gap, p2, SB_W2 * wk * p2


def sb_frame(f):
    pad = Pad(W, H)
    u, d, A = fields(pad)
    head, tail, wk, fade = SL_HEAD[f], SL_TAIL[f], SL_WK[f], SL_FADE[f]
    s = (u - tail) / (head - tail)
    p1, W1, D2, p2, W2 = sb_geom(u, head, tail, wk)
    live1, live2 = p1 > 0.004, p2 > 0.004
    outer = clamp01((d + 0.1) * SS + 0.5)
    # ---- 빛 칸: 바깥 초승달 (바깥 날 또렷 · 안쪽으로 옅어짐 · 안쪽 끝에 가는 빛 줄)
    arc1 = outer * smooth(W1 + 0.6, W1 - 1.4, d) * live1
    glow = arc1 * (0.56 + 0.44 * np.exp(-np.maximum(d, 0) / 3.2))
    glow = np.maximum(glow, gauss(d - (W1 - 1.3), 0.55) * live1 * swin(s, 0.1, 0.95, 0.15, 0.05) * 0.85)
    # ---- 심지 칸: 안쪽 초승달 (양쪽 또렷 · 바깥쪽 1/3 에 밝은 줄) + 안쪽으로만 옅은 빛
    arc2 = clamp01((d - D2) * SS + 0.5) * clamp01((D2 + W2 - d) * SS + 0.5) * live2
    tn = np.clip((d - D2) / np.maximum(W2, 0.3), 0, 1)
    core = arc2 * (0.74 + 0.26 * gauss(tn - 0.3, 0.2))
    core = np.maximum(core, gauss(np.maximum(d - D2 - W2, 0), 1.5) * (d > D2 + W2) * live2 * 0.32 * np.sqrt(p2))
    # ---- 각진 머리 번쩍 (바깥 띠 머리의 길쭉한 마름모 별 · 안쪽 띠 머리의 작은 마름모)
    fl = [0.8, 1.0, 0.92, 0.55, 0.28, 0.0, 0.0, 0.0][f]
    if fl > 0:
        hu = head - (0.012 if head < 1 else 0.03)
        hx, hy = slash_P(hu, inward=3.0)
        tx, ty = slash_T(hu)
        rr = min(18.0 * fl + 4.0, (W - 6 - hx) * 1.25)
        core = np.maximum(core, pad.poly(star_pts(hx, hy, rr * 0.42, 4, 0.3, rot=ang_of(tx, ty), r2=rr)))
        core = np.maximum(core, pad.poly(star_pts(hx, hy, rr * 0.3, 4, 0.2, rot=ang_of(tx, ty) + math.pi / 4)) * 0.8)
        glow = np.maximum(glow, pad.radial(hx, hy, rr * 0.8 + 4, 1.4) * 0.9)
        h2u = head - 0.045
        if h2u > tail + 0.06:
            _, _, d2h, _, w2h = sb_geom(np.float64(h2u), head, tail, wk)
            x2, y2 = slash_P(h2u, inward=float(d2h + w2h * 0.5))
            r2 = 3.4 * fl + 1.2
            c2, s2 = tx, ty
            core = np.maximum(core, pad.poly([(x2 + c2 * r2 * 1.7, y2 + s2 * r2 * 1.7), (x2 - s2 * r2, y2 + c2 * r2),
                                              (x2 - c2 * r2 * 1.7, y2 - s2 * r2 * 1.7), (x2 + s2 * r2, y2 - c2 * r2)]))
    # ---- 룬 불티: 머리가 지난 안쪽 띠 둘레에 지어낸 각진 글자 (처음 장에 작은 번쩍) → 안으로 떠오르며 사라짐 (빛 칸에는 옅은 보라 무리)
    rnd = random.Random(404)
    runes = pad.zeros()
    for k in range(7):
        uk = 0.13 + 0.115 * k + rnd.uniform(-0.025, 0.025)
        gk = SB_GLYPHS[rnd.randrange(len(SB_GLYPHS))]
        rot_j = rnd.uniform(-0.25, 0.25)
        dj = rnd.uniform(-2.5, 3.5)
        size = rnd.uniform(0.9, 1.12)
        f0 = head_frame(uk, SL_HEAD, 0.06)
        age = f - f0
        if age < 0 or age > 3:
            continue
        al = [1.0, 0.88, 0.58, 0.26][age] * fade
        sc = [0.8, 1.0, 1.0, 0.94][age] * size
        _, _, d2k, p2k, w2k = sb_geom(np.float64(uk), head, tail, wk)
        base = float(d2k + w2k) if p2k > 0.004 else 22.0 * wk
        x, y = slash_P(uk, inward=max(base, 12.0) + 7.0 + dj + 3.0 * age)
        tx, ty = slash_T(uk)
        m = glyph_mask(pad, gk, x, y, 8.0 * sc, 11.0 * sc, 1.4, ang=ang_of(tx, ty) + rot_j * (1 + age))
        runes = np.maximum(runes, m * al)
        if age == 0:
            runes = np.maximum(runes, star4(pad, x, y, 4.8, 0.85 * fade, rot=0.2 * k, thin=0.12, diag=0.0))
    core = np.maximum(core, runes)
    glow = np.maximum(glow, runes * 0.45)
    # ---- 각진 불티 (작은 마름모): 안쪽 띠 머리 뒤에서 앞 · 안으로 튐
    rnd = random.Random(405)
    for i in range(16):
        fs = rnd.choice((0, 1, 1, 2, 2, 3))
        u0 = max(0.05, min(0.97, SL_HEAD[fs] - 0.05 - rnd.uniform(0.0, 0.1)))
        mix = rnd.uniform(0.4, 0.85)
        sp = rnd.uniform(5, 11)
        life = rnd.uniform(2.2, 3.4)
        age = f - fs + rnd.uniform(0.3, 0.7)
        rr = rnd.uniform(1.3, 2.3)
        if age <= 0 or age >= life:
            continue
        tx, ty = slash_T(u0)
        nx, ny = out_n(u0)
        vx, vy = unit(tx * mix - nx * (1 - mix), ty * mix - ny * (1 - mix))
        _, _, d2k, _, w2k = sb_geom(np.float64(u0), SL_HEAD[fs], SL_TAIL[fs], SL_WK[fs])
        x0, y0 = slash_P(u0, inward=float(d2k + w2k * 0.5))
        x, y = x0 + vx * sp * age, y0 + vy * sp * age
        a = (1 - age / life) ** 1.2 * fade * inside_k(x, y)
        if a <= 0.02:
            continue
        c, sn = vx, vy
        pts = [(x + c * rr * 1.8, y + sn * rr * 1.8), (x - sn * rr * 0.7, y + c * rr * 0.7), (x - c * rr * 1.8, y - sn * rr * 1.8), (x + sn * rr * 0.7, y - c * rr * 0.7)]
        core = np.maximum(core, pad.poly(pts, a))
        glow = np.maximum(glow, pad.disc_field(x, y, rr * 2.2, soft=1.2) * a * 0.5)
    core = core * fade
    glow = glow * fade
    g1, c1 = finish(pad, glow, core, halo=((1.3, 0.42), (3.4, 0.34), (8.0, 0.2)), body_k=0.92, core_bloom=((1.2, 0.18), (3.0, 0.12)), win=4.0)
    # ---- 틈: 빛 칸 무리가 두 칼빛 사이를 메우지 않게 (안쪽 띠 아래도 옅게)
    gap = clamp01((d - W1 - 0.4) * SS + 0.5) * clamp01((D2 + 0.4 - d) * SS + 0.5) * live1
    g1 = g1 * (1 - 0.86 * pad.down(gap)) * (1 - 0.55 * pad.down(arc2))
    return side_soft(clamp01(g1)), c1


# ============================================================ rfx_jslash_rp — 사신 (큰 낫 초승달 · 갈고리 날 끝 · 혼불 혀, 8 장)

RP_END = 1.18   # 호를 8° 너머로 더 감아 (위로 올라가며) 날 끝을 갈고리로
RP_HOOK = 28.0  # 갈고리 끝에서 바깥 날을 안쪽으로 미는 거리 (칸 점) — 끝이 왼쪽 (안쪽) 을 향함
RP_HEAD = [h * RP_END for h in SL_HEAD]
RP_TAIL = [t * RP_END for t in SL_TAIL]
RP_WK = [0.92, 1.0, 1.0, 0.98, 0.88, 0.76, 0.63, 0.5]
RP_FADE = SL_FADE
RP_WC, RP_WG = 34.0, 42.0


def rp_hook(u):
    return RP_HOOK * np.clip((u - 0.95) / (RP_END - 0.95), 0, 1) ** 2.4


def rp_prof(s, u):
    """낫 굵기 (꺾임 없이 매끈): 꼬리에서 빨리 굵어진 큰 배 · 그어지는 머리 쪽은 짧게 모임 · 갈고리 (u 0.84 → 끝) 는 바늘 끝까지 가늘어짐"""
    s = np.clip(s, 0, 1)
    rise = np.sin(np.pi / 2 * np.clip(s / 0.55, 0, 1)) ** 0.75
    headt = np.sin(np.pi / 2 * np.clip((1 - s) / 0.24, 0, 1)) ** 0.9
    hookt = np.sin(np.pi / 2 * np.clip((RP_END - u) / (RP_END - 0.84), 0, 1))
    return rise * headt * hookt


def tongue(pad, base, ang0, L, wd, curl, ph, attach, a=1.0, wob=0.6, n=20):
    """혼불 혀 (몸 · 속 줄 · 빛 무리): base 에서 ang0 쪽으로 L — 끝으로 갈수록 curl 만큼 휘고 흔들림 (머리카락처럼 가는 끝).
    wd = 가장 굵은 곳의 반폭. attach 1 = 뿌리가 날에 넓게 붙음, 0 = 떨어져 뿌리가 둥글게 모임. 밝기는 뿌리 쪽이 밝음"""
    pts = [base]
    x, y = base
    step = L / n
    for i in range(1, n + 1):
        t = i / n
        aa = ang0 + curl * t ** 1.3 + wob * math.sin(ph + t * 7.5) * t
        x += math.cos(aa) * step
        y += math.sin(aa) * step
        pts.append((x, y))

    def half(t):
        t = np.clip(t, 0.0, 1.0)
        stuck = (1 - t) ** 1.25
        free = np.sin(np.pi / 2 * np.clip(t / 0.2, 0, 1)) ** 0.6 * np.minimum(1.0, (1 - t) / 0.8) ** 1.6
        return wd * (attach * stuck + (1 - attach) * free)

    body = pad.stroke(pts, 1.0, wfn=lambda t: 2 * half(t), afn=lambda t: a * (1 - 0.5 * np.clip(t, 0, 1)))
    m = int(n * 0.85)
    inner = pad.stroke(pts[:m + 1], 1.0, wfn=lambda t: 0.8 * half(t * m / n), afn=lambda t: a * (1 - 0.3 * np.clip(t, 0, 1)))
    halo = pad.stroke(pts, 1.0, wfn=lambda t: 3.4 * half(t) + 1.6, afn=lambda t: a * 0.7 * (1 - 0.4 * np.clip(t, 0, 1)), soft=1.0)
    return body, inner, halo, pts[-1]


def rp_frame(f):
    pad = Pad(W, H)
    u, d0, A = fields(pad)
    head, tail, wk, fade = RP_HEAD[f], RP_TAIL[f], RP_WK[f], RP_FADE[f]
    d = d0 - rp_hook(u)
    s = (u - tail) / (head - tail)
    on = (s > 0) & (s < 1)
    prof = rp_prof(s, u) * on
    live = prof > 0.004
    smoke = fbm(A / 22.0, d0 / 7.0, seed=77, octaves=3)
    wc0 = RP_WC * wk * prof
    wc = wc0 * (0.9 + 0.14 * smoke)
    wg = RP_WG * wk * prof * (0.9 + 0.14 * smoke)
    outer = clamp01((d + 0.12) * SS + 0.5)
    t = np.clip(d / np.maximum(wc, 0.3), 0, 1.5)
    ends = smooth(-0.01, 0.04, s) * smooth(1.0, 0.99, s)
    # ---- 심지: 면도날 바깥 날 (밝고 또렷) · 날 뒤로 옅은 청록 윤 · 연기 결
    band = outer * smooth(wc + 1.0, wc - 3.0, d) * live
    edge = outer * gauss(np.maximum(d, 0), 0.6 + 0.75 * prof) * ends * live
    sheen = band * np.exp(-np.maximum(d, 0) / (2.0 + 4.5 * prof)) * 0.85
    wisp_tex = band * clamp01(smoke * 1.6 - 0.1) * (1 - t) * 0.3
    core = np.maximum.reduce([edge, sheen, wisp_tex])
    # ---- 빛: 짙은 몸 (넓은 띠 · 안쪽은 연기처럼 흐림) · 안쪽으로 연기 결이 진 짙은 기운
    tg = np.clip(d / np.maximum(wg, 0.3), 0, 1)
    gband = smooth(-3.0, 0.6, d) * smooth(wg + 3.0, wg - 6.0, d) * live * (0.96 - 0.3 * tg ** 1.5)
    smoke2 = fbm(A / 16.0, d0 / 6.0, seed=79, octaves=3)
    wake = 0.26 * np.exp(-np.maximum(d - wg, 0) / 12.0) * (d > wg * 0.6) * prof ** 0.8 * smooth(1.0, 0.92, s) * clamp01(0.35 + 1.3 * smoke2)
    glow = np.maximum(gband, wake)
    # ---- 머리 (그어지는 동안): 차가운 작은 번쩍 · 갈고리 끝에 닿으면 날 끝 반짝임
    fl = [0.7, 0.85, 0.75, 0.0, 0.0, 0.0, 0.0, 0.0][f]
    if fl > 0:
        hu = head - 0.012
        hx, hy = slash_P(hu, inward=2.5 + float(rp_hook(hu)))
        tx, ty = slash_T(hu)
        rr = min(11.0 * fl + 3.0, (W - 6 - hx) * 1.2)
        core = np.maximum(core, star4(pad, hx, hy, rr, 1.0, rot=ang_of(tx, ty), thin=0.09, diag=0.3))
        glow = np.maximum(glow, pad.radial(hx, hy, rr + 5, 1.3))
    tip = [0.0, 0.0, 0.0, 1.0, 0.6, 0.25, 0.0, 0.0][f]
    if tip > 0:   # 날 끝 반짝임 — 바늘 끝이 가려지지 않게 조금 뒤에 작게
        tu = RP_END - 0.03
        tx_, ty_ = slash_P(tu, inward=float(rp_hook(tu)) + 0.6)
        core = np.maximum(core, star4(pad, tx_, ty_, 5.5 * tip + 1.5, tip, rot=0.5, thin=0.09, diag=0.3))
        glow = np.maximum(glow, pad.radial(tx_, ty_, 6.0 * tip + 3, 1.4) * tip)
    # ---- 혼불 혀: 머리가 지난 바깥 날에 붙어 일다가 떨어져 뒤 (꼬리 쪽) · 바깥으로 끌림 → 흔들리며 줄어들고 사라짐
    rnd = random.Random(707)
    for i in range(14):
        uk = rnd.uniform(0.16, 0.97)
        big = rnd.random() < 0.45
        L0 = rnd.uniform(22.0, 31.0) if big else rnd.uniform(12.0, 19.0)
        wd = rnd.uniform(2.7, 3.4) if big else rnd.uniform(1.8, 2.3)
        ph = rnd.uniform(0, TAU)
        curl = rnd.uniform(0.3, 0.75) * (1 if i % 2 else -1)
        life = rnd.uniform(3.0, 4.2)
        off = rnd.uniform(0.2, 0.8)
        fork = big and rnd.random() < 0.6
        fk = head_frame(uk, RP_HEAD, 0.02)
        age = f - fk + off
        if age <= 0 or age >= life:
            continue
        q = age / life
        tx, ty = slash_T(uk)
        nx, ny = out_n(uk)
        bx, by = slash_P(uk - 0.012 * age, inward=-(0.3 + 2.2 * age))
        ang0 = ang_of(-tx * 0.92 + nx * 0.4, -ty * 0.92 + ny * 0.4)
        L = L0 * (0.5 + 0.5 * min(1.0, age / 1.2)) * (1 - 0.4 * q)
        attach = max(0.0, 1 - age / 1.1)
        a = (1 - q) ** 1.15 * fade
        for kk, (da, lk, wk2) in enumerate(((0.0, 1.0, 1.0),) + (((0.45 * (1 if curl < 0 else -1), 0.6, 0.7),) if fork else ())):
            b, inn, hl, end = tongue(pad, (bx, by), ang0 + da, L * lk, wd * wk2 * (1 - 0.3 * q), curl, ph + age * 2.3 + kk, attach, 1.0)
            ak = a * inside_k(bx, by, 10.0) * inside_k(end[0], end[1], 10.0)
            if ak <= 0.02:
                continue
            core = np.maximum(core, np.maximum(inn, b * 0.55) * ak)
            glow = np.maximum(glow, np.maximum(b, hl) * ak)
    # ---- 혼 불씨 (작은 점이 바깥으로 떠오름)
    rnd = random.Random(708)
    for i in range(18):
        uk = rnd.uniform(0.08, 0.98)
        st = rnd.uniform(0.2, 0.9)
        life = rnd.uniform(2.4, 3.8)
        sp = rnd.uniform(3.0, 6.0)
        r = rnd.uniform(0.7, 1.3)
        fk = head_frame(uk, RP_HEAD, 0.01)
        age = f - fk + st
        if age <= 0 or age >= life:
            continue
        x, y = slash_P(uk, inward=-(2.0 + sp * age))
        y -= 1.5 * age * age
        r *= 1 - 0.3 * age / life
        a = (1 - age / life) ** 1.3 * fade * inside_k(x, y)
        core = np.maximum(core, pad.disc_field(x, y, r) * a)
        glow = np.maximum(glow, pad.disc_field(x, y, r * 2.8, soft=1.2) * a * 0.6)
    core = core * fade
    glow = glow * fade
    g1, c1 = finish(pad, glow, core, halo=((1.5, 0.55), (4.0, 0.45), (9.5, 0.3)), body_k=0.95, core_bloom=((1.3, 0.4), (3.5, 0.3)), win=4.0)
    return side_soft(g1), c1


# ============================================================ 그림 목록

SPRITES = {}


def sprite(name):
    def deco(fn):
        SPRITES[name] = fn
        return fn
    return deco


def _frames(fn, n):
    glows, cores = run_frames(fn, n)
    for i, (g, c) in enumerate(zip(glows, cores)):
        assert g.shape == (H, W) and c.shape == (H, W), (fn.__name__, i, g.shape)
        assert np.isfinite(g).all() and np.isfinite(c).all(), (fn.__name__, i, "NaN")
    return [clamp01(g).astype(np.float32) for g in glows], [clamp01(c).astype(np.float32) for c in cores]


@sprite("rfx_jslash_sw")
def make_sw():
    glows, cores = _frames(sw_frame, SW_N)
    return Sprite("rfx_jslash_sw", "rotate_xyz", W, H, SW_N, "swordsman", glows, cores, cols=4, zoom=4)


@sprite("rfx_jslash_wa")
def make_wa():
    glows, cores = _frames(wa_frame, 8)
    return Sprite("rfx_jslash_wa", "rotate_xyz", W, H, 8, "warrior", glows, cores, cols=4, zoom=2)


@sprite("rfx_jslash_sb")
def make_sb():
    glows, cores = _frames(sb_frame, 8)
    return Sprite("rfx_jslash_sb", "rotate_xyz", W, H, 8, "spellblade", glows, cores, cols=4, zoom=3)


@sprite("rfx_jslash_rp")
def make_rp():
    glows, cores = _frames(rp_frame, 8)
    return Sprite("rfx_jslash_rp", "rotate_xyz", W, H, 8, "reaper", glows, cores, cols=4, zoom=3)


# ============================================================ rfx_jslash2 와 견준 그림 (같은 배율 · 같은 때의 rfx_jslash2 테두리를 겹침)

def _outline(mask):
    m = mask > 0.5
    if _ndi is not None:
        er = _ndi.binary_erosion(m, iterations=1)
    else:
        er = m.copy()
        er[1:, :] &= m[:-1, :]
        er[:-1, :] &= m[1:, :]
        er[:, 1:] &= m[:, :-1]
        er[:, :-1] &= m[:, 1:]
    return m & ~er


def _bbox(a, th=0.1):
    ys, xs = np.nonzero(a > th)
    if ys.size == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def _union(bbs):
    bbs = [b for b in bbs if b]
    if not bbs:
        return None
    return min(b[0] for b in bbs), min(b[1] for b in bbs), max(b[2] for b in bbs), max(b[3] for b in bbs)


def compare(sprites, path, scale=0.6):
    """줄마다 그림 하나 (장면을 같은 배율로 나란히) — 분홍 줄 = 같은 때 (장 시작 몫) 의 rfx_jslash2 테두리 (빛 · 심지 알파 > 0.12)"""
    ref = old_cells("rfx_jslash2")
    if ref is None:
        print("  (rfx_jslash2 texture not found — no compare image)")
        return None
    rg, rc = ref[:8], ref[8:16]
    gap = 6
    cw, ch = int(W * scale), int(H * scale)
    bgc = tuple(int(v * 255) for v in BG)
    rows = [("rfx_jslash2  (reference · swordsman colors)", rg, rc, COLORS["swordsman"], None)]
    for sp in sprites:
        ub = _union([_bbox(np.maximum(g, c)) for g, c in zip(sp.glows, sp.cores)])
        rows.append((f"{sp.name}  ({sp.job} · x{sp.frames})  pink = rfx_jslash2 at the same time · union bbox {ub}", sp.glows, sp.cores, COLORS[sp.job],
                     [min(7, (k * 8) // sp.frames) for k in range(sp.frames)]))
    nmax = max(len(r[1]) for r in rows)
    out = Image.new("RGB", (gap + nmax * (cw + gap), len(rows) * (ch + 30) + 8), bgc)
    for ri, (title, gl, co, (ca, cb), refk) in enumerate(rows):
        y0 = 4 + ri * (ch + 30)
        if refk is None:
            title += f"  union bbox {_union([_bbox(np.maximum(g, c)) for g, c in zip(rg, rc)])}"
        label(out, title, (gap, y0))
        n = len(gl)
        for k in range(n):
            im = np.asarray(tint_comp_scaled(gl[k], co[k], ca, cb, W, H), np.float32).copy()
            if refk is not None:
                ln = _outline(np.maximum(rg[refk[k]], rc[refk[k]]) > 0.12)
                im[ln] = im[ln] * 0.3 + np.array([255, 70, 200], np.float32) * 0.7
            pim = Image.fromarray(np.clip(im, 0, 255).astype(np.uint8), "RGB").resize((cw, ch), Image.LANCZOS)
            x = gap + k * (cw + gap)
            out.paste(pim, (x, y0 + 13))
            label(out, f"{k}  t={k / n:.2f}" + ("" if refk is None else f"  ref {refk[k]}"), (x + 2, y0 + 14 + ch))
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
    made = []
    for name, fn in SPRITES.items():
        if only and name not in only:
            continue
        sp = fn()
        save_sprite(sp)
        made.append(sp)
        if folder:
            print("  preview:", preview_sprite(sp, folder))
    if folder and made:
        print("  compare:", compare(made, os.path.join(folder, "slash_compare.png")))


if __name__ == "__main__":
    main()
