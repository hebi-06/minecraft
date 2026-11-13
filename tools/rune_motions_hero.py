"""2026-11-13 영웅 · 초월 룬 동작 다시 (사용자 "걷기는 안할래 이재 다른 룬들도 모션하고 이펙트 추가하자" → 고른 것: 초월 · 영웅 먼저 ·
"새 연출에 맞춰 다시 만듦"): 새 3D 연출 (룬 전용 검 2판 · 3D 물체 · 몹) 에 맞춘 큰 동작. 피해 틱은 그대로 (RpgRuneTimingTests).

뻣뻣하지 않게 (직업 2차 동작 job_motions_d 와 같은 기준 — 그 파일은 jb_ 동작을 만들므로 가져오지 않고 쓰는 조각만 여기에 옮겨 둠):
- 치기 전에 반대쪽으로 크게 감고 (몸을 비틀고 무릎을 굽혀 낮아짐) 치는 틱에 뿌리가 앞으로 나가며 (내디딤) 몸이 반대쪽까지 돌아감
- 친 뒤 지나쳐 멈췄다가 조금 되돌아옴 · 왼팔은 반대로 흔들려 몸의 무게를 받음
- 멈춘 자세도 숨 쉬듯 조금씩 움직임 (같은 자세를 오래 두지 않음)
틱 0 = 룬이 나간 틱 (첫 피해가 0 인 룬은 감는 틈이 없음 — 0 에서 바로 감은 자세로 들어가 1틱 남짓에 벰).
"""
import copy

import rune_motion_kit as K
from rune_motion_kit import (A_CHEST, A_CROSS, A_GUARD, A_HIP, A_PALM, A_RAISE, A_SPREAD_DOWN, A_SPREAD_UP, BACK_END, BACK_HIT, BACK_WIND, FP_CHAMBER,
                             FP_LOW, FP_PALM, FP_RAISE, FPW_BACK, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_UP, L, LEGS_FIGHT, LEGS_WIDE, P, W_READY, W_SKY,
                             W_UP_FRONT, arm, backhand, bent, fpl, fpw, graft, keyp, lerpP, nudge, spin_right, strike, use)

Motion = K.Motion

# ------------------------------------------------------------ 큰 동작 조각 (job_motions_d 에서 옮김)

LEG_KEYS = ("body", "rl", "ll", "rk", "lk", "root", "head")
LUNGE_DEEP = dict(rl=[32, 0, 4], ll=[-48, 0, -2], rk=22, lk=54)  # 낮고 넓게 내디딘 다리 (왼발 앞)
BRACE = dict(rl=[30, 0, 8], ll=[-26, 0, -8], rk=60, lk=54)  # 무릎을 깊이 굽혀 버팀


def strike_with(m, name, wind_tick, hit_tick, end_tick, a, b):
    """다듬어 둔 베기 name 을 옮겨 쓰고 (팔 · 1인칭 · 궤적 그대로) 새 열쇠의 몸 · 다리 · 뿌리 · 머리를 a (준비) → b (끝) 사이로 덮음 — 더 크게 감고 내디딤"""
    before = set(m.keys)
    strike(m, name, wind_tick, hit_tick, end_tick=end_tick)
    t0, t1 = m.t(wind_tick), m.t(end_tick)
    for t, k in m.keys.items():
        if t in before or not (t0 - 1e-9 <= t <= t1 + 1e-9):
            continue
        s = 0.0 if t1 <= t0 else (t - t0) / (t1 - t0)
        mix = lerpP(a, b, s)
        for f in LEG_KEYS:
            if f in mix:
                k[f] = mix[f]


def breathe(m, t0, t1, pose, step=2.5, lean=1.5, knee=3):
    """t0 ~ t1 동안 같은 자세를 숨 쉬듯 (숙임 · 무릎이 조금씩 오르내림)"""
    n = max(1, int((t1 - t0) / step))
    for i in range(n + 1):
        tick = t0 + (t1 - t0) * i / n
        s = 1 if i % 2 else -1
        b = list(pose.get("body", [0, 0, 0]))
        q = P(pose, body=[b[0] + s * lean, b[1], b[2]])
        if "rk" in pose:
            q["rk"] = pose["rk"] + s * knee
        if "lk" in pose:
            q["lk"] = pose["lk"] + s * knee
        keyp(m, tick, q)


def fp_each(m, ticks, pre, dp_fn):
    for i, tick in enumerate(ticks):
        fpw(m, tick, pre, dp=dp_fn(i))


# ------------------------------------------------------------ 검사 · 민첩 · 마검사 · 평타 강화 (룬 전용 검 2판)

# 삼단베기 (효과: 0 가로 — 3D 검이 오른쪽 앞에서 왼쪽 뒤로 휩씀 · 10 세로 — 하늘 뒤에서 앞 땅까지 · 20 찌르기 — 칼끝이 찌르는 끝까지 날아감):
# 0 에서 바로 오른쪽으로 감은 자세 → 크게 내디디며 가로로 휩쓸고 몸이 왼쪽 끝까지 → 일어서며 칼을 머리 위 뒤로 젖혀 발끝으로 섬 (몸이 뒤로 휨) →
# 깊이 내디디며 세로로 내려침 (칼끝이 땅까지) → 칼을 오른 허리 뒤로 당기고 왼손으로 앞을 겨눔 (무게는 뒷발) → 몸을 던지듯 깊이 내디디며 찌름 → 뻗은 채 숨 → 거둠
m = Motion("blade_triple", 32, "삼단베기 — 크게 내디디며 가로로 휩쓸고, 칼을 머리 위 뒤로 젖혔다가 깊이 내려치고, 칼을 허리 뒤로 당겨 왼손으로 겨눈 뒤 몸을 던지듯 찌름",
           fade=(0.02, 0.1))
strike_with(m, "slash_h", 0.0, 1.3, 3.6, a=P(body=[6, 48, 0], root=[-0.18, -0.05], rl=[22, 0, 4], ll=[-26, 0, -4], rk=36, lk=40),
            b=P(body=[14, -52, 0], root=[-0.3, 0.5], **LUNGE_DEEP))
keyp(m, 5.0, P(body=[10, -40, 0], ra=arm([-0.75, 0.1, 0.45], 25, [-0.8, 0.0, -0.4]), la=arm([-0.45, -0.7, -0.2], 30), root=[-0.24, 0.42], **LUNGE_DEEP))
OVER_ARCH = P(body=[-20, 8, 0], head=[-14, 0], ra=arm([0.16, 0.95, -0.25], 40, [0.05, 0.4, -0.92]), la=arm([-0.35, 0.25, 0.9], 8), rl=[6, 0, 2], ll=[-8, 0, -2],
              rk=6, lk=10, root=[0.08, 0.1])  # 칼을 머리 위 뒤로 젖히고 발끝으로 섬 (왼손은 앞을 겨눔)
keyp(m, 7.6, OVER_ARCH, "out")
strike_with(m, "slash_v", 8.3, 10.2, 12.6, a=P(body=[-22, 6, 0], head=[-14, 0], root=[0.08, 0.1], rl=[6, 0, 2], ll=[-8, 0, -2], rk=6, lk=10),
            b=P(body=[38, -4, 0], head=[8, 0], root=[-0.36, 0.62], **LUNGE_DEEP))
keyp(m, 14.0, P(body=[34, -6, 0], head=[6, 0], ra=arm([0.05, -0.75, 0.66], 10, [0.0, -0.9, 0.4]), la=arm([-0.5, -0.6, -0.1], 30), root=[-0.34, 0.6],
                **LUNGE_DEEP))
TH_WIND = P(body=[2, 44, 0], head=[0, -30], ra=arm([0.5, -0.3, -0.8], 100, [0.0, 0.08, 1.0]), la=arm([-0.2, 0.12, 1.0], 4, [0, 0.1, 1]), rl=[26, 0, 4],
            ll=[-18, 0, -4], rk=44, lk=18, root=[-0.2, -0.2])  # 칼을 오른 허리 뒤로 당기고 왼손으로 앞을 겨눔 (무게는 뒷발)
keyp(m, 16.6, TH_WIND, "out")
keyp(m, 18.2, P(TH_WIND, body=[3, 50, 0], rk=50, root=[-0.24, -0.26]))
strike_with(m, "thrust", 18.4, 20.1, 22.4, a=P(body=[3, 50, 0], root=[-0.24, -0.26], rl=[26, 0, 4], ll=[-18, 0, -4], rk=50, lk=18),
            b=P(body=[26, -30, 0], head=[4, 0], root=[-0.34, 0.85], **LUNGE_DEEP))
keyp(m, 25.0, P(body=[24, -28, 0], head=[4, 0], ra=arm([-0.1, 0.05, 1.0], 4, [0, 0.05, 1]), la=arm([-0.55, -0.45, -0.6], 25), root=[-0.32, 0.8],
                **LUNGE_DEEP))
keyp(m, 28.5, P(W_READY, body=[8, 0, 0], root=[-0.06, 0.3]))
fpw(m, 5.0, FPW_LOW, dp=(-0.6, 0.1, 0))
fpw(m, 7.6, FPW_SKY, "out")
fpw(m, 14.0, FPW_DOWN)
fpw(m, 16.6, FPW_BACK, "out")
fpw(m, 18.2, FPW_BACK, dp=(0.08, -0.05, 0.1))
fpw(m, 25.0, FPW_FWD)
fpw(m, 28.5, FPW_LOW)
use(m, "tripleCut")
use(m, "triElement")  # 2026-11-13 삼원소 연격도 같은 때 (가로 0 · 세로 10 · 찌르기 20) 의 3D 원소 검 (RuneBlade.tripleSword — 원소마다 색)

# 천지개벽 (효과: 0 ~ 7 오른쪽 땅에서 거대한 3D 검이 솟음 · 9 ~ 18 뒤로 눕혀 오른쪽 뒤로 당겨짐 (떨림) · 18 ~ 23 몸 앞을 가로로 휩씀 — 20 거대한 검기):
# 오른발을 뒤로 빼며 두 손으로 무기를 오른쪽 뒤 낮게 끌어 (거대한 검과 함께) 몸을 끝까지 감고 낮아짐 → 떨리며 더 감김 → 폭발하듯 깊이 내디디며 가로로 휩쓸고
# 몸이 왼쪽 끝까지 돌아감 → 뻗은 채 멈춤 (숨) → 일어섬
COIL = P(body=[10, 62, 0], head=[2, -55], ra=arm([0.7, -0.45, -0.55], 30, [0.35, -0.2, -0.9]), la=arm([0.45, -0.55, 0.4], 70), rl=[26, 0, 6], ll=[-34, 0, -4],
         rk=44, lk=46, root=[-0.2, -0.12])
m = Motion("heaven_split", 34, "천지개벽 — 오른발을 빼며 두 손으로 무기를 오른쪽 뒤 낮게 끌어 몸을 끝까지 감고 떨며 기를 모으다가, 폭발하듯 깊이 내디디며 거대하게 "
           "가로로 휩쓸어 몸이 왼쪽 끝까지 돎", fade=(0.03, 0.08))
keyp(m, 3.0, COIL, "out3")
for i, tick in enumerate((6.0, 8.5, 11.0, 13.5, 16.0)):
    s = 1 if i % 2 else -1
    q = i / 4.0
    keyp(m, tick, P(COIL, body=[10 + 2 * q + s * 1.2, 62 + 12 * q, s * 1.0], rk=44 + 10 * q, lk=46 + 10 * q, root=[-0.2 - 0.1 * q, -0.12 - 0.04 * q]))
keyp(m, 18.4, P(COIL, body=[13, 80, 0], rk=58, lk=60, root=[-0.32, -0.18]), "in")
strike_with(m, "skill_slash", 18.6, 20.2, 22.8, a=P(body=[13, 80, 0], head=[2, -55], root=[-0.32, -0.18], rl=[26, 0, 6], ll=[-34, 0, -4], rk=58, lk=60),
            b=P(body=[16, -74, 0], head=[4, 0], root=[-0.4, 0.8], **LUNGE_DEEP))
FIN = P(body=[14, -70, 0], head=[4, 10], ra=arm([-0.85, 0.05, 0.4], 14, [-0.7, -0.1, -0.6]), la=arm([0.4, -0.45, -0.7], 25), root=[-0.38, 0.76], **LUNGE_DEEP)
keyp(m, 24.4, FIN, "out")
breathe(m, 25.5, 28.5, FIN, step=1.5, lean=1.2, knee=2)
keyp(m, 31.5, P(W_READY, body=[8, -10, 0], root=[-0.08, 0.3]))
fpw(m, 3.0, FPW_BACK, "out3")
fp_each(m, (6.0, 8.5, 11.0, 13.5, 16.0), FPW_BACK, lambda i: (0.04 * i + (0.015 if i % 2 else -0.015), -0.03 * i, 0.03 * i))
fpw(m, 18.4, FPW_BACK, "in", dp=(0.2, -0.14, 0.14))
fpw(m, 24.4, FPW_LOW, dp=(-0.9, 0.05, 0))
fpw(m, 31.5, FPW_LOW)
use(m, "heavenSplit")

# 칼바람 폭풍 (효과: 0 · 10 · 20 · 30 · 40 몸 둘레를 벰 — 3D 검 넷이 몸 둘레를 돌다가 벨 때마다 바깥으로 튐, 끝 46 에 날아감):
# 벨 때마다 무기를 뻗은 채 몸째 오른쪽으로 한 바퀴씩 (다섯 바퀴 — 회오리) · 점점 낮게 → 마지막에 낮게 멈춰 무기를 털어냄
m = Motion("blade_storm", 48, "칼바람 폭풍 — 무기를 뻗은 채 몸째 오른쪽으로 벨 때마다 한 바퀴씩 다섯 바퀴 휘돌며 점점 낮아지고, 낮게 멈춰 무기를 털어냄",
           fade=(0.02, 0.08))
SPIN_LOW = P(K.SPIN_FWD_R, body=[14, 4, 0], rk=40, lk=40, root=[-0.2, 0])
spin_right(m, (1.0, 10.0, 20.0, 30.0, 40.0), 43.5, wind_tick=0.2, fwd=SPIN_LOW,
           end_pose=P(K.SPIN_END_R, body=[18, 22, 0], rk=46, lk=40, root=[-0.24, 0.1]))
keyp(m, 46.0, P(body=[12, 16, 0], ra=arm([0.55, -0.7, 0.45], 20, [0.4, -0.85, 0.35]), la=arm([-0.5, -0.6, -0.2], 30), rk=30, lk=26, root=[-0.14, 0.05],
                spin=-1800))
use(m, "bladeStorm")

# 무영검 (효과: 0 시간이 멈춤 · 2 ~ 22 적 둘레에 3D 검이 하나씩 · 26 한꺼번에 꽂힘): 0 에서 바로 낮게 감은 발도 자세 → 크게 내디디며 왼쪽에서 오른쪽으로 뽑아 벰 →
# 베어 낸 자세 (깊이 내디딘 채) 로 멈춰 숨을 고름 (시간이 멈춘 동안 — 아주 조금씩) → 천천히 일어서며 칼을 왼 허리 칼집으로 가져가 (23) 밀어 넣는 순간 (26 — 딸깍) 터짐
# → 숨을 내쉬며 섬
IAI_COIL = P(body=[28, -60, 0], ra=arm([-0.3, -0.7, 0.6], 70, [-0.6, -0.2, -0.75]), la=arm([-0.35, -0.8, 0.3], 40), rl=[24, 0, 4], ll=[-34, 0, -2], rk=60,
             lk=78, root=[-0.42, -0.04])
