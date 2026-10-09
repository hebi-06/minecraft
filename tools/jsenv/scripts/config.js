// =====================================================================
//  MMO RPG 설정 파일
//  숫자를 바꾸면 밸런스가 바뀝니다. 아이템 ID 규칙(rpg:종류_l레벨_등급)은 바꾸지 마세요.
// =====================================================================

export const CONFIG = {
  // ---- 체력 ----
  // 체력은 바닐라와 같은 단위 (하트 1칸 = 2). 철골렘 한 방 7.5~21.5 가 그대로 깎인다.
  // 최대 체력 = BASE_HP + HP_PER_LEVEL × 경험치 레벨(최고 기록) + HP_PER_VIT × 체력 스탯
  BASE_HP: 20, // 레벨 0, 체력 스탯 0일 때 (바닐라 기본 체력과 같음)
  HP_PER_LEVEL: 2, // 경험치 레벨(최고 기록) 1당 +2 → 20렙 60, 40렙 100
  HP_PER_VIT: 1, // 체력 스탯 1당 +1 (하트 반 칸)
  // v2.6.3 배고픔 없음: 배고픔·포화 수치를 늘 가득 채움 (화면의 배고픔 칸은 리소스 팩이 숨김). 포화 효과를 쓰지 않아 효과 아이콘이 남지 않음.
  // 베드락의 자연 회복은 배고픔 18 이상이면 4초에 1 (포화가 있어도 더 빠르지 않음) — 늘 배부른 것과 같음. false 면 바닐라 배고픔
  NO_HUNGER: true,
  DAMAGE_TAKEN_SCALE: 1, // 받은 바닐라 피해 × 이 값 = 깎이는 체력 (1 = 바닐라 그대로)
  // ---- 스탯 ----
  START_POINTS: 5, // 처음 접속했을 때 받는 스탯 포인트
  POINTS_PER_LEVEL: 3, // 경험치 레벨이 처음 오를 때마다 받는 포인트
  // 스탯 1포인트당 무기 피해 증가는 아래 STAT_INFO 의 perPoint (힘·마력 2%, 민첩 1%)
  // ---- 요구 스탯 · 요구 레벨 ----
  REQUIREMENT_ENABLED: true, // false 로 바꾸면 요구 스탯을 검사하지 않음
  // v2.6.2 무기 렙제: [N렙] 무기는 최고 경험치 레벨(rpg_lvmax, 죽어서 내려가도 그대로)이 N 이상이어야 씀 (요구 스탯과 함께).
  // false 로 바꾸면 v2.6.1 까지처럼 요구 스탯만 봄 (무기만 — 갑옷의 레벨 조건은 이 설정과 상관없이 늘 있음)
  WEAPON_LEVEL_REQUIRED: true,
  // ---- 연출 ----
  DAMAGE_TEXT: true, // 적 머리 위에 피해 숫자 띄우기
  NPC_EFFECTS: true, // RPG NPC 머리 위 파티클과 접근 안내
  // ---- 상점 ----
  CURRENCY_ITEM: "minecraft:emerald",
  CURRENCY_NAME: "에메랄드",
  CURRENCY_ICON: "textures/items/emerald",
};

// ---- 스탯 (창에 표시되는 순서) ----
// 역할: 힘·마력 = 깡딜 (1포인트 피해 +2%), 민첩 = 치명타 무기(단검·활)용 (1포인트 피해 +1%),
//       체력 = 최대 체력 (+ 도끼 피해 조금, TYPES.axe 의 bonus)
// perPoint: 1포인트당 무기 피해 증가 비율. 치명타는 스탯이 아니라 무기에서만 나온다 (TYPES 의 crit 값).
export const STAT_KEYS = ["str", "dex", "int", "vit"];
export const STAT_INFO = {
  str: { name: "힘", color: "§c", dark: "§4", icon: "textures/rpg_ui/icon_str", objective: "rpg_str", effect: "칼·도끼·마검 피해", weapons: "칼·도끼·마검", perPoint: 0.02 },
  dex: { name: "민첩", color: "§a", dark: "§2", icon: "textures/rpg_ui/icon_dex", objective: "rpg_dex", effect: "단검·활 피해", weapons: "단검·활", perPoint: 0.01 },
  int: { name: "마력", color: "§9", dark: "§1", icon: "textures/rpg_ui/icon_int", objective: "rpg_int", effect: "완드·마검 피해", weapons: "완드·마검", perPoint: 0.02 },
  vit: { name: "체력", color: "§d", dark: "§5", icon: "textures/rpg_ui/icon_vit", objective: "rpg_vit", effect: "최대 체력·방패·도끼", weapons: "방패·도끼", perPoint: 0 },
};

/** 스탯 1포인트당 무기 피해 증가 비율 */
export function damagePerPoint(stat) {
  return STAT_INFO[stat]?.perPoint ?? 0;
}
export const OBJ_POINT = "rpg_point";
export const OBJ_LVMAX = "rpg_lvmax";

// ---- 무기 ----
// 일반·희귀·고귀 무기의 레벨 (상점·퀘스트 보상의 기본 목록)
export const LEVELS = [0, 5, 10, 15, 20, 30, 40];
// v1.1.9: 등급마다 있는 레벨. 영웅은 30렙제로 나오고 대장장이가 5렙씩 올린다(35, 40). 초월은 40렙제
export const GRADE_LEVELS = { common: LEVELS, rare: LEVELS, noble: LEVELS, hero: [30, 35, 40], transcend: [40] };
// 아이템이 있는 모든 레벨 (이전 버전의 영웅 0~20렙·초월 0~30렙 아이템도 없어지지 않게 남아 있음)
export const ALL_LEVELS = [0, 5, 10, 15, 20, 30, 35, 40];

/**
 * v2.16.0 종류 6 × 등급 5 표 밖의 무기 (사용자 요청 "마검사 무기로 티르빙 — 따로 길을 안 만들고 제작에만"): 종류 → {등급: [레벨]}.
 * 그 조합만 아이템이 있음 (구버전 조합도 없음). TYPE_ORDER 에 넣지 않으므로 상점 · 퀘스트 보상 · 무기 시험 · 무기의 길 · /weapon 에 나오지 않고
 * 대장장이 보스 무기 제작 (BOSS_CRAFT) 으로만 얻음. generator/build.py 의 EXTRA_WEAPONS 와 같게
 * @type {Record<string, Record<string, number[]>>}
 */
export const TYPE_ONLY = { greatsword: { transcend: [40] } };

/**
 * 지금 규칙에 있는 무기 조합인지 (등급별 레벨, v2.16.0 표 밖의 종류는 TYPE_ONLY 의 조합만)
 * @param {number} level
 * @param {string} gradeId
 * @param {string} [type] 무기 종류 (없으면 등급 표만 봄)
 */
export function isValidWeapon(level, gradeId, type = undefined) {
  const only = type ? TYPE_ONLY[type] : undefined;
  if (only) return (only[gradeId] ?? []).includes(level);
  return (GRADE_LEVELS[gradeId] ?? []).includes(level);
}

/** 그 등급에서 level 에 가장 가까운 레벨 (퀘스트 보상·명령어에서 없는 조합을 고칠 때) */
export function nearestGradeLevel(level, gradeId) {
  const list = GRADE_LEVELS[gradeId] ?? LEVELS;
  return list.reduce((best, l) => (Math.abs(l - level) < Math.abs(best - level) ? l : best), list[0]);
}

/** "30·35·40렙" 처럼 그 등급의 레벨 목록 글자 */
export function gradeLevelsText(gradeId) {
  return `${(GRADE_LEVELS[gradeId] ?? LEVELS).join("·")}렙`;
}

// mult: 피해 배율 (치명타는 무기 종류별로 TYPES 에서 정함)
// color: 어두운 배경(아이템 설명)용 색, dark: 밝은 배경(상자 창)용 색
export const GRADES = [
  { id: "common", name: "일반", color: "§f", dark: "§0", mult: 1.0, icon: "textures/rpg_ui/grade_common" },
  { id: "rare", name: "희귀", color: "§9", dark: "§1", mult: 1.15, icon: "textures/rpg_ui/grade_rare" },
  { id: "noble", name: "고귀", color: "§u", dark: "§5", mult: 1.3, icon: "textures/rpg_ui/grade_noble" },
  { id: "hero", name: "영웅", color: "§v", dark: "§6", mult: 1.5, icon: "textures/rpg_ui/grade_hero" },
  { id: "transcend", name: "초월", color: "§c§l", dark: "§4§l", mult: 1.75, icon: "textures/rpg_ui/grade_transcend" },
];

export const TYPE_ORDER = ["sword", "dagger", "axe", "shield", "bow", "wand"];

// ---- 무기 이름 (v1.2.0): 클로드 디자인에서 받은 디자인 이름. generator/build.py 의 NAMES 와 같게
// 초월 도끼 자리는 궁니르(창 모양, 수치는 도끼 규칙), 초월 활은 아르테미스의 은활
export const WEAPON_NAMES = {
  sword: { common: "철검", rare: "푸른 강철검", noble: "황금 왕검", hero: "발뭉", transcend: "그람" },
  dagger: { common: "철 단검", rare: "청강 단검", noble: "황금 의식단검", hero: "그림자 단검", transcend: "클라우 솔라스" },
  axe: { common: "철 전투도끼", rare: "서리 전투도끼", noble: "황금 군주도끼", hero: "대지의 심판", transcend: "궁니르" },
  shield: { common: "참나무 방패", rare: "푸른 강철 방패", noble: "황금 사자 방패", hero: "듀란드의 방패", transcend: "아이기스의 방패" },
  bow: { common: "참나무 장궁", rare: "청월 장궁", noble: "태양 장궁", hero: "엘프의 활", transcend: "아르테미스의 은활" },
  wand: { common: "견습 마법 지팡이", rare: "청수정 지팡이", noble: "태양 홍옥 지팡이", hero: "리치의 지팡이", transcend: "간반테인" },
  greatsword: { transcend: "티르빙" }, // v2.16.0 마검사 무기 (제작 전용, TYPE_ONLY)
};
// 설명 첫 줄의 종류 이름·연출을 바꾸는 무기 ("종류_등급"): 궁니르는 도끼 규칙이지만 창
export const LOOK = { axe_transcend: { label: "창", slash: "spear" } };
/*
 * stat: 주 스탯 (요구·피해 보너스), damage: 종류별 피해 배율, req: 주 스탯 요구 배율 (REQ 의 요구 단위에 곱함)
 * reqExtra: 주 스탯 말고 더 요구하는 스탯 { 스탯: 배율 }, bonus: 주 스탯 말고 피해를 올리는 스탯 { 스탯: 1포인트당 비율 }
 *   v1.8.1: 도끼는 힘 2/3 + 체력 1/3 (합이 다른 무기의 한 스탯 요구와 같게, 체력은 힘의 절반)
 * perPoint: 주 스탯 1포인트당 피해 비율을 이 무기만 따로 정할 때 (없으면 STAT_INFO 의 perPoint)
 * 치명타 확률 = crit + critPerLevel × 무기 레벨 + critPerGrade × 등급 순번 (최대 CRIT_CAP)
 * 치명타 배율 = critMult + critMultPerLevel × 무기 레벨 + critMultPerGrade × 등급 순번
 *
 * 밸런스 기준 (치명타 포함 평균, 칼 = 1, 같은 레벨·같은 포인트):
 *   도끼 = 딜탱: 체력도 요구하고 체력 1당 피해 +0.5%. 칼보다 초당 피해 약 5~15% 낮고 최대 체력 약 5~20% 높음,
 *                최대 체력 × 초당 피해는 칼과 비슷 (0.99~1.04배)
 *   단검 한 방 약 1.0배 (평소 타격은 약하고 치명타가 자주·세게)
 *   활   한 방 약 1.0배, 1초에 한 발이라 초당 약 0.5배 (원거리)
 *   완드 한 방 약 1.0배, 0.5초 시전 + 1초마다 한 번이라 초당 약 0.5배 (원거리·방어 무시)
 *        (v1.1.7까지 한 방 1.2배 · 0.8초마다. 시전 시간이 생겨 느려진 만큼 한 방을 1.25배로 올려 초당 피해는 같게)
 *   v2.3.0 (사용자 의견 "근접은 아파서 죽겠는데 원거리는 딜 차이가 크지 않아 쉬움"): 근접(칼·단검·도끼) 피해 배율 ×1.25,
 *          원거리(활·완드) ×0.85 — 근접은 몹이 밀려나고 공격을 피하느라 실제로 때리는 시간이 원거리보다 짧고(약 70% 대 95%),
 *          원거리는 같은 레벨 몹이 다가오는 동안 대부분 잡아서 거의 맞지 않았음. 이제 한 방은 비슷하고 초당은 근접이 2배,
 *          실제로 때리는 시간을 넣으면 근접이 원거리보다 약 1.4~1.5배 빨리 잡음 (이전에는 거의 같았음).
 *          (이전 배율: 칼 1.0 · 단검 0.9 · 도끼 0.95 · 활 1.4 · 완드 1.5, 원거리 한 방 약 1.45~1.5배 · 초당 약 0.73~0.75배)
 *          스킬도 무기 공격력에서 나오므로 같은 비율로 바뀜 (스킬 = 평타 몇 초 분량 규칙은 그대로)
 *   방패 = 체력 탱커 (v1.1.7, 오른손으로 때림): 포인트를 전부 체력에 찍는 기준. 피해 배율 0.8, 체력 1당 +0.5%. v2.3.0 에도 그대로
 *          초당 피해는 칼(최적 분배)의 0.62배(0렙) → 0.37배(40렙), 대신 최대 체력·피해 감소로 버티는 양이 1.2배 → 2.3배.
 *          (v2.3.0 전 칼 기준 0.78배 → 0.46배: 방패는 그대로이고 칼이 올라서 낮아짐. 사냥 안전감은 방패가 가장 높음)
 *   근접은 몹 무적 시간 때문에 초당 약 2번 들어감
 */
export const TYPES = {
  sword: { name: "칼", stat: "str", damage: 1.25, req: 1.0, crit: 0.05, critPerLevel: 0.0005, critPerGrade: 0.01, critMult: 1.5, critMultPerLevel: 0, critMultPerGrade: 0.05 },
  dagger: { name: "단검", stat: "dex", damage: 1.125, req: 1.0, crit: 0.22, critPerLevel: 0.007, critPerGrade: 0.01, critMult: 2.0, critMultPerLevel: 0.0125, critMultPerGrade: 0 },
  axe: { name: "도끼", stat: "str", damage: 1.1875, req: 2 / 3, reqExtra: { vit: 1 / 3 }, bonus: { vit: 0.005 }, crit: 0.02, critPerLevel: 0.0005, critPerGrade: 0.005, critMult: 1.5, critMultPerLevel: 0, critMultPerGrade: 0.05 },
  // v1.1.7 방패 = 체력만 찍은 탱커의 오른손 무기 (방패로 때림). perPoint: 이 무기만의 체력 1포인트당 피해 증가
  shield: { name: "방패", stat: "vit", damage: 0.8, perPoint: 0.005, req: 1.0, crit: 0.02, critPerLevel: 0.0005, critPerGrade: 0.005, critMult: 1.5, critMultPerLevel: 0, critMultPerGrade: 0.05 },
  bow: { name: "활", stat: "dex", damage: 1.19, req: 1.0, crit: 0.15, critPerLevel: 0.007, critPerGrade: 0.01, critMult: 2.0, critMultPerLevel: 0.0125, critMultPerGrade: 0 },
  wand: { name: "완드", stat: "int", damage: 1.275, req: 1.0, crit: 0.03, critPerLevel: 0.0005, critPerGrade: 0.01, critMult: 1.5, critMultPerLevel: 0, critMultPerGrade: 0.05 },
  // v2.16.0 마검 (티르빙, 제작 전용 — TYPE_ONLY): 마검사 (v2.13.1 사용자가 고른 마력 2 : 힘 1). 요구 = 마력 2/3 + 힘 1/3 (합은 다른 무기와 같음 — 40렙 초월 74 + 36),
  //   피해 = 1 + 마력 × 2% + 힘 × 2% → 같은 포인트면 칼과 같은 평타 (요구만 찍으면 3.2배, 125 를 모두 찍으면 3.5배). 수치는 칼과 같고 평타는 공격 피해 (검)
  //   초월 스킬은 검 (공격 피해) + 마력 (마법 피해, 파란 숫자) 로 나뉨 (SKILLS.greatsword)
  greatsword: { name: "마검", stat: "int", damage: 1.25, req: 2 / 3, reqExtra: { str: 1 / 3 }, bonus: { str: 0.02 }, crit: 0.05, critPerLevel: 0.0005, critPerGrade: 0.01, critMult: 1.5, critMultPerLevel: 0, critMultPerGrade: 0.05 },
};
export const CRIT_CAP = 0.75; // 치명타 확률 최대

// 레벨별 기본 피해 (일반 등급 칼 기준)
export const LEVEL_DAMAGE = { 0: 4, 5: 5, 10: 6, 15: 7, 20: 8, 30: 10, 35: 11, 40: 12 };

/**
 * 요구 스탯 (v1.8.1): 요구 단위 = (무기 레벨 + 등급 순번 × gradeStep) × perLevel, 스탯마다 × 배율, 올림
 * - 레벨이 오를 때 받는 포인트가 3개라서, 40렙 무기는 주 스탯 100 (40렙 포인트 125의 80%). 등급마다 +2.5 (초월 40렙 110)
 * - 도끼는 힘 2/3 + 체력 1/3, 합은 다른 무기와 같음 (40렙 일반 = 힘 67 · 체력 33)
 * - (v1.8.0까지: (레벨 + 등급 순번 × 2) × 1 → 40렙 일반 40, 초월 48)
 */
export const REQ = { perLevel: 2.5, gradeStep: 1 };

/** 요구 단위 (무기·갑옷 같음, v1.8.2부터 갑옷도): (레벨 + 등급 순번 × gradeStep) × perLevel */
export function reqUnit(level, gradeIndex) {
  return (level + gradeIndex * REQ.gradeStep) * REQ.perLevel;
}

/**
 * 요구 단위를 스탯마다 나눔: mults = [[스탯, 배율], ...] (첫 번째가 주 스탯) → 스탯마다 단위 × 배율 올림.
 * 여러 스탯이면 합이 (배율 합 × 단위) 올림을 넘지 않게 마지막 스탯에서 뺌 (도끼·힘+체력 갑옷 40렙 일반 = 힘 67 + 체력 33 = 100)
 * @param {number} unit
 * @param {[string, number][]} mults
 * @returns {{stat: string, value: number}[]}
 */
export function splitReq(unit, mults) {
  const out = mults.map(([stat, m]) => ({ stat, value: Math.ceil(unit * Number(m) - 1e-9) }));
  if (out.length > 1) {
    const total = Math.ceil(unit * mults.reduce((a, [, m]) => a + Number(m), 0) - 1e-9);
    const sum = out.reduce((a, r) => a + r.value, 0);
    const last = out[out.length - 1];
    last.value = Math.max(0, last.value - Math.max(0, sum - total));
  }
  return out;
}

export function requiredStat(level, gradeIndex, type) {
  return requiredStats(level, gradeIndex, type)[0].value;
}

/**
 * 무기의 요구 스탯 목록 (첫 번째가 주 스탯)
 * @returns {{stat: string, value: number}[]}
 */
export function requiredStats(level, gradeIndex, type) {
  const t = TYPES[type];
  /** @type {[string, number][]} */
  const mults = [[t?.stat ?? "str", t?.req ?? 1], ...Object.entries(t?.reqExtra ?? {}).map(([s, m]) => /** @type {[string, number]} */ ([s, Number(m)]))];
  return splitReq(reqUnit(level, gradeIndex), mults);
}

/**
 * 무기 피해를 올리는 스탯 목록 (첫 번째가 주 스탯). 피해 배율 = 1 + Σ 스탯 × per
 * @returns {{stat: string, per: number}[]}
 */
export function damageScaling(type) {
  const t = TYPES[type];
  const stat = t?.stat ?? "str";
  const out = [{ stat, per: t?.perPoint ?? damagePerPoint(stat) }];
  for (const [stat, per] of Object.entries(t?.bonus ?? {})) out.push({ stat, per: Number(per) });
  return out;
}

export const SHIELD = {
  passiveBase: 0.05, // 들고만 있어도 받는 피해 감소
  passivePerLevel: 0.0025,
  passivePerGrade: 0.02,
  passivePerVit: 0.001, // 체력 스탯 1당 +0.1%p
  passiveCap: 0.4,
  blockBase: 0.5, // 웅크려 정면 공격을 막을 때 감소
  blockPerLevel: 0.005,
  blockPerGrade: 0.05,
  blockCap: 0.95,
  bashBlockLockTicks: 10, // 방패로 공격(휘두름)하면 이 시간(틱) 동안 웅크려 막기가 풀림. 들고 있을 때 감소는 그대로
};

// v1.1.6: 활도 완드처럼 누르면 바로 쏜다 (당기기·화살 아이템 없음, 바라보는 대상에 바로 명중)
export const BOW = {
  range: 24, // 사거리 (블록)
  cooldownTicks: 20, // 재사용 대기 (20틱 = 1초). 아이템의 cooldown(1초)과 같게
  arrowSpeed: 60, // 빛줄기가 날아가는 속도 (블록/초, 연출만)
  aimAssist: 12, // v1.2.0 조준 보정: 시선에 겹친 몹이 없으면 시선에서 이 각도(도) 안의 몹에게 (벽 뒤 제외). 0 이면 끔
  // 등급별 화살 빛 색 (빨강, 초록, 파랑 0~1): 일반 · 희귀 · 고귀 · 영웅 · 초월
  color: [
    [0.8, 1.0, 0.8],
    [0.45, 0.75, 1.0],
    [0.8, 0.55, 1.0],
    [1.0, 0.82, 0.3],
    [1.0, 0.35, 0.35],
  ],
};

// v1.1.8: 완드는 선딜레이(시전)가 있다. 누르면 손 앞에 마법진이 그려지고, 다 그려지면 마법이 나간다.
export const WAND = {
  range: 20, // 사거리 (블록)
  aimAssist: 12, // v1.2.0 조준 보정 (BOW.aimAssist 와 같음)
  castTicks: 10, // 시전 시간 = 마법진이 다 그려지는 시간 (20틱 = 1초). 이동은 되고, 손에서 완드를 바꾸면 취소
  cooldownTicks: 20, // 시전 시작부터 다음 시전까지 (시전 시간 포함). 아이템의 cooldown(1초)과 같게
  // 마법진: 눈앞 dist, 오른쪽(완드 든 손) side, 아래 drop 블록 자리(1인칭에서 완드 끝 근처)에 크기 size(반지름, 블록). 틱마다 spinPerTick 도씩 돎
  // v1.1.9: 1.5배 크게 (반지름 0.24 → 0.36). 등급 크기(초월 1.25배)까지 커져도 조준점을 가리지 않게 오른쪽으로 옮김 (side 0.36 → 0.48)
  circle: { dist: 1.1, side: 0.48, drop: 0.12, size: 0.36, spinPerTick: 9, alpha: 0.9, flashLife: 0.25 },
  // 등급별 마법진 색 (빨강, 초록, 파랑 0~1): 일반 · 희귀 · 고귀 · 영웅 · 초월
  color: [
    [0.75, 0.95, 1.0],
    [0.45, 0.7, 1.0],
    [0.8, 0.5, 1.0],
    [1.0, 0.75, 0.3],
    [1.0, 0.35, 0.35],
  ],
  // 등급별 궤적 파티클
  trail: [
    "minecraft:endrod",
    "minecraft:blue_flame_particle",
    "minecraft:dragon_breath_trail",
    "minecraft:basic_flame_particle",
    "minecraft:redstone_wire_dust_particle",
  ],
};

// ---- 근접 참격 연출 (칼·단검·도끼로 공격할 때마다, 허공도 포함) ----
// glow: 빛 색 (빨강, 초록, 파랑 0~1), size: 참격 호의 반지름 (블록), life: 보이는 시간 (초)
// speed: 앞으로 나가는 속도 (블록/초), dist: 눈앞 거리 (블록), jitter: 매번 조금씩 다른 각도 (±도)
// 베는 방향(위에서 아래로)과 기울기는 텍스처에 들어 있고, 좌우는 매번 번갈아 나간다.
export const SLASH = {
  enabled: true,
  cameraShake: true, // 도끼로 맞히면 화면이 살짝 흔들림
  drop: 0.1, // 눈앞 참격을 눈높이보다 얼마나 아래에 (블록)
  offAxis: 12, // 화면 가운데에서 이 각도(도)보다 벗어난 대상을 눌러 맞히면 맞은 자리에도 참격
  sword: { shape: "arc", glow: [0.47, 0.84, 1.0], size: 1.25, life: 0.22, speed: 4, dist: 1.5, jitter: 6 }, // 깔끔한 초승달 베기
  dagger: { shape: "thin", glow: [0.31, 1.0, 0.59], size: 1.2, life: 0.2, speed: 6, dist: 1.4, jitter: 5, second: 2 }, // 빠른 X자 두 번 베기 (second: 두 번째 베기까지 틱)
  axe: { shape: "heavy", glow: [1.0, 0.51, 0.18], size: 1.05, life: 0.3, speed: 2, dist: 1.8, jitter: 4 }, // 두껍게 내려찍기 + 충격파
  // 방패 치기: 휘두르면 눈앞에 푸른 강철빛 고리가 퍼지고, 맞히면 대상 앞에 충격 폭발(가시 달린 고리) + 둔탁한 소리 + 작은 화면 흔들림
  shield: { bash: true, glow: [0.62, 0.8, 1.0], size: 0.36, life: 0.18, speed: 3, dist: 1.2, jitter: 25, hitSize: 0.62, hitLife: 0.24, critColor: [1.0, 0.8, 0.3] },
  // 궁니르 (v1.2.0, 초월 도끼 자리의 창): 휘두르면 눈앞으로 곧게 찌르는 빛줄기, 맞히면 대상 앞에 얼음빛 섬광
  spear: { thrust: true, glow: [0.56, 0.83, 1.0], core: [1.0, 0.92, 0.6], length: 1.4, thick: 0.16, dist: 0.9, life: 0.16, speed: 16, hitSize: 0.55 },
  // v2.16.0 티르빙 (마검): 장밋빛 두꺼운 베기 + 안쪽에 칼날 룬의 하늘빛 가는 베기 (rune, 1틱 늦게) — 검 + 마력. 치명타는 보랏빛 X + 장밋빛 초승달
  greatsword: { shape: "heavy", glow: [0.96, 0.52, 0.5], rune: [0.53, 0.8, 1.0], size: 1.3, life: 0.26, speed: 3, dist: 1.7, jitter: 5 },
  gradeSize: [1.0, 1.05, 1.1, 1.18, 1.25], // 등급별 크기 (일반 → 초월)
  gradeAura: [null, null, null, [1.0, 0.8, 0.3], [1.0, 0.3, 0.35]], // 영웅(금빛)·초월(붉은빛)은 참격 안쪽에 색 기운이 더해짐
};

// ---- 상점 ----
export const SHOP = {
  grades: ["common", "rare"], // 상점에서 파는 등급
  basePrice: { 0: 2, 5: 4, 10: 8, 15: 12, 20: 16, 30: 24, 40: 32 }, // 레벨별 일반 등급 가격
  gradePriceMult: { common: 1, rare: 3, noble: 8, hero: 20, transcend: 50 },
  typePriceMult: { sword: 1, dagger: 1, axe: 1, shield: 1, bow: 1, wand: 1 }, // v2.16.0 마검 (티르빙) 은 상점에 없음 (TYPE_ONLY)
};

// ---- 대장장이 (v1.1.9) ----
// 영웅 무기의 레벨 제한을 5렙씩 올린다 (30 → 35 → 40). 기본 성능(공격력·치명타·방패 감소)은 올린 레벨 기준으로 오르고
// 요구 레벨 (v2.6.2 렙제)·요구 스탯도 그 레벨 기준이 된다. 강화(+1~+10)는 별도 시스템 (v1.5.0 ENHANCE, 레벨을 올려도 강화 단계는 그대로)
export const SMITH = {
  step: 5,
  // 올리기 전 레벨 → 비용
  cost: {
    30: { emerald: 64, ore: 4 },
    35: { emerald: 96, ore: 8 },
  },
  oreItem: "rpg:star_ore", // 레이드 보스에게서 나오는 재료 (레이드가 생기기 전에는 /ore 명령으로 지급)
  oreName: "별철 광석",
  oreIcon: "textures/items/rpg/star_ore",
  oreLore: ["§r§7레이드 보스에게서 나오는 광석", "§r§7영웅 무기의 레벨 제한을 올릴 때", "§r§7대장장이에게 가져가세요"],
};

