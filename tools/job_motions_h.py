"""0.51.0 2차 직업 스킬 동작 — 궁수 각성 에길(스나이퍼) · 반전 미르크비드(섀도 헌터) (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
SniperSkills · ShadowhunterSkills). 둘 다 활 (오른손에 활 · 왼손이 시위를 당김 — 1차 궁수 job_motions_b 의 BOW_UP · BOW_DRAWN · BOW_LOOSE 와 같은 꼴).
몸이 뜨는 동안의 다리는 job_motions.AIR. 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (JobKit.motion — 세 번째 화살의 내려앉음 · 놓음,
어둠숲의 사냥의 사냥의 화살, 그림자 구르기의 왼쪽)."""
import rune_motion_kit as K
from job_motions_b import BOW_DRAWN, BOW_LOOSE, BOW_UP, FP_BOW, FP_BOW_LOOSE
from rune_motion_kit import A_RAISE, A_SPREAD_DOWN, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_WIDE, P, arm, bent, fpw, keyp, use_skill

Motion = K.Motion

# 공용 활 자세 (몸이 기울거나 무릎 꿇은 꼴)
KNEEL_UP = P(BOW_UP, body=[4, -22, 0], **LEGS_KNEEL)
KNEEL_DRAWN = P(BOW_DRAWN, body=[4, -24, 0], head=[2, 0], **LEGS_KNEEL)
KNEEL_FULL = P(BOW_DRAWN, body=[2, -26, 0], head=[2, 0], la=arm([0.15, 0.06, -0.38], 148), **LEGS_KNEEL)
KNEEL_LOOSE = P(BOW_LOOSE, body=[-2, -16, 0], **LEGS_KNEEL)
SKY_DRAW = P(body=[-16, -20, 0], head=[-30, 0], ra=arm([0.05, 0.82, 0.57], 0, [0, 1, -0.45]), la=arm([0.2, 0.5, -0.32], 140), **LEGS_WIDE)  # 하늘로 당김
SKY_LOOSE = P(body=[-14, -16, 0], head=[-28, 0], ra=arm([0.05, 0.78, 0.62], 2, [0, 1, -0.35]), la=arm([-0.3, 0.1, -0.6], 40), **LEGS_WIDE)  # 하늘로 놓음
FP_SKY = dict(p=(-0.25, 0.32, 0.1))
FP_LOW_BOW = dict(p=(-0.2, -0.12, 0.08))

# ------------------------------------------------------------ 에길(스나이퍼) (활 — 밝은 연두 · 금빛 · 흰 섬광)

# 꿰뚫는 연사 (효과: 0 ~ 8 뒤로 2.5칸 구름 (낮은 도약) · 2 · 6 에 화살 → 12 무릎 꿇고 12 · 15 에 화살 · 18 에 꿰뚫는 셋째 화살)
ROLL_BACK = P(BOW_DRAWN, body=[-34, -20, 0], head=[14, 0], root=[-0.2, 0])  # 몸을 뒤로 젖혀 구르며 당긴 채
m = Motion("sniper_pierce", 24, "꿰뚫는 연사 — 몸을 뒤로 젖혀 구르며 두 발을 쏘고, 무릎 꿇고 내려앉아 두 발 · 마지막 꿰뚫는 화살을 크게 놓음 (몸이 뒤로 밀림)", fade=(0.02, 0.12))
keyp(m, 0.6, ROLL_BACK, "out3")
fpw(m, 0.6, FP_BOW, "out3")
for t in (2, 6):
    keyp(m, t + 0.7, P(BOW_LOOSE, body=[-30 + t * 2, -16, 0], head=[12, 0], root=[-0.2, 0]), "out3")
    fpw(m, t + 0.7, FP_BOW_LOOSE, "out3")
    keyp(m, t + 2.4, P(ROLL_BACK, body=[-26 + t * 3, -20, 0]))
    fpw(m, t + 2.4, FP_BOW)
keyp(m, 10.0, P(KNEEL_DRAWN, root=[-0.3, 0]), "out")
keyp(m, 11.6, KNEEL_DRAWN)
for i, t in enumerate((12, 15, 18)):
    last = i == 2
    keyp(m, t + 0.7, P(KNEEL_LOOSE, body=[-12, -14, 0], root=[-0.4, -0.12]) if last else KNEEL_LOOSE, "out3")
    fpw(m, t + 0.7, dict(p=(-0.25, 0.12, 0.2)) if last else FP_BOW_LOOSE, "out3")
    if not last:
        keyp(m, t + 2.3, KNEEL_DRAWN)
        fpw(m, t + 2.3, FP_BOW)
