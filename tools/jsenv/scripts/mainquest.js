import { BOSS_CRAFT, BOSS_MATERIALS, BOSSES, MONSTER_DROPS, MONSTERS, RAID_MATERIALS, RUNE, TYPE_ORDER } from "./config.js";
import { ITEM_NAMES } from "./names.js";

/**
 * 메인 퀘스트 (v2.6.0): 애드온에 들어 있는 이야기. 운영자는 /mainnpc 번호 로 NPC만 세우면 된다.
 * - 프롤로그: 모험을 꿈꾸는 작은 소년 → 할아버지 하랄 → 훈련 조교 브란 (무기 여섯 자루 시험 → 하나 고르기)
 * - v2.9.0 지역 퀘스트 (사용자 요청: "보스 퀘스트를 그 지역에서, 왔다 갔다 시키지 않고, 일반 몹 → 드랍템 → 보스"):
 *   스승 (마을) → 농장 → 사막 → 바다 → 북쪽 → 스승 → 할아버지. 지역마다 NPC 한 명 (13 농장 · 14 사막 · 15 바다 · 16 북쪽, /mainnpc 13~16)
 *   보스마다 그 지역 NPC가 [그 지역 일반 몬스터 처치 → 그 몬스터 전리품 모으기 → 보스] 를 차례로 주고, 지역을 마치면 다음 지역 NPC를 찾아가는 퀘스트
 *   모든 길이 보스 8마리를 모두 잡음 (무기 제작이 여러 보스의 재료를 섞어 씀 — config.js BOSS_CRAFT). 길마다 다른 것은 스승의 대사와 요르문간드 · 이미르 보상뿐
 *   경험치 보상 (레벨) 은 따로 사냥하지 않아도 보스 단계 레벨 (보스 레벨 -1, 이미르 40) 에 닿게 맞춤 — 전리품이 잡을 때마다 떨어지는 가장 적은 경우에도
 *   (tests/sim/mainquest_test.mjs 가 퀘스트 정의로 다시 셈)
 * - 전설 무기 4개 (듀란드의 방패 · 엘프의 활 · 그람 · 아이기스의 방패) 는 만들 수 없고 그 무기의 길의 전설 퀘스트로만 (sidequest.js, side "legend")
 *   요르문간드 · 이미르 단계에서 그 길 무기를 만들 수 있으면 주재료 절반 + 에메랄드 + 별철 광석, 아니면 에메랄드 + 별철 광석
 * - 퀘스트 정의는 이 파일에만 있음 (세계에 저장하지 않음 → 애드온을 고치면 바로 바뀜, 게임 안 편집 창에서는 고칠 수 없음)
 *   v2.9.0: 단계 ID "mq_<길>_rNN". 예전 (v2.6.0~2.8.x, 18단계 "mq_<길>_NN") 진행은 migrateMainState 가 가까운 단계로 옮김
 * - 대사 규칙: "|" 줄바꿈, "||" 다음 장 (forms.js toPages). 한 장은 휴대폰 대화창에 3줄 안팎 (70자 이하)
 *   {master} = 그 길의 스승 이름, {mat} = 보스 재료 이름
 */

/** 메인 퀘스트 ID 앞머리 */
export const MAIN_PREFIX = "mq_";
/** 무기의 길 순서 (= 무기 종류 순서) */
export const PATH_ORDER = [...TYPE_ORDER];

/**
 * 메인 퀘스트 NPC (/mainnpc 번호). key = NPC 고유 번호 (퀘스트의 giver·target)
 * greet: own(제 길의 제자) · other(다른 길) · none(아직 무기를 고르지 않음) · done(이야기를 끝냄) — 스승만. 나머지는 quest.js mainGreeting
 * wait: 레벨이 모자라 다음 퀘스트를 못 받을 때 ({lv} = 필요한 레벨)
 * @type {{n: number, key: string, name: string, role: string, path?: string, region?: string, smith?: boolean, runesmith?: boolean, wait: string, greet?: {own: string, other: string, none: string, done: string}}[]}
 */
export const MAIN_NPCS = [
  { n: 1, key: "main_1", name: "할아버지 하랄", role: "이야기의 시작과 끝", wait: "조금 더 강해지면 다시 오거라. 레벨 {lv}에 오르면 말이다." },
  { n: 2, key: "main_2", name: "훈련 조교 브란", role: "무기 시험 · 무기 고르기 (곁에 /dummy 허수아비를 두면 좋음)", wait: "아직 이르다! 레벨 {lv}에 오르면 다시 와라!" },
  {
    n: 3,
    key: "main_3",
    name: "검술 스승 시그문드",
    role: "칼의 길",
    path: "sword",
    wait: "아직 때가 아니다. 레벨 {lv}에 오르면 다시 오너라.",
    greet: {
      own: "검은 마음을 비추는 거울이다. 오늘도 수련하러 왔나?",
      other: "다른 무기를 골랐군. 길은 달라도 지키려는 마음은 같을 게다.",
      none: "아직 무기를 고르지 않았군. 훈련장의 브란에게 먼저 가 보게.",
      done: "서리 거인을 쓰러뜨린 영웅이 왔군. 이제 내가 너에게 배울 차례다.",
    },
  },
  {
    n: 4,
    key: "main_4",
    name: "그림자 스승 헬가",
    role: "단검의 길",
    path: "dagger",
    wait: "…아직 약해. 레벨 {lv}에 오르면 와.",
    greet: {
      own: "…왔어? 발소리가 아직 커. 더 조용히 걸어.",
      other: "…내 제자는 아니군. 용건 없으면 가 봐.",
      none: "…무기도 없이 뭐 하러 왔어. 브란한테 먼저 가.",
      done: "…그림자의 영웅이군. 이제 내가 네 등 뒤를 지켜 줄게.",
    },
  },
  {
    n: 5,
    key: "main_5",
    name: "도끼 전사 토르발",
    role: "도끼의 길",
    path: "axe",
    wait: "하하, 아직 근육이 모자라! 레벨 {lv}에 오르면 와라!",
    greet: {
      own: "하하하! 왔구나, 꼬마 전사! 오늘도 땀 좀 흘려 볼까?",
      other: "하하! 도끼는 아니지만 눈빛이 좋구나! 언제든 놀러 와라!",
      none: "무기도 없는 꼬마로군! 브란에게 가서 하나 골라 와라! 하하!",
      done: "북쪽의 영웅이 왔구나! 오늘은 잔치다! 하하하!",
    },
  },
  {
    n: 6,
    key: "main_6",
    name: "수호 기사 하콘",
    role: "방패의 길",
    path: "shield",
    wait: "서두르지 말게. 레벨 {lv}에 오르면 다시 오게.",
    greet: {
      own: "방패는 나를 위한 것이 아니라 뒤에 선 이들을 위한 것이네. 어서 오게.",
      other: "다른 길을 걷는군. 그래도 위험할 땐 내 방패 뒤로 오게.",
      none: "아직 무기가 없군. 훈련장의 브란을 먼저 찾아가게.",
      done: "이 세상의 방패가 찾아왔군. 영광일세.",
    },
  },
  {
    n: 7,
    key: "main_7",
    name: "엘프 사냥꾼 엘린",
    role: "활의 길",
    path: "bow",
    wait: "조금만 더 기다려요. 레벨 {lv}에 오르면 다시 와요.",
    greet: {
      own: "바람이 오늘은 동쪽에서 불어요. 활을 쏘기 좋은 날이네요.",
      other: "당신은 다른 길을 걷는군요. 그래도 숲은 모두를 반겨요.",
      none: "아직 무기를 고르지 않았군요. 브란 씨에게 먼저 가 봐요.",
      done: "바람의 영웅이 왔네요. 숲이 노래하고 있어요.",
    },
  },
  {
    n: 8,
    key: "main_8",
    name: "마법사 볼바",
    role: "완드의 길",
    path: "wand",
    wait: "룬이 아직 때가 아니라 하느니라. 레벨 {lv}에 오르면 오거라.",
    greet: {
      own: "기다리고 있었느니라. 룬이 네가 올 것을 말해 주었지.",
      other: "네 운명은 다른 무기에 묶여 있구나. 그 길을 가거라.",
      none: "무기를 고르지 않은 자의 운명은 아직 흐릿하구나. 브란에게 가거라.",
      done: "예언을 이룬 아이로구나. 이제 룬도 네 앞에서 침묵하느니라.",
    },
  },
  { n: 9, key: "main_9", name: "군나르", role: "대장장이 (강화 · 영웅 무기 레벨 · 보스 무기 제작) · 심부름", smith: true, wait: "자네에겐 아직 이른 일이야. 레벨 {lv}에 오르면 다시 오게." },
  { n: 10, key: "main_10", name: "여관 주인 잉그리드", role: "심부름 · 소문 · 반복 의뢰 (몬스터 전리품, v2.7.0)", wait: "아직은 위험해요! 레벨 {lv}에 오르면 다시 와 주세요." },
  // v2.7.0: 메인 이야기 밖의 NPC (왕실 퀘스트 · 룬술사)
  { n: 11, key: "main_11", name: "왕녀 시그리드", role: "왕실 퀘스트 (35렙부터, 끝까지 마치면 초월 갑옷)", wait: "아직은 위험한 부탁이에요. 레벨 {lv}에 오르면 다시 찾아와 주세요." },
  { n: 12, key: "main_12", name: "룬술사 에이빈드", role: "룬술사 (룬 갈기 → 룬 조각 → 룬 뽑기권 · 고급 룬 뽑기권)", runesmith: true, wait: "룬은 서두르는 자를 싫어하지. 레벨 {lv}에 오르면 오게." },
  // v2.9.0: 지역 NPC — 그 지역의 사냥터 · 보스 제단 곁에 세움 (REGIONS)
  { n: 13, key: "main_13", name: "농부 올라프", role: "농장 지역 (허수아비 · 허수아비 킹)", region: "farm", wait: "아직은 위험하구먼. 레벨 {lv}에 오르면 다시 오게." },
  { n: 14, key: "main_14", name: "대상 우두머리 라그나르", role: "사막 지역 (굴론 · 전갈 · 거대 웜)", region: "desert", wait: "그 실력으론 아직이다. 레벨 {lv}에 오르면 다시 와!" },
  { n: 15, key: "main_15", name: "늙은 선장 비요른", role: "바다 지역 (스켈야스크림슬리 · 닉스 · 크라켄 · 요르문간드)", region: "sea", wait: "서두르지 말게. 레벨 {lv}에 오르면 다시 오게나." },
  { n: 16, key: "main_16", name: "서리 사냥꾼 스카디", role: "북쪽 지역 (드라우그 · 트롤 · 니드호그 · 스콜과 하티 · 이미르)", region: "north", wait: "아직 약해. 레벨 {lv}에 오르면 와." },
];
/** 메인 이야기에 나오는 NPC 번호 (v2.9.0: 1~8 마을 · 13~16 지역). 9 대장장이 · 10 여관 (반복 의뢰) · 11 왕녀 · 12 룬술사는 이야기 밖 */
export const STORY_NPC_NUMBERS = [1, 2, 3, 4, 5, 6, 7, 8, 13, 14, 15, 16];

