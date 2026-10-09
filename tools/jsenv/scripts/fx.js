import { EntityTypes, system } from "@minecraft/server";
import { CONFIG } from "./config.js";

/** 연출 모음: 피해 숫자, 치명타·막기 효과, 경고 문구 */

const TEXT_ENTITY = "rpg:dmg_text";
/** 피해 숫자 모양: 평타 흰색, 치명타 금색 !, 받은 피해 빨강 -, 스킬 하늘색 굵게, 스킬 치명타 분홍 굵게 !!, 회복 초록 +, 룬 노랑, v2.13.0 마검사의 마력 (마법) 파랑 (마력 스탯 색) */
const TEXT_STYLE = {
  normal: (n) => `§f${n}`,
  crit: (n) => `§6§l${n}!`,
  player: (n) => `§c-${n}`,
  skill: (n) => `§b§l${n}`,
  skillcrit: (n) => `§d§l${n}!!`,
  heal: (n) => `§a§l+${n}`,
  rune: (n) => `§e§l${n}`, // v1.3.0 룬 스킬 피해
  spell: (n) => `§9§l${n}`, // v2.13.0 마검사 룬의 마력 (마법 피해) · 마검 부여 — 마력 스탯 색
  spellcrit: (n) => `§9§l${n}!!`, // v2.16.0 티르빙 스킬의 마력 치명타 (무기 스킬은 치명타가 있음)
};
/** 피해 숫자 모양 @typedef {"normal" | "crit" | "player" | "skill" | "skillcrit" | "heal" | "rune" | "spell" | "spellcrit"} TextStyle */
const MAX_TEXTS = 30;
/** v2.16.0 내 눈앞 글자 (회복 · 피의 대가 · 콤보 등) 는 남의 피해 숫자가 많아도 몇 개 더 띄움 (여럿이 보스를 칠 때 내 체력 알림이 사라지지 않게) */
const SELF_EXTRA = 6;
/** @type {{e: import("@minecraft/server").Entity, t: number, pos: {x:number,y:number,z:number}}[]} */
const texts = [];
let textBroken = false;

export function play(dimension, sound, location, pitch = 1, volume = 1) {
  try {
    dimension.playSound(sound, location, { pitch, volume });
  } catch {
    // 무시
  }
}

export function particle(dimension, id, location) {
  try {
    dimension.spawnParticle(id, location);
  } catch {
    // 청크가 로드되지 않은 곳 등
  }
}

/** v1.7.0: 피해 숫자 자리 (몬스터는 이름표·체력 막대와 겹치지 않게 몸통 높이). undefined 면 머리 위 @type {(victim: any) => ({x: number, y: number, z: number} | undefined)} */
/**
 * v1.8.0: 여러 모듈이 등록 (몬스터·보스), 처음 자리를 준 것. v2.12.0: 때린 사람 (attacker) 도 넘김 (수르트처럼 큰 몸은 때린 쪽 겉면에 띄움)
 * @type {((victim: any, attacker?: any) => ({x: number, y: number, z: number} | undefined))[]}
 */
const anchors = [];
export function setDamageTextAnchor(fn) {
  if (fn) anchors.push(fn);
}
function textAnchor(victim, attacker) {
  for (const f of anchors) {
    const at = f(victim, attacker);
    if (at) return at;
  }
  return undefined;
}

/**
 * 맞은 대상 머리 위에 피해 숫자를 띄운다.
 * @param {import("@minecraft/server").Entity} victim
 * @param {number} amount
 * @param {TextStyle} style
 * @param {string} [mark] 숫자 앞 표시 (v2.3.0 보스 근접 보너스 »)
 * @param {any} [attacker] 때린 사람 (v2.12.0: 큰 보스는 그 사람 쪽에 숫자를 띄움)
 * @param {number} [lift] 더 높이 띄울 칸 (v2.13.0: 한 번에 나온 둘째 숫자 — 마검 부여 — 가 평타 숫자를 가리지 않게)
 */
