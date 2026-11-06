"""0.51.0 2차 직업 스킬 동작 — 사제 2차 (2026-11-06 사용자 "다음 작업"): 각성 세인트 · 반전 워록 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SaintSkills · WarlockSkills). 둘 다 홀 (오른손 — 1차 사제 job_motions_c 꼴, 왼손은 맨손). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄
(JobKit.motionLater — 에버블룸의 꽃 피움 :bloom, 크림슨 마이어의 솟구침 :rise, 블러드 노바의 터짐 :burst, 블러드 뱅큇의 터짐 :burst). 단계 동작의 틱 0 열쇠는
앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게). 세인트는 가슴에 빛을 모아 펼치고 · 꽃잎을 흩뿌리고 · 나비를 지휘하고 · 땅에 연꽃을 피워 하늘의 꽃을 받드는 부드럽고
큰 동작, 워록은 피를 끌어당기고 · 창을 던지고 · 녹아내렸다 돌며 솟구치고 · 불타 되살아나고 · 잔치를 여는 마녀의 동작 (1차 사제 · 다른 직업과 겹치지 않게 —
1차의 내밀기 cast 꼴은 쓰지 않음)."""
import rune_motion_kit as K
from job_motions_d import first, impacts
from rune_motion_kit import (A_CHEST, A_PALM, A_PULL, A_SPREAD_DOWN, FP_CHAMBER, FP_CROSS, FP_DOWN, FP_JAB, FP_LOW, FP_PALM, FP_PALM_UP, FP_PUSH_OUT,
                             FP_SPREAD_UP, FPW_ACROSS, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, LEGS_FIGHT, LEGS_WIDE, RA_UPRIGHT, P, L, arm,
                             bent, fpl, fpw, keyp, nudge, off_bare, use_skill)

Motion = K.Motion

# 세인트 끝 자세: 홀을 앞 오른쪽에 비스듬히 세워 들고 왼손은 편히 내림
READY_SA = P(body=[4, 4, 0], head=[-6, 0], ra=arm([0.25, -0.55, 0.6], 55, [0.05, 0.85, 0.5]), la=arm([-0.3, -0.9, 0.2], 15), rl=[6, 0, 4], ll=[-6, 0, -4],
             rk=8, lk=8)
# 워록 끝 자세: 홀을 앞 오른쪽에 들고 왼손은 갈고리처럼 허리 앞에
READY_WK = P(body=[6, 8, 0], head=[-4, -4], ra=arm([0.3, -0.5, 0.55], 70, [0.1, 0.75, 0.65]), la=bent([-0.35, -0.55, 0.45], 70, (0, 1, 0.3)), **LEGS_FIGHT)

# 1인칭 홀 · 왼팔 꼴
FPW_CHEST = dict(p=(-0.38, 0.05, 0.0), blade=(-0.05, 1, 0.2))  # 가슴 앞에 곧게 세움 (화면 가운데)
FPW_HIGH_R = dict(p=(0.15, 0.55, 0.1), blade=(0.15, 1, 0.05))  # 오른쪽 위로 치켜듦 (화면 오른쪽 가장자리)
FPW_FACE = dict(p=(-0.4, 0.3, -0.05), blade=(0.0, 1, 0.1))  # 얼굴 앞에 곧게 세움 (맹세)
FPW_EDGE_UP = dict(p=(0.4, 0.45, 0.05), blade=(0.6, 0.8, 0.0))  # 오른쪽 위 바깥으로 활짝
FPW_SINK = dict(p=(0.3, -0.95, 0.3), blade=(0.3, -0.6, 0.7))  # 화면 밖 아래 (몸이 핏물이 된 동안 — 숨긴 빈손)
FPW_AIM_UP = dict(p=(-0.4, 0.4, -0.45), blade=(-0.05, 0.45, 1), edge=(0, -1, 0))  # 앞 위로 겨눔 (먼 곳의 심장)
FPL_HEART = nudge(FP_CROSS, (0.05, -0.2, 0.1))  # 왼손을 가슴에 얹음
FPL_SINK = nudge(FP_LOW, (0.0, -0.45, 0.0))  # 왼팔도 화면 밖 아래

# ------------------------------------------------------------ 세인트 (홀 — 금빛 · 연둣빛 꽃)

# 하트 어코드 (효과: 0 ~ 6 발밑에 연꽃 두 겹 · 가슴으로 빛이 모임 → 6 아군들의 가슴을 빛줄기로 이어 체력 비율을 고르게 → 12 모두 회복 · 머리 위 꽃 · 땅의 빛 고리)
ACC_GATHER = P(body=[10, 0, 0], head=[14, 0], ra=bent([0.15, -0.55, 0.75], 105, (-0.3, 1, 0.15)), la=L(A_CHEST), rl=[4, 0, 3], ll=[-4, 0, -3], rk=14, lk=14,
               root=[-0.06, 0.0])  # 고개를 숙이고 홀을 가슴 앞에 세워 든 채 왼 주먹을 가슴에 얹음 (빛을 모음)
ACC_OPEN = P(body=[-8, 0, 0], head=[-10, 0], ra=arm([0.85, 0.3, 0.35], 8, [0.45, 0.85, 0.2]), la=arm([-0.85, 0.3, 0.35], 8), **LEGS_WIDE)  # 두 팔을 옆으로 활짝
ACC_PROCLAIM = P(body=[-12, 0, 0], head=[-26, 0], ra=arm([0.18, 0.97, 0.12], 4, [0.06, 1, 0.04]), la=L(A_CHEST), rl=[0, 0, 8], ll=[0, 0, -8], rk=4, lk=4,
                 root=[0.04, 0.0])  # 발끝으로 서며 홀을 하늘로 곧게 치켜들고 왼 주먹은 가슴에
m = Motion("saint_accord", 18, "하트 어코드 — 고개를 숙여 홀을 가슴 앞에 세우고 왼 주먹을 가슴에 얹어 빛을 모았다가, 두 팔을 옆으로 활짝 펼쳐 아군들의 가슴을 잇고, "
           "발끝으로 서며 홀을 하늘로 곧게 치켜들어 넘치는 빛을 내림", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.4, ACC_GATHER, "out3")
