"""0.51.0 2차 직업 스킬 동작 — 사신 2차 (2026-11-06 사용자 "다음 작업"): 각성 소울 리퍼 · 반전 엑시큐셔너 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SoulreaperSkills · ExecutionerSkills). 둘 다 낫 (오른손 — 1차 사신 job_motions_b 의 망령 GHOST 꼴, 왼손은 맨손). 몸 쓰는 직업이라 제 몸 · 제 낫 동작만 (영혼 등불 ·
영혼 줄 · 거대한 망령 사신 · 단두대 날 · 처형 표식은 그림). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (JobKit.motionLater — 바운티풀 하베스트의
뜯어냄 :rip, 페일 하베스트의 내리꽂음 :slam, 길로틴의 내려침 :drop). 단계 동작의 틱 0 열쇠는 앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게)."""
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

# 랜턴 오브 소울즈 (효과: 0 ~ 8 왼쪽 어깨 위에 영혼 등불이 뜸 → 8 · 24 · … 사슬은 등불이 던짐): 왼손을 들어 등불을 불러내고 손끝으로 가리킴
LANTERN_CALL = P(body=[-6, -12, 0], head=[-16, 14], ra=arm([0.3, -0.6, 0.5], 60, [0.1, 0.9, 0.3]), la=arm([-0.55, 0.75, 0.32], 10), **LEGS_FIGHT)
m = Motion("soulreaper_lantern", 18, "랜턴 오브 소울즈 — 낫을 옆에 세워 쥔 채 왼손을 왼 어깨 위로 들어 영혼 등불을 불러내 띄우고, 손끝으로 앞을 가리켜 사슬을 보냄",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 3.0, LANTERN_CALL, "out3")
keyp(m, 8.0, P(LANTERN_CALL, la=arm([-0.5, 0.7, 0.4], 6)))
keyp(m, 10.0, P(LANTERN_CALL, body=[4, -4, 0], head=[-4, 4], la=arm([-0.12, 0.15, 0.98], 2)), "out3")
keyp(m, 17.4, READY_S, "inout")
fpw(m, 3.0, FPW_LOW, "out3")
fpl(m, 3.0, nudge(FP_RAISE, (-0.15, 0.0, -0.1), (0, -10, 0)), "out3")
fpl(m, 10.0, FP_PALM, "out3")
fpl(m, 17.0, FP_LOW)
use_skill(m, "soulreaper_lantern")

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

# 컨뎀 (효과: 0 ~ 6 낫을 크게 뒤로 감음 → 6 비스듬히 크게 벰): 오른 어깨 위 뒤로 감고 왼손으로 겨눔 → 비스듬히 내리벰
CONDEMN_WIND = P(body=[4, -40, 0], head=[-6, 24], ra=arm([0.55, 0.65, -0.5], 40, [0.3, 0.6, -0.75]), la=arm([-0.4, 0.1, 0.9], 10), rl=[16, 0, 4], ll=[-28, 0, -4],
                 rk=20, lk=32, root=[-0.12, -0.05])
m = Motion("executioner_condemn", 16, "컨뎀 — 낫을 오른 어깨 위 뒤로 크게 감고 왼손으로 적을 겨눴다가, 크게 내디디며 비스듬히 내리벰", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, CONDEMN_WIND, "out3")
keyp(m, 3.6, P(CONDEMN_WIND, body=[2, -46, 0], root=[-0.16, -0.08]))
strike(m, "skill_slash", 4.4, 6.0)
fpl(m, 1.2, FP_PALM, "out3")
fpl(m, 5.0, FP_LOW)
impacts(m, 6.0)
use_skill(m, "executioner_condemn")

# 섀도 스토크 (효과: 0 ~ 8 겨눈 적의 건너편으로 뛰어듦 — 6 ~ 8 그 적 쪽으로 돌아섬 → 8 내려서며 등을 벰)
STALK_AIR = P(body=[18, 0, 0], head=[-10, 0], ra=bent([0.25, 0.85, -0.35], 60, (0.2, 0.3, -1)), la=arm([-0.6, 0.1, 0.4], 20), root=[0.1, 0.1])  # 낫을 머리 위 뒤로
m = Motion("executioner_stalk", 16, "섀도 스토크 — 몸을 낮췄다가 그림자처럼 높이 뛰어들어 낫을 머리 위 뒤로 감고, 내려서며 적의 등을 내리벰", fade=(0.02, 0.1))
off_bare(m)
keyp(m, 0.8, P(F_CROUCH, body=[30, 0, 0]), "out3")
keyp(m, 2.6, STALK_AIR, "out")
keyp(m, 5.6, P(STALK_AIR, body=[10, 0, 0], head=[6, 0]))
strike(m, "chop", 6.4, 8.0)
fpw(m, 0.8, FPW_LOW, "out3")
fpw(m, 2.6, FPW_UP, "out")
impacts(m, 8.0)
use_skill(m, "executioner_stalk")

