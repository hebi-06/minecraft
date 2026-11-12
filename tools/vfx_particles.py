#!/usr/bin/env python3
"""2026-11-12 모드 없는 클라이언트의 파티클 다리 (vfx/PBridge — 사용자 "룬 스킬 다 만들어보자", 서버에 접속해 리소스만 받으면 됨):
모드 클라이언트가 그리는 애드온 파티클 (rpg:* — assets/mmorpg/addon_particles.json) 과 2D 효과 판 (Fx3d 의 slash_* 카드) 을 모드 없는 클라이언트에서는
서버가 글자 표시 엔티티로 띄움. 이 도구는 그 그림을 글꼴 rpg:vfxp 의 글자로 옮김.

  python3 tools/vfx_particles.py          # 씀
  python3 tools/vfx_particles.py --check  # 쓸 것이 없는지

만드는 것
- src/main/resources/assets/rpg/textures/font/vfxp/<판>.png   파티클 그림의 칸 (uv 칸 하나 = 글자 하나 — 긴 변 CAP 픽셀까지 줄임, 알파를 곱해 줄여 가장자리가 검지 않게)
- src/main/resources/assets/rpg/font/vfxp.json                 글꼴 (판마다 bitmap — 높이 H · ascent H/2 − 2: 글자 가운데가 엔티티 자리, 뒤로 옮김 space)
- src/main/resources/mmorpg/vfx_particles.json                 서버 목록: 파티클 ID → 판, 판 → 첫 글자 · 칸 수 · uv 칸 크기 · 글자 너비 (advance), 카드 → 칸마다 겹

글자는 그림 그대로 (흰 그림 · 칠한 그림) — 서버가 파티클의 색 (식) 을 글자 색으로 곱함 (파티클과 같음).
칸마다 오른쪽 위 구석에 알파 1 점: 마인크래프트가 비트맵 글자의 너비를 빈 열만큼 줄이지 않게 (너비 = 칸 전체 → 가운데가 맞음 · 서버가 너비를 앎).
직업 스킬 그림 (rfx_j*) 은 직업 단계에서 (JOB_PARTICLES) — 룬이 쓰는 것만 RUNE_J 에.

2026-11-12 픽셀 글꼴 rpg:vfxq (사용자 "오케 이런 느낌으로 룬 다 바꿔보자" — 시범 룬 일곱의 픽셀 그림 rpg:px 와 같은 느낌): 같은 그림을 반 크기 픽셀로 줄여
띠 색 (어두운 외곽선 · 반투명 무리 둘 · 짙은 색 · 계열 색) + 흰 심지로 다시 그림. 룬을 쓰는 동안의 파티클 (서버 PBridge — Tasks 꼬리표 · 주인) 만 이 글꼴.
- A 글자 (rpg:vfxp 와 같은 번호): 띠 색 (회색 × 파티클 색 — 미리 칠한 그림은 색조 그대로 밝기만 띠)
- B 글자 (번호 + QB): 가장 밝은 봉우리 (흰빛 섞은 색으로 A 위에 겹침 — A 와 픽셀이 겹치지 않음)
- C 글자 (번호 + QC): 겹쳐 그리는 심지 층 (rfx2 의 두 번째 장 · 날아가는 빛의 속줄 — 외곽선 · 무리 없이 밝은 곳만)
"""
import json
import os
import sys
from io import BytesIO

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DEFS = os.path.join(RES, "assets/mmorpg/addon_particles.json")
PARTICLE_TEX = os.path.join(RES, "assets/mmorpg/textures/particle")
MODEL_DIR = os.path.join(RES, "assets/mmorpg/fxmodel")
OUT_TEX = os.path.join(RES, "assets/rpg/textures/font/vfxp")
OUT_FONT = os.path.join(RES, "assets/rpg/font/vfxp.json")
OUT_INDEX = os.path.join(RES, "mmorpg/vfx_particles.json")
OUT_QTEX = os.path.join(RES, "assets/rpg/textures/font/vfxq")
OUT_QFONT = os.path.join(RES, "assets/rpg/font/vfxq.json")

