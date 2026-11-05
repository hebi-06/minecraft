"""0.51.0 2차 직업 스킬 동작 — 검사 각성 소드마스터 · 반전 블러드 나이트 — 재설계안 2판 (사용자 "이펙트가 전직업보다 안좋았음
타격감이 없었음 이펙트가 작았음 동작이 뻣뻣했음"). 틱 0 = 스킬 (또는 단계 열쇠 — 서버가 그 순간에 다시 보냄: JobKit.motion · JobRecast.motion) 이 나간 틱,
효과의 later · every 와 같은 틱 (SwordmasterSkills · BloodknightSkills).

뻣뻣하지 않게:
- 치기 전에 반대쪽으로 크게 감고 (예비 동작 — 몸을 비틀고 무릎을 굽혀 낮아짐) 치는 틱에 뿌리가 앞으로 나가며 (내디딤) 몸이 반대쪽까지 돌아감
- 친 뒤 지나쳐 멈췄다가 조금 되돌아옴 (따라 휘두름 → 가라앉음) · 왼팔은 반대로 흔들려 몸의 무게를 받음
- 멈춘 자세도 숨 쉬듯 조금씩 움직임 (같은 자세를 오래 두지 않음)
- 이어지는 단계 (재사용 · 가로지르기 · X 베기) 는 앞 단계의 끝 자세에서 바로 시작 (들어오는 무게 0 — 쉬는 자세로 꺼졌다 오지 않게)
맞는 틱의 멈칫 (JobImpact — 클라이언트는 멈춘 만큼을 뒤에서 1.5배로 따라잡음 · ClientMotions.CATCH) 이 있는 동작은 그 뒤 열쇠를 따라잡는 시각에 맞춤.
몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만). 몸이 뜨는 단계는 다리를 이 파일에서 직접 (softLegs 끔).

다른 직업 동작이 쓰는 조각 (carry_spin · diag · diag_back · OVER1 · REV_RAISE · FPW_REV_UP · GRASP_*) 은 그대로 둠 (job_motions_e · f · g)."""
import copy

import rune_motion_kit as K
from job_motions_a import DRAW_END, DRAW_HIT, GATHER_W, IAI, PLANT, RUSH, ZANSHIN
from rune_motion_kit import (A_GUARD, A_JAB, A_PALM, A_PULL, A_SPREAD_DOWN, BACK_END, BACK_HIT, BACK_WIND, FP_CHAMBER, FP_DOWN, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW,
                             FP_PALM, FP_PUSH_OUT, FPL_RAISE2H, FPW_BACK, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, L, RAISE2H, W_HIGH2, W_READY,
                             P, arm, backhand, bent, fpl, fpw, keyp, lerpP, nudge, off_bare, spin_right, strike, use_skill)

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
# 왼손을 펼쳤다가 움켜쥠 (상처 터뜨리기 꼴 — 헥스 블레이드 · 네크로맨서가 씀)
GRASP_OPEN = P(body=[10, -20, 0], head=[4, 0], ra=arm([0.6, -0.5, 0.45], 20, [0.5, -0.6, 0.6]), la=arm([-0.12, 0.12, 1], 0, [0, 0.3, 1]), rl=[14, 0, 4],
               ll=[-16, 0, -4], rk=16, lk=12)
GRASP_CLOSE = P(body=[16, -8, 0], head=[8, 0], ra=arm([0.6, -0.5, 0.45], 20, [0.5, -0.6, 0.6]), la=bent([-0.25, -0.3, 0.6], 120, (0.3, 1, 0.2)),
                rl=[20, 0, 4], ll=[-26, 0, -4], rk=22, lk=30, root=[-0.12, 0])


# ------------------------------------------------------------ 0.51.0 재설계안 2판 — 더 크게 · 덜 뻣뻣하게 쓰는 도구

LEG_KEYS = ("body", "rl", "ll", "rk", "lk", "root", "head")


def deepen(m, a_tick, b_tick, **over):
    """a_tick ~ b_tick 의 열쇠 (다듬어 둔 베기를 옮겨 쓴 것 등) 의 몸 · 다리 · 뿌리 · 머리를 덮음 (팔 · 1인칭은 그대로)"""
    ta, tb = m.t(a_tick) - 1e-9, m.t(b_tick) + 1e-9
    for t, k in m.keys.items():
        if ta <= t <= tb:
            for f, v in over.items():
                k[f] = copy.deepcopy(v)


def strike_with(m, name, wind_tick, hit_tick, end_tick, a, b):
    """다듬어 둔 베기 name 을 옮겨 쓰고 (팔 · 1인칭 · 궤적 그대로) 새 열쇠의 몸 · 다리 · 뿌리 · 머리를 a (준비) → b (끝) 사이로 덮음 — 더 크게 감고 내디딤"""
    before = set(m.keys)
    strike(m, name, wind_tick, hit_tick, end_tick=end_tick)
    t0, t1 = m.t(wind_tick), m.t(end_tick)
    for t, k in m.keys.items():
        if t in before or not (t0 - 1e-9 <= t <= t1 + 1e-9):
            continue
        s = 0.0 if t1 <= t0 else (t - t0) / (t1 - t0)
        mix = lerpP(a, b, s)
        for f in LEG_KEYS:
            if f in mix:
                k[f] = mix[f]


def impacts(m, *ticks):
    """맞는 때 (틱 — 서버가 피해를 넣는 틱): 클라이언트 멈칫이 이 자세에 맞춰 멈춤 (ClientMotions.snap — 멈칫이 1틱쯤 일찍 와도 베기 직전에 멈추지 않게)"""
    m.set(impacts=[m.t(x) for x in ticks])


def first(m, pose, fp=None):
    """틱 0 의 자세 (앞 단계의 끝 자세에서 바로 이어짐 — 들어오는 무게 0 과 함께)"""
    keyp(m, 0.0, pose)
    if fp is not None:
        fpw(m, 0.0, fp)


# 낮고 넓게 내디딘 다리 (왼발 앞 — 크게 벨 때)
LUNGE_DEEP = dict(rl=[32, 0, 4], ll=[-48, 0, -2], rk=22, lk=54)
# 무릎을 깊이 굽혀 버팀 (멈춘 자세 · 미끄러져 멈춤)
BRACE = dict(rl=[30, 0, 8], ll=[-26, 0, -8], rk=60, lk=54)
# 1인칭: 칼을 왼 허리 아래로 (칼집 — 화면 밖 왼쪽 아래)
FPW_SHEATHED = dict(p=(-0.75, -0.62, 0.35), blade=(-0.4, -0.3, -0.85))
# 1인칭: 베어 낸 칼을 오른쪽 아래에 (잔심)
FPW_ZAN = dict(p=(0.4, -0.25, 0.2), blade=(0.7, -0.3, -0.6))
FPW_FLICK = dict(p=(0.3, -0.45, 0.1), blade=(0.5, -0.8, 0.3))
FP_BACK0 = dict(p=K.fp_mirror_x((0.548, 0.458, 0.225)), blade=K.fp_flip((0.76, 0.375, -0.532)))  # 되돌려 베기 준비 (화면 밖 왼쪽 — backhand 의 첫 1인칭)

# ------------------------------------------------------------ 소드마스터 (칼 — 금빛 · 불빛)

# 칼집 섬광 (효과: 0 칼집 쪽 금빛 · 6 뽑음 — 피해 한 번 (멈칫 2틱) · 칼자국 판 6 ~ 9 (한 틱에 둘) · 10 마지막 큰 가로 베기 (왼쪽 → 오른쪽 — 투지 3 이면 12)).
# 멈칫 (6 ~ 8) 뒤 1.5배로 따라잡아 동작 9 ≈ 효과 10 → 뽑아 베기 6 · 바로 베기 7.7 · 마지막 되돌려 베기 9.0
SHEATHE = P(body=[10, -28, 0], head=[4, 0], ra=arm([-0.42, -0.64, 0.5], 72, [-0.55, -0.14, -0.82]), la=arm([-0.36, -0.84, 0.3], 36), rl=[18, 0, 4],
            ll=[-24, 0, -4], rk=28, lk=36, root=[-0.14, 0.0])  # 칼을 왼 허리 칼집에 넣으며 몸을 왼쪽으로 감음
