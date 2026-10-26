"""0.47.0 직업 스킬 동작 — 궁수 · 암살자 · 창술사 · 사신 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱)."""
import rune_motion_kit as K
from rune_motion_kit import (A_CROSS, A_HIP, A_PALM, A_SPREAD_UP, BACK_END, BACK_HIT, BACK_WIND, CALL, FP_CHAMBER, FP_CROSS, FP_GUARD, FP_LOW, FP_PALM, FP_PALM_UP,
                             FP_RAISE, FP_SPREAD_UP, FPL_RAISE2H, FPW_BACK, FPW_DOWN, FPW_DRAWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, L, LEGS_FIGHT, LEGS_LUNGE,
                             LEGS_SQUAT, LEGS_WIDE, P, RA_SKY, RAISE2H, W_HIGH2, W_READY, arm, backhand, bent, fpl, fpr, fpw, graft, keyp, nudge, off_bare,
                             spin_right, strike, use_skill)

Motion = K.Motion

# ------------------------------------------------------------ 궁수 (활 — 오른손에 활, 왼손이 시위를 당김 — 평타 shoot 와 같은 꼴)

BOW_UP = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.35, 0.15, 0.6], 120))  # 활을 들고 시위에 손
BOW_DRAWN = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140))  # 얼굴까지 당김
BOW_LOOSE = P(body=[-4, -16, 0], ra=arm([0.05, 0.16, 1], 0, [0, 1, -0.2]), la=arm([-0.3, 0.0, -0.7], 40), root=[0, -0.05])  # 놓음
FP_BOW = dict(p=(-0.25, 0.08, 0.0))
FP_BOW_LOOSE = dict(p=(-0.25, 0.1, 0.1))

# 속사 (효과: 0 · 3 · 6 (· 9 — 호흡 3 이상) 에 빛의 화살)
m = Motion("archer_rapid", 14, "속사 — 활을 겨눈 채 시위를 당겼다 놓기를 빠르게 되풀이 (세 · 네 발)", fade=(0.03, 0.12))
keyp(m, 0.3, BOW_DRAWN, "out3")
fpw(m, 0.3, FP_BOW, "out3")
for t in (0, 3, 6, 9):
    keyp(m, t + 0.7, BOW_LOOSE, "out3")
    fpw(m, t + 0.7, FP_BOW_LOOSE, "out3")
    if t < 9:
        keyp(m, t + 1.7, BOW_UP)
        keyp(m, t + 2.6, BOW_DRAWN, "in")
        fpw(m, t + 1.7, FP_BOW)
keyp(m, 12, BOW_UP)
use_skill(m, "archer_rapid")

# 바람 타기 (효과: 4틱 동안 움직이는 쪽 (서 있으면 뒤) 5칸을 미끄러짐 — 다음 평타 두 번이 두 발)
SLIDE = P(body=[-4, -20, 0], head=[2, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140), rl=[-30, 0, 6], ll=[40, 0, -6], rk=10, lk=70,
          root=[-0.32, -0.1])  # 시위를 당긴 채 낮게 미끄러짐 (한 다리를 뻗음)
m = Motion("archer_wind", 12, "바람 타기 — 시위를 당긴 채 한 다리를 뻗고 낮게 미끄러졌다가 일어남", fade=(0.03, 0.12))
keyp(m, 0.5, SLIDE, "out3")
keyp(m, 5, P(SLIDE, root=[-0.3, -0.08]))
keyp(m, 8, BOW_UP)
fpw(m, 0.5, dict(p=(-0.25, 0.0, 0.0)), "out3")
fpw(m, 8, FP_BOW)
use_skill(m, "archer_wind")

# 화살비 (효과: 조준한 곳에 과녁 → 6 · 16 · 26 · 36 · 46 에 하늘에서 화살비)
m = Motion("archer_rain", 16, "화살비 — 활을 하늘로 들어 당겼다가 높이 쏘아 올림", fade=(0.03, 0.12))
strike(m, "shoot_up", 0.6, 4.0)
keyp(m, 9, P(body=[-6, -14, 0], head=[-20, 0], ra=arm([0.05, 0.7, 1], 2, [0, 1, -0.4]), la=arm([-0.3, -0.2, -0.6], 40)))
use_skill(m, "archer_rain")

