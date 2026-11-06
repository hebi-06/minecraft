"""0.51.0 2차 직업 스킬 동작 — 성기사 2차 (2026-11-06 사용자 "다음 작업"): 각성 썬더로드 · 반전 타이런트 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
ThunderlordSkills · TyrantSkills). 둘 다 망치 (오른손 — 1차 성기사 job_motions_c 꼴, 왼손은 맨손). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄
(JobKit.motionLater — 썬더스텝의 내리꽂힘 :land, 스톰 스파이어의 내리침 :call, 던브레이커의 새벽 :dawn, 킹스 그립의 내리꽂음 :slam, 스톨른 썬더의 내리침 :slam).
단계 동작의 틱 0 열쇠는 앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게). 썬더로드는 크게 휩쓸고 · 하늘을 부르고 · 땅에 꽂는 동작, 타이런트는 한 손으로 크게 내려치고 ·
사슬을 당기고 · 왼손으로 움켜쥐는 거인 왕의 동작 (1차 · 다른 직업과 겹치지 않게)."""
import rune_motion_kit as K
from job_motions_d import BRACE, F_CROUCH, LUNGE_DEEP, S_IMPACT, first, impacts, strike_with
from rune_motion_kit import (A_CHAMBER, A_FLEX, A_PALM, A_PULL, A_SPREAD_DOWN, A_SPREAD_UP, FP_CHAMBER, FP_DOWN, FP_JAB, FP_LOW, FP_PALM, FP_PALM_UP,
                             FP_PUSH_OUT, FP_RAISE, FP_SPREAD_UP, FP_UPPER, FPL_GRIP2H, FPL_RAISE2H, FPW_BACK, FPW_DOWN, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP,
                             GRIP2H, LEGS_WIDE, RA_SKY, RAISE2H, W_READY, P, L, arm, bent, fpl, fpw, keyp, nudge, off_bare, strike, use_skill)

Motion = K.Motion

READY_T = P(W_READY, body=[8, 6, 0])  # 끝 자세 (다음 단계 동작의 틱 0)
READY_Y = P(W_READY, body=[10, 8, 0], rk=24, lk=20)


def trail(m, a, b):
    """무기 끝 자국 (a ~ b 틱)"""
    m.head.setdefault("trail", []).append([m.t(a), m.t(b)])
    m.set(tilt=30)


# ------------------------------------------------------------ 썬더로드 (망치 — 금빛 · 하늘빛 벼락)

# 아크 스매시 (효과: 0 ~ 6 망치를 오른쪽 뒤로 감음 — 번개가 감김 → 6 앞 120° 를 가로로 휩씀 → 16 맞은 적마다 번개가 갈라져 튐)
ARC_WIND = P(body=[8, 66, 0], head=[-6, -36], ra=arm([0.7, 0.42, -0.58], 58, [0.2, 0.8, -0.56]), la=arm([-0.5, -0.05, 0.86], 10),
             rl=[16, 0, 8], ll=[-30, 0, -8], rk=40, lk=50, root=[-0.26, -0.06])  # 망치를 오른쪽 뒤로 크게 감고 왼손을 앞으로 뻗어 겨눔 (무릎을 굽혀 버팀)
FORK_DRAW = P(body=[10, -40, 0], head=[-6, 20], ra=arm([-0.8, -0.3, 0.3], 20, [-0.5, -0.5, -0.7]), la=L(A_CHAMBER), root=[-0.24, 0.4], **LUNGE_DEEP)  # 왼 주먹을 허리로
FORK_PUSH = P(body=[14, 6, 0], head=[-8, 0], ra=arm([0.45, -0.6, 0.3], 30, [0.3, 0.3, 0.9]), la=L(A_PALM), root=[-0.22, 0.42], **LUNGE_DEEP)  # 왼손바닥을 앞으로
m = Motion("thunderlord_arc", 22, "아크 스매시 — 망치를 오른쪽 뒤로 크게 감아 무릎을 굽혀 버텼다가 크게 내디디며 앞을 가로로 크게 휩쓸고, 왼 주먹을 허리로 당겼다가 "
           "손바닥을 앞으로 내밀어 번개를 갈라 보냄", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, ARC_WIND, "out3")
keyp(m, 3.8, P(ARC_WIND, body=[10, 72, 0], root=[-0.3, -0.1]))
strike_with(m, "slash_h", 4.6, 6.0, 8.6, a=P(body=[10, 58, 0], head=[-6, -30], rl=[16, 0, 8], ll=[-30, 0, -8], rk=40, lk=50, root=[-0.28, -0.06]),
            b=P(body=[12, -56, 0], head=[-6, 26], root=[-0.3, 0.5], **LUNGE_DEEP))
