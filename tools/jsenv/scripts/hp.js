import { Difficulty, EntityDamageCause, EquipmentSlot, GameMode, system, world } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { ensureStats, getStatsCached, reqLevelOf } from "./stats.js";

/**
 * 가상 체력(RPG 체력) 시스템
 * - 바닐라 체력(20)은 그대로 두고, 실제 체력은 이 모듈이 관리한다.
 * - 받은 바닐라 피해(방어구 적용 후) × DAMAGE_TAKEN_SCALE 만큼 RPG 체력이 깎인다 (기본 1 = 바닐라 단위).
 * - 바닐라 체력은 RPG 체력 비율을 따라가도록 맞춘다(2~20). 그래서 자연 회복·물약은
 *   바닐라처럼 동작하고, 회복량은 최대 체력 비율로 바뀐다.
 * - 큰 피해는 바닐라 체력이 0이 되지 않게 줄여서 통과시키고, 실제로 들어간 양(after 이벤트)으로
 *   RPG 체력을 계산한다. RPG 체력이 0이 되면 같은 원인으로 사망시킨다.
 * - 독·배고픔(어려움 제외)처럼 바닐라에서 죽지 않는 피해는 RPG 체력 1에서 멈춘다.
 * - 화면 표시는 제목(title) 채널로 "hpbar:" 문자열을 보내고, 리소스 팩 UI가 숨겨서 읽는다.
 *   v2.6.3: "hpbar:" + 체력 % 3자리 + 방어 수치 2자리 (갑옷 칸) + 레벨 3자리 (레벨 칸 — 이 애드온의 레벨) + 글자. 배고픔은 늘 가득 (feedTick)
 *   v2.6.6: 두 숫자 칸은 "§f" + 숫자 (자리가 남으면 뒤에 빈칸) — 예 "hpbar:062§f8 §f32 §l12.5§r§7 / 20" (체력 글자는 20바이트부터).
 *   게임의 화면 식은 숫자로 시작하는 글자에 빼기 (-) 를 하면 숫자로 바꿔 계산하고, 숫자는 글자 칸에 나오지 않음 → 화면은 "§f" 로
 *   시작하는 조각을 그대로 보여 주고 (§f 는 보이지 않는 흰색 코드), 숫자로 시작하는 글자에는 빼기를 하지 않음 (v2.6.3~2.6.5 숫자가 안 나온 까닭)
 *   (다른 칸 (길 안내) 을 보낸 뒤 HP_TAIL 틱 안에 체력 바를 한 번 더 보내 마지막 제목이 체력 바가 되게 함 — 화면이 새로 만들어져도 곧 잡게.
 *   v2.6.5: 갑옷 칸 · 레벨 칸은 체력 칸 (화면 맨 위층) 에 붙음 — 숫자가 안 나온 진짜 원인은 아래 v2.6.6 의 형 변환)
 *   v2.6.1: 길 안내 화살표 ("rpgnav:") 도 같은 채널 (setHudExtra). 같은 틱에 제목 두 개를 보내면 화면이 하나만 읽을 수 있어서
 *   2틱마다 하나씩 (둘 다 기다리면 번갈아) 보낸다.
 */

const MIRROR_MAX = 20;
const HUD_KEY = "hpbar:";
const SAVE_KEY = "rpg:hp_ratio"; // 남은 체력 비율(0~1)로 저장: 체력 단위나 스탯이 바뀌어도 맞음
const OLD_SAVE_KEY = "rpg:hp"; // v1.1.1 까지: 체력 값 (그때 최대 체력 = 100 + 체력 스탯 × 10)
const TOTEM = "minecraft:totem_of_undying";
const MATCH_TICKS = 2; // before·after 이벤트를 짝지을 때 허용하는 틱 차이
const HURT_COOLDOWN = 10; // 바닐라 무적 시간(틱). 이 안에 들어온 피해로는 방어 비율을 배우지 않는다.

/**
 * @typedef {{pre:number, clamp?:number, r:number, tick:number, cause:string, from:string, safe:boolean}} Pending
 * @typedef {{hp:number, max:number, dead:boolean, deadTick:number, awaitRespawn:boolean,
 *   kBy:Record<string, number>, queue:Pending[], lastHurt:number,
 *   lastHud:string, lastSent:number, pauseUntil:number, saved:number, mode?:string, forceHud:boolean, lastKind?:string}} HpState
 */
