#!/usr/bin/env python3
"""2026-11-16 피해 숫자 도트 글꼴 rpg:dmg (사용자 "2번으로 해보자" — 피드백 "스킬을 고퀄 이미지로 하니깐 몹이랑 데미지 피해 표시는 도트여서 괴리감이 든다"):
도트로 맞춘 스킬 그림과 같은 결의 굵은 숫자 — 2 픽셀 굵기 획 · 검은 외곽선 1 픽셀 · 위에서 아래로 어두워지는 칠 (글자 색으로 물듦: 흰 칠 × 글자 색 →
숫자 색 규칙 "무속성 흰색 · 치명타 노랑 · 원소 색 · 치명타는 짙게" 를 그대로 씀 — 외곽선은 검정이라 물들지 않음).

  python3 tools/dmg_font.py          # 씀
  python3 tools/dmg_font.py --check  # 쓸 것이 없는지

만드는 것: assets/rpg/textures/font/dmg.png (칸 10 × 12 — 칸 하나 = 글자 하나) · assets/rpg/font/dmg.json (비트맵 + 나머지 글자는 minecraft:default 참조 —
반응 이름 "증발 " · 표시 "◆" 등은 바닥 글꼴). 한 픽셀 = 글꼴 1 단위 (높이 12 · 그림자 · 굵게 옮김이 픽셀에 맞음). DamageNumbers 가 숫자에 씀 (NUM_SCALE 로 크기 맞춤).
"""
import io
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_TEX = os.path.join(ROOT, "src/main/resources/assets/rpg/textures/font/dmg.png")
OUT_FONT = os.path.join(ROOT, "src/main/resources/assets/rpg/font/dmg.json")

CW, CH = 10, 12  # 칸 (외곽선 포함)
ASCENT = 10

# 칠 (# = 획) — 숫자 7 × 10, 2 픽셀 굵기
GLYPHS = {
    "0": [".#####.",
          "##...##",
          "##...##",
          "##..###",
          "##.####",
          "####.##",
          "###..##",
          "##...##",
          "##...##",
          ".#####."],
    "1": ["..##",
          ".###",
          "####",
          "..##",
          "..##",
          "..##",
          "..##",
          "..##",
          "..##",
          "..##"],
    "2": [".#####.",
          "##...##",
          ".....##",
          ".....##",
          "....###",
          "..###..",
          ".##....",
          "##.....",
          "##.....",
          "#######"],
    "3": [".#####.",
          "##...##",
          ".....##",
          ".....##",
          "..####.",
          ".....##",
          ".....##",
          ".....##",
          "##...##",
          ".#####."],
    "4": ["....##.",
          "...###.",
          "..####.",
          ".##.##.",
          "##..##.",
          "##..##.",
          "#######",
          "....##.",
          "....##.",
          "....##."],
    "5": ["#######",
          "##.....",
          "##.....",
          "######.",
          ".....##",
          ".....##",
          ".....##",
          ".....##",
          "##...##",
          ".#####."],
    "6": ["..####.",
          ".##....",
          "##.....",
          "##.....",
          "######.",
          "##...##",
          "##...##",
          "##...##",
          "##...##",
          ".#####."],
    "7": ["#######",
          "##...##",
          ".....##",
          "....##.",
          "....##.",
          "...##..",
          "...##..",
          "..##...",
          "..##...",
          "..##..."],
    "8": [".#####.",
          "##...##",
          "##...##",
          "##...##",
          ".#####.",
          "##...##",
          "##...##",
          "##...##",
          "##...##",
          ".#####."],
    "9": [".#####.",
          "##...##",
          "##...##",
          "##...##",
          "##...##",
          ".######",
          ".....##",
          ".....##",
          "....##.",
          ".####.."],
    "+": [".....",
          ".....",
          "..#..",
          "..#..",
          "#####",
          "#####",
          "..#..",
          "..#..",
          ".....",
          "....."],
    "-": ["....",
          "....",
          "....",
          "....",
          "####",
          "####",
          "....",
          "....",
          "....",
          "...."],
    ".": ["..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "##",
          "##"],
    "!": ["##",
          "##",
          "##",
          "##",
          "##",
          "##",
          "##",
          "..",
          "##",
          "##"],
    ",": ["..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "..",
          "##",
          "#."],
    "%": ["##...##",
          "##..##.",
          "....##.",
          "...##..",
          "...##..",
          "..##...",
          "..##...",
          ".##....",
          ".##..##",
          "##...##"],
}
ORDER = "0123456789+-.!,%"
# 칠의 밝기 (줄마다 — 위가 글자 색 그대로, 아래로 짙어짐)
SHADE = [255, 255, 246, 236, 226, 214, 202, 190, 178, 166]
OUTLINE = (12, 10, 18)


def glyph(ch):
    rows = GLYPHS[ch]
    h = len(rows)
    w = len(rows[0])
    fill = np.array([[c == "#" for c in r] for r in rows], bool)
    img = np.zeros((CH, CW, 4), np.uint8)
    # 칸 안 자리: 외곽선 1 픽셀 여유 (왼쪽 1 · 위 1)
    ox, oy = 1, 1 + (10 - h)
    big = np.zeros((CH, CW), bool)
    big[oy:oy + h, ox:ox + w] = fill
    out = np.zeros_like(big)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            out |= np.roll(np.roll(big, dy, 0), dx, 1)
    out &= ~big
    img[out] = OUTLINE + (255,)
    for y in range(CH):
        for x in range(CW):
            if big[y, x]:
                v = SHADE[min(len(SHADE) - 1, y - oy)]
                img[y, x] = (v, v, v, 255)
    return img


def build():
    cols = len(ORDER)
    sheet = np.zeros((CH, CW * cols, 4), np.uint8)
    for i, ch in enumerate(ORDER):
        sheet[:, i * CW:(i + 1) * CW] = glyph(ch)
    buf = io.BytesIO()
    Image.fromarray(sheet, "RGBA").save(buf, "PNG", optimize=True)
    font = {"providers": [{"type": "bitmap", "file": "rpg:font/dmg.png", "height": CH, "ascent": ASCENT, "chars": [ORDER]},
                          {"type": "reference", "id": "minecraft:default"}]}
    return {OUT_TEX: buf.getvalue(), OUT_FONT: (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")}


def main():
    check = "--check" in sys.argv
    bad = []
    for path, data in build().items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if check:
        if bad:
            print("dmg font differs: " + ", ".join(bad) + " — python3 tools/dmg_font.py")
            return 1
        print("dmg font ok")
        return 0
    print("wrote dmg font (%d glyphs)" % len(ORDER))
    return 0


if __name__ == "__main__":
    sys.exit(main())