/** 대사 속에서 부르는 이름 (이름표 이름과 다를 때) */
const CALL = { main_1: "할아버지 하랄", main_2: "훈련 조교 브란", main_9: "대장장이 군나르", main_10: "여관 주인 잉그리드", main_11: "왕녀 시그리드", main_12: "룬술사 에이빈드" };

export function mainNpcByKey(key) {
  return MAIN_NPCS.find((d) => d.key === key);
}
export function mainNpcByNumber(n) {
  return MAIN_NPCS.find((d) => d.n === n);
}
/** 퀘스트 목표·의뢰에 보이는 이름 (NPC를 세우기 전에도) */
export function mainNpcCall(key) {
  return CALL[key] ?? mainNpcByKey(key)?.name ?? "";
}

/**
 * 무기의 길. mentor = 스승 NPC 번호, desc = 고르는 창의 한 줄 설명, color = 연출 색 [r, g, b]
 * @type {Record<string, {name: string, mentor: string, desc: string, tint: string, color: number[]}>}
 */
export const MAIN_PATHS = {
  sword: { name: "칼의 길", mentor: "main_3", desc: "힘 · 균형 잡힌 근접 딜러", tint: "§c", color: [1.0, 0.42, 0.36] },
  dagger: { name: "단검의 길", mentor: "main_4", desc: "민첩 · 치명타가 잦은 근접", tint: "§a", color: [0.4, 1.0, 0.55] },
  axe: { name: "도끼의 길", mentor: "main_5", desc: "힘+체력 · 튼튼한 딜탱", tint: "§6", color: [1.0, 0.6, 0.25] },
  shield: { name: "방패의 길", mentor: "main_6", desc: "체력 · 동료를 지키는 탱커", tint: "§d", color: [0.62, 0.8, 1.0] },
  bow: { name: "활의 길", mentor: "main_7", desc: "민첩 · 멀리서 쏘는 사냥꾼", tint: "§2", color: [0.75, 1.0, 0.5] },
  wand: { name: "완드의 길", mentor: "main_8", desc: "마력 · 방어를 꿰뚫는 마법", tint: "§9", color: [0.45, 0.62, 1.0] },
};

// ============================================================ 지역 (v2.9.0)
/**
 * 지역마다 NPC 한 명. 운영자가 그 지역의 사냥터 (/mobzone) · 보스 제단 곁에 세움 (/mainnpc 13~16)
 * mobs · bosses: /mainnpc 목록에 보이는 "그 지역에 둘 것". greet: 인사말 (none 무기 전 · before 아직 오지 않음 · here 이 지역 진행 중 · after 이 지역을 마침)
 */
export const REGIONS = {
  farm: {
    npc: "main_13",
    name: "농장",
    mobs: ["scarecrow"],
    bosses: ["scarecrow_king"],
    greet: {
      none: "모험가인가? 무기도 없이 밭에 나오면 위험하구먼. 마을 훈련장에 먼저 가 보게.",
      before: "허수아비들 때문에 큰일이구먼. 누가 좀 도와주면 좋으련만…",
      here: "어서 오게! 밭일은 잘 되어 가는가?",
      after: "자네 덕분에 다시 밭을 갈고 있구먼. 고맙네!",
    },
  },
  desert: {
    npc: "main_14",
    name: "사막",
    mobs: ["gulon"],
    bosses: ["scorpions", "great_worm"],
    greet: {
      none: "무기도 없이 사막에 오다니, 겁도 없군. 마을로 돌아가 무기부터 골라 와.",
      before: "길목이 막혀서 장사를 못 하고 있다. 쓸 만한 모험가가 없나…",
      here: "왔군! 사막 일은 어떻게 돼 가나?",
      after: "하! 사막의 영웅이 왔군! 덕분에 대상이 다시 움직인다.",
    },
  },
  sea: {
    npc: "main_15",
    name: "바다",
    mobs: ["skeljaskrimsli", "nix"],
    bosses: ["kraken", "jormungandr"],
    greet: {
      none: "무기도 없이 바다에 나오면 큰일 나네. 마을에서 무기부터 고르게.",
      before: "요즘 바다가 심상치 않네. 배들이 자꾸 가라앉아…",
      here: "왔군. 바다 일은 잘 되어 가나?",
      after: "허허, 바다를 구한 영웅이 왔군! 언제든 배에 태워 주지.",
    },
  },
  north: {
    npc: "main_16",
    name: "북쪽",
    mobs: ["draugr", "troll"],
    bosses: ["nidhogg", "skoll_hati", "ymir"],
    greet: {
      none: "…무기도 없이 여기까지? 돌아가. 여긴 얼어 죽기 딱 좋아.",
      before: "북쪽은 위험해. 아직 네가 올 곳이 아니야.",
      here: "왔어? 몸은 녹였어?",
      after: "…북쪽의 영웅이네. 여기선 언제든 환영이야.",
    },
  },
};
export const REGION_ORDER = ["farm", "desert", "sea", "north"];

/** 이 NPC가 맡은 지역 ("" = 지역 NPC 아님) */
export function regionOfNpc(key) {
  return REGION_ORDER.find((r) => REGIONS[r].npc === key) ?? "";
}