keyp(m, 4.6, P(ACC_GATHER, body=[12, 0, 0], head=[18, 0], rk=18, lk=18, root=[-0.08, 0.0]))
keyp(m, 6.0, ACC_OPEN, "out3")
keyp(m, 9.6, P(ACC_OPEN, body=[-10, 0, 0], head=[-14, 0]))
keyp(m, 12.0, ACC_PROCLAIM, "out3")
keyp(m, 14.6, P(ACC_PROCLAIM, body=[-10, 0, 0], head=[-22, 0]))
keyp(m, 17.6, READY_SA, "inout")
fpw(m, 1.4, FPW_CHEST, "out3")
fpw(m, 4.6, FPW_CHEST, dp=(0.0, -0.03, 0.03))
fpw(m, 6.0, FPW_SPREAD, "out3")
fpw(m, 9.6, FPW_SPREAD)
fpw(m, 12.0, FPW_SKY, "out3")
fpw(m, 14.6, FPW_SKY)
fpw(m, 17.6, FPW_LOW)
fpl(m, 1.4, FPL_HEART, "out3")
fpl(m, 4.6, FPL_HEART)
fpl(m, 6.0, FP_PUSH_OUT, "out3")
fpl(m, 9.6, FP_PUSH_OUT)
fpl(m, 12.0, FPL_HEART, "out3")
fpl(m, 14.6, FPL_HEART)
fpl(m, 17.0, FP_LOW)
use_skill(m, "saint_accord")

# 페탈 베일 (효과: 0 ~ 3 홀 끝에서 꽃잎이 흘러 겨눈 아군 (없으면 나) 에게 → 4 꽃잎 보호막 · 빨라짐)
VEIL_WIND = P(body=[4, -34, 0], head=[-6, 26], ra=arm([-0.65, 0.1, 0.6], 30, [-0.7, 0.55, 0.3]), la=arm([-0.55, -0.55, -0.2], 15), rl=[10, 0, 6], ll=[-14, 0, -6],
              rk=16, lk=18)  # 몸을 왼쪽으로 감고 홀을 왼쪽 앞에 (손등 쪽으로 감음)
VEIL_SWEEP = P(body=[6, 22, 0], head=[-8, -16], ra=arm([0.55, 0.2, 0.8], 4, [0.45, 0.55, 0.7]), la=arm([-0.7, -0.35, 0.3], 10), rl=[16, 0, 4], ll=[-22, 0, -4],
               rk=12, lk=26, root=[-0.06, 0.16])  # 내디디며 홀을 오른쪽 앞으로 크게 쓸어 꽃잎을 흩뿌림
VEIL_BLESS = P(body=[2, 4, 0], head=[-10, 0], ra=arm([0.2, 0.55, 0.8], 18, [0.08, 0.95, 0.3]), la=L(A_PALM), **LEGS_FIGHT)  # 홀을 앞 위로 세워 들고 왼손바닥을 내밂
m = Motion("saint_veil", 14, "페탈 베일 — 몸을 왼쪽으로 감아 홀을 왼쪽 앞에 두었다가 내디디며 오른쪽 앞으로 크게 쓸어 꽃잎을 흩뿌리고, 홀을 앞 위로 세워 든 채 "
           "왼손바닥을 내밀어 꽃잎 보호막을 씌움", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 0.8, VEIL_WIND, "out3")
keyp(m, 3.0, VEIL_SWEEP, "out3")
m.arc(3.0, "ra", (0.1, 0.45, 1))
keyp(m, 5.0, VEIL_BLESS, "out3")
keyp(m, 9.0, P(VEIL_BLESS, body=[0, 4, 0], head=[-8, 0]))
keyp(m, 13.4, READY_SA, "inout")
fpw(m, 0.8, FPW_ACROSS, "out3")
fpw(m, 3.0, dict(p=(0.15, 0.12, -0.35), blade=(0.55, 0.45, 0.7)), "out3")
fpw(m, 5.0, FPW_UP, "out3")
fpw(m, 9.0, FPW_UP)
fpw(m, 13.4, FPW_LOW)
fpl(m, 0.8, FP_LOW)
fpl(m, 3.6, FP_LOW)
fpl(m, 5.0, FP_PALM, "out3")
fpl(m, 9.0, FP_PALM)
fpl(m, 12.6, FP_LOW)
m.set(softLegs=False)
use_skill(m, "saint_veil")

# 글리머윙 (효과: 0 홀 끝에서 나비가 날아오름 → 8 · 16 · 24 · 32 · 40 · 48 적과 아군을 번갈아 — 적은 8 · 24 · 40 에 맞음)
WING_RELEASE = P(body=[-6, -6, 0], head=[-16, 4], ra=arm([0.3, 0.75, 0.6], 8, [0.2, 0.95, 0.25]), la=arm([-0.35, 0.45, 0.82], 10), rl=[-2, 0, 5], ll=[4, 0, -5],
                 rk=4, lk=8, root=[0.04, 0.0])  # 발끝으로 서며 홀을 앞 위로 튕기고 왼손을 펴 날려 보냄
WING_GUIDE = P(body=[4, -8, 0], head=[-6, 6], ra=arm([0.1, 0.25, 0.96], 10, [0.0, 0.4, 0.92]), la=arm([-0.45, -0.3, 0.6], 40), **LEGS_FIGHT)  # 홀 끝으로 나비를 가리킴 (지휘)
m = Motion("saint_wing", 28, "글리머윙 — 발끝으로 서며 홀을 앞 위로 튕기고 왼손을 펴 나비를 날려 보낸 뒤, 지휘봉처럼 홀 끝으로 나비를 따라가며 적에게 닿을 때마다 "
           "홀을 튕기고 아군에게 닿을 때 왼손을 내밂", fade=(0.03, 0.08))
