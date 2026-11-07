"""0.51.0 2차 직업 스킬 동작 — 궁수 각성 스나이퍼 · 반전 배틀 아처 (재설계안 2판 2026-11-06 — 섀도 헌터 대신 배틀 아처. 틱 0 = 스킬이 나간 틱, 효과의
later · every 와 같은 틱 — SniperSkills · BattlearcherSkills). 둘 다 활 (오른손에 활 · 왼손이 시위를 당김 — 1차 궁수 job_motions_b 의 BOW_UP · BOW_DRAWN · BOW_LOOSE
와 같은 꼴). 몸이 뜨는 동안의 다리는 job_motions.AIR (스네어 샷의 반동 · 팰컨 다이브). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄 (JobKit.motion —
하트시커의 놓음 (다시 누름 · 저절로) · 데드아이의 한 발 (누를 때마다 · 저절로) · 퀴버 스톰의 마지막 화살비)."""
import rune_motion_kit as K
from job_motions_b import BOW_DRAWN, BOW_LOOSE, BOW_UP, FP_BOW, FP_BOW_LOOSE
from rune_motion_kit import A_RAISE, A_SPREAD_DOWN, L, LEGS_FIGHT, LEGS_KNEEL, LEGS_WIDE, P, arm, bent, fpw, keyp, use_skill  # noqa: F401

Motion = K.Motion

# 공용 활 자세 (몸이 기울거나 무릎 꿇은 꼴)
KNEEL_UP = P(BOW_UP, body=[4, -22, 0], **LEGS_KNEEL)
KNEEL_FULL = P(BOW_DRAWN, body=[2, -26, 0], head=[2, 0], la=arm([0.15, 0.06, -0.38], 148), **LEGS_KNEEL)
KNEEL_LOOSE = P(BOW_LOOSE, body=[-2, -16, 0], **LEGS_KNEEL)
WIDE_FULL = P(BOW_DRAWN, body=[-2, -26, 0], head=[3, -3], la=arm([0.15, 0.08, -0.42], 150), **LEGS_WIDE)  # 다리를 벌려 끝까지 당김
SKY_DRAW = P(body=[-16, -20, 0], head=[-30, 0], ra=arm([0.05, 0.82, 0.57], 0, [0, 1, -0.45]), la=arm([0.2, 0.5, -0.32], 140), **LEGS_WIDE)  # 하늘로 당김
SKY_LOOSE = P(body=[-14, -16, 0], head=[-28, 0], ra=arm([0.05, 0.78, 0.62], 2, [0, 1, -0.35]), la=arm([-0.3, 0.1, -0.6], 40), **LEGS_WIDE)  # 하늘로 놓음
FP_SKY = dict(p=(-0.25, 0.32, 0.1))
FP_LOW_BOW = dict(p=(-0.2, -0.12, 0.08))
FP_FULL = dict(p=(-0.27, 0.12, -0.04))

# ------------------------------------------------------------ 스나이퍼 (활 — 밝은 연두 · 금빛 · 흰 섬광)

# 하트시커 (효과: 0 ~ 20 끝까지 당김 — 다시 누르면 그 틱에 놓음 (단계 release) · 안 누르면 20 에 저절로)
m = Motion("sniper_heart", 28, "하트시커 — 다리를 넓게 벌려 버티고 활을 들어 시위를 천천히 끝까지 당김 (몸이 뒤로 비틀림) · 다 당긴 채 숨을 멈추고 미세하게 떨며 겨눔",
           fade=(0.03, 0.1))
keyp(m, 1.5, P(BOW_UP, body=[2, -20, 0], **LEGS_WIDE), "out3")
keyp(m, 5.0, P(BOW_DRAWN, body=[0, -22, 0], la=arm([0.25, 0.08, 0.1], 130), **LEGS_WIDE))
keyp(m, 18.5, WIDE_FULL, "inout")
for i, t in enumerate((20.5, 22.5, 24.5, 26.5)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(WIDE_FULL, body=[-2, -26, s * 1.2], head=[3, -3 + s * 0.6]))
fpw(m, 1.5, FP_BOW, "out3")
fpw(m, 18.5, FP_FULL, "inout")
use_skill(m, "sniper_heart")
m = Motion("sniper_heart_release", 14, "하트시커 — 큰 화살을 놓는 순간 반동에 몸이 뒤로 젖혀지며 시위 손이 크게 뒤로 튐 → 천천히 겨눈 자세로", fade=(0.0, 0.12))
keyp(m, 0.0, WIDE_FULL)  # 틱 0 = 당긴 자세 (들어오는 무게 0 — 영상 1: 틱 0 이 쉬는 자세라 놓는 순간 한 장 팔이 내려갔음)
keyp(m, 1.4, P(BOW_LOOSE, body=[-16, -12, 0], head=[-6, 0], la=arm([-0.35, 0.05, -0.8], 30), root=[-0.12, 0], **LEGS_WIDE), "out3")
keyp(m, 6.5, P(BOW_LOOSE, body=[-6, -14, 0], **LEGS_WIDE))
keyp(m, 12.5, P(BOW_UP, **LEGS_FIGHT), "inout")
fpw(m, 0.0, FP_FULL)
fpw(m, 1.4, dict(p=(-0.25, 0.16, 0.26)), "out3")
fpw(m, 12.5, FP_BOW, "inout")
use_skill(m, "sniper_heart:release")

