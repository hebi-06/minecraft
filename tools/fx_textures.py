#!/usr/bin/env python3
"""이펙트 그림: 애드온 파티클 (RPG_RP/particles · textures/particle) → 자바 파티클 그림 + 목록.

  python3 tools/fx_textures.py <애드온 폴더>

만들어지는 것
- src/main/resources/assets/mmorpg/textures/particle/fx/<그림>[_<장>].png  (여러 장 그림은 장마다 나누고, 정사각형이 아니면 투명하게 늘려 정사각형으로
  — 자바 파티클 사각형은 정사각형이라 큰 쪽 길이로 맞추면 애드온의 가로 · 세로 비율이 그대로)
- src/main/resources/assets/mmorpg/particles/fx.json                      (클라이언트가 이름으로 찾음)
- src/main/java/mmorpg/fabric/FxSprite.java                               (일반 그림 파티클의 모양 · 움직임: 장 수 · 크기 배율 · 커짐 · 끌림 · 사라짐 · 방향)
활 빛줄기 · 지나간 자리 · 불꽃 · 마법진은 FxParticle 이 따로 그림 (FxOptions.ARROW_* · MAGIC_*) — 그림만 여기서 만듦.
무기 빛 알갱이 (FxOptions.GLINT) 는 바닐라 파티클 그림을 그대로 씀 (애드온도 바닐라 particles.png 의 칸) — 목록에 이름만 넣음.
자바 전용 그림 (JAVA_ONLY — 애드온에 없음, 여기서 그림): twinkle (네 갈래 반짝임 — FxOptions.GLINT_TEXTURES[TWINKLE], 0.14.2 요정 날개 가루).
  python3 tools/fx_textures.py --java-only   # 애드온 없이 자바 전용 그림만 다시 (fx.json 에 없으면 이름을 더함)
"""
import json
import math
import os
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "textures", "particle", "fx")
OUT_JSON = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "particles", "fx.json")
OUT_JAVA = os.path.join(ROOT, "src", "main", "java", "mmorpg", "fabric", "FxSprite.java")

# 특수 파티클 (FxParticle 이 직접): 그림만
SPECIAL = {
    "fx/arrow_streak": None,
    "fx/arrow_trail": None,
    "fx/arrow_spark": None,
    "magic/magic_circle": {"frames": 10, "frame": (128, 128)},
}
# 일반 그림 파티클 (FxSprite): 애드온 파티클 이름
SPRITES = [f"slash/slash_{shape}_{side}_{layer}" for shape in ("arc", "thin", "heavy") for side in ("r", "l") for layer in ("glow", "core", "aura")]
SPRITES += ["slash/slash_x", "slash/shock_ring", "slash/bash_ring", "slash/bash_burst"]
# 무기 빛 알갱이: 바닐라 그림 (FxOptions.GLINT_TEXTURES 와 같은 순서 — tools/weapon_models.py 의 VANILLA_PARTICLE_CELL)
VANILLA = ["minecraft:generic_0", "minecraft:flame"]


def twinkle_texture():
    """네 갈래 반짝임 (흰색 — 색은 파티클이 곱함): 가는 십자 빛 + 옅은 대각선 + 둥근 심지"""
    n = 32
    img = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    px = img.load()
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = (x - c) / (n / 2), (y - c) / (n / 2)
            r = math.hypot(dx, dy)
            arm = 1.3 * max(math.exp(-(abs(dy) / 0.06) ** 1.5) * max(0.0, 1 - abs(dx)) ** 1.3,
                            math.exp(-(abs(dx) / 0.06) ** 1.5) * max(0.0, 1 - abs(dy)) ** 1.3)
            diag = math.exp(-abs(abs(dx) - abs(dy)) / 0.06) * max(0.0, 1 - r / 0.5) ** 2 * 0.5
            core = max(0.0, 1 - r / 0.3) ** 1.6
            px[x, y] = (255, 255, 255, int(round(min(1.0, arm + diag + core) * 255)))
    return img


# 자바 전용 그림 (애드온에 없음): 이름 → 그리는 함수
JAVA_ONLY = {"twinkle": twinkle_texture}


def java_only():
    names = []
    for name, fn in JAVA_ONLY.items():
        fn().save(os.path.join(OUT_TEX, f"{name}.png"))
        names.append(f"mmorpg:fx/{name}")
    return names


def tex_name(path):
    return path.split("/")[-1]


