"""0.47.0 직업 스킬 동작 — 성기사 · 사제 · 수호기사 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱)."""
import rune_motion_kit as K
from rune_motion_kit import (A_CHEST, A_HIP, A_POINT_UP, A_PRAY, A_RAISE, A_SPREAD_DOWN, CALL, FP_CROSS, FP_GUARD, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE,
                             FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_WIDE, P, RA_UPF, W_HIGH2,
                             W_READY, W_SKY, arm, bent, fpl, fpr, fpw, keyp, nudge, off_bare, strike, use_skill)

Motion = K.Motion


def shield(d, bend, blade=(0, 1, 0), face=(0, 0, 1)):
    """방패를 든 팔 (겉면이 face 쪽)"""
    a = arm(d, bend, list(blade))
    a["face"] = K.n3(face)
    return a


# ------------------------------------------------------------ 성기사 (망치 — 금빛 · 하늘빛 벼락)

# 뇌격 강타 (효과: 0 에 망치로 앞 땅을 쳐 부채꼴 충격파 → 10 에 맞은 자리마다 벼락)
m = Motion("paladin_smite", 16, "뇌격 강타 — 망치로 앞 땅을 내리치고 왼손을 하늘로 들어 벼락을 불러 내리꽂음", fade=(0.03, 0.12))
off_bare(m)
strike(m, "smite", 0.1, 1.1)
keyp(m, 7.4, P(body=[-8, -6, 0], head=[-24, 0], ra=arm([0.2, -0.6, 0.75], 20, [0, -0.6, 0.8]), la=L(A_POINT_UP), **LEGS_FIGHT), "out3")
keyp(m, 10.2, P(body=[14, -10, 0], head=[4, 0], ra=arm([0.2, -0.6, 0.75], 20, [0, -0.6, 0.8]), la=bent([-0.2, -0.3, 0.7], 110, (0.3, 1, 0.2)),
                 **LEGS_FIGHT), "in")
keyp(m, 13, P(W_READY, body=[8, 0, 0]))
fpw(m, 7.4, FPW_DOWN)
fpw(m, 13, FPW_LOW)
fpl(m, 3, FP_LOW)
fpl(m, 7.4, FP_RAISE, "out3")
fpl(m, 10.2, nudge(FP_PALM, (0.05, -0.15, 0.05), (-20, 10, 0)), "in")
fpl(m, 13, FP_LOW)
use_skill(m, "paladin_smite")

# 신념의 돌진 (효과: 4틱 동안 6칸 내달림 — 처음 닿은 적 앞에서 멈춰 부딪힘 → 멈춘 자리 아군 보호막)
HCHARGE = P(body=[28, 10, 0], ra=arm([-0.1, -0.4, 0.9], 80, [-0.95, 0.2, 0.2]), la=arm([-0.05, -0.4, 0.9], 70), rl=[-40, 0, 0], ll=[30, 0, 0], rk=50, lk=30,
            root=[-0.15, 0.2])  # 망치를 가슴 앞에 가로로 들고 숙여 달림
m = Motion("paladin_charge", 14, "신념의 돌진 — 망치를 가슴 앞에 가로로 들고 숙여 내달려 부딪힌 뒤 우뚝 서서 망치를 치켜듦", fade=(0.03, 0.12))
keyp(m, 0.5, HCHARGE, "out3")
keyp(m, 4, HCHARGE)
keyp(m, 5.6, P(body=[-6, 0, 0], head=[-10, 0], ra=arm([0.3, 0.9, 0.2], 30, [0.1, 1, -0.1]), la=L(A_HIP), **LEGS_WIDE), "out3")
keyp(m, 10, P(body=[-4, 0, 0], head=[-8, 0], ra=arm([0.3, 0.85, 0.2], 30, [0.1, 1, -0.1]), la=L(A_HIP), **LEGS_WIDE))
fpw(m, 0.5, dict(p=(-0.6, 0.1, 0.0), blade=(-1, 0.2, 0.2), edge=(0, 0, 1)), "out3")
fpw(m, 4, dict(p=(-0.6, 0.1, 0.0), blade=(-1, 0.2, 0.2), edge=(0, 0, 1)))
fpw(m, 5.6, FPW_UP, "out3")
fpw(m, 10, FPW_UP)
use_skill(m, "paladin_charge")