# 정조준 (태세 — 효과: 눈앞 과녁이 조여 듦 · 몸의 빛)
m = Motion("archer_focus", 26, "정조준 — 활을 들어 천천히 끝까지 당기고 숨을 멈춘 채 겨눴다가 시위를 늦춤", fade=(0.04, 0.12))
keyp(m, 2.5, BOW_UP, "out3")
keyp(m, 10, P(BOW_DRAWN, head=[2, 0], la=arm([0.15, 0.06, -0.35], 145)), "linear")
keyp(m, 18, P(BOW_DRAWN, head=[2, 0], la=arm([0.15, 0.06, -0.35], 145)))
keyp(m, 21.5, P(body=[0, -10, 0], ra=arm([0.15, -0.55, 0.75], 10, [0, 0.9, 0.3]), la=arm([-0.25, -0.8, 0.3], 20)), "out")
fpw(m, 2.5, FP_BOW, "out3")
fpw(m, 18, dict(p=(-0.27, 0.1, -0.02)))
fpw(m, 21.5, dict(p=(-0.1, -0.35, 0.1)), "out")
use_skill(m, "archer_focus")

# 관천일시 (효과: 0 ~ 24 눈앞에 커지는 빛의 활 (시위를 당김) → 24 거대한 빛의 화살 → 36 부터 잔상이 터짐)
ULT_UP = P(body=[-6, -24, 0], head=[-4, 0], ra=arm([0.05, 0.18, 1], 0, [0, 1, 0]), la=arm([0.35, 0.2, 0.6], 120), rl=[-16, 0, 8], ll=[24, 0, -8], rk=20, lk=30)
ULT_DRAW = P(body=[-10, -26, 0], head=[-6, 0], ra=arm([0.05, 0.18, 1], 0, [0, 1, 0]), la=arm([0.1, 0.1, -0.6], 150), rl=[-18, 0, 8], ll=[26, 0, -8], rk=24,
             lk=34, root=[-0.1, -0.05])
m = Motion("archer_ult", 36, "관천일시 — 다리를 벌려 버티고 활을 높이 들어 1초 넘게 끝까지 당겼다가 (떨림) 거대한 화살을 놓으며 뒤로 밀려남", fade=(0.03, 0.1))
keyp(m, 1.5, ULT_UP, "out3")
keyp(m, 12, P(ULT_DRAW, la=arm([0.15, 0.08, -0.35], 145)), "linear")
for i, t in enumerate((15, 18, 20.5, 22.5)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(ULT_DRAW, body=[-10, -26, s * 1.5], root=[-0.1 - 0.01 * i, -0.05]))
strike(m, "shoot", 22.0, 24.2)
keyp(m, 27, P(BOW_LOOSE, body=[-12, -14, 0], root=[-0.04, -0.3], rl=[-24, 0, 8], ll=[30, 0, -8], rk=20, lk=30))
fpw(m, 1.5, dict(p=(-0.25, 0.14, 0.05)), "out3")
fpw(m, 12, dict(p=(-0.27, 0.15, 0.08)), "linear")
fpw(m, 21.5, dict(p=(-0.26, 0.16, 0.1)))
fpw(m, 27, dict(p=(-0.25, 0.16, 0.25)))
use_skill(m, "archer_ult")

# ------------------------------------------------------------ 암살자 (단검 — 자홍 · 짙은 보라)

# 낙화 (효과: 0 에 겨눈 적의 표식이 터짐 — 2틱마다 엇갈린 베기 · 꽃잎)
m = Motion("assassin_bloom", 12, "낙화 — 단검으로 허공을 X 자로 두 번 긋고 칼끝으로 적을 가리키며 왼손을 펼쳐 꽃을 흩뜨림", fade=(0.03, 0.12))
off_bare(m)
graft(m, "dagger_x", 0, 5)
keyp(m, 7.5, P(body=[6, -20, 0], ra=arm([0.0, 0.1, 1], 4, [0, 0.4, 0.9]), la=arm([-0.6, 0.3, 0.5], 10), **LEGS_FIGHT), "out3")
fpw(m, 7.5, dict(p=(-0.3, 0.2, -0.25), blade=(0, 0.45, 0.9)), "out3")
fpw(m, 10.5, FPW_LOW)
fpl(m, 4.5, FP_LOW)
fpl(m, 7.5, nudge(FP_SPREAD_UP, (-0.05, -0.1, -0.1)), "out3")
fpl(m, 10.5, FP_LOW)
use_skill(m, "assassin_bloom")

# 잔영 (효과: 3틱 동안 앞 6칸 순간 이동 — 원래 자리에 분신)
NINJA = P(body=[40, 0, 0], head=[-20, 0], ra=arm([0.3, -0.3, -0.9], 10, [0.2, 0.3, -0.9]), la=arm([-0.3, -0.3, -0.9], 10), rl=[-50, 0, 0], ll=[40, 0, 0],
          rk=60, lk=30, root=[-0.2, 0.2])  # 몸을 숙이고 두 팔을 뒤로 젖혀 달림
