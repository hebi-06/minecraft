"""0.51.0 2차 직업 스킬 동작 — 수호기사 2차 (2026-11-06 다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음 … 나는 멋이 제일 중요함" → 고른 것:
푸른 불사조 · 타락한 흑기사): 각성 피닉스 가드 · 반전 다크 나이트 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 — PhoenixSkills · DarkknightSkills). 둘 다 방패
(오른손 — 1차 수호기사 job_motions_c 꼴, 왼손은 맨손). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (JobKit.motionLater — 블루 인페르노의 터짐
:burst, 피닉스 디센트의 내리꽂음 :dive, 피스트의 물기 :bite, 어비설 메이의 터뜨림 :crush · JobRecast.motion — 크런치의 다시 누름 :2). 단계 동작의 틱 0 열쇠는 앞 동작의
끝 자세 (한 장 쉬는 자세로 튀지 않게). 피닉스 가드는 방패를 치켜들고 · 날개처럼 팔을 펼쳐 날아들고 · 무릎 꿇어 방패를 박고 · 가슴을 펴 포효하고 · 하늘로 방패를
치켜들어 불사조를 부르는 영웅의 큰 동작, 다크 나이트는 방패를 낮게 들고 몸을 숙인 짐승 같은 기사 — 방패를 앞으로 내질러 물고 · 비틀어 찢고 · 엎드려 뛰어들고 ·
땅에 박은 방패 뒤에서 끌어당기고 · 웅크려 포효하고 · 왼손을 갈퀴처럼 들어 땅의 아가리를 부름 (1차 수호기사 · 다른 직업과 겹치지 않게 — 1차의 방패 치기 strike 꼴은
쓰지 않음)."""
import rune_motion_kit as K
from job_motions_c import shield
from job_motions_d import first, impacts
from rune_motion_kit import (A_CHEST, A_HIP, A_PULL, FP_CROSS, FP_DOWN, FP_GROUND, FP_GUARD, FP_JAB, FP_LOW, FP_PALM, FP_PUSH_OUT, FP_RAISE, FP_SPREAD_UP,
                             FPW_DOWN, FPW_LOW, FPW_SKY, FPW_UP, LEGS_FIGHT, LEGS_WIDE, P, L, arm, bent, fpl, fpw, keyp, nudge, off_bare, use_skill)

Motion = K.Motion

# 피닉스 가드 끝 자세: 방패를 가슴 앞에 세워 들고 왼손은 허리에
READY_PH = P(body=[8, -4, 0], head=[-4, 0], ra=shield([0.2, -0.35, 0.9], 70, (0, 1, 0.1), (0.1, 0, 1)), la=L(A_HIP), **LEGS_FIGHT)
# 다크 나이트 끝 자세: 방패를 낮게 앞으로 내민 채 몸을 숙이고 왼손은 갈퀴처럼 펴 허리 앞에 (노려봄)
READY_DK = P(body=[18, 8, 0], head=[8, -6], ra=shield([0.3, -0.55, 0.78], 55, (0.1, 1, 0.2), (0.2, 0, 1)), la=bent([-0.3, -0.6, 0.55], 70, (0.2, 1, 0.3)),
             **LEGS_FIGHT)

# 1인칭 방패 꼴
FPS_FRONT = dict(p=(-0.3, 0.1, -0.35))  # 앞 가운데에 세움
FPS_GUARD = dict(p=(-0.35, 0.05, -0.2))  # 가슴 앞
FPS_BACK = dict(p=(0.45, -0.25, 0.35))  # 오른쪽 뒤 허리로 당김 (화면 오른쪽 아래 밖)
FPS_THRUST = dict(p=(-0.32, 0.12, -0.6))  # 앞으로 곧게 내지름 (조준점 가까이)
FPS_SPREAD = dict(p=(0.5, 0.0, -0.05))  # 오른쪽 바깥으로 펼침 (날개)
FPL_CLAW = ((-0.05, 0.25, -0.35), (60, 40, 0))  # 왼손을 앞 위로 갈퀴처럼 듦
FPL_WING = ((-0.3, 0.05, -0.05), (20, -40, 0))  # 왼팔을 왼쪽 바깥으로 펼침 (날개)

# 두 손으로 방패를 머리 위로 높이 듦 (불꽃을 받음 · 날개가 펼쳐짐)
RAISE = P(body=[-12, 0, 0], head=[-28, 0], ra=shield([0.1, 0.97, 0.2], 25, (0, 1, 0), (0, 0.4, 1)), la=bent([0.1, 0.97, 0.18], 14, (-0.3, 1, 0)), rl=[0, 0, 10],
          ll=[0, 0, -10], rk=14, lk=14, root=[-0.04, 0.0])
RAISE_HI = P(RAISE, body=[-16, 0, -1], head=[-32, 0], rk=20, lk=20, root=[-0.06, 0.0])
# 무릎을 꿇으며 방패를 앞 땅에 내리꽂음 (왼손은 땅을 짚음)
PLANT = P(body=[34, 0, 0], head=[2, 0], ra=shield([0.06, -0.62, 0.78], 18, (0, -0.3, 1), (0, 0.6, 0.8)), la=arm([-0.3, -0.85, 0.42], 8), rl=[6, 0, 6], rk=94,
          ll=[-84, 0, -6], lk=88, root=[-0.42, 0.0])

