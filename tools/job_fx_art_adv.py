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

0.51.0 전사 2차 (뵈드바르(워로드) · 울프헤딘(라이칸)) — 빛 칸 = 빛나는 몸 (넓은 무리), 심지 칸 = 눈 · 이빨 · 발톱 · 털끝 · 테:
- rfx_jbear   rotate_xyz       256×256 ×8 (4 열)  곰의 혼 (앞모습 — 일어선 곰의 머리 · 어깨 · 양옆으로 든 앞발): 0 ~ 2 기운이 소용돌이쳐 모여 곰이 됨 (턱이 열리기 시작) →
                                                   3 ~ 6 포효 (이어 돌림 — 턱이 열렸다 닫혔다 · 털끝이 일렁임) → 7 위로 흩어짐. 머리 가운데 (128, 104) · 성난 눈 · 송곳니 · 귀 · 앞발 발톱
- rfx_jpaw    rotate_xyz       256×256 ×4 (4 열)  곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중). 발바닥 가운데 = 칸 가운데
- rfx_jclaw4  rotate_xyz       192×192 ×6 (6 열)  늑대 발톱 네 줄: 0 ~ 1 네 줄이 오른쪽 위에서 왼쪽 아래로 찢어짐 (가운데 굵고 양 끝 뾰족 · 조금 엇갈림) · 2 번쩍 →
                                                   3 ~ 5 흐려지며 튀는 불티 · 떨어지는 방울. 칸 가운데 = 맞은 자리
- rfx_jmoon   rotate_xyz       256×256 ×8 (4 열)  달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 둥근 빛무리) → 1 ~ 6 그림자가 오른쪽에서 달을 삼킴 (가려진 곳은 비움) →
                                                   7 남은 테 (코로나 — 게임은 붉게). 원판 반지름 79 (= 반 크기 × 0.62)

0.51.0 마검사 2차 (뵐룬드(룬 나이트) · 투르스(헥스 블레이드)) — 빛 칸 = 글자의 넓은 빛 (룬 나이트 보라 · 헥스 블레이드 먹빛), 심지 칸 = 날카로운 획 (금빛 · 호박빛):
- rfx_jglyph   rotate_xyz       192×192 ×18 (6 열) 공중에 새긴 룬 글자 셋 (지어낸 각진 글자 GLYPHS 2 · 7 · 4 — 종류마다 6장): 0 획이 반쯤 그어짐 (긋는 끝 불티) · 1 다 그어지며 번쩍 →
                                                   2 ~ 3 빛남 (둘레 옅은 고리 · 반짝임이 바뀜 — 이어 돌림) → 4 번쩍 (네 뿔 별) → 5 획이 조각나 바깥으로 흩어짐. 글자 가운데 = 칸 가운데
- rfx_jrunes24 rotate_xyz       320×320 ×8 (4 열)  하늘의 룬 스물넷: 띠 (반지름 122 · 위 = 바깥) 의 글자가 넷씩 켜짐 (0 ~ 5 — 새로 켜진 넷이 번쩍) → 6 모두 켜져 띠 안의 여덟 갈래 무늬 · 테가
                                                   이어짐 (이어 돌림 6 ↔ 7) · 7 더 밝게 · 빛살
- rfx_jthorn   rotate_xyz       128×128 ×6 (6 열)  가시 룬 ᚦ (세로 줄기 · 오른쪽 가시 · 줄기의 작은 가시): 0 박힘 (번쩍 · 금) · 1 자리 잡음 → 2 ~ 3 맥동 (불씨 — 이어 돌림) → 4 금이 감 ·
                                                   5 조각나 흩어짐. 글자 가운데 = 칸 가운데
- rfx_jthurs   direction_z      320×320 ×8 (4 열)  땅의 거대한 ᚦ (땅에 누움 — 그림 위 = 바라본 쪽): 줄기 (x 138.7 · y 288 → 32 = 칸의 0.8) · 가시 (줄기 0.3 ~ 0.7 · 오른쪽 85점 = 줄기의 1/3):
                                                   0 줄기가 갈라진 홈으로 새겨짐 · 1 위 가시 획 · 2 아래 가시 획 → 3 ~ 4 맥동 (이어 돌림) → 5 줄기를 따라 가른 빛 · 6 금이 벌어지며 터짐 · 7 흐려짐

