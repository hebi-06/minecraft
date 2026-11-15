"""2026-11-15 직업 전용 2D 이펙트 그림의 공용 도구 (사용자 "직업마다 전용으로 그릴래" — tools/jobvfx/<직업>.py 가 씀). 처음 만든 곳: 시범 팬텀 버스트
(예전 tools/vfx_pilot_art.py). 원래 머리말:

2026-11-15 시범 2D 프레임 애니메이션 (사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐 나는 압도적인 퀄을 중요하게 여김" — 팬텀 버스트):
빛 · 폭발 · 충격파 · 섬광 · 모으는 빛 · 빛 무리 · 불티 · 그을린 땅을 미리 렌더링한 칠한 그림 (colored — 글자 색 흰색으로 그대로).

- 글자 표시 (text display) 는 명암 계산이 없어 칠한 색이 그대로 나옴. 글꼴 텍스처는 가장 가까운 점 (확대하면 픽셀이 커짐) → 화면에서 크게 보이는 것은
  2 × 2 조각 (512 픽셀 — 조각마다 256 글자) 으로 나눠 띄움 (PhantomArcher.tiled).
- 그림: 시간에 따라 바뀌는 3D 값 잡음 (x · y · t) + 좌표 비틀기 → 불덩이의 결, 온도 → 셀 띠 색 램프 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인 → 보랏빛 연기),
  밝은 곳은 흐리게 번진 빛 (블룸) 을 더함, 연기는 잡음 문턱이 올라가며 흩어짐.
- 팔레트: 진홍 폭발 (팬텀 아처) + 푸른 흰빛 (투영의 빛 — 충격파 앞 날 · 섬광 빛살) — 한 가지 색이 아니게.

지금: python3 -m jobvfx.<직업> --preview 폴더 (tools 에서) — 칸마다 어두운 · 풀밭 · 노을 바탕. tools/vfx_sprites.py 가 직업마다 sprites() 를 SPRITES 에 더함 (colored).
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

TILE = 256


# ------------------------------------------------------------------ 잡음

class Noise3:
    """값 잡음 (x · y · t — 격자 64³ 반복, 부드러운 이음)"""

    def __init__(self, seed, n=64):
        self.n = n
        self.g = np.random.default_rng(seed).random((n, n, n))

    def __call__(self, x, y, t):
        n = self.n
        xi, yi, ti = np.floor(x).astype(int), np.floor(y).astype(int), int(math.floor(t))
        fx, fy, ft = x - xi, y - yi, t - ti
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        ft = ft * ft * (3 - 2 * ft)
        x0, y0, t0 = xi % n, yi % n, ti % n
        x1, y1, t1 = (x0 + 1) % n, (y0 + 1) % n, (t0 + 1) % n
        g = self.g

        def plane(tt):
            a = g[x0, y0, tt] * (1 - fx) + g[x1, y0, tt] * fx
            b = g[x0, y1, tt] * (1 - fx) + g[x1, y1, tt] * fx
            return a * (1 - fy) + b * fy
        return plane(t0) * (1 - ft) + plane(t1) * ft

    def fbm(self, x, y, t, oct=5, lac=2.0, gain=0.5):
        s, a, tot = 0.0, 1.0, 0.0
        for o in range(oct):
            s = s + a * self(x * lac ** o + o * 17.3, y * lac ** o - o * 9.1, t * (1 + 0.3 * o) + o * 5.7)
            tot += a
            a *= gain
        return s / tot


def sstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def H(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def ramp(stops):
    """[(위치, '#색')] → f(t) (선형)"""
    P = np.array([p for p, _ in stops])
    C = np.stack([H(c) for _, c in stops])

    def f(t):
        t = np.clip(np.asarray(t, float), P[0], P[-1])
        out = np.empty(t.shape + (3,))
        for ch in range(3):
            out[..., ch] = np.interp(t, P, C[:, ch])
        return out
    return f


def banded(t, edges, soft=0.035):
    """온도를 셀 띠로 (부드러운 경계) — 손으로 칠한 띠 느낌"""
    out = np.zeros_like(t)
    lo = 0.0
    for e in edges:
        out = out + (e - lo) * sstep(e - soft, e + soft, t)
        lo = e
    return out + (t - out) * 0.35


# 진홍 폭발 (흰빛 → 크림 → 금 → 주황 → 진홍 → 와인 → 보랏빛 연기)
FIRE = ramp([(0.0, "#1B0E1C"), (0.12, "#3A0F24"), (0.26, "#7A0D2A"), (0.4, "#C8182F"), (0.52, "#F2412F"), (0.64, "#FF7A2E"),
             (0.76, "#FFB348"), (0.86, "#FFE08C"), (0.94, "#FFF6D6"), (1.0, "#FFFFFF")])
SMOKE = ramp([(0.0, "#160C14"), (0.45, "#33192A"), (0.8, "#5A3446"), (1.0, "#7A5060")])
CYAN = ramp([(0.0, "#2A5CFF"), (0.45, "#6FD4FF"), (0.8, "#D8F6FF"), (1.0, "#FFFFFF")])


def grid(n):
    g = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(g, -g)
    return x, y


def _shape(n):
    """그림 크기 n (정사각 한 변) 또는 (높이, 너비)"""
    return (n, n) if isinstance(n, int) else tuple(n)


def compose(layers, n):
    """[(rgb, a)] 를 차례로 겹침 (앞이 아래) → 곧은 알파 RGBA (n = 한 변 또는 (높이, 너비))"""
    sh = _shape(n)
    rgb = np.zeros(sh + (3,))
    a = np.zeros(sh)
    for c, al in layers:
        al = np.clip(al, 0, 1)
        c = np.broadcast_to(c, sh + (3,))
        oa = al + a * (1 - al)
        rgb = (c * al[..., None] + rgb * (a * (1 - al))[..., None]) / np.maximum(oa, 1e-9)[..., None]
        a = oa
    return rgb, a


def bloom(rgb, a, n, thresh=0.62, sigma=0.035, gain=0.9, tint=None):
    """밝은 곳 (밝기 > thresh) 을 흐리게 번진 빛으로 — 몸 바깥에 빛 무리 (알파) · 몸 안은 밝게"""
    lum = (rgb * np.array([0.3, 0.55, 0.15])).sum(-1) * a
    e = np.clip((lum - thresh) / (1 - thresh), 0, 1)
    src = rgb * e[..., None]
    s = sigma * max(_shape(n))
    blur = np.stack([ndimage.gaussian_filter(src[..., i], s) for i in range(3)], -1)
    ba = ndimage.gaussian_filter(e, s)
    ba2 = ndimage.gaussian_filter(e, s * 2.6) * 0.6
    gcol = blur / np.maximum(ba, 1e-6)[..., None]
    if tint is not None:
        gcol = gcol * 0.5 + tint * 0.5
    ga = np.clip((ba * 1.6 + ba2) * gain, 0, 1)
    gl = np.clip(gcol, 0, 1)
    # 빛 무리 (아래) + 몸 (위)
    return compose([(gl, ga), (rgb, a)], n)


def to_img(rgb, a):
    out = np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], -1)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def tiles(img, k=2):
    """한 칸 그림 (k·256) → 조각 k × k (위 왼쪽부터 줄마다)"""
    out = []
    for r in range(k):
        for c in range(k):
            out.append(img.crop((c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE)))
    return out


# ------------------------------------------------------------------ 모양 도구 (2판)

def spike(x, y, a0, L, w0, p=1.0, aa=0.01):
    """가운데에서 a0 방향으로 뻗는 뾰족한 빛살 (밑동 반폭 w0 → 끝 L 에서 0, 굵기 곡선 p) — 가장자리 부드럽게 aa"""
    ca, sa = math.cos(a0), math.sin(a0)
    along = x * ca + y * sa
    perp = np.abs(-x * sa + y * ca)
    t = np.clip(along / L, 0, 1)
    w = w0 * (1 - t) ** p
    return (1 - sstep(w - aa, w + aa, perp)) * sstep(-aa, aa, along) * (1 - sstep(L - aa, L + aa, along))


def star_shape(r, th, K, r_in, outs, rot, sharp=2.2, jitter=None):
    """뾰족한 별 테두리 반지름 R(th) — 꼭짓점 K 개 (바깥 반지름 outs[i])"""
    prof = np.zeros_like(r)
    for i in range(K):
        a0 = i * 2 * math.pi / K + rot + (0 if jitter is None else jitter[i])
        d = np.abs((th - a0 + math.pi) % (2 * math.pi) - math.pi)
        hw = math.pi / K
        prof = np.maximum(prof, np.clip(1 - d / hw, 0, 1) ** sharp * outs[i])
    return r_in + prof


WHITE = H("#FFFDF6")



def tiled(name, make, k=2):
    """한 그림 (k·256) 의 칸들을 조각 k × k 판으로: 이름_rc (r = 줄 · c = 칸)"""
    cache = {}

    def frames():
        if "f" not in cache:
            cache["f"] = make()
        return cache["f"]
    out = []
    for r in range(k):
        for c in range(k):
            def mk(r=r, c=c):
                return [im.crop((c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE)) for im in frames()]
            out.append(("%s_%d%d" % (name, r, c), mk, True))
    return out




def preview(sets, out):
    """{이름: [칸 그림]} 을 어두운 · 풀밭 · 노을 바탕에 (칸마다 256 으로 줄여) 이름_바탕.png"""
    os.makedirs(out, exist_ok=True)
    bgs = {"dark": (0.06, 0.06, 0.09), "grass": (0.36, 0.5, 0.26), "dusk": (0.42, 0.3, 0.38)}
    for name, frames in sets.items():
        for bgn, bg in bgs.items():
            ims = []
            for im in frames:
                a = np.asarray(im, float) / 255
                c = a[..., :3] * a[..., 3:4] + np.array(bg) * (1 - a[..., 3:4])
                w, h = im.size
                s = 256 / max(w, h)
                ims.append(Image.fromarray((c * 255).astype(np.uint8)).resize((max(1, int(w * s)), max(1, int(h * s))), Image.LANCZOS))
            cols = 6
            rows = (len(ims) + cols - 1) // cols
            sheet = Image.new("RGB", (cols * 256, rows * 256), tuple(int(v * 255) for v in bg))
            for i, im in enumerate(ims):
                sheet.paste(im, ((i % cols) * 256 + (256 - im.size[0]) // 2, (i // cols) * 256 + (256 - im.size[1]) // 2))
            sheet.save(os.path.join(out, "%s_%s.png" % (name, bgn)))
    print(out)
