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

# 연환권: 오른 주먹 (0) · 왼 주먹 (10) · 오른 주먹 (20) · 두 손바닥 장풍 (30) — 효과가 박자 틱에 손에서 나가므로 뻗음이 0 ~ 2 · 10 ~ 12 · 20 ~ 22 · 30 ~ 32 에 다 뻗게
m = fists(Motion("chain_fist", 40, "연환권 — 오른 · 왼 · 오른 주먹을 내지르고 두 손바닥으로 장풍", fade=(0.03, 0.1)))
keyp(m, 1.5, PUNCH_R, "out3")
keyp(m, 4.5, FIGHT)
keyp(m, 8, CHAMBER_L)
keyp(m, 11, PUNCH_L, "out3")
keyp(m, 14.5, FIGHT)
keyp(m, 18, CHAMBER_R)
keyp(m, 21, P(PUNCH_R, body=[12, -34, 0]), "out3")
keyp(m, 24.5, FIGHT)
keyp(m, 27.5, GATHER)
keyp(m, 31, PALMS, "out3")
keyp(m, 35, PALMS)
fp_jab(m, 1.5, strong=True)
fp_guard(m, 4.5)
fpl(m, 8, FP_CHAMBER)
fpr(m, 8, FP_GUARD)
fp_jab(m, 11, right=False, strong=True)
fp_guard(m, 14.5)
fpr(m, 18, FP_CHAMBER)
fpl(m, 18, FP_GUARD)
fp_jab(m, 21, hi=0.05, strong=True)
fp_guard(m, 24.5)
fpb(m, 27.5, FP_GATHER_R, FP_GATHER_L)
fpb(m, 31, FP_PALM, ease="out3")
fpb(m, 35, FP_PALM)
use(m, "chainFist")

# 백열권: 두 틱마다 좌우 번갈아 주먹 (0 ~ 48 — 높이 · 옆을 조금씩 바꿔 퍼부음) → 오른 주먹을 크게 당겼다가 (47) 마무리 일격 (50)
m = fists(Motion("hundred_fists", 58, "백열권 — 좌우 주먹을 쉴 새 없이 퍼붓다가 크게 당겨 마무리 일격", fade=(0.02, 0.08)))
VAR = [(A_JAB, 0.0, 0.0), (A_JAB_HI, 0.08, 0.05), (A_JAB_LO, -0.08, -0.06), (A_JAB, 0.04, 0.08), (A_JAB_HI, 0.1, -0.05), (A_JAB_LO, -0.05, 0.03)]
for i in range(24):
    tick = 1 + 2 * i
    a, hi, side = VAR[i % len(VAR)]
    right = i % 2 == 0
    sway = 4 if i % 4 < 2 else -4
    if right:
        keyp(m, tick, P(PUNCH_R, body=[10, -22 + sway, 0], ra=a), "out")
    else:
        keyp(m, tick, P(PUNCH_L, body=[10, 18 + sway, 0], la=L(a)), "out")
    fp_jab(m, tick, right, hi, side, "out")
keyp(m, 47.5, P(CHAMBER_R, body=[2, 38, 0], rk=30, lk=26, root=[-0.1, 0]))
keyp(m, 50.5, P(PUNCH_R, body=[14, -40, 0], ll=[-40, 0, -2], lk=40, root=[-0.16, 0.4]), "out3")
keyp(m, 54, P(PUNCH_R, body=[14, -38, 0], ll=[-40, 0, -2], lk=40, root=[-0.16, 0.4]))
fpr(m, 47.5, nudge(FP_CHAMBER, (0.05, -0.05, 0.1)))
fpl(m, 47.5, FP_GUARD)
fpr(m, 50.5, nudge(FP_JAB2, (0, 0.03, -0.1), (3, 3, 0)), "out3")
fpr(m, 54, FP_JAB2)
fpl(m, 54, FP_GUARD)
use(m, "hundredFists")

# 붕권: 두 손을 오른 허리에 모아 기를 모으고 (0 ~ 9 — 조금씩 떨림) 10 에 두 손바닥을 앞으로 내지름
m = fists(Motion("palm_blast", 22, "붕권 — 두 손을 허리에 모아 기를 모았다가 두 손바닥으로 크게 내지름", fade=(0.04, 0.12)))
keyp(m, 2, GATHER, "out")
keyp(m, 5, P(GATHER, body=[3, 36, 0], root=[-0.14, 0]))
keyp(m, 7.5, P(GATHER, body=[2, 33, 0], root=[-0.13, 0]))
keyp(m, 9, P(GATHER, body=[1, 38, 0], rk=38, lk=34, root=[-0.16, 0]))
keyp(m, 10.5, P(PALMS, body=[16, -12, 0], root=[-0.14, 0.42]), "out3")
keyp(m, 15, P(PALMS, body=[14, -10, 0], root=[-0.12, 0.4]))
for t, dx in ((2, 0.0), (5, 0.015), (7.5, -0.015), (9, 0.0)):
    fpb(m, t, nudge(FP_GATHER_R, (dx, 0, 0)), nudge(FP_GATHER_L, (-dx, 0, 0)))
