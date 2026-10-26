"""0.46.0 룬 동작 도구 (tools/rune_motions.py · rune_motions_*.py 가 씀): 동작 하나 (Motion) · 룬 표 (use) · 자세 조각 · 옮겨 쓰기 (graft) · 1인칭 맨손.

룬마다 효과의 실제 시각 (RpgRuneTimingTests hits · 효과 코드의 Tasks.later) 에 맞춘 동작. 틱 0 = 룬이 나간 틱 (효과와 같은 틱에 동작 시작).
자세 조각 (오른팔 기준 — 왼팔은 거울) 을 이어 붙여 열쇠를 만들고, 무기 베기는 다듬어 둔 평타 · 스킬 동작 (slash_h · slash_v · thrust …) 의 구간을 옮겨 씀 (graft).
"""
import copy
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")

PREFIX = "rn_"
MOTIONS = []  # 동작들 (정의 차례)
TABLE = {}  # rune:fn (또는 rune:fn:등급) → 동작 이름


def use(motion, *keys):
    """이 동작을 룬 열쇠들 (fn 또는 fn:등급) 에"""
    for k in keys:
        TABLE["rune:" + k] = motion.name
    return motion


def n3(v):
    m = math.sqrt(sum(x * x for x in v))
    return [round(x / m, 4) for x in v]


def arm(d, bend=0, blade=None):
    a = {"dir": n3(d), "bend": round(bend, 1)}
    if blade is not None:
        a["blade"] = n3(blade)
    return a


def bent(d, bend, toward=(0, 1, 0)):
    """팔꿈치를 toward 쪽으로 bend 도 굽힌 팔 (손 끝 = 팔뚝 방향을 blade 로 — 맨손의 굽는 쪽을 정함)"""
    d = n3(d)
    t = list(toward)
    k = sum(a * b for a, b in zip(t, d))
    p = n3([t[i] - k * d[i] for i in range(3)])
    b = math.radians(bend)
    f = [d[i] * math.cos(b) + p[i] * math.sin(b) for i in range(3)]
    return arm(d, bend, f)


def L(a):
    """오른팔 조각 → 왼팔"""
    if a is None:
        return None
    b = copy.deepcopy(a)
    for k in ("dir", "blade", "face"):
        if k in b:
            b[k] = [-b[k][0], b[k][1], b[k][2]]
    return b


def P(base=None, **kw):
    """자세 (열쇠 하나의 몸 · 팔 · 다리 …): base 에 kw 를 덮음"""
    d = copy.deepcopy(base) if base else {}
    for k, v in kw.items():
        if v is None:
            d.pop(k, None)
        else:
            d[k] = copy.deepcopy(v)
    return d


def lerpP(a, b, s):
    """두 자세의 사이 (몸 · 다리 · 뿌리 · 머리 — 팔은 b)"""
    out = copy.deepcopy(b)
    for k in ("body", "rl", "ll", "head", "root"):
        if k in a or k in b:
            va = a.get(k, [0] * len(b.get(k, a.get(k))))
            vb = b.get(k, [0] * len(va))
            out[k] = [round(x + (y - x) * s, 3) for x, y in zip(va, vb)]
    for k in ("rk", "lk"):
        if k in a or k in b:
            out[k] = round(a.get(k, 0) + (b.get(k, 0) - a.get(k, 0)) * s, 2)
    return out


# ------------------------------------------------------------ 동작 하나


