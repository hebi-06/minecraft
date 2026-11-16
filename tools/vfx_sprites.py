#!/usr/bin/env python3
"""2026-11-11 3D 효과 엔진 (Vfx — 모드 없는 클라이언트도 보는 효과)의 2D 그림: 글자 표시 엔티티 (text_display) 에 쓰는 비트맵 글꼴 rpg:vfx.

사용자 지침 "골렘 룬과 같이 3d 몹 + 2d 연출" · "모든 스킬들은 최대한 화려하게": 모드 클라이언트만 보던 2D 효과 (애드온 파티클 rpg:rfx_* — 모드가 그림) 를
모드 없는 클라이언트도 보게, 같은 그림 (tools/java_fx · java_fx_art 의 rfx_* 그림판) 을 글꼴 글자로 옮기고 새 그림 (베기 초승달 · 회전 원반 · 빛줄기 …) 을 더함.

  python3 tools/vfx_sprites.py          # 씀
  python3 tools/vfx_sprites.py --check  # 쓸 것이 없는지
  python3 tools/vfx_sprites.py --only el_warrior,el_bear   # 2026-11-16 그 접두의 그림판만 다시 그림 (나머지는 이미 쓴 판을 옮김 — 글자 번호만 새로)

만드는 것
- src/main/resources/assets/rpg/textures/font/vfx/<이름>.png   그림판 (칸 하나 = 글자 하나, 한 판의 칸 크기는 같음 · 칸은 256 이하 — 글꼴 텍스처 한 장이 256 × 256)
- src/main/resources/assets/rpg/font/vfx.json                   글꼴 (판마다 bitmap 제공자 — 높이 H · ascent = H / 2 − 2: 글자 가운데가 글자 표시 엔티티 자리)
- src/main/resources/mmorpg/vfx_sprites.json                    서버가 읽는 목록 (이름 → 글자들 · 칸 수 · 높이 · 가로세로 비 · 색이 있는 그림인지)

글자 그림은 흰색 (밝기) — 글자 색으로 물들임 (불덩이 · 폭발처럼 미리 칠한 것은 colored: 흰 글자로 씀).
칸마다 오른쪽 위 구석에 알파 1 점 (보이지 않음 — 셰이더가 0.1 아래를 버림): 마인크래프트가 비트맵 글자의 너비를 오른쪽 빈 열만큼 줄이지 않게 (가운데가 맞게).
2026-11-16 빈 가장자리 자르기 (crop_cells — 사용자 "8gb 지원할거고 … 용량 확보하자"): 판의 모든 칸에서 알파 26 아래인 가장자리 (화면에 나오지 않는 자리) 를 잘라
칸을 줄이고 글꼴 높이 · ascent 를 그만큼 고침 (픽셀당 글꼴 단위는 같게). 서버 (Vfx.Sprite) 가 목록의 crop (예전 칸 가운데 → 자른 칸 가운데, 칸 높이 몫) 만큼 옮김
→ 화면의 그림은 예전과 같은 픽셀 · 같은 자리 · 같은 크기. 목록의 aspect 는 예전 칸 그대로.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/particle/addon/rpg/fx")
OUT_TEX = os.path.join(ROOT, "src/main/resources/assets/rpg/textures/font/vfx")
OUT_FONT = os.path.join(ROOT, "src/main/resources/assets/rpg/font/vfx.json")
OUT_INDEX = os.path.join(ROOT, "src/main/resources/mmorpg/vfx_sprites.json")

H = 64  # 글자 높이 (글꼴 단위 — 글자 표시 엔티티에서 1 = 0.025칸 → 1.6칸, 크기는 변환으로)
FIRST = 0xE000  # 글자 (사용자 영역)
COLS = 8  # 판 한 줄의 칸 수 (넘으면 다음 줄)


# ------------------------------------------------------------ 그림판 자르기 (rfx_* 그림)

def slice_sheet(file, cell, cols, rows, take=None, size=None):
    """file 을 cols × rows 칸 (cell = [w, h]) 으로 잘라 칸 목록 (take = 쓸 칸 번호, size = 줄인 칸 크기 [w, h])"""
    im = Image.open(os.path.join(SRC, file)).convert("RGBA")
    cw, ch = cell
    out = []
    for i in range(cols * rows):
        if take is not None and i not in take:
            continue
        x, y = (i % cols) * cw, (i // cols) * ch
        c = im.crop((x, y, x + cw, y + ch))
        if size and tuple(size) != (cw, ch):
            c = c.resize(tuple(size), Image.LANCZOS)
        out.append(c)
    return out


# ------------------------------------------------------------ 새 그림 (numpy — 밝기 + 알파, 흰색)

def to_img(alpha):
    """알파 (0 ~ 1) → 흰 RGBA (빛의 세기는 알파로 — 글자 색으로 물듦)"""
    a = np.clip(alpha, 0, 1)
    rgba = np.zeros(a.shape + (4,), dtype=np.uint8)
    rgba[..., 0:3] = 255
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def grid(n, w=None):
    w = w or n
    y, x = np.mgrid[0:n, 0:w].astype(float)
    return (x + 0.5) / w * 2 - 1, (y + 0.5) / n * 2 - 1  # −1 ~ 1


def slash_frames(n=8, size=256):
    """베기 초승달 (휘두른 칼빛): 두 원으로 짠 초승달 (아래로 볼록 — 가운데 굵기 0.34, 두 뿔로 가늘어짐) 이 왼쪽 뿔에서 오른쪽 뿔로 드러남.
    0 ~ 4 드러남 (드러나는 끝 = 칼끝이 가장 밝음), 5 다 드러남, 6 · 7 안쪽부터 옅어지며 바깥 날만 남음. 바깥 날 (외곽선) 은 밝은 심지 + 빛 무리.
    서버가 변환으로 돌려 휘두르는 쪽에 맞춤 (그림 +x = 휘두르는 쪽)"""
    x, y = grid(size)
    # 초승달 (아래 y −0.86 ~ 뿔 −0.24) 을 칸 가운데로 옮기고 1.12 배 (칸을 넓게 씀)
    x = x / 1.12
    yy = -y / 1.12 - 0.55  # 위가 +
    R = 0.86
    r_out = np.hypot(x, yy)
    r_in = np.hypot(x, yy - 0.34)
    body = (r_out <= R) & (r_in >= R)
    ang = (np.degrees(np.arctan2(yy, x)) + 360) % 360  # 0 = 오른쪽, 반시계
    # 왼쪽 뿔 (약 200°) → 아래 (270°) → 오른쪽 뿔 (약 340°): 시계 반대 방향으로 드러남
    rel = (ang - 196) % 360
    full = 148.0
    edge_d = np.abs(r_out - R)
    thick = np.clip(R - r_out, 0, None)  # 바깥 날에서 안쪽까지의 거리
    frames = []
    for f in range(n):
        if f <= 5:
            span = full * min(1.0, 0.28 + f * 0.155)
            fade_in = 1.0
            inner = 1.0
        else:
            span = full
            fade_in = 0.7 if f == 6 else 0.4
            inner = 0.45 if f == 6 else 0.12
        shown = rel <= span
        t = np.clip(rel / full, 0, 1)
        headness = np.clip(1 - (span - rel) / 40, 0, 1) if f <= 4 else np.zeros_like(rel)  # 드러나는 끝 40° 가 밝음
        fill = np.where(body, np.clip(1 - thick / 0.34, 0, 1) ** 1.8, 0) * 0.55 * inner
        core = np.exp(-(edge_d / 0.022) ** 2) * (r_out <= R + 0.05)
        glow = np.exp(-(edge_d / 0.08) ** 2) * 0.45
        taper = np.sin(np.clip(t, 0, 1) * math.pi) ** 0.5  # 두 뿔 끝은 가늘게
        a = (fill + core * (0.75 + 0.25 * headness) + glow * (0.6 + 0.4 * headness)) * taper * shown * fade_in
        a *= np.clip((span - rel) / 4 + 0.15, 0, 1)
        frames.append(to_img(a))
    return frames


def spin_disc(size=256):
    """돌아가는 원반의 흐림 (도는 도끼 · 칼날): 바깥 고리 + 안쪽으로 옅어지는 바람 무늬 (세 갈래) — 서버가 돌림 (변환)"""
    x, y = grid(size)
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    ring = np.exp(-((r - 0.82) / 0.07) ** 2)
    swirl = (0.5 + 0.5 * np.cos(3 * ang + r * 7)) ** 3 * np.clip(1 - np.abs(r - 0.62) / 0.3, 0, 1)
    a = ring * 0.9 + swirl * 0.45
    a *= np.clip((0.97 - r) / 0.05, 0, 1)
    return [to_img(a)]


def streak(w=256, h=32):
    """빛줄기 (지나간 자리): 가로로 긴 빛 — 가운데 밝은 심지, 두 끝으로 가늘어짐"""
    x, y = grid(h, w)
    taper = np.clip(1 - np.abs(x) ** 2, 0, 1)
    core = np.exp(-(y / 0.18) ** 2) * taper
    glow = np.exp(-(y / 0.55) ** 2) * taper * 0.45
    return [to_img(core + glow)]


def glow_ball(size=128):
    """둥근 빛 (빛 무리)"""
    x, y = grid(size)
    r = np.hypot(x, y)
    a = np.exp(-(r / 0.42) ** 2) * 0.9 + np.exp(-(r / 0.14) ** 2) * 0.6
    a *= np.clip((1 - r) / 0.08, 0, 1)
    return [to_img(a)]


def dust_frames(n=6, size=128, seed=7):
    """흙먼지 (회색으로 물들여 씀): 덩어리가 부풀며 옅어짐"""
    rng = np.random.default_rng(seed)
    x, y = grid(size)
    blobs = [(rng.uniform(-0.35, 0.35), rng.uniform(-0.25, 0.3), rng.uniform(0.18, 0.32)) for _ in range(9)]
    frames = []
    for f in range(n):
        k = 0.55 + f * 0.11
        a = np.zeros_like(x)
        for bx, by, br in blobs:
            a = np.maximum(a, np.exp(-(((x - bx * k) ** 2 + (y - by * k - 0.05 * f) ** 2) / (br * k) ** 2) * 1.6))
        noise = rng.uniform(0.75, 1.0, x.shape)
        a = a * noise * (1 - f / (n + 0.5)) * 0.85
        a *= np.clip((1 - np.hypot(x, y)) / 0.1, 0, 1)
        frames.append(to_img(a))
    return frames


def xcut_frames(n=4, size=256):
    """X 베기 자국: 첫 획 (왼쪽 위 → 오른쪽 아래) → 둘째 획 → 둘 다 → 가늘어짐"""
    x, y = grid(size)
    frames = []

    def stroke(dx, dy, grow, thick):
        # 선분 (−dx, −dy) → (dx, dy) 의 grow 만큼, 가운데가 굵음
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        s = x * ux + y * uy  # 축 위 자리
        d = np.abs(-x * uy + y * ux)
        lim = L * grow
        along = (s + L) / (2 * L)
        inside = (s >= -L) & (s <= -L + 2 * lim)
        w = thick * np.sin(np.clip(along, 0, 1) * math.pi) ** 0.6
        core = np.clip(1 - d / np.maximum(w, 1e-3), 0, 1) ** 1.2
        glow = np.exp(-(d / np.maximum(w * 2.6, 1e-3)) ** 2) * 0.5 * (w > 0.004)
        return (core + glow) * inside

    for f in range(n):
        if f == 0:
            a = stroke(0.72, 0.72, 1.0, 0.07)
        elif f == 1:
            a = np.maximum(stroke(0.72, 0.72, 1.0, 0.07), stroke(-0.72, 0.72, 1.0, 0.07))
        elif f == 2:
            a = np.maximum(stroke(0.72, 0.72, 1.0, 0.055), stroke(-0.72, 0.72, 1.0, 0.055)) * 0.8
        else:
            a = np.maximum(stroke(0.72, 0.72, 1.0, 0.03), stroke(-0.72, 0.72, 1.0, 0.03)) * 0.5
        frames.append(to_img(a))
    return frames


def shock_ring(n=6, size=256):
    """땅의 충격파 고리 (위에서 봄 — 땅에 눕혀 씀): 커지며 가늘어짐 (칸 반지름 0.3 → 0.95)"""
    x, y = grid(size)
    r = np.hypot(x, y)
    frames = []
    for f in range(n):
        t = f / (n - 1)
        R = 0.3 + 0.65 * t
        w = 0.1 * (1 - t) + 0.025
        a = np.exp(-((r - R) / w) ** 2) * (1 - t * 0.7) + np.exp(-((r - R) / (w * 3)) ** 2) * 0.3 * (1 - t)
        a *= np.clip((0.99 - r) / 0.03, 0, 1)
        frames.append(to_img(a))
    return frames


# ------------------------------------------------------------ 목록

SPRITES = [
    # (이름, 칸 목록 만들기, 색이 있는 그림)
    ("burst", lambda: slice_sheet("rfx_jburst.png", (192, 192), 4, 4), False),
    ("burst2", lambda: slice_sheet("rfx_jburst2.png", (192, 192), 4, 4), False),
    ("bladering", lambda: slice_sheet("rfx_jbladering.png", (256, 256), 4, 4), False),
    ("blood", lambda: slice_sheet("rfx_jblood.png", (128, 128), 6, 2), False),
    ("cinder", lambda: slice_sheet("rfx_jcinder.png", (192, 384), 4, 4, size=(128, 256)), False),
    ("crack", lambda: slice_sheet("rfx_crack.png", (256, 256), 2, 2), False),
    ("fissure", lambda: slice_sheet("rfx_fissure.png", (512, 128), 1, 3, size=(256, 64)), False),
    ("aura", lambda: slice_sheet("rfx_aura.png", (128, 256), 4, 2), False),
    ("flare", lambda: slice_sheet("rfx_flare.png", (256, 256), 2, 1), False),
    ("wave", lambda: slice_sheet("rfx_wave.png", (256, 128), 1, 2), False),
    ("fireball", lambda: slice_sheet("rfx_fireball.png", (128, 128), 4, 1), True),
    ("boom", lambda: slice_sheet("rfx_boom.png", (128, 128), 4, 2), True),
    ("slash", lambda: slash_frames(), False),
    ("spin", lambda: spin_disc(), False),
    ("streak", lambda: streak(), False),
    ("glow", lambda: glow_ball(), False),
    ("dust", lambda: dust_frames(), False),
    ("xcut", lambda: xcut_frames(), False),
    ("shock", lambda: shock_ring(), False),
]

# 2026-11-15 직업 전용 2D 이펙트 그림 (사용자 "직업마다 전용으로 그릴래" — tools/jobvfx/<직업>.py, 미리 칠한 colored): 팬텀 아처 pa_* (시범 팬텀 버스트의
# pb_* 를 옮김 · 다른 스킬 · 특성 · 평타 그림을 더함)
from jobvfx import phantom as _jobvfx_phantom  # noqa: E402
# 2026-11-15 궁수 2차 둘: 아스널 킹 ak_* (금빛 문 · 빛 폭발) · 블레이드 레인저 br_* (달아오른 베기 · 쇳물 폭발)
from jobvfx import arsenal as _jobvfx_arsenal  # noqa: E402
from jobvfx import forge as _jobvfx_forge  # noqa: E402
# 2026-11-15 원소 공용 묶음 (사용자 "원소 공용 + 직업 대표") — 강철 el_steel (검사) · 피 el_blood (블러드 나이트) · 금 ak_slash … (소드마스터 — 아스널 킹 묶음에 더함)
from jobvfx import element as _jobvfx_element  # noqa: E402

SPRITES += _jobvfx_phantom.sprites()
SPRITES += _jobvfx_arsenal.sprites()
SPRITES += _jobvfx_forge.sprites()
SPRITES += _jobvfx_element.sprites()


# 2026-11-16 빈 가장자리 자르기 (사용자 "8gb 지원할거고 옛그림이 있으면 그냥 참고만 하게 따로 치우고 용량 확보하자"): 글꼴 그림판은 접속할 때 통째로 풀려 메모리에
# 올라감 (픽셀당 4 바이트 — 그림이 없는 가장자리도). 글자 셰이더는 알파 0.1 아래 (26 아래) 를 버림 → 판의 모든 칸에서 26 아래인 가장자리는 화면에 나오지 않는 자리
VISIBLE = 26
CROP_PAD = 2  # 보이는 상자 바깥 여유 (픽셀)
CROP_MIN_UNITS = 8  # 자른 칸 높이 하한 (글꼴 단위)


def mc_advance(width_px, height_units, cell_h):
    """마인크래프트 비트맵 글자의 너비 (BitmapProvider: (int) (0.5F + 실제 너비 × (높이 / 칸 높이)) + 1 — float 셈) — 글자 표시 엔티티는 줄 너비의 반만큼 왼쪽에서 시작"""
    scale = np.float32(height_units) / np.float32(cell_h)
    return int(np.float32(0.5) + np.float32(width_px) * scale) + 1


def crop_cells(cells):
    """칸들을 모든 칸의 보이는 상자 (+ CROP_PAD) 로 자름 → (칸들, 글꼴 높이, ascent, (dx, dy), 예전 칸 크기)

    높이는 글꼴 단위가 정수인 픽셀 배수 (칸 높이 / gcd(칸 높이, H)) 로 늘림 — 한 픽셀의 글꼴 단위 (H / 칸 높이) 가 예전과 같음 = 그림 크기가 같음.
    (dx, dy) = 자른 칸을 옮길 자리 (예전 칸 높이 몫 — 오른쪽 x · 위 y): 글자 표시 엔티티는 글자를 줄 너비 (advance) 의 반만큼 왼쪽 · ascent 만큼 위에서 그림
    (글자 위 = 줄 위 + 10 − ascent) → 가로 (advance 차이) / 2 + 자른 왼쪽 · 세로 ascent 차이 + 자른 위 만큼 옮기면 예전 칸의 픽셀 자리와 같음.
    """
    cw, ch = cells[0].size
    A0 = H // 2 - 2
    a = np.stack([np.asarray(c.convert("RGBA"))[..., 3] for c in cells])
    ys, xs = np.nonzero((a >= VISIBLE).any(axis=0))
    if len(xs) == 0:
        return cells, H, A0, (0.0, 0.0), (cw, ch)
    x0, x1 = max(0, int(xs.min()) - CROP_PAD), min(cw, int(xs.max()) + 1 + CROP_PAD)
    y0, y1 = max(0, int(ys.min()) - CROP_PAD), min(ch, int(ys.max()) + 1 + CROP_PAD)
    p = ch // math.gcd(ch, H)
    min_px = math.ceil(CROP_MIN_UNITS * ch / H / p) * p
    need = max(math.ceil((y1 - y0) / p) * p, min_px)
    if need >= ch:
        y0, y1 = 0, ch
    else:
        extra = need - (y1 - y0)
        y0 -= extra // 2
        y1 += extra - extra // 2
        if y0 < 0:
            y1 -= y0
            y0 = 0
        if y1 > ch:
            y0 -= y1 - ch
            y1 = ch
    if (x0, y0, x1, y1) == (0, 0, cw, ch):
        return cells, H, A0, (0.0, 0.0), (cw, ch)
    cw2, ch2 = x1 - x0, y1 - y0
    assert (ch2 * H) % ch == 0 and 0 <= y0 and y1 <= ch, (cw, ch, y0, y1)
    h2 = ch2 * H // ch
    a2 = h2 // 2 - 2
    u = H / ch
    dxu = (mc_advance(cw2, h2, ch2) - mc_advance(cw, H, ch)) / 2 + x0 * u
    dyu = a2 - A0 + y0 * u
    out = [c.crop((x0, y0, x1, y1)) for c in cells]
    return out, h2, a2, (round(dxu / H, 6), round(-dyu / H, 6)), (cw, ch)


# 2026-11-16 도트 그림 시범 (사용자 "2번으로 해보자" — 피드백 "스킬을 고퀄 이미지로 하니깐 몹이랑 데미지 피해 표시는 도트여서 괴리감이 든다"): 이 묶음
# (접두 → tools/jobvfx/element.py STYLES 이름 · "auto" = 그림 자체의 색으로 램프) 의 그림판은 같은 그림을 세계 밀도의 도트 그림으로 (tools/jobvfx/pixel.py —
# 램프 색 · 꽉 찬 텍셀 · 네모 픽셀). 비우면 칠한 그림 그대로.
# 2026-11-16 위르드식 확정 (사용자 "이제 위르드식으로 만들어보자" — 도트 2D + 바닐라 파티클 + 직접 만든 3D): 칠한 묶음 모두 도트로 (비전은 원소 램프 그대로 —
# 시범에서 본 모습, 나머지는 그림 자체의 색 — 그림자 꽃잎 · 팬텀 아처 불꽃처럼 원소 램프 밖의 색이 있음)
PIXEL_LIBS = {"el_arcane": "arcane", "el_steel": "auto", "el_blood": "auto", "el_shadow": "auto", "el_soul": "auto", "pa": "auto", "ak": "auto", "br": "auto",
              # 2026-11-16 남은 직업 묶음은 원소 램프 그대로 (auto 는 거울의 청록 유리가 자홍으로 묻혔음 — CI 43872303193 트릭스터 거울):
              # 트릭스터 환영 · 피에로 서커스 · 전사 분노 · 워로드 곰 · 프로스트 레이더 서리
              "el_illusion": "illusion", "el_circus": "circus", "el_rage": "rage", "el_bear": "bear", "el_frost": "frost",
              "el_rune": "rune",  # 2026-11-16 마검사 · 아케인 블레이드 룬
              "el_element": "element",  # 2026-11-16 엘리멘탈 블레이드
              "el_holy": "holy", "el_petal": "petal",  # 2026-11-16 사제 · 세인트 (성광 · 꽃잎)
              "el_hex": "hex",  # 2026-11-16 워록 (혈주)
              "el_ward": "ward",  # 2026-11-16 수호기사 (수호)
              "el_maw": "maw",  # 2026-11-16 다크 나이트 (아가리)
              "el_thunder": "thunder", "el_tyrant": "tyrant",  # 2026-11-16 성기사 · 썬더로드 (뇌광) · 타이런트 (폭군)
              "el_reap": "reap", "el_soulreap": "soulreap", "el_exec": "exec",  # 2026-11-16 사신 라인
              "el_lance": "lance", "el_gale": "gale", "el_dragon": "dragon",  # 2026-11-16 창술사 라인
              "el_phoenix": "phoenix"}  # 2026-11-16 피닉스 가드


def _old_outputs():
    """2026-11-16 이미 쓴 글꼴 · 목록 (--only 로 다시 쓰지 않는 그림판을 그대로 옮길 때): {판 이름: (제공자, 목록 항목)}"""
    try:
        font = json.load(open(OUT_FONT, encoding="utf-8"))
        idx = json.load(open(OUT_INDEX, encoding="utf-8"))["sprites"]
    except (OSError, ValueError, KeyError):
        return {}
    out = {}
    for pv in font.get("providers", []):
        name = os.path.splitext(os.path.basename(pv.get("file", "")))[0]
        if name in idx and os.path.exists(os.path.join(OUT_TEX, name + ".png")):
            out[name] = (pv, idx[name])
    return out


def _wanted(name, only):
    """--only 접두 (그림판 이름이 '접두' 또는 '접두_…') 에 드는지 — only 가 없으면 모두"""
    return only is None or any(name == p or name.startswith(p + "_") for p in only)


def build(only=None):
    """→ {파일 경로: 바이트} · 글꼴 · 목록. only = 다시 그릴 그림판 접두 목록 (2026-11-16 — 나머지는 이미 쓴 판을 그대로 옮김: 글자 번호만 새로 매김.
    직업 묶음을 하나 더할 때 모든 묶음을 다시 그리면 15분 넘게 걸림)"""
    from jobvfx import pixel as _pixel
    files = {}
    providers = []
    index = {}
    code = FIRST
    saved = [0, 0]  # 예전 · 자른 픽셀 수
    makes = {n: mk for n, mk, _ in SPRITES}
    pix_cache = {}
    old = _old_outputs() if only is not None else {}
    reused = 0
    for name, make, colored in SPRITES:
        if not _wanted(name, only) and name in old:
            pv, ent = old[name]
            lines = []
            chars = []
            for line in pv["chars"]:
                nl = ""
                for ch_ in line:
                    if ch_ == "\u0000":
                        nl += "\u0000"
                    else:
                        c = chr(code)
                        code += 1
                        chars.append(c)
                        nl += c
                lines.append(nl)
            assert len(chars) == ent["frames"], name
            path = os.path.join(OUT_TEX, name + ".png")
            files[path] = open(path, "rb").read()
            providers.append({"type": "bitmap", "file": "rpg:font/vfx/%s.png" % name, "height": pv["height"], "ascent": pv["ascent"], "chars": lines})
            e = {"chars": "".join(chars), "frames": ent["frames"], "aspect": ent["aspect"], "colored": colored}
            if "crop" in ent:
                e["crop"] = ent["crop"]
            index[name] = e
            reused += 1
            continue
        cells = _pixel.sheet_cells(name, makes, pix_cache, PIXEL_LIBS) if PIXEL_LIBS else None
        if cells is None:
            cells = make()
        cw, ch = cells[0].size
        assert cw <= 256 and ch <= 256, (name, cw, ch)
        assert all(c.size == (cw, ch) for c in cells), name
        cells, gh, ga, crop, (cw0, ch0) = crop_cells(cells)
        cw, ch = cells[0].size
        saved[0] += cw0 * ch0 * len(cells)
        saved[1] += cw * ch * len(cells)
        # 2026-11-15 빈 칸 줄임: 글꼴 그림판은 접속할 때 통째로 풀려 메모리에 올라감 (빈 칸도 자리를 차지) — 줄 수를 먼저 정하고 한 줄 칸 수를 고르게 (10칸 = 5 × 2 · 예전 8 × 2)
        rows = (len(cells) + COLS - 1) // COLS
        cols = (len(cells) + rows - 1) // rows
        sheet = Image.new("RGBA", (cols * cw, rows * ch), (0, 0, 0, 0))
        chars = []
        lines = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(cells):
                    cell = cells[i].copy()
                    px = cell.load()
                    if px[cw - 1, 0][3] == 0:
                        px[cw - 1, 0] = (255, 255, 255, 1)  # 너비가 줄지 않게 (보이지 않음)
                    sheet.paste(cell, (c * cw, r * ch))
                    ch_ = chr(code)
                    code += 1
                    chars.append(ch_)
                    line += ch_
                else:
                    line += "\u0000"
            lines.append(line)
        from io import BytesIO
        buf = BytesIO()
        sheet.save(buf, "PNG", optimize=True)
        files[os.path.join(OUT_TEX, name + ".png")] = buf.getvalue()
        providers.append({"type": "bitmap", "file": "rpg:font/vfx/%s.png" % name, "height": gh, "ascent": ga, "chars": lines})
        index[name] = {"chars": "".join(chars), "frames": len(chars), "aspect": round(cw0 / ch0, 4), "colored": colored}
        if crop != (0.0, 0.0):
            index[name]["crop"] = list(crop)
    font = {"providers": providers}
    if only is not None:
        print("reused %d sheets (--only %s)" % (reused, ",".join(only)))
    print("crop: %.1f MB → %.1f MB (decoded RGBA)" % (saved[0] * 4 / 1e6, saved[1] * 4 / 1e6))
    if _pixel.MISSING:
        print("pixel: 크기 없음 (DEFAULT_SIZE %.1f 칸) — %s" % (_pixel.DEFAULT_SIZE, " ".join(sorted(_pixel.MISSING))))
    idx = {"_doc": "tools/vfx_sprites.py 가 만듦 (손으로 고치지 않음) — 글꼴 rpg:vfx 의 그림: 이름 → 글자 (칸 차례) · 칸 수 · 가로세로 비 (예전 칸) · 미리 칠한 그림인지 · "
                   "crop (빈 가장자리를 자른 칸을 옮길 자리 — 예전 칸 높이 몫, 오른쪽 x · 위 y). 글자 높이 h 글꼴 단위 (글자 표시 엔티티 1 단위 = 0.025칸)",
           "font": "rpg:vfx", "h": H, "sprites": index}
    files[OUT_FONT] = (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_INDEX] = (json.dumps(idx, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    return files, index


def main():
    check = "--check" in sys.argv
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    files, index = build(only)
    bad = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    # 2026-11-16 지난 판 (목록에서 빠진 그림 — 리소스 팩 zip 에 그대로 들어갔음) 지우기
    if os.path.isdir(OUT_TEX):
        for f in sorted(os.listdir(OUT_TEX)):
            p = os.path.join(OUT_TEX, f)
            if p not in files:
                if check:
                    bad.append(os.path.relpath(p, ROOT))
                else:
                    os.remove(p)
    if check:
        if bad:
            print("vfx sprites differ: " + ", ".join(bad) + " — python3 tools/vfx_sprites.py")
            return 1
        print("vfx sprites ok (%d)" % len(index))
        return 0
    total = sum(len(v) for k, v in files.items() if k.endswith(".png"))
    print("wrote %d sprites, %d glyphs, png %.1f KB" % (len(index), sum(v["frames"] for v in index.values()), total / 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
