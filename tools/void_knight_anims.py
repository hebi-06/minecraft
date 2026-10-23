#!/usr/bin/env python3
"""0.33.0 보스 공허 기사 — 사용자 모델 (tools/boss_art/void_knight) 뼈에 더하는 동작 (tools/void_knight.py 가 씀).

뼈: body (뿌리 · 엉덩이 축) ⊃ head · rightArm ⊃ sword · leftArm · rightCape · leftCape, 다리 rightLeg · leftLeg (뿌리).
값은 모두 기본 자세 (사용자 idle 위에 더함) 에서의 차이 — 회전 (도) · 위치 (픽셀) · 크기 (배).
방향 (미리보기로 확인): 팔 · 다리 X - = 앞으로 들어 올림, 몸 X + = 앞으로 숙임, 몸 Y + = 오른쪽 어깨가 뒤로 (오른쪽으로 비틂),
오른팔 Z + = 옆으로 벌림, 칼 X + = 칼날이 앞 · 아래로 (칼날 각 a = 팔 X + 75 + 칼 X: 0 = 위, 90 = 앞 수평, 180 = 아래), 망토 X + = 뒤로 펄럭임.

이어 붙이기: 같은 틀 (rpg_atk) 에서 다음 동작의 첫 자세 = 앞 동작의 끝 자세 (끊기지 않게). _ready (자세 잡기 — 끝 자세 유지) → 이름 (휘두름 — 끝 자세 유지,
빈틈 동안 그대로) → _rec (기본 자세로 — 한 번). 서버 (BossVoidKnight) 가 틱에 맞춰 차례로 틂 — 선딜레이 길이는 서버가 정해서 _ready 는 길이에 상관없이 버팀.
"""

BONES = ["body", "head", "rightArm", "sword", "leftArm", "rightCape", "leftCape", "rightLeg", "leftLeg"]
DEFAULT = {"r": [0, 0, 0], "p": [0, 0, 0], "s": [1, 1, 1]}
KEY = {"r": "rotation", "p": "position", "s": "scale"}


def tremble(base, amp=1.5, rate=1900, phase=0):
    """떨림 (힘을 모으는 자세): 기본값 + sin(시간) — 식 글자"""
    return f"{base} + math.sin(q.life_time * {rate} + {phase}) * {amp}"


def pose(**bones):
    """자세: pose(body={"r": [...], "p": [...]}, ...) — 빠진 뼈 · 채널은 기본값"""
    return {b: dict(v) for b, v in bones.items()}


def capes(rx, rz=0):
    return {"rightCape": {"r": [rx, 0, rz]}, "leftCape": {"r": [rx, 0, -rz]}}


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

# ---------------------------------------------------------------- 움직임

DASH = merge(pose(body={"r": [24, 0, 0], "p": [0, -0.8, 0]}, head={"r": [-18, 0, 0]}, rightArm={"r": [40, 0, 14]}, sword={"r": [76, 0, 0]},
                  leftArm={"r": [46, 0, -10]}), capes(56))

BACK1 = merge(pose(body={"r": [-14, 0, 0], "p": [0, -1.5, 0]}, rightArm={"r": [-46, 0, 20]}, sword={"r": [15, 0, 0]}, leftArm={"r": [-34, 0, -22]},
                   rightLeg={"r": [-30, 0, 0]}, leftLeg={"r": [-22, 0, 0]}), capes(-30))
BACK2 = merge(pose(body={"r": [10, 0, 0], "p": [0, -1.2, 0]}, rightArm={"r": [-20, 0, 12]}, leftArm={"r": [-10, 0, -12]}, rightLeg={"r": [-10, 0, 0]},
                   leftLeg={"r": [16, 0, 0]}), capes(22))

# ---------------------------------------------------------------- 내려베기 · 3연격 마지막 · 공허 참

CLEAVE_UP = merge(pose(body={"r": [-12, 22, 0], "p": [0, 0.4, 0]}, head={"r": [8, -14, 0]}, rightArm={"r": [tremble(-168), 0, 14]}, sword={"r": [-15, 0, 0]},
                       leftArm={"r": [-150, 0, -24]}, rightLeg={"r": [-16, 0, 0]}, leftLeg={"r": [14, 0, 0]}), capes(-6))