// ---- NPC 연출 ----
export const NPC = {
  shopParticle: "minecraft:villager_happy", // 상점 NPC
  smithParticle: "minecraft:lava_particle", // 대장장이 NPC (불똥)
  runesmithParticle: "minecraft:mob_portal", // 룬술사 NPC (보랏빛, v2.7.0)
  questParticle: "minecraft:totem_particle", // 받을 퀘스트가 있는 NPC (그 플레이어에게만 보임)
  readyParticle: "minecraft:endrod", // 완료 보고할 퀘스트가 있는 NPC (그 플레이어에게만 보임)
  repeatParticle: "minecraft:blue_flame_particle", // 받을 것이 반복 의뢰뿐인 NPC (v2.7.0, 그 플레이어에게만 보임)
  particleRange: 24,
  promptRange: 5,
};

// =====================================================================
//  초월 무기 스킬 (v1.2.0)
//  조작: 근접(그람·클라우 솔라스·궁니르·아이기스) 버튼 = 스킬 1, 웅크리고 버튼 = 스킬 2
//        활·완드(아르테미스·간반테인) 버튼 짧게 = 평타, 웅크리고 버튼 = 스킬 1, 길게 누르기 = 스킬 2
//  피해 = 무기 공격력 × mult × 스탯 배율(무기 피해와 같은 1 + 스탯 × 1포인트당 비율) → 스탯을 올리면 스킬 피해도 오른다
//  치명타는 무기의 치명타 확률·배율 (critBonus 만큼 확률 더함, sure = 반드시 치명타)
//  요구 조건은 무기와 같다 (최고 레벨 40 + 40렙 초월 무기 요구 스탯을 채워야 씀, v2.6.2)
//
//  밸런스 기준 ("평타 몇 초어치" = 한 대상에게 주는 피해 ÷ 그 무기의 평타 초당 피해):
//    스킬 1 = 약 2초어치 (재사용 8~10초 → 평타 초당 피해 +20~25%)
//    스킬 2 = 대상마다 약 1.5초어치 (재사용 12초, 여러 대상), 또는 방어·회복·기절 같은 효과
//    근접은 평타가 초당 2번이라 mult 가 원거리의 2배쯤 (같은 초어치)
// =====================================================================
export const SKILLS = {
  holdTicks: 12, // 활·완드: 이만큼(0.6초) 누르고 있으면 스킬 2. generator/build.py 의 SKILL_HOLD(초)와 같게
  hitPlayers: false, // 스킬이 다른 플레이어에게도 맞는지 (false = 몹만)
  hudEvery: 10, // 초월 무기를 들고 있으면 이 주기(틱)로 화면 하단에 재사용 대기 표시
  // 종류별 [스킬 1, 스킬 2]. name: 이름, desc: 설명 한 줄(아이템 설명 50바이트 안), cd: 재사용 대기(초)
  sword: [
    // 그람: 균형형 근접 딜러 — v2.17.0 개편 (사용자 요청 "멋있고 쎄게, 쿨이 길어도 좋으니" · 고른 세기 "영웅 룬~초월 룬 사이"):
    //   한 대상 배율 합 (skillTotalMult) 스킬 1 8.5 · 스킬 2 13.5 = Lv.40 힘 125 · 치명타 평균 약 840 (영웅 룬 한 번) · 1340 (초월 룬 한 번)
    // 화룡 돌진: 보는 쪽으로 dash 칸 돌진 — 땅을 따라 (한 칸 오르내림 · 두 칸 높이 굴도), 벽 · 낭떠러지 · 물 · 용암 · 불 앞에서 멈춤.
    //   돌진 길 (내 몸 폭) 에 몸이 걸치는 첫 적의 몸 앞 stop 칸에서 멈춤 (큰 보스는 몸 폭만큼 앞 · 옆에 선 적은 지나치며 벰). 지나온 길에 불꽃.
    //   멈춘 자리 앞 cone 도 · range 칸 + 지나온 길 (폭 width) 의 적을 벰 (mult, knock 밀쳐냄) → delay 틱 뒤 길이 불기둥으로 터지고 앞쪽 burst 칸 폭발
    //   (afterMult — 벤 적은 밀려났어도 burst + 2.5칸 안이면). 속박 중이거나 무엇이든 탄 동안은 제자리에서 벰
    { id: "gram_charge", name: "화룡 돌진", desc: "불꽃 길을 남기며 돌진해 벰", desc2: "0.5초 뒤 길과 앞쪽이 폭발", cd: 12, mult: 5.0, afterMult: 3.5, dash: 8, width: 1.4, stop: 1.2, range: 3.4, cone: 160, burst: 3.6, delay: 10, knock: 0.7 },
    // 용익 강하 (사용자 예시 "불꽃 날개를 펼쳐서 위로 잠깐 올라간 후 내려찍기"): 발밑에 단단한 땅이 있어야 씀 (속박 · 탄 동안이면 못 씀). 불꽃 날개를 펴며 떠오름
    //   (세로 넉백 rise, 약 4칸) → 꼭대기 apex 틱부터 hover 틱까지 머묾 (느린 낙하, 내려찍을 곳을 불꽃 룬으로 보임) → dive 틱에 걸쳐 조준한 땅
    //   (가로 reach 칸 안 · 설 수 있는 곳, 막히거나 물 · 용암 · 불이면 바로 아래) 으로 내려찍음 → 반지름 radius 칸 폭발 (mult, 띄움) →
    //   delay 틱 뒤 불꽃 고리 (afterMult — 띄운 적은 밀려났어도 radius + 2.5칸 안이면). 낙하 피해 없음. 떠 있는 동안 침묵 · 속박 · 탐 · 끌려감이면 끊김
    { id: "gram_descent", name: "용익 강하", desc: "불꽃 날개로 떠올라 내려찍기", desc2: "반지름 6칸 화염 폭발", cd: 30, mult: 9.5, afterMult: 4.0, rise: 0.85, apex: 9, hover: 18, dive: 3, reach: 6, radius: 6, delay: 10 },
  ],
  dagger: [
    // 클라우 솔라스 (치명타 암살 — 민첩) — v2.19.0 개편 (사용자가 고른 컨셉 "섬광 난무 / 광휘의 검진", 세기 "영웅 룬~초월 룬"):
    //   한 대상 배율 합 (skillTotalMult) 스킬 1 8.7 · 스킬 2 14.0 = Lv.40 민첩 125 · 치명타 평균 (54% × 2.5 → ×1.81) 약 836 · 1346
    //   = 같은 캐릭터의 민첩 룬 (위력 P 42) 영웅 20P 840 · 초월 32P 1344 한 번
    // 섬광 난무: 앞쪽 (cone 도) range 칸 안의 적 (조준한 적부터, 가까운 순으로 이어서) 최대 targets 명 사이를 gap 틱마다 빛이 되어 순간이동하며 벰 —
    //   모두 blinks 번 (적이 적으면 돌아가며: 한 명이면 그 둘레 네 방향에서). 몸 반폭 + side 칸 떨어진 설 자리로 (지나온 쪽의 반대편, 번갈아 ±35°), 그 적을 바라봄.
    //   적마다 처음 벨 때 mult, 빛의 자국이 남음 → 처음 자리로 돌아와 delay 틱 뒤 검을 거두면 모든 자국이 터짐 (afterMult).
    //   설 자리가 없거나 속박 · 탄 동안이면 그 자리에서 빛의 참격을 날림 (피해 같음). 낙하 피해 없음
    { id: "solas_rush", name: "섬광 난무", desc: "빛이 되어 적 4명 사이를 벰", desc2: "돌아와 검을 거두면 자국이 폭발", cd: 12, mult: 3.5, afterMult: 5.2, range: 10, cone: 150, targets: 4, blinks: 4, gap: 3, delay: 10, side: 1.3 },
    // 광휘의 검진: 머리 위에 빛의 검 swords 자루가 반원으로 하나씩 나타나 (form 틱) 맡은 적을 겨눔 → hold 틱 뒤 launchGap 틱마다 하나씩 날아가 (flight 틱) 꽂힘 —
    //   조준한 곳 (조준한 몹 · 시선이 닿은 곳, range 칸) 반지름 radius 칸의 적 (가까운 순 targets 명, 검은 돌아가며 나눔). 적마다 처음 꽂힐 때 mult →
    //   마지막 검이 꽂힌 pillar 틱 뒤 꽂힌 검이 빛기둥으로 터짐 (pillarMult) → cross 틱 뒤 거대한 빛의 X 참격 (afterMult, 꽂힌 적은 밀려났어도 radius + 2.5칸 안이면).
    //   조준한 곳에 적이 없으면 재사용 대기 없이 거절. half = 빛의 검 반 길이 (모델 2.56칸보다 작게), ring = 반원 반지름 (머리 위 1.1칸 · 칼끝이 바깥 — 햇살처럼)
    { id: "solas_halo", name: "광휘의 검진", desc: "빛의 검 8자루가 적에게 꽂힘", desc2: "꽂힌 검이 빛기둥 → 빛의 X 참격", cd: 30, mult: 4.0, pillarMult: 4.0, afterMult: 6.0, swords: 8, targets: 8, range: 24, radius: 6, form: 8, hold: 4, launchGap: 2, flight: 5, pillar: 10, cross: 10, half: 0.85, ring: 1.35 },
  ],
  axe: [
    // 궁니르 (도끼 규칙: 힘 + 체력 딜탱) — v2.18.0 개편 (사용자가 고른 컨셉 "신창 귀환 / 신창 강림", 세기 "영웅 룬~초월 룬", 그림 "파티클 그림만"):
    //   한 대상 배율 합 (skillTotalMult) 스킬 1 9.9 · 스킬 2 15.9 = Lv.40 힘 83 · 체력 42 (요구 스탯과 같은 2 : 1) · 치명타 평균 약 737 · 1184
    //   = 같은 캐릭터의 도끼 룬 (적용 스탯 힘 + 체력 × 0.5 = 104, 위력 P 36.96) 영웅 20P 739 · 초월 32P 1183 한 번
    // 신창 귀환: 조준한 쪽 (조준 보정) 으로 궁니르를 던짐 — 창끝이 손에서 range 칸 (벽 앞까지) 에 닿을 때까지 틱당 speed 칸으로 날며 폭 width 칸을 꿰뚫음 (mult) →
    //   끝에서 hover 틱 동안 세로로 한 바퀴 돌며 번개를 모음 → 손으로 돌아오며 (3틱마다 지금 손을 다시 겨눔) 지나는 적을 다시 벰 (backMult),
    //   끌 수 있는 몹 (보스 · 소품 · 허수아비 · 아주 큰 몹 · 몸이 넓은 몹 아님) 은 hook 마리까지 창끝에 꿰어 내 앞까지 끌어옴. half = 창 반 길이 (모델 길이 4.19칸)
    { id: "gungnir_return", name: "신창 귀환", desc: "꿰뚫고 돌아오며 적을 끌어옴", desc2: "갈 때 · 올 때 두 번 벰", cd: 12, mult: 4.4, backMult: 5.5, range: 18, width: 1.4, speed: 2, hover: 6, hook: 4, half: 2.1 },
    // 신창 강림: 창을 하늘로 던짐 → 조준한 곳 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞 · 그 아래 땅) 에 delay 틱 동안 번개 룬 · 그림자 · 먹구름 (cloud 칸 위) →
    //   거대한 궁니르 (길이 length × giant 칸) 가 drop 칸 위에서 fall 틱 동안 떨어져 꽂힘: 반지름 radius 칸 번개 폭발 (mult, 띄움) →
    //   pulseGap 틱마다 pulses 번 꽂힌 창에서 번개 (pulseMult, 반지름 radius + 1, 띄운 적은 밀려났어도 radius + 2.5 안) → stay 틱 뒤 fade 틱 동안 빛으로 흩어짐.
    //   준 피해의 heal 만큼 회복 — 한 번 쓸 때 최대 체력의 healCap 까지 (한 대상에 약 1180 이라 15% 면 체력 바 하나가 넘음 — 도끼 룬 회복과 비슷한 크기로).
    //   땅이 없는 곳 (허공) 을 조준하면 재사용 대기 없이 거절
    { id: "gungnir_fall", name: "신창 강림", desc: "하늘에서 거대한 궁니르가 꽂힘", desc2: "피해 15% 회복 (최대 체력 35%)", cd: 30, mult: 9.5, pulseMult: 3.2, pulses: 2, pulseGap: 14, heal: 0.15, healCap: 0.35, range: 24, min: 5, radius: 6, delay: 20, fall: 5, drop: 30, cloud: 16, stay: 40, fade: 12, length: 4.19, giant: 8, half: 2.1 },
  ],
  shield: [
    // 아이기스의 방패 (탱커 — 체력) — v2.20.0 개편 (사용자가 고른 컨셉 "고르곤 돌진 / 신의 방벽", 세기 "영웅 룬~초월 룬"):
    //   한 대상 배율 합 (skillTotalMult) 스킬 1 29.5 · 스킬 2 47.0 = Lv.40 체력 125 · 치명타 평균 (6% × 1.7 → ×1.042) 약 839 · 1337
    //   = 같은 캐릭터의 체력 · 성기사 룬 (위력 P 42) 영웅 20P 840 · 초월 32P 1344 한 번 (방패는 평타가 약해서 — 공격력 16.8, 체력 1포인트 0.5% — 배율이 큼)
    // 고르곤 돌진: 아이기스 모양 금빛 빛의 방패를 앞세워 보는 쪽으로 dash 칸 돌진 (틱마다 speed 칸, 땅을 따라 — 벽 · 낭떠러지 · 물 · 용암 앞에서 멈춤).
    //   방패 (몸 앞 front 칸, 길에서 옆으로 width 칸) 에 닿은 적: 부딪힘 (mult) — 끌 수 있는 몹은 carry 마리까지 방패 앞에 붙여 밀고 감 (벽에 몰리면 거기서 멈춤),
    //   끌 수 없는 몹 (보스 · 큰 몹 · 허수아비) 은 그 앞에서 멈춤. 끝에서 방패의 메두사 눈이 번쩍 — 방패 앞 gaze 칸 · cone 도 적과 밀던 적이 stone 틱 동안 돌이 됨
    //   (움직이지 못하고 근접 공격이 거의 들어가지 않음, 보스 · 소품은 걸리지 않음) → 돌이 깨지며 afterMult + 도발 (taunt 틱 동안 나를 노림)
    { id: "aegis_charge", name: "고르곤 돌진", desc: "방패를 앞세워 적을 밀며 돌진", desc2: "석화 1초 → 깨지며 피해 · 도발", cd: 12, mult: 9.5, afterMult: 20.0, dash: 6, speed: 1.0, front: 1.0, width: 1.0, carry: 6, gaze: 5, cone: 80, stone: 20, taunt: 60 },
    // 신의 방벽: 방패를 땅에 꽂아 (발밑 땅) 반지름 radius 칸 금빛 육각 돔을 ticks 틱 동안 세움 — 꽂는 충격 mult (돔 안의 적, 도발 · 둔화).
    //   돔 안에 서 있는 동안 내가 받는 피해 -reduce (막을 때마다 육각 칸이 번쩍), 10틱마다 돔 안의 적을 도발 · 둔화 (구속 slow + 1).
    //   ticks 틱에 돔이 안으로 접힘 (fold 틱 — 적을 가운데로 끌어당기며 foldMult) → burst 틱 뒤 번개와 함께 바깥으로 터짐 (afterMult, 밖으로 밀쳐냄 —
    //   접힐 때 맞은 적은 밀려났어도 radius + 2.5칸 안이면)
    { id: "aegis_dome", name: "신의 방벽", desc: "금빛 돔 4초: 받는 피해 -60%", desc2: "적 도발 → 접히며 번개 폭발", cd: 30, mult: 7.0, foldMult: 10.0, afterMult: 30.0, radius: 5, ticks: 80, reduce: 0.6, fold: 8, burst: 10, slow: 2, taunt: 30 },
  ],
  bow: [
    // 아르테미스의 은활 (원거리 치명타 — 민첩) — v2.21.0 개편 (사용자가 고른 컨셉 "은월 관통 / 만월의 사냥", 세기 "영웅 룬~초월 룬"):
    //   한 대상 배율 합 (skillTotalMult) 스킬 1 8.8 · 스킬 2 14.0 = Lv.40 민첩 125 · 치명타 평균 (47% × 2.5 → ×1.705) 약 844 · 1343
    //   = 같은 캐릭터의 민첩 · 궁수 룬 (위력 P 42) 영웅 20P 840 · 초월 32P 1344 한 번 (활 한 대 25 × 2.25 = 56.25)
    // 은월 관통: charge 틱 동안 활에 달빛이 모임 → 그때 조준한 쪽 (조준 보정) 으로 거대한 은빛 화살 (그림 반 길이 half 칸) — 손에서 range 칸 (벽 앞까지) 을
    //   틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult) → 끝에 닿고 delay 틱 뒤 지나간 길이 초승달 참격으로 갈라짐 (afterMult — 그때 길 위의 적 +
    //   꿰뚫린 적은 밀려났어도 길에서 width + 2.5칸 안이면). 모으는 동안 활을 바꾸거나 침묵이면 쏘지 않음
    { id: "artemis_pierce", name: "은월 관통", desc: "달빛을 모아 거대한 화살로 관통", desc2: "0.5초 뒤 길이 초승달로 갈라짐", cd: 12, mult: 3.6, afterMult: 5.2, charge: 8, range: 24, width: 1.2, speed: 3, delay: 10, half: 1.7 },
    // 만월의 사냥: 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘에 만월을 품은 빛의 은활 (모델 모양) → rise 틱부터 gap 틱마다
    //   화살비 waves 번 (반지름 radius 칸 안의 적마다 한 발 — mult) → final 틱에 거대한 화살이 가운데에 내리꽂혀 달빛 폭발 (afterMult, 밀쳐 띄움 —
    //   화살비에 맞은 적은 밀려났어도 radius + 2.5칸 안이면). 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절
    { id: "artemis_moon", name: "만월의 사냥", desc: "만월의 은활이 화살비 3번", desc2: "거대한 화살 → 달빛 폭발", cd: 30, mult: 2.0, waves: 3, afterMult: 8.0, radius: 6, range: 24, min: 6, rise: 16, gap: 10, final: 56 },
  ],
  wand: [
    // v2.22.0 간반테인 개편 (사용자가 고른 컨셉 "후긴과 무닌 / 오딘의 눈" · 세기 "영웅 룬~초월 룬" — Lv.40 마력 125 의 마력 룬 위력 P 42 기준, 원거리 마법 · 방어 무시):
    //   한 대상 8.4 · 13.5 × 지팡이 한 대 93.8 × 치명타 평균 1.063 ≈ 838 · 1346 = 영웅 20P 840 · 초월 32P 1344 한 번 (예전 v1.2.0~2.21.0: 오딘의 번개 · 룬 대폭발)
    // 후긴과 무닌: 조준한 적 (range 칸, 조준 보정) + 그 적에서 jump 칸 안의 적 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비, 그 적의 몸에서 보이는) 가까운 순 targets 명까지.
    //   빛의 까마귀 둘이 번갈아 hits 번 쪼음 (mult — 적을 돌아가며, 같은 적은 0.5초 간격 · 쪼음 사이 gap 틱 이상, 날아오름 launch 틱 · 틱당 speed 칸) →
    //   마지막 쪼음 back 틱 뒤 까마귀가 돌아오면 쪼인 적마다 룬 표식이 터짐 (afterMult). 한 대상이면 여섯 번 모두 그 적에게 (한 대상 합 = mult × hits + afterMult)
    { id: "odin_ravens", name: "후긴과 무닌", desc: "빛의 까마귀 둘이 적을 6번 쪼음", desc2: "돌아오면 쪼인 적마다 룬 폭발", cd: 12, mult: 0.8, hits: 6, afterMult: 3.6, range: 20, jump: 8, targets: 4, launch: 6, speed: 1.2, gap: 5, back: 8 },
    // 오딘의 눈: 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘에 지팡이 고리 테두리의 거대한 금빛 눈 → open 틱에 다 뜸 → gap 틱마다 waves 번
    //   눈의 광선이 반지름 radius 칸의 적 (눈에서 보이는) 을 둘레를 따라 차례로 훑음 (mult) → final 6틱 전부터 감기며 final 틱에 대폭발 (afterMult, 밀쳐 띄움)
    { id: "odin_eye", name: "오딘의 눈", desc: "하늘의 눈이 광선으로 적을 훑음", desc2: "눈이 감기며 범위 대폭발", cd: 30, mult: 1.5, waves: 4, afterMult: 7.5, radius: 6, range: 24, min: 6, open: 10, gap: 10, final: 56 },
  ],
  // v2.16.0 티르빙 (마검 — 마력 2 : 힘 1, 제작 전용): 타격이 검 (공격 피해, 하늘색 숫자) + 마력 (마법 피해, 파란 숫자 · 치명타는 파란 !!) 로 나뉨.
  //   같은 적에게는 0.5초 간격 (몹 무적 시간). 한 대상 합 = mult × hits + spellMult
  greatsword: [
    // v2.23.0 티르빙 개편 (사용자가 고른 컨셉 "앙간튀르의 무덤" · 세기 "스킬1 영웅 룬 · 스킬2 초월 룬 × 1.4 (체력 대가)" — Lv.40 125포인트 (마력 83 · 힘 42) 의 마검사 룬 위력 P 41.9 기준):
    //   한 대상 8.5 · 18.9 × 티르빙 한 대 92.05 × 치명타 평균 1.077 ≈ 843 · 1874 = 영웅 20P 838 · 초월 32P × 1.4 1877 한 번 (예전 v2.16.0~2.22.0: 필중의 검기 · 저주 해방).
    //   모든 타격 × 최대 체력 ÷ hpBase (v2.17.0). 검 (공격 피해) mult · 마력 (마법 피해) spellMult
    // 무덤불: 보는 쪽 (조준한 적이 있으면 그 적 쪽 — 가로만) 땅을 따라 start ~ range 칸에 고르게 (갈라진 금처럼 좌우로 zig 칸씩 번갈아) pillars 개 무덤불 기둥이 lead 틱부터 한 틱씩 차례로 솟음 —
    //   기둥마다 width 칸 안의 적을 벰 (검 mult, 적마다 한 번) → burst 틱에 기둥이 한꺼번에 터짐 (마력 spellMult — 기둥 width + 0.4칸 안 · 벤 적은 밀려났어도 width + 2칸 안이면)
    { id: "tyrfing_fire", name: "무덤불", desc: "무덤불 기둥이 차례로 솟음", desc2: "불 기둥이 한꺼번에 터짐", cd: 12, mult: 3.5, spellMult: 5.0, range: 12, start: 2, pillars: 8, zig: 0.3, width: 1.6, lead: 2, burst: 20 },
    // 망자의 각성 (체력 대가 — 최대 체력 × cost 를 바침: 주위 radius 칸에 적이 없거나 바치면 1 아래가 되면 쓰지 않음, 이 스킬로 쓰러뜨리면 한 번 돌려받음):
    //   발밑 (그 자리에 남음) 에 무덤 룬, 둘레 radius 칸에 앙간튀르의 열두 형제 (warriors, 반 크기 size) 가 솟음 (rise 틱) → first 틱부터 step 틱마다 차례로 자기 쪽 (30°) 을 벰 (검 mult) →
    //   second 틱에 다 같이 내리벰 (검 mult) → final 틱에 무덤이 무덤불로 터짐 (마력 spellMult, 밀쳐 띄움). 한 대상 = mult × hits + spellMult
    //   (hits 2 = 첫 바퀴 · 둘째 바퀴 — 스킬의 짜임새라 스탯 창 · 설명의 합에만 씀, 바꾸면 skills.js 의 바퀴 수도 같이)
    { id: "tyrfing_waking", name: "망자의 각성", desc: "열두 망령이 베고 무덤불 폭발", desc2: "체력 10% 대가 · 쓰러뜨리면 반환", cd: 30, mult: 3.5, hits: 2, spellMult: 11.9, cost: 0.1, radius: 5, warriors: 12, rise: 10, first: 18, step: 2, second: 50, final: 62, size: 1.6 },
  ],
  // v2.16.0 티르빙 연출 색: spell = 칼날 룬의 하늘빛 (마력), rune = 보랏빛 룬, curse = 핏빛 (저주), dark = 어두운 자주 (저주 기운)
  // v2.17.0 hpBase (사용자 선택 "내 최대 체력 비례 · 스킬에만"): 티르빙 스킬의 모든 타격 × 최대 체력 ÷ hpBase.
  //   40렙 체력 스탯 0 (최대 체력 100) 이 ×1 — 체력 1포인트마다 스킬 피해 +1% (평타는 그대로 마력 · 힘)
  tyrfing: { spell: [0.53, 0.8, 1.0], rune: [0.62, 0.36, 0.9], curse: [0.8, 0.08, 0.2], dark: [0.32, 0.05, 0.26], hpBase: 100 },
  // 스킬 색 (연출): 종류별 [빛, 심지]
  color: {
    sword: [[1.0, 0.42, 0.16], [1.0, 0.9, 0.55]],
    dagger: [[1.0, 0.86, 0.35], [1.0, 1.0, 0.9]],
    axe: [[0.56, 0.83, 1.0], [1.0, 0.95, 0.7]],
    shield: [[1.0, 0.78, 0.3], [1.0, 0.97, 0.82]], // v2.20.0 아이기스: 금빛 빛 + 흰 금빛 심지 (메두사의 눈은 청록빛 — skills.js AE_EYE)
    bow: [[0.78, 0.9, 1.0], [1.0, 1.0, 1.0]],
    wand: [[1.0, 0.84, 0.42], [1.0, 1.0, 0.85]],
    greatsword: [[0.96, 0.56, 0.52], [1.0, 0.9, 0.84]], // v2.16.0 티르빙: 장밋빛 칼날 + 살굿빛 흰 심지
  },
};

/**
 * 스킬 한 번이 한 대상에게 주는 배율 합 (치명타 제외, 스탯 창 · 시험 · 설명용).
 * mult (또는 반사 reflect) × 횟수 (hits · shots · waves) + 번개 boltMult + 마력 spellMult + 뒤따르는 타격 afterMult (v2.17.0 그람)
 * + 돌아오는 타격 backMult · 번개 pulseMult × pulses (v2.18.0 궁니르) + 빛기둥 pillarMult (v2.19.0 클라우 솔라스) + 돔이 접힘 foldMult (v2.20.0 아이기스)
 * @param {any} k SKILLS 의 스킬 하나
 */
export function skillTotalMult(k) {
  return (k.mult ?? k.reflect ?? 0) * (k.hits ?? k.shots ?? k.waves ?? 1) + (k.boltMult ?? 0) + (k.spellMult ?? 0) + (k.afterMult ?? 0) + (k.backMult ?? 0) + (k.pulseMult ?? 0) * (k.pulses ?? 0) + (k.pillarMult ?? 0) + (k.foldMult ?? 0);
}

/**
 * v2.17.0 티르빙 스킬의 최대 체력 배율 (사용자 선택 "내 최대 체력 비례 · 스킬에만"): 최대 체력 ÷ SKILLS.tyrfing.hpBase
 * @param {number} maxHp
 */
export function tyrfingHpFactor(maxHp) {
  const base = SKILLS.tyrfing?.hpBase ?? 100;
  return maxHp > 0 && base > 0 ? maxHp / base : 1;
}