# 스네어 샷 (효과: 0 ~ 2 재빨리 당김 → 2 에 그물 화살 · 같은 틱 반동으로 뒤로 6칸 (1.2칸 높이) → 12 내려앉음)
RECOIL_AIR = P(BOW_LOOSE, body=[-22, -14, 0], head=[10, 0], la=arm([-0.35, 0.1, -0.8], 30), root=[-0.15, 0])
m = Motion("sniper_snare", 18, "스네어 샷 — 활을 낮게 재빨리 당겨 그물 화살을 쏘고, 반동에 몸이 뒤로 젖혀진 채 뒤로 날아가 웅크려 내려앉음", fade=(0.02, 0.12))
keyp(m, 0.4, P(BOW_DRAWN, body=[12, -20, 0], head=[-6, 0]), "out3")
keyp(m, 2.6, RECOIL_AIR, "out3")
keyp(m, 8.0, P(RECOIL_AIR, body=[-10, -16, 0], head=[4, 0]))
keyp(m, 12.4, P(BOW_UP, body=[16, -18, 0], root=[-0.32, 0]), "out")
keyp(m, 16.5, BOW_UP, "inout")
fpw(m, 0.4, FP_LOW_BOW, "out3")
fpw(m, 2.6, dict(p=(-0.25, 0.18, 0.22)), "out3")
fpw(m, 12.4, FP_BOW, "out")
use_skill(m, "sniper_snare")

# 트릭 샷 (효과: 0 ~ 3 활을 비스듬히 눕혀 당김 → 3 에 화살 → 4 · 14 · 24 튐)
TRICK_DRAWN = P(BOW_DRAWN, body=[2, -30, -10], head=[0, 4], ra=arm([0.1, 0.32, 1], 0, [0.6, 0.8, 0]), la=arm([0.3, 0.2, -0.25], 140))  # 활을 옆으로 눕힘
TRICK_LOOSE = P(BOW_LOOSE, body=[-6, -24, -8], head=[0, 4], ra=arm([0.1, 0.36, 1], 0, [0.6, 0.8, -0.2]), la=arm([-0.25, 0.4, -0.7], 30))
WATCH = P(body=[-4, -10, 0], head=[-10, 6], ra=arm([0.25, -0.35, 0.85], 10, [0.2, 1, 0.3]), la=L(arm([0.35, 0.4, 0.85], 4)), **LEGS_FIGHT)  # 활을 내리고 왼손으로 튄 화살을 가리킴
m = Motion("sniper_trick", 28, "트릭 샷 — 활을 옆으로 눕혀 비틀어 당겨 쏘고, 활을 빙글 내리며 왼손 손가락으로 튀어 오르는 화살을 따라 가리킴", fade=(0.02, 0.12))
keyp(m, 0.4, P(TRICK_DRAWN, la=arm([0.3, 0.2, 0.2], 120)), "out3")
keyp(m, 2.6, TRICK_DRAWN, "in")
keyp(m, 3.5, TRICK_LOOSE, "out3")
keyp(m, 8.0, P(WATCH, head=[-6, 2]), "inout")
keyp(m, 14.5, P(WATCH, head=[-16, 8], la=L(arm([0.3, 0.62, 0.72], 4))))
keyp(m, 22.0, P(WATCH, head=[-8, 4]))
keyp(m, 26.5, BOW_UP, "inout")
fpw(m, 0.4, FP_BOW, "out3")
fpw(m, 3.5, FP_BOW_LOOSE, "out3")
fpw(m, 8.0, FP_LOW_BOW, "inout")
fpw(m, 26.5, FP_BOW, "inout")
use_skill(m, "sniper_trick")

