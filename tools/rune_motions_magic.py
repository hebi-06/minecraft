"""0.46.0 룬 동작 — 마법 · 기운 룬 (체력 · 마력 · 신성 · 번개 · 육체 · 중력 · 해류 · 정령사).
오른손 (무기 · 지팡이) 은 든 채로 겨누고 · 치켜들고 · 내던지는 몸짓, 왼손은 맨손 (off_bare — 1인칭 왼팔 fpo) 으로 손바닥을 펴고 · 움켜쥐고 · 휘저음.
시각은 효과의 실제 틱 (rules RUNES 의 delay · gap · hits · charge, RpgRuneTimingTests hits)."""
import rune_motion_kit as K
from rune_motion_kit import (A_CHAMBER, A_CHEST, A_CROSS, A_DOWN_FWD, A_FLEX, A_GROUND, A_GUARD, A_HIP, A_PALM, A_PALM_LOW, A_POINT, A_PULL,
                             A_RAISE, A_RAISE_FIST, A_RAISE_FWD, A_SHIELD, A_THROW_FOLLOW, A_THROW_REL, CALL, FP_CHAMBER, FP_CROSS, FP_DOWN, FP_GATHER_L,
                             FP_GROUND, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, FP_SPREAD_UP, FPW_ACROSS, FPW_DOWN, FPW_DRAWN,
                             FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_SQUAT, LEGS_STOMP, LEGS_STOMP_UP,
                             LEGS_WIDE, P, RA_ACROSS, RA_BACK_HIGH, RA_LOW, RA_PLANT, RA_POINT, RA_POINT_LOW, RA_READY, RA_SKY, RA_SPREAD_DOWN, RA_SPREAD_UP,
                             RA_UPF, RA_UPRIGHT, W_READY, arm, bent, fists, fpl, fpr, fpw, graft, keyp, nudge, off_bare, strike, use)

Motion = K.Motion

LA_SPREAD_DOWN = L(bent([0.8, -0.5, 0.3], 20, (0, 0.3, 1)))  # 왼팔을 아래 바깥으로 (주먹)
FPL_SPREAD_DOWN = ((0.3, -0.15, -0.1), (0, -35, 0))  # 1인칭 왼팔: 아래 바깥으로 뻗음
FPL_CHEST = ((-0.18, -0.12, 0.12), (25, 70, 0))  # 1인칭 왼팔: 주먹 · 손바닥을 가슴에
FPL_FACE = ((-0.1, 0.05, -0.05), (28, 62, 0))  # 1인칭 왼팔: 팔뚝으로 얼굴을 가림

# ------------------------------------------------------------ 체력 (수호)

# ------------------------------------------------------------ 마력

# ------------------------------------------------------------ 신성

# 천벌: 무기와 왼손을 하늘로 들어 빛의 문장을 엶 (2.5 ~ 10) → 빛기둥이 내리꽂히는 때 (12) 조준한 곳으로 무기를 내려 가리킴
m = off_bare(Motion("divine_smite", 22, "천벌 — 무기를 하늘로 들어 빛의 문장을 열고, 빛기둥이 내리꽂힐 때 무기를 내려 가리킴", fade=(0.04, 0.1)))
keyp(m, 2.5, CALL, "out3")
keyp(m, 10, P(CALL, body=[-15, 0, 0], head=[-30, 0]))
SMITE = P(body=[18, -8, 0], head=[4, 0], ra=RA_POINT_LOW, la=L(A_DOWN_FWD), **LEGS_LUNGE)
keyp(m, 12.4, SMITE, "in")
keyp(m, 17, P(SMITE, body=[16, -8, 0]))
fpw(m, 2.5, FPW_SKY, "out3")
fpw(m, 10, FPW_SKY)
fpw(m, 12.4, FPW_DOWN, "in")
fpw(m, 17, FPW_DOWN)
fpl(m, 2.5, FP_RAISE, "out3")
fpl(m, 10, FP_RAISE)
fpl(m, 12.4, FP_DOWN, "in")
fpl(m, 17, FP_DOWN)
use(m, "divineSmite")

# ------------------------------------------------------------ 번개

# ------------------------------------------------------------ 육체 (몸에 거는 기운)

# ------------------------------------------------------------ 중력

# ------------------------------------------------------------ 해류