fpb(m, 10.5, nudge(FP_PALM, (0, 0, -0.08), (2, 4, 0)), ease="out3")
fpb(m, 15, FP_PALM)
use(m, "palmBlast")

# 비각: 낮게 숙여 달려 (돌진 1 ~ 3) → 날아차기 (3 — 오른발을 곧게) → 무릎을 당겨 솟음 → 내려찍으며 착지 (12 ~ 13 — 오른 주먹으로 땅)
m = fists(Motion("flying_kick", 22, "비각 — 숙여 달려들어 날아차기, 무릎을 당겨 솟았다가 내려찍으며 착지", fade=(0.04, 0.12)))
keyp(m, 1, P(body=[30, -6, 0], ra=A_BACK, la=L(A_BACK), rl=[-40, 0, 0], ll=[30, 0, 0], rk=60, lk=40, root=[-0.18, 0.2]), "out")
keyp(m, 3.5, P(body=[-18, 10, 0], ra=arm([0.35, -0.3, -0.85], 20), la=L(A_GUARD), rl=[-88, 0, 4], rk=4, ll=[-30, 0, -4], lk=115,
               head=[10, 0], root=[0.22, 0.35]), "out3")
keyp(m, 6, P(body=[-8, 6, 0], ra=arm([0.35, -0.35, -0.8], 25), la=L(A_GUARD), rl=[-85, 0, 4], rk=6, ll=[-34, 0, -4], lk=110,
             head=[8, 0], root=[0.24, 0.3]))
keyp(m, 8.5, P(body=[6, 0, 0], ra=A_GUARD, la=L(A_GUARD), rl=[-70, 0, 4], rk=110, ll=[-70, 0, -4], lk=110, root=[0.3, 0.1]))
keyp(m, 11, P(body=[-6, 0, 0], ra=A_RAISE_FIST, la=L(A_GUARD), rl=[-115, 0, 4], rk=6, ll=[-20, 0, -4], lk=40, head=[-6, 0], root=[0.2, 0.05]))
keyp(m, 13, P(body=[34, 0, 0], ra=A_GROUND, la=L(A_BACK), rl=[-40, 0, 6], rk=80, ll=[20, 0, -6], lk=95, root=[-0.36, 0.1]), "in")
keyp(m, 17, P(body=[22, 0, 0], ra=A_DOWN_FWD, la=L(A_GUARD), rl=[-30, 0, 6], rk=50, ll=[12, 0, -6], lk=60, root=[-0.22, 0.05]))
fpb(m, 1, FP_LOW)
fpr(m, 3.5, FP_LOW)
fpl(m, 3.5, FP_GUARD)
fp_guard(m, 8.5)
fpr(m, 11, FP_RAISE)
fpl(m, 11, FP_GUARD)
fpr(m, 13, FP_GROUND, "in")
fpl(m, 13, FP_LOW)
fpr(m, 17, FP_DOWN)
fpl(m, 17, FP_GUARD)
use(m, "flyingKick")

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

# 바위 주먹: 웅크려 오른 주먹으로 땅을 침 (0 ~ 1) → 돌 주먹이 솟는 때 (4 ~ 7) 함께 올려 침
m = fists(Motion("stone_fist", 16, "바위 주먹 — 웅크려 주먹으로 땅을 치고, 돌 주먹이 솟을 때 함께 올려 침", fade=(0.03, 0.12)))
keyp(m, 1.5, P(body=[40, -10, 0], ra=A_GROUND, la=L(A_GUARD), **LEGS_KNEEL), "out3")
keyp(m, 4.5, P(body=[36, -8, 0], ra=A_GROUND, la=L(A_GUARD), **LEGS_KNEEL))
keyp(m, 7.5, P(body=[-6, -24, 0], ra=A_UPPER, la=L(A_CHAMBER), head=[-12, 0], **LEGS_FIGHT), "out3")
keyp(m, 11, P(body=[-4, -20, 0], ra=A_RAISE_FIST, la=L(A_CHAMBER), head=[-10, 0], **LEGS_FIGHT))
fpr(m, 1.5, FP_GROUND, "out3")
fpl(m, 1.5, FP_GUARD)
fpr(m, 4.5, nudge(FP_GROUND, (0, 0.02, 0)))
fpr(m, 7.5, FP_UPPER, "out3")
fpl(m, 7.5, FP_CHAMBER)
fpr(m, 11, FP_RAISE)
use(m, "stoneFist")