# ------------------------------------------------------------ 피닉스 가드 (방패 — 푸른 불꽃 · 흰 불)

# 피닉스 배시 (효과: 0 ~ 6 치켜든 방패 양옆으로 날개가 펼쳐짐 → 6 앞을 내리침 · 날개가 깃털로)
BA_SLAM = P(body=[36, 0, 0], head=[-6, 0], ra=shield([0.05, -0.55, 0.83], 10, (0, -0.85, 0.5), (0, 0.5, 0.85)), la=arm([-0.55, -0.35, 0.6], 10), rl=[30, 0, 4],
            ll=[-44, 0, -4], rk=26, lk=54, root=[-0.3, 0.42])  # 크게 내디디며 머리 위의 방패를 앞 땅으로 내리침 (왼팔은 옆으로 펼침)
m = Motion("phoenix_bash", 16, "피닉스 배시 — 두 손으로 방패를 머리 위로 높이 치켜들었다가, 크게 내디디며 앞 땅으로 힘껏 내리치고 왼팔을 날개처럼 옆으로 펼침",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.6, RAISE, "out3")
keyp(m, 4.8, RAISE_HI)
keyp(m, 6.0, BA_SLAM, "in")
keyp(m, 9.4, P(BA_SLAM, body=[32, 0, 0], root=[-0.28, 0.4]))
keyp(m, 15.6, READY_PH, "inout")
fpw(m, 2.6, FPW_SKY, "out3")
fpw(m, 4.8, FPW_SKY, dp=(0.02, 0.05, 0.05))
fpw(m, 6.0, FPW_DOWN, "in")
fpw(m, 9.4, FPW_DOWN)
fpw(m, 15.6, FPW_LOW)
fpl(m, 2.6, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out3")
fpl(m, 4.8, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 6.0, FPL_WING, "in")
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 6.0)
use_skill(m, "phoenix_bash")

# 윙 커버 (효과: 0 등의 날개가 펼쳐짐 → 0 ~ 10 아군에게 낮게 날아듦 → 10 내려앉아 내리침 · 날개가 아군을 감쌈)
WG_CROUCH = P(body=[24, 0, 0], head=[8, 0], ra=shield([0.25, -0.45, 0.84], 60, (0, 1, 0.1), (0.1, 0, 1)), la=arm([-0.5, -0.7, 0.3], 15), rl=[-34, 0, 8], ll=[-34, 0, -8],
              rk=72, lk=72, root=[-0.32, 0.0])  # 웅크려 날아오를 힘을 모음
WG_GLIDE = P(body=[30, 0, 0], head=[18, 0], ra=shield([0.85, 0.1, 0.5], 20, (0.2, 1, 0), (1, 0, 0.3)), la=arm([-0.88, 0.12, 0.45], 6), rl=[20, 0, 6], ll=[6, 0, -6],
             rk=30, lk=48)  # 몸을 앞으로 눕혀 두 팔을 날개처럼 활짝 (방패는 오른쪽 바깥) · 다리는 뒤로 흐름
WG_GLIDE2 = P(WG_GLIDE, body=[34, 0, 0], ra=shield([0.82, 0.25, 0.5], 20, (0.2, 1, 0), (1, 0, 0.3)), la=arm([-0.85, 0.28, 0.45], 6))  # 날갯짓 (팔이 조금 위로)
WG_GUARD = P(body=[-4, 0, 0], head=[-10, 0], ra=shield([0.12, -0.15, 0.98], 50, (0, 1, 0.05), (0, 0.05, 1)), la=arm([-0.9, 0.05, 0.4], 6), **LEGS_WIDE)  # 방패를 앞에 · 왼팔로 아군을 감쌈
m = Motion("phoenix_wing", 24, "윙 커버 — 웅크렸다가 몸을 앞으로 눕혀 두 팔을 날개처럼 활짝 펼친 채 아군에게 낮게 날아들고, 무릎을 꿇으며 방패를 땅에 내리꽂았다가 "
           "일어서며 왼팔을 펼쳐 아군을 감쌈", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, WG_CROUCH, "out3")
keyp(m, 3.4, WG_GLIDE, "out3")
keyp(m, 5.6, WG_GLIDE2, "inout")
keyp(m, 7.8, WG_GLIDE, "inout")
keyp(m, 10.0, PLANT, "in")
keyp(m, 13.0, P(PLANT, body=[30, 0, 0], root=[-0.4, 0.0]))
keyp(m, 16.6, WG_GUARD, "out3")
keyp(m, 23.6, READY_PH, "inout")
fpw(m, 1.2, FPS_GUARD, "out3", dp=(0.0, -0.1, 0.0))
fpw(m, 3.4, FPS_SPREAD, "out3")
fpw(m, 7.8, FPS_SPREAD, dp=(0.02, 0.05, 0.0))
fpw(m, 10.0, FPW_DOWN, "in")
fpw(m, 13.0, FPW_DOWN)
fpw(m, 16.6, FPS_FRONT, "out3")
fpw(m, 23.6, FPW_LOW)
fpl(m, 1.2, FP_LOW)
fpl(m, 3.4, FPL_WING, "out3")
fpl(m, 7.8, FPL_WING)
fpl(m, 10.0, FP_GROUND, "in")
fpl(m, 13.0, FP_GROUND)
fpl(m, 16.6, FP_PUSH_OUT, "out3")
fpl(m, 22.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "phoenix_wing")