# 헌터스 마크 (태세 8초 — 효과: 손 앞 은빛 큰 화살 · 은빛 기운)
m = Motion("sniper_mark", 22, "헌터스 마크 — 왼손을 오른 어깨 뒤 화살통으로 뻗어 은빛 화살을 뽑아 머리 위로 들어 보이고, 시위에 걸어 눈높이로 겨눔", fade=(0.03, 0.12))
keyp(m, 2.5, P(BOW_UP, body=[-4, -12, 0], la=bent([-0.35, 0.75, -0.3], 70, (0, 0, -1)), **LEGS_FIGHT), "out3")
keyp(m, 7.0, P(BOW_UP, body=[-8, -8, 0], head=[-14, 0], la=L(A_RAISE), **LEGS_FIGHT), "out3")
keyp(m, 11.0, P(BOW_UP, body=[-4, -16, 0], head=[-6, 0], la=L(arm([0.45, 0.6, 0.65], 6)), **LEGS_FIGHT))
keyp(m, 15.5, P(BOW_DRAWN, body=[0, -22, 0], **LEGS_WIDE), "inout")
keyp(m, 20.5, P(BOW_UP, **LEGS_WIDE))
fpw(m, 2.5, FP_LOW_BOW, "out3")
fpw(m, 15.5, FP_BOW, "inout")
use_skill(m, "sniper_mark")

# 사일런트 아울 (2026-11-07 재설계안 3판 — 데드아이 대신, 효과: 0 ~ 40 조준 (등 뒤 위 부엉이 정령 · 조여 드는 과녁) — 20 부터 다시 누르면 한 발 (단계 shot) · 40 이 지나면 저절로)
m = Motion("sniper_ult", 44, "사일런트 아울 — 한쪽 무릎을 꿇고 활을 세워 끝까지 당긴 채 숨을 죽이고 미동 없이 겨눔 (저격 자세)", fade=(0.03, 0.1))
keyp(m, 1.5, P(KNEEL_UP, root=[-0.3, 0]), "out3")
keyp(m, 6.0, KNEEL_FULL, "inout")
for i, t in enumerate(range(12, 44, 8)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(KNEEL_FULL, body=[2 + s * 0.6, -26, 0], head=[2, s * 0.5]))
fpw(m, 1.5, FP_BOW, "out3")
fpw(m, 6.0, dict(p=(-0.27, 0.15, 0.06)), "inout")
use_skill(m, "sniper_ult")
m = Motion("sniper_ult_shot", 18, "사일런트 아울 — 부엉이 정령이 된 화살을 소리 없이 놓고, 반동에 무릎 꿇은 몸이 조금 젖혀졌다가 활을 내리며 천천히 일어섬", fade=(0.0, 0.1))
keyp(m, 0.0, KNEEL_FULL)  # 틱 0 = 무릎 꿇고 당긴 자세
keyp(m, 1.3, P(KNEEL_LOOSE, body=[-14, -12, 0], head=[-4, 0], la=arm([-0.35, 0.05, -0.75], 30), root=[-0.34, 0]), "out3")
keyp(m, 7.0, P(KNEEL_LOOSE, body=[-6, -14, 0], la=bent([-0.3, 0.7, -0.35], 70, (0, 0, -1))))
keyp(m, 13.0, P(KNEEL_UP, root=[-0.3, 0]), "inout")
keyp(m, 17.5, P(KNEEL_UP, body=[0, -6, 0], root=[-0.1, 0]))
fpw(m, 0.0, dict(p=(-0.27, 0.15, 0.06)))
fpw(m, 1.3, dict(p=(-0.25, 0.22, 0.32)), "out3")
fpw(m, 13.0, FP_BOW, "inout")
use_skill(m, "sniper_ult:shot")

# ------------------------------------------------------------ 배틀 아처 (활 — 폭풍 하늘빛 · 번개 노랑)

# 호넷 스웜 (효과: 0 ~ 2 어깨 위에 화살 고리 → 2 에 사방으로 쏘아 올림 → 7 꽂힘)
m = Motion("battlearcher_swarm", 16, "호넷 스웜 — 활을 머리 위로 들어 끝까지 당겼다 하늘로 쏘아 올리며 몸을 한 바퀴 휘돌리고, 활을 앞으로 내려 겨눔", fade=(0.02, 0.12))
keyp(m, 0.3, P(SKY_DRAW, body=[-12, -14, 0]), "out3")
keyp(m, 1.8, SKY_DRAW, "in")
keyp(m, 2.6, P(SKY_LOOSE, spin=-120), "out3")
keyp(m, 5.0, P(SKY_LOOSE, body=[-8, -10, 0], spin=-260))
keyp(m, 8.0, P(BOW_UP, body=[6, -18, 0], spin=-360, **LEGS_WIDE), "out")
keyp(m, 13.5, P(BOW_UP, spin=-360, **LEGS_FIGHT), "inout")
fpw(m, 0.3, FP_SKY, "out3")
fpw(m, 2.6, dict(p=(-0.25, 0.36, 0.18)), "out3")
fpw(m, 8.0, FP_BOW, "out")
use_skill(m, "battlearcher_swarm")

