"""2026-11-16 칠한 이펙트 그림 → 도트 그림 (사용자 "2번으로 해보자" — 피드백 "스킬을 고퀄 이미지로 하니깐 몹이랑 데미지 피해 표시는 도트여서 괴리감이 든다").

마인크래프트 세계 (블록 · 몹 · 바닐라 파티클 · 글자) 는 한 칸에 16 텍셀 — 칠한 그림 (한 칸에 25 ~ 130 텍셀 · 부드러운 빛 번짐 · 반투명 그러데이션) 이
따로 놀아 보임. 같은 그림 (모양 · 움직임 · 장면) 을 세계 밀도의 도트 그림으로 옮김:

1. 줄이기: 그림이 화면에서 가장 크게 보일 때 (jobcensus 의 "가장 큰 크기") 한 칸에 density 텍셀이 되게. 한 텍셀 = 원래 픽셀 묶음 —
   알파는 묶음의 평균과 높은 몫 (분위수) 중 큰 것 (가는 빛줄기 · 별이 1 텍셀로 남게), 색은 묶음에서 밝은 픽셀들의 색 (밝은 심이 묻히지 않게).
2. 색: 묶음의 색 램프 (main 몸 · accent 둘째 색 · smoke 연기 — tools/jobvfx/element.py Style) 위의 가장 가까운 자리를 찾아 몇 단계 (levels) 로 —
   팔레트가 원소마다 6 + 4 + 3 색으로 정해짐 (도트 그림의 "램프": 어두운 바깥 → 밝은 심 · 흰빛).
3. 알파: 세 단계 — 없음 · 빛 번짐 (glow_alpha, 램프의 짙은 가운데 색) · 꽉 참 (1). 부드러운 그러데이션 대신 띠.
4. 다듬기: 외톨이 텍셀 (둘레 8 칸에 같은 단계가 없음) 은 둘레의 다수 단계로 — 잡음이 지글거리지 않게.

글꼴 텍스처는 가장 가까운 점 (확대하면 텍셀이 네모) — 도트 그림이 그대로 네모 텍셀로 보임.
"""
import re

import numpy as np
from PIL import Image
from scipy import ndimage


# ------------------------------------------------------------------ 색 공간 (OKLab — 가까운 색 찾기)

def _lin(c):
    c = np.asarray(c, float)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def oklab(rgb):
    l_ = _lin(rgb)
    M1 = np.array([[0.4122214708, 0.5363325363, 0.0514459929], [0.2119034982, 0.6806995451, 0.1073969566], [0.0883024619, 0.2817188376, 0.6299787005]])
    M2 = np.array([[0.2104542553, 0.7936177850, -0.0040720468], [1.9779984951, -2.4285922050, 0.4505937099], [0.0259040371, 0.7827717662, -0.8086757660]])
    lms = np.cbrt(l_ @ M1.T)
    return lms @ M2.T


def from_oklab(lab):
    """OKLab → sRGB (0 ~ 1, 잘라냄)"""
    lab = np.asarray(lab, float)
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    lin = np.clip(np.stack([r, g, bb], -1), 0, 1)
    return np.where(lin <= 0.0031308, lin * 12.92, 1.055 * lin ** (1 / 2.4) - 0.055)


# ------------------------------------------------------------------ 묶음 셈 (원래 픽셀 → 텍셀)