/** @type {Map<string, HpState>} */
const states = new Map();

/** 최대 체력 = 기본 + 레벨 보너스(경험치 레벨 최고 기록) + 체력 스탯 */
export function maxHpOf(player) {
  const s = getStatsCached(player);
  return CONFIG.BASE_HP + levelHpOf(player) + s.vit * CONFIG.HP_PER_VIT;
}

/** 최대 체력 중 레벨 보너스 */
export function levelHpOf(player) {
  return (getStatsCached(player).lvmax ?? 0) * (CONFIG.HP_PER_LEVEL ?? 0);
}

/** @returns {HpState} */
export function hpState(player) {
  let s = states.get(player.id);
  if (!s) {
    ensureStats(player); // 스탯을 읽기 전에 만들면 최대 체력이 낮게 잡힌다
    const max = maxHpOf(player);
    let ratio = Number(player.getDynamicProperty(SAVE_KEY));
    let migrated = false;
    if (!(ratio > 0 && ratio <= 1)) {
      // 이전 버전 저장값을 비율로 바꾼다
      const old = Number(player.getDynamicProperty(OLD_SAVE_KEY));
      const oldMax = 100 + getStatsCached(player).vit * 10;
      ratio = Number.isFinite(old) && old > 0 ? Math.min(1, old / oldMax) : 1;
      migrated = Number.isFinite(old) && old > 0;
    }
    s = {
      hp: ratio * max,
      max,
      dead: false,
      deadTick: 0,
      awaitRespawn: false,
      kBy: {},
      queue: [],
      lastHurt: -100,
      lastHud: "",
      lastSent: -1000,
      pauseUntil: 0,
      saved: migrated ? -1 : ratio,
      forceHud: true,
    };
    states.set(player.id, s);
  }
  return s;
}

/** 체력이 0이 되어 사망 처리 중인지 */
/** 여러 줄 이름표를 지금 상태로 다시 맞추는 함수 (v1.7.0: monsters.js 가 등록). 맞췄으면 true */
let nameRefresh = (/** @type {any} */ _e) => false;
/** @param {((e: any) => boolean) | undefined} fn */
export function setNameRefresh(fn) {
  nameRefresh = fn ?? (() => false);
}

export function isDying(player) {
  return states.get(player.id)?.dead === true;
}

export function forgetHp(playerId) {
  states.delete(playerId);
  shields.delete(playerId);
  extras.delete(playerId);
}

export function isRpgMode(player) {
  try {
    const m = player.getGameMode();
    return m === GameMode.Survival || m === GameMode.Adventure;
  } catch {
    return false;
  }
}

function mirrorOf(s) {
  if (s.dead) return 0;
  if (s.hp >= s.max) return MIRROR_MAX;
  return Math.min(MIRROR_MAX - 1, Math.max(2, Math.floor((MIRROR_MAX * s.hp) / s.max)));
}

/** 짝을 찾지 못한 오래된 대기 피해를 지운다 (바닐라가 무시한 피해). */
function prune(s, now) {
  while (s.queue.length && now - s.queue[0].tick > MATCH_TICKS) s.queue.shift();
}

/** 바닐라 체력을 RPG 체력 비율에 맞춘다 (제한 없는 실행 문맥에서만 호출). */
export function syncVanilla(player) {
  const s = hpState(player);
  prune(s, system.currentTick);
  if (s.dead || s.queue.length) return;
  try {
    const h = player.getComponent("minecraft:health");
    if (!h) return;
    const target = mirrorOf(s);
    if (target > 0 && Math.abs(h.currentValue - target) > 0.01) h.setCurrentValue(target);
  } catch {
    // 플레이어가 나갔거나 아직 로딩 중
  }
}

function scheduleSync(player) {
  system.run(() => {
    if (player.isValid) syncVanilla(player);
  });
}

function entityId(entity) {
  try {
    return entity?.id ?? "";
  } catch {
    return "";
  }
}

/** 바닐라에서 죽지 않는 피해인지: 독(마법 피해 1), 배고픔(어려움 난이도 제외). 읽기만 한다. */
function isNonLethal(player, cause, damage) {
  try {
    if (cause === EntityDamageCause.starve) return world.getDifficulty() !== Difficulty.Hard;
    if (cause === EntityDamageCause.magic && damage <= 1.01) return player.getEffect("minecraft:poison") !== undefined;
  } catch {
    // 무시
  }
  return false;
}

