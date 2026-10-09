import { EquipmentSlot, GameMode, ItemStack, system, world } from "@minecraft/server";
import { withFormLock } from "./chestui.js";
import { ARMOR_PIECE_ORDER, ARMOR_TIERS, ARMOR_TYPES, BOSS_MATERIALS, BOSSES, CONFIG, MONSTER_ORDER, MONSTERS, RAID_MATERIALS, RUNE, RUNE_SMITH, STAT_INFO, TYPES } from "./config.js";
import { DialogForm, ICON, clipText, showPages, toPages } from "./forms.js";
import { notify, particle, play } from "./fx.js";
import { showRealTitle } from "./hp.js";
import { MAIN_CHOOSE, MAIN_PATHS, MAIN_PREFIX, MAIN_START, PATH_ORDER, PATH_STEPS, REGIONS, buildMainQuests, firstPathQuest, mainId, mainNpcByKey, mainNpcCall, migrateMainState, oldKillDone, rewardItemName } from "./mainquest.js";
import { PATH_ARMOR, REPEAT_GIVER, ROYAL_GIVER, SIDE_PREFIX, TRANSCEND_LEGENDS, buildSideQuests, transcendLegendId } from "./sidequest.js";
import { dropItem, dropKeyOf, huntKind, killCredit, xpCapped } from "./loot.js";
import { mobKey, shortId } from "./names.js";
import { greeting, isRunesmith, isSmith, npcKey, npcName, npcNameByKey, shopLevel } from "./npcdata.js";
import { armorFrom, armorLack, createArmorItem, storeLoop } from "./armor.js";
import { materialItem } from "./enhance.js";
import { createTicketItem } from "./runes.js";
import { createShardItem, runesmithLoop } from "./runesmith.js";
import { add, burst, flat, fx, shock, sparks, up } from "./skills.js";
import { bossMaterialItem, smithLoop } from "./smith.js";
import { countItem, giveItem, removeItem } from "./shop.js";
import { addPoints, reqLevelOf } from "./stats.js";
import { createWeaponItem, currentWeapon, needsLevel, parseWeapon } from "./weapons.js";

/**
 * 퀘스트
 * - 퀘스트 정의: 세계 동적 속성 "rpg:quest:<번호>" (게임 안 NPC 설정 창에서 만들고 고친다)
 *   + 메인 퀘스트 (v2.6.0, mainquest.js): 애드온에 들어 있음 (ID "mq_…", 편집 창에서 고칠 수 없음)
 *   + 반복 의뢰 · 왕실 퀘스트 (v2.7.0, sidequest.js): 애드온에 들어 있음 (ID "sq_…", side "repeat" · "royal", 편집 창에서 고칠 수 없음)
 *     반복 의뢰는 메인 퀘스트의 그 몬스터 처치 단계 (afterStep) 를 끝내면 열리고, 경험치 (포인트) 는 그 몬스터의 xpUntil 이상이면 0.
 *     왕실 퀘스트 마지막 보상은 무기의 길에 맞는 초월 갑옷 (reward.armor "path")
 *   + v2.9.0 전설 퀘스트 (sidequest.js, side "legend"): 그 무기의 길에서만, 메인의 그 보스 단계 뒤. 목표 종류 trial = 처치 · 보스 목표 여럿 (goals, 진행 e.o)
 *   v2.9.0 메인 이야기가 지역 퀘스트 (31단계) 로 바뀜: 예전 18단계 진행은 처음 읽을 때 mainquest.js migrateMainState 가 옮김
 * - 플레이어 진행: 플레이어 동적 속성 "rpg:qs", 메인 퀘스트의 무기의 길: "rpg:main_path"
 * - 목표 종류: kill(몹 처치) / collect(아이템 가져오기) / talk(다른 NPC에게 말 걸기) / level(레벨 달성)
 *   메인 퀘스트만: try(무기 여러 종류로 한 번씩 때리기) / choose(무기의 길 고르기) / boss(보스 처치 — 잡을 때 보상 받은 사람 모두)
 *   v2.9.0 전설 퀘스트만: trial(처치 · 보스 목표 여럿을 한 퀘스트에서 — goals)
 * - 완료 보고: 퀘스트를 준 NPC에게 (talk 는 대상 NPC에게)
 * - 길 안내 (v2.6.1, nav.js): navGoal = 지금 갈 곳. 받은 퀘스트를 안내하고 (플레이어 동적 속성 "rpg:nav" {off, q}),
 *   끝내거나 포기하면 메인 이야기 → 진행 중인 다른 퀘스트. 퀘스트 목록에서 고르거나 끌 수 있음 (/nav)
 */

export const QUEST_TYPES = {
  kill: { name: "몹 처치", icon: "textures/items/iron_sword" },
  collect: { name: "아이템 가져오기", icon: "textures/items/bundle" },
  talk: { name: "NPC에게 말 걸기", icon: "textures/items/paper" },
  level: { name: "레벨 달성", icon: "textures/items/experience_bottle" },
  try: { name: "무기 시험", icon: "textures/items/iron_axe" },
  choose: { name: "무기 고르기", icon: "textures/items/book_enchanted" },
  boss: { name: "보스 처치", icon: "textures/items/nether_star" },
  trial: { name: "전설의 시련", icon: "textures/items/totem" },
};
/** 편집 창에서 고를 수 있는 목표 종류 (나머지는 메인 퀘스트 전용) */
export const QUEST_TYPE_KEYS = ["kill", "collect", "talk", "level"];

const Q_PREFIX = "rpg:quest:";
const Q_SEQ = "rpg:quest_seq";
const P_STATE = "rpg:qs";
const P_PATH = "rpg:main_path";
const P_NAV = "rpg:nav";
/** 시험용 무기 표시 (아이템 동적 속성) */
const TRIAL = "rpg:trial";
const LOG_PAGE = 3; // 퀘스트 목록 한 장에 보이는 수 (휴대폰 화면 높이 기준)
const TALK_PAGE = 3; // NPC 대화창 한 장에 보이는 퀘스트 수
const TITLE_WIDTH = 160; // 선택지에 들어가는 퀘스트 이름 폭 (뒤에 상태 표시가 붙음)
const MAIN_TAG = "§6[메인] ";
const MAIN_TAG_WIDTH = 34; // "[메인] " 폭 (이름 폭은 그대로 두고 앞에 붙임). v2.7.0 "[반복] " · "[왕실] " 도 같은 폭
/** v2.7.0 반복 의뢰 · 왕실 퀘스트 · v2.9.0 전설 퀘스트 머리표 (선택지·목록·알림) */
const SIDE_TAG = { repeat: "§b[반복] ", royal: "§d[왕실] ", legend: "§c[전설] " };

/** 퀘스트 머리표 ("" = 만든 퀘스트) */
function tagOf(q) {
  return q.main ? MAIN_TAG : q.side ? (SIDE_TAG[q.side] ?? "") : "";
}

/** 알림 앞머리 (색 다음 굵게 — 굵게 다음에 색을 쓰면 굵게가 풀림): "§6§l[메인] " · "§b§l[반복] " · "§d§l[왕실] " · 만든 퀘스트 "§e§l" */
function noticeTag(q) {
  const t = tagOf(q);
  return t ? `${t.slice(0, 2)}§l${t.slice(2)}` : "§e§l";
}

/**
 * @typedef {{emerald:number, xp:number, points:number, weapon:string, item:{id:string, amount:number, name:string, nameTag?:string} | null, items?: {id:string, amount:number}[],
 *   xpPoints?: number, armor?: string, armorType?: string, capped?: string}} Reward
 *   xpPoints: 경험치 포인트 (v2.7.0 반복 의뢰), armor: "path" = 무기의 길에 맞는 초월 갑옷 (왕실), armorType: 실제로 줄 갑옷 종류, capped: 경험치 제한에 걸린 몬스터
 * @typedef {{id:string, title:string, giver:string, type:string, target:string, targetName:string, count:number,
 *   offer:string, progress:string, done:string, reward:Reward, repeat:boolean, requires:string, minLevel:number,
 *   main?: boolean, side?: string, path?: string, ord?: number, step?: number, give?: string[], doneBy?: Record<string, string>, legend?: boolean, finale?: boolean,
 *   afterStep?: number, xpMob?: string, goals?: Goal[], honor?: string}} Quest  honor: v2.12.0 초월 보스 전설 퀘스트를 끝낼 때 큰 글자 (무기 보상 대신)
 * @typedef {{kind: string, mobs?: string[], boss?: string, count: number, label?: string}} Goal  v2.9.0 trial 목표: kill (mobs 중 어느 것이든) · boss
 * @typedef {{s:string, n:number, c:number, r?:number, t?:string[], o?:number[]}} Entry  s: "a" 진행 중, "d" 완료 / n: 처치 수 / c: 완료 횟수 / r: 달성 알림 / t: 써 본 무기 종류 (try) / o: 목표마다 진행 (trial)
 */

function int(v, max) {
  return Math.max(0, Math.min(max, Math.floor(Number(v) || 0)));
}

/**
 * @param {any} q
 * @param {boolean} [main] 메인 퀘스트 정의 (메인 전용 목표 종류 try·choose·boss 를 받음)
 * @returns {Quest}
 */
export function normalizeQuest(q, main = false) {
  const r = q?.reward ?? {};
  /** @type {Reward["item"]} */
  let item = null;
  if (r.item && r.item.id) {
    item = { id: String(r.item.id), amount: Math.max(1, int(r.item.amount, 6400)), name: String(r.item.name ?? "") };
    if (r.item.nameTag) item.nameTag = String(r.item.nameTag);
  }
  const type = (main ? q?.type in QUEST_TYPES : QUEST_TYPE_KEYS.includes(q?.type)) ? q.type : "kill";
  return {
    id: String(q?.id ?? ""),
    title: String(q?.title ?? "").trim() || "퀘스트",
    giver: String(q?.giver ?? ""),
    type,
    target: String(q?.target ?? ""),
    targetName: String(q?.targetName ?? ""),
    count: Math.max(1, int(q?.count, 10000)),
    offer: String(q?.offer ?? ""),
    progress: String(q?.progress ?? ""),
    done: String(q?.done ?? ""),
    reward: {
      emerald: int(r.emerald, 6400),
      xp: int(r.xp, 1000),
      points: int(r.points, 1000),
      weapon: String(r.weapon ?? ""),
      item,
    },
    // 레벨·말 걸기 퀘스트는 반복하면 보상을 무한히 받을 수 있어서 반복 불가
    repeat: !!q?.repeat && (type === "kill" || type === "collect"),
    requires: String(q?.requires ?? ""),
    minLevel: int(q?.minLevel, 10000),
  };
}

/**
 * 애드온에 들어 있는 퀘스트 (mainquest.js 메인 · v2.7.0 sidequest.js 반복 의뢰 · 왕실) → 퀘스트 + 전용 필드
 * @returns {Quest}
 */
function normalizeBuiltin(q) {
  const n = normalizeQuest(q, true);
  const items = (Array.isArray(q?.reward?.items) ? q.reward.items : []).filter((it) => it && it.id).map((it) => ({ id: String(it.id), amount: Math.max(1, int(it.amount, 6400)) }));
  const side = q?.side === "repeat" || q?.side === "royal" || q?.side === "legend" ? q.side : "";
  return {
    ...n,
    repeat: side === "repeat" && (n.type === "kill" || n.type === "collect"),
    reward: { ...n.reward, items, xpPoints: int(q?.reward?.xpPoints, 100000), armor: q?.reward?.armor === "path" ? "path" : "" },
    main: !side,
    side,
    path: typeof q.path === "string" && q.path in MAIN_PATHS ? q.path : "",
    ord: Number(q.ord) || 0,
    step: Number(q.step) || 0,
    give: Array.isArray(q.give) ? q.give.filter((t) => PATH_ORDER.includes(t)) : [], // v2.16.0 길이 있는 여섯 종류만 (마검 티르빙은 아님)
    doneBy: q.doneBy && typeof q.doneBy === "object" ? { ...q.doneBy } : undefined,
    legend: !!q.legend,
    finale: !!q.finale,
    afterStep: Number(q.afterStep) || 0,
    xpMob: typeof q.xpMob === "string" && MONSTERS[q.xpMob] ? q.xpMob : "",
    goals: n.type === "trial" ? normalizeGoals(q.goals) : undefined,
    honor: typeof q.honor === "string" ? q.honor : "",
  };
}

