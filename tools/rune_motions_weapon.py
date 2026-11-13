"""0.46.0 룬 동작 — 무기를 쓰는 룬 (힘 · 민첩 · 도끼 · 궁수 · 검사 · 마검사 · 원소 검사 · 사신 · 평타 강화 · 무간 · 대지).
베기 · 내려치기 · 찌르기는 다듬어 둔 평타 · 스킬 동작을 효과의 타격 틱에 맞춰 옮겨 씀 (strike — 1인칭 무기 · 베는 길 · 궤적도 함께)."""
import rune_motion_kit as K
from rune_motion_kit import (A_BACK, A_CHAMBER, A_CHEST, A_CROSS, A_DOWN_FWD, A_FLEX, A_GROUND, A_GUARD, A_HIP, A_JAB, A_OVER, A_OVER_BACK,
                             A_PALM, A_POINT, A_POINT_UP, A_PULL, A_RAISE, A_RAISE_FIST, A_RAISE_FWD, A_SPREAD, A_SPREAD_DOWN, A_SPREAD_UP,
                             FPW_ACROSS, FPW_BACK, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_REST, FPW_SKY, FPW_UP, FP_CHAMBER, FP_GUARD, FP_JAB, FP_JAB2,
                             FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_SQUAT, LEGS_STOMP, LEGS_STOMP_UP, LEGS_WIDE, P, arm, bent,
                             SPIN_FWD_R, W_GUARD_H, W_HIGH2, W_READY, W_SKY, W_UP_FRONT, charge_slam, fpl, fpr, fpw, graft, keyp, nudge, spin_right, strike,
                             use)

Motion = K.Motion

# 무기만 쓰는 자세 (함께 쓰는 자세 · 회전은 rune_motion_kit)
W_IAIDO = P(body=[16, 32, 0], ra=arm([-0.32, -0.75, 0.5], 60, [-0.7, -0.15, 0.7]), la=arm([-0.25, -0.85, 0.3], 45), rl=[20, 0, 4], ll=[-32, 0, -2],
            rk=42, lk=46, root=[-0.16, 0])  # 낮게 웅크려 칼자루를 쥠 (발도 직전)
W_SHEATHE = P(body=[6, 14, 0], ra=arm([-0.25, -0.7, 0.62], 78, [-0.8, -0.3, -0.5]), la=arm([-0.25, -0.85, 0.35], 40), **LEGS_FIGHT)  # 칼을 왼 허리로 거둠
W_POINT_FWD = P(body=[6, -14, 0], ra=arm([-0.05, 0.08, 1], 0, [0, 0.08, 1]), la=L(A_HIP), **LEGS_FIGHT)  # 무기 끝으로 앞을 겨눔
W_CHARGE_LOW = P(body=[10, 52, 0], ra=arm([0.7, -0.45, -0.55], 30, [0.35, -0.2, -0.9]), la=arm([0.45, -0.55, 0.4], 70), rl=[22, 0, 6], ll=[-30, 0, -4],
                 rk=36, lk=38, root=[-0.15, 0])  # 두 손으로 오른쪽 뒤 낮게 (모아 베기 준비)
FPW_ZANSHIN = dict(p=(-0.95, 0.05, 0.05), blade=(-0.9, -0.1, 0.4), edge=(0, -1, 0))  # 1인칭: 가로로 벤 뒤 왼쪽에 멈춘 칼
ZANSHIN = P(body=[14, -34, 0], ra=arm([-0.75, -0.2, 0.62], 14, [-0.85, -0.3, 0.4]), la=arm([-0.3, -0.75, 0.35], 35), rl=[18, 0, 4], ll=[-28, 0, -2],
            rk=36, lk=40, root=[-0.14, 0.1])  # 가로로 벤 뒤 멈춘 자세 (발도 일섬 · 무간참역 — 2026-11-13 무영검 동작이 rune_motions_hero 로 옮겨 여기에 둠)
def fp_slash_ready(m, tick):
    fpw(m, tick, FPW_BACK)




# ------------------------------------------------------------ 힘