0.51.0 매지션 2차 (오드레리르(아크메이지) · 나글파르(네크로맨서)) — 빛 칸 = 넓은 빛 (아크메이지 밤하늘 자주 · 네크로맨서 짙은 녹색 혼불), 심지 칸 = 금빛 별 심지 · 뼈 ·
눈구멍의 녹색 불 (네크로맨서는 '빛' 칸에 뼈 몸을 거의 불투명하게):
- rfx_jstarnode  rotate_xyz       192×192 ×8 (4 열)  별자리의 별: 0 번쩍 · 1 피어남 → 2 ~ 5 반짝임 (네 갈래 긴 빛살 · 짧은 대각 빛살 — 이어 돌림) → 6 크게 번쩍 · 7 별빛 조각으로 흩어짐
- rfx_jfallstar  lookat_direction 256×96  ×4 (4 열)  별똥 (오른쪽 = 나아가는 쪽 · 머리 x 0.88 W): 빛나는 별 머리 · 길게 가늘어지는 꼬리 · 꼬리를 따라 흐르는 반짝이 가루 (이어 돌림)
- rfx_jbonespear lookat_direction 256×64  ×4 (4 열)  뼈 창 (창끝 x 0.97 W): 이빨 뼈 날 · 등뼈 마디 자루 · 뒤로 휜 갈비 가시 · 꼬리 혹 · 녹색 혼불 · 끌리는 연기 (이어 돌림)
- rfx_jbonewall  lookat_y         256×256 ×8 (4 열)  뼈 방벽 (아래 끝 = 땅): 0 ~ 2 휜 뼈 가시 일곱이 땅을 뚫고 솟음 (흙 튐) → 3 ~ 5 서 있음 (혼불이 타고 오름 — 이어 돌림) →
                                                     6 금이 벌어짐 · 7 조각으로 흩어짐
- rfx_jskull     rotate_xyz       128×128 ×6 (6 열)  망자의 해골: 0 연기가 모여 해골 · 1 ~ 3 눈구멍 혼불 맥동 (이어 돌림) · 4 턱이 벌어지며 비명 · 5 연기로 흩어짐
- rfx_jrift      direction_z      128×320 ×6 (6 열)  갈라진 땅 (그림 위 = 금이 뻗는 쪽): 0 가는 금 · 1 ~ 2 벌어지며 돌조각 → 3 ~ 4 틈의 녹색 빛 맥동 (불티 — 이어 돌림) →
                                                     5 닫히며 흐려짐