/** v2.9.0 trial 목표 다듬기 (모르는 몬스터 · 보스 · 종류는 뺌) @returns {Goal[]} */
function normalizeGoals(list) {
  /** @type {Goal[]} */
  const out = [];
  for (const g of Array.isArray(list) ? list : []) {
    const count = Math.max(1, int(g?.count, 10000));
    if (g?.kind === "boss" && BOSSES[g.boss]) out.push({ kind: "boss", boss: String(g.boss), count });
    else if (g?.kind === "kill") {
      const mobs = (Array.isArray(g.mobs) ? g.mobs : []).filter((m) => MONSTERS[m]);
      if (mobs.length) out.push({ kind: "kill", mobs, count, label: String(g.label ?? "") || mobs.map((m) => MONSTERS[m].name).join(" · ") });
    }
  }
  return out;
}

export function isMainId(id) {
  return String(id ?? "").startsWith(MAIN_PREFIX);
}

/** 애드온에 들어 있는 퀘스트 ID (메인 mq_ · v2.7.0 반복 의뢰 · 왕실 sq_) — 저장·삭제로 바꿀 수 없음 */
export function isBuiltinId(id) {
  const s = String(id ?? "");
  return s.startsWith(MAIN_PREFIX) || s.startsWith(SIDE_PREFIX);
}

/** 애드온에 들어 있는 퀘스트인지 (편집 창에서 고칠 수 없음, 이름표 [!] 에 세지 않음) */
export function isBuiltin(q) {
  return !!q && (!!q.main || !!q.side);
}

// ------------------------------------------------------------ 퀘스트 정의 저장소
/** @type {Map<string, Quest> | undefined} */
let quests;
/** @type {Quest[] | undefined} 메인 퀘스트 (한 번만 만듦) */
let mainList;
/** @type {Quest[] | undefined} 반복 의뢰 · 왕실 퀘스트 (v2.7.0, 한 번만 만듦) */
let sideList;
/** @type {Map<string, Quest[]> | undefined} */
let killIndex;
/** @type {Map<string, Quest[]> | undefined} 주는 NPC → 퀘스트 (순서대로) */
let giverIndex;
/** @type {Map<string, Quest[]> | undefined} 말 걸기 대상 NPC → 퀘스트 */
let talkIndex;
/** @type {Quest[] | undefined} */
let sorted;

function mainQuests() {
  if (!mainList) mainList = buildMainQuests().map(normalizeBuiltin);
  return mainList;
}

/** 반복 의뢰 · 왕실 퀘스트 (v2.7.0) */
export function sideQuests() {
  if (!sideList) sideList = buildSideQuests().map(normalizeBuiltin);
  return sideList;
}

function loadQuests() {
  if (quests) return quests;
  const map = new Map();
  for (const q of mainQuests()) map.set(q.id, q);
  for (const q of sideQuests()) map.set(q.id, q);
  for (const id of world.getDynamicPropertyIds()) {
    if (!id.startsWith(Q_PREFIX)) continue;
    try {
      const raw = world.getDynamicProperty(id);
      if (typeof raw !== "string") continue;
      const q = normalizeQuest(JSON.parse(raw));
      if (q.id && !isBuiltinId(q.id)) map.set(q.id, q);
    } catch (e) {
      console.warn(`[rpg] quest ${id}: ${e}`);
    }
  }
  quests = map;
  return map;
}

function invalidate() {
  killIndex = undefined;
  giverIndex = undefined;
  talkIndex = undefined;
  sorted = undefined;
}

/** 메인 퀘스트가 먼저 (이야기 순서), 그다음 반복 의뢰 · 왕실 퀘스트 (v2.7.0), 그다음 만든 퀘스트 (번호 순서) */
function questOrder(a, b) {
  const rank = (q) => (q.main ? 0 : q.side ? 1 : 2);
  const am = rank(a);
  const bm = rank(b);
  if (am !== bm) return am - bm;
  if (am < 2) return (a.ord ?? 0) - (b.ord ?? 0) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
  return Number(a.id.slice(1)) - Number(b.id.slice(1));
}

export function allQuests() {
  if (!sorted) sorted = [...loadQuests().values()].sort(questOrder);
  return [...sorted];
}

export function getQuest(id) {
  return loadQuests().get(id);
}

function indexes() {
  if (!giverIndex || !talkIndex) {
    const g = new Map();
    const t = new Map();
    for (const q of allQuests()) {
      if (!g.has(q.giver)) g.set(q.giver, []);
      g.get(q.giver).push(q);
      if (q.type === "talk" && q.target) {
        if (!t.has(q.target)) t.set(q.target, []);
        t.get(q.target).push(q);
      }
    }
    giverIndex = g;
    talkIndex = t;
  }
  return { giverIndex, talkIndex };
}

/** 이 NPC가 주는 퀘스트 */
export function questsOf(giverKey) {
  if (!giverKey) return [];
  return [...(indexes().giverIndex.get(giverKey) ?? [])];
}

/** 이 NPC에게 말 걸기 퀘스트 */
function talkTargetsOf(key) {
  if (!key) return [];
  return indexes().talkIndex.get(key) ?? [];
}

/** 저장하고 저장된 퀘스트를 돌려준다. 번호가 없으면 새로 붙인다. 애드온에 들어 있는 퀘스트 (메인 · 반복 의뢰 · 왕실) 는 바꾸지 않는다 */
export function saveQuest(q) {
  const map = loadQuests();
  if (isBuiltinId(q?.id)) return map.get(q.id);
  const nq = normalizeQuest(q);
  if (!nq.id) {
    const n = Number(world.getDynamicProperty(Q_SEQ) ?? 0) + 1;
    world.setDynamicProperty(Q_SEQ, n);
    nq.id = `q${n}`;
  }
  world.setDynamicProperty(Q_PREFIX + nq.id, JSON.stringify(nq));
  map.set(nq.id, nq);
  invalidate();
  return nq;
}

export function deleteQuest(id) {
  if (isBuiltinId(id)) return; // 메인 · 반복 의뢰 · 왕실 퀘스트는 애드온에 들어 있어 지우지 않음
  world.setDynamicProperty(Q_PREFIX + id, undefined);
  loadQuests().delete(id);
  invalidate();
}

function kills() {
  if (!killIndex) {
    const idx = new Map();
    for (const q of loadQuests().values()) {
      if (q.type !== "kill") continue;
      const k = mobKey(q.target);
      if (!idx.has(k)) idx.set(k, []);
      idx.get(k).push(q);
    }
    killIndex = idx;
  }
  return killIndex;
}

// ------------------------------------------------------------ 플레이어 진행
/** @type {Map<string, Record<string, Entry>>} */
const states = new Map();
/** 무기의 길 (메인 퀘스트) @type {Map<string, string>} */
const paths = new Map();
/** 메인 퀘스트 "새 이야기" 알림을 한 퀘스트 (같은 퀘스트는 한 번) @type {Map<string, string>} */
const mainNoted = new Map();
/** v2.7.0 왕실 퀘스트 "새 부탁" 알림을 한 퀘스트 @type {Map<string, string>} */
const royalNoted = new Map();
/** v2.9.0 전설 퀘스트 "열림" 알림을 한 퀘스트 @type {Map<string, Set<string>>} */
const legendNoted = new Map();
/** 길 안내 설정 (v2.6.1) @type {Map<string, {off: boolean, q: string}>} */
const navs = new Map();

/** @returns {Record<string, Entry>} */
function stateOf(player) {
  const cached = states.get(player.id);
  if (cached) return cached;
  /** @type {Record<string, Entry>} */
  let st = {};
  try {
    const raw = player.getDynamicProperty(P_STATE);
    const parsed = typeof raw === "string" ? JSON.parse(raw) : undefined;
    if (parsed && typeof parsed === "object") st = parsed;
  } catch {
    st = {};
  }
  states.set(player.id, st);
  // v2.9.0: 예전 (18단계) 메인 진행이 있으면 지역 퀘스트 단계로 옮기고 저장 (처음 읽을 때 한 번)
  try {
    if (migrateMainState(st, pathOf(player))) saveState(player);
  } catch (e) {
    console.warn(`[rpg] main quest migration: ${e}`);
  }
  return st;
}

function saveState(player) {
  try {
    player.setDynamicProperty(P_STATE, JSON.stringify(stateOf(player)));
  } catch (e) {
    console.warn(`[rpg] quest state: ${e}`);
  }
}

/** 메인 퀘스트에서 고른 무기의 길 (무기 종류, 아직 안 골랐으면 "") */
export function pathOf(player) {
  const cached = paths.get(player.id);
  if (cached !== undefined) return cached;
  let v = "";
  try {
    const raw = player.getDynamicProperty(P_PATH);
    if (typeof raw === "string" && raw in MAIN_PATHS) v = raw;
  } catch {
    v = "";
  }
  paths.set(player.id, v);
  return v;
}

function setPath(player, type) {
  paths.set(player.id, type);
  try {
    player.setDynamicProperty(P_PATH, type ? type : undefined);
  } catch (e) {
    console.warn(`[rpg] main path: ${e}`);
  }
}

export function forgetQuestPlayer(playerId) {
  states.delete(playerId);
  paths.delete(playerId);
  mainNoted.delete(playerId);
  royalNoted.delete(playerId);
  legendNoted.delete(playerId);
  navs.delete(playerId);
}

/** 퀘스트 기록 초기화 (테스트용). 메인 퀘스트의 무기의 길·길 안내로 고른 퀘스트도 지움 (켜고 끈 것은 그대로) */
export function resetQuests(player) {
  states.set(player.id, {});
  saveState(player);
  setPath(player, "");
  mainNoted.delete(player.id);
  royalNoted.delete(player.id);
  legendNoted.delete(player.id);
  pinQuest(player, "");
}

// ------------------------------------------------------------ 길 안내 설정 (v2.6.1)
/** 길 안내 설정: off = 껐음 (/nav), q = 안내할 퀘스트 (받은 퀘스트 · 퀘스트 목록에서 고른 퀘스트, 없으면 "") */
export function navOf(player) {
  const cached = navs.get(player.id);
  if (cached) return cached;
  let v = { off: false, q: "" };
  try {
    const raw = player.getDynamicProperty(P_NAV);
    const o = typeof raw === "string" ? JSON.parse(raw) : undefined;
    if (o && typeof o === "object") v = { off: !!o.off, q: typeof o.q === "string" ? o.q : "" };
  } catch {
    v = { off: false, q: "" };
  }
  navs.set(player.id, v);
  return v;
}

/** 갈 곳이 바뀔 일이 생기면 (nav.js 가 등록: 다음 틱에 바로 다시 정함 — 0.5초 기다리지 않게) */
let navChanged = (_player) => {};
export function onNavChange(fn) {
  navChanged = fn;
}

function navNow(player) {
  try {
    navChanged(player);
  } catch {
    // 무시
  }
}

function saveNav(player, v) {
  navs.set(player.id, v);
  try {
    player.setDynamicProperty(P_NAV, v.off || v.q ? JSON.stringify({ off: v.off ? 1 : 0, q: v.q }) : undefined);
  } catch (e) {
    console.warn(`[rpg] nav: ${e}`);
  }
  navNow(player);
}

/** 길 안내 켜기·끄기 */
export function setNavOff(player, off) {
  saveNav(player, { ...navOf(player), off: !!off });
}

/** 이 퀘스트를 안내 ("" = 고른 퀘스트 없음: 메인 이야기 → 진행 중인 다른 퀘스트) */
export function pinQuest(player, id) {
  saveNav(player, { ...navOf(player), q: String(id ?? "") });
}

/** 고른 퀘스트를 끝내거나 포기했으면 고른 것을 풂 */
function unpin(player, id) {
  if (navOf(player).q === id) pinQuest(player, "");
}

/** v2.9.0 trial: 목표마다 진행 (받지 않았으면 모두 0) */
export function trialCounts(player, q) {
  const o = stateOf(player)[q.id]?.o;
  return (q.goals ?? []).map((_, i) => Math.max(0, Number(Array.isArray(o) ? o[i] : 0) || 0));
}

/** 현재 진행도 (처치 수 / 가진 아이템 수 / 레벨 / 써 본 무기 종류 수 / v2.9.0 trial: 목표마다 채운 수의 합) */
export function progressOf(player, q) {
  const e = stateOf(player)[q.id];
  if (q.type === "kill" || q.type === "boss") return e?.n ?? 0;
  if (q.type === "trial") return trialCounts(player, q).reduce((n, c, i) => n + Math.min(c, q.goals?.[i]?.count ?? 0), 0);
  if (q.type === "try") return Array.isArray(e?.t) ? e.t.length : 0;
  if (q.type === "collect") return countItem(player, q.target);
  if (q.type === "level") return questLevel(player);
  return 0;
}