class Motion:
    """룬 동작 하나: 열쇠 (틱) · 1인칭 열쇠 · 머리 값. build() → poses.json 의 동작"""

    def __init__(self, name, ticks, about, parts=("body", "ra", "la", "legs", "head", "root"), fade=(0.06, 0.12)):
        self.name = PREFIX + name
        MOTIONS.append(self)
        self.ticks = ticks
        self.about = about
        self.parts = list(parts)
        self.fade = list(fade)
        self.keys = {}
        self.fps = {}
        self.fpos = {}
        self.head = {}
        self.arcs = []

    def t(self, tick):
        return round(min(1.0, max(0.0, tick / self.ticks)), 4)

    def key(self, tick, ease=None, **pose):
        k = dict(pose)
        if ease:
            k["ease"] = ease
        self.keys[self.t(tick)] = k
        return self

    def arc(self, tick, side="ra", via=None):
        """tick 의 열쇠로 가는 구간을 베는 길로 (무기 끝이 via 를 지나는 원 — motion_arcs)"""
        self.arcs.append([self.t(tick), side, n3(via) if via is not None else None])
        return self

    def fp(self, tick, p=(0, 0, 0), r=None, blade=None, edge=None, ease=None):
        self.fps[self.t(tick)] = fpkey(p, r, blade, edge, ease)
        return self

    def fpo(self, tick, p=(0, 0, 0), r=None, ease=None):
        self.fpos[self.t(tick)] = fpkey(p, r, None, None, ease)
        return self

    def set(self, **kw):
        self.head.update(kw)
        return self

    def build(self):
        # softLegs: 걸으면 다리 (· 낮춘 몸) 를 바닐라 걸음에 맡김 — 룬은 움직이며 쓰는 일이 많음 (서서 쓰는 동작이 미끄러지지 않게)
        m = {"ticks": self.ticks, "parts": self.parts, "softLegs": True, "fade": self.fade}
        m.update(self.head)
        if self.arcs:
            m["arcs"] = sorted(self.arcs, key=lambda a: a[0])
        keys = dict(self.keys)
        keys.setdefault(0.0, {})
        keys.setdefault(1.0, {})
        m["keys"] = [dict({"t": t}, **keys[t]) for t in sorted(keys)]
        for name, src in (("fp", self.fps), ("fpo", self.fpos)):
            if name == "fpo" and not src:
                continue
            ks = dict(src)
            ks.setdefault(0.0, {})
            ks.setdefault(1.0, {})
            m[name] = [dict({"t": t}, **ks[t]) for t in sorted(ks)]
        return m


def fpkey(p, r, blade, edge, ease):
    k = {}
    if any(abs(x) > 1e-9 for x in p):
        k["p"] = [round(x, 3) for x in p]
    if r is not None:
        k["r"] = [round(x, 1) for x in r]
    if blade is not None:
        k["blade"] = n3(blade)
    if edge is not None:
        k["edge"] = n3(edge)
    if ease:
        k["ease"] = ease
    return k



# ------------------------------------------------------------ 자세 조각 (오른팔)

A_GUARD = bent([0.12, -0.75, 0.6], 105, (-0.35, 1, 0.25))  # 주먹을 턱 앞에 (막는 자세)
A_CHAMBER = bent([0.35, -0.6, -0.5], 112, (0, 0.35, 1))  # 주먹을 허리에 당김
A_JAB = arm([-0.16, 0.06, 1], 2)  # 곧게 뻗은 주먹 (가운데로)
A_JAB_HI = arm([-0.12, 0.2, 1], 2)
A_JAB_LO = arm([-0.1, -0.25, 1], 2)
A_HOOK = bent([0.78, 0.1, 0.62], 95, (-1, 0, 0.3))  # 옆에서 휘두른 주먹
A_UPPER = bent([0.1, -0.45, 0.88], 105, (-0.15, 1, 0.2))  # 올려 치기
A_PALM = arm([-0.07, 0.1, 1], 0, [0, 1, 0.1])  # 손바닥 밀기
A_PALM_LOW = arm([-0.05, -0.2, 1], 0, [0, 1, 0.1])
A_RAISE = arm([0.14, 1, 0.08], 4, [0, 1, 0.05])  # 하늘로
A_RAISE_FWD = arm([0.1, 0.72, 0.68], 4, [0, 1, 0.4])  # 앞 위로
A_RAISE_FIST = bent([0.2, 0.95, 0.12], 25, (-0.2, 1, 0.3))
A_DOWN_FWD = arm([0.06, -0.62, 0.78], 4)  # 앞 아래로 뻗음
A_SPREAD = arm([1, 0.22, 0.12], 6)  # 옆으로 펼침
A_SPREAD_UP = arm([0.82, 0.58, 0.1], 6)
A_SPREAD_DOWN = arm([0.75, -0.6, 0.18], 8)
A_CHEST = bent([0.3, -0.62, 0.55], 140, (-1, 0.35, 0.3))  # 주먹을 가슴에 (맹세)
A_PRAY = bent([0.22, -0.62, 0.68], 120, (-0.75, 0.9, 0.2))  # 두 손을 모음
A_BACK = arm([0.28, -0.45, -0.85], 25)  # 뒤로 젖힘
A_HIP = bent([0.4, -0.88, 0.05], 45, (0, 0, 1))
A_FLEX = bent([0.95, 0.18, 0.08], 120, (0, 1, 0.15))  # 알통
A_CROSS = bent([0.25, -0.35, 0.85], 120, (-1, 0.25, 0))  # 팔짱 (몸 앞에서 엇갈림)
A_OVER_BACK = bent([0.22, 0.88, -0.42], 70, (0, 0.3, -1))  # 머리 위 뒤로 젖힘
A_OVER = bent([0.18, 0.95, 0.1], 40, (-0.2, 0.5, 1))  # 머리 위
A_THROW_WIND = arm([0.45, 0.55, -0.7], 85, [0, 1, -0.2])  # 위로 던지기 준비
A_THROW_REL = arm([0.05, 0.15, 1], 2, [0, 0.3, 1])
A_THROW_FOLLOW = arm([-0.25, -0.5, 0.8], 10, [-0.2, -0.4, 0.9])
A_SIDE_WIND = bent([0.85, 0.12, -0.5], 50, (0, 0, 1))  # 옆으로 던지기 준비 (오른쪽 뒤)
A_SIDE_REL = arm([-0.55, 0.05, 0.85], 4)  # 옆으로 쓸어 던짐 (왼쪽 앞까지)
A_UNDER_WIND = arm([0.25, -0.82, -0.52], 12)  # 아래로 던지기 준비
A_UNDER_REL = arm([0.06, -0.12, 1], 6)
A_POINT = arm([-0.03, 0.12, 1], 0, [0, 0.12, 1])
A_POINT_UP = arm([0.02, 0.62, 0.78], 0)
A_GROUND = arm([0.12, -0.92, 0.38], 4)  # 땅을 짚음
A_PULL = bent([0.45, -0.3, -0.3], 125, (0, 0.2, 1))  # 당김 (주먹을 허리 뒤로)
A_SHIELD = bent([0.15, -0.35, 0.92], 75, (-1, 0.4, 0.2))  # 팔뚝을 앞으로 세움 (방패)

