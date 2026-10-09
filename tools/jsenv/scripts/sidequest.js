import { BOSS_ORDER, BOSSES, MONSTER_DROPS, MONSTER_ORDER, MONSTERS, REPEAT_QUESTS } from "./config.js";
import { MAIN_CHOOSE, MAIN_PATHS, legendId, mainBossStep, mainKillStep, mainNpcCall, rewardItemId } from "./mainquest.js";

/**
 * 메인 이야기 밖의 들어 있는 퀘스트 (v2.7.0). 정의는 이 파일에만 있음 (세계에 저장하지 않음, 편집 창에서 고칠 수 없음, ID "sq_…")
 * - 반복 의뢰 (side "repeat", 여관 주인 잉그리드 main_10): 몬스터마다 하나 — 메인 퀘스트에서 그 몬스터 처치 단계 (길마다 같은 단계) 를
 *   끝내면 열림. 전리품을 가져오면 에메랄드 + 경험치 (포인트, 최고 레벨이 그 몬스터의 xpUntil 이상이면 0). 보고하면 바로 다시 받을 수 있음
 * - 왕실 퀘스트 (side "royal", 왕녀 시그리드 main_11): 35렙부터, 무기의 길을 고른 사람만. 7단계 (말 걸기 · 전리품 · 보스 · 트롤 토벌),
 *   마지막 (이미르) 을 마치면 무기의 길에 맞는 초월 갑옷 (황제 세트) 한 벌 — 칼 힘 · 단검 민첩 · 도끼 힘+체력 · 방패 체력 · 활 민첩 · 완드 마력
 * - v2.9.0 전설 퀘스트 (side "legend", 그 무기의 길의 스승 main_3~8): 전설 무기 4개 (듀란드의 방패 · 엘프의 활 · 그람 · 아이기스의 방패) 는
 *   만들 수 없고 이 퀘스트로만 (사용자 요청: "그 무기 길에서 어려운 퀘스트를 해결 시 무기를 주는 방식, 일반 몹 40마리 + 보스 3마리 · 모든 보스 종류별로").
 *   메인 이야기와 따로 (선택), 메인의 그 보스 단계 (afterStep) 를 끝내면 열림. 목표 종류 "trial" = 처치 · 보스 목표 여럿을 한 퀘스트에서 셈 (받은 뒤에 잡은 것만)
 * - v2.12.0 초월 보스 전설 퀘스트 (side "legend", 왕녀 시그리드 main_11, 무기의 길과 무관): 메인 이야기에는 없는 초월 보스 (첫째: 수르트) 를
 *   잡는 퀘스트 (사용자 요청: "메인퀘스트로 없고 그냥 전설퀘스트로 왕녀한테 받아서"). 왕실 퀘스트를 모두 마치고 40렙부터, 받은 뒤에 잡은 것만 셈
 * - 대사 규칙은 메인 퀘스트와 같음: "|" 줄바꿈, "||" 다음 장, 한 장 70자 이하
 */

export const SIDE_PREFIX = "sq_";
export const REPEAT_GIVER = "main_10";
export const ROYAL_GIVER = "main_11";
/** 왕실 퀘스트를 받을 수 있는 레벨 */
export const ROYAL_LEVEL = 35;
/** 무기의 길 → 왕실 퀘스트 보상 갑옷 종류 (config.js ARMOR_TYPES) */
export const PATH_ARMOR = { sword: "str", dagger: "dex", axe: "strvit", shield: "vit", bow: "dex", wand: "int" };

export function repeatId(mob) {
  return `${SIDE_PREFIX}rep_${mob}`;
}
export function royalId(step) {
  return `${SIDE_PREFIX}royal_${String(step).padStart(2, "0")}`;
}
/** v2.12.0 초월 보스 전설 퀘스트 ID (왕녀가 줌, 무기의 길과 무관) */
export function transcendLegendId(boss) {
  return `${SIDE_PREFIX}legend_${boss}`;
}

/** [제목, 줄 때, 진행 중, 완료] */
const T = (title, offer, progress, done) => ({ title, offer, progress, done });

