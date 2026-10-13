#!/usr/bin/env python3
"""애드온 파티클 119종 → 자바 범용 파티클 (mmorpg:addon) 의 정의 · 그림 · 이름표.

  python3 tools/addon_particles.py <애드온 폴더>

애드온 RPG_RP/particles/**/*.particle.json 의 구성 요소 (발생기 · 수명 · 속도 · 회전 · 움직임 · 빌보드 · UV · 색) 를 Molang 식 그대로 옮긴다.
클라이언트 (mmorpg.fabric.client.AddonParticles) 가 식을 해석해 베드락과 같은 계산으로 그린다.

만들어지는 것
- src/main/resources/assets/mmorpg/addon_particles.json   파티클 정의 (식은 문자열)
- src/main/resources/assets/mmorpg/textures/particle/addon/<애드온 그림 경로>.png   애드온 그림 그대로
    (자바 파티클 사각형은 정사각형만 되므로 직사각형은 클라이언트가 정사각형 여러 장으로 나눠 그림 — 그림은 손대지 않음).
    바닐라 그림 (textures/particle/particles 의 칸) 은 자바 바닐라 파티클 그림으로.
- src/main/resources/assets/mmorpg/particles/addon.json   그림 목록 (파티클 아틀라스)
- src/main/java/mmorpg/fabric/AddonFxTable.java           파티클 ID · 변수 이름표 (서버 · 클라이언트가 같은 번호를 씀)

12단계부터 자바 전용 파티클도 함께: tools/java_fx (tools/java_fx_art.py 가 만듦 — 애드온과 같은 꼴의 particles/**/*.particle.json + textures/…) 를
애드온 파티클 다음에 같은 방법으로 옮김 (이름이 겹치면 멈춤).
"""
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg")
OUT_TEX = os.path.join(ASSETS, "textures", "particle", "addon")
OUT_DEFS = os.path.join(ASSETS, "addon_particles.json")
OUT_SPRITES = os.path.join(ASSETS, "particles", "addon.json")
OUT_JAVA = os.path.join(ROOT, "src", "main", "java", "mmorpg", "fabric", "AddonFxTable.java")

# 바닐라 particles.png 칸 → 자바 바닐라 파티클 그림 (무기 빛 알갱이)
VANILLA_CELL = {(0, 0): "minecraft:generic_0", (0, 24): "minecraft:flame"}
# 파티클이 스스로 갖는 값 (식에서 쓰는 이름 — 서버가 보내지 않음)
BUILTIN = ["particle_age", "particle_lifetime", "particle_random_1", "particle_random_2", "particle_random_3", "particle_random_4",
           "emitter_age", "emitter_lifetime", "emitter_random_1", "emitter_random_2", "emitter_random_3", "emitter_random_4"]
MATERIAL = {"particles_blend": "blend", "particles_add": "add", "particles_alpha": "alpha"}


def norm(e):
    """식 안의 v.xxx → variable.xxx (이름표는 variable. 을 뺀 이름)"""
    if isinstance(e, str):
        return re.sub(r"(?<![\w.])v\.", "variable.", e)
    return e


def var_names(e):
    if not isinstance(e, str):
        return set()
    return {m for m in re.findall(r"variable\.([A-Za-z_][\w.]*)", norm(e))}


# ------------------------------------------------------------ 변환

