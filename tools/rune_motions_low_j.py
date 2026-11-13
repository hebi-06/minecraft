"""2026-11-13 일반 · 희귀 · 고귀 룬 동작 다시 — 묶음 J (사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 정령사 · 도끼 (회전 도끼 · 도약 찍기) · 신체 강화 · 평타 강화.
새 3D 연출 (정령 몹 · 룬 도끼 · 빛 고리 · 육각 판 · 숫돌 고리가 훑는 룬 검 · X 로 휩쓰는 검 · 가로로 휩쓰는 도끼) 에 맞춘 큰 동작. 피해 · 효과 틱은 그대로.
"""
import rune_motion_kit as K
from rune_motion_kit import (A_CHAMBER, A_CHEST, A_GUARD, A_HIP, A_POINT, FP_CHAMBER, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PALM, FP_PALM_UP, FPW_DOWN,
                             FPW_FWD, FPW_LOW, FPW_SKY, FPW_UP, L, LEGS_FIGHT, LEGS_WIDE, P, W_READY, arm, bent, fpl, fpr, fpw, keyp, nudge, use)
from rune_motions_hero import BRACE, LUNGE_DEEP, breathe, strike_with

Motion = K.Motion
off_bare = K.off_bare
LA = L  # 왼팔 거울

# ------------------------------------------------------------ 정령사 (등급마다 부르는 정령이 다름 — rune:summonSpirits:등급)

# 불의 정령 (효과: 0 3D 샐러맨더가 나타남 · 8 첫 불덩이): 0 에서 웅크려 왼손을 아래에 오므려 불씨를 모음 → 2.6 에 일어서며 오므린 손을 가슴 앞으로 들어 올림
# (정령이 깨어남) → 6.8 에 왼손을 오른 어깨 쪽으로 감고 몸을 비틂 → 8.4 에 몸째 풀며 앞으로 힘껏 뿌려 보냄 (크게 내디딤) → 숨 → 거둠
m = off_bare(Motion("spirit_fire", 20, "불의 정령 — 웅크려 손에 불씨를 모아 들어 올려 정령을 깨우고, 몸을 비틀었다가 몸째 풀며 앞으로 힘껏 뿌려 보냄", fade=(0.01, 0.1)))
EMBER = P(body=[24, 4, 0], head=[16, 0], ra=K.RA_READY, la=LA(bent([0.0, -0.75, 0.6], 70, (0, 1, 0.2))), rl=[-18, 0, 8], ll=[-22, 0, -8], rk=50, lk=54,
          root=[-0.3, 0])
CUP = P(body=[-6, 6, 0], head=[2, 0], ra=K.RA_READY, la=LA(bent([-0.05, -0.2, 0.95], 85, (0, 1, 0.2))), **LEGS_FIGHT)
SPIN_UP = P(body=[4, 44, 0], head=[0, -24], ra=K.RA_READY, la=LA(bent([-0.7, 0.3, 0.4], 90, (0.3, 0.4, 1))), rl=[20, 0, 6], ll=[-12, 0, -6], rk=36, lk=22,
            root=[-0.16, -0.08])
FLING = P(body=[16, -30, 0], head=[2, 10], ra=K.RA_READY, la=LA(A_POINT), root=[-0.3, 0.5], **LUNGE_DEEP)
keyp(m, 0.0, EMBER)
keyp(m, 2.6, CUP, "inout")
keyp(m, 5.0, P(CUP, body=[-8, 8, 0], head=[0, 0]))
keyp(m, 6.8, SPIN_UP, "out")
keyp(m, 8.4, FLING, "in")
keyp(m, 9.6, P(FLING, body=[18, -36, 0]), "out")
breathe(m, 10.8, 14.4, FLING, step=1.8, lean=1.0, knee=2)
keyp(m, 18.5, P(W_READY, body=[6, 0, 0]))
FPL_CUP = ((-0.12, -0.02, -0.12), (35, 40, 0))
fpw(m, 0.0, FPW_LOW)
fpw(m, 18.5, FPW_LOW)
fpl(m, 0.0, nudge(FPL_CUP, (0, -0.25, 0.05)))
fpl(m, 2.6, FPL_CUP, "inout")
fpl(m, 6.8, ((-0.3, 0.08, 0.0), (15, 80, 0)), "out")
fpl(m, 8.4, FP_JAB2, "in")
fpl(m, 14.4, FP_JAB)
fpl(m, 18.5, FP_LOW)
use(m, "summonSpirits:common", "summonSpirits")