CROUCH_D = P(body=[24, 0, 0], head=[-6, 0], ra=arm([0.4, -0.5, 0.7], 60, [0.2, -0.2, -0.95]), la=arm([-0.2, -0.9, 0.3], 10), **LEGS_SQUAT)
m = Motion("assassin_shade", 12, "잔영 — 몸을 숙이고 두 팔을 뒤로 젖혀 그림자처럼 내달린 뒤 낮게 내려앉음", fade=(0.02, 0.12))
keyp(m, 0.4, NINJA, "out3")
keyp(m, 3, NINJA)
keyp(m, 4.4, CROUCH_D, "out3")
keyp(m, 8, P(CROUCH_D, root=[-0.3, 0]))
fpw(m, 0.4, FPW_BACK, "out3")
fpw(m, 3, FPW_BACK)
fpw(m, 4.4, dict(p=(0.0, -0.35, 0.1), blade=(0.2, -0.3, -0.9)), "out3")
fpw(m, 9, FPW_LOW)
use_skill(m, "assassin_shade")

# 귀환 비수 (효과: 0 에 여덟 방향으로 비수 (8틱) → 4틱 멈춤 → 8틱에 돌아옴 (20))
ARMS_CROSS = P(body=[10, 0, 0], head=[6, 0], ra=bent([-0.3, -0.35, 0.85], 110, (1, 0.2, 0)), la=L(bent([-0.3, -0.35, 0.85], 110, (1, 0.2, 0))), rk=24, lk=24,
               root=[-0.1, 0])
ARMS_OUT = P(body=[-6, 0, 0], head=[-6, 0], ra=arm([1, 0.15, 0.1], 4, [1, 0.1, 0.2]), la=L(arm([1, 0.15, 0.1], 4)), rl=[0, 0, 12], ll=[0, 0, -12], rk=8, lk=8)
m = Motion("assassin_daggers", 24, "귀환 비수 — 두 팔을 엇갈려 모았다가 한 바퀴 돌며 두 팔을 펼쳐 비수를 사방으로 날리고, 돌아오는 비수를 엇갈린 팔로 받음",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 0.2, ARMS_CROSS)
keyp(m, 1.6, P(ARMS_OUT, spin=-200), "out3")
keyp(m, 3.4, P(ARMS_OUT, spin=-360), "out")
keyp(m, 16, P(ARMS_OUT, ra=arm([0.85, -0.1, 0.4], 10, [0.8, 0.1, 0.55]), la=L(arm([0.85, -0.1, 0.4], 10)), spin=-360))
keyp(m, 19.6, P(ARMS_OUT, ra=arm([0.6, 0.1, 0.75], 10, [0.4, 0.3, 0.85]), la=L(arm([0.6, 0.1, 0.75], 10)), spin=-360))
keyp(m, 21, P(ARMS_CROSS, spin=-360), "in")
m.keys[1.0] = {"spin": -360}
fpw(m, 0.2, dict(p=(-0.5, 0.0, 0.05), blade=(-0.6, 0.6, 0.5)))
fpw(m, 1.6, FPW_SPREAD, "out3")
fpw(m, 19.6, FPW_SPREAD)
fpw(m, 21, dict(p=(-0.5, 0.02, 0.05), blade=(-0.6, 0.6, 0.5)), "in")
fpw(m, 23, FPW_LOW)
fpl(m, 0.2, FP_CROSS)
fpl(m, 1.6, FP_LOW, "out3")
fpl(m, 19.6, nudge(FP_PALM, (0.1, -0.05, 0.1)))
fpl(m, 21, FP_CROSS, "in")
fpl(m, 23, FP_LOW)
use_skill(m, "assassin_daggers")

# 암영 (은신 — 효과: 그림자 연기 · 흩어지는 분신 · 1인칭 눈앞 연기)
HIDE = P(body=[30, 0, 0], head=[20, 0], ra=arm([0.3, -0.6, 0.5], 70, [0, 0.3, -1]), la=bent([0.35, 0.4, 0.85], 120, (0.8, 0.5, 0)), rl=[-40, 0, 6],
         ll=[-40, 0, -6], rk=70, lk=70, root=[-0.35, 0])  # 낮게 웅크려 왼팔로 얼굴을 가림 (단검은 거꾸로 쥐고 낮게)
