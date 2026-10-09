import { BlockPermutation, BlockTypes, BlockVolume, GameMode, ItemStack, PlayerPermissionLevel, system, world } from "@minecraft/server";
import { BOSS, BOSSES, altarId } from "./config.js";
import { MenuForm } from "./forms.js";
import { notify, play, warn } from "./fx.js";
import { fx } from "./skills.js";
import { areaLoaded, holdArea, releaseArea } from "./ticking.js";

/**
 * 보스 제단과 보스 필드 (v1.8.0)
 * - 제단 블록(rpg:altar_<보스>)을 놓으면 제단 목록(월드 동적 속성 rpg:altars)에 넣고, 놓은 운영자에게 "보스 필드 만들기" 확인 창
 *   → 만들기: 보스마다 정한 반지름·높이 안의 지형을 신전(테마)으로 바꿈. 몇 초에 걸쳐 단계별로 (땅 고르기 → 기초 → 바닥 → 울타리·기둥 → 장식)
 *   → 나중에: 제단만 (필드 없이도 보스는 소환됨). 크리에이티브에서 웅크리고 제단을 터치하거나 /boss build 로 다시 물음
 * - 필드 안 블록은 운영자·크리에이티브가 아니면 부수거나 놓을 수 없음 (놓으면 되돌리고 아이템을 돌려줌). 물·용암·불도 못 놓고, 폭발도 필드 블록은 부수지 않음
 * - 제단이 부서지면 목록에서 빠짐 (필드 블록은 그대로)
 * 좌표: 제단 블록 자리 (x, y, z) = 필드 가운데. 바닥은 y - 1 층, 플레이어는 y 층에 섬
 */

const KEY = "rpg:altars";
const PLAYER = "minecraft:player";

/**
 * @typedef {{key: string, dim: string, x: number, y: number, z: number, built?: boolean, cooldownUntil?: number}} Altar
 */

/** @type {Altar[] | undefined} */
let cache;

/** 제단 목록 (월드에 저장) */
export function altars() {
  if (!cache) {
    cache = [];
    try {
      const raw = world.getDynamicProperty(KEY);
      if (typeof raw === "string") {
        const list = JSON.parse(raw);
        if (Array.isArray(list)) cache = list.filter((a) => a && typeof a.key === "string" && BOSSES[a.key]);
      }
    } catch {
      cache = [];
    }
  }
  return cache;
}

export function saveAltars() {
  try {
    world.setDynamicProperty(KEY, JSON.stringify(altars()));
  } catch (e) {
    console.warn(`[rpg] altar save: ${e}`);
  }
}

/** 제단 블록 ID → 보스 키 (제단이 아니면 undefined) */
export function altarKeyOf(typeId) {
  if (typeof typeId !== "string" || !typeId.startsWith("rpg:altar_")) return undefined;
  const k = typeId.slice("rpg:altar_".length);
  return BOSSES[k] ? k : undefined;
}

const same = (a, dim, l) => a.dim === dim && a.x === Math.floor(l.x) && a.y === Math.floor(l.y) && a.z === Math.floor(l.z);

/** 이 블록 자리의 제단 */
export function altarAt(dim, loc) {
  return altars().find((a) => same(a, dim, loc));
}

export function addAltar(key, dim, loc) {
  const found = altarAt(dim, loc);
  if (found) return found;
  /** @type {Altar} */
  const a = { key, dim, x: Math.floor(loc.x), y: Math.floor(loc.y), z: Math.floor(loc.z), built: false, cooldownUntil: 0 };
  altars().push(a);
  saveAltars();
  return a;
}

/** @type {((a: Altar) => void) | undefined} */
let onRemoved;
/** 제단이 없어질 때 (보스 정리) */
export function setAltarRemovedHook(fn) {
  onRemoved = fn;
}

export function removeAltar(a) {
  const list = altars();
  const i = list.indexOf(a);
  if (i < 0) return;
  list.splice(i, 1);
  saveAltars();
  try {
    onRemoved?.(a);
  } catch (e) {
    console.warn(`[rpg] altar removed hook: ${e}`);
  }
}

/** 필드 크기 (보스 설정) */
export function arenaSize(a) {
  const ar = BOSSES[a.key].arena;
  return { radius: ar.radius, height: ar.height };
}

/** 가운데 (제단 블록 위 가운데) */
export function arenaCenter(a) {
  return { x: a.x + 0.5, y: a.y, z: a.z + 0.5 };
}

/** loc 이 필드 안인지 (가로 반지름 + margin, 높이는 바닥 3칸 아래 ~ 천장) */
export function inArena(a, dimId, loc, margin = 0) {
  if (a.dim !== dimId) return false;
  const { radius, height } = arenaSize(a);
  const c = arenaCenter(a);
  if (loc.y < a.y - 3 || loc.y > a.y + height + 1) return false;
  return Math.hypot(loc.x - c.x, loc.z - c.z) <= radius + margin;
}

/** 이 블록 자리(정수 좌표)를 품은 지어진 필드 (보호용: 울타리 고리까지, 칸 가운데로 잼. 위로는 끝까지 — 위에서 붓는 용암·모래 등) */
export function builtArenaAt(dimId, loc) {
  const x = Math.floor(loc.x) + 0.5;
  const z = Math.floor(loc.z) + 0.5;
  return altars().find((a) => {
    if (!a.built || a.dim !== dimId || loc.y < a.y - 4) return false;
    const c = arenaCenter(a);
    return Math.hypot(x - c.x, z - c.z) <= arenaSize(a).radius + 2.1;
  });
}

export function canManage(player) {
  try {
    return player.playerPermissionLevel === PlayerPermissionLevel.Operator || player.getGameMode() === GameMode.Creative;
  } catch {
    return false;
  }
}

// ------------------------------------------------------------ 테마 (설계)
/**
 * 칠하기: (dx, dz, r, deg) → 블록 이름 | [이름, 상태] | null(그대로). dx·dz 는 제단 기준 블록 칸, r = 칸 가운데까지 거리, deg = 각도 (+x 에서 +z 쪽으로)
 * @typedef {string | [string, Record<string, string | number | boolean>] | null} Paint
 * @typedef {{dy: number, paint: (dx: number, dz: number, r: number, deg: number) => Paint}} Layer
 * @typedef {{dx: number, dy: number, dz: number, block: string, states?: Record<string, string | number | boolean>}} Put
 * @typedef {{name: string, layers?: Layer[], puts?: Put[], sound?: string}} Stage
 * @typedef {{stages: Stage[], features: {totems: {dx: number, dz: number}[], decoys: {dx: number, dz: number}[], gates: {dx: number, dz: number}[], posts: {dx: number, dz: number}[], pillars?: {dx: number, dz: number, half: number}[], holes?: {dx: number, dz: number}[], tile?: number}}} Plan
 */

/** 제단 위치로 정해지는 난수 (같은 자리면 같은 장식) */
function seeded(a) {
  // >>> 0: XOR 결과가 음수여도 양수 씨앗으로 (v1.9.0 고침: 음수면 0~1 밖의 값이 나와 장식이 울타리·담장 자리에 놓였음)
  let s = ((Math.abs(a.x * 73856093) ^ Math.abs(a.y * 19349663) ^ Math.abs(a.z * 83492791)) >>> 0) % 2147483647 || 7;
  return () => (s = (s * 16807) % 2147483647) / 2147483647;
}

/** 가운데를 보게 하는 호박 방향 (dx, dz = 제단 기준) */
function facingCenter(dx, dz) {
  if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "west" : "east";
  return dz > 0 ? "north" : "south";
}

const DEG = (dx, dz) => ((Math.atan2(dz, dx) * 180) / Math.PI + 360) % 360;
const polar = (r, deg) => ({ dx: Math.round(r * Math.cos((deg * Math.PI) / 180)), dz: Math.round(r * Math.sin((deg * Math.PI) / 180)) });

/**
 * 허수아비 킹: 수확이 끝난 밭의 신전. 가운데 짙은 참나무 단(제단) + 짚단 고리, 성문 쪽 십자 흙길, 갈아엎은 밭 줄무늬(회백토·거친 흙),
 * 대각선 네 곳 허수아비 토템(짚단 2 + 잭오랜턴 머리 + 울타리 팔, 둘레 짚단 원 = 수확의 낫 안전지대), 가장자리 석재 벽돌 고리,
 * 짙은 참나무 울타리(높이 2, 네 방향 3칸 성문) + 기둥 8개(원목 3 + 잭오랜턴), 울타리 안쪽 짚단·호박 장식
 */
function harvest(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const totems = [45, 135, 225, 315].map((d) => polar(9, d));
  const decoys = [0, 90, 180, 270].map((d) => polar(8, d));
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const nearTotem = (dx, dz, k) => totems.some((t) => Math.hypot(dx - t.dx, dz - t.dz) <= k);
  const onPath = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  // 울타리 칸: 반지름 R+0.5 원 바로 바깥 칸 중 (대각선 포함) 안쪽 칸과 닿는 칸 → 끊기지 않고 이어지는 고리 (성문 네 곳만 빔)
  const inner = (dx, dz) => Math.hypot(dx, dz) <= R + 0.5;
  const fenceCell = (dx, dz) => !inner(dx, dz) && !onPath(dx, dz) && [-1, 0, 1].some((a) => [-1, 0, 1].some((b) => inner(dx + a, dz + b)));
  const ringCells = [];
  for (let dx = -Math.ceil(R + 2); dx <= Math.ceil(R + 2); dx++) for (let dz = -Math.ceil(R + 2); dz <= Math.ceil(R + 2); dz++) if (fenceCell(dx, dz)) ringCells.push({ dx, dz });
  // 기둥: 울타리 고리 위 (그 각도에 가장 가까운 칸)
  const posts = [22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5].map((d) => {
    const want = polar(R + 1, d);
    let best = ringCells[0];
    for (const c of ringCells) if (Math.hypot(c.dx - want.dx, c.dz - want.dz) < Math.hypot(best.dx - want.dx, best.dz - want.dz)) best = c;
    return best;
  });
  const EDGE = R + 2; // 바닥·땅 고르기가 닿는 반지름 (울타리 고리까지)
  /** @type {Put[]} */
  const struct = [];
  for (const p of posts) {
    for (let dy = 0; dy <= 2; dy++) struct.push({ dx: p.dx, dy, dz: p.dz, block: "minecraft:dark_oak_log" });
    struct.push({ dx: p.dx, dy: 3, dz: p.dz, block: "minecraft:lit_pumpkin", states: { "minecraft:cardinal_direction": facingCenter(p.dx, p.dz) } });
  }
  for (const t of totems) {
    struct.push({ dx: t.dx, dy: 0, dz: t.dz, block: "minecraft:hay_block" });
    struct.push({ dx: t.dx, dy: 1, dz: t.dz, block: "minecraft:hay_block" });
    struct.push({ dx: t.dx, dy: 2, dz: t.dz, block: "minecraft:lit_pumpkin", states: { "minecraft:cardinal_direction": facingCenter(t.dx, t.dz) } });
  }
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  for (let n = 0, tries = 0; n < 18 && tries < 400; tries++) {
    const deg = rand() * 360;
    const r = R - 1.5 - rand() * 2;
    const p = polar(r, deg);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || onPath(p.dx, p.dz) || nearTotem(p.dx, p.dz, 3.5)) continue;
    taken.add(k);
    const pumpkin = n % 3 === 2;
    deco.push(pumpkin ? { dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:pumpkin", states: { "minecraft:cardinal_direction": facingCenter(p.dx, p.dz) } } : { dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:hay_block" });
    n++;
  }
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.grass" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:dirt" : null) },
          { dy: -2, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:coarse_dirt" : null) },
        ],
        sound: "dig.gravel",
      },
      {
        name: "바닥",
        layers: [
          {
            dy: -1,
            paint: (dx, dz, r) => {
              if (r > EDGE) return null;
              if (r > R) return "minecraft:mossy_stone_bricks";
              if (r > R - 1) return "minecraft:stone_bricks";
              if (r <= 2.5) return "minecraft:dark_oak_planks";
              if (r <= 3.5) return "minecraft:hay_block";
              if (nearTotem(dx, dz, 2.4)) return "minecraft:hay_block";
              if (onPath(dx, dz)) return "minecraft:grass_path";
              return ((dz % 4) + 4) % 4 < 2 ? "minecraft:podzol" : "minecraft:coarse_dirt";
            },
          },
        ],
        sound: "dig.wood",
      },
      {
        name: "울타리·기둥",
        layers: [0, 1].map((dy) => ({ dy, paint: (dx, dz) => (fenceCell(dx, dz) ? "minecraft:dark_oak_fence" : null) })),
        puts: struct,
        sound: "dig.wood",
      },
      { name: "장식", puts: deco, sound: "block.lantern.place" },
    ],
    features: { totems, decoys, gates, posts },
  };
}

/** 울타리 고리 칸 (반지름 R+0.5 원 바로 바깥, 대각선으로도 안쪽과 닿는 칸 = 끊기지 않는 고리). gap(dx, dz) 인 칸(성문)은 뺌 */
function ringOf(R, gap) {
  const inner = (dx, dz) => Math.hypot(dx, dz) <= R + 0.5;
  const cell = (dx, dz) => !inner(dx, dz) && !gap(dx, dz) && [-1, 0, 1].some((i) => [-1, 0, 1].some((j) => inner(dx + i, dz + j)));
  const cells = [];
  for (let dx = -Math.ceil(R + 2); dx <= Math.ceil(R + 2); dx++) for (let dz = -Math.ceil(R + 2); dz <= Math.ceil(R + 2); dz++) if (cell(dx, dz)) cells.push({ dx, dz });
  return { cell, cells };
}

/** 그 각도에 가장 가까운 고리 칸 */
function nearestCell(cells, want) {
  let best = cells[0];
  for (const c of cells) if (Math.hypot(c.dx - want.dx, c.dz - want.dz) < Math.hypot(best.dx - want.dx, best.dz - want.dz)) best = c;
  return best;
}

/**
 * 사막 전갈 + 바위 전갈: 모래와 바위가 반씩인 사막 신전. 서쪽(-x) = 사막 전갈의 모래 (모래·사암·붉은 모래),
 * 동쪽(+x) = 바위 전갈의 바위 (돌·안산암·자갈·조약돌), 경계에 가까울수록 서로 섞임.
 * 남북으로 붉은 사암 길 (두 땅의 경계선), 동서로 매끄러운 사암·윤나는 안산암 길. 가운데 단: 두 재료 반반 + 붉은 사암 고리.
 * 가장자리 고리 (서쪽 깎은 사암·동쪽 석재 벽돌), 담장 (서쪽 사암 담장·동쪽 조약돌 담장, 높이 1 = 넘을 수 없음),
 * 네 성문: 양쪽 기둥 + 위 들보 + 매단 랜턴, 대각선 네 곳 오벨리스크 (꼭대기 랜턴). 장식은 가장자리에만 (서쪽 마른 덤불·뼈 화석·해골, 동쪽 이끼 낀 바위)
 */
