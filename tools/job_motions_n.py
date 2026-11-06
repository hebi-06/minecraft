"""0.51.0 2차 직업 스킬 동작 — 수호기사 2차 (2026-11-06 사용자 "다음 작업"): 각성 센티널 · 반전 어벤저 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SentinelSkills · AvengerSkills). 둘 다 방패 (오른손 — 1차 수호기사 job_motions_c 꼴, 왼손은 맨손). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄
(JobKit.motionLater — 배스천 차지의 밀어붙임 :slam, 헤이븐 리프의 내려앉음 :land, 레인보우 램파트의 내리침 :slam, 클라리온 콜의 내려앉음 :land, 조브레이커의 내리꽂음
:slam). 단계 동작의 틱 0 열쇠는 앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게). 센티널은 방패를 세워 버티고 · 무지개를 타고 뛰어 · 방패로 땅을 내리쳐 장벽을 세우고 ·
물결을 이끌고 · 뿔피리를 부는 영웅의 큰 동작, 어벤저는 방패를 낮게 늘어뜨린 채 몸을 숙인 복수자 — 크게 비틀어 후려치고 · 황소처럼 들이받고 · 웅크려 모았다가
내지르고 · 가슴을 치며 포효하고 · 붙잡아 솟구쳤다 내리꽂음 (1차 수호기사 · 다른 직업과 겹치지 않게 — 1차의 방패 치기 strike 꼴은 쓰지 않음)."""
import rune_motion_kit as K
from job_motions_c import shield
from job_motions_d import first, impacts
from rune_motion_kit import (A_CHEST, A_HIP, A_PULL, A_SPREAD, FP_CROSS, FP_DOWN, FP_GROUND, FP_GUARD, FP_JAB, FP_LOW, FP_PALM, FP_PALM_UP, FP_PUSH_OUT, FP_RAISE,
                             FP_SPREAD_UP, FPW_DOWN, FPW_LOW, FPW_SKY, FPW_UP, LEGS_FIGHT, LEGS_WIDE, P, L, arm, bent, fpl, fpw, keyp, nudge, off_bare, use_skill)

Motion = K.Motion

# 센티널 끝 자세: 방패를 가슴 앞에 세워 들고 왼손은 허리에
READY_SN = P(body=[8, -4, 0], head=[-4, 0], ra=shield([0.2, -0.35, 0.9], 70, (0, 1, 0.1), (0.1, 0, 1)), la=L(A_HIP), **LEGS_FIGHT)
# 어벤저 끝 자세: 방패를 낮게 늘어뜨리고 왼 주먹을 가슴 앞에 쥔 채 몸을 숙임 (노려봄)
READY_AV = P(body=[16, 10, 0], head=[6, -6], ra=shield([0.35, -0.7, 0.45], 50, (0.1, 1, 0.3), (0.3, 0, 1)), la=bent([-0.25, -0.5, 0.6], 110, (0.2, 1, 0.3)),
             **LEGS_FIGHT)

# 1인칭 방패 꼴
FPS_FRONT = dict(p=(-0.3, 0.1, -0.35))  # 앞 가운데에 세움 (막는 방패)
FPS_GUARD = dict(p=(-0.35, 0.05, -0.2))  # 가슴 앞 (막는 자세)
FPS_BACK = dict(p=(0.45, -0.25, 0.35))  # 오른쪽 뒤 허리로 당김 (화면 오른쪽 아래 밖)
FPS_LEFT = dict(p=(-0.75, 0.1, -0.3))  # 왼쪽 앞으로 휘두름 (화면 왼쪽)
FPS_THRUST = dict(p=(-0.32, 0.12, -0.6))  # 앞으로 곧게 내지름 (조준점 가까이)
FPL_HORN = ((-0.12, 0.28, -0.25), (60, 30, 0))  # 왼손을 입가로 (뿔피리)
FPL_GRAB = ((0.05, 0.05, -0.45), (20, 40, 0))  # 왼손을 앞으로 뻗어 움켜쥠

# ------------------------------------------------------------ 센티널 (방패 — 강철 하늘빛 · 무지개)

# 배스천 차지 (효과: 0 ~ 4 앞의 프리즘 방패가 맺힘 → 4 ~ 10 돌진 (닿은 적을 밀고 감) → 12 :slam → 16 밀어붙임)
CH_BRACE = P(body=[18, -10, 0], head=[8, 6], ra=shield([0.1, -0.15, 0.98], 40, (0, 1, 0.05), (0, 0.05, 1)), la=bent([0.05, -0.2, 0.95], 85, (-1, 0.3, 0.2)),
             rl=[22, 0, 4], ll=[-30, 0, -4], rk=30, lk=40, root=[-0.16, 0.1])  # 낮게 숙여 방패를 앞에 세우고 왼손으로 방패 뒤를 받침
CH_RUN = P(body=[30, -12, 0], head=[18, 8], ra=shield([0.05, -0.1, 0.99], 30, (0, 1, 0.05), (0, 0.05, 1)), la=bent([0.05, -0.15, 0.95], 80, (-1, 0.3, 0.2)),
           rl=[-46, 0, 2], ll=[36, 0, -2], rk=62, lk=24, root=[-0.12, 0.3])  # 방패에 어깨를 대고 내달림 (오른발 앞)
