#!/usr/bin/env python3
"""2026-11-11 3D 효과 엔진 (Vfx — 모드 없는 클라이언트도 보는 효과)의 2D 그림: 글자 표시 엔티티 (text_display) 에 쓰는 비트맵 글꼴 rpg:vfx.

사용자 지침 "골렘 룬과 같이 3d 몹 + 2d 연출" · "모든 스킬들은 최대한 화려하게": 모드 클라이언트만 보던 2D 효과 (애드온 파티클 rpg:rfx_* — 모드가 그림) 를
모드 없는 클라이언트도 보게, 같은 그림 (tools/java_fx · java_fx_art 의 rfx_* 그림판) 을 글꼴 글자로 옮기고 새 그림 (베기 초승달 · 회전 원반 · 빛줄기 …) 을 더함.

  python3 tools/vfx_sprites.py          # 씀
  python3 tools/vfx_sprites.py --check  # 쓸 것이 없는지

만드는 것
- src/main/resources/assets/rpg/textures/font/vfx/<이름>.png   그림판 (칸 하나 = 글자 하나, 한 판의 칸 크기는 같음 · 칸은 256 이하 — 글꼴 텍스처 한 장이 256 × 256)
- src/main/resources/assets/rpg/font/vfx.json                   글꼴 (판마다 bitmap 제공자 — 높이 H · ascent = H / 2 − 2: 글자 가운데가 글자 표시 엔티티 자리)
- src/main/resources/mmorpg/vfx_sprites.json                    서버가 읽는 목록 (이름 → 글자들 · 칸 수 · 높이 · 가로세로 비 · 색이 있는 그림인지)

글자 그림은 흰색 (밝기) — 글자 색으로 물들임 (불덩이 · 폭발처럼 미리 칠한 것은 colored: 흰 글자로 씀).
칸마다 오른쪽 위 구석에 알파 1 점 (보이지 않음 — 셰이더가 0.1 아래를 버림): 마인크래프트가 비트맵 글자의 너비를 오른쪽 빈 열만큼 줄이지 않게 (가운데가 맞게).
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


def build():
    """→ {파일 경로: 바이트} · 글꼴 · 목록"""
    files = {}
    providers = []
    index = {}
    code = FIRST
    for name, make, colored in SPRITES:
        cells = make()
        cw, ch = cells[0].size
        assert cw <= 256 and ch <= 256, (name, cw, ch)
        assert all(c.size == (cw, ch) for c in cells), name
        cols = min(COLS, len(cells))
        rows = (len(cells) + cols - 1) // cols
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
        providers.append({"type": "bitmap", "file": "rpg:font/vfx/%s.png" % name, "height": H, "ascent": H // 2 - 2, "chars": lines})
        index[name] = {"chars": "".join(chars), "frames": len(chars), "aspect": round(cw / ch, 4), "colored": colored}
    font = {"providers": providers}
    idx = {"_doc": "tools/vfx_sprites.py 가 만듦 (손으로 고치지 않음) — 글꼴 rpg:vfx 의 그림: 이름 → 글자 (칸 차례) · 칸 수 · 가로세로 비 · 미리 칠한 그림인지. "
                   "글자 높이 h 글꼴 단위 (글자 표시 엔티티 1 단위 = 0.025칸)",
           "font": "rpg:vfx", "h": H, "sprites": index}
    files[OUT_FONT] = (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_INDEX] = (json.dumps(idx, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    return files, index


def main():
    check = "--check" in sys.argv
    files, index = build()
    bad = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
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
