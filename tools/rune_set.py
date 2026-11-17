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
# 2026-11-17 등급마다 재사용 범위 (초 — 사용자가 정함, 양 끝 포함)
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
         set={"fn": "rampart", "ticks": 60, "reduce": 0.5, "knock": 0.9}, spread={"t": 60, "w": 60, "guard": True},
         desc="빛의 방패 결계로 3초 버티고 앞으로 밀어 냄", use="받는 피해 −50% 3초 · 끝에 앞을 밀쳐냄"),
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
         set={"range": 6, "bison": 1}, desc="들소 영혼과 함께 앞으로 돌진해 부딪힌 적을 밀쳐냄", use="돌진 · 밀쳐냄 · 받는 피해 −30%"),
    dict(key="grove", name="그로브 블레싱", grade="rare", cd=15, pair=["priest_hymn"], r="mid", src="vit.rare", u=[], support=True,
         # 2026-11-17 쿨 6 → 15초: 회복도 초당 그대로 (0.0667 × 15 ÷ 6)
         set={"heal": 0.1667, "pulses": 4, "gap": 15, "radius": 5, "share": 0.5}, desc="발밑에 새싹이 자라 빛 잎을 흩날리며 나와 둘레 아군을 회복",
         use="회복 — 나 37% · 아군 19% (3초)"),
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
    dict(key="bastion", name="바스티온", grade="hero", cd=32, pair=["guardian_bulwark"], r="melee", src="vit.hero", u=["allyGuard"], look="gladiator:hero",
         set={"reduce": 0.2, "ticks": 100, "pulse": 10}, spread={"t": 100, "zone": True},
         desc="방패 벽을 둘러 세워 아군을 지키고 안의 적을 침", use="장판 5초 · 아군 받는 피해 −20%"),
    # ---------------------------------------------------------------- 초월 (초당 89%)
    # 2026-11-17 도감 메모 (천붕 → 스카이폴 "유틸 삭제 및 쿨타임 증가, 이에 따른 데미지 소량 증가"): 띄움을 뺌 (유틸 −5% 가 피해로 · 쿨 50초 = 예산 × 50/36)
    dict(key="skyfall", name="스카이폴", grade="transcend", cd=50, pair=["swordmaster_ult"], r="far", src="str.transcend", u=[], cast=1.0,
         set={"lift": 0}, desc="하늘 높이에서 거대한 검이 떨어져 꽂힘", use="원거리 광역 큰 한 방"),
    dict(key="berserker", name="버서커", grade="transcend", cd=32, pair=["bloodknight_frenzy"], r="melee", src="axe.transcend", u=["lifesteal", "reduce"],
         set={"reduce": 0.2}, desc="광폭화 — 핏빛 거대한 룬 도끼가 둘레를 거듭 내려찍음 · 흡혈", use="둘레 연타 · 흡혈 · 받는 피해 −20%"),
    # 2026-11-17 등급 안 밸런스: 달빛 폭발이 둘레 3칸 적도 침 (splashMult 16 — 대상의 절반) → 단일 (+15%) 이 아니라 좁은 범위 (+8% — π × 3² ≈ 28칸²)
    dict(key="lunaexec", name="루나 엑시큐션", grade="transcend", cd=50, pair=["assassin_ult"], r="mid", src="assassin.transcend", u=["blink"], a="narrow",
         desc="달빛 표식 뒤 대상 등 뒤에 나타나 X 베기 · 달빛 폭발", use="대상 + 둘레 3칸 달빛 폭발 (절반) · 순간 이동"),
    # 2026-11-17 도감 메모 (메테오 "유틸 띄움 삭제 데미지 소량 증가 및 이에 따른 쿨타임 증가"): 띄움을 뺌 · 쿨 60초 (판 14 — 본 뒤 메모는 마법진 색만) →
    # 등급 범위 (초월 32 ~ 50초) 의 끝 50초 (긴 쿨 큰 한 방 그대로). 매지션의 범위 안 스킬 (프리즘 레이 35초) 은 스피릿 킹의 짝
    dict(key="meteor", name="메테오", grade="transcend", cd=50, r="far", src="int.transcend", elem="fire", u=[], cast=1.0,
         set={"lift": 0}, desc="각진 운석이 떨어져 폭발 · 불타는 땅", use="원거리 광역 큰 한 방"),
    dict(key="spiritking", name="스피릿 킹", grade="transcend", cd=35, pair=["mage_overload"], r="mid", src="spirit.transcend", elem="multi", u=["pull", "slow", "knock"],
         desc="네 정령이 차례로 공격한 뒤 정령왕이 강림", use="소환 · 원소"),
    dict(key="immortaloath", name="이모탈 오스", grade="transcend", cd=32, pair=["priest_stigma", "saint_bond"], r="melee", src="vit.transcend", u=["undying", "heal"],
         desc="쓰러지지 않는 맹약 — 버티며 회복하고 둘레를 침", use="버팀 · 회복"),
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
            fam, grade = e["src"].split(".")
            src = dict(runes[fam][grade])
            d = dict(src)
            d.update(e.get("set", {}))
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
            sp = dict(S["runes"].get(e["src"]) or {})
            sp.update(e.get("spread", {}))
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
            if e.get("cast"):
                # 시전: 동작 안의 선딜 (w) 이 시전보다 짧으면 그만큼 먼저 기다림 (castAdd 틱 — 느리게 걷기), 시전 = 둘 중 긴 쪽
                w = int(sp.get("w", 0))
                ct = max(int(round(e["cast"] * 20)), w)
                sp["cast"] = ct / 20.0
                sp["castAdd"] = ct - w
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
            util = min(S["utilMax"], sum(S["utils"][u]["cost"] for u in e["u"]) * base_cd / e["cd"])
            area = 1 + (S["area"][e["a"]]["bonus"] if e.get("a") else 0)
            el = 0.95 if e.get("elem") else 1.0
            tm = time_mult(sp, more, e.get("cast"))
            eff = 0 if e.get("support") else budget * 100 * RANGE[e["r"]] * el * (1 - util) * tm * area
            rows.append(dict(grade=g, key=key, name=e["name"], cd=e["cd"], r=e["r"], elem=e.get("elem"), u=[S["utils"][u]["name"] for u in e["u"]],
                             budget=budget * 100, tm=tm, area=area, eff=eff, cast=sp.get("cast"), look=d["look"],
                             pair=[skills[x][0] for x in e.get("pair", [])], kind="필러" if e["cd"] <= 13 else "중간" if e["cd"] <= 25 else "딜타임"))
    hidden = list(more.get("RUNE_HIDDEN", []))
    for f in OLD_VISIBLE:
        if f not in hidden:
            hidden.insert(0, f)
    out = {
        "_comment": "2026-11-17 룬 재개발 (tools/rune_set.py 가 씀 — 손으로 고치지 말 것). 계열 없이 등급만 (등급마다 6 = 30) · 룬마다 재사용 (등급 범위 안 — 일반 6 ~ 15 · 희귀 12 ~ 21 · 고귀 15 ~ 25 · 영웅 27 ~ 38 · 초월 32 ~ 50초, 예산 = 등급 초당 값 × 재사용) · "
                    "룬 위력 = 네 스탯의 합 · 거리 보정 (RUNE_SET.range) · 원거리 시전 (cast — 선딜 보정 없음) · 받는 피해 증가 없음 (한정 룬만). 옛 계열 9 는 숨김 (아이템은 접속 때 새 룬으로).",
        "RUNE_FAMILIES": fams,
        "RUNES": defs,
        "RUNE_FAMILY_ORDER": order,
        "RUNE_HIDDEN": hidden,
        "RUNE": {"ticketLore": ["§r§7보스·퀘스트·룬술사에게서 얻음", "§r§7들고 누르면 룬 하나를 뽑습니다", "§r§7일반 50% · 희귀 28% · 고귀 14%",
                                "§r§7영웅 6.5% · 초월 1.5%", "§r§7등급마다 룬 6개 중 하나 (같은 확률)"]},
        "RUNE_SET": {
            "range": RANGE,
            "rangeName": {"melee": "근거리", "mid": "중거리", "far": "원거리"},
            "cdRange": {g: list(GRADE_CD[g]) for g in GRADES},
            "_baseCd": "2026-11-17 유틸 값의 기준 재사용 (등급 · 한정 — Runes.utilCdFactor: 유틸 값 × 기준 ÷ 룬의 재사용)",
            "baseCd": dict({g: GRADE_BASE[g][1] for g in GRADES}, limited=36),
            "spread": spread,
            "migrateFamilies": OLD_VISIBLE,
            "migrate": migrate,
            "_pairs": "2026-11-17 룬 → 같은 쿨의 어울리는 직업 스킬 id (사용자 \"검사면 검사랑 어울리는 룬 스킬이 있으면 해당하는 두 스킬에 쿨이 같게\" — 등급 범위 안의 스킬만 · 직업 스킬 하나에 룬 하나) — 게임에는 안 보임 (룬에 직업 글자 없음), RuneScaleTest 가 쿨이 같은지 봄",
            "pairs": pairs,
        },
    }
    return out, rows


def md(rows):
    lines = ["| 등급 | 룬 | 쓰임 | 재사용 | 같은 쿨 직업 스킬 | 거리 | 원소 | 유틸 | 예산 | 시간 · 범위 | 실효 |", "|---|---|---|---|---|---|---|---|---|---|---|"]
    for x in rows:
        tb = "×" + format(x["tm"], ".2f") + (" · 단일 +15%" if x["area"] > 1.1 else " · 좁은 +8%" if x["area"] > 1.01 else "")
        lines.append("| {} | {} | {} | {}초 | {} | {}{} | {} | {} | {:.0f}% | {} | {} |".format(
            GRADE_KO[x["grade"]], x["name"], x["kind"], x["cd"], " · ".join(x["pair"]) or "—", RANGE_KO[x["r"]], " · 시전 " + str(x["cast"]) + "초" if x["cast"] else "",
            ELEM_KO.get(x["elem"], "—"), " · ".join(x["u"]) or "—", x["budget"], tb, "회복" if x["eff"] == 0 else "{:.0f}%".format(x["eff"])))
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