# 다리 · 몸
LEGS_FIGHT = dict(rl=[14, 0, 4], ll=[-16, 0, -4], rk=16, lk=12)
LEGS_LUNGE = dict(rl=[24, 0, 4], ll=[-34, 0, -2], rk=14, lk=34, root=[-0.1, 0.25])
LEGS_WIDE = dict(rl=[0, 0, 12], ll=[0, 0, -12], rk=12, lk=12)
LEGS_SQUAT = dict(rl=[-42, 0, 10], ll=[-42, 0, -10], rk=88, lk=88, root=[-0.34, 0])
LEGS_KNEEL = dict(rl=[2, 0, 4], rk=92, ll=[-82, 0, -4], lk=88, root=[-0.38, 0])
LEGS_STOMP_UP = dict(rl=[-72, 0, 6], rk=92, ll=[-6, 0, -4], lk=14, root=[0.04, 0])
LEGS_STOMP = dict(rl=[-14, 0, 10], ll=[-6, 0, -10], rk=34, lk=26, root=[-0.16, 0])

FIGHT = P(dict(body=[6, -14, 0], ra=A_GUARD, la=L(A_GUARD), head=[2, 0]), **LEGS_FIGHT)


# ------------------------------------------------------------ 다듬어 둔 동작에서 옮겨 쓰기

SRC = None


def src(name):
    global SRC
    if SRC is None:
        with open(POSES, encoding="utf8") as f:
            SRC = json.load(f)["motions"]
    return SRC[name]


def graft(m, name, tick0, ticks, t_from=0.0, t_to=1.0, fp=True, keys=True, trail=True, drop_ends=True):
    """다듬어 둔 동작 name 의 [t_from, t_to] 구간 (열쇠 · 1인칭 · 베는 길 · 궤적) 을 m 의 tick0 ~ tick0 + ticks 에 (끝 빈 열쇠 · 잇는 열쇠는 뺌)"""
    s = src(name)
    span = t_to - t_from

    def at(t):
        return tick0 + (t - t_from) / span * ticks

    if keys:
        for k in s["keys"]:
            if k.get("gen"):
                continue
            t = k["t"]
            if t < t_from - 1e-9 or t > t_to + 1e-9:
                continue
            if drop_ends and (t <= 0.0 or t >= 1.0) and len([x for x in k if x != "t"]) == 0:
                continue
            kk = {x: copy.deepcopy(v) for x, v in k.items() if x not in ("t", "gen", "genEase", "ease")}
            e = k.get("genEase", k.get("ease"))
            if e and e != "inout":
                kk["ease"] = e
            m.keys[m.t(at(t))] = kk
        import motion_arcs  # noqa
        for t, side, via in motion_arcs.ARCS.get(name, []):
            if t_from - 1e-9 <= t <= t_to + 1e-9:
                m.arcs.append([m.t(at(t)), side, via])
    if fp:
        for k in s.get("fp", []):
            t = k["t"]
            if t < t_from - 1e-9 or t > t_to + 1e-9:
                continue
            if drop_ends and (t <= 0.0 or t >= 1.0) and len([x for x in k if x != "t"]) == 0:
                continue
            m.fps[m.t(at(t))] = {x: copy.deepcopy(v) for x, v in k.items() if x != "t"}
    if trail and "trail" in s:
        tr = m.head.setdefault("trail", [])
        for a, b in s["trail"]:
            lo, hi = max(a, t_from), min(b, t_to)
            if hi > lo:
                tr.append([m.t(at(lo)), m.t(at(hi))])
        if "tilt" in s:
            m.head["tilt"] = s["tilt"]
    return m


