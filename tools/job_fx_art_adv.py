#!/usr/bin/env python3
"""0.51.0 2차 직업 그림 — 블러드 나이트 (검사 반전) 의 피 · 전사 · 마검사 · 매지션 · 궁수 2차 → tools/java_fx (rpg:rfx_j*), 설치는 tools/java_fx_add.py.

  python3 tools/job_fx_art_adv.py                        # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/job_fx_art_adv.py --preview <폴더>       # 확인용 그림도 씀 (v2c 꼴 — 장면 줄 · 작은 크기 줄 · 한 장 확대 · 빛 / 심지 알파)
  python3 tools/java_fx_add.py rfx_jdrop rfx_jblood      # 게임에 넣음

꼴은 v2c (tools/job_fx_art_v2c_marks.py) 와 같음: 모두 흰색 (밝기 = 알파) 으로 4배 그림판에 그려 LANCZOS 로 줄임 · 칸 = 빛 장면 N 개 다음에 심지 장면 N 개.
피는 빛나는 그림이 아니라 몸이 있는 액체라 '빛' 칸 = 짙은 몸 (불투명에 가깝게 · 가장자리만 살짝 흐림), '심지' 칸 = 밝은 속 · 젖은 반짝임 (게임은 빛 칸에 짙은 피,
심지 칸에 밝은 피를 입힘 — JobTraits5.drop · blood). 고정 시드.

그림 (이름 — 보는 꼴 — 칸 — 장 수 — 열 수):
- rfx_jdrop   lookat_direction 128×64  ×1 (2 열)  핏방울: 오른쪽 (+X = 나아가는 쪽) 둥근 머리 · 왼쪽으로 가늘어지는 꼬리. 심지 = 머리 쪽 속 · 젖은 반짝임 점
- rfx_jblood  rotate_xyz       128×128 ×6 (6 열)  피 튀김: 0 가운데 덩이 · 짧은 가시 → 1 ~ 3 가시가 뻗다 끊겨 방울로 날아감 (나아가는 쪽으로 늘어남) · 가운데 왕관 꼴로 퍼짐 →
                                                   4 ~ 5 방울이 작아지고 흐려지며 가운데는 옅은 얼룩으로 사라짐. 칸 가운데 = 튄 자리

0.51.0 전사 2차 (워로드 · 재설계안 2판 글래디에이터 — 라이칸 의 늑대 발톱 rfx_jclaw4 는 뺌, 달 rfx_jmoon 은 섀도 헌터가 씀) —
빛 칸 = 빛나는 몸 (넓은 무리), 심지 칸 = 눈 · 이빨 · 발톱 · 털끝 · 테:
- rfx_jbear   rotate_xyz       256×256 ×8 (4 열)  곰의 혼 (앞모습 — 일어선 곰의 머리 · 어깨 · 양옆으로 든 앞발): 0 ~ 2 기운이 소용돌이쳐 모여 곰이 됨 (턱이 열리기 시작) →
                                                   3 ~ 6 포효 (이어 돌림 — 턱이 열렸다 닫혔다 · 털끝이 일렁임) → 7 위로 흩어짐. 머리 가운데 (128, 104) · 성난 눈 · 송곳니 · 귀 · 앞발 발톱
- rfx_jpaw    rotate_xyz       256×256 ×4 (4 열)  곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중). 발바닥 가운데 = 칸 가운데
- rfx_jmoon   rotate_xyz       256×256 ×8 (4 열)  달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 둥근 빛무리) → 1 ~ 6 그림자가 오른쪽에서 달을 삼킴 (가려진 곳은 비움) →
                                                   7 남은 테 (코로나 — 게임은 붉게). 원판 반지름 79 (= 반 크기 × 0.62)
- rfx_jspike  lookat_y         256×256 ×8 (4 열)  땅에서 솟는 돌창 무리 (워로드 — 빛 = 돌 몸 · 심지 = 틈의 녹은 주황): 0 ~ 2 솟음 · 3 ~ 5 서 있음 (맥동) · 6 금 · 7 무너짐
- rfx_jstake  lookat_y         128×256 ×6 (6 열)  결투 말뚝 (글래디에이터 — 빛 = 나무 몸 · 심지 = 밧줄 · 룬 ᛏ): 0 솟음 · 1 ~ 4 서 있음 (이어 돌림) · 5 가라앉음
- rfx_jarms   rotate_xyz       320×320 ×8 (4 열)  여덟 팔의 도끼 잔상 (글래디에이터 여덟 팔 — 이어 돌림): 어깨 위 부채꼴 팔 여덟이 차례로 내리침 · 잔상 · 날 끝 호
- rfx_jarena  direction_z      384×384 ×6 (3 열)  결투장 바닥 (홀름강 — 땅에 누움): 0 ~ 1 테가 그어짐 · 2 ~ 4 룬이 돎 (이어 돌림) · 5 흐려짐

0.51.0 마검사 2차 (룬 스미스 — 재설계안 2판: 소드 콜러 는 입체 마력 검만 써서 그림이 없음 · 헥스 블레이드 의 가시 룬 rfx_jthorn ·
땅의 ᚦ rfx_jthurs · 옛 궁극기의 룬 스물넷 rfx_jrunes24 는 뺌) — 글자: 빛 칸 = 넓은 빛 (보라) · 심지 칸 = 날카로운 획 (금빛) / 대장간: 빛 칸 = 쇳물 (주황) · 어두운 쇠
(모루 몸 — 거의 불투명), 심지 칸 = 흰 열 (금 · 거품 · 룬 · 눈 · 뿔 테):
- rfx_jglyph   rotate_xyz       192×192 ×18 (6 열) 공중에 새긴 룬 글자 셋 (지어낸 각진 글자 GLYPHS 2 · 7 · 4 — 종류마다 6장): 0 획이 반쯤 그어짐 (긋는 끝 불티) · 1 다 그어지며 번쩍 →
                                                   2 ~ 3 빛남 (둘레 옅은 고리 · 반짝임이 바뀜 — 이어 돌림) → 4 번쩍 (네 뿔 별) → 5 획이 조각나 바깥으로 흩어짐. 글자 가운데 = 칸 가운데
- rfx_jheat    rotate_xyz       128×128 ×6 (6 열)  달궈진 몸: 0 달아오름 (번쩍 · 열 고리) → 1 ~ 4 몸을 감싼 쇳물빛 기운 · 오르는 열기 줄 · 불씨 (이어 돌림) → 5 식으며 흰 열 금
- rfx_jmolten  direction_z      256×256 ×8 (4 열)  쇳물 웅덩이 (땅에 누움): 0 ~ 1 번지며 달아오름 → 2 ~ 5 끓음 (식은 껍질 판 사이의 금 · 가장자리 띠 · 거품 — 이어 돌림) → 6 ~ 7 식어 흐려짐
- rfx_janvil   lookat_y         256×256 ×8 (4 열)  룬 모루 (아래 끝 = 땅): 0 ~ 1 받침 돌과 솟음 → 2 ~ 4 룬이 차례로 켜지며 달아오름 → 5 흰 열 → 6 조각이 튀며 번쩍 · 7 흩어짐
- rfx_jram     lookat_y         320×320 ×8 (4 열)  쇳물 숫양 (앞모습 · 아래 끝 = 발굽): 0 ~ 1 쇳물 기둥이 숫양이 됨 → 2 ~ 5 달려옴 (앞발 번갈아 · 나선 뿔 · 이마 룬 · 쇳물 금 —
                                                   이어 돌림) → 6 뿔로 받아 버팀 (큰 불티 별) → 7 쇳물 방울로 터짐

0.51.0 매지션 2차 (아크메이지 · 네크로맨서) — 빛 칸 = 넓은 빛 (아크메이지 밤하늘 자주 · 네크로맨서 짙은 녹색 혼불), 심지 칸 = 금빛 별 심지 · 뼈 ·
눈구멍의 녹색 불 (네크로맨서는 '빛' 칸에 뼈 몸을 거의 불투명하게):
- rfx_jstarnode  rotate_xyz       192×192 ×8 (4 열)  별자리의 별: 0 번쩍 · 1 피어남 → 2 ~ 5 반짝임 (네 갈래 긴 빛살 · 짧은 대각 빛살 — 이어 돌림) → 6 크게 번쩍 · 7 별빛 조각으로 흩어짐
- rfx_jfallstar  lookat_direction 256×96  ×4 (4 열)  별똥 (오른쪽 = 나아가는 쪽 · 머리 x 0.88 W): 빛나는 별 머리 · 길게 가늘어지는 꼬리 · 꼬리를 따라 흐르는 반짝이 가루 (이어 돌림)
- rfx_jbonespear lookat_direction 256×64  ×4 (4 열)  뼈 창 (창끝 x 0.97 W): 이빨 뼈 날 · 등뼈 마디 자루 · 뒤로 휜 갈비 가시 · 꼬리 혹 · 녹색 혼불 · 끌리는 연기 (이어 돌림)
- rfx_jbonewall  lookat_y         256×256 ×8 (4 열)  뼈 방벽 (아래 끝 = 땅): 0 ~ 2 휜 뼈 가시 일곱이 땅을 뚫고 솟음 (흙 튐) → 3 ~ 5 서 있음 (혼불이 타고 오름 — 이어 돌림) →
                                                     6 금이 벌어짐 · 7 조각으로 흩어짐
- rfx_jskull     rotate_xyz       128×128 ×6 (6 열)  망자의 해골: 0 연기가 모여 해골 · 1 ~ 3 눈구멍 혼불 맥동 (이어 돌림) · 4 턱이 벌어지며 비명 · 5 연기로 흩어짐
- rfx_jrift      direction_z      128×320 ×6 (6 열)  갈라진 땅 (그림 위 = 금이 뻗는 쪽): 0 가는 금 · 1 ~ 2 벌어지며 돌조각 → 3 ~ 4 틈의 녹색 빛 맥동 (불티 — 이어 돌림) →
                                                     5 닫히며 흐려짐

0.51.0 궁수 2차 (스나이퍼 · 섀도 헌터) — 스나이퍼: 빛 칸 = 연두 무리 · 심지 칸 = 흰 금빛 (JobTraits9.SNIPE) / 섀도 헌터: 빛 칸 = 먹빛 몸 (JobTraits9.DUSK
— 화살대 · 깃 · 연기 · 덤불을 거의 불투명하게: 밝은 하늘 · 풀 위에서 검게 보임) · 심지 칸 = 은빛 (촉 · 테 · 눈):
- rfx_jweak       rotate_xyz       128×128 ×6 (6 열)  약점 과녁: 0 조여 들며 나타남 · 1 ~ 3 맥동 (네 토막 테 · 틈으로 들어오는 눈금 · 대각 꺾쇠가 30 도씩 돎 · 가운데 사과빛 점 깜빡
                                                     — 이어 돌림) · 4 맞음 (가운데가 깨지며 흰 섬광 · 금 일곱) · 5 흐려짐
- rfx_jgreatarrow lookat_direction 320×96  ×4 (2 열)  거대한 셋째 화살 (촉 끝 x 0.975 W): 넓은 잎 촉 · 감아 도는 바람 띠 두 가닥 (촉 쪽 좁고 뒤로 넓어짐 — 뒤로 흐름) ·
                                                     흰 섬광 깃 · 살대를 타고 앞으로 흐르는 빛 · 공기를 가르는 원뿔 줄 (이어 돌림)
- rfx_jblackarrow lookat_direction 256×64  ×4 (2 열)  검은 화살 (촉 끝 x 0.975 W): 미늘 달린 은빛 촉 · 검은 살대 (은빛 결 · 새김 셋 깜빡) · 갈라진 까마귀 깃 ·
                                                     뒤로 끌리는 그림자 연기 · 연기 가닥 · 은빛 티끌 (이어 돌림)
- rfx_jcurse      rotate_xyz       128×128 ×6 (6 열)  저주 문양: 0 연기가 소용돌이치며 모여 고리 (반쯤) · 1 ~ 3 맥동 (꼬인 덩굴 고리 · 바깥 / 안 가시 아홉씩이 1/3 칸씩 돎 ·
                                                     가운데 감긴 눈 — 이어 돌림) · 4 터짐 (눈이 뜨임 · 가시가 튐) · 5 흐려짐
- rfx_jthicket    lookat_y         256×256 ×8 (4 열)  어둠숲 가시덤불 (아래 끝 = 땅): 0 ~ 2 휜 가시 줄기 일곱 (곁가지 · 갈고리 끝 · 건너 얽힌 덩굴) 이 땅을 뚫고 자람 →
                                                     3 ~ 5 일렁임 (덤불 속 성난 눈 셋 · 은빛 티끌 — 이어 돌림, 4 에서 눈 깜빡) → 6 은빛 금 · 7 조각 · 연기로 흩어짐
"""
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402
from job_fx_art_v2 import COLORS, TAU, Pad, Stack, bevel_light, bez, clamp01, gblur, obj_glow, rim_of, screen, smooth, taper_poly, to_sheet  # noqa: E402
from job_fx_art_v2 import GLYPHS, glyph_lines, glyph_mask  # noqa: E402
from job_fx_art_v2b import arc_pts, chunk_pts, cut_polyline, faceted, fbm, finish, gauss, lens, polar, star4, streak, win_border  # noqa: E402
import job_fx_art_v2c_marks as V2C  # noqa: E402 — 확인용 그림 (preview) 을 같은 꼴로

BLOOD = ((0.62, 0.02, 0.08), (0.95, 0.18, 0.2))
COLORS.setdefault("bloodknight", BLOOD)
# 확인용 색 (빛 · 심지 — 게임이 입히는 색과 같게): 곰 = 갈색 몸 · 주황 눈 / 달 = 달빛 은회 · 흰빛 (섀도 헌터)
COLORS.setdefault("warlord", ((0.66, 0.36, 0.14), (1.0, 0.7, 0.33)))
COLORS.setdefault("moon", ((0.76, 0.8, 0.92), (1.0, 1.0, 1.0)))
# 0.51.0 마검사 2차: 룬 스미스 = 보라 · 금빛
COLORS.setdefault("runeknight", ((0.62, 0.34, 1.0), (1.0, 0.8, 0.32)))


class Spec(V2C.Spec):
    pass


SPECS = {}


def sprite(name, facing, cw, ch, frames, cols, job, zoom=0, desc="", loop=False):
    def deco(fn):
        SPECS[name] = Spec(name, facing, cw, ch, frames, cols, job, fn, loop, zoom, desc)
        return fn
    return deco


def drop_shape(pad, hx, hy, r, tail, ang=0.0, n=20):
    """눈물 꼴 (머리 가운데 hx, hy · 반지름 r · 꼬리 길이 tail — 꼬리는 ang 의 반대쪽): 둥근 머리 + 가늘어지는 꼬리 (4배 그림판 배열)"""
    ux, uy = math.cos(ang), math.sin(ang)
    pts = [(hx - ux * tail * (1 - i / n), hy - uy * tail * (1 - i / n)) for i in range(n + 1)]
    body = pad.poly(taper_poly(pts, lambda t: r * (t ** 1.6) * 0.98)) if tail > 0.5 else pad.zeros()
    return np.maximum(body, pad.disc_field(hx, hy, r))


# ------------------------------------------------------------ rfx_jdrop (핏방울 — 오른쪽이 나아가는 쪽)

def drop_frame(f):
    cw, ch = 128, 64
    pad = Pad(cw, ch)
    hx, hy, r = 90.0, 32.0, 15.0
    body = drop_shape(pad, hx, hy, r, 66.0)
    inner = drop_shape(pad, hx - 1.5, hy + 1.0, r * 0.62, 40.0)
    shine = pad.disc_field(hx + 5.0, hy - 6.0, 3.6) + pad.disc_field(hx - 8.0, hy - 7.5, 1.8) * 0.6
    b1 = pad.down(body)
    glow = screen(b1 * 0.94, gblur(b1, 1.6) * 0.5)
    core = np.maximum(pad.down(inner) * 0.62, clamp01(pad.down(shine)) * 1.0)
    wb = win_border(glow.shape, 3.0)
    return clamp01(glow) * wb, clamp01(core) * wb


sprite("rfx_jdrop", "lookat_direction", 128, 64, 1, 2, "bloodknight",
       desc="핏방울: 오른쪽 (+X = 나아가는 쪽) 둥근 머리 · 왼쪽으로 가늘어지는 꼬리, 심지 = 머리 쪽 속 · 젖은 반짝임 점")(drop_frame)


# ------------------------------------------------------------ rfx_jblood (피 튀김 — 칸 가운데 = 튄 자리)

BL_N = 6
_R = random.Random(5106)
BL_SPIKES = [(_R.uniform(0, TAU), _R.uniform(0.75, 1.0), _R.uniform(0.8, 1.2)) for _ in range(9)]
BL_DROPS = [(_R.uniform(0, TAU), _R.uniform(0.55, 1.0), _R.uniform(2.6, 5.2)) for _ in range(16)]
# 가운데 덩이 반지름 · 가시 길이 · 방울이 간 몫 · 밝기 (장마다)
BL_CORE_R = [13.0, 17.0, 19.0, 19.5, 18.5, 17.0]
BL_SPIKE = [0.45, 1.0, 0.7, 0.3, 0.0, 0.0]
BL_FLY = [0.0, 0.32, 0.58, 0.78, 0.92, 1.0]
BL_A = [1.0, 1.0, 1.0, 0.86, 0.6, 0.32]


def blood_frame(f):
    cw = ch = 128
    pad = Pad(cw, ch)
    c = 64.0
    a = BL_A[f]
    # 가운데 덩이: 가장자리가 울퉁불퉁한 원 (노이즈 반지름) — 뒤 장면일수록 옅은 얼룩
    rr, th = polar(pad, c, c)
    wob = fbm(np.cos(th) * 1.6 + 3.0, np.sin(th) * 1.6 - 2.0 + f * 0.15, seed=33, octaves=3)
    R0 = BL_CORE_R[f]
    blob = clamp01((R0 * (1 + 0.28 * wob) - rr) * pad.ss / 2.0 + 0.5)
    body = blob * (1.0 if f < 4 else 0.7 if f == 4 else 0.45)
    inner = clamp01((R0 * 0.55 * (1 + 0.3 * wob) - rr) * pad.ss / 2.0 + 0.5) * (1.0 if f < 3 else 0.5)
    # 가시: 가운데에서 바깥으로 뻗는 뾰족한 혀 (0 ~ 3) — 끝이 둥근 방울
    for ang, ln, wd in BL_SPIKES:
        L = 44.0 * ln * BL_SPIKE[f]
        if L < 2:
            continue
        ux, uy = math.cos(ang), math.sin(ang)
        pts = [(c + ux * (R0 * 0.6 + L * i / 16), c + uy * (R0 * 0.6 + L * i / 16)) for i in range(17)]
        sp = pad.poly(taper_poly(pts, lambda t, w=wd: 4.2 * w * (1 - t) ** 1.2 + 0.6))
        tip = pad.disc_field(pts[-1][0], pts[-1][1], 2.4 * wd)
        body = np.maximum(body, np.maximum(sp, tip))
        inner = np.maximum(inner, pad.poly(taper_poly(pts[:10], lambda t, w=wd: 1.6 * w * (1 - t))) * 0.8)
    # 방울: 바깥으로 날아감 (나아가는 쪽으로 늘어난 눈물 꼴 — 꼬리는 가운데 쪽) · 뒤로 갈수록 작아짐
    for ang, sp_, r0 in BL_DROPS:
        t = BL_FLY[f] * sp_
        if t <= 0.02:
            continue
        dist = R0 * 0.8 + 40.0 * t
        x, y = c + math.cos(ang) * dist, c + math.sin(ang) * dist
        r = r0 * (1.0 - 0.45 * BL_FLY[f])
        tail = r * (3.2 if f <= 2 else 1.8 if f == 3 else 0.6)
        d = drop_shape(pad, x, y, r, tail, ang)
        body = np.maximum(body, d)
        inner = np.maximum(inner, pad.disc_field(x + 0.3 * r, y - 0.35 * r, r * 0.38))
    b1 = pad.down(body)
    glow = screen(b1 * 0.92, gblur(b1, 1.8) * 0.45) * a
    core = clamp01(pad.down(inner)) * a
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow) * wb, clamp01(core) * wb


sprite("rfx_jblood", "rotate_xyz", 128, 128, BL_N, 6, "bloodknight", zoom=2,
       desc="피 튀김: 0 가운데 덩이 · 짧은 가시 → 1 ~ 3 가시가 뻗다 끊겨 방울로 날아감 · 가운데가 퍼짐 → 4 ~ 5 방울이 작아지고 흐려지며 옅은 얼룩으로 사라짐")(blood_frame)


# ============================================================ 0.51.0 전사 2차 — 곰의 혼 · 곰 앞발 · 늑대 발톱 · 달

# ------------------------------------------------------------ rfx_jmoon (달 — 원판 반지름 79 · 칸 가운데)

MN_N = 8
MN_R = 79.0
_RM = random.Random(7070)
MN_CRATERS = [(_RM.uniform(-0.7, 0.7), _RM.uniform(-0.7, 0.7), _RM.uniform(4.0, 11.0)) for _ in range(14)]


def moon_frame(f):
    S = 256
    pad = Pad(S, S)
    c = S / 2
    R = MN_R
    X, Y = pad.grid()
    rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    disc = clamp01((R - rr) * pad.ss + 0.5)
    nz = fbm(X / 46.0 + 3.1, Y / 46.0 - 1.7, seed=77, octaves=4)
    maria = smooth(0.0, 0.32, nz)
    limb = 1.0 - 0.3 * np.clip(rr / R, 0, 1) ** 2.2
    surf = disc * (0.62 + 0.38 * (1 - maria)) * limb
    core = disc * smooth(R - 7.0, R - 1.0, rr) * 0.55  # 밝은 가장자리 (달의 테)
    core = np.maximum(core, disc * (1 - maria) * 0.32 * limb)
    for cxr, cyr, r in MN_CRATERS:
        x, y = c + cxr * R * 0.85, c + cyr * R * 0.85
        if math.hypot(x - c, y - c) + r > R - 3:
            continue
        d = np.sqrt((X - x) ** 2 + (Y - y) ** 2)
        ring = clamp01(1 - np.abs(d - r) / 1.4) * disc * smooth(-0.6, 0.6, (Y - y) / max(1.0, r))  # 아래쪽 테만 밝게 (빛이 위에서)
        bowl = clamp01((r - d) / r) * disc
        surf = surf * (1 - 0.22 * bowl)
        core = np.maximum(core, ring * 0.45)
    p = min(1.0, f / 6.0)
    halo_k = 1.0 - 0.75 * p
    if f <= 6:
        sx = c + 2.06 * R * (1 - p) + 2.0 * p
        sy = c - 10.0 * (1 - p)
        ds = np.sqrt((X - sx) ** 2 + (Y - sy) ** 2)
        shadow = clamp01((R * 1.03 - ds) * pad.ss / 3.0 + 0.5)
    else:
        shadow = disc
    lit = surf * (1 - shadow)
    core_l = core * (1 - shadow)
    # 그림자 가장자리의 옅은 테 (삼키는 이빨 자국처럼 들쭉날쭉)
    if 1 <= f <= 6:
        th = np.arctan2(Y - sy, X - sx)
        jag = 1.0 + 0.035 * np.sin(th * 26.0) + 0.02 * np.sin(th * 11.0 + 1.3)
        edge = clamp01(1 - np.abs(ds - R * 1.03 * jag) / 2.2) * disc
        core_l = np.maximum(core_l, edge * 0.45)
    # 코로나 (다 가려짐 6 · 붉은 테 7): 원판 둘레의 테 · 일렁이는 빛살
    if f >= 6:
        k = 1.0 if f == 7 else 0.6
        th = np.arctan2(Y - c, X - c)
        flare = 1.0 + 0.35 * np.sin(th * 7.0 + 0.6) * np.sin(th * 3.0 + 2.0)
        ring = clamp01(1 - np.abs(rr - (R + 2.0)) / (3.0 + 2.0 * k)) * k
        rays = clamp01(1 - (rr - R) / (26.0 * flare * k + 1e-6)) * (rr > R) * 0.55 * k
        core_l = np.maximum(core_l, ring)
        lit = np.maximum(lit, np.maximum(ring * 0.9, rays))
    b1 = pad.down(lit)
    halo = pad.down(clamp01(1 - (rr - R) / 46.0) * (rr > R - 2)) * 0.4 * halo_k
    glow = screen(b1, gblur(b1, 3.0) * 0.35, halo)
    core1 = pad.down(core_l)
    wb = win_border(glow.shape, 6.0)
    return clamp01(glow) * wb, clamp01(core1) * wb


sprite("rfx_jmoon", "rotate_xyz", 256, 256, MN_N, 4, "moon", zoom=0,
       desc="달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 빛무리) → 1 ~ 6 그림자가 오른쪽에서 삼킴 (들쭉날쭉한 그림자 테) → 7 붉은 테 (코로나 · 빛살)")(moon_frame)


# ------------------------------------------------------------ rfx_jpaw (곰 앞발 — 발바닥 가운데 = 칸 가운데, 이어 돌림)

PW_N = 4


def paw_frame(f, S=256, frames=PW_N):
    pad = Pad(S, S)
    c = S / 2
    st = Stack(pad)
    ph = TAU * f / frames
    # 위로 흐르는 바람 줄 (내려치는 중 — 줄이 위로 흘러감, 맨 뒤)
    rnd = random.Random(909)
    wind = pad.zeros()
    for i in range(9):
        x = c + rnd.uniform(-70, 70)
        q = (rnd.random() + f / frames) % 1.0
        y0 = 70 - 60 * q
        Ln = rnd.uniform(40, 80)
        wind = np.maximum(wind, pad.stroke([(x, y0 + Ln), (x, y0)], 2.6, 0.6, a0=0.55 * math.sin(math.pi * q), a1=0.0))
    # 팔 (위로 이어지며 흐려짐)
    arm = pad.poly([(c - 50, 0), (c + 50, 0), (c + 58, c - 10), (c - 58, c - 10)])
    X, Y = pad.grid()
    arm = arm * smooth(10.0, 70.0, Y)
    st.over(arm, 0.5, rim_of(arm, 0.8) * 0.4 * arm)
    # 발 (털 가장자리가 들쭉날쭉한 둥근 덩이)
    pts = []
    for i in range(72):
        t = TAU * i / 72
        r = 70 + 4.5 * math.sin(t * 9 + ph * 0.5) * (1 if math.sin(t) < 0.3 else 0.35)
        pts.append((c + math.cos(t) * r * 1.05, c + 2 + math.sin(t) * r * 0.78))
    pawm = pad.poly(pts)
    lit, dark, _ = bevel_light(pawm, 14.0)
    st.over(pawm, 0.58 + 0.2 * lit - 0.15 * dark, (rim_of(pawm, 1.4) * 0.8 + lit * 0.25) * pawm)
    # 털결 (짧은 줄)
    for i in range(14):
        x = c - 56 + 8 * i + rnd.uniform(-2, 2)
        y = c - 30 + rnd.uniform(-8, 8)
        st.add_core(pad.stroke([(x, y), (x - 2, y + 10)], 1.0, 0.3, a0=0.35, a1=0.05) * pawm)
    # 발바닥 볼록 (큰 것 하나 — 위 · 발가락 넷 — 아래로 둥글게 늘어섬: 발톱이 먼저 내려오는 앞발)
    main = pad.ellipse(c, c - 8, 38, 24)
    st.over(main, 0.82, (rim_of(main, 1.2) * 0.9 + 0.22) * main)
    toes = [(-50, 22), (-18, 32), (18, 32), (50, 22)]
    for tx, ty in toes:
        m = pad.ellipse(c + tx, c + ty, 13, 12)
        st.over(m, 0.85, (rim_of(m, 1.0) * 0.9 + 0.28) * m)
    # 발톱 넷: 발가락 끝에서 아래로 길게 휨 (빛나는 갈고리 — 가운데 둘이 더 김)
    for k, (tx, ty) in enumerate(toes):
        bx, by = c + tx, c + ty + 9
        inward = -1 if tx > 0 else 1
        tip = (bx + inward * 8, by + (64 if abs(tx) < 30 else 52))
        ctrl = (bx - inward * 6, by + 30)
        cl = bez((bx, by), ctrl, tip, 18)
        m = pad.poly(taper_poly(cl, lambda t: 8.0 * (1 - t) ** 0.85 + 0.4))
        st.over(m, 0.9, pad.poly(taper_poly(cl, lambda t: 3.2 * (1 - t) ** 0.9 + 0.3)) * (0.75 + 0.25 * math.sin(ph + k)))
    core1 = pad.down(st.core)
    glow = obj_glow(pad, np.maximum(st.body, wind * 0.6), core1, halo=((1.6, 0.5), (4.5, 0.36), (10.0, 0.2)), body_k=0.95)
    glow = screen(glow, pad.down(wind) * 0.5)
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow) * wb, clamp01(core1) * wb


sprite("rfx_jpaw", "rotate_xyz", 256, 256, PW_N, 4, "warlord", zoom=0, loop=True,
       desc="곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중)")(paw_frame)


