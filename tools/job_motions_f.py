"""0.51.0 2차 직업 스킬 동작 — 마검사 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신) · 반전 엘리멘탈 블레이드 (2026-11-07 소드 콜러 대신) — 재설계안 2판 (사용자 "이펙트가
전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음 동작이 뻣뻣했음" · "단타 애들이 너무 밋밋해 ... 커다란 검기가 나오면서 때리면"). 틱 0 = 스킬 (또는 단계 열쇠 —
서버가 그 순간에 다시 보냄: JobKit.motion) 이 나간 틱, 효과의 later 와 같은 틱 (ArcanebladeSkills · ElementalbladeSkills).

뻣뻣하지 않게 (job_motions_d 와 같은 기준): 치기 전에 반대쪽으로 크게 감고 낮아짐 → 치는 틱에 내디딤 → 지나쳐 멈췄다 되돌아옴 · 멈춘 자세도 숨 쉬듯 조금씩.
마검사는 몸 쓰는 직업이 아님: 아케인 블레이드 — 칼을 크게 감았다가 크게 휘두름 (맞는 것은 칼끝에서 나간 검기 · 마력 대검),
엘리멘탈 블레이드 — 칼에 원소를 깃들여 크게 감고 휘두름 (불꽃 베기 · 땅을 긁으며 내달림 · 번개 찌르기 · 칼날을 쓸어 올림 · 하늘의 다섯 칼을 부림)."""
import rune_motion_kit as K
from job_motions_a import PLANT, RISE_END, RISE_HIT
from job_motions_d import (DASH_R, FLICK, FPW_FLICK, FPW_REV_UP, FPW_ZAN, J_IMPACT, LUNGE_DEEP, REV_RAISE, ZAN2, carry_spin, diag, diag_back, first, impacts,
                           strike_with)
from rune_motion_kit import (A_PALM, A_POINT_UP, A_PULL, A_RAISE, A_SPREAD, FP_DOWN, FP_LOW, FP_PALM, FP_RAISE, FPL_GRIP2H, FPL_RAISE2H, FPW_DOWN, FPW_DRAWN,
                             FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, GRIP2H, LEGS_FIGHT, LEGS_LUNGE, LEGS_WIDE, RA_BACK_HIGH, RA_POINT, RA_SKY, RAISE2H,
                             W_READY, W_UP_FRONT, L, P, arm, bent, fpl, fpw, keyp, nudge, off_bare, spin_right, use_skill)

Motion = K.Motion

# 1인칭 왼손: 꽂은 칼 자루를 누름 · 왼손을 펼침 · 움켜쥠
FPL_PLANT = nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0))
FPL_PLANT_HOLD = nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0))
FPL_OPEN = ((0.0, 0.05, -0.4), (18, 55, 0))
FPL_GRASP = ((0.0, -0.08, -0.22), (10, 30, 0))

# ------------------------------------------------------------ 아케인 블레이드 (칼 · 대검 — 보라 · 청록 마력 검기, 크게 감고 크게 휘두름)
# 사용자 2026-11-05 "단타 애들이 너무 밋밋해 좀 더 화려하게 커다란 검기가 나오면서 때리면" (크게 감는 동작 + 커다란 검기 — 둘 다): 치기 전에 반대쪽 끝까지 감고
# 낮아졌다가 (예비 동작 — 그동안 칼끝에 빛이 모임) 치는 틱에 크게 내디디며 몸이 반대쪽 끝까지 돌아감

# 크레센트 엣지 (효과: 0 ~ 6 칼끝에 빛이 모임 → 6 가로 검기 (맞음 7) → 11 ~ 16 머리 위 칼끝에 다시 모임 → 16 세로 검기 (맞음 17)).
# 몸을 오른쪽 끝까지 비틀어 칼을 오른쪽 뒤로 크게 당겨 낮게 웅크렸다가, 크게 내디디며 오른쪽 → 왼쪽으로 끝까지 휩쓸고, 되돌려 두 손으로 머리 위로 들어 젖혔다가
# 크게 내디디며 세로로 내려 그어 낮게 버팀
CR_COIL = P(body=[14, 66, 0], head=[4, 0], ra=arm([0.72, 0.22, -0.45], 58, [0.55, 0.2, -0.8]), la=arm([0.35, -0.3, 0.7], 50), rl=[24, 0, 6], ll=[-30, 0, -6],
            rk=44, lk=52, root=[-0.28, -0.1])  # 칼을 오른쪽 뒤로 크게 당기고 몸을 오른쪽 끝까지 비틀어 낮게
CR_COIL2 = P(CR_COIL, body=[16, 76, 0], rk=50, lk=58, root=[-0.32, -0.12])  # 치기 직전 (가장 크게 감음)
CR_END = P(body=[14, -62, 0], head=[0, 0], ra=arm([-0.88, -0.12, 0.3], 14, [-0.6, -0.25, -0.75]), la=arm([0.6, -0.45, -0.4], 25), root=[-0.3, 0.5],
           **LUNGE_DEEP)  # 왼쪽 끝까지 휩쓸고 몸이 끝까지 돎 (왼손은 반대로 뒤로)