function isReady(player, q) {
  if (q.type === "talk" || q.type === "choose") return false;
  return progressOf(player, q) >= q.count;
}

/**
 * 퀘스트 조건의 레벨 = 경험치 레벨의 최고 기록 (v2.6.0, 최대 체력·갑옷과 같음): 죽어서 경험치를 잃어도 받은 이야기가 잠기지 않게
 * @param {import("@minecraft/server").Player} player
 */
export function questLevel(player) {
  return reqLevelOf(player); // v2.6.2: 무기 렙제 · 갑옷과 같은 함수
}

/** 먼저 끝낼 퀘스트를 끝냈는지 (무기 고르기는 길이 정해져 있으면 끝낸 것). v2.7.0 반복 의뢰: 내 길의 메인 afterStep 단계를 끝냈는지 */
function requiresMet(player, q) {
  if (q.afterStep) {
    const path = pathOf(player);
    const st = stateOf(player);
    // v2.9.0: 예전 (18단계) 에서 그 몬스터 처치 단계를 끝냈으면 반복 의뢰는 그대로 열림 (옮긴 진행이 앞 단계여도)
    if (!path || !(st[mainId(path, q.afterStep)]?.c > 0 || (q.side === "repeat" && oldKillDone(st, path, q.xpMob)))) return false;
  }
  if (!q.requires || !getQuest(q.requires)) return true;
  const r = stateOf(player)[q.requires];
  if (r && r.c > 0) return true;
  return q.requires === MAIN_CHOOSE && !!pathOf(player);
}

/**
 * 받을 수 없는 까닭: "path"(다른 무기의 길·이미 고름) / "requires"(앞 퀘스트) / "level" / "" (받을 수 있음)
 * 레벨은 마지막에 봐서 "level" 이면 레벨만 모자란 것
 */
export function lockReason(player, q) {
  if ((q.main || q.side === "legend") && q.path && pathOf(player) !== q.path) return "path";
  if (q.type === "choose" && pathOf(player)) return "path";
  if (!requiresMet(player, q)) return "requires";
  if (q.minLevel > 0 && questLevel(player) < q.minLevel) return "level";
  return "";
}

/** @returns {"locked" | "available" | "active" | "ready" | "done"} */
export function statusOf(player, q) {
  const e = stateOf(player)[q.id];
  if (q.type === "choose" && (pathOf(player) || (e && e.c > 0))) return "done";
  if (e && e.s === "a") return isReady(player, q) ? "ready" : "active";
  if (e && e.c > 0 && !q.repeat) return "done";
  return lockReason(player, q) ? "locked" : "available";
}

/** 메인 퀘스트 목록 (그 길 또는 프롤로그, 이야기 순서) */
function mainChain(path) {
  return mainQuests().filter((q) => q.path === path);
}

/** 이 플레이어의 다음 메인 퀘스트 (진행 중이거나 아직 끝내지 않은 첫 퀘스트, 모두 끝냈으면 undefined) */
export function nextMain(player) {
  for (const q of mainChain(pathOf(player))) if (statusOf(player, q) !== "done") return q;
  return undefined;
}

/** 메인 이야기를 끝까지 마쳤는지 */
export function mainFinished(player) {
  const path = pathOf(player);
  const last = path ? getQuest(mainId(path, PATH_STEPS)) : undefined;
  return !!last && statusOf(player, last) === "done";
}

/** 왕실 퀘스트 (v2.7.0): 이 플레이어의 다음 단계 (진행 중이거나 아직 끝내지 않은 첫 단계, 모두 끝냈으면 undefined) */
export function nextRoyal(player) {
  for (const q of sideQuests()) if (q.side === "royal" && statusOf(player, q) !== "done") return q;
  return undefined;
}

/** 왕실 퀘스트를 끝까지 마쳤는지 */
export function royalFinished(player) {
  return sideQuests().some((q) => q.side === "royal") && !nextRoyal(player);
}

// ------------------------------------------------------------ 문구
export function giverName(q) {
  return npcNameByKey(q.giver) || mainNpcCall(q.giver) || "(NPC 없음)";
}

function targetLabel(q) {
  if (q.targetName) return q.targetName;
  if (q.type === "talk") return npcNameByKey(q.target) || mainNpcCall(q.target) || "NPC";
  if (q.type === "boss") return BOSSES[q.target]?.name ?? q.target;
  return shortId(q.target) || "대상";
}

/**
 * v2.9.0 trial 목표 글자: "바다 몬스터 12/40마리 · 요르문간드 1/3번", 보스가 모두 한 번씩이면 "보스 8종 각 1번 3/8"
 * @param {Quest} q @param {number[]} [counts] 목표마다 진행 (넣으면 진행을 붙임)
 */
export function trialText(q, counts) {
  const goals = q.goals ?? [];
  const at = (i, g) => (counts ? `${Math.min(counts[i] ?? 0, g.count)}/` : "");
  const parts = [];
  goals.forEach((g, i) => {
    if (g.kind === "kill") parts.push(`${g.label} ${at(i, g)}${g.count}마리`);
  });
  const bosses = goals.map((g, i) => ({ g, i })).filter((x) => x.g.kind === "boss");
  if (bosses.length > 1 && bosses.every((x) => x.g.count === 1)) {
    const done = counts ? bosses.filter((x) => (counts[x.i] ?? 0) >= 1).length : 0;
    parts.push(`보스 ${bosses.length}종 각 1번${counts ? ` ${done}/${bosses.length}` : ""}`);
  } else for (const { g, i } of bosses) parts.push(`${BOSSES[g.boss ?? ""]?.name ?? g.boss} ${at(i, g)}${g.count}번`);
  return parts.join(" · ");
}

/** v2.9.0 trial: 아직 잡지 않은 보스 이름 (보스가 모두 한 번씩인 목표) */
function trialBossesLeft(q, counts) {
  return (q.goals ?? []).filter((g, i) => g.kind === "boss" && (counts[i] ?? 0) < g.count).map((g) => BOSSES[g.boss ?? ""]?.name ?? g.boss);
}

/** 진행을 붙인 목표 글자 (말 걸기는 진행 없음) */
export function objectiveOf(player, q) {
  if (q.type === "trial") return trialText(q, trialCounts(player, q));
  return objectiveText(q, q.type === "talk" ? undefined : progressOf(player, q));
}

/** @param {Quest} q @param {number} [n] 진행도 (넣으면 괄호로 표시) */
export function objectiveText(q, n) {
  const t = targetLabel(q);
  const prog = n === undefined ? "" : ` (${Math.min(n, q.count)}/${q.count})`;
  switch (q.type) {
    case "kill":
      return `${t} ${q.count}마리 처치${prog}`;
    case "collect":
      return `${t} ${q.count}개 가져오기${prog}`;
    case "talk":
      return `${t}에게 말 걸기`;
    case "try":
      return `무기 ${q.count}종으로 한 번씩 때리기${prog}`;
    case "choose":
      return "무기 하나 고르기";
    case "boss": {
      const lv = BOSSES[q.target]?.level;
      return `보스 ${t}${lv ? `(Lv.${lv})` : ""} 처치${prog}`;
    }
    case "trial":
      return trialText(q);
    default:
      return `레벨 ${q.count} 달성${n === undefined ? "" : ` (현재 ${n})`}`;
  }
}

/** @param {Quest} q @param {Reward} [rw] 실제로 주는 보상 (고르기는 고른 무기, v2.7.0 rewardFor: 경험치 제한 · 갑옷 종류) */
export function rewardText(q, rw = q.reward) {
  const r = rw;
  const parts = [];
  if (q.type === "choose" && !r.weapon) parts.push("고른 무기");
  if (r.emerald > 0) parts.push(`${CONFIG.CURRENCY_NAME} ${r.emerald}`);
  if (r.xp > 0) parts.push(`경험치 ${r.xp}레벨`);
  if ((r.xpPoints ?? 0) > 0) parts.push(`경험치 ${r.xpPoints}`);
  else if (r.capped && MONSTERS[r.capped]) parts.push(`§7경험치 없음 (최고 레벨 ${MONSTERS[r.capped].xpUntil}부터)§f`);
  if (r.points > 0) parts.push(`스탯 포인트 ${r.points}`);
  if (r.armorType && ARMOR_TYPES[r.armorType]) parts.push(`§c${ARMOR_TIERS.transcend_40.name} 갑옷 세트§r§f (${ARMOR_TYPES[r.armorType].name}, 초월)`);
  else if (r.armor === "path") parts.push(`§c${ARMOR_TIERS.transcend_40.name} 갑옷 세트§r§f (무기의 길에 맞는 종류, 초월)`);
  const w = r.weapon ? currentWeapon(parseWeapon(`rpg:${r.weapon}`)) : undefined;
  if (w) parts.push(`${w.name}§r§f`);
  if (r.item) {
    const iw = rewardItemWeapon(r.item);
    parts.push(iw ? `${r.item.nameTag || iw.name}§r§f ×${r.item.amount}` : `${r.item.name || shortId(r.item.id)} ×${r.item.amount}`);
  }
  for (const it of r.items ?? []) parts.push(`${rewardItemName(it.id)} ×${it.amount}`);
  return parts.length ? parts.join("§7, §f") : "없음";
}

export function conditionText(q) {
  const parts = [];
  if (q.main) parts.push(q.path ? `메인 퀘스트 · ${MAIN_PATHS[q.path].name}` : "메인 퀘스트");
  if (q.side === "repeat") parts.push(`반복 의뢰 · 메인 퀘스트 ${q.afterStep}단계 (그 몬스터 처치) 뒤`);
  if (q.side === "royal") parts.push("왕실 퀘스트 · 무기의 길을 고른 뒤");
  if (q.side === "legend") parts.push(q.path ? `전설 퀘스트 · ${MAIN_PATHS[q.path]?.name ?? ""} · 메인 퀘스트 ${q.afterStep}단계 (보스) 뒤` : "전설 퀘스트 · 초월 보스 (무기의 길과 무관)");
  if (q.requires) parts.push(`먼저 끝낼 퀘스트: ${getQuest(q.requires)?.title ?? "(지워짐)"}`);
  if (q.minLevel > 0) parts.push(`레벨 ${q.minLevel} 이상`);
  if (q.repeat) parts.push("반복 가능");
  return parts.length ? parts.join(", ") : "없음";
}

function lastPage(text, fallback) {
  const pages = toPages(text);
  return pages.length ? pages[pages.length - 1] : fallback;
}

/** 선택지·목록에 보이는 퀘스트 이름 (메인 퀘스트는 앞에 [메인], v2.7.0 반복 의뢰 [반복] · 왕실 [왕실]) */
function titleOf(q, color) {
  const tag = tagOf(q);
  return tag ? clipText(`${tag}${color}${q.title}`, TITLE_WIDTH + MAIN_TAG_WIDTH) : `${color}${clipText(q.title, TITLE_WIDTH)}`;
}

// ------------------------------------------------------------ 진행 처리
function sparkle(player, id) {
  const h = player.getHeadLocation();
  particle(player.dimension, id, { x: h.x, y: h.y + 0.2, z: h.z });
}

function accept(player, q) {
  const st = stateOf(player);
  /** @type {Entry} */
  const e = { s: "a", n: 0, c: st[q.id]?.c ?? 0 };
  if (q.type === "try") e.t = [];
  if (q.type === "trial") e.o = (q.goals ?? []).map(() => 0);
  st[q.id] = e;
  saveState(player);
  if (q.give?.length) giveTrialWeapons(player, q.give);
  pinQuest(player, q.id); // 길 안내 (v2.6.1): 받은 퀘스트를 안내
  notify(player, `${noticeTag(q)}퀘스트 수락 §r§f${q.title} §7- ${objectiveText(q)}`);
  player.playSound("random.orb", { pitch: 0.9 });
  sparkle(player, "minecraft:villager_happy");
}

export function abandon(player, q) {
  const st = stateOf(player);
  const c = st[q.id]?.c ?? 0;
  if (c > 0) st[q.id] = { s: "d", n: 0, c };
  else delete st[q.id];
  saveState(player);
  unpin(player, q.id);
  if (q.give?.length) takeBackTrials(player); // 무기 시험을 포기하면 빌린 무기도 돌려받음 (다시 받으면 새로)
}