# 픽셀 글꼴 (rpg:vfxq)
QDIV = 2.0  # 칸을 이만큼 줄임 (긴 변 128 → 64 픽셀 — 화면에서 블록 질감과 비슷한 픽셀)
QB = 0x400  # B 글자 (흰 심지) 번호 = A + QB
QC = 0x800  # C 글자 (겹쳐 그리는 심지 층) 번호 = A + QC
Q_T = (0.07, 0.24, 0.45, 0.72)  # 밝기 (밝기 × 알파) 띠: 무리 · 단단한 곳 · 계열 색 · 심지 봉우리
Q_OUT, Q_DARK, Q_MID, Q_BASE = 0.2, 0.42, 0.7, 1.0  # A 회색 단계 (× 파티클 색) — rpg:px 와 같음
Q_HALO = 0.85  # 무리 (반투명) 의 회색

H = 64  # 글자 높이 (글꼴 단위 — 글자 표시 엔티티 1 단위 = 0.025칸 → 1.6칸, 크기는 변환으로)
CAP = 128  # 칸의 긴 변 (픽셀) — 글꼴 그림은 모두 메모리에 올라감 (룬 묶음 약 700만 픽셀)
FIRST = 0xE000  # 글자 (사용자 영역 E000 ~ F8FF)
SPACE_FIRST = 0xF800  # 뒤로 옮김 글자 (−1 · −2 · −4 … −1024)
COLS = 8  # 판 한 줄의 칸 수

# 룬이 쓰는 직업 그림 (rfx_j*) — 나머지 직업 그림은 직업 단계에서
# (2026-11-12 census — VanillaProbe vcensus: 룬 120 이 보낸 파티클 가운데 직업 그림 — 원소 표식 · 터짐 · 타격 · 웅덩이 · 연기 · 깨짐 표식)
RUNE_J = {"rpg:rfx_jbreak", "rpg:rfx_jelem", "rpg:rfx_jburst2", "rpg:rfx_jhits", "rpg:rfx_jpool", "rpg:rfx_jsmoke2"}
# 칸이 많은 작은 그림은 더 줄임 (타격 그림 132칸 — 화면에서 한 칸 남짓)
CAP_OF = {"rpg:rfx_jhits": 64}
# Fx3d 카드 (룬 휘두름 판 — RuneKit.arc · waveCard · groundCrescent)
CARDS = ["slash_wave", "slash_arc", "slash_arc_soft", "slash_heavy", "slash_heavy_soft", "slash_thin", "slash_thin_soft"]


def wanted(pid):
    return "rfx_j" not in pid or pid in RUNE_J


def shrink(cell, cap):
    """긴 변을 cap 까지 (알파를 곱해 줄임 — 투명한 곳의 색이 번지지 않게)"""
    w, h = cell.size
    k = min(1.0, cap / max(w, h))
    if k >= 1.0:
        return cell
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    a = np.asarray(cell, dtype=np.float32) / 255.0
    pm = a.copy()
    pm[..., :3] *= pm[..., 3:4]
    img = Image.fromarray((pm * 255 + 0.5).astype(np.uint8), "RGBA").resize((nw, nh), Image.LANCZOS)
    b = np.asarray(img, dtype=np.float32) / 255.0
    al = b[..., 3:4]
    rgb = np.where(al > 1e-4, b[..., :3] / np.maximum(al, 1e-4), 0)
    out = np.concatenate([np.clip(rgb, 0, 1), al], axis=-1)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 픽셀 글꼴 (rpg:vfxq)

def q_size(cw, ch):
    return max(4, round(cw / QDIV)), max(4, round(ch / QDIV))


def _q_resize_v(v, nw, nh):
    """밝기 판 → (nw, nh): 평균과 최댓값을 반씩 (가는 선 · 작은 점이 사라지지 않게)"""
    im = Image.fromarray((np.clip(v, 0, 1) * 255 + 0.5).astype(np.uint8), "L")
    mean = np.asarray(im.resize((nw, nh), Image.BOX), np.float32) / 255
    up = np.asarray(im.resize((nw * 4, nh * 4), Image.BILINEAR), np.float32) / 255
    mx = up.reshape(nh, 4, nw, 4).max(axis=(1, 3))
    return 0.5 * mean + 0.5 * mx


