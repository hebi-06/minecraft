"""0.46.0 룬 동작 — 던지고 · 부리고 · 끌어당기는 룬 (암살자 · 사냥꾼 · 화산 · 투사) 과 한정 초월 (세계수 · 심연 · 빙하 — 2026-11-11 만상귀일 · 칠성 · 천공의 눈을 뺌).
던지는 것은 손에 소품을 쥐었다가 (rpg:prop_* — tools/prop_art.py) 놓는 순간 사라짐 (효과가 그 자리에서 날아가는 것을 그림):
  prop — 주 손 무기 대신 소품, 던진 뒤는 빈손 (1인칭 fp 열쇠: 소품 → 빈 팔) · swap — 소품 구간 밖은 무기 그대로 (연막탄을 던지고 다시 단검)
  왼손 prop — 왼손에 소품 (holdOff), 던진 뒤 왼손은 빈손 (fpo)."""
import rune_motion_kit as K
from rune_motion_kit import (A_CHAMBER, A_CHEST, A_DOWN_FWD, A_GROUND, A_GUARD, A_HIP, A_OVER, A_PALM, A_PALM_LOW, A_POINT, A_PULL, A_RAISE,
                             A_RAISE_FIST, A_RAISE_FWD, A_THROW_FOLLOW, A_THROW_REL, A_UNDER_WIND, CALL, FP_CHAMBER, FP_CROSS, FP_DOWN, FP_GATHER_L,
                             FP_GROUND, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PALM, FP_PALM_UP, FP_RAISE, FP_SPREAD_UP, FPW_DOWN, FPW_FWD, FPW_LOW,
                             FPW_SKY, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_LUNGE, LEGS_SQUAT, LEGS_STOMP, LEGS_STOMP_UP, LEGS_WIDE, P, RA_POINT,
                             RA_POINT_LOW, RA_READY, RA_SKY, RA_SPREAD_UP, SPIN_FWD_R, W_HIGH2, W_READY, arm, bent, fists, fpl, fpr, fpw, graft, keyp,
                             nudge, off_bare, spin_right, strike, use)

Motion = K.Motion


def prop_throw_fp(m, wind, rel, after=None):
    """1인칭 주 손 소품: wind 틱에 머리 뒤로 젖혀 들고 rel 틱에 앞으로 내던짐 (손 자리에서 돌림) → 그 뒤 빈 팔 (after = [(틱, 꼴, 빠르기) …])"""
    m.fp(wind, (0.05, 0.35, 0.25), (20, 0, 0))
    m.fp(rel, (-0.1, 0.2, -0.45), (-15, 0, 0), ease="out3")
    for tick, pre, ease in after or []:
        fpr(m, tick, pre, ease)


# ------------------------------------------------------------ 암살자

# 배후 일격: 대상 뒤에 나타나 (틱 0) 낮게 숙여 곧바로 찌름 (1.4)
m = Motion("backstab", 12, "배후 일격 — 대상 뒤에 나타나 낮게 숙여 곧바로 찌름", fade=(0.02, 0.14))
strike(m, "thrust", 0.2, 1.4)
use(m, "backstab")

# 독 표창: 왼손에 쥔 표창을 오른 어깨 쪽으로 당겼다가 (0.25) 앞으로 뿌려 던짐 (1.3 — 눈앞에서 부채꼴로 셋)
m = Motion("poison_stars", 12, "독 표창 — 왼손에 쥔 표창을 오른 어깨 쪽에서 앞으로 뿌려 부채꼴로 던짐", fade=(0.03, 0.14))
m.set(hands={"main": "weapon", "off": "prop"}, holdOff="rpg:prop_shuriken")
m.set(holdOffAt=[0.0, m.t(1.3)])
keyp(m, 0.25, P(body=[2, 28, 0], ra=RA_READY, la=L(bent([-0.6, 0.15, 0.4], 100, (0.2, 0.3, 1))), **LEGS_FIGHT))
keyp(m, 1.3, P(body=[10, -26, 0], ra=RA_READY, la=L(arm([0.55, 0.08, 0.83], 4)), **LEGS_LUNGE), "out3")
keyp(m, 6, P(body=[8, -22, 0], ra=RA_READY, la=L(arm([0.6, -0.05, 0.8], 8)), **LEGS_LUNGE))
fpw(m, 0.25, FPW_LOW)
fpw(m, 6, FPW_LOW)
m.fpo(0.25, (0.6, 0.2, 0.15), (0, 0, 30))  # 표창을 쥔 왼손이 오른 어깨 쪽 (화면 좌표)
m.fpo(1.25, (0.1, 0.12, -0.35), (0, 0, -20), "out3")  # 앞으로 뿌림
fpl(m, 1.35, ((0.3, 0.2, -0.28), (32, -28, 0)))  # 던진 왼손 (빈손)
fpl(m, 6, ((0.3, 0.1, -0.22), (22, -28, 0)))
use(m, "poisonStars")