/**
 * RPG 체력을 깎는다. 0이 되면 사망 처리.
 * 세계를 바꾸는 호출은 모두 미루므로 before 이벤트 안에서도 부를 수 있다.
 */
/** 쓰러지지 않는 상태인지 (v1.3.0 룬 불사의 맹약). runes.js 가 setUndying 으로 넣는다 */
let undyingCheck = (_player) => false;
let undyingSaved = (_player) => {};
export function setUndying(check, onSave) {
  undyingCheck = check;
  undyingSaved = onSave;
}

/**
 * v2.26.0 결투: 이 피해로는 쓰러지지 않는지 (결투 상대가 준 피해 — PVP.lethal 이 아니면 체력 1로 남고 짐) · 졌을 때 (다음 틱에).
 * pvp.js 가 setDuelHooks 로 넣는다. floor 는 before 이벤트 안에서도 부르므로 읽기만
 */
let duelFloor = (_player, _source) => false;
let duelLost = (_player, _source) => {};
export function setDuelHooks(floor, lost) {
  duelFloor = floor;
  duelLost = lost;
}

/**
 * 보호막 (v1.4.0 성기사 룬): 플레이어 id → 남은 양·끝나는 틱. 받는 피해를 체력보다 먼저 흡수 (독·배고픔처럼 죽지 않는 피해는 흡수하지 않음)
 * @type {Map<string, {amount: number, until: number}>}
 */
const shields = new Map();
let shieldBroken = (_player) => {};
let shieldAbsorbed = (_player, _amount) => {};
/** runes.js 가 깨질 때·흡수할 때 연출을 넣는다 */
export function setShieldHooks(onBreak, onAbsorb) {
  shieldBroken = onBreak;
  shieldAbsorbed = onAbsorb;
}

/** 보호막 더하기 (양은 cap 까지만, 지속 시간은 새 ticks 와 남은 시간 중 긴 쪽). 실제로 늘어난 양 */
export function addShield(player, amount, ticks, cap = Infinity) {
  const now = system.currentTick;
  const cur = shields.get(player.id);
  const live = cur && cur.until > now ? cur : undefined;
  const have = live ? live.amount : 0;
  const next = Math.max(have, Math.min(cap, have + Math.max(0, amount)));
  shields.set(player.id, { amount: next, until: Math.max(live ? live.until : 0, now + ticks) });
  return next - have;
}

/** 남은 보호막 (없으면 0) */
export function shieldOf(player) {
  const sh = shields.get(player.id);
  return sh && sh.until > system.currentTick ? sh.amount : 0;
}

/** 보호막이 흡수하고 남은 피해 */
function absorbShield(player, loss) {
  const sh = shields.get(player.id);
  if (!sh) return loss;
  if (sh.until <= system.currentTick) {
    shields.delete(player.id);
    return loss;
  }
  const used = Math.min(sh.amount, loss);
  sh.amount -= used;
  if (sh.amount <= 1e-6) {
    shields.delete(player.id);
    system.run(() => {
      if (player.isValid) shieldBroken(player);
    });
  } else if (used > 0) {
    system.run(() => {
      if (player.isValid) shieldAbsorbed(player, used);
    });
  }
  return loss - used;
}

function takeLoss(player, s, loss, safe, source) {
  if (loss > 0 && !safe) loss = absorbShield(player, loss);
  if (loss > 0) {
    let next = s.hp - loss;
    if (safe && next < 1) next = Math.min(s.hp, 1);
    if (next < 1 && undyingCheck(player)) {
      next = Math.min(s.hp, 1); // 체력 1에서 버팀
      system.run(() => {
        if (player.isValid) undyingSaved(player);
      });
    } else if (next <= 0 && duelFloor(player, source)) {
      next = Math.min(s.hp, 1); // v2.26.0 결투 상대에게 쓰러짐 (죽을 피해): 죽지 않고 체력 1 (그보다 적었으면 그대로) 로 남음 → 결투에서 짐
      system.run(() => {
        if (player.isValid) duelLost(player, source);
      });
    }
    if (next <= 0) {
      s.hp = 0;
      s.dead = true;
      s.deadTick = system.currentTick;
      s.queue.length = 0;
      killPlayer(player, source);
      return;
    }
    s.hp = next;
  }
  scheduleSync(player);
}

/**
 * before 이벤트: 플레이어가 피해를 받기 직전.
 * @param {import("@minecraft/server").EntityHurtBeforeEvent} ev
 * @param {number} reduction 방패 등으로 줄어드는 비율 (0~1)
 */
