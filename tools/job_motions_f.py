"""0.51.0 2차 직업 스킬 동작 — 마검사 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신) · 반전 소드 콜러 — 재설계안 2판 (사용자 "이펙트가
전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음 동작이 뻣뻣했음" · "단타 애들이 너무 밋밋해 ... 커다란 검기가 나오면서 때리면"). 틱 0 = 스킬 (또는 단계 열쇠 —
서버가 그 순간에 다시 보냄: JobKit.motion) 이 나간 틱, 효과의 later 와 같은 틱 (ArcanebladeSkills · SwordcallerSkills).

뻣뻣하지 않게 (job_motions_d 와 같은 기준): 치기 전에 반대쪽으로 크게 감고 낮아짐 → 치는 틱에 내디딤 → 지나쳐 멈췄다 되돌아옴 · 멈춘 자세도 숨 쉬듯 조금씩.
마검사는 몸 쓰는 직업이 아님: 아케인 블레이드 — 칼을 크게 감았다가 크게 휘두름 (맞는 것은 칼끝에서 나간 검기 · 마력 대검),
소드 콜러 — 칼이 손을 떠나 스스로 싸우므로 내 동작은 칼에게 내리는 명령 (칼끝으로 겨눔 · 왼손 손짓 · 돌아오는 칼을 받음)."""
import rune_motion_kit as K
from job_motions_a import PLANT, RISE_END, RISE_HIT
from job_motions_d import (DASH_R, FLICK, FPW_FLICK, FPW_REV_UP, FPW_ZAN, J_IMPACT, LUNGE_DEEP, REV_RAISE, ZAN2, carry_spin, diag, diag_back, first, impacts,
                           strike_with)