// =====================================================================
//  룬 스킬 (v1.3.0, v1.4.0 계열 8개 · 40종, v1.6.0 계열 10개 · 50종, v2.13.0 계열 11개 · 55종, v2.14.0 계열 13개 · 65종, v2.15.0 14개 · 70종, v2.24.0 16개 · 80종)
//  - 룬 = 핫바에 두고 누르면 쓰는 스킬 아이템 (rpg:rune_<계열>_<등급>). 요구 스탯 없음
//    v1.6.0: 터치(모바일)는 핫바에서 룬 칸을 고르기만 해도 바로 사용 (RUNE_QUICK, /runequick 로 켜고 끔)
//  - 룬 뽑기권(rpg:rune_ticket)을 쓰면 등급 확률(weight)대로 뽑고, 계열은 RUNE_FAMILY_ORDER (v2.24.0 열여섯) 중 하나 (같은 확률)
//  - 위력 P = 레벨 기본값(LEVEL_DAMAGE, 최고 경험치 레벨 기준 4~12) × (1 + 적용 스탯 × perPoint)
//    적용 스탯 = max(계열 계수로 더한 스탯, 가장 큰 스탯 × offStat) — 다른 스탯 캐릭터도 쓸 수 있지만 계수가 0.85
//    예: 도끼 룬 = 힘 × 1.0 + 체력 × 0.5, 검사 룬 = 힘 × 1.0 + 민첩 × 0.5, 나머지 계열 = 그 스탯 × 1.0
//    v2.13.0 마검사 룬 = (마력 + 힘 + 민첩) × (1 + 섞음), 섞음 = mix × 작은 쪽 ÷ 큰 쪽 (마력 ↔ 힘·민첩 중 큰 것) — 고르게 섞을수록 최대 +20%
//  - 스킬 피해 = P × mult (여러 번 때리면 한 번마다). 체력 룬의 회복·반사도 P 또는 적용 스탯에 비례
//  - 한 대상에게 들어가는 한 번 사용의 피해 합(단위 P): 일반 4 · 희귀 7 · 고귀 12 · 영웅 20 · 초월 32 (체력 룬은 피해 대신 방어·회복)
// =====================================================================
export const RUNE_GRADES = {
  // cd: 재사용 대기(초), weight: 뽑기 확률 가중치(합 100 = %)
  common: { cd: 8, weight: 50 },
  rare: { cd: 12, weight: 28 },
  noble: { cd: 18, weight: 14 },
  hero: { cd: 26, weight: 6.5 },
  transcend: { cd: 36, weight: 1.5 },
};
export const RUNE_STAT_ORDER = ["str", "dex", "vit", "int"];
/**
 * 룬 계열 (v1.4.0: 스탯마다 둘, v1.6.0: 암살자·검사 추가, v2.24.0: 신체 강화·평타 강화 — best = 네 스탯 중 가장 높은 것 × 1.0). stat = 대표 스탯 (아이콘 묶음), name = 이름, coef = 적용 스탯 계산 (스탯 × 계수의 합)
 * 앞의 넷은 v1.3.0 룬 (ID 그대로)
 */
export const RUNE_FAMILY_ORDER = ["str", "dex", "vit", "int", "axe", "bow", "holy", "storm", "assassin", "blade", "spellblade", "element", "spirit", "gravity", "body", "strike"];
export const RUNE_FAMILIES = {
  str: { stat: "str", name: "힘", coef: { str: 1 } },
  dex: { stat: "dex", name: "민첩", coef: { dex: 1 } },
  vit: { stat: "vit", name: "체력", coef: { vit: 1 } },
  int: { stat: "int", name: "마력", coef: { int: 1 } },
  axe: { stat: "str", name: "도끼", coef: { str: 1, vit: 0.5 } }, // 힘 딜탱: 힘 + 체력 절반
  bow: { stat: "dex", name: "궁수", coef: { dex: 1 } },
  holy: { stat: "vit", name: "성기사", coef: { vit: 1 } },
  storm: { stat: "int", name: "번개", coef: { int: 1 } },
  assassin: { stat: "dex", name: "암살자", coef: { dex: 1 } }, // v1.6.0: 표식·연계 콤보 (RUNE_COMBO)
  blade: { stat: "str", name: "검사", coef: { str: 1, dex: 0.5 } }, // v1.6.0: 힘 + 민첩 절반 (검술)
  // v2.13.0 마검사 (사용자 요청 "마력 + 힘 + 민첩을 섞은 하이브리드 룬" · 고른 것: 고르게 찍을수록 보너스). 마검 부여 (RUNE_SPELLBLADE)
  // v2.13.1 (사용자 요청 "힘이랑 마력 섞는데 마력을 좀 더 찍어야 하는 걸로" · 고른 것: 마력 2 : 힘 1): 마력 + 힘·민첩 × 0.5,
  //   마력 ÷ ratio 와 무기 스탯 (힘·민첩 중 큰 것) 이 비슷할수록 최대 +mix (작은 쪽 ÷ 큰 쪽 × mix) — 마력 2 : 무기 스탯 1 에서 가장 셈.
  //   40렙 (125 포인트) 어떻게 나눠도 적용 스탯이 125 를 넘지 않음 (마력만 125 · 마력 83 + 힘 42 → 124.6, 한 스탯 캐릭터의 자기 룬과 같은 줄)
  spellblade: { stat: "int", name: "마검사", coef: { int: 1, str: 0.5, dex: 0.5 }, mix: 0.2, ratio: 2 },
  // v2.14.0 (사용자 요청 "마검사가 쓸 수 있는 컨셉에 룬 한 쌍 더 · 마력 기반 룬도 하나 더" · 고른 것: 새 계열 둘 · 원소 검사 · 정령 소환)
  //   원소 검사: 마검사와 같은 적용 스탯 (마력 + 힘·민첩 절반, 마력 2 : 힘 1 에서 최대 +20%) — 검에 불·얼음·번개를 실어 벰
  element: { stat: "int", name: "원소 검사", coef: { int: 1, str: 0.5, dex: 0.5 }, mix: 0.2, ratio: 2 },
  //   정령사: 마력 × 1.0 — 정령을 불러 몇 초 동안 대신 싸우게 함 (RUNE_SPIRITS)
  spirit: { stat: "int", name: "정령사", coef: { int: 1 } },
  // v2.15.0 중력 (사용자 요청 "새로운 마법 룬으로 중력 마법" · 고른 것: 새 계열 5종 · "띄우고 내리꽂거나 끌어모아서 터트리는 느낌"): 마력 × 1.0 (RUNE_GRAVITY)
  gravity: { stat: "int", name: "중력", coef: { int: 1 } },
  // v2.24.0 (사용자 요청 "신체 강화나 평타 강화 같은 룬도 추가" · 고른 것: 새 계열 둘 · 10종, 적용 스탯 = 가장 높은 스탯):
  //   best = 적용 스탯이 네 스탯 중 가장 높은 것 × 1.0 (누구나 제 힘으로 씀 — 다른 스탯 0.85 가 아님). coef 는 쓰지 않음 (표시용)
  //   신체 강화: 몇 초 동안 몸이 강해짐 (피해 감소 · 이동 속도 · 회복 · 모든 스탯) — 체력 룬처럼 피해 대신 버팀 (RUNE_BODY)
  body: { stat: "vit", name: "신체 강화", coef: { vit: 1 }, best: true },
  //   평타 강화: 몇 초 · 몇 번 동안 평타 (근접 · 활 · 완드) 가 강해짐 — 버프 동안 더 들어가는 피해 ≈ 그 등급 룬 한 번 (RUNE_STRIKE)
  strike: { stat: "str", name: "평타 강화", coef: { str: 1 }, best: true },
};
export const RUNE = {
  perPoint: 0.02, // 적용 스탯 1포인트당 위력 +2% (네 스탯 같음)
  offStat: 0.85, // 룬 스탯이 아닌 스탯의 계수
  ticketItem: "rpg:rune_ticket",
  ticketName: "룬 뽑기권",
  ticketIcon: "textures/items/rpg/rune_ticket",
  ticketLore: ["§r§7보스·퀘스트·룬술사에게서 얻음", "§r§7들고 누르면 룬 하나를 뽑습니다", "§r§7일반 50% · 희귀 28% · 고귀 14%", "§r§7영웅 6.5% · 초월 1.5%", "§r§7계열 16개 중 하나 (같은 확률)"],
  // v2.7.0 고급 룬 뽑기권 (룬술사에게서 룬 조각으로 삼, 메인 퀘스트 마지막·왕실 퀘스트 보상): 일반 룬이 나오지 않음 (advWeights, 합 100)
  advTicketItem: "rpg:high_rune_ticket",
  advTicketName: "고급 룬 뽑기권",
  advTicketIcon: "textures/items/rpg/high_rune_ticket",
  advTicketLore: ["§r§7룬술사에게서 사는 고급 뽑기권", "§r§7들고 누르면 룬 하나를 뽑습니다", "§r§7희귀 50% · 고귀 30%", "§r§7영웅 15% · 초월 5%", "§r§7일반 룬은 나오지 않음"],
  advWeights: { common: 0, rare: 50, noble: 30, hero: 15, transcend: 5 },
  hitPlayers: false, // 룬 공격이 다른 플레이어에게도 맞는지
  hudEvery: 10, // 룬을 들고 있으면 이 주기(틱)로 화면 하단에 재사용 대기
  broadcastFrom: "hero", // 이 등급 이상을 뽑으면 모두에게 알림
  undeadBonus: 1.5, // 성기사 룬(신성 피해)이 언데드(좀비·스켈레톤 등)에게 주는 피해 배수
  // 계열별 연출 색 [빛, 심지]
  color: {
    str: [[1.0, 0.42, 0.18], [1.0, 0.86, 0.62]],
    dex: [[0.35, 1.0, 0.55], [0.9, 1.0, 0.92]],
    vit: [[1.0, 0.42, 0.68], [1.0, 0.9, 0.95]],
    int: [[0.45, 0.62, 1.0], [0.86, 0.92, 1.0]],
    axe: [[0.95, 0.2, 0.14], [1.0, 0.78, 0.62]], // 핏빛
    bow: [[0.66, 1.0, 0.28], [0.95, 1.0, 0.82]], // 연두
    holy: [[1.0, 0.82, 0.32], [1.0, 0.98, 0.86]], // 금빛
    storm: [[0.62, 0.48, 1.0], [0.9, 0.92, 1.0]], // 보랏빛 번개
    assassin: [[0.84, 0.18, 0.96], [1.0, 0.84, 1.0]], // 자홍 그림자
    blade: [[0.3, 0.92, 0.92], [0.9, 1.0, 1.0]], // 청백 검기
    spellblade: [[0.5, 0.3, 1.0], [0.55, 1.0, 1.0]], // v2.13.0 마검사: 남보라 마력 [빛] + 청록 칼날 [심지]
    element: [[1.0, 0.58, 0.22], [1.0, 0.95, 0.82]], // v2.14.0 원소 검사 (뽑기 연출 등 계열 색 — 룬마다는 불·얼음·번개 색, RUNE_ELEMENTS)
    spirit: [[0.42, 1.0, 0.76], [0.92, 1.0, 0.97]], // v2.14.0 정령사 (계열 색 — 정령마다는 RUNE_SPIRITS 의 색)
    gravity: [[0.6, 0.24, 0.9], [1.0, 0.86, 0.98]], // v2.15.0 중력: 자줏빛 [빛] + 분홍빛 흰 [심지], 어두운 핵은 RUNE_GRAVITY.void
    body: [[1.0, 0.66, 0.2], [1.0, 0.96, 0.82]], // v2.24.0 신체 강화: 호박빛 기운 (룬마다 accent)
    strike: [[0.78, 0.86, 1.0], [1.0, 1.0, 1.0]], // v2.24.0 평타 강화: 칼날 빛 (룬마다 accent)
  },
};
/**
 * 룬 80종 (v2.24.0, 16계열 × 5등급): [계열][등급]. fn = runes.js 의 연출·효과 함수, 나머지는 그 함수의 수치.
 * name: 이름, desc: 설명 한 줄, use: 용도 (아이템 설명 한 줄 50바이트 안)
 * mult: 한 번 맞을 때 위력 배수, hits: 맞는 횟수 (한 대상 합 = mult × hits)
 * cone (대상을 고르는 룬): 조준한 몹이 없을 때 시선에서 cone 도 안의 가장 가운데 몹 (활 조준 보정과 같은 방식)
 */
export const RUNES = {
  str: {
    common: { fn: "groundSlam", name: "대지 강타", desc: "앞쪽 땅을 내려쳐 충격파", use: "근접 광역·띄우기", mult: 4, dist: 2.2, radius: 2.8, lift: 0.35 },
    rare: { fn: "whirlwind", name: "회오리 베기", desc: "제자리에서 3번 휘몰아치기", use: "주위 광역·3타", mult: 2.35, hits: 3, gap: 10, radius: 3.5 },
    noble: { fn: "fissure", name: "대지 균열", desc: "앞으로 땅이 갈라지며 솟구침", use: "직선 광역", mult: 12, length: 9, width: 1.4, lift: 0.6 },
    hero: { fn: "titanSmash", name: "거인의 일격", desc: "거대한 망치로 내려찍기", use: "전방 대형 광역", mult: 20, dist: 3.5, radius: 5, delay: 8, lift: 0.8 },
    transcend: { fn: "heavenFall", name: "천붕", desc: "하늘에서 거대한 검이 떨어짐", use: "원거리 대형 광역·둔화", mult: 32, range: 22, radius: 6, delay: 24, lift: 1.0 },
  },
  dex: {
    common: { fn: "galeSlash", name: "질풍 베기", desc: "앞으로 돌진하며 베기", use: "돌진·이동", mult: 4, dash: 5, width: 1.3 },
    rare: { fn: "bladeFan", name: "칼날 부채", desc: "칼날 7개를 부채꼴로 던짐", use: "부채꼴 원거리", mult: 7, blades: 7, spread: 50, range: 10 },
    noble: { fn: "shadowRaid", name: "그림자 습격", desc: "분신이 적 4명을 기습", use: "다수 추적", mult: 12, targets: 4, range: 9, cone: 70, gap: 4 },
    hero: { fn: "bladeStorm", name: "칼바람 폭풍", desc: "칼바람을 두르고 주위를 벰", use: "주위 5타·이동 속도", mult: 4, hits: 5, gap: 10, radius: 3.5, speedTicks: 50 },
    transcend: { fn: "shadowless", name: "무영검", desc: "적을 멈추고 칼자국 폭발", use: "다수 제압·폭발", mult: 32, range: 12, cone: 140, freeze: 30, cuts: 8, delay: 26, maxTargets: 8 },
  },
  vit: {
    common: { fn: "guardShout", name: "수호의 외침", desc: "주위 적 도발, 받는 피해 -30%", use: "도발·피해 감소", mult: 2.5, radius: 6, reduce: 0.3, ticks: 80 },
    rare: { fn: "lifeSpring", name: "생명의 샘", desc: "체력 회복 (주위 아군 절반)", use: "회복·아군 회복", heal: 0.14, pulses: 4, gap: 10, radius: 6, share: 0.5 },
    noble: { fn: "ironSkin", name: "강철 피부", desc: "6초 받는 피해 -45%, 반사", use: "피해 감소·반사", mult: 3, radius: 3, reduce: 0.45, reflect: 1.5, ticks: 120 },
    // 체력 룬은 방어·회복이 있어 피해 몫을 다른 스탯의 약 2/3 로
    hero: { fn: "bulwark", name: "불굴의 방벽", desc: "방벽 안 아군 보호, 적은 피해", use: "아군 보호 구역", mult: 2.2, radius: 4.5, reduce: 0.4, ticks: 120, pulse: 20 },
    transcend: { fn: "undyingPact", name: "불사의 맹약", desc: "8초 쓰러지지 않고 회복·반격", use: "생존·회복", mult: 2.5, radius: 4, heal: 0.05, ticks: 160, pulse: 20 },
  },
  int: {
    common: { fn: "fireball", name: "화염구", desc: "불덩이를 날려 폭발", use: "원거리 폭발·불", mult: 4, range: 20, speed: 24, radius: 2.2, fire: 3 },
    rare: { fn: "frostNova", name: "서리 폭발", desc: "주위를 얼려 피해와 둔화", use: "주위 광역·둔화", mult: 7, radius: 4.2, slowTicks: 60, slowAmp: 1 },
    noble: { fn: "fireStorm", name: "화염 폭풍", desc: "조준한 곳에 불기둥 소용돌이", use: "지속 광역·끌어당김", mult: 2, hits: 6, gap: 10, range: 18, radius: 3.2, pull: 0.25 },
    hero: { fn: "blizzard", name: "블리자드", desc: "조준한 곳에 눈보라 4초", use: "지속 광역·둔화", mult: 2.5, hits: 8, gap: 10, range: 18, radius: 5, slowTicks: 40, slowAmp: 2 },
    transcend: { fn: "meteor", name: "메테오", desc: "조준한 곳에 운석 낙하", use: "원거리 대형 광역·불", mult: 32, range: 24, radius: 6, delay: 30, fire: 5, lift: 0.9 },
  },
  // ---- v1.4.0 새 계열
  // 도끼 (힘 1.0 + 체력 0.5): 무겁게 찍고, 피를 빨고, 버팀
  axe: {
    common: { fn: "axeBoomerang", name: "회전 도끼", desc: "도끼를 던져 왕복하며 벰", use: "원거리·왕복 2타", mult: 2, hits: 2, range: 10, width: 1.5, speed: 28, hover: 10 },
    rare: { fn: "bloodCleave", name: "피의 도끼질", desc: "앞을 찍고 맞힌 적마다 체력 회복", use: "근접 광역·흡혈", mult: 7, radius: 3.5, cone: 110, heal: 0.04, healMax: 5 },
    noble: { fn: "leapSlam", name: "도약 찍기", desc: "조준한 곳으로 뛰어 내려찍기", use: "접근·광역·둔화", mult: 12, range: 9, radius: 3.5, lift: 0.5, slowTicks: 40 },
    hero: { fn: "execute", name: "처형", desc: "한 대상 강타, 약하면 1.5배", use: "단일 마무리", mult: 20, range: 5, cone: 30, below: 0.35, bonus: 1.5, delay: 8 },
    transcend: { fn: "berserker", name: "광전사의 혼", desc: "6초 광폭화, 주위 난타·흡혈", use: "난전·흡혈·피해 감소", mult: 5.33, hits: 6, gap: 20, radius: 4, reduce: 0.25, heal: 0.02, healMax: 5 },
  },
  // 궁수 (민첩): 멀리서 꿰뚫고, 묶고, 빠지며 쏨
  bow: {
    common: { fn: "pierceArrow", name: "관통 화살", desc: "일직선으로 꿰뚫는 화살", use: "직선 원거리 관통", mult: 4, range: 18, width: 1.0 },
    rare: { fn: "pinArrow", name: "속박 화살", desc: "대상을 땅에 묶어 3초 멈춤", use: "단일 속박", mult: 7, range: 18, cone: 20, root: 60 },
    noble: { fn: "backstep", name: "도약 사격", desc: "뒤로 뛰며 화살 3발", use: "후퇴·다수 사격", mult: 12, back: 5, arrows: 3, range: 16, cone: 80, gap: 3 },
    hero: { fn: "snipe", name: "저격", desc: "조준 후 40칸 강한 한 발", use: "초장거리 단일", mult: 20, range: 40, cone: 8, charge: 16, push: 1.2 },
    transcend: { fn: "skyBow", name: "천궁", desc: "하늘의 활이 화살 5발 낙하", use: "다수 추적·광역", mult: 32, range: 16, targets: 5, splash: 2, draw: 16, gap: 3 },
  },
  // 성기사 (체력): 보호막·정화·기절, 신성 피해 (마법, 언데드 1.5배). 피해 몫은 체력 룬처럼 약 2/3
  holy: {
    common: { fn: "lightBarrier", name: "빛의 보호막", desc: "나와 아군에게 보호막", use: "보호막·아군 보호", mult: 2.5, radius: 5, hitRadius: 3, shield: 0.15, ticks: 160 },
    rare: { fn: "purify", name: "정화의 빛", desc: "회복하고 나쁜 효과 제거", use: "회복·정화", mult: 4, heal: 0.1, radius: 8, hitRadius: 4 },
    noble: { fn: "judgeHammer", name: "심판의 망치", desc: "망치가 3명을 튀며 기절", use: "기절·연쇄", mult: 5, range: 16, cone: 20, bounces: 2, bounceRange: 5, stun: 40, speed: 24 },
    hero: { fn: "divineSmite", name: "천벌", desc: "빛기둥: 적 피해, 아군 회복", use: "원거리 광역·아군 회복", mult: 13, range: 18, radius: 4, delay: 12, heal: 0.15 },
    transcend: { fn: "archangel", name: "대천사 강림", desc: "8초 날개: 아군 회복·보호막", use: "아군 회복·보호막·광역", mult: 2.5, hits: 8, gap: 20, radius: 6, allyRadius: 8, heal: 0.03, shield: 0.04, cap: 0.25 },
  },
  // 번개 (마력): 튀고, 순간이동하고, 기절
  storm: {
    common: { fn: "chainLightning", name: "연쇄 번개", desc: "번개가 적 4명에게 튐", use: "연쇄 다수", mult: 4, range: 18, cone: 20, chains: 3, chainRange: 6, gap: 3 },
    rare: { fn: "blink", name: "섬광 이동", desc: "8칸 순간이동, 지나간 적 감전", use: "이동·도주·직선", mult: 7, dist: 8, width: 1.6 },
    noble: { fn: "staticPrison", name: "뇌전 감옥", desc: "조준한 곳의 적 2.5초 기절", use: "광역 기절", mult: 12, range: 16, radius: 4, delay: 10, stun: 50 },
    hero: { fn: "thundercloud", name: "뇌운", desc: "4초간 번개가 적에게 떨어짐", use: "지속 광역·무작위", mult: 2.5, hits: 8, gap: 10, range: 18, radius: 6, splash: 2, height: 7 },
    transcend: { fn: "thunderSpear", name: "뇌신의 창", desc: "관통하는 번개 창, 연쇄 폭발", use: "직선 관통·연쇄·기절", mult: 32, chainMult: 16, range: 24, width: 1.6, charge: 20, chainRange: 6, stun: 20 },
  },
  // ---- v1.6.0 새 계열
  // 암살자 (민첩): 파고들고, 독을 묻히고, 숨고, 난도질. 맞힌 적에게 암살 표식. 2종 이상 모으면 이어 쓰기 콤보 (RUNE_COMBO)
  // span = 누른 순간부터 마지막 타격까지 틱, 가장 늦을 때 기준 (독 표창: 14칸 날아가는 10틱 + 중독 2번 20틱). 콤보로 이어 쓴 룬이 그 10틱 뒤에 나감
  assassin: {
    common: { fn: "backstab", name: "배후 일격", desc: "대상 뒤로 순간이동해 찌름", use: "접근·단일", mult: 4, range: 8, cone: 25, span: 0 },
    rare: { fn: "poisonStars", name: "독 표창", desc: "표창 3개, 맞은 적은 1초 중독", use: "원거리·지속 피해", mult: 4, dot: 1.5, dots: 2, dotGap: 10, stars: 3, spread: 30, range: 14, speed: 30, span: 30 },
    noble: { fn: "smokeBomb", name: "연막", desc: "연막 속 적을 3번 베고 몸을 숨김", use: "주위 제압·은신", mult: 4, hits: 3, gap: 10, radius: 4, hide: 60, slowTicks: 60, span: 20 },
    hero: { fn: "shadowFlurry", name: "그림자 난도질", desc: "대상에 달라붙어 5번 난도질", use: "단일 연타", mult: 4, hits: 5, gap: 10, range: 7, cone: 30, span: 40 },
    transcend: { fn: "moonAssassin", name: "월광 암살", desc: "달빛 표식 뒤 등 뒤에서 X 베기", use: "원거리 단일·주위", mult: 32, range: 14, cone: 20, delay: 20, splash: 3, splashMult: 16, span: 20 },
  },
  // 검사 (힘 1.0 + 민첩 0.5): 발도·검기·반격·연속 베기
  blade: {
    common: { fn: "quickDraw", name: "발도 일섬", desc: "칼을 뽑으며 앞을 한 번에 벰", use: "근접 전방 광역", mult: 4, range: 4.2, cone: 120 },
    rare: { fn: "swordWave", name: "검기 날리기", desc: "초승달 검기가 날아가며 관통", use: "직선 원거리 관통", mult: 7, range: 14, width: 1.6, speed: 26 },
    noble: { fn: "counterStance", name: "반격 자세", desc: "막는 자세, 때린 적을 바로 반격", use: "반격·피해 감소", mult: 12, ticks: 50, reduce: 0.5, range: 4.5, cone: 120 },
    hero: { fn: "tripleCut", name: "삼단베기", desc: "가로·세로 베기 뒤 찌르기", use: "전방 3연타", mult: 6.67, hits: 3, gap: 10, range: 4.2, cone: 110, thrust: 6.5 },
    transcend: { fn: "heavenSplit", name: "천지개벽", desc: "검기를 모아 거대한 검기 발사", use: "직선 대형 관통", mult: 32, charge: 20, range: 18, width: 2.6, speed: 30, knock: 1.0 },
  },
  // ---- v2.13.0 마검사 (마력 + 힘·민첩 절반, 남보라 마력 + 청록 칼날): 검 (공격 피해, 노란 숫자) 과 마력 (마법 피해, 파란 숫자) 을 섞어 씀.
  //   imbue = 마검 부여: 쓰면 룬의 마지막 타격 (span 틱, 늦을 때) 뒤 RUNE_SPELLBLADE.imbue.ticks 까지 다음 평타 hits 번에 위력 × imbue 를 더함 (룬 한 대상 피해 합의 약 1/4 을 세 번에 나눔)
  //   hits 가 있는 룬은 앞 절반 검 · 뒤 절반 마력 (마검 결계는 번갈아, kind "magic" = 모두 마력), gap 틱 간격 (몹 무적 시간)
  spellblade: {
    common: { fn: "arcaneCut", name: "마력 베기", desc: "앞을 베고 그 자리에 마력 폭발", use: "근접 전방·검+마력", mult: 2, hits: 2, gap: 10, range: 4.2, cone: 110, imbue: 0.35, span: 10 },
    // v2.13.1 마나 블라스터 (사용자 요청 "마력 쪽 지속 딜링기" · 고른 것: 마나 블라스터 · 마검사 희귀 칸 — v2.13.0 환영검 대신):
    //   hits × gap 틱 동안 조준점을 따라가는 마력 광선 (range 칸, 벽 앞까지), gap 틱마다 광선 폭 width 안의 적 모두에게 위력 × mult (마법)
    rare: { fn: "manaBlaster", name: "마나 블라스터", desc: "조준점을 따라가는 마력 광선 3초", use: "지속 관통 광선 7타", kind: "magic", mult: 1, hits: 7, gap: 10, range: 16, width: 0.9, imbue: 0.6, span: 62 },
    noble: { fn: "riftSlash", name: "차원 베기", desc: "돌진해 베고 갈라진 자리가 폭발", use: "돌진·직선 2타", mult: 6, hits: 2, dist: 8, width: 1.6, delay: 12, imbue: 1.0, span: 12 },
    hero: { fn: "orbitBlades", name: "마검 결계", desc: "마력 검 넷이 4초 동안 주위를 벰", use: "주위 지속 8타", mult: 2.5, hits: 8, gap: 10, radius: 3.6, swords: 4, orbit: 2.0, imbue: 1.7, span: 70 },
    transcend: { fn: "spellbladeRelease", name: "마검 해방", desc: "거대한 마력 검으로 십자 베기", use: "전방 대형 십자 2타", mult: 16, hits: 2, charge: 16, gap: 10, range: 9, cone: 150, length: 12, width: 2.4, cross: 3, imbue: 2.7, span: 26 },
  },
  // ---- v2.14.0 원소 검사 (마력 + 힘·민첩 절반): 검에 불·얼음·번개를 실어 벰. el = 원소 (fire 불붙음 · ice 구속 · thunder 새 적에게 튐 · all 셋)
  //   kind: sword = 검만 (공격 피해, 노란 숫자) · magic = 원소만 (마법 피해, 파란 숫자) · 없음 = 섞임. 여러 번 때리면 gap 틱 간격 (몹 무적 시간)
  element: {
    common: { fn: "flameSlash", name: "화염 베기", desc: "불꽃 검으로 앞을 베어 불태움", use: "근접 전방·불", kind: "sword", el: "fire", mult: 4, range: 4.2, cone: 120, fire: 4 },
    rare: { fn: "frostThrust", name: "서리 일섬", desc: "찌른 줄에 얼음 가시가 솟음", use: "직선 2타·얼음", el: "ice", mult: 3.5, hits: 2, gap: 10, range: 7, width: 1.3, slowTicks: 60, slowAmp: 1 },
    noble: { fn: "thunderSlash", name: "뇌광 베기", desc: "벤 적에게 번개가 떨어져 튐", use: "근접 전방·번개 연쇄", el: "thunder", mult: 6, hits: 2, gap: 10, range: 4.5, cone: 120, chain: 6, chainMult: 6 },
    hero: { fn: "triElement", name: "삼원소 연격", desc: "불·얼음·번개로 세 번 연속 공격", use: "전방 3연타·세 원소", el: "all", mult: 6.67, hits: 3, gap: 10, range: 4.2, cone: 110, thrust: 6.5, fire: 3, slowTicks: 40, slowAmp: 1, chain: 4, chainMult: 3.33 },
    transcend: { fn: "elementRelease", name: "원소 해방", desc: "세 원소 검기를 부채꼴로 날림", use: "부채꼴 관통·세 원소", kind: "magic", el: "all", mult: 32, charge: 16, range: 18, width: 2.0, spread: 22, speed: 30, fire: 5, slowTicks: 60, slowAmp: 2, chain: 6, chainMult: 16 },
  },
  // ---- v2.14.0 정령사 (마력): 정령을 불러 ticks 틱 동안 대신 싸우게 함 (RUNE_SPIRITS). spirits = 부르는 정령, hits = 모든 정령의 공격 횟수 합 (정령마다 hits ÷ 정령 수),
  //   gap = 한 정령의 공격 간격 (틱), first = 처음 공격까지, range = 내 주위 공격 범위. 한 적에게 들어가는 피해 합 = mult × hits (등급 예산)
  spirit: {
    common: { fn: "summonSpirits", name: "불의 정령", desc: "샐러맨더가 불덩이를 던짐", use: "소환 · 원거리 폭발 4번", spirits: ["salamander"], mult: 1, hits: 4, gap: 12, first: 8, ticks: 60, range: 12 },
    rare: { fn: "summonSpirits", name: "물의 정령", desc: "운디네가 물줄기를 쏨", use: "소환 · 밀치는 물줄기 3번", spirits: ["undine"], mult: 2.33, hits: 3, gap: 20, first: 8, ticks: 70, range: 11 },
    noble: { fn: "summonSpirits", name: "바람의 정령", desc: "실프가 적을 꿰뚫으며 돌진", use: "소환 · 관통 돌진 4번", spirits: ["sylph"], mult: 3, hits: 4, gap: 18, first: 8, ticks: 80, range: 12 },
    hero: { fn: "summonSpirits", name: "땅의 정령", desc: "노움이 바위 가시를 솟게 함", use: "소환 · 범위 가시 4번", spirits: ["gnome"], mult: 5, hits: 4, gap: 20, first: 10, ticks: 90, range: 12 },
    transcend: { fn: "summonSpirits", name: "정령왕의 부름", desc: "네 정령이 함께 싸움", use: "소환 넷 · 정령마다 2번", spirits: ["salamander", "undine", "sylph", "gnome"], mult: 4, hits: 8, gap: 36, first: 10, stagger: 10, ticks: 100, range: 14 },
  },
  // ---- v2.15.0 중력 (마력): 끌어모아 터뜨림 · 띄우고 내리꽂기 (RUNE_GRAVITY). 한 적에게 들어가는 피해 합 = mult × hits + crush (등급 예산)
  //   pull = 끌어당기는 반지름, pullTicks = 끌어당기는 틱 · lift = 띄우는 틱 (liftAmp = 공중 부양 세기), gap = 띄운 뒤 내리꽂기까지 · 펄스 간격,
  //   crush = 마지막 붕괴 · 내리꽂기 배율 (crushName), radius = 피해 반지름, height = 블랙홀 핵 높이,
  //   minDist = 조준점이 이보다 가까우면 보는 방향으로 이만큼 밀어냄 (가까이에 쓴 중력장 · 블랙홀이 1인칭 화면을 덮지 않게)
  gravity: {
    common: { fn: "gravityOrb", name: "중력탄", desc: "끌어당긴 뒤 터지는 중력 구체", use: "원거리 · 끌어모아 폭발", mult: 4, range: 18, speed: 22, pull: 3.6, pullTicks: 10, radius: 2.6 },
    rare: { fn: "liftSlam", name: "공중 부양", desc: "앞의 적을 띄웠다가 내리꽂음", use: "전방 띄우기 · 내리꽂기", mult: 3.5, hits: 2, gap: 18, lift: 18, liftAmp: 2, range: 6.5, cone: 100 },
    noble: { fn: "gravityWell", name: "중력장", desc: "끌어모은 뒤 붕괴하는 중력장", use: "지속 끌어당김 · 붕괴", mult: 1.2, hits: 5, gap: 10, crush: 6, crushName: "붕괴", range: 18, minDist: 4, radius: 5, pull: 6, slowAmp: 1 },
    hero: { fn: "gravityReverse", name: "중력 역전", desc: "주위의 적을 띄웠다가 내리꽂음", use: "주위 띄우기 · 내리꽂기", mult: 4, crush: 16, crushName: "내리꽂기", gap: 24, lift: 22, liftAmp: 1, radius: 5.5, slowTicks: 40, slowAmp: 1 },
    transcend: { fn: "blackHole", name: "블랙홀", desc: "모든 것을 빨아들여 띄운 뒤 붕괴", use: "빨아들이기 · 붕괴", mult: 2, hits: 6, gap: 10, crush: 20, crushName: "붕괴", charge: 16, range: 22, minDist: 6, radius: 8, pull: 9, height: 2.6 },
  },
  // ---- v2.24.0 신체 강화 (가장 높은 스탯 × 1.0): ticks 틱 동안 몸이 강해짐 — 몸에 기운 (accent 색). 같은 룬을 다시 쓰면 처음부터, 다른 등급은 함께 (스탯은 가장 큰 것, 감소는 가장 센 것, 회복은 더함)
  //   reduce = 받는 피해 감소, speed = 이동 속도 효과 세기 (0 = I, 1 = II), steadfast = 보스 · 몬스터 기술의 밀치기 · 끌어당김을 받지 않음,
  //   heal = pulse 틱마다 최대 체력 × heal × 회복 배율, statUp = 모든 스탯 + 비율 (죽거나 나가면 끝), mult · radius = 쓸 때 주위 충격파
  body: {
    common: { fn: "fightingSpirit", name: "투지", desc: "5초 동안 빨라지고 덜 다침", use: "이동 속도 · 피해 감소", ticks: 100, reduce: 0.15, speed: 1, accent: [1.0, 0.56, 0.16] },
    rare: { fn: "steelBody", name: "강철 근육", desc: "7초 동안 강철 같은 몸", use: "감소 · 밀려나지 않음", ticks: 140, reduce: 0.25, steadfast: true, accent: [0.62, 0.78, 1.0] },
    noble: { fn: "regenBody", name: "재생", desc: "9초 동안 매초 체력 회복", use: "회복 · 이동 속도", ticks: 180, heal: 0.02, pulse: 20, speed: 0, accent: [0.42, 1.0, 0.5] },
    hero: { fn: "warGod", name: "투신", desc: "12초 동안 모든 스탯 +15%", use: "모든 스탯 · 피해 감소", ticks: 240, statUp: 0.15, reduce: 0.2, accent: [1.0, 0.3, 0.2] },
    transcend: { fn: "awakening", name: "초인 각성", desc: "15초 각성: 스탯 +25% · 재생", use: "스탯 · 재생 · 충격파", ticks: 300, statUp: 0.25, reduce: 0.3, heal: 0.015, pulse: 20, mult: 8, radius: 5, accent: [1.0, 0.86, 0.4] },
  },
  // ---- v2.24.0 평타 강화 (가장 높은 스탯 × 1.0): ticks 틱 동안 (hits 가 있으면 그 번까지) 평타 (근접 · 활 · 완드) 에 위력 × bonus 를 더함 (평타와 한 번에 — 몹 무적 시간에 막히지 않음).
  //   버프 동안 한 대상에게 더 들어가는 피해 ≈ 그 등급 룬 한 번 (hits × bonus = 4 · 12 · 20 · 32, 연타는 7초 동안 근접 약 14번 × echo).
  //   rangedCd = 활 · 완드 재사용 배율, echo = 근접 평타마다 위력 × echo 를 더하고 echoRange 칸 안 옆의 적 하나에게도 같은 피해,
  //   cleave · cone = 근접 평타가 앞 부채꼴 (반지름 · 각) 의 다른 적도 벰 (활 · 완드는 맞은 적 뒤 splash 칸 부채꼴), heal = 맞힐 때마다 최대 체력 × heal × 회복 배율,
  //   wave · width · speed = 평타마다 거대한 검기 (길이 · 폭 · 칸/초 — 맞은 적 너머의 적), crit = 평타 치명타 확률 + (최대 RUNE_STRIKE.critCap)
  strike: {
    common: { fn: "edgeHone", name: "날 세우기", desc: "다음 평타 3번이 더 아픔", use: "평타 3번 강화", hits: 3, ticks: 120, bonus: 1.33, accent: [0.86, 0.94, 1.0] },
    rare: { fn: "flurry", name: "연타", desc: "활·완드 빠르게 · 근접 잔상", use: "평타 빠르게 · 잔상", ticks: 140, rangedCd: 0.6, echo: 0.5, echoRange: 3, accent: [0.5, 0.95, 1.0] },
    noble: { fn: "cleave", name: "가르기", desc: "평타가 앞 부채꼴의 적도 벰", use: "평타 9번 광역", hits: 9, ticks: 180, bonus: 1.33, cleave: 4, cone: 120, splash: 3.5, accent: [0.72, 0.82, 1.0] },
    hero: { fn: "bloodBlade", name: "피의 칼날", desc: "평타 추가 피해, 맞히면 회복", use: "평타 12번 강화 · 흡혈", hits: 12, ticks: 240, bonus: 1.67, heal: 0.015, accent: [1.0, 0.16, 0.22] },
    transcend: { fn: "swordGod", name: "검신 강림", desc: "평타마다 거대한 검기 · 치명타", use: "평타 16번 · 관통 검기", hits: 16, ticks: 300, bonus: 2, wave: 10, width: 2.2, speed: 30, crit: 0.2, accent: [1.0, 0.84, 0.36] },
  },
};