keyp(m, 21.5, P(BOW_UP, **LEGS_FIGHT))
fpw(m, 21.5, FP_BOW)
use_skill(m, "sniper_pierce")

# 순풍 공중 사격 (효과: 0 ~ 12 바람을 타고 뒤로 5칸 · 2.5칸 높이 → 4 · 7 · 10 에 공중에서 화살 (아래로 겨눔))
AIR_DRAWN = P(BOW_DRAWN, body=[8, -22, 0], head=[16, 0], ra=arm([0.05, -0.18, 1], 0, [0, 1, 0.1]), la=arm([0.2, -0.12, -0.2], 140))  # 공중에서 아래로 겨눔
AIR_LOOSE = P(BOW_LOOSE, body=[4, -18, 0], head=[14, 0], ra=arm([0.05, -0.12, 1], 0, [0, 1, -0.1]), la=arm([-0.3, -0.2, -0.7], 40))
m = Motion("sniper_gale", 16, "순풍 공중 사격 — 웅크렸다 바람을 타고 뒤로 높이 뛰어올라, 공중에서 아래를 겨눠 화살 세 발을 잇달아 쏘고 내려앉음", fade=(0.02, 0.12))
keyp(m, 0.5, P(BOW_UP, body=[16, -20, 0], root=[-0.25, 0]), "out3")
keyp(m, 2.6, AIR_DRAWN, "out3")
fpw(m, 0.5, FP_BOW, "out3")
for t in (4, 7, 10):
    keyp(m, t + 0.6, AIR_LOOSE, "out3")
    fpw(m, t + 0.6, FP_BOW_LOOSE, "out3")
    if t < 10:
        keyp(m, t + 2.0, AIR_DRAWN)
        fpw(m, t + 2.0, FP_BOW)
keyp(m, 14.5, BOW_UP)
fpw(m, 14.5, FP_BOW)
use_skill(m, "sniper_gale")

# 울르의 폭우 (효과: 0 ~ 12 제자리에서 1.5칸 뛰어오름 · 0 · 8 에 하늘로 화살 → 16 · 30 에 화살비)
m = Motion("sniper_storm", 20, "울르의 폭우 — 뛰어오르며 하늘로 활을 들어 쏘고, 공중에서 다시 끝까지 당겨 한 번 더 쏘아 올린 뒤 내려앉음", fade=(0.02, 0.12))
keyp(m, 0.4, SKY_LOOSE, "out3")
fpw(m, 0.4, FP_SKY, "out3")
keyp(m, 3.0, P(SKY_DRAW, la=arm([0.25, 0.45, -0.1], 115)))
keyp(m, 6.8, SKY_DRAW, "in")
keyp(m, 8.5, SKY_LOOSE, "out3")
fpw(m, 8.5, dict(p=(-0.25, 0.36, 0.18)), "out3")
keyp(m, 13.0, P(BOW_UP, body=[6, -18, 0], head=[-10, 0]))
keyp(m, 17.5, BOW_UP)
fpw(m, 13.0, FP_BOW)
use_skill(m, "sniper_storm")

# 사과 겨누기 (태세 8초 — 효과: 눈앞 · 몸 앞의 약점 과녁이 조여 듦 · 몸의 빛)
APPLE_FULL = P(BOW_DRAWN, body=[0, -24, 0], head=[3, -4], la=arm([0.15, 0.08, -0.4], 148), **LEGS_WIDE)
m = Motion("sniper_apple", 28, "사과 겨누기 — 다리를 벌려 버티고 활을 눈높이로 들어 천천히 끝까지 당긴 뒤, 숨을 멈추고 미동 없이 겨눴다가 겨눈 채 시위를 반쯤 늦춤", fade=(0.04, 0.12))
keyp(m, 2.5, P(BOW_UP, **LEGS_WIDE), "out3")
keyp(m, 11, APPLE_FULL, "linear")
keyp(m, 15, P(APPLE_FULL, body=[1, -24, 0]))
keyp(m, 19, P(APPLE_FULL, body=[0, -24, 0]))
keyp(m, 24, P(BOW_DRAWN, body=[0, -22, 0], **LEGS_WIDE), "out")
fpw(m, 2.5, FP_BOW, "out3")
fpw(m, 11, dict(p=(-0.27, 0.12, -0.02)), "linear")
fpw(m, 24, FP_BOW, "out")
use_skill(m, "sniper_apple")