IAI_LOW = P(IAI, body=[24, -46, 0], head=[2, 0], rk=52, lk=70, root=[-0.36, 0.02])  # 더 낮게 웅크려 칼자루를 쥐고 숨을 고름
IAI_COIL = P(IAI_LOW, body=[28, -60, 0], rk=60, lk=78, root=[-0.42, -0.04])  # 뽑기 직전 (가장 크게 감음)
DRAW_HIT2 = P(DRAW_HIT, body=[14, 30, 0], root=[-0.26, 0.55], **LUNGE_DEEP)  # 크게 내디디며 뽑아 벰
DRAW_END2 = P(DRAW_END, body=[12, 56, 0], root=[-0.28, 0.52], **LUNGE_DEEP)
BIG_WIND = P(BACK_WIND, body=[12, -78, 0], ra=arm([-0.62, 0.3, 0.55], 74, [-0.85, 0.16, -0.5]), la=arm([-0.6, -0.4, -0.3], 30), rl=[26, 0, 4],
             ll=[-40, 0, -2], rk=30, lk=50, root=[-0.26, 0.45])
BIG_HIT = P(BACK_HIT, body=[16, 28, 0], la=arm([-0.75, -0.3, -0.45], 25), root=[-0.3, 0.6], **LUNGE_DEEP)
BIG_END = P(BACK_END, body=[14, 64, 0], ra=arm([0.9, -0.06, 0.1], 14, [0.62, -0.26, -0.74]), la=arm([-0.8, -0.35, -0.3], 25), root=[-0.32, 0.56],
            **LUNGE_DEEP)
ZAN2 = P(ZANSHIN, body=[12, 54, 0], root=[-0.3, 0.5], **LUNGE_DEEP)  # 베어 낸 자세로 멈춤
FLICK = P(ZAN2, ra=arm([0.55, -0.72, 0.4], 26, [0.4, -0.88, 0.25]), body=[10, 36, 0], root=[-0.24, 0.42])  # 칼의 피를 털어냄
NOTO_A = P(body=[6, -16, 0], head=[6, 0], ra=arm([-0.3, -0.6, 0.72], 62, [-0.25, -0.32, 0.91]), la=arm([-0.36, -0.86, 0.3], 30), rl=[10, 0, 4],
           ll=[-12, 0, -4], rk=14, lk=16, root=[-0.08, 0.16])  # 칼끝을 왼 허리 칼집 어귀로
NOTO_B = P(NOTO_A, body=[4, -20, 0], head=[2, 0], ra=arm([-0.45, -0.74, 0.36], 70, [-0.55, -0.16, -0.82]), root=[-0.06, 0.08])  # 칼을 칼집에 밀어 넣음 (딸깍)
m = Motion("swordmaster_draw", 26, "칼집 섬광 — 칼을 왼 허리 칼집에 넣고 낮게 웅크려 숨을 고르다가, 크게 내디디며 뽑아 베고 되베고 몸을 끝까지 돌려 크게 가로 벤 뒤, "
           "베어 낸 자세로 멈췄다가 칼의 피를 털고 다시 칼집에 넣음", fade=(0.03, 0.08))
keyp(m, 1.4, SHEATHE, "out3")
keyp(m, 3.6, IAI_LOW)
backhand(m, 5.0, 6.0, 6.8, wind=IAI_COIL, hit=DRAW_HIT2, end=DRAW_END2)
keyp(m, 5.0, IAI_COIL, "in")  # 뽑기 직전으로 빨라지며 감음 (backhand 의 준비 열쇠를 덮음)
strike_with(m, "slash_h", 7.0, 7.7, 8.3, a=P(body=[12, 54, 0], root=[-0.28, 0.5], **LUNGE_DEEP), b=P(body=[14, -40, 0], root=[-0.28, 0.5], **LUNGE_DEEP))
backhand(m, 8.35, 9.0, 10.4, wind=BIG_WIND, hit=BIG_HIT, end=BIG_END)
keyp(m, 12.2, ZAN2)
keyp(m, 14.4, P(ZAN2, body=[10, 50, 0], root=[-0.26, 0.48], rk=24, lk=50))
keyp(m, 15.6, FLICK, "in")
keyp(m, 16.8, P(FLICK, body=[9, 32, 0]), "out")
keyp(m, 19.0, NOTO_A)
keyp(m, 21.0, NOTO_B, "in")
keyp(m, 23.8, P(W_READY, body=[6, 4, 0]))
fpw(m, 1.4, FPW_SHEATHED, "out3")
fpw(m, 3.6, FPW_SHEATHED)
fpw(m, 12.2, FPW_ZAN)
fpw(m, 14.4, FPW_ZAN, dp=(0.02, -0.03, 0))
fpw(m, 15.6, FPW_FLICK, "in")
fpw(m, 19.0, FPW_SHEATHED)
fpw(m, 21.0, FPW_SHEATHED, "in")
fpw(m, 23.8, FPW_LOW)
impacts(m, 6.0, 7.7, 9.0)
use_skill(m, "swordmaster_draw")

# 받아넘기기 (효과: 0 ~ 10 칼을 세워 막음 (받는 피해 없음) → 아무것도 막지 않으면 10 에 한 걸음 내디뎌 가로 베기 (왼쪽 → 오른쪽 — 맞히면 멈칫 2틱))
GUARD_V = P(body=[4, -20, 0], head=[2, 0], ra=arm([0.02, 0.3, 0.95], 96, [-0.12, 0.98, 0.12]), la=bent([-0.05, 0.25, 0.95], 100, (0.7, 1, 0)), rl=[18, 0, 6],
            ll=[-12, 0, -6], rk=36, lk=26, root=[-0.18, -0.1])  # 칼을 얼굴 앞에 세우고 왼손으로 칼등을 받침 (무게는 뒷발)
FPW_GUARD = dict(p=(-0.45, 0.3, -0.05), blade=(0.08, 1, 0.1))
STEP_WIND = P(BACK_WIND, body=[10, -68, 0], rl=[22, 0, 4], ll=[-26, 0, -4], rk=40, lk=42, root=[-0.24, -0.02])
STEP_HIT = P(BACK_HIT, body=[16, 24, 0], root=[-0.24, 0.48], **LUNGE_DEEP)
STEP_END = P(BACK_END, body=[14, 54, 0], root=[-0.26, 0.42], **LUNGE_DEEP)
m = Motion("swordmaster_parry", 16, "받아넘기기 — 칼을 얼굴 앞에 세우고 왼손으로 칼등을 받쳐 막는 자세로 버티다가, 아무것도 오지 않으면 몸을 감았다 한 걸음 내디디며 가로 벰",
           fade=(0.02, 0.1))
keyp(m, 1.2, GUARD_V, "out3")
keyp(m, 4.6, P(GUARD_V, body=[6, -22, 0], rk=40, lk=30, root=[-0.21, -0.1]))
keyp(m, 7.4, P(GUARD_V, body=[3, -18, 0], rk=34, lk=24, root=[-0.17, -0.1]))
backhand(m, 8.8, 10.0, 11.4, wind=STEP_WIND, hit=STEP_HIT, end=STEP_END)
keyp(m, 13.6, P(W_READY, body=[8, 8, 0]))
fpw(m, 1.2, FPW_GUARD, "out3")
fpw(m, 7.4, FPW_GUARD, dp=(0.0, -0.03, 0.0))
fpw(m, 13.6, FPW_LOW)
impacts(m, 10.0)
use_skill(m, "swordmaster_parry")

# 받아치기 (단계 :riposte — 막은 순간 서버가 보냄: 0 쨍 (4틱 멈칫 — 막은 자세 그대로) → 1.5배로 따라잡으며 효과 +6 · +8 · +10 (되돌려 · 바로 · 내려) =
# 동작 1.5 · 4.5 · 7.5)
DEFLECT = P(BACK_WIND, body=[2, -46, 0], head=[0, 0], ra=arm([-0.55, 0.5, 0.65], 66, [-0.7, 0.45, -0.55]), la=arm([-0.5, -0.5, 0.3], 30), rl=[22, 0, 6],
            ll=[-16, 0, -6], rk=40, lk=30, root=[-0.2, -0.14])  # 받아낸 칼을 왼쪽 위로 쳐냄 (밀려 웅크림 — 되돌려 벨 준비)