0.51.0 궁수 2차 (에길(스나이퍼) · 미르크비드(섀도 헌터)) — 스나이퍼: 빛 칸 = 연두 무리 · 심지 칸 = 흰 금빛 (JobTraits9.SNIPE) / 섀도 헌터: 빛 칸 = 먹빛 몸 (JobTraits9.DUSK
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
from job_fx_art_v2b import arc_pts, cut_polyline, fbm, finish, gauss, lens, polar, star4, streak, win_border  # noqa: E402
import job_fx_art_v2c_marks as V2C  # noqa: E402 — 확인용 그림 (preview) 을 같은 꼴로

BLOOD = ((0.62, 0.02, 0.08), (0.95, 0.18, 0.2))
COLORS.setdefault("bloodknight", BLOOD)
# 확인용 색 (빛 · 심지 — 게임이 입히는 색과 같게): 곰 = 갈색 몸 · 주황 눈 / 발톱 = 붉은 눈빛 · 달빛 흰빛 / 달 = 달빛 은회 · 흰빛
COLORS.setdefault("warlord", ((0.66, 0.36, 0.14), (1.0, 0.7, 0.33)))
COLORS.setdefault("lycan", ((0.9, 0.14, 0.18), (0.9, 0.92, 0.97)))
COLORS.setdefault("moon", ((0.76, 0.8, 0.92), (1.0, 1.0, 1.0)))
# 0.51.0 마검사 2차: 룬 나이트 = 보라 · 금빛 / 헥스 블레이드 = 먹빛 겉 · 호박빛 심지 (JobFx.HEX_OUTER · HEX_INNER)
COLORS.setdefault("runeknight", ((0.62, 0.34, 1.0), (1.0, 0.8, 0.32)))
COLORS.setdefault("hexblade", ((0.24, 0.07, 0.16), (1.0, 0.7, 0.26)))


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

# ------------------------------------------------------------ rfx_jclaw4 (늑대 발톱 네 줄 — 칸 가운데 = 맞은 자리)

CL_N = 6
CL_LEN = [0.55, 1.0, 1.0, 1.0, 1.0, 1.0]  # 그어진 몫 (오른쪽 위 → 왼쪽 아래)
CL_A = [1.0, 1.0, 0.95, 0.72, 0.44, 0.2]
CL_W = [0.85, 1.08, 1.0, 0.88, 0.74, 0.6]
CL_FLASH = [0.55, 1.0, 0.45, 0.12, 0.0, 0.0]
CL_FLY = [0.0, 0.0, 0.25, 0.55, 0.8, 1.0]
_RC = random.Random(4404)
CL_SPARKS = [(_RC.uniform(0.15, 0.85), _RC.choice((-1, 1)), _RC.uniform(0.6, 1.0), _RC.uniform(-0.4, 0.4)) for _ in range(10)]


def claw4_frame(f):
    cw = ch = 192
    pad = Pad(cw, ch)
    a = CL_A[f]
    p0, p1 = (148.0, 30.0), (42.0, 160.0)
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    ux, uy = (p1[0] - p0[0]) / L, (p1[1] - p0[1]) / L
    nx, ny = -uy, ux
    body = pad.zeros()
    core = pad.zeros()
    mids = []
    for o in (-1.5, -0.5, 0.5, 1.5):
        sp = 20.0 * o
        lenk = 1.0 - 0.14 * abs(o)
        start = (1 - lenk) * L * 0.5 + 7.0 * o
        a0 = (p0[0] + nx * sp + ux * start, p0[1] + ny * sp + uy * start)
        a1 = (a0[0] + ux * L * lenk, a0[1] + uy * L * lenk)
        bow = 9.0 + 2.0 * o
        mid = ((a0[0] + a1[0]) / 2 + nx * bow, (a0[1] + a1[1]) / 2 + ny * bow)
        pts = cut_polyline(bez(a0, mid, a1, 30), CL_LEN[f])
        if len(pts) < 3:
            continue
        mids.append(pts[len(pts) // 2])
        W = (11.5 - 1.6 * abs(o)) * CL_W[f]
        g = pad.poly(taper_poly(pts, lambda t, W=W: W * 0.5 * max(0.0, math.sin(math.pi * min(1.0, max(0.0, t)))) ** 0.6 + 0.25))
        c = pad.poly(taper_poly(pts, lambda t, W=W: W * 0.16 * max(0.0, math.sin(math.pi * min(1.0, max(0.0, t)))) ** 0.8 + 0.15))
        body = np.maximum(body, g)
        core = np.maximum(core, c)
        # 찢긴 가장자리: 줄 바깥쪽에 짧은 가시 몇 (같은 줄을 따라)
        for k in range(3):
            t = 0.28 + 0.22 * k
            if t > CL_LEN[f]:
                break
            q = bez(a0, mid, a1, 30)[int(t * 30)]
            tip = (q[0] + nx * (W * 0.9) + ux * 4, q[1] + ny * (W * 0.9) + uy * 4)
            body = np.maximum(body, pad.poly([(q[0] - ux * 3, q[1] - uy * 3), tip, (q[0] + ux * 5, q[1] + uy * 5)]) * 0.8)
    # 불티 · 방울: 줄 가운데에서 바깥으로 튐 (2 ~ 5)
    fly = CL_FLY[f]
    if fly > 0:
        for t, side, sp_, jit in CL_SPARKS:
            q = (p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t)
            d = 26.0 + 46.0 * fly * sp_
            dx, dy = nx * side + ux * jit, ny * side + uy * jit
            n_ = math.hypot(dx, dy) or 1
            dx, dy = dx / n_, dy / n_
            hx, hy = q[0] + dx * d, q[1] + dy * d + 14.0 * fly * fly
            r = 2.2 * (1.0 - 0.6 * fly)
            core = np.maximum(core, pad.stroke([(hx - dx * 7, hy - dy * 7), (hx, hy)], 0.4, r * 1.6, a0=0.0, a1=0.9))
            body = np.maximum(body, pad.disc_field(hx, hy, r * 1.4, soft=1.0) * 0.7)
    b1 = pad.down(body)
    glow = screen(b1 * 0.95, gblur(b1, 2.0) * 0.6, gblur(b1, 6.0) * 0.32)
    if CL_FLASH[f] > 0:
        cx, cy = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        glow = screen(glow, pad.down(pad.radial(cx, cy, 70.0, 1.6)) * 0.55 * CL_FLASH[f])
    core1 = pad.down(core)
    wb = win_border(glow.shape, 5.0)
    return clamp01(glow * a) * wb, clamp01(core1 * a) * wb


sprite("rfx_jclaw4", "rotate_xyz", 192, 192, CL_N, 6, "lycan", zoom=1,
       desc="늑대 발톱 네 줄: 0 ~ 1 오른쪽 위에서 왼쪽 아래로 찢어짐 (엇갈린 네 줄 · 찢긴 가장자리) · 2 번쩍 → 3 ~ 5 흐려지며 불티 · 방울이 튐")(claw4_frame)


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


# ============================================================ 0.51.0 마검사 2차 — 룬 글자 · 룬 스물넷 · 가시 룬 ᚦ · 땅의 ᚦ

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


# ------------------------------------------------------------ rfx_jrunes24 (하늘의 룬 스물넷 — 칸 가운데 = 고리 가운데)

R24_N = 8
R24_R = 122.0


def runes24_frame(f, S=320):
    pad = Pad(S, S)
    c = S / 2
    body = pad.zeros()
    core = pad.zeros()
    lit_n = 24 if f >= 6 else 4 * (f + 1)
    flare = 1.0 if f < 7 else 1.25
    for i in range(24):
        a = -math.pi / 2 + i * TAU / 24
        x, y = c + math.cos(a) * R24_R, c + math.sin(a) * R24_R
        ang = a + math.pi / 2
        m = glyph_mask(pad, i % len(GLYPHS), x, y, 17.0, 25.0, 3.0, ang=ang)
        if i >= lit_n:
            body = np.maximum(body, m * 0.14)
            continue
        fresh = f < 6 and i >= lit_n - 4
        mi = glyph_mask(pad, i % len(GLYPHS), x, y, 17.0, 25.0, 1.25, ang=ang)
        body = np.maximum(body, m * (1.0 if fresh else 0.8) * flare)
        core = np.maximum(core, mi * (1.0 if fresh else 0.85))
        if fresh:
            core = np.maximum(core, star4(pad, x, y, 22.0, 0.85, rot=ang, thin=0.06, diag=0.35, core=False))
            body = np.maximum(body, pad.radial(x, y, 24.0, 1.4) * 0.7)
    # 띠의 테 (안 · 밖) — 켜진 몫만큼 (6 부터 다)
    span = TAU * min(1.0, lit_n / 24.0)
    for rr_, a_ in ((R24_R - 20.0, 0.55), (R24_R + 20.0, 0.45)):
        pts = arc_pts(c, c, rr_, -math.pi / 2 - TAU / 48, -math.pi / 2 - TAU / 48 + span, n=max(8, int(96 * span / TAU)))
        core = np.maximum(core, pad.stroke(pts, 1.2, a0=a_))
        body = np.maximum(body, pad.stroke(pts, 4.5, a0=a_ * 0.5, soft=1.5))
    if f >= 6:
        # 띠 안의 여덟 갈래 길잡이 무늬 (북쪽 마법 표 — 가지 끝마다 갈래 · 가로대, 첫 그림의 육각 별은 종교 표지라 뺌) · 가운데 작은 원 · 빛살 (7)
        for k in range(8):
            a = -math.pi / 2 + k * TAU / 8
            ux, uy = math.cos(a), math.sin(a)
            nx, ny = -uy, ux
            p0, p1 = (c + ux * 34, c + uy * 34), (c + ux * 94, c + uy * 94)
            arm = [p0, p1]
            core = np.maximum(core, pad.stroke(arm, 1.6, a0=0.85 * flare))
            body = np.maximum(body, pad.stroke(arm, 5.0, a0=0.45 * flare, soft=1.5))
            if k % 2 == 0:
                fork = [(p1[0] + nx * 9 - ux * 9, p1[1] + ny * 9 - uy * 9), p1, (p1[0] - nx * 9 - ux * 9, p1[1] - ny * 9 - uy * 9)]
            else:
                q = (c + ux * 78, c + uy * 78)
                fork = [(q[0] + nx * 8, q[1] + ny * 8), (q[0] - nx * 8, q[1] - ny * 8)]
            core = np.maximum(core, pad.stroke(fork, 1.4, a0=0.8 * flare))
            body = np.maximum(body, pad.stroke(fork, 4.5, a0=0.4 * flare, soft=1.5))
            mid = (c + ux * 58 + nx * 7, c + uy * 58 + ny * 7)
            dm = pad.poly([(mid[0] + ux * 4, mid[1] + uy * 4), (mid[0] + nx * 3, mid[1] + ny * 3), (mid[0] - ux * 4, mid[1] - uy * 4), (mid[0] - nx * 3, mid[1] - ny * 3)])
            core = np.maximum(core, dm * 0.75)
        core = np.maximum(core, pad.stroke(arc_pts(c, c, 30.0, 0, TAU, n=64), 1.4, a0=0.8))
        body = np.maximum(body, pad.radial(c, c, 50.0, 1.6) * 0.4 * flare)
        if f == 7:
            for k in range(12):
                a = k * TAU / 12 + 0.13
                core = np.maximum(core, pad.stroke([(c + math.cos(a) * 36, c + math.sin(a) * 36), (c + math.cos(a) * 88, c + math.sin(a) * 88)], 1.0, 2.6, a0=0.7, a1=0.0))
    return finish(pad, body, core, halo=((1.4, 0.5), (4.0, 0.4), (9.5, 0.26)), body_k=0.88, core_bloom=((1.4, 0.35), (3.6, 0.3)), win=5.0)


sprite("rfx_jrunes24", "rotate_xyz", 320, 320, R24_N, 4, "runeknight", zoom=6,
       desc="하늘의 룬 스물넷: 띠의 글자가 넷씩 켜짐 (0 ~ 5 — 새로 켜진 넷이 번쩍) → 6 모두 켜져 여덟 갈래 길잡이 무늬 · 테가 이어짐 (6 ↔ 7 이어 돌림) · 7 더 밝게 · 빛살")(runes24_frame)


# ------------------------------------------------------------ rfx_jthorn (가시 룬 ᚦ — 칸 가운데 = 글자 가운데)

TH_N = 6


def thorn_lines(cx, cy, h, sc=1.0):
    """ᚦ 의 획 (줄기 · 위 가시 획 · 아래 가시 획) — 높이 h, 가시는 줄기 0.28 ~ 0.72 에서 오른쪽 0.36 h"""
    top, bot = cy - h / 2 * sc, cy + h / 2 * sc
    sx = cx - h * 0.12 * sc
    up_y, lo_y = top + h * 0.28 * sc, top + h * 0.72 * sc
    tip = (sx + h * 0.36 * sc, cy)
    return [[(sx, top), (sx, bot)], [(sx, up_y), tip], [tip, (sx, lo_y)]]


def thorn_frame(f, S=128):
    pad = Pad(S, S)
    c = S / 2
    sc = [1.16, 1.0, 1.0, 1.0, 1.04, 1.0][f]
    h = 92.0
    lines = thorn_lines(c, c, h, sc)
    lw = 8.5 * sc
    rs = random.Random(5500 + f)
    body = pad.zeros()
    core = pad.zeros()
    if f <= 4:
        for ln in lines:
            body = np.maximum(body, pad.stroke(ln, lw, soft=1.0))
            core = np.maximum(core, pad.stroke(ln, lw * 0.34))
        # 줄기의 작은 가시 (왼쪽으로 · 아래를 향해 — 거꾸로 선 가시)
        sx = lines[0][0][0]
        for k in range(4):
            y = lines[0][0][1] + h * sc * (0.12 + 0.22 * k)
            tri = [(sx - lw * 0.4, y - 4.0), (sx - lw * 0.4 - 9.0 * sc, y + 6.0), (sx - lw * 0.4, y + 3.0)]
            body = np.maximum(body, pad.poly(tri))
            core = np.maximum(core, pad.poly(tri) * 0.55)
        if f == 0:
            # 박힘: 번쩍 · 둘레로 갈라지는 금
            body = np.maximum(body, pad.radial(c, c, 60.0, 1.4) * 0.7)
            core = np.maximum(core, star4(pad, c, c, 54.0, 0.9, rot=0.2, thin=0.05, diag=0.4, core=False))
            for i in range(7):
                a = rs.uniform(0, TAU)
                pts = [(c + math.cos(a) * 26, c + math.sin(a) * 26)]
                for s_ in range(4):
                    a += rs.uniform(-0.5, 0.5)
                    pts.append((pts[-1][0] + math.cos(a) * 8, pts[-1][1] + math.sin(a) * 8))
                core = np.maximum(core, pad.stroke(pts, 1.4, 0.4, a0=0.8, a1=0.1))
        elif f in (2, 3):
            # 맥동: 불씨 점 (장마다 다른 자리)
            k = 1.0 if f == 2 else 0.82
            body = body * k
            for i in range(5):
                x, y = c + rs.uniform(-30, 30), c + rs.uniform(-44, 44)
                core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.0, 2.0), soft=0.8) * 0.9)
                body = np.maximum(body, pad.disc_field(x, y, 4.0, soft=2.0) * 0.4)
        elif f == 4:
            # 금이 감: 획을 가로지르는 밝은 금
            for i in range(5):
                y = c + rs.uniform(-40, 40)
                x0 = lines[0][0][0] + rs.uniform(-10, 10)
                pts = [(x0 - 12, y - 3), (x0 - 2, y + 2), (x0 + 6, y - 2), (x0 + 16, y + 4)]
                core = np.maximum(core, pad.stroke(pts, 1.6, a0=1.0))
            body = np.maximum(body, pad.radial(c, c, 56.0, 1.6) * 0.55)
        g, cc = finish(pad, body, core, halo=((1.2, 0.5), (3.0, 0.42), (7.0, 0.3)), body_k=0.95, core_bloom=((1.2, 0.4), (3.0, 0.3)), win=4.0)
        return g, cc
    # 5 조각나 흩어짐
    for ln in lines:
        a_, b_ = ln[0], ln[1]
        L = math.hypot(b_[0] - a_[0], b_[1] - a_[1])
        n = max(2, int(L / 14))
        for i in range(n):
            p0 = (a_[0] + (b_[0] - a_[0]) * i / n, a_[1] + (b_[1] - a_[1]) * i / n)
            p1 = (a_[0] + (b_[0] - a_[0]) * (i + 0.65) / n, a_[1] + (b_[1] - a_[1]) * (i + 0.65) / n)
            mx, my = (p0[0] + p1[0]) / 2 - c, (p0[1] + p1[1]) / 2 - c
            d = math.hypot(mx, my) or 1.0
            push = 14.0 + rs.uniform(0, 10)
            q0 = (p0[0] + mx / d * push, p0[1] + my / d * push + 6)
            q1 = (p1[0] + mx / d * push, p1[1] + my / d * push + 6)
            body = np.maximum(body, pad.stroke([q0, q1], lw * 0.75, a0=0.75))
            core = np.maximum(core, pad.stroke([q0, q1], lw * 0.26, a0=0.85))
    g, cc = finish(pad, body, core, halo=((1.2, 0.45), (3.0, 0.36), (7.0, 0.24)), body_k=0.85, win=4.0)
    return g * 0.7, cc * 0.7