export function onPlayerHurtBefore(ev, reduction) {
  const player = /** @type {import("@minecraft/server").Player} */ (ev.hurtEntity);
  if (!isRpgMode(player)) return;
  const s = hpState(player);
  if (s.dead) return; // 사망 처리 중인 피해는 그대로 통과
  const pre = ev.damage;
  if (!(pre > 0)) return;
  const src = ev.damageSource;
  const cause = String(src.cause);
  const now = system.currentTick;
  prune(s, now);
  const health = player.getComponent("minecraft:health");
  const vanilla = health ? health.currentValue : MIRROR_MAX;
  /** @type {Pending} */
  const p = { pre, r: reduction, tick: now, cause, from: entityId(src.damagingEntity), safe: isNonLethal(player, cause, pre) };

  if (pre >= vanilla - 1) {
    if (vanilla > 1.5) {
      // 바닐라 체력이 0이 되지 않도록 피해를 줄인다. 실제 RPG 피해는 after 이벤트에서 계산.
      ev.damage = vanilla - 1;
      p.clamp = vanilla - 1;
    } else {
      // 바닐라 체력이 거의 없으면 피해를 취소하고 RPG 체력만 깎는다 (방어 비율은 기억한 값).
      ev.cancel = true;
      const k = s.kBy[cause] ?? 1;
      takeLoss(player, s, pre * k * CONFIG.DAMAGE_TAKEN_SCALE * (1 - reduction), p.safe, src);
      return;
    }
  }
  s.queue.push(p);
  // after 이벤트가 오지 않으면(무적 시간 등) 대기를 지우고 바닐라 체력을 다시 맞춘다
  system.runTimeout(() => {
    if (player.isValid) syncVanilla(player);
  }, MATCH_TICKS + 1);
}

/**
 * after 이벤트: 플레이어가 실제로 피해를 받은 뒤. RPG 체력을 깎고 깎인 양을 돌려준다.
 * @param {import("@minecraft/server").EntityHurtAfterEvent} ev
 */
export function onPlayerHurtAfter(ev) {
  const player = /** @type {import("@minecraft/server").Player} */ (ev.hurtEntity);
  if (!isRpgMode(player)) return 0;
  const s = hpState(player);
  if (s.dead) return 0;
  const actual = ev.damage;
  const src = ev.damageSource;
  const cause = String(src.cause);
  const from = entityId(src.damagingEntity);
  const now = system.currentTick;
  const i = s.queue.findIndex((q) => q.cause === cause && q.from === from && now - q.tick <= MATCH_TICKS);
  let loss;
  let safe;
  if (i >= 0) {
    const p = s.queue[i];
    s.queue.splice(0, i + 1); // 짝이 없는 더 오래된 대기는 바닐라가 무시한 피해
    const base = p.clamp ?? p.pre;
    const k = base > 0 ? Math.min(1, Math.max(0, actual / base)) : 1;
    // 방어구·효과로 줄어드는 비율을 원인별로 기억 (무적 시간 중에는 차이만 들어오므로 제외)
    if (p.clamp === undefined && base >= 1 && actual > 0 && now - s.lastHurt > HURT_COOLDOWN) s.kBy[cause] = k;
    loss = p.pre * k * CONFIG.DAMAGE_TAKEN_SCALE * (1 - p.r);
    safe = p.safe;
  } else {
    loss = actual * CONFIG.DAMAGE_TAKEN_SCALE;
    safe = isNonLethal(player, cause, actual);
  }
  s.lastHurt = now;
  takeLoss(player, s, loss, safe, src);
  return Math.max(0, loss);
}

function totemCount(player) {
  let n = 0;
  try {
    const eq = player.getComponent("minecraft:equippable");
    for (const slot of [EquipmentSlot.Mainhand, EquipmentSlot.Offhand]) {
      const it = eq?.getEquipment(slot);
      if (it && it.typeId === TOTEM) n += it.amount;
    }
  } catch {
    // 무시
  }
  return n;
}

function revive(player, s, hp) {
  s.dead = false;
  s.awaitRespawn = false;
  s.queue.length = 0;
  s.hp = Math.max(1, Math.min(s.max, Math.round(hp)));
  s.forceHud = true;
  scheduleSync(player);
}