CH_RUN2 = P(CH_RUN, rl=[36, 0, 2], ll=[-46, 0, -2], rk=24, lk=62)  # (왼발 앞)
CH_STOP = P(body=[22, -8, 0], head=[12, 6], ra=shield([0.08, -0.12, 0.98], 36, (0, 1, 0.05), (0, 0.05, 1)), la=bent([0.05, -0.18, 0.95], 85, (-1, 0.3, 0.2)),
            rl=[26, 0, 6], ll=[-36, 0, -6], rk=34, lk=46, root=[-0.2, 0.18])  # 미끄러지며 버티어 멈춤 (방패에 적을 붙인 채)
m = Motion("sentinel_charge", 12, "배스천 차지 — 낮게 숙여 방패를 앞에 세우고 왼손으로 방패 뒤를 받친 채, 방패에 어깨를 대고 내달려 적을 밀고 가다 버티어 멈춤",
           fade=(0.03, 0.0))
off_bare(m)
keyp(m, 1.6, CH_BRACE, "out3")
keyp(m, 3.6, P(CH_BRACE, body=[20, -10, 0], root=[-0.18, 0.1]))
keyp(m, 5.0, CH_RUN, "out3")
keyp(m, 6.6, CH_RUN2, "inout")
keyp(m, 8.2, CH_RUN, "inout")
keyp(m, 9.8, CH_RUN2, "inout")
keyp(m, 12.0, CH_STOP, "out3")
fpw(m, 1.6, FPS_FRONT, "out3")
fpw(m, 9.8, FPS_FRONT, dp=(0.0, -0.04, -0.05))
fpw(m, 12.0, FPS_FRONT, dp=(0.0, -0.02, -0.02))
fpl(m, 1.6, nudge(FP_CROSS, (0.05, 0.0, -0.1)), "out3")
fpl(m, 12.0, nudge(FP_CROSS, (0.05, 0.0, -0.1)))
use_skill(m, "sentinel_charge")

# 배스천 차지 :slam (12 — 밀어붙임 16 = 이 동작의 4): 방패를 가슴으로 당겨 웅크렸다가 두 팔로 방패를 앞으로 힘껏 내밀며 크게 내디딤
SL_GATHER = P(body=[12, -6, 0], head=[4, 4], ra=shield([0.15, -0.4, 0.82], 95, (0, 1, 0.1), (0, 0.1, 1)), la=bent([0.05, -0.4, 0.8], 110, (-1, 0.3, 0.2)),
              rl=[20, 0, 6], ll=[-26, 0, -6], rk=44, lk=44, root=[-0.24, 0.0])  # 방패를 가슴으로 당겨 웅크림
SL_SHOVE = P(body=[30, -14, 0], head=[8, 10], ra=shield([0.04, -0.02, 1], 4, (0, 1, 0), (0, 0, 1)), la=arm([0.02, -0.05, 1], 6), rl=[34, 0, 4], ll=[-50, 0, -4],
             rk=20, lk=58, root=[-0.3, 0.48])  # 크게 내디디며 두 팔로 방패를 앞으로 힘껏 내밂
m = Motion("sentinel_charge_slam", 14, "배스천 차지 끝 — 방패를 가슴으로 당겨 웅크렸다가, 크게 내디디며 두 팔로 방패를 앞으로 힘껏 내밀어 적을 벽에 박음", fade=(0.0, 0.1))
off_bare(m)
first(m, CH_STOP, dict(p=(-0.3, 0.08, -0.37)))
keyp(m, 2.4, SL_GATHER, "inout")
keyp(m, 4.0, SL_SHOVE, "in")
keyp(m, 7.4, P(SL_SHOVE, body=[28, -12, 0], root=[-0.28, 0.46]))
keyp(m, 13.4, READY_SN, "inout")
fpw(m, 2.4, FPS_GUARD, "inout", dp=(0.0, -0.05, 0.12))
fpw(m, 4.0, FPS_THRUST, "in")
fpw(m, 7.4, FPS_THRUST)
fpw(m, 13.4, FPW_LOW)
fpl(m, 0.0, nudge(FP_CROSS, (0.05, 0.0, -0.1)))
fpl(m, 2.4, nudge(FP_CROSS, (0.05, -0.05, 0.05)), "inout")
fpl(m, 4.0, nudge(FP_JAB, (-0.15, -0.05, 0.0)), "in")
fpl(m, 7.4, nudge(FP_JAB, (-0.15, -0.05, 0.0)))
fpl(m, 12.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "sentinel_charge:slam")

# 헤이븐 리프 (효과: 0 무지개를 타고 뛰어오름 → 6 :land → 8 내려앉아 내리침 · 피난처)
HV_CROUCH = P(body=[22, 0, 0], head=[8, 0], ra=shield([0.2, -0.45, 0.86], 60, (0, 1, 0.1), (0.1, 0, 1)), la=arm([-0.45, -0.75, 0.3], 15), rl=[-34, 0, 8], ll=[-34, 0, -8],
              rk=72, lk=72, root=[-0.32, 0.0])  # 웅크려 뛸 힘을 모음
HV_AIR = P(body=[-14, 0, 0], head=[-22, 0], ra=shield([0.15, 0.92, 0.3], 30, (0, 1, 0), (0, 0.3, 1)), la=arm([-0.65, 0.45, 0.4], 15), rl=[-30, 0, 6], ll=[-10, 0, -6],
           rk=56, lk=30, root=[0.0, 0.0])  # 방패를 머리 위로 치켜들고 날아오름 (왼팔을 펼침)