# 세 번째 화살 (효과: 0 ~ 8 뒤로 3칸 · 2.2칸 높이 뛰어오름 → 22 까지 떠 있음 · 8 · 18 에 공중에서 큰 화살 → 22 ~ 26 내려앉음 (단계 land) → 40 셋째 화살 (단계 release))
m = Motion("sniper_ult", 22, "세 번째 화살 — 웅크렸다 뒤로 높이 뛰어올라 공중에 멈춘 채 아래를 겨눠 빛나는 큰 화살 두 발을 쏨", fade=(0.03, 0.06))
keyp(m, 0.5, P(BOW_UP, body=[18, -20, 0], root=[-0.28, 0]), "out3")
keyp(m, 3.5, P(AIR_DRAWN, la=arm([0.3, -0.05, 0.3], 110)), "out3")
keyp(m, 7.6, AIR_DRAWN, "in")
keyp(m, 8.6, AIR_LOOSE, "out3")
keyp(m, 12.0, P(AIR_DRAWN, la=arm([0.3, -0.05, 0.3], 110)))
keyp(m, 17.6, AIR_DRAWN, "in")
keyp(m, 18.6, AIR_LOOSE, "out3")
keyp(m, 21.5, P(AIR_DRAWN, la=arm([0.3, -0.05, 0.3], 110)))
fpw(m, 0.5, FP_BOW, "out3")
for t in (8, 18):
    fpw(m, t + 0.6, FP_BOW_LOOSE, "out3")
    fpw(m, t + 2.5, FP_BOW)
use_skill(m, "sniper_ult")
m = Motion("sniper_ult_land", 19, "세 번째 화살 — 내려앉아 무릎 꿇고, 활을 들어 시위를 끝까지 당김 (커지는 셋째 화살 — 힘이 모여 몸이 떨림)", fade=(0.02, 0.04))
keyp(m, 2.0, P(AIR_DRAWN, la=arm([0.3, -0.05, 0.3], 110)))
keyp(m, 4.6, P(KNEEL_UP, root=[-0.46, 0]), "out3")
keyp(m, 6.0, KNEEL_UP)
keyp(m, 13.0, KNEEL_FULL, "linear")
for i, t in enumerate((14.0, 15.2, 16.4, 17.6)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(KNEEL_FULL, body=[2, -26, s * 1.6]))
keyp(m, 18.6, KNEEL_FULL)
fpw(m, 4.6, FP_BOW, "out3")
fpw(m, 13.0, dict(p=(-0.27, 0.15, 0.06)), "linear")
fpw(m, 18.6, dict(p=(-0.26, 0.16, 0.08)))
use_skill(m, "sniper_ult:land")
m = Motion("sniper_ult_release", 16, "세 번째 화살 — 셋째 화살을 놓으며 반동에 몸이 크게 젖혀진 채 무릎 꿇은 자세로 뒤로 밀려나고, 천천히 일어섬", fade=(0.0, 0.12))
keyp(m, 0.6, KNEEL_FULL)
keyp(m, 1.5, P(KNEEL_LOOSE, body=[-22, -12, 0], head=[-8, 0], ra=arm([0.05, 0.35, 0.94], 0, [0, 1, -0.3]), la=arm([-0.35, 0.05, -0.75], 30)), "out3")
keyp(m, 7.0, P(KNEEL_LOOSE, body=[-10, -14, 0], root=[-0.4, 0]))
keyp(m, 14.0, P(BOW_UP, **LEGS_FIGHT), "inout")
fpw(m, 0.6, dict(p=(-0.26, 0.16, 0.08)))
fpw(m, 1.5, dict(p=(-0.25, 0.22, 0.32)), "out3")
fpw(m, 14.0, FP_BOW, "inout")
use_skill(m, "sniper_ult:release")

# ------------------------------------------------------------ 미르크비드(섀도 헌터) (활 — 남보라 · 은빛 · 검은 깃 화살)

# 검은 화살 (효과: 0 ~ 14 움직이는 쪽으로 3칸 달림 · 0 · 4 · 8 에 검은 화살 — 다리는 걸음에 맡김)
RUN_DRAWN = P(BOW_DRAWN, body=[14, -22, 0], head=[-8, 0])
RUN_LOOSE = P(BOW_LOOSE, body=[12, -16, 0], head=[-6, 0])
m = Motion("shadowhunter_black", 14, "검은 화살 — 몸을 앞으로 숙여 달리며 시위를 당겼다 놓기를 세 번 (검은 화살 세 발)", fade=(0.02, 0.12))
keyp(m, 0.2, RUN_DRAWN, "out3")
fpw(m, 0.2, FP_BOW, "out3")
for t in (0, 4, 8):
    keyp(m, t + 0.8, RUN_LOOSE, "out3")
    fpw(m, t + 0.8, FP_BOW_LOOSE, "out3")
    if t < 8:
        keyp(m, t + 2.2, P(RUN_DRAWN, la=arm([0.3, 0.1, 0.45], 115)))
        keyp(m, t + 3.6, RUN_DRAWN, "in")
        fpw(m, t + 2.2, FP_BOW)