# 뇌진 (효과: 0 · 10 · 20 에 망치로 내려쳐 3 → 5 → 7칸 번개 고리)
m = Motion("paladin_quake", 24, "뇌진 — 망치를 두 손으로 세 번 연달아 내려쳐 땅을 울림", fade=(0.03, 0.1))
strike(m, "slam", 0.1, 1.2)
strike(m, "slam", 8.0, 10.2)
strike(m, "slam", 18.0, 20.2)
keyp(m, 22.5, P(body=[30, 0, 0], ra=arm([0.05, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.75], 18), rl=[-20, 0, 0], ll=[12, 0, 0], rk=34,
                lk=24, root=[-0.2, 0.1]))
use_skill(m, "paladin_quake")

# 수호의 맹세 (태세 — 효과: 등 뒤로 펼쳐지는 빛의 날개 · 발밑 오라 고리)
OATH = P(body=[6, 0, 0], head=[14, 0], ra=arm([0.15, -0.55, 0.82], 60, [0, -1, 0.05]), la=L(A_CHEST), **LEGS_KNEEL)  # 무릎 꿇고 망치를 앞에 세워 짚고 왼 주먹을 가슴에
m = Motion("paladin_oath", 26, "수호의 맹세 — 한쪽 무릎을 꿇고 망치를 앞에 세워 짚은 채 왼 주먹을 가슴에 얹어 맹세한 뒤 일어남", fade=(0.05, 0.12))
off_bare(m)
keyp(m, 3, OATH, "out3")
keyp(m, 14, P(OATH, head=[18, 0]))
keyp(m, 18.5, P(W_READY, body=[4, 0, 0]), "out")
fpw(m, 3, dict(p=(-0.35, -0.35, -0.1), blade=(0, -1, 0.1)), "out3")
fpw(m, 14, dict(p=(-0.35, -0.35, -0.1), blade=(0, -1, 0.1)))
fpw(m, 18.5, FPW_LOW)
fpl(m, 3, nudge(FP_CROSS, (0.05, -0.2, 0.1)), "out3")
fpl(m, 14, nudge(FP_CROSS, (0.05, -0.2, 0.1)))
fpl(m, 17, FP_LOW)
use_skill(m, "paladin_oath")

# 천뢰의 대망치 (효과: 0 ~ 20 먹구름 · 번개 대망치가 내려옴 → 20 · 34 · 48 에 함께 내리침 → 빛의 성역). 내리침은 서버가 3틱 전에 :smash
m = Motion("paladin_ult", 20, "천뢰의 대망치 — 망치와 왼손을 하늘로 높이 들어 먹구름 속 번개 대망치를 부름", fade=(0.04, 0.06))
keyp(m, 3, P(CALL, ra=arm([0.12, 1, 0.08], 5, [0, 1, 0.02])), "out3")
keyp(m, 16, P(CALL, ra=arm([0.12, 1, 0.08], 5, [0, 1, 0.02]), head=[-32, 0]))
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 16, FPW_SKY)
use_skill(m, "paladin_ult")
m = Motion("paladin_ult_smash", 14, "천뢰의 대망치 — 머리 위로 든 망치를 번개 대망치와 함께 내리침", fade=(0.03, 0.08))
keyp(m, 0.8, P(W_HIGH2, head=[-12, 0]), "out3")
strike(m, "slam", 1.0, 3.0)
keyp(m, 9, P(body=[30, 0, 0], ra=arm([0.05, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.75], 18), rl=[-20, 0, 0], ll=[12, 0, 0], rk=34, lk=24,
             root=[-0.2, 0.1]))
fpw(m, 0.8, FPW_UP, "out3")
fpw(m, 9, FPW_DOWN)
use_skill(m, "paladin_ult:smash")

# ------------------------------------------------------------ 사제 (홀 — 초록 · 옅은 금빛)

# 치유의 손길 (효과: 0 에 겨눈 아군 회복)
m = Motion("priest_heal", 14, "치유의 손길 — 홀과 왼손을 아군 쪽으로 내밀어 빛을 보냄", fade=(0.03, 0.12))
strike(m, "cast", 0.1, 1.6)
keyp(m, 9, P(body=[6, -6, 0], ra=arm([0.12, 0.22, 1], 4, [0, 1, 0.3]), la=arm([-0.15, 0.18, 1], 14), root=[0, 0.12]))
use_skill(m, "priest_heal")

# 빛의 걸음 (효과: 4틱 동안 앞 6칸을 미끄러짐 — 발자국마다 빛 십자)
GLIDE = P(body=[8, 0, 0], head=[-4, 0], ra=arm([0.2, 0.3, 0.92], 30, [0, 1, 0.3]), la=arm([-0.55, -0.6, 0.2], 10), rl=[-12, 0, 0], ll=[16, 0, 0], rk=8, lk=14,
          root=[0.04, 0.1])  # 홀을 앞으로 들고 왼팔을 아래로 펼쳐 미끄러짐
