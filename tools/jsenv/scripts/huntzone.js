import { system, world } from "@minecraft/server";
import { HUNT_ZONE, MONSTER, MONSTERS, monsterId } from "./config.js";
import { particle, play } from "./fx.js";
import { fightOf, monsterDef, monsterLabel } from "./monsters.js";

/**
 * 사냥터 (v2.6.0): 운영자가 정한 자리에 몬스터를 늘 정해 둔 수만큼 채움 (몬스터는 자연 스폰이 없어 잡으면 사라짐 → 메인 퀘스트의 처치 단계가 막히지 않게)
 * - /mobzone 종류 [수]: 내 발밑을 가운데로 사냥터를 만들고 바로 그 수만큼 부름 (수 1~HUNT_ZONE.maxCount, 기본 defaultCount)
 *   /mobzone: 사냥터 목록 · /mobzone clear: 16칸 안의 가장 가까운 사냥터와 그 몬스터를 치움
 * - 1초마다: 가운데에서 HUNT_ZONE.active 칸 안에 플레이어가 있고, 몬스터가 돌아다니는 넓이 (radius + 몬스터 homeRadius) 가
 *   다 불러와진 사냥터만 셈 (불러오지 않은 곳의 몬스터를 못 세서 더 부르지 않게). 모자라면 HUNT_ZONE.respawn 틱 뒤에 한 마리
 *   (가운데에서 radius 칸 안의 설 수 있는 자리, 연기 + 소리). 쫓아 나간 몬스터도 그 사냥터의 수로 셈 (태그 rpg_zone_<번호>)
 * - 그래도 넘치면 (멀리 끌려가 남았던 몬스터가 다시 불러와짐 등) 싸우지 않는 것 중 가운데에서 먼 것부터 치움.
 *   지운 사냥터의 몬스터가 나중에 불러와지면 치움 (entityLoad). /mob clear 는 사냥터 몬스터를 남김
 * - 기록: 세계 동적 속성 rpg:hunt_zones [{id, kind, count, dim, x, y, z}]
 */

const W_ZONES = "rpg:hunt_zones";
const W_SEQ = "rpg:hunt_zone_seq";
/** @typedef {{id: string, kind: string, count: number, dim: string, x: number, y: number, z: number}} Zone */
/** @type {Zone[] | undefined} */
let zones;
/** 사냥터 번호 → 다음 몬스터를 부를 틱 (모자란 것을 안 때부터 respawn 틱 뒤) */
const dueAt = new Map();

/** @returns {Zone[]} */
function load() {
  if (zones) return zones;
  try {
    const v = world.getDynamicProperty(W_ZONES);
    const list = typeof v === "string" ? JSON.parse(v) : [];
    zones = Array.isArray(list) ? list.filter((z) => z && MONSTERS[z.kind] && Number.isFinite(z.x) && Number.isFinite(z.y) && Number.isFinite(z.z)) : [];
  } catch {
    zones = [];
  }
  return zones;
}

function save() {
  world.setDynamicProperty(W_ZONES, JSON.stringify(load()));
}

/** 다시 읽게 (시험용) */
export function forgetZones() {
  zones = undefined;
  dueAt.clear();
}

export function zoneList() {
  return [...load()];
}

const tagOf = (z) => `rpg_zone_${z.id}`;
const centerOf = (z) => ({ x: z.x + 0.5, y: z.y, z: z.z + 0.5 });

function membersOf(dim, z) {
  try {
    return dim.getEntities({ type: monsterId(z.kind), tags: [tagOf(z)] });
  } catch {
    return [];
  }
}

function aliveIn(dim, z) {
  return membersOf(dim, z).length;
}

/** 몬스터가 돌아다니는 넓이 (가운데 + 네 모서리) 가 다 불러와졌는지 */
function areaLoaded(dim, z) {
  const r = HUNT_ZONE.radius + MONSTER.homeRadius;
  const c = centerOf(z);
  for (const [dx, dz] of [[0, 0], [-r, -r], [r, -r], [-r, r], [r, r]]) {
    try {
      if (!dim.isChunkLoaded({ x: c.x + dx, y: c.y, z: c.z + dz })) return false;
    } catch {
      return false;
    }
  }
  return true;
}

