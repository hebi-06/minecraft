"""0.51.0 2차 직업 스킬 동작 — 마검사 각성 뵐룬드(룬 스미스) · 반전 스키르니르(소드 콜러) — 재설계안 2판 (사용자 "이펙트가 전직업보다 안좋았음 타격감이 없었음
이펙트가 작았음 동작이 뻣뻣했음"). 틱 0 = 스킬 (또는 단계 열쇠 — 서버가 그 순간에 다시 보냄: JobKit.motion) 이 나간 틱, 효과의 later 와 같은 틱
(RuneknightSkills · SwordcallerSkills).

뻣뻣하지 않게 (job_motions_d 와 같은 기준): 치기 전에 반대쪽으로 크게 감고 낮아짐 → 치는 틱에 내디딤 → 지나쳐 멈췄다 되돌아옴 · 멈춘 자세도 숨 쉬듯 조금씩.
마검사는 몸 쓰는 직업이 아님: 룬 스미스 — 대검을 두 손으로 내려찍고 · 겨누고 · 꽂고 · 받아침 (불 · 쇳물 · 모루 · 룬 망치는 저절로 솟고 침),
소드 콜러 — 칼이 손을 떠나 스스로 싸우므로 내 동작은 칼에게 내리는 명령 (칼끝으로 겨눔 · 왼손 손짓 · 돌아오는 칼을 받음)."""
import rune_motion_kit as K
from job_motions_a import PLANT, RISE_END, RISE_HIT
from job_motions_d import FPW_REV_UP, LUNGE_DEEP, REV_RAISE, diag, first, impacts, strike_with
from rune_motion_kit import (A_PALM, A_POINT_UP, A_RAISE, A_SPREAD, FP_DOWN, FP_LOW, FP_PALM, FP_RAISE, FPL_GRIP2H, FPL_RAISE2H, FPW_ACROSS, FPW_DOWN,
                             FPW_DRAWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, GRIP2H, LEGS_FIGHT, LEGS_LUNGE, LEGS_WIDE, RA_BACK_HIGH, RA_POINT, RA_SKY,
                             RAISE2H, W_GUARD_H, W_READY, W_UP_FRONT, L, P, arm, bent, fpl, fpw, keyp, nudge, off_bare, use_skill)

Motion = K.Motion

# 1인칭 왼손: 꽂은 칼 자루를 누름 · 왼손을 펼침 · 움켜쥠
FPL_PLANT = nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0))
FPL_PLANT_HOLD = nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0))
FPL_OPEN = ((0.0, 0.05, -0.4), (18, 55, 0))
FPL_GRASP = ((0.0, -0.08, -0.22), (10, 30, 0))

# ------------------------------------------------------------ 뵐룬드(룬 스미스) (대검 — 보라 · 금빛 룬 · 쇳물)

# 룬 모루 (효과: 4 에 앞 땅을 내려찍음 → 앞으로 갈라짐 · 처음 닿은 적 바로 뒤에 모루가 솟아 12틱 뒤 터짐). 두 손으로 머리 위로 크게 들어 젖혔다가 크게 내디디며 내려찍음
m = Motion("runeknight_anvil", 16, "룬 모루 — 두 손으로 대검을 머리 위로 크게 들어 젖혔다가 크게 내디디며 앞 땅을 내려찍어 가르고, 칼끝을 땅에 댄 채 낮게 버팀",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.2, GRIP2H, "out3")
keyp(m, 2.4, P(RAISE2H, body=[-18, 0, 0], head=[-24, 0], rk=24, lk=26, root=[-0.08, 0]), "out3")
strike_with(m, "slam", 2.6, 4.0, 5.6, a=P(RAISE2H, body=[-18, 0, 0], rl=[16, 0, 4], ll=[-24, 0, -4], rk=26, lk=34),
            b=P(body=[40, 0, 0], head=[-10, 0], root=[-0.32, 0.32], **LUNGE_DEEP))
keyp(m, 8.0, P(PLANT, body=[38, 4, 0], root=[-0.3, 0.28], **LUNGE_DEEP))
keyp(m, 11.0, P(PLANT, body=[34, 6, 0], root=[-0.26, 0.24], **LUNGE_DEEP))
keyp(m, 15.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.2, FPW_UP, "out3", dp=(-0.2, -0.15, 0))
fpw(m, 2.4, FPW_UP, "out3", dp=(0.1, -0.05, 0))
fpw(m, 8.0, FPW_DOWN)
fpw(m, 11.0, FPW_DOWN, dp=(0, 0.02, 0))
fpw(m, 15.5, FPW_LOW)
fpl(m, 1.2, FPL_GRIP2H, "out3")
fpl(m, 2.4, FPL_RAISE2H, "out3")
fpl(m, 5.0, FPL_PLANT, "in")
fpl(m, 11.0, FPL_PLANT_HOLD)
fpl(m, 15.0, FP_LOW)
impacts(m, 4.0)
use_skill(m, "runeknight_anvil")

