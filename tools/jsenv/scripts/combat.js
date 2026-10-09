import { EntityDamageCause, EntitySwingSource, EquipmentSlot, MolangVariableMap, system, world } from "@minecraft/server";
import { BOSSES, BOW, MONSTERS, MOUNT, PVP, RUNE_STRIKE, SHIELD, SLASH, WAND, bossId, enhanceBonus, monsterId } from "./config.js";
import { attackerOf, duelDamage, isDuelPartner, notePartnerHit, petOwnerId } from "./duel.js";
import { blockFx, critFx, particle, play, showDamageText, warn } from "./fx.js";
import { isDying, onPlayerHealBefore, onPlayerHurtAfter, onPlayerHurtBefore } from "./hp.js";
import { registerSlashes, slashHit } from "./slash.js";
import { blockedByMount } from "./riding.js";
import { getBaseStats, getStatsCached, reqLevelOf } from "./stats.js";
import { enhanceOf, parseWeapon, reqsText, weaponLack } from "./weapons.js";

/**
 * 전투 규칙
 * - 칼·단검·도끼·방패: 근접 피해를 (무기 공격력 × 스탯 보너스 × 치명타)로 바꾼다. 방패는 체력 스탯으로 때린다 (v1.1.7).
 * - 완드·활: 사용하면 바라보는 대상에 명중 (완드는 마법 피해 = 방어구 무시, 활은 화살 피해 = 방어구 적용).
 *   활은 v1.1.6부터 당기기·화살 아이템 없이 쏘고, 빛줄기가 닿는 순간 맞는다.
 *   완드는 v1.1.8부터 시전(선딜레이)이 있다: 손 앞에 마법진이 그려지고 다 그려지면(WAND.castTicks) 마법이 나간다.
 *   몹을 눌러 때리면(모바일 터치) 평타 대신 그 몹을 향해 쏜다.
 * - 방패 (오른손만, v1.1.7): 들고 있으면 받는 피해 감소, 웅크리면 정면 공격을 크게 막는다.
 *   방패로 휘두르면 SHIELD.bashBlockLockTicks 동안 웅크려 막기가 풀린다 (막으면서 계속 때리지 못하게).
 * - 치명타는 무기에서만 나온다. 요구 스탯이 부족하면 피해가 들어가지 않는다.
 * - v2.6.2 렙제: [N렙] 무기는 최고 레벨이 N 이상이어야 쓴다 (요구 스탯과 함께 — 평타·발사·방패 감소·막기·스킬 모두).
 */

const PLAYER = "minecraft:player";
const MELEE = new Set(["sword", "dagger", "axe", "shield", "greatsword"]); // v2.16.0 마검 (티르빙)

/** 공격 표시 (맞은 대상|공격자 → 무기·치명타·v2.13.0 더한 피해). after 이벤트에서 연출에 쓴다 @type {Map<string, {crit: boolean, tick: number, w: any, bonus?: WeaponBonus}>} */
const marks = new Map();
const markKey = (victim, attacker) => `${victim.id}|${attacker.id}`;
/** 원거리(완드·활) 재사용: "플레이어|종류" → 다시 쓸 수 있는 틱 @type {Map<string, number>} */
const rangedReady = new Map();
/** 완드 시전 중인 플레이어 → 시전 정보 @type {Map<string, {w: any, start: number, forced: any, spin: number}>} */
const casting = new Map();
/** 방패로 마지막으로 휘두른 틱 (그 뒤 잠깐은 웅크려 막기가 안 됨) @type {Map<string, number>} */
const lastBash = new Map();
/** 스킬 피해 표시 (맞은 대상|공격자 → 치명타, 숫자 모양). 스킬 피해는 평타 공식으로 바꾸지 않는다 @type {Map<string, {crit: boolean, tick: number, style?: import("./fx.js").TextStyle}>} */
const skillMarks = new Map();
/** 활·완드 명중 (스크립트가 넣는 피해): 평타 공식·터치 발사로 바꾸지 않는다 @type {Map<string, number>} */
const scripted = new Map();
/** v2.14.0 스킬·룬 피해가 실제로 들어간 틱 (맞은 대상|공격자). 보스는 바닐라 피해 이벤트가 없어 정령이 무적 시간을 피하려고 봄 @type {Map<string, number>} */
const skillLanded = new Map();
/** v2.16.0 엔티티 id → 마지막으로 피해를 받은 틱 (게임의 피해 알림 — 누가 때렸든). 티르빙 스킬이 몹 무적 시간을 피해 넣을 때 @type {Map<string, number>} */
const hurtTicks = new Map();
/**
 * v2.24.0 평타에 더한 피해가 들어간 틱 (맞은 엔티티 id|공격자 id → 틱). 그 뒤 BONUS_GAP 틱 (몹 · 보스 무적 시간) 안의 평타에는 더하지 않음:
 * 무적 시간 안의 더 센 평타 (치명타) 는 차이만큼만 들어가므로, 더하면 마검 부여 · 평타 강화 횟수만 쓰이고 더한 피해는 거의 들어가지 않음 (모바일 연타)
 * @type {Map<string, number>}
 */
const bonusLanded = new Map();
const BONUS_GAP = 10;
/** 받는 피해 감소·반사 버프 (v1.2.0 스킬: v2.19.0 까지 불멸의 방벽 · v2.17.0 까지 신창 선풍, v1.3.0 룬, v1.6.0 반격 자세, v2.20.0 신의 방벽 — 돔 안에서만 cond). v1.6.0: 건 버프마다 한 층 (층마다 따로 끝남) @type {Map<string, Array<{until: number, reduce: number, reflect?: {mult?: number, w?: any, amount?: number, style?: import("./fx.js").TextStyle, fn?: (player: any, attacker: any) => void}, cond?: (player: any) => boolean}>>} */
const guards = new Map();

/**
 * 피해 훅 (v1.7.0 몬스터 monsters.js, v1.8.0 보스 bosses.js 가 등록). 모두 before 이벤트 안에서 (읽기만, 바꾸는 일은 system.run 으로)
 * - block(ev): 막을 피해 (몬스터·보스의 바닐라 근접 — 피해는 스크립트가 넣음). 하나라도 true 면 취소
 * - incoming(ev): 받는 피해 조정 (껍질 등, 무기 계산 뒤)
 * - absorb(ev): 받은 피해를 스크립트가 가져감 (보스: 플레이어마다 무적 시간, 체력은 스크립트가 줄임). true 면 바닐라 피해는 취소
 * - absorbed(target, attacker): 방금 absorb 가 가져간 공격인지 (스킬·원거리가 applyDamage 결과가 false 여도 맞은 것으로)
 * @type {{block?: (ev: any) => boolean, incoming?: (ev: any) => void, absorb?: (ev: any) => boolean, absorbed?: (target: any, attacker: any) => boolean}[]}
 */
const hurtHooks = [];
export function addHurtHooks(h) {
  if (h) hurtHooks.push(h);
}
/** 이전 이름 (v1.7.0) */
export function setMonsterHooks(h) {
  addHurtHooks(h);
}
/**
 * 무기로 맞힘 훅 (v2.6.0 메인 퀘스트의 무기 시험): 평타·활·완드 피해가 실제로 들어간 뒤 (after 이벤트) fn(공격자, 맞은 엔티티, 무기, v2.13.0 더한 피해)
 * @type {((attacker: any, victim: any, w: any, bonus?: any) => void)[]}
 */
const weaponHitHooks = [];
export function addWeaponHitHook(fn) {
  if (fn) weaponHitHooks.push(fn);
}
/**
 * v2.13.0 평타에 더하는 피해 (마검사 룬의 마검 부여): 평타·활·완드 피해를 넣기 직전 (치명타 뒤) fn(공격자, 맞을 엔티티, 무기) → {add, mark, key} | undefined.
 * 더한 피해는 그 평타와 한 번에 들어감 (몹 무적 시간에 막히지 않게). before 이벤트 안에서도 부르므로 세계를 바꾸지 않음.
 * 피해가 실제로 들어가면 무기로 맞힘 훅이 넷째 값으로 받음 (더한 것 · 피해 숫자는 평타 몫과 더한 몫으로 나눠 보임)
 * v2.24.0: style = 더한 몫의 피해 숫자 모양 (없으면 spell — 마검 부여 보라, 평타 강화 rune 노랑), grades = 평타 강화가 더한 등급들
 * @typedef {{add: number, total: number, mark: string, parts: {add: number, mark?: string, key?: string, style?: string, grades?: string[]}[]}} WeaponBonus
 * @type {((attacker: any, victim: any, w: any) => ({add: number, mark?: string, key?: string, style?: string, grades?: string[]} | undefined))[]}
 */