/** 반복 의뢰 대사 (몬스터마다) — {n} = 개수 */
const REPEAT_LINES = {
  scarecrow: T("짚단 거두기", "여관 지붕을 새로 이으려는데 짚이 모자라요.||허수아비가 떨어뜨리는 낡은 짚단 {n}개만 가져다주실래요? 몇 번이든 좋아요!", "낡은 짚단 {n}개요. 허수아비를 쓰러뜨리면 가끔 떨어져요.", "고마워요! 이걸로 지붕이 따뜻해지겠어요.||또 모이면 언제든 가져와 주세요!"),
  gulon: T("털가죽 이불", "겨울 이불을 만들려고요. 굴론 털가죽이 그렇게 따뜻하대요.||{n}장만 가져다주실래요? 손님들이 정말 좋아할 거예요!", "굴론 털가죽 {n}장이요. 사막의 굴론이 떨어뜨려요.", "푹신해라! 오늘 밤 손님들은 따뜻하게 자겠어요.||또 부탁해도 되죠?"),
  skeljaskrimsli: T("껍질 그릇", "스켈야스크림슬리 껍질로 만든 그릇이 요즘 상인들 사이에서 인기래요.||단단한 껍질 {n}개만 구해 주시면 값을 넉넉히 쳐 드릴게요!", "단단한 껍질 {n}개요. 해안의 껍질 괴물이 떨어뜨려요.", "정말 단단하네요! 상인들이 줄을 서겠어요.||또 구하면 가져와 주세요."),
  nix: T("물빛 등불", "닉스 비늘에 불을 비추면 물빛이 일렁여서 정말 예뻐요.||여관 등불로 쓰려고요. 비늘 {n}개만 부탁해요!", "닉스 비늘 {n}개요. 물가에서는 조심하세요!", "와, 여관이 호수 속처럼 반짝여요!||또 모이면 가져와 주세요."),
  draugr: T("얼음 창고", "서리 뼛조각은 녹지 않는 얼음처럼 차갑대요.||창고에 넣어 두면 음식이 상하지 않는다나요? {n}개만 구해 주세요!", "서리 뼛조각 {n}개요. 북쪽 무덤의 드라우그가 떨어뜨려요.", "창고가 얼음장처럼 차가워졌어요! 고마워요.||또 부탁드려요."),
  troll: T("엄니 공예", "트롤 엄니로 깎은 장식은 귀족들도 탐내는 물건이래요.||{n}개만 구해 주시면 제가 상인들한테 잘 팔아 볼게요!", "트롤 엄니 {n}개요. 북쪽 산길의 트롤이 떨어뜨려요. 조심하세요!", "이렇게 큰 엄니라니! 상인들이 깜짝 놀라겠어요.||또 구하면 꼭 가져와 주세요."),
};

/**
 * 왕실 퀘스트 7단계. goal: talk [NPC] · collect [전리품 몬스터 키, 개수] · boss [보스 키] · kill [몬스터 키, 수]
 * reward: items = [아이템 키, 개수] (mainquest.js rewardItemId), armor = 무기의 길에 맞는 초월 갑옷 (마지막)
 * @type {{lv: number, goal: any, reward: {emerald?: number, items?: any[], armor?: string}, finale?: boolean, title: string, offer: string, progress: string, done: string}[]}
 */
