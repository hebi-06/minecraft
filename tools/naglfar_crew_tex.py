#!/usr/bin/env python3
"""0.51.0 네크로맨서 의 망자 선원 텍스처 (컨셉 "망자의 군대 — 스킬로 쓰러뜨린 적이 망자 선원으로 일어나 싸움", 색 독초록 · 뼈색):
북쪽 몬스터 드라우그 (rpg:draugr — 뿔 투구 · 해골 얼굴 · 사슬 갑옷 · 방패 · 서리 도끼 · 망토) 의 텍스처를 독초록 혼불 · 뼈색 망자로 다시 칠함
→ assets/mmorpg/textures/entity/rpg/allies/naglfar_crew(_emissive).png (모델 · 동작은 드라우그 그대로 — bedrock/entities.json 의 rpg:naglfar_crew).
티르빙의 보랏빛 망령 전사 (tools/tyrfing_warrior_tex.py) 와 같은 틀.

  python3 tools/naglfar_crew_tex.py              # 텍스처 두 장을 씀
  python3 tools/naglfar_crew_tex.py --preview D  # 확인용 그림 (드라우그 · 망자 선원 텍스처 나란히)

칠하기 (HSV — 밝기는 거의 그대로, 같은 무늬): 회색 (갑옷 · 피부 · 뼈) → 밝은 곳은 누런 뼈색 · 어두운 곳은 이끼 낀 푸른 쇠 (밝을수록 뼈 쪽),
갈색 (녹 · 가죽 · 나무) → 썩은 올리브 갈색 (짙게), 청록 빛 (눈 · 서리 도끼날) → 독초록 혼불 (빛나는 칸 _emissive 도 같은 칸 · 같은 색).
드라우그 (적 — 청록 서리) · 티르빙의 전사 (보라) 와 한눈에 구별되게.
"""
import colorsys
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/entity/rpg/monsters/draugr")
OUT = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/entity/rpg/allies/naglfar_crew")

BONE = 46 / 360.0  # 뼈 (누런 상아빛)
IRON = 150 / 360.0  # 이끼 낀 쇠 (푸른 초록 — 어두운 갑옷)
ROT = 72 / 360.0  # 썩은 가죽 · 나무 (올리브 갈색)
GLOW = 128 / 360.0  # 혼불 (독초록)


def recolor_px(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if v < 0.02:
        return r, g, b
    deg = h * 360
    if s > 0.4 and 160 <= deg <= 215:  # 청록 빛 (눈 · 서리 도끼날) → 독초록 혼불
        return colorsys.hsv_to_rgb(GLOW, 0.78, min(1.0, v * 1.08))
    if s >= 0.22 and 8 <= deg <= 65:  # 갈색 (녹 · 가죽 · 나무) → 썩은 올리브 갈색
        return colorsys.hsv_to_rgb(ROT, min(0.6, s * 0.85), v * 0.78)
    # 회색 (갑옷 · 피부 · 뼈): 밝은 곳 = 누런 뼈 · 어두운 곳 = 이끼 낀 푸른 쇠 (가운데는 섞음)
    k = max(0.0, min(1.0, (v - 0.32) / 0.26))
    if k >= 1:
        return colorsys.hsv_to_rgb(BONE, min(0.3, 0.16 + s * 0.3), min(1.0, v * 1.04))
    if k <= 0:
        return colorsys.hsv_to_rgb(IRON, min(0.42, 0.28 + s * 0.6), v * 0.9)
    # 가운데: 쇠 빛 → 뼈 빛을 RGB 로 섞음 (색상을 돌리면 그 사이의 노란 초록이 나옴)
    a = colorsys.hsv_to_rgb(IRON, min(0.42, 0.28 + s * 0.6), v * 0.9)
    b = colorsys.hsv_to_rgb(BONE, min(0.3, 0.16 + s * 0.3), min(1.0, v * 1.04))
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


def recolor(path_in, path_out):
    im = np.asarray(Image.open(path_in).convert("RGBA")).astype(np.float64) / 255.0
    out = im.copy()
    hgt, wid = im.shape[:2]
    for y in range(hgt):
        for x in range(wid):
            r, g, b, a = im[y, x]
            if a <= 0:
                continue
            out[y, x, :3] = recolor_px(r, g, b)
    img = Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    os.makedirs(os.path.dirname(path_out), exist_ok=True)
    img.save(path_out)
    return img


def main():
    a = recolor(SRC + ".png", OUT + ".png")
    recolor(SRC + "_emissive.png", OUT + "_emissive.png")
    print("wrote", os.path.relpath(OUT + ".png", ROOT), a.size, "+ _emissive")
    if "--preview" in sys.argv:
        d = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(d, exist_ok=True)
        src = Image.open(SRC + ".png").convert("RGBA")
        s = 6
        W, H = src.size[0] * s, src.size[1] * s
        sheet = Image.new("RGBA", (W * 2 + 8, H), (40, 40, 48, 255))
        for i, im in enumerate((src, a)):
            big = im.resize((W, H), Image.NEAREST)
            sheet.paste(big, (i * (W + 8), 0), big)
        p = os.path.join(d, "naglfar_crew_tex.png")
        sheet.save(p)
        print("preview", p)


if __name__ == "__main__":
    main()