// ============================================================ 보스마다 [처치 → 모으기 → 보스] (모든 길 같은 순서)
/**
 * kill · collect: [몬스터 키, 수] (모으기 = 그 몬스터의 전리품), xp: 보상 레벨 [처치, 모으기], emerald: [처치, 모으기],
 * lv: 보스 단계의 최소 레벨 (보스 레벨 -1, 이미르 40), reward: 보스 보상 ("@hero" · "@transcend" = 길마다 — bossReward)
 * xp 는 가장 적게 잡는 경우에 맞춤: 잡을 때마다 전리품이 떨어져 처치 단계의 전리품으로 모으기를 채움 (모으기에서 더 잡지 않음).
 *   이때도 보스 단계 레벨에 닿음 (몬스터 · 보스 경험치는 바닐라 곡선, xpUntil 이상이면 0). 보통 (50%) 이면 보스 레벨 안팎으로 도착
 * 같은 레벨의 두 번째 보스 (크라켄 · 스콜과 하티) 는 보상 레벨을 줄여 다음 보스에서 레벨이 너무 앞서지 않게
 */
const ARCS = [
  { boss: "scarecrow_king", region: "farm", kill: ["scarecrow", 10], collect: ["scarecrow", 6], xp: [3, 4], emerald: [5, 6], lv: 9, reward: { emerald: 12, xp: 2, weapon: "l10_noble", items: [["ticket", 1]] } },
  { boss: "scorpions", region: "desert", kill: ["gulon", 12], collect: ["gulon", 8], xp: [2, 2], emerald: [10, 12], lv: 17, reward: { emerald: 18, xp: 3, weapon: "l20_noble", items: [["ticket", 1]] } },
  { boss: "great_worm", region: "desert", kill: ["gulon", 10], collect: ["gulon", 6], xp: [1, 2], emerald: [14, 16], lv: 24, reward: { emerald: 24, xp: 2, items: [["stone", 4], ["ore", 1], ["ticket", 1]] } },
  { boss: "kraken", region: "sea", kill: ["skeljaskrimsli", 12], collect: ["skeljaskrimsli", 8], xp: [0, 0], emerald: [14, 16], lv: 24, reward: { emerald: 24, xp: 1, items: [["stone", 2], ["ore", 1], ["ticket", 1]] } },
  { boss: "jormungandr", region: "sea", kill: ["nix", 12], collect: ["nix", 8], xp: [1, 2], emerald: [18, 20], lv: 31, reward: "@hero" },
  { boss: "nidhogg", region: "north", kill: ["draugr", 12], collect: ["draugr", 8], xp: [1, 1], emerald: [24, 24], lv: 37, reward: { emerald: 40, xp: 1, items: [["ticket", 1], ["scroll", 1]] } },
  { boss: "skoll_hati", region: "north", kill: ["troll", 8], collect: ["troll", 6], xp: [0, 0], emerald: [28, 28], lv: 37, reward: { emerald: 40, xp: 0, items: [["ticket", 1], ["bright", 1]] } },
  { boss: "ymir", region: "north", kill: ["draugr", 10], collect: ["troll", 6], xp: [0, 0], emerald: [30, 30], lv: 40, reward: "@transcend" },
];
/** 지역 사이 · 마지막 귀환 보상 (에메랄드) */
const TRAVEL_EMERALD = { farm: 2, desert: 4, sea: 6, north: 8, home: 20 };

/**
 * 길의 단계 (정의만 — 모든 길 같은 뼈대). kind: intro (조교 → 스승) · depart (스승 → 농장) · travel (지역 → 다음 지역) ·
 * kill · collect · boss · home (북쪽 → 스승) · outro (스승 → 할아버지). from · to · giver = NPC 키 ("@mentor" = 그 길의 스승)
 * @typedef {{kind: string, giver: string, to?: string, region?: string, arc?: any, mob?: string, count?: number, boss?: string, lv: number}} StepDef
 * @returns {StepDef[]}
 */
function skeleton() {
  /** @type {StepDef[]} */
  const steps = [{ kind: "intro", giver: "main_2", to: "@mentor", lv: 0 }];
  let at = "";
  let lv = 0;
  for (const arc of ARCS) {
    const npc = REGIONS[arc.region].npc;
    if (arc.region !== at) {
      steps.push({ kind: at ? "travel" : "depart", giver: at ? REGIONS[at].npc : "@mentor", to: npc, region: arc.region, lv });
      at = arc.region;
    }
    steps.push({ kind: "kill", giver: npc, arc, mob: String(arc.kill[0]), count: Number(arc.kill[1]), lv });
    steps.push({ kind: "collect", giver: npc, arc, mob: String(arc.collect[0]), count: Number(arc.collect[1]), lv });
    steps.push({ kind: "boss", giver: npc, arc, boss: arc.boss, lv: arc.lv });
    lv = arc.lv;
  }
  steps.push({ kind: "home", giver: REGIONS[at].npc, to: "@mentor", lv });
  steps.push({ kind: "outro", giver: "@mentor", to: "main_1", lv });
  return steps;
}
const SKELETON = skeleton();
/** 단계 수 (길마다 같음) */
export const PATH_STEPS = SKELETON.length;

/** 길의 단계 정의 (스승 키를 채움) */
export function pathSteps(type) {
  const mentor = MAIN_PATHS[type]?.mentor ?? "";
  return SKELETON.map((s) => ({ ...s, giver: s.giver === "@mentor" ? mentor : s.giver, to: s.to === "@mentor" ? mentor : s.to }));
}

/** 그 몬스터를 처음 잡는 단계 (모든 길 같은 단계, 없으면 0) — v2.7.0 잉그리드의 반복 의뢰가 열리는 단계 */
export function mainKillStep(mob) {
  return SKELETON.findIndex((s) => s.kind === "kill" && s.mob === mob) + 1;
}

/** 그 보스를 잡는 단계 (없으면 0) — v2.9.0 전설 퀘스트가 열리는 단계 */
export function mainBossStep(boss) {
  return SKELETON.findIndex((s) => s.kind === "boss" && s.boss === boss) + 1;
}

/** 그 길 · 등급의 보스 무기 제작법 (없으면 undefined — 전설 무기는 만들 수 없음) */
function craftOf(type, grade) {
  return BOSS_CRAFT.find((r) => r.type === type && r.grade === grade);
}

// ============================================================ 대사
/** [제목, 줄 때, 진행 중, 완료] — 완료 대사는 보고받는 NPC가 말함 (말 걸기는 받는 사람) */
const T = (title, offer, progress, done) => ({ title, offer, progress, done });

/** 프롤로그 */
const PROLOGUE = {
  start: T(
    "모험의 꿈",
    "또 언덕 위에서 먼 곳을 보고 있었구나. 모험가가 되고 싶은 게냐?||허허, 이 할아비도 젊을 땐 세계수 아래를 누비던 모험가였단다.||좋다. 훈련장의 브란에게 가 보거라. 무기 쓰는 법을 가르쳐 줄 게다.",
    "브란은 훈련장에 있단다. 어서 가 보거라.",
    "하랄 어르신의 손자라고? 하하, 눈빛이 제법이군!||좋다, 오늘부터 훈련이다. 각오는 됐겠지?",
  ),
  trial: T(
    "여섯 자루의 무기",
    "모험가에게 가장 중요한 건 자기 손에 맞는 무기를 찾는 거다.||칼, 단검, 도끼, 방패, 활, 완드. 여섯 자루를 모두 빌려주마.||하나씩 들고 허수아비든 몬스터든 한 번씩 맞혀 봐라!",
    "아직 안 써 본 무기가 있군. 여섯 자루 모두 한 번씩 맞혀 봐라!",
    "어떠냐, 손맛이 다 다르지?||활과 완드는 누르면 쏘고, 방패는 들고 웅크리면 앞을 막는다. 기억해 둬라.",
  ),
  choose: T("나의 무기", "이제 고를 시간이다. 가장 손에 맞았던 무기를 골라라.||고른 무기의 스승을 소개해 주마. 한 번 고르면 다시는 바꿀 수 없다!", "", ""),
};

