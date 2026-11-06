"""0.51.0 2차 직업 스킬 동작 — 창술사 2차 (2026-11-06 사용자 "다음 작업"): 각성 스톰 랜서 · 반전 드라군 (틱 0 = 스킬이 나간 틱, 효과의 later · every 와 같은 틱 —
StormlancerSkills · DragoonSkills). 둘 다 창 (오른손 — 1차 창술사 job_motions_b 의 당김 STORM_PULL · 꿰뚫음 PIERCE · 비틀어 당김 COIL · 내달림 CHARGE 와 같은
꼴, 왼손은 자루를 받침). 몸 쓰는 직업이라 제 몸 · 제 창 동작만 (바람 · 용의 혼 · 불은 그림). 단계 열쇠 (스킬 id:단계) 의 동작은 서버가 그 순간에 다시 보냄
(JobKit.motion · JobRecast.motion — 게일 드라이브의 되찌르기 :back, 렌딩 게일의 뽑음 :rip, 아이 오브 더 스톰의 찌름 :thrust · 마지막 :final, 하이 점프의 내리꽂음 :dive).
단계 동작의 틱 0 열쇠는 앞 동작의 끝 자세 (한 장 쉬는 자세로 튀지 않게). 드라군은 몸이 뜨는 동안 다리를 이 파일에서 직접 (softLegs 끔 · 걸음 다리 끔)."""
import rune_motion_kit as K
from job_motions_b import CHARGE, COIL, FPW_PULL, PIERCE, STORM_LA, STORM_PULL
from job_motions_d import F_AIR, F_CROUCH, F_PLUNGE, FPW_REV_UP, REV_RAISE, S_IMPACT, SF_RISE, first, impacts
from rune_motion_kit import (FP_DOWN, FP_LOW, FP_PALM, FPL_RAISE2H, FPW_DOWN, FPW_FWD, FPW_LOW, FPW_SKY, LEGS_WIDE, RAISE2H, W_READY, P, arm, bent, fpl, fpw,
                             keyp, nudge, spin_right, strike, use_skill)

Motion = K.Motion

# 공용 창 자세
READY_L = P(W_READY, body=[8, 6, 0])  # 끝 자세 (다음 단계 동작의 틱 0)
# 찌름: 겨누는 쪽 (dx 오른쪽 · dy 위) 으로 크게 내지름 (1차 사우전드 스팅의 한 번 찌르기 꼴)


def thrust_pose(dx=0.0, dy=0.04, deep=1.0):
    return P(body=[16 + 6 * deep, -14 - dx * 40, 0], head=[-8 - dy * 20, 8 + dx * 40], ra=arm([dx, dy, 1], 2, [dx, dy, 1]), la=STORM_LA,
             root=[-0.2 - 0.06 * deep, 0.22 + 0.12 * deep], rl=[24, 0, 6], ll=[-34 - 8 * deep, 0, -4], rk=26, lk=46 + 6 * deep)


def fpw_thrust(dx=0.0, dy=0.04):
    return dict(p=(-0.61 + dx * 0.6, 0.33 + dy * 0.6, -0.33), blade=(-0.12 + dx, 0.05 + dy, 0.99), edge=(0, -1, 0))


# ------------------------------------------------------------ 스톰 랜서 (창 — 옥빛 바람 · 흰빛)

# 크로스 게일 (효과: 2 왼쪽으로 비껴 찌름 → 6 오른쪽으로 비껴 찌름 → 두 바람이 앞에서 엇갈려 소용돌이 → 26 터짐). 터지는 틱에 창끝으로 엇갈린 자리를 가리키며 홱 비틂
m = Motion("stormlancer_cross", 30, "크로스 게일 — 창을 허리로 당겼다가 왼쪽 앞으로 비껴 찌르고, 곧바로 되당겨 오른쪽 앞으로 비껴 찌름 → 창끝으로 엇갈린 자리를 겨눈 채 버티다가 "
           "터지는 순간 창을 홱 비틀어 당김", fade=(0.03, 0.1))
