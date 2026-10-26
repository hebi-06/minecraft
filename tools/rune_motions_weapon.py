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
def fp_slash_ready(m, tick):
    fpw(m, tick, FPW_BACK)




# ------------------------------------------------------------ 힘

# 대지 강타: 두 손 내려찍기를 바로 (닿음 2) → 땅에 박은 채 → 거둠
m = Motion("ground_slam", 16, "대지 강타 — 두 손으로 앞 땅을 바로 내려찍음", fade=(0.03, 0.12))
strike(m, "slam", 0, 2.2)
keyp(m, 9, P(body=[30, 0, 0], ra=arm([0.05, -0.6, 0.8], 12, [0, -0.85, 0.45]), la=arm([-0.02, -0.65, 0.75], 18), rl=[-22, 0, 0], ll=[14, 0, 0],
             rk=36, lk=26, root=[-0.22, 0.12]))
fpw(m, 9, FPW_DOWN)
use(m, "groundSlam")

# 회오리 베기: 무기를 앞으로 뻗고 오른쪽으로 세 바퀴 (효과의 휘몰아침 0 · 10 · 20 — 바퀴마다 무기가 앞을 지남)
m = Motion("whirlwind", 34, "회오리 베기 — 무기를 앞으로 뻗고 제자리에서 오른쪽으로 세 바퀴 휘몰아침", fade=(0.03, 0.08))
spin_right(m, (2, 12, 22), 31)
use(m, "whirlwind")

# 대지 균열: 무기를 앞 땅에 내리꽂음 (닿음 1) → 갈라짐이 앞으로 달리는 동안 박은 채 (≈ 6) → 뽑음
m = Motion("fissure", 18, "대지 균열 — 무기를 앞 땅에 내리꽂아 땅이 갈라지게 함", fade=(0.03, 0.1))
strike(m, "chop", 0, 1.4)
keyp(m, 7, P(body=[32, 0, 0], ra=arm([0.06, -0.62, 0.78], 12, [0, -0.9, 0.42]), la=arm([-0.02, -0.65, 0.76], 18), rl=[-22, 0, 0], ll=[14, 0, 0],
             rk=34, lk=24, root=[-0.2, 0.1]))
keyp(m, 12, P(W_READY, body=[10, 4, 0]))
fpw(m, 7, FPW_DOWN)
fpw(m, 12, FPW_LOW)
use(m, "fissure")

# 거인의 일격: 울부짖으며 무기를 하늘로 (0 ~ 4) → 두 손으로 머리 위 (5) → 망치가 떨어지는 때 (8) 함께 내려찍음
m = Motion("titan_smash", 20, "거인의 일격 — 무기를 들고 포효한 뒤 떨어지는 거대한 망치와 함께 내려찍음", fade=(0.04, 0.1))
keyp(m, 2.5, P(W_SKY, head=[-30, 0], body=[-16, 0, 0]), "out3")
keyp(m, 5.5, W_HIGH2)
strike(m, "slam", 5.5, 8.2)
fpw(m, 2.5, FPW_SKY, "out3")
use(m, "titanSmash")

# 천붕: 무기를 하늘로 들어 거대한 검을 부름 (0 ~ 15 — 조금 흔들림) → 끌어내림 (16 ~ 19) → 땅에 박힐 때 (24) 웅크려 버팀
m = Motion("heaven_fall", 30, "천붕 — 무기를 하늘로 들어 거대한 검을 부르고, 끌어내려 땅에 박음", fade=(0.04, 0.1))
keyp(m, 3.5, W_SKY, "out3")
keyp(m, 9, P(W_SKY, body=[-14, -2, 0]))
keyp(m, 15.5, P(W_SKY, body=[-12, -6, 0], head=[-28, 0]))
keyp(m, 19, P(body=[28, -10, 0], ra=arm([0.05, -0.45, 0.88], 8, [0, -0.6, 0.8]), la=L(A_HIP), rl=[-20, 0, 0], ll=[14, 0, 0], rk=30, lk=20,
              root=[-0.16, 0.1]), "in")
keyp(m, 24.5, P(body=[20, -6, 0], ra=arm([0.05, -0.5, 0.85], 10, [0, -0.7, 0.7]), la=L(A_GUARD), rl=[-34, 0, 8], ll=[-30, 0, -8], rk=60, lk=60,
                root=[-0.26, 0.05]))
fpw(m, 3.5, FPW_SKY, "out3")
fpw(m, 15.5, FPW_SKY, dp=(0, 0.03, 0))
fpw(m, 19, FPW_DOWN, "in")
fpw(m, 24.5, FPW_DOWN, dp=(0, -0.05, 0))
use(m, "heavenFall")

# ------------------------------------------------------------ 민첩

# 질풍 베기: 이미 끝 자리 (틱 0 순간 이동) — 낮게 숙여 베어 지나간 뒤의 자세에서 일어섬
m = Motion("gale_slash", 12, "질풍 베기 — 낮게 내달려 베고 지나간 자세에서 일어섬", fade=(0.03, 0.2))
graft(m, "skill_slash", 0.35, 4.6, t_from=0.48, t_to=0.85)
use(m, "galeSlash")

