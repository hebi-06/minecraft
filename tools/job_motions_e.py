"""0.51.0 2차 직업 스킬 동작 — 전사 각성 워로드 (재설계안 2판 — 사용자 "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음 동작이 뻣뻣했음")
· 반전 프로스트 레이더 (2026-11-07 재설계안 3판 — 글래디에이터 대신). 틱 0 = 스킬 (또는 단계 열쇠 — 서버가 그 순간에 다시 보냄: JobKit.motion ·
JobRecast.motion) 이 나간 틱, 효과의 later · every 와 같은 틱 (WarlordSkills · FrostraiderSkills).

뻣뻣하지 않게 (job_motions_d 와 같은 기준): 치기 전에 반대쪽으로 크게 감고 낮아짐 → 치는 틱에 뿌리가 앞으로 (내디딤) → 지나쳐 멈췄다 되돌아옴 · 멈춘 자세도
숨 쉬듯 조금씩 · 이어지는 단계는 앞 단계의 끝 자세에서 바로 (들어오는 무게 0). 맞는 때 (impacts — 클라이언트 멈칫이 이 자세에 맞춰 멈춤).
워로드는 두 손으로 무겁게 (도끼를 끌고 · 들어 올려 · 내려찍음), 프로스트 레이더는 한 손 도끼를 던지고 되부름 (빈 손을 뻗어 움켜쥐고 젖힘 · 받음).
몸 쓰는 직업 기준 (제 몸 · 제 도끼 동작만). 몸이 뜨는 단계 (아이스 바운드) 는 다리를 이 파일에서 직접 (softLegs 끔)."""
import rune_motion_kit as K
from job_motions_d import LUNGE_DEEP, carry_spin, first, impacts, strike_with
from rune_motion_kit import (A_CROSS, A_GUARD, A_POINT, A_THROW_FOLLOW, A_THROW_REL, A_THROW_WIND, FP_CHAMBER, FP_DOWN, FP_GUARD, FP_JAB, FP_JAB2,
                             FP_LOW, FP_PUSH_OUT, FPL_RAISE2H, FPW_DOWN, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, W_HIGH2, W_READY, L, P, arm, bent, fpl,
                             fpw, keyp, nudge, off_bare, spin_right, use_skill)

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

# 베어 스톰프 (효과: 0 등 뒤에 곰의 혼 · 도끼에 빛 (두 손으로 머리 위에 들고 웅크림) → 4 ~ 16 조준한 곳으로 뛰어오름 (JobMove.hop — 4.5칸 높이) · 거대한
# 곰의 혼이 함께 내려옴 → 16 내려찍음 (둘레 · 띄움 · 맞히면 멈칫) → 26 땅이 갈라지며 둘레로 솟음). 롤 볼리베어 R — 하늘에서 몸을 젖혀 도끼를 끝까지 들었다가
# 떨어지며 두 손으로 내려찍고 깊이 웅크려 버팀 (몸이 뜨는 동안 다리를 직접 — softLegs 끔)
STOMP_CROUCH = P(AXE_OVER, body=[24, 0, 0], head=[-20, 0], rl=[-40, 0, 10], ll=[-40, 0, -10], rk=84, lk=84, root=[-0.34, 0])  # 도끼를 머리 위에 들고 깊이 웅크림
STOMP_AIR = P(AXE_OVER, body=[-22, 0, 0], head=[-24, 0], rl=[-60, 0, 8], rk=100, ll=[-30, 0, -8], lk=80, root=[0, 0.1])  # 하늘에서 몸을 젖히고 다리를 접음
STOMP_FALL = P(AXE_OVER, body=[-30, 0, 0], head=[-30, 0], rl=[-24, 0, 10], rk=50, ll=[-10, 0, -10], lk=30, root=[0, 0.1])  # 떨어지며 도끼를 끝까지 젖힘 (다리를 폄)
m = Motion("warlord_stomp", 30, "베어 스톰프 — 도끼를 두 손으로 머리 위에 들고 깊이 웅크렸다가 높이 뛰어올라 하늘에서 몸을 젖히고 다리를 접은 채, 떨어지며 도끼를 끝까지 "
           "젖혔다가 내려찍고 깊이 웅크려 버틴 뒤 일어섬", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 2.0, STOMP_CROUCH, "out3")