/** 무기를 고른 뒤 조교의 말 (길마다) */
const CHOSEN = {
  sword: "칼이라! 정직하고 굳센 무기지.||검술 스승 시그문드를 찾아가라. 이 추천서를 보여 주면 될 거다.",
  dagger: "단검이라… 빠르고 날카롭군.||그림자 스승 헬가를 찾아가라. 말수가 적은 사람이니 놀라지 마라.",
  axe: "도끼! 하하, 배짱 있는 녀석이군.||도끼 전사 토르발을 찾아가라. 목소리가 커서 금방 찾을 거다.",
  shield: "방패를 고르다니, 남을 지키는 길이군.||수호 기사 하콘을 찾아가라. 마을에서 가장 믿음직한 기사다.",
  bow: "활이라. 눈이 좋은 녀석이었군.||엘프 사냥꾼 엘린을 찾아가라. 숲에서 온 엘프지만 마음이 따뜻하다.",
  wand: "완드라… 마력이 느껴지더라니.||마법사 볼바를 찾아가라. 앞날을 읽는다는 이상한 노파다.",
};

/** 스승 (길마다): intro = 브란 → 스승 (추천서), depart = 스승 → 농장 (완료 대사는 농부), home = 북쪽에서 돌아왔을 때 스승의 말, outro = 스승 → 할아버지 */
const MASTER = {
  sword: {
    intro: T("시그문드를 찾아서", "칼을 골랐으니 검술 스승 시그문드에게 가 봐라.||이 추천서를 보여 주면 될 거다.", "시그문드에게 추천서를 보여 줘라.", "브란의 추천서로군. 칼을 골랐다고?||나는 시그문드. 한때 왕실 근위대장이었지.||좋다, 오늘부터 너는 내 제자다. 칼은 힘으로 휘두르되 마음으로 멈추는 것이다."),
    depart: T("첫 실전", "기본은 익혔다. 이제 실전이다.||마을 밖 농장의 허수아비들이 저절로 움직인다더군. 세계수가 시들며 생긴 일이지.||농부 올라프를 찾아가 도와라. 그리고 이 검 조각을 가져가거라.||우리 가문의 검 그람의 조각이다. 삼십 년 전 서리 거인 이미르 앞에서 부서졌지.||이미르를 쓰러뜨리거든 돌아오너라. 그람을 되살릴 길을 알려 주마.", "농장의 농부 올라프를 찾아가라.", ""),
    home: "…이미르를 쓰러뜨렸구나. 삼십 년 전 내가 끝내지 못한 일을 네가 해냈다.||이제 그람을 되살릴 차례다. 준비가 되면 내게 말하거라.",
    outro: T("영웅의 귀환", "더는 가르칠 것이 없구나. 너는 나의 자랑스러운 제자다.||할아버지께 가 보아라. 누구보다 너를 기다리셨을 게다.", "하랄 어르신께 가 보아라.", "시그문드가 그러더구나. 네가 이미르를 쓰러뜨렸다고!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 구한 영웅이 되어 돌아왔구나.||이 할아비는 이제 여한이 없다. 네 모험은 이제부터 시작이란다."),
  },
  dagger: {
    intro: T("헬가를 찾아서", "단검을 골랐으니 그림자 스승 헬가에게 가 봐라.||이 추천서를 건네면 된다. 말수가 적어도 놀라지 마라.", "헬가에게 추천서를 건네라.", "…브란이 보냈군. 단검을 골랐다고.||난 헬가. 이 마을의 그림자를 지키지.||좋아, 받아 주지. 대신 내 말은 두 번 하지 않아."),
    depart: T("그림자의 첫 임무", "…기본은 됐어. 이제 밖으로 나가.||농장의 허수아비들이 움직이고 있어. 농부 올라프를 찾아가 도와.||크라켄의 칠흑 먹물, 스콜의 불꽃 송곳니. 좋은 단검의 재료야. 보이면 챙겨.||군나르가 벼려 줄 거야. 그리고… 살아서 돌아와.", "농장의 올라프. 가.", ""),
    home: "…살아 돌아왔군. 다행이야. 진심으로.||스카디가 전갈을 보냈더군. 넌 이제 내 제자가 아니라 동료야.",
    outro: T("영웅의 귀환", "가르칠 건 다 가르쳤어. 이제 넌 나보다 조용히 걷지.||할아버지께 가 봐. …기다리고 계셔.", "할아버지께.", "얘야! 그 무뚝뚝한 헬가가 너를 칭찬하더구나!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 지킨 영웅이 되었구나.||자랑스럽다. 네 모험은 이제부터 시작이란다."),
  },
  axe: {
    intro: T("토르발을 찾아서", "도끼를 골랐으니 도끼 전사 토르발에게 가 봐라.||목소리가 커서 금방 찾을 거다. 이 추천서를 건네라.", "토르발에게 추천서를 건네라.", "하하하! 도끼를 골랐다고? 브란이 제대로 된 녀석을 보냈군!||나는 토르발! 북쪽 얼음 땅에서 왔다.||도끼는 힘으로 내리찍고 몸으로 버티는 무기다. 힘과 체력을 함께 길러라!"),
    depart: T("전사의 첫걸음", "하하! 이제 도끼 맛을 보여 줄 때다!||농장의 허수아비들이 날뛴다더군. 농부 올라프를 찾아가 도와라!||거대 웜의 심장석, 니드호그의 세계수 가지! 군나르에게 가져가면 전설의 도끼가 된다!||그리고 내 고향을 얼린 서리 거인 이미르… 언젠가 꼭 쓰러뜨려 다오!", "농장의 올라프에게 가라! 하하!", ""),
    home: "돌아왔구나! 북쪽 하늘이 녹고 있다더군!||내 고향 사람들도 이제 편히 잠들겠지. 고맙다, 전사여! 하하… 정말 고맙다.",
    outro: T("영웅의 귀환", "하하하! 이제 네게 가르칠 건 없다! 하랄에게 가 봐라.||우리 전우가 너를 기다린다!", "하랄에게 가라!", "네가 이미르를 쓰러뜨렸다고! 우리가 끝내 못 한 일을…||언덕에서 모험을 꿈꾸던 꼬마가 북쪽의 영웅이 되었구나.||고맙다, 얘야. 네 모험은 이제부터 시작이란다."),
  },
  shield: {
    intro: T("하콘을 찾아서", "방패를 골랐으니 수호 기사 하콘에게 가 봐라.||마을에서 가장 믿음직한 기사다. 이 추천서를 건네라.", "하콘에게 추천서를 건네라.", "브란이 보냈군. 방패를 골랐다고?||나는 하콘, 이 마을을 지키는 기사일세.||방패를 들고 웅크리면 앞에서 오는 공격을 막을 수 있네. 남을 지키는 법부터 배우세."),
    depart: T("지키는 자의 첫걸음", "이제 마을 밖을 지킬 차례일세.||농장의 허수아비들 때문에 농부들이 밭에 나가지 못한다네. 농부 올라프를 찾아가 돕게.||내 스승 듀란드 경은 요르문간드에 맞서다 바다로 사라지셨네. 그분의 방패가 그 뱀의 뱃속에 있다지.||요르문간드를 쓰러뜨리거든 돌아오게. 그 방패 이야기를 해 주겠네.", "농장의 농부 올라프를 찾아가게.", ""),
    home: "이미르의 얼음이 녹았다지? 자네가 해냈군!||그 얼음 속에서 신들의 방패 아이기스의 기운이 느껴진다네. 준비가 되면 말하게.",
    outro: T("영웅의 귀환", "이제 내가 자네를 지킬 일은 없겠군. 자네가 모두를 지키니까.||할아버지께 가 보게. 기다리고 계실 걸세.", "하랄 어르신께 가 보게.", "하콘이 그러더구나. 네가 모두를 지켜 냈다고!||언덕에서 모험을 꿈꾸던 꼬마가 모두를 지키는 방패가 되어 돌아왔구나.||자랑스럽다, 얘야. 네 모험은 이제부터 시작이란다."),
  },
  bow: {
    intro: T("엘린을 찾아서", "활을 골랐으니 엘프 사냥꾼 엘린에게 가 봐라.||숲에서 온 엘프지만 마음이 따뜻하다. 이 추천서를 건네라.", "엘린에게 추천서를 건네라.", "브란 씨가 보낸 아이군요. 활을 골랐다고요?||저는 엘린, 숲에서 온 엘프예요.||활은 숨을 고르고 바람을 읽는 무기예요. 천천히 배워 봐요."),
    depart: T("숲 밖으로", "이제 숲 밖으로 나갈 때예요.||농장의 허수아비들이 움직인대요. 농부 올라프 씨를 찾아가 도와줘요.||오래전 요르문간드가 바다를 일으켜 제 고향 숲을 삼켰어요. 일족의 보물, 엘프의 활도요.||요르문간드를 쓰러뜨리면 돌아와 줘요. 그 활 이야기를 해 줄게요.", "농장의 올라프 씨를 찾아가요.", ""),
    home: "돌아왔군요… 숲이 다시 녹고 있어요. 새들이 노래해요.||약속을 지켜 줘서 고마워요. 정말로요.",
    outro: T("영웅의 귀환", "이제 제가 가르칠 것은 없어요. 당신은 바람 그 자체예요.||할아버지께 가 봐요. 기다리고 계실 거예요.", "하랄 할아버지께요.", "엘린이 그러더구나. 숲이 다시 노래한다고!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 지킨 영웅이 되었구나.||자랑스럽다. 네 모험은 이제부터 시작이란다."),
  },
  wand: {
    intro: T("볼바를 찾아서", "완드를 골랐으니 마법사 볼바에게 가 봐라.||앞날을 읽는다는 이상한 노파다. 이 추천서를 건네라.", "볼바에게 추천서를 건네라.", "왔구나. 룬이 네가 올 것을 말해 주었느니라.||나는 볼바, 세계수의 룬을 읽는 자.||완드는 마력으로 쏘는 무기. 마력을 기르고, 시전하는 동안 흔들리지 말거라."),
    depart: T("운명의 첫 줄", "룬이 말하는구나. 네 여정이 시작될 때라고.||농장의 허수아비들이 움직이느니라. 농부 올라프를 찾아가 도와주거라.||예언을 들려주마. '흑룡이 뿌리를 갉고, 서리 거인이 눈을 뜨고, 작은 자가 그를 잠재우리라.'||작은 자, 그것이 바로 너니라. 가거라.", "농장의 올라프를 찾아가거라.", ""),
    home: "…룬이 고요해졌구나. 예언이 끝났느니라.||작은 자가 서리 거인을 잠재웠구나. 이제 너의 이야기는 네가 쓰거라.",
    outro: T("영웅의 귀환", "내 룬은 더 이상 네 앞날을 읽지 못하느니라. 네 운명은 이제 네 것이니.||할아비에게 가거라. 기다리고 있느니라.", "하랄에게 가거라.", "볼바의 예언 속 작은 자가… 정말 우리 손자였구나!||언덕에서 모험을 꿈꾸던 꼬마가 예언을 이룬 영웅이 되었구나.||자랑스럽다, 얘야. 네 모험은 이제부터 시작이란다."),
  },
};