/**
 * RPG 체력이 0이 된 플레이어를 같은 원인으로 사망시킨다.
 * 불사의 토템이 쓰였으면 최대 체력의 10%로 되살린다.
 */
function killPlayer(player, source) {
  const s = hpState(player);
  const tick = s.deadTick;
  system.run(() => {
    if (!player.isValid) return;
    const totems = totemCount(player);
    try {
      const attacker = source?.damagingEntity?.isValid ? source.damagingEntity : undefined;
      const projectile = source?.damagingProjectile?.isValid ? source.damagingProjectile : undefined;
      // v1.7.0: 여러 줄 이름표(몬스터 체력 막대)는 사망 메시지에 첫 줄(Lv·이름)만 나오게 잠깐 줄였다가 되돌림
      const tag = attacker?.nameTag;
      const cut = typeof tag === "string" && tag.includes("\n") ? tag.split("\n")[0] : undefined;
      if (attacker && cut !== undefined) {
        attacker.nameTag = cut;
        system.runTimeout(() => {
          try {
            // 몬스터는 지금 체력으로 다시 맞추고 (그 사이 체력이 바뀌었을 수 있음), 아니면 원래 이름으로
            if (attacker.isValid && attacker.nameTag === cut && !nameRefresh(attacker)) attacker.nameTag = tag;
          } catch {
            // 무시
          }
        }, 2);
      }
      if (projectile) player.applyDamage(100000, { damagingProjectile: projectile, damagingEntity: attacker });
      else player.applyDamage(100000, { cause: source?.cause ?? EntityDamageCause.override, damagingEntity: attacker });
    } catch {
      // 원인을 재현할 수 없는 피해는 아래에서 처리
    }
    system.runTimeout(() => {
      if (!player.isValid || !s.dead || s.deadTick !== tick || s.awaitRespawn) return;
      try {
        const h = player.getComponent("minecraft:health");
        if (!h || h.currentValue <= 0) return; // 정상적으로 사망
        if (totemCount(player) < totems) {
          revive(player, s, s.max * 0.1); // 불사의 토템이 막음
          return;
        }
        player.kill();
      } catch {
        // 무시
      }
    }, 2);
  });
}

/**
 * 즉사 (v1.8.0 보스 기믹): 보호막·불사의 맹약을 무시하고 RPG 체력을 0으로 → 원인 = attacker(보스)로 사망 처리 (불사의 토템은 바닐라처럼 막음)
 * @param {import("@minecraft/server").Player} player
 * @param {import("@minecraft/server").Entity | undefined} attacker
 * @returns {boolean} 처리했는지 (이미 쓰러지는 중이면 false)
 */
export function instantKill(player, attacker) {
  const s = hpState(player);
  if (s.dead || !player.isValid) return false;
  if (!isRpgMode(player)) {
    try {
      player.applyDamage(100000, { cause: EntityDamageCause.magic, damagingEntity: attacker });
    } catch {
      // 무시
    }
    return true;
  }
  s.hp = 0;
  s.dead = true;
  s.deadTick = system.currentTick;
  s.queue.length = 0;
  killPlayer(player, { cause: EntityDamageCause.magic, damagingEntity: attacker });
  scheduleSync(player);
  return true;
}

/**
 * before 이벤트: 플레이어가 회복되기 직전. 바닐라 회복량을 최대 체력 비율로 바꿔 더한다.
 * @param {import("@minecraft/server").EntityHealBeforeEvent} ev
 */
export function onPlayerHealBefore(ev) {
  const player = /** @type {import("@minecraft/server").Player} */ (ev.healedEntity);
  if (!isRpgMode(player)) return;
  const s = hpState(player);
  // 불사의 토템 (원인 값이 있는 버전). 없는 버전은 killPlayer 의 토템 확인이 처리한다.
  if (String(ev.healSource?.cause) === "TotemOfUndying") {
    revive(player, s, s.max * 0.1);
    return;
  }
  if (s.dead || !(ev.healing > 0)) return;
  s.hp = Math.min(s.max, s.hp + (ev.healing * s.max) / MIRROR_MAX);
  scheduleSync(player);
}

export function onPlayerDied(player) {
  shields.delete(player.id);
  const s = hpState(player);
  s.deadTick = system.currentTick;
  s.dead = true;
  s.awaitRespawn = true;
  s.hp = 0;
  s.queue.length = 0;
}