# 칼날 부채: 왼손을 오른 어깨 쪽에서 왼쪽 앞으로 크게 뿌려 칼날 일곱을 부채꼴로 던짐 (틱 0)
m = Motion("blade_fan", 12, "칼날 부채 — 왼손을 몸 앞으로 크게 뿌려 칼날을 부채꼴로 던짐", fade=(0.03, 0.12))
m.set(hands={"main": "weapon", "off": "fist"})
keyp(m, 0.6, P(body=[2, 30, 0], ra=arm([0.3, -0.6, 0.4], 60, [0, 0.5, 0.85]), la=bent([0.55, 0.1, 0.7], 95, (1, 0.2, 0)), **LEGS_FIGHT))
keyp(m, 2, P(body=[10, -26, 0], ra=arm([0.3, -0.6, 0.4], 60, [0, 0.5, 0.85]), la=arm([-0.95, 0.05, 0.3], 4), **LEGS_LUNGE), "out3")
keyp(m, 6, P(body=[8, -22, 0], ra=arm([0.3, -0.6, 0.4], 60, [0, 0.5, 0.85]), la=arm([-0.9, 0.0, 0.35], 8), **LEGS_LUNGE))
fpl(m, 0.6, ((-0.2, 0.15, 0.0), (25, 75, 0)))
fpl(m, 2, ((0.3, 0.25, -0.3), (35, -25, 0)), "out3")
fpl(m, 6, ((0.3, 0.2, -0.25), (30, -25, 0)))
fpw(m, 2, FPW_LOW)
use(m, "bladeFan")

# 그림자 습격: 분신이 나가는 때 (1 · 4 · 8 · 12) 왼손 · 무기 · 왼손 · 무기로 왼쪽 · 오른쪽을 번갈아 가리킴
m = Motion("shadow_raid", 20, "그림자 습격 — 분신이 덮칠 적을 왼손 · 무기로 번갈아 가리킴", fade=(0.03, 0.12))
m.set(hands={"main": "weapon", "off": "fist"})
POINT_L = P(body=[6, 22, 0], ra=arm([0.25, -0.6, 0.55], 70, [0.1, 0.55, 0.8]), la=arm([-0.55, 0.12, 0.85], 0), **LEGS_FIGHT)
POINT_R = P(body=[6, -24, 0], ra=arm([0.45, 0.1, 0.9], 4, [0.4, 0.1, 0.9]), la=L(A_GUARD), **LEGS_FIGHT)
for tick, pose in ((1.5, POINT_L), (4.5, POINT_R), (8.5, P(POINT_L, la=arm([-0.35, 0.05, 0.95], 0))),
                   (12.5, P(POINT_R, ra=arm([0.25, 0.05, 0.97], 4, [0.25, 0.05, 0.97])))):
    keyp(m, tick, pose, "out3")
fpl(m, 1.5, ((0.1, 0.05, -0.35), (15, 20, 0)), "out3")
fpw(m, 1.5, FPW_LOW)
fpl(m, 4.5, FP_GUARD)
m.fp(4.5, (-0.1, 0.25, -0.4), None, (0.35, 0.08, 0.93), (0, -1, 0), "out3")
fpl(m, 8.5, ((0.0, 0.05, -0.38), (15, 35, 0)), "out3")
fpw(m, 8.5, FPW_LOW)
fpl(m, 12.5, FP_GUARD)
m.fp(12.5, (-0.25, 0.25, -0.42), None, (0.2, 0.08, 0.97), (0, -1, 0), "out3")
use(m, "shadowRaid")

# 칼바람 폭풍: 한 바퀴 크게 돌아 칼바람을 두르고 (0 ~ 8) 무기를 낮게 뻗은 채 내달릴 자세
m = Motion("blade_storm", 18, "칼바람 폭풍 — 오른쪽으로 한 바퀴 돌아 칼바람을 두르고 낮게 내달릴 자세", fade=(0.03, 0.12))
spin_right(m, (2,), 9)
keyp(m, 12.5, P(body=[24, -10, 0], ra=arm([0.45, -0.55, 0.7], 10, [0.6, -0.2, 0.75]), la=arm([-0.4, -0.5, -0.75], 25), rl=[-30, 0, 4], ll=[20, 0, -4],
                rk=45, lk=35, root=[-0.15, 0.1], spin=-360))
fpw(m, 12.5, FPW_LOW)
use(m, "bladeStorm")

# 무영검: 발도 (0 ~ 2 — 가로로 한 번에) → 낮게 멈춘 자세로 기다림 (3 ~ 24) → 칼을 거두는 순간 (26) 칼자국이 터짐
m = Motion("shadowless", 34, "무영검 — 한 번에 뽑아 베고 멈춰 기다리다가 칼을 거두는 순간 칼자국이 터짐", fade=(0.03, 0.1))
strike(m, "slash_h", 0, 1.5, 3.5)
ZANSHIN = P(body=[14, -34, 0], ra=arm([-0.75, -0.2, 0.62], 14, [-0.85, -0.3, 0.4]), la=arm([-0.3, -0.75, 0.35], 35), rl=[18, 0, 4], ll=[-28, 0, -2],
            rk=36, lk=40, root=[-0.14, 0.1])
keyp(m, 6, ZANSHIN)
keyp(m, 22, P(ZANSHIN, body=[15, -35, 0]))
keyp(m, 25, W_SHEATHE, "out3")
keyp(m, 29, P(W_SHEATHE, body=[4, 8, 0]))
fpw(m, 6, FPW_ZANSHIN)
fpw(m, 22, FPW_ZANSHIN)
m.fp(25, (-0.4, -0.35, 0.2), None, (-0.85, -0.3, -0.4), None, "out3")
use(m, "shadowless")

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

