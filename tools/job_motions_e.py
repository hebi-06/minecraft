"""0.51.0 2차 직업 스킬 동작 — 전사 각성 워로드 · 반전 글래디에이터 — 재설계안 2판 (사용자 "이펙트가 전직업보다 안좋았음 타격감이 없었음
이펙트가 작았음 동작이 뻣뻣했음"). 틱 0 = 스킬 (또는 단계 열쇠 — 서버가 그 순간에 다시 보냄: JobKit.motion · JobRecast.motion) 이 나간 틱, 효과의 later · every 와
같은 틱 (WarlordSkills · GladiatorSkills).

뻣뻣하지 않게 (job_motions_d 와 같은 기준): 치기 전에 반대쪽으로 크게 감고 낮아짐 → 치는 틱에 뿌리가 앞으로 (내디딤) → 지나쳐 멈췄다 되돌아옴 · 멈춘 자세도
숨 쉬듯 조금씩 · 이어지는 단계는 앞 단계의 끝 자세에서 바로 (들어오는 무게 0). 맞는 때 (impacts — 클라이언트 멈칫이 이 자세에 맞춰 멈춤).
워로드는 두 손으로 무겁게 (도끼를 끌고 · 들어 올려 · 내려찍음), 글래디에이터는 한 손 도끼 + 왼 주먹으로 빠르게 (연타 — 몸이 좌우로 꺾임).
몸 쓰는 직업 기준 (제 몸 · 제 도끼 동작만). 몸이 뜨는 단계 (홀름강의 뛰어듦 · 마지막 내려찍기) 는 다리를 이 파일에서 직접 (softLegs 끔)."""
import rune_motion_kit as K
from job_motions_d import LUNGE_DEEP, carry_spin, diag, diag_back, first, impacts, strike_with
from rune_motion_kit import (A_CROSS, A_GUARD, A_JAB, A_POINT, A_SPREAD, FP_CHAMBER, FP_DOWN, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PUSH_OUT, FPL_RAISE2H,
                             FPW_BACK, FPW_DOWN, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, W_HIGH2, W_READY, L, P, arm, bent, fpl, fpw, keyp, nudge, off_bare,
                             spin_right, use_skill)

Motion = K.Motion

# 두 손으로 도끼를 머리 위로 크게 (내려찍기 준비)
AXE_OVER = P(W_HIGH2, head=[-14, 0], body=[-16, 0, 0])
# 내려찍고 도끼를 땅에 박은 채 버팀
AXE_PLANT = P(body=[34, 0, 0], ra=arm([0.05, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.75], 18), rl=[-24, 0, 0], ll=[16, 0, 0], rk=40,
              lk=30, root=[-0.26, 0.16])
# 내리찍은 자리에서 깊이 웅크려 버팀 (큰 내려찍기)
AXE_IMPACT = P(AXE_PLANT, body=[44, 0, 0], head=[-8, 0], rl=[-34, 0, 10], ll=[20, 0, -8], rk=76, lk=48, root=[-0.4, 0.3])
# 무거운 도끼를 오른쪽 아래에 늘어뜨림 (쉼)
AXE_HANG = P(body=[12, 18, 0], head=[-4, 0], ra=arm([0.45, -0.75, 0.4], 30, [0.4, -0.3, 0.85]), la=arm([-0.45, -0.75, 0.3], 30), rl=[18, 0, 4], ll=[-22, 0, -4],
             rk=26, lk=30, root=[-0.14, 0.12])
FPW_HANG = dict(p=(0.25, -0.5, 0.2), blade=(0.5, 0.15, 0.85))

# ------------------------------------------------------------ 워로드 (도끼 — 주황 · 갈색 곰 기운, 두 손으로 무겁게)

# 대지 가르기 (모음 — 효과: 0 ~ 24 도끼 끝이 땅을 긁으며 불티 · 앞 땅의 빛 금이 길어짐 → 다시 누르거나 24 에 단계 :slam (내려침)). 모으는 동안 몸을 오른쪽으로 감아
# 도끼를 오른쪽 뒤 땅에 끌고 낮게 웅크려 떨며 더 낮아짐 (다 모이면 가장 크게 감김 — 감는 것은 몸통 24 ~ 32° 까지: 영상 1 의 40 ~ 50° 는 옆으로 돌아선 듯 보였음)
REND_DRAG = P(body=[24, 24, 0], head=[-12, 0], ra=arm([0.42, -0.72, -0.55], 22, [0.35, -0.55, -0.76]), la=bent([0.35, -0.55, 0.2], 80, (0.8, 0.2, -0.3)),
              rl=[26, 0, 6], ll=[-34, 0, -6], rk=48, lk=58, root=[-0.3, 0.05])  # 도끼를 오른쪽 뒤 땅에 끎 (왼손은 자루 가까이)
FPW_DRAG = dict(p=(0.6, -0.7, 0.45), blade=(0.55, -0.45, -0.7))
m = Motion("warlord_rend", 32, "대지 가르기 — 몸을 오른쪽으로 감아 도끼를 오른쪽 뒤 땅에 끌며 낮게 웅크리고, 힘을 모으는 동안 떨리며 더 낮아지고 더 감김", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.4, REND_DRAG, "out3")
for i, tk in enumerate((5.0, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 22.5)):
    s = (i + 1) / 8
    sg = 1 if i % 2 else -1
    keyp(m, tk, P(REND_DRAG, body=[24 + 6 * s, 24 + 8 * s, sg * 1.4], rk=48 + 12 * s, lk=58 + 10 * s, root=[-0.3 - 0.08 * s, 0.05]))
    fpw(m, tk, FPW_DRAG, dp=(sg * 0.02, -0.03 * s, 0.03 * s))