const bonusHooks = [];
export function addWeaponBonusHook(fn) {
  if (fn) bonusHooks.push(fn);
}
/**
 * v2.24.0 평타 치명타 확률에 더할 것 (평타 강화 룬 검신 강림): fn(공격자, 무기, 맞을 엔티티 — 없으면 표시용) → 더할 확률 (0 이면 없음). before 이벤트 안에서도 부름 (읽기만)
 * @type {((attacker: any, w: any, victim?: any) => number)[]}
 */
const critHooks = [];
export function addCritBonusHook(fn) {
  if (fn) critHooks.push(fn);
}
/** 평타 치명타 확률 = 무기 확률 + 훅들 (더한 것이 있으면 최대 cap = RUNE_STRIKE.critCap — 무기 확률이 그보다 높으면 그대로). victim = 맞을 엔티티 (훅이 대상을 가림) */
export function critChanceOf(attacker, w, victim = undefined, cap = RUNE_STRIKE.critCap) {
  let add = 0;
  for (const fn of critHooks) {
    try {
      add += Math.max(0, Number(fn(attacker, w, victim)) || 0);
    } catch (e) {
      console.warn(`[rpg] crit bonus: ${e}`);
    }
  }
  return add > 0 ? Math.min(Math.max(cap, w.critChance), w.critChance + add) : w.critChance;
}
/**
 * v2.24.0 활 · 완드 재사용 대기 배율 (평타 강화 룬 연타): fn(플레이어, 무기) → 배율 (1 = 그대로). 가장 작은 것
 * @type {((player: any, w: any) => number)[]}
 */
const rangedCdHooks = [];
export function addRangedCooldownHook(fn) {
  if (fn) rangedCdHooks.push(fn);
}
function rangedCdScale(player, w) {
  let k = 1;
  for (const fn of rangedCdHooks) {
    try {
      const v = Number(fn(player, w));
      if (v > 0 && v < k) k = v;
    } catch (e) {
      console.warn(`[rpg] ranged cooldown: ${e}`);
    }
  }
  return k;
}
/** v2.24.0 밀려나지 않음 (신체 강화 룬 강철 근육): 플레이어 → 끝나는 틱. 보스 · 몬스터 기술의 밀치기 · 끌어당김을 받지 않음 (bosskit · 크라켄) @type {Map<string, number>} */
const steadfast = new Map();
export function setSteadfast(player, ticks) {
  steadfast.set(player.id, Math.max(steadfast.get(player.id) ?? 0, system.currentTick + ticks));
}
export function isSteadfast(player) {
  return !!player && (steadfast.get(player.id) ?? 0) > system.currentTick;
}
export function clearSteadfast(playerId) {
  steadfast.delete(playerId);
}
/** 근접 무기인지 (칼 · 단검 · 도끼 · 방패 · 마검) */
export function isMeleeWeapon(w) {
  return !!w && MELEE.has(w.type);
}
/**
 * 평타 피해 dmg 에 더할 것 (없으면 undefined)
 * @returns {WeaponBonus | undefined}
 */
function weaponBonus(attacker, victim, w, dmg) {
  if (system.currentTick - (bonusLanded.get(markKey(victim, attacker)) ?? -1000) < BONUS_GAP) return undefined; // v2.24.0 무적 시간 안 (위)
  let add = 0;
  let mk = "";
  const parts = [];
  for (const fn of bonusHooks) {
    try {
      const r = fn(attacker, victim, w);
      if (r && r.add > 0) {
        add += r.add;
        mk += r.mark ?? "";
        parts.push(r);
      }
    } catch (e) {
      console.warn(`[rpg] weapon bonus: ${e}`);
    }
  }
  return add > 0 ? { add, total: dmg + add, mark: mk, parts } : undefined;
}

/** 이 틱에 스킬·룬 피해(applySkillDamage)로 들어가는 공격인지 (v2.1.0 보스: 스킬은 평타와 따로 무적 시간을 셈) */
export function isSkillHit(victim, attacker) {
  return !!victim && !!attacker && skillMarks.get(markKey(victim, attacker))?.tick === system.currentTick;
}

/** 보스 등이 방금 가져간 공격인지 */
export function absorbedHit(target, attacker) {
  return hurtHooks.some((h) => {
    try {
      return !!h.absorbed?.(target, attacker);
    } catch {
      return false;
    }
  });
}

/** 방패로 휘두른 직후라 막기가 풀려 있는지 */
function bashLocked(player) {
  return system.currentTick - (lastBash.get(player.id) ?? -1000) < (SHIELD.bashBlockLockTicks ?? 0);
}

function weaponIn(player, slot) {
  try {
    const item = player.getComponent("minecraft:equippable")?.getEquipment(slot);
    return item ? parseWeapon(item.typeId) : undefined;
  } catch {
    return undefined;
  }
}

/** 무기를 쓰는 데 모자란 것 (레벨·요구 스탯). v1.5.0: 직접 찍은 스탯 기준 (갑옷 보너스는 넣지 않음) */
export function lackOf(player, w) {
  return weaponLack(w, reqLevelOf(player), getBaseStats(player));
}

/** 무기를 쓸 수 있는지: v2.6.2 레벨 조건 (최고 레벨 ≥ 무기 레벨) + 요구 스탯 (도끼는 힘과 체력 둘 다) */
export function meetsRequirement(player, w) {
  return lackOf(player, w).length === 0;
}

/** 피해 배율 = 1 + Σ 스탯 × 1포인트당 비율 (도끼는 힘 2% + 체력 0.5%). 스킬 피해도 같은 배율 (v1.2.0). 갑옷 보너스 포함 (v1.5.0) */
export function statMultiplier(player, w) {
  const s = getStatsCached(player);
  return 1 + w.scaling.reduce((sum, b) => sum + (s[b.stat] ?? 0) * b.per, 0);
}

/** 최근에 손에 들었던 무기의 강화 단계: 플레이어 → 무기 ID → {n, tick} (v1.5.0) */
const recentEnhance = new Map();
const ENHANCE_MEMORY_TICKS = 120; // 쏜 뒤 무기를 바꿔도 날아가는 화살·스킬 파도·반사(최대 5초)에는 그 단계

/** 손에 든 무기의 강화 단계를 기록 (main.js heldTick 에서 4틱마다) */
export function rememberEnhance(player, wid, n) {
  let m = recentEnhance.get(player.id);
  if (!m) recentEnhance.set(player.id, (m = new Map()));
  m.set(wid, { n, tick: system.currentTick });
}

/**
 * 무기 w 의 강화 단계 (v1.5.0): 지금 손에 들었으면 그 아이템, 아니면 최근 6초 안에 들었던 같은 무기의 단계
 * (활을 쏘고 곧바로 룬으로 바꿔도 날아가던 화살·유성우·반사는 쏠 때의 강화로 맞음)
 */
export function heldEnhance(player, w) {
  try {
    const item = player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand);
    if (item?.typeId === w.id) {
      const n = enhanceOf(item);
      rememberEnhance(player, w.id, n);
      return n;
    }
  } catch {
    // 아래 기록 사용
  }
  const r = recentEnhance.get(player.id)?.get(w.id);
  return r && system.currentTick - r.tick <= ENHANCE_MEMORY_TICKS ? r.n : 0;
}

/** 무기 피해 배율 = 스탯 배율 × (1 + 강화 보너스). 평타·원거리·초월 스킬·반사 모두 이것 (v1.5.0) */
export function powerMultiplier(player, w) {
  return statMultiplier(player, w) * (1 + enhanceBonus(heldEnhance(player, w)));
}

/**
 * 쓸 수 없을 때 경고 글자 (화면 하단). 레벨이 모자라면 레벨 먼저 (스탯은 레벨이 올라 포인트를 받아야 찍을 수 있으므로)
 * @param {any} w
 * @param {any} [player] 없으면 요구 스탯 전체
 */
export function reqText(w, player) {
  const lack = player ? lackOf(player, w) : [];
  const lvLack = lack.some((x) => x.level !== undefined);
  const statLack = !player || lack.some((x) => x.stat);
  const reqs = reqsText(w.reqs, false, "§c · ");
  if (!lvLack) return `§c요구 스탯 부족: ${reqs}§c 필요 §7(스탯 책)`;
  const now = `§7(최고 레벨 ${reqLevelOf(player)})`;
  if (!statLack) return `§c레벨 ${w.level}부터 쓸 수 있는 무기 ${now}`;
  return `§c레벨 ${w.level} · ${reqs}§c 필요 ${now}`;
}

function warnLater(player, text) {
  system.run(() => {
    if (player.isValid) warn(player, text);
  });
}

/**
 * 공격을 기억만 한다. 치명타·참격 연출은 피해가 실제로 들어간 뒤(after 이벤트)에 한다.
 * (무적 시간에 막힌 공격은 before 이벤트만 오고 after 이벤트는 오지 않는다)
 * @param {any} victim
 * @param {any} attacker
 * @param {any} w
 * @param {boolean} crit
 * @param {WeaponBonus} [bonus] v2.13.0 평타에 더한 피해
 */