def convert(path, rp):
    pe = json.load(open(path, encoding="utf-8"))["particle_effect"]
    pid = pe["description"]["identifier"]
    brp = pe["description"]["basic_render_parameters"]
    c = pe["components"]
    bb = c["minecraft:particle_appearance_billboard"]
    uv = bb.get("uv", {})
    d = {"material": MATERIAL[brp["material"]]}
    tex_path = brp["texture"]
    used = set()

    def keep(e):
        e = norm(e)
        used.update(var_names(e))
        return e

    # 그림
    if tex_path == "textures/particle/particles":
        cell = (uv["uv"][0], uv["uv"][1])
        d["sprite"] = VANILLA_CELL[cell]
        d["uv"] = {"static": [0, 0], "size": [1, 1], "tw": 1, "th": 1}
    else:
        png = os.path.join(rp, tex_path + ".png")
        img = Image.open(png).convert("RGBA")
        tw, th = float(uv.get("texture_width", img.size[0])), float(uv.get("texture_height", img.size[1]))
        base = tex_path.replace("textures/particle/", "")
        name = "addon/" + base
        file = os.path.join(OUT_TEX, base + ".png")
        os.makedirs(os.path.dirname(file), exist_ok=True)
        if not os.path.exists(file):
            img.save(file)
        d["sprite"] = "mmorpg:" + name
        fb = uv.get("flipbook")
        if fb:
            d["uv"] = {"flipbook": {"base": [keep(x) for x in fb["base_UV"]], "size": fb["size_UV"], "step": fb.get("step_UV", [0, 0]),
                                    "fps": keep(fb.get("frames_per_second", 0)), "max": keep(fb.get("max_frame", 1)),
                                    "stretch": bool(fb.get("stretch_to_lifetime", False)), "loop": bool(fb.get("loop", False))},
                       "tw": tw, "th": th}
        else:
            d["uv"] = {"static": [keep(x) for x in uv.get("uv", [0, 0])], "size": [keep(x) for x in (uv["uv_size"] if "uv_size" in uv else [tw, th])],
                       "tw": tw, "th": th}

    # 발생기
    if "minecraft:emitter_rate_instant" in c:
        d["rate"] = {"instant": keep(c["minecraft:emitter_rate_instant"].get("num_particles", 10))}
    else:
        st = c["minecraft:emitter_rate_steady"]
        d["rate"] = {"steady": keep(st.get("spawn_rate", 1)), "max": keep(st.get("max_particles", 50))}
    once = c.get("minecraft:emitter_lifetime_once")
    assert once is not None, pid
    d["active"] = keep(once.get("active_time", 10))
    shape = None
    for kind in ("point", "sphere", "box", "disc"):
        s = c.get(f"minecraft:emitter_shape_{kind}")
        if s is None:
            continue
        shape = {"type": kind}
        if "offset" in s:
            shape["offset"] = [keep(x) for x in s["offset"]]
        if "radius" in s:
            shape["radius"] = keep(s["radius"])
        if "half_dimensions" in s:
            shape["half"] = [keep(x) for x in s["half_dimensions"]]
        if "plane_normal" in s:
            pn = s["plane_normal"]
            shape["normal"] = {"x": [1, 0, 0], "y": [0, 1, 0], "z": [0, 0, 1]}[pn] if isinstance(pn, str) else [keep(x) for x in pn]
        shape["surface"] = bool(s.get("surface_only", False))
        dirv = s.get("direction", "outwards")
        shape["dir"] = dirv if isinstance(dirv, str) else [keep(x) for x in dirv]
    assert shape is not None, pid
    d["shape"] = shape

    # 알갱이
    d["life"] = keep(c["minecraft:particle_lifetime_expression"].get("max_lifetime", 1))
    sp = c.get("minecraft:particle_initial_speed", 0)
    assert not isinstance(sp, list), pid
    d["speed"] = keep(sp)
    spin = c.get("minecraft:particle_initial_spin")
    if spin:
        d["spin"] = [keep(spin.get("rotation", 0)), keep(spin.get("rotation_rate", 0))]
    dyn = c.get("minecraft:particle_motion_dynamic")
    par = c.get("minecraft:particle_motion_parametric")
    assert not (dyn and par and dyn != {}), pid
    if par:
        d["motion"] = {"parametric": [keep(x) for x in par["relative_position"]]}
    elif dyn is not None:  # 빈 particle_motion_dynamic {} 도 처음 빠르기로 움직임 (가속 · 끌림 0)
        d["motion"] = {"accel": [keep(x) for x in dyn.get("linear_acceleration", [0, 0, 0])], "drag": keep(dyn.get("linear_drag_coefficient", 0))}
    d["size"] = [keep(x) for x in bb["size"]]
    d["facing"] = bb.get("facing_camera_mode", "rotate_xyz")
    dr = bb.get("direction")
    if isinstance(dr, dict) and dr.get("mode") == "custom":
        d["face_dir"] = [keep(x) for x in dr["custom_direction"]]
    elif isinstance(dr, dict):
        d["min_speed"] = float(dr.get("min_speed_threshold", 0.01))
    d["color"] = [keep(x) for x in c["minecraft:particle_appearance_tinting"]["color"]]
    if len(d["color"]) == 3:
        d["color"].append(1)
    return pid, d, used


