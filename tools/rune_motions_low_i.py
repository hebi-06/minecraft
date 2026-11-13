"""2026-11-13 일반 · 희귀 · 고귀 룬 동작 다시 — 묶음 I (사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 체력 · 암살자 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사.
새 3D 연출 (탑 방패 · 육각 판 · 물기둥 · 그림자 분신 · 표창 · 연막탄 · 바위 · 기의 주먹 · 덫 · 운석 조각 · 갈고리와 사슬 · 가시 철구) 에 맞춘 큰 동작.
피해 틱은 그대로 (RpgRuneTimingTests) — 첫 피해가 0 ~ 2 인 룬은 0 에서 바로 감은 자세로 들어가 피해 틱에 침. 기준은 rune_motions_low (묶음 G · H) 와 같음.
도약 룬 (바위 도약 · 비각) 은 rules_more 의 motionAt 틱 뒤에 동작이 시작됨 (0 = 공중).
"""
import rune_motion_kit as K
from rune_motion_kit import (A_CHAMBER, A_CROSS, A_GROUND, A_GUARD, A_HIP, A_JAB, A_PALM, A_POINT, A_PULL, A_RAISE, A_RAISE_FIST, A_UPPER, FIGHT,
                             FP_CHAMBER, FP_CROSS, FP_DOWN, FP_GATHER_L, FP_GATHER_R, FP_GROUND, FP_GUARD, FP_JAB, FP_JAB2, FP_LOW, FP_PALM, FP_PALM_UP,
                             FP_PUSH_OUT, FP_RAISE, FP_SPREAD_UP, FP_UPPER, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, FPW_UP, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_WIDE,
                             P, W_READY, arm, bent, fists, fpb, fpl, fpr, fpw, keyp, nudge, spin_right, use)
from rune_motions_hero import BRACE, LUNGE_DEEP, breathe, strike_with

Motion = K.Motion
off_bare = K.off_bare
LA = L  # 왼팔 거울

# ------------------------------------------------------------ 체력

# 수호의 외침 (효과: 0 피해 · 머리 위 앞에 3D 탑 방패 문장이 내려와 박히듯 커짐 (1 ~ 3) · 옆 · 뒤로 3D 육각 판이 퍼짐 · 버티는 동안 작은 방패 둘이 따라옴):
# 0 에서 숨을 모아 웅크려 두 팔을 가슴 앞에 엇갈림 → 1 에 터지듯 가슴을 펴고 고개를 젖혀 두 팔을 아래 바깥으로 힘껏 펼치며 외침 (다리를 넓게 디딤) →
# 지나쳐 더 젖혔다가 돌아옴 → 외친 자세로 숨 → 거둠
m = off_bare(Motion("guard_shout", 20, "수호의 외침 — 숨을 모아 웅크려 팔을 엇갈렸다가 터지듯 가슴을 펴고 두 팔을 펼치며 크게 외침", fade=(0.01, 0.1)))
INHALE = P(body=[24, 0, 0], head=[16, 0], ra=K.RA_ACROSS, la=LA(A_CROSS), rl=[-18, 0, 8], ll=[-18, 0, -8], rk=56, lk=56, root=[-0.3, 0])
ROAR = P(body=[-18, 0, 0], head=[-30, 0], ra=arm([0.82, -0.3, 0.42], 6, [0.55, -0.2, 0.8]), la=LA(arm([0.82, -0.3, 0.42], 6)), rl=[0, 0, 20], ll=[0, 0, -20],
         rk=26, lk=26, root=[-0.16, -0.04])
keyp(m, 0.0, INHALE)
keyp(m, 1.1, ROAR, "in")
keyp(m, 2.4, P(ROAR, body=[-22, 0, 0], head=[-36, 0], ra=arm([0.86, -0.18, 0.45], 4, [0.55, -0.1, 0.8]), la=LA(arm([0.86, -0.18, 0.45], 4))), "out")
keyp(m, 3.6, ROAR)
breathe(m, 4.6, 13.0, ROAR, step=2.0, lean=1.5, knee=3)
keyp(m, 17.5, P(W_READY, body=[6, 0, 0]))
FPL_ROAR = ((0.34, -0.1, -0.1), (5, -40, 0))
fpw(m, 0.0, K.FPW_ACROSS)
fpw(m, 1.1, K.FPW_SPREAD, "in")
fpw(m, 13.0, K.FPW_SPREAD)
fpl(m, 0.0, FP_CROSS)
fpl(m, 1.1, FPL_ROAR, "in")
fpl(m, 13.0, FPL_ROAR)
fpl(m, 17.5, FP_LOW)
use(m, "guardShout")

# 생명의 샘 (효과: 0 몸 앞 양옆 땅에서 3D 물기둥 둘이 솟음 · 0 · 10 · 20 · 30 회복 — 물기둥이 번쩍 커짐 · 44 가라앉음): 0 에서 한쪽 무릎을 꿇고 왼손을 땅에 대
# 샘을 부름 → 회복마다 왼 손바닥을 땅에서 위로 퍼 올리듯 (물이 솟음) 일어서며 → 30 에 두 팔을 하늘로 활짝 → 천천히 내림
m = off_bare(Motion("life_spring", 42, "생명의 샘 — 무릎을 꿇고 손을 땅에 대 샘을 솟게 한 뒤, 회복마다 손바닥으로 물을 퍼 올리듯 일어서며 마지막에 두 팔을 활짝 듦",
                    fade=(0.01, 0.08)))
TOUCH = P(body=[30, 0, 0], head=[18, 0], ra=arm([0.35, -0.7, 0.55], 40, [0, 0.4, 0.9]), la=LA(A_GROUND), root=[-0.42, 0.05], **{k: v for k, v in LEGS_KNEEL.items()
                                                                                                                           if k != "root"})
SCOOP = P(body=[6, 0, 0], head=[-8, 0], ra=arm([0.4, -0.5, 0.6], 40, [0, 0.5, 0.85]), la=LA(arm([0.1, 0.35, 0.93], 10, [0, 1, 0.2])), rl=[-10, 0, 10],
          ll=[-24, 0, -10], rk=36, lk=44, root=[-0.2, 0.05])
BLOOM = P(body=[-14, 0, 0], head=[-28, 0], ra=K.RA_SPREAD_UP, la=LA(arm([0.6, 0.75, 0.2], 10)), **LEGS_WIDE)
keyp(m, 0.0, TOUCH)
keyp(m, 1.0, P(TOUCH, body=[34, 0, 0], root=[-0.46, 0.05]), "out")
breathe(m, 2.0, 8.0, P(TOUCH, body=[34, 0, 0], root=[-0.46, 0.05]), step=2.0, lean=1.5, knee=2)
for T, rise in ((10, 0.35), (20, 0.7)):
    low = K.lerpP(TOUCH, SCOOP, rise - 0.35)
    keyp(m, T - 1.4, P(low, la=LA(arm([0.15, -0.8, 0.55], 10))))
    keyp(m, T + 0.6, K.lerpP(TOUCH, SCOOP, rise), "out3")
    keyp(m, T + 6.0, P(K.lerpP(TOUCH, SCOOP, rise), body=[K.lerpP(TOUCH, SCOOP, rise)["body"][0] + 2, 0, 0]))
keyp(m, 28.6, P(SCOOP, body=[16, 0, 0], ra=arm([0.3, -0.6, 0.6], 40, [0, 0.5, 0.85]), la=LA(arm([0.2, -0.6, 0.7], 10))), "out")
keyp(m, 30.6, BLOOM, "out3")
keyp(m, 32.0, P(BLOOM, body=[-17, 0, 0], head=[-32, 0]), "out")
breathe(m, 33.0, 37.0, BLOOM, step=2.0, lean=1.5, knee=2)
keyp(m, 41.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 28.6, FPW_LOW)
fpw(m, 30.6, FPW_SKY, "out3", dp=(0.2, -0.3, 0))
fpw(m, 37.0, FPW_SKY, dp=(0.2, -0.3, 0))
fpw(m, 41.0, FPW_LOW)
fpl(m, 0.0, FP_GROUND)
for T in (10, 20):
    fpl(m, T - 1.4, nudge(FP_GROUND, (0, -0.05, 0)))
    fpl(m, T + 0.6, FP_PALM_UP, "out3")
    fpl(m, T + 6.0, nudge(FP_PALM_UP, (0, -0.05, 0)))
fpl(m, 28.6, nudge(FP_GROUND, (0, 0.05, 0)), "out")
fpl(m, 30.6, FP_SPREAD_UP, "out3")
fpl(m, 37.0, FP_SPREAD_UP)
fpl(m, 41.0, FP_LOW)
use(m, "lifeSpring")

