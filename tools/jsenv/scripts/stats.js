import { world } from "@minecraft/server";
import { CONFIG, OBJ_LVMAX, OBJ_POINT, STAT_INFO, STAT_KEYS } from "./config.js";

/**
 * 스탯은 스코어보드에 저장한다. 명령어로도 바꿀 수 있게 하기 위함.
 *   rpg_str / rpg_dex / rpg_int / rpg_vit : 스탯
 *   rpg_point : 남은 포인트,  rpg_lvmax : 포인트를 받은 최고 경험치 레벨
 */

/** @typedef {{str:number, dex:number, int:number, vit:number, point:number, lvmax:number}} Stats */

/** 찍은 스탯 (스코어보드) @type {Map<string, Stats>} */
const cache = new Map();
/** 장비 보너스 (v1.5.0 갑옷) @type {Map<string, {str:number, dex:number, int:number, vit:number}>} */
const bonuses = new Map();
/** v2.24.0 룬 버프 (신체 강화 투신 · 초인 각성): 모든 스탯 + 비율 @type {Map<string, number>} */
const buffs = new Map();
/** 찍은 스탯 + 장비 보너스 (× 룬 버프) @type {Map<string, Stats>} */
const merged = new Map();
const NO_BONUS = Object.freeze({ str: 0, dex: 0, int: 0, vit: 0 });
/** @type {((player: import("@minecraft/server").Player, reason: "stats" | "equipment" | "buff") => void)[]} */
const listeners = [];

export function onStatsChanged(fn) {
  listeners.push(fn);
}

/** reason: "stats" = 찍은 스탯·레벨이 바뀜, "equipment" = 갑옷 보너스가 바뀜 (v1.5.0), "buff" = 룬 버프가 바뀜 (v2.24.0) */
function emit(player, reason = "stats") {
  for (const fn of listeners) {
    try {
      fn(player, /** @type {"stats" | "equipment" | "buff"} */ (reason));
    } catch (e) {
      console.warn(`[rpg] stats listener: ${e}`);
    }
  }
}

export function initObjectives() {
  for (const k of STAT_KEYS) ensureObjective(STAT_INFO[k].objective, STAT_INFO[k].name);
  ensureObjective(OBJ_POINT, "스탯 포인트");
  ensureObjective(OBJ_LVMAX, "최고 레벨");
}

function ensureObjective(id, name) {
  try {
    if (!world.scoreboard.getObjective(id)) world.scoreboard.addObjective(id, name);
  } catch (e) {
    console.warn(`[rpg] objective ${id}: ${e}`);
  }
}

function getScore(objId, player) {
  try {
    const obj = world.scoreboard.getObjective(objId);
    if (!obj) return 0;
    const v = obj.getScore(player);
    return typeof v === "number" ? v : 0;
  } catch {
    return 0;
  }
}

function setScore(objId, player, value) {
  try {
    world.scoreboard.getObjective(objId)?.setScore(player, Math.floor(value));
  } catch (e) {
    console.warn(`[rpg] setScore ${objId}: ${e}`);
  }
}

/**
 * 스코어보드에서 새로 읽어 캐시를 갱신한다. 값이 바뀌었으면 알림 (quiet 이면 알리지 않음).
 * @param {import("@minecraft/server").Player} player
 * @param {boolean} [quiet]
 */
export function readStats(player, quiet = false) {
  const prev = cache.get(player.id);
  /** @type {Stats} */
  const s = {
    str: Math.max(0, getScore(STAT_INFO.str.objective, player)),
    dex: Math.max(0, getScore(STAT_INFO.dex.objective, player)),
    int: Math.max(0, getScore(STAT_INFO.int.objective, player)),
    vit: Math.max(0, getScore(STAT_INFO.vit.objective, player)),
    point: Math.max(0, getScore(OBJ_POINT, player)),
    lvmax: Math.max(0, getScore(OBJ_LVMAX, player)), // 최대 체력의 레벨 보너스에 쓰임
  };
  cache.set(player.id, s);
  merged.delete(player.id);
  if (quiet) return s;
  if (!prev || prev.str !== s.str || prev.dex !== s.dex || prev.int !== s.int || prev.vit !== s.vit || prev.point !== s.point || prev.lvmax !== s.lvmax) {
    emit(player);
  }
  return s;
}

/** 캐시가 없으면 조용히 읽어 둔다. 읽기만 하므로 before 이벤트 안에서도 호출 가능. */
export function ensureStats(player) {
  if (!cache.has(player.id)) readStats(player, true);
}

/**
 * 효과에 쓰는 스탯 = 찍은 스탯 + 장비 보너스 (피해·최대 체력·방패·룬). 캐시만 읽는다 (before 이벤트 안에서 사용).
 * 요구 스탯 확인은 getBaseStats (찍은 스탯만)
 */