keyp(m, 26.0, P(REND_DRAG, body=[30, 32, 0], rk=60, lk=68, root=[-0.38, 0.05]))
keyp(m, 31.0, P(W_READY, body=[8, 6, 0]))
fpw(m, 2.4, FPW_DRAG, "out3")
fpw(m, 31.0, FPW_LOW)
fpl(m, 2.4, ((0.12, -0.3, 0.1), (-5, -30, 0)), "out3")
fpl(m, 31.0, FP_LOW)
m.set(softLegs=False)
use_skill(m, "warlord_rend")

# 내려침 (단계 :slam — 다시 누름 · 다 모임: 0 ~ 2 끌던 도끼를 오른쪽에서 크게 머리 위로 휘돌려 → 4 앞 땅을 내려찍음 (갈라짐 · 맞히면 멈칫 2 ~ 3틱))
m = Motion("warlord_rend_slam", 16, "대지 가르기 내려침 — 끌던 도끼를 오른쪽 뒤에서 머리 위로 크게 휘돌려 들고, 크게 내디디며 두 손으로 앞 땅을 내려찍어 박은 채 버팀", fade=(0.0, 0.1))
off_bare(m)
first(m, P(REND_DRAG, body=[28, 30, 0], rk=56, lk=64, root=[-0.34, 0.05]), FPW_DRAG)
keyp(m, 1.2, P(body=[-4, 30, 0], head=[-16, 0], ra=arm([0.55, 0.75, -0.35], 30, [0.2, 0.6, -0.8]), la=bent([0.3, 0.5, 0.3], 70, (0.5, 1, 0)), rl=[20, 0, 6],
                ll=[-24, 0, -6], rk=34, lk=40, root=[-0.16, 0.1]), "out")
keyp(m, 2.2, P(AXE_OVER, body=[-20, 6, 0], root=[-0.1, 0.18]), "out3")
strike_with(m, "slam", 2.5, 4.0, 5.4, a=P(AXE_OVER, body=[-20, 4, 0], root=[-0.1, 0.2], rl=[20, 0, 4], ll=[-30, 0, -4], rk=30, lk=40),
            b=P(AXE_IMPACT))
keyp(m, 8.0, P(AXE_IMPACT, body=[40, 0, 0], root=[-0.37, 0.28]))
keyp(m, 11.0, P(AXE_IMPACT, body=[34, 4, 0], rk=60, lk=40, root=[-0.3, 0.22]))
keyp(m, 14.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.2, dict(p=(0.4, 0.4, 0.3), blade=(0.3, 0.9, -0.2)), "out")
fpw(m, 2.2, FPW_UP, "out3")
fpw(m, 8.0, FPW_DOWN)
fpw(m, 14.6, FPW_LOW)
fpl(m, 2.2, FPL_RAISE2H, "out3")
fpl(m, 3.2, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 4.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 8.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 12.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 4.0)
use_skill(m, "warlord_rend:slam")

# 동면 (효과: 0 둘레에 돌창 굴이 솟음 · 웅크린 곰의 혼 → 2 · 10 · 18 · 26 · 34 숨 (흙먼지) → 40 깨어나며 둘레가 터짐 (밀쳐냄 — 맞히면 멈칫 3틱))
DEN = P(body=[46, 0, 0], head=[22, 0], ra=bent([0.1, -0.35, 0.8], 115, (-1, 0.4, 0.2)), la=L(A_CROSS), rl=[-48, 0, 12], ll=[-48, 0, -12], rk=100, lk=100,
        root=[-0.46, -0.04])  # 몸을 말아 웅크림 (도끼를 가슴에 품고 왼팔로 감쌈)
WAKE = P(body=[-20, 0, 0], head=[-34, 0], ra=arm([0.9, 0.3, 0.2], 10, [0.7, 0.5, 0.5]), la=L(arm([0.9, 0.3, 0.2], 10)), rl=[0, 0, 16], ll=[0, 0, -16], rk=16,
         lk=16, root=[0.06, 0])  # 몸을 펴며 팔을 활짝 (곰처럼 일어서 포효)
m = Motion("warlord_slumber", 50, "동면 — 도끼를 가슴에 품고 몸을 말아 웅크린 채 숨을 쉬며 버티다가, 몸을 펴며 두 팔을 활짝 펼쳐 일어서 포효함", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 3.0, DEN, "out3")
for i, tk in enumerate((6.0, 10.0, 14.0, 18.0, 22.0, 26.0, 30.0, 34.0)):
    out = i % 2 == 0
    keyp(m, tk, P(DEN, body=[46 - (4 if out else 0), 0, 0], root=[-0.46 + (0.03 if out else 0), -0.04]))