m = Motion("swordmaster_parry_riposte", 16, "받아치기 — 막아낸 칼을 왼쪽 위로 쳐내고 되돌려 베고, 바로 베고, 머리 위로 들어 내려 벤 뒤 낮게 버팀", fade=(0.0, 0.1))
first(m, GUARD_V, FPW_GUARD)
backhand(m, 0.7, 1.5, 2.5, wind=DEFLECT, hit=P(BACK_HIT, body=[16, 24, 0], root=[-0.24, 0.4], **LUNGE_DEEP),
         end=P(BACK_END, body=[14, 50, 0], root=[-0.26, 0.4], **LUNGE_DEEP))
strike_with(m, "slash_h", 3.6, 4.5, 5.4, a=P(body=[14, 44, 0], root=[-0.26, 0.4], **LUNGE_DEEP), b=P(body=[16, -42, 0], root=[-0.28, 0.46], **LUNGE_DEEP))
keyp(m, 6.1, P(OVER1, body=[-14, -10, 0], head=[-10, 0], rl=[20, 0, 4], ll=[-26, 0, -4], rk=24, lk=30, root=[-0.1, 0.34]), "out3")
strike_with(m, "slash_v", 6.6, 7.5, 9.0, a=P(body=[-14, -6, 0], rl=[20, 0, 4], ll=[-26, 0, -4], rk=24, lk=30, root=[-0.1, 0.34]),
            b=P(body=[36, 0, 0], head=[-8, 0], root=[-0.36, 0.5], **LUNGE_DEEP))
keyp(m, 11.0, P(body=[30, 4, 0], head=[-8, 0], ra=arm([0.08, -0.6, 0.8], 10, [0.05, -0.75, 0.66]), la=arm([-0.6, -0.55, 0.0], 30), root=[-0.32, 0.46],
                **LUNGE_DEEP))
keyp(m, 13.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 11.0, FPW_DOWN)
fpw(m, 13.6, FPW_LOW)
use_skill(m, "swordmaster_parry:riposte")

# 불의 울타리 (효과: 0 둘레에 불꽃 울타리 → 3 · 13 · 23 · 33 · 43 가로지르기 (단계 :cut0 · :cut1 번갈아 · 마지막 :last — 3틱에 내달려 그 끝에서 벰 (6 · 16 …
# 맞히면 멈칫 2틱)) → 56 별 자국이 타오름 (:last 의 13)). 가로지르는 쪽은 적마다 달라 다리는 제자리에서 내달리는 꼴 (몸은 서버가 옮김)
LOW_L = P(IAI, body=[20, -32, 0], head=[-4, 0], rl=[22, 0, 6], ll=[-30, 0, -6], rk=46, lk=56, root=[-0.28, 0.06])  # 칼을 왼 허리 아래로 숨기고 낮게 (되돌려 벨 준비)
LOW_R = P(body=[20, 30, 0], head=[-4, 0], ra=arm([0.55, -0.6, 0.2], 40, [0.6, -0.1, -0.8]), la=arm([-0.5, -0.55, 0.45], 35), rl=[20, 0, 6], ll=[-28, 0, -6],
          rk=44, lk=52, root=[-0.26, 0.08])  # 칼을 오른쪽 아래 뒤로 끌어 낮게 (바로 벨 준비)
SPRINT_A = dict(rl=[-46, 0, 2], ll=[34, 0, -2], rk=58, lk=30)  # 내달리는 다리 (오른발 앞)
SPRINT_B = dict(rl=[30, 0, 2], ll=[-44, 0, -2], rk=26, lk=60)  # (왼발 앞)
DASH_L = P(LOW_L, body=[34, -28, 0], head=[-20, 0], la=arm([0.1, -0.5, -0.85], 20), root=[-0.22, 0.3], **SPRINT_A)
DASH_R = P(RUSH, body=[34, 22, 0], head=[-20, 0], root=[-0.22, 0.3], **SPRINT_A)
PASS_HIT_B = P(DRAW_HIT, body=[20, 34, 0], head=[-8, 0], root=[-0.3, 0.5], **LUNGE_DEEP)  # 지나가며 되돌려 벰
PASS_END_B = P(DRAW_END, body=[16, 60, 0], head=[-8, 0], root=[-0.32, 0.45], **LUNGE_DEEP)
SKID_B = P(ZANSHIN, body=[22, 46, 0], head=[-6, 0], la=arm([-0.45, -0.8, 0.25], 15), root=[-0.42, 0.22], **BRACE)  # 미끄러져 멈춤 (칼은 오른쪽 아래)
SKID_F = P(body=[22, -46, 0], head=[-6, 0], ra=arm([-0.7, -0.45, 0.4], 20, [-0.6, -0.6, -0.5]), la=arm([0.1, -0.85, 0.4], 15), root=[-0.42, 0.22], **BRACE)

m = Motion("swordmaster_ring", 8, "불의 울타리 — 칼을 낮게 뻗고 오른쪽으로 한 바퀴 휘둘러 둘레에 불꽃 울타리를 세운 뒤 칼을 왼 허리 아래로 숨기고 낮게 웅크림",
           fade=(0.03, 0.1))
spin_right(m, [1.5], 2.8, wind_tick=0.2, fwd=P(K.SPIN_FWD_R, ra=arm([0.12, -0.25, 0.96], 6, [0.4, -0.3, 0.87]), rk=42, lk=42, root=[-0.26, 0]),
           end_pose=LOW_L)
keyp(m, 6.0, P(LOW_L, spin=-360, body=[22, -34, 0], root=[-0.3, 0.06]))
fpw(m, 6.0, FPW_SHEATHED)
carry_spin(m, 2.8, -360)
m.set(softLegs=False)
use_skill(m, "swordmaster_ring")


def ring_cut(name, key, back, last=False):
    """가로지르기 한 번 (단계 동작 — 0 에 서버가 보냄: 0 ~ 3 내달림 → 3 지나가며 벰 → 미끄러져 멈춤 → 다음 쪽으로 낮게 (9 — 다음 단계의 0 자세))"""
    ticks = 18 if last else 13
    about = ("되돌려 베기" if back else "바로 베기")
    mo = Motion(name, ticks, "불의 울타리 가로지르기 — 낮게 숙여 내달리다 지나가며 " + about + (" 하고 미끄러져 멈춰 베어 낸 자세로 버티다가 칼을 털어냄" if last else
                " 하고 미끄러져 멈춘 뒤 다음 쪽으로 낮게 웅크림"), fade=(0.0, 0.06 if not last else 0.1))
    if back:
        first(mo, LOW_L, FPW_SHEATHED)
        keyp(mo, 1.0, DASH_L, "out3")
        keyp(mo, 2.1, P(DASH_L, body=[36, -34, 0], root=[-0.24, 0.36], **SPRINT_B))
        backhand(mo, 2.4, 3.0, 4.2, wind=P(DASH_L, body=[32, -46, 0], root=[-0.26, 0.4], **SPRINT_B), hit=PASS_HIT_B, end=PASS_END_B)
        keyp(mo, 6.4, SKID_B, "out")
        fpw(mo, 1.0, FPW_SHEATHED, "out3")
        fpw(mo, 6.4, FPW_ZAN, "out")
    else:
        first(mo, LOW_R, FPW_BACK)
        keyp(mo, 1.0, DASH_R, "out3")
        keyp(mo, 2.1, P(DASH_R, body=[36, 26, 0], root=[-0.24, 0.36], **SPRINT_B))
        strike_with(mo, "slash_h", 2.4, 3.0, 4.2, a=P(body=[32, 40, 0], head=[-10, 0], root=[-0.26, 0.4], **SPRINT_B),
                    b=P(body=[16, -60, 0], head=[-8, 0], root=[-0.32, 0.45], **LUNGE_DEEP))
        keyp(mo, 6.4, SKID_F, "out")
        fpw(mo, 1.0, FPW_BACK, "out3")
        fpw(mo, 6.4, dict(p=(-1.2, -0.3, 0.2), blade=(-0.7, -0.35, -0.6)), "out")
    if last:
        keyp(mo, 9.6, P(SKID_B, body=[18, 50, 0], root=[-0.36, 0.2], rk=54, lk=50))
        keyp(mo, 12.2, P(FLICK, root=[-0.3, 0.16], **BRACE), "in")  # 칼을 털어냄 = 별 자국이 타오름 (13)
        keyp(mo, 13.4, P(FLICK, body=[12, 30, 0], root=[-0.3, 0.16], **BRACE), "out")
        keyp(mo, 16.0, P(W_READY, body=[8, 6, 0]))
        fpw(mo, 9.6, FPW_ZAN)
        fpw(mo, 12.2, FPW_FLICK, "in")
        fpw(mo, 16.0, FPW_LOW)
    elif back:
        keyp(mo, 9.0, LOW_R)  # 다음은 바로 베기 (:cut1 의 0)
        fpw(mo, 9.0, FPW_BACK)
    else:
        keyp(mo, 9.0, LOW_L)  # 다음은 되돌려 베기 (:cut0 · :last 의 0)
        fpw(mo, 9.0, FPW_SHEATHED)
    mo.set(softLegs=False)
    impacts(mo, 3.0)
    use_skill(mo, key)