# 다듬어 둔 동작의 때 (t): 준비 · 닿음 · 끝 자세 — graft2 의 기준
CUE = {
    "slash_h": (0.3, 0.5, 0.66, 0.84),
    "slash_v": (0.32, 0.52, 0.7, 0.86),
    "skill_slash": (0.3, 0.48, 0.66, 0.85),
    "chop": (0.36, 0.56, 0.76, 0.76),
    "smite": (0.32, 0.55, 0.75, 0.75),
    "slam": (0.4, 0.58, 0.8, 0.8),
    "thrust": (0.3, 0.5, 0.7, 0.7),
    "lance_sweep": (0.28, 0.5, 0.66, 0.85),
    "throw": (0.38, 0.55, 0.75, 0.9),
    "throw_weapon": (0.38, 0.55, 0.75, 0.9),
    "shoot": (0.3, 0.62, 0.85, 0.85),
    "shoot_up": (0.3, 0.62, 0.85, 0.85),
    "bash": (0.3, 0.5, 0.72, 0.72),
}


def strike(m, name, wind_tick, hit_tick, end_tick=None):
    """다듬어 둔 베기 · 내려치기 · 찌르기 name 을 준비 = wind_tick, 닿음 = hit_tick 으로 (끝 자세 end_tick — 없으면 같은 빠르기)"""
    w, h, f, r = CUE[name]
    k = (hit_tick - wind_tick) / (h - w)
    if end_tick is None:
        end_tick = hit_tick + (r - h) * k
    graft(m, name, wind_tick, end_tick - wind_tick, t_from=w, t_to=r)
    return m


def keyp(m, tick, pose, ease=None):
    m.key(tick, ease, **pose)


def hold(m, a, b, pose, ease=None):
    m.key(a, ease, **pose)
    m.key(b, None, **pose)


# ------------------------------------------------------------ 1인칭 맨손 (fp · fpo — (p: 오른쪽 · 위 · 뒤 칸, r: [x 위로 젖힘, y 가운데로 돎, z] 도), 어깨가 축)
# tools/hands_preview.py 로 맞춘 꼴 (오른손 기준 — 왼손은 fpl 이 거울로)