# 연막: 연막탄을 귀 옆으로 들었다가 (0.2) 발 앞 땅에 내던짐 (1.0 — 3틱에 터짐) → 다시 단검을 쥐고 연기 속에 낮게 웅크려 숨음 (4.5 ~ 14)
# (손: swap — 연막탄 구간 밖은 무기 그대로)
m = Motion("smoke_bomb", 20, "연막 — 연막탄을 발 앞 땅에 내던져 터뜨리고 다시 단검을 쥐고 연기 속에 낮게 웅크려 숨음", fade=(0.03, 0.14))
m.set(hands={"main": "swap"}, hold="rpg:prop_smoke_bomb")
m.set(holdAt=[0.0, m.t(1.05)])
keyp(m, 0.2, P(body=[-2, 18, 0], ra=bent([0.45, 0.4, -0.3], 100, (0, 1, 0.3)), la=L(A_GUARD), **LEGS_FIGHT))
keyp(m, 1.1, P(body=[22, -10, 0], ra=arm([0.05, -0.45, 0.9], 6), la=L(A_GUARD), **LEGS_LUNGE), "out3")
HIDE = P(body=[30, 0, 0], head=[-4, 0], ra=arm([0.3, -0.5, 0.6], 75, [0.1, 0.5, 0.85]), la=L(bent([0.3, -0.5, 0.6], 90, (-0.5, 0.8, 0.3))), **LEGS_SQUAT)
keyp(m, 4.5, HIDE)
keyp(m, 14, P(HIDE, body=[28, 0, 0]))
m.fp(0.2, (0.05, 0.3, 0.2), (15, 0, 0))  # 연막탄 (손 자리에서 돌림)
m.fp(1.0, (-0.05, -0.15, -0.35), (-25, 0, 0), ease="out3")  # 발 앞으로 내던짐
fpw(m, 1.15, FPW_LOW, dp=(0, -0.45, 0.1))  # 다시 쥔 단검 (화면 아래에서)
fpw(m, 4.5, FPW_LOW, "out", dp=(-0.1, 0.1, 0))
fpw(m, 14, FPW_LOW, dp=(-0.1, 0.1, 0))
use(m, "smokeBomb")

# ------------------------------------------------------------ 사냥꾼

# 강철 덫: 왼손에 쥔 덫을 아래에서 앞 위로 던져 올림 (0.3 → 1.3 — 포물선으로 조준한 곳에) → 턱이 닫히는 때 (8) 주먹을 쥐어 당김
m = Motion("steel_trap", 16, "강철 덫 — 왼손에 쥔 덫을 아래에서 앞으로 던져 올리고, 턱이 닫힐 때 주먹을 쥐어 당김", fade=(0.03, 0.12))
m.set(hands={"main": "weapon", "off": "prop"}, holdOff="rpg:prop_steel_trap")
m.set(holdOffAt=[0.0, m.t(1.3)])
keyp(m, 0.3, P(body=[16, 10, 0], ra=RA_READY, la=L(A_UNDER_WIND), rl=[-10, 0, 6], ll=[-16, 0, -6], rk=30, lk=26, root=[-0.1, 0]))
keyp(m, 1.3, P(body=[-4, -6, 0], ra=RA_READY, la=L(arm([0.06, 0.35, 0.94], 6)), **LEGS_FIGHT), "out3")
keyp(m, 6, P(body=[2, -4, 0], ra=RA_READY, la=L(arm([0.1, 0.1, 0.99], 10)), **LEGS_FIGHT))
keyp(m, 8.6, P(body=[6, 8, 0], ra=RA_READY, la=L(A_PULL), **LEGS_FIGHT), "out3")
keyp(m, 12, P(body=[5, 6, 0], ra=RA_READY, la=L(A_PULL), **LEGS_FIGHT))
fpw(m, 0.3, FPW_LOW)
fpw(m, 12, FPW_LOW)
m.fpo(0.3, (0.05, -0.3, 0.2), (-30, 0, 0))  # 덫을 아래로 (화면 좌표)
m.fpo(1.25, (0.15, 0.25, -0.3), (25, 0, 0), "out3")  # 앞 위로 던져 올림
fpl(m, 1.35, nudge(FP_PALM_UP, (0, 0.05, 0)))
fpl(m, 6, FP_JAB)
fpl(m, 8.6, FP_CHAMBER, "out3")
fpl(m, 12, FP_CHAMBER)
use(m, "steelTrap")

