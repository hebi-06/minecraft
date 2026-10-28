"""0.48.0 무기 스킬 동작 — 초월 (칼 그람 · 단검 솔라스 · 도끼 이그드라실 · 방패 스발린 · 활 아르테미스 · 지팡이 오딘 · 대검 티르빙).
틱 0 = 스킬이 나간 틱 (효과와 같은 틱). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (Kit.motion · motionLater — 주석의 "보냄")."""
import math

import rune_motion_kit as K
from rune_motion_kit import (A_CHEST, A_CROSS, A_HIP, A_PALM, A_PRAY, A_PULL, A_RAISE, A_RAISE_FIST, A_RAISE_FWD, A_SPREAD_DOWN, A_SPREAD_UP, A_THROW_FOLLOW,
                             A_THROW_REL, A_THROW_WIND, CALL, FP_CHAMBER, FP_CROSS, FP_DOWN, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, FP_SPREAD_UP, FPL_RAISE2H,
                             FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_SPREAD, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_SQUAT, LEGS_WIDE, P, RA_LOW,
                             RA_POINT, RA_POINT_LOW, RA_SKY, RA_UPF, RAISE2H, W_HIGH2, W_READY, W_SKY, arm, bent, fp_flip, fp_mirror_x, fpl, fpr, fpw, keyp,
                             n3, nudge, off_bare, strike, use_skill)

Motion = K.Motion


def shield(d, bend, blade=(0, 1, 0), face=(0, 0, 1)):
    """방패를 든 팔 (겉면이 face 쪽) — 직업 수호기사와 같음"""
    a = arm(d, bend, list(blade))
    a["face"] = n3(face)
    return a


def ready_out(m, tick, fp=True):
    keyp(m, tick, P(W_READY, body=[6, 4, 0]))
    if fp:
        fpw(m, tick, FPW_LOW)


# ------------------------------------------------------------ 대각선 베기 (X 자): D1 = 오른쪽 위 → 왼쪽 아래 (내려 벰), D2 = 왼쪽 위 → 오른쪽 아래 (되돌려 내려 벰)

D1_WIND = P(body=[-6, 36, 6], ra=arm([0.5, 0.75, -0.42], 55, [0.3, 0.8, -0.52]), la=arm([-0.35, -0.45, 0.6], 40), rl=[14, 0, 2], ll=[-24, 0, -2], rk=20, lk=28,
            root=[-0.1, 0.1])
D1_HIT = P(body=[18, -12, 0], ra=arm([-0.08, -0.12, 1], 6, [-0.62, -0.48, 0.62]), la=arm([-0.5, -0.6, -0.1], 35), rl=[18, 0, 2], ll=[-30, 0, -2], rk=18, lk=34,
           root=[-0.14, 0.3])
D1_END = P(body=[16, -40, 0], ra=arm([-0.62, -0.55, 0.55], 16, [-0.55, -0.8, -0.2]), la=arm([-0.45, -0.7, -0.2], 30), rl=[18, 0, 2], ll=[-30, 0, -2], rk=18,
           lk=34, root=[-0.14, 0.26])
D2_WIND = P(body=[0, -38, -4], ra=arm([-0.5, 0.62, 0.6], 80, [-0.42, 0.82, -0.38]), la=arm([-0.45, -0.6, 0.2], 35), rl=[14, 0, 2], ll=[-26, 0, -2], rk=20,
            lk=30, root=[-0.12, 0.22])
D2_HIT = P(body=[18, 14, 0], ra=arm([0.3, -0.14, 0.94], 6, [0.62, -0.48, 0.62]), la=arm([-0.5, -0.6, 0.0], 35), rl=[18, 0, 2], ll=[-30, 0, -2], rk=18, lk=34,
           root=[-0.14, 0.3])
D2_END = P(body=[16, 40, 0], ra=arm([0.8, -0.45, 0.4], 16, [0.5, -0.82, -0.28]), la=arm([-0.5, -0.65, -0.1], 30), rl=[18, 0, 2], ll=[-30, 0, -2], rk=18, lk=34,
           root=[-0.14, 0.26])
# 1인칭 (p = 오른쪽 · 위 · 뒤, blade = 무기 끝): D1 — 화면 오른쪽 위에서 들어와 가운데를 지나 왼쪽 아래로 나감. D2 는 화면 거울
FP_D1 = [((0.3, 0.95, 0.2), (0.4, 0.78, -0.48)), ((-0.25, 0.75, 0.1), (-0.2, 0.75, 0.62)), ((-0.85, 0.25, 0.05), (-0.65, -0.45, 0.6)),
         ((-1.55, -0.3, 0.2), (-0.62, -0.72, -0.3))]
FP_D2 = [(fp_mirror_x(p), fp_flip(b)) for p, b in FP_D1]


def _edge(a, b, blade):
    """날 (움직이는 쪽 — blade 에 수직)"""
    d = [y - x for x, y in zip(a, b)]
    d[2] = -d[2]  # p 는 뒤가 + · blade 는 앞이 +
    k = sum(x * y for x, y in zip(d, blade)) / max(1e-9, sum(x * x for x in blade))
    e = [x - k * y for x, y in zip(d, blade)]
    if math.sqrt(sum(x * x for x in e)) < 1e-6:
        return None
    return n3(e)


def fp_stroke(m, keys, wind_tick, hit_tick, end_tick=None, dp=None):
    """1인칭 베기 네 열쇠 (준비 · 들어옴 · 가운데 · 나감) — 날은 움직이는 쪽"""
    if end_tick is None:
        end_tick = hit_tick + (hit_tick - wind_tick) * 0.75
    ts = (wind_tick, wind_tick + (hit_tick - wind_tick) * 0.45, hit_tick, end_tick)
    eases = (None, "in", "linear", "out")
    ps = [tuple(a + b for a, b in zip(p, dp)) if dp else p for p, _ in keys]
    for i, (t, (p, blade), e) in enumerate(zip(ts, keys, eases)):
        q = ps[i]
        nxt = ps[min(3, i + 1)] if i < 3 else ps[3]
        prv = ps[i - 1] if i > 0 else ps[0]
        edge = _edge(prv, nxt, blade) if i > 0 else _edge(ps[0], ps[1], blade)
        m.fp(t, q, None, blade, edge, e)