off_bare(m)
keyp(m, 1.4, WING_RELEASE, "out3")
keyp(m, 5.0, WING_GUIDE, "inout")
keyp(m, 8.0, P(WING_GUIDE, body=[8, 6, 0], head=[-6, -4], ra=arm([0.2, 0.15, 0.97], 6, [0.25, 0.1, 0.96])), "out3")
keyp(m, 12.4, P(WING_GUIDE, body=[2, 10, 0], head=[-8, -6], ra=arm([0.35, 0.35, 0.87], 12, [0.35, 0.5, 0.8])), "inout")
keyp(m, 16.0, P(WING_GUIDE, body=[4, -10, 0], head=[-8, 8], ra=arm([-0.05, 0.3, 0.95], 6, [-0.2, 0.45, 0.87]), la=arm([-0.4, 0.0, 0.9], 6)), "out3")
keyp(m, 20.0, P(WING_GUIDE, ra=arm([0.15, 0.32, 0.93], 10, [0.05, 0.5, 0.86])), "inout")
keyp(m, 24.0, P(WING_GUIDE, body=[10, 2, 0], head=[-4, 0], ra=arm([0.15, 0.05, 0.99], 4, [0.15, -0.05, 0.99])), "out3")
keyp(m, 27.6, READY_SA, "inout")
fpw(m, 1.4, FPW_UP, "out3", dp=(0.05, 0.1, -0.1))
fpw(m, 5.0, FPW_FWD, "inout")
fpw(m, 8.0, FPW_FWD, "out3", dp=(0.08, -0.04, -0.04))
fpw(m, 12.4, FPW_FWD, "inout", dp=(0.12, 0.08, 0.0))
fpw(m, 16.0, FPW_FWD, "out3", dp=(-0.08, 0.04, -0.04))
fpw(m, 20.0, FPW_FWD, "inout", dp=(0.02, 0.05, 0.0))
fpw(m, 24.0, FPW_FWD, "out3", dp=(0.04, -0.06, -0.08))
fpw(m, 27.6, FPW_LOW)
fpl(m, 1.4, FP_PALM_UP, "out3")
fpl(m, 5.0, FP_LOW)
fpl(m, 14.4, FP_LOW)
fpl(m, 16.0, FP_PALM, "out3")
fpl(m, 19.4, FP_LOW)
impacts(m, 8.0, 24.0)
use_skill(m, "saint_wing")

# 소울본드 (태세 — 효과: 0 겨눈 아군과 빛의 리본으로 이어짐 · 둘의 발밑 · 머리 위 연꽃)
BOND_REACH = P(body=[8, 16, 0], head=[-6, -12], ra=arm([0.5, 0.35, 0.2], 30, [0.25, 0.95, 0.1]), la=arm([-0.08, 0.15, 1], 0, [0, 0.3, 1]), rl=[12, 0, 4],
               ll=[-18, 0, -4], rk=10, lk=20, root=[-0.04, 0.12])  # 홀을 오른쪽에 치켜든 채 왼팔을 아군에게 곧게 뻗음
BOND_DRAW = P(body=[-4, 4, 0], head=[-8, -2], ra=arm([0.5, 0.35, 0.2], 30, [0.25, 0.95, 0.1]), la=L(A_CHEST), **LEGS_WIDE)  # 리본을 쥔 왼 주먹을 가슴으로 당김
BOND_VOW = P(body=[-6, 0, 0], head=[-4, 0], ra=RA_UPRIGHT, la=L(A_CHEST), rl=[0, 0, 12], ll=[0, 0, -12], rk=10, lk=10)  # 홀을 얼굴 앞에 곧게 세워 맹세
m = Motion("saint_bond", 22, "소울본드 — 홀을 오른쪽에 치켜든 채 왼팔을 아군에게 곧게 뻗어 빛의 리본을 움켜쥐고, 쥔 주먹을 가슴으로 당겨 두 심장을 이은 뒤 "
           "홀을 얼굴 앞에 곧게 세워 맹세함", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.8, BOND_REACH, "out3")
keyp(m, 4.4, P(BOND_REACH, la=bent([-0.08, 0.18, 1], 30, (0, 1, 0.2))))
keyp(m, 7.0, BOND_DRAW, "inout")
keyp(m, 10.0, BOND_VOW, "out3")
keyp(m, 16.0, P(BOND_VOW, head=[-8, 0], body=[-4, 0, 0]))
keyp(m, 21.4, READY_SA, "inout")
fpw(m, 1.8, FPW_HIGH_R, "out3")
fpw(m, 7.0, FPW_HIGH_R)
fpw(m, 10.0, FPW_FACE, "out3")
fpw(m, 16.0, FPW_FACE)
fpw(m, 21.4, FPW_LOW)
fpl(m, 1.8, FP_PALM, "out3")
fpl(m, 4.4, FP_JAB)
fpl(m, 7.0, FPL_HEART, "inout")
fpl(m, 16.0, FPL_HEART)
fpl(m, 20.4, FP_LOW)
use_skill(m, "saint_bond")

# 에버블룸 (효과: 0 발밑 둘레에 거대한 연꽃이 펼쳐짐 · 앞 하늘에 꽃봉오리가 떠오름 → 20 · 40 · 60 꽃잎 폭풍 → 76 :bloom → 80 꽃이 활짝)
ULT_TOUCH = P(body=[34, 0, 0], head=[-20, 0], ra=arm([0.18, -0.6, 0.78], 8, [0.1, -0.85, 0.5]), la=arm([-0.55, -0.7, 0.45], 10), rl=[-34, 0, 8], ll=[-40, 0, -8],
              rk=70, lk=74, root=[-0.3, 0.04])  # 깊이 웅크려 홀 끝을 앞 땅에 대고 왼손을 아래로 펼침 (연꽃을 피움)
ULT_OFFER = P(body=[-10, 0, 0], head=[-34, 0], ra=arm([0.25, 0.85, 0.45], 8, [0.1, 0.98, 0.2]), la=arm([-0.25, 0.7, 0.68], 6), **LEGS_WIDE)  # 일어서며 홀과 왼손바닥을 꽃봉오리로
STORM_SIDE = P(body=[-8, -18, 0], head=[-30, 14], ra=arm([-0.3, 0.88, 0.35], 10, [-0.45, 0.85, 0.2]), la=arm([-0.6, 0.4, 0.45], 10), **LEGS_WIDE)  # 홀을 머리 위 왼쪽으로 감음
STORM_SWEEP = P(body=[-8, 20, 0], head=[-30, -14], ra=arm([0.7, 0.62, 0.3], 6, [0.6, 0.75, 0.1]), la=arm([-0.35, 0.65, 0.65], 6), **LEGS_WIDE)  # 머리 위로 크게 휘돌려 오른쪽으로
m = Motion("saint_ult", 78, "에버블룸 — 깊이 웅크려 홀 끝을 앞 땅에 대어 거대한 연꽃을 피우고, 천천히 일어서며 홀과 왼손바닥을 하늘의 꽃봉오리로 받든 채 꽃잎 폭풍마다 "
           "홀을 머리 위로 크게 휘돌려 폭풍을 이끎", fade=(0.03, 0.03))
