#!/usr/bin/env python3
"""스킬 칸 HUD 그림 (11단계): skill_slot · skill_edge (9조각) · skill_glow · skill_key (9조각) — 애드온 HUD 와 같은 픽셀 그림 느낌.

사용: python3 tools/skill_bar_sprites.py src/main/resources/assets/mmorpg/textures/gui/sprites/hud
skill_edge · skill_glow 는 흰색 (게임에서 등급 색 · 연출 색을 곱함).
"""
import json, math, os, sys
from PIL import Image

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

def save(name, im, nine=None):
    im.save(os.path.join(out, name + ".png"))
    meta = os.path.join(out, name + ".png.mcmeta")
    if nine:
        w, h, b = nine
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": b}}}, open(meta, "w"), indent=2)
    elif os.path.exists(meta):
        os.remove(meta)

# 칸 바탕 20x20: 검은 바깥 줄 · 어두운 남색 속 · 위왼쪽 밝은 줄 · 아래오른쪽 어두운 줄
N = 20
im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = im.load()
for y in range(N):
    for x in range(N):
        edge = x == 0 or y == 0 or x == N - 1 or y == N - 1
        corner = (x in (0, N - 1)) and (y in (0, N - 1))
        if corner:
            continue
        if edge:
            px[x, y] = (6, 6, 10, 225)
        elif x == 1 or y == 1:
            px[x, y] = (88, 92, 112, 200)
        elif x == N - 2 or y == N - 2:
            px[x, y] = (8, 9, 16, 210)
        else:
            # 속: 위가 조금 밝은 세로 그러데이션
            t = (y - 2) / (N - 5)
            c = (int(30 - 12 * t), int(33 - 13 * t), int(48 - 18 * t))
            px[x, y] = c + (185,)
save("skill_slot", im, (N, N, 3))

# 등급 테두리 20x20 (흰색 — 등급 색을 곱함): 안쪽 1칸 줄 + 모서리 보석 점
im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = im.load()
for y in range(N):
    for x in range(N):
        ring = (x in (1, N - 2) and 1 <= y <= N - 2) or (y in (1, N - 2) and 1 <= x <= N - 2)
        if ring:
            px[x, y] = (255, 255, 255, 235)
for (x, y) in [(2, 2), (N - 3, 2), (2, N - 3), (N - 3, N - 3)]:
    px[x, y] = (255, 255, 255, 150)
save("skill_edge", im, (N, N, 3))

# 빛 고리 32x32 (흰색 — 색을 곱함): 반지름 12 둘레가 가장 밝음
G = 32
im = Image.new("RGBA", (G, G), (0, 0, 0, 0))
px = im.load()
c = (G - 1) / 2
for y in range(G):
    for x in range(G):
        d = math.hypot(x - c, y - c)
        a = math.exp(-((d - 11.5) / 2.6) ** 2) * 0.95 + math.exp(-(d / 9.5) ** 2) * 0.18
        a = max(0, min(1, a))
        if d > 15.5:
            a = 0
        px[x, y] = (255, 255, 255, int(a * 255))
save("skill_glow", im)

# 키 모양 10x10 (9조각 3): 어두운 바탕 · 밝은 테 (위가 더 밝음) · 아래 그림자
K = 10
im = Image.new("RGBA", (K, K), (0, 0, 0, 0))
px = im.load()
for y in range(K):
    for x in range(K):
        corner = (x in (0, K - 1)) and (y in (0, K - 1))
        if corner:
            continue
        if y == K - 1:
            px[x, y] = (0, 0, 0, 170)
        elif x == 0 or x == K - 1 or y == 0:
            px[x, y] = (196, 200, 214, 255) if y == 0 else (150, 154, 170, 255)
        elif y == K - 2:
            px[x, y] = (96, 100, 116, 255)
        else:
            px[x, y] = (24, 26, 36, 235)
save("skill_key", im, (K, K, 3))
print("ok")
