#!/usr/bin/env python3
"""0.35.0 보스 헬 — 사용자 모델 (tools/boss_art/hel) 뼈에 더하는 동작 (tools/hel.py 가 씀).

뼈: skirt (뿌리 · 허리 축 — 아래 드레스, 모든 몸의 부모) ⊃ body ⊃ head · hairBack · rightArm · leftArm, mist (뿌리 — 발밑 안개 조각).
몸의 오른쪽 (x < 0 — 오른팔 쪽) 이 검은 반쪽 (죽은 쪽), 왼쪽이 창백한 반쪽 (산 쪽).
값은 모두 기본 자세 (사용자 idle 위에 더함) 에서의 차이 — 회전 (도) · 위치 (픽셀) · 크기 (배).
방향 (공허 기사와 같은 그리기): 팔 X - = 앞으로 들어 올림, 몸 X + = 앞으로 숙임, 머리 X - = 올려다봄, 몸 Y + = 오른쪽 어깨가 뒤로 (오른쪽으로 비틂),
오른팔 Z + = 옆으로 벌림 (왼팔은 Z -), 머리카락 X + = 뒤로 날림, skirt 위치 Y + = 몸 전체가 떠오름.

이어 붙이기: 같은 틀 (rpg_atk) 에서 다음 동작의 첫 자세 = 앞 동작의 끝 자세. _ready (힘을 모음 — 떨림, 끝 자세 유지) → 이름 (침 — 끝 자세 유지)
→ _rec (기본 자세로 — 한 번). 선딜레이 길이는 서버 (BossHel) 가 정해서 _ready 는 길이에 상관없이 버팀.
"""

BONES = ["skirt", "body", "head", "hairBack", "rightArm", "leftArm", "mist"]
DEFAULT = {"r": [0, 0, 0], "p": [0, 0, 0], "s": [1, 1, 1]}
KEY = {"r": "rotation", "p": "position", "s": "scale"}


def tremble(base, amp=1.5, rate=1900, phase=0):
    """떨림 (힘을 모으는 자세): 기본값 + sin(시간) — 식 글자"""
    return f"{base} + math.sin(q.life_time * {rate} + {phase}) * {amp}"


def pose(**bones):
    """자세: pose(body={"r": [...], "p": [...]}, ...) — 빠진 뼈 · 채널은 기본값"""
    return {b: dict(v) for b, v in bones.items()}


def merge(*parts):
    out = {}
    for p in parts:
        for b, v in p.items():
            out.setdefault(b, {}).update(v)
    return out


def num(v):
    return isinstance(v, (int, float))


def lerp(a, b, t):
    if num(a) and num(b):
        return a + (b - a) * t
    if t <= 0:
        return a
    if t >= 1:
        return b
    return f"({a}) + (({b}) - ({a})) * {t:.3f}"


def blend(p1, p2, t):
    """두 자세 사이 (t = 0 → p1, 1 → p2)"""
    out = {}
    for b in set(p1) | set(p2):
        out[b] = {}
        for ch in set(p1.get(b, {})) | set(p2.get(b, {})):
            a = p1.get(b, {}).get(ch, DEFAULT[ch])
            c = p2.get(b, {}).get(ch, DEFAULT[ch])
            out[b][ch] = [lerp(x, y, t) for x, y in zip(a, c)]
    return out


def r3(v):
    if num(v):
        v = round(v, 3)
        return int(v) if v == int(v) else v
    return v


def tkey(t):
    s = f"{t:.3f}".rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def seq(frames, loop=None, length=None):
    """키프레임 동작: frames = [(초, 자세), ...] — 쓰인 뼈 · 채널마다 모든 시각에 값 (빠지면 기본값)"""
    used = {}
    for _, p in frames:
        for b, chs in p.items():
            used.setdefault(b, set()).update(chs)
    bones = {}
    for b in BONES:
        if b not in used:
            continue
        out = {}
        for ch in ("r", "p", "s"):
            if ch not in used[b]:
                continue
            out[KEY[ch]] = {tkey(t): [r3(x) for x in p.get(b, {}).get(ch, DEFAULT[ch])] for t, p in frames}
        bones[b] = out
    a = {}
    if loop is not None:
        a["loop"] = loop
    a["animation_length"] = length if length is not None else max(t for t, _ in frames)
    a["bones"] = bones
    return a