keyp(m, 0.8, P(STORM_PULL, body=[10, 26, 0]), "out3")
keyp(m, 2.0, thrust_pose(-0.2, 0.04), "out3")
keyp(m, 4.2, P(STORM_PULL, body=[12, 10, 0]), "in")
keyp(m, 6.0, thrust_pose(0.22, 0.04), "out3")
keyp(m, 9.0, P(thrust_pose(0.04, 0.04, 0.6), root=[-0.2, 0.2]))
keyp(m, 22.0, P(thrust_pose(0.0, 0.06, 0.5), root=[-0.2, 0.18]), "inout")
keyp(m, 24.6, P(COIL, body=[8, 24, 0], root=[-0.26, -0.04]), "out3")
keyp(m, 26.0, thrust_pose(0.0, 0.05, 1.2), "out3")
keyp(m, 29.6, READY_L)
fpw(m, 0.8, FPW_PULL, "out3")
fpw(m, 2.0, fpw_thrust(-0.2, 0.04), "out3")
fpw(m, 4.2, FPW_PULL, "in")
fpw(m, 6.0, fpw_thrust(0.22, 0.04), "out3")
fpw(m, 22.0, fpw_thrust(0.0, 0.06), "inout")
fpw(m, 24.6, dict(p=(0.05, 0.05, 0.6), blade=(-0.1, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 26.0, fpw_thrust(0.0, 0.05), "out3")
fpw(m, 29.6, FPW_LOW)
use_skill(m, "stormlancer_cross")

# 게일 드라이브 (효과: 0 ~ 5 보는 쪽 6칸을 꿰뚫고 지나감 — 다시 누르면 :back). 창을 옆구리에 끼고 숙여 내달렸다가 미끄러지며 멈춤 (몸이 뜨지 않음 — 제 다리로)
DR_SKID = P(body=[6, 18, 0], head=[-6, -10], ra=arm([0.2, -0.3, 0.9], 30, [0.05, 0.05, 1]), la=arm([-0.5, -0.5, 0.4], 30), rl=[30, 0, 8], ll=[-40, 0, -6], rk=60,
            lk=40, root=[-0.3, -0.1])  # 미끄러지며 멈춤 (뒷발에 무게)
m = Motion("stormlancer_drive", 14, "게일 드라이브 — 창을 옆구리에 끼고 몸을 낮게 숙여 바람을 타고 내달렸다가, 미끄러지며 멈춰 창을 앞으로 겨눔", fade=(0.02, 0.1))
keyp(m, 0.5, P(CHARGE, body=[36, 6, 0]), "out3")
keyp(m, 4.0, P(CHARGE, body=[38, 8, 0], root=[-0.2, 0.34]))
keyp(m, 5.4, DR_SKID, "out")
keyp(m, 9.0, P(DR_SKID, root=[-0.26, -0.06]))
keyp(m, 13.0, READY_L)
fpw(m, 0.5, dict(p=(-0.35, 0.05, -0.1), blade=(-0.1, 0.02, 1), edge=(0, -1, 0)), "out3")
fpw(m, 5.4, FPW_FWD, "out")
fpw(m, 13.0, FPW_LOW)
use_skill(m, "stormlancer_drive")

# 게일 드라이브 :back (다시 누름 — 서버가 표식이 남은 적 쪽으로 돌려 세움 → 2 바람 창)
m = Motion("stormlancer_drive_back", 12, "게일 드라이브 — 돌아서며 창을 깊이 당겼다가 지나온 적들 쪽으로 온 힘으로 내지름", fade=(0.0, 0.1))
first(m, READY_L, FPW_LOW)
keyp(m, 1.0, P(COIL, body=[6, 30, 0]), "out3")
keyp(m, 2.0, thrust_pose(0.0, 0.04, 1.3), "out3")
keyp(m, 6.0, P(PIERCE, root=[-0.28, 0.36]))
keyp(m, 11.0, READY_L)
fpw(m, 1.0, dict(p=(0.05, 0.05, 0.6), blade=(-0.1, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 2.0, fpw_thrust(0.0, 0.04), "out3")
fpw(m, 11.0, FPW_LOW)
use_skill(m, "stormlancer_drive:back")

# 템페스트 드릴 (효과: 0 ~ 6 창끝에 빛이 모임 → 6 ~ 36 앞으로 겨눈 창이 거대한 바람 드릴 · 6 · 16 · 26 갈아냄 → 36 터짐). 두 손으로 창을 앞으로 내밀고 버팀
# (창이 돌며 떨림 — 손목이 조금씩 비틀림) → 터질 때 한 걸음 내지름
DRILL = P(body=[14, -18, 0], head=[-6, 10], ra=arm([0.02, 0.06, 1], 6, [0.0, 0.03, 1]), la=bent([-0.12, -0.08, 0.95], 30, (0, 0.3, 1)), rl=[26, 0, 6],
          ll=[-36, 0, -4], rk=40, lk=50, root=[-0.26, 0.12])  # 두 손으로 창을 앞으로 내밀고 낮게 버팀
m = Motion("stormlancer_drill", 40, "템페스트 드릴 — 창을 허리로 당겼다가 두 손으로 앞으로 길게 내밀어 낮게 버티고, 돌며 떨리는 창을 끝까지 붙든 채 겨누는 쪽을 따라 "
           "천천히 돌다가, 드릴이 터질 때 한 걸음 깊이 내지름", fade=(0.03, 0.1))
keyp(m, 1.0, P(STORM_PULL, body=[10, 20, 0]), "out3")
keyp(m, 4.0, DRILL, "out3")
for i, t in enumerate(range(6, 34, 3)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(DRILL, ra=arm([0.02 + 0.015 * s, 0.06 - 0.01 * s, 1], 6, [0.015 * s, 0.03, 1]), root=[-0.26, 0.12 + 0.01 * s]))
keyp(m, 34.4, P(DRILL, body=[10, -10, 0], root=[-0.24, 0.04]), "inout")
keyp(m, 36.0, thrust_pose(0.0, 0.05, 1.3), "out3")
keyp(m, 39.6, READY_L)
fpw(m, 1.0, FPW_PULL, "out3")
fpw(m, 4.0, dict(p=(-0.55, 0.25, -0.4), blade=(-0.1, 0.04, 1), edge=(0, -1, 0)), "out3")
fpw(m, 34.4, dict(p=(-0.5, 0.22, -0.3), blade=(-0.1, 0.04, 1), edge=(0, -1, 0)), "inout")
fpw(m, 36.0, fpw_thrust(0.0, 0.05), "out3")
fpw(m, 39.6, FPW_LOW)
fpl(m, 4.0, nudge(FP_PALM, (0.12, -0.1, 0.05), (10, 20, 0)), "out3")
fpl(m, 34.4, nudge(FP_PALM, (0.12, -0.1, 0.05), (10, 20, 0)))
fpl(m, 39.6, FP_LOW)
use_skill(m, "stormlancer_drill")

# 렌딩 게일 (효과: 0 앞 가로 큰 바람 검기 · 2 앞 적에 바람 창이 박힘 — 다시 누르면 :rip). 창을 오른쪽에서 왼쪽으로 크게 휘둘러 휩씀
m = Motion("stormlancer_rend", 14, "렌딩 게일 — 창을 오른쪽 뒤로 감았다가 앞을 왼쪽까지 크게 휘둘러 휩쓸고, 창끝을 앞으로 돌려 겨눔", fade=(0.03, 0.1))
strike(m, "lance_sweep", 0.0, 1.6, 4.4)
keyp(m, 8.0, P(STORM_PULL, body=[10, 10, 0]))
keyp(m, 13.0, READY_L)
fpw(m, 8.0, FPW_PULL)
fpw(m, 13.0, FPW_LOW)
use_skill(m, "stormlancer_rend")

# 렌딩 게일 :rip (다시 누름 · 창이 닫힘 — 2 박힌 바람 창을 한꺼번에 뽑음). 창을 앞으로 겨눴다가 홱 뒤로 잡아당기며 몸을 젖힘
RIP = P(body=[-14, 30, 0], head=[-10, -16], ra=arm([0.5, 0.1, -0.7], 70, [0.1, 0.1, 1]), la=arm([-0.35, 0.2, 0.9], 20), rl=[-10, 0, 6], ll=[16, 0, -6], rk=30,
        lk=20, root=[-0.12, -0.3])  # 뒤로 잡아당김 (왼손은 앞으로 뻗어 끌어당기는 손짓)
m = Motion("stormlancer_rend_rip", 14, "렌딩 게일 — 창끝을 앞으로 겨눴다가 홱 뒤로 잡아당기며 몸을 젖히고 왼손을 움켜쥐어 박힌 바람 창을 한꺼번에 뽑음", fade=(0.0, 0.1))
first(m, READY_L, FPW_LOW)
keyp(m, 1.0, thrust_pose(0.0, 0.06, 0.4), "out3")
keyp(m, 2.4, RIP, "out3")
keyp(m, 6.0, P(RIP, body=[-10, 26, 0], la=bent([-0.3, 0.0, 0.7], 120, (0.3, 1, 0.2))))
keyp(m, 13.0, READY_L)
fpw(m, 1.0, fpw_thrust(0.0, 0.06), "out3")
fpw(m, 2.4, dict(p=(0.15, 0.0, 0.45), blade=(-0.05, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 13.0, FPW_LOW)
fpl(m, 2.4, nudge(FP_PALM, (0.0, 0.05, -0.1)), "out3")
fpl(m, 6.0, nudge(FP_PALM, (0.05, -0.05, 0.05), (20, 40, 0)))
fpl(m, 13.0, FP_LOW)
use_skill(m, "stormlancer_rend:rip")

# 아이 오브 더 스톰 (효과: 0 ~ 10 창을 높이 들어 폭풍을 모음 → 10 찌르면 폭풍의 눈이 열림 → 14 · 24 · 34 · 44 · 54 찌름 (:thrust — 2틱 전에) → 64 :final → 70 터짐)
STORM_UP = P(RAISE2H, body=[-16, 10, 0], head=[-24, 0], ra=arm([0.05, 0.97, 0.22], 10, [0.05, 0.6, 0.8]), rl=[0, 0, 12], ll=[0, 0, -12], rk=16, lk=16,
             root=[-0.04, 0])  # 창을 높이 들고 창끝을 앞 위로 (폭풍을 모음)
BRACE_S = P(STORM_PULL, body=[12, 16, 0], root=[-0.22, 0.04])  # 버팀 (다음 찌름 준비 — 단계 동작의 틱 0)
m = Motion("stormlancer_ult", 22, "아이 오브 더 스톰 — 다리를 벌려 버티고 창을 하늘 높이 들어 폭풍을 모았다가, 겨눈 곳으로 온 힘으로 내질러 폭풍의 눈을 열고 창을 허리로 당겨 "
           "버팀", fade=(0.03, 0.06))
keyp(m, 2.0, STORM_UP, "out3")
keyp(m, 8.4, P(STORM_UP, body=[-20, 12, 0], head=[-28, 0], root=[-0.06, -0.04]))
keyp(m, 10.0, thrust_pose(0.0, 0.08, 1.3), "out3")
keyp(m, 14.0, P(thrust_pose(0.0, 0.06, 1.0), root=[-0.26, 0.3]))
keyp(m, 20.0, BRACE_S, "inout")
fpw(m, 2.0, FPW_SKY, "out3")
fpw(m, 8.4, FPW_SKY, dp=(0.0, 0.06, 0.1))
fpw(m, 10.0, fpw_thrust(0.0, 0.08), "out3")
fpw(m, 20.0, FPW_PULL, "inout")
fpl(m, 2.0, FPL_RAISE2H, "out3")
fpl(m, 10.0, FP_LOW, "out3")
use_skill(m, "stormlancer_ult")

# 아이 오브 더 스톰 :thrust (서버가 찌르기 2틱 전에 — 0 버팀 → 2 찌름 → 당김)
m = Motion("stormlancer_ult_thrust", 9, "아이 오브 더 스톰 — 버틴 채 폭풍의 눈으로 창을 빠르게 내지르고 다시 허리로 당김", fade=(0.0, 0.04))
first(m, BRACE_S, FPW_PULL)
keyp(m, 0.8, P(COIL, body=[8, 24, 0], root=[-0.26, -0.02]), "out3")
keyp(m, 2.0, thrust_pose(0.04, 0.1, 1.1), "out3")
keyp(m, 4.6, P(thrust_pose(0.02, 0.08, 0.8), root=[-0.24, 0.24]))
keyp(m, 8.6, BRACE_S, "inout")
fpw(m, 0.8, dict(p=(0.05, 0.05, 0.6), blade=(-0.1, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 2.0, fpw_thrust(0.04, 0.1), "out3")
fpw(m, 8.6, FPW_PULL, "inout")
use_skill(m, "stormlancer_ult:thrust")

# 아이 오브 더 스톰 :final (서버가 터지기 6틱 전에 — 0 ~ 6 몸을 크게 비틀어 창을 깊이 당김 → 6 거대한 폭풍 창으로 꿰찌름)
m = Motion("stormlancer_ult_final", 18, "아이 오브 더 스톰 끝 — 몸을 크게 비틀어 창을 깊이 당겼다가 크게 내디디며 거대한 폭풍 창을 온 힘으로 꿰찔러 폭풍을 터뜨림",
           fade=(0.0, 0.12))
first(m, BRACE_S, FPW_PULL)
keyp(m, 3.0, P(COIL, body=[4, 40, 0], root=[-0.34, -0.14]), "out3")
keyp(m, 5.2, P(COIL, body=[2, 44, 0], root=[-0.36, -0.16]))
keyp(m, 6.0, P(PIERCE, body=[24, -26, 0], root=[-0.34, 0.5]), "out3")
keyp(m, 11.0, P(PIERCE, body=[22, -22, 0], root=[-0.32, 0.46]))
keyp(m, 17.4, READY_L)
fpw(m, 3.0, dict(p=(0.08, 0.02, 0.66), blade=(-0.1, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 6.0, fpw_thrust(0.0, 0.05), "out3")
fpw(m, 11.0, fpw_thrust(0.0, 0.05))
fpw(m, 17.4, FPW_LOW)
impacts(m, 6.0)
use_skill(m, "stormlancer_ult:final")

# ------------------------------------------------------------ 드라군 (창 — 금빛 용 · 주홍 불)

# 공중 · 내리꽂기 자세 (스타폴 블레이드의 F_ · S_ 꼴 — 창을 거꾸로 쥐고 두 손으로)
D_LOW = P(body=[30, -40, 0], head=[-14, 20], ra=arm([0.55, -0.55, -0.4], 40, [-0.6, -0.4, -0.7]), la=arm([-0.3, -0.6, 0.4], 30), rl=[-40, 0, 10], ll=[-40, 0, -10],
          rk=84, lk=84, root=[-0.36, 0.0])  # 창을 낮게 오른쪽 뒤로 감고 깊이 웅크림 (자루 끝이 앞)
D_SWEEP = P(body=[22, 20, 0], head=[-8, -10], ra=arm([0.2, -0.45, 0.85], 10, [0.85, -0.3, 0.4]), la=arm([-0.6, -0.4, -0.3], 30), rl=[-36, 0, 10],
            ll=[-30, 0, -10], rk=76, lk=70, root=[-0.32, 0.0])  # 낮게 휩쓸며 돎 (자루가 둘레를 쓸어 감)
D_IMPACT = P(S_IMPACT, body=[40, 0, 0], root=[-0.4, 0.26])  # 한 무릎을 꿇고 거꾸로 쥔 창을 땅에 꽂음
D_HOVER = P(REV_RAISE, body=[-6, 0, 0], head=[14, 0], rl=[-14, 0, 6], rk=30, ll=[4, 0, -6], lk=40, root=[0.0, 0.08])  # 떠 있음 (창을 거꾸로 들고 아래를 내려다봄)

# 웜테일 (효과: 0 ~ 6 창을 낮게 감음 → 6 자루로 둘레를 휩씀 · 띄움 → 7 ~ 17 따라 뛰어오름 → 18 창을 거꾸로 내리꽂음)
m = Motion("dragoon_tail", 26, "웜테일 — 창을 낮게 오른쪽 뒤로 감고 깊이 웅크렸다가 자루로 둘레를 한 바퀴 휩쓸고, 띄운 적을 따라 뛰어올라 창을 거꾸로 머리 위에 든 채 "
           "떨어져 땅에 내리꽂고 한 무릎으로 받은 뒤 일어섬", fade=(0.03, 0.1))
keyp(m, 2.0, D_LOW, "out3")
keyp(m, 4.6, P(D_LOW, body=[32, -48, 0], root=[-0.4, -0.02]))
spin_right(m, (6.0,), 7.6, wind_tick=4.8, fwd=D_SWEEP)
keyp(m, 9.0, P(F_AIR, body=[-10, 0, 0], spin=360), "out")
keyp(m, 13.0, P(F_AIR, body=[-16, 0, 0], head=[10, 0], spin=360))
keyp(m, 16.6, P(F_PLUNGE, spin=360), "in")
keyp(m, 18.0, P(D_IMPACT, spin=360), "in")
keyp(m, 21.0, P(D_IMPACT, body=[36, 0, 0], root=[-0.36, 0.24], spin=360))
keyp(m, 25.6, P(READY_L, spin=360), "inout")
fpw(m, 2.0, FPW_LOW, "out3")
fpw(m, 9.0, FPW_REV_UP, "out")
fpw(m, 16.6, FPW_DOWN, "in")
fpw(m, 21.0, FPW_DOWN)
fpw(m, 25.6, FPW_LOW)
fpl(m, 9.0, FPL_RAISE2H, "out")
fpl(m, 16.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 21.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 25.6, FP_LOW)
m.set(softLegs=False)
impacts(m, 18.0)
use_skill(m, "dragoon_tail")

# 하이 점프 (효과: 0 ~ 2 웅크림 → 2 ~ 10 곧장 7칸 솟구침 → 10 ~ 50 떠 있음 (등 뒤 용의 혼) — 다시 누르면 :dive · 창이 닫히면 저절로)
JUMP_UP = P(RAISE2H, body=[-8, 0, 0], head=[-20, 0], rl=[-6, 0, 4], rk=10, ll=[6, 0, -4], lk=16, root=[0.0, 0.1])  # 창을 하늘로 들고 곧게 솟구침 (다리를 뻗음)
m = Motion("dragoon_jump", 52, "하이 점프 — 창을 세워 쥐고 깊이 웅크렸다가 창을 하늘로 들며 곧장 솟구치고, 하늘에서 창을 거꾸로 쥐어 두 손으로 머리 위에 든 채 아래를 "
           "내려다보며 떠 있음", fade=(0.02, 0.1))
keyp(m, 1.4, F_CROUCH, "out3")
keyp(m, 3.0, JUMP_UP, "out")
keyp(m, 8.0, P(JUMP_UP, body=[-12, 0, 0], head=[-24, 0]))
keyp(m, 11.0, D_HOVER, "inout")
for i, t in enumerate(range(16, 50, 6)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(D_HOVER, body=[-6 + 2 * s, 0, 0], rl=[-14 + 4 * s, 0, 6], ll=[4 - 4 * s, 0, -6], root=[0.0, 0.08 + 0.02 * s]), "inout")
fpw(m, 1.4, FPW_LOW, "out3")
fpw(m, 3.0, FPW_SKY, "out")
fpw(m, 11.0, FPW_REV_UP, "inout")
fpl(m, 3.0, FPL_RAISE2H, "out")
fpl(m, 51.0, FPL_RAISE2H)
m.set(softLegs=False)
use_skill(m, "dragoon_jump")

# 하이 점프 :dive (다시 누름 · 창이 닫힘 — 0 ~ 5 겨눈 곳으로 내리꽂힘 → 5 땅에 꽂음)
DIVE = P(F_PLUNGE, body=[34, 0, 0], head=[20, 0], rl=[-6, 0, 6], rk=20, ll=[10, 0, -6], lk=14, root=[0.0, 0.2])  # 몸을 숙여 창끝을 앞 아래로 — 혜성처럼
m = Motion("dragoon_jump_dive", 16, "하이 점프 — 떠 있던 채 몸을 앞으로 숙여 창끝을 겨눈 곳으로 내리며 혜성처럼 내리꽂히고, 땅에 창을 꽂으며 한 무릎으로 받은 뒤 일어섬",
           fade=(0.0, 0.1))
first(m, D_HOVER, FPW_REV_UP)
keyp(m, 1.6, DIVE, "in")
keyp(m, 5.0, D_IMPACT, "in")
keyp(m, 8.6, P(D_IMPACT, body=[36, 0, 0], root=[-0.36, 0.24]))
keyp(m, 15.4, READY_L, "inout")
fpw(m, 1.6, FPW_DOWN, "in")
fpw(m, 8.6, FPW_DOWN)
fpw(m, 15.4, FPW_LOW)
fpl(m, 0.0, FPL_RAISE2H)
fpl(m, 1.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 8.6, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 15.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 5.0)
use_skill(m, "dragoon_jump:dive")

# 임페일 (효과: 0 ~ 6 창을 크게 당김 → 6 꿰뚫음 → 6 ~ 16 꿴 적을 머리 위로 들어 올림 → 16 ~ 20 앞 땅에 내리꽂음)
HOIST = P(body=[-18, -6, 0], head=[-26, 0], ra=arm([0.05, 0.9, 0.42], 20, [0.05, 0.82, 0.57]), la=bent([0.05, 0.75, 0.6], 40, (0, 0.3, 1)), rl=[-6, 0, 10],
          ll=[18, 0, -10], rk=20, lk=30, root=[-0.08, -0.06])  # 꿴 적을 두 손으로 머리 위 앞까지 들어 올림 (몸을 뒤로 젖힘)
SLAM_FWD = P(body=[46, 0, 0], head=[-10, 0], ra=arm([0.04, -0.55, 0.84], 10, [0.0, -0.75, 0.66]), la=bent([0.02, -0.5, 0.86], 30, (0, 0, 1)), rl=[-30, 0, 10],
             ll=[30, 0, -8], rk=70, lk=40, root=[-0.4, 0.34])  # 앞 땅으로 내리꽂음 (깊이 숙임)
m = Motion("dragoon_impale", 26, "임페일 — 몸을 비틀어 창을 깊이 당겼다가 앞의 적을 꿰뚫고, 꿴 채 두 손으로 머리 위 앞까지 높이 들어 올렸다가 몸을 숙이며 앞 땅에 내리꽂음",
           fade=(0.03, 0.1))
keyp(m, 2.0, P(COIL, body=[6, 36, 0]), "out3")
keyp(m, 5.0, P(COIL, body=[4, 40, 0], root=[-0.34, -0.14]))
keyp(m, 6.0, P(PIERCE, root=[-0.3, 0.42]), "out3")
keyp(m, 8.0, P(PIERCE, body=[18, -18, 0], root=[-0.26, 0.36]))
keyp(m, 13.0, HOIST, "inout")
keyp(m, 16.0, P(HOIST, body=[-22, -4, 0], head=[-30, 0]))
keyp(m, 20.0, SLAM_FWD, "in")
keyp(m, 23.0, P(SLAM_FWD, body=[40, 0, 0], root=[-0.36, 0.3]))
keyp(m, 25.6, READY_L, "inout")
fpw(m, 2.0, dict(p=(0.05, 0.05, 0.6), blade=(-0.1, 0.05, 1), edge=(0, -1, 0)), "out3")
fpw(m, 6.0, fpw_thrust(0.0, 0.05), "out3")
fpw(m, 13.0, FPW_SKY, "inout")
fpw(m, 20.0, FPW_DOWN, "in")
fpw(m, 23.0, FPW_DOWN)
fpw(m, 25.6, FPW_LOW)
fpl(m, 13.0, FPL_RAISE2H, "inout")
fpl(m, 20.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 25.6, FP_LOW)
impacts(m, 6.0, 20.0)
use_skill(m, "dragoon_impale")

# 드래곤 브레스 (효과: 0 창을 땅에 꽂음 · 등 뒤에 용의 혼 → 2 ~ 14 숨을 들이켬 → 14 불길). 창을 앞 땅에 꽂고 두 손으로 짚은 채 가슴을 펴고 고개를 젖혀 포효했다가
# 불길이 나가는 순간 몸을 앞으로 숙여 내뿜음
PLANT_D = P(body=[18, 0, 0], head=[0, 0], ra=arm([0.1, -0.45, 0.88], 30, [0.0, -0.9, 0.42]), la=bent([0.0, -0.4, 0.9], 40, (0, 0, 1)), rl=[10, 0, 10],
            ll=[-20, 0, -10], rk=20, lk=26, root=[-0.12, 0.1])  # 창을 앞 땅에 꽂고 두 손으로 짚음
ROAR = P(PLANT_D, body=[-16, 0, 0], head=[-34, 0], rl=[0, 0, 12], ll=[0, 0, -12], rk=14, lk=14, root=[-0.04, 0.0])  # 가슴을 펴고 고개를 젖혀 포효
BLAST = P(PLANT_D, body=[30, 0, 0], head=[14, 0], rl=[20, 0, 10], ll=[-36, 0, -10], rk=34, lk=46, root=[-0.24, 0.24])  # 앞으로 숙여 내뿜음
m = Motion("dragoon_breath", 24, "드래곤 브레스 — 창을 앞 땅에 꽂고 두 손으로 짚은 채 가슴을 펴고 고개를 젖혀 크게 숨을 들이켰다가, 불길이 나가는 순간 몸을 앞으로 숙여 "
           "내뿜음", fade=(0.03, 0.1))
keyp(m, 1.6, PLANT_D, "out3")
keyp(m, 5.0, ROAR, "inout")
keyp(m, 12.4, P(ROAR, body=[-20, 0, 0], head=[-38, 0]))
keyp(m, 14.0, BLAST, "out3")
keyp(m, 19.0, P(BLAST, body=[26, 0, 0], root=[-0.2, 0.2]))
keyp(m, 23.6, READY_L, "inout")
fpw(m, 1.6, FPW_DOWN, "out3")
fpw(m, 19.0, FPW_DOWN)
fpw(m, 23.6, FPW_LOW)
fpl(m, 1.6, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "out3")
fpl(m, 19.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)))
fpl(m, 23.6, FP_LOW)
use_skill(m, "dragoon_breath")

# 드래곤폴 (효과: 0 ~ 12 웅크려 용의 혼을 두름 → 12 ~ 18 하늘로 솟구침 → 18 ~ 36 겨눈 곳 위로 옮겨 감 → 40 ~ 46 내리꽂힘 → 46 충돌 → 56 불기둥)
ULT_CROUCH = P(F_CROUCH, body=[30, 0, 0], head=[-22, 0], rk=96, lk=96, root=[-0.4, 0.04])
m = Motion("dragoon_ult", 60, "드래곤폴 — 창을 낮게 쥐고 깊이 웅크려 용의 혼을 두르고 하늘로 솟구쳐, 하늘 높이에서 창을 거꾸로 머리 위에 든 채 겨눈 곳 위로 날아가 "
           "몸을 숙여 내리꽂히고, 땅에 창을 꽂으며 한 무릎으로 받았다가 불기둥이 솟을 때 창을 뽑아 하늘로 치켜듦", fade=(0.03, 0.12))
keyp(m, 3.0, F_CROUCH, "out3")
keyp(m, 10.6, ULT_CROUCH)
keyp(m, 12.4, SF_RISE, "out")
keyp(m, 17.0, P(SF_RISE, body=[-22, 0, 0], head=[-32, 0]))
keyp(m, 19.0, F_AIR, "inout")
for i, t in enumerate(range(24, 38, 5)):
    s = 1 if i % 2 else -1
    keyp(m, t, P(F_AIR, body=[-14 + 3 * s, 0, 0], root=[0.0, 0.1 + 0.02 * s]), "inout")
keyp(m, 41.0, P(F_PLUNGE, body=[30, 0, 0], head=[20, 0]), "in")
keyp(m, 46.0, P(D_IMPACT, body=[44, 0, 0], root=[-0.42, 0.28]), "in")
keyp(m, 52.0, P(D_IMPACT, body=[38, 0, 0], root=[-0.38, 0.26]))
keyp(m, 55.0, P(RAISE2H, body=[-14, 0, 0], head=[-24, 0], **LEGS_WIDE), "out3")
keyp(m, 59.4, READY_L, "inout")
fpw(m, 3.0, FPW_LOW, "out3")
fpw(m, 12.4, FPW_SKY, "out")
fpw(m, 19.0, FPW_REV_UP, "inout")
fpw(m, 41.0, FPW_DOWN, "in")
fpw(m, 52.0, FPW_DOWN)
fpw(m, 55.0, FPW_SKY, "out3")
fpw(m, 59.4, FPW_LOW)
fpl(m, 12.4, FPL_RAISE2H, "out")
fpl(m, 41.0, nudge(FP_DOWN, (-0.2, 0, 0), (0, 40, 0)), "in")
fpl(m, 52.0, nudge(FP_DOWN, (-0.2, -0.05, 0), (0, 40, 0)))
fpl(m, 55.0, FPL_RAISE2H, "out3")
fpl(m, 59.4, FP_LOW)
m.set(softLegs=False)
impacts(m, 46.0)
use_skill(m, "dragoon_ult")
