#!/usr/bin/env python3
"""창 그림 (GUI 스프라이트) 만들기: 애드온 rpg_ui 그림을 복사하고 늘이는 그림은 nine_slice 정보 (애드온 .json 의 nineslice_size) 를 씀.

tools/ui_sprites.py <애드온 폴더 (rpg_addon)>

- assets/mmorpg/textures/gui/sprites/ui/<이름>.png (+ .png.mcmeta: 상자 창 · 대화창 테두리)
상자 창 (chest_bg · slot · pane …) · 대화창 (dialog_bg · dialog_btn · dialog_name) · 선택지 그림 (q_new · icon_check …) · 등급 그림.
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "gui", "sprites", "ui")
NAMES = [
    "chest_bg", "slot", "slot_hover", "slot18", "slot18_hover", "pane",
    "dialog_bg", "dialog_btn", "dialog_btn_hover", "dialog_btn_press", "dialog_name",
    "icon_check", "icon_cross", "icon_next", "q_new", "q_active", "q_ready",
    "grade_common", "grade_rare", "grade_noble", "grade_hero", "grade_transcend",
    "item_unknown", "glint", "icon_str", "icon_dex", "icon_int", "icon_vit", "icon_point", "icon_mount", "ender_chest",
    "mode_btn", "mode_btn_on",
]


def main():
    addon = sys.argv[1]
    src = os.path.join(addon, "RPG_RP", "textures", "rpg_ui")
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
    print(f"ui sprites {n} -> {OUT}")


if __name__ == "__main__":
    main()
