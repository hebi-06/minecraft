"""0.51.0 2차 직업 스킬 동작 — 검사 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트) (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SwordmasterSkills · BloodknightSkills). 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만). 몸이 뜨는 동안의 다리는 job_motions.AIR.
단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (JobKit.motion — 용의 심장 받아치기)."""
import rune_motion_kit as K
from job_motions_a import DRAW_END, DRAW_HIT, IAI, PLANT, RISE_END, RISE_HIT, RISE_WIND, RUSH, ZANSHIN
from rune_motion_kit import (BACK_END, BACK_HIT, BACK_WIND, FP_DOWN, FP_LOW, FPL_RAISE2H, FPW_BACK, FPW_DOWN, FPW_LOW, FPW_SPREAD, FPW_UP, LEGS_FIGHT,
                             LEGS_LUNGE, SPIN_WIND_R, W_HIGH2, W_READY, W_UP_FRONT, P, arm, backhand, bent, charge_slam, fpl, fpw, keyp, nudge, off_bare,
                             spin_right, strike, use_skill)

Motion = K.Motion


def carry_spin(m, from_tick, value):
    """from_tick 뒤의 열쇠에 몸 돎 (spin) 을 이어 둠 — 빠진 열쇠는 0 으로 읽혀 몸이 거꾸로 풀리므로 (MotionPoses)"""
    t0 = m.t(from_tick)
    for t, k in m.keys.items():
        if t > t0 + 1e-9 and "spin" not in k:
            k["spin"] = value
    m.keys.setdefault(1.0, {})
    if "spin" not in m.keys[1.0] or m.keys[1.0]["spin"] != value:
        m.keys[1.0]["spin"] = value


# 비스듬히 베기 (오른쪽 위 → 왼쪽 아래 — 바로 베기를 기울임): 왼쪽 위 → 오른쪽 아래는 backhand(low=…)
DIAG_WIND = P(body=[-6, 42, 0], ra=arm([0.62, 0.68, -0.25], 58, [0.35, 0.82, -0.45]), la=arm([-0.4, -0.6, 0.35], 35), rl=[8, 0, 0], ll=[-16, 0, 0], rk=18,
              lk=22)  # 몸을 오른쪽으로 감고 칼을 오른쪽 위 뒤로