m = Motion("assassin_veil", 18, "암영 — 낮게 웅크리며 왼팔로 얼굴을 가려 그림자 속으로 숨었다가 일어남", fade=(0.04, 0.12))
off_bare(m)
keyp(m, 2, HIDE, "out3")
keyp(m, 10, P(HIDE, root=[-0.33, 0]))
keyp(m, 14, P(W_READY, body=[10, 0, 0]))
fpw(m, 2, dict(p=(0.05, -0.4, 0.1), blade=(0.2, 0.3, -0.9)), "out3")
fpw(m, 14, FPW_LOW)
fpl(m, 2, nudge(FP_CROSS, (0.0, 0.12, 0.05), (20, 0, 0)), "out3")
fpl(m, 9, nudge(FP_CROSS, (0.0, 0.1, 0.05), (20, 0, 0)))
fpl(m, 12, FP_LOW)
use_skill(m, "assassin_veil")

# 흑야 (효과: 0 · 5 · 10 · 15 · 20 · 25 에 적 곁으로 순간 이동해 벰 → 30 처음 자리로 → 40 벤 자국이 한꺼번에 터짐). 베기는 서버가 그 틱에 :slash0 · :slash1 (번갈아),
# 돌아옴 :home, 터지기 3틱 전 :final
m = Motion("assassin_ult_slash0", 6, "흑야 — 적 곁에 나타나 단검을 X 자로 그음", fade=(0.03, 0.05))
graft(m, "dagger_x", 0, 4.2)
use_skill(m, "assassin_ult", "assassin_ult:slash0")
m = Motion("assassin_ult_slash1", 6, "흑야 — 적 곁에 나타나 단검을 크게 되돌려 그음", fade=(0.03, 0.05))
backhand(m, 0.1, 1.0, 2.4)
use_skill(m, "assassin_ult:slash1")
m = Motion("assassin_ult_home", 12, "흑야 — 처음 자리로 돌아와 단검을 털고 거꾸로 쥐어 옆으로 내림", fade=(0.04, 0.05))
keyp(m, 1.6, P(body=[4, 20, 0], ra=arm([0.65, -0.3, 0.6], 14, [0.6, -0.5, 0.6]), la=L(A_HIP), **LEGS_FIGHT), "in")
keyp(m, 4, P(body=[2, 10, 0], head=[6, 0], ra=arm([0.3, -0.85, 0.2], 20, [0.1, 0.4, -0.9]), la=L(A_HIP), rl=[0, 0, 3], ll=[0, 0, -3]), "out3")
keyp(m, 11, P(body=[2, 10, 0], head=[6, 0], ra=arm([0.3, -0.85, 0.2], 20, [0.1, 0.4, -0.9]), la=L(A_HIP), rl=[0, 0, 3], ll=[0, 0, -3]))
m.keys[1.0] = {"body": [2, 10, 0], "head": [6, 0], "ra": arm([0.3, -0.85, 0.2], 20, [0.1, 0.4, -0.9]), "la": L(A_HIP)}
fpw(m, 1.6, FPW_SPREAD, "in")
fpw(m, 4, dict(p=(0.15, -0.45, 0.1), blade=(0.1, 0.4, -0.9)), "out3")
use_skill(m, "assassin_ult:home")
m = Motion("assassin_ult_final", 10, "흑야 끝 — 거꾸로 쥔 단검을 허리로 탁 거두는 순간 벤 자국이 모두 터짐", fade=(0.0, 0.15))
keyp(m, 0.5, P(body=[2, 10, 0], head=[6, 0], ra=arm([0.3, -0.85, 0.2], 20, [0.1, 0.4, -0.9]), la=L(A_HIP), rl=[0, 0, 3], ll=[0, 0, -3]))
keyp(m, 3, P(body=[4, -10, 0], head=[-4, 0], ra=bent([-0.3, -0.8, 0.4], 70, (0, 0, 1)), la=L(A_HIP), rl=[0, 0, 3], ll=[0, 0, -3]), "in")
keyp(m, 8, P(body=[4, -10, 0], head=[-8, 0], ra=bent([-0.3, -0.8, 0.4], 70, (0, 0, 1)), la=L(A_HIP)))
fpw(m, 0.5, dict(p=(0.15, -0.45, 0.1), blade=(0.1, 0.4, -0.9)))
fpw(m, 3, dict(p=(-0.6, -0.6, 0.2), blade=(-0.5, -0.4, -0.75)), "in")
use_skill(m, "assassin_ult:final")

# ------------------------------------------------------------ 창술사 (창 — 금빛 · 흰빛)