sprite("rfx_jthorn", "rotate_xyz", 128, 128, TH_N, 6, "hexblade", zoom=1,
       desc="가시 룬 ᚦ: 0 박힘 (번쩍 · 금) · 1 자리 잡음 → 2 ~ 3 맥동 (불씨 — 이어 돌림) → 4 금이 감 → 5 조각나 흩어짐")(thorn_frame)


# ------------------------------------------------------------ rfx_jthurs (땅의 거대한 ᚦ — 그림 위 = 바라본 쪽, 칸 가운데 = 룬 가운데)

TU_N = 8
TU_SX = 138.7
TU_Y0, TU_Y1 = 288.0, 32.0  # 줄기 아래 끝 (가까운 쪽) → 위 끝 (먼 쪽)
TU_THORN = 85.3


def _jag(p0, p1, rs, amp=3.0, n=14):
    """갈라진 홈의 들쭉날쭉한 줄 (p0 → p1)"""
    out = []
    nx, ny = -(p1[1] - p0[1]), p1[0] - p0[0]
    L = math.hypot(nx, ny) or 1.0
    nx, ny = nx / L, ny / L
    for i in range(n + 1):
        t = i / n
        o = 0.0 if i in (0, n) else rs.uniform(-amp, amp)
        out.append((p0[0] + (p1[0] - p0[0]) * t + nx * o, p0[1] + (p1[1] - p0[1]) * t + ny * o))
    return out


