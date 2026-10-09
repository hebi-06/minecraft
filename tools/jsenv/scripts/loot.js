import { ItemStack, system } from "@minecraft/server";
import { BOSS_CRAFT, CONFIG, MONSTER_DROPS, MONSTER_ORDER, MONSTERS } from "./config.js";
import { play, showTextAt } from "./fx.js";
import { add, fx, len, sparks, sub, unit, up } from "./skills.js";
import { reqLevelOf } from "./stats.js";
import { setLoreSafe } from "./weapons.js";
import { isMounted } from "./riding.js";

/**
 * 몬스터 전리품 · 경험치 (v2.7.0)
 * - 전리품: 플레이어가 잡으면 MONSTER_DROPS 의 chance 로 1개가 몬스터 자리에 떨어짐 (짧은 빛기둥 · 반짝이 · 소리).
 *   상점의 "전리품 팔기" 에서 에메랄드로, 메인 퀘스트 심부름 · 잉그리드의 반복 의뢰 재료
 * - 경험치: 구슬 대신 잡은 플레이어에게 바로 (MONSTERS.xp, 몬스터 엔티티의 구슬은 0). 최고 레벨 (무기 렙제와 같은 reqLevelOf) 이
 *   그 몬스터의 xpUntil (다음 몬스터의 레벨) 이상이면 0 — 몬스터마다 채팅으로 한 번 알림 (10분에 한 번)
 *   주면: 몬스터 자리에 "+N 경험치" 글자 + 플레이어 쪽으로 날아가는 연두 빛 + 구슬 소리
 * - 잡은 플레이어 (killCredit): 마지막 피해를 준 것이 플레이어면 그 사람. 불에 타거나 떨어져 죽거나 다른 몹 (늑대 등) 이
 *   마무리하면 10초 (CREDIT_TICKS) 안에 마지막으로 때린 플레이어 (monsters.js 가 noteHit 으로 적음, 바닐라 경험치 구슬과 같은 생각).
 *   플레이어가 때린 적이 없으면 없음. 퀘스트 처치 수 (quest.js) 도 같은 사람에게
 */

const PLAYER = "minecraft:player";
const XP_COLOR = [0.65, 1.0, 0.32];
/** 경험치 없음 알림: 같은 몬스터는 이 틱에 한 번 (10분) */
const NOTE_GAP = 12000;
/** "플레이어|몬스터" → 마지막으로 알린 틱 @type {Map<string, number>} */
const noted = new Map();

/** 때린 엔티티가 없는 죽음 (불 · 떨어짐) 을 마지막으로 때린 플레이어가 잡은 것으로 치는 시간 (10초) */
export const CREDIT_TICKS = 200;
/** 몬스터 ID → 마지막으로 때린 플레이어 · 틱 @type {Map<string, {player: any, tick: number}>} */
const lastHit = new Map();

/** 플레이어가 몬스터를 때림 (monsters.js 의 entityHurt) */
export function noteHit(entity, player) {
  let id;
  try {
    id = entity.id;
  } catch {
    return;
  }
  const now = system.currentTick;
  lastHit.set(id, { player, tick: now });
  if (lastHit.size > 256) for (const [k, h] of lastHit) if (now - h.tick > CREDIT_TICKS) lastHit.delete(k);
}

/**
 * 이 죽음을 누가 잡았는지 (경험치 · 전리품 · 퀘스트 처치 수): 마지막 피해를 준 플레이어,
 * 아니면 (불에 탐 · 떨어짐 · 다른 몹이 마무리) CREDIT_TICKS 안에 마지막으로 때린 플레이어. 없으면 undefined
 * @param {any} dead
 * @param {any} source damageSource
 */
export function killCredit(dead, source) {
  try {
    const k = source?.damagingEntity;
    if (k?.typeId === PLAYER) return k.isValid && !isMounted(k) ? k : undefined;
  } catch {
    // 아래에서 마지막으로 때린 사람
  }
  let id;
  try {
    id = dead.id;
  } catch {
    return undefined;
  }
  const h = lastHit.get(id);
  if (!h || system.currentTick - h.tick > CREDIT_TICKS) return undefined;
  try {
    return h.player?.isValid && !isMounted(h.player) ? h.player : undefined; // v2.8.0 탈것에 탄 동안은 잡을 수 없음 (불에 타 죽어도)
  } catch {
    return undefined;
  }
}