# 삼연창 (효과: 0 · 5 · 10 에 찌름 — 셋째는 두 줄 관통)
m = Motion("lancer_triple", 16, "삼연창 — 창을 빠르게 세 번 찌름 (마지막은 깊게 내디디며)", fade=(0.03, 0.1))
strike(m, "thrust", 0.1, 0.9, 2.6)
strike(m, "thrust", 4.1, 5.4, 7.0)
strike(m, "thrust", 9.0, 10.4, 12.0)
keyp(m, 12.8, P(body=[18, -20, 0], ra=arm([0.04, 0.02, 1], 2, [0, 0.02, 1]), la=arm([-0.4, -0.6, -0.4], 30), **LEGS_LUNGE))
use_skill(m, "lancer_triple")

# 장대 도약 (효과: 0 에 창을 짚고 10틱 포물선으로 7칸 앞까지 → 10 에 내리찍음)
PLANT = P(body=[24, 0, 0], ra=arm([0.05, -0.35, 0.94], 30, [0, -0.95, 0.3]), la=arm([-0.05, -0.3, 0.95], 30), rl=[-30, 0, 0], ll=[20, 0, 0], rk=50, lk=30,
          root=[-0.2, 0.1])  # 창을 앞 땅에 짚음
VAULT = P(body=[-30, 0, 0], head=[10, 0], ra=arm([0.05, -0.8, 0.5], 10, [0, -0.98, 0.2]), la=arm([-0.05, -0.8, 0.5], 10), rl=[-80, 0, 0], ll=[-70, 0, 0],
          rk=40, lk=40, root=[0.4, 0])  # 창을 밀어 두 다리를 앞 위로 차올림
m = Motion("lancer_vault", 14, "장대 도약 — 창을 앞 땅에 짚고 다리를 차올려 넘어간 뒤 공중에서 창을 머리 위로 들어 내리찍음", fade=(0.03, 0.1))
keyp(m, 0.5, PLANT, "out3")
keyp(m, 2.6, VAULT, "out3")
keyp(m, 5.6, P(W_HIGH2, rl=[-60, 0, 0], ll=[-30, 0, 0], rk=90, lk=80, root=[0.3, 0]))
strike(m, "slam", 8.0, 10.0)
fpw(m, 0.5, FPW_DOWN, "out3")
fpw(m, 2.6, dict(p=(-0.35, -0.3, -0.1), blade=(0.05, -0.95, 0.3)), "out3")
fpw(m, 5.6, FPW_UP)
use_skill(m, "lancer_vault")

# 선회창 (효과: 0 · 10 에 머리 위로 돌린 창이 둘레를 벰 — 둘째는 밀쳐냄)
OVER_SPIN = P(body=[-4, -4, 0], head=[-10, 0], ra=arm([0.25, 0.95, 0.1], 25, [1, 0.05, 0.25]), la=arm([-0.25, 0.95, 0.1], 25), rk=20, lk=20,
              root=[-0.06, 0])  # 두 손으로 창을 머리 위에 가로로 들고 돎
m = Motion("lancer_cyclone", 16, "선회창 — 창을 머리 위로 들어 가로로 돌리며 두 바퀴 (둘째 바퀴에 밀쳐냄)", fade=(0.03, 0.1))
spin_right(m, (1.0, 10.0), 13.6, wind_tick=0.2, fwd=OVER_SPIN, fp_dp=(0, 0.55, 0))
use_skill(m, "lancer_cyclone")

# 창벽 (태세 — 효과: 앞으로 펼쳐지는 빛의 창 다섯)
BRACE = P(body=[16, -28, 0], head=[-6, 0], ra=arm([0.25, -0.5, 0.85], 40, [-0.05, 0.05, 1]), la=arm([-0.1, -0.2, 0.98], 20), rl=[22, 0, 6], ll=[-34, 0, -4],
          rk=36, lk=46, root=[-0.24, 0.05])  # 낮게 버티고 창을 앞으로 겨눔
m = Motion("lancer_wall", 26, "창벽 — 다리를 벌리고 낮게 버텨 창을 앞으로 겨눔 (창끝이 벽처럼)", fade=(0.04, 0.12))
keyp(m, 2.5, BRACE, "out3")
keyp(m, 18, P(BRACE, root=[-0.22, 0.05]))
keyp(m, 22, P(W_READY, body=[8, -6, 0]))
fpw(m, 2.5, FPW_FWD, "out3", dp=(0.05, -0.15, 0))
fpw(m, 18, FPW_FWD, dp=(0.05, -0.15, 0))
fpw(m, 22, FPW_LOW)
use_skill(m, "lancer_wall")