# 피의 도끼질: 오른쪽 위에서 왼쪽 아래로 크게 찍음 (닿음 2) → 피가 손으로 모일 때 (8) 왼 주먹을 가슴에
m = Motion("blood_cleave", 16, "피의 도끼질 — 오른쪽 위에서 왼쪽 아래로 크게 찍고 피를 거둠", fade=(0.03, 0.12))
keyp(m, 0.6, P(body=[-6, 34, 0], ra=arm([0.55, 0.8, -0.25], 40, [0.25, 0.55, -0.8]), la=L(A_GUARD), rl=[12, 0, 0], ll=[-16, 0, 0], rk=14, lk=14))
keyp(m, 2.2, P(body=[22, -28, 0], ra=arm([-0.35, -0.3, 0.88], 6, [-0.55, -0.62, 0.55]), la=arm([-0.4, -0.6, -0.3], 30), rl=[-14, 0, 0], ll=[14, 0, 0],
               rk=30, lk=20, root=[-0.14, 0.14]), "linear")
keyp(m, 4, P(body=[24, -34, 0], ra=arm([-0.7, -0.55, 0.42], 14, [-0.55, -0.82, -0.12]), la=arm([-0.4, -0.65, -0.3], 30), rl=[-14, 0, 0], ll=[14, 0, 0],
             rk=30, lk=20, root=[-0.14, 0.14]), "out")
keyp(m, 8.5, P(body=[6, -10, 0], ra=arm([-0.2, -0.75, 0.55], 20, [-0.3, -0.5, 0.8]), la=L(A_CHEST), head=[-6, 0], **LEGS_FIGHT))
m.arc(2.2, "ra", [0.25, 0.45, 1])
m.set(tilt=30, trail=[[m.t(0.8), m.t(4)]])
m.fp(0.6, (0.4, 0.9, 0.2), None, (0.4, 0.75, -0.5), (0.5, -0.2, 0.85))
m.fp(2.2, (-0.6, 0.25, -0.05), None, (-0.55, -0.55, 0.62), (-0.6, -0.5, -0.6), "linear")
m.fp(4, (-1.0, -0.2, 0.1), None, (-0.55, -0.8, -0.1), (-0.2, 0.1, -0.97), "out")
fpw(m, 8.5, FPW_LOW)
use(m, "bloodCleave")

# 도약 찍기: 웅크려 (0) 도끼를 머리 위로 든 채 뛰어 (2) 내려찍음 (닿음 4 — 순간 이동 3)
m = Motion("leap_slam", 16, "도약 찍기 — 웅크렸다가 도끼를 머리 위로 들고 뛰어 내려찍음", fade=(0.03, 0.12))
graft(m, "leap_slam", 0, 7.6, t_from=0.2, t_to=0.82)
keyp(m, 0.3, P(body=[25, 0, 0], ra=arm([0.3, -0.4, -0.8], 40, [0, 0, 1]), la=arm([-0.3, -0.4, -0.8], 40), rl=[-30, 0, 0], ll=[-30, 0, 0], rk=70, lk=70,
               root=[-0.3, 0]))
use(m, "leapSlam")

# 처형: 무기를 천천히 높이 들어 (0 ~ 6) 대상 위에 떠오른 도끼와 함께 곧장 내려찍음 (8)
m = Motion("execute", 18, "처형 — 무기를 천천히 높이 들었다가 곧장 내려찍음", fade=(0.04, 0.1))
keyp(m, 3, P(W_HIGH2, body=[-18, 6, 0], head=[-8, 0]), "out")
strike(m, "chop", 6, 8.2)
fpw(m, 3, FPW_SKY, "out")
use(m, "execute")

# 광전사의 혼: 몸을 웅크렸다가 (0 ~ 2) 가슴을 펴고 포효하며 무기와 주먹을 치켜듦 (4 ~ 12)
m = Motion("berserker", 18, "광전사의 혼 — 웅크렸다가 가슴을 펴고 무기와 주먹을 치켜들며 포효", fade=(0.04, 0.12))
keyp(m, 2, P(body=[26, 0, 0], ra=arm([0.35, -0.6, 0.55], 95, [0, 0.4, 0.9]), la=L(A_CHAMBER), head=[18, 0], rl=[-20, 0, 8], ll=[-20, 0, -8], rk=45,
             lk=45, root=[-0.2, 0]))
keyp(m, 5, P(body=[-18, 0, 0], ra=arm([0.7, 0.65, 0.15], 25, [0.3, 1, 0.1]), la=L(A_RAISE_FIST), head=[-30, 0], **LEGS_WIDE), "out3")
keyp(m, 12, P(body=[-14, 0, 0], ra=arm([0.7, 0.62, 0.15], 28, [0.3, 1, 0.1]), la=L(A_RAISE_FIST), head=[-26, 0], **LEGS_WIDE))
fpw(m, 2, FPW_LOW)
fpw(m, 5, FPW_SKY, "out3", dp=(0.3, -0.1, 0))
fpw(m, 12, FPW_SKY, dp=(0.3, -0.12, 0))
use(m, "berserker")

# ------------------------------------------------------------ 궁수 (주 손에 든 활 — 왼손이 시위를 당김)