keyp(m, 12.5, BOW_UP)
fpw(m, 12.5, FP_BOW)
use_skill(m, "shadowhunter_black")


# 그림자 구르기 (효과: 0 ~ 8 옆으로 4칸 구름 · 4 에 그림자 화살 — 오른쪽 = 기본, 왼쪽은 단계 열쇠 left 로 서버가 1틱에 다시 보냄)
def roll(name, side, key):
    s = 1 if side > 0 else -1
    dive = P(BOW_UP, body=[30, -20 + s * 14, s * 58], head=[-10, s * 6], la=bent([0.25, -0.4, 0.6], 110, (0, 1, 0.3)), root=[-0.42, 0])
    low = P(BOW_DRAWN, body=[16, -22 + s * 8, s * 22], head=[-6, 0], root=[-0.5, 0])
    shot = P(BOW_LOOSE, body=[14, -18 + s * 8, s * 18], head=[-6, 0], root=[-0.5, 0])
    m = Motion(name, 14, "그림자 구르기 — " + ("오른" if s > 0 else "왼") + "쪽으로 몸을 던져 낮게 구르다, 한가운데서 웅크린 채 시위를 당겨 그림자 화살을 쏘고 일어섬",
               fade=(0.02, 0.12))
    keyp(m, 0.6, P(BOW_UP, body=[14, -20, s * 18], root=[-0.2, 0]), "out3")
    keyp(m, 2.4, dive, "out3")
    keyp(m, 3.6, low, "out")
    keyp(m, 4.6, shot, "out3")
    keyp(m, 7.4, P(shot, body=[18, -20, s * 6], root=[-0.42, 0]))
    keyp(m, 12.5, BOW_UP, "inout")
    fpw(m, 0.6, FP_LOW_BOW, "out3")
    fpw(m, 3.6, FP_BOW, "out")
    fpw(m, 4.6, FP_BOW_LOOSE, "out3")
    fpw(m, 12.5, FP_BOW, "inout")
    use_skill(m, key)


roll("shadowhunter_roll", 1, "shadowhunter_roll")
roll("shadowhunter_roll_left", -1, "shadowhunter_roll:left")

# 저주 번짐 (효과: 0 ~ 6 그림자가 활로 모여 듦 (시위를 당김) → 6 에 저주 화살 → 36 문양이 터짐)
SPREAD_FULL = P(BOW_DRAWN, body=[-4, -26, 0], head=[4, 0], la=arm([0.12, 0.08, -0.42], 150), **LEGS_WIDE)
m = Motion("shadowhunter_spread", 18, "저주 번짐 — 다리를 벌려 몸을 낮추고 그림자가 모이는 동안 시위를 끝까지 당겨, 저주 화살을 놓으며 앞으로 몸을 내밂", fade=(0.03, 0.12))
keyp(m, 1.0, P(BOW_UP, body=[4, -22, 0], **LEGS_WIDE), "out3")
keyp(m, 5.6, SPREAD_FULL, "in")
keyp(m, 6.7, P(BOW_LOOSE, body=[10, -14, 0], head=[-4, 0], root=[-0.08, 0.12], **LEGS_WIDE), "out3")
keyp(m, 10.5, P(BOW_LOOSE, body=[8, -16, 0], **LEGS_WIDE))
keyp(m, 15.5, BOW_UP, "inout")
fpw(m, 1.0, FP_BOW, "out3")
fpw(m, 5.6, dict(p=(-0.27, 0.12, -0.04)), "in")
fpw(m, 6.7, dict(p=(-0.25, 0.1, 0.18)), "out3")
fpw(m, 15.5, FP_BOW, "inout")
use_skill(m, "shadowhunter_spread")

# 그믐밤 (태세 6초 — 효과: 둘레에 그림자가 퍼지고 하늘에 그믐달 · 둘레 적이 나를 놓침)
HUNT_LOW = P(body=[18, -10, 0], head=[-14, 0], ra=arm([0.25, -0.55, 0.8], 10, [0.1, 0.6, 0.8]), la=bent([-0.3, -0.5, 0.6], 60, (0, 1, 0.4)), rl=[30, 0, 10],
             ll=[-34, 0, -10], rk=50, lk=60, root=[-0.3, 0])  # 활을 낮게 든 채 웅크린 사냥꾼