# 천창유성 (효과: 0 에 창을 하늘로 던짐 → 10 에 하늘에서 갈라짐 → 20 · 30 · 40 · 50 에 쏟아짐 → 65 진짜 창이 떨어져 폭발). 물결은 서버가 1틱 전에 :wave,
# 진짜 창은 7틱 전에 :final (창이 손을 떠난 동안 빈손 — thrown)
m = Motion("lancer_ult", 22, "천창유성 — 창을 등 뒤로 당겼다가 하늘 높이 던지고 빈손을 하늘로 뻗음", fade=(0.03, 0.05))
m.set(hands={"main": "thrown"})
keyp(m, 0.8, P(body=[-14, 30, 0], head=[-20, 0], ra=arm([0.45, 0.35, -0.8], 70, [0.2, 0.9, -0.3]), la=arm([-0.2, 0.6, 0.75], 10), rl=[18, 0, 0],
               ll=[-24, 0, 0], rk=20, lk=16), "out3")
keyp(m, 2.2, P(body=[-22, -10, 0], head=[-34, 0], ra=arm([0.08, 0.98, 0.15], 4, [0, 1, 0.1]), la=arm([-0.4, -0.4, 0.5], 20), rl=[14, 0, 0], ll=[-22, 0, 0],
               rk=12, lk=18), "out3")
keyp(m, 8, P(CALL, ra=arm([0.12, 1, 0.08], 5)))
keyp(m, 20, P(CALL, ra=arm([0.12, 1, 0.08], 5), head=[-34, 0]))
m.set(hide=[m.t(2.1), 1.0])
fpw(m, 0.8, FPW_DRAWN, "out3")
fpr(m, 2.4, FP_RAISE, "out3")
fpr(m, 20, FP_RAISE)
use_skill(m, "lancer_ult")
m = Motion("lancer_ult_wave", 10, "천창유성 — 빈손을 하늘에서 앞 땅으로 휘둘러 창비를 쏟아지게 함 (한 물결)", fade=(0.05, 0.1))
m.set(hands={"main": "thrown"}, hide=[0.0, 1.0])
keyp(m, 0.4, P(CALL, ra=arm([0.12, 1, 0.08], 5)))
keyp(m, 1.6, P(body=[8, -10, 0], head=[-10, 0], ra=arm([0.05, 0.1, 1], 2), la=L(A_HIP), **LEGS_FIGHT), "out3")
keyp(m, 8, P(body=[6, -10, 0], head=[-14, 0], ra=arm([0.05, 0.15, 1], 4), la=L(A_HIP), **LEGS_FIGHT))
fpr(m, 0.4, FP_RAISE)
fpr(m, 1.6, nudge(FP_PALM, (0, 0.05, -0.05)), "out3")
fpr(m, 8, FP_PALM)
use_skill(m, "lancer_ult:wave")
m = Motion("lancer_ult_final", 18, "천창유성 끝 — 빈손을 하늘로 들어 진짜 창을 부르고 내리꽂히는 순간 주먹을 쥐어 당김 → 창이 손으로 돌아옴", fade=(0.03, 0.1))
m.set(hands={"main": "thrown"}, hide=[0.0, m.t(10.5)])
keyp(m, 2, P(CALL, ra=arm([0.12, 1, 0.08], 5)), "out3")
keyp(m, 6.4, P(CALL, ra=arm([0.12, 1, 0.08], 5), head=[-30, 0]))
keyp(m, 7.4, P(body=[16, -6, 0], head=[0, 0], ra=bent([0.3, -0.4, 0.4], 120, (0, 0.3, 1)), la=L(A_HIP), **LEGS_FIGHT), "in")
keyp(m, 12, P(W_READY, body=[8, 0, 0]), "out")
fpr(m, 2, FP_RAISE, "out3")
fpr(m, 6.4, FP_RAISE)
fpr(m, 7.4, nudge(FP_PALM_UP, (0.05, -0.25, 0.15), (-30, 0, 0)), "in")
m.fp(10.6, (0.0, -0.5, 0.1), None, (0.1, 0.7, 0.7))
fpw(m, 13, FPW_LOW, "out")
use_skill(m, "lancer_ult:final")

# ------------------------------------------------------------ 사신 (낫 — 청록 · 짙은 청록)