keyp(m, 12.0, FORK_DRAW, "inout")
keyp(m, 14.6, P(FORK_DRAW, body=[12, -46, 0]))
keyp(m, 16.0, FORK_PUSH, "out3")
keyp(m, 18.4, P(FORK_PUSH, body=[12, 4, 0]))
keyp(m, 21.6, READY_T, "inout")
fpw(m, 1.2, FPW_BACK, "out3")
fpw(m, 12.0, FPW_LOW)
fpw(m, 21.6, FPW_LOW)
fpl(m, 1.2, FP_PALM, "out3")
fpl(m, 4.4, FP_LOW)
fpl(m, 12.0, FP_CHAMBER)
fpl(m, 14.6, FP_CHAMBER)
fpl(m, 16.0, nudge(FP_PALM, (0.0, 0.04, -0.05), (10, 0, 0)), "out3")
fpl(m, 20.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 6.0, 16.0)
use_skill(m, "thunderlord_arc")

# 썬더스텝 (효과: 0 ~ 3 웅크림 (발밑에 번개) → 3 몸이 번개가 되어 하늘로 사라짐 (보이지 않음 — 망치는 1틱부터 숨김) → 6 :land 조준한 곳에 벼락으로 내리꽂힘)
STEP_CROUCH = P(F_CROUCH, ra=arm([0.3, 0.88, -0.3], 30, [0.1, 0.85, -0.5]))  # 망치를 든 채 웅크림
STEP_UP = P(body=[-12, 0, 0], head=[-28, 0], ra=RA_SKY, la=arm([-0.25, -0.85, -0.3], 10), rl=[-6, 0, 4], rk=8, ll=[6, 0, -4], lk=14, root=[0.0, 0.12])  # 솟구침
STEP_DIVE = P(body=[20, 0, 0], head=[10, 0], ra=arm([0.2, 0.9, -0.35], 40, [0.05, 0.3, -0.95]), la=arm([-0.4, -0.3, 0.6], 20), rl=[-30, 0, 8], rk=60,
              ll=[-10, 0, -8], lk=30, root=[0.0, 0.1])  # 망치를 머리 위 뒤로 들고 내리꽂힘
LAND = P(body=[36, 0, 0], head=[-16, 0], ra=arm([0.05, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=L(A_SPREAD_DOWN), rl=[2, 0, 4], rk=92, ll=[-82, 0, -4], lk=88,
         root=[-0.38, 0.24])  # 한 무릎을 꿇고 망치를 앞 땅에 내리꽂음 · 왼팔은 아래 바깥으로
m = Motion("thunderlord_step", 8, "썬더스텝 — 망치를 든 채 웅크렸다가 번개가 되어 하늘로 솟구치고 (보이지 않음), 망치를 머리 위로 들고 내리꽂힘", fade=(0.02, 0.06))
m.set(hands={"main": "thrown", "off": "fist"}, hide=[m.t(1.0), m.t(8.0)])  # 3틱에 보이지 않게 됨 (서버가 웅크린 뒤 투명 — 투명 효과가 동작보다 1 ~ 2틱 먼저 닿음) — 그 전에 망치를 숨김
keyp(m, 0.8, STEP_CROUCH, "out3")
keyp(m, 2.8, STEP_UP, "out")
keyp(m, 4.8, STEP_DIVE, "inout")
keyp(m, 6.0, LAND, "in")
keyp(m, 8.0, LAND)
fpw(m, 0.8, FPW_UP, "out3")
fpw(m, 2.8, FPW_SKY, "out")
fpw(m, 6.0, FPW_DOWN, "in")
fpl(m, 0.8, FP_LOW)
fpl(m, 2.8, FP_RAISE, "out")
fpl(m, 6.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
m.set(softLegs=False)
use_skill(m, "thunderlord_step")

# 썬더스텝 :land (6 — 효과 6 = 이 동작의 0): 벼락과 함께 한 무릎으로 내려앉아 망치를 땅에 꽂은 채 버텼다가, 일어서며 망치를 치켜들고 왼팔을 펼쳐 아군을 감쌈
STEP_RISE = P(body=[-8, 0, 0], head=[-14, 0], ra=arm([0.3, 0.88, 0.2], 30, [0.1, 1, -0.1]), la=L(A_SPREAD_UP), **LEGS_WIDE)
m = Motion("thunderlord_step_land", 16, "썬더스텝 끝 — 벼락과 함께 한 무릎으로 내려앉아 망치를 앞 땅에 꽂은 채 버텼다가, 일어서며 망치를 치켜들고 왼팔을 위로 펼쳐 아군을 "
           "감쌈", fade=(0.0, 0.1))
off_bare(m)
first(m, LAND, FPW_DOWN)
keyp(m, 3.6, P(LAND, body=[32, 0, 0], root=[-0.36, 0.22]))
keyp(m, 7.6, STEP_RISE, "out3")
keyp(m, 11.0, P(STEP_RISE, body=[-6, 0, 0], head=[-12, 0]))
keyp(m, 15.4, READY_T, "inout")
fpw(m, 3.6, FPW_DOWN)
fpw(m, 7.6, FPW_UP, "out3")
fpw(m, 11.0, FPW_UP)
fpw(m, 15.4, FPW_LOW)
fpl(m, 0.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)))
fpl(m, 3.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)))
fpl(m, 7.6, FP_PALM_UP, "out3")
fpl(m, 14.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 0.0)
use_skill(m, "thunderlord_step:land")