# 강철 피부 (효과: 0 피해 · 0 ~ 4 3D 쇳빛 육각 판 여덟이 몸 옆 · 뒤에서 돌며 몸으로 날아듦 · 5 붙으며 번쩍 · 버티는 동안 작은 판 둘이 따라옴): 0 에서 두 팔을 몸 앞에
# 엇갈려 깊이 웅크려 버팀 (떨림) → 4 에 판이 붙는 순간 두 팔을 옆으로 벌려 알통을 쥐듯 힘을 주며 가슴을 폄 → 지나쳐 더 폈다가 → 힘준 자세로 숨 → 거둠
m = off_bare(Motion("iron_skin", 20, "강철 피부 — 팔을 엇갈려 깊이 웅크려 버티다가 쇠 판이 몸에 붙는 순간 두 팔을 벌려 힘을 주며 가슴을 폄", fade=(0.01, 0.1)))
CROUCH = P(body=[26, 0, 0], head=[14, 0], ra=K.RA_ACROSS, la=LA(A_CROSS), root=[-0.36, 0], **BRACE)
FLEXED = P(body=[-12, 0, 0], head=[-14, 0], ra=arm([0.95, 0.2, 0.1], 118, [0.1, 1, 0.1]), la=LA(K.A_FLEX), rl=[0, 0, 16], ll=[0, 0, -16], rk=24, lk=24,
           root=[-0.16, 0])
keyp(m, 0.0, CROUCH)
for i, tk in enumerate((1.0, 2.0, 3.0)):
    s = 1 if i % 2 else -1
    keyp(m, tk, P(CROUCH, body=[27 + s, s * 2, s * 1.0], root=[-0.37, 0]))
keyp(m, 4.6, FLEXED, "in")
keyp(m, 5.8, P(FLEXED, body=[-16, 0, 0], head=[-20, 0]), "out")
keyp(m, 7.0, FLEXED)
breathe(m, 8.0, 14.0, FLEXED, step=2.0, lean=1.5, knee=3)
keyp(m, 18.5, P(W_READY, body=[6, 0, 0]))
FPL_FLEX = ((0.22, 0.12, 0.0), (55, -10, 0))
fpw(m, 0.0, K.FPW_ACROSS)
fpw(m, 3.0, K.FPW_ACROSS, dp=(0, -0.05, 0))
fpw(m, 4.6, FPW_UP, "in", dp=(0.4, -0.15, 0.1))
fpw(m, 14.0, FPW_UP, dp=(0.4, -0.15, 0.1))
fpl(m, 0.0, FP_CROSS)
fpl(m, 3.0, nudge(FP_CROSS, (0, -0.05, 0)))
fpl(m, 4.6, FPL_FLEX, "in")
fpl(m, 14.0, FPL_FLEX)
fpl(m, 18.5, FP_LOW)
use(m, "ironSkin")

# ------------------------------------------------------------ 암살자

# 배후 일격 (효과: 0 대상 뒤로 옮겨 찌름 — 떠난 자리의 3D 그림자는 흩어짐 · 4 대상 옆의 3D 그림자 분신이 함께 찌름): 0 = 이미 대상 뒤 — 낮게 숙여 단검을
# 허리에 당긴 자세 → 0.7 에 몸을 던지듯 깊이 찌름 → 찌른 채 버티다 4 에 손목을 비틀어 쑤심 → 6 에 홱 뽑으며 물러섬 → 거둠
m = Motion("backstab", 14, "배후 일격 — 대상 뒤에 나타나 낮게 숙여 깊이 찌르고, 비틀어 쑤신 뒤 홱 뽑으며 물러섬", fade=(0.01, 0.12))
STAB_IN = P(body=[30, -20, 0], head=[8, 6], ra=arm([0.05, -0.05, 1], 4, [0, 0.05, 1]), la=LA(arm([0.4, -0.3, 0.6], 60)), root=[-0.36, 0.6], **LUNGE_DEEP)
strike_with(m, "thrust", 0.0, 0.7, 2.0, a=P(body=[24, 30, 0], root=[-0.3, 0.2], **LUNGE_DEEP), b=STAB_IN)
keyp(m, 2.6, STAB_IN, "out")
keyp(m, 4.0, P(STAB_IN, body=[32, -26, 4], ra=arm([0.1, -0.12, 1], 10, [0.4, -0.2, 0.9])), "in")
keyp(m, 6.2, P(body=[6, 14, 0], head=[0, -6], ra=arm([0.35, -0.3, 0.6], 70, [0.1, 0.4, 0.9]), la=LA(A_GUARD), rl=[18, 0, 6], ll=[-10, 0, -6], rk=34, lk=22,
               root=[-0.2, -0.2]), "out3")
keyp(m, 9.0, P(body=[10, 10, 0], ra=arm([0.3, -0.45, 0.6], 75, [0.05, 0.5, 0.85]), la=LA(A_GUARD), root=[-0.16, -0.2], **LEGS_FIGHT))
keyp(m, 13.0, P(W_READY, body=[6, 4, 0]))
fpw(m, 2.6, FPW_FWD, "out", dp=(0.1, -0.15, -0.15))
fpw(m, 4.0, FPW_FWD, "in", dp=(0.15, -0.18, -0.12))
fpw(m, 6.2, FPW_LOW, "out3", dp=(0, 0.1, 0.1))
fpw(m, 13.0, FPW_LOW)
use(m, "backstab")

# 독 표창 (효과: 0 3D 표창 셋이 부채꼴로 돌며 날아감 — 맞으면 꽂힘): 0 에서 표창을 쥔 왼손을 오른 어깨 위로 감고 몸을 오른쪽으로 끝까지 비틀어 웅크림 →
# 0.9 에 몸째 왼쪽으로 풀며 왼팔을 앞으로 가로질러 뿌려 던짐 (크게 내디딤) → 지나쳐 왼쪽 끝에서 멈춤 → 숨 → 거둠
m = Motion("poison_stars", 14, "독 표창 — 표창을 쥔 왼손을 오른 어깨 위로 감고 몸을 비틀었다가 몸째 풀며 앞으로 가로질러 부채꼴로 뿌림", fade=(0.01, 0.12))
m.set(hands={"main": "weapon", "off": "prop"}, holdOff="rpg:prop_shuriken")
m.set(holdOffAt=[0.0, m.t(0.95)])
STAR_WIND = P(body=[10, 50, 0], head=[0, -34], ra=arm([0.4, -0.5, 0.45], 80, [0.05, 0.55, 0.8]), la=LA(bent([-0.75, 0.35, 0.3], 95, (0.3, 0.4, 1))),
              rl=[22, 0, 6], ll=[-12, 0, -6], rk=44, lk=26, root=[-0.2, -0.1])
STAR_REL = P(body=[16, -34, 0], head=[2, 12], ra=arm([0.35, -0.55, 0.45], 80, [0.05, 0.55, 0.8]), la=LA(arm([0.7, 0.05, 0.72], 4)), root=[-0.3, 0.5],
             **LUNGE_DEEP)
keyp(m, 0.0, STAR_WIND)
keyp(m, 0.95, STAR_REL, "in")
keyp(m, 2.2, P(STAR_REL, body=[18, -46, 0], la=LA(arm([0.88, -0.05, 0.45], 4))), "out")
breathe(m, 3.2, 7.4, P(STAR_REL, body=[18, -46, 0], la=LA(arm([0.88, -0.05, 0.45], 4))), step=1.4, lean=1.0, knee=2)
keyp(m, 12.5, P(W_READY, body=[6, -4, 0]))
fpw(m, 0.0, FPW_LOW, dp=(0.15, 0, 0.1))
fpw(m, 12.5, FPW_LOW)
m.fpo(0.0, (0.62, 0.22, 0.18), (0, 0, 32))  # 표창을 쥔 왼손이 오른 어깨 쪽 (화면 좌표)
m.fpo(0.9, (0.05, 0.12, -0.38), (0, 0, -22), "in")  # 앞으로 가로질러 뿌림
fpl(m, 1.0, ((0.3, 0.18, -0.3), (30, -30, 0)))  # 던진 왼손 (빈손)
fpl(m, 2.2, ((0.42, 0.12, -0.24), (24, -42, 0)), "out")
fpl(m, 7.4, ((0.4, 0.06, -0.2), (18, -40, 0)))
fpl(m, 12.5, FP_LOW)
use(m, "poisonStars")

