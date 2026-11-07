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

0.51.0 전사 2차 (워로드 · 재설계안 2판 글래디에이터 — 라이칸 의 늑대 발톱 rfx_jclaw4 는 뺌, 달 rfx_jmoon 은 블러드 나이트의 블러드 문이 씀) —
빛 칸 = 빛나는 몸 (넓은 무리), 심지 칸 = 눈 · 이빨 · 발톱 · 털끝 · 테:
- rfx_jbear   rotate_xyz       256×256 ×8 (4 열)  곰의 혼 (앞모습 — 일어선 곰의 머리 · 어깨 · 양옆으로 든 앞발): 0 ~ 2 기운이 소용돌이쳐 모여 곰이 됨 (턱이 열리기 시작) →
                                                   3 ~ 6 포효 (이어 돌림 — 턱이 열렸다 닫혔다 · 털끝이 일렁임) → 7 위로 흩어짐. 머리 가운데 (128, 104) · 성난 눈 · 송곳니 · 귀 · 앞발 발톱
- rfx_jpaw    rotate_xyz       256×256 ×4 (4 열)  곰 앞발 (이어 돌림): 털 덮인 앞발 · 발바닥 볼록 · 아래로 휜 발톱 넷 · 위로 흐르는 바람 줄 (내려치는 중). 발바닥 가운데 = 칸 가운데
- rfx_jmoon   rotate_xyz       256×256 ×8 (4 열)  달: 0 보름달 (바다 · 구덩이 테 · 가장자리 어둡게 · 둥근 빛무리) → 1 ~ 6 그림자가 오른쪽에서 달을 삼킴 (가려진 곳은 비움) →
                                                   7 남은 테 (코로나 — 게임은 붉게). 원판 반지름 79 (= 반 크기 × 0.62)
- rfx_jspike  lookat_y         256×256 ×8 (4 열)  땅에서 솟는 돌창 무리 (워로드 — 빛 = 돌 몸 · 심지 = 틈의 녹은 주황): 0 ~ 2 솟음 · 3 ~ 5 서 있음 (맥동) · 6 금 · 7 무너짐

0.51.0 마검사 2차 (재설계안 2판 — 소드 콜러 는 입체 마력 검만 써서 그림이 없음 · 헥스 블레이드 의 가시 룬 rfx_jthorn · 땅의 ᚦ rfx_jthurs · 옛 궁극기의 룬
스물넷 rfx_jrunes24 · 2026-11-05 룬 스미스 의 대장간 rfx_jheat · rfx_jmolten · rfx_janvil · rfx_jram 은 뺌 — 아케인 블레이드 는 rfx_jwave · rfx_jglyph) — 글자: 빛 칸 =
넓은 빛 (보라) · 심지 칸 = 날카로운 획 (청록):
- rfx_jglyph   rotate_xyz       192×192 ×18 (6 열) 공중에 새긴 룬 글자 셋 (지어낸 각진 글자 GLYPHS 2 · 7 · 4 — 종류마다 6장): 0 획이 반쯤 그어짐 (긋는 끝 불티) · 1 다 그어지며 번쩍 →
                                                   2 ~ 3 빛남 (둘레 옅은 고리 · 반짝임이 바뀜 — 이어 돌림) → 4 번쩍 (네 뿔 별) → 5 획이 조각나 바깥으로 흩어짐. 글자 가운데 = 칸 가운데
- rfx_jtear    rotate_xyz       512×256 ×8 (4 열)  차원의 틈 (아케인 블레이드 디멘션 슬래시 — 2026-11-05 스펠 오버드라이브 대신, 빛 칸 = 테 · 금 · 속의 소용돌이 ·
                                                   별빛 · 빨려 드는 티끌, 심지 칸 = 검은 속): 0 가는 빛 금 · 번쩍 → 1 벌어짐 → 2 ~ 5 벌어져 일렁임 (이어 돌림 — 같은 꼴) →
                                                   6 닫힘 · 7 가로로 번쩍이며 닫힘 (조각). 틈 끝 x 0.065 · 0.935 W (반 크기 × 0.87) · 가운데 반 높이 46 점

0.51.0 매지션 2차 (아크메이지 · 네크로맨서 — 2026-11-06 재설계안 2판에서 1판의 뼈 방벽 rfx_jbonewall 은 뺌) — 빛 칸 = 넓은 빛 (아크메이지 밤하늘 자주 · 네크로맨서 짙은 녹색 혼불), 심지 칸 = 금빛 별 심지 · 뼈 ·
눈구멍의 녹색 불 (네크로맨서는 '빛' 칸에 뼈 몸을 거의 불투명하게):
- rfx_jstarnode  rotate_xyz       192×192 ×8 (4 열)  별자리의 별: 0 번쩍 · 1 피어남 → 2 ~ 5 반짝임 (네 갈래 긴 빛살 · 짧은 대각 빛살 — 이어 돌림) → 6 크게 번쩍 · 7 별빛 조각으로 흩어짐
- rfx_jfallstar  lookat_direction 256×96  ×4 (4 열)  별똥 (오른쪽 = 나아가는 쪽 · 머리 x 0.88 W): 빛나는 별 머리 · 길게 가늘어지는 꼬리 · 꼬리를 따라 흐르는 반짝이 가루 (이어 돌림)
- rfx_jbonespear lookat_direction 256×64  ×4 (4 열)  뼈 창 (창끝 x 0.97 W): 이빨 뼈 날 · 등뼈 마디 자루 · 뒤로 휜 갈비 가시 · 꼬리 혹 · 녹색 혼불 · 끌리는 연기 (이어 돌림)
- rfx_jskull     rotate_xyz       128×128 ×6 (6 열)  망자의 해골: 0 연기가 모여 해골 · 1 ~ 3 눈구멍 혼불 맥동 (이어 돌림) · 4 턱이 벌어지며 비명 · 5 연기로 흩어짐
- rfx_jrift      direction_z      128×320 ×6 (6 열)  갈라진 땅 (그림 위 = 금이 뻗는 쪽): 0 가는 금 · 1 ~ 2 벌어지며 돌조각 → 3 ~ 4 틈의 녹색 빛 맥동 (불티 — 이어 돌림) →
                                                     5 닫히며 흐려짐

0.51.0 매지션 2차 재설계안 2판 (2026-11-06 — 아크메이지 셀레스티얼 이글 · 스타 볼텍스, 네크로맨서 무덤):
- rfx_jeagle     lookat_direction 384×384 ×8 (4 열)  별빛 독수리 (머리 = 오른쪽 · 날개 = 위아래, 몸 가운데 (200, 192)): 0 ~ 5 날갯짓 (이어 돌림 — 1 = 내리친 끝 · 깃털 별빛 ·
                                                     2 ~ 4 접으며 올림) · 6 ~ 7 날개를 몸에 붙여 내리꽂음 (뒤로 끌리는 별빛 줄 · 7 머리 번쩍). 빛 = 성운 결 몸, 심지 = 금빛 테 · 깃 · 별
- rfx_jgalaxy    direction_z      384×384 ×8 (4 열)  별의 소용돌이 (팔 끝 = 반 크기 × 0.9): 0 번쩍 · 1 펼쳐짐 → 2 ~ 5 두 갈래 나선 팔 (이어 돌림 — 별 반짝임, 도는 것은
                                                     spinRate) → 6 감기며 조여 듦 → 7 한 점 번쩍 · 옅은 충격 고리
- rfx_jgrave     lookat_y         128×192 ×8 (4 열)  무덤 (아래 8 점 = 땅): 0 ~ 1 흙을 튀기며 아치 묘비가 솟음 → 2 ~ 5 서 있음 (꼭대기 녹색 혼불 · 새긴 해골의 눈 빛 —
                                                     이어 돌림) → 6 금 · 혼불이 사그라짐 → 7 돌무더기 · 먼지 · 오르는 혼. 빛 = 녹색 혼불, 심지 = 뼈색 돌

0.51.0 궁수 2차 (스나이퍼 · 2026-11-06 재설계안 2판에서 섀도 헌터 의 검은 화살 rfx_jblackarrow · 저주 문양 rfx_jcurse · 어둠숲 가시덤불 rfx_jthicket 은 뺌 —
배틀 아처 는 rfx_jfalcon) — 스나이퍼: 빛 칸 = 연두 무리 · 심지 칸 = 흰 금빛 (JobTraits9.SNIPE):
- rfx_jweak       rotate_xyz       128×128 ×6 (6 열)  약점 과녁: 0 조여 들며 나타남 · 1 ~ 3 맥동 (네 토막 테 · 틈으로 들어오는 눈금 · 대각 꺾쇠가 30 도씩 돎 · 가운데 사과빛 점 깜빡
                                                     — 이어 돌림) · 4 맞음 (가운데가 깨지며 흰 섬광 · 금 일곱) · 5 흐려짐
- rfx_jgreatarrow lookat_direction 320×96  ×4 (2 열)  거대한 셋째 화살 (촉 끝 x 0.975 W): 넓은 잎 촉 · 감아 도는 바람 띠 두 가닥 (촉 쪽 좁고 뒤로 넓어짐 — 뒤로 흐름) ·
                                                     흰 섬광 깃 · 살대를 타고 앞으로 흐르는 빛 · 공기를 가르는 원뿔 줄 (이어 돌림)
- rfx_jnet        rotate_xyz       256×256 ×8 (4 열)  그물 (스네어 샷 — 빛 = 밧줄 몸 · 심지 = 매듭 · 추): 0 묶인 다발 → 1 ~ 3 펼쳐짐 (팔각 테두리 · 사각 그물코 · 매듭 ·
                                                     가장자리 추 여덟) → 4 ~ 5 감김 (위로 모여 조여 든 자루 꼴 — 이어 돌림) → 6 끊어짐 · 7 흩어짐
- rfx_jfalcon     lookat_direction 384×384 ×6 (3 열)  폭풍 매 (팰컨 다이브 — 머리 = 오른쪽 · 날개 = 위아래, 빛 = 바람 결 몸 · 심지 = 번개 테 · 눈): 0 ~ 3 날갯짓
                                                     (이어 돌림 — 뾰족한 긴 날개) → 4 ~ 5 날개를 접고 내리꽂음 (뒤로 끌리는 바람 줄 · 5 머리 앞 번개)

0.51.0 큰 검기 (사용자 2026-11-05 "단타 애들이 너무 밋밋해 좀 더 화려하게 커다란 검기가 나오면서 때리면" — 직업 공통, JobFx.bigWave): 빛 칸 = 넓은 빛 (직업 겉빛) ·
심지 칸 = 흰 날 · 결 · 반짝임:
- rfx_jwave       rotate_xyz       384×384 ×8 (4 열)  초승달 검기 (위로 불룩 · 칸 가운데 = 초승달 가운데): 0 ~ 1 번쩍이며 펼쳐짐 → 2 ~ 5 날아감 (흰 날 · 뒤따르는 겹 호
                                                     둘 · 결 · 날 끝 반짝임) → 6 ~ 7 부서짐 (조각 · 칼날 파편)

0.51.0 창술사 2차 (2026-11-06 — 스톰 랜서 · 드라군): 빛 칸 = 넓은 빛 (옥빛 바람 · 금빛 용 · 진홍 불), 심지 칸 = 앞 테 · 뼈 · 뜨거운 속:
- rfx_jswirl      rotate_xyz       256×256 ×8 (4 열)  바람 소용돌이 (카메라를 봄 — 앞으로 눕는 바람 창이 뒤 3인칭 · 1인칭에서 보이지 않아 길 위에 세움): 0 번쩍 → 1 펼쳐짐
                                                     → 2 ~ 5 세 갈래 바람 팔 (이어 돌림) → 6 풀림 → 7 흩어짐
- rfx_jdragon     rotate_xyz       448×448 ×8 (4 열)  용의 혼 (앞뒤가 같은 꼴 — 날개를 편 용): 0 생겨남 · 1 다 생김 → 2 ~ 5 날갯짓 (이어 돌림) → 6 포효 (머리 위 불) →
                                                     7 내리꽂음 (날개를 위로 모아 V · 머리가 아래)
- rfx_jfire       rotate_xyz       256×256 ×8 (4 열)  불꽃 덩이 (위 = 솟는 쪽): 0 불붙음 → 1 ~ 3 부풀어 오름 (불의 혀) → 4 ~ 6 흩어짐 → 7 불티

0.51.0 사신 2차 (2026-11-06 — 소울 리퍼 · 엑시큐셔너): 빛 칸 = 넓은 빛 (영혼 하늘빛 · 망령 보라 · 해 질 녘 금빛), 심지 칸 = 테 · 날 끝 · 결:
- rfx_jlantern    rotate_xyz       192×192 ×8 (4 열)  영혼 등불 (위 = 매단 고리): 0 불이 붙음 → 1 ~ 4 흔들리며 타오름 (이어 돌림 — 눈 둘의 영혼 불꽃) → 5 번쩍 (빛살) →
                                                     6 ~ 7 꺼지며 연기
- rfx_jguillotine lookat_y         192×256 ×8 (4 열)  망령 단두대 날 (아래 = 비스듬한 날): 0 생겨남 · 1 다 생김 → 2 ~ 3 매달림 (이어 돌림 — 사슬 · 번득임) → 4 떨어짐 →
                                                     5 내리침 (섬광) → 6 ~ 7 연기로 흩어짐
- rfx_jxmark      direction_z      384×384 ×8 (4 열)  처형 표식 (땅에 누움 — 획 끝 = 반 크기 × 0.9): 0 ~ 2 붓 획 둘이 그어지며 X → 3 ~ 5 맥동 (이어 돌림) → 6 벰 →
                                                     7 타 들어감

2026-11-07 재설계안 3판 (직업 스킬 도감 표시 고침):
- rfx_jecho       lookat_direction 384×64  ×8 (4 열)  걸린 칼자국 (소드마스터 블레이드 에코): 0 그어짐 → 1 ~ 4 걸려 떨림 (이어 돌림) → 5 갈라져 번쩍 → 6 ~ 7 흩어짐
- rfx_jexcal      lookat_direction 512×64  ×8 (2 열)  빛의 대검 (소드마스터 엑스칼리버): 0 모여 듦 → 1 자라남 → 2 번쩍 → 3 ~ 5 빛남 (이어 돌림) → 6 휘두름 → 7 흩어짐
- rfx_jicecage    lookat_y         256×384 ×8 (4 열)  얼음 결정 감옥 (프로스트 레이더 샤터 · 핌불윈터 — 아래 = 땅): 0 솟음 → 1 번쩍 → 2 ~ 4 갇혀 있음 (이어 돌림) →
                                                     5 금 → 6 깨짐 → 7 조각이 흩어짐
"""
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402
from job_fx_art_v2 import _ndi, COLORS, TAU, Pad, Stack, bevel_light, bez, clamp01, gblur, obj_glow, rim_of, screen, smooth, taper_poly, to_sheet  # noqa: E402
from job_fx_art_v2 import GLYPHS, glyph_lines, glyph_mask  # noqa: E402
from job_fx_art_v2b import arc_pts, chunk_pts, cut_polyline, faceted, fbm, finish, gauss, lens, polar, star4, streak, win_border  # noqa: E402
import job_fx_art_v2c_marks as V2C  # noqa: E402 — 확인용 그림 (preview) 을 같은 꼴로

BLOOD = ((0.62, 0.02, 0.08), (0.95, 0.18, 0.2))
COLORS.setdefault("bloodknight", BLOOD)
# 확인용 색 (빛 · 심지 — 게임이 입히는 색과 같게): 곰 = 갈색 몸 · 주황 눈 / 달 = 달빛 은회 · 흰빛
COLORS.setdefault("warlord", ((0.66, 0.36, 0.14), (1.0, 0.7, 0.33)))
COLORS.setdefault("moon", ((0.76, 0.8, 0.92), (1.0, 1.0, 1.0)))
# 0.51.0 마검사 2차: 룬 글자 확인용 색 = 보라 · 금빛 (게임이 입히는 색은 직업 색)
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


# ============================================================ 0.51.0 궁수 2차 (스나이퍼 · 배틀 아처 — 재설계안 2판)
# 스나이퍼: 빛 칸 = 연두 무리 (JobKit.glow) · 심지 칸 = 흰 금빛 (JobTraits9.SNIPE — 과녁 선 · 촉 · 섬광 깃)

COLORS.setdefault("sniper", ((0.72, 1.0, 0.38), (1.0, 0.98, 0.84)))


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


# ============================================================ 0.51.0 암살자 2차 (재설계안 2판 2026-11-06 — 트릭스터 · 피에로)

# 확인용 색 (게임이 입히는 색과 같게 — JobFx.outer · inner): 트릭스터 = 검보라 겉 · 연분홍 흰빛 속 / 거울 조각 · 거울 = 청록 테 (TricksterSkills.ILLUSION) · 연분홍 면 /
# 들불 = 자홍 · 흰 금빛 (TricksterSkills.FIRE) / 피에로 서커스 = 빨강 · 금빛 · 분홍 (JobFx.CIRCUS — 색종이 · 풍선 · 불꽃놀이는 빛마다 다른 색을 입힘)
COLORS.setdefault("trickster", ((0.32, 0.07, 0.46), (0.98, 0.68, 0.91)))
COLORS.setdefault("illusion", ((0.3, 0.95, 0.9), (0.98, 0.68, 0.91)))


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


sprite("rfx_jribbon", "direction_z", 256, 256, RB_N, 4, "trickster", zoom=0, loop=True,
       desc="빛 리본 (땅과 나란히 누움 — 이어 돌림, 장마다 45°): 몸 둘레를 휘감아 도는 두 띠 (반지름 108 = 반 크기 × 0.85 · 끝이 가늘고 비틀려 폭이 바뀜 · 머리가 밝고 꼬리로 "
       "옅어짐) · 머리 반짝임 · 꼬리에서 떨어지는 반짝이")(ribbon_frame)


# ------------------------------------------------------------ rfx_jpane (거울 — 세워 면이 앞을 봄, 이어 돌림 · 칸 가운데 = 거울 가운데)

PN_N = 4
PN_W, PN_H = 128, 224


def _pane_mask(pad, inset=0.0):
    """아치 꼴 거울면 (위가 반원 · 아래가 네모 — 칸 가장자리에서 14 점 안) — inset 만큼 안쪽"""
    x0, x1 = 22 + inset, PN_W - 22 - inset
    r = (x1 - x0) / 2
    cx = PN_W / 2
    ytop = 16 + inset + r
    y1 = PN_H - 16 - inset
    pts = [(cx + math.cos(a) * r, ytop - math.sin(a) * r) for a in np.linspace(0, math.pi, 40)]
    pts += [(x0, y1), (x1, y1)]
    return pad.poly(pts, 1.0)


def pane_frame(f):
    pad = Pad(PN_W, PN_H)
    outer = _pane_mask(pad, -5.0)
    face = _pane_mask(pad, 4.0)
    frame = clamp01(outer - face)
    X, Y = pad.grid()
    # 거울면: 옅은 빛 (위가 조금 밝게) · 비스듬히 지나가는 빛줄기 (장마다 아래로)
    grad = clamp01(0.32 - 0.14 * (Y / PN_H))
    glow = face * grad
    p = -0.55 + 0.55 * f
    u = (X / PN_W) * 0.9 + (Y / PN_H) * 0.75 - 0.6 - p
    sweep = np.exp(-0.5 * (u / 0.07) ** 2) * face
    sweep2 = np.exp(-0.5 * ((u - 0.22) / 0.03) ** 2) * face * 0.6
    core = np.maximum(sweep * 0.9, sweep2)
    # 테: 굵은 빛 (빛 칸) · 안쪽 가는 밝은 테 (심지) · 위 장식 (작은 마름모) · 아래 받침 · 옆 둥근 장식
    glow = np.maximum(glow, frame)
    core = np.maximum(core, rim_of(face > 0.5, 1.4) * 0.95)
    cx = PN_W / 2
    for (x, y, r) in ((cx, 9.0, 7.0), (cx, PN_H - 9.0, 6.0), (16.0, PN_H * 0.55, 4.5), (PN_W - 16.0, PN_H * 0.55, 4.5)):
        d = pad.poly([(x, y - r * 1.4), (x + r, y), (x, y + r * 1.4), (x - r, y)], 1.0)
        glow = np.maximum(glow, d)
        core = np.maximum(core, pad.disc_field(x, y, r * 0.45, soft=0.6))
    # 테를 도는 반짝임 (장마다 자리가 바뀜)
    rs = random.Random(9500 + f)
    for k in range(3):
        a = rs.uniform(0, math.pi)
        x = cx + math.cos(a) * 46
        y = 16 + 46 - math.sin(a) * 46 if k else PN_H - 20
        core = np.maximum(core, star4(pad, x, y, rs.uniform(6, 10), 1.0, rot=0.3, thin=0.1, diag=0.3))
    return finish(pad, glow, core, halo=((1.2, 0.4), (3.0, 0.3), (7.0, 0.16)), body_k=0.95, core_bloom=((1.0, 0.3), (2.5, 0.2)), win=3.0)


sprite("rfx_jpane", "direction_z", PN_W, PN_H, PN_N, 4, "illusion", zoom=1, loop=True,
       desc="거울 (미러 팰리스 — 세워 면이 dir 쪽, 이어 돌림): 아치 꼴 거울 (위 반원 · 아래 네모) · 빛 칸 = 굵은 테 · 위 · 아래 · 옆 마름모 장식 · 옅은 거울면, 심지 칸 = 안쪽 가는 테 · "
       "장마다 비스듬히 아래로 지나가는 빛줄기 둘 · 테의 반짝임")(pane_frame)


# ------------------------------------------------------------ rfx_jhexa (육망성 — 땅에 누움, 칸 가운데 = 별 가운데 · 꼭짓점 = 반 크기 × 0.88)

HX_N = 6
HX_S = 256


def _hexa_lines(R, c):
    """두 세모의 여섯 변 [(p, q)] (꼭짓점 위부터)"""
    out = []
    for k in (0, 1):
        pts = [(c + math.cos(-math.pi / 2 + k * math.pi / 3 + i * TAU / 3) * R, c + math.sin(-math.pi / 2 + k * math.pi / 3 + i * TAU / 3) * R) for i in range(3)]
        for i in range(3):
            out.append((pts[i], pts[(i + 1) % 3]))
    return out


def hexa_frame(f):
    S = HX_S
    pad = Pad(S, S)
    c = S / 2
    R = 0.88 * S / 2
    glow = pad.zeros()
    core = pad.zeros()
    lines = _hexa_lines(R, c)
    verts = [(c + math.cos(-math.pi / 2 + i * math.pi / 3) * R, c + math.sin(-math.pi / 2 + i * math.pi / 3) * R) for i in range(6)]
    rs = random.Random(9600 + f)
    if f <= 4:
        frac = 0.5 if f == 0 else 1.0
        w = (5.0, 6.5, 5.5, 6.0, 10.0)[f]
        for (p, q) in lines:
            e = (p[0] + (q[0] - p[0]) * frac, p[1] + (q[1] - p[1]) * frac)
            glow = np.maximum(glow, pad.stroke([p, e], w, w, soft=1.0))
            core = np.maximum(core, pad.stroke([p, e], w * 0.32, w * 0.32, soft=0.4))
            if f == 0:
                core = np.maximum(core, star4(pad, e[0], e[1], 9, 1.0, thin=0.12, diag=0.4))
        # 둘레 고리 · 고리 위 룬 점 (장마다 돎)
        if f >= 1:
            ring = pad.stroke(arc_pts(c, c, R + 6, 0, TAU, n=96), 3.0, soft=0.6)
            glow = np.maximum(glow, ring * (0.75 if f != 4 else 1.0))
            core = np.maximum(core, pad.stroke(arc_pts(c, c, R + 6, 0, TAU, n=96), 1.0, soft=0.3) * 0.7)
            for i in range(12):
                a = i * TAU / 12 + f * 0.13
                x, y = c + math.cos(a) * (R + 16), c + math.sin(a) * (R + 16)
                glow = np.maximum(glow, pad.poly([(x, y - 4), (x + 3, y), (x, y + 4), (x - 3, y)], 0.8))
        bright = (0.8, 1.0, 0.75, 0.95, 1.0)[f]
        for (x, y) in verts:
            core = np.maximum(core, star4(pad, x, y, (6, 16, 9, 11, 22)[f], bright, rot=rs.uniform(0, 0.6), thin=0.11, diag=0.35))
        if f == 4:
            glow = np.maximum(glow, pad.radial(c, c, R * 0.9, 1.6) * 0.6)
            core = np.maximum(core, star4(pad, c, c, 40, 1.0, thin=0.08, diag=0.5))
        if f == 1:
            glow = np.maximum(glow, pad.radial(c, c, R * 0.5, 1.8) * 0.5)
    else:
        # 깨짐: 변이 토막나 바깥으로 밀리며 흐려짐 · 반짝이 티끌
        for (p, q) in lines:
            for t0 in (0.0, 0.34, 0.67):
                t1 = t0 + 0.26
                a = (p[0] + (q[0] - p[0]) * t0, p[1] + (q[1] - p[1]) * t0)
                b = (p[0] + (q[0] - p[0]) * t1, p[1] + (q[1] - p[1]) * t1)
                m = ((a[0] + b[0]) / 2 - c, (a[1] + b[1]) / 2 - c)
                k = 0.22
                a = (a[0] + m[0] * k, a[1] + m[1] * k)
                b = (b[0] + m[0] * k, b[1] + m[1] * k)
                glow = np.maximum(glow, pad.stroke([a, b], 4.0, soft=0.8) * 0.6)
                core = np.maximum(core, pad.stroke([a, b], 1.3, soft=0.3) * 0.6)
        for _ in range(14):
            a = rs.uniform(0, TAU)
            r = rs.uniform(20, R + 10)
            core = np.maximum(core, star4(pad, c + math.cos(a) * r, c + math.sin(a) * r, rs.uniform(3, 6), 0.7, diag=0.0))
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.32), (9.0, 0.18)), body_k=0.9, core_bloom=((1.2, 0.3), (3.0, 0.22)), win=4.0)


sprite("rfx_jhexa", "direction_z", HX_S, HX_S, HX_N, 6, "trickster", zoom=0,
       desc="육망성 (사우전드 팬텀 — 땅에 누움, 꼭짓점 반지름 = 반 크기 × 0.88): 0 두 세모의 여섯 변이 반쯤 그어짐 (긋는 끝 반짝임) → 1 다 그어지며 번쩍 (꼭짓점 별 · 둘레 고리 · "
       "고리 위 마름모 룬 열둘) → 2 ~ 3 빛남 (이어 돌림 — 룬이 돎) → 4 크게 번쩍 (굵은 변 · 가운데 별) → 5 변이 토막나 바깥으로 흩어짐")(hexa_frame)


# ------------------------------------------------------------ rfx_jmaw (들불 표식 — 카메라를 봄, 칸 가운데 = 표식 가운데)

MW_N = 8
MW_S = 256


def _flame_tongue(pad, base, ang, L, w, sway):
    """불꽃 혀 하나 (밑 base 에서 ang 쪽으로 L — 흔들려 휜 끝)"""
    bx, by = base
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    pts = []
    for i in range(13):
        t = i / 12
        off = math.sin(t * math.pi * 1.2) * sway * t
        pts.append((bx + ux * L * t + nx * off, by + uy * L * t + ny * off))
    return pad.stroke(pts, w, 0.6, a0=1.0, a1=0.5, soft=1.2)


def maw_frame(f):
    S = MW_S
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(9700 + f)
    Rr = 64.0
    lit = (0.35, 0.75, 1, 1, 1, 1, 1.15, 0.7)[f]
    if f <= 6:
        # 불꽃 고리: 혀 열둘이 바깥 · 위로 (장마다 흔들림 · 돎)
        n = 12
        for i in range(n):
            a = i * TAU / n + f * 0.11
            if f == 0 and i % 3:
                continue
            if f == 1 and i % 2:
                continue
            base = (c + math.cos(a) * Rr, c + math.sin(a) * Rr)
            ang = a - 0.5 * math.cos(a)  # 위쪽으로 조금 기욺
            L = (36 + 16 * math.sin(i * 1.7 + f * 1.3)) * lit
            glow = np.maximum(glow, _flame_tongue(pad, base, ang, L, 22 * lit, 9 * math.sin(f * 1.1 + i)))
            core = np.maximum(core, _flame_tongue(pad, base, ang, L * 0.55, 8 * lit, 4 * math.sin(f * 1.1 + i)) * 0.9)
            # 곁불 (작은 혀 — 큰 혀 사이)
            a2 = a + TAU / n / 2
            b2 = (c + math.cos(a2) * Rr, c + math.sin(a2) * Rr)
            glow = np.maximum(glow, _flame_tongue(pad, b2, a2 - 0.5 * math.cos(a2), L * 0.5, 12 * lit, 5 * math.cos(f * 1.3 + i)) * 0.85)
        if f >= 2:
            glow = np.maximum(glow, pad.radial(c, c, Rr * 1.05, 0.8) * 0.28)
        glow = np.maximum(glow, pad.stroke(arc_pts(c, c, Rr, 0, TAU, n=96), 8.0 * lit, soft=1.5))
        core = np.maximum(core, pad.stroke(arc_pts(c, c, Rr, 0, TAU, n=96), 2.4, soft=0.5) * min(1.0, lit))
        # 가운데: 불타는 눈 (렌즈 꼴 · 세로 동공 · 둘레 불꽃)
        if f >= 1:
            eye = lens(pad, (c - 40, c), (c + 40, c), 34 * min(1.0, lit), 1.0, power=0.7)
            glow = np.maximum(glow, eye * 0.85)
            core = np.maximum(core, rim_of(eye > 0.5, 2.0) * 0.95)
            pupil = lens(pad, (c, c - 16), (c, c + 16), 8, 1.0, power=0.6)
            core = np.maximum(core, pupil)
            for k in (-1, 1):
                core = np.maximum(core, pad.stroke([(c + k * 46, c), (c + k * 58, c - 6)], 3.0, 1.0, soft=0.5))
        if f == 6:
            glow = np.maximum(glow, pad.radial(c, c, 110, 1.4) * 0.8)
            core = np.maximum(core, star4(pad, c, c, 70, 1.0, thin=0.07, diag=0.45))
    else:
        # 터짐: 불길 조각이 사방으로 · 흐려지는 고리
        for i in range(16):
            a = i * TAU / 16 + rs.uniform(-0.15, 0.15)
            r0 = Rr * 1.25
            p = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            glow = np.maximum(glow, _flame_tongue(pad, p, a, 30, 12, 4) * 0.7)
            core = np.maximum(core, _flame_tongue(pad, p, a, 16, 4, 2) * 0.6)
        glow = np.maximum(glow, pad.stroke(arc_pts(c, c, Rr * 1.5, 0, TAU, n=96), 5.0, soft=2.0) * 0.5)
    for _ in range(10 if f >= 2 else 5):
        a = rs.uniform(0, TAU)
        r = rs.uniform(Rr * 0.8, Rr * 1.7)
        core = np.maximum(core, star4(pad, c + math.cos(a) * r, c + math.sin(a) * r, rs.uniform(2.5, 5), 0.8, diag=0.0))
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.36), (9.0, 0.2)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.25)), win=4.0)


COLORS.setdefault("wildfire", ((1.0, 0.3, 0.72), (1.0, 0.88, 0.62)))
sprite("rfx_jmaw", "rotate_xyz", MW_S, MW_S, MW_N, 4, "wildfire", zoom=0,
       desc="들불 표식 (와일드파이어 피스트 — 카메라를 봄): 0 ~ 1 불씨가 고리로 번지며 타오름 → 2 ~ 5 불꽃 혀 열둘이 바깥으로 흔들리며 도는 고리 · 가운데 불타는 눈 (렌즈 꼴 · "
       "세로 동공 — 이어 돌림) → 6 크게 번쩍 → 7 불길 조각이 사방으로 터짐. 고리 반지름 64 (= 반 크기 × 0.5)")(maw_frame)


# ------------------------------------------------------------ rfx_jconfetti (색종이 폭죽 — 카메라를 봄, 칸 가운데 = 터진 자리)

CF_N = 6
CF_S = 256
_R = random.Random(9800)
CF_PIECES = [(_R.uniform(0, TAU), _R.uniform(0.35, 1.0), _R.uniform(0, math.pi), _R.uniform(-4, 4), _R.uniform(2.2, 3.4), _R.uniform(4.5, 7.0)) for _ in range(46)]
CF_RIBBONS = [(_R.uniform(0, TAU), _R.uniform(0.6, 1.0), _R.uniform(0, TAU)) for _ in range(9)]


def confetti_frame(f):
    S = CF_S
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    # 퍼진 몫 (빨리 퍼졌다 느려짐) · 떨어짐 (뒤 장) · 밝기
    spread = (0.12, 0.45, 0.72, 0.88, 0.96, 1.0)[f]
    fall = (0, 0, 2, 8, 18, 30)[f]
    a_ = (1.0, 1.0, 1.0, 0.95, 0.75, 0.45)[f]
    if f == 0:
        glow = np.maximum(glow, pad.radial(c, c, 46, 1.5))
        core = np.maximum(core, star4(pad, c, c, 30, 1.0, thin=0.1, diag=0.45))
    for (ang, k, rot, spin, w, h) in CF_PIECES:
        r = 104 * k * spread
        x = c + math.cos(ang) * r
        y = c + math.sin(ang) * r + fall * k
        a = rot + spin * f * 0.5
        # 돌아가는 네모 (얇아졌다 넓어짐 — 종이가 뒤집힘)
        flip = abs(math.cos(a * 1.7))
        hw, hh = w * (0.35 + 0.65 * flip), h
        ca, sa = math.cos(a), math.sin(a)
        pts = [(x + ca * dx - sa * dy, y + sa * dx + ca * dy) for dx, dy in ((-hw, -hh / 2), (hw, -hh / 2), (hw, hh / 2), (-hw, hh / 2))]
        m = pad.poly(pts, 1.0)
        glow = np.maximum(glow, m * a_)
        core = np.maximum(core, m * a_ * (0.25 + 0.75 * flip * flip))
    for (ang, k, ph) in CF_RIBBONS:
        r1 = 100 * k * spread
        pts = []
        for i in range(18):
            t = i / 17
            rr = r1 * (0.25 + 0.75 * t)
            off = math.sin(t * 9 + ph + f) * 7
            px = c + math.cos(ang) * rr - math.sin(ang) * off
            py = c + math.sin(ang) * rr + math.cos(ang) * off + fall * k * t
            pts.append((px, py))
        glow = np.maximum(glow, pad.stroke(pts, 3.2, 2.0, a0=a_, a1=a_ * 0.8, soft=0.4))
        core = np.maximum(core, pad.stroke(pts, 1.0, 0.6, a0=a_ * 0.8, a1=a_ * 0.4, soft=0.2))
    return finish(pad, glow, core, halo=((1.2, 0.25), (3.5, 0.15)), body_k=1.0, core_bloom=((1.0, 0.2),), win=4.0)


COLORS.setdefault("confetti", ((0.98, 0.2, 0.28), (1.0, 0.82, 0.78)))
sprite("rfx_jconfetti", "rotate_xyz", CF_S, CF_S, CF_N, 6, "confetti", zoom=1,
       desc="색종이 폭죽 (피에로 — 카메라를 봄): 0 번쩍 · 가운데 뭉친 조각 → 1 ~ 3 네모 조각 마흔여섯 (돌며 얇아졌다 넓어짐 — 종이가 뒤집힘) · 곱슬 리본 아홉이 사방으로 튐 → "
       "4 ~ 5 흩날리며 떨어지고 흐려짐. 빛 칸 = 종이 몸 (거의 불투명), 심지 칸 = 뒤집혀 빛을 받는 면 · 리본 결 (게임은 색 셋을 겹쳐 그림 — JobFx.confetti)")(confetti_frame)


# ------------------------------------------------------------ rfx_jballoon (풍선 동물 · 감음 · 펑 — 카메라를 봄, 칸 가운데)

BL2_N = 8
BL2_S = 192


def _sausage(pad, p, q, w):
    return pad.stroke([p, q], w, w, soft=1.0)


def _balloon_parts(kind, tilt):
    """풍선 동물의 마디 [(p, q, 굵기)] (칸 가운데 기준) — kind 0 강아지 · 1 기린"""
    c = BL2_S / 2
    ct, st = math.cos(tilt), math.sin(tilt)

    def P(x, y):
        return (c + x * ct - y * st, c + x * st + y * ct)
    if kind == 0:
        parts = [((-38, 2), (22, 2), 26), ((22, 2), (34, -24), 22), ((34, -24), (52, -30), 18), ((30, -40), (24, -56), 13), ((40, -40), (46, -56), 13),
                 ((-30, 14), (-34, 44), 13), ((-14, 14), (-16, 44), 13), ((12, 14), (12, 44), 13), ((26, 12), (30, 42), 13), ((-38, 0), (-54, -20), 14)]
    else:
        parts = [((-26, 20), (20, 20), 24), ((14, 12), (30, -48), 15), ((30, -48), (46, -56), 17), ((26, -60), (22, -72), 10), ((36, -62), (40, -74), 10),
                 ((-20, 30), (-24, 60), 12), ((-6, 30), (-8, 60), 12), ((10, 30), (10, 60), 12), ((20, 28), (24, 58), 12), ((-26, 18), (-40, 4), 9)]
    return [(P(*a), P(*b), w) for a, b, w in parts]


def balloon_frame(f):
    S = BL2_S
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    if f <= 3:
        kind = 0 if f <= 1 else 1
        tilt = (-0.06, 0.06, -0.05, 0.05)[f]
        for (p, q, w) in _balloon_parts(kind, tilt):
            body = _sausage(pad, p, q, w)
            glow = np.maximum(glow, body)
            # 반짝임: 마디마다 위쪽 작은 흰 점 · 이음매 매듭
            mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
            core = np.maximum(core, pad.disc_field(mx - w * 0.12, my - w * 0.22, w * 0.16, soft=0.8))
            core = np.maximum(core, _sausage(pad, (mx - w * 0.25, my - w * 0.3), (mx + w * 0.1, my - w * 0.36), w * 0.1) * 0.7)
            core = np.maximum(core, pad.disc_field(q[0], q[1], 2.2, soft=0.4) * 0.8)
        # 테두리 (조금 어두운 가장자리 — 고무 두께)
        core = np.maximum(core, rim_of(glow > 0.5, 1.2) * 0.25)
    elif f <= 5:
        # 감음: 비틀린 풍선 고리 (마디 여덟 — 이음매에서 꼬임)
        R = 62.0
        n = 8
        rot = 0.0 if f == 4 else math.pi / n
        for i in range(n):
            a0 = rot + i * TAU / n + 0.08
            a1 = rot + (i + 1) * TAU / n - 0.08
            pts = arc_pts(c, c, R, a0, a1, n=10, ry=R * 0.62)
            glow = np.maximum(glow, pad.stroke(pts, 20, 20, soft=1.0, taper=0.15))
            m = pts[len(pts) // 2]
            core = np.maximum(core, pad.disc_field(m[0] - 2, m[1] - 6, 3.4, soft=0.8))
            j = pts[-1]
            core = np.maximum(core, pad.disc_field(j[0], j[1], 2.8, soft=0.5) * 0.9)
        core = np.maximum(core, rim_of(glow > 0.5, 1.2) * 0.25)
    else:
        # 펑: 터지는 고리 · 고무 조각
        rs = random.Random(9900 + f)
        R = 54.0 if f == 6 else 80.0
        a_ = 1.0 if f == 6 else 0.5
        glow = np.maximum(glow, pad.stroke(arc_pts(c, c, R, 0, TAU, n=96), 6.0 if f == 6 else 3.0, soft=1.5) * a_)
        if f == 6:
            glow = np.maximum(glow, pad.radial(c, c, 46, 1.4) * 0.8)
            core = np.maximum(core, star4(pad, c, c, 36, 1.0, thin=0.1, diag=0.5))
        for _ in range(12):
            a = rs.uniform(0, TAU)
            r = R * rs.uniform(0.8, 1.15)
            x, y = c + math.cos(a) * r, c + math.sin(a) * r + (0 if f == 6 else 10)
            sz = rs.uniform(5, 9)
            pts = [(x + math.cos(a + k * 2.1) * sz * (1 if k % 2 else 0.5), y + math.sin(a + k * 2.1) * sz * (1 if k % 2 else 0.5)) for k in range(3)]
            m = pad.poly(pts, a_)
            glow = np.maximum(glow, m)
            core = np.maximum(core, m * 0.4)
    return finish(pad, glow, core, halo=((1.2, 0.25), (3.0, 0.15)), body_k=1.0, core_bloom=((1.0, 0.25),), win=3.0)


COLORS.setdefault("balloon", ((1.0, 0.42, 0.72), (1.0, 0.88, 0.95)))
sprite("rfx_jballoon", "rotate_xyz", BL2_S, BL2_S, BL2_N, 8, "balloon", zoom=2,
       desc="풍선 (벌룬 퍼레이드 — 카메라를 봄): 0 ~ 1 풍선 강아지 (둥근 마디 — 몸 · 머리 · 주둥이 · 두 귀 · 네 다리 · 꼬리, 이어 돌림 — 흔들림) · 2 ~ 3 풍선 기린 (긴 목) · "
       "4 ~ 5 비틀린 풍선 고리 (마디 여덟 — 적을 칭칭 감음, 이어 돌림) · 6 펑 (번쩍 · 고리) · 7 고무 조각이 흩어짐. 빛 칸 = 고무 몸 (불투명), 심지 칸 = 반짝임 · 매듭")(balloon_frame)


# ------------------------------------------------------------ rfx_jfirework (불꽃놀이 — 카메라를 봄, 칸 가운데 = 터진 자리)

FW_N = 6
FW_S = 256
_R = random.Random(9950)
FW_RAYS = [(i * TAU / 36 + _R.uniform(-0.05, 0.05), _R.uniform(0.82, 1.0)) for i in range(36)]


def firework_frame(f):
    S = FW_S
    pad = Pad(S, S)
    c = S / 2
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(9960 + f)
    if f == 0:
        glow = np.maximum(glow, pad.radial(c, c, 40, 1.4))
        core = np.maximum(core, star4(pad, c, c, 34, 1.0, thin=0.09, diag=0.5))
    else:
        R = (0, 48, 80, 100, 106, 108)[f]
        droop = (0, 0, 2, 8, 20, 34)[f]
        a_ = (1, 1, 1, 0.95, 0.7, 0.4)[f]
        tail = (0, 0.55, 0.6, 0.55, 0.45, 0.3)[f]
        for (ang, k) in FW_RAYS:
            r = R * k
            hx, hy = c + math.cos(ang) * r, c + math.sin(ang) * r + droop * k
            tx, ty = c + math.cos(ang) * r * (1 - tail), c + math.sin(ang) * r * (1 - tail) + droop * k * 0.3
            if f >= 4:
                # 버들: 끝이 아래로 처짐 (휜 꼬리)
                mx, my = (hx + tx) / 2, (hy + ty) / 2 - droop * 0.2
                pts = [(tx, ty), (mx, my), (hx, hy)]
            else:
                pts = [(tx, ty), (hx, hy)]
            glow = np.maximum(glow, pad.stroke(pts, 1.2, 4.0, a0=0.0, a1=a_, soft=0.6))
            core = np.maximum(core, pad.disc_field(hx, hy, 2.6 if f < 4 else 1.8, soft=0.6) * a_)
        if f >= 3:
            for _ in range(18):
                a = rs.uniform(0, TAU)
                r = rs.uniform(30, R)
                core = np.maximum(core, star4(pad, c + math.cos(a) * r, c + math.sin(a) * r + droop * 0.6, rs.uniform(2.5, 5), a_, diag=0.0))
        if f == 1:
            glow = np.maximum(glow, pad.radial(c, c, 34, 1.6) * 0.6)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.32), (9.0, 0.2)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.25)), win=4.0)


COLORS.setdefault("firework", ((1.0, 0.78, 0.18), (1.0, 0.97, 0.75)))
sprite("rfx_jfirework", "rotate_xyz", FW_S, FW_S, FW_N, 6, "firework", zoom=0,
       desc="불꽃놀이 (그랜드 피날레 — 카메라를 봄): 0 번쩍 → 1 ~ 3 빛 구슬 서른여섯이 꼬리를 끌며 둥글게 퍼짐 (반짝이) → 4 ~ 5 버들처럼 아래로 처지며 흐려짐. "
       "빛 칸 = 꼬리, 심지 칸 = 구슬 머리 · 반짝이")(firework_frame)


# ------------------------------------------------------------ rfx_jspot (조명 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅, 이어 돌림)

SPT_N = 4
SPT_W, SPT_H = 128, 384


def spot_frame(f):
    pad = Pad(SPT_W, SPT_H)
    X, Y = pad.grid()
    cx = SPT_W / 2
    top, bot = 6.0, SPT_H - 26.0
    t = clamp01((Y - top) / (bot - top))
    half = 5 + 52 * t
    edge = clamp01((half - np.abs(X - cx)) / 6.0)
    cone = edge * (Y <= bot + 2) * (0.22 + 0.4 * t)
    glow = cone
    core = edge * (Y <= bot + 2) * np.exp(-0.5 * ((X - cx) / (6 + 10 * t)) ** 2) * (0.25 + 0.35 * t)
    # 바닥의 밝은 웅덩이 (납작한 타원)
    pool = pad.radial(cx, bot + 4, 58, 1.2, sy=0.18)
    glow = np.maximum(glow, pool * 0.85)
    core = np.maximum(core, pad.radial(cx, bot + 4, 34, 1.5, sy=0.18) * 0.8)
    # 떠도는 먼지 (장마다 자리 — 원뿔 안)
    rs = random.Random(9990 + f)
    for _ in range(16):
        y = rs.uniform(top + 30, bot - 10)
        tt = (y - top) / (bot - top)
        x = cx + rs.uniform(-1, 1) * (5 + 48 * tt) * 0.85
        core = np.maximum(core, star4(pad, x, y, rs.uniform(1.8, 3.2), rs.uniform(0.5, 0.9), diag=0.0))
    return finish(pad, glow, core, halo=((2.0, 0.3), (6.0, 0.2)), body_k=0.9, core_bloom=((1.5, 0.3),), win=2.0)


COLORS.setdefault("spot", ((1.0, 0.84, 0.32), (1.0, 0.97, 0.92)))
sprite("rfx_jspot", "lookat_y", SPT_W, SPT_H, SPT_N, 4, "spot", zoom=0, loop=True,
       desc="조명 (피에로 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅, 이어 돌림): 위에서 내리비추는 빛 원뿔 (아래로 넓고 밝아짐) · 가운데 밝은 기둥 · 바닥의 납작한 빛 웅덩이 · "
       "떠도는 먼지 (장마다 자리)")(spot_frame)


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

# ============================================================ 0.51.0 전사 2차 재설계안 2판 — 워로드 의 땅 (글래디에이터 의 결투 말뚝 rfx_jstake · 여덟 팔 rfx_jarms ·
# 결투장 rfx_jarena 는 2026-11-07 재설계안 3판에서 프로스트 레이더로 바뀌며 뺌)
# 워로드: 빛 칸 = 돌 몸 (짙은 흙빛 돌 — 거의 불투명) · 심지 칸 = 틈의 주황 (녹은 금)

COLORS.setdefault("warlord_rock", ((0.34, 0.25, 0.18), (1.0, 0.58, 0.16)))

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


# ============================================================ 0.51.0 큰 검기 (사용자 2026-11-05 "단타 애들이 너무 밋밋해 좀 더 화려하게 커다란 검기가 나오면서 때리면")
# 단타 직업 스킬이 칠 때 크게 뻗어 나가는 초승달 검기 (JobFx.wave — 날아가는 쪽 · 기울기 · 잔상 둘). 빛 칸 = 넓은 빛 (직업 겉빛), 심지 칸 = 흰 날 · 결 · 반짝임

COLORS.setdefault("wave", ((0.36, 0.62, 1.0), (0.94, 0.97, 1.0)))

# ------------------------------------------------------------ rfx_jwave (큰 검기 — 카메라를 봄, 초승달이 위로 불룩 · 칸 가운데 = 초승달 가운데)

WV_N = 8
WV = dict(cx=0.5, cy=0.66, rx=0.46, ry=0.36, a0=196.0, a1=344.0)
_WV_RS = random.Random(12100)
WV_WISPS = [(_WV_RS.uniform(0.06, 0.94), _WV_RS.uniform(0.35, 1.0), _WV_RS.uniform(0, TAU), _WV_RS.uniform(0.6, 1.0)) for _ in range(26)]
WV_SPARKS = [(_WV_RS.uniform(0.05, 0.95), _WV_RS.uniform(-0.1, 1.0), _WV_RS.uniform(0, 1)) for _ in range(30)]
WV_SHARDS = [(_WV_RS.uniform(0.04, 0.96), _WV_RS.uniform(0.6, 1.4), _WV_RS.uniform(-0.6, 0.6), _WV_RS.uniform(5, 11)) for _ in range(22)]


def _wv_P(S, u, inward=0.0):
    g = WV
    a = math.radians(g["a0"] + (g["a1"] - g["a0"]) * u)
    cx, cy, rx, ry = g["cx"] * S, g["cy"] * S, g["rx"] * S, g["ry"] * S
    x, y = cx + math.cos(a) * rx, cy + math.sin(a) * ry
    if inward:
        nx, ny = math.cos(a) / rx, math.sin(a) / ry
        L = math.hypot(nx, ny)
        x, y = x - nx / L * inward, y - ny / L * inward
    return x, y


def _wv_fields(pad, S):
    """(u — 호 위 자리 0 ~ 1, d — 바깥 날에서 안쪽 (타원 가운데 쪽) 으로의 거리 · 칸 점)"""
    X, Y = pad.grid()
    g = WV
    x = (X - g["cx"] * S) / (g["rx"] * S)
    y = (Y - g["cy"] * S) / (g["ry"] * S)
    rho = np.sqrt(x * x + y * y) + 1e-6
    gx = x / (g["rx"] * S * rho)
    gy = y / (g["ry"] * S * rho)
    d = (1 - rho) / np.sqrt(gx * gx + gy * gy)
    th = np.degrees(np.arctan2(y, x)) % 360.0
    u = (th - g["a0"]) / (g["a1"] - g["a0"])
    return u, d


def wave_frame(f, S=384):
    pad = Pad(S, S)
    u, d = _wv_fields(pad, S)
    on = (u > 0) & (u < 1)
    uc = np.clip(u, 0, 1)
    # 굵기: 가운데가 가장 굵고 양 끝이 날카롭게 모임 (조금 비대칭 — 오른쪽 끝이 길게)
    prof = (np.clip(np.sin(np.pi * uc), 0, None) ** 0.7) * (0.9 + 0.1 * uc) * on
    grow = [0.42, 1.0, 1.0, 1.0, 1.0, 1.0, 0.92, 0.75][f]
    fade = [1.0, 1.0, 1.0, 0.97, 0.95, 0.92, 0.72, 0.38][f]
    W0 = 56.0 * grow
    w = W0 * prof
    outer = clamp01((d + 0.3) * 1.2 + 0.5)
    band = outer * smooth(w + 2.0, w - 5.0, d) * (prof > 0.01)
    t = np.clip(d / np.maximum(w, 0.5), 0, 1)
    ph = (f - 2) / 4.0 if 2 <= f <= 5 else 0.0
    # ---- 심지: 바깥 흰 날 (가장 밝음) · 안으로 흐려지는 몸 (밝은 마디가 날을 따라 흐름) · 결 둘
    edge = outer * np.exp(-np.maximum(d, 0) / (1.4 + 4.0 * prof)) * band
    flow = 0.72 + 0.28 * np.cos(TAU * (uc * 2.5 - ph))
    body = band * (1 - smooth(0.2, 1.0, t)) ** 1.1 * 0.92 * flow
    strands = np.zeros_like(d)
    for tk, sk, wk in ((0.38, 0.55, 0.035), (0.62, 0.4, 0.03)):
        strands = np.maximum(strands, gauss(t - tk - 0.05 * np.sin(TAU * (uc * 2.0 + ph + tk)), wk) * sk)
    core = np.maximum.reduce([edge, body, strands * band * flow])
    # ---- 잔상 초승달 셋: 날 뒤 (안쪽) 에 나란히 · 점점 가늘고 짧고 옅게 (날아가며 끌리는 검기)
    inner = smooth(WV["ry"] * S * 0.78, WV["ry"] * S * 0.5, d)
    for k, (dk, span, a0) in enumerate(((1.05, 0.8, 0.62), (1.6, 0.62, 0.36))):
        if f == 0 and k > 0:
            continue
        lag = 0.04 * math.sin(TAU * (ph + k * 0.3)) if 2 <= f <= 5 else 0.0
        win = smooth(0.5 - span / 2 + lag, 0.5 - span / 2 + 0.16 + lag, uc) * smooth(0.5 + span / 2 + lag, 0.5 + span / 2 - 0.12 + lag, uc)
        dd = d - W0 * dk
        thin = (2.0 + 4.0 * win) * (1 - 0.22 * k)
        echo = np.exp(-(dd / thin) ** 2) * win * (prof > 0.01) * inner
        core = np.maximum(core, echo * a0 * 0.8 * flow)
        if k == 0:
            echo_g = np.exp(-(dd / (thin * 3.2)) ** 2) * win * a0 * inner
        else:
            echo_g = np.maximum(echo_g, np.exp(-(dd / (thin * 3.2)) ** 2) * win * a0 * inner)
    # ---- 빛: 넓은 띠 + 안쪽으로 끌리는 옅은 기운 (얼룩진 결)
    wg = (W0 * 1.4 + 12) * prof
    gband = smooth(-8.0, 1.0, d) * smooth(wg + 6.0, wg - 10.0, d) * (prof > 0.01)
    X, Y = pad.grid()
    nz = fbm(X / S * 6.0 + ph * 2.0, Y / S * 3.0 - ph * 3.0, seed=12150, octaves=3)
    wake = 0.5 * np.exp(-np.maximum(d - w, 0) / (60.0 * grow + 6)) * (d > w * 0.4) * prof ** 0.6 * (0.55 + 0.9 * clamp01(nz - 0.3)) * inner
    glow = np.maximum.reduce([gband, wake, echo_g])
    # ---- 날 위를 달리는 반짝임 · 바깥으로 튀는 짧은 불티
    if 1 <= f <= 6:
        for (u0, sp, ph0) in WV_SPARKS[:18]:
            uu = (u0 + 0.16 * (f - 1) * sp + ph0 * 0.1) % 1.0
            if uu < 0.08 or uu > 0.92:
                continue
            x, y = _wv_P(S, uu, inward=-1.0)
            tw = 0.5 + 0.5 * math.sin(TAU * (ph0 + f * 0.37))
            core = np.maximum(core, star4(pad, x, y, (6 + 8 * tw) * grow, (0.45 + 0.55 * tw) * fade, rot=ph0 * 3, thin=0.13, diag=0.0))
        rnd = random.Random(12200 + f)
        for i in range(9):
            uu = rnd.uniform(0.15, 0.85)
            x0, y0 = _wv_P(S, uu, inward=-2.0)
            a = math.radians(WV["a0"] + (WV["a1"] - WV["a0"]) * uu)
            nx, ny = math.cos(a), math.sin(a) * WV["rx"] / WV["ry"]
            Ln = math.hypot(nx, ny)
            nx, ny = nx / Ln, ny / Ln
            dist = rnd.uniform(10, 26) * grow
            sa = rnd.uniform(0.45, 0.9) * fade
            core = np.maximum(core, streak(pad, (x0 + nx * dist * 0.35, y0 + ny * dist * 0.35), (x0 + nx * dist, y0 + ny * dist), rnd.uniform(1.6, 2.6), a=sa))
    # ---- 0 일어남: 가운데 흰 번쩍 · 큰 네 뿔 별 / 1 가장 밝음
    if f == 0:
        mx, my = _wv_P(S, 0.5, inward=6)
        core = np.maximum(core, star4(pad, mx, my, 150, 1.0, rot=0.0, thin=0.06, diag=0.35))
        glow = np.maximum(glow, pad.radial(mx, my, 130, 1.3) * 0.9)
    if f == 1:
        mx, my = _wv_P(S, 0.5, inward=4)
        core = np.maximum(core, star4(pad, mx, my, 100, 0.9, rot=0.0, thin=0.07, diag=0.3))
    # ---- 6 ~ 7 부서짐: 날이 마디마디 끊기고 가는 조각 (날을 따라 누운 은빛 쪼가리) 이 바깥으로 흩어짐
    if f >= 6:
        k6 = 1 if f == 6 else 2
        breaks = np.ones_like(d)
        rb = random.Random(12300)
        for _ in range(7 + 4 * k6):
            ub = rb.uniform(0.08, 0.92)
            breaks = breaks * (1 - gauss(u - ub, 0.012 * k6 + 0.006))
        core = core * breaks * (0.9 if f == 6 else 0.45)
        glow = glow * (0.85 if f == 6 else 0.5)
        for (u0, sp, ang, sz) in WV_SHARDS:
            x0, y0 = _wv_P(S, u0, inward=3)
            a = math.radians(WV["a0"] + (WV["a1"] - WV["a0"]) * u0)
            nx, ny = math.cos(a + ang * 0.6), math.sin(a + ang * 0.6)
            dist = (12 + 22 * sp) * k6
            x, y = x0 + nx * dist, y0 + ny * dist
            tx, ty = -math.sin(a), math.cos(a)
            L = sz * (1.6 - 0.35 * k6)
            aa = (0.95 if f == 6 else 0.5) * fade + 0.1
            core = np.maximum(core, lens(pad, (x - tx * L, y - ty * L), (x + tx * L, y + ty * L), 2.6, a=aa, power=0.6, skew=0.3))
            glow = np.maximum(glow, lens(pad, (x - tx * L * 1.2, y - ty * L * 1.2), (x + tx * L * 1.2, y + ty * L * 1.2), 7.0, a=aa * 0.5, power=0.6, skew=0.3))
    core = core * fade
    glow = glow * fade
    return finish(pad, glow, core, halo=((1.4, 0.5), (3.8, 0.42), (9.5, 0.3)), body_k=0.92, core_bloom=((1.4, 0.38), (3.8, 0.28)), win=4.0)


sprite("rfx_jwave", "rotate_xyz", 384, 384, WV_N, 4, "wave", zoom=0,
       desc="큰 검기 (단타 직업 스킬이 칠 때 크게 뻗어 나가는 초승달 — 카메라를 봄, 위로 불룩, 칸 가운데 = 초승달 가운데): 0 가운데 흰 번쩍 · 큰 네 뿔 별과 가는 초승달이 "
       "일어남 → 1 가장 밝고 굵은 초승달 (흰 날 · 결 셋) → 2 ~ 5 날아감 (이어 돌림 — 날을 따라 밝은 마디가 흐르고 날 뒤로 휜 기운 가닥이 흘러내림 · 날 위 반짝임 · 바깥으로 "
       "튀는 불티) → 6 날이 마디마디 끊기며 조각이 튐 → 7 조각 · 가닥이 흐려짐")(wave_frame)



# ------------------------------------------------------------ rfx_jtear (차원의 틈 — 카메라를 봄, 칸 가운데 = 틈 가운데 · 틈 끝 x 0.065 W · 0.935 W)

TR_N = 8
TR_W, TR_H = 512, 256
COLORS.setdefault("tear", ((0.66, 0.36, 1.0), (0.07, 0.01, 0.14)))  # 확인용: 빛 = 아케인 블레이드 겉빛 · 심지 = 검은 보라 속
TR_L = 222.0  # 틈 반 길이 (칸 점 — 반 크기 × 0.87)
TR_OPEN = [3.0, 22.0, 46.0, 46.0, 46.0, 46.0, 15.0, 0.0]  # 장마다 가운데 반 높이 (2 ~ 5 이어 돌림은 같은 꼴 — 겹 둘의 일렁임 차례가 달라도 테가 어긋나지 않게)
_RT = random.Random(8120)
# 틈 테에서 바깥으로 뻗는 금 (가로 몫 u · 위 (−1) / 아래 (+1) · 기울기 · 길이 · 꺾임 씨앗)
TR_CRACKS = [(_RT.uniform(-0.8, 0.8), _RT.choice((-1, 1)), _RT.uniform(-0.55, 0.55), _RT.uniform(26, 70), _RT.randrange(10_000)) for _ in range(11)]
# 틈 속 별빛 (반지름 몫 · 각 · 크기) · 빨려 드는 티끌 (각 · 처음 거리 · 길이)
TR_STARS = [(_RT.uniform(0.1, 0.92), _RT.uniform(0, TAU), _RT.uniform(1.2, 2.6)) for _ in range(26)]
TR_MOTES = [(_RT.uniform(0, TAU), _RT.uniform(0.0, 1.0), _RT.uniform(6, 14)) for _ in range(18)]


def _tr_edge(x, top):
    """틈의 위 (top) · 아래 테 높이 몫 (가운데 반 높이 × 이 값): 끝이 뾰족한 렌즈 꼴 × 찢긴 들쭉날쭉함 (큰 물결은 작게 · 잔 톱니는 날카롭게 — 깨진 유리 결)"""
    u = np.clip(np.abs(x - TR_W / 2) / TR_L, 0, 1)
    env = np.clip(1 - u ** 2.2, 0, 1) ** 0.75
    k = 0 if top else 1
    jag = 1 + 0.09 * np.sin(0.07 * x + 1.3 + 2.1 * k) + 0.06 * np.sin(0.19 * x + 0.4 + 1.7 * k)
    tooth = np.abs(((x + 5.5 * k) / 11.0) % 2 - 1)  # 잔 톱니 (0 ~ 1)
    tooth2 = np.abs(((x + 2.0 + 3.0 * k) / 4.6) % 2 - 1)
    return env * (jag + 0.16 * (tooth - 0.5) + 0.05 * (tooth2 - 0.5))


def _tr_dist(pad, inside):
    """틈 테까지의 거리 (칸 점 · 안 < 0) — 4배 판의 참 거리"""
    if _ndi is None:
        return None
    din = _ndi.distance_transform_edt(inside)
    dout = _ndi.distance_transform_edt(~inside)
    return ((dout - din) / pad.ss).astype(np.float32)


def tear_frame(f):
    S = TR_W
    pad = Pad(TR_W, TR_H)
    X, Y = pad.grid()
    cx, cy = TR_W / 2, TR_H / 2
    Hc = TR_OPEN[f]
    loop = 2 <= f <= 5
    ph = (f - 2) / 4.0 if loop else (0.0 if f < 2 else 0.25)
    top = _tr_edge(X, True) * Hc
    bot = _tr_edge(X, False) * Hc
    inside_x = np.abs(X - cx) < TR_L
    dy = Y - cy
    # 테까지의 거리 (안 < 0): 참 거리 (scipy) — 없으면 세로 거리 (넓은 틈이라 거의 같음)
    d_edge = np.where(dy < 0, -dy - top, dy - bot)
    d_edge = np.where(inside_x, d_edge, np.sqrt((np.abs(X - cx) - TR_L).clip(0) ** 2 + dy ** 2))
    if Hc > 0.5:
        dt = _tr_dist(pad, d_edge < 0)
        if dt is not None:
            d_edge = dt
    open_k = clamp01(Hc / 46.0)
    glow = pad.zeros()
    core = pad.zeros()
    # ---- 틈 속 (심지 = 검은 보라 속 — 가장자리 1 점 부드럽게)
    if Hc > 0.5:
        core = clamp01((-d_edge) * pad.ss / 3.0 + 0.5) * 0.96
    # ---- 테: 밝은 금 줄 (빛) · 바깥으로 번지는 띠
    flick = 1.0 if not loop else 0.9 + 0.1 * math.sin(TAU * ph * 2 + 0.7)
    rim_w = 2.2 if Hc > 0.5 else 2.6
    env_x = clamp01(1 - (np.abs(X - cx) / (TR_L * 1.04)) ** 6)
    rim = gauss(d_edge, rim_w) * flick
    band = clamp01(1 - d_edge / 18.0) * (d_edge > 0) * 0.7 * env_x
    if f == 0:
        rim = gauss(np.abs(dy), 1.6) * env_x * 1.0
        band = gauss(np.abs(dy), 7.0) * env_x * 0.6
    if f == 7:  # 닫힘: 가로로 번쩍이는 가는 줄 (가운데가 가장 밝음)
        flash = gauss(np.abs(dy), 2.0) * clamp01(1 - (np.abs(X - cx) / (TR_L * 0.95)) ** 2)
        rim = flash
        band = gauss(np.abs(dy), 9.0) * clamp01(1 - (np.abs(X - cx) / TR_L) ** 2) * 0.7
    glow = np.maximum(glow, rim)
    glow = screen(glow, band)
    # ---- 금: 테에서 바깥으로 뻗는 깨진 유리 같은 금 (1 장부터 뻗음 · 7 장은 흐림)
    grow = [0.15, 0.6, 1.0, 1.0, 1.0, 1.0, 0.85, 0.55][f]
    ca = [0.55, 0.95, 0.92, 0.92, 0.92, 0.92, 0.85, 0.4][f]
    for (u0, side, tilt, ln, seed) in TR_CRACKS:
        x0 = cx + u0 * TR_L
        e = float(_tr_edge(np.array([x0]), side < 0)[0]) * Hc
        y0 = cy + side * e
        rc = random.Random(seed)
        pts = [(x0, y0)]
        L = ln * grow
        n = 4
        for i in range(1, n + 1):
            t = i / n
            pts.append((x0 + (tilt * L * t) + rc.uniform(-4, 4), y0 + side * L * t + rc.uniform(-2, 2)))
        a = ca * (0.85 + 0.15 * math.sin(TAU * ph + seed)) if loop else ca
        glow = np.maximum(glow, pad.stroke(pts, 3.0, 0.7, a0=a, a1=a * 0.35))
        if L > 30 and rc.random() < 0.6:  # 곁가지
            k = rc.randrange(1, n)
            bx, by = pts[k]
            pts2 = [(bx, by), (bx + rc.uniform(-14, 14), by + side * rc.uniform(8, 18))]
            glow = np.maximum(glow, pad.stroke(pts2, 1.4, 0.4, a0=a * 0.7, a1=0.1))
    # ---- 틈 속 소용돌이 · 별빛 (빛 칸 — 게임은 흰 청록 겹을 틈 위에 옅게 덮음: 속이 도는 듯)
    if Hc > 8:
        sy = max(0.12, Hc / TR_L)
        rr = np.sqrt((X - cx) ** 2 + ((Y - cy) / sy) ** 2)
        th = np.arctan2((Y - cy) / sy, X - cx)
        swirl = 0.5 + 0.5 * np.sin(th * 3.0 - rr * 0.045 + TAU * ph)
        swirl = swirl ** 3 * clamp01(1 - rr / TR_L) * (d_edge < -2.0) * 0.32 * open_k
        glow = np.maximum(glow, swirl)
        for (rf, a0, sz) in TR_STARS:
            ang = a0 + TAU * ph * (0.25 + 0.5 * rf)  # 안쪽이 빨리 돎 (이어 돌림 — 한 바퀴의 몫)
            x = cx + math.cos(ang) * rf * TR_L * 0.9
            y = cy + math.sin(ang) * rf * TR_L * 0.9 * sy
            m = float(np.interp(x, [cx - TR_L, cx + TR_L], [0, 1]))
            if -0.95 < (x - cx) / TR_L < 0.95:
                lim_t = float(_tr_edge(np.array([x]), True)[0]) * Hc - 3
                lim_b = float(_tr_edge(np.array([x]), False)[0]) * Hc - 3
                if -lim_t < y - cy < lim_b:
                    tw = 0.6 + 0.4 * math.sin(TAU * ph * 2 + a0 * 3 + m)
                    glow = np.maximum(glow, pad.disc_field(x, y, sz * 0.6) * 0.75 * tw)
    # ---- 빨려 드는 티끌 (테 바깥에서 틈 쪽으로 — 이어 돌림)
    if 1 <= f <= 6:
        for (a0, d0, ln) in TR_MOTES:
            prog = (d0 + ph) % 1.0 if loop else d0
            rad = (1 - prog) * 1.0
            x = cx + math.cos(a0) * (TR_L * 0.55 + 70 * rad)
            y = cy + math.sin(a0) * (Hc + 14 + 46 * rad)
            if not (10 < x < TR_W - 10 and 8 < y < TR_H - 8):
                continue
            vx, vy = cx - x, cy - y
            vl = math.hypot(vx, vy) + 1e-6
            p1 = (x + vx / vl * 2.0, y + vy / vl * 2.0)
            p0 = (x - vx / vl * ln, y - vy / vl * ln)
            a = 0.75 * math.sin(math.pi * prog) if loop else 0.5
            glow = np.maximum(glow, streak(pad, p0, p1, 2.4, a=a))
    # ---- 번쩍 (0 · 1 갈라짐 · 7 닫힘): 가운데 큰 네 뿔 별
    if f in (0, 1, 7):
        r = {0: 70, 1: 50, 7: 110}[f]
        a = {0: 1.0, 1: 0.7, 7: 1.0}[f]
        glow = np.maximum(glow, star4(pad, cx, cy, r, a, rot=0.0, thin=0.05, diag=0.3))
    if f == 7:  # 닫히며 튀는 조각 (가로로 퍼짐)
        rs = random.Random(9031)
        for _ in range(14):
            x = cx + rs.uniform(-1, 1) * TR_L * 0.8
            y = cy + rs.uniform(-1, 1) * 30
            dx = (x - cx) / TR_L
            glow = np.maximum(glow, lens(pad, (x, y), (x + dx * 26 + rs.uniform(-4, 4), y + (y - cy) * 0.8), 3.0, a=0.7, power=0.6, skew=0.3))
    return finish(pad, glow, core, halo=((1.6, 0.5), (4.5, 0.4), (11.0, 0.28)), body_k=0.95, core_bloom=(), win=5.0)


sprite("rfx_jtear", "rotate_xyz", TR_W, TR_H, TR_N, 4, "tear", zoom=0,
       desc="차원의 틈 (디멘션 슬래시 — 카메라를 봄, 칸 가운데 = 틈 가운데 · 틈 끝 x 0.065 · 0.935 W): 0 가는 빛 금 · 가운데 번쩍 → 1 벌어짐 (테가 가장 밝음 · 금이 "
       "뻗음) → 2 ~ 5 벌어져 일렁임 (이어 돌림 — 같은 꼴: 테의 깜빡임 · 속의 소용돌이 · 도는 별빛 · 바깥에서 빨려 드는 티끌) → 6 닫힘 · 7 가로로 번쩍이며 닫힘 "
       "(조각이 튐). 빛 칸 = 테 · 금 · 속의 소용돌이, 심지 칸 = 검은 속 (불투명)")(tear_frame)


# ============================================================ 0.51.0 매지션 2차 재설계안 2판 (2026-11-06 — 셀레스티얼 이글 · 스타 볼텍스 · 무덤)
# 아크메이지: 빛 칸 = 밤하늘 자주 몸 (성운 결) · 심지 칸 = 금빛 테 · 별 / 네크로맨서: 빛 칸 = 녹색 혼불 · 안개 · 심지 칸 = 뼈색 돌 (몸)

# ------------------------------------------------------------ rfx_jeagle (별빛 독수리 — 가는 쪽으로 누움, 머리 = 오른쪽 · 날개 = 위아래)

EG_N = 8
EG_S = 384
# 장마다 (날개 펼침 몫 · 앞뒤로 젖힘 — + 앞 · − 뒤): 0 · 1 내리침 (1 = 내리친 끝 — 깃털이 쏟아지는 장) · 2 ~ 4 접으며 올림 · 5 다시 펼침 · 6 ~ 7 날개를 접고 내리꽂음
EG_POSE = [(1.0, 0.15), (0.93, 0.0), (0.72, -0.25), (0.58, -0.42), (0.78, -0.2), (0.95, 0.1), (0.4, -1.5), (0.4, -1.5)]
_RE = random.Random(9100)
EG_DUST = [(_RE.uniform(0, 1), _RE.uniform(0, 1), _RE.uniform(0.6, 1.6)) for _ in range(70)]


# 위 날개의 기본 꼴 (펼침 1 · 젖힘 0 — 어깨 기준 칸 점, x + = 앞 (머리 쪽) · y − = 바깥): 앞 테 (어깨 → 팔꿈치 → 손목 → 손 앞) · 손가락 깃 다섯 (밑 → 끝 —
# 바깥 깃부터, 끝이 부채처럼 벌어짐) · 둘째 깃 뒤 테 (마지막 손가락 밑 → 몸)
EG_LEAD = [(0.0, 0.0), (5.0, -42.0), (10.0, -86.0), (4.0, -112.0)]
EG_FINGERS = [((-2.0, -114.0), (-4.0, -158.0)), ((-12.0, -113.0), (-20.0, -154.0)), ((-21.0, -110.0), (-34.0, -146.0)), ((-30.0, -105.0), (-46.0, -134.0)),
              ((-38.0, -98.0), (-55.0, -120.0))]
EG_TRAIL = [(-44.0, -86.0), (-52.0, -66.0), (-56.0, -44.0), (-57.0, -22.0), (-50.0, -4.0)]


def _eg_dive_wing(side):
    """내리꽂을 때 날개 (몸 옆에 붙여 뒤로 접음 — 칼날 꼴): 다각형 · 앞 테 · 손가락 깃 · 뒤 테 (꼴은 _eg_wing 과 같음)"""
    cy = 192.0

    def m(p):
        return (p[0], cy + side * -p[1])

    lead = [m((224.0, 14.0)), m((196.0, 30.0)), m((160.0, 38.0)), m((112.0, 34.0))]
    fingers = [(m((118.0, 32.0)), m((80.0, 36.0))), (m((122.0, 28.0)), m((84.0, 28.0))), (m((126.0, 24.0)), m((90.0, 20.0)))]
    trail = [m((140.0, 20.0)), m((168.0, 14.0)), m((196.0, 10.0))]
    poly = lead + [b for (b, _) in fingers] + trail
    return poly, lead, fingers, trail


def _eg_wing(span, sweep, side, sc=0.92):
    """날개 하나 (side −1 = 위 · +1 = 아래 — 위아래로 뒤집음): 기본 꼴을 펼침 span (바깥 길이) · 젖힘 sweep (+ 앞 · − 뒤 — 끝으로 갈수록 더 돎 · 접히면 손이 뒤로
    꺾임) 으로 바꿈. 다각형 · (어깨 · 손목 · 끝) · 손가락 깃 (밑 · 끝) · 뒤 테"""
    Sx, Sy = 214.0, 192.0 + side * 15.0

    def tf(p):
        x, y = p[0] * sc, p[1] * sc
        r = -y / (158.0 * sc)
        a = sweep * (0.3 + 0.7 * r) - (1 - span) * 0.9 * max(0.0, r - 0.5)
        y2 = y * (0.35 + 0.65 * span)
        ca, sa = math.cos(a), math.sin(a)
        # 앞쪽으로 돎 (a > 0 이면 바깥 끝이 +x 쪽)
        xr = x * ca - y2 * sa
        yr = x * sa + y2 * ca
        return (Sx + xr, Sy + yr if side < 0 else Sy - yr)

    lead = [tf(p) for p in EG_LEAD]
    fingers = [(tf(b), tf(t)) for (b, t) in EG_FINGERS]
    trail = [tf(p) for p in EG_TRAIL]
    poly = lead + [b for (b, _) in fingers] + trail
    return poly, lead, fingers, trail


def eagle_frame(f):
    S = EG_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    cy = S / 2
    span, sweep = EG_POSE[f]
    dive = f >= 6
    glow = pad.zeros()
    core = pad.zeros()
    sil = pad.zeros()
    lines = []
    joints = []
    for side in (-1, 1):
        poly, lead, fingers, trail = _eg_dive_wing(side) if dive else _eg_wing(span, sweep, side)
        Sh, Wr = lead[0], lead[2]
        T = fingers[0][1]
        sil = np.maximum(sil, pad.poly(poly))
        for i, (b, tip) in enumerate(fingers):
            # 손가락 깃 (끝이 뾰족 · 사이가 벌어짐) · 테
            sil = np.maximum(sil, pad.stroke([b, tip], 11.0 - i * 0.6, 2.5))
            lines.append(([b, tip], 1.5, 0.85))
        lines.append((lead + [T], 2.6, 1.0))
        lines.append((trail, 1.0, 0.5))
        # 날개 결 (앞 테에서 뒤 테 쪽으로 — 깃 줄기)
        for k in range(4):
            t = (k + 0.6) / 4.5
            i0 = min(len(lead) - 2, int(t * (len(lead) - 1)))
            u = t * (len(lead) - 1) - i0
            p0 = (lead[i0][0] + (lead[i0 + 1][0] - lead[i0][0]) * u, lead[i0][1] + (lead[i0 + 1][1] - lead[i0][1]) * u)
            q = trail[min(len(trail) - 1, len(trail) - 1 - k)]
            lines.append(([p0, q], 0.9, 0.4))
        joints += [(Sh, 7.0), (Wr, 9.0), (T, 10.0)]
    # 몸 · 머리 · 부리 · 꼬리 (내리꽂을 때는 몸이 조금 길게)
    bl = 1.12 if dive else 1.0
    sil = np.maximum(sil, pad.ellipse(200, cy, 64 * bl, 21))
    hx = 272 + (14 if dive else 0)
    sil = np.maximum(sil, pad.disc_field(hx, cy, 18.0, soft=1.0))
    beak = [(hx + 12, cy - 7), (hx + 34, cy - 1), (hx + 30, cy + 5), (hx + 12, cy + 7)]
    sil = np.maximum(sil, pad.poly(beak))
    tw = 0.55 if dive else 1.0
    tail = [(146, cy - 12), (78, cy - 34 * tw), (70, cy - 18 * tw), (66, cy), (70, cy + 18 * tw), (78, cy + 34 * tw), (146, cy + 12)]
    sil = np.maximum(sil, pad.poly(tail))
    for k in range(-2, 3):
        lines.append(([(144, cy + k * 4), (74, cy + k * 15 * tw)], 1.0, 0.5))
    lines.append(([(140, cy), (200, cy - 10), (hx, cy - 8)], 1.4, 0.6))
    lines.append(([(140, cy), (200, cy + 10), (hx, cy + 8)], 1.4, 0.6))
    joints += [((hx, cy), 9.0), ((70, cy), 8.0)]
    # 빛: 성운 결로 채운 몸 · 가장자리 빛 · 둘레 옅은 무리
    neb = fbm(X / S, Y / S, seed=910 + (f % 6 if not dive else 7), octaves=4, px=5, py=5)
    neb = clamp01(0.35 + 0.65 * neb)
    edge = clamp01(sil - gblur(sil, 3.0 * SS)) * 2.0
    glow = np.maximum(glow, sil * neb * 0.62)
    glow = np.maximum(glow, clamp01(edge) * 0.7)
    glow = screen(glow, gblur(sil, 10.0 * SS) * 0.35)
    # 심지: 금빛 테 · 별자리 결 · 마디 별 · 눈 · 몸 안 잔별
    for pts, w, a in lines:
        core = np.maximum(core, pad.stroke(pts, w, w * 0.8, a0=a, a1=a, soft=0.4))
    for (p, r) in joints:
        core = np.maximum(core, star4(pad, p[0], p[1], r * 1.6, 1.0, rot=0.2 * f, thin=0.1, diag=0.4))
        glow = np.maximum(glow, pad.disc_field(p[0], p[1], r * 1.4, soft=r) * 0.7)
    core = np.maximum(core, pad.disc_field(hx + 4, cy - 4, 3.2, soft=0.8))
    for (u, v, r) in EG_DUST:
        x, y = 60 + u * 260, 40 + v * 304
        ix, iy = int(min(S - 1, x)), int(min(S - 1, y))
        if sil[int(iy * SS), int(ix * SS)] < 0.5:
            continue
        tw2 = 0.55 + 0.45 * math.sin(f * 1.9 + u * 17 + v * 11)
        core = np.maximum(core, pad.disc_field(x, y, r * 0.8, soft=0.6) * tw2)
    if f == 1:
        # 내리친 끝: 날개 뒤 테에서 깃털 별빛이 흩어짐
        rs = random.Random(9150)
        for side in (-1, 1):
            for _ in range(9):
                x = rs.uniform(150, 260)
                y = cy + side * rs.uniform(60, 150) * span
                core = np.maximum(core, star4(pad, x, y, rs.uniform(5, 9), 0.9, rot=rs.uniform(0, 1)))
                glow = np.maximum(glow, pad.stroke([(x + 10, y - side * 6), (x - 6, y + side * 10)], 3.0, 0.6, a0=0.5, a1=0.0))
    if dive:
        # 내리꽂음: 뒤로 끌리는 별빛 줄 · 머리 번쩍 (7)
        rs = random.Random(9160 + f)
        for k in range(9):
            y = cy + (k - 4) * 10 + rs.uniform(-3, 3)
            x0 = 66 - rs.uniform(0, 20)
            ln = rs.uniform(40, 62)
            glow = np.maximum(glow, pad.stroke([(x0 - ln, y), (x0, y)], 1.0, 5.0, a0=0.0, a1=0.6))
            core = np.maximum(core, pad.stroke([(x0 - ln * 0.7, y), (x0, y)], 0.4, 1.6, a0=0.0, a1=0.8))
        if f == 7:
            core = np.maximum(core, star4(pad, hx + 30, cy, 34.0, 1.0, rot=0.0, thin=0.08, diag=0.35))
            glow = np.maximum(glow, pad.radial(hx + 26, cy, 48.0, 1.6) * 0.8)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (10.0, 0.24)), body_k=0.92, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)


COLORS.setdefault("eagle", ((0.42, 0.26, 0.86), (1.0, 0.86, 0.42)))
sprite("rfx_jeagle", "lookat_direction", EG_S, EG_S, EG_N, 4, "eagle", zoom=0, loop=True,
       desc="별빛 독수리 (셀레스티얼 이글 — 가는 쪽으로 누움, 오른쪽 = 머리 · 날개는 위아래로 펼침, 몸 가운데 (200, 192)): 0 ~ 5 날갯짓 (이어 돌림 — 0 · 1 내리침 "
       "(1 = 내리친 끝 · 깃털 별빛) · 2 ~ 4 앞으로 접으며 올림 · 5 다시 펼침) · 6 ~ 7 날개를 접고 내리꽂음 (뒤로 끌리는 별빛 줄 · 7 머리 번쩍). 빛 칸 = 성운 결로 "
       "채운 몸 · 가장자리 빛, 심지 칸 = 금빛 테 · 깃 결 · 마디 별 · 눈 · 몸 안 잔별")(eagle_frame)


# ------------------------------------------------------------ rfx_jgalaxy (별의 소용돌이 — 땅에 누움, 칸 가운데 = 소용돌이 가운데 · 팔 끝 = 반 크기 × 0.9)

GX_N = 8
GX_S = 384
_RG = random.Random(9200)
GX_STARS = [(_RG.uniform(0.12, 1.0), _RG.uniform(0, TAU), _RG.uniform(1.0, 2.6), _RG.randrange(1000)) for _ in range(90)]


def galaxy_frame(f):
    S = GX_S
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    Rfull = S / 2 * 0.9
    Rm = [0.35, 0.7, 1.0, 1.0, 1.0, 1.0, 0.5, 0.0][f] * Rfull
    b = 0.32 if f < 6 else 0.2  # 감긴 정도 (작을수록 많이 감김)
    glow = pad.zeros()
    core = pad.zeros()
    if f <= 6:
        r0 = 10.0
        lr = np.log(np.maximum(rr, r0) / r0)
        arm_phase = lr / b
        wid = 0.32 + 0.25 * clamp01(rr / max(1.0, Rm))
        arms = pad.zeros()
        for k in range(2):
            dphi = np.angle(np.exp(1j * (th - arm_phase - k * math.pi)))
            arms = np.maximum(arms, np.exp(-0.5 * (dphi / wid) ** 2))
            # 사이 팔 (옅게 — 성운이 넓게 보이게)
            dphi2 = np.angle(np.exp(1j * (th - arm_phase - k * math.pi - math.pi / 2)))
            arms = np.maximum(arms, np.exp(-0.5 * (dphi2 / (wid * 0.8)) ** 2) * 0.38)
        fall = clamp01(1 - (rr / max(1.0, Rm)) ** 2.2) * clamp01(rr / 6.0)
        X, Y = pad.grid()
        nz = clamp01(0.45 + 0.75 * fbm(X / S, Y / S, seed=920, octaves=4, px=6, py=6))
        twk = 1.0 if f < 2 or f == 6 else [1.0, 0.9, 0.96, 0.86][f - 2]
        glow = np.maximum(glow, arms * fall * nz * 1.0 * twk)
        glow = screen(glow, fall * 0.16 * nz)
        # 팔 등줄 (심지 — 가는 밝은 줄)
        ridge = pad.zeros()
        for k in range(2):
            dphi = np.angle(np.exp(1j * (th - arm_phase - k * math.pi)))
            ridge = np.maximum(ridge, np.exp(-0.5 * (dphi / (wid * 0.28)) ** 2))
        core = np.maximum(core, ridge * fall * 0.55)
        # 가운데 불룩 (밝은 심)
        bulge = np.exp(-0.5 * (rr / (16.0 + (10.0 if f == 6 else 0.0))) ** 2)
        core = np.maximum(core, bulge * (1.0 if f != 0 else 0.8))
        glow = np.maximum(glow, np.exp(-0.5 * (rr / 34.0) ** 2) * 0.9)
        # 별 (팔을 따라 · 반짝임은 장마다 다름)
        for (rf, a0, sz, seed) in GX_STARS:
            r_ = rf * Rm
            if r_ < 8:
                continue
            ph = math.log(max(r_, 10.0) / 10.0) / b
            a = ph + a0 * 0.18 + (0 if seed % 2 else math.pi)
            x, y = c + math.cos(a) * r_, c + math.sin(a) * r_
            tw = 0.5 + 0.5 * math.sin(f * 2.1 + seed)
            if seed % 5 == 0:
                core = np.maximum(core, star4(pad, x, y, sz * 3.2, 0.9 * tw + 0.1, rot=0.3, thin=0.12, diag=0.4))
            else:
                core = np.maximum(core, pad.disc_field(x, y, sz * 0.7, soft=0.5) * (0.5 + 0.5 * tw))
        if f == 0:
            core = np.maximum(core, star4(pad, c, c, 60.0, 1.0, rot=0.0, thin=0.08, diag=0.4))
    else:
        # 한 점 번쩍 · 옅은 충격 고리
        core = np.maximum(core, star4(pad, c, c, 120.0, 1.0, rot=0.3, thin=0.06, diag=0.35))
        core = np.maximum(core, pad.disc_field(c, c, 12.0, soft=6.0))
        glow = np.maximum(glow, pad.radial(c, c, 110.0, 1.4) * 0.9)
        glow = np.maximum(glow, gauss(rr - 120.0, 6.0) * 0.6)
        core = np.maximum(core, gauss(rr - 120.0, 1.6) * 0.5)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (10.0, 0.22)), body_k=0.9, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=5.0)


sprite("rfx_jgalaxy", "direction_z", GX_S, GX_S, GX_N, 4, "arcmage", zoom=0,
       desc="별의 소용돌이 (스타 볼텍스 — 땅에 누움, 칸 가운데 = 소용돌이 가운데 · 팔 끝 = 반 크기 × 0.9): 0 가운데 번쩍 · 작은 팔 → 1 펼쳐짐 → 2 ~ 5 두 갈래 나선 팔 "
       "(이어 돌림 — 팔을 따라 별이 반짝임, 도는 것은 게임이 spinRate 로) → 6 팔이 감기며 조여 듦 · 가운데가 밝아짐 → 7 한 점으로 번쩍 · 옅은 충격 고리. 빛 칸 = 성운 "
       "팔 · 둘레, 심지 칸 = 팔 등줄 · 별 · 가운데 불룩")(galaxy_frame)


# ------------------------------------------------------------ rfx_jgrave (무덤 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

GV_N = 8
GV_W, GV_H = 128, 192
GV_BASE = GV_H - 8.0
_RV = random.Random(9300)
GV_CRACKS = [[(64 + _RV.uniform(-26, 26), GV_BASE - _RV.uniform(30, 80))] for _ in range(4)]
for _cr in GV_CRACKS:
    for _ in range(4):
        x, y = _cr[-1]
        _cr.append((x + _RV.uniform(-9, 9), y + _RV.uniform(5, 13)))


def _gv_stone(pad, dy=0.0, shift=0.0):
    """묘비 (아래가 땅 · 위가 둥근 아치): 몸 · 새긴 해골 자리 (어두운 홈) · 해골 눈 둘"""
    x0, x1 = 34.0 + shift, 94.0 + shift
    top = GV_BASE - 92.0 + dy
    body = pad.poly([(x0, GV_BASE + dy), (x0 + 2, top + 30), (x1 - 2, top + 30), (x1, GV_BASE + dy)])
    body = np.maximum(body, pad.ellipse((x0 + x1) / 2, top + 30, (x1 - x0) / 2 - 2, 30))
    # 받침 (조금 넓게)
    body = np.maximum(body, pad.poly([(x0 - 6, GV_BASE + dy), (x0 - 4, GV_BASE - 10 + dy), (x1 + 4, GV_BASE - 10 + dy), (x1 + 6, GV_BASE + dy)]))
    cx = (x0 + x1) / 2
    sk, holes, gaps, e1, e2 = _skull_masks(pad, 64, 0.0, 0.42)
    # 해골은 칸 가운데 (64, 64) 기준 — 묘비 가운데 위쪽으로 옮김
    def mv(a, ox, oy):
        sx, sy = int(round(ox * SS)), int(round(oy * SS))
        out = np.zeros_like(a)
        ys = slice(max(0, sy), a.shape[0] + min(0, sy))
        yd = slice(max(0, -sy), a.shape[0] - max(0, sy))
        xs = slice(max(0, sx), a.shape[1] + min(0, sx))
        xd = slice(max(0, -sx), a.shape[1] - max(0, sx))
        out[ys, xs] = a[yd, xd]
        return out
    ox, oy = cx - 64, (top + 52) - 64
    carve = mv(sk, ox, oy)
    eyes = [(e1[0] + ox, e1[1] + oy), (e2[0] + ox, e2[1] + oy)]
    return body, carve, eyes, top, cx


def grave_frame(f):
    pad = Pad(GV_W, GV_H)
    rs = random.Random(9310 + f)
    frac = [0.38, 0.82, 1, 1, 1, 1, 1, 0][f]
    H = 92.0
    dy = H * (1 - frac)
    clip = pad.zeros()
    clip[: int(GV_BASE * SS), :] = 1
    glow = pad.zeros()
    stone = pad.zeros()
    dark = pad.zeros()
    flame_k = [0.3, 0.6, 1.0, 1.0, 1.0, 1.0, 0.55, 0.0][f]
    if f <= 6:
        body, carve, eyes, top, cx = _gv_stone(pad, dy)
        body = body * clip
        lit, shade, _ = bevel_light(body, 5.0)
        stone = clamp01(body * (0.8 + 0.2 * lit - 0.25 * shade))
        dark = np.maximum(dark, carve * clip * 0.75)
        # 금 (6 은 크게 벌어짐)
        for cr in GV_CRACKS[: (4 if f == 6 else 2)]:
            pts = [(x, y + dy) for x, y in cr]
            dark = np.maximum(dark, pad.stroke(pts, 2.2 if f == 6 else 1.2, 0.5) * body)
        if f == 6:
            for k in range(3):
                y = GV_BASE - rs.uniform(25, 75)
                dark = np.maximum(dark, pad.stroke([(36, y), (64 + rs.uniform(-8, 8), y + rs.uniform(-6, 6)), (94, y - 4)], 2.6) * body)
        # 해골 눈의 녹색 빛 · 몸 둘레 옅은 빛
        for (ex, ey) in eyes:
            if ey < GV_BASE:
                glow = np.maximum(glow, pad.disc_field(ex, ey, 4.5, soft=2.5) * (0.6 + 0.4 * flame_k))
        glow = np.maximum(glow, gblur(body, 4.0 * SS) * 0.45)
        # 꼭대기의 녹색 혼불 (2 ~ 5 일렁임)
        if flame_k > 0:
            ph = f * 1.7
            fx, fy = cx, top + 8
            for k in range(5):
                h = (48 + 12 * math.sin(ph + k * 1.3)) * flame_k
                sway = 7 * math.sin(ph * 0.8 + k)
                w = (16 - 2.4 * k) * (0.7 + 0.3 * flame_k)
                pts = bez((fx + (k - 2) * 4, fy), (fx + sway * 0.5, fy - h * 0.5), (fx + sway, fy - h), n=12)
                glow = np.maximum(glow, pad.stroke(pts, w, 1.0, a0=0.95, a1=0.0, soft=3.0))
            glow = np.maximum(glow, pad.radial(fx, fy - 10, 30.0 * flame_k + 1, 1.6) * 0.75)
            stone = np.maximum(stone, pad.stroke(bez((fx, fy), (fx + 2, fy - 12), (fx + 3 * math.sin(ph), fy - 24 * flame_k), n=8), 5.0, 0.8, a0=0.75, a1=0.0,
                                                 soft=1.5) * flame_k)
    else:
        # 7: 무너진 돌무더기 · 먼지 · 오르는 혼 둘
        for k in range(9):
            x = 64 + rs.uniform(-34, 34)
            y = GV_BASE - rs.uniform(2, 16)
            stone = np.maximum(stone, pad.poly(chunk_pts(rs, x, y, rs.uniform(5, 10))) * 0.85)
        glow = np.maximum(glow, pad.radial(64, GV_BASE - 20, 56.0, 1.6, sy=0.6) * 0.55)
        for k in range(2):
            x = 50 + 28 * k
            glow = np.maximum(glow, pad.stroke(bez((x, GV_BASE - 20), (x - 6, GV_BASE - 60), (x + 4, GV_BASE - 100), n=12), 7.0, 1.0, a0=0.5, a1=0.0, soft=3.0))
    # 땅: 녹색 빛 · 솟을 때 튀는 흙 · 금
    glow = np.maximum(glow, pad.radial(64, GV_BASE, 60.0, 2.0, sy=0.18) * 0.85)
    if f <= 1:
        for k in range(12):
            x = 64 + rs.uniform(-50, 50)
            y = GV_BASE - rs.uniform(4, 46) * (f + 1) / 2
            stone = np.maximum(stone, pad.disc_field(x, y, rs.uniform(1.6, 3.6)) * 0.9)
        glow = np.maximum(glow, pad.stroke([(14, GV_BASE - 1), (114, GV_BASE - 1)], 3.0, 3.0, a0=0.8, a1=0.8, soft=2.0))
    body = screen(glow * 0.9, stone * 0.18)
    core = clamp01(stone - dark * 0.85)
    g = pad.down(body)
    g = screen(g, gblur(g, 2.5) * 0.5, gblur(g, 6.0) * 0.3)
    cc = pad.down(core)
    wb = win_border(g.shape, 2.0)
    return clamp01(g * wb), clamp01(cc * wb)


sprite("rfx_jgrave", "lookat_y", GV_W, GV_H, GV_N, 4, "necromancer", zoom=2,
       desc="무덤 (그레이브 콜 · 그레이브야드 · 망자의 군대 — 아래 끝 = 땅 (아래 8 점)): 0 ~ 1 흙을 튀기며 둥근 아치 묘비가 솟음 → 2 ~ 5 서 있음 (꼭대기의 녹색 혼불이 "
       "일렁임 · 새긴 해골의 눈 빛 — 이어 돌림) → 6 금이 벌어지고 혼불이 사그라짐 → 7 돌무더기 · 먼지 · 오르는 혼. 빛 칸 = 녹색 혼불 · 땅빛, 심지 칸 = 뼈색 돌 (새긴 "
       "해골 · 금은 비움)")(grave_frame)



# ============================================================ 0.51.0 궁수 2차 재설계안 2판 (2026-11-06 — 스나이퍼 그물 · 배틀 아처 폭풍 매)
# 스나이퍼 그물: 빛 칸 = 밧줄 몸 (거의 불투명 — 밝은 하늘 · 풀 위에서도 보이게) · 심지 칸 = 매듭 · 추 · 밧줄 결 / 배틀 아처 매: 빛 칸 = 바람 결 몸 · 심지 칸 = 번개 테 · 눈

# ------------------------------------------------------------ rfx_jnet (그물 — 카메라를 봄, 칸 가운데 = 그물 가운데)

NT_N = 8
NT_S = 256


def _net_map(u, v, R, wob, bag, f, cx=128.0, cy=128.0):
    """그물 칸 (u, v — −1 ~ 1) → 그림 점: 펼친 그물은 둥글게 부푼 사각 그물 (가장자리가 조금 처짐 · 흔들림 wob), bag > 0 이면 위로 모여 조인 자루"""
    x = u * (1 - 0.18 * v * v)
    y = v * (1 - 0.18 * u * u)
    x += 0.05 * wob * math.sin(3.1 * v + f * 1.3)
    y += 0.05 * wob * math.sin(2.7 * u + f * 1.7)
    if bag > 0:
        t = (y + 1) / 2  # 0 위 · 1 아래
        pinch = 0.18 + 0.82 * (t ** 0.55)
        x = x * (1 - bag + bag * pinch)
        y = y * (1 - 0.25 * bag) + bag * 0.18 * math.sin(math.pi * min(1.0, abs(u)) / 2) * t
    return (cx + x * R, cy + y * R)


def _net_lines(R, wob, bag, f, n=7, keep=None, rs=None):
    """그물 밧줄 (u 줄 · v 줄 — 팔각 둘레 안만) · 매듭 (만나는 점) · 가장자리 점 (추 자리). keep(rs) 이 False 인 토막은 뺌 (끊어짐)"""
    lines, knots, rim = [], [], []

    def inside(u, v):
        return abs(u) <= 1.0001 and abs(v) <= 1.0001 and abs(u) + abs(v) <= 1.42

    ticks = [-1 + 2 * i / (n - 1) for i in range(n)]
    for along in (0, 1):
        for g in ticks:
            seg = []
            for i in range(41):
                t = -1 + 2 * i / 40
                u, v = (g, t) if along == 0 else (t, g)
                if inside(u, v):
                    seg.append(_net_map(u, v, R, wob, bag, f))
                elif seg:
                    break
            if len(seg) >= 2:
                if keep is None:
                    lines.append(seg)
                else:
                    for q in range(0, len(seg) - 1, 5):
                        if keep(rs):
                            lines.append(seg[q:q + 6])
    for u in ticks:
        for v in ticks:
            if inside(u, v):
                knots.append(_net_map(u, v, R, wob, bag, f))
    for k in range(8):
        a = k * TAU / 8 + TAU / 16
        u, v = math.cos(a), math.sin(a)
        sc = 1.42 / (abs(u) + abs(v)) if abs(u) + abs(v) > 1.42 else 1.0
        sc = min(sc, 1 / max(abs(u), abs(v)))
        rim.append(_net_map(u * sc, v * sc, R, wob, bag, f))
    return lines, knots, rim


def net_frame(f):
    S = NT_S
    pad = Pad(S, S)
    c = (S / 2, S / 2)
    glow = pad.zeros()
    core = pad.zeros()
    rope = pad.zeros()
    knots = []
    weights = []
    if f == 0:
        # 묶인 다발: 꼬인 밧줄 덩이 · 삐져나온 끝 · 매달린 추 넷
        for k in range(7):
            a = k * TAU / 7
            p = (c[0] + math.cos(a) * 16, c[1] + math.sin(a) * 12)
            q = (c[0] - math.cos(a + 0.9) * 18, c[1] - math.sin(a + 0.9) * 14)
            rope = np.maximum(rope, pad.stroke([p, (c[0], c[1]), q], 6.0, 5.0, soft=0.6))
        for k in range(4):
            a = 0.4 + k * TAU / 4
            p = (c[0] + math.cos(a) * 26, c[1] + math.sin(a) * 22)
            rope = np.maximum(rope, pad.stroke([(c[0], c[1]), p], 3.5, 3.0, soft=0.5))
            weights.append((p, 7.0))
        knots.append(((c[0], c[1]), 10.0))
    elif f <= 5:
        if f <= 3:
            R = (44.0, 74.0, 100.0)[f - 1]
            wob, bag, n = (1.0, 0.7, 0.4)[f - 1], 0.0, (5, 7, 7)[f - 1]
        else:
            R, wob, bag, n = 96.0, 0.35, 0.72 + 0.08 * (f - 4), 7
        lines, kn, rim = _net_lines(R, wob, bag, f, n=n)
        w = 4.2 if f >= 2 else 4.8
        for seg in lines:
            rope = np.maximum(rope, pad.stroke(seg, w, w, soft=0.6))
        # 테두리 밧줄 (가장자리 점을 잇는 굵은 줄)
        rope = np.maximum(rope, pad.stroke(rim + [rim[0]], w + 1.2, w + 1.2, soft=0.6))
        knots += [(p, 4.6 if f >= 2 else 5.2) for p in kn]
        for p in rim:
            if bag > 0 and p[1] < c[1] - R * 0.3:
                continue
            a = math.atan2(p[1] - c[1], p[0] - c[0])
            d = (0.0, 1.0) if bag > 0 else (math.cos(a), math.sin(a))
            wpt = (p[0] + d[0] * 10, p[1] + d[1] * 10)
            rope = np.maximum(rope, pad.stroke([p, wpt], 2.6, 2.4, soft=0.5))
            weights.append((wpt, 7.5))
        if bag > 0:
            # 조이는 기운 (자루 바깥 옅은 빛 줄)
            for k in range(6):
                a = k * TAU / 6 + 0.3 * f
                p = (c[0] + math.cos(a) * (R * 0.9 + 14), c[1] + math.sin(a) * (R * 0.8 + 10))
                q = (c[0] + math.cos(a + 0.35) * (R * 0.9 + 6), c[1] + math.sin(a + 0.35) * (R * 0.8 + 4))
                glow = np.maximum(glow, pad.stroke([p, q], 2.0, 0.6, a0=0.6, a1=0.0, soft=0.8))
    else:
        # 끊어짐 (6) · 흩어짐 (7): 밧줄 토막이 바깥으로 흩어지며 흐려짐 · 추가 떨어짐
        rs = random.Random(9310 + f)
        R = 100.0 + (f - 6) * 16
        lines, kn, rim = _net_lines(R, 0.6, 0.0, f, n=7, keep=lambda r: r.random() > (0.35 if f == 6 else 0.65), rs=rs)
        a_ = 1.0 if f == 6 else 0.5
        for seg in lines:
            m = seg[len(seg) // 2]
            ox, oy = (m[0] - c[0]) * 0.12 * (f - 5), (m[1] - c[1]) * 0.12 * (f - 5)
            rope = np.maximum(rope, pad.stroke([(x + ox, y + oy) for (x, y) in seg], 3.6, 1.2, a0=a_, a1=a_ * 0.3, soft=0.6))
        for p in rim:
            if rs.random() < 0.5:
                continue
            weights.append(((p[0], p[1] + 10 + (f - 6) * 12), 6.0 * a_))
    # 빛 = 밧줄 몸 (거의 불투명) + 둘레 옅은 무리 / 심지 = 밧줄 결 (가는 밝은 줄) · 매듭 · 추
    glow = np.maximum(glow, rope)
    glow = screen(glow, gblur(rope, 5.0 * SS) * 0.4)
    core = np.maximum(core, clamp01(rope - gblur(rope, 1.2 * SS) * 0.6) * 0.35)
    for (p, r) in knots:
        core = np.maximum(core, pad.disc_field(p[0], p[1], r * 0.75, soft=0.8))
        glow = np.maximum(glow, pad.disc_field(p[0], p[1], r * 1.1, soft=1.5))
    for (p, r) in weights:
        glow = np.maximum(glow, pad.disc_field(p[0], p[1], r, soft=0.8))
        core = np.maximum(core, pad.disc_field(p[0] - r * 0.25, p[1] - r * 0.25, r * 0.45, soft=0.8))
    return finish(pad, glow, core, halo=((1.5, 0.4), (4.0, 0.3), (9.0, 0.18)), body_k=0.95, core_bloom=((1.2, 0.3), (3.0, 0.2)), win=4.0)


COLORS.setdefault("net", ((0.72, 1.0, 0.38), (1.0, 0.94, 0.62)))
sprite("rfx_jnet", "rotate_xyz", NT_S, NT_S, NT_N, 4, "net", zoom=0,
       desc="그물 (스네어 샷 — 카메라를 봄, 칸 가운데 = 그물 가운데): 0 묶인 다발 (꼬인 밧줄 · 매달린 추) → 1 ~ 3 펼쳐짐 (팔각 테두리 안의 사각 그물코 · "
       "매듭 · 가장자리 추 여덟) → 4 ~ 5 감김 (위로 모여 조인 자루 — 이어 돌림) → 6 끊어짐 · 7 흩어짐. 빛 칸 = 밧줄 몸 (거의 불투명), 심지 칸 = 매듭 · 추 · 밧줄 결")(net_frame)


# ------------------------------------------------------------ rfx_jfalcon (폭풍 매 — 가는 쪽으로 누움, 머리 = 오른쪽 · 날개 = 위아래)

FC_N = 6
FC_S = 384
# 장마다 (날개 펼침 몫 · 앞뒤로 젖힘): 0 · 1 내리침 · 2 접으며 올림 · 3 다시 펼침 · 4 ~ 5 날개를 접고 내리꽂음
FC_POSE = [(1.0, 0.1), (0.9, -0.05), (0.62, -0.35), (0.85, 0.05), (0.3, -1.4), (0.3, -1.4)]
# 위 날개 기본 꼴 (어깨 기준 — x + 앞 · y − 바깥): 낫 꼴 매 날개 (앞 테가 손목에서 앞으로 불룩했다가 손 끝이 뒤로 크게 휘어 뾰족 · 뒤 테는 곧게 몸으로 —
# 끝 쪽에 깃 톱니 셋)
FC_LEAD = [(0.0, 0.0), (14.0, -38.0), (12.0, -78.0), (-14.0, -116.0), (-64.0, -150.0)]
FC_TRAIL = [(-64.0, -150.0), (-58.0, -132.0), (-66.0, -126.0), (-56.0, -110.0), (-64.0, -104.0), (-54.0, -88.0), (-50.0, -60.0), (-42.0, -30.0), (-32.0, -4.0)]


def _fc_wing(span, sweep, side, dive=False):
    """날개 하나 (side −1 = 위 · +1 = 아래): 다각형 · 앞 테 · 뒤 테 (내리꽂을 때는 몸에 붙인 칼날 꼴)"""
    Sx, Sy = 214.0, 192.0 + side * 12.0
    if dive:
        def m(p):
            return (p[0], 192.0 + side * -p[1])
        lead = [m((226.0, 12.0)), m((196.0, 26.0)), m((150.0, 30.0)), m((86.0, 24.0))]
        trail = [m((86.0, 24.0)), m((120.0, 18.0)), m((166.0, 12.0)), m((200.0, 8.0))]
        return lead + trail[1:], lead, trail

    def tf(p):
        x, y = p[0] * 0.95, p[1] * 0.95
        r = -y / 150.0
        a = sweep * (0.3 + 0.7 * r) - (1 - span) * 0.8 * max(0.0, r - 0.45)
        y2 = y * (0.35 + 0.65 * span)
        ca, sa = math.cos(a), math.sin(a)
        xr = x * ca - y2 * sa
        yr = x * sa + y2 * ca
        return (Sx + xr, Sy + yr if side < 0 else Sy - yr)

    lead = [tf(p) for p in FC_LEAD]
    trail = [tf(p) for p in FC_TRAIL]
    return lead + trail[1:], lead, trail


def falcon_frame(f):
    S = FC_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    cy = S / 2
    span, sweep = FC_POSE[f]
    dive = f >= 4
    glow = pad.zeros()
    core = pad.zeros()
    sil = pad.zeros()
    bolts = []
    for side in (-1, 1):
        poly, lead, trail = _fc_wing(span, sweep, side, dive)
        sil = np.maximum(sil, pad.poly(poly))
        core = np.maximum(core, pad.stroke(lead, 3.0, 1.2, a0=1.0, a1=0.8, soft=0.4))
        core = np.maximum(core, pad.stroke(trail, 1.0, 0.6, a0=0.45, a1=0.25, soft=0.4))
        if not dive:
            # 번개 결: 날개 안을 달리는 갈짓자 둘 (어깨 → 날개 끝)
            rs = random.Random(9400 + f * 3 + (side + 1))
            for b in range(2):
                p0 = lead[1]
                p1 = trail[1 + b]
                pts = []
                for i in range(7):
                    t = i / 6
                    x = p0[0] + (p1[0] - p0[0]) * t + rs.uniform(-7, 7) * math.sin(math.pi * t)
                    y = p0[1] + (p1[1] - p0[1]) * t + rs.uniform(-7, 7) * math.sin(math.pi * t)
                    pts.append((x, y))
                bolts.append(pts)
    # 몸 · 머리 · 부리 (매: 작은 머리 · 짧은 꼬리 · 몸이 날렵)
    bl = 1.15 if dive else 1.0
    sil = np.maximum(sil, pad.ellipse(206, cy, 58 * bl, 17))
    hx = 270 + (12 if dive else 0)
    sil = np.maximum(sil, pad.disc_field(hx, cy, 15.0, soft=1.0))
    beak = [(hx + 10, cy - 6), (hx + 28, cy + 1), (hx + 22, cy + 6), (hx + 10, cy + 6)]
    sil = np.maximum(sil, pad.poly(beak))
    tw = 0.5 if dive else 1.0
    tail = [(154, cy - 10), (100, cy - 20 * tw), (92, cy), (100, cy + 20 * tw), (154, cy + 10)]
    sil = np.maximum(sil, pad.poly(tail))
    # 빛: 바람 결 (나는 쪽으로 길게 늘어진 결 — 앞에서 뒤로) 로 채운 몸 · 가장자리 빛 · 둘레 무리
    flow = clamp01(0.5 + 0.9 * (fbm(X / S, Y / S, seed=940 + (f % 4), octaves=3, px=2, py=16) - 0.5))
    edge = clamp01(sil - gblur(sil, 3.0 * SS)) * 2.0
    glow = np.maximum(glow, sil * (0.38 + 0.4 * flow))
    glow = np.maximum(glow, clamp01(edge) * 0.8)
    glow = screen(glow, gblur(sil, 10.0 * SS) * 0.35)
    # 심지: 번개 결 · 눈 · 부리 끝 · 꼬리 끝
    for pts in bolts:
        core = np.maximum(core, pad.stroke(pts, 1.6, 0.8, a0=0.9, a1=0.5, soft=0.4))
        glow = np.maximum(glow, pad.stroke(pts, 6.0, 3.0, a0=0.45, a1=0.2, soft=1.5))
    core = np.maximum(core, pad.disc_field(hx + 3, cy - 4, 3.4, soft=0.8))
    core = np.maximum(core, pad.stroke([(hx + 12, cy), (hx + 27, cy + 1)], 2.0, 0.8, a0=0.9, a1=0.6))
    if dive:
        # 내리꽂음: 뒤로 끌리는 바람 줄 · 5 = 머리 앞 번개 번쩍
        rs = random.Random(9460 + f)
        for k in range(11):
            y = cy + (k - 5) * 9 + rs.uniform(-3, 3)
            x0 = 100 - rs.uniform(0, 24)
            ln = rs.uniform(50, 84)
            glow = np.maximum(glow, pad.stroke([(x0 - ln, y), (x0, y)], 1.0, 5.0, a0=0.0, a1=0.65))
            core = np.maximum(core, pad.stroke([(x0 - ln * 0.6, y), (x0, y)], 0.4, 1.6, a0=0.0, a1=0.8))
        if f == 5:
            pts = [(hx + 26, cy - 30), (hx + 40, cy - 8), (hx + 30, cy - 2), (hx + 46, cy + 26)]
            core = np.maximum(core, pad.stroke(pts, 3.0, 1.6, a0=1.0, a1=0.8, soft=0.4))
            glow = np.maximum(glow, pad.radial(hx + 30, cy, 44.0, 1.6) * 0.8)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (10.0, 0.24)), body_k=0.9, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)


COLORS.setdefault("battlearcher", ((0.25, 0.75, 1.0), (1.0, 0.94, 0.5)))
sprite("rfx_jfalcon", "lookat_direction", FC_S, FC_S, FC_N, 3, "battlearcher", zoom=0, loop=True,
       desc="폭풍 매 (팰컨 다이브 — 가는 쪽으로 누움, 오른쪽 = 머리 · 날개는 위아래로 펼침, 몸 가운데 (206, 192)): 0 ~ 3 날갯짓 (이어 돌림 — 뾰족하고 긴 매 날개 · "
       "날개 안을 달리는 번개 결) → 4 ~ 5 날개를 접고 내리꽂음 (뒤로 끌리는 바람 줄 · 5 머리 앞 번개). 빛 칸 = 바람 결로 채운 몸 · 가장자리 빛, 심지 칸 = 번개 테 · "
       "날개 앞 테 · 눈 · 부리")(falcon_frame)


# ------------------------------------------------------------ 0.51.0 창술사 2차 (2026-11-06 — 스톰 랜서 · 드라군)

# ------------------------------------------------------------ rfx_jswirl (바람 소용돌이 — 카메라를 봄, 칸 가운데 = 소용돌이 눈)

SW_N = 8
SW_S = 256


def swirl_frame(f):
    S = SW_S
    pad = Pad(S, S)
    c = S / 2
    rr, th = polar(pad, c, c)
    R = S / 2 * 0.86
    # 장마다 (크기 몫 · 밝기 · 감긴 정도 b — 작을수록 많이 감김 · 돌린 각)
    scale = [0.42, 0.78, 1.0, 1.0, 1.0, 1.0, 1.06, 1.1][f]
    alpha = [0.85, 1.0, 1.0, 1.0, 1.0, 1.0, 0.75, 0.4][f]
    b = [0.34, 0.3, 0.28, 0.28, 0.28, 0.28, 0.42, 0.6][f]
    rot = f * (math.pi / 6)
    Rm = R * scale
    glow = pad.zeros()
    core = pad.zeros()
    r0 = 9.0
    # 바람 팔 셋 (로그 나선 — 바깥이 굵고 눈 쪽으로 가늘어짐 · 끝이 뾰족) + 사이 팔 셋 (옅게)
    for k in range(6):
        main = k % 2 == 0
        pts = []
        n = 40
        for i in range(n + 1):
            r = r0 + (Rm - r0) * (i / n) ** 0.85
            a = rot + k * math.pi / 3 + math.log(max(r, r0) / r0) / b
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        pts = pts[::-1]  # 바깥 → 눈
        w_out = (16.0 if main else 8.0) * (0.6 + 0.4 * scale)
        if f == 7:
            w_out *= 0.45

        def wfn(s, w_out=w_out):
            return w_out * (1 - s) ** 0.7 * np.clip(np.sin(np.pi * np.clip(s * 0.5 + 0.08, 0, 1)), 0.2, 1) + 1.0

        def afn(s, main=main):
            return (0.95 if main else 0.5) * np.clip(np.sin(np.pi * np.clip(s * 0.85 + 0.1, 0, 1)), 0, 1) ** 0.6

        glow = np.maximum(glow, pad.stroke(pts, w_out, wfn=wfn, afn=afn, soft=2.0) * alpha)
        if main:
            # 앞 테 (심지 — 가는 밝은 줄 · 팔의 바깥 쪽)
            edge = []
            for i in range(n + 1):
                r = r0 + (Rm - r0) * (i / n) ** 0.85
                a = rot + k * math.pi / 3 + math.log(max(r, r0) / r0) / b + 0.12
                edge.append((c + math.cos(a) * r, c + math.sin(a) * r))
            edge = edge[::-1]
            core = np.maximum(core, pad.stroke(edge, 2.6, 0.8, afn=lambda s: 0.9 * np.clip(np.sin(np.pi * np.clip(s * 0.9 + 0.05, 0, 1)), 0, 1), soft=0.6) * alpha)
    # 바람 줄 (둘레를 도는 짧은 호 — 빠르기)
    rs = random.Random(5100 + f)
    for q in range(9):
        r = Rm * rs.uniform(0.35, 1.0)
        a0 = rot + rs.uniform(0, TAU)
        span = rs.uniform(0.5, 1.1)
        arc = arc_pts(c, c, r, a0, a0 + span, n=18)
        glow = np.maximum(glow, pad.stroke(arc, 0.6, 2.6, a0=0.0, a1=0.55 * alpha))
        core = np.maximum(core, pad.stroke(arc, 0.3, 1.0, a0=0.0, a1=0.5 * alpha))
    # 눈: 가는 고리 · 가운데 옅은 빛 (0 = 번쩍)
    ring = gauss(rr - 13.0 * (0.7 + 0.3 * scale), 2.4)
    core = np.maximum(core, ring * 0.75 * alpha)
    glow = np.maximum(glow, np.exp(-0.5 * (rr / (26.0 * scale + 6)) ** 2) * 0.55 * alpha)
    if f == 0:
        core = np.maximum(core, star4(pad, c, c, 44.0, 1.0, rot=0.4, thin=0.1, diag=0.4))
    if f == 7:
        # 흩어지는 바람 조각
        for q in range(10):
            a = rot + q * TAU / 10 + rs.uniform(-0.2, 0.2)
            r = Rm * rs.uniform(0.7, 1.0)
            p1 = (c + math.cos(a) * r, c + math.sin(a) * r)
            p0 = (c + math.cos(a - 0.35) * r * 0.86, c + math.sin(a - 0.35) * r * 0.86)
            glow = np.maximum(glow, streak(pad, p0, p1, 3.0, 0.5))
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.34), (9.0, 0.2)), body_k=0.85, core_bloom=((1.3, 0.35), (3.5, 0.22)), win=4.0)


COLORS.setdefault("stormlancer", ((0.42, 1.0, 0.82), (0.95, 1.0, 0.92)))
sprite("rfx_jswirl", "rotate_xyz", SW_S, SW_S, SW_N, 4, "stormlancer", zoom=0,
       desc="바람 소용돌이 (스톰 랜서 — 카메라를 봄, 칸 가운데 = 눈 · 팔 끝 = 반 크기 × 0.86): 0 작게 번쩍이며 감김 → 1 펼쳐짐 → 2 ~ 5 세 갈래 바람 팔 + 사이 팔 셋 "
       "(이어 돌림 — 장마다 30 도씩 돎) · 둘레를 도는 바람 줄 → 6 풀리며 퍼짐 → 7 흩어지는 바람 조각. 빛 칸 = 바람 팔, 심지 칸 = 팔의 앞 테 · 눈 고리")(swirl_frame)


# ------------------------------------------------------------ rfx_jdragon (용의 혼 — 카메라를 봄, 앞뒤가 같은 꼴 · 날개를 편 용, 가슴 (224, 238))

DR_N = 8
DR_S = 448
# 장마다 (날개 각 — 어깨 기준 라디안, + 아래 · 펼침 몫 · 머리 (1 포효 · −1 내리꽂음) · 밝기): 0 생겨남 · 1 다 생김 · 2 ~ 5 날갯짓 (위 · 내려감 · 아래 · 올라감 —
# 이어 돌림) · 6 포효 (날개를 위로 활짝 · 머리를 젖히고 머리 위에 불) · 7 내리꽂음 (날개를 접어 몸 뒤로 · 머리가 아래 — 그린 뒤 위아래를 뒤집음)
DR_POSE = [(-0.15, 0.72, 0, 0.6), (-0.2, 0.92, 0, 1.0), (-0.5, 0.96, 0, 1.0), (-0.16, 0.86, 0, 1.0), (0.26, 0.9, 0, 1.0), (-0.08, 0.8, 0, 1.0),
           (-0.56, 1.0, 1, 1.0), (0.95, 0.5, -1, 1.0)]


def _dr_wing(side, ang, span, cx, cy):
    """날개 하나 (side −1 = 왼쪽 · +1 = 오른쪽): 막 다각형 (어깨 → 팔 → 손목 → 손가락 끝 셋 사이 오목한 물결 → 엉덩이) · 뼈 (휜 줄) · 손가락 끝"""
    sx, sy = cx + side * 18.0, cy - 34.0
    ca, sa = math.cos(ang), math.sin(ang)

    def tf(p):
        x, y = p[0] * span, p[1] * (0.82 + 0.18 * span)
        xr = x * ca - y * sa
        yr = x * sa + y * ca
        return (sx + side * xr, sy + yr)

    elbow = (52.0, -40.0)
    wrist = (96.0, -74.0)
    tips = [(176.0, -112.0), (182.0, -42.0), (160.0, 22.0), (104.0, 66.0)]
    hip = (8.0, 74.0)
    arm = [tf(p) for p in bez((0.0, 0.0), (26.0, -30.0), elbow, n=8)] + [tf(p) for p in bez(elbow, (74.0, -66.0), wrist, n=8)][1:]
    bones = [arm]
    for t in tips:
        mid = ((wrist[0] + t[0]) / 2 + (t[1] - wrist[1]) * 0.08, (wrist[1] + t[1]) / 2 - (t[0] - wrist[0]) * 0.08)
        bones.append([tf(p) for p in bez(wrist, mid, t, n=10)])
    edge = [tf(wrist), tf(tips[0])]
    seq = tips + [hip]
    for a, b in zip(seq[:-1], seq[1:]):
        # 오목한 물결: 두 끝 사이 가운데를 손목 쪽으로 당긴 곡선
        m = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        pull = 0.26
        ctrl = (m[0] + (wrist[0] - m[0]) * pull, m[1] + (wrist[1] - m[1]) * pull)
        edge += [tf(p) for p in bez(a, ctrl, b, n=10)][1:]
    poly = arm + edge[1:] + [tf((0.0, 40.0))]
    return poly, bones, [tf(t) for t in tips], edge[1:]


def dragon_frame(f):
    S = DR_S
    pad = Pad(S, S)
    ang, span, head, lum = DR_POSE[f]
    dive = head < 0
    cx, cy = S / 2, (210.0 if dive else 238.0)
    glow = pad.zeros()
    core = pad.zeros()
    memb = pad.zeros()
    bonesm = pad.zeros()
    trail = []
    for side in (-1, 1):
        poly, bones, tips, edge = _dr_wing(side, ang, span, cx, cy)
        memb = np.maximum(memb, pad.poly(poly))
        for bo in bones:
            bonesm = np.maximum(bonesm, pad.stroke(bo, 4.2, 1.8, soft=0.6))
            core = np.maximum(core, pad.stroke(bo, 2.6, 1.0, a0=0.95, a1=0.7, soft=0.4))
        for t in tips:
            core = np.maximum(core, pad.disc_field(t[0], t[1], 2.6, soft=0.8) * 0.85)
        trail.append(edge)
    # 몸: 가슴 · 배 (둥근 근육) · 목 (위로 가늘어짐) · 꼬리 (S 꼴 · 끝 가래 꼴) · 등 가시
    body = pad.ellipse(cx, cy - 10, 30, 44)
    body = np.maximum(body, pad.ellipse(cx, cy + 34, 22, 40))
    neck = bez((cx, cy - 40), (cx, cy - 82), (cx, cy - 116), n=12)
    body = np.maximum(body, pad.stroke(neck, 24.0, 15.0, soft=1.0))
    tail = bez((cx, cy + 60), (cx + 40, cy + 104), (cx - 34, cy + 140), n=24, p3=(cx + 10, cy + 176))
    body = np.maximum(body, pad.stroke(tail, 20.0, 3.0, soft=1.0))
    tip = tail[-1]
    spade = [(tip[0], tip[1] - 4), (tip[0] + 10, tip[1] + 6), (tip[0], tip[1] + 18), (tip[0] - 10, tip[1] + 6)]
    body = np.maximum(body, pad.poly(spade))
    hx, hy = cx, cy - 130 - (12.0 if head > 0 else 0.0)
    # 머리 (위에서 본 꼴 — 눈 없이: 길쭉한 주둥이 · 뒤로 휜 뿔 둘 · 볏)
    snout = [(hx - 14, hy + 12), (hx - 12, hy - 8), (hx - 6, hy - 28), (hx, hy - 34), (hx + 6, hy - 28), (hx + 12, hy - 8), (hx + 14, hy + 12), (hx, hy + 20)]
    body = np.maximum(body, pad.poly(snout))
    for s in (-1, 1):
        horn = bez((hx + s * 10, hy + 4), (hx + s * 28, hy + 8), (hx + s * 34, hy + 34), n=10)
        body = np.maximum(body, pad.stroke(horn, 7.5, 1.0, soft=0.6))
        core = np.maximum(core, pad.stroke(horn, 2.2, 0.6, a0=0.9, a1=0.6, soft=0.4))
        frill = [(hx + s * 14, hy + 10), (hx + s * 26, hy + 22), (hx + s * 16, hy + 22)]
        body = np.maximum(body, pad.poly(frill, 0.8))
    # 앞발 (짧게 · 발톱 셋)
    for s in (-1, 1):
        arm = bez((cx + s * 22, cy - 18), (cx + s * 42, cy + 2), (cx + s * 34, cy + 24), n=8)
        body = np.maximum(body, pad.stroke(arm, 9.0, 5.0, soft=0.6))
        for q in (-1, 0, 1):
            core = np.maximum(core, pad.stroke([(cx + s * 34, cy + 24), (cx + s * (34 + q * 6), cy + 35)], 2.2, 0.5, a0=0.8, a1=0.5))
    # 등 가시 (목 · 등 · 꼬리를 따라 작은 세모)
    spine = neck[::-1] + [(cx, cy - 10), (cx, cy + 30)] + tail
    for i in range(2, len(spine) - 3, 3):
        x, y = spine[i]
        body = np.maximum(body, pad.poly([(x - 4, y + 3), (x, y - 7), (x + 4, y + 3)], 0.9))
    sil = np.maximum(memb * 0.6, body)
    # 빛: 몸 · 막 (뼈 가까이 밝게 · 결) · 가장자리 빛 · 무리
    X, Y = pad.grid()
    vein = clamp01(0.55 + 0.7 * (fbm(X / S, Y / S, seed=7300 + f % 4, octaves=3, px=6, py=6) - 0.5))
    near = clamp01(gblur(bonesm, 9.0 * SS) * 2.2)
    edge = clamp01(sil - gblur(sil, 2.5 * SS)) * 2.2
    glow = np.maximum(glow, memb * (0.28 + 0.34 * near) * vein)
    glow = np.maximum(glow, bonesm * 0.85)
    glow = np.maximum(glow, body * 0.84)
    glow = np.maximum(glow, clamp01(edge) * 0.92)
    glow = screen(glow, gblur(sil, 12.0 * SS) * 0.42)
    # 심지: 등줄 · 이마 빛 · 날개 뒤 테 (불꽃처럼 일렁이는 가는 줄)
    core = np.maximum(core, pad.stroke(spine, 3.0, 0.8, a0=0.85, a1=0.45, soft=0.6))
    core = np.maximum(core, pad.disc_field(hx, hy - 6, 4.0, soft=1.4) * 0.95)
    for e in trail:
        core = np.maximum(core, pad.stroke(e, 1.6, 1.6, a0=0.55, a1=0.55, soft=0.5))
    if head > 0:
        # 포효: 입 앞 (머리 위) 에서 터지는 불
        mx, my = hx, hy - 44
        glow = np.maximum(glow, pad.radial(mx, my, 50.0, 1.4) * 0.95)
        core = np.maximum(core, pad.radial(mx, my, 24.0, 1.2))
        core = np.maximum(core, star4(pad, mx, my, 58.0, 0.9, rot=0.3, thin=0.1, diag=0.4))
    if f == 0:
        # 생겨남: 둘레에서 감겨 드는 금빛 기운
        rr, th = polar(pad, cx, cy)
        sw = np.exp(-0.5 * (np.angle(np.exp(1j * (th - np.log(np.maximum(rr, 10) / 10) / 0.35))) / 0.5) ** 2) * clamp01(1 - rr / 170)
        glow = np.maximum(glow * 0.6, sw * 0.7)
        core = core * 0.5
    if dive:
        # 내리꽂음: 위아래를 뒤집음 (머리가 아래 · 접은 날개 끝이 위) → 위로 끌리는 불꽃 줄
        glow = glow[::-1, :].copy()
        core = core[::-1, :].copy()
        rs = random.Random(7350)
        for k in range(14):
            x = cx + (k - 6.5) * 11 + rs.uniform(-4, 4)
            y0 = 150 - rs.uniform(0, 24)
            ln = rs.uniform(40, 90)
            glow = np.maximum(glow, pad.stroke([(x, y0 - ln), (x, y0)], 1.0, 6.0, a0=0.0, a1=0.6))
            core = np.maximum(core, pad.stroke([(x, y0 - ln * 0.6), (x, y0)], 0.4, 2.0, a0=0.0, a1=0.7))
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (11.0, 0.24)), body_k=0.88, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)


COLORS.setdefault("dragoon", ((1.0, 0.62, 0.16), (1.0, 0.95, 0.72)))
sprite("rfx_jdragon", "rotate_xyz", DR_S, DR_S, DR_N, 4, "dragoon", zoom=0,
       desc="용의 혼 (드라군 — 카메라를 봄, 앞뒤가 같은 꼴: 날개를 편 용 · 눈 없이 뿔 · 이마 빛, 가슴 (224, 238)): 0 둘레에서 감겨 드는 기운 속에 생겨남 → 1 다 생김 → "
       "2 ~ 5 날갯짓 (위 · 내려감 · 아래 · 올라감 — 이어 돌림) → 6 포효 (날개를 위로 활짝 · 머리를 젖히고 머리 위에 불) → 7 내리꽂음 (날개를 접어 몸 뒤로 · 머리가 "
       "아래 · 위로 끌리는 불꽃 줄). 빛 칸 = 몸 · 막 (옅은 결) · 가장자리 빛, 심지 칸 = 날개 뼈 · 등줄 · 뿔 · 발톱 · 이마 · 불")(dragon_frame)


# ------------------------------------------------------------ rfx_jfire (불꽃 덩이 — 카메라를 봄, 칸 가운데 = 불의 가운데 · 위 = 불이 솟는 쪽)

FI_N = 8
FI_S = 256


def _flame_field(X, Y, cx, cy, r, f, seed, tall=1.0):
    """불꽃 하나의 밀도 (0 ~ 1): 아래가 둥글고 위로 뾰족 · 노이즈로 일렁이는 가장자리 (위로 흐름)"""
    nx = (X - cx) / max(1.0, r)
    ny = (Y - cy) / max(1.0, r)
    turb = fbm(X / FI_S * 3.0, Y / FI_S * 3.0 + f * 0.42, seed=seed, octaves=4, px=6, py=6) - 0.5
    turb2 = fbm(X / FI_S * 6.0, Y / FI_S * 6.0 + f * 0.7, seed=seed + 7, octaves=3, px=12, py=12) - 0.5
    up = np.clip(-ny, 0, None)
    x = nx + turb * 0.8 * (0.35 + up)
    y = ny + turb2 * 0.34
    # 폭: 아래 (y 0.55) 둥근 끝 · 가운데 넓음 · 위 (y −tall) 뾰족
    top = -1.05 * tall
    t = clip01((y - top) / (0.6 - top))
    w = 0.78 * np.sqrt(t) * np.clip((0.62 - y) / 0.3, 0, 1) ** 0.5
    return clamp01((w - np.abs(x)) * 3.2)


def clip01(a):
    return np.clip(a, 0.0, 1.0)


def fire_frame(f):
    S = FI_S
    pad = Pad(S, S)
    c = S / 2
    X, Y = pad.grid()
    R = S / 2 * 0.8
    size = [0.34, 0.66, 0.9, 1.0, 1.0, 0.98, 0.94, 0.9][f]
    hole = [0.0, 0.0, 0.0, 0.0, 0.16, 0.36, 0.54, 0.72][f]
    lum = [1.0, 1.0, 1.0, 1.0, 0.9, 0.76, 0.56, 0.38][f]
    rise = [0.0, 0.0, 0.0, 0.03, 0.08, 0.16, 0.24, 0.32][f]
    r = R * size
    cy = c + r * 0.3 - rise * R
    # 불꽃 셋 (가운데 높게 · 양옆 낮게 — 겹쳐 일렁이는 불덩이)
    body = _flame_field(X, Y, c, cy, r, f, 8100, tall=1.15)
    body = np.maximum(body, _flame_field(X, Y, c - r * 0.42, cy + r * 0.12, r * 0.72, f, 8120, tall=0.9) * 0.9)
    body = np.maximum(body, _flame_field(X, Y, c + r * 0.4, cy + r * 0.1, r * 0.7, f, 8140, tall=0.95) * 0.9)
    mask = 1.0
    if hole > 0:
        nz = fbm(X / S * 5.0, Y / S * 5.0 + f * 0.6, seed=8160, octaves=3, px=10, py=10)
        mask = clamp01((nz - hole) * 3.4 + 0.15)
        body = body * mask
    glow = body * 0.95
    # 심지: 안쪽 뜨거운 불 (작은 같은 꼴 — 아래 가운데 · 흩어질 때는 같은 구멍 · 빨리 식음)
    hot = _flame_field(X, Y, c, cy + r * 0.12, r * (0.55 if f < 4 else 0.42), f, 8200, tall=1.0)
    core = hot * mask * (0.95 if f < 4 else max(0.0, 0.95 - (f - 3) * 0.3))
    rs = random.Random(8180 + f)
    if f == 0:
        core = np.maximum(core, star4(pad, c, c + 6, 48.0, 1.0, rot=0.2, thin=0.12, diag=0.4))
        glow = np.maximum(glow, pad.radial(c, c + 6, 60.0, 1.2))
    if f >= 4:
        # 불티 (위로 흩어짐)
        for k in range(10 + 3 * (f - 4)):
            x = c + rs.uniform(-0.8, 0.8) * r
            y = cy - rs.uniform(0.2, 1.4) * r
            rad = rs.uniform(1.2, 2.4)
            core = np.maximum(core, pad.disc_field(x, y, rad, soft=0.6) * 0.85)
            glow = np.maximum(glow, pad.disc_field(x, y, rad * 2.2, soft=1.5) * 0.45)
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.42), (4.0, 0.34), (9.0, 0.22)), body_k=0.92, core_bloom=((1.3, 0.3), (3.5, 0.2)), win=4.0)


COLORS.setdefault("dragonfire", ((1.0, 0.42, 0.12), (1.0, 0.9, 0.55)))
sprite("rfx_jfire", "rotate_xyz", FI_S, FI_S, FI_N, 4, "dragonfire", zoom=0,
       desc="불꽃 덩이 (드라군 — 용의 불길 · 불기둥 · 내리꽂은 자리, 카메라를 봄 · 위 = 불이 솟는 쪽): 0 작게 번쩍이며 불붙음 → 1 ~ 3 불꽃 셋이 겹쳐 일렁이며 부풀어 "
       "오름 (가장자리가 위로 흐름) → 4 ~ 6 구멍이 뚫리며 위로 흩어짐 · 불티 → 7 불티. 빛 칸 = 불덩이, 심지 칸 = 안쪽 뜨거운 불 · 불티")(fire_frame)



# ------------------------------------------------------------ 0.51.0 사신 2차 (2026-11-06 — 소울 리퍼 · 엑시큐셔너)

# ------------------------------------------------------------ rfx_jlantern (영혼 등불 — 카메라를 봄, 위 = 매단 고리 · 등불 몸 가운데 (96, 100))

LT_N = 8
LT_S = 192


def _soul_flame(X, Y, cx, cy, r, f, seed, tall=1.2, wob=0.35):
    """곧게 선 영혼 불꽃 하나의 밀도 (0 ~ 1): 아래가 둥글고 위로 뾰족 · 가장자리가 조금 일렁임 (rfx_jfire 보다 덜 휨)"""
    nx = (X - cx) / max(1.0, r)
    ny = (Y - cy) / max(1.0, r)
    turb = fbm(X / LT_S * 3.0, Y / LT_S * 3.0 + f * 0.45, seed=seed, octaves=3, px=6, py=6) - 0.5
    up = np.clip(-ny, 0, None)
    x = nx + turb * wob * (0.3 + up) + np.sin(f * 1.3 + ny * 2.0) * 0.06 * up
    top = -1.05 * tall
    t = np.clip((ny - top) / (0.62 - top), 0, 1)
    w = 0.8 * np.sqrt(t) * np.clip((0.66 - ny) / 0.3, 0, 1) ** 0.5
    return clamp01((w - np.abs(x)) * 3.4)


def lantern_frame(f):
    S = LT_S
    pad = Pad(S, S)
    c = S / 2
    X, Y = pad.grid()
    lum = [0.55, 1.0, 1.0, 1.0, 1.0, 1.0, 0.72, 0.38][f]
    fl = [0.45, 1.0, 0.9, 1.06, 0.96, 1.25, 0.5, 0.0][f]
    pane = [0.35, 0.8, 0.72, 0.88, 0.76, 1.0, 0.45, 0.12][f]
    glow = pad.zeros()
    core = pad.zeros()
    # 고리 · 짧은 사슬 (위)
    rr = np.sqrt((X - c) ** 2 + (Y - 18.0) ** 2)
    glow = np.maximum(glow, gauss(rr - 7.0, 1.4) * 0.85)
    core = np.maximum(core, gauss(rr - 7.0, 0.6) * 0.6)
    glow = np.maximum(glow, pad.stroke([(c, 25.0), (c, 35.0)], 2.6, soft=0.6) * 0.85)
    # 지붕 (뾰족한 덮개 — 옅게 채움 · 테) · 꼭지
    roof = [(c - 32, 56.0), (c - 16, 44.0), (c, 34.0), (c + 16, 44.0), (c + 32, 56.0)]
    glow = np.maximum(glow, pad.poly(roof + [(c + 32, 57.0), (c - 32, 57.0)]) * 0.42)
    glow = np.maximum(glow, pad.stroke(roof + [roof[0]], 3.0, soft=0.7) * 0.9)
    core = np.maximum(core, pad.stroke(roof, 1.1, soft=0.4) * 0.55)
    # 띠 (위 · 아래) · 틀 (바깥 기둥 둘 · 안 기둥 셋 — 가운데는 불에 가려 옅게)
    for y0, y1 in ((56.0, 63.0), (131.0, 138.0)):
        band = [(c - 31, y0), (c + 31, y0), (c + 31, y1), (c - 31, y1)]
        glow = np.maximum(glow, pad.poly(band) * 0.62)
        core = np.maximum(core, pad.stroke([(c - 31, y0 + 0.8), (c + 31, y0 + 0.8)], 1.0, soft=0.4) * 0.5)
    for x, a, w in ((c - 28, 0.95, 3.4), (c + 28, 0.95, 3.4), (c - 14, 0.4, 1.8), (c + 14, 0.4, 1.8), (c, 0.3, 1.6)):
        top = (x, 63.0)
        bot = (x + (1.5 if x < c else -1.5 if x > c else 0), 131.0)
        glow = np.maximum(glow, pad.stroke([top, bot], w, soft=0.6) * a)
        if a > 0.9:
            core = np.maximum(core, pad.stroke([top, bot], 1.0, soft=0.4) * 0.45)
    # 받침 (거꾸로 사다리꼴) · 아래 꼭지
    base = [(c - 27, 138.0), (c + 27, 138.0), (c + 16, 148.0), (c - 16, 148.0)]
    glow = np.maximum(glow, pad.poly(base) * 0.55)
    glow = np.maximum(glow, pad.stroke(base + [base[0]], 2.4, soft=0.6) * 0.8)
    glow = np.maximum(glow, pad.disc_field(c, 153.0, 3.4, soft=0.8) * 0.8)
    # 유리 (불빛이 새어 나옴 — 불 쪽이 밝게)
    glass = pad.poly([(c - 26, 63.0), (c + 26, 63.0), (c + 25, 131.0), (c - 25, 131.0)])
    glow = np.maximum(glow, glass * (0.16 + 0.55 * pad.radial(c, 104.0, 52.0, 1.2)) * pane)
    # 영혼 불꽃 (안 — 일렁임 · 눈 둘의 혼) — 0 작게 · 5 크게 · 7 꺼짐
    if fl > 0:
        r = 27.0 * fl
        cy = 108.0
        body = _soul_flame(X, Y, c, cy, r, f % 5, 9100, tall=1.3)
        body = np.maximum(body, _soul_flame(X, Y, c - r * 0.4, cy + r * 0.2, r * 0.6, f % 5, 9120, tall=0.95) * 0.8)
        body = np.maximum(body, _soul_flame(X, Y, c + r * 0.4, cy + r * 0.18, r * 0.58, f % 5, 9140, tall=1.0) * 0.8)
        body = body * glass
        hot = _soul_flame(X, Y, c, cy + r * 0.08, r * 0.78, f % 5, 9200, tall=1.1, wob=0.2) * glass
        # 혼의 얼굴: 눈 둘 · 벌린 입 (뜨거운 속에 뚫린 구멍)
        eyes = np.maximum(pad.disc_field(c - 6.0 * fl, cy - 2.0 * fl, 3.0 * fl, soft=0.7), pad.disc_field(c + 6.0 * fl, cy - 2.0 * fl, 3.0 * fl, soft=0.7))
        mouth = pad.ellipse(c, cy + 8.0 * fl, 2.6 * fl, 4.0 * fl)
        glow = np.maximum(glow, body * 0.95)
        core = np.maximum(core, clamp01(hot * 1.05 - np.maximum(eyes, mouth) * 0.95))
    rs = random.Random(9300 + f)
    if 1 <= f <= 4:
        # 안에서 오르는 작은 불티
        for k in range(4):
            x = c + rs.uniform(-18, 18)
            y = 126.0 - ((f * 13 + k * 23) % 56)
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.1, 1.8), soft=0.6) * 0.75 * glass)
    if f == 0:
        core = np.maximum(core, star4(pad, c, 104.0, 30.0, 0.9, rot=0.3, thin=0.12, diag=0.4))
    if f == 5:
        # 번쩍: 유리 틈으로 뻗는 빛살 · 큰 반짝임
        core = np.maximum(core, star4(pad, c, 104.0, 56.0, 1.0, rot=0.0, thin=0.1, diag=0.45))
        for k in range(10):
            a = k * TAU / 10 + 0.2
            p0 = (c + math.cos(a) * 34, 104.0 + math.sin(a) * 40)
            p1 = (c + math.cos(a) * 86, 104.0 + math.sin(a) * 86)
            glow = np.maximum(glow, pad.stroke([p0, p1], 5.0, 0.6, a0=0.7, a1=0.0, soft=1.5))
    if f >= 6:
        # 꺼짐: 지붕 위로 오르는 연기 줄 셋
        for k in range(3):
            pts = []
            for i in range(17):
                t = i / 16
                y = 32.0 - t * 30.0 - (f - 6) * 6.0
                x = c + (k - 1) * 9.0 + math.sin(t * 5.0 + k * 1.7 + f) * 6.0 * t
                pts.append((x, y))
            glow = np.maximum(glow, pad.stroke(pts, 4.5, 1.5, a0=0.55, a1=0.0, soft=2.0))
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (10.0, 0.24)), body_k=0.9, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)


COLORS.setdefault("soulreaper", ((0.3, 0.85, 1.0), (0.85, 0.97, 1.0)))
sprite("rfx_jlantern", "rotate_xyz", LT_S, LT_S, LT_N, 4, "soulreaper", zoom=2,
       desc="영혼 등불 (소울 리퍼 랜턴 오브 소울즈 — 카메라를 봄, 위 = 매단 고리 · 등불 몸 가운데 (96, 100)): 0 불이 붙음 (작은 불 · 반짝임) → 1 ~ 4 흔들리며 타오름 (이어 돌림 — "
       "뾰족한 지붕 · 띠 · 기둥 틀 · 유리로 새는 빛 · 눈 둘의 영혼 불꽃 · 오르는 불티) → 5 번쩍 (사슬을 던짐 — 빛살) → 6 ~ 7 꺼지며 지붕 위로 연기. 빛 칸 = 틀 · 유리 빛 · "
       "불꽃, 심지 칸 = 테 · 뜨거운 속")(lantern_frame)


# ------------------------------------------------------------ rfx_jguillotine (망령 단두대 날 — 세워 카메라 쪽으로 돎, 아래 = 비스듬한 날 · 날 가운데 약 (96, 140))

GL_N = 8
GL_W, GL_H = 192, 256


def guillotine_frame(f):
    W, H = GL_W, GL_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    lum = [0.62, 1.0, 1.0, 1.0, 1.0, 1.0, 0.72, 0.38][f]
    st = 1.12 if f == 4 else 1.0
    top, bar_h = 78.0, 26.0
    x0, x1 = 44.0, 148.0
    bt = top + bar_h
    edge_r = bt + 46.0 * st
    edge_l = bt + 100.0 * st
    glow = pad.zeros()
    core = pad.zeros()
    # 무게 판 (위 가로 쇳덩이 — 채움 · 테 · 못 넷 · 가운데 해골 무늬)
    bar = [(x0 - 6, top), (x1 + 6, top), (x1 + 6, bt), (x0 - 6, bt)]
    glow = np.maximum(glow, pad.poly(bar) * 0.6)
    glow = np.maximum(glow, pad.stroke(bar + [bar[0]], 3.0, soft=0.7) * 0.9)
    for x in (x0 + 2, x0 + 22, x1 - 22, x1 - 2):
        core = np.maximum(core, pad.disc_field(x, top + bar_h / 2, 2.6, soft=0.6) * 0.75)
    skull = np.maximum(pad.disc_field(cx, top + 11.0, 8.0, soft=0.7), pad.poly([(cx - 5, top + 15), (cx + 5, top + 15), (cx + 4, top + 22), (cx - 4, top + 22)]))
    holes = np.maximum(pad.disc_field(cx - 3.2, top + 11.0, 2.2, soft=0.5), pad.disc_field(cx + 3.2, top + 11.0, 2.2, soft=0.5))
    core = np.maximum(core, clamp01(skull - holes) * 0.7)
    # 날 (오른쪽이 높고 왼쪽이 낮은 비스듬한 날 — 망령이라 옅게 채우고 날 쪽이 밝게)
    blade = [(x0, bt), (x1, bt), (x1, edge_r), (x0, edge_l)]
    bm = pad.poly(blade)
    ex, ey = x0 - x1, edge_l - edge_r
    el = math.hypot(ex, ey)
    nx, ny = -ey / el, ex / el  # 날에 수직 (위쪽)
    if ny > 0:
        nx, ny = -nx, -ny
    dist_edge = np.abs((X - x1) * nx + (Y - edge_r) * ny)
    glow = np.maximum(glow, bm * (0.3 + 0.55 * np.exp(-dist_edge / 26.0)))
    glow = np.maximum(glow, pad.stroke(blade + [blade[0]], 2.6, soft=0.7) * 0.8)
    # 날 끝 (심지 — 굵은 밝은 줄 · 위의 가는 날 결)
    edge = [(x1, edge_r), (x0, edge_l)]
    core = np.maximum(core, pad.stroke(edge, 3.6, soft=0.6) * 1.0)
    glow = np.maximum(glow, pad.stroke(edge, 9.0, soft=3.0) * 0.8)
    bevel = [(x1 + nx * 11, edge_r + ny * 11), (x0 + nx * 11, edge_l + ny * 11)]
    core = np.maximum(core, pad.stroke(bevel, 1.2, soft=0.5) * 0.4 * bm)
    # 쇠의 결: 날과 나란한 비스듬한 반사 띠 둘 (위가 어둡고 날 쪽으로 밝게 — 깃발이 아니라 쇠 날로 보이게)
    for off, a_, wd in ((24.0, 0.32, 7.0), (40.0, 0.2, 4.0)):
        band = [(x1 + nx * off, edge_r + ny * off), (x0 + nx * off, edge_l + ny * off)]
        glow = screen(glow, pad.stroke(band, wd, soft=2.5, taper=0.6) * a_ * 1.4 * bm)
    # 사슬 둘 (판 위 모서리에서 위로 — 위로 갈수록 흐림), 떨어지는 장면엔 없음
    if f != 4 and f < 6:
        ca = 0.35 if f == 0 else 0.8
        for sx, ex2 in ((x0 + 8, x0 + 20), (x1 - 8, x1 - 20)):
            for i in range(7):
                t0 = i / 7
                y = top - 6.0 - i * 11.0
                x = sx + (ex2 - sx) * t0
                fade = 1.0 - t0 * 0.85
                ring = gauss(np.sqrt(((X - x) / 3.4) ** 2 + ((Y - y) / 5.6) ** 2) - 1.0, 0.28)
                glow = np.maximum(glow, ring * 0.8 * ca * fade)
                core = np.maximum(core, ring * 0.35 * ca * fade)
    # 망령 연기: 날 둘레 · 날 끝 아래로 흘러내리는 자락
    nz = fbm(X / W * 5.0, Y / H * 5.0 + f * 0.35, seed=9500, octaves=4, px=10, py=10)
    near = np.exp(-np.maximum(0.0, dist_edge - 2.0) / 14.0) * clamp01((Y - edge_r + 6.0) / 12.0)
    glow = np.maximum(glow, clamp01((nz - 0.5) * 2.6) * near * 0.55)
    rs = random.Random(9600 + f)
    for k in range(5):
        t = (k + 0.5) / 5
        px = x1 + (x0 - x1) * t
        py = edge_r + (edge_l - edge_r) * t
        L = rs.uniform(14, 30) * (1.4 if f >= 4 else 1.0)
        pts = [(px + math.sin(i * 0.7 + k + f) * 3.0, py + 3 + L * i / 10) for i in range(11)]
        glow = np.maximum(glow, pad.stroke(pts, 5.0, 0.8, a0=0.45, a1=0.0, soft=1.6))
    if f == 0:
        hole = clamp01((fbm(X / W * 6.0, Y / H * 6.0, seed=9550, octaves=3, px=12, py=12) - 0.5) * 3.0 + 0.5)
        glow = glow * (0.4 + 0.6 * hole)
        core = core * (0.3 + 0.7 * hole)
        core = np.maximum(core, star4(pad, cx, 130.0, 34.0, 0.8, rot=0.3, thin=0.12, diag=0.4))
    if f in (1, 2, 3):
        # 번득임: 날 끝을 따라 옮겨 가는 반짝임 (1 = 오른 끝 · 2 = 1/3 · 3 = 2/3)
        t = [0.04, 0.35, 0.7][f - 1]
        gx = x1 + (x0 - x1) * t
        gy = edge_r + (edge_l - edge_r) * t
        core = np.maximum(core, star4(pad, gx, gy, 22.0 if f == 1 else 18.0, 1.0, rot=0.2, thin=0.1, diag=0.4))
    if f == 4:
        # 떨어짐: 위로 끌리는 바람 줄
        for k in range(9):
            x = x0 - 2 + (x1 - x0 + 4) * k / 8
            L = rs.uniform(40, 70)
            glow = np.maximum(glow, pad.stroke([(x, top - 4 - L), (x, top - 2)], 0.8, 4.0, a0=0.0, a1=0.55, soft=1.2))
            core = np.maximum(core, pad.stroke([(x, top - 4 - L * 0.6), (x, top - 2)], 0.4, 1.4, a0=0.0, a1=0.4, soft=0.5))
    if f == 5:
        # 내리침: 날 끝 섬광 · 옆으로 튀는 불티 · 아래로 퍼지는 충격 줄
        mx, my = (x0 + x1) / 2, (edge_r + edge_l) / 2
        core = np.maximum(core, star4(pad, mx, my + 4, 64.0, 1.0, rot=0.0, thin=0.09, diag=0.35))
        glow = np.maximum(glow, pad.radial(mx, my + 6, 70.0, 1.6) * 0.6)
        for k in range(12):
            a = rs.uniform(-0.9, 0.9) + (0 if k % 2 == 0 else math.pi)
            p0 = (mx + math.cos(a) * 18, my + 8 + math.sin(a) * 6)
            p1 = (mx + math.cos(a) * rs.uniform(50, 80), my + 8 + abs(math.sin(a)) * rs.uniform(10, 40))
            core = np.maximum(core, streak(pad, p0, p1, 2.6, 0.9))
    if f >= 6:
        cut = 0.38 if f == 6 else 0.2
        hole = clamp01((fbm(X / W * 5.5, Y / H * 5.5 + 0.3, seed=9580, octaves=4, px=11, py=11) - cut) * 3.2)
        rise = fbm(X / W * 4.0, Y / H * 4.0 + f * 0.5, seed=9590, octaves=3, px=8, py=8)
        glow = np.maximum(glow * (1 - hole), clamp01((rise - 0.55) * 2.0) * bm * 0.5)
        core = core * (1 - hole) * 0.7
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.34), (9.0, 0.22)), body_k=0.88, core_bloom=((1.3, 0.38), (3.5, 0.24)), win=4.0)


COLORS.setdefault("executioner", ((0.62, 0.22, 0.6), (1.0, 0.93, 0.74)))
sprite("rfx_jguillotine", "lookat_y", GL_W, GL_H, GL_N, 4, "executioner", zoom=2,
       desc="망령 단두대 날 (엑시큐셔너 길로틴 — 세워 카메라 쪽으로 돎, 아래 = 비스듬한 날): 0 연기가 모여 생겨남 → 1 다 생김 (오른 끝 번득임) → 2 ~ 3 매달림 (이어 돌림 — "
       "사슬 둘 · 날 끝을 따라 옮겨 가는 번득임 · 날 아래로 흘러내리는 망령 자락) → 4 떨어짐 (위로 끌리는 바람 줄) → 5 내리침 (날 끝 섬광 · 튀는 불티) → 6 ~ 7 연기로 "
       "흩어짐. 빛 칸 = 쇳덩이 · 옅은 날 · 망령 연기, 심지 칸 = 날 끝 · 못 · 해골 무늬")(guillotine_frame)


# ------------------------------------------------------------ rfx_jxmark (처형 표식 — 땅에 누움, 칸 가운데 = X 가운데 · 획 끝 = 반 크기 × 0.9)

XM_N = 8
XM_S = 384


def _brush(pad, X, Y, p0, p1, w, frac, seed):
    """붓 획 p0 → p1 (가운데 굵기 w · 앞 frac 만): 양 끝이 가늘고 (시작이 조금 굵게) 가장자리가 거친 획 · 결 (심지)"""
    n = 48
    if frac <= 0.02:
        return pad.zeros(), pad.zeros()
    pts = [(p0[0] + (p1[0] - p0[0]) * i / n, p0[1] + (p1[1] - p0[1]) * i / n) for i in range(n + 1)]
    pts = cut_polyline(pts, frac)
    if len(pts) < 2:
        return pad.zeros(), pad.zeros()

    def wfn(s):
        return w * (0.1 + 0.9 * np.clip(np.sin(np.pi * np.clip(s * 0.94 + 0.03, 0, 1)), 0, 1) ** 0.55) * (1.08 - 0.16 * s)

    body = pad.stroke(pts, w, wfn=wfn, soft=3.0)
    # 거친 가장자리 (속은 꽉 차게 — 가장자리 띠에서만 노이즈로 깎음)
    nz = fbm(X / XM_S * 10.0, Y / XM_S * 10.0, seed=seed, octaves=3, px=20, py=20)
    rough = clamp01((body - 0.42 + (nz - 0.5) * 0.45) * 5.0 + 0.5)
    # 결: 획을 따라 가는 줄 셋 (붓털)
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)
    ox, oy = -dy / L, dx / L
    vein = pad.zeros()
    for k, off in enumerate((-0.22, 0.0, 0.24)):
        q = [(x + ox * w * off, y + oy * w * off) for x, y in pts]
        q = q[int(len(q) * (0.06 + 0.05 * k)):max(2, int(len(q) * (0.94 - 0.07 * k)))]
        if len(q) >= 2:
            vein = np.maximum(vein, pad.stroke(q, w * (0.1 if k == 1 else 0.06), soft=1.0, afn=lambda s: 0.9 * np.clip(np.sin(np.pi * np.clip(s, 0, 1)), 0, 1) ** 0.4))
    return rough, vein * rough


def xmark_frame(f):
    S = XM_S
    pad = Pad(S, S)
    c = S / 2
    X, Y = pad.grid()
    R0 = S / 2 * 0.9
    k = R0 * math.cos(math.pi / 4)
    lum = [0.9, 1.0, 1.1, 0.85, 1.0, 0.9, 1.15, 0.5][f]
    fa = [0.62, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    fb = [0.0, 0.55, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    w = 46.0
    ga, ca = _brush(pad, X, Y, (c - k, c - k), (c + k, c + k), w, fa, 9700)
    gb, cb = _brush(pad, X, Y, (c + k, c - k), (c - k, c + k), w * 0.92, fb, 9710)
    glow = np.maximum(ga, gb) * 0.95
    core = np.maximum(ca, cb)
    # 처형의 원 (가는 테 · 여덟 눈금)
    rr, th = polar(pad, c, c)
    ring_a = [0.25, 0.45, 0.8, 0.6, 0.7, 0.62, 0.95, 0.35][f]
    glow = np.maximum(glow, gauss(rr - R0 * 1.02, 2.6) * ring_a)
    core = np.maximum(core, gauss(rr - R0 * 1.02, 1.0) * ring_a * 0.6)
    for q in range(8):
        a = q * TAU / 8 + TAU / 16
        p0 = (c + math.cos(a) * R0 * 0.9, c + math.sin(a) * R0 * 0.9)
        p1 = (c + math.cos(a) * R0 * 1.0, c + math.sin(a) * R0 * 1.0)
        glow = np.maximum(glow, pad.stroke([p0, p1], 4.0, soft=1.2) * ring_a * 0.8)
    # 튄 먹 (획 끝 둘레 작은 방울)
    rs = random.Random(9800)
    if f >= 1:
        for q in range(14):
            ex, ey = [(c - k, c - k), (c + k, c + k), (c + k, c - k), (c - k, c + k)][q % 4]
            if q % 4 >= 2 and fb < 1:
                continue
            a = rs.uniform(0, TAU)
            d = rs.uniform(10, 40)
            glow = np.maximum(glow, pad.disc_field(ex * 0.9 + c * 0.1 + math.cos(a) * d, ey * 0.9 + c * 0.1 + math.sin(a) * d, rs.uniform(2.0, 5.0), soft=1.0) * 0.8)
    rs2 = random.Random(9810 + f)
    if 3 <= f <= 5 or f == 7:
        # 일렁이는 불티 (획 둘레에서 바깥으로)
        for q in range(16):
            t = rs2.uniform(0.1, 0.9)
            first = q % 2 == 0
            x = (c - k + 2 * k * t) if first else (c + k - 2 * k * t)
            y = c - k + 2 * k * t
            a = rs2.uniform(0, TAU)
            d = rs2.uniform(20, 60)
            core = np.maximum(core, pad.disc_field(x + math.cos(a) * d, y + math.sin(a) * d, rs2.uniform(1.6, 3.0), soft=0.8) * 0.7)
    if f == 0:
        core = np.maximum(core, star4(pad, c - k + 2 * k * 0.62, c - k + 2 * k * 0.62, 40.0, 0.9, rot=0.3, thin=0.1, diag=0.4))
    if f == 2:
        core = np.maximum(core, star4(pad, c, c, 70.0, 1.0, rot=0.0, thin=0.1, diag=0.45))
    if f == 6:
        # 벰: 획 가운데 굵은 흰 줄 · 큰 섬광 · 퍼지는 빛살
        for p0, p1 in (((c - k, c - k), (c + k, c + k)), ((c + k, c - k), (c - k, c + k))):
            core = np.maximum(core, pad.stroke([p0, p1], 9.0, taper=0.8, soft=2.0))
        core = np.maximum(core, star4(pad, c, c, 120.0, 1.0, rot=0.0, thin=0.08, diag=0.4))
        for q in range(12):
            a = q * TAU / 12 + 0.13
            glow = np.maximum(glow, pad.stroke([(c + math.cos(a) * 40, c + math.sin(a) * 40), (c + math.cos(a) * R0 * 0.95, c + math.sin(a) * R0 * 0.95)], 7.0, 0.8,
                                               a0=0.6, a1=0.0, soft=2.0))
    if f == 7:
        hole = clamp01((fbm(X / S * 6.0, Y / S * 6.0, seed=9820, octaves=4, px=12, py=12) - 0.42) * 3.2)
        glow = glow * (1 - hole)
        core = core * (1 - hole) * 0.8
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((2.0, 0.45), (5.0, 0.34), (12.0, 0.22)), body_k=0.9, core_bloom=((1.5, 0.35), (4.0, 0.24)), win=4.0)


COLORS.setdefault("dusk", ((1.0, 0.72, 0.26), (1.0, 0.93, 0.74)))
sprite("rfx_jxmark", "direction_z", XM_S, XM_S, XM_N, 4, "dusk", zoom=0,
       desc="처형 표식 (엑시큐셔너 비포 선셋 — 땅에 누움, 칸 가운데 = X 가운데 · 획 끝 = 반 크기 × 0.9): 0 첫 획이 그어짐 → 1 둘째 획 → 2 X 다 그어지며 번쩍 → 3 ~ 5 "
       "맥동 (이어 돌림 — 거친 붓 획 · 붓털 결 · 처형의 원과 여덟 눈금 · 튄 먹 · 일렁이는 불티) → 6 벰 (획 가운데 흰 줄 · 큰 섬광 · 빛살) → 7 타 들어가 흐려짐. 빛 칸 = "
       "붓 획 · 원, 심지 칸 = 결 · 불티 · 섬광")(xmark_frame)


# ------------------------------------------------------------ 0.51.0 성기사 2차 (2026-11-06 — 썬더로드 · 타이런트)

def _zigzag(rs, p0, p1, amp, depth, rough=0.55, band=None):
    """p0 → p1 번개 줄 점들 (가운데 밀기 — 깊이마다 폭이 rough 배로 줄어듦, 양 끝은 그대로 · 각진 꺾임). band 가 있으면 p0 → p1 줄에서 그만큼만 벗어남"""
    pts = [p0, p1]
    a = amp
    for _ in range(depth):
        out = [pts[0]]
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            t = rs.uniform(0.35, 0.65)
            mx, my = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            dx, dy = x1 - x0, y1 - y0
            L = math.hypot(dx, dy) or 1.0
            off = rs.uniform(-a, a)
            out.append((mx - dy / L * off, my + dx / L * off))
            out.append((x1, y1))
        pts = out
        a *= rough
    if band is not None:
        ux, uy = p1[0] - p0[0], p1[1] - p0[1]
        L = math.hypot(ux, uy) or 1.0
        nx, ny = -uy / L, ux / L
        fixed = []
        for (x, y) in pts:
            d = (x - p0[0]) * nx + (y - p0[1]) * ny
            dd = max(-band, min(band, d))
            fixed.append((x - nx * (d - dd), y - ny * (d - dd)))
        pts = fixed
    return pts


def _bolt(pad, rs, pts, w, glow_w, a=1.0, branches=0, blen=60.0, bspread=0.8):
    """번개 한 줄 (심지 = 가는 밝은 줄 · 빛 = 굵은 무리) + 가지 branches 개 (줄 위 한 점에서 비스듬히 — 끝이 가늘고 흐려짐)"""
    core = pad.stroke(pts, w, soft=0.6) * a
    glow = np.maximum(pad.stroke(pts, glow_w, soft=glow_w * 0.35) * 0.75 * a, pad.glowline(pts, glow_w * 0.9, 0.5 * a))
    for _ in range(branches):
        i = rs.randrange(2, max(3, len(pts) - 3))
        x, y = pts[i]
        j2 = min(len(pts) - 1, i + 1)
        dx, dy = pts[j2][0] - x, pts[j2][1] - y
        ang = math.atan2(dy, dx) + rs.choice((-1, 1)) * rs.uniform(0.35, bspread)
        L = blen * rs.uniform(0.5, 1.0)
        q = _zigzag(rs, (x, y), (x + math.cos(ang) * L, y + math.sin(ang) * L), L * 0.18, 3)
        core = np.maximum(core, pad.stroke(q, w * 0.7, w * 0.15, a0=a * 0.9, a1=a * 0.15, soft=0.5))
        glow = np.maximum(glow, pad.stroke(q, glow_w * 0.6, glow_w * 0.2, a0=a * 0.5, a1=0.0, soft=glow_w * 0.25))
    return glow, core


# ------------------------------------------------------------ rfx_jarc (번개 줄 — 방향으로 누움, 줄 끝 = 칸 가로 끝 · 가운데 높이)

AR_N = 8
AR_W, AR_H = 512, 128


def arc_frame(f):
    W, H = AR_W, AR_H
    pad = Pad(W, H)
    cy = H / 2
    rs = random.Random(10100 + f)
    p0, p1 = (3.0, cy), (W - 3.0, cy)
    glow = pad.zeros()
    core = pad.zeros()
    if f <= 3:
        pts = _zigzag(rs, p0, p1, 46.0, 5, rough=0.5, band=40.0)
        g, c = _bolt(pad, rs, pts, 3.2, 12.0, 1.0, branches=2 + f % 2, blen=70.0)
        glow, core = np.maximum(glow, g), np.maximum(core, c)
        # 곁의 가는 줄 하나 (얽힌 번개)
        q = _zigzag(rs, p0, p1, 34.0, 5, rough=0.5, band=36.0)
        g, c = _bolt(pad, rs, q, 1.4, 6.0, 0.55)
        glow, core = np.maximum(glow, g), np.maximum(core, c)
    elif f in (4, 5):
        # 번쩍: 굵은 줄 · 가지 많이 · 줄을 따라 밝은 마디
        pts = _zigzag(rs, p0, p1, 50.0, 5, rough=0.5, band=38.0)
        g, c = _bolt(pad, rs, pts, 5.0 if f == 4 else 4.0, 18.0, 1.0, branches=5, blen=90.0, bspread=1.0)
        glow, core = np.maximum(glow, g), np.maximum(core, c)
        for k in range(2):
            q = _zigzag(rs, p0, p1, 36.0, 5, rough=0.5, band=36.0)
            g, c = _bolt(pad, rs, q, 1.6, 7.0, 0.6)
            glow, core = np.maximum(glow, g), np.maximum(core, c)
        for k in range(5):
            x, y = pts[rs.randrange(4, len(pts) - 4)]
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(2.5, 4.0), soft=1.0) * 0.9)
        if f == 4:
            glow = np.maximum(glow, pad.radial(W / 2, cy, W * 0.45, 1.5, sy=0.25) * 0.35)
    else:
        # 사그라짐: 끊어진 줄 (6 마디 · 7 흐린 점)
        pts = _zigzag(rs, p0, p1, 40.0, 6, rough=0.5, band=36.0)
        n = len(pts)
        for k in range(0, n - 1, 8 if f == 6 else 12):
            seg = pts[k:min(n, k + (5 if f == 6 else 3))]
            if len(seg) >= 2:
                a = 0.7 if f == 6 else 0.35
                glow = np.maximum(glow, pad.stroke(seg, 9.0, soft=3.0) * a * 0.6)
                core = np.maximum(core, pad.stroke(seg, 2.0, soft=0.6) * a)
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.35), (7.0, 0.2)), body_k=0.9, core_bloom=((1.0, 0.4), (2.5, 0.25)), win=0.0)


COLORS.setdefault("thunderlord", ((1.0, 0.84, 0.3), (1.0, 0.97, 0.82)))
sprite("rfx_jarc", "lookat_direction", AR_W, AR_H, AR_N, 4, "thunderlord", zoom=1,
       desc="번개 줄 (썬더로드 갈래 벼락 · 사슬 · 타이런트 훔친 번개 줄 — 방향으로 누움, 줄 끝 = 칸 가로 끝 · 가운데 높이라 조각끼리 이어짐): 0 ~ 3 지직임 (이어 돌림 — 꺾인 줄 · "
       "곁의 가는 줄 · 가지 둘 셋) → 4 번쩍 (굵은 줄 · 가지 다섯 · 밝은 마디) → 5 가지 → 6 끊어진 마디 → 7 흐린 점. 빛 칸 = 줄 둘레 무리, 심지 칸 = 가는 밝은 줄")(arc_frame)


# ------------------------------------------------------------ rfx_jpillar (번개 기둥 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

PL_N = 8
PL_W, PL_H = 160, 640


def pillar_frame(f):
    W, H = PL_W, PL_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    gy = H - 8.0
    rs = random.Random(10200 + f)
    glow = pad.zeros()
    core = pad.zeros()
    if f == 0:
        # 길잡이: 위에서 가운데까지 내려오는 가는 번개 · 끝 불티
        end = (cx + rs.uniform(-8, 8), H * 0.58)
        pts = _zigzag(rs, (cx, 0.0), end, 36.0, 5, rough=0.52, band=32.0)
        g, c = _bolt(pad, rs, pts, 3.0, 12.0, 0.9, branches=2, blen=50.0)
        glow, core = np.maximum(glow, g), np.maximum(core, c)
        core = np.maximum(core, star4(pad, end[0], end[1], 26.0, 0.9, rot=0.2, thin=0.12, diag=0.4))
    elif f in (1, 2):
        # 기둥: 얽힌 굵은 번개 셋 · 가운데 하얀 기둥 · 땅에 튀는 빛
        col = clamp01(1 - np.abs(X - cx) / (26.0 if f == 1 else 22.0)) ** 0.8 * clamp01((Y - 2.0) / 30.0)
        glow = np.maximum(glow, col * 0.85)
        core = np.maximum(core, clamp01(1 - np.abs(X - cx) / (9.0 if f == 1 else 7.0)) * clamp01((Y - 2.0) / 20.0))
        for k in range(3):
            pts = _zigzag(rs, (cx + rs.uniform(-6, 6), 0.0), (cx + rs.uniform(-10, 10), gy), 46.0, 6, rough=0.52, band=34.0)
            g, c = _bolt(pad, rs, pts, 4.0 if k == 0 else 2.4, 16.0, 1.0, branches=3, blen=70.0, bspread=0.9)
            glow, core = np.maximum(glow, g), np.maximum(core, c)
        # 땅: 납작한 빛 · 옆으로 튀는 줄
        splash = pad.radial(cx, gy, 78.0 if f == 2 else 64.0, 1.4, sy=0.28)
        glow = np.maximum(glow, splash * 0.95)
        core = np.maximum(core, pad.radial(cx, gy, 40.0, 1.6, sy=0.3) * 0.9)
        for k in range(10 if f == 2 else 6):
            a = rs.uniform(-0.9, 0.9) + (math.pi if k % 2 else 0.0)
            L = rs.uniform(30, 75)
            core = np.maximum(core, streak(pad, (cx + math.cos(a) * 10, gy - 4), (cx + math.cos(a) * L, gy - 6 - abs(math.sin(a)) * L * 0.7), 2.4, 0.9))
        if f == 1:
            glow = np.maximum(glow, clamp01(1 - np.abs(X - cx) / 62.0) ** 1.6 * 0.3)
    elif f <= 5:
        # 지직이는 기둥 (이어 돌림): 줄 둘 · 옅은 기둥 · 땅의 빛
        col = clamp01(1 - np.abs(X - cx) / 18.0) ** 1.2 * clamp01((Y - 2.0) / 40.0)
        glow = np.maximum(glow, col * 0.55)
        for k in range(2):
            pts = _zigzag(rs, (cx + rs.uniform(-8, 8), 0.0), (cx + rs.uniform(-8, 8), gy), 40.0, 6, rough=0.52, band=32.0)
            g, c = _bolt(pad, rs, pts, 3.0 if k == 0 else 1.8, 12.0, 0.95, branches=2, blen=50.0)
            glow, core = np.maximum(glow, g), np.maximum(core, c)
        glow = np.maximum(glow, pad.radial(cx, gy, 52.0, 1.5, sy=0.3) * 0.7)
        core = np.maximum(core, pad.radial(cx, gy, 24.0, 1.6, sy=0.3) * 0.6)
    elif f == 6:
        pts = _zigzag(rs, (cx, 0.0), (cx + rs.uniform(-8, 8), gy), 36.0, 6, rough=0.52, band=30.0)
        n = len(pts)
        for k in range(0, n - 1, 10):
            seg = pts[k:min(n, k + 6)]
            if len(seg) >= 2:
                glow = np.maximum(glow, pad.stroke(seg, 10.0, soft=3.0) * 0.45)
                core = np.maximum(core, pad.stroke(seg, 2.2, soft=0.6) * 0.7)
        glow = np.maximum(glow, pad.radial(cx, gy, 40.0, 1.5, sy=0.3) * 0.4)
    else:
        for k in range(14):
            x = cx + rs.uniform(-22, 22)
            y = gy - rs.uniform(0, H * 0.7)
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.2, 2.4), soft=0.7) * 0.6)
            glow = np.maximum(glow, pad.disc_field(x, y, 5.0, soft=3.0) * 0.25)
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.4), (10.0, 0.25)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.28)), win=3.0)


sprite("rfx_jpillar", "lookat_y", PL_W, PL_H, PL_N, 8, "thunderlord", zoom=1,
       desc="번개 기둥 (썬더로드 스톰 스파이어 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅 · 높이 = 너비 × 4): 0 가는 길잡이 번개가 가운데까지 내려옴 → 1 굵은 기둥이 내리꽂힘 "
       "(얽힌 굵은 번개 셋 · 하얀 가운데 · 땅에 튀는 빛 · 옆으로 튀는 불티) → 2 땅의 빛이 커짐 → 3 ~ 5 지직이는 기둥 (이어 돌림) → 6 끊어지며 사그라짐 → 7 남은 불티. "
       "빛 칸 = 기둥 둘레 무리 · 땅의 빛, 심지 칸 = 번개 줄 · 하얀 가운데")(pillar_frame)


# ------------------------------------------------------------ rfx_jcloud (먹구름 — 카메라를 봄, 빛 칸 = 짙은 구름 몸 · 심지 칸 = 속의 번개)

CL_N = 8
CL_W, CL_H = 384, 192


def _cloud_body(pad, X, Y, rs, cx, cy, sx, sy, f, seed, n=13):
    """뭉게구름 몸 (밀도 0 ~ 1): 아래 줄의 큰 뭉치 + 위의 작은 뭉치 (울퉁불퉁한 윗선) — 거리장에 노이즈를 더해 가장자리만 일렁이고 속은 꽉 참 · 아래는 납작"""
    field = np.full(X.shape, -1.0, np.float32)
    for k in range(n):
        t = k / max(1, n - 1)
        x = cx + (t - 0.5) * sx * 1.75 + rs.uniform(-8, 8)
        top = 1 - (2 * t - 1) ** 2
        y = cy + sy * 0.12 - top * sy * 0.18 + rs.uniform(-5, 5)
        r = sy * (0.42 + 0.28 * top) * rs.uniform(0.9, 1.1)
        field = np.maximum(field, 1 - np.sqrt((X - x) ** 2 + (Y - y) ** 2) / r)
    for k in range(6):
        t = (k + 0.5) / 6
        x = cx + (t - 0.5) * sx * 1.1 + rs.uniform(-12, 12)
        y = cy - sy * (0.35 + 0.25 * (1 - (2 * t - 1) ** 2)) + rs.uniform(-6, 6)
        r = sy * rs.uniform(0.32, 0.46)
        field = np.maximum(field, 1 - np.sqrt((X - x) ** 2 + (Y - y) ** 2) / r)
    nz = fbm(X / CL_W * 7.0 + f * 0.09, Y / CL_H * 5.0, seed=seed, octaves=4, px=14, py=7)
    field = field + (nz - 0.5) * 0.32
    flat = clamp01((cy + sy * 0.62 - Y) / (sy * 0.22))
    dens = clamp01(field / 0.22) * flat
    shade = fbm(X / CL_W * 4.0, Y / CL_H * 3.0 + f * 0.05, seed=seed + 7, octaves=3, px=8, py=4)
    return dens * (0.86 + 0.14 * shade)

def cloud_frame(f):
    W, H = CL_W, CL_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx, cy = W / 2, H * 0.52
    rs = random.Random(10300)
    scale = [0.62, 0.84, 1.0, 1.0, 1.0, 1.0, 1.0, 0.96][f]
    body = _cloud_body(pad, X, Y, rs, cx, cy, W * 0.42 * scale, H * 0.42 * scale, f % 6 if f < 6 else 5, 10310)
    glow = body * [0.5, 0.78, 0.94, 0.94, 0.94, 0.94, 0.66, 0.34][f]
    if f >= 6:
        hole = clamp01((fbm(X / W * 5.0, Y / H * 4.0, seed=10320 + f, octaves=4, px=10, py=5) - (0.45 if f == 6 else 0.32)) * 3.0)
        glow = glow * (1 - hole)
    if f == 0:
        hole = clamp01((fbm(X / W * 6.0, Y / H * 5.0, seed=10325, octaves=3, px=12, py=6) - 0.5) * 2.5)
        glow = glow * (1 - hole * 0.7)
    core = pad.zeros()
    rs2 = random.Random(10330 + f)
    # 속의 번쩍임: 구름 몸 안의 번개 (3 = 큰 번쩍 · 5 = 작은 번쩍) · 아래 테의 빛
    if f in (1, 2, 3, 5):
        n = {1: 1, 2: 1, 3: 3, 5: 2}[f]
        for k in range(n):
            x0 = cx + rs2.uniform(-110, 110) * scale
            pts = _zigzag(rs2, (x0, cy - 34 * scale), (x0 + rs2.uniform(-60, 60), cy + 26 * scale), 22.0, 4, rough=0.5)
            c = pad.stroke(pts, 2.6 if f == 3 else 1.8, soft=0.6)
            core = np.maximum(core, c * clamp01(body * 1.4))
        if f == 3:
            core = np.maximum(core, pad.radial(cx + rs2.uniform(-60, 60), cy, 90.0, 1.8, sy=0.6) * 0.55 * body)
    core = np.maximum(core, clamp01((body - 0.6) * 2.0) * gauss(Y - (cy + H * 0.18 * scale), 9.0) * (0.22 if f < 6 else 0.08))
    return finish(pad, glow, core, halo=((2.0, 0.3), (6.0, 0.2)), body_k=1.0, core_bloom=((1.5, 0.4), (4.0, 0.25)), win=4.0)

sprite("rfx_jcloud", "rotate_xyz", CL_W, CL_H, CL_N, 4, "thunderlord", zoom=0,
       desc="먹구름 (썬더로드 썬더헤드 · 스톰 스파이어 · 던브레이커, 타이런트 스톨른 썬더 — 카메라를 봄, 너비 = 높이 × 2): 0 바깥에서 구름 자락이 감겨 듦 → 1 작은 구름 → 2 ~ 5 "
       "뭉게구름 (이어 돌림 — 노이즈 가장자리 · 납작한 아래 · 속의 번쩍임: 3 큰 번쩍 · 5 작은 번쩍) → 6 ~ 7 구멍이 뚫리며 흩어짐. 빛 칸 = 짙은 구름 몸 (게임은 짙은 보라 회색), "
       "심지 칸 = 속의 번개 · 아래 테의 빛")(cloud_frame)


# ------------------------------------------------------------ rfx_jstone (돌 껍데기 — 세워 카메라 쪽으로 돎, 아래 끝 = 발 · 너비 = 높이 × 0.75)

ST_N = 8
ST_W, ST_H = 192, 256


def _stone_outline(cx, top, bot, hw):
    """사람을 감싼 모난 돌 덩이 — 둥근 머리 덩이 · 넓은 어깨 · 발 쪽이 조금 좁음 (꼭짓점 사이가 곧은 줄이라 모나 보임)"""
    rs = random.Random(10400)
    half = [(0.30, 0.02), (0.47, 0.11), (0.42, 0.21), (0.78, 0.29), (0.97, 0.41), (0.89, 0.56), (0.96, 0.70), (0.84, 0.85), (0.74, 1.0)]
    H = bot - top

    def side(sgn):
        return [(cx + sgn * u * hw * rs.uniform(0.92, 1.05), top + v * H + (rs.uniform(-0.016, 0.016) * H if 0 < v < 1 else 0.0)) for u, v in half]
    right, left = side(1), side(-1)
    return [(cx + 4.0, top)] + right + left[::-1]


def _stone_cells(X, Y, cx, top, bot, hw):
    """돌 면 (보로노이 18칸): 칸 번호 · 이웃 칸 번호 · 경계까지 거리 · 면 밝기 (왼쪽 위 새벽빛을 받는 면이 밝음)"""
    rs = random.Random(10405)
    cols, rows = 3, 6
    seeds = []
    for r in range(rows):
        for c in range(cols):
            x = cx - hw + (c + 0.5 + rs.uniform(-0.34, 0.34)) / cols * 2 * hw
            y = top + (r + 0.5 + rs.uniform(-0.34, 0.34)) / rows * (bot - top)
            seeds.append((x, y))
    d1 = np.full(X.shape, 1e9, np.float32)
    d2 = d1.copy()
    lab = np.zeros(X.shape, np.int32)
    lab2 = np.zeros(X.shape, np.int32)
    for k, (x, y) in enumerate(seeds):
        d = np.sqrt((X - x) ** 2 + (Y - y) ** 2)
        closer = d < d1
        sec = (~closer) & (d < d2)
        lab2 = np.where(closer, lab, np.where(sec, k, lab2))
        d2 = np.where(closer, d1, np.where(sec, d, d2))
        lab = np.where(closer, k, lab)
        d1 = np.where(closer, d, d1)
    sx = np.array([p[0] for p in seeds], np.float32)
    sy = np.array([p[1] for p in seeds], np.float32)
    gap = np.maximum(1e-3, np.hypot(sx[lab] - sx[lab2], sy[lab] - sy[lab2]))
    edge = (d2 ** 2 - d1 ** 2) / (2 * gap)
    mid = (top + bot) / 2
    shade = []
    for x, y in seeds:
        nx = (x - cx) / hw * 0.8 + rs.uniform(-0.45, 0.45)
        ny = (y - mid) / ((bot - top) / 2) * 0.5 + rs.uniform(-0.45, 0.45)
        shade.append(min(1.0, max(0.0, 0.5 + 0.65 * (nx * -0.6 + ny * -0.8))))
    return seeds, lab, lab2, edge, np.array(shade, np.float32)


def _shift(a, dx, dy):
    """그림 a 를 (dx, dy) 칸 점만큼 옮김 (빈 데는 0)"""
    out = np.zeros_like(a)
    Hs, Ws = a.shape
    x0, y0 = int(round(dx * SS)), int(round(dy * SS))
    xs0, xs1 = max(0, -x0), min(Ws, Ws - x0)
    ys0, ys1 = max(0, -y0), min(Hs, Hs - y0)
    if xs1 > xs0 and ys1 > ys0:
        out[ys0 + y0:ys1 + y0, xs0 + x0:xs1 + x0] = a[ys0:ys1, xs0:xs1]
    return out


def stone_frame(f):
    W, H = ST_W, ST_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    top, bot = 8.0, H - 4.0
    hw = 74.0
    mid = (top + bot) / 2
    rs = random.Random(10410 + f)
    outline = _stone_outline(cx, top, bot, hw)
    shell = pad.poly(outline)
    seeds, lab, lab2, edge, shade = _stone_cells(X, Y, cx, top, bot, hw)
    sh = shade[lab]
    rock = fbm(X / W * 11.0, Y / H * 11.0, seed=10420, octaves=3, px=22, py=22)
    seam = clamp01(1 - edge / 1.3)
    # 위 · 왼쪽 가장자리 (새벽빛 테): 덩이 - 오른쪽 아래로 옮긴 덩이
    rim = clamp01(shell - pad.poly([(x + 3.5, y + 3.5) for x, y in outline]))
    if f <= 5:
        body = shell * (0.72 + 0.24 * sh) * (0.9 + 0.1 * rock) * (1 - 0.5 * seam)
        glint = shell * 0.13 * sh ** 2 * (1 - seam)
        filled = np.ones_like(X)
        front = pad.zeros()
        if f <= 1:
            # 발부터 차오름 — 모난 윗선 (곧은 토막 6개) · 윗선에 새벽빛 줄
            fill = (0.36, 0.74)[f]
            base = bot - (bot - top) * fill
            xs = [cx - hw - 4 + i * (2 * hw + 8) / 6 for i in range(7)]
            ys = [base + rs.uniform(-9, 9) for _ in xs]
            line = list(zip(xs, ys))
            filled = pad.poly(line + [(xs[-1], H + 4), (xs[0], H + 4)])
            front = pad.stroke(line, 2.2, soft=0.8) * shell
            body = body * filled
            for k in range(12):
                x = cx + rs.uniform(-hw * 0.85, hw * 0.85)
                y = base + rs.uniform(-34, 4)
                body = np.maximum(body, pad.disc_field(x, y, rs.uniform(1.4, 2.8), soft=0.8) * 0.65)
        glow = body
        core = glint * filled + rim * filled * (0.62 if f in (2, 3) else 0.42) * (0.85 if f == 3 else 1.0) + front * 0.9
        if f in (4, 5):
            # 금이 면 사이 틈을 따라 감 — 4: 틈의 절반 (가늘게) · 5: 모든 틈 (굵게) + 가운데에서 빛이 터짐
            rs3 = random.Random(10450)
            pick = np.array([[rs3.random() < 0.5 for _ in range(18)] for _ in range(18)])
            pick = pick | pick.T
            sel = pick[lab, lab2] if f == 4 else np.ones(X.shape, bool)
            wd = 1.3 if f == 4 else 2.6
            crack = clamp01(1 - edge / wd) * sel * shell * clamp01((shell - 0.5) * 2)
            core = np.maximum(core, crack)
            glow = glow * (1 - crack * (0.55 if f == 4 else 0.9))
            if f == 5:
                cy = top + (bot - top) * 0.42
                core = np.maximum(core, star4(pad, cx, cy, 74.0, 1.0, rot=0.2, thin=0.1, diag=0.4))
                core = np.maximum(core, pad.radial(cx, cy, 62.0, 1.6, sy=1.3) * 0.55 * shell)
                for k in range(10):
                    a = k * TAU / 10 + 0.2
                    core = np.maximum(core, pad.stroke([(cx + math.cos(a) * 20, cy + math.sin(a) * 26), (cx + math.cos(a) * 98, cy + math.sin(a) * 124)], 5.0, 0.6,
                                                       a0=0.7, a1=0.0, soft=2.0))
        return finish(pad, glow, core, halo=((1.2, 0.25),), body_k=1.0, core_bloom=((1.2, 0.45), (3.5, 0.3)), win=3.0)
    # 깨짐: 면마다 조각이 되어 바깥으로 흩어짐 (6 가깝게 · 크게 — 테에 새벽빛 / 7 멀리 · 작고 흐리게)
    glow = pad.zeros()
    core = pad.zeros()
    gapw = 2.2 if f == 6 else 6.0
    piece = shell * clamp01((edge - gapw) / 1.0)
    rs4 = random.Random(10460)
    for k, (x, y) in enumerate(seeds):
        m = piece * (lab == k)
        if float(m.max()) <= 0:
            continue
        dx, dy = x - cx, y - mid
        L = math.hypot(dx, dy) or 1.0
        push = (22.0 if f == 6 else 50.0) * rs4.uniform(0.75, 1.3)
        ox, oy = dx / L * push + rs4.uniform(-5, 5), dy / L * push * 0.7 - (6.0 if f == 7 else 2.0)
        mm = _shift(m, ox, oy)
        glow = np.maximum(glow, mm * (0.95 if f == 6 else 0.5) * (0.8 + 0.2 * shade[k]))
        core = np.maximum(core, clamp01(mm - _shift(m, ox + 2.5, oy + 2.5)) * (0.7 if f == 6 else 0.3))
    if f == 6:
        core = np.maximum(core, pad.radial(cx, mid - 10, 92.0, 1.4, sy=1.3) * 0.72)
    return finish(pad, glow, core, halo=((1.2, 0.25),), body_k=1.0, core_bloom=((1.2, 0.45), (3.5, 0.3)), win=3.0)


COLORS.setdefault("stone", ((0.5, 0.48, 0.46), (1.0, 0.86, 0.5)))
sprite("rfx_jstone", "lookat_y", ST_W, ST_H, ST_N, 4, "stone", zoom=2,
       desc="돌 껍데기 (썬더로드 던브레이커 — 새벽빛에 굳은 적, 세워 카메라 쪽으로 돎, 아래 끝 = 발): 0 발부터 돌이 차오름 (돌먼지) → 1 가슴까지 → 2 ~ 3 다 굳음 (이어 돌림 — "
       "모난 덩이 · 면마다 다른 밝기 (왼쪽 위 면에 새벽빛) · 면 사이 어두운 틈 · 위 왼쪽 새벽빛 테) → 4 틈의 절반을 따라 금빛이 새어 나옴 → 5 모든 틈이 벌어짐 · 빛이 터져 나옴 → 6 면마다 조각이 되어 사방으로 → 7 작은 조각이 "
       "흐려짐. 빛 칸 = 돌 몸 (게임은 잿빛 · 거의 불투명), 심지 칸 = 새벽빛 테 · 금 · 터지는 빛")(stone_frame)


# ------------------------------------------------------------ rfx_jgrip (거인 왕의 손 — 카메라를 봄, 손 가운데 = 칸 가운데)

GR_N = 8
GR_S = 256


def grip_frame(f):
    S = GR_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    lum = [0.75, 0.95, 1.05, 1.0, 1.08, 1.1, 0.7, 0.4][f]
    # 오므린 정도 (0 편 손 → 1 움켜쥠 — 손가락이 가운데 (쥔 적) 를 감쌈) · 쥐어짬 (4 조금 더 조임)
    k = [0.0, 0.5, 1.0, 1.0, 1.08, 1.0, 1.0, 1.0][f]
    glow = pad.zeros()
    core = pad.zeros()
    # 손목 (위 — 소맷부리 띠) · 손등 (아래로 넓어지는 사다리꼴 — 마디 줄 y 96)
    wrist = [(c - 30, 0.0), (c + 30, 0.0), (c + 34, 30.0), (c - 34, 30.0)]
    back = [(c - 34, 28.0), (c + 34, 28.0), (c + 62, 96.0), (c - 62, 96.0)]
    glow = np.maximum(glow, pad.poly(wrist) * 0.5)
    glow = np.maximum(glow, pad.poly(back) * 0.58)
    core = np.maximum(core, pad.stroke(back + [back[0]], 2.2, soft=0.7) * 0.75)
    for y in (18.0, 26.0):
        core = np.maximum(core, pad.stroke([(c - 33, y), (c + 33, y)], 2.4 if y > 20 else 1.4, soft=0.6) * 0.8)
    # 힘줄 넷 (손등 — 손목에서 마디로)
    for dx in (-36.0, -12.0, 12.0, 36.0):
        core = np.maximum(core, pad.stroke([(c + dx * 0.4, 34.0), (c + dx, 92.0)], 1.4, soft=0.6) * 0.35)
    # 손가락 넷: 마디 (y 96) 에서 아래로 — 편 손은 바깥으로 벌어져 곧게, 움켜쥐면 바깥으로 불룩하게 휘어 끝이 가운데 아래로 모임
    bases = [(c - 48, 96.0), (c - 17, 100.0), (c + 17, 100.0), (c + 48, 96.0)]
    for i, (bx, by) in enumerate(bases):
        side = -1 if bx < c else 1
        outer = abs(bx - c) > 30
        open_tip = (bx + side * (30 if outer else 12), 206.0)
        shut_tip = (c + side * (14 if outer else 6), 214.0)
        tip = (open_tip[0] + (shut_tip[0] - open_tip[0]) * min(1, k), open_tip[1] + (shut_tip[1] - open_tip[1]) * min(1, k))
        bulge = side * (18 if outer else 8) * min(1, k) * (1.0 + (k - 1) * 2 if k > 1 else 1.0)
        mid = ((bx + tip[0]) / 2 + bulge + side * 10 * min(1, k), (by + tip[1]) / 2)
        pts = bez((bx, by), mid, tip, n=18)
        w0 = 24.0 if not outer else 21.0
        body = pad.stroke(pts, w0, w0 * 0.55, soft=1.2)
        glow = np.maximum(glow, body * 0.62)
        core = np.maximum(core, clamp01(body - pad.stroke(pts, w0 - 6.0, w0 * 0.55 - 6.0, soft=1.2)) * 0.8)
        # 마디 둘 · 발톱
        for t in (0.36, 0.68):
            x, y = pts[int(t * (len(pts) - 1))]
            core = np.maximum(core, pad.disc_field(x, y, 2.6, soft=0.8) * 0.6)
        tx, ty = pts[-1]
        px_, py_ = pts[-3]
        L = math.hypot(tx - px_, ty - py_) or 1.0
        ux, uy = (tx - px_) / L, (ty - py_) / L
        claw = [(tx - uy * 6, ty + ux * 6), (tx + ux * 15, ty + uy * 15), (tx + uy * 6, ty - ux * 6)]
        core = np.maximum(core, pad.poly(claw) * 0.9)
    # 엄지 (왼쪽 손등에서 — 움켜쥐면 가운데 앞을 가로지름)
    t0 = (c - 58, 72.0)
    t_open = (c - 92, 150.0)
    t_shut = (c - 16, 176.0)
    ttip = (t_open[0] + (t_shut[0] - t_open[0]) * min(1, k), t_open[1] + (t_shut[1] - t_open[1]) * min(1, k))
    tm = ((t0[0] + ttip[0]) / 2 - 14 * min(1, k), (t0[1] + ttip[1]) / 2 + 18 * min(1, k))
    tpts = bez(t0, tm, ttip, n=16)
    body = pad.stroke(tpts, 25.0, 15.0, soft=1.2)
    glow = np.maximum(glow, body * 0.62)
    core = np.maximum(core, clamp01(body - pad.stroke(tpts, 19.0, 9.0, soft=1.2)) * 0.8)
    rs = random.Random(10500 + f)
    if 2 <= f <= 5:
        # 손등의 번개 핏줄 · 쥔 가운데 (적) 로 새는 핏빛
        for q in range(3 if f != 2 else 1):
            pts = _zigzag(rs, (c + rs.uniform(-24, 24), 32.0), (c + rs.uniform(-50, 50), 120.0 + rs.uniform(0, 50)), 18.0, 4, rough=0.5)
            core = np.maximum(core, pad.stroke(pts, 2.0, soft=0.6) * (0.95 if f in (3, 4) else 0.6))
        core = np.maximum(core, pad.radial(c, 150.0, 48.0, 1.5) * (0.5 if f in (3, 4) else 0.3))
    if f == 5:
        # 내리꽂음: 위로 끌리는 줄 (아래로 움직임) · 아래 번쩍
        for q in range(9):
            x = c - 64 + q * 16
            glow = np.maximum(glow, pad.stroke([(x, 0.0), (x, 34.0 + rs.uniform(0, 24))], 0.6, 4.0, a0=0.0, a1=0.5, soft=1.2))
        core = np.maximum(core, star4(pad, c, 226.0, 60.0, 1.0, rot=0.0, thin=0.1, diag=0.4))
    if f == 0:
        hole = clamp01((fbm(X / S * 6.0, Y / S * 6.0, seed=10510, octaves=3, px=12, py=12) - 0.45) * 3.0)
        glow = glow * (1 - hole * 0.55)
        for q in range(8):
            y = rs.uniform(0, 30)
            x = c + rs.uniform(-80, 80)
            glow = np.maximum(glow, pad.stroke([(x, y), (x, y + 44)], 0.6, 3.0, a0=0.0, a1=0.4, soft=1.0))
    if f >= 6:
        hole = clamp01((fbm(X / S * 5.0, Y / S * 5.0 + f * 0.4, seed=10520, octaves=4, px=10, py=10) - (0.42 if f == 6 else 0.25)) * 3.0)
        glow = glow * (1 - hole)
        core = core * (1 - hole) * 0.6
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.34), (10.0, 0.22)), body_k=0.9, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)

COLORS.setdefault("tyrant", ((0.7, 0.32, 1.0), (1.0, 0.3, 0.34)))
sprite("rfx_jgrip", "rotate_xyz", GR_S, GR_S, GR_N, 4, "tyrant", zoom=2,
       desc="거인 왕의 손 (타이런트 킹스 그립 — 카메라를 봄, 손바닥이 위 · 손가락이 아래, 쥔 가운데 (128, 150)): 0 편 손이 내려옴 (흐림 · 위로 끌리는 줄) → 1 손가락을 오므림 "
       "→ 2 움켜쥠 (가운데가 빈 주먹 — 쥔 적이 보이게) → 3 ~ 4 쥐어짬 (이어 돌림 — 손등의 번개 핏줄 · 쥔 가운데로 새는 핏빛) → 5 내리꽂음 (위로 끌리는 줄 · 아래 번쩍) → "
       "6 ~ 7 연기로 흩어짐. 빛 칸 = 손 몸 (보랏빛 망령), 심지 칸 = 테 · 마디 · 발톱 · 핏줄 (게임은 핏빛)")(grip_frame)


# ------------------------------------------------------------ rfx_jcrown (번개 왕관 — 카메라를 봄)

CR_N = 8
CR_W, CR_H = 192, 128


def crown_frame(f):
    W, H = CR_W, CR_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    band_t, band_b = 78.0, 98.0
    tips = [(cx - 64, 42.0), (cx - 32, 28.0), (cx, 16.0), (cx + 32, 28.0), (cx + 64, 42.0)]
    vals = [(cx - 48, 66.0), (cx - 16, 62.0), (cx + 16, 62.0), (cx + 48, 66.0)]
    outline = [(cx - 70, band_b), (cx - 70, band_t - 6)] + [p for pair in zip(tips, vals + [None]) for p in pair if p is not None] + [(cx + 70, band_t - 6),
                                                                                                                                         (cx + 70, band_b)]
    lum = [0.6, 1.15, 1.0, 1.0, 1.0, 1.0, 0.7, 0.35][f]
    glow = pad.zeros()
    core = pad.zeros()
    body = pad.poly(outline)
    if f == 0:
        # 불티가 모여 테가 됨 (점선 테)
        rs = random.Random(10600)
        for k in range(40):
            i = rs.randrange(len(outline) - 1)
            t = rs.random()
            x = outline[i][0] + (outline[i + 1][0] - outline[i][0]) * t
            y = outline[i][1] + (outline[i + 1][1] - outline[i][1]) * t
            core = np.maximum(core, pad.disc_field(x + rs.uniform(-6, 6), y + rs.uniform(-6, 6), rs.uniform(1.2, 2.2), soft=0.6) * 0.8)
        glow = np.maximum(glow, body * 0.2)
    else:
        glow = np.maximum(glow, body * 0.55)
        core = np.maximum(core, pad.stroke(outline + [outline[0]], 2.6, soft=0.7) * 0.85)
        # 띠의 무늬 (가로 줄 둘 · 보석 셋)
        for y in (band_t + 2, band_b - 3):
            core = np.maximum(core, pad.stroke([(cx - 68, y), (cx + 68, y)], 1.2, soft=0.5) * 0.5)
        for x in (cx - 40, cx, cx + 40):
            core = np.maximum(core, pad.ellipse(x, (band_t + band_b) / 2, 6.0, 5.0) * 0.95)
        for (x, y) in tips:
            core = np.maximum(core, pad.disc_field(x, y - 4, 4.0, soft=1.0) * 0.9)
    rs = random.Random(10610 + f)
    if 2 <= f <= 5:
        # 뿔 끝 사이를 잇는 번개 둘 (장마다 다른 짝) · 위로 튀는 불티
        pairs = [(0, 2), (2, 4), (1, 3), (0, 4)]
        for q in range(2):
            a, b = pairs[(f + q) % 4]
            p0, p1 = (tips[a][0], tips[a][1] - 4), (tips[b][0], tips[b][1] - 4)
            pts = _zigzag(rs, p0, p1, 10.0, 4)
            mid = [(x, y - 10 * math.sin(i / max(1, len(pts) - 1) * math.pi)) for i, (x, y) in enumerate(pts)]
            core = np.maximum(core, pad.stroke(mid, 1.8, soft=0.6) * 0.95)
            glow = np.maximum(glow, pad.stroke(mid, 7.0, soft=2.5) * 0.4)
        for q in range(5):
            x, y = tips[rs.randrange(5)]
            core = np.maximum(core, streak(pad, (x, y - 4), (x + rs.uniform(-8, 8), y - rs.uniform(10, 22)), 1.6, 0.8))
    if f == 1:
        core = np.maximum(core, star4(pad, cx, 16.0, 40.0, 1.0, rot=0.0, thin=0.1, diag=0.4))
    if f >= 6:
        hole = clamp01((fbm(X / W * 6.0, Y / H * 4.0, seed=10620 + f, octaves=3, px=12, py=8) - (0.45 if f == 6 else 0.3)) * 3.0)
        glow = glow * (1 - hole)
        core = core * (1 - hole)
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.3, 0.45), (3.5, 0.34), (8.0, 0.2)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.26)), win=3.0)


sprite("rfx_jcrown", "rotate_xyz", CR_W, CR_H, CR_N, 4, "tyrant", zoom=2,
       desc="번개 왕관 (타이런트 콜로서스 — 머리 위, 카메라를 봄): 0 불티가 모여 테가 됨 → 1 왕관이 번쩍 (가운데 뿔 위 반짝임) → 2 ~ 5 뿔 다섯 · 띠 · 보석 셋 (이어 돌림 — "
       "뿔 끝 사이를 잇는 번개 둘 · 위로 튀는 불티) → 6 ~ 7 흩어짐. 빛 칸 = 왕관 몸, 심지 칸 = 테 · 보석 · 번개")(crown_frame)


# ------------------------------------------------------------ 2026-11-06 사제 2차 (세인트 · 워록)

COLORS.setdefault("saint", ((1.0, 0.88, 0.5), (1.0, 0.98, 0.88)))
COLORS.setdefault("saintpetal", ((1.0, 0.62, 0.8), (1.0, 0.95, 0.97)))
COLORS.setdefault("warlock", ((0.66, 0.02, 0.12), (1.0, 0.34, 0.36)))
COLORS.setdefault("warlockfire", ((1.0, 0.3, 0.12), (1.0, 0.86, 0.46)))


def _obovate(cx, cy, ang, r0, r1, wmax, n=30, a_pow=0.7, b=0.35):
    """주걱 꼴 꽃잎 (뿌리 r0 가늘고 끝 r1 쪽이 넓고 둥긂) 다각형 점 (ang 쪽으로 뻗음)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    L = r1 - r0
    left, right = [], []
    for i in range(n + 1):
        t = i / n
        hw = wmax / 2 * (math.sin(math.pi * t) ** a_pow) * (b + (1 - b) * t)
        x, y = cx + ux * (r0 + L * t), cy + uy * (r0 + L * t)
        left.append((x + px * hw, y + py * hw))
        right.append((x - px * hw, y - py * hw))
    return left + right[::-1]


def _rot_pts(pts, cx, cy, a):
    ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def _centroid(pts):
    return sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)


# ------------------------------------------------------------ rfx_jlotus (빛의 연꽃 — 땅에 누움, 위에서 본 꼴)

LO_N = 8
LO_S = 384


def lotus_frame(f):
    S = LO_S
    pad = Pad(S, S)
    c = S / 2
    R = S / 2 * 0.9
    open_k = [0.3, 0.6, 0.86, 1.0, 1.0, 1.0, 1.02, 1.04][f]
    lum = [0.95, 1.0, 1.0, 1.0, 1.0, 1.0, 0.72, 0.36][f]
    lift = [0, 0, 0, 0, 0, 0, 0.03, 0.06][f] * R
    rs = random.Random(11000 + f)
    glow = pad.zeros()
    core = pad.zeros()
    tips = []
    # 겹: (꽃잎 수, 돌림, 길이, 폭, 밝기) — 바깥 · 안 (22.5° 돌림) · 가장 안
    layers = [(8, 0.0, 1.0, 0.34, 0.55), (8, math.pi / 8, 0.72, 0.4, 0.72), (8, 0.0, 0.44, 0.46, 0.95)]
    for li, (n, rot, lk, wk, bright) in enumerate(layers):
        for i in range(n):
            a = rot + i * TAU / n + (rs.uniform(-0.05, 0.05) if f >= 6 else 0.0)
            push = lift * (1.0 - li * 0.3)
            r0 = R * 0.08 + push
            r1 = R * lk * open_k + push
            if r1 - r0 < 6:
                continue
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * r1, c + math.sin(a) * r1)
            w = (r1 - r0) * wk
            m = lens(pad, p0, p1, w, 1.0, power=0.7, skew=0.5)
            glow = np.maximum(glow, m * bright * 0.6)
            core = np.maximum(core, rim_of(m, 2.6 if li == 0 else 2.2) * 0.85)
            vein_end = (p0[0] + (p1[0] - p0[0]) * 0.86, p0[1] + (p1[1] - p0[1]) * 0.86)
            core = np.maximum(core, pad.stroke([p0, vein_end], 1.8, 0.5, a0=0.55, a1=0.15, soft=0.6))
            if li == 0:
                tips.append(p1)
    # 가운데 씨방 (둥근 판 · 씨 구멍 · 빛)
    cr = R * 0.12 * min(1.0, open_k + 0.3)
    glow = np.maximum(glow, pad.radial(c, c, cr * 2.6, 1.3) * 0.9)
    core = np.maximum(core, pad.disc_field(c, c, cr, soft=1.0) * 0.7)
    for k in range(7):
        a = k * TAU / 7 + 0.3
        core = np.maximum(core, pad.disc_field(c + math.cos(a) * cr * 0.55, c + math.sin(a) * cr * 0.55, cr * 0.13, soft=0.5))
    core = np.maximum(core, pad.disc_field(c, c, cr * 0.18, soft=0.5))
    if f == 0:
        core = np.maximum(core, star4(pad, c, c, R * 0.42, 1.0, rot=0.0, thin=0.1, diag=0.45))
    # 꽃잎 끝 반짝임 (3 ~ 5 이어 돌림 — 장마다 다른 끝)
    if 3 <= f <= 5 and tips:
        for q in range(3):
            x, y = tips[(f * 3 + q * 3) % len(tips)]
            core = np.maximum(core, star4(pad, x, y, 13.0, 0.95, rot=rs.uniform(0, 1.5), thin=0.12, diag=0.4))
        glow = glow * (0.94 + 0.06 * (f - 3))
    if f >= 6:
        # 떠오르는 빛 알갱이
        for k in range(18 if f == 6 else 10):
            a = rs.uniform(0, TAU)
            rr = R * rs.uniform(0.5, 1.05)
            x, y = c + math.cos(a) * rr, c + math.sin(a) * rr
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.5, 3.0), soft=0.8) * 0.9)
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.35), (9.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.26)), win=4.0)


sprite("rfx_jlotus", "direction_z", LO_S, LO_S, LO_N, 4, "saint", zoom=3,
       desc="빛의 연꽃 (세인트 하트 어코드 · 페탈 베일 · 소울본드 · 에버블룸 — 땅에 누움, 위에서 본 꼴): 0 씨앗 빛 · 작은 꽃잎 → 1 ~ 2 꽃잎 세 겹이 열림 → 3 ~ 5 활짝 "
       "(이어 돌림 — 꽃잎 끝 반짝임) → 6 꽃잎이 바깥으로 떠오름 · 빛 알갱이 → 7 흐려짐. 꽃잎 끝 = 반 크기 × 0.9. 빛 칸 = 꽃잎 몸 · 씨방 빛, 심지 칸 = 꽃잎 테 · 잎맥 · "
       "씨방 · 반짝임")(lotus_frame)


# ------------------------------------------------------------ rfx_jbloom (큰 꽃 — 카메라를 봄, 꽃봉오리 → 활짝)

BL_N = 8
BL_S = 384


def _petal_rim(pad, pts, w=3.0):
    m = pad.poly(pts)
    return m, rim_of(m, w)


def bloom_frame(f):
    S = BL_S
    pad = Pad(S, S)
    c = S / 2
    R = S / 2 * 0.85
    rs = random.Random(11100 + f)
    glow = pad.zeros()
    core = pad.zeros()
    if f <= 1:
        k = [0.62, 0.78][f]
        h = R * 1.05 * k
        wb = R * 0.62 * k
        cy = c + R * 0.12
        top = (c, cy - h * 0.62)
        bot = (c, cy + h * 0.38)
        # 꽃잎 셋이 감싼 봉오리: 가운데 앞 꽃잎 · 양옆 꽃잎 (렌즈)
        mid = lens(pad, bot, top, wb * 0.72, 1.0, power=0.55, skew=0.42)
        lft = lens(pad, (c - wb * 0.12, bot[1] - 4), (c - wb * 0.26, top[1] + h * 0.1), wb * 0.62, 1.0, power=0.55, skew=0.42)
        rgt = lens(pad, (c + wb * 0.12, bot[1] - 4), (c + wb * 0.26, top[1] + h * 0.1), wb * 0.62, 1.0, power=0.55, skew=0.42)
        body = np.maximum(mid, np.maximum(lft, rgt))
        glow = np.maximum(glow, body * (0.6 if f == 0 else 0.7))
        glow = np.maximum(glow, pad.radial(c, cy, h * 0.9, 1.6) * (0.35 if f == 0 else 0.55))
        # 빛나는 틈 (꽃잎 사이 — 1 은 벌어지며 더 밝게)
        gap = 1.0 + f * 1.2
        for sgn in (-1, 1):
            pts = bez((c + sgn * wb * 0.08, bot[1] - 2), (c + sgn * wb * (0.34 + f * 0.05), cy), (c + sgn * 2, top[1] + 6), n=24)
            core = np.maximum(core, pad.stroke(pts, gap * 1.6, gap * 0.8, a0=0.9, a1=0.6, taper=0.6, soft=0.8))
            glow = np.maximum(glow, pad.stroke(pts, gap * 7, gap * 4, a0=0.45 + f * 0.2, a1=0.3, taper=0.5, soft=3.0))
        core = np.maximum(core, rim_of(clamp01(body * 2), 2.5) * 0.6)
        core = np.maximum(core, star4(pad, top[0], top[1] - 2, 22.0 + f * 10, 0.9, rot=0.0, thin=0.1, diag=0.35))
    elif f <= 5:
        k = [0, 0, 0.62, 0.85, 1.0, 1.0][f]
        spin = [0, 0, -0.18, -0.08, 0.0, 0.05][f]
        outer = []
        for i in range(6):
            a = -math.pi / 2 + i * TAU / 6 + spin
            pts = _obovate(c, c, a, R * 0.1, R * k, R * 0.66 * k)
            m, rim = _petal_rim(pad, pts, 3.2)
            glow = np.maximum(glow, m * 0.5)
            core = np.maximum(core, rim * 0.8)
            # 잎맥 셋 (뿌리에서 끝으로 부채꼴)
            for d in (-0.22, 0.0, 0.22):
                ex = c + math.cos(a + d * 0.6) * R * k * 0.82
                ey = c + math.sin(a + d * 0.6) * R * k * 0.82
                core = np.maximum(core, pad.stroke([(c + math.cos(a) * R * 0.14, c + math.sin(a) * R * 0.14), (ex, ey)], 1.4, 0.5, a0=0.35, a1=0.1, soft=0.6))
            outer.append((c + math.cos(a) * R * k * 0.95, c + math.sin(a) * R * k * 0.95))
        for i in range(6):
            a = -math.pi / 2 + math.pi / 6 + i * TAU / 6 + spin * 1.6
            pts = _obovate(c, c, a, R * 0.06, R * 0.64 * k, R * 0.5 * k)
            m, rim = _petal_rim(pad, pts, 2.6)
            glow = np.maximum(glow, m * 0.72)
            core = np.maximum(core, rim * 0.85)
        # 가운데: 꽃술 (점 고리) · 빛 덩이
        glow = np.maximum(glow, pad.radial(c, c, R * 0.3 * max(0.6, k), 1.2))
        for q in range(12):
            a = q * TAU / 12 + f * 0.2
            rr = R * 0.13 * max(0.6, k)
            core = np.maximum(core, pad.disc_field(c + math.cos(a) * rr, c + math.sin(a) * rr, 2.6, soft=0.8))
            core = np.maximum(core, pad.stroke([(c, c), (c + math.cos(a) * rr, c + math.sin(a) * rr)], 1.0, a0=0.5, soft=0.4))
        core = np.maximum(core, pad.disc_field(c, c, R * 0.05, soft=1.0))
        if f >= 4:
            # 빛살 (가늘게 · 길고 짧게 번갈아 — 조금씩 돎)
            for q in range(12):
                a = q * TAU / 12 + (f - 4) * 0.13 + 0.1
                L = R * (1.08 if q % 2 == 0 else 0.86)
                core = np.maximum(core, pad.stroke([(c + math.cos(a) * R * 0.22, c + math.sin(a) * R * 0.22), (c + math.cos(a) * L, c + math.sin(a) * L)], 2.2, 0.4,
                                                   a0=0.5, a1=0.0, soft=0.8))
            for q in range(3):
                x, y = outer[(q * 2 + f) % 6]
                core = np.maximum(core, star4(pad, x, y, 15.0, 0.95, rot=rs.uniform(0, 1.2), thin=0.12, diag=0.4))
    else:
        # 6 · 7: 꽃잎이 떨어져 바깥으로 흩날림 (작아지며 돎)
        push = [0.18, 0.3][f - 6] * R
        fade = [0.8, 0.38][f - 6]
        for i in range(12):
            inner = i >= 6
            a = -math.pi / 2 + (math.pi / 6 if inner else 0.0) + (i % 6) * TAU / 6
            L = R * (0.64 if inner else 1.0)
            pts = _obovate(c, c, a, R * 0.08, L, R * (0.5 if inner else 0.66))
            pts = [(x + math.cos(a) * push * (0.7 if inner else 1.0), y + math.sin(a) * push * (0.7 if inner else 1.0)) for x, y in pts]
            cxp, cyp = _centroid(pts)
            pts = _rot_pts(pts, cxp, cyp, rs.uniform(-0.6, 0.6))
            sc = [0.85, 0.65][f - 6]
            pts = [(cxp + (x - cxp) * sc, cyp + (y - cyp) * sc) for x, y in pts]
            m, rim = _petal_rim(pad, pts, 2.6)
            glow = np.maximum(glow, m * (0.6 if inner else 0.48) * fade)
            core = np.maximum(core, rim * 0.75 * fade)
        glow = np.maximum(glow, pad.radial(c, c, R * 0.25, 1.4) * 0.5 * fade)
        for k2 in range(16):
            a = rs.uniform(0, TAU)
            rr = R * rs.uniform(0.3, 1.1)
            core = np.maximum(core, pad.disc_field(c + math.cos(a) * rr, c + math.sin(a) * rr, rs.uniform(1.4, 2.6), soft=0.7) * 0.9 * fade)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (9.0, 0.24)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.26)), win=4.0)


sprite("rfx_jbloom", "rotate_xyz", BL_S, BL_S, BL_N, 4, "saintpetal", zoom=4,
       desc="큰 꽃 (세인트 에버블룸 · 하트 어코드 · 글리머윙 · 페탈 베일 — 카메라를 봄): 0 꽃잎 셋이 감싼 꽃봉오리 (빛나는 틈 · 끝 반짝임) → 1 부풀며 틈이 벌어져 빛이 샘 → "
       "2 ~ 3 겉 꽃잎 여섯 · 속 꽃잎 여섯이 열림 → 4 ~ 5 활짝 (이어 돌림 — 빛살 · 꽃술 · 꽃잎 끝 반짝임) → 6 꽃잎이 떨어져 흩날림 → 7 흐려짐. 꽃 반지름 = 반 크기 × 0.85. "
       "빛 칸 = 꽃잎 몸 · 가운데 빛, 심지 칸 = 꽃잎 테 · 잎맥 · 꽃술 · 빛살")(bloom_frame)


# ------------------------------------------------------------ rfx_jfly (빛나는 나비 — 카메라를 봄, 날갯짓)

FL_N = 8
FL_S = 192
FL_FLAP = [1.0, 0.74, 0.4, 0.16, 0.48, 0.84]


def _wing(c, side, upper, sx):
    """나비 날개 하나 (몸 가운데 c — side = +1 오른쪽 · −1 왼쪽, upper = 위 날개) 다각형 점 — 가로 sx 배 (날갯짓)"""
    if upper:
        ctrl = [(2, -6), (30, -54), (78, -66), (86, -38), (80, -10), (40, 4), (4, 2)]
    else:
        ctrl = [(4, 2), (44, 6), (66, 26), (56, 58), (30, 64), (14, 40), (4, 14)]
    pts = []
    for i in range(len(ctrl) - 1):
        a, b = ctrl[i], ctrl[i + 1]
        m = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        if i == 0:
            pts.append(a)
        nxt = ctrl[i + 2] if i + 2 < len(ctrl) else ctrl[0]
        pts += bez(m, b, ((b[0] + nxt[0]) / 2, (b[1] + nxt[1]) / 2), n=8)
    return [(c + side * x * sx * 0.92, c + y * 0.92) for x, y in pts]


def fly_frame(f):
    S = FL_S
    pad = Pad(S, S)
    c = S / 2
    rs = random.Random(11200 + f)
    glow = pad.zeros()
    core = pad.zeros()
    if f <= 5:
        sx = FL_FLAP[f]
        for side in (-1, 1):
            for upper in (True, False):
                pts = _wing(c, side, upper, sx)
                m = pad.poly(pts)
                glow = np.maximum(glow, m * (0.6 if upper else 0.5))
                core = np.maximum(core, rim_of(m, 2.4) * 0.9)
                # 잎맥 (몸에서 날개 끝으로 셋)
                ends = [(70, -54), (84, -26), (58, -2)] if upper else [(52, 30), (40, 56), (20, 50)]
                for ex, ey in ends:
                    core = np.maximum(core, pad.stroke([(c + side * 4 * sx, c), (c + side * ex * sx, c + ey)], 1.2, 0.5, a0=0.45, a1=0.15, soft=0.5))
                # 눈무늬 (위 날개) · 끝자락 점 (아래 날개)
                if upper and sx > 0.3:
                    ex, ey = c + side * 58 * sx, c - 36
                    ring = clamp01(pad.disc_field(ex, ey, 9 * max(0.5, sx), soft=0.8) - pad.disc_field(ex, ey, 5 * max(0.5, sx), soft=0.8))
                    core = np.maximum(core, ring * 0.9)
                    core = np.maximum(core, pad.disc_field(ex, ey, 2.4 * max(0.5, sx), soft=0.6))
                if not upper and sx > 0.3:
                    for q in range(3):
                        core = np.maximum(core, pad.disc_field(c + side * (30 + q * 9) * sx, c + 52 - q * 10, 2.0, soft=0.6) * 0.8)
        # 몸 · 더듬이
        body = pad.ellipse(c, c + 4, 4.5, 26)
        core = np.maximum(core, body)
        glow = np.maximum(glow, pad.radial(c, c, 34, 1.4) * 0.8)
        for side in (-1, 1):
            ant = bez((c + side * 1.5, c - 20), (c + side * 6, c - 38), (c + side * 18, c - 46), n=12)
            core = np.maximum(core, pad.stroke(ant, 1.4, 1.0, a0=0.85, soft=0.4))
            core = np.maximum(core, pad.disc_field(c + side * 18, c - 46, 2.6, soft=0.6))
        # 아래로 떨어지는 반짝이 (날갯짓마다)
        for q in range(2):
            x = c + rs.uniform(-18, 18)
            y = c + rs.uniform(30, 70)
            core = np.maximum(core, star4(pad, x, y, rs.uniform(5, 9), 0.8, rot=rs.uniform(0, 1.5), thin=0.14, diag=0.4))
    else:
        # 6 반짝이로 흩어짐 · 7 흐려짐
        spread = [1.08, 1.22][f - 6]
        fade = [0.9, 0.42][f - 6]
        for side in (-1, 1):
            for upper in (True, False):
                pts = _wing(c, side, upper, 1.0)
                for q in range(0, len(pts), 3):
                    x, y = pts[q]
                    x = c + (x - c) * spread + rs.uniform(-4, 4)
                    y = c + (y - c) * spread + rs.uniform(-4, 4)
                    core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.4, 2.6), soft=0.6) * fade)
                    glow = np.maximum(glow, pad.disc_field(x, y, 5.0, soft=2.0) * 0.4 * fade)
        core = np.maximum(core, star4(pad, c, c, 40.0 if f == 6 else 22.0, fade, rot=0.3, thin=0.1, diag=0.4))
        glow = np.maximum(glow, pad.radial(c, c, 40, 1.5) * 0.6 * fade)
    return finish(pad, glow, core, halo=((1.3, 0.45), (3.5, 0.36), (8.0, 0.22)), body_k=0.9, core_bloom=((1.1, 0.4), (2.8, 0.26)), win=3.0)


sprite("rfx_jfly", "rotate_xyz", FL_S, FL_S, FL_N, 4, "saint", zoom=0, loop=True,
       desc="빛나는 나비 (세인트 글리머윙 · 에버블룸 나비 떼 — 카메라를 봄): 0 ~ 5 날갯짓 (이어 돌림 — 날개를 폈다 접어 옆으로 선 뒤 다시 폄 · 위 날개 눈무늬 · 아래 날개 끝 점 · "
       "잎맥 · 떨어지는 반짝이) → 6 날개가 반짝이로 흩어짐 → 7 흐려짐. 날개 너비 = 반 크기 × 1.8. 빛 칸 = 날개 몸 · 가운데 빛, 심지 칸 = 날개 테 · 잎맥 · 무늬 · 몸 · "
       "더듬이")(fly_frame)


# ------------------------------------------------------------ rfx_jlance (핏빛 창 — 가는 쪽으로 누움, 창끝 = 오른쪽)

LA_N = 4
LA_W, LA_H = 320, 96


def lance_frame(f):
    W, H = LA_W, LA_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    tip = W * 0.975
    rs = random.Random(11300 + f)
    # 창날 (잎 꼴 — 뒤쪽이 넓음) · 뒤로 휜 미늘 둘
    blade = lens(pad, (192, cy), (tip, cy), 36, 1.0, power=0.8, skew=0.3)
    barbs = pad.polys([[(198, cy - 4), (168, cy - 22), (184, cy - 8), (206, cy - 2)], [(198, cy + 4), (168, cy + 22), (184, cy + 8), (206, cy + 2)]])
    # 자루 (흐르는 핏물 — 살짝 일렁임 · 창날 쪽이 굵음)
    shaft_pts = [(30 + i * 5.8, cy + 1.6 * math.sin((30 + i * 5.8) / 16.0 + f * 1.57)) for i in range(30)]
    shaft = pad.stroke(shaft_pts, 6.0, 12.0, soft=0.8)
    # 자루의 가시 (뒤로 누운 셋 — 위아래 번갈아)
    thorns = pad.zeros()
    for q, x in enumerate((84, 124, 160)):
        s = -1 if q % 2 == 0 else 1
        thorns = np.maximum(thorns, pad.poly([(x + 6, cy + s * 3), (x - 12, cy + s * 15), (x - 2, cy + s * 3)]))
    # 꼬리의 핏방울 (뒤로 떨어져 나감 — 장마다 조금씩 뒤로)
    drops = pad.zeros()
    for q in range(4):
        x = 26 - q * 8 - f * 2.5
        y = cy + 1 + q * 1.5
        r = 4.6 - q * 0.9
        if x > 4:
            drops = np.maximum(drops, pad.disc_field(x, y, r, soft=0.8))
    body = np.maximum.reduce([blade, barbs, shaft, thorns, drops])
    glow = body * 0.95
    glow = np.maximum(glow, pad.radial(tip - 30, cy, 52, 1.4) * 0.35)
    # 심지: 젖은 윤기 (창날 테 · 위 가장자리를 따라 흐르는 빛 — 장마다 앞으로) · 창끝 반짝임
    core = rim_of(clamp01(blade * 2), 2.4) * 0.75
    hl_x = 70 + f * 62
    hl = pad.stroke([(40, cy - 3.5), (300, cy - 6)], 2.4, a0=1.0, soft=0.6) * np.exp(-0.5 * ((X - hl_x) / 34.0) ** 2)
    core = np.maximum(core, hl)
    core = np.maximum(core, pad.stroke([(206, cy - 5), (296, cy - 2)], 1.8, 0.8, a0=0.7, a1=0.3, soft=0.5))
    core = np.maximum(core, pad.stroke([(200, cy), (tip - 6, cy)], 2.6, 0.8, a0=0.55, a1=0.9, soft=0.6))
    core = np.maximum(core, drops * 0.5)
    if f % 2 == 0:
        core = np.maximum(core, star4(pad, tip - 2, cy, 14.0, 0.9, rot=0.0, thin=0.12, diag=0.35))
    return finish(pad, glow, core, halo=((1.2, 0.4), (3.0, 0.3), (6.0, 0.18)), body_k=0.95, core_bloom=((1.0, 0.35), (2.5, 0.22)), win=2.5)


sprite("rfx_jlance", "lookat_direction", LA_W, LA_H, LA_N, 2, "warlock", zoom=0, loop=True,
       desc="핏빛 창 (워록 생귄 랜스 — 가는 쪽으로 누움, 창끝 x 0.975 W): 뒤가 넓은 잎 꼴 창날 · 뒤로 휜 미늘 둘 · 흐르는 핏물 자루 (살짝 일렁임) · 뒤로 누운 가시 셋 · "
       "꼬리에서 떨어져 나가는 핏방울 (이어 돌림 — 위 가장자리를 따라 앞으로 흐르는 윤기 · 창끝 반짝임). 빛 칸 = 핏물 몸 (짙게), 심지 칸 = 젖은 윤기 · 테")(lance_frame)


# ------------------------------------------------------------ rfx_jpool (핏물 웅덩이 — 땅에 누움)

PO_N = 8
PO_S = 256


def pool_frame(f):
    S = PO_S
    pad = Pad(S, S)
    c = S / 2
    R = S / 2 * 0.85
    d, th = polar(pad, c, c)
    rs = random.Random(11400 + f)
    k = [0.5, 0.95, 1.0, 1.0, 1.0, 1.0, 0.64, 0.5][f]
    lum = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.85, 0.42][f]
    wob = 0.05 * np.sin(3 * th + 1.1) + 0.04 * np.sin(7 * th + 2.3) + 0.026 * np.sin(11 * th + 0.4) + 0.016 * np.sin(17 * th + f * 0.5) + 0.02 * np.sin(5 * th + 4.0)
    rr = R * k * (1 + wob)
    body = clamp01((rr - d) / 2.2)
    glow = body * (0.82 + 0.12 * fbm(pad.grid()[0] / 40.0, pad.grid()[1] / 40.0, seed=11410, octaves=3, px=6, py=6))
    if 1 <= f <= 6:
        rq = random.Random(11420)
        for q in range(6):
            a = rq.uniform(0, TAU)
            rd = R * k * rq.uniform(1.0, 1.12)
            glow = np.maximum(glow, pad.disc_field(c + math.cos(a) * rd, c + math.sin(a) * rd, rq.uniform(3.0, 6.5) * min(1.0, k + 0.2), soft=1.0) * 0.9)
    # 젖은 테 · 위왼쪽 윤기
    core = clamp01(1 - np.abs(d - rr * 0.94) / 2.6) * body * 0.55
    hl = clamp01(1 - np.abs(d - rr * 0.78) / 4.0) * clamp01(np.cos(th + 2.3) * 2.2 - 1.0) * body
    core = np.maximum(core, hl * 0.7)
    if f == 0:
        # 튀며 퍼짐: 바깥으로 뻗는 핏줄기 · 방울
        for q in range(12):
            a = q * TAU / 12 + rs.uniform(-0.2, 0.2)
            r0, r1 = R * k * 0.85, R * rs.uniform(0.85, 1.05)
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * r1, c + math.sin(a) * r1)
            glow = np.maximum(glow, pad.stroke([p0, p1], 9, 2, soft=1.0) * 0.9)
            glow = np.maximum(glow, pad.disc_field(p1[0] + math.cos(a) * 8, p1[1] + math.sin(a) * 8, rs.uniform(3, 6), soft=1.0) * 0.9)
            core = np.maximum(core, pad.disc_field(p1[0] + math.cos(a) * 8 - 1, p1[1] + math.sin(a) * 8 - 1, 1.6, soft=0.6) * 0.8)
    if 2 <= f <= 5:
        # 물결 고리 셋 (바깥으로 퍼짐 — 이어 돌림) · 거품
        for q in range(3):
            frac = ((f - 2) / 4.0 + q / 3.0) % 1.0
            rq = R * (0.12 + 0.78 * frac)
            core = np.maximum(core, clamp01(1 - np.abs(d - rq) / 2.0) * (0.55 * (1 - frac) + 0.1) * body)
        for q in range(4):
            a = rs.uniform(0, TAU)
            rb = R * rs.uniform(0.15, 0.7)
            bx, by = c + math.cos(a) * rb, c + math.sin(a) * rb
            br = rs.uniform(4.0, 8.0)
            ring = clamp01(pad.disc_field(bx, by, br, soft=0.8) - pad.disc_field(bx, by, br - 1.8, soft=0.8))
            core = np.maximum(core, ring * 0.85)
            core = np.maximum(core, pad.disc_field(bx - br * 0.35, by - br * 0.35, 1.3, soft=0.5))
    if f == 6:
        # 빨려 듦: 소용돌이 줄 둘
        for q in range(2):
            pts = []
            for i in range(40):
                t = i / 39
                a = q * math.pi + t * 4.2
                r = R * k * (0.95 - 0.85 * t)
                pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
            core = np.maximum(core, pad.stroke(pts, 3.0, 1.0, a0=0.7, a1=0.3, soft=0.8))
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.0, 0.3), (2.5, 0.22)), body_k=1.0, core_bloom=((1.0, 0.3), (2.5, 0.18)), win=3.0)


sprite("rfx_jpool", "direction_z", PO_S, PO_S, PO_N, 4, "warlock", zoom=3,
       desc="핏물 웅덩이 (워록 크림슨 마이어 · 블러드 노바 · 블러드 뱅큇 — 땅에 누움): 0 튀며 퍼짐 (바깥으로 뻗는 핏줄기 · 방울) → 1 고임 → 2 ~ 5 물결 고리 셋이 퍼짐 · 거품 (이어 "
       "돌림) → 6 소용돌이치며 줄어듦 → 7 흐려짐. 웅덩이 반지름 = 반 크기 × 0.85 (테가 일렁임). 빛 칸 = 핏물 몸 (짙게), 심지 칸 = 젖은 테 · 윤기 · 물결 · 거품")(pool_frame)


# ------------------------------------------------------------ rfx_jgeyser (핏물 기둥 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

GE_N = 8
GE_W, GE_H = 192, 384


def geyser_frame(f):
    W, H = GE_W, GE_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    base = H - 8
    rs = random.Random(11500 + f)
    glow = pad.zeros()
    core = pad.zeros()
    usable = H - 40
    top = [0.0, 0.45, 0.74, 0.84, 0.8, 0.62, 0.0, 0.0][f]
    width = [0, 40, 38, 36, 26, 14, 0, 0][f]
    if width > 0:
        ytop = base - top * usable
        nz = fbm(X / 30.0, Y / 30.0 - f * 0.9, seed=11510, octaves=3, px=8, py=16) - 0.5
        t = np.clip((base - Y) / max(1.0, base - ytop), 0, 1)
        # 폭: 아래 넓게 튐 · 가운데 가늘게 · 위로 퍼짐 (분수) — 가장자리가 위로 흐르며 일렁임
        hw = width / 2 * (0.7 + 0.5 * (1 - t) ** 3 + 0.9 * t ** 2.5) + nz * width * 0.35
        col = clamp01((hw - np.abs(X - cx)) * 1.2) * (Y <= base) * smooth(ytop - 2, ytop + 24, Y)
        if f == 5:
            col = col * clamp01(np.sin((Y - ytop) / 20.0 + 0.6) * 3.0)
        glow = np.maximum(glow, col * 0.95)
        # 왼쪽 윤기 줄
        core = np.maximum(core, pad.stroke([(cx - width * 0.24, base - 6), (cx - width * 0.2, ytop + 18)], 2.6, 1.4, a0=0.7, a1=0.4, soft=0.8) * col)
        if f in (1, 2, 3, 4):
            # 꼭대기 물보라: 둥근 덩이 여럿이 부푼 머리 · 위로 솟았다 휘어 떨어지는 줄기 · 끝 방울 (2 · 3 은 왕관처럼 크게)
            spread = [0, 0.7, 1.0, 1.2, 0.85][f]
            for q in range(7):
                a = math.pi + q * math.pi / 6
                rr = width * 0.55 * spread + 4
                bx, by = cx + math.cos(a) * rr * 0.9, ytop + 10 + math.sin(a) * rr * 0.55
                glow = np.maximum(glow, pad.disc_field(bx, by, rs.uniform(7, 12) * (0.6 + 0.4 * spread), soft=1.2) * 0.95)
            glow = np.maximum(glow, pad.ellipse(cx, ytop + 12, width * 0.6 * spread + 6, 12 * spread + 5) * 0.95)
            core = np.maximum(core, pad.disc_field(cx - width * 0.3, ytop + 4, 3.0 * spread + 1.0, soft=0.8) * 0.75)
            n = [0, 6, 9, 12, 8][f]
            for q in range(n):
                a = -math.pi / 2 + (q - (n - 1) / 2) * (0.3 * spread + 0.06) + rs.uniform(-0.08, 0.08)
                L = rs.uniform(22, 44) * spread
                p0 = (cx + math.cos(a) * width * 0.45 * spread, ytop + 8 + math.sin(a) * 8)
                p1 = (p0[0] + math.cos(a) * L, p0[1] + math.sin(a) * L)
                p2 = (p1[0] + math.cos(a) * L * 0.35, p1[1] + L * 0.45 * (0.4 + spread * 0.4))
                arcp = bez(p0, p1, p2, n=14)
                glow = np.maximum(glow, pad.stroke(arcp, 5.0, 2.2, a0=0.95, a1=0.8, soft=0.9))
                glow = np.maximum(glow, pad.disc_field(p2[0], p2[1] + 3, rs.uniform(2.6, 4.2), soft=0.8) * 0.95)
                core = np.maximum(core, pad.disc_field(p2[0] - 1, p2[1] + 2, 1.4, soft=0.5) * 0.8)
        # 발밑 튀는 고리
        if f in (1, 2, 3):
            glow = np.maximum(glow, pad.ellipse(cx, base - 2, width * 1.1, 7) * 0.85)
    if f == 0:
        # 땅이 부풂 · 낮게 튀는 방울
        dome = pad.ellipse(cx, base, 60, 26)
        dome = dome * clamp01(0.6 + (fbm(X / 16.0, Y / 16.0, seed=11530, octaves=2, px=12, py=24) - 0.5) * 2.5)
        glow = np.maximum(glow, dome * 0.95)
        core = np.maximum(core, pad.stroke(arc_pts(cx, base, 50, math.pi * 1.15, math.pi * 1.85, n=24, ry=20), 2.2, a0=0.65, soft=0.8))
        for q in range(9):
            a = -math.pi / 2 + (q - 4) * 0.3
            p1 = (cx + math.cos(a) * rs.uniform(52, 76), base - 16 + math.sin(a) * rs.uniform(26, 56))
            glow = np.maximum(glow, pad.disc_field(p1[0], p1[1], rs.uniform(3, 5.5), soft=1.0) * 0.9)
    if f >= 4:
        # 떨어지는 방울 (아래로 늘어진 꼴)
        n = [0, 0, 0, 0, 12, 16, 10, 6][f]
        for q in range(n):
            x = cx + rs.uniform(-66, 66)
            y = base - rs.uniform(0.05, 0.8) * usable * (0.55 if f >= 6 else 1.0)
            r = rs.uniform(2.5, 5.0)
            glow = np.maximum(glow, pad.stroke([(x, y - r * 3), (x, y)], r * 0.6, r * 2, soft=0.8) * 0.9)
            core = np.maximum(core, pad.disc_field(x - r * 0.3, y - r * 0.2, r * 0.35, soft=0.5) * 0.7)
    if f in (5, 6, 7):
        # 땅에 튀는 작은 왕관들
        for q in range(3 if f >= 6 else 2):
            x = cx + (q - 1) * 46 + rs.uniform(-10, 10)
            sc = [0, 0, 0, 0, 0, 0.7, 1.0, 0.6][f]
            glow = np.maximum(glow, pad.ellipse(x, base - 2, 16 * sc, 6 * sc) * 0.8)
            for e in range(5):
                a = -math.pi / 2 + (e - 2) * 0.45
                p1 = (x + math.cos(a) * 16 * sc, base - 4 + math.sin(a) * 18 * sc)
                glow = np.maximum(glow, pad.disc_field(p1[0], p1[1], 2.4 * sc + 0.6, soft=0.8) * 0.9)
    lum = [1.0, 1.0, 1.0, 1.0, 0.95, 0.85, 0.7, 0.4][f]
    return finish(pad, glow * lum, core * lum, halo=((1.0, 0.32), (2.5, 0.24), (5.0, 0.14)), body_k=1.0, core_bloom=((1.0, 0.3), (2.5, 0.18)), win=2.5)


sprite("rfx_jgeyser", "lookat_y", GE_W, GE_H, GE_N, 4, "warlock", zoom=3,
       desc="핏물 기둥 (워록 크림슨 마이어 솟아오름 · 생귄 랜스 · 블러드 노바 · 블러드 뱅큇 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅): 0 땅이 부풀며 낮게 튐 → 1 ~ 2 솟구침 "
       "(둥근 머리 · 2 꼭대기 왕관 물보라) → 3 가장 높이 (넓은 왕관) → 4 가늘어지며 방울이 떨어짐 → 5 토막나 떨어짐 · 땅에 튐 → 6 땅에 튀는 작은 왕관들 → 7 흐려짐. "
       "높이 = 반 너비 × 4. 빛 칸 = 핏물 몸 (짙게), 심지 칸 = 왼쪽 윤기 · 방울 반짝임")(geyser_frame)


# ------------------------------------------------------------ rfx_jheart (피의 심장 — 카메라를 봄, 고동침)

HE_N = 8
HE_S = 256


def _heart_mask(pad, c, cy, r):
    """심장 꼴 (둥근 위 봉우리 둘 · 아래 뾰족 — 하트 곡선, 가운데 c · cy, 반 너비 약 r)"""
    pts = []
    for i in range(96):
        t = i / 96 * TAU
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((c + x / 16 * r, cy + (y + 2.5) / 16 * r))
    return gblur(pad.poly(pts), 1.5)


def heart_frame(f):
    S = HE_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    c = S / 2
    R = S / 2 * 0.55
    rs = random.Random(11600 + f)
    glow = pad.zeros()
    core = pad.zeros()
    if f == 0:
        # 핏물이 감겨 모임 (나선 넷 · 가운데 작은 심장)
        for q in range(4):
            pts = []
            for i in range(30):
                t = i / 29
                a = q * math.pi / 2 + t * 3.4
                r = R * (1.32 - 1.0 * t)
                pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
            glow = np.maximum(glow, pad.stroke(pts, 3.0, 9.0, a0=0.5, a1=0.95, soft=1.0))
            core = np.maximum(core, pad.stroke(pts, 1.0, 2.6, a0=0.3, a1=0.8, soft=0.5))
        m = _heart_mask(pad, c, c, R * 0.5)
        glow = np.maximum(glow, m * 0.95)
        core = np.maximum(core, rim_of(clamp01(m * 2), 2.0) * 0.7)
    elif f <= 5:
        k = [0, 0.9, 1.0, 1.1, 0.96, 1.25][f]
        r = R * k
        m = _heart_mask(pad, c, c, r)
        shade = fbm(X / S * 5.0, Y / S * 5.0 + f * 0.3, seed=11610, octaves=3, px=10, py=10)
        glow = np.maximum(glow, m * (0.78 + 0.22 * shade))
        glow = np.maximum(glow, pad.radial(c, c, r * 1.75, 1.4) * 0.4)
        # 핏줄 (가지 — 장마다 밝기가 맥동)
        vein_a = [0, 0.55, 0.95, 0.7, 0.8, 1.0][f]
        rv = random.Random(11620)
        for q in range(6):
            x, y = c + rv.uniform(-0.6, 0.6) * r, c - r * rv.uniform(0.3, 0.6)
            pts = [(x, y)]
            ang = math.pi / 2 + rv.uniform(-0.8, 0.8)
            for s in range(7):
                ang += rv.uniform(-0.5, 0.5)
                x += math.cos(ang) * r * 0.16
                y += math.sin(ang) * r * 0.16
                pts.append((x, y))
            core = np.maximum(core, pad.stroke(pts, 2.4, 0.8, a0=vein_a, a1=vein_a * 0.4, soft=0.6) * m)
        # 윤기 (위왼쪽 봉우리의 반달) · 테
        hl = pad.disc_field(c - r * 0.55, c - r * 0.38, r * 0.26, soft=2.0) * (1 - pad.disc_field(c - r * 0.45, c - r * 0.3, r * 0.24, soft=2.0))
        core = np.maximum(core, hl * 0.9)
        core = np.maximum(core, rim_of(clamp01(m * 2), 2.2) * 0.55)
        # 둘레를 도는 금빛 불티 (심지)
        for q in range(5):
            a = q * TAU / 5 + f * 0.55
            rr = r * 1.45
            core = np.maximum(core, pad.disc_field(c + math.cos(a) * rr, c + math.sin(a) * rr * 0.8, 2.2, soft=0.6) * 0.9)
    else:
        # 6 터짐 · 7 방울이 흩어짐
        fade = [1.0, 0.45][f - 6]
        spread = [1.0, 1.7][f - 6]
        ring = clamp01(1 - np.abs(np.hypot(X - c, Y - c) - R * (1.25 if f == 6 else 1.6)) / 3.0) * 0.7 * fade
        core = np.maximum(core, ring)
        for q in range(22):
            a = rs.uniform(0, TAU)
            r0 = R * rs.uniform(0.3, 0.8) * spread
            L = R * rs.uniform(0.25, 0.6)
            p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
            p1 = (c + math.cos(a) * (r0 + L), c + math.sin(a) * (r0 + L))
            glow = np.maximum(glow, streak(pad, p0, p1, rs.uniform(4.0, 7.0), 0.95 * fade))
            core = np.maximum(core, pad.disc_field(p1[0], p1[1], 1.6, soft=0.5) * 0.8 * fade)
        if f == 6:
            glow = np.maximum(glow, pad.radial(c, c, R * 1.3, 1.2) * 0.8)
            core = np.maximum(core, star4(pad, c, c, R * 1.1, 0.9, rot=0.2, thin=0.1, diag=0.4))
    return finish(pad, glow, core, halo=((1.2, 0.42), (3.0, 0.34), (7.0, 0.22)), body_k=0.95, core_bloom=((1.0, 0.36), (2.6, 0.24)), win=3.0)


sprite("rfx_jheart", "rotate_xyz", HE_S, HE_S, HE_N, 4, "warlock", zoom=2,
       desc="피의 심장 (워록 블러드 노바 · 블러드 뱅큇 피의 역병 · 생귄 랜스 — 카메라를 봄): 0 핏물 나선 넷이 감겨 모여 작은 심장 → 1 ~ 4 고동침 (이어 돌림 — 부풀었다 "
       "줄어듦 · 핏줄 맥동 · 윤기 · 둘레를 도는 금빛 불티) → 5 크게 부풂 → 6 터짐 (고리 · 사방으로 튀는 핏줄기) → 7 방울이 흩어짐. 심장 크기 = 반 크기 × 0.55. 빛 칸 = "
       "심장 몸 (짙게), 심지 칸 = 핏줄 · 윤기 · 테 · 불티")(heart_frame)


# ------------------------------------------------------------ rfx_jpyre (핏빛 불기둥 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

PY_N = 8
PY_W, PY_H = 192, 448


def pyre_frame(f):
    W, H = PY_W, PY_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    base = H - 10
    rs = random.Random(11700 + f)
    hk = [0.3, 0.65, 0.92, 1.0, 0.96, 1.0, 0.8, 0.6][f]
    lum = [0.95, 1.0, 1.0, 1.0, 1.0, 1.0, 0.75, 0.45][f]
    sway = [0, 4, -6, 5, -4, 6, 0, 0][f]
    # 불의 혀를 아래에서 위로 쌓음 (아래 넓은 불 · 가운데 · 위의 가는 혀 — _flame_field: 아래 둥근 · 위 뾰족 · 위로 흐르는 가장자리)
    tongues = [(0, 0.0, 54, 3.4, 1.0), (0, 0.34, 38, 3.0, 0.95), (0, 0.6, 26, 2.7, 0.9), (-1, 0.04, 28, 2.2, 0.85), (1, 0.07, 28, 2.4, 0.85)]
    body = pad.zeros()
    hot = pad.zeros()
    for side, up, r, tall, a in tongues:
        rr = r * (0.55 + 0.45 * hk)
        x = cx + side * 32 + sway * up - 6
        y = base - rr * 0.6 - up * (H - 60) * hk
        if y - rr * tall < 8:
            continue
        body = np.maximum(body, _flame_field(X, Y, x, y, rr, f, 11710 + int(up * 100) + side * 7, tall=tall) * a)
        if side == 0:
            hot = np.maximum(hot, _flame_field(X, Y, x, y + rr * 0.15, rr * 0.55, f, 11760 + int(up * 100), tall=tall * 0.9) * a)
    if f >= 6:
        hole = fbm(X / 40.0, Y / 40.0 + f * 0.6, seed=11750, octaves=3, px=8, py=16)
        mask = clamp01((hole - (0.38 if f == 6 else 0.52)) * 3.4 + 0.2)
        body = body * mask
        hot = hot * mask * 0.5
    # 혀 사이를 잇는 기둥 빛 (세로로 긴 부드러운 빛)
    colh = (H - 70) * hk
    body = np.maximum(body, pad.radial(cx + sway * 0.3, base - colh * 0.42, W * 0.24, 1.2, sy=colh / (W * 0.24) * 0.5) * 0.75 * (1.0 if f < 6 else 0.4))
    glow = body * 0.95
    core = hot * 0.95
    # 발밑 불 고리 (납작한 타원)
    ring = clamp01(1 - np.abs(np.hypot(X - cx, (Y - base + 4) / 0.22) - W * 0.36) / 4.0) * clamp01((Y - (base - 24)) / 6.0)
    glow = np.maximum(glow, ring * 0.7 * lum)
    # 불티 (위로)
    if f >= 2:
        for q in range(8 + 3 * min(4, f - 2)):
            x = cx + rs.uniform(-0.4, 0.4) * W
            y = base - rs.uniform(0.35, 0.95) * (H - 40) * hk
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.3, 2.6), soft=0.6) * 0.9)
            glow = np.maximum(glow, pad.disc_field(x, y, 5.0, soft=2.0) * 0.4)
    if f == 0:
        core = np.maximum(core, star4(pad, cx, base - 24, 34.0, 1.0, rot=0.2, thin=0.12, diag=0.4))
    return finish(pad, glow * lum, core * lum, halo=((1.4, 0.42), (3.5, 0.34), (8.0, 0.22)), body_k=0.92, core_bloom=((1.2, 0.32), (3.0, 0.22)), win=3.0)


sprite("rfx_jpyre", "lookat_y", PY_W, PY_H, PY_N, 4, "warlockfire", zoom=3,
       desc="핏빛 불기둥 (워록 애시본 — 세 번 불타 되살아남 · 태세의 발밑 불길, 세워 카메라 쪽으로 돎, 아래 끝 = 땅): 0 불붙음 (번쩍) → 1 ~ 2 솟구침 → 3 ~ 5 타오름 (이어 "
       "돌림 — 가운데 기둥 · 곁 기둥 둘 · 위로 흐르는 가장자리 · 불티 · 발밑 불 고리) → 6 구멍이 뚫리며 무너짐 → 7 흩어짐. 높이 = 반 너비 × 4.67. 빛 칸 = 불 몸, 심지 칸 = "
       "안쪽 뜨거운 불 · 불티")(pyre_frame)


# ------------------------------------------------------------ 2026-11-06 수호기사 2차 (피닉스 가드 · 다크 나이트 — 다시: 푸른 불사조 · 타락한 흑기사)

COLORS.setdefault("phoenix", ((0.26, 0.62, 1.0), (0.9, 0.97, 1.0)))
COLORS.setdefault("darkknight", ((0.16, 0.03, 0.06), (1.0, 0.22, 0.28)))


def _jag(rs, p0, p1, n, amp):
    """들쭉날쭉한 금 (p0 → p1 사이 n 마디 · 옆으로 ±amp)"""
    pts = [p0]
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / L, dx / L
    for i in range(1, n):
        t = i / n
        o = rs.uniform(-amp, amp)
        pts.append((p0[0] + dx * t + nx * o, p0[1] + dy * t + ny * o))
    pts.append(p1)
    return pts


# ================================================================ 피닉스 가드 — 푸른 불꽃 날개 · 불사조

# 날개 하나의 뼈대 (오른쪽 날개 — 바깥 = +x, 위 = −y, 어깨 = (0, 0)): 팔 (어깨 → 팔꿈치 → 손목) · 깃 끝 열 (첫째 깃 넷 = 손목에서 · 둘째 깃 여섯 = 팔을 따라)
FW_ARM = [(0.0, 0.0), (40.0, -36.0), (88.0, -64.0), (132.0, -86.0)]
FW_TIPS = [(238.0, -132.0), (236.0, -96.0), (226.0, -62.0), (208.0, -32.0), (182.0, -6.0), (152.0, 14.0), (120.0, 28.0), (88.0, 36.0), (56.0, 38.0), (24.0, 34.0)]
FW_ROOT = [1.0, 1.0, 0.97, 0.9, 0.78, 0.64, 0.5, 0.36, 0.22, 0.08]
FW_WIDTH = [17.0, 20.0, 22.0, 24.0, 26.0, 27.0, 27.0, 26.0, 24.0, 20.0]


def _arm_at(u):
    """팔 꺾은선을 따라 몫 u (0 = 어깨 · 1 = 손목) 의 점"""
    P = FW_ARM
    seg = [math.hypot(P[i + 1][0] - P[i][0], P[i + 1][1] - P[i][1]) for i in range(len(P) - 1)]
    total = sum(seg)
    d = u * total
    for i, s in enumerate(seg):
        if d <= s or i == len(seg) - 1:
            t = 0 if s <= 0 else min(1.0, d / s)
            return (P[i][0] + (P[i + 1][0] - P[i][0]) * t, P[i][1] + (P[i + 1][1] - P[i][1]) * t)
        d -= s
    return P[-1]


def wing_geom(cx, cy, side, ang, span, scale=1.0, curl=0.0):
    """오른쪽 날개 뼈대를 (어깨 cx + side × 12, cy) 로 옮김 — ang = 날갯짓 (어깨 둘레 회전, + = 아래로) · span = 펼친 몫 (가로) ·
    curl = 감싸기 (손목 바깥의 첫째 깃을 손목 둘레로 아래 · 안쪽으로 접음 — 몸 앞에서 깃 끝이 엇갈림)"""
    ox = cx + side * 12.0 * scale
    ca, sa = math.cos(ang), math.sin(ang)
    wx, wy = FW_ARM[-1]
    cc, sc = math.cos(2.1 * curl), math.sin(2.1 * curl)

    def tf(p):
        x, y = p[0], p[1]
        if curl and x > wx * 0.55:
            # 손목에서 멀수록 더 접음 (둘째 깃은 반만)
            k = clamp01(np.float32((x - wx * 0.55) / (wx * 0.45)))
            c2, s2 = math.cos(2.1 * curl * float(k)), math.sin(2.1 * curl * float(k))
            dx, dy = x - wx, y - wy
            x, y = wx + dx * c2 - dy * s2, wy + dx * s2 + dy * c2
        x = x * span
        xr = x * ca - y * sa
        yr = x * sa + y * ca
        return (ox + side * xr * scale, cy + yr * scale)

    arm = [tf(p) for p in FW_ARM]
    feathers = []
    for i, tip in enumerate(FW_TIPS):
        root = _arm_at(FW_ROOT[i])
        feathers.append((tf(root), tf(tip), FW_WIDTH[i] * scale * (0.85 + 0.15 * span)))
    return arm, feathers, tf


def flame_tongue(pad, base, d, L, w, sway, a=1.0, skew=0.35):
    """불의 혀 (base 에서 d 쪽 · 길이 L · 폭 w · 끝이 sway 만큼 옆으로 휨) — 굽은 렌즈"""
    dx, dy = d
    n = math.hypot(dx, dy) or 1.0
    dx, dy = dx / n, dy / n
    nx, ny = -dy, dx
    pts = []
    for i in range(17):
        t = i / 16
        off = sway * math.sin(t * math.pi * 0.9) * t
        pts.append((base[0] + dx * L * t + nx * off, base[1] + dy * L * t + ny * off))

    def wf(t):
        u = t / max(1e-6, skew) if t <= skew else (1 - t) / max(1e-6, 1 - skew)
        return w / 2 * math.sin(math.pi / 2 * min(1.0, u)) ** 0.7
    return pad.poly(taper_poly(pts, wf), a)


def rise(pad, m, dist, steps=14):
    """위로 번지는 불 (m 을 위로 0 ~ dist 칸 밀어 겹침 — 위로 갈수록 옅게)"""
    out = m.copy()
    for k in range(1, steps + 1):
        d = int(dist * pad.ss * k / steps)
        if d <= 0 or d >= m.shape[0]:
            continue
        sh = np.zeros_like(m)
        sh[:-d, :] = m[d:, :]
        out = np.maximum(out, sh * (1 - 0.62 * k / steps))
    return out


def flames_of(pad, body, f, seed, dist, scale=1.0, thresh=0.3):
    """몸에서 위로 일렁이는 불의 혀 (빛 · 심지): 위로 번진 몸 × 세로 결 노이즈 (장마다 위로 흐름)"""
    X, Y = pad.grid()
    n1 = fbm(X / (22.0 * scale), Y / (64.0 * scale) + f * 0.6, seed=seed, octaves=3, px=24, py=8)
    n2 = fbm(X / (40.0 * scale), Y / (40.0 * scale) + f * 0.35, seed=seed + 11, octaves=2, px=12, py=12)
    n1 = clamp01(n1)
    n2 = clamp01(n2)
    up = rise(pad, gblur(body, 1.5 * pad.ss), dist)
    live = clamp01(up * 8.0)
    tongues = clamp01(up * 1.25 - (thresh + 0.7 * n1)) * live
    hot = clamp01(up * 1.3 - (thresh + 0.32 + 0.6 * n1)) * live
    return tongues, hot, n2


def draw_wing(pad, glow, core, cx, cy, side, ang, span, f, rs, scale=1.0, flame=1.0, alpha=1.0, curl=0.0):
    """불꽃 날개 하나를 glow · core 에 더함 (뾰족한 깃 · 덮깃 → 위로 일렁이는 불꽃 결 · 깃 속 · 앞 테 · 첫째 깃 끝의 긴 불의 혀)"""
    arm, feathers, tf = wing_geom(cx, cy, side, ang, span, scale, curl)
    body = pad.zeros()
    inner = pad.zeros()
    for root, tip, w in feathers:
        body = np.maximum(body, lens(pad, root, tip, w, 1.0, power=0.55, skew=0.38))
        mid = (root[0] + (tip[0] - root[0]) * 0.1, root[1] + (tip[1] - root[1]) * 0.1)
        inner = np.maximum(inner, lens(pad, mid, tip, w * 0.32, 1.0, power=0.6, skew=0.3))
    cover = [arm[0]] + arm[1:] + [(r[0] + (t[0] - r[0]) * 0.45, r[1] + (t[1] - r[1]) * 0.45) for r, t, _ in feathers] + [arm[0]]
    body = np.maximum(body, pad.poly(cover))
    tongues, hot, n2 = flames_of(pad, body, f, 8100 + (3 if side > 0 else 0), 46 * scale * flame, scale)
    glow[:] = np.maximum(glow, body * (0.3 + 0.32 * n2) * alpha)
    glow[:] = np.maximum(glow, tongues * 0.9 * alpha)
    glow[:] = np.maximum(glow, rim_of(body, 2.4 * scale) * 0.55 * alpha)
    core[:] = np.maximum(core, hot * 0.85 * alpha)
    core[:] = np.maximum(core, inner * (0.35 + 0.35 * n2) * alpha)
    # 깃대 (옅게) · 첫째 깃 끝의 긴 불의 혀 · 반짝임
    for i, (root, tip, w) in enumerate(feathers):
        core[:] = np.maximum(core, pad.stroke([root, tip], 1.4 * scale, 0.5 * scale, a0=0.35 * alpha, a1=0.15 * alpha, soft=0.5))
        if i < 4:
            # 첫째 깃 끝: 위로 휘어 오르는 가는 불줄기 (깃과 이어짐)
            d = (tip[0] - root[0], tip[1] - root[1])
            Ld = math.hypot(*d) or 1.0
            pts = []
            for s in np.linspace(0, 1, 14):
                bend = s * s
                px = tip[0] - d[0] / Ld * 30 * scale + d[0] / Ld * (30 + 26 * flame) * scale * s
                py = tip[1] - d[1] / Ld * 30 * scale + (d[1] / Ld * (30 + 26 * flame) * s - 40 * flame * bend) * scale
                px += side * 7 * scale * math.sin(f * 1.7 + i * 1.3 + s * 4.0) * s
                pts.append((px, py))
            glow[:] = np.maximum(glow, pad.stroke(pts, w * 0.5, 1.0, a0=0.75 * alpha, a1=0.1 * alpha, soft=2.0))
            core[:] = np.maximum(core, pad.stroke(pts, w * 0.16, 0.5, a0=0.8 * alpha, a1=0.1 * alpha, soft=0.8))
            if rs.random() < 0.6:
                core[:] = np.maximum(core, star4(pad, pts[-3][0], pts[-3][1], (4 + rs.random() * 4) * scale, 0.6 * alpha, rot=rs.uniform(0, 1.5)))
    # 앞 테 (팔) — 밝은 불꽃 줄
    core[:] = np.maximum(core, pad.stroke(arm, 3.0 * scale, 1.4 * scale, a0=0.7 * alpha, a1=0.45 * alpha, soft=1.4))
    glow[:] = np.maximum(glow, pad.stroke(arm, 15.0 * scale, 9.0 * scale, a0=0.7 * alpha, a1=0.5 * alpha, soft=3.0))
    return body


# ------------------------------------------------------------ rfx_jfwings (불꽃 날개 한 쌍 — 카메라를 봄, 등 = (256, 184))

FW_N = 8
FW_W, FW_H = 512, 416
FW_C = (256.0, 262.0)
# (날갯짓 각 · 펼침 · 불 · 밝기 · 감김)
FW_POSE = [(0.5, 0.35, 0.5, 0.45, 0.0), (0.32, 0.72, 0.8, 0.85, 0.0), (-0.22, 0.94, 1.0, 1.0, 0.0), (0.04, 0.96, 1.0, 1.0, 0.0), (0.3, 0.92, 0.9, 1.0, 0.0),
           (0.06, 0.96, 1.15, 1.0, 0.0), (0.62, 0.86, 0.9, 1.0, 1.0), (0.0, 1.0, 0.0, 0.8, 0.0)]


def fwings_frame(f):
    pad = Pad(FW_W, FW_H)
    X, Y = pad.grid()
    cx, cy = FW_C
    rs = random.Random(13000 + f)
    glow = pad.zeros()
    core = pad.zeros()
    ang, span, flame, lum, curl = FW_POSE[f]
    if f == 0:
        # 모임: 둘레에서 등으로 빨려 드는 불티 · 작은 날개 윤곽
        for q in range(26):
            a = rs.uniform(-math.pi * 0.95, -math.pi * 0.05) if q % 3 else rs.uniform(0, TAU)
            d = rs.uniform(120, 230)
            p1 = (cx + math.cos(a) * d * 0.45, cy + math.sin(a) * d * 0.4)
            p0 = (cx + math.cos(a) * d, cy + math.sin(a) * d * 0.8)
            core = np.maximum(core, streak(pad, p0, p1, rs.uniform(2.0, 3.6), 0.9))
        for side in (-1, 1):
            draw_wing(pad, glow, core, cx, cy, side, ang, span, f, rs, flame=flame, alpha=0.5)
        glow = np.maximum(glow, pad.radial(cx, cy, 70, power=1.4) * 0.8)
        core = np.maximum(core, pad.radial(cx, cy, 26, power=1.2))
    elif f == 7:
        # 흩어짐: 불꽃 깃털이 바깥으로 · 불티
        for side in (-1, 1):
            _, feathers, _ = wing_geom(cx, cy, side, 0.0, 1.0)
            for root, tip, w in feathers:
                for k in range(2):
                    t = rs.uniform(0.3, 1.0)
                    px, py = root[0] + (tip[0] - root[0]) * t, root[1] + (tip[1] - root[1]) * t
                    px += side * rs.uniform(10, 40)
                    py -= rs.uniform(0, 30)
                    a = math.atan2(tip[1] - root[1], tip[0] - root[0]) + rs.uniform(-0.6, 0.6)
                    L = rs.uniform(20, 34)
                    p0 = (px - math.cos(a) * L / 2, py - math.sin(a) * L / 2)
                    p1 = (px + math.cos(a) * L / 2, py + math.sin(a) * L / 2)
                    glow = np.maximum(glow, lens(pad, p0, p1, w * 0.5, 0.6))
                    core = np.maximum(core, pad.stroke([p0, p1], 1.6, 0.6, a0=0.6, a1=0.3))
        for q in range(30):
            x, y = cx + rs.uniform(-230, 230), cy + rs.uniform(-150, 60)
            core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.2, 2.6), soft=0.6) * 0.7)
    else:
        for side in (-1, 1):
            draw_wing(pad, glow, core, cx, cy, side, ang, span, f, rs, flame=flame, curl=curl)
        # 등의 불 (두 날개의 뿌리)
        glow = np.maximum(glow, pad.radial(cx, cy, 46, power=1.2) * 0.85)
        core = np.maximum(core, pad.radial(cx, cy, 18, power=1.0) * 0.9)
        if f == 6:
            # 감쌈: 감긴 깃 끝 사이 (몸 앞 아래) 의 빛
            glow = np.maximum(glow, pad.radial(cx, cy + 70, 70, power=1.8, sy=0.8) * 0.3)
    glow, core = glow * lum, core * lum
    return finish(pad, glow, core, halo=((1.4, 0.4), (4.0, 0.32), (10.0, 0.22)), body_k=0.9, core_bloom=((1.2, 0.34), (3.0, 0.22)), win=4.0)


sprite("rfx_jfwings", "rotate_xyz", FW_W, FW_H, FW_N, 4, "phoenix", zoom=2,
       desc="푸른 불꽃 날개 한 쌍 (피닉스 가드 — 카메라를 봄, 등 (두 날개의 뿌리) = 칸 가운데에서 아래로 반 높이 × 0.15 · 날개 끝 사이 약 반 너비 × 1.9): 0 둘레의 불티가 등으로 빨려 들며 "
       "작은 날개 → 1 펼쳐짐 → 2 ~ 5 날갯짓 (이어 돌림 — 위 · 가운데 · 아래 · 가운데, 깃 끝마다 위로 휘는 불의 혀) → 6 감쌈 (깃 끝이 몸 앞 아래로 감김) → 7 불꽃 깃털로 흩어짐. "
       "빛 칸 = 깃 · 덮깃 몸 · 불의 혀 · 테, 심지 칸 = 깃대 · 앞 테 · 불의 혀 속 · 반짝임")(fwings_frame)


# ------------------------------------------------------------ rfx_jphoenix (푸른 불사조 — 카메라를 봄, 가슴 = (256, 250))

PH_N = 8
PH_S = 512
# (날갯짓 각 · 펼침 · 밝기)
PH_POSE = [(0.2, 0.6, 0.55), (-0.2, 1.0, 1.0), (0.26, 0.97, 1.0), (0.0, 1.0, 1.0), (-0.22, 1.0, 1.0), (-1.0, 0.8, 1.0), (0.06, 1.04, 1.0), (0.1, 1.0, 0.55)]
PH_WING_K = 0.8
PH_SHOULDER = 16.0


def _lerp_prof(prof, t):
    """[(t, v), …] 꺾은선 값"""
    for i in range(len(prof) - 1):
        t0, v0 = prof[i]
        t1, v1 = prof[i + 1]
        if t <= t1 or i == len(prof) - 2:
            k = 0.0 if t1 <= t0 else min(1.0, max(0.0, (t - t0) / (t1 - t0)))
            k = k * k * (3 - 2 * k)
            return v0 + (v1 - v0) * k
    return prof[-1][1]


def _curl_pts(pts, turn, r0, n=14):
    """줄 끝에 말림 (끝 방향에서 turn 라디안만큼 돌며 반지름 r0 → 0.35 r0) 을 이어 붙임"""
    (x1, y1), (x0, y0) = pts[-1], pts[-2]
    a = math.atan2(y1 - y0, x1 - x0)
    out = list(pts)
    x, y = x1, y1
    step = abs(turn) * r0 / n
    for i in range(1, n + 1):
        k = i / n
        a += turn / n
        r = r0 * (1 - 0.65 * k)
        x += math.cos(a) * step * r / r0
        y += math.sin(a) * step * r / r0
        out.append((x, y))
    return out


def _ph_tail(pad, TL, T, f, lum):
    """불사조 꼬리 = 깃 부채 (몸 아래 (0, 54) 에서 아래로 ±32° — 깃 아홉이 겹침, 가운데가 가장 김 · 바깥 깃일수록 바깥으로 휨): 깃마다 렌즈 꼴 몸 (가운데 깃대 ·
    비스듬한 깃가지 결 · 끝 가까이 눈 무늬 테와 점). 바깥 깃부터 그려 안쪽 깃이 위에 (앞 깃 둘레를 어둡게 — 겹친 깃이 갈라져 보임) — 부채 전체가 장마다 일렁임.
    TL · T = 몸 좌표 → 칸 (내리꽂음은 180° 돎). (빛, 심지, 깃 모양)"""
    glow = pad.zeros()
    core = pad.zeros()
    mass = pad.zeros()
    sway = math.sin(f * 0.9) * math.radians(4.0)
    order = [-4, 4, -3, 3, -2, 2, -1, 1, 0]
    for k in order:
        th = math.radians(90.0 + k * 8.0) + sway
        L = 186.0 - abs(k) * 13.0
        W = 28.0 - abs(k) * 2.0
        bow = k * 0.14 + math.sin(f * 1.1 + k) * 0.04
        r0 = (k * 2.5, 54.0)
        d = (math.cos(th), math.sin(th))
        nrm = (-d[1], d[0])
        pts = []
        for i in range(31):
            t = i / 30
            off = bow * L * 0.5 * t * t
            pts.append((r0[0] + d[0] * L * t + nrm[0] * off * (-1 if k < 0 else 1), r0[1] + d[1] * L * t + nrm[1] * off * (-1 if k < 0 else 1)))

        def wf(t, W=W):
            return W / 2 * (math.sin(math.pi * min(1.0, 0.04 + t * 0.98)) ** 0.75) * (0.55 + 0.45 * math.sin(math.pi * min(1.0, t * 1.3)))
        m = pad.poly(TL(taper_poly(pts, wf)))
        # 겹침: 앞 깃 둘레를 어둡게 (뒤 깃과 갈라짐)
        edge = clamp01(gblur(m, 2.2 * SS) * 2.6)
        glow = glow * (1 - 0.55 * edge)
        core = core * (1 - 0.7 * edge)
        mass = np.maximum(mass, m)
        glow = np.maximum(glow, m * (0.62 * lum))
        glow = np.maximum(glow, rim_of(m, 1.6) * 0.75 * lum)
        # 깃대 · 깃가지 결
        core = np.maximum(core, pad.stroke(TL(pts[3:27]), 2.4, 0.9, a0=0.8 * lum, a1=0.5 * lum, soft=0.6))
        for i in range(3, 25, 2):
            t = i / 30
            px, py = pts[i]
            w = wf(t) * 0.92
            for s in (-1, 1):
                q = (px + nrm[0] * w * s * (-1 if k < 0 else 1) + d[0] * w * 0.9, py + nrm[1] * w * s * (-1 if k < 0 else 1) + d[1] * w * 0.9)
                core = np.maximum(core, pad.stroke(TL([(px, py), q]), 0.9, 0.5, a0=0.28 * lum, a1=0.05 * lum, soft=0.5))
        # 눈 무늬 (끝 가까이 테 · 가운데 점)
        ex, ey = pts[24]
        e_dir = (pts[26][0] - pts[22][0], pts[26][1] - pts[22][1])
        el = math.hypot(*e_dir) or 1.0
        e_dir = (e_dir[0] / el, e_dir[1] / el)
        ring = lens(pad, T(ex - e_dir[0] * 16, ey - e_dir[1] * 16), T(ex + e_dir[0] * 13, ey + e_dir[1] * 13), W * 0.62, 1.0, power=0.55)
        core = np.maximum(core, rim_of(ring, 1.4) * 0.85 * lum)
        core = np.maximum(core, pad.disc_field(*T(ex + e_dir[0] * 1.0, ey + e_dir[1] * 1.0), W * 0.13, soft=1.0) * lum)
        glow = np.maximum(glow, ring * 0.85 * lum)
    return glow, core, mass


def _phoenix(pad, cx, cy, f, rs, lum=1.0, inv=False):
    """몸 · 목 · 옆을 본 머리 (갈고리 부리 · 성난 눈 · 뒤로 흐르는 불꽃 볏 넷) · 목의 갈기 · 가슴의 빛 · 꼬리 (_ph_tail — 깃 부채).
    inv = 머리가 아래 (내리꽂음 — 몸 전체 180°). (빛, 심지, 몸 모양 — _ph_over 로 날개 위에 얹음)"""
    glow = pad.zeros()
    core = pad.zeros()

    def T(x, y):
        return (cx - x, cy - y) if inv else (cx + x, cy + y)

    def TL(pts):
        return [T(x, y) for x, y in pts]

    body = pad.zeros()
    spine = [(0.0, -56.0 + 122.0 * i / 20) for i in range(21)]
    prof = [(0.0, 17.0), (0.22, 32.0), (0.55, 25.0), (0.85, 12.0), (1.0, 5.0)]
    body = np.maximum(body, pad.poly(TL(taper_poly(spine, lambda t: _lerp_prof(prof, t)))))
    if inv:
        neck = bez((0.0, -44.0), (0.0, -80.0), (-2.0, -104.0), n=16)
        H = (-3.0, -120.0)
        b = (-0.2, -1.0)
    else:
        neck = bez((0.0, -44.0), (16.0, -78.0), (-6.0, -100.0), n=16, p3=(-12.0, -114.0))
        H = (-14.0, -124.0)
        b = (-1.0, 0.3)
    bl = math.hypot(*b)
    b = (b[0] / bl, b[1] / bl)
    nd = (b[1], -b[0]) if not inv else (-b[1], b[0])
    if nd[1] < 0 and not inv:
        nd = (-nd[0], -nd[1])

    def hp(u, v):
        return (H[0] + b[0] * u + nd[0] * v, H[1] + b[1] * u + nd[1] * v)
    body = np.maximum(body, pad.poly(TL(taper_poly(neck, lambda t: 14.0 - 4.0 * t))))
    body = np.maximum(body, lens(pad, T(*hp(-24, 2)), T(*hp(16, 1)), 30.0, 1.0, power=0.45, skew=0.45))
    beak = bez(hp(8, -10), hp(26, -8), hp(38, 9), n=10) + bez(hp(38, 9), hp(24, 4), hp(9, 7), n=8)[1:]
    beak_m = pad.poly(TL(beak))
    body = np.maximum(body, beak_m)
    crests = []
    crestm = pad.zeros()
    for i in range(4):
        base = hp(-6 - 7 * i, -10 + 3 * i)
        back = (-b[0], -b[1])
        up = (-nd[0], -nd[1])
        L = 96 + 20 * i
        lift = 1.15 - 0.27 * i
        c1 = (base[0] + back[0] * L * 0.35 + up[0] * L * 0.45 * lift, base[1] + back[1] * L * 0.35 + up[1] * L * 0.45 * lift)
        c2 = (base[0] + back[0] * L * 0.75 + up[0] * L * 0.55 * lift, base[1] + back[1] * L * 0.75 + up[1] * L * 0.55 * lift)
        c3 = (base[0] + back[0] * L + up[0] * L * 0.25 * lift + math.sin(f * 1.2 + i) * 5, base[1] + back[1] * L + up[1] * L * 0.25 * lift)
        pts = _curl_pts(bez(base, c1, c2, n=18, p3=c3), (-1 if (i % 2 == 0) == (not inv) else 1) * 2.4 * (1 if b[0] < 0 else -1), 9.0)
        crests.append(pts)
        crestm = np.maximum(crestm, pad.poly(TL(taper_poly(pts, lambda t: 7.0 * (1 - t) ** 0.8 + 0.8))))
    tg, tc, tailm = _ph_tail(pad, TL, T, f, lum)
    X, Y = pad.grid()
    nz = fbm(X / 70.0, Y / 70.0 + f * 0.45, seed=8300, octaves=3, px=8, py=8)
    tongues, hot, n2 = flames_of(pad, body, f, 8320, 30.0, 0.8)
    t2, h2, _ = flames_of(pad, tailm, f, 8340, 8.0, 0.6)
    t3, h3, _ = flames_of(pad, crestm, f, 8360, 9.0, 0.6)
    tongues, hot = np.maximum(np.maximum(tongues, t2 * 0.8), t3), np.maximum(np.maximum(hot, h2 * 0.7), h3)
    allb = np.maximum(np.maximum(body, tailm), crestm)
    glow = np.maximum(glow, body * (0.66 + 0.3 * nz) * lum)
    glow = np.maximum(glow, tailm * (0.55 + 0.25 * nz) * lum)
    glow = np.maximum(glow, crestm * (0.7 + 0.2 * nz) * lum)
    glow = np.maximum(glow, tg)
    glow = np.maximum(glow, tongues * 0.85 * lum)
    glow = np.maximum(glow, rim_of(allb, 2.0) * 0.6 * lum)
    core = np.maximum(core, tc)
    core = np.maximum(core, hot * 0.7 * lum)
    for i in range(6):
        t = 0.15 + i * 0.14
        px, py = neck[int(t * (len(neck) - 1))]
        d = (8.0, -6.0) if not inv else (6.0, 8.0)
        tg2 = flame_tongue(pad, T(px + (9 if not inv else 6), py), (d[0] if not inv else -d[0], d[1] if not inv else -d[1]), 26 + 4 * i, 11, rs.uniform(-6, 6), 1.0)
        glow = np.maximum(glow, tg2 * 0.62 * lum)
        core = np.maximum(core, tg2 * 0.3 * lum)
    for pts in crests:
        core = np.maximum(core, pad.stroke(TL(pts), 2.6, 0.8, a0=0.9 * lum, a1=0.5 * lum, soft=0.6))
    core = np.maximum(core, pad.stroke(TL(neck), 7.0, 5.0, a0=0.55 * lum, a1=0.8 * lum, soft=1.2))
    for r in range(3):
        y = -26.0 + r * 18
        s = 20.0 - r * 4
        core = np.maximum(core, pad.stroke(TL([(-s, y - 7), (0.0, y + 3), (s, y - 7)]), 2.0, a0=0.5 * lum, soft=0.7))
    E = hp(4, -4)
    core = np.maximum(core, lens(pad, T(E[0] + b[0] * 8 + nd[0] * 1.5, E[1] + b[1] * 8 + nd[1] * 1.5), T(E[0] - b[0] * 8 - nd[0] * 4, E[1] - b[1] * 8 - nd[1] * 4), 5.0, lum))
    glow = np.maximum(glow, pad.disc_field(*T(*E), 12, soft=3.0) * 0.6 * lum)
    core = np.maximum(core, rim_of(beak_m, 1.4) * 0.8 * lum)
    hx, hy = T(0.0, -12.0)
    core = np.maximum(core, pad.radial(hx, hy, 34, power=1.3) * 0.9 * lum)
    glow = np.maximum(glow, pad.radial(hx, hy, 70, power=1.4) * 0.8 * lum)
    return glow, core, allb


def _ph_over(wg, wc, bg, bc, body):
    """몸을 날개 위에 — 몸 둘레 4칸 띠에서 날개를 어둡게 (윤곽이 날개 빛에 묻히지 않음)"""
    D = clamp01(gblur(body, 4.0 * SS) * 3.0)
    return np.maximum(wg * (1 - 0.72 * D), bg), np.maximum(wc * (1 - 0.85 * D), bc)


def phoenix_frame(f):
    S = PH_S
    pad = Pad(S, S)
    cx, cy = S / 2, 250.0
    rs = random.Random(13100 + f)
    glow = pad.zeros()
    core = pad.zeros()
    ang, span, lum = PH_POSE[f]
    if f == 7:
        for q in range(46):
            a = rs.uniform(0, TAU)
            d = rs.uniform(60, 225)
            x, y = cx + math.cos(a) * d, cy + math.sin(a) * d * 0.7 - rs.uniform(0, 40)
            b = a + rs.uniform(-0.8, 0.8)
            L = rs.uniform(18, 34)
            p0, p1 = (x - math.cos(b) * L / 2, y - math.sin(b) * L / 2), (x + math.cos(b) * L / 2, y + math.sin(b) * L / 2)
            glow = np.maximum(glow, lens(pad, p0, p1, rs.uniform(6, 11), 0.55))
            core = np.maximum(core, pad.stroke([p0, p1], 1.6, 0.5, a0=0.55, a1=0.25))
        for q in range(40):
            core = np.maximum(core, pad.disc_field(cx + rs.uniform(-225, 225), cy + rs.uniform(-200, 160), rs.uniform(1.2, 2.8), soft=0.6) * 0.7)
        glow = np.maximum(glow, pad.radial(cx, cy, 160, power=2.0) * 0.3)
        return finish(pad, glow * lum, core * lum, halo=((1.5, 0.4), (4.0, 0.3), (11.0, 0.2)), body_k=0.88, core_bloom=((1.3, 0.34), (3.5, 0.22)), win=4.0)
    if f == 5:
        for side in (-1, 1):
            draw_wing(pad, glow, core, cx, cy + 40, side, ang, span, f, rs, scale=PH_WING_K, flame=1.1)
        bg, bc, body = _phoenix(pad, cx, cy + 10, f, rs, inv=True)
        glow, core = _ph_over(glow, core, bg, bc, body)
        for k in range(20):
            x = cx + (k - 9.5) * 11 + rs.uniform(-4, 4)
            y1 = cy - 40 - rs.uniform(0, 60)
            L = rs.uniform(60, 150)
            glow = np.maximum(glow, pad.stroke([(x, y1 - L), (x, y1)], 1.0, 8.0, a0=0.0, a1=0.55))
            core = np.maximum(core, pad.stroke([(x, y1 - L * 0.6), (x, y1)], 0.4, 2.4, a0=0.0, a1=0.7))
    else:
        for side in (-1, 1):
            draw_wing(pad, glow, core, cx, cy - PH_SHOULDER, side, ang, span, f, rs, scale=PH_WING_K, flame=0.85)
        bg, bc, body = _phoenix(pad, cx, cy, f, rs)
        glow, core = _ph_over(glow, core, bg, bc, body)
    if f == 0:
        rr, th = polar(pad, cx, cy)
        sw = np.exp(-0.5 * (np.angle(np.exp(1j * (th - np.log(np.maximum(rr, 10) / 10) / 0.32))) / 0.45) ** 2) * clamp01(1 - rr / 225)
        glow = np.maximum(glow * 0.55, sw * 0.8)
        core = np.maximum(core * 0.45, sw * 0.35)
    if f == 6:
        glow = np.maximum(glow, pad.radial(cx, cy, 190, power=1.6) * 0.9)
        core = np.maximum(core, pad.radial(cx, cy, 78, power=1.1))
        rr, th = polar(pad, cx, cy)
        ring = np.exp(-0.5 * ((rr - 186) / 7.0) ** 2)
        glow = np.maximum(glow, ring * 0.7)
        core = np.maximum(core, np.exp(-0.5 * ((rr - 186) / 2.2) ** 2) * 0.8)
        for q in range(28):
            a = q * TAU / 28 + rs.uniform(-0.06, 0.06)
            r0, r1 = rs.uniform(60, 100), rs.uniform(165, 232)
            core = np.maximum(core, streak(pad, (cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1), rs.uniform(2.5, 4.5), 0.75))
    glow, core = glow * lum, core * lum
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.36), (11.0, 0.26)), body_k=0.88, core_bloom=((1.3, 0.35), (3.5, 0.24)), win=4.0)


sprite("rfx_jphoenix", "rotate_xyz", PH_S, PH_S, PH_N, 4, "phoenix", zoom=1,
       desc="푸른 불사조 (피닉스 가드 피닉스 디센트 — 카메라를 봄, 가슴 = (256, 250) · 날개 끝 사이 약 반 크기 × 1.7 · 볏 끝 ~ 꼬리 끝 약 반 크기 × 1.76 — 칸 테에서 "
       "20점 넘게 안쪽): 0 둘레에서 감겨 드는 불길 속에 생겨남 → 1 날개를 활짝 · 머리를 옆으로 듦 (갈고리 부리 · 성난 눈 · 뒤로 흐르는 불꽃 볏 넷 · 목의 갈기 · 가슴의 빛 · "
       "꼬리 = 깃 부채 — 아래로 ±32° 로 겹친 깃 아홉, 깃대 · 비스듬한 깃가지 결 · 끝 가까이 눈 무늬, 부채가 장마다 일렁임) → 2 ~ 4 날갯짓 (이어 돌림 — 아래 · 가운데 · 위) → "
       "5 내리꽂음 (머리가 아래 · 날개를 위로 모아 V · 깃 부채 · 불꽃 줄이 위로 끌림) → 6 땅에 닿아 크게 터짐 (날개를 넓게 · 가슴의 큰 불 · 고리 · 불줄기) → 7 깃털 · 불티로 "
       "흩어짐. 빛 칸 = 몸 · 깃 · 불의 혀, 심지 칸 = 깃대 · 앞 테 · 볏 · 깃 결 · 눈 무늬 · 눈 · 부리 테 · 불의 속 (처음 그림 — 끝이 말리는 굵은 꼬리 깃 다섯은 문어 다리처럼 "
       "보여 2026-11-06 영상 검토에서 깃 부채로 · 날개가 칸 밖으로 잘려 0.8 배로)")(phoenix_frame)


# ------------------------------------------------------------ rfx_jfury (분노의 부채꼴 — 땅에 누움, 꼭짓점 = 칸 왼쪽 가운데)

FU_N = 8
FU_S = 384
FU_APEX = (16.0, 192.0)
FU_R = 296.0
FU_HALF = math.radians(40)


def fury_frame(f):
    S = FU_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    rs = random.Random(12500 + f)
    ax, ay = FU_APEX
    rr, th = polar(pad, ax, ay)
    glow = pad.zeros()
    core = pad.zeros()
    angk = clamp01((FU_HALF - np.abs(th)) * 14.0 + 0.5)
    if f == 0:
        glow = np.maximum(glow, pad.radial(ax + 30, ay, 70, power=1.3) * angk * 0.95)
        core = np.maximum(core, pad.radial(ax + 20, ay, 36, power=1.6) * 0.95)
        for q in range(9):
            a = (q - 4) / 4.0 * FU_HALF * 0.9
            core = np.maximum(core, streak(pad, (ax + 10, ay), (ax + math.cos(a) * 80, ay + math.sin(a) * 80), 3.0, 0.9))
    else:
        reach = [0, 0.55, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f] * FU_R
        lum = [0, 1.0, 1.0, 0.85, 0.65, 0.45, 0.3, 0.15][f]
        inside = clamp01((reach - rr) * 0.5 + 0.5) * angk
        nz = fbm(X / 30.0, Y / 30.0, seed=12510, octaves=3, px=12, py=12)
        if f <= 3:
            # 몸: 가운데 줄이 밝고 옆으로 옅게 · 앞 테가 밝음
            centre = gauss(th, 0.14)
            body = inside * (0.35 + 0.45 * centre + 0.25 * nz) * clamp01(rr / 40.0)
            glow = np.maximum(glow, body * lum)
            lane = inside * gauss(th, 0.06) * clamp01(rr / 30.0)
            core = np.maximum(core, lane * (0.95 if f <= 2 else 0.6))
            front = gauss(rr - reach, 6.0) * angk
            core = np.maximum(core, front * (0.9 if f <= 2 else 0.5))
            glow = np.maximum(glow, gauss(rr - reach, 16.0) * angk * 0.7 * lum)
            # 불씨 줄 (꼭짓점에서 퍼짐)
            for q in range(16):
                a = rs.uniform(-FU_HALF * 0.95, FU_HALF * 0.95)
                r1 = reach * rs.uniform(0.6, 1.0)
                r0 = r1 * rs.uniform(0.45, 0.75)
                core = np.maximum(core, streak(pad, (ax + math.cos(a) * r0, ay + math.sin(a) * r0), (ax + math.cos(a) * r1, ay + math.sin(a) * r1),
                                               rs.uniform(2.0, 3.6), 0.85 * lum))
        if f >= 3:
            # 달아오른 금 (3 가장 밝음 → 흐려짐)
            ck = [0, 0, 0, 1.0, 0.75, 0.45, 0.2, 0.08][f]
            for q in range(9):
                a = (q - 4) / 4.0 * FU_HALF * 0.85 + rs.uniform(-0.06, 0.06)
                p0 = (ax + math.cos(a) * 40, ay + math.sin(a) * 40)
                p1 = (ax + math.cos(a) * FU_R * rs.uniform(0.7, 0.98), ay + math.sin(a) * FU_R * rs.uniform(0.7, 0.98))
                pts = _jag(rs, p0, p1, 8, 10)
                core = np.maximum(core, pad.stroke(pts, 2.4, 1.0, a0=ck, a1=ck * 0.5, soft=0.7))
                glow = np.maximum(glow, pad.stroke(pts, 6.0, 2.0, a0=ck * 0.6, a1=ck * 0.2, soft=1.0))
        if f >= 4:
            # 흩어지는 불씨 · 재 연기
            n = [0, 0, 0, 0, 40, 28, 16, 8][f]
            for q in range(n):
                a = rs.uniform(-FU_HALF, FU_HALF)
                r = rs.uniform(30, FU_R)
                x, y = ax + math.cos(a) * r, ay + math.sin(a) * r
                core = np.maximum(core, pad.disc_field(x, y, rs.uniform(1.4, 3.0), soft=0.6) * lum * 1.2)
            if f >= 5:
                smoke = clamp01(nz * 2.0 - 0.7) * inside
                glow = np.maximum(glow, smoke * [0, 0, 0, 0, 0, 0.35, 0.3, 0.15][f])
    return finish(pad, glow, core, halo=((1.5, 0.4), (4.0, 0.3), (9.0, 0.18)), body_k=0.9, core_bloom=((1.2, 0.35), (3.0, 0.22)), win=3.0)


sprite("rfx_jfury", "direction_z", FU_S, FU_S, FU_N, 4, "phoenix", zoom=1,
       desc="분노의 부채꼴 (피닉스 가드 피닉스 배시 — 푸른 불꽃 · 다크 나이트 크런치 찢기 — 검붉게 / 처음 = 2026-11-06 어벤저 — 땅에 누움, 꼭짓점 = 칸 왼쪽 가운데 (16, 192) · 길이 = 반 크기 × 1.54 · 반각 40°): 0 꼭짓점의 번쩍 · 짧은 불씨 줄 → "
       "1 부채가 반쯤 퍼짐 → 2 다 퍼짐 (가운데 흰 불길 줄 · 밝은 앞 테 · 불씨 줄) → 3 땅의 금이 달아오름 → 4 ~ 5 불씨가 흩어지며 사그라짐 → 6 ~ 7 재 연기. 빛 칸 = 부채 몸 · "
       "무리, 심지 칸 = 가운데 줄 · 앞 테 · 불씨 · 금")(fury_frame)


# ================================================================ 다크 나이트 — 방패의 아가리 · 검은 혀 · 땅의 거대한 아가리

# ------------------------------------------------------------ rfx_jdmaw (저주받은 방패의 아가리 — 카메라를 봄, 방패 가운데 = 칸 가운데)

DM_N = 8
DM_S = 256
# 방패 (위 가운데가 솟은 연 꼴 — 처음 그림은 위가 네모라 크게 그리면 상자처럼 보였음) · 뿔 · 이빨을 원래 자리에 그리고 칸 가운데 (128, 132) 둘레로 DM_K 배 줄여 놓음
# (가장 크게 벌린 2 · 6 에서도 뿔 끝 · 아래 턱이 칸 안 — 처음 그림은 칸 밖으로 잘렸음)
DM_K = 0.8
DM_C = (128.0, 132.0)
DM_SHIELD = [(60, 46), (94, 34), (128, 20), (162, 34), (196, 46), (214, 68), (214, 118), (198, 166), (168, 204), (128, 236), (88, 204), (58, 166), (42, 118),
             (42, 68)]
DM_MOUTH = 132.0
# (벌림 · 밝기)
DM_POSE = [(0, 1.0), (26, 1.0), (62, 1.0), (0, 1.0), (16, 1.0), (0, 0.95), (60, 1.0), (0, 0.55)]


def _dm(x, y):
    """그린 자리 → 칸 (DM_C 둘레로 DM_K 배)"""
    return (DM_C[0] + (x - DM_C[0]) * DM_K, DM_C[1] + (y - DM_C[1]) * DM_K)


def _dml(pts):
    return [_dm(x, y) for x, y in pts]


def _clip_poly_y(pts, y, keep_above):
    """다각형을 가로줄 y 로 자름 (keep_above = 위쪽 (y 작은 쪽) 을 남김)"""
    out = []
    n = len(pts)
    for i in range(n):
        p, q = pts[i], pts[(i + 1) % n]
        pin = p[1] <= y if keep_above else p[1] >= y
        qin = q[1] <= y if keep_above else q[1] >= y
        if pin:
            out.append(p)
        if pin != qin:
            t = (y - p[1]) / (q[1] - p[1])
            out.append((p[0] + (q[0] - p[0]) * t, y))
    return out


def _fangs(xs, y, sign, big, small, bigs):
    """송곳니 세모 (y 줄에서 sign 쪽으로 — +1 아래 · −1 위)"""
    out = []
    for x in xs:
        L = big if x in bigs else small
        w = 9.0 if x in bigs else 6.0
        out.append([(x - w, y - sign * 2), (x + w * 0.15, y + sign * L), (x + w, y - sign * 2)])
    return out


def dmaw_frame(f):
    S = DM_S
    K = DM_K
    pad = Pad(S, S)
    X, Y = pad.grid()
    rs = random.Random(13200 + f)
    glow = pad.zeros()
    core = pad.zeros()
    gap, lum = DM_POSE[f]
    cx = S / 2
    ym = DM_MOUTH
    mx, my = _dm(cx, ym)
    if f == 7:
        nz = fbm(X / 30.0, Y / 30.0, seed=13210, octaves=3, px=8, py=8)
        smoke = clamp01(nz * 2.2 - 0.8) * pad.radial(mx, my, 120 * K, power=1.0)
        glow = np.maximum(glow, smoke * 0.85)
        for q in range(26):
            a = rs.uniform(0, TAU)
            d = rs.uniform(30, 110) * K
            core = np.maximum(core, pad.disc_field(mx + math.cos(a) * d, my + math.sin(a) * d - rs.uniform(0, 20) * K, rs.uniform(1.4, 3.0), soft=0.6) * 0.8)
        return finish(pad, glow * lum, core * lum, halo=((1.2, 0.35), (3.0, 0.25), (7.0, 0.15)), body_k=0.95, core_bloom=((1.0, 0.35), (2.5, 0.25)), win=3.0)
    up = gap * 0.45
    dn = gap * 0.55
    upper = [(x, y - up) for x, y in _clip_poly_y(DM_SHIELD, ym, True)]
    lower = [(x, y + dn) for x, y in _clip_poly_y(DM_SHIELD, ym, False)]
    um = pad.poly(_dml(upper))
    lm = pad.poly(_dml(lower))
    plate = np.maximum(um, lm)
    # 목구멍 (벌어진 틈 — 붉은 빛) · 송곳니 (검게 — 빛을 가림)
    teeth = pad.zeros()
    if gap > 0:
        ty, by = ym - up, ym + dn
        throat = pad.poly(_dml([(50, ty), (206, ty), (196, by), (60, by)]))
        tcx, tcy = _dm(cx, (ty + by) / 2)
        heat = pad.radial(tcx, tcy, 90 * K, power=0.8, sy=max(0.3, gap / 90.0))
        upf = _fangs([62, 82, 100, 118, 138, 156, 174, 194], ty, 1, min(30, 8 + gap * 0.45), min(16, 5 + gap * 0.2), (82, 174))
        lof = _fangs([70, 90, 110, 146, 166, 186], by, -1, min(24, 6 + gap * 0.35), min(13, 4 + gap * 0.16), (90, 166))
        for tri in upf + lof:
            teeth = np.maximum(teeth, pad.poly(_dml(tri)))
        core = np.maximum(core, throat * (0.35 + 0.65 * heat) * (1 - teeth))
        glow = np.maximum(glow, throat * 0.7)
        core = np.maximum(core, rim_of(teeth, 1.2) * 0.55)
        if f in (1, 2):
            # 침 줄 (위아래 이를 잇는 가는 줄)
            for x in (96, 126, 160):
                core = np.maximum(core, pad.stroke(_dml([(x, ty + 8), (x + rs.uniform(-4, 4), (ty + by) / 2), (x + rs.uniform(-3, 3), by - 6)]), 1.4 * K, 0.8 * K, a0=0.45,
                                                   a1=0.3, soft=0.5))
    else:
        # 다문 이: 엇갈린 이빨 줄 (지그재그 붉은 틈)
        zz = _dml([(46 + i * 8, ym + (4 if i % 2 else -4)) for i in range(21)])
        core = np.maximum(core, pad.stroke(zz, 2.6 * K, a0=0.9, soft=0.6))
        glow = np.maximum(glow, pad.stroke(zz, 9.0 * K, a0=0.6, soft=2.0))
    glow = np.maximum(glow, plate * 0.9)
    glow = np.maximum(glow, teeth * 0.95)
    # 판의 결: 가운데 등줄 · 테 · 위 판의 솟은 마루 줄 · 금 사이로 새는 붉은 빛
    core = np.maximum(core, rim_of(plate, 2.0) * 0.35)
    core = np.maximum(core, pad.stroke(_dml([(cx, 26 - up), (cx, ym - up - 6)]), 2.0 * K, a0=0.3, soft=0.6))
    core = np.maximum(core, pad.stroke(_dml([(cx, ym + dn + 6), (cx, 230 + dn)]), 2.0 * K, a0=0.3, soft=0.6))
    for s in (-1, 1):
        core = np.maximum(core, pad.stroke(_dml([(cx + s * 8, 34 - up), (cx + s * 40, 46 - up), (cx + s * 74, 54 - up)]), 1.6 * K, 0.8 * K, a0=0.4, a1=0.15, soft=0.5))
    for (p0, p1, sy) in (((70, 66), (92, 106), -up), ((186, 64), (166, 102), -up), ((76, 168), (100, 196), dn), ((182, 166), (158, 194), dn)):
        pts = _jag(rs, (p0[0], p0[1] + sy), (p1[0], p1[1] + sy), 5, 4)
        core = np.maximum(core, pad.stroke(_dml(pts), 1.6 * K, 0.8 * K, a0=0.5, a1=0.2, soft=0.5))
    # 눈: 위 판의 비스듬한 눈 둘 (안쪽 아래로 — 성남) · 눈썹 뿔 (판 위로 휘어 솟음)
    eye_a = {0: 1.0, 1: 1.0, 2: 1.0, 3: 1.0, 4: 1.0, 5: 0.7, 6: 1.0}[f]
    narrow = 3.2 if f == 5 else 6.5
    for s in (-1, 1):
        ex, ey = cx + s * 36, 86 - up
        core = np.maximum(core, lens(pad, _dm(ex - s * 22, ey - 7), _dm(ex + s * 16, ey + 7), narrow * 2 * K, eye_a))
        gx, gy = _dm(ex, ey)
        glow = np.maximum(glow, pad.radial(gx, gy, 30 * K, power=1.5) * 0.6)
        core = np.maximum(core, pad.radial(gx, gy, 18 * K, power=2.0) * 0.5 * eye_a)
        horn = _dml(bez((cx + s * 62, 46 - up), (cx + s * 96, 36 - up), (cx + s * 104, 8 - up), n=12))
        glow = np.maximum(glow, pad.stroke(horn, 20.0 * K, 2.0 * K, soft=0.8))
        core = np.maximum(core, pad.stroke(horn, 2.2 * K, 0.6 * K, a0=0.45, a1=0.25, soft=0.5))
    if f == 3:
        # 다묾: 번쩍 · 옆으로 튀는 붉은 조각
        core = np.maximum(core, star4(pad, mx, my, 96 * K, 0.9, rot=0.0, thin=0.08, diag=0.4))
        for q in range(12):
            a = rs.choice([0.0, math.pi]) + rs.uniform(-0.7, 0.7)
            d = rs.uniform(80, 124)
            core = np.maximum(core, streak(pad, _dm(cx + math.cos(a) * 60, ym + math.sin(a) * 30), _dm(cx + math.cos(a) * d, ym + math.sin(a) * d * 0.6),
                                           rs.uniform(2, 3.6) * K, 0.85))
    if f == 4:
        for q in range(10):
            a = rs.choice([0.15, math.pi - 0.15, -0.3, math.pi + 0.3]) + rs.uniform(-0.25, 0.25)
            d = rs.uniform(70, 120)
            core = np.maximum(core, streak(pad, _dm(cx + math.cos(a) * 40, ym + math.sin(a) * 20), _dm(cx + math.cos(a) * d, ym + math.sin(a) * d * 0.7),
                                           rs.uniform(1.8, 3.0) * K, 0.8))
    if f == 5:
        glow = np.maximum(glow, pad.radial(mx, my, 70 * K, power=1.6) * 0.5)
        core = np.maximum(core, pad.radial(mx, my, 50 * K, power=2.4) * 0.45)
    if f == 6:
        # 포효: 앞으로 퍼지는 고리 (울림)
        for k, r in enumerate((104, 118)):
            core = np.maximum(core, pad.stroke(_dml(arc_pts(cx, ym, r, 0, TAU, n=64, ry=r * 0.86)), (2.4 - k * 0.8) * K, a0=0.7 - k * 0.3, soft=0.8))
    return finish(pad, glow * lum, core * lum, halo=((1.2, 0.3), (3.0, 0.22), (7.0, 0.14)), body_k=0.97, core_bloom=((1.0, 0.38), (2.6, 0.26)), win=3.0)


sprite("rfx_jdmaw", "rotate_xyz", DM_S, DM_S, DM_N, 4, "darkknight", zoom=2,
       desc="저주받은 방패의 아가리 (다크 나이트 — 카메라를 봄, 방패 가운데 = 칸 가운데 · 방패 너비 약 반 크기 × 1.1 — 위 가운데가 솟은 연 꼴 · 휘어 솟는 눈썹 뿔, 가장 "
       "크게 벌려도 칸 안): 0 다문 방패 (성난 눈 둘 · 엇갈린 이 틈의 붉은 빛) → 1 벌어지기 시작 (침 줄) → 2 크게 벌어짐 (붉은 목구멍 · 검은 송곳니) → 3 다묾 (번쩍 · 옆으로 "
       "튀는 붉은 조각) → 4 물어 찢음 (조금 벌어진 채 붉은 줄기) → 5 삼킴 (다문 채 속이 고동침 · 가는 눈) → 6 포효 (크게 벌어짐 · 울림 고리) → 7 검은 연기 · 불티로 흩어짐. "
       "빛 칸 = 검은 방패 판 · 뿔 · 이빨 · 연기 (짙은 색), 심지 칸 = 눈 · 목구멍 · 이 틈 · 금 · 마루 줄 · 테 (붉은 빛)")(dmaw_frame)


# ------------------------------------------------------------ rfx_jtendril (검은 혀 — 가는 쪽으로 누움, 뿌리 = 왼쪽 · 끝 갈고리 = 오른쪽)

TD_N = 4
TD_W, TD_H = 512, 96


def tendril_frame(f):
    W, H = TD_W, TD_H
    pad = Pad(W, H)
    glow = pad.zeros()
    core = pad.zeros()
    cy = H / 2
    ph = f * math.pi / 2
    pts = []
    for i in range(64):
        t = i / 63
        x = 10 + t * (W - 34)
        amp = 10.0 * math.sin(t * math.pi) ** 0.8
        y = cy + amp * math.sin(t * 9.0 - ph)
        pts.append((x, y))

    def wfn(s):
        return 34.0 * (1 - np.clip(s, 0, 1)) ** 0.55 + 6.0
    body = pad.stroke(pts, 34.0, 6.0, soft=0.8, wfn=wfn)
    # 끝 갈고리 (아래로 휜 발톱)
    tx, ty = pts[-1]
    hook = bez((tx - 8, ty - 4), (tx + 16, ty - 8), (tx + 20, ty + 14), n=10)
    body = np.maximum(body, pad.stroke(hook, 9.0, 1.4, soft=0.5))
    # 가시 (위아래 번갈아 — 뒤로 휨)
    thorns = pad.zeros()
    for k in range(10):
        t = 0.1 + k * 0.085
        i = int(t * 63)
        x, y = pts[i]
        s = 1 if k % 2 else -1
        w = float(wfn(t))
        thorns = np.maximum(thorns, pad.poly([(x - 6, y + s * w * 0.3), (x + 5, y + s * (w * 0.5 + 11 * (1 - t) + 4)), (x + 8, y + s * w * 0.3)]))
    glow = np.maximum(glow, np.maximum(body, thorns) * 0.95)
    # 핏줄 (가운데 붉은 줄 — 뿌리에서 끝으로 흐르는 맥동) · 마디 (가로 주름 — 혀의 결) · 가시 끝 · 갈고리 테
    pulse = lambda s: 0.35 + 0.45 * np.maximum(0.0, np.sin(np.asarray(s) * 14.0 - ph * 2.0)) ** 2  # noqa: E731
    core = np.maximum(core, pad.stroke(pts, 4.0, 1.2, soft=0.6, afn=pulse))
    for k in range(14):
        i = 3 + k * 4
        if i >= 60:
            break
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / L, dx / L
        w = float(wfn(i / 63)) * 0.42
        core = np.maximum(core, pad.stroke([(x0 - nx * w, y0 - ny * w), (x0 + dx / L * 3, y0 + dy / L * 3), (x0 + nx * w, y0 + ny * w)], 1.4, 1.0, a0=0.22, a1=0.22,
                                           soft=0.6))
    core = np.maximum(core, rim_of(thorns, 1.0) * 0.6)
    core = np.maximum(core, pad.stroke(hook, 2.0, 0.6, a0=0.8, a1=0.5, soft=0.4))
    core = np.maximum(core, rim_of(body, 1.2) * 0.3)
    return finish(pad, glow, core, halo=((1.0, 0.3), (2.5, 0.2)), body_k=0.97, core_bloom=((1.0, 0.3), (2.2, 0.2)), win=2.0)


sprite("rfx_jtendril", "lookat_direction", TD_W, TD_H, TD_N, 2, "darkknight", zoom=0,
       desc="검은 혀 (다크 나이트 피스트 · 어비설 메이 — 가는 쪽으로 누움, 뿌리 = 왼쪽 x 10 · 갈고리 끝 = 오른쪽 x 496 (반 크기 × 0.96)): 뿌리에서 끝으로 가늘어지는 굵은 물결 "
       "몸 (뿌리 굵기 = 칸 높이의 0.4 — 처음 그림 (512 × 64 · 18점) 은 영상에서 가는 줄로만 보여 2026-11-06 굵게) · 위아래 가시 · 가로 주름 · 아래로 휜 갈고리 끝 · 가운데 붉은 "
       "핏줄이 끝으로 흐름 (이어 돌림 — 물결이 끝으로 감). 빛 칸 = 검은 몸 · 가시, 심지 칸 = 핏줄 · 주름 · 가시 테 · 갈고리 테")(tendril_frame)


# ------------------------------------------------------------ rfx_jabyss (땅에서 솟는 거대한 아가리 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

AB_N = 8
AB_S = 384
AB_BASE = AB_S - 10
# (높이 · 벌림 · 밝기 · 흔들림)
AB_POSE = [(0.0, 0.0, 1.0, 0.0), (0.36, 0.62, 1.0, 0.0), (1.0, 1.0, 1.0, 0.0), (0.97, 0.0, 1.0, 0.0), (0.97, 0.1, 1.0, 1.0), (0.96, 0.03, 1.0, -1.0), (1.0, 1.3, 1.0, 0.0),
           (0.45, 0.62, 0.6, 0.0)]


def _abyss_jaw(side, h, o, wob):
    """턱 하나 (side −1 = 왼쪽): 땅에서 솟아 끝이 안쪽 위로 휘는 갈고리 엄니 (안쪽 테가 바깥으로 불룩 — 벌린 입이 가운데가 넓은 타원) — (다각형, 안쪽 테 (끝 → 밑), 바깥 테 (밑 → 끝))"""
    cx = AB_S / 2
    base = AB_BASE
    H = 304.0 * h
    oo = max(0.0, min(1.4, o))
    tip = (cx + side * (4 + 60 * oo) + wob * side * 5, base - H * (1.02 - 0.02 * oo))
    outer = bez((cx + side * 176, base), (cx + side * 204, base - H * 0.46), (cx + side * (60 + 82 * oo), base - H * 0.94), n=28, p3=tip)
    inner = bez(tip, (cx + side * (38 + 84 * oo), base - H * 0.74), (cx + side * (48 + 64 * oo), base - H * 0.28), n=28, p3=(cx + side * 64, base))
    return outer + inner[1:], inner, outer


def abyss_frame(f):
    S = AB_S
    pad = Pad(S, S)
    X, Y = pad.grid()
    cx = S / 2
    base = AB_BASE
    rs = random.Random(13400 + f)
    glow = pad.zeros()
    core = pad.zeros()
    h, o, lum, wob = AB_POSE[f]
    # 땅: 갈라진 금 (붉은 빛) · 검은 구덩이
    pit = pad.ellipse(cx, base - 2, 180 if f else 120, 16)
    glow = np.maximum(glow, pit * 0.9)
    for q in range(10):
        a = math.pi + (q + 0.5) * math.pi / 10
        p0 = (cx + math.cos(a) * 30, base - 2)
        p1 = (cx + math.cos(a) * rs.uniform(130, 188), base - 2 + math.sin(a) * rs.uniform(4, 14))
        pts = _jag(rs, p0, p1, 7, 4)
        core = np.maximum(core, pad.stroke(pts, 2.6, 1.0, a0=0.95, a1=0.4, soft=0.6))
    if f == 0:
        core = np.maximum(core, pad.ellipse(cx, base - 3, 92, 7) * 0.85)
        for q in range(18):
            x = cx + rs.uniform(-130, 130)
            y = base - rs.uniform(10, 130)
            core = np.maximum(core, streak(pad, (x, y + 14), (x + rs.uniform(-3, 3), y), rs.uniform(1.8, 3.0), 0.85))
        return finish(pad, glow * lum, core * lum, halo=((1.2, 0.3), (3.0, 0.22), (7.0, 0.14)), body_k=0.95, core_bloom=((1.0, 0.36), (2.6, 0.24)), win=3.0)
    jaws = pad.zeros()
    teeth = pad.zeros()
    gums = pad.zeros()
    inners = []
    for side in (-1, 1):
        poly, inner, outer = _abyss_jaw(side, h, o, wob)
        jm = pad.poly(poly)
        jaws = np.maximum(jaws, jm)
        inners.append(inner)
        gums = np.maximum(gums, pad.stroke(inner, 12.0, 8.0, soft=1.5))
        # 송곳니: 안쪽 테를 따라 가운데 · 조금 아래로 (크고 작게 번갈아)
        n = len(inner)
        for k in range(8):
            i = int(n * (0.1 + k * 0.105))
            if i >= n - 1:
                break
            p, q = inner[i], inner[i + 1]
            dx, dy = q[0] - p[0], q[1] - p[1]
            L = math.hypot(dx, dy) or 1.0
            ux, uy = dx / L, dy / L
            nx, ny = -uy, ux
            if (nx * side) > 0:
                nx, ny = -nx, -ny
            big = k % 2 == 1
            ln = (40 if big else 22) * (0.55 + 0.45 * h)
            w = 12 if big else 7
            tip = (p[0] + nx * ln, p[1] + ny * ln + ln * 0.35)
            teeth = np.maximum(teeth, pad.poly([(p[0] - ux * w, p[1] - uy * w), tip, (p[0] + ux * w, p[1] + uy * w)]))
        # 등뼈 마루 (바깥 테를 따라 안쪽으로 겹친 줄 둘) · 바깥 테의 작은 가시 · 붉게 새는 금
        for k, off in enumerate((20, 42)):
            ridge = [(x - side * off, y + off * 0.3) for x, y in outer[2:-4]]
            core = np.maximum(core, pad.stroke(ridge, 2.2, 1.0, a0=0.3, a1=0.12, soft=0.8) * jm)
        for k in range(6):
            i = 4 + k * 4
            if i >= len(outer) - 3:
                break
            p, q = outer[i], outer[i + 1]
            dx, dy = q[0] - p[0], q[1] - p[1]
            L = math.hypot(dx, dy) or 1.0
            nx, ny = dy / L * side, -dx / L * side
            if nx * side < 0:
                nx, ny = -nx, -ny
            sp = (p[0] + nx * 16 * h, p[1] + ny * 16 * h - 6)
            spike = pad.poly([(p[0] - dx / L * 7, p[1] - dy / L * 7), sp, (p[0] + dx / L * 7, p[1] + dy / L * 7)])
            jaws = np.maximum(jaws, spike)
        for q in range(2):
            y0 = base - rs.uniform(0.25, 0.7) * 300 * h
            x0 = cx + side * rs.uniform(100, 150)
            pts = _jag(rs, (x0, y0), (x0 - side * rs.uniform(20, 40), y0 + rs.uniform(20, 50)), 5, 4)
            core = np.maximum(core, pad.stroke(pts, 1.8, 0.8, a0=0.7, a1=0.3, soft=0.5) * jm)
    # 목구멍: 두 안쪽 테 사이 (땅에서 솟는 붉은 빛 — 아래가 밝음)
    if o > 0.05:
        mouth = pad.poly(inners[0] + inners[1][::-1])
        vy = clamp01((Y - (base - 300 * h)) / max(1.0, 300 * h)) ** 1.2
        throat = mouth * (0.25 + 0.75 * vy) * (1 - teeth) * clamp01(1 - jaws)
        core = np.maximum(core, throat * (0.6 + 0.4 * clamp01(o)))
        glow = np.maximum(glow, mouth * 0.5)
        # 혀: 목구멍에서 솟는 검은 혀 셋 (붉은 핏줄)
        for k in (-1, 0, 1):
            ph = f * 1.4 + k * 2.0
            pts = [(cx + k * 22 + 18 * math.sin(ph + s * 4.0) * s, base - 8 - s * 200 * h * (0.75 - 0.15 * abs(k))) for s in np.linspace(0, 1, 20)]
            tm = pad.stroke(pts, 16.0, 3.0, soft=0.8)
            glow = np.maximum(glow, tm * 0.95)
            core = core * clamp01(1 - tm * 0.9)
            core = np.maximum(core, pad.stroke(pts, 2.0, 0.6, a0=0.7, a1=0.3, soft=0.5))
        if f == 2:
            # 침 줄 (양쪽 이 사이로 처진 가는 줄)
            for k in range(3):
                y = base - (0.35 + k * 0.17) * 300 * h
                pts = [(cx - 70 + 140 * s, y + 18 * math.sin(math.pi * s)) for s in np.linspace(0, 1, 16)]
                core = np.maximum(core, pad.stroke(pts, 1.4, 1.0, a0=0.4, a1=0.4, soft=0.5) * mouth)
    glow = np.maximum(glow, jaws * 0.95)
    glow = np.maximum(glow, teeth * 0.97)
    core = np.maximum(core, gums * 0.5 * (1 - teeth))
    core = np.maximum(core, rim_of(teeth, 1.3) * 0.55)
    core = np.maximum(core, rim_of(jaws, 2.0) * 0.35)
    if f == 3:
        # 맞물림: 끝의 번쩍 · 위로 튀는 조각
        ty = base - 300 * h
        core = np.maximum(core, star4(pad, cx, ty + 24, 130, 0.95, rot=0.0, thin=0.07, diag=0.45))
        glow = np.maximum(glow, pad.radial(cx, ty + 24, 90, power=1.4) * 0.5)
        for q in range(14):
            a = -math.pi / 2 + rs.uniform(-1.3, 1.3)
            d = rs.uniform(60, 150)
            core = np.maximum(core, streak(pad, (cx + math.cos(a) * 30, ty + 24 + math.sin(a) * 20), (cx + math.cos(a) * d, ty + 24 + math.sin(a) * d), rs.uniform(2, 3.4), 0.8))
    if f in (4, 5):
        # 짓씹음: 맞물린 가운데 틈 (맞물린 이처럼 지그재그) 으로 새는 빛 — 턱 안에서만 (처음 그림은 틈이 턱 위로 칸 끝까지 뻗어 기둥처럼 보였음) · 튀는 조각
        zig = 7.0 * (2 / math.pi) * np.arcsin(np.sin(Y * TAU / 26.0 + f * 1.3))
        inside = clamp01(gblur(jaws, 2.0 * SS) * 1.6)
        seam = gauss(X - cx - zig, 3.5 + 3 * o * 10) * smooth(base, base - 60 * h, Y) * inside
        core = np.maximum(core, seam * 0.95)
        for q in range(9):
            x = cx + rs.uniform(-24, 24)
            y = base - rs.uniform(60, 260) * h
            core = np.maximum(core, streak(pad, (x, y), (x + rs.uniform(-60, 60), y - rs.uniform(10, 40)), rs.uniform(1.6, 2.8), 0.75))
    if f == 6:
        # 터뜨림: 가운데에서 위로 뻗는 붉은 빛줄기 · 조각
        glow = np.maximum(glow, pad.radial(cx, base - 150, 170, power=1.3, sy=1.4) * 0.55)
        core = np.maximum(core, pad.radial(cx, base - 130, 110, power=1.4, sy=1.6) * 0.8)
        for q in range(14):
            a = -math.pi / 2 + (q - 6.5) * 0.16
            core = np.maximum(core, streak(pad, (cx + math.cos(a) * 40, base - 60 + math.sin(a) * 40), (cx + math.cos(a) * 250, base - 60 + math.sin(a) * 300), 4.0, 0.8))
        for q in range(16):
            x, y = cx + rs.uniform(-170, 170), base - rs.uniform(40, 320)
            pts = chunk_pts(rs, x, y, rs.uniform(6, 13), n=rs.choice([3, 4]), rot=rs.uniform(0, TAU))
            m = pad.poly(pts)
            glow = np.maximum(glow, m * 0.9)
            core = np.maximum(core, rim_of(m, 1.4) * 0.6)
    if f == 7:
        nz = fbm(X / 40.0, Y / 50.0, seed=13410, octaves=3, px=8, py=8)
        smoke = clamp01(nz * 2.0 - 0.7) * smooth(base, base - 200, Y) * gauss(X - cx, 90.0)
        glow = np.maximum(glow, smoke * 0.7)
    return finish(pad, glow * lum, core * lum, halo=((1.2, 0.3), (3.0, 0.22), (7.0, 0.14)), body_k=0.96, core_bloom=((1.0, 0.36), (2.6, 0.24)), win=3.0)


sprite("rfx_jabyss", "lookat_y", AB_S, AB_S, AB_N, 4, "darkknight", zoom=2,
       desc="땅에서 솟는 거대한 아가리 (다크 나이트 어비설 메이 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅 (칸 아래에서 10점 위) · 턱 높이 약 반 크기 × 1.56 · 너비 약 반 크기 × 1.56): "
       "0 땅이 붉게 갈라짐 (불티가 솟음) → 1 엄니 끝이 솟음 → 2 두 턱이 크게 벌어진 채 솟음 (안쪽 송곳니 · 땅에서 솟는 붉은 목구멍) → 3 맞물림 (끝의 번쩍 · 튀는 조각) → "
       "4 ~ 5 짓씹음 (이어 돌림 — 맞물린 틈으로 새는 빛 · 흔들림) → 6 터뜨림 (턱이 바깥으로 젖혀짐 · 위로 뻗는 붉은 빛줄기 · 조각) → 7 가라앉음 (연기). 빛 칸 = 검은 턱 · "
       "이빨 · 구덩이 · 연기, 심지 칸 = 목구멍 · 금 · 틈 · 테 (붉은 빛)")(abyss_frame)


# ------------------------------------------------------------ rfx_jcinder (불씨 기둥 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅)

CI_N = 8
CI_W, CI_H = 192, 384


def cinder_frame(f):
    W, H = CI_W, CI_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx = W / 2
    base = H - 8
    rs = random.Random(12700 + f)
    glow = pad.zeros()
    core = pad.zeros()
    usable = H - 30
    reach = [0.18, 0.55, 0.92, 0.95, 0.9, 0.94, 0.5, 0.25][f]
    top = base - usable * reach
    ph = (f - 2) * math.pi / 2 if 2 <= f <= 5 else 0.0
    # 바닥 불씨 (땅의 빛)
    glow = np.maximum(glow, pad.ellipse(cx, base - 2, 70 if f < 6 else 50, 9) * (0.85 if f < 6 else 0.5))
    core = np.maximum(core, pad.ellipse(cx, base - 2, 40, 4) * (0.8 if f < 6 else 0.4))
    if f <= 5:
        # 아지랑이 기둥: 일렁이는 굵은 줄기 셋 (아래 넓고 위로 가늘게 · 흐려짐)
        for q in range(3):
            pts = []
            for i in range(26):
                t = i / 25
                y = base - t * (base - top)
                x = cx + (q - 1) * 26 * (1 - t * 0.6) + 12 * math.sin(t * 7 + ph + q * 2.1) * (0.4 + t)
                pts.append((x, y))
            glow = np.maximum(glow, pad.stroke(pts, 22, 6, a0=0.7, a1=0.0, soft=1.0))
            core = np.maximum(core, pad.stroke(pts, 3.0, 0.8, a0=0.55, a1=0.0, soft=0.8))
        # 밑동의 불꽃 혀
        for q in range(5):
            x = cx + (q - 2) * 20 + rs.uniform(-4, 4)
            L = rs.uniform(36, 70) * (0.5 if f == 0 else 1.0)
            tip = (x + 8 * math.sin(ph + q), base - L)
            glow = np.maximum(glow, lens(pad, (x, base - 2), tip, 18, 0.9, skew=0.3))
            core = np.maximum(core, lens(pad, (x, base - 2), (x + (tip[0] - x) * 0.6, base - L * 0.6), 7, 0.85, skew=0.3))
        # 솟는 불씨 (장마다 위로)
        n = [6, 14, 22, 22, 22, 22][f]
        for q in range(n):
            x = cx + rs.uniform(-60, 60) * (0.5 + 0.5 * (q % 3) / 2)
            y = base - ((q * 53 + max(0, f - 1) * 31) % int(max(20, base - top)))
            r = rs.uniform(1.6, 3.4)
            core = np.maximum(core, streak(pad, (x, y + r * 6), (x + rs.uniform(-2, 2), y), r * 1.3, 0.95))
    elif f == 6:
        # 무너짐: 불씨가 옆으로 흩어짐
        for q in range(24):
            a = rs.uniform(-math.pi, 0)
            d = rs.uniform(20, 90)
            x, y = cx + math.cos(a) * d, base - 20 + math.sin(a) * d * 1.4
            core = np.maximum(core, streak(pad, (cx + math.cos(a) * d * 0.5, base - 20 + math.sin(a) * d * 0.7), (x, y), rs.uniform(2.0, 3.2), 0.85))
    else:
        # 재 연기 (흐린 덩이)
        nz = fbm(X / 26.0, Y / 40.0, seed=12710, octaves=3, px=8, py=10)
        smoke = clamp01(nz * 2.0 - 0.8) * smooth(base, base - 200, Y) * smooth(base - 300, base - 160, Y) * gauss(X - cx, 50.0)
        glow = np.maximum(glow, smoke * 0.5)
    lum = [0.9, 1.0, 1.0, 1.0, 1.0, 1.0, 0.85, 0.5][f]
    return finish(pad, glow * lum, core * lum, halo=((1.2, 0.35), (3.0, 0.25), (6.0, 0.15)), body_k=0.85, core_bloom=((1.0, 0.32), (2.5, 0.2)), win=2.5)


sprite("rfx_jcinder", "lookat_y", CI_W, CI_H, CI_N, 4, "phoenix", zoom=2,
       desc="불씨 기둥 (피닉스 가드 블루 인페르노 불꽃 고리 · 피닉스 디센트 불기둥 — 푸른 불꽃 · 다크 나이트 언홀리 프렌지 · 어비설 메이 — 검붉게 / 처음 = 2026-11-06 어벤저 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅 · 높이 = 반 너비 × 4): 0 발밑에 불씨가 붙음 → 1 솟아오름 → "
       "2 ~ 5 이글거림 (이어 돌림 — 일렁이는 아지랑이 줄기 셋 · 밑동의 불꽃 혀 · 솟는 불씨) → 6 무너지며 불씨가 흩어짐 → 7 재 연기. 빛 칸 = 아지랑이 · 불꽃 몸, 심지 칸 = "
       "줄기 속 · 불꽃 속 · 불씨")(cinder_frame)



# ============================================================ 2026-11-07 재설계안 3판 (직업 스킬 도감 표시 고침) — 소드마스터
# 블레이드 에코의 걸린 칼자국 (rfx_jecho) · 엑스칼리버의 빛의 대검 (rfx_jexcal). 빛 칸 = 금빛 번짐, 심지 칸 = 흰 날 · 반짝임.

EC_N = 8
EC_W, EC_H = 384, 64


def echo_frame(f):
    """걸린 칼자국 (가는 쪽 = 오른쪽 · 칸 가운데 = 칼자국 가운데): 0 번쩍이며 그어짐 → 1 ~ 4 걸려 떨림 → 5 갈라져 번쩍 → 6 ~ 7 조각으로 흩어짐"""
    W, H = EC_W, EC_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cx, cy = W / 2, H / 2
    x0, x1 = 14.0, W - 14.0
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(16100 + f)
    if f <= 4:
        # 떨림: 칼자국이 아주 조금 물결침 (장마다 다른 위상)
        wob = 0.0 if f == 0 else 0.9
        ph = f * 1.7
        pts = [(x0 + (x1 - x0) * i / 40, cy + wob * math.sin(i * 0.5 + ph) * math.sin(math.pi * i / 40)) for i in range(41)]
        w = 3.4 if f == 0 else 2.4
        line = pad.stroke(pts, w, taper=1.0, soft=0.6)
        core = np.maximum(core, line)
        glow = np.maximum(glow, pad.stroke(pts, w * 3.2, taper=1.0, soft=3.0) * 0.85)
        glow = np.maximum(glow, pad.stroke(pts, w * 7.0, taper=1.0, soft=6.0) * 0.32)
        if f == 0:
            core = np.maximum(core, star4(pad, cx, cy, 22.0, 1.0, rot=0.0, thin=0.1, diag=0.35))
            glow = np.maximum(glow, pad.radial(cx, cy, 26.0, 1.6) * 0.5)
        else:
            # 날을 따라 옮겨 가는 반짝임 · 날 둘레에 떠도는 금빛 티끌
            t = [0.18, 0.42, 0.66, 0.88][f - 1]
            gx = x0 + (x1 - x0) * t
            core = np.maximum(core, star4(pad, gx, cy, 13.0, 0.95, rot=0.25 * f, thin=0.12, diag=0.4))
            for _ in range(7):
                px = rs.uniform(x0 + 20, x1 - 20)
                py = cy + rs.uniform(-14, 14)
                glow = np.maximum(glow, pad.disc_field(px, py, rs.uniform(1.2, 2.2), soft=1.0) * rs.uniform(0.4, 0.8))
    elif f == 5:
        # 갈라짐: 날이 벌어진 빛의 틈 · 가운데 섬광 · 위아래로 뻗는 빛살
        core = np.maximum(core, lens(pad, (x0, cy), (x1, cy), 9.0, 1.0, power=0.55))
        glow = np.maximum(glow, lens(pad, (x0 - 6, cy), (x1 + 6, cy), 26.0, 0.9, power=0.5))
        core = np.maximum(core, star4(pad, cx, cy, 24.0, 1.0, rot=0.0, thin=0.08, diag=0.3))
        for k in range(10):
            px = x0 + 30 + (x1 - x0 - 60) * k / 9
            L = rs.uniform(10, 26)
            s = 1 if k % 2 == 0 else -1
            core = np.maximum(core, streak(pad, (px, cy + s * 3), (px + rs.uniform(-6, 6), cy + s * L), 2.0, 0.85))
    else:
        # 조각: 짧은 칼날 조각이 날을 따라 흩어짐 (6 크게 · 7 작고 흐리게)
        a = 1.0 if f == 6 else 0.5
        n = 16 if f == 6 else 12
        spread = 10.0 if f == 6 else 20.0
        for k in range(n):
            px = x0 + 10 + (x1 - x0 - 20) * (k + rs.uniform(0, 1)) / n
            py = cy + rs.uniform(-spread, spread)
            L = rs.uniform(8, 22) * (1.0 if f == 6 else 0.6)
            ang = rs.uniform(-0.5, 0.5)
            p0 = (px - math.cos(ang) * L / 2, py - math.sin(ang) * L / 2)
            p1 = (px + math.cos(ang) * L / 2, py + math.sin(ang) * L / 2)
            core = np.maximum(core, pad.stroke([p0, p1], 1.6, taper=1.0, soft=0.5) * a)
            glow = np.maximum(glow, pad.stroke([p0, p1], 5.0, taper=1.0, soft=2.0) * a * 0.6)
        if f == 6:
            glow = np.maximum(glow, lens(pad, (x0, cy), (x1, cy), 14.0, 0.35, power=0.5))
    return finish(pad, glow, core, halo=((1.2, 0.5), (3.0, 0.38), (7.0, 0.22)), body_k=0.9, core_bloom=((1.0, 0.4), (2.5, 0.25)), win=3.0)


COLORS.setdefault("swordmaster", ((1.0, 0.8, 0.3), (0.94, 0.93, 0.88)))
sprite("rfx_jecho", "lookat_direction", EC_W, EC_H, EC_N, 4, "swordmaster", zoom=0,
       desc="걸린 칼자국 (소드마스터 블레이드 에코 — 가는 쪽으로 누움, 칸 가운데 = 칼자국 가운데 · 칼자국 길이 = 반 너비 × 1.9): 0 번쩍이며 그어짐 → 1 ~ 4 허공에 걸려 떨림 (이어 "
       "돌림 — 가는 흰 날 · 금빛 번짐 · 날을 따라 옮겨 가는 반짝임 · 떠도는 금빛 티끌) → 5 갈라져 번쩍 (벌어진 빛의 틈 · 위아래 빛살) → 6 ~ 7 칼날 조각으로 흩어짐. 빛 칸 = "
       "금빛 번짐, 심지 칸 = 흰 날 · 반짝임")(echo_frame)


# ------------------------------------------------------------ rfx_jexcal (빛의 대검 — 가는 쪽으로 누움, 손잡이 = 왼쪽 (칸 가운데에서 반 너비 × 0.94) · 칼끝 = 오른쪽 (× 0.97))

XC_N = 8
XC_W, XC_H = 512, 64


def _xc_blade(pad, X, Y, xh, xt, cy, hw):
    """칼날 (xh 코등이 · xt 칼끝 · 반 굵기 hw — 칼끝 쪽 18% 에서 뾰족하게) 의 다각형과 날 가운데에서의 거리 (홈 · 날 결)"""
    xs = xh + (xt - xh) * 0.82
    pts = [(xh, cy - hw), (xs, cy - hw * 0.92), (xt, cy), (xs, cy + hw * 0.92), (xh, cy + hw)]
    return pad.poly(pts), np.abs(Y - cy)


def excal_frame(f):
    W, H = XC_W, XC_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    cy = H / 2
    xh = W / 2 - W / 2 * 0.94 + 18.0   # 코등이 (손잡이 끝에서 18 점)
    x_grip = W / 2 - W / 2 * 0.94       # 손잡이 끝 (손)
    xt = W / 2 + W / 2 * 0.97           # 칼끝
    hw = 7.0
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(16200 + f)
    lum = [0.5, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 0.6][f]
    blade, dy = _xc_blade(pad, X, Y, xh, xt, cy, hw)
    along = clamp01((X - xh) / (xt - xh))
    if f == 0:
        # 모여 듦: 칼 자리로 날아드는 빛 알갱이 · 가운데 가는 줄
        line = pad.stroke([(xh, cy), (xt, cy)], 1.2, taper=0.6, soft=0.8)
        glow = np.maximum(glow, line * 0.5)
        for _ in range(40):
            px = rs.uniform(xh, xt)
            py = cy + rs.choice([-1, 1]) * rs.uniform(6, 26)
            L = rs.uniform(5, 14)
            core = np.maximum(core, streak(pad, (px, py + math.copysign(L, py - cy)), (px, py), 1.4, 0.8))
    else:
        if f == 1:
            # 자라남: 날 테가 밝고 속은 옅음
            glow = np.maximum(glow, blade * 0.35)
            core = np.maximum(core, pad.stroke([(xh, cy - hw), (xt, cy)], 1.4, soft=0.6))
            core = np.maximum(core, pad.stroke([(xh, cy + hw), (xt, cy)], 1.4, soft=0.6))
            core = np.maximum(core, pad.stroke([(xh, cy), (xt - 30, cy)], 1.0, soft=0.6) * 0.6)
        else:
            # 다 자란 칼날: 흰 날 (가운데가 가장 밝음) · 홈 (가운데 줄) · 날 끝 테
            body = blade * (0.55 + 0.45 * np.exp(-(dy / (hw * 0.55)) ** 2))
            core = np.maximum(core, body * 0.9)
            core = np.maximum(core, pad.stroke([(xh + 4, cy), (xt - 26, cy)], 1.3, taper=0.3, soft=0.6) * 1.0)
            glow = np.maximum(glow, blade)
            glow = np.maximum(glow, gblur(blade, 24.0) * 0.9)
            if f in (3, 4, 5):
                # 홈을 따라 칼끝으로 흐르는 빛 띠 (장마다 앞으로) · 날 가장자리 반짝임 · 오르는 빛 알갱이
                ph = (f - 3) / 3.0
                for k in range(3):
                    u = (ph + k / 3.0) % 1.0
                    bx = xh + (xt - xh) * u
                    core = np.maximum(core, np.exp(-((X - bx) / 14.0) ** 2) * blade * 0.9)
                for _ in range(5):
                    u = rs.uniform(0.1, 0.95)
                    px = xh + (xt - xh) * u
                    side = rs.choice([-1, 1])
                    core = np.maximum(core, star4(pad, px, cy + side * hw * (1 - 0.8 * max(0.0, (u - 0.82) / 0.18)), rs.uniform(6, 10), 0.9, rot=rs.uniform(0, 1),
                                                  thin=0.14, diag=0.4))
                for _ in range(14):
                    px = rs.uniform(xh, xt)
                    py = cy + rs.choice([-1, 1]) * rs.uniform(hw + 4, 28)
                    glow = np.maximum(glow, pad.disc_field(px, py, rs.uniform(1.0, 2.0), soft=0.8) * rs.uniform(0.4, 0.9))
            if f == 2:
                # 번쩍: 칼끝 큰 별 · 날 전체가 하얗게
                core = np.maximum(core, blade)
                core = np.maximum(core, star4(pad, xt - 34, cy, 26.0, 1.0, rot=0.0, thin=0.07, diag=0.3))
                glow = np.maximum(glow, pad.radial(xt - 34, cy, 28.0, 1.6) * 0.7)
            if f == 6:
                # 휘두름: 날 둘레로 끌리는 빛 (위아래 결 줄)
                for k in range(26):
                    px = xh + (xt - xh) * (k + 0.5) / 26
                    L = (10 + 18 * (k / 26)) * rs.uniform(0.7, 1.1)
                    s = 1 if k % 2 == 0 else -1
                    glow = np.maximum(glow, pad.stroke([(px, cy + s * hw), (px - 6, cy + s * (hw + L))], 2.6, 0.6, a0=0.8, a1=0.0, soft=1.2))
                    core = np.maximum(core, pad.stroke([(px, cy + s * hw), (px - 4, cy + s * (hw + L * 0.6))], 1.0, 0.3, a0=0.6, a1=0.0, soft=0.5))
                core = np.maximum(core, blade)
            if f == 7:
                hole = clamp01((fbm(X / W * 9.0, Y / H * 2.0, seed=16250, octaves=4, px=18, py=4) - 0.42) * 3.0)
                core = core * (1 - hole) * 0.8
                glow = glow * (1 - hole * 0.8)
                for _ in range(30):
                    px = rs.uniform(xh, xt)
                    py = cy + rs.uniform(-24, 24)
                    core = np.maximum(core, pad.disc_field(px, py, rs.uniform(1.0, 2.2), soft=0.8) * rs.uniform(0.5, 1.0))
        # 코등이 · 손잡이 (빛으로 된 — 테만)
        guard = pad.stroke([(xh - 1, cy - 17), (xh - 1, cy + 17)], 3.0, taper=0.4, soft=0.6)
        grip = pad.stroke([(x_grip + 2, cy), (xh - 2, cy)], 2.4, soft=0.6)
        core = np.maximum(core, (guard + grip * 0.7) * (0.6 if f == 1 else 0.9))
        glow = np.maximum(glow, pad.stroke([(xh - 1, cy - 19), (xh - 1, cy + 19)], 9.0, soft=3.0) * 0.6)
    glow = glow * lum
    core = core * lum
    return finish(pad, glow, core, halo=((1.5, 0.5), (4.0, 0.42), (10.0, 0.28)), body_k=0.9, core_bloom=((1.2, 0.42), (3.0, 0.3)), win=3.0)


sprite("rfx_jexcal", "lookat_direction", XC_W, XC_H, XC_N, 2, "swordmaster", zoom=0,
       desc="빛의 대검 (소드마스터 엑스칼리버 — 가는 쪽으로 누움, 손잡이 = 왼쪽 · 칼끝 = 오른쪽): 0 둘레의 빛 알갱이가 한 줄로 모여 듦 → 1 칼날 테가 자라남 → 2 다 자라 번쩍 "
       "(칼끝 큰 별) → 3 ~ 5 빛남 (이어 돌림 — 흰 칼날 · 금빛 번짐 · 홈을 따라 칼끝으로 흐르는 빛 · 날 반짝임 · 오르는 빛 알갱이) → 6 휘두름 (날 둘레로 끌리는 빛) → 7 빛 알갱이로 "
       "흩어짐. 빛 칸 = 금빛 번짐, 심지 칸 = 흰 칼날 · 홈 · 반짝임")(excal_frame)


# ============================================================ 2026-11-07 재설계안 3판 — 프로스트 레이더 (전사 반전 · 글래디에이터 대신)
# 얼음 결정 감옥 (rfx_jicecage — 샤터 · 핌불윈터의 빙하). 빛 칸 = 비치는 얼음 몸 (면마다 옅고 짙게 — 안의 적이 보이게) · 서리 · 번짐, 심지 칸 = 모서리 · 면 줄 · 반짝임 · 금

IC_N = 8
IC_W, IC_H = 256, 384
# 결정: 밑동 가운데 x · 높이 · 반 너비 · 꼭대기 기울기 (x 점) · 자라는 늦음 (0 ~ 1)
IC_CRYSTALS = [(54, 150, 24, -26, 0.35), (204, 136, 22, 28, 0.3), (88, 252, 36, -16, 0.15), (172, 270, 38, 18, 0.1), (128, 352, 56, 0, 0.0)]


def _ic_pts(bx, base, h, w, lean, rise=1.0):
    """결정 하나의 앞모습 (육각 기둥 — 밑 왼쪽 · 왼 어깨 · 꼭지 · 오른 어깨 · 밑 오른쪽): rise = 자란 몫 (뾰족한 꼴 그대로 키가 자람 · 조금 가늚)"""
    hh = h * max(0.05, rise)
    ww = w * (0.75 + 0.25 * rise)
    sh = base - hh * 0.8
    ll = lean * rise
    apex = (bx + ll, base - hh)
    return [(bx - ww, base + 6), (bx - ww + ll * 0.8, sh), apex, (bx + ww + ll * 0.8, sh), (bx + ww, base + 6)], apex


def _ic_crystal(pad, X, Y, bx, base, h, w, lean, rise, glint=None):
    """결정 하나 (빛 · 심지): 왼 면 (짙게) · 가운데 면 (옅게 — 안이 비침) · 오른 면, 모서리 · 면 줄 (심지), 밑동 서리 (빛)"""
    pts, apex = _ic_pts(bx, base, h, w, lean, rise)
    m = pad.poly(pts)
    t = clamp01((base - Y) / max(1.0, h))
    u = (X - (bx + lean * 0.8 * t)) / max(1.0, w)
    shade = np.where(u < -0.38, 0.62, np.where(u > 0.38, 0.5, 0.3)).astype(np.float32)
    frost = clamp01((fbm(X / 40.0, Y / 40.0, seed=17010 + int(bx), octaves=4, px=7, py=10) - 0.35) * 2.2) * clamp01(1.0 - t * 1.6)
    glow = m * (shade + frost * 0.35)
    core = m * frost * 0.18
    # 모서리 (닫힌 테) · 면 줄 (밑에서 어깨까지 — 꼭지로 모임)
    ring = pts + [pts[0]]
    core = np.maximum(core, pad.stroke(ring, 2.4, soft=0.8) * 0.95)
    glow = np.maximum(glow, pad.stroke(ring, 7.0, soft=3.0) * 0.7)
    sh_y = pts[1][1]
    for s in (-1, 1):
        fx0 = bx + s * w * 0.38
        fx1 = bx + s * w * 0.38 + lean * 0.8 * clamp01((base - sh_y) / max(1.0, h))
        line = [(fx0, base + 4), (fx1, sh_y), apex]
        core = np.maximum(core, pad.stroke(line, 1.3, soft=0.6) * 0.55)
    # 왼 면의 긴 빛 반사 (비스듬한 렌즈)
    if rise > 0.5:
        p0 = (bx - w * 0.68, base - h * 0.22)
        p1 = (bx - w * 0.52 + lean * 0.55, base - h * min(0.72, rise * 0.72))
        core = np.maximum(core, lens(pad, p0, p1, max(3.0, w * 0.12), 0.75, power=0.6) * m)
    # 반짝이며 지나가는 빛 (glint = 위아래 자리 0 ~ 1 — 이어 돌림)
    if glint is not None and rise >= 1.0:
        gy = base - h * (0.15 + 0.75 * glint)
        band = np.exp(-(((Y - gy) + (X - bx) * 0.55) / 10.0) ** 2) * m
        core = np.maximum(core, band * 0.75)
        glow = np.maximum(glow, band * 0.5)
    return m, glow, core, pts, apex


def _shift(a, sx, sy):
    """그림판을 (sx, sy) 점 밀기 — 밀려 나간 곳은 버리고 빈 곳은 0 (np.roll 처럼 감기지 않게)"""
    out = np.zeros_like(a)
    h, w = a.shape
    ys0, ys1 = max(0, sy), min(h, h + sy)
    xs0, xs1 = max(0, sx), min(w, w + sx)
    if ys1 <= ys0 or xs1 <= xs0:
        return out
    out[ys0:ys1, xs0:xs1] = a[ys0 - sy:ys1 - sy, xs0 - sx:xs1 - sx]
    return out


def _ic_cracks(rs, bx, base, h, w, lean):
    """결정 안의 금 (가운데 쯤에서 사방으로 — 갈래 둘 셋)"""
    c = (bx + lean * 0.4 + rs.uniform(-w * 0.2, w * 0.2), base - h * rs.uniform(0.4, 0.55))
    out = []
    for k in range(rs.randint(4, 6)):
        a = rs.uniform(0, TAU)
        L = rs.uniform(0.5, 1.0) * min(w * 1.6, h * 0.45)
        pts = [c]
        x, y = c
        for i in range(1, 5):
            aa = a + rs.uniform(-0.45, 0.45)
            x, y = x + math.cos(aa) * L / 4, y + math.sin(aa) * L / 4 * 1.5
            pts.append((x, y))
        out.append(pts)
    return c, out


def icecage_frame(f):
    """얼음 결정 감옥 (아래 = 땅): 0 서리가 기어올라 결정이 솟음 → 1 다 솟아 번쩍 → 2 ~ 4 갇혀 있음 (이어 돌림) → 5 금 → 6 깨짐 → 7 조각이 흩어짐"""
    W, H = IC_W, IC_H
    pad = Pad(W, H)
    X, Y = pad.grid()
    base = H - 8.0
    glow = pad.zeros()
    core = pad.zeros()
    rs = random.Random(17000 + f)
    rise_f = [0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    glints = {2: 0.1, 3: 0.45, 4: 0.8}
    cx, cy = W / 2, base - 170.0
    if f <= 5:
        for i, (bx, h, w, lean, late) in enumerate(IC_CRYSTALS):
            rise = clamp01((rise_f - late * 0.5) / (1 - late * 0.5)) if f == 0 else 1.0
            if rise <= 0.03:
                continue
            m, g, c, pts, apex = _ic_crystal(pad, X, Y, bx, base, h, w, lean, rise, glints.get(f))
            glow = np.maximum(glow, g)
            core = np.maximum(core, c)
            if f == 0:
                # 자라는 끝: 서리 김 · 반짝임
                core = np.maximum(core, star4(pad, apex[0], apex[1], 9.0 + w * 0.15, 0.9, rot=0.3 * i, thin=0.12, diag=0.4))
                glow = np.maximum(glow, pad.radial(apex[0], apex[1], 24.0, 1.6) * 0.5)
            if f == 1:
                glow = np.maximum(glow, m * 0.75)
                core = np.maximum(core, m * 0.35)
            if f == 5:
                cc, cracks = _ic_cracks(random.Random(17100 + i), bx, base, h, w, lean)
                for line in cracks:
                    core = np.maximum(core, pad.stroke(line, 2.6, 1.0, a0=1.0, a1=0.6) * m)
                    glow = np.maximum(glow, pad.stroke(line, 9.0, 4.0, a0=0.6, a1=0.25) * m)
                core = np.maximum(core, star4(pad, cc[0], cc[1], 10.0 + w * 0.2, 0.9, rot=0.2 * i, thin=0.1, diag=0.35))
        if f == 1:
            core = np.maximum(core, star4(pad, 128, base - 330, 34.0, 1.0, rot=0.0, thin=0.07, diag=0.3))
            glow = np.maximum(glow, pad.radial(128, base - 330, 40.0, 1.6) * 0.6)
        if f in (2, 3, 4):
            for k in range(4):
                bx, h, w, lean, _ = IC_CRYSTALS[rs.randrange(len(IC_CRYSTALS))]
                px = bx + rs.uniform(-w * 0.8, w * 0.8)
                py = base - h * rs.uniform(0.15, 0.75)
                core = np.maximum(core, star4(pad, px, py, rs.uniform(6, 11), 0.85, rot=rs.uniform(0, 1), thin=0.13, diag=0.4))
    else:
        # 깨짐: 결정마다 위 · 가운데 · 아래 세 덩이 × 왼 · 오른 두 쪽 → 가운데에서 바깥으로 밀려남 (7 은 멀리 · 흐리게)
        a = 1.0 if f == 6 else 0.5
        for i, (bx, h, w, lean, _) in enumerate(IC_CRYSTALS):
            m, g, c, pts, apex = _ic_crystal(pad, X, Y, bx, base, h, w, lean, 1.0)
            cuts = [base + 10, base - h * 0.33, base - h * 0.64, base - h - 10]
            slope = [0.0, 0.45 if i % 2 else -0.45, -0.4 if i % 2 else 0.4, 0.0]
            r2 = random.Random(17200 + i)
            for k in range(3):
                lo = cuts[k] + (X - bx) * slope[k]
                hi = cuts[k + 1] + (X - bx) * slope[k + 1]
                band = ((Y <= lo) & (Y > hi)).astype(np.float32)
                mid = bx + lean * 0.8 * (k + 0.5) / 3
                yy = (cuts[k] + cuts[k + 1]) / 2
                for side in (-1, 1):
                    half = (((X - mid + (Y - yy) * (0.35 if k % 2 else -0.35)) * side) >= 0).astype(np.float32)
                    pm = m * band * half
                    if pm.max() <= 0:
                        continue
                    py = (cuts[k] + cuts[k + 1]) / 2
                    px = mid + side * w * 0.5
                    dx, dy = px - cx, py - cy
                    dl = max(1.0, math.hypot(dx, dy))
                    push = (10.0 if f == 6 else 46.0) * r2.uniform(0.7, 1.3)
                    ox = dx / dl * push + side * (4.0 if f == 6 else 12.0)
                    oy = dy / dl * push + (0.0 if f == 6 else 22.0)
                    sx, sy = int(round(ox * pad.ss)), int(round(oy * pad.ss))
                    gm = _shift(g * band * half, sx, sy)
                    cm = _shift(c * band * half, sx, sy)
                    rm = _shift(rim_of(pm, 0.6), sx, sy)
                    glow = np.maximum(glow, gm * a)
                    core = np.maximum(core, np.maximum(cm, rm * 0.9) * a)
        if f == 6:
            core = np.maximum(core, star4(pad, cx, cy, 52.0, 1.0, rot=0.2, thin=0.06, diag=0.3))
            glow = np.maximum(glow, pad.radial(cx, cy, 90.0, 1.4) * 0.6)
        for _ in range(26 if f == 6 else 18):
            px, py = rs.uniform(16, W - 16), rs.uniform(base - 330, base - 10)
            core = np.maximum(core, pad.disc_field(px, py, rs.uniform(1.2, 2.6), soft=0.8) * rs.uniform(0.5, 1.0) * (1.0 if f == 6 else 0.7))
    # 밑동 서리 (솟는 동안 · 서 있는 동안 — 깨지면 흐려짐)
    fa = [0.9, 0.8, 0.65, 0.65, 0.65, 0.7, 0.5, 0.25][f]
    glow = np.maximum(glow, pad.radial(W / 2, base, 124.0, 1.5, sy=0.16) * fa)
    rb = random.Random(17300)
    for k in range(12):
        x = rb.uniform(20, W - 20)
        y = base - rb.uniform(0, 14)
        mm, bb, cc = faceted(pad, chunk_pts(rb, x, y, rb.uniform(3.0, 7.0)), a=0.85 * fa)
        glow = np.maximum(glow, bb * 0.8)
        core = np.maximum(core, cc * 0.5)
    return finish(pad, glow, core, halo=((1.5, 0.45), (4.0, 0.35), (9.0, 0.2)), body_k=0.9, core_bloom=((1.2, 0.4), (3.0, 0.25)), win=4.0)


COLORS.setdefault("frostraider", ((0.6, 0.9, 1.0), (0.94, 0.99, 1.0)))
sprite("rfx_jicecage", "lookat_y", IC_W, IC_H, IC_N, 4, "frostraider", zoom=0,
       desc="얼음 결정 감옥 (프로스트 레이더 샤터 · 핌불윈터의 빙하 — 세워 카메라 쪽으로 돎, 아래 끝 = 땅 · 높이 = 반 너비 × 3): 0 서리가 기어올라 육각 얼음 기둥 다섯이 솟음 "
       "(가운데가 먼저 · 끝의 반짝임) → 1 다 솟아 번쩍 → 2 ~ 4 갇혀 있음 (이어 돌림 — 면마다 옅고 짙은 비치는 얼음 · 모서리 · 면 줄 · 왼 면의 긴 빛 반사 · 위로 지나가는 "
       "빛 띠 · 반짝임 · 밑동 서리) → 5 금이 사방으로 감 → 6 깨짐 (덩이가 바깥으로 밀려남 · 가운데 섬광) → 7 조각이 멀리 흩어지며 흐려짐. 빛 칸 = 비치는 얼음 몸 · 서리 · "
       "번짐, 심지 칸 = 모서리 · 면 줄 · 반짝임 · 금")(icecage_frame)


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