def thurs_frame(f, S=320):
    pad = Pad(S, S)
    c = S / 2
    L = TU_Y0 - TU_Y1
    a = (TU_SX, TU_Y0 - 0.3 * L)
    cc_ = (TU_SX, TU_Y0 - 0.7 * L)
    b = (TU_SX + TU_THORN, TU_Y0 - 0.5 * L)
    strokes = [((TU_SX, TU_Y0), (TU_SX, TU_Y1)), (cc_, b), (b, a)]
    shown = min(3, f + 1) if f <= 2 else 3
    body = pad.zeros()
    core = pad.zeros()
    rs = random.Random(3300)
    pulse = {3: 1.0, 4: 0.82}.get(f, 1.0)
    for i in range(shown):
        p0, p1 = strokes[i]
        pts = _jag(p0, p1, rs, 2.6, 18)
        fresh = f <= 2 and i == shown - 1
        w = 24.0 if i == 0 else 20.0  # 첫 그림: 13 · 3.2 점은 11칸 룬에서 0.45 · 0.1칸 — 땅에서 가늘어 굵게
        body = np.maximum(body, pad.stroke(pts, w, soft=3.0) * (1.0 if fresh else 0.85 * pulse))
        body = np.maximum(body, pad.stroke(pts, w * 1.8, soft=6.0) * 0.35 * (1.0 if fresh else pulse))
        core = np.maximum(core, pad.stroke(pts, 5.2, a0=1.0 if fresh or f >= 3 else 0.85))
        # 홈에서 갈라져 나간 잔금
        for k in range(5):
            t = rs.uniform(0.1, 0.9)
            q = (p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t)
            ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0]) + rs.choice((-1, 1)) * rs.uniform(0.7, 1.3)
            br = [q]
            for s_ in range(3):
                ang += rs.uniform(-0.4, 0.4)
                br.append((br[-1][0] + math.cos(ang) * 9, br[-1][1] + math.sin(ang) * 9))
            core = np.maximum(core, pad.stroke(br, 1.4, 0.4, a0=0.6, a1=0.05))
        if fresh:
            core = np.maximum(core, star4(pad, p1[0], p1[1], 26.0, 0.9, rot=0.3, thin=0.08, diag=0.35))
            body = np.maximum(body, pad.radial(p1[0], p1[1], 30.0, 1.4) * 0.6)
    if f in (3, 4):
        # 맥동: 홈을 따라 오르는 불씨 · 둘레 먹빛 기운
        for i in range(10):
            s_ = strokes[i % 3]
            t = rs.uniform(0, 1)
            x = s_[0][0] + (s_[1][0] - s_[0][0]) * t + rs.uniform(-6, 6)
            y = s_[0][1] + (s_[1][1] - s_[0][1]) * t + rs.uniform(-6, 6) - (8 if f == 4 else 0)
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.2, 2.4), soft=1.0) * 0.9)
        body = np.maximum(body, pad.radial(c, c, 150.0, 1.8) * 0.28 * pulse)
    if f == 5:
        # 가름: 줄기를 따라 곧은 빛줄 (홈보다 길게) · 양옆으로 튀는 불티
        cut = [(TU_SX, TU_Y0 + 14), (TU_SX, TU_Y1 - 14)]
        core = np.maximum(core, pad.stroke(cut, 5.0, a0=1.0))
        body = np.maximum(body, pad.stroke(cut, 26.0, a0=0.8, soft=6.0))
        for i in range(14):
            y = rs.uniform(TU_Y1, TU_Y0)
            side = rs.choice((-1, 1))
            core = np.maximum(core, streak(pad, (TU_SX + side * 6, y), (TU_SX + side * rs.uniform(20, 42), y + rs.uniform(-10, 10)), 1.6, 0.85))
    if f >= 6:
        # 터짐: 홈이 벌어지며 사방으로 금 · 큰 빛 (6) → 흐려짐 · 연기 가닥 (7)
        k = 1.0 if f == 6 else 0.45
        for i in range(16):
            ang = i * TAU / 16 + rs.uniform(-0.15, 0.15)
            pts = [(c + math.cos(ang) * 30, c + math.sin(ang) * 30)]
            for s_ in range(6):
                ang += rs.uniform(-0.35, 0.35)
                pts.append((pts[-1][0] + math.cos(ang) * 18, pts[-1][1] + math.sin(ang) * 18))
            core = np.maximum(core, pad.stroke(pts, 3.0 * k, 0.6, a0=0.9 * k, a1=0.1))
            body = np.maximum(body, pad.stroke(pts, 12.0 * k, 3.0, a0=0.5 * k, a1=0.0, soft=3.0))
        body = np.maximum(body, pad.radial(c, c, 150.0, 1.3) * 0.75 * k)
        core = core * (1.0 if f == 6 else 0.6)
    g, cc = finish(pad, body, core, halo=((1.6, 0.5), (4.5, 0.4), (10.0, 0.26)), body_k=0.9, core_bloom=((1.4, 0.4), (4.0, 0.3)), win=6.0)
    if f == 7:
        g, cc = g * 0.55, cc * 0.5
    return g, cc


