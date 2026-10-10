#!/usr/bin/env python3
"""길 안내 화면 칸 그림 (GUI 스프라이트): 애드온 RPG_RP/textures/rpg_ui/nav 를 복사 (테두리는 nine_slice 정보 — 애드온 .json 의 nineslice_size).

tools/nav_sprites.py <애드온 폴더 (rpg_addon)>

- assets/mmorpg/textures/gui/sprites/nav/<이름>.png (+ bg1 · bg2 의 .png.mcmeta)
화살표 a_m00 ~ a_m15 (메인 금빛) · a_c00 ~ a_c15 (일반 하늘빛) — 내가 보는 쪽에서 시계 방향으로 22.5° 씩, 도착 a_mhr · a_chr,
테두리 bg1 (메인) · bg2 (일반) — 24×18, nine_slice 4. 투명 그림 (bg0 · a_non) 은 그리지 않아서 넣지 않음.
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "nav")
NAMES = [f"a_{c}{i:02d}" for c in "mc" for i in range(16)] + ["a_mhr", "a_chr", "bg1", "bg2"]


def main():
    addon = sys.argv[1]
    src = os.path.join(addon, "RPG_RP", "textures", "rpg_ui", "nav")
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for name in NAMES:
        png = os.path.join(src, f"{name}.png")
        if not os.path.exists(png):
            raise SystemExit(f"missing {png}")
        shutil.copyfile(png, os.path.join(OUT, f"{name}.png"))
        meta = os.path.join(src, f"{name}.json")
        if os.path.exists(meta):
            m = json.load(open(meta, encoding="utf-8"))
            w, h = m["base_size"]
            border = m["nineslice_size"]
            with open(os.path.join(OUT, f"{name}.png.mcmeta"), "w", encoding="utf-8") as f:
                json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}, f, indent=2)
                f.write("\n")
        n += 1
    print(f"nav sprites {n} -> {OUT}")


if __name__ == "__main__":
    main()
