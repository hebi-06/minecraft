#!/usr/bin/env python3
"""1인칭 모션 시범 (2026-11-18 축복의 길 — 사용자 "1인칭 공격 · 스킬 모션", 모드 없이): 모드 클라이언트의 1인칭 동작
(assets/mmorpg/motion/poses.json 의 "fp" 열쇠 — MotionRender.firstPerson) 을 무기 디자인마다 프레임 모델로 구움 (손으로 고치지 않음).

  assets/rpg/models/item/fp/<디자인>/<동작>_<k>.json = {"parent": "rpg:item/model/<디자인>", "display": {"firstperson_righthand": …}}

아이템 정의 (VanillaPack.fpWrap) 가 1인칭 오른손에서 custom_model_data 글자 1번 (동작 이름) → minecraft:range_dispatch
(minecraft:cooldown) 로 프레임을 고르고, 서버 (VanillaFirstPerson) 가 동작마다 그 사람에게만 쿨타임 패킷을 보내 재생함.
쿨타임 몫은 틱마다 바뀌므로 (부분 틱 없음) 프레임 수 = 동작 틱 수, 프레임 k 는 동작 시간 (k + 0.5) / 틱 수의 자세.

모드 클라이언트와 같은 계산 (MotionRender.firstPerson · MotionPoses.fpAt · fpRotation):
  1인칭 꼭짓점 = ARM + fp·w + g + q_w · (t − g + R · s · (p / 16 − 0.5))
    ARM = 바닐라 손 자리 (0.56, −0.52, −0.72), t · R · s = 무기 모델의 1인칭 display, g = 손잡이 (손 자리 기준 — WeaponBlades.fpGrip − ARM),
    fp = 열쇠 사이 자리 (끝 열쇠의 ease), q = 끝 · 날 방향 (blade · edge) 으로 돌리는 회전 (열쇠 사이 slerp), w = 동작 앞뒤 무게 (fade)
  → 프레임 display: R' = q_w · R,  t' = fp·w + g + q_w · (t − g)

  python3 tools/fp_frames.py                     # 프레임 모델 (디자인마다 motions_of) · fp_map.json 을 씀
  FP_DESIGNS=a,b python3 tools/fp_frames.py --preview DIR   # 그 디자인만 미리 보기
  python3 tools/fp_frames.py --preview DIR       # 동작마다 1인칭 미리 보기 (시야 70 · 16:9 · 프레임을 한 장에)
"""
import json
import math
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_models as wm  # noqa: E402

ROOT = wm.ROOT
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg", "models", "item")
POSES = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "motion", "poses.json")
BLADES = os.path.join(ROOT, "src", "client", "java", "mmorpg", "fabric", "client", "motion", "WeaponBlades.java")

# 2026-11-18 무기 종류 모두 (시범 칼 → 디자인 62): 디자인 이름 앞 = 무기 종류 (staff 디자인 = 완드)
TYPE_OF_PREFIX = {"staff": "wand"}
RULES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules_more.json")
MAP_OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "motion", "fp_map.json")
# MotionRender.grip (모드 1인칭 손잡이 — 화면 공간 칸 · 오른손): 칼 · 도끼 · 마검 · 창 · 낫 · 망치 · 홀 · 대검은 GRIP
GRIPS = {
    "default": np.array([0.266, -0.099, -0.655]),
    "dagger": np.array([0.143, -0.002, -0.405]),
    "shield": np.array([0.331, 0.019, -0.468]),
    "wand": np.array([0.491, -0.282, -0.780]),
    "bow": np.array([0.612, 0.204, -1.155]),
}
# 디자인마다 굽는 동작 = 그 종류의 바탕 (평타 연타 · 활 쏘기 · 완드 시전) + 스킬 대표 동작 (직업 · 룬 스킬 동작은 가장 닮은 대표로 — fp_map.json)
MELEE_SKILLS = ["skill_slash", "slam", "thrust", "cast", "dash", "spin"]
TYPE_MOTIONS = {
    "shield": ["bash", "slam", "thrust", "cast", "guard", "dash"],
    "bow": ["shoot", "shoot_up", "cast", "dash"],
    "wand": ["cast", "cast_up", "thrust", "slam", "dash"],
}
# 프레임 하나가 맡는 틱 (1 = 틱마다 — 초당 20장). 메모리 예산 (구운 면 = 디자인 면 수 × 프레임 수) 을 보고 정함
FRAME_STEP = 1