def _q_resize_rgba(a, nw, nh):
    """칠한 그림 → (nw, nh) 색 · 알파 (알파를 곱해 줄임)"""
    pm = a[..., :3] * a[..., 3:4]
    ch = [np.asarray(Image.fromarray((np.clip(pm[..., i], 0, 1) * 255 + 0.5).astype(np.uint8), "L").resize((nw, nh), Image.BOX), np.float32) / 255
          for i in range(3)]
    al = np.asarray(Image.fromarray((np.clip(a[..., 3], 0, 1) * 255 + 0.5).astype(np.uint8), "L").resize((nw, nh), Image.BOX), np.float32) / 255
    rgb = np.dstack(ch)
    rgb = np.where(al[..., None] > 1e-3, rgb / np.maximum(al[..., None], 1e-3), 0)
    return np.clip(rgb, 0, 1), al


def _q_dil(m):
    o = m.copy()
    o[1:, :] |= m[:-1, :]
    o[:-1, :] |= m[1:, :]
    o[:, 1:] |= m[:, :-1]
    o[:, :-1] |= m[:, 1:]
    return o


def q_colored(cells):
    """미리 칠한 그림인지 (불 · 바위 · 블랙홀 …) — 채도가 높은 픽셀이 많으면"""
    sat = []
    for c in cells:
        a = np.asarray(c, np.float32) / 255
        m = a[..., 3] > 0.3
        if m.sum() < 4:
            continue
        rgb = a[m][:, :3]
        sat.append(float(np.mean(rgb.max(1) - rgb.min(1))))
    return bool(sat) and float(np.mean(sat)) > 0.12