from rune_motion_kit import (A_PALM, A_POINT_UP, A_RAISE, A_SPREAD, FP_DOWN, FP_LOW, FP_PALM, FP_RAISE, FPL_GRIP2H, FPL_RAISE2H, FPW_DOWN, FPW_DRAWN,
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

# 스펠 오버드라이브 (효과: 0 ~ 8 칼끝으로 빛이 모임 · 4 ~ 8 칼날을 따라 빛이 흐름 → 9 켜짐 (칼이 커지며 빛남)). 칼을 얼굴 앞에 곧게 세우고 왼손을 칼끝에 댔다가
# 칼날을 따라 쓸어 내린 뒤, 칼을 오른쪽 바깥으로 크게 털어 휘두르며 한 걸음 내디딤
OD_HOLD = P(W_UP_FRONT, la=bent([0.05, 0.55, 0.85], 60, (-0.3, 1, 0)))  # 칼을 얼굴 앞에 세우고 왼손을 칼끝 가까이
m = Motion("arcaneblade_overdrive", 16, "스펠 오버드라이브 — 칼을 얼굴 앞에 곧게 세우고 왼손을 칼끝에 댔다가 칼날을 따라 쓸어 내린 뒤, 칼을 오른쪽 바깥으로 크게 털어 "
           "휘두르며 한 걸음 내디딤", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.6, OD_HOLD, "out3")
keyp(m, 4.0, P(OD_HOLD, body=[-8, 0, 0], head=[-10, 0]))
keyp(m, 8.0, P(W_UP_FRONT, body=[-6, 0, 0], head=[-8, 0], la=bent([0.08, 0.05, 0.9], 70, (-0.3, 1, 0))), "inout")
keyp(m, 9.2, P(P(body=[6, 34, 0], head=[-4, 0], ra=arm([0.95, 0.05, 0.25], 6, [0.75, 0.25, 0.6]), la=arm([-0.6, -0.5, 0.3], 30), **LEGS_LUNGE),
               root=[-0.1, 0.26]), "out")
keyp(m, 12.0, P(P(body=[6, 28, 0], head=[-4, 0], ra=arm([0.92, 0.05, 0.3], 8, [0.72, 0.3, 0.6]), la=arm([-0.6, -0.5, 0.3], 30), **LEGS_LUNGE),
                root=[-0.08, 0.2]))
keyp(m, 15.5, P(W_READY, body=[8, 6, 0]))
m.arc(9.2, "ra", (0.4, 0.1, 1))
m.head.setdefault("trail", []).append([m.t(8.4), m.t(10.4)])
m.set(tilt=10)
fpw(m, 1.6, FPW_UP, "out3")
fpw(m, 8.0, FPW_UP, dp=(0, -0.05, 0))
fpw(m, 9.2, FPW_SPREAD, "out")
fpw(m, 15.5, FPW_LOW)
fpl(m, 1.6, nudge(FP_PALM, (-0.1, 0.25, 0.05), (20, 20, 0)), "out3")
fpl(m, 8.0, nudge(FP_PALM, (-0.1, -0.15, 0.05), (10, 20, 0)), "inout")
fpl(m, 9.2, FP_LOW, "out")
use_skill(m, "arcaneblade_overdrive")

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

# ------------------------------------------------------------ 소드 콜러 (칼 — 청록 · 흰빛 마력 칼, 칼에게 내리는 명령)

# 칼끝을 귀 옆 뒤로 당겼다가 (명령 준비) 겨눈 곳으로 곧게 내밀어 가리킴
CALL_BACK = P(body=[2, -22, 0], head=[2, 0], ra=RA_BACK_HIGH, la=arm([-0.4, -0.5, 0.45], 30), **LEGS_FIGHT)
CALL_POINT = P(P(body=[14, 14, 0], head=[4, 0], ra=RA_POINT, la=arm([-0.55, -0.6, -0.15], 30), **LEGS_LUNGE), root=[-0.06, 0.22])

# 베어라 (효과: 2 에 칼이 손을 떠나 겨눈 곳으로 → 닿아 맴돌며 벰 → 10틱 뒤 두 번 더 → 땅에 꽂힘)
m = Motion("swordcaller_strike", 13, "베어라 — 칼끝을 귀 옆 뒤로 당겼다가 겨눈 곳을 곧게 가리켜 칼을 보내고, 왼손을 펼쳐 내밀어 칼을 부림", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.0, CALL_BACK, "out3")
keyp(m, 2.2, CALL_POINT, "out")
keyp(m, 4.5, P(CALL_POINT, body=[12, 4, 0], la=L(A_PALM), root=[-0.05, 0.18]), "out3")
keyp(m, 8.0, P(CALL_POINT, body=[10, 2, 0], la=L(A_PALM), root=[-0.04, 0.16]))
keyp(m, 12.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, FPW_DRAWN, "out3")
fpw(m, 2.2, FPW_FWD, "out")
fpw(m, 8.0, FPW_FWD, dp=(0, -0.03, 0.05))
fpw(m, 12.5, FPW_LOW)
fpl(m, 4.5, FPL_OPEN, "out3")
fpl(m, 11.0, FP_LOW)
use_skill(m, "swordcaller_strike")

# 데려가라 (효과: 1 부터 꽂힌 칼 쪽 (또는 앞 8칸) 으로 끌려 날아감 · 지나간 적을 벰 → 닿으면 칼을 뽑아 쥠). 왼손을 앞으로 뻗어 끌려가며 몸이 앞으로 기울고
# 칼은 뒤로 끌림 → 내려서며 미끄러짐
PULLED = P(body=[30, 8, 0], head=[-14, 0], ra=arm([0.5, -0.45, -0.72], 20, [0.35, -0.2, -0.92]), la=arm([-0.04, 0.1, 1], 0, [0, 0.3, 1]), rl=[-24, 0, 6],
           ll=[34, 0, -6], rk=60, lk=22, root=[-0.12, 0.3])
m = Motion("swordcaller_shift", 14, "데려가라 — 왼손을 칼 쪽으로 곧게 뻗어 끌려가듯 몸을 앞으로 기울여 날아가고 (칼은 뒤로 끌림), 내려서며 낮게 미끄러져 칼을 고쳐 쥠",
           fade=(0.02, 0.1))
off_bare(m)
keyp(m, 1.0, PULLED, "out3")
keyp(m, 6.0, P(PULLED, body=[34, 6, 0], root=[-0.14, 0.32]))
keyp(m, 8.5, P(body=[22, -16, 0], head=[0, 0], ra=arm([0.55, -0.5, 0.55], 20, [0.6, -0.4, 0.7]), la=arm([-0.5, -0.7, 0.2], 20), rl=[30, 0, 8], ll=[-30, 0, -8],
                rk=66, lk=58, root=[-0.36, 0.0]), "out")
keyp(m, 13.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, dict(p=(0.35, -0.55, 0.35), blade=(0.4, -0.2, -0.9)), "out3")
fpw(m, 8.5, FPW_SPREAD, "out")
fpw(m, 13.5, FPW_LOW)
fpl(m, 1.0, FP_PALM, "out3")
fpl(m, 8.5, FP_LOW, "out")
use_skill(m, "swordcaller_shift")

# 꿰뚫고 맴돌아라 (효과: 2 에 칼이 날아가 꿰뚫음 → 10틱마다 그 적 둘레를 돌며 벰 넷 → 손으로 돌아옴). 겨눠 보낸 뒤 왼손 검지로 허공에 원을 그리며 칼을 돌림
m = Motion("swordcaller_orbit", 24, "꿰뚫고 맴돌아라 — 칼끝을 당겼다가 곧게 내밀어 칼을 꿰뚫게 보내고, 왼손을 들어 검지로 허공에 원을 크게 두 번 그리며 칼을 돌림",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.0, CALL_BACK, "out3")
keyp(m, 2.2, CALL_POINT, "out")
circle = [(0.35, 0.45), (0.05, 0.75), (-0.25, 0.45), (0.05, 0.15)]
for i in range(8):
    tk = 5.0 + i * 2.2
    cxy = circle[i % 4]
    keyp(m, tk, P(CALL_POINT, body=[10, 6, 0], la=arm([-0.25 + cxy[0] * 0.6, 0.1 + cxy[1] * 0.6, 0.9], 6, [0, 1, 0.2]), root=[-0.05, 0.16]),
         "inout" if i else "out3")
keyp(m, 23.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, FPW_DRAWN, "out3")
fpw(m, 2.2, FPW_FWD, "out")
fpw(m, 12.0, FPW_FWD, dp=(0, -0.05, 0.08))
fpw(m, 23.5, FPW_LOW)
fpl(m, 5.0, nudge(FP_PALM, (0.05, 0.12, 0), (30, 30, 0)), "out3")
for i in range(1, 8):
    cxy = circle[i % 4]
    fpl(m, 5.0 + i * 2.2, nudge(FP_PALM, (cxy[0] * 0.25, cxy[1] * 0.25 - 0.05, 0), (30, 30, 0)), "inout")
fpl(m, 23.0, FP_LOW)
use_skill(m, "swordcaller_orbit")

# 둘로 나뉘어라 (효과: 3 에 칼이 번쩍이며 둘로 갈라져 양옆으로 → 6 부터 칼 둘이 몸 둘레를 돎). 칼을 얼굴 앞에 세웠다가 칼과 왼손을 양옆으로 펼쳐 가름
m = Motion("swordcaller_twin", 14, "둘로 나뉘어라 — 칼을 얼굴 앞에 곧게 세워 숨을 고른 뒤, 칼을 오른쪽 · 왼손을 왼쪽으로 크게 펼쳐 칼을 둘로 가름", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.5, P(W_UP_FRONT, la=bent([0.05, 0.3, 0.95], 95, (-0.3, 1, 0))), "out3")
keyp(m, 3.0, P(W_UP_FRONT, body=[-6, 0, 0], head=[-8, 0], la=bent([0.05, 0.32, 0.95], 95, (-0.3, 1, 0))))
keyp(m, 4.6, P(body=[-6, 0, 0], head=[-10, 0], ra=arm([0.95, 0.25, 0.2], 6, [0.7, 0.55, 0.45]), la=L(A_SPREAD), **LEGS_WIDE), "out")
keyp(m, 9.0, P(body=[-4, 0, 0], head=[-8, 0], ra=arm([0.92, 0.2, 0.3], 8, [0.7, 0.5, 0.5]), la=L(A_SPREAD), **LEGS_WIDE))
keyp(m, 13.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.5, FPW_UP, "out3")
fpw(m, 4.6, FPW_SPREAD, "out")
fpw(m, 13.5, FPW_LOW)
fpl(m, 1.5, FPL_GRIP2H, "out3")
fpl(m, 4.6, nudge(FP_PALM, (-0.25, 0.05, 0.1), (10, -10, 0)), "out")
fpl(m, 13.0, FP_LOW)
use_skill(m, "swordcaller_twin")

# 칼날 감옥 (효과: 0 ~ 6 등 뒤에 칼 여섯이 솟음 → 6 에 겨눈 적에게 날아가 10 에 둘러쌈 → 10틱마다 벰 여섯 → 67 에 :catch). 칼과 왼손을 하늘로 들어 칼들을
# 부르고 → 칼끝으로 적을 겨눠 가둠 → 왼손을 펼쳐 쥐었다 폈다 하며 칼들을 부림
m = Motion("swordcaller_ult", 30, "칼날 감옥 — 칼과 왼손을 하늘로 들어 칼 여섯을 부르고, 칼끝으로 겨눈 적을 곧게 가리켜 가둔 뒤 왼손을 펼쳤다 움켜쥐며 칼들을 부림",
           fade=(0.04, 0.08))
off_bare(m)
keyp(m, 2.5, P(body=[-12, 0, 0], head=[-26, 0], ra=RA_SKY, la=L(A_RAISE), **LEGS_WIDE), "out3")
keyp(m, 5.5, P(body=[-14, 0, 0], head=[-28, 0], ra=RA_SKY, la=L(A_RAISE), rl=[0, 0, 12], ll=[0, 0, -12], rk=18, lk=18))
keyp(m, 8.0, P(CALL_POINT, la=L(A_POINT_UP)), "out")
keyp(m, 12.0, P(CALL_POINT, body=[12, 8, 0], la=L(A_PALM)), "out3")
keyp(m, 18.0, P(CALL_POINT, body=[12, 6, 0], la=bent([-0.25, -0.1, 0.9], 110, (0.3, 1, 0.2))), "in")
keyp(m, 24.0, P(CALL_POINT, body=[12, 8, 0], la=L(A_PALM)), "out")
keyp(m, 29.5, P(CALL_POINT, body=[10, 6, 0], la=arm([-0.5, -0.6, 0.2], 30)))
fpw(m, 2.5, FPW_SKY, "out3")
fpw(m, 8.0, FPW_FWD, "out")
fpw(m, 29.5, FPW_FWD, dp=(0, -0.04, 0.05))
fpl(m, 2.5, FP_RAISE, "out3")
fpl(m, 8.0, FP_LOW, "out")
fpl(m, 12.0, FPL_OPEN, "out3")
fpl(m, 18.0, FPL_GRASP, "in")
fpl(m, 24.0, FPL_OPEN, "out")
use_skill(m, "swordcaller_ult")
# 받음 (단계 :catch — 칼들이 적을 꿰뚫으며 손으로 돌아옴 · 3틱 뒤 큰 한 방): 왼손을 내밀어 돌아오는 칼들을 받고, 칼을 왼쪽 위에서 오른쪽 아래로 크게 내려 벰
m = Motion("swordcaller_ult_catch", 14, "칼날 감옥 끝 — 왼손을 앞으로 내밀어 돌아오는 칼들을 받아들이고, 칼을 오른쪽 위에서 왼쪽 아래로 크게 내려 베어 마무리", fade=(0.0, 0.1))
off_bare(m)
first(m, P(CALL_POINT, body=[10, 6, 0], la=arm([-0.5, -0.6, 0.2], 30)), dict(FPW_FWD))
keyp(m, 1.2, P(CALL_POINT, body=[6, -4, 0], la=L(A_PALM), root=[-0.04, 0.1]), "out3")
diag(m, 1.8, 3.0, 4.6)
keyp(m, 9.0, P(body=[16, -30, 0], head=[2, 0], ra=arm([-0.6, -0.55, 0.55], 14, [-0.5, -0.7, -0.4]), la=arm([-0.5, -0.6, 0.1], 35), root=[-0.12, 0.3],
                **LUNGE_DEEP))
keyp(m, 13.5, P(W_READY, body=[8, 6, 0]))
fpl(m, 1.2, FPL_OPEN, "out3")
fpl(m, 3.0, FP_LOW, "out")
fpw(m, 13.5, FPW_LOW)
impacts(m, 3.0)
use_skill(m, "swordcaller_ult:catch")