# 매의 습격: 왼팔을 앞으로 세워 매를 앉힘 (1) → 위로 힘껏 뻗어 날려 보냄 (2.8) → 대상을 가리킴 (5 ~ 14 — 매가 급강하)
m = off_bare(Motion("falcon_strike", 20, "매의 습격 — 왼팔에 앉힌 매를 위로 힘껏 날려 보내고 대상을 가리킴", fade=(0.03, 0.12)))
keyp(m, 1, P(body=[-2, 10, 0], head=[-6, 0], ra=RA_READY, la=L(bent([-0.1, 0.25, 0.95], 75, (0, 1, 0.2))), **LEGS_FIGHT))
keyp(m, 2.8, P(body=[-6, -8, 0], head=[-18, 0], ra=RA_READY, la=L(arm([-0.05, 0.75, 0.66], 4)), **LEGS_FIGHT), "out3")
POINTL = P(body=[6, -16, 0], ra=RA_READY, la=L(A_POINT), **LEGS_FIGHT)
keyp(m, 5, POINTL)
keyp(m, 14, P(POINTL, body=[5, -14, 0]))
fpw(m, 1, FPW_LOW)
fpw(m, 14, FPW_LOW)
fpl(m, 1, ((-0.05, 0.05, -0.1), (40, 40, 0)))
fpl(m, 2.8, nudge(FP_PALM_UP, (0, 0.15, -0.05), (20, 0, 0)), "out3")
fpl(m, 5, FP_JAB)
fpl(m, 14, FP_JAB)
use(m, "falconStrike")

# 사냥 그물: 두 손으로 쥔 그물을 오른쪽 뒤로 감았다가 (0.3) 앞 위로 크게 펼쳐 던짐 (1.4 — 포물선으로 조준한 곳에 덮임)
m = Motion("hunt_net", 16, "사냥 그물 — 두 손으로 쥔 그물을 오른쪽 뒤로 감았다가 앞 위로 크게 펼쳐 던짐", fade=(0.03, 0.12))
m.set(hands={"main": "prop", "off": "fist"}, hold="rpg:prop_hunt_net")
m.set(holdAt=[0.0, m.t(1.4)])
keyp(m, 0.3, P(body=[4, 40, 0], ra=bent([0.6, -0.2, -0.5], 60, (0, 0.5, 1)), la=L(bent([-0.2, -0.3, 0.3], 80, (1, 0.5, 0.2))), rl=[16, 0, 0], ll=[-20, 0, 0],
               rk=20, lk=20))
SPREADF = P(body=[12, -24, 0], ra=arm([0.35, 0.45, 0.82], 4), la=L(arm([0.35, 0.45, 0.82], 4)), **LEGS_LUNGE)
keyp(m, 1.4, SPREADF, "out3")
keyp(m, 6, P(SPREADF, ra=arm([0.3, 0.2, 0.93], 10), la=L(arm([0.3, 0.2, 0.93], 10))))
FPB_OPEN = nudge(FP_PALM_UP, (0.12, 0, 0), (0, -15, 0))
m.fp(0.3, (0.25, -0.2, 0.25), (-20, 0, 0))  # 그물 (오른쪽 아래 뒤)
m.fp(1.3, (-0.15, 0.25, -0.4), (30, 0, 0), ease="out3")  # 앞 위로 던짐
fpr(m, 1.45, FPB_OPEN)
fpr(m, 6, nudge(FPB_OPEN, (0, -0.1, 0), (-10, 0, 0)))
fpl(m, 0.3, FP_GATHER_L)
fpl(m, 1.4, FPB_OPEN, "out3")
fpl(m, 6, nudge(FPB_OPEN, (0, -0.1, 0), (-10, 0, 0)))
use(m, "huntNet")