# ------------------------------------------------------------ rfx_jbear (곰의 혼 — 앞모습, 머리 가운데 (128, 104))

BR_N = 8
BR_JAW = [0.0, 0.22, 0.55, 0.82, 1.0, 0.9, 0.8, 0.6]
BR_A = [0.38, 0.66, 0.9, 1.0, 1.0, 1.0, 1.0, 0.42]
BR_SCALE = [0.82, 0.91, 0.97, 1.0, 1.025, 1.012, 1.0, 1.05]
BR_HX, BR_HY = 128.0, 104.0


def bear_head_pts(ph, n=96):
    """머리 둘레 (넓적한 머리 · 아래 볼 털이 양옆으로 뻗침 — 털끝이 조금 일렁임)"""
    pts = []
    for i in range(n):
        t = TAU * i / n
        s, co = math.sin(t), math.cos(t)
        rx, ry = 58.0, 50.0 if s < 0 else 54.0
        r = 1.0
        if s > 0.05:  # 아래 반 (볼 · 턱): 양옆으로 볼록 · 털 가시
            r += 0.2 * max(0.0, abs(co)) ** 1.4 * s ** 0.5
            r += 0.08 * max(0.0, math.sin(t * 15 + ph)) ** 3
        else:  # 위 (이마): 조금 납작 · 짧은 털
            r += 0.025 * max(0.0, math.sin(t * 18 + ph * 0.7)) ** 3
        pts.append((BR_HX + co * rx * r, BR_HY + s * ry * r))
    return pts


def bear_frame(f, S=256):
    pad = Pad(S, S)
    st = Stack(pad)
    X, Y = pad.grid()
    loop_ph = TAU * ((f - 3) % 4) / 4 if 3 <= f <= 6 else 0.0
    J = BR_JAW[f]
    sc = BR_SCALE[f]
    rnd = random.Random(3131)

    def T(x, y):  # 가운데 (128, 128) 둘레로 크기
        return (128 + (x - 128) * sc, 128 + (y - 128) * sc)

    def TP(pts):
        return [T(x, y) for x, y in pts]

    # ---- 어깨 · 몸 (맨 뒤 — 둥근 어깨 · 털 가시 · 허리로 좁아지며 가슴 아래부터 흐려짐)
    # 영상 1 (0.51.0): 어깨 너비 그대로 칸 아래 끝까지 내려가 · 짙게 채운 몸이 곰의 화신 (몸을 감쌈) 에서 주황 상자처럼 보였음 →
    # 허리로 좁힘 (아래 끝 너비 136 → 44) · 흐려짐을 가슴 (168) 부터 · 채움을 옅게 (0.42 → 0.28 — 테 · 털결로 꼴을 읽게)
    sh = []
    for i in range(49):
        u = i / 48
        x = 30 + 196 * u
        top = 172 - 30 * math.sin(math.pi * u) ** 0.6 + 7 * max(0.0, math.sin(u * 34 + loop_ph)) ** 3
        sh.append((x, top))
    sh += [(210, 200), (184, 230), (150, 250), (106, 250), (72, 230), (46, 200)]
    shm0 = pad.poly(TP(sh))
    fur = fbm(X / 14.0, Y / 9.0 + loop_ph * 0.15, seed=12, octaves=3)
    fade = smooth(236.0, 168.0, Y)
    shm = shm0 * fade
    lit, dark, _ = bevel_light(shm0, 18.0)
    st.over(shm, (0.28 + 0.12 * lit - 0.08 * dark) * (0.8 + 0.3 * fur), rim_of(shm0, 1.2) * 0.45 * shm)
    for i in range(9):  # 가슴 털결
        x0 = 70 + 14 * i + rnd.uniform(-3, 3)
        y0 = 182 + rnd.uniform(-4, 8)
        st.add_core(pad.stroke(TP([(x0, y0), (x0 + rnd.uniform(-3, 3), y0 + 14)]), 1.2, 0.3, a0=0.28, a1=0.0) * shm)
    # ---- 앞발 둘 (양옆으로 들어 올림 — 굵은 팔 · 털 가시 · 발 · 발톱)
    for side in (-1, 1):
        sx = 128 + side * 86
        arm = TP([(128 + side * 50, 190), (128 + side * 92, 180), (sx + side * 26, 134), (sx + side * 8, 112), (sx - side * 20, 120), (128 + side * 44, 158)])
        am = pad.poly(arm)
        lit, dark, _ = bevel_light(am, 10.0)
        st.over(am, (0.52 + 0.16 * lit - 0.1 * dark) * (0.85 + 0.2 * fur), rim_of(am, 1.0) * 0.4 * am)
        for k in range(5):  # 팔 바깥 털 가시
            u = k / 4
            bx, by = T(128 + side * (88 - 6 * u) + side * (sx - 128 - side * 88 + side * 26) * 0 , 178 - 46 * u)
            bx, by = T(128 + side * (92 - 6 * u) - (92 - 6 * u) * 0 + side * (u * (86 + 26 - 92)), 180 - 46 * u)
            tip = (bx + side * 9, by - 4 + 2 * math.sin(loop_ph + k))
            m = pad.poly([(bx, by - 4), tip, (bx, by + 5)])
            st.over(m, 0.5, rim_of(m, 0.6) * 0.3 * m)
        px, py = T(sx + side * 4, 114)
        pm = pad.ellipse(px, py, 25 * sc, 21 * sc)
        lit, dark, _ = bevel_light(pm, 8.0)
        st.over(pm, 0.6 + 0.15 * lit - 0.1 * dark, (rim_of(pm, 1.0) * 0.6 + lit * 0.2) * pm)
        for k in range(4):
            a0 = math.radians(-150 + 30 * k) if side < 0 else math.radians(-30 - 30 * k)
            bx, by = px + math.cos(a0) * 19 * sc, py + math.sin(a0) * 16 * sc
            tip = (bx + math.cos(a0 + side * 0.5) * 24 * sc, by + math.sin(a0 + side * 0.5) * 24 * sc - 4)
            cl = bez((bx, by), (bx + math.cos(a0) * 15 * sc, by + math.sin(a0) * 15 * sc - 4), tip, 12)
            m = pad.poly(taper_poly(cl, lambda t: 3.8 * sc * (1 - t) ** 0.9 + 0.3))
            st.over(m, 0.85, pad.poly(taper_poly(cl, lambda t: 1.6 * sc * (1 - t) + 0.25)))
    # ---- 귀 (머리 뒤)
    for side in (-1, 1):
        ex, ey = T(128 + side * 49, 62)
        em = pad.poly(TP([(128 + side * 34, 72), (128 + side * 40, 50), (128 + side * 52, 44), (128 + side * 64, 52), (128 + side * 66, 72)]))
        st.over(em, 0.58, (rim_of(em, 1.0) * 0.55) * em)
        inner = pad.poly(TP([(128 + side * 42, 68), (128 + side * 46, 54), (128 + side * 54, 51), (128 + side * 60, 58), (128 + side * 60, 70)]))
        st.add_core(inner * 0.3)
        st.body = st.body * (1 - inner * 0.4)
    # ---- 머리
    hm = pad.poly(TP(bear_head_pts(loop_ph)))
    lit, dark, hh = bevel_light(hm, 18.0)
    head_fur = fbm(X / 10.0, Y / 10.0, seed=41, octaves=3)
    st.over(hm, (0.6 + 0.16 * lit - 0.12 * dark) * (0.88 + 0.18 * head_fur), (rim_of(hm, 1.2) * 0.5 + lit * 0.14) * hm)
    # 이마 · 볼 털결
    for i in range(16):
        a = rnd.uniform(-2.6, -0.5)
        r0 = rnd.uniform(18, 46)
        x0, y0 = 128 + math.cos(a) * r0, 104 + math.sin(a) * r0 * 0.8
        x1, y1 = x0 + math.cos(a) * 9, y0 + math.sin(a) * 7
        st.add_core(pad.stroke(TP([(x0, y0), (x1, y1)]), 1.1 * sc, 0.3, a0=0.32, a1=0.05) * hm)
    for side in (-1, 1):
        for k in range(4):
            x0, y0 = 128 + side * (34 + 6 * k), 124 + 7 * k
            st.add_core(pad.stroke(TP([(x0, y0), (x0 + side * 12, y0 + 4)]), 1.2 * sc, 0.3, a0=0.4, a1=0.05) * hm)
    # ---- 주둥이 (조금 밝은 볼록) · 입 (턱이 J 만큼 열림)
    mx, my = T(128, 131)
    muz = pad.ellipse(mx, my, 28 * sc, 25 * sc)
    lit_m, dark_m, _ = bevel_light(muz, 8.0)
    st.over(muz, 0.74 + 0.12 * lit_m - 0.08 * dark_m, (rim_of(muz, 0.9) * 0.5) * muz)
    drop = 30.0 * J
    lip = 138.0
    mouth_pts = TP(bez((106, lip), (128, lip - 4), (150, lip), 10) + bez((150, lip), (146, lip + 8 + drop), (128, lip + 10 + drop), 8)[1:] +
                   bez((128, lip + 10 + drop), (110, lip + 8 + drop), (106, lip), 8)[1:])
    mouth = pad.poly(mouth_pts) if J > 0.05 else pad.zeros()
    st.over(mouth, 0.14, mouth * 0.05)
    if J > 0.3:  # 혀
        tg = pad.ellipse(*T(128, lip + 6 + drop * 0.85), 13 * sc, 6 * sc) * mouth
        st.over(tg, 0.36, rim_of(clamp01(tg * 1.5), 0.6) * 0.4 * tg)
    # 아래턱 (열린 만큼 내려감 — 털 덮인 턱 끝)
    chin = pad.poly(TP(bez((108, lip + 6 + drop), (128, lip + 24 + drop), (148, lip + 6 + drop), 12)))
    st.over(chin, 0.66, rim_of(chin, 0.8) * 0.5 * chin)
    # 이빨: 위 송곳니 둘 (아래로) · 아래 송곳니 둘 (위로) · 작은 앞니
    teeth = pad.zeros()
    for side in (-1, 1):
        x = 128 + side * 14
        teeth = np.maximum(teeth, pad.poly(TP([(x - 4, lip - 1), (x + 4, lip - 1), (x + side * 1.5, lip + 9 + 9 * J)])))
        if J > 0.2:
            yb = lip + 8 + drop
            teeth = np.maximum(teeth, pad.poly(TP([(x - 3.5, yb), (x + 3.5, yb), (x - side * 1.0, yb - 7 - 5 * J)])))
    for k in range(3):
        x = 122 + 6 * k
        teeth = np.maximum(teeth, pad.poly(TP([(x - 2.5, lip - 1), (x + 2.5, lip - 1), (x, lip + 4)])))
    st.over(teeth, 0.95, teeth)
    # ---- 코 (어둡게 · 빛 점)
    nose = pad.poly(TP(bez((116, 118), (128, 112), (140, 118), 8) + bez((140, 118), (136, 128), (128, 130), 6)[1:] + bez((128, 130), (120, 128), (116, 118), 6)[1:]))
    st.over(nose, 0.22, nose * 0.05)
    st.add_core(pad.disc_field(*T(124, 118), 2.2 * sc) * 0.9)
    # ---- 눈썹 뼈 (성난 — 코 쪽으로 내려옴) · 눈 (빛나는 아몬드 · 반짝임)
    for side in (-1, 1):
        bx0, by0 = T(128 + side * 44, 82)
        bx1, by1 = T(128 + side * 12, 92)
        st.body = st.body * (1 - pad.stroke([(bx0, by0), (bx1, by1)], 6.0 * sc, 4.0 * sc) * 0.45)
        st.add_core(pad.stroke([(bx0, by0 - 2), (bx1, by1 - 2)], 1.4 * sc, 0.8 * sc, a0=0.5, a1=0.8))
        e0, e1 = T(128 + side * 38, 92), T(128 + side * 16, 100)
        eye = lens(pad, e0 if side < 0 else e1, e1 if side < 0 else e0, 7.0 * sc, 1.0, power=0.6, skew=0.55)
        st.over(eye, 1.0, eye)
        ex, ey = T(128 + side * 26, 96)
        st.add_core(star4(pad, ex, ey, 9.0 * sc, 0.95, rot=0.2, thin=0.12, diag=0.4))
    # ---- 모여듦 (0 ~ 2): 바깥에서 안으로 소용돌이치는 기운 / 흩어짐 (7): 위로 흩어지는 가닥
    wisp = pad.zeros()
    if f <= 2:
        k = 1.0 - f / 3.0
        for i in range(10):
            a0 = TAU * i / 10 + 0.3
            pts = []
            for jj in range(20):
                u = jj / 19
                r = (120 - 70 * u) * (0.8 + 0.4 * k)
                a = a0 + 1.6 * u
                pts.append((128 + math.cos(a) * r, 120 + math.sin(a) * r * 0.85))
            wisp = np.maximum(wisp, pad.stroke(pts, 1.0, wfn=lambda t: 5.0 * (1 - t) + 1.0, afn=lambda t, k=k: k * 0.6 * np.sin(np.pi * t), soft=1.5))
        st.body = st.body * (0.55 + 0.45 * (f / 2.0))
    if f == 7:
        nz = fbm(X / 22.0, Y / 22.0 - 2.0, seed=31, octaves=3)
        keep = smooth(-0.05, 0.25, nz + (Y - 40) / 260.0)
        st.body = st.body * keep
        st.core = st.core * keep
        for i in range(12):
            x0 = 40 + 16 * i + rnd.uniform(-4, 4)
            y0 = rnd.uniform(60, 200)
            pts = [(x0 + 6 * math.sin(u * 3 + i), y0 - 50 * u) for u in np.linspace(0, 1, 12)]
            wisp = np.maximum(wisp, pad.stroke(pts, 1.0, wfn=lambda t: 4.0 * (1 - t) + 0.8, afn=lambda t: 0.5 * (1 - t), soft=1.2))
    core1 = pad.down(st.core)
    glow = obj_glow(pad, np.maximum(st.body, wisp), core1, halo=((1.5, 0.5), (4.0, 0.38), (9.0, 0.22)), body_k=0.92)
    a = BR_A[f]
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow * a) * wb, clamp01(core1 * a) * wb


sprite("rfx_jbear", "rotate_xyz", 256, 256, BR_N, 4, "warlord", zoom=4,
       desc="곰의 혼 (앞모습 · 일어선 곰): 0 ~ 2 기운이 소용돌이쳐 모임 → 3 ~ 6 포효 (이어 돌림 — 턱 · 털끝) → 7 위로 흩어짐. 성난 눈 · 송곳니 · 귀 · 양옆 앞발 발톱")(bear_frame)


# ============================================================ 0.51.0 마검사 2차 — 룬 글자 (재설계안 2판에서 룬 스물넷 · 가시 룬 ᚦ · 땅의 ᚦ 는 뺌)

GL_KINDS = (2, 7, 4)  # GLYPHS 번호 (지어낸 각진 글자 — 실제 룬 문자가 아님. 첫 그림: 6 은 열쇠 · 10 은 아치 창처럼 보여 바꿈)
GL_STEP = 6
GL_N = len(GL_KINDS) * GL_STEP


def _lines_len(lines):
    return [sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(ln, ln[1:])) for ln in lines]


def _cut_lines(lines, frac):
    """줄들을 차례로 이어 그었을 때 앞 frac (0 ~ 1) 만 — (자른 줄들, 긋는 끝 점)"""
    lens_ = _lines_len(lines)
    total = sum(lens_) or 1.0
    want = total * frac
    out, tip = [], None
    for ln, L in zip(lines, lens_):
        if want <= 0:
            break
        if want >= L:
            out.append(ln)
            tip = ln[-1]
            want -= L
            continue
        part = cut_polyline(ln, want / max(1e-6, L))
        if len(part) >= 2:
            out.append(part)
            tip = part[-1]
        want = 0
    return out, tip


def glyph_frame(f, S=192):
    kind = GL_KINDS[f // GL_STEP]
    step = f % GL_STEP
    pad = Pad(S, S)
    c = S / 2
    gw, gh, lw = 72.0, 104.0, 7.0
    lines, dots = glyph_lines(kind, c, c, gw, gh)
    rs = random.Random(9100 + f)
    body = pad.zeros()
    core = pad.zeros()
    if step <= 1:
        frac = 0.5 if step == 0 else 1.0
        part, tip = _cut_lines(lines, frac)
        for ln in part:
            body = np.maximum(body, pad.stroke(ln, lw, soft=1.2))
            core = np.maximum(core, pad.stroke(ln, lw * 0.36))
        if step == 1:
            for x, y in dots:
                r = lw * 1.25
                dm = pad.poly([(x, y - r), (x + r, y), (x, y + r), (x - r, y)])
                body = np.maximum(body, dm)
                core = np.maximum(core, dm * 0.8)
        if tip is not None:
            # 긋는 끝: 밝은 점 · 튀는 불티
            core = np.maximum(core, star4(pad, tip[0], tip[1], 16.0, 1.0, rot=0.3, thin=0.1, diag=0.35))
            body = np.maximum(body, pad.radial(tip[0], tip[1], 20.0, 1.4) * 0.8)
            for i in range(6):
                a = rs.uniform(0, TAU)
                d0, d1 = rs.uniform(5, 9), rs.uniform(14, 26)
                core = np.maximum(core, streak(pad, (tip[0] + math.cos(a) * d0, tip[1] + math.sin(a) * d0),
                                               (tip[0] + math.cos(a) * d1, tip[1] + math.sin(a) * d1), rs.uniform(1.2, 2.0), 0.9))
        if step == 1:
            body = np.maximum(body, pad.radial(c, c, 82.0, 1.5) * 0.55)
        return finish(pad, body, core, halo=((1.4, 0.5), (3.6, 0.4), (8.0, 0.26)), body_k=0.9, win=5.0)
    gm = glyph_mask(pad, kind, c, c, gw, gh, lw)
    gi = glyph_mask(pad, kind, c, c, gw, gh, lw * 0.36)
    if step in (2, 3):
        k = 1.0 if step == 2 else 0.86
        body = gm * k
        core = gi * (0.95 if step == 2 else 1.0)
        # 둘레 옅은 고리 (마법진 테) · 고리 위 작은 마름모 넷 (장마다 돌아 있음)
        rot = 0.0 if step == 2 else math.pi / 8
        ring = pad.stroke(arc_pts(c, c, 78.0, 0, TAU, n=96), 1.6, a0=0.38)
        body = np.maximum(body, pad.stroke(arc_pts(c, c, 78.0, 0, TAU, n=96), 5.0, a0=0.22, soft=1.5))
        core = np.maximum(core, ring)
        for i in range(4):
            a = rot + i * math.pi / 2
            x, y = c + math.cos(a) * 78.0, c + math.sin(a) * 78.0
            dm = pad.poly([(x, y - 5), (x + 3.4, y), (x, y + 5), (x - 3.4, y)])
            core = np.maximum(core, dm * 0.8)
        for i in range(4):
            a = rs.uniform(0, TAU)
            d = rs.uniform(30, 66)
            core = np.maximum(core, star4(pad, c + math.cos(a) * d, c + math.sin(a) * d, rs.uniform(3, 6), rs.uniform(0.5, 0.9), rot=rs.uniform(0, 1), thin=0.15, diag=0.0))
        return finish(pad, body, core, halo=((1.4, 0.5), (3.6, 0.42), (8.0, 0.28)), body_k=0.88, win=5.0)
    if step == 4:
        gm2 = glyph_mask(pad, kind, c, c, gw * 1.06, gh * 1.06, lw * 1.15)
        body = np.maximum(gm2, pad.radial(c, c, 92.0, 1.3) * 0.75)
        core = np.maximum(glyph_mask(pad, kind, c, c, gw * 1.06, gh * 1.06, lw * 0.5), star4(pad, c, c, 86.0, 1.0, rot=0.0, thin=0.05, diag=0.4, core=False))
        return finish(pad, body, core, halo=((1.4, 0.55), (4.0, 0.45), (9.0, 0.3)), body_k=0.92, win=5.0)
    # 5 터짐: 획이 조각나 바깥으로 흩어짐 · 퍼지는 고리
    for ln in lines:
        for a_, b_ in zip(ln, ln[1:]):
            L = math.hypot(b_[0] - a_[0], b_[1] - a_[1])
            n = max(1, int(L / 16))
            for i in range(n):
                p0 = (a_[0] + (b_[0] - a_[0]) * i / n, a_[1] + (b_[1] - a_[1]) * i / n)
                p1 = (a_[0] + (b_[0] - a_[0]) * (i + 0.7) / n, a_[1] + (b_[1] - a_[1]) * (i + 0.7) / n)
                mx, my = (p0[0] + p1[0]) / 2 - c, (p0[1] + p1[1]) / 2 - c
                d = math.hypot(mx, my) or 1.0
                push = 18.0 + rs.uniform(0, 10)
                ox, oy = mx / d * push, my / d * push
                q0, q1 = (p0[0] + ox, p0[1] + oy), (p1[0] + ox, p1[1] + oy)
                body = np.maximum(body, pad.stroke([q0, q1], lw * 0.8, a0=0.7))
                core = np.maximum(core, pad.stroke([q0, q1], lw * 0.28, a0=0.8))
    body = np.maximum(body, pad.stroke(arc_pts(c, c, 84.0, 0, TAU, n=96), 6.0, a0=0.35, soft=2.0))
    core = np.maximum(core, pad.stroke(arc_pts(c, c, 84.0, 0, TAU, n=96), 1.4, a0=0.5))
    g, cc = finish(pad, body, core, halo=((1.4, 0.45), (3.6, 0.36), (8.0, 0.22)), body_k=0.8, win=5.0)
    return g * 0.75, cc * 0.75


sprite("rfx_jglyph", "rotate_xyz", 192, 192, GL_N, 6, "runeknight", zoom=1,
       desc="공중에 새긴 룬 글자 셋 (종류마다 6장): 0 획이 반쯤 그어짐 · 1 다 그어지며 번쩍 → 2 ~ 3 빛남 (옅은 고리 · 이어 돌림) → 4 번쩍 → 5 조각나 흩어짐")(glyph_frame)


# ============================================================ 0.51.0 매지션 2차 (아크메이지 · 네크로맨서)
# 아크메이지: 빛 칸 = 밤하늘 자주 무리 · 심지 칸 = 금빛 별 (게임은 JobTraits8.NIGHT · GOLD — 별 · 별똥은 색을 정해 그림)
# 네크로맨서: 빛 칸 = 독초록 혼불 · 연기 · 심지 칸 = 뼈 (몸 — 짙게 · 금 · 틈은 비침)

from job_fx_art_v2 import SS, star_pts  # noqa: E402
from job_fx_art_v2b import star5_pts  # noqa: E402

COLORS.setdefault("arcmage", ((0.42, 0.26, 0.86), (1.0, 0.86, 0.42)))
COLORS.setdefault("necromancer", ((0.3, 0.92, 0.45), (0.96, 0.95, 0.88)))


# ------------------------------------------------------------ rfx_jstarnode (별자리의 별 — 카메라를 봄, 칸 가운데 = 별 가운데)

SN_N = 8


def _flares(pad, c, r, a, rot, thin=0.035):
    """네 갈래 긴 빛살 (가로 · 세로 — 가늘게 뾰족) + 짧은 대각 빛살"""
    out = pad.poly(star_pts(c, c, r, 4, thin, rot), a)
    out = np.maximum(out, pad.poly(star_pts(c, c, r * 0.42, 4, thin * 1.6, rot + math.pi / 4), a * 0.7))
    return out


def starnode_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(7100 + f)
    body = pad.zeros()
    core = pad.zeros()
    if f == 0:
        # 번쩍: 작은 밝은 점 · 긴 네 갈래 빛살 · 둥근 빛
        body = np.maximum(body, pad.radial(c, c, 46.0, 1.6))
        body = np.maximum(body, _flares(pad, c, 74.0, 0.8, 0.0, 0.03))
        core = np.maximum(core, pad.disc_field(c, c, 7.0, soft=2.0))
        core = np.maximum(core, _flares(pad, c, 62.0, 1.0, 0.0, 0.022))
    elif f <= 5:
        # 1 피어남 (작은 별) · 2 ~ 5 반짝임 (빛살 길이 · 돎 · 둘레 반짝이가 바뀜 — 이어 돌림)
        k = 0.72 if f == 1 else 1.0
        tw = [1.0, 0.86, 0.96, 0.8][(f - 2) % 4] if f >= 2 else 0.9
        rot = (f - 2) * math.radians(4.0) if f >= 2 else 0.0
        R = 38.0 * k
        star = pad.poly(star5_pts(c, c, R, inner=0.44, rot=0.0))
        body = np.maximum(body, star * 0.95)
        body = np.maximum(body, pad.radial(c, c, 70.0 * k, 1.8) * 0.85)
        body = np.maximum(body, _flares(pad, c, 88.0 * k * tw, 0.75, rot))
        core = np.maximum(core, pad.poly(star5_pts(c, c, R * 0.7, inner=0.42, rot=0.0)))
        core = np.maximum(core, _flares(pad, c, 78.0 * k * tw, 0.95, rot, 0.022))
        core = np.maximum(core, pad.disc_field(c, c, 6.0, soft=1.5))
        # 둘레를 도는 작은 반짝이 셋 (장마다 다른 자리)
        for i in range(3):
            a = (f * 0.9 + i * TAU / 3) % TAU
            rr = 52.0 * k + 6 * math.sin(f + i)
            x, y = c + math.cos(a) * rr, c + math.sin(a) * rr
            core = np.maximum(core, star4(pad, x, y, 7.0 + 2 * ((f + i) % 2), 0.9, rot=0.3 * i))
    elif f == 6:
        # 크게 번쩍
        R = 48.0
        body = np.maximum(body, pad.poly(star5_pts(c, c, R, inner=0.46)))
        body = np.maximum(body, pad.radial(c, c, 84.0, 1.4))
        body = np.maximum(body, _flares(pad, c, 94.0, 0.95, 0.12))
        core = np.maximum(core, pad.poly(star5_pts(c, c, R * 0.8, inner=0.44)))
        core = np.maximum(core, _flares(pad, c, 90.0, 1.0, 0.12, 0.028))
        core = np.maximum(core, pad.disc_field(c, c, 14.0, soft=4.0))
    else:
        # 별빛 조각으로 흩어짐: 바깥으로 날아가는 반짝이 · 옅은 별 자국
        body = np.maximum(body, pad.radial(c, c, 60.0, 2.4) * 0.45)
        core = np.maximum(core, pad.poly(star5_pts(c, c, 20.0, inner=0.44)) * 0.45)
        for i in range(10):
            a = i * TAU / 10 + rs.uniform(-0.2, 0.2)
            rr = rs.uniform(44, 74)
            x, y = c + math.cos(a) * rr, c + math.sin(a) * rr
            core = np.maximum(core, star4(pad, x, y, rs.uniform(6, 11), 0.9, rot=rs.uniform(0, 1)))
            body = np.maximum(body, pad.stroke([(c + math.cos(a) * rr * 0.55, c + math.sin(a) * rr * 0.55), (x, y)], 1.0, 4.0, a0=0.0, a1=0.6))
    g, cc = finish(pad, body, core, halo=((1.5, 0.55), (4.0, 0.45), (10.0, 0.32)), body_k=0.9, core_bloom=((1.5, 0.45), (4.0, 0.32)), win=4.0)
    return g, cc


sprite("rfx_jstarnode", "rotate_xyz", 192, 192, SN_N, 4, "arcmage", zoom=3,
       desc="별자리의 별: 0 번쩍 · 1 피어남 → 2 ~ 5 반짝임 (이어 돌림) → 6 크게 번쩍 · 7 별빛 조각으로 흩어짐")(starnode_frame)


# ------------------------------------------------------------ rfx_jfallstar (별똥 — 가는 쪽으로 누움, 머리 = 오른쪽)

FS_N = 4
FS_HEAD = (226.0, 48.0)


def fallstar_frame(f, W=256, H=96):
    pad = Pad(W, H)
    hx, hy = FS_HEAD
    rs = random.Random(7200 + f)
    body = pad.zeros()
    core = pad.zeros()
    # 꼬리: 머리에서 왼쪽으로 길게 가늘어짐 (조금 물결)
    tail = [(hx - 210 * i / 24, hy + 2.5 * math.sin(i * 0.7 + f * 1.4) * (i / 24)) for i in range(25)]
    tail = tail[::-1]
    body = np.maximum(body, pad.stroke(tail, 2.0, 26.0, a0=0.0, a1=0.9, soft=4.0))
    core = np.maximum(core, pad.stroke(tail, 0.6, 7.0, a0=0.0, a1=1.0, soft=1.0))
    # 반짝이 가루 (꼬리를 따라 — 장마다 다른 자리 · 이어 돌림)
    for i in range(9):
        t = (i / 9 + f / (FS_N * 9)) % 1
        x = hx - 30 - 170 * t
        y = hy + rs.uniform(-14, 14) * (0.4 + t)
        r = 6.0 * (1 - t) + 2.5
        core = np.maximum(core, star4(pad, x, y, r, 0.9 * (1 - t * 0.7), rot=rs.uniform(0, 1)))
    # 머리: 별 · 둥근 빛 · 짧은 네 갈래 빛살 (장마다 조금 돎)
    body = np.maximum(body, pad.radial(hx, hy, 34.0, 1.5))
    body = np.maximum(body, pad.poly(star5_pts(hx, hy, 17.0, inner=0.45, rot=f * 0.3)))
    core = np.maximum(core, pad.poly(star5_pts(hx, hy, 12.0, inner=0.44, rot=f * 0.3)))
    core = np.maximum(core, pad.poly(star_pts(hx, hy, 30.0 * [1.0, 0.85, 0.95, 0.8][f], 4, 0.05, 0.15 * f), 0.9))
    core = np.maximum(core, pad.disc_field(hx, hy, 4.5, soft=1.0))
    g, cc = finish(pad, body, core, halo=((1.5, 0.5), (3.5, 0.4), (8.0, 0.25)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.3)), win=3.5)
    return g, cc