CR_RAISE = P(RAISE2H, body=[-18, 0, 0], head=[-24, 0], rl=[16, 0, 4], ll=[-24, 0, -4], rk=26, lk=34, root=[-0.1, 0.1])  # 두 손으로 머리 위로 들어 젖힘
CR_RAISE2 = P(CR_RAISE, body=[-24, 0, 0], head=[-28, 0], rk=32, lk=40, root=[-0.16, 0.06])
CR_LOW = P(PLANT, body=[40, 0, 0], head=[-8, 0], root=[-0.36, 0.4], **LUNGE_DEEP)  # 세로로 내려 그은 뒤 칼끝을 땅 가까이 두고 낮게 버팀
m = Motion("arcaneblade_crescent", 24, "크레센트 엣지 — 몸을 오른쪽 끝까지 비틀어 칼을 오른쪽 뒤로 크게 당겨 낮게 웅크렸다가, 크게 내디디며 오른쪽에서 왼쪽 끝까지 "
           "휩쓸고, 되돌려 두 손으로 머리 위로 들어 젖혔다가 크게 내디디며 세로로 내려 그어 낮게 버팀", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, CR_COIL, "out3")
keyp(m, 4.4, CR_COIL2, "in")
strike_with(m, "slash_h", 4.9, 6.0, 7.6, a=CR_COIL2, b=CR_END)
keyp(m, 9.4, P(CR_END, body=[12, -54, 0], root=[-0.26, 0.42]))
keyp(m, 12.0, CR_RAISE, "out3")
keyp(m, 14.4, CR_RAISE2, "in")
strike_with(m, "slam", 14.7, 16.0, 17.6, a=CR_RAISE2, b=CR_LOW)
keyp(m, 20.0, P(CR_LOW, body=[36, 2, 0], root=[-0.32, 0.36]))
keyp(m, 23.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.6, dict(p=(0.62, 0.38, 0.3), blade=(0.75, 0.35, -0.56)), "out3")
fpw(m, 4.4, dict(p=(0.72, 0.32, 0.38), blade=(0.72, 0.3, -0.62)), "in")
fpw(m, 9.4, dict(p=(-1.7, 0.28, 0.22), blade=(-0.85, 0.0, -0.52)))
fpw(m, 12.0, FPW_UP, "out3")
fpw(m, 14.4, FPW_SKY, "in")
fpw(m, 20.0, FPW_DOWN)
fpw(m, 23.5, FPW_LOW)
fpl(m, 1.6, ((0.1, -0.2, -0.05), (10, 40, 0)), "out3")
fpl(m, 6.0, FP_LOW, "linear")
fpl(m, 12.0, FPL_RAISE2H, "out3")
fpl(m, 16.0, FPL_PLANT, "in")
fpl(m, 21.0, FP_LOW)
impacts(m, 7.0, 17.0)
use_skill(m, "arcaneblade_crescent")

# 팬텀 크로스 (효과: 0 가장 가까운 적 앞으로 순간 이동 → 1 X 자로 두 번 그음 (새김) → 9 ~ 12 칼을 들어 → 12 털어 거둠 (검흔이 거대한 X 검기로 터짐)).
# 낮게 내달리는 자세로 나타나 오른쪽 위 → 왼쪽 아래 · 왼쪽 위 → 오른쪽 아래로 X 를 긋고, 베어 낸 자세로 멈췄다가 칼을 들어 오른쪽 아래로 털어 냄
m = Motion("arcaneblade_cross", 18, "팬텀 크로스 — 낮게 내달린 자세로 나타나 오른쪽 위에서 왼쪽 아래로, 이어 왼쪽 위에서 오른쪽 아래로 X 자로 그은 뒤 베어 낸 자세로 멈췄다가, "
           "칼을 들어 오른쪽 아래로 털어 거둠", fade=(0.02, 0.1))
off_bare(m)
keyp(m, 0.3, P(DASH_R, root=[-0.2, 0.34]), "out")
diag(m, 0.6, 1.2, 1.9)
diag_back(m, 2.0, 2.7, 3.8)
keyp(m, 6.0, P(ZAN2, body=[14, 50, 0], root=[-0.3, 0.48]))
keyp(m, 9.0, P(ZAN2, body=[10, 44, 0], ra=arm([0.62, 0.2, 0.5], 40, [0.45, 0.75, 0.45]), root=[-0.24, 0.42]), "inout")
keyp(m, 11.2, P(ZAN2, body=[8, 40, 0], ra=arm([0.6, 0.35, 0.45], 46, [0.35, 0.88, 0.3]), root=[-0.22, 0.4]), "in")
keyp(m, 12.0, FLICK, "linear")
keyp(m, 13.2, P(FLICK, body=[9, 32, 0]), "out")
keyp(m, 17.5, P(W_READY, body=[6, 4, 0]))
fpw(m, 0.3, dict(p=(0.4, -0.5, 0.3), blade=(0.45, -0.15, -0.88)), "out")
fpw(m, 6.0, FPW_ZAN)
fpw(m, 11.2, dict(p=(0.35, 0.05, 0.1), blade=(0.35, 0.85, 0.35)), "in")
fpw(m, 12.0, FPW_FLICK, "linear")
fpw(m, 17.5, FPW_LOW)
fpl(m, 0.3, FP_LOW, "out")
impacts(m, 1.2, 12.0)
use_skill(m, "arcaneblade_cross")