# 연막 (효과: 0 3D 연막탄이 손에서 발 앞까지 날아가 3 에 터짐 · 연기 속 · 10 · 20 3D 그림자 분신이 적을 벰 — 내 몸은 투명): 0 에서 연막탄을 귀 옆 위로 치켜들고
# 몸을 젖힘 → 0.9 에 발 앞 땅에 힘껏 내던짐 (숙임) → 3 에 터질 때 팔로 얼굴을 가리며 낮게 웅크려 숨음 → 단검을 쥐고 웅크린 채 숨 → 거둠
m = Motion("smoke_bomb", 22, "연막 — 연막탄을 치켜들었다가 발 앞 땅에 힘껏 내던지고, 터질 때 팔로 얼굴을 가리며 낮게 웅크려 연기 속에 숨음", fade=(0.01, 0.12))
m.set(hands={"main": "swap"}, hold="rpg:prop_smoke_bomb")
m.set(holdAt=[0.0, m.t(0.95)])
BOMB_UP = P(body=[-14, 16, 0], head=[-10, -8], ra=bent([0.4, 0.6, -0.4], 100, (0, 1, 0.3)), la=LA(arm([0.2, -0.2, 0.95], 20)), rl=[10, 0, 6], ll=[-14, 0, -6],
            rk=20, lk=16, root=[-0.04, -0.06])
BOMB_DOWN = P(body=[34, -12, 0], head=[14, 0], ra=arm([0.05, -0.7, 0.7], 6), la=LA(A_GUARD), root=[-0.32, 0.3], **LUNGE_DEEP)
HIDE = P(body=[30, 0, 0], head=[-4, 0], ra=arm([0.3, -0.5, 0.6], 75, [0.1, 0.5, 0.85]), la=LA(bent([0.3, 0.1, 0.6], 120, (-0.5, 0.8, 0.3))),
         rl=[-40, 0, 10], ll=[-40, 0, -10], rk=92, lk=92, root=[-0.4, 0])
keyp(m, 0.0, BOMB_UP)
keyp(m, 0.95, BOMB_DOWN, "in")
keyp(m, 2.0, P(BOMB_DOWN, body=[38, -14, 0]), "out")
keyp(m, 3.4, HIDE, "out3")
breathe(m, 4.6, 16.0, HIDE, step=2.4, lean=1.5, knee=2)
keyp(m, 20.5, P(W_READY, body=[8, 0, 0]))
m.fp(0.0, (0.05, 0.38, 0.25), (22, 0, 0))  # 연막탄 (귀 옆 위)
m.fp(0.9, (-0.05, -0.18, -0.38), (-28, 0, 0), ease="in")  # 발 앞으로 내던짐
fpw(m, 1.0, FPW_LOW, dp=(0, -0.45, 0.1))  # 다시 쥔 단검 (화면 아래에서)
fpw(m, 3.4, FPW_LOW, "out3", dp=(-0.1, 0.1, 0))
fpw(m, 16.0, FPW_LOW, dp=(-0.1, 0.1, 0))
fpl(m, 3.4, ((0.05, 0.25, -0.1), (60, 30, 0)), "out3")  # 얼굴을 가린 왼팔
fpl(m, 16.0, ((0.05, 0.2, -0.08), (55, 30, 0)))
fpl(m, 20.5, FP_LOW)
use(m, "smokeBomb")

# ------------------------------------------------------------ 골렘 (맨손)

# 바위 도약 (motionAt 4 — 0 = 공중 · 6 = 착지 (효과: 0 ~ 10 3D 바위 넷이 몸 둘레를 돎 · 10 착지 — 바위가 발밑에 내리꽂히고 3D 바위가 솟아 튐)):
# 0 에서 공중에서 무릎을 당기고 두 주먹을 깍지 껴 머리 위 뒤로 치켜듦 (몸을 젖힘) → 6 에 착지하며 온몸으로 두 주먹을 땅에 내리꽂고 깊이 웅크림 → 반동 →
# 박은 채 숨 → 일어서며 두 주먹을 부딪침
m = fists(Motion("stone_fist", 20, "바위 도약 — 공중에서 두 주먹을 깍지 껴 머리 위로 치켜들었다가 착지하며 땅에 내리꽂고 깊이 웅크림", fade=(0.01, 0.1)))
AIR_HIGH = P(body=[-20, 0, 0], head=[-12, 0], ra=arm([0.1, 0.85, -0.5], 40, [0, 0.1, -1]), la=LA(arm([0.1, 0.85, -0.5], 44)), rl=[-70, 0, 6], ll=[-60, 0, -6],
             rk=100, lk=96, root=[0.1, 0])
FIST_SLAM = P(body=[50, 0, 0], head=[18, 0], ra=arm([0.08, -0.7, 0.71], 8), la=LA(arm([0.08, -0.7, 0.71], 8)), rl=[-34, 0, 12], ll=[-36, 0, -12], rk=88, lk=90,
              root=[-0.52, 0.3])
keyp(m, 0.0, AIR_HIGH)
keyp(m, 3.0, P(AIR_HIGH, body=[-24, 0, 0], head=[-16, 0], rl=[-60, 0, 6], ll=[-52, 0, -6], rk=86, lk=84))
keyp(m, 4.8, P(AIR_HIGH, body=[-4, 0, 0], ra=arm([0.1, 0.75, 0.3], 30, [0, 0.95, 0.3]), la=LA(arm([0.1, 0.75, 0.3], 34)), rl=[-30, 0, 6], ll=[-26, 0, -6],
               rk=50, lk=50), "in")
keyp(m, 6.0, FIST_SLAM, "in")
keyp(m, 7.0, P(FIST_SLAM, body=[44, 0, 0], rk=80, lk=82, root=[-0.46, 0.28]), "out")
keyp(m, 8.4, FIST_SLAM)
breathe(m, 9.4, 12.6, FIST_SLAM, step=1.6, lean=1.5, knee=3)
CLASH = P(body=[2, 0, 0], head=[-4, 0], ra=bent([-0.15, -0.3, 0.9], 95, (0, 1, 0)), la=LA(bent([-0.15, -0.3, 0.9], 95, (0, 1, 0))), **LEGS_WIDE)
keyp(m, 15.2, CLASH, "out3")
keyp(m, 18.6, P(FIGHT, body=[6, -8, 0]))
fpb(m, 0.0, FP_RAISE)
fpb(m, 4.8, nudge(FP_RAISE, (0, -0.15, -0.1)), ease="in")
fpb(m, 6.0, FP_GROUND, ease="in")
fpb(m, 12.6, FP_GROUND)
fpb(m, 15.2, FP_GUARD, ease="out3")
use(m, "stoneFist")

# 돌의 수호 (효과: 0 보호막 · 3D 비취빛 육각 판 여섯이 몸 둘레에서 솟음 · 4 돌기둥 넷이 솟음 (피해) · 21 ~ 24 기둥이 바깥으로 쓰러짐 (피해)):
# 0 에서 오른 무릎을 높이 들고 두 팔을 가슴 앞에 엇갈림 → 3.4 에 발을 힘껏 굴러 웅크리며 (기둥이 솟음) 팔을 엇갈린 채 버팀 → 숨 →
# 20 에 웅크려 몸을 모았다가 → 21.6 에 두 팔을 바깥으로 힘껏 밀어내며 일어섬 (기둥이 쓰러짐) → 지나쳐 → 펼친 채 숨 → 거둠
m = fists(Motion("stone_ward", 32, "돌의 수호 — 무릎을 들어 땅을 힘껏 굴러 돌기둥을 세우고 팔을 엇갈려 버티다가 두 팔을 바깥으로 밀어 기둥을 쓰러뜨림", fade=(0.01, 0.08)))
STOMP_UP = P(body=[-8, 0, 0], head=[-6, 0], ra=A_CROSS, la=LA(A_CROSS), rl=[-86, 0, 6], rk=100, ll=[-4, 0, -4], lk=10, root=[0.04, 0])
STOMPED = P(body=[22, 0, 0], head=[8, 0], ra=A_CROSS, la=LA(A_CROSS), rl=[-20, 0, 14], ll=[-16, 0, -14], rk=50, lk=46, root=[-0.3, 0])
PUSHED = P(body=[-6, 0, 0], head=[-10, 0], ra=arm([0.95, 0.1, 0.28], 4, [0.2, 1, 0.1]), la=LA(arm([0.95, 0.1, 0.28], 4)), rl=[0, 0, 18], ll=[0, 0, -18],
           rk=20, lk=20, root=[-0.12, 0])
