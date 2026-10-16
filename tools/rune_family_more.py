#!/usr/bin/env python3
"""자바 전용 룬 계열의 아이콘 · 아이템 모델 · 아이템 정의 (0.12.6 — 사신 계열).

tools/rune_family_more.py

rules.json 위에 rules_more.json 을 덧붙인 RUNE_FAMILY_ORDER 에서 애드온에 없던 계열마다 다섯 등급:

- 아이콘 assets/rpg/textures/item/rune/rune_<계열>_<등급>.png (32×32): 애드온 룬 그림과 같은 돌 · 등급 테두리 위에 계열 문양.
  돌은 그 등급의 애드온 룬 16장에서 문양이 없는 쪽 (칸마다 무채색 · 어두운 돌 빛에 가장 가까운 것) 을 모으고, 문양 자리 (가운데 10 × 14) 는
  둘레 돌 빛으로 맞춘 매끈한 면 (2차 곡면) 으로 채움. 문양은 8 × 8 겹쳐 그려 가장자리를 부드럽게: 바깥 (굵게) = 계열 빛, 안 (가늘게) = 연한 심지,
  둘레 1 ~ 2칸 = 어두운 빛무리 (애드온 문양처럼).
  사신 (reaper): 핏빛 초승달 (왼쪽이 볼록 — 가운데가 굵음) + 아래 끝에 맺힌 핏방울
- 아이템 모델 assets/rpg/models/item/rune_<계열>_<등급>.json (generated), 아이템 정의 assets/rpg/items/rune_<계열>_<등급>.json
  (tools/rune_break_badges.py 와 같은 모양 — custom_model_data 첫 수 = 돌파 단계로 range_dispatch → 배지 모델 composite)
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
TEX = os.path.join(RES, "textures", "item", "rune")
GRADES = ["common", "rare", "noble", "hero", "transcend"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ------------------------------------------------------------ 돌 (문양 없는 룬)

def blank_stone(grade, families):
    """그 등급의 애드온 룬들에서 문양을 지운 돌 (테두리 · 빛은 그대로)"""
    ims = np.stack([np.array(Image.open(os.path.join(TEX, f"rune_{f}_{grade}.png")).convert("RGBA")).astype(float) for f in families])
    out = ims[0].copy()
    for y in range(6, 27):
        for x in range(9, 23):
            px = ims[:, y, x, :3]
            score = np.abs(px.mean(1) - 56) + (px.max(1) - px.min(1)) * 1.5  # 어두운 무채색 돌 빛에서 먼 정도
            out[y, x, :3] = px[int(np.argmin(score))]
    # 문양 자리는 둘레 돌 빛으로 맞춘 매끈한 면
    pts, vals = [], []
    for y in range(7, 25):
        for x in range(10, 22):
            if x in (10, 11, 20, 21) or y in (7, 8, 23, 24):
                pts.append((x, y))
                vals.append(out[y, x, :3])
    a = np.array([[1, x, y, x * x, y * y, x * y] for x, y in pts], float)
    coef, *_ = np.linalg.lstsq(a, np.array(vals, float), rcond=None)
    for y in range(9, 23):
        for x in range(11, 21):
            out[y, x, :3] = np.array([1, x, y, x * x, y * y, x * y], float) @ coef
    return out


# ------------------------------------------------------------ 문양

def coverage(fn, ss=8):
    cov = np.zeros((32, 32))
    for y in range(32):
        for x in range(32):
            n = 0
            for j in range(ss):
                for i in range(ss):
                    if fn(x + (i + 0.5) / ss, y + (j + 0.5) / ss):
                        n += 1
            cov[y, x] = n / (ss * ss)
    return cov


def reaper_shape(grow):
    """핏빛 초승달 (가운데 (16, 15.4) · 반지름 6.4, 위 끝 58° → 왼쪽 → 아래 끝 296°, 가운데가 굵음) + 아래 끝에 맺힌 핏방울"""
    cx, cy, r0, t0, t1 = 16.0, 15.4, 6.4, 58.0, 296.0

    def crescent(x, y):
        th = (math.degrees(math.atan2(y - cy, x - cx)) + 360) % 360
        if not (t0 - grow * 10 <= th <= t1 + grow * 10):
            return False
        q = max(0.0, min(1.0, (th - t0) / (t1 - t0)))
        thick = 0.3 + 2.3 * math.sin(math.pi * q)
        r = math.hypot(x - cx, y - cy)
        return r0 - thick - grow <= r <= r0 + grow * 0.8

    def drop(x, y):
        dx, dy, rr = 20.6, 23.6, 1.05 + grow
        if (x - dx) ** 2 + (y - dy) ** 2 <= rr * rr:
            return True
        top = dy - 2.7 - grow
        if top <= y <= dy:
            return abs(x - dx) <= rr * (y - top) / (dy - top)
        return False

    return lambda x, y: crescent(x, y) or drop(x, y)


# 계열 → (문양 모양, 심지 · 빛 · 빛무리 색)
GLYPHS = {
    "reaper": (reaper_shape, (255, 214, 206), (232, 52, 66), (150, 16, 34)),
}


def draw_glyph(stone, shape, core, mid, glow):
    cov_in = coverage(shape(0.0))
    cov_out = coverage(shape(0.85))
    near = np.zeros_like(cov_out)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if dx == 0 and dy == 0:
                continue
            w = 0.42 if max(abs(dx), abs(dy)) == 1 else 0.16
            near = np.maximum(near, np.roll(np.roll(cov_out, dy, 0), dx, 1) * w)
    out = stone.copy()
    core, mid, glow = (np.array(c, float) for c in (core, mid, glow))
    for y in range(32):
        for x in range(32):
            co, ci = cov_out[y, x], cov_in[y, x]
            px = out[y, x, :3]
            g = near[y, x] * (1 - co)
            px = px * (1 - g) + glow * g
            px = px * (1 - co) + mid * co
            px = px * (1 - ci) + core * ci
            out[y, x, :3] = px
    return Image.fromarray(np.clip(np.round(out), 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------ 모델 · 아이템 정의

def item_files(name, levels):
    write_json(os.path.join(RES, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/rune/{name}"}})
    base = {"type": "minecraft:model", "model": f"rpg:item/{name}"}
    entries = [{"threshold": n, "model": {"type": "minecraft:composite", "models": [base, {"type": "minecraft:model", "model": f"rpg:item/rune/break_{n}"}]}}
               for n in range(1, levels + 1)]
    write_json(os.path.join(RES, "items", f"{name}.json"), {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                                                                      "fallback": base, "entries": entries}})


def main():
    rules = json.load(open(os.path.join(CORE, "rules.json"), encoding="utf-8"))
    more = json.load(open(os.path.join(CORE, "rules_more.json"), encoding="utf-8"))
    rb = json.load(open(os.path.join(CORE, "rune_break.json"), encoding="utf-8"))
    addon = rules["RUNE_FAMILY_ORDER"]
    order = more.get("RUNE_FAMILY_ORDER", addon)
    made = []
    for fam in order:
        if fam in addon:
            continue
        if fam not in GLYPHS:
            raise SystemExit(f"{fam}: 문양이 없음 (GLYPHS)")
        shape, core, mid, glow = GLYPHS[fam]
        for g in GRADES:
            name = f"rune_{fam}_{g}"
            draw_glyph(blank_stone(g, addon), shape, core, mid, glow).save(os.path.join(TEX, f"{name}.png"))
            item_files(name, int(rb["max"]))
            made.append(name)
    print(f"rune items: {len(made)} ({', '.join(made)})")


if __name__ == "__main__":
    main()