/** 넘치는 몬스터를 치움: 싸우지 않는 것 중 가운데에서 먼 것부터. 치운 수 */
function trimExtra(z, members) {
  const extra = members.length - z.count;
  if (extra <= 0) return 0;
  const c = centerOf(z);
  const idle = members.filter((e) => !fightOf(e)).sort((a, b) => Math.hypot(b.location.x - c.x, b.location.z - c.z) - Math.hypot(a.location.x - c.x, a.location.z - c.z));
  let n = 0;
  for (const e of idle.slice(0, extra)) {
    try {
      e.remove();
      n++;
    } catch {
      // 무시
    }
  }
  return n;
}

/** 설 수 있는 자리 (발·머리가 비고 밑이 단단함). 못 찾으면 가운데 */
function spotIn(dim, z) {
  for (let tries = 0; tries < 10; tries++) {
    const a = Math.random() * Math.PI * 2;
    const r = Math.sqrt(Math.random()) * HUNT_ZONE.radius;
    // 사냥터 가운데 (블록 가운데, centerOf) 에서 r 안 → 고른 블록의 가운데는 반지름 + 0.71 안 (v2.9.0: 블록 모서리 기준이라 드물게 1.4칸 밖에 나오던 것)
    const x = Math.floor(z.x + 0.5 + Math.cos(a) * r);
    const zz = Math.floor(z.z + 0.5 + Math.sin(a) * r);
    try {
      for (let y = z.y + 3; y >= z.y - 4; y--) {
        const feet = dim.getBlock({ x, y, z: zz });
        const head = dim.getBlock({ x, y: y + 1, z: zz });
        const below = dim.getBlock({ x, y: y - 1, z: zz });
        if (feet?.isAir && head?.isAir && below && !below.isAir && !below.isLiquid) return { x: x + 0.5, y, z: zz + 0.5 };
      }
    } catch {
      // 불러오지 않은 곳: 다른 자리
    }
  }
  return centerOf(z);
}

function spawnOne(dim, z) {
  const at = spotIn(dim, z);
  const e = dim.spawnEntity(monsterId(z.kind), at);
  e.addTag(tagOf(z));
  const def = monsterDef(e);
  if (def) e.nameTag = monsterLabel(def, 1, 1);
  particle(dim, "minecraft:death_explosion_emitter", { x: at.x, y: at.y + 0.6, z: at.z });
  play(dim, "random.pop", at, 0.6, 0.8);
  return e;
}

/**
 * /mobzone 종류 [수]: 내 발밑에 사냥터
 * @param {import("@minecraft/server").Player} player
 * @returns {string} 알림
 */
export function addZone(player, kind, count) {
  if (!MONSTERS[kind]) return `§c종류: ${Object.keys(MONSTERS).join(" · ")}`;
  const list = load();
  if (list.length >= HUNT_ZONE.maxZones) return `§c사냥터는 ${HUNT_ZONE.maxZones}곳까지입니다. §7/mobzone clear 로 하나를 지우세요.`;
  const n = Math.max(1, Math.min(HUNT_ZONE.maxCount, Math.floor(Number(count ?? HUNT_ZONE.defaultCount)) || HUNT_ZONE.defaultCount));
  const seq = Number(world.getDynamicProperty(W_SEQ) ?? 0) + 1;
  world.setDynamicProperty(W_SEQ, seq);
  const l = player.location;
  /** @type {Zone} */
  const z = { id: `z${seq}`, kind, count: n, dim: player.dimension.id, x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) };
  list.push(z);
  save();
  const dim = player.dimension;
  for (let i = 0; i < n; i++) {
    try {
      spawnOne(dim, z);
    } catch {
      break;
    }
  }
  // 넓이를 보여 주는 고리
  const c = centerOf(z);
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * Math.PI * 2;
    particle(dim, "minecraft:villager_happy", { x: c.x + Math.cos(a) * HUNT_ZONE.radius, y: c.y + 0.3, z: c.z + Math.sin(a) * HUNT_ZONE.radius });
  }
  player.playSound("random.orb", { pitch: 1.1 });
  const m = MONSTERS[kind];
  return `§6[사냥터] §f${z.id}: Lv.${m.level} ${m.name} ${n}마리 §7(잡으면 ${Math.round(HUNT_ZONE.respawn / 20)}초 뒤 다시 나옴, ${HUNT_ZONE.active}칸 안에 사람이 있을 때)`;
}