CLEAVE_DOWN = merge(pose(body={"r": [30, -18, 0], "p": [0, -1.8, 0]}, head={"r": [14, 10, 0]}, rightArm={"r": [-28, 0, -8]}, sword={"r": [53, 0, 0]},
                         leftArm={"r": [-40, 0, 10]}, rightLeg={"r": [-30, 0, 0]}, leftLeg={"r": [24, 0, 0]}), capes(12))

# ---------------------------------------------------------------- 돌진 찌르기

THRUST_BACK = merge(pose(body={"r": [10, 20, 0], "p": [0, -1.2, 0]}, head={"r": [-6, -16, 0]}, rightArm={"r": [32, -24, 10]}, sword={"r": [tremble(-25, 1.2), 0, 0]},
                         leftArm={"r": [-78, 0, -8]}, rightLeg={"r": [22, 0, 0]}, leftLeg={"r": [-26, 0, 0]}), capes(8))
THRUST_OUT = merge(pose(body={"r": [22, -26, 0], "p": [0, -2, 0]}, head={"r": [-14, 22, 0]}, rightArm={"r": [-86, 0, -4]}, sword={"r": [84, 0, 0]},
                        leftArm={"r": [40, 0, -14]}, rightLeg={"r": [34, 0, 0]}, leftLeg={"r": [-38, 0, 0]}), capes(24))

# ---------------------------------------------------------------- 도약 내려찍기

LEAP_READY = merge(pose(body={"r": [16, 8, 0], "p": [0, -2.6, 0]}, head={"r": [-16, 0, 0]}, rightArm={"r": [tremble(-150), 0, 18]}, sword={"r": [-51, 0, 0]},
                        leftArm={"r": [-70, 0, -24]}, rightLeg={"r": [-34, 0, 0]}, leftLeg={"r": [26, 0, 0]}), capes(12))
LEAP_AIR = merge(pose(body={"r": [-14, 0, 0], "p": [0, 1, 0]}, head={"r": [-6, 0, 0]}, rightArm={"r": [-176, 0, 8]}, sword={"r": [55, 0, 0]},
                      leftArm={"r": [-162, 0, -14]}, rightLeg={"r": [-58, 0, 0]}, leftLeg={"r": [-16, 0, 0]}), capes(40))
SLAM = merge(pose(body={"r": [36, 0, 0], "p": [0, -3, 0]}, head={"r": [22, 0, 0]}, rightArm={"r": [-42, 0, 2]}, sword={"r": [71, 0, 0]}, leftArm={"r": [-44, 0, 8]},
                  rightLeg={"r": [-40, 0, 0]}, leftLeg={"r": [32, 0, 0]}), capes(10))

# ---------------------------------------------------------------- 회전 베기

SPIN_READY = merge(pose(body={"r": [8, 72, 0], "p": [0, -1.6, 0]}, head={"r": [-4, -60, 0]}, rightArm={"r": [-14, 0, tremble(78, 1.2)]}, sword={"r": [105, 0, 0]},
                        leftArm={"r": [-30, 0, -30]}, rightLeg={"r": [-20, 0, 10]}, leftLeg={"r": [20, 0, -10]}), capes(10, 12))
SPIN_END = merge(pose(body={"r": [14, -348, 0], "p": [0, -1.6, 0]}, head={"r": [0, 20, 0]}, rightArm={"r": [-14, 0, 70]}, sword={"r": [105, 0, 0]},
                      leftArm={"r": [-30, 0, -30]}, rightLeg={"r": [-20, 0, 10]}, leftLeg={"r": [20, 0, -10]}), capes(40, -20))
N_SPUN = pose(body={"r": [0, -360, 0]})

# ---------------------------------------------------------------- 균열 걸음

GONE = pose(body={"s": [0, 0, 0], "p": [0, -1.5, 0]}, rightLeg={"s": [0, 0, 0]}, leftLeg={"s": [0, 0, 0]})
BLINK_READY = merge(pose(body={"r": [6, 46, 0], "p": [0, -1.2, 0]}, head={"r": [0, -36, 0]}, rightArm={"r": [-40, 0, 58]}, sword={"r": [70, 0, 0]},
                         leftArm={"r": [-60, 0, -20]}, rightLeg={"r": [18, 0, 0]}, leftLeg={"r": [-20, 0, 0]}), capes(10))