def q_cell(cell, colored, nw, nh, alpha=1.0):
    """칸 하나 → (A, B, C) RGBA uint8 판 (nh × nw) · B 에 픽셀이 있는지. alpha = A 층 불투명도 몫 (빛줄기)"""
    cell = cell.convert("RGBA")
    if nw > cell.size[0] * 1.05 or nh > cell.size[1] * 1.05:
        # 원본보다 크게: 부드럽게 늘린 뒤 띠로 (모서리가 계단이 아니라 고운 픽셀로)
        pm = np.asarray(cell, np.float32) / 255
        pm[..., :3] *= pm[..., 3:4]
        up = Image.fromarray((pm * 255 + 0.5).astype(np.uint8), "RGBA").resize((nw * 2, nh * 2), Image.BICUBIC)
        b = np.asarray(up, np.float32) / 255
        al = b[..., 3:4]
        b[..., :3] = np.where(al > 1e-4, b[..., :3] / np.maximum(al, 1e-4), 0)
        cell = Image.fromarray((np.clip(b, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    a = np.asarray(cell, np.float32) / 255
    lum = a[..., 0] * 0.3 + a[..., 1] * 0.55 + a[..., 2] * 0.15
    v = _q_resize_v(lum * a[..., 3], nw, nh)
    rgb, al = _q_resize_rgba(a, nw, nh)
    lum2 = rgb[..., 0] * 0.3 + rgb[..., 1] * 0.55 + rgb[..., 2] * 0.15
    t0, t1, t2, t3 = Q_T
    # 어두운 픽셀로 그린 곳 (블랙홀 속 · 검은 테두리) — 밝기는 낮아도 보임
    dark = (al >= 0.5) & (lum2 < 0.3)
    vis = (v >= t0) | dark
    solid = (v >= t1) | dark
    halo2 = (v >= (t0 + t1) / 2) & ~solid
    halo1 = vis & ~solid & ~halo2
    # 심지 봉우리: 밝고 둘레 (흐린 판) 보다 밝은 곳 — 평평한 면 전체가 하얘지지 않게 (가장자리 안쪽 빛 · 가는 빛줄 · 빛 덩이 가운데)
    blur = np.asarray(Image.fromarray((np.clip(v, 0, 1) * 255 + 0.5).astype(np.uint8), "L").filter(
        ImageFilter.GaussianBlur(max(1.0, max(nw, nh) / 24))), np.float32) / 255
    # 능선 (둘레 3 × 3 의 가장 밝은 곳 — 굵은 빛줄은 가운데 한두 픽셀만 흰 심지, 나머지는 계열 색)
    pad = np.pad(v, 1, mode="edge")
    mx3 = np.max(np.stack([pad[dy:dy + nh, dx:dx + nw] for dy in range(3) for dx in range(3)]), axis=0)
    core = (v >= t3) & (v >= blur + 0.05) & (v >= mx3 - 0.03) & ~dark
    lvl = np.where(v >= t2, Q_BASE, np.where(v >= t1, Q_MID, Q_DARK)).astype(np.float32)
    A = np.zeros((nh, nw, 4), np.float32)
    if colored:
        mx = np.maximum(rgb.max(-1), 1e-3)
        hue = rgb / mx[..., None]
        # 밝기 띠 (색조 그대로): 가장 밝은 채널을 다섯 단계로
        q = np.select([mx < 0.18, mx < 0.38, mx < 0.6, mx < 0.82], [0.1, 0.3, 0.5, 0.75], 1.0)
        A[..., :3] = hue * q[..., None]
        A[halo1 | halo2, :3] = (hue * np.maximum(q, 0.5)[..., None])[halo1 | halo2]
    else:
        A[..., :3] = lvl[..., None]
        A[halo1 | halo2, :3] = Q_HALO
        A[dark, :3] = Q_OUT
    A[..., 3] = np.where(solid, 1.0, np.where(halo2, 0.55, np.where(halo1, 0.3, 0.0)))
    if max(nw, nh) >= 12:
        ring = _q_dil(solid) & ~solid
        A[ring, :3] = 0.16 if colored else Q_OUT
        A[ring, 3] = 1.0
    A[core, 3] = 0.0
    A[..., 3] *= alpha
    B = np.zeros((nh, nw, 4), np.float32)
    B[..., :3] = 1.0
    B[..., 3] = core
    # C: 겹쳐 그리는 심지 층 — 외곽선 없이 (옅은 곳은 반투명)
    C = np.zeros((nh, nw, 4), np.float32)
    cm = (v >= t0) & ~dark
    if colored:
        C[..., :3] = rgb
    else:
        C[..., :3] = np.where(v >= t2, 1.0, np.where(v >= t1, 0.85, 0.7))[..., None]
    C[..., 3] = np.where(v >= t1, 1.0, 0.55) * cm

    def u8(x):
        return (np.clip(x, 0, 1) * 255 + 0.5).astype(np.uint8)

    return u8(A), u8(B), u8(C), bool(core.any())


# 칸의 긴 변 (픽셀) — 화면에서 보이는 크기 (칸) × 약 16 (블록 질감 밀도). 땅의 큰 고리 · 마법진 · 균열은 반지름 3 ~ 15칸이라 256 (원본보다 크면 부드럽게 늘린 뒤 띠로)
Q_LONG = [
    (("slash_shock_ring", "fx_rfx_nova", "fx_rfx_sigil", "fx_rfx_crack", "fx_rfx_aurora", "fx_rfx_fissure"), 256),
    (("magic_magic_circle", "fx_rfx_flare", "fx_rfx_hole", "fx_rfx_sun", "boss_whirlpool"), 192),
    (("fx_rfx_jpool", "fx_rfx_splash", "fx_rfx_bloom", "fx_rfx_cloud", "fx_rfx_king", "fx_rfx_emblem", "fx_rfx_fist", "fx_rfx_net", "fx_rfx_frost",
      "fx_rfx_rift", "fx_rfx_spiritking", "fx_rfx_punch", "fx_rfx_beast", "fx_rfx_wings", "fx_rfx_boom", "fx_rfx_meteor", "fx_rfx_moon", "fx_rfx_spear",
      "fx_rfx_bolt", "fx_rfx_chain", "fx_rfx_rays", "slash_bash_burst"), 128),
    (("boss_ground_crack", "fx_rfx_wave", "slash_slash_"), 96),
]


# 빛줄기 (하늘에서 내리는 빛 — 대천사 강림 · 천권 강림 둘레에 여럿): 픽셀로 단단히 그리면 둘레를 가리는 기둥이 됨 → A 층을 반투명 (B 심지는 그대로)
Q_ALPHA = {"fx_rfx_rays": 0.55}


def q_alpha(key):
    for n, a in Q_ALPHA.items():
        if key.startswith(n):
            return a
    return 1.0


def q_long(key, long128):
    """판 key 의 픽셀 칸 긴 변 (없으면 rpg:vfxp 칸의 반)"""
    for names, L in Q_LONG:
        if any(key.startswith(n) for n in names):
            return L
    return max(8, round(long128 / QDIV))


def rune_sheets(of):
    """룬이 쓰는 파티클의 판 (픽셀 글꼴은 이것만 — 나머지는 rpg:vfxp 로 그림): 룬 코드의 "rpg:…" · rfx 그림 이름 + 공용 도구가 쓰는 파티클"""
    import glob
    import re
    src = ""
    for f in glob.glob(os.path.join(ROOT, "src/main/java/mmorpg/fabric/rune/*.java")):
        src += open(f, encoding="utf-8").read()
    ids = set(re.findall(r'"(rpg:[a-z_0-9]+)"', src))
    for m in re.finditer(r'(?:rfx2a?|rfx|aura|auraCell|skyPlate|two)\([a-zA-Z_.()]+, "([a-z_0-9]+)"', src):
        ids.add("rpg:rfx_" + m.group(1))
    # RuneKit · RuneBoost 안에서 이름을 정한 그림 (마법진 · 고리 · 균열 · 섬광 · 빛기둥 · 분신 · 구슬 · 회오리 · 기운 · 방패 · 육각 · 화살 · 맞은 자리 …)
    for n in ("sigil", "nova", "crack", "flare", "rays", "shade", "plasma", "vortex", "aura", "shield", "hex", "arrow", "prop", "wave", "jhits", "jburst2",
              "jbreak", "jelem", "jpool", "jsmoke2"):
        ids.add("rpg:rfx_" + n)
    # SkillFx 도우미 (빛줄기 · 번개 · 베인 자국 · 바닥 룬 · 터짐 · 고리 · 충격파 · 불꽃 · 휘두름)
    ids.update(("rpg:arrow_trail", "rpg:light_cut", "rpg:skill_rune", "rpg:bash_burst", "rpg:bash_ring", "rpg:shock_ring", "rpg:arrow_spark", "rpg:arrow_shot",
                "rpg:slash_x"))
    for sh in ("arc", "heavy", "thin"):
        for lr in "lr":
            for layer in ("aura", "glow", "core"):
                ids.add("rpg:slash_%s_%s_%s" % (sh, lr, layer))
    return set(of[i] for i in ids if i in of)


def particle_sheets(defs):
    """파티클 ID → 판 이름, 판 이름 → (칸 그림 목록, uv 칸 [uw, vh], 칸 수 [cols, rows])"""
    sheets = {}
    of = {}
    for pid in sorted(defs):
        if not wanted(pid):
            continue
        d = defs[pid]
        sprite = d["sprite"]
        if not sprite.startswith("mmorpg:"):
            continue  # 바닐라 그림 (generic · flame) — 서버가 바닐라 파티클로
        uv = d["uv"]
        uw, vh = (uv["flipbook"]["size"] if "flipbook" in uv else uv["size"])
        tw, th = uv["tw"], uv["th"]
        name = sprite.split(":", 1)[1].replace("addon/rpg/", "").replace("/", "_")
        key = "%s_%dx%d" % (name, uw, vh)
        of[pid] = key
        if key in sheets:
            continue
        path = os.path.join(PARTICLE_TEX, sprite.split(":", 1)[1] + ".png")
        im = Image.open(path).convert("RGBA")
        W, Hh = im.size
        sx, sy = W / tw, Hh / th
        cols, rows = round(tw / uw), round(th / vh)
        cells = []
        full = []
        for r in range(rows):
            for c in range(cols):
                x0, y0 = round(c * uw * sx), round(r * vh * sy)
                x1, y1 = round((c + 1) * uw * sx), round((r + 1) * vh * sy)
                crop = im.crop((x0, y0, x1, y1))
                cells.append(shrink(crop, CAP_OF.get(pid, CAP)))
                full.append(shrink(crop, 256))  # 픽셀 글꼴의 원본 (큰 그림은 256 까지)
        sheets[key] = (cells, [uw, vh], [cols, rows], full)
    return of, sheets


def card_layers():
    """카드 모델 → 칸 (anim play 의 f0 … 또는 main 하나) 마다 겹 [(그림, mat, 가운데 x y z, 반 너비 · 반 높이)] — 앞뒤 빛 겹 (z ±) 은 하나로"""
    out = {}
    for name in CARDS:
        m = json.load(open(os.path.join(MODEL_DIR, name + ".json")))
        tex = m["tex"].split(":", 1)[1]
        im = Image.open(os.path.join(RES, "assets/mmorpg", tex)).convert("RGBA")
        W, Hh = im.size
        frames = []
        for p in m["parts"]:
            if not p["q"]:
                continue
            layers = []
            seen = set()
            for q in p["q"]:
                xs = [q[i * 8] for i in range(4)]
                ys = [q[i * 8 + 1] for i in range(4)]
                us = [q[i * 8 + 3] for i in range(4)]
                vs = [q[i * 8 + 4] for i in range(4)]
                mat = int(q[32])
                rect = (round(min(us) * W), round(min(vs) * Hh), round(max(us) * W), round(max(vs) * Hh))
                if (mat, rect) in seen:
                    continue  # 뒤쪽 빛 겹 (같은 그림) — 서버는 한 장
                seen.add((mat, rect))
                # 판 (모델) 의 가로세로 비로 늘임 — 모드는 그림 칸을 판에 늘여 붙임 (몸 · 빛 그림의 비가 달라도 같은 판). 한 글줄에 겹쳐 그리므로 겹마다 같은 비
                qa = (max(xs) - min(xs)) / max(1e-6, max(ys) - min(ys))
                crop = im.crop(rect)
                ch_px = crop.size[1]
                crop = crop.resize((max(1, round(ch_px * qa)), ch_px), Image.LANCZOS)
                cell = shrink(crop, CAP)
                cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
                layers.append((cell, mat, [round(cx, 4), round(cy, 4), 0.0], [round((max(xs) - min(xs)) / 2, 4), round((max(ys) - min(ys)) / 2, 4)]))
            # 그리는 차례: 몸 (mat 0) 먼저, 빛 (mat 1) 을 위에 (모델에서 빛 겹이 몸 앞뒤에 있음)
            layers.sort(key=lambda l: l[1])
            frames.append(layers)
        out[name] = frames
    return out


def build():
    defs = json.load(open(DEFS))["particles"]
    of, sheets = particle_sheets(defs)
    cards = card_layers()
    files = {}
    providers = []
    qproviders = []
    index_sheets = {}
    code = FIRST

    def sheet_png(key, cells, uniform=None):
        """칸 목록 → 판 그림 (칸 크기를 가장 큰 칸에 맞춤 — 한 판의 칸은 같은 크기) · 글자들 · advance (uniform 에 같은 크기로 맞춘 칸들)"""
        nonlocal code
        cw = max(c.size[0] for c in cells)
        ch = max(c.size[1] for c in cells)
        cols = min(COLS, len(cells))
        rows = (len(cells) + cols - 1) // cols
        img = Image.new("RGBA", (cols * cw, rows * ch), (0, 0, 0, 0))
        lines = []
        chars = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(cells):
                    cell = cells[i]
                    if cell.size != (cw, ch):
                        cell = cell.resize((cw, ch), Image.LANCZOS)
                    if uniform is not None:
                        uniform.append(cell)
                    cell = cell.copy()
                    px = cell.load()
                    if px[cw - 1, 0][3] == 0:
                        px[cw - 1, 0] = (255, 255, 255, 1)  # 너비가 줄지 않게 (보이지 않음 — 0.1 아래는 버림)
                    img.paste(cell, (c * cw, r * ch))
                    chars.append(code)
                    line += chr(code)
                    code += 1
                else:
                    line += "\u0000"
            lines.append(line)
        buf = BytesIO()
        img.save(buf, "PNG", optimize=True)
        files[os.path.join(OUT_TEX, key + ".png")] = buf.getvalue()
        providers.append({"type": "bitmap", "file": "rpg:font/vfxp/%s.png" % key, "height": H, "ascent": H // 2 - 2, "chars": lines})
        scale = H / ch
        advance = int(0.5 + cw * scale) + 1
        return chars, cw, ch, advance

    def q_png(name, glyphs, gw, gh):
        """(그림, 글자 번호) 목록 → 판 하나 (COLS 칸씩 — 빈 칸은 \u0000)"""
        cols = min(COLS, len(glyphs))
        rows = (len(glyphs) + cols - 1) // cols
        img = Image.new("RGBA", (cols * gw, rows * gh), (0, 0, 0, 0))
        lines = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(glyphs):
                    arr, code = glyphs[i]
                    arr = arr.copy()
                    if arr[0, gw - 1, 3] == 0:
                        arr[0, gw - 1] = (255, 255, 255, 1)  # 너비가 줄지 않게
                    img.paste(Image.fromarray(arr, "RGBA"), (c * gw, r * gh))
                    line += chr(code)
                else:
                    line += "\u0000"
            lines.append(line)
        buf = BytesIO()
        img.save(buf, "PNG", optimize=True)
        files[os.path.join(OUT_QTEX, name + ".png")] = buf.getvalue()
        qproviders.append({"type": "bitmap", "file": "rpg:font/vfxq/%s.png" % name, "height": H, "ascent": H // 2 - 2, "chars": lines})

    def q_sheet(key, cells, chars, L):
        """
        픽셀 글꼴 판 (번호 + 0 · QB · QC): A (모든 칸) · B (흰 심지가 있는 칸만 촘촘히) · C (겹치는 심지 층 — 반 크기, 따로 띄우는 글자라 A 와 픽셀을 맞출
        필요 없음) → 칸 크기 · advance · 심지가 있는 칸 ("0101…"). L = 칸의 긴 변 (픽셀)
        """
        cw, ch = cells[0].size
        k = L / max(cw, ch)
        nw, nh = max(4, round(cw * k)), max(4, round(ch * k))
        cnw, cnh = max(4, round(nw / 2)), max(4, round(nh / 2))
        colored = q_colored(cells)
        As, Bs, Cs = [], [], []
        has_b = ""
        qa = q_alpha(key)
        for i, cell in enumerate(cells):
            A, B, _c, hb = q_cell(cell, colored, nw, nh, qa)
            C = q_cell(cell, colored, cnw, cnh)[2]
            has_b += "1" if hb else "0"
            As.append((A, chars[i]))
            if hb:
                Bs.append((B, chars[i] + QB))
            Cs.append((C, chars[i] + QC))
        q_png(key, As, nw, nh)
        if Bs:
            q_png(key + "_b", Bs, nw, nh)
        q_png(key + "_c", Cs, cnw, cnh)
        return {"px": [nw, nh], "adv": int(0.5 + nw * H / nh) + 1, "b": has_b, "colored": colored}

    rune = rune_sheets(of)
    for key in sorted(sheets):
        cells, uvc, grid, full = sheets[key]
        chars, cw, ch, adv = sheet_png(key, cells)
        index_sheets[key] = {"first": chars[0], "n": len(chars), "cols": grid[0], "rows": grid[1], "uw": uvc[0], "vh": uvc[1], "px": [cw, ch], "adv": adv}
        if key in rune:
            # 픽셀 글꼴: 같은 크기로 맞춘 원본 칸 (판의 가로세로 비 — rpg:vfxp 칸과 같게)
            fw = max(c.size[0] for c in full)
            fh = max(c.size[1] for c in full)
            fw, fh = (fw, round(fw * ch / cw)) if cw >= ch else (round(fh * cw / ch), fh)
            uni = [c if c.size == (fw, fh) else c.resize((fw, fh), Image.LANCZOS) for c in full]
            index_sheets[key]["q"] = q_sheet(key, uni, chars, q_long(key, max(cw, ch)))
    index_cards = {}
    for name in CARDS:
        frames = []
        for fi, layers in enumerate(cards[name]):
            fr = []
            for li, (cell, mat, c, half) in enumerate(layers):
                chars, cw, ch, adv = sheet_png("card_%s_f%d_%d" % (name, fi, li), [cell])
                fr.append({"c": chars[0], "mat": mat, "at": c, "half": half, "adv": adv})
            frames.append(fr)
        index_cards[name] = frames
    assert code < SPACE_FIRST, "glyphs overflow %x" % code
    assert code - FIRST <= QB and FIRST + QC + (code - FIRST) < SPACE_FIRST, "pixel glyphs overflow"
    # 뒤로 옮김 (서버가 여러 겹을 한 글줄에 겹쳐 그림)
    adv = {chr(SPACE_FIRST + b): -(1 << b) for b in range(11)}
    providers.append({"type": "space", "advances": adv})
    qproviders.append({"type": "space", "advances": adv})
    font = {"providers": providers}
    idx = {
        "_doc": "tools/vfx_particles.py 가 만듦 (손으로 고치지 않음) — 글꼴 rpg:vfxp: 파티클 ID → 판 (uv 칸 하나 = 글자 하나, 칸 번호 = 줄 × cols + 칸), "
                "카드 (Fx3d 판) → 칸마다 겹 (글자 · mat 0 몸 / 1 빛 · 모델 자리 · 반 크기). 글자 높이 h 글꼴 단위 (1 단위 = 0.025칸), adv = 글자 너비 (뒤로 옮김 space "
                "글자 space+b 가 −2^b). 픽셀 글꼴 qfont (판마다 q — 칸 크기 · adv · 흰 심지 B 가 있는 칸): A = 같은 번호 · B = + qb · C (겹치는 심지 층) = + qc",
        "font": "rpg:vfxp", "h": H, "space": SPACE_FIRST,
        "qfont": "rpg:vfxq", "qb": QB, "qc": QC,
        "particles": of, "sheets": index_sheets, "cards": index_cards,
    }
    files[OUT_FONT] = (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_QFONT] = (json.dumps({"providers": qproviders}, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_INDEX] = (json.dumps(idx, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8")
    return files, idx


def main():
    check = "--check" in sys.argv
    files, idx = build()
    bad = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    # 지난 판 (이제 쓰지 않는 그림) 지우기
    for d in (OUT_TEX, OUT_QTEX):
        if not os.path.isdir(d):
            continue
        for f in os.listdir(d):
            p = os.path.join(d, f)
            if p not in files:
                if check:
                    bad.append(os.path.relpath(p, ROOT))
                else:
                    os.remove(p)
    if check:
        if bad:
            print("vfx particles differ: " + ", ".join(bad[:8]) + (" …" if len(bad) > 8 else "") + " — python3 tools/vfx_particles.py")
            return 1
        print("vfx particles ok (%d particles, %d sheets, %d cards)" % (len(idx["particles"]), len(idx["sheets"]), len(idx["cards"])))
        return 0
    png = sum(len(v) for k, v in files.items() if k.endswith(".png"))
    px = 0
    for k, v in files.items():
        if k.endswith(".png"):
            w, h = Image.open(BytesIO(v)).size
            px += w * h
    print("wrote %d particles → %d sheets, %d cards; png %.1f MB, %.1f M px (RGBA %.0f MB)" % (len(idx["particles"]), len(idx["sheets"]), len(idx["cards"]),
                                                                                         png / 1e6, px / 1e6, px * 4 / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main())