function desert(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onPath = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const sand = (dx) => dx < 0;
  /** 칸마다 정해진 난수 0~1 (같은 제단 자리면 같은 값) */
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  /** 부드럽게 이어지는 무늬 (-3~3): 사암·안산암·자갈 조각 */
  const patch = (dx, dz, k) => Math.sin(dx * 0.62 + k) + Math.sin(dz * 0.55 + k * 2.1) + Math.sin((dx - dz) * 0.37 + k * 0.7);
  const { cell: wallCell, cells: ring } = ringOf(R, onPath);
  // 성문 양쪽 기둥: 고리 칸 중 성문 길 바로 옆 (길에서 2칸)
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const s of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? s : 0), dz: gz * (R + 1) + (gx !== 0 ? s : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  const obelisks = [45, 135, 225, 315].map((d) => nearestCell(ring, polar(R + 1, d)));
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...obelisks];
  /** 재료: 서쪽 모래 / 동쪽 바위 (남북 성문은 경계라 붉은 사암) */
  const M = (dx, s, r) => (dx === 0 ? "minecraft:cut_red_sandstone" : sand(dx) ? s : r);
  /** @type {Put[]} */
  const struct = [];
  for (const f of flanks) {
    for (let dy = 0; dy <= 3; dy++) struct.push({ dx: f.dx, dy, dz: f.dz, block: dy === 0 || dy === 3 ? M(f.dx, "minecraft:chiseled_sandstone", "minecraft:chiseled_stone_bricks") : M(f.dx, "minecraft:cut_sandstone", "minecraft:stone_bricks") });
  }
  // 들보: 두 기둥 위를 잇는 5칸 + 가운데 매단 랜턴
  DIRS.forEach(([gx, gz], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    for (let k = lo; k <= hi; k++) {
      const dx = gx !== 0 ? at : k;
      const dz = gx !== 0 ? k : at;
      struct.push({ dx, dy: 4, dz, block: gi % 2 === 1 ? "minecraft:cut_red_sandstone" : M(dx, "minecraft:cut_sandstone", "minecraft:stone_bricks") });
    }
    const mid = (lo + hi) / 2;
    struct.push({ dx: gx !== 0 ? at : Math.round(mid), dy: 3, dz: gx !== 0 ? Math.round(mid) : at, block: "minecraft:lantern", states: { hanging: true } });
  });
  for (const o of obelisks) {
    const s = sand(o.dx);
    const body = s ? ["minecraft:chiseled_sandstone", "minecraft:cut_sandstone", "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone"] : ["minecraft:chiseled_stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks"];
    body.forEach((block, dy) => struct.push({ dx: o.dx, dy, dz: o.dz, block }));
    struct.push({ dx: o.dx, dy: body.length, dz: o.dz, block: "minecraft:lantern", states: { hanging: false } });
  }
  // 장식: 가장자리 띠에만 (싸우는 가운데는 비워 둠), 길·성문·기둥 둘레 제외
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  for (let n = 0, tries = 0; n < 26 && tries < 600; tries++) {
    const deg = rand() * 360;
    const r = R - 1.5 - rand() * 2.5;
    const p = polar(r, deg);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || onPath(p.dx, p.dz) || Math.abs(p.dx) <= 3 || nearPost(p.dx, p.dz) || Math.hypot(p.dx, p.dz) > R - 1) continue;
    taken.add(k);
    const pick = rand();
    if (sand(p.dx)) {
      if (pick < 0.55) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:deadbush" });
      else if (pick < 0.8) {
        // 뼈 화석: 뼈 블록 누운 것 2칸 (가운데 쪽으로 누움)
        const axis = Math.abs(p.dx) > Math.abs(p.dz) ? "x" : "z";
        const step = axis === "x" ? { dx: Math.sign(p.dx) || 1, dz: 0 } : { dx: 0, dz: Math.sign(p.dz) || 1 };
        const k2 = `${p.dx + step.dx},${p.dz + step.dz}`;
        if (taken.has(k2) || onPath(p.dx + step.dx, p.dz + step.dz) || Math.hypot(p.dx + step.dx, p.dz + step.dz) > R - 1) continue;
        taken.add(k2);
        deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:bone_block", states: { pillar_axis: axis } });
        deco.push({ dx: p.dx + step.dx, dy: 0, dz: p.dz + step.dz, block: "minecraft:bone_block", states: { pillar_axis: axis } });
      } else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:skeleton_skull", states: { facing_direction: 1 } });
    } else if (pick < 0.6) {
      // 이끼 낀 바위: 한 칸 또는 위로 한 칸 더
      deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:mossy_cobblestone" });
      if (rand() < 0.4) deco.push({ dx: p.dx, dy: 1, dz: p.dz, block: "minecraft:cobblestone" });
    } else if (pick < 0.85) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:andesite" });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:deadbush" }); // 바위 틈 마른 덤불 (바닥이 모래가 아니면 놓이지 않으므로 아래에서 모래로)
    n++;
  }
  // 마른 덤불 아래는 모래 (바위 쪽이어도): 바닥 칠하기에서 이 칸들을 모래로
  const bushSoil = new Set(deco.filter((d) => d.block === "minecraft:deadbush").map((d) => `${d.dx},${d.dz}`));
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    const s = sand(dx);
    if (r > R) return s ? "minecraft:cut_sandstone" : "minecraft:stone_bricks";
    if (r > R - 1) return s ? "minecraft:smooth_sandstone" : "minecraft:polished_andesite";
    if (r <= 3.5) {
      if (r > 2.5) return "minecraft:cut_red_sandstone";
      if (dx === 0) return "minecraft:chiseled_red_sandstone";
      return s ? "minecraft:cut_sandstone" : "minecraft:polished_andesite";
    }
    if (Math.abs(dx) <= 1) return "minecraft:cut_red_sandstone"; // 두 땅의 경계선
    if (Math.abs(dz) <= 1) return s ? "minecraft:smooth_sandstone" : "minecraft:polished_andesite";
    if (bushSoil.has(`${dx},${dz}`)) return "minecraft:sand";
    const h = hash(dx, dz);
    // 경계 가까이 (2~5칸): 서로의 재료가 섞임
    const mix = Math.max(0, (6 - Math.abs(dx)) / 12);
    if (hash(dx, dz, 3) < mix) return s ? (h < 0.5 ? "minecraft:cobblestone" : "minecraft:gravel") : "minecraft:sand";
    if (s) {
      if (patch(dx, dz, 1.3) > 1.5) return "minecraft:sandstone";
      if (h < 0.02) return "minecraft:red_sand";
      return "minecraft:sand";
    }
    if (patch(dx, dz, 2.7) > 1.2) return "minecraft:andesite";
    if (patch(dx, dz, 4.1) < -1.6) return "minecraft:gravel";
    if (h < 0.08) return "minecraft:cobblestone";
    return "minecraft:stone";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.sand" },
      {
        name: "기초",
        layers: [-3, -2].map((dy) => ({ dy, paint: (dx, _dz, r) => (r <= EDGE ? (sand(dx) ? "minecraft:sandstone" : "minecraft:stone") : null) })),
        sound: "dig.stone",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "dig.sand" },
      {
        name: "담장·기둥",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? (sand(dx) ? "minecraft:sandstone_wall" : "minecraft:cobblestone_wall") : null) }],
        puts: struct,
        sound: "dig.stone",
      },
      { name: "장식", puts: deco, sound: "block.lantern.place" },
    ],
    features: { totems: [], decoys: [], gates, posts },
  };
}

/**
 * 크라켄 (v2.0.0): 바다에서 떠오른 가라앉은 신전 (프리즈머린). 가운데 짙은 프리즈머린 단 + 바다 랜턴 고리 (크라켄이 앉는 곳),
 * 네 방향 길(프리즈머린 벽돌) 위 12칸에 촉수 물구덩이 3×3 (물 1칸 깊이, 둘레 산호 블록 — 물에 닿아 살아 있음, 모서리 바다 랜턴),
 * 대각선 10칸에 기둥 4개 (3×3, 높이 6, 꼭대기 바다 랜턴 = 해일을 피하는 그림자), 짙은 프리즈머린 고리 무늬(8·16칸), 모래·자갈 퇴적,
 * 가장자리 고리 + 프리즈머린 담장 (네 성문: 양쪽 기둥 + 들보 + 매단 영혼 랜턴), 대각선 담장 기둥 (꼭대기 바다 랜턴),
 * 장식은 가장자리에만 (죽은 산호 블록·부채, 젖은 스펀지, 바다 피클, 무너진 프리즈머린)
 */
function sunken(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onPath = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.58 + k) + Math.sin(dz * 0.51 + k * 2.1) + Math.sin((dx + dz) * 0.33 + k * 0.7);
  // 촉수 물구덩이 (길 위, 가운데에서 12칸) · 기둥 (대각선 10칸, 3×3)
  const holes = [0, 90, 180, 270].map((d) => polar(12, d));
  const pillars = [45, 135, 225, 315].map((d) => ({ ...polar(10, d), half: 1.5 }));
  const cheb = (dx, dz, c) => Math.max(Math.abs(dx - c.dx), Math.abs(dz - c.dz));
  const inHole = (dx, dz) => holes.some((h) => cheb(dx, dz, h) <= 1);
  const rimOf = (dx, dz) => holes.find((h) => cheb(dx, dz, h) === 2);
  const nearPillar = (dx, dz, k) => pillars.some((p) => cheb(dx, dz, p) <= k);
  const { cell: wallCell, cells: ring } = ringOf(R, onPath);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const s of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? s : 0), dz: gz * (R + 1) + (gx !== 0 ? s : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  const towers = [45, 135, 225, 315].map((d) => nearestCell(ring, polar(R + 1, d)));
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...towers];
  /** @type {Put[]} */
  const struct = [];
  for (const f of flanks) for (let dy = 0; dy <= 3; dy++) struct.push({ dx: f.dx, dy, dz: f.dz, block: dy === 0 || dy === 3 ? "minecraft:dark_prismarine" : "minecraft:prismarine_bricks" });
  // 들보 (두 기둥 위) + 가운데 매단 영혼 랜턴 (푸른 빛)
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    for (let k = lo; k <= hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 4, dz: gx !== 0 ? k : at, block: k === lo || k === hi ? "minecraft:dark_prismarine" : "minecraft:prismarine_bricks" });
    const mid = Math.round((lo + hi) / 2);
    struct.push({ dx: gx !== 0 ? at : mid, dy: 3, dz: gx !== 0 ? mid : at, block: "minecraft:soul_lantern", states: { hanging: true } });
  });
  for (const t of towers) {
    ["minecraft:dark_prismarine", "minecraft:prismarine_bricks", "minecraft:prismarine_bricks", "minecraft:dark_prismarine"].forEach((block, dy) => struct.push({ dx: t.dx, dy, dz: t.dz, block }));
    struct.push({ dx: t.dx, dy: 4, dz: t.dz, block: "minecraft:sea_lantern" });
  }
  // 기둥: 밑동·머리 짙은 프리즈머린, 몸통 프리즈머린 벽돌 (모서리는 프리즈머린), 꼭대기 가운데 바다 랜턴
  for (const p of pillars) {
    for (let i = -1; i <= 1; i++) {
      for (let j = -1; j <= 1; j++) {
        const corner = i !== 0 && j !== 0;
        for (let dy = 0; dy <= 5; dy++) {
          const block = dy === 0 || dy === 5 ? "minecraft:dark_prismarine" : corner ? "minecraft:prismarine" : "minecraft:prismarine_bricks";
          struct.push({ dx: p.dx + i, dy, dz: p.dz + j, block });
        }
      }
    }
    struct.push({ dx: p.dx, dy: 6, dz: p.dz, block: "minecraft:sea_lantern" });
  }
  // 장식: 가장자리 띠에만 (싸우는 가운데·길·기둥·물구덩이 둘레 제외)
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  const CORALS = ["tube", "brain", "bubble", "fire", "horn"];
  for (let n = 0, tries = 0; n < 30 && tries < 700; tries++) {
    const deg = rand() * 360;
    const r = R - 1.5 - rand() * 2.5;
    const p = polar(r, deg);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || onPath(p.dx, p.dz) || Math.abs(p.dx) <= 3 || Math.abs(p.dz) <= 3 || nearPost(p.dx, p.dz) || nearPillar(p.dx, p.dz, 3) || Math.hypot(p.dx, p.dz) > R - 1) continue;
    taken.add(k);
    const pick = rand();
    const coral = CORALS[Math.floor(rand() * CORALS.length)];
    if (pick < 0.3) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: `minecraft:dead_${coral}_coral_fan`, states: { coral_fan_direction: rand() < 0.5 ? 0 : 1 } });
    else if (pick < 0.55) {
      // 죽은 산호 덩어리: 한 칸 또는 위로 한 칸 더
      deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: `minecraft:dead_${coral}_coral_block` });
      if (rand() < 0.35) deco.push({ dx: p.dx, dy: 1, dz: p.dz, block: `minecraft:dead_${CORALS[Math.floor(rand() * CORALS.length)]}_coral_block` });
    } else if (pick < 0.7) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:wet_sponge" });
    else if (pick < 0.85) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:sea_pickle", states: { cluster_count: Math.floor(rand() * 4), dead_bit: true } });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: rand() < 0.5 ? "minecraft:prismarine" : "minecraft:dark_prismarine" }); // 무너진 돌
    n++;
  }
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (inHole(dx, dz)) return "minecraft:water";
    if (r > R) return "minecraft:dark_prismarine";
    if (r > R - 1) return "minecraft:prismarine_bricks";
    const rim = rimOf(dx, dz);
    if (rim) {
      // 물구덩이 둘레: 모서리 바다 랜턴 (물에 닿지 않음), 나머지는 산호 블록 (옆 칸이 물이라 살아 있음)
      if (Math.abs(dx - rim.dx) === 2 && Math.abs(dz - rim.dz) === 2) return "minecraft:sea_lantern";
      return `minecraft:${CORALS[Math.floor(hash(dx, dz, 5) * CORALS.length)]}_coral_block`;
    }
    if (r <= 2.5) return "minecraft:dark_prismarine";
    if (r <= 3.5) return "minecraft:prismarine_bricks";
    if (r <= 4.5) {
      // 크라켄 둘레 빛나는 고리: 촉수 사이 여덟 곳 바다 랜턴
      const deg = DEG(dx, dz);
      return Math.abs(((deg + 22.5) % 45) - 22.5) < 6 ? "minecraft:sea_lantern" : "minecraft:dark_prismarine";
    }
    if (onPath(dx, dz)) return "minecraft:prismarine_bricks";
    if ((r > 7.5 && r <= 8.5) || (r > 15.5 && r <= 16.5)) return "minecraft:dark_prismarine"; // 고리 무늬
    const h = hash(dx, dz);
    if (patch(dx, dz, 1.7) > 1.45) return h < 0.3 ? "minecraft:gravel" : "minecraft:sand"; // 모래 퇴적
    if (h < 0.06) return "minecraft:dark_prismarine";
    if (h < 0.1) return "minecraft:prismarine_bricks";
    return "minecraft:prismarine";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.stone" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:prismarine" : null) },
          { dy: -2, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:dark_prismarine" : null) },
        ],
        sound: "dig.stone",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "bucket.empty_water" },
      {
        name: "담장·기둥",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:prismarine_wall" : null) }],
        puts: struct,
        sound: "dig.stone",
      },
      { name: "장식", puts: deco, sound: "dig.coral" },
    ],
    features: { totems: [], decoys: [], gates, posts, pillars, holes },
  };
}