/** 확률 (시험이 바꿈) */
let rand = Math.random;
/** @param {() => number} fn */
export function setLootRandom(fn) {
  rand = fn ?? Math.random;
}

/** 전리품 아이템 ID → 몬스터 키 */
const BY_ITEM = new Map(MONSTER_ORDER.filter((k) => MONSTER_DROPS[k]).map((k) => [MONSTER_DROPS[k].item, k]));

/** 전리품 아이템 ID 의 몬스터 키 (전리품이 아니면 undefined) */
export function dropKeyOf(itemId) {
  return BY_ITEM.get(String(itemId ?? ""));
}

/** 전리품 아이템 ID 목록 (몬스터 순서) */
export function dropItemIds() {
  return [...BY_ITEM.keys()];
}

/** 전리품 설명 (한 줄 50바이트 안). v2.9.0: 보스 무기 제작에 쓰이면 "보스 무기 재료: 영웅 4 · 초월 6" */
export function dropLore(key) {
  const d = MONSTER_DROPS[key];
  const m = MONSTERS[key];
  const lines = [`§r§7${m.name} (Lv.${m.level}) 전리품`, `§r§7${d.desc}`, `§r§7상점: 1개 ${d.price} ${CONFIG.CURRENCY_NAME}`, "§r§7잉그리드의 반복 의뢰 재료"];
  const uses = BOSS_CRAFT.filter((r) => (r.drops ?? []).some(([k]) => k === key));
  const n = (g) => uses.filter((r) => r.grade === g).length;
  const parts = [n("hero") ? `영웅 ${n("hero")}` : "", n("transcend") ? `초월 ${n("transcend")}` : ""].filter(Boolean);
  if (parts.length) lines.push(`§r§6보스 무기 재료: ${parts.join(" · ")}`);
  return lines;
}

/** 설명이 붙은 전리품 (몬스터가 떨어뜨린 것·퀘스트·명령어 모두 이것 — 겹쳐 쌓이게) */
export function dropItem(key, amount = 1) {
  const d = MONSTER_DROPS[key];
  const item = new ItemStack(d.item, Math.max(1, Math.min(64, Math.floor(amount))));
  setLoreSafe(item, dropLore(key));
  return item;
}

/** 이 플레이어는 그 몬스터에게서 경험치를 못 받는지 (최고 레벨 ≥ xpUntil) */
export function xpCapped(player, key) {
  const m = MONSTERS[key];
  return !!m && m.xpUntil > 0 && reqLevelOf(player) >= m.xpUntil;
}

/** 그 몬스터를 잡으면 받는 경험치 (못 받으면 0) */
export function monsterXp(player, key) {
  const m = MONSTERS[key];
  return !m || xpCapped(player, key) ? 0 : m.xp;
}

/** 레벨에 맞는 몬스터 (몬스터 레벨 ≤ 내 레벨 + 2 중 가장 높은 것) — 추천 사냥 */
export function huntKind(lv) {
  let best = MONSTER_ORDER[0];
  for (const k of MONSTER_ORDER) if (MONSTERS[k].level <= lv + 2) best = k;
  return best;
}

/**
 * 몬스터가 죽음 (monsters.js 의 entityDie): 잡은 플레이어 (killCredit) 가 있으면 경험치 + 전리품
 * @param {any} dead 몬스터 (몸은 쓰러지는 중이거나 이미 없음 — 자리·차원만 씀)
 * @param {any} def 몬스터 설정 (키 포함)
 * @param {any} killer 잡은 플레이어 (killCredit, 없으면 undefined)
 * @param {number} height 몬스터 키 (연출 높이)
 */