const ROYAL = [
  {
    lv: ROYAL_LEVEL,
    goal: { type: "talk", npc: "main_1" },
    reward: { emerald: 20, items: [["ticket", 1]] },
    ...T(
      "왕녀의 편지",
      "모험가님, 저는 이 나라의 왕녀 시그리드예요. 신분을 숨기고 영웅을 찾아왔어요.||북쪽에서 서리 거인이 깨어나면 왕국도 무사하지 못해요.||먼저 이 편지를 하랄 어르신께 전해 주세요. 선왕과 북쪽 원정을 떠났던 분이라 들었어요.",
      "하랄 어르신께 왕실의 편지를 전해 주세요.",
      "왕실의 봉인이로구나… 시그리드 왕녀님이 직접 오셨다고?||선왕 폐하와 북쪽 원정을 떠났던 게 엊그제 같구나. 그때도 서리가 문제였지.||얘야, 왕녀님을 잘 도와 드리거라. 이 할아비의 부탁이다.",
    ),
  },
  {
    lv: ROYAL_LEVEL,
    goal: { type: "collect", drop: "troll", count: 10 },
    reward: { emerald: 30, items: [["ticket", 1]] },
    ...T(
      "산성의 창살",
      "북쪽 국경의 산성이 트롤들에게 부서지고 있어요.||트롤 엄니는 쇠보다 단단해서 창살로 쓰기에 딱이래요. 엄니 열 개를 구해 주세요.",
      "트롤 엄니 열 개예요. 북쪽 산길의 트롤이 떨어뜨려요.",
      "이렇게 굵은 엄니라니… 이걸로 창살을 세우면 트롤도 못 넘어올 거예요.||고마워요. 왕실의 이름으로 감사드려요.",
    ),
  },
  {
    lv: 36,
    goal: { type: "talk", npc: "main_9" },
    reward: { emerald: 20, items: [["stone", 3]] },
    ...T(
      "왕실 문장",
      "대장장이 군나르 님께 이 왕실 문장을 보여 주세요.||당신에게 어울리는 갑옷을 맞추려면 치수부터 재야 하거든요. 후후, 아직은 비밀이에요.",
      "군나르 님께 왕실 문장을 보여 주세요.",
      "이건 왕실 문장 아닌가! 자네, 대체 무슨 일에 휘말린 겐가?||…황제 갑옷이라고? 전설 속 갑옷을 내 손으로 다시 맞추게 되다니!||자, 팔을 벌려 보게. 치수를 재야지. 왕녀님께 잘 전해 주게!",
    ),
  },
  {
    lv: 37,
    goal: { type: "boss", boss: "nidhogg" },
    reward: { emerald: 40, items: [["ticket", 1], ["bright", 1]] },
    ...T(
      "세계수의 성소",
      "세계수 뿌리 곁에는 왕실의 오래된 성소가 있어요.||니드호그가 그곳까지 갉아 들어오고 있대요. 흑룡을 쓰러뜨려 성소를 지켜 주세요!",
      "니드호그는 날아오르면 낙인을 찍어요. 낙인이 찍히면 동료와 떨어지세요.",
      "성소가 무사하대요! 기도하던 사제들이 울면서 기뻐했어요.||당신 같은 사람이 왕국에 있어서 다행이에요.",
    ),
  },
  {
    lv: 37,
    goal: { type: "boss", boss: "skoll_hati" },
    reward: { emerald: 40, items: [["ticket", 1], ["scroll", 1]] },
    ...T(
      "하늘을 쫓는 늑대",
      "해를 쫓는 스콜과 달을 쫓는 하티가 왕국의 하늘을 어지럽히고 있어요.||낮과 밤이 뒤섞이면 농사도 항해도 할 수 없어요. 늑대들을 쫓아 주세요!",
      "낮에는 스콜이, 밤에는 하티가 거세진대요. 시간을 읽으세요.",
      "하늘이 제자리를 찾았어요! 오늘 밤엔 달도 별도 제대로 떴네요.||이제 마지막 위협만 남았어요.",
    ),
  },
  {
    lv: 38,
    goal: { type: "kill", mob: "troll", count: 15 },
    reward: { emerald: 40, items: [["ore", 2]] },
    ...T(
      "국경 토벌",
      "산성 창살 덕분에 국경이 버티고 있지만, 트롤 무리가 계속 몰려와요.||트롤 열다섯을 쓰러뜨려 국경을 정리해 주세요. 마지막 싸움 전에 길을 열어 둬야 해요.",
      "트롤은 상처가 금방 아물어요. 쉬지 말고 몰아치세요. 열다섯이에요.",
      "국경이 조용해졌어요. 병사들이 당신 이름을 외치고 있어요!||이제… 서리 거인만 남았어요.",
    ),
  },
  {
    lv: 40,
    goal: { type: "boss", boss: "ymir" },
    reward: { emerald: 64, armor: "path", items: [["ticket_adv", 1]] },
    finale: true,
    ...T(
      "서리 거인의 왕관",
      "선왕 폐하께서 북쪽 원정에서 끝내 쓰러뜨리지 못한 거인, 이미르.||이미르를 쓰러뜨려 주세요. 왕실은 왕국을 지킨 영웅에게 황제 갑옷을 바치기로 했어요.||군나르 님이 당신 치수에 맞춰 손질해 두셨어요. 꼭 살아 돌아와요.",
      "이미르가 숨을 들이쉬면 불이 켜진 모닥불 곁으로 가세요. 꼭 살아 돌아와요!",
      "…해냈군요. 왕국의 하늘에서 서리가 걷혔어요.||왕실의 이름으로 당신에게 황제 갑옷을 바칩니다. 오늘부터 당신은 왕실의 수호자예요.||고마워요, 영웅님. 이 은혜는 잊지 않을게요.",
    ),
  },
];