/**
 * 아이템 보상이 RPG 무기면 지금 규칙의 무기 (구버전 무기는 같은 등급의 가장 가까운 레벨로, v1.1.9)
 * @param {{id: string} | null | undefined} item
 */
export function rewardItemWeapon(item) {
  return item ? currentWeapon(parseWeapon(item.id)) : undefined;
}

/**
 * 이 플레이어가 실제로 받는 보상 (v2.7.0): 반복 의뢰의 경험치는 최고 레벨이 그 몬스터의 xpUntil 이상이면 0 (capped),
 * 왕실 퀘스트의 갑옷은 무기의 길에 맞는 종류 (armorType)
 * @param {any} player
 * @param {Quest} q
 * @param {Reward} [base]
 * @returns {Reward}
 */
export function rewardFor(player, q, base = q.reward) {
  /** @type {Reward} */
  const r = { ...base };
  if (q.xpMob && xpCapped(player, q.xpMob)) {
    r.xpPoints = 0;
    r.xp = 0;
    r.capped = q.xpMob;
  }
  if (r.armor === "path") r.armorType = PATH_ARMOR[pathOf(player)] ?? "str";
  return r;
}

/** 왕실 퀘스트 갑옷 (초월 40렙 한 벌, 부위 순서) */
function armorSet(type) {
  return ARMOR_PIECE_ORDER.map((piece) => armorFrom(type, 40, "transcend", piece)).filter(Boolean);
}

/**
 * 보상으로 줄 아이템 묶음 (giveRewards 가 이대로 줌 — 가방 계산 missingSlots 도 같은 묶음으로)
 * @param {Reward} r
 * @returns {import("@minecraft/server").ItemStack[]}
 */
function rewardStacks(r) {
  /** @type {import("@minecraft/server").ItemStack[]} */
  const out = [];
  const add = (make) => {
    try {
      out.push(make());
    } catch (e) {
      console.warn(`[rpg] reward item: ${e}`);
    }
  };
  /** 한 칸 최대 개수로 나눠 담음 */
  const split = (id, amount, make) => {
    let max = 64;
    try {
      max = new ItemStack(id, 1).maxAmount;
    } catch (e) {
      console.warn(`[rpg] reward item ${id}: ${e}`);
      return;
    }
    for (let left = Math.floor(amount); left > 0; ) {
      const n = Math.min(max, left);
      add(() => make(n));
      left -= n;
    }
  };
  if (r.emerald > 0) split(CONFIG.CURRENCY_ITEM, r.emerald, (n) => new ItemStack(CONFIG.CURRENCY_ITEM, n));
  const w = r.weapon ? currentWeapon(parseWeapon(`rpg:${r.weapon}`)) : undefined;
  if (w) add(() => createWeaponItem(w));
  if (r.item) {
    const it = r.item;
    const iw = rewardItemWeapon(it);
    if (iw) {
      for (let i = 0; i < Math.min(64, Math.floor(it.amount)); i++)
        add(() => {
          const item = createWeaponItem(iw);
          if (it.nameTag) item.nameTag = it.nameTag;
          return item;
        });
    } else
      split(it.id, it.amount, (n) => {
        // v2.7.0: 전리품 · 룬 조각 · 뽑기권 · 레이드 재료를 주는 만든 퀘스트도 게임에서 얻은 것과 같은 설명 (겹쳐 쌓이게)
        const stack = materialStack(it.id, n);
        if (it.nameTag) stack.nameTag = it.nameTag;
        return stack;
      });
  }
  for (const it of r.items ?? []) split(it.id, it.amount, (n) => materialStack(it.id, n));
  // v2.7.0 왕실 퀘스트: 무기의 길에 맞는 초월 갑옷 한 벌 (rewardFor 가 종류를 정한 보상만)
  if (r.armorType) for (const a of armorSet(r.armorType)) add(() => createArmorItem(a));
  return out;
}

/** 재료 보상: 레이드·보스 재료·룬 뽑기권·(v2.7.0) 고급 룬 뽑기권·룬 조각·전리품은 게임에서 얻은 것과 겹치도록 같은 설명으로 */
function materialStack(id, n) {
  for (const [k, m] of Object.entries(RAID_MATERIALS)) if (m.item === id) return materialItem(k, n);
  for (const [k, m] of Object.entries(BOSS_MATERIALS)) if (m.item === id) return bossMaterialItem(k, n);
  if (id === RUNE.ticketItem) return createTicketItem(n);
  if (id === RUNE.advTicketItem) return createTicketItem(n, true);
  if (id === RUNE_SMITH.shardItem) return createShardItem(n);
  const drop = dropKeyOf(id);
  if (drop) return dropItem(drop, n);
  return new ItemStack(id, n);
}

/**
 * 받은 무기를 아직 쓸 수 없으면 알림 한 줄 (v2.6.2 렙제). 보상 무기 (무기 보상·아이템 보상 — 붙인 이름이 있으면 그 이름) 중 레벨이 가장 높은 것.
 * 보상 경험치를 받은 뒤의 레벨로 봄. 없으면 ""
 * @param {Reward} r
 */
export function rewardLevelLine(player, r) {
  /** @type {{w: any, name: string}[]} */
  const ws = [];
  const w = r.weapon ? currentWeapon(parseWeapon(`rpg:${r.weapon}`)) : undefined;
  if (w) ws.push({ w, name: w.name });
  const iw = rewardItemWeapon(r.item);
  if (iw) ws.push({ w: iw, name: r.item?.nameTag || iw.name });
  const top = ws.filter((x) => needsLevel(x.w)).sort((a, b) => b.w.level - a.w.level)[0];
  const lv = reqLevelOf(player);
  return top && lv < top.w.level ? `${top.name}§r§c: 레벨 ${top.w.level}부터 쓸 수 있음 §7(지금 최고 레벨 ${lv})` : "";
}

/** v2.7.0 왕실 갑옷: 아직 입을 수 없으면 알림 한 줄 (모자란 레벨·스탯 — 입어도 벗겨짐). 없으면 "" */
export function rewardArmorLine(player, r) {
  if (!r.armorType) return "";
  const a = armorSet(r.armorType)[0];
  const lack = a ? armorLack(player, a) : [];
  return lack.length ? `§c${ARMOR_TIERS.transcend_40.name} 갑옷: ${lack.join(" · ")} 필요 §7(모자라면 입어도 벗겨짐)` : "";
}

/** 완료 창 아래에 붙이는 알림 (줄바꿈 + rewardLevelLine · rewardArmorLine) */
export function rewardLevelNote(player, r) {
  const lines = [rewardLevelLine(player, r), rewardArmorLine(player, r)].filter(Boolean);
  return lines.length ? `\n${lines.join("\n")}` : "";
}

/** @param {Reward} r */
function giveRewards(player, r) {
  if (r.xp > 0) player.addLevels(r.xp);
  if ((r.xpPoints ?? 0) > 0) player.addExperience(r.xpPoints ?? 0);
  if (r.points > 0) addPoints(player, r.points);
  for (const stack of rewardStacks(r)) {
    try {
      giveItem(player, stack);
    } catch (e) {
      console.warn(`[rpg] reward: ${e}`);
    }
  }
}

/**
 * 보상을 넣을 자리가 모자란 칸 수 (v2.6.0: 가방이 차서 보상이 땅에 떨어지면 사라질 수 있음 — 특히 전설 무기).
 * 실제로 줄 아이템 묶음 (rewardStacks) 을 가방에 넣어 보는 셈: 이미 있는 묶음에는 게임처럼 겹칠 수 있을 때만 (isStackableWith, 한 칸 최대 개수).
 * 가져오기 퀘스트는 가져갈 아이템을 뺀 뒤로, trials = 시험용 무기를 돌려받은 뒤로 셈
 * @param {Reward} r
 */
function missingSlots(player, q, r, trials = false) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (!inv) return 0;
  /** @type {({item: import("@minecraft/server").ItemStack, n: number} | undefined)[]} */
  const slots = [];
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    slots.push(it && !(trials && isTrial(it)) ? { item: it, n: it.amount } : undefined);
  }
  if (q.type === "collect") {
    let left = q.count;
    for (let i = 0; i < slots.length && left > 0; i++) {
      const sl = slots[i];
      if (!sl || sl.item.typeId !== q.target) continue;
      const take = Math.min(left, sl.n);
      left -= take;
      sl.n -= take;
      if (sl.n <= 0) slots[i] = undefined;
    }
  }
  let missing = 0;
  for (const stack of rewardStacks(r)) {
    let n = stack.amount;
    const max = stack.maxAmount;
    for (const sl of slots) {
      if (n <= 0) break;
      if (!sl || sl.n >= Math.min(max, sl.item.maxAmount)) continue;
      let same = false;
      try {
        same = sl.item.isStackableWith(stack);
      } catch {
        same = false;
      }
      if (!same) continue;
      const add = Math.min(n, max - sl.n);
      sl.n += add;
      n -= add;
    }
    if (n <= 0) continue;
    const i = slots.findIndex((sl) => !sl);
    if (i < 0) {
      missing++;
      continue;
    }
    slots[i] = { item: stack, n };
  }
  return missing;
}

/** 가방이 모자랄 때 대화 */
async function fullBag(player, name, missing) {
  player.playSound("note.bass", { pitch: 0.7 });
  await new DialogForm(name, `§c가방이 꽉 찼군. 보상을 넣을 자리가 없어.\n§7빈 칸 §f${missing}칸§7을 만들고 다시 말을 걸어 주세요.`).choice("§f확인", ICON.OK).show(player);
}

/** 완료 처리. 아이템이 모자라면 false. reward = 실제로 주는 보상 (고르기는 고른 무기) */
function finish(player, q, reward = q.reward) {
  if (q.type === "collect" && !removeItem(player, q.target, q.count)) return false;
  giveRewards(player, reward);
  // v2.6.2 렙제: 받은 무기를 아직 쓸 수 없으면 채팅에도 남김 (완료 대사를 닫아 완료 창을 보지 못해도)
  const lvLine = rewardLevelLine(player, reward);
  if (lvLine) player.sendMessage(`§6[보상] §r${lvLine}`);
  const armorLine = rewardArmorLine(player, reward); // v2.7.0 왕실 갑옷을 아직 입을 수 없으면
  if (armorLine) player.sendMessage(`§6[보상] §r${armorLine}`);
  const st = stateOf(player);
  st[q.id] = { s: "d", n: 0, c: (st[q.id]?.c ?? 0) + 1 };
  saveState(player);
  unpin(player, q.id);
  navNow(player);
  notify(player, `${tagOf(q) ? `${noticeTag(q)}퀘스트 완료!` : "§6§l퀘스트 완료!"} §r§f${q.title}`);
  player.playSound("random.levelup", { pitch: 1.1 });
  sparkle(player, "minecraft:totem_particle");
  try {
    if (q.legend) legendFx(player, reward, q);
    if (q.finale && q.side === "royal") royalFx(player, reward);
    else if (q.finale) finaleFx(player);
    if (q.main && q.type === "kill") repeatOpened(player, q);
  } catch (e) {
    console.warn(`[rpg] main fx: ${e}`);
  }
  return true;
}

/** v2.7.0: 메인 처치 단계를 끝내면 그 몬스터의 반복 의뢰가 열림 → 채팅으로 한 번 (잉그리드를 세운 적이 있을 때) */
function repeatOpened(player, q) {
  if (!npcNameByKey(REPEAT_GIVER)) return;
  for (const r of sideQuests()) {
    if (r.side !== "repeat" || r.afterStep !== q.step || statusOf(player, r) !== "available") continue;
    player.sendMessage(`§b[반복 의뢰] §f${giverName(r)}§7에게 §f${r.title}§7 의뢰가 열렸습니다 §8(${objectiveText(r)} · 몇 번이든)`);
  }
}

function notifyReady(player, q) {
  navNow(player); // 길 안내: 이제 준 NPC에게
  const where = q.type === "talk" ? "" : ` §7- ${giverName(q)}에게 돌아가세요`;
  notify(player, `§a§l퀘스트 목표 달성! §r§f${q.title}${where}`, 80);
  player.playSound("random.levelup", { pitch: 1.4, volume: 0.8 });
  sparkle(player, "minecraft:totem_particle");
}