export function lootOnDeath(dead, def, killer, height = 1.5) {
  try {
    if (!killer || killer.typeId !== PLAYER || !killer.isValid) return;
  } catch {
    return;
  }
  let at;
  let dim;
  try {
    at = { ...dead.location };
    dim = dead.dimension;
  } catch {
    return;
  }
  giveXp(killer, def, dim, up(at, Math.max(0.6, height * 0.75)));
  rollDrop(def.key, dim, at);
}

/** 경험치를 주거나 (연출), 못 받으면 알림 */
function giveXp(player, def, dim, from) {
  const xp = monsterXp(player, def.key);
  if (xp <= 0) {
    capNote(player, def);
    return;
  }
  try {
    player.addExperience(xp);
  } catch {
    return;
  }
  showTextAt(dim, from, `§a+${xp} 경험치`);
  // 몸에서 빠져나와 플레이어에게 빨려 드는 연두 빛 (구슬 대신)
  let to;
  try {
    to = up(player.location, 1.1);
  } catch {
    return;
  }
  for (let i = 0; i < 4; i++) {
    const s = add(from, { x: (rand() - 0.5) * 0.7, y: rand() * 0.5, z: (rand() - 0.5) * 0.7 });
    const d = sub(to, s);
    const L = len(d);
    if (L < 0.4) continue;
    fx(dim, "rpg:arrow_spark", s, { dir: unit(d), speed: Math.min(40, L / 0.35), size: 0.13, grow: 0.5, life: 0.35, color: XP_COLOR, alpha: 1 });
  }
  try {
    player.playSound("random.orb", { pitch: 0.9 + rand() * 0.5, volume: 0.55 });
  } catch {
    // 무시
  }
}

/** 경험치를 못 받는 까닭을 몬스터마다 한 번 (10분에 한 번) 채팅으로: 추천 사냥 몬스터와 함께 */
function capNote(player, def) {
  const k = `${player.id}|${def.key}`;
  const now = system.currentTick;
  const last = noted.get(k);
  if (last !== undefined && now - last < NOTE_GAP) return;
  noted.set(k, now);
  const m = MONSTERS[def.key];
  const best = MONSTERS[huntKind(reqLevelOf(player))];
  const hint = best && best !== m ? ` §8· §7추천 사냥: §f${best.name} (Lv.${best.level})` : "";
  try {
    player.sendMessage(`§7[경험치] §f${m.name}§7${eunNeun(m.name)} 최고 레벨 ${m.xpUntil}부터 경험치를 주지 않습니다${hint}`);
  } catch {
    // 무시
  }
}

/** 받침이 있으면 "은", 없으면 "는" (트롤은 · 허수아비는) */
function eunNeun(word) {
  const c = String(word).charCodeAt(String(word).length - 1) - 0xac00;
  return c >= 0 && c < 11172 && c % 28 !== 0 ? "은" : "는";
}

/** 전리품: chance 로 1개가 그 자리에 떨어짐 + 짧은 빛기둥 (전리품 색) · 반짝이 · 소리 */
function rollDrop(key, dim, at) {
  const d = MONSTER_DROPS[key];
  if (!d || rand() >= d.chance) return;
  const spot = up(at, 0.4);
  try {
    dim.spawnItem(dropItem(key, 1), spot);
  } catch {
    return;
  }
  fx(dim, "rpg:arrow_shot", up(at, 1.3), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: 1.2, thick: 0.22, life: 0.9, color: d.rgb, alpha: 0.85 });
  fx(dim, "rpg:arrow_shot", up(at, 1.3), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: 1.15, thick: 0.07, life: 0.8, color: [1, 1, 1], alpha: 0.9 });
  sparks(dim, spot, d.rgb, 5, 2.5);
  play(dim, "random.pop", spot, 1.2 + rand() * 0.3, 0.7);
}

/** 플레이어가 나가면 알림 기록 · 때린 기록을 지움 */
export function forgetLoot(playerId) {
  for (const k of noted.keys()) if (k.startsWith(`${playerId}|`)) noted.delete(k);
  for (const [k, h] of lastHit) {
    let pid;
    try {
      pid = h.player?.id;
    } catch {
      pid = undefined;
    }
    if (pid === playerId || pid === undefined) lastHit.delete(k);
  }
}