/** 지역 NPC의 첫인사 (그 지역으로 가는 퀘스트의 완료 대사) */
const ARRIVE = {
  farm: "{master}의 제자라고? 어이구, 잘 왔구먼!||나는 농부 올라프여. 요즘 밭의 허수아비들이 저절로 움직여서 큰일이구먼.",
  desert: "올라프 영감이 보냈다고? 하, 마침 잘 왔다!||나는 라그나르, 이 사막을 건너는 대상의 우두머리다. 요즘 길이 완전히 막혔어.",
  sea: "라그나르 녀석이 보냈다고? 허허, 반갑네.||나는 비요른, 이 바다에서 오십 년을 산 늙은 선장이라네.",
  north: "비요른 영감이 보냈어? …좋아, 쓸 만해 보이네.||난 스카디, 북쪽 눈밭의 사냥꾼이야. 여긴 지금 서리가 모든 걸 삼키고 있어.",
};

/** 다음 지역 (또는 마을의 스승) 으로 가는 퀘스트 — 키 = 가는 곳 (완료 대사는 ARRIVE · 스승은 MASTER.home) */
const TRAVEL = {
  desert: T("사막의 길목", "고맙네, 젊은이. 그런데 사막 길목에서 상인들이 발이 묶였다는구먼.||대상 우두머리 라그나르를 찾아가 보게. 자네 같은 사람이 필요할 거여.", "사막의 라그나르를 찾아가 보게. 대상 천막에 있을 거여.", ""),
  sea: T("항구의 선장", "사막은 이제 걱정 없겠군. 그런데 바닷길에서 배들이 가라앉는다는 소식이 왔다.||항구의 늙은 선장 비요른을 찾아가 봐. 바다라면 그 영감이 제일 잘 알지.", "바닷가의 비요른 선장을 찾아가. 항구에 있을 거다.", ""),
  north: T("북쪽의 사냥꾼", "바다는 이제 걱정 없겠네. 그런데 북쪽에서 서리가 내려온다는 소문이 도네.||서리 사냥꾼 스카디를 찾아가 보게. 그 땅은 그 아가씨가 제일 잘 안다네.", "북쪽의 스카디를 찾아가게. 눈밭의 사냥꾼 막사에 있을 걸세.", ""),
  home: T("스승에게", "북쪽 하늘이 녹고 있어. 네 덕분이야.||이제 마을로 돌아가. 네 스승이 기다리고 있을 거야.", "마을의 네 스승에게 돌아가.", ""),
};

/**
 * 보스마다 [처치, 모으기, 보스] 대사 (모든 길 같음). 보스 완료 대사에서 길마다 다른 것:
 * hero (요르문간드) · transcend (이미르) — legend = 그 길의 전설 무기가 있음 (전설 퀘스트 안내), mat = 그 길 무기 제작 주재료를 줌
 */