# 블루 인페르노 (효과: 0 ~ 10 둘레의 불꽃이 치켜든 방패로 모임 → 10 방패를 땅에 박아 불꽃 고리가 솟음 → 10 ~ 30 날개 둘이 고리를 돎 → 27 :burst → 30 바깥으로 터짐)
IF_KNEEL_L = P(PLANT, la=arm([-0.92, -0.25, 0.3], 6))  # 무릎 꿇은 채 왼팔을 왼쪽으로 뻗어 고리를 이끎
IF_KNEEL_F = P(PLANT, la=arm([-0.25, -0.1, 0.96], 6))  # 앞으로
IF_KNEEL_R = P(PLANT, la=arm([0.45, -0.15, 0.88], 8))  # 몸 앞을 지나 오른쪽으로
m = Motion("phoenix_inferno", 30, "블루 인페르노 — 두 손으로 방패를 머리 위로 높이 들어 둘레의 불꽃을 모았다가, 무릎을 꿇으며 방패를 땅에 박고 왼팔을 크게 휘둘러 "
           "불꽃 날개가 고리를 돌게 이끎", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 2.4, RAISE, "out3")
keyp(m, 6.0, P(RAISE, body=[-14, 0, 1], head=[-30, 0], rk=18, lk=18))
keyp(m, 8.6, RAISE_HI)
keyp(m, 10.0, PLANT, "in")
keyp(m, 13.0, P(PLANT, body=[30, 0, 0], root=[-0.4, 0.0]))
keyp(m, 17.0, IF_KNEEL_L, "inout")
keyp(m, 21.0, IF_KNEEL_F, "inout")
keyp(m, 25.0, IF_KNEEL_R, "inout")
keyp(m, 30.0, P(IF_KNEEL_R, body=[28, 0, 0]))
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 8.6, FPW_SKY, dp=(0.02, 0.04, 0.0))
fpw(m, 10.0, FPW_DOWN, "in")
fpw(m, 30.0, FPW_DOWN)
fpl(m, 2.4, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out3")
fpl(m, 8.6, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 10.0, FP_GROUND, "in")
fpl(m, 17.0, FPL_WING, "inout")
fpl(m, 21.0, FP_PALM, "inout")
fpl(m, 25.0, FP_PUSH_OUT, "inout")
fpl(m, 30.0, FP_PUSH_OUT)
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "phoenix_inferno")

# 블루 인페르노 :burst (27 — 터짐 30 = 이 동작의 3): 웅크린 채 힘을 모았다가 솟구치듯 일어서며 방패를 하늘로 치켜들고 왼팔을 펼쳐 불꽃을 바깥으로 터뜨림
IB_RISE = P(body=[-14, 0, 0], head=[-30, 0], ra=shield([0.3, 0.9, 0.3], 20, (0, 1, 0), (0, 0.3, 1)), la=arm([-0.85, 0.45, 0.25], 6), **LEGS_WIDE)
m = Motion("phoenix_inferno_burst", 16, "블루 인페르노 끝 — 웅크린 채 힘을 모았다가 솟구치듯 일어서며 방패를 하늘로 치켜들고 왼팔을 활짝 펼쳐 불꽃을 터뜨림",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(IF_KNEEL_R, body=[28, 0, 0]), FPW_DOWN)
keyp(m, 1.6, P(PLANT, body=[38, 0, 0], la=arm([-0.2, -0.8, 0.5], 10), root=[-0.44, 0.0]), "inout")
keyp(m, 3.0, IB_RISE, "out3")
keyp(m, 8.0, P(IB_RISE, body=[-10, 0, 0], head=[-24, 0]))
keyp(m, 15.6, READY_PH, "inout")
fpw(m, 1.6, FPW_DOWN, dp=(0.0, -0.05, 0.05))
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 8.0, FPW_SKY)
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FP_PUSH_OUT)
fpl(m, 1.6, FP_GROUND, "inout")
fpl(m, 3.0, FP_SPREAD_UP, "out3")
fpl(m, 8.0, FP_SPREAD_UP)
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "phoenix_inferno:burst")

# 이그니션 (태세 — 효과: 0 등의 날개가 펼쳐짐 · 발밑 푸른 불꽃 기둥)
IG_GATHER = P(body=[14, 0, 0], head=[6, 0], ra=shield([0.0, -0.3, 0.95], 95, (-0.4, 1, 0.1), (-0.6, 0, 0.8)), la=L(A_CHEST), rl=[-20, 0, 8], ll=[-20, 0, -8], rk=40,
              lk=40, root=[-0.2, 0.0])  # 방패를 가슴 앞에 끌어안고 왼 주먹을 가슴에 웅크림 (불을 품음)