keyp(m, 3.6, P(STOMP_CROUCH, body=[30, 0, 0], rk=94, lk=94, root=[-0.4, 0]))
keyp(m, 6.0, STOMP_AIR, "out")
keyp(m, 11.0, P(STOMP_AIR, body=[-26, 0, 0], head=[-28, 0], rl=[-64, 0, 8], rk=104))
keyp(m, 14.4, STOMP_FALL, "inout")
strike_with(m, "slam", 14.6, 16.0, 17.4, a=STOMP_FALL, b=P(AXE_IMPACT, body=[48, 0, 0], rk=88, lk=60, root=[-0.44, 0.3]))
keyp(m, 20.0, P(AXE_IMPACT, body=[44, 0, 0], root=[-0.4, 0.28]))
keyp(m, 26.0, P(AXE_IMPACT, body=[36, 4, 0], rk=60, lk=40, root=[-0.3, 0.22]), "inout")
keyp(m, 29.4, P(W_READY, body=[10, 6, 0], rk=30, lk=26))
fpw(m, 2.0, FPW_UP, "out3")
fpw(m, 6.0, FPW_SKY, "out")
fpw(m, 14.4, FPW_SKY, "inout", dp=(0.0, 0.08, 0.15))
fpw(m, 16.0, FPW_DOWN, "in")
fpw(m, 26.0, FPW_DOWN)
fpw(m, 29.4, FPW_LOW)
fpl(m, 2.0, FPL_RAISE2H, "out3")
fpl(m, 14.4, FPL_RAISE2H)
fpl(m, 16.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 26.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 29.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 16.0)
use_skill(m, "warlord_stomp")

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

# ------------------------------------------------------------ 프로스트 레이더 (2026-11-07 재설계안 3판 — 글래디에이터 대신: 한 손 서리 도끼를 던져 박고 되부름 ·
# 왼 주먹은 턱 앞 — 얼음빛 · 서리 흰빛)

FR_READY = P(body=[14, -8, 0], head=[-6, 0], ra=arm([0.4, -0.3, 0.86], 75, [0.2, 0.7, 0.68]), la=L(A_GUARD), rl=[20, 0, 6], ll=[-24, 0, -6], rk=30, lk=32,
             root=[-0.16, 0.08])  # 낮게 몸을 숙이고 도끼를 앞에 · 왼 주먹은 턱 앞 (싸울 자세)
FPW_FR = dict(p=(0.1, -0.15, -0.05), blade=(0.2, 0.75, 0.6))
# 도끼를 던진 뒤: 빈 오른손을 앞으로 펼침 (박힌 도끼와 이어짐) · 왼 주먹은 턱 앞
FR_EMPTY = P(body=[12, -10, 0], head=[-6, 0], ra=arm([0.3, 0.05, 0.95], 12, [0.1, 0.9, 0.3]), la=L(A_GUARD), rl=[20, 0, 6], ll=[-24, 0, -6], rk=30, lk=32,
             root=[-0.14, 0.08])
FPW_EMPTY = dict(p=(0.05, 0.05, -0.35), blade=(0.15, 0.95, 0.25))
# 되부름: 빈 손을 앞으로 끝까지 뻗어 움켜쥠 (떨림) → 몸을 뒤로 젖혀 잡아당김 → 날아온 도끼를 받아 손이 뒤로 밀림
PULL_REACH = P(FR_EMPTY, body=[18, -14, 0], ra=arm([0.25, 0.15, 0.96], 4, [0.1, 0.95, 0.2]), root=[-0.2, 0.14])
PULL_YANK = P(FR_READY, body=[-12, 16, 0], head=[-10, 0], ra=bent([0.45, -0.1, 0.35], 80, (0, 0.4, -1)), la=arm([-0.5, -0.4, 0.3], 30), rl=[-24, 0, 8],
              ll=[16, 0, -8], rk=20, lk=30, root=[0.06, -0.16])