/** 사망 처리했는데 살아 있는 경우(죽이기 실패 등)를 되돌린다. 20틱마다 호출. */
export function checkStuck(player) {
  const s = hpState(player);
  if (!s.dead || s.awaitRespawn || system.currentTick - s.deadTick < 40) return;
  try {
    const h = player.getComponent("minecraft:health");
    if (!h || h.currentValue <= 0) return; // 실제로 사망한 상태
  } catch {
    return;
  }
  revive(player, s, s.max * 0.1);
}

export function onPlayerRespawn(player) {
  const s = hpState(player);
  s.max = maxHpOf(player);
  s.hp = s.max;
  s.dead = false;
  s.awaitRespawn = false;
  s.queue.length = 0;
  s.kBy = {};
  forceHud(player); // 체력 바와 길 안내 칸 (죽음 화면 동안 놓쳤을 수 있음)
  syncVanilla(player);
}

/** 최대 체력이 바뀌었을 때 호출 (체력 스탯 변경 등). */
/**
 * 최대 체력을 다시 계산한다.
 * keepRatio = false (스탯·레벨): 늘어난 만큼 현재 체력도 늘림
 * keepRatio = true (갑옷 입기·벗기, v1.5.0): 남은 비율을 유지 (벗었다 입었다 해서 체력이 차지 않게)
 */
export function refreshMaxHp(player, keepRatio = false) {
  const s = hpState(player);
  const nm = maxHpOf(player);
  if (nm === s.max) return;
  const diff = nm - s.max;
  const ratio = s.max > 0 ? s.hp / s.max : 1;
  s.max = nm;
  if (!s.dead) s.hp = keepRatio ? Math.min(nm, Math.max(Math.min(1, s.hp), ratio * nm)) : Math.min(nm, Math.max(1, s.hp + Math.max(0, diff)));
  scheduleSync(player);
}

/** 체력을 amount 만큼 회복 (최대 체력까지). 실제로 오른 양을 돌려준다 (v1.2.0 스킬 흡혈) */
/**
 * v2.16.0 티르빙 체력 대가 스킬 (저주 해방 → v2.23.0 망자의 각성): 체력을 바침 — 피해가 아니라 보호막 · 방어 · 불사의 맹약과 상관없음. 바친 뒤 1 이상 남을 때만 바치고 그 양을 돌려줌 (못 바치면 0).
 * 생존 · 모험 모드만 (크리에이티브 · 관전은 RPG 체력이 깎이지 않으므로 0)
 */
export function payHp(player, amount) {
  if (!isRpgMode(player) || !(amount > 0)) return 0;
  const s = hpState(player);
  if (s.dead || s.hp - amount < 1 - 1e-6) return 0; // 소수 오차는 봐줌 (16.4 - 15.4 = 0.99999…)
  s.hp = Math.max(1, s.hp - amount);
  scheduleSync(player);
  return amount;
}

export function healBy(player, amount) {
  const s = hpState(player);
  if (s.dead || !(amount > 0)) return 0;
  const before = s.hp;
  s.hp = Math.min(s.max, s.hp + amount);
  if (s.hp !== before) scheduleSync(player);
  return s.hp - before;
}

export function healFull(player) {
  const s = hpState(player);
  if (s.dead) return;
  s.hp = s.max;
  syncVanilla(player);
}

// ------------------------------------------------------------ 화면 표시

/** 표시용 현재 체력: 소수 첫째 자리까지, 살아 있으면 최소 0.1 */
export function hpNumber(s) {
  if (s.dead || !(s.hp > 0)) return "0";
  return String(Math.max(0.1, Math.round(s.hp * 10) / 10));
}

/**
 * 입은 갑옷의 방어 수치 (바닐라 방어 포인트 합 — 이 애드온 갑옷도 그대로, 0~99). 갑옷 칸 (v2.6.3)
 * @param {import("@minecraft/server").Player} player
 */
export function armorPoints(player) {
  let v = 0;
  try {
    v = Number(player.getComponent("minecraft:equippable")?.totalArmor) || 0;
  } catch {
    v = 0;
  }
  return Math.max(0, Math.min(99, Math.round(v)));
}

/**
 * 레벨 칸 (v2.6.3): 이 애드온의 레벨 = 최고 경험치 레벨 (무기 · 갑옷 · 퀘스트 조건 · 최대 체력이 쓰는 reqLevelOf).
 * 죽거나 경험치를 써서 바닐라 레벨이 내려가도 그대로. 0~999
 * @param {import("@minecraft/server").Player} player
 */