# 심해의 군주: 두 팔을 하늘로 들어 (3 ~ 15 — 소용돌이에서 바다뱀이 솟음) → 휘감기가 시작될 때 (20) 무기와 왼손을 앞으로 내려 이끎
m = off_bare(Motion("leviathan", 40, "심해의 군주 — 두 팔을 하늘로 들어 바다뱀을 불러 올리고, 무기와 왼손을 앞으로 내려 휘감기를 이끎", fade=(0.04, 0.1)))
keyp(m, 3, CALL, "out3")
keyp(m, 15, P(CALL, body=[-15, 0, 0], head=[-30, 0]))
LEAD = P(body=[10, -6, 0], ra=RA_POINT, la=L(A_PALM), **LEGS_LUNGE)
keyp(m, 20.4, LEAD, "in")
keyp(m, 34, P(LEAD, body=[8, -6, 0]))
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 15, FPW_SKY)
fpw(m, 20.4, FPW_FWD, "in")
fpw(m, 34, FPW_FWD)
fpl(m, 3, FP_RAISE, "out3")
fpl(m, 15, FP_RAISE)
fpl(m, 20.4, FP_PALM, "in")
fpl(m, 34, FP_PALM)
use(m, "leviathan")

# ------------------------------------------------------------ 정령사 (등급마다 부르는 정령이 다름 — rune:summonSpirits:등급)

# 땅의 정령 (노움): 왼손바닥을 앞 땅으로 펴고 (2.5) 오른발을 들어 (8.5) 땅을 구름 (10.4 — 첫 바위 공격의 예고)
m = off_bare(Motion("spirit_earth", 20, "땅의 정령 — 왼손을 땅으로 펴고 발을 굴러 땅의 정령을 깨움", fade=(0.04, 0.12)))
PALMD = P(body=[14, 0, 0], head=[10, 0], ra=RA_READY, la=L(A_PALM_LOW), **LEGS_WIDE)
keyp(m, 2.5, PALMD, "out3")
keyp(m, 7, P(PALMD, body=[12, 0, 0]))
keyp(m, 8.6, P(PALMD, body=[2, 0, 0], **LEGS_STOMP_UP))
keyp(m, 10.4, P(PALMD, body=[20, 0, 0], la=L(A_DOWN_FWD), **LEGS_STOMP), "in")
keyp(m, 15, P(PALMD, body=[18, 0, 0], la=L(A_DOWN_FWD), **LEGS_STOMP))
fpw(m, 2.5, FPW_LOW)
fpw(m, 15, FPW_LOW)
fpl(m, 2.5, FP_DOWN, "out3")
fpl(m, 8.6, nudge(FP_DOWN, (0, 0.08, 0)))
fpl(m, 10.4, FP_GROUND, "in")
fpl(m, 15, FP_GROUND)
use(m, "summonSpirits:hero")

# 정령왕 강림: 두 팔을 활짝 들어 네 정령을 부름 (3 ~ 26 — 정령마다 한 번씩 공격) → 합체 (34) 에 두 손을 머리 위로 모음 → 정령왕이 내려와 팔을 들 때 (50) 두 팔을 활짝
m = off_bare(Motion("spirit_king", 62, "정령왕 강림 — 두 팔을 펴 네 정령을 부르고, 머리 위로 손을 모아 합체시킨 뒤 정령왕과 함께 두 팔을 활짝 폄", fade=(0.03, 0.08)))
OPEN = P(body=[-10, 0, 0], head=[-20, 0], ra=RA_SPREAD_UP, la=L(arm([0.6, 0.75, 0.2], 10)), **LEGS_WIDE)
keyp(m, 3, OPEN, "out3")
keyp(m, 14, P(OPEN, body=[-8, 0, 0]))
keyp(m, 26, P(OPEN, body=[-11, 0, 0]))
JOIN = P(body=[-14, 0, 0], head=[-30, 0], ra=bent([0.2, 0.95, 0.1], 40, (-1, 0.2, 0)), la=L(bent([0.2, 0.95, 0.1], 40, (-1, 0.2, 0))), **LEGS_WIDE)
keyp(m, 33.6, JOIN)
keyp(m, 45, P(JOIN, body=[-12, 0, 0]))
KING = P(body=[-16, 0, 0], head=[-30, 0], ra=arm([0.85, 0.5, 0.1], 6, [0.5, 0.85, 0.1]), la=L(arm([0.85, 0.5, 0.1], 6)), root=[0.04, 0], **LEGS_WIDE)
keyp(m, 50.4, KING, "out3")
keyp(m, 58, P(KING, body=[-12, 0, 0]))
fpw(m, 3, FPW_SKY, "out3", dp=(0.35, -0.15, 0))
fpw(m, 26, FPW_SKY, dp=(0.35, -0.15, 0))
fpw(m, 33.6, FPW_SKY, dp=(-0.05, 0.05, 0))
fpw(m, 45, FPW_SKY, dp=(-0.05, 0.05, 0))
fpw(m, 50.4, FPW_SKY, "out3", dp=(0.55, -0.35, 0))
fpw(m, 58, FPW_SKY, dp=(0.55, -0.35, 0))
fpl(m, 3, FP_SPREAD_UP, "out3")
fpl(m, 26, FP_SPREAD_UP)
fpl(m, 33.6, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 45, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 50.4, FP_SPREAD_UP, "out3")
fpl(m, 58, FP_SPREAD_UP)
use(m, "spiritKing")
