"""0.46.0 룬 동작 — 맨손 (무투 · 골렘): 주먹 · 장풍 · 날아차기 · 땅 치기. 무기를 숨기고 두 손 맨팔 (1인칭도 주먹이 나감)."""
import rune_motion_kit as K
from rune_motion_kit import (A_BACK, A_CHAMBER, A_CROSS, A_DOWN_FWD, A_GROUND, A_GUARD, A_HIP, A_JAB, A_JAB_HI, A_JAB_LO, A_OVER, A_PALM,
                             A_PALM_LOW, A_RAISE, A_RAISE_FIST, A_RAISE_FWD, A_SPREAD, A_SPREAD_UP, A_UPPER, FIGHT, FP_CHAMBER, FP_DOWN, FP_GUARD, FP_JAB,
                             FP_JAB2, FP_LOW, FP_PALM, FP_RAISE, FP_UPPER, FP_GATHER_R, FP_GATHER_L, FP_GROUND, FP_CROSS, FP_PUSH_OUT, FP_SPREAD_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_STOMP, LEGS_STOMP_UP, LEGS_WIDE, P, arm,
                             bent, fists, fpb, fpl, fpr, keyp, nudge, use)

Motion = K.Motion

# 주먹을 뻗은 몸 (오른 주먹 — 몸을 왼쪽으로 틀며 앞발로 디딤)
PUNCH_R = P(FIGHT, body=[10, -28, 0], ra=A_JAB, la=L(A_GUARD), head=[2, 0], **LEGS_LUNGE)
PUNCH_L = P(FIGHT, body=[10, 22, 0], ra=A_GUARD, la=L(A_JAB), head=[2, 0], **LEGS_LUNGE)
CHAMBER_R = P(FIGHT, body=[4, 18, 0], ra=A_CHAMBER, la=L(A_GUARD))
CHAMBER_L = P(FIGHT, body=[4, -20, 0], ra=A_GUARD, la=L(A_CHAMBER))
PALMS = P(FIGHT, body=[14, -6, 0], ra=A_PALM, la=L(A_PALM), head=[4, 0], **LEGS_LUNGE)
GATHER = P(FIGHT, body=[2, 34, 0], ra=A_CHAMBER, la=bent([0.15, -0.55, 0.55], 105, (1, 0.3, 0.2)), rk=34, lk=30, root=[-0.12, 0])


def fp_guard(m, tick, ease=None):
    fpb(m, tick, FP_GUARD, ease=ease)


def fp_jab(m, tick, right=True, hi=0.0, side=0.0, ease="out3", strong=False):
    pre = nudge(FP_JAB2 if strong else FP_JAB, (0, hi * 0.5, 0), (hi * 60, side * 60, 0))
    if right:
        fpr(m, tick, pre, ease)
        fpl(m, tick, FP_GUARD)
    else:
        fpl(m, tick, pre, ease)
        fpr(m, tick, FP_GUARD)


# ------------------------------------------------------------ 무투

# 천권 강림: 오른 주먹을 하늘로 들어 거대한 주먹을 부름 (0 ~ 16) → 주먹 비 (20 · 30 · 40 · 50) 에 맞춰 좌우로 내려 찍는 주먹 → 거둠
m = fists(Motion("heaven_fist", 62, "천권 강림 — 주먹을 하늘로 들어 거대한 주먹을 부르고, 쏟아지는 주먹 비에 맞춰 좌우로 내려 찍음", fade=(0.04, 0.08)))
RAISED = P(body=[-10, -6, 0], ra=A_RAISE_FIST, la=L(A_HIP), head=[-24, 0], **LEGS_WIDE)
keyp(m, 4, RAISED, "out3")
keyp(m, 10, P(RAISED, body=[-12, -4, 0]))
keyp(m, 16, P(RAISED, body=[-9, -8, 0], head=[-20, 0]))
fpr(m, 4, FP_RAISE, "out3")
fpl(m, 4, FP_LOW)
fpr(m, 16, nudge(FP_RAISE, (0, -0.03, 0)))
fpl(m, 16, FP_LOW)
for i, tick in enumerate((20, 30, 40, 50)):
    right = i % 2 == 0
    keyp(m, tick - 3, P(CHAMBER_R if right else CHAMBER_L, root=[0.02, 0]))
    keyp(m, tick, P(PUNCH_R if right else PUNCH_L, ra=A_JAB_LO if right else A_GUARD, la=L(A_GUARD) if right else L(A_JAB_LO)), "out3")
    if right:
        fpr(m, tick - 3, FP_CHAMBER)
        fpl(m, tick - 3, FP_GUARD)
    else:
        fpl(m, tick - 3, FP_CHAMBER)
        fpr(m, tick - 3, FP_GUARD)
    fp_jab(m, tick, right, -0.1, strong=True)
keyp(m, 55, FIGHT)
fp_guard(m, 55)
use(m, "heavenFist")

# ------------------------------------------------------------ 골렘 (맨손으로 땅을 다룸)

# 거신 강림: 무릎 꿇고 땅을 짚음 → 두 팔을 천천히 들어 올려 거신을 일으킴 (6 ~ 24) → 거신이 울부짖을 때 (28) 팔을 펼침
m = fists(Motion("colossus", 36, "거신 강림 — 땅을 짚고 두 팔을 천천히 들어 거신을 일으킨 뒤 함께 포효", fade=(0.03, 0.1)))
keyp(m, 2, P(body=[42, 0, 0], ra=A_GROUND, la=L(A_GROUND), **LEGS_KNEEL), "out3")
keyp(m, 8, P(body=[30, 0, 0], ra=A_PALM_LOW, la=L(A_PALM_LOW), rl=[-40, 0, 6], rk=60, ll=[-40, 0, -6], lk=60, root=[-0.22, 0]))
keyp(m, 16, P(body=[0, 0, 0], ra=A_RAISE_FWD, la=L(A_RAISE_FWD), head=[-14, 0], **LEGS_WIDE))
keyp(m, 24, P(body=[-12, 0, 0], ra=A_RAISE, la=L(A_RAISE), head=[-26, 0], **LEGS_WIDE))
keyp(m, 28.5, P(body=[-16, 0, 0], ra=A_SPREAD_UP, la=L(A_SPREAD_UP), head=[-28, 0], **LEGS_WIDE), "out3")
keyp(m, 32, P(body=[-12, 0, 0], ra=A_SPREAD_UP, la=L(A_SPREAD_UP), head=[-22, 0], **LEGS_WIDE))
fpb(m, 2, FP_GROUND, ease="out3")
fpb(m, 8, FP_DOWN)
fpb(m, 16, nudge(FP_PALM, (0, 0.1, 0.1), (20, -10, 0)))
fpb(m, 24, FP_RAISE)
fpb(m, 28.5, FP_SPREAD_UP, ease="out3")
fpb(m, 32, FP_SPREAD_UP)
use(m, "colossus")