# MotionPoses.FP_REST_BLADE · FP_REST_EDGE (1인칭에서 세워 든 무기의 끝 · 날 방향 — 화면 공간 +x 오른쪽 · +y 위 · -z 앞)
REST_BLADE = np.array([-0.10004, 0.97034, -0.22008])
REST_EDGE = np.array([-0.35775, -0.24147, -0.90206])
# MotionPoses: fade 기본값 (동작 앞 · 뒤 무게)
FADE = (0.08, 0.2)


def type_of(design):
    p = design.split("_")[0]
    return TYPE_OF_PREFIX.get(p, p)


def combo_of(typ):
    rules = json.load(open(RULES, encoding="utf-8"))
    return list(rules["MOTION"]["combo"].get(typ, []))


def motions_of(typ):
    """디자인에 굽는 동작 (처음 = 바탕 — 이름이 없을 때 · 평타)"""
    if typ in TYPE_MOTIONS:
        return list(TYPE_MOTIONS[typ])
    return list(dict.fromkeys(combo_of(typ) + MELEE_SKILLS))


def designs():
    out = []
    for f in sorted(os.listdir(os.path.join(MODELS, "model"))):
        if f.endswith(".json"):
            d = f[:-5]
            if type_of(d) in GRIPS or type_of(d) in ("sword", "greatsword", "scythe", "axe", "hammer", "lance", "scepter"):
                out.append(d)
    return out


_POSES = None


def poses():
    """poses.json (한 번만 읽음)"""
    global _POSES
    if _POSES is None:
        _POSES = json.load(open(POSES, encoding="utf-8"))
    return _POSES


_MOTION = {}


def ease(u, kind):
    """MotionPoses.ease"""
    u = max(0.0, min(1.0, u))
    if kind == "linear":
        return u
    if kind == "in":
        return u * u
    if kind == "out":
        return 1 - (1 - u) * (1 - u)
    if kind == "out3":
        return 1 - (1 - u) ** 3
    return u * u * (3 - 2 * u)