/** 몹이 죽었을 때 (entityDie after 이벤트). v2.7.0: 잡은 사람 = loot.js killCredit (불에 타 죽은 몬스터는 10초 안에 마지막으로 때린 플레이어) */
export function onEntityDied(dead, source) {
  const killer = killCredit(dead, source);
  if (!killer) return;
  let typeId;
  try {
    typeId = dead.typeId;
  } catch {
    return;
  }
  const list = kills().get(mobKey(typeId)) ?? [];
  const player = /** @type {import("@minecraft/server").Player} */ (killer);
  // v2.9.0 전설의 시련: 그 몬스터가 든 처치 목표 (RPG 몬스터만)
  const trials = typeId.startsWith("rpg:") && MONSTERS[shortId(typeId)] ? activeOfType(player, "trial") : [];
  if (!list.length && !trials.length) return;
  const st = stateOf(player);
  let changed = false;
  for (const [q, e] of trials) {
    const mob = shortId(typeId);
    if (trialStep(player, q, e, (g) => g.kind === "kill" && !!g.mobs?.includes(mob))) changed = true;
  }
  for (const q of list) {
    const e = st[q.id];
    if (!e || e.s !== "a" || e.n >= q.count) continue;
    e.n++;
    changed = true;
    if (e.n >= q.count) notifyReady(player, q);
    else {
      notify(player, `§e[퀘스트] §f${q.title} §7· §f${targetLabel(q)} §e${e.n}/${q.count}`);
      player.playSound("random.orb", { pitch: 0.7 + (0.6 * e.n) / q.count, volume: 0.5 });
    }
  }
  if (changed) saveState(player);
}

/** 진행 중인 퀘스트 중 이 종류 [퀘스트, 기록] */
function activeOfType(player, type) {
  const out = [];
  for (const [id, e] of Object.entries(stateOf(player))) {
    if (e.s !== "a") continue;
    const q = getQuest(id);
    if (q && q.type === type) out.push([q, e]);
  }
  return out;
}

/**
 * v2.9.0 trial: 맞는 목표 하나를 채움 (덜 찬 것 중 첫 목표). 채웠으면 true (저장은 부른 쪽)
 * @param {any} player @param {Quest} q @param {Entry} e @param {(g: Goal) => boolean} match
 */
function trialStep(player, q, e, match) {
  const goals = q.goals ?? [];
  const o = goals.map((_, i) => Math.max(0, Number(Array.isArray(e.o) ? e.o[i] : 0) || 0));
  const i = goals.findIndex((g, k) => match(g) && o[k] < g.count);
  if (i < 0) return false;
  o[i]++;
  e.o = o;
  e.n = o.reduce((n, c, k) => n + Math.min(c, goals[k].count), 0);
  if (e.n >= q.count) notifyReady(player, q);
  else {
    notify(player, `§c[전설] §f${q.title} §7· §f${trialText(q, o)}`);
    try {
      player.playSound("random.orb", { pitch: 0.7 + (0.6 * e.n) / q.count, volume: 0.5 });
    } catch {
      // 무시
    }
  }
  return true;
}

/**
 * RPG 무기로 몹을 맞혔을 때 (combat.js hitFeedback: 피해가 실제로 들어간 평타·활·완드) → 무기 시험(try)
 * @param {any} player
 * @param {any} victim
 * @param {any} w 맞힌 무기
 */
export function onWeaponHit(player, victim, w) {
  if (!w?.type || !player?.isValid || player.typeId !== "minecraft:player") return;
  try {
    if (!victim?.isValid || victim.typeId === "minecraft:player" || victim.typeId === "minecraft:npc" || victim.hasTag("rpg_npc")) return;
  } catch {
    return;
  }
  const list = activeOfType(player, "try");
  if (!list.length) return;
  let changed = false;
  for (const [q, e] of list) {
    if (!Array.isArray(e.t)) e.t = [];
    // v2.16.0 무기 시험은 길이 있는 여섯 종류만 셈 (제작 전용 마검 티르빙으로 때려도 세지 않음)
    if (e.t.includes(w.type) || e.t.length >= q.count || !PATH_ORDER.includes(w.type)) continue;
    e.t.push(w.type);
    e.n = e.t.length;
    changed = true;
    if (e.t.length >= q.count) notifyReady(player, q);
    else {
      notify(player, `§e[퀘스트] §f${q.title} §7· §a${TYPES[w.type].name} 손맛 확인 §e${e.t.length}/${q.count}`);
      player.playSound("random.orb", { pitch: 0.8 + (0.6 * e.t.length) / q.count, volume: 0.6 });
    }
  }
  if (changed) saveState(player);
}

/**
 * 보스를 쓰러뜨렸을 때 (bosses.js: 보상을 받은 사람 모두) → 보스 처치(boss)
 * @param {string} key 보스 키 (BOSSES)
 * @param {any[]} players
 */
export function onBossKilled(key, players) {
  for (const p of players ?? []) {
    if (!p?.isValid) continue;
    let changed = false;
    for (const [q, e] of activeOfType(p, "boss")) {
      if (q.target !== key || e.n >= q.count) continue;
      e.n++;
      changed = true;
      if (e.n >= q.count) notifyReady(p, q);
      else notify(p, `§e[퀘스트] §f${q.title} §7· §f${targetLabel(q)} §e${e.n}/${q.count}`);
    }
    // v2.9.0 전설의 시련: 그 보스 목표
    for (const [q, e] of activeOfType(p, "trial")) if (trialStep(p, q, e, (g) => g.kind === "boss" && g.boss === key)) changed = true;
    if (changed) saveState(p);
  }
}

/** 아이템·레벨 퀘스트 달성 알림 + 메인 퀘스트 새 이야기 알림. 주기적으로 호출. */
export function questTick(player) {
  const st = stateOf(player);
  let changed = false;
  for (const [id, e] of Object.entries(st)) {
    if (e.s !== "a") continue;
    const q = getQuest(id);
    if (!q || (q.type !== "collect" && q.type !== "level")) continue;
    const ready = isReady(player, q);
    if (ready && !e.r) {
      e.r = 1;
      changed = true;
      notifyReady(player, q);
    } else if (!ready && e.r) {
      e.r = 0;
      changed = true;
    }
  }
  if (changed) saveState(player);
  mainTick(player);
}

/** 다음 메인 퀘스트를 받을 수 있게 되면 (레벨이 오르는 등) 한 번 알림. 그 NPC를 세운 적이 없으면 알리지 않음 */
function mainTick(player) {
  const nx = nextMain(player);
  if (nx && npcNameByKey(nx.giver) && statusOf(player, nx) === "available" && mainNoted.get(player.id) !== nx.id) {
    mainNoted.set(player.id, nx.id);
    notify(player, `§6§l[메인] §r§f${giverName(nx)}에게 새 이야기가 있습니다 §7- ${nx.title}`, 80);
    player.playSound("random.orb", { pitch: 0.8, volume: 0.7 });
    return;
  }
  // v2.9.0 전설 퀘스트: 받을 수 있게 되면 한 번 (그 스승을 세운 적이 있을 때만)
  const path = pathOf(player);
  if (path) {
    const noted = legendNoted.get(player.id) ?? new Set();
    for (const lq of sideQuests()) {
      // v2.12.0 초월 보스 전설 (왕녀, 무기의 길과 무관) 은 길을 보지 않음
      if (lq.side !== "legend" || (lq.path && lq.path !== path) || noted.has(lq.id) || !npcNameByKey(lq.giver) || statusOf(player, lq) !== "available") continue;
      noted.add(lq.id);
      legendNoted.set(player.id, noted);
      notify(player, `§c§l[전설] §r§f${giverName(lq)}에게 전설 퀘스트가 있습니다 §7- ${lq.title}`, 80);
      player.playSound("random.orb", { pitch: 0.7, volume: 0.8 });
      return;
    }
  }
  // v2.7.0 왕실 퀘스트: 다음 단계를 받을 수 있게 되면 한 번 (왕녀를 세운 적이 있을 때만, 메인 알림과 겹치면 다음 차례에)
  const rq = nextRoyal(player);
  if (!rq || !npcNameByKey(rq.giver) || statusOf(player, rq) !== "available" || royalNoted.get(player.id) === rq.id) return;
  royalNoted.set(player.id, rq.id);
  notify(player, `§d§l[왕실] §r§f${giverName(rq)}에게 새 부탁이 있습니다 §7- ${rq.title}`, 80);
  player.playSound("random.orb", { pitch: 1.0, volume: 0.7 });
}

/**
 * 이름표 [!] 에 셀 퀘스트 수: 편집 창으로 만든 퀘스트만 (메인 · 반복 의뢰 · 왕실 퀘스트는 사람마다 달라 모두에게 [!] 를 띄우지 않고,
 * 받을 수 있는 사람에게만 머리 위 파티클·접근 안내로 알림)
 */
export function badgeCount(key) {
  return questsOf(key).filter((q) => !isBuiltin(q)).length;
}

/** 이 NPC와 관련된 퀘스트(주는 퀘스트 또는 말 걸기 대상)가 있는지. 읽기만 한다. */
export function hasQuestFor(key) {
  if (!key) return false;
  const { giverIndex: g, talkIndex: t } = indexes();
  return (g.get(key)?.length ?? 0) > 0 || (t.get(key)?.length ?? 0) > 0;
}

/** NPC 머리 위 표시: "ready"(보고할 퀘스트 있음) / "available"(받을 퀘스트 있음) / "repeat"(v2.7.0 받을 것이 반복 의뢰뿐) / "" */
export function questMark(player, key) {
  if (!key) return "";
  for (const q of talkTargetsOf(key)) if (q.giver !== key && stateOf(player)[q.id]?.s === "a") return "ready";
  let mark = "";
  for (const q of questsOf(key)) {
    const s = statusOf(player, q);
    if (s === "ready") return "ready";
    if (s === "available") mark = q.repeat && mark !== "available" ? "repeat" : "available";
  }
  return mark;
}

// ------------------------------------------------------------ 길 안내 (v2.6.1, nav.js 가 그림)
/**
 * 지금 가야 할 곳.
 * why: turnin (준 NPC에게 보고) · talk (그 NPC에게 — 스승 찾기·전하기) · offer (다음 메인 이야기를 줄 NPC)
 *      · hunt (몬스터 처치 → 그 몬스터 사냥터) · boss (보스 처치 → 그 보스 제단) · try (무기 시험 → 허수아비 사냥터)
 *      · collect (모으기 — 정해진 곳 없음) · level (레벨이 모자람 → 레벨에 맞는 사냥터)
 * @typedef {{q: Quest, main: boolean, why: string, npc?: string, mob?: string, mobs?: string[], boss?: string, what?: string, n?: number, count?: number, lv?: number}} NavGoal
 *   mobs: v2.9.0 전설의 시련 — 이 몬스터들 중 가장 가까운 사냥터
 */

/** 이 플레이어가 메인 이야기를 시작했는지 (할아버지 하랄의 첫 퀘스트를 받았거나 길을 골랐음) */
function mainStarted(player) {
  return !!pathOf(player) || !!stateOf(player)[MAIN_START];
}

/** 퀘스트 → 갈 곳 (갈 곳이 없으면 undefined) @returns {NavGoal | undefined} */
function goalOf(player, q) {
  const s = statusOf(player, q);
  const main = !!q.main;
  if (s === "ready") return { q, main, why: "turnin", npc: q.giver };
  if (s === "active") {
    if (q.type === "talk") return { q, main, why: "talk", npc: q.target };
    const n = progressOf(player, q);
    if (q.type === "kill") {
      const k = shortId(q.target);
      return { q, main, why: "hunt", mob: q.target.startsWith("rpg:") && MONSTERS[k] ? k : undefined, what: targetLabel(q), n, count: q.count };
    }
    if (q.type === "boss") return { q, main, why: "boss", boss: q.target, what: targetLabel(q), n, count: q.count };
    // v2.9.0 전설의 시련: 아직 덜 찬 첫 목표 (처치 → 그 몬스터들의 가장 가까운 사냥터, 보스 → 그 보스의 제단)
    if (q.type === "trial") {
      const o = trialCounts(player, q);
      const goals = q.goals ?? [];
      const i = goals.findIndex((g, k) => o[k] < g.count);
      const g = goals[i];
      if (!g) return undefined;
      if (g.kind === "boss") return { q, main, why: "boss", boss: g.boss, what: BOSSES[g.boss ?? ""]?.name ?? String(g.boss), n: o[i], count: g.count };
      return { q, main, why: "hunt", mobs: g.mobs, what: g.label, n: o[i], count: g.count };
    }
    if (q.type === "try") return { q, main, why: "try", mob: MONSTER_ORDER[0], what: "무기 시험", n, count: q.count };
    // v2.7.0: 몬스터 전리품이면 그 몬스터의 사냥터로 (글자는 "낡은 짚단 모으기 3/8")
    if (q.type === "collect") return { q, main, why: "collect", what: targetLabel(q), n, count: q.count, mob: dropKeyOf(q.target) };
    if (q.type === "level") return { q, main, why: "level", lv: q.count, mob: huntKind(questLevel(player)) };
    return undefined;
  }
  // 메인 이야기의 다음 단계: 받을 수 있으면 준 NPC에게, 레벨만 모자라면 레벨에 맞는 사냥터로
  // (이야기를 시작하지 않았으면 그 NPC를 세운 적이 있을 때만 — 메인 NPC가 없는 월드에서는 안내하지 않음)
  if (!main || (!mainStarted(player) && !npcNameByKey(q.giver))) return undefined;
  if (s === "available") return { q, main, why: "offer", npc: q.giver };
  if (s === "locked" && lockReason(player, q) === "level") return { q, main, why: "level", lv: q.minLevel, mob: huntKind(questLevel(player)) };
  return undefined;
}

