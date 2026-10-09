import { BlockPermutation, system, world } from "@minecraft/server";
import { ENDER } from "./config.js";
import { warn } from "./fx.js";

/**
 * v2.10.0 의 임시 엔더 상자 정리 (v2.11.0~).
 * v2.10.0 은 스탯 창 칸을 누르면 눈앞에 진짜 엔더 상자를 잠깐 놓았음 (v2.11.0 부터는 엔더 상자 창 — enderui.js).
 * 그때 놓인 채 월드를 닫은 상자가 남아 있을 수 있어서:
 * - 세계 동적 속성 rpg:ender_tmp:차원:x:y:z (상자마다 하나) 를 읽어 그 자리가 아직 엔더 상자면 원래 블록 (상태까지) 으로 되돌리고 기록을 지움.
 *   다른 블록으로 바뀌었으면 기록만 지움. 불러오지 않은 곳이면 ENDER.retryEvery 틱마다 다시
 * - 치우기 전까지는 부술 수 없고 (흑요석 · 엔더 상자를 얻지 못하게) 폭발에도 남음. 그 칸에 엔더 상자가 아닌 블록이 놓이면 (이미 없어짐) 기록만 지움
 */

const PREFIX = "rpg:ender_tmp:";
const AIR = "minecraft:air";

/** @typedef {{o: string, d: string, x: number, y: number, z: number, t: string, s: Record<string, string | number | boolean>}} Rec  o 주인 · d 차원 · t s 원래 블록과 상태 */
/** 치워야 하는 상자 @type {Rec[]} */
let pending = [];
let loaded = false;

const keyOf = (r) => `${PREFIX}${r.d}:${r.x}:${r.y}:${r.z}`;

function unpersist(r) {
  try {
    world.setDynamicProperty(keyOf(r), undefined);
  } catch (e) {
    console.warn(`[rpg] ender unsave: ${e}`);
  }
}

/** 처음 쓸 때 한 번: 남은 기록을 모두 치울 목록으로. 세계를 아직 읽을 수 없으면 (불러오는 중) 다음에 다시. 읽었으면 true */
function ensureLoaded() {
  if (loaded) return true;
  let ids;
  try {
    ids = world.getDynamicPropertyIds().filter((k) => k.startsWith(PREFIX));
  } catch {
    return false;
  }
  loaded = true;
  for (const k of ids) {
    let r;
    try {
      const raw = world.getDynamicProperty(k);
      r = typeof raw === "string" ? JSON.parse(raw) : undefined;
    } catch {
      r = undefined;
    }
    if (r && typeof r.d === "string" && [r.x, r.y, r.z].every(Number.isFinite) && keyOf(r) === k) pending.push(r);
    else {
      try {
        world.setDynamicProperty(k, undefined); // 읽을 수 없는 기록
      } catch {
        // 무시
      }
    }
  }
  return true;
}

const same = (r, dimId, l) => r.d === dimId && r.x === Math.floor(l.x) && r.y === Math.floor(l.y) && r.z === Math.floor(l.z);

/** 이 자리가 아직 치우지 못한 임시 엔더 상자인지 */
export function isTempChestAt(dimId, loc) {
  ensureLoaded();
  return pending.some((r) => same(r, dimId, loc));
}

/** 치우지 못하고 남은 상자 수 */
export function enderPendingCount() {
  ensureLoaded();
  return pending.length;
}

/** 원래 블록 (상태까지). 못 만들면 상태 없이 → 그래도 안 되면 공기 */
function prevPerm(r) {
  try {
    return BlockPermutation.resolve(r.t || AIR, r.s ?? {});
  } catch {
    try {
      return BlockPermutation.resolve(r.t || AIR);
    } catch {
      return BlockPermutation.resolve(AIR);
    }
  }
}

/** 그 칸의 지금 상태: "ours" (엔더 상자) · "gone" (다른 블록) · "unknown" (불러오지 않은 곳) */
function cellState(r) {
  try {
    const b = world.getDimension(r.d).getBlock({ x: r.x, y: r.y, z: r.z });
    if (!b) return "unknown";
    return b.typeId === ENDER.block ? "ours" : "gone";
  } catch {
    return "unknown";
  }
}

/** 상자를 치우고 원래 블록으로 (그 자리가 아직 엔더 상자일 때만). 끝났으면 (치움 · 이미 없음) 기록도 지우고 true, 불러오지 않은 곳이면 false */
function retire(r) {
  const st = cellState(r);
  if (st === "unknown") return false;
  if (st === "ours") {
    try {
      const b = world.getDimension(r.d).getBlock({ x: r.x, y: r.y, z: r.z });
      if (!b) return false;
      try {
        b.setPermutation(prevPerm(r));
      } catch (e) {
        console.warn(`[rpg] ender restore: ${e}`);
        b.setType(AIR);
      }
    } catch {
      return false;
    }
  }
  unpersist(r);
  return true;
}

/** 그 자리의 기록을 지움 (블록은 건드리지 않음) */
function dropRecordsAt(dimId, loc) {
  pending = pending.filter((r) => {
    if (!same(r, dimId, loc)) return true;
    unpersist(r);
    return false;
  });
}

function tick() {
  if (!ensureLoaded() || !pending.length) return;
  if (system.currentTick % ENDER.retryEvery >= ENDER.checkEvery) return;
  pending = pending.filter((r) => !retire(r));
}

/** 시험용: 스크립트를 다시 불러온 것처럼 (기억을 비움 → 다음 확인 때 세계 동적 속성에서 다시 읽음) */
export function forgetEnderState() {
  pending = [];
  loaded = false;
}

export function registerEnderCleanup() {
  // 치우기 전에는 부술 수 없음 (흑요석 · 엔더 상자를 얻지 못하게, 크리에이티브 포함)
  world.beforeEvents.playerBreakBlock.subscribe((ev) => {
    try {
      const b = ev.block;
      if (!isTempChestAt(b.dimension.id, b.location)) return;
      ev.cancel = true;
      const pl = ev.player;
      system.run(() => {
        if (pl?.isValid) warn(pl, `§d예전에 잠깐 불러온 ${ENDER.name}는 부술 수 없습니다 §7(곧 사라짐)`);
      });
    } catch (e) {
      console.warn(`[rpg] ender break: ${e}`);
    }
  });
  // 기록된 칸에 엔더 상자가 아닌 블록이 놓임 = 상자가 이미 없음 → 기록만 지움 (나중에 그 블록을 덮지 않게)
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    try {
      const b = ev.block;
      if (b.typeId === ENDER.block) return;
      if (isTempChestAt(b.dimension.id, b.location)) dropRecordsAt(b.dimension.id, b.location);
    } catch (e) {
      console.warn(`[rpg] ender place event: ${e}`);
    }
  });
  // 폭발 (크리퍼 · TNT) 에도 남음
  world.beforeEvents.explosion.subscribe((ev) => {
    try {
      if (!ensureLoaded() || !pending.length) return;
      const list = ev.getImpactedBlocks();
      const dimId = ev.dimension.id;
      const keep = list.filter((b) => !isTempChestAt(dimId, b.location));
      if (keep.length !== list.length) ev.setImpactedBlocks(keep);
    } catch (e) {
      console.warn(`[rpg] ender explosion: ${e}`);
    }
  });
  system.runInterval(tick, ENDER.checkEvery);
}