off_bare(m)
keyp(m, 2.8, ULT_TOUCH, "out3")
keyp(m, 5.4, P(ULT_TOUCH, body=[32, 0, 0], head=[-22, 0]))
keyp(m, 10.0, ULT_OFFER, "inout")
for i, s in enumerate((20, 40, 60)):
    a, b = (STORM_SIDE, STORM_SWEEP) if i % 2 == 0 else (STORM_SWEEP, STORM_SIDE)
    via = (0.15, 1, 0.25)
    keyp(m, s - 6.0, P(ULT_OFFER, body=[-11, 0, 0], head=[-35, 0]), "inout")
    keyp(m, s - 2.6, a, "inout")
    keyp(m, s, b, "out3")
    m.arc(s, "ra", via)
    keyp(m, s + 5.0, ULT_OFFER, "inout")
keyp(m, 76.0, P(ULT_OFFER, body=[-12, 0, 0], head=[-36, 0]))
keyp(m, 78.0, P(ULT_OFFER, body=[-12, 0, 0], head=[-36, 0]))
FPW_STORM_L = dict(p=(-0.6, 1.0, 0.1), blade=(-0.4, 0.9, 0.0))
FPW_STORM_R = dict(p=(0.3, 0.9, 0.05), blade=(0.55, 0.8, -0.1))
fpw(m, 2.8, FPW_DOWN, "out3")
fpw(m, 5.4, FPW_DOWN)
fpw(m, 10.0, FPW_SKY, "inout")
for i, s in enumerate((20, 40, 60)):
    a, b = (FPW_STORM_L, FPW_STORM_R) if i % 2 == 0 else (FPW_STORM_R, FPW_STORM_L)
    fpw(m, s - 6.0, FPW_SKY, "inout")
    fpw(m, s - 2.6, a, "inout")
    fpw(m, s, b, "out3")
    fpw(m, s + 5.0, FPW_SKY, "inout")
fpw(m, 78.0, FPW_SKY)
fpl(m, 2.8, FP_LOW, "out3")
fpl(m, 5.4, FP_LOW)
fpl(m, 10.0, FP_PALM_UP, "inout")
fpl(m, 78.0, FP_PALM_UP)
impacts(m, 20.0, 40.0, 60.0)
use_skill(m, "saint_ult")

# 에버블룸 :bloom (76 — 꽃이 활짝 피는 80 = 이 동작의 4): 받들던 두 손을 가슴으로 모아 웅크렸다가, 꽃이 필 때 두 팔을 위로 활짝 펼치며 몸을 젖히고, 천천히 내림
BLOOM_GATHER = P(body=[12, 0, 0], head=[8, 0], ra=bent([0.15, -0.5, 0.75], 110, (-0.3, 1, 0.15)), la=L(A_CHEST), rl=[-8, 0, 6], ll=[-8, 0, -6], rk=26, lk=26,
                 root=[-0.12, 0.0])  # 홀을 가슴 앞에 세우고 왼 주먹을 가슴에 — 웅크려 모음
BLOOM_OPEN = P(body=[-20, 0, 0], head=[-36, 0], ra=arm([0.75, 0.62, 0.15], 4, [0.5, 0.85, 0.05]), la=arm([-0.75, 0.62, 0.15], 4), rl=[0, 0, 12], ll=[0, 0, -12],
               rk=2, lk=2, root=[0.06, 0.0])  # 두 팔을 위로 활짝 · 몸을 젖히고 발끝으로
BLOOM_DOWN = P(body=[-4, 0, 0], head=[-12, 0], ra=arm([0.6, -0.2, 0.5], 20, [0.3, 0.9, 0.3]), la=arm([-0.6, -0.3, 0.5], 15), **LEGS_WIDE)  # 두 팔을 천천히 내림
m = Motion("saint_ult_bloom", 20, "에버블룸 끝 — 꽃봉오리를 받들던 두 손을 가슴으로 모아 웅크렸다가, 꽃이 활짝 필 때 두 팔을 위로 활짝 펼치며 몸을 젖히고 발끝으로 선 뒤, "
           "꽃잎이 흩날리는 동안 두 팔을 천천히 내림", fade=(0.0, 0.1))
off_bare(m)
first(m, P(ULT_OFFER, body=[-12, 0, 0], head=[-36, 0]), FPW_SKY)
keyp(m, 2.4, BLOOM_GATHER, "inout")
keyp(m, 4.0, BLOOM_OPEN, "out3")
keyp(m, 10.0, P(BLOOM_OPEN, body=[-16, 0, 0], head=[-30, 0]))
keyp(m, 14.4, BLOOM_DOWN, "inout")
keyp(m, 19.4, READY_SA, "inout")
fpw(m, 2.4, FPW_CHEST, "inout")
fpw(m, 4.0, FPW_EDGE_UP, "out3")
fpw(m, 10.0, FPW_EDGE_UP)
fpw(m, 14.4, FPW_SPREAD, "inout")
fpw(m, 19.4, FPW_LOW)
fpl(m, 0.0, FP_PALM_UP)
fpl(m, 2.4, FPL_HEART, "inout")
fpl(m, 4.0, FP_SPREAD_UP, "out3")
fpl(m, 10.0, FP_SPREAD_UP)
fpl(m, 14.4, FP_PUSH_OUT, "inout")
fpl(m, 18.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "saint_ult:bloom")


# ------------------------------------------------------------ 워록 (홀 — 핏빛 · 옅은 금빛)

# 생귄 랜스 (효과: 0 ~ 8 홀 위로 핏방울이 모여 핏빛 창이 맺힘 (2 — 과녁을 가리킴) → 8 날아감 → 12 겨눈 적을 꿰뚫음 · 빼앗은 체력을 되돌림)
LANCE_RAISE = P(body=[-12, 18, 0], head=[-14, -12], ra=arm([0.3, 0.92, -0.1], 16, [0.12, 1, -0.05]), la=bent([-0.55, -0.15, 0.55], 80, (0, 1, 0.3)), rl=[-6, 0, 8],
                ll=[-20, 0, -8], rk=18, lk=28, root=[-0.06, -0.08])  # 홀을 오른 어깨 위로 치켜들고 왼손을 갈고리처럼 (핏방울을 끌어모음)