m = Motion("priest_step", 12, "빛의 걸음 — 홀을 앞으로 들고 왼팔을 아래로 펼친 채 빛을 밟으며 미끄러짐", fade=(0.03, 0.12))
keyp(m, 0.5, GLIDE, "out3")
keyp(m, 4, P(GLIDE, rl=[14, 0, 0], ll=[-12, 0, 0], rk=14, lk=8))
keyp(m, 6.5, P(body=[2, 0, 0], ra=arm([0.2, 0.2, 0.95], 30, [0, 1, 0.2]), la=L(A_HIP)))
fpw(m, 0.5, dict(p=(-0.3, 0.25, -0.1), blade=(0, 1, 0.3)), "out3")
fpw(m, 6.5, FPW_LOW)
use_skill(m, "priest_step")

# 성가 (효과: 0 · 10 · … · 70 에 둘레 회복 · 음표 — 4초 노래, 사제를 따라감)
SING = P(body=[-4, 0, 0], head=[-14, 0], ra=arm(RA_UPF["dir"], RA_UPF["bend"], RA_UPF["blade"]), la=L(A_CHEST), rl=[0, 0, 3], ll=[0, 0, -3])
m = Motion("priest_hymn", 72, "성가 — 홀을 들어 박자를 저으며 왼손을 가슴에 얹고 좌우로 천천히 흔들리며 노래함 (4초)", fade=(0.02, 0.06))
off_bare(m)
keyp(m, 2.5, SING, "out3")
for i, t in enumerate(range(5, 70, 10)):
    s = 1 if i % 2 == 0 else -1
    keyp(m, t + 2.5, P(SING, body=[-4, 0, 6 * s], head=[-14, 5 * s], ra=arm([0.2 + 0.15 * s, 0.6, 0.75], 20, [0.25 * s, 0.9, 0.3])))
keyp(m, 69, P(SING, body=[-2, 0, 0]))
fpw(m, 2.5, dict(p=(-0.2, 0.45, 0.0), blade=(0, 0.9, 0.4)), "out3")
for i, t in enumerate(range(5, 70, 10)):
    s = 1 if i % 2 == 0 else -1
    fpw(m, t + 2.5, dict(p=(-0.2 + 0.08 * s, 0.47, 0.0), blade=(0.25 * s, 0.9, 0.35)))
fpw(m, 69, FPW_LOW)
fpl(m, 2.5, nudge(FP_CROSS, (0.05, -0.2, 0.1)), "out3")
fpl(m, 68, nudge(FP_CROSS, (0.05, -0.2, 0.1)))
use_skill(m, "priest_hymn")

# 성흔 (효과: 0 에 홀 끝에서 겨눈 적까지 빛줄기 · 머리 위 십자가)
m = Motion("priest_stigma", 14, "성흔 — 홀을 적에게 겨누어 찌르듯 내밀어 빛의 성흔을 새김", fade=(0.03, 0.12))
strike(m, "cast", 0.1, 1.4)
keyp(m, 9, P(body=[8, -10, 0], ra=arm([0.0, 0.18, 1], 2, [0, 0.4, 0.92]), la=arm([-0.4, -0.5, 0.4], 30), root=[0, 0.14]))
use_skill(m, "priest_stigma")