/**
 * v2.24.0 평타 강화 룬 (RUNES.strike): 더한 피해의 숫자 앞 표시 · 연타 근접 잔상 수 어림 (설명의 피해 합) · 치명타 확률 최대
 */
export const RUNE_STRIKE = { family: "strike", mark: "§6강화 ", echoHits: 14, critCap: 0.95 };
/** v2.24.0 신체 강화 룬 (RUNES.body) */
export const RUNE_BODY = { family: "body" };

/**
 * v2.14.0 원소 검사의 원소: 이름 · 색 [빛, 심지] · 피해 숫자 앞 표시 없음 (검 = 노란 숫자 · 원소 = 파란 숫자)
 */
export const RUNE_ELEMENTS = {
  fire: { name: "불", text: "§6", color: [[1.0, 0.46, 0.12], [1.0, 0.9, 0.55]] },
  ice: { name: "얼음", text: "§b", color: [[0.55, 0.86, 1.0], [0.93, 0.98, 1.0]] },
  thunder: { name: "번개", text: "§e", color: [[1.0, 0.86, 0.25], [1.0, 1.0, 0.82]] },
};

/**
 * v2.14.0 정령사의 정령 (스크립트가 움직이고 파티클로 그림 — 엔티티가 아님): 이름 · 색 [빛, 심지] · 공격
 * - salamander 불덩이: 날아가 반지름 radius 폭발 + 불붙음 fire 초
 * - undine 물줄기: 대상 둘레 반지름 radius 물보라 + 밀침 knock + 구속 I slowTicks
 * - sylph 돌진: 정령이 대상을 꿰뚫고 pierce 칸 너머까지 날아감 (폭 width 의 적 모두) + 띄움
 * - gnome 바위 가시: 대상 발밑에 warn 틱 예고 → 반지름 radius 가시 + 구속 II slowTicks
 * speed = 날아가는 빠르기 (칸/초)
 */
export const RUNE_SPIRITS = {
  salamander: { name: "샐러맨더", text: "§6", color: [[1.0, 0.42, 0.1], [1.0, 0.88, 0.5]], radius: 1.6, fire: 2, speed: 18 },
  undine: { name: "운디네", text: "§9", color: [[0.25, 0.58, 1.0], [0.82, 0.94, 1.0]], radius: 1.8, knock: 0.9, slowTicks: 20, speed: 24 },
  sylph: { name: "실프", text: "§a", color: [[0.45, 1.0, 0.62], [0.92, 1.0, 0.94]], pierce: 3, width: 1.2, speed: 28 },
  gnome: { name: "노움", text: "§6", color: [[0.86, 0.6, 0.28], [1.0, 0.9, 0.66]], radius: 2.2, warn: 8, slowTicks: 30 },
};

/**
 * v2.15.0 중력 룬 (사용자가 고른 느낌: "띄우고 내리꽂거나 끌어모아서 터트리는 느낌의 룬")
 * - 끌어당김: pullEvery 틱마다 (그 몹의 속도를 지우고) 가운데 쪽 가로 힘 = min(pullMax, 거리 × pullK), 가운데 pullStop 칸 안은 멈춤. 벽은 뚫지 않음 (게임 물리)
 * - 띄움: 공중 부양 효과 (룬의 liftAmp · lift 틱) + 그 동안 공격 못 함 (구속 XI · 나약함 V — 기절과 같음). 내리꽂기 = 아래로 slamPower,
 *   땅에 닿을 때 (landWait 틱까지 기다림) 피해. 띄운 적은 떨어질 때 바닐라 낙하 피해를 받지 않음 (룬 피해만)
 * - 보스 · 보스 소품 · 아주 큰 몹 (heavy) 은 끌리거나 뜨지 않음 (피해 · 효과는 같음)
 * - void = 블랙홀 · 구체의 어두운 핵 색
 */
export const RUNE_GRAVITY = {
  void: [0.1, 0.02, 0.16],
  pullEvery: 3,
  pullK: 0.13,
  pullMax: 0.75,
  pullStop: 0.9,
  slamPower: 1.6,
  landWait: 8,
  heavy: ["minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian", "minecraft:ghast", "minecraft:happy_ghast"],
};

/**
 * v2.13.0 마검사 룬의 마검 부여 (사용자가 고른 체계: "룬이 무기에 마력 부여")
 * - 마검사 룬을 쓰면 룬의 마지막 타격 (룬마다 span 틱) 뒤 imbue.ticks 틱까지 다음 평타 (근접·활·완드) imbue.hits 번이 마력 검기를 날림: 그 평타 피해에 위력 × 룬의 imbue 를 더함
 *   (룬 타격 사이에는 그 적이 몹 무적 시간이라 평타가 막히므로 — 특히 4초 동안 주위를 베는 마검 결계. 그 사이에도 다른 적에게는 씀)
 *   (평타와 한 번에 들어가 몹 무적 시간에 막히지 않음. 피해 숫자는 평타 · 보라 "마검 +N" 로 나눠 보임)
 * - 검기는 맞은 적 너머 imbue.pierce 칸 (벽 앞까지) 을 폭 imbue.width 로 꿰뚫어 그 사이 다른 적에게도 같은 피해 (마법)
 *   (멀리서 쏜 활·완드는 맞은 적 앞 imbue.lead 칸에서 검기가 나타남), 초당 imbue.speed 칸
 * - 다른 마검사 룬을 쓰면 새로 (마지막에 쓴 룬의 값, 횟수·시간 처음부터). 죽거나 차원을 옮기면 사라짐
 */
export const RUNE_SPELLBLADE = {
  family: "spellblade",
  imbue: { hits: 3, ticks: 80, pierce: 4, width: 1.2, speed: 30, lead: 3 },
  mark: "§9마검 ", // 마검 부여가 더한 피해 숫자 앞 표시 (마력 스탯 색)
};

/**
 * 암살자 룬 콤보 (v1.6.0)
 * - 암살자 룬을 minOwned 종 이상 가지고 있으면: 다른 암살자 룬을 window 틱(3초) 안에 이어 쓸 때마다 콤보 단계 +1 (같은 룬은 한 번씩)
 * - 콤보 n 단계의 룬 피해 × (1 + step × (n - 1)): 2단계 1.1배 · 3단계 1.2배 · 4단계 1.3배 · 5단계 1.4배
 * - 이어 쓴 룬은 앞 룬의 마지막 타격 gapAfter 틱 뒤에 나감 (몹 무적 시간 10틱에 막히지 않게. 누른 입력은 기억해 둠)
 * - 암살자 룬에 맞은 적은 markTicks 동안 암살 표식 (머리 위 자홍 룬)
 * - 5종을 모두 이으면 마무리 기술 finisher: 표식이 있는 적마다 (range 안, 최대 maxTargets), 표식이 없으면 주위 radius 칸의 적
 */
export const RUNE_COMBO = {
  family: "assassin",
  minOwned: 2,
  window: 60,
  step: 0.1,
  gapAfter: 10,
  markTicks: 100,
  finisher: { name: "그림자 처형", mult: 20, range: 16, radius: 6, maxTargets: 6, gap: 4 },
};

/**
 * 룬 칸을 고르면 바로 사용 (v1.6.0). 플레이어마다 prop 에 "on" · "off" (없으면 auto)
 * auto: 마지막 입력이 터치(모바일)이면 켬. 키보드·게임패드는 끔 (핫바를 굴려 지나가는 룬이 나가지 않게, 누르면 사용)
 */
export const RUNE_QUICK = { prop: "rpg:rune_quick" };

/**
 * 룬술사 (v2.7.0, runesmith.js): 겹치거나 쓰지 않는 룬을 갈아 룬 조각으로, 룬 조각으로 뽑기권을 삼
 * grind: 룬 한 개를 갈면 나오는 조각 (등급), price: 뽑기권 한 장 값 (조각). NPC 태그 rpg_runesmith (설정 창 · /mainnpc 12)
 * 뽑기권을 사서 바로 갈면 손해 (일반 뽑기권 평균 약 4.5조각 < 10, 고급 약 9.4조각 < 40) — 조각을 불려 쓸 수 없음
 */
export const RUNE_SMITH = {
  shardItem: "rpg:rune_shard",
  shardName: "룬 조각",
  shardIcon: "textures/items/rpg/rune_shard",
  shardLore: ["§r§7룬을 갈면 나오는 조각", "§r§7룬술사에게 모아 가면", "§r§7룬 뽑기권으로 바꿔 줌"],
  grind: { common: 1, rare: 3, noble: 8, hero: 20, transcend: 50 },
  price: { ticket: 10, adv: 40 },
};

/**
 * 탈것 (v2.8.0, mount.js): 스탯 창 오른쪽 아래 칸 → 다이어울프를 불러 바로 탐, 내리면 (웅크리기) 사라짐.
 * 탄 동안: 공격 (평타 · 활 · 완드 · 지속 피해로 잡기) · 무기 스킬 · 룬을 쓸 수 없음. 탈것은 다치지 않음 (스스로 공격하지 않음)
 * 모델 · 텍스처 · 애니메이션 = art/mounts/dire_wolf/ (사용자 FantasyMounts 팩의 fw:dire_wolf, 빌드가 이름만 rpg: 로 바꿈 — 원본 팩과 같이 써도 겹치지 않음)
 * speed · jump · seat · box 는 원본 그대로, step 은 v2.8.1 에 더함 (build.py 가 행동 팩 엔티티에 씀)
 */
export const MOUNT = {
  id: "rpg:dire_wolf_mount",
  name: "다이어울프",
  speed: 0.32,
  jump: 0.8,
  seat: [0, 1.52, -0.125],
  box: [1.6, 2],
  /** 점프 없이 걸어 올라가는 높이 (블록, v2.8.1): 한 칸 + 1픽셀 — 한 칸짜리 블록은 그냥 달려서 올라감 (바닐라 엔더맨 · 크리킹과 같은 값. 기본은 반 칸 + 1픽셀) */
  step: 1.0625,
  icon: "textures/rpg_ui/icon_mount",
  /** 연출 색 (얼음 파랑 — 원본 소환 알 색 #5fd8ff) */
  color: [0.37, 0.85, 1.0],
  /** 사라진 뒤 다시 부를 수 있을 때까지 (틱) */
  cooldownTicks: 40,
  /** 부른 뒤 탄 사람을 확인하기 시작할 때까지 (틱) — 그동안 태우기를 다시 시도 */
  graceTicks: 10,
  /** 막힘 안내 간격 (틱) */
  warnEvery: 40,
  /** 탄 사람이 없는 탈것을 찾아 치우는 간격 (틱, 다시 불러온 청크 등) */
  sweepEvery: 40,
};

// ---- 엔더 상자 창 (v2.11.0, scripts/enderui.js · ui/rpg_ender.json): 스탯 창 아랫줄 칸 → 상자 모양 창에 내 엔더 상자 27칸 + 인벤토리 36칸.
// 칸을 누르면 반대쪽으로 옮김 (전부 · 절반 · 1개). 엔더 상자 칸은 스크립트 2.8.0 의 minecraft:ender_inventory (게임 1.26.30 이상)
// v2.10.0 의 '눈앞에 진짜 엔더 상자를 잠깐 놓기' 는 없앰 — 그때 놓여 남은 상자만 치움 (scripts/enderchest.js)
export const ENDER = {
  name: "엔더 상자",
  /** 스탯 창 칸 아이콘 (build.py 가 그림: 상자 모양 + 보랏빛) */
  icon: "textures/rpg_ui/ender_chest",
  /** 창 제목 앞의 표식 (리소스 팩 UI 가 이 표식으로 엔더 상자 창을 그림) */
  marker: "§r§e§n§d§r",
  /** 연출 색 (보랏빛 — 엔더) */
  color: [0.72, 0.38, 1.0],
  /** 옮기는 양 (창 머리줄 단추) */
  modes: ["전부", "절반", "1개"],
  /** v2.10.0 에 놓였던 임시 엔더 상자: 그 블록 */
  block: "minecraft:ender_chest",
  /** …치우기 확인 간격 (틱) · 불러오지 않은 곳에 남은 것을 다시 치워 보는 간격 (틱) */
  checkEvery: 4,
  retryEvery: 40,
};

/** 룬 아이템 ID (계열: str·dex·vit·int·axe·bow·holy·storm) */
export function runeId(family, grade) {
  return `rpg:rune_${family}_${grade}`;
}

// =====================================================================
//  레이드 재료 (v1.1.9 별철 광석, v1.5.0 강화 재료 3종). 레이드 보스 보상으로 줄 아이템
//  레이드가 생기기 전에는 /raidmat 명령어(운영자)나 퀘스트 보상으로 지급
// =====================================================================
export const RAID_MATERIALS = {
  ore: { item: "rpg:star_ore", name: "별철 광석", color: "§e", icon: "textures/items/rpg/star_ore", lore: ["§r§7레이드 보스에게서 나오는 광석", "§r§7영웅 무기의 레벨 제한을 올릴 때", "§r§7대장장이에게 가져가세요"] },
  stone: { item: "rpg:enhance_stone", name: "강화석", color: "§b", icon: "textures/items/rpg/enhance_stone", lore: ["§r§7레이드 보스에게서 나오는 재료", "§r§7무기를 강화할 때마다 씀 (+1~+10)", "§r§7대장장이에게 가져가세요"] },
  bright: { item: "rpg:bright_stone", name: "빛나는 강화석", color: "§d", icon: "textures/items/rpg/bright_stone", lore: ["§r§7레이드 보스에게서 드물게 나옴", "§r§7무기를 +6 이상으로 강화할 때 씀", "§r§7대장장이에게 가져가세요"] },
  scroll: { item: "rpg:guard_scroll", name: "수호 주문서", color: "§6", icon: "textures/items/rpg/guard_scroll", lore: ["§r§7레이드 보스에게서 드물게 나옴", "§r§7+6 이상 강화에 실패해도", "§r§7단계가 떨어지지 않게 지켜 줌", "§r§7(실패했을 때만 사라짐)"] },
};
export const RAID_MATERIAL_ORDER = ["ore", "stone", "bright", "scroll"];

// =====================================================================
//  보스 재료 (v2.0.0): 보스마다 따로 떨어뜨리는 재료 → 대장장이 "보스 무기 제작" (BOSS_CRAFT)
//  - 크라켄: 칠흑 먹물 · 심해의 눈, 거대 웜 (v2.1.0): 파쇄의 송곳니 · 대지의 심장석, 니드호그 (v2.3.0): 세계수의 가지 · 타락한 세계수 뿌리,
//    스콜과 하티 (v2.4.0): 스콜의 불꽃 송곳니 · 하티의 달빛 갈기, 이미르 (v2.5.0): 이미르의 서리 심장 · 태초의 거인 뼈
//  - v2.9.0: 무기마다 여러 보스의 재료 + 일반 몬스터 전리품을 섞어 만듦 (BOSS_CRAFT) — 설명의 "보스 무기 제작" 줄은 BOSS_CRAFT 에서 만듦 (아래 craftLore)
//  - 보스 보상(BOSSES.<키>.loot)에 재료 키로 [최소, 최대, 확률] 을 적으면 줌. 아이콘은 build.py 가 그림 (art/materials/<키>.png 가 있으면 그것)
// =====================================================================
export const BOSS_MATERIALS = {
  kraken_ink: { item: "rpg:kraken_ink", name: "칠흑 먹물", color: "§5", boss: "kraken", icon: "textures/items/rpg/kraken_ink", lore: ["§r§7크라켄에게서 나오는 보스 재료", "§r§7빛을 삼키는 심해의 먹물"] },
  kraken_eye: { item: "rpg:kraken_eye", name: "심해의 눈", color: "§3", boss: "kraken", icon: "textures/items/rpg/kraken_eye", lore: ["§r§7크라켄에게서 나오는 보스 재료", "§r§7아직도 무언가를 지켜보는 눈"] },
  worm_fang: { item: "rpg:worm_fang", name: "파쇄의 송곳니", color: "§e", boss: "great_worm", icon: "textures/items/rpg/worm_fang", lore: ["§r§7거대 웜에게서 나오는 보스 재료", "§r§7바위도 씹어 부수는 송곳니"] },
  worm_heart: { item: "rpg:worm_heart", name: "대지의 심장석", color: "§6", boss: "great_worm", icon: "textures/items/rpg/worm_heart", lore: ["§r§7거대 웜에게서 나오는 보스 재료", "§r§7땅속 깊이 맥박치는 돌"] },
  ygg_branch: { item: "rpg:ygg_branch", name: "세계수의 가지", color: "§a", boss: "nidhogg", icon: "textures/items/rpg/ygg_branch", lore: ["§r§7니드호그가 주는 보스 재료", "§r§7독에 물들지 않은 세계수의 가지"] },
  ygg_root: { item: "rpg:ygg_root", name: "타락한 세계수 뿌리", color: "§5", boss: "nidhogg", icon: "textures/items/rpg/ygg_root", lore: ["§r§7니드호그가 주는 보스 재료", "§r§7니드호그가 갉아 독이 스민 뿌리"] },
  skoll_fang: { item: "rpg:skoll_fang", name: "스콜의 불꽃 송곳니", color: "§6", boss: "skoll_hati", icon: "textures/items/rpg/skoll_fang", lore: ["§r§7스콜과 하티가 주는 보스 재료", "§r§7해를 쫓는 검은 늑대의 송곳니"] },
  hati_mane: { item: "rpg:hati_mane", name: "하티의 달빛 갈기", color: "§b", boss: "skoll_hati", icon: "textures/items/rpg/hati_mane", lore: ["§r§7스콜과 하티가 주는 보스 재료", "§r§7달을 쫓는 흰 늑대의 갈기"] },
  ymir_heart: { item: "rpg:ymir_heart", name: "이미르의 서리 심장", color: "§b", boss: "ymir", icon: "textures/items/rpg/ymir_heart", lore: ["§r§7이미르가 주는 보스 재료", "§r§7얼음처럼 뛰는 거인의 심장"] },
  ymir_bone: { item: "rpg:ymir_bone", name: "태초의 거인 뼈", color: "§f", boss: "ymir", icon: "textures/items/rpg/ymir_bone", lore: ["§r§7이미르가 주는 보스 재료", "§r§7산맥이 된 거인의 뼈 조각"] },
  // v2.12.0 초월 보스 수르트 (사용자 결정 "재료 먼저, 장비는 나중"): 아직 쓰는 무기·갑옷이 없음 (future) — 사용자가 클로드 디자인에서 만든 장비가 들어오면 BOSS_CRAFT 등에 넣음
  muspel_heart: { item: "rpg:muspel_heart", name: "무스펠의 불꽃 심장", color: "§c", boss: "surtr", tier: "transcend", future: true, icon: "textures/items/rpg/muspel_heart", lore: ["§r§7수르트가 주는 초월 보스 재료", "§r§7꺼지지 않는 거인의 불꽃 심장"] },
  laevateinn_shard: { item: "rpg:laevateinn_shard", name: "레바테인 조각", color: "§6", boss: "surtr", tier: "transcend", future: true, icon: "textures/items/rpg/laevateinn_shard", lore: ["§r§7수르트가 주는 초월 보스 재료", "§r§7불꽃 검 레바테인의 부서진 조각"] },
};
export const BOSS_MATERIAL_ORDER = ["kraken_ink", "kraken_eye", "worm_fang", "worm_heart", "ygg_branch", "ygg_root", "skoll_fang", "hati_mane", "ymir_heart", "ymir_bone", "muspel_heart", "laevateinn_shard"];