ring_cut("swordmaster_ring_cut0", "swordmaster_ring:cut0", True)
ring_cut("swordmaster_ring_cut1", "swordmaster_ring:cut1", False)
ring_cut("swordmaster_ring_last", "swordmaster_ring:last", True, last=True)

# 용의 피 (태세 — 효과: 0 몸에서 솟는 금빛 불길 · 하늘 빛줄기 · 용의 울음 · 0 · 4 · 8 발밑 불꽃 고리)
DB_GATHER = P(GATHER_W, body=[26, -8, 0], head=[24, 0], rk=48, lk=48, root=[-0.26, 0])  # 웅크려 칼을 몸 앞에 가로로 품음
DB_ERUPT = P(body=[-16, 6, 0], head=[-30, 0], ra=arm([0.82, -0.42, 0.3], 10, [0.6, -0.55, 0.55]), la=L(A_SPREAD_DOWN), rl=[0, 0, 14], ll=[0, 0, -14], rk=12,
             lk=12, root=[0.06, 0])  # 가슴을 펴며 두 팔을 아래 바깥으로 펼치고 하늘을 보며 포효
DB_STANCE = P(body=[26, -14, 0], head=[-12, 0], ra=arm([0.45, -0.55, 0.7], 30, [0.25, 0.1, 0.96]), la=bent([-0.35, -0.1, 0.9], 60, (0.3, 1, 0)), rl=[24, 0, 8],
              ll=[-30, 0, -8], rk=56, lk=50, root=[-0.32, 0.12])  # 용처럼 낮게 — 칼끝을 앞으로 · 왼손은 발톱처럼
m = Motion("swordmaster_blood", 24, "용의 피 — 웅크려 칼을 품었다가 가슴을 펴며 두 팔을 펼치고 하늘을 향해 포효하고 (몸이 떨림), 낮게 내려앉아 칼끝을 앞으로 겨눈 용의 자세를 잡음",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 2.0, DB_GATHER, "out3")
keyp(m, 3.2, DB_ERUPT, "out")
keyp(m, 6.0, P(DB_ERUPT, body=[-20, 6, 2], head=[-36, 0], root=[0.08, 0]))
keyp(m, 8.2, P(DB_ERUPT, body=[-18, 4, -2], head=[-33, 0], root=[0.07, 0]))
keyp(m, 10.5, P(DB_ERUPT, body=[-17, 6, 1], head=[-30, 0], root=[0.06, 0]))
keyp(m, 13.5, DB_STANCE, "in")
keyp(m, 18.0, P(DB_STANCE, body=[24, -10, 0], root=[-0.3, 0.1], rk=52, lk=48))
keyp(m, 21.5, P(W_READY, body=[8, 4, 0]))
fpw(m, 2.0, dict(p=(-0.5, -0.05, 0.0), blade=(-1, 0.1, 0.2)), "out3")
fpw(m, 3.2, FPW_SPREAD, "out")
fpw(m, 10.5, FPW_SPREAD, dp=(0.05, -0.05, 0))
fpw(m, 13.5, dict(p=(-0.25, -0.22, -0.15), blade=(0.05, 0.15, 1)), "in")
fpw(m, 18.0, dict(p=(-0.24, -0.25, -0.12), blade=(0.05, 0.12, 1)))
fpw(m, 21.5, FPW_LOW)
fpl(m, 2.0, nudge(FP_GUARD, (-0.05, -0.1, 0)), "out3")
fpl(m, 3.2, FP_PUSH_OUT, "out")
fpl(m, 10.5, FP_PUSH_OUT)
fpl(m, 13.5, nudge(FP_PALM, (0.05, -0.14, 0.05)), "in")
fpl(m, 18.0, nudge(FP_PALM, (0.05, -0.16, 0.06)))
fpl(m, 21.5, FP_LOW)
use_skill(m, "swordmaster_blood")

# 스타폴 블레이드 (유성 연참 — 효과: 0 노을 하늘 · 웅크려 칼에 빛 → 4 ~ 8 솟구침 (JobMove 6칸) → 9 ~ 12 첫 혜성 (단계 :dive — 10틱마다 여섯) → 63 ~ 75 마지막 낙하 (:final)).
# 공중 자세 (F_ · S_) 는 블러드 문 뛰어오름도 씀
F_CROUCH = P(body=[26, 0, 0], head=[-16, 0], ra=arm([0.4, -0.5, -0.5], 30, [0.2, -0.1, -0.97]), la=arm([-0.4, -0.5, -0.5], 30), rl=[-42, 0, 10],
             ll=[-42, 0, -10], rk=86, lk=86, root=[-0.34, 0.05])
F_AIR = P(REV_RAISE, body=[-14, 0, 0], head=[8, 0], rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0.1])  # 칼을 거꾸로 쥐고 두 손으로 머리 위로
F_PLUNGE = P(body=[24, 0, 0], head=[16, 0], ra=arm([0.0, -0.3, 0.95], 10, [0, -0.96, 0.25]), la=bent([0.05, -0.3, 0.95], 14, (0, 0, 1)), rl=[-20, 0, 6],
             rk=40, ll=[-6, 0, -6], lk=24, root=[0.0, 0.2])  # 칼끝을 아래로 내리며 떨어짐
S_IMPACT = P(body=[38, 0, 0], head=[-6, 0], ra=arm([0.0, -0.62, 0.78], 12, [0, -0.97, 0.22]), la=bent([0.05, -0.64, 0.76], 16, (0, 0, 1)), rl=[2, 0, 4], rk=92,
             ll=[-82, 0, -4], lk=88, root=[-0.38, 0.25])  # 한 무릎을 꿇고 거꾸로 쥔 칼을 땅에 꽂음
SF_CROUCH = P(F_CROUCH, body=[30, 0, 0], head=[-20, 0], ra=arm([0.3, -0.62, 0.6], 40, [0.15, 0.25, 0.95]), la=bent([0.05, -0.6, 0.62], 40, (0, 0, 1)),
              root=[-0.36, 0.05])  # 두 손으로 칼을 낮게 앞으로 쥐고 깊이 웅크림
SF_RISE = P(RAISE2H, body=[-16, 0, 0], head=[-26, 0], rl=[-30, 0, 8], rk=70, ll=[-10, 0, -8], lk=40, root=[0.0, 0.1])  # 칼을 하늘로 들고 솟구침 (다리를 접음)
SF_LAND = P(S_IMPACT, body=[34, 0, 0], head=[-4, 0], root=[-0.32, 0.25])  # 혜성이 꽂힘 — 한 무릎 · 칼끝이 땅
m = Motion("swordmaster_ult", 10, "스타폴 블레이드 — 두 손으로 칼을 낮게 앞으로 쥐고 깊이 웅크렸다가, 칼을 하늘로 들어 올리며 솟구쳐 다리를 접고, 하늘에서 칼을 거꾸로 쥠",
           fade=(0.03, 0.03))