# 새벽의 종 (효과: 0 ~ 16 큰 종이 내려옴 → 16 · 32 · 48 에 울림). 울림은 서버가 4틱 전에 :toll
PRAY = P(body=[2, 0, 0], head=[-22, 0], ra=bent([0.22, -0.6, 0.7], 118, (-0.75, 0.9, 0.2)), la=L(A_PRAY), **LEGS_KNEEL)  # 무릎 꿇고 두 손을 모음 (홀을 세워 쥠)
m = Motion("priest_ult", 18, "새벽의 종 — 무릎을 꿇고 두 손을 모아 홀을 세워 쥐고 하늘에서 내려오는 종을 우러러봄", fade=(0.04, 0.06))
off_bare(m)
keyp(m, 3, PRAY, "out3")
keyp(m, 16, P(PRAY, head=[-26, 0]))
fpw(m, 3, dict(p=(-0.42, 0.0, 0.1), blade=(-0.05, 1, 0.15)), "out3")
fpw(m, 16, dict(p=(-0.42, 0.02, 0.1), blade=(-0.05, 1, 0.15)))
fpl(m, 3, nudge(FP_PALM, (0.12, -0.2, 0.15), (10, 30, 0)), "out3")
fpl(m, 16, nudge(FP_PALM, (0.12, -0.2, 0.15), (10, 30, 0)))
use_skill(m, "priest_ult")
m = Motion("priest_ult_toll", 16, "새벽의 종 — 일어서며 홀을 높이 들었다가 종을 치듯 내리쳐 울림", fade=(0.03, 0.08))
keyp(m, 1.2, P(body=[-8, 6, 0], head=[-24, 0], ra=arm([0.2, 0.92, 0.2], 30, [0, 1, -0.2]), la=L(A_RAISE), **LEGS_WIDE), "out3")
strike(m, "smite", 2.2, 4.0)
keyp(m, 10, P(body=[12, -6, 0], head=[-10, 0], ra=arm([0.05, -0.2, 0.98], 10, [0, -0.2, 1]), la=L(A_SPREAD_DOWN), **LEGS_WIDE))
fpw(m, 1.2, FPW_SKY, "out3")
fpw(m, 10, FPW_FWD)
use_skill(m, "priest_ult:toll")

# ------------------------------------------------------------ 수호기사 (방패 — 오른손에 방패 · 평타 bash)

# 방패 치기 (효과: 0 에 앞 3.5칸 적을 쳐 기절 · 도발)
m = Motion("guardian_bash", 12, "방패 치기 — 방패를 앞으로 힘껏 내질러 침", fade=(0.03, 0.12))
strike(m, "bash", 0.1, 1.2)
keyp(m, 7, P(body=[12, -8, 0], ra=shield([0.05, 0.0, 1], 24), la=arm([-0.35, -0.5, -0.3], 40), rl=[14, 0, 0], ll=[-22, 0, 0], rk=12, lk=22,
             root=[-0.06, 0.22]))
use_skill(m, "guardian_bash")

# 수호 돌입 (효과: 4틱 동안 아군 (없으면 앞 8칸) 쪽으로 돌진 → 닿은 자리 둘레 적을 침)
RUSH = P(body=[24, -10, 0], ra=shield([0.05, 0.0, 1], 22), la=arm([-0.35, -0.5, -0.3], 40), rl=[-40, 0, 0], ll=[30, 0, 0], rk=50, lk=30, root=[-0.15, 0.2])
m = Motion("guardian_rush", 12, "수호 돌입 — 방패를 앞으로 내민 채 숙여 내달리다 닿으며 방패로 침", fade=(0.03, 0.12))
keyp(m, 0.5, RUSH, "out3")
keyp(m, 3.0, RUSH)
strike(m, "bash", 3.2, 4.4)
keyp(m, 8, P(body=[12, -8, 0], ra=shield([0.05, 0.0, 1], 24), la=arm([-0.35, -0.5, -0.3], 40), **LEGS_FIGHT))
fpw(m, 0.5, dict(p=(-0.3, 0.1, -0.35)), "out3")
fpw(m, 3.0, dict(p=(-0.3, 0.1, -0.35)))
use_skill(m, "guardian_rush")

# 방패 투척 (효과: 0 에 방패를 던짐 → 적 사이를 튕김 → 돌아옴 — 돌아오는 때는 서버가 :catch). 방패가 손을 떠난 동안 빈손 (thrown)
m = Motion("guardian_throw", 60, "방패 투척 — 방패를 왼쪽으로 감았다가 옆으로 휘둘러 던지고 빈손으로 막는 자세로 기다림", fade=(0.03, 0.05))
m.set(hands={"main": "thrown"})
keyp(m, 0.3, P(body=[0, -44, 0], ra=shield([-0.55, -0.1, 0.55], 95, (0, 1, 0), (-0.6, 0, 0.8)), la=arm([-0.4, -0.5, -0.3], 30), rl=[10, 0, 0], ll=[-14, 0, 0],
               rk=18, lk=18, root=[-0.08, 0]))
keyp(m, 1.3, P(body=[12, 24, 0], ra=arm([0.55, 0.05, 0.84], 4, [0.2, 0.9, 0.3]), la=arm([-0.5, -0.5, -0.3], 30), rl=[14, 0, 0], ll=[-26, 0, 0], rk=14,
               lk=24, root=[-0.06, 0.2]), "out3")
keyp(m, 3, P(body=[10, 30, 0], ra=arm([0.85, -0.05, 0.4], 10), la=arm([-0.5, -0.5, -0.3], 30), rl=[14, 0, 0], ll=[-26, 0, 0], rk=14, lk=24,
             root=[-0.06, 0.16]), "out")