export function hudLevel(player) {
  let v = 0;
  try {
    v = Number(reqLevelOf(player)) || 0;
  } catch {
    v = 0;
  }
  return Math.max(0, Math.min(999, Math.floor(v)));
}

/**
 * 체력 바 문자열 (v2.6.6): "hpbar:" + 백분율 3자리 (6~8바이트) + "§f" + 방어 수치 2자리 (9~13, 갑옷 칸) + "§f" + 레벨 3자리 (14~19, 레벨 칸)
 * + 표시 글자 (20바이트부터 — 현재 체력은 굵게, 최대 체력은 회색). 리소스 팩은 앞에서부터 바이트 수로 자름 ("§" 는 2바이트).
 * 숫자 자리가 남으면 뒤에 빈칸 (글자 칸이 왼쪽에 붙어 있어 보이지 않음). 화면 식은 숫자로 시작하는 글자에 빼기를 하지 않음
 * (게임이 숫자로 바꿔 글자 칸이 비게 됨) — 숫자 칸 조각은 "§f" 로 시작
 */
function hudText(s, armor = 0, level = 0) {
  const ratio = s.max > 0 ? s.hp / s.max : 0;
  let p = Math.max(0, Math.min(100, Math.floor(ratio * 100)));
  if (s.hp > 0 && p === 0) p = 1;
  return `${HUD_KEY}${String(p).padStart(3, "0")}§f${String(armor).padEnd(2, " ")}§f${String(level).padEnd(3, " ")}§l${hpNumber(s)}§r§7 / ${s.max}`;
}

let feedWarned = false; // 배고픔 속성을 읽지 못했다는 알림은 한 번만

/**
 * v2.6.3 배고픔 없음: 배고픔·포화를 가득 (20틱마다·접속·다시 살아날 때). CONFIG.NO_HUNGER 가 false 면 아무것도 하지 않음
 * @param {import("@minecraft/server").Player} player
 */
export function feedTick(player) {
  if (!CONFIG.NO_HUNGER) return;
  for (const id of ["minecraft:player.hunger", "minecraft:player.saturation"]) {
    try {
      const c = /** @type {any} */ (player.getComponent(id));
      if (!c) {
        // 화면의 배고픔 칸을 숨겼으므로 채우지 못하면 보이지 않음 (달리기 · 자연 회복이 멈춤) → 콘텐츠 로그에 한 번
        if (!feedWarned && player.isValid) {
          feedWarned = true;
          console.warn(`[rpg] 배고픔 없음: ${id} 를 읽지 못해 채우지 못함`);
        }
        continue;
      }
      if (c.currentValue < c.effectiveMax) c.resetToMaxValue();
    } catch {
      // 로딩 중 등
    }
  }
}

const HUD_REFRESH = 600; // 화면이 다시 만들어져도 30초 안에 체력 바가 돌아오게 (일반 /title 을 덜 끊도록 길게)
const AGAIN = 40; // 숨김으로 바뀐 뒤 한 번 더 보내기까지 (틱)
const HP_TAIL = 40; // v2.6.3: 다른 칸 (길 안내) 을 보낸 뒤 체력 바를 다시 보내기까지 (틱) — 마지막 제목이 체력 바가 되게 (새로 만들어진 화면 칸도 2초 안에 잡게)

/**
 * 같은 제목 채널을 쓰는 다른 HUD 칸 (v2.6.1 길 안내): 플레이어 → 이름 → {want 보낼 글자, sent 보낸 글자, vis 보이는 상태인지, at 보낸 틱, force,
 * again 숨김으로 바뀐 뒤 한 번 더 보낼지 (채팅창·가방 등이 열려 있어 화면이 놓쳤을 수 있어서)}
 * @type {Map<string, Map<string, {want: string, sent: string, vis: boolean, at: number, force: boolean, again?: boolean}>>}
 */
const extras = new Map();

/**
 * HUD 칸 글자를 맡김 (updateHud 가 체력 바와 번갈아 보냄). visible = false 는 숨김 글자
 * (스크립트를 다시 불러온 뒤에는 화면에 무엇이 남았는지 모르므로 숨김도 처음 한 번은 보냄)
 * @param {import("@minecraft/server").Player} player
 * @param {string} key
 * @param {string} text
 * @param {boolean} visible
 */
