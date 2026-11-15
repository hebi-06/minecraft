#!/usr/bin/env python3
"""2026-11-15 HD 칠 (사용자 "색칠한게 너무 발로 한거 같음 대충한거 같음" · "저게 완성본이면 안됨 퀄이 너무 낮잖아" ·
"그림을 넣는다고 끝나는게 아니라 퀄이 좋을려면 AI가 만든 느낌을 줄이고 단색이 아니라 색을 좀 예쁘게 다양하게 넣어서 좋게 만들어야 함").

원칙
- 한 가지 색을 밝기만 바꾸지 않음: 어두운 쪽은 보라 · 와인 쪽으로, 밝은 쪽은 노랑 · 흰빛 쪽으로 (색조를 옮긴 손그림 램프).
- 도트처럼 보이지 않게: 모델 1 픽셀당 텍셀을 넉넉히 (4 ~ 32) + 3배로 그려 줄임 (매끈한 가장자리), 잡티 · 잔결 없음.
- 기계적인 무늬 대신 형태를 읽히게: 날의 등 · 빗면 · 반사 띠, 금의 윗면 빛 · 새긴 홈, 가죽 감은 띠, 보석 면 · 반짝임.
- 물체마다 여러 색: 강철 (차가운 푸른 회색) + 금 (호박 · 크림) + 보석 (루비 · 사파이어 · 에메랄드 · 토파즈) + 빛 (진홍 · 단조 불빛 · 금빛 물결 · 청록 고리).
- 상자 칠은 3D 셰이더: 면의 텍셀마다 모델 좌표 (X, Y, Z) 로 칠함 → 상자를 쌓은 날 · 불꽃이 이음매 없이 이어짐.

물들임 (tint) 을 쓰는 공용 이펙트 상자 (fx_bar · fx_cube · fx_orb · fx_flame): 바탕 = 곱하는 색 (가운데 흰빛 → 가장자리 보라 · 와인 쪽 그늘 — 물들임 0 이 어떤
색이든 그늘의 색조가 옮겨감) + 겹 = 뜨거운 심 (물들임 1 — 서버가 흰빛 · 노랑 쪽으로 옮긴 색, BlockFx.hot).

  python3 tools/vfx_blocky.py --preview <폴더> fx_bar phantom_sword gold_gate …   # vfx_blocky 가 이 모듈의 모델도 만듦
"""
import math

import numpy as np
from PIL import Image

import vfx_blocky as vb
from vfx_blocky import BModel, Mat

SS = 3


# ------------------------------------------------------------------ 색 · 램프

def H(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def ramp(*hexes):
    """고르게 놓인 색 램프 (손으로 고른 색 — 그늘은 보라 · 와인 쪽, 빛은 노랑 · 흰빛 쪽)"""
    cols = np.stack([H(x) for x in hexes])
    n = len(cols)

    def f(v):
        v = np.clip(np.asarray(v, float), 0.0, 1.0) * (n - 1)
        i = np.minimum(np.floor(v).astype(int), n - 2)
        t = (v - i)[..., None]
        return cols[i] * (1 - t) + cols[i + 1] * t
    return f


def sstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def rgba(rgb, a=1.0):
    a = np.broadcast_to(np.asarray(a, float), rgb.shape[:-1])
    return np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], -1)


def mixc(a, b, t):
    t = np.asarray(t, float)[..., None]
    return a * (1 - t) + b * t


def bands(t, edges, cols, soft):
    """셀 띠: t (0 → 1) 가 edges 를 넘을 때마다 다음 색 (부드러운 경계 soft)"""
    out = np.broadcast_to(np.asarray(cols[0], float), np.shape(t) + (3,)).copy()
    for e, c in zip(edges, cols[1:]):
        k = sstep(e - soft, e + soft, t)[..., None]
        out = out * (1 - k) + np.asarray(c, float) * k
    return out


STEEL = ramp("#1C2131", "#343D55", "#56637F", "#8190AE", "#AEBDD6", "#D8E3F2", "#F4F8FF", "#FFFFFF")
EDGE = ramp("#9FB4D2", "#CFE1F5", "#EEF8FF", "#FFFFFF")
GOLD = ramp("#3A1806", "#6C330B", "#A35A13", "#D69530", "#F2C35A", "#FFE39C", "#FFF8E2")
PGOLD = ramp("#6B3A0C", "#A8661A", "#DDA640", "#F6D27A", "#FFEDB8", "#FFFAEA", "#FFFFFF")  # 보물 날 (흰 금)
LEATHER = ramp("#150A0C", "#2B1416", "#472124", "#66322A", "#8A4A36", "#B06B4C")
CRIMSON_WRAP = ramp("#1E0409", "#3F0812", "#69101F", "#94192D", "#BC2E3F", "#E0606A")
RUBY = ramp("#2B030B", "#640A1C", "#AA1730", "#E0364A", "#FF7882", "#FFD3D7", "#FFF6F7")
SAPPHIRE = ramp("#070E34", "#112574", "#214DB6", "#4A7FE6", "#8FB8FF", "#DEEBFF", "#FFFFFF")
EMERALD = ramp("#022316", "#075236", "#108752", "#2AC07A", "#82EAB5", "#DDFFEE", "#FFFFFF")
TOPAZ = ramp("#361702", "#733606", "#BB640B", "#EC9620", "#FFC55E", "#FFEEC0", "#FFFFFF")
AMETHYST = ramp("#1B0830", "#3D1569", "#682AA8", "#9A5EE3", "#CBA6FF", "#F0E4FF", "#FFFFFF")
CRIMSON_E = ramp("#3A0517", "#780D29", "#BF1C35", "#EE403C", "#FF7A52", "#FFBD92", "#FFEBD9", "#FFFBF6")
MOLTEN = ramp("#2B0603", "#661404", "#AE3109", "#E55D13", "#FF922E", "#FFC565", "#FFEFC0", "#FFFDF4")
DSTEEL = ramp("#0D0F14", "#181C25", "#272E3C", "#3C4659", "#58657D", "#7F8CA6", "#AEBAD0", "#DCE4F0")
LACQUER = ramp("#21040A", "#470914", "#741121", "#A01C30", "#C63849", "#E46C78")
EBONY = ramp("#0A080C", "#16121A", "#251F29", "#382F3D", "#52465A", "#72657C")
LAPIS = ramp("#060E2C", "#0D1F60", "#183897", "#2B5BCB", "#6890EC", "#B8CEFF")

W3 = np.array([1.0, 1.0, 1.0])
# 곱하는 그늘 (물들임 색에 곱해짐 — 따뜻한 색은 와인 · 갈색, 차가운 색은 남보라 쪽으로 옮겨감)
MUL_MID = np.array([0.80, 0.60, 0.78])
MUL_RIM = np.array([0.40, 0.21, 0.47])
HOT_EDGE = np.array([0.97, 0.88, 0.74])


# ------------------------------------------------------------------ 3D 셰이더 (면 텍셀 → 모델 좌표)

def coords(face, f, t, w, h, ss):
    a = (np.arange(w * ss) + 0.5) / (w * ss)
    b = (np.arange(h * ss) + 0.5) / (h * ss)
    A, B = np.meshgrid(a, b)
    (fx, fy, fz), (tx, ty, tz) = f, t
    if face == "north":
        X, Y, Z = tx - A * (tx - fx), ty - B * (ty - fy), np.full_like(A, fz)
    elif face == "south":
        X, Y, Z = fx + A * (tx - fx), ty - B * (ty - fy), np.full_like(A, tz)
    elif face == "west":
        X, Y, Z = np.full_like(A, fx), ty - B * (ty - fy), fz + A * (tz - fz)
    elif face == "east":
        X, Y, Z = np.full_like(A, tx), ty - B * (ty - fy), tz - A * (tz - fz)
    elif face == "up":
        X, Y, Z = fx + A * (tx - fx), np.full_like(A, ty), fz + B * (tz - fz)
    else:
        X, Y, Z = fx + A * (tx - fx), np.full_like(A, fy), tz - B * (tz - fz)
    return X, Y, Z, A, B


def down(img, ss):
    """ss 배로 그린 그림을 줄임 (알파 무게 — 투명한 곳의 검은 테두리 없이)"""
    if ss == 1:
        return img
    h, w = img.shape[0] // ss, img.shape[1] // ss
    r = img[:h * ss, :w * ss].reshape(h, ss, w, ss, 4)
    a = r[..., 3]
    asum = a.sum(axis=(1, 3))
    rgb = (r[..., :3] * a[..., None]).sum(axis=(1, 3)) / np.maximum(asum, 1e-9)[..., None]
    plain = r[..., :3].mean(axis=(1, 3))
    rgb = np.where((asum > 1e-6)[..., None], rgb, plain)
    return np.concatenate([rgb, (asum / (ss * ss))[..., None]], -1)