# 늑대 무리: 왼팔을 몸 앞으로 감았다가 (0.3) 앞으로 휘둘러 무리를 보냄 (1.5 — 늑대 다섯이 달려들어 물어뜯음 0 ~ 40) → 무리가 울부짖을 때 (52) 주먹을 하늘로
m = off_bare(Motion("wolf_pack", 58, "늑대 무리 — 왼팔을 앞으로 휘둘러 늑대 무리를 보내고, 무리가 울부짖을 때 주먹을 하늘로 치켜듦", fade=(0.03, 0.08)))
keyp(m, 0.3, P(body=[0, 20, 0], ra=RA_READY, la=L(bent([-0.5, 0.0, 0.6], 70, (1, 0.3, 0.3))), **LEGS_FIGHT))
SIC = P(body=[10, -20, 0], ra=RA_READY, la=L(arm([0.45, 0.05, 0.89], 4)), **LEGS_LUNGE)
keyp(m, 1.5, SIC, "out3")
keyp(m, 6, P(SIC, body=[8, -16, 0]))
keyp(m, 12, W_READY)
keyp(m, 46, P(W_READY, body=[10, 4, 0]))
HOWL = P(body=[-12, 0, 0], head=[-28, 0], ra=RA_READY, la=L(A_RAISE_FIST), **LEGS_WIDE)
keyp(m, 51.5, HOWL, "out3")
keyp(m, 55, HOWL)
fpw(m, 0.3, FPW_LOW)
fpw(m, 55, FPW_LOW)
fpl(m, 0.3, ((-0.22, 0.0, 0.0), (15, 75, 0)))
fpl(m, 1.5, ((0.28, 0.08, -0.22), (18, -35, 0)), "out3")
fpl(m, 6, ((0.26, 0.04, -0.2), (14, -35, 0)))
fpl(m, 12, FP_GUARD)
fpl(m, 46, FP_GUARD)
fpl(m, 51.5, FP_RAISE, "out3")
fpl(m, 55, FP_RAISE)
use(m, "wolfPack")

# 야수왕 강림: 두 팔을 하늘로 들어 늑대왕을 부름 (3 ~ 17 — 하늘에서 내려옴) → 거대한 발톱이 할퀼 때마다 (20 · 30 · 40 · 50) 팔을 크게 할퀴듯 휘두름 (좌우 번갈아)
m = off_bare(Motion("beast_king", 60, "야수왕 강림 — 두 팔을 하늘로 들어 늑대왕을 부르고, 발톱이 할퀼 때마다 좌우로 크게 할퀴듯 휘두름", fade=(0.03, 0.08)))
keyp(m, 3, CALL, "out3")
keyp(m, 16, P(CALL, body=[-15, 0, 0], head=[-30, 0]))
CLAW_R_UP = P(body=[-4, 24, 0], ra=arm([0.55, 0.7, 0.3], 30, [0.3, 0.9, 0.3]), la=L(A_GUARD), **LEGS_FIGHT)
CLAW_R_DN = P(body=[16, -20, 0], ra=arm([-0.35, -0.55, 0.75], 10, [-0.4, -0.7, 0.6]), la=L(A_GUARD), **LEGS_LUNGE)
CLAW_L_UP = P(body=[-4, -24, 0], ra=RA_READY, la=L(arm([0.55, 0.7, 0.3], 30)), **LEGS_FIGHT)
CLAW_L_DN = P(body=[16, 20, 0], ra=RA_READY, la=L(arm([-0.35, -0.55, 0.75], 10)), **LEGS_LUNGE)
fpw(m, 3, FPW_SKY, "out3")
fpw(m, 16, FPW_SKY)
fpl(m, 3, FP_RAISE, "out3")
fpl(m, 16, FP_RAISE)
for i, T in enumerate((20, 30, 40, 50)):
    right = i % 2 == 0
    keyp(m, T - 1.6, CLAW_R_UP if right else CLAW_L_UP)
    keyp(m, T + 0.4, CLAW_R_DN if right else CLAW_L_DN, "in")
    if right:
        fpw(m, T - 1.6, FPW_UP, dp=(0.3, 0.1, 0.1))
        fpw(m, T + 0.4, FPW_DOWN, "in", dp=(-0.4, 0, 0))
        fpl(m, T - 1.6, FP_GUARD)
        fpl(m, T + 0.4, FP_GUARD)
    else:
        fpw(m, T - 1.6, FPW_LOW)
        fpw(m, T + 0.4, FPW_LOW)
        fpl(m, T - 1.6, nudge(FP_RAISE, (0.1, 0.05, 0), (0, -10, 0)))
        fpl(m, T + 0.4, nudge(FP_DOWN, (-0.25, 0, 0), (0, 25, 0)), "in")
keyp(m, 55, W_READY)
fpw(m, 55, FPW_LOW)
fpl(m, 55, FP_GUARD)
use(m, "beastKing")