N = {}

# ---------------------------------------------------------------- 죽음의 손톱 (오른손 — 검은 손)

CLAW_UP = pose(skirt={"r": [0, 10, 0]}, body={"r": [-12, 24, 0]}, head={"r": [-14, 12, -4]}, rightArm={"r": [tremble(-152), 0, 34]},
               leftArm={"r": [-34, 0, -30]}, hairBack={"r": [-6, 0, 0]})
CLAW_DOWN = pose(skirt={"r": [0, -8, 0]}, body={"r": [18, -28, 0]}, head={"r": [12, -12, 4]}, rightArm={"r": [-14, 0, -30]}, leftArm={"r": [16, 0, -14]},
                 hairBack={"r": [28, 0, 0]})

# ---------------------------------------------------------------- 망자의 손 (손바닥을 땅으로 → 두 팔을 들어 망자를 일으킴)

HANDS_LOW = pose(body={"r": [12, 0, 0]}, head={"r": [18, 0, 0]}, rightArm={"r": [tremble(-24, 2.5, 1500), 0, 44]},
                 leftArm={"r": [tremble(-24, 2.5, 1500, 90), 0, -44]}, hairBack={"r": [10, 0, 0]}, skirt={"p": [0, -1, 0]})
HANDS_UP = pose(body={"r": [-16, 0, 0]}, head={"r": [-24, 0, 0]}, rightArm={"r": [-168, 0, 18]}, leftArm={"r": [-168, 0, -18]}, hairBack={"r": [-12, 0, 0]},
                skirt={"p": [0, 2, 0]})

# ---------------------------------------------------------------- 삶과 죽음의 경계 (두 팔을 옆으로 — 검은 오른손이 죽음 쪽을 가리킴 → 가로질러 벰)

DIVIDE_OPEN = pose(body={"r": [-4, 0, 0]}, head={"r": [-6, -10, 12]}, rightArm={"r": [-12, 0, tremble(94, 1.2, 1700)]}, leftArm={"r": [-12, 0, -94]},
                   hairBack={"r": [4, 0, 0]})
DIVIDE_CUT = pose(body={"r": [14, -26, 0]}, head={"r": [10, -16, 0]}, rightArm={"r": [-64, 0, -24]}, leftArm={"r": [-10, 0, -76]}, hairBack={"r": [22, 0, 0]})

# ---------------------------------------------------------------- 굘 강의 물결 (두 손을 한쪽으로 모았다가 쓸어 보냄)

RIVER_GATHER = pose(skirt={"r": [0, 30, 0]}, body={"r": [4, 18, 0]}, head={"r": [0, -34, 0]}, rightArm={"r": [tremble(-84, 1.5), 0, 22]},
                    leftArm={"r": [tremble(-84, 1.5, 1900, 60), 0, -8]}, hairBack={"r": [8, 0, 0]})
RIVER_PUSH = pose(skirt={"r": [0, -28, 0]}, body={"r": [12, -20, 0]}, head={"r": [6, 30, 0]}, rightArm={"r": [-84, 0, -12]}, leftArm={"r": [-84, 0, -46]},
                  hairBack={"r": [26, 0, 0]})

# ---------------------------------------------------------------- 헬의 시선 (몸을 숙이고 팔을 뒤로 — 검은 눈으로 노림 → 고개를 쳐듦)

GAZE_AIM = pose(body={"r": [14, 0, 0]}, head={"r": [tremble(-8, 1.2, 2600), 0, 0]}, rightArm={"r": [34, 0, 30]}, leftArm={"r": [34, 0, -30]},
                hairBack={"r": [16, 0, 0]}, skirt={"p": [0, 1, 0]})
GAZE_FIRE = pose(body={"r": [2, 0, 0]}, head={"r": [-16, 0, 0]}, rightArm={"r": [46, 0, 42]}, leftArm={"r": [46, 0, -42]}, hairBack={"r": [30, 0, 0]},
                 skirt={"p": [0, 1, 0]})