# 풀무 숨 (효과: 4 · 14 · 24 에 큰 숨 — 그 사이 이어지는 불길, 30 에 끝). 두 손으로 대검을 가슴 앞에 곧게 겨누고 낮게 버팀 · 숨마다 칼을 밀어냈다가 반동
BELLOWS_AIM = P(body=[12, 4, 0], head=[2, 0], ra=arm([0.04, 0.02, 1], 32, [0.0, 0.06, 1]), la=bent([-0.12, -0.05, 0.95], 70, (0.4, 1, 0)), rl=[22, 0, 8],
                ll=[-26, 0, -8], rk=36, lk=42, root=[-0.16, 0.02])
BELLOWS_PUSH = P(BELLOWS_AIM, body=[18, 4, 0], ra=arm([0.04, 0.04, 1], 8, [0.0, 0.08, 1]), la=bent([-0.1, -0.02, 0.97], 40, (0.4, 1, 0)), root=[-0.2, 0.14])
m = Motion("runeknight_bellows", 31, "풀무 숨 — 두 손으로 대검을 가슴 앞에 곧게 겨누고 낮게 버티며, 불을 뿜을 때마다 칼을 앞으로 밀어내고 반동에 조금 밀려남",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, BELLOWS_AIM, "out3")
for tk in (4.0, 14.0, 24.0):
    keyp(m, tk, BELLOWS_PUSH, "out")
    keyp(m, tk + 3.5, P(BELLOWS_AIM, body=[8, 4, 0], root=[-0.13, -0.05]), "inout")
    keyp(m, tk + 8.0, BELLOWS_AIM, "inout")
    fpw(m, tk, FPW_FWD, "out", dp=(0, 0.02, -0.1))
    fpw(m, tk + 3.5, FPW_FWD, "inout", dp=(0, 0, 0.05))
keyp(m, 30.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 2.0, FPW_FWD, "out3")
fpw(m, 30.5, FPW_LOW)
fpl(m, 2.0, FPL_GRIP2H, "out3")
fpl(m, 30.0, FP_LOW)
impacts(m, 4.0)
use_skill(m, "runeknight_bellows")

# 용광로 (효과: 2 에 칼을 꽂은 자리 → 룬 불길이 조준한 곳으로 · 6 부터 용광로가 달아오름 → 16 에 쇳물 기둥). 칼을 거꾸로 들어 앞 땅에 꽂고 누르며 버티다가
# 솟구치는 순간 뽑아 올림
m = Motion("runeknight_furnace", 21, "용광로 — 대검을 거꾸로 들어 앞 땅에 내리꽂고 두 손으로 누르며 버티다가, 쇳물이 솟구치는 순간 단숨에 뽑아 올림", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.0, P(REV_RAISE, root=[-0.04, 0.06]), "out3")
keyp(m, 2.2, PLANT, "in")
for i, tk in enumerate((5.0, 8.0, 11.0, 13.5)):
    sg = 1 if i % 2 else -1
    s = (i + 1) / 4
    keyp(m, tk, P(PLANT, body=[34 + 4 * s, sg * 2.0, 0], rk=40 + 10 * s, lk=30 + 8 * s, root=[-0.24 - 0.06 * s, 0.15]))
    fpw(m, tk, FPW_DOWN, dp=(sg * 0.015, -0.02 * s, 0))
keyp(m, 15.0, P(PLANT, body=[42, 0, 0], rk=56, lk=42, root=[-0.34, 0.12]), "in")
keyp(m, 16.2, P(RISE_HIT, root=[0.02, 0.22]), "out")
keyp(m, 18.0, RISE_END, "out")
keyp(m, 20.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, FPW_REV_UP, "out3")
fpw(m, 2.2, FPW_DOWN, "in")
fpw(m, 15.0, FPW_DOWN, dp=(0, -0.05, 0))
fpw(m, 16.2, FPW_UP, "out")
fpw(m, 18.0, FPW_SKY, "out")
fpw(m, 20.5, FPW_LOW)
fpl(m, 1.0, FPL_RAISE2H, "out3")
fpl(m, 2.2, FPL_PLANT, "in")
fpl(m, 15.0, FPL_PLANT_HOLD)
fpl(m, 16.5, FP_LOW, "out")
impacts(m, 16.0)
use_skill(m, "runeknight_furnace")