// ---- 보스 무기 제작 (v2.0.0, 대장장이 세 번째 창): 재료 + 에메랄드 + 별철 광석 → 무기 (강화 0, 설명 포함)
// 영웅 무기는 30렙으로 만들고, 레벨 제한은 대장장이에서 35 → 40 으로 올림 (SMITH). 초월 무기(v2.3.0 니드호그)는 40렙 하나뿐이라 그대로
// v2.5.0: 제작 창을 등급마다 한 쪽 (영웅 30렙 · 초월 40렙, 윗줄 가운데 칸으로 바꿈) — 한 쪽에 9줄까지
// v2.9.0 (사용자 요청 "보스에 나오는 모든 재료들이랑 일반 몬스터 드랍템을 적절히 섞어서"): 무기마다
//   mat × count = 주재료 (그 무기를 대표하는 보스 재료), also = 다른 보스의 재료, drops = 일반 몬스터 전리품 (MONSTER_DROPS 키) [키, 개수]
//   영웅 30렙: 주재료 8 + 다른 Lv.25 보스 재료 4 + 중간 몬스터 전리품 10 · 6
//   초월 40렙: 주재료 10 + 다른 Lv.38~40 보스 재료 5 + Lv.25 보스 재료 4 + 북쪽 몬스터 전리품 12 · 8 — 보스 재료 10종을 모두 씀
//   재료 하나는 보스를 한 번 잡을 때 평균 2.75개 (보스 보상 [2, 3, 0.25])
//   전설 무기 4개 (듀란드의 방패 · 엘프의 활 · 그람 · 아이기스의 방패) 는 만들 수 없음 — 그 무기의 길의 전설 퀘스트로만 (mainquest.js LEGENDS, 사용자 요청)
export const BOSS_CRAFT = [
  { type: "dagger", grade: "hero", level: 30, mat: "kraken_ink", count: 8, also: [["worm_fang", 4]], drops: [["nix", 10], ["gulon", 6]], emerald: 64, ore: 2 },
  { type: "wand", grade: "hero", level: 30, mat: "kraken_eye", count: 8, also: [["worm_heart", 4]], drops: [["nix", 10], ["skeljaskrimsli", 6]], emerald: 64, ore: 2 },
  { type: "sword", grade: "hero", level: 30, mat: "worm_fang", count: 8, also: [["kraken_eye", 4]], drops: [["skeljaskrimsli", 10], ["gulon", 6]], emerald: 64, ore: 2 },
  { type: "axe", grade: "hero", level: 30, mat: "worm_heart", count: 8, also: [["kraken_ink", 4]], drops: [["skeljaskrimsli", 10], ["gulon", 6]], emerald: 64, ore: 2 },
  { type: "axe", grade: "transcend", level: 40, mat: "ygg_branch", count: 10, also: [["skoll_fang", 5], ["worm_heart", 4]], drops: [["troll", 12], ["draugr", 8]], emerald: 128, ore: 4 },
  { type: "wand", grade: "transcend", level: 40, mat: "ygg_root", count: 10, also: [["ymir_heart", 5], ["kraken_eye", 4]], drops: [["draugr", 12], ["troll", 8]], emerald: 128, ore: 4 },
  { type: "dagger", grade: "transcend", level: 40, mat: "skoll_fang", count: 10, also: [["ymir_bone", 5], ["kraken_ink", 4]], drops: [["troll", 12], ["draugr", 8]], emerald: 128, ore: 4 },
  { type: "bow", grade: "transcend", level: 40, mat: "hati_mane", count: 10, also: [["ygg_branch", 5], ["worm_fang", 4]], drops: [["draugr", 12], ["troll", 8]], emerald: 128, ore: 4 },
  // v2.16.0 티르빙 (마검사 무기, 사용자 요청 "따로 길을 안 만들고 제작에만" · 고른 것 "다른 초월 무기와 같은 급"): 길이 없어 메인 퀘스트가 재료를 주지 않음.
  //   태초의 거인 뼈 (드베르그는 이미르의 몸에서 났다는 전설 — 드베르그가 벼린 칼) · 타락한 세계수 뿌리 (저주) · 파쇄의 송곳니 (돌과 쇠를 천처럼 벰) · 드라우그 (무덤의 망자에게서 꺼낸 칼)
  { type: "greatsword", grade: "transcend", level: 40, mat: "ymir_bone", count: 10, also: [["ygg_root", 5], ["worm_fang", 4]], drops: [["draugr", 12], ["troll", 8]], emerald: 128, ore: 4 },
];

/** 제작 재료 목록 [{key, kind: "boss" | "drop", count}] — 주재료 · 다른 보스 재료 · 전리품 순서 */
export function craftMaterials(r) {
  return [{ key: r.mat, kind: "boss", count: r.count }, ...(r.also ?? []).map(([key, count]) => ({ key, kind: "boss", count })), ...(r.drops ?? []).map(([key, count]) => ({ key, kind: "drop", count }))];
}

/** v2.9.0: 재료 설명의 "보스 무기 제작" 줄 — 그 재료를 쓰는 무기 (BOSS_CRAFT 순서, 무기마다 한 줄 · 50바이트 안) */
export function craftUseLines(kind, key) {
  const out = [];
  for (const r of BOSS_CRAFT) {
    const m = craftMaterials(r).find((x) => x.kind === kind && x.key === key);
    if (m) out.push(`§r§f${WEAPON_NAMES[r.type][r.grade]} §7${r.grade === "hero" ? "영웅" : "초월"} ${m.count}`);
  }
  return out;
}
// v2.12.0: 아직 쓰는 곳이 없는 재료 (future, 수르트) 는 "초월 장비 재료 (준비 중)" 한 줄
for (const [k, m] of Object.entries(BOSS_MATERIALS)) m.lore = m.future ? [...m.lore, "§r§8초월 장비 재료 §7(준비 중)"] : [...m.lore, "§r§7대장장이 보스 무기 제작:", ...craftUseLines("boss", k)];

// =====================================================================
//  무기 강화 (v1.5.0): 대장장이. +1 ~ +10
//  - 단계마다 성공 확률·재료. 1~5강은 실패해도 그대로, 6강부터 실패하면 한 단계 떨어짐 (수호 주문서가 있으면 그대로)
//  - 무기 피해 = 기본 공격력 × (1 + 강화 보너스) × 스탯 배율 → 평타·초월 스킬·반사 모두 (룬은 무기와 따로라 그대로)
//  - 강화 단계는 아이템에 저장 (다른 사람에게 줘도 그대로), 이름 앞에 +N
//  - announceFrom 강 이상 성공하면 서버 모두에게 알림 + 폭죽
// =====================================================================
export const ENHANCE = {
  max: 10,
  // 목표 단계(1~10) → rate 성공 확률, stone 강화석, bright 빛나는 강화석 (등급 배수 전)
  steps: {
    1: { rate: 0.95, stone: 1, bright: 0 },
    2: { rate: 0.9, stone: 1, bright: 0 },
    3: { rate: 0.85, stone: 2, bright: 0 },
    4: { rate: 0.75, stone: 2, bright: 0 },
    5: { rate: 0.65, stone: 3, bright: 0 },
    6: { rate: 0.55, stone: 3, bright: 1 },
    7: { rate: 0.45, stone: 4, bright: 1 },
    8: { rate: 0.35, stone: 4, bright: 2 },
    9: { rate: 0.25, stone: 5, bright: 2 },
    10: { rate: 0.15, stone: 6, bright: 3 },
  },
  downFrom: 6, // 이 단계로 올리다 실패하면 한 단계 떨어짐 (수호 주문서가 있으면 그대로)
  // 단계별 무기 피해 증가 (누적): +1 3% … +10 52%
  bonus: [0, 0.03, 0.06, 0.09, 0.13, 0.17, 0.22, 0.28, 0.35, 0.43, 0.52],
  gradeCost: { common: 1, rare: 1, noble: 1.5, hero: 2, transcend: 2.5 }, // 재료 배수 (올림)
  announceFrom: 7,
  animTicks: 22, // 강화 연출 (망치질 3번) 길이
};

/** 강화 단계 n 의 무기 피해 증가 비율 (0 ~ 0.52) */
export function enhanceBonus(n) {
  return ENHANCE.bonus[Math.max(0, Math.min(ENHANCE.max, Math.floor(Number(n) || 0)))] ?? 0;
}

/** n 강 → n+1 강 비용 (등급 배수 올림). 없으면 undefined */
export function enhanceCost(n, gradeId) {
  const s = ENHANCE.steps[n + 1];
  if (!s) return undefined;
  const k = ENHANCE.gradeCost[gradeId] ?? 1;
  return { rate: s.rate, stone: Math.ceil(s.stone * k - 1e-9), bright: Math.ceil(s.bright * k - 1e-9), down: n + 1 >= ENHANCE.downFrom };
}

// =====================================================================
//  갑옷 (v1.5.0)
//  - 종류 5개 (힘·민첩·체력·힘+체력·마력), 등급은 일반(0·10·20·30·40렙)과 초월(40렙)만
//  - 같은 레벨의 5종은 모양이 같다 (디자인은 레벨마다, art/armor). 아이콘 구석의 스탯 색으로 구분
//  - 입으면: 방어 포인트(바닐라 방어구와 같은 계산) + 스탯 보너스 (피해·최대 체력·방패·룬에 모두 반영)
//  - 요구: 최고 경험치 레벨 ≥ 갑옷 레벨, 직접 찍은 스탯 ≥ 요구치 (갑옷 보너스는 요구치에 넣지 않음). 모자라면 벗겨짐
//    · 요구치 (v1.8.2): 무기와 같은 요구 단위 (reqUnit: 40렙 일반 100, 초월 110) × 종류 배율 → 같은 레벨 무기의 주 스탯과 같음
//      민첩 무기를 든 사람이 남는 포인트를 체력에 조금 찍어 같은 레벨 체력 갑옷(방어 ×1.2)을 입을 수 없게.
//      힘+체력 갑옷은 도끼와 같이 힘 2/3 · 체력 1/3 (도끼와 함께 입을 수 있게). (v1.8.1까지: 레벨 + 등급 순번 × 2, 힘+체력은 0.6씩)
//  - 초월 갑옷 (황제 세트): 왕실 퀘스트 (v2.7.0, 35렙부터) 를 끝까지 마치면 무기의 길에 맞는 한 벌 · /armor 명령어
// =====================================================================
export const ARMOR_TYPES = {
  // stats: 세트 보너스를 나누는 비율, prot: 방어 포인트 배율, req: 요구 스탯 배율 (요구 단위 reqUnit 에 곱함, 첫 번째가 주 스탯)
  str: { name: "힘", stats: { str: 1 }, prot: 1.0, req: { str: 1 }, color: "§c", badge: [235, 64, 52] },
  dex: { name: "민첩", stats: { dex: 1 }, prot: 0.8, req: { dex: 1 }, color: "§a", badge: [72, 214, 96] },
  vit: { name: "체력", stats: { vit: 1 }, prot: 1.2, req: { vit: 1 }, color: "§d", badge: [255, 110, 190] },
  strvit: { name: "힘+체력", stats: { str: 0.6, vit: 0.6 }, prot: 1.1, req: { str: 2 / 3, vit: 1 / 3 }, color: "§6", badge: [235, 64, 52], badge2: [255, 110, 190] },
  int: { name: "마력", stats: { int: 1 }, prot: 0.7, req: { int: 1 }, color: "§9", badge: [90, 130, 255] },
};
export const ARMOR_TYPE_ORDER = ["str", "dex", "vit", "strvit", "int"];
export const ARMOR_PIECES = {
  helmet: { name: "투구", slot: "Head", share: 0.2, price: 0.3 },
  chest: { name: "갑옷", slot: "Chest", share: 0.35, price: 0.45 },
  legs: { name: "바지", slot: "Legs", share: 0.3, price: 0.4 },
  boots: { name: "신발", slot: "Feet", share: 0.15, price: 0.25 },
};
export const ARMOR_PIECE_ORDER = ["helmet", "chest", "legs", "boots"];
export const ARMOR_LEVELS = [0, 10, 20, 30, 40];
export const ARMOR_GRADE_LEVELS = { common: ARMOR_LEVELS, transcend: [40] };
/**
 * 등급·레벨 → set: 디자인(art/armor 폴더), name: 세트 이름, prot: 방어 포인트 [투구, 갑옷, 바지, 신발] (배율 1 기준),
 * bonus: 네 부위를 다 입었을 때 스탯 보너스 합 (종류의 stats 비율로 나눔)
 * 방어 포인트는 바닐라 방어구와 같은 계산 (1포인트당 받는 피해 -4%, 20포인트 = -80% 가 최대)
 */
export const ARMOR_TIERS = {
  common_0: { set: "ranger", name: "수련생", prot: [1, 3, 2, 1], bonus: 3 }, // 0렙 디자인이 없어서 순찰대 모양
  common_10: { set: "ranger", name: "순찰대", prot: [2, 5, 4, 1], bonus: 6 },
  common_20: { set: "knight", name: "강철 기사", prot: [2, 6, 5, 2], bonus: 10 },
  common_30: { set: "warden", name: "흑요석 수호자", prot: [3, 7, 6, 3], bonus: 15 },
  common_40: { set: "dragon", name: "적룡", prot: [3, 8, 6, 3], bonus: 20 },
  transcend_40: { set: "emperor", name: "황제", prot: [4, 9, 7, 4], bonus: 32 },
};
export const ARMOR_SHOP = {
  setDiscount: 0.9, // 네 부위를 한 번에 사면
};

/** 갑옷 아이템 ID */
export function armorId(type, level, grade, piece) {
  return `rpg:armor_${type}_l${level}_${grade}_${piece}`;
}

// =====================================================================
//  몬스터 (v1.7.0): 일반 몬스터 6종, 레벨 순서 허수아비 → 굴론 → 스켈야스크림슬리 → 닉스 → 드라우그 → 트롤
//  - 선공하지 않음 (후공): 맞기 전에는 둔 자리 둘레를 돌아다니기만 하고, 맞으면 때린 플레이어를 쫓아가 공격
//  - 모델: art/monsters/<키>/ (클로드 디자인 FantasyMonsters 팩, 허수아비는 훈련용 허수아비 모델을 고쳐 씀). 자연 스폰 없음 (소환 알·/mob 으로 둠)
//  - 수치 기준 = 그 레벨의 플레이어
//    · 플레이어 체력 = 20 + 2 × 레벨 + 체력 스탯 (스탯 포인트 5 + 3 × 레벨 의 20% 를 체력에 둔다고 봄)
//    · 플레이어 한 방 = 레벨에 맞는 일반 등급 칼 (0·5·10·15·20·30·40렙 중 레벨 이하) × (1 + 힘 × 2%) (포인트 80% 를 힘에)
//    · 몬스터 체력 ≈ 그 한 방으로 6.4~8.2번 (껍질처럼 받는 피해가 줄면 줄어든 만큼 더, 재생하는 트롤이 가장 많음)
//    · 몬스터 한 방 ≈ 레벨에 맞는 일반 힘 갑옷(0·10·20·30·40렙 중 레벨 이하, 방어 배율 1.0, 1포인트당 -4% 최대 80%, 베드락 방식)을 입은 플레이어 체력의 7.7~9.2%
//      (민첩 ×0.8·마력 ×0.7 갑옷이면 더 아프고, 체력 ×1.2 갑옷이면 덜 아픔)
//  - 사거리 reach: 몬스터 몸 가장자리 ~ 플레이어 몸 가장자리 (가로, 칸). 이 안이면 공격 동작 시작 → windup 틱 뒤 reach + MONSTER.tolerance 안이면 맞음
//    (그 사이 물러나거나 등 뒤로 돌아가면 빗나감, 벽 너머로는 서로 못 때림). 공격 주기 every 틱 = 공격 동작 시작 ~ 다음 공격 동작 시작
//  - 터치는 바닐라 사거리가 6칸이라 reach 밖에서도 때릴 수 있음 → reach 밖 MONSTER.lungeRange 안이면 달려들며(lunge) 공격 (lungeEvery 틱마다),
//    높이 차이로 달려들 수도 없는 곳(기둥 위 등)에서 때리면 원거리 반격 (플레이어 발밑 고리)
//  - xp: 경험치 (같은 레벨에서 약 8마리에 1레벨, 허수아비는 처음이라 약 4~5마리), emerald: [확률, 최소, 최대] 개
//    v2.7.0: 경험치는 구슬이 아니라 스크립트가 잡은 플레이어에게 바로 줌 (monsters.js · loot.js). 최고 레벨이 xpUntil 이상이면 0
//    (다음 몬스터의 레벨 — 트롤은 43). 그 몬스터의 반복 의뢰 (REPEAT_QUESTS) 경험치도 같음
//  - scale: 모델 크기 배율 (충돌 상자 box [폭, 높이] 에도 곱해짐), speed: 걷는 빠르기, chase: 쫓을 때 배율
// =====================================================================
export const MONSTER_ORDER = ["scarecrow", "gulon", "skeljaskrimsli", "nix", "draugr", "troll"];
export const MONSTERS = {
  scarecrow: {
    name: "허수아비", level: 1, hp: 30, attack: 3, every: 30, windup: 8, reach: 1.5, lungeEvery: 60,
    speed: 0.22, chase: 1.25, knockResist: 0.2, xp: 2, xpUntil: 8, emerald: [0.2, 1, 1], scale: 0.8, box: [0.875, 2.4],
    color: [1.0, 0.82, 0.35], trait: "짚 팔 휘두르기",
    sounds: { windup: "mob.creaking.ambient", hit: "mob.creaking.attack" },
  },
  gulon: {
    name: "굴론", level: 8, hp: 48, attack: 5, every: 20, windup: 8, reach: 1.5, lungeEvery: 32,
    speed: 0.3, chase: 1.45, knockResist: 0.2, xp: 3, xpUntil: 15, emerald: [0.35, 1, 1], scale: 1.3, box: [0.9, 1.1],
    color: [0.92, 0.42, 0.25], trait: "빠르고 자주 달려듦",
    sounds: { windup: "mob.hoglin.angry", hit: "mob.hoglin.attack" },
  },
  skeljaskrimsli: {
    name: "스켈야스크림슬리", level: 15, hp: 70, attack: 9, every: 28, windup: 9, reach: 2.4, lungeEvery: 50,
    speed: 0.25, chase: 1.3, knockResist: 0.6, xp: 5, xpUntil: 22, emerald: [0.3, 1, 2], scale: 1.3, box: [1.0, 1.4],
    color: [0.93, 0.86, 0.7], shell: 0.2, trait: "단단한 껍질: 받는 피해 -20%, 긴 목",
    sounds: { windup: "mob.ravager.ambient", hit: "mob.ravager.bite" },
  },
  nix: {
    name: "닉스", level: 22, hp: 110, attack: 15, every: 24, windup: 9, reach: 1.8, lungeEvery: 44,
    speed: 0.26, chase: 1.35, knockResist: 0.3, xp: 9, xpUntil: 29, emerald: [0.4, 1, 2], scale: 1.1, box: [0.6, 2.0],
    color: [0.35, 0.9, 0.8], amphibious: true, onHit: { effect: "slowness", seconds: 2, amplifier: 1 },
    trait: "물의 손아귀: 맞으면 구속 II 2초, 물속에서도 쫓아옴",
    sounds: { windup: "mob.drowned.say_water", hit: "random.splash" },
  },
  draugr: {
    name: "드라우그", level: 29, hp: 140, attack: 20, every: 26, windup: 9, reach: 2.0, lungeEvery: 48,
    speed: 0.25, chase: 1.35, knockResist: 0.5, xp: 13, xpUntil: 36, emerald: [0.4, 2, 3], scale: 1.2, box: [0.7, 2.1],
    color: [0.45, 0.95, 1.0], undead: true, onHit: { effect: "slowness", seconds: 2, amplifier: 0 },
    trait: "언데드 (성기사 룬 1.5배, 햇빛에 타지 않음), 맞으면 구속 I 2초",
    sounds: { windup: "mob.stray.ambient", hit: "game.player.attack.strong" },
  },
  troll: {
    name: "트롤", level: 36, hp: 230, attack: 38, every: 36, windup: 10, reach: 2.6, lungeEvery: 70,
    speed: 0.24, chase: 1.35, knockResist: 0.9, xp: 21, xpUntil: 43, emerald: [0.5, 2, 4], scale: 1.7, box: [1.0, 2.1],
    color: [0.55, 0.78, 0.3], regen: { after: 60, perSec: 0.03 }, shock: { radius: 1.8, mult: 0.5 },
    trait: "재생: 3초 안 맞으면 1초마다 3%, 내려치면 둘레 1.8칸 충격파 (절반)",
    sounds: { windup: "mob.ravager.roar", hit: "mob.warden.attack" },
  },
};

// ---- 몬스터 전리품 (v2.7.0, loot.js): 잡으면 chance 확률로 1개가 몬스터 자리에 떨어짐 (플레이어가 잡았을 때만, 짧은 빛기둥)
// - 상점 (무기·갑옷 상점의 "전리품 팔기") 에서 price 에메랄드에 팖. 메인 퀘스트 심부름 · 잉그리드의 반복 의뢰 재료
// - 아이콘: art/materials/loot_<몬스터>.png 가 있으면 그 그림, 없으면 build.py 가 그림
export const MONSTER_DROPS = {
  scarecrow: { item: "rpg:loot_scarecrow", icon: "textures/items/rpg/loot/scarecrow", name: "낡은 짚단", color: "§e", chance: 0.5, price: 1, desc: "움직이던 기운이 남은 질긴 짚", rgb: [1.0, 0.84, 0.38] },
  gulon: { item: "rpg:loot_gulon", icon: "textures/items/rpg/loot/gulon", name: "굴론 털가죽", color: "§6", chance: 0.5, price: 1, desc: "거칠고 뻣뻣한 짐승 털가죽", rgb: [0.95, 0.55, 0.3] },
  skeljaskrimsli: { item: "rpg:loot_skeljaskrimsli", icon: "textures/items/rpg/loot/skeljaskrimsli", name: "단단한 껍질", color: "§f", chance: 0.5, price: 2, desc: "쇠처럼 단단한 껍질 조각", rgb: [0.96, 0.9, 0.76] },
  nix: { item: "rpg:loot_nix", icon: "textures/items/rpg/loot/nix", name: "닉스 비늘", color: "§3", chance: 0.5, price: 3, desc: "물빛으로 일렁이는 비늘", rgb: [0.35, 0.92, 0.85] },
  draugr: { item: "rpg:loot_draugr", icon: "textures/items/rpg/loot/draugr", name: "서리 뼛조각", color: "§b", chance: 0.5, price: 4, desc: "서리가 녹지 않는 망자의 뼈", rgb: [0.55, 0.92, 1.0] },
  troll: { item: "rpg:loot_troll", icon: "textures/items/rpg/loot/troll", name: "트롤 엄니", color: "§2", chance: 0.5, price: 5, desc: "바위도 부수는 굵은 엄니", rgb: [0.62, 0.85, 0.36] },
};

// ---- 반복 의뢰 (v2.7.0, sidequest.js): 여관 주인 잉그리드. 몬스터마다 하나 — 메인 퀘스트에서 그 몬스터 처치 단계를 끝내면 열림,
// 보고하면 바로 다시 받음. count: 가져올 전리품 수, emerald: 에메랄드, xp: 경험치 (포인트 — 최고 레벨이 그 몬스터의 xpUntil 이상이면 0)
// 기준: 전리품 count 개 ≈ count × 2 마리. 에메랄드 ≈ 상점에 판 값의 1.25배, 경험치 = count 마리를 더 잡은 만큼
export const REPEAT_QUESTS = {
  scarecrow: { count: 8, emerald: 10, xp: 16 },
  gulon: { count: 8, emerald: 10, xp: 24 },
  skeljaskrimsli: { count: 10, emerald: 25, xp: 50 },
  nix: { count: 10, emerald: 38, xp: 90 },
  draugr: { count: 10, emerald: 50, xp: 130 },
  troll: { count: 10, emerald: 63, xp: 210 },
};
export const MONSTER = {
  lungeRange: 6.5, // reach 밖 이 거리 안이면 달려듦 (터치 사거리 6칸 + 여유)
  lungeHeight: 3, // 높이 차이가 이보다 크면 달려들지 않음
  lungePerBlock: 0.14, // 달려들 때 가로 힘 = (남은 거리 - reach × 0.5) × 이 값 (0.3~1.0)
  tolerance: 0.5, // 공격 동작 끝에 reach + 이만큼 안이면 맞음
  leash: 24, // 이보다 멀어지면 쫓기 그만 (몹 follow_range 와 같게)
  homeRadius: 10, // 돌아다니는 범위 (둔 자리 둘레), 쫓다가 놓치면 돌아옴
  regenDelay: 120, // 싸움이 끝나고 이 틱 동안 맞지 않으면
  regenPerSec: 0.1, // 1초마다 최대 체력의 이만큼 회복
  hpBar: 20, // 이름 아래 체력 막대 칸 수 (다쳤을 때만 보임)
  arc: 100, // 공격 동작 끝에 처음 노린 쪽에서 이 각도(도) 안이어야 맞음 (등 뒤로 돌아가면 빗나감)
  counterRadius: 1.3, // 원거리 반격: 플레이어 발밑 고리 반지름 (끝날 때 이 안이면 맞음)
  counterExtra: 6, // 원거리 반격은 공격 동작(windup)보다 이만큼(틱) 늦게 떨어짐 (고리 밖으로 피할 틈)
  rootAmp: 6, // 몬스터가 구속 VII(세기 6) 이상이면 못 움직임 (속박·기절·빙결 룬, 무기 기절): 달려들기·원거리 반격 없음. 나약함까지 있으면 공격도 못 함
  weakPerLevel: 0.2, // 몬스터가 나약함이면 한 단계마다 피해 -20%
  pauseLimit: 200, // 대상이 크리에이티브·관전·쓰러지는 중인 채로 이 틱이 지나면 싸움 끝
  counterWindow: 60, // 원거리 반격은 닿지 못하는 곳의 플레이어가 이 틱 안에 때렸을 때만 (쉬고 있으면 하지 않음)
  hideMax: 100, // 플레이어의 투명이 이 틱 이하로 남았으면(룬의 짧은 은신) 달려들기·원거리 반격 없음. 물약처럼 긴 투명은 셈하지 않음
};

// ---- 사냥터 (v2.6.0, /mobzone): 운영자가 정한 자리에 몬스터를 늘 채워 둠 (몬스터는 자연 스폰이 없어서 메인 퀘스트의 처치 단계용)
// radius: 가운데에서 이 칸 안에 나옴, active: 가운데에서 이 칸 안에 플레이어가 있을 때만 채움,
// respawn: 모자란 것을 안 뒤 한 마리를 다시 부르기까지 (틱, 200 = 10초), maxCount·defaultCount: 한 사냥터의 수, maxZones: 사냥터 수
export const HUNT_ZONE = { radius: 6, active: 48, respawn: 200, maxCount: 8, defaultCount: 4, maxZones: 32 };