def local(face, X, Y, Z, f, t):
    """면 위 가로 s · 세로 q (옆면은 위가 큼) 와 범위"""
    (fx, fy, fz), (tx, ty, tz) = f, t
    if face in ("north", "south"):
        return X, fx, tx, Y, fy, ty
    if face in ("east", "west"):
        return Z, fz, tz, Y, fy, ty
    return X, fx, tx, Z, fz, tz


def bevel(face, X, Y, Z, f, t, bw=0.32, hi=0.16, lo=0.18, rim=0.08):
    """깎은 모서리 (윗모서리 밝게 · 아랫모서리 어둡게 · 옆 모서리 살짝 빛) — 값에 더할 몫"""
    s, s0, s1, q, q0, q1 = local(face, X, Y, Z, f, t)
    dl, dr, db, dt = s - s0, s1 - s, q - q0, q1 - q
    if face in ("up", "down"):
        e = np.minimum(np.minimum(dl, dr), np.minimum(db, dt))
        return (hi * 0.8 if face == "up" else -lo * 0.5) * (1 - sstep(0, bw, e))
    v = hi * (1 - sstep(0, bw, dt)) - lo * (1 - sstep(0, bw, db))
    v = v + rim * (1 - sstep(0, bw, np.minimum(dl, dr)))
    return v


def hpaint(fn, f, t, ss=SS):
    def paint(w, h, face, rng, frame):
        X, Y, Z, A, B = coords(face, f, t, w, h, ss)
        res = fn(face, X, Y, Z, A, B)
        base, ov = res if isinstance(res, tuple) else (res, None)
        return down(base, ss), (down(ov, ss) if ov is not None else None)
    return paint


class HModel(BModel):
    """상자마다 제 셰이더 (모델 좌표로 칠함)"""

    def __init__(self, name, credit, d):
        super().__init__(name, credit, d)
        self._k = 0

    def hbox(self, fn, f, t, rot=None, faces=None, tint=None, glow=True, overlay_tint=None, overlay_pad=0.08, ss=SS, name=None, d=None):
        """fn(face, X, Y, Z, A, B) 를 이 상자 (f, t) 의 좌표로 부르는 셰이더 하나 = 재질 하나 (d = 이 상자만의 밀도)"""
        self._k += 1
        key = "%s%d" % (name or "p", self._k)
        sh = fn(f, t) if getattr(fn, "_factory", False) else fn
        self.mat(key, Mat(hpaint(sh, f, t, ss), tint=tint, glow=glow, overlay_tint=overlay_tint if overlay_tint is not None else 0, overlay_pad=overlay_pad))
        self.box(key, f, t, rot=rot, faces=faces, name=name or key, d=d)


def factory(fn):
    """(f, t) → 셰이더 를 만드는 함수 표시"""
    fn._factory = True
    return fn


# ------------------------------------------------------------------ 재질 셰이더

def metal(pal, base=0.55, top=0.2, grad=0.16, sheen=0.13, groove=None, bw=0.3):
    """금 · 강철 덩이: 윗면 밝게 · 아랫면 어둡게, 옆면은 위가 밝은 세로 그라데이션 + 윗쪽 반사 띠 + 깎은 모서리, groove = 옆면 가운데 새긴 홈 (높이 몫)"""
    @factory
    def mk(f, t):
        def fn(face, X, Y, Z, A, B):
            v = np.full_like(X, base)
            if face == "up":
                v = v + top
            elif face == "down":
                v = v - top * 1.1
            else:
                s, s0, s1, q, q0, q1 = local(face, X, Y, Z, f, t)
                qn = (q - q0) / max(q1 - q0, 1e-6)
                v = v + grad * (qn - 0.5) * 2 * 0.5
                v = v + sheen * np.exp(-((qn - 0.76) / 0.08) ** 2)
                if groove is not None and (q1 - q0) > 0.9:
                    v = v - 0.26 * np.exp(-((qn - groove) / 0.045) ** 2)
                    v = v + 0.14 * np.exp(-((qn - groove + 0.075) / 0.035) ** 2)
            v = v + bevel(face, X, Y, Z, f, t, bw=bw)
            return rgba(pal(v))
        return fn
    return mk


def gem(pal, base=0.46):
    """보석: 여덟 면 (번갈아 밝고 어둡게) · 가운데로 밝게 · 왼쪽 위 반짝임 · 가장자리 어둡게"""
    @factory
    def mk(f, t):
        def fn(face, X, Y, Z, A, B):
            du, dv = A - 0.5, B - 0.5
            ang = np.arctan2(dv, du)
            rr = np.minimum(1.0, np.maximum(np.abs(du), np.abs(dv)) * 2)
            v = base + 0.3 * (1 - rr) + 0.09 * np.sign(np.cos(ang * 4 + 0.4)) * rr
            v = v + 0.42 * np.exp(-(((du + 0.17) / 0.09) ** 2 + ((dv + 0.19) / 0.07) ** 2))
            v = v - 0.2 * sstep(0.78, 1.0, rr)
            if face == "up":
                v = v + 0.08
            elif face == "down":
                v = v - 0.12
            return rgba(pal(v))
        return fn
    return mk


def wrap(pal, period=1.45, slope=0.75, wire=None):
    """감은 손잡이: 비스듬한 띠 (띠마다 위 빛 · 아래 그늘 · 사이 틈) · 옆 가장자리 어둡게, wire = 띠 사이 금실 (램프)"""
    @factory
    def mk(f, t):
        def fn(face, X, Y, Z, A, B):
            s, s0, s1, q, q0, q1 = local(face, X, Y, Z, f, t)
            if face in ("up", "down"):
                return rgba(pal(np.full_like(X, 0.3)))
            ph = ((Y + (s - s0) * slope) / period) % 1.0
            v = 0.5 + 0.26 * (1 - sstep(0.0, 0.28, ph)) - 0.34 * sstep(0.6, 0.92, ph)
            sn = (s - s0) / max(s1 - s0, 1e-6)
            v = v - 0.14 * np.abs(2 * sn - 1) ** 3
            rgb = pal(v)
            if wire is not None:
                m = sstep(0.86, 0.9, ph) * (1 - sstep(0.97, 1.0, ph))
                rgb = mixc(rgb, wire(0.62 + 0.25 * (1 - np.abs(2 * sn - 1))), m)
            return rgba(rgb)
        return fn
    return mk


def blade(pal, zc=8.0, ylo=3.2, yhi=31.0, base=0.0, lit_hi=0.86, dark_lo=0.56, streaks=(9.5, 21.5), hot=None, spill=None, cool=None):
    """날의 넓은 면 (모델 ±X 면 — 가로 = Z, 칼끝 +Y): 가운데 등 (흰 줄 + 그 너머 그늘 줄) · 빛 받는 빗면 (Z < 가운데) 밝게 · 반대 빗면 어둡게 ·
    칼끝 쪽 밝게 · 비스듬한 반사 띠 둘. hot = (램프, 가장자리 쪽) — 단조 날의 달아오른 날 끝 (Z 큰 쪽), spill = (색, 세기, 위 끝 y) — 빛 홈의 빛이 날에
    번짐, cool = (색, 세기) — 그늘 쪽 빗면에 비친 하늘빛 (한 가지 회색이 아니게)"""
    @factory
    def mk(f, t):
        (fx, fy, fz), (tx, ty, tz) = f, t
        half = max((tz - fz) / 2, 1e-6)
        cz = (fz + tz) / 2

        def fn(face, X, Y, Z, A, B):
            gy = np.clip((Y - ylo) / (yhi - ylo), 0, 1)
            if face in ("east", "west"):
                zr = (Z - zc) / half
                v = np.where(zr < 0, lit_hi - 0.12 * np.clip(-zr, 0, 1), dark_lo + 0.12 * np.clip(zr, 0, 1))
                d = Z - zc
                v = np.where(np.abs(d) < 0.12, 1.0, v)
                v = np.where((d > 0.12) & (d < 0.32), v - 0.16, v)
                v = v + 0.08 * (gy - 0.5) + base
                sc = (Y - ylo) + (Z - zc) * 1.5
                for c0 in streaks:
                    v = v + 0.16 * np.exp(-((sc - c0) / 1.0) ** 2)
                rgb = pal(v)
                if cool is not None:
                    rgb = mixc(rgb, H(cool[0]), cool[1] * sstep(0.0, 0.5, zr))
                if spill is not None:
                    sp_ = np.exp(-((np.abs(d) - 0.75) / 0.5) ** 2) * (1 - sstep(spill[2] - 1.0, spill[2] + 0.5, Y))
                    rgb = mixc(rgb, H(spill[0]), spill[1] * sp_)
                if hot is not None:
                    hp, h0 = hot
                    k = sstep(h0, 1.0, (Z - cz) / half)
                    rgb = mixc(rgb, hp(0.45 + 0.5 * k), np.clip(k * 1.2, 0, 1))
                return rgba(rgb)
            if face in ("north", "south"):
                return rgba(pal(np.full_like(X, 0.78 + base)))
            return rgba(pal(np.full_like(X, (0.72 if face == "up" else 0.34) + base)))
        return fn
    return mk