# 아케인 템페스트 (효과: 0 ~ 8 머리 위에서 칼을 돌려 모음 → 8 크게 한 바퀴 (원형 검기) → 8 ~ 20 마력 폭풍 → 20 터짐).
# 칼을 머리 위로 들어 두 바퀴 돌리며 일어섰다가, 낮게 웅크려 칼을 끝까지 뻗고 오른쪽으로 크게 한 바퀴 휘두른 뒤, 칼을 하늘로 치켜들고 왼손을 펼쳐 폭풍을
# 부리다가 칼을 앞 땅에 내리꽂아 터뜨림
TW_UP = P(body=[-8, 0, 0], head=[-20, 0], la=arm([-0.5, -0.3, 0.6], 30), **LEGS_WIDE)  # 칼을 머리 위로 (돌리는 동안 — 팔은 열쇠마다)
twirl = [(1.0, 0.12, 0.0), (0.0, 0.12, 1.0), (-1.0, 0.12, 0.0), (0.0, 0.12, -1.0)]
m = Motion("arcaneblade_tempest", 28, "아케인 템페스트 — 칼을 머리 위로 들어 두 바퀴 돌리며 일어섰다가, 낮게 웅크려 칼을 끝까지 뻗고 오른쪽으로 크게 한 바퀴 휘두르고, "
           "칼을 하늘로 치켜들고 왼손을 펼쳐 폭풍을 부리다가 앞 땅에 내리꽂아 터뜨림", fade=(0.03, 0.1))
off_bare(m)
for i in range(6):
    tk = 0.8 + i * 1.0
    bx, by, bz = twirl[i % 4]
    keyp(m, tk, P(TW_UP, ra=arm([0.15 + bx * 0.25, 0.92, 0.1 + bz * 0.25], 16, [bx, by, bz]), rk=12 + i * 3, lk=12 + i * 3), "out3" if i == 0 else "linear")
fpw(m, 0.8, FPW_SKY, "out3")
fpw(m, 5.8, FPW_SKY, dp=(0.05, 0.05, 0))
spin_right(m, [8.0], 10.4, wind_tick=6.6, fwd=P(K.SPIN_FWD_R, ra=arm([0.12, -0.1, 0.99], 6, [0.4, -0.15, 0.9]), rk=44, lk=44, root=[-0.26, 0]),
           end_pose=P(K.SPIN_END_R, rk=36, lk=36, root=[-0.2, 0]))
keyp(m, 12.4, P(body=[-12, 0, 0], head=[-24, 0], ra=RA_SKY, la=L(A_SPREAD), **LEGS_WIDE), "out3")
for i, tk in enumerate((14.6, 16.6)):
    sg = 1 if i % 2 else -1
    keyp(m, tk, P(body=[-14, sg * 3, 0], head=[-26, 0], ra=RA_SKY, la=L(A_SPREAD), rl=[0, 0, 12], ll=[0, 0, -12], rk=16 + 3 * i, lk=16 + 3 * i))
keyp(m, 18.8, P(REV_RAISE, body=[-16, 0, 0], root=[-0.04, 0.08]), "in")
keyp(m, 20.0, P(PLANT, body=[40, 0, 0], head=[-6, 0], root=[-0.38, 0.3], **LUNGE_DEEP), "linear")
keyp(m, 23.0, P(PLANT, body=[36, 0, 0], head=[-6, 0], root=[-0.34, 0.26], **LUNGE_DEEP))
keyp(m, 27.5, P(W_READY, body=[8, 6, 0]))
carry_spin(m, 10.4, -360)
fpw(m, 12.4, FPW_SKY, "out3")
fpw(m, 18.8, FPW_REV_UP, "in")
fpw(m, 20.0, FPW_DOWN, "linear")
fpw(m, 27.5, FPW_LOW)
fpl(m, 12.4, FP_RAISE, "out3")
fpl(m, 18.8, FPL_RAISE2H, "in")
fpl(m, 20.0, FPL_PLANT, "linear")
fpl(m, 25.0, FP_LOW)
impacts(m, 8.0, 20.0)
use_skill(m, "arcaneblade_tempest")

# 디멘션 슬래시 (효과: 0 ~ 6 오른쪽 뒤로 당긴 칼끝에 마력이 빨려 듦 → 6 허공을 가로로 가름 (앞 3.5칸에 차원의 틈 · 맞음) → 7 ~ 21 틈이 둘레의 적을 빨아들임
# → 단계 :close (22 — 틈이 닫히며 폭발)). 몸을 오른쪽 끝까지 비틀어 칼을 오른쪽 뒤로 크게 당겨 낮게 웅크렸다가, 크게 내디디며 오른쪽 → 왼쪽 끝까지 허공을
# 가르고, 칼을 왼쪽 아래에 둔 채 왼손을 틈으로 활짝 내밀어 끌어당김 (손이 조금씩 당겨지며 떨림)
RIFT_PALM = P(body=[10, -30, 0], head=[-4, 0], ra=arm([-0.55, -0.62, 0.3], 20, [-0.6, -0.45, -0.65]), la=L(A_PALM), rl=[20, 0, 6], ll=[-30, 0, -6], rk=26,
              lk=40, root=[-0.18, 0.34])  # 칼을 왼쪽 아래에 두고 왼손을 틈으로 활짝 내밂