// ---- 길 안내 (v2.6.1, nav.js): 퀘스트를 받으면 다음에 갈 곳 (NPC · 사냥터 · 보스 제단) 을 그 플레이어에게만 보여 줌
// hudKey: 화면 화살표 칸이 읽는 제목 글자 (체력 바 "hpbar:" 와 같은 방식, RPG_RP/ui/hud_screen.json)
// goalEvery: 목적지를 다시 정하는 간격, waveEvery: 바닥 빛 화살표, beamEvery: 목적지 빛기둥 (틱)
// beamRange: 빛기둥을 그리는 거리, pingRange: 목적지 발밑 고리를 그리는 거리, arrive: NPC 도착 거리 (칸)
// wave: 바닥 빛 화살표 (첫 화살표까지 from 칸, step 칸 간격, count 개, 0.12초씩 차례로 켜짐)
// bossHide: 싸우는 보스 필드 가장자리에서 이 칸 안이면 화면 화살표를 숨김 (보스 막대 hud_range 40 과 겹치지 않게, 10틱 사이 달려오는 몫 포함)
// height: 목적지가 이만큼 위·아래면 글자에 "위 N"·"아래 N"
// hudGap: 화살표·거리만 바뀌면 화면 칸 글자를 이 틱에 한 번 (제목 채널을 덜 씀 — /title 이 덜 끊김), sectorHold: 화살표가 바뀌려면 칸 경계를 넘어야 하는 각 (도)
export const NAV = {
  hudKey: "rpgnav:",
  hudGap: 6,
  sectorHold: 4,
  goalEvery: 10,
  waveEvery: 40,
  beamEvery: 20,
  beamRange: 96,
  pingRange: 32,
  arrive: 4,
  wave: { from: 2.6, step: 1.7, count: 5, delay: 0.12 },
  bossHide: 46,
  height: 6,
  colors: { m: [1.0, 0.78, 0.28], c: [0.45, 0.86, 1.0] },
};

/** 몬스터 엔티티 ID */
export function monsterId(key) {
  return `rpg:${key}`;
}