export function showDamageText(victim, amount, style, mark = "", attacker = undefined, lift = 0) {
  const self = style === "heal" && victim?.typeId === "minecraft:player";
  if (!CONFIG.DAMAGE_TEXT || textBroken || texts.length >= MAX_TEXTS + (self ? SELF_EXTRA : 0)) return;
  if (!victim.isValid) return;
  let head;
  try {
    // 허수아비는 머리 위에 기록(피해 합·초당 피해)이 떠 있어서 숫자를 몸통 높이에서 띄운다
    const l = victim.typeId === "rpg:training_dummy" ? victim.location : undefined;
    head = l ? { x: l.x, y: l.y + 1.25, z: l.z } : (textAnchor(victim, attacker) ?? victim.getHeadLocation());
    // v1.3.0: 플레이어가 받은 회복은 얼굴 앞 2칸, 조준점 오른쪽 아래에서 떠오름 (머리 위는 1인칭 화면 밖이라 안 보임)
    if (style === "heal" && victim.typeId === "minecraft:player") {
      const v = victim.getViewDirection();
      const h = Math.hypot(v.x, v.z) || 1;
      const f = { x: v.x / h, z: v.z / h };
      head = { x: head.x + f.x * 2.0 - f.z * 0.55, y: head.y - 0.6, z: head.z + f.z * 2.0 + f.x * 0.55 };
    }
  } catch {
    return;
  }
  const pos = { x: head.x + (Math.random() - 0.5) * 0.7, y: head.y + 0.3 + lift, z: head.z + (Math.random() - 0.5) * 0.7 };
  let e;
  try {
    e = victim.dimension.spawnEntity(TEXT_ENTITY, pos);
  } catch (err) {
    // 엔티티가 등록되지 않았을 때만 기능을 끈다. 로드 안 된 곳·높이 제한 등은 이번 것만 건너뛴다.
    if (!EntityTypes.get(TEXT_ENTITY)) {
      textBroken = true;
      console.warn(`[rpg] damage text disabled: ${err}`);
    }
    return;
  }
  // 스탯 1포인트 차이도 보이도록 100 미만은 소수 첫째 자리까지
  const v = Math.max(0, amount);
  const n = v >= 100 ? String(Math.round(v)) : String(Math.round(v * 10) / 10);
  e.nameTag = mark + (TEXT_STYLE[style] ? TEXT_STYLE[style](n) : `§f${n}`);
  texts.push({ e, t: 0, pos });
}

/**
 * 플레이어 눈앞에 떠오르는 글자 (v1.6.0 콤보·반격 등: 머리 위는 1인칭 화면 밖이라 얼굴 앞 dist 칸에).
 * side: 화면 오른쪽(+)으로, up: 눈높이에서 위로
 * @param {import("@minecraft/server").Player} player
 * @param {string} text
 * @param {{dist?: number, side?: number, up?: number}} [o]
 */
export function showFaceText(player, text, o = {}) {
  if (!CONFIG.DAMAGE_TEXT || textBroken || texts.length >= MAX_TEXTS + SELF_EXTRA || !player.isValid) return;
  let pos;
  try {
    const h = player.getHeadLocation();
    const v = player.getViewDirection();
    const hz = Math.hypot(v.x, v.z) || 1;
    const f = { x: v.x / hz, z: v.z / hz };
    const dist = o.dist ?? 2.2;
    const side = o.side ?? 0;
    pos = { x: h.x + f.x * dist - f.z * side, y: h.y + (o.up ?? 0.2), z: h.z + f.z * dist + f.x * side };
  } catch {
    return;
  }
  let e;
  try {
    e = player.dimension.spawnEntity(TEXT_ENTITY, pos);
  } catch (err) {
    if (!EntityTypes.get(TEXT_ENTITY)) {
      textBroken = true;
      console.warn(`[rpg] damage text disabled: ${err}`);
    }
    return;
  }
  e.nameTag = text;
  texts.push({ e, t: 0, pos });
}

/**
 * 그 자리에서 떠오르는 글자 (v2.7.0: 몬스터를 잡은 자리의 "+3 경험치" 등 — 쓰러지는 몸이 이미 사라졌을 수 있어 자리로 받음)
 * @param {import("@minecraft/server").Dimension} dim
 * @param {{x:number, y:number, z:number}} at
 * @param {string} text
 */
export function showTextAt(dim, at, text) {
  if (!CONFIG.DAMAGE_TEXT || textBroken || texts.length >= MAX_TEXTS) return;
  const pos = { x: at.x + (Math.random() - 0.5) * 0.4, y: at.y, z: at.z + (Math.random() - 0.5) * 0.4 };
  let e;
  try {
    e = dim.spawnEntity(TEXT_ENTITY, pos);
  } catch (err) {
    if (!EntityTypes.get(TEXT_ENTITY)) {
      textBroken = true;
      console.warn(`[rpg] damage text disabled: ${err}`);
    }
    return;
  }
  e.nameTag = text;
  texts.push({ e, t: 0, pos });
}