/** 거대 웜 필드의 체크무늬 한 칸 크기 (칸) */
export function deepTileSize(key = "great_worm") {
  return BOSSES[key]?.quake?.tile ?? 3;
}

/**
 * 체크무늬 칸 (v2.1.0 심층의 신전): 제단 기준 블록 칸 (dx, dz) → 칸 번호 (i, j), 어두운 칸이면 dark.
 * 가운데 칸 = 제단을 가운데로 한 size×size (크기 3이면 dx -1~1). 대지 분출 판정과 바닥 칠하기가 같은 식을 씀
 */
export function deepTile(dx, dz, size = 3) {
  const h = Math.floor(size / 2);
  const i = Math.floor((dx + h) / size);
  const j = Math.floor((dz + h) / size);
  return { i, j, dark: (((i + j) % 2) + 2) % 2 === 0 };
}

/** 거대 웜의 굴 입구 (제단 기준 블록 칸): 가운데 + 대각선 넷 (체크무늬 어두운 칸의 가운데) */
function deepHoles(size) {
  const k = 3 * size;
  return [
    { dx: 0, dz: 0 },
    { dx: k, dz: k },
    { dx: -k, dz: k },
    { dx: -k, dz: -k },
    { dx: k, dz: -k },
  ];
}

/**
 * 거대 웜 (v2.1.0): 심층의 신전. 바닥 = 3×3칸 체크무늬 (어두운 심층암 타일 / 밝은 윤나는 응회암) — 대지 분출이 한쪽 칸씩 솟음 (담장 밖 둘레까지 같은 무늬),
 * 굴 입구 5곳 (가운데 + 대각선): 파헤쳐진 흙 칸 (진흙투성이 맹그로브 뿌리: 어두운 칸 색을 지키면서 굴이 눈에 띔), 가운데는 진흙 (가운데 굴은 제단). 가장자리 심층암 벽돌 담장 (높이 1),
 * 네 성문 = 양쪽 기둥 + 들보 + 매단 영혼 랜턴, 대각선 담장 탑 (꼭대기 영혼 랜턴).
 * 장식은 가장자리에만 (종유석 석순, 자수정 무리, 켜진 양초, 스컬크, 무너진 심층암)
 */
function deep(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const size = deepTileSize(a.key);
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onGate = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const holes = deepHoles(size);
  const h = Math.floor(size / 2);
  const holeOf = (dx, dz) => holes.find((o) => Math.abs(dx - o.dx) <= h && Math.abs(dz - o.dz) <= h);
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  const towers = [45, 135, 225, 315].map((d) => nearestCell(ring, polar(R + 1, d)));
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...towers];
  /** @type {Put[]} */
  const struct = [];
  for (const f of flanks) {
    ["minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:chiseled_deepslate"].forEach((block, dy) => struct.push({ dx: f.dx, dy, dz: f.dz, block }));
  }
  // 들보 (두 기둥 위) + 가운데 매단 영혼 랜턴
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    for (let k = lo; k <= hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 4, dz: gx !== 0 ? k : at, block: k === lo || k === hi ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles" });
    const mid = Math.round((lo + hi) / 2);
    struct.push({ dx: gx !== 0 ? at : mid, dy: 3, dz: gx !== 0 ? mid : at, block: "minecraft:soul_lantern", states: { hanging: true } });
  });
  for (const t of towers) {
    ["minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:chiseled_deepslate"].forEach((block, dy) => struct.push({ dx: t.dx, dy, dz: t.dz, block }));
    struct.push({ dx: t.dx, dy: 4, dz: t.dz, block: "minecraft:soul_lantern", states: { hanging: false } });
  }
  // 장식: 가장자리 띠에만 (싸우는 가운데·성문 길·굴·담장 기둥 둘레 제외)
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  const CANDLES = ["minecraft:gray_candle", "minecraft:black_candle", "minecraft:candle"];
  for (let n = 0, tries = 0; n < 30 && tries < 700; tries++) {
    const deg = rand() * 360;
    const r = R - 1.5 - rand() * 2.5;
    const p = polar(r, deg);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || Math.abs(p.dx) <= 2 || Math.abs(p.dz) <= 2 || nearPost(p.dx, p.dz) || holeOf(p.dx, p.dz) || Math.hypot(p.dx, p.dz) > R - 1) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.3) {
      // 석순: 점적석 블록 + 뾰족한 점적석 1~2칸 (위로)
      deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:dripstone_block" });
      if (rand() < 0.5) {
        deco.push({ dx: p.dx, dy: 1, dz: p.dz, block: "minecraft:pointed_dripstone", states: { dripstone_thickness: "frustum", hanging: false } });
        deco.push({ dx: p.dx, dy: 2, dz: p.dz, block: "minecraft:pointed_dripstone", states: { dripstone_thickness: "tip", hanging: false } });
      } else deco.push({ dx: p.dx, dy: 1, dz: p.dz, block: "minecraft:pointed_dripstone", states: { dripstone_thickness: "tip", hanging: false } });
    } else if (pick < 0.5) {
      // 자수정: 자수정 블록 + 위로 자란 무리 (빛남)
      deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:amethyst_block" });
      deco.push({ dx: p.dx, dy: 1, dz: p.dz, block: "minecraft:amethyst_cluster", states: { "minecraft:block_face": "up" } });
    } else if (pick < 0.7) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: CANDLES[Math.floor(rand() * CANDLES.length)], states: { candles: Math.floor(rand() * 4), lit: true } });
    else if (pick < 0.85) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:sculk" });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: rand() < 0.5 ? "minecraft:cobbled_deepslate" : "minecraft:cracked_deepslate_bricks" }); // 무너진 돌
    n++;
  }
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    const o = holeOf(dx, dz);
    if (o) return dx === o.dx && dz === o.dz && (dx !== 0 || dz !== 0) ? "minecraft:mud" : ["minecraft:muddy_mangrove_roots", { pillar_axis: "y" }];
    return deepTile(dx, dz, size).dark ? "minecraft:deepslate_tiles" : "minecraft:polished_tuff";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.stone" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? ["minecraft:deepslate", { pillar_axis: "y" }] : null) },
          { dy: -2, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:cobbled_deepslate" : null) },
        ],
        sound: "dig.deepslate",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "place.deepslate" },
      {
        name: "담장·기둥",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:deepslate_brick_wall" : null) }],
        puts: struct,
        sound: "dig.deepslate_bricks",
      },
      { name: "장식", puts: deco, sound: "place.amethyst_block" },
    ],
    features: { totems: [], decoys: [], gates, posts, holes, tile: size },
  };
}

/** 뱀의 섬 치수 (v2.2.0 요르문간드): 섬 반지름·해자 바깥 반지름·범람 때 안전한 흰 고리 반지름 (설정 BOSSES.<키>.arena) */
export function isleSize(key = "jormungandr") {
  const ar = BOSSES[key]?.arena ?? {};
  return { island: ar.island ?? 13, moat: ar.moat ?? 18.5, safe: ar.safe ?? 7 };
}

/**
 * 요르문간드 (v2.2.0): 뱀의 섬. 가운데 섬(윤나는 안산암) = 가운데 단(조각된 석재 벽돌 + 청록 구리 고리, 제단), 석재 벽돌 고리 무늬,
 * 흰 석영 고리(반지름 safe = 독해 범람 때 안전한 곳), 대각선 넷 청록 구리 줄(비늘 무늬), 이끼 낀 석재 벽돌 물가.
 * 섬 둘레 독의 해자 (물 1칸 깊이, 바닥 진흙·점토·자갈 + 초록 개구리불: 독처럼 빛남, 수련 몇 장). 다리 없음.
 * 해자 밖 둑 (이끼 낀 석재 벽돌·금 간 석재 벽돌) + 이끼 낀 석재 벽돌 담장 (높이 1), 네 성문 = 양쪽 기둥 + 들보(청록 구리) + 매단 랜턴,
 * 대각선 담장 탑 (꼭대기 켜진 청록 구리 전구). 장식은 둑의 담장 쪽에만 (이끼 카펫, 뼈 블록, 이끼 낀 조약돌)
 */
function isle(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const { island: ISL, moat: MOAT, safe: SAFE } = isleSize(a.key);
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onGate = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.61 + k) + Math.sin(dz * 0.47 + k * 2.3) + Math.sin((dx - dz) * 0.37 + k * 0.9);
  const inMoat = (r) => r > ISL && r <= MOAT;
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  const towers = [45, 135, 225, 315].map((d) => nearestCell(ring, polar(R + 1, d)));
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...towers];
  const PILLAR = ["minecraft:mossy_stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks"];
  /** @type {Put[]} */
  const struct = [];
  for (const f of flanks) PILLAR.forEach((block, dy) => struct.push({ dx: f.dx, dy, dz: f.dz, block }));
  // 들보 (두 기둥 위: 끝은 이끼 낀 석재 벽돌, 가운데 청록 구리) + 가운데 매단 랜턴
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    for (let k = lo; k <= hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 4, dz: gx !== 0 ? k : at, block: k === lo || k === hi ? "minecraft:mossy_stone_bricks" : "minecraft:waxed_oxidized_cut_copper" });
    const mid = Math.round((lo + hi) / 2);
    struct.push({ dx: gx !== 0 ? at : mid, dy: 3, dz: gx !== 0 ? mid : at, block: "minecraft:lantern", states: { hanging: true } });
  });
  for (const t of towers) {
    PILLAR.forEach((block, dy) => struct.push({ dx: t.dx, dy, dz: t.dz, block }));
    struct.push({ dx: t.dx, dy: 4, dz: t.dz, block: "minecraft:waxed_oxidized_copper_bulb", states: { lit: true, powered_bit: false } });
  }
  // 장식: 둑의 담장 쪽 (반지름 R-1 ~ R, 성문 길·담장 기둥 둘레 제외) + 해자에 수련 몇 장
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  for (let n = 0, tries = 0; n < 22 && tries < 700; tries++) {
    const deg = rand() * 360;
    const p = polar(R - rand() * 0.9, deg);
    const r = Math.hypot(p.dx, p.dz);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || r <= MOAT + 0.6 || r > R || Math.abs(p.dx) <= 2 || Math.abs(p.dz) <= 2 || nearPost(p.dx, p.dz)) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.55) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:moss_carpet" });
    else if (pick < 0.8) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:bone_block", states: { pillar_axis: rand() < 0.5 ? "x" : "z" } }); // 먹힌 것들의 뼈
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:mossy_cobblestone" }); // 무너진 돌
    n++;
  }
  for (let n = 0, tries = 0; n < 10 && tries < 400; tries++) {
    const p = polar(ISL + 1 + rand() * (MOAT - ISL - 1.5), rand() * 360);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || !inMoat(Math.hypot(p.dx, p.dz)) || onGate(p.dx, p.dz)) continue;
    taken.add(k);
    deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:waterlily" });
    n++;
  }
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (r > R) return "minecraft:mossy_cobblestone";
    if (r > MOAT) return hash(dx, dz, 3) < 0.25 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks"; // 둑
    if (inMoat(r)) return "minecraft:water";
    if (r > ISL - 1) return "minecraft:mossy_stone_bricks"; // 물가
    if (r <= 1.5) return "minecraft:chiseled_stone_bricks"; // 가운데 단 (제단)
    if (r <= 2.5) return "minecraft:waxed_oxidized_cut_copper";
    if (r > SAFE - 1 && r <= SAFE) return ["minecraft:smooth_quartz", { pillar_axis: "y" }]; // 범람 때 안전한 흰 고리 (칸 바깥 가장자리 약 SAFE + 0.7 까지 안전)
    if ((r > 3.5 && r <= 4.5) || (r > 9.5 && r <= 10.5)) return "minecraft:stone_bricks";
    // 대각선 넷 청록 구리 줄 (폭 약 2칸, 고리 사이)
    if (r > 4.5 && Math.min(Math.abs(dx - dz), Math.abs(dx + dz)) <= 1) return "minecraft:waxed_oxidized_cut_copper";
    const h = hash(dx, dz);
    if (patch(dx, dz, 2.1) > 1.5) return h < 0.5 ? "minecraft:andesite" : "minecraft:mossy_stone_bricks"; // 이끼·거친 돌 조각
    return h < 0.08 ? "minecraft:cracked_stone_bricks" : "minecraft:polished_andesite";
  };
  /** 해자 바닥: 진흙·점토·자갈 조각 + 초록 개구리불 (독처럼 빛남) */
  const bed = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (!inMoat(r)) return r > MOAT ? "minecraft:mossy_cobblestone" : "minecraft:andesite";
    if (hash(dx, dz, 7) < 0.07) return ["minecraft:verdant_froglight", { pillar_axis: "y" }];
    const p = patch(dx, dz, 4.2);
    return p > 1.1 ? "minecraft:clay" : p < -1.2 ? "minecraft:gravel" : "minecraft:mud";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.stone" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:tuff" : null) },
          { dy: -2, paint: bed },
        ],
        sound: "dig.gravel",
      },
      { name: "바닥·해자", layers: [{ dy: -1, paint: floor }], sound: "bucket.empty_water" },
      {
        name: "담장·기둥",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:mossy_stone_brick_wall" : null) }],
        puts: struct,
        sound: "dig.stone",
      },
      { name: "장식", puts: deco, sound: "dig.grass" },
    ],
    features: { totems: [], decoys: [], gates, posts },
  };
}

/** 세계수의 뿌리 치수 (v2.3.0 니드호그): 뿌리가 담장 탑에서 나오는 높이(arch)·땅에 닿는 가운데에서의 거리(ground) (설정 BOSSES.<키>.arena) */
export function rootsSize(key = "nidhogg") {
  const ar = BOSSES[key]?.arena ?? {};
  return { arch: ar.arch ?? 8, ground: ar.ground ?? 12 };
}

/** 뿌리 하나의 가운데 줄 (s = 0 담장 탑 ~ 1 땅에 닿음 ~ 1.25 땅속): 가운데에서의 거리 r, 각도(도), 아랫면 높이 b, 굵기(반지름) rho */
export function rootAt(R, arch, ground, deg, turn, s) {
  const r = R + 1 - s * (R + 1 - ground);
  const b = s <= 1 ? arch * Math.pow(1 - s, 1.4) : -(s - 1) * 8;
  return { r, deg: deg + turn * s, b, rho: 1.7 - 0.4 * Math.min(1, s) };
}