def edge_metal(pal=EDGE, ylo=3.2, yhi=31.0, outer_z=None):
    """흰 날 끝: 바깥 (outer_z 쪽) 으로 갈수록 흰빛 · 칼끝 쪽 밝게"""
    @factory
    def mk(f, t):
        (fx, fy, fz), (tx, ty, tz) = f, t

        def fn(face, X, Y, Z, A, B):
            gy = np.clip((Y - ylo) / (yhi - ylo), 0, 1)
            if face in ("east", "west") and outer_z is not None:
                k = np.abs(Z - (fz + tz) / 2) / max((tz - fz) / 2, 1e-6)
                out = np.where(np.abs(Z - outer_z) < np.abs((fz + tz) / 2 - outer_z), 1.0, 0.0)
                v = 0.62 + 0.3 * np.where(out > 0, k, -k * 0.5) + 0.12 * gy
            else:
                v = np.full_like(X, 0.85) + 0.1 * gy
            return rgba(pal(v))
        return fn
    return mk


def energy(pal, axis="z", c=8.0, half=0.65, ylo=3.2, yhi=24.0, fade=0.3):
    """빛 홈 (진홍 · 단조 불빛 · 청금석): 가운데 흰빛 → 가장자리 짙은 색 (셀 띠), 위 끝으로 조금 어두워짐"""
    @factory
    def mk(f, t):
        def fn(face, X, Y, Z, A, B):
            gy = np.clip((Y - ylo) / (yhi - ylo), 0, 1)
            P = Z if axis == "z" else X
            if face in ("east", "west") if axis == "z" else face in ("north", "south"):
                k = np.clip(np.abs(P - c) / half, 0, 1)
                v = 1.0 - 0.22 * sstep(0.06, 0.3, k) - 0.33 * sstep(0.4, 0.7, k) - 0.15 * sstep(0.8, 1.0, k)
            elif face in ("up", "down"):
                v = np.full_like(X, 0.8)
            else:
                v = np.full_like(X, 0.5)
            v = v - fade * sstep(0.82, 1.0, gy)
            return rgba(pal(v))
        return fn
    return mk


# ------------------------------------------------------------------ 공용 이펙트 상자 (물들임)

def fx_bar():
    """BlockFx 줄 상자 (빛 줄 · 고리 · 호 · 가시 · 베인 줄 · 조각): 0 ~ 16. 긴 축 = Y. 옆면 가로 셀 띠 (가운데 흰빛 → 곱 그늘 → 보라 · 와인 쪽 가장자리, 물들임 0) + 겹 =
    가운데 뜨거운 심 줄 (물들임 1). 끝면은 네모 띠"""
    m = HModel("fx_bar", "BlockFx 줄 상자 (2026-11-15 HD — 색조를 옮긴 셀 띠 · 뜨거운 심 겹)", d=4.0)

    def fn(face, X, Y, Z, A, B):
        if face in ("up", "down"):
            t = np.maximum(np.abs(2 * A - 1), np.abs(2 * B - 1))
        else:
            t = np.abs(2 * A - 1)
        base = rgba(bands(t, [0.5, 0.8], [W3, MUL_MID, MUL_RIM], 0.06))
        ov = rgba(mixc(W3, HOT_EDGE, sstep(0.0, 0.3, t)), 1 - sstep(0.24, 0.34, t))
        return base, ov
    m.hbox(fn, (0, 0, 0), (16, 16, 16), tint=0, overlay_tint=1, overlay_pad=0.3, name="bar")
    return m


def fx_cube():
    """BlockFx 보석 상자 (반짝 조각 · 불티 · 번쩍 별 심): 면마다 네 빗면 (위 밝게 · 왼 · 오른 · 아래 짙게 — 곱 그늘) · 모서리 줄 (물들임 0) + 겹 = 가운데 뜨거운
    마름모 (물들임 1)"""
    m = HModel("fx_cube", "BlockFx 보석 상자 (2026-11-15 HD — 네 빗면 · 색조를 옮긴 그늘 · 뜨거운 마름모 겹)", d=4.0)

    def fn(face, X, Y, Z, A, B):
        du, dv = A - 0.5, B - 0.5
        top = dv < -np.abs(du)
        bot = dv > np.abs(du)
        left = (du < 0) & ~top & ~bot
        rr = np.maximum(np.abs(du), np.abs(dv)) * 2
        mult = np.where(top[..., None], W3, np.where(bot[..., None], MUL_RIM, np.where(left[..., None], (W3 * 0.55 + MUL_MID * 0.45), MUL_MID)))
        mult = mixc(mult, W3, 0.35 * (1 - rr))
        mult = mult * (1 - 0.25 * sstep(0.86, 1.0, rr))[..., None]
        if face == "down":
            mult = mult * np.array([0.78, 0.7, 0.85])
        k = np.abs(du) + np.abs(dv)
        ov = rgba(mixc(W3, HOT_EDGE, sstep(0.0, 0.22, k)), 1 - sstep(0.18, 0.26, k))
        return rgba(mult), ov
    m.hbox(fn, (0, 0, 0), (16, 16, 16), tint=0, overlay_tint=1, overlay_pad=0.3, name="cube")
    return m


def fx_orb():
    """BlockFx 불덩이 · 연기 · 빛 덩이: 둥근 계단 공 (상자 일곱 — 겹치는 겉면 없이) · 공 하나로 빛을 받은 그늘 (모델 왼쪽 위 앞에서 — 면마다 따로 칠하지 않아 얼룩이
    없음, 서버가 돌리면 빛이 소용돌이침) · 빛 받는 쪽은 물들임 색 → 반대쪽은 와인 · 보라 쪽 (물들임 0) + 겹 = 빛 받는 쪽 뜨거운 덮개 (물들임 1)"""
    m = HModel("fx_orb", "BlockFx 덩이 (2026-11-15 HD — 둥근 계단 공 · 색조를 옮긴 그늘 · 뜨거운 심)", d=3.0)
    R = 8.0
    c = 8.0

    L = np.array([-0.42, 0.78, -0.46])
    L = L / np.linalg.norm(L)

    def fn(face, X, Y, Z, A, B):
        P = np.stack([X - c, Y - c, Z - c], -1)
        n = P / np.maximum(np.sqrt((P ** 2).sum(-1)), 1e-6)[..., None]
        lam = (n * L).sum(-1)
        t = np.clip(0.5 - 0.5 * lam, 0, 1)
        base = rgba(bands(t, [0.42, 0.7], [W3, MUL_MID, MUL_RIM], 0.07))
        a = sstep(0.38, 0.62, lam)
        ov = rgba(mixc(HOT_EDGE, W3, sstep(0.55, 0.9, lam)), a)
        return base, ov
    ext = [(0.7, 0.7, 0.7), (1.0, 0.5, 0.5), (0.5, 1.0, 0.5), (0.5, 0.5, 1.0), (0.85, 0.85, 0.42), (0.85, 0.42, 0.85), (0.42, 0.85, 0.85)]
    for (hx, hy, hz) in ext:
        m.hbox(fn, (c - hx * R, c - hy * R, c - hz * R), (c + hx * R, c + hy * R, c + hz * R), tint=0, overlay_tint=1, overlay_pad=0.12, name="orb")
    return m