BLINK_END = merge(pose(body={"r": [16, -48, 0], "p": [0, -1.6, 0]}, head={"r": [0, 30, 0]}, rightArm={"r": [-70, 0, -38]}, sword={"r": [80, 0, 0]},
                       leftArm={"r": [20, 0, -20]}, rightLeg={"r": [18, 0, 0]}, leftLeg={"r": [-20, 0, 0]}), capes(36))

# ---------------------------------------------------------------- 3연격

C1_READY = merge(pose(body={"r": [-4, 34, 0], "p": [0, -0.8, 0]}, head={"r": [0, -26, 0]}, rightArm={"r": [tremble(-128, 1.2), 0, 44]}, sword={"r": [-8, 0, 0]},
                      leftArm={"r": [-40, 0, -20]}, rightLeg={"r": [-14, 0, 0]}, leftLeg={"r": [12, 0, 0]}), capes(4))
C1_END = merge(pose(body={"r": [18, -30, 0], "p": [0, -1.4, 0]}, head={"r": [6, 24, 0]}, rightArm={"r": [-34, 0, -34]}, sword={"r": [52, 0, 0]},
                    leftArm={"r": [14, 0, -18]}, rightLeg={"r": [-26, 0, 0]}, leftLeg={"r": [18, 0, 0]}), capes(24))
C2_END = merge(pose(body={"r": [6, 40, 0], "p": [0, -1, 0]}, head={"r": [0, -30, 0]}, rightArm={"r": [-112, 0, 64]}, sword={"r": [26, 0, 0]},
                    leftArm={"r": [-30, 0, -30]}, rightLeg={"r": [16, 0, 0]}, leftLeg={"r": [-22, 0, 0]}), capes(18))

# ---------------------------------------------------------------- 각성 (2페이즈) · 공허 참 · 등장 · 죽음

CROUCH = merge(pose(body={"r": [20, 0, 0], "p": [0, -2, 0]}, head={"r": [20, 0, 0]}, rightArm={"r": [-20, 0, 20]}, leftArm={"r": [-20, 0, -20]},
                    rightLeg={"r": [-24, 0, 0]}, leftLeg={"r": [20, 0, 0]}), capes(12))
ROAR = merge(pose(body={"r": [-18, 0, 0], "p": [0, 0.6, 0]}, head={"r": [tremble(-22, 2, 2400), 0, 0]}, rightArm={"r": [-34, 0, 64]}, sword={"r": [-60, 0, 0]},
                  leftArm={"r": [-34, 0, -64]}, rightLeg={"r": [0, 0, 8]}, leftLeg={"r": [0, 0, -8]}), capes(tremble(40, 6, 1400)))
CROSS_READY = merge(pose(body={"r": [-10, 0, 0], "p": [0, 0.6, 0]}, head={"r": [-26, 0, 0]}, rightArm={"r": [tremble(-176), 0, -6]}, sword={"r": [108, 0, 0]},
                         leftArm={"r": [-172, 0, 8]}, rightLeg={"r": [0, 0, 10]}, leftLeg={"r": [0, 0, -10]}), capes(tremble(40, 8, 1200)))
CROSS_END = merge(pose(body={"r": [40, 0, 0], "p": [0, -4, 0]}, head={"r": [26, 0, 0]}, rightArm={"r": [-36, 0, 0]}, sword={"r": [56, 0, 0]},
                       leftArm={"r": [-36, 0, 6]}, rightLeg={"r": [-40, 0, 0]}, leftLeg={"r": [34, 0, 0]}), capes(30))
KNEEL = merge(pose(body={"r": [30, 0, 0], "p": [0, -4.5, 0]}, head={"r": [36, 0, 0]}, rightArm={"r": [-60, 0, 0]}, sword={"r": [135, 0, 0]},
                   leftArm={"r": [-20, 0, -6]}, rightLeg={"r": [-62, 0, 0], "p": [0, -3, 0]}, leftLeg={"r": [64, 0, 0], "p": [0, -4, 0]}), capes(18))