m = Motion("sentinel_haven", 6, "헤이븐 리프 — 웅크렸다가 방패를 머리 위로 치켜들고 왼팔을 펼친 채 무지개를 타고 아군에게 뛰어오름", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 1.2, HV_CROUCH, "out3")
keyp(m, 3.0, HV_AIR, "out3")
keyp(m, 6.0, P(HV_AIR, body=[-8, 0, 0], head=[-14, 0]))
fpw(m, 1.2, FPS_GUARD, "out3", dp=(0.0, -0.1, 0.0))
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 6.0, FPW_SKY)
fpl(m, 1.2, FP_LOW)
fpl(m, 3.0, FP_SPREAD_UP, "out3")
fpl(m, 6.0, FP_SPREAD_UP)
use_skill(m, "sentinel_haven")

# 헤이븐 리프 :land (6 — 내려앉음 8 = 이 동작의 2): 무릎을 꿇으며 방패를 앞 땅에 내리꽂고 왼손으로 땅을 짚었다가, 일어서며 방패를 앞에 세우고 왼팔을 펼쳐 피난처를 감쌈
HL_PLANT = P(body=[30, 0, 0], head=[4, 0], ra=shield([0.08, -0.6, 0.8], 20, (0, -0.2, 1), (0, 0.6, 0.8)), la=arm([-0.3, -0.85, 0.42], 8), rl=[6, 0, 6], rk=92,
             ll=[-82, 0, -6], lk=86, root=[-0.4, 0.0])  # 무릎을 꿇으며 방패를 앞 땅에 내리꽂고 왼손으로 땅을 짚음
HL_GUARD = P(body=[-4, 0, 0], head=[-10, 0], ra=shield([0.12, -0.15, 0.98], 50, (0, 1, 0.05), (0, 0.05, 1)), la=arm([-0.9, 0.05, 0.4], 6), **LEGS_WIDE)  # 방패를 앞에 · 왼팔을 펼침
m = Motion("sentinel_haven_land", 16, "헤이븐 리프 끝 — 무릎을 꿇으며 방패를 앞 땅에 내리꽂고 왼손으로 땅을 짚었다가, 일어서며 방패를 앞에 세우고 왼팔을 펼쳐 피난처로 감쌈",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(HV_AIR, body=[-8, 0, 0], head=[-14, 0]), FPW_SKY)
keyp(m, 2.0, HL_PLANT, "in")
keyp(m, 6.0, P(HL_PLANT, body=[26, 0, 0], root=[-0.38, 0.0]))
keyp(m, 9.6, HL_GUARD, "out3")
keyp(m, 15.6, READY_SN, "inout")
fpw(m, 2.0, FPW_DOWN, "in")
fpw(m, 6.0, FPW_DOWN)
fpw(m, 9.6, FPS_FRONT, "out3")
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FP_SPREAD_UP)
fpl(m, 2.0, FP_GROUND, "in")
fpl(m, 6.0, FP_GROUND)
fpl(m, 9.6, FP_PUSH_OUT, "out3")
fpl(m, 14.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 2.0)
use_skill(m, "sentinel_haven:land")

# 레인보우 램파트 (효과: 0 ~ 10 하늘의 무지개가 방패로 모임 → 7 :slam → 10 방패로 땅을 내리쳐 장벽이 솟음)
RP_RAISE = P(body=[-12, 0, 0], head=[-28, 0], ra=shield([0.1, 0.97, 0.2], 25, (0, 1, 0), (0, 0.4, 1)), la=bent([0.1, 0.97, 0.18], 14, (-0.3, 1, 0)), rl=[0, 0, 10],
             ll=[0, 0, -10], rk=14, lk=14, root=[-0.04, 0.0])  # 두 손으로 방패를 머리 위로 높이 듦 (무지개를 받음)
m = Motion("sentinel_rampart", 7, "레인보우 램파트 — 두 손으로 방패를 머리 위로 높이 들어 하늘의 무지개를 방패로 받음", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 2.4, RP_RAISE, "out3")
keyp(m, 5.0, P(RP_RAISE, body=[-14, 0, 1], head=[-30, 0], rk=18, lk=18))
keyp(m, 7.0, P(RP_RAISE, body=[-16, 0, -1], head=[-32, 0], rk=20, lk=20, root=[-0.06, 0.0]))
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 7.0, FPW_SKY, dp=(0.02, 0.04, 0.0))
fpl(m, 2.4, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)), "out3")
fpl(m, 7.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
use_skill(m, "sentinel_rampart")

# 레인보우 램파트 :slam (7 — 내리침 10 = 이 동작의 3): 방패를 머리 뒤로 젖혔다가 앞 땅에 힘껏 내리쳐 장벽을 세우고, 일어서며 왼팔을 장벽을 따라 옆으로 쓸어 펼침
RS_SLAM = P(body=[38, 0, 0], head=[-8, 0], ra=shield([0.05, -0.62, 0.78], 12, (0, -0.9, 0.42), (0, 0.42, 0.9)), la=arm([-0.02, -0.65, 0.75], 18), rl=[-22, 0, 4],
            ll=[14, 0, -4], rk=40, lk=28, root=[-0.26, 0.12])  # 몸을 숙여 방패를 앞 땅에 내리침
RS_SWEEP = P(body=[-4, -16, 0], head=[-12, 14], ra=shield([0.3, -0.3, 0.9], 40, (0, 1, 0.1), (0.2, 0, 1)), la=arm([-0.95, 0.12, 0.25], 4), **LEGS_WIDE)  # 왼팔을 장벽을 따라 옆으로
m = Motion("sentinel_rampart_slam", 18, "레인보우 램파트 끝 — 머리 위의 방패를 앞 땅에 힘껏 내리쳐 무지개 장벽을 세우고, 일어서며 왼팔을 장벽을 따라 옆으로 쓸어 펼침",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(RP_RAISE, body=[-16, 0, -1], head=[-32, 0], rk=20, lk=20, root=[-0.06, 0.0]), FPW_SKY)
keyp(m, 1.6, P(RP_RAISE, body=[-20, 0, 0], head=[-36, 0], rk=22, lk=22), "out3")
keyp(m, 3.0, RS_SLAM, "in")
keyp(m, 7.0, P(RS_SLAM, body=[34, 0, 0], root=[-0.24, 0.12]))
keyp(m, 10.6, RS_SWEEP, "out3")
keyp(m, 17.4, READY_SN, "inout")
fpw(m, 1.6, FPW_SKY, "out3", dp=(0.0, 0.05, 0.1))
fpw(m, 3.0, FPW_DOWN, "in")
fpw(m, 7.0, FPW_DOWN)
fpw(m, 10.6, FPS_GUARD, "out3")
fpw(m, 17.4, FPW_LOW)
fpl(m, 0.0, nudge(FP_RAISE, (-0.05, 0.02, 0), (2, 5, 0)))
fpl(m, 3.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 7.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)))
fpl(m, 10.6, FP_PUSH_OUT, "out3")
fpl(m, 16.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "sentinel_rampart:slam")

