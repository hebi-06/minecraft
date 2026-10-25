#!/usr/bin/env python3
"""0.40.0 참격 궤적: 무기 디자인마다 손에 든 무기의 손잡이 · 무기 끝 자리 → WeaponBlades.java (손으로 고치지 않음).

무기 모델 (tools/weapon_models.py 와 같은 계산 — 상자 · 손 자세 display) 에서
- 3인칭: 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤) 의 손잡이 (weapon 뼈 축) · 무기 끝
  (손잡이에서 무기 축 (모델 +y) 방향으로 가장 먼 꼭짓점 — 상자 회전 포함)
- 1인칭: 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z, 오른손 · 동작 없음 · 손 시야 70) 의 손잡이 · 무기 끝
근접 무기 디자인 (칼 · 단검 · 도끼 · 마검 · 전투 망치 · 창 · 홀 · 낫 — 방패 · 활 · 완드는 빼고) 과 그 무기 스킨 (cosmetics.json).
클라이언트 BladeTrails 가 동작 자세 (MotionPoses) 로 손잡이 → 무기 끝을 옮겨 칼이 지나간 길을 그림.

  python3 tools/weapon_blades.py            # WeaponBlades.java 를 씀 + 디자인마다 무기 끝까지 거리
  python3 tools/weapon_blades.py --json F   # 같은 값을 JSON 으로 (미리 보기 tools/blade_trail_preview.py)
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402
import weapon_models as wm  # noqa: E402

ROOT = wm.ROOT
OUT = os.path.join(ROOT, "src", "client", "java", "mmorpg", "fabric", "client", "motion", "WeaponBlades.java")
MELEE = ("sword_", "dagger_", "axe_", "greatsword_", "hammer_", "lance_", "scepter_", "scythe_")


def corners(f, t):
    return [np.array([x, y, z]) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]


def blade(design, dm):
    """디자인 하나: {grip, tip} (3인칭 팔 공간) · {fpGrip, fpTip} (1인칭 카메라 공간) — 블록"""
    m = geo_tex.load(design, os.path.join(wm.ART, f"{design}.geo.json"), os.path.join(wm.ART, f"{design}.png"))
    geo = m.geo
    wb = next(b for b in geo["bones"] if b["name"].lower() == "weapon")
    P = np.array(wb.get("pivot") or [0, 0, 0], float)
    lo = np.full(3, 1e9)
    hi = np.full(3, -1e9)
    pts = []
    for c in m.cubes:
        a = wm.to3(np.array(c.lo, float)) - wm.to3(P)
        b = wm.to3(np.array(c.hi, float)) - wm.to3(P)
        f, t = np.minimum(a, b), np.maximum(a, b)
        lo, hi = np.minimum(lo, f), np.maximum(hi, t)
        cs = corners(f, t)
        if c.rot is not None:
            axis, ang, piv = c.rot
            three = {"x": -ang, "y": -ang, "z": ang}[axis]
            o = wm.to3(np.array(piv, float)) - wm.to3(P)
            R = {"x": wm.rx, "y": wm.ry, "z": wm.rz}[axis](math.radians(three))
            cs = [R @ (p - o) + o for p in cs]
        pts += cs
    extent = float(np.max(hi - lo))
    k = min(1.0, (wm.EXTENT_MAX - wm.EXTENT_MIN - 0.5) / extent)
    C0 = 8.0 - k * (lo + hi) / 2
    mpts = [k * p + C0 for p in pts]
    # 1인칭 (weapon_models.convert 와 같은 손 자세)
    bones = wm.skeleton(geo)
    wm.pose_first_person(bones, dm["fp"])
    R_w, sig, Wt = wm.split_affine(wm.world(bones, "weapon"))
    D = np.diag([-1.0, 1.0, -1.0])
    R_fp = D @ R_w
    s_fp = sig / k
    J_P = D @ (Wt - wm.BEDROCK_FP_CAMERA) / 16
    if design.startswith(wm.UPRIGHT):
        R_fp = wm.min_rot(R_fp @ np.array([0.0, 1.0, 0.0]), wm.UPRIGHT_DIR) @ R_fp
        if design.startswith(wm.EDGE_FORWARD):
            R_fp = wm.edge_forward() @ R_fp
    t_fp = J_P - wm.JAVA_FP_ARM - R_fp @ (s_fp * (C0 / 16 - 0.5))
    # 3인칭
    bones = wm.skeleton(geo)
    wm.pose_third_person(bones)
    cache = {}
    rel = np.linalg.inv(wm.world(bones, "rightarm", cache)) @ wm.world(bones, "weapon", cache)
    R_rel, sig3, rel_t = wm.split_affine(rel)
    T3J = np.diag([-1.0, -1.0, 1.0])
    Q = wm.rx(math.radians(-90)) @ wm.ry(math.radians(180))
    R_3p = Q.T @ T3J @ R_rel
    s_3p = sig3 / k
    t_3p = Q.T @ T3J @ rel_t / 16 - wm.JAVA_3P_OFFSET - R_3p @ (s_3p * (C0 / 16 - 0.5))

    def arm(p):
        return Q @ (wm.JAVA_3P_OFFSET + t_3p + R_3p @ (s_3p * (p / 16 - 0.5)))

    def fp(p):
        return wm.JAVA_FP_ARM + t_fp + R_fp @ (s_fp * (p / 16 - 0.5))

    grip, fgrip = arm(C0), fp(C0)
    ax, fax = Q @ R_3p @ np.array([0, 1.0, 0]), R_fp @ np.array([0, 1.0, 0])
    tip = max((arm(p) for p in mpts), key=lambda q: float((q - grip) @ ax))
    ftip = max((fp(p) for p in mpts), key=lambda q: float((q - fgrip) @ fax))
    return {"grip": grip.tolist(), "tip": tip.tolist(), "fpGrip": fgrip.tolist(), "fpTip": ftip.tolist()}


def jf(v):
    return f"{round(float(v), 5)!r}F"


def main(argv):
    meta = json.load(open(os.path.join(wm.ART, "designs.json"), encoding="utf-8"))
    data = {}
    for design, dm in sorted(meta["designs"].items()):
        if design.startswith(MELEE):
            data[design] = blade(design, dm)
    if "--json" in argv:
        out = argv[argv.index("--json") + 1]
        with open(out, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=1)
        print(f"{len(data)} designs → {out}")
        return 0
    cosmetics = json.load(open(os.path.join(ROOT, "src", "main", "resources", "mmorpg", "cosmetics.json"), encoding="utf-8"))
    skins = sorted((cid, it["design"]) for cid, it in cosmetics["items"].items() if it.get("slot") == "weapon" and it.get("design") in data)
    lines = []
    for design, b in data.items():
        g, t, fg, ft = b["grip"], b["tip"], b["fpGrip"], b["fpTip"]
        lines.append(f'            Map.entry("{design}", new Blade({jf(g[0])}, {jf(g[1])}, {jf(g[2])}, {jf(t[0])}, {jf(t[1])}, {jf(t[2])},\n'
                     f'                    {jf(fg[0])}, {jf(fg[1])}, {jf(fg[2])}, {jf(ft[0])}, {jf(ft[1])}, {jf(ft[2])}))')
    skin_lines = [f'            Map.entry("{cid}", "{d}")' for cid, d in skins]
    body = ",\n".join(lines)
    skin_body = ",\n".join(skin_lines)
    java = f'''package mmorpg.fabric.client.motion;

import java.util.Map;

/**
 * 0.40.0 참격 궤적: 근접 무기 디자인마다 손에 든 무기의 손잡이 · 무기 끝 (tools/weapon_blades.py 가 무기 모델에서 계산 — 손으로 고치지 않음).
 * <ul>
 *   <li>grip · tip: 3인칭 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤)</li>
 *   <li>fpGrip · fpTip: 1인칭 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z), 오른손 · 동작 없음</li>
 * </ul>
 * 왼손은 x 를 뒤집음. 무기 ID 의 디자인은 종류 · 등급 (보통 = 종류_lv0, 나머지 = 종류_등급_lv0) — BladeTrails.designOf.
 */
public final class WeaponBlades {{
    private WeaponBlades() {{}}

    public record Blade(float gripX, float gripY, float gripZ, float tipX, float tipY, float tipZ, float fpGripX, float fpGripY, float fpGripZ,
            float fpTipX, float fpTipY, float fpTipZ) {{}}

    /** 디자인 → 손잡이 · 무기 끝 */
    public static final Map<String, Blade> BY_DESIGN = Map.ofEntries(
{body});

    /** 무기 스킨 (장식 ID — cosmetics.json) → 디자인 */
    public static final Map<String, String> SKIN_DESIGN = Map.ofEntries(
{skin_body});
}}
'''
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(java)
    for design, b in data.items():
        g, t = np.array(b["grip"]), np.array(b["tip"])
        print(f"{design:26s} blade {np.linalg.norm(t - g):5.2f}")
    print(f"{len(data)} designs, {len(skins)} skins → {os.path.relpath(OUT, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