def write_frames(img, frames, fw, fh, step, base):
    """장마다 나눠 정사각형으로 (가운데 맞춤, 남는 곳은 투명)"""
    side = max(fw, fh)
    names = []
    for i in range(frames):
        x, y = step[0] * i, step[1] * i
        fr = img.crop((x, y, x + fw, y + fh))
        sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        sq.paste(fr, ((side - fw) // 2, (side - fh) // 2))
        name = base if frames == 1 else f"{base}_{i}"
        sq.save(os.path.join(OUT_TEX, f"{name}.png"))
        names.append(f"mmorpg:fx/{name}")
    return names


def factor(expr):
    """'variable.size * (1 + 0.25 * (…)) * 1.0616' → (커짐 0.25, 배율 1.0616)"""
    g = re.search(r"\(1 \+ ([\d.]+) \*", expr)
    m = re.search(r"\*\s*([\d.]+)\s*$", expr.strip())
    return (float(g.group(1)) if g else 0.0), (float(m.group(1)) if m else 1.0)


def main(addon):
    rp = os.path.join(addon, "RPG_RP")
    os.makedirs(OUT_TEX, exist_ok=True)
    for f in os.listdir(OUT_TEX):
        os.remove(os.path.join(OUT_TEX, f))
    textures = []
    # 특수 그림
    for path, spec in SPECIAL.items():
        img = Image.open(os.path.join(rp, "textures", "particle", "rpg", f"{path}.png")).convert("RGBA")
        base = tex_name(path)
        if spec is None:
            img.save(os.path.join(OUT_TEX, f"{base}.png"))
            textures.append(f"mmorpg:fx/{base}")
        else:
            fw, fh = spec["frame"]
            textures += write_frames(img, spec["frames"], fw, fh, (0, fh), base)
    # 일반 그림 파티클
    entries = []
    done_tex = set()
    for pid in SPRITES:
        pe = json.load(open(os.path.join(rp, "particles", "rpg", f"{pid}.particle.json"), encoding="utf-8"))["particle_effect"]
        comp = pe["components"]
        tex_path = pe["description"]["basic_render_parameters"]["texture"]  # textures/particle/rpg/slash/slash_arc_r_glow
        base = tex_path.split("/")[-1]
        bb = comp["minecraft:particle_appearance_billboard"]
        gx, wx = factor(bb["size"][0])
        gy, wy = factor(bb["size"][1])
        assert abs(gx - gy) < 1e-9, pid
        uv = bb["uv"]
        fb = uv.get("flipbook")
        img = Image.open(os.path.join(rp, f"{tex_path}.png")).convert("RGBA")
        if fb:
            frames = int(fb["max_frame"])
            fw, fh = fb["size_UV"]
            step = fb["step_UV"]
            assert fb.get("stretch_to_lifetime"), pid
            # 텍스처 크기가 uv 의 texture_width 와 다르면 (심지 = 2배 해상도) 비율대로
            sx = img.size[0] / uv["texture_width"]
            sy = img.size[1] / uv["texture_height"]
            fw, fh, step = round(fw * sx), round(fh * sy), (round(step[0] * sx), round(step[1] * sy))
        else:
            frames = 1
            fw, fh = img.size
            step = (0, 0)
        # 정사각형 한 변 = 큰 쪽 → 배율도 큰 쪽 것
        scale = wx if fw >= fh else wy
        if base not in done_tex:
            textures += write_frames(img, frames, fw, fh, step, base)
            done_tex.add(base)
        drag = float(comp.get("minecraft:particle_motion_dynamic", {}).get("linear_drag_coefficient", 0))
        alpha = comp["minecraft:particle_appearance_tinting"]["color"][3]
        fade = "SQUARE" if "particle_age / variable.particle_lifetime) * (variable.particle_age" in alpha else "NONE"
        facing = "FLAT" if bb["facing_camera_mode"] == "emitter_transform_xz" else "CAMERA"
        assert bb["facing_camera_mode"] in ("rotate_xyz", "emitter_transform_xz"), pid
        entries.append((tex_name(pid).upper(), base, frames, scale, gx, drag, fade, facing))
    textures += VANILLA
    textures += java_only()
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump({"textures": textures}, fh, indent=2)
        fh.write("\n")
    # 자바 목록
    lines = []
    for name, base, frames, scale, grow, drag, fade, facing in entries:
        lines.append(f'    {name}("{base}", {frames}, {scale}F, {grow}F, {drag}F, Fade.{fade}, Facing.{facing})')
    body = ",\n".join(lines)
    java = f'''package mmorpg.fabric;

/**
 * 일반 그림 파티클 (애드온 RPG_RP/particles/rpg 의 같은 이름) — tools/fx_textures.py 가 만듦, 손으로 고치지 않음.
 * 그림 assets/mmorpg/textures/particle/fx/<texture>[_<장>].png (정사각형으로 늘린 것).
 * 반 크기 = size × scale × (1 + grow × 진행), 움직임 = 처음 속도 × 끌림 (초당 감속 계수), 여러 장이면 수명 동안 차례로.
 */
public enum FxSprite {{
{body};

    public enum Fade {{ NONE, SQUARE }}

    public enum Facing {{ CAMERA, FLAT }}

    public final String texture;
    public final int frames;
    public final float scale;
    public final float grow;
    public final float drag;
    public final Fade fade;
    public final Facing facing;

    FxSprite(String texture, int frames, float scale, float grow, float drag, Fade fade, Facing facing) {{
        this.texture = texture;
        this.frames = frames;
        this.scale = scale;
        this.grow = grow;
        this.drag = drag;
        this.fade = fade;
        this.facing = facing;
    }}

    private static final FxSprite[] VALUES = values();

    public static FxSprite byId(int id) {{
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }}
}}
'''
    with open(OUT_JAVA, "w", encoding="utf-8") as fh:
        fh.write(java)
    print(f"{len(textures)} textures, {len(entries)} sprite effects")
    for e in entries:
        print("  ", e)


def main_java_only():
    os.makedirs(OUT_TEX, exist_ok=True)
    names = java_only()
    with open(OUT_JSON, encoding="utf-8") as fh:
        d = json.load(fh)
    for n in names:
        if n not in d["textures"]:
            d["textures"].append(n)
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(d, fh, indent=2)
        fh.write("\n")
    print("java-only textures:", ", ".join(names))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--java-only":
        main_java_only()
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "/home/claude/rpg_addon")
