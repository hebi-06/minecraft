#!/usr/bin/env python3
"""0.50.0 티르빙 망자의 각성의 아군 전사 텍스처 (사용자 "티르빙 궁은 실제 아군 몹이 나와서 때리는 느낌이면 좋을거 같음"):
북쪽 몬스터 드라우그 (rpg:draugr — 뿔 투구 · 해골 얼굴 · 사슬 갑옷 · 방패 · 서리 도끼 · 망토) 의 텍스처를 보랏빛 망령으로 다시 칠함
→ assets/mmorpg/textures/entity/rpg/allies/tyrfing_warrior(_emissive).png (모델 · 동작은 드라우그 그대로 — bedrock/entities.json 의 rpg:tyrfing_warrior).

  python3 tools/tyrfing_warrior_tex.py              # 텍스처 두 장을 씀
  python3 tools/tyrfing_warrior_tex.py --preview D  # 확인용 그림 (드라우그 · 망령 전사 텍스처 나란히)

칠하기 (HSV — 밝기는 거의 그대로, 같은 무늬): 회색 (갑옷 · 피부 · 뼈) → 보랏빛 회색 (밝을수록 옅게 — 뼈는 연보라), 갈색 (녹 · 가죽 · 나무) →
짙은 자줏빛 갈색, 청록 빛 (눈 · 서리 도끼날) → 밝은 보랏빛 (빛나는 칸 _emissive 도 같은 칸 · 같은 색). 드라우그 (적) 와 한눈에 구별되게.
"""
import colorsys
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/entity/rpg/monsters/draugr")
OUT = os.path.join(ROOT, "src/main/resources/assets/mmorpg/textures/entity/rpg/allies/tyrfing_warrior")

VIOLET = 268 / 360.0  # 갑옷 · 피부의 보랏빛
PLUM = 318 / 360.0  # 녹 · 가죽 · 나무의 자줏빛
GLOW = 284 / 360.0  # 눈 · 도끼날의 빛


def recolor_px(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if v < 0.02:
        return r, g, b
    deg = h * 360
    if s > 0.4 and 160 <= deg <= 215:  # 청록 빛 (눈 · 서리 도끼날)
        return colorsys.hsv_to_rgb(GLOW, 0.66, min(1.0, v * 1.04))
    if s >= 0.22 and 8 <= deg <= 65:  # 갈색 (녹 · 가죽 · 나무)
        return colorsys.hsv_to_rgb(PLUM, min(0.55, s * 0.78), v * 0.86)
    # 회색 (갑옷 · 피부 · 뼈): 밝을수록 옅은 보라 (뼈 = 연보라 · 갑옷 = 짙은 보랏빛 쇠)
    sat = min(0.42, 0.1 + s * 0.9 + 0.2 * (1 - v))
    return colorsys.hsv_to_rgb(VIOLET, sat, min(1.0, v * (0.92 + 0.1 * v)))


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
    b = recolor(SRC + "_emissive.png", OUT + "_emissive.png")
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
        p = os.path.join(d, "tyrfing_warrior_tex.png")
        sheet.save(p)
        print("preview", p)


if __name__ == "__main__":
    main()
