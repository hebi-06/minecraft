"""0.46.0 룬 동작 — 던지고 · 부리고 · 끌어당기는 룬 (암살자 · 사냥꾼 · 화산 · 투사) 과 한정 초월 (세계수 · 심연 · 빙하 — 2026-11-11 만상귀일 · 칠성 · 천공의 눈을 뺌).
던지는 것은 손에 소품을 쥐었다가 (rpg:prop_* — tools/prop_art.py) 놓는 순간 사라짐 (효과가 그 자리에서 날아가는 것을 그림):
  prop — 주 손 무기 대신 소품, 던진 뒤는 빈손 (1인칭 fp 열쇠: 소품 → 빈 팔) · swap — 소품 구간 밖은 무기 그대로 (연막탄을 던지고 다시 단검)
  왼손 prop — 왼손에 소품 (holdOff), 던진 뒤 왼손은 빈손 (fpo)."""
import rune_motion_kit as K
from rune_motion_kit import (A_CHAMBER, A_CHEST, A_DOWN_FWD, A_GROUND, A_GUARD, A_HIP, A_OVER, A_PALM, A_PALM_LOW, A_POINT, A_PULL, A_RAISE,
                             A_RAISE_FIST, A_RAISE_FWD, A_THROW_FOLLOW, A_THROW_REL, A_UNDER_WIND, CALL, FP_CHAMBER, FP_CROSS, FP_DOWN, FP_GATHER_L,
                             FP_GROUND, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, FP_SPREAD_UP, FPW_DOWN, FPW_FWD, FPW_LOW,
                             FPW_SKY, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_SQUAT, LEGS_STOMP, LEGS_STOMP_UP, LEGS_WIDE, P, RA_POINT,
                             RA_POINT_LOW, RA_READY, RA_SKY, RA_SPREAD_UP, SPIN_FWD_R, W_HIGH2, W_READY, arm, bent, fists, fpl, fpr, fpw, graft, keyp,
                             nudge, off_bare, spin_right, strike, use)

Motion = K.Motion


def prop_throw_fp(m, wind, rel, after=None):
    """1인칭 주 손 소품: wind 틱에 머리 뒤로 젖혀 들고 rel 틱에 앞으로 내던짐 (손 자리에서 돌림) → 그 뒤 빈 팔 (after = [(틱, 꼴, 빠르기) …])"""
    m.fp(wind, (0.05, 0.35, 0.25), (20, 0, 0))
    m.fp(rel, (-0.1, 0.2, -0.45), (-15, 0, 0), ease="out3")
    for tick, pre, ease in after or []:
        fpr(m, tick, pre, ease)


# ------------------------------------------------------------ 암살자

# ------------------------------------------------------------ 사냥꾼

# 늑대 무리: 왼팔을 몸 앞으로 감았다가 (0.3) 앞으로 휘둘러 무리를 보냄 (1.5 — 늑대 다섯이 달려들어 물어뜯음 0 ~ 40) → 무리가 울부짖을 때 (52) 주먹을 하늘로
m = off_bare(Motion("wolf_pack", 58, "늑대 무리 — 왼팔을 앞으로 휘둘러 늑대 무리를 보내고, 무리가 울부짖을 때 주먹을 하늘로 치켜듦", fade=(0.03, 0.08)))
keyp(m, 0.3, P(body=[0, 20, 0], ra=RA_READY, la=L(bent([-0.5, 0.0, 0.6], 70, (1, 0.3, 0.3))), **LEGS_FIGHT))
SIC = P(body=[10, -20, 0], ra=RA_READY, la=L(arm([0.45, 0.05, 0.89], 4)), **LEGS_LUNGE)
keyp(m, 1.5, SIC, "out3")
keyp(m, 6, P(SIC, body=[8, -16, 0]))
keyp(m, 12, W_READY)
keyp(m, 46, P(W_READY, body=[10, 4, 0]))
HOWL = P(body=[-12, 0, 0], head=[-28, 0], ra=RA_READY, la=L(A_RAISE_FIST), **LEGS_WIDE)
keyp(m, 51.5, HOWL, "out3")
keyp(m, 55, HOWL)
fpw(m, 0.3, FPW_LOW)
fpw(m, 55, FPW_LOW)
fpl(m, 0.3, ((-0.22, 0.0, 0.0), (15, 75, 0)))
fpl(m, 1.5, ((0.28, 0.08, -0.22), (18, -35, 0)), "out3")
fpl(m, 6, ((0.26, 0.04, -0.2), (14, -35, 0)))
fpl(m, 12, FP_GUARD)
fpl(m, 46, FP_GUARD)
fpl(m, 51.5, FP_RAISE, "out3")
fpl(m, 55, FP_RAISE)
use(m, "wolfPack")