sprite("rfx_jthurs", "direction_z", 320, 320, TU_N, 4, "hexblade", zoom=5,
       desc="땅의 거대한 ᚦ (위 = 바라본 쪽): 0 줄기 · 1 위 가시 획 · 2 아래 가시 획 (갈라진 홈) → 3 ~ 4 맥동 → 5 줄기를 따라 가른 빛 → 6 금이 벌어지며 터짐 · 7 흐려짐")(thurs_frame)


# ============================================================ 0.51.0 매지션 2차 (오드레리르(아크메이지) · 나글파르(네크로맨서))
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


# ============================================================ 0.51.0 궁수 2차 (에길(스나이퍼) · 미르크비드(섀도 헌터))
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
        ring_c = _arcs4(pad, c, R, 0.0, 0.2, 1.7, A)
        ring_b = _arcs4(pad, c, R, 0.0, 0.2, 5.0, A * 0.85, soft=1.6)
        ticks_c = pad.zeros()
        ticks_b = pad.zeros()
        for q in range(4):
            ux, uy = math.cos(q * math.pi / 2), math.sin(q * math.pi / 2)
            p0 = (c + ux * (R + 7.0), c + uy * (R + 7.0))
            p1 = (c + ux * (R - 11.0), c + uy * (R - 11.0))
            ticks_c = np.maximum(ticks_c, pad.stroke([p0, p1], 1.9, 1.0, a0=A, a1=A * 0.85))
            ticks_b = np.maximum(ticks_b, pad.stroke([p0, p1], 5.0, 3.0, a0=A * 0.8, a1=A * 0.5, soft=1.4))
        br_c = _brackets(pad, c, Rb, rot, 2.2, A * (0.95 if f else 0.8))
        br_b = gblur(br_c, 1.6 * SS) * 1.6
        core = np.maximum.reduce([core, ring_c, ticks_c, br_c])
        body = np.maximum.reduce([body, ring_b, ticks_b, br_b])
        if f < 4:
            inner = pad.stroke(arc_pts(c, c, rin, 0, TAU, n=40), 1.3, a0=A * 0.75)
            core = np.maximum(core, inner)
            body = np.maximum(body, pad.stroke(arc_pts(c, c, rin, 0, TAU, n=40), 3.6, a0=A * 0.45, soft=1.2))
            for q in range(4):
                ux, uy = math.cos(q * math.pi / 2), math.sin(q * math.pi / 2)
                core = np.maximum(core, pad.stroke([(c + ux * 4.5, c + uy * 4.5), (c + ux * (rin - 2.5), c + uy * (rin - 2.5))], 1.0, a0=A * 0.7))
            # 사과빛 점 (가운데 — 깜빡임) · 둘레 무리
            core = np.maximum(core, pad.disc_field(c, c, 2.2 + 2.4 * dot, soft=0.9) * (0.55 + 0.45 * dot))
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
        core = np.maximum(core, pad.stroke(lid, 2.2, a0=a, taper=0.5))
        up = bez((c - 17, c - 1), (c, c - 8), (c + 17, c - 1), 20)
        core = np.maximum(core, pad.stroke(up, 1.0, a0=a * 0.35, taper=0.7))
        for i in range(5):
            t = 0.18 + 0.64 * i / 4
            x = (1 - t) ** 2 * (c - 17) + 2 * (1 - t) * t * c + t * t * (c + 17)
            y = (1 - t) ** 2 * (c - 1) + 2 * (1 - t) * t * (c + 10) + t * t * (c - 1)
            ang = math.pi / 2 + (t - 0.5) * 1.2
            core = np.maximum(core, pad.stroke([(x, y + 1), (x + math.cos(ang) * 5.5, y + 1 + math.sin(ang) * 5.5)], 1.2, 0.4, a0=a * 0.85))
        body = np.maximum(body, pad.stroke(lid, 7.0, a0=a * 0.5, soft=2.0))
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
            body = np.maximum(body, pad.stroke(v, 6.5, soft=1.5))
            core = np.maximum(core, pad.stroke(v, 1.2, a0=0.7))
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
            body = np.maximum(body, pad.stroke(v, 7.0, soft=1.6))
            core = np.maximum(core, pad.stroke(v, 1.25, a0=0.8 * k + 0.1))
        polys, tips = _thorns(c, CU_R, rot)
        th_m = pad.polys(polys)
        body = np.maximum(body, th_m)
        core = np.maximum(core, rim_of(th_m, 0.8) * 0.75 * k)
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
                body = np.maximum(body, pad.stroke(pts, 6.0, soft=1.4) * 0.85)
                core = np.maximum(core, pad.stroke(pts, 1.1, a0=0.9))
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
        body = np.maximum(body, gblur(wood, 3.0 * SS) * 0.45)
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
    g, cc = finish(pad, body, core, halo=((1.2, 0.35), (3.0, 0.22)), body_k=0.98, core_bloom=((1.0, 0.15),), win=3.0)
    return g, cc


sprite("rfx_jthicket", "lookat_y", 256, 256, TK_N, 4, "shadowhunter", zoom=4,
       desc="어둠숲 가시덤불 (아래 끝 = 땅): 0 ~ 2 S 꼴로 휜 가시 줄기 일곱 (곁가지 · 갈고리 끝 · 가시) 이 땅을 뚫고 자람 (흙 튐) → 3 ~ 5 서 있음 (가지가 일렁임 · 은빛 티끌 · "
       "덤불 속 눈 셋 — 4 에서 깜빡 — 이어 돌림) → 6 은빛 금이 가며 부서짐 · 7 조각 · 연기로 흩어짐")(thicket_frame)


# ============================================================ 실행

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