# 스톰 스파이어 (효과: 0 ~ 16 겨눈 곳 하늘에 먹구름 · 땅의 고리가 조여 듦 → 13 :call → 16 번개 기둥 → 26 땅의 번개 물결)
SPIRE_CALL = P(body=[-12, -8, 0], head=[-24, 6], ra=RA_SKY, la=arm([-0.25, 0.3, 0.92], 2, [0, 0.3, 1]), **LEGS_WIDE)  # 망치를 하늘로 · 왼손으로 그곳을 가리킴
SPIRE_HOLD = P(SPIRE_CALL, body=[-16, -8, 0], head=[-28, 6], rk=16, lk=16)
m = Motion("thunderlord_spire", 14, "스톰 스파이어 — 망치를 하늘로 곧게 치켜들고 왼손으로 겨눈 곳을 가리켜 먹구름을 부름 (몸이 떨리며 점점 젖혀짐)", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 2.6, SPIRE_CALL, "out3")
for i, t in enumerate((6.0, 9.0, 11.6)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(SPIRE_CALL, body=[-13 - i, -8, s * 1.2], head=[-25 - i, 6], rk=12 + 2 * i, lk=12 + 2 * i), "inout")
keyp(m, 13.0, SPIRE_HOLD)
keyp(m, 14.0, SPIRE_HOLD)
fpw(m, 2.6, FPW_SKY, "out3")
fpw(m, 13.0, FPW_SKY)
fpl(m, 2.6, nudge(FP_PALM, (0.0, 0.12, 0.0), (28, 0, 0)), "out3")
fpl(m, 13.0, nudge(FP_PALM, (0.0, 0.12, 0.0), (28, 0, 0)))
use_skill(m, "thunderlord_spire")

# 스톰 스파이어 :call (13 — 효과 16 = 이 동작의 3 · 물결 26 = 13): 하늘로 든 망치를 그곳으로 내리쳐 번개 기둥을 떨어뜨리고, 왼손을 아래 바깥으로 펼쳐 물결을 퍼뜨림
SPIRE_POINT = P(body=[16, -4, 0], head=[-6, 0], ra=arm([0.15, -0.3, 0.94], 6, [0.05, -0.35, 0.94]), la=L(A_CHAMBER), rl=[18, 0, 6], ll=[-26, 0, -6], rk=22,
                lk=34, root=[-0.16, 0.24])  # 망치 머리로 그곳을 겨눈 채 왼 주먹을 허리로
SPIRE_SPREAD = P(SPIRE_POINT, body=[10, 0, 0], head=[-10, 0], la=L(A_SPREAD_DOWN), root=[-0.12, 0.2])  # 왼손을 아래 바깥으로 펼침
m = Motion("thunderlord_spire_call", 18, "스톰 스파이어 끝 — 하늘로 든 망치를 겨눈 곳으로 힘껏 내리쳐 번개 기둥을 떨어뜨리고, 망치 머리로 그곳을 겨눈 채 왼 주먹을 당겼다가 "
           "아래 바깥으로 펼쳐 번개 물결을 퍼뜨림", fade=(0.0, 0.1))
off_bare(m)
first(m, SPIRE_HOLD, FPW_SKY)
strike(m, "smite", 1.2, 3.0)
keyp(m, 7.0, SPIRE_POINT, "inout")
keyp(m, 10.8, P(SPIRE_POINT, body=[14, -6, 0]))
keyp(m, 13.0, SPIRE_SPREAD, "out3")
keyp(m, 15.0, P(SPIRE_SPREAD, body=[8, 0, 0]))
keyp(m, 17.6, READY_T, "inout")
fpw(m, 7.0, dict(p=(-0.42, 0.0, -0.45), blade=(-0.1, -0.3, 1), edge=(0, -1, 0)))
fpw(m, 17.6, FPW_LOW)
fpl(m, 0.0, nudge(FP_PALM, (0.0, 0.12, 0.0), (28, 0, 0)))
fpl(m, 2.0, FP_LOW)
fpl(m, 10.8, FP_CHAMBER)
fpl(m, 13.0, FP_PUSH_OUT, "out3")
fpl(m, 17.0, FP_LOW)
impacts(m, 3.0, 13.0)
use_skill(m, "thunderlord_spire:call")