/**
 * 니드호그 (v2.3.0): 세계수의 뿌리. 가운데 수액 샘(깎은 송진 벽돌 + 송진 벽돌 + 버섯불 고리 = 제단), 대각선 네 줄로 흐르는 수액(버섯불 줄기),
 * 뿌리 흙·이끼·창백한 이끼·진흙·회백토가 섞인 바닥, 검은 돌 가장자리. 대각선 네 담장 탑에서 창백한 참나무 뿌리가 필드로 휘어 들어와
 * 가운데에서 ground 칸 거리에서 땅속으로 파고듦 (담장 쪽은 아래로 지나갈 수 있는 아치, 뿌리 밑에 늘어진 뿌리·창백한 이끼, 군데군데 빛나는 수액).
 * 윤나는 흑암 벽돌 담장 (높이 1), 네 성문 = 양쪽 기둥 + 들보(가운데 우는 흑요석) + 매단 영혼 랜턴. 장식은 둑의 담장 쪽에만 (뼈 더미·해골: 시체의 해안)
 */
function roots(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const { arch: ARCH, ground: GROUND } = rootsSize(a.key);
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onGate = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.53 + k) + Math.sin(dz * 0.49 + k * 2.1) + Math.sin((dx + dz) * 0.31 + k * 0.7);
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  const ROOT_DEG = [45, 135, 225, 315];
  const towers = ROOT_DEG.map((d) => nearestCell(ring, polar(R + 1, d)));
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...towers];
  /** @type {Put[]} */
  const struct = [];
  const PILLAR = ["minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone"];
  for (const f of flanks) PILLAR.forEach((block, dy) => struct.push({ dx: f.dx, dy, dz: f.dz, block }));
  // 들보 (끝은 윤나는 흑암 벽돌, 가운데 우는 흑요석) + 가운데 매단 영혼 랜턴
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    const mid = Math.round((lo + hi) / 2);
    for (let k = lo; k <= hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 4, dz: gx !== 0 ? k : at, block: k === mid ? "minecraft:crying_obsidian" : "minecraft:polished_blackstone_bricks" });
    struct.push({ dx: gx !== 0 ? at : mid, dy: 3, dz: gx !== 0 ? mid : at, block: "minecraft:soul_lantern", states: { hanging: true } });
  });
  // 세계수의 뿌리: 탑 자리에서 곧게 올라온 줄기(arch + 2 칸) → 필드 쪽으로 휘어 내려와 ground 칸 거리에서 땅속으로
  /** @type {Map<string, Put>} */
  const rootCells = new Map();
  const putRoot = (dx, dy, dz, block) => {
    const k = `${dx},${dy},${dz}`;
    if (dy < -1 || dy > H || (dx === 0 && dz === 0)) return;
    if (!rootCells.has(k)) rootCells.set(k, { dx, dy, dz, block });
  };
  ROOT_DEG.forEach((deg, i) => {
    const turn = i % 2 ? -14 : 14;
    const t = towers[i];
    for (let dy = -1; dy <= ARCH + 2; dy++) {
      for (const [ox, oz] of [
        [0, 0],
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
      ]) {
        putRoot(t.dx + ox, dy, t.dz + oz, hash(t.dx + ox, t.dz + oz, dy) < 0.12 ? "minecraft:dark_oak_wood" : "minecraft:pale_oak_wood");
      }
    }
    for (let s = 0; s <= 1.25; s += 0.004) {
      const q = rootAt(R, ARCH, GROUND, deg, turn, s);
      const cx = q.r * Math.cos((q.deg * Math.PI) / 180);
      const cz = q.r * Math.sin((q.deg * Math.PI) / 180);
      const cy = q.b + q.rho;
      const span = Math.ceil(q.rho);
      for (let dx = Math.floor(cx - span); dx <= Math.ceil(cx + span); dx++) {
        for (let dz = Math.floor(cz - span); dz <= Math.ceil(cz + span); dz++) {
          for (let dy = Math.floor(cy - span); dy <= Math.ceil(cy + span); dy++) {
            if (Math.hypot(dx - cx, dz - cz, dy - cy) > q.rho + 0.15) continue;
            const glow = hash(dx, dz, dy + 11) < 0.05 && dy >= 0;
            putRoot(dx, dy, dz, glow ? "minecraft:shroomlight" : hash(dx, dz, dy) < 0.1 ? "minecraft:dark_oak_wood" : "minecraft:pale_oak_wood");
          }
        }
      }
    }
  });
  // 뿌리 밑에 늘어진 뿌리·창백한 이끼 (아래가 비어 있는 뿌리 칸 밑)
  /** @type {Put[]} */
  const hang = [];
  for (const c of rootCells.values()) {
    if (c.dy < 3 || rootCells.has(`${c.dx},${c.dy - 1},${c.dz}`) || Math.hypot(c.dx, c.dz) > R) continue;
    const h = hash(c.dx, c.dz, c.dy + 5);
    if (h < 0.22) hang.push({ dx: c.dx, dy: c.dy - 1, dz: c.dz, block: "minecraft:hanging_roots" });
    else if (h < 0.34) hang.push({ dx: c.dx, dy: c.dy - 1, dz: c.dz, block: "minecraft:pale_hanging_moss", states: { tip: true } });
  }
  const rootList = [...rootCells.values()];
  const rootAtCell = new Set(rootList.filter((c) => c.dy === -1).map((c) => `${c.dx},${c.dz}`));
  // 장식: 둑의 담장 쪽 (반지름 R-1.5 ~ R, 성문 길·기둥·뿌리 둘레 제외) — 시체의 해안 (뼈 더미, 해골)
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  const nearRoot = (dx, dz) => rootList.some((c) => c.dy <= 1 && Math.hypot(dx - c.dx, dz - c.dz) < 1.5);
  for (let n = 0, tries = 0; n < 20 && tries < 700; tries++) {
    const deg = rand() * 360;
    const p = polar(R - rand() * 1.2, deg);
    const r = Math.hypot(p.dx, p.dz);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || r > R || r < R - 1.6 || Math.abs(p.dx) <= 2 || Math.abs(p.dz) <= 2 || nearPost(p.dx, p.dz) || nearRoot(p.dx, p.dz)) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.55) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:bone_block", states: { pillar_axis: rand() < 0.5 ? "x" : "z" } });
    else if (pick < 0.8) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:skeleton_skull", states: { facing_direction: 1 } });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:pale_moss_carpet" });
    n++;
  }
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (r > R) return hash(dx, dz, 3) < 0.25 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks"; // 둑
    if (r > R - 1) return "minecraft:polished_blackstone"; // 가장자리
    if (r <= 1.5) return "minecraft:chiseled_resin_bricks"; // 수액 샘 (제단)
    if (r <= 3) return "minecraft:resin_bricks";
    if (r <= 3.8) return "minecraft:shroomlight";
    // 수액 줄기: 대각선 넷 (가운데 → 뿌리가 땅에 닿는 곳)
    const d = Math.min(Math.abs(dx - dz), Math.abs(dx + dz));
    if (r < GROUND - 0.5 && (d === 0 || (d === 1 && hash(dx, dz, 9) < 0.35))) return "minecraft:shroomlight";
    const h = hash(dx, dz);
    const pa = patch(dx, dz, 1.3);
    const pb = patch(dx, dz, 4.1);
    if (pa > 1.4) return h < 0.55 ? "minecraft:moss_block" : "minecraft:pale_moss_block";
    if (pb > 1.5) return "minecraft:mud";
    if (pb < -1.6) return "minecraft:podzol";
    return h < 0.12 ? "minecraft:coarse_dirt" : "minecraft:dirt_with_roots";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.grass" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:deepslate" : null) },
          { dy: -2, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:dirt" : null) },
        ],
        sound: "dig.gravel",
      },
      { name: "바닥", layers: [{ dy: -1, paint: (dx, dz, r) => (rootAtCell.has(`${dx},${dz}`) ? null : floor(dx, dz, r)) }], sound: "dig.grass" },
      {
        name: "담장·뿌리",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) && !rootCells.has(`${dx},0,${dz}`) ? "minecraft:polished_blackstone_brick_wall" : null) }],
        puts: [...struct, ...rootList],
        sound: "dig.wood",
      },
      { name: "장식", puts: [...hang, ...deco], sound: "block.lantern.place" },
    ],
    features: { totems: [], decoys: [], gates, posts },
  };
}

/** 해와 달의 돌 고리 치수 (v2.4.0 스콜과 하티): 가운데 해·달 원판 반지름(disc)·선돌까지의 거리(stones) (설정 BOSSES.<키>.arena) */
export function stonesSize(key = "skoll_hati") {
  const ar = BOSSES[key]?.arena ?? {};
  return { disc: ar.disc ?? 7, stones: ar.stones ?? 19.5 };
}

/** 선돌 8개의 각도 (도, 성문 사이) */
export const STONE_DEG = [22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5];

/**
 * 원판의 어느 쪽인지 (v2.4.0 해와 달의 심판): 서 있는 칸(제단 칸 기준 dx, dz)으로 봄 — 칠해진 그대로.
 * 칸 가운데가 disc 안인 칸 (hypot(dx, dz) ≤ disc) 이 원판: dx ≥ 1 = 해(금빛, 동쪽 +x), dx ≤ -1 = 달(푸른, 서쪽 -x), dx = 0 = 경계 (흰 줄), 그 밖 = 없음.
 * at = 서 있는 자리 (월드), center = 필드 가운데 (월드, 제단 칸 가운데)
 * @returns {"sun" | "moon" | "line" | undefined}
 */
export function discSide(at, center, disc) {
  const dx = Math.floor(at.x) - Math.floor(center.x);
  const dz = Math.floor(at.z) - Math.floor(center.z);
  if (Math.hypot(dx, dz) > disc) return undefined;
  return dx >= 1 ? "sun" : dx <= -1 ? "moon" : "line";
}

/**
 * 스콜과 하티 (v2.4.0): 해와 달의 돌 고리. 눈 덮인 들판 (눈·방해석·돌 조각), 가운데 해와 달의 원판 (반지름 disc:
 * 동쪽 반원 = 해 — 노란·주황 콘크리트 햇살 + 금 블록 해 + 버섯불, 서쪽 반원 = 달 — 검정·파랑 콘크리트 밤하늘 + 흰 초승달 + 바다 랜턴 별,
 * 가운데 남북 한 줄 = 석영 경계, 테두리 윤나는 응회암), 성문 네 길(응회암), 가장자리 응회암 벽돌 담장 (높이 1) + 성문 4 (응회암 벽돌 기둥 + 들보 + 매단 랜턴),
 * 성문 사이 선돌 8개 (가운데에서 stones 칸, 폭 2·높이 5, 동쪽 넷은 버섯불 눈 = 해, 서쪽 넷은 진줏빛 개구리불 눈 = 달). 장식은 담장 쪽 띠에만 (눈 더미·뼈·해골)
 */
function stones(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const { disc: DISC, stones: SR } = stonesSize(a.key);
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onGate = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.47 + k) + Math.sin(dz * 0.53 + k * 2.3) + Math.sin((dx - dz) * 0.29 + k * 0.9);
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  /** @type {Put[]} */
  const struct = [];
  // 성문: 양쪽 기둥 (응회암 벽돌 3 + 깎은 응회암 벽돌) + 들보 (가운데 깎은 응회암) + 매단 랜턴
  for (const f of flanks) {
    for (let dy = 0; dy <= 2; dy++) struct.push({ dx: f.dx, dy, dz: f.dz, block: "minecraft:tuff_bricks" });
    struct.push({ dx: f.dx, dy: 3, dz: f.dz, block: "minecraft:chiseled_tuff_bricks" });
  }
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    const mid = Math.round((lo + hi) / 2);
    for (let k = lo; k <= hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 4, dz: gx !== 0 ? k : at, block: k === mid ? "minecraft:chiseled_tuff" : "minecraft:polished_tuff" });
    struct.push({ dx: gx !== 0 ? at : mid, dy: 3, dz: gx !== 0 ? mid : at, block: "minecraft:lantern", states: { hanging: true } });
  });
  // 선돌 8개: 가운데에서 SR 칸, 둘레 방향으로 폭 2, 높이 5 (응회암 3 + 윤나는 응회암 + 깎은 응회암 벽돌 머리), 가운데 쪽 한 칸에 빛나는 눈 (동쪽 = 해, 서쪽 = 달)
  /** @type {{dx: number, dz: number, sun: boolean}[]} */
  const stoneList = [];
  /** @type {Set<string>} */
  const stoneCells = new Set();
  for (const deg of STONE_DEG) {
    const rad = (deg * Math.PI) / 180;
    const b = polar(SR, deg);
    const t = { dx: Math.round(-Math.sin(rad)), dz: Math.round(Math.cos(rad)) };
    const cells = [b, { dx: b.dx + t.dx, dz: b.dz + t.dz }];
    const sun = Math.cos(rad) > 0;
    stoneList.push({ dx: b.dx, dz: b.dz, sun });
    cells.forEach((c, i) => {
      stoneCells.add(`${c.dx},${c.dz}`);
      for (let dy = 0; dy <= 2; dy++) {
        const eye = dy === 2 && i === 0;
        struct.push({ dx: c.dx, dy, dz: c.dz, block: eye ? (sun ? "minecraft:shroomlight" : "minecraft:pearlescent_froglight") : "minecraft:tuff" });
      }
      struct.push({ dx: c.dx, dy: 3, dz: c.dz, block: "minecraft:polished_tuff" });
      struct.push({ dx: c.dx, dy: 4, dz: c.dz, block: "minecraft:chiseled_tuff_bricks" });
    });
  }
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...stoneList.map((s) => ({ dx: s.dx, dz: s.dz }))];
  // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·선돌 둘레 제외) — 눈 더미, 뼈, 해골 (늑대의 사냥터)
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  for (let n = 0, tries = 0; n < 22 && tries < 700; tries++) {
    const deg = rand() * 360;
    const p = polar(R - rand() * 1.2, deg);
    const r = Math.hypot(p.dx, p.dz);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || r > R || r < R - 1.6 || Math.abs(p.dx) <= 2 || Math.abs(p.dz) <= 2 || nearPost(p.dx, p.dz)) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.5) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:snow_layer", states: { height: rand() < 0.5 ? 1 : 2, covered_bit: false } });
    else if (pick < 0.78) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:bone_block", states: { pillar_axis: rand() < 0.5 ? "x" : "z" } });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:skeleton_skull", states: { facing_direction: 1 } });
    n++;
  }
  // 원판: 동쪽 반원 = 해 (가운데 해 원반 + 햇살 + 버섯불), 서쪽 반원 = 달 (밤하늘 + 초승달 + 별)
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const discBlock = (dx, dz, r) => {
    if (dx === 0) return "minecraft:smooth_quartz"; // 경계 (남북 한 줄)
    if (r > DISC - 0.8) return dx > 0 ? "minecraft:raw_gold_block" : "minecraft:lapis_block"; // 테두리
    if (dx > 0) {
      const sr = Math.hypot(dx - DISC * 0.5, dz);
      if (sr <= DISC * 0.34) return sr <= 1 ? "minecraft:shroomlight" : "minecraft:gold_block";
      const ang = ((Math.atan2(dz, dx - DISC * 0.5) * 180) / Math.PI + 360) % 30;
      if (ang < 9) return hash(dx, dz, 5) < 0.18 ? "minecraft:shroomlight" : "minecraft:orange_concrete";
      return "minecraft:yellow_concrete";
    }
    // 달: 초승달 = 큰 원 - 옆으로 비킨 원
    const mx = -DISC * 0.5;
    const inA = Math.hypot(dx - mx, dz) <= DISC * 0.36;
    const inB = Math.hypot(dx - (mx + DISC * 0.16), dz + DISC * 0.08) <= DISC * 0.3;
    if (inA && !inB) return "minecraft:white_concrete";
    if (dx <= -2 && hash(dx, dz, 7) < 0.07) return "minecraft:sea_lantern"; // 별 (가운데 흰 줄 곁에는 없음: 줄이 넓어 보이지 않게)
    return r > DISC * 0.6 ? "minecraft:blue_concrete" : "minecraft:black_concrete";
  };
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (r > R) return hash(dx, dz, 3) < 0.3 ? "minecraft:polished_tuff" : "minecraft:tuff_bricks"; // 둑
    if (r > R - 1) return "minecraft:polished_tuff"; // 가장자리
    if (r <= DISC) return discBlock(dx, dz, r);
    if (r <= DISC + 1) return "minecraft:polished_tuff"; // 원판 테두리
    if (onGate(dx, dz)) return hash(dx, dz, 2) < 0.35 ? "minecraft:polished_tuff" : "minecraft:tuff"; // 성문 길
    const h = hash(dx, dz);
    const pa = patch(dx, dz, 1.7);
    const pb = patch(dx, dz, 5.3);
    if (pa > 1.5) return h < 0.6 ? "minecraft:calcite" : "minecraft:snow";
    if (pb > 1.7) return h < 0.5 ? "minecraft:andesite" : "minecraft:stone";
    return h < 0.05 ? "minecraft:tuff" : "minecraft:snow";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.grass" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:stone" : null) },
          { dy: -2, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:dirt" : null) },
        ],
        sound: "dig.gravel",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "dig.snow" },
      {
        name: "담장·선돌",
        layers: [{ dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:tuff_brick_wall" : null) }],
        puts: struct,
        sound: "dig.stone",
      },
      { name: "장식", puts: deco, sound: "block.lantern.place" },
    ],
    features: { totems: [], decoys: [], gates, posts, stones: stoneList },
  };
}