IG_ROAR = P(body=[-20, 0, 0], head=[-30, 0], ra=shield([0.9, 0.25, 0.3], 10, (0, 1, 0), (1, 0, 0.3)), la=arm([-0.9, 0.3, 0.3], 6), rl=[0, 0, 14], ll=[0, 0, -14],
            rk=10, lk=10, root=[0.02, 0.0])  # 가슴을 펴고 두 팔을 날개처럼 활짝 펼친 채 몸을 젖혀 포효
m = Motion("phoenix_ignition", 22, "이그니션 — 방패를 가슴에 끌어안고 웅크려 불을 품었다가, 가슴을 펴고 두 팔을 날개처럼 활짝 펼친 채 몸을 젖혀 포효함", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.4, IG_GATHER, "out3")
keyp(m, 4.6, P(IG_GATHER, body=[16, 0, 0], root=[-0.22, 0.0]))
keyp(m, 6.4, IG_ROAR, "out3")
keyp(m, 14.0, P(IG_ROAR, body=[-16, 0, 0], head=[-26, 0]))
keyp(m, 21.6, READY_PH, "inout")
fpw(m, 2.4, FPS_GUARD, "out3", dp=(0.1, -0.1, 0.1))
fpw(m, 6.4, FPS_SPREAD, "out3")
fpw(m, 14.0, FPS_SPREAD)
fpw(m, 21.6, FPW_LOW)
fpl(m, 2.4, nudge(FP_CROSS, (0.05, -0.05, 0.1)), "out3")
fpl(m, 6.4, FP_SPREAD_UP, "out3")
fpl(m, 14.0, FP_SPREAD_UP)
fpl(m, 20.6, FP_LOW)
m.set(softLegs=False)
use_skill(m, "phoenix_ignition")

# 피닉스 디센트 (효과: 0 ~ 10 하늘의 불이 치켜든 방패로 → 10 불기둥 → 13 불사조가 깨어나 하늘을 돎 → 32 :dive → 34 ~ 40 내리꽂음)
UL_SKY = P(body=[-22, 0, 0], head=[-40, 0], ra=shield([0.08, 0.98, 0.1], 10, (0, 1, 0), (0, 0.2, 1)), la=bent([0.1, 0.97, 0.15], 18, (-0.3, 1, 0)), rl=[0, 0, 10],
           ll=[0, 0, -10], rk=16, lk=16, root=[-0.06, 0.0])  # 몸을 젖혀 두 손으로 방패를 하늘로 곧게 치켜듦
UL_WATCH = P(body=[-10, 0, 0], head=[-34, 0], ra=shield([0.35, 0.85, 0.35], 30, (0, 1, 0), (0.2, 0.3, 1)), la=arm([-0.8, 0.45, 0.35], 6), **LEGS_WIDE)  # 방패를 든 채 하늘의 불사조를 올려다봄 · 왼팔을 펼침
m = Motion("phoenix_ult", 34, "피닉스 디센트 — 두 손으로 방패를 머리 위로 들어 하늘의 불을 받다가 몸을 젖혀 방패를 하늘로 곧게 치켜들어 불기둥을 세우고, 왼팔을 펼친 "
           "채 하늘을 도는 불사조를 올려다봄", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 3.0, RAISE, "out3")
keyp(m, 7.6, RAISE_HI)
keyp(m, 10.0, UL_SKY, "in")
keyp(m, 15.0, P(UL_SKY, body=[-20, 0, 0], head=[-38, 0]))
keyp(m, 19.0, UL_WATCH, "inout")
keyp(m, 25.0, P(UL_WATCH, body=[-12, 6, 0], head=[-36, -8]), "inout")
keyp(m, 30.0, P(UL_WATCH, body=[-12, -6, 0], head=[-36, 8]), "inout")
keyp(m, 34.0, UL_WATCH, "inout")
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 15.0, FPW_SKY, dp=(0.02, 0.06, 0.0))
fpw(m, 19.0, FPW_UP, "inout")
fpw(m, 34.0, FPW_UP)
fpl(m, 3.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out3")
fpl(m, 15.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 19.0, FP_SPREAD_UP, "inout")
fpl(m, 34.0, FP_SPREAD_UP)
impacts(m, 10.0)
use_skill(m, "phoenix_ult")

# 피닉스 디센트 :dive (32 — 땅에 닿음 40 = 이 동작의 8): 하늘의 방패를 겨눈 곳으로 힘껏 내리그어 불사조를 내리꽂게 하고, 내디딘 채 버팀
UD_POINT = P(body=[24, -6, 0], head=[4, 4], ra=shield([0.1, -0.2, 0.97], 10, (0, 1, 0.1), (0, 0.15, 1)), la=arm([-0.6, -0.3, -0.6], 15), rl=[30, 0, 4], ll=[-46, 0, -4],
             rk=22, lk=54, root=[-0.3, 0.42])  # 크게 내디디며 방패를 겨눈 곳으로 내리그음 (왼팔은 뒤로)
m = Motion("phoenix_ult_dive", 16, "피닉스 디센트 끝 — 하늘로 치켜든 방패를 겨눈 곳으로 힘껏 내리그어 불사조를 내리꽂게 하고, 내디딘 채 버티다가 방패를 가슴 앞에 세움",
           fade=(0.0, 0.1))
off_bare(m)
first(m, UL_WATCH, FPW_UP)
keyp(m, 1.6, P(UL_SKY, body=[-18, 0, 0]), "out3")
keyp(m, 3.6, UD_POINT, "in")
keyp(m, 9.0, P(UD_POINT, body=[22, -4, 0], root=[-0.28, 0.4]))
keyp(m, 15.6, READY_PH, "inout")
fpw(m, 1.6, FPW_SKY, "out3")
fpw(m, 3.6, FPS_THRUST, "in")
fpw(m, 9.0, FPS_THRUST)
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FP_SPREAD_UP)
fpl(m, 3.6, FP_LOW, "in")
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 8.0)
use_skill(m, "phoenix_ult:dive")