def diag(m, which, wind_tick, hit_tick, end_tick=None, wind=None, hit=None, end=None, fp=True, fp_dp=None):
    """대각선 베기 하나 (which = 1: 오른쪽 위 → 왼쪽 아래, 2: 왼쪽 위 → 오른쪽 아래)"""
    if end_tick is None:
        end_tick = hit_tick + (hit_tick - wind_tick) * 0.9
    w0, h0, e0 = (D1_WIND, D1_HIT, D1_END) if which == 1 else (D2_WIND, D2_HIT, D2_END)
    keyp(m, wind_tick, wind or w0)
    keyp(m, hit_tick, hit or h0, "linear")
    keyp(m, end_tick, end or e0, "out")
    m.arc(hit_tick, "ra", (0.0, 0.05, 1))
    m.head.setdefault("trail", []).append([m.t(wind_tick + (hit_tick - wind_tick) * 0.25), m.t(end_tick)])
    m.set(tilt=30)
    if fp:
        fp_stroke(m, FP_D1 if which == 1 else FP_D2, wind_tick, hit_tick, end_tick, fp_dp)
    return m


# 칼을 털어냄 (피 · 불길을 털듯 오른쪽 아래로 뿌림)
FLICK = P(body=[10, 26, 0], ra=arm([0.72, -0.5, 0.48], 8, [0.62, -0.68, 0.38]), la=arm([-0.45, -0.6, -0.1], 30), rl=[16, 0, 2], ll=[-24, 0, -2], rk=16, lk=26,
          root=[-0.1, 0.18])
FP_FLICK = dict(p=(0.35, -0.3, 0.05), blade=(0.6, -0.62, 0.48))

# ------------------------------------------------------------ 칼 — 그람 (불꽃 · 용)

# 화룡 돌진 (효과: 0 에 돌진 (순간) · 앞에 불꽃 초승달 X (0 · 2) → 6 ~ 9 지나온 길이 터짐 → 10 앞쪽 폭발)
m = Motion("gram_charge", 20, "화룡 돌진 — 낮게 내달려 앞을 X 자로 두 번 벰 (내려 베기 → 되돌려 내려 베기) → 칼을 털어 지나온 길에 불길을 일으킴", fade=(0.03, 0.1))
diag(m, 1, 0.0, 0.6, 1.3, wind=P(D1_WIND, **LEGS_LUNGE))
diag(m, 2, 1.9, 2.7, 3.6)
keyp(m, 8.2, P(D2_END, body=[14, 36, 0], root=[-0.16, 0.22]))
keyp(m, 9.6, FLICK, "in")
keyp(m, 11.5, P(FLICK, body=[8, 22, 0]))
ready_out(m, 16)
m.fp(8.2, (0.15, -0.35, 0.15), None, (0.5, -0.8, -0.2))
fpw(m, 9.6, FP_FLICK, "in")
fpw(m, 11.5, FP_FLICK)
use_skill(m, "gram_charge")

# 용익 강하 (효과: 0 에 불꽃 날개를 펴며 떠오름 (rise) → 9 꼭대기 · 18 까지 머묾) → 18 내려찍기 (:dive — 보냄, 20 에 땅)
CROUCH = P(body=[24, 0, 0], ra=arm([0.3, -0.4, -0.8], 40, [0, 0, 1]), la=arm([-0.3, -0.4, -0.8], 40), rl=[-30, 0, 0], ll=[-30, 0, 0], rk=70, lk=70,
           root=[-0.3, 0])
LAUNCH = P(body=[-14, 0, 0], head=[-22, 0], ra=arm([0.45, -0.6, -0.55], 10, [0.2, -0.3, -0.9]), la=arm([-0.45, -0.6, -0.55], 10), rl=[10, 0, 6], ll=[-6, 0, -6],
           rk=10, lk=20, root=[0.1, 0])
HOVER = P(RAISE2H, body=[-10, 0, 0], head=[-14, 0], rl=[-26, 0, 6], ll=[-44, 0, -6], rk=64, lk=52, root=[0.0, 0])  # 칼을 두 손으로 하늘로 · 무릎을 당김
m = Motion("gram_descent", 20, "용익 강하 — 웅크렸다가 날개를 펴며 솟구쳐 두 손으로 칼을 하늘로 들고 떠 있음", fade=(0.03, 0.05))
off_bare(m)
keyp(m, 0, CROUCH)
keyp(m, 1.5, LAUNCH, "out3")
keyp(m, 5, HOVER, "out3")
for i, t in enumerate((9, 13, 17)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(HOVER, body=[-10 - 2 * (i % 2), 0, s * 1.5], rk=64 + 4 * s, lk=52 - 4 * s))
fpw(m, 0, FPW_LOW)
fpw(m, 1.5, FPW_LOW, "out3", dp=(0.1, -0.2, 0.1))
fpw(m, 5, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 17, FPW_UP, dp=(0.12, -0.06, 0.04))
fpl(m, 1.5, FP_LOW)
fpl(m, 5, FPL_RAISE2H, "out3")
fpl(m, 17, nudge(FPL_RAISE2H, (0.02, 0.02, 0)))
use_skill(m, "gram_descent")
PLUNGE = P(body=[46, 0, 0], ra=arm([0.05, -0.55, 0.84], 8, [0, -0.95, 0.3]), la=arm([-0.05, -0.55, 0.84], 12), rl=[-50, 0, 4], ll=[10, 0, -4], rk=60, lk=30,
           root=[0.0, 0.2])