keyp(m, 0.0, STOMP_UP)
keyp(m, 2.4, P(STOMP_UP, rl=[-96, 0, 6], rk=110, body=[-10, 0, 0]), "out")
keyp(m, 3.6, STOMPED, "in")
keyp(m, 4.6, P(STOMPED, body=[26, 0, 0], rk=58, lk=54, root=[-0.36, 0]), "out")
keyp(m, 6.0, STOMPED)
breathe(m, 7.0, 17.0, STOMPED, step=2.5, lean=1.5, knee=3)
keyp(m, 19.6, P(STOMPED, body=[30, 0, 0], ra=bent([0.3, -0.4, 0.5], 120, (0, 1, 0.2)), la=LA(bent([0.3, -0.4, 0.5], 120, (0, 1, 0.2))), rk=66, lk=62,
                root=[-0.4, 0]), "out")
keyp(m, 21.6, PUSHED, "in")
keyp(m, 23.0, P(PUSHED, body=[-10, 0, 0], head=[-16, 0]), "out")
breathe(m, 24.4, 27.6, PUSHED, step=1.6, lean=1.0, knee=2)
keyp(m, 31.0, P(FIGHT, body=[6, -8, 0]))
fpb(m, 0.0, FP_CROSS)
fpb(m, 3.6, nudge(FP_CROSS, (0, -0.08, 0)), ease="in")
fpb(m, 17.0, FP_CROSS)
fpb(m, 19.6, nudge(FP_CROSS, (0, -0.12, 0.05)), ease="out")
fpb(m, 21.6, FP_PUSH_OUT, ease="in")
fpb(m, 27.6, FP_PUSH_OUT)
fpb(m, 31.0, FP_GUARD)
use(m, "stoneWard")

# 석화 결계 (효과: 0 결계가 펼쳐짐 (피해) — 둘레에 3D 육각 판이 차례로 솟음 · 24 적이 돌 껍데기에 갇힘 (3D 바위가 달라붙음) · 36 깨짐 (피해)):
# 0 에서 오른 손바닥을 조준한 곳으로 힘껏 내뻗음 (내디딤) → 뻗은 채 떨며 버팀 (결계를 누름) → 22.4 에 손가락을 오그려 → 24 에 주먹을 꽉 쥐며 비틀어 당김 (굳힘) →
# 쥔 채 버팀 → 34.6 에 주먹을 허리로 끌어당겼다가 → 36 에 손바닥을 펴 힘껏 내질러 깨뜨림 → 지나쳐 → 거둠
m = fists(Motion("petrify", 42, "석화 결계 — 손바닥을 내뻗어 결계를 누르다가 주먹을 꽉 쥐어 비틀어 돌로 굳히고, 손바닥을 펴 힘껏 내질러 깨뜨림", fade=(0.01, 0.08)))
REACH = P(body=[14, -22, 0], head=[4, 8], ra=A_PALM, la=LA(A_HIP), root=[-0.24, 0.3], **{k: v for k, v in LUNGE_DEEP.items()})
GRIP = P(REACH, ra=bent([-0.05, 0.08, 1], 34, (0, 1, 0)), body=[10, -14, 6], root=[-0.22, 0.2])
keyp(m, 0.0, P(REACH, body=[4, 10, 0], ra=A_CHAMBER, root=[-0.1, 0]))
keyp(m, 0.9, REACH, "in")
keyp(m, 2.0, P(REACH, body=[17, -26, 0]), "out")
for i in range(9):
    tk = 3.2 + i * 2.1
    s = 1 if i % 2 else -1
    keyp(m, tk, P(REACH, body=[15 + s * 0.8, -23 + s * 1.2, s * 0.6]))
keyp(m, 22.4, P(REACH, ra=bent([-0.05, 0.08, 1], 12, (0, 1, 0))))
keyp(m, 24.2, GRIP, "in")
breathe(m, 25.4, 32.4, GRIP, step=2.3, lean=1.0, knee=2)
keyp(m, 34.6, P(REACH, body=[2, 16, 0], ra=A_CHAMBER, root=[-0.16, 0]), "out")
keyp(m, 36.2, P(REACH, body=[20, -30, 0], ra=A_PALM, root=[-0.32, 0.55]), "in")
keyp(m, 37.4, P(REACH, body=[22, -34, 0], ra=arm([-0.08, 0.12, 1], 0, [0, 1, 0.1]), root=[-0.34, 0.6]), "out")
keyp(m, 41.0, P(FIGHT, body=[6, -8, 0]))
fpr(m, 0.0, FP_CHAMBER)
fpr(m, 0.9, FP_PALM, "in")
fpr(m, 22.4, nudge(FP_PALM, (0, 0, 0.03)))
fpr(m, 24.2, nudge(FP_PALM, (0, -0.03, 0.1), (0, -10, 35)), "in")
fpr(m, 32.4, nudge(FP_PALM, (0, -0.03, 0.1), (0, -10, 35)))
fpr(m, 34.6, FP_CHAMBER, "out")
fpr(m, 36.2, nudge(FP_PALM, (0, 0, -0.12)), "in")
fpr(m, 41.0, FP_GUARD)
fpl(m, 0.0, FP_LOW)
fpl(m, 41.0, FP_GUARD)
use(m, "petrify")

# ------------------------------------------------------------ 무투

# 연환권 (효과: 0 · 10 · 20 손에서 3D 기의 주먹이 날아감 (오른 · 왼 · 오른) · 30 3D 기의 손바닥이 밀려 나가는 장풍): 주먹마다 그 틱에 허리에서 비틀어 쭉 뻗음 —
# 0 에서 오른 주먹을 허리에 당기고 몸을 오른쪽으로 비튼 자세 → 0.8 에 몸째 왼쪽으로 풀며 내지름 (내디딤) → 당김 → 왼 · 오른 → 27.6 에 두 손을 오른 허리에
# 모아 깊이 웅크렸다가 → 30.8 에 크게 내디디며 두 손바닥을 앞으로 힘껏 밀어냄 → 지나쳐 → 숨 → 거둠
m = fists(Motion("chain_fist", 40, "연환권 — 허리에서 비틀어 오른 · 왼 · 오른 주먹을 쭉 내지르고, 두 손을 허리에 모았다가 크게 내디디며 두 손바닥으로 장풍", fade=(0.01, 0.08)))
CHAM_R = P(FIGHT, body=[8, 34, 0], head=[0, -18], ra=A_CHAMBER, la=LA(A_GUARD), rl=[18, 0, 6], ll=[-16, 0, -6], rk=36, lk=26, root=[-0.16, 0])
CHAM_L = P(FIGHT, body=[8, -34, 0], head=[0, 18], ra=A_GUARD, la=LA(A_CHAMBER), rl=[18, 0, 6], ll=[-16, 0, -6], rk=36, lk=26, root=[-0.16, 0])
JAB_R = P(FIGHT, body=[16, -30, 0], head=[4, 10], ra=A_JAB, la=LA(A_GUARD), root=[-0.28, 0.45], **LUNGE_DEEP)
JAB_L = P(FIGHT, body=[16, 28, 0], head=[4, -10], ra=A_GUARD, la=LA(A_JAB), root=[-0.28, 0.45], **LUNGE_DEEP)
GATHER2 = P(FIGHT, body=[6, 42, 0], head=[0, -22], ra=A_CHAMBER, la=bent([0.15, -0.55, 0.55], 105, (1, 0.3, 0.2)), rl=[22, 0, 6], ll=[-14, 0, -6], rk=50, lk=36,
            root=[-0.26, -0.05])
PALMS2 = P(FIGHT, body=[20, -10, 0], head=[6, 0], ra=K.A_PALM, la=LA(K.A_PALM), root=[-0.34, 0.6], **LUNGE_DEEP)
keyp(m, 0.0, CHAM_R)
keyp(m, 0.8, JAB_R, "in")
keyp(m, 1.8, P(JAB_R, body=[18, -36, 0]), "out")
keyp(m, 4.5, P(JAB_R, body=[12, -24, 0], root=[-0.22, 0.35]))
for T, cham, jab, sd in ((10, CHAM_L, JAB_L, -1), (20, CHAM_R, JAB_R, 1)):
    keyp(m, T - 2.2, cham, "out")
    keyp(m, T + 0.8, jab, "in")
    keyp(m, T + 1.8, P(jab, body=[18, sd * -36, 0]), "out")
    keyp(m, T + 4.5, P(jab, body=[12, sd * -24, 0], root=[-0.22, 0.35]))
