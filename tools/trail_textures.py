#!/usr/bin/env python3
"""0.40.0 참격 궤적 그림 (클라이언트 BladeTrails — RenderTypes.text 로 띠에 붙임).

띠 그림은 가로 (u) = 궤적 길이 방향 (같은 값 — 오래된 쪽이 흐려지는 것은 꼭짓점 색으로), 세로 (v) = 띠 너비 방향
(v = 0 무기 끝 쪽 바깥 가장자리 → v = 1 손잡이 쪽 안 가장자리). 흰색 그림 + 알파 (색은 꼭짓점 색으로 입힘), 흐리게 늘림 (blur).
- blade_trail_core: 바깥 가장자리가 또렷하고 밝은 흰 띠 → 안쪽으로 부드럽게 옅어짐 (사진처럼 넓은 흰 띠) + 바람결 줄 (v 마다 조금씩 다른 밝기)
- blade_trail_glow: 넓고 부드러운 빛 (무기 색) — 바깥으로 조금 넘침
맨 위 · 아래 한 줄은 늘 투명 (BladeTrails 가 겹마다 같은 사각형에 v 만 달리 붙여 그 밖은 가장자리 줄이 늘어나므로).

  python3 tools/trail_textures.py
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "fx")
W, H = 32, 128


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def core(v):
    edge = smooth(0.0, 0.05, v)  # 바깥 가장자리 (부드러운 1 ~ 2 텍셀)
    body = 1.0 - 0.75 * smooth(0.18, 0.95, v)  # 안쪽으로 옅어짐
    inner = 1.0 - smooth(0.82, 1.0, v)  # 안 가장자리는 0 으로
    # 바람결: 가는 밝은 줄 · 옅은 줄 (v 로만 — 띠를 따라 이어짐)
    streak = 1.0 + 0.10 * math.sin(v * 46.0) * smooth(0.1, 0.3, v) - 0.12 * math.exp(-((v - 0.42) / 0.03) ** 2)
    return max(0.0, min(1.0, edge * body * inner * streak))


def glow(v):
    # 무기 끝 줄 (v ≈ 0.15 — 빛 띠는 바깥으로 넘치게 그림) 에서 가장 밝고 양쪽으로 옅어짐
    out = smooth(0.0, 0.15, v)
    inn = 1.0 - smooth(0.15, 1.0, v) ** 0.8
    return max(0.0, min(1.0, out * inn * 0.95))


def write(name, fn):
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        v = (y + 0.5) / H
        # 맨 위 · 아래 줄은 투명: 한 사각형에 여러 겹을 그려 v 가 0 ~ 1 밖으로 나가면 (clamp) 이 줄이 늘어남
        a = 0 if y in (0, H - 1) else round(fn(v) * 255)
        for x in range(W):
            px[x, y] = (255, 255, 255, a)
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"))
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"texture": {"blur": True, "clamp": True}}, fh)
        fh.write("\n")
    print(os.path.relpath(os.path.join(OUT, name + ".png"), ROOT))


if __name__ == "__main__":
    write("blade_trail_core", core)
    write("blade_trail_glow", glow)