def fx_flame():
    """BlockFx 불꽃 혀: 밑 (8, 0, 8) 에서 위 28 로 좁아지며 S 자로 휘는 상자 여덟 · 아래 가운데 흰 심 (겹 — 물들임 1) → 물들임 색 (0) → 위 · 가장자리는 와인 · 보라
    쪽 그늘. 쥔 곳 = 밑 가운데"""
    m = HModel("fx_flame", "BlockFx 불꽃 혀 (2026-11-15 HD — 휘는 계단 불꽃 · 아래 흰 심 · 위로 짙어지는 색조)", d=3.0)
    Ht = 28.0
    segs = [(0.0, 2.2, 6.4, 0.0, 0.0), (1.6, 5.4, 9.6, 0.2, 0.0), (4.8, 9.6, 11.2, 0.5, -0.3), (9.0, 13.2, 10.0, 1.0, -0.6), (12.6, 16.4, 8.0, 1.3, -0.5),
            (15.8, 19.2, 6.0, 0.9, 0.0), (18.6, 21.8, 4.4, 0.1, 0.6), (21.2, 24.2, 3.0, -0.7, 1.0), (23.6, 26.4, 1.9, -1.2, 1.3), (25.8, 28.4, 1.0, -1.4, 1.5),
            (9.6, 12.8, 3.2, -3.9, 1.0), (12.2, 15.2, 2.2, -4.4, 1.4), (14.6, 17.0, 1.2, -4.7, 1.7)]
    FL_MID = np.array([0.98, 0.7, 0.74])
    FL_RIM = np.array([0.86, 0.3, 0.5])

    def fn(face, X, Y, Z, A, B):
        g = np.clip(Y / Ht, 0, 1)
        if face in ("up", "down"):
            e = np.maximum(np.abs(2 * A - 1), np.abs(2 * B - 1))
        else:
            e = np.abs(2 * A - 1)
        k = g * 0.85 + e * 0.3
        base = rgba(bands(k, [0.4, 0.7], [W3, FL_MID, FL_RIM], 0.07))
        a = (1 - sstep(0.26, 0.5, g)) * (1 - sstep(0.42, 0.72, e))
        if face == "down":
            a = a * 0
        ov = rgba(mixc(W3, HOT_EDGE, sstep(0.1, 0.45, g + e * 0.3)), a)
        return base, ov
    for (y0, y1, w, ox, oz) in segs:
        h = w / 2
        m.hbox(fn, (8 + ox - h, y0, 8 + oz - h), (8 + ox + h, y1, 8 + oz + h), tint=0, overlay_tint=1, overlay_pad=0.12, name="flame")
    return m


# ------------------------------------------------------------------ 칼 · 보물 (구운 색 — 물들임 없음)

def _blade_steps(m, steps, pal, edge_pal=EDGE, x0=7.4, x1=8.6, tip=None, edge=0.55, edge_x=(7.55, 8.45), hot=None, ylo=3.2, yhi=31.0, base=0.0,
                 lit_hi=0.86, dark_lo=0.56, edge_hot=None, spill=None, cool=None):
    """단으로 좁아지는 날 (steps = (y0, y1, zi) — Z 는 zi ~ 16 − zi) + 흰 날 끝 (양쪽)"""
    for (y0, y1, zi) in steps:
        m.hbox(blade(pal, ylo=ylo, yhi=yhi, base=base, lit_hi=lit_hi, dark_lo=dark_lo, hot=hot, spill=spill, cool=cool), (x0, y0, zi), (x1, y1, 16 - zi), name="blade")
    if tip:
        y0, y1, zi = tip
        m.hbox(blade(pal, ylo=ylo, yhi=yhi, base=base + 0.05, lit_hi=lit_hi, dark_lo=dark_lo, hot=hot, spill=spill, cool=cool), (x0 + 0.02, y0, zi), (x1 - 0.02, y1, 16 - zi),
               name="blade")
    for (y0, y1, zi) in steps:
        m.hbox(edge_metal(edge_pal, ylo=ylo, yhi=yhi, outer_z=zi - edge), (edge_x[0], y0, zi - edge), (edge_x[1], y1, zi), name="edge")
        m.hbox(edge_metal(edge_hot or edge_pal, ylo=ylo, yhi=yhi, outer_z=16 - zi + edge), (edge_x[0], y0, 16 - zi), (edge_x[1], y1, 16 - zi + edge), name="edge")


def phantom_sword():
    """팬텀 아처 투영 장검 (HD — 쥔 곳 · 칼끝 그대로: 손잡이 (8, −3.5, 8) · 칼끝 y 31): 차가운 푸른 강철 날 (등 · 빗면 · 반사 띠) · 흰 날 끝 · 진홍 빛 홈 (흰 심) ·
    금 코등이 (윗면 빛 · 새긴 홈) · 루비 · 검붉은 가죽 손잡이 (감은 띠 · 금 고리) · 금 자루 끝"""
    m = HModel("phantom_sword", "팬텀 아처 투영 장검 (2026-11-15 HD — 강철 날 · 진홍 빛 홈 · 금 코등이 · 루비 · 가죽 손잡이)", d=5.0)
    gold = metal(GOLD, base=0.55, groove=0.45)
    goldp = metal(GOLD, base=0.58)
    m.hbox(goldp, (7.2, -10.4, 7.2), (8.8, -8.8, 8.8), name="gold")
    m.hbox(gem(RUBY), (7.5, -10.0, 6.9), (8.5, -9.2, 9.1), name="ruby")
    m.hbox(wrap(LEATHER, wire=GOLD), (7.35, -8.8, 7.35), (8.65, -0.5, 8.65), name="grip")
    for y in (-6.6, -3.2):
        m.hbox(goldp, (7.15, y, 7.15), (8.85, y + 0.6, 8.85), name="gold")
    m.hbox(gold, (7.0, -0.5, 2.6), (9.0, 1.3, 13.4), name="gold")
    m.hbox(goldp, (6.8, -0.9, 2.0), (9.2, 1.7, 3.2), name="gold")
    m.hbox(goldp, (6.8, -0.9, 12.8), (9.2, 1.7, 14.0), name="gold")
    m.hbox(gem(RUBY, 0.5), (6.7, -0.4, 7.1), (9.3, 1.2, 8.9), rot=("x", 45, (8, 0.4, 8)), name="ruby")
    m.hbox(metal(STEEL, base=0.46, top=0.24), (7.2, 1.3, 5.4), (8.8, 3.2, 10.6), name="neck")
    m.hbox(energy(CRIMSON_E, c=8.0, half=0.65), (7.28, 3.2, 7.35), (8.72, 24.0, 8.65), name="fuller")
    steps = ((3.2, 9.0, 4.6), (9.0, 14.0, 4.8), (14.0, 19.0, 5.0), (19.0, 23.5, 5.4), (23.5, 27.0, 6.1), (27.0, 29.5, 6.8))
    _blade_steps(m, steps, STEEL, tip=(29.5, 31.0, 7.4), spill=("#FF7466", 0.42, 24.0), cool=("#7FA6FF", 0.22))
    return m


def forge_sword():
    """블레이드 레인저 투영 검 (단조): 검푸른 강철 날 (한쪽 날 끝이 달아오른 주황 → 노랑 · 코등이 쪽 날에 식어 가는 불빛 줄) · 검은 쇠 코등이 (가운데 달아오른 홈) ·
    호박 불씨 보석 · 숯빛 손잡이 (구리 실) · 쇠 자루 끝. 쥔 곳 · 칼끝은 투영 장검과 같음"""
    m = HModel("forge_sword", "블레이드 레인저 단조 검 (2026-11-15 HD — 검푸른 강철 · 달아오른 날 끝 · 검은 쇠 코등이 · 불씨 보석)", d=5.0)
    iron = metal(DSTEEL, base=0.42, top=0.22, groove=0.5)
    m.hbox(metal(DSTEEL, base=0.45), (7.1, -10.6, 7.1), (8.9, -8.8, 8.9), name="iron")
    m.hbox(gem(TOPAZ, 0.55), (7.45, -10.2, 6.8), (8.55, -9.2, 9.2), name="ember")
    m.hbox(wrap(ramp("#0B0B0E", "#17171C", "#25242B", "#36333C", "#4B4652", "#615A6A"), period=1.3, wire=ramp("#5A2408", "#8E3E10", "#C2621C", "#E58B3A", "#FFB56A")),
           (7.35, -8.8, 7.35), (8.65, -0.5, 8.65), name="grip")
    m.hbox(metal(ramp("#4A1A05", "#7A2E0A", "#A84A14", "#CF6E22", "#EE9A45", "#FFC27C"), base=0.55), (7.12, -4.6, 7.12), (8.88, -3.9, 8.88), name="copper")
    m.hbox(iron, (6.9, -0.6, 3.0), (9.1, 1.4, 13.0), name="iron")
    m.hbox(metal(DSTEEL, base=0.5), (6.7, -1.0, 2.2), (9.3, 1.8, 3.4), name="iron")
    m.hbox(metal(DSTEEL, base=0.5), (6.7, -1.0, 12.6), (9.3, 1.8, 13.8), name="iron")
    m.hbox(energy(MOLTEN, axis="x", c=8.0, half=1.1, ylo=-0.6, yhi=1.4, fade=0.0), (6.75, -0.2, 6.6), (9.25, 1.0, 9.4), name="hot")
    m.hbox(metal(DSTEEL, base=0.4, top=0.24), (7.2, 1.4, 5.0), (8.8, 3.2, 11.0), name="neck")
    # 날: 등 (Z 작은 쪽) 은 두꺼운 쇠, 날 끝 (Z 큰 쪽) 이 달아오름
    steps = ((3.2, 10.0, 4.4), (10.0, 16.0, 4.6), (16.0, 21.5, 4.9), (21.5, 25.5, 5.5), (25.5, 28.5, 6.4), (28.5, 30.2, 7.2))
    hot = (ramp("#5E1204", "#A3260A", "#E2470E", "#FF7418", "#FFA030", "#FFD064"), 0.15)
    _blade_steps(m, steps, DSTEEL, tip=(30.2, 31.2, 7.6), hot=hot, base=0.1, lit_hi=0.78, dark_lo=0.5, cool=("#6E9CFF", 0.25),
                 edge_pal=ramp("#3C4659", "#7F8CA6", "#C9D3E4", "#F0F4FA"), edge_hot=ramp("#B2330A", "#E8540F", "#FF7A1A", "#FF9A2E", "#FFC04A", "#FFE38A"))
    # 식어 가는 불빛 줄 (코등이 쪽 날에 가로로 — 위로 갈수록 짧고 어둡게)
    for i, (y, L) in enumerate(((5.0, 1.0), (7.4, 0.82), (10.0, 0.64), (13.0, 0.46), (16.4, 0.3))):
        z0 = 16 - 4.6 - 3.0 * L
        m.hbox(energy(MOLTEN, axis="x", c=8.0, half=0.7, ylo=0, yhi=40, fade=0.0), (7.34, y, z0), (8.66, y + 0.42, 16 - 4.62), name="vein")
    return m