# 갈퀴 베기 (효과: 0 = 왼쪽 → 오른쪽, 10 = 오른쪽 → 왼쪽 — 적을 끌어당김)
m = Motion("reaper_reap", 18, "갈퀴 베기 — 낫을 왼쪽에서 오른쪽으로 크게 휘두르고 되돌려 오른쪽에서 왼쪽으로 끌어당기듯 벰", fade=(0.03, 0.1))
backhand(m, 0.2, 1.4, 3.6, end=P(BACK_END, body=[10, 46, 0], ra=arm([0.85, -0.1, 0.0], 30, [0.4, -0.2, -0.9])))
strike(m, "skill_slash", 8.2, 10.6)
keyp(m, 14, P(body=[14, -30, 0], ra=arm([-0.5, -0.5, 0.55], 40, [-0.4, -0.6, -0.7]), la=arm([-0.3, -0.7, 0.3], 30), rl=[18, 0, 0], ll=[-26, 0, 0], rk=20,
              lk=30, root=[-0.1, -0.1]))  # 당겨 들임
use_skill(m, "reaper_reap")

# 망령 걸음 (효과: 6틱 동안 7칸을 망령으로 지나감)
GHOST = P(body=[34, 0, 0], head=[-18, 0], ra=arm([0.3, -0.4, -0.85], 10, [0, -0.2, -1]), la=arm([-0.3, -0.4, -0.85], 10), rl=[18, 0, 0], ll=[24, 0, 0], rk=36,
          lk=40, root=[0.12, 0.1])  # 앞으로 기울어 떠서 미끄러짐 (낫과 팔이 뒤로 끌림)
m = Motion("reaper_wraith", 12, "망령 걸음 — 몸을 앞으로 기울이고 발이 뜬 채 낫과 팔을 뒤로 끌며 미끄러지듯 지나감", fade=(0.03, 0.12))
keyp(m, 0.5, GHOST, "out3")
keyp(m, 6, P(GHOST, root=[0.14, 0.1]))
keyp(m, 8.5, P(W_READY, body=[10, 0, 0]))
fpw(m, 0.5, FPW_BACK, "out3")
fpw(m, 6, FPW_BACK)
fpw(m, 8.5, FPW_LOW)
use_skill(m, "reaper_wraith")

# 혼백 폭발 (효과: 0 ~ 3 혼불이 몸으로 모임 → 3 에 둘레 폭발)
m = Motion("reaper_burst", 14, "혼백 폭발 — 웅크려 낫을 끌어안듯 혼을 모았다가 두 팔을 펼치며 터뜨림", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 2, P(body=[24, 0, 0], head=[20, 0], ra=arm([0.1, -0.4, 0.9], 100, [-0.6, 0.7, 0.3]), la=L(A_CROSS), rk=50, lk=50, root=[-0.24, 0]), "out3")
keyp(m, 3.8, P(body=[-14, 0, 0], head=[-28, 0], ra=arm([0.85, 0.3, 0.3], 10, [0.6, 0.7, 0.3]), la=L(A_SPREAD_UP), rl=[0, 0, 14], ll=[0, 0, -14], rk=8, lk=8,
               root=[0.06, 0]), "out3")
keyp(m, 9, P(body=[-10, 0, 0], head=[-22, 0], ra=arm([0.85, 0.25, 0.3], 10, [0.6, 0.7, 0.3]), la=L(A_SPREAD_UP), rl=[0, 0, 12], ll=[0, 0, -12]))
fpw(m, 2, dict(p=(-0.35, -0.2, 0.1), blade=(-0.6, 0.6, 0.4)), "out3")
fpw(m, 3.8, FPW_SPREAD, "out3")
fpw(m, 9, FPW_SPREAD)
fpw(m, 12, FPW_LOW)
fpl(m, 2, FP_CROSS, "out3")
fpl(m, 3.8, FP_SPREAD_UP, "out3")
fpl(m, 9, FP_SPREAD_UP)
fpl(m, 11.5, FP_LOW)
use_skill(m, "reaper_burst")

# 사신 현신 (태세 — 효과: 땅에서 솟는 거대한 사신 그림자 · 연기 · 혼불)
m = Motion("reaper_avatar", 26, "사신 현신 — 두 손으로 낫을 머리 위로 높이 들어 사신을 부르고 옆으로 크게 내려 휘두름", fade=(0.04, 0.1))
off_bare(m)
keyp(m, 3, P(RAISE2H, head=[-30, 0]), "out3")
keyp(m, 10, P(RAISE2H, head=[-30, 0], body=[-12, 0, 0], rk=18, lk=18, root=[-0.05, 0]))
keyp(m, 13.6, P(body=[10, 30, 0], ra=arm([0.8, -0.4, 0.4], 10, [0.7, -0.5, 0.5]), la=L(A_HIP), **LEGS_FIGHT), "in")
keyp(m, 18, P(W_READY, body=[6, 14, 0]))
fpw(m, 3, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 10, FPW_UP, dp=(0.12, -0.08, 0))
fpw(m, 13.6, FPW_SPREAD, "in")
fpw(m, 18, FPW_LOW)
fpl(m, 3, FPL_RAISE2H, "out3")
fpl(m, 10, FPL_RAISE2H)
fpl(m, 13, FP_LOW)
use_skill(m, "reaper_avatar")