# ---------------------------------------------------------------- 각성 · 걀라르 다리 · 헬헤임의 문 · 등장 · 죽음

ROAR = pose(skirt={"p": [0, 3, 0]}, body={"r": [-20, 0, 0]}, head={"r": [tremble(-30, 3, 2600), 0, 0]}, rightArm={"r": [-46, 0, 82]}, leftArm={"r": [-46, 0, -82]},
            hairBack={"r": [tremble(-16, 4, 1400), 0, 0]})
BRIDGE_UP = pose(skirt={"p": [0, 7, 0]}, body={"r": [-12, 0, 0]}, head={"r": [-26, 0, 0]}, rightArm={"r": [tremble(-174), 0, 10]}, leftArm={"r": [-174, 0, -10]},
                 hairBack={"r": [-10, 0, 0]})
BRIDGE_DOWN = pose(skirt={"p": [0, -1, 0]}, body={"r": [26, 0, 0]}, head={"r": [22, 0, 0]}, rightArm={"r": [-28, 0, 20]}, leftArm={"r": [-28, 0, -20]},
                   hairBack={"r": [32, 0, 0]})
GATE = pose(skirt={"p": [0, 4, 0]}, body={"r": [-10, 0, 0]}, head={"r": [-18, 0, 0]}, rightArm={"r": [-92, 0, tremble(72, 2, 1500)]},
            leftArm={"r": [-92, 0, tremble(-72, 2, 1500, 45)]}, hairBack={"r": [tremble(-8, 3, 1100), 0, 0]})
BECKON = pose(rightArm={"r": [-74, 0, 18]}, leftArm={"r": [-10, 0, -8]}, head={"r": [6, 8, 0]}, body={"r": [4, 10, 0]})
SINK = pose(body={"r": [10, 0, 0]}, head={"r": [-30, 0, 0]}, rightArm={"r": [-150, 0, 30]}, leftArm={"r": [-150, 0, -30]}, hairBack={"r": [-20, 0, 0]})


def thin(p, k):
    """안개로 흩어지는 모습: 가로로 가늘고 (k) 세로로 길게 (skirt — 몸 전체)"""
    out = merge(p)
    out.setdefault("skirt", {})["s"] = [k, 1 + (1 - k) * 0.5, k]
    return out


def gone(p):
    out = merge(p)
    out.setdefault("skirt", {})["s"] = [0, 0, 0]
    return out


def under(p, dy):
    """땅 밑 dy 픽셀 (등장 · 죽음)"""
    out = merge(p)
    sk = out.setdefault("skirt", {})
    base = sk.get("p", [0, 0, 0])
    sk["p"] = [base[0], base[1] - dy, base[2]]
    return out