DRAW_HIT2 = P(body=[14, 30, 0], ra=arm([0.75, 0.05, 0.62], 4, [0.9, -0.05, 0.4]), la=arm([-0.6, -0.5, -0.4], 30), root=[-0.26, 0.55], **LUNGE_DEEP)
DRAW_END2 = P(body=[12, 56, 0], ra=arm([0.85, -0.2, 0.25], 14, [0.55, -0.45, -0.7]), la=arm([-0.6, -0.5, -0.4], 30), root=[-0.28, 0.52], **LUNGE_DEEP)
ZAN2 = P(body=[12, 54, 0], head=[2, -30], ra=arm([0.7, -0.45, 0.4], 20, [0.6, -0.6, -0.5]), la=arm([-0.5, -0.6, -0.2], 30), root=[-0.3, 0.5], **LUNGE_DEEP)
NOTO_A = P(body=[6, -16, 0], head=[6, 0], ra=arm([-0.3, -0.6, 0.72], 62, [-0.25, -0.32, 0.91]), la=arm([-0.36, -0.86, 0.3], 30), rl=[10, 0, 4],
           ll=[-12, 0, -4], rk=14, lk=16, root=[-0.08, 0.16])  # 칼끝을 왼 허리 칼집 어귀로
NOTO_B = P(NOTO_A, body=[4, -20, 0], head=[2, 0], ra=arm([-0.45, -0.74, 0.36], 70, [-0.55, -0.16, -0.82]), root=[-0.06, 0.08])  # 밀어 넣음 (딸깍)
FPW_SHEATHED = dict(p=(-0.75, -0.62, 0.35), blade=(-0.4, -0.3, -0.85))
FPW_ZAN = dict(p=(0.4, -0.25, 0.2), blade=(0.7, -0.3, -0.6))
m = Motion("shadowless", 36, "무영검 — 낮게 감았다가 크게 내디디며 뽑아 베고, 베어 낸 자세로 멈춰 숨을 고르다가, 천천히 일어서며 칼을 칼집에 밀어 넣는 순간 모든 칼이 꽂힘",
           fade=(0.02, 0.08))
backhand(m, 0.0, 1.3, 2.6, wind=IAI_COIL, hit=DRAW_HIT2, end=DRAW_END2)
keyp(m, 4.5, ZAN2, "out")
breathe(m, 6.0, 20.0, ZAN2, step=3.5, lean=0.8, knee=1.5)
keyp(m, 23.2, NOTO_A, "inout")
keyp(m, 25.8, NOTO_B, "in")
keyp(m, 28.0, P(NOTO_B, body=[-4, -16, 0], head=[-6, 0], root=[-0.02, 0.06]), "out")
keyp(m, 32.5, P(W_READY, body=[4, 0, 0]))
fpw(m, 4.5, FPW_ZAN)
fpw(m, 20.0, FPW_ZAN, dp=(0.02, -0.02, 0))
fpw(m, 23.2, FPW_SHEATHED, "inout")
fpw(m, 25.8, FPW_SHEATHED, "in", dp=(-0.05, -0.04, 0.05))
fpw(m, 32.5, FPW_LOW)
use(m, "shadowless")

# 마검 결계 (효과: 0 ~ 80 3D 검 넷이 몸 둘레를 돎 · 10틱마다 바깥으로 튐): 한 발 물러서며 무기를 얼굴 앞에 세우고 (예) → 왼손을 뻗어 몸 둘레를 크게 한 바퀴 그리며
# 몸도 손을 따라 돎 → 무기를 앞 위로 크게 내뻗어 검들에게 명령 · 왼손을 펴 내밂 → 싸울 자세
m = Motion("orbit_blades", 22, "마검 결계 — 물러서며 무기를 얼굴 앞에 세우고, 왼손을 뻗어 몸 둘레를 크게 그리며 몸도 따라 돌고, 무기를 앞 위로 내뻗어 검들을 부림",
           fade=(0.03, 0.1))
m.set(hands={"main": "weapon", "off": "fist"})
SALUTE = P(W_UP_FRONT, body=[-8, 0, 0], head=[-8, 0], rl=[14, 0, 4], ll=[-6, 0, -4], rk=24, lk=10, root=[-0.1, -0.2])
keyp(m, 2.5, SALUTE, "out3")
for tick, d, tw in ((4.5, [-0.95, 0.15, 0.25], -26), (6.5, [-0.45, 0.25, -0.85], -40), (8.5, [0.5, 0.3, -0.8], 10), (10.5, [0.9, 0.2, 0.35], 34),
                    (12.0, [0.1, 0.25, 0.97], 10)):
    keyp(m, tick, P(SALUTE, body=[-6, tw, 0], la=arm(d, 6)))
COMMAND = P(body=[10, -18, 0], head=[-6, 0], ra=arm([-0.05, 0.45, 0.9], 2, [0, 0.55, 0.84]), la=arm([-0.35, 0.0, 0.94], 0, [0, 1, 0.1]), rl=[-8, 0, 4],
            ll=[22, 0, -4], rk=10, lk=34, root=[-0.12, 0.3])  # 무기를 앞 위로 내뻗음 · 왼손을 펴 내밂
keyp(m, 14.0, COMMAND, "out")
keyp(m, 17.0, P(COMMAND, body=[8, -16, 0]))
keyp(m, 20.5, P(W_READY, body=[8, 0, 0]))
fpw(m, 2.5, FPW_UP, "out3")
fpw(m, 12.0, FPW_UP)
fpw(m, 14.0, FPW_FWD, "out", dp=(0, 0.25, 0))
fpw(m, 17.0, FPW_FWD, dp=(0, 0.2, 0.05))
fpw(m, 20.5, FPW_LOW)
fpl(m, 2.5, FP_LOW)
fpl(m, 4.5, ((0.35, 0.1, -0.1), (20, -35, 0)))
fpl(m, 6.5, ((0.3, 0.3, 0.05), (50, -20, 0)))
fpl(m, 8.5, ((-0.05, 0.25, -0.2), (40, 30, 0)))
fpl(m, 10.5, ((-0.2, 0.08, -0.3), (20, 55, 0)))
fpl(m, 14.0, FP_PALM, "out")
fpl(m, 17.0, FP_PALM)
fpl(m, 20.5, FP_LOW)
use(m, "orbitBlades")

# 피의 칼날 (효과: 0 ~ 9 눈앞의 핏빛 3D 검이 바람개비처럼 돌며 피를 빨아들임 · 11 ~ 14 손으로 날아들어 스밈): 무기를 가로로 눈앞에 들고 왼손바닥을 날에 대고 →
# 날을 따라 손바닥을 그어 피를 냄 (피의 맹세) → 피 묻은 주먹을 꽉 쥐어 가슴으로 당기며 무기를 오른쪽 아래로 털고 고개를 숙임 → (검이 스밀 때) 웅크렸다가 고개를 들며 섬
m = Motion("blood_blade", 20, "피의 칼날 — 무기를 가로로 들어 손바닥으로 날을 그어 피를 내고, 피 묻은 주먹을 가슴으로 당기며 고개를 숙였다가, 핏빛 검이 스밀 때 웅크렸다 일어섬",
           fade=(0.03, 0.1))
m.set(hands={"main": "weapon", "off": "fist"})
OATH = P(body=[2, -10, 0], head=[10, 0], ra=arm([0.25, -0.2, 0.95], 85, [-0.95, 0.1, 0.2]), la=bent([0.25, -0.25, 0.92], 70, (0.3, 1, 0)), rl=[10, 0, 4],
         ll=[-10, 0, -4], rk=12, lk=12)  # 무기를 가로로 눈앞에 · 왼손바닥을 날 밑에
keyp(m, 2.5, OATH, "out3")
keyp(m, 6.5, P(OATH, body=[4, -18, 0], la=bent([-0.45, -0.2, 0.86], 40, (0.3, 1, 0))), "inout")  # 손바닥이 날을 따라 칼끝 쪽으로
keyp(m, 9.5, P(body=[14, 12, 0], head=[22, 0], ra=arm([0.55, -0.75, 0.3], 20, [0.4, -0.85, 0.3]), la=L(A_CHEST), rl=[16, 0, 4], ll=[-14, 0, -4], rk=24,
                lk=20, root=[-0.08, 0]), "out3")
keyp(m, 12.0, P(body=[22, 8, 0], head=[18, 0], ra=arm([0.55, -0.75, 0.3], 20, [0.4, -0.85, 0.3]), la=L(A_CHEST), rk=50, lk=50, root=[-0.24, 0]))
keyp(m, 15.0, P(body=[-10, 6, 0], head=[-16, 0], ra=arm([0.6, -0.55, 0.45], 25, [0.5, -0.4, 0.75]), la=bent([-0.4, -0.55, 0.5], 110, (0.3, 1, 0.2)),
                rl=[6, 0, 6], ll=[-6, 0, -6], rk=8, lk=8, root=[0.02, 0]), "out")
keyp(m, 18.5, P(W_READY, body=[8, 0, 0]))
fpw(m, 2.5, FPW_UP, "out3", dp=(-0.1, -0.2, 0))
fpw(m, 6.5, FPW_UP, dp=(-0.1, -0.2, 0))
fpw(m, 9.5, FPW_DOWN, "out3")
fpw(m, 15.0, FPW_LOW)
fpl(m, 2.5, ((-0.25, -0.05, -0.05), (20, 60, 0)), "out3")
fpl(m, 6.5, ((-0.45, 0.0, -0.1), (25, 70, 0)))
fpl(m, 9.5, FP_CHAMBER, "out3")
fpl(m, 15.0, FP_LOW)
use(m, "bloodBlade")

# 검신 강림 (효과: 0 ~ 5 하늘 13칸에서 거대한 3D 금빛 검이 떨어짐 · 6 꽂힘 (충격) · 그 뒤 어깨 위에 3D 금빛 검 둘): 무기와 왼손을 하늘로 치켜들고 발끝으로 서서
# 하늘을 봄 → 검이 꽂히는 순간 깊이 웅크려 왼팔로 얼굴을 막고 버팀 → 천천히 일어나며 가슴을 펴고 무기를 옆으로 늘어뜨린 채 앞을 봄 (검신)
m = Motion("sword_god", 24, "검신 강림 — 무기와 왼손을 하늘로 치켜들어 거대한 검을 부르고, 검이 꽂히는 충격에 깊이 웅크려 버틴 뒤, 가슴을 펴고 천천히 일어섬",
           fade=(0.03, 0.1))
CALL_UP = P(W_SKY, body=[-16, 0, 0], head=[-32, 0], la=L(A_RAISE), rl=[0, 0, 8], ll=[0, 0, -8], rk=4, lk=4, root=[0.08, 0])
keyp(m, 3.0, CALL_UP, "out3")
keyp(m, 5.2, P(CALL_UP, body=[-18, 0, 0], root=[0.1, 0]))
BRACED = P(body=[26, 0, 0], head=[14, 0], ra=arm([0.35, -0.55, 0.55], 70, [0.1, 0.6, 0.8]), la=bent([0.15, 0.1, 0.95], 120, (-1, 0.3, 0)), root=[-0.36, -0.1],
           **BRACE)  # 깊이 웅크려 왼팔로 얼굴을 막고 버팀
keyp(m, 6.6, BRACED, "in")
keyp(m, 9.0, P(BRACED, body=[22, 0, 0], root=[-0.32, -0.08]))
GODSTAND = P(body=[-8, 0, 0], head=[-6, 0], ra=arm([0.45, -0.85, 0.2], 10, [0.3, -0.9, 0.3]), la=arm([-0.4, -0.88, 0.1], 10), rl=[0, 0, 8], ll=[0, 0, -8],
             rk=6, lk=6, root=[0.02, 0])  # 가슴을 펴고 무기를 옆으로 늘어뜨림
keyp(m, 14.0, GODSTAND, "inout")
keyp(m, 18.0, P(GODSTAND, body=[-10, 0, 0], head=[-8, 0]))
keyp(m, 22.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 5.2, FPW_SKY)
fpw(m, 6.6, FPW_LOW, "in", dp=(0, -0.1, 0))
fpw(m, 14.0, FPW_LOW)
use(m, "swordGod")


# ------------------------------------------------------------ 궁수 · 암살자 · 사신 (2026-11-13 묶음 B — 3D 빛 화살 · 하늘의 활 · 그림자 분신 · 핏빛 낫)

DRAW = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140))  # 활을 앞으로 · 시위를 뺨까지

# 저격 (효과: 0 ~ 16 손 앞의 3D 빛 화살이 길어지며 당겨짐 · 16 쏨 — 날아가 꽂힘): 한쪽 무릎을 꿇으며 활을 들어 끝까지 천천히 당기고 (몸을 겨눔 쪽으로 기울이고 숨을 참음 —
# 아주 조금 떨림) → 놓는 순간 당긴 손이 뒤로 튕기고 몸이 반동으로 젖혀짐 → 그대로 겨눈 채 멈췄다가 일어섬
m = Motion("snipe", 30, "저격 — 한쪽 무릎을 꿇으며 활을 들어 끝까지 천천히 당기고 숨을 참았다가, 놓는 순간 손이 뒤로 튕기고 몸이 반동으로 젖혀짐", fade=(0.03, 0.1))
AIM = P(DRAW, body=[8, -26, 0], head=[4, 0], **K.LEGS_KNEEL)
keyp(m, 3.5, P(AIM, la=arm([0.3, 0.1, 0.4], 120)), "out")
keyp(m, 8.0, P(AIM, la=arm([0.22, 0.06, -0.12], 142)))
keyp(m, 12.0, P(AIM, body=[9, -27, 0.6], la=arm([0.2, 0.05, -0.22], 148)))
keyp(m, 15.4, P(AIM, body=[10, -27, -0.6], la=arm([0.2, 0.05, -0.25], 150)))
REL = P(AIM, body=[-8, -18, 0], head=[-4, 0], ra=arm([0.05, 0.22, 1], 0, [0, 1, -0.2]), la=arm([-0.45, 0.1, -0.75], 25), root=[-0.36, -0.14])
keyp(m, 16.6, REL, "out3")
keyp(m, 22.0, P(REL, body=[-2, -18, 0], la=arm([-0.35, -0.25, -0.6], 35), root=[-0.34, -0.1]))
keyp(m, 27.5, P(W_READY, body=[4, -10, 0]))
m.fp(3.5, (-0.28, 0.06, -0.02), ease="out")
m.fp(15.4, (-0.28, 0.07, -0.02))
m.fp(16.6, (-0.26, 0.14, 0.14), ease="out3")
m.fp(22.0, (-0.26, 0.1, 0.08))
m.fp(27.5, (0, 0, 0))
use(m, "snipe")

# 천궁 (효과: 0 ~ 16 앞쪽 하늘의 3D 활이 시위를 당김 · 16 하늘로 쏨 · 20 ~ 하늘에서 빛 화살이 떨어짐): 다리를 넓게 벌리고 몸을 뒤로 젖혀 하늘을 향해 활을 들고
# 하늘의 활과 함께 천천히 끝까지 당김 (점점 더 젖혀짐) → 하늘로 놓으며 발끝으로 솟음 → 화살이 떨어지는 하늘을 올려다보며 섬
m = Motion("sky_bow", 30, "천궁 — 다리를 넓게 벌리고 몸을 뒤로 젖혀 하늘을 향해 하늘의 활과 함께 끝까지 당기고, 하늘로 놓으며 발끝으로 솟아 떨어지는 화살을 올려다봄",
           fade=(0.03, 0.1))