# ------------------------------------------------------------ 다크 나이트 (방패 — 검붉은 핏빛 · 심연)

# 크런치 (효과: 0 ~ 4 방패의 아가리가 벌어짐 → 4 앞 적을 물어뜯음 → 다시 누르면 :2)
CR_COIL = P(body=[14, 34, 0], head=[2, -28], ra=shield([0.6, -0.4, -0.2], 85, (0, 1, 0), (0.6, 0, -0.5)), la=bent([-0.4, -0.45, 0.6], 70, (0.2, 1, 0.3)),
            rl=[10, 0, 6], ll=[-16, 0, -6], rk=26, lk=24, root=[-0.14, -0.05])  # 몸을 비틀어 방패를 오른 허리로 당김 (왼손은 앞으로)
CR_BITE = P(body=[24, -18, 0], head=[4, 14], ra=shield([0.0, 0.0, 1.0], 0, (0, 1, 0), (0, 0, 1)), la=bent([-0.45, -0.4, 0.3], 90, (0.2, 1, 0.3)), rl=[28, 0, 4],
            ll=[-40, 0, -4], rk=20, lk=48, root=[-0.26, 0.36])  # 내디디며 방패를 앞으로 곧게 내질러 물어뜯음
m = Motion("darkknight_crunch", 12, "크런치 — 몸을 비틀어 방패를 허리로 당겼다가, 내디디며 방패를 앞으로 곧게 내질러 아가리로 물어뜯음", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, CR_COIL, "out3")
keyp(m, 4.0, CR_BITE, "in")
keyp(m, 6.6, P(CR_BITE, body=[22, -16, 0], root=[-0.24, 0.34]))
keyp(m, 11.6, READY_DK, "inout")
fpw(m, 1.6, FPS_BACK, "out3")
fpw(m, 4.0, FPS_THRUST, "in")
fpw(m, 6.6, FPS_THRUST)
fpw(m, 11.6, FPW_LOW)
fpl(m, 1.6, FP_PALM, "out3")
fpl(m, 4.0, FP_LOW, "in")
fpl(m, 10.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "darkknight_crunch")

# 크런치 :2 (다시 누름 — 효과: 0 ~ 3 한 걸음 내디디며 더 크게 벌림 → 3 물어 찢음 · 끌어옴): 방패를 높이 들었다가 내지르며 비틀어 찢고 몸 쪽으로 홱 끌어당김
C2_RAISE = P(body=[6, 20, 0], head=[-6, -14], ra=shield([0.45, 0.45, 0.6], 60, (0, 1, 0), (0.4, 0, 0.9)), la=bent([-0.35, -0.3, 0.7], 60, (0.2, 1, 0.3)),
             **LEGS_FIGHT)  # 방패를 오른쪽 위로 높이 듦
C2_TEAR = P(body=[30, -24, 0], head=[8, 18], ra=shield([-0.15, -0.3, 0.94], 10, (0, 0.9, 0.3), (-0.3, -0.3, 0.9)), la=arm([-0.5, -0.3, 0.8], 8), rl=[34, 0, 4],
            ll=[-50, 0, -4], rk=22, lk=58, root=[-0.32, 0.5])  # 크게 내디디며 방패를 앞 아래로 내질러 비틀어 찢음
C2_YANK = P(body=[2, 30, 0], head=[-6, -24], ra=shield([0.55, -0.45, 0.2], 90, (0, 1, 0), (0.7, 0, -0.3)), la=L(A_PULL), rl=[12, 0, 6], ll=[-28, 0, -6], rk=24,
            lk=38, root=[-0.2, 0.1])  # 몸 쪽으로 홱 끌어당김 (몸을 비틀며 뒤로)