FP_GUARD = ((0.0, 0.05, 0.0), (15, 15, 0))  # 주먹을 조금 올려 가운데로 (막는 자세)
FP_JAB = ((0.0, 0.05, -0.35), (15, 45, 0))  # 가운데 앞으로 뻗은 주먹
FP_JAB2 = ((0.0, 0.1, -0.42), (22, 52, 0))  # 크게 뻗은 주먹 (조준점 가까이)
FP_CHAMBER = ((0.05, -0.15, 0.15), (-10, -5, 0))  # 허리로 당김
FP_HOOK = ((0.05, 0.1, -0.2), (10, 70, -25))  # 옆에서 휘두른 주먹
FP_UPPER = ((0.0, 0.1, -0.2), (40, 30, 0))  # 올려 치기
FP_RAISE = ((-0.1, 0.4, 0.1), (80, 30, 0))  # 하늘로 듦 (팔이 화면 아래 구석에서 위로 비스듬히 올라가 화면 밖으로)
FP_DOWN = ((0.0, -0.1, -0.3), (-20, 30, 0))  # 아래로 내리침 · 땅을 짚음
FP_PALM = ((0.0, 0.05, -0.4), (18, 55, 0))  # 손바닥을 앞으로 내밂
FP_LOW = ((0.0, -0.4, 0.1), (0, 0, 0))  # 내림 (거의 안 보임)
FP_REST = ((0.0, 0.0, 0.0), (0, 0, 0))
FP_GATHER_R = ((0.0, -0.08, 0.0), (10, 20, 0))  # 두 손을 오른 허리 앞에 모음 (오른손)
FP_GATHER_L = ((-0.25, -0.05, 0.0), (10, 60, 0))  # (왼손 — 가운데를 넘어 오른손 곁으로)
FP_GROUND = ((0.0, -0.2, -0.35), (0, 35, 0))  # 앞 아래 땅을 침 · 짚음
FP_PALM_UP = ((0.0, 0.15, -0.3), (40, 40, 0))  # 손을 앞 위로 들어 올림
FP_CROSS = ((-0.05, 0.05, -0.05), (20, 60, 0))  # 팔을 몸 앞에서 엇갈림
FP_PUSH_OUT = ((0.12, 0.08, -0.25), (15, -20, 0))  # 두 팔을 바깥으로 밀어냄
FP_SPREAD_UP = ((0.25, 0.25, 0.05), (45, -35, 0))  # 두 팔을 위로 펼침 (화면 가장자리)


def nudge(pre, dp=(0, 0, 0), dr=(0, 0, 0)):
    """꼴을 조금 옮김 · 돌림"""
    return (tuple(a + b for a, b in zip(pre[0], dp)), tuple(a + b for a, b in zip(pre[1], dr)))


# 1인칭 무기 (fp — p: 오른쪽 · 위 · 뒤 칸, blade: 무기 끝 [오른쪽, 위, 앞], edge: 날)
FPW_UP = dict(p=(-0.3, 0.75, 0.1), blade=(0.0, 1, 0.12))  # 세워 올림
FPW_SKY = dict(p=(-0.25, 1.15, 0.2), blade=(0.0, 1, -0.15))  # 하늘로
FPW_FWD = dict(p=(-0.42, 0.28, -0.45), blade=(-0.1, 0.05, 1), edge=(0, -1, 0))  # 앞으로 겨눔
FPW_LOW = dict(p=(0.08, -0.4, 0.15), blade=(0.15, 0.55, 0.8))  # 내림
FPW_ACROSS = dict(p=(-0.55, 0.32, 0.0), blade=(-1, 0.1, 0.2), edge=(0, 0, 1))  # 가로로 앞에 듦
FPW_DOWN = dict(p=(-0.35, -0.15, -0.25), blade=(0.05, -0.85, 0.5), edge=(0, -0.5, -0.85))  # 앞 아래로 꽂음
FPW_BACK = dict(p=(0.45, 0.0, 0.3), blade=(0.75, 0.25, -0.6), edge=(0.6, 0, 0.75))  # 오른쪽 뒤로 젖힘 (모음)
FPW_REST = dict(p=(0, 0, 0))


def fpw(m, tick, pre, ease=None, dp=None):
    """1인칭 무기 열쇠 (위의 꼴 — dp 만큼 더 옮김)"""
    p = pre["p"]
    if dp is not None:
        p = tuple(a + b for a, b in zip(p, dp))
    m.fp(tick, p, None, pre.get("blade"), pre.get("edge"), ease)


def fists(m, main=True, off=True):
    m.set(hands={"main": "fist" if main else "weapon", "off": "fist" if off else "weapon"})
    return m


def fpr(m, tick, pre, ease=None):
    """1인칭 오른손 (주 손) 맨팔 — pre = (p, r) 꼴"""
    p, r = pre
    m.fp(tick, p, r, ease=ease)


def fpl(m, tick, pre, ease=None):
    """1인칭 왼손 맨팔 (오른손처럼 적은 꼴을 거울로 — x 와 y · z 회전을 뒤집음)"""
    p, r = pre
    m.fpo(tick, (-p[0], p[1], p[2]), (r[0], -r[1], -r[2]), ease=ease)


def fpb(m, tick, pre, pre_l=None, ease=None):
    """1인칭 두 손 (왼손은 pre_l — 없으면 같은 꼴의 거울)"""
    fpr(m, tick, pre, ease)
    fpl(m, tick, pre_l if pre_l is not None else pre, ease)


# ------------------------------------------------------------ 무기 든 자세 (오른손에 무기 — blade = 무기 끝)

