"""0.51.0 2차 직업 스킬 동작 — 사신 2차 (2026-11-06 사용자 "다음 작업"): 각성 소울 리퍼 · 반전 엑시큐셔너 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SoulreaperSkills · ExecutionerSkills). 둘 다 낫 (오른손 — 1차 사신 job_motions_b 의 망령 GHOST 꼴, 왼손은 맨손). 몸 쓰는 직업이라 제 몸 · 제 낫 동작만 (영혼 줄 ·
영혼 낫날 · 거대한 망령 사신 · 까마귀 · 사슬 · 망령 종 · 하늘의 단두대 · 처형 표식은 그림). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄
(JobKit.motionLater · JobRecast.motion — 바운티풀 하베스트의 뜯어냄 :rip, 페일 하베스트의 내리꽂음 :slam, 헤드헌트 다시 누름 :x, 체인 오브 저지먼트의 내리찍음 :drop,
길로틴 오브 헤븐의 내리그음 :drop). 단계 동작의 틱 0 열쇠는 앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게).
2026-11-07 3판 (직업 스킬 도감 표시 고침): 소울 리퍼 랜턴 오브 소울즈 → 스펙트럴 사이스, 엑시큐셔너 다섯 (컨뎀 · 섀도 스토크 · 길로틴 · 원 라스트 데이 · 비포 선셋 →
헤드헌트 · 레이븐 시프트 · 체인 오브 저지먼트 · 데스 넬 · 길로틴 오브 헤븐)."""
import rune_motion_kit as K
from job_motions_b import GHOST
from job_motions_d import BRACE, F_CROUCH, S_IMPACT, first, impacts
from rune_motion_kit import (A_CHEST, A_GUARD, A_PULL, FP_CHAMBER, FP_DOWN, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, FPL_RAISE2H, FPW_BACK, FPW_DOWN, FPW_LOW,
                             FPW_SKY, FPW_UP, LEGS_FIGHT, LEGS_SQUAT, LEGS_WIDE, RA_PLANT, RAISE2H, W_READY, P, L, arm, backhand, bent, fpl, fpw, keyp,
                             nudge, off_bare, spin_right, strike, use_skill)

Motion = K.Motion

READY_S = P(W_READY, body=[8, 6, 0])  # 끝 자세 (다음 단계 동작의 틱 0)


def trail(m, a, b):
    """무기 끝 자국 (a ~ b 틱)"""
    m.head.setdefault("trail", []).append([m.t(a), m.t(b)])
    m.set(tilt=30)


# ------------------------------------------------------------ 소울 리퍼 (낫 — 영혼 하늘빛 · 짙은 남청)

# 리핑 크레센트 (효과: 0 ~ 4 앞 3.5칸을 내달림 → 4 · 14 몸 둘레를 크게 휩씀). 낫을 뒤로 끌며 숙여 내달렸다가 (제 다리) 그 기세로 두 바퀴
DASH_LOW = P(GHOST, body=[30, 4, 0], head=[-14, 0], rl=[30, 0, 4], ll=[-42, 0, -4], rk=34, lk=64, root=[-0.18, 0.14])
m = Motion("soulreaper_crescent", 20, "리핑 크레센트 — 낫을 뒤로 끌며 몸을 낮게 숙여 내달렸다가, 그 기세로 낫과 함께 몸을 두 바퀴 크게 돌리고 낫을 앞으로 당겨 멈춤",
           fade=(0.02, 0.1))
keyp(m, 0.6, DASH_LOW, "out3")
keyp(m, 2.2, P(DASH_LOW, root=[-0.2, 0.16]))
spin_right(m, (4.0, 14.0), 17.4, wind_tick=3.0)
keyp(m, 19.6, P(READY_S, spin=-720), "inout")
fpw(m, 0.6, FPW_BACK, "out3")
m.set(softLegs=False)
impacts(m, 4.0, 14.0)
use_skill(m, "soulreaper_crescent")