def _bins(H, W, th, tw):
    by = np.minimum((np.arange(H) * th) // H, th - 1)
    bx = np.minimum((np.arange(W) * tw) // W, tw - 1)
    return (by[:, None] * tw + bx[None, :]).ravel()


def _bin_quantile(v, b, nb, q):
    """묶음마다 v 의 q 분위수 (v · b 는 1차원)"""
    order = np.lexsort((v, b))
    vs, bs = v[order], b[order]
    start = np.searchsorted(bs, np.arange(nb), "left")
    end = np.searchsorted(bs, np.arange(nb), "right")
    cnt = end - start
    idx = start + np.clip(np.floor((cnt - 1) * q).astype(int), 0, None)
    out = np.zeros(nb)
    ok = cnt > 0
    out[ok] = vs[np.minimum(idx[ok], len(vs) - 1)]
    return out


MAIN_LV = (0.34, 0.48, 0.62, 0.76, 0.88, 1.0)
ACCENT_LV = (0.5, 0.7, 0.85, 1.0)
SMOKE_LV = (0.45, 0.8, 1.0)
AUTO_LV = (0.17, 0.33, 0.5, 0.67, 0.83, 1.0)


def _ramp_from_stops(stops):
    """OKLab 자리 (n × 3, 어둠 → 밝음) → f(t) (OKLab 에서 고르게 섞어 sRGB)"""
    ts = np.linspace(0, 1, len(stops))

    def f(t):
        t = np.clip(np.asarray(t, float), 0, 1)
        lab = np.stack([np.interp(t, ts, stops[:, k]) for k in range(3)], -1)
        return from_oklab(lab)
    return f


def _build_ramp(lab, w):
    """한 색 무리 (OKLab · 무게) → 밝기 분위수 7 자리의 램프"""
    order = np.argsort(lab[:, 0])
    lab, w = lab[order], w[order]
    cw = np.cumsum(w)
    cw /= cw[-1]
    stops = []
    for q in (0.02, 0.15, 0.35, 0.55, 0.75, 0.9, 0.995):
        lo = np.searchsorted(cw, max(0.0, q - 0.05))
        hi = max(lo + 1, np.searchsorted(cw, min(1.0, q + 0.05)))
        stops.append(np.average(lab[lo:hi], axis=0, weights=w[lo:hi]))
    stops = np.array(stops)
    stops[:, 0] = np.maximum.accumulate(stops[:, 0])
    return _ramp_from_stops(stops)


class Palette:
    """램프 셋 → 단계 색. S (원소 묶음 Style — main · accent · smoke) 또는 ramps [(f(t), 단계들, 빛 번짐 자리)]"""

    def __init__(self, S=None, ramps=None):
        if ramps is None:
            ramps = [(S.main, MAIN_LV, 0.62), (S.accent, ACCENT_LV, 0.8), (S.smoke, SMOKE_LV, 1.0)]
        self.ramps = [r[0] for r in ramps]
        self.levels = [np.array(r[1]) for r in ramps]
        self.glow = np.array([r[2] for r in ramps])
        ts = np.linspace(0, 1, 97)
        cand, rid, tt = [], [], []
        for k, f in enumerate(self.ramps):
            c = f(ts)
            cand.append(c)
            rid += [k] * len(ts)
            tt += list(ts)
        self.cand = np.concatenate(cand)
        self.lab = oklab(self.cand)
        self.rid = np.array(rid)
        self.t = np.array(tt)

    @staticmethod
    def auto(frames, max_k=4, seed=7):
        """그림 자체의 색에서 램프 셋을 만듦 (원소 묶음 Style 이 없는 그림 — 팬텀 아처 · 아스널 킹 · 블레이드 레인저 · 그림자 꽃잎 · 앞으로 그릴 룬):
        보이는 픽셀 (알파 0.2 이상) 을 OKLab 색상각 봉우리 (10° 칸 · 둘레 매끄럽게 · 무게 = 알파 × 채도, 전체의 6% 이상 · 40° 안은 합침 · 많아야 max_k) 로
        나누고 무채색 (채도 0.03 아래 — 흰 심 · 잿빛 연기) 이 4% 넘으면 따로 → 무리마다 밝기 분위수 7 자리 램프 (단계 6 · 빛 번짐은 가운데 0.5)"""
        rng = np.random.default_rng(seed)
        pix, wts = [], []
        for fr in frames[::max(1, len(frames) // 8)]:
            A = np.asarray(fr.convert("RGBA"), float) / 255.0
            a = A[..., 3].ravel()
            m = a >= 0.2
            pix.append(A[..., :3].reshape(-1, 3)[m])
            wts.append(a[m])
        C = np.concatenate(pix) if pix else np.zeros((0, 3))
        W = np.concatenate(wts) if wts else np.zeros(0)
        if len(C) < 16:
            gray = np.array([[0.2, 0, 0], [0.6, 0, 0], [1.0, 0, 0]])
            return Palette(ramps=[(_ramp_from_stops(gray), AUTO_LV, 0.5)])
        if len(C) > 60000:
            idx = rng.choice(len(C), 60000, replace=False)
            C, W = C[idx], W[idx]
        lab = oklab(C)
        ch = np.hypot(lab[:, 1], lab[:, 2])
        hue = np.arctan2(lab[:, 2], lab[:, 1])
        chrom = ch >= 0.03
        ramps = []
        if chrom.sum() >= 32:
            hb = (((hue[chrom] + np.pi) / (2 * np.pi)) * 36).astype(int) % 36
            hist = np.bincount(hb, weights=W[chrom] * ch[chrom], minlength=36)
            hs = (np.roll(hist, 1) + 2 * hist + np.roll(hist, -1)) / 4
            cand = [i for i in range(36) if hs[i] >= hs[(i - 1) % 36] and hs[i] >= hs[(i + 1) % 36] and hs[i] >= 0.06 * hs.sum()]
            cand.sort(key=lambda i: -hs[i])
            peaks = []
            for i in cand:
                if all(min((i - j) % 36, (j - i) % 36) >= 4 for j in peaks):
                    peaks.append(i)
                if len(peaks) >= max_k:
                    break
            if not peaks:
                peaks = [int(np.argmax(hs))]
            cen = np.array([(p + 0.5) / 36 * 2 * np.pi - np.pi for p in peaks])
            hc = hue[chrom]
            d = np.abs(np.angle(np.exp(1j * (hc[:, None] - cen[None, :]))))
            lab_c, w_c = lab[chrom], W[chrom]
            asg = d.argmin(1)
            for k in range(len(peaks)):
                m = asg == k
                if m.sum() >= 8:
                    ramps.append((_build_ramp(lab_c[m], w_c[m]), AUTO_LV, 0.5))
        if (~chrom).sum() >= 8 and W[~chrom].sum() >= 0.04 * W.sum() or not ramps:
            ramps.append((_build_ramp(lab[~chrom] if (~chrom).sum() >= 8 else lab, W[~chrom] if (~chrom).sum() >= 8 else W), AUTO_LV, 0.5))
        return Palette(ramps=ramps)

    def nearest(self, rgb):
        """색 (N × 3) → (램프 번호, 램프 자리 t)"""
        lab = oklab(np.clip(rgb, 0, 1))
        best = np.empty(len(rgb), int)
        step = 4096
        for i in range(0, len(rgb), step):
            d = ((lab[i:i + step, None, :] - self.lab[None, :, :]) ** 2).sum(-1)
            best[i:i + step] = d.argmin(1)
        return self.rid[best], self.t[best]

    def snap(self, rid, t):
        """램프 자리 → 가장 가까운 단계 (단계 번호 · 단계 자리)"""
        lv = np.zeros(len(t), int)
        tq = np.zeros(len(t))
        for k, L in enumerate(self.levels):
            m = rid == k
            if not m.any():
                continue
            j = np.abs(t[m, None] - L[None, :]).argmin(1)
            lv[m] = j
            tq[m] = L[j]
        return lv, tq

    def color(self, rid, tq):
        out = np.zeros((len(tq), 3))
        for k, f in enumerate(self.ramps):
            m = rid == k
            if m.any():
                out[m] = f(tq[m])
        return out


def _cleanup(code, valid):
    """외톨이 텍셀 (둘레 8 칸에 같은 값이 없음) 을 둘레의 다수 값으로 (code: 2차원 정수, valid: 바꿔도 되는 곳)"""
    h, w = code.shape
    pad = np.pad(code, 1, constant_values=-1)
    neigh = np.stack([pad[1 + dy:1 + dy + h, 1 + dx:1 + dx + w] for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dy, dx) != (0, 0)])
    same = (neigh == code[None]).sum(0)
    out = code.copy()
    lone = (same == 0) & valid
    if lone.any():
        ys, xs = np.nonzero(lone)
        for y, x in zip(ys, xs):
            vals = neigh[:, y, x]
            vals = vals[vals >= 0]
            if len(vals):
                u, c = np.unique(vals, return_counts=True)
                out[y, x] = u[c.argmax()]
    return out


def density(extent):
    """그림 전체가 화면에서 가장 크게 보일 때의 긴 변 (칸) → 한 칸의 텍셀 수: 큰 그림 (4 칸 넘게) 은 블록 · 몹과 같은 16, 작은 그림 (1 칸 아래) 은
    바닐라 파티클처럼 32 (작은 불티 · 맞은 자리가 뭉개지지 않게), 사이는 고르게"""
    return float(np.clip(32 - (extent - 1) * 16 / 3, 16, 32))


def texels(size, aspect=1.0, k=1):
    """칸 하나의 가장 큰 높이 size (칸 — jobcensus) · 가로세로 비 · 조각 수 k (2 × 2 조각이면 2) → 칸 높이 (텍셀)"""
    return max(8, int(round(size * density(size * k * max(1.0, aspect)))))


# 2026-11-16 그림판이 화면에서 가장 크게 보인 크기 (칸 높이 — 조각 그림은 이름_rc 를 뺀 바탕 이름, CI 영상 jobcensus "|| sheets 이름=수@크기" 의 가장 큰 값):
# tools/jobvfx/sheet_sizes.json (tools/jobvfx/sheet_sizes.py 가 CI 로그에서 모음 — 손으로 고쳐도 됨). 없는 그림은 DEFAULT_SIZE
import json as _json
import os as _os

SIZES_FILE = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "sheet_sizes.json")
SIZES = {k: v for k, v in _json.load(open(SIZES_FILE)).items() if not k.startswith("_")} if _os.path.exists(SIZES_FILE) else {}
DEFAULT_SIZE = 2.0
MISSING = set()  # 크기가 없어 DEFAULT_SIZE 로 옮긴 그림 (vfx_sprites.py 가 알림)


def size_of(base):
    if base in SIZES:
        return SIZES[base]
    MISSING.add(base)
    return DEFAULT_SIZE


def pixelize(frames, th, S, cut=0.14, solid=0.5, haze=0.45, glow_r=1, glow_alpha=1.0, q=0.8, cleanup=True, pal=None, tw=None):
    """칠한 칸들 (PIL RGBA, 같은 크기) → 도트 칸들 (높이 th 텍셀 · 가로 tw — 없으면 같은 비). S = 원소 묶음 Style (램프).
    알파: 에너지 solid 이상 꽉 참 · 알파 haze 이상이거나 (cut 이상 + 꽉 찬 텍셀에서 십자로 glow_r 텍셀 안) 빛 번짐 (glow_alpha — 1 이면 꽉 참: 바닐라 파티클처럼
    반투명 없이 램프 색) · 나머지 없음 (넓게 옅은 안개는 지움 — 도트 그림의 빛 번짐은 모양 둘레의 띠)"""
    pal = pal or Palette(S)
    W, H = frames[0].size
    tw = tw or max(1, int(round(W * th / H)))
    nb = th * tw
    b = _bins(H, W, th, tw)
    cnt = np.bincount(b, minlength=nb).astype(float)
    out = []
    for fr in frames:
        A = np.asarray(fr.convert("RGBA"), float) / 255.0
        a = A[..., 3].ravel()
        rgb = A[..., :3].reshape(-1, 3)
        lum = rgb @ np.array([0.3, 0.59, 0.11])
        e = a * (0.35 + 0.65 * (0.5 * lum + 0.5 * rgb.max(1)))
        mean_a = np.bincount(b, a, nb) / np.maximum(cnt, 1)
        qa = _bin_quantile(a, b, nb, q)
        alpha = np.maximum(mean_a, qa)
        # 꽉 찬 텍셀은 밝기까지 본 에너지로 (짙은 빛 번짐 · 블룸이 큰 덩어리가 되지 않게 — 밝은 심 · 몸만)
        energy = np.maximum(np.bincount(b, e, nb) / np.maximum(cnt, 1), _bin_quantile(e, b, nb, q))
        # 밝은 픽셀들의 색: 묶음에서 에너지가 가장 큰 몫 (최대의 60% 이상) 의 에너지 가중 평균
        emax = np.zeros(nb)
        np.maximum.at(emax, b, e)
        w = e * (e >= 0.6 * emax[b])
        ws = np.bincount(b, w, nb)
        col = np.stack([np.bincount(b, w * rgb[:, c], nb) for c in range(3)], 1) / np.maximum(ws, 1e-9)[:, None]
        # 빛 번짐 텍셀의 색: 묶음의 알파 가중 평균
        aw = np.bincount(b, a, nb)
        gcol = np.stack([np.bincount(b, a * rgb[:, c], nb) for c in range(3)], 1) / np.maximum(aw, 1e-9)[:, None]
        sol = (energy >= solid).reshape(th, tw)
        # 빛 번짐 띠: 꽉 찬 텍셀에서 십자로 glow_r 텍셀 (점 하나 둘레는 마름모 — 반짝임 모양, 둥근 고리가 되지 않게)
        near = ndimage.binary_dilation(sol, structure=ndimage.generate_binary_structure(2, 1), iterations=glow_r) if glow_r > 0 and sol.any() else sol
        glow = ((alpha >= haze) | ((alpha >= cut) & near.ravel())) & ~sol.ravel()
        state = np.where(sol.ravel(), 2, np.where(glow, 1, 0))
        src = np.where((state == 2)[:, None], col, gcol)
        rid, t = pal.nearest(src)
        t = np.where(state == 1, pal.glow[rid], t)
        lv, tq = pal.snap(rid, t)
        code = np.where(state == 0, -1, state * 1000 + rid * 100 + lv).reshape(th, tw)
        if cleanup:
            # 밝은 꽉 찬 텍셀 (램프의 맨 위 두 단계 — 별 · 반짝임) 은 외톨이여도 남김
            top = np.array([len(L) for L in pal.levels])[rid] - 2
            bright = ((state == 2) & (lv >= top)).reshape(th, tw)
            code = _cleanup(code, (code >= 0) & ~bright)
            code = np.where(code >= 0, code, -1)
        code = code.ravel()
        st = np.where(code < 0, 0, code // 1000)
        rid2 = np.where(code < 0, 0, (code % 1000) // 100)
        lv2 = np.where(code < 0, 0, code % 100)
        tq2 = np.array([pal.levels[r][l] for r, l in zip(rid2, lv2)])
        c = pal.color(rid2, tq2)
        al = np.where(st == 2, 1.0, np.where(st == 1, glow_alpha, 0.0))
        img = np.concatenate([c, al[:, None]], 1).reshape(th, tw, 4)
        out.append(Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA"))
    return out


# ------------------------------------------------------------------ 그림판 (tools/vfx_sprites.py 가 씀)

TILE_RE = re.compile(r"^(.*)_(\d)(\d)$")
_PAL = {}


def sheet_cells(name, makes, cache, libs):
    """그림판 name 이 도트 묶음 (libs: 접두 → element.STYLES 이름 · "auto" = 그림 자체의 색으로 램프) 이면 도트 칸들, 아니면 None.
    조각 그림 (이름_rc) 은 조각들을 한 그림으로 붙여 도트로 옮긴 뒤 다시 나눔 (조각 경계에서 텍셀이 어긋나지 않게). makes = {이름: 만들기}, cache = 조각 묶음 셈"""
    lib = max((l for l in libs if name.startswith(l + "_")), key=len, default=None)
    if lib is None:
        return None
    style = libs[lib]
    S = None
    if style != "auto":
        from .element import STYLES
        S = STYLES[style]

    def pal_for(frames):
        if S is not None:
            return _PAL.setdefault(lib, Palette(S))
        return Palette.auto(frames)
    m = TILE_RE.match(name)
    if m and m.group(1) + "_00" in makes:
        base = m.group(1)
        if base not in cache:
            keys = [k for k in makes if TILE_RE.match(k) and TILE_RE.match(k).group(1) == base]
            tiles = {(int(TILE_RE.match(k).group(2)), int(TILE_RE.match(k).group(3))): makes[k]() for k in keys}
            R = max(r for r, _ in tiles) + 1
            C = max(c for _, c in tiles) + 1
            tw0, th0 = tiles[(0, 0)][0].size
            size = size_of(base)
            th_t = texels(size, tw0 / th0, max(R, C))
            tw_t = max(1, int(round(tw0 * th_t / th0)))
            full = []
            for f in range(len(tiles[(0, 0)])):
                im = Image.new("RGBA", (tw0 * C, th0 * R), (0, 0, 0, 0))
                for (r, c), cells in tiles.items():
                    im.paste(cells[f], (c * tw0, r * th0))
                full.append(im)
            px = pixelize(full, th_t * R, S, pal=pal_for(full), tw=tw_t * C)
            for (r, c) in tiles:
                cache["%s_%d%d" % (base, r, c)] = [im.crop((c * tw_t, r * th_t, (c + 1) * tw_t, (r + 1) * th_t)) for im in px]
        return cache[name]
    cells = makes[name]()
    w0, h0 = cells[0].size
    return pixelize(cells, texels(size_of(name), w0 / h0), S, pal=pal_for(cells))