sprite("rfx_jfallstar", "lookat_direction", 256, 96, FS_N, 4, "arcmage", zoom=0, loop=True,
       desc="별똥 (오른쪽 = 나아가는 쪽 · 머리 x 0.88 W): 빛나는 별 머리 · 길게 가늘어지는 꼬리 · 꼬리를 따라 흐르는 반짝이 가루 (이어 돌림)")(fallstar_frame)


# ------------------------------------------------------------ rfx_jbonespear (뼈 창 — 가는 쪽으로 누움, 창끝 = 오른쪽)

BS_N = 4


def bonespear_frame(f, W=256, H=64):
    pad = Pad(W, H)
    cy = H / 2
    rs = random.Random(7300 + f)
    bone = pad.zeros()
    dark = pad.zeros()
    glow = pad.zeros()
    # 자루: 등뼈 마디 (x 30 ~ 176) — 둥근 마디 · 위아래 작은 돌기 · 마디 사이 틈
    x = 30.0
    while x < 172:
        w = 13.0
        bone = np.maximum(bone, pad.ellipse(x + w / 2, cy, w / 2 + 0.5, 5.2))
        bone = np.maximum(bone, pad.poly([(x + 3, cy - 4), (x + 6, cy - 9.5), (x + 9, cy - 4)]))
        bone = np.maximum(bone, pad.poly([(x + 4, cy + 4), (x + 7, cy + 8.5), (x + 10, cy + 4)]))
        dark = np.maximum(dark, pad.stroke([(x + w + 0.2, cy - 4), (x + w + 0.2, cy + 4)], 1.6))
        x += w + 1.4
    # 꼬리 끝 혹 (작은 머리뼈 마디)
    bone = np.maximum(bone, pad.ellipse(26, cy, 7.5, 7.0))
    dark = np.maximum(dark, pad.disc_field(24, cy - 1.5, 1.8))
    # 창날: 이빨처럼 들쭉날쭉한 뼈 날 (x 172 ~ 248)
    blade = [(170, cy - 7), (186, cy - 11), (196, cy - 8), (204, cy - 12), (214, cy - 7), (224, cy - 9), (236, cy - 4), (249, cy + 0.5),
             (236, cy + 5), (224, cy + 9), (214, cy + 7), (204, cy + 12), (196, cy + 8), (186, cy + 11), (170, cy + 7)]
    bone = np.maximum(bone, pad.poly(blade))
    dark = np.maximum(dark, pad.stroke([(176, cy), (240, cy + 0.3)], 1.4, 0.6, a0=0.8))
    # 갈비 가시 (날 밑동에서 뒤로 휜 갈고리 — 위 · 아래)
    for sgn in (-1, 1):
        hook = bez((168, cy + sgn * 6), (160, cy + sgn * 22), (146, cy + sgn * 24), n=14)
        bone = np.maximum(bone, pad.stroke(hook, 5.0, 1.2))
    # 녹색 혼불: 몸을 감싸는 빛 · 뒤로 끌리는 연기 가닥 (장마다 물결이 뒤로 흐름 — 이어 돌림)
    glow = np.maximum(glow, gblur(bone, 3.0 * SS) * 1.4)
    for k in range(3):
        ph = f * TAU / BS_N + k * 2.1
        pts = [(26 - 4 * i, cy + (k - 1) * 6 + 5 * math.sin(i * 0.55 + ph)) for i in range(7)]
        glow = np.maximum(glow, pad.stroke(pts, 7.0, 1.0, a0=0.85, a1=0.0, soft=2.0))
    for i in range(4):
        t = (i / 4 + f / (BS_N * 4)) % 1
        wx = 40 + 130 * t
        wy = cy + (8 + 4 * math.sin(i + f)) * (1 if i % 2 else -1)
        glow = np.maximum(glow, pad.disc_field(wx, wy, 3.0 + 2 * (1 - t), soft=2.0) * 0.8)
    body = screen(glow * 0.9, bone * 0.25)
    core = clamp01(bone - dark * 0.6)
    g = pad.down(body)
    g = screen(g, gblur(g, 2.0) * 0.6, gblur(g, 5.0) * 0.4)
    cc = pad.down(core)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jbonespear", "lookat_direction", 256, 64, BS_N, 4, "necromancer", zoom=0, loop=True,
       desc="뼈 창 (오른쪽 = 나아가는 쪽 · 창끝 x 0.97 W): 들쭉날쭉한 이빨 뼈 날 · 등뼈 마디 자루 · 뒤로 휜 갈비 가시 · 꼬리 혹 · 녹색 혼불 감쌈 · 뒤로 끌리는 연기 (이어 돌림)")(bonespear_frame)


# ------------------------------------------------------------ rfx_jbonewall (뼈 방벽 — 세워 카메라 쪽, 아래 끝 = 땅)

BW_N = 8
# 뼈 가시: (밑동 x, 높이, 기울기 (위 끝이 옆으로), 밑동 폭)
BW_SPIKES = [(128, 206, 6, 30), (88, 158, -26, 26), (170, 170, 24, 26), (54, 108, -34, 20), (204, 118, 34, 20), (110, 96, -10, 16), (150, 104, 12, 16)]


def _spike(pad, bx, h, lean, w, base_y, frac):
    """휘어 솟은 뼈 가시 (밑동 bx · 높이 h · 위 끝이 lean 만큼 옆) — frac = 땅 위로 나온 몫 (아래부터)"""
    pts = bez((bx, base_y), (bx + lean * 0.2, base_y - h * 0.55), (bx + lean, base_y - h), n=20)
    shown = max(2, int(round(len(pts) * frac)))
    seg = pts[:shown]
    # 땅에서 위로 솟는 중이면 전체 모양을 아래로 밀어 (끝이 먼저 보이게)
    if frac < 1:
        dy = h * (1 - frac)
        seg = [(x, y + dy) for x, y in pts]
    poly = taper_poly(seg, lambda t: w / 2 * (1 - t) ** 0.8 + 0.6)
    m = pad.poly(poly)
    # 마디 혹 (가시를 따라 두 곳)
    for t in (0.3, 0.58):
        i = int(t * (len(seg) - 1))
        x, y = seg[i]
        m = np.maximum(m, pad.ellipse(x, y, w * (1 - t) * 0.62, w * 0.22))
    clip = pad.zeros()
    clip[: int(base_y * pad.ss), :] = 1
    return m * clip, seg


def bonewall_frame(f, S=256):
    pad = Pad(S, S)
    base = S - 10.0
    rs = random.Random(7400 + f)
    bone = pad.zeros()
    dark = pad.zeros()
    glow = pad.zeros()
    if f <= 5:
        frac = [0.3, 0.66, 1.0, 1.0, 1.0, 1.0][f]
        for i, (bx, h, lean, w) in enumerate(BW_SPIKES):
            fr = min(1.0, frac * (1.15 if i == 0 else 1.0))
            m, seg = _spike(pad, bx, h, lean, w, base, fr)
            bone = np.maximum(bone, m)
            # 금 (가시를 따라 가는 어두운 줄)
            pts = [(x + 2, y) for x, y in seg[2:-3]]
            if len(pts) > 2:
                dark = np.maximum(dark, pad.stroke(pts, 1.4, 0.6, a0=0.7) * m)
        # 밑동: 갈라진 땅 · 튀는 흙 (솟는 동안) · 녹색 빛
        glow = np.maximum(glow, pad.radial(S / 2, base, 120.0, 2.0, sy=0.22) * 0.9)
        if f <= 2:
            for i in range(10):
                x = rs.uniform(30, S - 30)
                y = base - rs.uniform(10, 70) * (f + 1) / 3
                bone = np.maximum(bone, pad.disc_field(x, y, rs.uniform(2.0, 4.5)))
        # 혼불이 가시를 타고 오름 (장마다 다른 높이 — 이어 돌림 3 ~ 5)
        for i in range(5):
            t = ((i * 0.37 + f * 0.33) % 1)
            bx, h, lean, w = BW_SPIKES[i % len(BW_SPIKES)]
            x = bx + lean * t * 0.8
            y = base - h * t * 0.9
            glow = np.maximum(glow, pad.disc_field(x, y, 9.0 * (1 - t) + 3, soft=5.0) * 0.85)
        glow = np.maximum(glow, gblur(bone, 4.0 * SS) * 1.2)
    else:
        # 6 금이 벌어짐 · 7 조각으로 흩어짐
        push = 0.0 if f == 6 else 1.0
        for i, (bx, h, lean, w) in enumerate(BW_SPIKES):
            m, seg = _spike(pad, bx, h, lean, w, base, 1.0)
            if f == 6:
                bone = np.maximum(bone, m)
                for k in range(4):
                    y = base - h * rs.uniform(0.2, 0.85)
                    dark = np.maximum(dark, pad.stroke([(bx - 14, y), (bx + lean * 0.5, y + rs.uniform(-6, 6)), (bx + lean + 14, y - 3)], 2.2) * m)
            else:
                for k in range(5):
                    t = rs.uniform(0.1, 0.95)
                    idx = int(t * (len(seg) - 1))
                    x, y = seg[idx]
                    dx = (x - S / 2) * 0.25 + rs.uniform(-20, 20)
                    dy = -rs.uniform(10, 30)
                    r = w * (1 - t) * 0.45 + 2
                    chip = [(x + dx + rs.uniform(-r, r), y + dy + rs.uniform(-r, r)) for _ in range(4)]
                    bone = np.maximum(bone, pad.poly(chip) * 0.85)
        glow = np.maximum(glow, gblur(bone, 5.0 * SS) * (1.0 if f == 6 else 0.7))
        glow = np.maximum(glow, pad.radial(S / 2, base - 40, 110.0, 2.0) * (0.7 if f == 6 else 0.35))
    body = screen(glow * 0.85, bone * 0.2)
    core = clamp01(bone - dark * 0.7)
    g = pad.down(body)
    g = screen(g, gblur(g, 3.0) * 0.5, gblur(g, 7.0) * 0.35)
    cc = pad.down(core)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jbonewall", "lookat_y", 256, 256, BW_N, 4, "necromancer", zoom=3,
       desc="뼈 방벽 (아래 끝 = 땅): 0 ~ 2 휘어진 뼈 가시 일곱이 땅을 뚫고 솟음 (흙 튐) → 3 ~ 5 서 있음 (녹색 혼불이 가시를 타고 오름 — 이어 돌림) → 6 금이 벌어짐 · 7 조각으로 흩어짐")(bonewall_frame)


# ------------------------------------------------------------ rfx_jskull (망자의 해골 — 카메라를 봄, 칸 가운데 = 해골 가운데)

SK_N = 6


def _skull_masks(pad, c, jaw=0.0, k=1.0):
    """해골 (뼈 · 구멍): 둥근 머리 · 광대 · 눈구멍 둘 · 코 구멍 · 위 이빨 · 아래턱 (jaw 만큼 벌어짐)"""
    cx, cy = c, c - 6 * k
    skull = pad.ellipse(cx, cy - 6 * k, 34 * k, 32 * k)
    skull = np.maximum(skull, pad.poly([(cx - 26 * k, cy + 4 * k), (cx + 26 * k, cy + 4 * k), (cx + 18 * k, cy + 24 * k), (cx - 18 * k, cy + 24 * k)]))
    holes = pad.ellipse(cx - 13 * k, cy + 2 * k, 9.5 * k, 8.5 * k)
    holes = np.maximum(holes, pad.ellipse(cx + 13 * k, cy + 2 * k, 9.5 * k, 8.5 * k))
    holes = np.maximum(holes, pad.poly([(cx, cy + 9 * k), (cx - 4.5 * k, cy + 17 * k), (cx + 4.5 * k, cy + 17 * k)]))
    # 위 이빨 줄 (틈)
    teeth_gap = pad.zeros()
    for i in range(5):
        x = cx - 10 * k + i * 5 * k
        teeth_gap = np.maximum(teeth_gap, pad.stroke([(x, cy + 19 * k), (x, cy + 25 * k)], 1.1 * k))
    # 아래턱 (jaw 만큼 아래로)
    jy = cy + 27 * k + jaw
    jawm = pad.poly([(cx - 17 * k, jy), (cx + 17 * k, jy), (cx + 12 * k, jy + 12 * k), (cx - 12 * k, jy + 12 * k)])
    for i in range(4):
        x = cx - 7.5 * k + i * 5 * k
        teeth_gap = np.maximum(teeth_gap, pad.stroke([(x, jy), (x, jy + 4 * k)], 1.0 * k))
    bone = clamp01(np.maximum(skull, jawm) - holes)
    return bone, holes, teeth_gap, (cx - 13 * k, cy + 2 * k), (cx + 13 * k, cy + 2 * k)


def skull_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(7500 + f)
    jaw = 9.0 if f == 4 else 0.0
    k = [0.9, 1.0, 1.0, 1.0, 1.05, 1.08][f]
    bone, holes, gaps, e1, e2 = _skull_masks(pad, c, jaw, k)
    smoke = pad.zeros()
    # 연기 가닥: 0 은 바깥에서 모여듦 · 5 는 위로 흩어짐 · 그 사이는 해골 둘레를 흐름
    for i in range(7):
        a = i * TAU / 7 + f * 0.4
        r0 = 58.0 if f == 0 else 44.0
        r1 = 30.0 if f == 0 else 54.0
        pts = []
        for s_ in range(8):
            t = s_ / 7
            rr = r0 + (r1 - r0) * t
            aa = a + 0.5 * t
            pts.append((c + math.cos(aa) * rr, c + math.sin(aa) * rr - (24 * t if f == 5 else 0)))
        smoke = np.maximum(smoke, pad.stroke(pts, 9.0, 2.0, a0=0.6, a1=0.0, soft=3.0))
    eye = [1.0, 1.0, 0.75, 0.9, 1.25, 0.6][f]
    glowm = np.maximum(smoke, gblur(bone, 3.0 * SS) * 1.3)
    for ex, ey in (e1, e2):
        glowm = np.maximum(glowm, pad.disc_field(ex, ey, 9.0 * eye, soft=4.0))
        glowm = np.maximum(glowm, pad.radial(ex, ey, 20.0 * eye, 2.0) * 0.8)
    if f == 0:
        bone = bone * 0.45
    if f == 5:
        bone = gblur(bone, 2.5 * SS) * 0.55
    core = clamp01(bone - gaps * 0.9)
    # 눈 속 밝은 점 (심지 — 혼불 심)
    if f != 5:
        for ex, ey in (e1, e2):
            core = np.maximum(core, pad.disc_field(ex, ey, 2.4 * eye, soft=1.0) * 0.9)
    body = screen(glowm * 0.9, bone * 0.2)
    g = pad.down(body)
    g = screen(g, gblur(g, 2.0) * 0.5, gblur(g, 5.0) * 0.35)
    cc = pad.down(core)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jskull", "rotate_xyz", 128, 128, SK_N, 6, "necromancer", zoom=2,
       desc="망자의 해골: 0 연기가 모여 해골 · 1 ~ 3 눈구멍의 혼불이 맥동 (이어 돌림) · 4 턱이 벌어지며 비명 · 5 연기로 흩어짐")(skull_frame)


# ------------------------------------------------------------ rfx_jrift (갈라진 땅 — 땅에 누움, 그림 위 = 금이 뻗는 쪽, 칸 가운데 = 금 가운데)

RF_N = 6


def _rift_line(rs, W, H, amp):
    pts = []
    n = 22
    for i in range(n + 1):
        t = i / n
        y = 16 + (H - 32) * t
        x = W / 2 + (rs.uniform(-amp, amp) if 0 < i < n else 0)
        pts.append((x, y))
    return pts


def rift_frame(f, W=128, H=320):
    pad = Pad(W, H)
    rs = random.Random(7600)
    line = _rift_line(rs, W, H, 7.0)
    rs2 = random.Random(7610 + f)
    width = [2.5, 7.0, 12.0, 13.0, 13.0, 5.0][f]
    glow_k = [0.5, 0.75, 1.0, 1.0, 0.85, 0.45][f]
    # 틈 (가운데가 넓고 양 끝이 가늘어짐)
    gap = pad.stroke(line, width, wfn=lambda s: width * np.maximum(0.0, np.sin(np.pi * np.clip(s, 0, 1))) ** 0.6 + 0.8, soft=1.0)
    body = np.maximum(gblur(gap, 6.0 * SS) * 1.4, gap) * glow_k
    # 심지 = 틈 가운데의 가는 빛 (틈 폭의 0.4 — 둘레는 녹색 빛이 보이게)
    core = pad.stroke(line, width, wfn=lambda s: (width * np.maximum(0.0, np.sin(np.pi * np.clip(s, 0, 1))) ** 0.6 + 0.8) * 0.4, soft=0.8) * glow_k
    # 가지 금 (곁으로 뻗는 짧은 금)
    for i in range(7):
        t = 0.1 + 0.8 * i / 6
        idx = int(t * (len(line) - 1))
        x, y = line[idx]
        side = 1 if i % 2 else -1
        pts = [(x, y), (x + side * rs.uniform(10, 20), y + rs.uniform(-12, 12)), (x + side * rs.uniform(20, 34), y + rs.uniform(-18, 18))]
        core = np.maximum(core, pad.stroke(pts, 2.0 * min(1, width / 6), 0.5, a0=0.8 * glow_k, a1=0.1))
        body = np.maximum(body, pad.stroke(pts, 6.0, 1.0, a0=0.5 * glow_k, a1=0.0, soft=2.0))
    # 돌조각 · 불티 (벌어질 때 · 맥동 때 솟는 불티)
    if f in (1, 2):
        for i in range(12):
            t = rs2.uniform(0.1, 0.9)
            x, y = line[int(t * (len(line) - 1))]
            core = np.maximum(core, pad.disc_field(x + rs2.uniform(-22, 22), y + rs2.uniform(-10, 10), rs2.uniform(1.5, 3.2)) * 0.9)
    if f in (3, 4):
        for i in range(8):
            t = rs2.uniform(0.1, 0.9)
            x, y = line[int(t * (len(line) - 1))]
            core = np.maximum(core, star4(pad, x + rs2.uniform(-6, 6), y, rs2.uniform(4, 7), 0.85))
            body = np.maximum(body, pad.disc_field(x, y, 10.0, soft=6.0) * 0.6)
    g = pad.down(body)
    g = screen(g, gblur(g, 3.0) * 0.5)
    cc = pad.down(core)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jrift", "direction_z", 128, 320, RF_N, 6, "necromancer", zoom=3,
       desc="갈라진 땅 (그림 위 = 금이 뻗는 쪽): 0 가는 금 · 1 ~ 2 벌어지며 돌조각 → 3 ~ 4 틈의 녹색 빛 맥동 (불티 — 이어 돌림) → 5 닫히며 흐려짐")(rift_frame)


# ============================================================ 0.51.0 궁수 2차 (스나이퍼 · 섀도 헌터)
# 스나이퍼: 빛 칸 = 에길 연두 무리 (JobKit.glow) · 심지 칸 = 흰 금빛 (JobTraits9.SNIPE — 과녁 선 · 촉 · 섬광 깃)
# 섀도 헌터: 빛 칸 = 먹빛 몸 (JobTraits9.DUSK — 짙은 남색: 화살대 · 깃 · 연기 · 가시덤불을 거의 불투명하게) · 심지 칸 = 은빛 (SILVER — 촉 · 테 · 눈)

COLORS.setdefault("sniper", ((0.72, 1.0, 0.38), (1.0, 0.98, 0.84)))
COLORS.setdefault("shadowhunter", ((0.12, 0.1, 0.34), (0.86, 0.9, 1.0)))


def _arcs4(pad, c, R, rot, gap, w, a=1.0, soft=0.0):
    """원 R 을 네 토막 (rot + 0 · 90 · 180 · 270 도 자리에 틈 — 반쪽 틈 gap 라디안)"""
    out = pad.zeros()
    for q in range(4):
        a0 = rot + q * math.pi / 2 + gap
        a1 = rot + (q + 1) * math.pi / 2 - gap
        out = np.maximum(out, pad.stroke(arc_pts(c, c, R, a0, a1, n=20), w, a0=a, soft=soft))
    return out


# ------------------------------------------------------------ rfx_jweak (약점 과녁 — 카메라를 봄, 칸 가운데 = 과녁 가운데)

WK_N = 6
# 장마다: 테 반지름 · 꺾쇠 반지름 · 꺾쇠 돎 (도) · 밝기 · 가운데 점 밝기 · 안쪽 고리 반지름
WK_R = [46.0, 38.0, 38.0, 38.0, 40.0, 47.0]
WK_B = [55.0, 47.0, 47.0, 47.0, 49.0, 55.0]
WK_ROT = [-40.0, 0.0, 30.0, 60.0, 70.0, 80.0]
WK_A = [0.62, 1.0, 0.92, 0.96, 1.0, 0.42]
WK_DOT = [0.45, 1.0, 0.68, 0.86, 0.0, 0.0]
WK_IN = [17.0, 13.0, 14.4, 13.7, 15.0, 0.0]


def _brackets(pad, c, R, rot, w, a):
    """대각 네 자리의 꺾쇠 (짧은 호 + 가운데를 가리키는 작은 쐐기) — 4 겹 대칭이라 30 도씩 세 장 돌면 처음과 이어짐"""
    out = pad.zeros()
    for q in range(4):
        th = rot + math.pi / 4 + q * math.pi / 2
        out = np.maximum(out, pad.stroke(arc_pts(c, c, R, th - 0.24, th + 0.24, n=12), w, a0=a))
        ux, uy = math.cos(th), math.sin(th)
        tx, ty = -uy, ux
        bx, by = c + ux * (R - w * 0.4), c + uy * (R - w * 0.4)
        tipx, tipy = c + ux * (R - 7.0), c + uy * (R - 7.0)
        out = np.maximum(out, pad.poly([(bx + tx * 3.6, by + ty * 3.6), (tipx, tipy), (bx - tx * 3.6, by - ty * 3.6)], a))
    return out


def weak_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(8100 + f)
    R, Rb, rot, A, dot, rin = WK_R[f], WK_B[f], math.radians(WK_ROT[f]), WK_A[f], WK_DOT[f], WK_IN[f]
    body = pad.zeros()
    core = pad.zeros()
    if f <= 4:
        # 바깥 테 (네 토막 — 가로 · 세로 자리에 틈) · 틈으로 들어오는 눈금 · 대각 꺾쇠 · 안쪽 고리 · 가는 십자 · 가운데 사과빛 점
        # 0.51.0 영상 1 뒤: 선을 굵게 (머리 위 과녁은 1칸 안팎 — 가는 선은 화면에서 사라졌음)
        ring_c = _arcs4(pad, c, R, 0.0, 0.2, 3.4, A)
        ring_b = _arcs4(pad, c, R, 0.0, 0.2, 8.5, A * 0.85, soft=2.0)
        ticks_c = pad.zeros()
        ticks_b = pad.zeros()
        for q in range(4):
            ux, uy = math.cos(q * math.pi / 2), math.sin(q * math.pi / 2)
            p0 = (c + ux * (R + 7.0), c + uy * (R + 7.0))
            p1 = (c + ux * (R - 11.0), c + uy * (R - 11.0))
            ticks_c = np.maximum(ticks_c, pad.stroke([p0, p1], 3.6, 2.0, a0=A, a1=A * 0.85))
            ticks_b = np.maximum(ticks_b, pad.stroke([p0, p1], 8.0, 5.0, a0=A * 0.8, a1=A * 0.5, soft=1.8))
        br_c = _brackets(pad, c, Rb, rot, 3.6, A * (0.95 if f else 0.8))
        br_b = gblur(br_c, 2.0 * SS) * 1.6
        core = np.maximum.reduce([core, ring_c, ticks_c, br_c])
        body = np.maximum.reduce([body, ring_b, ticks_b, br_b])
        if f < 4:
            inner = pad.stroke(arc_pts(c, c, rin, 0, TAU, n=40), 2.4, a0=A * 0.8)
            core = np.maximum(core, inner)
            body = np.maximum(body, pad.stroke(arc_pts(c, c, rin, 0, TAU, n=40), 6.0, a0=A * 0.5, soft=1.6))
            for q in range(4):
                ux, uy = math.cos(q * math.pi / 2), math.sin(q * math.pi / 2)
                core = np.maximum(core, pad.stroke([(c + ux * 4.5, c + uy * 4.5), (c + ux * (rin - 2.5), c + uy * (rin - 2.5))], 1.8, a0=A * 0.75))
            # 사과빛 점 (가운데 — 깜빡임) · 둘레 무리
            core = np.maximum(core, pad.disc_field(c, c, 3.2 + 3.0 * dot, soft=1.0) * (0.6 + 0.4 * dot))
            body = np.maximum(body, pad.radial(c, c, 9.0 + 9.0 * dot, 1.6) * dot)
        else:
            # 맞음: 가운데가 깨지며 흰 섬광 — 갈라진 금 일곱 · 큰 네 뿔 별 · 깨진 조각
            core = np.maximum(core, star4(pad, c, c, 40.0, 1.0, rot=0.0, thin=0.08, diag=0.5))
            core = np.maximum(core, pad.disc_field(c, c, 7.5, soft=2.5))
            body = np.maximum(body, pad.radial(c, c, 34.0, 1.3))
            for i in range(7):
                a = i * TAU / 7 + rs.uniform(-0.2, 0.2)
                pts = [(c, c)]
                r = 0.0
                while r < 30:
                    r += rs.uniform(5, 9)
                    aa = a + rs.uniform(-0.25, 0.25)
                    pts.append((c + math.cos(aa) * r, c + math.sin(aa) * r))
                core = np.maximum(core, pad.stroke(pts, 1.8, 0.5, a0=1.0, a1=0.4))
                body = np.maximum(body, pad.stroke(pts, 4.5, 1.5, a0=0.8, a1=0.2, soft=1.2))
                x, y = pts[-1]
                core = np.maximum(core, pad.poly([(x + rs.uniform(-3, 3), y + rs.uniform(-3, 3)) for _ in range(3)], 0.85))
    else:
        # 흐려짐: 테 · 꺾쇠가 퍼지며 옅어짐 · 조각이 바깥으로
        core = np.maximum(core, _arcs4(pad, c, R, 0.0, 0.32, 1.2, A))
        body = np.maximum(body, _arcs4(pad, c, R, 0.0, 0.32, 4.0, A * 0.7, soft=1.6))
        core = np.maximum(core, _brackets(pad, c, Rb, rot, 1.6, A * 0.8))
        body = np.maximum(body, pad.radial(c, c, 26.0, 2.0) * 0.25)
        for i in range(9):
            a = rs.uniform(0, TAU)
            r = rs.uniform(18, 40)
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            core = np.maximum(core, pad.stroke([(c + math.cos(a) * (r - 6), c + math.sin(a) * (r - 6)), (x, y)], 0.4, 1.6, a0=0.0, a1=0.6))
    g, cc = finish(pad, body, core, halo=((1.4, 0.55), (3.5, 0.42), (8.0, 0.26)), body_k=0.88, core_bloom=((1.2, 0.4), (3.0, 0.3)), win=4.0)
    return g, cc


sprite("rfx_jweak", "rotate_xyz", 128, 128, WK_N, 6, "sniper", zoom=1,
       desc="약점 과녁: 0 조여 들며 나타남 · 1 ~ 3 맥동 (꺾쇠가 30 도씩 돎 · 가운데 사과빛 점이 깜빡임 — 이어 돌림) · 4 맞음 (가운데가 깨지며 흰 섬광) · 5 흐려짐")(weak_frame)


# ------------------------------------------------------------ rfx_jgreatarrow (거대한 셋째 화살 — 가는 쪽으로 누움, 촉 = 오른쪽)

GA_N = 4