RIFT_DRAW = P(RIFT_PALM, body=[8, -22, 0], la=L(arm([-0.1, 0.06, 0.95], 35, [0, 1, 0.1])), root=[-0.16, 0.26])  # 내민 손이 조금 당겨짐 (끌어당김)
m = Motion("arcaneblade_rift", 24, "디멘션 슬래시 — 몸을 오른쪽 끝까지 비틀어 칼을 오른쪽 뒤로 크게 당겨 낮게 웅크렸다가, 크게 내디디며 오른쪽에서 왼쪽 끝까지 허공을 "
           "가르고, 칼을 왼쪽 아래에 둔 채 왼손을 활짝 내밀어 틈 쪽으로 끌어당김", fade=(0.03, 0.06))
off_bare(m)
keyp(m, 1.6, P(CR_COIL, la=arm([0.3, -0.25, 0.75], 40)), "out3")
keyp(m, 4.4, P(CR_COIL2, la=arm([0.3, -0.25, 0.75], 40)), "in")
strike_with(m, "slash_h", 4.9, 6.0, 7.6, a=P(CR_COIL2, la=arm([0.3, -0.25, 0.75], 40)), b=CR_END)
keyp(m, 10.0, RIFT_PALM, "out3")
for i, tk in enumerate((12.0, 14.0, 16.0, 18.0, 20.0)):
    q = (i + 1) / 5
    keyp(m, tk, P(RIFT_PALM if i % 2 == 0 else RIFT_DRAW, body=[10 - 2 * q, -30 + 8 * q, 1.5 if i % 2 == 0 else -1.5]), "inout")
keyp(m, 22.0, RIFT_DRAW, "inout")
keyp(m, 23.6, RIFT_DRAW)
fpw(m, 1.6, dict(p=(0.62, 0.38, 0.3), blade=(0.75, 0.35, -0.56)), "out3")
fpw(m, 4.4, dict(p=(0.72, 0.32, 0.38), blade=(0.72, 0.3, -0.62)), "in")
fpw(m, 9.4, dict(p=(-1.7, 0.28, 0.22), blade=(-0.85, 0.0, -0.52)))
fpw(m, 12.0, FPW_LOW, "out3", dp=(-0.25, 0.0, 0.0))
fpw(m, 23.6, FPW_LOW, dp=(-0.25, 0.0, 0.0))
fpl(m, 1.6, ((0.1, -0.2, -0.05), (10, 40, 0)), "out3")
fpl(m, 6.0, FP_LOW, "linear")
fpl(m, 10.0, FPL_OPEN, "out3")
fpl(m, 16.0, nudge(FPL_OPEN, (0.0, 0.0, 0.06)), "inout")
fpl(m, 22.0, nudge(FPL_OPEN, (0.0, -0.02, 0.1)), "inout")
impacts(m, 7.0)
use_skill(m, "arcaneblade_rift")

# 닫음 (단계 :close — 0 = 틈이 닫히며 폭발: 0 ~ 1 내민 왼손을 움켜쥐며 허리로 확 당김 (몸이 오른쪽으로 돌아감) → 버팀 → 싸울 자세)
m = Motion("arcaneblade_rift_close", 12, "닫음 — 내민 왼손을 움켜쥐며 허리 뒤로 확 당겨 틈을 닫고, 몸이 오른쪽으로 돌아가며 칼을 아래로 턴 뒤 싸울 자세를 잡음", fade=(0.0, 0.1))
off_bare(m)
first(m, RIFT_DRAW, dict(FPW_LOW, p=(FPW_LOW["p"][0] - 0.25, FPW_LOW["p"][1], FPW_LOW["p"][2])))
keyp(m, 1.2, P(RIFT_DRAW, body=[12, 18, 0], head=[-6, 0], la=L(A_PULL), ra=arm([0.55, -0.72, 0.4], 26, [0.4, -0.88, 0.25]), root=[-0.24, 0.3]), "out")
keyp(m, 4.6, P(RIFT_DRAW, body=[14, 22, 0], head=[-4, 0], la=L(A_PULL), ra=arm([0.55, -0.72, 0.4], 26, [0.4, -0.88, 0.25]), root=[-0.26, 0.3]))
keyp(m, 11.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.2, FPW_FLICK, "out")
fpw(m, 4.6, FPW_FLICK)
fpw(m, 11.6, FPW_LOW)
fpl(m, 0.0, nudge(FPL_OPEN, (0.0, -0.02, 0.1)))
fpl(m, 1.2, FPL_GRASP, "out")
fpl(m, 3.0, nudge(FP_LOW, (0.0, 0.1, 0.0)), "in")
fpl(m, 11.6, FP_LOW)
impacts(m, 0.5)
use_skill(m, "arcaneblade_rift:close")

