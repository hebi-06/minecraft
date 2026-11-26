#!/usr/bin/env python3
"""2026-11-26 평타 동작 다시 맞추기 (사용자 "플레이어 모션은 더 부드럽고 자연스럽게").

평타 동작 (MOTION.combo) 의 베는 구간 끝 열쇠 (휘두름이 끝나는 자세) 를 0.04 ~ 0.06 늦춰, 9 ~ 12틱 동작에서 두 틱이 채 안 되던 휘두름이
더 많은 틱에 걸치게 함. 따라 휘두름 · 멈춤 열쇠도 그만큼 뒤로 (거두기 구간이 짧아짐), 3인칭 궤적 구간 (trail — 기본 참격 판 · 칼 궤적의 때)
의 끝도 같이. 들어 올리는 구간 (시작 ~ 휘두름 시작) 과 1인칭 열쇠 (fp) 는 그대로.

  python3 tools/motion_retime_basic.py   # 한 번 (이미 옮긴 열쇠는 다시 옮기지 않음 — 옛 t 가 없으면 건너뜀)

그다음 tools/motion_arcs.py 가 베는 길 열쇠 (gen) 를 새 구간으로 다시 채움 (이 도구가 부름). 찌르기 · 밀치기의 참격 판 때는
rules_more.json BASIC_SLASH.motions 의 time.
"""
import importlib.util
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")

# 동작 → ({옛 t: 새 t} — 손으로 정한 열쇠만, 새 trail)
RETIME = {
    "slash_hb": ({0.5: 0.56, 0.66: 0.72, 0.84: 0.86}, [[0.3, 0.74]]),
    "slash_h": ({0.5: 0.56, 0.66: 0.72, 0.84: 0.86}, [[0.3, 0.74]]),
    "slash_v": ({0.52: 0.58, 0.7: 0.75, 0.86: 0.88}, [[0.32, 0.77]]),
    "gs_slam": ({0.56: 0.62, 0.78: 0.82}, [[0.38, 0.84]]),
    "reap_up": ({0.5: 0.56, 0.66: 0.72, 0.84: 0.86}, [[0.32, 0.74]]),
    "chop": ({0.56: 0.62, 0.76: 0.8}, [[0.36, 0.82]]),
    "smite": ({0.55: 0.61, 0.75: 0.79}, [[0.32, 0.81]]),
    "lance_sweep": ({0.5: 0.56, 0.66: 0.72, 0.85: 0.87}, [[0.28, 0.74]]),
    "dagger_x": ({0.36: 0.4, 0.72: 0.76}, [[0.16, 0.42], [0.5, 0.78]]),
    "thrust": ({0.5: 0.55, 0.7: 0.74}, None),
    "bash": ({0.5: 0.55, 0.72: 0.76}, None),
}


def load_arcs():
    spec = importlib.util.spec_from_file_location("motion_arcs", os.path.join(ROOT, "tools/motion_arcs.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    arcs = load_arcs()
    import json

    src = open(POSES, encoding="utf-8").read()
    data = json.loads(src)
    assert arcs.fmt(data) == src, "poses.json 모양이 다름"
    moved = 0
    for name, (table, trail) in RETIME.items():
        m = data["motions"][name]
        keys = [k for k in m["keys"] if not k.get("gen")]
        olds = {round(k["t"], 4) for k in keys}
        if not any(round(o, 4) in olds for o in table):
            print(f"{name:12s} 이미 옮김 — 건너뜀")
            continue
        for k in keys:
            t = round(k["t"], 4)
            if t in table:
                k["t"] = table[t]
                moved += 1
        ts = [k["t"] for k in keys]
        assert ts == sorted(ts) and len(set(ts)) == len(ts), (name, ts)
        m["keys"] = keys  # 베는 길 열쇠 (gen) 는 densify 가 새로
        if trail is not None:
            m["trail"] = trail
        print(f"{name:12s} {[round(k['t'], 4) for k in keys]} trail {m.get('trail')}")
    for name, m in data["motions"].items():
        if not name.startswith("_"):
            arcs.densify(name, m)
    res = arcs.fmt(data)
    if res != src:
        open(POSES, "w", encoding="utf-8").write(res)
    print(f"moved {moved} keys")
    return 0


if __name__ == "__main__":
    sys.exit(main())
