import { GameMode, PlayerPermissionLevel, system, world } from "@minecraft/server";
import { notify, particle, play, warn } from "./fx.js";
import { markToolTap } from "./npc.js";

/**
 * 훈련용 허수아비 (v1.2.2, v1.7.0 이름 바꿈 — 몬스터 허수아비와 구별): 죽지 않는 샌드백. 맞은 피해를 모아 머리 위에 피해 합·초당 피해·횟수·최고 한 방을 보여 준다.
 * - 소환: /dummy (운영자, 눈앞 3칸에 나를 보게), 크리에이티브 목록의 "훈련용 허수아비 소환" 알
 * - 치우기: /dummy clear (주변 48칸), NPC 설정 도구를 들고 허수아비 터치 (운영자·크리에이티브)
 * - 5초 동안 안 맞으면 기록을 지운다. 밀려나도 제자리로 돌아온다
 */
export const DUMMY = "rpg:training_dummy";
export const DUMMY_IDLE_TICKS = 100;
const ANCHOR = "rpg:anchor";
const TOOL = "rpg:npc_tool";

/** 허수아비 id → 이번 기록 @type {Map<string, {e: any, first: number, last: number, total: number, hits: number, max: number}>} */
const sessions = new Map();
/** 이번 틱에 들어온 원래 피해 (죽지 않게 줄이기 전 값) @type {Map<string, number>} */
const pending = new Map();

/** 1234.5 → "1,234.5" (100 이상은 정수) */
export function dummyNum(n) {
  const v = Math.max(0, n);
  const s = v >= 100 ? String(Math.round(v)) : (Math.round(v * 10) / 10).toFixed(1);
  const [i, f] = s.split(".");
  const withComma = i.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return f !== undefined ? `${withComma}.${f}` : withComma;
}

/** 머리 위 표시 (기록이 없으면 안내) */
export function dummyLabel(s) {
  if (!s || s.hits === 0) return "§6§l훈련용 허수아비§r\n§7때리면 피해 합·초당 피해가 나옵니다";
  const secs = Math.max(1, (s.last - s.first) / 20 + 0.5);
  return `§6§l훈련용 허수아비§r\n§f합 §e${dummyNum(s.total)} §7· §f초당 §e${dummyNum(s.total / secs)}\n§7${s.hits}회 · 최고 한 방 §f${dummyNum(s.max)}`;
}

/** 운영자이거나 크리에이티브 */
function canManage(player) {
  try {
    return player.playerPermissionLevel === PlayerPermissionLevel.Operator || player.getGameMode() === GameMode.Creative;
  } catch {
    return false;
  }
}

function anchorOf(e) {
  try {
    const raw = e.getDynamicProperty(ANCHOR);
    return typeof raw === "string" ? JSON.parse(raw) : undefined;
  } catch {
    return undefined;
  }
}

function setAnchor(e, loc) {
  try {
    e.setDynamicProperty(ANCHOR, JSON.stringify({ x: loc.x, y: loc.y, z: loc.z }));
  } catch {
    // 무시
  }
}

function poof(dim, loc) {
  particle(dim, "minecraft:death_explosion_emitter", { x: loc.x, y: loc.y + 1, z: loc.z });
  play(dim, "random.pop", loc, 0.8, 0.9);
}

/** 눈앞 3칸에 허수아비 (플레이어를 보게) */
export function spawnDummy(player) {
  const dim = player.dimension;
  const v = player.getViewDirection();
  const l = Math.hypot(v.x, v.z) || 1;
  const p = player.location;
  const loc = { x: p.x + (v.x / l) * 3, y: p.y, z: p.z + (v.z / l) * 3 };
  const e = dim.spawnEntity(DUMMY, loc);
  try {
    e.teleport(loc, { facingLocation: { x: p.x, y: loc.y, z: p.z } });
  } catch {
    // 무시
  }
  setAnchor(e, loc);
  e.nameTag = dummyLabel(undefined);
  poof(dim, loc);
  return e;
}

function removeDummy(e) {
  let dim;
  let loc;
  try {
    dim = e.dimension;
    loc = e.location;
  } catch {
    return;
  }
  sessions.delete(e.id);
  try {
    e.remove();
  } catch {
    return;
  }
  poof(dim, loc);
}