def treasure_sword():
    """아스널 킹 보물 · 금빛 장검 (원작에 없는 새 디자인): 흰 금 잎 날 (등 · 빗면 · 반사 띠) · 청금석 홈 + 금 룬 점 · 날개 금 코등이 + 에메랄드 · 진홍 감은 손잡이 (금실)
    · 루비 자루 끝. 쥔 곳 · 칼끝은 투영 장검과 같음"""
    m = HModel("treasure_sword", "아스널 킹 보물 · 금빛 장검 (2026-11-15 HD — 흰 금 잎 날 · 청금석 홈 · 날개 코등이 · 에메랄드 · 루비)", d=5.0)
    goldp = metal(GOLD, base=0.6)
    m.hbox(metal(GOLD, base=0.6, groove=0.5), (6.9, -11.4, 6.9), (9.1, -9.4, 9.1), name="gold")
    m.hbox(gem(RUBY, 0.52), (7.3, -11.0, 6.55), (8.7, -9.8, 9.45), name="ruby")
    m.hbox(metal(GOLD, base=0.62), (7.5, -12.4, 7.5), (8.5, -11.4, 8.5), name="gold")
    m.hbox(wrap(CRIMSON_WRAP, period=1.2, wire=GOLD), (7.38, -9.4, 7.38), (8.62, -0.6, 8.62), name="grip")
    # 날개 코등이
    m.hbox(metal(GOLD, base=0.58, groove=0.5), (6.6, -0.9, 5.0), (9.4, 1.7, 11.0), name="gold")
    m.hbox(goldp, (6.95, -0.3, 1.6), (9.05, 1.2, 5.0), name="gold")
    m.hbox(goldp, (6.95, -0.3, 11.0), (9.05, 1.2, 14.4), name="gold")
    m.hbox(goldp, (6.95, 1.2, 1.0), (9.05, 3.0, 2.5), name="gold")
    m.hbox(goldp, (6.95, 1.2, 13.5), (9.05, 3.0, 15.0), name="gold")
    m.hbox(metal(GOLD, base=0.7), (7.05, 3.0, 0.7), (8.95, 3.8, 1.9), name="gold")
    m.hbox(metal(GOLD, base=0.7), (7.05, 3.0, 14.1), (8.95, 3.8, 15.3), name="gold")
    m.hbox(gem(EMERALD, 0.5), (6.35, -0.3, 7.0), (9.65, 1.1, 9.0), rot=("x", 45, (8, 0.4, 8)), name="emerald")
    m.hbox(gem(SAPPHIRE, 0.5), (6.75, 0.0, 2.6), (9.25, 0.9, 3.5), name="sapph")
    m.hbox(gem(SAPPHIRE, 0.5), (6.75, 0.0, 12.5), (9.25, 0.9, 13.4), name="sapph")
    # 잎 날 (가운데가 조금 넓음) · 청금석 홈
    steps = ((1.7, 8.0, 4.9), (8.0, 15.0, 4.55), (15.0, 21.0, 4.95), (21.0, 25.5, 5.7), (25.5, 28.6, 6.6))
    _blade_steps(m, steps, PGOLD, tip=(28.6, 31.2, 7.3), ylo=1.7, base=-0.02, lit_hi=0.8, dark_lo=0.4, cool=("#D88A2A", 0.25),
                 edge_pal=ramp("#E9C46A", "#FFEAB0", "#FFF9E8", "#FFFFFF"), spill=("#9FC2FF", 0.3, 20.5))
    m.hbox(energy(LAPIS, c=8.0, half=0.6, ylo=2.0, yhi=21.0, fade=0.15), (7.3, 2.4, 7.4), (8.7, 20.5, 8.6), name="lapis")
    for y in (4.2, 7.4, 10.6, 13.8, 17.0):
        m.hbox(metal(GOLD, base=0.72), (7.26, y, 7.72), (8.74, y + 0.7, 8.28), name="rune")
    return m


def treasure_spear():
    """아스널 킹 보물 · 창 (새 디자인): 진홍 옻칠 자루 (금 띠 넷 · 금 물미) · 금 창목 · 날개 가로대 (끝이 위로 말림) · 은빛 잎 창날 + 사파이어. 쥔 곳 (8, −3.5, 8) ·
    창끝 y 31.8"""
    m = HModel("treasure_spear", "아스널 킹 보물 · 창 (2026-11-15 HD — 진홍 옻칠 자루 · 금 띠 · 날개 가로대 · 은빛 잎 창날 · 사파이어)", d=5.0)
    gold = metal(GOLD, base=0.58, groove=0.5)
    goldp = metal(GOLD, base=0.6)
    m.hbox(metal(LACQUER, base=0.5, top=0.18, sheen=0.16), (7.35, -15.6, 7.35), (8.65, 16.4, 8.65), name="shaft")
    m.hbox(goldp, (7.1, -16.0, 7.1), (8.9, -14.4, 8.9), name="gold")
    for y in (-8.0, -1.2, 5.6, 11.8):
        m.hbox(goldp, (7.15, y, 7.15), (8.85, y + 0.7, 8.85), name="gold")
    m.hbox(gold, (6.85, 16.0, 6.85), (9.15, 19.2, 9.15), name="gold")
    m.hbox(metal(GOLD, base=0.66), (6.6, 17.2, 6.6), (9.4, 18.0, 9.4), name="gold")
    m.hbox(goldp, (7.45, 18.6, 3.4), (8.55, 19.8, 12.6), name="gold")
    m.hbox(goldp, (7.45, 19.8, 3.4), (8.55, 21.4, 4.5), name="gold")
    m.hbox(goldp, (7.45, 19.8, 11.5), (8.55, 21.4, 12.6), name="gold")
    m.hbox(gem(RUBY, 0.5), (7.25, 21.3, 3.55), (8.75, 22.3, 4.35), name="ruby")
    m.hbox(gem(RUBY, 0.5), (7.25, 21.3, 11.65), (8.75, 22.3, 12.45), name="ruby")
    steps = ((19.2, 22.0, 5.7), (22.0, 25.4, 5.2), (25.4, 28.0, 6.0), (28.0, 30.2, 6.8))
    _blade_steps(m, steps, STEEL, tip=(30.2, 31.8, 7.45), ylo=19.2, yhi=31.8, base=0.06, x0=7.45, x1=8.55, edge=0.5, edge_x=(7.6, 8.4))
    m.hbox(gem(SAPPHIRE, 0.52), (7.15, 21.0, 7.2), (8.85, 23.6, 8.8), name="sapph")
    return m