LANCE_COIL = P(body=[-20, 30, 0], head=[-14, -22], ra=arm([0.42, 0.8, -0.42], 36, [0.2, 0.75, -0.62]), la=arm([-0.35, 0.28, 0.9], 4), rl=[-8, 0, 8], ll=[-26, 0, -8],
               rk=26, lk=36, root=[-0.1, -0.14])  # 홀을 뒤로 젖혀 감고 왼손으로 과녁을 겨눔 (창 던지기 준비)
LANCE_THROW = P(body=[28, -26, 0], head=[-18, 18], ra=arm([0.05, 0.08, 1], 4, [0.0, 0.15, 1]), la=L(A_PULL), rl=[32, 0, 4], ll=[-46, 0, -4], rk=24, lk=58,
                root=[-0.32, 0.42])  # 크게 내디디며 홀을 과녁으로 내뻗고 왼 주먹을 허리 뒤로 당김
m = Motion("warlock_lance", 18, "생귄 랜스 — 홀을 오른 어깨 위로 치켜들고 왼손을 갈고리처럼 굽혀 핏방울을 끌어모았다가, 홀을 뒤로 젖혀 감고 왼손으로 과녁을 겨눈 뒤, "
           "크게 내디디며 창을 던지듯 홀을 내뻗고, 꿰뚫으면 왼손을 펼쳐 피를 돌려줌", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, LANCE_RAISE, "out3")
keyp(m, 4.6, P(LANCE_RAISE, la=bent([-0.5, 0.0, 0.6], 100, (0, 1, 0.3))))
keyp(m, 6.6, LANCE_COIL, "inout")
keyp(m, 8.0, LANCE_THROW, "in")
m.arc(8.0, "ra", (0.25, 0.9, 0.35))
keyp(m, 10.4, P(LANCE_THROW, body=[24, -22, 0], root=[-0.3, 0.4]))
keyp(m, 12.0, P(LANCE_THROW, body=[20, -18, 0], la=L(A_SPREAD_DOWN), root=[-0.28, 0.38]), "out3")
keyp(m, 17.4, READY_WK, "inout")
fpw(m, 2.0, FPW_SKY, "out3")
fpw(m, 6.6, FPW_SKY, "inout", dp=(0.12, -0.05, 0.25))
fpw(m, 8.0, FPW_FWD, "in")
fpw(m, 12.0, FPW_FWD)
fpw(m, 17.4, FPW_LOW)
fpl(m, 2.0, FP_PALM_UP, "out3")
fpl(m, 6.6, FP_PALM, "inout")
fpl(m, 8.0, FP_CHAMBER, "in")
fpl(m, 12.0, FP_PUSH_OUT, "out3")
fpl(m, 16.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 12.0)
use_skill(m, "warlock_lance")

# 크림슨 마이어 (효과: 0 ~ 2 몸이 녹아 핏물 웅덩이가 됨 (2 부터 보이지 않음 — 받는 피해 없음) → 0 ~ 10 웅덩이째 미끄러짐 → 10 · 20 · 30 · 40 둘레를 벰 →
# 37 :rise → 40 핏물 기둥과 함께 솟구침)
MIRE_MELT = P(body=[30, 0, 0], head=[10, 0], ra=arm([0.7, -0.6, 0.3], 10, [0.5, -0.5, 0.6]), la=arm([-0.7, -0.6, 0.3], 10), rl=[-44, 0, 12], ll=[-44, 0, -12],
              rk=92, lk=92, root=[-0.4, 0.06])  # 두 팔을 아래 바깥으로 떨구며 무너지듯 주저앉음 (녹아내림)
MIRE_POOL = P(MIRE_MELT, body=[40, 0, 0], head=[16, 0], root=[-0.5, 0.06])  # 웅덩이 속 (보이지 않음)
m = Motion("warlock_mire", 40, "크림슨 마이어 — 두 팔을 아래 바깥으로 떨구며 무너지듯 주저앉아 핏물 웅덩이로 녹아내림 (보이지 않는 동안 홀도 숨김 — 1인칭 손도 화면 밖)",
           fade=(0.02, 0.04))
m.set(hands={"main": "thrown", "off": "fist"}, hide=[m.t(0.4), 1.0])  # 2틱에 보이지 않게 됨 (투명 효과가 동작보다 1 ~ 2틱 먼저 닿음) — 그 전에 홀을 숨김
keyp(m, 1.2, MIRE_MELT, "out")
keyp(m, 3.0, MIRE_POOL)
keyp(m, 40.0, MIRE_POOL)
fpw(m, 1.2, FPW_SINK, "out")
fpw(m, 40.0, FPW_SINK)
fpl(m, 1.2, FPL_SINK, "out")
fpl(m, 40.0, FPL_SINK)
m.set(softLegs=False)
impacts(m, 10.0, 20.0, 30.0)
use_skill(m, "warlock_mire")

# 크림슨 마이어 :rise (37 — 솟구침 40 = 이 동작의 3): 웅덩이 속에서 몸을 감싸 웅크렸다가 핏물 기둥과 함께 솟구치며 한 바퀴 돌아 두 팔을 펼침
RISE_WRAP = P(body=[-6, 0, 0], head=[-10, 0], ra=bent([0.25, -0.2, 0.8], 135, (-1, 0.6, 0)), la=bent([-0.25, -0.3, 0.8], 135, (1, 0.6, 0)), rl=[-4, 0, 6],
              ll=[4, 0, -6], rk=4, lk=4, root=[0.1, 0.0])  # 두 팔로 몸을 감싼 채 곧게 솟구침
RISE_UNFOLD = P(body=[-14, 0, 0], head=[-22, 0], ra=arm([0.9, 0.1, 0.3], 6, [0.6, 0.55, 0.55]), la=arm([-0.9, 0.1, 0.3], 6), rl=[0, 0, 10], ll=[0, 0, -10], rk=6,
                lk=6, root=[0.02, 0.0])  # 돌며 두 팔을 옆으로 펼침
m = Motion("warlock_mire_rise", 16, "크림슨 마이어 끝 — 웅덩이 속에서 두 팔로 몸을 감싸 웅크렸다가 핏물 기둥과 함께 곧게 솟구쳐, 한 바퀴 돌며 감쌌던 두 팔을 옆으로 "
           "활짝 펼침", fade=(0.0, 0.1))