def main(addon):
    rp = os.path.join(addon, "RPG_RP")
    if os.path.isdir(OUT_TEX):
        for dirpath, _, files in os.walk(OUT_TEX):
            for f in files:
                os.remove(os.path.join(dirpath, f))
    os.makedirs(OUT_TEX, exist_ok=True)
    defs = {}
    all_vars = set()
    files = []
    for dirpath, _, fs in os.walk(os.path.join(rp, "particles")):
        files += [os.path.join(dirpath, f) for f in fs if f.endswith(".json")]
    for f in sorted(files):
        pid, d, used = convert(f, rp)
        assert pid not in defs, pid
        defs[pid] = d
        all_vars |= used
    # 자바 전용 (12단계 — tools/java_fx): 애드온과 같은 꼴, 그림도 그 폴더에서
    java_fx = os.path.join(ROOT, "tools", "java_fx")
    extra = []
    for dirpath, _, fs in os.walk(os.path.join(java_fx, "particles")):
        extra += [os.path.join(dirpath, f) for f in fs if f.endswith(".json")]
    for f in sorted(extra):
        pid, d, used = convert(f, java_fx)
        assert pid not in defs, f"자바 전용 파티클 이름이 애드온과 겹침: {pid}"
        defs[pid] = d
        all_vars |= used
    custom = sorted(v for v in all_vars if v not in BUILTIN)
    sprites = sorted({d["sprite"] for d in defs.values()})
    with open(OUT_DEFS, "w", encoding="utf-8") as fh:
        json.dump({"source": "MMO RPG 애드온 RPG_RP/particles + 자바 전용 tools/java_fx (tools/addon_particles.py 가 만듦, 손으로 고치지 않음)",
                   "builtin": BUILTIN, "vars": custom, "particles": dict(sorted(defs.items()))}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    with open(OUT_SPRITES, "w", encoding="utf-8") as fh:
        json.dump({"textures": sprites}, fh, indent=2)
        fh.write("\n")
    ids = sorted(defs)
    java = """package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;

/**
 * 애드온 파티클 이름표 — tools/addon_particles.py 가 만듦, 손으로 고치지 않음.
 * 서버는 파티클 ID · 변수 이름을 번호로 바꿔 보내고 (AddonParticleOptions), 클라이언트는 같은 번호로 정의 (assets/mmorpg/addon_particles.json) 를 찾음.
 */
public final class AddonFxTable {
    private AddonFxTable() {}

    /** 파티클 ID (번호 = 순서) */
    public static final String[] PARTICLES = {
%s
    };

    /** 서버가 보내는 변수 이름 (variable. 뒤, 번호 = 순서) */
    public static final String[] VARS = {
%s
    };

    private static final Map<String, Integer> PARTICLE_INDEX = new HashMap<>();
    private static final Map<String, Integer> VAR_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < PARTICLES.length; i++) PARTICLE_INDEX.put(PARTICLES[i], i);
        for (int i = 0; i < VARS.length; i++) VAR_INDEX.put(VARS[i], i);
    }

    /** 파티클 번호 (없으면 -1) */
    public static int particle(String id) {
        return PARTICLE_INDEX.getOrDefault(id, -1);
    }

    /** 변수 번호 (없으면 -1 — 어느 파티클 식에도 없는 변수) */
    public static int var(String name) {
        return VAR_INDEX.getOrDefault(name, -1);
    }
}
""" % (",\n".join(f'        "{i}"' for i in ids), ",\n".join(f'        "{v}"' for v in custom))
    with open(OUT_JAVA, "w", encoding="utf-8") as fh:
        fh.write(java)
    print(f"{len(defs)} particles, {len(sprites)} sprites, {len(custom)} variables")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/home/claude/rpg_addon")