DIAG_HIT = P(body=[16, -12, 0], ra=arm([-0.12, -0.05, 1.0], 6, [-0.72, -0.48, 0.5]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=16,
             lk=24, root=[-0.06, 0.14])
DIAG_END = P(body=[14, -36, 0], ra=arm([-0.72, -0.5, 0.48], 16, [-0.55, -0.72, -0.4]), la=arm([-0.5, -0.6, 0.1], 35), rl=[8, 0, 0], ll=[-20, 0, 0], rk=14,
             lk=24, root=[-0.08, 0.1])
FPW_DIAG_WIND = dict(p=(0.4, 0.5, 0.2), blade=(0.45, 0.75, -0.5))
FPW_DIAG_HIT = dict(p=(-0.45, 0.05, -0.2), blade=(-0.72, -0.45, 0.5))


def diag(m, wind_tick, hit_tick, end_tick):
    """비스듬히 바로 베기: wind_tick 에 오른쪽 위 뒤로 감았다가 hit_tick 에 앞을 지나 왼쪽 아래로"""
    keyp(m, wind_tick, DIAG_WIND)
    keyp(m, hit_tick, DIAG_HIT, "linear")
    keyp(m, end_tick, DIAG_END, "out")
    m.arc(hit_tick, "ra", (-0.1, -0.35, 1))
    m.head.setdefault("trail", []).append([m.t(wind_tick + (hit_tick - wind_tick) * 0.3), m.t(end_tick)])
    m.set(tilt=30)
    fpw(m, wind_tick, FPW_DIAG_WIND)
    fpw(m, hit_tick, FPW_DIAG_HIT, "linear")
    fpw(m, end_tick, FPW_LOW, "out")


def diag_back(m, wind_tick, hit_tick, end_tick, low=0.55):
    """비스듬히 되돌려 베기 (왼쪽 위 → 오른쪽 아래 — 마검사 비전 거검 slash2 꼴)"""
    backhand(m, wind_tick, hit_tick, end_tick, wind=P(BACK_WIND, body=[-4, -50, 0], ra=arm([-0.55, 0.65, 0.5], 60, [-0.55, 0.75, -0.35])),
             hit=P(BACK_HIT, body=[18, 20, 0]), end=P(BACK_END, ra=arm([0.8, -0.45, 0.35], 14, [0.6, -0.65, -0.45]), body=[16, 40, 0]), low=low)


# 한 손으로 칼을 머리 위로 (돌며 내려 벨 준비)
OVER1 = P(body=[-12, -8, 0], head=[-14, 0], ra=arm([0.18, 0.95, -0.22], 38, [0.05, 0.42, -0.9]), la=arm([-0.75, -0.25, 0.3], 30), rl=[-30, 0, 6], rk=60,
          ll=[-12, 0, -6], lk=44, root=[0.0, 0])
# 칼을 거꾸로 쥐고 두 손으로 머리 위로 (칼끝이 아래 — 땅에 꽂을 준비)
REV_RAISE = P(body=[-12, 0, 0], head=[-18, 0], ra=arm([-0.1, 0.97, 0.18], 14, [0, -0.96, 0.25]), la=bent([0.1, 0.97, 0.18], 16, (-0.3, 1, 0)), rl=[0, 0, 10],
              ll=[0, 0, -10], rk=14, lk=14, root=[-0.04, 0])
FPW_REV_UP = dict(p=(-0.2, 0.7, 0.1), blade=(0.0, -0.9, -0.3))


# ------------------------------------------------------------ 시구르드(소드마스터) (칼 — 금빛 · 은빛)

# 금빛 연격 (효과: 0 (-12 — 왼쪽 → 오른쪽) · 3 (168 — 오른쪽 → 왼쪽) · 10 · 13 같은 둘 · 16 ~ 23 작게 뜀 (JobMove.hop) — 20 에 내려 벰 (95) · 30 (35 — 왼쪽 위 →
# 오른쪽 아래) · 33 (145 — 오른쪽 위 → 왼쪽 아래) X자 (투지 3 이면 30 에 검기 두 줄))
m = Motion("swordmaster_flurry", 38, "금빛 연격 — 내딛으며 되돌려 베고 바로 베기를 두 차례 → 작게 뛰어 오른쪽으로 한 바퀴 돌며 머리 위에서 내려 벰 → 비스듬히 X자로 두 번",
           fade=(0.03, 0.1))
backhand(m, 0.1, 1.0, 2.1)
strike(m, "slash_h", 2.2, 3.4, end_tick=5.0)
keyp(m, 6.6, P(W_READY, body=[10, 2, 0], **LEGS_LUNGE))
backhand(m, 8.1, 10.0, 11.2)
strike(m, "slash_h", 11.4, 13.2, end_tick=14.6)
keyp(m, 15.6, P(SPIN_WIND_R, root=[-0.22, 0]), "out3")
keyp(m, 17.3, P(OVER1, spin=-180), "linear")
keyp(m, 18.8, P(OVER1, spin=-330), "out")
strike(m, "slash_v", 19.0, 20.3, end_tick=22.4)
keyp(m, 25.0, P(W_READY, body=[10, 0, 0], **LEGS_LUNGE))
diag_back(m, 28.3, 30.0, 31.2)
diag(m, 31.4, 33.0, 34.6)
keyp(m, 36.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 6.6, FPW_LOW)
fpw(m, 15.6, FPW_BACK, "out3")
fpw(m, 17.3, FPW_UP)
fpw(m, 25.0, FPW_LOW)
fpw(m, 36.6, FPW_LOW)
carry_spin(m, 18.8, -360)
use_skill(m, "swordmaster_flurry")

# 그라니의 질주 (효과: 0 출발 → 1 · 2 · 3 앞 오른쪽으로 한 마디씩 → 10 출발 → 11 · 12 · 13 앞 왼쪽으로, 지나간 적은 2틱 뒤 베인 줄 · 17 치명 준비)
m = Motion("swordmaster_grani", 24, "그라니의 질주 — 낮게 웅크려 칼자루를 쥐고 앞 오른쪽으로 내달리며 뽑아 베고, 꺾어 앞 왼쪽으로 내달리며 되베어 낸 뒤 칼을 털어냄",
           fade=(0.03, 0.1))
keyp(m, 0.3, IAI, "out3")
backhand(m, 0.9, 2.3, 3.6, wind=IAI, hit=DRAW_HIT, end=DRAW_END)
keyp(m, 7.6, P(DRAW_END, body=[16, 46, 0], root=[-0.22, 0.2]))
strike(m, "slash_h", 10.0, 12.2, end_tick=14.2)
keyp(m, 16.4, P(ZANSHIN, body=[10, -36, 0], ra=arm([-0.6, -0.45, 0.55], 20, [-0.5, -0.6, -0.5])))
keyp(m, 18.4, P(ZANSHIN, body=[8, -26, 0], ra=arm([-0.45, -0.7, 0.45], 30, [-0.35, -0.85, 0.3])), "in")  # 칼을 털어냄
keyp(m, 22, P(W_READY, body=[6, 8, 0]))
fpw(m, 7.6, dict(p=(0.4, -0.25, 0.2), blade=(0.7, -0.3, -0.6)))
fpw(m, 16.4, dict(p=(-0.5, -0.25, 0.1), blade=(-0.6, -0.4, -0.6)))
fpw(m, 18.4, dict(p=(-0.35, -0.45, 0.1), blade=(-0.45, -0.8, 0.3)), "in")
fpw(m, 22, FPW_LOW)
use_skill(m, "swordmaster_grani")

# 만월 강하 (효과: 0 ~ 4 2칸 솟아오름 (JobMove) → 4 · 14 · 24 공중에서 둥근 베기 (오른쪽으로) → 30 ~ 34 칼을 꽂으며 내려앉음 — 34 땅 터짐)
m = Motion("swordmaster_moon", 44, "만월 강하 — 웅크렸다 솟아올라 칼을 뻗고 공중에서 오른쪽으로 세 바퀴 돌며 벤 뒤, 칼을 두 손으로 머리 위로 들어 내려앉으며 땅에 꽂음",
           fade=(0.03, 0.1))
keyp(m, 0.5, P(SPIN_WIND_R, root=[-0.3, 0], rk=60, lk=60), "out3")
spin_right(m, [4.0, 14.0, 24.0], 27.0, wind_tick=2.2)
keyp(m, 29.4, P(W_HIGH2, body=[-16, 4, 0], head=[-12, 0]), "out3")
strike(m, "slam", 31.6, 34.0, end_tick=36.0)
keyp(m, 38.5, PLANT)
keyp(m, 41.5, P(PLANT, body=[30, 0, 0], root=[-0.22, 0.12]))
fpw(m, 29.4, FPW_UP, "out3")
fpw(m, 38.5, FPW_DOWN)
fpw(m, 42.5, FPW_LOW)
off_bare(m)
fpl(m, 29.4, FPL_RAISE2H, "out3")
fpl(m, 34.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 41.5, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 43.5, FP_LOW)
carry_spin(m, 27.0, -1080)
use_skill(m, "swordmaster_moon")

# 용의 심장 (태세 — 효과: 3 에 칼을 땅에 꽂음 (땅 터짐 · 솟는 기운) · 4 · 9 · 14 고리 · 눈앞 고리 · 용의 울음)
m = Motion("swordmaster_heart", 26, "용의 심장 — 칼을 두 손으로 들어 앞 땅에 꽂고 자루를 누른 채 하늘을 보며 기합을 지른 뒤 칼을 뽑아 듦", fade=(0.04, 0.12))
off_bare(m)
keyp(m, 1.0, P(W_HIGH2, head=[-10, 0]), "out3")
strike(m, "slam", 1.2, 3.0, end_tick=4.4)
keyp(m, 6.0, PLANT)
keyp(m, 9.0, P(PLANT, body=[8, 0, 0], head=[-34, 0], root=[-0.16, 0.1]), "out3")  # 하늘을 보며 기합
keyp(m, 15.0, P(PLANT, body=[10, 0, 0], head=[-26, 0], root=[-0.18, 0.1]))
keyp(m, 19.0, P(W_UP_FRONT, body=[0, 0, 0]), "out")  # 칼을 뽑아 얼굴 앞에 세움
keyp(m, 23.0, P(W_READY, body=[6, 4, 0]))
fpw(m, 1.0, FPW_UP, "out3")
fpw(m, 6.0, FPW_DOWN)
fpw(m, 15.0, FPW_DOWN)
fpw(m, 19.0, FPW_UP, "out")
fpw(m, 23.0, FPW_LOW)
fpl(m, 1.0, FPL_RAISE2H, "out3")
fpl(m, 3.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 15.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 19.0, FP_LOW)
use_skill(m, "swordmaster_heart")
m = Motion("swordmaster_heart_counter", 10, "용의 심장 받아치기 — 맞은 쪽으로 몸을 틀며 짧게 되돌려 벰", fade=(0.02, 0.1))
backhand(m, 0.0, 1.0, 2.6, wind=P(BACK_WIND, body=[6, -52, 0]), hit=P(BACK_HIT, body=[14, 22, 0], **LEGS_LUNGE),
         end=P(BACK_END, body=[12, 44, 0], **LEGS_LUNGE))
keyp(m, 7.5, P(W_READY, body=[8, 10, 0]))
use_skill(m, "swordmaster_heart:counter")

# 신들의 황혼 (효과: 0 ~ 6 앞의 적에게 돌진 → 6 올려 베어 띄움 (275) → 8 ~ 14 3칸 뛰어오름 (JobMove) → 16 (172 — 오른쪽 → 왼쪽) · 26 (-8 — 왼쪽 → 오른쪽) ·
# 36 한 바퀴 · 46 내려 벰 (95) → 54 두 손으로 머리 위로 · 55 ~ 60 내려앉으며 60 에 내리꽂음 (세 갈래 균열))
m = Motion("swordmaster_ult", 68, "신들의 황혼 — 몸을 숙여 내달려 아래에서 쳐올려 띄우고, 따라 뛰어올라 공중에서 바로 베기 · 되돌려 베기 · 한 바퀴 · 내려 베기 뒤, 두 손으로 칼을 "
           "머리 위로 들고 내려앉으며 땅에 내리꽂음", fade=(0.03, 0.1))
keyp(m, 0.6, RUSH, "out3")
keyp(m, 3.6, P(RUSH, body=[32, -16, 0]))
keyp(m, 4.6, RISE_WIND, "out3")
keyp(m, 6.2, RISE_HIT, "linear")
keyp(m, 8.4, RISE_END, "out")
m.arc(6.2, "ra", (0.15, -0.1, 1))
m.head.setdefault("trail", []).append([m.t(5.0), m.t(8.2)])
keyp(m, 12.4, P(W_READY, body=[2, -10, 0]))
strike(m, "slash_h", 14.4, 16.2, end_tick=18.6)
keyp(m, 21.5, P(W_READY, body=[4, -4, 0]))
backhand(m, 24.2, 26.0, 27.8)
keyp(m, 31.0, P(W_READY, body=[4, 6, 0]))
spin_right(m, [36.0], 39.4, wind_tick=33.8)
keyp(m, 42.2, P(OVER1, spin=-360), "out3")
strike(m, "slash_v", 44.6, 46.2, end_tick=48.6)
charge_slam(m, 60.0, grip_tick=50.4, raise_tick=52.6)
keyp(m, 63.5, PLANT)
keyp(m, 66.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 0.6, FPW_BACK, "out3")
fpw(m, 3.6, FPW_BACK)
fpw(m, 4.6, dict(p=(0.35, -0.5, 0.15), blade=(0.35, -0.55, -0.75)), "out3")
fpw(m, 6.2, dict(p=(-0.25, 0.55, -0.15), blade=(0.0, 1, 0.15)), "linear")
fpw(m, 8.4, FPW_UP)
fpw(m, 12.4, FPW_LOW)
fpw(m, 21.5, FPW_LOW)
fpw(m, 31.0, FPW_LOW)
fpw(m, 42.2, FPW_UP, "out3")
fpw(m, 63.5, FPW_DOWN)
fpw(m, 66.5, FPW_LOW)
carry_spin(m, 39.4, -360)
use_skill(m, "swordmaster_ult")

# ------------------------------------------------------------ 다인슬레이프(블러드 나이트) (칼 — 검붉은 빛)

# 핏빛 연격 (효과: 0 (-28 — 왼쪽 아래 → 오른쪽 위로 찌르듯) · 10 (160 — 오른쪽 → 왼쪽) · 20 (95 — 머리 위에서 내려 벰 · 앞 땅에 핏빛 터짐))
m = Motion("bloodknight_flurry", 28, "핏빛 연격 — 크게 내딛으며 왼쪽 아래에서 오른쪽 위로 찌르듯 베고 되돌려 바로 벤 뒤 머리 위에서 내려 벰", fade=(0.03, 0.1))
backhand(m, 0.1, 1.0, 2.6, wind=P(BACK_WIND, body=[14, -40, 0], ra=arm([-0.6, -0.35, 0.7], 60, [-0.75, -0.3, -0.55]), root=[-0.2, 0.1]),
         hit=P(BACK_HIT, body=[18, 16, 0], **LEGS_LUNGE), end=P(BACK_END, body=[10, 40, 0], ra=arm([0.8, 0.35, 0.45], 18, [0.75, 0.45, -0.45]), **LEGS_LUNGE),
         low=-0.4)
keyp(m, 6.4, P(W_READY, body=[12, 14, 0], **LEGS_LUNGE))
strike(m, "skill_slash", 8.4, 10.2, end_tick=12.6)
keyp(m, 15.6, P(OVER1, body=[-14, -4, 0], **LEGS_FIGHT), "out3")
strike(m, "slash_v", 18.4, 20.2, end_tick=22.4)
keyp(m, 25.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 6.4, FPW_LOW)
fpw(m, 15.6, FPW_UP, "out3")
fpw(m, 25.6, FPW_LOW)
use_skill(m, "bloodknight_flurry")

# 피의 질주 (효과: 0 · 1 · 2 틱에 한 마디씩 6칸 → 지나간 적 2틱 뒤 베인 줄 · 4 길 전체 일섬)
m = Motion("bloodknight_rush", 18, "피의 질주 — 칼을 뒤로 끌며 몸을 숙여 내달리다 지나가며 되돌려 베고, 멈춰 서서 칼의 피를 털어냄", fade=(0.03, 0.1))
keyp(m, 0.4, RUSH, "out3")
backhand(m, 1.2, 2.6, 4.2, wind=P(RUSH, body=[30, -30, 0]), hit=P(DRAW_HIT, body=[16, 30, 0]), end=DRAW_END)
keyp(m, 8.4, P(ZANSHIN, body=[10, 44, 0]))
keyp(m, 10.6, P(ZANSHIN, ra=arm([0.55, -0.7, 0.4], 30, [0.4, -0.85, 0.3]), body=[8, 30, 0]), "in")  # 칼의 피를 털어냄
keyp(m, 15, P(W_READY, body=[6, 8, 0]))
fpw(m, 0.4, FPW_BACK, "out3")
fpw(m, 8.4, dict(p=(0.4, -0.25, 0.2), blade=(0.7, -0.3, -0.6)))
fpw(m, 10.6, dict(p=(0.3, -0.45, 0.1), blade=(0.5, -0.8, 0.3)), "in")
fpw(m, 15, FPW_LOW)
use_skill(m, "bloodknight_rush")

# 상처 터뜨리기 (효과: 0 둘레 한 바퀴 (오른쪽으로) → 5 상처가 끓음 → 10 상처를 터뜨림 (주먹을 움켜쥠))
GRASP_OPEN = P(body=[10, -20, 0], head=[4, 0], ra=arm([0.6, -0.5, 0.45], 20, [0.5, -0.6, 0.6]), la=arm([-0.12, 0.12, 1], 0, [0, 0.3, 1]), **LEGS_FIGHT)
GRASP_CLOSE = P(body=[16, -8, 0], head=[8, 0], ra=arm([0.6, -0.5, 0.45], 20, [0.5, -0.6, 0.6]), la=bent([-0.25, -0.3, 0.6], 120, (0.3, 1, 0.2)),
                rl=[20, 0, 4], ll=[-26, 0, -4], rk=22, lk=30, root=[-0.12, 0])
m = Motion("bloodknight_burst", 18, "상처 터뜨리기 — 칼을 뻗고 오른쪽으로 크게 한 바퀴 돈 뒤, 왼손을 적들 쪽으로 펼쳤다가 움켜쥐어 상처를 터뜨림", fade=(0.03, 0.1))
off_bare(m)
spin_right(m, [1.2], 4.6, wind_tick=0.1)
keyp(m, 7.6, P(GRASP_OPEN, spin=-360), "out3")
keyp(m, 10.0, P(GRASP_CLOSE, spin=-360), "in")
keyp(m, 13.4, P(GRASP_CLOSE, body=[14, -6, 0], spin=-360))
keyp(m, 16.4, P(W_READY, body=[6, 4, 0], spin=-360))
fpl(m, 4.6, FP_LOW)
fpl(m, 7.6, ((0.0, 0.05, -0.4), (18, 55, 0)), "out3")
fpl(m, 10.0, ((0.0, -0.08, -0.22), (10, 30, 0)), "in")
fpl(m, 14.0, FP_LOW)
carry_spin(m, 4.6, -360)
use_skill(m, "bloodknight_burst")

# 회그니의 맹세 (태세 — 효과: 2 · 4 · 6 · 8 칼날에서 떨어지는 핏방울 · 3 · 8 · 13 발밑 고리 · 몸에서 솟는 핏빛 기운)
OATH_UP = P(W_UP_FRONT, body=[-4, 0, 0], head=[6, 0], la=bent([0.2, 0.1, 0.7], 100, (-0.6, 1, 0.2)))
m = Motion("bloodknight_oath", 24, "회그니의 맹세 — 칼을 얼굴 앞에 곧게 세우고 왼손으로 칼날을 밑동에서 끝까지 쓸어 피를 묻힌 뒤 고개를 숙였다가 칼을 옆으로 내림",
           fade=(0.04, 0.12))
off_bare(m)
keyp(m, 2.4, OATH_UP, "out3")
keyp(m, 5.4, P(OATH_UP, la=bent([0.15, 0.35, 0.8], 70, (-0.6, 1, 0.2))))
keyp(m, 8.4, P(OATH_UP, la=arm([0.1, 0.62, 0.78], 20, [0.0, 0.9, 0.4])), "in")
keyp(m, 12.4, P(OATH_UP, head=[18, 0], body=[4, 0, 0], la=arm([-0.35, -0.75, 0.3], 25)))  # 고개를 숙임
keyp(m, 17.0, P(body=[6, 12, 0], ra=arm([0.7, -0.45, 0.45], 10, [0.55, -0.55, 0.6]), la=arm([-0.25, -0.9, 0.1], 20), **LEGS_FIGHT), "out")
keyp(m, 21.0, P(W_READY, body=[6, 4, 0]))
fpw(m, 2.4, dict(p=(-0.18, 0.38, 0.05), blade=(0.04, 1, 0.08)), "out3")
fpw(m, 12.4, dict(p=(-0.18, 0.34, 0.05), blade=(0.04, 1, 0.08)))
fpw(m, 17.0, FPW_SPREAD, "out")
fpw(m, 21.0, FPW_LOW)
fpl(m, 2.4, ((-0.18, -0.05, -0.1), (15, 60, 0)), "out3")
fpl(m, 8.4, ((-0.22, 0.3, -0.15), (60, 50, 0)), "in")
fpl(m, 12.4, FP_LOW)
use_skill(m, "bloodknight_oath")

# 끝나지 않는 전투 (효과: 0 ~ 6 앞의 적에게 돌진 → X자 둘씩 넷: 6 (35) · 9 (145) / 16 (-35) · 19 (215) / 26 (55) · 29 (125) / 36 (20) · 39 (160) →
# 44 칼을 거꾸로 쥐고 들어 올림 · 44 ~ 52 작게 뜀 (JobMove.hop) → 52 땅에 꽂음 (핏빛 기둥))
m = Motion("bloodknight_ult", 66, "끝나지 않는 전투 — 몸을 숙여 내달려 X자로 두 번씩 네 차례 몰아 벤 뒤, 칼을 거꾸로 쥐고 두 손으로 들어 올려 작게 뛰어 땅에 꽂음",
           fade=(0.03, 0.1))
keyp(m, 0.6, RUSH, "out3")
keyp(m, 3.8, P(RUSH, body=[32, -16, 0]))
diag_back(m, 4.6, 6.0, 7.2)
diag(m, 7.4, 9.0, 10.6)
keyp(m, 13.0, P(W_READY, body=[10, -4, 0], **LEGS_LUNGE))
backhand(m, 14.6, 16.0, 17.2, low=-0.4)
strike(m, "skill_slash", 17.4, 19.0, end_tick=20.8)
keyp(m, 23.0, P(W_READY, body=[10, 4, 0], **LEGS_LUNGE))
diag_back(m, 24.6, 26.0, 27.2, low=0.8)
strike(m, "slash_v", 27.4, 29.0, end_tick=30.8)
keyp(m, 33.0, P(W_READY, body=[10, -2, 0], **LEGS_LUNGE))
backhand(m, 34.6, 36.0, 37.2, low=0.25)
strike(m, "slash_h", 37.4, 39.0, end_tick=40.8)
keyp(m, 42.4, P(W_READY, body=[8, 6, 0]))
off_bare(m)
keyp(m, 45.6, REV_RAISE, "out3")
keyp(m, 49.6, P(REV_RAISE, body=[-16, 0, 0], head=[-22, 0], rk=24, lk=24))
keyp(m, 52.0, P(PLANT, body=[36, 0, 0]), "in")
keyp(m, 56.0, PLANT)
keyp(m, 60.0, P(PLANT, body=[30, 0, 0], root=[-0.22, 0.12]))
keyp(m, 64.0, P(W_READY, body=[8, 6, 0]))
fpw(m, 0.6, FPW_BACK, "out3")
fpw(m, 3.8, FPW_BACK)
fpw(m, 13.0, FPW_LOW)
fpw(m, 23.0, FPW_LOW)
fpw(m, 33.0, FPW_LOW)
fpw(m, 42.4, FPW_LOW)
fpw(m, 45.6, FPW_REV_UP, "out3")
fpw(m, 49.6, FPW_REV_UP)
fpw(m, 52.0, FPW_DOWN, "in")
fpw(m, 60.0, FPW_DOWN)
fpw(m, 64.0, FPW_LOW)
fpl(m, 42.4, FP_LOW)
fpl(m, 45.6, FPL_RAISE2H, "out3")
fpl(m, 52.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 60.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 64.0, FP_LOW)
use_skill(m, "bloodknight_ult")