# 돌의 수호: 무릎을 들어 땅을 구름 (0 ~ 3 — 기둥이 4 에 솟음) · 팔을 엇갈려 버팀 → 기둥이 바깥으로 쓰러질 때 (21 ~ 24) 두 팔을 밖으로 밀어냄
m = fists(Motion("stone_ward", 30, "돌의 수호 — 발을 굴러 돌기둥을 세우고 팔을 엇갈려 버티다가 바깥으로 밀어 쓰러뜨림", fade=(0.04, 0.1)))
keyp(m, 2, P(body=[-4, 0, 0], ra=A_CROSS, la=L(A_CROSS), **LEGS_STOMP_UP), "out")
keyp(m, 4.5, P(body=[18, 0, 0], ra=A_CROSS, la=L(A_CROSS), **LEGS_STOMP), "in")
keyp(m, 18, P(body=[12, 0, 0], ra=A_CROSS, la=L(A_CROSS), **LEGS_WIDE))
keyp(m, 23, P(body=[6, 0, 0], ra=A_SPREAD, la=L(A_SPREAD), head=[-6, 0], **LEGS_WIDE), "out3")
keyp(m, 26, P(body=[4, 0, 0], ra=A_SPREAD, la=L(A_SPREAD), **LEGS_WIDE))
fpb(m, 2, FP_CROSS)
fpb(m, 4.5, nudge(FP_CROSS, (0, -0.08, 0)), ease="in")
fpb(m, 18, FP_CROSS)
fpb(m, 23, FP_PUSH_OUT, ease="out3")
fpb(m, 26, FP_PUSH_OUT)
use(m, "stoneWard")

# 석화 결계: 손바닥을 결계 쪽으로 뻗음 (2 ~ 22) → 주먹을 쥐며 비틂 (24 — 굳힘) → 손을 펴 내질러 깨뜨림 (36)
m = fists(Motion("petrify", 40, "석화 결계 — 손바닥을 뻗어 결계를 펴고, 주먹을 쥐어 돌로 굳힌 뒤 손을 펴 깨뜨림", fade=(0.04, 0.08)))
REACH = P(body=[8, -16, 0], ra=A_PALM, la=L(A_HIP), **LEGS_FIGHT)
keyp(m, 3, REACH, "out")
keyp(m, 21, P(REACH, body=[9, -18, 0]))
keyp(m, 24.5, P(REACH, ra=bent([-0.1, 0.05, 1], 30, (0, 1, 0)), body=[12, -10, 0]), "out3")
keyp(m, 33, P(REACH, ra=bent([0.2, -0.2, 0.6], 90, (0, 1, 0)), body=[4, 10, 0]))
keyp(m, 36.5, P(REACH, ra=A_PALM, body=[14, -22, 0], **LEGS_LUNGE), "out3")
fpr(m, 3, FP_PALM, "out")
fpl(m, 3, FP_LOW)
fpr(m, 21, FP_PALM)
fpr(m, 24.5, nudge(FP_PALM, (0, -0.03, 0.1), (0, -10, 35)), "out3")
fpr(m, 33, FP_CHAMBER)
fpr(m, 36.5, nudge(FP_PALM, (0, 0, -0.1)), "out3")
fpl(m, 36.5, FP_LOW)
use(m, "petrify")

# 골렘 소환: 무릎 꿇고 두 손바닥으로 땅을 짚음 (1 ~ 6 — 골렘이 솟음) → 일어나며 두 주먹을 들어 (8 ~ 12) 골렘과 함께 내려침 (16)
m = fists(Motion("golem_summon", 24, "골렘 소환 — 두 손으로 땅을 짚어 골렘을 깨우고, 두 주먹을 들어 골렘과 함께 내려침", fade=(0.04, 0.1)))
keyp(m, 1.5, P(body=[42, 0, 0], ra=A_GROUND, la=L(A_GROUND), **LEGS_KNEEL), "out3")
keyp(m, 6, P(body=[40, 0, 0], ra=A_GROUND, la=L(A_GROUND), **LEGS_KNEEL))
keyp(m, 11, P(body=[-14, 0, 0], ra=A_OVER, la=L(A_OVER), head=[-10, 0], **LEGS_WIDE))
keyp(m, 16, P(body=[30, 0, 0], ra=A_DOWN_FWD, la=L(A_DOWN_FWD), rk=40, lk=40, root=[-0.2, 0.05]), "in")
keyp(m, 20, P(body=[24, 0, 0], ra=A_DOWN_FWD, la=L(A_DOWN_FWD), rk=30, lk=30, root=[-0.15, 0.05]))
fpb(m, 1.5, FP_GROUND, ease="out3")
fpb(m, 6, FP_GROUND)
fpb(m, 11, FP_RAISE)
fpb(m, 16, FP_DOWN, ease="in")
fpb(m, 20, FP_DOWN)
use(m, "golemSummon")

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