# 썬더헤드 (태세 — 효과: 0 내게 벼락 · 머리 위 먹구름): 벼락을 받아 낮게 버텼다가 망치와 왼손을 하늘로 V 자로 펼쳐 폭풍을 맞음
HEAD_BRACE = P(body=[16, 0, 0], head=[-6, 0], ra=arm([0.4, -0.5, 0.5], 40, [0.2, 0.6, 0.75]), la=L(A_CHAMBER), root=[-0.2, 0.0], **BRACE)
HEAD_Y = P(body=[-14, 0, 0], head=[-34, 0], ra=arm([0.6, 0.78, 0.15], 8, [0.35, 0.93, 0.05]), la=arm([-0.6, 0.78, 0.15], 8), **LEGS_WIDE)
m = Motion("thunderlord_head", 20, "썬더헤드 — 내리꽂힌 벼락을 받아 무릎을 굽혀 낮게 버텼다가, 망치와 왼손을 하늘로 V 자로 펼쳐 머리 위 먹구름을 맞이함", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, HEAD_BRACE, "out3")
keyp(m, 2.6, P(HEAD_BRACE, body=[18, 0, 0], root=[-0.22, 0.0]))
keyp(m, 5.0, HEAD_Y, "out3")
keyp(m, 12.0, P(HEAD_Y, body=[-16, 0, 0], head=[-36, 0]))
keyp(m, 19.4, READY_T, "inout")
fpw(m, 1.2, FPW_LOW, "out3")
fpw(m, 5.0, FPW_SKY, "out3")
fpw(m, 12.0, FPW_SKY)
fpw(m, 19.4, FPW_LOW)
fpl(m, 1.2, FP_CHAMBER, "out3")
fpl(m, 5.0, FP_SPREAD_UP, "out3")
fpl(m, 12.0, FP_SPREAD_UP)
fpl(m, 17.0, FP_LOW)
use_skill(m, "thunderlord_head")

# 던브레이커 (효과: 0 ~ 10 두 손으로 망치를 하늘로 → 10 번개 사슬이 내리꽂힘 → 26 :dawn → 30 망치를 땅에 꽂음 · 새벽 → 46 돌이 깨짐)
ULT_RAISE = P(RAISE2H, head=[-30, 0], body=[-12, 0, 0])
ULT_HOLD = P(RAISE2H, head=[-34, 0], body=[-18, 0, 1.2], rk=22, lk=22, root=[-0.08, 0.0])
m = Motion("thunderlord_ult", 28, "던브레이커 — 두 손을 모아 망치를 세웠다가 머리 위로 곧게 치켜들고, 번개 사슬이 내리꽂힐 때 몸이 크게 젖혀진 채 폭풍을 붙들고 버팀 "
           "(몸이 떨림)", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 1.6, GRIP2H, "out3")
keyp(m, 4.6, ULT_RAISE, "out3")
keyp(m, 9.0, P(ULT_RAISE, body=[-14, 0, 0], head=[-32, 0], rk=16, lk=16))
keyp(m, 10.6, P(ULT_RAISE, body=[-22, 0, 0], head=[-38, 0], rk=24, lk=24, root=[-0.1, -0.04]), "out3")
for i, t in enumerate((14.0, 17.0, 20.0, 23.0)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(ULT_HOLD, body=[-18 + s, 0, s * 1.2]), "inout")
keyp(m, 26.0, ULT_HOLD)
keyp(m, 28.0, ULT_HOLD)
fpw(m, 1.6, FPW_UP, "out3", dp=(-0.2, -0.15, 0))
fpw(m, 4.6, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 10.6, FPW_UP, "out3", dp=(0.14, -0.04, 0.05))
fpw(m, 28.0, FPW_UP, dp=(0.12, -0.06, 0))
fpl(m, 1.6, FPL_GRIP2H, "out3")
fpl(m, 4.6, FPL_RAISE2H, "out3")
fpl(m, 28.0, FPL_RAISE2H)
impacts(m, 10.0)
use_skill(m, "thunderlord_ult")

# 던브레이커 :dawn (26 — 새벽 30 = 이 동작의 4 · 깨짐 46 = 20): 치켜든 망치를 두 손으로 땅에 꽂으며 한 무릎을 꿇고 새벽을 맞았다가, 돌이 깨질 때 망치를 뽑아
# 하늘로 치켜듦
DAWN_KNEEL = P(S_IMPACT, body=[30, 0, 0], head=[-22, 0], root=[-0.36, 0.22])  # 꽂은 망치를 짚고 고개를 들어 새벽을 봄
DAWN_RISE = P(body=[-12, 0, 0], head=[-26, 0], ra=RA_SKY, la=L(A_SPREAD_UP), **LEGS_WIDE)  # 일어서며 망치를 뽑아 하늘로
m = Motion("thunderlord_ult_dawn", 26, "던브레이커 끝 — 치켜든 망치를 두 손으로 땅에 꽂으며 한 무릎을 꿇어 새벽을 부르고, 꽂은 망치를 짚은 채 고개를 들어 빛을 맞았다가, "
           "돌이 깨질 때 일어서며 망치를 뽑아 하늘로 치켜듦", fade=(0.0, 0.1))