# 엄브럴 트레스패스 (효과: 0 ~ 2 그림자로 겨눈 적에게 스며듦 → 2 ~ 18 몸속 (보이지 않음) → 18 건너편으로 찢고 나오며 베어 올림)
DIVE_IN = P(GHOST, body=[42, 0, 0], head=[-22, 0], rl=[22, 0, 4], ll=[-30, 0, -4], rk=40, lk=52, root=[-0.1, 0.26])  # 몸을 앞으로 던짐
CURL = P(body=[36, 8, 0], head=[18, 0], ra=arm([0.3, -0.75, -0.3], 60, [0.2, -0.3, -0.95]), la=L(A_GUARD), **LEGS_SQUAT)  # 몸속에서 웅크림
RISE_WIND = P(body=[26, -32, 0], head=[-4, 12], ra=arm([-0.55, -0.7, 0.35], 30, [-0.7, -0.55, 0.45]), la=arm([-0.3, -0.6, 0.4], 30), rl=[-30, 0, 8],
              ll=[-24, 0, -8], rk=70, lk=64, root=[-0.3, 0.0])  # 낫을 왼쪽 아래로 감음 (웅크린 채)
RISE_HIT = P(body=[-22, 26, 0], head=[-26, -10], ra=arm([0.45, 0.85, 0.25], 6, [0.3, 0.9, -0.3]), la=arm([-0.6, -0.3, 0.3], 20), rl=[-8, 0, 6], ll=[12, 0, -6],
             rk=14, lk=22, root=[0.04, 0.16])  # 몸을 젖히며 낫을 오른쪽 위로 크게 베어 올림
m = Motion("soulreaper_trespass", 26, "엄브럴 트레스패스 — 몸을 그림자로 던져 적에게 스며들고 (보이지 않는 동안 웅크림), 건너편으로 찢고 나오며 낫을 왼쪽 아래에서 "
           "오른쪽 위로 크게 베어 올림", fade=(0.02, 0.1))
m.set(hands={"main": "thrown"}, hide=[m.t(1.6), m.t(17.4)])  # 몸속에 있는 동안 낫도 숨김 (투명한 몸 밖으로 낫만 보였음 — 영상 1)
keyp(m, 1.2, DIVE_IN, "out3")
keyp(m, 3.0, CURL, "out")
keyp(m, 15.6, CURL)
keyp(m, 16.8, RISE_WIND, "out3")
keyp(m, 18.0, RISE_HIT, "out3")
m.arc(18.0, "ra", (0.1, 0.25, 1))
keyp(m, 21.0, P(RISE_HIT, body=[-16, 22, 0], root=[0.0, 0.12]))
keyp(m, 25.4, READY_S, "inout")
trail(m, 16.9, 19.0)
fpw(m, 1.2, FPW_BACK, "out3")
fpw(m, 16.8, dict(p=(-0.6, -0.5, 0.0), blade=(-0.6, -0.5, 0.6)), "out3")
fpw(m, 18.0, dict(p=(0.3, 0.6, -0.1), blade=(0.4, 0.9, 0.1)), "out3")
fpw(m, 25.4, FPW_LOW, "inout")
m.set(softLegs=False)
impacts(m, 18.0)
use_skill(m, "soulreaper_trespass")