/** /mobzone clear: 가장 가까운 사냥터 (range 칸 안) 와 그 몬스터 (불러와진 것) 를 치움 */
export function removeNearestZone(player, range = 16) {
  const list = load();
  const l = player.location;
  let best;
  let bestD = Infinity;
  for (const z of list) {
    if (z.dim !== player.dimension.id) continue;
    const d = Math.hypot(z.x + 0.5 - l.x, z.y - l.y, z.z + 0.5 - l.z);
    if (d < bestD) {
      bestD = d;
      best = z;
    }
  }
  if (!best || bestD > range) return `§c${range}칸 안에 사냥터가 없습니다. §7/mobzone 으로 목록을 보세요.`;
  let n = 0;
  try {
    for (const e of player.dimension.getEntities({ type: monsterId(best.kind), tags: [tagOf(best)] })) {
      e.remove();
      n++;
    }
  } catch {
    // 무시
  }
  list.splice(list.indexOf(best), 1);
  dueAt.delete(best.id);
  save();
  return `§6[사냥터] §f${best.id} (${MONSTERS[best.kind].name}) 를 지우고 몬스터 ${n}마리를 치웠습니다.`;
}

/** /mobzone: 목록 */
export function zoneListText() {
  const list = load();
  if (!list.length) return "§6[사냥터] §7없음 §8— /mobzone 종류 수 (예: /mobzone gulon 5) 로 내 발밑에 만듭니다";
  const lines = [`§6[사냥터] §f${list.length}곳 §7(잡으면 ${Math.round(HUNT_ZONE.respawn / 20)}초 뒤 다시 나옴)`];
  for (const z of list) {
    let dim;
    try {
      dim = world.getDimension(z.dim);
    } catch {
      dim = undefined;
    }
    const m = MONSTERS[z.kind];
    lines.push(`§e${z.id} §fLv.${m.level} ${m.name} ×${z.count} §7${z.dim.replace("minecraft:", "")} ${z.x} ${z.y} ${z.z}${dim ? ` §8(지금 ${aliveIn(dim, z)}마리)` : ""}`);
  }
  return lines.join("\n");
}

/** 1초마다: 사람이 가까운 사냥터를 채움 */
function zoneTick() {
  const now = system.currentTick;
  for (const z of load()) {
    let dim;
    try {
      dim = world.getDimension(z.dim);
    } catch {
      continue;
    }
    let near = [];
    try {
      near = dim.getPlayers({ location: centerOf(z), maxDistance: HUNT_ZONE.active });
    } catch {
      near = [];
    }
    if (!near.length || !areaLoaded(dim, z)) continue;
    const members = membersOf(dim, z);
    if (members.length >= z.count) {
      trimExtra(z, members);
      dueAt.delete(z.id);
      continue;
    }
    const due = dueAt.get(z.id);
    if (due === undefined) {
      dueAt.set(z.id, now + HUNT_ZONE.respawn);
      continue;
    }
    if (now < due) continue;
    try {
      spawnOne(dim, z);
    } catch (e) {
      console.warn(`[rpg] hunt zone ${z.id}: ${e}`);
    }
    dueAt.set(z.id, now + HUNT_ZONE.respawn);
  }
}

/** 사냥터 몬스터 수 (불러와진 것, 주변 radius 칸) — /mob clear 안내용 */
export function zoneMonstersNear(player, radius = 48) {
  let n = 0;
  for (const kind of Object.keys(MONSTERS)) {
    try {
      n += player.dimension.getEntities({ type: monsterId(kind), location: player.location, maxDistance: radius }).filter((e) => e.getTags().some((t) => t.startsWith("rpg_zone_"))).length;
    } catch {
      // 무시
    }
  }
  return n;
}

export function registerHuntZones() {
  // 지운 사냥터의 몬스터 (그때 불러와지지 않아 남은 것) 가 불러와지면 치움
  world.afterEvents.entityLoad?.subscribe((ev) => {
    const e = ev.entity;
    let tag;
    try {
      tag = e.getTags().find((t) => t.startsWith("rpg_zone_"));
    } catch {
      return;
    }
    if (!tag || load().some((z) => tagOf(z) === tag)) return;
    system.run(() => {
      try {
        if (e.isValid) e.remove();
      } catch {
        // 무시
      }
    });
  });
  system.runInterval(() => {
    try {
      zoneTick();
    } catch (e) {
      console.warn(`[rpg] hunt zones: ${e}`);
    }
  }, 20);
}