DRAW = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140))  # 활을 앞으로 · 시위를 뺨까지
FP_BOW = (-0.25, 0.08, 0.0)

# 관통 화살: 바로 당겨 (0 ~ 0.5) 놓음 (1) · 활을 든 채 남음
m = Motion("pierce_arrow", 12, "관통 화살 — 바로 당겨 곧게 쏨", fade=(0.03, 0.12))
graft(m, "shoot", 0, 6, t_from=0.3, t_to=0.85)
keyp(m, 0.4, DRAW)
use(m, "pierceArrow")

# 속박 화살: 대상의 발께로 조금 낮게 겨눠 쏨 (화살이 대상을 땅에 묶음)
m = Motion("pin_arrow", 12, "속박 화살 — 대상의 발께로 낮게 겨눠 쏨", fade=(0.03, 0.12))
LOW = dict(ra=arm([0.05, -0.25, 1], 0, [0, 1, 0.25]))
keyp(m, 0.4, P(DRAW, body=[8, -20, 0], **LOW))
keyp(m, 1.4, P(DRAW, body=[6, -16, 0], ra=arm([0.05, -0.2, 1], 0, [0, 1, 0.05]), la=arm([-0.3, 0.0, -0.7], 40)), "out3")
keyp(m, 6, P(DRAW, body=[4, -14, 0], ra=arm([0.05, -0.22, 1], 4, [0, 1, 0.2]), la=arm([-0.3, -0.2, -0.6], 40)))
for t, p in ((0.4, FP_BOW), (1.4, (-0.25, 0.1, 0.1)), (6, FP_BOW)):
    m.fp(t, (p[0], p[1] - 0.08, p[2]))
use(m, "pinArrow")

# 도약 사격: 뒤로 뛴 자리에 낮게 내려앉음 (0) → 2 · 5 · 8 에 빠르게 세 발
m = Motion("backstep", 14, "도약 사격 — 뒤로 뛰어 낮게 내려앉으며 세 발을 빠르게 쏨", fade=(0.03, 0.12))
CROUCH = dict(rl=[-30, 0, 8], ll=[-20, 0, -8], rk=60, lk=50, root=[-0.24, -0.1])
keyp(m, 0.5, P(DRAW, body=[16, -20, 0], la=arm([0.1, -0.4, -0.3], 60), **CROUCH))
for tick in (2, 5, 8):
    keyp(m, tick - 0.6, P(DRAW, body=[12, -20, 0], **CROUCH))
    keyp(m, tick + 0.4, P(DRAW, body=[10, -16, 0], la=arm([-0.3, 0.0, -0.7], 40), **CROUCH), "out3")
    m.fp(tick - 0.6, FP_BOW)
    m.fp(tick + 0.4, (-0.25, 0.1, 0.1), ease="out3")
use(m, "backstep")

# 저격: 한쪽 무릎을 꿇고 끝까지 당겨 겨눔 (2 ~ 15 — 조금 떨림) → 놓음 (16) · 반동
m = Motion("snipe", 26, "저격 — 무릎을 꿇고 끝까지 당겨 겨누다가 놓음", fade=(0.04, 0.1))
AIM = P(DRAW, body=[6, -24, 0], la=arm([0.25, 0.08, -0.25], 145), head=[2, 0], **LEGS_KNEEL)
keyp(m, 3, AIM, "out")
keyp(m, 9, P(AIM, body=[6, -25, 0]))
keyp(m, 15, P(AIM, body=[7, -24, 1]))
keyp(m, 17, P(AIM, body=[-6, -18, 0], la=arm([-0.35, 0.05, -0.75], 30), root=[-0.36, -0.08]), "out3")
keyp(m, 21, P(AIM, body=[-2, -18, 0], la=arm([-0.35, -0.2, -0.6], 35)))
m.fp(3, (-0.28, 0.06, -0.02), ease="out")
m.fp(15, (-0.28, 0.065, -0.02))
m.fp(17, (-0.26, 0.12, 0.12), ease="out3")
use(m, "snipe")

# 천궁: 하늘을 향해 천천히 당김 (0 ~ 15 — 하늘의 활이 같이 당겨짐) → 하늘로 놓음 (16)
m = Motion("sky_bow", 24, "천궁 — 하늘을 향해 천천히 당겼다가 하늘로 놓음", fade=(0.04, 0.1))
graft(m, "shoot_up", 2, 15, t_from=0.3, t_to=0.85)
keyp(m, 13, P(body=[-6, -20, 0], ra=arm([0.05, 0.8, 1], 0, [0, 1, 0]), la=arm([0.2, 0.42, -0.2], 145), head=[-20, 0]))
use(m, "skyBow")

# ------------------------------------------------------------ 검사

# 발도 일섬: 낮게 웅크려 칼자루를 쥐고 (0) 가로로 한 번에 베고 (닿음 1.5) 멈췄다가 (3 ~ 5) 칼을 거둠 (6 ~ 8)
m = Motion("quick_draw", 16, "발도 일섬 — 웅크려 칼을 뽑으며 가로로 한 번에 베고, 멈췄다가 칼을 거둠", fade=(0.03, 0.12))
keyp(m, 0.3, W_IAIDO)
strike(m, "slash_h", 0.5, 1.6, 3.5)
keyp(m, 5, P(ZANSHIN, body=[12, -30, 0]))
keyp(m, 8, W_SHEATHE, "out3")
keyp(m, 12, P(W_SHEATHE, body=[4, 6, 0]))
fpw(m, 5, FPW_ZANSHIN)
m.fp(8, (-0.4, -0.35, 0.2), None, (-0.85, -0.3, -0.4), None, "out3")
use(m, "quickDraw")