CATCH_REACH = P(FR_READY, body=[6, -10, 0], ra=arm([0.25, 0.25, 0.94], 6, [0.1, 0.9, 0.4]))
CATCH_HIT = P(FR_READY, body=[0, 8, 0], head=[-10, 0], ra=arm([0.55, 0.1, 0.55], 50, [0.2, 0.95, -0.25]), root=[-0.1, -0.08])
CATCH_TWIRL = P(FR_READY, body=[12, -4, 0], ra=arm([0.55, 0.0, 0.7], 70, [0.3, -0.2, 0.9]))
# 던지기: 도끼를 오른 어깨 뒤로 크게 감아 낮아짐 (왼손은 겨눈 적을 가리킴) → 크게 내디디며 앞으로 던짐 → 던진 손이 앞 아래로 따라감
TH_WIND = P(body=[4, 34, 0], head=[-8, 0], ra=A_THROW_WIND, la=arm([0.6, 0.1, 0.75], 30), rl=[20, 0, 6], ll=[-26, 0, -6], rk=40, lk=40, root=[-0.22, -0.08])
TH_REL = P(body=[22, -26, 0], head=[-8, 0], ra=A_THROW_REL, la=arm([-0.4, -0.55, 0.15], 30), root=[-0.16, 0.36], **LUNGE_DEEP)
TH_FOLLOW = P(body=[26, -30, 0], head=[-6, 0], ra=A_THROW_FOLLOW, la=arm([-0.45, -0.6, 0.1], 30), root=[-0.2, 0.4], **LUNGE_DEEP)
LEAP_CROUCH = P(body=[30, -6, 0], head=[-14, 0], ra=arm([0.2, 0.9, -0.35], 40, [0.05, 0.35, -0.94]), la=arm([-0.4, -0.5, -0.5], 30), rl=[-42, 0, 10],
                ll=[-42, 0, -10], rk=84, lk=84, root=[-0.34, 0.0])  # 도끼를 머리 위로 들며 웅크림
SLAM_AIR = P(AXE_OVER, body=[-26, 0, 0], ra=arm([0.1, 0.8, -0.58], 34, [0, 0.0, -1]), la=arm([-0.05, 0.8, -0.56], 38), rl=[-48, 0, 8], rk=90, ll=[-22, 0, -8],
             lk=76, root=[0.02, 0.14])  # 떠서 두 손으로 도끼를 머리 뒤까지 젖힘

# 프로스트 바이트 (효과: 2 던짐 → 6 박힘 (서리 +2 — 맞히면 멈칫) → 10 틱 뒤부터 다시 누르면 단계 :recall). 롤 드레이븐 Q · 올라프 Q
m = Motion("frostraider_throw", 16, "프로스트 바이트 — 도끼를 오른 어깨 뒤로 감아 낮아졌다가 크게 내디디며 앞으로 회전시켜 던지고, 던진 손을 앞으로 펼친 채 (박힌 도끼와 이어짐) "
           "왼 주먹을 들고 버팀", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.0, TH_WIND, "out3")
keyp(m, 1.6, P(TH_WIND, body=[6, 40, 0], root=[-0.25, -0.12]), "in")
keyp(m, 2.0, TH_REL, "in")
m.arc(2.0, "ra", (-0.2, 0.6, 1))
m.head.setdefault("trail", []).append([m.t(1.6), m.t(3.0)])
keyp(m, 4.0, TH_FOLLOW, "out")
keyp(m, 8.0, FR_EMPTY, "inout")
keyp(m, 15.4, P(FR_EMPTY, body=[10, -8, 0]))
fpw(m, 1.0, dict(p=(0.75, 0.55, 0.45), blade=(0.3, 0.75, -0.6)), "out3")
fpw(m, 1.6, dict(p=(0.8, 0.6, 0.55), blade=(0.25, 0.7, -0.67)), "in")
fpw(m, 2.0, FPW_SPREAD, "in", dp=(-0.3, 0.1, 0.4))
fpw(m, 4.0, FPW_DOWN, "out")
fpw(m, 8.0, FPW_EMPTY, "inout")
fpw(m, 15.4, FPW_EMPTY)
fpl(m, 1.0, nudge(FP_PUSH_OUT, (0.2, 0.05, 0.15), (0, -20, 0)), "out3")
fpl(m, 2.0, FP_LOW, "in")
fpl(m, 8.0, FP_GUARD, "inout")
fpl(m, 15.4, FP_GUARD)
impacts(m, 6.0)
use_skill(m, "frostraider_throw")

# 되부름 (단계 :recall — 0 = 다시 누름: 0 ~ 5 빈 손을 앞으로 뻗어 움켜쥐고 떪 (박힌 도끼가 얼음을 터뜨리며 비틀림) → 5 몸을 젖혀 잡아당김 (도끼가 뽑히며 벰 —
# 맞히면 멈칫) → 9 날아온 도끼를 받음 (손이 뒤로 밀림) → 손목으로 한 바퀴 돌려 싸울 자세)
m = Motion("frostraider_throw_recall", 14, "되부름 — 빈 오른손을 앞으로 끝까지 뻗어 움켜쥐고 떨다가 몸을 뒤로 젖혀 잡아당기고, 날아온 도끼를 받아 손이 뒤로 밀린 뒤 "
           "손목으로 한 바퀴 돌려 싸울 자세", fade=(0.03, 0.1))