# 황천문 (효과: 0 ~ 10 조준한 곳에 명계의 문이 솟음 → 10 · 20 · … · 60 에 혼을 빨아냄 → 75 문이 닫히며 큰 피해). 빨아냄은 서버가 1틱 전에 :drain,
# 닫힘은 4틱 전에 :close
m = Motion("reaper_ult", 14, "황천문 — 낫을 치켜들고 왼손을 문 쪽으로 뻗어 아래에서 위로 쓸어 올려 문을 엶", fade=(0.04, 0.06))
off_bare(m)
keyp(m, 2, P(body=[-4, -10, 0], head=[-6, 0], ra=arm([0.5, 0.75, 0.3], 30, [0.2, 1, -0.2]), la=arm([-0.05, -0.35, 0.94], 4), **LEGS_WIDE), "out3")
keyp(m, 8.5, P(body=[-8, -12, 0], head=[-14, 0], ra=arm([0.5, 0.75, 0.3], 30, [0.2, 1, -0.2]), la=arm([-0.05, 0.55, 0.83], 4), **LEGS_WIDE), "out3")
keyp(m, 13, P(body=[-6, -12, 0], head=[-12, 0], ra=arm([0.5, 0.7, 0.3], 30, [0.2, 1, -0.2]), la=arm([-0.05, 0.5, 0.86], 6), **LEGS_WIDE))
fpw(m, 2, dict(p=(0.1, 0.5, 0.2), blade=(0.2, 1, -0.2)), "out3")
fpw(m, 13, dict(p=(0.1, 0.48, 0.2), blade=(0.2, 1, -0.2)))
fpl(m, 2, nudge(FP_PALM, (0, -0.25, 0.05), (-25, 0, 0)), "out3")
fpl(m, 8.5, nudge(FP_PALM_UP, (0, 0.05, 0)), "out3")
fpl(m, 13, FP_PALM_UP)
use_skill(m, "reaper_ult")
m = Motion("reaper_ult_drain", 10, "황천문 — 문 쪽으로 뻗은 왼손을 움켜쥐며 끌어당겨 혼을 빨아냄", fade=(0.05, 0.1))
off_bare(m)
keyp(m, 0.6, P(body=[-2, -12, 0], ra=arm([0.5, 0.7, 0.3], 30, [0.2, 1, -0.2]), la=arm([-0.05, 0.25, 0.97], 2), **LEGS_WIDE))
keyp(m, 3.6, P(body=[-10, -18, 0], head=[-6, 0], ra=arm([0.5, 0.7, 0.3], 30, [0.2, 1, -0.2]), la=bent([-0.2, -0.2, 0.6], 115, (0.3, 1, 0.2)),
               root=[-0.04, -0.08], **LEGS_WIDE), "out3")
keyp(m, 9, P(body=[-6, -14, 0], ra=arm([0.5, 0.7, 0.3], 30, [0.2, 1, -0.2]), la=bent([-0.15, -0.1, 0.75], 90, (0.3, 1, 0.2)), **LEGS_WIDE))
fpw(m, 0.6, dict(p=(0.1, 0.48, 0.2), blade=(0.2, 1, -0.2)))
fpw(m, 9, dict(p=(0.1, 0.48, 0.2), blade=(0.2, 1, -0.2)))
fpl(m, 0.6, FP_PALM)
fpl(m, 3.6, nudge(FP_CHAMBER, (0.05, 0.1, -0.05), (15, 20, 0)), "out3")
fpl(m, 9, nudge(FP_PALM, (0.05, -0.05, 0.1)))
use_skill(m, "reaper_ult:drain")
m = Motion("reaper_ult_close", 16, "황천문 끝 — 낫을 크게 휘둘러 문을 닫음 (큰 피해)", fade=(0.03, 0.1))
strike(m, "skill_slash", 1.6, 4.0)
keyp(m, 10, P(body=[14, -50, 0], ra=arm([-0.8, -0.1, 0.5], 18, [-0.9, -0.2, -0.3]), la=arm([-0.5, -0.7, 0.2], 30), rl=[18, 0, 0], ll=[-30, 0, 0], rk=20,
              lk=34, root=[-0.16, 0.3]))
use_skill(m, "reaper_ult:close")