# 팰컨 다이브 (효과: 0 ~ 12 높이 뛰어올라 적 무리 가운데로 → 12 내려앉으며 둘레에 화살)
DIVE_AIR = P(BOW_DRAWN, body=[30, -20, 0], head=[-14, 0], ra=arm([0.05, -0.45, 0.9], 0, [0, 1, 0.3]), la=arm([0.25, -0.25, 0.25], 130))  # 공중에서 아래로 겨눔
LAND_LOOSE = P(BOW_LOOSE, body=[24, -40, 0], head=[-8, 0], ra=arm([0.35, -0.3, 0.88], 0, [0, 1, 0.2]), la=arm([-0.35, -0.1, -0.6], 30), rl=[34, 0, 14], ll=[-30, 0, -14],
               rk=62, lk=40, root=[-0.4, 0])  # 낮게 내려앉아 활을 휘둘러 쏨
m = Motion("battlearcher_dive", 24, "팰컨 다이브 — 웅크렸다 앞으로 높이 뛰어올라 공중에서 몸을 숙여 아래를 겨누고, 적 한가운데 낮게 내려앉으며 활을 크게 휘둘러 둘레로 쏨",
           fade=(0.02, 0.12))
keyp(m, 0.6, P(BOW_UP, body=[22, -18, 0], root=[-0.3, 0]), "out3")
keyp(m, 3.5, P(DIVE_AIR, la=arm([0.3, -0.1, 0.4], 110)), "out3")
keyp(m, 10.6, DIVE_AIR, "in")
keyp(m, 12.5, LAND_LOOSE, "out3")
keyp(m, 15.0, P(LAND_LOOSE, body=[20, 30, 0], ra=arm([0.4, -0.25, 0.88], 0, [0, 1, 0.2])), "out")
keyp(m, 18.5, P(LAND_LOOSE, body=[14, 0, 0], root=[-0.25, 0]))
keyp(m, 22.5, P(BOW_UP, **LEGS_FIGHT), "inout")
fpw(m, 0.6, FP_BOW, "out3")
fpw(m, 3.5, FP_LOW_BOW, "out3")
fpw(m, 12.5, dict(p=(-0.2, -0.05, 0.2)), "out3")
fpw(m, 22.5, FP_BOW, "inout")
use_skill(m, "battlearcher_dive")

# 스플린터 샷 (효과: 0 ~ 6 활에 빛이 모임 · 끝까지 당김 → 6 에 부채꼴로 터짐)
COIL = P(BOW_DRAWN, body=[-10, -34, 0], head=[4, 4], la=arm([0.15, 0.08, -0.5], 152), rl=[-8, 0, 14], ll=[18, 0, -14], rk=18, lk=26, root=[-0.12, 0])  # 몸을 뒤로 감아 당김
BLAST = P(BOW_LOOSE, body=[18, -8, 0], head=[-4, 0], ra=arm([0.0, 0.08, 1], 0, [0, 1, -0.1]), la=arm([-0.35, 0.05, -0.75], 25), rl=[-24, 0, 12], ll=[30, 0, -12], rk=32,
              lk=12, root=[0.12, -0.1])  # 앞으로 내디디며 터뜨림
m = Motion("battlearcher_splinter", 20, "스플린터 샷 — 몸을 뒤로 비틀어 감으며 시위를 끝까지 당겼다가, 앞으로 크게 내디디며 화살을 부채꼴로 터뜨림 (반동에 활이 튐)", fade=(0.02, 0.12))
keyp(m, 1.0, P(BOW_UP, body=[-4, -24, 0], **LEGS_WIDE), "out3")
keyp(m, 5.6, COIL, "in")
keyp(m, 6.6, BLAST, "out3")
keyp(m, 9.0, P(BLAST, ra=arm([0.0, 0.3, 0.95], 0, [0, 1, -0.3])))
keyp(m, 14.0, P(BLAST, body=[8, -12, 0]))
keyp(m, 18.5, P(BOW_UP, **LEGS_FIGHT), "inout")
fpw(m, 1.0, FP_BOW, "out3")
fpw(m, 5.6, FP_FULL, "in")
fpw(m, 6.6, dict(p=(-0.25, 0.16, 0.3)), "out3")
fpw(m, 18.5, FP_BOW, "inout")
use_skill(m, "battlearcher_splinter")