/** v2.9.0 전설 퀘스트 목표 (kind: kill = mobs 중 어느 것이든 · boss = 그 보스) */
const SEA_MOBS = { kind: "kill", mobs: ["skeljaskrimsli", "nix"], count: 40, label: "바다 몬스터" };
const NORTH_MOBS = { kind: "kill", mobs: ["draugr", "troll"], count: 40, label: "북쪽 몬스터" };
const EVERY_BOSS = BOSS_ORDER.map((boss) => ({ kind: "boss", boss, count: 1 }));

/**
 * 전설 퀘스트 4개 (v2.9.0): path 의 스승이 줌. after = 메인 이야기에서 이 보스를 잡는 단계를 끝내면 열림, lv = 최소 레벨 (무기 레벨),
 * weapon = 보상 (전설 무기 — 큰 글자 · 모두에게 알림)
 * @type {{path: string, grade: string, weapon: string, after: string, lv: number, goals: any[], title: string, offer: string, progress: string, done: string}[]}
 */
export const LEGENDS = [
  {
    path: "shield",
    grade: "hero",
    weapon: "shield_l30_hero",
    after: "jormungandr",
    lv: 30,
    goals: [SEA_MOBS, { kind: "boss", boss: "jormungandr", count: 3 }],
    ...T(
      "듀란드의 방패",
      "요르문간드를 쓰러뜨렸다니 장하네. 하지만 스승님의 방패는 아직 그 뱀의 뱃속 깊은 곳에 있을 걸세.||뱀은 쓰러져도 다시 일어나지. 세 번 쓰러뜨려 그 방패를 토해 내게 하게.||그리고 바다 몬스터 마흔을 물리쳐 바닷길을 지키게. 그것이 듀란드 경의 방패를 들 자격일세.",
      "바다 몬스터 마흔, 요르문간드 세 번일세. 스승님의 방패를 부탁하네.",
      "이 방패는… 틀림없이 스승님의 방패일세!||듀란드의 방패, 자네가 가지게. 스승님도 그것을 바라실 걸세.",
    ),
  },
  {
    path: "bow",
    grade: "hero",
    weapon: "bow_l30_hero",
    after: "jormungandr",
    lv: 30,
    goals: [SEA_MOBS, { kind: "boss", boss: "jormungandr", count: 3 }],
    ...T(
      "잃어버린 엘프의 활",
      "요르문간드를 쓰러뜨렸다고요? 정말 대단해요!||하지만 엘프의 활은 아직 뱀의 뱃속 깊은 곳에 있을 거예요. 세 번은 쓰러뜨려야 토해 낼 거예요.||바다 몬스터 마흔도 물리쳐 줘요. 제 숲을 삼킨 바다를 깨끗하게 해 주고 싶어요.",
      "바다 몬스터 마흔, 요르문간드 세 번이에요. 부탁해요.",
      "이 활… 정말 엘프의 활이에요! 다시 볼 수 있을 줄은 몰랐어요.||당신이 가져요. 이 활은 당신 손에서 다시 노래할 거예요.",
    ),
  },
  {
    path: "sword",
    grade: "transcend",
    weapon: "sword_l40_transcend",
    after: "ymir",
    lv: 40,
    goals: [NORTH_MOBS, ...EVERY_BOSS],
    ...T(
      "그람의 부활",
      "이미르를 쓰러뜨렸다니… 네가 해냈구나.||하지만 그람을 되살리려면 이 세상 모든 괴수를 이겨 낸 칼끝이 필요하다.||보스 여덟을 종류마다 한 번씩, 그리고 북쪽 몬스터 마흔을 베어라. 그때 그람이 깨어날 게다.",
      "보스 여덟 종류를 하나씩, 북쪽 몬스터 마흔이다. 그람이 기다린다.",
      "…보아라, 이 붉은 불꽃을. 그람이 다시 숨을 쉰다.||삼십 년 전 내가 잃은 검이 네 손에서 다시 살아났다. 너야말로 이 검의 주인이다.",
    ),
  },
  {
    path: "shield",
    grade: "transcend",
    weapon: "shield_l40_transcend",
    after: "ymir",
    lv: 40,
    goals: [NORTH_MOBS, ...EVERY_BOSS],
    ...T(
      "아이기스의 방패",
      "이미르의 얼음이 녹은 자리에서 신들의 방패, 아이기스의 기운이 느껴진다네.||하지만 그 방패는 모든 괴수를 이겨 낸 자에게만 모습을 드러낸다고 하지.||보스 여덟을 종류마다 한 번씩, 그리고 북쪽 몬스터 마흔을 물리치게. 그때 아이기스가 자네를 고를 걸세.",
      "보스 여덟 종류를 하나씩, 북쪽 몬스터 마흔일세.",
      "얼음 속에서 이것이 나왔네… 아이기스의 방패!||신들의 방패가 자네를 주인으로 골랐군. 받게. 자네가 가질 자격이 있네.",
    ),
  },
];

