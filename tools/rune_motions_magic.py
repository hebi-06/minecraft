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

# 투지: 웅크려 주먹을 허리에 당겼다가 (1.5) 가슴을 펴며 주먹을 굳게 쥠 (4 — 주황 기운)
m = off_bare(Motion("fighting_spirit", 16, "투지 — 웅크려 주먹을 허리에 당겼다가 가슴을 펴며 주먹을 굳게 쥠", fade=(0.04, 0.12)))
keyp(m, 1.5, P(body=[18, 0, 0], head=[14, 0], ra=arm([0.35, -0.6, -0.45], 100, [0, 0.3, 1]), la=L(A_CHAMBER), rl=[-10, 0, 10], ll=[-10, 0, -10], rk=36,
               lk=36, root=[-0.14, 0]))
GRIT = P(body=[-8, 0, 0], head=[-10, 0], ra=arm([0.25, -0.3, 0.9], 95, [0, 0.9, 0.3]), la=L(A_GUARD), **LEGS_WIDE)
keyp(m, 4, GRIT, "out3")
keyp(m, 11, P(GRIT, body=[-6, 0, 0]))
fpw(m, 1.5, FPW_LOW)
fpw(m, 4, FPW_UP, "out3", dp=(0.05, -0.25, -0.1))
fpw(m, 11, FPW_UP, dp=(0.05, -0.25, -0.1))
fpl(m, 1.5, FP_CHAMBER)
fpl(m, 4, FP_GUARD, "out3")
fpl(m, 11, FP_GUARD)
use(m, "fightingSpirit")

# 강철 근육: 두 팔을 몸 앞 아래로 모아 온몸에 힘을 줌 (2.2 ~ 11 — 쇳빛 기운 · 강철 비늘, 부르르 떨림)
m = off_bare(Motion("steel_body", 16, "강철 근육 — 두 팔을 몸 앞 아래로 모아 온몸에 힘을 줌", fade=(0.04, 0.12)))
CRAB = P(body=[18, 0, 0], head=[-4, 0], ra=bent([0.45, -0.55, 0.55], 70, (-1, 0, 0.2)), la=L(bent([0.45, -0.55, 0.55], 70, (-1, 0, 0.2))),
         rl=[-8, 0, 12], ll=[-8, 0, -12], rk=26, lk=26, root=[-0.1, 0])
keyp(m, 2.2, CRAB, "out3")
for i, tick in enumerate((4, 5.5, 7, 8.5)):
    sgn = 1 if i % 2 else -1
    keyp(m, tick, P(CRAB, body=[18 + sgn, 0, sgn]))
keyp(m, 11, CRAB)
FPL_CRAB = ((-0.18, -0.15, -0.1), (5, 55, 0))
fpw(m, 2.2, FPW_LOW, "out3", dp=(-0.25, 0.1, -0.05))
fpw(m, 11, FPW_LOW, dp=(-0.25, 0.1, -0.05))
fpl(m, 2.2, FPL_CRAB, "out3")
fpl(m, 11, FPL_CRAB)
use(m, "steelBody")

# 재생: 왼손을 가슴에 대고 숨을 깊이 들이쉼 (3 — 가슴을 펴고 고개를 듦) → 내쉼 (9)
m = off_bare(Motion("regen_body", 18, "재생 — 왼손을 가슴에 대고 숨을 깊게 들이쉬었다가 내쉼", fade=(0.05, 0.14)))
BREATH = P(body=[-8, 0, 0], head=[-16, 0], ra=RA_LOW, la=L(A_CHEST), rk=2, lk=2, root=[0.03, 0])
keyp(m, 3, BREATH)
keyp(m, 9, P(BREATH, body=[6, 0, 0], head=[8, 0], root=[-0.02, 0]))
keyp(m, 13, P(BREATH, body=[0, 0, 0], head=[0, 0], root=[0, 0]))
fpw(m, 3, FPW_LOW)
fpw(m, 13, FPW_LOW)
fpl(m, 3, FPL_CHEST)
fpl(m, 13, FPL_CHEST)
use(m, "regenBody")

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