PLANTED = P(LEGS_KNEEL, body=[36, 0, 0], head=[16, 0], ra=arm([0.05, -0.7, 0.7], 20, [0, -1, 0.15]), la=arm([-0.05, -0.7, 0.7], 24))  # 무릎 · 칼을 땅에 꽂음
m = Motion("gram_descent_dive", 18, "용익 강하 — 칼을 머리 뒤로 젖혔다가 몸을 숙여 칼끝을 아래로 내리꽂으며 떨어짐 → 무릎을 꿇고 땅에 꽂음 → 불꽃 고리에 칼을 뽑아 털어냄",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 0, HOVER)
keyp(m, 0.8, P(W_HIGH2, body=[-16, 0, 0], head=[-10, 0], rl=[-26, 0, 6], ll=[-44, 0, -6], rk=60, lk=50))
keyp(m, 1.8, PLUNGE, "in")
keyp(m, 2.3, PLANTED, "out3")
keyp(m, 10, P(PLANTED, body=[34, 0, 0], head=[12, 0]))
keyp(m, 11.4, P(body=[14, 16, 0], ra=arm([0.6, -0.3, 0.7], 20, [0.7, 0.2, 0.7]), la=arm([-0.4, -0.6, -0.1], 30), **LEGS_FIGHT))
keyp(m, 12.6, P(FLICK, body=[8, 34, 0]), "out")
ready_out(m, 16, fp=False)
m.head.setdefault("trail", []).append([m.t(0.9), m.t(2.4)])
m.head["trail"].append([m.t(11.4), m.t(13)])
m.arc(1.8, "ra", (0, 0.2, 1))
fpw(m, 0, FPW_UP, dp=(0.1, -0.1, 0))
fpw(m, 0.8, FPW_SKY)
fpw(m, 1.8, FPW_DOWN, "in", dp=(0, -0.15, -0.1))
fpw(m, 2.3, FPW_DOWN, "out3")
fpw(m, 10, FPW_DOWN)
fpw(m, 12.6, FP_FLICK, "out")
fpw(m, 16, FPW_LOW)
fpl(m, 0, FPL_RAISE2H)
fpl(m, 0.8, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 1.8, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 10, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 12, FP_LOW)
use_skill(m, "gram_descent:dive")

# ------------------------------------------------------------ 단검 — 솔라스 (금빛 빛)

# 섬광 난무 (효과: 0 · 3 · 6 · 9 에 적 둘레로 순간이동하며 벰 (±38°) → 12 처음 자리로 돌아옴 → 22 검을 거두면 자국이 한꺼번에 터짐)
LOW_LUNGE = dict(rl=[24, 0, 4], ll=[-38, 0, -2], rk=30, lk=48, root=[-0.24, 0.3])
m = Motion("solas_rush", 26, "섬광 난무 — 낮게 파고들며 엇갈려 네 번 벰 (빛이 되어 옮겨 다님) → 돌아와 바로 서서 단검을 허리로 천천히 거두고 딸깍 (자국이 터짐)",
           fade=(0.02, 0.1))
for i, t0 in enumerate((0.0, 3.0, 6.0, 9.0)):
    which = 1 if i % 2 == 0 else 2
    w0 = D1_WIND if which == 1 else D2_WIND
    diag(m, which, t0, t0 + 0.6, t0 + 1.4, wind=P(w0, **LOW_LUNGE), hit=P(D1_HIT if which == 1 else D2_HIT, **LOW_LUNGE),
         end=P(D1_END if which == 1 else D2_END, **LOW_LUNGE))
SHEATHE = P(body=[4, -12, 0], head=[6, 0], ra=arm([-0.35, -0.8, 0.45], 70, [0.05, -0.95, -0.25]), la=bent([-0.3, -0.85, 0.25], 60, (0.5, 0, 1)), rl=[2, 0, 4],
            ll=[-2, 0, -4], rk=4, lk=4)  # 바로 서서 단검을 왼 허리로 (거둠)
keyp(m, 12.6, P(SHEATHE, ra=arm([0.1, -0.35, 0.9], 70, [0.1, 0.4, 0.9])), "out3")
keyp(m, 20.8, SHEATHE)
keyp(m, 22, P(SHEATHE, body=[0, -6, 0], head=[-6, 0], ra=arm([-0.3, -0.9, 0.3], 55, [0, -1, 0])), "in")
ready_out(m, 25)
fpw(m, 12.6, dict(p=(-0.25, -0.1, 0.0), blade=(0.05, 0.5, 0.86)), "out3")
fpw(m, 20.8, dict(p=(-0.45, -0.45, 0.05), blade=(0.1, -0.9, 0.3)))
fpw(m, 22, dict(p=(-0.55, -0.75, 0.1), blade=(0.0, -1, 0.1)), "in")
use_skill(m, "solas_rush")

# 광휘의 검진 (효과: 0 ~ 8 머리 뒤로 빛의 검 여덟이 차례로 솟음 → 12 + 2i 에 하나씩 날아가 꽂힘). 빛기둥 :pillar · X 참격 :cross 는 보냄 (2틱 전)
m = Motion("solas_halo", 28, "광휘의 검진 — 단검과 왼손을 하늘로 들어 빛의 검을 부름 → 단검 끝으로 앞을 겨눠 하나씩 튕겨 날려 보냄", fade=(0.04, 0.08))
off_bare(m)
keyp(m, 3, CALL, "out3")
keyp(m, 9.5, P(CALL, head=[-30, 0]))
keyp(m, 11.4, P(body=[6, -12, 0], ra=RA_POINT, la=L(A_HIP), **LEGS_FIGHT), "out3")
for t in (13.8, 17.8, 21.8):
    keyp(m, t, P(body=[4, -10, 0], ra=arm([0.08, 0.3, 0.95], 35, [0, 0.8, 0.6]), la=L(A_HIP), **LEGS_FIGHT))
    keyp(m, t + 1.3, P(body=[8, -13, 0], ra=RA_POINT, la=L(A_HIP), **LEGS_FIGHT), "out3")
keyp(m, 26, P(body=[6, -12, 0], ra=RA_POINT, la=L(A_HIP), **LEGS_FIGHT))
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 9.5, FPW_SKY)
fpw(m, 11.4, FPW_FWD, "out3")
for t in (13.8, 17.8, 21.8):
    fpw(m, t, dict(p=(-0.35, 0.45, 0.0), blade=(0, 0.8, 0.5)))
    fpw(m, t + 1.3, FPW_FWD, "out3")