const ARC_TEXT = {
  scarecrow_king: [
    T("움직이는 허수아비", "밭에 세워 둔 허수아비들이 밤마다 걸어 다녀서 아무도 밭에 못 나가는구먼.||허수아비 열을 쓰러뜨려 주게. 짚단을 휘두르니 조심하고.", "허수아비 열이여. 밭 한가운데에 몰려 있구먼.", "어이구, 시원하게도 쓰러뜨리는구먼! 자네 솜씨가 보통이 아니여."),
    T("질긴 짚단", "쓰러진 허수아비에서 나온 짚은 이상하게 질기더구먼. 뭔가 기운이 남았어.||낡은 짚단 여섯 개만 모아다 주게. 어디서 온 기운인지 살펴봐야겠구먼.", "낡은 짚단 여섯이여. 허수아비를 쓰러뜨리면 가끔 떨어지는구먼.", "이 짚 좀 보게. 밭 가운데 제단 쪽으로 기운이 흘러가는구먼.||허수아비들을 부리는 놈이 거기 있는 게 틀림없어."),
    T("수확의 왕", "놈의 정체가 드러났구먼. 수확의 왕, 허수아비 킹이여.||밭 가운데 제단을 터치하면 놈이 나온다네. 동료를 모아 쓰러뜨려 주게!", "놈은 분신을 만들어 숨는다네. 진짜는 어깨에 까마귀를 얹고 있구먼.", "해냈구먼! 이제 다시 밭을 갈 수 있겠어.||{master}께서 자네가 이기면 주라고 맡겨 둔 무기여. 받게!"),
  ],
  scorpions: [
    T("모래 위의 굴론 떼", "모래 언덕마다 굴론 떼가 들끓어서 낙타가 놀라 달아나고 있다.||굴론 열둘을 쫓아내 줘. 빠르고 자꾸 달려드니 맞받아쳐!", "굴론 열둘이다. 달려들기 전에 몸을 낮추니 그때를 노려.", "하! 이제야 낙타들이 진정하는군. 솜씨 좋은데?"),
    T("굴론 털가죽", "모래바람을 막으려면 두꺼운 천막이 필요해. 굴론 털가죽이 그만이지.||털가죽 여덟 장만 구해 줘. 굴론을 잡으면 가끔 떨어진다.", "굴론 털가죽 여덟 장이다. 사막의 굴론이 떨어뜨려.", "튼튼한 천막이 되겠군! 이제 모래바람도 버티겠어.||그런데 길목을 막은 진짜 문제는 따로 있다."),
    T("모래와 바위의 한 쌍", "길목에 거대한 전갈 두 마리가 자리를 잡았다. 모래와 바위의 한 쌍이지.||둘이 붙어 있으면 서로를 지켜. 동료와 나눠서 떼어 놓고 쓰러뜨려!", "둘을 떼어 놓아. 바위 전갈이 울부짖으면 등을 돌리고.", "길이 열렸다! 대상이 다시 움직일 수 있어.||상인 조합이 고맙다며 이 무기를 내놨다. 네 손에 맞춰 벼렸다더군."),
  ],
  great_worm: [
    T("흔들리는 모래", "전갈이 사라지자 이번엔 땅이 울린다. 굴론들이 겁에 질려 날뛰고 있어.||굴론 열을 잠재워 줘. 땅속에서 뭔가 큰 게 깨어나고 있다.", "굴론 열이다. 땅이 흔들리면 잠깐 멈춰서 살펴.", "굴론들이 그렇게 겁먹은 건 처음 본다. 땅속의 그놈 때문이야."),
    T("모래 망토", "땅속 놈을 상대하려면 모래에 몸을 숨겨야 해.||굴론 털가죽 여섯 장으로 모래 망토를 지어 주지. 털가죽을 모아 와.", "굴론 털가죽 여섯 장이다.", "모래 망토다. 이걸 두르면 놈도 쉽게 못 찾을 거다.||이제 그놈을 불러낼 차례야."),
    T("대지의 포식자", "거대 웜이 깨어났다. 땅속을 옮겨 다니다 갑자기 솟구치지.||사막 깊은 곳의 제단을 터치하면 놈이 나온다. 땅이 갈라지는 소리를 듣고 피해!", "땅이 흔들리면 그 자리를 떠. 놈이 귀 기울일 땐 움직이지 마.", "땅이 조용해졌다! 사막이 다시 숨을 쉬는군.||놈이 떨어뜨린 재료는 모아 둬. 마을의 군나르가 무기를 벼려 줄 거다."),
  ],
  kraken: [
    T("해안의 껍질 괴물", "해안에 스켈야스크림슬리가 몰려와 어부들이 그물을 못 던지네.||열둘만 쫓아 주게. 목이 길어서 생각보다 멀리서 물어뜯는다네.", "스켈야스크림슬리 열둘일세. 껍질이 두꺼우니 깊게 치게.", "허허, 해안이 조용해졌군. 어부들이 고마워할 걸세."),
    T("단단한 껍질", "가라앉은 신전으로 가려면 배를 손봐야 하네.||단단한 껍질 여덟 개로 뱃바닥을 덧대면 촉수도 못 뚫지.", "단단한 껍질 여덟 개일세. 해안의 껍질 괴물이 떨어뜨리네.", "이제 배가 웬만한 촉수엔 끄떡없겠군.||자, 바다 밑에 무엇이 있는지 말해 주지."),
    T("심해의 군주", "바다 깊은 곳에서 크라켄이 올라왔네. 배들이 가라앉은 건 다 놈 짓이야.||가라앉은 신전의 제단을 터치하면 놈이 나온다네. 촉수부터 끊게!", "촉수가 살아 있으면 놈이 낫는다네. 큰 물결이 오면 기둥 뒤로 숨게.", "바다가 잠잠해졌군! 오십 년 뱃사람 인생에 이런 날이 다 오다니.||놈이 떨어뜨린 재료는 모아 두게. 마을의 군나르가 무기를 벼려 줄 걸세."),
  ],
  jormungandr: [
    T("물가의 닉스", "요즘 물가에서 닉스가 사람들을 물속으로 끌어들이려 하네.||열둘을 물리쳐 주게. 붙잡히면 몸이 무거워지니 거리를 재게.", "닉스 열둘일세. 물가에서는 발을 조심하게.", "고맙네. 닉스가 이렇게 날뛰는 건 더 큰 놈이 깨어난 탓일세."),
    T("닉스 비늘", "뱀의 섬으로 가는 물길은 독이 섞여 있다네.||닉스 비늘 여덟 개를 갈아 뱃전에 바르면 독물에도 배가 상하지 않지.", "닉스 비늘 여덟 개일세. 물가의 닉스가 떨어뜨리네.", "물빛 비늘이로군. 이제 배가 뱀의 섬까지 버틸 걸세.||마지막으로, 그 뱀 이야기를 해 주지."),
    T("세계를 휘감은 뱀", "요르문간드가 몸을 일으켰네. 세계를 휘감은 뱀이지.||뱀의 섬 제단을 터치하면 놈이 나온다네. 독을 조심하게!", "해자의 독물에는 들어가지 말게. 범람하면 흰 고리 안으로 피하게.", ""),
  ],
  nidhogg: [
    T("북쪽 무덤의 망자", "북쪽 무덤에서 드라우그가 일어났어. 서리를 두른 망자들이야.||열둘을 다시 잠재워. 맞으면 몸이 굳으니 맞지 말고 베.", "드라우그 열둘. 몸이 굳으면 물러서.", "…망자들이 일어난 건 징조야. 뭔가 큰 게 깨어나고 있어."),
    T("서리 뼛조각", "드라우그의 뼈에는 녹지 않는 서리가 남아.||서리 뼛조각 여덟 개를 가져와. 그 서리가 어디서 오는지 읽어 볼 거야.", "서리 뼛조각 여덟 개. 드라우그가 떨어뜨려.", "…이 서리, 세계수 뿌리 쪽에서 흘러와."),
    T("세계수를 갉는 흑룡", "세계수의 뿌리를 갉는 흑룡, 니드호그가 모습을 드러냈어.||세계수가 쓰러지면 서리를 막을 게 없어. 뿌리의 제단을 터치해서 불러내.", "흑룡이 날아오르면 기다려. 낙인이 찍히면 동료와 떨어져.", "세계수가 숨을 돌렸어. …잘했어.||그런데 하늘이 이상해. 해와 달이 쫓기고 있어."),
  ],
  skoll_hati: [
    T("돌 고리의 트롤", "해와 달의 돌 고리 둘레를 트롤들이 차지했어.||여덟을 쓰러뜨려. 상처가 금방 아무니까 쉬지 말고 몰아쳐.", "트롤 여덟. 재생하기 전에 몰아쳐.", "길이 열렸어. 트롤 맷집이 장난이 아니지?"),
    T("트롤 엄니", "늑대들은 트롤 엄니 냄새를 싫어해.||엄니 여섯 개로 부적을 깎아 줄게. 늑대들의 눈을 피할 수 있을 거야.", "트롤 엄니 여섯 개. 트롤이 떨어뜨려.", "부적이 됐어. 이제 늑대들을 불러낼 차례야."),
    T("해와 달을 쫓는 늑대", "하늘이 어지러워. 해를 쫓는 스콜과 달을 쫓는 하티가 내려왔어.||돌 고리의 제단을 터치해서 불러내. 낮과 밤에 따라 늑대들이 달라져.", "낮에는 스콜이, 밤에는 하티가 거세져. 시간을 읽어.", "하늘이 제자리로 돌아왔어. …잘했어.||이제 남은 건 하나야. 서리 거인."),
  ],
  ymir: [
    T("거인의 숨결", "이미르의 숨결에 망자들이 다시 일어났어. 빙원을 드라우그가 메우고 있어.||열을 잠재워. 거인에게 가는 길을 열어야 해.", "드라우그 열. 몸이 굳으면 물러서.", "길이 열렸어. 저 너머가 태초의 빙원이야."),
    T("빙원의 화로", "이미르의 숨결은 모든 걸 얼려. 불씨를 지킬 화로가 필요해.||트롤 엄니 여섯 개로 화로를 깎을 거야. 엄니를 모아 와.", "트롤 엄니 여섯 개. 트롤이 떨어뜨려.", "화로가 됐어. 이 불씨가 꺼지지 않으면 거인의 숨결도 버틸 수 있어.||…이제 거인을 만나러 가자."),
    T("태초의 서리 거인", "태초의 서리 거인, 이미르가 완전히 깨어났어.||빙원의 제단을 터치해서 불러내. 숨결이 오면 불이 켜진 모닥불 곁으로 가.", "숨결이 오면 모닥불 곁으로. 다리를 노려 무릎을 꿇려.", ""),
  ],
};