m = Motion("darkknight_crunch_2", 16, "크런치 다시 — 방패를 오른쪽 위로 들었다가 크게 내디디며 앞 아래로 내질러 비틀어 찢고, 몸을 비틀며 홱 끌어당김", fade=(0.0, 0.1))
off_bare(m)
first(m, READY_DK, FPW_LOW)
keyp(m, 1.4, C2_RAISE, "out3")
keyp(m, 3.0, C2_TEAR, "in")
keyp(m, 5.6, C2_YANK, "out3")
keyp(m, 9.0, P(C2_YANK, body=[4, 26, 0]))
keyp(m, 15.6, READY_DK, "inout")
fpw(m, 1.4, FPW_UP, "out3", dp=(0.2, -0.2, 0.0))
fpw(m, 3.0, FPS_THRUST, "in", )
fpw(m, 5.6, FPS_BACK, "out3")
fpw(m, 9.0, FPS_BACK)
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FP_LOW)
fpl(m, 3.0, FP_PALM, "in")
fpl(m, 5.6, FP_LOW, "out3")
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "darkknight_crunch:2")

# 헝거 런지 (효과: 0 ~ 8 아가리를 앞세워 뛰어듦 → 8 물고 → 8 ~ 14 끌고 감)
LG_LOW = P(body=[34, 0, 0], head=[22, 0], ra=shield([0.1, -0.1, 0.99], 30, (0, 1, 0.1), (0, 0.1, 1)), la=bent([-0.3, -0.55, 0.5], 60, (0.2, 1, 0.3)), rl=[18, 0, 4],
           ll=[-26, 0, -4], rk=36, lk=46, root=[-0.24, 0.1])  # 짐승처럼 엎드려 방패를 앞세움
LG_RUN = P(LG_LOW, body=[38, 0, 0], rl=[-48, 0, 2], ll=[38, 0, -2], rk=66, lk=24, root=[-0.14, 0.34])
LG_RUN2 = P(LG_RUN, rl=[38, 0, 2], ll=[-48, 0, -2], rk=24, lk=66)
LG_SNAP = P(body=[34, -10, 0], head=[16, 6], ra=shield([0.05, 0.05, 1.0], 0, (0, 1, 0), (0, 0, 1)), la=arm([-0.4, -0.55, 0.6], 10), rl=[34, 0, 4], ll=[-54, 0, -4],
            rk=24, lk=62, root=[-0.34, 0.5])  # 방패를 앞으로 내질러 콱 묾
LG_DRAG = P(LG_SNAP, body=[30, -6, 0], la=bent([-0.3, -0.5, 0.6], 80, (0.2, 1, 0.3)), rl=[-30, 0, 2], ll=[30, 0, -2], rk=50, lk=30, root=[-0.26, 0.3])  # 문 채 밀고 감
m = Motion("darkknight_lunge", 18, "헝거 런지 — 짐승처럼 엎드려 방패를 앞세운 채 내달려 방패를 앞으로 내질러 콱 물고, 문 채 몸으로 밀고 감", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.4, LG_LOW, "out3")
keyp(m, 3.2, LG_RUN, "out3")
keyp(m, 4.8, LG_RUN2, "inout")
keyp(m, 6.4, LG_RUN, "inout")
keyp(m, 8.0, LG_SNAP, "in")
keyp(m, 11.0, LG_DRAG, "inout")
keyp(m, 14.0, P(LG_DRAG, rl=[30, 0, 2], ll=[-30, 0, -2], rk=30, lk=50))
keyp(m, 17.6, READY_DK, "inout")
fpw(m, 1.4, FPS_FRONT, "out3", dp=(0.05, -0.1, 0.05))
fpw(m, 6.4, FPS_FRONT, dp=(0.05, -0.12, 0.0))
fpw(m, 8.0, FPS_THRUST, "in")
fpw(m, 14.0, FPS_THRUST, dp=(0.0, -0.04, 0.05))
fpw(m, 17.6, FPW_LOW)
fpl(m, 1.4, FP_GUARD, "out3")
fpl(m, 16.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 8.0)
use_skill(m, "darkknight_lunge")

# 피스트 (효과: 0 ~ 6 방패를 앞 땅에 박음 · 아가리가 벌어짐 → 6 검은 혀가 뻗음 → 6 ~ 16 끌어옴 → 13 :bite → 16 거대한 아가리가 물어뜯음)
FE_PLANT = P(body=[30, 0, 0], head=[6, 0], ra=shield([0.05, -0.6, 0.8], 15, (0, -0.2, 1), (0, 0.6, 0.8)), la=bent([-0.3, -0.5, 0.6], 70, (0.2, 1, 0.3)), rl=[20, 0, 6],
             ll=[-40, 0, -6], rk=44, lk=60, root=[-0.3, 0.2])  # 웅크려 방패를 앞 땅에 박음
FE_HAUL = P(body=[-6, 10, 0], head=[-12, -8], ra=shield([0.1, -0.45, 0.88], 40, (0, 0.6, 0.8), (0, 0.5, 0.9)), la=L(A_PULL), rl=[-16, 0, 8], ll=[20, 0, -8], rk=40,
            lk=20, root=[-0.24, -0.16])  # 몸을 뒤로 젖혀 박은 방패를 붙든 채 왼손으로 끌어당김