SKY_AIM = P(body=[-18, -20, 0], head=[-30, 0], ra=arm([0.05, 0.85, 0.55], 0, [0, 1, -0.3]), la=arm([0.3, 0.55, 0.2], 120), rl=[6, 0, 12], ll=[-6, 0, -12],
            rk=20, lk=20, root=[-0.1, 0])
keyp(m, 3.0, SKY_AIM, "out3")
for i, tick in enumerate((6.5, 10.0, 13.5)):
    q = (i + 1) / 3.0
    keyp(m, tick, P(SKY_AIM, body=[-18 - 8 * q, -20, 0], head=[-30 - 6 * q, 0], la=arm([0.25, 0.42 - 0.12 * q, -0.1 - 0.15 * q], 135 + 10 * q),
                    rk=20 + 12 * q, lk=20 + 12 * q, root=[-0.1 - 0.08 * q, 0]))
keyp(m, 15.6, P(SKY_AIM, body=[-27, -20, 0], head=[-37, 0], la=arm([0.24, 0.28, -0.26], 146), rk=34, lk=34, root=[-0.2, 0]), "in")
SKY_REL = P(body=[-20, -14, 0], head=[-34, 0], ra=arm([0.05, 0.95, 0.3], 0, [0, 1, -0.1]), la=arm([-0.5, -0.2, -0.7], 25), rl=[4, 0, 10], ll=[-4, 0, -10],
            rk=4, lk=4, root=[0.08, 0])
keyp(m, 16.8, SKY_REL, "out3")
keyp(m, 22.0, P(SKY_REL, body=[-14, -10, 0], head=[-22, 0], la=arm([-0.6, -0.5, -0.3], 20), root=[0.0, 0]))
keyp(m, 27.5, P(W_READY, body=[4, -10, 0]))
m.fp(3.0, (-0.3, 0.3, -0.02), ease="out3")
m.fp(15.6, (-0.3, 0.38, 0.05), ease="in")
m.fp(16.8, (-0.28, 0.5, 0.15), ease="out3")
m.fp(22.0, (-0.25, 0.3, 0.1))
m.fp(27.5, (0, 0, 0))
use(m, "skyBow")

# 그림자 난도질 (효과: 0 대상에게 달라붙어 벰 · 10 · 20 · 30 둘레의 3D 그림자 분신이 함께 벰 · 40 분신 셋과 함께 깊이 찌름): 달라붙으며 가로로 크게 벰 →
# 뛰어올라 세로로 내려 벰 → 몸째 한 바퀴 돌며 벰 → X 자로 벰 → 낮게 깊이 내디디며 찌름 → 뒤로 공중제비를 돌아 물러남
m = Motion("shadow_flurry", 56, "그림자 난도질 — 달라붙어 가로로 베고, 뛰어올라 내려 베고, 몸째 돌며 베고, X 자로 벤 뒤 낮게 깊이 찌르고 뒤로 공중제비를 돌아 물러남",
           fade=(0.02, 0.06))
strike_with(m, "slash_h", 0.0, 1.2, 3.4, a=P(body=[8, 44, 0], root=[-0.16, 0.0], rl=[22, 0, 4], ll=[-26, 0, -4], rk=34, lk=38),
            b=P(body=[16, -48, 0], root=[-0.26, 0.4], **LUNGE_DEEP))
keyp(m, 6.5, P(body=[-14, 6, 0], head=[-10, 0], ra=arm([0.16, 0.95, -0.25], 40, [0.05, 0.4, -0.92]), la=arm([-0.4, 0.2, 0.85], 10), rl=[-40, 0, 4], rk=70,
                ll=[-10, 0, -4], lk=30, root=[0.32, 0.1]), "out")  # 뛰어오름 (무릎을 당김)
strike_with(m, "slash_v", 8.2, 10.2, 12.6, a=P(body=[-16, 6, 0], head=[-10, 0], root=[0.3, 0.1], rl=[-40, 0, 4], rk=70, ll=[-10, 0, -4], lk=30),
            b=P(body=[34, -4, 0], head=[8, 0], root=[-0.34, 0.5], **LUNGE_DEEP))
spin_right(m, (20.0,), 23.0, wind_tick=17.4, fwd=P(K.SPIN_FWD_R, body=[12, 4, 0], rk=36, lk=36, root=[-0.18, 0.1]))
graft(m, "dagger_x", 28.6, 4.6, t_from=0.16, t_to=0.72)
keyp(m, 28.6, P(body=[-6, 20, 0], head=[-8, 0], ra=arm([0.5, 0.7, 0.3], 40, [0.3, 0.9, -0.2]), la=arm([-0.5, 0.65, 0.3], 40), rl=[10, 0, 6], ll=[-12, 0, -6],
                rk=18, lk=22, root=[0.02, 0], spin=-360))
strike_with(m, "thrust", 37.8, 40.2, 42.6, a=P(body=[4, 40, 0], root=[-0.2, -0.15], rl=[26, 0, 4], ll=[-18, 0, -4], rk=50, lk=18),
            b=P(body=[30, -26, 0], head=[6, 0], root=[-0.42, 0.85], **LUNGE_DEEP))
# 뒤로 공중제비 (몸을 웅크리고 한 바퀴 — flip −360) 로 물러남 → 내려앉음
keyp(m, 45.0, P(body=[20, 0, 0], ra=arm([0.3, -0.4, 0.6], 60), la=arm([-0.3, -0.4, 0.6], 60), rl=[-60, 0, 4], rk=110, ll=[-60, 0, -4], lk=110, root=[0.25, -0.25],
                spin=-360, flip=-120))
keyp(m, 48.0, P(body=[20, 0, 0], ra=arm([0.3, -0.4, 0.6], 60), la=arm([-0.3, -0.4, 0.6], 60), rl=[-60, 0, 4], rk=110, ll=[-60, 0, -4], lk=110, root=[0.35, -0.45],
                spin=-360, flip=-300))
keyp(m, 50.5, P(P(body=[18, 0, 0], ra=arm([0.4, -0.5, 0.5], 40), la=arm([-0.4, -0.5, 0.5], 40), spin=-360, flip=-360, **BRACE), root=[-0.3, -0.55]), "out")
keyp(m, 54.5, P(W_READY, body=[8, 0, 0], root=[-0.06, -0.25], spin=-360, flip=-360))
m.keys[1.0] = {"spin": -360, "flip": -360}
fpw(m, 6.5, FPW_SKY, "out")
fpw(m, 45.0, FPW_LOW, dp=(0, -0.3, 0.2))
fpw(m, 54.5, FPW_LOW)
use(m, "shadowFlurry")

# 월광 암살 (효과: 0 ~ 20 나는 사라짐 (투명 — 뼈 몸도 그리지 않음) · 3D 분신 셋이 대상 둘레를 돎 · 20 대상 뒤에 나타나 분신 셋과 함께 X 베기):
# 사라지며 웅크림 → 나타나기 직전 두 단검을 머리 위로 엇갈려 들고 발끝으로 섬 → 바깥 아래로 크게 X 를 그으며 한쪽 무릎을 꿇고 내려앉음 → 천천히 일어섬
m = Motion("moon_assassin", 34, "월광 암살 — 사라졌다가 대상 뒤에 나타나 두 단검을 머리 위로 엇갈려 들고, 바깥 아래로 크게 X 를 그으며 한쪽 무릎을 꿇고 내려앉음",
           fade=(0.03, 0.08))
CROUCH = P(body=[30, 10, 0], head=[-8, 0], ra=arm([0.3, -0.55, 0.6], 70, [-0.2, -0.6, 0.75]), la=L(A_GUARD), **K.LEGS_SQUAT)
keyp(m, 2.5, CROUCH, "out3")
keyp(m, 16.0, P(CROUCH, body=[32, 10, 0]))
XRAISE = P(body=[-14, 0, 0], head=[-12, 0], ra=arm([-0.35, 0.92, 0.2], 30, [-0.6, 0.75, -0.25]), la=arm([0.35, 0.92, 0.2], 30, [0.6, 0.75, -0.25]),
           rl=[4, 0, 8], ll=[-4, 0, -8], rk=6, lk=6, root=[0.08, 0])  # 두 단검을 머리 위로 엇갈려 듦
keyp(m, 18.6, XRAISE, "out")
XCUT = P(P(body=[34, 0, 0], head=[10, 0], ra=arm([0.75, -0.55, 0.35], 10, [0.85, -0.45, 0.25]), la=arm([-0.75, -0.55, 0.35], 10, [-0.85, -0.45, 0.25]),
           **K.LEGS_KNEEL), root=[-0.38, 0.3])  # 바깥 아래로 X 를 긋고 한쪽 무릎을 꿇음
keyp(m, 20.4, XCUT, "in")
m.arc(20.4, "ra", (0.6, -0.4, 0.7))
keyp(m, 25.0, P(XCUT, body=[30, 0, 0], head=[6, 0]))
keyp(m, 31.0, P(W_READY, body=[8, 0, 0]))
fpw(m, 2.5, FPW_LOW, "out3", dp=(0, -0.1, 0.1))
fpw(m, 16.0, FPW_LOW, dp=(0, -0.1, 0.1))
fpw(m, 18.6, FPW_SKY, "out")
fpw(m, 20.4, FPW_DOWN, "in", dp=(0.3, 0, 0))
fpw(m, 31.0, FPW_LOW)
use(m, "moonAssassin")

# 피의 낙인 (효과: 0 핏빛 초승달을 던짐 · 2 ~ 8 낙인 (머리 위 뒤에 3D 핏빛 낫) · 20 낫이 내리그어 낙인이 터짐): 낫을 크게 휘둘러 초승달을 던지고 →
# 왼손을 대상들 쪽으로 쭉 뻗어 손가락을 펴고 (저주) → 천천히 주먹을 쥐며 당겨 몸을 감음 (떨림) → 터지는 순간 주먹을 가슴으로 홱 당기며 낫을 치켜듦 → 섬
m = Motion("blood_brand", 30, "피의 낙인 — 낫을 크게 휘둘러 초승달을 던지고, 왼손을 대상들 쪽으로 뻗어 천천히 주먹을 쥐며 당기다가, 터지는 순간 주먹을 가슴으로 홱 당기며 "
           "낫을 치켜듦", fade=(0.03, 0.08))
m.set(hands={"main": "weapon", "off": "fist"})
strike_with(m, "skill_slash", 0.0, 1.4, 3.8, a=P(body=[6, 50, 0], root=[-0.16, -0.04], rl=[22, 0, 4], ll=[-26, 0, -4], rk=34, lk=38),
            b=P(body=[14, -46, 0], root=[-0.26, 0.42], **LUNGE_DEEP))
CURSE = P(body=[8, -24, 0], head=[2, 0], ra=arm([0.55, -0.6, -0.45], 30, [0.2, 0.6, -0.75]), la=arm([-0.1, 0.12, 1.0], 0, [0, 1, 0.1]), rl=[14, 0, 4],
          ll=[-18, 0, -4], rk=18, lk=16, root=[-0.08, 0.1])  # 왼손을 앞으로 쭉 뻗음 · 낫은 오른쪽 뒤로
keyp(m, 6.0, CURSE, "out")
for i, tick in enumerate((9.0, 12.0, 15.0, 18.2)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(CURSE, body=[8 + 4 * q, -24 + 14 * q, s * 0.8], la=bent([-0.1, 0.1 - 0.05 * q, 1.0 - 0.25 * q], 40 * q + 10, (0.2, 1, 0)),
                    rk=18 + 14 * q, lk=16 + 14 * q, root=[-0.08 - 0.08 * q, 0.1 - 0.12 * q]))
YANK = P(body=[-8, 14, 0], head=[-14, 0], ra=arm([0.3, 0.85, 0.3], 30, [0.1, 0.9, -0.3]), la=L(A_CHEST), rl=[0, 0, 8], ll=[0, 0, -8], rk=8, lk=8,
         root=[0.02, -0.1])  # 주먹을 가슴으로 홱 당기며 낫을 치켜듦
keyp(m, 20.4, YANK, "out3")
keyp(m, 24.5, P(YANK, body=[-10, 14, 0]))
keyp(m, 28.5, P(W_READY, body=[8, 0, 0]))
fpw(m, 6.0, FPW_LOW, "out", dp=(0.2, -0.1, 0.1))
fpw(m, 18.2, FPW_LOW, dp=(0.25, -0.15, 0.15))
fpw(m, 20.4, FPW_SKY, "out3", dp=(0.1, -0.2, 0))
fpw(m, 28.5, FPW_LOW)
fpl(m, 3.8, FP_LOW)
fpl(m, 6.0, FP_PALM, "out")
fpl(m, 18.2, ((0.0, 0.0, -0.25), (15, 50, 0)))
fpl(m, 20.4, FP_CHAMBER, "out3")
fpl(m, 28.5, FP_LOW)
use(m, "bloodBrand")


# ------------------------------------------------------------ 중력 · 무투 · 화산 · 대지 · 빙하 (2026-11-13 묶음 C — 3D 바위 · 공허 핵 · 기의 화신 · 화산탄)

def off_bare(m):
    m.set(hands={"main": "weapon", "off": "fist"})
    return m


A_SKY = arm([0.14, 1, 0.08], 4, [0, 1, 0.05])
A_SCOOP = arm([0.3, -0.85, 0.42], 10)  # 아래 바깥에서 퍼 올릴 준비

# 중력 역전 (효과: 0 띄움 — 둘레의 3D 바위가 솟아 떠오름 · 24 한꺼번에 내리꽂음): 낮게 웅크려 두 팔을 아래 바깥으로 → 퍼 올리듯 두 팔을 하늘로 쳐들며
# 발끝으로 솟음 → 하늘을 받친 채 떨며 버팀 (더 젖혀짐) → 두 주먹을 머리 뒤로 젖혔다가 힘껏 내리꽂으며 깊이 웅크림 → 일어섬
m = off_bare(Motion("gravity_reverse", 34, "중력 역전 — 웅크렸다가 두 팔을 퍼 올리듯 하늘로 쳐들어 적과 바위를 띄우고, 떨며 받치다가 두 주먹을 힘껏 내리꽂으며 깊이 웅크림",
                    fade=(0.02, 0.08)))
SCOOP = P(body=[26, 0, 0], head=[8, 0], ra=K.A_SPREAD_DOWN, la=L(K.A_SPREAD_DOWN), **K.LEGS_SQUAT)
keyp(m, 0.0, SCOOP)
HOLD_SKY = P(body=[-16, 0, 0], head=[-30, 0], ra=arm([0.25, 0.97, 0.05], 8, [0, 1, 0.1]), la=L(arm([0.25, 0.97, 0.05], 8)), rl=[0, 0, 10], ll=[0, 0, -10],
             rk=4, lk=4, root=[0.12, 0])
keyp(m, 3.2, HOLD_SKY, "out3")
for i, tick in enumerate((7, 10.5, 14, 17.5)):
    s = 1 if i % 2 else -1
    q = (i + 1) / 4.0
    keyp(m, tick, P(HOLD_SKY, body=[-16 - 6 * q, 0, s * 1.5], head=[-30 - 4 * q, 0], root=[0.08, 0], rk=8 + 6 * q, lk=8 + 6 * q))