export function setHudExtra(player, key, text, visible) {
  let m = extras.get(player.id);
  if (!m) extras.set(player.id, (m = new Map()));
  const e = m.get(key) ?? { want: "", sent: "", vis: false, at: -100000, force: false };
  e.want = text;
  e.vis = !!visible;
  m.set(key, e);
}

/** 보낼 HUD 칸 (바뀌었거나, 화면을 다시 만들었을 수 있을 때 (forceHud) · 숨김으로 바뀐 뒤 AGAIN 틱 · 30초마다 다시) */
function dueExtra(playerId, now) {
  const m = extras.get(playerId);
  if (!m) return undefined;
  for (const e of m.values()) {
    if (e.want !== e.sent || e.force || now - e.at >= (e.again ? AGAIN : HUD_REFRESH)) return e;
  }
  return undefined;
}

/** 필요하면 체력 바 문자열 (또는 길 안내 등 다른 HUD 칸) 을 보낸다. 2틱마다 호출 — 한 번에 하나 */
export function updateHud(player) {
  const s = hpState(player);
  const mode = String(player.getGameMode());
  if (mode !== s.mode) {
    s.mode = mode;
    s.forceHud = true;
  }
  const now = system.currentTick;
  if (now < s.pauseUntil) return;
  const rpg = isRpgMode(player);
  const text = rpg ? hudText(s, armorPoints(player), hudLevel(player)) : "";
  const hpDue = rpg && (text !== s.lastHud || s.forceHud || now - s.lastSent >= (s.lastKind === "extra" ? HP_TAIL : HUD_REFRESH));
  const ex = dueExtra(player.id, now);
  if (!hpDue && !ex) return;
  try {
    // 둘 다 기다리면 번갈아. 다만 바뀐 칸 (길 안내) 이 있으면 그것 먼저 — 큰 글자가 끝난 뒤 체력 바가 먼저 가서 예전 칸이 잠깐 보이지 않게
    if (hpDue && !(ex && (s.lastKind === "hp" || ex.want !== ex.sent))) {
      player.onScreenDisplay.setTitle(text);
      s.lastHud = text;
      s.lastSent = now;
      s.forceHud = false;
      s.lastKind = "hp";
    } else if (ex) {
      const changed = ex.sent !== ex.want;
      player.onScreenDisplay.setTitle(ex.want);
      ex.sent = ex.want;
      ex.at = now;
      ex.force = false;
      ex.again = changed && !ex.vis;
      s.lastKind = "extra";
    }
  } catch {
    // 로딩 중
  }
}

export function forceHud(player) {
  hpState(player).forceHud = true;
  for (const e of extras.get(player.id)?.values() ?? []) e.force = true;
}

/**
 * 일반 제목 표시. 체력 바 갱신을 잠시 멈춰서 제목이 끊기지 않게 한다.
 * @param {{fadeIn?: number, stay?: number, fadeOut?: number}} [t] v2.26.0 보이는 시간 (틱, 없으면 10 · 70 · 20 — 결투 3 · 2 · 1 은 짧게)
 */
export function showRealTitle(player, title, subtitle, t = undefined) {
  const s = hpState(player);
  const opts = { fadeInDuration: t?.fadeIn ?? 10, stayDuration: t?.stay ?? 70, fadeOutDuration: t?.fadeOut ?? 20 };
  s.pauseUntil = system.currentTick + opts.fadeInDuration + opts.stayDuration + opts.fadeOutDuration + 5;
  s.forceHud = true;
  // 일반 제목이 보이는 동안 화면의 길 안내 칸은 숨음 (hud_screen.json) → 끝나면 다시 보냄
  for (const e of extras.get(player.id)?.values() ?? []) e.force = true;
  if (subtitle) opts.subtitle = subtitle;
  player.onScreenDisplay.setTitle(title, opts);
}

/** 남은 체력 비율을 저장한다 (20틱마다). 죽어 있으면 1(가득). */
export function saveHp(player) {
  const s = hpState(player);
  const ratio = s.dead || !(s.max > 0) ? 1 : Math.max(0.001, Math.min(1, Math.round((s.hp / s.max) * 1000) / 1000));
  if (Math.abs(ratio - s.saved) < 0.005) return;
  try {
    player.setDynamicProperty(SAVE_KEY, ratio);
    if (player.getDynamicProperty(OLD_SAVE_KEY) !== undefined) player.setDynamicProperty(OLD_SAVE_KEY, undefined);
    s.saved = ratio;
  } catch {
    // 무시
  }
}