# 불의 정령 (샐러맨더): 왼손 위에 불씨를 피워 (2.5 ~ 6 — 정령이 나타남) 첫 공격 (8) 에 앞으로 뿌려 보냄
m = off_bare(Motion("spirit_fire", 18, "불의 정령 — 왼손 위에 불씨를 피워 정령을 부르고 앞으로 뿌려 보냄", fade=(0.04, 0.12)))
CUP = P(body=[-4, 6, 0], head=[4, 0], ra=RA_READY, la=L(bent([-0.05, -0.3, 0.9], 80, (0, 1, 0.2))), **LEGS_FIGHT)
keyp(m, 2.5, CUP, "out3")
keyp(m, 6, P(CUP, body=[-2, 8, 0]))
FLING = P(body=[10, -14, 0], ra=RA_READY, la=L(A_POINT), **LEGS_LUNGE)
keyp(m, 8.4, FLING, "out3")
keyp(m, 13, P(FLING, body=[8, -12, 0]))
FPL_CUP = ((-0.12, -0.02, -0.12), (35, 40, 0))
fpw(m, 2.5, FPW_LOW)
fpw(m, 13, FPW_LOW)
fpl(m, 2.5, FPL_CUP, "out3")
fpl(m, 6, nudge(FPL_CUP, (0, 0.03, 0)))
fpl(m, 8.4, FP_JAB2, "out3")
fpl(m, 13, FP_JAB)
use(m, "summonSpirits:common", "summonSpirits")

# 물의 정령 (운디네): 두 손바닥을 몸 앞에 모아 물을 떠받듦 (2.5 ~ 6) → 물결치듯 앞으로 밀어 보냄 (8.6)
m = off_bare(Motion("spirit_water", 18, "물의 정령 — 두 손으로 물을 떠받들어 정령을 부르고 물결치듯 앞으로 밀어 보냄", fade=(0.04, 0.12)))
BOWL = P(body=[6, 0, 0], head=[8, 0], ra=bent([0.1, -0.4, 0.9], 70, (-0.6, 1, 0)), la=L(bent([0.1, -0.4, 0.9], 70, (-0.6, 1, 0))), **LEGS_WIDE)
keyp(m, 2.5, BOWL, "out3")
keyp(m, 6, P(BOWL, body=[4, 0, 0], root=[0.02, 0]))
WAVE = P(body=[12, 0, 0], ra=arm([0.15, 0.1, 1], 6, [0, 0.6, 0.8]), la=L(arm([0.15, 0.1, 1], 4, [0, 1, 0.2])), **LEGS_LUNGE)
keyp(m, 8.6, WAVE, "inout")
keyp(m, 13, P(WAVE, body=[10, 0, 0], ra=arm([0.15, -0.05, 1], 8, [0, 0.5, 0.85]), la=L(arm([0.15, -0.05, 1], 6, [0, 1, 0.2]))))
fpw(m, 2.5, FPW_LOW, "out3", dp=(-0.2, 0.15, -0.1))
fpw(m, 8.6, FPW_FWD, dp=(0.2, -0.15, 0))
fpw(m, 13, FPW_FWD, dp=(0.2, -0.2, 0))
fpl(m, 2.5, ((-0.15, -0.05, -0.15), (30, 50, 0)), "out3")
fpl(m, 6, ((-0.15, 0.0, -0.15), (32, 50, 0)))
fpl(m, 8.6, nudge(FP_PALM, (0.1, 0, 0), (0, -15, 0)))
fpl(m, 13, nudge(FP_PALM, (0.1, -0.05, 0), (-5, -15, 0)))
use(m, "summonSpirits:rare")

# 바람의 정령 (실프): 왼팔을 오른쪽으로 감았다가 (2.5 ~ 6) 첫 공격 (8) 에 바깥으로 크게 휘둘러 바람을 일으킴
m = off_bare(Motion("spirit_wind", 18, "바람의 정령 — 왼팔을 몸 앞으로 감았다가 바깥으로 크게 휘둘러 바람의 정령을 날려 보냄", fade=(0.04, 0.12)))
COIL = P(body=[2, 26, 0], ra=RA_READY, la=L(bent([-0.6, 0.0, 0.6], 70, (1, 0.3, 0.3))), **LEGS_FIGHT)
keyp(m, 2.5, COIL, "out3")
keyp(m, 6, P(COIL, body=[2, 30, 0]))
GUST = P(body=[6, -24, 0], ra=RA_READY, la=L(arm([0.8, 0.2, 0.55], 4)), **LEGS_LUNGE)
keyp(m, 8.5, GUST, "out3")
keyp(m, 13, P(GUST, body=[4, -20, 0]))
fpw(m, 2.5, FPW_LOW)
fpw(m, 13, FPW_LOW)
fpl(m, 2.5, ((-0.25, 0.05, 0.0), (15, 80, 0)), "out3")
fpl(m, 6, ((-0.28, 0.05, 0.0), (15, 85, 0)))
fpl(m, 8.5, ((0.32, 0.12, -0.2), (25, -40, 0)), "out3")
fpl(m, 13, ((0.3, 0.08, -0.18), (20, -40, 0)))
use(m, "summonSpirits:noble")

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