# ------------------------------------------------------------ 화산

# 용암 강타: 두 주먹을 머리 위로 들어 (0.3) 앞 땅을 내리침 (1.3 — 용암이 갈라져 튐)
m = fists(Motion("magma_slam", 16, "용암 강타 — 두 주먹을 머리 위로 들어 앞 땅을 힘껏 내리쳐 용암을 터뜨림", fade=(0.03, 0.12)))
keyp(m, 0.3, P(body=[-14, 0, 0], ra=A_OVER, la=L(A_OVER), rl=[6, 0, 6], ll=[-6, 0, -6], rk=10, lk=10))
SMASH = P(body=[40, 0, 0], head=[16, 0], ra=arm([0.05, -0.75, 0.66], 10), la=L(arm([0.05, -0.75, 0.66], 10)), **LEGS_SQUAT)
keyp(m, 1.3, SMASH, "in")
keyp(m, 8, P(SMASH, body=[38, 0, 0]))
fpr(m, 0.3, FP_RAISE)
fpr(m, 1.3, FP_GROUND, "in")
fpr(m, 8, FP_GROUND)
fpl(m, 0.3, FP_RAISE)
fpl(m, 1.3, FP_GROUND, "in")
fpl(m, 8, FP_GROUND)
use(m, "magmaSlam")

# 화산탄: 오른손에 화산탄을 쥐고 크게 던짐 — 세 번 (0 · 10 · 20 — 포물선 · 연기 꼬리), 던질 때마다 새 화산탄이 손에
m = Motion("lava_bombs", 28, "화산탄 — 오른손에 쥔 불타는 화산탄을 세 번 크게 던짐", fade=(0.03, 0.1))
m.set(hands={"main": "prop"}, hold="rpg:prop_lava_bomb")
LOB_WIND = P(body=[-6, 34, 0], ra=bent([0.45, 0.55, -0.55], 85, (0, 1, -0.2)), la=L(A_POINT), rl=[18, 0, 0], ll=[-24, 0, 0], rk=14, lk=16)
LOB_REL = P(body=[16, -28, 0], ra=A_THROW_REL, la=L(A_HIP), **LEGS_LUNGE)
LOB_REST = P(body=[8, -10, 0], ra=A_THROW_FOLLOW, la=L(A_GUARD), **LEGS_FIGHT)
windows = []
for T, wind in ((0, 0.2), (10, 8.2), (20, 18.2)):
    rel = max(T, wind) + 0.9
    keyp(m, wind, LOB_WIND)
    keyp(m, rel, LOB_REL, "out3")
    keyp(m, rel + 3.5, LOB_REST)
    windows += [m.t(wind - 1.4) if wind > 1 else 0.0, m.t(rel + 0.05)]
    if wind > 1:
        fpr(m, wind - 1.5, FP_LOW)  # 빈손이 내려가 새 화산탄을 쥠
    prop_throw_fp(m, wind, rel, [(rel + 0.15, nudge(FP_JAB2, (0, -0.05, 0)), None), (rel + 3.5, FP_GUARD, None)])
m.set(holdAt=windows)
use(m, "lavaBombs")

# 용암 분출: 왼손을 조준한 곳 땅으로 내밀어 (1.2 — 땅이 용암빛으로 갈라짐) 분출마다 (6 · 16 · 26 · 36) 손바닥을 확 들어 올림
m = off_bare(Motion("magma_geyser", 42, "용암 분출 — 왼손을 갈라진 땅으로 내밀고, 용암 기둥이 솟을 때마다 손바닥을 확 들어 올림", fade=(0.03, 0.08)))
keyp(m, 1.2, P(body=[10, 10, 0], ra=RA_READY, la=L(A_PALM_LOW), **LEGS_WIDE), "out3")
fpl(m, 1.2, FP_DOWN, "out3")
for T in (6, 16, 26, 36):
    keyp(m, T - 1.6, P(body=[12, 10, 0], ra=RA_READY, la=L(arm([-0.05, -0.35, 0.94], 4, [0, 1, 0.2])), **LEGS_WIDE))
    keyp(m, T + 0.5, P(body=[-4, 10, 0], head=[-8, 0], ra=RA_READY, la=L(arm([-0.05, 0.45, 0.89], 4, [0, 1, 0.1])), **LEGS_WIDE), "out3")
    fpl(m, T - 1.6, nudge(FP_DOWN, (0, -0.05, 0)))
    fpl(m, T + 0.5, FP_PALM_UP, "out3")