# 나인 타이즈 (태세 — 효과: 0 발밑에서 아홉 물결이 솟아 부서짐 → 둘레를 아홉 물결이 감돎)
TD_DRAW = P(body=[4, -24, 0], head=[-8, 18], ra=shield([0.55, -0.4, 0.72], 40, (0.1, 1, 0.1), (0.4, 0, 0.9)), la=arm([-0.82, -0.25, 0.5], 8), rl=[6, 0, 12], ll=[-6, 0, -12],
              rk=16, lk=16)  # 몸을 왼쪽으로 돌려 왼손을 왼쪽 앞 아래로 뻗어 물결을 끌어올림
TD_SWEEP = P(body=[2, 26, 0], head=[-10, -18], ra=shield([0.6, -0.35, 0.7], 40, (0.1, 1, 0.1), (0.5, 0, 0.85)), la=arm([0.45, 0.25, 0.85], 6), **LEGS_WIDE)  # 왼손이 몸 앞을 지나 오른쪽으로 휘돎
TD_LIFT = P(body=[-8, 0, 0], head=[-18, 0], ra=shield([0.3, -0.2, 0.93], 55, (0, 1, 0.1), (0.15, 0, 1)), la=arm([-0.45, 0.3, 0.84], 8, [0, 1, 0.3]), **LEGS_WIDE)  # 왼손바닥을 위로 펴 물결을 띄움
m = Motion("sentinel_tides", 24, "나인 타이즈 — 몸을 돌려 왼손으로 발밑의 물결을 끌어올려 몸 앞으로 크게 한 바퀴 휘돌린 뒤, 왼손바닥을 위로 펴 아홉 물결을 띄움",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.4, TD_DRAW, "out3")
keyp(m, 6.4, TD_SWEEP, "inout")
m.arc(6.4, "la", (0.0, -0.3, 1))
keyp(m, 10.0, P(TD_DRAW, body=[2, -16, 0], head=[-10, 12]), "inout")
m.arc(10.0, "la", (-0.3, 0.2, 1))
keyp(m, 13.6, TD_LIFT, "out3")
keyp(m, 18.0, P(TD_LIFT, body=[-6, 0, 0], head=[-14, 0]))
keyp(m, 23.6, READY_SN, "inout")
fpw(m, 2.4, FPS_GUARD, "out3", dp=(0.15, -0.1, 0.1))
fpw(m, 18.0, FPS_GUARD, dp=(0.15, -0.1, 0.1))
fpw(m, 23.6, FPW_LOW)
fpl(m, 2.4, nudge(FP_DOWN, (0.1, -0.1, 0.05)), "out3")
fpl(m, 6.4, FP_PUSH_OUT, "inout")
fpl(m, 10.0, nudge(FP_PALM, (0.1, -0.1, 0.0)), "inout")
fpl(m, 13.6, FP_PALM_UP, "out3")
fpl(m, 18.0, FP_PALM_UP)
fpl(m, 22.6, FP_LOW)
use_skill(m, "sentinel_tides")

# 클라리온 콜 (효과: 0 ~ 10 황금 뿔피리를 듦 → 10 · 20 · 30 붊 (10 이 침 — 위험한 아군에 빛 기둥) → 40 무지개 다리를 타고 날아감 → 46 :land → 50 내려앉음)
HORN_LIFT = P(body=[-6, 0, 0], head=[-14, 0], ra=shield([0.35, -0.35, 0.86], 60, (0.1, 1, 0.1), (0.2, 0, 1)), la=bent([-0.12, 0.3, 0.55], 140, (0.2, 1, 0.3)),
              **LEGS_WIDE)  # 왼손으로 뿔피리를 입가로 들어 올림
HORN_BLOW = P(body=[-22, 0, 0], head=[-36, 0], ra=shield([0.45, -0.25, 0.86], 50, (0.1, 1, 0.1), (0.3, 0, 1)), la=bent([-0.06, 0.5, 0.7], 128, (0.2, 1, 0.4)),
              rl=[4, 0, 10], ll=[-6, 0, -10], rk=8, lk=8, root=[0.04, 0.0])  # 몸을 젖혀 하늘로 뿔피리를 붊