export function startFxLoop() {
  system.runInterval(() => {
    for (let i = texts.length - 1; i >= 0; i--) {
      const a = texts[i];
      a.t++;
      if (!a.e.isValid || a.t > 18) {
        try {
          if (a.e.isValid) a.e.remove();
        } catch {
          // 무시
        }
        texts.splice(i, 1);
        continue;
      }
      a.pos.y += a.t < 6 ? 0.09 : 0.025;
      try {
        a.e.teleport(a.pos);
      } catch {
        // 무시
      }
    }
  }, 1);
}

/** 치명타 효과 (제한 없는 문맥에서 호출). */
export function critFx(victim) {
  if (!victim.isValid) return;
  const loc = victim.getHeadLocation();
  particle(victim.dimension, "minecraft:critical_hit_emitter", loc);
  play(victim.dimension, "game.player.attack.strong", loc, 1.1, 1);
  play(victim.dimension, "random.anvil_land", loc, 2.0, 0.25);
}

/** 방패 막기 효과. */
export function blockFx(player) {
  if (!player.isValid) return;
  const d = player.getViewDirection();
  const h = player.getHeadLocation();
  const loc = { x: h.x + d.x * 0.8, y: h.y - 0.4, z: h.z + d.z * 0.8 };
  particle(player.dimension, "minecraft:critical_hit_emitter", loc);
  play(player.dimension, "item.shield.block", loc, 1, 1);
}

/** @type {Map<string, number>} */
const lastWarn = new Map();
/** 액션바 알림을 보여 주는 동안(틱)에는 NPC 접근 안내가 덮어쓰지 않게 한다 @type {Map<string, number>} */
const noticeUntil = new Map();

/** 화면 하단에 경고를 띄운다 (1.5초에 한 번). 제한 없는 문맥에서 호출. */
export function warn(player, text) {
  const now = system.currentTick;
  if ((lastWarn.get(player.id) ?? -1000) + 30 > now) return;
  lastWarn.set(player.id, now);
  noticeUntil.set(player.id, now + 40);
  try {
    player.onScreenDisplay.setActionBar(text);
    player.playSound("note.bass", { pitch: 0.7, volume: 0.8 });
  } catch {
    // 무시
  }
}

/** 화면 하단 알림 (퀘스트 진행 등). hold 틱 동안 다른 안내가 덮어쓰지 않는다. */
export function notify(player, text, hold = 60) {
  noticeUntil.set(player.id, system.currentTick + hold);
  try {
    player.onScreenDisplay.setActionBar(text);
  } catch {
    // 무시
  }
}

/** 지금 액션바에 안내를 띄워도 되는지 */
export function actionbarFree(player) {
  return (noticeUntil.get(player.id) ?? -1) <= system.currentTick;
}

/**
 * v2.13.0 화면 하단 상태 줄의 덧붙임 (룬·초월 무기 스킬 재사용 표시 뒤에 붙음, 예: 마검사 룬의 마검 부여). fn(player) → 글자 ("" 이면 없음)
 * @type {((player: any) => string)[]}
 */
const hudSuffixes = [];
export function addHudSuffix(fn) {
  if (fn) hudSuffixes.push(fn);
}
/** 상태 줄에 덧붙일 글자 (" §8| …" 모양으로, 없으면 "") */
export function hudSuffix(player) {
  let out = "";
  for (const fn of hudSuffixes) {
    try {
      const t = fn(player);
      if (t) out += ` §8| ${t}`;
    } catch {
      // 무시
    }
  }
  return out;
}

/** NPC 접근 안내를 띄운 틱 (스킬 재사용 표시가 번갈아 덮어쓰지 않게) @type {Map<string, number>} */
const promptAt = new Map();

export function markPrompt(player) {
  promptAt.set(player.id, system.currentTick);
}

/** 1초 안에 NPC 접근 안내를 띄웠는지 */
export function promptedRecently(player) {
  return system.currentTick - (promptAt.get(player.id) ?? -1000) < 20;
}

export function forgetFx(playerId) {
  lastWarn.delete(playerId);
  noticeUntil.delete(playerId);
  promptAt.delete(playerId);
}