WIND_SKY = P(body=[-26, 0, 0], head=[-34, 0], ra=bent([0.2, 0.9, -0.4], 60, (0, 0.3, -1)), la=L(bent([0.2, 0.9, -0.4], 60, (0, 0.3, -1))), rl=[0, 0, 10],
             ll=[0, 0, -10], rk=16, lk=16, root=[0.04, 0])
keyp(m, 22.0, WIND_SKY, "out")
SLAM = P(body=[42, 0, 0], head=[16, 0], ra=bent([0.18, -0.8, 0.55], 40, (0, 1, 0.3)), la=L(bent([0.18, -0.8, 0.55], 40, (0, 1, 0.3))), **K.LEGS_SQUAT)
keyp(m, 24.2, P(SLAM, root=[-0.4, 0.1]), "in")
keyp(m, 28.0, P(SLAM, body=[38, 0, 0], root=[-0.36, 0.1]))
keyp(m, 32.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW, dp=(0, -0.2, 0))
fpw(m, 3.2, FPW_SKY, "out3")
fpw(m, 22.0, FPW_SKY, dp=(0.1, 0.1, 0.2))
fpw(m, 24.2, FPW_DOWN, "in")
fpw(m, 32.5, FPW_LOW)
fpl(m, 0.0, FP_LOW)
fpl(m, 3.2, FP_RAISE, "out3")
fpl(m, 22.0, nudge(FP_RAISE, (0.05, 0.1, 0.1)))
fpl(m, 24.2, K.FP_GROUND, "in")
fpl(m, 32.5, FP_LOW)
use(m, "gravityReverse")

# 블랙홀 (효과: 0 ~ 16 조준한 곳 위에 3D 공허 핵이 자람 · 16 ~ 76 빨아들임 · 76 붕괴 — 붕괴는 단계 동작 black_hole_crush 를 70 에 다시 보냄):
# 두 손을 앞 위로 들어 핵을 빚듯 둘레를 번갈아 감싸고 → 핵이 다 자라면 한 손을 쭉 뻗어 붙들고 다른 손으로 받친 채 끌려가지 않으려고 몸을 뒤로 젖혀 버팀 (떨림)
m = off_bare(Motion("black_hole", 64, "블랙홀 — 두 손으로 앞 위의 공허를 빚어 키우고, 다 자라면 손을 쭉 뻗어 붙든 채 빨려 들지 않으려고 몸을 젖혀 버팀",
                    fade=(0.03, 0.04)))
SHAPE = P(body=[-6, 0, 0], head=[-14, 0], ra=arm([0.2, 0.5, 0.85], 30, [0.0, 0.9, 0.45]), la=L(bent([0.2, 0.5, 0.86], 35, (-1, 0.4, 0))), **LEGS_WIDE)
keyp(m, 2.5, SHAPE, "out3")
for i, tick in enumerate((5.5, 8.5, 11.5, 14.5)):
    up = i % 2 == 0
    keyp(m, tick, P(SHAPE, body=[-6, -10 if up else 10, 0], ra=arm([0.3 if up else 0.05, 0.75 if up else 0.3, 0.6], 30, [0, 0.9, 0.4]),
                    la=L(bent([0.05 if up else 0.3, 0.3 if up else 0.75, 0.9], 35, (-1, 0.4, 0)))))
BRACE_HOLD = P(body=[-12, -8, 0], head=[-14, 0], ra=arm([0.0, 0.45, 0.9], 2, [0, 0.5, 0.86]), la=L(bent([0.3, 0.3, 0.8], 70, (1, 0.3, 0))),
               rl=[34, 0, 10], ll=[-6, 0, -10], rk=40, lk=18, root=[-0.18, -0.15])  # 한 손을 쭉 뻗어 붙들고 몸을 뒤로 젖혀 버팀 (오른발 뒤)
keyp(m, 17.5, BRACE_HOLD, "out")
for i in range(12):
    tick = 21 + i * 3.4
    s = 1 if i % 2 else -1
    keyp(m, tick, P(BRACE_HOLD, body=[-12 + s * 2, -8 + s * 3, s * 1.2], root=[-0.18 + 0.02 * s, -0.15 - 0.01 * i]))
keyp(m, 62.0, P(BRACE_HOLD, body=[-10, -6, 0], root=[-0.2, -0.28]))
fpw(m, 2.5, FPW_UP, "out3", dp=(0.1, 0, 0))
fpw(m, 17.5, FPW_FWD, "out", dp=(0, 0.3, 0))
fpw(m, 62.0, FPW_FWD, dp=(0, 0.3, 0.05))
fpl(m, 2.5, FP_PALM, "out3")
fpl(m, 8.5, nudge(FP_PALM, (0.1, 0.15, 0)))
fpl(m, 14.5, nudge(FP_PALM, (-0.05, -0.05, 0)))
fpl(m, 17.5, nudge(FP_PALM, (0.15, 0.1, 0.1)), "out")
fpl(m, 62.0, nudge(FP_PALM, (0.15, 0.1, 0.1)))
use(m, "blackHole")

# 블랙홀 붕괴 (단계 — 효과 70 에 보냄 · 6 이 붕괴): 버티던 자세에서 두 주먹을 앞에 모아 쥐고 (0 ~ 4) → 허리로 힘껏 끌어내리며 발을 구름 (6) → 섬
m = off_bare(Motion("black_hole_crush", 16, "블랙홀 붕괴 — 버티던 두 손을 앞에 모아 쥐고 허리로 힘껏 끌어내리며 발을 굴러 공허를 짓뭉갬", fade=(0.0, 0.12)))
keyp(m, 0.0, P(BRACE_HOLD, body=[-10, -6, 0], root=[-0.2, -0.28]))
keyp(m, 3.6, P(body=[-8, 0, 0], head=[-10, 0], ra=bent([0.1, 0.4, 0.9], 50, (-1, 0.3, 0)), la=L(bent([0.1, 0.4, 0.9], 50, (-1, 0.3, 0))), **LEGS_WIDE), "out")
keyp(m, 6.2, P(P(body=[30, 0, 0], head=[12, 0], ra=K.A_PULL, la=L(K.A_PULL), **K.LEGS_STOMP), root=[-0.3, 0]), "in")
keyp(m, 10.0, P(P(body=[24, 0, 0], head=[8, 0], ra=K.A_PULL, la=L(K.A_PULL), **K.LEGS_STOMP), root=[-0.26, 0]))
keyp(m, 14.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 3.6, FPW_FWD, "out")
fpw(m, 6.2, FPW_LOW, "in")
fpl(m, 0.0, nudge(FP_PALM, (0.15, 0.1, 0.1)))
fpl(m, 3.6, FP_PALM, "out")
fpl(m, 6.2, FP_CHAMBER, "in")
fpl(m, 14.5, FP_LOW)
use(m, "blackHole:crush")

# 백열권 (효과: 0 ~ 50 2틱마다 주먹 비 — 뒤의 3D 기의 화신도 함께 · 50 마지막 일격): 말을 탄 듯 낮고 넓게 서서 몸을 크게 좌우로 비틀며 주먹을 쉴 새 없이 퍼붓고
# (점점 빨라지듯 비틀림이 커짐) → 오른 주먹을 허리 뒤로 깊이 당겨 몸을 끝까지 감았다가 → 크게 내디디며 쭉 뻗어 침
m = K.fists(Motion("hundred_fists", 58, "백열권 — 낮고 넓게 서서 몸을 크게 비틀며 주먹을 쉴 새 없이 퍼붓다가, 오른 주먹을 깊이 당겨 크게 내디디며 마무리 일격",
                   fade=(0.02, 0.08)))
HORSE = dict(rl=[-24, 0, 16], ll=[-24, 0, -16], rk=58, lk=58, root=[-0.26, 0])
GUARD2 = P(body=[12, 0, 0], head=[2, 0], ra=K.A_GUARD, la=L(K.A_GUARD), **HORSE)
VAR = [(K.A_JAB, 0.0, 0.0), (K.A_JAB_HI, 0.08, 0.05), (K.A_JAB_LO, -0.08, -0.06), (K.A_JAB, 0.04, 0.08), (K.A_JAB_HI, 0.1, -0.05), (K.A_JAB_LO, -0.05, 0.03)]
keyp(m, 0.0, GUARD2)
for i in range(24):
    tick = 1 + 2 * i
    a, hi, side = VAR[i % len(VAR)]
    right = i % 2 == 0
    tw = 26 + min(10, i * 0.6)
    if right:
        keyp(m, tick, P(GUARD2, body=[14, -tw, 0], ra=a, la=L(K.A_CHAMBER)), "out")
    else:
        keyp(m, tick, P(GUARD2, body=[14, tw, 0], ra=K.A_CHAMBER, la=L(a)), "out")
    pre = nudge(K.FP_JAB, (0, hi * 0.5, 0), (hi * 60, side * 60, 0))
    if right:
        K.fpr(m, tick, pre, "out3")
        K.fpl(m, tick, K.FP_GUARD)
    else:
        K.fpl(m, tick, pre, "out3")
        K.fpr(m, tick, K.FP_GUARD)
keyp(m, 47.6, P(GUARD2, body=[4, 52, 0], ra=K.A_CHAMBER, la=L(arm([-0.1, 0.1, 1], 4)), rk=64, lk=60, root=[-0.3, -0.1]))
keyp(m, 50.4, P(body=[18, -46, 0], head=[4, 0], ra=K.A_JAB, la=L(K.A_CHAMBER), root=[-0.3, 0.6], **LUNGE_DEEP), "out3")
keyp(m, 54.5, P(body=[16, -42, 0], head=[4, 0], ra=K.A_JAB, la=L(K.A_CHAMBER), root=[-0.28, 0.58], **LUNGE_DEEP))
K.fpr(m, 47.6, nudge(FP_CHAMBER, (0.05, -0.05, 0.1)))
K.fpl(m, 47.6, K.FP_JAB)
K.fpr(m, 50.4, nudge(K.FP_JAB2, (0, 0.03, -0.1), (3, 3, 0)), "out3")
K.fpl(m, 50.4, FP_CHAMBER)
K.fpr(m, 54.5, K.FP_JAB2)
K.fpl(m, 54.5, K.FP_GUARD)
use(m, "hundredFists")

# 마그마 대지 (효과: 0 조준한 곳의 땅이 녹음 · 10틱마다 화상 · 40 대분출 — 용암 기둥 · 3D 화산탄): 오른 무릎을 높이 들었다 힘껏 땅을 구르며 두 손바닥으로 땅을
# 내리침 → 손바닥으로 땅을 누른 채 열기에 떨며 버팀 → 대분출 직전 두 손을 가슴으로 끌어올렸다가 → 두 팔을 하늘로 확 펼치며 솟음
m = off_bare(Motion("magma_field", 48, "마그마 대지 — 무릎을 들어 땅을 구르며 두 손바닥으로 땅을 내리쳐 녹이고, 누른 채 떨다가, 대분출 때 두 팔을 하늘로 확 펼치며 솟음",
                    fade=(0.02, 0.08)))
keyp(m, 0.0, P(body=[-6, 10, 0], head=[-4, 0], ra=K.A_RAISE_FIST, la=L(K.A_RAISE_FIST), **K.LEGS_STOMP_UP))
PRESS = P(body=[34, 0, 0], head=[12, 0], ra=arm([0.25, -0.7, 0.66], 20, [0.1, -0.3, 0.95]), la=L(arm([0.2, -0.7, 0.68], 6, [0, 1, 0.3])), **K.LEGS_SQUAT)
keyp(m, 2.4, P(PRESS, root=[-0.38, 0.05]), "in")
for i in range(9):
    tick = 6 + i * 3.6
    s = 1 if i % 2 else -1
    keyp(m, tick, P(PRESS, body=[34 + s * 1.5, s * 3, s * 1.0], root=[-0.36, 0.05]))
keyp(m, 38.2, P(body=[14, 0, 0], head=[4, 0], ra=bent([0.2, -0.4, 0.6], 120, (0, 1, 0.3)), la=L(bent([0.2, -0.4, 0.6], 120, (0, 1, 0.3))), rk=50, lk=50,
                 rl=[-30, 0, 10], ll=[-30, 0, -10], root=[-0.3, 0]), "out")
ERUPT = P(body=[-20, 0, 0], head=[-28, 0], ra=K.A_SPREAD_UP, la=L(K.A_SPREAD_UP), rl=[0, 0, 12], ll=[0, 0, -12], rk=0, lk=0, root=[0.16, 0])
keyp(m, 40.4, ERUPT, "out3")
keyp(m, 44.0, P(ERUPT, body=[-14, 0, 0], root=[0.02, 0]))
keyp(m, 47.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_UP)
fpw(m, 2.4, FPW_DOWN, "in")
fpw(m, 38.2, FPW_LOW)
fpw(m, 40.4, FPW_SKY, "out3")
fpw(m, 47.0, FPW_LOW)
fpl(m, 0.0, nudge(FP_RAISE, (0, -0.2, 0)))
fpl(m, 2.4, K.FP_GROUND, "in")
fpl(m, 36.0, K.FP_GROUND)
fpl(m, 38.2, FP_CHAMBER, "out")
fpl(m, 40.4, K.FP_SPREAD_UP, "out3")
fpl(m, 47.0, FP_LOW)
use(m, "magmaField")

# 대분화 (효과: 0 ~ 24 화산이 솟음 · 24 ~ 74 화산탄 · 90 대분화 — 대분화는 단계 동작 grand_eruption_burst 를 80 에 다시 보냄): 한쪽 무릎을 꿇고 두 손을
# 땅에 꽂았다가 무거운 것을 들어 올리듯 천천히 일어서며 두 손을 끌어올림 (떨림) → 화산이 터지는 순간 두 팔을 하늘로 활짝 → 화산탄이 쏟아지는 동안 두 팔로 번갈아 겨눔
m = off_bare(Motion("grand_eruption", 64, "대분화 — 무릎을 꿇고 두 손을 땅에 꽂았다가 무거운 것을 들듯 천천히 일어서며 화산을 끌어올리고, 터지는 순간 두 팔을 활짝 펴 "
                    "쏟아지는 화산탄을 번갈아 겨눔", fade=(0.02, 0.05)))
DIG = P(body=[36, 0, 0], head=[12, 0], ra=arm([0.2, -0.9, 0.4], 10, [0, -1, 0.2]), la=L(arm([0.2, -0.9, 0.4], 10)), **K.LEGS_KNEEL)
keyp(m, 2.2, DIG, "out3")
for i, tick in enumerate((6, 10, 14, 18)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(body=[36 - 40 * q, 0, s * 1.5], head=[12 - 30 * q, 0], ra=arm([0.25, -0.9 + 1.4 * q, 0.4 - 0.1 * q], 30 - 20 * q),
                    la=L(arm([0.25, -0.9 + 1.4 * q, 0.4 - 0.1 * q], 30 - 20 * q)), rl=[2 - 2 * q, 0, 4 + 6 * q], rk=92 - 80 * q, ll=[-82 + 82 * q, 0, -4 - 6 * q],
                    lk=88 - 76 * q, root=[-0.38 + 0.36 * q, 0]))
ERUPT2 = P(body=[-18, 0, 0], head=[-28, 0], ra=K.A_SPREAD_UP, la=L(K.A_SPREAD_UP), rl=[0, 0, 12], ll=[0, 0, -12], rk=4, lk=4, root=[0.1, 0])
keyp(m, 24.4, ERUPT2, "out3")
for i in range(6):
    tick = 30 + i * 5.5
    right = i % 2 == 0
    keyp(m, tick, P(body=[4, -20 if right else 20, 0], head=[-6, 0], ra=arm([-0.1, 0.25, 1], 0) if right else K.A_SPREAD_DOWN,
                    la=L(K.A_SPREAD_DOWN) if right else L(arm([-0.1, 0.25, 1], 0)), **LEGS_WIDE), "out")
