import { world } from "@minecraft/server";
import { LEVELS } from "./config.js";

/**
 * NPC 데이터 (NPC 엔티티의 동적 속성과 태그에 저장)
 *   rpg:npc_key  : 퀘스트와 연결할 고유 번호 (예: n3)
 *   rpg:npc_name : 이름 (이름표 앞의 [!]·[상점] 표시는 자동)
 *   rpg:greet    : 인사말
 *   태그 rpg_npc / rpg_shop / rpg_shop_lv_<레벨> / rpg_quest / rpg_smith(대장장이, v1.1.9) / rpg_runesmith(룬술사, v2.7.0)
 * 세계 동적 속성 rpg:npcs 에 번호 → 이름 목록을 둔다 (퀘스트 편집 창의 NPC 선택용).
 */

export const NPC_TYPE = "minecraft:npc";
export const NPC_TOOL_ID = "rpg:npc_tool";

const P_KEY = "rpg:npc_key";
const P_NAME = "rpg:npc_name";
const P_GREET = "rpg:greet";
const W_SEQ = "rpg:npc_seq";
const W_REG = "rpg:npcs";

export function stripFormat(s) {
  return String(s ?? "").replace(/§./g, "");
}

/** 이전 버전 이름표("[상점] 무기상 (10렙)")에서 이름만 꺼낸다 */
function legacyName(nameTag) {
  return stripFormat(nameTag)
    .replace(/\[(상점|!|대장장이|룬술사)\]/g, "")
    .replace(/\(\d+렙\)/g, "")
    .trim();
}

/**
 * NPC 고유 번호. create=true 이면 없을 때 새로 만든다 (세계를 바꾸므로 before 이벤트 밖에서만).
 * @param {import("@minecraft/server").Entity} npc
 */
export function npcKey(npc, create = false) {
  const k = npc.getDynamicProperty(P_KEY);
  if (typeof k === "string" && k) return k;
  if (!create) return "";
  const n = Number(world.getDynamicProperty(W_SEQ) ?? 0) + 1;
  world.setDynamicProperty(W_SEQ, n);
  const key = `n${n}`;
  npc.setDynamicProperty(P_KEY, key);
  return key;
}

/** 정해 둔 번호를 붙임 (v2.6.0 메인 퀘스트 NPC: main_1 …) */
export function setNpcKey(npc, key) {
  npc.setDynamicProperty(P_KEY, String(key));
}

export function npcName(npc) {
  const n = npc.getDynamicProperty(P_NAME);
  if (typeof n === "string" && n) return n;
  return legacyName(npc.nameTag) || "NPC";
}

export function setNpcName(npc, name) {
  npc.setDynamicProperty(P_NAME, String(name ?? "").trim() || "NPC");
}

export function greeting(npc) {
  const g = npc.getDynamicProperty(P_GREET);
  return typeof g === "string" ? g : "";
}

export function setGreeting(npc, text) {
  const t = String(text ?? "").trim();
  npc.setDynamicProperty(P_GREET, t ? t : undefined);
}

/** 상점 레벨. 상점이 아니면 -1 */
export function shopLevel(npc) {
  if (!npc.hasTag("rpg_shop")) return -1;
  for (const tag of npc.getTags()) {
    if (tag.startsWith("rpg_shop_lv_")) {
      const lv = Number(tag.slice("rpg_shop_lv_".length));
      if (LEVELS.includes(lv)) return lv;
    }
  }
  return LEVELS[0];
}

/** @param {number} level -1 이면 상점 해제 */
export function setShop(npc, level) {
  for (const tag of npc.getTags()) if (tag === "rpg_shop" || tag.startsWith("rpg_shop_lv_")) npc.removeTag(tag);
  if (LEVELS.includes(level)) {
    npc.addTag("rpg_shop");
    npc.addTag(`rpg_shop_lv_${level}`);
  }
}

/** 대장장이인지 (영웅 무기 레벨 올리기) */
export function isSmith(npc) {
  return npc.hasTag("rpg_smith");
}

export function setSmith(npc, on) {
  if (on) npc.addTag("rpg_smith");
  else npc.removeTag("rpg_smith");
}

/** 룬술사인지 (v2.7.0: 룬 갈기 · 뽑기권) */
export function isRunesmith(npc) {
  return npc.hasTag("rpg_runesmith");
}

export function setRunesmith(npc, on) {
  if (on) npc.addTag("rpg_runesmith");
  else npc.removeTag("rpg_runesmith");
}