# 야수왕 강림: 두 팔을 하늘로 들어 늑대왕을 부름 (3 ~ 17 — 하늘에서 내려옴) → 거대한 발톱이 할퀼 때마다 (20 · 30 · 40 · 50) 팔을 크게 할퀴듯 휘두름 (좌우 번갈아)
m = off_bare(Motion("beast_king", 60, "야수왕 강림 — 두 팔을 하늘로 들어 늑대왕을 부르고, 발톱이 할퀼 때마다 좌우로 크게 할퀴듯 휘두름", fade=(0.03, 0.08)))
keyp(m, 3, CALL, "out3")
keyp(m, 16, P(CALL, body=[-15, 0, 0], head=[-30, 0]))
CLAW_R_UP = P(body=[-4, 24, 0], ra=arm([0.55, 0.7, 0.3], 30, [0.3, 0.9, 0.3]), la=L(A_GUARD), **LEGS_FIGHT)
CLAW_R_DN = P(body=[16, -20, 0], ra=arm([-0.35, -0.55, 0.75], 10, [-0.4, -0.7, 0.6]), la=L(A_GUARD), **LEGS_LUNGE)
CLAW_L_UP = P(body=[-4, -24, 0], ra=RA_READY, la=L(arm([0.55, 0.7, 0.3], 30)), **LEGS_FIGHT)
CLAW_L_DN = P(body=[16, 20, 0], ra=RA_READY, la=L(arm([-0.35, -0.55, 0.75], 10)), **LEGS_LUNGE)
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 16, FPW_SKY)
fpl(m, 3, FP_RAISE, "out3")
fpl(m, 16, FP_RAISE)
for i, T in enumerate((20, 30, 40, 50)):
    right = i % 2 == 0
    keyp(m, T - 1.6, CLAW_R_UP if right else CLAW_L_UP)
    keyp(m, T + 0.4, CLAW_R_DN if right else CLAW_L_DN, "in")
    if right:
        fpw(m, T - 1.6, FPW_UP, dp=(0.3, 0.1, 0.1))
        fpw(m, T + 0.4, FPW_DOWN, "in", dp=(-0.4, 0, 0))
        fpl(m, T - 1.6, FP_GUARD)
        fpl(m, T + 0.4, FP_GUARD)
    else:
        fpw(m, T - 1.6, FPW_LOW)
        fpw(m, T + 0.4, FPW_LOW)
        fpl(m, T - 1.6, nudge(FP_RAISE, (0.1, 0.05, 0), (0, -10, 0)))
        fpl(m, T + 0.4, nudge(FP_DOWN, (-0.25, 0, 0), (0, 25, 0)), "in")
keyp(m, 55, W_READY)
fpw(m, 55, FPW_LOW)
fpl(m, 55, FP_GUARD)
use(m, "beastKing")

# ------------------------------------------------------------ 화산

# ------------------------------------------------------------ 투사

# ------------------------------------------------------------ 한정 초월 (마법)

# 세계수의 가호: 무릎을 꿇고 왼손을 땅에 대 씨앗을 심음 (2 ~ 6 — 앞 왼쪽에 세계수가 자라기 시작) → 일어서며 두 팔을 천천히 들어 올려 나무를 키움 (8 ~ 16) →
# 잎 지붕이 필 때 (18) 두 팔을 활짝 펴 가호를 내림
m = off_bare(Motion("world_tree", 32, "세계수의 가호 — 무릎을 꿇고 씨앗을 심은 뒤 일어서며 두 팔로 나무를 키우고, 잎 지붕이 필 때 두 팔을 활짝 폄", fade=(0.04, 0.1)))
SEED = P(body=[24, -10, 0], head=[18, 0], ra=arm([0.3, -0.75, 0.55], 30, [0, 0.4, 0.9]), la=L(arm([0.0, -0.92, 0.38], 4)), **LEGS_KNEEL)
keyp(m, 2, SEED)
keyp(m, 6, P(SEED, body=[26, -10, 0]))
GROW = P(body=[-4, -10, 0], head=[-14, 0], ra=arm([0.3, 0.35, 0.89], 25, [0.1, 0.9, 0.4]), la=L(arm([0.2, 0.35, 0.92], 6, [0, 1, 0.2])), **LEGS_WIDE)
keyp(m, 12, GROW)
keyp(m, 16, P(GROW, body=[-8, -10, 0], head=[-20, 0]))
BLOOM = P(body=[-12, 0, 0], head=[-24, 0], ra=RA_SPREAD_UP, la=L(arm([0.6, 0.75, 0.2], 10)), **LEGS_WIDE)
keyp(m, 18.6, BLOOM, "out3")
keyp(m, 27, P(BLOOM, body=[-10, 0, 0]))
fpw(m, 2, FPW_LOW)
fpw(m, 6, FPW_LOW)
fpw(m, 12, FPW_UP, dp=(0.25, -0.25, 0))
fpw(m, 16, FPW_UP, dp=(0.25, -0.15, 0))
fpw(m, 18.6, FPW_SKY, "out3", dp=(0.35, -0.15, 0))
fpw(m, 27, FPW_SKY, dp=(0.35, -0.15, 0))
fpl(m, 2, FP_GROUND)
fpl(m, 6, FP_GROUND)
fpl(m, 12, FP_PALM_UP)
fpl(m, 16, nudge(FP_PALM_UP, (0, 0.05, 0), (10, 0, 0)))
fpl(m, 18.6, FP_SPREAD_UP, "out3")
fpl(m, 27, FP_SPREAD_UP)
use(m, "worldTreeGrace")