function mark(victim, attacker, w, crit, bonus = undefined) {
  marks.set(markKey(victim, attacker), { crit, w, tick: system.currentTick, bonus });
}

/** 공격하는 쪽 보정. before 이벤트 안이라 세계를 바꾸는 호출은 system.run 으로 미룬다. */
function adjustOutgoing(ev) {
  const src = ev.damageSource;
  const attacker = src.damagingEntity;
  if (!attacker || attacker.typeId !== PLAYER) return;
  const victim = ev.hurtEntity;
  if (victim.id === attacker.id) return;
  // 스킬 피해·활 명중은 스크립트가 계산한 값 그대로 (평타 공식·터치 발사로 바꾸지 않음)
  if (skillMarks.get(markKey(victim, attacker))?.tick === system.currentTick) return;
  if (scripted.get(markKey(victim, attacker)) === system.currentTick) return;
  // 체력이 0이 되어 사망 처리 중인 플레이어는 보정하지 않는다 (사망 피해가 줄어들지 않게)
  if (victim.typeId === PLAYER && isDying(victim)) return;

  if (src.cause === EntityDamageCause.entityAttack) {
    const w = weaponIn(attacker, EquipmentSlot.Mainhand);
    if (!w) return;
    if (w.type === "wand" || w.type === "bow") {
      if (isTalkable(victim)) return; // NPC·주민은 그대로 (쏘지 않음)
      // 모바일 터치: 완드·활로 몹을 눌러 때리면 평타(1) 대신 그 몹을 향해 쏜다 (재사용 대기 중이면 아무 일 없음)
      ev.cancel = true;
      const player = /** @type {any} */ (attacker);
      system.run(() => {
        if (player.isValid && victim.isValid) castRanged(player, w, victim);
      });
      return;
    }
    if (!MELEE.has(w.type)) return;
    if (!meetsRequirement(attacker, w)) {
      ev.cancel = true;
      warnLater(attacker, reqText(w, attacker));
      return;
    }
    let dmg = w.damage * powerMultiplier(attacker, w);
    const crit = Math.random() < critChanceOf(attacker, w, victim); // v2.24.0 검신 강림 +치명타
    if (crit) dmg *= w.critMult;
    const bonus = weaponBonus(attacker, victim, w, dmg); // v2.13.0 마검 부여, v2.24.0 평타 강화
    if (bonus) dmg += bonus.add;
    ev.damage = dmg;
    mark(victim, attacker, w, crit, bonus);
  }
}

/** NPC·주민처럼 터치하면 대화·거래하는 엔티티 (쏘지 않음) */
const TALKABLE = new Set(["minecraft:npc", "minecraft:villager", "minecraft:villager_v2", "minecraft:wandering_trader"]);
export function isTalkable(e) {
  try {
    return TALKABLE.has(e.typeId) || e.hasTag("rpg_npc");
  } catch {
    return false;
  }
}

/** 체력이 0 이하 (죽는 모습이 나오는 중인 몹) */
export function hasNoHealth(e) {
  try {
    const hp = e.getComponent("minecraft:health");
    return hp !== undefined && hp.currentValue <= 0;
  } catch {
    return false;
  }
}

/** 엔티티를 터치(상호작용)한 틱. 모바일에서 같은 터치로 itemUse 가 함께 오면 쏘지 않으려고 @type {Map<string, number>} */
const lastTalk = new Map();

/** 방금(2틱 안에) NPC·주민을 터치했는지 (같은 터치로 온 아이템 사용은 무시) */
export function recentlyTalked(player) {
  return system.currentTick - (lastTalk.get(player.id) ?? -100) <= 2;
}

/**
 * 스킬 피해 (v1.2.0). 평타 공식을 거치지 않고 그대로 들어가며, 피해 숫자는 스킬 색으로 나온다.
 * @param {import("./fx.js").TextStyle} [style] 피해 숫자 모양 (없으면 스킬 색)
 * @returns {boolean} 피해가 실제로 들어갔는지 (무적 시간·면역이면 false)
 */
export function applySkillDamage(player, target, amount, crit, cause, style) {
  if (!player.isValid || !target?.isValid || !(amount > 0)) return false;
  const key = markKey(target, player);
  skillMarks.set(key, { crit, tick: system.currentTick, style }); // style: 피해 숫자 모양 (룬 = "rune")
  let hurt = false;
  try {
    hurt = target.applyDamage(amount, { cause, damagingEntity: player });
  } catch {
    hurt = false;
  }
  if (!hurt && absorbedHit(target, player)) hurt = true; // v1.8.0 보스: 체력은 스크립트가 줄이고 숫자도 그쪽에서
  if (!hurt) skillMarks.delete(key);
  else skillLanded.set(key, system.currentTick);
  return hurt;
}

/** v2.14.0 이 공격자의 스킬·룬 피해가 target 에 마지막으로 들어간 틱 (없으면 -1000) */
export function skillLandedAt(target, attacker) {
  return skillLanded.get(markKey(target, attacker)) ?? -1000;
}

/**
 * v2.16.0 target 이 마지막으로 피해를 받은 틱: 게임의 피해 알림 (누가 때렸든 — 평타 · 다른 사람 · 불 등) 과
 * 이 공격자의 스킬 · 룬 피해 (바로 적음 — 보스는 스크립트가 받아 게임 알림이 없고, 알림은 그 틱의 타이머가 다 돈 뒤에 옴) 중 늦은 것. 없으면 -1000
 */
export function lastHurtTick(target, attacker) {
  return Math.max(hurtTicks.get(target.id) ?? -1000, attacker ? skillLandedAt(target, attacker) : -1000);
}

/**
 * 받는 피해 감소(·반사) 버프. reflect 가 있으면 때린 몹에게 무기 공격력 × mult × 스탯 배율을 돌려준다.
 * 겹쳐 걸면 버프마다 제 시간에 끝난다 (v1.6.0): 감소는 걸려 있는 것 중 가장 센 것, 반사는 가장 나중에 건 것 (그것이 끝나면 앞의 것).
 * 예: 방벽(5초·반사) 중에 반격 자세(2.5초) → 2.5초 동안 반격, 그 뒤 방벽이 끝날 때까지 방벽 반사
 * @param {import("@minecraft/server").Player} player
 * @param {number} ticks
 * @param {number} reduce 0~1
 * @param {{mult?: number, w?: any, amount?: number, style?: import("./fx.js").TextStyle, fn?: (player: any, attacker: any) => void} | undefined} [reflect]
 * @param {((player: any) => boolean) | undefined} [cond] v2.20.0 이 조건일 때만 (신의 방벽: 돔 안에 서 있는 동안) — 맞을 때마다 확인
 */
export function setGuard(player, ticks, reduce, reflect, cond) {
  const now = system.currentTick;
  const list = (guards.get(player.id) ?? []).filter((g) => g.until > now);
  list.push({ until: now + ticks, reduce, reflect, cond });
  guards.set(player.id, list);
}

/** 감소·반사 버프를 모두 지움 (v1.6.0: 죽으면 — 다시 태어난 뒤까지 남지 않게) */
export function clearGuards(playerId) {
  guards.delete(playerId);
}

/**
 * 지금 걸려 있는 감소·반사 버프를 합친 것 (없으면 undefined)
 * @returns {{until: number, reduce: number, reflect?: {mult?: number, w?: any, amount?: number, style?: import("./fx.js").TextStyle, fn?: (player: any, attacker: any) => void}} | undefined}
 */
export function guardOf(player) {
  const now = system.currentTick;
  const list = (guards.get(player.id) ?? []).filter((g) => g.until > now);
  if (!list.length) {
    guards.delete(player.id);
    return undefined;
  }
  guards.set(player.id, list);
  let reduce = 0;
  let until = now;
  let reflect;
  let any = false;
  for (const g of list) {
    if (g.cond) {
      // v2.20.0 조건이 있는 버프 (신의 방벽: 돔 안): 지금 조건이 아니면 감소 · 반사 모두 없음
      let on = false;
      try {
        on = !!g.cond(player);
      } catch {
        on = false;
      }
      if (!on) continue;
    }
    any = true;
    reduce = Math.max(reduce, g.reduce);
    until = Math.max(until, g.until);
    if (g.reflect) reflect = g.reflect;
  }
  return any ? { until, reduce, reflect } : undefined;
}