def quat(R):
    """회전 행렬 → 사원수 (w, x, y, z)"""
    t = np.trace(R)
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        return np.array([0.25 * s, (R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s])
    i = int(np.argmax([R[0, 0], R[1, 1], R[2, 2]]))
    if i == 0:
        s = math.sqrt(1.0 + R[0, 0] - R[1, 1] - R[2, 2]) * 2
        return np.array([(R[2, 1] - R[1, 2]) / s, 0.25 * s, (R[0, 1] + R[1, 0]) / s, (R[0, 2] + R[2, 0]) / s])
    if i == 1:
        s = math.sqrt(1.0 + R[1, 1] - R[0, 0] - R[2, 2]) * 2
        return np.array([(R[0, 2] - R[2, 0]) / s, (R[0, 1] + R[1, 0]) / s, 0.25 * s, (R[1, 2] + R[2, 1]) / s])
    s = math.sqrt(1.0 + R[2, 2] - R[0, 0] - R[1, 1]) * 2
    return np.array([(R[1, 0] - R[0, 1]) / s, (R[0, 2] + R[2, 0]) / s, (R[1, 2] + R[2, 1]) / s, 0.25 * s])


def mat(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def slerp(a, b, u):
    """가까운 쪽 (JOML Quaternionf.slerp 와 같음)"""
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        q = a + u * (b - a)
        return q / np.linalg.norm(q)
    th = math.acos(d)
    return (math.sin((1 - u) * th) * a + math.sin(u * th) * b) / math.sin(th)


IDENT = np.array([1.0, 0.0, 0.0, 0.0])


def fp_rotation(blade, edge):
    """MotionPoses.fpRotation: 1인칭 자세의 무기 (끝 REST_BLADE · 날 REST_EDGE) 를 끝 blade · 날 edge 쪽으로 ([r 오른쪽, u 위, f 앞])"""
    b = np.array([blade[0], blade[1], -blade[2]], float)
    if np.linalg.norm(b) < 1e-4:
        return np.eye(3)
    b /= np.linalg.norm(b)
    e = np.array([0.0, 0.0, -1.0]) if edge is None else np.array([edge[0], edge[1], -edge[2]], float)
    e = e - e.dot(b) * b
    if np.linalg.norm(e) < 1e-3:
        e = np.array([0.0, 1.0, 0.0]) - b[1] * b
        if np.linalg.norm(e) < 1e-3:
            e = np.array([1.0, 0.0, 0.0]) - b[0] * b
    e /= np.linalg.norm(e)
    b0 = REST_BLADE / np.linalg.norm(REST_BLADE)
    e0 = REST_EDGE - REST_EDGE.dot(b0) * b0
    e0 /= np.linalg.norm(e0)
    target = np.column_stack([b, e, np.cross(b, e)])
    rest = np.column_stack([b0, e0, np.cross(b0, e0)])
    return target @ rest.T


def motion(name):
    """poses.json 의 동작 → (틱 수, fade, 열쇠 [(t, ease, p, q)]) — 한 번 계산"""
    if name not in _MOTION:
        _MOTION[name] = _motion(name)
    return _MOTION[name]


def _motion(name):
    data = poses()
    m = (data.get("motions") or data)[name]
    keys = []
    for k in m.get("fp", []):
        p = np.array(k.get("p", [0.0, 0.0, 0.0]), float)
        if "blade" in k:
            q = quat(fp_rotation(k["blade"], k.get("edge")))
        elif "r" in k:
            r = [math.radians(v) for v in k["r"]]
            q = quat(wm.ry(r[1]) @ wm.rx(r[0]) @ wm.rz(r[2]))
        else:
            q = IDENT.copy()
        keys.append((float(k["t"]), k.get("ease", "inout"), p, q))
    fade = m.get("fade", list(FADE))
    return int(m.get("ticks", 10)), (float(fade[0]), float(fade[1])), keys


def pose_at(keys, fade, t):
    """MotionPoses.fpAt · 무게 → (fp·w, q_w)"""
    if not keys:
        return np.zeros(3), np.eye(3)
    a, b = keys[0], keys[-1]
    for k0, k1 in zip(keys, keys[1:]):
        if k0[0] <= t <= k1[0]:
            a, b = k0, k1
            break
    sp = b[0] - a[0]
    v = ease(0.0 if sp <= 0 else (t - a[0]) / sp, b[1])
    p = a[2] + (b[2] - a[2]) * v
    q = slerp(a[3], b[3], v)
    w = 1.0
    if fade[0] > 0 and t < fade[0]:
        w = ease(t / fade[0], "inout")
    if fade[1] > 0 and t > 1 - fade[1]:
        w = min(w, ease((1 - t) / fade[1], "inout"))
    return p * w, mat(slerp(IDENT, q, w))


def grip(typ):
    """1인칭 손잡이 (손 자리 기준 — MotionRender.grip: 무기 모델의 1인칭 display 가 손잡이를 손 자리에서 이만큼 떨어뜨려 둠)"""
    return GRIPS.get(typ, GRIPS["default"])


def rot_for(typ, qw):
    """종류마다 1인칭 회전 (MotionRender.firstPerson): 방패는 옮기기만 (겉면이 앞을 봄) · 활은 비틀기 없이 끝 방향만 (가장 작은 회전)"""
    if typ == "shield":
        return np.eye(3)
    if typ == "bow":
        b0 = REST_BLADE / np.linalg.norm(REST_BLADE)
        b1 = qw @ b0
        v = np.cross(b0, b1)
        c = float(b0.dot(b1))
        if np.linalg.norm(v) < 1e-6:
            return np.eye(3) if c > 0 else qw
        vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
        return np.eye(3) + vx + vx @ vx * (1 / (1 + c))
    return qw


def hidden(name, t):
    """poses.json hide (무기를 던진 동안 — 손에 없음): [시작, 끝] 또는 그 목록"""
    data = poses()
    h = (data.get("motions") or data)[name].get("hide")
    if not h:
        return False
    spans = h if isinstance(h[0], list) else [h]
    return any(a <= t <= b for a, b in spans)


def base_display(design):
    """무기 모델의 1인칭 오른손 display (쉴 때 자세) → (R, s, t 칸) · 모델"""
    m = json.load(open(os.path.join(MODELS, "model", design + ".json"), encoding="utf-8"))
    d = m["display"]["firstperson_righthand"]
    a, b, c = (math.radians(x) for x in d["rotation"])
    R = wm.rx(a) @ wm.ry(b) @ wm.rz(c)
    return R, float(d["scale"][0]), np.array(d["translation"]) / 16, m


def frame_count(name):
    n = motion(name)[0]
    return max(2, math.ceil(n / FRAME_STEP))


def frames(design, name):
    """동작의 프레임 (FRAME_STEP 틱마다 한 장): [(R', s, t') 또는 None (숨김)] — k = 0 이 쿨타임 처음"""
    typ = type_of(design)
    R, s, t, _ = base_display(design)
    g = grip(typ)
    n, fade, keys = motion(name)
    m = frame_count(name)
    out = []
    for k in range(m):
        u = (k + 0.5) / m
        if hidden(name, u):
            out.append(None)
            continue
        fp, qw = pose_at(keys, fade, u)
        q = rot_for(typ, qw)
        out.append((q @ R, s, fp + g + q @ (t - g)))
    return out


def write():
    n = 0
    ds = designs()
    per_type = {}
    for design in ds:
        typ = type_of(design)
        d = os.path.join(MODELS, "fp", design)
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            if f.endswith(".json"):
                os.remove(os.path.join(d, f))
        names = motions_of(typ)
        per_type.setdefault(typ, names)
        for name in names:
            for k, fr in enumerate(frames(design, name)):
                if fr is None:
                    # 던진 동안: 빈 모델 (면 없음 — 그리지 않음)
                    model = {"credit": "MMO RPG — 1인칭 모션 (tools/fp_frames.py — 무기를 던진 동안, 빈 모델)", "elements": []}
                else:
                    R2, s, t2 = fr
                    model = {
                        "credit": "MMO RPG — 1인칭 모션 (tools/fp_frames.py 가 poses.json 의 fp 열쇠로 만듦, 손으로 고치지 않음)",
                        "parent": f"rpg:item/model/{design}",
                        "display": {"firstperson_righthand": wm.display(R2, s, t2)},
                    }
                with open(os.path.join(d, f"{name}_{k}.json"), "w", encoding="utf-8") as f:
                    json.dump(model, f, ensure_ascii=False, separators=(",", ":"))
                    f.write("\n")
                n += 1
    for f in os.listdir(os.path.join(MODELS, "fp")):
        if f not in ds and os.path.isdir(os.path.join(MODELS, "fp", f)):
            import shutil
            shutil.rmtree(os.path.join(MODELS, "fp", f))
    write_map(per_type)
    print(f"frames {n} ({len(ds)} designs, step {FRAME_STEP})")


# ------------------------------------------------------------ 스킬 동작 → 대표 동작 (fp_map.json)

def traj(name, N=24):
    """1인칭 끝 방향 · 자리 (무게를 곱함) — 시간 N 칸"""
    n, fade, keys = motion(name)
    b0 = REST_BLADE / np.linalg.norm(REST_BLADE)
    out = []
    for i in range(N):
        p, qw = pose_at(keys, fade, (i + 0.5) / N)
        out.append((qw @ b0, p))
    return out


def dtw(a, b, angle=1.0, lam=1.2, band=5):
    """끝 방향 각 + lam × 자리 차의 DTW (시간을 band 칸까지 늘이고 줄임) — 칸당 평균"""
    n = len(a)
    D = np.full((n + 1, n + 1), 1e9)
    D[0, 0] = 0
    for i in range(1, n + 1):
        for j in range(max(1, i - band), min(n, i + band) + 1):
            ba, pa = a[i - 1]
            bb, pb = b[j - 1]
            c = angle * math.acos(max(-1.0, min(1.0, float(ba.dot(bb))))) + lam * float(np.linalg.norm(pa - pb))
            D[i, j] = c + min(D[i - 1, j], D[i, j - 1], D[i - 1, j - 1])
    return D[n, n] / n


def write_map(per_type):
    """종류마다 모든 동작 → 그 종류에 구운 동작 가운데 1인칭 움직임이 가장 닮은 것 (VanillaFirstPerson — 구운 동작이 아니면 이 표)"""
    data = poses()
    names = sorted((data.get("motions") or data).keys())
    T = {m: traj(m) for m in names}
    out = {"_comment": "2026-11-18 1인칭 모션 (tools/fp_frames.py 가 만듦 — 손으로 고치지 않음): 무기 종류마다 동작 → 그 종류에 구운 대표 동작 "
                       "(1인칭 끝 방향 · 자리의 DTW 가 가장 가까운 것). base = 이름이 없을 때 · 평타 프레임, ticks = 구운 동작의 틱 (프레임 수는 FRAME_STEP 에 따라)",
           "base": {}, "baked": {}, "ticks": {}, "types": {}}
    for typ, baked in sorted(per_type.items()):
        out["base"][typ] = baked[0]
        out["baked"][typ] = list(baked)
        for c in baked:
            out["ticks"][c] = motion(c)[0]
        shield = typ == "shield"
        m = {}
        for name in names:
            if name in baked:
                continue
            best = min(baked, key=lambda c: dtw(T[name], T[c], angle=0.0 if shield else 1.0))
            m[name] = best
        out["types"][typ] = m
    with open(MAP_OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print("map", MAP_OUT, {t: len(v) for t, v in out["types"].items()})


# ------------------------------------------------------------ 미리 보기

def element_quads(m):
    """모델 상자 → 면 (꼭짓점 넷, 모델 단위) · 상자 번호"""
    quads = []
    for i, e in enumerate(m.get("elements", [])):
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        cs = {(x, y, z): np.array([[f, t][x][0], [f, t][y][1], [f, t][z][2]]) for x in (0, 1) for y in (0, 1) for z in (0, 1)}
        r = e.get("rotation")
        if r and r.get("angle"):
            o = np.array(r["origin"], float)
            M = {"x": wm.rx, "y": wm.ry, "z": wm.rz}[r["axis"]](math.radians(r["angle"]))
            cs = {k: M @ (v - o) + o for k, v in cs.items()}
        faces = [((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)), ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
                 ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
                 ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)), ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))]
        for fc in faces:
            quads.append(([cs[k] for k in fc], i))
    return quads