keyp(m, 37.6, P(DEN, body=[50, 0, 0], rk=106, lk=106, root=[-0.5, -0.04]), "in")
keyp(m, 40.0, WAKE, "out")
keyp(m, 43.0, P(WAKE, body=[-24, 0, 2], head=[-38, 0], root=[0.08, 0]))
keyp(m, 45.4, P(WAKE, body=[-18, 0, -2], head=[-32, 0], root=[0.05, 0]))
keyp(m, 49.0, P(W_READY, body=[8, 6, 0]))
fpw(m, 3.0, dict(p=(-0.35, -0.35, 0.2), blade=(-0.4, 0.8, 0.4)), "out3")
fpw(m, 37.6, dict(p=(-0.4, -0.45, 0.25), blade=(-0.4, 0.8, 0.4)), "in")
fpw(m, 40.0, FPW_SPREAD, "out")
fpw(m, 45.4, FPW_SPREAD)
fpw(m, 49.0, FPW_LOW)
fpl(m, 3.0, nudge(FP_GUARD, (0.05, -0.15, 0.1)), "out3")
fpl(m, 37.6, nudge(FP_GUARD, (0.05, -0.2, 0.12)), "in")
fpl(m, 40.0, FP_PUSH_OUT, "out")
fpl(m, 45.4, FP_PUSH_OUT)
fpl(m, 49.0, FP_LOW)
m.set(softLegs=False)
impacts(m, 40.0)
use_skill(m, "warlord_slumber")

# 곰의 포옹 (효과: 0 두 팔을 활짝 (곰의 두 앞발이 양옆에서) → 2 ~ 8 양옆 적을 끌어옴 → 8 가운데에서 부딪힘 → 9 머리 위로 → 12 내려찍음 (맞히면 멈칫 3틱))
HUG_OPEN = P(body=[-10, 0, 0], head=[-10, 0], ra=arm([0.95, 0.25, 0.2], 18, [0.6, 0.7, 0.4]), la=L(arm([0.95, 0.25, 0.2], 18)), rl=[0, 0, 14], ll=[0, 0, -14],
             rk=24, lk=24, root=[-0.12, 0])  # 두 팔을 양옆으로 활짝 (안을 듯)
HUG_CLOSE = P(body=[18, 0, 0], head=[2, 0], ra=bent([-0.25, -0.1, 0.95], 60, (-1, 0.3, 0.2)), la=bent([0.25, -0.1, 0.95], 60, (1, 0.3, 0.2)), rl=[-14, 0, 12],
              ll=[-14, 0, -12], rk=40, lk=40, root=[-0.24, 0.1])  # 두 팔을 앞으로 모아 끌어안음
m = Motion("warlord_hug", 24, "곰의 포옹 — 두 팔을 양옆으로 활짝 펼쳤다가 끌어안듯 앞으로 모으고, 곧바로 두 손으로 도끼를 머리 위로 들어 앞 땅을 내려찍음", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, HUG_OPEN, "out3")
keyp(m, 4.0, P(HUG_OPEN, body=[-6, 0, 0], ra=arm([0.85, 0.2, 0.45], 25, [0.4, 0.7, 0.6]), la=L(arm([0.85, 0.2, 0.45], 25))))
keyp(m, 8.0, HUG_CLOSE, "in")
keyp(m, 9.2, P(AXE_OVER, body=[-18, 0, 0], rl=[16, 0, 6], ll=[-24, 0, -6], rk=30, lk=34, root=[-0.12, 0.12]), "out3")
strike_with(m, "slam", 10.4, 12.0, 13.6, a=P(AXE_OVER, body=[-20, 0, 0], rl=[16, 0, 6], ll=[-28, 0, -6], rk=30, lk=40, root=[-0.12, 0.16]), b=P(AXE_IMPACT))
keyp(m, 16.4, P(AXE_IMPACT, body=[40, 0, 0], root=[-0.37, 0.27]))
keyp(m, 19.4, P(AXE_HANG, root=[-0.2, 0.18]), "out3")
keyp(m, 22.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.6, dict(p=(1.4, 0.2, 0.3), blade=(0.8, 0.5, 0.2)), "out3")
fpw(m, 8.0, dict(p=(-0.2, -0.2, -0.2), blade=(-0.3, 0.3, 0.9)), "in")
fpw(m, 9.2, FPW_UP, "out3")
fpw(m, 16.4, FPW_DOWN)
fpw(m, 19.4, FPW_HANG, "out3")
fpw(m, 22.6, FPW_LOW)
fpl(m, 1.6, nudge(FP_PUSH_OUT, (0.4, 0.05, 0.2), (0, -30, 0)), "out3")
fpl(m, 8.0, nudge(FP_JAB, (-0.05, -0.1, 0.05)), "in")
fpl(m, 9.2, FPL_RAISE2H, "out3")
fpl(m, 11.2, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 12.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 16.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 19.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 12.0)
use_skill(m, "warlord_hug")

# 거인의 무게 (태세 — 효과: 0 몸의 기운 → 6 한 발로 땅을 구름 (둘레가 갈라지고 돌창 넷))
TITAN_LIFT = P(body=[-4, -10, -8], head=[-6, 0], ra=bent([0.5, 0.6, -0.3], 120, (-0.3, 1, -0.2)), la=arm([-0.9, 0.2, 0.25], 15), rl=[-80, 0, 8], rk=96,
               ll=[-4, 0, -6], lk=16, root=[0.04, 0])  # 오른발을 높이 들고 도끼를 어깨에 메고 왼팔로 균형 (시코)
TITAN_STOMP = P(body=[22, 4, 6], head=[0, 0], ra=bent([0.5, 0.55, -0.3], 120, (-0.3, 1, -0.2)), la=arm([-0.8, -0.3, 0.4], 20), rl=[-12, 0, 16], ll=[-10, 0, -14],
                rk=58, lk=52, root=[-0.36, 0])  # 발을 구르며 크게 낮아짐