W_READY = P(body=[8, 6, 0], ra=arm([0.2, -0.55, 0.65], 80, [0.05, 0.6, 0.8]), la=L(A_GUARD), **LEGS_FIGHT)
W_SKY = P(body=[-12, -4, 0], ra=arm([0.12, 1, 0.08], 5, [0, 1, 0.02]), la=L(A_HIP), head=[-24, 0], **LEGS_WIDE)  # 무기를 하늘로
W_HIGH2 = P(body=[-14, 4, 0], ra=arm([0.12, 0.95, -0.3], 40, [0, 0.3, -1]), la=arm([-0.05, 0.95, -0.25], 45), rl=[8, 0, 0], ll=[-8, 0, 0],
            rk=8, lk=8, root=[0.05, 0])  # 두 손으로 머리 위 (내려찍기 준비 — slam 의 준비 자세)
W_GUARD_H = P(body=[10, 10, 0], ra=arm([0.15, -0.25, 0.95], 82, [-0.95, 0.15, 0.2]), la=arm([-0.1, -0.35, 0.92], 62), **LEGS_FIGHT)  # 무기를 가로로 앞에 (막기)
W_UP_FRONT = P(body=[-6, 0, 0], ra=arm([0.08, 0.35, 0.95], 85, [0, 1, 0.08]), la=arm([-0.05, 0.3, 0.95], 80), head=[-6, 0], **LEGS_FIGHT)  # 무기를 얼굴 앞에 세움


# ------------------------------------------------------------ 오른쪽으로 도는 회전 베기
# 룬 효과의 칼 · 초승달 · 회오리는 모두 위에서 보아 시계 방향 (오른쪽) 으로 돎 → 몸도 오른쪽으로 (spin −). 무기를 앞으로 뻗은 채 몸째 돌아
# 앞을 지나는 틱 (passes — 효과의 타격마다 한 바퀴) 에 무기가 앞을 지남. 1인칭은 무기가 왼쪽 가장자리에서 들어와 오른쪽으로 쓸고 나감 (뒤쪽 반 바퀴는 화면 밖).

SPIN_WIND_R = P(body=[6, -34, 0], ra=arm([-0.5, 0.15, 0.65], 60, [-0.7, 0.05, 0.6]), la=arm([-0.55, -0.5, -0.4], 35), rk=26, lk=26, rl=[8, 0, 0],
                ll=[-6, 0, 0], root=[-0.12, 0])  # 몸을 왼쪽으로 감고 무기를 왼쪽 앞에 (되돌려 벨 준비)
SPIN_FWD_R = P(body=[8, 4, 0], ra=arm([0.12, 0.06, 1], 6, [0.35, 0.0, 1]), la=arm([-0.88, -0.3, -0.3], 30), rk=26, lk=26, rl=[-6, 0, 0], ll=[8, 0, 0],
               root=[-0.12, 0])  # 무기를 앞으로 뻗음 (몸째 돎)
SPIN_END_R = P(body=[6, 18, 0], ra=arm([0.55, -0.2, 0.78], 18, [0.85, -0.15, 0.45]), la=arm([-0.6, -0.7, -0.2], 30), rk=20, lk=20, root=[-0.08, 0])

FP_GRIP_X = 0.826  # 1인칭 무기 손잡이의 화면 x (바닐라 손 자리 0.56 + 손잡이 0.266) — 화면 거울: p_x → −p_x − 2 × 이 값


def fp_mirror_x(p):
    return (-p[0] - 2 * FP_GRIP_X, p[1], p[2])


FP_SPIN_OFF_R = dict(p=fp_mirror_x((-3.0, 0.25, 0.55)), blade=(0.5, 0.0, -0.85))  # 1인칭 회전: 화면 밖 오른쪽 뒤 (뒤쪽 반 바퀴 동안 여기에)