/** 왕실 퀘스트 단계 수 */
export const ROYAL_STEPS = ROYAL.length;

/**
 * v2.12.0 초월 보스 전설 퀘스트 (왕녀가 줌): boss = 잡을 초월 보스 (config.js TRANSCEND_ORDER), lv = 최소 레벨, 왕실 퀘스트 마지막 단계를 끝내면 열림.
 * reward.items = [아이템 키, 개수] (mainquest.js rewardItemId), honor = 끝냈을 때 큰 글자 (무기 보상이 없으므로 칭호로 축하)
 * 다른 초월 보스를 더할 때 여기에 한 줄 (같은 틀)
 * @type {{boss: string, lv: number, reward: {emerald: number, items: any[]}, honor: string, greet: {offer: string, active: string, done: string}, title: string, offer: string, progress: string, done: string}[]}
 */
export const TRANSCEND_LEGENDS = [
  {
    boss: "surtr",
    lv: 40,
    reward: { emerald: 128, items: [["ticket_adv", 2], ["laevateinn_shard", 2], ["muspel_heart", 1]] },
    honor: "무스펠의 불을 끈 자",
    greet: {
      offer: "왕실의 수호자님, 남쪽 하늘이 밤마다 붉게 타고 있어요. 드릴 말씀이 있어요.",
      active: "수르트의 불꽃은 신전째로 녹여요. 꼭 살아 돌아와요, 영웅님.",
      done: "무스펠의 불을 끈 영웅님! 왕국 어디를 가도 당신 이야기뿐이에요.",
    },
    ...T(
      "무스펠의 불꽃 거인",
      "왕실의 수호자님, 남쪽 하늘이 밤마다 붉게 타고 있어요.||무스펠헤임의 불의 거인, 수르트가 깨어났대요.|옛 신전째로 세상을 태우려 한대요.||선왕 폐하의 기록에 따르면 그 거인은 쉽게 쓰러지지 않는대요.|칼이 땅에 박힌 틈을 노리고, 신전의 사슬과 얼음을 쓰래요.||이건 왕국만의 일이 아니에요. 부탁드려요, 영웅님.",
      "칼이 금빛으로 번쩍이면 스킬로 반격하고, 업화가 타오르면 모두 함께 쳐서 꺼뜨리세요!",
      "…남쪽 하늘이 다시 파랗게 돌아왔어요.||무스펠의 불꽃을 끈 영웅이라니, 왕국의 노래가 하나 더 생기겠어요.||거인이 남긴 불씨는 대장장이들이 탐낼 거예요.|언젠가 쓸 곳이 생길 거예요. 잘 간직해 두세요.",
    ),
  },
];

function itemsOf(list) {
  return (list ?? []).map(([k, n]) => ({ id: rewardItemId(k), amount: n }));
}

/**
 * 들어 있는 퀘스트 전체 (반복 의뢰 6 + 왕실 7 + v2.9.0 전설 4 + v2.12.0 초월 보스 전설 1, 정의 그대로 — quest.js 가 다듬어 씀)
 * 추가 필드: side ("repeat" · "royal"), ord (순서), afterStep (반복 의뢰: 이 메인 단계를 끝내면 열림), xpMob (경험치 제한 몬스터),
 *   reward.xpPoints (경험치 포인트), reward.armor ("path" = 무기의 길에 맞는 초월 갑옷), finale (왕실 마지막), honor (v2.12.0 초월 보스 전설: 끝낼 때 큰 글자)
 */