fpw(m, 26, FPW_FWD)
fpl(m, 3, FP_RAISE, "out3")
fpl(m, 9.5, FP_RAISE)
fpl(m, 11.4, FP_LOW, "out3")
use_skill(m, "solas_halo")
m = Motion("solas_halo_pillar", 12, "광휘의 검진 — 왼손을 앞으로 펴 꽂힌 검을 겨눴다가 움켜쥐며 끌어내림 (빛기둥)", fade=(0.04, 0.12))
off_bare(m)
keyp(m, 0.5, P(body=[4, -8, 0], ra=RA_POINT, la=L(A_PALM), **LEGS_FIGHT), "out3")
keyp(m, 2, P(body=[10, -6, 0], head=[4, 0], ra=RA_POINT, la=L(bent([0.3, -0.45, 0.35], 110, (0, 1, 0.5))), **LEGS_FIGHT), "in")
ready_out(m, 9)
fpw(m, 0.5, FPW_FWD, "out3")
fpw(m, 2, FPW_FWD)
fpl(m, 0.5, FP_PALM, "out3")
fpl(m, 2, nudge(FP_CHAMBER, (-0.1, 0.1, -0.1)), "in")
fpl(m, 9, FP_LOW)
use_skill(m, "solas_halo:pillar")
m = Motion("solas_halo_cross", 14, "광휘의 검진 끝 — 단검으로 허공을 X 자로 그어 빛의 X 참격을 일으킴", fade=(0.02, 0.12))
diag(m, 1, 0.2, 2.0, 2.6)
diag(m, 2, 2.6, 3.4, 4.2)
keyp(m, 8, P(D2_END, body=[12, 34, 0]))
ready_out(m, 12)
use_skill(m, "solas_halo:cross")

# ------------------------------------------------------------ 도끼 — 이그드라실 (초록 · 금빛 잎)

# 세계수의 씨앗 (효과: 0 에 씨앗이 손에서 날아감 (포물선) → 8 에 떨어진 곳 가시덤불 → 32 묶인 적에서 꽃이 핌 (:bloom — 보냄, 2틱 전))
m = Motion("ygg_seed", 16, "세계수의 씨앗 — 왼손으로 씨앗을 앞 높이 던지고 도끼를 들어 가시덤불이 솟게 함", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 0, P(body=[2, 24, 0], ra=RA_LOW, la=L(A_THROW_WIND), rl=[10, 0, 2], ll=[-16, 0, -2], rk=14, lk=18))
keyp(m, 0.9, P(body=[10, -18, 0], ra=RA_LOW, la=L(A_THROW_REL), rl=[18, 0, 2], ll=[-28, 0, -2], rk=14, lk=26, root=[-0.06, 0.2]), "out3")
keyp(m, 3.2, P(body=[8, -12, 0], ra=RA_LOW, la=L(A_THROW_FOLLOW), **LEGS_FIGHT))
keyp(m, 7, P(body=[4, -6, 0], ra=RA_UPF, la=L(A_HIP), **LEGS_FIGHT), "out3")
keyp(m, 8.2, P(body=[10, -6, 0], ra=arm([0.15, 0.75, 0.6], 15, [0, 0.9, 0.4]), la=L(A_HIP), **LEGS_FIGHT), "in")
ready_out(m, 13)
fpw(m, 0, FPW_LOW)
fpw(m, 7, FPW_UP, "out3", dp=(0, -0.1, 0))
fpw(m, 8.2, FPW_UP, "in", dp=(0, 0.05, -0.1))
fpl(m, 0, nudge(FP_RAISE, (0.05, -0.05, 0.2), (10, 0, 0)))
fpl(m, 0.9, FP_PALM_UP, "out3")
fpl(m, 3.2, FP_LOW)
use_skill(m, "ygg_seed")
m = Motion("ygg_seed_bloom", 12, "세계수의 씨앗 — 왼손을 펴 위로 피워 올림 (꽃이 핌)", fade=(0.04, 0.12))
off_bare(m)
keyp(m, 0.5, P(body=[4, -6, 0], ra=RA_LOW, la=L(A_PALM), **LEGS_FIGHT), "out3")
keyp(m, 2.2, P(body=[-2, -6, 0], head=[-8, 0], ra=RA_LOW, la=L(A_RAISE_FWD), **LEGS_FIGHT), "out3")
ready_out(m, 10)
fpl(m, 0.5, FP_PALM, "out3")
fpl(m, 2.2, FP_PALM_UP, "out3")
fpl(m, 9, FP_LOW)
use_skill(m, "ygg_seed:bloom")

# 세계수 강림 (효과: 1 ~ 9 발밑에서 뿌리가 땅을 타고 달려감 → 20 세계수가 솟음 (가시 고리 20 · 21 · 22) → 34 · 48 금빛 잎)
ROOTED = P(LEGS_KNEEL, body=[26, 0, 0], head=[10, 0], ra=arm([0.05, -0.65, 0.75], 18, [0, -0.95, 0.3]), la=arm([-0.05, -0.6, 0.78], 20))  # 무릎 · 도끼를 땅에 박음
m = Motion("ygg_tree", 56, "세계수 강림 — 도끼를 두 손으로 땅에 박고 무릎 꿇어 뿌리를 보냄 → 일어서며 두 팔을 하늘로 들어 세계수를 부름 (잎이 쏟아질 때 한 번씩 더 뻗음)",
           fade=(0.03, 0.12))
off_bare(m)
keyp(m, 0, P(W_HIGH2, head=[-10, 0]))
strike(m, "chop", 0.2, 1.2)
keyp(m, 3, ROOTED, "out3")
keyp(m, 16, P(ROOTED, body=[24, 0, 0]))
keyp(m, 19.2, P(CALL, ra=RA_SKY), "out3")
for t in (32.6, 46.6):
    keyp(m, t, P(CALL, ra=RA_SKY, head=[-30, 0]))
    keyp(m, t + 1.4, P(CALL, ra=RA_SKY, body=[-18, 0, 0], head=[-36, 0], root=[0.03, 0]), "out3")