def fp_spin_pass(m, tk):
    """1인칭: tk 에 무기가 화면 가운데를 지나 왼쪽 → 오른쪽으로 쓸고 나감 (평타 회전 베기 spin 의 1인칭을 화면 거울로)"""
    m.fp(tk - 3.0, fp_mirror_x((1.4, 0.25, 0.55)), None, (-0.5, 0.0, -0.85), None, "linear")  # 화면 밖 (왼쪽 뒤)
    m.fp(tk - 2.1, fp_mirror_x((0.548, 0.394, 0.225)), None, (-0.807, 0.174, -0.565), (-0.565, 0, 0.807), "linear")
    m.fp(tk - 0.6, fp_mirror_x((0.045, 0.409, 0.125)), None, (-0.637, 0.139, 0.759), (0.759, 0, 0.637), "in")
    m.fp(tk + 0.6, fp_mirror_x((-1.085, 0.364, 0.075)), None, (0.936, 0.087, 0.341), (0.341, 0, -0.936), "linear")  # tk 에 화면 가운데
    m.fp(tk + 1.6, fp_mirror_x((-2.028, 0.329, 0.225)), None, (0.766, 0, -0.643), (-0.643, 0, -0.766), "linear")
    m.fp(tk + 2.4, FP_SPIN_OFF_R["p"], None, FP_SPIN_OFF_R["blade"], None, "linear")  # 화면 밖 (오른쪽 뒤)


def spin_right(m, passes, end_tick, wind_tick=None, fwd=None):
    """passes = 무기가 앞을 지나는 틱 (바퀴마다), end_tick = 마지막 바퀴가 느려지며 멈추는 틱 (무기는 오른쪽 앞), fwd = 도는 동안의 자세 (없으면 SPIN_FWD_R)"""
    first = passes[0]
    keyp(m, wind_tick if wind_tick is not None else max(0.3, first - 1.8), SPIN_WIND_R)
    for i, tk in enumerate(passes):
        keyp(m, tk, P(fwd or SPIN_FWD_R, spin=-360 * i), "in" if i == 0 else "linear")
    n = len(passes)
    keyp(m, end_tick, P(SPIN_END_R, spin=-360 * n), "out")
    m.keys[1.0] = {"spin": -360 * n}
    m.set(tilt=30, trail=[[m.t(max(0.3, first - 1.2)), m.t(end_tick - 1)]])
    for i, tk in enumerate(passes):
        if i > 0:  # 뒤쪽 반 바퀴: 화면 밖 오른쪽에 머물다가 다음 바퀴 바로 전에 화면 밖 왼쪽으로 (화면을 거꾸로 가로지르지 않게)
            m.fp(tk - 3.05, FP_SPIN_OFF_R["p"], None, FP_SPIN_OFF_R["blade"], None, "linear")
        fp_spin_pass(m, tk)
    fpw(m, end_tick, FPW_LOW, "out")





# ------------------------------------------------------------ 0.46.0 마법 · 소품 룬이 함께 쓰는 조각 (오른손 = 무기 · 지팡이, 왼손 = 맨손)

RA_POINT = arm([-0.05, 0.08, 1], 0, [0, 0.1, 1])  # 무기 끝으로 앞을 겨눔
RA_POINT_LOW = arm([-0.02, -0.25, 0.97], 4, [0, -0.35, 0.94])  # 앞 땅을 겨눔
RA_SKY = arm([0.12, 1, 0.08], 5, [0, 1, 0.02])  # 하늘로
RA_UPF = arm([0.2, 0.55, 0.8], 20, [0.1, 0.9, 0.4])  # 앞 위로 듦
RA_LOW = arm([0.25, -0.85, 0.3], 25, [0, 0.3, 0.95])  # 옆에 내림
RA_READY = arm([0.2, -0.55, 0.65], 80, [0.05, 0.6, 0.8])  # 앞에 듦 (싸울 자세)
RA_UPRIGHT = arm([0.08, 0.35, 0.95], 85, [0, 1, 0.08])  # 얼굴 앞에 세움
RA_ACROSS = arm([0.15, -0.25, 0.95], 82, [-0.95, 0.15, 0.2])  # 가로로 앞에
RA_SPREAD_UP = arm([0.6, 0.75, 0.2], 10, [0.3, 1, 0.1])  # 위로 펼침
RA_SPREAD_DOWN = arm([0.85, -0.45, 0.25], 6, [0.6, -0.3, 0.75])  # 아래 바깥으로 펼침
RA_BACK_HIGH = arm([0.45, 0.35, -0.6], 85, [0.1, 0.9, -0.3])  # 귀 옆 뒤로 당김 (던질 준비)
RA_PLANT = arm([0.05, -0.35, 0.94], 40, [0, -0.95, 0.3])  # 앞 땅에 꽂음

# 1인칭 무기 꼴 더
FPW_DRAWN = dict(p=(0.2, 0.25, 0.4), blade=(0.2, 0.9, -0.3))  # 귀 옆 뒤로 당김
FPW_SPREAD = dict(p=(0.45, -0.05, 0.0), blade=(0.65, 0.45, 0.6))  # 오른쪽 바깥으로 펼침