def greatarrow_frame(f, W=320, H=96):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy, tip, hb, sock0, s0 = 48.0, 312.0, 264.0, 250.0, 30.0
    ph = f * TAU / GA_N
    st = Stack(pad)

    # ---- 감아 도는 바람 띠 (두 가닥 · 촉 쪽은 좁게 감고 뒤로 갈수록 넓어짐) — 뒤로 흐름 (장마다 1/4 물결)
    def band(k, front):
        out_b = pad.zeros()
        out_c = pad.zeros()
        xs = np.linspace(66.0, 300.0, 120)
        pts, fr = [], []
        for x in xs:
            t = (x - 66.0) / (300.0 - 66.0)
            amp = 5.0 + 21.0 * (1 - t) ** 0.9
            th = TAU * x / 58.0 + ph + k * math.pi
            pts.append((x, cy + amp * math.sin(th)))
            fr.append(math.cos(th))
        # 앞 (cos > 0) · 뒤 를 토막으로 나눠 그림
        seg = []
        for i, (p, cf) in enumerate(zip(pts, fr)):
            on = (cf > 0) == front
            if on:
                seg.append((p, i / (len(pts) - 1)))
            if (not on or i == len(pts) - 1) and len(seg) >= 2:
                sp_ = [q for q, _ in seg]
                s_a = seg[0][1]
                s_b = seg[-1][1]
                fade = lambda s, a=s_a, b=s_b: (np.minimum(1.0, (a + (b - a) * s) / 0.12) * np.minimum(1.0, (1 - (a + (b - a) * s)) / 0.08))
                wfn = lambda s, a=s_a, b=s_b: 1.4 + 3.6 * (1 - (a + (b - a) * s))
                out_b = np.maximum(out_b, pad.stroke(sp_, 4.0, wfn=lambda s, w=wfn: w(s) * 2.2, afn=lambda s, fd=fade: fd(s) * (0.8 if front else 0.42), soft=1.4))
                out_c = np.maximum(out_c, pad.stroke(sp_, 2.0, wfn=lambda s, w=wfn: w(s) * 0.45, afn=lambda s, fd=fade: fd(s) * (0.85 if front else 0.22)))
                seg = []
            elif not on:
                seg = []
        return out_b, out_c

    for k in (0, 1):
        bb, bc = band(k, False)
        st.body = np.maximum(st.body, bb)
        st.add_core(bc)
    # ---- 빠르게 흐르는 바람 줄 (뒤로 — 주기 4 장)
    rw = random.Random(8211)
    for i in range(10):
        b = rw.random()
        q = (b + f / GA_N) % 1.0
        x1 = rw.uniform(150, 250) - 120 * q
        L = rw.uniform(30, 70)
        y = cy + rw.choice((-1, 1)) * rw.uniform(12, 34)
        al = math.sin(math.pi * q) * rw.uniform(0.35, 0.7)
        st.body = np.maximum(st.body, pad.stroke([(x1 - L, y), (x1, y)], 0.6, 2.6, a0=0.0, a1=al, soft=0.8))
        st.add_core(pad.stroke([(x1 - L * 0.6, y), (x1, y)], 0.3, 1.0, a0=0.0, a1=al * 0.8))
    # ---- 섬광 깃: 위 · 아래 깃 (빛으로 된 깃 — 뒤 끝이 비스듬히 잘림 · 깃가지 결) + 가운데 좁은 깃 · 깃 끝에서 흩날리는 빛
    for sg in (-1, 1):
        edge = bez((s0 + 76, cy + sg * 3.0), (s0 + 44, cy + sg * 5.5), (s0 + 12, cy + sg * 23.0), 18)
        vane = [(s0 + 2, cy + sg * 3.0)] + edge + [(s0 + 4, cy + sg * 23.5), (s0 - 4, cy + sg * 13.0)]
        vm = pad.poly(vane)
        # 깃가지 결 (뒤 · 바깥으로 비스듬히) · 살대 쪽이 밝고 바깥으로 옅어짐 · 가장자리 테
        barbs = 0.5 + 0.5 * np.cos((X - s0 + sg * (Y - cy) * 1.25) * TAU / 4.0)
        dist = clamp01((np.abs(Y - cy) - 3.0) / 21.0)
        vcore = vm * (0.86 - 0.34 * dist) * (0.8 + 0.2 * barbs)
        vcore = np.maximum(vcore, rim_of(vm, 0.9) * 0.95)
        vcore = np.maximum(vcore, pad.stroke(edge[2:], 1.0, 0.4, a0=1.0, a1=0.6) * vm)
        st.over(vm, 0.9 - 0.2 * dist, vcore)
        for i in range(4):
            q = (i / 4 + f / GA_N) % 1.0
            x = s0 + 8 - 26 * q
            y = cy + sg * (16 + 10 * q + 4 * i % 3)
            al = (1 - q) * 0.75
            st.add_core(pad.stroke([(x - 8, y + sg * 2), (x + 6, y)], 0.4, 1.8, a0=0.0, a1=al))
            st.body = np.maximum(st.body, pad.disc_field(x, y, 4.0, soft=2.0) * al * 0.6)
    mid_pts = [(s0 + 70 - 66 * s, cy - 0.5) for s in np.linspace(0, 1, 16)]
    mid = pad.poly(taper_poly(mid_pts, lambda s: 4.2 * (1 - s) ** 0.6 * min(1.0, s / 0.1 + 0.2)))
    st.over(mid, 0.95, np.maximum(mid * 0.8, rim_of(mid, 0.6)))
    nock = pad.poly([(s0 - 9, cy - 4.4), (s0 + 3, cy - 3.2), (s0 + 3, cy + 3.2), (s0 - 9, cy + 4.4), (s0 - 4, cy)])
    st.over(nock, 0.9, rim_of(nock, 0.8) * nock * 0.9)
    # ---- 살대 (위가 밝음) · 앞으로 흐르는 빛 덩이 (주기 96 점 · 24 점/장)
    shaft = pad.poly([(s0, cy - 3.0), (sock0 + 2, cy - 3.4), (sock0 + 2, cy + 3.4), (s0, cy + 3.0)])
    pul = np.zeros_like(X)
    for kk in range(-1, 5):
        dist = X - (60 + kk * 96 + 24.0 * f)
        pul = np.maximum(pul, np.where(dist > 0, np.exp(-(dist / 4.0) ** 2), np.exp(np.maximum(dist, -300.0) / 18.0)))
    pul = pul * smooth(s0 + 40, s0 + 80, X) * smooth(sock0, sock0 - 24, X)
    sc = np.maximum(gauss(Y - cy + 0.8, 0.8) * 0.75, shaft * clamp01((cy - Y + 3.0) / 3.0) * 0.45)
    st.over(shaft, 0.88, sc * shaft)
    st.add_core(pul * shaft * 0.9)
    st.body = np.maximum(st.body, pul * gauss(Y - cy, 4.0 + 2.0 * pul) * 0.7)
    # ---- 촉 받침 (고리 둘)
    sock = pad.poly([(sock0, cy - 4.2), (hb + 3, cy - 6.4), (hb + 3, cy + 6.4), (sock0, cy + 4.2)])
    lit_s, dark_s, _ = bevel_light(sock, 2.0)
    soc_c = rim_of(sock, 0.7) * 0.85 + lit_s * 0.6
    for xx in (sock0 + 4, sock0 + 10):
        soc_c = np.maximum(soc_c, pad.stroke([(xx, cy - 7), (xx, cy + 7)], 1.3, a0=0.95) * sock)
    st.over(sock, 0.95 - 0.12 * dark_s, soc_c * sock)
    # ---- 촉: 넓은 잎 날 (뒤로 뻗은 짧은 미늘 · 가운데 등줄 · 위 면 밝고 아래 면 어두움 · 갈아 세운 날)
    top = [(hb, cy - 5.6), (hb - 9, cy - 15.5), (hb + 3, cy - 19.5)] + bez((hb + 3, cy - 19.5), (hb + 19, cy - 19.6), (tip - 20, cy - 7.4), 24, p3=(tip, cy))[1:]
    bot = [(x, 2 * cy - y) for x, y in top]
    head = pad.poly(top + bot[::-1])
    upper = head * (Y < cy)
    lower = head * (Y >= cy)
    hl, hd, _ = bevel_light(head, 5.0)
    ramp = clamp01((X - hb) / (tip - hb))
    hcore = rim_of(head, 1.2) * 0.98
    hcore = np.maximum(hcore, upper * (0.66 + 0.3 * ramp))
    hcore = np.maximum(hcore, lower * (0.3 + 0.2 * ramp))
    hcore = np.maximum(hcore, pad.stroke([(hb + 2, cy), (tip - 3, cy)], 2.2, 0.7, a0=1.0, a1=0.95) * head)
    for sg in (-1, 1):
        bev = [(x, cy + sg * (abs(y - cy) - 3.8)) for x, y in top[3:-3]]
        hcore = np.maximum(hcore, pad.stroke(bev, 0.7, a0=0.85) * head)
    hcore = np.maximum(hcore, hl * 0.55)
    st.over(head, upper * 1.0 + lower * 0.85, hcore * head)
    # ---- 앞 바람 띠 (살대 · 촉 앞을 지나는 가닥)
    for k in (0, 1):
        bb, bc = band(k, True)
        st.body = np.maximum(st.body, bb)
        st.add_core(bc)
    # ---- 공기를 가르는 원뿔 (촉 끝에서 뒤로 벌어지는 가는 흰 줄 — 장마다 깜빡) · 촉 끝 반짝임 · 촉 둘레 큰 빛
    fl = [1.0, 0.6, 0.85, 0.5][f]
    for sg in (-1, 1):
        cone = bez((tip - 4, cy + sg * 2.5), (hb + 6, cy + sg * 22), (hb - 46, cy + sg * 36), 18)
        st.add_core(pad.stroke(cone, 1.3, 0.3, a0=0.55 * fl, a1=0.0))
        st.body = np.maximum(st.body, pad.stroke(cone, 5.0, 2.0, a0=0.45 * fl, a1=0.0, soft=1.6))
    st.add_core(star4(pad, tip - 5, cy, 10 + 7 * fl, 1.0, rot=0.1 * f, thin=0.1, diag=0.45))
    st.body = np.maximum(st.body, pad.radial(tip - 18, cy, 42.0, 1.6, sy=0.8) * 0.58)
    g1, c1 = finish(pad, st.body, st.core, halo=((1.5, 0.5), (4.0, 0.38), (8.0, 0.2)), body_k=0.92, core_bloom=((1.5, 0.35), (4.0, 0.26)), win=4.0)
    yy = np.arange(H, dtype=np.float32)[:, None] + 0.5
    vw = smooth(3.0, 9.0, np.minimum(yy, H - yy))
    return g1 * vw, c1 * vw


sprite("rfx_jgreatarrow", "lookat_direction", 320, 96, GA_N, 2, "sniper", zoom=0, loop=True,
       desc="거대한 셋째 화살 (오른쪽 = 나아가는 쪽 · 촉 끝 x 0.975 W): 넓은 잎 촉 · 감아 도는 바람 띠 두 가닥 (촉 쪽 좁고 뒤로 넓어짐 — 뒤로 흐름) · 흰 섬광 깃 · 공기를 가르는 원뿔 줄 (이어 돌림)")(greatarrow_frame)


# ------------------------------------------------------------ rfx_jblackarrow (검은 화살 — 가는 쪽으로 누움, 촉 = 오른쪽)

BA_N = 4


def blackarrow_frame(f, W=256, H=64):
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy, tip, hb, sock0, s0 = 32.0, 249.0, 224.0, 214.0, 44.0
    st = Stack(pad)
    # ---- 그림자 연기 (깃 뒤로 끌림 — 장마다 16 점 뒤로 흐름 · 주기 64 점)
    sig = 3.0 + 12.0 * clamp01((s0 + 50 - X) / (s0 + 46))
    env = smooth(0.0, 30.0, X) * smooth(s0 + 64, s0 + 22, X) * gauss(Y - cy, sig)
    n1 = fbm((X + 16.0 * f) / 16.0, Y / 10.0, seed=81, octaves=3, px=4)
    smoke = env * clamp01(0.45 + 1.0 * n1)
    st.body = np.maximum(st.body, smoke * 0.88)
    # 연기 가닥 셋 (깃 뒤에서 꼬리처럼 — 물결이 뒤로 흐름)
    for k, (yo, amp) in enumerate(((-3.5, 5.0), (0.5, 3.5), (3.5, 5.5))):
        ph = f * TAU / BA_N + k * 2.0
        pts = [(s0 + 8 - 52 * t, cy + yo * (1 + 2.6 * t) + amp * t * math.sin(t * 7.0 + ph)) for t in np.linspace(0, 1, 22)]
        st.body = np.maximum(st.body, pad.stroke(pts, 3.0, 9.0, a0=0.8, a1=0.0, soft=2.4))
    # 연기 속 은빛 티끌 (뒤로 흐르며 꺼짐)
    rm = random.Random(8311)
    for i in range(7):
        b = rm.random()
        q = (b + f / BA_N) % 1.0
        x = s0 + 6 - 44 * q
        y = cy + rm.uniform(-1, 1) * (4 + 10 * q)
        st.add_core(pad.disc_field(x, y, 0.9 + 0.5 * (1 - q), soft=0.5) * math.sin(math.pi * q) * 0.85)
    # ---- 그림자 줄 (화살 위아래로 뒤로 흐르는 검은 줄)
    for i in range(6):
        b = rm.random()
        q = (b + f / BA_N) % 1.0
        x1 = rm.uniform(120, 200) - 100 * q
        L = rm.uniform(24, 50)
        y = cy + rm.choice((-1, 1)) * rm.uniform(5, 13)
        al = math.sin(math.pi * q) * 0.55
        st.body = np.maximum(st.body, pad.stroke([(x1 - L, y), (x1, y)], 0.5, 1.8, a0=0.0, a1=al, soft=0.6))
    # ---- 까마귀 깃 (위 · 아래 — 뒤 끝이 들쭉날쭉 · 깃가지 결 은빛 테) + 가운데 좁은 깃
    rf = random.Random(8321)
    xs_ = [s0 + 52, s0 + 43, s0 + 34, s0 + 25, s0 + 16, s0 + 7, s0 - 2]

    def vane_h(x):
        u = min(1.0, max(0.0, (s0 + 54 - x) / 52.0))
        return 3.5 + 14.5 * math.sin(u * math.pi / 2) ** 1.25

    for sg in (-1, 1):
        # 까마귀 깃: 깃가지 뭉치 여섯 (뒤 · 바깥으로 비스듬히 — 사이가 갈라져 들쭉날쭉)
        vm = pad.zeros()
        for i in range(len(xs_) - 1):
            xb, xa = xs_[i] - 0.6, xs_[i + 1] + 0.6
            ha, hb_ = vane_h(xa) + rf.uniform(-1.2, 1.2), vane_h(xb) + rf.uniform(-1.0, 0.8)
            sw = rf.uniform(6.0, 8.5)
            clump = [(xb, cy + sg * 1.8), (xa, cy + sg * 1.8), (xa - sw * 0.9, cy + sg * (ha - 1.0)), (xa - sw + 1.5, cy + sg * (ha + 1.6)),
                     (xb - sw * 0.85, cy + sg * (hb_ - 0.6))]
            vm = np.maximum(vm, pad.poly(clump))
        vcore = rim_of(vm, 0.55) * 0.4
        vcore = np.maximum(vcore, pad.stroke([(s0 - 2, cy + sg * 1.6), (s0 + 52, cy + sg * 1.6)], 0.7, a0=0.3) * vm)
        st.over(vm, 0.97, vcore * vm)
    mid_pts = [(s0 + 48 - 50 * s, cy - 0.3) for s in np.linspace(0, 1, 14)]
    mid = pad.poly(taper_poly(mid_pts, lambda s: 3.4 * (1 - s) ** 0.5 * min(1.0, s / 0.12 + 0.25)))
    st.over(mid, 0.98, rim_of(mid, 0.5) * 0.35 * mid)
    nock = pad.poly([(s0 - 12, cy - 3.2), (s0 - 2, cy - 2.4), (s0 - 2, cy + 2.4), (s0 - 12, cy + 3.2), (s0 - 8, cy)])
    st.over(nock, 0.95, rim_of(nock, 0.6) * nock * 0.65)
    # ---- 살대 (검은 나무 — 위에 가는 은빛 결 · 세 군데 은빛 새김이 깜빡)
    shaft = pad.poly([(s0 - 3, cy - 2.0), (sock0 + 2, cy - 2.3), (sock0 + 2, cy + 2.3), (s0 - 3, cy + 2.0)])
    sc = gauss(Y - cy + 1.1, 0.45) * 0.42 * shaft
    for i, xx in enumerate((s0 + 70, s0 + 100, s0 + 130)):
        tw = 0.35 + 0.45 * (0.5 + 0.5 * math.cos(f * TAU / BA_N + i * 2.1))
        sc = np.maximum(sc, pad.stroke([(xx - 2.5, cy + 1.6), (xx, cy - 1.6), (xx + 2.5, cy + 1.6)], 0.8, a0=tw) * shaft)
    st.over(shaft, 0.98, sc)
    # ---- 촉 받침 · 미늘 달린 은빛 촉
    sock = pad.poly([(sock0, cy - 2.6), (hb + 2, cy - 3.8), (hb + 2, cy + 3.8), (sock0, cy + 2.6)])
    st.over(sock, 0.95, (rim_of(sock, 0.5) * 0.7 + pad.stroke([(sock0 + 4, cy - 4), (sock0 + 4, cy + 4)], 0.9, a0=0.8)) * sock)
    top = [(hb, cy - 3.0), (hb - 9, cy - 11.5), (hb + 2.5, cy - 8.4)] + bez((hb + 2.5, cy - 8.4), (hb + 15, cy - 7.6), (tip, cy), 18)[1:]
    bot = [(x, 2 * cy - y) for x, y in top]
    head = pad.poly(top + bot[::-1])
    upper = head * (Y < cy)
    lower = head * (Y >= cy)
    hl, hd, _ = bevel_light(head, 3.0)
    ramp = clamp01((X - hb) / (tip - hb))
    hcore = rim_of(head, 0.8) * 1.0
    hcore = np.maximum(hcore, upper * (0.72 + 0.25 * ramp))
    hcore = np.maximum(hcore, lower * (0.42 + 0.2 * ramp))
    hcore = np.maximum(hcore, pad.stroke([(hb + 1, cy), (tip - 2, cy)], 1.2, 0.5, a0=1.0) * head)
    hcore = np.maximum(hcore, hl * 0.5)
    st.over(head, 0.9, hcore * head)
    # ---- 촉 끝 은빛 반짝임 (장마다 크기) · 검은 몸 둘레 옅은 그림자 무리
    tw = [1.0, 0.5, 0.8, 0.4][f]
    st.add_core(star4(pad, tip - 3, cy, 6 + 5 * tw, 0.95, rot=0.12 * f, thin=0.12, diag=0.4))
    sil = clamp01(st.body * 1.2)
    st.body = np.maximum(st.body, gblur(sil, 2.5 * SS) * 0.55)
    g1, c1 = finish(pad, st.body, st.core, halo=((1.2, 0.4), (3.0, 0.28)), body_k=0.97, core_bloom=((1.0, 0.18),), win=3.5)
    return g1, c1


sprite("rfx_jblackarrow", "lookat_direction", 256, 64, BA_N, 2, "shadowhunter", zoom=0, loop=True,
       desc="검은 화살 (오른쪽 = 나아가는 쪽 · 촉 끝 x 0.975 W): 미늘 달린 은빛 촉 · 검은 살대 (은빛 결 · 새김 셋이 깜빡) · 들쭉날쭉한 까마귀 깃 · 뒤로 끌리는 그림자 연기 · 은빛 티끌 (이어 돌림)")(blackarrow_frame)


# ------------------------------------------------------------ rfx_jcurse (저주 문양 — 카메라를 봄, 칸 가운데 = 문양 가운데)

CU_N = 6
CU_T = 9          # 가시 수
CU_R = 37.0       # 고리 반지름


def _vine_pts(c, R, rot, k, frac=1.0, n=120):
    """꼬인 덩굴 한 가닥 (k = 0 · 1 — 두 가닥이 서로 엇갈려 꼬임)"""
    pts = []
    m = max(2, int(n * frac))
    for i in range(m + 1):
        th = rot + TAU * i / n
        r = R + 2.6 * math.sin(th * CU_T + k * math.pi - rot * CU_T)
        pts.append((c + math.cos(th) * r, c + math.sin(th) * r))
    return pts


def _thorns(c, R, rot, push=0.0, rs=None):
    """가시 다각형: 바깥 가시 (조금 시계 방향으로 휨) CU_T 개 · 안쪽 가시 CU_T 개 — push 만큼 바깥으로 튐"""
    out, tips = [], []
    for i in range(CU_T):
        th = rot + i * TAU / CU_T
        p = push * (rs.uniform(0.7, 1.2) if rs else 1.0)
        for side, ln, wd, dth in ((1, 12.0, 0.11, 0.1), (-1, 7.5, 0.085, TAU / CU_T / 2)):
            a = th + dth
            rb = R + side * 1.5 + p * (1 if side > 0 else 0.6)
            b0 = (c + math.cos(a - wd) * rb, c + math.sin(a - wd) * rb)
            b1 = (c + math.cos(a + wd) * rb, c + math.sin(a + wd) * rb)
            tr = rb + side * ln
            ta = a + 0.12 * side
            tp = (c + math.cos(ta) * tr, c + math.sin(ta) * tr)
            out.append([b0, tp, b1])
            tips.append((tp, side))
    return out, tips


def _eye(pad, c, open_=0.0, a=1.0):
    """가운데 눈: 감김 (아래로 휜 선 · 아래로 난 속눈썹 · 옅은 윗눈꺼풀) — open_ = 1 이면 뜬 눈 (아몬드 · 홍채 · 세로 동공)"""
    core = pad.zeros()
    body = pad.zeros()
    if open_ <= 0:
        lid = bez((c - 17, c - 1), (c, c + 10), (c + 17, c - 1), 20)
        core = np.maximum(core, pad.stroke(lid, 3.4, a0=a, taper=0.4))
        up = bez((c - 17, c - 1), (c, c - 8), (c + 17, c - 1), 20)
        core = np.maximum(core, pad.stroke(up, 1.6, a0=a * 0.4, taper=0.6))
        for i in range(5):
            t = 0.18 + 0.64 * i / 4
            x = (1 - t) ** 2 * (c - 17) + 2 * (1 - t) * t * c + t * t * (c + 17)
            y = (1 - t) ** 2 * (c - 1) + 2 * (1 - t) * t * (c + 10) + t * t * (c - 1)
            ang = math.pi / 2 + (t - 0.5) * 1.2
            core = np.maximum(core, pad.stroke([(x, y + 1), (x + math.cos(ang) * 5.5, y + 1 + math.sin(ang) * 6.5)], 2.0, 0.6, a0=a * 0.9))
        body = np.maximum(body, pad.stroke(lid, 10.0, a0=a * 0.55, soft=2.4))
    else:
        up = bez((c - 18, c), (c, c - 13), (c + 18, c), 20)
        lo = bez((c - 18, c), (c, c + 12), (c + 18, c), 20)
        almond = pad.poly(up + lo[::-1])
        core = np.maximum(core, pad.stroke(up, 1.8, a0=a, taper=0.4))
        core = np.maximum(core, pad.stroke(lo, 1.5, a0=a * 0.85, taper=0.4))
        iris = pad.disc_field(c, c, 6.5, soft=0.6) * almond
        pupil = pad.poly(taper_poly([(c, c - 6), (c, c + 6)], lambda t: 1.6 * math.sin(math.pi * t) ** 0.7))
        core = np.maximum(core, clamp01(iris - pupil) * a)
        body = np.maximum(body, almond * 0.9)
    return body, core


def curse_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(8400 + f)
    body = pad.zeros()
    core = pad.zeros()
    step = TAU / CU_T
    if f == 0:
        # 검은 연기가 소용돌이치며 모여 고리가 그어짐 (반쯤) · 눈은 옅게
        for i in range(8):
            a = i * TAU / 8
            pts = []
            for s_ in range(10):
                t = s_ / 9
                rr = 58.0 + (CU_R - 58.0) * t
                aa = a + 0.9 * t
                pts.append((c + math.cos(aa) * rr, c + math.sin(aa) * rr))
            body = np.maximum(body, pad.stroke(pts, 10.0, 4.0, a0=0.25, a1=0.85, soft=3.0))
            core = np.maximum(core, pad.stroke(pts[5:], 0.6, 1.2, a0=0.0, a1=0.5))
        for k in (0, 1):
            v = _vine_pts(c, CU_R, -0.6, k, frac=0.55)
            body = np.maximum(body, pad.stroke(v, 9.0, soft=1.8))
            core = np.maximum(core, pad.stroke(v, 2.2, a0=0.75))
        eb, ec = _eye(pad, c, 0.0, 0.4)
        body = np.maximum(body, pad.radial(c, c, 26.0, 0.8) * 0.75)
        body = np.maximum(body, eb)
        core = np.maximum(core, ec)
    elif f <= 3:
        # 맥동 (이어 돌림): 가시가 1/3 칸씩 돎 (세 장 = 가시 한 칸) · 눈이 숨 쉬듯 밝아졌다 어두워짐
        rot = (f - 1) * step / 3
        k = [1.0, 0.78, 0.9][f - 1]
        for kk in (0, 1):
            v = _vine_pts(c, CU_R, rot, kk)
            body = np.maximum(body, pad.stroke(v, 9.5, soft=1.8))
            core = np.maximum(core, pad.stroke(v, 2.4, a0=0.8 * k + 0.15))
        polys, tips = _thorns(c, CU_R, rot)
        th_m = pad.polys(polys)
        body = np.maximum(body, th_m)
        core = np.maximum(core, rim_of(th_m, 1.4) * 0.85 * k)
        for (tx, ty), side in tips:
            if side > 0:
                core = np.maximum(core, pad.disc_field(tx, ty, 1.3, soft=0.6) * k)
        body = np.maximum(body, pad.radial(c, c, 27.0, 0.7) * 0.85)
        # 고리 안의 도는 연기 (돎 방향 — 이어 돌림)
        for i in range(3):
            a0 = rot * 3 + i * TAU / 3
            pts = arc_pts(c, c, 22.0, a0, a0 + 1.6, n=14)
            body = np.maximum(body, pad.stroke(pts, 6.0, 1.0, a0=0.5, a1=0.0, soft=2.0))
            core = np.maximum(core, pad.stroke(pts[:9], 0.8, 0.2, a0=0.35 * k, a1=0.0))
        eb, ec = _eye(pad, c, 0.0, 0.62 + 0.38 * k)
        body = np.maximum(body, eb)
        core = np.maximum(core, ec)
        body = np.maximum(body, pad.radial(c, c, 56.0, 2.2) * 0.35 * k)
    elif f == 4:
        # 터짐: 눈이 번쩍 뜨이고 가시가 바깥으로 튐 · 고리가 토막으로 갈라짐
        rot = step / 3 * 3
        for kk in (0, 1):
            for i in range(CU_T):
                a0 = rot + i * step + 0.12
                pts = arc_pts(c, c, CU_R + 4, a0, a0 + step - 0.3, n=8)
                body = np.maximum(body, pad.stroke(pts, 9.0, soft=1.8) * 0.85)
                core = np.maximum(core, pad.stroke(pts, 2.2, a0=0.9))
        polys, tips = _thorns(c, CU_R, rot, push=9.0, rs=rs)
        th_m = pad.polys(polys)
        body = np.maximum(body, th_m)
        core = np.maximum(core, rim_of(th_m, 0.9) * 0.95)
        for (tx, ty), side in tips:
            if side > 0:
                ux, uy = (tx - c), (ty - c)
                L = math.hypot(ux, uy) or 1
                core = np.maximum(core, pad.stroke([(tx - ux / L * 14, ty - uy / L * 14), (tx, ty)], 0.3, 1.6, a0=0.0, a1=0.9))
        eb, ec = _eye(pad, c, 1.0, 1.0)
        body = np.maximum(body, pad.radial(c, c, 30.0, 0.8))
        body = np.maximum(body, eb)
        core = np.maximum(core, ec)
        core = np.maximum(core, star4(pad, c, c, 30.0, 0.9, thin=0.08, diag=0.4))
    else:
        # 흐려짐: 가시 조각이 멀리 · 연기가 퍼지며 옅어짐 · 감긴 눈 자국
        for i in range(CU_T * 2):
            a = i * TAU / (CU_T * 2) + rs.uniform(-0.15, 0.15)
            r = rs.uniform(46, 56)
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            sh = [(x + math.cos(a) * 4, y + math.sin(a) * 4), (x - math.sin(a) * 1.6, y + math.cos(a) * 1.6), (x + math.sin(a) * 1.6, y - math.cos(a) * 1.6)]
            body = np.maximum(body, pad.poly(sh, 0.6))
            core = np.maximum(core, pad.poly(sh, 0.4))
        for i in range(6):
            a = i * TAU / 6 + 0.3
            pts = arc_pts(c, c, 34.0 + 6 * (i % 2), a, a + 0.9, n=10)
            body = np.maximum(body, pad.stroke(pts, 12.0, 4.0, a0=0.4, a1=0.0, soft=4.0))
        eb, ec = _eye(pad, c, 0.0, 0.3)
        body = np.maximum(body, eb * 0.6)
        core = np.maximum(core, ec)
        body = np.maximum(body, pad.radial(c, c, 34.0, 1.2) * 0.3)
    sil = clamp01(body * 1.3)
    body = np.maximum(body, gblur(sil, 2.5 * SS) * 0.5)
    g, cc = finish(pad, body, core, halo=((1.2, 0.4), (3.0, 0.28)), body_k=0.97, core_bloom=((1.0, 0.2),), win=4.0)
    return g, cc