keyp(m, 62.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 2.2, FPW_DOWN, "out3")
fpw(m, 18, FPW_UP)
fpw(m, 24.4, FPW_SKY, "out3")
fpw(m, 30, FPW_FWD, "out")
fpw(m, 62.0, FPW_LOW)
fpl(m, 2.2, K.FP_GROUND, "out3")
fpl(m, 18, FP_PALM)
fpl(m, 24.4, K.FP_SPREAD_UP, "out3")
fpl(m, 35.5, FP_PALM, "out")
fpl(m, 62.0, FP_LOW)
use(m, "grandEruption")

# 대분화 터짐 (단계 — 효과 80 에 보냄 · 10 이 대분화): 두 팔을 가슴 앞에 엇갈려 모았다가 (8) 하늘로 확 펼치며 솟음 (10)
m = off_bare(Motion("grand_eruption_burst", 20, "대분화 터짐 — 두 팔을 가슴 앞에 엇갈려 모아 힘을 끌어모았다가 하늘로 확 펼치며 솟음", fade=(0.08, 0.15)))
keyp(m, 3.0, P(body=[16, 0, 0], head=[8, 0], ra=K.A_CROSS, la=L(K.A_CROSS), rk=40, lk=40, rl=[-24, 0, 10], ll=[-24, 0, -10], root=[-0.24, 0]), "out")
keyp(m, 8.4, P(body=[22, 0, 0], head=[12, 0], ra=K.A_CROSS, la=L(K.A_CROSS), rk=56, lk=56, rl=[-32, 0, 10], ll=[-32, 0, -10], root=[-0.32, 0]))
keyp(m, 10.4, P(ERUPT2, root=[0.18, 0]), "out3")
keyp(m, 15.0, P(ERUPT2, body=[-12, 0, 0]))
fpw(m, 3.0, FPW_LOW, "out")
fpw(m, 10.4, FPW_SKY, "out3")
fpl(m, 3.0, K.FP_CROSS, "out")
fpl(m, 10.4, K.FP_SPREAD_UP, "out3")
fpl(m, 18.0, FP_LOW)
use(m, "grandEruption:burst")

# 대지 가르기 (효과: 0 ~ 24 머리 위 대지의 대검이 커지며 뒤로 젖혀짐 · 24 앞으로 내려찍어 땅이 갈라짐 · 40 ~ 90 여진): 두 팔을 하늘로 쳐들어 대검을 부르고 →
# 두 손을 모아 머리 위 뒤로 젖히며 몸을 활처럼 젖혀 떨며 버팀 (점점 더) → 깊이 내디디며 두 손으로 앞 땅까지 내려찍음 → 내려찍은 채 버팀
m = Motion("earth_splitter", 34, "대지 가르기 — 두 팔을 쳐들어 대검을 부르고, 머리 위 뒤로 젖혀 몸을 활처럼 휘며 버티다가, 깊이 내디디며 두 손으로 앞 땅까지 내려찍음",
           fade=(0.03, 0.08))
keyp(m, 4.0, P(W_SKY, la=L(A_RAISE), body=[-10, 0, 0], head=[-26, 0]), "out3")
keyp(m, 9.0, P(K.W_HIGH2, body=[-22, 4, 0], head=[-16, 0]))
for i, tick in enumerate((12.5, 15.5, 18.5, 21.0)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(K.W_HIGH2, body=[-24 - 8 * q, 4, s * 1.2], head=[-16 - 6 * q, 0], ra=arm([0.12, 0.85 - 0.15 * q, -0.5 - 0.15 * q], 45, [0, 0.1, -1]),
                    rk=8 + 10 * q, lk=8 + 10 * q, root=[0.05 - 0.04 * q, -0.05 * q]))
strike_with(m, "slam", 21.6, 24.2, 27.0, a=P(body=[-32, 4, 0], head=[-22, 0], root=[0.0, -0.2], rl=[8, 0, 0], ll=[-8, 0, 0], rk=18, lk=18),
            b=P(body=[44, 0, 0], head=[14, 0], root=[-0.42, 0.75], **LUNGE_DEEP))
keyp(m, 30.0, P(body=[40, 0, 0], head=[12, 0], ra=arm([0.05, -0.8, 0.6], 10, [0, -0.95, 0.3]), la=L(arm([0.05, -0.8, 0.6], 10)), root=[-0.4, 0.72],
                **LUNGE_DEEP))
fpw(m, 4.0, FPW_SKY, "out3")
fpw(m, 21.0, FPW_SKY, dp=(0.1, 0.1, 0.25))
fpw(m, 30.0, FPW_DOWN)
use(m, "earthSplitter")

# 만년빙하 (효과: 0 ~ 16 조준한 곳에 얼음판 · 17 부터 14틱마다 거대한 얼음이 솟음 — 사용자 "잘나온거"): 몸째 오른쪽으로 한 바퀴 돌며 왼손으로 서리 빛을
# 크게 흩뿌리고 → 낮게 웅크려 두 손바닥으로 땅을 내리쳐 얼음판을 퍼뜨림 → 손바닥을 땅에 댄 채 얼음이 솟을 때마다 조금씩 눌러 줌 → 일어섬
m = off_bare(Motion("glacier_field", 40, "만년빙하 — 몸째 한 바퀴 돌며 왼손으로 서리 빛을 크게 흩뿌리고, 낮게 웅크려 두 손바닥으로 땅을 내리쳐 얼음판을 퍼뜨린 뒤 눌러 줌",
                    fade=(0.03, 0.06)))
keyp(m, 0.0, P(body=[4, 30, 0], ra=K.RA_READY, la=L(bent([-0.5, 0.05, 0.6], 80, (1, 0.3, 0.3))), rk=24, lk=24, root=[-0.1, 0]))
keyp(m, 3.0, P(body=[6, -20, 0], ra=K.RA_READY, la=L(K.A_SPREAD), rk=24, lk=24, root=[-0.12, 0], spin=-180), "linear")
keyp(m, 6.0, P(body=[8, -24, 0], ra=K.RA_READY, la=L(arm([-0.6, 0.1, 0.8], 4)), rk=24, lk=24, root=[-0.12, 0], spin=-360), "out")
FREEZE = P(body=[36, 0, 0], head=[12, 0], ra=arm([0.25, -0.7, 0.66], 20, [0.1, -0.3, 0.95]), la=L(arm([0.2, -0.7, 0.68], 6, [0, 1, 0.3])), spin=-360,
           **K.LEGS_SQUAT)
keyp(m, 9.0, P(FREEZE, body=[24, 0, 0], root=[-0.2, 0]))
keyp(m, 11.4, P(FREEZE, root=[-0.4, 0.05]), "in")
for i, tick in enumerate((17.0, 31.0)):
    keyp(m, tick - 1.5, P(FREEZE, body=[30, 0, 0], root=[-0.34, 0.05]))
    keyp(m, tick, P(FREEZE, body=[40, 0, 0], root=[-0.42, 0.05]), "in")
keyp(m, 37.0, P(W_READY, body=[6, 0, 0], spin=-360))
m.keys[1.0] = {"spin": -360}
fpw(m, 3.0, FPW_LOW)
fpw(m, 11.4, FPW_DOWN, "in")
fpw(m, 37.0, FPW_LOW)
fpl(m, 0.0, nudge(FP_PALM, (0.2, 0, 0.1)))
fpl(m, 3.0, K.FP_PUSH_OUT, "linear")
fpl(m, 6.0, FP_PALM, "out")
fpl(m, 11.4, K.FP_GROUND, "in")
fpl(m, 31.0, K.FP_GROUND)
fpl(m, 37.0, FP_LOW)
use(m, "glacierField")


# ------------------------------------------------------------ 신성 · 체력 · 신체 (2026-11-13 묶음 D — 3D 날개 · 빛의 육각 판 · 핏빛 결정 · 투신의 화신 · 바위)

FPL_CHEST_ = ((-0.18, -0.12, 0.12), (25, 70, 0))  # 1인칭 왼팔: 주먹 · 손바닥을 가슴에 (rune_motions_magic.FPL_CHEST)
LA_SPREAD_DOWN_ = L(bent([0.8, -0.5, 0.3], 20, (0, 0.3, 1)))
FPL_SPREAD_DOWN_ = ((0.3, -0.15, -0.1), (0, -35, 0))
A_BLESS = arm([0.55, 0.05, 0.84], 18, [0.2, 0.95, 0.1])  # 앞 바깥으로 손바닥을 위로 내밂 (축복)

# 대천사 강림 (효과: 0 ~ 6 등 뒤의 3D 날개가 접힌 채 솟아 활짝 펼쳐짐 · 20틱마다 날갯짓 — 회복 · 피해 틱에 내려친 날개가 끝남): 웅크려 두 팔을 가슴 앞에 엇갈려
# 고개를 숙임 (날개를 품은 듯) → 날개가 펼쳐지는 틱에 터지듯 두 팔을 활짝 펴고 몸을 젖혀 하늘을 보며 발끝으로 솟음 → 천천히 두 팔을 앞 바깥으로 내려 손바닥을 위로
# (축복) — 날갯짓 (20) 에 어깨가 한 번 들썩임
m = off_bare(Motion("archangel", 40, "대천사 강림 — 웅크려 두 팔을 가슴에 엇갈려 품었다가 날개가 펼쳐질 때 두 팔을 활짝 펴 하늘을 보며 솟고, 천천히 두 팔을 내려 "
                    "손바닥을 위로 축복함", fade=(0.03, 0.1)))
FOLD = P(body=[22, 0, 0], head=[22, 0], ra=K.RA_ACROSS, la=L(A_CROSS), rl=[-20, 0, 6], ll=[-20, 0, -6], rk=38, lk=38, root=[-0.2, 0])
keyp(m, 0.0, P(FOLD, body=[14, 0, 0], root=[-0.12, 0]))
keyp(m, 2.6, FOLD, "out")
keyp(m, 4.4, P(FOLD, body=[26, 0, 0], rk=44, lk=44, root=[-0.24, 0]))
SPREAD = P(body=[-16, 0, 0], head=[-26, 0], ra=arm([0.92, 0.38, 0.08], 6, [0.6, 0.8, 0.0]), la=L(arm([0.92, 0.38, 0.08], 6)), rl=[4, 0, 6], ll=[-4, 0, -6],
           rk=2, lk=2, root=[0.14, 0])
keyp(m, 6.2, SPREAD, "out3")
keyp(m, 10.0, P(SPREAD, body=[-14, 0, 0], head=[-22, 0], root=[0.12, 0]))
BLESS = P(body=[-4, 0, 0], head=[-8, 0], ra=A_BLESS, la=L(A_BLESS), rl=[2, 0, 5], ll=[-2, 0, -5], rk=4, lk=4, root=[0.08, 0])
keyp(m, 17.0, BLESS, "inout")
keyp(m, 19.0, P(BLESS, body=[-6, 0, 0], ra=arm([0.6, 0.15, 0.78], 18, [0.2, 0.95, 0.1]), la=L(arm([0.6, 0.15, 0.78], 18)), root=[0.1, 0]))
keyp(m, 21.5, P(BLESS, body=[-3, 0, 0], root=[0.07, 0]), "out")
keyp(m, 28.0, P(BLESS, body=[-5, 2, 0], head=[-10, 4]))
keyp(m, 34.0, P(BLESS, body=[-4, -2, 0], head=[-8, -4]))
keyp(m, 38.5, P(W_READY, body=[2, 0, 0]))
fpw(m, 0.0, K.FPW_ACROSS)
fpw(m, 4.4, K.FPW_ACROSS, dp=(0, -0.05, 0))
fpw(m, 6.2, FPW_SKY, "out3", dp=(0.55, -0.35, 0))
fpw(m, 17.0, FPW_FWD, "inout", dp=(0.3, -0.1, 0))
fpw(m, 34.0, FPW_FWD, dp=(0.3, -0.1, 0))
fpw(m, 38.5, FPW_LOW)
fpl(m, 0.0, K.FP_CROSS)
fpl(m, 4.4, K.FP_CROSS)
fpl(m, 6.2, K.FP_SPREAD_UP, "out3")
fpl(m, 17.0, nudge(FP_PALM, (0.1, -0.05, 0.1), (-30, 0, 0)), "inout")
fpl(m, 34.0, nudge(FP_PALM, (0.1, -0.05, 0.1), (-30, 0, 0)))
fpl(m, 38.5, FP_LOW)
use(m, "archangel")

# 불굴의 방벽 (효과: 1 ~ 12 둘레의 3D 빛의 육각 판이 보는 쪽부터 차례로 솟음 · 20틱마다 판이 번쩍): 무릎을 들며 무기를 두 손으로 머리 위로 치켜들었다가 (날이 아래)
# 앞 땅에 힘껏 꽂으며 깊이 버팀 (3) → 두 손으로 자루를 누른 채 숨 쉬듯 버팀 · 판이 번쩍일 때 (20) 한 번 더 눌러 꽂음
m = off_bare(Motion("bulwark", 32, "불굴의 방벽 — 무기를 두 손으로 머리 위로 치켜들었다가 앞 땅에 힘껏 꽂고, 두 손으로 자루를 누른 채 버팀", fade=(0.02, 0.1)))
HOIST = P(body=[-10, 0, 0], head=[-12, 0], ra=arm([0.1, 0.92, 0.35], 40, [0, -0.3, 0.95]), la=arm([-0.05, 0.92, 0.35], 40), **K.LEGS_STOMP_UP)
keyp(m, 1.2, HOIST, "out")
PLANT2 = P(body=[30, 0, 0], head=[8, 0], ra=K.RA_PLANT, la=arm([-0.04, -0.3, 0.95], 45), root=[-0.3, 0.12], **BRACE)
keyp(m, 3.2, PLANT2, "in")
keyp(m, 6.0, P(PLANT2, body=[27, 0, 0], root=[-0.27, 0.12]))
for i, tick in enumerate((10.0, 14.0, 17.0)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(PLANT2, body=[26 + s, 0, s * 1.0], root=[-0.26, 0.12]))
keyp(m, 19.0, P(PLANT2, body=[24, 0, 0], root=[-0.24, 0.12]), "out")
keyp(m, 20.6, P(PLANT2, body=[32, 0, 0], root=[-0.32, 0.14]), "in")
keyp(m, 24.0, P(PLANT2, body=[26, 0, 0], root=[-0.27, 0.12]))
keyp(m, 30.0, P(PLANT2, body=[22, 0, 0], root=[-0.22, 0.1]))
FPL_GRIP_ = ((-0.25, -0.1, -0.15), (-5, 60, 0))
fpw(m, 1.2, FPW_UP, "out")
fpw(m, 3.2, FPW_DOWN, "in")
fpw(m, 30.0, FPW_DOWN)
fpl(m, 1.2, ((-0.2, 0.25, 0.0), (40, 60, 0)), "out")
fpl(m, 3.2, FPL_GRIP_, "in")
fpl(m, 30.0, FPL_GRIP_)
use(m, "bulwark")