def treasure_axe():
    """아스널 킹 보물 · 도끼 (새 디자인): 흑단 자루 (금 띠 · 금 물미) · 금 머리 (초승달 날 — 흰 강철 날 끝 · 아래로 처진 턱) · 뒤 가시 · 꼭대기 금 창끝 · 자수정.
    쥔 곳 (8, −3.5, 8) · 꼭대기 y 31"""
    m = HModel("treasure_axe", "아스널 킹 보물 · 도끼 (2026-11-15 HD — 흑단 자루 · 금 초승달 머리 · 강철 날 끝 · 자수정)", d=5.0)
    gold = metal(GOLD, base=0.58, groove=0.5)
    goldp = metal(GOLD, base=0.6)
    m.hbox(metal(EBONY, base=0.5, top=0.2, sheen=0.18), (7.35, -15.6, 7.35), (8.65, 26.0, 8.65), name="shaft")
    m.hbox(goldp, (7.1, -16.0, 7.1), (8.9, -14.4, 8.9), name="gold")
    for y in (-7.0, 0.0, 7.0):
        m.hbox(goldp, (7.15, y, 7.15), (8.85, y + 0.7, 8.85), name="gold")
    m.hbox(gold, (7.0, 16.6, 7.0), (9.0, 25.4, 9.0), name="gold")
    m.hbox(metal(GOLD, base=0.66), (7.25, 25.4, 7.25), (8.75, 27.6, 8.75), name="gold")
    m.hbox(metal(GOLD, base=0.7), (7.5, 27.6, 7.5), (8.5, 30.0, 8.5), name="gold")
    m.hbox(metal(GOLD, base=0.76), (7.72, 30.0, 7.72), (8.28, 31.0, 8.28), name="gold")
    # 머리 (+Z 쪽): 목 · 초승달 날 (위아래로 넓어짐) · 강철 날 끝
    m.hbox(gold, (7.45, 18.2, 9.0), (8.55, 24.0, 10.8), name="gold")
    m.hbox(metal(GOLD, base=0.6, groove=0.55), (7.5, 16.6, 10.8), (8.5, 25.6, 12.2), name="gold")
    m.hbox(metal(GOLD, base=0.62), (7.55, 14.6, 12.2), (8.45, 27.2, 13.4), name="gold")
    m.hbox(metal(GOLD, base=0.64), (7.58, 13.2, 13.4), (8.42, 28.4, 14.2), name="gold")
    # 볼록한 강철 날 끝 (가운데가 가장 바깥 — 위아래 끝은 안으로)
    m.hbox(edge_metal(EDGE, ylo=12, yhi=30, outer_z=15.6), (7.66, 16.4, 14.2), (8.34, 25.4, 15.6), name="edge")
    m.hbox(edge_metal(EDGE, ylo=12, yhi=30, outer_z=15.1), (7.66, 13.8, 14.2), (8.34, 16.4, 15.1), name="edge")
    m.hbox(edge_metal(EDGE, ylo=12, yhi=30, outer_z=15.1), (7.66, 25.4, 14.2), (8.34, 28.0, 15.1), name="edge")
    m.hbox(edge_metal(EDGE, ylo=12, yhi=30, outer_z=14.6), (7.68, 12.4, 13.4), (8.32, 13.8, 14.6), name="edge")
    m.hbox(edge_metal(EDGE, ylo=12, yhi=30, outer_z=14.6), (7.68, 28.0, 13.4), (8.32, 29.4, 14.6), name="edge")
    # 뒤 가시 (−Z)
    m.hbox(goldp, (7.55, 19.4, 4.4), (8.45, 22.6, 7.0), name="gold")
    m.hbox(metal(GOLD, base=0.7), (7.68, 20.2, 2.9), (8.32, 21.8, 4.4), name="gold")
    m.hbox(gem(AMETHYST, 0.5), (7.2, 19.6, 9.4), (8.8, 22.2, 10.9), name="amethyst")
    m.hbox(gem(EMERALD, 0.5), (7.3, 20.2, 7.1), (8.7, 21.6, 8.9), rot=("y", 45, (8, 20.9, 8)), name="emerald")
    return m


# ------------------------------------------------------------------ 2D 그림판 (금빛 문 · 땅 마법진)

class Canvas:
    """[-1, 1]² 그림판 (y 위가 +): 거리장 선 · 빛 번짐 · 점 — 겹쳐 칠함 (알파)"""

    def __init__(self, N):
        self.N = N
        g = (np.arange(N) + 0.5) / N * 2 - 1
        self.x, self.y = np.meshgrid(g, -g)
        self.r = np.hypot(self.x, self.y)
        self.th = np.arctan2(self.y, self.x)
        self.rgb = np.zeros((N, N, 3))
        self.a = np.zeros((N, N))
        self.px = 2.0 / N

    def over(self, col, a):
        a = np.clip(np.asarray(a, float), 0, 1)
        col = np.broadcast_to(np.asarray(col, float), self.rgb.shape)
        oa = a + self.a * (1 - a)
        self.rgb = (col * a[..., None] + self.rgb * (self.a * (1 - a))[..., None]) / np.maximum(oa, 1e-9)[..., None]
        self.a = oa

    def line(self, d, w):
        return 1 - sstep(w / 2 - self.px * 0.75, w / 2 + self.px * 0.75, np.abs(d))

    def stroke(self, d, w, core, glow=None, gw=0.0, ga=0.55):
        if glow is not None and gw > 0:
            self.over(glow, ga * np.exp(-(np.maximum(np.abs(d) - w / 2, 0) / gw) ** 2))
        self.over(core, self.line(d, w))

    def seg(self, p, q):
        p, q = np.asarray(p, float), np.asarray(q, float)
        d = q - p
        L2 = max(float(d @ d), 1e-12)
        t = np.clip(((self.x - p[0]) * d[0] + (self.y - p[1]) * d[1]) / L2, 0, 1)
        return np.hypot(self.x - (p[0] + t * d[0]), self.y - (p[1] + t * d[1]))

    def segs(self, pts_pairs):
        d = np.full_like(self.x, 9.0)
        for p, q in pts_pairs:
            d = np.minimum(d, self.seg(p, q))
        return d

    def img(self, ss):
        return down(np.concatenate([self.rgb, self.a[..., None]], -1), ss)


def poly(n, R, a0=math.pi / 2):
    return [(R * math.cos(a0 + 2 * math.pi * k / n), R * math.sin(a0 + 2 * math.pi * k / n)) for k in range(n)]


def closed(pts):
    return [(pts[i], pts[(i + 1) % len(pts)]) for i in range(len(pts))]


def rune_strokes(k, th, r0, r1, wt):
    """룬 글자 하나 (원작 없는 새 꼴 — 줄기 + 가지 · 갈매기 · 마름모 · 가로대): 각 th · 반지름 r0 ~ r1 · 너비 ±wt"""
    n = np.array([math.cos(th), math.sin(th)])
    t = np.array([-math.sin(th), math.cos(th)])

    def P(rr, tt):
        return tuple(n * (r0 + (r1 - r0) * rr) + t * wt * tt)
    kind = (k * 7 + 3) % 6
    if kind == 0:
        return [(P(0, 0), P(1, 0)), (P(0.55, 0), P(0.95, 1))]
    if kind == 1:
        return [(P(0, 0), P(1, 0)), (P(0.25, -1), P(0.6, 1)), (P(0.55, -1), P(0.9, 1))]
    if kind == 2:
        return [(P(0, -1), P(1, 0)), (P(1, 0), P(0, 1))]
    if kind == 3:
        return [(P(0, 0), P(1, 0)), (P(0.5, -1), P(0.5, 1))]
    if kind == 4:
        return [(P(0.1, 0), P(0.5, 1)), (P(0.5, 1), P(0.9, 0)), (P(0.9, 0), P(0.5, -1)), (P(0.5, -1), P(0.1, 0))]
    return [(P(0, -1), P(0, 1)), (P(0, 0), P(1, 0)), (P(1, -0.8), P(1, 0.8))]


def gate_disc_img(N, ss=3):
    """금빛 문 판 (원판 반지름 1 = 상자 반 너비): 흰 금 심 → 금빛 물결 (마루 밝게 · 골 호박) · 빛살 열여섯 · 청록 고리 · 흰 반짝임 · 가장자리 호박 그늘 → 0.9 밖 투명"""
    cv = Canvas(N * ss)
    r, th = cv.r, cv.th
    Rv = 0.9
    rn = r / Rv
    base = 0.86 - 0.42 * np.clip(rn, 0, 1) ** 1.2
    wob = 0.035 * np.sin(3 * th + 1.3) + 0.025 * np.sin(5 * th - 0.4) + 0.02 * np.sin(2 * th + 2.2)
    w = 0.5 + 0.5 * np.cos(2 * math.pi * (rn * 6.3 - 0.12 - 0.35 * rn ** 2 + wob * (0.4 + rn) + th / (2 * math.pi) * 0.5))
    w = sstep(0.3, 0.7, w) * (0.75 + 0.25 * np.cos(4 * th + rn * 9.0))
    v = base + 0.2 * w * sstep(0.1, 0.22, rn) - 0.06
    rays = np.maximum(0, np.cos(8 * th)) ** 14 * 0.1 * sstep(0.12, 0.3, rn) * (1 - sstep(0.6, 0.9, rn))
    v = v + rays
    col = GOLD(np.clip(v, 0, 1))
    core = 1 - sstep(0.05, 0.17, rn)
    col = mixc(col, H("#FFFDF2"), core)
    col = mixc(col, H("#6A3209"), sstep(0.9, 0.99, rn) * 0.85)
    cv.over(col, (rn < 1.0).astype(float))
    cv.stroke(r - Rv * 0.68, 0.01, H("#C8FFF6"), glow=H("#2FC6B4"), gw=0.026, ga=0.42)
    cv.stroke(r - Rv * 0.84, 0.008, H("#FFF2C2"), glow=H("#F2B640"), gw=0.02, ga=0.4)
    rng = np.random.default_rng(42)
    for _ in range(11):
        rr = rng.uniform(0.24, 0.62) * Rv
        aa = rng.uniform(0, 2 * math.pi)
        cx, cy = rr * math.cos(aa), rr * math.sin(aa)
        s = rng.uniform(0.035, 0.06)
        d = cv.segs([((cx - s, cy), (cx + s, cy)), ((cx, cy - s), (cx, cy + s))])
        dd = np.hypot(cv.x - cx, cv.y - cy)
        cv.over(H("#FFFFFF"), (1 - sstep(0.0, s, dd)) * (1 - sstep(0.0, 0.006, d - 0.004 * (1 - dd / s))))
        cv.over(H("#FFF6D0"), 0.5 * np.exp(-(dd / (s * 0.45)) ** 2))
    cv.a = np.where(r < Rv, cv.a, 0.0)
    return cv.img(ss)