# 검기 날리기: 크게 휘둘러 (닿음 1.5) 초승달 검기를 날림
m = Motion("sword_wave", 12, "검기 날리기 — 크게 휘둘러 초승달 검기를 날림", fade=(0.03, 0.12))
strike(m, "skill_slash", 0, 1.6)
use(m, "swordWave")

# 반격 자세: 무기를 가로로 들고 막는 자세 (2 ~ 46) → 풀며 가로 베기 (50)
m = Motion("counter_stance", 58, "반격 자세 — 무기를 가로로 들고 버티다가 풀며 가로로 벰", fade=(0.03, 0.08))
keyp(m, 2.5, W_GUARD_H, "out3")
keyp(m, 20, P(W_GUARD_H, body=[11, 9, 0]))
keyp(m, 44, P(W_GUARD_H, body=[10, 12, 0]))
strike(m, "slash_h", 47, 50)
fpw(m, 2.5, FPW_ACROSS, "out3")
fpw(m, 44, FPW_ACROSS, dp=(0, 0.02, 0))
use(m, "counterStance")

# 삼단베기 (· 삼원소 연격): 가로 (0) → 세로 (10) → 찌르기 (20)
m = Motion("triple_cut", 28, "삼단베기 — 가로로 베고 세로로 내려 벤 뒤 앞으로 찌름", fade=(0.03, 0.1))
strike(m, "slash_h", 0, 1.5)
strike(m, "slash_v", 8.3, 10.5)
strike(m, "thrust", 18.3, 20.6)
use(m, "tripleCut", "triElement")

# 천지개벽: 두 손으로 무기를 오른쪽 뒤 낮게 모아 기운을 모음 (0 ~ 19 — 점점 낮게) → 거대한 가로 베기 (20)
m = Motion("heaven_split", 32, "천지개벽 — 두 손으로 무기를 뒤로 모아 검기를 모으다가 거대하게 가로로 벰", fade=(0.03, 0.1))
keyp(m, 3, W_CHARGE_LOW, "out3")
keyp(m, 11, P(W_CHARGE_LOW, rk=42, lk=44, root=[-0.18, 0]))
keyp(m, 18, P(W_CHARGE_LOW, body=[12, 58, 0], rk=48, lk=50, root=[-0.22, 0]))
strike(m, "skill_slash", 18.4, 20.6)
fpw(m, 3, FPW_BACK, "out3")
fpw(m, 18, FPW_BACK, dp=(0.1, -0.1, 0.05))
use(m, "heavenSplit")

# ------------------------------------------------------------ 마검사

# 마력 베기: 가로 베기 (닿음 1.5) → 맞힌 적에 마력이 터지는 때 (10) 왼손을 펴 내밂
m = Motion("arcane_cut", 16, "마력 베기 — 가로로 베고 왼손을 펴 내밀어 베인 자리의 마력을 터뜨림", fade=(0.03, 0.1))
m.set(hands={"main": "weapon", "off": "fist"})
strike(m, "slash_h", 0, 1.5)
keyp(m, 10, P(body=[8, 18, 0], ra=arm([-0.35, -0.65, 0.55], 25, [-0.35, -0.2, 0.9]), la=L(A_PALM), **LEGS_FIGHT), "out3")
keyp(m, 13, P(body=[6, 14, 0], ra=arm([-0.3, -0.7, 0.5], 25, [-0.35, -0.2, 0.9]), la=L(A_PALM), **LEGS_FIGHT))
fpl(m, 7, FP_LOW)
fpl(m, 10, FP_PALM, "out3")
fpl(m, 13, FP_PALM)
fpw(m, 10, FPW_LOW)
use(m, "arcaneCut")

# 마나 블라스터: 무기를 앞으로 겨누고 왼손으로 오른팔을 받친 채 광선을 쏨 (0 ~ 60 — 10 틱마다 반동)
m = Motion("mana_blaster", 64, "마나 블라스터 — 무기를 앞으로 겨누고 왼손으로 받쳐 광선을 쏘며 반동을 버팀", fade=(0.03, 0.06))
BEAM = P(body=[6, -16, 0], ra=arm([-0.02, 0.06, 1], 0, [0, 0.06, 1]), la=bent([0.3, -0.25, 0.92], 70, (1, 0.2, 0)), **LEGS_WIDE)
keyp(m, 1.5, BEAM, "out3")
fpw(m, 1.5, FPW_FWD, "out3")
for i in range(6):
    tick = 10 * i + 1.5
    keyp(m, tick + 1, P(BEAM, body=[2, -14, 0], root=[-0.04, -0.06]), "out")
    keyp(m, tick + 6, BEAM)
    fpw(m, tick + 1, FPW_FWD, "out", dp=(0, 0.02, 0.08))
    fpw(m, tick + 6, FPW_FWD)
use(m, "manaBlaster")