# 물의 정령 (효과: 0 3D 운디네가 물에서 솟음 · 8 첫 물결): 0 에서 웅크려 두 손바닥을 아래로 모아 물을 뜸 → 3 에 일어서며 두 손으로 물을 떠받들어 가슴 앞으로 →
# 7 에 두 손을 가슴으로 끌어당겼다가 → 8.6 에 물결치듯 두 손바닥을 앞으로 밀어 보냄 (내디딤) → 숨 → 거둠
m = off_bare(Motion("spirit_water", 20, "물의 정령 — 웅크려 두 손으로 물을 떠 들어 올려 정령을 부르고, 가슴으로 당겼다가 물결치듯 앞으로 밀어 보냄", fade=(0.01, 0.1)))
SCOOP = P(body=[26, 0, 0], head=[16, 0], ra=bent([0.1, -0.75, 0.6], 50, (-0.6, 1, 0)), la=LA(bent([0.1, -0.75, 0.6], 50, (-0.6, 1, 0))), rl=[-18, 0, 10],
          ll=[-18, 0, -10], rk=56, lk=56, root=[-0.32, 0])
BOWL = P(body=[4, 0, 0], head=[6, 0], ra=bent([0.1, -0.35, 0.9], 70, (-0.6, 1, 0)), la=LA(bent([0.1, -0.35, 0.9], 70, (-0.6, 1, 0))), **LEGS_WIDE)
DRAW_IN = P(body=[-6, 0, 0], head=[-4, 0], ra=bent([0.2, -0.2, 0.4], 120, (-0.6, 1, 0)), la=LA(bent([0.2, -0.2, 0.4], 120, (-0.6, 1, 0))), rl=[12, 0, 10],
            ll=[-8, 0, -10], rk=20, lk=16, root=[-0.06, -0.1])
WAVE = P(body=[16, 0, 0], head=[4, 0], ra=arm([0.15, 0.1, 1], 6, [0, 0.6, 0.8]), la=LA(arm([0.15, 0.1, 1], 4, [0, 1, 0.2])), root=[-0.3, 0.55], **LUNGE_DEEP)
keyp(m, 0.0, SCOOP)
keyp(m, 3.0, BOWL, "inout")
keyp(m, 5.4, P(BOWL, body=[2, 0, 0], root=[0.02, 0]))
keyp(m, 7.0, DRAW_IN, "out")
keyp(m, 8.6, WAVE, "in")
keyp(m, 10.0, P(WAVE, body=[20, 0, 0], ra=arm([0.15, -0.05, 1], 8, [0, 0.5, 0.85]), la=LA(arm([0.15, -0.05, 1], 6, [0, 1, 0.2]))), "out")
breathe(m, 11.2, 14.4, P(WAVE, body=[18, 0, 0]), step=1.6, lean=1.0, knee=2)
keyp(m, 18.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW, dp=(-0.2, -0.1, -0.1))
fpw(m, 3.0, FPW_LOW, "inout", dp=(-0.2, 0.15, -0.1))
fpw(m, 8.6, FPW_FWD, "in", dp=(0.2, -0.15, 0))
fpw(m, 14.4, FPW_FWD, dp=(0.2, -0.2, 0))
fpw(m, 18.5, FPW_LOW)
fpl(m, 0.0, ((-0.15, -0.3, -0.12), (20, 50, 0)))
fpl(m, 3.0, ((-0.15, -0.05, -0.15), (30, 50, 0)), "inout")
fpl(m, 7.0, ((-0.15, 0.0, 0.0), (32, 50, 0)), "out")
fpl(m, 8.6, nudge(FP_PALM, (0.1, 0, 0), (0, -15, 0)), "in")
fpl(m, 14.4, nudge(FP_PALM, (0.1, -0.05, 0), (-5, -15, 0)))
fpl(m, 18.5, FP_LOW)
use(m, "summonSpirits:rare")

# 바람의 정령 (효과: 0 3D 실프가 바람에서 나타남 · 8 첫 돌풍): 0 에서 왼팔을 몸 앞으로 감아 오른 어깨 위까지 끌어올리고 몸을 오른쪽으로 끝까지 비틂 (발끝) →
# 감은 채 바람을 모으며 조금 더 감고 → 8.4 에 몸째 왼쪽으로 크게 돌며 왼팔을 바깥으로 휘둘러 바람을 일으킴 (내디딤) → 지나쳐 → 숨 → 거둠
m = off_bare(Motion("spirit_wind", 20, "바람의 정령 — 왼팔을 오른 어깨 위로 감아 몸을 끝까지 비틀었다가 몸째 돌며 바깥으로 크게 휘둘러 바람의 정령을 날려 보냄",
                    fade=(0.01, 0.1)))