def render(m, R, s, t, W=320, H=180, ghost=None):
    from PIL import Image, ImageDraw
    img = Image.new("RGB", (W, H), (34, 38, 46))
    d = ImageDraw.Draw(img)
    f = 1 / math.tan(math.radians(70) / 2)
    asp = W / H
    quads = element_quads(m)
    n = max(1, len(m.get("elements", [])))

    def proj(v):
        x, y, z = v
        z = min(z, -0.05)
        return (W / 2 + (x / -z) * f / asp * W / 2, H / 2 - (y / -z) * f * H / 2)

    polys = []
    for pose, dim in ([(ghost, True)] if ghost is not None else []) + [((R, s, t), False)]:
        R_, s_, t_ = pose
        for q, i in quads:
            cam = [wm.JAVA_FP_ARM + t_ + R_ @ (s_ * (p / 16 - 0.5)) for p in q]
            nrm = np.cross(cam[1] - cam[0], cam[2] - cam[0])
            shade = 0.55 + 0.45 * abs(nrm[2] / (np.linalg.norm(nrm) + 1e-9))
            base = np.array([150, 170, 205]) * (0.6 + 0.4 * i / n)
            col = tuple(int(min(255, c * shade)) for c in base)
            if dim:
                col = tuple(int(c * 0.35 + 34 * 0.65) for c in col)
            polys.append((float(np.mean([c[2] for c in cam])) - (10 if dim else 0), [proj(c) for c in cam], col))
    for _, pts, col in sorted(polys, key=lambda p: p[0]):
        d.polygon(pts, fill=col)
    return img


def preview(out):
    from PIL import Image, ImageDraw
    os.makedirs(out, exist_ok=True)
    only = os.environ.get("FP_DESIGNS")
    for design in (only.split(",") if only else designs()):
        R0, s0, t0, m = base_display(design)
        for name in motions_of(type_of(design)):
            fs = frames(design, name)
            cols = 5
            W, H = 320, 180
            sheet = Image.new("RGB", (cols * W, math.ceil(len(fs) / cols) * H), (20, 20, 24))
            for k, fr in enumerate(fs):
                if fr is None:
                    im = render({"elements": []}, R0, s0, t0, W, H, ghost=(R0, s0, t0))
                else:
                    R2, s, t2 = fr
                    im = render(m, R2, s, t2, W, H, ghost=(R0, s0, t0))
                ImageDraw.Draw(im).text((6, 4), f"{name} {k}", fill=(255, 255, 255))
                sheet.paste(im, ((k % cols) * W, (k // cols) * H))
            p = os.path.join(out, f"fp_{design}_{name}.png")
            sheet.save(p)
            print(p)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        write()