/** 태초의 빙원 치수 (v2.5.0 이미르): 가운데 얼어붙은 호수 반지름(lake)·모닥불까지의 거리(fires)·모닥불 곁 반지름(warm) (설정 BOSSES.<키>.arena) */
export function glacierSize(key = "ymir") {
  const ar = BOSSES[key]?.arena ?? {};
  return { lake: ar.lake ?? 6, fires: ar.fires ?? 14, warm: ar.warm ?? 3.5 };
}

/** 무스펠헤임의 모닥불 넷의 각도 (도, 대각선: 북동·남동·남서·북서 — +x 에서 +z 쪽으로) */
export const FIRE_DEG = [45, 135, 225, 315];
/** 얼음 첨탑 여덟의 각도 (도, 성문과 모닥불 사이) */
export const SPIRE_DEG = [22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5];

/**
 * 이미르 (v2.5.0): 태초의 빙원. 눈밭 (눈 + 방해석·섬록암 조각 + 파란 얼음 틈 줄), 가운데 얼어붙은 호수 (반지름 lake: 하늘색 색유리 밑에 푸른 얼음,
 * 밑바닥에 바다 랜턴 — 유리 너머로 빛남, 흰 색유리 금 줄), 호수 둘레 석영 벽돌 테 + 깎은 석영 룬 여덟, 성문 네 길 (심층암 타일 + 윤나는 심층암),
 * 가장자리 단단한 얼음 (높이 2 얼음 벽: 단단한 얼음 + 푸른 얼음, 넘을 수 없음) + 성문 4 (푸른 얼음 기둥 + 들보 + 매단 영혼 랜턴),
 * 성문과 모닥불 사이 벽 위 얼음 첨탑 여덟 (필드 밖으로만, 높이 9~13), 대각선 무스펠헤임의 모닥불 넷 (가운데에서 fires 칸:
 * 5×5 검은 돌 화덕 + 가운데 윤나는 흑암 위 모닥불 + 둘레 흑암 벽돌 담장 — 모닥불에 발을 들이지 못하게). 장식은 담장 쪽 띠에만 (눈 더미·뼈 블록·해골)
 */
function glacier(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const { lake: LAKE, fires: FR } = glacierSize(a.key);
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const onGate = (dx, dz) => Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.47 + k) + Math.sin(dz * 0.53 + k * 2.3) + Math.sin((dx - dz) * 0.29 + k * 0.9);
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-2, 2]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  // 무스펠헤임의 모닥불 넷 (대각선), 화덕 = 가운데 5×5
  const fires = FIRE_DEG.map((deg) => polar(FR, deg));
  /** 모닥불 화덕 칸이면 그 모닥불 (가운데에서 dx, dz 차이) */
  const hearthOf = (dx, dz) => {
    for (const f of fires) if (Math.abs(dx - f.dx) <= 2 && Math.abs(dz - f.dz) <= 2) return { ddx: dx - f.dx, ddz: dz - f.dz };
    return undefined;
  };
  /** @type {Put[]} */
  const struct = [];
  // 성문: 양쪽 기둥 (푸른 얼음 5 + 단단한 얼음 머리) + 들보 (단단한 얼음, 가운데 푸른 얼음) + 매단 영혼 랜턴
  for (const f of flanks) {
    for (let dy = 0; dy <= 4; dy++) struct.push({ dx: f.dx, dy, dz: f.dz, block: "minecraft:blue_ice" });
    struct.push({ dx: f.dx, dy: 5, dz: f.dz, block: "minecraft:packed_ice" });
  }
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    const mid = Math.round((lo + hi) / 2);
    for (let k = lo + 1; k < hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 5, dz: gx !== 0 ? k : at, block: k === mid ? "minecraft:blue_ice" : "minecraft:packed_ice" });
    struct.push({ dx: gx !== 0 ? at : mid, dy: 4, dz: gx !== 0 ? mid : at, block: "minecraft:soul_lantern", states: { hanging: true } });
  });
  // 모닥불: 화덕 가운데 윤나는 흑암 위에 불이 켜진 모닥불 (둘레 담장은 층에서)
  for (const f of fires) {
    struct.push({ dx: f.dx, dy: 1, dz: f.dz, block: "minecraft:campfire", states: { extinguished: false, "minecraft:cardinal_direction": "south" } });
  }
  // 얼음 첨탑 여덟: 벽 고리 칸에서 밖으로만 (필드 안으로 들어오지 않게, 둑 끝까지), 아래는 넓고 위로 갈수록 좁아짐, 꼭대기 푸른 얼음
  /** @type {{dx: number, dz: number, h: number}[]} */
  const spires = [];
  SPIRE_DEG.forEach((deg, i) => {
    const c = nearestCell(ring, polar(R + 1, deg));
    const h = 9 + (i % 3) * 2;
    spires.push({ dx: c.dx, dz: c.dz, h });
    const cells = [];
    for (let ox = -2; ox <= 2; ox++) {
      for (let oz = -2; oz <= 2; oz++) {
        const x = c.dx + ox;
        const z = c.dz + oz;
        const r = Math.hypot(x, z);
        const d = Math.hypot(ox, oz);
        if (d > 1.5 || r <= R + 0.5 || r > EDGE) continue;
        cells.push({ x, z, d });
      }
    }
    for (const q of cells) {
      // 넓은 밑동 (d ≤ 1.5): h 의 40%, 가운데 줄기 (d ≤ 1): h 의 70%, 꼭대기 (가운데 칸): h
      const top = q.d < 0.5 ? h : q.d <= 1.01 ? Math.round(h * 0.7) : Math.round(h * 0.4);
      for (let dy = 0; dy < top; dy++) {
        const tip = q.d < 0.5 && dy >= h - 2;
        const vein = q.d < 0.5 ? dy % 3 === 1 : hash(q.x, q.z, dy) < 0.18;
        struct.push({ dx: q.x, dy, dz: q.z, block: tip || vein ? "minecraft:blue_ice" : "minecraft:packed_ice" });
      }
    }
  });
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...fires.map((f) => ({ dx: f.dx, dz: f.dz })), ...spires.map((s) => ({ dx: s.dx, dz: s.dz }))];
  // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·첨탑 둘레 제외) — 눈 더미, 뼈 블록 (산맥이 된 거인의 뼈), 해골
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  for (let n = 0, tries = 0; n < 24 && tries < 800; tries++) {
    const deg = rand() * 360;
    const p = polar(R - rand() * 1.2, deg);
    const r = Math.hypot(p.dx, p.dz);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || r > R || r < R - 1.6 || Math.abs(p.dx) <= 2 || Math.abs(p.dz) <= 2 || nearPost(p.dx, p.dz)) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.5) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:snow_layer", states: { height: rand() < 0.5 ? 1 : 2, covered_bit: false } });
    else if (pick < 0.8) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:bone_block", states: { pillar_axis: rand() < 0.5 ? "x" : "z" } });
    else deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:skeleton_skull", states: { facing_direction: 1 } });
    n++;
  }
  // 호수 금 줄 (가운데에서 뻗는 흰 색유리 다섯 줄) · 룬 여덟 (호수 둘레 테) · 눈밭의 파란 얼음 틈 여섯 줄 (성문 길·모닥불 화덕을 피해 구불구불)
  const cracks = [0, 1, 2, 3, 4].map((i) => ((i * 72 + rand() * 30) * Math.PI) / 180);
  const angDist = (a1, a2) => Math.abs(Math.atan2(Math.sin(a1 - a2), Math.cos(a1 - a2)));
  const isCrack = (dx, dz, r) => r >= 1.5 && cracks.some((c) => angDist(Math.atan2(dz, dx), c) * r <= 0.55);
  const rifts = [0, 1, 2, 3, 4, 5].map((i) => ({ a: ((i * 60 + 20 + rand() * 20) * Math.PI) / 180, w: 1.2 + rand() * 1.2, ph: rand() * 6 }));
  const isRift = (dx, dz, r) => {
    if (r < LAKE + 2.5 || r > R - 2.5) return false;
    const ang = Math.atan2(dz, dx);
    // 구불구불: 반지름에 따라 각도가 조금씩 흔들림
    return rifts.some((f) => angDist(ang, f.a + (Math.sin(r * 0.45 + f.ph) * f.w) / r) * r <= 0.55);
  };
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (r > R) return hash(dx, dz, 3) < 0.25 ? "minecraft:packed_ice" : "minecraft:snow"; // 둑
    if (r > R - 1) return "minecraft:packed_ice"; // 가장자리
    const hf = hearthOf(dx, dz);
    if (hf) {
      if (Math.abs(hf.ddx) === 2 && Math.abs(hf.ddz) === 2) return "minecraft:gilded_blackstone";
      return Math.abs(hf.ddx) <= 1 && Math.abs(hf.ddz) <= 1 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_bricks";
    }
    if (r <= LAKE) return isCrack(dx, dz, r) ? "minecraft:white_stained_glass" : "minecraft:light_blue_stained_glass";
    if (r <= LAKE + 1.2) {
      const rune = [0, 45, 90, 135, 180, 225, 270, 315].some((d) => angDist(Math.atan2(dz, dx), (d * Math.PI) / 180) * r <= 0.5);
      return rune ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_bricks";
    }
    if (onGate(dx, dz)) return dx === 0 || dz === 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate"; // 성문 길
    if (isRift(dx, dz, r)) return "minecraft:light_blue_concrete";
    const h = hash(dx, dz);
    const pa = patch(dx, dz, 1.7);
    const pb = patch(dx, dz, 5.3);
    if (pa > 1.5) return h < 0.6 ? "minecraft:calcite" : "minecraft:snow";
    if (pb > 1.7) return h < 0.5 ? "minecraft:diorite" : "minecraft:polished_diorite";
    return "minecraft:snow";
  };
  /** 호수 밑바닥 (색유리 밑): 푸른 얼음, 두 고리에 바다 랜턴 (유리 너머로 빛남) */
  const lakeBottom = (dx, dz, r) => {
    const ang = (Math.atan2(dz, dx) * 180) / Math.PI;
    const onRing = (rr, n, off) => Math.abs(r - rr) <= 0.5 && [...Array(n).keys()].some((i) => angDist((ang * Math.PI) / 180, ((i * 360) / n + off) * (Math.PI / 180)) * r <= 0.6);
    return onRing(2.8, 4, 45) || onRing(5, 6, 0) ? "minecraft:sea_lantern" : "minecraft:blue_ice";
  };
  /** 화덕 둘레 담장 (모닥불 가운데 칸 둘레 8칸) · 가운데 윤나는 흑암 (위에 모닥불) */
  const hearthRing = (dx, dz) => {
    const hf = hearthOf(dx, dz);
    if (!hf || Math.max(Math.abs(hf.ddx), Math.abs(hf.ddz)) > 1) return null;
    return hf.ddx === 0 && hf.ddz === 0 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_brick_wall";
  };
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.snow" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? (r <= LAKE ? "minecraft:packed_ice" : "minecraft:stone") : null) },
          { dy: -2, paint: (dx, dz, r) => (r <= EDGE ? (r <= LAKE ? lakeBottom(dx, dz, r) : "minecraft:dirt") : null) },
        ],
        sound: "dig.gravel",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "dig.snow" },
      {
        name: "얼음 벽·첨탑·모닥불",
        layers: [
          { dy: 0, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:packed_ice" : hearthRing(dx, dz)) },
          { dy: 1, paint: (dx, dz) => (wallCell(dx, dz) ? (hash(dx, dz, 9) < 0.25 ? "minecraft:blue_ice" : "minecraft:packed_ice") : null) },
        ],
        puts: struct,
        sound: "random.glass",
      },
      { name: "장식", puts: deco, sound: "fire.ignite" },
    ],
    features: { totems: [], decoys: [], gates, posts, fires, spires },
  };
}

/** 무스펠헤임의 신전 치수 (v2.12.0 수르트): 가운데 화구 반지름(pit) · 사슬 기둥까지(chains) · 얼음 결정까지(crystals) (설정 BOSSES.<키>.arena) */
export function muspelSize(key = "surtr") {
  const ar = BOSSES[key]?.arena ?? {};
  return { pit: ar.pit ?? 7, chains: ar.chains ?? 16, crystals: ar.crystals ?? 26 };
}
/** 봉인의 사슬 기둥 넷의 각도 (도, 대각선 — +x 에서 +z 쪽으로) */
export const CHAIN_DEG = [45, 135, 225, 315];
/** 니플헤임의 얼음 결정 여덟의 각도 (성문 길과 사슬 기둥 사이) */
export const CRYSTAL_DEG = [22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5];
/** 불꽃 탑 넷 (담장 위 대각선, 필드 밖으로만) */
export const TOWER_DEG = [45, 135, 225, 315];
/** 사슬 기둥·얼음 결정의 가운데 3×3 반폭 (칸): 라그나로크 그림자 폭·칼 붙잡기의 기준 */
export const CORE_HALF = 1.5;
/** 사슬 기둥 높이 (덮개까지, 칸): 칼 줄이 이 높이 아래로 지나가는 자리부터 붙잡힘 */
export const CHAIN_HEIGHT = 16;
/** 얼음 결정 높이 (칸) */
export const CRYSTAL_HEIGHT = 9;