COIL = P(body=[-4, 52, 0], head=[-4, -30], ra=K.RA_READY, la=LA(bent([-0.65, 0.45, 0.35], 80, (1, 0.3, 0.3))), rl=[18, 0, 6], ll=[-12, 0, -6], rk=20, lk=14,
         root=[0.0, -0.06])
GUST = P(body=[10, -40, 0], head=[0, 14], ra=K.RA_READY, la=LA(arm([0.85, 0.25, 0.45], 4)), root=[-0.28, 0.5], **LUNGE_DEEP)
keyp(m, 0.0, P(COIL, body=[6, 30, 0], la=LA(bent([-0.5, 0.0, 0.6], 70, (1, 0.3, 0.3))), root=[-0.12, 0]))
keyp(m, 2.6, COIL, "inout")
keyp(m, 6.6, P(COIL, body=[-6, 58, 0], head=[-6, -34]))
keyp(m, 8.4, GUST, "in")
keyp(m, 9.6, P(GUST, body=[12, -52, 0], la=LA(arm([0.92, 0.2, 0.3], 4))), "out")
breathe(m, 10.8, 14.4, P(GUST, body=[12, -48, 0]), step=1.8, lean=1.0, knee=2)
keyp(m, 18.5, P(W_READY, body=[6, -4, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 18.5, FPW_LOW)
fpl(m, 0.0, ((-0.2, 0.0, 0.0), (15, 70, 0)))
fpl(m, 2.6, ((-0.3, 0.12, 0.0), (20, 85, 0)), "inout")
fpl(m, 6.6, ((-0.32, 0.14, 0.02), (20, 90, 0)))
fpl(m, 8.4, ((0.34, 0.12, -0.2), (25, -45, 0)), "in")
fpl(m, 14.4, ((0.3, 0.08, -0.18), (20, -40, 0)))
fpl(m, 18.5, FP_LOW)
use(m, "summonSpirits:noble")

# ------------------------------------------------------------ 도끼

# 회전 도끼 (효과: 0 3D 룬 도끼가 손을 떠나 톱날처럼 돌며 날아감 · 끝에서 머물며 휩씀 · 돌아와 ≈ 22 손에 잡힘): 0 에서 도끼를 오른 어깨 위 뒤로 크게 감고
# 몸을 오른쪽으로 비틀어 왼손으로 겨눔 → 0.9 에 몸째 내던지며 크게 내디딤 (손을 떠남) → 빈손으로 버팀 → 20.6 에 오른손을 앞 위로 뻗어 → 21.8 에 받아
# 몸으로 당기며 반동 → 거둠 (손: thrown — 손을 떠난 동안 빈손)
m = Motion("axe_boomerang", 28, "회전 도끼 — 도끼를 어깨 위 뒤로 크게 감았다가 몸째 내던지고, 빈손으로 버티다가 돌아오는 도끼를 받아 당김", fade=(0.01, 0.08))
m.set(hands={"main": "thrown"})
AXE_WIND = P(body=[-6, 50, 0], head=[0, -30], ra=arm([0.55, 0.6, -0.55], 60, [0.2, 0.7, -0.65]), la=LA(A_POINT), rl=[22, 0, 4], ll=[-18, 0, -4], rk=34,
             lk=16, root=[-0.1, -0.14])
AXE_REL = P(body=[24, -34, 0], head=[4, 10], ra=arm([-0.1, -0.05, 1], 4, [0, 0.3, 0.95]), la=LA(A_HIP), root=[-0.32, 0.55], **LUNGE_DEEP)
WAIT = P(body=[8, -8, 0], ra=arm([0.25, -0.2, 0.9], 30), la=LA(A_GUARD), root=[-0.1, 0.15], **LEGS_FIGHT)
CATCH = P(body=[4, 8, 0], head=[-4, 0], ra=arm([0.4, -0.15, 0.6], 80, [0, 0.55, 0.85]), la=LA(A_GUARD), rl=[22, 0, 4], ll=[-12, 0, -4], rk=26, lk=20,
          root=[-0.1, -0.12])
keyp(m, 0.0, AXE_WIND)
keyp(m, 0.95, AXE_REL, "in")
keyp(m, 2.2, P(AXE_REL, body=[28, -40, 0], ra=arm([-0.15, -0.3, 0.94], 8)), "out")
keyp(m, 6.0, WAIT, "inout")
breathe(m, 7.0, 18.4, WAIT, step=2.8, lean=1.0, knee=2)
keyp(m, 20.6, P(WAIT, ra=arm([0.3, 0.35, 0.88], 20), body=[2, -4, 0]), "out")
keyp(m, 21.8, CATCH, "in")
keyp(m, 23.0, P(CATCH, body=[2, 12, 0], root=[-0.08, -0.16]), "out")
keyp(m, 26.5, P(W_READY, body=[6, 4, 0]))
m.set(hide=[m.t(0.95), m.t(21.7)])
fpr(m, 1.0, nudge(FP_JAB2, (0, -0.08, 0), (-8, 0, 0)))  # 던진 빈손이 앞으로 뻗음
fpr(m, 6.0, FP_GUARD, "inout")
fpr(m, 18.4, FP_GUARD)
fpr(m, 20.6, FP_PALM_UP, "out")  # 돌아오는 도끼를 잡으러 손을 듦
m.fp(21.8, (-0.2, 0.15, 0.0), None, (0.05, 0.75, 0.65))  # 잡은 무기
m.fp(24.5, (-0.05, 0.05, 0.05), None, (0.0, 0.9, 0.35), None, "out")
use(m, "axeBoomerang")

# 도약 찍기 (효과: 0 ~ 6 뛰어오름 — 머리 위로 치켜든 3D 룬 도끼가 몸을 따라옴 · 6 내려서며 날이 앞 땅에 박힘 (피해)): 0 에서 깊이 웅크려 도끼를 아래 뒤로 끌고 →
# 1 에 박차 오르며 도끼를 두 손으로 머리 위 뒤로 치켜들고 몸을 활처럼 젖힘 (무릎을 당김) → 4.8 에 내려오며 휘두르기 시작 → 6 에 온몸으로 앞 땅을 내리찍고
# 깊이 웅크림 → 반동 → 박은 채 숨 → 일어섬
m = off_bare(Motion("leap_slam", 18, "도약 찍기 — 깊이 웅크렸다가 박차 오르며 도끼를 머리 위 뒤로 치켜들어 몸을 젖히고, 내려서며 온몸으로 앞 땅을 내리찍음",
                    fade=(0.01, 0.1)))
CROUCH_AXE = P(body=[30, 6, 0], head=[14, 0], ra=arm([0.3, -0.45, -0.85], 30, [0, 0.2, -1]), la=LA(arm([0.3, -0.45, -0.85], 40)), rl=[-34, 0, 8],
               ll=[-34, 0, -8], rk=86, lk=86, root=[-0.4, 0])
AIR_ARCH = P(body=[-24, 2, 0], head=[-12, 0], ra=arm([0.08, 0.86, -0.5], 40, [0, 0.12, -1]), la=LA(bent([-0.08, 0.86, -0.45], 46, (0, 1, 0))), rl=[-60, 0, 6],
             ll=[-50, 0, -6], rk=96, lk=90, root=[0.1, 0])
AXE_DOWN = P(body=[50, 0, 0], head=[18, 0], ra=arm([0.05, -0.66, 0.75], 10, [0, -0.92, 0.38]), la=LA(arm([-0.03, -0.68, 0.73], 16)), rl=[-32, 0, 10],
             ll=[-36, 0, -10], rk=80, lk=84, root=[-0.5, 0.32])
keyp(m, 0.0, CROUCH_AXE)
keyp(m, 1.2, AIR_ARCH, "out")
keyp(m, 3.4, P(AIR_ARCH, body=[-28, 2, 0], head=[-16, 0]))
keyp(m, 4.8, P(AIR_ARCH, body=[-6, 2, 0], ra=arm([0.1, 0.7, 0.35], 30, [0, 0.95, 0.3]), la=LA(arm([-0.08, 0.72, 0.3], 34)), rl=[-30, 0, 6], ll=[-26, 0, -6],
               rk=50, lk=50), "in")
keyp(m, 6.0, AXE_DOWN, "in")
keyp(m, 7.0, P(AXE_DOWN, body=[44, 0, 0], rk=72, lk=76, root=[-0.44, 0.3]), "out")
keyp(m, 8.2, AXE_DOWN)
breathe(m, 9.2, 12.6, AXE_DOWN, step=1.7, lean=1.5, knee=3)
keyp(m, 16.5, P(W_READY, body=[10, 4, 0], root=[-0.04, 0.1]))
m.arc(6.0, "ra", [0.05, 0.6, 0.8])
m.set(trail=[[m.t(4.6), m.t(6.4)]])
fpw(m, 0.0, FPW_LOW, dp=(0.1, -0.1, 0.2))
fpw(m, 1.2, FPW_SKY, "out", dp=(0.1, 0.12, 0.3))
fpw(m, 6.0, FPW_DOWN, "in", dp=(0, -0.1, 0))
fpw(m, 12.6, FPW_DOWN)
fpw(m, 16.5, FPW_LOW)
fpl(m, 1.2, nudge(K.FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out")
fpl(m, 6.0, FP_LOW, "in")
use(m, "leapSlam")

# ------------------------------------------------------------ 신체 강화 (몸에 거는 기운)

# 투지 (효과: 0 몸에서 기운이 터짐 · 1 ~ 10 3D 빛 고리 둘이 발밑에서 머리 위로 솟음): 0 에서 깊이 웅크려 두 주먹을 허리에 당김 → 1.2 에 터지듯 일어서며 가슴을
# 펴고 두 주먹을 몸 앞 아래로 굳게 쥐어 힘을 줌 (고개를 젖혀 포효) → 지나쳐 → 힘준 자세로 숨 → 거둠
m = off_bare(Motion("fighting_spirit", 18, "투지 — 깊이 웅크려 주먹을 허리에 당겼다가 터지듯 일어서며 가슴을 펴고 두 주먹을 굳게 쥐어 포효함", fade=(0.01, 0.1)))
COILED = P(body=[26, 0, 0], head=[16, 0], ra=arm([0.35, -0.6, -0.45], 100, [0, 0.3, 1]), la=LA(A_CHAMBER), root=[-0.36, 0], **BRACE)
GRIT = P(body=[-16, 0, 0], head=[-26, 0], ra=bent([0.5, -0.55, 0.5], 110, (0, 1, 0.3)), la=LA(bent([0.5, -0.55, 0.5], 110, (0, 1, 0.3))), rl=[0, 0, 18],
         ll=[0, 0, -18], rk=22, lk=22, root=[-0.12, 0])
keyp(m, 0.0, COILED)
keyp(m, 1.2, GRIT, "in")
keyp(m, 2.4, P(GRIT, body=[-20, 0, 0], head=[-32, 0]), "out")
keyp(m, 3.6, GRIT)
breathe(m, 4.6, 11.0, GRIT, step=2.0, lean=1.5, knee=3)
keyp(m, 16.5, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 1.2, FPW_UP, "in", dp=(0.05, -0.25, -0.1))
fpw(m, 11.0, FPW_UP, dp=(0.05, -0.25, -0.1))
fpw(m, 16.5, FPW_LOW)
fpl(m, 0.0, FP_CHAMBER)
fpl(m, 1.2, FP_GUARD, "in")
fpl(m, 11.0, FP_GUARD)
fpl(m, 16.5, FP_LOW)
use(m, "fightingSpirit")

# 강철 근육 (효과: 1 ~ 8 3D 쇳빛 육각 판 여섯이 몸 둘레를 나선으로 감아 오름 · 9 번쩍 · 10 몸에 스밈): 0 에서 웅크려 두 팔을 몸 앞 아래로 모아 온몸에 힘을 줌
# (부르르 떨림 — 판이 감아 오름) → 9.4 에 판이 붙는 순간 두 팔을 옆으로 벌려 알통을 쥐듯 가슴을 폄 → 지나쳐 → 숨 → 거둠
m = off_bare(Motion("steel_body", 18, "강철 근육 — 웅크려 두 팔을 몸 앞 아래로 모아 온몸에 힘을 주며 떨다가, 쇠 판이 붙는 순간 두 팔을 벌려 가슴을 폄", fade=(0.01, 0.1)))
CRAB = P(body=[22, 0, 0], head=[-2, 0], ra=bent([0.45, -0.55, 0.55], 70, (-1, 0, 0.2)), la=LA(bent([0.45, -0.55, 0.55], 70, (-1, 0, 0.2))), rl=[-14, 0, 12],
         ll=[-14, 0, -12], rk=40, lk=40, root=[-0.2, 0])
DOUBLE = P(body=[-12, 0, 0], head=[-14, 0], ra=arm([0.95, 0.2, 0.1], 118, [0.1, 1, 0.1]), la=LA(K.A_FLEX), rl=[0, 0, 16], ll=[0, 0, -16], rk=22, lk=22,
           root=[-0.12, 0])
keyp(m, 0.0, CRAB)
for i, tk in enumerate((1.6, 3.0, 4.4, 5.8, 7.2, 8.4)):
    s = 1 if i % 2 else -1
    keyp(m, tk, P(CRAB, body=[22 + s * 1.2, s * 1.5, s * 1.2], rk=40 + i, lk=40 + i))
keyp(m, 9.4, DOUBLE, "in")
keyp(m, 10.6, P(DOUBLE, body=[-16, 0, 0], head=[-20, 0]), "out")
breathe(m, 11.8, 14.0, DOUBLE, step=1.1, lean=1.0, knee=2)
keyp(m, 17.0, P(W_READY, body=[6, 0, 0]))
FPL_CRAB = ((-0.18, -0.15, -0.1), (5, 55, 0))
FPL_FLEX = ((0.22, 0.12, 0.0), (55, -10, 0))
fpw(m, 0.0, FPW_LOW, dp=(-0.25, 0.1, -0.05))
fpw(m, 8.4, FPW_LOW, dp=(-0.25, 0.08, -0.05))
fpw(m, 9.4, FPW_UP, "in", dp=(0.4, -0.15, 0.1))
fpw(m, 14.0, FPW_UP, dp=(0.4, -0.15, 0.1))
fpw(m, 17.0, FPW_LOW)
fpl(m, 0.0, FPL_CRAB)
fpl(m, 8.4, FPL_CRAB)
fpl(m, 9.4, FPL_FLEX, "in")
fpl(m, 14.0, FPL_FLEX)
fpl(m, 17.0, FP_LOW)
use(m, "steelBody")

# 재생 (효과: 1 ~ 24 3D 생명빛 고리가 머리 위에서 돌며 발밑까지 내려옴 · 20 틱마다 회복 — 발밑에서 작은 고리가 솟음): 0 에서 왼손을 가슴에 대고 고개를 숙임 →
# 5 에 숨을 깊이 들이쉬며 가슴을 펴고 고개를 듦 (고리가 머리 위에서 돎) → 10 에 내쉬며 → 13 에 두 팔을 옆으로 천천히 펴 손바닥을 위로 (빛을 받음) → 거둠
m = off_bare(Motion("regen_body", 24, "재생 — 손을 가슴에 대고 숨을 깊이 들이쉬어 고개를 들고, 내쉬며 두 팔을 천천히 펴 손바닥으로 빛을 받음", fade=(0.01, 0.1)))
BOW_IN = P(body=[14, 0, 0], head=[18, 0], ra=K.RA_LOW, la=LA(A_CHEST), rl=[-6, 0, 6], ll=[-6, 0, -6], rk=16, lk=16, root=[-0.08, 0])
BREATH = P(body=[-12, 0, 0], head=[-22, 0], ra=K.RA_LOW, la=LA(A_CHEST), rk=2, lk=2, root=[0.04, 0])
OPEN = P(body=[-6, 0, 0], head=[-14, 0], ra=arm([0.85, -0.25, 0.4], 10, [0.3, 0.9, 0.2]), la=LA(arm([0.85, -0.25, 0.4], 10)), **LEGS_WIDE)
keyp(m, 0.0, BOW_IN)
keyp(m, 5.0, BREATH, "inout")
keyp(m, 8.0, P(BREATH, body=[-14, 0, 0], head=[-26, 0]))
keyp(m, 10.6, P(BREATH, body=[4, 0, 0], head=[6, 0], root=[-0.02, 0]), "inout")
keyp(m, 14.0, OPEN, "inout")
breathe(m, 15.4, 19.4, OPEN, step=2.0, lean=1.2, knee=2)
keyp(m, 23.0, P(W_READY, body=[6, 0, 0]))
FPL_CHEST = ((-0.25, -0.12, 0.05), (10, 70, 0))
fpw(m, 0.0, FPW_LOW)
fpw(m, 23.0, FPW_LOW)
fpl(m, 0.0, FPL_CHEST)
fpl(m, 10.6, FPL_CHEST)
fpl(m, 14.0, nudge(FP_PALM_UP, (0.2, -0.1, 0.05), (0, -30, 0)), "inout")
fpl(m, 19.4, nudge(FP_PALM_UP, (0.2, -0.12, 0.05), (0, -30, 0)))
fpl(m, 23.0, FP_LOW)
use(m, "regenBody")

# ------------------------------------------------------------ 평타 강화 (무기에 힘을 실음)

# 날 세우기 (효과: 1 ~ 3 몸 앞 오른쪽에 3D 룬 검이 섬 · 2 ~ 5 · 6 ~ 9 숫돌 고리가 손잡이에서 칼끝까지 훑음 · 10 번쩍 · 11 ~ 16 손으로 스밈): 0 에서 무기를 얼굴 앞에
# 곧게 세우고 왼손으로 날밑을 잡음 → 2 ~ 5 왼손으로 날을 따라 칼끝까지 훑어 올림 (몸을 조금 젖힘) → 내려 → 6 ~ 9 한 번 더 → 10.6 에 무기를 아래 오른쪽으로
# 홱 털어 세움 → 거둠
m = off_bare(Motion("edge_hone", 18, "날 세우기 — 무기를 얼굴 앞에 곧게 세우고 왼손으로 날을 두 번 훑어 올린 뒤 아래로 홱 털어 세움", fade=(0.01, 0.1)))
UPRIGHT = P(body=[-4, 6, 0], head=[-4, 0], ra=K.RA_UPRIGHT, la=LA(bent([0.05, -0.1, 0.9], 100, (-1, 0.5, 0))), **LEGS_FIGHT)
STROKE_TOP = P(UPRIGHT, body=[-10, 6, 0], head=[-12, 0], la=LA(arm([-0.12, 0.62, 0.78], 30)))
FLICK = P(body=[12, -14, 0], head=[4, 6], ra=arm([0.5, -0.45, 0.72], 8, [0.6, -0.5, 0.6]), la=LA(A_GUARD), root=[-0.2, 0.3], **LUNGE_DEEP)
keyp(m, 0.0, UPRIGHT)
for t0 in (2.0, 6.0):
    keyp(m, t0, UPRIGHT)
    keyp(m, t0 + 3.0, STROKE_TOP, "inout")
keyp(m, 5.8, P(UPRIGHT, la=LA(bent([0.05, -0.05, 0.9], 95, (-1, 0.5, 0)))), "in")
keyp(m, 10.6, FLICK, "in")
keyp(m, 11.8, P(FLICK, body=[14, -18, 0]), "out")
breathe(m, 13.0, 15.0, FLICK, step=1.0, lean=1.0, knee=2)
keyp(m, 17.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_UP, dp=(-0.1, -0.1, 0))
fpw(m, 9.0, FPW_UP, dp=(-0.1, -0.08, 0))
fpw(m, 10.6, FPW_DOWN, "in", dp=(0.4, 0, 0))
fpw(m, 15.0, FPW_DOWN, dp=(0.4, 0, 0))
fpw(m, 17.0, FPW_LOW)
for t0 in (2.0, 6.0):
    fpl(m, t0, ((-0.3, -0.05, -0.1), (15, 70, 0)))
    fpl(m, t0 + 3.0, ((-0.32, 0.35, -0.1), (30, 70, 0)), "inout")
fpl(m, 10.6, FP_LOW, "in")
use(m, "edgeHone")

# 연타 (효과: 0 ~ 2 3D 룬 검이 오른쪽 위에서 왼쪽 아래로 · 2 ~ 4 왼쪽 위에서 오른쪽 아래로 — X): 0 에서 무기를 오른 어깨 위로 감은 자세 → 1 에 왼쪽 아래로
# 비스듬히 베어 내림 → 2 에 왼 어깨 위로 되감아 → 3.2 에 오른쪽 아래로 베어 내림 (내디딤) → 지나쳐 → 숨 → 거둠
m = Motion("flurry", 12, "연타 — 오른 어깨 위에서 왼쪽 아래로, 되감아 왼 어깨 위에서 오른쪽 아래로 X 자로 두 번 빠르게 베어 내림", fade=(0.01, 0.1))
X_R_UP = P(body=[0, 34, 0], head=[0, -16], ra=arm([0.6, 0.6, -0.1], 60, [0.1, 0.8, -0.6]), la=LA(A_GUARD), rl=[16, 0, 4], ll=[-14, 0, -4], rk=26, lk=18,
           root=[-0.1, 0])
X_L_DN = P(body=[16, -30, 0], head=[4, 10], ra=arm([-0.45, -0.55, 0.7], 8, [-0.5, -0.65, 0.55]), la=LA(A_GUARD), root=[-0.22, 0.3], **LEGS_FIGHT)
X_L_UP = P(body=[2, -34, 0], head=[0, 16], ra=arm([-0.45, 0.65, 0.4], 60, [-0.6, 0.75, -0.2]), la=LA(A_GUARD), rl=[16, 0, 4], ll=[-14, 0, -4], rk=24, lk=18,
           root=[-0.12, 0.1])
X_R_DN = P(body=[20, 28, 0], head=[4, -10], ra=arm([0.6, -0.55, 0.55], 8, [0.6, -0.6, 0.5]), la=LA(A_GUARD), root=[-0.3, 0.5], **LUNGE_DEEP)
keyp(m, 0.0, X_R_UP)
keyp(m, 1.0, X_L_DN, "in")
keyp(m, 2.1, X_L_UP, "inout")
keyp(m, 3.2, X_R_DN, "in")
keyp(m, 4.3, P(X_R_DN, body=[22, 34, 0]), "out")
breathe(m, 5.4, 7.6, X_R_DN, step=1.1, lean=1.0, knee=2)
keyp(m, 11.0, P(W_READY, body=[6, 4, 0]))
m.set(trail=[[m.t(0.0), m.t(1.3)], [m.t(2.1), m.t(3.5)]])
fpw(m, 0.0, FPW_UP, dp=(0.3, 0.1, 0.1))
fpw(m, 1.0, FPW_DOWN, "in", dp=(-0.4, 0, 0))
fpw(m, 2.1, FPW_UP, "inout", dp=(-0.5, 0.1, 0.1))
fpw(m, 3.2, FPW_DOWN, "in", dp=(0.4, 0, 0))
fpw(m, 7.6, FPW_DOWN, dp=(0.4, 0, 0))
fpw(m, 11.0, FPW_LOW)
use(m, "flurry")

# 가르기 (효과: 0 ~ 3 3D 룬 도끼가 낮고 넓게 오른쪽에서 왼쪽 끝까지 가로로 휩씀): 0 에서 무기를 오른쪽 뒤 낮게 끌어 몸을 끝까지 감고 웅크림 → 1.5 에 낮고 넓게
# 왼쪽 끝까지 쓸어 벰 (깊이 내디딤) → 지나쳐 → 숨 → 거둠
m = Motion("cleave", 14, "가르기 — 무기를 오른쪽 뒤 낮게 끌어 몸을 끝까지 감았다가 깊이 내디디며 낮고 넓게 왼쪽 끝까지 쓸어 벰", fade=(0.01, 0.1))
SWEEP_W = P(body=[18, 56, 0], head=[4, -34], root=[-0.3, -0.05], rl=[24, 0, 6], ll=[-14, 0, -6], rk=60, lk=40)
SWEEP_E = P(body=[28, -58, 0], head=[8, 16], root=[-0.42, 0.6], **LUNGE_DEEP)
strike_with(m, "lance_sweep", 0.0, 1.5, 3.0, a=SWEEP_W, b=SWEEP_E)
keyp(m, 3.6, P(SWEEP_E, ra=arm([-0.8, -0.35, 0.45], 12, [-0.85, -0.3, -0.4]), la=LA(arm([0.35, -0.5, -0.75], 25))), "out")
breathe(m, 4.6, 8.0, P(SWEEP_E, ra=arm([-0.8, -0.35, 0.45], 12, [-0.85, -0.3, -0.4]), la=LA(arm([0.35, -0.5, -0.75], 25))), step=1.7, lean=1.0, knee=2)
keyp(m, 12.5, P(W_READY, body=[8, -6, 0], root=[-0.04, 0.2]))
fpw(m, 3.6, dict(p=(-0.95, -0.15, 0.05), blade=(-0.9, -0.2, 0.4), edge=(0, -1, 0)), "out")
fpw(m, 8.0, dict(p=(-0.95, -0.15, 0.05), blade=(-0.9, -0.2, 0.4), edge=(0, -1, 0)))
fpw(m, 12.5, FPW_LOW)
use(m, "cleave")
