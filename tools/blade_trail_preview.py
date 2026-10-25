#!/usr/bin/env python3
"""0.40.0 참격 궤적 미리 보기 — 동작 (poses.json, tools/motion_preview.py 와 같은 자세) 의 궤적 구간 동안 무기 (tools/weapon_blades.py 의 손잡이 · 무기 끝) 가
지나간 띠를 위 · 3/4 · 앞에서 그리고 1인칭 화면 (시야 70) 에 그림. 빨간 선 = 참격 범위 피해 (rules_more MOTION.area).
자바 BladeTrails.thirdPerson · firstPerson 과 같은 차례 (손목 → 비틀기 → 팔꿈치 → 위팔 · 어깨 → 모델 0.9375 · 뒤집기 → 뿌리).

  python3 tools/blade_trail_preview.py [동작:디자인 ...] [--out 폴더]    # 기본: 평타 동작 · 종류마다 보통 디자인
  python3 tools/blade_trail_preview.py --reach                            # 무기 종류 · 등급마다 궤적이 닿는 거리 (MOTION.area.reach 표)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_preview as mp  # noqa: E402

ROOT = mp.ROOT
RULES = json.load(open(os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json"), encoding="utf-8"))
MODEL_SCALE = 0.9375
EYE = 1.62
GRADES = ("common", "rare", "noble", "hero", "transcend")
COMBO = RULES["MOTION"]["combo"]
TRAIL = RULES.get("TRAIL", {})
_BLADES = None


def blades():
    global _BLADES
    if _BLADES is None:
        import tempfile
        import subprocess
        with tempfile.TemporaryDirectory() as d:
            out = os.path.join(d, "blades.json")
            subprocess.run([sys.executable, os.path.join(ROOT, "tools", "weapon_blades.py"), "--json", out], check=True, stdout=subprocess.DEVNULL)
            _BLADES = json.load(open(out))
    return _BLADES


def design(wtype, grade):
    return f"{wtype}_lv0" if grade == "common" else f"{wtype}_{grade}_lv0"


def hand_point(p, bend, tw, wr, dv, cx=-1.0):
    """MotionRender.hand: 손목 (cx, 9.5, 0) X · Z → 비틀기 (cx, *, 0) Y → 팔꿈치 (cx, 4, 0) X — 팔 공간 픽셀"""
    W, A, J = np.array([cx, 9.5, 0.0]), np.array([cx, 0.0, 0.0]), np.array([cx, 4.0, 0.0])
    q = W + mp.rx(wr) @ mp.rz(dv) @ (p - W)
    q = A + mp.ry(tw) @ (q - A)
    return J + mp.rx(bend) @ (q - J)


def body_point(pose, arm_blocks, slim=False):
    """오른팔 공간 (블록) 의 무기 점 → 우리 공간 (발 원점 · +x 오른쪽 · +y 위 · -z 앞, 블록). slim = 가는 팔 (어깨 높이 2.5 · 손 자리 x +0.5 · 팔 가운데 -0.5)"""
    w = pose["w"]
    R, bend, tw, wr, dv = pose["ra"]
    Ra = mp.mat_of(mp.slerp(mp.quat_of(np.eye(3)), mp.quat_of(R), w))
    lean, twist, side = [math.radians(v) * w for v in pose["body"]]
    base = np.array([-5.0, 2.5 if slim else 2.0, 0.0])
    sh = mp.HIP + mp.zyx(lean, twist, side) @ (np.array([-5.0, 2.0, 0.0]) - mp.HIP)
    sh = base + (sh - base) * w
    if slim:
        sh = sh + np.array([0.5, 0.0, 0.0])
    h = hand_point(np.asarray(arm_blocks, float) * 16, bend * w, tw * w, wr * w, dv * w, cx=-0.5 if slim else -1.0)
    model = sh / 16.0 + Ra @ (h / 16.0)
    o = MODEL_SCALE * (np.diag([-1.0, -1.0, 1.0]) @ (model + np.array([0.0, -1.501, 0.0])))
    o = o - np.array([0.0, mp.FLIP_PIVOT, 0.0])
    o = mp.rx(-math.radians(pose["flip"])) @ o
    o = o + np.array([0.0, mp.FLIP_PIVOT, 0.0])
    o = mp.ry(math.radians(pose["spin"])) @ o
    return o + np.array([0.0, pose["root"][0] * w, -pose["root"][1] * w])


def samples(motion, dsg, n=72, fp=False):
    """궤적 구간들의 (t, 손잡이, 무기 끝, 구간 첫 점)"""
    m = MOTIONS[motion]
    b = blades()[dsg]
    out = []
    for w0, w1 in m.get("trail", []):
        for i in range(n + 1):
            t = w0 + (w1 - w0) * i / n
            if fp:
                p, R = mp.fp_evaluate(m, t)
                g0 = np.array(b["fpGrip"])
                v = np.array(b["fpTip"]) - g0
                G = g0 + p
                out.append((t, G, G + R @ v, i == 0))
            else:
                pose = mp.evaluate(m, t)
                out.append((t, body_point(pose, b["grip"]), body_point(pose, b["tip"]), i == 0))
    return out


with open(mp.POSES, encoding="utf8") as f:
    MOTIONS = json.load(f)["motions"]


def frm(wtype):
    return TRAIL.get("from", {}).get(wtype, TRAIL.get("from", {}).get("default", 0.35))


def area_outline(motion, wtype, grade):
    area = RULES["MOTION"]["area"]
    shp = area["shapes"].get(motion)
    if not shp:
        return None
    reach = None if shp.get("fixed") else area.get("reach", {}).get(wtype)  # 0.42.0 fixed (창 찌르기) 는 reach 표를 쓰지 않음
    rng = reach[GRADES.index(grade)] if reach else shp["range"] * area.get("scale", {}).get(wtype, 1.0)
    eye = np.array([0.0, EYE, 0.0])
    if shp["shape"] == "arc":
        half = math.radians(shp["angle"] / 2)
        return [eye] + [eye + np.array([math.sin(a) * rng, 0, -math.cos(a) * rng]) for a in np.linspace(-half, half, 25)] + [eye]
    if shp["shape"] in ("band", "line"):
        w = shp["width"] / 2
        return [eye + np.array(v) for v in ([-w, 0, 0], [-w, 0, -rng], [w, 0, -rng], [w, 0, 0], [-w, 0, 0])]
    return [np.array([math.sin(a) * rng, EYE, math.cos(a) * rng]) for a in np.linspace(0, 2 * math.pi, 49)]


def draw(s, view, wtype, outline=None, size=360, scale=60.0):
    sx, sy, _ = mp.VIEWS[view]
    img = Image.new("RGBA", (size, size), (34, 44, 70, 255))
    d = ImageDraw.Draw(img, "RGBA")
    cx, cy = size / 2, size * (0.5 if view == "top" else 0.66)

    def P(p):
        return (cx + float(np.dot(p, sx)) * scale, cy - float(np.dot(p, sy)) * scale)

    if view == "top":
        d.ellipse([cx - 0.3 * scale, cy - 0.3 * scale, cx + 0.3 * scale, cy + 0.3 * scale], outline=(200, 200, 200, 255))
        d.line([P(np.zeros(3)), P(np.array([0, 0, -0.6]))], fill=(255, 200, 80, 255), width=3)
    else:
        d.line([P(np.array([-3, 0, 0])), P(np.array([3, 0, 0]))], fill=(90, 90, 100, 255))
        d.rectangle([cx - 0.25 * scale, cy - 1.8 * scale, cx + 0.25 * scale, cy], outline=(200, 200, 200, 255))
    if outline:
        d.line([P(p) for p in outline], fill=(255, 90, 90, 255), width=2)
    f = frm(wtype)
    g = TRAIL.get("glowTo", 1.12)
    for i in range(len(s) - 1):
        _, g0, t0, _ = s[i]
        _, g1, t1, st = s[i + 1]
        if st:
            continue
        k = 1 - (i + 0.5) / len(s)
        for out_f, in_f, col in ((g, f * 0.85, (120, 190, 255)), (1.0, f, (255, 255, 255))):
            a = int(200 * (1 - k) ** 0.7) + 30
            q = [P(g0 + (t0 - g0) * out_f), P(g1 + (t1 - g1) * out_f), P(g1 + (t1 - g1) * in_f), P(g0 + (t0 - g0) * in_f)]
            d.polygon(q, fill=col + (a,))
    return img


def fp_draw(s, wtype, W=480, H=270):
    img = Image.new("RGBA", (W, H), (60, 90, 60, 255))
    d = ImageDraw.Draw(img, "RGBA")
    f = frm(wtype)
    for i in range(len(s) - 1):
        _, g0, t0, _ = s[i]
        _, g1, t1, st = s[i + 1]
        if st:
            continue
        pr = [mp.fp_project(p, W, H) for p in (t0, t1, g1 + (t1 - g1) * f, g0 + (t0 - g0) * f)]
        if any(p is None for p in pr):
            continue
        k = 1 - (i + 0.5) / len(s)
        d.polygon(pr, fill=(255, 255, 255, int(200 * (1 - k) ** 0.7) + 30))
    d.line([(W / 2 - 5, H / 2), (W / 2 + 5, H / 2)], fill=(255, 255, 255, 255))
    d.line([(W / 2, H / 2 - 5), (W / 2, H / 2 + 5)], fill=(255, 255, 255, 255))
    return img


def reach(motion, dsg, wtype):
    """궤적 (무기 색 빛의 바깥 가장자리 — glowTo) 이 눈에서 수평으로 닿는 가장 먼 거리"""
    g = TRAIL.get("glowTo", 1.12)
    eye = np.array([0.0, EYE, 0.0])
    best = 0.0
    for _, gp, tp, _ in samples(motion, dsg, n=60):
        v = gp + (tp - gp) * g - eye
        best = max(best, math.hypot(v[0], v[2]))
    return best


def main(argv):
    out = os.path.join(ROOT, "build", "blade_trails")
    if "--reach" in argv:
        table = {}
        for wtype, combo in COMBO.items():
            ms = [m for m in combo if MOTIONS[m].get("trail")]
            if not ms:
                continue
            row = []
            for g in GRADES:
                dsg = design(wtype, g)
                if dsg not in blades():
                    dsg = next(d for d in blades() if d.startswith(wtype + "_") and "skin" not in d)
                row.append(round(max(reach(m, dsg, wtype) for m in ms), 2))
            table[wtype] = row
            print(f"{wtype:11s} {row}  ({', '.join(ms)})")
        print(json.dumps(table))
        return 0
    jobs = []
    i = 0
    while i < len(argv):
        if argv[i] == "--out":
            out = argv[i + 1]
            i += 2
            continue
        jobs.append(argv[i].split(":"))
        i += 1
    if not jobs:
        for wtype, combo in COMBO.items():
            for m in combo:
                if MOTIONS[m].get("trail"):
                    jobs.append([m, design(wtype, "common")])
    os.makedirs(out, exist_ok=True)
    for motion, dsg in jobs:
        wtype = dsg.split("_")[0]
        grade = next((g for g in GRADES if f"_{g}_" in dsg), "common")
        s = samples(motion, dsg)
        f = samples(motion, dsg, fp=True)
        row = Image.new("RGBA", (360 * 3 + 480, 360), (20, 20, 26, 255))
        row.paste(draw(s, "top", wtype, area_outline(motion, wtype, grade)), (0, 0))
        row.paste(draw(s, "34", wtype), (360, 0))
        row.paste(draw(s, "front", wtype), (720, 0))
        row.paste(fp_draw(f, wtype), (1080, 45))
        ImageDraw.Draw(row).text((4, 4), f"{motion} · {dsg} · reach {reach(motion, dsg, wtype):.2f}", fill=(255, 255, 255))
        p = os.path.join(out, f"{motion}_{dsg}.png")
        row.save(p)
        print(p)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