/** 버프로 반사: 때린 몹에게 피해 + 금빛 충격. reflect.fn 이 있으면 그것으로 (v1.6.0 검사 룬 반격 자세) */
function reflectHit(player, attacker, g) {
  if (!g.reflect || !attacker?.isValid || attacker.id === player.id || (attacker.typeId === PLAYER && !isDuelPartner(player, attacker))) return; // v2.26.0 결투 상대에게도
  if (g.reflect.fn) {
    try {
      g.reflect.fn(player, attacker);
    } catch (e) {
      console.warn(`[rpg] counter: ${e}`);
    }
    return;
  }
  const w = g.reflect.w;
  // 룬(강철 피부)은 정해 둔 양, 무기 공격력 × mult × 스탯 배율은 v2.19.0 까지의 무기 스킬 불멸의 방벽 (지금은 쓰는 곳 없음)
  const dmg = g.reflect.amount ?? w.damage * g.reflect.mult * powerMultiplier(player, w);
  if (!applySkillDamage(player, attacker, dmg, false, EntityDamageCause.thorns, g.reflect.style)) return;
  const at = bodyPoint(attacker);
  const m = new MolangVariableMap();
  m.setVector3("variable.dir", { x: 0, y: 0, z: 1 });
  m.setFloat("variable.speed", 0);
  m.setFloat("variable.spin", Math.random() * 360);
  m.setFloat("variable.size", 0.7);
  m.setFloat("variable.life", 0.25);
  m.setColorRGBA("variable.color", { red: 1.0, green: 0.85, blue: 0.4, alpha: 0.95 });
  spawnFx(attacker.dimension, "rpg:bash_burst", at, m);
  play(attacker.dimension, "item.shield.block", at, 1.3, 0.6);
}

/** v1.8.0: 방향이 없는 공격 (보스의 발밑 폭발·까마귀·기믹 벌): 웅크려 막기(방향 막기)가 되지 않음. 플레이어 id → 틱 */
const noBlock = new Map();
export function markNoBlock(player) {
  noBlock.set(player.id, system.currentTick);
}
function noBlockNow(player) {
  const t = noBlock.get(player.id);
  if (t === undefined) return false;
  if (system.currentTick - t > 1) {
    noBlock.delete(player.id);
    return false;
  }
  return true;
}

/** 방패 감소 비율 (0~1). 방패는 오른손(주 손)에 들었을 때만 (v1.1.7) */
function shieldReduction(player, ev) {
  const main = weaponIn(player, EquipmentSlot.Mainhand);
  const w = main?.type === "shield" ? main : undefined;
  if (!w) return 0;
  if (!meetsRequirement(player, w)) {
    if (player.isSneaking) warnLater(player, reqText(w, player));
    return 0;
  }
  const vit = getStatsCached(player).vit ?? 0;
  let r = Math.min(SHIELD.passiveCap, w.passive + vit * SHIELD.passivePerVit);
  const src = ev.damageSource;
  const from = src.damagingProjectile ?? src.damagingEntity;
  if (player.isSneaking && from && from.isValid && !bashLocked(player) && !noBlockNow(player)) {
    const dir = player.getViewDirection();
    const dx = from.location.x - player.location.x;
    const dz = from.location.z - player.location.z;
    const len = Math.hypot(dx, dz) * (Math.hypot(dir.x, dir.z) || 1);
    const dot = len > 0 ? (dx * dir.x + dz * dir.z) / len : 1;
    if (dot > 0.2) {
      r = Math.max(r, w.block);
      system.run(() => blockFx(player));
    }
  }
  return r;
}

/** 조준 보정·스킬에서 맞히지 않는 것 */
const AIM_SKIP = new Set([
  MOUNT.id, // v2.8.0 탈것 (다치지 않음)
  "minecraft:item",
  "minecraft:xp_orb",
  "minecraft:arrow",
  "minecraft:armor_stand",
  "minecraft:painting",
  "minecraft:leash_knot",
  "minecraft:fishing_hook",
  "minecraft:ender_crystal",
  "minecraft:iron_golem",
  "minecraft:snow_golem",
  "rpg:dmg_text",
]);

/** from 에서 to 까지 막는 블록이 없는지 (풀·꽃·물은 통과) */
export function lineOfSight(player, from, to) {
  const d = add(to, from, -1);
  const L = length(d);
  if (L < 0.3) return true;
  try {
    const hit = player.dimension.getBlockFromRay(from, unit(d), { maxDistance: L, includePassableBlocks: false, includeLiquidBlocks: false });
    if (!hit) return true;
    const b = hit.block.location;
    const f = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
    return Math.hypot(b.x + f.x - from.x, b.y + f.y - from.y, b.z + f.z - from.z) >= L - 0.35;
  } catch {
    return true;
  }
}

/** 서면 다치는 블록 (땅·발·머리 칸): 용암·불·모닥불·마그마·선인장·가시덤불·위더 장미·가루눈 */
const HAZARD_BLOCK = /lava|fire|campfire|magma|cactus|sweet_berry|wither_rose|powder_snow/;

/**
 * 순간이동해서 설 자리 (v1.6.0: 대상 뒤로 옮기는 기술 — 룬 배후 일격·월광 암살·그림자 난도질, 무기 스킬 섬광 도약).
 * at 의 수직선에서 위로 천장을 찾고 (낮은 굴에서 광선이 천장 블록 안에서 시작하지 않게), at 보다 1.6칸 위(천장이 낮으면 그 바로 아래)부터
 * 아래로 내려가 처음 닿는 블록이 단단한 땅이어야 하고 (물·용암·마그마·선인장 등이면 안 됨, 반 블록도 됨),
 * 그 땅이 at 높이와 maxDy 칸 안이어야 하며 (허공·낭떠러지·날고 있는 몹 아래는 안 됨), 땅 위 1.8칸에 막는 블록이 없고
 * 발·머리 칸에 불·가시덤불 같은 것이 없어야 한다 (풀·꽃은 괜찮음). 조건을 만족하면 그 땅 위 자리, 아니면 undefined (옮기지 않음)
 * @param {import("@minecraft/server").Dimension} dim
 * @param {{x: number, y: number, z: number}} at
 * @returns {{x: number, y: number, z: number} | undefined}
 */
export function standSpot(dim, at, maxDy = 1.6) {
  const UP = { x: 0, y: 1, z: 0 };
  try {
    const ceilHit = dim.getBlockFromRay({ x: at.x, y: at.y + 0.1, z: at.z }, UP, { maxDistance: 1.5, includePassableBlocks: false, includeLiquidBlocks: false });
    const ceil = ceilHit ? ceilHit.block.location.y + (ceilHit.faceLocation?.y ?? 0) : Infinity;
    const top = Math.max(at.y + 0.1, Math.min(at.y + 1.6, ceil - 0.05));
    const down = dim.getBlockFromRay({ x: at.x, y: top, z: at.z }, { x: 0, y: -1, z: 0 }, { maxDistance: top - at.y + maxDy + 0.2, includePassableBlocks: false, includeLiquidBlocks: true });
    if (!down) return undefined;
    const b = down.block;
    if (b.isLiquid || /water/.test(b.typeId ?? "") || HAZARD_BLOCK.test(b.typeId ?? "")) return undefined;
    const fy = down.faceLocation?.y;
    const y = b.location.y + (typeof fy === "number" && fy > 0.05 && fy <= 1 ? fy : 1);
    if (Math.abs(y - at.y) > maxDy) return undefined;
    const p = { x: at.x, y, z: at.z };
    if (dim.getBlockFromRay({ x: p.x, y: p.y + 0.05, z: p.z }, UP, { maxDistance: 1.75, includePassableBlocks: false, includeLiquidBlocks: true })) return undefined;
    for (const dy of [0.1, 1.1]) {
      const c = dim.getBlock({ x: p.x, y: p.y + dy, z: p.z });
      if (c && HAZARD_BLOCK.test(c.typeId ?? "")) return undefined;
    }
    return p;
  } catch {
    return undefined;
  }
}

/** 조준 보정 대상이 될 수 있는 것: 살아 있는 몹 (NPC·주민·골렘·길들인 동물·다른 플레이어 제외 — v2.26.0 결투 상대는 됨) */
export function assistable(player, e) {
  if (!e?.isValid || e.id === player.id || AIM_SKIP.has(e.typeId) || isTalkable(e)) return false;
  if (e.typeId === PLAYER && !isDuelPartner(player, e)) return false;
  try {
    if (e.getComponent("minecraft:is_tamed")) return false;
    return !!e.getComponent("minecraft:health") && !hasNoHealth(e);
  } catch {
    return false;
  }
}

/**
 * 조준 보정 (v1.2.0): 시선에 정확히 겹친 대상이 없을 때, 시선에서 deg 도 안(가까우면 옆으로 1칸 안)의 몹 중
 * 가장 가운데에 가까운 것 (벽 뒤는 제외). 모바일은 조준점이 없어 정확히 겨누기 어렵다.
 * @returns {{e: any, d: number, u: {x:number,y:number,z:number}} | undefined}
 */
