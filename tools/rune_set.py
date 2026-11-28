#!/usr/bin/env python3
"""룬 재개발 (2026-11-17) — 계열 없이 등급만 (등급마다 6 = 30) · 룬마다 재사용 · 스탯과 상관없는 룬 위력 → core/src/main/resources/mmorpg/rules_runes.json

사용자: "직업별 스탯별 룬 있는데 그거 없애고 그냥 표시를 없애라는 뜻 … 체감상 이게 힘룬이구나를 느끼게" · "덤을 주면 안된다 … 모든 룬이 밸런스가 맞고" ·
"받피증은 딜러들한테 안넣어주고 힐러랑 한정 룬에만" · "다 쿨타임도 다르게 해서 딜 사이클도 다르게" ·
"쿨 바꿔서 직업 스킬하고 딜타임 만들어둘건데 그사이에 꽂아넣을수 있도록" ·
"버프 같은 스킬도 쿨 살짝 다르게 해서 딜레이 만들기 + 검사면 검사랑 어울리는 룬 스킬이 있으면 해당하는 두 스킬에 쿨이 같게 설정" →
2026-11-17 "쿨타임이 너무 극단적인거 같아서 쿨타임을 정해줌 일반은 6~15초 이내에서 만들고 희귀는 12초~21초 사이 고귀는 15초~25초 사이
영웅은 27초~38초 사이 초월은 32초~50초 사이로" (판 1 의 쓰임별 필러 4 ~ 8초 · 딜타임 27.5 ~ 70초 대신 등급마다 범위 — GRADE_CD).

규칙 (claude/rune-job-synergy.md · Docs 「룬 재개발 컨셉 (초안)」):
- 예산 (총 배율) = 등급의 초당 값 × 재사용 (일반 400%/8초 · 희귀 700/12 · 고귀 1200/18 · 영웅 2000/26 · 초월 3200/36 — 초당 값은 그대로) → 재사용이 길면 한 번이 셈
- 재사용 = 등급 범위 안 (GRADE_CD — 일반 6 ~ 15 · 희귀 12 ~ 21 · 고귀 15 ~ 25 · 영웅 27 ~ 38 · 초월 32 ~ 50초) — 범위 안에 어울리는 직업 스킬 (pair) 이 있으면
  그 스킬과 같은 쿨 (jobs.json 과 맞는지 · 범위 안인지 여기서 봄 · 직업 스킬 하나에 룬 하나 · RUNE_SET.pairs → RuneScaleTest). 범위 안에 어울리는 스킬이 없으면 짝 없음
- 실효 = 예산 × 거리 (근 ×1.2 · 중 ×1.1 · 원 ×1.0) × 원소 (−5%) × (1 − 유틸) × 시간 보정 × 범위 보정 — Runes.damageScale (+ 거리 · scale)
- 시간 보정: 원거리 시전 룬 (cast) 은 선딜 보정 없음 (거리 보정 안) · 막는 선딜 (spread guard — 선딜 동안 피해 감소) 도 선딜 보정 없음
- 유틸 값 = 표의 값 × 등급 기준 재사용 ÷ 룬의 재사용 (RUNE_SET.baseCd — Runes.utilCdFactor: 한 번에 같은 유틸 → 쿨이 길면 드물게 켜져 초당 값이 작음)
- 2026-11-28 지키는 유틸 (버팀 · 회복 · 흡혈 · 받는 피해 감소 · 보호막 · 아군 보호) 은 쿨이 등급 기준보다 짧으면 지속 · 세기를 쿨 ÷ 기준 만큼 줄임 (사용자 "너가 말한대로 하자" —
  더 자주 켜지는 만큼 한 번을 줄여 켜져 있는 몫을 등급 기준 그대로 · FIT: 룬마다 줄인 비율, 그 유틸 값 = 표의 값 × 비율 → uCost)
- 룬 위력 = 네 스탯의 합 (한정 룬 식 — sum)
- 받는 피해 증가는 일반 룬에 없음 (한정 룬만)
- 동작 (fn) 은 처음에 옛 룬의 것을 이어 씀 (look = 옛 계열:등급 — 위르드식 그림 묶음), 새 컨셉 여덟은 비슷한 옛 동작 → 시범 · 등급 묶음에서 전용 동작으로

tools/rune_set.py            rules_runes.json 을 씀 + 표 (실효) 를 찍음
tools/rune_set.py --md       표를 마크다운으로
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
OUT = os.path.join(RES, "rules_runes.json")

GRADES = ["common", "rare", "noble", "hero", "transcend"]
GRADE_KO = {"common": "일반", "rare": "희귀", "noble": "고귀", "hero": "영웅", "transcend": "초월"}
# 등급의 (예산 %, 재사용 초) — 초당 값 = 예산 ÷ 재사용
GRADE_BASE = {"common": (400, 8), "rare": (700, 12), "noble": (1200, 18), "hero": (2000, 26), "transcend": (3200, 36)}
# 2026-11-28 다섯째 한정 룬 기준 (예산 34 · 재사용 36초) — 초월 등급 상한의 "다음 등급" (사용자 "모두가 해당 룬을 쓸거 같다는거 있으면 없애": 그랜드 크로스 112%)
LIMITED_BASE = (3400, 36)
# 2026-11-17 등급마다 재사용 범위 (초 — 사용자가 정함, 양 끝 포함)
# 2026-11-18 받는 피해 감소의 기준 (오록스 차지 −30% · 2초 = 완전히 막은 시간 0.6초 — 표의 값 0.1)
REDUCE_REF = 0.3 * 2
# 2026-11-28 지원 룬 (사용자 "근접 및 힐러 등 같은 애들의 룬 추가가 필요할듯" → 고른 것 "회복 + 아군 버프 반반"): 피해 없음 — 예산 (등급 초당 값 × 쿨, 1 = 위력 P 의 100%)
# 을 모두 회복 · 버프로 (claude/rune-job-synergy.md "지원 룬의 회복 · 버프는 유틸 값으로 피해 몫을 치름 (그로브 블레싱처럼)"). 값의 기준:
#   회복 (최대 체력 × 회복 배율 — 쓴 사람 몫, 둘레 아군은 share): 그로브 블레싱 (희귀 15초 · 예산 8.75 → 0.1667)
#   가하는 피해 + · 받는 피해 − (나 · 아군 모두 같은 세기) 1% · 1초: 세계수의 가호 (한정 48초 · 예산 34 ÷ 36 × 48 = 45.3 → 40렙 125포인트 0돌 +20% · −20% 12초)
#   공격 속도 + 1% · 1초 = 가하는 피해 × 평타 몫 0.3 (claude/balance-check.md — 칼 지속 피해의 평타 몫 37%: 공격 속도는 평타만 빠르게 함)
#   이동 속도 + 1% · 1초: 칼바람 폭풍 (이동 속도 유틸 0.05 × 영웅 예산 20 = 1 ↔ 신속 I (+20%) 2.5초)
#   나쁜 효과 제거 하나 = 유틸 값 cleanse 0.05 × 예산 · 장판 회복 (자리에 남아야 받음) = 시간 보정의 장판과 같게 4초 안 +10% · 넘으면 +15%
SUPPORT_HEAL = 0.1667 / 8.75
SUPPORT_BUFF = (3400 / 36 * 48 / 100) / ((20 + 20) * 12)
SUPPORT_HASTE = SUPPORT_BUFF * 0.3
SUPPORT_MOVE = 1.0 / (20 * 2.5)
SUPPORT_TOL = 0.05
# 툴팁 · 설명 글의 기준 회복 배율 (40렙 125포인트 — 네 스탯 합 125 × perPoint 0.02 × 0.5 + 1)
SUPPORT_REF_SCALE = 2.25
# 2026-11-28 지키는 유틸 — 쿨이 등급 기준보다 짧은 룬은 지속 · 세기를 쿨 ÷ 기준 만큼 (룬의 fit = 줄인 비율 · 기준과 0.03 안)
GUARD_UTILS = {"undying", "heal", "lifesteal", "reduce", "shield", "allyGuard"}
FIT_TOL = 0.03

GRADE_CD = {"common": (6, 15), "rare": (12, 21), "noble": (15, 25), "hero": (27, 38), "transcend": (32, 50)}
RANGE = {"melee": 1.2, "mid": 1.1, "far": 1.0}
RANGE_KO = {"melee": "근", "mid": "중", "far": "원"}
ELEM_KO = {"fire": "불", "water": "물", "thunder": "전기", "ice": "얼음", "rock": "바위", "multi": "여럿"}
OLD_VISIBLE = ["str", "dex", "vit", "int", "axe", "bow", "assassin", "blade", "spirit"]

# 새 룬 30 — key · 이름 · 등급 · 재사용 · 거리 · 출처 (옛 계열.등급 — fn · 그림 묶음) · 원소 · 유틸 · 범위 · 시전 (초 — 원거리) · 덮어쓸 값 · 시간 표 덮기
# 설명 (desc · use) 은 컨셉 문서의 흐름을 줄인 것 — 받는 피해 증가 (표식 · 방어 깨짐) 는 뺌
SET = [
    # ---------------------------------------------------------------- 일반 (초당 50%) — pair = 같은 쿨의 어울리는 직업 스킬 id
    dict(key="earthbreaker", name="어스 브레이커", grade="common", cd=7, pair=["warrior_crush"], r="melee", src="str.common", elem="rock", u=["stun05"], a="narrow",
         desc="무기로 앞 땅을 내려찍어 바위 기둥이 솟음 · 기절", use="근접 부채꼴 · 기절 0.5초"),
    dict(key="boomerangaxe", name="부메랑 액스", grade="common", cd=8, pair=["frostraider_throw"], r="mid", src="axe.common", u=[], a="narrow",
         set={"buff": 0}, desc="룬 도끼를 회전시켜 던져 갔다가 돌아옴", use="직선 왕복 2타"),
    dict(key="galerush", name="게일 러시", grade="common", cd=12, pair=["swordsman_flash"], r="melee", src="dex.common", u=["blink"], a="narrow",
         desc="앞으로 돌진하며 지나간 자리를 벰", use="돌진 · 이동"),
    dict(key="icelance", name="아이스 랜스", grade="common", cd=6, pair=["mage_bolts"], r="far", src="int.common", elem="ice", u=["slow"], a="narrow", cast=0.5,
         set={"fn": "iceLance", "fire": 0, "slowTicks": 40, "slowAmp": 1}, spread={"t": 6}, desc="얼음 창을 쏴 꽂고 터뜨림 · 둔화", use="원거리 · 파열 · 둔화 2초"),
    dict(key="salamander", name="샐러맨더", grade="common", cd=7, pair=["necromancer_call"], r="mid", src="spirit.common", elem="fire", u=[], a="narrow",
         desc="샐러맨더를 불러 4초 동안 불덩이를 뱉게 함", use="소환 · 불덩이 4번"),
    # 2026-11-17 램파트 · 블레이드 헤일 · 아이스 랜스는 새 동작 (RuneSet · RuneInt.iceLance — 옛 동작을 빌리지 않음), look = 그림 묶음 계열 (꼬리표 · 빛)
    dict(key="rampart", name="램파트", grade="common", cd=15, pair=["guardian_throw"], r="melee", src="blade.noble", u=["reduce", "knock"], a="narrow", look="gladiator:common",
         set={"fn": "rampart", "ticks": 60, "reduce": 0.5, "knock": 0.9}, spread={"t": 60, "w": 60, "guard": True}, guard_sec=3,
         desc="빛의 방패 결계로 3초 버티고 앞으로 밀어 냄", use="받는 피해 −50% 3초 · 끝에 앞을 밀쳐냄"),
    # 2026-11-28 근접 딜 새 룬 (사용자 "근접 및 힐러 등 같은 애들의 룬 추가가 필요할듯" → 고른 것 "등급마다 2개 · 10개" · "근접 딜 5개 먼저"):
    # 옛 동작을 빌리지 않음 (src 없음 — 정의는 set 만, RuneMelee). 크로스 컷 = 민첩 (가는 칼 · 빠른 X) · 짝 암살자 블랙 로즈 8초
    dict(key="crosscut", name="크로스 컷", grade="common", cd=8, pair=["assassin_bloom"], r="melee", stat_fam="dex", look="blade:common", u=[], a="narrow",
         set={"fn": "crossCut", "mult": 1.6, "crush": 2.4, "range": 3.8, "cone": 120, "hold": 10, "multName": "X 베기 ", "crushName": "X 폭발",
              "flow": ["칼날을 X 자로 두 번 그어 앞 {range}칸을 벰", "{t:hold} 뒤 허공의 X 가 터짐"]},
         spread={"t": 13, "w": 3}, desc="칼날을 X 자로 두 번 그은 뒤 터뜨림", use="근접 X 베기 · 0.5초 뒤 폭발"),
    # 2026-11-28 지원 룬 다섯 (SUPPORT_* — 회복은 예산의 남는 몫 전부): 라이프 볼트 = 겨눈 아군 (없으면 나) 에게 생명의 빛살 · 짝 사제 루미너스 터치 6초
    dict(key="lifebolt", name="라이프 볼트", grade="common", cd=6, pair=["priest_heal"], r="far", stat_fam="vit", look="spirit:common", u=[], support=True,
         set={"fn": "lifeBolt", "range": 16, "splash": 3, "share": 0.5, "flight": 6,
              "flow": ["겨눈 아군 ({range}칸 — 없으면 나) 에게 생명의 빛살", "닿은 자리 둘레 {splash}칸 아군은 절반"]},
         desc="생명의 빛살로 겨눈 아군 (없으면 나) 을 회복 · 빛 꽃이 핌", use="회복 — 대상 {heal} · 둘레 아군 {ally}"),
    # ---------------------------------------------------------------- 희귀 (초당 58%)
    dict(key="solarwave", name="솔라 웨이브", grade="rare", cd=13, pair=["swordmaster_echo"], r="mid", src="blade.rare", u=[], look="holy:rare",
         desc="크게 감아 가로로 베어 태양빛 검기를 날림", use="직선 관통 검기"),
    dict(key="chainarrow", name="체인 애로", grade="rare", cd=12, pair=["bladeranger_boomerang"], r="far", src="bow.rare", u=["root"], a="single", cast=0.5,
         set={"root": 30}, desc="룬 활로 쏜 화살이 첫 적을 땅에 사슬로 묶음", use="원거리 단일 · 묶음 1.5초"),
    dict(key="shadowstrike", name="섀도 스트라이크", grade="rare", cd=13, pair=["trickster_twin"], r="mid", src="dex.noble", u=[],
         set={"vuln": 0, "vulnTicks": 0}, desc="그림자 분신이 둘레 적들을 하나씩 등 뒤에서 벰", use="여러 적 · 기습"),
    dict(key="undine", name="운디네", grade="rare", cd=18, r="mid", src="spirit.rare", elem="water", u=["knock"], a="narrow",
         desc="운디네를 불러 물줄기를 세 번 쏘게 함", use="소환 · 관통 · 밀쳐냄"),
    dict(key="aurochs", name="오록스 차지", grade="rare", cd=12, pair=["darkknight_lunge"], r="melee", src="gladiator.rare", u=["blink", "knock", "reduce"], a="narrow",
         set={"range": 6, "bison": 1}, guard_sec=2, desc="들소 영혼과 함께 앞으로 돌진해 부딪힌 적을 밀쳐냄", use="돌진 · 밀쳐냄 · 받는 피해 −30%"),
    dict(key="grove", name="그로브 블레싱", grade="rare", cd=15, pair=["priest_hymn"], r="mid", src="vit.rare", u=[], support=True,
         # 2026-11-17 쿨 6 → 15초: 회복도 초당 그대로 (0.0667 × 15 ÷ 6)
         set={"heal": 0.1667, "pulses": 4, "gap": 15, "radius": 5, "share": 0.5}, desc="발밑에 새싹이 자라 빛 잎을 흩날리며 나와 둘레 아군을 회복",
         use="회복 — 나 37% · 아군 19% (3초)"),
    # 2026-11-28 근접 딜 새 룬: 브루탈 클리브 = 힘 (크게 쓸어 벰 · 갈라진 땅 · 낮은 쿵) · 짝 전사 램페이지 러시 12초
    dict(key="brutalcleave", name="브루탈 클리브", grade="rare", cd=12, pair=["warrior_charge"], r="melee", stat_fam="str", look="str:rare", u=["knock"],
         set={"fn": "brutalCleave", "mult": 4.2, "crush": 2.8, "radius": 5.0, "cone": 200, "knock": 0.8, "quake": 10, "multName": "휩쓸기 ", "crushName": "땅 터짐",
              "flow": ["거대한 칼로 앞 {radius}칸 반원을 쓸어 벰 · 밀쳐냄", "{t:quake} 뒤 벤 자리를 따라 땅이 터짐"]},
         spread={"t": 12, "w": 2}, desc="거대한 칼로 앞을 크게 쓸어 벤 뒤 땅이 터짐", use="앞 반원 · 밀쳐냄 · 땅 터짐"),
    # 2026-11-28 지원: 워 드럼 = 전쟁 북 세 번 · 나와 둘레 아군 공격 속도 + 이동 속도 (신속 I) · 짝 세인트 페탈 베일 12초 (이동 속도 +40% 3초)
    dict(key="wardrum", name="워 드럼", grade="rare", cd=12, pair=["saint_veil"], r="mid", stat_fam="vit", look="str:rare", u=[], support=True,
         set={"fn": "warDrum", "haste": 0.35, "speedAmp": 0, "buffTicks": 100, "radius": 8, "beats": 3, "beatGap": 6,
              "flow": ["전쟁 북을 {beats}번 울림 — 나와 둘레 {radius}칸 아군 {t:buffTicks}"]},
         desc="전쟁 북을 세 번 울려 나와 둘레 아군을 북돋움", use="공격 속도 +35% · 이동 속도 +20% (5초)"),
    # ---------------------------------------------------------------- 고귀 (초당 67%)
    dict(key="fissureslam", name="피셔 슬램", grade="noble", cd=16, r="mid", src="str.noble", u=["lift"], a="narrow",
         set={"vuln": 0, "vulnTicks": 0}, desc="위에서 아래로 내려찍어 앞 땅을 가름 · 띄움", use="직선 관통 · 띄움"),
    dict(key="bladehail", name="블레이드 헤일", grade="noble", cd=15, pair=["bloodknight_chains"], r="melee", src="dex.hero", u=[],
         set={"fn": "bladeHail", "speedTicks": 0}, spread={"t": 44, "w": 4, "zone": True}, desc="둘레에 룬 칼날이 우박처럼 꽂힘 (움직일 수 있음)", use="둘레 연타 · 장판"),
    dict(key="thundervolley", name="썬더 볼리", grade="noble", cd=15, pair=["arsenalking_rain"], r="far", src="bow.noble", elem="thunder", u=["leap"], cast=0.5,
         desc="뒤로 뛰며 번개 화살을 부채꼴로 쏨", use="뒤로 도약 · 부채꼴 연사"),
    dict(key="frostburst", name="프로스트 버스트", grade="noble", cd=15, pair=["frostraider_shatter"], r="melee", src="int.rare", elem="ice", u=["stun1"],
         desc="발을 굴러 둘레에 얼음 가시를 솟게 함 · 빙결", use="둘레 · 빙결 1초"),
    dict(key="firestorm", name="파이어 스톰", grade="noble", cd=18, pair=["warlock_nova"], r="far", src="int.noble", elem="fire", u=["pull"], cast=0.7,
         desc="겨눈 곳에 불기둥 소용돌이 · 끌어당김", use="장판 · 끌어당김"),
    dict(key="fortress", name="포트리스 슬램", grade="noble", cd=22, r="melee", src="golem.rare", elem=None, u=["shield"],
         desc="땅을 내리쳐 둘레에 성벽 조각을 세웠다가 쓰러뜨림 · 몸을 지킴", use="둘레 2타 · 보호막"),
    # 2026-11-28 근접 딜 새 룬: 팬텀 스러스트 = 민첩 (빠른 찌르기 · 그림자 분신) · 짝 피에로 벌룬 퍼레이드 15초
    dict(key="phantomthrust", name="팬텀 스러스트", grade="noble", cd=15, pair=["pierrot_balloon"], r="melee", stat_fam="dex", look="assassin:noble", u=[], a="narrow",
         set={"fn": "phantomThrust", "mult": 3.0, "hits": 2, "crush": 4.0, "length": 5.5, "width": 1.3, "gap": 10, "multName": "분신 찌르기 ", "crushName": "환영 창",
              "flow": ["앞 {length}칸을 꿰뚫어 찌름", "그림자 분신 둘이 함께 찌름 → 셋이 환영 창으로"]},
         spread={"t": 21, "w": 1}, desc="그림자 분신들과 함께 앞을 세 번 꿰뚫음", use="직선 관통 3연타"),
    # 2026-11-28 지원: 새크리드 서클 = 발밑 성스러운 원 (자리에 남음 · 5초 — 장판 4초 넘게 +15%) · 처음에 나쁜 효과 하나 제거 · 짝 세인트 글리머윙 15초
    dict(key="sacredcircle", name="새크리드 서클", grade="noble", cd=15, pair=["saint_wing"], r="mid", stat_fam="vit", look="holy:noble", u=[], support=True,
         zone=True, set={"fn": "sacredCircle", "radius": 4.5, "pulses": 6, "gap": 20, "share": 0.5, "cleanse": 1, "zoneTicks": 100,
                         "flow": ["발밑 반지름 {radius}칸 성스러운 원 {t:zoneTicks}", "안의 나 · 아군 회복 (아군은 절반)", "처음에 나쁜 효과 {cleanse}개 제거"]},
         desc="발밑에 성스러운 원 — 안의 아군 회복 · 나쁜 기운을 걷음", use="장판 5초 — 나 {heal} · 아군 {ally} · 나쁜 효과 하나 제거"),
    # ---------------------------------------------------------------- 영웅 (초당 77%)
    dict(key="titanhammer", name="타이탄 해머", grade="hero", cd=37, pair=["warrior_spirit"], r="melee", src="str.hero", u=["leap", "stun1"],
         set={"range": 12}, desc="겨눈 곳으로 높이 뛰어 거대한 망치와 함께 내려찍음 · 기절", use="도약 · 기절 1초"),
    # 2026-11-17 룬 재블린은 새 동작 (RuneSet.javelin — 어깨 위 3D 빛의 창 · 창 던지기 동작, 옛 저격의 활 시위 · 석궁 소리가 설명 "던짐" 과 어긋났음 · CI 44046252216)
    dict(key="runejavelin", name="룬 재블린", grade="hero", cd=27.5, pair=["archer_burst"], r="far", src="bow.hero", u=[], a="single", cast=1.0,
         set={"fn": "javelin", "charge": 20, "len": 3.0, "push": 0}, spread={"t": 21, "w": 21}, desc="빛의 창을 만들어 던짐 — 처음 맞은 적에 꽂혀 터짐",
         use="원거리 단일 큰 한 방"),
    dict(key="shadowblitz", name="섀도 블리츠", grade="hero", cd=29.5, pair=["trickster_waltz"], r="mid", src="assassin.hero", u=["blink"], a="single",
         set={"vuln": 0, "vulnTicks": 0}, desc="대상 등 뒤로 순간 이동해 난도질", use="단일 연타 · 순간 이동"),
    dict(key="blizzard", name="블리자드", grade="hero", cd=34.5, pair=["frostraider_jotunn"], r="far", src="int.hero", elem="ice", u=["slow"], cast=0.8,
         desc="겨눈 곳 하늘에 구름을 모아 눈보라 · 둔화", use="장판 · 둔화"),
    dict(key="gnome", name="노움", grade="hero", cd=32.5, pair=["necromancer_blight"], r="mid", src="spirit.hero", elem="rock", u=["root"], a="narrow",
         desc="노움을 불러 바위 가시를 일으킴 · 묶음", use="소환 · 묶음 1초"),
    # 2026-11-18 코드 감사 (사용자 "정체성은 값, 덤은 줄임"): 0.5초마다 둘레 적을 밀쳐냄 0.7 (5초 — 방벽 밖으로 밀어냄) 이 표에 없던 유틸 → 밀쳐냄 값
    dict(key="bastion", name="바스티온", grade="hero", cd=32, pair=["guardian_bulwark"], r="melee", src="vit.hero", u=["allyGuard", "knock"], look="gladiator:hero",
         set={"reduce": 0.2, "ticks": 100, "pulse": 10}, spread={"t": 100, "zone": True},
         desc="방패 벽을 둘러 세워 아군을 지키고 안의 적을 침", use="장판 5초 · 아군 받는 피해 −20%"),
    # 2026-11-28 근접 딜 새 룬: 모탈 스트라이크 = 힘 (크게 젖혔다 내려찍는 한 방) · 짝 소드마스터 드래곤 블러드 27초
    # 2026-11-28 사거리 4.5 → 6칸 (영상 무대의 앞 과녁 5칸 — CI 46099514617 "film cast vanilla_mortalstrike_hero" 대상 없음): 하늘에서 내리꽂는 검이라 손 닿는 거리보다 넉넉히
    dict(key="mortalstrike", name="모탈 스트라이크", grade="hero", cd=27, pair=["swordmaster_blood"], r="melee", stat_fam="str", look="axe:hero", u=[], a="single",
         set={"fn": "mortalStrike", "mult": 14, "crush": 6.7692, "range": 6.0, "cone": 50, "wound": 10, "multName": "처형 일격 ", "crushName": "상처 터짐",
              "flow": ["앞 {range}칸의 적 하나에 처형의 검을 내리꽂음", "{t:wound} 뒤 깊은 상처가 터짐"]},
         spread={"t": 18, "w": 8}, desc="처형의 검을 한 적에게 내리꽂고 상처를 터뜨림", use="단일 큰 한 방 · 상처 터짐"),
    # 2026-11-28 지원: 발키리 송 = 발키리의 노래 · 나와 둘레 아군 가하는 피해 + · 받는 피해 − (받는 피해 감소는 다른 감소와 겹치지 않음 — 가호와 같음) · 짝 워록 애시본 37초
    dict(key="valkyriesong", name="발키리 송", grade="hero", cd=37, pair=["warlock_ash"], r="mid", stat_fam="vit", look="gladiator:hero", u=[], support=True,
         set={"fn": "valkyrieSong", "buffUp": 0.15, "buffCut": 0.15, "buffTicks": 200, "radius": 8, "descend": 12,
              "flow": ["발키리의 노래 — 나와 둘레 {radius}칸 아군 {t:buffTicks}", "받는 피해 감소는 다른 감소와 겹치지 않음"]},
         desc="발키리의 노래가 나와 둘레 아군에게 전장의 가호를 내림", use="가하는 피해 +15% · 받는 피해 −15% (10초)"),
    # ---------------------------------------------------------------- 초월 (초당 89%)
    # 2026-11-17 도감 메모 (천붕 → 스카이폴 "유틸 삭제 및 쿨타임 증가, 이에 따른 데미지 소량 증가"): 띄움을 뺌 (유틸 −5% 가 피해로 · 쿨 50초 = 예산 × 50/36)
    dict(key="skyfall", name="스카이폴", grade="transcend", cd=50, pair=["swordmaster_ult"], r="far", src="str.transcend", u=[], cast=1.0,
         set={"lift": 0}, desc="하늘 높이에서 거대한 검이 떨어져 꽂힘", use="원거리 광역 큰 한 방"),
    # 2026-11-28 쿨 32초 < 초월 기준 36초 → 지키는 유틸 세기 × 0.9 (받는 피해 −20 → −18% · 흡혈 2 → 1.8%)
    # 2026-11-28 사용자 메모 (룬 도감 판 15 — "도끼를 그냥 처음에 한번 크게 내려치고 그이후에는 버프만 받는걸로 지속딜 삭제 및 한방딜로 변경, 버프는 유지") ·
    # 흡혈 (사용자 고른 것 "광폭화 동안 모든 공격 흡혈"): 새 동작 rageSlam (옛 광전사의 혼 berserker 의 난타는 숨긴 옛 룬에 그대로) — windup 틱 치켜든 거대한
    # 도끼로 앞을 한 번 (예산 전부 — mult 32 · 선딜 0.65초 보정 +13%) → 그때부터 광폭화 ticks 틱: 받는 피해 −18% · 모든 공격 (평타 · 직업 스킬 · 룬) 준 피해의
    # leech 회복 (1초에 최대 체력 × heal × 회복 배율까지 — 옛 난타 흡혈의 한 적 몫 1초 1.8% 와 같은 상한). 유틸 값은 그대로 (흡혈 0.1 · 받는 피해 −18% 6초 × 0.9)
    dict(key="berserker", name="버서커", grade="transcend", cd=32, pair=["bloodknight_frenzy"], r="melee", src="axe.transcend", u=["lifesteal", "reduce"],
         set={"fn": "rageSlam", "mult": 32, "windup": 13, "dist": 2.8, "radius": 4.5, "ticks": 120, "reduce": 0.18, "heal": 0.018, "leech": 0.05,
              "flow": ["{t:windup} 치켜든 거대한 도끼로 앞 {radius}칸을 내려찍음", "그 뒤 광폭화 {t:ticks} (모든 공격 흡혈)"]},
         drop=["hits", "gap", "healMax"], spread={"t": 13, "w": 13, "zone": None}, guard_sec=6, fit={"lifesteal": 0.9, "reduce": 0.9},
         desc="핏빛 거대한 도끼로 크게 내려찍은 뒤 광폭화 · 모든 공격 흡혈", use="큰 한 방 · 광폭화 6초 (흡혈 · 받는 피해 −18%)"),
    # 2026-11-17 등급 안 밸런스: 달빛 폭발이 둘레 3칸 적도 침 (splashMult 16 — 대상의 절반) → 단일 (+15%) 이 아니라 좁은 범위 (+8% — π × 3² ≈ 28칸²)
    # 2026-11-18 코드 감사 (사용자 "램파트와 같게"): 표식 1초 (선딜) 동안 나는 투명 · 대상은 둔화 IV → 선딜이 위험이 아님 (램파트의 막는 선딜과 같음 — 선딜 보정 없음),
    # 투명 (은신) · 대상 둔화도 표에 없던 유틸 → 값
    dict(key="lunaexec", name="루나 엑시큐션", grade="transcend", cd=50, pair=["assassin_ult"], r="mid", src="assassin.transcend", u=["blink", "hide", "slow"], a="narrow",
         spread={"guard": True}, desc="달빛 표식 뒤 대상 등 뒤에 나타나 X 베기 · 달빛 폭발", use="대상 + 둘레 3칸 달빛 폭발 (절반) · 순간 이동"),
    # 2026-11-17 도감 메모 (메테오 "유틸 띄움 삭제 데미지 소량 증가 및 이에 따른 쿨타임 증가"): 띄움을 뺌 · 쿨 60초 (판 14 — 본 뒤 메모는 마법진 색만) →
    # 등급 범위 (초월 32 ~ 50초) 의 끝 50초 (긴 쿨 큰 한 방 그대로). 매지션의 범위 안 스킬 (프리즘 레이 35초) 은 스피릿 킹의 짝
    dict(key="meteor", name="메테오", grade="transcend", cd=50, r="far", src="int.transcend", elem="fire", u=[], cast=1.0,
         set={"lift": 0}, desc="각진 운석이 떨어져 폭발 · 불타는 땅", use="원거리 광역 큰 한 방"),
    dict(key="spiritking", name="스피릿 킹", grade="transcend", cd=35, pair=["mage_overload"], r="mid", src="spirit.transcend", elem="multi", u=["pull", "slow", "knock"],
         desc="네 정령이 차례로 공격한 뒤 정령왕이 강림", use="소환 · 원소"),
    # 2026-11-28 쿨 32초 < 초월 기준 36초 → 맹약 8 → 7초 (× 0.875 — 0.889 에 가까운 1초 단위 · 반격 · 회복 20틱마다 8 → 7번, 마지막 피해 140 → 120틱 ·
    # 돌파 10 → 9초 · 12 → 11초 — rune_break.json undyingPact)
    dict(key="immortaloath", name="이모탈 오스", grade="transcend", cd=32, pair=["priest_stigma", "saint_bond"], r="melee", src="vit.transcend", u=["undying", "heal"],
         set={"ticks": 140}, spread={"t": 120}, fit={"undying": 0.875, "heal": 0.875},
         desc="쓰러지지 않는 맹약 — 버티며 회복하고 둘레를 침", use="버팀 · 회복"),
    # 2026-11-28 근접 딜 새 룬: 그랜드 크로스 = 힘 (가로 · 세로 두 번 → 빛의 십자가) · 짝 다크 나이트 언홀리 프렌지 34.5초. 배율 합 32 (초월 돌파 선 32 · 39 · 50
    # — rune_break.json grandCross) × scale (34.5 ÷ 36)
    dict(key="grandcross", name="그랜드 크로스", grade="transcend", cd=34.5, pair=["darkknight_frenzy"], r="melee", stat_fam="str", look="holy:transcend", u=[],
         set={"fn": "grandCross", "mult": 8, "hits": 2, "crush": 16, "radius": 5.0, "length": 7.5, "width": 2.0, "at": 3.0, "multName": "베기 ",
              "crushName": "그랜드 크로스",
              "flow": ["가로로 앞 {radius}칸을 휩쓸고 세로로 내려침", "벤 자리에 빛의 십자가가 솟아 터짐"]},
         spread={"t": 24, "w": 1}, desc="가로 · 세로로 벤 자리에 빛의 십자가가 솟아 터짐", use="광역 2연타 · 십자 폭발"),
    # 2026-11-28 지원: 에덴 블룸 = 낙원의 꽃이 피어 둘레가 꽃밭 · 회복 (3초 나눠 — 그로브 블레싱처럼 몸을 따라감) + 가하는 피해 + (나 · 아군) · 짝 없음 (초월 범위의 직업 스킬
    # 중 어울리는 것 없음)
    dict(key="edenbloom", name="에덴 블룸", grade="transcend", cd=45, r="mid", stat_fam="vit", look="vit:transcend", u=[], support=True,
         set={"fn": "edenBloom", "buffUp": 0.18, "buffTicks": 240, "radius": 10, "pulses": 5, "gap": 15, "share": 0.5, "charge": 14,
              "flow": ["둘레 {radius}칸 꽃밭 — 나 · 아군 회복 (아군은 절반)", "나와 아군 가하는 피해 {t:buffTicks}"]},
         desc="낙원의 꽃이 피어 둘레가 꽃밭으로 — 회복 · 아군을 북돋움", use="나 {heal} · 아군 {ally} 회복 · 가하는 피해 +18% 12초"),
]


# 옛 룬 → 새 룬 (컨셉 문서 「옛 룬 → 새 룬」 22 — 출처가 곧 옛 룬). 나머지 옛 계열 9 의 룬은 접속 때 룬 조각 (옛 등급의 갈기 값)
CARRIED = {"earthbreaker", "boomerangaxe", "galerush", "salamander", "chainarrow", "shadowstrike", "undine", "fissureslam", "thundervolley", "frostburst",
           "firestorm", "titanhammer", "shadowblitz", "blizzard", "gnome", "bastion", "skyfall", "berserker", "lunaexec", "meteor", "spiritking", "immortaloath"}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def merged_runes():
    r = load_json(os.path.join(RES, "rules.json"))
    m = load_json(os.path.join(RES, "rules_more.json"))
    runes = {k: dict(v) for k, v in r["RUNES"].items() if isinstance(v, dict)}
    for k, v in m["RUNES"].items():
        if k.startswith("_") or not isinstance(v, dict):
            continue
        runes[k] = v  # rules_more 는 계열을 통째로 바꿈 (Rules.merge 와 같음)
    return r, m, runes


def total_mult(d):
    """Runes.runeTotalMult 와 같은 셈"""
    fn = d.get("fn")
    if not d.get("mult"):
        return 0.0
    mult = d["mult"]
    if fn in ("bulwark", "undyingPact"):
        return mult * math.floor(d.get("ticks", float("nan")) / d.get("pulse", float("nan")))
    if fn == "spiritKing":
        return mult * d.get("hits", 1) + d.get("elemMult", 0) * 4 + d.get("burstMult", 0)
    return mult * d.get("hits", 1) + d.get("dot", 0) * d.get("dots", 0) + d.get("crush", 0)


def time_mult(sp, more, cast):
    """Runes.timeMult — 원거리 시전 룬은 선딜 보정 없음"""
    S = more["RUNE_SPREAD"]
    if not sp or "t" not in sp:
        return 1.0
    w = sp.get("w", 0)
    wt = w if w >= S["windup"]["min"] else 0
    wb = 0 if cast or sp.get("guard") else min(S["windup"]["max"], wt * S["windup"]["perTick"])
    d = max(0, sp["t"] - wt)
    if sp.get("zone"):
        kind = "zoneLong" if d > S["zoneLong"] else "zone"
    elif d <= S["instant"]:
        kind = None
    else:
        kind = "long" if d > S["short"] else "short"
    sb = 0 if kind is None else S["bonus"][kind]
    return min(S["cap"], (1 + wb) * (1 + sb))


def grade_cap(g):
    """Runes.gradeCap — 2026-11-28 보정 곱 상한 = 다음 등급 초당 값 ÷ 이 등급 초당 값 (초월은 다섯째부터 한정 룬 기준 LIMITED_BASE — 34 ÷ 36)"""
    i = GRADES.index(g)
    per = lambda x: GRADE_BASE[x][0] / GRADE_BASE[x][1]
    nxt = per(GRADES[i + 1]) if i + 1 < len(GRADES) else LIMITED_BASE[0] / LIMITED_BASE[1]
    return nxt / per(g)


def support_split(e, d, budget, S):
    """2026-11-28 지원 룬 (SUPPORT_* 주석): 버프 · 정화 · 회복의 예산 몫. 회복하는 룬 (둘레 아군 몫 share 가 있음) 은 남는 예산이 모두 회복 (heal 을 씀 —
    그로브 블레싱처럼 정의에 있으면 그 값이 맞는지 봄), 회복 없는 룬은 버프가 예산과 맞는지 봄. 돌려줌: {guard: 지키는 몫 (회복 · 받는 피해 − · 정화), boost: 북돋는 몫}"""
    sec = d.get("buffTicks", 0) / 20.0
    up = d.get("buffUp", 0) * 100 * sec * SUPPORT_BUFF
    cut = d.get("buffCut", 0) * 100 * sec * SUPPORT_BUFF
    haste = d.get("haste", 0) * 100 * sec * SUPPORT_HASTE
    move = (d["speedAmp"] + 1) * 20 * sec * SUPPORT_MOVE if "speedAmp" in d else 0
    cleanse = d.get("cleanse", 0) * S["utils"]["cleanse"]["cost"] * budget
    spent = up + cut + haste + move + cleanse
    heal_units = 0
    if "share" in d:
        zone = 1.0
        if e.get("zone"):
            t = (d["pulses"] - 1) * d["gap"]
            zone = 1 + S["bonus"]["zoneLong" if t > S["zoneLong"] else "zone"]
        heal_units = budget - spent
        if heal_units <= 0:
            raise SystemExit("지원 룬의 버프가 예산을 다 씀 (회복 몫 없음): " + e["name"])
        want = heal_units * SUPPORT_HEAL * zone
        if "heal" in d:
            if abs(d["heal"] / want - 1) > SUPPORT_TOL:
                raise SystemExit("지원 룬 회복이 예산과 다름: {} {} ↔ {:.4f}".format(e["name"], d["heal"], want))
        else:
            d["heal"] = round(want, 4)
    elif abs(spent / budget - 1) > SUPPORT_TOL:
        raise SystemExit("지원 룬 버프가 예산과 다름: {} {:.2f} ↔ 예산 {:.2f}".format(e["name"], spent, budget))
    return {"guard": round((heal_units + cut + cleanse) / budget, 3), "boost": round((up + haste + move) / budget, 3)}


def support_text(t, d):
    """설명 글의 {heal} · {ally} = 기준 회복 배율 (SUPPORT_REF_SCALE) 의 회복 % (쓴 사람 · 둘레 아군)"""
    if "{heal}" not in t:
        return t
    pct = lambda f: "{:.0f}%".format(f * SUPPORT_REF_SCALE * 100)
    return t.replace("{heal}", pct(d["heal"])).replace("{ally}", pct(d["heal"] * d.get("share", 0)))


def job_skills():
    """jobs.json 의 직업 스킬 id → (이름, 쿨) — 1차 · 2차 모두"""
    j = load_json(os.path.join(RES, "jobs.json"))
    out = {}
    for job in list(j["jobs"].values()) + [v for k, v in j["advance"]["jobs"].items() if not k.startswith("_")]:
        for k in job["skills"]:
            out[k["id"]] = (k["name"], k["cd"])
    return out


def build():
    rules, more, runes = merged_runes()
    skills = job_skills()
    pairs = {}
    used = {}
    S = more["RUNE_SPREAD"]
    src_fams = dict(rules["RUNE_FAMILIES"])
    src_fams.update({k: v for k, v in more.get("RUNE_FAMILIES", {}).items() if not k.startswith("_")})
    fams, defs, spread, rows, migrate = {}, {}, {}, [], {}
    order = []
    for g in GRADES:
        for e in [x for x in SET if x["grade"] == g]:
            if e.get("src"):
                fam, grade = e["src"].split(".")
                src = dict(runes[fam][grade])
            else:
                # 2026-11-28 새 룬 (근접 딜 다섯 — 옛 동작을 빌리지 않음): 정의는 set 만 · 피해 종류 (공격 / 마법) 는 stat_fam 계열 · 그림 묶음은 look
                if not e.get("look") or not e.get("stat_fam"):
                    raise SystemExit("새 룬은 look · stat_fam 이 있어야 함: " + e["name"])
                fam, grade = e["stat_fam"], g
                src = {}
            d = dict(src)
            d.update(e.get("set", {}))
            # 2026-11-28 옛 동작에서 이어받은 값 중 새 동작에 없는 것 (버서커 rageSlam — 난타 hits · gap · 흡혈 명수 healMax)
            for k in e.get("drop", []):
                if k not in d:
                    raise SystemExit("drop 할 값이 없음: {} {}".format(e["name"], k))
                d.pop(k)
            d["name"] = e["name"]
            d["desc"] = e["desc"]
            d["use"] = e["use"]
            d["cd"] = e["cd"]
            d["look"] = e.get("look", fam + ":" + grade)
            budget_pct, base_cd = GRADE_BASE[g]
            budget = budget_pct / base_cd * e["cd"] / 100.0  # 배율 합 (예: 3.5 = 350%)
            old = total_mult(d)
            if not e.get("support") and old > 0:
                d["scale"] = round(budget / old, 4)
            sup = None
            if e.get("support"):
                if old > 0:
                    raise SystemExit("지원 룬에 피해가 있음: " + e["name"])
                sup = support_split(e, d, budget, S)
                d["use"] = support_text(d["use"], d)
            sp = dict(S["runes"].get(e["src"]) or {}) if e.get("src") else {}
            sp.update(e.get("spread", {}))
            for k in [k for k, v in sp.items() if v is None]:
                sp.pop(k)  # 2026-11-28 옛 표의 값을 뺌 (None — 버서커: 장판 zone)
            sp["u"] = list(e["u"])
            if e.get("a"):
                sp["a"] = e["a"]
            else:
                sp.pop("a", None)
            if e.get("elem"):
                sp["elem"] = e["elem"]
            else:
                sp.pop("elem", None)
            sp["r"] = e["r"]
            if sup is not None:
                sp["sup"] = sup
            else:
                sp.pop("sup", None)
            if e.get("cast"):
                # 시전: 동작 안의 선딜 (w) 이 시전보다 짧으면 그만큼 먼저 기다림 (castAdd 틱 — 느리게 걷기), 시전 = 둘 중 긴 쪽
                w = int(sp.get("w", 0))
                ct = max(int(round(e["cast"] * 20)), w)
                sp["cast"] = ct / 20.0
                sp["castAdd"] = ct - w
            # 2026-11-18 받는 피해 감소 = 세기 × 막는 시간 비례 (사용자 "비례로 다시 매김" — 기준 −30% · 2초 = 표의 값 0.1)
            ucost = {}
            if "reduce" in e["u"]:
                sec = e.get("guard_sec")
                if not sec or not d.get("reduce"):
                    raise SystemExit("받는 피해 감소의 세기 · 시간이 없음: " + e["name"])
                ticks = d.get("ticks") or d.get("hits", 0) * d.get("gap", 0)
                if abs(ticks / 20.0 - sec) > 1e-9:
                    raise SystemExit("막는 시간이 정의와 다름: {} {}초 ↔ {}틱".format(e["name"], sec, ticks))
                ucost["reduce"] = round(S["utils"]["reduce"]["cost"] * d["reduce"] * sec / REDUCE_REF, 4)
            # 2026-11-28 지키는 유틸 · 쿨 (사용자 "너가 말한대로 하자"): 쿨이 등급 기준보다 짧으면 지속 · 세기를 쿨 ÷ 기준 만큼 줄였는지 (fit) 보고, 그 유틸 값 = 표의 값 × 비율
            # (받는 피해 감소는 줄인 세기 · 시간으로 위에서 셈)
            guard = sorted(GUARD_UTILS & set(e["u"]))
            fit = e.get("fit", {})
            if guard and e["cd"] < base_cd:
                want = e["cd"] / base_cd
                for u in guard:
                    if u not in fit or abs(fit[u] - want) > FIT_TOL:
                        raise SystemExit("쿨이 등급 기준보다 짧은 룬의 지키는 유틸을 줄이지 않음: {} {} (쿨 {} ÷ 기준 {} = {:.3f}, fit {})".format(
                            e["name"], u, e["cd"], base_cd, want, fit.get(u)))
            elif fit:
                raise SystemExit("쿨이 기준보다 짧지 않은데 fit: " + e["name"])
            for u, k in fit.items():
                if u not in e["u"]:
                    raise SystemExit("fit 의 유틸이 룬에 없음: {} {}".format(e["name"], u))
                if u != "reduce":
                    ucost[u] = round(S["utils"][u]["cost"] * k, 4)
            if ucost:
                sp["uCost"] = ucost
            else:
                sp.pop("uCost", None)
            key = e["key"]
            lo, hi = GRADE_CD[g]
            if not lo <= e["cd"] <= hi:
                raise SystemExit("재사용이 등급 범위 밖: {} {}초 ({} {} ~ {}초)".format(e["name"], e["cd"], GRADE_KO[g], lo, hi))
            for sid in e.get("pair", []):
                if sid not in skills or abs(skills[sid][1] - e["cd"]) > 1e-9:
                    raise SystemExit("짝 쿨이 다름: {} {}초 ↔ {} {}".format(e["name"], e["cd"], sid, skills.get(sid)))
                if sid in used:
                    raise SystemExit("직업 스킬 하나에 룬 둘: {} ↔ {} · {}".format(sid, used[sid], e["name"]))
                used[sid] = e["name"]
            if e.get("pair"):
                pairs[key] = list(e["pair"])
            # stat = 피해 종류 (마력 = 마법 · 나머지 = 공격 — RuneKit.runeHit) · 출처 계열의 것 (룬 위력은 네 스탯의 합 — coef 모두 1)
            fams[key] = {"stat": src_fams[fam]["stat"], "name": e["name"], "coef": {"str": 1, "dex": 1, "vit": 1, "int": 1}, "sum": True, "set": True}
            if key in CARRIED:
                migrate[e["src"]] = key + "." + g
            defs[key] = {g: d}
            spread[key + "." + g] = sp
            order.append(key)
            # 실효 (표 — 원거리 룬은 시전에 선딜 보정 없음)
            util = min(S["utilMax"], sum(sp.get("uCost", {}).get(u, S["utils"][u]["cost"]) for u in e["u"]) * base_cd / e["cd"])
            area = 1 + (S["area"][e["a"]]["bonus"] if e.get("a") else 0)
            el = 0.95 if e.get("elem") else 1.0
            tm = time_mult(sp, more, e.get("cast"))
            mods = RANGE[e["r"]] * el * (1 - util) * tm * area
            cap = grade_cap(g)
            eff = 0 if e.get("support") else budget * 100 * min(mods, cap)
            rows.append(dict(grade=g, key=key, name=e["name"], cd=e["cd"], r=e["r"], elem=e.get("elem"), u=[S["utils"][u]["name"] for u in e["u"]], sup=sup,
                             budget=budget * 100, tm=tm, area=area, eff=eff, cast=sp.get("cast"), look=d["look"],
                             pair=[skills[x][0] for x in e.get("pair", [])], kind="필러" if e["cd"] <= 13 else "중간" if e["cd"] <= 25 else "딜타임"))
    hidden = list(more.get("RUNE_HIDDEN", []))
    for f in OLD_VISIBLE:
        if f not in hidden:
            hidden.insert(0, f)
    out = {
        "_comment": "2026-11-17 룬 재개발 (tools/rune_set.py 가 씀 — 손으로 고치지 말 것). 계열 없이 등급만 (등급마다 6 = 30 → 2026-11-28 근접 딜 · 지원 다섯씩 더해 8 = 40 — 지원 룬은 피해 없이 예산을 회복 · 버프로: RUNE_SET.spread 의 sup) · 룬마다 재사용 (등급 범위 안 — 일반 6 ~ 15 · 희귀 12 ~ 21 · 고귀 15 ~ 25 · 영웅 27 ~ 38 · 초월 32 ~ 50초, 예산 = 등급 초당 값 × 재사용) · "
                    "룬 위력 = 네 스탯의 합 · 거리 보정 (RUNE_SET.range) · 원거리 시전 (cast — 선딜 보정 없음) · 받는 피해 증가 없음 (한정 룬만). 옛 계열 9 는 숨김 (아이템은 접속 때 새 룬으로).",
        "RUNE_FAMILIES": fams,
        "RUNES": defs,
        "RUNE_FAMILY_ORDER": order,
        "RUNE_HIDDEN": hidden,
        "RUNE": {"ticketLore": ["§r§7보스·퀘스트·룬술사에게서 얻음", "§r§7들고 누르면 룬 하나를 뽑습니다", "§r§7일반 50% · 희귀 28% · 고귀 14%",
                                "§r§7영웅 6.5% · 초월 1.5%", "§r§7등급마다 룬 8개 중 하나 (같은 확률)"]},
        "RUNE_SET": {
            "range": RANGE,
            "rangeName": {"melee": "근거리", "mid": "중거리", "far": "원거리"},
            "cdRange": {g: list(GRADE_CD[g]) for g in GRADES},
            "_baseCd": "2026-11-17 유틸 값의 기준 재사용 (등급 · 한정 — Runes.utilCdFactor: 유틸 값 × 기준 ÷ 룬의 재사용)",
            "baseCd": dict({g: GRADE_BASE[g][1] for g in GRADES}, limited=36),
            "_budget": "2026-11-28 등급의 배율 합 (기준 재사용 baseCd 의 예산 — 초당 값 = budget ÷ baseCd). 등급 상한 (사용자 \"다음 등급을 못 넘게 상한\" — Runes.gradeCap): "
                       "보정 곱 (유틸 · 시간 · 범위 · 원소 · 거리) ≤ 다음 등급 초당 값 ÷ 이 등급 초당 값 → 0돌 피해/초가 다음 등급 기준을 넘지 않음 (초월의 다음 = 한정 기준 limited — 2026-11-28 다섯째, 한정은 없음)",
            "budget": dict({g: GRADE_BASE[g][0] / 100 for g in GRADES}, limited=LIMITED_BASE[0] / 100),
            "spread": spread,
            "migrateFamilies": OLD_VISIBLE,
            "migrate": migrate,
            "_pairs": "2026-11-17 룬 → 같은 쿨의 어울리는 직업 스킬 id (사용자 \"검사면 검사랑 어울리는 룬 스킬이 있으면 해당하는 두 스킬에 쿨이 같게\" — 등급 범위 안의 스킬만 · 직업 스킬 하나에 룬 하나) — 게임에는 안 보임 (룬에 직업 글자 없음), RuneScaleTest 가 쿨이 같은지 봄",
            "pairs": pairs,
        },
    }
    return out, rows


def support_label(x):
    """표의 실효 칸 — 지원 룬: 지키는 몫 · 북돋는 몫"""
    sup = x.get("sup") or {}
    parts = []
    if sup.get("guard"):
        parts.append("지킴 {:.0f}%".format(sup["guard"] * 100))
    if sup.get("boost"):
        parts.append("북돋움 {:.0f}%".format(sup["boost"] * 100))
    return "지원 (" + " · ".join(parts) + ")" if parts else "지원"


def md(rows):
    lines = ["| 등급 | 룬 | 쓰임 | 재사용 | 같은 쿨 직업 스킬 | 거리 | 원소 | 유틸 | 예산 | 시간 · 범위 | 실효 |", "|---|---|---|---|---|---|---|---|---|---|---|"]
    for x in rows:
        tb = "×" + format(x["tm"], ".2f") + (" · 단일 +15%" if x["area"] > 1.1 else " · 좁은 +8%" if x["area"] > 1.01 else "")
        lines.append("| {} | {} | {} | {}초 | {} | {}{} | {} | {} | {:.0f}% | {} | {} |".format(
            GRADE_KO[x["grade"]], x["name"], x["kind"], x["cd"], " · ".join(x["pair"]) or "—", RANGE_KO[x["r"]], " · 시전 " + str(x["cast"]) + "초" if x["cast"] else "",
            ELEM_KO.get(x["elem"], "—"), " · ".join(x["u"]) or "—", x["budget"], tb, support_label(x) if x["eff"] == 0 else "{:.0f}%".format(x["eff"])))
    return "\n".join(lines)


def main():
    out, rows = build()
    if "--md" in sys.argv:
        print(md(rows))
        return
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    for g in GRADES:
        rs = [x for x in rows if x["grade"] == g]
        cds = [x["cd"] for x in rs]
        print(GRADE_KO[g], "재사용 평균", sum(cds) / len(cds), "초당 실효", " · ".join("{} {:.0f}".format(x["name"], x["eff"] / x["cd"]) for x in rs if x["eff"]))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