sprite("rfx_jcurse", "rotate_xyz", 128, 128, CU_N, 6, "shadowhunter", zoom=1,
       desc="저주 문양: 0 검은 연기가 소용돌이치며 모여 가시 고리 (반쯤) · 1 ~ 3 맥동 (꼬인 덩굴 고리 · 바깥 / 안쪽 가시 아홉씩이 1/3 칸씩 돎 · 가운데 감긴 눈 — 이어 돌림) · "
       "4 터짐 (눈이 번쩍 뜨임 · 가시가 바깥으로 튐) · 5 흐려짐")(curse_frame)


# ------------------------------------------------------------ rfx_jthicket (어둠숲 가시덤불 — 세워 카메라 쪽, 아래 끝 = 땅)

TK_N = 8
TK_BASE = 248.0
# 줄기: (밑동 x, 높이, 기울기, 밑동 폭, 휨)
TK_STEMS = [(128, 204, 8, 14, 14), (94, 152, -34, 11, -12), (164, 164, 30, 11, 12), (62, 106, -38, 9, -10), (196, 114, 38, 9, 10), (110, 96, -6, 8, 9),
            (148, 102, 12, 8, -9)]


def _stem_pts(bx, h, lean, curl, sway=0.0, n=26):
    """덩굴 줄기 (S 꼴 휨 · 끝은 기운 쪽으로 갈고리처럼 휨) — sway = 위로 갈수록 커지는 옆 흔들림"""
    base = TK_BASE
    p0 = (bx, base + 4)
    p1 = (bx + curl, base - h * 0.34)
    p2 = (bx + lean * 0.75 - curl * 0.8, base - h * 0.72)
    p3 = (bx + lean, base - h)
    pts = bez(p0, p1, p2, n, p3=p3)
    sgn = 1 if lean >= 0 else -1
    ex, ey = p3
    hook = [(ex + sgn * 4.0, ey - 3.0), (ex + sgn * 8.5, ey - 1.5), (ex + sgn * 10.5, ey + 3.0)]
    pts = pts + hook
    out = []
    L = len(pts)
    for i, (x, y) in enumerate(pts):
        t = i / (L - 1)
        out.append((x + sway * t ** 1.6, y))
    return out


def _thorny(pts, w, rs, every=9.0, scale=1.0):
    """줄기 다각형 (밑이 굵고 끝이 가늚) · 가시 세모들 · 가시 끝 자리"""
    stem = taper_poly(pts, lambda t: w / 2 * (1 - t) ** 0.8 + 0.7)
    thorns, tips = [], []
    acc = rs.uniform(0, every)
    side = 1
    for idx in range(1, len(pts)):
        (ax, ay), (bx, by) = pts[idx - 1], pts[idx]
        L = math.hypot(bx - ax, by - ay)
        acc += L
        if acc < every:
            continue
        acc = 0.0
        tx, ty = (bx - ax) / (L or 1), (by - ay) / (L or 1)
        nx, ny = -ty * side, tx * side
        t = idx / (len(pts) - 1)
        if t > 0.94:
            continue
        r_half = (w / 2 * (1 - t) ** 0.8 + 0.7)
        bw = 1.7 + r_half * 0.42
        ln = (5.5 + r_half * 0.85) * scale
        cx_, cy_ = bx + nx * r_half * 0.8, by + ny * r_half * 0.8
        tip = (cx_ + nx * ln + tx * ln * 0.55, cy_ + ny * ln + ty * ln * 0.55)
        thorns.append([(cx_ - tx * bw, cy_ - ty * bw), tip, (cx_ + tx * bw, cy_ + ty * bw)])
        tips.append(tip)
        side = -side
    return stem, thorns, tips


def _thicket_geom(frac, sway_ph, rs_seed=8500):
    """덤불 전체: 줄기 · 곁가지 다각형 · 가시 · 가시 끝 — frac = 땅 위로 나온 몫 (작으면 아래로 밀어 끝부터 보임)"""
    rs = random.Random(rs_seed)
    polys, thorns, tips, spines = [], [], [], []
    for i, (bx, h, lean, w, curl) in enumerate(TK_STEMS):
        fr = min(1.0, frac * (1.12 if i == 0 else 1.0 - 0.04 * i))
        sway = 0.0 if sway_ph is None else 5.5 * math.sin(sway_ph + i * 1.3) * (h / 200.0)
        pts = _stem_pts(bx, h, lean, curl, sway)
        dy = h * (1 - fr)
        pts = [(x, y + dy) for x, y in pts]
        stem, th, tp = _thorny(pts, w, rs)
        polys.append(stem)
        thorns += th
        tips += tp
        spines.append((pts, w))
        # 곁가지 둘 (줄기 0.35 ~ 0.75 에서 바깥으로 휨)
        for k in range(2):
            t = rs.uniform(0.32, 0.72)
            j = int(t * (len(pts) - 1))
            x, y = pts[j]
            sgn = (1 if (k + i) % 2 else -1)
            ln = h * rs.uniform(0.22, 0.32)
            q1 = (x + sgn * ln * 0.45, y - ln * 0.35)
            q2 = (x + sgn * ln * 0.95, y - ln * 0.55 + rs.uniform(-6, 6))
            bpts = bez((x, y), q1, q2, 12)
            bpts = bpts + [(q2[0] + sgn * 3.0, q2[1] + 2.5)]
            bw = w * 0.5 * (1 - t * 0.5)
            st_, th_, tp_ = _thorny(bpts, bw, rs, every=7.0, scale=0.8)
            polys.append(st_)
            thorns += th_
            tips += tp_
            spines.append((bpts, bw))
    # 줄기 사이를 꼬며 건너는 가는 덩굴 셋 (서로 얽힌 덤불)
    for (ia, ta), (ib, tb), bend in (((1, 0.45), (0, 0.62), -14), ((2, 0.4), (0, 0.5), 12), ((3, 0.6), (5, 0.7), -8)):
        pa = spines[ia * 3][0]
        pb = spines[ib * 3][0]
        a = pa[int(ta * (len(pa) - 1))]
        b = pb[int(tb * (len(pb) - 1))]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + bend)
        vpts = bez(a, mid, b, 14)
        st_, th_, tp_ = _thorny(vpts, 4.0, rs, every=8.0, scale=0.7)
        st_ = taper_poly(vpts, lambda t: 1.6 + 0.6 * math.sin(math.pi * t))
        polys.append(st_)
        thorns += th_
        tips += tp_
    return polys, thorns, tips, spines


def thicket_frame(f, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    rs = random.Random(8510 + f)
    base = TK_BASE
    ground_clip = (Y < base + 0.5).astype(np.float32)
    if f <= 5:
        frac = [0.3, 0.68, 1.0, 1.0, 1.0, 1.0][f]
        sway = None if f <= 2 else (f - 3) * TAU / 3
        polys, thorns, tips, spines = _thicket_geom(frac, sway)
        m = pad.polys(polys)
        th = pad.polys(thorns)
        wood = clamp01(np.maximum(m, th)) * ground_clip
        lit, dark, hh = bevel_light(m * ground_clip, 3.0, light=(-0.7, -0.7))
        body = wood * 0.97
        # 은빛: 달빛 테 (빛 쪽 비탈) · 가시 끝 · 덤불 속 눈 (둘씩 — 4 장에서 깜빡 감음)
        core = lit * 0.42 + rim_of(th, 0.5) * th * 0.3 * ground_clip
        rg = random.Random(8590)
        for (tx, ty) in tips:
            if ty < base - 2 and rg.random() < 0.22:
                core = np.maximum(core, pad.disc_field(tx, ty, 1.0, soft=0.5) * 0.8)
        if f >= 2:
            # 덤불 속 눈 셋 (둘씩 — 안쪽이 처진 성난 눈 · 4 에서 깜빡 감음)
            op = 0.35 if f == 4 else 2.4
            for ex, ey in ((112, 170), (152, 148), (84, 198)):
                body = np.maximum(body, pad.radial(ex, ey, 11.0, 1.2) * 0.9)
                for sgn in (-1, 1):
                    x0 = ex + sgn * 3.6
                    p0 = (x0 - 2.7, ey - 1.0 if sgn < 0 else ey + 0.7)
                    p1 = (x0 + 2.7, ey + 0.7 if sgn < 0 else ey - 1.0)
                    core = np.maximum(core, lens(pad, p0, p1, op, 1.0, power=0.7))
        # 땅: 뿌리 둔덕 (짙음) · 자라는 동안 튀는 흙
        body = np.maximum(body, pad.radial(S / 2, base + 2, 118.0, 1.2, sy=0.09) * 0.95)
        if f <= 2:
            for i in range(14):
                x = rs.uniform(40, S - 40)
                y = base - rs.uniform(6, 48) * (f + 1) / 3
                r = rs.uniform(1.6, 3.8)
                chip = [(x + rs.uniform(-r, r), y + rs.uniform(-r, r)) for _ in range(4)]
                body = np.maximum(body, pad.poly(chip, 0.9))
                core = np.maximum(core, pad.poly(chip, 0.15))
        # 덤불 사이를 떠도는 은빛 티끌 (서 있는 동안 — 이어 돌림)
        if f >= 3:
            for i in range(6):
                q = (i / 6 + (f - 3) / 3) % 1.0
                x = 60 + i * 26 + 6 * math.sin(q * TAU + i)
                y = base - 30 - 150 * q
                core = np.maximum(core, pad.disc_field(x, y, 1.2, soft=0.6) * math.sin(math.pi * q) * 0.8)
        body = np.maximum(body, gblur(wood, 2.0 * SS) * 0.3)
    else:
        polys, thorns, tips, spines = _thicket_geom(1.0, 0.0)
        m = pad.polys(polys)
        th = pad.polys(thorns)
        wood = clamp01(np.maximum(m, th)) * ground_clip
        # 금 (은빛으로 갈라짐) — 6 은 금 · 부서지기 시작, 7 은 조각 · 연기
        n1 = fbm(X / 9.0, Y / 9.0, seed=85, octaves=3)
        crack = clamp01(1.0 - np.abs(n1) / 0.06)
        # 연기: 덤불 자리를 넓게 흐린 것 (7 은 위로 떠오름) × 노이즈
        lift = 0 if f == 6 else 22 * SS
        wl = np.zeros_like(wood)
        wl[: wood.shape[0] - lift] = wood[lift:]
        smoke_env = clamp01(gblur(wl, 7.0 * SS) * 2.2)
        n2 = fbm(X / 18.0, (Y + 26.0 * (f - 5)) / 15.0, seed=86, octaves=3)
        smoke = smoke_env * clamp01(0.4 + 1.0 * n2)
        if f == 6:
            body = np.maximum(wood * clamp01(1 - crack * 0.9) * 0.95, smoke * 0.6)
            core = crack * wood * 0.85
            for (tx, ty) in tips[::3]:
                core = np.maximum(core, pad.disc_field(tx, ty + 4, 0.8, soft=0.5) * 0.5)
        else:
            body = smoke * 0.55
            core = pad.zeros()
            for pts, w in spines:
                for k in range(4):
                    t = rs.uniform(0.05, 0.95)
                    x, y = pts[int(t * (len(pts) - 1))]
                    dx = (x - S / 2) * 0.18 + rs.uniform(-10, 10)
                    dy = rs.uniform(-6, 14)
                    r = w * (1 - t) * 0.5 + 1.6
                    chip = [(x + dx + rs.uniform(-r, r), y + dy + rs.uniform(-r, r)) for _ in range(4)]
                    body = np.maximum(body, pad.poly(chip, 0.8))
                    core = np.maximum(core, pad.poly(chip, 0.12))
            for i in range(8):
                x = rs.uniform(50, S - 50)
                y = rs.uniform(base - 170, base - 20)
                core = np.maximum(core, pad.disc_field(x, y, 1.0, soft=0.6) * 0.6)
        body = np.maximum(body, pad.radial(S / 2, base + 2, 110.0, 1.2, sy=0.09) * (0.7 if f == 6 else 0.35))
    g, cc = finish(pad, body, core, halo=((1.2, 0.3),), body_k=0.98, core_bloom=((1.0, 0.12),), win=3.0)
    # 옅은 안개는 지움 (가까이 선 카메라에서 넓은 반투명 판으로 보였음 — 영상 1)
    g = clamp01((g - 0.05) / 0.95)
    return g, cc


sprite("rfx_jthicket", "lookat_y", 256, 256, TK_N, 4, "shadowhunter", zoom=4,
       desc="어둠숲 가시덤불 (아래 끝 = 땅): 0 ~ 2 S 꼴로 휜 가시 줄기 일곱 (곁가지 · 갈고리 끝 · 가시) 이 땅을 뚫고 자람 (흙 튐) → 3 ~ 5 서 있음 (가지가 일렁임 · 은빛 티끌 · "
       "덤불 속 눈 셋 — 4 에서 깜빡 — 이어 돌림) → 6 은빛 금이 가며 부서짐 · 7 조각 · 연기로 흩어짐")(thicket_frame)


# ============================================================ 0.51.0 암살자 2차 (트릭스터 · 블레이드 댄서)

# 확인용 색 (게임이 입히는 색과 같게 — JobFx.outer · inner): 트릭스터 = 검보라 겉 · 연분홍 흰빛 속 / 거울 조각 = 청록 테 (TricksterSkills.ILLUSION) · 연분홍 면 /
# 다시 뜨는 해 = 흰 금빛 · 흰빛 (TricksterSkills.DAWN) / 블레이드 댄서 = 연분홍 겉 · 흰 금빛 속 (JobFx.BD_OUTER · BD_INNER)
COLORS.setdefault("trickster", ((0.32, 0.07, 0.46), (0.98, 0.68, 0.91)))
COLORS.setdefault("illusion", ((0.3, 0.95, 0.9), (0.98, 0.68, 0.91)))
COLORS.setdefault("dawn", ((1.0, 0.86, 0.55), (1.0, 0.98, 0.9)))
COLORS.setdefault("bladedancer", ((1.0, 0.52, 0.74), (1.0, 0.95, 0.8)))


# ------------------------------------------------------------ rfx_jcoil (요르문간드의 똬리 — 땅에 누움, 그림 위 = 길이 뻗는 쪽, 칸 가운데 = 똬리 가운데)

CO_N = 8
# 장마다: 그린 몫 (머리까지) · 옆 폭 배율 (조여 듦) · 몸 밝기 · 비늘 흐름 · 섬광 · 흩어짐
CO_DRAW = [0.42, 0.82, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]
CO_AMP = [1.0, 1.0, 1.0, 0.93, 0.87, 0.82, 0.84, 0.86]
CO_A = [0.95, 1.0, 1.0, 1.0, 1.0, 1.0, 0.6, 0.28]
CO_PH = [0.0, 0.0, 0.0, 0.33, 0.66, 1.0, 1.0, 1.0]
CO_FLASH = [0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.25, 0.0]
CO_MIST = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.55, 0.9]


def _coil_pts(W, H, amp, frac=1.0, n=260):
    """세로로 선 8 자 (Gerono 의 렘니스케이트 — 아래 끝에서 왼쪽 아래 고리 → 가운데 엇갈림 → 오른쪽 위 고리 → 위 끝 → 왼쪽 위 → 가운데 → 오른쪽 아래 → 아래 끝):
    S 자로 갔다가 거꾸로 된 S 로 되돌아온 길 (TricksterSkills.coil) 을 둥글게 이은 것. 위 아래 끝 = H 의 0.08 · 0.92, 옆 = ±amp"""
    c = W / 2
    B = H * 0.42
    out = []
    m = max(2, int(n * frac))
    for i in range(m + 1):
        th = -math.pi / 2 + TAU * frac * i / m
        x = c + 2 * amp * math.sin(th) * math.cos(th)
        y = H / 2 - B * math.sin(th)
        out.append((x, y))
    return out


def coil_frame(f, W=256, H=256):
    pad = Pad(W, H)
    rs = random.Random(9100 + f)
    amp = W * 0.235 * CO_AMP[f]
    pts = _coil_pts(W, H, amp, CO_DRAW[f])
    A = CO_A[f]
    d, s = pad.line_field(pts, 22)
    n = len(pts)
    L = sum(math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(n - 1))
    full = CO_DRAW[f] >= 1.0
    # 몸 굵기: 꼬리 (처음) 에서 굵어졌다 머리 쪽 조금 가늘게 (닫힌 똬리면 고르게)
    sl = s * L
    if full:
        wid = np.full_like(s, 8.6)
    else:
        wid = 3.0 + 6.0 * clamp01(sl / 60.0) - 1.2 * clamp01((sl - (L - 30)) / 30.0)
    body = clamp01((wid - d) * SS * 0.25 + 0.5) * (d < 30)
    # 비늘: 몸을 따라 흐르는 갈매기 (|d| 로 휘게) — 빛나는 마루
    k = TAU / 9.0
    chev = 0.5 + 0.5 * np.sin(sl * k - np.abs(d) * 0.55 - CO_PH[f] * TAU)
    scales = clamp01((chev - 0.55) / 0.3) * clamp01((wid * 0.78 - d) * SS * 0.3 + 0.5)
    ridge = clamp01((wid * 0.32 - d) * SS * 0.3 + 0.5) * 0.55
    core = np.maximum(scales * 0.85, ridge) * A
    # 가장자리 테 (몸의 바깥 줄 — 밝은 테)
    edge = clamp01(1 - np.abs(d - wid * 0.92) * SS * 0.18) * (d < wid + 2)
    core = np.maximum(core, edge * 0.7 * A)
    glow = body * (0.78 * A)
    # 머리 (그린 끝 — 닫히면 꼬리를 무는 아래 끝): 쐐기 머리 · 눈 · 두 갈래 혀
    hx, hy = pts[-1]
    px, py = pts[-6]
    ang = math.atan2(hy - py, hx - px)
    ux, uy = math.cos(ang), math.sin(ang)
    tx, ty = -uy, ux
    head = [(hx + ux * 16, hy + uy * 16), (hx + tx * 9 + ux * 2, hy + ty * 9 + uy * 2), (hx - ux * 8 + tx * 6, hy - uy * 8 + ty * 6),
            (hx - ux * 8 - tx * 6, hy - uy * 8 - ty * 6), (hx - tx * 9 + ux * 2, hy - ty * 9 + uy * 2)]
    hm = pad.poly(head, 1.0)
    glow = np.maximum(glow, hm * A)
    core = np.maximum(core, rim_of(hm, 1.6) * 0.9 * A)
    for sg in (-1, 1):
        ex, ey = hx + ux * 5 + tx * sg * 4.2, hy + uy * 5 + ty * sg * 4.2
        core = np.maximum(core, pad.disc_field(ex, ey, 2.0, soft=0.6) * A)
    if not full or f >= 5:
        tg = [(hx + ux * 16, hy + uy * 16), (hx + ux * 24, hy + uy * 24)]
        core = np.maximum(core, pad.stroke(tg, 1.4, 1.0, a0=0.8 * A))
        for sg in (-1, 1):
            core = np.maximum(core, pad.stroke([tg[1], (hx + ux * 29 + tx * sg * 3.5, hy + uy * 29 + ty * sg * 3.5)], 1.2, 0.6, a0=0.8 * A))
    if not full:
        # 미끄러지는 머리 앞 옅은 길 (그려질 곳) · 꼬리 쪽 먼지
        ahead = _coil_pts(W, H, amp, 1.0)
        rest = ahead[len(pts) - 1:len(pts) + 40]
        if len(rest) > 2:
            glow = np.maximum(glow, pad.stroke(rest, 3.0, 0.5, a0=0.35, a1=0.0, soft=1.5))
    # 섬광 (조임): 몸 전체가 밝게 · 두 고리 가운데에서 터지는 빛 고리 · 튀는 불티
    fl = CO_FLASH[f]
    if fl > 0:
        core = np.maximum(core, body * 0.95 * fl)
        for cy in (H / 2 - H * 0.21, H / 2 + H * 0.21):
            ring = pad.stroke(arc_pts(W / 2, cy, 34 + 14 * (1 - fl), 0, TAU, n=48), 4.0 * fl + 0.8, a0=fl)
            core = np.maximum(core, ring)
            glow = np.maximum(glow, pad.stroke(arc_pts(W / 2, cy, 34 + 14 * (1 - fl), 0, TAU, n=48), 12.0, a0=0.6 * fl, soft=4.0))
            for i in range(9):
                a = rs.uniform(0, TAU)
                r0, r1 = rs.uniform(14, 24), rs.uniform(36, 56)
                core = np.maximum(core, streak(pad, (W / 2 + math.cos(a) * r0, cy + math.sin(a) * r0), (W / 2 + math.cos(a) * r1, cy + math.sin(a) * r1), 2.4, fl))
        core = np.maximum(core, star4(pad, W / 2, H / 2, 34 * fl, fl, rot=0.0, thin=0.1, diag=0.4))
    # 흩어짐: 몸에 구멍이 나며 독 안개로
    mist = CO_MIST[f]
    if mist > 0:
        X, Y = pad.grid()
        nz = fbm(X / 22.0, Y / 22.0, seed=91 + f, octaves=3)
        holes = clamp01((nz + 0.15 - mist * 0.55) * 4.0)
        core = core * holes
        glow = np.maximum(glow * holes, gblur(body, 6.0 * SS) * 0.5 * mist * A)
    g, cc = finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.38), (8.0, 0.24)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.25)), win=4.0)
    return g, cc


sprite("rfx_jcoil", "direction_z", 256, 256, CO_N, 4, "trickster", zoom=0,
       desc="요르문간드의 똬리 (땅에 누움 — 그림 위 = 길이 뻗는 쪽): 0 ~ 1 뱀이 미끄러져 세로 8 자 (S 로 갔다 거꾸로 된 S 로 되돌아온 길) 를 그림 · 2 ~ 4 비늘 마루가 흐르며 "
       "조여 듦 → 5 조이는 섬광 (두 고리 가운데의 빛 고리 · 불티) → 6 ~ 7 몸에 구멍이 나며 독 안개로 흐려짐")(coil_frame)


# ------------------------------------------------------------ rfx_jmirror (환영이 깨지는 거울 조각 — 카메라를 봄, 칸 가운데 = 깨진 자리)

MI_N = 6
MI_P = [0.0, 0.2, 0.46, 0.7, 0.88, 1.0]
MI_A = [1.0, 1.0, 0.95, 0.8, 0.55, 0.28]
_R = random.Random(9200)
MI_SHARDS = []
for _i in range(18):
    _a = _i * TAU / 18 + _R.uniform(-0.2, 0.2)
    _nv = _R.choice((3, 3, 4))
    _sz = _R.uniform(7, 16)
    _poly = [(_R.uniform(0.6, 1.0) * _sz * math.cos(k * TAU / _nv + _R.uniform(-0.3, 0.3)), _R.uniform(0.6, 1.0) * _sz * math.sin(k * TAU / _nv + _R.uniform(-0.3, 0.3)))
             for k in range(_nv)]
    MI_SHARDS.append((_a, _R.uniform(0.45, 1.0), _poly, _R.uniform(0, TAU), _R.uniform(-2.5, 2.5), _R.random() < 0.45))


def mirror_frame(f, S=192):
    pad = Pad(S, S)
    c = S / 2
    p, A = MI_P[f], MI_A[f]
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(9210 + f)
    if f == 0:
        # 금 간 거울 (사람 키 렌즈 꼴 빛) · 가운데에서 뻗는 금 · 번쩍
        pane = lens(pad, (c, c - 62), (c, c + 62), 52, 0.55, power=0.5)
        glow = np.maximum(glow, pane)
        core = np.maximum(core, rim_of(pane > 0.2, 1.6) * 0.8)
        for i in range(9):
            a = i * TAU / 9 + rs.uniform(-0.2, 0.2)
            pts = [(c, c)]
            r = 0.0
            while r < 55:
                r += rs.uniform(7, 12)
                aa = a + rs.uniform(-0.3, 0.3)
                pts.append((c + math.cos(aa) * r * 0.55, c + math.sin(aa) * r))
            core = np.maximum(core, pad.stroke(pts, 2.0, 0.6, a0=1.0, a1=0.5))
        core = np.maximum(core, star4(pad, c, c, 30, 1.0, rot=0.2, thin=0.1, diag=0.5))
        glow = np.maximum(glow, pad.radial(c, c, 40, 1.4) * 0.8)
    else:
        # 조각: 사방으로 튀며 돌고 작아짐 — 면 (빛) · 테 (심지) · 몇 조각에 반짝임
        for (a, k, poly, r0, spin, sh) in MI_SHARDS:
            r = 8 + 74 * (p ** 0.75) * k
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            rot = r0 + spin * p
            sc = 1.0 - 0.45 * p
            cr, sr = math.cos(rot), math.sin(rot)
            pts = [(x + (px * cr - py * sr) * sc, y + (px * sr + py * cr) * sc) for px, py in poly]
            m = pad.poly(pts, 1.0)
            glow = np.maximum(glow, m * 0.6 * A)
            core = np.maximum(core, rim_of(m, 1.5) * 0.95 * A)
            if sh and f <= 4:
                core = np.maximum(core, star4(pad, x, y, 7 * A, A, rot=rot, thin=0.12, diag=0.0))
            # 날아가는 꼬리 (가는 빛 줄)
            if f <= 3:
                r_tail = max(4, r - 18)
                glow = np.maximum(glow, pad.stroke([(c + math.cos(a) * r_tail, c + math.sin(a) * r_tail), (x, y)], 0.5, 3.0, a0=0.0, a1=0.45 * A, soft=1.2))
        if f <= 2:
            fl = 1.0 if f == 1 else 0.45
            core = np.maximum(core, star4(pad, c, c, 26 * fl, fl, rot=0.6, thin=0.1, diag=0.4))
            glow = np.maximum(glow, pad.radial(c, c, 30 + 20 * p, 1.3) * 0.7 * fl)
            core = np.maximum(core, pad.stroke(arc_pts(c, c, 18 + 60 * p, 0, TAU, n=48), 2.6 * fl, a0=0.8 * fl))
        for _ in range(10 if f >= 3 else 4):
            a = rs.uniform(0, TAU)
            r = rs.uniform(20, 80) * (0.5 + p)
            core = np.maximum(core, star4(pad, c + math.cos(a) * min(r, 84), c + math.sin(a) * min(r, 84), rs.uniform(2, 4.5), A * rs.uniform(0.5, 1.0), diag=0.0))
    g, cc = finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.4), (8.0, 0.24)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.25)), win=4.0)
    return g, cc


sprite("rfx_jmirror", "rotate_xyz", 192, 192, MI_N, 6, "illusion", zoom=1,
       desc="환영이 깨지는 거울 조각: 0 금 간 렌즈 꼴 빛 (사람 키) · 가운데에서 뻗는 금 · 번쩍 → 1 ~ 3 조각 열여섯이 돌며 사방으로 튐 (면 = 빛 · 테 = 심지 · 몇 조각 반짝임 · "
       "가는 꼬리) → 4 ~ 5 작아지며 흐려짐 · 반짝이 티끌")(mirror_frame)


# ------------------------------------------------------------ rfx_jdawn (다시 뜨는 해 — 카메라를 봄, 칸 가운데 = 해 가운데)

DW_N = 8
DW_R = 62.0
# 장마다: 밝아진 몫 (원판 안 — 구슬 쪽부터) · 빛살 길이 · 퍼지는 고리 · 밝기
DW_LIT = [0.0, 0.0, 0.3, 0.62, 1.0, 1.0, 1.0, 1.0]
DW_RAY = [0.0, 40.0, 92.0, 118.0, 134.0, 128.0, 104.0, 70.0]
DW_RING = [0.0, 0.0, 0.0, 0.0, 0.0, 84.0, 112.0, 134.0]
DW_A = [0.85, 1.0, 1.0, 1.0, 1.0, 0.95, 0.7, 0.4]
_R = random.Random(9300)
DW_RAYS = [(i * TAU / 16 + _R.uniform(-0.06, 0.06), _R.uniform(0.6, 1.0), _R.uniform(2.2, 3.6)) for i in range(16)]