export function buildSideQuests() {
  const out = [];
  MONSTER_ORDER.forEach((mob, i) => {
    const R = REPEAT_QUESTS[mob];
    const d = MONSTER_DROPS[mob];
    const L = REPEAT_LINES[mob];
    if (!R || !d || !L) return;
    const fill = (t) => t.replaceAll("{n}", String(R.count));
    out.push({
      id: repeatId(mob),
      side: "repeat",
      ord: 100 + i,
      giver: REPEAT_GIVER,
      type: "collect",
      target: d.item,
      targetName: d.name,
      count: R.count,
      title: L.title,
      offer: fill(L.offer),
      progress: fill(L.progress),
      done: fill(L.done),
      reward: { emerald: R.emerald, xp: 0, points: 0, weapon: "", item: null, items: [], xpPoints: R.xp },
      repeat: true,
      requires: "",
      afterStep: mainKillStep(mob),
      xpMob: mob,
      minLevel: 0,
    });
  });
  let prev = MAIN_CHOOSE;
  ROYAL.forEach((r, i) => {
    const step = i + 1;
    /** @type {any} */
    const q = {
      id: royalId(step),
      side: "royal",
      ord: 200 + step,
      step,
      giver: ROYAL_GIVER,
      title: r.title,
      offer: r.offer,
      progress: r.progress,
      done: r.done,
      count: 1,
      target: "",
      targetName: "",
      reward: { emerald: r.reward.emerald ?? 0, xp: 0, points: 0, weapon: "", item: null, items: itemsOf(r.reward.items), armor: r.reward.armor ?? "" },
      repeat: false,
      requires: prev,
      minLevel: r.lv,
      finale: !!r.finale,
    };
    const g = r.goal;
    if (g.type === "talk") Object.assign(q, { type: "talk", target: g.npc, targetName: mainNpcCall(g.npc) });
    else if (g.type === "collect") Object.assign(q, { type: "collect", target: MONSTER_DROPS[g.drop].item, targetName: MONSTER_DROPS[g.drop].name, count: g.count });
    else if (g.type === "kill") Object.assign(q, { type: "kill", target: `rpg:${g.mob}`, targetName: `${MONSTERS[g.mob].name}(Lv.${MONSTERS[g.mob].level})`, count: g.count });
    else Object.assign(q, { type: "boss", target: g.boss, targetName: BOSSES[g.boss]?.name ?? g.boss });
    out.push(q);
    prev = q.id;
  });
  // v2.9.0 전설 퀘스트 (그 무기의 길의 스승, 메인의 그 보스 단계 뒤)
  LEGENDS.forEach((L, i) => {
    out.push({
      id: legendId(L.path, L.grade),
      side: "legend",
      ord: 300 + i,
      path: L.path,
      giver: MAIN_PATHS[L.path].mentor,
      type: "trial",
      target: "",
      targetName: L.title,
      goals: L.goals.map((g) => ({ ...g, mobs: g.mobs ? [...g.mobs] : undefined })),
      count: L.goals.reduce((n, g) => n + g.count, 0),
      title: L.title,
      offer: L.offer,
      progress: L.progress,
      done: L.done,
      reward: { emerald: 0, xp: 0, points: 0, weapon: L.weapon, item: null, items: [] },
      legend: true,
      repeat: false,
      requires: "",
      afterStep: mainBossStep(L.after),
      minLevel: L.lv,
    });
  });
  // v2.12.0 초월 보스 전설 퀘스트 (왕녀, 왕실 퀘스트 마지막 단계 뒤)
  TRANSCEND_LEGENDS.forEach((L, i) => {
    out.push({
      id: transcendLegendId(L.boss),
      side: "legend",
      ord: 320 + i,
      giver: ROYAL_GIVER,
      type: "boss",
      target: L.boss,
      targetName: BOSSES[L.boss]?.name ?? L.boss,
      count: 1,
      title: L.title,
      offer: L.offer,
      progress: L.progress,
      done: L.done,
      reward: { emerald: L.reward.emerald, xp: 0, points: 0, weapon: "", item: null, items: itemsOf(L.reward.items) },
      legend: true,
      honor: L.honor,
      repeat: false,
      requires: royalId(ROYAL_STEPS),
      minLevel: L.lv,
    });
  });
  return out;
}
