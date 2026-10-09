import { system, world } from "@minecraft/server";

/**
 * 틱킹 영역 (v2.12.0 초월 보스): 큰 보스 필드 (수르트 반지름 44칸) 를 짓는 동안·싸우는 동안 붙잡음.
 * 모바일의 시뮬레이션 거리 (4청크 = 64칸) 밖으로 나가는 곳이 있어서, 사람이 한쪽 끝에 있으면 반대쪽 블록·보스가 멈추거나 불러와지지 않을 수 있음.
 * - /tickingarea add <네모> <이름> true (미리 불러오기). 이름 "rpg_ba_" + 종류 + 시각 (한 월드에 10개까지 — 게임 제한)
 * - 쓰는 중인 이름은 월드에 기록 (rpg:ba_areas): 월드를 나가 남은 것은 다음에 불러올 때 치움 (cleanupAreas)
 * - 못 만들면 (10개가 참 등) undefined: 짓기·싸움은 지금 불러온 것만으로 그대로 함
 */
const KEY = "rpg:ba_areas";
const PREFIX = "rpg_ba_";
let seq = 0;

/** 쓰는 중인 틱킹 영역 기록 */
function note(dimId, name, on) {
  try {
    const v = world.getDynamicProperty(KEY);
    /** @type {[string, string][]} */
    let list = typeof v === "string" ? JSON.parse(v) : [];
    if (!Array.isArray(list)) list = [];
    list = list.filter((a) => Array.isArray(a) && a[1] !== name);
    if (on) list.push([dimId, name]);
    world.setDynamicProperty(KEY, list.length ? JSON.stringify(list) : undefined);
  } catch {
    // 무시
  }
}

/** 네모 (가운데 ± half 칸) 의 가로 범위 */
function box(center, half) {
  return { x0: Math.floor(center.x - half), x1: Math.floor(center.x + half), z0: Math.floor(center.z - half), z1: Math.floor(center.z + half), y: Math.floor(center.y) };
}

/**
 * 가운데 ± half 칸 네모를 틱킹 영역으로 (청크 100개 제한: half 48 이면 7×7 = 49청크). 만든 이름 (못 만들면 undefined)
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} center
 * @param {number} half
 * @param {string} [tag] 종류 (영문 소문자·숫자: b = 짓기, f = 싸움)
 */
export function holdArea(dim, center, half, tag = "x") {
  const t = String(tag).replace(/[^a-z0-9]/g, "").slice(0, 4) || "x";
  /** @type {string | undefined} */
  let name = `${PREFIX}${t}${Date.now().toString(36)}${(++seq).toString(36)}`;
  const b = box(center, half);
  try {
    const res = dim.runCommand(`tickingarea add ${b.x0} ${b.y} ${b.z0} ${b.x1} ${b.y} ${b.z1} ${name} true`);
    if (res && res.successCount === 0) throw new Error("tickingarea add failed");
    note(dim.id, name, true);
  } catch {
    name = undefined;
  }
  return name;
}

/** 틱킹 영역 치우기 (없으면 그대로) */
export function releaseArea(dim, name) {
  if (!name) return;
  try {
    dim.runCommand(`tickingarea remove ${name}`);
  } catch {
    // 이미 없음
  }
  note(dim.id, name, false);
}

/** 네모에 걸친 청크가 모두 불러와졌는지 (청크마다 한 칸씩 봄) */
export function areaLoaded(dim, center, half) {
  const b = box(center, half);
  for (let cx = Math.floor(b.x0 / 16); cx <= Math.floor(b.x1 / 16); cx++) {
    for (let cz = Math.floor(b.z0 / 16); cz <= Math.floor(b.z1 / 16); cz++) {
      const at = { x: Math.min(b.x1, Math.max(b.x0, cx * 16 + 8)), y: b.y, z: Math.min(b.z1, Math.max(b.z0, cz * 16 + 8)) };
      try {
        if (!dim.isChunkLoaded(at)) return false;
      } catch {
        return false;
      }
    }
  }
  return true;
}

/**
 * 월드를 불러올 때: 지난번에 남은 틱킹 영역 (rpg_ba_…) 의 기록을 바로 떼어 내고 (그 뒤에 새로 만든 것은 건드리지 않게) delay 틱 뒤 치움
 * @returns {number} 치울 영역 수
 */
export function cleanupAreas(delay = 20) {
  let list;
  try {
    const v = world.getDynamicProperty(KEY);
    list = typeof v === "string" ? JSON.parse(v) : [];
  } catch {
    list = [];
  }
  if (!Array.isArray(list) || !list.length) return 0;
  try {
    world.setDynamicProperty(KEY, undefined);
  } catch {
    // 무시
  }
  const stale = list.filter((a) => Array.isArray(a) && typeof a[0] === "string" && typeof a[1] === "string" && /^rpg_ba_[a-z0-9]+$/.test(a[1]));
  system.runTimeout(() => {
    for (const a of stale) {
      try {
        world.getDimension(a[0]).runCommand(`tickingarea remove ${a[1]}`);
      } catch {
        // 이미 없음
      }
    }
  }, delay);
  return stale.length;
}