def anims():
    """새 동작 {이름: 동작} (ID 는 tools/hel.py 가 animation.rpg.boss.hel.<이름> 으로)"""
    a = {}
    # 늘: 발 없이 떠 있음 (드레스 끝이 땅에서 조금 떠서 오르내림 — 발밑 안개 mist 는 땅에 그대로)
    a["hover"] = {"loop": True, "bones": {"skirt": {"position": [0, "3 + math.sin(q.life_time * 80) * 0.6", 0]}}}
    # 죽음의 손톱: 오른손을 높이 들어 떪 → 내리긁음 → 기본
    a["claw_ready"] = seq([(0, N), (0.15, blend(N, CLAW_UP, 0.6)), (0.4, CLAW_UP)], loop="hold_on_last_frame")
    a["claw"] = seq([(0, CLAW_UP), (0.06, blend(CLAW_UP, CLAW_DOWN, 0.6)), (0.14, CLAW_DOWN)], loop="hold_on_last_frame")
    a["claw_rec"] = seq([(0, CLAW_DOWN), (0.3, blend(CLAW_DOWN, N, 0.6)), (0.6, N)])
    # 망자의 손: 손바닥을 땅으로 내려 떪 → 두 팔을 하늘로 (땅에서 손이 솟음) → 기본
    a["hands_ready"] = seq([(0, N), (0.3, HANDS_LOW)], loop="hold_on_last_frame")
    a["hands"] = seq([(0, HANDS_LOW), (0.12, HANDS_UP)], loop="hold_on_last_frame")
    a["hands_rec"] = seq([(0, HANDS_UP), (0.5, N)])
    # 삶과 죽음의 경계: 두 팔을 옆으로 벌림 (검은 오른손 쪽이 죽음) → 가로질러 벰 → 기본
    a["divide_ready"] = seq([(0, N), (0.35, DIVIDE_OPEN)], loop="hold_on_last_frame")
    a["divide"] = seq([(0, DIVIDE_OPEN), (0.14, DIVIDE_CUT)], loop="hold_on_last_frame")
    a["divide_rec"] = seq([(0, DIVIDE_CUT), (0.5, N)])
    # 굘 강의 물결: 두 손을 한쪽으로 모음 → 쓸어 보냄 → 기본
    a["river_ready"] = seq([(0, N), (0.35, RIVER_GATHER)], loop="hold_on_last_frame")
    a["river"] = seq([(0, RIVER_GATHER), (0.2, RIVER_PUSH)], loop="hold_on_last_frame")
    a["river_rec"] = seq([(0, RIVER_PUSH), (0.5, N)])
    # 헬의 시선: 몸을 숙이고 노림 → 고개를 쳐들며 쏨 → 기본
    a["gaze_ready"] = seq([(0, N), (0.3, GAZE_AIM)], loop="hold_on_last_frame")
    a["gaze"] = seq([(0, GAZE_AIM), (0.08, GAZE_FIRE)], loop="hold_on_last_frame")
    a["gaze_rec"] = seq([(0, GAZE_FIRE), (0.45, N)])
    # 안개 걸음: 안개로 흩어짐 (보이지 않음) → 대상 뒤에서 모여 나타남
    a["vanish"] = seq([(0, N), (0.12, merge(N, {"skirt": {"s": [1.15, 0.9, 1.15]}})), (0.3, thin(N, 0.15)), (0.4, gone(N))], loop="hold_on_last_frame")
    a["appear"] = seq([(0, gone(N)), (0.1, thin(N, 0.15)), (0.28, merge(N, {"skirt": {"s": [1.1, 0.94, 1.1]}})), (0.38, N)], loop="hold_on_last_frame")
    # 각성 (망자의 여왕): 떠올라 두 팔을 벌리고 울부짖음
    a["roar"] = seq([(0, N), (0.4, blend(N, ROAR, 0.6)), (0.8, ROAR), (2.0, ROAR), (2.4, N)])
    # 걀라르 다리: 높이 떠올라 두 팔을 하늘로 (떪) → 내리침 → 기본
    a["bridge_ready"] = seq([(0, N), (0.6, blend(N, BRIDGE_UP, 0.7)), (1.0, BRIDGE_UP)], loop="hold_on_last_frame")
    a["bridge"] = seq([(0, BRIDGE_UP), (0.1, BRIDGE_DOWN)], loop="hold_on_last_frame")
    a["bridge_rec"] = seq([(0, BRIDGE_DOWN), (0.6, N)])
    # 헬헤임의 문 (제한 시간): 두 팔을 활짝 벌려 문을 엶 (끝 자세 유지) → 기본
    a["gate"] = seq([(0, N), (0.6, GATE)], loop="hold_on_last_frame")
    a["gate_rec"] = seq([(0, GATE), (0.6, N)])
    # 등장: 땅의 안개에서 솟아오름 (가늘게 → 부풂) → 손짓 (부른 사람을 부름) → 기본
    a["spawn"] = seq([(0, gone(under(N, 48))), (0.1, thin(under(N, 40), 0.3)), (0.7, thin(under(N, 8), 0.8)), (1.0, N), (1.3, BECKON), (1.65, BECKON), (1.9, N)])
    # 죽음 (1초 — 바닐라가 1초 뒤 치움): 두 팔을 들고 안개 속으로 가라앉으며 흩어짐
    a["die"] = seq([(0, N), (0.3, SINK), (0.7, thin(under(SINK, 18), 0.5)), (1.0, gone(under(SINK, 40)))], loop="hold_on_last_frame")
    return a