keyp(m, 50, P(CALL, ra=RA_SKY))
ready_out(m, 54, fp=False)
fpw(m, 3, FPW_DOWN, "out3")
fpw(m, 16, FPW_DOWN)
fpw(m, 19.2, FPW_SKY, "out3")
fpw(m, 50, FPW_SKY)
fpw(m, 54, FPW_LOW)
fpl(m, 0, FPL_RAISE2H)
fpl(m, 1.2, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 16, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 19.2, FP_RAISE, "out3")
fpl(m, 50, FP_RAISE)
fpl(m, 54, FP_LOW)
use_skill(m, "ygg_tree")

# ------------------------------------------------------------ 방패 — 스발린 (서리 · 해)

SH_CHARGE = P(body=[26, -10, 0], ra=shield([0.05, -0.1, 1], 70), la=arm([-0.35, -0.5, -0.3], 40), rl=[-40, 0, 0], ll=[30, 0, 0], rk=50, lk=30,
              root=[-0.15, 0.2])  # 방패를 앞에 세우고 숙여 내달림
FP_SH = dict(p=(-0.3, 0.1, -0.35))

# 서리 돌진 (효과: 한 틱에 한 칸씩 돌진 (방패 앞에 적을 밀고 감) → 멈추면 :freeze — 보냄)
m = Motion("svalinn_charge", 12, "서리 돌진 — 방패를 앞에 세우고 몸을 숙여 내달림", fade=(0.03, 0.08))
keyp(m, 0.5, SH_CHARGE, "out3")
keyp(m, 6, P(SH_CHARGE, body=[28, -8, 0]))
keyp(m, 11, SH_CHARGE)
fpw(m, 0.5, FP_SH, "out3")
fpw(m, 11, FP_SH)
use_skill(m, "svalinn_charge")
# 멈춤 (효과: 0 에 얼음 핵이 번쩍 · 서리 숨결 → 8 방패가 흩어짐 → 20 얼음이 깨짐)
m = Motion("svalinn_charge_freeze", 24, "서리 돌진 끝 — 방패로 밀쳐 세운 채 서리를 뿜고 → 방패를 당겼다가 앞으로 내질러 얼음을 깨뜨림", fade=(0.02, 0.1))
keyp(m, 0.6, P(body=[18, -8, 0], ra=shield([0.05, 0.0, 1], 20), la=arm([-0.35, -0.5, -0.3], 40), **LEGS_LUNGE), "out3")
keyp(m, 16, P(body=[16, -8, 0], ra=shield([0.05, 0.0, 1], 24), la=arm([-0.35, -0.5, -0.3], 40), **LEGS_LUNGE))
keyp(m, 18, P(body=[4, 12, 0], ra=shield([0.25, -0.3, 0.5], 100), la=arm([-0.35, -0.5, -0.3], 40), **LEGS_FIGHT))
keyp(m, 19.6, P(body=[22, -12, 0], ra=shield([0.05, 0.05, 1], 4), la=arm([-0.4, -0.5, -0.4], 35), **LEGS_LUNGE), "in")
keyp(m, 23, P(W_READY, body=[8, -4, 0]))
fpw(m, 0.6, dict(p=(-0.3, 0.12, -0.4)), "out3")
fpw(m, 16, dict(p=(-0.3, 0.1, -0.38)))
fpw(m, 18, dict(p=(0.0, -0.02, 0.15)))
fpw(m, 19.6, dict(p=(-0.32, 0.12, -0.5)), "in")
fpw(m, 23, FPW_LOW)
use_skill(m, "svalinn_charge:freeze")

# 일식 (효과: 0 에 방패를 땅에 꽂음 (손을 떠남 — thrown) · 돔이 솟음 (0 ~ 7) · 해 앞의 일식 → 80 접힘 :fold — 보냄 → 90 해가 드러나며 폭발)
m = Motion("svalinn_dome", 84, "일식 — 방패를 앞 땅에 내리꽂고 무릎 꿇어 두 손을 모음 → 일어서 두 팔을 들어 해를 가리는 일식을 붙듦", fade=(0.03, 0.05))
m.set(hands={"main": "thrown"})
keyp(m, 0, P(body=[-6, 6, 0], ra=shield([0.15, 0.4, 0.8], 70), la=arm([-0.3, -0.5, 0.4], 40), **LEGS_FIGHT))
keyp(m, 1.2, P(body=[28, 0, 0], ra=shield([0.05, -0.75, 0.66], 20, (0, 0.85, 0.5)), la=arm([-0.3, -0.6, 0.3], 40), **LEGS_LUNGE), "in")
keyp(m, 3.5, P(LEGS_KNEEL, body=[16, 0, 0], head=[18, 0], ra=A_PRAY, la=L(A_PRAY)), "out3")
keyp(m, 16, P(LEGS_KNEEL, body=[14, 0, 0], head=[14, 0], ra=A_PRAY, la=L(A_PRAY)))
keyp(m, 20, P(body=[-10, 0, 0], head=[-24, 0], ra=A_RAISE_FWD, la=L(A_RAISE_FWD), **LEGS_WIDE), "out3")
for i, t in enumerate((34, 50, 66)):
    keyp(m, t, P(body=[-12, 0, (1 if i % 2 else -1) * 1.2], head=[-28, 0], ra=A_RAISE_FWD, la=L(A_RAISE_FWD), **LEGS_WIDE))