TITAN_STANCE = P(body=[16, 0, 0], head=[-4, 0], ra=arm([0.3, -0.35, 0.88], 70, [-0.85, 0.3, 0.4]), la=bent([-0.2, -0.3, 0.92], 70, (0.8, 0.4, 0)),
                 rl=[-8, 0, 16], ll=[-8, 0, -16], rk=50, lk=50, root=[-0.3, 0])  # 넓게 낮게 버티며 도끼를 두 손으로 앞에 가로로
m = Motion("warlord_titan", 30, "거인의 무게 — 도끼를 어깨에 메고 오른발을 높이 들었다가 땅을 굴러 크게 낮아지고, 넓게 버티며 도끼를 두 손으로 앞에 가로로 쥠", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 3.4, TITAN_LIFT, "out3")
keyp(m, 4.8, P(TITAN_LIFT, rl=[-86, 0, 8], rk=100, root=[0.06, 0]), "out")
keyp(m, 6.0, TITAN_STOMP, "in")
keyp(m, 8.6, P(TITAN_STOMP, body=[18, 2, 2], root=[-0.32, 0]))
keyp(m, 12.0, TITAN_STANCE, "out3")
keyp(m, 18.0, P(TITAN_STANCE, body=[18, 0, 0], root=[-0.32, 0]))
keyp(m, 23.0, P(TITAN_STANCE, body=[15, 0, 0], root=[-0.29, 0]))
keyp(m, 28.4, P(W_READY, body=[10, 6, 0], rk=30, lk=26))
fpw(m, 3.4, dict(p=(0.25, 0.35, 0.45), blade=(0.2, 0.6, -0.8)), "out3")
fpw(m, 6.0, dict(p=(0.2, 0.2, 0.4), blade=(0.25, 0.5, -0.85)), "in")
fpw(m, 12.0, dict(p=(-0.3, -0.1, -0.2), blade=(-0.95, 0.15, 0.25)), "out3")
fpw(m, 23.0, dict(p=(-0.3, -0.12, -0.2), blade=(-0.95, 0.15, 0.25)))
fpw(m, 28.4, FPW_LOW)
fpl(m, 3.4, nudge(FP_PUSH_OUT, (0.3, 0.0, 0.15), (0, -25, 0)), "out3")
fpl(m, 12.0, nudge(FP_GUARD, (-0.1, -0.15, -0.05)), "out3")
fpl(m, 28.4, FP_LOW)
m.set(softLegs=False)
use_skill(m, "warlord_titan")

# 마지막 휘두름 (효과: 0 ~ 24 도끼를 뒤로 크게 젖혀 힘을 모음 (어스름한 하늘 · 바깥 고리가 달아오름 · 곰의 혼이 솟음) → 24 둘레를 한 바퀴 (오른쪽으로 — 도끼가
# 앞을 지나는 때 · 맞히면 멈칫 3 ~ 4틱) → 28 한 바퀴를 마치고 도끼를 뻗은 채 버팀)
ULT_WIND = P(body=[22, -78, 0], head=[-6, 0], ra=arm([-0.75, -0.25, -0.6], 30, [-0.7, -0.1, -0.7]), la=bent([-0.3, -0.45, -0.2], 70, (-1, 0.3, 0.2)),
             rl=[28, 0, 10], ll=[-36, 0, -10], rk=58, lk=66, root=[-0.36, -0.06])  # 몸을 왼쪽으로 크게 감고 도끼를 왼쪽 뒤로 낮게 (두 손)
ULT_FWD = P(K.SPIN_FWD_R, body=[16, 6, 0], ra=arm([0.1, -0.12, 1], 4, [0.5, -0.1, 0.86]), la=arm([-0.9, -0.3, -0.3], 25), rl=[20, 0, 10], ll=[-30, 0, -10],
            rk=46, lk=52, root=[-0.32, 0.12])  # 도끼를 끝까지 뻗고 낮게 돎
ULT_END = P(body=[18, 30, 0], head=[-6, 0], ra=arm([0.85, -0.25, 0.45], 10, [0.8, -0.35, -0.5]), la=arm([-0.75, -0.4, -0.4], 25), rl=[24, 0, 10],
            ll=[-34, 0, -10], rk=52, lk=56, root=[-0.34, 0.16])  # 한 바퀴를 마치고 도끼를 오른쪽으로 뻗은 채 버팀
m = Motion("warlord_ult", 40, "마지막 휘두름 — 몸을 왼쪽으로 크게 감아 도끼를 왼쪽 뒤로 낮게 젖히고 떨리며 힘을 모으다가, 낮게 도끼를 끝까지 뻗어 오른쪽으로 크게 한 바퀴 휘두르고 "
           "도끼를 뻗은 채 버틴 뒤 거둠", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 3.0, ULT_WIND, "out3")
for i, tk in enumerate((6.0, 9.0, 12.0, 15.0, 18.0, 20.5)):
    s = (i + 1) / 6
    sg = 1 if i % 2 else -1
    keyp(m, tk, P(ULT_WIND, body=[22 + 4 * s, -62 - 10 * s, sg * 1.6], rk=58 + 8 * s, lk=66 + 8 * s, root=[-0.36 - 0.06 * s, -0.06]))