# 대지 균열 (2026-11-13 사용자 메모 "애 왜 위에서 아래로 안내려찍어?"): 무기를 두 손으로 머리 위로 치켜든 채 시작 (틱 0 = 첫 갈라짐 — 감는 틈이 없음)
# → 곧장 위에서 아래로 앞 땅에 내리꽂으며 깊이 내디딤 (1.6) → 갈라짐이 앞으로 달리는 동안 박은 채 버팀 → 뽑음
m = Motion("fissure", 20, "대지 균열 — 머리 위로 치켜든 무기를 곧장 위에서 아래로 앞 땅에 내리꽂아 땅이 갈라지게 함", fade=(0.01, 0.1))
keyp(m, 0.0, P(W_HIGH2, body=[-18, 4, 0], head=[-12, 0], ra=arm([0.12, 0.85, -0.5], 45, [0, 0.1, -1]), rl=[8, 0, 0], ll=[-8, 0, 0], rk=14, lk=14))
fpw(m, 0.0, FPW_SKY, dp=(0.1, 0.1, 0.25))
keyp(m, 1.6, P(body=[44, 0, 0], head=[14, 0], ra=arm([0.06, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.76], 18), rl=[32, 0, 4],
               ll=[-48, 0, -2], rk=22, lk=54, root=[-0.4, 0.5]), "in")
fpw(m, 1.6, FPW_DOWN, "in")
keyp(m, 8.0, P(body=[40, 0, 0], head=[12, 0], ra=arm([0.06, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.76], 18), rl=[32, 0, 4],
               ll=[-48, 0, -2], rk=22, lk=54, root=[-0.38, 0.48]))
keyp(m, 14, P(W_READY, body=[10, 4, 0]))
fpw(m, 8, FPW_DOWN)
fpw(m, 14, FPW_LOW)
use(m, "fissure")

# ------------------------------------------------------------ 민첩

# ------------------------------------------------------------ 도끼

# 회전 도끼: 도끼를 크게 던짐 (틱 0 — 바로 손을 떠남) → 빈손으로 날아가는 도끼를 봄 → 돌아오는 도끼를 받음 (≈ 22)
# (손: thrown — 무기가 손을 떠난 동안 (hide) 은 1인칭 · 3인칭 모두 빈손, 1인칭 빈손은 fp 열쇠대로)
m = Motion("axe_boomerang", 28, "회전 도끼 — 도끼를 크게 던지고 빈손으로 기다렸다가 돌아오는 도끼를 받음", fade=(0.03, 0.1))
m.set(hands={"main": "thrown"})
graft(m, "throw_weapon", 0.3, 2.6, t_from=0.38, t_to=0.75)
for t in [t for t in m.fps if m.t(1.5) < t < m.t(3.5)]:
    del m.fps[t]  # 옮겨 온 1인칭 '무기를 화면 밖으로' 는 빼고 빈손 열쇠로
keyp(m, 8, P(body=[8, -10, 0], ra=arm([0.25, -0.2, 0.9], 30), la=L(A_GUARD), **LEGS_FIGHT))
keyp(m, 19, P(body=[2, -4, 0], ra=arm([0.3, 0.35, 0.85], 25), la=L(A_GUARD), **LEGS_FIGHT))
keyp(m, 22, P(body=[4, 10, 0], ra=arm([0.4, -0.2, 0.6], 80, [0, 0.5, 0.85]), la=L(A_GUARD), **LEGS_FIGHT), "out")
m.set(hide=[m.t(1.55), m.t(21.5)])
fpr(m, 1.6, nudge(FP_JAB2, (0, -0.08, 0), (-8, 0, 0)))  # 던진 빈손이 앞으로 뻗음
fpr(m, 7, FP_GUARD)
fpr(m, 18, FP_GUARD)
fpr(m, 21.4, FP_PALM_UP, "out")  # 돌아오는 도끼를 잡으러 손을 듦
m.fp(21.6, (-0.2, 0.15, 0.0), None, (0.05, 0.75, 0.65))  # 잡은 무기
m.fp(24.5, (-0.05, 0.05, 0.05), None, (0.0, 0.9, 0.35), None, "out")
use(m, "axeBoomerang")

# 도약 찍기: 웅크려 (0) 도끼를 머리 위로 든 채 뛰어 (2) 내려찍음 (닿음 4 — 순간 이동 3)
m = Motion("leap_slam", 16, "도약 찍기 — 웅크렸다가 도끼를 머리 위로 들고 뛰어 내려찍음", fade=(0.03, 0.12))
graft(m, "leap_slam", 0, 7.6, t_from=0.2, t_to=0.82)
keyp(m, 0.3, P(body=[25, 0, 0], ra=arm([0.3, -0.4, -0.8], 40, [0, 0, 1]), la=arm([-0.3, -0.4, -0.8], 40), rl=[-30, 0, 0], ll=[-30, 0, 0], rk=70, lk=70,
               root=[-0.3, 0]))