keyp(m, 82, P(body=[-10, 0, 0], head=[-24, 0], ra=A_RAISE_FWD, la=L(A_RAISE_FWD), **LEGS_WIDE))
m.set(hide=[m.t(1.3), 1.0])
fpw(m, 0, dict(p=(-0.25, 0.25, -0.1)))
fpw(m, 1.2, dict(p=(-0.3, -0.35, -0.45)), "in")
fpr(m, 1.6, FP_LOW)
fpr(m, 3.5, K.FP_GATHER_R, "out3")
fpr(m, 16, K.FP_GATHER_R)
fpr(m, 20, FP_RAISE, "out3")
fpr(m, 82, FP_RAISE)
use_skill(m, "svalinn_dome")
m = Motion("svalinn_dome_fold", 16, "일식 끝 — 두 팔을 끌어내려 웅크렸다가 (돔이 접힘) 돌아온 방패를 하늘로 치켜들어 해를 드러냄", fade=(0.02, 0.1))
m.set(hands={"main": "thrown"}, hide=[0.0, m.t(6)])
keyp(m, 0.6, P(body=[22, 0, 0], head=[10, 0], ra=A_CROSS, la=L(A_CROSS), **LEGS_SQUAT), "out3")
keyp(m, 6, P(body=[20, 0, 0], ra=shield([0.1, -0.4, 0.8], 80), la=L(A_CROSS), **LEGS_SQUAT))
keyp(m, 9.4, P(body=[-14, 0, 0], head=[-34, 0], ra=shield([0.08, 0.98, 0.2], 6, (0, 0.2, -1), (0, 1, 0)), la=L(A_SPREAD_UP), **LEGS_WIDE), "in")
keyp(m, 14, P(body=[-10, 0, 0], head=[-28, 0], ra=shield([0.1, 0.95, 0.25], 10, (0, 0.2, -1), (0, 1, 0)), la=L(A_SPREAD_UP), **LEGS_WIDE))
fpr(m, 0.6, FP_CROSS, "out3")
fpr(m, 5.8, FP_LOW)
fpw(m, 6.2, dict(p=(-0.2, -0.3, 0.1)))
fpw(m, 9.4, FPW_SKY, "in")
fpw(m, 14, FPW_SKY)
use_skill(m, "svalinn_dome:fold")

# ------------------------------------------------------------ 활 — 아르테미스 (은빛 달)

BOW_UP = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.35, 0.15, 0.6], 120))
BOW_DRAWN = P(body=[0, -20, 0], ra=arm([0.05, 0.1, 1], 0, [0, 1, 0]), la=arm([0.2, 0.05, -0.2], 140))
BOW_LOOSE = P(body=[-4, -16, 0], ra=arm([0.05, 0.16, 1], 0, [0, 1, -0.2]), la=arm([-0.3, 0.0, -0.7], 40), root=[0, -0.05])
FP_BOW = dict(p=(-0.25, 0.08, 0.0))
FP_BOW_LOOSE = dict(p=(-0.25, 0.1, 0.1))
HEAVY_DRAW = P(body=[-4, -24, 0], head=[2, 0], ra=arm([0.05, 0.12, 1], 0, [0, 1, 0]), la=arm([0.12, 0.08, -0.45], 148), rl=[-16, 0, 8], ll=[24, 0, -8], rk=22,
               lk=30, root=[-0.08, -0.04])  # 다리를 벌려 버티고 끝까지 당김

# 은월 관통 (효과: 0 ~ 8 손에 달빛이 모임 (초승달) → 8 에 거대한 화살)
m = Motion("artemis_pierce", 16, "은월 관통 — 다리를 벌려 버티고 시위를 천천히 끝까지 당겼다가 (달빛이 모임) 놓아 거대한 화살을 쏨", fade=(0.03, 0.1))
keyp(m, 0.6, BOW_UP, "out3")
keyp(m, 6.8, HEAVY_DRAW, "linear")
keyp(m, 7.6, P(HEAVY_DRAW, body=[-4, -24, 1.2]))
keyp(m, 8.3, P(BOW_LOOSE, body=[-8, -16, 0], root=[-0.02, -0.2], rl=[-20, 0, 8], ll=[28, 0, -8], rk=18, lk=28), "out3")
keyp(m, 12, BOW_UP)
fpw(m, 0.6, FP_BOW, "out3")
fpw(m, 6.8, dict(p=(-0.27, 0.12, 0.06)), "linear")
fpw(m, 8.3, dict(p=(-0.25, 0.12, 0.2)), "out3")
fpw(m, 12, FP_BOW)
use_skill(m, "artemis_pierce")

# 만월의 사냥 (효과: 0 에 하늘의 은활 · 만월 → 16 · 26 · 36 화살비 → 48 거대한 화살을 당김 :final — 보냄 → 52 놓음 → 56 꽂힘)
BOW_SKY = P(body=[-6, -14, 0], head=[-28, 0], ra=arm([0.05, 0.75, 0.66], 2, [0, 0.7, -0.7]), la=arm([-0.3, -0.2, -0.6], 40))  # 활을 하늘로 들어 겨눔
BOW_SKY_DRAWN = P(body=[-8, -16, 0], head=[-30, 0], ra=arm([0.05, 0.75, 0.66], 2, [0, 0.7, -0.7]), la=arm([0.18, 0.35, -0.1], 145))
m = Motion("artemis_moon", 50, "만월의 사냥 — 활을 하늘로 쏘아 올려 은활과 만월을 부르고 → 활을 하늘로 든 채 화살비마다 시위를 튕김", fade=(0.03, 0.05))
strike(m, "shoot_up", 0.0, 1.6)
keyp(m, 5, BOW_SKY, "out3")
for t in (16, 26, 36):
    keyp(m, t - 1.6, P(BOW_SKY_DRAWN, la=arm([0.15, 0.3, -0.05], 140)), "in")
    keyp(m, t, P(BOW_SKY, body=[-8, -14, 0]), "out3")
keyp(m, 48, BOW_SKY)
fpw(m, 5, dict(p=(-0.25, 0.35, 0.0), blade=(0.0, 0.8, -0.6)), "out3")
fpw(m, 48, dict(p=(-0.25, 0.35, 0.0), blade=(0.0, 0.8, -0.6)))
use_skill(m, "artemis_moon")
m = Motion("artemis_moon_final", 16, "만월의 사냥 끝 — 하늘의 은활과 함께 시위를 끝까지 당겼다가 놓아 거대한 화살을 내리꽂음", fade=(0.02, 0.12))
keyp(m, 0.4, BOW_SKY)
keyp(m, 3.6, P(BOW_SKY_DRAWN, rl=[-16, 0, 8], ll=[24, 0, -8], rk=20, lk=28), "linear")
keyp(m, 4.3, P(BOW_SKY, body=[-12, -12, 0], head=[-34, 0], la=arm([-0.35, 0.1, -0.7], 40), root=[-0.02, -0.15]), "out3")
keyp(m, 9, BOW_SKY)
keyp(m, 13, BOW_UP)
fpw(m, 0.4, dict(p=(-0.25, 0.35, 0.0), blade=(0.0, 0.8, -0.6)))
fpw(m, 3.6, dict(p=(-0.27, 0.38, 0.08), blade=(0.0, 0.8, -0.6)), "linear")
fpw(m, 4.3, dict(p=(-0.25, 0.4, 0.2), blade=(0.0, 0.75, -0.66)), "out3")
fpw(m, 13, FP_BOW)
use_skill(m, "artemis_moon:final")