off_bare(m)
keyp(m, 1.5, SF_CROUCH, "out3")
keyp(m, 3.6, P(SF_CROUCH, body=[34, 0, 0], rk=96, lk=96, root=[-0.4, 0.05]))
keyp(m, 5.0, SF_RISE, "out")
keyp(m, 8.0, P(SF_RISE, body=[-20, 0, 0], head=[-30, 0]))
keyp(m, 9.6, P(F_AIR, body=[-16, 0, 0]), "in")
fpw(m, 1.5, FPW_LOW, "out3")
fpw(m, 5.0, FPW_SKY, "out")
fpw(m, 9.6, FPW_REV_UP, "in")
fpl(m, 5.0, FPL_RAISE2H, "out")
fpl(m, 9.6, FPL_RAISE2H)
m.set(softLegs=False)
use_skill(m, "swordmaster_ult")

# 혜성 (단계 :dive — 10틱마다: 0 ~ 3 칼끝을 아래로 내리며 떨어짐 → 3 꽂힘 (둘레 · 맞히면 멈칫) → 4 ~ 9 칼을 뽑아 들며 다음 적 위로 솟구침 · 칼을 다시 거꾸로 쥠)
m = Motion("swordmaster_ult_dive", 10, "혜성 — 칼을 거꾸로 쥐고 두 손으로 머리 위에 든 채, 칼끝을 아래로 내리며 떨어져 땅에 내리꽂고 한 무릎으로 받은 뒤 칼을 뽑아 들며 다시 솟구침",
           fade=(0.0, 0.03))
off_bare(m)
first(m, P(F_AIR, body=[-16, 0, 0]), FPW_REV_UP)
keyp(m, 1.6, F_PLUNGE, "in")
keyp(m, 3.0, SF_LAND, "in")
keyp(m, 4.6, P(SF_LAND, body=[36, 0, 0], root=[-0.34, 0.25]))
keyp(m, 6.6, P(SF_RISE, body=[-10, 0, 0]), "out3")
keyp(m, 9.6, P(F_AIR, body=[-16, 0, 0]), "in")
fpw(m, 1.6, FPW_DOWN, "in")
fpw(m, 4.6, FPW_DOWN)
fpw(m, 6.6, FPW_SKY, "out3")
fpw(m, 9.6, FPW_REV_UP, "in")
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 1.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 4.6, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 6.6, FPL_RAISE2H, "out3")
m.set(softLegs=False)
impacts(m, 3.0)
use_skill(m, "swordmaster_ult:dive")

# 마지막 낙하 (단계 :final — 0 = 여섯째 혜성 다음 틱: 0 ~ 7 칼을 뽑아 들며 높이 솟구침 → 7 ~ 9 하늘에서 칼을 크게 젖혀 불길을 모음 → 9 ~ 12 거꾸로 쥐고 떨어짐 →
# 12 땅에 깊이 내리꽂음 (대폭발 — 맞히면 멈칫) → 버티고 일어섬)
m = Motion("swordmaster_ult_final", 18, "마지막 낙하 — 칼을 뽑아 들며 높이 솟구쳐 하늘에서 칼을 머리 위로 크게 젖혀 불길을 모았다가, 칼을 거꾸로 쥐고 가운데로 떨어져 땅에 깊이 "
           "내리꽂고 한 무릎으로 버틴 뒤 일어섬", fade=(0.0, 0.12))
off_bare(m)
first(m, SF_LAND, FPW_DOWN)
keyp(m, 2.4, SF_RISE, "out3")
keyp(m, 6.0, P(SF_RISE, body=[-24, 0, 0], head=[-34, 0]))
keyp(m, 8.2, P(SF_RISE, body=[-30, 0, 0], head=[-38, 0], root=[0.02, 0.12]))
keyp(m, 9.6, P(F_AIR, body=[-22, 0, 0]), "in")
keyp(m, 11.0, F_PLUNGE, "in")
keyp(m, 12.0, P(S_IMPACT, body=[42, 0, 0], root=[-0.42, 0.28]), "in")
keyp(m, 14.6, P(S_IMPACT, body=[40, 0, 0], root=[-0.4, 0.26]))
keyp(m, 16.4, P(S_IMPACT, body=[28, 6, 0], rk=64, lk=58, root=[-0.28, 0.2]))
keyp(m, 17.8, P(W_READY, body=[8, 4, 0]))
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 8.2, FPW_SKY, dp=(0.0, 0.08, 0.2))
fpw(m, 9.6, FPW_REV_UP, "in")
fpw(m, 11.0, FPW_DOWN, "in")
fpw(m, 16.4, FPW_DOWN)
fpw(m, 17.8, FPW_LOW)
fpl(m, 2.4, FPL_RAISE2H, "out3")
fpl(m, 9.6, FPL_RAISE2H)
fpl(m, 11.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 16.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 17.8, FP_LOW)
m.set(softLegs=False)
impacts(m, 12.0)
use_skill(m, "swordmaster_ult:final")

# ------------------------------------------------------------ 블러드 나이트 (칼 — 검붉은 빛 · 무겁게 두 손으로)

# 핏빛 대검 1 (효과: 0 칼에 피가 감김 → 5 크게 휩씀 (왼쪽 → 오른쪽 — 칼끝은 × 1.5 · 맞히면 멈칫 2 ~ 3틱))
CL_WIND = P(body=[12, -74, 0], head=[6, 0], ra=arm([-0.55, -0.45, 0.6], 60, [-0.75, -0.2, -0.62]), la=bent([-0.6, -0.55, 0.45], 50, (0.3, 1, 0.2)),
            rl=[22, 0, 6], ll=[-28, 0, -6], rk=40, lk=50, root=[-0.24, -0.1])  # 두 손으로 칼을 왼쪽 뒤 아래로 끌어 크게 감음 (무게는 뒷발)
CL_WIND2 = P(CL_WIND, body=[14, -86, 0], rk=52, lk=62, root=[-0.34, -0.14])
CL_HIT = P(body=[18, 26, 0], head=[0, 0], ra=arm([0.35, -0.05, 0.94], 6, [0.88, -0.08, 0.46]), la=bent([0.1, -0.2, 0.97], 30, (0.6, 1, 0)), root=[-0.3, 0.48],
           **LUNGE_DEEP)
CL_END = P(body=[14, 64, 0], head=[0, 0], ra=arm([0.9, -0.12, 0.25], 16, [0.7, -0.3, -0.65]), la=arm([-0.82, -0.3, -0.45], 25), root=[-0.32, 0.5],
           **LUNGE_DEEP)  # 칼에 끌려 몸이 끝까지 돌고 왼손은 놓아 뒤로
HEAVY_R = P(body=[14, 26, 0], head=[-4, 0], ra=arm([0.5, -0.7, 0.35], 30, [0.45, -0.35, 0.82]), la=arm([-0.45, -0.75, 0.3], 30), rl=[20, 0, 4], ll=[-24, 0, -4],
            rk=30, lk=34, root=[-0.18, 0.2])  # 무거운 칼을 오른쪽 아래에 늘어뜨림
FPW_HEAVY_R = dict(p=(0.2, -0.45, 0.15), blade=(0.45, 0.2, 0.87))
m = Motion("bloodknight_cleave", 14, "핏빛 대검 — 두 손으로 칼을 왼쪽 뒤 아래로 끌어 크게 감고 낮아졌다가, 크게 내디디며 앞을 가로로 휩쓸어 몸이 끝까지 돌고 칼을 오른쪽에 "
           "늘어뜨림", fade=(0.03, 0.08))
off_bare(m)
keyp(m, 2.4, CL_WIND, "out3")
backhand(m, 4.0, 5.0, 6.2, wind=CL_WIND2, hit=CL_HIT, end=CL_END)
keyp(m, 8.4, P(CL_END, body=[12, 56, 0], root=[-0.28, 0.44]))
keyp(m, 11.4, HEAVY_R)
fpw(m, 2.4, FP_BACK0, "out3")
fpw(m, 8.4, FPW_SPREAD, dp=(0, -0.1, 0))
fpw(m, 11.4, FPW_HEAVY_R)
fpl(m, 2.4, ((-0.3, -0.12, -0.05), (10, 70, 0)), "out3")
fpl(m, 5.0, FP_LOW, "linear")
fpl(m, 11.4, FP_LOW)
impacts(m, 5.0)
use_skill(m, "bloodknight_cleave")