m.set(hands={"main": "thrown", "off": "fist"}, hide=[0.0, m.t(1.6)])  # 몸이 다시 보이는 3틱 (1 ~ 2틱 먼저 닿음) 까지 홀을 숨김
first(m, MIRE_POOL, FPW_SINK)
keyp(m, 2.0, P(MIRE_POOL, body=[34, 0, 0], ra=bent([0.25, -0.3, 0.8], 130, (-1, 0.6, 0)), la=bent([-0.25, -0.35, 0.8], 130, (1, 0.6, 0)), root=[-0.44, 0.06]))
keyp(m, 3.2, RISE_WRAP, "out")
keyp(m, 4.2, P(RISE_WRAP, spin=0))
keyp(m, 8.4, P(RISE_UNFOLD, spin=-360), "out3")
keyp(m, 11.0, P(RISE_UNFOLD, body=[-10, 0, 0], head=[-16, 0], spin=-360))
keyp(m, 15.4, P(READY_WK, spin=-360), "inout")
m.keys[1.0] = dict(m.keys.get(1.0, {}), spin=-360)
fpw(m, 1.6, FPW_SINK)
fpw(m, 3.2, FPW_CHEST, "out")
fpw(m, 8.4, FPW_SPREAD, "out3")
fpw(m, 11.0, FPW_SPREAD)
fpw(m, 15.4, FPW_LOW)
fpl(m, 0.0, FPL_SINK)
fpl(m, 1.6, FPL_SINK)
fpl(m, 3.2, FPL_HEART, "out")
fpl(m, 8.4, FP_PUSH_OUT, "out3")
fpl(m, 11.0, FP_PUSH_OUT)
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "warlock_mire:rise")

# 블러드 노바 (효과: 0 머리 위에 피의 심장 · 발밑 핏빛 원 → 10 · 20 · 30 둘레 적의 피를 빨아들임 (심장이 자라며 고동침) → 36 :burst → 40 심장이 터짐)
NOVA_RAISE = P(body=[-10, 6, 0], head=[-24, -4], ra=arm([0.15, 0.97, 0.12], 6, [0.04, 1, 0.04]), la=arm([-0.8, 0.05, 0.55], 6), rl=[0, 0, 12], ll=[0, 0, -12],
               rk=16, lk=16)  # 홀을 머리 위로 곧게 (심장을 받침) · 왼팔을 옆 앞으로 뻗음
NOVA_YANK = P(NOVA_RAISE, body=[-16, 6, 0], head=[-28, -4], la=bent([-0.35, 0.15, 0.6], 125, (0.3, 1, 0.2)), rk=24, lk=24, root=[-0.08, 0.0])  # 갈고리 손을 가슴으로 홱 당김
NOVA_HOLD = P(NOVA_RAISE, body=[-14, 6, 0], head=[-28, -4], la=bent([-0.5, 0.2, 0.5], 90, (0.3, 1, 0.2)), rk=20, lk=20)
m = Motion("warlock_nova", 38, "블러드 노바 — 홀을 머리 위로 곧게 치켜들어 피의 심장을 받치고, 둘레 적의 피를 빨아들일 때마다 옆으로 뻗은 왼손을 갈고리처럼 굽혀 가슴으로 "
           "홱 당기며 심장이 고동치듯 몸이 젖혀짐", fade=(0.03, 0.04))
off_bare(m)
keyp(m, 2.4, NOVA_RAISE, "out3")
for t in (10, 20, 30):
    keyp(m, t - 2.0, P(NOVA_RAISE, la=arm([-0.88, 0.1, 0.45], 0)), "inout")
    keyp(m, t + 0.6, NOVA_YANK, "out3")
    keyp(m, t + 4.0, P(NOVA_RAISE, body=[-12, 6, 0]), "inout")
keyp(m, 36.0, NOVA_HOLD, "inout")
keyp(m, 38.0, NOVA_HOLD)
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 38.0, FPW_SKY)
fpl(m, 2.4, FP_PUSH_OUT, "out3")
for t in (10, 20, 30):
    fpl(m, t - 2.0, FP_PUSH_OUT, "inout")
    fpl(m, t + 0.6, nudge(FP_CROSS, (0.05, -0.1, 0.05)), "out3")
    fpl(m, t + 4.0, FP_PUSH_OUT, "inout")
fpl(m, 36.0, nudge(FP_PUSH_OUT, (-0.05, 0.0, 0.05)), "inout")
fpl(m, 38.0, nudge(FP_PUSH_OUT, (-0.05, 0.0, 0.05)))
impacts(m, 10.0, 20.0, 30.0)
use_skill(m, "warlock_nova")

# 블러드 노바 :burst (36 — 터짐 40 = 이 동작의 4): 왼손을 머리 위 심장으로 뻗어 움켜쥐었다가, 심장이 터질 때 깊이 주저앉으며 두 팔을 아래 바깥으로 내리꽂음
BURST_GRAB = P(NOVA_HOLD, body=[-18, 2, 0], head=[-32, 0], la=bent([-0.12, 0.9, 0.3], 40, (0.3, 0.5, 1)), rk=12, lk=12, root=[0.02, 0.0])  # 왼손으로 머리 위 심장을 움켜쥠
BURST_DOWN = P(body=[18, 0, 0], head=[-14, 0], ra=arm([0.75, -0.62, 0.25], 6, [0.55, -0.35, 0.75]), la=arm([-0.75, -0.62, 0.25], 6), rl=[-36, 0, 16], ll=[-36, 0, -16],
               rk=78, lk=78, root=[-0.34, 0.0])  # 깊이 주저앉으며 두 팔을 아래 바깥으로 내리꽂음 (고개는 앞)
m = Motion("warlock_nova_burst", 18, "블러드 노바 끝 — 왼손을 머리 위 심장으로 뻗어 움켜쥐었다가, 심장이 터질 때 깊이 주저앉으며 두 팔을 아래 바깥으로 힘껏 내리꽂고, "
           "천천히 일어섬", fade=(0.0, 0.1))