def sigil_img(N, style, ss=2):
    """땅 마법진 (위에서 봄 — 마크 면 밝기 1): 옅은 빛 바닥 · 두꺼운 겉 고리 · 가는 고리 · 룬 띠 · 안 고리 + 점 · 직업 도형 · 안쪽 원 + 눈금 · 가운데 문장.
    style: crimson (팬텀 아처 — 육망성 · 칼) · gold (아스널 킹 — 팔각 별 · 햇살 · 청금석) · forge (블레이드 레인저 — 톱니 · 엇갈린 칼 · 푸른 강철 고리)"""
    P = {
        "crimson": dict(fill=H("#FF9C8A"), glow=H("#E3233A"), core=H("#FFE0CC"), accent=H("#FFC566"), accent_glow=H("#FF8A3A"), dot=H("#FFD27A"),
                        ring2=H("#FF6A55")),
        "gold": dict(fill=H("#FFE1A0"), glow=H("#F2AE34"), core=H("#FFF6DA"), accent=H("#7FA8FF"), accent_glow=H("#2F62E0"), dot=H("#4BE0A0"),
                     ring2=H("#FFD978")),
        "forge": dict(fill=H("#FFB078"), glow=H("#FF6414"), core=H("#FFEBC4"), accent=H("#9CC8FF"), accent_glow=H("#3D7BEA"), dot=H("#FF3A12"),
                      ring2=H("#FFB24A")),
    }[style]
    cv = Canvas(N * ss)
    r, th = cv.r, cv.th
    # 옅은 빛 바닥
    cv.over(P["fill"], (0.14 + 0.1 * (1 - r)) * (r < 0.95))
    cv.over(P["glow"], 0.12 * np.exp(-((r - 0.62) / 0.3) ** 2) * (r < 0.95))
    # 겉 고리 (두껍게) · 가는 고리
    cv.stroke(r - 0.925, 0.046, P["core"], glow=P["glow"], gw=0.04, ga=0.75)
    cv.stroke(r - 0.862, 0.014, P["ring2"], glow=P["glow"], gw=0.018, ga=0.5)
    # 룬 띠
    pairs = []
    n_r = 28
    for k in range(n_r):
        pairs += rune_strokes(k, 2 * math.pi * (k + 0.5) / n_r, 0.79, 0.85, 0.022)
    cv.stroke(cv.segs(pairs), 0.012, P["accent"], glow=P["accent_glow"], gw=0.014, ga=0.6)
    # 안 고리 + 점
    cv.stroke(r - 0.765, 0.02, P["core"], glow=P["glow"], gw=0.024, ga=0.6)
    for k in range(12):
        a = 2 * math.pi * k / 12
        dd = np.hypot(cv.x - 0.765 * math.cos(a), cv.y - 0.765 * math.sin(a))
        cv.over(P["glow"], 0.6 * np.exp(-(dd / 0.03) ** 2))
        cv.over(P["dot"], 1 - sstep(0.016, 0.024, dd))
    # 직업 도형
    if style == "crimson":
        tri1, tri2 = poly(3, 0.765), poly(3, 0.765, -math.pi / 2)
        d = cv.segs(closed(tri1) + closed(tri2))
        cv.stroke(d, 0.02, P["core"], glow=P["glow"], gw=0.032, ga=0.7)
        for (px, py) in tri1 + tri2:
            dd = np.hypot(cv.x - px * 0.66, cv.y - py * 0.66)
        hexr = poly(6, 0.44, 0)
        cv.stroke(cv.segs(closed(hexr)), 0.011, P["accent"], glow=P["accent_glow"], gw=0.014, ga=0.55)
    elif style == "gold":
        sq1, sq2 = poly(4, 0.765, math.pi / 4), poly(4, 0.765, 0)
        cv.stroke(cv.segs(closed(sq1) + closed(sq2)), 0.019, P["core"], glow=P["glow"], gw=0.03, ga=0.65)
        rays = []
        for k in range(16):
            a = 2 * math.pi * (k + 0.5) / 16
            rays.append(((0.46 * math.cos(a), 0.46 * math.sin(a)), (0.6 * math.cos(a), 0.6 * math.sin(a))))
        cv.stroke(cv.segs(rays), 0.01, P["ring2"], glow=P["glow"], gw=0.014, ga=0.5)
        for (px, py) in poly(8, 0.765, math.pi / 8):
            pass
        for k in range(8):
            a = 2 * math.pi * k / 8
            cx, cy = 0.62 * math.cos(a), 0.62 * math.sin(a)
            dd = np.abs(cv.x - cx) + np.abs(cv.y - cy)
            cv.over(P["accent_glow"], 0.5 * np.exp(-(dd / 0.04) ** 2))
            cv.over(P["dot"], 1 - sstep(0.024, 0.032, dd))
    else:
        teeth = []
        n_t = 24
        for k in range(n_t):
            a0 = 2 * math.pi * k / n_t
            a1 = a0 + 2 * math.pi / n_t * 0.5
            for (ra, rb, aa, bb) in ((0.68, 0.68, a0, a1), (0.68, 0.73, a1, a1), (0.73, 0.73, a1, a0 + 2 * math.pi / n_t), (0.73, 0.68, a0 + 2 * math.pi / n_t, a0 + 2 * math.pi / n_t)):
                teeth.append(((ra * math.cos(aa), ra * math.sin(aa)), (rb * math.cos(bb), rb * math.sin(bb))))
        cv.stroke(cv.segs(teeth), 0.016, P["core"], glow=P["glow"], gw=0.026, ga=0.65)
        dia = poly(4, 0.6, math.pi / 2)
        cv.stroke(cv.segs(closed(dia)), 0.016, P["ring2"], glow=P["glow"], gw=0.024, ga=0.55)
    # 안쪽 원 + 눈금
    cv.stroke(r - 0.42, 0.017, P["core"], glow=P["glow"], gw=0.024, ga=0.6)
    ticks = []
    for k in range(36):
        a = 2 * math.pi * k / 36
        L = 0.05 if k % 3 == 0 else 0.028
        ticks.append(((0.425 * math.cos(a), 0.425 * math.sin(a)), ((0.425 + L) * math.cos(a), (0.425 + L) * math.sin(a))))
    cv.stroke(cv.segs(ticks), 0.01, P["accent"], glow=P["accent_glow"], gw=0.012, ga=0.45)
    cv.stroke(r - 0.3, 0.012, P["accent"], glow=P["accent_glow"], gw=0.016, ga=0.55)
    # 가운데 문장
    if style == "crimson":
        blade_ = [((0, 0.26), (0.035, 0.0)), ((0.035, 0.0), (0, -0.2)), ((0, -0.2), (-0.035, 0.0)), ((-0.035, 0.0), (0, 0.26))]
        cv.stroke(cv.segs(blade_), 0.018, P["core"], glow=P["glow"], gw=0.034, ga=0.75)
        cv.stroke(cv.segs([((-0.1, -0.07), (0.1, -0.07))]), 0.022, P["accent"], glow=P["accent_glow"], gw=0.024, ga=0.65)
    elif style == "gold":
        star = []
        for k in range(8):
            a = 2 * math.pi * k / 8
            L = 0.24 if k % 2 == 0 else 0.13
            star.append(((0, 0), (L * math.cos(a), L * math.sin(a))))
        cv.stroke(cv.segs(star), 0.018, P["core"], glow=P["glow"], gw=0.034, ga=0.75)
        dd = np.hypot(cv.x, cv.y)
        cv.over(P["accent"], 1 - sstep(0.035, 0.045, dd))
    else:
        b1 = [((-0.2, -0.2), (0.2, 0.2))]
        b2 = [((0.2, -0.2), (-0.2, 0.2))]
        cv.stroke(cv.segs(b1 + b2), 0.026, P["core"], glow=P["glow"], gw=0.036, ga=0.8)
        cv.stroke(cv.segs([((-0.2, -0.2), (-0.13, -0.13)), ((0.2, -0.2), (0.13, -0.13))]), 0.036, P["accent"], glow=P["accent_glow"], gw=0.024, ga=0.65)
    cv.a = np.where(r < 0.975, cv.a, 0.0)
    return cv.img(ss)