# 불사의 맹약 (효과: 1 ~ 7 둘레에서 핏빛 결정 아홉이 바깥으로 기울어 솟음 · 20틱마다 몸 둘레에 작은 결정): 한쪽 무릎을 꿇고 왼 주먹을 가슴에 대 고개를 깊이
# 숙임 (맹세) → 결정이 솟는 틱에 고개를 젖히며 → 일어서며 무기를 하늘로 치켜듦 (왼 주먹은 가슴에) → 떨며 버팀
m = off_bare(Motion("undying_pact", 34, "불사의 맹약 — 무릎을 꿇고 주먹을 가슴에 대 맹세하다가, 핏빛 결정이 솟을 때 일어서며 무기를 하늘로 치켜듦", fade=(0.03, 0.1)))
OATH2 = P(body=[18, 0, 0], head=[30, 0], ra=K.RA_LOW, la=L(A_CHEST), **K.LEGS_KNEEL)
keyp(m, 1.0, OATH2, "out")
keyp(m, 3.6, P(OATH2, body=[20, 0, 0], head=[34, 0]))
keyp(m, 5.0, P(OATH2, body=[4, 0, 0], head=[-20, 0]), "out")
RISE = P(body=[-4, -2, 0], head=[-16, 0], ra=arm([0.15, 0.7, 0.7], 30, [0, 0.9, 0.4]), la=L(A_CHEST), rl=[-30, 0, 4], ll=[-10, 0, -4], rk=40, lk=30,
         root=[-0.16, 0])
keyp(m, 9.0, RISE, "inout")
LIFT2 = P(body=[-14, -4, 0], head=[-28, 0], ra=K.RA_SKY, la=L(A_CHEST), rl=[0, 0, 8], ll=[0, 0, -8], rk=4, lk=4, root=[0.06, 0])
keyp(m, 12.0, LIFT2, "out3")
for i, tick in enumerate((16.0, 20.0, 24.0, 28.0)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(LIFT2, body=[-13 + s, -4, s * 1.2], head=[-27, s * 2]))
keyp(m, 32.5, P(W_READY, body=[4, 0, 0]))
fpw(m, 1.0, FPW_LOW, "out")
fpw(m, 5.0, FPW_LOW)
fpw(m, 9.0, FPW_UP, "inout")
fpw(m, 12.0, FPW_SKY, "out3")
fpw(m, 28.0, FPW_SKY)
fpw(m, 32.5, FPW_LOW)
fpl(m, 1.0, FPL_CHEST_, "out")
fpl(m, 28.0, FPL_CHEST_)
fpl(m, 32.5, FP_LOW)
use(m, "undyingPact")

# 투신 (효과: 0 등 뒤에 핏빛 투신의 화신이 솟음 · 10 화신이 두 팔을 활짝 펴 포효 · 28 화신이 두 주먹으로 앞 땅을 내리찍음 · 36 쓴 사람에게 스며듦): 무릎을 들어
# 땅을 힘껏 구르고 (3.6) → 두 팔을 가슴 앞에 엇갈려 웅크렸다가 → 화신과 함께 두 팔을 위 바깥으로 활짝 펴 포효 (떨림) → 두 주먹을 모아 머리 위로 들었다가 화신과
# 함께 땅을 내리찍음 (28 — 깊이 웅크림) → 화신이 스며들 때 가슴을 펴고 일어섬
m = off_bare(Motion("war_god", 42, "투신 — 땅을 구르고 웅크렸다가 등 뒤의 투신과 함께 두 팔을 활짝 펴 포효하고, 두 주먹을 모아 함께 땅을 내리찍은 뒤 가슴을 펴고 일어섬",
                    fade=(0.02, 0.08)))
keyp(m, 1.6, P(body=[-4, 0, 0], head=[-6, 0], ra=arm([0.6, 0.6, 0.4], 70, [0.2, 0.9, 0.3]), la=L(K.A_RAISE_FIST), **K.LEGS_STOMP_UP), "out")
STOMP = P(body=[14, 0, 0], head=[4, 0], ra=K.RA_SPREAD_DOWN, la=LA_SPREAD_DOWN_, **K.LEGS_STOMP)
keyp(m, 3.6, STOMP, "in")
GATHER2 = P(body=[24, 0, 0], head=[16, 0], ra=K.RA_ACROSS, la=L(A_CROSS), rl=[-26, 0, 10], ll=[-26, 0, -10], rk=56, lk=56, root=[-0.24, 0])
keyp(m, 8.0, GATHER2, "inout")
ROAR2 = P(body=[-18, 0, 0], head=[-30, 0], ra=K.RA_SPREAD_UP, la=L(K.A_SPREAD_UP), rl=[-6, 0, 12], ll=[-6, 0, -12], rk=16, lk=16, root=[-0.06, 0])
keyp(m, 10.4, ROAR2, "out3")
for i, tick in enumerate((12.5, 14.5, 16.5, 18.5)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(ROAR2, body=[-17 + s, s * 2, s * 1.2], head=[-29, s * 3]))
OVER = P(body=[-14, 0, 0], head=[-14, 0], ra=bent([0.1, 0.95, 0.1], 40, (0, 0.2, -1)), la=L(bent([0.1, 0.95, 0.1], 40, (0, 0.2, -1))), rl=[-10, 0, 10],
         ll=[-10, 0, -10], rk=20, lk=20, root=[0.02, 0])
keyp(m, 22.0, OVER, "inout")
keyp(m, 26.0, P(OVER, body=[-20, 0, 0], head=[-18, 0], root=[0.04, 0]))
POUND = P(body=[48, 0, 0], head=[18, 0], ra=bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3)), la=L(bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3))), **K.LEGS_SQUAT)
keyp(m, 28.2, P(POUND, root=[-0.42, 0.08]), "in")
keyp(m, 31.0, P(POUND, body=[44, 0, 0], root=[-0.4, 0.08]))
PROUD = P(body=[-8, 0, 0], head=[-10, 0], ra=arm([0.5, -0.65, 0.2], 30, [0.4, 0.3, 0.9]), la=L(arm([0.5, -0.65, 0.2], 30)), rl=[0, 0, 8], ll=[0, 0, -8],
          rk=4, lk=4, root=[0.02, 0])
keyp(m, 36.0, PROUD, "inout")
keyp(m, 40.5, P(PROUD, body=[-6, 0, 0]))
fpw(m, 1.6, FPW_UP, "out", dp=(0.25, 0, 0))
fpw(m, 3.6, K.FPW_SPREAD, "in")
fpw(m, 8.0, K.FPW_ACROSS, "inout")
fpw(m, 10.4, FPW_SKY, "out3", dp=(0.45, -0.25, 0))
fpw(m, 22.0, FPW_SKY, "inout")
fpw(m, 28.2, FPW_DOWN, "in")
fpw(m, 36.0, FPW_LOW, "inout")
fpl(m, 1.6, FP_RAISE, "out")
fpl(m, 3.6, FPL_SPREAD_DOWN_, "in")
fpl(m, 8.0, K.FP_CROSS, "inout")
fpl(m, 10.4, K.FP_SPREAD_UP, "out3")
fpl(m, 22.0, FP_RAISE, "inout")
fpl(m, 28.2, K.FP_GROUND, "in")
fpl(m, 36.0, FP_LOW, "inout")
use(m, "warGod")

# 초인 각성 (효과: 1 ~ 4 둘레의 3D 바위가 떨며 떠오르고 금빛 빛기둥이 내려옴 · 5 충격파 — 바위가 바깥으로 날아감): 깊이 웅크려 두 팔을 얼굴 앞에 엇갈리고 떨며
# 힘을 모음 → 5 에 터지듯 두 팔을 위 바깥으로 활짝 펴고 하늘을 보며 포효 (발끝으로 솟음 · 떨림) → 두 주먹을 허리 옆으로 내려 힘을 줌 (선 자세)
m = off_bare(Motion("awakening", 34, "초인 각성 — 웅크려 팔을 엇갈려 떨며 힘을 모았다가 충격파와 함께 두 팔을 활짝 펴 포효하고, 두 주먹을 허리 옆에 쥐고 섬",
                    fade=(0.02, 0.08)))
GATHER3 = P(body=[22, 0, 0], head=[18, 0], ra=arm([0.2, -0.05, 0.98], 120, [-0.9, 0.3, 0.2]), la=L(bent([0.2, -0.05, 0.98], 120, (-1, 0.5, 0))),
            rl=[-30, 0, 10], ll=[-30, 0, -10], rk=62, lk=62, root=[-0.28, 0])
keyp(m, 1.0, GATHER3, "out")
keyp(m, 2.6, P(GATHER3, body=[24, 0, 1.5], root=[-0.3, 0]))
keyp(m, 4.0, P(GATHER3, body=[25, 0, -1.5], root=[-0.32, 0]))
BURST2 = P(body=[-18, 0, 0], head=[-30, 0], ra=K.RA_SPREAD_UP, la=L(arm([0.6, 0.75, 0.2], 10)), rl=[0, 0, 12], ll=[0, 0, -12], rk=0, lk=0, root=[0.16, 0])
keyp(m, 5.4, BURST2, "out3")
for i, tick in enumerate((8.0, 10.5, 13.0, 15.5)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(BURST2, body=[-16 + s, 0, s * 1.2], head=[-27, s * 2], root=[0.1 - 0.015 * i, 0]))
POWER = P(body=[2, 0, 0], head=[-4, 0], ra=bent([0.45, -0.75, 0.2], 70, (0, 0.6, 0.8)), la=L(bent([0.45, -0.75, 0.2], 70, (0, 0.6, 0.8))), rl=[-8, 0, 12],
          ll=[-8, 0, -12], rk=16, lk=16, root=[-0.06, 0])
keyp(m, 21.0, POWER, "inout")
keyp(m, 26.0, P(POWER, body=[4, 0, 0], root=[-0.08, 0]))
keyp(m, 32.5, P(W_READY, body=[4, 0, 0]))
fpw(m, 1.0, K.FPW_ACROSS, "out")
fpw(m, 4.0, K.FPW_ACROSS)
fpw(m, 5.4, FPW_SKY, "out3", dp=(0.4, -0.2, 0))
fpw(m, 15.5, FPW_SKY, dp=(0.4, -0.2, 0))
fpw(m, 21.0, FPW_LOW, "inout")
fpw(m, 32.5, FPW_LOW)
fpl(m, 1.0, K.FP_CROSS, "out")
fpl(m, 4.0, K.FP_CROSS)
fpl(m, 5.4, K.FP_SPREAD_UP, "out3")
fpl(m, 15.5, K.FP_SPREAD_UP)
fpl(m, 21.0, FP_LOW, "inout")
fpl(m, 32.5, FP_LOW)
use(m, "awakening")


# ------------------------------------------------------------ 힘 · 마력 · 도끼 · 번개 (2026-11-13 묶음 E — 3D 망치 · 하늘의 검 · 구름 · 운석 · 도끼 · 뇌신의 창)

FPL_FACE_ = ((-0.1, 0.05, -0.05), (28, 62, 0))  # 1인칭 왼팔: 팔뚝으로 얼굴을 가림 (rune_motions_magic.FPL_FACE)

# 거인의 도약 (동작은 쓴 뒤 4틱 — motionAt: 동작 0 = 뛰어오른 지 4틱 · 8 = 착지): 공중에서 무릎을 높이 당겨 웅크리고 3D 망치를 두 손으로 머리 위 뒤로 크게 젖힘
# (몸이 활처럼) → 착지하며 (8) 온몸으로 앞 땅을 내려찍고 깊이 웅크림 → 박은 채 버팀 → 일어섬
m = Motion("titan_smash", 22, "거인의 도약 — 공중에서 무릎을 당겨 망치를 머리 위 뒤로 크게 젖혔다가, 착지하며 온몸으로 앞 땅을 내려찍고 깊이 웅크림",
           fade=(0.0, 0.1))
AIR = dict(rl=[-62, 0, 8], ll=[-48, 0, -8], rk=96, lk=88, root=[0.06, 0])
keyp(m, 0.0, P(K.W_HIGH2, body=[-18, 4, 0], head=[-14, 0], **AIR))
keyp(m, 4.5, P(K.W_HIGH2, body=[-30, 4, 0], head=[-22, 0], ra=arm([0.12, 0.8, -0.58], 45, [0, 0.1, -1]), **AIR), "out")
strike_with(m, "slam", 5.6, 8.2, 11.0, a=P(body=[-32, 4, 0], head=[-22, 0], **AIR),
            b=P(body=[52, 0, 0], head=[18, 0], root=[-0.46, 0.25], **LUNGE_DEEP))
keyp(m, 14.0, P(body=[48, 0, 0], head=[14, 0], ra=arm([0.05, -0.8, 0.6], 10, [0, -0.95, 0.3]), la=L(arm([0.05, -0.8, 0.6], 10)), root=[-0.44, 0.24],
                **LUNGE_DEEP))
keyp(m, 20.0, P(W_READY, body=[8, 0, 0]))
fpw(m, 0.0, FPW_SKY)
fpw(m, 4.5, FPW_SKY, "out", dp=(0.1, 0.1, 0.25))
fpw(m, 14.0, FPW_DOWN)
use(m, "titanSmash")

# 천붕 (효과: 0 ~ 16 하늘의 3D 거대한 검이 칼끝을 아래로 내려옴 · 16 ~ 24 떨어짐 · 24 꽂힘): 발끝으로 솟으며 무기와 왼손을 하늘로 쳐들어 검을 부르고 → 하늘을 붙든 채
# 떨며 버팀 (몸이 활처럼 점점 젖혀짐) → 16 에 두 팔로 하늘의 검을 끌어내리듯 힘껏 내리며 몸을 숙임 → 24 꽂히는 틱에 깊이 웅크려 무기 끝을 땅에 박고 버팀
m = Motion("heaven_fall", 34, "천붕 — 무기와 왼손을 하늘로 쳐들어 하늘의 검을 부르고 떨며 버티다가, 두 팔로 끌어내리듯 내려 검이 꽂힐 때 깊이 웅크려 무기를 땅에 박음",
           fade=(0.03, 0.08))
SKYC = P(body=[-14, -2, 0], head=[-28, 0], ra=K.RA_SKY, la=L(A_RAISE), rl=[0, 0, 10], ll=[0, 0, -10], rk=2, lk=2, root=[0.12, 0])
keyp(m, 3.0, SKYC, "out3")
for i, tick in enumerate((6.0, 9.0, 12.0, 14.5)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(SKYC, body=[-14 - 10 * q, -2, s * 1.2], head=[-28 - 6 * q, s * 2], root=[0.1 - 0.04 * q, 0]))
DRAG = P(body=[30, -8, 0], head=[10, 0], ra=arm([0.08, -0.35, 0.93], 12, [0, -0.5, 0.86]), la=L(arm([0.08, -0.35, 0.93], 12)), rl=[-20, 0, 6],
         ll=[14, 0, -4], rk=40, lk=24, root=[-0.2, 0.15])
keyp(m, 19.0, DRAG, "in")
keyp(m, 22.5, P(DRAG, body=[26, -8, 0], root=[-0.18, 0.15]))
IMPACT = P(body=[38, -6, 0], head=[14, 0], ra=K.RA_PLANT, la=L(arm([0.05, -0.6, 0.8], 10)), root=[-0.42, 0.1], **BRACE)
keyp(m, 24.4, IMPACT, "in")
keyp(m, 28.0, P(IMPACT, body=[34, -6, 0], root=[-0.38, 0.1]))
keyp(m, 32.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 14.5, FPW_SKY, dp=(0, 0.05, 0.1))
fpw(m, 19.0, FPW_DOWN, "in")
fpw(m, 28.0, FPW_DOWN, dp=(0, -0.05, 0))
fpw(m, 32.5, FPW_LOW)
fpl(m, 3.0, FP_RAISE, "out3")
fpl(m, 14.5, FP_RAISE)
fpl(m, 19.0, K.FP_DOWN, "in")
fpl(m, 32.5, FP_LOW)
use(m, "heavenFall")