# 바운티풀 하베스트 (효과: 6 낫을 앞 땅에 꽂음 → 10 · 20 · 30 · 40 영혼 줄로 빨아들임 → 46 :rip → 50 뜯어냄)
H_RAISE = P(body=[-10, -6, 0], head=[-16, 0], ra=arm([0.2, 0.95, 0.2], 20, [0.05, 0.2, 0.98]), la=arm([-0.3, 0.2, 0.9], 10), **LEGS_WIDE)  # 낫을 머리 위로 (날이 앞)
PLANT = P(body=[30, 0, 0], head=[-12, 0], ra=RA_PLANT, la=arm([-0.1, 0.12, 0.99], 4), rl=[30, 0, 6], ll=[-42, 0, -6], rk=44, lk=62, root=[-0.3, 0.1])
CHANNEL = P(PLANT, body=[24, -6, 0], la=arm([-0.25, 0.25, 0.94], 6), root=[-0.28, 0.08])  # 꽂은 낫을 쥔 채 왼손을 앞으로 뻗어 줄을 붙듦
GRASP = P(CHANNEL, la=bent([-0.2, 0.2, 0.9], 70, (0, 1, 0.3)), body=[20, -10, 0], root=[-0.26, 0.04])  # 왼손을 움켜쥐며 당김
m = Motion("soulreaper_harvest", 48, "바운티풀 하베스트 — 낫을 머리 위로 들었다가 앞 땅에 내리꽂고, 무릎을 굽혀 버틴 채 왼손을 앞으로 뻗어 영혼 줄을 붙들고 빨아들일 "
           "때마다 움켜쥐어 당김", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 2.6, H_RAISE, "out3")
keyp(m, 6.0, PLANT, "in")
keyp(m, 9.0, CHANNEL, "out")
for t in (10, 20, 30, 40):
    keyp(m, t + 2.0, GRASP, "out3")
    keyp(m, t + 7.0, CHANNEL, "inout")
keyp(m, 47.6, CHANNEL)
fpw(m, 2.6, FPW_UP, "out3")
fpw(m, 6.0, FPW_DOWN, "in")
fpw(m, 47.6, FPW_DOWN)
fpl(m, 2.6, FP_PALM_UP, "out3")
fpl(m, 9.0, FP_PALM, "out")
for t in (10, 20, 30, 40):
    fpl(m, t + 2.0, nudge(FP_CHAMBER, (-0.05, 0.12, -0.25), (15, 20, 0)), "out3")
    fpl(m, t + 7.0, FP_PALM, "inout")
impacts(m, 6.0)
use_skill(m, "soulreaper_harvest")

# 바운티풀 하베스트 :rip (46 — 효과 50 = 이 동작의 4): 꽂은 낫을 홱 뽑아 오른 어깨 위 뒤로 당기며 몸을 젖히고 왼손을 움켜쥔 채 허리로 당김
RIP = P(body=[-22, 20, 0], head=[-20, -6], ra=arm([0.45, 0.75, -0.45], 40, [0.2, 0.92, -0.35]), la=L(A_PULL), rl=[-10, 0, 8], ll=[16, 0, -8], rk=16, lk=26,
        root=[-0.06, -0.12])
m = Motion("soulreaper_harvest_rip", 14, "바운티풀 하베스트 끝 — 꽂은 낫을 홱 뽑아 어깨 위 뒤로 당기며 몸을 젖히고, 왼손을 움켜쥔 채 허리로 끌어당겨 영혼을 뜯어냄",
           fade=(0.0, 0.1))
off_bare(m)
first(m, CHANNEL, FPW_DOWN)
keyp(m, 2.4, P(CHANNEL, body=[34, 0, 0], root=[-0.34, 0.12]), "in")
keyp(m, 4.0, RIP, "out3")
keyp(m, 8.0, P(RIP, body=[-16, 16, 0]))
keyp(m, 13.4, READY_S, "inout")
trail(m, 2.6, 5.0)
fpw(m, 4.0, FPW_SKY, "out3")
fpw(m, 13.4, FPW_LOW, "inout")
fpl(m, 0.0, FP_PALM)
fpl(m, 4.0, nudge(FP_CHAMBER, (0.0, 0.05, 0.05), (10, 10, 0)), "out3")
fpl(m, 12.0, FP_LOW)
impacts(m, 4.0)
use_skill(m, "soulreaper_harvest:rip")