FLY_CROUCH = P(body=[26, 0, 0], head=[6, 0], ra=shield([0.15, -0.3, 0.94], 50, (0, 1, 0.1), (0, 0.1, 1)), la=arm([-0.45, -0.75, -0.3], 15), rl=[-36, 0, 8],
               ll=[-36, 0, -8], rk=76, lk=76, root=[-0.34, 0.0])  # 깊이 웅크림 (날아갈 힘을 모음)
FLY = P(body=[12, 0, 0], head=[-2, 0], ra=shield([0.1, -0.2, 0.97], 40, (0, 1, 0.1), (0, 0.15, 1)), la=arm([-0.55, 0.25, -0.75], 10), rl=[-30, 0, 6], ll=[-10, 0, -6],
          rk=56, lk=30)  # 방패를 앞에 세우고 왼팔을 뒤로 뻗어 날아감
m = Motion("sentinel_ult", 46, "클라리온 콜 — 왼손으로 황금 뿔피리를 입가로 들어 몸을 젖혀 하늘로 세 번 불고, 깊이 웅크렸다가 방패를 앞에 세우고 왼팔을 뒤로 뻗어 "
           "무지개 다리를 타고 날아감", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 3.0, HORN_LIFT, "out3")
keyp(m, 7.6, P(HORN_LIFT, body=[-8, 0, 0], head=[-18, 0]))
for s in (10, 20, 30):
    keyp(m, s, HORN_BLOW, "out3")
    keyp(m, s + 4.0, P(HORN_BLOW, body=[-18, 0, 0], head=[-30, 0]))
    keyp(m, s + 7.6, P(HORN_LIFT, body=[-10, 0, 0], head=[-20, 0]), "inout")
keyp(m, 39.0, FLY_CROUCH, "out3")
keyp(m, 41.0, FLY, "out3")
keyp(m, 46.0, P(FLY, body=[16, 0, 0]))
fpw(m, 3.0, FPS_GUARD, "out3", dp=(0.2, -0.15, 0.1))
fpw(m, 37.6, FPS_GUARD, dp=(0.2, -0.15, 0.1))
fpw(m, 39.0, FPS_GUARD, "out3", dp=(0.0, -0.15, 0.0))
fpw(m, 41.0, FPS_FRONT, "out3")
fpw(m, 46.0, FPS_FRONT)
fpl(m, 3.0, FPL_HORN, "out3")
fpl(m, 37.6, FPL_HORN)
fpl(m, 39.0, FP_LOW, "out3")
fpl(m, 46.0, FP_LOW)
impacts(m, 10.0)
use_skill(m, "sentinel_ult")

# 클라리온 콜 :land (46 — 내려앉음 50 = 이 동작의 4): 떨어지며 방패를 머리 위로 들었다가 무릎을 꿇으며 방패를 땅에 내리꽂고, 일어서며 방패와 왼 주먹을 하늘로 치켜듦
UL_DROP = P(body=[-10, 0, 0], head=[-20, 0], ra=shield([0.12, 0.95, 0.25], 30, (0, 1, 0), (0, 0.3, 1)), la=arm([-0.6, 0.5, 0.35], 15), rl=[-30, 0, 6], ll=[-12, 0, -6],
            rk=50, lk=30)  # 떨어지며 방패를 머리 위로
UL_PLANT = P(body=[34, 0, 0], head=[2, 0], ra=shield([0.06, -0.62, 0.78], 18, (0, -0.3, 1), (0, 0.6, 0.8)), la=arm([-0.3, -0.85, 0.42], 8), rl=[6, 0, 6], rk=94,
             ll=[-84, 0, -6], lk=88, root=[-0.42, 0.0])  # 무릎을 꿇으며 방패를 땅에 내리꽂음
UL_RISE = P(body=[-12, 0, 0], head=[-28, 0], ra=shield([0.25, 0.9, 0.3], 20, (0, 1, 0), (0, 0.3, 1)), la=bent([-0.2, 0.95, 0.12], 25, (-0.2, 1, 0.3)), **LEGS_WIDE)
m = Motion("sentinel_ult_land", 20, "클라리온 콜 끝 — 떨어지며 방패를 머리 위로 들었다가 무릎을 꿇으며 방패를 땅에 내리꽂아 무지개를 터뜨리고, 일어서며 방패와 왼 주먹을 "
           "하늘로 치켜듦", fade=(0.0, 0.1))
off_bare(m)
first(m, P(FLY, body=[16, 0, 0]), FPS_FRONT)
keyp(m, 2.4, UL_DROP, "out3")
keyp(m, 4.0, UL_PLANT, "in")
keyp(m, 8.6, P(UL_PLANT, body=[30, 0, 0], root=[-0.4, 0.0]))
keyp(m, 12.6, UL_RISE, "out3")
keyp(m, 19.6, READY_SN, "inout")
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 4.0, FPW_DOWN, "in")
fpw(m, 8.6, FPW_DOWN)
fpw(m, 12.6, FPW_SKY, "out3")
fpw(m, 19.6, FPW_LOW)
fpl(m, 0.0, FP_LOW)
fpl(m, 2.4, FP_SPREAD_UP, "out3")
fpl(m, 4.0, FP_GROUND, "in")
fpl(m, 8.6, FP_GROUND)
fpl(m, 12.6, FP_RAISE, "out3")
fpl(m, 18.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "sentinel_ult:land")