def dawn_frame(f, S=320):
    pad = Pad(S, S)
    c = S / 2
    A = DW_A[f]
    glow = pad.zeros()
    core = pad.zeros()
    X, Y = pad.grid()
    rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    th = np.arctan2(Y - c, X - c)
    bead = -math.pi / 4  # 오른쪽 위 (화면 y 가 아래로 + 이므로 -45°)
    bx, by = c + math.cos(bead) * DW_R, c + math.sin(bead) * DW_R
    # 코로나: 원판 둘레의 일렁이는 빛 (가려진 원판 안은 비움 — 하늘색)
    nz = fbm(np.cos(th) * 3 + 10, np.sin(th) * 3 + 10, seed=93, octaves=3)
    streamer = clamp01(0.55 + 0.6 * nz)
    corona = np.exp(-np.maximum(0, rr - DW_R) / (22.0 + 26.0 * streamer)) * (rr >= DW_R - 1) * (0.75 if f >= 1 else 0.6)
    lit = DW_LIT[f]
    disk = clamp01((DW_R - rr) * SS * 0.25 + 0.5)
    if lit > 0:
        # 구슬 쪽부터 밝아지는 원판 (낫 꼴 → 반 → 가득)
        ux, uy = math.cos(bead), math.sin(bead)
        proj = ((X - c) * ux + (Y - c) * uy) / DW_R
        cres = clamp01((proj - (1 - 2 * lit)) * 3.0) * disk
        core = np.maximum(core, cres * A)
        glow = np.maximum(glow, cres * A)
    glow = np.maximum(glow, corona * A * (1.0 - 0.4 * lit))
    if f <= 4:
        ring = clamp01(1 - np.abs(rr - DW_R) * SS * 0.2) * A
        core = np.maximum(core, ring * (0.9 if f <= 1 else 0.6))
    if f >= 1 and f <= 5:
        # 다이아몬드 구슬 · 큰 네 뿔 별
        k = 1.0 if f == 1 else max(0.3, 1 - 0.2 * (f - 1))
        core = np.maximum(core, pad.disc_field(bx, by, 9 + 7 * k, soft=3) * A)
        core = np.maximum(core, star4(pad, bx, by, 80 * k, A, rot=0.12, thin=0.07, diag=0.35))
        glow = np.maximum(glow, pad.radial(bx, by, 60 * k, 1.2) * A)
    ray_len = DW_RAY[f]
    if ray_len > 0:
        for a, kk, w in DW_RAYS:
            L = ray_len * kk
            r0 = DW_R * (0.7 if lit >= 1 else 1.04)
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * (DW_R + L), c + math.sin(a) * (DW_R + L))
            core = np.maximum(core, pad.stroke([p0, p1], w, 0.4, a0=0.9 * A, a1=0.0))
            glow = np.maximum(glow, pad.stroke([p0, p1], w * 3.2, 1.0, a0=0.55 * A, a1=0.0, soft=2.5))
    if lit >= 1:
        glow = np.maximum(glow, pad.radial(c, c, DW_R * 2.1, 1.1) * 0.8 * A)
    ring_r = DW_RING[f]
    if ring_r > 0:
        core = np.maximum(core, pad.stroke(arc_pts(c, c, ring_r, 0, TAU, n=96), 3.4 * A, a0=0.85 * A))
        glow = np.maximum(glow, pad.stroke(arc_pts(c, c, ring_r, 0, TAU, n=96), 14.0, a0=0.5 * A, soft=5.0))
    g, cc = finish(pad, glow, core, halo=((2.0, 0.5), (5.0, 0.38), (12.0, 0.26)), body_k=0.9, core_bloom=((1.5, 0.4), (4.0, 0.3)), win=5.0)
    return g, cc


sprite("rfx_jdawn", "rotate_xyz", 320, 320, DW_N, 4, "dawn", zoom=1,
       desc="다시 뜨는 해: 0 가려진 해의 가는 테 · 일렁이는 코로나 (원판 안은 빔) → 1 오른쪽 위가 번쩍 (다이아몬드 고리 · 큰 네 뿔 별) → 2 ~ 4 구슬 쪽부터 원판이 밝아지며 "
       "빛살 열여섯이 뻗음 → 5 ~ 7 가득 찬 해 · 큰 빛 고리가 퍼지며 흐려짐")(dawn_frame)


# ------------------------------------------------------------ rfx_jlightbloom (빛 꽃 — 카메라를 봄, 칸 가운데 = 꽃 가운데)

LB_N = 8
# 장마다: 열린 몫 · 바깥으로 날아간 거리 · 밝기 · 가운데 별 · 돎 (도)
LB_OPEN = [0.12, 0.48, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0]
LB_FLY = [0.0, 0.0, 0.0, 0.0, 0.0, 14.0, 30.0, 46.0]
LB_A = [0.8, 0.95, 1.0, 1.0, 1.0, 0.85, 0.55, 0.25]
LB_STAR = [0.3, 0.5, 0.7, 0.9, 1.0, 0.8, 0.35, 0.0]
LB_ROT = [0.0, 3.0, 6.0, 8.0, 11.0, 14.0, 17.0, 20.0]


def _petal_mask(pad, c, ang, r0, L, w):
    p0 = (c + math.cos(ang) * r0, c + math.sin(ang) * r0)
    p1 = (c + math.cos(ang) * (r0 + L), c + math.sin(ang) * (r0 + L))
    return lens(pad, p0, p1, w, 1.0, power=0.55, skew=0.62), p0, p1


def lightbloom_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(9400 + f)
    o, fly, A, sk, rot = LB_OPEN[f], LB_FLY[f], LB_A[f], LB_STAR[f], math.radians(LB_ROT[f])
    st = Stack(pad)
    X, Y = pad.grid()
    rr = np.sqrt((X - c) ** 2 + (Y - c) ** 2)
    # 빛살 (꽃잎 뒤 — 활짝 필 때): 가는 빛줄기 열둘
    if o >= 0.8 and fly <= 0:
        for i in range(12):
            a = rot * 2 + i * TAU / 12 + math.pi / 12
            p1 = (c + math.cos(a) * 118, c + math.sin(a) * 118)
            st.add_core(pad.stroke([(c, c), p1], 1.6, 0.3, a0=0.45 * A * sk, a1=0.0))
            st.body = np.maximum(st.body, pad.stroke([(c, c), p1], 6.0, 1.0, a0=0.3 * A, a1=0.0, soft=2.0))
    # 겹꽃잎: 바깥 여섯 (길게) → 안 여섯 (30° 돌려 짧게 — 앞에 덮음). 꽃잎 몸은 밑동이 밝고 끝으로 옅어짐 (빛)
    shrink = 1 - 0.5 * fly / 46.0
    for layer, (n, Lmax, wmax, off, br) in enumerate(((6, 90.0, 44.0, 0.0, 0.5), (6, 60.0, 34.0, math.pi / 6, 0.68))):
        L = Lmax * (0.32 + 0.68 * o) * shrink
        w = wmax * (0.42 + 0.58 * o) * shrink
        r0 = 6.0 + fly * (1.0 if layer == 0 else 0.75)
        for i in range(n):
            ang = rot + off + i * TAU / n - math.pi / 2
            m, p0, p1 = _petal_mask(pad, c, ang, r0, L, w)
            grad = clamp01(1.15 - (rr - r0) / max(1.0, L)) * m
            vein = pad.stroke([p0, ((p0[0] * 0.25 + p1[0] * 0.75), (p0[1] * 0.25 + p1[1] * 0.75))], 1.6, 0.6, a0=0.75, a1=0.15) * m
            st.over(m, br * A * (0.55 + 0.45 * grad), (rim_of(m, 1.8) * 0.95 + vein * 0.6 + grad * 0.35) * A)
    # 가운데: 빛 심 (꽃술 점 여섯) · 네 뿔 별
    st.add_core(pad.disc_field(c, c, 8 + 4 * o, soft=2.5) * A)
    for i in range(6):
        a = rot + i * TAU / 6
        st.add_core(pad.disc_field(c + math.cos(a) * 13 * o, c + math.sin(a) * 13 * o, 2.4, soft=0.8) * A * o)
    if sk > 0:
        st.add_core(star4(pad, c, c, 30 + 26 * sk, sk, rot=rot, thin=0.09, diag=0.45))
    glow_extra = pad.down(pad.radial(c, c, 70 + 20 * o, 1.3)) * 0.55 * A
    # 반짝이: 피어 있을 때 둘레를 돌고, 터지면 사방으로
    for i in range(10):
        a = i * TAU / 10 + rot * 3
        r = (74 + 8 * math.sin(i)) if fly <= 0 else (60 + fly * 1.1 + rs.uniform(-6, 6))
        if o < 0.7:
            continue
        x, y = c + math.cos(a) * min(r, 118), c + math.sin(a) * min(r, 118)
        st.add_core(star4(pad, x, y, rs.uniform(3, 6), A * rs.uniform(0.6, 1.0), rot=rs.uniform(0, 1), diag=0.0))
    if fly > 0:
        st.add_core(pad.stroke(arc_pts(c, c, 30 + fly * 1.4, 0, TAU, n=64), 2.4 * A + 0.5, a0=0.7 * A))
    core1 = pad.down(st.core)
    glow1 = obj_glow(pad, st.body, core1, halo=((1.4, 0.5), (3.5, 0.38), (8.0, 0.24)), body_k=0.92, core_bloom=((1.4, 0.32), (3.5, 0.24)))
    glow1 = screen(glow1, glow_extra)
    wb = win_border(core1.shape, 4.0)
    return clamp01(glow1) * wb, clamp01(core1) * wb


sprite("rfx_jlightbloom", "rotate_xyz", 256, 256, LB_N, 4, "bladedancer", zoom=0,
       desc="빛 꽃: 0 ~ 2 겹꽃잎 (바깥 여섯 · 안 여섯) 봉오리가 열림 → 3 ~ 4 활짝 핀 빛 꽃 (꽃잎 테 · 잎맥 · 가운데 꽃술 · 네 뿔 별 · 둘레를 도는 반짝이) → 5 ~ 7 꽃잎이 "
       "바깥으로 날아가며 작아지고 퍼지는 빛 고리 · 흩어지는 반짝이")(lightbloom_frame)


# ------------------------------------------------------------ rfx_jribbon (빛 리본 — 땅과 나란히 누움, 이어 돌림, 칸 가운데 = 몸)

RB_N = 8
RB_R = 108.0


def ribbon_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    ph = f / RB_N
    for band in range(2):
        a0 = TAU * ph + band * math.pi
        span = math.radians(165)
        n = 72
        pts = []
        for i in range(n + 1):
            t = i / n
            a = a0 + span * t
            r = RB_R + 5.5 * math.sin(TAU * (2 * t + ph)) - 6.0 * (1 - t)
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))

        def wf(t, band=band):
            twist = 0.42 + 0.58 * abs(math.cos(math.pi * (2.6 * t + ph * 2 + band * 0.5)))
            return 12.5 * (math.sin(math.pi * min(1.0, t * 1.08)) ** 0.7) * twist * (0.35 + 0.65 * t)
        m = pad.poly(taper_poly(pts, wf), 1.0)
        # 앞 (머리 — 진행 쪽) 이 밝고 꼬리로 옅어짐
        d, s = pad.line_field(pts, 16)
        fade = clamp01(s * 1.25) ** 1.2
        glow = np.maximum(glow, m * fade * 0.8)
        core = np.maximum(core, rim_of(m, 1.7) * fade * 0.95)
        core = np.maximum(core, pad.stroke(pts[int(n * 0.35):], 1.4, 2.2, a0=0.25, a1=0.85) * m)
        # 머리 반짝임 · 꼬리에서 떨어지는 반짝이
        hx, hy = pts[-1]
        core = np.maximum(core, star4(pad, hx, hy, 9.0, 1.0, rot=a0, thin=0.12, diag=0.3))
        rs = random.Random(9500 + band)
        for k in range(6):
            t = (rs.random() + ph * 0.9) % 0.6
            px, py = pts[int(t * n)]
            core = np.maximum(core, star4(pad, px + rs.uniform(-4, 4), py + rs.uniform(-4, 4), rs.uniform(2, 3.6), 0.4 + 0.5 * t, diag=0.0))
    g, cc = finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.38), (8.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.3), (3.0, 0.22)), win=4.0)
    return g, cc


sprite("rfx_jribbon", "direction_z", 256, 256, RB_N, 4, "bladedancer", zoom=0, loop=True,
       desc="빛 리본 (땅과 나란히 누움 — 이어 돌림, 장마다 45°): 몸 둘레를 휘감아 도는 두 띠 (반지름 108 = 반 크기 × 0.85 · 끝이 가늘고 비틀려 폭이 바뀜 · 머리가 밝고 꼬리로 "
       "옅어짐) · 머리 반짝임 · 꼬리에서 떨어지는 반짝이")(ribbon_frame)


# ------------------------------------------------------------ rfx_jfairy (요정 날개 — 등 뒤, 이어 돌림 4장 날갯짓, 날개 뿌리 = 칸 가운데 조금 아래)

FA_N = 4
FA_SPREAD = [1.0, 0.86, 0.7, 0.86]
FA_LIFT = [0.0, 3.0, 6.0, 3.0]


def _wing_outline(root, sg, kx, lift, pts_rel):
    """뿌리 root 에서 상대 점들 (x 는 바깥쪽 +) 을 지나는 매끈한 닫힌 날개 (베지어 고리)"""
    rx, ry = root
    P = [(rx + sg * x * kx, ry + y - lift * (abs(y) / 80.0)) for x, y in pts_rel]
    out = []
    m = len(P)
    for i in range(m):
        p0 = P[i]
        p1 = P[(i + 1) % m]
        p2 = P[(i + 2) % m]
        a = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2)
        b = ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
        out += bez(a, p1, b, 12)[:-1]
    return out, P


def fairy_frame(f, W=256, H=192):
    pad = Pad(W, H)
    root = (W / 2, H * 0.53)
    kx, lift = FA_SPREAD[f], FA_LIFT[f]
    glow = pad.zeros()
    core = pad.zeros()
    UP = [(4, -4), (30, -44), (70, -78), (110, -92), (118, -78), (96, -40), (56, -12)]
    LO = [(4, 4), (36, 12), (68, 36), (84, 62), (70, 70), (30, 40)]
    for sg in (-1, 1):
        for shape, br in ((UP, 0.46), (LO, 0.4)):
            outline, P = _wing_outline(root, sg, kx, lift if shape is UP else -lift * 0.5, shape)
            m = pad.poly(outline, 1.0)
            # 비치는 막 (가장자리로 갈수록 짙음) · 밝은 테 · 날개맥 · 끝 반짝임
            rim_w = rim_of(m, 9.0)
            glow = np.maximum(glow, m * (br * 0.42) + rim_w * br * 0.7)
            core = np.maximum(core, rim_of(m, 1.6) * 0.95)
            tip = max(P, key=lambda q: math.hypot(q[0] - root[0], q[1] - root[1]))
            # 날개맥: 뿌리에서 부채처럼 퍼지는 여섯 · 그 사이를 잇는 가로맥 두 줄 (잠자리 · 요정 날개)
            ends = [P[1 + k * (len(P) - 2) // 6] for k in range(6)]
            veins = []
            for tgt in ends:
                mid = ((root[0] + tgt[0]) / 2 + sg * 5, (root[1] + tgt[1]) / 2 - 3)
                vein = bez(root, mid, (tgt[0] * 0.94 + root[0] * 0.06, tgt[1] * 0.94 + root[1] * 0.06), 16)
                veins.append(vein)
                core = np.maximum(core, pad.stroke(vein, 1.2, 0.45, a0=0.5, a1=0.18) * m)
            for frac in (0.45, 0.72):
                cross = [v[int(frac * (len(v) - 1))] for v in veins]
                core = np.maximum(core, pad.stroke(cross, 0.9, a0=0.32) * m)
            core = np.maximum(core, star4(pad, tip[0], tip[1], 8 + 2 * (1 - kx) * 10, 0.9, rot=0.3, thin=0.12, diag=0.0))
    core = np.maximum(core, pad.disc_field(root[0], root[1], 4.0, soft=1.5))
    core = np.maximum(core, star4(pad, root[0], root[1], 14, 0.85, rot=0.0, diag=0.0))
    # 떨어지는 빛 가루 (이어 돌림 — 장마다 조금씩 내려감)
    rnd = random.Random(9600)
    for i in range(10):
        x0 = W / 2 + rnd.choice((-1, 1)) * rnd.uniform(24, 108)
        t = (rnd.random() + f / FA_N) % 1.0
        y = H * 0.6 + 64 * t
        core = np.maximum(core, star4(pad, x0 + 4 * math.sin(t * 6 + i), y, 2.4 + 2 * rnd.random(), math.sin(math.pi * t) * 0.85, diag=0.0))
    g, cc = finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.34), (9.0, 0.2)), body_k=0.95, core_bloom=((1.4, 0.3), (3.5, 0.22)), win=4.0)
    return g, cc


sprite("rfx_jfairy", "direction_z", 256, 192, FA_N, 4, "bladedancer", zoom=2, loop=True,
       desc="요정 날개 (등 뒤 — 이어 돌림 4장 날갯짓): 비치는 두 쌍 (위 길고 뾰족한 날개 · 아래 작은 날개 — 가장자리로 짙어지는 막 · 밝은 테 · 부채꼴 날개맥 여섯 · "
       "가로맥 두 줄 · 끝 반짝임) · 뿌리 섬광 · 떨어지는 빛 가루")(fairy_frame)


# ============================================================ 실행


# ============================================================ 0.51.0 재설계 — 타격감 · 검사 2차 (소드마스터)
# 사용자 "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음": 맞은 자리의 큰 타격 섬광 (모든 2차 직업 — JobImpact) · 칼집 섬광의 칼자국 다발 ·
# 불의 울타리의 서 있는 불꽃

COLORS.setdefault("swordmaster", ((1.0, 0.8, 0.3), (0.94, 0.93, 0.88)))
COLORS.setdefault("forgefire", ((1.0, 0.38, 0.08), (1.0, 0.88, 0.5)))

# ------------------------------------------------------------ rfx_jimpact (타격 섬광 — 카메라를 봄)

IM_N = 8
_IM_RS = random.Random(9700)
IM_RAYS = [(i * TAU / 12 + _IM_RS.uniform(-0.2, 0.2), _IM_RS.uniform(0.72, 1.0), _IM_RS.uniform(0.75, 1.3)) for i in range(12)]
_IM_SP = random.Random(9750)
IM_SPARKS = [(_IM_SP.uniform(0, TAU), _IM_SP.uniform(0.55, 1.0), _IM_SP.uniform(2.0, 3.6)) for _ in range(18)]


def impact_frame(f, S=256):
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    ca = [1.0, 0.95, 0.7, 0.45, 0.26, 0.13, 0.05, 0.0][f]
    cr = [24, 32, 30, 24, 18, 12, 8, 4][f]
    if ca > 0:
        core = np.maximum(core, pad.disc_field(c, c, cr, soft=1.2) * ca)
        glow = np.maximum(glow, pad.radial(c, c, cr * 2.8, 1.3) * ca)
    # 가시: 처음 두 장이 가장 길고 굵음 → 짧고 가늘어짐
    rl = [0.8, 1.0, 0.84, 0.6, 0.4, 0.22, 0.1, 0.0][f]
    rw = [8.0, 7.0, 5.0, 3.4, 2.4, 1.6, 1.0, 0.0][f]
    if rl > 0:
        for (a, k, w) in IM_RAYS:
            L = 118 * k * rl
            r0 = cr * 0.35
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * max(L, r0 + 4), c + math.sin(a) * max(L, r0 + 4))
            glow = np.maximum(glow, lens(pad, p0, p1, rw * w, a=0.95, power=0.55, skew=0.16))
            core = np.maximum(core, lens(pad, p0, p1, rw * w * 0.36, a=0.45 + 0.55 * ca, power=0.6, skew=0.14))
    # 퍼지는 충격 고리
    rr = [0, 44, 66, 84, 98, 108, 114, 118][f]
    ra = [0, 0.95, 0.85, 0.68, 0.5, 0.32, 0.18, 0.08][f]
    rwid = [0, 7.0, 5.6, 4.4, 3.4, 2.6, 2.0, 1.6][f]
    if ra > 0:
        ring = arc_pts(c, c, rr, 0, TAU, n=120)
        glow = np.maximum(glow, pad.stroke(ring, rwid, a0=ra))
        core = np.maximum(core, pad.stroke(ring, rwid * 0.32, a0=ra * 0.85))
    # 바깥으로 튀는 불티 줄
    if f >= 1:
        q = (f - 1) / (IM_N - 2)
        for (a, k, w) in IM_SPARKS:
            d1 = 28 + 92 * k * (q ** 0.7)
            d0 = max(10, d1 - 24 * (1 - 0.5 * q))
            sa = (1 - q) ** 1.2
            if sa < 0.03:
                continue
            p0 = (c + math.cos(a) * d0, c + math.sin(a) * d0)
            p1 = (c + math.cos(a) * d1, c + math.sin(a) * d1)
            sk = streak(pad, p0, p1, w, a=sa)
            core = np.maximum(core, sk)
            glow = np.maximum(glow, sk * 0.7)
    if f <= 2:
        core = np.maximum(core, star4(pad, c, c, [72, 90, 52][f], [1.0, 0.92, 0.5][f], rot=0.3, thin=0.08, diag=0.5))
    return finish(pad, glow, core, halo=((1.4, 0.55), (3.5, 0.42), (9.0, 0.28)), body_k=0.9, core_bloom=((1.4, 0.4), (3.5, 0.3)), win=4.0)


sprite("rfx_jimpact", "rotate_xyz", 256, 256, IM_N, 4, "swordmaster", zoom=0,
       desc="타격 섬광 (모든 2차 직업의 맞은 자리 — JobImpact): 0 흰 심지 · 길고 굵은 가시 열둘 · 네 뿔 별 → 1 가시가 가장 길고 충격 고리 → 2 ~ 4 가시가 짧고 가늘어지며 "
       "고리가 퍼지고 불티 줄이 바깥으로 튐 → 5 ~ 7 흐려짐. 게임은 빛 칸에 직업 겉빛, 심지 칸에 흰빛")(impact_frame)


# ------------------------------------------------------------ rfx_jcuts (칼집 섬광 — 칼자국 다발, 카메라를 봄)

CT_N = 8
CUT_LINES = [(-12, 0.95), (160, 0.86), (32, 0.9), (205, 0.8), (6, 1.0), (145, 0.88), (78, 0.74)]


def cuts_frame(f, S=320):
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    shown = [1, 3, 5, 7, 7, 7, 7, 7][f]
    fade = [1.0, 1.0, 1.0, 1.0, 1.0, 0.7, 0.42, 0.18][f]
    rs = random.Random(9800)
    for i, (deg, k) in enumerate(CUT_LINES):
        off = rs.uniform(-26, 26)
        bend = rs.uniform(-14, 14)
        if i >= shown:
            continue
        a = math.radians(deg)
        L = 148 * k
        na = a + math.pi / 2
        cx, cy = c + math.cos(na) * off, c + math.sin(na) * off
        p0 = (cx - math.cos(a) * L, cy - math.sin(a) * L)
        p1 = (cx + math.cos(a) * L, cy + math.sin(a) * L)
        pts = []
        for jj in range(33):
            t = jj / 32
            b = bend * math.sin(math.pi * t)
            pts.append((p0[0] + (p1[0] - p0[0]) * t + math.cos(na) * b, p0[1] + (p1[1] - p0[1]) * t + math.sin(na) * b))
        newest = i >= shown - 2 and f <= 3
        w = (14.0 if newest else 9.0) * (0.55 + 0.45 * fade)
        al = (1.0 if newest else 0.8) * fade
        glow = np.maximum(glow, pad.poly(taper_poly(pts, lambda t, w=w: w / 2 * (math.sin(math.pi * t) ** 0.55)), al))
        core = np.maximum(core, pad.poly(taper_poly(pts, lambda t, w=w: w * 0.17 * (math.sin(math.pi * t) ** 0.6)), min(1.0, al * 1.1)))
        if newest:
            core = np.maximum(core, star4(pad, pts[16][0], pts[16][1], 24, 0.9, rot=a, thin=0.1, diag=0.3))
        if f >= 5:
            sr = random.Random(9810 + i * 7 + f)
            for _ in range(3):
                t = sr.uniform(0.15, 0.85)
                px, py = pts[int(t * 32)]
                core = np.maximum(core, star4(pad, px + sr.uniform(-5, 5), py + sr.uniform(-5, 5), sr.uniform(3, 6), 0.7 * fade + 0.2, diag=0.0))
    if f == 4:
        core = np.maximum(core, pad.disc_field(c, c, 18, soft=1.5))
        core = np.maximum(core, star4(pad, c, c, 110, 1.0, rot=0.2, thin=0.06, diag=0.45))
        glow = np.maximum(glow, pad.radial(c, c, 96, 1.4) * 0.8)
    return finish(pad, glow, core, halo=((1.4, 0.5), (3.5, 0.38), (8.0, 0.24)), body_k=0.9, core_bloom=((1.4, 0.34), (3.5, 0.24)), win=4.0)


sprite("rfx_jcuts", "rotate_xyz", 320, 320, CT_N, 4, "swordmaster", zoom=0,
       desc="칼집 섬광의 칼자국 다발 (1인칭 눈앞): 0 ~ 3 칼자국이 둘씩 그어짐 (막 그은 둘은 굵고 밝음 · 가운데 반짝임 — 가로 · 비스듬 · 되돌림이 엇갈림, 조금 휨) → "
       "4 모두 번쩍 (가운데 흰 섬광 · 큰 네 뿔 별) → 5 ~ 7 가늘어지며 흐려지고 칼자국을 따라 반짝이")(cuts_frame)


# ------------------------------------------------------------ rfx_jflame (서 있는 불꽃 — 아래 끝 = 땅, 이어 돌림)

FL_N = 6


def flame_frame(f, W=128, H=256):
    pad = Pad(W, H)
    X, Y = pad.grid()
    t = np.clip((H - Y) / H, 0, 1)  # 0 = 땅 → 1 = 꼭대기
    ph = f / FL_N
    cx = W / 2
    # 위로 흐르는 노이즈 (한 바퀴에 첫 겹 주기만큼 — 이어 돌림)
    n1 = fbm(X / W * 3.0, Y / H * 4.0 + ph * 4.0, seed=411, octaves=3, px=3, py=4)
    n2 = fbm(X / W * 6.0 + 7.3, Y / H * 8.0 + ph * 8.0, seed=433, octaves=2, px=6, py=8)
    sway = (n1 - 0.5) * 34.0 * t
    width = 46.0 * (1 - t) ** 0.55 * (0.75 + 0.5 * n2)
    dx = np.abs(X - cx - sway)
    body = clamp01((width - dx) / 6.0) * clamp01(t * 14.0) * clamp01((0.96 - t) * 8.0)
    # 꼭대기 쪽 혀 (갈라진 불꽃): 노이즈로 깎음
    tongue = clamp01((n1 + 0.35 - t * 0.9) * 3.0)
    body = body * np.maximum(tongue, clamp01((0.55 - t) * 4.0))
    hot = clamp01((width * 0.45 - dx) / 5.0) * clamp01((0.62 - t) * 3.0) * clamp01(t * 10.0)
    hot = hot * (0.75 + 0.25 * n2)
    glow = body.astype(np.float32)
    core = np.maximum(hot, body * 0.25).astype(np.float32)
    # 불티: 위로 날아가는 작은 점
    rs = random.Random(9900)
    for _ in range(7):
        x0 = rs.uniform(cx - 26, cx + 26)
        y0 = rs.uniform(40, 200)
        y = (y0 - ph * 180) % 220 + 20
        a = clamp01((y - 20) / 60.0)
        core = np.maximum(core, pad.disc_field(x0 + math.sin(y * 0.05) * 6, y, 2.2, soft=1.0) * a)
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.36), (9.0, 0.2)), body_k=0.92, core_bloom=((1.4, 0.32), (3.5, 0.2)), win=3.0)


sprite("rfx_jflame", "lookat_y", 128, 256, FL_N, 6, "forgefire", zoom=0, loop=True,
       desc="서 있는 불꽃 (아래 끝 = 땅 — 이어 돌림, 위로 흐르는 불): 아래가 넓고 위로 가늘어지는 혀 · 노이즈로 일렁이며 갈라지는 꼭대기 · 아래쪽의 밝은 속불 · 위로 "
       "날아가는 불티")(flame_frame)

# ============================================================ 0.51.0 전사 2차 재설계안 2판 — 워로드 의 땅 · 글래디에이터 의 결투
# 워로드: 빛 칸 = 돌 몸 (짙은 흙빛 돌 — 거의 불투명) · 심지 칸 = 틈의 주황 (녹은 금) / 글래디에이터: 빛 칸 = 진홍 기운 · 나무 말뚝 몸, 심지 칸 = 모래 금빛 테 · 룬 · 밧줄

COLORS.setdefault("warlord_rock", ((0.34, 0.25, 0.18), (1.0, 0.58, 0.16)))
COLORS.setdefault("gladiator", ((0.98, 0.26, 0.16), (0.98, 0.9, 0.7)))
COLORS.setdefault("stake", ((0.45, 0.28, 0.16), (0.98, 0.26, 0.16)))

# ------------------------------------------------------------ rfx_jspike (땅에서 솟는 돌창 무리 — 아래 끝 = 땅)

SP_N = 8
# 돌창: 밑동 x · 높이 · 끝 기울기 · 밑 너비 · 솟는 늦음 (0 ~ 1)
SP_SHARDS = [(128, 214, 6, 56, 0.0), (88, 146, -30, 40, 0.18), (170, 162, 30, 42, 0.1), (54, 92, -34, 28, 0.32), (204, 100, 36, 30, 0.26)]