# 핏빛 대검 2 (단계 :2 — 다시 누름 (첫 단계 뒤 6틱부터): 5 오른쪽 아래에서 왼쪽 위로 비스듬히 올려 휩씀 (칼끝 × 1.5))
C2_WIND = P(body=[18, 66, 0], head=[6, 0], ra=arm([0.55, -0.75, -0.2], 30, [0.55, -0.35, -0.75]), la=bent([0.3, -0.7, 0.3], 60, (0, 1, 0.3)), rl=[26, 0, 6],
            ll=[-30, 0, -6], rk=48, lk=54, root=[-0.3, 0.05])  # 칼을 오른쪽 뒤 아래로 떨어뜨려 감음
C2_HIT = P(body=[2, -24, 0], head=[-10, 0], ra=arm([-0.35, 0.45, 0.82], 8, [-0.55, 0.62, 0.55]), la=bent([-0.2, 0.3, 0.9], 40, (0.5, 1, 0)), rl=[30, 0, 4],
           ll=[-44, 0, -2], rk=20, lk=46, root=[-0.14, 0.48])
C2_END = P(body=[-6, -52, 0], head=[-14, 0], ra=arm([-0.6, 0.72, 0.2], 14, [-0.45, 0.75, -0.48]), la=arm([0.25, -0.6, 0.6], 30), rl=[30, 0, 4], ll=[-44, 0, -2],
           rk=18, lk=44, root=[-0.12, 0.44])  # 칼이 왼쪽 위 뒤까지 올라감
m = Motion("bloodknight_cleave_2", 14, "핏빛 대검 둘째 — 칼을 오른쪽 뒤 아래로 떨어뜨려 감았다가 크게 내디디며 왼쪽 위로 비스듬히 올려 휩쓸고 칼을 높이 든 채 버팀",
           fade=(0.0, 0.08))
off_bare(m)
first(m, P(CL_END, body=[12, 56, 0], root=[-0.28, 0.44]), FPW_SPREAD)
keyp(m, 2.6, C2_WIND, "out3")
keyp(m, 4.0, P(C2_WIND, body=[20, 76, 0], rk=56, lk=62, root=[-0.38, 0.0]), "in")
keyp(m, 5.0, C2_HIT, "linear")
keyp(m, 6.4, C2_END, "out")
m.arc(5.0, "ra", (-0.3, 0.2, 1))
m.head.setdefault("trail", []).append([m.t(4.3), m.t(6.3)])
m.set(tilt=30)
keyp(m, 9.0, P(C2_END, body=[-2, -44, 0], root=[-0.12, 0.38]))
keyp(m, 12.0, P(C2_END, body=[4, -30, 0], head=[-8, 0], rk=24, lk=30, root=[-0.12, 0.2]))
fpw(m, 2.6, dict(p=(0.55, -0.62, 0.3), blade=(0.62, -0.4, -0.68)), "out3")
fpw(m, 4.0, dict(p=(0.5, -0.58, 0.25), blade=(0.65, -0.45, -0.6)), "in")
fpw(m, 4.55, dict(p=(0.0, -0.45, -0.05), blade=(0.5, -0.1, 0.86)), "linear")
fpw(m, 5.0, dict(p=(-0.55, 0.0, -0.1), blade=(-0.55, 0.65, 0.52)), "linear")
fpw(m, 5.6, dict(p=(-1.3, 0.55, 0.1), blade=(-0.8, 0.55, -0.2)), "linear")
fpw(m, 6.4, dict(p=(-2.0, 0.8, 0.4), blade=(-0.6, 0.6, -0.55)), "out")
fpw(m, 9.0, dict(p=(-0.9, 0.7, 0.2), blade=(-0.3, 0.9, -0.3)))
fpw(m, 12.0, FPW_UP)
fpl(m, 2.6, ((0.15, -0.3, 0.0), (0, -20, 0)), "out3")
fpl(m, 6.4, FP_LOW)
impacts(m, 5.0)
use_skill(m, "bloodknight_cleave:2")

# 핏빛 대검 3 (단계 :3 — 0 ~ 7 앞으로 1.2칸 · 0.9칸 높이 뜀 (JobMove.hop) → 7 앞 땅에 내리찍음 (둘레 · 가운데 × 1.5 · 갈라짐 — 맞히면 멈칫 2 ~ 3틱))
J_CROUCH = P(body=[26, -6, 0], head=[-8, 0], ra=arm([0.2, 0.9, -0.35], 40, [0.05, 0.35, -0.94]), la=arm([-0.1, 0.92, -0.3], 44), rl=[-42, 0, 10],
             ll=[-42, 0, -10], rk=84, lk=84, root=[-0.34, 0.0])  # 칼을 머리 위로 들며 웅크림 (뛰기 전)
J_AIR = P(W_HIGH2, body=[-18, 0, 0], head=[-12, 0], rl=[-54, 0, 8], rk=94, ll=[-26, 0, -8], lk=80, root=[0.0, 0.1])  # 떠서 몸을 젖히고 무릎을 당김
J_APEX = P(J_AIR, body=[-24, 0, 0], ra=arm([0.1, 0.8, -0.58], 34, [0, 0.0, -1]), la=arm([-0.05, 0.8, -0.56], 38), root=[0.02, 0.14])
J_IMPACT = P(PLANT, body=[42, 0, 0], head=[-6, 0], rl=[-34, 0, 10], ll=[20, 0, -8], rk=78, lk=48, root=[-0.4, 0.3])  # 내리찍고 깊이 웅크려 버팀
m = Motion("bloodknight_cleave_3", 16, "핏빛 대검 셋째 — 칼을 머리 위로 들며 웅크렸다가 뛰어올라 몸을 젖혀 칼을 머리 뒤까지 넘기고, 내려앉으며 앞 땅을 내리찍어 깊이 웅크려 버팀",
           fade=(0.02, 0.1))
off_bare(m)
first(m, P(C2_END, body=[4, -30, 0], head=[-8, 0], rk=24, lk=30, root=[-0.12, 0.2]), FPW_UP)
keyp(m, 1.2, J_CROUCH, "out3")
keyp(m, 3.2, J_AIR, "out")
keyp(m, 5.2, J_APEX)
strike_with(m, "slam", 5.6, 7.0, 8.6, a=P(J_APEX, root=[0.0, 0.14]), b=P(J_IMPACT))
keyp(m, 10.4, P(J_IMPACT, body=[40, 0, 0], root=[-0.38, 0.28]))
keyp(m, 12.6, P(body=[20, 10, 0], head=[-6, 0], ra=arm([0.5, -0.5, 0.7], 20, [0.4, 0.4, 0.82]), la=arm([-0.45, -0.7, 0.3], 30), rl=[20, 0, 4],
                ll=[-26, 0, -4], rk=30, lk=34, root=[-0.2, 0.15]), "out3")
keyp(m, 14.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.2, FPW_UP, "out3")
fpw(m, 3.2, FPW_SKY, "out")
fpw(m, 10.4, FPW_DOWN)
fpw(m, 12.6, FPW_SPREAD, "out3")
fpw(m, 14.6, FPW_LOW)
fpl(m, 1.2, FPL_RAISE2H, "out3")
fpl(m, 5.2, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 6.0, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 7.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 10.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 12.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 7.0)
use_skill(m, "bloodknight_cleave:3")

# 피의 돌진 (효과: 0 ~ 6 적에게 1.3칸 높이로 뛰어듦 (JobMove.hop) → 6 내려 박음 (기절 · 방어 깨짐 — 맞히면 멈칫 3틱))
LP_CROUCH = P(body=[28, 10, 0], head=[-16, 0], ra=arm([0.45, -0.45, -0.6], 30, [0.35, -0.1, -0.93]), la=arm([-0.4, -0.4, 0.6], 30), rl=[-42, 0, 10],
              ll=[-42, 0, -10], rk=86, lk=86, root=[-0.36, 0.0])  # 칼을 뒤로 끌며 깊이 웅크림