# 블리자드 (효과: 3 ~ 9 조준한 곳 위에 3D 눈구름 · 10틱마다 눈보라): 무기와 왼손을 하늘로 쳐들어 크게 휘저어 구름을 모으고 (몸이 함께 돎) → 두 손을 앞 아래로 힘껏
# 내리 겨눠 눈보라를 쏟음 (내디딤) → 겨눈 채 10틱마다 몸이 조금씩 밀림 · 좌우로 몰아감
m = off_bare(Motion("blizzard", 44, "블리자드 — 무기와 왼손을 하늘로 쳐들어 크게 휘저어 눈구름을 모으고, 두 손을 앞으로 내리 겨눠 눈보라를 쏟으며 좌우로 몰아감",
                    fade=(0.03, 0.08)))
CALL2 = P(K.CALL, body=[-16, 0, 0], head=[-28, 0], root=[0.06, 0])
keyp(m, 2.4, CALL2, "out3")
for i, (d, yaw) in enumerate((([0.35, 0.92, 0.1], 18), ([0.05, 0.92, -0.35], 0), ([-0.3, 0.92, 0.1], -18), ([0.05, 0.92, 0.35], 0))):
    keyp(m, 3.8 + i * 1.5, P(CALL2, body=[-16, yaw, 0], ra=arm(d, 5, [d[0] * 1.4, 1, d[2] * 1.4])), "linear")
    fpw(m, 3.8 + i * 1.5, FPW_SKY, "linear", dp=(d[0] * 0.3, 0, -d[2] * 0.3))
POUR = P(body=[16, -10, 0], head=[4, 0], ra=K.RA_POINT_LOW, la=L(A_PALM), root=[-0.24, 0.3], **LUNGE_DEEP)
keyp(m, 10.6, POUR, "in")
for i in range(3):
    tick = 13.0 + i * 10
    yaw = (-14, 10, -6)[i]
    keyp(m, tick, P(POUR, body=[18, -10 + yaw, 0], root=[-0.27, 0.27]))
    keyp(m, tick + 4, P(POUR, body=[14, -10 + yaw * 0.5, 0], root=[-0.23, 0.3]))
keyp(m, 42.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 10.6, FPW_DOWN, "in", dp=(0, 0.2, -0.1))
fpw(m, 37.0, FPW_DOWN, dp=(0, 0.2, -0.1))
fpw(m, 42.0, FPW_LOW)
fpl(m, 2.4, FP_RAISE, "out3")
fpl(m, 9.0, FP_RAISE)
fpl(m, 10.6, FP_PALM, "in")
fpl(m, 37.0, FP_PALM)
fpl(m, 42.0, FP_LOW)
use(m, "blizzard")

# 메테오 (효과: 0 ~ 30 조준한 곳에 불의 룬 · 하늘에서 3D 운석이 나타나 떨어짐 · 30 떨어짐): 웅크려 두 팔을 아래에 모았다가 → 솟으며 두 팔을 하늘로 쳐들어 운석을
# 끌어내림 (떨며 점점 젖혀짐) → 무기로 떨어질 곳을 힘껏 가리킴 (내디딤 — 떨어지는 동안 겨눔) → 떨어지는 틱에 팔로 얼굴을 가리며 웅크려 버팀 (뒤로 밀림)
m = off_bare(Motion("meteor", 52, "메테오 — 웅크려 모았다가 솟으며 두 팔을 하늘로 쳐들어 운석을 끌어내리고, 무기로 떨어질 곳을 가리킨 뒤 떨어질 때 얼굴을 가리며 버팀",
                    fade=(0.03, 0.08)))
keyp(m, 1.6, P(body=[24, 0, 0], head=[14, 0], ra=K.A_SPREAD_DOWN, la=L(K.A_SPREAD_DOWN), **K.LEGS_SQUAT), "out")
CALL3 = P(K.CALL, body=[-14, 0, 0], head=[-28, 0], rl=[0, 0, 10], ll=[0, 0, -10], rk=2, lk=2, root=[0.12, 0])
keyp(m, 4.6, CALL3, "out3")
for i, tick in enumerate((7.0, 9.5, 12.0)):
    q = (i + 1) / 3.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(CALL3, body=[-14 - 10 * q, 0, s * 1.2], head=[-28 - 4 * q, 0], root=[0.1, 0]))
POINT3 = P(body=[14, -14, 0], head=[2, 0], ra=K.RA_POINT_LOW, la=L(A_HIP), root=[-0.2, 0.28], **LUNGE_DEEP)
keyp(m, 15.6, POINT3, "in")
keyp(m, 22.0, P(POINT3, body=[12, -12, 0], root=[-0.18, 0.28]))
keyp(m, 28.0, P(POINT3, body=[10, -10, 0], root=[-0.16, 0.26]))
GUARD3 = P(body=[24, -6, 0], head=[14, 0], ra=K.RA_LOW, la=L(K.A_SHIELD), root=[-0.36, -0.18], **BRACE)
keyp(m, 30.6, GUARD3, "out3")
keyp(m, 36.0, P(GUARD3, body=[20, -6, 0], root=[-0.32, -0.22]))
keyp(m, 46.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 1.6, FPW_LOW, "out")
fpw(m, 4.6, FPW_SKY, "out3")
fpw(m, 12.0, FPW_SKY)
fpw(m, 15.6, FPW_DOWN, "in", dp=(0, 0.15, -0.1))
fpw(m, 28.0, FPW_DOWN, dp=(0, 0.15, -0.1))
fpw(m, 30.6, FPW_LOW, "out3")
fpw(m, 46.0, FPW_LOW)
fpl(m, 1.6, FP_LOW, "out")
fpl(m, 4.6, FP_RAISE, "out3")
fpl(m, 12.0, FP_RAISE)
fpl(m, 15.6, FP_LOW)
fpl(m, 30.6, FPL_FACE_, "out3")
fpl(m, 36.0, FPL_FACE_)
fpl(m, 46.0, FP_LOW)
use(m, "meteor")

# 처형 (효과: 0 ~ 8 대상 위의 3D 룬 도끼가 커지며 한 바퀴 돌아 날이 아래로 · 8 내리꽂힘): 무릎을 들며 무기를 두 손으로 천천히 머리 위 뒤로 크게 젖힘 (몸이 활처럼)
# → 8 에 온몸으로 내려찍으며 깊이 내디딤 → 내리찍은 채 머묾 → 일어섬
m = Motion("execute", 24, "처형 — 무릎을 들며 무기를 머리 위 뒤로 천천히 크게 젖혔다가, 대상 위의 도끼와 함께 온몸으로 내려찍으며 깊이 내디딤", fade=(0.02, 0.1))
keyp(m, 3.0, P(K.W_HIGH2, body=[-20, 6, 0], head=[-10, 0], **K.LEGS_STOMP_UP), "out")
keyp(m, 5.4, P(K.W_HIGH2, body=[-28, 6, 0], head=[-14, 0], ra=arm([0.12, 0.82, -0.56], 45, [0, 0.1, -1]), **K.LEGS_STOMP_UP))
strike_with(m, "chop", 6.0, 8.2, 11.0, a=P(body=[-28, 6, 0], head=[-14, 0], **K.LEGS_STOMP_UP), b=P(body=[46, -4, 0], head=[14, 0], root=[-0.4, 0.5],
                                                                                                   **LUNGE_DEEP))
keyp(m, 15.0, P(body=[42, -4, 0], head=[12, 0], ra=arm([0.05, -0.75, 0.66], 10, [0, -0.9, 0.4]), la=L(A_HIP), root=[-0.38, 0.48], **LUNGE_DEEP))
keyp(m, 22.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 3.0, FPW_SKY, "out")
fpw(m, 5.4, FPW_SKY, dp=(0.1, 0.1, 0.25))
fpw(m, 15.0, FPW_DOWN)
use(m, "execute")

# 광전사의 혼 (효과: 0 광폭화 — 핏빛 터짐 · 몸 둘레를 도는 3D 룬 도끼 둘 · 20틱마다 난타): 웅크려 몸을 떨며 분노를 모았다가 → 4 에 터지듯 가슴을 펴고 무기와 주먹을
# 치켜들며 포효 (떨림) → 낮고 넓게 서서 무기를 앞에 겨눈 광전사의 자세로
m = Motion("berserker", 30, "광전사의 혼 — 웅크려 떨며 분노를 모았다가 터지듯 가슴을 펴고 무기와 주먹을 치켜들며 포효한 뒤, 낮고 넓게 서서 무기를 겨눔",
           fade=(0.02, 0.1))
RAGE = P(body=[30, 0, 0], head=[22, 0], ra=arm([0.35, -0.6, 0.55], 95, [0, 0.4, 0.9]), la=L(K.A_CHAMBER), rl=[-24, 0, 8], ll=[-24, 0, -8], rk=56, lk=56,
         root=[-0.26, 0])
keyp(m, 1.0, RAGE, "out")
keyp(m, 2.4, P(RAGE, body=[32, 0, 2]))
keyp(m, 3.4, P(RAGE, body=[31, 0, -2]))
HOWL = P(body=[-22, 0, 0], head=[-34, 0], ra=arm([0.7, 0.65, 0.15], 25, [0.3, 1, 0.1]), la=L(K.A_RAISE_FIST), rl=[-6, 0, 12], ll=[-6, 0, -12], rk=16, lk=16,
         root=[0.02, 0])
keyp(m, 4.6, HOWL, "out3")
for i, tick in enumerate((7.0, 9.0, 11.0, 13.0)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(HOWL, body=[-21 + s, s * 2, s * 1.5], head=[-33, s * 3]))
STANCE = P(body=[18, 22, 0], head=[-4, -16], ra=arm([0.3, -0.25, 0.9], 60, [0.1, 0.8, 0.5]), la=L(bent([0.4, -0.4, 0.6], 90, (0, 0.5, 0.8))), rl=[-28, 0, 14],
           ll=[-28, 0, -14], rk=60, lk=60, root=[-0.28, 0])
keyp(m, 18.0, STANCE, "inout")
keyp(m, 24.0, P(STANCE, body=[20, 24, 0], root=[-0.3, 0]))
keyp(m, 28.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, FPW_LOW, "out")
fpw(m, 4.6, FPW_SKY, "out3", dp=(0.3, -0.1, 0))
fpw(m, 13.0, FPW_SKY, dp=(0.3, -0.12, 0))
fpw(m, 18.0, FPW_FWD, "inout")
fpw(m, 28.5, FPW_LOW)
use(m, "berserker")

# 뇌운 (효과: 2.5 ~ 8.5 조준한 곳 위에 3D 먹구름 · 12 부터 10틱마다 번개): 무기를 하늘로 치켜들며 뛰어오르듯 솟고 → 몸째 크게 돌리며 머리 위로 휘저어 구름을 모음 →
# 12.4 에 앞으로 힘껏 내리 겨눠 번개를 부름 (내디딤) → 겨눈 채 번개마다 움찔
m = Motion("thundercloud", 36, "뇌운 — 무기를 하늘로 치켜들고 몸째 크게 휘저어 먹구름을 모은 뒤, 앞으로 힘껏 내리 겨눠 번개를 부름", fade=(0.03, 0.08))
SKY2 = P(body=[-14, -4, 0], head=[-26, 0], ra=K.RA_SKY, la=L(A_HIP), rl=[0, 0, 10], ll=[0, 0, -10], rk=2, lk=2, root=[0.12, 0])
keyp(m, 2.4, SKY2, "out3")
fpw(m, 2.4, FPW_SKY, "out3")
for i, (d, yaw) in enumerate((([0.4, 0.9, 0.1], 22), ([0.05, 0.9, -0.45], 0), ([-0.4, 0.9, 0.1], -22), ([0.05, 0.9, 0.45], 0), ([0.4, 0.9, 0.1], 22))):
    keyp(m, 3.8 + i * 1.6, P(SKY2, body=[-14, -4 + yaw, 0], ra=arm(d, 5, [d[0] * 1.4, 1, d[2] * 1.4]), root=[0.06, 0]), "linear")
    fpw(m, 3.8 + i * 1.6, FPW_SKY, "linear", dp=(d[0] * 0.3, 0, -d[2] * 0.3))
AIM3 = P(body=[14, -12, 0], head=[2, 0], ra=K.RA_POINT, la=L(A_HIP), root=[-0.2, 0.3], **LUNGE_DEEP)
keyp(m, 12.4, AIM3, "in")
for tick in (14.5, 24.5):
    keyp(m, tick, P(AIM3, body=[10, -12, 0], root=[-0.16, 0.24]), "out")
    keyp(m, tick + 3, P(AIM3, body=[13, -12, 0], root=[-0.19, 0.29]))
keyp(m, 34.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 12.4, FPW_FWD, "in")
fpw(m, 30.0, FPW_FWD)
fpw(m, 34.0, FPW_LOW)
use(m, "thundercloud")

# 뇌신의 창 (효과: 0 ~ 20 오른손 위에서 3D 번개 창이 커지며 겨눔 · 20 던짐): 한 발 물러서며 오른손을 머리 뒤로 높이 들어 창을 쥐고 몸을 크게 비틀어 왼손으로 겨눔 →
# 떨며 점점 더 뒤로 젖힘 → 20 에 깊이 내디디며 온몸을 돌려 힘껏 내던짐 → 던진 팔이 앞 아래로 지나감
m = K.fists(Motion("thunder_spear", 34, "뇌신의 창 — 한 발 물러서며 오른손을 머리 뒤로 높이 들어 번개 창을 쥐고 몸을 비틀어 겨누다가, 깊이 내디디며 온몸으로 "
                   "내던짐", fade=(0.02, 0.08)))
JAV2 = P(body=[-8, 48, 0], head=[-6, -30], ra=bent([0.42, 0.75, -0.5], 70, (0, 0.6, 1)), la=L(arm([-0.1, 0.3, 0.95], 4)), rl=[26, 0, 4], ll=[-30, 0, -2],
         rk=30, lk=22, root=[-0.12, -0.25])
keyp(m, 2.6, JAV2, "out3")
for i, tick in enumerate((6.0, 9.5, 13.0, 16.5)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(JAV2, body=[-8 - 6 * q, 48 + 6 * q, s * 1.2], root=[-0.12 - 0.04 * q, -0.25 - 0.05 * q]))
keyp(m, 19.0, P(JAV2, body=[-16, 58, 0], ra=bent([0.45, 0.7, -0.6], 80, (0, 0.6, 1)), root=[-0.18, -0.32]), "out")
THROW = P(body=[22, -42, 0], head=[4, 20], ra=K.A_THROW_REL, la=arm([-0.45, -0.6, -0.4], 30), root=[-0.3, 0.55], **LUNGE_DEEP)
keyp(m, 20.6, THROW, "out3")
keyp(m, 25.0, P(THROW, body=[20, -36, 0], ra=K.A_THROW_FOLLOW, root=[-0.28, 0.55]))
keyp(m, 32.0, P(body=[6, 0, 0], ra=K.A_GUARD, la=L(K.A_GUARD), **LEGS_FIGHT))
K.fpr(m, 2.6, nudge(FP_RAISE, (0.1, 0.05, 0.12)), "out3")
K.fpr(m, 19.0, nudge(FP_RAISE, (0.14, 0.08, 0.2)))
K.fpr(m, 20.6, K.FP_JAB2, "out3")
K.fpr(m, 25.0, FP_LOW)
K.fpl(m, 2.6, K.FP_JAB, "out3")
K.fpl(m, 19.0, K.FP_JAB)
K.fpl(m, 20.6, FP_LOW, "out3")
use(m, "thunderSpear")