# 차원 베기: 꿰뚫고 지나간 뒤의 자세 (틱 0 — 이미 끝 자리 · 돌아섬) → 갈라진 자리가 터지는 때 (12) 무기를 털어 냄
m = Motion("rift_slash", 18, "차원 베기 — 꿰뚫고 지나간 자세에서 무기를 털어 갈라진 자리를 터뜨림", fade=(0.03, 0.12))
keyp(m, 0.5, P(body=[30, -40, 0], ra=arm([-0.8, 0.0, 0.5], 18, [-0.9, -0.1, -0.35]), la=arm([-0.5, -0.6, -0.3], 30), rl=[-40, 0, 0], ll=[30, 0, 0],
               rk=50, lk=36, root=[-0.22, 0.15]))
keyp(m, 7, P(body=[10, -20, 0], ra=arm([-0.4, -0.6, 0.6], 20, [-0.4, -0.2, 0.9]), la=L(A_GUARD), **LEGS_FIGHT))
keyp(m, 10.5, P(body=[6, 6, 0], ra=arm([0.3, 0.55, 0.6], 60, [0.2, 0.95, -0.2]), la=L(A_GUARD), **LEGS_FIGHT))
keyp(m, 12.5, P(body=[10, -10, 0], ra=arm([0.35, -0.6, 0.65], 12, [0.4, -0.75, 0.5]), la=L(A_GUARD), **LEGS_FIGHT), "out3")
fpw(m, 0.5, FPW_LOW, dp=(-1.0, 0.2, 0))
fpw(m, 7, FPW_LOW)
fpw(m, 10.5, FPW_UP)
fpw(m, 12.5, FPW_DOWN, "out3")
use(m, "riftSlash")

# 마검 결계: 무기를 얼굴 앞에 세우고 (0 ~ 3) 왼손으로 둘레를 한 바퀴 그려 (3 ~ 10) 검 넷을 띄움
m = Motion("orbit_blades", 16, "마검 결계 — 무기를 얼굴 앞에 세우고 왼손으로 둘레를 그려 마력 검을 띄움", fade=(0.04, 0.12))
m.set(hands={"main": "weapon", "off": "fist"})
keyp(m, 2.5, W_UP_FRONT, "out3")
for tick, d in ((4.5, [-0.85, 0.2, 0.5]), (6.5, [-0.4, 0.4, -0.75]), (8.5, [0.35, 0.3, 0.85]), (10.5, [-0.95, 0.05, 0.3])):
    keyp(m, tick, P(W_UP_FRONT, la=arm(d, 10)))
fpw(m, 2.5, FPW_UP, "out3")
fpw(m, 10.5, FPW_UP)
fpl(m, 4.5, ((0.3, 0.1, -0.15), (20, -30, 0)))
fpl(m, 6.5, ((0.25, 0.25, 0.0), (45, -20, 0)))
fpl(m, 8.5, ((0.0, 0.1, -0.35), (20, 45, 0)))
fpl(m, 10.5, ((0.35, 0.05, -0.15), (10, -35, 0)))
use(m, "orbitBlades")

# 마검 해방 (사용자 "마검 초월 같은거는 두손을 모으고 검을 위로 들고 기를 모으다가 아래로 내려찍는 모션으로"): 두 손을 모아 무기를 세우고 (1.5) →
# 두 손으로 머리 위로 곧게 들어 기를 모음 (4.5 ~ 23 — 눈앞에 거대한 마력 검이 솟고, 16 에 그 검이 가로로 벰) → 거대한 검이 앞으로 쓰러지며 내려찍는 때 (26) 함께 내려찍음
m = Motion("spellblade_release", 36, "마검 해방 — 두 손으로 무기를 머리 위로 들어 기를 모으다가 거대한 마력 검과 함께 내려찍음", fade=(0.03, 0.08))
charge_slam(m, 26)
use(m, "spellbladeRelease")

# ------------------------------------------------------------ 원소 검사

# 화염 베기: 낮게 디디며 무겁게 가로로 벰 (닿음 1.5)
m = Motion("flame_slash", 14, "화염 베기 — 낮게 디디며 불꽃 검으로 무겁게 가로로 벰", fade=(0.03, 0.12))
strike(m, "skill_slash", 0, 1.6)
keyp(m, 0.4, P(body=[4, 58, 0], ra=arm([0.75, 0.3, -0.6], 45, [0.3, 0.2, -0.95]), la=arm([-0.3, -0.3, 0.8], 40), rl=[22, 0, 0], ll=[-28, 0, 0], rk=36,
               lk=36, root=[-0.2, 0]))
use(m, "flameSlash")

# 서리 일섬: 앞으로 내디디며 곧게 찌름 (2) → 얼음 가시가 솟는 때 (10) 무기를 비틀어 거둠
m = Motion("frost_thrust", 16, "서리 일섬 — 내디디며 곧게 찌르고, 얼음 가시가 솟을 때 비틀어 거둠", fade=(0.03, 0.12))
strike(m, "thrust", 0, 2.2)
keyp(m, 10, P(body=[10, -12, 0], ra=arm([0.05, -0.1, 1], 20, [0.3, 0.3, 0.9]), la=L(A_GUARD), **LEGS_LUNGE))
fpw(m, 10, FPW_FWD, dp=(0, 0, 0.15))
use(m, "frostThrust")