# 길로틴 (효과: 0 ~ 18 낫을 높이 치켜듦 · 적 머리 위 하늘에 단두대 날 → 18 :drop → 22 내리침)
m = Motion("executioner_guillotine", 20, "길로틴 — 두 손으로 낫을 머리 위로 곧게 치켜들고 (몸이 떨리며 점점 젖혀짐) 하늘의 날을 겨눔", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 3.0, P(RAISE2H, head=[-26, 0]), "out3")
for i, t in enumerate((7.0, 11.0, 15.0, 18.0)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(RAISE2H, head=[-28, 0], body=[-12 - 2 * i + s, s, 0], rk=16, lk=16, root=[-0.04 - 0.01 * i, 0.0]), "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 18.0, FPW_UP, dp=(0.12, -0.06, 0))
fpl(m, 3.0, FPL_RAISE2H, "out3")
fpl(m, 18.0, FPL_RAISE2H)
use_skill(m, "executioner_guillotine")

# 길로틴 :drop (18 — 효과 22 = 이 동작의 4): 젖혔던 몸을 앞으로 접으며 두 손으로 낫을 온 힘으로 내리침
m = Motion("executioner_guillotine_drop", 12, "길로틴 끝 — 젖혔던 몸을 앞으로 접으며 두 손으로 낫을 온 힘으로 내리쳐 날을 떨어뜨림", fade=(0.0, 0.1))
off_bare(m)
first(m, P(RAISE2H, head=[-28, 0], body=[-18, 1, 0], rk=16, lk=16, root=[-0.07, 0.0]), FPW_UP)
keyp(m, 2.4, P(RAISE2H, head=[-34, 0], body=[-24, 0, 0], rk=22, lk=22, root=[-0.1, -0.04]), "out")
keyp(m, 4.0, P(S_IMPACT, body=[36, 0, 0], root=[-0.34, 0.2]), "in")
keyp(m, 7.0, P(S_IMPACT, body=[32, 0, 0], root=[-0.3, 0.18]))
keyp(m, 11.4, READY_S, "inout")
trail(m, 2.6, 4.6)
fpw(m, 2.4, FPW_SKY, "out")
fpw(m, 4.0, FPW_DOWN, "in")
fpw(m, 11.4, FPW_LOW)
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 4.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 11.4, FP_LOW)
impacts(m, 4.0)
use_skill(m, "executioner_guillotine:drop")

# 원 라스트 데이 (태세): 낫 자루를 곁에 세워 짚고 왼 주먹을 가슴에 대며 고개를 들어 해 질 녘을 맞음
LASTDAY = P(body=[-8, 0, 0], head=[-20, 0], ra=arm([0.4, -0.3, 0.3], 70, [0.05, 1, 0.1]), la=L(A_CHEST), **LEGS_WIDE)
m = Motion("executioner_lastday", 18, "원 라스트 데이 — 낫 자루를 곁에 세워 짚고 왼 주먹을 가슴에 대며 고개를 들어 해 질 녘을 맞음", fade=(0.04, 0.1))
off_bare(m)
keyp(m, 3.0, LASTDAY, "out3")
keyp(m, 12.0, P(LASTDAY, head=[-24, 0], body=[-10, 0, 0]))
keyp(m, 17.4, READY_S, "inout")
fpw(m, 3.0, FPW_UP, "out3", dp=(0.25, -0.2, 0))
fpw(m, 17.0, FPW_LOW)
fpl(m, 3.0, nudge(FP_CHAMBER, (0.1, 0.1, -0.05), (0, 30, 0)), "out3")
fpl(m, 16.0, FP_LOW)
use_skill(m, "executioner_lastday")

# 비포 선셋 (효과: 0 ~ 10 X 표식이 번짐 · 웅크림 → 10 순간 이동 → 12 첫 획 (오른쪽 위 → 왼쪽 아래) → 22 둘째 획 (왼쪽 아래 → 오른쪽 위 — 되돌려 벰))
SUNSET_CROUCH = P(body=[30, -24, 0], head=[-16, 18], ra=arm([0.5, -0.4, -0.6], 50, [0.4, 0.4, -0.8]), la=arm([-0.3, -0.5, 0.5], 30), **BRACE, root=[-0.34, 0.04])
m = Motion("executioner_ult", 26, "비포 선셋 — 낫을 뒤로 감고 낮게 웅크렸다가 표식으로 순간 이동해 오른쪽 위에서 왼쪽 아래로 크게 베고, 되돌려 왼쪽 아래에서 오른쪽 위로 베어 "
           "X 자를 그음", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, SUNSET_CROUCH, "out3")
keyp(m, 9.0, P(SUNSET_CROUCH, body=[32, -28, 0], root=[-0.36, 0.02]))
strike(m, "skill_slash", 10.4, 12.0)
backhand(m, 19.6, 22.0, low=0.35)
keyp(m, 25.6, READY_S, "inout")
fpw(m, 1.6, FPW_BACK, "out3")
impacts(m, 12.0, 22.0)
use_skill(m, "executioner_ult")