off_bare(m)
first(m, ULT_HOLD, FPW_UP)
strike(m, "slam", 1.6, 4.0)
keyp(m, 4.0, P(S_IMPACT, body=[40, 0, 0], root=[-0.4, 0.24]), "in")
keyp(m, 8.0, DAWN_KNEEL, "inout")
keyp(m, 16.0, P(DAWN_KNEEL, head=[-26, 0], body=[28, 0, 0]))
keyp(m, 18.4, P(DAWN_KNEEL, body=[34, 0, 0], head=[-14, 0], root=[-0.4, 0.22]))
keyp(m, 20.0, DAWN_RISE, "out3")
keyp(m, 22.6, P(DAWN_RISE, body=[-10, 0, 0], head=[-22, 0]))
keyp(m, 25.6, READY_T, "inout")
fpw(m, 4.0, FPW_DOWN, "in")
fpw(m, 18.4, FPW_DOWN)
fpw(m, 20.0, FPW_SKY, "out3")
fpw(m, 25.6, FPW_LOW)
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 4.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 18.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 20.0, FP_SPREAD_UP, "out3")
fpl(m, 24.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0, 20.0)
use_skill(m, "thunderlord_ult:dawn")


# ------------------------------------------------------------ 타이런트 (망치 — 보랏빛 번개 · 핏빛)

# 필리지 (효과: 0 ~ 8 망치를 머리 위 뒤로 높이 치켜듦 — 보랏빛 번개가 모임 → 8 앞의 적 하나를 내려침)
PIL_RAISE = P(body=[-20, 14, 0], head=[-8, -10], ra=arm([0.3, 0.82, -0.48], 46, [0.12, 0.25, -0.96]), la=arm([-0.35, 0.4, 0.85], 8),
              rl=[-6, 0, 8], ll=[-18, 0, -8], rk=22, lk=34, root=[-0.06, -0.12])  # 한 손으로 망치를 머리 위 뒤로 · 왼손으로 적을 겨눔 · 몸을 젖힘
PIL_COIL = P(PIL_RAISE, body=[-26, 18, 0], head=[-10, -12], rk=30, lk=40, root=[-0.1, -0.16])
PIL_HIT = P(body=[40, -8, 0], head=[-20, 6], ra=arm([0.05, -0.5, 0.86], 6, [0.0, -0.78, 0.62]), la=arm([-0.55, -0.6, -0.4], 25),
            rl=[36, 0, 6], ll=[-50, 0, -4], rk=26, lk=64, root=[-0.4, 0.5])  # 크게 내디디며 앞의 적에게 내려침 (깊이 숙임)
m = Motion("tyrant_pillage", 16, "필리지 — 한 손으로 망치를 머리 위 뒤로 높이 치켜들고 왼손으로 적을 겨눈 채 몸을 크게 젖혔다가, 크게 내디디며 깊이 숙여 그 적을 "
           "내려침", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, PIL_RAISE, "out3")
keyp(m, 5.8, PIL_COIL)
keyp(m, 8.0, PIL_HIT, "in")
m.arc(8.0, "ra", (0.15, 0.8, 0.6))
keyp(m, 11.0, P(PIL_HIT, body=[36, -8, 0], root=[-0.36, 0.46]))
keyp(m, 15.4, READY_Y, "inout")
trail(m, 6.4, 8.4)
fpw(m, 1.6, FPW_SKY, "out3")
fpw(m, 5.8, FPW_SKY, dp=(0.05, 0.05, 0.1))
fpw(m, 8.0, FPW_DOWN, "in")
fpw(m, 11.0, FPW_DOWN)
fpw(m, 15.4, FPW_LOW)
fpl(m, 1.6, nudge(FP_PALM, (0.0, 0.1, 0.0), (25, 0, 0)), "out3")
fpl(m, 6.4, FP_LOW)
fpl(m, 15.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 8.0)
use_skill(m, "tyrant_pillage")

# 섀클 볼트 (효과: 0 왼손에서 사슬이 날아감 → 4 감음 → 4 ~ 10 사슬을 당겨 끌려감 → 10 어깨로 들이받음)
SH_THROW = P(body=[12, 16, 0], head=[-8, -8], ra=arm([0.45, -0.55, 0.4], 50, [0.2, 0.4, 0.9]), la=arm([-0.05, 0.12, 1], 0, [0, 0.12, 1]), rl=[20, 0, 6],
             ll=[-28, 0, -6], rk=18, lk=30, root=[-0.14, 0.18])  # 왼팔을 앞으로 내뻗어 사슬을 던짐
SH_YANK = P(body=[24, -34, 0], head=[-14, 18], ra=arm([0.55, -0.6, -0.35], 25, [0.35, -0.2, -0.9]), la=L(A_PULL), rl=[34, 0, 4], ll=[-40, 0, -4], rk=30,
            lk=56, root=[-0.3, 0.32])  # 사슬을 허리로 당기며 오른 어깨를 앞으로 · 망치는 오른쪽 뒤로 끌림