# ------------------------------------------------------------ 지팡이 — 오딘 (룬 · 금빛)

# 후긴과 무닌 (효과: 0 에 까마귀 둘이 날아감 → 쪼기 → 돌아오면 :return — 보냄)
m = Motion("odin_ravens", 14, "후긴과 무닌 — 두 팔을 가슴 앞에 모았다가 활짝 펼쳐 까마귀를 날려 보내고 지팡이로 적을 가리킴", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 0, P(body=[6, 0, 0], ra=arm([-0.3, -0.3, 0.9], 95, [-0.6, 0.6, 0.5]), la=L(A_CROSS), **LEGS_FIGHT))
keyp(m, 1.3, P(body=[-6, 0, 0], head=[-12, 0], ra=arm([0.85, 0.25, 0.45], 10, [0.6, 0.5, 0.6]), la=L(A_SPREAD_UP), **LEGS_WIDE), "out3")
keyp(m, 5, P(body=[6, -12, 0], ra=RA_POINT, la=L(A_HIP), **LEGS_FIGHT), "out3")
ready_out(m, 11)
fpw(m, 0, dict(p=(-0.35, 0.0, 0.05), blade=(-0.6, 0.6, 0.5)))
fpw(m, 1.3, FPW_SPREAD, "out3")
fpw(m, 5, FPW_FWD, "out3")
fpl(m, 0, FP_CROSS)
fpl(m, 1.3, FP_SPREAD_UP, "out3")
fpl(m, 5, FP_LOW)
use_skill(m, "odin_ravens")
FALCON = P(body=[-2, -6, 0], head=[-8, -10], ra=RA_LOW, la=bent([-0.75, 0.05, 0.6], 85, (0, 1, 0.1)), **LEGS_FIGHT)  # 왼 팔뚝을 옆으로 내밀어 받음
m = Motion("odin_ravens_return", 14, "후긴과 무닌 — 왼 팔뚝을 내밀어 돌아오는 까마귀를 받았다가 팔을 휘둘러 흩뿌림 (룬 폭발)", fade=(0.03, 0.12))
off_bare(m)
keyp(m, 0.8, FALCON, "out3")
keyp(m, 7.5, FALCON)
keyp(m, 9, P(body=[-4, 10, 0], head=[-10, 0], ra=arm([0.6, 0.5, 0.6], 10, [0.4, 0.8, 0.4]), la=L(A_SPREAD_UP), **LEGS_WIDE), "out3")
ready_out(m, 13)
fpl(m, 0.8, nudge(FP_PALM_UP, (0.1, -0.05, 0.05), (-10, -20, 0)), "out3")
fpl(m, 7.5, nudge(FP_PALM_UP, (0.1, -0.05, 0.05), (-10, -20, 0)))
fpl(m, 9, FP_SPREAD_UP, "out3")
fpl(m, 12, FP_LOW)
fpw(m, 9, FPW_SPREAD, "out3")
use_skill(m, "odin_ravens:return")

# 오딘의 눈 (효과: 0 ~ 10 하늘에 눈이 열림 → 10 · 20 · 30 · 40 빛줄기 물결 → 50 감김 :close — 보냄 → 56 폭발)
EYE_HOLD = P(body=[-6, -8, 0], head=[-22, 0], ra=arm([0.1, 0.62, 0.78], 6, [0.05, 0.75, 0.66]), la=L(A_RAISE_FWD), **LEGS_WIDE)  # 지팡이 · 왼손을 하늘의 눈으로
m = Motion("odin_eye", 50, "오딘의 눈 — 지팡이를 하늘로 치켜들어 눈을 열고 → 지팡이와 왼손을 눈으로 뻗어 빛줄기마다 밀어 보냄", fade=(0.03, 0.05))
off_bare(m)
keyp(m, 2.4, P(CALL, ra=RA_SKY), "out3")
keyp(m, 8.4, EYE_HOLD, "out3")
for t in (10, 20, 30, 40):
    keyp(m, t - 0.6, P(EYE_HOLD, body=[-10, -8, 0]))
    keyp(m, t + 0.6, P(EYE_HOLD, body=[2, -8, 0], ra=arm([0.08, 0.55, 0.84], 2, [0.05, 0.7, 0.72]), root=[-0.02, 0.06]), "out3")
keyp(m, 48, EYE_HOLD)
fpw(m, 2.4, FPW_SKY, "out3")
fpw(m, 8.4, dict(p=(-0.35, 0.65, -0.1), blade=(0.0, 0.75, 0.66)), "out3")
fpw(m, 48, dict(p=(-0.35, 0.65, -0.1), blade=(0.0, 0.75, 0.66)))
fpl(m, 2.4, FP_RAISE, "out3")
fpl(m, 48, FP_RAISE)
use_skill(m, "odin_eye")
m = Motion("odin_eye_close", 14, "오딘의 눈 끝 — 왼손으로 하늘의 눈을 움켜쥐어 감기고 지팡이를 내리쳐 폭발시킴", fade=(0.02, 0.12))
off_bare(m)
keyp(m, 0.6, EYE_HOLD)
keyp(m, 3, P(EYE_HOLD, la=L(A_RAISE_FIST), head=[-16, 0]), "in")
strike(m, "smite", 4.0, 6.0)
ready_out(m, 12, fp=False)
fpl(m, 0.6, FP_RAISE)
fpl(m, 3, nudge(FP_RAISE, (0.0, -0.12, -0.05), (-10, 0, 0)), "in")
fpl(m, 5, FP_LOW)
fpw(m, 12, FPW_LOW)
use_skill(m, "odin_eye:close")