export function nameTagFor(name, shopLv, hasQuest, smith = false, runesmith = false) {
  let prefix = "";
  if (hasQuest) prefix += "§e§l[!] ";
  if (shopLv >= 0) prefix += "§a§l[상점] ";
  if (smith) prefix += "§6§l[대장장이] ";
  if (runesmith) prefix += "§d§l[룬술사] ";
  const suffix = shopLv >= 0 ? ` §7(${shopLv}렙)` : "";
  return `${prefix}§r§f${name}${suffix}`;
}

// ------------------------------------------------------------ 이름 목록
/** @type {Record<string, string> | undefined} */
let registry;

function readRegistry() {
  if (!registry) {
    try {
      const v = world.getDynamicProperty(W_REG);
      registry = typeof v === "string" ? JSON.parse(v) : {};
    } catch {
      registry = {};
    }
  }
  return /** @type {Record<string, string>} */ (registry);
}

function writeRegistry(reg) {
  registry = reg;
  world.setDynamicProperty(W_REG, JSON.stringify(reg));
}

export function npcNameByKey(key) {
  return readRegistry()[key] ?? "";
}

/** [[번호, 이름], ...] 이름순 */
export function npcList() {
  return Object.entries(readRegistry()).sort((a, b) => (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0));
}

export function forgetNpc(key) {
  const reg = { ...readRegistry() };
  delete reg[key];
  writeRegistry(reg);
  forgetNpcPos(key);
}

// ------------------------------------------------------------ NPC 위치 기록 (v2.6.1 길 안내: 불러오지 않은 곳의 NPC도 안내)
/** 세계 동적 속성: NPC 번호 → 마지막으로 본 엔티티 {id, dim, x, y, z} (NPC를 만들거나 옮길 때, 가까이 가거나 불러와질 때 적음) */
const W_POS = "rpg:npc_pos";
/** @typedef {{id: string, dim: string, x: number, y: number, z: number}} NpcSpot */
/** @type {Record<string, NpcSpot> | undefined} */
let positions;

/** @returns {Record<string, NpcSpot>} */
function readPositions() {
  if (!positions) {
    try {
      const v = world.getDynamicProperty(W_POS);
      const o = typeof v === "string" ? JSON.parse(v) : {};
      positions = o && typeof o === "object" ? o : {};
    } catch {
      positions = {};
    }
  }
  return /** @type {Record<string, NpcSpot>} */ (positions);
}

function writePositions() {
  try {
    world.setDynamicProperty(W_POS, JSON.stringify(readPositions()));
  } catch (e) {
    console.warn(`[rpg] npc positions: ${e}`);
  }
}

/** 다시 읽게 (시험용) */
export function forgetNpcPositions() {
  positions = undefined;
}

const round1 = (v) => Math.round(v * 10) / 10;

/**
 * NPC 자리를 적어 둠 (반 칸 넘게 옮겼거나 차원·엔티티가 바뀌었을 때만 저장)
 * @param {import("@minecraft/server").Entity} npc
 * @param {string} [key] NPC 번호 (없으면 NPC에 붙은 것)
 * @param {import("@minecraft/server").Vector3} [at] 자리 (옮긴 직후처럼 엔티티 위치가 아직 바뀌지 않았을 때)
 * @param {string} [dimId]
 */
export function noteNpcPos(npc, key, at, dimId) {
  const k = key ?? npcKey(npc);
  if (!k) return;
  const main = readMainRecords()[k];
  if (main && main.id !== npc.id) return; // 예전에 세운 중복 메인 NPC (같은 번호로 더 나중에 세운 것이 있음)
  const l = at ?? npc.location;
  const dim = dimId ?? npc.dimension.id;
  const all = readPositions();
  const cur = all[k];
  if (cur && cur.id === npc.id && cur.dim === dim && Math.hypot(cur.x - l.x, cur.y - l.y, cur.z - l.z) < 0.5) return;
  if (cur && cur.id !== npc.id && !at) {
    // 같은 번호의 다른 NPC (구조물로 복사한 NPC 등) 가 아직 있으면 먼저 적은 것을 지킴 (서로 덮어써서 저장이 잦아지고 안내가 튀지 않게)
    try {
      const old = world.getEntity(cur.id);
      if (old?.isValid && old.typeId === NPC_TYPE && npcKey(old) === k) return;
    } catch {
      // 무시
    }
  }
  all[k] = { id: npc.id, dim, x: round1(l.x), y: round1(l.y), z: round1(l.z) };
  writePositions();
}

/**
 * 마지막으로 적어 둔 NPC 자리. 메인 NPC는 v2.6.0 에 /mainnpc 로 세운 기록도 봄
 * @returns {NpcSpot | undefined}
 */
export function npcPos(key) {
  if (!key) return undefined;
  const p = readPositions()[key];
  if (p) return p;
  const m = readMainRecords()[key];
  return m ? { id: m.id, dim: m.dim, x: m.x + 0.5, y: m.y, z: m.z + 0.5 } : undefined;
}