spin_right(m, [24.0], 28.0, wind_tick=22.2, fwd=ULT_FWD, end_pose=ULT_END)
keyp(m, 22.2, P(ULT_WIND, body=[28, -74, 0], rk=68, lk=76, root=[-0.43, -0.06]), "in")
keyp(m, 32.0, P(ULT_END, body=[16, 26, 0], root=[-0.3, 0.14], spin=-360))
keyp(m, 37.6, P(W_READY, body=[8, 6, 0], spin=-360))
carry_spin(m, 28.0, -360)
fpw(m, 3.0, dict(p=(-1.2, -0.45, 0.6), blade=(-0.7, -0.2, -0.7)), "out3")
fpw(m, 20.5, dict(p=(-1.3, -0.5, 0.65), blade=(-0.7, -0.2, -0.7)))
fpw(m, 32.0, FPW_SPREAD)
fpw(m, 37.6, FPW_LOW)
fpl(m, 3.0, ((-0.25, -0.2, 0.05), (10, 70, 0)), "out3")
fpl(m, 22.2, FP_LOW, "in")
fpl(m, 37.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 24.0)
use_skill(m, "warlord_ult")

# ------------------------------------------------------------ 글래디에이터 (한 손 도끼 + 왼 주먹 — 진홍 · 모래 금빛, 빠르게)

GL_READY = P(body=[14, -8, 0], head=[-6, 0], ra=arm([0.4, -0.3, 0.86], 75, [0.2, 0.7, 0.68]), la=L(A_GUARD), rl=[20, 0, 6], ll=[-24, 0, -6], rk=30, lk=32,
             root=[-0.16, 0.08])  # 낮게 몸을 숙이고 도끼를 앞에 · 왼 주먹은 턱 앞 (싸울 자세)
FPW_GL = dict(p=(0.1, -0.15, -0.05), blade=(0.2, 0.75, 0.6))
OVERHEAD1 = P(body=[-18, -6, 0], head=[-20, 0], ra=arm([0.15, 0.95, -0.25], 40, [0.05, 0.4, -0.9]), la=L(A_GUARD), rl=[22, 0, 6], ll=[-30, 0, -6], rk=30,
              lk=40, root=[-0.12, 0.2])  # 한 손으로 도끼를 머리 위로 (크게 내려칠 준비)
CHOP_DOWN = P(body=[30, 2, 0], head=[-6, 0], ra=arm([0.02, -0.55, 0.84], 8, [0.0, -0.9, 0.45]), la=arm([-0.5, -0.5, -0.2], 30), root=[-0.3, 0.42],
              **LUNGE_DEEP)  # 앞으로 크게 내디디며 곧게 내려침

# 도끼 난타 (효과: 2 · 5 · 8 (크게) — 무리 하나 · 12 · 15 · 18 (크게) — 무리 둘 (맞히면 크게 내려칠 때 멈칫 3틱))
m = Motion("gladiator_flurry", 26, "도끼 난타 — 한 걸음씩 내디디며 도끼로 비스듬히 내려 베고 되돌려 올려 벤 뒤 머리 위에서 곧게 내려찍기를 두 번 되풀이함 (왼 주먹은 턱 앞)",
           fade=(0.02, 0.1))
off_bare(m)
for base in (0.0, 10.0):
    diag(m, base + 0.6, base + 2.0, base + 3.0)
    diag_back(m, base + 3.4, base + 5.0, base + 6.0, low=0.4)
    keyp(m, base + 6.8, OVERHEAD1, "out3")
    strike_with(m, "chop", base + 7.0, base + 8.0, base + 9.2, a=P(body=[-10, -6, 0], rl=[22, 0, 6], ll=[-30, 0, -6], rk=30, lk=40, root=[-0.12, 0.2]),
                b=P(CHOP_DOWN))
keyp(m, 22.0, P(CHOP_DOWN, body=[26, 2, 0], root=[-0.26, 0.38]))
keyp(m, 25.0, P(GL_READY))
fpw(m, 22.0, FPW_DOWN)
fpw(m, 25.0, FPW_GL)
fpl(m, 0.6, FP_GUARD)
fpl(m, 25.0, FP_LOW)
impacts(m, 2.0, 5.0, 8.0, 12.0, 15.0, 18.0)
use_skill(m, "gladiator_flurry")

# 걸어 당기기 (효과: 0 크게 휘두름 → 1 날 끝이 적에게 걸림 → 2 ~ 6 끌어옴 → 6 내려찍음 (기절 — 맞히면 멈칫 3틱))
HOOK_WIND = P(body=[10, 72, 0], head=[-4, 0], ra=arm([0.8, 0.1, -0.55], 40, [0.6, 0.3, -0.75]), la=arm([-0.5, -0.2, 0.8], 10), rl=[18, 0, 6], ll=[-24, 0, -6],
              rk=28, lk=32, root=[-0.16, 0.0])  # 도끼를 오른쪽 뒤로 크게 젖힘 · 왼손은 앞으로
HOOK_REACH = P(body=[16, -40, 0], head=[-2, 0], ra=arm([-0.35, 0.05, 0.95], 2, [-0.85, 0.1, 0.5]), la=arm([-0.6, -0.4, -0.5], 25), root=[-0.26, 0.5],
               **LUNGE_DEEP)  # 크게 내디디며 도끼를 앞으로 끝까지 뻗어 휘두름 (날 끝을 걺)
HOOK_YANK = P(body=[-14, 30, 0], head=[-6, 0], ra=bent([0.45, -0.2, 0.3], 95, (0, 0.4, -1)), la=bent([0.1, -0.15, 0.95], 50, (0.6, 1, 0)), rl=[-24, 0, 8],
              ll=[18, 0, -8], rk=30, lk=44, root=[-0.24, -0.28])  # 몸을 뒤로 젖혀 도끼를 허리로 잡아당김 · 왼손은 끌려오는 적을 잡으려 앞으로