keyp(m, 8, P(body=[8, -6, 0], ra=bent([0.25, -0.5, 0.65], 90, (-0.3, 1, 0.2)), la=L(A_HIP), **LEGS_FIGHT))
keyp(m, 58, P(body=[8, -6, 0], ra=bent([0.25, -0.5, 0.65], 90, (-0.3, 1, 0.2)), la=L(A_HIP), **LEGS_FIGHT))
m.set(hide=[m.t(1.25), 1.0])
fpw(m, 0.3, dict(p=(-0.7, -0.1, 0.2)))
fpr(m, 1.4, nudge(FP_PALM, (0.1, 0.0, -0.05), (0, -20, 0)), "out3")
fpr(m, 6, FP_GUARD)
fpr(m, 58, FP_GUARD)
use_skill(m, "guardian_throw")
m = Motion("guardian_throw_catch", 10, "방패 투척 — 돌아오는 방패를 손을 뻗어 받아 앞으로 세움", fade=(0.0, 0.12))
m.set(hands={"main": "thrown"}, hide=[0.0, m.t(0.6)])
keyp(m, 0.3, P(body=[4, -4, 0], ra=arm([0.25, 0.1, 0.96], 10, [0, 1, 0]), la=L(A_HIP), **LEGS_FIGHT))
keyp(m, 2.4, P(body=[10, -8, 0], ra=shield([0.12, -0.1, 0.98], 60), la=L(A_HIP), **LEGS_FIGHT), "out3")
keyp(m, 7, P(W_READY, body=[6, 0, 0]))
fpr(m, 0.3, FP_PALM_UP)
m.fp(0.7, (-0.25, 0.1, -0.2), None, None)
fpw(m, 2.4, dict(p=(-0.3, 0.05, -0.15)), "out3")
fpw(m, 7, FPW_LOW)
use_skill(m, "guardian_throw:catch")

# 철옹성 (태세 — 효과: 앞의 큰 빛의 방패가 솟음 · 발밑 고리)
BULWARK = P(body=[18, 0, 0], head=[14, 0], ra=shield([0.15, -0.1, 0.98], 70), la=arm([-0.2, -0.4, 0.85], 60), rl=[-20, 0, 10], ll=[-20, 0, -10], rk=50,
            lk=50, root=[-0.28, 0])  # 낮게 웅크려 방패 뒤로 몸을 숨김
m = Motion("guardian_bulwark", 22, "철옹성 — 다리를 벌려 낮게 웅크리고 방패를 앞에 세워 그 뒤로 몸을 숨김", fade=(0.04, 0.12))
keyp(m, 2.5, BULWARK, "out3")
keyp(m, 16, P(BULWARK, root=[-0.26, 0]))
fpw(m, 2.5, dict(p=(-0.35, 0.05, -0.2)), "out3")
fpw(m, 16, dict(p=(-0.35, 0.05, -0.2)))
fpw(m, 20, FPW_LOW)
use_skill(m, "guardian_bulwark")

# 성채 (효과: 0 ~ 8 둘레 7칸에 돌 성벽이 솟음 — 8초 요새 → 무너짐)
m = Motion("guardian_ult", 20, "성채 — 방패를 머리 위로 들어 땅에 내리찍어 성벽을 솟게 한 뒤 방패를 높이 치켜듦", fade=(0.03, 0.1))
keyp(m, 1.6, P(W_HIGH2, head=[-14, 0]), "out3")
strike(m, "slam", 2.0, 4.2)
keyp(m, 10, P(body=[30, 0, 0], ra=arm([0.05, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.75], 18), rl=[-20, 0, 0], ll=[12, 0, 0], rk=34,
              lk=24, root=[-0.2, 0.1]))
keyp(m, 14.5, P(body=[-8, 0, 0], head=[-20, 0], ra=shield([0.2, 0.92, 0.25], 20, (0, 1, 0), (0, 0.3, 1)), la=L(A_HIP), **LEGS_WIDE), "out3")
keyp(m, 18, P(body=[-6, 0, 0], head=[-16, 0], ra=shield([0.2, 0.9, 0.25], 20, (0, 1, 0), (0, 0.3, 1)), la=L(A_HIP), **LEGS_WIDE))
fpw(m, 1.6, FPW_UP, "out3")
fpw(m, 10, FPW_DOWN)
fpw(m, 14.5, FPW_SKY, "out3")
use_skill(m, "guardian_ult")