export function forgetNpcPos(key) {
  const all = readPositions();
  if (!(key in all)) return;
  delete all[key];
  writePositions();
}

/**
 * 태그·이름표·이름 목록을 현재 데이터에 맞춘다.
 * @param {import("@minecraft/server").Entity} npc
 * @param {number} questCount 이 NPC가 주는 퀘스트 수
 */
export function refreshNpc(npc, questCount) {
  const key = npcKey(npc, true);
  const name = npcName(npc);
  const lv = shopLevel(npc);
  npc.addTag("rpg_npc");
  if (questCount > 0) npc.addTag("rpg_quest");
  else npc.removeTag("rpg_quest");
  npc.nameTag = nameTagFor(name, lv, questCount > 0, isSmith(npc), isRunesmith(npc));
  const reg = readRegistry();
  if (reg[key] !== name) writeRegistry({ ...reg, [key]: name });
  if (!readPositions()[key]) noteNpcPos(npc, key); // v2.6.1 길 안내: 처음 보는 NPC만 (옮긴 자리는 옮기는 쪽이 직접 · NPC 연출이 따라감 — 옮긴 직후 엔티티 위치가 아직 예전일 수 있어서)
  return key;
}

// ------------------------------------------------------------ 메인 퀘스트 NPC 기록 (v2.6.0)
/** 세계 동적 속성: 메인 NPC 번호(main_1 …) → 마지막으로 세운 엔티티 {id, dim, x, y, z} */
const W_MAIN = "rpg:main_npc_ids";

function readMainRecords() {
  try {
    const v = world.getDynamicProperty(W_MAIN);
    const o = typeof v === "string" ? JSON.parse(v) : {};
    return o && typeof o === "object" ? o : {};
  } catch {
    return {};
  }
}

/** @returns {{id: string, dim: string, x: number, y: number, z: number} | undefined} */
export function mainNpcRecord(key) {
  return readMainRecords()[key];
}

/**
 * 이 NPC가 그 번호의 메인 NPC 라고 적어 둠 (같은 번호의 다른 엔티티는 예전 것 → 보이면 치움)
 * @param {string} key
 * @param {import("@minecraft/server").Entity} npc
 * @param {import("@minecraft/server").Vector3} [at] 옮긴 자리 (옮긴 직후 엔티티 위치가 아직 바뀌지 않았을 수 있어서)
 * @param {string} [dimId]
 */
export function setMainNpcRecord(key, npc, at, dimId) {
  const all = readMainRecords();
  const l = at ?? npc.location;
  all[key] = { id: npc.id, dim: dimId ?? npc.dimension.id, x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) };
  world.setDynamicProperty(W_MAIN, JSON.stringify(all));
}

export function forgetMainNpcRecord(key) {
  const all = readMainRecords();
  if (!(key in all)) return;
  delete all[key];
  world.setDynamicProperty(W_MAIN, JSON.stringify(all));
}

/**
 * NPC를 세울 자리: 플레이어 앞 2칸 (막혀 있으면 플레이어 자리)
 * @param {import("@minecraft/server").Player} player
 */
export function npcSpotNear(player) {
  const dir = player.getViewDirection();
  const len = Math.hypot(dir.x, dir.z) || 1;
  const base = player.location;
  let loc = { x: base.x + (dir.x / len) * 2, y: base.y, z: base.z + (dir.z / len) * 2 };
  try {
    const feet = player.dimension.getBlock(loc);
    const head = player.dimension.getBlock({ x: loc.x, y: loc.y + 1, z: loc.z });
    if (!feet || !head || !feet.isAir || !head.isAir) loc = { x: base.x, y: base.y, z: base.z };
  } catch {
    loc = { x: base.x, y: base.y, z: base.z };
  }
  return loc;
}

/**
 * 플레이어 앞 2칸에 NPC를 만들고 플레이어를 바라보게 한다 (막혀 있으면 플레이어 자리).
 * @param {import("@minecraft/server").Player} player
 */
export function spawnNpcNear(player) {
  const loc = npcSpotNear(player);
  const npc = player.dimension.spawnEntity(NPC_TYPE, loc);
  try {
    npc.teleport(loc, { facingLocation: player.getHeadLocation() });
  } catch {
    // 무시
  }
  return npc;
}

/**
 * 가장 가까운 NPC
 * @param {import("@minecraft/server").Player} player
 * @param {number} range
 */
export function nearestNpc(player, range) {
  const [npc] = player.dimension.getEntities({ type: NPC_TYPE, location: player.location, maxDistance: range, closest: 1 });
  return npc;
}