export function getStatsCached(player) {
  const m = merged.get(player.id);
  if (m) return m;
  const base = cache.get(player.id);
  if (!base) return { str: 0, dex: 0, int: 0, vit: 0, point: 0, lvmax: 0 };
  const b = bonuses.get(player.id) ?? NO_BONUS;
  const k = 1 + (buffs.get(player.id) ?? 0); // v2.24.0 룬 버프: (찍은 스탯 + 갑옷) × (1 + 비율), 반올림
  const out = { ...base, str: Math.round((base.str + b.str) * k), dex: Math.round((base.dex + b.dex) * k), int: Math.round((base.int + b.int) * k), vit: Math.round((base.vit + b.vit) * k) };
  merged.set(player.id, out);
  return out;
}

/** v2.24.0 룬 버프 비율 (없으면 0) */
export function statBuffOf(player) {
  return buffs.get(player.id) ?? 0;
}

/** v2.24.0 룬 버프를 바꾼다 (신체 강화 — 0 이면 없앰). 바뀌었으면 알림 (최대 체력 다시 계산: 늘면 그만큼 차고, 줄면 넘친 것만 깎임) */
export function setStatBuff(player, pct) {
  const prev = buffs.get(player.id) ?? 0;
  const next = pct > 0 ? pct : 0;
  if (Math.abs(prev - next) < 1e-9) return false;
  if (next > 0) buffs.set(player.id, next);
  else buffs.delete(player.id);
  merged.delete(player.id);
  emit(player, "buff");
  return true;
}

/** 찍은 스탯만 (요구 스탯 확인·스탯 창의 포인트) */
export function getBaseStats(player) {
  return cache.get(player.id) ?? { str: 0, dex: 0, int: 0, vit: 0, point: 0, lvmax: 0 };
}

/**
 * 요구 레벨에 쓰는 레벨 (v2.6.2 — 무기 렙제 · 갑옷 · 퀘스트 조건 모두): 최고 경험치 레벨 (rpg_lvmax — 죽거나 경험치를 써서 내려가도 그대로).
 * 막 오른 레벨은 기록 (checkLevelUp) 전이어도 셈. 읽기만 하므로 before 이벤트 안에서도 됨
 * @param {import("@minecraft/server").Player} player
 */
export function reqLevelOf(player) {
  let lv = 0;
  try {
    lv = Number(player.level) || 0;
  } catch {
    lv = 0;
  }
  return Math.max(getBaseStats(player).lvmax ?? 0, lv);
}

/** 장비 보너스 */
export function statBonusOf(player) {
  return bonuses.get(player.id) ?? NO_BONUS;
}

/** 장비 보너스를 바꾼다 (v1.5.0 갑옷). 바뀌었으면 알림 (최대 체력 다시 계산 등) */
export function setStatBonus(player, b) {
  const prev = bonuses.get(player.id) ?? NO_BONUS;
  const next = { str: b.str ?? 0, dex: b.dex ?? 0, int: b.int ?? 0, vit: b.vit ?? 0 };
  if (prev.str === next.str && prev.dex === next.dex && prev.int === next.int && prev.vit === next.vit) return false;
  bonuses.set(player.id, next);
  merged.delete(player.id);
  emit(player, "equipment");
  return true;
}

export function forget(playerId) {
  cache.delete(playerId);
  bonuses.delete(playerId);
  buffs.delete(playerId);
  merged.delete(playerId);
}

/** 포인트 1을 써서 스탯 1을 올린다. 성공하면 true. */
export function spendPoint(player, key) {
  const s = readStats(player);
  if (s.point <= 0 || !STAT_KEYS.includes(key)) return false;
  setScore(OBJ_POINT, player, s.point - 1);
  setScore(STAT_INFO[key].objective, player, s[key] + 1);
  readStats(player);
  return true;
}

export function addPoints(player, n) {
  const s = readStats(player);
  setScore(OBJ_POINT, player, Math.max(0, s.point + n));
  readStats(player);
}

/** 투자한 스탯을 모두 포인트로 되돌린다. */
export function resetStats(player) {
  const s = readStats(player);
  const refund = s.str + s.dex + s.int + s.vit;
  for (const k of STAT_KEYS) setScore(STAT_INFO[k].objective, player, 0);
  setScore(OBJ_POINT, player, s.point + refund);
  readStats(player);
  return refund;
}

/** 처음 접속한 플레이어 초기화. 새로 초기화했으면 true. */
export function setupNewPlayer(player) {
  if (player.hasTag("rpg_init")) {
    readStats(player);
    return false;
  }
  for (const k of STAT_KEYS) setScore(STAT_INFO[k].objective, player, 0);
  setScore(OBJ_POINT, player, CONFIG.START_POINTS);
  setScore(OBJ_LVMAX, player, player.level);
  player.addTag("rpg_init");
  readStats(player);
  return true;
}

/** 경험치 레벨이 처음 도달한 값이면 포인트를 준다. 받은 포인트 수를 돌려준다. */
export function checkLevelUp(player) {
  const lv = player.level;
  const best = getScore(OBJ_LVMAX, player);
  if (lv <= best) return 0;
  const gained = (lv - best) * CONFIG.POINTS_PER_LEVEL;
  setScore(OBJ_LVMAX, player, lv);
  addPoints(player, gained);
  return gained;
}