m = Motion("shadowhunter_moonless", 26, "그믐밤 — 왼손을 하늘로 들어 달을 가리고, 크게 휘둘러 내리며 그림자를 둘레로 퍼뜨린 뒤 활을 낮게 든 채 웅크려 숨음", fade=(0.04, 0.12))
keyp(m, 3.0, P(body=[-10, -6, 0], head=[-30, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(A_RAISE), **LEGS_WIDE), "out3")
keyp(m, 8.0, P(body=[-8, 4, 0], head=[-26, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(arm([0.6, 0.75, -0.25], 8)), **LEGS_WIDE))
keyp(m, 12.0, P(body=[12, -14, 0], head=[-8, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(A_SPREAD_DOWN), **LEGS_WIDE), "out3")
keyp(m, 17.0, HUNT_LOW, "inout")
keyp(m, 24.0, P(HUNT_LOW, body=[16, -12, 0]))
fpw(m, 3.0, FP_LOW_BOW, "out3")
fpw(m, 17.0, dict(p=(-0.22, -0.05, 0.05)), "inout")
use_skill(m, "shadowhunter_moonless")

# 어둠숲의 사냥 (효과: 0 ~ 10 뒤로 1.5칸 뛰어 물러남 · 4 에 하늘로 검은 화살 → 10 가시덤불 · 저주 → 16 ~ 46 검은 화살 물결 → 44 단계 hunt · 50 사냥의 화살 · 58 거대한 검은 화살)
m = Motion("shadowhunter_ult", 22, "어둠숲의 사냥 — 뒤로 뛰어 물러나며 하늘로 검은 화살을 쏘아 올리고, 내려앉아 왼손을 앞으로 휘둘러 어둠숲을 부른 뒤 낮게 겨눔", fade=(0.03, 0.08))
keyp(m, 0.5, P(SKY_DRAW, body=[0, -20, 0], root=[-0.2, 0]), "out3")
keyp(m, 3.4, SKY_DRAW, "in")
keyp(m, 4.6, SKY_LOOSE, "out3")
keyp(m, 9.0, P(SKY_LOOSE, body=[-6, -16, 0], head=[-12, 0]))
keyp(m, 12.5, P(body=[14, -4, 0], head=[-6, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(arm([0.2, 0.15, 0.97], 4)), **LEGS_WIDE), "out3")
keyp(m, 16.0, P(body=[16, -2, 0], head=[-4, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(arm([0.35, -0.3, 0.88], 6)), **LEGS_WIDE))
keyp(m, 21.0, P(BOW_UP, body=[8, -20, 0], **LEGS_WIDE), "inout")
fpw(m, 0.5, FP_SKY, "out3")
fpw(m, 4.6, dict(p=(-0.25, 0.36, 0.18)), "out3")
fpw(m, 12.5, FP_LOW_BOW, "out3")
fpw(m, 21.0, FP_BOW, "inout")
use_skill(m, "shadowhunter_ult")
m = Motion("shadowhunter_ult_hunt", 20, "어둠숲의 사냥 끝 — 하늘로 활을 들어 사냥의 화살을 끝까지 당겨 쏘아 올리고, 화살이 갈라져 내리꽂히는 순간 왼 주먹을 땅으로 내리찍음",
           fade=(0.03, 0.12))
keyp(m, 1.0, P(SKY_DRAW, la=arm([0.25, 0.45, -0.1], 115)), "out3")
keyp(m, 5.6, SKY_DRAW, "in")
keyp(m, 6.7, SKY_LOOSE, "out3")
keyp(m, 11.0, P(body=[-6, -6, 0], head=[-20, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(A_RAISE), **LEGS_WIDE), "out")
keyp(m, 14.2, P(body=[26, -6, 0], head=[8, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(arm([0.25, -0.85, 0.45], 4)), rl=[24, 0, 10], ll=[-30, 0, -10],
                rk=46, lk=58, root=[-0.32, 0]), "out3")
keyp(m, 18.5, P(BOW_UP, body=[8, -20, 0], **LEGS_WIDE), "inout")
fpw(m, 1.0, FP_SKY, "out3")
fpw(m, 6.7, dict(p=(-0.25, 0.36, 0.18)), "out3")
fpw(m, 11.0, FP_LOW_BOW, "out")
fpw(m, 18.5, FP_BOW, "inout")
use_skill(m, "shadowhunter_ult:hunt")
