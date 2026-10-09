import { system, world } from "@minecraft/server";
import { PVP } from "./config.js";
import { hpState } from "./hp.js";

/**
 * v2.26.0 결투 (PvP) 상태: 누가 누구와 결투 중인지만 (명령 · 창 · 연출은 pvp.js).
 * combat.js (평타 · 활 · 완드 · 피해 막기 · 배율) · skills.js (스킬 · 룬 대상) · runes.js (룬 · 아군 효과 · 상태 줄) 가 읽는다.
 * 이 모듈은 위 모듈들을 가져오지 않는다 (서로 가져오는 고리가 생기지 않게).
 */

/** 플레이어 id → 결투 {partner: 상대 id, partnerName, start: 피해가 들어가기 시작하는 틱 (3 · 2 · 1 뒤), since: 수락한 틱, token} @type {Map<string, {partner: string, partnerName: string, start: number, since: number, token: number}>} */
const duels = new Map();
let nextToken = 1;

/** 이 플레이어의 결투 (없으면 undefined) */
export function duelOf(playerId) {
  return duels.get(playerId);
}

/** 결투 중 (3 · 2 · 1 포함) 상대의 id (없으면 undefined) — 아군 효과 (회복 · 보호막 · 방벽) 에서 뺄 때 */
export function duelPartnerId(player) {
  try {
    return duels.get(player.id)?.partner;
  } catch {
    return undefined;
  }
}

/**
 * a 와 b 가 서로 결투 상대이고 결투가 시작됐는지 (3 · 2 · 1 이 끝남). 이때만 플레이어끼리 피해가 들어간다.
 * before 이벤트 안에서도 부름 (읽기만)
 */
export function isDuelPartner(a, b) {
  try {
    if (!a || !b || a.id === b.id) return false;
    const d = duels.get(a.id);
    return !!d && d.partner === b.id && system.currentTick >= d.start && duels.get(b.id)?.partner === a.id;
  } catch {
    return false;
  }
}

/** 결투 시작 (둘 다 적음). 돌려주는 token 으로 나중에 같은 결투인지 확인 */
export function setDuel(a, b, start) {
  const token = nextToken++;
  const now = system.currentTick;
  duels.set(a.id, { partner: b.id, partnerName: b.name, start, since: now, token });
  duels.set(b.id, { partner: a.id, partnerName: a.name, start, since: now, token });
  return token;
}

/** 결투를 지움 (둘 다). 지운 결투 (없었으면 undefined) */
export function clearDuel(playerId) {
  const d = duels.get(playerId);
  if (!d) return undefined;
  duels.delete(playerId);
  if (duels.get(d.partner)?.partner === playerId) duels.delete(d.partner);
  partnerHits.delete(playerId);
  partnerHits.delete(d.partner);
  return d;
}

/** 이 플레이어가 결투 중인지 (3 · 2 · 1 포함) */
export function inDuel(playerId) {
  return duels.has(playerId);
}

/**
 * 도움 (회복 · 보호막 · 방벽) 을 줄 수 있는 사람인지: 자기 자신, 또는 결투 중이 아닌 사람 (결투 중인 두 사람은 밖에서 돕지 못함 · 결투 상대도 아님)
 * @param {any} caster
 * @param {any} pl
 */
export function canAssist(caster, pl) {
  try {
    return pl.id === caster?.id || !duels.has(pl.id);
  } catch {
    return false;
  }
}

/**
 * 피해를 준 플레이어: 플레이어 자신, 또는 길들인 동물 (늑대 등) 의 주인 (없으면 undefined). before 이벤트 안에서도 부름 (읽기만)
 * @param {any} source EntityDamageSource
 */
export function attackerOf(source) {
  try {
    const e = source?.damagingEntity;
    if (!e) return undefined;
    if (e.typeId === "minecraft:player") return e;
    const t = e.getComponent?.("minecraft:tameable");
    if (!t?.isTamed) return undefined;
    const owner = t.tamedToPlayer ?? (t.tamedToPlayerId ? world.getEntity(t.tamedToPlayerId) : undefined);
    return owner?.typeId === "minecraft:player" ? owner : undefined;
  } catch {
    return undefined;
  }
}

/**
 * 길들인 동물이면 주인 id (주인이 접속하지 않았거나 불러오지 않았어도). 아니면 undefined. before 이벤트 안에서도 부름 (읽기만)
 * @param {any} source EntityDamageSource
 */
export function petOwnerId(source) {
  try {
    const e = source?.damagingEntity;
    if (!e || e.typeId === "minecraft:player") return undefined;
    const t = e.getComponent?.("minecraft:tameable");
    return t?.isTamed ? t.tamedToPlayerId : undefined;
  } catch {
    return undefined;
  }
}

/** 결투 상대에게 마지막으로 맞은 틱 (맞은 사람 id → 틱): 그 뒤 PVP.blameTicks 안의 불 · 용암 · 떨어짐 · 위더는 결투 상대 탓 @type {Map<string, number>} */
const partnerHits = new Map();

/** combat.js: 결투 상대의 피해가 들어가려 함 (before 이벤트 — 기록만) */
export function notePartnerHit(victimId) {
  partnerHits.set(victimId, system.currentTick);
}

/** 결투 상대에게 마지막으로 맞은 틱 (없으면 -100000) */
export function partnerHitAt(victimId) {
  return partnerHits.get(victimId) ?? -100000;
}

/** 지금 결투가 하나라도 있는지 */
export function anyDuel() {
  return duels.size > 0;
}

/** 결투 중인 플레이어 id 들 */
export function duelists() {
  return [...duels.keys()];
}

/**
 * 결투 상대에게 들어가는 피해: × PVP.mult, PVP.capRatio > 0 이면 받는 사람 최대 체력 × capRatio 까지
 * @param {any} victim
 * @param {number} damage
 */
export function duelDamage(victim, damage) {
  let d = damage * PVP.mult;
  if (PVP.capRatio > 0) {
    try {
      d = Math.min(d, hpState(victim).max * PVP.capRatio);
    } catch {
      // 체력 상태가 없으면 줄이기만
    }
  }
  return d;
}

/** 상태 줄 덧붙임 (runes.js · skills.js 의 화면 하단 줄): 결투 상대 이름 · 남은 체력 (3 · 2 · 1 동안은 남은 초) */
export function duelHud(player) {
  const d = duels.get(player.id);
  if (!d) return "";
  const now = system.currentTick;
  if (now < d.start) return `§e⚔ ${d.partnerName} §f${Math.ceil((d.start - now) / 20)}초 뒤 결투`;
  let pct = "";
  try {
    const p = world.getEntity(d.partner);
    if (p?.isValid) {
      const s = hpState(p);
      pct = ` §f${Math.max(0, Math.round((s.hp / Math.max(1, s.max)) * 100))}%`;
    }
  } catch {
    pct = "";
  }
  return `§c⚔ ${d.partnerName}${pct}`;
}