# 담금질 (효과: 3 · 7 · 11 에 룬 망치가 칼날을 내려침 → 14 에 김 · 칼이 쇳물빛으로). 대검을 가슴 앞에 가로로 들고 망치를 받을 때마다 움찔 → 세워 들어 보임
TEMPER_HOLD = P(W_GUARD_H, body=[12, 6, 0], head=[8, 0], la=bent([0.1, -0.3, 0.9], 60, (0.4, 1, 0.2)), rl=[16, 0, 6], ll=[-20, 0, -6], rk=26, lk=30,
                root=[-0.1, 0.04])
TEMPER_DIP = P(TEMPER_HOLD, body=[16, 6, 0], ra=arm([0.15, -0.42, 0.88], 78, [-0.95, 0.05, 0.2]), rk=34, lk=36, root=[-0.15, 0.04])
m = Motion("runeknight_temper", 20, "담금질 — 대검을 가슴 앞에 가로로 들어 룬 망치를 받고 (내려칠 때마다 칼이 움찔 내려가고 무릎이 굽음), 김이 솟을 때 칼을 얼굴 앞에 세워 "
           "달군 날을 들어 보임", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.5, TEMPER_HOLD, "out3")
for tk in (3.0, 7.0, 11.0):
    keyp(m, tk, TEMPER_DIP, "in")
    keyp(m, tk + 2.0, TEMPER_HOLD, "out")
    fpw(m, tk, FPW_ACROSS, "in", dp=(0, -0.09, 0))
    fpw(m, tk + 2.0, FPW_ACROSS, "out")
keyp(m, 14.0, P(W_UP_FRONT, body=[-4, 0, 0]), "out3")
keyp(m, 16.5, P(W_UP_FRONT, body=[-10, 0, 0], head=[-12, 0], root=[0.0, 0.04]))
keyp(m, 19.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.5, FPW_ACROSS, "out3")
fpw(m, 14.0, FPW_UP, "out3")
fpw(m, 16.5, FPW_UP, dp=(0, 0.05, 0))
fpw(m, 19.5, FPW_LOW)
fpl(m, 1.5, nudge(FP_PALM, (-0.1, -0.05, 0.05), (0, 20, 0)), "out3")
fpl(m, 14.0, FP_LOW, "out3")
impacts(m, 3.0, 7.0, 11.0)
use_skill(m, "runeknight_temper")

# 쇳물 숫양 (효과: 6 에 숫양이 솟음 → 10 부터 달려옴 → 23 에 :clash → 30 에 :strike). 칼끝을 땅에 짚고 왼손을 앞으로 뻗어 부름 → 두 손으로 칼을 앞에 가로로
# 들고 깊이 버팀
RAM_BRACE = P(body=[16, 0, 0], head=[4, 0], ra=arm([0.1, -0.1, 0.99], 70, [-0.9, 0.35, 0.25]), la=bent([-0.12, -0.12, 0.97], 70, (0.4, 1, 0)), rl=[28, 0, 10],
              ll=[-30, 0, -10], rk=58, lk=60, root=[-0.32, 0.0])
m = Motion("runeknight_ult", 24, "쇳물 숫양 — 칼끝을 땅에 짚고 왼손을 앞으로 곧게 뻗어 숫양을 부른 뒤, 두 손으로 대검을 앞에 비스듬히 가로로 들고 깊이 웅크려 받을 준비",
           fade=(0.04, 0.06))
off_bare(m)
keyp(m, 2.0, P(body=[6, -12, 0], head=[-4, 0], ra=arm([0.2, -0.7, 0.6], 30, [0.05, -0.95, 0.3]), la=arm([-0.05, 0.25, 0.97], 0, [0, 0.4, 1]), **LEGS_WIDE),
     "out3")
keyp(m, 6.0, P(body=[2, -14, 0], head=[-8, 0], ra=arm([0.2, -0.7, 0.6], 30, [0.05, -0.95, 0.3]), la=L(A_RAISE), **LEGS_WIDE), "out")
keyp(m, 10.0, RAM_BRACE, "out3")
for i, tk in enumerate((13.0, 16.0, 19.0, 21.5)):
    sg = 1 if i % 2 else -1
    keyp(m, tk, P(RAM_BRACE, body=[16 + i, sg * 1.5, 0], rk=58 + 3 * i, lk=60 + 3 * i, root=[-0.32 - 0.02 * i, 0.0]))