# ------------------------------------------------------------ 어벤저 (방패 — 불씨 주황 · 재)

# 리프라이절 (효과: 0 ~ 6 방패에 불씨가 모임 → 6 앞 부채꼴의 적을 후려침 (앙갚음 30%))
RE_WIND = P(body=[6, 48, 0], head=[-4, -38], ra=shield([0.72, -0.3, -0.5], 50, (0, 1, 0), (0.7, 0, -0.5)), la=arm([-0.5, -0.2, 0.75], 15), rl=[12, 0, 6], ll=[-16, 0, -6],
            rk=28, lk=24, root=[-0.12, -0.05])  # 몸을 오른쪽으로 크게 비틀어 방패를 오른쪽 뒤로 당김 (왼손은 앞으로)
RE_BASH = P(body=[16, -42, 0], head=[-8, 32], ra=shield([-0.55, -0.05, 0.84], 15, (0, 1, 0), (-0.8, 0, 0.6)), la=arm([-0.6, -0.45, -0.4], 20), rl=[26, 0, 4], ll=[-38, 0, -4],
            rk=20, lk=46, root=[-0.24, 0.3])  # 내디디며 방패를 왼쪽 앞으로 크게 후려침
m = Motion("avenger_reprisal", 14, "리프라이절 — 몸을 오른쪽으로 크게 비틀어 방패를 오른쪽 뒤로 당겼다가, 내디디며 방패를 왼쪽 앞으로 크게 후려침", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, RE_WIND, "out3")
keyp(m, 4.6, P(RE_WIND, body=[6, 52, 0], head=[-4, -42], root=[-0.14, -0.06]))
keyp(m, 6.0, RE_BASH, "in")
m.arc(6.0, "ra", (0.3, 0.1, 1))
keyp(m, 9.0, P(RE_BASH, body=[14, -46, 0], root=[-0.22, 0.28]))
keyp(m, 13.6, READY_AV, "inout")
fpw(m, 1.6, FPS_BACK, "out3")
fpw(m, 4.6, FPS_BACK, dp=(0.05, -0.02, 0.05))
fpw(m, 6.0, FPS_LEFT, "in")
fpw(m, 9.0, FPS_LEFT, dp=(-0.05, -0.05, 0.0))
fpw(m, 13.6, FPW_LOW)
fpl(m, 1.6, FP_PALM, "out3")
fpl(m, 4.6, FP_PALM)
fpl(m, 6.0, FP_LOW, "in")
fpl(m, 12.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 6.0)
use_skill(m, "avenger_reprisal")

# 그러지 차지 (효과: 0 나를 가장 많이 때린 적 머리 위 과녁 → 2 ~ 8 불씨를 끌며 돌진 → 8 방패로 들이받음)
GR_LOWER = P(body=[28, 0, 0], head=[20, 0], ra=shield([0.15, 0.0, 0.98], 30, (0, 1, 0.1), (0, 0.1, 1)), la=bent([-0.3, -0.4, 0.5], 90, (0.2, 1, 0.3)), rl=[16, 0, 4],
             ll=[-24, 0, -4], rk=32, lk=42, root=[-0.2, 0.1])  # 고개를 숙이고 방패를 어깨 앞에 (황소처럼 들이받을 준비)
GR_RUN = P(GR_LOWER, body=[34, 0, 0], rl=[-46, 0, 2], ll=[36, 0, -2], rk=62, lk=24, root=[-0.12, 0.32])
GR_RUN2 = P(GR_RUN, rl=[36, 0, 2], ll=[-46, 0, -2], rk=24, lk=62)
GR_RAM = P(body=[36, -8, 0], head=[24, 4], ra=shield([0.1, 0.05, 0.99], 10, (0, 1, 0), (0, 0, 1)), la=arm([-0.4, -0.5, -0.4], 15), rl=[30, 0, 4], ll=[-52, 0, -4],
           rk=24, lk=62, root=[-0.32, 0.46])  # 어깨와 방패로 들이받음
m = Motion("avenger_grudge", 14, "그러지 차지 — 고개를 숙이고 방패를 어깨 앞에 붙인 채 황소처럼 내달려 어깨와 방패로 들이받음", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.4, GR_LOWER, "out3")
keyp(m, 3.2, GR_RUN, "out3")
keyp(m, 4.8, GR_RUN2, "inout")
keyp(m, 6.4, GR_RUN, "inout")
keyp(m, 8.0, GR_RAM, "in")
keyp(m, 10.4, P(GR_RAM, body=[30, -6, 0], root=[-0.28, 0.4]))
keyp(m, 13.6, READY_AV, "inout")
fpw(m, 1.4, FPS_FRONT, "out3", dp=(0.05, -0.08, 0.05))
fpw(m, 6.4, FPS_FRONT, dp=(0.05, -0.1, 0.0))
fpw(m, 8.0, FPS_THRUST, "in")
fpw(m, 10.4, FPS_THRUST)
fpw(m, 13.6, FPW_LOW)
fpl(m, 1.4, FP_GUARD, "out3")
fpl(m, 12.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 8.0)
use_skill(m, "avenger_grudge")