fpw(m, 1.2, FPW_LOW)
fpw(m, 38, FPW_LOW)
use(m, "magmaGeyser")

# ------------------------------------------------------------ 투사

# 사슬 갈고리: 오른손에 쥔 갈고리를 크게 던짐 (0.2 → 1.1 — 사슬이 뻗어 나감) → 꽂히면 (3) 사슬을 두 손으로 잡아 홱 당김 (5.5)
m = Motion("chain_hook", 14, "사슬 갈고리 — 갈고리를 크게 던져 꽂고 사슬을 두 손으로 잡아 홱 당겨 끌어옴", fade=(0.03, 0.12))
m.set(hands={"main": "prop", "off": "fist"}, hold="rpg:prop_chain_hook")
m.set(holdAt=[0.0, m.t(1.1)])
keyp(m, 0.2, P(body=[-6, 36, 0], ra=bent([0.45, 0.55, -0.6], 85, (0, 1, -0.2)), la=L(A_POINT), rl=[18, 0, 0], ll=[-24, 0, 0], rk=14, lk=16))
keyp(m, 1.1, P(body=[16, -28, 0], ra=A_THROW_REL, la=L(A_HIP), **LEGS_LUNGE), "out3")
keyp(m, 3, P(body=[12, -20, 0], ra=arm([0.05, 0.0, 1], 10), la=L(arm([0.0, -0.05, 1], 15)), **LEGS_LUNGE))
PULL = P(body=[-8, 16, 0], ra=A_PULL, la=L(bent([0.2, -0.3, 0.2], 110, (0, 0.3, 1))), rl=[30, 0, 0], ll=[-10, 0, 0], rk=10, lk=30, root=[-0.06, -0.12])
keyp(m, 5.5, PULL, "out3")
keyp(m, 9, P(PULL, body=[-6, 12, 0]))
prop_throw_fp(m, 0.2, 1.0, [(1.2, FP_JAB2, None), (3, FP_JAB, None), (5.5, FP_CHAMBER, "out3"), (9, FP_CHAMBER, None)])
fpl(m, 0.2, FP_JAB)
fpl(m, 1.1, FP_LOW)
fpl(m, 3, FP_JAB)
fpl(m, 5.5, FP_CHAMBER, "out3")
fpl(m, 9, FP_CHAMBER)
use(m, "chainHook")

# 방패 돌진: 무기를 앞세워 낮게 내달림 (0.6 ~ 3.6) → 부딪히며 밀쳐냄 (5.2) → 두 손으로 머리 위로 들어 땅을 내려찍음 (12.6)
m = Motion("shield_rush", 20, "방패 돌진 — 무기를 앞세워 낮게 내달려 부딪혀 밀쳐내고, 머리 위로 들어 땅을 내려찍음", fade=(0.03, 0.1))
CHARGE = P(body=[28, -6, 0], head=[-10, 0], ra=arm([0.05, -0.1, 1], 70, [0, 0.9, 0.4]), la=L(A_GUARD), rl=[-36, 0, 0], ll=[24, 0, 0], rk=50, lk=30,
           root=[-0.16, 0.15])
keyp(m, 0.6, CHARGE, "out3")
keyp(m, 2.1, P(CHARGE, rl=[24, 0, 0], ll=[-36, 0, 0], rk=30, lk=50))
keyp(m, 3.6, CHARGE)
fpw(m, 0.6, FPW_FWD, "out3", dp=(0.2, -0.2, 0.1))
fpw(m, 3.6, FPW_FWD, dp=(0.2, -0.2, 0.1))
strike(m, "bash", 3.8, 5.2)
keyp(m, 9.2, W_HIGH2)
strike(m, "slam", 9.6, 12.6)
use(m, "shieldRush")