# 월드 클리버 (효과: 0 ~ 21 머리 위에서 하늘을 찌르는 대검으로 자라남 → 18 ~ 21 휘둘러 내려옴 → 21 :slam (내려쳐 가름) → 30 갈라진 땅이 솟구침).
# 두 손으로 칼을 쥐고 웅크렸다가 하늘로 곧게 치켜들고 몸을 크게 젖혀 버티며 (떨림 — 점점 더 젖혀짐), 크게 내디디며 앞으로 내려침
WC_SKY = P(RAISE2H, body=[-16, 0, 0], head=[-28, 0], rl=[0, 0, 14], ll=[0, 0, -14], rk=20, lk=20, root=[-0.06, 0.0])  # 하늘로 곧게 치켜듦
m = Motion("arcaneblade_ult", 22, "월드 클리버 — 두 손으로 칼을 쥐고 웅크렸다가 하늘로 곧게 치켜들고, 몸을 크게 젖혀 떨리며 버티다가 (점점 더 젖혀짐) 크게 내디디며 앞으로 "
           "내려침", fade=(0.04, 0.0))
off_bare(m)
keyp(m, 1.6, P(GRIP2H, body=[18, 0, 0], head=[-4, 0], rk=46, lk=46, root=[-0.24, 0.0]), "out3")
keyp(m, 4.4, WC_SKY, "out")
for i, tk in enumerate((7.0, 10.0, 13.0, 16.0)):
    sg = 1 if i % 2 else -1
    q = (i + 1) / 4
    keyp(m, tk, P(WC_SKY, body=[-16 - 8 * q, sg * 2, 0], head=[-28 - 4 * q, 0], rk=20 + 10 * q, lk=20 + 10 * q, root=[-0.06 - 0.08 * q, -0.04 * q]))
strike_with(m, "slam", 18.2, 20.6, 21.8, a=P(WC_SKY, body=[-26, 0, 0], head=[-32, 0], rk=30, lk=30, root=[-0.14, -0.04]), b=J_IMPACT)
fpw(m, 1.6, FPW_FWD, "out3")
fpw(m, 4.4, FPW_SKY, "out")
fpw(m, 16.0, FPW_SKY, dp=(0, 0.05, 0.05))
fpl(m, 1.6, FPL_GRIP2H, "out3")
fpl(m, 4.4, FPL_RAISE2H, "out")
fpl(m, 21.0, FPL_PLANT, "in")
use_skill(m, "arcaneblade_ult")
# 내려쳐 가름 (단계 :slam — 21 · 거대한 대검이 앞 땅을 가름 → 9 (효과 30) 에 갈라진 땅이 솟구침): 내리친 자세로 깊이 웅크려 칼을 누르며 버티다가, 솟구칠 때 칼을
# 끌어올리며 일어섬
m = Motion("arcaneblade_ult_slam", 18, "월드 클리버 내려침 — 앞 땅을 내리친 자세로 깊이 웅크려 두 손으로 칼을 누르며 버티다가, 땅이 솟구칠 때 칼을 끌어올리며 일어섬",
           fade=(0.0, 0.1))
off_bare(m)
first(m, J_IMPACT, dict(FPW_DOWN))
keyp(m, 1.2, P(J_IMPACT, body=[44, 0, 0], rk=82, lk=50, root=[-0.42, 0.3]), "out")
keyp(m, 4.5, P(J_IMPACT, body=[42, 2, 0], root=[-0.4, 0.3]))
keyp(m, 7.5, P(J_IMPACT, body=[43, -2, 0], rk=80, lk=50, root=[-0.41, 0.3]))
keyp(m, 9.0, P(RISE_HIT, root=[0.02, 0.22]), "out")
keyp(m, 11.0, RISE_END, "out")
keyp(m, 17.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 7.5, FPW_DOWN, dp=(0, -0.04, 0))
fpw(m, 9.0, FPW_UP, "out")
fpw(m, 17.5, FPW_LOW)
fpl(m, 0.0, FPL_PLANT)
fpl(m, 7.5, FPL_PLANT_HOLD)
fpl(m, 9.5, FP_LOW, "out")
impacts(m, 0.0, 9.0)
use_skill(m, "arcaneblade_ult:slam")

# ------------------------------------------------------------ 엘리멘탈 블레이드 (칼 · 대검 — 스킬마다 원소 빛: 불 · 바위 · 전기 · 물과 얼음 · 다섯, 2026-11-07 소드 콜러 대신)
# 몸 쓰는 직업이 아님 (사용자 "몸을 쓰는 직업만 몸을 움직이고") — 원소를 칼에 깃들여 크게 감고 크게 휘두름 (맞는 것은 칼과 칼빛 · 원소)

# 플레어 엣지 (효과: 0 ~ 5 오른쪽 뒤로 감은 칼날에 불꽃이 감김 → 5 앞을 가로로 크게 벰 (불꽃 초승달 검기) → 다시 누르면 :spin).
# 몸을 오른쪽 끝까지 비틀어 칼을 오른쪽 뒤로 당겨 낮게 웅크렸다가 크게 내디디며 오른쪽 → 왼쪽 끝까지 휩쓸고 베어 낸 자세로 멈춤
FL_COIL = P(CR_COIL, la=arm([0.3, -0.25, 0.75], 40))
FL_COIL2 = P(CR_COIL2, la=arm([0.3, -0.25, 0.75], 40))
m = Motion("elementalblade_flare", 15, "플레어 엣지 — 몸을 오른쪽 끝까지 비틀어 불꽃이 감기는 칼을 오른쪽 뒤로 당겨 낮게 웅크렸다가, 크게 내디디며 오른쪽에서 왼쪽 끝까지 "
           "가로로 휩쓸고 베어 낸 자세로 멈춤", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.4, FL_COIL, "out3")