# 레크닝 (효과: 0 ~ 12 몸의 불씨가 방패로 빨려 듦 · 발밑 불씨 기둥 → 12 앞 부채꼴로 내지름 (앙갚음 전부))
RK_COIL = P(body=[22, 42, 0], head=[6, -36], ra=shield([0.55, -0.55, -0.4], 70, (0, 1, 0), (0.6, 0, -0.6)), la=arm([-0.25, -0.05, 0.97], 4), rl=[-30, 0, 10],
            ll=[-24, 0, -10], rk=72, lk=62, root=[-0.32, -0.05])  # 깊이 웅크리며 몸을 비틀어 방패를 허리 뒤로 끌어당기고 왼손으로 겨눔
RK_THRUST = P(body=[24, -22, 0], head=[-4, 18], ra=shield([0.0, 0.05, 1.0], 0, (0, 1, 0), (0, 0, 1)), la=L(A_PULL), rl=[36, 0, 4], ll=[-52, 0, -4], rk=22, lk=62,
              root=[-0.36, 0.52])  # 크게 내디디며 방패를 앞으로 곧게 내지름 (왼 주먹은 허리 뒤로)
m = Motion("avenger_reckoning", 22, "레크닝 — 깊이 웅크리며 몸을 비틀어 방패를 허리 뒤로 끌어당기고 왼손으로 겨눈 채 불씨를 모았다가, 크게 내디디며 방패를 앞으로 곧게 "
           "내질러 앙갚음을 터뜨림", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, RK_COIL, "out3")
for i, tk in enumerate((4.4, 6.8, 9.2)):
    sgn = 1 if i % 2 == 0 else -1
    keyp(m, tk, P(RK_COIL, body=[22 + i, 44 + i * 2, sgn * 1.5], root=[-0.33 - i * 0.01, -0.05]))
keyp(m, 10.8, P(RK_COIL, body=[24, 48, 0], head=[6, -40], root=[-0.35, -0.06]), "inout")
keyp(m, 12.0, RK_THRUST, "in")
keyp(m, 16.0, P(RK_THRUST, body=[22, -20, 0], root=[-0.34, 0.5]))
keyp(m, 21.6, READY_AV, "inout")
fpw(m, 2.0, FPS_BACK, "out3")
fpw(m, 10.8, FPS_BACK, dp=(0.05, -0.04, 0.08))
fpw(m, 12.0, FPS_THRUST, "in")
fpw(m, 16.0, FPS_THRUST)
fpw(m, 21.6, FPW_LOW)
fpl(m, 2.0, FP_JAB, "out3")
fpl(m, 10.8, FP_JAB)
fpl(m, 12.0, FP_LOW, "in")
fpl(m, 20.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 12.0)
use_skill(m, "avenger_reckoning")

# 페인 이터 (태세 — 효과: 0 발밑 불씨 기둥 · 둘레 아군에게서 불씨 줄)
PN_BEAT = P(body=[-4, 0, 0], head=[-12, 0], ra=shield([0.55, -0.55, 0.5], 40, (0.1, 1, 0.2), (0.5, 0, 0.8)), la=L(A_CHEST), **LEGS_WIDE)  # 왼 주먹으로 가슴을 침
PN_AWAY = P(PN_BEAT, la=bent([-0.45, -0.3, 0.55], 100, (0.2, 1, 0.3)))  # 주먹을 떼어 다시 칠 준비
PN_ROAR = P(body=[-20, 0, 0], head=[-32, 0], ra=shield([0.88, 0.2, 0.3], 10, (0, 1, 0), (1, 0, 0.3)), la=arm([-0.88, 0.25, 0.3], 6), rl=[0, 0, 14], ll=[0, 0, -14],
            rk=10, lk=10, root=[0.02, 0.0])  # 두 팔을 활짝 펼치고 몸을 젖혀 포효 ("모두 내게로")
m = Motion("avenger_pain", 24, "페인 이터 — 왼 주먹으로 가슴을 세 번 치고, 두 팔을 활짝 펼친 채 몸을 젖혀 포효하며 아군의 아픔을 끌어당김", fade=(0.03, 0.1))
off_bare(m)
for tk in (1.6, 4.4, 7.2):
    keyp(m, tk - 1.2, PN_AWAY, "out3")
    keyp(m, tk, PN_BEAT, "in")
keyp(m, 11.0, PN_ROAR, "out3")
keyp(m, 18.0, P(PN_ROAR, body=[-16, 0, 0], head=[-26, 0]))
keyp(m, 23.6, READY_AV, "inout")
fpw(m, 1.6, FPS_GUARD, "out3", dp=(0.25, -0.15, 0.15))
fpw(m, 11.0, FPW_UP, "out3", dp=(0.3, -0.2, 0.0))
fpw(m, 18.0, FPW_UP, dp=(0.3, -0.2, 0.0))
fpw(m, 23.6, FPW_LOW)
for tk in (1.6, 4.4, 7.2):
    fpl(m, tk - 1.2, nudge(FP_CROSS, (0.0, -0.05, 0.1)), "out3")
    fpl(m, tk, nudge(FP_CROSS, (0.05, -0.1, 0.15)), "in")
fpl(m, 11.0, FP_SPREAD_UP, "out3")
fpl(m, 18.0, FP_SPREAD_UP)
fpl(m, 22.6, FP_LOW)
m.set(softLegs=False)
use_skill(m, "avenger_pain")