SH_RAM = P(body=[30, -46, 0], head=[-16, 24], ra=arm([0.55, -0.6, -0.35], 25, [0.35, -0.2, -0.9]), la=L(A_PULL), rl=[40, 0, 4], ll=[-50, 0, -4], rk=24,
           lk=64, root=[-0.36, 0.5])  # 오른 어깨로 들이받음
m = Motion("tyrant_shackle", 16, "섀클 볼트 — 왼팔을 앞으로 내뻗어 보랏빛 사슬을 던지고, 감기면 사슬을 허리로 홱 당기며 오른 어깨를 앞세워 끌려가 그 적을 어깨로 들이받음",
           fade=(0.02, 0.1))
off_bare(m)
keyp(m, 1.0, SH_THROW, "out3")
keyp(m, 3.4, P(SH_THROW, la=bent([-0.05, 0.12, 1], 40, (0, 1, 0.2))))
keyp(m, 4.6, SH_YANK, "out3")
keyp(m, 8.6, P(SH_YANK, body=[28, -40, 0], root=[-0.34, 0.4]))
keyp(m, 10.0, SH_RAM, "in")
keyp(m, 12.6, P(SH_RAM, body=[26, -40, 0], root=[-0.32, 0.46]))
keyp(m, 15.6, READY_Y, "inout")
fpw(m, 1.0, FPW_LOW)
fpw(m, 4.6, dict(p=(0.4, -0.3, 0.3), blade=(0.6, -0.2, -0.75)), "out3")
fpw(m, 12.6, dict(p=(0.4, -0.3, 0.3), blade=(0.6, -0.2, -0.75)))
fpw(m, 15.6, FPW_LOW)
fpl(m, 1.0, nudge(FP_PALM, (0.0, 0.05, -0.05), (10, 0, 0)), "out3")
fpl(m, 3.4, FP_JAB)
fpl(m, 4.6, FP_CHAMBER, "out3")
fpl(m, 12.6, FP_CHAMBER)
fpl(m, 15.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "tyrant_shackle")

# 킹스 그립 (효과: 0 ~ 6 거대한 손이 내려옴 → 6 움켜쥐어 들어 올림 → 10 · 20 · 30 쥐어짬 → 36 :slam → 40 내리꽂음)
GRIP_REACH = P(body=[8, 14, 0], head=[-8, -10], ra=arm([0.45, -0.6, 0.3], 60, [0.15, 0.6, 0.78]), la=arm([-0.1, 0.18, 1], 0, [0, 0.3, 1]), rl=[16, 0, 6],
               ll=[-22, 0, -6], rk=18, lk=26, root=[-0.12, 0.12])  # 왼손을 활짝 펴 적에게 뻗음 (왼 어깨가 앞)
GRIP_GRAB = P(GRIP_REACH, la=bent([-0.1, 0.3, 0.95], 50, (0, 1, 0.2)))  # 움켜쥠
GRIP_LIFT = P(body=[-6, 18, 0], head=[-18, -12], ra=arm([0.45, -0.6, 0.3], 60, [0.15, 0.6, 0.78]), la=bent([-0.1, 0.62, 0.78], 70, (0, 1, 0.3)),
              rl=[10, 0, 10], ll=[-12, 0, -10], rk=16, lk=18, root=[-0.06, 0.04])  # 쥔 주먹을 앞 위로 들어 올림
GRIP_SQUEEZE = P(GRIP_LIFT, body=[12, 24, 0], head=[-10, -14], la=bent([-0.16, 0.4, 0.62], 118, (0, 1, 0.3)), rk=32, lk=32, root=[-0.18, 0.02])  # 팔꿈치를 깊이 굽혀 주먹을 당기며 몸을 웅크려 쥐어짬
m = Motion("tyrant_grip", 38, "킹스 그립 — 왼손을 활짝 펴 적에게 뻗었다가 움켜쥐어 앞 위로 들어 올리고, 쥐어짤 때마다 팔꿈치를 굽혀 몸을 웅크리며 주먹을 비틂",
           fade=(0.03, 0.04))
off_bare(m)
keyp(m, 2.4, GRIP_REACH, "out3")
keyp(m, 5.0, P(GRIP_REACH, body=[10, 16, 0], la=arm([-0.12, 0.2, 1], 0, [0, 0.3, 1])))
keyp(m, 6.0, GRIP_GRAB, "in")
keyp(m, 9.0, GRIP_LIFT, "out3")
for t in (10, 20, 30):
    keyp(m, t + 1.0, GRIP_SQUEEZE, "out3")
    keyp(m, t + 5.0, GRIP_LIFT, "inout")
keyp(m, 36.0, GRIP_LIFT)
keyp(m, 38.0, GRIP_LIFT)
fpw(m, 2.4, FPW_LOW, "out3")
fpw(m, 38.0, FPW_LOW)
fpl(m, 2.4, FP_PALM, "out3")
fpl(m, 6.0, FP_JAB, "in")
fpl(m, 9.0, FP_UPPER, "out3")
for t in (10, 20, 30):
    fpl(m, t + 1.0, nudge(FP_UPPER, (0.0, -0.04, 0.06), (12, 12, 0)), "out3")
    fpl(m, t + 5.0, FP_UPPER, "inout")