off_bare(m)
first(m, NOVA_HOLD, FPW_SKY)
keyp(m, 2.6, BURST_GRAB, "inout")
keyp(m, 4.0, BURST_DOWN, "in")
keyp(m, 8.6, P(BURST_DOWN, body=[14, 0, 0], root=[-0.3, 0.0]))
keyp(m, 17.4, READY_WK, "inout")
fpw(m, 2.6, FPW_SKY, "inout", dp=(0.05, 0.05, 0.05))
fpw(m, 4.0, dict(p=(0.35, -0.45, -0.1), blade=(0.5, -0.5, 0.7)), "in")
fpw(m, 8.6, dict(p=(0.35, -0.45, -0.1), blade=(0.5, -0.5, 0.7)))
fpw(m, 17.4, FPW_LOW)
fpl(m, 0.0, nudge(FP_PUSH_OUT, (-0.05, 0.0, 0.05)))
fpl(m, 2.6, nudge(FP_PALM_UP, (0.0, 0.2, 0.0), (30, 0, 0)), "inout")
fpl(m, 4.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 8.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)))
fpl(m, 16.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "warlock_nova:burst")

# 애시본 (태세 — 효과: 0 · 6 · 12 핏빛 불기둥이 세 번 몸을 삼킴 (갈수록 크게 — 12 에 섬광 · 재 연기))
ASH_WRAP = P(body=[18, 0, 0], head=[16, 0], ra=bent([0.25, -0.2, 0.8], 135, (-1, 0.6, 0)), la=bent([-0.25, -0.3, 0.8], 135, (1, 0.6, 0)), rl=[-10, 0, 4],
             ll=[-10, 0, -4], rk=30, lk=30, root=[-0.12, 0.0])  # 고개를 숙이고 두 팔로 몸을 감싸 웅크림 (불길에 삼켜짐)
ASH_WRITHE = P(body=[-6, 0, 0], head=[-14, 0], ra=arm([0.8, -0.5, 0.3], 8, [0.6, 0.3, 0.7]), la=arm([-0.8, -0.5, 0.3], 8), **LEGS_WIDE)  # 두 팔을 아래 바깥으로 떨치며 일어섬
ASH_REBORN = P(body=[-20, 0, 0], head=[-36, 0], ra=arm([0.55, 0.82, 0.12], 6, [0.3, 0.95, 0.0]), la=arm([-0.55, 0.82, 0.12], 6), rl=[0, 0, 10], ll=[0, 0, -10],
               rk=2, lk=2, root=[0.06, 0.0])  # 두 팔을 하늘로 V 자로 · 몸을 젖히고 발끝으로 (되살아남)
m = Motion("warlock_ash", 22, "애시본 — 고개를 숙이고 두 팔로 몸을 감싸 불길에 삼켜졌다가, 두 팔을 아래 바깥으로 떨치며 일어서고, 세 번째 불기둥에 두 팔을 하늘로 "
           "V 자로 펼치며 몸을 젖혀 되살아남", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.4, ASH_WRAP, "out3")
keyp(m, 4.8, P(ASH_WRAP, body=[22, 0, 0], head=[20, 0], rk=36, lk=36, root=[-0.15, 0.0]))
keyp(m, 6.0, ASH_WRITHE, "out3")
keyp(m, 10.4, P(ASH_WRITHE, body=[6, 0, 0], head=[4, 0], rk=22, lk=22, root=[-0.08, 0.0]), "inout")
keyp(m, 12.0, ASH_REBORN, "out3")
keyp(m, 16.4, P(ASH_REBORN, body=[-16, 0, 0], head=[-30, 0]))
keyp(m, 21.4, READY_WK, "inout")
fpw(m, 1.4, FPW_ACROSS, "out3")
fpw(m, 4.8, FPW_ACROSS)
fpw(m, 6.0, FPW_SPREAD, "out3")
fpw(m, 10.4, FPW_SPREAD, "inout", dp=(0.0, -0.1, 0.0))
fpw(m, 12.0, FPW_SKY, "out3")
fpw(m, 16.4, FPW_SKY)
fpw(m, 21.4, FPW_LOW)
fpl(m, 1.4, FP_CROSS, "out3")
fpl(m, 4.8, FP_CROSS)
fpl(m, 6.0, FP_PUSH_OUT, "out3")
fpl(m, 10.4, FP_PUSH_OUT)
fpl(m, 12.0, FP_SPREAD_UP, "out3")
fpl(m, 16.4, FP_SPREAD_UP)
fpl(m, 20.4, FP_LOW)
m.set(softLegs=False)
use_skill(m, "warlock_ash")

# 블러드 뱅큇 (효과: 0 겨눈 곳에 핏물이 번짐 → 10 · 30 · 50 심장이 고동침 (역병 · 피해) → 62 둘레 아군의 피를 빼앗아 큰 심장으로 → 66 :burst → 70 모든 심장이 터짐)
FEAST_POINT = P(body=[10, -10, 0], head=[-8, 8], ra=arm([0.05, -0.1, 1], 4, [0, -0.15, 1]), la=bent([-0.4, -0.25, 0.5], 70, (0, 1, 0.3)), **LEGS_FIGHT)  # 홀로 겨눈 곳을 가리킴
FEAST_HOST = P(body=[-10, 0, 0], head=[-20, 0], ra=arm([0.6, 0.55, 0.5], 10, [0.3, 0.9, 0.3]), la=arm([-0.6, 0.55, 0.5], 10), **LEGS_WIDE)  # 두 팔을 앞 위로 벌려 잔치를 엶
FEAST_SQUEEZE = P(body=[10, 0, 0], head=[-4, 0], ra=bent([0.4, -0.35, 0.6], 120, (-0.3, 1, 0.2)), la=bent([-0.4, -0.35, 0.6], 120, (0.3, 1, 0.2)), rl=[-8, 0, 12],
                  ll=[-8, 0, -12], rk=26, lk=26, root=[-0.12, 0.0])  # 두 주먹을 가슴 앞으로 움켜쥐며 웅크림 (심장을 쥐어짬)