# 스톰 스트라이드 (태세 8초 — 효과: 발밑 폭풍 바람 두 겹 · 회오리 · 번개 불티)
m = Motion("battlearcher_stride", 18, "스톰 스트라이드 — 발을 구르며 활을 머리 위로 크게 휘돌려 바람을 일으키고, 몸을 낮춰 달릴 자세로 겨눔", fade=(0.03, 0.12))
keyp(m, 1.5, P(BOW_UP, body=[-6, -10, 0], ra=arm([0.2, 0.9, 0.3], 0, [0, 0.3, 1]), la=L(A_SPREAD_DOWN), **LEGS_WIDE), "out3")
keyp(m, 4.5, P(BOW_UP, body=[-6, 20, 0], ra=arm([-0.6, 0.75, 0.2], 0, [0, 0.3, 1]), la=L(A_SPREAD_DOWN), **LEGS_WIDE))
keyp(m, 7.5, P(BOW_UP, body=[10, -10, 0], ra=arm([0.4, 0.2, 0.9], 0, [0, 1, 0]), la=bent([-0.3, -0.5, 0.6], 60, (0, 1, 0.4)), rl=[30, 0, 10], ll=[-26, 0, -10],
               rk=40, lk=36, root=[-0.18, 0]), "out3")
keyp(m, 12.0, P(BOW_DRAWN, body=[14, -20, 0], head=[-8, 0], rl=[24, 0, 8], ll=[-22, 0, -8], rk=30, lk=28, root=[-0.12, 0]))
keyp(m, 16.5, P(BOW_UP, **LEGS_FIGHT), "inout")
fpw(m, 1.5, FP_SKY, "out3")
fpw(m, 7.5, FP_LOW_BOW, "out3")
fpw(m, 12.0, FP_BOW)
use_skill(m, "battlearcher_stride")

# 퀴버 스톰 (효과: 0 ~ 62 2틱마다 화살 물결 — 걸으며 쏨 · 10 ~ 60 피해 → 61 단계 final)
STORM_DRAWN = P(BOW_DRAWN, body=[8, -20, 0], head=[-4, 0], **LEGS_WIDE)
STORM_LOOSE = P(BOW_LOOSE, body=[4, -16, 0], head=[-4, 0], **LEGS_WIDE)
m = Motion("battlearcher_ult", 62, "퀴버 스톰 — 다리를 벌려 버티고 활을 앞으로 겨눈 채 쉬지 않고 시위를 당겼다 놓으며 (몸이 좌우로 조금씩 흔들림) 부채꼴로 화살을 퍼부음",
           fade=(0.03, 0.06))
keyp(m, 0.5, STORM_DRAWN, "out3")
fpw(m, 0.5, FP_BOW, "out3")
for i, t in enumerate(range(2, 60, 4)):
    s = 1 if i % 2 else -1
    keyp(m, t + 0.6, P(STORM_LOOSE, body=[4, -16 + s * 6, 0]), "out3")
    fpw(m, t + 0.6, FP_BOW_LOOSE, "out3")
    keyp(m, t + 2.2, P(STORM_DRAWN, body=[8, -20 + s * 6, 0]))
    fpw(m, t + 2.2, FP_BOW)
use_skill(m, "battlearcher_ult")
m = Motion("battlearcher_ult_final", 20, "퀴버 스톰 끝 — 활을 하늘로 들어 마지막 화살 다발을 쏘아 올리고, 화살비가 쏟아지는 순간 왼팔을 앞으로 휘둘러 내림", fade=(0.02, 0.12))
keyp(m, 0.8, SKY_DRAW, "out3")
keyp(m, 1.8, SKY_LOOSE, "out3")
keyp(m, 6.0, P(body=[-6, -6, 0], head=[-20, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(A_RAISE), **LEGS_WIDE), "out")
keyp(m, 9.5, P(body=[20, -6, 0], head=[4, 0], ra=arm([0.3, -0.7, 0.4], 10, [0.1, 0.8, 0.5]), la=L(arm([0.2, -0.3, 0.93], 4)), rl=[24, 0, 10], ll=[-30, 0, -10],
               rk=40, lk=50, root=[-0.24, 0]), "out3")
keyp(m, 18.5, P(BOW_UP, body=[8, -20, 0], **LEGS_WIDE), "inout")
fpw(m, 0.8, FP_SKY, "out3")
fpw(m, 1.8, dict(p=(-0.25, 0.36, 0.18)), "out3")
fpw(m, 9.5, FP_LOW_BOW, "out")
fpw(m, 18.5, FP_BOW, "inout")
use_skill(m, "battlearcher_ult:final")
