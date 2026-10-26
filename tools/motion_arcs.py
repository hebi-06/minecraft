#!/usr/bin/env python3
"""0.40.0 베는 길 (참격 궤적): poses.json 의 베는 구간 (열쇠 → 다음 열쇠) 을 잘게 나눈 열쇠로 채움.

열쇠 사이는 위팔 회전 (구면 보간) · 팔꿈치 · 팔뚝 비틀기 · 손목 (각 보간) 을 따로 보간해서, 큰 휘두르기 (120° 넘게) 는 무기 끝이
가장 짧은 쪽으로 돌거나 (가로 베기가 몸 뒤로 · 내려치기가 왼쪽으로) 땅까지 내려갔다 올라옴 — 3인칭 참격 궤적 (칼이 지나간 길) 에 그대로 보임.
그래서 베는 구간마다 무기 끝이 지나갈 방향 (via) 을 정해, 앞 열쇠 → via → 이 열쇠 를 지나는 원 (방향 구면 위) 을 따라 STEP 도마다 열쇠를 넣음
(via 가 없으면 큰 원 — 가장 짧은 쪽). 팔 방향 (dir) 은 가장 짧은 쪽, 팔꿈치 · 몸통 · 다리 · 뿌리는 그 구간의 빠르기 (ease) 대로.
넣은 열쇠는 "gen": true (다시 돌리면 지우고 새로), 이 구간의 마지막 열쇠는 빠르기를 linear 로 바꾸고 원래 빠르기는 "genEase" 에 둠.

  python3 tools/motion_arcs.py          # poses.json 을 고침 (같은 모양으로 다시 씀)
  python3 tools/motion_arcs.py --check  # 고칠 것이 없는지만 (CI · 시험)
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")
STEP = 18.0  # 넣는 열쇠 사이 무기 끝 회전 (도)

# 동작 → [(구간 끝 열쇠의 t, 팔, 무기 끝이 지나갈 방향 [r, u, f] 또는 None)] — 구간 = 그 앞 열쇠 → 이 열쇠
ARCS = {
    # 가로 베기: 오른쪽 뒤 → 오른쪽 · 앞 → 왼쪽 앞 (몸 앞을 지나 크게) → 왼쪽 뒤
    "slash_h": [(0.5, "ra", [0.95, 0.03, 0.3]), (0.66, "ra", None)],
    # 0.42.0 창 가로 휘두르기: 오른쪽 뒤 (낮게) → 오른쪽 앞 → 왼쪽 앞 → 왼쪽 뒤 — 가슴 높이로 평평하게 (가로 베기보다 낮고 넓게)
    "lance_sweep": [(0.5, "ra", [0.95, -0.05, 0.3]), (0.66, "ra", None)],
    # 세로 베기 · 내려찍기 · 내려치기: 머리 뒤 → 머리 위 → 앞 아래 (왼쪽으로 돌지 않게)
    "slash_v": [(0.52, "ra", [0.05, 1.0, 0.12]), (0.7, "ra", None)],
    "chop": [(0.56, "ra", [0.05, 1.0, 0.12]), (0.76, "ra", None)],
    "smite": [(0.55, "ra", [0.05, 1.0, 0.12]), (0.75, "ra", None)],
    # 회전 베기: 뒤 → 오른쪽 → 앞 (몸이 돌기 시작), 마지막 왼쪽 앞으로
    "spin": [(0.28, "ra", [1.0, 0.0, 0.0]), (0.8, "ra", None)],
    "spin2": [(0.24, "ra", [1.0, 0.0, 0.0]), (0.86, "ra", None)],
    # 단검 X자: 오른쪽 위 → 왼쪽 아래 · 왼쪽 위 → 오른쪽 아래 (앞을 지나 — 큰 원)
    "dagger_x": [(0.36, "ra", None), (0.72, "ra", None)],
    # 검기 베기: 가로 베기처럼 크게
    "skill_slash": [(0.48, "ra", [0.95, 0.05, 0.3]), (0.66, "ra", None)],
    # 연속 베기: 가로 (오른쪽 → 왼쪽) · 세로 (머리 위로) · 되돌려 베기 (왼쪽 → 오른쪽)
    "skill_flurry": [(0.26, "ra", [0.95, 0.05, 0.3]), (0.54, "ra", [0.05, 1.0, 0.12]), (0.82, "ra", [-0.95, 0.05, 0.3])],
    # 내려찍기 (스킬) · 도약 내려찍기
    "slam": [(0.58, "ra", [0.05, 1.0, 0.12]), (0.8, "ra", None)],
    "leap_slam": [(0.62, "ra", [0.05, 1.0, 0.12]), (0.82, "ra", None)],
    # 0.44.0 대장장이 망치질 (세 번): 머리 위로 세운 망치 → 앞으로 내려 모루 위 (가장 짧은 쪽 — 몸 앞으로. 모루 윗면 높이 1칸 · 앞 1.15칸)
    "smith_hammer": [(0.27, "ra", None), (0.57, "ra", None), (0.87, "ra", None)],
}

# ------------------------------------------------------------ 벡터


def norm(v):
    n = math.sqrt(sum(x * x for x in v))
    return [x / n for x in v] if n > 1e-12 else list(v)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def add(a, b, k=1.0):
    return [x + k * y for x, y in zip(a, b)]


def scale(a, k):
    return [x * k for x in a]


def slerp(a, b, s):
    a, b = norm(a), norm(b)
    d = max(-1.0, min(1.0, dot(a, b)))
    th = math.acos(d)
    if th < 1e-6:
        return a
    if math.pi - th < 1e-4:  # 정반대: 아무 수직 쪽으로
        p = norm(cross(a, [0, 1, 0]) if abs(a[1]) < 0.9 else cross(a, [1, 0, 0]))
        return norm(add(scale(a, math.cos(math.pi * s)), p, math.sin(math.pi * s)))
    return norm(add(scale(a, math.sin((1 - s) * th) / math.sin(th)), b, math.sin(s * th) / math.sin(th)))


def angle(a, b):
    return math.degrees(math.acos(max(-1.0, min(1.0, dot(norm(a), norm(b))))))


class Arc:
    """방향 구면 위에서 a → via → b 를 지나는 원 (via 가 없으면 큰 원). at(s): s = 0 → a, 1 → b"""

    def __init__(self, a, via, b):
        self.a, self.b = norm(a), norm(b)
        self.via = norm(via) if via is not None else None
        self.circle = None
        if self.via is not None:
            n = cross(add(self.via, self.a, -1), add(self.b, self.a, -1))
            if math.sqrt(dot(n, n)) > 1e-6:
                n = norm(n)
                c = scale(n, dot(n, self.a))
                e1 = add(self.a, c, -1)
                r = math.sqrt(dot(e1, e1))
                e1 = scale(e1, 1 / r)
                e2 = cross(n, e1)

                def ang(p):
                    q = add(p, c, -1)
                    return math.atan2(dot(q, e2), dot(q, e1)) % (2 * math.pi)

                pv, pb = ang(self.via), ang(self.b)
                if pv > pb:  # via 가 b 다음이면 반대로 돎
                    e2 = scale(e2, -1)
                    pv, pb = ang(self.via), ang(self.b)
                assert pv <= pb + 1e-9, (a, via, b)
                self.circle = (c, e1, e2, r, pb)

    def at(self, s):
        if self.circle is None:
            return slerp(self.a, self.b, s)
        c, e1, e2, r, pb = self.circle
        ph = pb * s
        return norm(add(c, add(scale(e1, r * math.cos(ph)), e2, r * math.sin(ph))))

    def sweep(self):
        """무기 끝이 도는 각 (도 — 대략)"""
        n = 32
        return sum(angle(self.at(i / n), self.at((i + 1) / n)) for i in range(n))


# ------------------------------------------------------------ 열쇠


def ease_inv(u, kind):
    """ease (MotionPoses.ease) 의 거꾸로: 진행 u → 시간 몫"""
    u = max(0.0, min(1.0, u))
    if kind == "linear":
        return u
    if kind == "in":
        return math.sqrt(u)
    if kind == "out":
        return 1 - math.sqrt(1 - u)
    if kind == "out3":
        return 1 - (1 - u) ** (1 / 3)
    lo, hi = 0.0, 1.0  # inout: u = x²(3 - 2x)
    for _ in range(60):
        mid = (lo + hi) / 2
        if mid * mid * (3 - 2 * mid) < u:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def lerp(a, b, s):
    return a + (b - a) * s


def lerp_list(a, b, s, n):
    a = a if a is not None else [0] * n
    b = b if b is not None else [0] * n
    return [round(lerp(x, y, s), 4) for x, y in zip(a, b)]


NEUTRAL = {"dir": [0, -1, 0], "blade": [0, 0, 1], "bend": 0}


def arm_at(a0, a1, s, arc=None):
    a0 = a0 or NEUTRAL
    a1 = a1 or NEUTRAL
    out = {"dir": [round(x, 4) for x in slerp(a0.get("dir", [0, -1, 0]), a1.get("dir", [0, -1, 0]), s)]}
    if arc is not None:
        out["blade"] = [round(x, 4) for x in arc.at(s)]
    elif "blade" in a0 or "blade" in a1:
        out["blade"] = [round(x, 4) for x in slerp(a0.get("blade", [0, 0, 1]), a1.get("blade", [0, 0, 1]), s)]
    out["bend"] = round(lerp(a0.get("bend", 0), a1.get("bend", 0), s), 3)
    if "face" in a0 or "face" in a1:
        out["face"] = [round(x, 4) for x in slerp(a0.get("face", [0, 0, 1]), a1.get("face", [0, 0, 1]), s)]
    if "wrist" in a0 or "wrist" in a1:
        out["wrist"] = round(lerp(a0.get("wrist", 0), a1.get("wrist", 0), s), 3)
    return out


def sub_key(k0, k1, s, t, arm, arc):
    k = {"t": round(t, 4), "ease": "linear", "gen": True}
    for f, n in (("body", 3), ("rl", 3), ("ll", 3), ("head", 2), ("root", 2)):
        if f in k0 or f in k1:
            k[f] = lerp_list(k0.get(f), k1.get(f), s, n)
    for side in ("ra", "la"):
        if side in k0 or side in k1:
            k[side] = arm_at(k0.get(side), k1.get(side), s, arc if side == arm else None)
    for f in ("rk", "lk", "spin", "flip"):
        if f in k0 or f in k1:
            k[f] = round(lerp(k0.get(f, 0), k1.get(f, 0), s), 3)
    return k


def densify(name, m):
    keys = [k for k in m["keys"] if not k.get("gen")]
    for k in keys:
        if "genEase" in k:
            if k["genEase"] == "inout":
                k.pop("ease", None)
            else:
                k["ease"] = k["genEase"]
            del k["genEase"]
    out = []
    # 0.46.0 룬 동작 (tools/rune_motions.py) 은 베는 길을 동작 안의 "arcs" 에 적음 ([[t, 팔, via], …])
    specs = {round(t, 4): (arm, via) for t, arm, via in ARCS.get(name, m.get("arcs", []))}
    for i, k in enumerate(keys):
        spec = specs.get(round(k["t"], 4))
        if spec is not None and i > 0:
            k0 = keys[i - 1]
            arm, via = spec
            a0 = (k0.get(arm) or NEUTRAL).get("blade", [0, 0, 1])
            a1 = (k.get(arm) or NEUTRAL).get("blade", [0, 0, 1])
            arc = Arc(a0, via, a1)
            n = max(1, math.ceil(arc.sweep() / STEP))
            kind = k.get("ease", "inout")
            for j in range(1, n):
                s = j / n
                t = k0["t"] + (k["t"] - k0["t"]) * ease_inv(s, kind)
                out.append(sub_key(k0, k, s, t, arm, arc))
            if n > 1:
                k["genEase"] = kind
                k["ease"] = "linear"
        out.append(k)
    m["keys"] = out
    return m


# ------------------------------------------------------------ 쓰기 (poses.json 과 같은 모양)


# 줄마다 열쇠 하나로 쓰는 목록 (차례대로 — 0.46.0 fpo: 1인칭 왼손)
LISTS = ("keys", "fp", "fpo")


def fmt(data):
    out = ["{", '  "_comment": ' + json.dumps(data["_comment"], ensure_ascii=False) + ",", '  "motions": {']
    names = list(data["motions"].keys())
    for ni, name in enumerate(names):
        m = data["motions"][name]
        out.append("    " + json.dumps(name) + ": {")
        head = {k: v for k, v in m.items() if k not in LISTS}
        out.append("      " + ", ".join(json.dumps(k) + ": " + json.dumps(v, ensure_ascii=False) for k, v in head.items()) + ",")
        parts = [p for p in LISTS if p in m]
        for pi, part in enumerate(parts):
            out.append("      " + json.dumps(part) + ": [")
            for i, k in enumerate(m[part]):
                out.append("        " + json.dumps(k, ensure_ascii=False) + ("," if i < len(m[part]) - 1 else ""))
            out.append("      ]" + ("," if pi < len(parts) - 1 else ""))
        out.append("    }" + ("," if ni < len(names) - 1 else ""))
    out += ["  }", "}"]
    return "\n".join(out) + "\n"


def main(argv):
    src = open(POSES, encoding="utf-8").read()
    data = json.loads(src)
    assert fmt(data) == src, "poses.json 모양이 다름 (fmt 를 맞출 것)"
    for name, m in data["motions"].items():
        if not name.startswith("_"):
            densify(name, m)
    res = fmt(data)
    if "--check" in argv:
        if res != src:
            print("poses.json: 베는 길 열쇠가 맞지 않음 — python3 tools/motion_arcs.py")
            return 1
        print("poses.json arcs ok")
        return 0
    if res != src:
        open(POSES, "w", encoding="utf-8").write(res)
    for name in ARCS:
        n = sum(1 for k in data["motions"][name]["keys"] if k.get("gen"))
        print(f"{name:14s} {n:3d} keys added")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