# ------------------------------------------------------------ 대검 — 티르빙 (저주받은 불꽃 · 피)

# 무덤불 (효과: 0 에 칼끝으로 땅을 긁음 → 2 ~ 9 앞으로 불기둥이 차례로 솟음 → 20 한꺼번에 터짐)
DRAG_WIND = P(body=[14, 40, 0], ra=arm([0.55, -0.6, -0.45], 20, [0.4, -0.8, -0.45]), la=bent([0.25, -0.55, -0.2], 60, (0, 0, 1)), rl=[16, 0, 2],
              ll=[-24, 0, -2], rk=26, lk=30, root=[-0.16, 0.05])
DRAG = P(body=[26, -10, 0], ra=arm([0.1, -0.72, 0.68], 10, [0.05, -0.75, 0.66]), la=arm([-0.05, -0.7, 0.7], 14), rl=[24, 0, 4], ll=[-38, 0, -2], rk=20, lk=44,
         root=[-0.22, 0.35])
LIFT = P(body=[-4, -6, 0], ra=arm([0.05, 0.5, 0.85], 25, [0, 0.95, 0.3]), la=arm([-0.05, 0.48, 0.86], 30), **LEGS_FIGHT)
m = Motion("tyrfing_fire", 26, "무덤불 — 대검 끝을 뒤에서 앞으로 땅을 긁으며 끌어올려 불길을 보내고 → 들어 올린 채 기다렸다가 내려찍어 불기둥을 한꺼번에 터뜨림",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 0, DRAG_WIND)
keyp(m, 1.6, DRAG, "linear")
keyp(m, 3.3, LIFT, "out3")
keyp(m, 8, P(RAISE2H, head=[-14, 0]), "out3")
keyp(m, 16, P(RAISE2H, body=[-12, 0, 0], head=[-16, 0], rk=18, lk=18))
strike(m, "slam", 17.6, 20.0)
ready_out(m, 25, fp=False)
m.head.setdefault("trail", []).append([m.t(0.2), m.t(3.3)])
fpw(m, 0, dict(p=(0.4, -0.45, 0.25), blade=(0.4, -0.8, -0.45)))
fpw(m, 1.6, dict(p=(-0.35, -0.5, -0.3), blade=(0.0, -0.75, 0.66)), "linear")
fpw(m, 3.3, FPW_UP, "out3")
fpw(m, 8, FPW_UP, "out3", dp=(0.1, -0.1, 0))
fpw(m, 16, FPW_UP, dp=(0.12, -0.06, 0.04))
fpw(m, 25, FPW_LOW)
fpl(m, 0, FP_LOW)
fpl(m, 3.3, K.FPL_GRIP2H, "out3")
fpl(m, 8, FPL_RAISE2H, "out3")
fpl(m, 17.6, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 20, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 24, FP_LOW)
use_skill(m, "tyrfing_fire")

# 망자의 각성 (효과: 0 체력 대가 · 둘레 5칸에 망령 열둘이 솟음 (0 ~ 16) → 18 + 2i 차례로 벰 → 50 함께 벰 → 62 마지막 일격)
PLANT_KNEEL = P(LEGS_KNEEL, body=[10, 0, 0], head=[22, 0], ra=arm([0.08, -0.4, 0.9], 60, [0, -1, 0.05]), la=bent([-0.08, -0.4, 0.9], 64, (0.3, 1, 0)))
m = Motion("tyrfing_waking", 70, "망자의 각성 — 대검을 앞 땅에 꽂고 무릎 꿇어 망령을 깨움 → 일어나 대검을 하늘로 들어 이끌고 → 앞을 겨눠 함께 베게 함 → 내려찍어 마지막 일격",
           fade=(0.03, 0.1))
off_bare(m)
keyp(m, 0, P(RAISE2H, head=[-10, 0]))
keyp(m, 1.2, P(body=[24, 0, 0], ra=arm([0.06, -0.55, 0.83], 20, [0, -0.95, 0.3]), la=arm([-0.06, -0.55, 0.83], 24), **LEGS_LUNGE), "in")
keyp(m, 3.5, PLANT_KNEEL, "out3")
keyp(m, 14, P(PLANT_KNEEL, head=[26, 0]))
keyp(m, 17.6, P(RAISE2H, head=[-26, 0], **LEGS_WIDE), "out3")
for i, t in enumerate((26, 34, 42)):
    keyp(m, t, P(RAISE2H, head=[-26, 0], body=[-10, 0, (1 if i % 2 else -1) * 1.5], **LEGS_WIDE))
keyp(m, 48.4, P(RAISE2H, head=[-20, 0], **LEGS_WIDE))
keyp(m, 50, P(body=[14, -8, 0], ra=RA_POINT, la=bent([0.05, -0.3, 0.95], 60, (-0.3, 1, 0)), **LEGS_LUNGE), "in")
keyp(m, 56, P(body=[12, -8, 0], ra=RA_POINT, la=bent([0.05, -0.3, 0.95], 60, (-0.3, 1, 0)), **LEGS_LUNGE))
strike(m, "slam", 59.4, 62.0)
ready_out(m, 68, fp=False)
fpw(m, 1.2, FPW_DOWN, "in")
fpw(m, 3.5, FPW_DOWN, "out3", dp=(0, -0.1, 0))
fpw(m, 14, FPW_DOWN, dp=(0, -0.12, 0))
fpw(m, 17.6, FPW_SKY, "out3")
fpw(m, 48.4, FPW_SKY)
fpw(m, 50, FPW_FWD, "in")
fpw(m, 56, FPW_FWD)
fpw(m, 68, FPW_LOW)
fpl(m, 0, FPL_RAISE2H)
fpl(m, 1.2, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 14, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 17.6, FPL_RAISE2H, "out3")
fpl(m, 48.4, FPL_RAISE2H)
fpl(m, 50, K.FPL_GRIP2H, "in")
fpl(m, 59.4, nudge(FPL_RAISE2H, (0.05, 0.1, 0.0), (8, 0, 0)))
fpl(m, 62, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 67, FP_LOW)
use_skill(m, "tyrfing_waking")