# 사슬 회전: 사슬에 매단 철구를 휘두르며 오른쪽으로 돎 (2 ~ 40 — 세 바퀴, 철구가 몸 둘레를 시계 방향으로) → 끝에 두 손으로 사슬을 홱 당김 (42 — 끌어모음)
m = Motion("chain_whirl", 48, "사슬 회전 — 두 손으로 사슬을 쥐고 철구를 휘두르며 오른쪽으로 돌다가 사슬을 홱 당겨 끌어모음", fade=(0.03, 0.08))
WHIRL = P(SPIN_FWD_R, la=arm([0.1, 0.0, 1], 12))  # 두 손으로 사슬을 앞으로
spin_right(m, (2, 15, 28), 40, fwd=WHIRL)
keyp(m, 42.4, P(body=[-10, 0, 0], ra=A_PULL, la=L(A_PULL), rl=[24, 0, 0], ll=[-12, 0, 0], rk=12, lk=30, root=[-0.06, -0.1], spin=-1080), "out3")
keyp(m, 45, P(body=[-8, 0, 0], ra=A_PULL, la=L(A_PULL), rl=[20, 0, 0], ll=[-10, 0, 0], rk=10, lk=24, root=[-0.04, -0.08], spin=-1080))
fpw(m, 42.4, FPW_LOW, "out3", dp=(0.1, -0.1, 0.25))
fpw(m, 45, FPW_LOW, dp=(0.1, -0.1, 0.25))
use(m, "chainWhirl")

# 투기장: 무기를 하늘로 치켜들고 왼 주먹을 가슴에 (2.5 ~ 7 — 둘레에 사슬 울타리가 섬) → 무기를 앞으로 내려 겨누며 싸울 자세 (11)
m = off_bare(Motion("arena_domain", 20, "투기장 — 무기를 하늘로 치켜들고 주먹을 가슴에 대 투기장을 연 뒤 앞을 겨누며 싸울 자세", fade=(0.04, 0.12)))
SALUTE = P(body=[-12, -4, 0], head=[-20, 0], ra=RA_SKY, la=L(A_CHEST), **LEGS_WIDE)
keyp(m, 2.5, SALUTE, "out3")
keyp(m, 7, P(SALUTE, body=[-10, -4, 0]))
ENGAGE = P(body=[8, -14, 0], ra=RA_POINT, la=L(A_GUARD), **LEGS_FIGHT)
keyp(m, 11, ENGAGE)
keyp(m, 16, P(ENGAGE, body=[6, -12, 0]))
fpw(m, 2.5, FPW_SKY, "out3")
fpw(m, 7, FPW_SKY)
fpw(m, 11, FPW_FWD)
fpw(m, 16, FPW_FWD)
fpl(m, 2.5, ((-0.18, -0.12, 0.12), (25, 70, 0)), "out3")
fpl(m, 7, ((-0.18, -0.12, 0.12), (25, 70, 0)))
fpl(m, 11, FP_GUARD)
fpl(m, 16, FP_GUARD)
use(m, "arenaDomain")

# 투신의 사슬: 두 손을 땅으로 뻗었다가 (0.6) 하늘로 끌어올림 (6 ~ 13 — 땅에서 사슬 기둥 여섯이 솟아 기욺) → 묶는 때 (16) 두 주먹을 쥐어 가슴으로 힘껏 당김 (조임)
m = off_bare(Motion("war_chains", 30, "투신의 사슬 — 땅에서 사슬 기둥을 끌어올리고, 묶을 때 두 주먹을 쥐어 힘껏 당겨 조임", fade=(0.03, 0.1)))
keyp(m, 0.6, P(body=[24, 0, 0], ra=arm([0.4, -0.8, 0.45], 10, [0.2, -0.6, 0.75]), la=L(arm([0.4, -0.8, 0.45], 10)), rl=[-14, 0, 10], ll=[-14, 0, -10],
               rk=40, lk=40, root=[-0.16, 0]))
RAISE2 = P(body=[-12, 0, 0], head=[-20, 0], ra=RA_SKY, la=L(A_RAISE_FIST), **LEGS_WIDE)
keyp(m, 6, RAISE2, "out")
keyp(m, 13, P(RAISE2, body=[-14, 0, 0]))
SQUEEZE = P(body=[14, 0, 0], head=[6, 0], ra=bent([0.4, -0.35, 0.25], 120, (0, 0.6, 1)), la=L(A_PULL), rl=[-10, 0, 10], ll=[-10, 0, -10], rk=34, lk=34,
            root=[-0.12, 0])
keyp(m, 16.5, SQUEEZE, "in")
keyp(m, 24, P(SQUEEZE, body=[12, 0, 0]))
fpw(m, 0.6, FPW_DOWN)
fpw(m, 6, FPW_SKY, "out")
fpw(m, 13, FPW_SKY)
fpw(m, 16.5, FPW_LOW, "in")
fpw(m, 24, FPW_LOW)
fpl(m, 0.6, FP_GROUND)
fpl(m, 6, FP_RAISE, "out")
fpl(m, 13, FP_RAISE)
fpl(m, 16.5, FP_CHAMBER, "in")
fpl(m, 24, FP_CHAMBER)
use(m, "warChains")