LP_AIR = P(W_HIGH2, body=[-14, 0, 0], head=[-4, 0], rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0.12])
m = Motion("bloodknight_leap", 16, "피의 돌진 — 칼을 뒤로 끌며 깊이 웅크렸다가 적에게 뛰어들어 공중에서 두 손으로 칼을 머리 위로 들고, 내려앉으며 내려 박은 뒤 칼을 뽑아 피를 털어냄",
           fade=(0.02, 0.1))
off_bare(m)
keyp(m, 1.0, LP_CROUCH, "out3")
keyp(m, 3.0, LP_AIR, "out")
keyp(m, 4.4, P(LP_AIR, body=[-20, 0, 0], root=[0.02, 0.16]))
strike_with(m, "slam", 4.7, 6.0, 7.4, a=P(LP_AIR, body=[-20, 0, 0], root=[0.02, 0.16]), b=P(J_IMPACT))
keyp(m, 9.2, P(J_IMPACT, body=[38, 0, 0], root=[-0.36, 0.28]))
keyp(m, 11.2, P(body=[14, 24, 0], head=[-6, 0], ra=arm([0.75, -0.3, 0.55], 18, [0.6, 0.55, -0.55]), la=arm([-0.5, -0.65, 0.3], 30), rl=[20, 0, 4],
                ll=[-26, 0, -4], rk=30, lk=34, root=[-0.18, 0.15]), "out3")  # 칼을 뽑아 올림
keyp(m, 12.6, P(body=[12, 30, 0], head=[-4, 0], ra=arm([0.6, -0.7, 0.35], 24, [0.45, -0.85, 0.25]), la=arm([-0.5, -0.65, 0.3], 30), rl=[18, 0, 4],
                ll=[-24, 0, -4], rk=26, lk=30, root=[-0.14, 0.12]), "in")  # 피를 털어냄
keyp(m, 14.8, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.0, FPW_BACK, "out3")
fpw(m, 3.0, FPW_UP, "out")
fpw(m, 9.2, FPW_DOWN)
fpw(m, 11.2, FPW_SPREAD, "out3")
fpw(m, 12.6, FPW_FLICK, "in")
fpw(m, 14.8, FPW_LOW)
fpl(m, 3.0, FPL_RAISE2H, "out")
fpl(m, 4.4, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 5.2, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 6.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 9.2, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 11.2, FP_LOW)
m.set(softLegs=False)
impacts(m, 6.0)
use_skill(m, "bloodknight_leap")

# 피의 사슬 (효과: 0 땅의 붉은 고리 → 4 둘레 적에게 사슬을 박음 (맞히면 멈칫 2틱) → 34 까지 사슬이 이어짐 → 34 끌어당김 (끌린 적이 있으면 단계 :pull))
CH_WIND = P(body=[8, 34, 0], head=[0, 0], ra=arm([0.45, -0.7, 0.3], 30, [0.35, -0.5, 0.8]), la=bent([0.55, 0.2, 0.4], 110, (0, 1, 0.3)), rl=[16, 0, 6],
            ll=[-14, 0, -6], rk=30, lk=26, root=[-0.16, -0.05])  # 왼손을 오른 어깨 쪽으로 당겨 감음
CH_THROW = P(body=[14, -26, 0], head=[-4, 0], ra=arm([0.45, -0.7, 0.3], 30, [0.35, -0.5, 0.8]), la=L(A_PALM), rl=[24, 0, 4], ll=[-34, 0, -2], rk=16, lk=36,
             root=[-0.12, 0.3])  # 왼손을 앞으로 내뻗어 사슬을 던짐
CH_HOLD = P(body=[-6, -18, 0], head=[-2, 0], ra=arm([0.5, -0.65, -0.2], 25, [0.4, -0.3, -0.85]), la=L(A_JAB), rl=[14, 0, 6], ll=[-30, 0, -6], rk=40, lk=10,
            root=[-0.2, -0.15])  # 사슬을 움켜쥐고 몸을 뒤로 젖혀 버팀
CH_YANK = P(body=[-12, 30, 0], head=[-6, 0], ra=arm([0.55, -0.6, 0.3], 25, [0.4, -0.4, 0.82]), la=L(A_PULL), rl=[22, 0, 6], ll=[-16, 0, -6], rk=44, lk=20,
            root=[-0.24, -0.25])  # 주먹을 허리로 당겨 사슬을 잡아당김
m = Motion("bloodknight_chains", 42, "피의 사슬 — 왼손을 오른 어깨 쪽으로 감았다가 앞으로 내뻗어 사슬을 던지고, 사슬을 움켜쥔 채 몸을 젖혀 버티다가 (흔들림) 한 번 늦췄다 "
           "주먹을 허리로 당겨 잡아당김", fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.8, CH_WIND, "out3")
keyp(m, 4.0, CH_THROW, "out")
keyp(m, 7.0, CH_HOLD, "out")
keyp(m, 12.0, P(CH_HOLD, body=[-10, -24, 0], root=[-0.22, -0.18]))
keyp(m, 17.0, P(CH_HOLD, body=[-5, -15, 0], root=[-0.19, -0.13]))
keyp(m, 22.0, P(CH_HOLD, body=[-12, -26, 0], root=[-0.23, -0.19]))
keyp(m, 27.0, P(CH_HOLD, body=[-7, -18, 0], root=[-0.2, -0.15]))
keyp(m, 31.6, P(CH_HOLD, body=[8, -22, 0], la=arm([0.12, 0.0, 1], 0), root=[-0.16, 0.02]))  # 한 번 늦춤 (당기기 전)
keyp(m, 34.0, CH_YANK, "in")
keyp(m, 36.6, P(CH_YANK, body=[-4, 20, 0], root=[-0.16, -0.15]))
keyp(m, 40.0, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.8, FPW_LOW, dp=(0.1, -0.05, 0))
fpw(m, 40.0, FPW_LOW)
fpl(m, 1.8, FP_CHAMBER, "out3")
fpl(m, 4.0, FP_PALM, "out")
fpl(m, 7.0, FP_JAB, "out")
fpl(m, 17.0, nudge(FP_JAB, (0.02, -0.02, 0.03)))
fpl(m, 27.0, nudge(FP_JAB, (-0.02, 0.0, 0.02)))
fpl(m, 31.6, FP_JAB2)
fpl(m, 34.0, FP_CHAMBER, "in")
fpl(m, 40.0, FP_LOW)
impacts(m, 4.0)
use_skill(m, "bloodknight_chains")

# 끌어당김 (단계 :pull — 34: 끌려온 적을 2 에 내리찍음 (기절 — 맞히면 멈칫 3틱))
m = Motion("bloodknight_chains_pull", 15, "피의 사슬 끌어당김 — 당긴 주먹을 칼자루로 옮겨 두 손으로 칼을 머리 위로 번쩍 들고 끌려온 적을 내리찍은 뒤 버팀", fade=(0.0, 0.1))
off_bare(m)
first(m, CH_YANK, FPW_LOW)
keyp(m, 1.0, P(W_HIGH2, body=[-14, 10, 0], head=[-10, 0], rl=[20, 0, 6], ll=[-24, 0, -6], rk=28, lk=26, root=[-0.08, 0.0]), "out3")
strike_with(m, "slam", 1.1, 2.0, 3.4, a=P(body=[-14, 10, 0], head=[-10, 0], rl=[20, 0, 6], ll=[-24, 0, -6], rk=28, lk=26, root=[-0.08, 0.0]),
            b=P(J_IMPACT, root=[-0.36, 0.3]))
keyp(m, 6.4, P(J_IMPACT, body=[38, 0, 0], root=[-0.34, 0.26]))
keyp(m, 9.4, P(HEAVY_R, root=[-0.2, 0.18]), "out3")
keyp(m, 12.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 6.4, FPW_DOWN)
fpw(m, 9.4, FPW_HEAVY_R, "out3")
fpw(m, 12.6, FPW_LOW)
fpl(m, 0.0, FP_CHAMBER)
fpl(m, 1.0, FPL_RAISE2H, "out3")
fpl(m, 1.4, ((0.18, 0.12, 0.05), (45, -15, 0)))
fpl(m, 2.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 6.4, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 9.4, FP_LOW)
impacts(m, 2.0)
use_skill(m, "bloodknight_chains:pull")

