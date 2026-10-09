import { GameMode, InputPermissionCategory, MolangVariableMap, system, world } from "@minecraft/server";
import { notify, play, showFaceText } from "./fx.js";
import { instantKill } from "./hp.js";

/**
 * 보스 기믹이 플레이어에게 거는 상태 (v1.8.0)
 * - 속박: 움직이기·뛰기를 막음 (입력 권한). 시간이 끝나거나, 죽거나, 나가거나, 다시 들어오면 풂
 * - 침묵: 무기 스킬·룬을 쓰지 못함 (castSkill·castRune 이 확인). 머리 위에 보라 고리
 * - 즉사: hp.js instantKill (보호막·불사의 맹약 무시, 불사의 토템은 바닐라처럼 막음)
 * 저장하지 않음: 다시 접속·/reload 하면 속박은 풀리고 침묵은 사라짐
 */

/** 속박: 플레이어 id → {p, until} */
const roots = new Map();
/** 침묵: 플레이어 id → {p, until} */
const silences = new Map();

function setMove(player, on) {
  try {
    player.inputPermissions.setPermissionCategory(InputPermissionCategory.Movement, on);
    player.inputPermissions.setPermissionCategory(InputPermissionCategory.Jump, on);
  } catch {
    // 권한을 바꿀 수 없는 플레이어
  }
}

/**
 * 속박 (움직이기·뛰기 막음). 이미 걸려 있으면 더 늦게 끝나는 쪽으로
 * @param {import("@minecraft/server").Player} player
 * @param {number} ticks
 * @param {string} [label] 화면 글자 (예: "짚단 감옥")
 */
export function root(player, ticks, label = "속박") {
  if (!player?.isValid) return;
  const until = system.currentTick + Math.max(1, Math.round(ticks));
  const cur = roots.get(player.id);
  roots.set(player.id, { p: player, until: Math.max(until, cur?.until ?? 0) });
  setMove(player, false);
  if (!cur) {
    showFaceText(player, `§6§l${label}!`, { dist: 2.2, up: -0.35 });
    play(player.dimension, "place.chain", player.location, 0.7, 1);
  }
}

/** 속박 풀기 (감옥이 부서짐 등) */
export function unroot(player) {
  if (!player) return;
  const had = roots.delete(player.id);
  if (player.isValid) setMove(player, true);
  return had;
}

export function isRooted(player) {
  const r = roots.get(player?.id);
  return !!r && r.until > system.currentTick;
}

/**
 * 침묵 (무기 스킬·룬 못 씀). 이미 걸려 있으면 더 늦게 끝나는 쪽으로
 * @param {import("@minecraft/server").Player} player
 * @param {number} ticks
 */
export function silence(player, ticks) {
  if (!player?.isValid) return;
  const until = system.currentTick + Math.max(1, Math.round(ticks));
  const cur = silences.get(player.id);
  silences.set(player.id, { p: player, until: Math.max(until, cur?.until ?? 0) });
  showFaceText(player, "§5§l침묵!", { dist: 2.2, up: 0.1 });
  play(player.dimension, "mob.evocation_illager.cast_spell", player.location, 0.6, 1);
}

/** 침묵 풀기 (v2.1.0 거대 웜이 삼킨 사람을 뱉음 등) */
export function unsilence(player) {
  if (!player) return false;
  return silences.delete(player.id);
}

/** 남은 침묵 (틱, 없으면 0) */
export function silenceLeft(player) {
  const s = silences.get(player?.id);
  return s ? Math.max(0, s.until - system.currentTick) : 0;
}

export function isSilenced(player) {
  return silenceLeft(player) > 0;
}

/** 침묵 중이면 알리고 true (스킬·룬 입구에서) */
export function blockedBySilence(player) {
  const left = silenceLeft(player);
  if (left <= 0) return false;
  notify(player, `§5침묵 중 §7— 스킬·룬을 쓸 수 없습니다 §f${(left / 20).toFixed(1)}초`, 30);
  try {
    player.playSound("note.bass", { pitch: 0.5, volume: 0.8 });
  } catch {
    // 무시
  }
  return true;
}

/**
 * 즉사 (보스 기믹). 크리에이티브·관전은 무시
 * @returns {boolean}
 */
export function kill(player, attacker) {
  if (!player?.isValid) return false;
  try {
    const gm = player.getGameMode();
    if (gm === GameMode.Creative || gm === GameMode.Spectator) return false;
  } catch {
    return false;
  }
  return instantKill(player, attacker);
}

/** 이 플레이어의 상태를 모두 풂 (죽음·나감·다시 들어옴) */
export function clearStatus(player) {
  if (!player) return;
  roots.delete(player.id);
  silences.delete(player.id);
  if (player.isValid) setMove(player, true);
}

function tick() {
  const now = system.currentTick;
  for (const [id, r] of roots) {
    if (!r.p.isValid) {
      roots.delete(id);
      continue;
    }
    if (r.until <= now) {
      roots.delete(id);
      setMove(r.p, true);
    }
  }
  for (const [id, s] of silences) {
    if (!s.p.isValid || s.until <= now) {
      silences.delete(id);
      continue;
    }
    // 머리 위 보라 고리 (0.5초마다)
    if (now % 10 < 2) {
      try {
        const h = s.p.getHeadLocation();
        const v = new MolangVariableMap();
        v.setVector3("variable.dir", { x: 0, y: 1, z: 0 });
        for (const [k, val] of Object.entries({ speed: 0, spin: (now * 12) % 360, size: 0.42, life: 0.5, thick: 0, delay: 0, grow: 0, progress: 1 })) v.setFloat(`variable.${k}`, val);
        v.setColorRGBA("variable.color", { red: 0.62, green: 0.25, blue: 0.95, alpha: 0.9 });
        s.p.dimension.spawnParticle("rpg:skill_rune", { x: h.x, y: h.y + 0.55, z: h.z }, v);
      } catch {
        // 무시
      }
    }
  }
}

export function registerStatus() {
  system.runInterval(tick, 2);
  world.afterEvents.entityDie.subscribe((ev) => {
    const e = ev.deadEntity;
    if (e?.typeId === "minecraft:player") clearStatus(e);
  });
  world.afterEvents.playerSpawn.subscribe((ev) => {
    // 처음 들어오거나 다시 살아남: 남아 있을 수 있는 속박(입력 권한)을 풂
    clearStatus(ev.player);
  });
  world.beforeEvents.playerLeave.subscribe((ev) => {
    const id = ev.player.id;
    system.run(() => {
      roots.delete(id);
      silences.delete(id);
    });
  });
  world.afterEvents.worldLoad.subscribe(() => {
    // /reload 뒤: 속박이 걸린 채 멈춘 플레이어가 없게
    for (const p of world.getAllPlayers()) setMove(p, true);
  });
}