# ------------------------------------------------------------ 한정 초월 (마법)

# 세계수의 가호: 무릎을 꿇고 왼손을 땅에 대 씨앗을 심음 (2 ~ 6 — 앞 왼쪽에 세계수가 자라기 시작) → 일어서며 두 팔을 천천히 들어 올려 나무를 키움 (8 ~ 16) →
# 잎 지붕이 필 때 (18) 두 팔을 활짝 펴 가호를 내림
m = off_bare(Motion("world_tree", 32, "세계수의 가호 — 무릎을 꿇고 씨앗을 심은 뒤 일어서며 두 팔로 나무를 키우고, 잎 지붕이 필 때 두 팔을 활짝 폄", fade=(0.04, 0.1)))
SEED = P(body=[24, -10, 0], head=[18, 0], ra=arm([0.3, -0.75, 0.55], 30, [0, 0.4, 0.9]), la=L(arm([0.0, -0.92, 0.38], 4)), **LEGS_KNEEL)
keyp(m, 2, SEED)
keyp(m, 6, P(SEED, body=[26, -10, 0]))
GROW = P(body=[-4, -10, 0], head=[-14, 0], ra=arm([0.3, 0.35, 0.89], 25, [0.1, 0.9, 0.4]), la=L(arm([0.2, 0.35, 0.92], 6, [0, 1, 0.2])), **LEGS_WIDE)
keyp(m, 12, GROW)
keyp(m, 16, P(GROW, body=[-8, -10, 0], head=[-20, 0]))
BLOOM = P(body=[-12, 0, 0], head=[-24, 0], ra=RA_SPREAD_UP, la=L(arm([0.6, 0.75, 0.2], 10)), **LEGS_WIDE)
keyp(m, 18.6, BLOOM, "out3")
keyp(m, 27, P(BLOOM, body=[-10, 0, 0]))
fpw(m, 2, FPW_LOW)
fpw(m, 6, FPW_LOW)
fpw(m, 12, FPW_UP, dp=(0.25, -0.25, 0))
fpw(m, 16, FPW_UP, dp=(0.25, -0.15, 0))
fpw(m, 18.6, FPW_SKY, "out3", dp=(0.35, -0.15, 0))
fpw(m, 27, FPW_SKY, dp=(0.35, -0.15, 0))
fpl(m, 2, FP_GROUND)
fpl(m, 6, FP_GROUND)
fpl(m, 12, FP_PALM_UP)
fpl(m, 16, nudge(FP_PALM_UP, (0, 0.05, 0), (10, 0, 0)))
fpl(m, 18.6, FP_SPREAD_UP, "out3")
fpl(m, 27, FP_SPREAD_UP)
use(m, "worldTreeGrace")

# 심연의 낙인: 왼손을 조준한 곳으로 뿌려 심연을 엶 (0.9) → 사슬이 감겨 낙인이 찍힐 때 (14) 주먹을 움켜쥐어 당김
m = off_bare(Motion("abyss_brand", 22, "심연의 낙인 — 왼손을 뿌려 심연을 열고, 낙인이 찍힐 때 주먹을 움켜쥐어 당김", fade=(0.03, 0.12)))
keyp(m, 0.3, P(body=[0, 18, 0], ra=RA_READY, la=L(bent([-0.5, 0.05, 0.6], 80, (1, 0.3, 0.3))), **LEGS_FIGHT))
CAST = P(body=[8, -10, 0], ra=RA_READY, la=L(arm([-0.02, 0.0, 1], 2, [0, 1, 0.1])), **LEGS_LUNGE)
keyp(m, 1.0, CAST, "out3")
keyp(m, 12, P(CAST, la=L(arm([-0.02, 0.02, 1], 10, [0, 1, 0.1]))))
GRIP = P(body=[8, 12, 0], ra=RA_READY, la=L(A_PULL), **LEGS_FIGHT)
keyp(m, 14.4, GRIP, "out3")
keyp(m, 18, GRIP)
fpw(m, 0.3, FPW_LOW)
fpw(m, 18, FPW_LOW)
fpl(m, 0.3, ((-0.2, 0.0, 0.0), (15, 75, 0)))
fpl(m, 1.0, FP_PALM, "out3")
fpl(m, 12, nudge(FP_PALM, (0, 0, 0.05)))
fpl(m, 14.4, FP_CHAMBER, "out3")
fpl(m, 18, FP_CHAMBER)
use(m, "abyssBrand")