/** 순서 (작을수록 먼저): 보고 → 메인 이야기의 진행 중 단계 → 다른 퀘스트의 말 걸기 → 다른 퀘스트의 나머지 → 다음 메인 이야기 받기 → 레벨 안내 */
function navRank(g) {
  if (g.why === "turnin") return 0;
  if (g.main && g.why !== "offer" && g.why !== "level") return 1;
  if (g.why === "talk") return 2;
  if (!g.main) return 3;
  return g.why === "offer" ? 4 : 5;
}

/** 크리에이티브·관전: 짓는 중인 운영자 — 받지 않은 메인 이야기 (새 이야기·레벨 안내) 는 안내하지 않음 */
function building(player) {
  try {
    const m = player.getGameMode();
    return m === GameMode.Creative || m === GameMode.Spectator;
  } catch {
    return false;
  }
}

/**
 * 지금 가야 할 곳: 고른 퀘스트 (받은 퀘스트·퀘스트 목록에서 고름) → 없거나 갈 곳이 없으면 navRank 순서 (같으면 메인 먼저).
 * 길 안내를 껐으면 undefined
 * @returns {NavGoal | undefined}
 */
export function navGoal(player) {
  const nav = navOf(player);
  if (nav.off) return undefined;
  const pinned = nav.q ? getQuest(nav.q) : undefined;
  const g = pinned ? goalOf(player, pinned) : undefined;
  if (g) return g;
  /** @type {NavGoal[]} */
  const cands = [];
  const nx = nextMain(player);
  const mg = nx ? goalOf(player, nx) : undefined;
  if (mg) cands.push(mg);
  for (const [id, e] of Object.entries(stateOf(player))) {
    if (e.s !== "a") continue;
    const q = getQuest(id);
    const og = q && !q.main ? goalOf(player, q) : undefined;
    if (og) cands.push(og);
  }
  const build = building(player);
  /** @type {NavGoal | undefined} */
  let best;
  let rank = 99;
  for (const c of cands) {
    if (build && c.main && (c.why === "offer" || c.why === "level")) continue; // 메인 이야기의 받지 않은 단계만 (받은 "레벨 달성" 퀘스트는 안내)
    const r = navRank(c);
    if (r < rank) {
      rank = r;
      best = c;
    }
  }
  return best;
}

// ------------------------------------------------------------ 메인 퀘스트: 시험용 무기 · 연출 · 인사말
function isTrial(item) {
  try {
    return item?.getDynamicProperty(TRIAL) === true;
  } catch {
    return false;
  }
}

/** 시험용 무기 (0렙 일반, 이름 앞 [시험용], 무기를 고르면 돌려받음) */
function trialItem(type) {
  const w = currentWeapon(parseWeapon(`rpg:${type}_l0_common`));
  const item = createWeaponItem(w);
  item.nameTag = `§7[시험용] §f${w.title}§r§7 [0렙]`;
  item.setDynamicProperty(TRIAL, true);
  return item;
}

/** 아직 가진 시험용 무기가 없는 종류만 준다 (포기했다가 다시 받아도 겹치지 않게) */
function giveTrialWeapons(player, types) {
  const have = new Set();
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (inv) {
    for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      const w = it && isTrial(it) ? parseWeapon(it.typeId) : undefined;
      if (w) have.add(w.type);
    }
  }
  for (const t of types) {
    if (have.has(t)) continue;
    try {
      giveItem(player, trialItem(t));
    } catch (e) {
      console.warn(`[rpg] trial weapon: ${e}`);
    }
  }
}

/** 시험용 무기를 모두 돌려받음 (가방·손). 돌려받은 수 */
function takeBackTrials(player) {
  let n = 0;
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (inv) {
    for (let i = 0; i < inv.size; i++) {
      if (!isTrial(inv.getItem(i))) continue;
      inv.setItem(i, undefined);
      n++;
    }
  }
  try {
    const eq = player.getComponent("minecraft:equippable");
    if (eq && isTrial(eq.getEquipment(EquipmentSlot.Offhand))) {
      eq.setEquipment(EquipmentSlot.Offhand, undefined);
      n++;
    }
  } catch {
    // 무시
  }
  return n;
}

const WHITE = [1, 1, 1];

/** 축하 연출: 눈앞 2.4칸에 빛기둥 + 마법진 섬광 + 폭발 + 발밑 충격파 (power 0~2: 길 고르기 · 전설 무기 · 마지막) */
function celebrateFx(player, color, power) {
  const dim = player.dimension;
  const c = player.location;
  const ground = add(c, flat(player.getViewDirection()), 2.4);
  const h = 3 + power * 1.5;
  const at = up(ground, 1.45);
  fx(dim, "rpg:arrow_shot", up(ground, h), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: h, thick: 0.6 + power * 0.2, life: 0.9 + power * 0.2, color, alpha: 0.9 });
  fx(dim, "rpg:arrow_shot", up(ground, h), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: h * 0.95, thick: 0.2 + power * 0.06, life: 0.8 + power * 0.2, color: WHITE, alpha: 1 });
  fx(dim, "rpg:magic_circle_flash", at, { size: 1 + power * 0.2, life: 0.7, spin: Math.random() * 360, progress: 1, color, alpha: 1 });
  burst(dim, at, color, 1.2 + power * 0.3, 0.45);
  shock(dim, c, color, 2 + power, 0.6);
  sparks(dim, at, color, 10 + power * 4, 5 + power);
  particle(dim, "minecraft:totem_particle", up(c, 1.2));
  play(dim, power >= 2 ? "random.totem" : "random.levelup", c, power >= 2 ? 1 : 1.2, 1);
  if (power >= 1) play(dim, "firework.twinkle", c, 1.1, 1);
  if (power >= 2) {
    play(dim, "firework.large_blast", c, 1, 1);
    for (let i = 0; i < 3; i++) system.runTimeout(() => shock(dim, c, i % 2 ? WHITE : color, 3 + i * 1.5, 0.6), 3 + i * 3);
  }
}

function broadcast(text) {
  try {
    world.sendMessage(text);
  } catch {
    // 무시
  }
}

/** 무기의 길을 고름: 큰 글자 + 길 색 연출 + 모두에게 알림 */
function pathFx(player, type) {
  const p = MAIN_PATHS[type];
  showRealTitle(player, `${p.tint}§l${p.name}`, `§f스승: ${mainNpcCall(p.mentor)}`);
  celebrateFx(player, p.color, 0);
  broadcast(`§6[메인 퀘스트] §e${player.name}§f님이 ${p.tint}${p.name}§f을 걷기 시작했습니다!`);
}

const GRADE_FX = { hero: [1.0, 0.8, 0.3], transcend: [1.0, 0.35, 0.35] };

/** 전설 무기 (영웅·초월) 를 받음 (v2.9.0 전설 퀘스트는 [전설 퀘스트] 로 알림). v2.12.0 초월 보스 전설 (무기 없음): 칭호 (honor) 를 큰 글자로 */
function legendFx(player, reward, q) {
  const w = reward.weapon ? currentWeapon(parseWeapon(`rpg:${reward.weapon}`)) : undefined;
  if (!w && q?.honor) {
    const boss = BOSSES[q.target];
    showRealTitle(player, `§c§l${q.honor}`, `§f${boss?.name ?? q.targetName} 토벌 §7· 전설 퀘스트 완료`);
    celebrateFx(player, boss?.color ?? GRADE_FX.transcend, 2);
    broadcast(`§c[전설 퀘스트] §e${player.name}§f님이 §c${boss?.name ?? q.targetName}§f을(를) 쓰러뜨리고 §6${q.honor}§f(이)가 되었습니다!`);
    return;
  }
  if (!w) return;
  showRealTitle(player, `${w.grade.color}${w.title}`, `§7${w.grade.name} 무기 획득!`);
  celebrateFx(player, GRADE_FX[w.grade.id] ?? WHITE, w.grade.id === "transcend" ? 2 : 1);
  broadcast(`${q?.side === "legend" ? "§c[전설 퀘스트]" : "§6[메인 퀘스트]"} §e${player.name}§f님이 ${w.name}§r§f을(를) 얻었습니다!`);
}

/** 메인 이야기의 끝 */
function finaleFx(player) {
  const p = MAIN_PATHS[pathOf(player)];
  showRealTitle(player, "§6§l영웅의 귀환", `§f${p ? `${p.name} · ` : ""}메인 퀘스트 완료`);
  celebrateFx(player, p?.color ?? [1.0, 0.82, 0.35], 2);
  if (p) broadcast(`§6[메인 퀘스트] §e${player.name}§f님이 ${p.tint}${p.name}§f을 끝까지 걸었습니다!`);
}

const ROYAL_COLOR = [0.86, 0.55, 1.0];

/** v2.7.0 왕실 퀘스트의 끝: 큰 글자 "왕실의 수호자" + 보랏빛 연출 (빛기둥 · 마법진 섬광 · 충격파 세 번), 모두에게 알림 */
function royalFx(player, reward) {
  const type = reward.armorType ? ARMOR_TYPES[reward.armorType] : undefined;
  showRealTitle(player, "§d§l왕실의 수호자", `§f${ARMOR_TIERS.transcend_40.name} 갑옷${type ? ` · ${type.name}` : ""} §7(초월)`);
  celebrateFx(player, ROYAL_COLOR, 2);
  broadcast(`§d[왕실 퀘스트] §e${player.name}§f님이 왕실의 수호자가 되어 §c${ARMOR_TIERS.transcend_40.name} 갑옷§f을 받았습니다!`);
}