keyp(m, 27.6, GATHER2, "out")
keyp(m, 29.4, P(GATHER2, body=[8, 46, 0], rk=56, lk=40, root=[-0.3, -0.06]))
keyp(m, 30.8, PALMS2, "in")
keyp(m, 32.0, P(PALMS2, body=[24, -14, 0]), "out")
breathe(m, 33.2, 36.0, PALMS2, step=1.4, lean=1.0, knee=2)
keyp(m, 39.0, P(FIGHT, body=[6, -8, 0]))
fpr(m, 0.0, FP_CHAMBER)
fpl(m, 0.0, FP_GUARD)
fpr(m, 0.8, FP_JAB2, "in")
fpr(m, 4.5, nudge(FP_JAB2, (0, -0.04, 0.08)))
fpl(m, 7.8, FP_CHAMBER, "out")
fpr(m, 7.8, FP_GUARD, "out")
fpl(m, 10.8, FP_JAB2, "in")
fpl(m, 14.5, nudge(FP_JAB2, (0, -0.04, 0.08)))
fpr(m, 17.8, FP_CHAMBER, "out")
fpl(m, 17.8, FP_GUARD, "out")
fpr(m, 20.8, FP_JAB2, "in")
fpr(m, 24.5, nudge(FP_JAB2, (0, -0.04, 0.08)))
fpb(m, 27.6, FP_GATHER_R, FP_GATHER_L, ease="out")
fpb(m, 30.8, nudge(FP_PALM, (0, 0, -0.1)), ease="in")
fpb(m, 36.0, FP_PALM)
fpb(m, 39.0, FP_GUARD)
use(m, "chainFist")

# 비각 (motionAt 5 — 0 = 공중 · 3 = 착지 날아차기 · 3 ~ 10 반동으로 떠오름 · 10 아래로 · 13 내려찍기): 0 에서 공중에서 오른 무릎을 가슴까지 당기고 몸을 젖힘 →
# 3 에 오른발을 앞으로 쭉 뻗어 날아참 (몸을 뒤로 눕힘) → 반동으로 무릎을 당겨 몸을 웅크리며 오른 주먹을 머리 위로 → 12 에 아래로 → 13 에 주먹으로 땅을
# 내려찍고 깊이 웅크림 → 박은 채 숨 → 일어섬
m = fists(Motion("flying_kick", 22, "비각 — 공중에서 무릎을 당겼다가 오른발을 쭉 뻗어 날아차고, 반동으로 솟아 웅크렸다가 주먹으로 땅을 내려찍음", fade=(0.01, 0.1)))
KNEE_IN = P(body=[-12, 10, 0], head=[6, 0], ra=A_GUARD, la=LA(A_GUARD), rl=[-110, 0, 4], rk=120, ll=[-20, 0, -4], lk=60, root=[0.2, 0.1])
KICK = P(body=[-28, 14, 0], head=[16, -6], ra=arm([0.4, -0.25, -0.85], 20), la=LA(A_GUARD), rl=[-92, 0, 4], rk=2, ll=[-24, 0, -4], lk=110, root=[0.24, 0.35])
TUCK = P(body=[10, 0, 0], head=[-4, 0], ra=A_RAISE_FIST, la=LA(A_GUARD), rl=[-88, 0, 4], rk=118, ll=[-80, 0, -4], lk=118, root=[0.34, 0.05])
POUND = P(body=[44, 0, 0], head=[16, 0], ra=A_GROUND, la=LA(K.A_BACK), rl=[-40, 0, 10], rk=86, ll=[22, 0, -10], lk=96, root=[-0.46, 0.15])
keyp(m, 0.0, KNEE_IN)
keyp(m, 2.0, P(KNEE_IN, body=[-16, 12, 0], rl=[-118, 0, 4], rk=128))
keyp(m, 3.1, KICK, "in")
keyp(m, 4.2, P(KICK, body=[-32, 16, 0]), "out")
keyp(m, 6.6, TUCK, "inout")
keyp(m, 10.4, P(TUCK, body=[4, 0, 0], ra=arm([0.15, 0.96, -0.2], 20, [0, 0.3, -1])), "out")
keyp(m, 12.0, P(TUCK, body=[24, 0, 0], ra=arm([0.15, 0.6, 0.78], 20), rl=[-50, 0, 6], ll=[-40, 0, -6], rk=60, lk=60, root=[0.1, 0.1]), "in")
keyp(m, 13.1, POUND, "in")
keyp(m, 14.2, P(POUND, body=[40, 0, 0], root=[-0.42, 0.14]), "out")
breathe(m, 15.4, 17.6, POUND, step=1.1, lean=1.0, knee=2)
keyp(m, 21.0, P(FIGHT, body=[6, -8, 0]))
fpb(m, 0.0, FP_GUARD)
fpr(m, 3.1, FP_LOW, "in")
fpl(m, 3.1, FP_GUARD)
fpr(m, 6.6, FP_RAISE, "inout")
fpr(m, 12.0, nudge(FP_RAISE, (0, -0.1, -0.15)), "in")
fpr(m, 13.1, FP_GROUND, "in")
fpl(m, 13.1, FP_LOW)
fpr(m, 17.6, FP_GROUND)
fpb(m, 21.0, FP_GUARD)
use(m, "flyingKick")

# 붕권 (효과: 0 ~ 10 3D 기의 손바닥이 몸 앞에서 커짐 (기를 모음) · 10 손바닥이 앞으로 밀려 나가며 부채꼴 장풍): 0 에서 두 손을 오른 허리에 모으고 몸을 오른쪽으로
# 끝까지 비틀어 웅크림 → 모으는 동안 조금씩 더 감고 떨림 → 10.4 에 크게 내디디며 몸째 풀어 두 손바닥을 앞으로 힘껏 내지름 → 지나쳐 → 숨 → 거둠
m = fists(Motion("palm_blast", 24, "붕권 — 두 손을 허리에 모아 몸을 끝까지 비틀어 기를 모았다가 크게 내디디며 몸째 풀어 두 손바닥으로 내지름", fade=(0.01, 0.1)))
COIL = P(FIGHT, body=[8, 48, 0], head=[0, -26], ra=A_CHAMBER, la=bent([0.15, -0.55, 0.55], 105, (1, 0.3, 0.2)), rl=[24, 0, 6], ll=[-14, 0, -6], rk=52, lk=36,
         root=[-0.28, -0.1])
BLAST = P(FIGHT, body=[22, -12, 0], head=[6, 0], ra=K.A_PALM, la=LA(K.A_PALM), root=[-0.36, 0.7], **LUNGE_DEEP)
keyp(m, 0.0, COIL)
for i, tk in enumerate((2.0, 4.0, 6.0, 8.0)):
    s = 1 if i % 2 else -1
    keyp(m, tk, P(COIL, body=[8 + i * 0.6, 48 + i * 1.5 + s, s * 0.8], rk=52 + i * 2, lk=36 + i * 2, root=[-0.28 - i * 0.015, -0.1]))
keyp(m, 9.4, P(COIL, body=[10, 56, 0], rk=62, lk=44, root=[-0.36, -0.12]))
keyp(m, 10.4, BLAST, "in")
keyp(m, 11.8, P(BLAST, body=[26, -16, 0]), "out")
breathe(m, 13.0, 18.0, BLAST, step=1.6, lean=1.2, knee=2)
keyp(m, 22.5, P(FIGHT, body=[6, -8, 0]))
for t, dx in ((0.0, 0.0), (2.0, 0.015), (4.0, -0.015), (6.0, 0.015), (8.0, -0.015), (9.4, 0.0)):
    fpb(m, t, nudge(FP_GATHER_R, (dx, 0, 0.02)), nudge(FP_GATHER_L, (-dx, 0, 0.02)))
fpb(m, 10.4, nudge(FP_PALM, (0, 0, -0.1), (2, 4, 0)), ease="in")
fpb(m, 18.0, FP_PALM)
fpb(m, 22.5, FP_GUARD)
use(m, "palmBlast")

# ------------------------------------------------------------ 사냥꾼