FEAST_TAKE = P(body=[-18, 0, 0], head=[-24, 0], ra=arm([0.55, 0.1, -0.6], 10, [0.4, 0.8, -0.3]), la=arm([-0.55, 0.1, -0.6], 10), **LEGS_WIDE)  # 두 팔을 뒤로 젖혀 가슴을 폄 (아군의 피를 빼앗음)
FEAST_AIM = P(body=[-6, -6, 0], head=[-22, 6], ra=arm([0.1, 0.45, 0.9], 6, [0.02, 0.5, 0.86]), la=bent([-0.35, -0.3, 0.55], 120, (0.3, 1, 0.2)), **LEGS_FIGHT)  # 홀로 큰 심장을 겨눔 · 왼 주먹을 가슴 앞에
m = Motion("warlock_ult", 68, "블러드 뱅큇 — 홀로 겨눈 곳을 가리켜 핏물을 번지게 하고, 두 팔을 앞 위로 벌려 잔치를 연 뒤 심장이 고동칠 때마다 두 주먹을 가슴 앞으로 "
           "움켜쥐며 웅크리고, 두 팔을 뒤로 젖혀 아군의 피를 빼앗아 홀로 큰 심장을 겨눔", fade=(0.03, 0.03))
off_bare(m)
keyp(m, 2.4, FEAST_POINT, "out3")
keyp(m, 5.0, P(FEAST_POINT, body=[8, -10, 0]))
keyp(m, 8.0, FEAST_HOST, "inout")
for t in (10, 30, 50):
    if t > 10:
        keyp(m, t - 2.4, FEAST_HOST, "inout")
    keyp(m, t + 0.6, FEAST_SQUEEZE, "out3")
    keyp(m, t + 6.0, P(FEAST_HOST, body=[-8, 0, 0], head=[-18, 0]), "inout")
keyp(m, 59.0, FEAST_HOST, "inout")
keyp(m, 62.0, FEAST_TAKE, "out3")
keyp(m, 64.6, FEAST_AIM, "inout")
keyp(m, 66.0, P(FEAST_AIM, body=[-8, -6, 0]))
keyp(m, 68.0, P(FEAST_AIM, body=[-8, -6, 0]))
fpw(m, 2.4, FPW_FWD, "out3", dp=(0.0, -0.08, 0.0))
fpw(m, 5.0, FPW_FWD, dp=(0.0, -0.08, 0.0))
fpw(m, 8.0, FPW_EDGE_UP, "inout")
for t in (10, 30, 50):
    if t > 10:
        fpw(m, t - 2.4, FPW_EDGE_UP, "inout")
    fpw(m, t + 0.6, FPW_CHEST, "out3")
    fpw(m, t + 6.0, FPW_EDGE_UP, "inout")
fpw(m, 59.0, FPW_EDGE_UP, "inout")
fpw(m, 62.0, dict(p=(0.6, 0.2, 0.4), blade=(0.6, 0.7, -0.3)), "out3")
fpw(m, 64.6, FPW_AIM_UP, "inout")
fpw(m, 68.0, FPW_AIM_UP)
fpl(m, 2.4, FP_LOW, "out3")
fpl(m, 8.0, FP_SPREAD_UP, "inout")
for t in (10, 30, 50):
    if t > 10:
        fpl(m, t - 2.4, FP_SPREAD_UP, "inout")
    fpl(m, t + 0.6, nudge(FP_CROSS, (0.05, -0.1, 0.05)), "out3")
    fpl(m, t + 6.0, FP_SPREAD_UP, "inout")
fpl(m, 59.0, FP_SPREAD_UP, "inout")
fpl(m, 62.0, FP_LOW, "out3")
fpl(m, 64.6, FPL_HEART, "inout")
fpl(m, 68.0, FPL_HEART)
impacts(m, 10.0, 30.0, 50.0)
use_skill(m, "warlock_ult")

# 블러드 뱅큇 :burst (66 — 터짐 70 = 이 동작의 4): 잔을 들듯 홀을 가슴 앞에 모아 왼손으로 받쳤다가, 건배하듯 홀을 큰 심장으로 높이 치켜들며 왼팔을 옆으로 활짝
# 펼쳐 모든 심장을 한꺼번에 터뜨림 (잔치의 건배 — 생귄 랜스의 던지기 · 노바의 내리꽂음과 다른 꼴)
TOAST_CUP = P(body=[8, 0, 0], head=[-6, 0], ra=bent([0.2, -0.45, 0.75], 115, (-0.3, 1, 0.2)), la=bent([-0.1, -0.55, 0.75], 95, (0.4, 0.6, 0.6)), rl=[-4, 0, 8],
              ll=[-4, 0, -8], rk=20, lk=20, root=[-0.08, 0.0])  # 홀을 가슴 앞에 세워 쥐고 왼손으로 아래를 받침 (잔을 듦)
TOAST_RAISE = P(body=[-18, 0, 0], head=[-30, 0], ra=arm([0.1, 0.95, 0.3], 4, [0.05, 0.9, 0.42]), la=arm([-0.95, 0.15, 0.2], 4), rl=[4, 0, 8], ll=[-4, 0, -8],
                rk=4, lk=4, root=[0.06, 0.0])  # 홀을 큰 심장으로 높이 치켜들고 (건배) 왼팔을 옆으로 활짝 · 몸을 젖히고 발끝으로
m = Motion("warlock_ult_burst", 18, "블러드 뱅큇 끝 — 잔을 들듯 홀을 가슴 앞에 모아 왼손으로 받쳤다가, 건배하듯 홀을 큰 심장으로 높이 치켜들며 왼팔을 옆으로 활짝 펼치고 "
           "몸을 젖혀 모든 심장을 한꺼번에 터뜨림", fade=(0.0, 0.1))
off_bare(m)
first(m, P(FEAST_AIM, body=[-8, -6, 0]), FPW_AIM_UP)
keyp(m, 2.6, TOAST_CUP, "inout")
keyp(m, 4.0, TOAST_RAISE, "out3")
keyp(m, 9.0, P(TOAST_RAISE, body=[-14, 0, 0], head=[-26, 0]))
keyp(m, 17.4, READY_WK, "inout")
fpw(m, 2.6, FPW_CHEST, "inout")
fpw(m, 4.0, FPW_SKY, "out3", dp=(0.0, 0.0, -0.1))
fpw(m, 9.0, FPW_SKY, dp=(0.0, 0.0, -0.1))
fpw(m, 17.4, FPW_LOW)
fpl(m, 0.0, FPL_HEART)
fpl(m, 2.6, nudge(FP_PALM_UP, (0.1, -0.15, 0.1)), "inout")
fpl(m, 4.0, FP_PUSH_OUT, "out3")
fpl(m, 9.0, FP_PUSH_OUT)
fpl(m, 16.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "warlock_ult:burst")