use(m, "leapSlam")

# ------------------------------------------------------------ 궁수 (주 손에 든 활 — 왼손이 시위를 당김)

DRAW = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140))  # 활을 앞으로 · 시위를 뺨까지
FP_BOW = (-0.25, 0.08, 0.0)

# ------------------------------------------------------------ 검사

# ------------------------------------------------------------ 마검사

# 마검 해방 (사용자 "마검 초월 같은거는 두손을 모으고 검을 위로 들고 기를 모으다가 아래로 내려찍는 모션으로"): 두 손을 모아 무기를 세우고 (1.5) →
# 두 손으로 머리 위로 곧게 들어 기를 모음 (4.5 ~ 23 — 눈앞에 거대한 마력 검이 솟고, 16 에 그 검이 가로로 벰) → 거대한 검이 앞으로 쓰러지며 내려찍는 때 (26) 함께 내려찍음
m = Motion("spellblade_release", 36, "마검 해방 — 두 손으로 무기를 머리 위로 들어 기를 모으다가 거대한 마력 검과 함께 내려찍음", fade=(0.03, 0.08))
charge_slam(m, 26)
use(m, "spellbladeRelease")

# ------------------------------------------------------------ 원소 검사

# 원소 해방 (0.47.0 마검 해방과 따로 — 사용자 "따로 원함"): 효과 = 눈앞 2.4칸에 솟는 거대한 검에 불 (0 ~ 5) → 얼음 (5 ~ 10) → 번개 (10 ~ 16) 가
# 차례로 감기고, 16 에 원소 검기 셋이 부채꼴로 (가운데 불 · 왼쪽 얼음 · 오른쪽 번개). 동작: 칼을 얼굴 앞에 세운 채 (경례) 왼손을 거대한 검 쪽으로 뻗어
# 원소가 바뀔 때마다 한 번씩 밀어 넣음 (1.2 · 6.2 · 11.2 — 사이에 조금 당김) → 칼을 오른쪽 뒤로 젖혀 (12.8) 부채꼴로 크게 가로 벰 (16 — 왼손은 내림)
EL_SALUTE_RA = (arm([0.22, 0.3, 0.93], 86, [0.04, 1, 0.1]), arm([0.12, 0.34, 0.93], 88, [-0.14, 1, 0.14]), arm([0.24, 0.42, 0.88], 80, [0.1, 1, 0.24]))
EL_PUSH_LA = arm([0.12, 0.03, 1], 4, [0.04, 1, 0.18])  # 손바닥을 거대한 검 쪽으로 (손끝이 위)
EL_PULL_LA = bent([0.1, -0.12, 0.95], 48, (0.1, 1, 0.1))  # 조금 당김
EL_BASE = P(head=[2, 0], **LEGS_WIDE)
m = Motion("element_release", 26, "원소 해방 — 칼을 얼굴 앞에 세우고 왼손으로 거대한 검에 불 · 얼음 · 번개를 차례로 밀어 넣은 뒤 부채꼴로 크게 휘둘러 원소 검기를 날림",
           fade=(0.03, 0.1))
K.off_bare(m)
for i, e in enumerate((0, 5, 10)):
    push, pull = e + 1.2, e + 3.8
    keyp(m, push, P(EL_BASE, body=[4, -12, 0], ra=EL_SALUTE_RA[i], la=EL_PUSH_LA, root=[-0.06, 0.06], rk=20, lk=20), "out3")
    fpw(m, push, dict(p=(0.02, 0.33, 0.04), blade=((-0.06, 0.03, 0.08)[i], 1, 0.1)), "out3")
    fpl(m, push, nudge(FP_PALM, (0.0, -0.02, -0.06), (6, -6, 0)), "out3")
    if i < 2:
        keyp(m, pull, P(EL_BASE, body=[-1, -8, 0], ra=EL_SALUTE_RA[i], la=EL_PULL_LA, root=[-0.04, 0], rk=16, lk=16))
        fpw(m, pull, dict(p=(0.03, 0.31, 0.06), blade=((-0.06, 0.03, 0.08)[i], 1, 0.1)))
        fpl(m, pull, nudge(FP_PALM, (0.12, -0.16, 0.3), (-4, -22, 0)))