def _shard(bx, h, lean, w, base, rise):
    """모난 돌창 (밑 왼쪽 · 왼 어깨 · 끝 · 오른 어깨 · 밑 오른쪽) — rise = 땅 위로 나온 몫 (아래로 밀어 땅에서 자름)"""
    dy = h * (1 - rise)
    pts = [(bx - w / 2, base + 4), (bx - w * 0.47 + lean * 0.22, base - h * 0.4 + dy), (bx + lean * 0.86 - w * 0.08, base - h * 0.88 + dy),
           (bx + lean, base - h + dy), (bx + lean * 0.9 + w * 0.1, base - h * 0.82 + dy), (bx + w * 0.44 + lean * 0.3, base - h * 0.46 + dy), (bx + w / 2, base + 4)]
    return pts


def _cracks(rs, bx, h, lean, w, base, dy):
    """돌창 안의 갈라진 금 (밑에서 끝 쪽으로 — 가지 하나 · 둘)"""
    out = []
    x, y = bx + rs.uniform(-w * 0.12, w * 0.12), base - 4
    main = [(x, y)]
    for k in range(1, 7):
        t = k / 6
        main.append((bx + lean * t * 0.85 + rs.uniform(-w * 0.14, w * 0.14) * (1 - t), base - h * 0.78 * t + dy))
    out.append(main)
    for k in range(rs.randint(1, 2)):
        i = rs.randint(1, 3)
        q = main[i]
        side = rs.choice((-1, 1))
        out.append([q, (q[0] + side * w * 0.22, q[1] - h * 0.08), (q[0] + side * w * 0.3, q[1] - h * 0.18)])
    return out


def spike_frame(f, S=256):
    pad = Pad(S, S)
    base = S - 8.0
    body = pad.zeros()
    core = pad.zeros()
    glow_extra = pad.zeros()
    rs0 = random.Random(5100)
    crack_sets = [_cracks(random.Random(5110 + i), bx, h, lean, w, base, 0.0) for i, (bx, h, lean, w, _) in enumerate(SP_SHARDS)]
    rise_f = [0.38, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    pulse = [0.5, 0.6, 0.8, 0.75, 1.0, 0.85, 1.0, 0.4][f]
    for i, (bx, h, lean, w, late) in enumerate(SP_SHARDS):
        rise = clamp01((rise_f - late * 0.6) / (1 - late * 0.6)) if f <= 2 else 1.0
        if rise <= 0.02:
            continue
        dy = h * (1 - rise)
        pts = _shard(bx, h, lean, w, base, rise)
        if f >= 6:
            # 6 금이 벌어져 조각이 갈라짐 · 7 조각이 무너져 흩어짐 (아래로 · 바깥으로)
            cuts = [base - h * 0.35, base - h * 0.68]
            pieces = []
            ys = [base + 6] + cuts + [base - h - 6]
            for k in range(len(ys) - 1):
                y0, y1 = ys[k], ys[k + 1]
                piece = []
                for (x, y) in pts:
                    piece.append((x, min(y0, max(y1, y))))
                pieces.append((k, piece))
            for k, piece in pieces:
                if f == 6:
                    ox, oy = (bx - S / 2) * 0.03 * k, -2.0 * k
                    a = 1.0
                else:
                    ox, oy = (bx - S / 2) * 0.12 * (k + 1) + rs0.uniform(-6, 6), 14.0 + 10.0 * k
                    a = 0.7
                pp = [(x + ox, y + oy) for x, y in piece]
                m, b, c = faceted(pad, pp, a=a)
                body = np.maximum(body, b * 0.98)
                core = np.maximum(core, rim_of(m, 0.5) * 0.35 * a)
            for line in crack_sets[i]:
                core = np.maximum(core, pad.stroke(line, 4.6 if f == 6 else 2.0, 2.4 if f == 6 else 1.0, a0=1.0 if f == 6 else 0.45, a1=0.7))
                glow_extra = np.maximum(glow_extra, pad.stroke(line, 12.0, 6.0, a0=0.7 if f == 6 else 0.25, a1=0.3))
            continue
        m, b, c = faceted(pad, pts)
        clip = pad.zeros()
        clip[: int(base * pad.ss), :] = 1
        m, b = m * clip, b * clip
        body = np.maximum(body, b * 0.98)
        core = np.maximum(core, rim_of(m, 0.5) * 0.3)
        # 틈의 녹은 빛 (서 있는 동안 맥동 — 금마다 조금씩 다르게)
        ph = pulse * (0.75 + 0.25 * math.sin(f * 2.1 + i * 1.7))
        for line in crack_sets[i]:
            ln = [(x, y + dy) for x, y in line]
            core = np.maximum(core, pad.stroke(ln, 3.0, 1.2, a0=0.95 * ph, a1=0.55 * ph) * m)
            glow_extra = np.maximum(glow_extra, pad.stroke(ln, 10.0, 5.0, a0=0.45 * ph, a1=0.2 * ph) * m)
    # 밑동: 갈라진 땅 · 튀는 흙 (솟는 동안) · 흙먼지
    if f <= 2:
        rs = random.Random(5200 + f)
        for k in range(14):
            x = rs.uniform(24, S - 24)
            y = base - rs.uniform(6, 80) * (f + 1) / 3
            r = rs.uniform(2.5, 6.0) * (1 - 0.2 * f)
            mm, bb, cc = faceted(pad, chunk_pts(rs, x, y, r), a=0.95)
            body = np.maximum(body, bb)
        body = np.maximum(body, pad.radial(S / 2, base, 110.0, 1.6, sy=0.2) * (0.7 - 0.15 * f))
    if f in (1, 2):
        glow_extra = np.maximum(glow_extra, pad.radial(S / 2, base - 8, 120.0, 2.0, sy=0.25) * 0.5)
    if f == 7:
        rs = random.Random(5300)
        for k in range(10):
            x = rs.uniform(30, S - 30)
            y = base - rs.uniform(0, 40)
            mm, bb, cc = faceted(pad, chunk_pts(rs, x, y, rs.uniform(3, 7)), a=0.6)
            body = np.maximum(body, bb)
        body = np.maximum(body, pad.radial(S / 2, base - 10, 120.0, 1.4, sy=0.3) * 0.45)
    # 빛 칸: 돌 몸 (거의 불투명 — 가장자리만 살짝) + 틈 빛 번짐 (게임은 빛 칸을 짙은 흙빛으로 — 번짐은 어두운 테가 아니라 몸이 살짝 커 보이게만)
    g = pad.down(body)
    g = screen(g * 0.98, gblur(g, 1.2) * 0.35)
    cc = pad.down(core)
    ge = pad.down(glow_extra)
    cc = screen(cc, gblur(cc, 2.0) * 0.5, ge * 0.6)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jspike", "lookat_y", 256, 256, SP_N, 4, "warlord_rock", zoom=3,
       desc="땅에서 솟는 돌창 무리 (아래 끝 = 땅 — 워로드 대지 가르기 · 동면 · 곰의 포옹 · 거인의 무게 · 마지막 휘두름): 0 ~ 2 모난 돌창 다섯이 땅을 뚫고 솟음 (가운데가 먼저 · "
       "흙 조각이 튐 · 밑동의 흙먼지) → 3 ~ 5 서 있음 (돌 면에 빛이 비치고 틈의 녹은 주황이 맥동 — 이어 돌림) → 6 금이 벌어져 조각이 갈라짐 · 7 조각이 무너져 "
       "흩어짐")(spike_frame)


# ------------------------------------------------------------ rfx_jstake (결투 말뚝 — 아래 끝 = 땅)

ST_N = 6


def stake_frame(f, W=128, H=256):
    pad = Pad(W, H)
    base = H - 6.0
    cx = W / 2
    h, w = 222.0, 30.0
    rise = [0.55, 1.0, 1.0, 1.0, 1.0, 0.8][f]
    a = [1.0, 1.0, 1.0, 1.0, 1.0, 0.55][f]
    dy = h * (1 - rise)
    top = base - h + dy
    # 몸: 곧은 기둥 (아래가 조금 넓음) · 깎은 뾰족한 머리
    pts = [(cx - w / 2 - 2, base + 4), (cx - w / 2 + 1, top + 34), (cx - 3, top + 4), (cx, top), (cx + 3, top + 4), (cx + w / 2 - 1, top + 34),
           (cx + w / 2 + 2, base + 4)]
    m, b, c = faceted(pad, pts, depth=6.0)
    clip = pad.zeros()
    clip[: int(base * pad.ss), :] = 1
    m, b = m * clip, b * clip
    body = b * 0.98
    core = rim_of(m, 0.5) * 0.25 * m
    # 나뭇결 (어두운 세로 줄 — 몸을 깎음)
    rs = random.Random(5400)
    for k in range(4):
        x = cx - w / 2 + 6 + k * (w - 12) / 3 + rs.uniform(-2, 2)
        body = body * (1 - pad.stroke([(x, top + 40), (x + rs.uniform(-2, 2), base)], 1.6, a0=0.35) * m)
    # 밧줄 띠 (머리 아래 — 엇갈려 감김) · 늘어진 끝 (서 있는 동안 흔들림)
    ry = top + 50
    sway = [0.0, 4.0, 7.0, 4.0, -1.0, 0.0][f]
    for k in range(3):
        y = ry + k * 6
        core = np.maximum(core, pad.stroke([(cx - w / 2 - 3, y + 3), (cx + w / 2 + 3, y - 3)], 3.4, a0=0.95))
    tail = bez((cx + w / 2 + 2, ry + 6), (cx + w / 2 + 10 + sway * 0.3, ry + 30), (cx + w / 2 + 6 + sway, ry + 58), 16)
    core = np.maximum(core, pad.stroke(tail, 3.2, 2.0, a0=0.9, a1=0.7))
    core = np.maximum(core, pad.disc_field(tail[-1][0], tail[-1][1], 3.2))
    body = np.maximum(body, pad.stroke(tail, 4.6, 3.0, a0=0.5) )
    # 새긴 룬 ᛏ (티와즈 — 싸움의 신 튀르): 세로 줄기 · 위에서 아래로 벌어진 두 획 (맥동)
    pulse = [0.5, 0.85, 1.0, 0.8, 0.95, 0.4][f]
    ty0, ty1 = top + 92, top + 150
    rune = [[(cx, ty0), (cx, ty1)], [(cx - 10, ty0 + 14), (cx, ty0), (cx + 10, ty0 + 14)]]
    for ln in rune:
        core = np.maximum(core, pad.stroke(ln, 3.4, a0=pulse) * m)
        body = body * (1 - pad.stroke(ln, 5.0, a0=0.6) * m)
    glow_r = pad.radial(cx, (ty0 + ty1) / 2, 34.0, 1.5) * 0.55 * pulse * m
    # 머리 끝 반짝임
    core = np.maximum(core, pad.disc_field(cx, top + 3, 2.6) * 0.9)
    # 솟음 · 가라앉음: 밑동의 흙
    if f in (0, 5):
        rs2 = random.Random(5450 + f)
        for k in range(8):
            x = cx + rs2.uniform(-34, 34)
            y = base - rs2.uniform(2, 26)
            mm, bb, cc = faceted(pad, chunk_pts(rs2, x, y, rs2.uniform(2.0, 4.5)), a=0.9)
            body = np.maximum(body, bb)
    g = pad.down(body)
    g = screen(g * 0.98, gblur(g, 1.2) * 0.3)
    cc = pad.down(core)
    cc = screen(cc, gblur(cc, 2.0) * 0.45, pad.down(glow_r))
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb * a), clamp01(cc * wb * a)


sprite("rfx_jstake", "lookat_y", 128, 256, ST_N, 6, "stake", zoom=3,
       desc="결투 말뚝 (아래 끝 = 땅 — 글래디에이터 결투 고리 · 홀름강): 깎은 뾰족한 나무 말뚝 · 나뭇결 · 머리 아래 엇갈려 감긴 밧줄 띠와 늘어진 끝 · 가운데 새긴 룬 ᛏ "
       "(싸움의 신 튀르). 0 땅에서 솟음 (흙) → 1 ~ 4 서 있음 (룬 맥동 · 밧줄 끝이 흔들림 — 이어 돌림) → 5 가라앉으며 흐려짐. 게임은 빛 칸에 짙은 나무빛, 심지 칸에 "
       "직업 진홍")(stake_frame)


# ------------------------------------------------------------ rfx_jarms (여덟 팔의 도끼 잔상 — 카메라를 봄, 칸 가운데 = 가슴)

AR_N = 8
# 팔 여덟 (아수라처럼 어깨 위로 부채꼴): 어깨 (오른쪽 +1 · 왼쪽 −1) · 기본 각 (도 — 0 = 오른쪽, 위 −) · 휘두름 차례 (0 ~ 1)
AR_ARMS = [(1, -12, 0.0), (1, -42, 0.25), (1, -72, 0.5), (1, -100, 0.75), (-1, -168, 0.125), (-1, -138, 0.375), (-1, -108, 0.625), (-1, -80, 0.875)]


def _axe_head(pad, hx, hy, ux, uy, side, size):
    """도끼날 (자루 끝 hx, hy · 자루 방향 u · side = 날이 향하는 쪽) — 초승달 날 다각형 · 날 끝 줄"""
    nx, ny = -uy * side, ux * side
    pts = []
    edge = []
    for k in range(13):
        t = -1 + 2 * k / 12
        along = t * size * 0.9
        out = size * (0.55 + 0.45 * (1 - t * t))
        q = (hx + ux * along + nx * out, hy + uy * along + ny * out)
        pts.append(q)
        edge.append(q)
    for k in range(12, -1, -1):
        t = -1 + 2 * k / 12
        along = t * size * 0.55
        pts.append((hx + ux * along + nx * size * 0.12, hy + uy * along + ny * size * 0.12))
    return pad.poly(pts), edge


def _arm_pose(side, base_deg, u):
    """팔 하나의 자세 (어깨 기준 — 위팔 각 · 팔꿈치 굽힘): 들었다 내리침 (내리칠 때 빠르게 — 사인을 비틂)"""
    sw = math.sin(TAU * u - 0.9 * math.sin(TAU * u))
    ang = math.radians(base_deg + side * 34 * sw)
    bend = math.radians(side * (22 + 18 * sw))
    return ang, bend


def arms_frame(f, S=320):
    pad = Pad(S, S)
    cx, cy = S / 2, S / 2 + 14
    body = pad.zeros()
    core = pad.zeros()
    up_len, fo_len, handle, blade = 40.0, 36.0, 26.0, 19.0

    def draw_arm(side, base_deg, u, a, edge_a):
        nonlocal body, core
        sx, sy = cx + side * 28, cy - 30
        ang, bend = _arm_pose(side, base_deg, u)
        ex, ey = sx + math.cos(ang) * up_len, sy + math.sin(ang) * up_len
        a2 = ang + bend
        hx, hy = ex + math.cos(a2) * fo_len, ey + math.sin(a2) * fo_len
        ux, uy = math.cos(a2), math.sin(a2)
        tx, ty = hx + ux * handle, hy + uy * handle
        body = np.maximum(body, pad.stroke([(sx, sy), (ex, ey)], 15.0, 12.0, a0=a))
        body = np.maximum(body, pad.stroke([(ex, ey), (hx, hy)], 12.5, 8.5, a0=a))
        body = np.maximum(body, pad.disc_field(hx, hy, 7.5) * a)
        body = np.maximum(body, pad.stroke([(hx, hy), (tx, ty)], 5.0, a0=a))
        head, edge = _axe_head(pad, tx, ty, ux, uy, side, blade)
        body = np.maximum(body, head * a)
        if edge_a > 0:
            core = np.maximum(core, pad.stroke(edge, 3.0, a0=edge_a))
            core = np.maximum(core, pad.disc_field(hx, hy, 3.0) * edge_a * 0.6)

    for side, base_deg, ph in AR_ARMS:
        u = (f / AR_N + ph) % 1.0
        swinging = math.cos(TAU * u) < 0.2  # 내리치는 쪽 (빠른 반쪽)
        if swinging:
            # 잔상: 앞 몇 순간의 팔 (흐리게 — 내리친 길을 따라 남음)
            for k, ga in ((3, 0.16), (2, 0.28), (1, 0.45)):
                draw_arm(side, base_deg, (u - 0.045 * k) % 1.0, ga, 0.0)
            draw_arm(side, base_deg, u, 0.92, 1.0)
            # 날 끝이 지난 자리의 밝은 호
            r = up_len + fo_len * 0.9 + handle + blade * 0.6
            ang0, _ = _arm_pose(side, base_deg, (u - 0.16) % 1.0)
            ang1, _ = _arm_pose(side, base_deg, u)
            sx, sy = cx + side * 28, cy - 30
            arc = [(sx + math.cos(ang0 + (ang1 - ang0) * t + side * 0.35) * r, sy + math.sin(ang0 + (ang1 - ang0) * t + side * 0.35) * r) for t in np.linspace(0, 1, 16)]
            body = np.maximum(body, pad.stroke(arc, 2.0, 16.0, a0=0.05, a1=0.55))
            core = np.maximum(core, pad.stroke(arc, 0.5, 3.6, a0=0.0, a1=0.75))
        else:
            draw_arm(side, base_deg, u, 0.6, 0.55)
    g = pad.down(body)
    g = screen(g * 0.92, gblur(g, 2.0) * 0.4, gblur(g, 6.0) * 0.28)
    cc = pad.down(core)
    cc = screen(cc, gblur(cc, 1.6) * 0.45)
    wb = win_border(g.shape, 5.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jarms", "rotate_xyz", 320, 320, AR_N, 4, "gladiator", zoom=2, loop=True,
       desc="여덟 팔의 도끼 잔상 (글래디에이터 여덟 팔 — 칸 가운데 = 가슴, 이어 돌림): 양 어깨 위로 넷씩 부채꼴로 솟은 팔 여덟 (아수라처럼) 이 도끼를 쥐고 차례로 들었다 "
       "바깥으로 내리침 (내리칠 때 빠르게 — 팔마다 1/8 씩 어긋남). 내리치는 팔은 짙고 지나온 자리에 흐린 잔상 셋 · 날 끝이 지난 자리의 밝은 호, 드는 팔은 옅음")(arms_frame)


# ------------------------------------------------------------ rfx_jarena (결투장 바닥 — 땅에 누움, 고리 끝 = 반 크기 × 0.92)

AN_N = 6


def _crossed_axes(pad, cx, cy, size, a=1.0):
    """엇갈린 두 도끼 (가운데 무늬): 자루 둘이 X · 위 끝마다 바깥을 보는 날"""
    out_b = pad.zeros()
    out_c = pad.zeros()
    for s in (-1, 1):
        ang = math.radians(-90 + s * 38)
        ux, uy = math.cos(ang), math.sin(ang)
        p0 = (cx - ux * size, cy - uy * size)
        p1 = (cx + ux * size, cy + uy * size)
        out_b = np.maximum(out_b, pad.stroke([p0, p1], size * 0.11, a0=a))
        head, edge = _axe_head(pad, p1[0] - ux * size * 0.15, p1[1] - uy * size * 0.15, ux, uy, s, size * 0.38)
        out_b = np.maximum(out_b, head * a)
        out_c = np.maximum(out_c, pad.stroke(edge, size * 0.05, a0=a))
    return out_b, out_c


def arena_frame(f, S=384):
    pad = Pad(S, S)
    c = S / 2
    R = c * 0.92
    body = pad.zeros()
    core = pad.zeros()
    draw = [0.5, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    a = [1.0, 1.0, 0.9, 0.95, 0.9, 0.4][f]
    # 바깥 테 두 겹 (그어지는 중이면 반 바퀴) · 그 사이 룬 스물넷 · 말뚝 자리 여덟
    ring = arc_pts(c, c, R, -math.pi / 2, -math.pi / 2 + TAU * draw, n=160)
    ring2 = arc_pts(c, c, R - 18, -math.pi / 2, -math.pi / 2 + TAU * draw, n=160)
    body = np.maximum(body, pad.stroke(ring, 9.0, a0=0.9 * a))
    core = np.maximum(core, pad.stroke(ring, 3.2, a0=1.0 * a))
    body = np.maximum(body, pad.stroke(ring2, 4.0, a0=0.6 * a))
    core = np.maximum(core, pad.stroke(ring2, 1.4, a0=0.7 * a))
    hi = (f - 2) / 3.0 * TAU if 2 <= f <= 4 else -10
    for i in range(24):
        th = -math.pi / 2 + i * TAU / 24
        if (i / 24) > draw:
            continue
        gx, gy = c + math.cos(th) * (R - 9), c + math.sin(th) * (R - 9)
        lit = 1.0 if hi < -5 else 0.55 + 0.45 * max(0.0, math.cos(th - hi + math.pi / 2)) ** 3
        lines, dots = glyph_lines(i % len(GLYPHS), gx, gy, 9.0, 11.0, ang=th + math.pi / 2)
        for ln in lines:
            core = np.maximum(core, pad.stroke(ln, 1.8, a0=0.9 * lit * a))
            body = np.maximum(body, pad.stroke(ln, 4.0, a0=0.35 * lit * a))
    for i in range(8):
        th = math.radians(i * 45 + 22.5)
        x, y = c + math.cos(th) * R, c + math.sin(th) * R
        sq = [(x + math.cos(th + q) * 8, y + math.sin(th + q) * 8) for q in (math.pi / 4, 3 * math.pi / 4, 5 * math.pi / 4, 7 * math.pi / 4)]
        if ((th + math.pi / 2) % TAU) / TAU <= draw:
            body = np.maximum(body, pad.poly(sq, 0.95 * a))
            core = np.maximum(core, pad.disc_field(x, y, 2.6) * a)
    # 안쪽 모래 바닥 (옅은 결) · 가운데 엇갈린 두 도끼 · 둘레 고리
    X, Y = pad.grid()
    if f >= 1:
        n = fbm(X / S * 6.0, Y / S * 6.0, seed=77, octaves=3)
        inside = clamp01((R - 24 - np.sqrt((X - c) ** 2 + (Y - c) ** 2)) / 10.0)
        body = np.maximum(body, (0.1 + 0.12 * n) * inside * a)
        b2, c2 = _crossed_axes(pad, c, c, 46.0, a=0.85 * a)
        body = np.maximum(body, b2 * 0.9)
        core = np.maximum(core, c2)
        circ = arc_pts(c, c, 64.0, 0, TAU, n=120)
        body = np.maximum(body, pad.stroke(circ, 3.0, a0=0.6 * a))
        core = np.maximum(core, pad.stroke(circ, 1.2, a0=0.6 * a))
    if f == 1:
        body = np.maximum(body, pad.radial(c, c, R, 2.5) * 0.35)
    g = pad.down(body)
    g = screen(g, gblur(g, 2.0) * 0.4, gblur(g, 6.0) * 0.25)
    cc = pad.down(core)
    cc = screen(cc, gblur(cc, 1.6) * 0.4)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jarena", "direction_z", 384, 384, AN_N, 3, "gladiator", zoom=0,
       desc="결투장 바닥 (홀름강 — 땅에 누움, 바깥 테 = 반 크기 × 0.92): 바깥 테 두 겹 · 그 사이 룬 스물넷 · 말뚝 자리 여덟 · 옅은 모래 결 · 가운데 엇갈린 두 도끼와 고리. "
       "0 테가 반 바퀴 그어짐 → 1 다 그어지며 번쩍 → 2 ~ 4 서 있음 (밝은 룬이 테를 따라 돎 — 이어 돌림) → 5 흐려짐")(arena_frame)


# ============================================================ 0.51.0 마검사 2차 재설계안 2판 — 룬 스미스 의 대장간
# 빛 칸 = 쇳물 (주황 — 숫양 · 웅덩이 · 달궈진 몸) / 어두운 쇠 (룬 모루 몸 — 거의 불투명), 심지 칸 = 흰 열 (금 · 거품 · 룬 · 눈 · 뿔 테)

COLORS.setdefault("forge", ((1.0, 0.42, 0.06), (1.0, 0.88, 0.5)))
COLORS.setdefault("anvil", ((0.24, 0.2, 0.24), (1.0, 0.42, 0.06)))
COLORS.setdefault("ram", ((0.62, 0.16, 0.04), (1.0, 0.8, 0.36)))

# ------------------------------------------------------------ rfx_jheat (달궈진 몸 — 카메라를 봄)

HE_N = 6
_RH = random.Random(9810)
HE_LINES = [(_RH.uniform(-30, 30), _RH.uniform(0, 1), _RH.uniform(0.7, 1.3)) for _ in range(7)]
HE_EMB = [(_RH.uniform(-36, 36), _RH.uniform(0, 1), _RH.uniform(1.4, 2.6)) for _ in range(10)]
HE_CRACKS = []
for _i in range(5):
    _a = _RH.uniform(0, TAU)
    _pts = [(64.0, 68.0)]
    for _k in range(1, 6):
        _pts.append((64 + math.cos(_a + _RH.uniform(-0.35, 0.35)) * 9 * _k, 68 + math.sin(_a + _RH.uniform(-0.35, 0.35)) * 11 * _k))
    HE_CRACKS.append(_pts)


def heat_frame(f, S=128):
    pad = Pad(S, S)
    X, Y = pad.grid()
    cx, cy = S / 2, S / 2 + 4
    loop = 1 <= f <= 4
    ph = (f - 1) / 4 if loop else (0.0 if f == 0 else 0.6)
    n = fbm(X / S * 3.0, Y / S * 3.0 + ph * 3.0, seed=981, octaves=3, px=3, py=3)
    n2 = fbm(X / S * 6.0 + 2.0, Y / S * 4.0 + ph * 4.0, seed=982, octaves=2, px=6, py=4)
    r = np.sqrt(((X - cx) / 34.0) ** 2 + ((Y - cy) / 44.0) ** 2)
    up = clamp01((cy - Y) / 50.0)
    # 몸을 감싼 고른 기운 (가장자리 부드럽게) + 위쪽으로 일렁이는 불길 혀
    aura = smooth(1.08, 0.5, r) * (0.62 + 0.38 * n)
    tongue = clamp01((n2 - 0.42) * 3.2) * up * smooth(1.75, 0.95, r)
    glow = np.maximum(aura * 0.78, tongue * 0.7)
    core = pad.zeros()
    # 오르는 열기 줄 (물결 — 아래에서 나타나 위로 오르며 사라짐)
    for ox, p0, amp in HE_LINES:
        u = (p0 + ph) % 1.0 if loop else p0
        yc = cy + 26 - u * 78
        a = math.sin(math.pi * u) ** 1.2
        pts = [(cx + ox + math.sin(yy * 0.14 + p0 * 6.3 + ph * 6.3) * 3.6 * amp, yy) for yy in np.linspace(yc + 13, yc - 13, 14)]
        core = np.maximum(core, pad.stroke(pts, 2.4, 1.0, a0=0.85 * a, a1=0.15 * a) * (0.6 + 0.4 * (f != 5)))
        glow = np.maximum(glow, pad.stroke(pts, 7.0, 3.0, a0=0.35 * a, a1=0.05))
    # 불씨 (위로)
    for ox, p0, rr in HE_EMB:
        u = (p0 + ph * 1.0) % 1.0 if loop else p0
        y = cy + 30 - u * 86
        a = math.sin(math.pi * u)
        core = np.maximum(core, pad.disc_field(cx + ox + math.sin(u * 9 + p0 * 5) * 4, y, rr, soft=0.8) * a)
    if f == 0:
        # 달아오름: 가운데 번쩍 · 퍼지는 열 고리
        core = np.maximum(core, pad.radial(cx, cy, 34.0, 1.6) * 0.95)
        ring = np.exp(-0.5 * ((np.sqrt((X - cx) ** 2 + ((Y - cy) * 0.82) ** 2) - 40.0) / 4.0) ** 2)
        core = np.maximum(core, ring * 0.7)
        glow = screen(glow * 1.1, pad.radial(cx, cy, 52.0, 1.2) * 0.6)
    if f == 5:
        # 식으며 금이 감: 흰 열 금 (가운데에서 갈라져 나감) · 빛이 옅어짐
        for ln in HE_CRACKS:
            core = np.maximum(core, pad.stroke(ln, 3.2, 0.8, a0=1.0, a1=0.4))
            glow = np.maximum(glow, pad.stroke(ln, 9.0, 3.0, a0=0.55, a1=0.1))
        glow = glow * 0.75
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.32), (9.0, 0.2)), body_k=0.9, core_bloom=((1.4, 0.4), (3.5, 0.25)), win=4.0)


sprite("rfx_jheat", "rotate_xyz", 128, 128, HE_N, 6, "forge", zoom=2,
       desc="달궈진 몸 (룬 스미스 — 풀무 숨 · 용광로 · 쇳물 숫양에 맞은 적): 0 달아오름 (가운데 번쩍 · 퍼지는 열 고리) → 1 ~ 4 몸을 감싼 쇳물빛 기운 (위로 일렁이는 "
       "가장자리 · 물결치며 오르는 열기 줄 · 불씨 — 이어 돌림) → 5 식으며 흰 열 금이 감 (식으며 깨짐)")(heat_frame)