/** 요르문간드 · 이미르 완료 대사: legend = 그 길의 전설 무기 (스승의 전설 퀘스트 안내), mat = 제작 주재료 ({mat}) */
const BOSS_DONE = {
  jormungandr: {
    legend: "놈의 뱃속 깊은 곳에서 무언가 빛나는 걸 봤네. 한 번으로는 토해 내지 않을 모양이야.||자네 스승에게 가 보게. 그분이라면 방법을 알 걸세.",
    mat: "뱀이 쓰러지며 토해 낸 보물 더미에서 쓸 만한 재료를 골라 두었네.||{mat}일세. 다른 재료와 모아 마을의 군나르에게 가져가면 영웅 무기를 벼려 줄 걸세.",
  },
  ymir: {
    legend: "…해냈어. 거인이 쓰러지자 얼음 속에서 뭔가가 울렸어.||네 스승한테 가 봐. 네 무기 이야기는 그 사람이 알 거야.",
    mat: "…해냈어. 거인이 쓰러진 자리에서 이걸 건졌어.||이건 {mat}. 다른 재료와 모아서 군나르에게 가져가. 초월 무기를 벼려 줄 거야.",
  },
};

// ============================================================ 퀘스트 만들기
/** 보상 아이템 키 → 아이템 ID (레이드 재료 · 보스 재료 · ticket · ticket_adv · 전리품 몬스터 키) */
export function rewardItemId(k) {
  if (RAID_MATERIALS[k]) return RAID_MATERIALS[k].item;
  if (BOSS_MATERIALS[k]) return BOSS_MATERIALS[k].item;
  if (k === "ticket") return RUNE.ticketItem;
  if (k === "ticket_adv") return RUNE.advTicketItem;
  if (MONSTER_DROPS[k]) return MONSTER_DROPS[k].item;
  return k;
}

/** 아이템 ID → 보이는 이름 */
export function rewardItemName(id) {
  for (const m of Object.values(RAID_MATERIALS)) if (m.item === id) return m.name;
  for (const m of Object.values(BOSS_MATERIALS)) if (m.item === id) return m.name;
  for (const m of Object.values(MONSTER_DROPS)) if (m.item === id) return m.name;
  if (id === RUNE.ticketItem) return RUNE.ticketName;
  if (id === RUNE.advTicketItem) return RUNE.advTicketName;
  return ITEM_NAMES[id] ?? id.replace(/^minecraft:/, "");
}

function items(list) {
  return (list ?? []).map(([k, n]) => ({ id: rewardItemId(k), amount: n }));
}

function rewardOf(r) {
  return { emerald: r.emerald ?? 0, xp: r.xp ?? 0, points: r.points ?? 0, weapon: r.weapon ?? "", item: null, items: items(r.items) };
}

/**
 * 요르문간드 (@hero) · 이미르 (@transcend) 보상: 그 길 · 등급의 무기를 만들 수 있으면 주재료 절반 + 제작 에메랄드 · 별철 광석,
 * 전설 무기 길이면 (만들 수 없음 — 전설 퀘스트) 같은 에메랄드 · 별철 광석만. 룬 뽑기권 1 · 2장, 경험치 2 · 3레벨
 * @returns {{reward: any, mat: string}} mat = 준 주재료 키 ("" = 없음)
 */
export function bossReward(type, grade) {
  const big = grade === "transcend";
  const ticket = ["ticket", big ? 2 : 1];
  const craft = craftOf(type, grade);
  const emerald = big ? 128 : 64;
  const ore = big ? 4 : 2;
  if (!craft) return { reward: rewardOf({ emerald, xp: big ? 3 : 2, items: [["ore", ore], ticket] }), mat: "" };
  return { reward: rewardOf({ emerald: craft.emerald, xp: big ? 3 : 2, items: [[craft.mat, Math.floor(craft.count / 2)], ["ore", craft.ore], ticket] }), mat: craft.mat };
}

/** 몬스터 이름 (목표 글자) */
function mobLabel(k) {
  return `${MONSTERS[k].name}(Lv.${MONSTERS[k].level})`;
}

/** v2.9.0 단계 ID: "mq_<길>_rNN" (예전 18단계 "mq_<길>_NN" 과 겹치지 않게) */
export function mainId(type, step) {
  return `${MAIN_PREFIX}${type}_r${String(step).padStart(2, "0")}`;
}

/** v2.9.0 전설 퀘스트 ID (sidequest.js) — grade: "hero" · "transcend" */
export function legendId(type, grade) {
  return `sq_legend_${type}_${grade}`;
}

/** 시작 퀘스트 ID */
export const MAIN_START = `${MAIN_PREFIX}start`;
export const MAIN_TRIAL = `${MAIN_PREFIX}trial`;
export const MAIN_CHOOSE = `${MAIN_PREFIX}choose`;

/** 대사 속 {master} · {mat} 채우기 */
function fill(text, type, mat = "") {
  return String(text ?? "")
    .replaceAll("{master}", mainNpcCall(MAIN_PATHS[type]?.mentor ?? ""))
    .replaceAll("{mat}", mat ? (BOSS_MATERIALS[mat]?.name ?? mat) : "");
}

/**
 * 메인 퀘스트 전체 (정의 그대로, quest.js 가 다듬어 씀)
 * 추가 필드: main(메인), path(그 길에서만, ""=모두), ord(순서), give(수락할 때 주는 시험용 무기 종류),
 *   doneBy(길마다 완료 대사: 고르기), finale(마지막), step(길 안의 단계 1~PATH_STEPS)
 */
export function buildMainQuests() {
  const out = [];
  const P = PROLOGUE;
  out.push({ id: MAIN_START, ord: 1, path: "", giver: "main_1", type: "talk", target: "main_2", targetName: mainNpcCall("main_2"), count: 1, ...P.start, reward: rewardOf({ emerald: 2 }), requires: "", minLevel: 0 });
  out.push({ id: MAIN_TRIAL, ord: 2, path: "", giver: "main_2", type: "try", target: "", targetName: "", count: TYPE_ORDER.length, ...P.trial, give: [...TYPE_ORDER], reward: rewardOf({ emerald: 3, xp: 1 }), requires: MAIN_START, minLevel: 0 });
  out.push({ id: MAIN_CHOOSE, ord: 3, path: "", giver: "main_2", type: "choose", target: "", targetName: "", count: 1, ...P.choose, doneBy: { ...CHOSEN }, reward: rewardOf({ emerald: 2 }), requires: MAIN_TRIAL, minLevel: 0 });

  for (const type of PATH_ORDER) {
    const M = MASTER[type];
    let prev = MAIN_CHOOSE;
    pathSteps(type).forEach((s, i) => {
      const step = i + 1;
      /** @type {any} */
      const q = { id: mainId(type, step), ord: 10 + step, step, path: type, giver: s.giver, requires: prev, minLevel: s.lv, count: 1, target: "", targetName: "" };
      let t = T("", "", "", "");
      let reward;
      let mat = "";
      if (s.kind === "intro" || s.kind === "depart" || s.kind === "travel" || s.kind === "home" || s.kind === "outro") {
        const to = s.to ?? "";
        Object.assign(q, { type: "talk", target: to, targetName: mainNpcCall(to) });
        if (s.kind === "intro") (t = M.intro), (reward = rewardOf({ emerald: 3 }));
        else if (s.kind === "depart") (t = { ...M.depart, done: ARRIVE.farm }), (reward = rewardOf({ emerald: TRAVEL_EMERALD.farm }));
        else if (s.kind === "travel") (t = { ...TRAVEL[String(s.region)], done: ARRIVE[String(s.region)] }), (reward = rewardOf({ emerald: TRAVEL_EMERALD[String(s.region)] }));
        else if (s.kind === "home") (t = { ...TRAVEL.home, done: M.home }), (reward = rewardOf({ emerald: TRAVEL_EMERALD.home }));
        else (t = M.outro), (reward = rewardOf({ emerald: 50, points: 3, items: [["ticket_adv", 1]] })), (q.finale = true);
      } else {
        const arc = s.arc;
        const k = String(s.mob);
        const [kt, ct, bt] = ARC_TEXT[arc.boss];
        if (s.kind === "kill") {
          Object.assign(q, { type: "kill", target: `rpg:${k}`, targetName: mobLabel(k), count: s.count });
          t = kt;
          reward = rewardOf({ emerald: arc.emerald[0], xp: arc.xp[0] });
        } else if (s.kind === "collect") {
          Object.assign(q, { type: "collect", target: MONSTER_DROPS[k].item, targetName: MONSTER_DROPS[k].name, count: s.count });
          t = ct;
          reward = rewardOf({ emerald: arc.emerald[1], xp: arc.xp[1] });
        } else {
          const boss = String(s.boss);
          Object.assign(q, { type: "boss", target: boss, targetName: BOSSES[boss].name });
          t = bt;
          if (typeof arc.reward === "string") {
            const grade = arc.reward === "@transcend" ? "transcend" : "hero";
            const r = bossReward(type, grade);
            reward = r.reward;
            mat = r.mat;
            t = { ...bt, done: r.mat ? BOSS_DONE[boss].mat : BOSS_DONE[boss].legend };
          } else reward = rewardOf(arc.reward);
        }
      }
      // 단계 표의 "l10_noble" = 그 길의 무기 종류
      if (reward?.weapon && /^l\d+_/.test(reward.weapon)) reward.weapon = `${type}_${reward.weapon}`;
      Object.assign(q, { title: t.title, offer: fill(t.offer, type, mat), progress: fill(t.progress, type, mat), done: fill(t.done, type, mat), reward });
      out.push(q);
      prev = q.id;
    });
  }
  return out;
}