# 번개가 감기는 동안 왼손이 떨림 (12 · 12.6)
keyp(m, 12.1, P(EL_BASE, body=[5, -12, 1], ra=EL_SALUTE_RA[2], la=arm([0.13, 0.05, 1], 6, [0.06, 1, 0.2]), root=[-0.07, 0.07], rk=22, lk=22))
fpl(m, 12.1, nudge(FP_PALM, (0.0, -0.04, 0.0), (8, -4, 0)))
strike(m, "skill_slash", 12.9, 16)
fpl(m, 13.6, FP_LOW, "in")
fpl(m, 25, FP_LOW)
use(m, "elementRelease")

# ------------------------------------------------------------ 사신 (낫)

# 적월 강림: 낫을 하늘로 들어 핏빛 달을 띄움 (0 ~ 16) → 칼날 비 (16 · 26 · 36) 에 맞춰 낫으로 하늘을 가르듯 (가로 베기) → 거둠
m = Motion("crimson_moon", 44, "적월 강림 — 낫을 하늘로 들어 핏빛 달을 띄우고, 쏟아지는 칼날에 맞춰 하늘을 가르듯 휘두름", fade=(0.04, 0.08))
keyp(m, 4, W_SKY, "out3")
keyp(m, 13, P(W_SKY, body=[-14, -2, 0], head=[-30, 0]))
strike(m, "slash_h", 14.5, 16.5)
strike(m, "slash_h", 24.5, 26.5)
strike(m, "slash_v", 34, 36.5)
fpw(m, 4, FPW_SKY, "out3")
fpw(m, 13, FPW_SKY)
use(m, "crimsonMoon")

# ------------------------------------------------------------ 평타 강화 (무기에 힘을 실음)

# 날 세우기: 무기를 가로로 들고 왼손으로 날을 훑음 (1 ~ 4 — 날에 빛이 달림)
m = Motion("edge_hone", 14, "날 세우기 — 무기를 가로로 들고 왼손으로 날을 훑어 세움", fade=(0.04, 0.12))
m.set(hands={"main": "weapon", "off": "fist"})
keyp(m, 1.5, P(W_GUARD_H, la=arm([0.2, -0.25, 0.95], 80)), "out3")
keyp(m, 4.5, P(W_GUARD_H, la=arm([-0.75, 0.0, 0.66], 8)))
keyp(m, 8, P(W_READY, body=[6, 6, 0]))
fpw(m, 1.5, FPW_ACROSS, "out3")
fpw(m, 4.5, FPW_ACROSS)
fpl(m, 1.5, ((-0.3, 0.1, -0.1), (15, 70, 0)), "out3")
fpl(m, 4.5, ((0.3, 0.12, -0.25), (15, -25, 0)))
fpl(m, 8, FP_LOW)
use(m, "edgeHone")

# 연타: X 자로 두 번 빠르게 (0 · 2)
m = Motion("flurry", 10, "연타 — 무기를 X 자로 두 번 빠르게 휘둘러 잔상을 남김", fade=(0.03, 0.12))
graft(m, "dagger_x", 0, 5, t_from=0.16, t_to=0.72)
use(m, "flurry")

# 가르기: 낮고 넓게 가로로 쓸어 벰 (0)
m = Motion("cleave", 12, "가르기 — 낮고 넓게 가로로 쓸어 벰", fade=(0.03, 0.12))
strike(m, "lance_sweep", 0, 1.5)
use(m, "cleave")

# ------------------------------------------------------------ 무간 · 대지 (한정 초월)

# 무간참역: 낮게 웅크려 칼자루를 쥐고 (0 ~ 4) 영역을 펴는 발도 (가로 — 5) → 칼을 거두며 영역을 닫음 (12 ~ 16)
m = Motion("endless_slash", 22, "무간참역 — 웅크려 칼을 뽑아 영역을 펼치고, 칼을 거두며 무수한 참격을 남김", fade=(0.03, 0.1))
keyp(m, 2, W_IAIDO, "out3")
keyp(m, 4, P(W_IAIDO, rk=46, lk=50, root=[-0.18, 0]))
strike(m, "slash_h", 4.4, 5.6, 7.5)
keyp(m, 11, ZANSHIN)
keyp(m, 14.5, W_SHEATHE, "out3")
keyp(m, 18, P(W_SHEATHE, body=[4, 6, 0]))
fpw(m, 11, FPW_ZANSHIN)
m.fp(14.5, (-0.4, -0.35, 0.2), None, (-0.85, -0.3, -0.4), None, "out3")
use(m, "endlessSlash")