keyp(m, 3.8, FL_COIL2, "in")
strike_with(m, "slash_h", 4.0, 5.0, 6.6, a=FL_COIL2, b=CR_END)
keyp(m, 9.0, P(CR_END, body=[12, -54, 0], root=[-0.26, 0.42]))
keyp(m, 14.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.4, dict(p=(0.62, 0.38, 0.3), blade=(0.75, 0.35, -0.56)), "out3")
fpw(m, 3.8, dict(p=(0.72, 0.32, 0.38), blade=(0.72, 0.3, -0.62)), "in")
fpw(m, 9.0, dict(p=(-1.7, 0.28, 0.22), blade=(-0.85, 0.0, -0.52)))
fpw(m, 14.5, FPW_LOW)
fpl(m, 1.4, ((0.1, -0.2, -0.05), (10, 40, 0)), "out3")
fpl(m, 5.0, FP_LOW, "linear")
impacts(m, 5.0)
use_skill(m, "elementalblade_flare")
# 불꽃 회오리 (단계 :spin — 0 ~ 4 베어 낸 자세에서 몸을 크게 돌려 → 4 한 바퀴 (둘레를 벰) → 멈춤)
m = Motion("elementalblade_flare_spin", 13, "불꽃 회오리 — 베어 낸 자세에서 낮게 웅크려 칼을 끝까지 뻗고 오른쪽으로 크게 한 바퀴 휘둘러 둘레를 벤 뒤 낮게 버팀",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(CR_END, body=[12, -54, 0], root=[-0.26, 0.42]), dict(FPW_LOW))
spin_right(m, [4.0], 6.6, wind_tick=2.4, fwd=P(K.SPIN_FWD_R, ra=arm([0.12, -0.1, 0.99], 6, [0.4, -0.15, 0.9]), rk=46, lk=46, root=[-0.28, 0]),
           end_pose=P(K.SPIN_END_R, rk=38, lk=38, root=[-0.22, 0]))
keyp(m, 12.5, P(W_READY, body=[8, 6, 0], spin=-360))
carry_spin(m, 6.6, -360)
fpw(m, 12.5, FPW_LOW)
impacts(m, 4.0)
use_skill(m, "elementalblade_flare:spin")

# 어스 서지 (효과: 0 ~ 6 앞으로 미끄러지듯 내달림 (칼끝이 땅을 긁음 · 받는 피해 −30%) → 7 갈라진 길에서 바위 가시가 솟음).
# 칼끝을 땅에 대고 낮게 웅크린 채 미끄러져 내달리다가, 멈추는 순간 칼을 땅에서 확 쳐올려 가시를 솟게 하고 일어섬
SURGE_LOW = P(body=[34, 18, 0], head=[-18, 0], ra=arm([0.4, -0.72, 0.56], 18, [0.3, -0.85, 0.42]), la=arm([-0.55, -0.45, -0.5], 25), rl=[30, 0, 6],
              ll=[-36, 0, -6], rk=62, lk=40, root=[-0.4, 0.3])  # 칼끝을 오른쪽 앞 땅에 대고 낮게 웅크려 미끄러짐 (왼팔은 뒤로 — 균형)
SURGE_UP = P(body=[-10, 10, 0], head=[-16, 0], ra=arm([0.45, 0.82, 0.35], 10, [0.25, 0.95, 0.2]), la=L(A_SPREAD), rl=[0, 0, 12], ll=[0, 0, -12], rk=16, lk=16,
             root=[-0.06, 0.1])  # 칼을 땅에서 확 쳐올려 일어섬
m = Motion("elementalblade_surge", 17, "어스 서지 — 칼끝을 오른쪽 앞 땅에 대고 낮게 웅크린 채 미끄러지듯 내달리다가, 멈추는 순간 칼을 땅에서 확 쳐올려 바위 가시를 "
           "솟게 하며 일어섬", fade=(0.02, 0.1))
off_bare(m)
keyp(m, 1.0, SURGE_LOW, "out")
keyp(m, 3.5, P(SURGE_LOW, body=[36, 16, 0], root=[-0.42, 0.32]))
keyp(m, 5.8, P(SURGE_LOW, body=[38, 14, 0], rk=70, lk=46, root=[-0.46, 0.26]), "in")
keyp(m, 7.0, SURGE_UP, "out")
keyp(m, 10.0, P(SURGE_UP, body=[-6, 8, 0], root=[-0.04, 0.08]))
keyp(m, 16.5, P(W_READY, body=[8, 6, 0]))
m.arc(7.0, "ra", (0.1, 1, 0.3))
fpw(m, 1.0, dict(p=(0.35, -0.6, 0.0), blade=(0.25, -0.75, 0.6)), "out")
fpw(m, 5.8, dict(p=(0.35, -0.65, -0.05), blade=(0.25, -0.8, 0.55)))
fpw(m, 7.0, FPW_UP, "out")
fpw(m, 16.5, FPW_LOW)
fpl(m, 1.0, FP_LOW, "out")
impacts(m, 7.0)
use_skill(m, "elementalblade_surge")