# 강철 덫 (효과: 0 3D 덫이 손에서 포물선으로 날아감 · 5 내려앉음 · 6 ~ 8 턱이 철컥 닫힘 (8 피해 · 묶음)): 0 에서 덫을 쥔 왼손을 아래 뒤로 크게 감고 웅크림 →
# 0.9 에 몸을 펴며 아래에서 앞 위로 힘껏 던져 올림 (내디딤) → 지켜봄 → 7.4 에 턱이 닫히는 순간 왼 주먹을 쥐어 허리로 홱 당김 → 숨 → 거둠
m = Motion("steel_trap", 16, "강철 덫 — 덫을 쥔 왼손을 아래 뒤로 감았다가 앞 위로 힘껏 던져 올리고, 턱이 닫히는 순간 주먹을 쥐어 홱 당김", fade=(0.01, 0.12))
m.set(hands={"main": "weapon", "off": "prop"}, holdOff="rpg:prop_steel_trap")
m.set(holdOffAt=[0.0, m.t(0.95)])
TRAP_WIND = P(body=[24, 22, 0], head=[8, -10], ra=K.RA_READY, la=LA(arm([0.3, -0.75, -0.6], 14)), rl=[20, 0, 6], ll=[-14, 0, -6], rk=48, lk=32, root=[-0.3, -0.05])
TRAP_REL = P(body=[-6, -14, 0], head=[-8, 6], ra=K.RA_READY, la=LA(arm([0.08, 0.5, 0.86], 4)), root=[-0.2, 0.4], **LUNGE_DEEP)
YANK = P(body=[2, 14, 0], head=[0, -4], ra=K.RA_READY, la=LA(A_PULL), rl=[26, 0, 4], ll=[-16, 0, -4], rk=30, lk=34, root=[-0.16, -0.1])
keyp(m, 0.0, TRAP_WIND)
keyp(m, 0.95, TRAP_REL, "in")
keyp(m, 2.2, P(TRAP_REL, body=[-10, -16, 0], la=LA(arm([0.08, 0.62, 0.78], 4))), "out")
keyp(m, 6.0, P(TRAP_REL, body=[4, -8, 0], la=LA(arm([0.1, 0.12, 0.99], 10)), root=[-0.16, 0.3]))
keyp(m, 7.6, YANK, "in")
keyp(m, 8.8, P(YANK, body=[-2, 18, 0]), "out")
breathe(m, 9.8, 12.0, YANK, step=1.1, lean=1.0, knee=2)
keyp(m, 15.0, P(W_READY, body=[6, 4, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 15.0, FPW_LOW)
m.fpo(0.0, (0.05, -0.32, 0.22), (-32, 0, 0))  # 덫을 아래로 (화면 좌표)
m.fpo(0.9, (0.15, 0.28, -0.32), (26, 0, 0), "in")  # 앞 위로 던져 올림
fpl(m, 1.0, nudge(FP_PALM_UP, (0, 0.05, 0)))
fpl(m, 6.0, FP_JAB)
fpl(m, 7.6, FP_CHAMBER, "in")
fpl(m, 12.0, FP_CHAMBER)
fpl(m, 15.0, FP_LOW)
use(m, "steelTrap")

# 매의 습격 (효과: 0 손에서 3D 매가 날아오름 · 4 · 14 · 24 · 34 급강하해 할큄): 0 에서 왼팔을 앞 아래로 세워 매를 앉히고 웅크림 → 1 에 일어서며 왼팔을 하늘로
# 힘껏 뻗어 날려 보냄 → 3 에 대상을 가리킴 → 급강하마다 손을 쥐었다 대상으로 홱 내리꽂듯 가리킴 → 거둠
m = off_bare(Motion("falcon_strike", 40, "매의 습격 — 왼팔에 앉힌 매를 하늘로 힘껏 날려 보내고, 급강하마다 대상을 홱 내리꽂듯 가리킴", fade=(0.01, 0.08)))
PERCH = P(body=[18, 10, 0], head=[6, 0], ra=K.RA_READY, la=LA(bent([-0.1, -0.1, 0.95], 80, (0, 1, 0.2))), rl=[-14, 0, 8], ll=[-18, 0, -8], rk=40, lk=40,
          root=[-0.24, 0])
LAUNCH = P(body=[-12, -6, 0], head=[-24, 0], ra=K.RA_READY, la=LA(arm([-0.05, 0.85, 0.5], 4)), **LEGS_WIDE)
POINT_T = P(body=[10, -16, 0], head=[2, 4], ra=K.RA_READY, la=LA(A_POINT), root=[-0.12, 0.2], **LEGS_FIGHT)
COMMAND = P(body=[18, -20, 0], head=[8, 4], ra=K.RA_READY, la=LA(arm([-0.02, -0.25, 0.97], 0)), root=[-0.22, 0.3], **LUNGE_DEEP)
keyp(m, 0.0, PERCH)
keyp(m, 1.0, LAUNCH, "in")
keyp(m, 2.0, P(LAUNCH, body=[-15, -6, 0], head=[-30, 0]), "out")
keyp(m, 3.4, POINT_T, "inout")
for T in (4, 14, 24, 34):
    if T > 4:
        keyp(m, T - 2.0, P(POINT_T, la=LA(bent([-0.1, 0.5, 0.6], 60, (0, 1, 0.2))), body=[4, -10, 0]), "out")
    keyp(m, T + 0.4, COMMAND, "in")
    keyp(m, T + 3.0, P(COMMAND, body=[14, -18, 0], root=[-0.18, 0.25]))
keyp(m, 39.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 39.0, FPW_LOW)
fpl(m, 0.0, ((-0.05, 0.0, -0.1), (40, 40, 0)))
fpl(m, 1.0, nudge(FP_PALM_UP, (0, 0.18, -0.05), (24, 0, 0)), "in")
fpl(m, 3.4, FP_JAB, "inout")
for T in (14, 24, 34):
    fpl(m, T - 2.0, nudge(FP_JAB, (0, 0.12, 0.1), (20, 0, 0)), "out")
    fpl(m, T + 0.4, nudge(FP_JAB, (0, -0.06, -0.05), (-12, 0, 0)), "in")
fpl(m, 39.0, FP_LOW)
use(m, "falconStrike")

# 사냥 그물 (효과: 0 3D 그물이 손에서 포물선으로 날며 펼쳐짐 · 6 땅을 덮어 묶음 · 6 · 18 · 30 조임): 0 에서 두 손으로 쥔 그물을 오른쪽 뒤 아래로 끝까지 감고
# 몸을 비틀어 웅크림 → 1.1 에 몸째 풀며 앞 위로 크게 펼쳐 던짐 (내디딤) → 조일 때마다 두 주먹을 허리로 홱 당기며 몸을 젖힘 → 거둠
m = Motion("hunt_net", 36, "사냥 그물 — 두 손으로 쥔 그물을 오른쪽 뒤로 끝까지 감았다가 앞 위로 크게 펼쳐 던지고, 조일 때마다 줄을 홱 당김", fade=(0.01, 0.08))
m.set(hands={"main": "prop", "off": "fist"}, hold="rpg:prop_hunt_net")
m.set(holdAt=[0.0, m.t(1.15)])
NET_WIND = P(body=[16, 52, 0], head=[2, -28], ra=bent([0.6, -0.35, -0.5], 50, (0, 0.5, 1)), la=LA(bent([-0.3, -0.35, 0.3], 70, (1, 0.5, 0.2))),
             rl=[22, 0, 6], ll=[-14, 0, -6], rk=46, lk=30, root=[-0.26, -0.1])
NET_REL = P(body=[8, -26, 0], head=[-8, 8], ra=arm([0.38, 0.5, 0.78], 4), la=LA(arm([0.38, 0.5, 0.78], 4)), root=[-0.24, 0.5], **LUNGE_DEEP)
TUG = P(body=[-12, 12, 0], head=[-4, -4], ra=A_PULL, la=LA(A_PULL), rl=[30, 0, 4], ll=[-12, 0, -4], rk=24, lk=34, root=[-0.12, -0.14])
HOLD_ROPE = P(body=[10, -6, 0], ra=arm([0.2, -0.3, 0.93], 30), la=LA(arm([0.2, -0.3, 0.93], 30)), root=[-0.14, 0.1], **LEGS_FIGHT)
keyp(m, 0.0, NET_WIND)
keyp(m, 1.15, NET_REL, "in")
keyp(m, 2.4, P(NET_REL, body=[10, -30, 0], ra=arm([0.4, 0.55, 0.74], 4), la=LA(arm([0.4, 0.55, 0.74], 4))), "out")
keyp(m, 4.4, HOLD_ROPE, "inout")
for T in (6, 18, 30):
    keyp(m, T - 1.2, P(HOLD_ROPE, body=[14, -8, 0], root=[-0.16, 0.14]))
    keyp(m, T + 0.6, TUG, "in")
    keyp(m, T + 2.0, P(TUG, body=[-15, 14, 0]), "out")
    if T < 30:
        keyp(m, T + 7.0, HOLD_ROPE, "inout")
keyp(m, 35.0, P(W_READY, body=[6, 0, 0]))
FPB_OPEN = nudge(FP_PALM_UP, (0.12, 0, 0), (0, -15, 0))
m.fp(0.0, (0.28, -0.22, 0.28), (-24, 0, 0))  # 그물 (오른쪽 아래 뒤)
m.fp(1.1, (-0.15, 0.25, -0.4), (30, 0, 0), ease="in")  # 앞 위로 던짐
fpr(m, 1.2, FPB_OPEN)
fpl(m, 0.0, FP_GATHER_L)
fpl(m, 1.15, FPB_OPEN, "in")
for T in (6, 18, 30):
    fpb(m, T - 1.2, FP_JAB)
    fpb(m, T + 0.6, FP_CHAMBER, ease="in")
fpb(m, 35.0, FP_GUARD)
use(m, "huntNet")

# ------------------------------------------------------------ 화산

# 용암 강타 (효과: 0 앞 땅이 갈라져 용암이 튐 (피해) — 3D 달아오른 바위 조각이 부채꼴로 솟아 튐): 0 에서 두 주먹을 깍지 껴 머리 위 뒤로 치켜들고 몸을 활처럼
# 젖힘 (발끝) → 0.9 에 온몸으로 앞 땅을 내리쳐 깊이 웅크림 → 반동 → 박은 채 숨 → 일어섬
m = fists(Motion("magma_slam", 16, "용암 강타 — 두 주먹을 머리 위 뒤로 치켜들어 몸을 젖혔다가 온몸으로 앞 땅을 내리쳐 깊이 웅크림", fade=(0.01, 0.1)))
keyp(m, 0.0, P(body=[-22, 0, 0], head=[-14, 0], ra=arm([0.1, 0.86, -0.5], 42), la=LA(arm([0.1, 0.86, -0.5], 46)), rl=[6, 0, 4], ll=[-8, 0, -4], rk=10, lk=12,
               root=[0.06, -0.08]))
keyp(m, 0.9, FIST_SLAM, "in")
keyp(m, 1.9, P(FIST_SLAM, body=[44, 0, 0], rk=80, lk=82, root=[-0.46, 0.28]), "out")
keyp(m, 3.0, FIST_SLAM)
breathe(m, 4.0, 9.0, FIST_SLAM, step=1.7, lean=1.5, knee=3)
keyp(m, 14.0, P(FIGHT, body=[6, -8, 0]))
fpb(m, 0.0, FP_RAISE)
fpb(m, 0.9, FP_GROUND, ease="in")
fpb(m, 9.0, FP_GROUND)
fpb(m, 14.0, FP_GUARD)
use(m, "magmaSlam")

# 화산탄 (효과: 0 · 10 · 20 손에서 3D 화산탄 (운석) 이 포물선으로 날아감 · 8 · 18 · 28 폭발): 던질 때마다 화산탄을 귀 옆 뒤로 크게 당겨 몸을 오른쪽으로 비틀고
# 왼손으로 겨눴다가 몸째 내던지며 크게 내디딤 (셋째는 더 크게) — 던지는 사이에 새 화산탄이 손에
m = Motion("lava_bombs", 30, "화산탄 — 불타는 화산탄을 귀 옆 뒤로 크게 당겨 몸을 비틀었다가 몸째 내던지며 크게 내디딤 — 세 번", fade=(0.01, 0.08))
m.set(hands={"main": "prop"}, hold="rpg:prop_lava_bomb")
LOB_WIND = P(body=[-8, 48, 0], head=[0, -30], ra=bent([0.45, 0.55, -0.55], 85, (0, 1, -0.2)), la=LA(A_POINT), rl=[22, 0, 4], ll=[-18, 0, -4], rk=34, lk=16,
             root=[-0.1, -0.14])
LOB_REL = P(body=[22, -32, 0], head=[4, 10], ra=K.A_THROW_REL, la=LA(A_HIP), root=[-0.32, 0.55], **LUNGE_DEEP)
LOB_REST = P(body=[10, -12, 0], ra=K.A_THROW_FOLLOW, la=LA(A_GUARD), root=[-0.14, 0.2], **LEGS_FIGHT)
windows = []
for i, (T, wind) in enumerate(((0, 0.0), (10, 7.4), (20, 17.4))):
    rel = T + 0.9
    big = 1.0 if i < 2 else 1.25
    keyp(m, wind, P(LOB_WIND, body=[-8, 48 * big, 0]))
    keyp(m, rel, P(LOB_REL, body=[22 * big, -32 * big, 0]), "in")
    keyp(m, rel + 1.3, P(LOB_REL, body=[26 * big, -38 * big, 0]), "out")
    keyp(m, rel + 4.0, LOB_REST)
    windows += [m.t(wind - 1.4) if wind > 1 else 0.0, m.t(rel + 0.05)]
    if wind > 1:
        fpr(m, wind - 1.5, FP_LOW)  # 빈손이 내려가 새 화산탄을 쥠
    m.fp(wind, (0.05, 0.35, 0.25), (20, 0, 0))
    m.fp(rel - 0.05, (-0.1, 0.2, -0.45), (-15, 0, 0), ease="in")
    fpr(m, rel + 0.15, nudge(FP_JAB2, (0, -0.05, 0)))
    fpr(m, rel + 4.0, FP_GUARD)
keyp(m, 28.5, P(W_READY, body=[6, -4, 0]))
m.set(holdAt=windows)
use(m, "lavaBombs")

# 용암 분출 (효과: 0 조준한 곳의 땅이 갈라짐 · 6 · 16 · 26 · 36 용암 기둥이 솟구침 — 3D 달아오른 바위가 함께 솟아 둘레로 떨어짐): 0 에서 웅크려 왼 손바닥을
# 조준한 곳 땅으로 힘껏 내리누름 → 분출마다 손바닥을 확 쳐올리며 몸을 젖힘 (다시 내리누르고) → 마지막은 두 팔을 하늘로 → 거둠
m = off_bare(Motion("magma_geyser", 42, "용암 분출 — 웅크려 왼 손바닥으로 갈라진 땅을 내리누르다가, 용암 기둥이 솟을 때마다 손바닥을 확 쳐올리며 몸을 젖힘", fade=(0.01, 0.08)))
PRESS = P(body=[26, 12, 0], head=[10, 0], ra=K.RA_READY, la=LA(arm([-0.05, -0.55, 0.83], 4, [0, 1, 0.2])), rl=[-16, 0, 14], ll=[-20, 0, -14], rk=50, lk=50,
          root=[-0.32, 0.05])
HEAVE = P(body=[-12, 10, 0], head=[-14, 0], ra=K.RA_READY, la=LA(arm([-0.05, 0.62, 0.78], 4, [0, 1, 0.1])), **LEGS_WIDE)
keyp(m, 0.0, P(PRESS, la=LA(arm([0.0, 0.2, 0.98], 20))))
keyp(m, 1.2, PRESS, "in")
for T in (6, 16, 26, 36):
    keyp(m, T - 1.6, P(PRESS, body=[30, 12, 0], rk=58, lk=58, root=[-0.38, 0.05]), "out")
    last = T == 36
    keyp(m, T + 0.5, P(HEAVE, ra=K.RA_SKY) if last else HEAVE, "in")
    keyp(m, T + 2.0, P(HEAVE, body=[-16, 10, 0], head=[-20, 0], ra=K.RA_SKY if last else K.RA_READY), "out")
keyp(m, 41.0, P(W_READY, body=[6, 0, 0]))
fpw(m, 0.0, FPW_LOW)
fpw(m, 36.5, FPW_SKY, "in", dp=(0.2, -0.3, 0))
fpw(m, 41.0, FPW_LOW)
fpl(m, 1.2, FP_DOWN, "in")
for T in (6, 16, 26, 36):
    fpl(m, T - 1.6, nudge(FP_DOWN, (0, -0.06, 0)), "out")
    fpl(m, T + 0.5, FP_PALM_UP, "in")
fpl(m, 41.0, FP_LOW)
use(m, "magmaGeyser")

# ------------------------------------------------------------ 투사

# 사슬 갈고리 (효과: 0 ~ 3 3D 갈고리가 손에서 사슬을 끌며 뻗어 꽂힘 · 3 피해 · 4 ~ 6 끌어옴 — 사슬이 감김): 0 에서 갈고리를 귀 옆 뒤로 크게 당기고 몸을 오른쪽으로
# 비틀어 왼손으로 겨눔 → 0.8 에 몸째 내던짐 (내디딤) → 3 에 사슬을 두 손으로 잡고 → 4.4 에 몸을 뒤로 젖히며 홱 당김 (뒷발로 버팀) → 한 번 더 감아 당김 → 거둠
m = Motion("chain_hook", 16, "사슬 갈고리 — 갈고리를 귀 옆 뒤로 당겼다가 몸째 내던져 꽂고, 사슬을 두 손으로 잡아 몸을 젖히며 홱 끌어당김", fade=(0.01, 0.1))
m.set(hands={"main": "prop", "off": "fist"}, hold="rpg:prop_chain_hook")
m.set(holdAt=[0.0, m.t(0.85)])
HOOK_WIND = P(body=[-8, 48, 0], head=[0, -30], ra=K.RA_BACK_HIGH, la=LA(A_POINT), rl=[22, 0, 4], ll=[-18, 0, -4], rk=34, lk=16, root=[-0.1, -0.14])
HOOK_REL = P(body=[22, -32, 0], head=[4, 10], ra=K.A_THROW_REL, la=LA(A_HIP), root=[-0.32, 0.55], **LUNGE_DEEP)
GRAB = P(body=[16, -18, 0], ra=arm([0.05, -0.05, 1], 12), la=LA(arm([0.0, -0.1, 1], 16)), root=[-0.28, 0.4], **LUNGE_DEEP)
HEAVE2 = P(body=[-16, 18, 0], head=[-6, -6], ra=A_PULL, la=LA(bent([0.2, -0.3, 0.2], 110, (0, 0.3, 1))), rl=[36, 0, 4], ll=[-8, 0, -4], rk=14, lk=40,
           root=[-0.08, -0.2])
keyp(m, 0.0, HOOK_WIND)
keyp(m, 0.85, HOOK_REL, "in")
keyp(m, 2.0, P(HOOK_REL, body=[24, -36, 0]), "out")
keyp(m, 3.2, GRAB, "inout")
keyp(m, 4.4, HEAVE2, "in")
keyp(m, 5.4, P(HEAVE2, body=[-20, 20, 0]), "out")
keyp(m, 6.6, P(GRAB, body=[8, -8, 0], root=[-0.18, 0.1]), "inout")
keyp(m, 7.8, P(HEAVE2, body=[-14, 16, 0]), "in")
breathe(m, 9.0, 11.0, P(HEAVE2, body=[-12, 14, 0]), step=1.0, lean=1.0, knee=2)
keyp(m, 15.0, P(W_READY, body=[6, 0, 0]))
m.fp(0.0, (0.05, 0.35, 0.25), (20, 0, 0))
m.fp(0.8, (-0.1, 0.2, -0.45), (-15, 0, 0), ease="in")
fpr(m, 1.0, FP_JAB2)
fpr(m, 3.2, FP_JAB, "inout")
fpr(m, 4.4, FP_CHAMBER, "in")
fpr(m, 6.6, FP_JAB, "inout")
fpr(m, 7.8, FP_CHAMBER, "in")
fpl(m, 0.0, FP_JAB)
fpl(m, 0.85, FP_LOW)
fpl(m, 3.2, FP_JAB, "inout")
fpl(m, 4.4, FP_CHAMBER, "in")
fpl(m, 6.6, FP_JAB, "inout")
fpl(m, 7.8, FP_CHAMBER, "in")
fpb(m, 15.0, FP_GUARD)
use(m, "chainHook")

# 방패 돌진 (효과: 0 ~ 돌진 — 3D 탑 방패를 몸 앞 오른쪽에 들고 · 돌진 끝 +1 (보통 3) 부딪혀 밀쳐냄 · +6 방패를 머리 위로 · +11 (보통 13) 앞 땅에 내려찍음):
# 0 에서 낮게 숙여 오른 팔뚝을 앞으로 세워 방패를 들고 박차고 → 내달림 → 3 에 오른 어깨째 방패를 앞으로 힘껏 밀쳐냄 → 물러서며 → 8 에 두 손으로 방패를
# 머리 위로 치켜들고 몸을 젖힘 → 13 에 온몸으로 앞 땅에 내려찍고 깊이 웅크림 → 박은 채 숨 → 일어섬 (무기는 숨기고 맨팔 — 방패를 듦)
m = fists(Motion("shield_rush", 22, "방패 돌진 — 방패를 앞세워 낮게 내달려 어깨째 밀쳐내고, 두 손으로 머리 위로 치켜들었다가 앞 땅에 내려찍음", fade=(0.01, 0.1)))
RUSH = P(body=[30, -8, 0], head=[-12, 0], ra=K.A_SHIELD, la=LA(K.A_BACK), rl=[-40, 0, 0], ll=[26, 0, 0], rk=56, lk=30, root=[-0.2, 0.2])
BASH = P(body=[22, -24, 0], head=[-4, 8], ra=arm([0.1, -0.15, 0.98], 60, [0, 1, 0.1]), la=LA(K.A_BACK), root=[-0.3, 0.55], **LUNGE_DEEP)
LIFT = P(body=[-18, 0, 0], head=[-14, 0], ra=arm([0.15, 0.9, -0.38], 40), la=LA(arm([0.15, 0.9, -0.38], 44)), rl=[6, 0, 6], ll=[-8, 0, -6], rk=12, lk=14,
         root=[0.04, -0.08])
DRIVE = P(body=[46, 0, 0], head=[16, 0], ra=arm([0.12, -0.6, 0.79], 10), la=LA(arm([0.12, -0.6, 0.79], 12)), rl=[-30, 0, 10], ll=[-34, 0, -10], rk=74, lk=78,
          root=[-0.5, 0.3])
keyp(m, 0.0, RUSH)
keyp(m, 1.0, P(RUSH, rl=[26, 0, 0], ll=[-40, 0, 0], rk=30, lk=56))
keyp(m, 2.0, RUSH)
keyp(m, 3.1, BASH, "in")
keyp(m, 4.2, P(BASH, body=[24, -28, 0]), "out")
keyp(m, 6.2, P(W_READY, body=[10, -6, 0], ra=K.A_SHIELD, la=LA(A_GUARD), root=[-0.12, 0.2]), "inout")
keyp(m, 8.4, LIFT, "out")
keyp(m, 11.6, P(LIFT, body=[-22, 0, 0], head=[-18, 0], rk=18, lk=20))
keyp(m, 13.0, DRIVE, "in")
keyp(m, 14.0, P(DRIVE, body=[42, 0, 0], root=[-0.46, 0.28]), "out")
breathe(m, 15.2, 18.0, DRIVE, step=1.4, lean=1.0, knee=2)
keyp(m, 21.0, P(FIGHT, body=[6, -8, 0]))
fpr(m, 0.0, nudge(FP_GUARD, (0.1, -0.1, -0.1)))
fpl(m, 0.0, FP_LOW)
fpr(m, 3.1, FP_JAB2, "in")
fpr(m, 6.2, FP_GUARD, "inout")
fpb(m, 8.4, FP_RAISE, ease="out")
fpb(m, 13.0, FP_GROUND, ease="in")
fpb(m, 18.0, FP_GROUND)
fpb(m, 21.0, FP_GUARD)
use(m, "shieldRush")

# 사슬 회전 (효과: 0 ~ 40 3D 가시 철구 둘이 사슬에 매달려 몸 둘레를 틱마다 24° 씩 돎 (15틱에 한 바퀴) · 0 · 10 · 20 · 30 둘레를 침 · 40 사슬로 끌어모음):
# 0 에서 몸을 왼쪽으로 감고 두 손으로 사슬을 쥠 → 철구와 함께 몸째 오른쪽으로 세 바퀴 (점점 낮게) → 41 에 두 손으로 사슬을 몸쪽으로 홱 당기며 젖힘 → 거둠
m = fists(Motion("chain_whirl", 48, "사슬 회전 — 두 손으로 사슬을 쥐고 철구와 함께 몸째 오른쪽으로 세 바퀴 휘몰아치다가 사슬을 몸쪽으로 홱 당겨 끌어모음", fade=(0.01, 0.08)))
WHIRL = P(K.SPIN_FWD_R, body=[14, 4, 0], ra=arm([0.25, 0.0, 0.97], 12), la=LA(arm([-0.1, 0.0, 1], 14)), rk=34, lk=34, root=[-0.18, 0])
spin_right(m, (1.0, 16.0, 31.0), 40.0, wind_tick=0.0, fwd=WHIRL, end_pose=P(K.SPIN_END_R, body=[16, 22, 0], rk=44, lk=40, root=[-0.24, 0.1]))
m.keys[0.0] = P(K.SPIN_WIND_R, body=[8, -50, 0], head=[0, 18], rk=36, lk=32, root=[-0.18, -0.05])
REEL = P(body=[-14, 0, 0], head=[-6, 0], ra=A_PULL, la=LA(A_PULL), rl=[28, 0, 0], ll=[-12, 0, 0], rk=14, lk=36, root=[-0.08, -0.16], spin=-1080)
keyp(m, 41.2, REEL, "in")
keyp(m, 42.4, P(REEL, body=[-18, 0, 0]), "out")
keyp(m, 44.0, REEL)
keyp(m, 47.0, P(FIGHT, body=[6, -8, 0], spin=-1080))
fpb(m, 41.2, FP_CHAMBER, ease="in")
fpb(m, 44.0, FP_CHAMBER)
fpb(m, 47.0, FP_GUARD)
use(m, "chainWhirl")