fpl(m, 38.0, FP_UPPER)
impacts(m, 10.0, 20.0, 30.0)
use_skill(m, "tyrant_grip")

# 킹스 그립 :slam (36 — 효과 40 = 이 동작의 4): 쥔 주먹을 머리 위로 더 들었다가 몸을 숙이며 앞 땅으로 내리꽂음
GRIP_SLAM = P(body=[36, 10, 0], head=[-14, -6], ra=arm([0.5, -0.65, 0.2], 40, [0.2, 0.4, 0.9]), la=arm([-0.1, -0.6, 0.8], 10), rl=[30, 0, 6], ll=[-44, 0, -6],
              rk=30, lk=60, root=[-0.36, 0.3])
m = Motion("tyrant_grip_slam", 14, "킹스 그립 끝 — 쥔 주먹을 머리 위로 더 높이 들었다가 몸을 숙이며 앞 땅으로 내리꽂음", fade=(0.0, 0.1))
off_bare(m)
first(m, GRIP_LIFT, FPW_LOW)
keyp(m, 2.2, P(GRIP_LIFT, body=[-14, 20, 0], head=[-22, -12], la=bent([-0.1, 0.88, 0.45], 60, (0, 1, 0.3)), root=[-0.04, -0.06]), "out")
keyp(m, 4.0, GRIP_SLAM, "in")
keyp(m, 8.0, P(GRIP_SLAM, body=[30, 8, 0], root=[-0.3, 0.26]))
keyp(m, 13.4, READY_Y, "inout")
fpw(m, 13.4, FPW_LOW)
fpl(m, 0.0, FP_UPPER)
fpl(m, 2.2, nudge(FP_UPPER, (0.0, 0.12, 0.05), (22, 0, 0)), "out")
fpl(m, 4.0, FP_DOWN, "in")
fpl(m, 8.0, FP_DOWN)
fpl(m, 13.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "tyrant_grip:slam")

# 콜로서스 (태세 — 효과: 0 몸이 거대해짐 · 머리 위 번개 왕관): 웅크려 힘을 모았다가 가슴을 펴고 두 팔을 굽혀 들어 포효
COL_HUNCH = P(body=[26, 0, 0], head=[8, 0], ra=bent([0.5, -0.55, 0.4], 100, (0, 1, 0.3)), la=bent([-0.5, -0.55, 0.4], 100, (0, 1, 0.3)), rl=[-30, 0, 10],
              ll=[-30, 0, -10], rk=64, lk=64, root=[-0.28, 0.0])  # 웅크려 두 주먹을 몸 앞 아래로 모음
COL_ROAR = P(body=[-20, 0, 0], head=[-36, 0], ra=arm([0.85, 0.35, 0.1], 40, [0.3, 0.9, 0.2]), la=L(A_FLEX), **LEGS_WIDE)  # 가슴을 펴고 두 팔을 굽혀 들어 포효
m = Motion("tyrant_colossus", 22, "콜로서스 — 웅크려 두 주먹을 몸 앞에 모아 힘을 끌어올렸다가, 가슴을 펴고 두 팔을 굽혀 들어 하늘을 보며 포효함", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, COL_HUNCH, "out3")
keyp(m, 4.4, P(COL_HUNCH, body=[30, 0, 0], rk=70, lk=70))
keyp(m, 6.4, COL_ROAR, "out3")
keyp(m, 14.0, P(COL_ROAR, body=[-22, 0, 0], head=[-38, 0]))
keyp(m, 21.4, READY_Y, "inout")
fpw(m, 1.6, FPW_LOW, "out3")
fpw(m, 6.4, FPW_SPREAD, "out3")
fpw(m, 14.0, FPW_SPREAD)
fpw(m, 21.4, FPW_LOW)
fpl(m, 1.6, FP_CHAMBER, "out3")
fpl(m, 6.4, FP_SPREAD_UP, "out3")
fpl(m, 14.0, FP_SPREAD_UP)
fpl(m, 19.0, FP_LOW)
m.set(softLegs=False)
use_skill(m, "tyrant_colossus")

# 스톨른 썬더 (효과: 0 ~ 10 망치를 하늘로 → 10 · 20 · 30 둘레 적들의 힘을 빨아들임 (망치가 자라남) → 32 뛰어오름 → 39 :slam → 44 내려침)
TY_RAISE = P(body=[-14, 4, 0], head=[-30, 0], ra=RA_SKY, la=bent([-0.55, 0.3, 0.5], 110, (0.3, 1, 0)), **LEGS_WIDE)  # 망치를 하늘로 · 왼 주먹을 어깨 높이로 굽혀 듦
TY_CROUCH = P(F_CROUCH, ra=arm([0.3, 0.9, -0.3], 30, [0.05, 0.6, -0.8]), la=bent([-0.4, -0.5, 0.4], 60, (0, 1, 0.3)))  # 뛰기 전 웅크림 (망치는 위)
TY_AIR = P(body=[-20, 0, 0], head=[-14, 0], ra=arm([-0.1, 0.9, -0.42], 40, [0, 0.2, -0.98]), la=bent([0.1, 0.9, -0.4], 45, (-0.3, 0.3, -1)), rl=[-56, 0, 8],
           rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0.1])  # 두 손으로 망치를 머리 뒤로 · 무릎을 당김