# ------------------------------------------------------------ rfx_jmolten (쇳물 웅덩이 — 땅에 누움)

MO_N = 8
_RM2 = random.Random(9830)
# 껍질 판의 씨: 흔든 격자 (가까이 붙은 씨가 없게 — 판이 고르게)
MO_SEEDS = [(18.0 + 37.0 * gx + _RM2.uniform(-11, 11), 18.0 + 37.0 * gy + _RM2.uniform(-11, 11), _RM2.uniform(0, TAU), _RM2.uniform(2.5, 5.0))
            for gx in range(7) for gy in range(7)]
MO_BUB = [(_RM2.uniform(-0.75, 0.75), _RM2.uniform(-0.75, 0.75), _RM2.uniform(0, 1), _RM2.uniform(5.0, 10.0)) for _ in range(9)]


def molten_frame(f, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    grow = [0.5, 0.86, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    loop = 2 <= f <= 5
    ph = (f - 2) / 4 if loop else (0.0 if f < 2 else 0.5)
    cool = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.55, 1.0][f]
    fresh = [1.0, 0.6, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0][f]
    R = 114.0 * grow
    ang = np.arctan2(Y - c, X - c)
    rr = np.hypot(X - c, Y - c)
    wob = fbm(np.cos(ang) * 1.3 + 3.0, np.sin(ang) * 1.3 + 3.0, seed=983, octaves=3)
    edge = R * (0.9 + 0.16 * (wob - 0.5) * 2.0 * 0.5 + 0.05)
    pool = smooth(edge + 3.0, edge - 3.0, rr)
    # 식은 껍질 판 (보로노이 — 판 사이가 빛나는 금): 씨가 작은 원을 그리며 움직임 (이어 돌림)
    d1 = np.full(X.shape, 1e9, np.float32)
    d2 = np.full(X.shape, 1e9, np.float32)
    for sx, sy, a0, amp in MO_SEEDS:
        px = c + (sx - c) * grow + math.cos(a0 + ph * TAU) * amp
        py = c + (sy - c) * grow + math.sin(a0 + ph * TAU) * amp
        d = np.hypot(X - px, Y - py)
        d2 = np.where(d < d1, d1, np.minimum(d2, d))
        d1 = np.minimum(d1, d)
    gap = d2 - d1
    crack = smooth(7.0 - 3.5 * cool, 0.8, gap)
    flow = fbm(X / S * 4.0 + ph * 1.0, Y / S * 4.0, seed=985, octaves=3, px=4, py=4)
    plates = 1 - crack
    glow = pool * (crack * 0.96 + plates * (0.3 + 0.18 * flow - 0.14 * cool))
    glow = np.maximum(glow, pool * fresh * 0.95)
    core = pool * (crack ** 1.4) * (0.95 - 0.7 * cool)
    core = np.maximum(core, pool * fresh * (0.55 + 0.4 * flow))
    # 가장자리의 밝은 테 (쇳물이 땅을 녹이는 띠)
    band = np.exp(-0.5 * ((rr - edge + 5.0) / 4.0) ** 2) * pool
    core = np.maximum(core, band * (0.7 - 0.5 * cool))
    # 거품 (끓는 동안 — 부풀었다 터짐)
    if loop or f == 1:
        for bx, by, p0, br in MO_BUB:
            u = (p0 + ph) % 1.0
            x, y = c + bx * R * 0.8, c + by * R * 0.8
            if math.hypot(x - c, y - c) > R * 0.8:
                continue
            if u < 0.75:
                rad = br * (0.3 + 0.7 * u / 0.75)
                ringd = np.abs(np.hypot(X - x, Y - y) - rad)
                core = np.maximum(core, clamp01(1.0 - ringd / 1.6) * 0.95 * pool)
                core = np.maximum(core, pad.disc_field(x - rad * 0.35, y - rad * 0.35, rad * 0.22, soft=0.6) * 0.9)
            else:
                k = (u - 0.75) / 0.25
                for q in range(6):
                    aa = q * TAU / 6 + p0 * 3
                    rd = br * (1.0 + 1.2 * k)
                    core = np.maximum(core, pad.disc_field(x + math.cos(aa) * rd, y + math.sin(aa) * rd, 1.8 * (1 - k) + 0.6, soft=0.6) * (1 - k))
    if f == 7:
        glow = glow * 0.55
        core = core * 0.5
    return finish(pad, glow, core, halo=((1.5, 0.4), (4.0, 0.3), (9.0, 0.18)), body_k=0.95, core_bloom=((1.5, 0.35), (4.0, 0.22)), win=4.0)


sprite("rfx_jmolten", "direction_z", 256, 256, MO_N, 4, "forge", zoom=3, loop=True,
       desc="쇳물 웅덩이 (땅에 누움 — 룬 모루 · 용광로 · 쇳물 숫양 · 담금질): 0 ~ 1 번지며 달아오름 (갓 쏟은 쇳물 — 온통 밝음) → 2 ~ 5 끓음 (식은 껍질 판 사이의 빛나는 금 · "
       "가장자리의 녹이는 띠 · 부풀었다 터지는 거품 — 이어 돌림) → 6 ~ 7 식어 금이 가늘어지며 흐려짐")(molten_frame)


# ------------------------------------------------------------ rfx_janvil (룬 모루 — 아래 끝 = 땅, 세워 카메라 쪽으로 돎)

AN_N2 = 8
AN_BASE = 248.0
# 모루 (옆모습): 왼쪽 뿔 끝 → 윗면 → 오른쪽 굽 → 허리 (오목) → 발 → 왼쪽 허리 → 뿔 아래
AN_OUTLINE = [(14, 104), (30, 97), (52, 92), (204, 92), (210, 96), (210, 113), (194, 117), (176, 121), (160, 125), (150, 140), (150, 158), (160, 171),
              (184, 181), (192, 197), (64, 197), (72, 181), (96, 171), (106, 158), (106, 140), (96, 125), (76, 121), (56, 118), (36, 111)]
AN_PED = [(80, AN_BASE + 2), (84, 222), (94, 206), (106, 197), (152, 197), (164, 206), (174, 222), (178, AN_BASE + 2)]
# 룬 (글자 · 가운데 · 크기 · 켜지는 장)
AN_RUNES = [(0, 128, 150, 34, 2), (1, 84, 105, 18, 3), (2, 172, 105, 18, 4)]
_RA = random.Random(9850)
AN_CRACKS = []
for _i in range(6):
    _x = _RA.uniform(70, 186)
    _y = _RA.uniform(98, 190)
    _a = _RA.uniform(0, TAU)
    _pts = [(_x, _y)]
    for _k in range(1, 5):
        _a += _RA.uniform(-0.6, 0.6)
        _pts.append((_pts[-1][0] + math.cos(_a) * 9, _pts[-1][1] + math.sin(_a) * 9))
    AN_CRACKS.append(_pts)
AN_FRAGS = [(_RA.uniform(40, 216), _RA.uniform(96, 236), _RA.uniform(7, 15), _RA.uniform(0, TAU)) for _ in range(16)]


def anvil_frame(f, S=256):
    pad = Pad(S, S)
    X, Y = pad.grid()
    body = pad.zeros()
    core = pad.zeros()
    gextra = pad.zeros()
    if f <= 5:
        rise = [0.45, 0.92, 1.0, 1.0, 1.0, 1.0][f]
        dy = (AN_BASE - 92.0) * (1 - rise)
        outline = [(x, y + dy) for x, y in AN_OUTLINE]
        ped = [(x, min(AN_BASE + 2, y + dy)) for x, y in AN_PED]
        clip = (Y < AN_BASE).astype(np.float32)
        # 받침 돌 (거칠게 · 면 빛)
        pm, pb, pc = faceted(pad, ped, depth=10.0)
        body = np.maximum(body, pb * 0.95 * clip)
        core = np.maximum(core, rim_of(pm, 0.6) * 0.18 * clip)
        # 모루 (어두운 쇠 — 비탈 빛 · 윗면 테)
        m = pad.poly(outline) * clip
        lit, dark, hh = bevel_light(m, 7.0)
        body = body * (1 - m) + m * (0.9 + 0.08 * lit - 0.12 * dark)
        heatk = [0.0, 0.0, 0.25, 0.5, 0.8, 1.0][f]
        top = pad.stroke([(30, 97 + dy), (52, 92 + dy), (204, 92 + dy), (210, 96 + dy)], 2.4, a0=0.5 + 0.4 * heatk) * clip
        core = np.maximum(core, top)
        core = np.maximum(core, (rim_of(m, 0.8) * (0.15 + 0.45 * heatk) + lit * 0.25 * heatk) * m)
        # 룬 (차례로 켜짐 — 켜진 것은 밝게, 아직은 옅은 홈)
        for k, gx, gy, gh, on in AN_RUNES:
            gm = glyph_mask(pad, k, gx, gy + dy, gh * 0.8, gh, max(2.4, gh * 0.13)) * m
            lit_on = 1.0 if f >= on else (0.18 if f >= 2 else 0.1)
            core = np.maximum(core, gm * lit_on)
            if f >= on:
                gextra = np.maximum(gextra, gblur_ss(gm, 6.0) * 0.8)
            body = body * (1 - gm * 0.35)
        # 달아오른 금 (4 ~ 5)
        if f >= 4:
            ka = 0.55 if f == 4 else 1.0
            for ln in AN_CRACKS:
                ln2 = [(x, y + dy) for x, y in ln]
                core = np.maximum(core, pad.stroke(ln2, 3.0 * ka + 0.8, 1.0, a0=ka, a1=0.5 * ka) * m)
                gextra = np.maximum(gextra, pad.stroke(ln2, 12.0, 5.0, a0=0.6 * ka, a1=0.2 * ka) * m)
        if f == 5:
            gextra = np.maximum(gextra, m * 0.35)
            core = np.maximum(core, m * 0.22)
        # 솟는 동안: 튀는 흙 · 흙먼지 · 땅의 빛
        if f <= 1:
            rs = random.Random(9860 + f)
            for _ in range(14):
                x = rs.uniform(30, S - 30)
                y = AN_BASE - rs.uniform(6, 70) * (f + 1) / 2
                mm, bb, cc = faceted(pad, chunk_pts(rs, x, y, rs.uniform(3, 7)), a=0.9)
                body = np.maximum(body, bb)
            body = np.maximum(body, pad.radial(S / 2, AN_BASE, 112.0, 1.6, sy=0.2) * 0.6)
            gextra = np.maximum(gextra, pad.radial(S / 2, AN_BASE - 6, 110.0, 2.0, sy=0.25) * 0.5)
    else:
        # 터짐: 조각이 튐 (6 — 가깝게 · 번쩍 / 7 — 멀리 · 흐림) · 조각 테의 쇳물빛
        k = 1.0 if f == 6 else 2.4
        cxm, cym = 128.0, 150.0
        rs = random.Random(9870)
        for x0, y0, r0, a0 in AN_FRAGS:
            vx, vy = x0 - cxm, y0 - cym
            L = math.hypot(vx, vy) or 1
            x = x0 + vx / L * 14 * k + rs.uniform(-4, 4)
            y = y0 + vy / L * 10 * k - 10 * k + (16 if f == 7 else 0)
            rr = r0 * (1.0 if f == 6 else 0.75)
            mm, bb, cc = faceted(pad, chunk_pts(rs, x, y, rr, rot=a0 + k), a=1.0 if f == 6 else 0.7)
            body = np.maximum(body, bb * 0.95)
            core = np.maximum(core, rim_of(mm, 1.0) * (0.95 if f == 6 else 0.5))
        if f == 6:
            core = np.maximum(core, pad.radial(cxm, cym, 70.0, 1.4) * 0.95)
            gextra = np.maximum(gextra, pad.radial(cxm, cym, 110.0, 1.2) * 0.9)
    g = pad.down(body)
    g = screen(g * 0.97, gblur(g, 1.2) * 0.3)
    cc = pad.down(core)
    ge = pad.down(gextra)
    cc = screen(cc, gblur(cc, 2.0) * 0.5, ge * 0.55)
    wb = win_border(g.shape, 3.0)
    return clamp01(g * wb), clamp01(cc * wb)


def gblur_ss(a, sigma):
    """4배 그림판에서 흐리게 (sigma 칸 점)"""
    return gblur(a, sigma * SS_ADV)


SS_ADV = 4


sprite("rfx_janvil", "lookat_y", 256, 256, AN_N2, 4, "anvil", zoom=4,
       desc="룬 모루 (룬 스미스 룬 모루 — 아래 끝 = 땅): 0 ~ 1 받침 돌과 함께 땅을 뚫고 솟음 (흙 조각 · 땅의 빛) → 2 ~ 4 서서 달아오름 (허리의 큰 룬 → 윗면의 룬 둘이 차례로 "
       "켜짐 · 윗면 테가 밝아짐 · 4 에서 흰 열 금) → 5 온통 흰 열 (터지기 직전) → 6 조각이 튀며 번쩍 · 7 조각이 멀리 흩어지며 흐려짐. 빛 = 어두운 쇠 몸 · 심지 = 룬 · 테 · 금의 쇳물빛")(
    anvil_frame)


# ------------------------------------------------------------ rfx_jram (쇳물 숫양 — 앞모습, 아래 끝 = 발굽, 세워 카메라 쪽으로 돎)

RA_N = 8
RA_BASE = 312.0
_RR = random.Random(9890)
RA_DRIPS = [(_RR.uniform(92, 228), _RR.uniform(0, 1), _RR.uniform(3.0, 5.5)) for _ in range(9)]
RA_BLOBS = [(_RR.uniform(0, TAU), _RR.uniform(0.4, 1.0), _RR.uniform(6, 14)) for _ in range(22)]


def _horn(side, cx, cy, sc=1.0, n=60):
    """뿔 (앞모습 — 머리 위 뿌리에서 바깥 · 뒤 · 아래로 감겨 앞으로 끝나는 나선): 가운데 줄 점들"""
    pts = []
    for i in range(n + 1):
        t = i / n
        th = -2.25 + t * 4.5                      # 위 (뿌리) → 바깥 → 아래 → 안쪽 앞
        r = (44.0 - 26.0 * t) * sc
        x = cx + side * (r * math.cos(th) + 4.0 * t)
        y = cy + r * math.sin(th)
        pts.append((x, y))
    return pts


def _voronoi_crack(X, Y, seeds, width):
    """보로노이 판 사이 금 (1 = 금) — seeds = [(x, y)]"""
    d1 = np.full(X.shape, 1e9, np.float32)
    d2 = np.full(X.shape, 1e9, np.float32)
    for sx, sy in seeds:
        d = np.hypot(X - sx, Y - sy)
        d2 = np.where(d < d1, d1, np.minimum(d2, d))
        d1 = np.minimum(d1, d)
    return smooth(width, 0.6, d2 - d1)


_RV = random.Random(9910)
RA_SEEDS = [(40.0 + 30.0 * gx + _RV.uniform(-9, 9), 60.0 + 30.0 * gy + _RV.uniform(-9, 9)) for gx in range(9) for gy in range(9)]


def _ram_parts(pad, f, ph, clash, bob):
    """숫양 몸의 조각 (마스크) 과 자리: 몸 · 갈기 줄 · 다리 둘 · 머리 · 뿔 둘 · 눈 자리"""
    X, Y = pad.grid()
    cx = pad.w / 2
    bcy = 198.0 + (8.0 if clash else 0.0) + bob * 0.5
    ang = np.arctan2(Y - bcy, X - cx)
    rr = np.hypot((X - cx) / 104.0, (Y - bcy) / 72.0)
    lump = fbm(np.cos(ang) * 2.2 + 5.0, np.sin(ang) * 2.2 + 5.0 + ph * 0.0, seed=1001, octaves=3)
    body = smooth(1.0 + 0.12 * (lump - 0.5) * 2 + 0.03, 0.97 + 0.12 * (lump - 0.5) * 2, rr)
    legs = []
    for side in (-1, 1):
        lift = max(0.0, math.sin(ph * TAU + (0 if side < 0 else math.pi))) * 30.0 if (2 <= f <= 5) else 0.0
        hx = cx + side * (42.0 + (6.0 if clash else 0.0))
        top_y = bcy + 26.0
        foot_y = RA_BASE - 10.0 - lift
        knee_y = (top_y + foot_y) / 2 - lift * 0.15
        kx = hx + side * lift * 0.22
        leg = [(hx - 17, top_y), (hx + 17, top_y), (kx + 12, knee_y), (hx + 9, foot_y), (hx - 9, foot_y), (kx - 12, knee_y)]
        legs.append((pad.poly(leg), hx, foot_y, lift))
    hy0 = 82.0 + (18.0 if clash else 0.0) + bob
    head = [(cx - 30, hy0 + 8), (cx - 16, hy0 - 4), (cx + 16, hy0 - 4), (cx + 30, hy0 + 8), (cx + 33, hy0 + 40), (cx + 26, hy0 + 78), (cx + 19, hy0 + 108),
            (cx + 11, hy0 + 124), (cx, hy0 + 128), (cx - 11, hy0 + 124), (cx - 19, hy0 + 108), (cx - 26, hy0 + 78), (cx - 33, hy0 + 40)]
    hsc = 1.12 if clash else 1.0
    horns = []
    for side in (-1, 1):
        hpts = _horn(side, cx + side * 60.0, hy0 + 32.0 + (8.0 if clash else 0.0), hsc)
        horns.append((pad.poly(taper_poly(hpts, lambda t: (16.0 * (1 - t) ** 0.75 + 3.5) * hsc)), hpts))
    return dict(bcy=bcy, body=body, legs=legs, head=pad.poly(head), hy0=hy0, horns=horns, hsc=hsc)


def ram_frame(f, S=320):
    pad = Pad(S, S)
    X, Y = pad.grid()
    cx = S / 2
    gal = 2 <= f <= 5
    ph = (f - 2) / 4 if gal else 0.0
    bob = 4.0 * math.sin(ph * TAU) if gal else 0.0
    clash = f == 6
    rnd = random.Random(9900 + f)
    P = _ram_parts(pad, f, ph, clash, bob)
    st = Stack(pad)
    gextra = pad.zeros()
    crack = _voronoi_crack(X, Y, [(x, y + bob * 0.3) for x, y in RA_SEEDS], 4.2)
    noise = fbm(X / S * 5.0 + ph * 2.0, Y / S * 5.0 - ph * 3.0, seed=991, octaves=3)
    # ---- 어깨 위로 오르는 불티 · 열기 (몸 뒤)
    for i in range(14):
        x0 = cx + rnd.uniform(-100, 100)
        u = (i / 14.0 + ph) % 1.0 if gal else rnd.uniform(0, 1)
        y = P["bcy"] - 40 - u * 110
        st.add_core(pad.disc_field(x0 + math.sin(u * 7 + i) * 6, y, 2.6 * (1 - u) + 0.8, soft=0.8) * (1 - u))
    gextra = np.maximum(gextra, pad.radial(cx, P["bcy"] - 30, 150.0, 1.6, sy=0.8) * 0.35)
    # ---- 몸 (식은 껍질 · 쇳물 금)
    bm = P["body"]
    lit, dark, _ = bevel_light(clamp01(bm * 2 - 0.5), 22.0)
    st.over(bm, (0.86 + 0.12 * lit - 0.16 * dark) * (0.9 + 0.12 * noise), (crack * (0.75 + 0.25 * noise) + lit * 0.25 + rim_of(clamp01(bm * 2 - 0.5), 1.4) * 0.5) * bm)
    # ---- 앞다리 · 발굽 (디딘 발의 불빛)
    for lm, hx, foot_y, lift in P["legs"]:
        llit, ldark, _ = bevel_light(lm, 8.0)
        st.over(lm, 0.86 + 0.12 * llit - 0.14 * ldark, (crack * 0.7 + rim_of(lm, 0.9) * 0.45) * lm)
        hoof = pad.poly([(hx - 13, foot_y - 3), (hx + 13, foot_y - 3), (hx + 11, foot_y + 9), (hx - 11, foot_y + 9)])
        st.over(hoof, 0.95, hoof * 0.15 + pad.stroke([(hx - 12, foot_y - 3), (hx + 12, foot_y - 3)], 2.0) * 0.9)
        if gal and lift < 4:
            gextra = np.maximum(gextra, pad.radial(hx, RA_BASE - 2, 30.0, 1.5, sy=0.35) * 0.8)
            st.add_core(pad.radial(hx, RA_BASE - 4, 14.0, 1.4, sy=0.5) * 0.9)
    # ---- 머리 · 뿔 · 눈 · 콧구멍 · 입김
    hm = P["head"]
    hy0 = P["hy0"]
    hlit, hdark, _ = bevel_light(hm, 14.0)
    fine = _voronoi_crack(X, Y, [(x * 0.62 + cx * 0.38, y * 0.62 + hy0 * 0.5) for x, y in RA_SEEDS], 3.0)
    st.over(hm, (0.88 + 0.12 * hlit - 0.16 * hdark) * (0.92 + 0.1 * noise), (fine * 0.38 + hlit * 0.32 + rim_of(hm, 1.0) * 0.55) * hm)
    # 이마의 룬 (뵐룬드의 표 — 금빛 홈)
    st.add_core(glyph_mask(pad, 0, cx, hy0 + 22, 12.0, 16.0, 2.2) * hm * 0.9)
    for hm2, hpts in P["horns"]:
        lit2, dark2, hh2 = bevel_light(hm2, 9.0)
        st.over(hm2, 0.9 + 0.1 * lit2 - 0.12 * dark2, (rim_of(hm2, 1.1) * 0.85 + lit2 * 0.45) * hm2)
        # 뿔의 감긴 골 (뿔을 따라 나란히 — 바깥 · 안쪽 두 줄) · 끝으로 갈수록 밝은 테
        for off in (-0.42, 0.38):
            g = []
            for q in range(1, len(hpts) - 1):
                t = q / (len(hpts) - 1)
                (x0, y0), (x1, y1) = hpts[q - 1], hpts[q + 1]
                L = math.hypot(x1 - x0, y1 - y0) or 1
                nx, ny = -(y1 - y0) / L, (x1 - x0) / L
                w = (16.0 * (1 - t) ** 0.75 + 3.5) * P["hsc"]
                g.append((hpts[q][0] + nx * w * off, hpts[q][1] + ny * w * off))
            st.add_core(pad.stroke(g, 1.4, 0.6, a0=0.55, a1=0.2) * hm2)
            st.body = st.body * (1 - pad.stroke(g, 2.4, 1.0, a0=0.18, a1=0.05) * hm2)
        for q in range(8, 56, 8):
            t = q / 60.0
            (x0, y0), (x1, y1) = hpts[q - 1], hpts[q + 1]
            L = math.hypot(x1 - x0, y1 - y0) or 1
            nx, ny = -(y1 - y0) / L, (x1 - x0) / L
            w = (16.0 * (1 - t) ** 0.75 + 3.5) * P["hsc"] * 0.8
            xm, ym = hpts[q]
            st.add_core(pad.stroke([(xm - nx * w, ym - ny * w), (xm + nx * w, ym + ny * w)], 1.0, a0=0.3) * hm2)
    for side in (-1, 1):
        e0 = (cx + side * 25, hy0 + 44)
        e1 = (cx + side * 8, hy0 + 51)
        eye = lens(pad, e0 if side < 0 else e1, e1 if side < 0 else e0, 7.5, 1.0, power=0.6, skew=0.5)
        st.over(eye, 1.0, eye)
        gextra = np.maximum(gextra, pad.radial(cx + side * 16, hy0 + 48, 20.0, 1.4) * 0.85)
        st.add_core(star4(pad, cx + side * 16, hy0 + 48, 11.0, 0.9, rot=0.3, thin=0.14, diag=0.35))
        st.add_core(lens(pad, (cx + side * 4, hy0 + 112), (cx + side * 11, hy0 + 117), 3.2, 0.85, power=0.7, skew=0.5))
    # ---- 떨어지는 쇳물 방울 (몸 아래)
    for x, p0, r in RA_DRIPS:
        u = (p0 + ph) % 1.0 if gal else p0
        y = P["bcy"] + 48 + u * 70
        a = 1 - u
        st.add_core(pad.disc_field(x, y, r * (1 - 0.4 * u), soft=0.8) * a)
        st.body = np.maximum(st.body, pad.disc_field(x, y, r * 1.3, soft=1.0) * a * 0.8)
    body = st.body
    core = st.core
    # ---- 솟음 (0 · 1)
    if f <= 1:
        if f == 0:
            col = pad.ellipse(cx, RA_BASE - 100, 64, 110) * clamp01((Y - (RA_BASE - 230)) / 40.0)
            spl = clamp01((fbm(X / S * 6.0, Y / S * 6.0, seed=997, octaves=3) - 0.35) * 3.0)
            body = np.maximum(body * 0.0, col * (0.6 + 0.4 * spl))
            core = col * (0.55 + 0.45 * spl) * 0.9
        else:
            er = clamp01((fbm(X / S * 4.0, Y / S * 4.0, seed=998, octaves=3) - 0.2 + clamp01((Y - 120) / 160.0) * 0.3) * 3.5)
            body = body * er
            core = np.maximum(core * er, crack * er * body * 0.9)
        for ang, rr, br in RA_BLOBS[:14]:
            x = cx + math.cos(ang) * 120 * rr
            y = RA_BASE - 160 - math.sin(ang) * 110 * rr - (40 if f == 1 else 0)
            core = np.maximum(core, pad.disc_field(x, y, br * 0.5, soft=1.0) * 0.95)
            body = np.maximum(body, pad.disc_field(x, y, br * 0.85, soft=1.5) * 0.85)
        gextra = np.maximum(gextra, pad.radial(cx, RA_BASE - 20, 120.0, 1.6, sy=0.3) * 0.7)
    # ---- 맞부딪침 (6)
    if clash:
        sx, sy = cx, hy0 + 28
        core = np.maximum(core, star4(pad, sx, sy - 6, 76.0, 1.0, rot=0.1, thin=0.1, diag=0.5))
        gextra = np.maximum(gextra, pad.radial(sx, sy, 96.0, 1.3) * 0.85)
        for i in range(12):
            a = rnd.uniform(-math.pi * 1.05, 0.05)
            L = rnd.uniform(44, 96)
            core = np.maximum(core, streak(pad, (sx + math.cos(a) * 14, sy + math.sin(a) * 14), (sx + math.cos(a) * L, sy + math.sin(a) * L), 3.2, a=0.95))
    # ---- 터짐 (7)
    if f == 7:
        er = clamp01((fbm(X / S * 5.0, Y / S * 5.0, seed=999, octaves=3) - 0.45) * 4.0)
        body = body * er * 0.75
        core = core * er * 0.6
        for ang, rr, br in RA_BLOBS:
            x = cx + math.cos(ang) * 140 * rr
            y = 190 - math.sin(ang) * 130 * rr
            body = np.maximum(body, pad.disc_field(x, y, br, soft=1.5) * 0.85)
            core = np.maximum(core, pad.disc_field(x, y, br * 0.6, soft=1.0) * 0.95)
        gextra = np.maximum(gextra, pad.radial(cx, 190, 150.0, 1.2) * 0.7)
    core1 = pad.down(core)
    glow = obj_glow(pad, body, core1, halo=((1.5, 0.45), (4.5, 0.3), (10.0, 0.18)), body_k=0.97, core_bloom=((2.0, 0.3), (5.0, 0.18)))
    glow = screen(glow, pad.down(gextra) * 0.45)
    core1 = screen(core1, gblur(core1, 2.0) * 0.4, pad.down(gextra) * 0.35)
    wb = win_border(glow.shape, 4.0)
    return clamp01(glow * wb), clamp01(core1 * wb)


sprite("rfx_jram", "lookat_y", 320, 320, RA_N, 4, "ram", zoom=3,
       desc="쇳물 숫양 (룬 스미스 쇳물 숫양 — 앞모습, 아래 끝 = 발굽): 0 ~ 1 땅에서 솟은 쇳물 기둥이 숫양 모양으로 깎이며 이룸 (방울) → 2 ~ 5 달려옴 (이어 돌림 — 앞발을 "
       "번갈아 들고 머리가 끄덕임 · 양옆의 큰 나선 뿔 · 흰 열 눈 · 쇳물 핏줄 · 위로 솟는 불 갈기 · 떨어지는 쇳물 방울 · 디딘 발의 불빛) → 6 머리를 숙여 뿔로 받아 버팀 (뿔 "
       "앞의 큰 불티 별) → 7 쇳물 방울로 터져 흩어짐")(ram_frame)


def main():
    args = sys.argv[1:]
    folder = None
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
    only = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    for name, sp in SPECS.items():
        if only and name not in only:
            continue
        glows, cores = V2C.render(sp)
        img = to_sheet(list(glows) + list(cores), sp.cols, sp.cw, sp.ch)
        assert img.size[0] <= 4096 and img.size[1] <= 4096, (sp.name, img.size)
        save(sp.name, img, sp.cw, sp.ch, sp.facing)
        if folder:
            print("  preview:", V2C.preview(sp, glows, cores, folder))


if __name__ == "__main__":
    main()