// =====================================================================
//  보스 (v1.8.0~): 제단 블록을 놓으면 보스 필드(신전)가 생기고, 제단을 터치하면 보스 소환. 한 마리씩 추가
//  - 순서 (같은 줄은 같은 레벨): 허수아비 킹(10) → 사막 전갈 + 바위 전갈(18, 한 번에) → 크라켄 / 거대 웜(25) → 요르문간드(32)
//    → 니드호그 / 스콜과 하티(38) → 이미르(40). BOSS_ORDER 에는 만든 것만 (v2.5.0 이미르까지 — 모두)
//  - 체력: 3~4명 파티 기준 고정. 같은 레벨 파티 4명(한 명 초당 1.6타 × 룬 1.3배)이 패턴을 피하며(시간의 65%) 약 2분 30초
//    · 보스는 플레이어마다 따로 무적 시간(10틱)을 셈 → 여럿이 때리면 그만큼 빨리 잡힘 (바닐라처럼 한 명이 때리면 다른 사람 공격이 막히지 않게)
//      v2.1.0: 평타와 스킬·룬 피해는 따로 셈 (평타 바로 뒤의 스킬이 막히지 않게)
//  - 피해 (방어 적용 전 바닐라 단위): 레벨에 맞는 일반 갑옷을 입은 같은 레벨 플레이어 체력 기준 — 평타 약 15%, 큰 패턴 20~60%, 즉사는 즉사
//    · 기믹을 놓친 벌(가짜 분신·분신 실패·감옥 불)은 마법 피해: 갑옷·불 저항으로 줄지 않음 (그래서 수치가 낮음)
//  - 보상 (잡을 때 필드 안에 있었거나 때린 플레이어 모두에게 각각): 에메랄드·소량의 경험치·룬 뽑기권·강화 재료 [최소, 최대] (확률은 chance)
//  - 제단: 보스마다 블록 하나 (rpg:altar_<키>), 크리에이티브 목록·/boss altar 로 얻음. 잡힌 뒤 cooldown 초 동안 다시 소환 못 함 (실제 시간)
//  - 여러 마리가 함께 나오는 싸움 (v1.9.0 전갈): units 에 한 마리씩 (key = 엔티티 rpg:boss_<key>·모델 art/bosses/<key>, spawn = 필드 가운데 기준 [x, z]).
//    · 한 마리마다 체력·크기·평타·패턴 수치가 따로, 보스 막대도 따로. 모두 쓰러뜨려야 보상
//    · 한 마리마다 제 자리(spawn) 둘레 home 반지름(필드 반지름 - 1 - 가운데와의 거리) 안에서만 움직임 → 전갈은 모래(서쪽)·바위(동쪽) 반씩
//  - 움직이지 않는 보스 (v2.0.0 크라켄, stationary): 가운데에 박혀 있음 (중력·충돌·밀림 없음, 발 높이 = 바닥 + stationary.y).
//    패턴마다 대상 쪽으로 몸을 돌림 (한 틱에 stationary.turn 도까지)
//  - 보스 재료 (v2.0.0): loot 에 BOSS_MATERIALS 키로 [최소, 최대, 확률] → 대장장이 "보스 무기 제작"
//  - 굴을 옮겨 다니는 보스 (v2.1.0 거대 웜): 움직이지 않는 보스가 필드의 굴 사이를 땅속으로 옮겨 다님 (땅속에 있는 동안 무적)
//  - 헤엄쳐 도는 보스 (v2.2.0 요르문간드): 움직이지 않는 보스를 스크립트가 매 틱 옮김 (섬 둘레 해자를 돎, 머리가 맞는 곳)
//  - 걷고 나는 보스 (v2.3.0 니드호그, drive): 움직이지 않는 보스를 스크립트가 걷게 하고 날게 함 (몸 돌리기도 모듈이)
//  - 이름 (v2.2.0): 보스 막대와 머리 위 이름표 = "[Lv.level] title name" (bosskit.js bossLabel · build.py boss_label).
//    여러 마리 싸움은 한 마리마다 title (없으면 싸움의 title)
// =====================================================================
export const BOSS_ORDER = ["scarecrow_king", "scorpions", "kraken", "great_worm", "jormungandr", "nidhogg", "skoll_hati", "ymir"];
export const BOSSES = {
  scarecrow_king: {
    name: "허수아비 킹", title: "수확의 왕", level: 10, hp: 7500, scale: 2.6, box: [1.0, 2.6], speed: 0.24, color: [1.0, 0.62, 0.18],
    arena: { theme: "harvest", radius: 16, height: 14 },
    cooldown: 600,
    // 평타: 낫 베기 (앞 부채꼴)
    melee: { reach: 4.5, cone: 120, windup: 16, every: 44, damage: 14 },
    // 패턴 수치
    crows: { every: [200, 260], targets: 3, windup: 26, width: 1.2, damage: 9, blind: 40 },
    bombs: { every: [240, 300], count: 5, onPlayers: 2, radius: 2.6, fuse: 50, damage: 18 },
    prison: { every: [400, 480], from: 0.8, hold: 120, hp: 60, damage: 28 },
    decoys: { at: [0.6, 0.25], count: 4, time: 300, silence: 120, wrongDamage: 6, failDamage: 24, stagger: 100, staggerBonus: 0.5 },
    reaping: { from: 0.4, every: 900, count: 100, safe: 2.4, perTotem: 2 },
    // 원거리 견제 (v2.3.0, 모든 보스 공통 규칙은 아래 FAR 설명): 불붙은 짚단 → 불 burn 초
    far: { name: "불붙은 짚단", look: "fire", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 11, burn: 2 },
    loot: {
      emerald: [8, 12], xp: 20, ticket: [1, 1, 0.3],
      stone: [3, 5], bright: [0, 1, 0.2], scroll: [0, 1, 0.1], ore: [1, 2],
    },
    sounds: { summon: "mob.evocation_illager.prepare_summon", roar: "mob.ravager.roar", hit: "mob.ravager.bite", swing: "item.trident.throw", death: "mob.wither.death" },
  },
  // 사막 전갈 + 바위 전갈 (v1.9.0, Lv.18, 한 번에 둘): 필드 서쪽(모래)에 사막 전갈, 동쪽(바위)에 바위 전갈
  // - 공생: 둘이 symbiosis.distance 칸 안이면 둘 다 받는 피해 -cut (금빛 끈이 이어짐) → 파티를 둘로 나눠 떨어뜨려 잡음
  // - 짝 잃은 분노: 하나가 쓰러지면 남은 쪽의 피해 ×enrage.damage, 패턴 재사용 ×enrage.cd, 걷는 빠르기 ×enrage.move
  scorpions: {
    name: "사막 전갈 · 바위 전갈", title: "모래와 바위의 한 쌍", level: 18, color: [0.93, 0.74, 0.38],
    arena: { theme: "desert", radius: 18, height: 12 },
    cooldown: 900,
    units: [
      {
        key: "desert_scorpion", name: "거대 사막 전갈", title: "모래의 사냥꾼", short: "사막 전갈", hp: 5000, scale: 2.2, box: [1.4, 0.8], speed: 0.3, spawn: [-8, 0], textHeight: 1.8, color: [0.96, 0.8, 0.42],
        // 평타: 집게 (앞 90°, 빠름)
        melee: { reach: 3.4, cone: 90, windup: 12, every: 36, damage: 18, anim: "pinch" },
        // 독침: 가장 먼 플레이어(range 안) 발밑 초록 원 → 꼬리 독침 (피해 + 독)
        sting: { every: [160, 200], range: 16, radius: 1.5, windup: 20, damage: 14, poison: 100 },
        // 모래 잠행: 모래 속으로 (무적) → 흙먼지 줄기가 대상을 speed 칸/초로 trail 틱 동안 쫓음 → 멈춘 곳에 원 windup 틱 → 튀어나오며 띄움
        burrow: { every: [360, 440], sink: 16, trail: 40, speed: 8, radius: 3, windup: 24, damage: 22, lift: 0.8 },
        // 맹독 표식 (체력 70% 아래): 한 명에게 time 틱 뒤 터지는 표식. radius 안의 사람끼리 total 을 나눠 받음 (마법), 혼자면 즉사
        venom: { from: 0.7, every: 600, first: 200, time: 120, radius: 3, total: 60 },
        sounds: { swing: "mob.spider.say", hit: "mob.hoglin.attack", death: "mob.spider.death" },
      },
      {
        key: "rock_scorpion", name: "초거대 바위 전갈", title: "바위의 파수꾼", short: "바위 전갈", hp: 7000, scale: 2.8, box: [1.5, 0.9], speed: 0.2, spawn: [8, 0], textHeight: 2.4, color: [0.66, 0.58, 0.5],
        // 평타: 집게 내려찍기 (앞 120°, 느리고 셈)
        melee: { reach: 4.3, cone: 120, windup: 20, every: 50, damage: 24, anim: "slam" },
        // 바위 던지기: 플레이어 count 명 발밑 갈색 원 → 바위가 날아와 떨어짐 (피해 + 느림)
        boulders: { every: [240, 300], count: 3, radius: 2.2, windup: 32, damage: 20, slow: 40 },
        // 대지의 파동: 내려찍을 때마다 바위 전갈에서 퍼지는 고리 waves 번 (speed 칸/초). 고리가 지나갈 때 땅에 있으면 맞음 → 점프로 피함
        tremor: { every: [400, 500], waves: 3, first: 24, gap: 24, speed: 7, damage: 16 },
        // 석화의 포효 (체력 70% 아래): windup 틱 동안 울부짖음 → 그때 바위 전갈 쪽을 보고 있던(cone° 안, range 칸 안) 사람은 hold 틱 동안 석화 (속박 + 침묵)
        roar: { from: 0.7, every: 600, first: 300, windup: 50, range: 24, cone: 90, hold: 80 },
        // 원거리 견제 (v2.3.0): 꼬리로 바위 조각을 날림 (바위 전갈에게서 멀리 떨어진 사람)
        far: { name: "바위 조각", look: "rock", from: 10, every: [240, 300], first: 220, count: 2, windup: 30, radius: 2.2, damage: 16, effect: { id: "slowness", ticks: 30, amp: 0 } },
        sounds: { swing: "mob.ravager.stun", hit: "random.anvil_land", death: "mob.spider.death" },
      },
    ],
    symbiosis: { distance: 7, cut: 0.6 },
    enrage: { damage: 1.4, cd: 0.7, move: 1.25 },
    loot: {
      emerald: [12, 18], xp: 35, ticket: [1, 1, 0.5],
      stone: [5, 8], bright: [0, 1, 0.35], scroll: [0, 1, 0.15], ore: [2, 3],
    },
    sounds: { summon: "mob.evocation_illager.prepare_summon", roar: "mob.ravager.roar", death: "mob.wither.death" },
  },
  // 크라켄 (v2.0.0, Lv.25): 가라앉은 신전 가운데에 박힌 거대한 두족류. 움직이지 않고 패턴마다 대상 쪽으로 몸을 돌림
  // - 촉수 소환 (체력 80%·45%): 네 물구덩이에서 촉수가 솟아 크라켄을 회복시킴 → 모두 베면 그로기 (받는 피해 증가)
  // - 해일 (체력 60%·25%, 촉수가 없을 때): 한쪽에서 큰 물결이 필드를 휩쓸고 지나감 → 기둥 뒤(그림자)에 숨지 않으면 즉사
  // - 보스 재료: 칠흑 먹물(그림자 단검) · 심해의 눈(리치의 지팡이) → 대장장이 "보스 무기 제작"
  kraken: {
    name: "크라켄", title: "심해의 군주", level: 25, hp: 15000, scale: 3, box: [1.3, 1.6], speed: 0, color: [0.3, 0.78, 0.9], textHeight: 3,
    arena: { theme: "sunken", radius: 20, height: 14 },
    cooldown: 900,
    // 움직이지 않음: 발 높이 = 바닥 + y (촉수가 바닥에 닿음, 가운데 제단 블록은 몸 아래), 몸 돌리기 한 틱에 turn 도까지
    stationary: { y: 0, turn: 16 },
    // 평타: 물어뜯기 (앞 100°, 대상 쪽으로 몸을 돌림)
    melee: { reach: 3.4, cone: 100, windup: 18, every: 40, damage: 34, anim: "bite" },
    // 촉수 내려치기: count 명 (체력 low 아래 countLow 명) 쪽으로 차례로 (gap 틱 간격) 몸을 돌려 앞 두 촉수를 내려침 (부채꼴 radius·cone) → 피해 + 휘감김 (속박 root 틱)
    //   한 번의 내려치기에서 한 사람은 한 번만 맞음 (휘감긴 채 다음 촉수를 또 맞지 않게), 끝나면 1초 동안 물어뜯기 없음
    slam: { every: [200, 260], first: 100, count: 2, countLow: 3, low: 0.5, windup: 36, gap: 30, radius: 12, cone: 56, damage: 50, root: 20 },
    // 먹물 분사: range 안의 한 명 쪽 부채꼴 (반지름 = 거리 + 2.5, 8~24칸 = 필드 가장자리까지, 13칸 넘게 멀면 좁게 coneFar°) → 피해 + 실명 + 침묵,
    //   먹물 웅덩이 puddles 개 (밟고 있으면 1초마다 느림 + 침묵 + 마법 피해). 실명이 끝날 때까지 내려치기 없음 (예고를 볼 수 있게)
    ink: { every: [280, 340], first: 180, range: 24, cone: 70, coneFar: 40, windup: 26, damage: 12, blind: 40, silence: 70, puddles: 3, puddleRadius: 2.2, puddleLife: 240, puddleDamage: 3 },
    // 소용돌이 (체력 from 아래): time 틱 동안 tug 틱마다 radius 안의 사람을 가운데로 끌어당김 (strength) → 끝나면 crush 칸 안은 큰 피해
    whirl: { from: 0.85, every: [520, 600], first: 300, time: 110, radius: 17, tug: 10, strength: 0.32, crush: 7, damage: 100 },
    // 촉수 소환 (체력 at): 네 물구덩이에서 촉수 (체력 hp, 크기 scale, life 틱 뒤 물러남). 살아 있는 촉수마다 크라켄 회복 (초당 최대 체력 × heal)
    //   촉수는 every 틱마다 둘레 radius 칸 휘두르기 (windup 예고 → 피해 + 속박 root). 모두 베면 크라켄 그로기 stagger 틱 (받는 피해 +bonus)
    tentacles: { at: [0.8, 0.45], hp: 380, scale: 2.5, box: [0.8, 2.4], life: 680, heal: 0.0015, every: 80, radius: 3.2, windup: 24, damage: 36, root: 20, stagger: 120, bonus: 0.4 },
    // 해일 (체력 at, 촉수가 없을 때): windup 틱 예고 → 물결이 speed 칸/초로 필드를 가로지름.
    //   물결이 지나갈 때 기둥의 그림자 (물결 반대쪽 기둥 뒷면에서 depth 칸 안, 폭 = 기둥 폭 + pad) 에 없으면 즉사
    //   물결은 필드 둘레 6칸(성문 밖 가까이)까지 휩쓺
    tide: { at: [0.6, 0.25], windup: 130, speed: 12, depth: 5, pad: 0.35 },
    // 높은 곳 (기둥·성문 위 등 바닥보다 above 칸 넘게, 땅에 서 있음): 촉수가 끌어내림 (피해 damage + 가운데 쪽으로 밀어 떨어뜨림, 사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 50 },
    // 원거리 견제 (v2.3.0): 먹물 포탄 → 실명 (크라켄에게서 멀리 떨어진 사람)
    far: { name: "먹물 포탄", look: "ink", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 26, effect: { id: "blindness", ticks: 30, amp: 0 } },
    loot: {
      emerald: [16, 24], xp: 50, ticket: [1, 1, 0.6],
      stone: [6, 9], bright: [0, 1, 0.5], scroll: [0, 1, 0.2], ore: [2, 4],
      kraken_ink: [2, 3, 0.25], kraken_eye: [2, 3, 0.25],
    },
    sounds: { summon: "mob.elderguardian.curse", roar: "mob.elderguardian.idle", swing: "mob.squid.ambient", hit: "mob.guardian.hit", death: "mob.elderguardian.death" },
  },
  // 거대 웜 (v2.1.0, Lv.25): 심층의 신전의 굴(가운데 + 대각선 넷)에 박혀 있고, 땅속으로 굴을 옮겨 다님 (땅속에 있는 동안 무적)
  // - 바닥이 3×3칸 체크무늬 (어두운 칸·밝은 칸): 대지 분출이 한쪽 칸씩 번갈아 솟음
  // - 삼키기 (체력 80% 아래): 한 명을 삼켜 안에서 속박 + 침묵 + 실명 + 계속 피해 → 나머지가 웜을 때려 뱉게 함
  // - 진동 감지 (체력 70%·45%·20%): 땅속에서 발소리를 들음 → 걷거나 뛰면 발밑에서 튀어나와 삼킴 (즉사), 웅크려 걸으면 들키지 않음
  // - 대지 가르기: 멀리서 때리는 사람 쪽으로 땅에 금 → 바위가 금을 따라 솟음 (멀리 있어도 안전하지 않음)
  // - 보스 재료: 파쇄의 송곳니(발뭉) · 대지의 심장석(대지의 심판) → 대장장이 "보스 무기 제작"
  great_worm: {
    name: "거대 웜", title: "대지의 포식자", level: 25, hp: 15000, scale: 3, box: [1.0, 2.4], speed: 0, color: [0.93, 0.55, 0.4], textHeight: 4,
    arena: { theme: "deep", radius: 19, height: 16 },
    cooldown: 900,
    // 움직이지 않음 (굴에 박힘): 발 높이 = 바닥 + y, 몸 돌리기 한 틱에 turn 도까지
    stationary: { y: 0, turn: 14 },
    // 평타: 머리 내려찍기 (앞 110°)
    melee: { reach: 3.6, cone: 110, windup: 20, every: 44, damage: 36, anim: "slam" },
    // 몸통 휘두르기: 한 사람 쪽으로 몸을 돌려 windup 틱 예고 → 앞 angle° (반지름 radius) 를 time 틱에 걸쳐 머리로 쓸어 버림 (머리가 지나가는 자리부터 맞음). 등 뒤 360-angle° 는 안전
    sweep: { every: [240, 300], first: 160, radius: 8, angle: 270, windup: 30, time: 12, damage: 50 },
    // 굴 이동: 땅속으로 (sink 틱, 무적) → 둘레 near 칸 안에 사람이 가장 많은 다른 굴 둘레 radius 칸에 windup 틱 예고 → 튀어나옴 (피해 + 띄움)
    tunnel: { every: [320, 400], first: 300, sink: 20, windup: 34, radius: 4.5, damage: 44, near: 10 },
    // 대지 분출 (체력 from 아래): 체크무늬(tile 칸) 한쪽 칸에 windup 틱 예고 → 솟음, 곧바로 반대쪽 칸에 gap 틱 예고 → 솟음 (피해 + 띄움 lift)
    quake: { from: 0.9, every: [560, 660], first: 400, tile: 3, windup: 40, gap: 34, damage: 54, lift: 0.6 },
    // 삼키기 (체력 from 아래): range 칸 안의 한 명을 노려 windup 틱 (원이 따라다니다 마지막 lock 틱은 붉게 멈춤: 달리면 빠져나갈 수 있는 시간) → 원(radius) 안에서 가운데에 가장 가까운 한 명을 삼킴.
    //   원은 머리가 닿는 reach 칸(가장 긴 덮치기) 밖으로는 따라가지 않음 (멀리 달아나도 피함).
    //   삼킨 사람: 속박 + 침묵 + 실명, 1초마다 dot (마법). 나머지가 웜에게 (함께 싸우는 다른 사람 수 × perHelper) 피해를 주면 뱉음 → 웜 gag 틱 구역질 (패턴 없음).
    //   hold 틱 안에 못 하면 fail (마법) 피해를 더 주고 뱉음. 원 안의 다른 사람은 splash 피해
    devour: { from: 0.8, every: [520, 620], first: 500, range: 10, reach: 9.2, windup: 40, lock: 18, radius: 2.2, hold: 160, perHelper: 110, dot: 4, fail: 20, gag: 40, splash: 30 },
    // 진동 감지 (체력 at): 땅속으로 → warn 틱 예고 → listen 틱 동안 발소리를 들음. 웅크리지 않고 grace 칸 넘게 움직이거나 뛰면 strike 틱 뒤 발밑에서 튀어나와 삼킴 (즉사, 여럿이면 gap 틱 간격).
    //   듣는 동안 rockAt 틱에 rocks 명씩 머리 위로 돌이 떨어짐 (rockWindup 틱 예고, 반지름 rockRadius, 피해 rockDamage) → 웅크려 걸어 나와야 함.
    //   돌에 맞은 사람은 calm 틱 동안 발소리를 셈하지 않음 (맞아 휘청인 것은 들키지 않음). 듣기 시작할 때 필드에 없던 사람은 듣지 않음 (예고를 못 봄).
    //   끝나면 아무 굴 둘레 radius 칸에 emerge 틱 예고 → 튀어나옴 (피해 damage)
    tremor: { at: [0.7, 0.45, 0.2], warn: 60, listen: 80, grace: 1.0, strike: 12, gap: 12, rocks: 2, rockAt: [10, 40], rockWindup: 36, rockRadius: 1.6, rockDamage: 40, calm: 16, emerge: 30, radius: 4.5, damage: 44 },
    // 대지 가르기 (멀리 있는 사람: 제 굴에서 far 칸 넘게): 최대 count 명 쪽으로 땅에 금(폭 width, 대상 너머 over 칸까지)을 windup 틱 예고 →
    //   머리로 내려치면 금을 따라 틱마다 speed 칸씩 바위가 솟음 (금 위의 사람: 피해 damage + 띄움 lift). 옆으로 비켜서면 피함. 삼킨 사람이 있으면 없음 (입이 막힘)
    rend: { every: [220, 280], first: 260, far: 9, count: 2, windup: 30, width: 2.2, over: 3, speed: 1.5, damage: 44, lift: 0.5 },
    // 높은 곳 (성문·담장 탑 위 등 바닥보다 above 칸 넘게, 땅에 서 있음): 웜이 땅을 울려 떨어뜨림 (피해 damage + 가운데 쪽으로 밀어 떨어뜨림, 사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 50 },
    // 원거리 견제 (v2.3.0): 바위 뱉기 → 느려짐
    far: { name: "바위 뱉기", look: "rock", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 26, effect: { id: "slowness", ticks: 40, amp: 0 } },
    loot: {
      emerald: [16, 24], xp: 50, ticket: [1, 1, 0.6],
      stone: [6, 9], bright: [0, 1, 0.5], scroll: [0, 1, 0.2], ore: [2, 4],
      worm_fang: [2, 3, 0.25], worm_heart: [2, 3, 0.25],
    },
    sounds: { summon: "mob.warden.emerge", roar: "mob.warden.roar", swing: "mob.warden.attack", hit: "mob.ravager.bite", death: "mob.warden.death" },
  },
  // 요르문간드 (v2.2.0, Lv.32): 뱀의 섬 (가운데 섬 + 둘레 독의 해자 + 바깥 둑) 의 해자를 헤엄쳐 돎. 맞는 곳은 머리 (몸통·꼬리는 모습)
  // - 독니 돌진: 섬을 가로지르는 길을 예고 → 그 길로 돌진 (피해 + 침묵)
  // - 꼬리 휘감기: 꼬리 쪽(머리 뒤 해자와 섬 가장자리)을 휘감음 (피해 + 속박)
  // - 독해 범람 (체력 85% 아래): 흰 고리(safe) 밖이 독에 잠김 → 고리 안으로 모임. 끝나면 모인 곳을 가로질러 돌진
  // - 아홉 걸음의 독 (체력 70% 아래): 표식이 붙은 사람이 스스로 아홉 칸을 걸으면 즉사. 동료가 곁에 서 있으면 독을 빼 줌
  // - 세계를 감는 고리 (체력 50%·20%): 몸으로 필드를 감싸고 조여 옴 → 머리와 꼬리 사이 틈으로만 빠져나갈 수 있음 (몸에 닿으면 즉사)
  // - 독의 해자: 해자 물에 들어가 있으면 1초마다 마법 피해. 보스 재료 없음
  jormungandr: {
    name: "요르문간드", title: "세계를 휘감은 뱀", level: 32, hp: 22000, scale: 3, box: [1.2, 1.8], speed: 0, color: [0.3, 0.86, 0.62], textHeight: 4,
    // 필드: 섬 반지름 island (흰 고리 safe 안 = 범람 때 안전), 해자 = island ~ moat (물 1칸 깊이), 그 밖은 둑과 담장
    arena: { theme: "isle", radius: 20, height: 14, island: 13, moat: 18.5, safe: 7 },
    cooldown: 900,
    // 움직이지 않는 보스 (스크립트가 옮김): 몸 돌리기 한 틱에 turn 도까지
    stationary: { y: 0, turn: 12 },
    // 헤엄: 목뿌리(머리에서 목 길이 neck 칸 뒤, 모델의 몸 뿌리)가 해자 가운데(radius 칸) 원을 한 틱에 speed 칸씩 돎. 패턴 중에는 목뿌리가 멈추고 목만 돌림 (turn 도/틱).
    //   가끔 돌던 방향을 바꿈 (돌진 뒤 flip 확률). 모델 마디(seg 칸, 크기 3에서)마다 굽혀 꼬리가 원을 따라감 (엔티티 속성 rpg:curve·rpg:tail),
    //   해자에서는 모델을 sink 칸 가라앉힘 (rpg:sink). length = 머리 끝에서 꼬리 끝까지
    swim: { radius: 15.75, speed: 0.18, turn: 12, flip: 0.3, seg: 1.5, neck: 3.94, length: 16.4, sink: 0.9 },
    // 평타: 물어뜯기. 목이 닿는 사람(목뿌리에서 목 + 머리 반폭 + reach 칸) 쪽으로 목을 돌려 목뿌리에서 앞 cone° 부채꼴
    melee: { reach: 2.6, cone: 70, windup: 18, every: 40, damage: 64, anim: "bite" },
    // 독니 돌진: 섬 위(해자 가운데보다 안쪽)의 한 명 쪽으로 목을 돌려, 반대편 해자(가운데 원보다 endIn 칸 안)까지 길(폭 width)을 windup 틱 예고 →
    //   한 틱에 speed 칸씩 돌진. 머리가 지나가는 자리의 사람: 피해 + 침묵 silence 틱 + 옆으로 밀림 knock. 머리 앞 near 칸 안의 사람은 노리지 않음
    dash: { every: [240, 300], first: 120, windup: 32, width: 3.2, speed: 1.0, near: 4, endIn: 1.5, damage: 96, silence: 60, knock: 1.0 },
    // 꼬리 휘감기: 목뿌리 뒤 from ~ to 칸(해자를 따라)의 꼬리 쪽, 가운데에서 inner 칸 밖을 windup 틱 예고 → 피해 + 속박 root 틱. 그 자리에 아무도 없으면 하지 않음
    tail: { every: [300, 360], first: 220, windup: 30, from: 0, to: 12, inner: 8, damage: 72, root: 50 },
    // 독해 범람 (체력 from 아래): windup 틱 동안 해자의 독이 차오름 → 흰 고리(safe 칸, 석영 칸의 바깥 가장자리 safe + 0.7) 밖의 사람: 피해 + 독 poison 틱.
    //   hold 틱 동안 독이 남음 (고리 밖이면 1초마다 마법 피해 tick). 끝나면 모인 곳을 가로질러 독니 돌진
    flood: { from: 0.85, every: [640, 740], first: 260, windup: 70, hold: 60, damage: 100, poison: 100, tick: 5 },
    // 아홉 걸음의 독 (체력 from 아래): 한 명 (싸우는 사람이 3명 넘으면 두 명) 에게 time 틱 동안 표식.
    //   표식이 붙은 사람이 스스로 steps 칸을 움직이면 즉사 (표식이 붙은 뒤 grace 틱, 보스에게 맞아 밀린 뒤 calm 틱은 셈하지 않음).
    //   표식이 없는 동료가 radius 칸 안에 need 틱 (모아서) 서 있으면 독을 빼 줌 (도운 사람은 마법 피해 helper). 시간이 다 되면 사라짐.
    //   필드 밖으로 leave 칸 넘게 멀어지면 (순간이동 등) 표식을 거둠
    venom: { from: 0.7, every: [720, 820], first: 200, time: 300, steps: 9, radius: 2.2, need: 30, helper: 8, calm: 16, grace: 10, leave: 12 },
    // 세계를 감는 고리 (체력 at): 필드 둘레(반지름 start)에 몸이 떠올라 windup 틱 예고 → 한 틱에 speed/20 칸씩 end 칸까지 조여 옴.
    //   머리와 꼬리 사이 gap° 틈으로만 빠져나갈 수 있음: 틈 밖에서 몸(두께 ±thick 칸)에 닿으면 즉사, 끝까지 고리 안에 있으면 즉사
    ring: { at: [0.5, 0.2], windup: 60, start: 21.5, end: 1.5, speed: 2.2, gap: 60, thick: 1.0 },
    // 독의 해자: 해자 물에 들어가 있으면 1초마다 마법 피해 damage
    moat: { damage: 3 },
    // 높은 곳 (성문·담장 탑 위 등 바닥보다 above 칸 넘게, 땅에 서 있음): 꼬리로 쳐서 떨어뜨림 (피해 damage + 가운데 쪽으로, 사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 60 },
    // 원거리 견제 (v2.3.0): 독 뱉기 → 독 (머리에서 멀리 떨어진 사람)
    far: { name: "독 뱉기", look: "venom", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 52, effect: { id: "poison", ticks: 60, amp: 0 } },
    loot: {
      emerald: [22, 32], xp: 70, ticket: [1, 1, 0.8],
      stone: [8, 12], bright: [1, 1, 0.4], scroll: [0, 1, 0.3], ore: [3, 5],
    },
    sounds: { summon: "mob.elderguardian.curse", roar: "mob.enderdragon.growl", swing: "mob.cat.hiss", hit: "mob.ravager.bite", death: "mob.enderdragon.death" },
  },
  // 니드호그 (v2.3.0, Lv.38): 세계수의 뿌리 방에서 뿌리를 갉는 흑룡. 스크립트가 걷고 날게 함 (drive: 모듈이 매 틱 옮김, 몸 전체가 돎)
  // - 물어뜯기 (평타, 머리 앞) · 꼬리 후려치기 (등 뒤에 사람이 있을 때)
  // - 독의 숨결: 한 사람 쪽을 가운데로 한쪽에서 반대쪽으로 쓸어 가는 독 불길 (부채꼴이 돎)
  // - 날갯짓 폭풍 (침묵): 날개를 펴고 몰아치는 바람 → 날개 밑(가까이)과 멀리는 안전, 그 사이는 날려 가며 침묵
  // - 뿌리의 올가미 (속박): 멀리 있는 사람 발밑에서 세계수 뿌리가 솟아 휘감음
  // - 비상 (체력 75%·40%): 날아올라 필드 위를 돌며 독의 비 세 번 → 가장 많이 모인 곳으로 급강하
  // - 독룡의 낙인 (체력 60% 아래, 즉사): 모두에게 낙인 → 터질 때 다른 사람의 낙인 원 안에 있으면 즉사 (흩어져야 함)
  // - 높은 곳: 날개로 쳐서 떨어뜨림. 보스 재료: 세계수의 가지(궁니르) · 타락한 세계수 뿌리(간반테인)
  nidhogg: {
    name: "니드호그", title: "세계수를 갉는 흑룡", level: 38, hp: 25000, scale: 2.5, box: [1.6, 1.7], speed: 0, color: [0.82, 0.22, 0.42], textHeight: 4.6,
    // 필드: 대각선 네 곳 담장 탑에서 세계수의 뿌리가 필드로 휘어 들어옴 (탑 위 arch 칸 높이 → 가운데에서 ground 칸 거리에서 땅에 닿음)
    arena: { theme: "roots", radius: 22, height: 20, arch: 8, ground: 12 },
    cooldown: 900,
    // 움직이지 않는 보스 (중력·충돌 없음): 모듈이 걷게 하고 날게 함 (몸 돌리기는 drive.turn)
    stationary: { y: 0 },
    // 걸음: 한 틱에 walk 칸, 몸 돌리기 한 틱에 turn 도. 대상이 near 칸 안이면 멈춤. 몸(엔티티 자리)은 가운데에서 keep 칸 안에서만
    //   (머리가 몸 앞 약 7칸이라 담장을 뚫고 나가지 않게)
    drive: { walk: 0.13, turn: 8, near: 5.5, keep: 12, head: 6.6 },
    // 평타: 물어뜯기. 몸 앞 cone° 안, 몸 가운데에서 몸 반폭 + reach 칸
    melee: { reach: 5.2, cone: 70, windup: 16, every: 44, damage: 75, anim: "bite" },
    // 꼬리 후려치기: 등 뒤 cone° 안, reach 칸 안에 사람이 있으면. 옆·바깥으로 밀림
    tail: { every: [140, 180], first: 100, windup: 18, reach: 9.5, cone: 130, damage: 60, knock: 1.2 },
    // 독의 숨결: 대상 쪽을 가운데로 sweep° 를 time 틱 동안 쓸어 감 (머리 앞에서 range 칸, 폭 cone°). tick 틱마다 불길 안의 사람: 피해 + 시듦 wither 틱
    //   (tick 은 플레이어의 바닐라 무적 시간 10틱과 같게: 더 짧으면 같은 피해가 무적 시간에 막혀 헛돎)
    breath: { every: [280, 340], first: 160, windup: 24, sweep: 120, time: 40, range: 16, cone: 40, tick: 10, damage: 40, wither: 60 },
    // 날갯짓 폭풍: windup 틱 날개를 폄 → 몸 가운데에서 inner 칸 밖 ~ outer 칸 안의 사람: 피해 + 침묵 silence 틱 + 바깥으로 밀림 knock
    gust: { every: [440, 520], first: 280, windup: 36, inner: 4.5, outer: 16, damage: 40, silence: 60, knock: 1.4 },
    // 뿌리의 올가미: 멀리(far 칸 밖) 있는 사람부터 count 명 발밑 원(radius) → windup 틱 뒤 뿌리가 솟음: 피해 + 속박 root 틱
    snare: { every: [320, 400], first: 200, windup: 28, count: 2, far: 8, radius: 2.2, damage: 50, root: 50 },
    // 비상 (체력 at): rise 틱 동안 height 칸 날아오름 → 가운데 둘레 circle 칸 원을 한 틱에 speed 칸씩 돌며 독의 비 volleys 번 (gap 틱 간격, 사람마다 발밑 원 → windup 틱 뒤 떨어짐)
    //   → 사람이 가장 많이 모인 곳에 dive.radius 원 dive.windup 틱 → 급강하 (피해 + 띄움) → land 틱 동안 내려앉음 (날아 있는 동안 근접 공격은 닿지 않음)
    flight: {
      at: [0.75, 0.4], rise: 30, height: 12, circle: 12, speed: 0.5, volleys: 3, gap: 40, first: 10,
      rain: { radius: 2.4, windup: 24, damage: 70, poison: 60 },
      dive: { windup: 32, radius: 6, damage: 150, lift: 0.9, time: 10 },
      land: 20,
    },
    // 독룡의 낙인 (체력 from 아래): 싸우는 사람 모두에게 time 틱 낙인 (반지름 radius 원이 따라다님) → 낙인마다 터짐: 원 안의 사람에게 마법 피해 damage.
    //   터지는 원 둘 이상에 든 사람 (다른 사람의 원 안에 있던 사람) 은 즉사. 낙인 동안 날갯짓 폭풍·올가미·비상은 없음
    brand: { from: 0.6, every: [760, 880], first: 300, time: 120, radius: 5, damage: 24 },
    // 높은 곳 (뿌리·담장·탑 위 등 바닥보다 above 칸 넘게, 그 위에서 뛰는 중이어도): grace 틱 넘게 머물면 날개로 쳐서 떨어뜨림 (피해 damage + 가운데 쪽으로, 사람마다 cd 틱에 한 번).
    //   처음 올라서면 경고 (뿌리를 넘어가는 것은 괜찮음). 확인은 0.5초마다라 grace 는 10의 배수
    high: { above: 2.5, cd: 40, damage: 70, grace: 30 },
    // 원거리 견제 (v2.3.0): 독 가시 → 시듦 (몸에서 멀리 떨어진 사람)
    far: { name: "독 가시", look: "spike", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 60, effect: { id: "wither", ticks: 40, amp: 0 } },
    loot: {
      emerald: [28, 40], xp: 90, ticket: [2, 2, 0.3],
      stone: [10, 14], bright: [1, 1, 0.6], scroll: [0, 1, 0.45], ore: [4, 6],
      ygg_branch: [2, 3, 0.25], ygg_root: [2, 3, 0.25],
    },
    sounds: { summon: "mob.enderdragon.growl", roar: "mob.enderdragon.growl", swing: "mob.enderdragon.flap", hit: "mob.ravager.bite", death: "mob.enderdragon.death" },
  },
  // 스콜과 하티 (v2.4.0, Lv.38): 해를 쫓는 검은 늑대 스콜과 달을 쫓는 흰 늑대 하티가 한 몸인 머리 둘 늑대. 스크립트가 걷고 뛰어오르게 함 (drive)
  // - 낮과 밤: 낮에는 스콜(검은 머리, 몸 왼쪽)이, 밤에는 하티(흰 머리, 몸 오른쪽)가 깨어 싸움 (stance.every 틱마다 바뀜).
  //   잠든 머리 쪽 옆구리가 약점 (그쪽에서 준 피해 +weak, 피해 숫자 앞 "약점")
  // - 물어뜯기 (평타, 깨어난 머리: 낮 = 불, 밤 = 서리) · 도약 (멀리 있는 사람에게 뛰어듦)
  // - 태양 바퀴 (낮): 몸에서 뻗은 햇살 줄기가 돎 — 몸 곁과 쓸고 가지 않는 쪽은 안전
  // - 달빛 사냥 (밤): 한 사람을 사냥감으로 세 번 잇따라 덮침 (서리 자국), 끝나면 지쳐 받는 피해 +bonus
  // - 해와 달의 심판 (체력 70%·25%, 즉사): 사람마다 해·달 표식 → 가운데 원판의 금빛(해)·푸른(달) 반원 중 제 표식 쪽에 서야 삶. 두 번째는 도중에 표식이 뒤바뀜
  // - 황혼 (체력 45% 아래): 두 머리가 모두 깨어남 — 약점이 사라지고 낮·밤 패턴이 모두 나옴, 재사용 대기 ×dusk.cd
  // - 높은 곳: 뛰어올라 끌어내림. 보스 재료: 스콜의 불꽃 송곳니(클라우 솔라스) · 하티의 달빛 갈기(아르테미스의 은활)
  skoll_hati: {
    name: "스콜과 하티", title: "해와 달을 쫓는 늑대", level: 38, hp: 27000, scale: 2.6, box: [1.4, 1.5], speed: 0, color: [1.0, 0.72, 0.3], textHeight: 5.4,
    // 필드: 해와 달의 돌 고리 (눈 덮인 들판, 가운데 해·달 원판 반지름 disc, 선돌 8개가 가운데에서 stones 칸)
    arena: { theme: "stones", radius: 22, height: 16, disc: 7, stones: 19.5 },
    cooldown: 900,
    // 움직이지 않는 보스 (중력·충돌 없음): 모듈이 걷게 하고 뛰어오르게 함
    stationary: { y: 0 },
    // 걸음: 한 틱에 walk 칸, 몸 돌리기 한 틱에 turn 도 (느리게: 옆구리로 돌아 들어갈 수 있게), near 칸 안이면 멈춤, 몸은 가운데에서 keep 칸 안 (뛰어들 때는 leap 칸 안).
    //   머리 = 몸 앞 head 칸 (옆으로 side 칸씩), 앞발 = 몸 앞 paw 칸 (뛰어들면 앞발이 대상 발밑에 닿음)
    drive: { walk: 0.2, turn: 6, near: 5, keep: 12.5, leap: 17, head: 4.6, side: 1.4, paw: 2 },
    // 평타: 물어뜯기 (몸 앞 cone°, 몸 가운데에서 반폭 + reach 칸). 낮(스콜) = 불 burn 초, 밤(하티) = 느림 slow 틱.
    //   동작은 무는 머리대로 bite_sun (스콜)·bite_moon (하티) — anim 은 엔진 평타의 기본 동작 (이 보스는 모듈이 물어뜯음)
    melee: { reach: 4.6, cone: 80, windup: 14, every: 44, damage: 75, burn: 2, slow: 40, anim: "bite_sun" },
    // 도약: 몸에서 min 칸 넘게·range 칸 안의 가장 먼 사람 발밑 원(radius) windup 틱 → time 틱 동안 포물선(높이 height)으로 뛰어듦 (keep 칸 안에 내려앉음)
    //   → 원 안: 피해 + 띄움 + 낮 = 불 burn 초 / 밤 = 느림 slow 틱
    pounce: { every: [240, 300], first: 160, min: 8, range: 22, radius: 3.4, windup: 22, time: 12, height: 4, damage: 80, lift: 0.6, burn: 3, slow: 40 },
    // 낮과 밤: every 틱마다 바뀜 (처음은 낮, first 틱 뒤 밤). 바뀔 때 howl 틱 울부짖음. 잠든 머리 쪽 옆구리(몸 방향에서 flank° 사이)에서 준 피해 +weak
    stance: { every: 600, first: 600, howl: 24, weak: 0.3, flank: [35, 145] },
    // 태양 바퀴 (낮·황혼): 몸 겉면 inner 칸 밖에서 range 칸까지 햇살 줄기 beams 개 (폭 width) windup 틱 예고 → time 틱 동안 sweep° 돎. 지나갈 때 피해 + 불 burn 초 (줄기마다 한 번)
    //   황혼에는 duskBeams 개 · duskSweep°
    wheel: { every: [360, 420], first: 120, windup: 32, time: 44, beams: 4, sweep: 45, width: 2.4, inner: 2.2, range: 22, damage: 50, burn: 3, duskBeams: 6, duskSweep: 30 },
    // 달빛 사냥 (밤·황혼): 사냥감 한 명 (몸에서 min 칸 넘게 떨어진 사람 먼저) → pounces 번 잇따라: 사냥감 발밑 원(radius) windup 틱 → time 틱 동안 뛰어듦 (높이 height)
    //   → 원 안: 피해 + 느림 slow 틱 + 서리 자국 (frost: 반지름·life 틱, 안에 있으면 1초마다 느림 + 피해 dmg). 끝나면 winded 틱 지침: 받는 피해 +bonus, 공격 없음
    hunt: { every: [360, 420], first: 120, min: 6, pounces: 3, windup: 18, time: 10, height: 3, radius: 3, damage: 60, slow: 40, frost: { radius: 2.2, life: 120, slow: 30, dmg: 3 }, winded: 40, bonus: 0.25 },
    // 해와 달의 심판 (체력 at, 즉사): 필드 북쪽 가장자리(가운데에서 keep 칸)로 뛰어가 울부짖음 → 사람마다 해·달 표식 (담장 밖에서 싸우던 사람은 안으로 끌려 들어옴)
    //   → windup 틱 뒤 원판(반지름 arena.disc)의 제 표식 반원 밖이면 즉사.
    //   두 번째(at 의 둘째)는 swapAt 틱에 표식이 뒤바뀜. 금빛 반원(해) = 가운데 동쪽(+x), 푸른 반원(달) = 서쪽(-x), 가운데 한 줄은 경계 (어느 쪽도 아님)
    //   가장자리로는 leap 틱 동안 뛰어감, 뛰어가서 판결까지 무적 (근접이 원판에 서 있는 동안 원거리만 때리지 않게). 판결 뒤 가장 많이 모인 곳으로 도약
    judge: { at: [0.7, 0.25], windup: 140, swapAt: 70, leap: 16 },
    // 황혼 (체력 from 아래): 두 머리가 모두 깨어남. howl 틱 울부짖음, 패턴 재사용 대기 × cd
    dusk: { from: 0.45, howl: 40, cd: 0.85 },
    // 높은 곳 (선돌·성문·담장 위 등 바닥보다 above 칸 넘게, 그 위에서 뛰는 중이어도): grace 틱 넘게 머물면 뛰어올라 끌어내림 (피해 damage + 가운데 쪽으로, 사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 70, grace: 30 },
    // 원거리 견제 (v2.3.0): 낮 = 태양 불씨 (불), 밤 = 달 서리 (느림 II). 황혼에는 번갈아
    far: { name: "태양 불씨", look: "fire", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 60, burn: 2, night: { name: "달 서리", look: "frost", effect: { id: "slowness", ticks: 40, amp: 1 } } },
    loot: {
      emerald: [28, 40], xp: 90, ticket: [2, 2, 0.3],
      stone: [10, 14], bright: [1, 1, 0.6], scroll: [0, 1, 0.45], ore: [4, 6],
      skoll_fang: [2, 3, 0.25], hati_mane: [2, 3, 0.25],
    },
    sounds: { summon: "mob.wolf.big.growl", roar: "mob.wolf.big.growl", howl: "mob.wolf.big.whine", swing: "mob.wolf.big.bark", hit: "mob.ravager.bite", step: "mob.wolf.step", pant: "mob.wolf.big.panting", death: "mob.wolf.big.death" },
  },
  // 이미르 (v2.5.0, Lv.40, 마지막 보스): 태초의 서리 거인. 얼음 몽둥이를 든 거인 (크기 ×4, 키 약 9칸). 스크립트가 걷게 함 (drive)
  // - 등장: 가운데 얼어붙은 호수 밑에서 얼음을 깨고 솟아오름
  // - 몽둥이 내려치기 (평타): 오른 어깨에서 대상 쪽 직사각형 · 발 구르기: 옆·뒤에 붙은 사람이 있으면 몸 둘레 원
  // - 얼음 가시: 몸에서 먼 사람들 쪽으로 가시가 차례로 솟는 줄 → 빙결 (얼음에 갇혀 못 움직임, 그동안 이미르의 다른 공격은 막아 줌)
  // - 고드름 비: 모두의 발밑 + 무작위 자리 원
  // - 균열 → 무릎 꿇기: 몸 가까이에서 때린 피해만큼 얼음 다리에 금이 감 (멀리서는 × far) → need 가 차면 무릎을 꿇어 공격 없이 받는 피해 +bonus ("빈틈")
  // - 니플헤임의 숨결 (체력 65%·30%, 즉사): 가운데로 뛰어와 숨을 들이쉼 → 불이 켜진 무스펠헤임의 모닥불 곁에 있어야 삶.
  //   불 하나에 싸우는 사람의 절반(올림)까지 — 넘치면 그 불이 꺼져 모두 얼어붙음. 두 번째는 마주 보는 모닥불 한 쌍을 먼저 꺼 버림
  // - 핌불베트르 (체력 20% 아래): 끝날 때까지 눈보라 (안개) — 패턴이 빨라지고 얼음 가시 줄·고드름이 늘어남
  // - 모닥불 곁에서는 느림이 풀림 · 높은 곳(첨탑·성문 기둥): 끌어내림 · 원거리 견제: 얼음 창 (느림 II)
  // - 보스 재료: 이미르의 서리 심장(그람) · 태초의 거인 뼈(아이기스의 방패)
  ymir: {
    name: "이미르", title: "태초의 서리 거인", level: 40, hp: 38000, scale: 4, box: [0.9, 2.3], speed: 0, color: [0.55, 0.86, 1.0], textHeight: 4.2,
    // 필드: 태초의 빙원 (가운데 얼어붙은 호수 반지름 lake, 대각선 모닥불 넷이 가운데에서 fires 칸, 모닥불 곁 = warm 칸 안)
    arena: { theme: "glacier", radius: 24, height: 20, lake: 6, fires: 14, warm: 3.5 },
    cooldown: 1200,
    // 움직이지 않는 보스 (중력·충돌 없음): 모듈이 걷게 하고 뛰게 함
    stationary: { y: 0 },
    // 걸음: 한 틱에 walk 칸, 몸 돌리기 한 틱에 turn 도, near 칸 안이면 멈춤, 몸은 가운데에서 keep 칸 안 (모닥불 화덕에 닿지 않게). 몽둥이 = 오른손 (몸 가운데에서 오른쪽 arm 칸)
    drive: { walk: 0.14, turn: 5, near: 6, keep: 10.5, arm: 2.25 },
    // 등장: 호수 밑 depth 칸에서 rise 틱 동안 솟아오름 → roar 틱 포효 (그동안 무적·공격 없음)
    arrive: { depth: 10, rise: 60, roar: 24 },
    // 평타: 몽둥이 내려치기 — 오른 어깨 밑에서 대상 쪽으로 from ~ reach 칸, 폭 width 인 직사각형 (몸이 그쪽으로 돎: 대상이 몽둥이 줄 위에 오게).
    //   몸 앞(±aim°)의 가장 가까운 사람. windup 틱 뒤 내리침 → 피해 + 느림 slow 틱. anim = 엔진 평타 동작 이름 (이 보스는 모듈이 내려침)
    melee: { from: 0.5, reach: 10.5, width: 3.2, aim: 75, windup: 18, every: 48, damage: 94, slow: 30, anim: "slam" },
    // 발 구르기: 몸 가운데에서 radius 칸 안의 앞 front° 밖(옆·뒤)에 사람이 있으면 → 몸 둘레 원 windup 틱 → 피해 + 띄움 lift
    stomp: { every: [180, 220], first: 100, radius: 6, front: 100, windup: 26, damage: 75, lift: 0.6 },
    // 얼음 가시: 가장 먼 사람부터 lines 명 쪽으로 몸에서 줄 (폭 width, 몸에서 range 칸 또는 필드 끝까지) windup 틱 → 줄을 따라 step 칸마다 가시가 초당 speed 칸으로 차례로 솟음
    //   → 맞으면 피해 + 빙결 root 틱 (얼음에 갇힘: 그동안 이미르의 다른 공격은 받지 않음)
    spikes: { every: [280, 340], first: 160, lines: 3, windup: 22, speed: 20, step: 1.5, width: 1.8, range: 22, damage: 62, root: 30 },
    // 고드름 비: 사람마다 발밑 원 + 무작위 자리 extra 개 (반지름 radius) windup 틱 → 고드름이 떨어짐: 피해 + 느림 slow 틱
    icicles: { every: [360, 420], first: 240, radius: 2.6, windup: 28, extra: 3, damage: 70, slow: 40 },
    // 균열 → 무릎 꿇기: 몸 가까이(근접 보너스 거리)에서 준 피해만큼 금 (멀리서 준 피해는 × far). need 가 차면 time 틱 동안 무릎을 꿇음 (공격 없음, 받는 피해 +bonus "빈틈"),
    //   일어난 뒤 lock 틱 동안은 금이 가지 않음. 등장·숨결·뛰는 동안에도 금이 가지 않음
    crack: { need: 3000, far: 0.25, time: 100, lock: 200, bonus: 0.5 },
    // 니플헤임의 숨결 (체력 at, 즉사): 가운데로 leap 틱 동안 뛰어옴 (그동안 무적) → windup 틱 숨을 들이쉼 → 내쉼: 불이 켜진 모닥불 곁이 아니면 얼어붙어 즉사.
    //   꺼진 불은 relight 틱 뒤 다시 켜짐
    breath: { at: [0.65, 0.3], leap: 20, windup: 140, relight: 200 },
    // 핌불베트르 (체력 from 아래, 끝날 때까지): 재사용 대기 × cd, 얼음 가시 줄 +lines, 고드름 +extra
    winter: { from: 0.2, cd: 0.8, lines: 1, extra: 2 },
    // 모닥불 곁: every 틱마다 느림을 풂
    warmth: { every: 20 },
    // 높은 곳 (첨탑·성문 기둥 위 등 선 자리의 바닥이 above 칸 넘게, 얼음 벽 2칸은 아님): grace 틱 넘게 머물면 몽둥이로 쳐서 가운데 쪽으로 끌어내림 (사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 87, grace: 30 },
    // 원거리 견제 (v2.3.0): 얼음 창 → 느림 II
    far: { name: "얼음 창", look: "ice", from: 10, every: [240, 300], first: 200, count: 2, windup: 30, radius: 2.4, damage: 75, effect: { id: "slowness", ticks: 30, amp: 1 } },
    loot: {
      emerald: [34, 48], xp: 120, ticket: [2, 2, 0.6],
      stone: [12, 16], bright: [1, 1, 0.8], scroll: [1, 1, 0.3], ore: [5, 7],
      ymir_heart: [2, 3, 0.25], ymir_bone: [2, 3, 0.25],
    },
    sounds: { summon: "mob.warden.roar", roar: "mob.warden.roar", swing: "mace.smash_air", hit: "mace.heavy_smash_ground", step: "mob.warden.step", ice: "random.glass", inhale: "mob.breeze.inhale", exhale: "mob.warden.sonic_boom", fire: "fire.ignite", snuff: "extinguish.candle", death: "mob.irongolem.death" },
  },
  // ===================================================================
  //  초월 보스 (v2.12.0~, TRANSCEND_ORDER): 메인 이야기 밖, 전설 퀘스트로 도전 (왕녀 시그리드: 40렙 + 왕실 퀘스트 완료). tier "transcend"
  //  - 기준: 초월 무기·초월 갑옷을 갖춘 45렙 플레이어 4명 (이미르보다 한 단계 위). 필드가 커서 싸우는 동안 틱킹 영역으로 붙잡음 (arena.tick)
  //  - 깨는 맛 (사용자 요청 "패턴을 깨는 맛이 많은 보스"): 무력화(그로기) · 부위 파괴 · 신전 구조물 활용 · 반격 타이밍
  // ===================================================================
  // 수르트 (v2.12.0, Lv.50, 첫 초월 보스): 무스펠헤임의 불의 거인. 불꽃 검 레바테인을 오른손에 든 거인 (크기 ×10, 키 약 33칸). 스크립트가 걷게 함 (drive)
  // - 등장: 가운데 무스펠의 화구에서 불길을 뚫고 솟아오름
  // - 레바테인 내려찍기 (평타): 오른 어깨에서 대상 쪽 긴 직사각형 → 칼이 박힌 동안 "레바테인의 핵"이 드러남 (부위 파괴), 지나간 자리는 불길 (칼이 온전할 때)
  //   · 사슬 기둥이 칼 줄 위에 있으면 기둥의 사슬이 칼을 붙잡음 → 속박 (받는 피해 +bonus, 칼의 핵 드러남, 기둥은 부서짐 — 싸움이 끝나면 다시 섬)
  // - 횡베기 · 찌르기 (반격): 칼이 금빛으로 번쩍이는 동안 (window 틱) 스킬·룬으로 때리면 패턴이 끊기고 비틀거림 (받는 피해 +bonus)
  // - 불의 주먹: 왼 주먹으로 땅을 내려침 → 주먹이 땅에 있는 동안 "건틀릿의 핵" (부위 파괴) · 무스펠의 불비 (메테오) · 불의 고리 (뛰어넘기) · 발 구르기
  // - 무스펠의 업화 (체력 80%·50%·20%, 무력화): 칼을 하늘로 들고 업화를 모음 → 보스 막대(업화)를 피해로 다 깎으면 쓰러짐 (그로기: 받는 피해 +bonus,
  //   "불꽃 왕관의 핵" 드러남), 못 깎으면 업화 폭발 (모두 마법 피해 + 불, 수르트 회복)
  // - 라그나로크 (체력 65%·35%): 뛰어올라 자리를 잡고 칼을 하늘로 → 세계를 태우는 불길이 퍼짐 → 온전한 니플헤임의 얼음 결정 뒤(그림자)만 안전 (즉사).
  //   쓴 결정은 녹음, 두 번째는 수르트가 결정 둘을 먼저 녹임. 레바테인이 부서졌으면 즉사 대신 큰 마법 피해
  // - 부위 파괴: 레바테인 (불길·라그나로크 즉사 없음, 피해 ↓) · 왼 건틀릿 (불의 주먹 없음, 메테오 ↓) · 불꽃 왕관 (업화 쉬움, 불의 고리 ↓) → 보상에 재료 더
  // - 보스 재료: 무스펠의 불꽃 심장 · 레바테인 조각 (장비는 나중에 — 사용자 결정)
  // - 예고 시간 (windup): 원·띠·줄의 한가운데에서 걸어서 (4.3칸/초) 밖까지 + 반응 0.6초 (모바일) 안에 비킬 수 있게 (시험 surtr_test 가 확인)
  // - 마법 피해 (업화 폭발 failDamage · 부서진 칼의 라그나로크 brokenDamage) 는 갑옷으로 줄지 않음: 50렙 체력 약 151 의 40% · 50% (분노 × 1.1)
  surtr: {
    name: "수르트", title: "무스펠헤임의 불의 거인", level: 50, tier: "transcend", hp: 90000, scale: 10, box: [1.0, 3.3], speed: 0, color: [1.0, 0.42, 0.12], textHeight: 3,
    // 보스 막대가 보이는 거리 (필드 반지름 44 + 성문 밖 둑까지, 공통 BOSS.hudRange 대신)
    hudRange: 72,
    // 피해 숫자를 때린 사람 쪽 몸 겉면 (눈높이)에 띄움 (몸이 10칸 넓어서 가운데에 띄우면 멀리 보임)
    textNear: true,
    // 필드: 무스펠헤임의 신전 (가운데 화구 반지름 pit, 대각선 봉인의 사슬 기둥 넷이 가운데에서 chains 칸, 니플헤임의 얼음 결정 여덟이 crystals 칸).
    //   tick = 짓는 동안·싸우는 동안 틱킹 영역으로 필드를 붙잡음 (모바일 시뮬레이션 거리 밖까지 큼)
    arena: { theme: "muspel", radius: 44, height: 40, pit: 7, chains: 16, crystals: 26, tick: true },
    cooldown: 1800,
    // 움직이지 않는 보스 (중력·충돌 없음): 모듈이 걷게 하고 뛰게 함
    stationary: { y: 0 },
    // 걸음: 한 틱에 walk 칸, 몸 돌리기 한 틱에 turn 도, near 칸 안이면 멈춤, 몸은 가운데에서 keep 칸 안 (사슬 기둥에 닿지 않게). 칼 = 오른손 (몸 가운데에서 오른쪽 arm 칸)
    drive: { walk: 0.1, turn: 3.5, near: 9, keep: 8, arm: 6.5625 },
    // 등장: 화구 밑 depth 칸에서 rise 틱 동안 솟아오름 → roar 틱 포효 (그동안 무적·공격 없음)
    arrive: { depth: 36, rise: 100, roar: 30 },
    // 평타: 레바테인 내려찍기 — 오른 어깨 밑에서 대상 쪽으로 from ~ reach 칸, 폭 width (몸 앞 ±aim° 의 사람, band 칸 사이의 사람을 먼저).
    //   windup 틱 뒤 내리침 → 피해 + 밀림. 칼은 embed 틱 동안 박혀 있음 (레바테인의 핵: 칼끝 쪽 core 칸). every = 다음 내려찍기까지
    melee: { from: 5, reach: 37, width: 6, aim: 70, band: [10, 37], windup: 36, embed: 50, core: 33, every: 80, damage: 130, knock: 1.0, anim: "slam" },
    // 불길: 레바테인이 온전하면 내려찍은 직사각형이 time 틱 동안 불탐 (tick 틱마다 안의 사람: 피해 + 불 burn 초)
    trail: { time: 120, tick: 20, damage: 30, burn: 2 },
    // 사슬 기둥 속박: 칼 줄 위 (몸 가운데에서 min 칸 넘게) 의 온전한 사슬 기둥이 칼을 붙잡음 → time 틱 속박, 받는 피해 +bonus (기둥 뒤는 맞지 않음).
    //   min 7: 몸은 가운데에서 drive.keep(8) 칸 안, 기둥은 chains(16) 칸 → 몸과 기둥은 늘 8칸 넘게 떨어져 있어 칼 줄 위의 기둥은 언제나 붙잡음 (규칙 그대로)
    bind: { time: 120, bonus: 0.3, min: 7 },
    // 횡베기 (반격): 몸 앞 angle° (몸 방향 + offset° 가운데) 의 inner ~ outer 칸을 windup 틱 예고 → 칼이 쓸고 감. 마지막 window 틱 = 반격 (칼이 금빛)
    sweep: { every: [300, 360], first: 200, windup: 56, window: 20, inner: 20, outer: 38, angle: 150, offset: 10, damage: 120, burn: 3, knock: 1.2 },
    // 찌르기 (반격): 가장 먼 사람 쪽으로 어깨에서 from ~ reach 칸, 폭 width 줄을 windup 틱 예고 → 찌름 (피해 + 줄 방향으로 밀림). 마지막 window 틱 = 반격
    //   (reach = 찌른 칼끝: build.py thrust 자세에서 칼끝이 어깨 줄 위 약 39.9칸 바닥)
    thrust: { every: [320, 380], first: 360, windup: 32, window: 18, from: 5, reach: 40, width: 4, damage: 140, knock: 1.4 },
    // 반격 성공: 하던 패턴이 끊기고 stagger 틱 비틀거림 (공격 없음), time 틱 동안 받는 피해 +bonus ("반격")
    counter: { stagger: 50, bonus: 0.25, time: 60 },
    // 불의 주먹: 몸 기준 at = [앞, 오른쪽(-는 왼쪽)] 칸에 왼 주먹 (반지름 radius 원) windup 틱 → 피해 + 띄움 lift. 주먹은 rest 틱 동안 땅에 (건틀릿의 핵)
    fist: { every: [260, 320], first: 140, windup: 46, at: [13.4, -7], radius: 7, rest: 60, damage: 125, lift: 0.7 },
    // 무스펠의 불비: 사람마다 발밑 원 + 무작위 extra 개 (건틀릿이 부서지면 extraBroken 개, 반지름 radius) windup 틱 → 메테오 (fall 틱 동안 날아옴): 피해 + 불 burn 초
    meteors: { every: [380, 440], first: 260, windup: 40, radius: 3.5, extra: 4, extraBroken: 0, damage: 100, burn: 3, fall: 16 },
    // 불의 고리: 칼을 땅에 꽂고 (plunge 틱) 몸에서 불의 고리 waves 번 (왕관이 부서지면 wavesBroken 번, gap 틱 간격, 초당 speed 칸).
    //   고리가 지나갈 때 땅에 있으면 피해 + 불 burn 초 → 뛰어넘기
    rings: { every: [440, 520], first: 480, plunge: 26, waves: 3, wavesBroken: 2, gap: 24, speed: 7, damage: 85, burn: 2 },
    // 발 구르기: 몸 가운데에서 radius 칸 안의 앞 front° 밖(옆·뒤)에 사람이 있으면 → 몸 둘레 원 windup 틱 → 피해 + 띄움 lift
    stomp: { every: [180, 220], first: 120, radius: 10.5, front: 110, windup: 40, damage: 100, lift: 0.7 },
    // 무스펠의 업화 (체력 at, 무력화): time 틱 동안 업화를 모음. 업화 막대 = 싸우는 사람 수(최소 min) × per 피해 (불꽃 왕관이 부서지면 × crownCut).
    //   그동안 erupt 틱마다 사람마다 곁에 용암 분출 (원 eruptRadius, eruptWindup 틱 → 피해 eruptDamage + 띄움).
    //   다 깎으면 쓰러짐 groggy 틱 (받는 피해 +bonus "빈틈", 불꽃 왕관의 핵) · 못 깎으면 업화 폭발: 모두 마법 피해 failDamage + 불 failBurn 초, 수르트 최대 체력 × heal 회복
    inferno: { at: [0.8, 0.5, 0.2], time: 240, per: 900, min: 2, crownCut: 0.8, erupt: 40, eruptWindup: 30, eruptRadius: 3, eruptDamage: 70, groggy: 160, bonus: 0.5, failDamage: 60, failBurn: 4, heal: 0.02 },
    // 라그나로크 (체력 at, 즉사): 가운데에서 spot 칸 자리로 leap 틱 동안 뛰어감 (무적) → windup 틱 칼을 하늘로 (담장 밖으로 나간 사람은 10틱마다 끌려 들어옴)
    //   → 불길이 초당 speed 칸으로 가운데에서 64칸 (boss_surtr.js RAG_REACH) 까지 퍼짐.
    //   불길이 지나갈 때 온전한 얼음 결정의 그림자 (수르트 반대쪽 결정 뒤 depth 칸, 폭 = 결정 반폭 + pad) 밖이면 즉사 (레바테인이 부서졌으면 마법 피해 brokenDamage).
    //   그림자에 사람이 든 결정은 녹음. 두 번째부터는 사람들 곁의 온전한 결정을 melt 개까지 먼저 녹임 — 온전한 결정을 keepIntact 개 이상 남기고,
    //   녹인 뒤에도 모두에게 safe 칸 안에 온전한 결정의 그림자가 있을 때만 (걸어서 windup 안에 닿는 거리: 9초 - 반응 1.5초 × 4.3칸/초 ≈ 32칸)
    ragnarok: { at: [0.65, 0.35], spot: 7, leap: 24, windup: 180, speed: 18, depth: 6, pad: 0.4, melt: 2, keepIntact: 2, safe: 32, brokenDamage: 75 },
    // 세계를 태우는 불꽃 (체력 from 아래, 끝날 때까지): 재사용 대기 × cd, 주는 피해 × dmg
    enrage: { from: 0.5, cd: 0.85, dmg: 1.1 },
    // 부위 파괴: 드러난 핵의 체력 (싸움 동안 이어짐)
    parts: { sword: 5000, fist: 3500, crown: 4000 },
    // 부위를 부수면 보상을 받는 사람마다 더 (재료 키 → 개수)
    partLoot: { sword: { laevateinn_shard: 2 }, fist: { laevateinn_shard: 1 }, crown: { muspel_heart: 1 } },
    // 높은 곳 (결정·기둥·담장·탑 위 등 선 자리의 바닥이 above 칸 넘게): grace 틱 넘게 머물면 불기둥 (피해 + 가운데 쪽으로, 사람마다 cd 틱에 한 번)
    high: { above: 2.5, cd: 40, damage: 120, grace: 30 },
    // 원거리 견제 (v2.3.0 규칙): 무스펠의 불덩이 → 불 (왼손에서)
    far: { name: "무스펠의 불덩이", look: "fire", from: 12, every: [200, 260], first: 200, count: 2, windup: 30, radius: 3, damage: 95, burn: 3 },
    loot: {
      emerald: [48, 64], xp: 200, ticket: [2, 3, 0.5], ticketAdv: [1, 1, 0.25],
      stone: [16, 20], bright: [2, 2, 0.5], scroll: [1, 2, 0.5], ore: [7, 9],
      muspel_heart: [1, 2, 0.3], laevateinn_shard: [2, 3, 0.3],
    },
    // 도전 창에 보이는 규칙 (깨는 법)
    notes: ["§e반격: §f칼이 금빛으로 번쩍이면 스킬·룬으로 때리기", "§6무력화: §f업화를 모을 때 업화 막대를 피해로 깎기", "§b구조물: §f라그나로크는 얼음 결정 뒤 · 사슬 기둥으로 칼을 붙잡기", "§c부위 파괴: §f레바테인 · 왼 건틀릿 · 불꽃 왕관"],
    sounds: { summon: "mob.wither.spawn", roar: "mob.enderdragon.growl", swing: "mace.smash_air", hit: "mace.heavy_smash_ground", step: "mob.ravager.step", fire: "mob.blaze.shoot", burn: "fire.fire", lava: "bucket.empty_lava", ignite: "fire.ignite", chain: "hit.chain", ice: "random.glass", melt: "random.fizz", counter: "random.anvil_land", parry: "block.bell.hit", shatter: "random.anvil_break", meteor: "mob.ghast.fireball", erupt: "random.explode", charge: "respawn_anchor.charge", thunder: "ambient.weather.thunder", death: "mob.wither.death" },
  },
};
/** 초월 보스 (v2.12.0): BOSS_ORDER (메인 이야기·그람·아이기스 전설 퀘스트의 "보스 여덟") 와 따로 */
export const TRANSCEND_ORDER = ["surtr"];
/** 모든 보스 (엔진·명령어·빌드·검증) */
export const ALL_BOSS_ORDER = [...BOSS_ORDER, ...TRANSCEND_ORDER];
export const BOSS = {
  iframes: 10, // 플레이어마다 무적 시간 (틱). 그 사이 더 센 공격은 차이만큼. 평타와 스킬·룬 피해는 따로 셈 (v2.1.0)
  resetAfter: 200, // 필드 안에 싸울 수 있는 플레이어가 없는 채로 이 틱이 지나면 보스가 제단으로 돌아가 초기화 (재사용 대기 없음)
  summonDelay: 60, // 제단을 터치하고 보스가 나오기까지 (연출)
  hudRange: 40, // 보스 체력 막대가 보이는 거리 (엔티티 minecraft:boss)
  buildPerTick: 1500, // 필드를 지을 때 한 틱에 바꾸는 블록 수 (fill 은 부피만큼, 모바일에서 끊기지 않게)
  flashTicks: 3, // 맞으면 붉게 번쩍이는 시간
  participantGrace: 600, // 때린 뒤 이 틱 안에 잡히면 필드 밖이어도 보상
  combatMargin: 2.5, // 필드 반지름 + 이만큼 (울타리·성문 자리) 안의 플레이어가 싸우는 사람: 패턴 대상이고, 이 밖에서 때린 공격은 막힘 (v2.1.0: 여유 칸 없앰)
  nearAltars: 6, // 다른 제단의 필드와 이만큼(칸)보다 가까우면 필드를 짓지 않음 (서로 지우지 않게)
  // v2.3.0 근접 보너스: 보스·맞는 소품(촉수)의 몸 겉면에서 range 칸 안에서 준 피해 × (1 + bonus) — 평타·스킬·룬 모두 (피해 숫자 앞 붉은 »)
  //   근접 무기 피해를 모든 몹에게 ×1.25 로 올리면서 (TYPES) 보스만의 보너스는 0.3 → 0.1 (보스 곁은 일반 몹보다 위험해 조금 남김). 0 이면 끔 (표시도 없음)
  close: { range: 4, bonus: 0.1 },
};
// v2.3.0 원거리 견제 (보스·마리 설정 far, 엔진 bosses.js farTick): 몸 겉면에서 from 칸 넘게 떨어진 사람 중 가장 먼 사람부터 count 명 발밑에 원(radius) →
//   windup 틱 뒤 보스가 던진 것(look: fire·rock·ink·venom·spike)이 포물선으로 날아와 떨어짐: 원 안 피해 damage + 효과 effect (+ burn 초 불).
//   first 틱 뒤부터 every 틱마다 (대상이 없으면 1초 뒤 다시 봄). 틀 없이 던짐 (보스는 하던 일을 계속). 큰 패턴 동안은 모듈이 쉬게 함 (farOk)