off_bare(m)
first(m, P(FR_EMPTY), FPW_EMPTY)
keyp(m, 1.6, PULL_REACH, "out3")
keyp(m, 2.6, P(PULL_REACH, body=[19, -12, 0]))
keyp(m, 3.4, P(PULL_REACH, body=[17, -16, 0]))
keyp(m, 4.2, P(PULL_REACH, body=[19, -13, 0]))
keyp(m, 5.4, PULL_YANK, "out")
keyp(m, 8.0, CATCH_REACH, "inout")
keyp(m, 9.2, CATCH_HIT, "out")
keyp(m, 11.4, CATCH_TWIRL, "inout")
keyp(m, 13.6, P(FR_READY))
m.arc(11.4, "ra", (0.6, 0.0, 1))
fpw(m, 1.6, dict(p=(0.05, 0.12, -0.45), blade=(0.15, 0.95, 0.2)), "out3")
fpw(m, 5.4, dict(p=(0.3, -0.1, 0.25), blade=(0.3, 0.9, -0.3)), "out")
fpw(m, 8.0, dict(p=(0.05, 0.1, -0.35), blade=(0.15, 0.95, 0.25)), "inout")
fpw(m, 9.2, dict(p=(0.25, 0.25, 0.25), blade=(0.25, 0.9, -0.35)), "out")
fpw(m, 11.4, dict(p=(0.2, -0.1, -0.05), blade=(0.35, -0.2, 0.9)), "inout")
fpw(m, 13.6, FPW_FR)
fpl(m, 1.6, FP_GUARD)
fpl(m, 13.6, FP_GUARD)
impacts(m, 5.0)
use_skill(m, "frostraider_throw:recall")

# 아이스 바운드 (효과: 0 ~ 10 서리 바람을 타고 날아감 (JobMove.hop — 박힌 도끼의 적 · 겨눈 적 앞으로) → 10 내려찍음 (둘레 · 얼음 가시 — 맞히면 멈칫) · 서리 갑옷)
m = Motion("frostraider_leap", 14, "아이스 바운드 — 도끼를 머리 위로 들며 웅크렸다 뛰어올라 공중에서 무릎을 당기고 두 손으로 도끼를 머리 뒤까지 젖힌 뒤, 내려앉으며 곧게 "
           "내려찍어 깊이 웅크려 버팀", fade=(0.02, 0.1))
