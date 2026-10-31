#!/usr/bin/env python3
"""0.50.0 자바 전용 파티클을 더하거나 바꿈 (애드온 폴더 없이): tools/java_fx 의 정의 · 그림 중 이름을 준 것만 옮겨, 이미 있는
assets/mmorpg/addon_particles.json · particles/addon.json · AddonFxTable.java 에 넣음 (다른 파티클은 그대로).

  python3 tools/java_fx_add.py rfx_jsword rfx_jaxe …     # 이 이름들 (tools/java_fx/particles/rpg/fx/<이름>.particle.json) 을 넣음 · 바꿈

tools/addon_particles.py <애드온 폴더> 는 애드온 파티클까지 처음부터 다시 만듦 (애드온 폴더가 있어야 함 — 없으면 이것을 씀). 결과 꼴은 같음.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import addon_particles as A  # noqa: E402

JAVA_FX = os.path.join(A.ROOT, "tools", "java_fx")


def main(names):
    data = json.load(open(A.OUT_DEFS, encoding="utf-8"))
    defs = data["particles"]
    variables = set(data["vars"])
    for n in names:
        f = os.path.join(JAVA_FX, "particles", "rpg", "fx", n + ".particle.json")
        if not os.path.exists(f):
            raise SystemExit("없음: " + f)
        pe = json.load(open(f, encoding="utf-8"))["particle_effect"]
        tex = pe["description"]["basic_render_parameters"]["texture"]
        out = os.path.join(A.OUT_TEX, tex.replace("textures/particle/", "") + ".png")
        if os.path.exists(out):
            os.remove(out)  # 바뀐 그림을 다시 씀 (convert 는 없을 때만 씀)
        pid, d, used = A.convert(f, JAVA_FX)
        defs[pid] = d
        variables |= {v for v in used if v not in A.BUILTIN}
        print("put", pid, d["sprite"])
    custom = sorted(variables | set(A.SERVER_VARS))
    sprites = sorted({d["sprite"] for d in defs.values()})
    with open(A.OUT_DEFS, "w", encoding="utf-8") as fh:
        json.dump({"source": data["source"], "builtin": A.BUILTIN, "vars": custom, "particles": dict(sorted(defs.items()))}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    with open(A.OUT_SPRITES, "w", encoding="utf-8") as fh:
        json.dump({"textures": sprites}, fh, indent=2)
        fh.write("\n")
    # 이름표: addon_particles.main 과 같은 꼴 (번호 = 정렬 순서)
    src = open(A.OUT_JAVA, encoding="utf-8").read()
    head = src[:src.index("    public static final String[] PARTICLES = {")]
    tail = src[src.index("    private static final Map<String, Integer> PARTICLE_INDEX"):]
    ids = sorted(defs)
    body = ("    public static final String[] PARTICLES = {\n" + ",\n".join(f'        "{i}"' for i in ids) + "\n    };\n\n"
            "    /** 서버가 보내는 변수 이름 (variable. 뒤, 번호 = 순서) */\n    public static final String[] VARS = {\n"
            + ",\n".join(f'        "{v}"' for v in custom) + "\n    };\n\n")
    with open(A.OUT_JAVA, "w", encoding="utf-8") as fh:
        fh.write(head + body + tail)
    print(f"{len(defs)} particles, {len(sprites)} sprites, {len(custom)} variables")


if __name__ == "__main__":
    main(sys.argv[1:])