FE_HAUL2 = P(FE_HAUL, body=[0, 4, 0], la=bent([-0.3, -0.35, 0.7], 90, (0.2, 1, 0.3)), root=[-0.26, -0.1])  # 다시 뻗어 잡음
m = Motion("darkknight_feast", 16, "피스트 — 웅크려 방패를 앞 땅에 박고, 몸을 뒤로 젖혀 박은 방패를 붙든 채 왼손으로 밧줄을 당기듯 몇 번이고 끌어당김", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 2.4, FE_PLANT, "in")
keyp(m, 6.0, FE_HAUL, "out3")
keyp(m, 9.0, FE_HAUL2, "inout")
keyp(m, 12.0, FE_HAUL, "inout")
keyp(m, 16.0, FE_HAUL2, "inout")
fpw(m, 2.4, FPW_DOWN, "in")
fpw(m, 16.0, FPW_DOWN, dp=(0.0, 0.0, 0.08))
fpl(m, 2.4, FP_GROUND, "in")
fpl(m, 6.0, FP_LOW, "out3")
fpl(m, 9.0, FP_PALM, "inout")
fpl(m, 12.0, FP_LOW, "inout")
fpl(m, 16.0, FP_PALM, "inout")
m.set(softLegs=False)
impacts(m, 2.4)
use_skill(m, "darkknight_feast")

# 피스트 :bite (13 — 물기 16 = 이 동작의 3): 박은 방패를 뽑아 크게 내디디며 두 손으로 앞으로 힘껏 내밀어 한꺼번에 물어뜯음
FB_SHOVE = P(body=[30, -10, 0], head=[8, 8], ra=shield([0.04, -0.02, 1], 4, (0, 1, 0), (0, 0, 1)), la=arm([0.02, -0.05, 1], 6), rl=[34, 0, 4], ll=[-50, 0, -4],
             rk=20, lk=58, root=[-0.3, 0.48])
m = Motion("darkknight_feast_bite", 14, "피스트 끝 — 박은 방패를 뽑아 크게 내디디며 두 손으로 앞으로 힘껏 내밀어 거대한 아가리로 한꺼번에 물어뜯음", fade=(0.0, 0.1))
off_bare(m)
first(m, FE_HAUL2, FPW_DOWN)
keyp(m, 1.6, P(FE_HAUL, body=[-10, 12, 0], root=[-0.26, -0.2]), "inout")
keyp(m, 3.0, FB_SHOVE, "in")
keyp(m, 7.0, P(FB_SHOVE, body=[28, -8, 0], root=[-0.28, 0.46]))
keyp(m, 13.6, READY_DK, "inout")
fpw(m, 1.6, FPW_DOWN, dp=(0.0, 0.05, 0.12))
fpw(m, 3.0, FPS_THRUST, "in")
fpw(m, 7.0, FPS_THRUST)
fpw(m, 13.6, FPW_LOW)
fpl(m, 0.0, FP_PALM)
fpl(m, 3.0, nudge(FP_JAB, (-0.15, -0.05, 0.0)), "in")
fpl(m, 7.0, nudge(FP_JAB, (-0.15, -0.05, 0.0)))
fpl(m, 12.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "darkknight_feast:bite")

# 언홀리 프렌지 (태세 — 효과: 0 방패의 아가리가 포효 · 발밑 검붉은 불)
FR_HUNCH = P(body=[34, 0, 0], head=[20, 0], ra=shield([0.1, 0.1, 0.99], 70, (0, 1, 0.1), (0, 0.1, 1)), la=bent([-0.25, -0.2, 0.6], 100, (0.2, 1, 0.3)),
             rl=[-30, 0, 10], ll=[-30, 0, -10], rk=64, lk=64, root=[-0.3, 0.0])  # 짐승처럼 웅크려 방패를 얼굴 앞에 (눈을 뜸)
FR_ROAR = P(body=[-14, 0, 0], head=[-34, 0], ra=shield([0.75, 0.0, 0.6], 30, (0, 1, 0), (0.8, 0, 0.6)), la=bent([-0.75, 0.25, 0.6], 60, (0.2, 1, 0.3)),
            rl=[-20, 0, 14], ll=[-20, 0, -14], rk=40, lk=40, root=[-0.16, 0.0])  # 웅크린 채 몸을 젖혀 포효 (방패 · 갈퀴 손을 벌림)
m = Motion("darkknight_frenzy", 20, "언홀리 프렌지 — 짐승처럼 웅크려 방패를 얼굴 앞에 들어 붉은 눈을 뜨고, 웅크린 채 몸을 젖혀 방패와 갈퀴 손을 벌리며 포효함",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, FR_HUNCH, "out3")
keyp(m, 4.4, P(FR_HUNCH, body=[36, 4, 0], head=[22, -4]))
keyp(m, 7.0, FR_ROAR, "out3")
keyp(m, 13.0, P(FR_ROAR, body=[-10, 0, 0], head=[-28, 0]))
keyp(m, 19.6, READY_DK, "inout")
fpw(m, 2.0, FPS_FRONT, "out3", dp=(0.05, 0.1, 0.08))
fpw(m, 7.0, FPS_SPREAD, "out3")
fpw(m, 13.0, FPS_SPREAD)
fpw(m, 19.6, FPW_LOW)
fpl(m, 2.0, FP_GUARD, "out3")
fpl(m, 7.0, FPL_CLAW, "out3")
fpl(m, 13.0, FPL_CLAW)
fpl(m, 18.6, FP_LOW)
m.set(softLegs=False)
use_skill(m, "darkknight_frenzy")