def img_paint(img, faces):
    """미리 그린 그림 (RGBA float) 을 면 크기로"""
    pil = Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")

    def paint(w, h, face, rng, frame):
        if face not in faces:
            return np.zeros((h, w, 4)), None
        im = pil if pil.size == (w, h) else pil.resize((w, h), Image.LANCZOS)
        return np.asarray(im, float) / 255.0, None
    return paint


def ring_xy(m, fn, c, R, wr, z0, z1, n=16, name="ring", d=None):
    """XY 면 고리 (Z 축 둘레): 가운데 c · 반지름 R · 띠 너비 wr · 두께 z0 ~ z1 — 접선 상자 n 개 (22.5° 단위 · 90° 를 넘으면 가로 세로를 바꿈)"""
    cx, cy = c
    L = 2 * math.pi * R / n * 1.07
    for k in range(n):
        thd = k * 360.0 / n
        a = math.radians(thd)
        px, py = cx + R * math.cos(a), cy + R * math.sin(a)
        tang = (thd + 90.0) % 180.0
        if tang > 90.0 + 1e-6:
            tang -= 180.0
        if abs(tang) <= 45.0 + 1e-6:
            f, t = (px - L / 2, py - wr / 2, z0), (px + L / 2, py + wr / 2, z1)
            ang = tang
        else:
            f, t = (px - wr / 2, py - L / 2, z0), (px + wr / 2, py + L / 2, z1)
            ang = tang - 90.0 if tang > 0 else tang + 90.0
        m.hbox(fn, f, t, rot=("z", ang, (px, py, (z0 + z1) / 2)) if abs(ang) > 1e-6 else None, name=name, d=d)


def gold_gate():
    """아스널 킹 금빛 문 (모델 하나 — BlockFx.gate): XY 원판 (앞 · 뒤 같은 그림 — 흰 금 심 · 금빛 물결 · 빛살 · 청록 고리 · 흰 반짝임) + 두꺼운 금 테 (마디 열여섯 ·
    새긴 홈) + 테의 보석 여덟 (루비 · 사파이어 · 에메랄드 · 토파즈 — 앞뒤) + 바깥 금 가시 여덟 (햇살 문장). 가운데 (8, 8, 8) · 지름 약 18 · 얇은 축 Z"""
    # 2026-11-15 원판 251 픽셀 (17 텍셀/픽셀 — 그림판 둘이 512 한 장에 들어가게: 20 이면 1024 로 커짐)
    m = HModel("gold_gate", "아스널 킹 금빛 문 (2026-11-15 HD — 금빛 물결 판 · 금 테 · 보석 넷 색 · 햇살 가시)", d=17.0)
    img = gate_disc_img(252)
    m.mat("disc", Mat(img_paint(img, ("north", "south")), glow=True))
    m.box("disc", (0.6, 0.6, 7.86), (15.4, 15.4, 8.14), faces=["north", "south"])
    ring_xy(m, metal(GOLD, base=0.6, top=0.22, groove=0.5, bw=0.18), (8, 8), 7.25, 1.3, 7.2, 8.8, n=16, name="frame", d=7.0)
    ring_xy(m, metal(GOLD, base=0.5, top=0.2, bw=0.12), (8, 8), 6.45, 0.4, 7.45, 8.55, n=16, name="inner", d=6.0)
    gems = [RUBY, SAPPHIRE, EMERALD, TOPAZ]
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        px, py = 8 + 7.25 * math.cos(a), 8 + 7.25 * math.sin(a)
        g = gem(gems[k % 4], 0.52)
        for (z0, z1) in ((6.85, 7.25), (8.75, 9.15)):
            m.hbox(g, (px - 0.62, py - 0.62, z0), (px + 0.62, py + 0.62, z1), rot=("z", 45, (px, py, (z0 + z1) / 2)), name="gem", d=9.0)
    for k in range(8):
        a = math.radians(k * 45)
        px, py = 8 + 8.25 * math.cos(a), 8 + 8.25 * math.sin(a)
        L = 1.25 if k % 2 == 0 else 0.85
        m.hbox(metal(GOLD, base=0.66, top=0.2, bw=0.14), (px - L / 2, py - L / 2, 7.55), (px + L / 2, py + L / 2, 8.45), rot=("z", 45, (px, py, 8.0)), name="spike",
               d=7.0)
    return m


def gold_link():
    """아스널 킹 금 사슬 고리 하나 (BlockFx.chain): 가운데 (8, 8, 8) · 긴 쪽 Y 약 11 · 너비 X 5.6 · 두께 Z 1.4 — 금 테 (윗면 빛 · 새긴 홈) · 모서리 둥글게"""
    m = HModel("gold_link", "아스널 킹 금 사슬 고리 (2026-11-15 HD — 금 테 · 둥근 모서리)", d=6.0)
    g = metal(GOLD, base=0.6, top=0.22, bw=0.2)
    m.hbox(g, (5.2, 4.0, 7.3), (6.6, 12.0, 8.7), name="gold")
    m.hbox(g, (9.4, 4.0, 7.3), (10.8, 12.0, 8.7), name="gold")
    m.hbox(g, (6.4, 2.4, 7.3), (9.6, 3.8, 8.7), name="gold")
    m.hbox(g, (6.4, 12.2, 7.3), (9.6, 13.6, 8.7), name="gold")
    for (px, py) in ((6.1, 3.7), (9.9, 3.7), (6.1, 12.3), (9.9, 12.3)):
        m.hbox(g, (px - 0.75, py - 0.75, 7.3), (px + 0.75, py + 0.75, 8.7), rot=("z", 45, (px, py, 8.0)), name="gold")
    return m


def sigil(style):
    def build():
        # 2026-11-15 윗면 508 픽셀 (가장자리 한 픽셀씩 더해 512 한 장 — 512 면 1024 그림판이 됨)
        m = HModel("sigil_" + style, "땅 마법진 %s (2026-11-15 HD — 빛 바닥 · 겉 고리 · 룬 띠 · 직업 도형 · 문장, 위에서 봄)" % style, d=31.75)
        img = sigil_img(508, style)
        m.mat("top", Mat(img_paint(img, ("up",)), glow=True))
        m.box("top", (0, 7.96, 0), (16, 8.04, 16), faces=["up"])
        return m
    return build


BUILD = {"fx_bar": fx_bar, "fx_cube": fx_cube, "fx_orb": fx_orb, "fx_flame": fx_flame, "phantom_sword": phantom_sword, "forge_sword": forge_sword,
         "treasure_sword": treasure_sword, "treasure_spear": treasure_spear, "treasure_axe": treasure_axe, "gold_gate": gold_gate, "gold_link": gold_link,
         "sigil_crimson": sigil("crimson"), "sigil_gold": sigil("gold"), "sigil_forge": sigil("forge")}

TINTS = {"fx_bar": [(1.0, 0.42, 0.08), (1.0, 0.8, 0.6)], "fx_cube": [(1.0, 0.76, 0.2), (1.0, 0.9, 0.63)], "fx_orb": [(0.92, 0.12, 0.14), (0.98, 0.72, 0.62)],
         "fx_flame": [(1.0, 0.42, 0.08), (1.0, 0.8, 0.6)]}
VIEWS_OF = {"phantom_sword": [(1.0, 0.25, 0.0001), (1.0, 0.45, -0.7)], "forge_sword": [(1.0, 0.25, 0.0001), (1.0, 0.45, -0.7)],
            "treasure_sword": [(1.0, 0.25, 0.0001), (1.0, 0.45, -0.7)], "treasure_spear": [(1.0, 0.25, 0.0001), (1.0, 0.45, -0.7)],
            "treasure_axe": [(1.0, 0.25, 0.0001), (1.0, 0.45, -0.7)], "gold_gate": [(0.0001, 0.15, -1.0), (0.7, 0.35, -1.0)],
            "gold_link": [(0.0001, 0.2, -1.0), (0.8, 0.4, -0.8)], "sigil_crimson": [(0.0, 1.0, 0.0001), (0.3, 1.0, 0.6)],
            "sigil_gold": [(0.0, 1.0, 0.0001), (0.3, 1.0, 0.6)], "sigil_forge": [(0.0, 1.0, 0.0001), (0.3, 1.0, 0.6)],
            "fx_flame": [(1.0, 0.3, -0.4), (0.4, 0.2, 1.0)]}