# 두 팔 자세
CALL = P(body=[-12, 0, 0], head=[-26, 0], ra=RA_SKY, la=L(A_RAISE), **LEGS_WIDE)  # 무기와 왼손을 하늘로 (부름)


def off_bare(m):
    """오른손은 무기 그대로 · 왼손은 맨손 (1인칭 왼팔 = fpo)"""
    m.set(hands={"main": "weapon", "off": "fist"})
    return m


# ------------------------------------------------------------ 두 손으로 들어 기를 모았다가 내려찍기
# (사용자 "마검 초월 같은거는 두손을 모으고 검을 위로 들고 기를 모으다가 아래로 내려찍는 모션으로")

GRIP2H = P(body=[-4, 0, 0], head=[-4, 0], ra=arm([-0.12, 0.3, 0.95], 95, [0, 1, 0.08]), la=bent([0.12, 0.3, 0.95], 95, (-0.3, 1, 0)),
           **LEGS_WIDE)  # 두 손을 모아 무기를 얼굴 앞에 세움 (한 손잡이를 두 손으로)
RAISE2H = P(body=[-10, 0, 0], head=[-22, 0], ra=arm([-0.1, 0.97, 0.18], 12, [0, 1, 0.04]), la=bent([0.1, 0.97, 0.18], 14, (-0.3, 1, 0)), rl=[0, 0, 10],
            ll=[0, 0, -10], rk=14, lk=14, root=[-0.04, 0])  # 두 손으로 무기를 머리 위로 곧게 듦
FPL_GRIP2H = ((-0.3, -0.02, -0.12), (20, 70, 0))  # 1인칭 왼손: 가운데를 넘어 무기 손잡이로
FPL_RAISE2H = nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0))  # 1인칭 왼손: 무기와 함께 머리 위로


def charge_slam(m, slam_tick, grip_tick=1.5, raise_tick=4.5):
    """두 손을 모아 무기를 세우고 (grip_tick) → 두 손으로 머리 위로 곧게 들어 기를 모음 (raise_tick ~ slam_tick − 2.6, 조금씩 떨리며 몸이 젖혀지고 무릎이 굽음)
    → 머리 뒤로 젖혔다가 아래로 내려찍음 (slam_tick — 다듬어 둔 내려찍기 slam 을 옮겨 씀). 왼손은 맨손 (1인칭 왼팔도 손잡이로 모임)"""
    off_bare(m)
    wind = slam_tick - 2.6
    keyp(m, grip_tick, GRIP2H, "out3")
    keyp(m, raise_tick, RAISE2H, "out3")
    fpw(m, grip_tick, FPW_UP, "out3", dp=(-0.2, -0.15, 0))
    fpw(m, raise_tick, FPW_UP, "out3", dp=(0.1, -0.1, 0))  # 1인칭: 머리 위로 든 무기가 화면 위쪽에 보이게 (떨리며 기를 모음)
    fpl(m, grip_tick, FPL_GRIP2H, "out3")
    fpl(m, raise_tick, FPL_RAISE2H, "out3")
    n = max(1, int((wind - 0.6 - raise_tick) / 2.5))
    for i in range(1, n + 1):
        tick = raise_tick + (wind - 0.6 - raise_tick) * i / n
        s = i / n
        sgn = 1 if i % 2 else -1
        keyp(m, tick, P(RAISE2H, body=[-10 - 4 * s, 0, sgn * 1.2], head=[-22 - 4 * s, 0], rk=14 + 10 * s, lk=14 + 10 * s, root=[-0.04 - 0.05 * s, 0]))
        fpw(m, tick, FPW_UP, dp=(0.1 + sgn * 0.02, -0.1 + 0.04 * s, 0.05 * s))
        fpl(m, tick, nudge(FPL_RAISE2H, (sgn * 0.02, 0.02 * s, 0)))
    strike(m, "slam", wind, slam_tick)
    fpl(m, wind, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))  # 머리 뒤로 젖힘 (왼팔이 화면 위 밖으로)
    fpl(m, slam_tick - 1.1, ((0.18, 0.12, 0.05), (45, -15, 0)))  # 내려오는 왼팔은 화면 왼쪽 가장자리로 (눈앞을 가로막지 않게)
    fpl(m, slam_tick, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
    fpl(m, slam_tick + 4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
    return m