# 스펙트럴 사이스 (태세 — 효과: 0 ~ 8 영혼이 치켜든 낫으로 모여듦 → 8 머리 위에 영혼 낫날이 핌 → 11 몸 둘레를 한 바퀴 휩쓺): 두 손으로 낫을 머리 위로 치켜들어
# 영혼을 모았다가 (떨림) 몸과 함께 한 바퀴 크게 휘둘러 펼침
m = Motion("soulreaper_scythe", 22, "스펙트럴 사이스 — 두 손으로 낫을 머리 위로 치켜들어 영혼을 모았다가 (몸이 떨림), 몸과 함께 한 바퀴 크게 휘둘러 영혼 낫날을 펼침",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 3.0, P(RAISE2H, head=[-26, 0]), "out3")
keyp(m, 5.6, P(RAISE2H, head=[-28, 0], body=[-12, -1, 0], rk=16, lk=16), "inout")
keyp(m, 8.0, P(RAISE2H, head=[-30, 0], body=[-14, 1, 0], rk=18, lk=18, root=[-0.05, 0.0]), "inout")
spin_right(m, (11.0,), 15.0, wind_tick=9.6)
keyp(m, 21.4, P(READY_S, spin=-360), "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 8.0, FPW_UP, dp=(0.12, -0.08, 0))
fpl(m, 3.0, FPL_RAISE2H, "out3")
fpl(m, 8.0, FPL_RAISE2H)
fpl(m, 9.6, FP_LOW)
use_skill(m, "soulreaper_scythe")

# 페일 하베스트 (효과: 0 ~ 20 낫을 치켜듦 · 4 거대한 망령 사신이 솟음 → 20 · 30 둘레를 크게 휩씀 → 40 :slam → 46 내리꽂음)
m = Motion("soulreaper_ult", 40, "페일 하베스트 — 두 손으로 낫을 머리 위로 곧게 치켜들어 사신을 부르고 (몸이 떨리며 젖혀짐), 거대한 사신과 함께 낫과 몸을 두 바퀴 크게 "
           "휘둘러 둘레를 휩씀", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 3.0, P(RAISE2H, head=[-30, 0]), "out3")
for i, t in enumerate((7.0, 10.0, 13.0, 16.0)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(RAISE2H, head=[-30, 0], body=[-12 + 2 * s, 2 * s, 0], rk=18, lk=18, root=[-0.06, 0.0]), "inout")
spin_right(m, (20.0, 30.0), 34.0, wind_tick=18.4)
keyp(m, 39.6, P(READY_S, spin=-720), "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 16.0, FPW_UP, dp=(0.12, -0.08, 0))
fpl(m, 3.0, FPL_RAISE2H, "out3")
fpl(m, 16.0, FPL_RAISE2H)
fpl(m, 18.0, FP_LOW)
impacts(m, 20.0, 30.0)
use_skill(m, "soulreaper_ult")

# 페일 하베스트 :slam (40 — 효과 46 = 이 동작의 6): 제자리에서 뛰어올라 낫을 두 손으로 머리 위로 → 한 무릎을 꿇으며 땅에 내리꽂음
SLAM_AIR = P(RAISE2H, body=[-14, 0, 0], head=[-10, 0], rl=[-50, 0, 8], rk=90, ll=[-26, 0, -8], lk=76, root=[0.34, 0.06])  # 뛰어올라 낫을 머리 위로
SLAM_HIT = P(S_IMPACT, body=[40, 0, 0], root=[-0.4, 0.24])  # 한 무릎을 꿇고 낫을 땅에 내리꽂음
m = Motion("soulreaper_ult_slam", 16, "페일 하베스트 끝 — 제자리에서 뛰어오르며 두 손으로 낫을 머리 위로 치켜들었다가, 한 무릎을 꿇으며 사신과 함께 땅에 내리꽂음",
           fade=(0.0, 0.1))
off_bare(m)
first(m, READY_S, FPW_LOW)
keyp(m, 3.0, SLAM_AIR, "out")
keyp(m, 4.8, P(SLAM_AIR, body=[-20, 0, 0], root=[0.3, 0.04]))
keyp(m, 6.0, SLAM_HIT, "in")
keyp(m, 10.0, P(SLAM_HIT, body=[36, 0, 0], root=[-0.36, 0.22]))
keyp(m, 15.4, READY_S, "inout")
trail(m, 4.8, 6.6)
fpw(m, 3.0, FPW_SKY, "out")
fpw(m, 6.0, FPW_DOWN, "in")
fpw(m, 10.0, FPW_DOWN)
fpw(m, 15.4, FPW_LOW)
fpl(m, 3.0, FPL_RAISE2H, "out")
fpl(m, 6.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 15.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 6.0)
use_skill(m, "soulreaper_ult:slam")


# ------------------------------------------------------------ 엑시큐셔너 (낫 — 해 질 녘 금빛 · 짙은 보라)

# 헤드헌트 (효과: 0 ~ 6 낫을 오른쪽 뒤로 크게 젖힘 → 6 가로로 크게 벰 · 다시 누르면 :x): 오른쪽 뒤로 낮게 젖히고 왼손으로 적을 겨눴다가 크게 내디디며 가로로 벰
HEAD_WIND = P(body=[6, -50, 0], head=[-4, 30], ra=arm([0.75, 0.05, -0.6], 30, [0.55, 0.15, -0.8]), la=arm([-0.4, 0.1, 0.9], 10), rl=[16, 0, 4], ll=[-28, 0, -4],
              rk=22, lk=34, root=[-0.14, -0.06])
m = Motion("executioner_headhunt", 16, "헤드헌트 — 낫을 오른쪽 뒤로 크게 젖히고 왼손으로 적을 겨눴다가, 크게 내디디며 가로로 크게 벰", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, HEAD_WIND, "out3")
keyp(m, 3.6, P(HEAD_WIND, body=[4, -56, 0], root=[-0.18, -0.08]))
strike(m, "slash_h", 4.4, 6.0)
fpl(m, 1.2, FP_PALM, "out3")
fpl(m, 5.0, FP_LOW)
impacts(m, 6.0)
use_skill(m, "executioner_headhunt")

# 헤드헌트 :x (다시 누름 — 효과 3): 벤 자리에서 낫을 되돌려 왼쪽 아래에서 오른쪽 위로 비스듬히 베어 올려 X
m = Motion("executioner_headhunt_x", 12, "헤드헌트 다시 — 벤 자리에서 낫을 되돌려 왼쪽 아래에서 오른쪽 위로 비스듬히 베어 올려 X 자를 그음", fade=(0.0, 0.1))
off_bare(m)
first(m, READY_S, FPW_LOW)
backhand(m, 1.4, 3.0, low=0.35)
keyp(m, 11.4, READY_S, "inout")
impacts(m, 3.0)
use_skill(m, "executioner_headhunt:x")

# 레이븐 시프트 (효과: 0 ~ 8 몸이 까마귀 떼로 흩어져 겨눈 적의 건너편으로 날아감 — 보이지 않음 · 6 ~ 8 그 적 쪽으로 돌아섬 → 8 모이며 베어 올림): 몸을 낮췄다가
# 흩어지고 (보이지 않는 동안 웅크림 — 낫도 숨김), 건너편에서 모이며 낫을 왼쪽 아래에서 오른쪽 위로 베어 올림
m = Motion("executioner_raven", 18, "레이븐 시프트 — 몸을 낮췄다가 까마귀 떼로 흩어지고 (보이지 않는 동안 웅크림), 적의 건너편에서 모이며 낫을 왼쪽 아래에서 오른쪽 위로 "
           "베어 올림", fade=(0.02, 0.1))
m.set(hands={"main": "thrown"}, hide=[m.t(0.6), m.t(7.4)])  # 까마귀 떼가 된 동안 낫도 숨김 (엄브럴 트레스패스처럼)
keyp(m, 0.6, P(F_CROUCH, body=[30, 0, 0]), "out3")
keyp(m, 2.0, CURL, "out")
keyp(m, 6.4, CURL)
keyp(m, 7.2, RISE_WIND, "out3")
keyp(m, 8.0, RISE_HIT, "out3")
m.arc(8.0, "ra", (0.1, 0.25, 1))
keyp(m, 11.0, P(RISE_HIT, body=[-16, 22, 0], root=[0.0, 0.12]))
keyp(m, 17.4, READY_S, "inout")
trail(m, 7.0, 9.0)
fpw(m, 0.6, FPW_LOW, "out3")
fpw(m, 7.2, dict(p=(-0.6, -0.5, 0.0), blade=(-0.6, -0.5, 0.6)), "out3")
fpw(m, 8.0, dict(p=(0.3, 0.6, -0.1), blade=(0.4, 0.9, 0.1)), "out3")
fpw(m, 17.4, FPW_LOW, "inout")
impacts(m, 8.0)
use_skill(m, "executioner_raven")

# 체인 오브 저지먼트 (효과: 0 ~ 4 적 둘레 땅에서 사슬이 솟아 묶음 · 낫을 치켜듦 → (거리만큼 — 11 ~ 16) :drop → 그 3틱 뒤 땅을 내리찍어 세로 검기 → 22 목을 벰)
m = Motion("executioner_chains", 20, "체인 오브 저지먼트 — 두 손으로 낫을 머리 위로 곧게 치켜들고 (몸이 떨리며 점점 젖혀짐) 사슬에 묶인 적을 겨눔", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 3.0, P(RAISE2H, head=[-26, 0]), "out3")
for i, t in enumerate((7.0, 11.0, 15.0, 18.0)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(RAISE2H, head=[-28, 0], body=[-12 - 2 * i + s, s, 0], rk=16, lk=16, root=[-0.04 - 0.01 * i, 0.0]), "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 18.0, FPW_UP, dp=(0.12, -0.06, 0))
fpl(m, 3.0, FPL_RAISE2H, "out3")
fpl(m, 18.0, FPL_RAISE2H)
use_skill(m, "executioner_chains")

# 체인 오브 저지먼트 :drop (효과의 내리찍음 = 이 동작의 3): 젖혔던 몸을 앞으로 접으며 두 손으로 낫을 땅에 내리찍음 (세로 검기가 땅을 타고 달려감)
m = Motion("executioner_chains_drop", 12, "체인 오브 저지먼트 끝 — 젖혔던 몸을 앞으로 접으며 두 손으로 낫을 땅에 내리찍어 세로 검기를 보냄", fade=(0.0, 0.1))
off_bare(m)
first(m, P(RAISE2H, head=[-28, 0], body=[-16, 1, 0], rk=16, lk=16, root=[-0.06, 0.0]), FPW_UP)
keyp(m, 1.8, P(RAISE2H, head=[-34, 0], body=[-24, 0, 0], rk=22, lk=22, root=[-0.1, -0.04]), "out")
keyp(m, 3.0, P(S_IMPACT, body=[36, 0, 0], root=[-0.34, 0.2]), "in")
keyp(m, 6.0, P(S_IMPACT, body=[32, 0, 0], root=[-0.3, 0.18]))
keyp(m, 11.4, READY_S, "inout")
trail(m, 1.9, 3.6)
fpw(m, 1.8, FPW_SKY, "out")
fpw(m, 3.0, FPW_DOWN, "in")
fpw(m, 11.4, FPW_LOW)
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 3.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 11.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "executioner_chains:drop")

# 데스 넬 (태세 — 효과: 0 머리 위에 망령 종이 내려와 크게 울림): 낫 자루를 곁에 세워 짚고 왼손을 머리 위로 높이 들어 종을 울림 (손을 좌우로 한 번 흔듦)
KNELL = P(body=[-8, 0, 0], head=[-24, 0], ra=arm([0.4, -0.3, 0.3], 70, [0.05, 1, 0.1]), la=arm([-0.15, 0.97, 0.12], 8), **LEGS_WIDE)
m = Motion("executioner_knell", 18, "데스 넬 — 낫 자루를 곁에 세워 짚고 왼손을 머리 위로 높이 들어 망령 종을 한 번 크게 흔들어 울림", fade=(0.04, 0.1))
off_bare(m)
keyp(m, 3.0, KNELL, "out3")
keyp(m, 6.0, P(KNELL, la=arm([0.22, 0.94, 0.16], 8)), "inout")
keyp(m, 9.0, P(KNELL, la=arm([-0.3, 0.92, 0.1], 8)), "inout")
keyp(m, 12.0, P(KNELL, head=[-28, 0], body=[-10, 0, 0]))
keyp(m, 17.4, READY_S, "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.25, -0.2, 0))
fpw(m, 17.0, FPW_LOW)
fpl(m, 3.0, FP_RAISE, "out3")
fpl(m, 6.0, nudge(FP_RAISE, (0.08, 0.0, 0.0), (0, 0, -8)), "inout")
fpl(m, 9.0, nudge(FP_RAISE, (-0.08, 0.0, 0.0), (0, 0, 8)), "inout")
fpl(m, 16.0, FP_LOW)
use_skill(m, "executioner_knell")

# 길로틴 오브 헤븐 (효과: 0 ~ 10 하늘에서 단두대가 내려와 박힘 → 16 · 28 칼날이 떨어짐 — :drop 은 12 · 24, 이 동작의 4 에 내리그음): 낫을 곁에 세워 쥐고
# 왼손을 하늘로 뻗어 단두대를 부름 → 쉬는 자세로 (12 에 :drop 이 이어 받음)
CALL = P(body=[-10, -8, 0], head=[-26, 8], ra=arm([0.45, -0.35, 0.3], 60, [0.1, 1, 0.2]), la=arm([-0.2, 0.9, 0.38], 6), **LEGS_WIDE)
m = Motion("executioner_ult", 13, "길로틴 오브 헤븐 — 낫을 곁에 세워 쥐고 왼손을 하늘로 높이 뻗어 해 질 녘 하늘의 단두대를 부름", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 3.0, CALL, "out3")
keyp(m, 8.0, P(CALL, la=arm([-0.14, 0.93, 0.34], 4), head=[-30, 8]))
keyp(m, 11.8, READY_S, "inout")
fpw(m, 3.0, FPW_LOW, "out3")
fpl(m, 3.0, FP_RAISE, "out3")
fpl(m, 8.0, nudge(FP_RAISE, (0.0, 0.05, 0.0), (0, 0, 0)))
fpl(m, 11.8, FP_LOW, "inout")
use_skill(m, "executioner_ult")

# 길로틴 오브 헤븐 :drop (12 · 24 — 효과 16 · 28 = 이 동작의 4): 두 손으로 낫을 머리 위로 들었다가 허공을 내리그어 하늘의 칼날을 떨어뜨림 (땅은 치지 않음)
m = Motion("executioner_ult_drop", 12, "길로틴 오브 헤븐 칼날 — 두 손으로 낫을 머리 위로 들었다가 허공을 크게 내리그어 하늘의 칼날을 떨어뜨림", fade=(0.0, 0.1))
off_bare(m)
first(m, READY_S, FPW_LOW)
keyp(m, 2.2, P(RAISE2H, head=[-30, 0], body=[-16, 0, 0], rk=18, lk=18, root=[-0.06, 0.0]), "out")
keyp(m, 4.0, P(S_IMPACT, body=[24, 0, 0], root=[-0.2, 0.1]), "in")
keyp(m, 6.6, P(S_IMPACT, body=[20, 0, 0], root=[-0.18, 0.08]))
keyp(m, 11.4, READY_S, "inout")
trail(m, 2.4, 4.4)
fpw(m, 2.2, FPW_SKY, "out")
fpw(m, 4.0, FPW_DOWN, "in")
fpw(m, 11.4, FPW_LOW)
fpl(m, 2.2, FPL_RAISE2H, "out")
fpl(m, 4.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 11.4, FP_LOW)
impacts(m, 4.0)
use_skill(m, "executioner_ult:drop")