/** 메인 퀘스트 NPC의 인사말 (NPC 설정 창에서 인사말을 넣으면 그것을 씀) */
export function mainGreeting(player, key) {
  const def = mainNpcByKey(key);
  if (!def) return "";
  const path = pathOf(player);
  const ended = mainFinished(player);
  // v2.9.0 지역 NPC: 무기 전 · 아직 오지 않음 · 이 지역 진행 중 · 이 지역을 마침
  const region = def.region ? REGIONS[def.region] : undefined;
  if (region) {
    if (!path) return region.greet.none;
    const chain = mainChain(path);
    const mine = chain.filter((q) => q.giver === key);
    if (mine.length && mine.every((q) => statusOf(player, q) === "done")) return region.greet.after;
    const arrive = chain.find((q) => q.type === "talk" && q.target === key);
    return arrive && statusOf(player, arrive) === "done" ? region.greet.here : region.greet.before;
  }
  if (def.greet) {
    if (!path) return def.greet.none;
    if (path !== def.path) return def.greet.other;
    return ended ? def.greet.done : def.greet.own;
  }
  if (key === "main_1") {
    if (ended) return "우리 영웅 왔구나! 오늘은 어떤 모험을 했니?";
    if (path) return `${MAIN_PATHS[path].name} 수련은 잘 되어 가니? 끼니는 거르지 말거라.`;
    if (stateOf(player)[MAIN_START]?.c > 0) return "브란에게는 다녀왔니? 무섭게 생겼어도 속은 따뜻한 사람이란다.";
    return "오, 우리 손자 왔구나. 또 언덕에서 먼 곳을 보고 있었니?";
  }
  if (key === "main_2") {
    if (path) return `${MAIN_PATHS[path].name} 수련은 어떠냐? 기본을 잊지 마라!`;
    if (!stateOf(player)[MAIN_START]) return "여긴 훈련장이다, 꼬마. 모험가가 되고 싶으면 먼저 하랄 어르신께 허락을 받아 와라!";
    return "훈련장에 온 걸 환영한다! 땀 흘릴 준비는 됐겠지?";
  }
  if (key === "main_9") return "무기를 벼려 주지. 강화석이나 별철 광석은 챙겨 왔나?";
  if (key === "main_10") {
    // v2.7.0 반복 의뢰: 열린 것이 있으면 의뢰 이야기
    if (sideQuests().some((q) => q.side === "repeat" && statusOf(player, q) !== "locked")) return "어서 오세요! 몬스터 전리품을 가져오셨나요? 의뢰가 잔뜩 쌓여 있어요!";
    return "어서 오세요! 따뜻한 수프가 있어요. 소문이 궁금하면 언제든지요!";
  }
  if (key === "main_11") {
    if (royalFinished(player)) {
      // v2.12.0 초월 보스 전설 퀘스트 (왕실 퀘스트를 마친 뒤): 받을 수 있음 · 진행 중 · 잡음
      // (초월 보스가 여럿이면 진행 중 → 받을 수 있음 → 가장 나중에 잡은 것 순서)
      const ls = TRANSCEND_LEGENDS.map((L) => {
        const lq = getQuest(transcendLegendId(L.boss));
        return { L, s: lq ? statusOf(player, lq) : "locked" };
      });
      const pick = ls.find((x) => x.s === "active") ?? ls.find((x) => x.s === "available") ?? [...ls].reverse().find((x) => x.s === "ready" || x.s === "done");
      if (pick) return pick.s === "active" ? pick.L.greet.active : pick.s === "available" ? pick.L.greet.offer : pick.L.greet.done;
      return "왕실의 수호자님! 황제 갑옷이 정말 잘 어울려요.";
    }
    if (!path) return "모험가님, 반가워요. 아직 무기를 고르지 않으셨군요. 훈련장의 브란 님께 먼저 가 보세요.";
    if (questLevel(player) < (nextRoyal(player)?.minLevel ?? 0)) return "북쪽의 위협에 맞설 영웅을 찾고 있어요. 조금 더 강해지면 다시 와 주세요.";
    return "와 주셨군요, 모험가님. 왕국을 위해 힘을 빌려주세요.";
  }
  if (key === "main_12") return "룬은 돌고 도는 법이지. 겹친 룬이나 쓰지 않는 룬이 있으면 가져오게. 갈아서 조각으로 만들어 주지.";
  return "";
}

/** 레벨에 맞는 몬스터 (몬스터 레벨 ≤ 내 레벨 + 2 중 가장 높은 것) — v2.7.0 loot.js (경험치 없음 알림의 추천 사냥과 같음) */
export { huntKind };

/** 레벨에 맞는 사냥터 이름 */
function huntHint(lv) {
  const k = huntKind(lv);
  return `${MONSTERS[k].name}(Lv.${MONSTERS[k].level})`;
}

/** 주 스탯 이름 (도끼는 힘 + 체력) */
function statText(type) {
  const t = TYPES[type];
  return [t.stat, ...Object.keys(t.reqExtra ?? {})].map((s) => STAT_INFO[s]?.name ?? s).join(" + ");
}

// ------------------------------------------------------------ NPC 대화
const KIND_ORDER = { deliver: 0, turnin: 0, offer: 1, progress: 2, wait: 2, more: 3, smith: 4, enhance: 4, shop: 4, armor: 4, runesmith: 4, bye: 5 };
/** 대화창의 일거리 칸 (퀘스트가 아닌 것) */
const SERVICE = new Set(["shop", "armor", "smith", "enhance", "runesmith", "bye"]);

/** @typedef {{kind:string, q?:Quest, text:string, icon:string}} Option */

/** @returns {Option[]} */
function dialogOptions(player, key, lv, smith = false, runesmith = false) {
  /** @type {Option[]} */
  const opts = [];
  for (const q of talkTargetsOf(key)) {
    if (q.giver !== key && stateOf(player)[q.id]?.s === "a") {
      opts.push({ kind: "deliver", q, text: `${titleOf(q, "§a")} §7(전달)`, icon: ICON.READY });
    }
  }
  for (const q of questsOf(key)) {
    const s = statusOf(player, q);
    if (s === "ready") opts.push({ kind: "turnin", q, text: `${titleOf(q, "§a")} §7(완료 보고)`, icon: ICON.READY });
    else if (s === "available") opts.push({ kind: "offer", q, text: titleOf(q, "§e"), icon: ICON.NEW });
    else if (s === "active") opts.push({ kind: "progress", q, text: `${titleOf(q, "§f")} §7(진행 중)`, icon: ICON.ACTIVE });
    // 메인 퀘스트 · 왕실 퀘스트 (v2.7.0): 레벨만 모자란 다음 이야기는 필요한 레벨과 함께 보여 줌
    else if (s === "locked" && (q.main || q.side === "royal" || q.side === "legend") && lockReason(player, q) === "level") opts.push({ kind: "wait", q, text: `${titleOf(q, "§7")} §8(레벨 ${q.minLevel})`, icon: QUEST_TYPES.level.icon });
  }
  if (smith) opts.push({ kind: "enhance", text: "§6무기 강화 §7(+1~+10)", icon: "textures/items/nether_star" });
  if (smith) opts.push({ kind: "smith", text: "§6영웅 무기 레벨 올리기", icon: "textures/items/iron_ingot" });
  if (lv >= 0) opts.push({ kind: "shop", text: "§b무기 상점 보기", icon: ICON.SHOP });
  if (lv >= 0) opts.push({ kind: "armor", text: "§b갑옷 상점 보기", icon: "textures/items/iron_chestplate" });
  if (runesmith) opts.push({ kind: "runesmith", text: "§d룬 갈기 · 뽑기권 사기", icon: RUNE_SMITH.shardIcon });
  opts.push({ kind: "bye", text: "§7대화 끝내기", icon: ICON.BYE });
  // 순서: 보고·전달 → 메인 → 왕실 · 전설 (v2.9.0) → 만든 퀘스트 → 반복 의뢰 (v2.7.0: 반복 의뢰가 많아도 이야기가 첫 장에) → 일거리 칸. 같은 묶음 안에서는 받기 → 진행 중
  const first = (o) => (o.kind === "deliver" || o.kind === "turnin" ? 0 : 1);
  const group = (o) => (!o.q ? 9 : o.q.main ? 0 : o.q.side === "royal" || o.q.side === "legend" ? 1 : o.q.side === "repeat" ? 3 : 2);
  return opts
    .map((o, i) => ({ o, i }))
    .sort((a, b) => first(a.o) - first(b.o) || group(a.o) - group(b.o) || KIND_ORDER[a.o.kind] - KIND_ORDER[b.o.kind] || a.i - b.i)
    .map((x) => x.o);
}

/** 보고·전달하거나 받을 퀘스트가 남았는지 (완료 보고 뒤 대화를 이어 갈지) */
function hasMoreToDo(player, key) {
  return dialogOptions(player, key, -1).some((o) => o.kind === "deliver" || o.kind === "turnin" || o.kind === "offer");
}

/** NPC를 터치했을 때 */
export function talkTo(player, npc) {
  return withFormLock(player, () => talkLoop(player, npc));
}

/**
 * NPC 대화 (폼 잠금 안에서 실행)
 * @param {import("@minecraft/server").Player} player
 * @param {import("@minecraft/server").Entity} npc
 */
export async function talkLoop(player, npc) {
  const key = npcKey(npc, true);
  const name = npcName(npc);
  let first = true;
  let page = 0;
  for (let guard = 0; guard < 50; guard++) {
    if (!npc.isValid || !player.isValid) return;
    const lv = shopLevel(npc);
    const smith = isSmith(npc);
    const runesmith = isRunesmith(npc);
    const greet = greeting(npc) || mainGreeting(player, key);
    const all = dialogOptions(player, key, lv, smith, runesmith);
    const questOpts = all.filter((o) => !SERVICE.has(o.kind));
    const hasQuest = questOpts.length > 0;
    // 휴대폰 화면 높이에 맞게 퀘스트는 한 장에 TALK_PAGE 개까지, 넘치면 "다른 퀘스트" 버튼
    const pages = Math.max(1, Math.ceil(questOpts.length / TALK_PAGE));
    page = page % pages;
    /** @type {Option[]} */
    const opts = questOpts.slice(page * TALK_PAGE, page * TALK_PAGE + TALK_PAGE);
    if (pages > 1) opts.push({ kind: "more", text: `§f다른 퀘스트 보기 §7(${page + 1}/${pages})`, icon: ICON.NEXT });
    for (const o of all) if (SERVICE.has(o.kind)) opts.push(o);
    // 인사말도 퀘스트도 없는 상점·대장장이·룬술사 NPC는 바로 그 창을 연다 (둘 이상이면 대화창에서 고름)
    const services = (lv >= 0 ? 1 : 0) + (smith ? 1 : 0) + (runesmith ? 1 : 0);
    if (first && !greet && !hasQuest && services === 1) {
      if (smith) await smithLoop(player, "enhance"); // 대장장이는 강화 창부터 (윗줄 왼쪽 칸 → 영웅 무기 레벨 올리기)
      else if (runesmith) await runesmithLoop(player);
      else await storeLoop(player, lv);
      return;
    }
    const fallback = smith
      ? "무기를 벼려 주지. 강화석이나 별철 광석은 챙겨 왔나?"
      : runesmith
        ? "룬은 돌고 도는 법이지. 쓰지 않는 룬이 있으면 가져오게."
        : lv >= 0
          ? "어서 오게. 좋은 무기와 갑옷이 많다네."
          : "안녕하신가, 모험가.";
    let text;
    if (first) {
      text = await showPages(player, name, greet, fallback);
      if (text === undefined) return;
    } else {
      text = lastPage(greet, fallback);
    }
    first = false;
    const form = new DialogForm(name, text);
    for (const o of opts) form.choice(o.text, o.icon);
    const o = opts[await form.show(player)];
    if (!o || o.kind === "bye") return;
    if (o.kind === "more") {
      page++;
      continue;
    }
    if (o.kind === "shop" || o.kind === "armor") {
      await storeLoop(player, lv, o.kind === "armor" ? "armor" : "weapons");
      return;
    }
    if (o.kind === "smith" || o.kind === "enhance") {
      await smithLoop(player, o.kind === "enhance" ? "enhance" : "smith");
      return;
    }
    if (o.kind === "runesmith") {
      await runesmithLoop(player);
      return;
    }
    const q = /** @type {Quest} */ (o.q);
    if (o.kind === "offer") {
      // 받자마자 끝낼 수 있으면 (v2.7.0: 전리품을 이미 모아 온 반복 의뢰 등) 대화를 이어 가 바로 보고
      if (await offerFlow(player, name, q)) {
        if (statusOf(player, q) === "ready") continue;
        return;
      }
      continue;
    }
    if (o.kind === "progress") {
      await progressFlow(player, name, q);
      continue;
    }
    if (o.kind === "wait") {
      await waitFlow(player, name, key, q);
      continue;
    }
    // 완료 보고·전달: 이 NPC에게 이어서 할 일(다음 이야기 등)이 있으면 대화를 이어 감
    if ((await turnInFlow(player, name, q)) && npc.isValid && player.isValid && hasMoreToDo(player, key)) continue;
    return;
  }
}

/** 퀘스트 제안. 수락하면 true */
async function offerFlow(player, name, q) {
  if (q.type === "choose") return chooseFlow(player, name, q);
  const text = await showPages(player, name, q.offer, "부탁이 하나 있네.");
  if (text === undefined) return false;
  const body = `${text}\n\n§e§l${q.title}§r\n§7목표: §f${objectiveText(q)}\n§7보상: §f${rewardText(q, rewardFor(player, q))}`;
  const sel = await new DialogForm(name, body).choice("§a수락하기", ICON.OK).choice("§7다음에 할게요", ICON.BYE).show(player);
  if (sel !== 0 || statusOf(player, q) !== "available") return false;
  accept(player, q);
  return true;
}