export function assistTarget(player, range, deg, ok = assistable) {
  const head = player.getHeadLocation();
  const view = player.getViewDirection();
  const cosMax = Math.cos((deg * Math.PI) / 180);
  let list = [];
  try {
    list = player.dimension.getEntities({ location: head, maxDistance: range + 1.5 });
  } catch {
    list = [];
  }
  const cands = [];
  for (const e of list) {
    if (!ok(player, e)) continue;
    const p = bodyToward(e, head, view); // 키 큰 몸은 시선에 가장 가까운 곳
    const d = add(p, head, -1);
    const L = length(d);
    if (L > range + 0.5 || L < 0.3) continue;
    const c = (d.x * view.x + d.y * view.y + d.z * view.z) / L;
    if (c <= 0) continue;
    const lateral = Math.sqrt(Math.max(0, 1 - c * c)) * L;
    if (c < cosMax && lateral > 1.0 + bodyPad(e)) continue;
    cands.push({ e, p, L, d, score: (Math.acos(Math.min(1, c)) * 180) / Math.PI + L * 0.15 });
  }
  cands.sort((a, b) => a.score - b.score);
  for (const c of cands.slice(0, 6)) {
    if (lineOfSight(player, head, c.p)) return { e: c.e, d: Math.max(0.5, c.L - 0.3), u: unit(c.d) };
  }
  return undefined;
}

/**
 * 바라보는 방향으로 사거리 안의 가장 가까운 대상과, 연출이 끝나는 거리 (머리에서 잰 블록 수).
 * assist > 0 이면 시선에 겹친 대상이 없을 때 조준 보정 (dir = 그 대상을 향한 방향)
 * @returns {{target: any, dist: number, dir?: {x:number,y:number,z:number}}}
 */
export function aimTarget(player, range, assist = 0) {
  const head = player.getHeadLocation();
  let target;
  let dist = range;
  let mountId;
  try {
    mountId = player.getComponent("minecraft:riding")?.entityRidingOn?.id; // 타고 있는 말 등은 맞지 않음
  } catch {
    mountId = undefined;
  }
  try {
    const hits = player.getEntitiesFromViewDirection({
      maxDistance: range,
      excludeTypes: ["minecraft:item", "minecraft:xp_orb", "minecraft:arrow", "rpg:dmg_text", MOUNT.id], // v2.8.0: 남의 탈것도 통과 (다치지 않아 맞혀도 헛방)
      excludeTags: ["rpg_npc", "rpg_gone"], // RPG NPC·사라지는 보스 소품(v2.0.0)은 통과
      includePassableBlocks: false, // 풀·꽃·덩굴은 막지 않음
      includeLiquidBlocks: false,
    });
    let best;
    for (const h of hits) {
      const e = h.entity;
      if (!e.isValid || e.id === player.id || e.id === mountId || hasNoHealth(e)) continue;
      if (e.typeId === PLAYER && PVP.protectOthers && !isDuelPartner(player, e)) continue; // v2.26.0 결투 상대가 아닌 플레이어는 다치지 않으므로 지나감 (뒤의 몹을 맞힘)
      if (!best || h.distance < best.distance) best = h;
    }
    if (best) {
      target = best.entity;
      dist = Math.min(range, best.distance);
    }
  } catch {
    // 무시
  }
  // 막는 블록이 대상보다 앞에 있으면 거기까지만 (블록 뒤의 대상은 맞지 않음). 풀·꽃·물은 통과
  try {
    const hit = player.getBlockFromViewDirection({ maxDistance: dist, includeLiquidBlocks: false, includePassableBlocks: false });
    if (hit) {
      const b = hit.block.location;
      const f = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 }; // 블록 모서리 기준 맞은 자리
      const bd = Math.hypot(b.x + f.x - head.x, b.y + f.y - head.y, b.z + f.z - head.z);
      if (target && bd < dist - 0.25) target = undefined;
      dist = Math.min(dist, bd);
    }
  } catch {
    // 무시
  }
  if (!target && assist > 0) {
    const a = assistTarget(player, range, assist, (pl, e) => assistable(pl, e) && e.id !== mountId);
    if (a) return { target: a.e, dist: a.d, dir: a.u };
  }
  return { target, dist };
}

/** 활 빛줄기 모양 (블록 단위) */
const SHOT = {
  start: 0.6, // 나오는 자리: 머리 앞
  side: 0.28, // 오른쪽 (활을 든 손 쪽)
  drop: 0.22, // 아래
  half: 1.1, // 빛줄기 길이의 절반
  thick: 0.2, // 빛줄기 두께의 절반
  segGap: 0.5, // 지나간 자리 선 조각 간격
  segHalf: 0.3, // 선 조각 길이의 절반 (간격보다 길어 조각끼리 겹침)
  segThick: 0.08, // 선 두께의 절반
  segFade: 0.4, // 선이 가늘어지며 사라지는 시간 (초)
};

const add = (a, b, k = 1) => ({ x: a.x + b.x * k, y: a.y + b.y * k, z: a.z + b.z * k });
const cross = (a, b) => ({ x: a.y * b.z - a.z * b.y, y: a.z * b.x - a.x * b.z, z: a.x * b.y - a.y * b.x });
const length = (a) => Math.hypot(a.x, a.y, a.z);
const unit = (a) => {
  const l = length(a) || 1;
  return { x: a.x / l, y: a.y / l, z: a.z / l };
};

/** 빛줄기 경로: 활을 든 오른손 아래쪽에서 나와 조준점(머리에서 dist 블록)에 닿는다 */
export function arrowPath(head, dir, dist) {
  const r = cross(dir, { x: 0, y: 1, z: 0 });
  const right = length(r) < 1e-3 ? { x: 1, y: 0, z: 0 } : unit(r);
  // 1.5칸보다 가까우면 나오는 자리를 눈 쪽으로 당긴다 (대상이 나오는 자리보다 가까워 뒤로 쏘는 것처럼 보이지 않게)
  const k = Math.min(1, Math.max(0.15, dist / 1.5));
  const from = add(add(add(head, dir, SHOT.start * k), right, SHOT.side * k), { x: 0, y: -SHOT.drop * k, z: 0 });
  const aim = add(head, dir, dist);
  const d = add(aim, from, -1);
  const len = length(d);
  return { from, aim, dir: len > 1e-6 ? unit(d) : dir, length: len };
}

/**
 * 빛줄기 크기·속도·수명. 앞끝이 수명이 끝날 때 정확히 조준점에 닿는다.
 * 아주 가까운 대상(경로가 빛줄기보다 짧음)은 빛줄기를 줄이고 한 틱 동안 천천히 움직여 대상을 뚫고 지나가지 않게 한다.
 */
export function arrowShot(pathLength) {
  const half = Math.min(SHOT.half, pathLength * 0.35);
  const travel = Math.max(0, pathLength - 2 * half);
  const life = Math.max(0.05, travel / BOW.arrowSpeed);
  return { half, life, speed: travel / life };
}

/** 화살 파티클 변수 (등급 색) */
function arrowVars(dir, c, o) {
  const m = new MolangVariableMap();
  m.setVector3("variable.dir", dir);
  m.setFloat("variable.speed", o.speed);
  m.setFloat("variable.size", o.size);
  m.setFloat("variable.thick", o.thick ?? 0);
  m.setFloat("variable.life", o.life);
  m.setFloat("variable.delay", o.delay ?? 0);
  m.setFloat("variable.grow", o.grow ?? 0);
  m.setColorRGBA("variable.color", { red: c[0], green: c[1], blue: c[2], alpha: o.alpha });
  return m;
}