/** 보스 엔티티 ID */
export function bossId(key) {
  return `rpg:boss_${key}`;
}
/** 싸움 하나의 보스 엔티티 키 (한 마리면 [키], 여럿이면 units 의 key) */
export function bossUnitKeys(key) {
  const b = BOSSES[key];
  return b?.units ? b.units.map((u) => u.key) : [key];
}
/** 한 마리가 움직이는 반지름 (엔티티 minecraft:home): 제 자리(spawn) 둘레 필드 반지름 - 1 - 가운데와의 거리. 한 마리 보스는 필드 반지름 - 1 */
export function unitHomeRadius(key, unit) {
  const R = BOSSES[key].arena.radius;
  return unit?.spawn ? R - 1 - Math.hypot(unit.spawn[0], unit.spawn[1]) : R - 1;
}
/** 제단 블록 ID */
export function altarId(key) {
  return `rpg:altar_${key}`;
}

// ---- 세계수 (v2.5.1): /worldtree · /세계수 — 바라보는 곳에 거대한 나무(이그드라실)를 심음 (운영자, worldtree.js)
export const WORLD_TREE = {
  name: "세계수 이그드라실",
  height: 64, // 기본 높이 (칸, 밑동에서 꼭대기 잎까지 약간 넘음)
  minHeight: 40,
  maxHeight: 96,
  lookRange: 96, // 바라보는 블록을 찾는 거리 (칸)
  clearance: 4, // 뿌리 끝에서 심는 사람까지 이만큼 더 떨어뜨림 (뿌리에 끼지 않게, 자라는 자리에 선 사람도 여기로 옮김)
  perTick: 400, // 한 틱에 놓는 블록 수 (모바일에서 끊기지 않게, 되돌리기는 두 배)
  msPerTick: 10, // 한 틱에 쓰는 시간 (밀리초): 느린 기기에서는 perTick 전에 멈추고 다음 틱에 이어서
  lagMs: 80, // 틱 사이가 이보다 길면 (서버가 느려짐: 빛 계산 등) 한 틱에 놓는 수를 줄임 (0.6배씩, minPerTick 까지), 괜찮으면 40개씩 늘림
  minPerTick: 50,
  loadWait: 200, // 나무 자리를 틱킹 영역으로 불러올 때 기다리는 최대 틱 (10초)
  saveEvery: 20, // 자라는(되돌리는) 동안 블록 기록을 월드에 저장하는 간격 (틱, 단계마다도 저장)
  stagePause: 5, // 단계 사이 쉬는 틱 (뿌리 → 줄기 → 가지 → 잎 → 빛 → 이끼 순으로 자라남)
  fruit: 0.022, // 잎 뭉치 겉의 빛나는 열매(버섯불) 비율
  berries: 0.035, // 잎 밑면에서 발광 열매 덩굴이 내려오는 비율
  maxBuilt: 12, // 줄기·뿌리 자리에 건물·설치물 블록이 이보다 많으면 심지 않음
  maxBlocked: 0.35, // 잎 자리가 이 비율보다 많이 막혀 있으면 (산·동굴 안) 심지 않음
  skySamples: 300, // 잎 자리를 살필 때 뽑아 보는 칸 수
  keep: 5, // 되돌릴 수 있게 월드에 기록해 두는 세계수 수 (최근 것부터)
};

// ---- 결투 (PvP, v2.26.0, scripts/duel.js · pvp.js): /pvp 닉네임 → 상대에게 수락 · 거절 창 → 수락하면 둘만 서로 피해 (평타 · 스킬 · 룬)
// 사용자 요청: "/pvp [플레이어 닉네임] 치면 pvp 수락 또는 거절 버튼이 나오고 수락버튼을 누르면 pvp가 가능하게 하고 pvp를 할때 데미지를 감소시켜서 받게 해"
// 고른 것: 피해 90% 감소 · 평타 + 스킬 + 룬 (결투 상대에게만 — 다른 플레이어는 다치지 않음) · 끝 = 쓰러지거나 /pvp 끝 (나가도 끝)
export const PVP = {
  mult: 0.1, // 결투 상대에게 들어가는 피해 배율 (0.1 = 90% 감소). 평타 · 활 · 완드 · 스킬 · 룬 · 바닐라 화살 모두
  capRatio: 0, // 한 번에 받는 피해의 최대 = 받는 사람 최대 체력 × 이 값 (0 = 없음 — 사용자가 고른 기본: 끔. 예: 0.3)
  requestTicks: 600, // 신청이 유효한 시간 (30초 — 지나면 취소, 열린 창도 닫음)
  countdownTicks: 60, // 수락 뒤 시작까지 (3초 — 3 · 2 · 1 동안은 서로 다치지 않음)
  retryTicks: 300, // 거절 · 만료 · 취소 뒤 같은 사람에게 다시 신청하기까지 (15초 — 창을 계속 띄우는 괴롭힘 막기)
  askGapTicks: 60, // 한 사람이 신청을 보내는 간격 (3초 — 누구에게든)
  quietIfHurtTicks: 100, // 받는 사람이 이 시간 (5초) 안에 다쳤으면 (싸우는 중) 창을 띄우지 않고 글로만 알림 (/pvp 수락 · /pvp 거절 · /pvp 창)
  lethal: false, // false = 결투 상대에게 쓰러지면 체력 1로 남고 짐 (죽지 않아 물건이 떨어지지 않음). true = 실제로 죽음
  graceTicks: 60, // lethal 이 아닐 때: 진 뒤 이 시간 (3초) 동안은 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않음 (체력 1 — 몹 · 보스에게는 그대로). 불은 끄고 독 · 위더는 없앰.
  //               결투 중에는 결투 상대 (펫 포함) 의 피해와, 결투 상대가 blameTicks 안에 때린 뒤의 주인 없는 피해 (불 · 용암 · 떨어짐 · 위더) 로도
  //               죽지 않고 짐 — 몹 · 보스 (주인 없는 보스 기술 포함) · /kill 에는 그대로 죽음 (결투가 목숨 하나를 더 주지 않게)
  blameTicks: 100, // 결투 상대가 때린 뒤 이 시간 (5초) 안의 불 · 용암 · 떨어짐 · 위더 피해는 결투 상대 탓 (쓰러지면 짐)
  protectOthers: true, // 결투 상대가 아닌 플레이어 사이의 피해를 막음 (false = 세계의 PvP 설정대로, 줄이지 않음 — v2.25.0 까지와 같음)
  markEvery: 20, // 결투 중 두 사람 머리 위 결투 표식 (교차한 검) 을 다시 그리는 간격 (틱)
  color: [1.0, 0.22, 0.16], // 결투 빛 (붉은색)
  core: [1.0, 0.92, 0.7], // 심지 (흰 금빛)
  win: [1.0, 0.78, 0.25], // 승리 빛 (금빛)
};

// ---- 초월 룬 2차 강화 (v2.27.0, scripts/ultfx.js · runes.js): 초월 룬 16종에 하늘 · 땅 전체 연출 — 예고 → 발동 → 여운 (피해 · 판정 · 시간은 그대로)
// 사용자 요청: "초월 룬이나 다른 일반 룬들 이펙트가 부족한 거 (ex. 정령 왕, 마검 시리즈 초월) 등 이펙트가 부족한 거를 채우고" · 고른 것: "초월 룬 2차 강화" (16종을 한 번 더 크고 화려하게 — 하늘 · 땅 전체 연출, 단계 추가)
// 하늘빛 = /fog (리소스 팩 fogs/ult_*.json — build.py 가 아래 fogs 표로 만듦): 둘레 사람의 하늘 가장자리 · 먼 곳이 룬의 색으로 물들고, 끝나면 걷힘 (보스 안개와 따로 쌓임)
// 번쩍임 = 카메라 페이드 (발동하는 순간 화면 전체가 룬의 색으로 짧게 번쩍임)
export const ULT_FX = {
  fog: true, // 하늘빛 (false = 끔)
  flash: true, // 발동 순간 화면 번쩍임 (false = 끔)
  audience: 32, // 하늘빛을 보는 거리 (칸 — 룬을 쓴 자리 · 겨눈 자리에서)
  flashRadius: 20, // 번쩍임을 보는 거리 (칸)
  flashTime: { fadeInTime: 0.04, holdTime: 0.02, fadeOutTime: 0.42 }, // 번쩍임 (초): 빠르게 차오르고 천천히 걷힘
  fogTag: "rpg_ult_fog", // /fog 이름표 (보스 안개 rpg_boss_fog 와 다름)
  // 하늘빛: 이름 → [흐려지기 시작하는 거리 (칸), 다 가리는 거리, 색]. 가까운 싸움터는 보이고 먼 곳 · 하늘 가장자리가 물듦
  fogs: {
    "rpg:ult_gold": [10, 70, "#D49A3A"], // 천붕: 금빛 하늘
    "rpg:ult_still": [6, 44, "#7C8494"], // 무영검: 멈춘 시간 (잿빛)
    "rpg:ult_crimson": [8, 56, "#7A0C1C"], // 불사의 맹약: 진홍
    "rpg:ult_meteor": [8, 60, "#B4401A"], // 메테오: 불타는 하늘
    "rpg:ult_blood": [6, 50, "#640808"], // 광전사의 혼: 핏빛
    "rpg:ult_dawn": [12, 80, "#C2D27A"], // 천궁: 새벽빛 (연두 금빛)
    "rpg:ult_heaven": [12, 88, "#F0D48C"], // 대천사 강림: 천상의 금빛
    "rpg:ult_storm": [6, 52, "#30285A"], // 뇌신의 창: 폭풍 (짙은 보랏빛)
    "rpg:ult_night": [6, 46, "#161B3A"], // 월광 암살: 한밤
    "rpg:ult_blade": [10, 72, "#86D6DA"], // 천지개벽: 청백 검기
    "rpg:ult_arcane": [8, 56, "#2C1C6C"], // 마검 해방: 남보라 마력
    "rpg:ult_fire": [10, 64, "#C4561E"], // 원소 해방: 불 → 얼음 → 번개 (모으는 동안 차례로)
    "rpg:ult_ice": [10, 64, "#86C2EA"],
    "rpg:ult_thunder": [10, 64, "#4C3C9E"],
    "rpg:ult_aurora": [10, 72, "#2E9E86"], // 정령왕의 부름: 오로라
    "rpg:ult_void": [5, 40, "#120820"], // 블랙홀: 공허 (어둠)
    "rpg:ult_awaken": [10, 70, "#D88A26"], // 초인 각성: 호박빛
    "rpg:ult_sword": [12, 84, "#E2D29C"], // 검신 강림: 흰 금빛
  },
  // 룬마다 (runes.js 의 함수 이름): fog = 하늘빛, flash = 번쩍임 색 (빨강 · 초록 · 파랑 0~1)
  runes: {
    heavenFall: { fog: "rpg:ult_gold", flash: [1.0, 0.8, 0.38] },
    shadowless: { fog: "rpg:ult_still", flash: [0.82, 1.0, 0.86], still: [0.86, 0.9, 0.96] }, // still = 시간이 멈출 때의 짧은 번쩍임
    undyingPact: { fog: "rpg:ult_crimson", flash: [0.85, 0.1, 0.18] },
    meteor: { fog: "rpg:ult_meteor", flash: [1.0, 0.56, 0.2] },
    berserker: { fog: "rpg:ult_blood", flash: [0.62, 0.02, 0.04] },
    skyBow: { fog: "rpg:ult_dawn", flash: [0.88, 1.0, 0.62] },
    archangel: { fog: "rpg:ult_heaven", flash: [1.0, 0.95, 0.76] },
    thunderSpear: { fog: "rpg:ult_storm", flash: [0.76, 0.66, 1.0] },
    moonAssassin: { fog: "rpg:ult_night", flash: [0.96, 0.76, 1.0] },
    heavenSplit: { fog: "rpg:ult_blade", flash: [0.72, 1.0, 1.0] },
    spellbladeRelease: { fog: "rpg:ult_arcane", flash: [0.62, 0.52, 1.0] },
    elementRelease: { fogs: ["rpg:ult_fire", "rpg:ult_ice", "rpg:ult_thunder"], flash: [1.0, 0.95, 0.86] },
    summonSpirits: { fog: "rpg:ult_aurora", flash: [0.62, 1.0, 0.86] },
    blackHole: { fog: "rpg:ult_void", flash: [0.96, 0.86, 1.0], dark: [0.16, 0.03, 0.24] }, // dark = 빛을 삼킬 때 (어둡게)
    awakening: { fog: "rpg:ult_awaken", flash: [1.0, 0.72, 0.26] },
    swordGod: { fog: "rpg:ult_sword", flash: [1.0, 0.93, 0.62] },
  },
};