SALUTE = merge(pose(rightArm={"r": [-118, 0, -26]}, sword={"r": [43, 0, 0]}, leftArm={"r": [-14, 0, -8]}, head={"r": [4, 0, 0]}), capes(6))


def thin(p, k):
    """균열을 비집고 드나드는 모습: 가로로 가늘고 (k) 세로로 길게"""
    out = merge(p)
    for b in ("body", "rightLeg", "leftLeg"):
        out.setdefault(b, {})["s"] = [k, 1 + (1 - k) * 0.4, k]
    return out


def gone(p):
    out = merge(p)
    for b in ("body", "rightLeg", "leftLeg"):
        out.setdefault(b, {})["s"] = [0, 0, 0]
    return out


def anims():
    """새 동작 {이름: 동작} (ID 는 tools/void_knight.py 가 animation.rpg.boss.void_knight.<이름> 으로)"""
    a = {}
    # 옆걸음 (속성 rpg:strafe: -1 왼쪽 · +1 오른쪽 — 무게 = 크기): 다리가 옆으로 엇갈리고 가는 쪽으로 기울며 망토가 반대로
    sgn = "math.clamp(q.property('rpg:strafe') * 40, -1, 1)"
    ph = "q.life_time * 560"
    a["strafe"] = {"loop": True, "bones": {
        "rightLeg": {"rotation": [f"math.sin({ph}) * 10", 0, f"math.sin({ph}) * 14"]},
        "leftLeg": {"rotation": [f"-math.sin({ph}) * 10", 0, f"math.sin({ph}) * 14"]},
        "body": {"rotation": [6, f"-8 * {sgn}", f"-6 * {sgn}"], "position": [0, f"math.abs(math.sin({ph})) * 0.6 - 0.4", 0]},
        "rightArm": {"rotation": [-10, 0, 6]},
        "rightCape": {"rotation": [14, 0, f"10 * {sgn}"]},
        "leftCape": {"rotation": [14, 0, f"10 * {sgn}"]},
    }}
    # 달려듦 (멀리 있는 대상에게 · 끝 자세 유지) · 멈춤
    a["dash"] = seq([(0, N), (0.15, DASH)], loop="hold_on_last_frame")
    a["dash_stop"] = seq([(0, DASH), (0.2, N)])
    # 뒤로 뛰어 물러남 (너무 붙으면)
    a["backstep"] = seq([(0, N), (0.08, BACK1), (0.28, BACK2), (0.4, N)])
    # 내려베기 (3연격 마지막도): 머리 위로 들어 떪 → 내려침 (땅에 박힌 채 빈틈) → 일어남
    a["cleave_ready"] = seq([(0, N), (0.12, blend(N, CLEAVE_UP, 0.6)), (0.3, CLEAVE_UP)], loop="hold_on_last_frame")
    a["cleave"] = seq([(0, CLEAVE_UP), (0.06, blend(CLEAVE_UP, CLEAVE_DOWN, 0.6)), (0.12, CLEAVE_DOWN)], loop="hold_on_last_frame")
    a["cleave_rec"] = seq([(0, CLEAVE_DOWN), (0.2, blend(CLEAVE_DOWN, N, 0.65)), (0.45, N)])
    # 돌진 찌르기: 칼을 뒤로 당겨 겨눔 → 앞으로 뻗음 (몸은 서버가 줄을 따라 옮김) → 거둠
    a["thrust_ready"] = seq([(0, N), (0.12, blend(N, THRUST_BACK, 0.6)), (0.3, THRUST_BACK)], loop="hold_on_last_frame")
    a["thrust"] = seq([(0, THRUST_BACK), (0.1, THRUST_OUT)], loop="hold_on_last_frame")
    a["thrust_rec"] = seq([(0, THRUST_OUT), (0.35, N)])
    # 도약 내려찍기: 웅크려 칼을 뒤로 → 공중 (칼을 머리 위로) → 내려찍음 (빈틈) → 일어남
    a["leap_ready"] = seq([(0, N), (0.25, LEAP_READY)], loop="hold_on_last_frame")
    a["leap_air"] = seq([(0, LEAP_READY), (0.3, LEAP_AIR)], loop="hold_on_last_frame")
    a["slam"] = seq([(0, LEAP_AIR), (0.1, SLAM)], loop="hold_on_last_frame")
    a["slam_rec"] = seq([(0, SLAM), (0.25, blend(SLAM, N, 0.6)), (0.5, N)])
    # 회전 베기: 몸을 비틀어 칼을 옆으로 → 한 바퀴 넘게 돎 → 풀림 (몸 Y -360 = 기본과 같은 모습)
    a["spin_ready"] = seq([(0, N), (0.3, SPIN_READY)], loop="hold_on_last_frame")
    a["spin"] = seq([(0, SPIN_READY), (0.36, SPIN_END)], loop="hold_on_last_frame")
    a["spin_rec"] = seq([(0, SPIN_END), (0.45, N_SPUN)])
    # 균열 걸음: 가늘어지며 균열로 사라짐 → (보이지 않음) → 대상 등 뒤 균열에서 비집고 나와 칼을 겨눔 → 가로 베기 → 거둠
    a["vanish"] = seq([(0, N), (0.1, merge(CROUCH, {"body": {"s": [1.15, 0.85, 1.15]}})), (0.22, thin(CROUCH, 0.15)), (0.3, gone(CROUCH))],
                      loop="hold_on_last_frame")
    a["appear"] = seq([(0, gone(BLINK_READY)), (0.08, thin(BLINK_READY, 0.15)), (0.2, merge(BLINK_READY, {"body": {"s": [1.1, 0.92, 1.1]}})),
                       (0.28, BLINK_READY)], loop="hold_on_last_frame")
    a["blink_slash"] = seq([(0, BLINK_READY), (0.14, BLINK_END)], loop="hold_on_last_frame")
    a["blink_rec"] = seq([(0, BLINK_END), (0.35, N)])
    # 3연격: 오른쪽 위에서 비스듬히 → 거슬러 올려 벰 → (머리 위로 — 셋째는 내려베기 cleave · cleave_rec)
    a["combo1_ready"] = seq([(0, N), (0.25, C1_READY)], loop="hold_on_last_frame")
    a["combo1"] = seq([(0, C1_READY), (0.12, C1_END)], loop="hold_on_last_frame")
    a["combo2"] = seq([(0, C1_END), (0.12, C2_END)], loop="hold_on_last_frame")
    a["combo3_ready"] = seq([(0, C2_END), (0.25, CLEAVE_UP)], loop="hold_on_last_frame")
    # 각성 (체력 절반): 웅크렸다가 두 팔을 벌리고 포효
    a["roar"] = seq([(0, N), (0.3, CROUCH), (0.6, ROAR), (2.0, ROAR), (2.4, N)])
    # 공허 참: 칼을 하늘로 세워 떪 → 내리그음 (무릎 꿇듯 빈틈) → 일어남
    a["cross_ready"] = seq([(0, N), (0.3, blend(N, CROSS_READY, 0.7)), (0.6, CROSS_READY)], loop="hold_on_last_frame")
    a["cross"] = seq([(0, CROSS_READY), (0.06, blend(CROSS_READY, CROSS_END, 0.6)), (0.12, CROSS_END)], loop="hold_on_last_frame")
    a["cross_rec"] = seq([(0, CROSS_END), (0.3, blend(CROSS_END, N, 0.6)), (0.6, N)])
    # 등장: 균열에서 가늘게 비집고 나와 → 무릎 꿇은 자세로 부풀고 → 일어나 칼을 얼굴 앞에 세움 (예) → 기본
    a["spawn"] = seq([(0, gone(KNEEL)), (0.12, thin(KNEEL, 0.12)), (0.4, KNEEL), (0.9, blend(KNEEL, N, 0.8)), (1.05, N), (1.3, SALUTE), (1.6, SALUTE), (1.85, N)])
    # 죽음 (1초 — 바닐라가 1초 뒤 치움): 무릎 꿇고 칼을 꽂음 → 가늘어져 균열로 빨려 듦
    a["die"] = seq([(0, N), (0.3, KNEEL), (0.55, KNEEL), (0.85, thin(KNEEL, 0.1)), (1.0, gone(KNEEL))], loop="hold_on_last_frame")
    return a