# 조브레이커 (효과: 0 ~ 6 달려듦 → 6 붙잡음 → 6 ~ 26 끌어안고 솟구침 → 22 :slam → 26 내리꽂음)
JB_LUNGE = P(body=[26, -14, 0], head=[8, 10], ra=shield([0.45, -0.3, 0.84], 40, (0.1, 1, 0.1), (0.3, 0, 1)), la=arm([-0.08, 0.0, 1.0], 2), rl=[30, 0, 4], ll=[-46, 0, -4],
             rk=24, lk=52, root=[-0.28, 0.42])  # 왼손을 앞으로 뻗어 달려듦
JB_GRAB = P(body=[12, 0, 0], head=[-6, 0], ra=shield([0.3, 0.35, 0.88], 60, (0, 1, 0.1), (0.2, 0, 1)), la=bent([-0.05, 0.05, 0.92], 100, (0.3, 1, 0.2)), **LEGS_FIGHT)  # 왼팔로 그 적을 끌어안음
JB_AIR = P(body=[-18, 0, 0], head=[-26, 0], ra=shield([0.2, 0.9, 0.35], 20, (0, 1, 0), (0, 0.3, 1)), la=bent([-0.05, 0.3, 0.85], 110, (0.3, 1, 0.2)),
           rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82)  # 끌어안은 채 몸을 젖혀 방패를 머리 위로
m = Motion("avenger_ult", 22, "조브레이커 — 왼손을 앞으로 뻗어 달려들어 그 적을 왼팔로 끌어안고, 몸을 젖혀 방패를 머리 위로 치켜든 채 하늘 높이 솟구침", fade=(0.03, 0.0))
off_bare(m)
keyp(m, 2.4, JB_LUNGE, "out3")
keyp(m, 6.0, JB_GRAB, "in")
keyp(m, 9.6, JB_AIR, "out3")
keyp(m, 16.0, P(JB_AIR, body=[-22, 0, 0], head=[-30, 0]))
keyp(m, 22.0, P(JB_AIR, body=[-24, 0, 0], head=[-32, 0]))
fpw(m, 2.4, FPS_GUARD, "out3", dp=(0.25, -0.1, 0.05))
fpw(m, 6.0, FPS_GUARD, "in", dp=(0.2, 0.05, 0.0))
fpw(m, 9.6, FPW_SKY, "out3")
fpw(m, 22.0, FPW_SKY)
fpl(m, 2.4, FPL_GRAB, "out3")
fpl(m, 6.0, nudge(FP_CROSS, (0.05, 0.0, -0.15)), "in")
fpl(m, 22.0, nudge(FP_CROSS, (0.05, 0.05, -0.1)))
impacts(m, 6.0)
use_skill(m, "avenger_ult")

# 조브레이커 :slam (22 — 내리꽂음 26 = 이 동작의 4): 공중에서 몸을 앞으로 접으며 방패를 머리 위에서 앞으로 넘겨, 무릎을 꿇으며 붙잡은 적째 땅에 내리꽂고, 일어서며
# 두 팔을 활짝 펼쳐 포효함 (늑대의 턱이 찢어짐)
JS_FOLD = P(body=[24, 0, 0], head=[4, 0], ra=shield([0.1, 0.6, 0.8], 30, (0, 0.8, 0.6), (0, 0.6, 0.8)), la=bent([-0.05, 0.2, 0.92], 100, (0.3, 1, 0.2)),
            rl=[-30, 0, 6], ll=[-12, 0, -6], rk=50, lk=30)  # 몸을 앞으로 접으며 방패를 앞으로 넘김
JS_SLAM = P(body=[40, 0, 0], head=[0, 0], ra=shield([0.05, -0.65, 0.76], 12, (0, -0.9, 0.42), (0, 0.42, 0.9)), la=arm([-0.05, -0.7, 0.7], 10), rl=[6, 0, 6], rk=94,
            ll=[-84, 0, -6], lk=88, root=[-0.44, 0.0])  # 무릎을 꿇으며 방패로 땅에 내리꽂음
JS_ROAR = P(PN_ROAR, body=[-18, 0, 0], head=[-30, 0])
m = Motion("avenger_ult_slam", 20, "조브레이커 끝 — 공중에서 몸을 앞으로 접으며 방패를 앞으로 넘겨 무릎을 꿇으며 붙잡은 적째 땅에 내리꽂고, 일어서며 두 팔을 활짝 펼쳐 "
           "포효함", fade=(0.0, 0.1))
off_bare(m)
first(m, P(JB_AIR, body=[-24, 0, 0], head=[-32, 0]), FPW_SKY)
keyp(m, 2.4, JS_FOLD, "out3")
keyp(m, 4.0, JS_SLAM, "in")
keyp(m, 8.6, P(JS_SLAM, body=[36, 0, 0], root=[-0.42, 0.0]))
keyp(m, 12.6, JS_ROAR, "out3")
keyp(m, 19.6, READY_AV, "inout")
fpw(m, 2.4, FPW_UP, "out3", dp=(0.0, 0.0, -0.15))
fpw(m, 4.0, FPW_DOWN, "in")
fpw(m, 8.6, FPW_DOWN)
fpw(m, 12.6, FPW_UP, "out3", dp=(0.3, -0.2, 0.0))
fpw(m, 19.6, FPW_LOW)
fpl(m, 0.0, nudge(FP_CROSS, (0.05, 0.05, -0.1)))
fpl(m, 2.4, nudge(FP_CROSS, (0.05, 0.0, -0.2)), "out3")
fpl(m, 4.0, FP_GROUND, "in")
fpl(m, 8.6, FP_GROUND)
fpl(m, 12.6, FP_SPREAD_UP, "out3")
fpl(m, 18.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "avenger_ult:slam")