function spawnFx(dim, id, at, vars) {
  try {
    dim.spawnParticle(id, at, vars);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 화살 연출: 날아가는 빛줄기 + 빛줄기가 지나가는 순간 켜져 가늘어지며 사라지는 선 */
function arrowFx(dim, path, gradeIndex) {
  const c = BOW.color[gradeIndex] ?? BOW.color[0];
  const shot = arrowShot(path.length);
  const thick = SHOT.thick * (shot.half / SHOT.half);
  spawnFx(dim, "rpg:arrow_shot", add(path.from, path.dir, shot.half), arrowVars(path.dir, c, { speed: shot.speed, size: shot.half, thick, life: shot.life, alpha: 1 }));
  for (let s = SHOT.segGap / 2; s <= path.length - 0.1; s += SHOT.segGap) {
    const delay = shot.speed > 0 ? Math.max(0, (s - shot.half) / shot.speed) : 0; // 빛줄기 가운데가 지나가는 시간
    const vars = arrowVars(path.dir, c, { speed: 0, size: SHOT.segHalf, thick: SHOT.segThick, life: delay + SHOT.segFade, delay, alpha: 0.9 });
    spawnFx(dim, "rpg:arrow_trail", add(path.from, path.dir, s), vars);
  }
}

/** 화살이 맞은 자리: 번쩍임 1개 + 쏜 쪽으로 튀는 불꽃 8개 */
function arrowImpactFx(dim, at, dir, gradeIndex) {
  const c = BOW.color[gradeIndex] ?? BOW.color[0];
  spawnFx(dim, "rpg:arrow_spark", at, arrowVars(dir, c, { speed: 0, size: 0.45, grow: 1.6, life: 0.15, alpha: 0.95 }));
  const u = unit(cross(dir, Math.abs(dir.y) > 0.9 ? { x: 1, y: 0, z: 0 } : { x: 0, y: 1, z: 0 }));
  const v = cross(u, dir);
  for (let k = 0; k < 8; k++) {
    const a = (k * Math.PI) / 4;
    const d = unit(add(add({ x: -dir.x * 0.7, y: -dir.y * 0.7, z: -dir.z * 0.7 }, u, Math.cos(a)), v, Math.sin(a)));
    spawnFx(dim, "rpg:arrow_spark", at, arrowVars(d, c, { speed: 5, size: 0.1, life: 0.25, alpha: 0.9 }));
  }
}

/**
 * 원거리 명중: 피해·치명타 판정 후 applyDamage (완드 = 마법 피해, 활 = 화살 피해).
 * path: 활 빛줄기 경로 (활만). 완드는 대상 머리에 연출.
 */
function rangedHit(player, target, w, path) {
  if (!player.isValid || !target.isValid) return;
  const dim = target.dimension;
  let dmg = w.damage * powerMultiplier(player, w);
  const crit = Math.random() < critChanceOf(player, w, target); // v2.24.0 검신 강림 +치명타
  if (crit) dmg *= w.critMult;
  const bonus = weaponBonus(player, target, w, dmg); // v2.13.0 마검 부여, v2.24.0 평타 강화
  if (bonus) dmg += bonus.add;
  mark(target, player, w, crit, bonus);
  // 활: 실제 화살 없이 넣는 피해라 화살 피해(projectile) 대신 공격 피해로 (방어구는 그대로 적용). 완드: 마법 피해 (방어 무시)
  const cause = w.type === "bow" ? EntityDamageCause.entityAttack : EntityDamageCause.magic;
  const key = markKey(target, player);
  scripted.set(key, system.currentTick);
  let hurt = false;
  try {
    hurt = target.applyDamage(dmg, { cause, damagingEntity: player });
  } catch {
    hurt = false;
  }
  if (!hurt && absorbedHit(target, player)) hurt = true; // v1.8.0 보스
  scripted.delete(key);
  // 피해가 들어가지 않았으면 (화살에 면역인 브리즈, 무적 시간 등) 맞힘 연출 없음
  if (!hurt) {
    marks.delete(markKey(target, player));
    return;
  }
  if (path) {
    arrowImpactFx(dim, path.aim, path.dir, w.gradeIndex);
    play(dim, "random.bowhit", path.aim, 1.0, 0.7);
  } else if (target.isValid) {
    const at = target.getHeadLocation();
    particle(dim, "minecraft:critical_hit_emitter", at);
    particle(dim, WAND.trail[w.gradeIndex] ?? WAND.trail[0], at);
    play(dim, "random.orb", at, 0.6, 0.5);
  }
}

/** 손에 든 무기의 재사용 대기 표시를 시작 (터치로 쏜 경우에도 핫바에 보이고, 스크립트 대기와 맞춰짐) */
function startItemCooldown(player, w) {
  try {
    const item = player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand);
    if (item?.typeId === w.id) item.getComponent("minecraft:cooldown")?.startCooldown(player);
  } catch {
    // 무시 (표시만)
  }
}

/** 몸 가운데 (발 위치와 눈 높이 사이 3/4 지점) */
export function bodyPoint(e) {
  try {
    const l = e.location;
    const h = e.getHeadLocation();
    return { x: l.x, y: l.y + (h.y - l.y) * 0.75, z: l.z };
  } catch {
    return { x: e.location.x, y: e.location.y + 1, z: e.location.z };
  }
}

// ------------------------------------------------------------ 몸 크기 (v2.1.0: 키가 크고 넓은 보스에게도 스킬 범위가 닿게)
/** 설정에서 온 몸 크기: 엔티티 ID → {half: 가로 반폭, height: 키} (충돌 상자 × 크기 배율) @type {Map<string, {half: number, height: number}>} */
const bodies = new Map();
/** 몸 크기 등록. box = [폭, 높이] */
export function registerBody(typeId, box, scale = 1) {
  if (!box) return;
  bodies.set(typeId, { half: (box[0] * scale) / 2, height: box[1] * scale });
}
// RPG 몬스터·보스·보스 소품 (설정의 충돌 상자 × 크기 배율 = 빌드된 엔티티와 같음)
for (const [k, m] of Object.entries(MONSTERS)) registerBody(monsterId(k), m.box, m.scale);
for (const [k, b] of Object.entries(BOSSES)) {
  if (b.units) for (const u of b.units) registerBody(bossId(u.key), u.box, u.scale);
  else registerBody(bossId(k), b.box, b.scale);
  if (b.tentacles?.box) registerBody(`rpg:prop_${k}_tentacle`, b.tentacles.box, b.tentacles.scale);
}

/** 스킬 범위 판정이 생각하는 보통 몹의 가로 반폭 (범위의 +0.5칸 여유, 직선 폭 등) */
const BASE_HALF = 0.4;
/** 몸이 보통 몹보다 넓은 만큼 (큰 보스는 몸 가장자리가 범위에 걸쳐도 맞음). 등록하지 않은 몹은 0 */
export function bodyPad(e) {
  const b = bodies.get(e?.typeId);
  return b ? Math.max(0, b.half - BASE_HALF) : 0;
}
/**
 * 스킬이 겨누는 몸의 세로 범위 [아래, 위] (y 좌표).
 * 보통 몹은 몸 가운데 한 점 (bodyPoint: 예전과 같음), 키가 2칸 넘는 몸(보스 등)은 발 1.28칸 위부터 머리 0.72칸 아래까지
 * (보통 몹의 몸 가운데 높이를 키 쪽으로 늘인 세로 선분) → 땅에 선 사람의 범위 기술·가로로 나는 기술이 키 큰 몸에도 닿음
 * @returns {[number, number]}
 */
export function bodySpan(e) {
  const b = bodies.get(e.typeId);
  if (b && b.height > 2) return [e.location.y + 1.28, e.location.y + b.height - 0.72];
  const y = bodyPoint(e).y;
  return [y, y];
}
/** 높이 y 에 가장 가까운 몸 위의 점 */
export function bodyAt(e, y) {
  const [lo, hi] = bodySpan(e);
  return { x: e.location.x, y: Math.max(lo, Math.min(hi, y)), z: e.location.z };
}
/** 직선(from → 단위 방향 d)에 가장 가까운 몸 위의 점 (몸 = bodySpan 세로 선분) */
export function bodyToward(e, from, d) {
  const [lo, hi] = bodySpan(e);
  const l = e.location;
  if (hi - lo < 1e-6) return { x: l.x, y: lo, z: l.z };
  // 두 직선의 가장 가까운 점: 몸 축 (l.x, s, l.z) 의 s (세로 선분 안으로 자름)
  const wx = from.x - l.x;
  const wz = from.z - l.z;
  const dw = d.x * wx + d.y * from.y + d.z * wz;
  const D = 1 - d.y * d.y;
  const s = D > 1e-6 ? (from.y - d.y * dw) / D : (lo + hi) / 2;
  return { x: l.x, y: Math.max(lo, Math.min(hi, s)), z: l.z };
}
/**
 * c 에서 수평 단위 방향 f 쪽 반각 half(라디안) 부채꼴에 몸이 걸치는지 (near 칸 안은 방향 무관).
 * 큰 몸은 몸 폭만큼 넓게 (몸 가장자리가 부채꼴에 들면 맞음)
 */
export function bodyInCone(e, c, f, half, near = 1.2) {
  const dx = e.location.x - c.x;
  const dz = e.location.z - c.z;
  const dist = Math.hypot(dx, dz);
  const pad = bodyPad(e);
  if (dist <= near + pad) return true;
  const cos = (dx * f.x + dz * f.z) / dist;
  return Math.acos(Math.max(-1, Math.min(1, cos))) <= half + Math.asin(Math.min(1, pad / dist));
}

/** 손에 든 아이템 ID */
export function heldTypeId(player) {
  try {
    return player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand)?.typeId;
  } catch {
    return undefined;
  }
}

/** 마법진 자리: 눈앞 · 완드를 든 오른손 쪽 · 조금 아래 */
export function wandCircleAt(player) {
  const head = player.getHeadLocation();
  const dir = player.getViewDirection();
  const r = cross(dir, { x: 0, y: 1, z: 0 });
  const right = length(r) < 1e-3 ? { x: 1, y: 0, z: 0 } : unit(r);
  const c = WAND.circle;
  return { at: add(add(add(head, dir, c.dist), right, c.side), { x: 0, y: -c.drop, z: 0 }), dir, head };
}