async function progressFlow(player, name, q) {
  const extra = [];
  if (q.type === "trial") {
    const left = trialBossesLeft(q, trialCounts(player, q));
    if (left.length && left.length <= 8) extra.push(`§7남은 보스: §f${left.join(", ")}`);
  }
  if (q.type === "try") {
    const tried = stateOf(player)[q.id]?.t ?? [];
    const left = PATH_ORDER.filter((t) => !tried.includes(t)).map((t) => TYPES[t].name);
    if (left.length) extra.push(`§7남은 무기: §f${left.join(", ")}`);
  }
  if (q.type === "boss") extra.push("§7보스 제단을 터치해 불러냅니다");
  const body = `${lastPage(q.progress, "아직 끝나지 않은 모양이군.")}\n\n§e§l${q.title}§r\n§7목표: §f${objectiveOf(player, q)}${extra.length ? `\n${extra.join("\n")}` : ""}`;
  const sel = await new DialogForm(name, body).choice("§f알겠습니다", ICON.OK).choice("§c퀘스트 포기", ICON.BYE).show(player);
  if (sel === 1) await confirmAbandon(player, name, q);
}

/** 메인 퀘스트: 레벨이 모자람 (필요한 레벨과 사냥터) */
async function waitFlow(player, name, key, q) {
  const line = (mainNpcByKey(key)?.wait ?? "레벨 {lv}에 오르면 다시 오게.").replace("{lv}", String(q.minLevel));
  const lv = questLevel(player);
  const body = `${line}\n\n§e§l${q.title}§r\n§7필요 레벨: §f${q.minLevel} §7(지금 ${lv})\n§7추천 사냥: §f${huntHint(lv)}`;
  await new DialogForm(name, body).choice("§f알겠습니다", ICON.OK).show(player);
}

/**
 * 메인 퀘스트: 무기의 길 고르기. 고르면 true
 * 휴대폰 화면 높이 때문에 대사는 모두 앞 장으로 넘기고, 고르는 창은 물음 한 줄 + 무기 여섯 (창을 닫으면 다음에)
 */
async function chooseFlow(player, name, q) {
  for (const page of toPages(q.offer)) {
    if ((await new DialogForm(name, page).choice("§f다음", ICON.NEXT).show(player)) !== 0) return false;
  }
  for (let guard = 0; guard < 20; guard++) {
    const form = new DialogForm(name, "§l어떤 무기의 길을 걷겠나?§r\n§7창을 닫으면 다음에 고릅니다");
    for (const t of PATH_ORDER) form.choice(`${MAIN_PATHS[t].tint}${TYPES[t].name} §7· ${MAIN_PATHS[t].desc}`, `textures/items/rpg/${t}/${t}_l0_common`);
    const type = PATH_ORDER[await form.show(player)];
    if (!type) return false;
    const p = MAIN_PATHS[type];
    const sure = await new DialogForm(name, `${p.tint}§l${p.name}§r을 걷겠나?\n§7스승: §f${mainNpcCall(p.mentor)}\n§7주 스탯: §f${statText(type)}\n§c한 번 고르면 바꿀 수 없다!`)
      .choice(`§a${p.name}로 가겠습니다`, ICON.OK)
      .choice("§7다시 고르기", ICON.NEXT)
      .show(player);
    if (sure < 0) return false;
    if (sure !== 0) continue;
    if (statusOf(player, q) !== "available") return false;
    const missing = missingSlots(player, q, { ...q.reward, weapon: `${type}_l0_common` }, true);
    if (missing > 0) {
      await fullBag(player, name, missing);
      return false;
    }
    await choosePath(player, name, q, type);
    return true;
  }
  return false;
}

/** 길을 정함: 시험용 무기를 돌려받고 고른 무기 (0렙 일반) + 보상, 스승에게 가는 퀘스트를 바로 받음 */
async function choosePath(player, name, q, type) {
  setPath(player, type);
  takeBackTrials(player);
  /** @type {Reward} */
  const reward = { ...q.reward, weapon: `${type}_l0_common` };
  finish(player, q, reward);
  pathFx(player, type);
  const nx = getQuest(firstPathQuest(type));
  if (nx && statusOf(player, nx) === "available") accept(player, nx);
  const text = await showPages(player, name, q.doneBy?.[type] ?? "", "스승을 찾아가라.");
  if (text === undefined) return;
  const next = nx ? `\n§6새 퀘스트: §f${nx.title} §7- ${objectiveText(nx)}` : "";
  await new DialogForm(name, `${text}\n\n§6§l퀘스트 완료!§r §f${q.title}\n§7받은 보상: §f${rewardText(q, reward)}${rewardLevelNote(player, reward)}${next}`).choice("§a확인", ICON.OK).show(player);
}

async function confirmAbandon(player, name, q) {
  const sel = await new DialogForm(name, `§e${q.title}§r 퀘스트를 포기할까요?\n§7지금까지의 진행 상황이 사라집니다.`)
    .choice("§c포기하기", ICON.BYE)
    .choice("§f취소", ICON.OK)
    .show(player);
  if (sel !== 0) return false;
  abandon(player, q);
  notify(player, `§7퀘스트 포기: ${q.title}`);
  player.playSound("note.bass", { pitch: 0.6 });
  return true;
}

/** 완료 보고·전달. 끝냈으면 true */
async function turnInFlow(player, name, q) {
  if (q.type === "talk") {
    if (stateOf(player)[q.id]?.s !== "a") return false;
  } else if (statusOf(player, q) !== "ready") return false;
  const reward = rewardFor(player, q); // v2.7.0: 경험치 제한 · 왕실 갑옷 종류
  const missing = missingSlots(player, q, reward);
  if (missing > 0) {
    await fullBag(player, name, missing);
    return false;
  }
  if (!finish(player, q, reward)) {
    await new DialogForm(name, `§c${targetLabel(q)}이(가) 모자라군.\n§7필요: ${q.count}개`).choice("§f확인", ICON.OK).show(player);
    return false;
  }
  // 보상은 이미 지급됨. 아래는 완료 대사.
  const text = await showPages(player, name, q.done, "고맙네! 약속한 보상일세.");
  if (text === undefined) return true;
  await new DialogForm(name, `${text}\n\n§6§l퀘스트 완료!§r §f${q.title}\n§7받은 보상: §f${rewardText(q, reward)}${rewardLevelNote(player, reward)}`)
    .choice("§a확인", ICON.OK)
    .show(player);
  return true;
}

// ------------------------------------------------------------ 퀘스트 목록 (플레이어)
export function openQuestLog(player) {
  return withFormLock(player, () => questLogLoop(player));
}

/** 퀘스트 목록 맨 위의 메인 퀘스트 한 줄 (메인 NPC를 세운 적이 없으면 없음) */
function mainLine(player) {
  if (!pathOf(player) && !npcNameByKey("main_1")) return "";
  const nx = nextMain(player);
  if (!nx) return "§6[메인] §a모든 이야기를 마쳤습니다!";
  const s = statusOf(player, nx);
  if (s === "active" || s === "ready") return `§6[메인] §f${nx.title} §7진행 중`;
  if (s === "available") return `§6[메인] §f${giverName(nx)}§7에게 새 이야기 §8(${nx.title})`;
  if (lockReason(player, nx) === "level") return `§6[메인] §7레벨 ${nx.minLevel}에 오르면 §f${giverName(nx)}§7에게 §8(지금 ${questLevel(player)})`;
  return "";
}

/** 진행 중인 퀘스트 목록 (폼 잠금 안에서 실행) */
export async function questLogLoop(player) {
  let page = 0;
  for (let guard = 0; guard < 100; guard++) {
    const st = stateOf(player);
    /** @type {Quest[]} */
    const list = [];
    for (const [id, e] of Object.entries(st)) {
      const q = e.s === "a" ? getQuest(id) : undefined;
      if (q) list.push(q);
    }
    list.sort(questOrder);
    const pages = Math.max(1, Math.ceil(list.length / LOG_PAGE));
    page = Math.min(page, pages - 1);
    const shown = list.slice(page * LOG_PAGE, page * LOG_PAGE + LOG_PAGE);
    const main = mainLine(player);
    const head =
      (main ? `${main}\n` : "") +
      (list.length
        ? `진행 중인 퀘스트 §e${list.length}개§r${pages > 1 ? ` §7(${page + 1}/${pages}쪽)` : ""}\n§7퀘스트를 누르면 자세히 봅니다.`
        : "진행 중인 퀘스트가 없습니다.\n§7이름 앞에 §e[!]§7 가 붙은 NPC에게 말을 걸어 보세요.");
    const form = new DialogForm("퀘스트 목록", `${head}\n${navLine(player)}`);
    for (const q of shown) {
      const ready = q.type !== "talk" && isReady(player, q);
      const n = q.type === "talk" ? undefined : progressOf(player, q);
      const label = ready ? "(완료 가능)" : n === undefined ? "" : `(${Math.min(n, q.count)}/${q.count})`;
      form.choice(`${titleOf(q, ready ? "§a" : "§f")} §7${label}`, ready ? ICON.READY : QUEST_TYPES[q.type].icon);
    }
    const hasNext = pages > 1;
    if (hasNext) form.choice(`§f다음 쪽 §7(${((page + 1) % pages) + 1}/${pages})`, ICON.NEXT);
    const navOff = navOf(player).off;
    form.choice(navOff ? "§b길 안내 켜기" : "§7길 안내 끄기", NAV_ICON);
    form.choice("§7닫기", ICON.BYE);
    const sel = await form.show(player);
    if (sel < 0) return;
    if (sel < shown.length) {
      await questDetail(player, shown[sel]);
      continue;
    }
    if (hasNext && sel === shown.length) {
      page = (page + 1) % pages;
      continue;
    }
    if (sel === shown.length + (hasNext ? 1 : 0)) {
      setNavOff(player, !navOff);
      notify(player, navOff ? "§b§l길 안내 §r§f켰습니다" : "§7길 안내를 껐습니다 §8(/nav 로 다시 켜기)");
      player.playSound("random.click", { pitch: navOff ? 1.3 : 0.8 });
      continue;
    }
    return;
  }
}

/** 길 안내 아이콘 (나침반) */
const NAV_ICON = "textures/items/compass_item";

/** 퀘스트 목록 위의 길 안내 한 줄 */
function navLine(player) {
  if (navOf(player).off) return "§7길 안내: 꺼짐 §8(아래 버튼 · /nav)";
  const g = navGoal(player);
  return g ? `§b길 안내: §f${g.q.title}` : "§7길 안내: 갈 곳 없음";
}

async function questDetail(player, q) {
  const ready = q.type !== "talk" && isReady(player, q);
  const left = q.type === "trial" ? trialBossesLeft(q, trialCounts(player, q)) : [];
  const lines = [
    q.main
      ? `§6메인 퀘스트${q.path ? ` · ${MAIN_PATHS[q.path].name}` : ""}`
      : q.side === "repeat"
        ? "§b반복 의뢰 §7(보고하면 바로 다시 받을 수 있음)"
        : q.side === "royal"
          ? "§d왕실 퀘스트"
          : q.side === "legend"
            ? `§c전설 퀘스트 §7(${q.path ? MAIN_PATHS[q.path]?.name ?? "" : "초월 보스"}, 한 번)`
            : "",
    `§7의뢰: §f${giverName(q)}`,
    `§7목표: §f${objectiveOf(player, q)}`,
    left.length && left.length <= 8 ? `§7남은 보스: §f${left.join(", ")}` : "",
    q.type === "boss" && !ready ? "§7보스 제단을 터치해 불러냅니다" : "",
    `§7보상: §f${rewardText(q, rewardFor(player, q))}`,
    ready ? `§a목표 달성! ${giverName(q)}에게 돌아가세요.` : "",
  ].filter(Boolean);
  // 길 안내 (v2.6.1): 이 퀘스트로 안내 (켜져 있고 지금 다른 퀘스트를 안내하는 중이면)
  const nav = navOf(player);
  const canPin = !nav.off && navGoal(player)?.q.id !== q.id && !!goalOf(player, q);
  const form = new DialogForm(q.title, lines.join("\n")).choice("§f돌아가기", ICON.OK).choice("§c퀘스트 포기", ICON.BYE);
  if (canPin) form.choice("§b이 퀘스트 길 안내", NAV_ICON);
  const sel = await form.show(player);
  if (sel === 1) await confirmAbandon(player, q.title, q);
  if (sel === 2 && canPin) {
    pinQuest(player, q.id);
    notify(player, `§b§l길 안내 §r§f${q.title}`);
    player.playSound("random.click", { pitch: 1.3 });
  }
}