# 뇌광 베기: 왼쪽에서 오른쪽으로 되돌려 벰 (닿음 1.5) → 번개가 떨어지는 때 (10) 무기를 하늘로 치켜듦
m = Motion("thunder_slash", 18, "뇌광 베기 — 왼쪽에서 오른쪽으로 되돌려 베고 무기를 하늘로 치켜들어 번개를 부름", fade=(0.03, 0.1))
keyp(m, 0.4, P(body=[6, -40, 0], ra=arm([-0.55, 0.25, 0.65], 75, [-0.85, 0.15, -0.45]), la=arm([-0.5, -0.5, 0.2], 40), rl=[10, 0, 0], ll=[-14, 0, 0],
               rk=16, lk=18))
keyp(m, 1.6, P(body=[10, 20, 0], ra=arm([0.4, 0.05, 0.9], 6, [0.85, 0, 0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-18, 0, 0], rk=14,
               lk=22, root=[-0.06, 0.12]), "linear")
keyp(m, 3.2, P(body=[10, 38, 0], ra=arm([0.85, 0.0, 0.4], 18, [0.85, -0.1, -0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-18, 0, 0], rk=12,
               lk=20, root=[-0.06, 0.12]), "out")
keyp(m, 9.5, P(W_SKY, body=[-14, 6, 0]), "out3")
keyp(m, 14, P(W_SKY, body=[-12, 4, 0]))
m.arc(1.6, "ra", [0.05, 0.05, 1])
m.set(tilt=30, trail=[[m.t(0.5), m.t(3.2)]])
m.fp(0.4, (-1.2, 0.35, 0.15), None, (-0.85, 0.12, -0.5), (0.5, 0, 0.85))
m.fp(1.6, (-0.1, 0.4, 0.05), None, (0.55, 0.1, 0.83), (0.83, 0, -0.55), "linear")
m.fp(3.2, (0.7, 0.32, 0.2), None, (0.9, 0, -0.42), (-0.42, 0, -0.9), "out")
fpw(m, 9.5, FPW_SKY, "out3")
fpw(m, 14, FPW_SKY)
use(m, "thunderSlash")

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

# 피의 초승달: 왼쪽에서 오른쪽으로 낫을 휘둘러 (닿음 1) 핏빛 초승달을 날림
m = Motion("blood_crescent", 14, "피의 초승달 — 낫을 왼쪽에서 오른쪽으로 휘둘러 핏빛 초승달을 날림", fade=(0.03, 0.12))
keyp(m, 0.3, P(body=[6, -44, 0], ra=arm([-0.6, 0.2, 0.6], 70, [-0.85, 0.1, -0.5]), la=arm([-0.5, -0.5, 0.25], 40), rl=[10, 0, 0], ll=[-16, 0, 0],
               rk=18, lk=20))
keyp(m, 1.4, P(body=[12, 18, 0], ra=arm([0.35, 0.0, 0.92], 6, [0.85, -0.05, 0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=16,
               lk=24, root=[-0.06, 0.14]), "linear")
keyp(m, 3, P(body=[12, 40, 0], ra=arm([0.85, 0.0, 0.4], 18, [0.85, -0.1, -0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=14,
             lk=22, root=[-0.06, 0.14]), "out")
keyp(m, 8, P(W_READY, body=[8, 14, 0]))
m.arc(1.4, "ra", [0.05, 0.0, 1])
m.set(tilt=30, trail=[[m.t(0.4), m.t(3)]])
m.fp(0.3, (-1.2, 0.35, 0.15), None, (-0.85, 0.12, -0.5), (0.5, 0, 0.85))
m.fp(1.4, (-0.1, 0.4, 0.05), None, (0.55, 0.1, 0.83), (0.83, 0, -0.55), "linear")
m.fp(3, (0.7, 0.32, 0.2), None, (0.9, 0, -0.42), (-0.42, 0, -0.9), "out")
fpw(m, 8, FPW_REST)
use(m, "bloodCrescent")

# 핏빛 수확: 오른쪽에서 왼쪽 (0) · 왼쪽에서 오른쪽 (10) 으로 낫을 두 번 크게
m = Motion("blood_harvest", 20, "핏빛 수확 — 낫을 오른쪽에서 왼쪽, 다시 왼쪽에서 오른쪽으로 두 번 크게 휘두름", fade=(0.03, 0.1))
strike(m, "lance_sweep", 0, 1.4)
keyp(m, 8.6, P(body=[6, -44, 0], ra=arm([-0.6, 0.2, 0.6], 70, [-0.85, 0.1, -0.5]), la=arm([-0.5, -0.5, 0.25], 40), rl=[10, 0, 0], ll=[-16, 0, 0],
               rk=18, lk=20))
keyp(m, 10.4, P(body=[12, 18, 0], ra=arm([0.35, 0.0, 0.92], 6, [0.85, -0.05, 0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=16,
                lk=24, root=[-0.06, 0.14]), "linear")
keyp(m, 12, P(body=[12, 40, 0], ra=arm([0.85, 0.0, 0.4], 18, [0.85, -0.1, -0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=14,
              lk=22, root=[-0.06, 0.14]), "out")
m.arc(10.4, "ra", [0.05, 0.0, 1])
m.head["trail"].append([m.t(8.8), m.t(12)])
m.fp(8.6, (-1.2, 0.35, 0.15), None, (-0.85, 0.12, -0.5), (0.5, 0, 0.85))
m.fp(10.4, (-0.1, 0.4, 0.05), None, (0.55, 0.1, 0.83), (0.83, 0, -0.55), "linear")
m.fp(12, (0.7, 0.32, 0.2), None, (0.9, 0, -0.42), (-0.42, 0, -0.9), "out")
use(m, "bloodHarvest")

# 피의 회오리: 낫을 앞으로 뻗고 오른쪽으로 네 바퀴 (효과의 참격 0 · 10 · 20 · 30 — 바퀴마다 낫이 앞을 지남)
m = Motion("blood_whirl", 44, "피의 회오리 — 낫을 앞으로 뻗고 오른쪽으로 네 바퀴 돌며 벰", fade=(0.03, 0.06))
spin_right(m, (2, 12, 22, 32), 41)
use(m, "bloodWhirl")

# 피의 낙인: 낫을 휘둘러 낙인을 던짐 (닿음 1) → 낙인이 터지는 때 (20) 왼 주먹을 움켜쥐며 당김
m = Motion("blood_brand", 26, "피의 낙인 — 낫을 휘둘러 낙인을 새기고, 주먹을 움켜쥐어 한꺼번에 터뜨림", fade=(0.03, 0.1))
m.set(hands={"main": "weapon", "off": "fist"})
strike(m, "slash_h", 0, 1.3)
keyp(m, 16, P(body=[6, -12, 0], ra=arm([-0.3, -0.7, 0.55], 25, [-0.35, -0.2, 0.9]), la=L(A_PALM), **LEGS_FIGHT))
keyp(m, 20.5, P(body=[10, 16, 0], ra=arm([-0.3, -0.7, 0.55], 25, [-0.35, -0.2, 0.9]), la=L(A_PULL), head=[4, 0], **LEGS_FIGHT), "out3")
keyp(m, 23, P(body=[8, 14, 0], ra=arm([-0.3, -0.7, 0.55], 25, [-0.35, -0.2, 0.9]), la=L(A_PULL), **LEGS_FIGHT))
fpl(m, 12, FP_LOW)
fpl(m, 16, FP_PALM)
fpl(m, 20.5, FP_CHAMBER, "out3")
fpl(m, 23, FP_CHAMBER)
fpw(m, 16, FPW_LOW)
use(m, "bloodBrand")

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

# 피의 칼날: 무기를 얼굴 앞에 세우고 (0 ~ 7 — 피가 무기로 모임) 왼 주먹을 꽉 쥠 → 털어 내림
m = Motion("blood_blade", 16, "피의 칼날 — 무기를 얼굴 앞에 세워 피의 기운을 모으고 주먹을 꽉 쥠", fade=(0.04, 0.12))
m.set(hands={"main": "weapon", "off": "fist"})
keyp(m, 2, P(W_UP_FRONT, la=L(A_CHEST)), "out3")
keyp(m, 7, P(W_UP_FRONT, la=L(A_CHEST), body=[-8, 0, 0]))
keyp(m, 10, P(W_READY, body=[10, -8, 0], ra=arm([0.3, -0.55, 0.65], 20, [0.4, -0.75, 0.5]), la=L(A_CHAMBER)), "out3")
fpw(m, 2, FPW_UP, "out3")
fpw(m, 7, FPW_UP)
fpw(m, 10, FPW_DOWN, "out3")
fpl(m, 2, ((-0.15, -0.05, 0.0), (20, 40, 0)), "out3")
fpl(m, 7, ((-0.15, -0.05, 0.0), (20, 40, 0)))
fpl(m, 10, FP_CHAMBER)
use(m, "bloodBlade")

# 검신 강림: 무기를 하늘로 치켜들어 (0 ~ 4) 하늘에서 떨어지는 거대한 검을 부르고 (6 에 박힘) 웅크려 버팀
m = Motion("sword_god", 16, "검신 강림 — 무기를 하늘로 치켜들어 거대한 검을 부르고, 검이 박힐 때 웅크려 버팀", fade=(0.04, 0.12))
keyp(m, 3, W_SKY, "out3")
keyp(m, 6.5, P(body=[18, 0, 0], ra=arm([0.25, -0.55, 0.6], 85, [0, 0.6, 0.8]), la=L(A_GUARD), rl=[-30, 0, 8], ll=[-30, 0, -8], rk=55, lk=55,
               root=[-0.22, 0]), "in")
keyp(m, 11, P(W_READY, body=[10, 0, 0]))
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 6.5, FPW_LOW, "in")
use(m, "swordGod")

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

# 대지 가르기: 두 팔을 들어 (0 ~ 9 — 대지의 대검이 커짐) 뒤로 젖혀 (9 ~ 19) 두 손으로 내려찍음 (24)
m = Motion("earth_splitter", 32, "대지 가르기 — 두 팔을 들어 대지의 대검을 세우고 뒤로 젖혔다가 두 손으로 내려찍음", fade=(0.03, 0.08))
keyp(m, 5, P(W_SKY, la=L(A_RAISE), body=[-8, 0, 0]), "out3")
keyp(m, 12, P(W_HIGH2, body=[-22, 4, 0], head=[-14, 0]))
keyp(m, 18.5, P(W_HIGH2, body=[-26, 4, 0], head=[-16, 0], ra=arm([0.12, 0.85, -0.5], 45, [0, 0.1, -1])))
strike(m, "slam", 19.5, 24.3)
fpw(m, 5, FPW_SKY, "out3")
fpw(m, 12, FPW_SKY, dp=(0, 0.05, 0.15))
fpw(m, 18.5, FPW_SKY, dp=(0, 0.08, 0.25))
use(m, "earthSplitter")