# 어비설 메이 (효과: 0 ~ 10 방패로 검붉은 연기가 빨려 듦 → 10 땅에 박음 · 땅이 갈라짐 · 검은 혀 → 26 땅의 아가리가 삼킴 → 짓씹음 → 40 :crush → 44 터뜨림)
AB_CLAW = P(PLANT, body=[24, 0, 0], head=[-10, 0], la=bent([-0.2, 0.9, 0.35], 50, (0.2, 1, 0.3)))  # 무릎 꿇은 채 왼손을 갈퀴처럼 하늘로 치켜듦 (아가리를 부름)
AB_GRIP = P(AB_CLAW, la=bent([-0.25, 0.85, 0.45], 110, (0.2, 1, 0.3)))  # 갈퀴 손을 움켜쥠 (짓씹음)
m = Motion("darkknight_ult", 40, "어비설 메이 — 두 손으로 방패를 머리 위로 들어 검붉은 연기를 빨아들이다가, 무릎을 꿇으며 방패를 땅에 박고 왼손으로 땅을 할퀴어 끌어당긴 뒤 "
           "갈퀴 손을 하늘로 치켜들어 땅의 아가리를 부르고 움켜쥐며 짓씹게 함", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 3.0, RAISE, "out3")
keyp(m, 8.0, RAISE_HI)
keyp(m, 10.0, PLANT, "in")
keyp(m, 13.0, P(PLANT, body=[30, 0, 0], root=[-0.4, 0.0]))
keyp(m, 16.0, P(PLANT, la=arm([-0.25, -0.75, 0.6], 20)), "inout")
keyp(m, 19.0, P(PLANT, la=L(A_PULL)), "inout")
keyp(m, 22.0, P(PLANT, la=arm([-0.25, -0.75, 0.6], 20)), "inout")
keyp(m, 26.0, AB_CLAW, "out3")
keyp(m, 30.0, AB_GRIP, "inout")
keyp(m, 34.0, AB_CLAW, "inout")
keyp(m, 38.0, AB_GRIP, "inout")
keyp(m, 40.0, AB_GRIP)
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 8.0, FPW_SKY, dp=(0.02, 0.04, 0.0))
fpw(m, 10.0, FPW_DOWN, "in")
fpw(m, 40.0, FPW_DOWN)
fpl(m, 3.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out3")
fpl(m, 8.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 10.0, FP_GROUND, "in")
fpl(m, 19.0, FP_LOW, "inout")
fpl(m, 22.0, FP_GROUND, "inout")
fpl(m, 26.0, FPL_CLAW, "out3")
fpl(m, 40.0, FPL_CLAW)
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "darkknight_ult")

# 어비설 메이 :crush (40 — 터뜨림 44 = 이 동작의 4): 치켜든 갈퀴 손을 당겼다가 일어서며 땅을 향해 내리쳐 움켜쥐어 아가리를 터뜨리고, 일어서 방패를 앞에 듦
UC_DRAW = P(AB_GRIP, body=[16, 0, 0], la=bent([-0.1, 0.7, -0.4], 100, (0.2, 1, 0.3)))  # 갈퀴 손을 머리 뒤로 당김
UC_CRUSH = P(body=[34, -8, 0], head=[6, 6], ra=shield([0.25, -0.5, 0.82], 40, (0.1, 1, 0.2), (0.2, 0, 1)), la=bent([-0.1, -0.7, 0.7], 110, (0.2, 1, 0.3)),
             rl=[28, 0, 4], ll=[-46, 0, -4], rk=30, lk=60, root=[-0.34, 0.36])  # 일어서 내디디며 주먹을 앞 아래로 내리쳐 움켜쥠
m = Motion("darkknight_ult_crush", 16, "어비설 메이 끝 — 치켜든 갈퀴 손을 머리 뒤로 당겼다가, 일어서 내디디며 주먹을 땅으로 내리쳐 움켜쥐어 아가리를 터뜨림",
           fade=(0.0, 0.1))
off_bare(m)
first(m, AB_GRIP, FPW_DOWN)
keyp(m, 2.4, UC_DRAW, "out3")
keyp(m, 4.0, UC_CRUSH, "in")
keyp(m, 8.6, P(UC_CRUSH, body=[30, -6, 0], root=[-0.32, 0.34]))
keyp(m, 15.6, READY_DK, "inout")
fpw(m, 2.4, FPW_DOWN, dp=(0.0, 0.05, 0.1))
fpw(m, 4.0, FPS_FRONT, "in")
fpw(m, 8.6, FPS_FRONT)
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FPL_CLAW)
fpl(m, 2.4, nudge(FPL_CLAW, (0.05, 0.15, 0.15)), "out3")
fpl(m, 4.0, FP_DOWN, "in")
fpl(m, 8.6, FP_DOWN)
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "darkknight_ult:crush")