m = Motion("gladiator_hook", 18, "걸어 당기기 — 도끼를 오른쪽 뒤로 젖혔다가 크게 내디디며 끝까지 뻗어 휘둘러 날 끝을 걸고, 몸을 뒤로 젖혀 잡아당긴 뒤 곧바로 머리 위에서 내려찍음",
           fade=(0.02, 0.1))
off_bare(m)
keyp(m, 0.2, HOOK_WIND, "out")
keyp(m, 1.2, HOOK_REACH, "linear")
m.arc(1.2, "ra", (-0.2, 0.0, 1))
m.head.setdefault("trail", []).append([m.t(0.3), m.t(1.6)])
m.set(tilt=10)
keyp(m, 2.4, P(HOOK_REACH, body=[14, -44, 0], root=[-0.24, 0.46]), "out")
keyp(m, 4.2, HOOK_YANK, "out3")
keyp(m, 5.0, P(OVERHEAD1, body=[-12, -4, 0], root=[-0.14, 0.1]), "out3")
strike_with(m, "chop", 5.2, 6.0, 7.2, a=P(body=[-12, -4, 0], rl=[22, 0, 6], ll=[-30, 0, -6], rk=30, lk=40, root=[-0.14, 0.1]), b=P(CHOP_DOWN))
keyp(m, 10.0, P(CHOP_DOWN, body=[26, 2, 0], root=[-0.26, 0.36]))
keyp(m, 13.0, P(GL_READY))
keyp(m, 16.6, P(W_READY, body=[8, 4, 0]))
fpw(m, 0.2, FPW_BACK, "out")
fpw(m, 1.2, dict(p=(-1.6, 0.0, -0.3), blade=(-0.8, 0.1, 0.55)), "linear")
fpw(m, 2.4, dict(p=(-1.4, 0.0, -0.2), blade=(-0.75, 0.1, 0.6)), "out")
fpw(m, 4.2, dict(p=(0.35, -0.25, 0.35), blade=(0.6, 0.4, -0.65)), "out3")
fpw(m, 10.0, FPW_DOWN)
fpw(m, 13.0, FPW_GL)
fpw(m, 16.6, FPW_LOW)
fpl(m, 0.2, nudge(FP_JAB, (0.0, -0.1, 0.05)), "out")
fpl(m, 4.2, FP_JAB2, "out3")
fpl(m, 6.0, FP_GUARD)
fpl(m, 16.6, FP_LOW)
impacts(m, 6.0)
use_skill(m, "gladiator_hook")

# 여덟 팔 (효과: 4 · 14 · 24 · 34 무리마다 숫자 넷 (2틱마다 도끼질) — 34 무리의 넷째 (40) 는 크게 엇갈려 (X), 맞히면 무리 첫 도끼질 (4 · 14 · 24) 과 40 에 멈칫).
# 오른손 도끼와 왼 주먹이 번갈아 2틱마다 (몸이 좌우로 꺾임) — 여덟 팔의 잔상은 rfx_jarms
R_WIND = P(body=[8, 40, 0], head=[-4, 0], ra=arm([0.7, 0.55, -0.2], 50, [0.3, 0.8, -0.5]), la=bent([-0.3, -0.2, 0.7], 90, (0.6, 1, 0)), rl=[20, 0, 6],
           ll=[-26, 0, -6], rk=34, lk=38, root=[-0.2, 0.18])
R_HIT = P(body=[22, -30, 0], head=[-4, 0], ra=arm([-0.45, -0.4, 0.8], 6, [-0.75, -0.55, 0.35]), la=bent([-0.45, -0.3, 0.3], 110, (0.6, 1, 0)), rl=[22, 0, 6],
          ll=[-30, 0, -6], rk=36, lk=44, root=[-0.24, 0.3])  # 오른손 도끼를 오른쪽 위에서 왼쪽 아래로
L_HIT = P(body=[20, 34, 0], head=[-4, 0], ra=bent([0.6, -0.2, 0.2], 100, (0, 1, -0.5)), la=L(A_JAB), rl=[22, 0, 6], ll=[-30, 0, -6], rk=36, lk=44,
          root=[-0.24, 0.3])  # 왼 주먹을 곧게 뻗음 (도끼는 거두어 오른쪽 위로)
X_WIND = P(body=[-14, 0, 0], head=[-14, 0], ra=arm([0.45, 0.85, -0.3], 30, [0.2, 0.7, -0.7]), la=arm([-0.45, 0.85, -0.3], 30), rl=[22, 0, 8], ll=[-30, 0, -8],
           rk=34, lk=44, root=[-0.14, 0.2])  # 도끼와 왼손을 머리 위로 엇갈려 들어 올림
X_HIT = P(body=[34, 0, 0], head=[-6, 0], ra=arm([-0.55, -0.6, 0.55], 6, [-0.6, -0.75, 0.25]), la=arm([0.55, -0.6, 0.55], 6), root=[-0.36, 0.45],
          **LUNGE_DEEP)  # 엇갈려 크게 내려 벰 (X)
m = Motion("gladiator_eight", 50, "여덟 팔 — 낮게 몸을 숙여 오른손 도끼와 왼 주먹을 번갈아 쉬지 않고 휘두르며 (몸이 좌우로 꺾임) 네 번 몰아치고, 끝에 도끼와 왼손을 머리 위로 "
           "엇갈려 들었다가 크게 X 로 내려 벰", fade=(0.02, 0.1))