/**
 * 봉인의 사슬 기둥 하나 (기둥 가운데 칸 기준 ox, dy, oz): 밑 3×3 우는 흑요석, 3×3 기둥 (윤나는 흑암 벽돌, 4칸마다 깎은 흑암 띠) dy 1~14,
 * 꼭대기 dy 15 덮개 5×5 (모서리 금 박힌 흑암), 덮개 네 모서리 밑으로 쇠사슬 여섯 칸 + 사슬 끝에 매단 랜턴
 * @returns {{ox: number, dy: number, oz: number, block: string, states?: Record<string, any>}[]}
 */
export function chainPillarCells() {
  const out = [];
  for (let ox = -1; ox <= 1; ox++) {
    for (let oz = -1; oz <= 1; oz++) {
      out.push({ ox, dy: 0, oz, block: "minecraft:crying_obsidian" });
      for (let dy = 1; dy <= 14; dy++) out.push({ ox, dy, oz, block: dy % 4 === 2 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks" });
    }
  }
  for (let ox = -2; ox <= 2; ox++) for (let oz = -2; oz <= 2; oz++) out.push({ ox, dy: CHAIN_HEIGHT - 1, oz, block: Math.abs(ox) === 2 && Math.abs(oz) === 2 ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone" });
  for (const [ox, oz] of [
    [-2, -2],
    [2, -2],
    [-2, 2],
    [2, 2],
  ]) {
    for (let dy = 9; dy <= 14; dy++) out.push({ ox, dy, oz, block: "minecraft:iron_chain", states: { pillar_axis: "y" } });
    out.push({ ox, dy: 8, oz, block: "minecraft:lantern", states: { hanging: true } });
  }
  return out;
}

/**
 * 니플헤임의 얼음 결정 하나 (결정 가운데 칸 기준): 3×3 결정 — 가운데 기둥 9칸 (꼭대기 두 칸 단단한 얼음), 옆 네 칸 6칸 (3칸마다 단단한 얼음 줄),
 * 모서리 네 칸 3칸 (푸른 얼음), 바깥 네 방향 작은 조각 2칸 (단단한 얼음)
 */
export function crystalCells() {
  const out = [];
  for (let ox = -1; ox <= 1; ox++) {
    for (let oz = -1; oz <= 1; oz++) {
      const edge = Math.abs(ox) + Math.abs(oz);
      const top = edge === 0 ? CRYSTAL_HEIGHT : edge === 1 ? 6 : 3;
      for (let dy = 0; dy < top; dy++) out.push({ ox, dy, oz, block: (edge === 0 && dy >= top - 2) || (edge === 1 && dy % 3 === 1) ? "minecraft:packed_ice" : "minecraft:blue_ice" });
    }
  }
  for (const [ox, oz] of [
    [2, 0],
    [-2, 0],
    [0, 2],
    [0, -2],
  ])
    for (let dy = 0; dy < 2; dy++) out.push({ ox, dy, oz, block: "minecraft:packed_ice" });
  return out;
}

/**
 * 수르트 (v2.12.0): 무스펠헤임의 신전 (반지름 44). 흑암·현무암 바닥, 가운데 무스펠의 화구 (반지름 pit: 주황 색유리 밑에 버섯불 — 녹은 용암처럼 빛남,
 * 붉은 색유리 나선 금), 화구 둘레 흑암 벽돌 테 + 금 박힌 흑암 룬 여덟, 다섯 칸 폭 성문 길 넷 (흑암 벽돌, 가운데 줄 깎은 흑암 룬),
 * 사슬 기둥 둘레의 붉은 네더 벽돌 고리 (반지름 chains), 용암 줄기 여덟 (주황 색유리 밑 버섯불, 구불구불), 가장자리 흑암 벽돌,
 * 높이 4 흑암 벽돌 담장 + 흑암 담장 성가퀴, 성문 넷 (현무암 기둥 + 네더 벽돌 들보·우는 흑요석 쐐기돌 + 매단 랜턴), 대각선 불꽃 탑 넷 (필드 밖으로만, 꼭대기 모닥불),
 * 봉인의 사슬 기둥 넷 (반지름 chains, 대각선) · 니플헤임의 얼음 결정 여덟 (반지름 crystals, 둘레 바닥에 서리). 장식은 담장 쪽 띠에만 (위더 해골·금 박힌 흑암·현무암)
 */
function muspel(a) {
  const { radius: R, height: H } = arenaSize(a);
  const rand = seeded(a);
  const EDGE = R + 2;
  const { pit: PIT, chains: CH, crystals: CR } = muspelSize(a.key);
  const onGate = (dx, dz) => Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
  const gates = [0, 90, 180, 270].map((d) => polar(R + 1, d));
  const hash = (dx, dz, k = 0) => {
    const v = Math.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
    return v - Math.floor(v);
  };
  const patch = (dx, dz, k) => Math.sin(dx * 0.31 + k) + Math.sin(dz * 0.37 + k * 2.3) + Math.sin((dx + dz) * 0.21 + k * 0.9);
  const angDist = (a1, a2) => Math.abs(Math.atan2(Math.sin(a1 - a2), Math.cos(a1 - a2)));
  const { cell: wallCell, cells: ring } = ringOf(R, onGate);
  const chainList = CHAIN_DEG.map((deg) => polar(CH, deg));
  const crystalList = CRYSTAL_DEG.map((deg) => polar(CR, deg));
  /** 사슬 기둥 밑 5×5 칸이면 기둥 안 자리 */
  const chainOf = (dx, dz) => {
    for (const c of chainList) if (Math.abs(dx - c.dx) <= 2 && Math.abs(dz - c.dz) <= 2) return { ox: dx - c.dx, oz: dz - c.dz };
    return undefined;
  };
  /** 얼음 결정 둘레 서리 (가운데에서 3.4칸 안) */
  const crystalOf = (dx, dz) => {
    for (const c of crystalList) {
      const d = Math.hypot(dx - c.dx, dz - c.dz);
      if (d <= 3.4) return { d };
    }
    return undefined;
  };
  // 용암 줄기 여덟: 사슬 기둥 양옆 (33.75°·56.25° + 90°k), 화구 테 밖에서 담장 3칸 안까지, 반지름에 따라 조금씩 구불구불
  const veins = [33.75, 56.25, 123.75, 146.25, 213.75, 236.25, 303.75, 326.25].map((d) => ({ a: (d * Math.PI) / 180, ph: rand() * 6 }));
  const isVein = (dx, dz, r) => {
    if (r < PIT + 1.6 || r > R - 3) return false;
    const ang = Math.atan2(dz, dx);
    return veins.some((v) => angDist(ang, v.a + (Math.sin(r * 0.4 + v.ph) * 0.8) / r) * r <= 0.55);
  };
  const pitCrack = (dx, dz, r) => r >= 1.2 && [0, 1, 2].some((k) => angDist(Math.atan2(dz, dx), r * 0.35 + (k * 2 * Math.PI) / 3) * r <= 0.5);
  /** @type {(dx: number, dz: number, r: number) => Paint} */
  const floor = (dx, dz, r) => {
    if (r > EDGE) return null;
    if (r > R) return hash(dx, dz, 3) < 0.3 ? ["minecraft:basalt", { pillar_axis: "y" }] : "minecraft:blackstone"; // 둑
    if (r > R - 1) return "minecraft:polished_blackstone_bricks"; // 가장자리
    const ch = chainOf(dx, dz);
    if (ch) return Math.abs(ch.ox) === 2 && Math.abs(ch.oz) === 2 ? "minecraft:gilded_blackstone" : "minecraft:chiseled_polished_blackstone";
    const cr = crystalOf(dx, dz);
    if (cr) return cr.d <= 1.6 ? "minecraft:blue_ice" : hash(dx, dz, 5) < 0.5 ? "minecraft:packed_ice" : "minecraft:snow";
    if (r <= PIT) return pitCrack(dx, dz, r) ? "minecraft:red_stained_glass" : "minecraft:orange_stained_glass";
    if (r <= PIT + 1.5) {
      const rune = [0, 45, 90, 135, 180, 225, 270, 315].some((d) => angDist(Math.atan2(dz, dx), (d * Math.PI) / 180) * r <= 0.6);
      return rune ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone_bricks";
    }
    if (onGate(dx, dz)) return (dx === 0 || dz === 0) && Math.round(r) % 4 === 0 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks"; // 성문 길
    if (Math.abs(r - CH) <= 0.6) return "minecraft:red_nether_brick";
    if (isVein(dx, dz, r)) return "minecraft:orange_stained_glass";
    const h = hash(dx, dz);
    if (patch(dx, dz, 1.7) > 1.5) return h < 0.5 ? ["minecraft:basalt", { pillar_axis: "y" }] : "minecraft:smooth_basalt";
    if (patch(dx, dz, 5.3) > 1.6) return h < 0.6 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone";
    return "minecraft:blackstone";
  };
  /** 바닥 밑: 빛나는 유리 (화구·용암 줄기) 밑은 버섯불, 그 밖은 흑암 */
  const glowsAbove = (dx, dz, r) => r <= R - 1 && !chainOf(dx, dz) && !crystalOf(dx, dz) && (r <= PIT || (!onGate(dx, dz) && Math.abs(r - CH) > 0.6 && r > PIT + 1.5 && isVein(dx, dz, r)));
  /** @type {Put[]} */
  const struct = [];
  // 성문: 양쪽 현무암 기둥 (dy 0~9) + 머리 (흑암 벽돌) + 들보 (네더 벽돌, 가운데 우는 흑요석) + 매단 랜턴 둘
  const DIRS = [
    [1, 0],
    [0, 1],
    [-1, 0],
    [0, -1],
  ];
  /** @type {{dx: number, dz: number, gate: number}[]} */
  const flanks = [];
  DIRS.forEach(([gx, gz], gi) => {
    for (const sd of [-3, 3]) {
      const c = nearestCell(ring, { dx: gx * (R + 1) + (gz !== 0 ? sd : 0), dz: gz * (R + 1) + (gx !== 0 ? sd : 0) });
      flanks.push({ dx: c.dx, dz: c.dz, gate: gi });
    }
  });
  for (const f of flanks) {
    for (let dy = 0; dy <= 9; dy++) struct.push({ dx: f.dx, dy, dz: f.dz, block: "minecraft:basalt", states: { pillar_axis: "y" } });
    struct.push({ dx: f.dx, dy: 10, dz: f.dz, block: "minecraft:polished_blackstone_bricks" });
  }
  DIRS.forEach(([gx], gi) => {
    const two = flanks.filter((f) => f.gate === gi);
    const along = gx !== 0 ? "dz" : "dx";
    const lo = Math.min(...two.map((f) => f[along]));
    const hi = Math.max(...two.map((f) => f[along]));
    const at = gx !== 0 ? two[0].dx : two[0].dz;
    const mid = Math.round((lo + hi) / 2);
    for (let k = lo + 1; k < hi; k++) struct.push({ dx: gx !== 0 ? at : k, dy: 10, dz: gx !== 0 ? k : at, block: k === mid ? "minecraft:crying_obsidian" : "minecraft:nether_brick" });
    for (const s of [-1, 1]) struct.push({ dx: gx !== 0 ? at : mid + s, dy: 9, dz: gx !== 0 ? mid + s : at, block: "minecraft:lantern", states: { hanging: true } });
  });
  // 불꽃 탑 넷: 담장 고리 칸에서 밖으로만 (현무암, 꼭대기 흑암 + 가운데 모닥불)
  /** @type {{dx: number, dz: number, h: number}[]} */
  const towers = [];
  TOWER_DEG.forEach((deg) => {
    const c = nearestCell(ring, polar(R + 1, deg));
    const h = 14;
    towers.push({ dx: c.dx, dz: c.dz, h });
    for (let ox = -2; ox <= 2; ox++) {
      for (let oz = -2; oz <= 2; oz++) {
        const x = c.dx + ox;
        const z = c.dz + oz;
        const r = Math.hypot(x, z);
        if (Math.hypot(ox, oz) > 1.5 || r <= R + 0.5 || r > EDGE) continue;
        for (let dy = 0; dy < h; dy++) struct.push({ dx: x, dy, dz: z, block: dy === h - 1 ? "minecraft:polished_blackstone" : dy % 5 === 4 ? "minecraft:polished_basalt" : "minecraft:basalt", states: dy === h - 1 ? undefined : { pillar_axis: "y" } });
      }
    }
    struct.push({ dx: c.dx, dy: h, dz: c.dz, block: "minecraft:campfire", states: { extinguished: false, "minecraft:cardinal_direction": "south" } });
  });
  // 사슬 기둥 넷 · 얼음 결정 여덟 (블록 목록은 기능 정보에도: 싸움 중 부서지고 녹으면 끝날 때 되돌림)
  const toPuts = (/** @type {{dx: number, dz: number}} */ c, cells) => cells.map((q) => ({ dx: c.dx + q.ox, dy: q.dy, dz: c.dz + q.oz, block: q.block, states: q.states }));
  const chains = chainList.map((c) => ({ dx: c.dx, dz: c.dz, half: CORE_HALF, h: CHAIN_HEIGHT, cells: toPuts(c, chainPillarCells()) }));
  // 결정 둘레 서리 바닥 (녹으면 그을린 현무암, 되돌릴 때 이 블록으로)
  const crystals = crystalList.map((c) => {
    const frost = [];
    for (let ox = -4; ox <= 4; ox++) {
      for (let oz = -4; oz <= 4; oz++) {
        const p = floor(c.dx + ox, c.dz + oz, Math.hypot(c.dx + ox, c.dz + oz));
        if (Math.hypot(ox, oz) <= 3.4 && p) frost.push({ dx: c.dx + ox, dy: -1, dz: c.dz + oz, block: Array.isArray(p) ? p[0] : p, states: Array.isArray(p) ? p[1] : undefined });
      }
    }
    return { dx: c.dx, dz: c.dz, half: CORE_HALF, h: CRYSTAL_HEIGHT, cells: toPuts(c, crystalCells()), frost };
  });
  const pieces = [...chains.flatMap((c) => c.cells), ...crystals.flatMap((c) => c.cells)];
  const posts = [...flanks.map((f) => ({ dx: f.dx, dz: f.dz })), ...towers.map((t) => ({ dx: t.dx, dz: t.dz }))];
  // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·탑 둘레 제외) — 위더 해골, 금 박힌 흑암, 현무암 기둥 (1~2칸)
  /** @type {Put[]} */
  const deco = [];
  const taken = new Set();
  const nearPost = (dx, dz) => posts.some((p) => Math.hypot(dx - p.dx, dz - p.dz) < 2.5);
  for (let n = 0, tries = 0; n < 30 && tries < 1000; tries++) {
    const deg = rand() * 360;
    const p = polar(R - rand() * 1.2, deg);
    const r = Math.hypot(p.dx, p.dz);
    const k = `${p.dx},${p.dz}`;
    if (taken.has(k) || r > R || r < R - 1.6 || Math.abs(p.dx) <= 3 || Math.abs(p.dz) <= 3 || nearPost(p.dx, p.dz)) continue;
    taken.add(k);
    const pick = rand();
    if (pick < 0.35) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:wither_skeleton_skull", states: { facing_direction: 1 } });
    else if (pick < 0.65) deco.push({ dx: p.dx, dy: 0, dz: p.dz, block: "minecraft:gilded_blackstone" });
    else {
      const tall = rand() < 0.5 ? 2 : 1;
      for (let dy = 0; dy < tall; dy++) deco.push({ dx: p.dx, dy, dz: p.dz, block: "minecraft:basalt", states: { pillar_axis: "y" } });
    }
    n++;
  }
  return {
    stages: [
      { name: "땅 고르기", layers: Array.from({ length: H + 1 }, (_, i) => ({ dy: i, paint: (_dx, _dz, r) => (r <= EDGE ? "minecraft:air" : null) })), sound: "dig.stone" },
      {
        name: "기초",
        layers: [
          { dy: -3, paint: (_dx, _dz, r) => (r <= EDGE ? (r <= PIT ? "minecraft:magma" : "minecraft:stone") : null) },
          { dy: -2, paint: (dx, dz, r) => (r <= EDGE ? (glowsAbove(dx, dz, r) ? "minecraft:shroomlight" : "minecraft:blackstone") : null) },
        ],
        sound: "dig.gravel",
      },
      { name: "바닥", layers: [{ dy: -1, paint: floor }], sound: "dig.basalt" },
      {
        name: "담장·성문·불꽃 탑",
        layers: [
          ...[0, 1, 2, 3].map((dy) => ({ dy, paint: (dx, dz) => (wallCell(dx, dz) ? (hash(dx, dz, 9 + dy) < 0.2 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks") : null) })),
          { dy: 4, paint: (dx, dz) => (wallCell(dx, dz) ? "minecraft:blackstone_wall" : null) },
        ],
        puts: struct,
        sound: "fire.ignite",
      },
      { name: "사슬 기둥·얼음 결정", puts: pieces, sound: "random.glass" },
      { name: "장식", puts: deco, sound: "mob.blaze.shoot" },
    ],
    features: { totems: [], decoys: [], gates, posts, chains, crystals, towers },
  };
}

/** 테마 이름 → 설계 */
const THEMES = { harvest, desert, sunken, deep, isle, roots, stones, glacier, muspel };

/** 제단의 필드 설계 (테마가 없으면 undefined) */
export function arenaPlan(a) {
  const t = THEMES[BOSSES[a.key]?.arena?.theme];
  return t ? t(a) : undefined;
}

/** 필드의 특징 자리 (월드 좌표, 칸 가운데): 토템·분신 자리·성문·기둥, (v2.0.0 가라앉은 신전) 해일 그림자 기둥(half = 가로 반폭)·촉수 물구덩이, (v2.1.0 심층의 신전) 굴 입구·체크무늬 칸 크기 */
export function arenaFeatures(a) {
  const plan = arenaPlan(a);
  const c = arenaCenter(a);
  const to = (/** @type {{dx: number, dz: number}} */ p) => ({ x: c.x + p.dx, y: a.y, z: c.z + p.dz });
  const f = plan?.features ?? { totems: [], decoys: [], gates: [], posts: [] };
  return {
    totems: f.totems.map(to),
    decoys: f.decoys.map(to),
    gates: f.gates.map(to),
    posts: f.posts.map(to),
    pillars: (f.pillars ?? []).map((p) => ({ ...to(p), half: p.half })),
    holes: (f.holes ?? []).map(to),
    tile: f.tile,
    // v2.4.0 해와 달의 돌 고리: 선돌 (sun = 동쪽 해 선돌)
    stones: (f.stones ?? []).map((p) => ({ ...to(p), sun: p.sun })),
    // v2.5.0 태초의 빙원: 무스펠헤임의 모닥불 (화덕 가운데 칸, 모닥불 블록은 한 칸 위) · 얼음 첨탑 (h = 높이)
    fires: (f.fires ?? []).map(to),
    spires: (f.spires ?? []).map((p) => ({ ...to(p), h: p.h })),
    // v2.12.0 무스펠헤임의 신전: 사슬 기둥 · 얼음 결정 (half = 가운데 3×3 반폭, h = 높이, cells = 블록 (월드 칸 좌표), frost = 결정 둘레 서리 바닥) · 불꽃 탑
    chains: (f.chains ?? []).map((p) => ({ ...to(p), half: p.half, h: p.h, cells: cellsOf(a, p.cells) })),
    crystals: (f.crystals ?? []).map((p) => ({ ...to(p), half: p.half, h: p.h, cells: cellsOf(a, p.cells), frost: cellsOf(a, p.frost) })),
    towers: (f.towers ?? []).map((p) => ({ ...to(p), h: p.h })),
  };
}

/** 설계의 놓을 블록 (제단 기준 dx, dy, dz) → 월드 칸 좌표 */
function cellsOf(a, list) {
  return (list ?? []).map((q) => ({ x: a.x + q.dx, y: a.y + q.dy, z: a.z + q.dz, block: q.block, states: q.states }));
}

// ------------------------------------------------------------ 짓기
/**
 * 설계 → 놓을 작업 목록. 층은 x 줄마다 같은 블록이 이어지는 구간을 fill 한 번으로 (제단 자리는 건너뜀).
 * 울타리는 이웃 울타리·단단한 블록 쪽으로 이어지는 상태(connection_*)를 직접 넣음
 * @returns {{stage: string, sound?: string, ops: {kind: "fill" | "set", from: {x: number, y: number, z: number}, to?: {x: number, y: number, z: number}, block: string, states?: Record<string, any>}[]}[]}
 */
export function planOps(a, plan) {
  const { radius: R } = arenaSize(a);
  const span = Math.ceil(R + 2);
  /** @type {Map<string, string>} 층 칠하기 결과 (울타리 이음 계산용): "dx,dy,dz" → 블록 */
  const grid = new Map();
  /** @type {any[]} */
  const out = [];
  for (const st of plan.stages) {
    /** @type {any[]} */
    const ops = [];
    for (const L of st.layers ?? []) {
      for (let dx = -span; dx <= span; dx++) {
        let run = null;
        const flush = () => {
          if (run) ops.push({ kind: "fill", from: { x: a.x + dx, y: a.y + L.dy, z: a.z + run.z0 }, to: { x: a.x + dx, y: a.y + L.dy, z: a.z + run.z1 }, block: run.block, states: run.states });
          run = null;
        };
        for (let dz = -span; dz <= span; dz++) {
          let p = L.paint(dx, dz, Math.hypot(dx, dz), DEG(dx, dz));
          if (dx === 0 && dz === 0 && L.dy === 0) p = null; // 제단
          const name = p === null ? null : Array.isArray(p) ? p[0] : p;
          const states = Array.isArray(p) ? p[1] : undefined;
          if (name !== null) grid.set(`${dx},${L.dy},${dz}`, name);
          const isFence = name !== null && name.endsWith("_fence");
          const isWall = name !== null && name.endsWith("_wall");
          if (name === null || isFence || isWall) {
            flush();
            if (isFence) ops.push({ kind: "set", from: { x: a.x + dx, y: a.y + L.dy, z: a.z + dz }, block: name, states: { __fence: [dx, L.dy, dz] } });
            if (isWall) ops.push({ kind: "set", from: { x: a.x + dx, y: a.y + L.dy, z: a.z + dz }, block: name, states: { __wall: [dx, L.dy, dz] } });
            continue;
          }
          const k = name + JSON.stringify(states ?? {});
          if (run && run.k === k && run.z1 === dz - 1) run.z1 = dz;
          else {
            flush();
            run = { k, block: name, states, z0: dz, z1: dz };
          }
        }
        flush();
      }
    }
    for (const p of st.puts ?? []) {
      if (p.dx === 0 && p.dz === 0 && p.dy === 0) continue;
      grid.set(`${p.dx},${p.dy},${p.dz}`, p.block);
      ops.push({ kind: "set", from: { x: a.x + p.dx, y: a.y + p.dy, z: a.z + p.dz }, block: p.block, states: p.states });
    }
    out.push({ stage: st.name, sound: st.sound, ops });
  }
  // 울타리·담장 이음: 이웃이 울타리·담장이거나 단단한 블록이면 이음 (덤불·해골·랜턴 같은 작은 장식은 빼고)
  const THIN = new Set(["minecraft:air", "minecraft:deadbush", "minecraft:skeleton_skull", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:sea_pickle", "minecraft:water", "minecraft:pointed_dripstone", "minecraft:amethyst_cluster", "minecraft:moss_carpet", "minecraft:waterlily", "minecraft:pale_moss_carpet", "minecraft:hanging_roots", "minecraft:pale_hanging_moss", "minecraft:snow_layer"]);
  const solid = (k) => {
    const b = grid.get(k);
    return !!b && !THIN.has(b) && !b.endsWith("_coral_fan") && !b.endsWith("candle");
  };
  for (const st of out) {
    for (const op of st.ops) {
      const f = op.states?.__fence;
      const w = op.states?.__wall;
      if (f) {
        const [dx, dy, dz] = f;
        op.states = {
          "minecraft:connection_east": solid(`${dx + 1},${dy},${dz}`),
          "minecraft:connection_west": solid(`${dx - 1},${dy},${dz}`),
          "minecraft:connection_south": solid(`${dx},${dy},${dz + 1}`),
          "minecraft:connection_north": solid(`${dx},${dy},${dz - 1}`),
        };
      } else if (w) {
        // 담장: 이음은 낮게(short), 곧은 줄(동서만·남북만)이 아니면 가운데 기둥(wall_post_bit)
        const [dx, dy, dz] = w;
        const e = solid(`${dx + 1},${dy},${dz}`);
        const wv = solid(`${dx - 1},${dy},${dz}`);
        const s = solid(`${dx},${dy},${dz + 1}`);
        const n = solid(`${dx},${dy},${dz - 1}`);
        const straight = (e && wv && !n && !s) || (n && s && !e && !wv);
        op.states = {
          wall_connection_type_east: e ? "short" : "none",
          wall_connection_type_west: wv ? "short" : "none",
          wall_connection_type_south: s ? "short" : "none",
          wall_connection_type_north: n ? "short" : "none",
          wall_post_bit: !straight,
        };
      }
    }
  }
  return out;
}

/** 블록 이름·상태 → 바꿈 (못 찾으면 돌) */
const perms = new Map();
let permWarned = false;
function permOf(name, states) {
  const k = name + JSON.stringify(states ?? {});
  let p = perms.get(k);
  if (p) return p;
  try {
    p = BlockPermutation.resolve(name, states ?? {});
  } catch (e) {
    try {
      p = BlockPermutation.resolve(name);
    } catch {
      if (!permWarned) console.warn(`[rpg] arena block ${name}: ${e}`);
      permWarned = true;
      p = BlockPermutation.resolve("minecraft:stone");
    }
  }
  perms.set(k, p);
  return p;
}

/** 짓는 중인 필드: 제단 → 작업 */
const jobs = new Map();

export function isBuilding(a) {
  return jobs.has(a);
}

/**
 * 지을 수 없는 까닭 (없으면 undefined): 세계 높이 끝·지옥 기반암에 닿음, 다른 제단의 필드와 겹침
 * @param {Altar} a
 */
export function buildProblem(a) {
  const { radius: R, height: H } = arenaSize(a);
  let dim;
  try {
    dim = world.getDimension(a.dim);
  } catch {
    return "차원을 찾을 수 없습니다";
  }
  const hr = dim.heightRange ?? { min: -64, max: 320 };
  if (a.y - 4 < hr.min + 5 || a.y + H + 2 > hr.max) return `높이가 맞지 않습니다 (제단을 y ${hr.min + 9} ~ ${hr.max - H - 2} 사이에 놓아 주세요)`;
  if (a.dim === "minecraft:nether" && (a.y + H + 2 >= 123 || a.y - 4 <= 5)) return "지옥에서는 천장·바닥 기반암에 닿습니다 (제단을 y 10 ~ 106 사이에 놓아 주세요)";
  const near = altars().find((o) => o !== a && o.dim === a.dim && Math.abs(o.y - a.y) < 40 && Math.hypot(o.x - a.x, o.z - a.z) < arenaSize(o).radius + R + BOSS.nearAltars);
  if (near) return `다른 보스 제단(${BOSSES[near.key].name}, ${near.x} ${near.y} ${near.z})과 너무 가깝습니다 (필드가 겹쳐 지워짐)`;
  return undefined;
}

/** 필드 둘레(바로 바깥)에 물·용암이 있는지 (지은 뒤 흘러들 수 있음): 몇 층만 훑어봄 */
function liquidsAround(a) {
  const { radius: R, height: H } = arenaSize(a);
  let dim;
  try {
    dim = world.getDimension(a.dim);
  } catch {
    return false;
  }
  const r0 = R + 2;
  for (let dy = -2; dy <= H; dy += 2) {
    for (let i = 0; i < 96; i++) {
      const ang = (i / 96) * Math.PI * 2;
      const loc = { x: a.x + Math.round(Math.cos(ang) * (r0 + 1)), y: a.y + dy, z: a.z + Math.round(Math.sin(ang) * (r0 + 1)) };
      try {
        if (dim.getBlock(loc)?.isLiquid) return true;
      } catch {
        // 불러오지 않은 곳
      }
    }
  }
  return false;
}

/**
 * 필드 짓기 시작 (몇 틱에 나눠서, 단계 사이 쉬며 연출)
 * @param {Altar} a
 * @param {import("@minecraft/server").Player | undefined} player 알릴 사람
 */
export function buildArena(a, player) {
  if (jobs.has(a)) return false;
  const plan = arenaPlan(a);
  if (!plan) return false;
  const why = buildProblem(a);
  if (why) {
    if (player?.isValid) player.sendMessage(`§c[보스 필드] 지을 수 없습니다: ${why}`);
    return false;
  }
  let dim;
  try {
    dim = world.getDimension(a.dim);
  } catch {
    return false;
  }
  const stages = planOps(a, plan);
  /** @type {any} */
  const job = { dim, stages, si: 0, oi: 0, pause: 0, player, errors: 0, area: undefined, wait: 0 };
  // v2.12.0 큰 필드 (arena.tick, 수르트 반지름 44): 짓는 동안 틱킹 영역으로 붙잡고 다 불러와질 때까지 (최대 TICK_WAIT 틱) 기다림
  if (BOSSES[a.key].arena?.tick) {
    const { radius: R } = arenaSize(a);
    job.area = holdArea(dim, arenaCenter(a), R + 4, "b");
    job.wait = job.area ? TICK_WAIT : 0;
  }
  jobs.set(a, job);
  if (player?.isValid) notify(player, `§6[보스 필드] §f${BOSSES[a.key].name}의 신전을 짓는 중...`, 80);
  return true;
}

/** 큰 필드를 틱킹 영역으로 불러올 때 기다리는 최대 틱 (10초) */
export const TICK_WAIT = 200;

function buildTick() {
  for (const [a, job] of jobs) {
    if (job.pause > 0) {
      job.pause--;
      continue;
    }
    // 틱킹 영역이 다 불러와질 때까지 (시간이 다 되면 불러온 만큼으로 지음)
    if (job.wait > 0) {
      job.wait--;
      if (!areaLoaded(job.dim, arenaCenter(a), arenaSize(a).radius + 4)) continue;
      job.wait = 0;
    }
    const st = job.stages[job.si];
    if (!st) {
      jobs.delete(a);
      releaseArea(job.dim, job.area);
      a.built = true;
      saveAltars();
      finishFx(a, job);
      continue;
    }
      // 한 틱에 바꾸는 블록 수로 나눔 (fill 은 부피만큼 셈)
    let budget = BOSS.buildPerTick;
    while (budget > 0 && job.oi < st.ops.length) {
      const op = st.ops[job.oi++];
      budget -= op.to ? (Math.abs(op.to.x - op.from.x) + 1) * (Math.abs(op.to.y - op.from.y) + 1) * (Math.abs(op.to.z - op.from.z) + 1) : 1;
      try {
        const perm = permOf(op.block, op.states);
        if (op.kind === "fill") job.dim.fillBlocks(new BlockVolume(op.from, /** @type {any} */ (op.to)), perm);
        else job.dim.setBlockPermutation(op.from, perm);
      } catch (e) {
        job.errors++;
        if (job.errors === 1) console.warn(`[rpg] arena build: ${e}`);
      }
    }
    if (job.oi >= st.ops.length) {
      stageFx(a, job, st);
      job.si++;
      job.oi = 0;
      job.pause = 8; // 단계 사이 0.4초 (올라오는 느낌)
    }
  }
}

function stageFx(a, job, st) {
  const { radius: R } = arenaSize(a);
  const c = arenaCenter(a);
  const col = BOSSES[a.key].color;
  try {
    for (let i = 0; i < 16; i++) {
      const d = (i / 16) * Math.PI * 2;
      fx(job.dim, "rpg:bash_burst", { x: c.x + Math.cos(d) * R, y: a.y + 0.3, z: c.z + Math.sin(d) * R }, { dir: { x: 0, y: 0, z: 1 }, size: 1.2, life: 0.4, color: col, alpha: 0.8 });
    }
    fx(job.dim, "rpg:shock_ring", { x: c.x, y: a.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: R / 2.1, life: 0.6, color: col, alpha: 0.8 });
    if (st.sound) play(job.dim, st.sound, c, 0.8, 1);
  } catch {
    // 무시
  }
}

function finishFx(a, job) {
  const c = arenaCenter(a);
  play(job.dim, "random.levelup", c, 0.8, 1);
  play(job.dim, BOSSES[a.key].sounds?.roar ?? "mob.ravager.roar", c, 0.7, 0.8);
  const msg = `§6[보스 필드] §f${BOSSES[a.key].name}의 신전이 완성되었습니다. §7제단을 터치하면 보스에게 도전합니다`;
  if (job.player?.isValid) job.player.sendMessage(msg);
  if (job.errors > 0 && job.player?.isValid) job.player.sendMessage(`§c[보스 필드] 블록 ${job.errors}묶음을 놓지 못했습니다 (불러오지 않은 곳 등). §7/boss build 로 다시 지을 수 있습니다`);
}

/** 필드를 지을지 묻기 (운영자·크리에이티브) */
export async function askBuild(player, a) {
  if (!canManage(player)) return;
  const b = BOSSES[a.key];
  const { radius, height } = arenaSize(a);
  const why = buildProblem(a);
  if (why) {
    player.sendMessage(`§c[보스 필드] 지을 수 없습니다: ${why}`);
    return;
  }
  const wet = liquidsAround(a) ? "\n§c- 둘레에 물·용암이 있습니다: 필드 안으로 흘러들 수 있습니다" : "";
  const sel = await new MenuForm(
    `§l${b.name} 보스 필드`,
    `§f제단 둘레 반지름 §e${radius}칸§f, 높이 §e${height}칸§f 안의 지형을 §6${b.title ?? b.name}§f의 신전으로 바꿉니다.\n\n§7- 그 안의 블록은 사라지고 바닥·울타리·기둥이 새로 생깁니다\n§7- 필드 안 블록은 운영자가 아니면 부술 수 없습니다\n§7- 보스는 필드를 지어야 도전할 수 있습니다\n§7- 나중에 크리에이티브에서 웅크리고 제단을 터치하거나 /boss build 로 지을 수 있습니다${wet}`,
  )
    .button("§2필드 만들기")
    .button("나중에")
    .show(player);
  if (sel !== 0) return;
  if (!altars().includes(a)) return;
  buildArena(a, player);
}

// ------------------------------------------------------------ 이벤트
function onPlace(ev) {
  const block = ev.block;
  const player = ev.player;
  const key = altarKeyOf(block.typeId);
  const dimId = block.dimension.id;
  if (key) {
    const a = addAltar(key, dimId, block.location);
    if (player?.isValid) {
      player.sendMessage(`§6[보스 제단] §f${BOSSES[key].name} 제단을 놓았습니다 §7(Lv.${BOSSES[key].level} · 권장 3~4명)`);
      system.run(() => void askBuild(player, a));
    }
    return;
  }
  // 지어진 필드 안: 운영자·크리에이티브가 아니면 되돌림 (기둥 쌓기 등)
  if (!player || canManage(player)) return;
  const a = builtArenaAt(dimId, block.location);
  if (!a) return;
  let item;
  try {
    item = block.permutation.getItemStack(1);
  } catch {
    item = undefined;
  }
  try {
    block.setType("minecraft:air");
  } catch {
    return;
  }
  if (item) {
    try {
      const inv = player.getComponent("minecraft:inventory")?.container;
      const left = inv?.addItem(item);
      if (left) player.dimension.spawnItem(left, player.location);
    } catch {
      // 무시
    }
  }
  warn(player, "§c보스 필드 안에는 블록을 놓을 수 없습니다");
}

function onBreakBefore(ev) {
  const player = ev.player;
  const block = ev.block;
  if (canManage(player)) return;
  let dimId;
  try {
    dimId = block.dimension.id;
  } catch {
    return;
  }
  if (altarKeyOf(block.typeId) || builtArenaAt(dimId, block.location)) {
    ev.cancel = true;
    system.run(() => {
      if (player.isValid) warn(player, "§c보스 필드는 부술 수 없습니다");
    });
  }
}

/** 필드를 바꾸는 도구 (필드 안에서는 운영자만): 물·용암·불, 껍질 벗기기·밭 갈기·길 만들기, 가위, 뼛가루 */
const TERRAIN_TOOLS = /^minecraft:(.*_bucket|flint_and_steel|fire_charge|.*_axe|.*_hoe|.*_shovel|shears|bone_meal)$/;
const FACE = { Up: [0, 1, 0], Down: [0, -1, 0], North: [0, 0, -1], South: [0, 0, 1], East: [1, 0, 0], West: [-1, 0, 0] };

/** 블록을 터치하기 직전: 필드 안에 블록 놓기·필드 바꾸는 도구는 막음 (놓은 뒤 되돌리면 상자 속 물건 등이 사라질 수 있어서 미리) */
function onInteractBefore(ev) {
  const id = ev.itemStack?.typeId;
  if (!id) return;
  const block = ev.block;
  if (id === "minecraft:bucket") {
    bucketBefore(ev);
    return;
  }
  if (altarKeyOf(block.typeId)) return; // 제단 터치 (도전)는 그대로
  const tool = TERRAIN_TOOLS.test(id);
  let placeable = false;
  if (!tool) {
    try {
      placeable = !!BlockTypes.get(id);
    } catch {
      placeable = false;
    }
  }
  if (!tool && !placeable) return;
  const player = ev.player;
  if (canManage(player)) return;
  let dimId;
  try {
    dimId = block.dimension.id;
  } catch {
    return;
  }
  // 터치한 블록과 그 면 쪽 칸 (물·블록이 놓이는 자리)
  const f = FACE[ev.blockFace] ?? [0, 0, 0];
  const l = block.location;
  if (!builtArenaAt(dimId, l) && !builtArenaAt(dimId, { x: l.x + f[0], y: l.y + f[1], z: l.z + f[2] })) return;
  ev.cancel = true;
  if (!ev.isFirstEvent && ev.isFirstEvent !== undefined) return;
  system.run(() => {
    if (player.isValid) warn(player, tool ? "§c보스 필드 안에서는 이 도구를 쓸 수 없습니다" : "§c보스 필드 안에는 블록을 놓을 수 없습니다");
  });
}

/** 빈 양동이 (v2.0.0): 필드 안의 물(크라켄 촉수 물구덩이)은 운영자만 뜰 수 있음. 터치한 블록이나 그 면 쪽 칸이 액체일 때 */
function bucketBefore(ev) {
  const block = ev.block;
  const player = ev.player;
  let dimId;
  let wet = false;
  const f = FACE[ev.blockFace] ?? [0, 0, 0];
  const l = block.location;
  const side = { x: l.x + f[0], y: l.y + f[1], z: l.z + f[2] };
  try {
    dimId = block.dimension.id;
    wet = !!block.isLiquid || !!block.dimension.getBlock(side)?.isLiquid;
  } catch {
    return;
  }
  if (!wet || canManage(player)) return;
  if (!builtArenaAt(dimId, l) && !builtArenaAt(dimId, side)) return;
  ev.cancel = true;
  if (!ev.isFirstEvent && ev.isFirstEvent !== undefined) return;
  system.run(() => {
    if (player.isValid) warn(player, "§c보스 필드의 물은 뜰 수 없습니다");
  });
}

/**
 * 빈 양동이를 허공에 씀 (v2.0.0): 게임이 물 뜨기를 블록 터치가 아니라 아이템 쓰기로 알리는 경우에 대비.
 * 보는 곳(물 포함 광선)의 액체가 필드 안이면 막음 (운영자·크리에이티브는 됨)
 */
function bucketUseBefore(ev) {
  if (ev.itemStack?.typeId !== "minecraft:bucket") return;
  const player = ev.source;
  if (player?.typeId !== "minecraft:player" || canManage(player)) return;
  let dimId;
  let loc;
  try {
    const b = player.getBlockFromViewDirection({ includeLiquidBlocks: true, includePassableBlocks: true, maxDistance: 8 })?.block;
    if (!b?.isLiquid) return;
    dimId = b.dimension.id;
    loc = b.location;
  } catch {
    return;
  }
  if (!builtArenaAt(dimId, loc)) return;
  ev.cancel = true;
  system.run(() => {
    if (player.isValid) warn(player, "§c보스 필드의 물은 뜰 수 없습니다");
  });
}

/** 폭발(크리퍼·TNT)이 필드 블록을 부수지 않게 */
function onExplosionBefore(ev) {
  let list;
  try {
    list = ev.getImpactedBlocks();
  } catch {
    return;
  }
  if (!list?.length || !altars().some((a) => a.built)) return;
  const dimId = ev.dimension.id;
  const keep = list.filter((b) => !builtArenaAt(dimId, b.location));
  if (keep.length !== list.length) ev.setImpactedBlocks(keep);
}

function onBreakAfter(ev) {
  const key = altarKeyOf(ev.brokenBlockPermutation?.type?.id);
  if (!key) return;
  const a = altarAt(ev.block.dimension.id, ev.block.location);
  if (a) removeAltar(a);
}

export function registerArenas() {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    try {
      onPlace(ev);
    } catch (e) {
      console.warn(`[rpg] arena place: ${e}`);
    }
  });
  world.beforeEvents.playerBreakBlock.subscribe((ev) => {
    try {
      onBreakBefore(ev);
    } catch (e) {
      console.warn(`[rpg] arena break: ${e}`);
    }
  });
  world.afterEvents.playerBreakBlock.subscribe((ev) => {
    try {
      onBreakAfter(ev);
    } catch (e) {
      console.warn(`[rpg] arena broken: ${e}`);
    }
  });
  world.beforeEvents.playerInteractWithBlock.subscribe((ev) => {
    try {
      onInteractBefore(ev);
    } catch (e) {
      console.warn(`[rpg] arena interact: ${e}`);
    }
  });
  world.beforeEvents.itemUse.subscribe((ev) => {
    try {
      bucketUseBefore(ev);
    } catch (e) {
      console.warn(`[rpg] arena bucket: ${e}`);
    }
  });
  world.beforeEvents.explosion.subscribe((ev) => {
    try {
      onExplosionBefore(ev);
    } catch (e) {
      console.warn(`[rpg] arena explosion: ${e}`);
    }
  });
  // 명령어(/fill 등)로 제단이 없어진 경우: 10초마다 불러온 곳의 제단 블록을 확인해 목록에서 뺌
  system.runInterval(checkAltarBlocks, 200);
  system.runInterval(buildTick, 1);
}

/** 목록의 제단 자리에 제단 블록이 없으면 (불러온 곳만) 목록에서 뺌 */
export function checkAltarBlocks() {
  for (const a of [...altars()]) {
    let b;
    try {
      b = world.getDimension(a.dim).getBlock({ x: a.x, y: a.y, z: a.z });
    } catch {
      continue; // 불러오지 않은 곳
    }
    if (b && b.typeId !== altarId(a.key) && !isBuilding(a)) removeAltar(a);
  }
}

/** 제단 블록 아이템 (/boss altar) */
export function altarItem(key, amount = 1) {
  return new ItemStack(altarId(key), amount);
}

/** 가장 가까운 제단 (같은 차원, maxDist 안) */
export function nearestAltar(player, maxDist = 64) {
  let best;
  let bd = maxDist;
  for (const a of altars()) {
    if (a.dim !== player.dimension.id) continue;
    const c = arenaCenter(a);
    const d = Math.hypot(c.x - player.location.x, c.y - player.location.y, c.z - player.location.z);
    if (d <= bd) {
      bd = d;
      best = a;
    }
  }
  return best;
}

/** 필드 안의 플레이어 (살아 있는지는 부르는 쪽이) */
export function playersIn(a, margin = 0) {
  let list = [];
  try {
    list = world.getDimension(a.dim).getPlayers();
  } catch {
    list = [];
  }
  return list.filter((p) => p.typeId === PLAYER && inArena(a, a.dim, p.location, margin));
}