/** 무기를 고른 뒤 처음 받는 퀘스트 (조교 → 스승) */
export function firstPathQuest(type) {
  return mainId(type, 1);
}

/** 길 이름 ("sword" …) 인지 */
function isPath(type) {
  return typeof type === "string" && Object.prototype.hasOwnProperty.call(MAIN_PATHS, type);
}

/**
 * v2.9.0: 예전 (v2.6.0~2.8.x) 18단계 진행 → 지역 퀘스트 단계. 예전에 끝낸 마지막 단계 (인덱스) → 지역 퀘스트에서 끝낸 것으로 볼 마지막 단계
 * 예전 단계: 1 스승 · 2 허수아비 · 3 짚단 · 4 굴론 · 5 허수아비 킹 · 6 심부름 · 7 스켈야스크림슬리 · 8 전갈 · 9 닉스 · 10 심부름 ·
 *   11 크라켄/웜 · 12 드라우그 · 13 요르문간드 · 14 심부름 · 15 트롤 · 16 니드호그/스콜과 하티 · 17 이미르 · 18 영웅의 귀환
 * 같은 레벨의 두 보스 중 하나를 잡았으면 둘 다 끝낸 것으로 (그 단계를 이미 지났으므로). 17 이미르는 스승에게 보고했으므로 마을 → 30 (스승에게) 까지
 */
const OLD_TO_NEW = [0, 1, 3, 4, 4, 5, 6, 6, 9, 9, 9, 16, 16, 19, 20, 20, 26, 30, 31];
/** 예전 보스 단계 → 새 보스 단계: 잡았는데 아직 보고하지 않았으면 (진행 중 · 처치 1) 새 보스 단계를 보고만 남은 채로 (보상은 지역 NPC에게) */
const OLD_BOSS = { 5: 5, 8: 9, 11: 16, 13: 19, 16: 26, 17: 29 };
/** 받아 둔 예전 말 걸기 단계 → 같은 새 단계 (진행 중 그대로): 1 스승 찾아가기 · 18 영웅의 귀환 */
const OLD_TALK = { 1: 1, 18: 31 };
const OLD_ID = /^mq_(sword|dagger|axe|shield|bow|wand)_(\d\d)$/;
/** 예전 (18단계) 에서 그 몬스터를 잡던 단계 — v2.7.0 반복 의뢰가 열리던 단계 (옮긴 사람의 반복 의뢰가 다시 잠기지 않게) */
const OLD_KILL_STEP = { scarecrow: 2, gulon: 4, skeljaskrimsli: 7, nix: 9, draugr: 12, troll: 15 };

/** 예전 기록에서 그 몬스터의 처치 단계를 끝냈는지 (예전 기록은 지우지 않고 둠) */
export function oldKillDone(st, path, mob) {
  const n = OLD_KILL_STEP[mob];
  return !!n && isPath(path) && st[`${MAIN_PREFIX}${path}_${String(n).padStart(2, "0")}`]?.c > 0;
}
/** 옮긴 기록 표시 (한 번만 옮김). 예전 기록은 지우지 않음 — 새 버전은 모르는 ID 라 무시하고, 2.8.x 로 되돌려도 예전 진행이 남음 */
export const MIGRATED_KEY = "_mig290";
/** 전설 퀘스트를 "모두 채움" 으로 둘 때의 목표별 수 (목표 수보다 크면 됨) */
const FULL = 9999;

/**
 * 플레이어 진행 (quest.js "rpg:qs") 에 예전 메인 단계가 있으면 (처음 한 번) 지역 퀘스트 단계로 옮김. 바꿨으면 true
 * - 끝낸 마지막 단계까지를 끝낸 것으로 (보상은 다시 주지 않음)
 * - 진행 중이던 단계: 보스를 잡고 보고 전이면 그 새 보스 단계를 보고만 남은 채로, 스승 찾아가기 · 영웅의 귀환은 그대로 진행 중, 나머지는 버림 (지역 NPC에게 다시 받음)
 * - 전설 무기: 예전 요르문간드 (13 — 활 · 방패) · 이미르 (17 — 칼 · 방패) 단계를 끝냈으면 받았으므로 그 전설 퀘스트도 끝,
 *   잡았는데 보고 전이면 그 전설 퀘스트를 모두 채운 채로 (스승에게 보고하면 받음)
 * @param {Record<string, any>} st
 * @param {string} path 고른 무기의 길 ("" = 아직)
 */
export function migrateMainState(st, path) {
  if (st[MIGRATED_KEY]) return false;
  let last = 0;
  let ready = 0;
  let talk = 0;
  let found = false;
  for (const id of Object.keys(st)) {
    const m = OLD_ID.exec(id);
    if (!m) continue;
    found = true;
    if (m[1] !== path) continue;
    const e = st[id];
    const n = Number(m[2]);
    if (e?.c > 0) last = Math.max(last, n);
    else if (e?.s === "a") {
      if (OLD_TALK[n]) talk = n;
      else if (OLD_BOSS[n] && (Number(e.n) || 0) >= 1) ready = n;
    }
  }
  if (!found) return false;
  st[MIGRATED_KEY] = { s: "d", n: 0, c: 1 };
  if (!isPath(path)) return true;
  const done = (id) => {
    if (!(st[id]?.c > 0)) st[id] = { s: "d", n: 0, c: 1 };
  };
  let to = OLD_TO_NEW[Math.min(OLD_TO_NEW.length - 1, last)];
  const readyAt = ready > last ? OLD_BOSS[ready] : 0;
  if (readyAt) to = Math.max(to, readyAt - 1);
  for (let s = 1; s <= to; s++) done(mainId(path, s));
  if (readyAt && !(st[mainId(path, readyAt)]?.c > 0)) st[mainId(path, readyAt)] = { s: "a", n: 1, c: 0 };
  else if (talk > last && OLD_TALK[talk] === to + 1) st[mainId(path, OLD_TALK[talk])] = { s: "a", n: 0, c: 0 };
  const legend = (grade, oldStep, paths) => {
    if (!paths.includes(path)) return;
    const id = legendId(path, grade);
    if (last >= oldStep) done(id);
    else if (ready === oldStep && !(st[id]?.c > 0)) st[id] = { s: "a", n: 0, c: 0, o: new Array(16).fill(FULL) };
  };
  legend("hero", 13, ["bow", "shield"]);
  legend("transcend", 17, ["sword", "shield"]);
  return true;
}