off_bare(m)
keyp(m, 1.2, LEAP_CROUCH, "out3")
keyp(m, 3.0, P(AXE_OVER, body=[-18, 0, 0], rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0.12]), "out")
keyp(m, 7.6, SLAM_AIR)
strike_with(m, "slam", 8.4, 10.0, 11.4, a=P(SLAM_AIR), b=P(AXE_IMPACT))
keyp(m, 13.4, P(AXE_IMPACT, body=[38, 0, 0], root=[-0.35, 0.25]))
fpw(m, 1.2, FPW_UP, "out3")
fpw(m, 3.0, FPW_SKY, "out")
fpw(m, 13.4, FPW_DOWN)
fpl(m, 1.2, FPL_RAISE2H, "out3")
fpl(m, 7.6, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 9.0, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 10.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 13.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
m.set(softLegs=False)
impacts(m, 10.0)
use_skill(m, "frostraider_leap")

# 샤터 (효과: 0 ~ 8 도끼를 머리 위로 감아 올림 → 8 내려찍어 얼음 결정에 가둠 (맞히면 멈칫) → 22 ~ 27 몸을 크게 비틀어 감음 → 28 결정째 가로로 후려쳐 깨부숨 (큰 숫자))
TW_WIND = P(body=[10, 70, 0], head=[-6, 0], ra=arm([0.75, -0.2, -0.6], 30, [0.6, 0.2, -0.75]), la=bent([0.3, -0.3, 0.3], 80, (0.8, 0.2, -0.3)), rl=[22, 0, 8],
            ll=[-30, 0, -8], rk=46, lk=50, root=[-0.28, -0.1])  # 몸을 오른쪽으로 크게 감고 도끼를 오른쪽 뒤 낮게
TW_HIT = P(body=[18, -60, 0], head=[-6, 0], root=[-0.2, 0.4], **LUNGE_DEEP)  # 크게 내디디며 왼쪽 끝까지 돌아감
OVER2 = P(AXE_OVER, body=[-24, 0, 0], head=[-18, 0], rl=[22, 0, 6], ll=[-30, 0, -6], rk=30, lk=40, root=[-0.12, 0.1])
m = Motion("frostraider_shatter", 34, "샤터 — 두 손으로 도끼를 머리 위로 크게 들어 올렸다가 내디디며 곧게 내려찍어 얼음 결정에 가두고, 도끼를 땅에 댄 채 숨을 고른 뒤 "
           "몸을 오른쪽으로 크게 비틀어 감았다가 결정째 가로로 크게 후려침", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 3.0, P(OVER2, body=[-18, 0, 0], head=[-14, 0]), "out3")
keyp(m, 6.0, OVER2)
strike_with(m, "slam", 6.6, 8.0, 9.6, a=P(OVER2), b=P(AXE_IMPACT))
keyp(m, 13.0, P(AXE_PLANT, body=[36, 0, 0]))
keyp(m, 18.0, P(AXE_PLANT, body=[32, 4, 0], root=[-0.24, 0.14]))
keyp(m, 22.0, P(TW_WIND, body=[8, 50, 0]), "inout")
keyp(m, 25.6, TW_WIND, "out3")
strike_with(m, "slash_h", 26.4, 28.0, 30.4, a=P(TW_WIND), b=P(TW_HIT))
keyp(m, 33.4, P(FR_READY))
fpw(m, 3.0, FPW_UP, "out3")
fpw(m, 6.0, FPW_SKY)
fpw(m, 13.0, FPW_DOWN)
fpw(m, 22.0, FPW_LOW, "inout")
fpw(m, 33.4, FPW_FR)
fpl(m, 3.0, FPL_RAISE2H, "out3")
fpl(m, 8.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 18.0, FP_LOW)
fpl(m, 33.4, FP_GUARD)
impacts(m, 8.0, 28.0)
use_skill(m, "frostraider_shatter")

# 요툰 그립 (태세 — 0 등 뒤 서리 거인이 깨어나 도끼를 크게 내려침 (JobTraits16.jotunnOn) · 얼음 고리 셋)
GATHER = P(body=[30, 0, 0], head=[10, 0], ra=bent([0.2, -0.2, 0.6], 90, (-1, 0.3, 0.2)), la=bent([-0.2, -0.2, 0.6], 90, (1, 0.3, 0.2)), rl=[-30, 0, 10],
           ll=[-30, 0, -10], rk=70, lk=70, root=[-0.3, 0])  # 웅크려 도끼와 왼 주먹을 가슴 앞에 모음
ROAR = P(body=[-16, 0, 0], head=[-26, 0], ra=arm([0.85, 0.45, 0.15], 20, [0.4, 0.9, 0.1]), la=L(arm([0.85, 0.45, 0.15], 20)), rl=[0, 0, 14], ll=[0, 0, -14],
         rk=16, lk=16, root=[-0.02, 0.0])  # 몸을 뒤로 젖히며 두 팔을 크게 벌려 하늘로 포효
m = Motion("frostraider_jotunn", 18, "요툰 그립 — 웅크려 도끼와 왼 주먹을 가슴 앞에 모았다가 몸을 뒤로 젖히며 두 팔을 크게 벌려 하늘을 향해 포효하고 (등 뒤 서리 거인이 깨어남), "
           "낮게 내디뎌 싸울 자세", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, GATHER, "out3")
keyp(m, 4.4, ROAR, "out")
keyp(m, 9.0, P(ROAR, body=[-18, 0, 0], head=[-28, 0]))
keyp(m, 12.0, P(FR_READY, body=[18, -8, 0], root=[-0.2, 0.1]), "inout")
keyp(m, 17.4, P(FR_READY))
fpw(m, 2.0, FPW_LOW, "out3")
fpw(m, 4.4, FPW_SPREAD, "out")
fpw(m, 12.0, FPW_FR, "inout")
fpl(m, 2.0, FP_CHAMBER, "out3")
fpl(m, 4.4, nudge(FP_PUSH_OUT, (0.25, 0.1, 0.1), (0, -30, 0)), "out")
fpl(m, 12.0, FP_GUARD, "inout")
use_skill(m, "frostraider_jotunn")

# 핌불윈터 (효과: 0 ~ 6 도끼를 몸 뒤 아래로 감음 → 6 하늘 높이 던짐 → 하늘이 얼어붙음 · 빙하 도끼가 자람 → 26 겨눈 곳에 내리꽂힘 (맞히면 멈칫) → 42 되부름
# (빈 손을 뻗어 움켜쥐고 젖힘) → 46 빙하가 산산이 깨짐 · 도끼를 받음 (큰 숫자))
UP_WIND = P(body=[24, 10, 0], head=[-4, 0], ra=arm([0.3, -0.6, -0.7], 30, [0.2, -0.3, -0.9]), la=arm([-0.3, -0.5, 0.6], 30), rl=[-34, 0, 10], ll=[-34, 0, -10],
            rk=70, lk=70, root=[-0.3, 0])  # 도끼를 몸 뒤 아래로 감고 깊이 웅크림
UP_THROW = P(body=[-20, -4, 0], head=[-34, 0], ra=arm([0.1, 0.99, 0.05], 2, [0, 1, 0.05]), la=arm([-0.5, -0.4, 0.4], 30), rl=[0, 0, 10], ll=[0, 0, -10], rk=6,
             lk=6, root=[0.0, 0.02])  # 몸을 펴며 하늘로 던져 올림
SUMMON = P(body=[-8, -12, 0], head=[-20, 0], ra=arm([0.15, 0.97, 0.15], 4, [0.05, 1, 0.1]), la=L(A_POINT), rl=[16, 0, 8], ll=[-20, 0, -8], rk=18, lk=22,
           root=[-0.04, 0.02])  # 오른손은 하늘로 뻗고 왼손으로 겨눈 곳을 가리킴 (빙하 도끼를 부름)
m = Motion("frostraider_ult", 50, "핌불윈터 — 도끼를 몸 뒤 아래로 감아 웅크렸다 하늘 높이 던져 올리고, 오른손을 하늘로 뻗은 채 왼손으로 겨눈 곳을 가리켜 빙하 도끼를 부르다 "
           "왼손을 내리꽂듯 휘둘러 내리고, 빈 손을 앞으로 뻗어 움켜쥐며 몸을 젖혀 되불러 받음", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 3.0, UP_WIND, "out3")
keyp(m, 5.0, P(UP_WIND, body=[28, 12, 0], root=[-0.34, 0]), "in")
keyp(m, 6.0, UP_THROW, "in")
m.head.setdefault("trail", []).append([m.t(5.0), m.t(6.6)])
keyp(m, 9.0, P(UP_THROW, body=[-18, -6, 0]), "out")
keyp(m, 13.0, SUMMON, "inout")
keyp(m, 22.0, P(SUMMON, body=[-10, -14, 0], head=[-22, 0]))
keyp(m, 26.0, P(SUMMON, body=[18, -10, 0], head=[-4, 0], la=arm([-0.2, -0.7, 0.7], 10), root=[-0.16, 0.16]), "in")
keyp(m, 30.0, P(FR_EMPTY, body=[16, -10, 0]))
keyp(m, 38.0, PULL_REACH, "inout")
keyp(m, 39.4, P(PULL_REACH, body=[19, -12, 0]))
keyp(m, 40.6, P(PULL_REACH, body=[17, -16, 0]))
keyp(m, 42.0, PULL_YANK, "out")
keyp(m, 45.0, CATCH_REACH, "inout")
keyp(m, 46.2, CATCH_HIT, "out")
keyp(m, 49.4, P(FR_READY))
fpw(m, 3.0, FPW_LOW, "out3")
fpw(m, 6.0, FPW_SKY, "in")
fpw(m, 9.0, FPW_EMPTY, "out")
fpw(m, 38.0, dict(p=(0.05, 0.12, -0.45), blade=(0.15, 0.95, 0.2)), "inout")
fpw(m, 42.0, dict(p=(0.3, -0.1, 0.25), blade=(0.3, 0.9, -0.3)), "out")
fpw(m, 46.2, dict(p=(0.25, 0.25, 0.25), blade=(0.25, 0.9, -0.35)), "out")
fpw(m, 49.4, FPW_FR)
fpl(m, 3.0, FP_LOW, "out3")
fpl(m, 13.0, nudge(FP_JAB2, (0.0, 0.1, 0.0)), "inout")
fpl(m, 26.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 30.0, FP_GUARD)
fpl(m, 49.4, FP_GUARD)
impacts(m, 26.0, 46.0)
use_skill(m, "frostraider_ult")