/** 주변 허수아비 치우기. 치운 수 */
export function clearDummies(player, radius = 48) {
  let list = [];
  try {
    list = player.dimension.getEntities({ type: DUMMY, location: player.location, maxDistance: radius });
  } catch {
    list = [];
  }
  for (const e of list) removeDummy(e);
  return list.length;
}

/** 밀려났으면 제자리로 (높이는 그대로 두어 땅에 서게) */
function snapBack(e) {
  if (!e?.isValid) return;
  const a = anchorOf(e);
  if (!a) return;
  const l = e.location;
  if (Math.hypot(l.x - a.x, l.z - a.z) <= 0.05) return;
  try {
    e.teleport({ x: a.x, y: l.y, z: a.z }, { keepVelocity: false });
  } catch {
    // 무시
  }
}

export function dummySession(e) {
  return sessions.get(e.id);
}

function onHurtBefore(ev) {
  const e = ev.hurtEntity;
  if (e?.typeId !== DUMMY) return;
  // 이 핸들러는 전투 계산(combat.js) 다음에 등록되어 최종 피해를 본다
  pending.set(e.id, ev.damage);
  let hp = 0;
  try {
    hp = e.getComponent("minecraft:health")?.currentValue ?? 0;
  } catch {
    hp = 0;
  }
  if (hp > 1 && ev.damage >= hp - 1) ev.damage = hp - 1; // 한 방에 쓰러지지 않게 (기록은 원래 피해)
}

function onHurtAfter(ev) {
  const e = ev.hurtEntity;
  if (e?.typeId !== DUMMY || !e.isValid) return;
  const d = Math.max(0, pending.get(e.id) ?? ev.damage);
  pending.delete(e.id);
  const now = system.currentTick;
  let s = sessions.get(e.id);
  if (!s || now - s.last > DUMMY_IDLE_TICKS) {
    s = { e, first: now, last: now, total: 0, hits: 0, max: 0 };
    sessions.set(e.id, s);
  }
  s.e = e;
  s.last = now;
  s.total += d;
  s.hits += 1;
  s.max = Math.max(s.max, d);
  try {
    e.getComponent("minecraft:health")?.resetToMaxValue();
  } catch {
    // 무시
  }
  try {
    e.nameTag = dummyLabel(s);
  } catch {
    // 무시
  }
  if (!anchorOf(e)) setAnchor(e, e.location);
  system.runTimeout(() => snapBack(e), 2);
}

/** 5초 동안 안 맞은 기록 지우기 */
function tick() {
  const now = system.currentTick;
  for (const [id, s] of sessions) {
    if (now - s.last <= DUMMY_IDLE_TICKS) continue;
    sessions.delete(id);
    if (!s.e?.isValid) continue;
    try {
      s.e.nameTag = dummyLabel(undefined);
    } catch {
      // 무시
    }
    snapBack(s.e);
  }
}

export function registerDummy() {
  world.beforeEvents.entityHurt.subscribe((ev) => {
    try {
      onHurtBefore(ev);
    } catch (e) {
      console.warn(`[rpg] dummy hurt: ${e}`);
    }
  });
  world.afterEvents.entityHurt.subscribe((ev) => {
    try {
      onHurtAfter(ev);
    } catch (e) {
      console.warn(`[rpg] dummy hurt: ${e}`);
    }
  });
  // 알로 소환한 허수아비: 제자리·안내 표시
  world.afterEvents.entitySpawn.subscribe((ev) => {
    const e = ev.entity;
    if (e?.typeId !== DUMMY) return;
    system.run(() => {
      if (!e.isValid) return;
      if (!anchorOf(e)) setAnchor(e, e.location);
      if (!e.nameTag) e.nameTag = dummyLabel(undefined);
    });
  });
  // NPC 설정 도구를 들고 터치 → 치우기 (운영자·크리에이티브)
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    if (ev.target?.typeId !== DUMMY || ev.itemStack?.typeId !== TOOL) return;
    ev.cancel = true;
    const player = ev.player;
    markToolTap(player); // 같은 터치로 NPC 관리 창이 열리지 않게
    const target = ev.target;
    system.run(() => {
      if (!player.isValid) return;
      if (!canManage(player)) {
        warn(player, "§c훈련용 허수아비는 운영자 또는 크리에이티브 모드에서만 치울 수 있습니다");
        return;
      }
      if (target.isValid) removeDummy(target);
      notify(player, "§7훈련용 허수아비를 치웠습니다", 30);
    });
  });
  system.runInterval(tick, 10);
}