fpw(m, 2.0, FPW_DOWN, "out3")
fpw(m, 10.0, FPW_ACROSS, "out3", dp=(0, 0.08, -0.05))
fpw(m, 21.5, FPW_ACROSS, dp=(0, 0.06, -0.08))
fpl(m, 2.0, FPL_OPEN, "out3")
fpl(m, 6.0, FP_RAISE, "out")
fpl(m, 10.0, FPL_GRIP2H, "out3")
use_skill(m, "runeknight_ult")
# 맞부딪침 (단계 :clash — 숫양 뿔이 대검에 부딪침): 밀려 젖혀졌다가 이를 악물고 앞으로 밀어붙이며 떨림
m = Motion("runeknight_ult_clash", 8, "쇳물 숫양 — 뿔이 대검에 부딪쳐 몸이 뒤로 밀려 젖혀졌다가, 두 손으로 칼을 밀어붙이며 버팀", fade=(0.0, 0.06))
off_bare(m)
first(m, RAM_BRACE, dict(FPW_ACROSS))
keyp(m, 1.0, P(RAM_BRACE, body=[-6, 0, 0], head=[-10, 0], rk=70, lk=66, root=[-0.4, -0.2]), "out")
keyp(m, 3.0, P(RAM_BRACE, body=[22, 2, 0], rk=62, lk=64, root=[-0.36, 0.04]), "inout")
keyp(m, 5.0, P(RAM_BRACE, body=[24, -2, 0], rk=64, lk=66, root=[-0.37, 0.05]))
keyp(m, 7.5, P(RAM_BRACE, body=[26, 2, 0], rk=66, lk=68, root=[-0.38, 0.06]))
fpw(m, 1.0, FPW_ACROSS, "out", dp=(0, 0.12, 0.1))
fpw(m, 3.0, FPW_ACROSS, "inout", dp=(0, 0.05, -0.1))
fpw(m, 7.5, FPW_ACROSS, dp=(0.02, 0.05, -0.12))
impacts(m, 0.5)
use_skill(m, "runeknight_ult:clash")
# 받아침 (단계 :strike — 숫양을 되돌려 보냄): 오른쪽 아래에서 왼쪽 위로 크게 올려 베며 내디딤
m = Motion("runeknight_ult_strike", 16, "쇳물 숫양 — 버티던 대검을 오른쪽 아래로 끌어내렸다가 크게 내디디며 왼쪽 위로 힘껏 올려 베어 숫양을 쇳물 물결로 되돌려 보냄",
           fade=(0.0, 0.1))
off_bare(m)
first(m, P(RAM_BRACE, body=[26, 2, 0], rk=66, lk=68, root=[-0.38, 0.06]), dict(FPW_ACROSS))
keyp(m, 0.8, P(body=[24, 34, 0], head=[-4, 0], ra=arm([0.62, -0.6, -0.2], 24, [0.45, -0.55, -0.7]), la=bent([0.4, -0.55, 0.3], 70, (0.6, 1, 0)), rl=[24, 0, 6],
                ll=[-30, 0, -6], rk=56, lk=60, root=[-0.36, 0.0]), "out")
keyp(m, 2.0, P(body=[2, 0, 0], head=[-10, 0], ra=arm([0.08, 0.1, 1.0], 6, [-0.6, 0.62, 0.48]), la=arm([-0.5, -0.6, 0.1], 35), root=[-0.12, 0.42], **LUNGE_DEEP),
     "linear")
keyp(m, 3.6, P(body=[-12, -36, 0], head=[-16, 0], ra=arm([-0.6, 0.62, 0.5], 14, [-0.5, 0.82, -0.25]), la=arm([-0.6, -0.5, 0.1], 30), root=[-0.14, 0.46],
                **LUNGE_DEEP), "out")
keyp(m, 8.0, P(body=[-8, -30, 0], head=[-14, 0], ra=arm([-0.55, 0.6, 0.5], 14, [-0.5, 0.8, -0.25]), la=arm([-0.6, -0.5, 0.1], 30), root=[-0.12, 0.42],
                **LUNGE_DEEP))
keyp(m, 15.0, P(W_READY, body=[8, 6, 0]))
m.arc(2.0, "ra", (0.3, -0.25, 1))
m.head.setdefault("trail", []).append([m.t(1.0), m.t(3.6)])
m.set(tilt=30)
fpw(m, 0.8, dict(p=(0.45, -0.45, 0.2), blade=(0.6, -0.45, -0.65)), "out")
fpw(m, 2.0, dict(p=(-0.25, 0.3, -0.25), blade=(-0.6, 0.62, 0.48)), "linear")
fpw(m, 3.6, dict(p=(-0.6, 0.75, 0.1), blade=(-0.55, 0.8, -0.2)), "out")
fpw(m, 15.0, FPW_LOW)
fpl(m, 0.8, FP_LOW, "out")
impacts(m, 2.0)
use_skill(m, "runeknight_ult:strike")

# ------------------------------------------------------------ 스키르니르(소드 콜러) (칼 — 청록 · 흰빛 마력 칼, 칼에게 내리는 명령)

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