# 광란 (태세 — 효과: 0 몸에서 터지는 피 · 붉은 포효 · 0 · 5 발밑 핏빛 고리)
FR_HUNCH = P(body=[34, 0, 0], head=[24, 0], ra=arm([0.3, -0.75, 0.55], 50, [0.0, -0.5, 0.86]), la=bent([-0.3, -0.7, 0.55], 90, (0.6, 1, 0)), rl=[-36, 0, 10],
             ll=[-36, 0, -10], rk=78, lk=78, root=[-0.32, 0])  # 몸을 말아 웅크림 (주먹을 움켜쥠)
FR_ROAR = P(body=[-18, 0, 0], head=[-34, 0], ra=arm([0.85, -0.35, 0.3], 10, [0.55, -0.5, 0.65]), la=L(A_SPREAD_DOWN), rl=[0, 0, 16], ll=[0, 0, -16], rk=14,
            lk=14, root=[0.05, 0])  # 가슴을 젖히고 두 팔을 펼쳐 포효
FR_FERAL = P(body=[30, -16, 0], head=[-14, 0], ra=arm([0.55, -0.55, -0.45], 25, [0.45, -0.35, -0.82]), la=bent([-0.3, -0.25, 0.9], 70, (0.4, 1, 0)),
             rl=[22, 0, 8], ll=[-34, 0, -8], rk=62, lk=56, root=[-0.34, 0.15])  # 짐승처럼 낮게 — 칼은 뒤로 · 왼손은 발톱처럼 앞으로
m = Motion("bloodknight_frenzy", 22, "광란 — 몸을 말아 웅크렸다가 가슴을 젖히고 두 팔을 펼쳐 포효하고 (몸이 떨림), 짐승처럼 낮게 내려앉아 칼을 뒤로 끌고 왼손을 발톱처럼 내밂",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 1.8, FR_HUNCH, "out3")
keyp(m, 2.8, FR_ROAR, "out")
keyp(m, 5.0, P(FR_ROAR, body=[-22, 0, 3], head=[-40, 0], root=[0.07, 0]))
keyp(m, 7.0, P(FR_ROAR, body=[-20, 0, -3], head=[-36, 0], root=[0.06, 0]))
keyp(m, 9.0, P(FR_ROAR, body=[-19, 0, 2], head=[-34, 0], root=[0.05, 0]))
keyp(m, 12.0, FR_FERAL, "in")
keyp(m, 16.0, P(FR_FERAL, body=[28, -10, 0], head=[-12, 0], root=[-0.32, 0.12]))
keyp(m, 19.5, P(W_READY, body=[8, 6, 0]))
fpw(m, 1.8, FPW_LOW, "out3", dp=(0, -0.1, 0))
fpw(m, 2.8, FPW_SPREAD, "out")
fpw(m, 9.0, FPW_SPREAD, dp=(0.04, -0.04, 0))
fpw(m, 12.0, FPW_BACK, "in", dp=(0, -0.2, 0))
fpw(m, 19.5, FPW_LOW)
fpl(m, 1.8, FP_GUARD, "out3")
fpl(m, 2.8, FP_PUSH_OUT, "out")
fpl(m, 9.0, FP_PUSH_OUT)
fpl(m, 12.0, nudge(FP_PALM, (0.05, -0.12, 0.05)), "in")
fpl(m, 16.0, nudge(FP_PALM, (0.04, -0.14, 0.06)))
fpl(m, 19.5, FP_LOW)
use_skill(m, "bloodknight_frenzy")

# 블러드 문 (효과: 0 붉은 하늘 · 앞 하늘의 핏빛 달 → 0 ~ 10 칼을 얼굴 앞에 세워 피를 빨아들임 (10 · 18 회복) → 단계 :leap (16))
BM_OFFER = P(body=[-4, 0, 0], head=[-10, 0], ra=arm([0.08, 0.35, 0.93], 70, [0.0, 1.0, 0.05]), la=bent([-0.05, 0.3, 0.95], 60, (0.2, 1, 0)), rl=[6, 0, 6],
             ll=[-6, 0, -6], rk=10, lk=12, root=[-0.04, 0])  # 칼을 얼굴 앞에 곧게 세우고 왼손을 칼날에 댐
BM_DRAIN = P(BM_OFFER, body=[-10, 0, 0], head=[-18, 0], rk=16, lk=18, root=[-0.08, 0])  # 피가 빨려 드는 동안 고개를 젖힘
FPW_OFFER = dict(p=(0.0, -0.05, 0.1), blade=(0.0, 1.0, 0.05))
m = Motion("bloodknight_ult", 18, "블러드 문 — 칼을 얼굴 앞에 곧게 세워 왼손을 칼날에 대고, 피가 빨려 드는 동안 고개를 젖혀 떨다가 칼을 오른쪽 아래로 내리며 깊이 웅크림",
           fade=(0.03, 0.03))
off_bare(m)
keyp(m, 2.0, BM_OFFER, "out3")
keyp(m, 6.0, P(BM_OFFER, body=[-6, 2, 0]))
keyp(m, 9.0, BM_DRAIN, "in")
keyp(m, 10.0, P(BM_DRAIN, body=[-14, 0, 0], head=[-24, 0]), "out")
keyp(m, 12.5, P(BM_DRAIN, body=[-12, -2, 0]))
keyp(m, 15.0, F_CROUCH, "in")
keyp(m, 16.0, P(F_CROUCH, rk=92, lk=92, root=[-0.38, 0.05]))
fpw(m, 2.0, FPW_OFFER, "out3")
fpw(m, 10.0, dict(p=(0.0, 0.0, 0.08), blade=(0.0, 1.0, -0.1)))
fpw(m, 15.0, FPW_BACK, "in")
fpl(m, 2.0, nudge(FP_PALM, (0.1, 0.05, 0.0)), "out3")
fpl(m, 10.0, nudge(FP_PALM, (0.1, 0.08, 0.0)))
fpl(m, 15.0, FP_LOW, "in")
use_skill(m, "bloodknight_ult")

# 뛰어오름 (단계 :leap — 0 = leapAt: 0 ~ 10 달을 향해 솟구침 (JobMove 5.5칸) · 10 ~ 12 칼을 거꾸로 머리 위로 들어 젖힘 · 12 ~ 15 떨어짐 → 15 내리꽂음 (초승달
# 검기 · 맞히면 멈칫) → 한 무릎으로 버팀 → 일어섬)
m = Motion("bloodknight_ult_leap", 24, "블러드 문 뛰어오름 — 웅크렸다 달을 향해 솟구쳐 칼을 거꾸로 쥐고 두 손으로 머리 위로 들어 몸을 크게 젖혔다가, 칼끝을 아래로 내리며 "
           "떨어져 땅에 내리꽂고 한 무릎으로 버틴 뒤 일어섬", fade=(0.0, 0.12))
off_bare(m)
first(m, F_CROUCH, FPW_BACK)
keyp(m, 2.0, P(F_AIR, body=[-10, 0, 0]), "out")
keyp(m, 8.0, P(F_AIR, body=[-20, 0, 0], head=[14, 0]))
keyp(m, 11.0, P(F_AIR, body=[-24, 0, 0], head=[16, 0]))
keyp(m, 13.4, F_PLUNGE, "in")
keyp(m, 15.0, S_IMPACT, "in")
keyp(m, 18.0, P(S_IMPACT, body=[40, 0, 0], root=[-0.4, 0.25]))
keyp(m, 21.0, P(S_IMPACT, body=[30, 6, 0], rk=70, lk=60, root=[-0.3, 0.2]))
keyp(m, 23.6, P(W_READY, body=[8, 6, 0]))
fpw(m, 2.0, FPW_REV_UP, "out")
fpw(m, 11.0, FPW_REV_UP)
fpw(m, 13.4, FPW_DOWN, "in")
fpw(m, 21.0, FPW_DOWN)
fpw(m, 23.6, FPW_LOW)
fpl(m, 2.0, FPL_RAISE2H, "out")
fpl(m, 11.0, FPL_RAISE2H)
fpl(m, 13.4, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 21.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 23.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 15.0)
use_skill(m, "bloodknight_ult:leap")