/** 마법진 파티클 변수 (등급 색) */
function circleVars(w, o) {
  const c = WAND.color[w.gradeIndex] ?? WAND.color[0];
  const m = new MolangVariableMap();
  m.setFloat("variable.size", o.size);
  m.setFloat("variable.life", o.life);
  m.setFloat("variable.spin", o.spin);
  m.setFloat("variable.progress", o.progress ?? 1);
  m.setColorRGBA("variable.color", { red: c[0], green: c[1], blue: c[2], alpha: o.alpha });
  return m;
}

function circleSize(w) {
  return WAND.circle.size * (SLASH.gradeSize[w.gradeIndex] ?? 1);
}

/**
 * 완드 시전: 매 틱 플레이어를 따라가며 마법진이 한 단계씩 그려진다 (0.1초짜리 파티클을 틱마다 새로 띄움).
 * 다 그려지면 발사. 손에서 완드를 바꾸거나 쓰러지면 취소 (재사용 대기는 그대로).
 */
function startWandCast(player, w, forced) {
  const cast = { w, start: system.currentTick, forced, spin: Math.random() * 360 };
  casting.set(player.id, cast);
  play(player.dimension, "block.enchanting_table.use", player.getHeadLocation(), 1.3, 0.8);
  const size = circleSize(w);
  const step = () => {
    if (casting.get(player.id) !== cast) return;
    const alive = player.isValid && (player.getComponent("minecraft:health")?.currentValue ?? 1) > 0;
    if (!alive || heldTypeId(player) !== w.id) {
      casting.delete(player.id);
      return;
    }
    const t = system.currentTick - cast.start;
    if (t >= WAND.castTicks) {
      casting.delete(player.id);
      fireWand(player, w, cast);
      return;
    }
    const { at } = wandCircleAt(player);
    const vars = circleVars(w, { size, life: 0.1, spin: cast.spin + t * WAND.circle.spinPerTick, progress: (t + 1) / WAND.castTicks, alpha: WAND.circle.alpha });
    spawnFx(player.dimension, "rpg:magic_circle", at, vars);
    system.runTimeout(step, 1);
  };
  step();
}

/** 시전 완료: 완성된 마법진이 번쩍이며 퍼지고, 마법진 가운데서 대상까지 궤적 + 마법 피해 (방어구 무시) */
function fireWand(player, w, cast) {
  const dim = player.dimension;
  const { at: from, dir: view, head } = wandCircleAt(player);
  let target;
  let aim;
  const f = cast.forced;
  const fp = f && f.isValid && !hasNoHealth(f) ? bodyPoint(f) : undefined;
  if (fp && length(add(fp, head, -1)) <= WAND.range + 2) {
    // 눌러서 때린 몹이 아직 있으면 그 몹에게
    const d = add(fp, head, -1);
    target = f;
    aim = add(head, unit(d), Math.max(0.5, length(d) - 0.3));
  } else {
    // 다 그려진 순간 바라보는 대상에게 (시전 중에 조준을 옮길 수 있다). 시선 가까이의 몹은 조준 보정
    const r = aimTarget(player, WAND.range, WAND.aimAssist ?? 0);
    target = r.target;
    aim = add(head, r.dir ?? view, r.dist);
  }
  const spin = cast.spin + WAND.castTicks * WAND.circle.spinPerTick;
  spawnFx(dim, "rpg:magic_circle_flash", from, circleVars(w, { size: circleSize(w), life: WAND.circle.flashLife, spin, alpha: 1 }));
  const seg = add(aim, from, -1);
  const len = length(seg);
  const u = len > 1e-6 ? unit(seg) : view;
  const trail = WAND.trail[w.gradeIndex] ?? WAND.trail[0];
  for (let s = 0.3; s <= len; s += 0.5) particle(dim, trail, add(from, u, s));
  play(dim, "mob.evocation_illager.cast_spell", head, 1.5, 0.6);
  if (target) rangedHit(player, target, w, undefined);
}

/**
 * 완드·활 발사: 바라보는 대상(또는 forced: 눌러서 때린 몹)에 명중. 당기기·화살 아이템 없음. 재사용 대기는 종류마다 따로.
 * 완드는 바로 맞고, 활은 빛줄기(초당 BOW.arrowSpeed 블록)가 닿는 순간 맞는다 (24칸에서 약 0.35초).
 */
export function castRanged(player, w, forced = undefined) {
  if (blockedByMount(player, "attack")) return; // v2.8.0 탈것에 탄 동안 활 · 완드 평타 없음
  const cfg = w.type === "bow" ? BOW : WAND;
  const key = `${player.id}|${w.type}`;
  const now = system.currentTick;
  if ((rangedReady.get(key) ?? 0) > now) return;
  if (!meetsRequirement(player, w)) {
    warn(player, reqText(w, player));
    return;
  }
  // v2.24.0 연타 (평타 강화): 재사용 대기 × 배율 — 아이템 재사용 대기 (누르기를 막음) 도 같은 틱으로 (완드는 시전 시간보다 짧아지지 않게)
  const cd = Math.max(w.type === "wand" ? WAND.castTicks + 1 : 2, Math.round(cfg.cooldownTicks * rangedCdScale(player, w)));
  rangedReady.set(key, now + cd);
  startItemCooldown(player, w);
  if (cd < cfg.cooldownTicks) {
    try {
      player.startItemCooldown(`rpg_${w.type}`, cd);
    } catch {
      // 표시만
    }
  }
  if (w.type === "wand") {
    if (!casting.has(player.id)) startWandCast(player, w, forced);
    return;
  }

  const dim = player.dimension;
  const head = player.getHeadLocation();
  let dir;
  let target;
  let dist;
  if (forced) {
    // 눌러서 때린 몹: 그 몹의 몸 가운데(키의 3/4 높이)를 향해, 몸 표면까지
    const aim = bodyPoint(forced);
    const d = add(aim, head, -1);
    const len = length(d);
    dir = len > 1e-6 ? unit(d) : player.getViewDirection();
    target = forced;
    dist = Math.max(0.5, len - 0.3);
  } else {
    // 시선에 겹친 대상, 없으면 시선 가까이의 몹 (조준 보정) 쪽으로
    const r = aimTarget(player, cfg.range, cfg.aimAssist ?? 0);
    ({ target, dist } = r);
    dir = r.dir ?? player.getViewDirection();
  }

  // 활: 빛줄기가 날아가 닿는 순간 명중
  const path = arrowPath(head, dir, dist);
  arrowFx(dim, path, w.gradeIndex);
  play(dim, "random.bow", head, 1.1, 0.8);
  if (target) system.runTimeout(() => rangedHit(player, target, w, path), Math.max(1, Math.round(arrowShot(path.length).life * 20)));
}

/**
 * v2.26.0 결투: 플레이어끼리의 피해인지 보고 "partner" (결투 상대 — 줄여서 들어감) · "block" (막음) · "" (플레이어끼리가 아님 · 쓰러지는 중).
 * 길들인 동물 (늑대 등) 의 피해는 주인의 피해로 봄. before 이벤트 안 (읽기만)
 */
function pvpGate(ev) {
  const victim = ev.hurtEntity;
  if (victim?.typeId !== PLAYER) return "";
  const from = attackerOf(ev.damageSource);
  if (!from) return PVP.protectOthers && petOwnerId(ev.damageSource) ? "block" : ""; // 주인이 접속하지 않은 길들인 동물: 결투일 수 없으므로 막음
  if (from.id === victim.id) return "";
  if (isDying(victim)) return ""; // 사망 처리 중인 피해는 그대로 (hp.js)
  if (isDuelPartner(from, victim)) return "partner";
  return PVP.protectOthers ? "block" : "";
}