m = Motion("tyrant_ult", 41, "스톨른 썬더 — 망치를 하늘로 치켜들고 왼 주먹을 굽혀 든 채 둘레의 힘을 빨아들일 때마다 몸을 크게 젖히며 떨고, 웅크렸다가 두 손으로 망치를 "
           "머리 뒤로 든 채 높이 뛰어오름", fade=(0.03, 0.04))
off_bare(m)
keyp(m, 3.0, TY_RAISE, "out3")
for t in (10, 20, 30):
    keyp(m, t - 0.8, P(TY_RAISE, body=[-15, 4, 0]))
    keyp(m, t + 0.8, P(TY_RAISE, body=[-22, 4, 0], head=[-38, 0], rk=22, lk=22, root=[-0.08, 0.0]), "out3")
    if t < 30:
        keyp(m, t + 5.0, P(TY_RAISE, body=[-16, 4, 0], head=[-32, 0], rk=14, lk=14), "inout")
keyp(m, 31.6, TY_CROUCH, "in")
keyp(m, 33.4, TY_AIR, "out")
keyp(m, 37.0, P(TY_AIR, body=[-22, 0, 0], head=[-12, 0]))
keyp(m, 39.0, P(TY_AIR, body=[-24, 0, 0], head=[-10, 0]))
keyp(m, 41.0, P(TY_AIR, body=[-25, 0, 0], head=[-10, 0]))
fpw(m, 3.0, FPW_SKY, "out3")
fpw(m, 30.0, FPW_SKY)
fpw(m, 31.6, FPW_UP, "in")
fpw(m, 33.4, FPW_SKY, "out")
fpw(m, 41.0, FPW_SKY)
fpl(m, 3.0, nudge(FP_UPPER, (0.0, 0.05, 0.0), (10, 0, 0)), "out3")
fpl(m, 30.0, nudge(FP_UPPER, (0.0, 0.05, 0.0), (10, 0, 0)))
fpl(m, 33.4, FPL_RAISE2H, "out")
fpl(m, 41.0, FPL_RAISE2H)
m.set(softLegs=False)
impacts(m, 10.0, 20.0, 30.0)
use_skill(m, "tyrant_ult")

# 스톨른 썬더 :slam (39 — 효과 44 = 이 동작의 5): 떠 있던 채 몸을 더 젖혔다가 두 손으로 망치를 땅에 내려치며 한 무릎으로 내려앉고, 일어서며 망치를 어깨에 멤
TY_IMPACT = P(S_IMPACT, body=[44, 0, 0], ra=arm([0.0, -0.6, 0.8], 10, [0, -0.85, 0.53]), la=bent([0.05, -0.6, 0.8], 14, (0, 0, 1)), root=[-0.42, 0.3])
TY_SHOULDER = P(body=[2, 10, 0], head=[-8, -6], ra=bent([0.5, 0.5, -0.2], 120, (0.2, 0.3, -1)), la=L(A_CHAMBER), **LEGS_WIDE)  # 망치를 어깨에 멤
m = Motion("tyrant_ult_slam", 16, "스톨른 썬더 끝 — 떠 있던 채 몸을 더 젖혔다가 두 손으로 망치를 땅에 내려치며 한 무릎으로 내려앉고, 일어서며 망치를 어깨에 멤",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(TY_AIR, body=[-24, 0, 0], head=[-10, 0]), FPW_SKY)
keyp(m, 3.4, P(TY_AIR, body=[-30, 0, 0], head=[-8, 0]), "out")
keyp(m, 5.0, TY_IMPACT, "in")
m.arc(5.0, "ra", (0.0, 0.7, 0.7))
keyp(m, 9.0, P(TY_IMPACT, body=[38, 0, 0], root=[-0.38, 0.26]))
keyp(m, 12.4, TY_SHOULDER, "out3")
keyp(m, 15.6, READY_Y, "inout")
trail(m, 3.6, 5.4)
fpw(m, 3.4, FPW_SKY, "out", dp=(0.05, 0.05, 0.1))
fpw(m, 5.0, FPW_DOWN, "in")
fpw(m, 9.0, FPW_DOWN)
fpw(m, 12.4, FPW_UP, "out3", dp=(0.25, -0.2, 0))
fpw(m, 15.6, FPW_LOW)
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 5.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 9.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 12.4, FP_CHAMBER, "out3")
fpl(m, 15.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 5.0)
use_skill(m, "tyrant_ult:slam")