off_bare(m)
keyp(m, 2.0, R_WIND, "out3")
lines = [4, 6, 8, 10, 14, 16, 18, 20, 24, 26, 28, 30, 34, 36, 38]
for i, tk in enumerate(lines):
    right = i % 2 == 0
    keyp(m, tk - 0.9, R_WIND if right else P(R_HIT, body=[16, -20, 0], la=bent([-0.25, -0.2, 0.5], 110, (0.6, 1, 0))))
    keyp(m, tk, R_HIT if right else L_HIT, "linear")
    if right:
        fpw(m, tk - 0.9, dict(p=(0.4, 0.45, 0.2), blade=(0.45, 0.75, -0.5)))
        fpw(m, tk, dict(p=(-0.55, -0.05, -0.2), blade=(-0.7, -0.5, 0.5)), "linear")
        fpl(m, tk, nudge(FP_GUARD, (0.0, -0.05, 0.05)))
    else:
        fpw(m, tk, dict(p=(0.35, 0.1, 0.25), blade=(0.4, 0.8, -0.4)), "linear")
        fpl(m, tk - 0.9, FP_CHAMBER)
        fpl(m, tk, FP_JAB2, "linear")
keyp(m, 38.8, X_WIND, "out3")
keyp(m, 40.0, X_HIT, "linear")
m.arc(40.0, "ra", (-0.3, -0.3, 1))
m.head.setdefault("trail", []).append([m.t(39.0), m.t(41.0)])
keyp(m, 43.0, P(X_HIT, body=[30, 0, 0], root=[-0.32, 0.4]))
keyp(m, 48.4, P(GL_READY))
fpw(m, 38.8, FPW_UP, "out3")
fpw(m, 40.0, FPW_DOWN, "linear")
fpw(m, 48.4, FPW_GL)
fpl(m, 38.8, FPL_RAISE2H, "out3")
fpl(m, 40.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "linear")
fpl(m, 48.4, FP_LOW)
impacts(m, 4.0, 14.0, 24.0, 40.0)
use_skill(m, "gladiator_eight")

# 불사의 분노 (태세 — 효과: 0 몸에서 붉은 불길이 터짐 · 고리 셋 · 포효)
FURY_HUNCH = P(body=[34, 0, 0], head=[24, 0], ra=bent([0.25, -0.6, 0.55], 100, (-0.5, 1, 0.3)), la=L(A_CROSS), rl=[-36, 0, 10], ll=[-36, 0, -10], rk=78,
               lk=78, root=[-0.34, 0])  # 몸을 말아 웅크림 (팔을 몸 앞에 엇갈려 분노를 모음)
FURY_ROAR = P(body=[-22, 0, 0], head=[-36, 0], ra=arm([0.85, 0.4, 0.2], 12, [0.6, 0.75, 0.2]), la=L(A_SPREAD), rl=[0, 0, 16], ll=[0, 0, -16], rk=14, lk=14,
              root=[0.06, 0])  # 가슴을 젖히고 팔을 활짝 펼쳐 포효 (도끼를 바깥 위로)
m = Motion("gladiator_fury", 26, "불사의 분노 — 몸을 말아 팔을 엇갈려 분노를 모았다가, 가슴을 젖히며 도끼와 왼팔을 활짝 펼쳐 하늘을 향해 포효하고 (몸이 떨림) 낮게 싸울 자세를 잡음",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, FURY_HUNCH, "out3")
keyp(m, 3.0, FURY_ROAR, "out")
keyp(m, 5.0, P(FURY_ROAR, body=[-26, 0, 3], head=[-40, 0], root=[0.08, 0]))
keyp(m, 7.0, P(FURY_ROAR, body=[-23, 0, -3], head=[-37, 0], root=[0.06, 0]))
keyp(m, 9.0, P(FURY_ROAR, body=[-25, 0, 2], head=[-38, 0], root=[0.07, 0]))
keyp(m, 11.0, P(FURY_ROAR, body=[-22, 0, -1], head=[-35, 0], root=[0.06, 0]))
keyp(m, 15.0, GL_READY, "in")
keyp(m, 20.0, P(GL_READY, body=[16, -10, 0], root=[-0.18, 0.08]))
keyp(m, 24.4, P(W_READY, body=[8, 4, 0]))
fpw(m, 2.0, FPW_LOW, "out3", dp=(0, -0.1, 0))
fpw(m, 3.0, FPW_SPREAD, "out")
fpw(m, 11.0, FPW_SPREAD, dp=(0.04, -0.04, 0))
fpw(m, 15.0, FPW_GL, "in")
fpw(m, 24.4, FPW_LOW)
fpl(m, 2.0, FP_GUARD, "out3")
fpl(m, 3.0, FP_PUSH_OUT, "out")
fpl(m, 11.0, FP_PUSH_OUT)
fpl(m, 15.0, FP_GUARD, "in")
fpl(m, 24.4, FP_LOW)
use_skill(m, "gladiator_fury")

# 홀름강 (효과: 0 결투장이 솟음 (도끼를 하늘로 · 왼손으로 상대를 가리킴 — 결투를 알림) → 5 ~ 9 상대 곁으로 뛰어듦 (JobMove.hop — 멀면) → 10 내려침 (맞히면 멈칫 3틱)
# → 132 단계 :slam (8 내려찍음))
DECLARE = P(body=[-10, -14, 0], head=[-16, 0], ra=arm([0.2, 1, 0.05], 6, [0.05, 1, 0.1]), la=L(A_POINT), rl=[14, 0, 8], ll=[-18, 0, -8], rk=16, lk=20,
            root=[-0.04, 0.02])  # 도끼를 하늘로 치켜들고 왼손으로 상대를 가리킴