export function registerCombat() {
  registerSlashes(meetsRequirement);
  world.afterEvents.playerSwingStart.subscribe(
    (ev) => {
      if (ev.swingSource !== EntitySwingSource.Attack) return;
      if (parseWeapon(ev.heldItemStack?.typeId)?.type === "shield") lastBash.set(ev.player.id, system.currentTick);
    },
    { swingSource: EntitySwingSource.Attack },
  );
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    if (isTalkable(ev.target)) lastTalk.set(ev.player.id, system.currentTick);
  });
  world.beforeEvents.entityHurt.subscribe((ev) => {
    for (const h of hurtHooks) {
      try {
        if (h.block?.(ev)) {
          ev.cancel = true;
          return;
        }
      } catch (e) {
        console.warn(`[rpg] hurt block: ${e}`);
      }
    }
    // v2.26.0 결투: 플레이어 (화살 등을 쏜 사람 포함) 가 다른 플레이어에게 주는 피해는 결투 상대에게만 (× PVP.mult), 아니면 막음
    const duel = pvpGate(ev);
    if (duel === "block") {
      ev.cancel = true;
      return;
    }
    try {
      adjustOutgoing(ev);
    } catch (e) {
      console.warn(`[rpg] outgoing: ${e}`);
    }
    if (duel === "partner" && !ev.cancel) {
      ev.damage = duelDamage(ev.hurtEntity, ev.damage);
      notePartnerHit(ev.hurtEntity.id); // 그 뒤 잠깐의 불 · 용암 · 떨어짐은 결투 상대 탓 (pvp.js)
    }
    for (const h of hurtHooks) {
      try {
        if (!ev.cancel) h.incoming?.(ev);
      } catch (e) {
        console.warn(`[rpg] hurt incoming: ${e}`);
      }
    }
    for (const h of hurtHooks) {
      try {
        if (!ev.cancel && h.absorb?.(ev)) {
          ev.cancel = true;
          return;
        }
      } catch (e) {
        console.warn(`[rpg] hurt absorb: ${e}`);
      }
    }
    if (ev.cancel || ev.hurtEntity.typeId !== PLAYER) return;
    try {
      const g = guardOf(ev.hurtEntity);
      const shield = shieldReduction(ev.hurtEntity, ev);
      onPlayerHurtBefore(ev, g ? 1 - (1 - shield) * (1 - g.reduce) : shield);
    } catch (e) {
      console.warn(`[rpg] incoming: ${e}`);
    }
  });

  world.beforeEvents.entityHeal.subscribe((ev) => {
    if (ev.healedEntity.typeId !== PLAYER) return;
    try {
      onPlayerHealBefore(ev);
    } catch (e) {
      console.warn(`[rpg] heal: ${e}`);
    }
  });

  world.afterEvents.entityHurt.subscribe((ev) => {
    const victim = ev.hurtEntity;
    hurtTicks.set(victim.id, system.currentTick); // v2.16.0
    let shown = ev.damage;
    let style = "normal";
    if (victim.typeId === PLAYER) {
      shown = onPlayerHurtAfter(ev);
      style = "player";
      // 반사 버프 (룬 강철 피부 등 — v2.19.0 까지 불멸의 방벽도): 때린 몹에게 반사 (몹의 공격·화살 모두, 쏜 몹에게)
      const g = guardOf(victim);
      const from = ev.damageSource.damagingEntity;
      // v2.26.0 결투 상대에게도 (반사 피해 (thorns) 에는 다시 반사하지 않음 — 둘 다 반사 버프면 서로 주고받지 않게)
      if (g?.reflect && from && from.isValid && (from.typeId !== PLAYER || (isDuelPartner(victim, from) && ev.damageSource.cause !== EntityDamageCause.thorns))) {
        const pl = /** @type {any} */ (victim);
        system.run(() => reflectHit(pl, from, g));
      }
    }
    const attacker = ev.damageSource.damagingEntity;
    if (!attacker || attacker.typeId !== PLAYER || attacker.id === victim.id) return;
    hitFeedback(victim, attacker, shown, ev.damageSource.cause, 0, style);
  });

}

/**
 * 플레이어가 준 피해의 연출: 스킬이면 스킬 색 숫자, 평타면 치명타·참격 + 숫자 (after 이벤트, v1.8.0 보스는 스크립트가 줄인 뒤)
 * @param {any} victim
 * @param {any} attacker 플레이어
 * @param {number} shown 보이는 피해
 * @param {string} cause 피해 원인
 * @param {number} [slack] 표시와 피해 사이 늦어진 틱 (보스는 1)
 * @param {string} [style0] 기본 숫자 모양
 * @param {string} [mark] 숫자 앞 표시 (v2.3.0 보스 근접 보너스)
 */
export function hitFeedback(victim, attacker, shown, cause, slack = 0, style0 = "normal", mark = "") {
  let style = style0;
  const key = markKey(victim, attacker);
  // 스킬 피해: 스킬 색 숫자 (치명타는 따로), 참격 연출은 스킬이 직접 한다
  const sm = skillMarks.get(key);
  if (sm && system.currentTick - sm.tick <= 1 + slack) {
    skillMarks.delete(key);
    if (shown > 0) showDamageText(victim, shown, sm.style ?? (sm.crit ? "skillcrit" : "skill"), mark, attacker);
    return;
  }
  const m = marks.get(key);
  marks.delete(key);
  // v2.13.0 평타에 더한 피해 (마검 부여): 숫자를 평타 몫과 더한 몫으로 나눔 (방어·보스 배율은 같은 비율로)
  let extra = 0;
  // 피해가 실제로 들어간 공격에만 치명타·참격 연출
  if (m && system.currentTick - m.tick <= 2 + slack) {
    if (m.bonus && m.bonus.total > 0 && shown > 0) {
      extra = shown * Math.min(1, m.bonus.add / m.bonus.total);
      shown -= extra;
    }
    if (m.bonus) bonusLanded.set(key, m.tick); // v2.24.0 무적 시간 동안 더하지 않음 — 맞은 틱부터 (보스는 피해 알림이 한 틱 늦음)
    for (const h of weaponHitHooks) {
      try {
        h(attacker, victim, m.w, m.bonus);
      } catch (e) {
        console.warn(`[rpg] weapon hit hook: ${e}`);
      }
    }
    if (m.crit) {
      style = "crit";
      critFx(victim);
    }
    if (MELEE.has(m.w.type) && cause === EntityDamageCause.entityAttack) {
      try {
        slashHit(/** @type {any} */ (attacker), victim, m.w, m.crit);
      } catch (e) {
        console.warn(`[rpg] slash hit: ${e}`);
      }
    }
  }
  if (shown > 0) showDamageText(victim, shown, /** @type {any} */ (style), mark, attacker);
  if (extra > 0) {
    // 평타 숫자 위에: 더한 것마다 (v2.24.0 — 마검 부여 = 보라 "마검", 평타 강화 = 노랑 "강화"), 숫자 모양이 같은 것끼리 합침
    const parts = m?.bonus?.parts ?? [];
    /** @type {Map<string, {add: number, mark: string}>} */
    const by = new Map();
    for (const p of parts) {
      const st = p.style ?? "spell";
      const g = by.get(st) ?? { add: 0, mark: "" };
      g.add += p.add;
      if (!g.mark.includes(p.mark ?? "")) g.mark += p.mark ?? "";
      by.set(st, g);
    }
    const total = [...by.values()].reduce((a, g) => a + g.add, 0);
    if (by.size <= 1 || !(total > 0)) showDamageText(victim, extra, /** @type {any} */ ([...by.keys()][0] ?? "spell"), m?.bonus?.mark ?? "", attacker, 0.45);
    else {
      let lift = 0.45;
      for (const [st, g] of by) {
        showDamageText(victim, (extra * g.add) / total, /** @type {any} */ (st), g.mark, attacker, lift);
        lift += 0.4;
      }
    }
  }
}

/** 완드·활 사용. 사용한 아이템이 완드나 활이면 true. */
export function tryUseRanged(player, itemStack) {
  const w = parseWeapon(itemStack?.typeId);
  if (!w || (w.type !== "wand" && w.type !== "bow")) return false;
  // NPC·주민을 터치한 같은 터치로 온 사용은 무시 (대화·거래만)
  if (system.currentTick - (lastTalk.get(player.id) ?? -100) <= 2) return true;
  heldEnhance(player, w); // 쏠 때의 강화 단계를 기록 (날아가는 중에 무기를 바꿔도 이 단계로 맞음)
  castRanged(player, w);
  return true;
}

/** 오래된 치명타 표시를 지운다 (주기적으로 호출). */
export function cleanupMarks() {
  const now = system.currentTick;
  for (const [id, m] of marks) if (now - m.tick > 40) marks.delete(id);
  for (const [id, m] of skillMarks) if (now - m.tick > 40) skillMarks.delete(id);
  for (const [id, t] of scripted) if (now - t > 40) scripted.delete(id);
  for (const [id, t] of skillLanded) if (now - t > 40) skillLanded.delete(id);
  for (const [id, t] of hurtTicks) if (now - t > 40) hurtTicks.delete(id);
  for (const [id, t] of bonusLanded) if (now - t > 40) bonusLanded.delete(id);
  for (const [id, list] of guards) {
    const left = list.filter((g) => g.until > now);
    if (left.length) guards.set(id, left);
    else guards.delete(id);
  }
}

export function forgetCombat(playerId) {
  steadfast.delete(playerId);
  for (const key of rangedReady.keys()) if (key.startsWith(`${playerId}|`)) rangedReady.delete(key);
  lastTalk.delete(playerId);
  lastBash.delete(playerId);
  casting.delete(playerId);
  guards.delete(playerId);
  recentEnhance.delete(playerId);
}