# ------------------------------------------------------------ 투사 · 심연 · 해류 · 골렘 (2026-11-13 묶음 F — 영웅 · 초월의 짧던 동작)

# 투기장 (효과: 2 ~ 5 둘레의 기둥이 솟아 사슬 울타리 · 10 부터 20틱마다 하늘에서 사슬이 내리침 · 84 모두 모여 내리침): 무기를 하늘로 높이 치켜들고 왼 주먹을 가슴에
# (몸을 젖혀 외침) → 무기를 머리 위로 크게 한 바퀴 휘둘러 둘레를 가리킴 (투기장) → 10 에 발을 구르며 무기로 앞의 적을 겨눔 (도전) → 싸울 자세로 숨 쉬며 기다림
m = off_bare(Motion("arena_domain", 40, "투기장 — 무기를 치켜들고 주먹을 가슴에 대 외친 뒤, 머리 위로 한 바퀴 휘둘러 투기장을 열고 발을 구르며 앞의 적을 겨눔",
                    fade=(0.03, 0.08)))
SALUTE2 = P(body=[-16, -4, 0], head=[-26, 0], ra=K.RA_SKY, la=L(A_CHEST), rl=[0, 0, 10], ll=[0, 0, -10], rk=4, lk=4, root=[0.06, 0])
keyp(m, 2.4, SALUTE2, "out3")
keyp(m, 4.6, P(SALUTE2, body=[-18, -4, 0], head=[-28, 0]))
for i, (d, yaw) in enumerate((([0.6, 0.75, 0.2], 24), ([0.1, 0.7, -0.7], 0), ([-0.6, 0.75, 0.2], -24))):
    keyp(m, 5.8 + i * 1.2, P(SALUTE2, body=[-10, yaw, 0], ra=arm(d, 8, [d[0], 1, d[2]])), "linear")
    fpw(m, 5.8 + i * 1.2, FPW_SKY, "linear", dp=(d[0] * 0.3, -0.1, -d[2] * 0.3))
ENGAGE2 = P(body=[12, -18, 0], head=[2, 0], ra=K.RA_POINT, la=L(A_GUARD), root=[-0.2, 0.3], **LUNGE_DEEP)
keyp(m, 10.2, ENGAGE2, "in")
for i, tick in enumerate((15.0, 20.0, 25.0, 30.0, 35.0)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(ENGAGE2, body=[10 + s, -18 + s * 3, 0], root=[-0.18 - 0.02 * s, 0.3]))
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 10.2, FPW_FWD, "in")
fpw(m, 35.0, FPW_FWD)
fpl(m, 2.4, FPL_CHEST_, "out3")
fpl(m, 9.0, FPL_CHEST_)
fpl(m, 10.2, K.FP_GUARD, "in")
fpl(m, 35.0, K.FP_GUARD)
use(m, "arenaDomain")

# 투신의 사슬 (효과: 0 ~ 16 조준한 곳 둘레에서 사슬 기둥 여섯이 솟아 기욺 · 16 묶음 · 16 부터 10틱마다 조임 · 78 하늘의 거대한 철퇴 — 철퇴는 단계 동작
# war_chains_slam 을 70 에 다시 보냄): 깊이 웅크려 두 손을 땅으로 뻗어 사슬을 쥐고 → 일어서며 두 팔을 하늘로 끌어올림 (기둥이 솟음) → 16 에 두 주먹을 쥐어 가슴으로
# 힘껏 당겨 묶음 (몸을 뒤로 젖혀 버팀) → 조일 때마다 다시 힘껏 당김
m = off_bare(Motion("war_chains", 60, "투신의 사슬 — 웅크려 땅에서 사슬을 쥐어 하늘로 끌어올리고, 묶을 때 두 주먹으로 힘껏 당겨 조이며 조일 때마다 다시 당김",
                    fade=(0.03, 0.06)))
keyp(m, 0.8, P(body=[30, 0, 0], head=[10, 0], ra=arm([0.4, -0.8, 0.45], 10, [0.2, -0.6, 0.75]), la=L(arm([0.4, -0.8, 0.45], 10)), **K.LEGS_SQUAT),
     "out")
RAISE3 = P(body=[-16, 0, 0], head=[-24, 0], ra=K.RA_SKY, la=L(K.A_RAISE_FIST), rl=[0, 0, 10], ll=[0, 0, -10], rk=4, lk=4, root=[0.08, 0])
keyp(m, 6.0, RAISE3, "inout")
keyp(m, 13.0, P(RAISE3, body=[-18, 0, 0]))
SQUEEZE2 = P(body=[-10, 0, 0], head=[-4, 0], ra=bent([0.4, -0.35, 0.25], 120, (0, 0.6, 1)), la=L(K.A_PULL), rl=[34, 0, 10], ll=[-6, 0, -10], rk=40,
             lk=18, root=[-0.2, -0.18])
keyp(m, 16.6, SQUEEZE2, "in")
for tick in (26.0, 36.0, 46.0, 56.0):
    keyp(m, tick - 2.5, P(SQUEEZE2, body=[-4, 0, 0], root=[-0.16, -0.1]), "out")
    keyp(m, tick + 0.6, P(SQUEEZE2, body=[-14, 0, 0], root=[-0.22, -0.22]), "in")
keyp(m, 59.0, P(SQUEEZE2, body=[-10, 0, 0]))
fpw(m, 0.8, FPW_DOWN, "out")
fpw(m, 6.0, FPW_SKY, "inout")
fpw(m, 13.0, FPW_SKY)
fpw(m, 16.6, FPW_LOW, "in")
fpw(m, 59.0, FPW_LOW)
fpl(m, 0.8, K.FP_GROUND, "out")
fpl(m, 6.0, FP_RAISE, "inout")
fpl(m, 13.0, FP_RAISE)
fpl(m, 16.6, FP_CHAMBER, "in")
fpl(m, 59.0, FP_CHAMBER)
use(m, "warChains")

# 투신의 사슬 철퇴 (단계 — 효과 70 에 보냄 · 8 이 철퇴가 내리꽂히는 때): 두 주먹을 모아 머리 위로 높이 들었다가 하늘의 철퇴와 함께 힘껏 내리침 → 버팀
m = off_bare(Motion("war_chains_slam", 18, "투신의 사슬 철퇴 — 두 주먹을 머리 위로 모아 들었다가 하늘의 철퇴와 함께 힘껏 내리침", fade=(0.06, 0.12)))
OVER2 = P(body=[-18, 0, 0], head=[-20, 0], ra=bent([0.1, 0.95, 0.1], 40, (0, 0.2, -1)), la=L(bent([0.1, 0.95, 0.1], 40, (0, 0.2, -1))), rl=[0, 0, 10],
          ll=[0, 0, -10], rk=10, lk=10, root=[0.04, 0])
keyp(m, 4.0, OVER2, "out")
keyp(m, 6.6, P(OVER2, body=[-24, 0, 0], head=[-22, 0]))
keyp(m, 8.4, P(P(body=[46, 0, 0], head=[16, 0], ra=bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3)), la=L(bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3))),
                 **K.LEGS_SQUAT), root=[-0.42, 0.08]), "in")
keyp(m, 12.0, P(P(body=[42, 0, 0], head=[14, 0], ra=bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3)), la=L(bent([0.12, -0.85, 0.5], 30, (0, 1, 0.3))),
                  **K.LEGS_SQUAT), root=[-0.38, 0.08]))
fpw(m, 4.0, FPW_SKY, "out")
fpw(m, 8.4, FPW_DOWN, "in")
fpl(m, 4.0, FP_RAISE, "out")
fpl(m, 8.4, K.FP_GROUND, "in")
fpl(m, 16.0, FP_LOW)
use(m, "warChains:slam")

# 심연의 낙인 (효과: 0 ~ 14 조준한 곳의 심연이 열려 사슬이 감김 · 14 낙인): 몸을 비틀며 왼손을 조준한 곳으로 크게 뿌려 심연을 엶 (내디딤) → 손가락을 갈퀴처럼 편 채
# 떨며 사슬을 휘감음 (손이 점점 오므라듦) → 14 에 주먹을 움켜쥐어 가슴으로 힘껏 당기며 몸을 뒤로 젖힘 → 노려보며 머묾
m = off_bare(Motion("abyss_brand", 30, "심연의 낙인 — 몸을 비틀며 왼손을 뿌려 심연을 열고, 갈퀴 같은 손으로 떨며 사슬을 감다가 낙인이 찍힐 때 주먹을 움켜쥐어 힘껏 당김",
                    fade=(0.02, 0.08)))
keyp(m, 0.0, P(body=[4, 26, 0], ra=K.RA_READY, la=L(bent([-0.5, 0.05, 0.6], 80, (1, 0.3, 0.3))), **LEGS_FIGHT))
CAST2 = P(body=[12, -18, 0], head=[2, 0], ra=K.RA_LOW, la=L(arm([-0.02, 0.05, 1], 4, [0, 1, 0.1])), root=[-0.16, 0.3], **LUNGE_DEEP)
keyp(m, 1.2, CAST2, "out3")
for i, tick in enumerate((4.0, 6.5, 9.0, 11.5)):
    q = (i + 1) / 4.0
    s = 1 if i % 2 else -1
    keyp(m, tick, P(CAST2, body=[12, -18, s * 1.2], la=L(bent([-0.02, 0.05, 1], 20 + 40 * q, (0, 1, 0.2)))))
YANK = P(body=[-8, 14, 0], head=[-6, 0], ra=K.RA_LOW, la=L(K.A_PULL), rl=[30, 0, 8], ll=[-8, 0, -8], rk=36, lk=14, root=[-0.16, -0.2])
keyp(m, 14.4, YANK, "in")
keyp(m, 19.0, P(YANK, body=[-6, 12, 0]))
keyp(m, 28.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 28.0, FPW_LOW)
fpl(m, 0.0, ((-0.2, 0.0, 0.0), (15, 75, 0)))
fpl(m, 1.2, FP_PALM, "out3")
fpl(m, 11.5, nudge(FP_PALM, (0.02, -0.02, 0.06), (10, 0, 0)))
fpl(m, 14.4, FP_CHAMBER, "in")
fpl(m, 19.0, FP_CHAMBER)
fpl(m, 28.0, FP_LOW)
use(m, "abyssBrand")

# 해일 (효과: 0 부터 앞으로 거대한 파도): 깊이 웅크려 두 팔을 가슴으로 당긴 채 시작 → 터지듯 깊이 내디디며 두 손을 앞으로 힘껏 밀어냄 (1.6) → 민 채 떨며 버팀 → 섬
m = off_bare(Motion("tidal_wave", 28, "해일 — 웅크려 두 팔을 가슴으로 당겼다가 깊이 내디디며 두 손을 앞으로 힘껏 밀어 거대한 파도를 일으키고 민 채 버팀",
                    fade=(0.0, 0.08)))
keyp(m, 0.0, P(body=[14, 18, 0], head=[6, 0], ra=bent([0.4, -0.35, -0.2], 120, (0, 0.6, 1)), la=L(K.A_CHAMBER), **K.LEGS_SQUAT))
PUSH3 = P(body=[22, -6, 0], head=[4, 0], ra=arm([0.12, 0.05, 1], 4, [0, 0.6, 0.8]), la=L(arm([0.12, 0.05, 1], 2, [0, 1, 0.1])), root=[-0.3, 0.6],
          **LUNGE_DEEP)
keyp(m, 1.6, PUSH3, "out3")
for i, tick in enumerate((4.0, 7.0, 10.0)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(PUSH3, body=[20, -6, s * 1.2], root=[-0.28, 0.58]))
keyp(m, 14.0, P(PUSH3, body=[16, -4, 0], root=[-0.24, 0.52]))
keyp(m, 26.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW, dp=(0, 0.1, 0.1))
fpw(m, 1.6, FPW_FWD, "out3", dp=(0.2, -0.1, 0))
fpw(m, 14.0, FPW_FWD, dp=(0.2, -0.1, 0))
fpw(m, 26.0, FPW_LOW)
fpl(m, 0.0, FP_CHAMBER)
fpl(m, 1.6, nudge(FP_PALM, (0.1, 0, 0), (0, -15, 0)), "out3")
fpl(m, 14.0, nudge(FP_PALM, (0.1, 0, 0), (0, -15, 0)))
fpl(m, 26.0, FP_LOW)
use(m, "tidalWave")

# 골렘 소환 (효과: 1 ~ 6 앞 땅에서 골렘이 솟음 · 16 골렘과 함께 내려침): 무릎을 꿇으며 두 손바닥으로 땅을 힘껏 내리쳐 짚고 (골렘이 솟는 동안 떨며 누름) → 일어서며 두 주먹을
# 머리 위로 높이 들어 몸을 젖힘 → 16 에 골렘과 함께 두 주먹으로 내려침 (깊이 웅크림) → 섬
m = K.fists(Motion("golem_summon", 30, "골렘 소환 — 무릎을 꿇으며 두 손바닥으로 땅을 내리쳐 골렘을 깨우고, 일어서며 두 주먹을 치켜들었다가 골렘과 함께 내려침",
                   fade=(0.02, 0.08)))
KNEEL = P(body=[46, 0, 0], head=[14, 0], ra=K.A_GROUND, la=L(K.A_GROUND), **K.LEGS_KNEEL)
keyp(m, 1.4, KNEEL, "in")
for i, tick in enumerate((3.0, 4.6, 6.2)):
    s = 1 if i % 2 else -1
    keyp(m, tick, P(KNEEL, body=[44 + s, 0, s * 1.2]))
OVER3 = P(body=[-20, 0, 0], head=[-14, 0], ra=K.A_OVER, la=L(K.A_OVER), rl=[0, 0, 10], ll=[0, 0, -10], rk=6, lk=6, root=[0.06, 0])
keyp(m, 11.0, OVER3, "inout")
keyp(m, 14.0, P(OVER3, body=[-26, 0, 0], head=[-18, 0]))
SMASH = P(body=[44, 0, 0], head=[14, 0], ra=K.A_DOWN_FWD, la=L(K.A_DOWN_FWD), root=[-0.4, 0.08], **BRACE)
keyp(m, 16.2, SMASH, "in")
keyp(m, 20.0, P(SMASH, body=[40, 0, 0], root=[-0.36, 0.08]))
keyp(m, 28.0, P(body=[6, 0, 0], ra=K.A_GUARD, la=L(K.A_GUARD), **LEGS_FIGHT))
K.fpb(m, 1.4, K.FP_GROUND, ease="in")
K.fpb(m, 6.2, K.FP_GROUND)
K.fpb(m, 11.0, FP_RAISE)
K.fpb(m, 14.0, FP_RAISE)
K.fpb(m, 16.2, K.FP_DOWN, ease="in")
K.fpb(m, 20.0, K.FP_DOWN)
K.fpb(m, 28.0, FP_LOW)
use(m, "golemSummon")