LEAP_CROUCH = P(body=[30, -6, 0], head=[-14, 0], ra=arm([0.2, 0.9, -0.35], 40, [0.05, 0.35, -0.94]), la=arm([-0.4, -0.5, -0.5], 30), rl=[-42, 0, 10],
                ll=[-42, 0, -10], rk=84, lk=84, root=[-0.34, 0.0])  # 도끼를 머리 위로 들며 웅크림
LEAP_AIR = P(OVERHEAD1, body=[-16, -4, 0], rl=[-54, 0, 8], rk=94, ll=[-26, 0, -8], lk=80, root=[0.0, 0.12])  # 떠서 무릎을 당기고 도끼를 머리 뒤로
m = Motion("gladiator_ult", 30, "홀름강 — 도끼를 하늘로 치켜들고 왼손으로 상대를 가리켜 결투를 알린 뒤, 웅크렸다 상대에게 뛰어들어 공중에서 도끼를 머리 뒤로 젖히고 내려앉으며 "
           "곧게 내려침", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, DECLARE, "out3")
keyp(m, 4.0, P(DECLARE, body=[-12, -14, 0], head=[-18, 0]))
keyp(m, 5.4, LEAP_CROUCH, "out")
keyp(m, 7.0, LEAP_AIR, "out")
keyp(m, 8.6, P(LEAP_AIR, body=[-20, -4, 0], root=[0.02, 0.14]))
strike_with(m, "chop", 8.9, 10.0, 11.4, a=P(LEAP_AIR, body=[-20, -4, 0], root=[0.02, 0.14]), b=P(CHOP_DOWN, rl=[-30, 0, 10], ll=[20, 0, -8], rk=70, lk=48,
                                                                                                 root=[-0.4, 0.3]))
keyp(m, 14.0, P(CHOP_DOWN, body=[30, 2, 0], rl=[-28, 0, 10], ll=[18, 0, -8], rk=60, lk=44, root=[-0.34, 0.28]))
keyp(m, 18.0, P(GL_READY), "out3")
keyp(m, 24.0, P(GL_READY, body=[16, -10, 0], root=[-0.18, 0.06]))
keyp(m, 28.6, P(GL_READY, body=[14, -6, 0]))
fpw(m, 2.0, FPW_SKY, "out3")
fpw(m, 5.4, FPW_UP, "out")
fpw(m, 8.6, dict(p=(-0.2, 0.9, 0.3), blade=(0.0, 0.7, -0.7)))
fpw(m, 14.0, FPW_DOWN)
fpw(m, 18.0, FPW_GL, "out3")
fpw(m, 28.6, FPW_GL)
fpl(m, 2.0, nudge(FP_JAB2, (0.0, 0.1, 0.0)), "out3")
fpl(m, 5.4, FP_LOW, "out")
fpl(m, 18.0, FP_GUARD, "out3")
fpl(m, 28.6, FP_GUARD)
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "gladiator_ult")

# 마지막 내려찍기 (단계 :slam — dur − 8: 0 ~ 2 웅크림 → 2 ~ 7 높이 뛰어올라 (JobMove.hop 3칸) 두 손으로 도끼를 머리 뒤까지 → 8 내려찍음 (맞히면 멈칫 4틱))
m = Motion("gladiator_ult_slam", 18, "홀름강 끝 — 웅크렸다 높이 뛰어올라 두 손으로 도끼를 머리 뒤까지 젖히고, 떨어지며 상대를 내려찍어 깊이 웅크려 버팀", fade=(0.02, 0.1))
off_bare(m)
first(m, P(GL_READY), FPW_GL)
keyp(m, 1.6, LEAP_CROUCH, "out3")
keyp(m, 3.6, P(AXE_OVER, body=[-18, 0, 0], rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0.12]), "out")
keyp(m, 6.2, P(AXE_OVER, body=[-26, 0, 0], ra=arm([0.1, 0.8, -0.58], 34, [0, 0.0, -1]), la=arm([-0.05, 0.8, -0.56], 38), rl=[-48, 0, 8], rk=90, ll=[-22, 0, -8],
                lk=76, root=[0.02, 0.14]))
strike_with(m, "slam", 6.6, 8.0, 9.6, a=P(AXE_OVER, body=[-26, 0, 0], rl=[-48, 0, 8], rk=90, ll=[-22, 0, -8], lk=76, root=[0.02, 0.14]), b=P(AXE_IMPACT))
keyp(m, 12.4, P(AXE_IMPACT, body=[40, 0, 0], root=[-0.37, 0.27]))
keyp(m, 16.6, P(GL_READY, body=[16, -6, 0]))
fpw(m, 1.6, FPW_UP, "out3")
fpw(m, 3.6, FPW_SKY, "out")
fpw(m, 12.4, FPW_DOWN)
fpw(m, 16.6, FPW_GL)
fpl(m, 1.6, FPL_RAISE2H, "out3")
fpl(m, 6.2, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 7.2, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 8.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 12.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 16.6, FP_GUARD)
m.set(softLegs=False)
impacts(m, 8.0)
use_skill(m, "gladiator_ult:slam")