# 볼트 피어스 (효과: 0 ~ 8 뒤로 당긴 칼끝에 번개가 모임 → 8 겨눈 적을 번개 창처럼 찌름 → 18 · 28 · 38 남은 번개가 내리꽂힘).
# 칼을 오른쪽 뒤로 수평으로 당겨 칼끝을 겨누고 몸을 낮췄다가, 크게 내디디며 곧게 찔러 끝까지 뻗고, 찌른 자세로 버티다 왼손을 들어 하늘의 번개를 부름
BOLT_DRAW = P(body=[6, -34, 0], head=[4, 0], ra=arm([0.62, 0.05, -0.78], 92, [0.0, 0.05, 1.0]), la=arm([-0.05, 0.2, 0.98], 10, [0, 0.4, 1]), rl=[20, 0, 6],
              ll=[-26, 0, -6], rk=36, lk=30, root=[-0.2, -0.12])  # 칼을 오른쪽 뒤로 수평으로 당겨 칼끝이 앞 (왼손은 겨눔)
BOLT_DRAW2 = P(BOLT_DRAW, body=[8, -40, 0], rk=42, lk=36, root=[-0.24, -0.16])
BOLT_THRUST = P(body=[14, 16, 0], head=[2, 0], ra=arm([-0.04, 0.06, 1], 0, [0, 0.05, 1]), la=arm([-0.6, -0.35, -0.6], 20), root=[-0.2, 0.6],
                **LUNGE_DEEP)  # 크게 내디디며 곧게 찔러 끝까지 뻗음 (왼팔은 뒤로)
m = Motion("elementalblade_bolt", 24, "볼트 피어스 — 칼을 오른쪽 뒤로 수평으로 당겨 번개가 모이는 칼끝을 겨누고 몸을 낮췄다가, 크게 내디디며 곧게 찔러 끝까지 뻗고, "
           "찌른 자세로 버티며 왼손을 들어 하늘의 번개를 부름", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, BOLT_DRAW, "out3")
keyp(m, 6.6, BOLT_DRAW2, "in")
keyp(m, 8.0, BOLT_THRUST, "linear")
keyp(m, 10.5, P(BOLT_THRUST, body=[12, 14, 0], root=[-0.18, 0.56]), "out")
keyp(m, 14.0, P(BOLT_THRUST, body=[8, 10, 0], la=L(A_RAISE), root=[-0.14, 0.48]), "out3")
keyp(m, 18.0, P(BOLT_THRUST, body=[6, 8, 0], la=L(A_RAISE), root=[-0.12, 0.44]))
keyp(m, 23.5, P(W_READY, body=[8, 6, 0]))
m.arc(8.0, "ra", (0, 0.05, 1))
fpw(m, 1.6, dict(p=(0.55, 0.15, 0.5), blade=(-0.05, 0.05, 1)), "out3")
fpw(m, 6.6, dict(p=(0.6, 0.12, 0.6), blade=(-0.05, 0.05, 1)), "in")
fpw(m, 8.0, FPW_FWD, "linear")
fpw(m, 18.0, FPW_FWD, dp=(0, -0.04, 0.06))
fpw(m, 23.5, FPW_LOW)
fpl(m, 1.6, FP_PALM, "out3")
fpl(m, 8.0, FP_LOW, "linear")
fpl(m, 14.0, FP_RAISE, "out3")
fpl(m, 20.0, FP_LOW)
impacts(m, 8.0)
use_skill(m, "elementalblade_bolt")

# 프로스트타이드 (효과: 0 칼에 물과 얼음이 소용돌이침 — 8초 태세). 칼을 얼굴 앞에 곧게 세워 왼손 두 손가락으로 칼날을 쓸어 올린 뒤, 칼을 오른쪽 아래로 털어 내림
m = Motion("elementalblade_tide", 15, "프로스트타이드 — 칼을 얼굴 앞에 곧게 세우고 왼손으로 칼날을 아래에서 위로 쓸어 올려 물과 서리를 깃들인 뒤, 칼을 오른쪽 아래로 털어 내림",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.5, P(W_UP_FRONT, la=bent([0.05, 0.1, 0.95], 95, (-0.3, 1, 0))), "out3")
keyp(m, 4.0, P(W_UP_FRONT, body=[-6, 0, 0], head=[-8, 0], la=bent([0.05, 0.42, 0.9], 70, (-0.3, 1, 0))), "inout")
keyp(m, 6.0, P(W_UP_FRONT, body=[-8, 0, 0], head=[-10, 0], la=arm([0.1, 0.75, 0.62], 20)), "out")
keyp(m, 8.4, P(FLICK, body=[6, 20, 0], la=arm([-0.5, -0.6, 0.2], 30), root=[-0.14, 0.2]), "out")
keyp(m, 14.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.5, FPW_UP, "out3")
fpw(m, 6.0, FPW_UP, dp=(0, 0.06, 0))
fpw(m, 8.4, FPW_FLICK, "out")
fpw(m, 14.5, FPW_LOW)
fpl(m, 1.5, FPL_GRIP2H, "out3")
fpl(m, 6.0, nudge(FP_RAISE, (-0.15, -0.1, 0), (0, 10, 0)), "inout")
fpl(m, 9.0, FP_LOW, "out")
use_skill(m, "elementalblade_tide")

# 엘리멘탈 칼라미티 (효과: 0 ~ 6 칼끝에서 다섯 빛줄기가 하늘로 → 6 하늘에 다섯 원소의 칼 → 18 ~ 58 차례로 내리꽂힘 → 66 :final).
# 칼과 왼손을 하늘로 들어 다섯 칼을 부르고, 칼끝으로 겨눈 적을 곧게 가리킨 뒤 왼손을 펼쳐 쥐었다 폈다 하며 칼을 하나씩 떨어뜨림
ULT_POINT = P(P(body=[14, 14, 0], head=[4, 0], ra=RA_POINT, la=arm([-0.55, -0.6, -0.15], 30), **LEGS_LUNGE), root=[-0.06, 0.22])  # 칼끝으로 겨눈 적을 가리킴
ULT_GRASP = bent([-0.25, -0.1, 0.9], 110, (0.3, 1, 0.2))  # 내민 왼손을 움켜쥠
ULT_HOLD = P(ULT_POINT, body=[10, 6, 0], la=arm([-0.5, -0.6, 0.2], 30), root=[-0.04, 0.18])
m = Motion("elementalblade_ult", 30, "엘리멘탈 칼라미티 — 칼과 왼손을 하늘로 들어 다섯 원소의 칼을 부르고, 칼끝으로 겨눈 적을 곧게 가리킨 뒤 왼손을 펼쳤다 움켜쥐며 칼을 "
           "하나씩 떨어뜨림", fade=(0.04, 0.08))
off_bare(m)
keyp(m, 2.5, P(body=[-12, 0, 0], head=[-26, 0], ra=RA_SKY, la=L(A_RAISE), **LEGS_WIDE), "out3")
keyp(m, 5.5, P(body=[-14, 0, 0], head=[-28, 0], ra=RA_SKY, la=L(A_RAISE), rl=[0, 0, 12], ll=[0, 0, -12], rk=18, lk=18))
keyp(m, 9.0, P(ULT_POINT, la=L(A_POINT_UP)), "out")
keyp(m, 13.0, P(ULT_POINT, body=[12, 8, 0], la=L(A_PALM), root=[-0.05, 0.2]), "out3")
keyp(m, 18.0, P(ULT_POINT, body=[12, 6, 0], la=ULT_GRASP, root=[-0.05, 0.2]), "in")
keyp(m, 23.0, P(ULT_POINT, body=[12, 8, 0], la=L(A_PALM), root=[-0.05, 0.2]), "out")
keyp(m, 28.0, P(ULT_POINT, body=[12, 6, 0], la=ULT_GRASP, root=[-0.05, 0.2]), "in")
keyp(m, 29.5, ULT_HOLD)
fpw(m, 2.5, FPW_SKY, "out3")
fpw(m, 9.0, FPW_FWD, "out")
fpw(m, 29.5, FPW_FWD, dp=(0, -0.04, 0.05))
fpl(m, 2.5, FP_RAISE, "out3")
fpl(m, 9.0, FP_LOW, "out")
fpl(m, 13.0, FPL_OPEN, "out3")
fpl(m, 18.0, FPL_GRASP, "in")
fpl(m, 23.0, FPL_OPEN, "out")
fpl(m, 28.0, FPL_GRASP, "in")
use_skill(m, "elementalblade_ult")
# 내리찍음 (단계 :final — 0 ~ 3 두 손으로 칼을 머리 위로 → 4 크게 내디디며 내려침 (합쳐진 대검이 적에게 꽂힘) → 낮게 버팀)
m = Motion("elementalblade_ult_final", 18, "엘리멘탈 칼라미티 끝 — 두 손으로 칼을 머리 위로 크게 치켜들어 젖혔다가, 크게 내디디며 앞으로 내려쳐 합쳐진 대검을 꽂고 낮게 "
           "버팀", fade=(0.02, 0.1))
off_bare(m)
first(m, ULT_HOLD, dict(FPW_FWD))
keyp(m, 2.2, P(RAISE2H, body=[-20, 0, 0], head=[-26, 0], rl=[16, 0, 4], ll=[-24, 0, -4], rk=30, lk=36, root=[-0.12, 0.08]), "out3")
strike_with(m, "slam", 2.6, 4.0, 5.6, a=P(RAISE2H, body=[-24, 0, 0], head=[-28, 0], rk=32, lk=40, root=[-0.16, 0.06]), b=J_IMPACT)
keyp(m, 9.5, P(J_IMPACT, body=[40, 0, 0], root=[-0.4, 0.3]))
keyp(m, 12.0, P(RISE_HIT, root=[0.02, 0.22]), "out")
keyp(m, 17.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 2.2, FPW_SKY, "out3")
fpw(m, 9.5, FPW_DOWN)
fpw(m, 17.5, FPW_LOW)
fpl(m, 2.2, FPL_RAISE2H, "out3")
fpl(m, 4.0, FPL_PLANT, "in")
fpl(m, 12.0, FP_LOW, "out")
impacts(m, 4.0)
use_skill(m, "elementalblade_ult:final")
