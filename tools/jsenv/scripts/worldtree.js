import { BlockPermutation, system, world } from "@minecraft/server";
import { altars, arenaSize } from "./arena.js";
import { BOSSES, WORLD_TREE } from "./config.js";
import { MenuForm } from "./forms.js";
import { notify, particle, play } from "./fx.js";
import { showRealTitle } from "./hp.js";
import { fx } from "./skills.js";

/**
 * 세계수 (v2.5.1): /worldtree · /세계수 — 바라보는 곳에 거대한 나무(이그드라실)를 심음 (운영자)
 * - 설계 treePlanGen(높이, 씨앗): 뿌리 (밑동에서 사방으로 뻗어 땅에 박힘, 몇은 아치) · 줄기 (버팀 뿌리로 넓게 퍼진 밑동, 위로 가늘어지고 살짝 휨,
 *   송진 줄무늬·이끼 얼룩) · 가지 (나선으로 돌며 비스듬히 위로, 잔가지, 꼭대기 세 갈래) · 잎 뭉치 (진달래 잎·꽃 핀 진달래 잎, 겉에 빛나는 버섯불 열매) ·
 *   잎 밑 발광 열매 덩굴 (이끼 블록에 매달림) · 가지 밑 포자 꽃 · 밑동 둘레 이끼 깔개·반딧불이 덤불·꽃 핀 진달래.
 *   계산은 system.runJob 으로 여러 틱에 나눔 (폰에서 한 틱이 길어지지 않게). 설계의 지문 (fp) 으로 되돌릴 때 같은 설계인지 봄
 * - 불러오기: 나무 자리 (잎 끝까지의 원) 를 임시 틱킹 영역 (반지름 4청크까지) 으로 붙잡아 두고, 그 안의 청크가 다 불러와질 때까지 기다림
 *   (폰의 시뮬레이션 거리 4청크 밖도 자라게). 다 자라거나 되돌리면 치우고, 월드를 나가 남은 영역은 다음 접속 때 치움
 * - 심기 전 살핌: 네더 · 세계 높이 · 보스 필드 · 불러오지 않은 곳 · 물 속 → 바로 거절. 그다음 (여러 틱에 나눠) 둘레 용암 · 줄기·뿌리 자리의 건물·설치물 ·
 *   잎 자리가 막힌 정도 (산·동굴 안) → 거절하며 까닭을 알림
 * - 놓는 법 (mode): wood = 공기·풀·꽃·물·자연 지형 (흙·모래·자갈·돌·눈·얼음 등)·들의 식물·다른 나무의 통나무만 바꿈 — 광석·조약돌·판자·껍질 벗긴 통나무·
 *   사방이 껍질인 나무 블록·테라코타·밭·길·상자 등은 그대로, leaf = 빈 곳·풀·꽃·다른 잎에만, hang = 빈 곳이고 위가 단단한 곳,
 *   carpet = 그 자리 둘레(위 2 ~ 아래 4칸)의 자연 땅·뿌리 위, bush = carpet 과 같되 흙 종류 위에만.
 *   바꾸는 칸 위에 선 풀·꽃·대나무 등 (받침을 잃는 것) 과 두 칸짜리 풀·꽃의 다른 반쪽은 먼저 치우고 기록 (되돌릴 때 다시 놓음)
 * - 뿌리부터 단계마다 자라남 (한 틱에 perTick 개·msPerTick 밀리초까지, 서버가 느려지면 줄임, 단계 사이 쉬며 소리·파티클).
 *   자라는 칸에 선 사람·동물은 옆으로 옮김
 * - 기록: 월드에 최근 keep 그루 (자리·높이·씨앗·지문) 와 칸마다 원래 블록 (상태 포함 팔레트 번호, 놓지 않은 칸 = 0) · 깔개 높이 · 함께 치운 칸.
 *   자라는 동안 saveEvery 틱마다·단계마다 저장
 * - 되돌리기 (확인 창): 기록에서 놓은 칸만, 지금도 우리가 놓은 블록일 때만 원래 블록으로 (누가 캐거나 바꾼 곳은 그대로), 늦게 놓은 것부터.
 *   설계가 달라졌으면 (다른 버전) 되돌리지 않음. 불러오지 못해 못 한 곳은 기록에 남아 다시 하면 이어서
 */

const TAU = Math.PI * 2;
const DEG = Math.PI / 180;
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
/** 받침이 있으면 a (이), 없으면 b (가) */
const josa = (word, a, b) => {
  const c = word.charCodeAt(word.length - 1) - 0xac00;
  return c >= 0 && c < 11172 && c % 28 !== 0 ? a : b;
};

// ------------------------------------------------------------ 블록
export const TREE_BLOCKS = {
  wood: "minecraft:pale_oak_wood",
  resin: "minecraft:resin_block",
  moss: "minecraft:moss_block",
  leaf: "minecraft:azalea_leaves",
  bloom: "minecraft:azalea_leaves_flowered",
  fruit: "minecraft:shroomlight",
  vine: "minecraft:cave_vines_body_with_berries",
  vineTip: "minecraft:cave_vines_head_with_berries",
  blossom: "minecraft:spore_blossom",
  carpet: "minecraft:moss_carpet",
  firefly: "minecraft:firefly_bush",
  azalea: "minecraft:flowering_azalea",
};
const B = TREE_BLOCKS;
const AIR = "minecraft:air";
const WATER = "minecraft:water";
/** 잎은 떨어지지 않게 (통나무가 멀어도) */
const LEAF_STATES = { persistent_bit: true, update_bit: false };

const ids = (list) => new Set(list.split(/\s+/).filter(Boolean).map((s) => `minecraft:${s}`));
/** 부드러운 블록: 잎·열매·덩굴·깔개를 놓아도 되는 곳 (풀·꽃·눈·덩굴 등, 공기·다른 나무의 잎·묘목 포함) */
const SOFT = ids(`air short_grass tall_grass fern large_fern deadbush snow_layer vine glow_lichen resin_clump leaf_litter wildflowers pink_petals
  moss_carpet pale_moss_carpet dandelion poppy blue_orchid allium azure_bluet red_tulip orange_tulip white_tulip pink_tulip oxeye_daisy cornflower
  lily_of_the_valley torchflower closed_eyeblossom open_eyeblossom cactus_flower sunflower lilac rose_bush peony sweet_berry_bush bush
  short_dry_grass tall_dry_grass brown_mushroom red_mushroom`);
/** 들에 자라는 식물 (땅을 찾을 때 지나감, 줄기·뿌리 자리면 바꿈): 대나무·사탕수수·선인장·다시마·해초·버섯 등 */
const PLANT = ids(`bamboo bamboo_sapling reeds cactus kelp seagrass sea_pickle waterlily big_dripleaf small_dripleaf_block cave_vines
  cave_vines_body_with_berries cave_vines_head_with_berries hanging_roots azalea flowering_azalea spore_blossom firefly_bush cocoa
  twisting_vines weeping_vines nether_sprouts crimson_roots warped_roots crimson_fungus warped_fungus pitcher_plant pale_hanging_moss shelf_mushroom`);
/** 밭 작물 (땅을 찾을 때 지나가지만 바꾸지 않음) */
const CROPS = ids("wheat carrots potatoes beetroot melon_stem pumpkin_stem torchflower_crop pitcher_crop nether_wart");
/** 사람이 만든 땅 (바꾸지 않고 장식도 올리지 않음): 밭·길 */
const TILLED = ids("farmland grass_path");
/** 자연 지형 (줄기·뿌리 자리면 바꿈) */
const TERRAIN = ids(`grass_block dirt coarse_dirt podzol mycelium dirt_with_roots mud muddy_mangrove_roots mangrove_roots clay gravel sand red_sand
  moss_block pale_moss_block snow powder_snow ice packed_ice blue_ice frosted_ice stone granite diorite andesite deepslate tuff calcite
  dripstone_block pointed_dripstone sandstone red_sandstone smooth_basalt sculk sculk_vein end_stone netherrack water flowing_water
  brown_mushroom_block red_mushroom_block mushroom_stem`);
/** 자연에 있지만 그대로 두는 블록 (광석·흑요석·자수정·테라코타·수상한 모래 등): 건물로 치지 않음 */
const KEPT = ids(`ancient_debris obsidian crying_obsidian magma amethyst_block budding_amethyst amethyst_cluster large_amethyst_bud medium_amethyst_bud
  small_amethyst_bud hardened_clay suspicious_sand suspicious_gravel bedrock resin_block shroomlight`);
/** 흙 종류 (덤불·진달래가 자랄 수 있는 땅) */
const DIRT = ids("grass_block dirt coarse_dirt podzol mycelium dirt_with_roots moss_block pale_moss_block mud muddy_mangrove_roots");
/** 밑이 받쳐 줘야 서 있는 풀·꽃·작물·줄기식물: 밑 블록을 바꾸면 떨어지므로 먼저 치우고 기록 */
const ROOTED = ids(`short_grass tall_grass fern large_fern deadbush dandelion poppy blue_orchid allium azure_bluet red_tulip orange_tulip white_tulip
  pink_tulip oxeye_daisy cornflower lily_of_the_valley torchflower closed_eyeblossom open_eyeblossom sunflower lilac rose_bush peony sweet_berry_bush
  bush short_dry_grass tall_dry_grass wildflowers pink_petals brown_mushroom red_mushroom bamboo bamboo_sapling reeds cactus cactus_flower kelp
  seagrass sea_pickle azalea flowering_azalea firefly_bush pitcher_plant big_dripleaf small_dripleaf_block wheat carrots potatoes beetroot
  melon_stem pumpkin_stem torchflower_crop pitcher_crop nether_wart crimson_roots warped_roots crimson_fungus warped_fungus nether_sprouts
  twisting_vines waterlily`);
/** 늘 물 속에 사는 식물: 치울 때 물로 채우고, 되돌릴 때 물에 잠긴 채로 */
const AQUATIC = ids("kelp seagrass");
/** 옮기지 않는 엔티티 (아이템·경험치·투사체·NPC·갑옷 거치대 등, 체력이 있어도) */
const STAY = new Set(
  `item xp_orb npc armor_stand arrow thrown_trident snowball egg ender_pearl fireball small_fireball splash_potion lingering_potion
  wind_charge_projectile breeze_wind_charge_projectile falling_block tnt painting leash_knot fishing_hook`
    .split(/\s+/)
    .filter(Boolean)
    .map((s) => `minecraft:${s}`),
);

/** 부드러운 블록 (공기·풀·꽃·다른 나무의 잎 등): 잎·열매·덩굴을 놓아도 됨 */
export function isSoft(b) {
  if (!b) return false;
  if (b.isAir) return true;
  const id = b.typeId;
  return SOFT.has(id) || /_leaves$|_leaves_flowered$|_sapling$|_propagule$/.test(id);
}
/** 다른 나무의 줄기·큰 버섯·세계수 줄기 (땅을 찾을 때 지나감). 사방이 껍질인 나무 블록 (_wood) 은 사람이 만든 것이라 땅으로 봄 */
const isTrunk = (id) => id === B.wood || /_log$|_stem$|mangrove_roots$|_mushroom_block$/.test(id);
/** 땅이 아님: 부드러운 블록·액체·다른 나무 줄기·식물·작물 */
const passable = (b) => isSoft(b) || b.isLiquid || isTrunk(b.typeId) || PLANT.has(b.typeId) || CROPS.has(b.typeId);
/** 땅 (나무를 세울 수 있는 곳) */
export function isGround(b) {
  return !!b && !passable(b);
}
/**
 * 줄기·뿌리가 바꿔도 되는 블록 (자연): 공기·부드러운 블록·식물·자연 지형·물·다른 나무의 통나무·다른 세계수의 줄기.
 * 껍질 벗긴 통나무·사방이 껍질인 나무 블록 (_wood, _hyphae)·판자는 사람이 만든 것으로 봄
 */
export function isNatural(b) {
  if (!b) return false;
  if (b.isAir || isSoft(b)) return true;
  const id = b.typeId;
  if (TERRAIN.has(id) || PLANT.has(id) || id === B.wood) return true;
  return !id.includes("stripped_") && /_log$|^minecraft:(crimson|warped)_stem$/.test(id);
}
/** 자연에 있지만 그대로 두는 블록 (건물로 치지 않음) */
function isKept(id) {
  return KEPT.has(id) || /_ore$|coral|^minecraft:infested_/.test(id) || (id.endsWith("_terracotta") && !id.includes("glazed"));
}
const isLava = (b) => !!b && /lava/.test(b.typeId);
/** 밑이 받쳐 줘야 서 있는 식물 */
const isRooted = (b) => ROOTED.has(b.typeId) || /_sapling$|_propagule$/.test(b.typeId);
/** 깔개·덤불을 올려도 되는 땅: 자연 지형 (물 빼고)·통나무·세계수 뿌리 */
const naturalGround = (b) => (TERRAIN.has(b.typeId) && !b.isLiquid) || isTrunk(b.typeId);

// ------------------------------------------------------------ 설계
/** 정해진 씨앗의 난수 (0 ~ 1) */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
/** 칸마다 정해진 잡음 (0 ~ 1) */
function hash3(x, y, z, k = 0) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(z | 0, 1274126177) ^ Math.imul(k | 0, 1911520717);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
/** 부드러운 잡음 (0 ~ 1): 정수 격자의 잡음을 이어 붙임 (이끼 얼룩) */
function vnoise(x, y, z, k) {
  const x0 = Math.floor(x);
  const y0 = Math.floor(y);
  const z0 = Math.floor(z);
  const fx = x - x0;
  const fy = y - y0;
  const fz = z - z0;
  const sm = (t) => t * t * (3 - 2 * t);
  const u = sm(fx);
  const v = sm(fy);
  const w = sm(fz);
  const L = (a, b, t) => a + (b - a) * t;
  const h = (i, j, l) => hash3(x0 + i, y0 + j, z0 + l, k);
  return L(L(L(h(0, 0, 0), h(1, 0, 0), u), L(h(0, 1, 0), h(1, 1, 0), u), v), L(L(h(0, 0, 1), h(1, 0, 1), u), L(h(0, 1, 1), h(1, 1, 1), u), v), w);
}
/** 자리에서 씨앗 (같은 자리 = 같은 나무) */
export function seedOf(base) {
  return Math.floor(hash3(base.x, base.y, base.z, 97) * 2147483646) + 1;
}

/**
 * @typedef {{dx: number, dy: number, dz: number, block: string, states?: Record<string, any>, mode: "wood" | "leaf" | "hang" | "carpet" | "bush"}} TreeOp
 * @typedef {{name: string, kind: string, sound: string, pitch: number, y: number, ops: TreeOp[]}} TreeStage
 * @typedef {{H: number, seed: number, stages: TreeStage[], reach: number, rootReach: number, canopy: number, trunkR: number, top: number, bottom: number,
 *   total: number, woodCount: number, counts: Record<string, number>, branches: {x: number, y: number, z: number}[], fp: number}} TreePlan
 *   fp = 설계의 지문 (칸 자리·블록·놓는 법): 되돌릴 때 다시 계산한 설계가 심을 때와 같은지 봄
 */

const MODES = ["wood", "leaf", "hang", "carpet", "bush"];
/**
 * 설계의 지문 (FNV-1a): 칸마다 자리·놓는 법·블록 이름
 * @param {TreeStage[]} stages
 * @returns {Generator<void, number, void>}
 */
function* fingerprintGen(stages) {
  let h = 0x811c9dc5;
  /** @type {Map<string, number>} */
  const bh = new Map();
  let n = 0;
  for (const st of stages) {
    for (const o of st.ops) {
      let b = bh.get(o.block);
      if (b === undefined) {
        b = 0x811c9dc5;
        for (let i = 0; i < o.block.length; i++) b = Math.imul(b ^ o.block.charCodeAt(i), 16777619);
        bh.set(o.block, b);
      }
      h = Math.imul(h ^ ((o.dx & 255) | ((o.dy & 255) << 8) | ((o.dz & 255) << 16) | (MODES.indexOf(o.mode) << 24)), 16777619);
      h = Math.imul(h ^ b, 16777619);
      if (++n % 5000 === 0) yield;
    }
  }
  return h >>> 0;
}

/** 설계의 지문 (한 번에: 시험용) @param {TreeStage[]} stages */
export function planFingerprint(stages) {
  const g = fingerprintGen(stages);
  for (;;) {
    const r = g.next();
    if (r.done) return r.value;
  }
}

/**
 * 뿌리가 닿는 가장 먼 거리 (설계 전에 쓰는 어림: 어떤 씨앗이든 이보다 멀지 않음).
 * 가장 긴 뿌리 0.345H, 잔뿌리 끝은 가장 멀어야 0.352H (가운데쯤에서 0.29 라디안 넘게 틀어 0.42배 뻗음) → 0.355H + 1
 */
export function rootReachOf(height) {
  const H = clamp(Math.round(height), WORLD_TREE.minHeight, WORLD_TREE.maxHeight);
  return Math.ceil(H * 0.355 + 1);
}

/**
 * 세계수 설계 (월드와 상관없음): 밑동 가운데 칸(땅 바로 위) 기준 상대 칸들, 자라는 순서대로 단계에 나눔.
 * 발생기: 조금씩 계산하고 쉼 (게임에서는 system.runJob 으로 여러 틱에 나눠 계산), 끝나면 설계를 돌려줌
 * @param {number} [height]
 * @param {number} [seed]
 * @returns {Generator<void, TreePlan, void>}
 */
export function* treePlanGen(height = WORLD_TREE.height, seed = 1) {
  const H = clamp(Math.round(height), WORLD_TREE.minHeight, WORLD_TREE.maxHeight);
  const s = H / 64;
  const R = rng(seed);
  /** @type {Map<string, {x: number, y: number, z: number, block: string, states?: any, part: string, mode: string, prio: number}>} */
  const cells = new Map();
  const put = (x, y, z, c) => {
    const k = `${x},${y},${z}`;
    const o = cells.get(k);
    if (o && o.prio >= c.prio) return;
    cells.set(k, { x, y, z, ...c });
  };
  const has = (x, y, z) => cells.has(`${x},${y},${z}`);
  const at = (x, y, z) => cells.get(`${x},${y},${z}`);

  /** a → b 굵기 r0 → r1 인 원기둥 (둥근 끝). pick(칸, 가장 가까운 줄 위 점, 거리, 반지름, t) → 칸 */
  const segment = (a, b, r0, r1, pick) => {
    const rm = Math.max(r0, r1) + 1;
    const d = { x: b.x - a.x, y: b.y - a.y, z: b.z - a.z };
    const L2 = d.x * d.x + d.y * d.y + d.z * d.z;
    for (let x = Math.floor(Math.min(a.x, b.x) - rm); x <= Math.ceil(Math.max(a.x, b.x) + rm); x++) {
      for (let y = Math.floor(Math.min(a.y, b.y) - rm); y <= Math.ceil(Math.max(a.y, b.y) + rm); y++) {
        for (let z = Math.floor(Math.min(a.z, b.z) - rm); z <= Math.ceil(Math.max(a.z, b.z) + rm); z++) {
          const t = L2 > 0 ? clamp(((x - a.x) * d.x + (y - a.y) * d.y + (z - a.z) * d.z) / L2, 0, 1) : 0;
          const q = { x: a.x + d.x * t, y: a.y + d.y * t, z: a.z + d.z * t };
          const dist = Math.hypot(x - q.x, y - q.y, z - q.z);
          const r = r0 + (r1 - r0) * t;
          if (dist <= r + 0.3) put(x, y, z, pick({ x, y, z }, q, dist, r, t));
        }
      }
    }
  };
  /** 점들을 이은 줄기·가지 (굵기는 처음 → 끝) */
  const limb = (pts, r0, r1, pick) => {
    for (let i = 0; i + 1 < pts.length; i++) segment(pts[i], pts[i + 1], r0 + (r1 - r0) * (i / (pts.length - 1)), r0 + (r1 - r0) * ((i + 1) / (pts.length - 1)), pick);
  };
  const wood = (part) => () => ({ block: B.wood, part, mode: "wood", prio: 5 });

  // ---- 줄기: 위로 가늘어지고 살짝 휨, 밑동은 뿌리 쪽으로 판자처럼 뻗은 버팀 뿌리 (밑동 가운데 = 0, 0)
  const Ht = Math.round(H * 0.7);
  const r0 = 4.0 * s;
  const rTop = 1.7 * s;
  const flare = 3.4 * s;
  const flareH = 7 * s;
  const sway = 1.2 * s;
  const ph1 = R() * TAU;
  const ph2 = R() * TAU;
  const nRoots = 8;
  const theta0 = R() * TAU;
  /** @type {number[]} */
  const rootAngles = [];
  for (let i = 0; i < nRoots; i++) rootAngles.push(theta0 + (i / nRoots) * TAU + (R() - 0.5) * 0.45);
  const axis = (y) => ({ x: sway * (Math.sin(y * 0.07 + ph1) - Math.sin(ph1)), y, z: sway * (Math.sin(y * 0.055 + ph2) - Math.sin(ph2)) });
  const rTrunk = (y) => {
    const t = clamp(y / Ht, 0, 1);
    let r = rTop + (r0 - rTop) * Math.pow(1 - t, 1.2);
    if (y < flareH) r += 0.9 * s * Math.pow(1 - Math.max(0, y) / flareH, 2);
    return r;
  };
  /** 버팀 뿌리: 뿌리 쪽으로 좁게 (각 뿌리 방향 ±20° 쯤) 뻗음, 위로 갈수록 줄어듦 */
  const buttress = (y, th) => {
    if (y >= flareH || y < 0) return 0;
    let m = 0;
    for (const a of rootAngles) m = Math.max(m, Math.pow(Math.max(0, Math.cos(th - a)), 10));
    return flare * Math.pow(1 - y / flareH, 1.6) * m;
  };
  const trunkPick = (p, q, dist, r) => {
    let block = B.wood;
    // 겉: 이끼 얼룩 (밑동에 많고 위로 갈수록 드묾, 줄기를 타고 오르는 모양)
    if (dist > r - 1.1 && p.y >= -1 && vnoise(p.x / 3.2, p.y / 4.5, p.z / 3.2, 41) > 0.42 + 0.45 * clamp(p.y / (Ht * 0.7), 0, 1)) block = B.moss;
    return { block, part: "trunk", mode: "wood", prio: 5 };
  };
  for (let y = -3; y < Ht; y++) {
    const c = axis(y);
    const rm = rTrunk(y) + flare + 1;
    for (let x = Math.floor(c.x - rm); x <= Math.ceil(c.x + rm); x++) {
      for (let z = Math.floor(c.z - rm); z <= Math.ceil(c.z + rm); z++) {
        const dist = Math.hypot(x - c.x, z - c.z);
        const r = rTrunk(y) + buttress(y, Math.atan2(z - c.z, x - c.x));
        if (dist <= r + 0.35) put(x, y, z, trunkPick({ x, y, z }, c, dist, r));
      }
    }
    yield;
  }
  const rBase = r0 + 0.9 * s;

  // ---- 뿌리: 버팀 뿌리 끝에서 사방으로 구불구불 뻗어 땅에 박힘 (짝수 번째는 아치), 중간에서 잔뿌리
  let rootReach = 0;
  for (let i = 0; i < nRoots; i++) {
    const th = rootAngles[i];
    const L = H * 0.3 * (0.85 + R() * 0.3);
    const arch = i % 2 === 0 ? (1.0 + R() * 1.4) * s : 0.2;
    const rr0 = (1.2 + R() * 0.35) * s;
    const ph = R() * TAU;
    const d0 = rBase + flare * 0.45;
    const pAt = (t) => {
      const d = d0 + (L - d0) * t;
      const ang = th + 0.16 * Math.sin(t * 3.2 + ph);
      const y = 2.0 * s * Math.pow(1 - t, 1.3) - 2.0 * t + arch * Math.sin(Math.PI * t) * (1 - t * 0.3);
      return { x: Math.cos(ang) * d, y, z: Math.sin(ang) * d };
    };
    const pts = [];
    for (let k = 0; k <= 10; k++) pts.push(pAt(k / 10));
    limb(pts, rr0, rr0 * 0.35, wood("root"));
    rootReach = Math.max(rootReach, L + 1);
    // 잔뿌리: 가운데쯤에서 옆으로
    const side = i % 2 === 0 ? 1 : -1;
    const from = pAt(0.45);
    const a2 = th + side * (0.45 + R() * 0.2);
    const L2 = L * 0.42;
    const sub = [];
    for (let k = 0; k <= 5; k++) {
      const t = k / 5;
      sub.push({ x: from.x + Math.cos(a2) * L2 * t, y: from.y + (-1.5 - from.y) * t, z: from.z + Math.sin(a2) * L2 * t });
    }
    limb(sub, rr0 * 0.6, rr0 * 0.3, wood("root"));
    rootReach = Math.max(rootReach, Math.hypot(sub[5].x, sub[5].z) + 1);
    yield;
  }

  // ---- 가지: 나선으로 돌며 비스듬히 위로, 끝·가운데·잔가지 끝에 잎 뭉치 (서로 겹쳐 하나의 너른 잎 지붕)
  /** @type {{c: {x: number, y: number, z: number}, rx: number, up: number, down: number}[]} */
  const blobs = [];
  const blob = (c, rx, up, down) => blobs.push({ c, rx: rx * s, up: up * s, down: down * s });
  /** @type {{x: number, y: number, z: number}[]} */
  const branchMids = [];
  const nb = 7;
  const bth0 = R() * TAU;
  for (let k = 0; k < nb; k++) {
    const f = k / (nb - 1);
    const yb = Ht * (0.5 + 0.44 * f);
    const th = bth0 + k * 137.5 * DEG;
    const elev = (20 + 22 * f + (R() - 0.5) * 8) * DEG;
    const Lb = H * (0.36 - 0.1 * f) * (0.9 + R() * 0.2);
    const c = axis(yb);
    const dir = { x: Math.cos(th) * Math.cos(elev), y: Math.sin(elev), z: Math.sin(th) * Math.cos(elev) };
    const start = { x: c.x + dir.x * rTrunk(yb) * 0.5, y: yb, z: c.z + dir.z * rTrunk(yb) * 0.5 };
    const bAt = (t) => ({ x: start.x + dir.x * Lb * t, y: start.y + dir.y * Lb * t + Lb * 0.1 * t * t, z: start.z + dir.z * Lb * t });
    const pts = [];
    for (let i = 0; i <= 6; i++) pts.push(bAt(i / 6));
    limb(pts, Math.max(1.0, rTrunk(yb) * 0.5), 0.55, wood("branch"));
    // 가지 밑 줄기 겉으로 흘러내린 송진
    const sx = Math.round(c.x + Math.cos(th) * (rTrunk(yb) - 0.4));
    const sz = Math.round(c.z + Math.sin(th) * (rTrunk(yb) - 0.4));
    for (let dy = 1, n = 0; dy <= 8 && n < 2 + (k % 3); dy++) {
      const cell = at(sx, Math.round(yb) - dy, sz);
      if (cell?.part !== "trunk") continue;
      cell.block = B.resin;
      n++;
    }
    const up = (t, h) => ({ ...bAt(t), y: bAt(t).y + h * s });
    blob(up(1, 1.4), 7.4, 4.4, 2.6);
    blob(up(0.78, 1.8), 6.0, 3.8, 2.2);
    blob(up(0.52, 2.0), 4.8, 3.2, 1.8);
    branchMids.push(bAt(0.45));
    // 잔가지 둘
    for (const [tt, turn] of [
      [0.55, 38 + R() * 12],
      [0.85, -(38 + R() * 12)],
    ]) {
      const o = bAt(tt);
      const a = th + turn * DEG;
      const e2 = elev + 12 * DEG;
      const L2 = Lb * 0.4;
      const tip = { x: o.x + Math.cos(a) * Math.cos(e2) * L2, y: o.y + Math.sin(e2) * L2, z: o.z + Math.sin(a) * Math.cos(e2) * L2 };
      segment(o, tip, 0.7, 0.5, wood("branch"));
      blob({ ...tip, y: tip.y + 1 * s }, 5.2, 3.4, 2.0);
    }
    yield;
  }
  // 꼭대기 세 갈래 + 가운데 큰 잎 뭉치
  const topC = axis(Ht - 2);
  for (let j = 0; j < 3; j++) {
    const a = bth0 + (j * 120 + 60) * DEG;
    const e = 58 * DEG;
    const Lc = H * 0.17;
    const tip = { x: topC.x + Math.cos(a) * Math.cos(e) * Lc, y: topC.y + Math.sin(e) * Lc, z: topC.z + Math.sin(a) * Math.cos(e) * Lc };
    segment(topC, tip, 1.4 * s, 0.6, wood("branch"));
    blob({ ...tip, y: tip.y + 1 * s }, 7.6, 4.6, 3.0);
  }
  blob({ ...axis(Ht), y: Ht + 4 * s }, 8.6, 5.4, 3.6);

  // ---- 잎 뭉치: 진달래 잎 (꽃 핀 잎 섞음), 위는 둥글고 밑은 납작 (늘어진 잎 지붕), 가장자리는 들쭉날쭉, 겉에 빛나는 열매
  for (const bl of blobs) {
    const { c, rx, up, down } = bl;
    const cx = Math.round(c.x);
    const cy = Math.round(c.y);
    const cz = Math.round(c.z);
    for (let x = cx - Math.ceil(rx) - 1; x <= cx + Math.ceil(rx) + 1; x++) {
      for (let y = cy - Math.ceil(down) - 1; y <= cy + Math.ceil(up) + 1; y++) {
        for (let z = cz - Math.ceil(rx) - 1; z <= cz + Math.ceil(rx) + 1; z++) {
          const ry = y >= c.y ? up : down;
          const e = ((x - c.x) / rx) ** 2 + ((y - c.y) / ry) ** 2 + ((z - c.z) / rx) ** 2;
          const n = hash3(x, y, z, 7);
          if (e > 1 + (n - 0.5) * 0.35) continue;
          if (e > 0.72 && n < 0.18) continue;
          if (e > 0.62 && hash3(x, y, z, 13) < WORLD_TREE.fruit) {
            put(x, y, z, { block: B.fruit, part: "leaf", mode: "leaf", prio: 4 });
            continue;
          }
          put(x, y, z, { block: hash3(x, y, z, 11) < 0.24 ? B.bloom : B.leaf, states: LEAF_STATES, part: "leaf", mode: "leaf", prio: 3 });
        }
      }
      yield;
    }
  }

  // ---- 뿌리 위 이끼 (위가 빈 뿌리 칸)
  let n = 0;
  for (const c of cells.values()) {
    if (++n % 4000 === 0) yield;
    if (c.part === "root" && c.y >= 0 && !has(c.x, c.y + 1, c.z) && hash3(c.x, c.y, c.z, 17) > 0.72) c.block = B.moss;
  }
  yield;

  // ---- 잎 밑 발광 열매 덩굴 (2 ~ 5칸), 가지 밑 포자 꽃
  const leaves = [...cells.values()].filter((c) => c.part === "leaf" && c.block !== B.fruit);
  yield;
  n = 0;
  for (const c of leaves) {
    if (++n % 800 === 0) yield;
    if (c.y < 10 * s || has(c.x, c.y - 1, c.z) || hash3(c.x, c.y, c.z, 19) >= WORLD_TREE.berries) continue;
    const len = 2 + Math.floor(hash3(c.x, c.y, c.z, 23) * 4);
    // 밑이 트인 곳에만 (덩굴 끝 밑으로 한 칸 더 빔: 잎 사이에 끼어 보이지 않는 덩굴은 만들지 않음)
    let free = true;
    for (let j = 1; j <= len + 1 && free; j++) if (has(c.x, c.y - j, c.z)) free = false;
    if (!free) continue;
    // 덩굴을 매다는 칸: 이끼 블록 (잎 밑에는 덩굴이 붙지 못할 수 있음 — 단단한 블록이 받침)
    c.block = B.moss;
    delete c.states;
    for (let j = 1; j <= len; j++) put(c.x, c.y - j, c.z, { block: j === len ? B.vineTip : B.vine, states: { growing_plant_age: j === len ? 25 : 0 }, part: "hang", mode: "hang", prio: 2 });
  }
  for (const m of branchMids) {
    const x = Math.round(m.x);
    const z = Math.round(m.z);
    let low;
    for (let y = Math.round(m.y) + 3; y >= Math.round(m.y) - 4; y--) if (at(x, y, z)?.part === "branch") low = y;
    if (low !== undefined && !has(x, low - 1, z)) put(x, low - 1, z, { block: B.blossom, part: "hang", mode: "hang", prio: 2 });
  }

  // ---- 밑동 둘레: 이끼 깔개 고리, 뿌리 사이 반딧불이 덤불·꽃 핀 진달래 (땅 높이는 놓을 때 찾음, 깔개는 덤불 자리를 비움)
  /** @type {TreeOp[]} */
  const bushes = [];
  /** @type {TreeOp[]} */
  const ground = [];
  for (let i = 0; i < nRoots; i++) {
    // 뿌리 사이 (잔뿌리가 지나가지 않는 칸을 찾아: 거리·각도를 조금씩 바꿔 봄)
    const a0 = rootAngles[i] + Math.PI / nRoots;
    const d0 = rBase + 4 + hash3(i, 1, 0, 31) * 3;
    for (const [dd, da] of [
      [0, 0],
      [1.5, 0],
      [-1.5, 0],
      [0, 0.15],
      [0, -0.15],
      [3, 0],
      [3, 0.15],
      [3, -0.15],
    ]) {
      const x = Math.round(Math.cos(a0 + da) * (d0 + dd));
      const z = Math.round(Math.sin(a0 + da) * (d0 + dd));
      let free = true;
      for (let y = -1; y <= 3 && free; y++) if (has(x, y, z)) free = false;
      if (!free) continue;
      bushes.push({ dx: x, dy: 0, dz: z, block: i % 2 === 0 ? B.firefly : B.azalea, mode: "bush" });
      break;
    }
  }
  // 깔개 자리: 평평한 땅이면 놓을 수 있는 곳만 (뿌리가 찾는 칸 (위 2 ~ 땅)을 다 채운 곳은 뺌 — 뿌리 위·아치 밑에는 놓임)
  const fits = (x, z) => [2, 1, 0].some((y) => !has(x, y, z) && (y === 0 || has(x, y - 1, z)));
  const rg = Math.ceil(rBase + 10);
  for (let x = -rg; x <= rg; x++) {
    for (let z = -rg; z <= rg; z++) {
      const r = Math.hypot(x, z);
      if (r < rBase + 1.5 || r > rBase + 9 || hash3(x, 0, z, 29) < 0.42) continue;
      if (bushes.some((b) => b.dx === x && b.dz === z) || !fits(x, z)) continue;
      ground.push({ dx: x, dy: 0, dz: z, block: B.carpet, mode: "carpet" });
    }
  }
  yield;
  ground.push(...bushes);

  // ---- 단계: 뿌리 → 줄기 (6칸씩 아래부터) → 가지 → 잎 (셋) → 빛 (열매·덩굴·포자 꽃) → 이끼 (정렬마다 쉼)
  const all = [...cells.values()];
  yield;
  const op = (c) => /** @type {TreeOp} */ ({ dx: c.x, dy: c.y, dz: c.z, block: c.block, ...(c.states ? { states: c.states } : {}), mode: c.mode });
  /** @type {TreeStage[]} */
  const stages = [];
  const roots = all.filter((c) => c.part === "root").sort((a, b) => Math.hypot(a.x, a.z) - Math.hypot(b.x, b.z) || a.y - b.y);
  stages.push({ name: "뿌리", kind: "root", sound: "block.mangrove_roots.place", pitch: 0.8, y: 0, ops: roots.map(op) });
  yield;
  const trunk = all.filter((c) => c.part === "trunk").sort((a, b) => a.y - b.y);
  yield;
  const slice = Math.max(4, Math.round(6 * s));
  let ti = 0;
  for (let y0 = -4; y0 < Ht; y0 += slice) {
    const part = [];
    while (ti < trunk.length && trunk[ti].y < y0 + slice) part.push(trunk[ti++]);
    if (part.length) stages.push({ name: "줄기", kind: "trunk", sound: "dig.wood", pitch: 0.6 + 0.5 * clamp((y0 + slice) / Ht, 0, 1), y: y0 + slice, ops: part.map(op) });
  }
  const branch = all.filter((c) => c.part === "branch").sort((a, b) => a.y - b.y);
  stages.push({ name: "가지", kind: "branch", sound: "place.wood", pitch: 0.9, y: Math.round(Ht * 0.75), ops: branch.map(op) });
  yield;
  const leafAll = all.filter((c) => c.part === "leaf" && c.block !== B.fruit);
  yield;
  leafAll.sort((a, b) => a.y - b.y);
  yield;
  for (let i = 0; i < 3; i++) {
    const part = leafAll.slice(Math.floor((leafAll.length * i) / 3), Math.floor((leafAll.length * (i + 1)) / 3));
    if (part.length) stages.push({ name: "잎", kind: "leaf", sound: "place.azalea_leaves", pitch: 0.9 + i * 0.15, y: part[Math.floor(part.length / 2)].y, ops: part.map(op) });
    yield;
  }
  const glow = all.filter((c) => c.block === B.fruit).concat(all.filter((c) => c.part === "hang").sort((a, b) => b.y - a.y));
  stages.push({ name: "빛", kind: "glow", sound: "chime.amethyst_block", pitch: 1.2, y: Math.round(Ht * 0.9), ops: glow.map(op) });
  stages.push({ name: "이끼", kind: "ground", sound: "place.moss", pitch: 1, y: 0, ops: ground });
  yield;

  let reach = 0;
  let top = 0;
  let bottom = 0;
  let woodCount = 0;
  /** @type {Record<string, number>} */
  const counts = {};
  n = 0;
  for (const c of all) {
    if (++n % 5000 === 0) yield;
    reach = Math.max(reach, Math.hypot(c.x, c.z));
    top = Math.max(top, c.y);
    bottom = Math.min(bottom, c.y);
    counts[c.block] = (counts[c.block] ?? 0) + 1;
    if (c.mode === "wood") woodCount++;
  }
  for (const g of ground) counts[g.block] = (counts[g.block] ?? 0) + 1;
  const canopy = Math.max(...blobs.map((b) => Math.hypot(b.c.x, b.c.z) + b.rx));
  const fp = yield* fingerprintGen(stages);
  return {
    H,
    seed,
    stages,
    reach: Math.ceil(Math.max(reach, rg)),
    rootReach: Math.ceil(rootReach),
    canopy: Math.ceil(canopy),
    trunkR: rBase,
    top,
    bottom,
    total: all.length + ground.length,
    woodCount,
    counts,
    branches: branchMids,
    fp,
  };
}

/**
 * 세계수 설계를 한 번에 (시험·미리보기용. 게임에서는 planAsync 로 나눠서)
 * @param {number} [height]
 * @param {number} [seed]
 * @returns {TreePlan}
 */
export function treePlan(height = WORLD_TREE.height, seed = 1) {
  const g = treePlanGen(height, seed);
  for (;;) {
    const r = g.next();
    if (r.done) return r.value;
  }
}

/**
 * 발생기를 여러 틱에 나눠 끝까지 돌리고 그 결과를 돌려줌 (system.runJob). runJob 을 못 쓰면 한 번에
 * @template T
 * @param {Generator<void, T, void>} gen
 * @returns {Promise<T>}
 */
function runGen(gen) {
  return new Promise((resolve, reject) => {
    function* job() {
      for (;;) {
        let r;
        try {
          r = gen.next();
        } catch (e) {
          reject(e);
          return;
        }
        if (r.done) {
          resolve(r.value);
          return;
        }
        yield;
      }
    }
    try {
      system.runJob(job());
    } catch {
      try {
        for (;;) {
          const r = gen.next();
          if (r.done) {
            resolve(r.value);
            return;
          }
        }
      } catch (e) {
        reject(e);
      }
    }
  });
}

/**
 * 세계수 설계를 여러 틱에 나눠 (폰에서 한 틱이 길어지지 않게)
 * @param {number} height
 * @param {number} seed
 * @returns {Promise<TreePlan>}
 */
export function planAsync(height, seed) {
  return runGen(treePlanGen(height, seed));
}

// ------------------------------------------------------------ 자리 찾기
/** 블록 (불러오지 않은 곳이면 undefined) */
function blockAt(dim, x, y, z) {
  try {
    return dim.getBlock({ x, y, z });
  } catch {
    return undefined;
  }
}

/**
 * x, z 칸의 땅 바로 위 높이: y0 에서 (막혀 있으면 16칸까지 위로 올라가) 아래로 48칸까지 땅을 찾음
 * (풀·꽃·잎·다른 나무 줄기·큰 버섯·대나무·작물 등 식물·물은 지나감)
 * @returns {number | undefined}
 */
export function groundY(dim, x, z, y0) {
  let y = Math.floor(y0);
  const open = (b) => !!b && passable(b);
  for (let i = 0; i < 16 && !open(blockAt(dim, x, y, z)); i++) y++;
  if (!open(blockAt(dim, x, y, z))) return undefined;
  for (let i = 0; i < 48; i++, y--) {
    const b = blockAt(dim, x, y - 1, z);
    if (!b) return undefined;
    if (isGround(b)) return y;
  }
  return undefined;
}

/**
 * 심을 자리: 바라보는 블록 (lookRange 안). 가까우면 (minD 안) 바라보는 쪽으로 minD 칸 앞, 아무것도 안 보이면 minD 칸 앞
 * @param {import("@minecraft/server").Player} player
 * @param {number} minD
 * @returns {{base?: {x: number, y: number, z: number}, reason?: string}}
 */
export function treeSpot(player, minD) {
  const dim = player.dimension;
  const l = player.location;
  let v = { x: 0, y: 0, z: 1 };
  try {
    v = player.getViewDirection();
  } catch {
    // 기본: 남쪽
  }
  let hx = v.x;
  let hz = v.z;
  const hl = Math.hypot(hx, hz);
  if (hl < 0.05) {
    // 바로 아래·위를 봄: 몸 방향
    let yaw = 0;
    try {
      yaw = player.getRotation().y * DEG;
    } catch {
      yaw = 0;
    }
    hx = -Math.sin(yaw);
    hz = Math.cos(yaw);
  } else {
    hx /= hl;
    hz /= hl;
  }
  let hit;
  try {
    hit = player.getBlockFromViewDirection({ maxDistance: WORLD_TREE.lookRange, includeLiquidBlocks: true, includePassableBlocks: false });
  } catch {
    hit = undefined;
  }
  let x;
  let z;
  let y0;
  const b = hit?.block;
  if (b && Math.hypot(b.location.x + 0.5 - l.x, b.location.z + 0.5 - l.z) >= minD) {
    x = b.location.x;
    z = b.location.z;
    y0 = b.location.y + 1;
  } else {
    x = Math.floor(l.x + hx * minD);
    z = Math.floor(l.z + hz * minD);
    y0 = Math.floor(l.y);
  }
  const y = groundY(dim, x, z, y0);
  if (y === undefined) return { reason: "심을 땅을 찾지 못했습니다 (허공·불러오지 않은 곳)" };
  return { base: { x, y, z } };
}

// ------------------------------------------------------------ 불러오기 (틱킹 영역)
const NOT_LOADED = "나무가 자랄 곳이 다 불러와지지 않았습니다";
const AREAS_KEY = "rpg:wt_areas";
/** t 틱 기다림 */
const wait = (t) => new Promise((r) => system.runTimeout(() => r(undefined), t));
function isLoaded(dim, loc) {
  try {
    return !!dim.isChunkLoaded(loc);
  } catch {
    return false;
  }
}
/**
 * 나무 자리 (밑동에서 r 칸 원) 에 걸친 청크가 모두 불러와졌는지: 청크마다 밑동에 가장 가까운 칸이 원 안이면 그 청크를 봄
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} base
 * @param {number} r
 */
export function footprintLoaded(dim, base, r) {
  for (let cx = Math.floor((base.x - r) / 16); cx <= Math.floor((base.x + r) / 16); cx++) {
    for (let cz = Math.floor((base.z - r) / 16); cz <= Math.floor((base.z + r) / 16); cz++) {
      const px = clamp(base.x, cx * 16, cx * 16 + 15);
      const pz = clamp(base.z, cz * 16, cz * 16 + 15);
      if (Math.hypot(px - base.x, pz - base.z) > r) continue;
      if (!isLoaded(dim, { x: px, y: base.y, z: pz })) return false;
    }
  }
  return true;
}
/** 쓰는 중인 틱킹 영역 (월드를 나가 남으면 다음 접속 때 치움) */
function noteArea(dim, name, on) {
  try {
    const v = world.getDynamicProperty(AREAS_KEY);
    /** @type {[string, string][]} */
    let list = typeof v === "string" ? JSON.parse(v) : [];
    if (!Array.isArray(list)) list = [];
    list = list.filter((a) => Array.isArray(a) && a[1] !== name);
    if (on) list.push([dim.id, name]);
    world.setDynamicProperty(AREAS_KEY, list.length ? JSON.stringify(list) : undefined);
  } catch {
    // 무시
  }
}
let areaSeq = 0;
/**
 * 나무 자리를 틱킹 영역 (나무를 덮는 네모, 청크 100개 제한 안: 높이 96 이어도 64개) 으로 붙잡고 다 불러와질 때까지 기다림
 * (못 만들면 지금 불러온 것만으로)
 * @returns {Promise<{name?: string, loaded: boolean}>}
 */
async function holdArea(dim, base, reach) {
  /** @type {string | undefined} */
  let name = `rpg_wt_${Date.now().toString(36)}${(++areaSeq).toString(36)}`;
  const r = Math.ceil(reach) + 1;
  try {
    const res = dim.runCommand(`tickingarea add ${base.x - r} ${base.y} ${base.z - r} ${base.x + r} ${base.y} ${base.z + r} ${name} true`);
    if (res && res.successCount === 0) throw new Error("tickingarea add failed");
    noteArea(dim, name, true);
  } catch {
    name = undefined;
  }
  const limit = name ? WORLD_TREE.loadWait : 10;
  for (let t = 0; t < limit && !footprintLoaded(dim, base, reach); t += 5) await wait(5);
  return { name, loaded: footprintLoaded(dim, base, reach) };
}
/** 불러오지 못한 까닭 */
function loadWhy(area) {
  return `${NOT_LOADED}${area.name ? " (시간 초과)" : " (틱킹 영역을 만들지 못함 — /tickingarea list 로 10개가 찼는지 보세요)"}`;
}
function releaseArea(dim, name) {
  if (!name) return;
  try {
    dim.runCommand(`tickingarea remove ${name}`);
  } catch {
    // 이미 없음
  }
  noteArea(dim, name, false);
}
/** 월드를 불러올 때: 지난번에 남은 틱킹 영역을 치움 */
function cleanupAreas() {
  let list;
  try {
    const v = world.getDynamicProperty(AREAS_KEY);
    list = typeof v === "string" ? JSON.parse(v) : [];
  } catch {
    list = [];
  }
  if (!Array.isArray(list) || !list.length) return;
  try {
    world.setDynamicProperty(AREAS_KEY, undefined);
  } catch {
    // 무시
  }
  system.runTimeout(() => {
    for (const a of list) {
      if (!Array.isArray(a) || typeof a[0] !== "string" || typeof a[1] !== "string" || !/^rpg_wt_[a-z0-9]+$/.test(a[1])) continue;
      try {
        world.getDimension(a[0]).runCommand(`tickingarea remove ${a[1]}`);
      } catch {
        // 이미 없음
      }
    }
  }, 20);
}

/**
 * 바로 알 수 있는 심을 수 없는 까닭 (없으면 undefined): 네더, 세계 높이, 보스 필드와 겹침, (loaded 면) 불러오지 않은 곳, 물 속
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} base
 * @param {TreePlan} plan
 * @param {boolean} [loaded] 나무 자리가 다 불러와졌는지도 봄
 */
export function treeProblem(dim, base, plan, loaded = true) {
  if (dim.id === "minecraft:nether") return "네더에서는 자라지 않습니다 (용암·천장)";
  const hr = dim.heightRange ?? { min: -64, max: 320 };
  if (base.y + plan.top + 2 > hr.max) return `너무 높은 곳입니다 (꼭대기가 세계 높이 ${hr.max}에 닿음 — y ${hr.max - plan.top - 2} 아래를 보세요)`;
  if (base.y + plan.bottom - 1 < hr.min) return "너무 낮은 곳입니다 (뿌리가 세계 바닥에 닿음)";
  for (const a of altars()) {
    if (a.dim !== dim.id) continue;
    const { radius, height } = arenaSize(a);
    if (base.y + plan.top < a.y - 6 || base.y + plan.bottom > a.y + height + 2) continue;
    if (Math.hypot(a.x - base.x, a.z - base.z) < radius + plan.reach + 3) return `보스 필드(${BOSSES[a.key]?.name ?? a.key}, ${a.x} ${a.y} ${a.z})와 겹칩니다`;
  }
  if (loaded && !footprintLoaded(dim, base, plan.reach)) return NOT_LOADED;
  if (blockAt(dim, base.x, base.y, base.z)?.isLiquid) return "물(용암) 속에는 심을 수 없습니다 — 물 밖의 땅을 보세요";
  return undefined;
}

/** @typedef {{why?: string, built?: number, blocked?: number}} TreeSurvey */

/**
 * 심기 전 살핌 (여러 틱에 나눠): 줄기·뿌리 자리의 건물·설치물 · 둘레 용암 · 잎 자리가 막힌 정도
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} base
 * @param {TreePlan} plan
 * @returns {Generator<void, TreeSurvey, void>}
 */
function* surveyGen(dim, base, plan) {
  const lavaWhy = (x, y, z) => `가까이에 용암이 있습니다 (${x} ${y} ${z} — 나무에 불이 붙을 수 있음)`;
  /** @type {Map<string, number>} */
  const built = new Map();
  let nBuilt = 0;
  /** @type {{x: number, y: number, z: number} | undefined} */
  let where;
  let i = 0;
  // 줄기·뿌리·가지 자리: 건물·설치물 수
  for (const st of plan.stages) {
    for (const o of st.ops) {
      if (o.mode !== "wood") continue;
      if (++i % 200 === 0) yield;
      const x = base.x + o.dx;
      const y = base.y + o.dy;
      const z = base.z + o.dz;
      const b = blockAt(dim, x, y, z);
      if (!b) return { why: NOT_LOADED };
      if (isLava(b)) return { why: lavaWhy(x, y, z) };
      const id = b.typeId;
      if (!isNatural(b) && !isKept(id)) {
        nBuilt++;
        built.set(id, (built.get(id) ?? 0) + 1);
        where ??= { x, y, z };
      }
    }
  }
  // 땅 가까이의 용암 (뿌리 둘레, 2칸 간격)
  const R = plan.rootReach + 2;
  for (let dx = -R; dx <= R; dx += 2) {
    for (let dz = -R; dz <= R; dz += 2) {
      if (dx * dx + dz * dz > R * R) continue;
      if (++i % 40 === 0) yield;
      for (let dy = -3; dy <= 2; dy++) {
        if (isLava(blockAt(dim, base.x + dx, base.y + dy, base.z + dz))) return { why: lavaWhy(base.x + dx, base.y + dy, base.z + dz) };
      }
    }
  }
  if (nBuilt > WORLD_TREE.maxBuilt) {
    const top = [...built]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([id, c]) => `${id.replace("minecraft:", "")} ${c}`)
      .join(", ");
    const w = /** @type {{x: number, y: number, z: number}} */ (where);
    return { why: `줄기·뿌리 자리에 건물·설치물이 있습니다 (블록 ${nBuilt}개: ${top}${built.size > 3 ? " …" : ""} · 예: ${w.x} ${w.y} ${w.z}) — 빈 땅을 보세요` };
  }
  // 잎 자리 (고르게 뽑아서): 막힌 곳이 많으면 산·동굴·건물 안
  /** @type {TreeOp[]} */
  const leafOps = [];
  for (const st of plan.stages) for (const o of st.ops) if (o.mode === "leaf") leafOps.push(o);
  const step = Math.max(1, Math.floor(leafOps.length / WORLD_TREE.skySamples));
  let n = 0;
  let blocked = 0;
  for (let k = 0; k < leafOps.length; k += step) {
    if (++i % 40 === 0) yield;
    const o = leafOps[k];
    const b = blockAt(dim, base.x + o.dx, base.y + o.dy, base.z + o.dz);
    if (!b) return { why: NOT_LOADED };
    if (isLava(b)) return { why: lavaWhy(base.x + o.dx, base.y + o.dy, base.z + o.dz) };
    n++;
    if (!isSoft(b)) blocked++;
  }
  const frac = n ? blocked / n : 0;
  if (frac > WORLD_TREE.maxBlocked) return { why: `잎이 자랄 곳의 ${Math.round(frac * 100)}%가 막혀 있습니다 (산·동굴·건물 안) — 트인 곳을 보세요` };
  return { built: nBuilt, blocked: frac };
}

/**
 * 심기 전 살핌 (여러 틱에 나눠)
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} base
 * @param {TreePlan} plan
 * @returns {Promise<TreeSurvey>}
 */
export function treeSurvey(dim, base, plan) {
  return runGen(surveyGen(dim, base, plan));
}

// ------------------------------------------------------------ 기록 (월드: 최근 keep 그루 + 칸마다 원래 블록)
const LIST_KEY = "rpg:world_trees";
const logKey = (id, k) => (k ? `rpg:wt_${id}.${k}` : `rpg:wt_${id}`);
const LOG_PART = 30000;
/**
 * @typedef {{id: string, d: string, x: number, y: number, z: number, h: number, s: number, r: number, by: string, v: number, fp: number, n: number}} TreeRecord
 *   h = 높이, s = 씨앗, r = 잎 끝까지 거리, by = 심은 사람, fp = 설계 지문, n = 칸 수
 * @typedef {{codes: Uint16Array, palette: any[][], pidx: Map<string, number>, dys: Map<number, number>,
 *   extras: Map<number, {x: number, y: number, z: number, code: number}[]>, enc?: {w: number, upTo: number, prefix: string}}} TreeLog
 *   codes[칸 번호] = 0 (놓지 않음) · k (놓음, 원래 블록 = palette[k - 1] = [이름, 상태, (1 = 물에 잠김)], palette[0] = 공기),
 *   dys = 깔개·덤불을 놓은 높이, extras = 함께 치운 칸, enc = 저장할 글의 앞부분 (자라는 동안 끝난 단계는 다시 줄이지 않음)
 */

/** 기록된 세계수 (오래된 것부터) @returns {TreeRecord[]} */
export function treeRecords() {
  let a;
  try {
    const v = world.getDynamicProperty(LIST_KEY);
    a = typeof v === "string" ? JSON.parse(v) : [];
  } catch {
    a = [];
  }
  if (!Array.isArray(a)) return [];
  return a.filter((t) => t && typeof t.id === "string" && /^[a-z0-9]+$/.test(t.id) && typeof t.d === "string" && [t.x, t.y, t.z, t.h, t.s, t.r, t.fp, t.n].every(Number.isFinite));
}
/** 마지막으로 심은 세계수 기록 */
function lastRecord() {
  const list = treeRecords();
  return list.length ? list[list.length - 1] : undefined;
}
function saveRecords(list) {
  try {
    world.setDynamicProperty(LIST_KEY, list.length ? JSON.stringify(list) : undefined);
  } catch (e) {
    console.warn(`[rpg] world tree record: ${e}`);
  }
}
function dropLog(id) {
  for (let k = 0; k < 64; k++) {
    try {
      if (world.getDynamicProperty(logKey(id, k)) === undefined) break;
      world.setDynamicProperty(logKey(id, k), undefined);
    } catch {
      break;
    }
  }
}
/** @param {TreeRecord} rec */
function addRecord(rec) {
  const list = treeRecords();
  list.push(rec);
  while (list.length > WORLD_TREE.keep) {
    const old = list.shift();
    if (old) dropLog(old.id);
  }
  saveRecords(list);
}
function dropRecord(id) {
  saveRecords(treeRecords().filter((t) => t.id !== id));
  dropLog(id);
}

/** 빈 기록 (칸 n 개) @returns {TreeLog} */
function newLog(n) {
  return { codes: new Uint16Array(n), palette: [[AIR, {}]], pidx: new Map([[`${AIR}{}`, 1]]), dys: new Map(), extras: new Map() };
}
/** 블록 → 팔레트 번호 (1 = 공기): 이름·상태·물에 잠겼는지 */
function codeOf(log, b) {
  if (!b || b.isAir) return 1;
  let st = {};
  try {
    st = b.permutation?.getAllStates() ?? {};
  } catch {
    st = {};
  }
  let wet = false;
  try {
    wet = !!b.isWaterlogged;
  } catch {
    wet = false;
  }
  const k = b.typeId + JSON.stringify(st) + (wet ? "~w" : "");
  let c = log.pidx.get(k);
  if (!c) {
    log.palette.push(wet ? [b.typeId, st, 1] : [b.typeId, st]);
    c = log.palette.length;
    log.pidx.set(k, c);
  }
  return c;
}
/** 팔레트 번호 → 블록 (알 수 없는 블록 — 다른 버전에서 이름이 바뀜 등 — 이면 undefined) */
function permOfCode(log, c) {
  const e = log.palette[c - 1];
  if (!e) return undefined;
  try {
    return permOf(e[0], e[1]);
  } catch {
    return undefined;
  }
}
/** 그 블록이 물에 잠겨 있었는지 (늘 물 속에 사는 식물 포함) */
function wetOfCode(log, c) {
  const e = log.palette[c - 1];
  return !!e && (e[2] === 1 || AQUATIC.has(e[0]));
}
/** 원래 블록으로 되돌려 놓음 (물에 잠겨 있었으면 물도). 알 수 없는 블록이면 false, 놓지 못하면 오류를 던짐 */
function restoreAt(d, loc, log, c) {
  const p = permOfCode(log, c);
  if (!p) return false;
  d.setBlockPermutation(loc, p);
  if (wetOfCode(log, c)) {
    try {
      d.getBlock(loc)?.setWaterlogged(true);
    } catch {
      // 물을 담지 못하는 블록
    }
  }
  return true;
}

/** 칸 번호들 → 짧은 글: 한 칸 = w 자리 36진, 같은 번호가 3번 넘게 이어지면 "번호*개수(36진)," */
export function encodeCodes(codes, w = 1) {
  /** @type {string[]} */
  const out = [];
  for (let i = 0; i < codes.length; ) {
    let j = i;
    while (j < codes.length && codes[j] === codes[i]) j++;
    const c = codes[i].toString(36).padStart(w, "0");
    const n = j - i;
    out.push(n >= 3 ? `${c}*${n.toString(36)},` : c.repeat(n));
    i = j;
  }
  return out.join("");
}
/** 짧은 글 → 칸 번호들 (n 칸). 틀린 글이면 undefined */
export function decodeCodes(s, n, w = 1) {
  const out = new Uint16Array(n);
  let k = 0;
  for (let i = 0; i < s.length; ) {
    const tok = s.slice(i, i + w);
    if (tok.length < w || !/^[0-9a-z]+$/.test(tok)) return undefined;
    const v = parseInt(tok, 36);
    i += w;
    let cnt = 1;
    if (s[i] === "*") {
      const e = s.indexOf(",", i);
      if (e < 0) return undefined;
      cnt = parseInt(s.slice(i + 1, e), 36);
      i = e + 1;
      if (!(cnt > 0)) return undefined;
    }
    if (k + cnt > n) return undefined;
    out.fill(v, k, k + cnt);
    k += cnt;
  }
  return k === n ? out : undefined;
}

/**
 * 기록을 월드에 (30000자씩 나눠서). stable = 이 칸 번호 앞은 더 바뀌지 않음 (자라는 동안 끝난 단계: 다시 줄이지 않고 이어 붙임)
 */
function saveLog(id, base, log, stable = 0) {
  const w = log.palette.length < 36 ? 1 : 2;
  let enc = log.enc;
  if (!enc || enc.w !== w || enc.upTo > stable) enc = log.enc = { w, upTo: 0, prefix: "" };
  if (stable > enc.upTo) {
    enc.prefix += encodeCodes(log.codes.subarray(enc.upTo, stable), w);
    enc.upTo = stable;
  }
  /** @type {number[][]} */
  const e = [];
  for (const [i, list] of log.extras) for (const x of list) e.push([i, x.x - base.x, x.y - base.y, x.z - base.z, x.code]);
  const data = JSON.stringify({ p: log.palette, w, c: enc.prefix + encodeCodes(log.codes.subarray(stable), w), y: [...log.dys], e });
  const parts = Math.max(1, Math.ceil(data.length / LOG_PART));
  try {
    for (let k = 0; k < parts; k++) world.setDynamicProperty(logKey(id, k), data.slice(k * LOG_PART, (k + 1) * LOG_PART));
    for (let k = parts; k < 64 && world.getDynamicProperty(logKey(id, k)) !== undefined; k++) world.setDynamicProperty(logKey(id, k), undefined);
    return true;
  } catch (err) {
    console.warn(`[rpg] world tree record: ${err}`);
    return false;
  }
}
/**
 * 월드의 기록 (칸 n 개). 없거나 망가졌으면 undefined
 * @param {TreeRecord} rec
 * @returns {TreeLog | undefined}
 */
export function loadLog(rec) {
  try {
    let s = "";
    for (let k = 0; k < 64; k++) {
      const v = world.getDynamicProperty(logKey(rec.id, k));
      if (typeof v !== "string") break;
      s += v;
    }
    if (!s) return undefined;
    const o = JSON.parse(s);
    if (!o || !Array.isArray(o.p) || !o.p.every((p) => Array.isArray(p) && typeof p[0] === "string" && p[1] && typeof p[1] === "object" && (p[2] === undefined || p[2] === 1))) return undefined;
    if ((o.w !== 1 && o.w !== 2) || typeof o.c !== "string" || !Array.isArray(o.y) || !Array.isArray(o.e)) return undefined;
    const codes = decodeCodes(o.c, rec.n, o.w);
    if (!codes) return undefined;
    for (let i = 0; i < codes.length; i++) if (codes[i] > o.p.length) return undefined;
    /** @type {TreeLog} */
    const log = { codes, palette: o.p, pidx: new Map(o.p.map((p, i) => [p[0] + JSON.stringify(p[1]) + (p[2] === 1 ? "~w" : ""), i + 1])), dys: new Map(), extras: new Map() };
    for (const d of o.y) if (Array.isArray(d) && Number.isInteger(d[0]) && Number.isInteger(d[1])) log.dys.set(d[0], d[1]);
    for (const x of o.e) {
      if (!Array.isArray(x) || x.length !== 5 || !x.every(Number.isInteger) || x[4] < 1 || x[4] > o.p.length) continue;
      const list = log.extras.get(x[0]) ?? [];
      list.push({ x: rec.x + x[1], y: rec.y + x[2], z: rec.z + x[3], code: x[4] });
      log.extras.set(x[0], list);
    }
    return log;
  } catch {
    return undefined;
  }
}

// ------------------------------------------------------------ 자라기 · 되돌리기
/** 블록 이름·상태 → 바꿈 (틀리면 상태 없이) */
const perms = new Map();
function permOf(name, states) {
  const k = name + JSON.stringify(states ?? {});
  let p = perms.get(k);
  if (p) return p;
  try {
    p = BlockPermutation.resolve(name, states ?? {});
  } catch {
    p = BlockPermutation.resolve(name);
  }
  perms.set(k, p);
  return p;
}

/**
 * 지금 자라는(되돌리는) 나무: 한 번에 하나
 * @type {any}
 */
let job;
/** 설계·살핌·불러오기 중인 일의 표 (0 = 없음, 한 번에 하나): 끝날 때 자기 표일 때만 지움 (다른 사람의 준비를 지우지 않게) */
let preparing = 0;
let prepSeq = 0;
function beginPrep() {
  preparing = ++prepSeq;
  return preparing;
}
function endPrep(me) {
  if (preparing === me) preparing = 0;
}
/** 열려 있는 확인 창 수 (시험용) */
let asking = 0;
let idc = 0;
/** 명령 이름 (commands.js 가 알려 줌): 영어 이름과, 한글 이름이 등록되었으면 그 이름 */
let cmdName = "/worldtree";
/** @type {string | undefined} */
let cmdAlias;
export function setTreeCommandName(name, alias) {
  cmdName = name;
  cmdAlias = alias;
}
/** 되돌리는 명령 안내 */
function undoHint() {
  return `§e${cmdName} undo${cmdAlias ? ` §7(또는 ${cmdAlias} undo)` : ""}`;
}

/** 시험용: 지금 일 */
export function treeJob() {
  return job;
}
/** 시험용: 무언가 하는 중 (계산·확인 창·자라기·되돌리기) */
export function treeBusy() {
  return !!job || preparing !== 0 || asking > 0;
}
/** 마지막으로 심은 세계수 (기록) */
export function lastTreeOf() {
  const r = lastRecord();
  if (!r) return undefined;
  return { ...r, base: { x: r.x, y: r.y, z: r.z }, H: r.h };
}

/** 나무 둘레 사람 (96칸 안) */
function watchers(dim, base) {
  try {
    return dim.getPlayers().filter((p) => Math.hypot(p.location.x - base.x, p.location.z - base.z) <= 96);
  } catch {
    return [];
  }
}
function bigTitle(dim, base, title, subtitle) {
  for (const p of watchers(dim, base)) {
    try {
      showRealTitle(p, title, subtitle);
    } catch {
      // 무시
    }
  }
}

/** 하는 중이면 알리고 true */
function busy(player) {
  if (job) {
    player.sendMessage("§c[세계수] 지금 세계수가 자라는(되돌리는) 중입니다 — 끝난 뒤에 다시 해 주세요");
    return true;
  }
  if (preparing) {
    player.sendMessage("§c[세계수] 세계수 자리를 살피는(계산하는) 중입니다 — 잠시 뒤에 다시 해 주세요");
    return true;
  }
  return false;
}

/** 단계마다 앞 단계들의 칸 수 (칸 번호 = starts[단계] + 단계 안 번호) */
function stageStarts(plan) {
  const out = [];
  let n = 0;
  for (const st of plan.stages) {
    out.push(n);
    n += st.ops.length;
  }
  return out;
}

/**
 * 자라기 시작 (단계마다 나눠서): 기록을 남기고 큰 글자·소리
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} base
 * @param {TreePlan} plan
 * @param {import("@minecraft/server").Player | undefined} player 알릴 사람
 * @param {string} [area] 붙잡아 둔 틱킹 영역 (다 자라면 치움)
 */
export function startTree(dim, base, plan, player, area) {
  if (job) return false;
  const id = `${Date.now().toString(36)}${(++idc).toString(36)}`;
  /** @type {TreeRecord} */
  const rec = { id, d: dim.id, x: base.x, y: base.y, z: base.z, h: plan.H, s: plan.seed, r: plan.reach, by: player?.name ?? "?", v: 2, fp: plan.fp, n: plan.total };
  addRecord(rec);
  const log = newLog(plan.total);
  saveLog(id, base, log, 0);
  job = {
    kind: "grow",
    id,
    rec,
    dim,
    base,
    plan,
    log,
    area,
    starts: stageStarts(plan),
    si: 0,
    oi: 0,
    pause: 0,
    player,
    placed: 0,
    skipped: 0,
    errors: 0,
    moved: 0,
    movedMobs: 0,
    budget: WORLD_TREE.perTick,
    lastT: 0,
    saveAt: system.currentTick + WORLD_TREE.saveEvery,
    t0: system.currentTick,
  };
  bigTitle(dim, base, "§a§l세계수", "§f뿌리부터 자라납니다");
  play(dim, "item.bone_meal.use", base, 0.8);
  play(dim, "block.creaking_heart.place", base, 0.7);
  return true;
}

/** 두 칸짜리 풀·꽃이면 윗 반쪽인지 (true/false), 아니면 undefined */
function halfOf(b) {
  try {
    const v = b.permutation?.getState("upper_block_bit");
    return typeof v === "boolean" ? v : undefined;
  } catch {
    return undefined;
  }
}

/** 한 칸 놓기 (놓는 법에 맞으면). gi = 칸 번호 */
function placeOp(j, gi, op) {
  const d = j.dim;
  const b = j.base;
  if (op.mode === "carpet" || op.mode === "bush") {
    const x = b.x + op.dx;
    const z = b.z + op.dz;
    for (let dy = op.dy + 2; dy >= op.dy - 4; dy--) {
      const y = b.y + dy;
      const here = blockAt(d, x, y, z);
      if (!here) {
        j.errors++;
        return;
      }
      if (!isSoft(here) || here.typeId === op.block) continue;
      const below = blockAt(d, x, y - 1, z);
      if (!below || !(op.mode === "bush" ? DIRT.has(below.typeId) : naturalGround(below))) continue;
      if (setAt(j, gi, { x, y, z }, here, op)) j.log.dys.set(gi, dy);
      return;
    }
    j.skipped++;
    return;
  }
  const loc = { x: b.x + op.dx, y: b.y + op.dy, z: b.z + op.dz };
  const here = blockAt(d, loc.x, loc.y, loc.z);
  if (!here) {
    j.errors++;
    return;
  }
  // 줄기·뿌리·가지: 자연 블록만 (그 밖의 블록은 그대로) · 잎·열매·덩굴: 빈 곳·풀·꽃·잎에만
  if (op.mode === "wood" ? !isNatural(here) : !isSoft(here)) {
    j.skipped++;
    return;
  }
  if (op.mode === "hang") {
    // 덩굴·포자 꽃: 빈 칸에, 위가 단단한 블록 (이끼·가지·덩굴 윗마디)일 때만
    const up = blockAt(d, loc.x, loc.y + 1, loc.z);
    if (!here.isAir || !up || up.isAir || up.isLiquid || isSoft(up)) {
      j.skipped++;
      return;
    }
  }
  setAt(j, gi, loc, here, op);
}

/**
 * 받침을 잃는 칸 (바꾸기 전에 읽어 둠): 두 칸짜리 풀·꽃의 아래 반쪽 (here 가 윗 반쪽이면), 바로 위에 선 풀·꽃·작물·줄기식물 (같은 종류로 이어진 기둥)
 * @returns {{x: number, y: number, z: number, code: number}[]} 아래에서 위 차례
 */
function dependents(j, loc, here) {
  const out = [];
  if (halfOf(here) === true) {
    const b = blockAt(j.dim, loc.x, loc.y - 1, loc.z);
    if (b && b.typeId === here.typeId) out.push({ x: loc.x, y: loc.y - 1, z: loc.z, code: codeOf(j.log, b) });
  }
  /** @type {string | undefined} */
  let first;
  for (let y = loc.y + 1; y <= loc.y + 24; y++) {
    const b = blockAt(j.dim, loc.x, y, loc.z);
    if (!b || b.isAir || !isRooted(b)) break;
    if (first === undefined) first = b.typeId;
    else if (b.typeId !== first && b.typeId !== "minecraft:cactus_flower") break;
    out.push({ x: loc.x, y, z: loc.z, code: codeOf(j.log, b) });
  }
  return out;
}

/** 놓고 원래 블록을 기록 (바꾸기 전에 읽음). 받침을 잃는 칸은 먼저 (위에서부터) 치움 (물 속 식물은 물로). 놓았으면 true */
function setAt(j, gi, loc, here, op) {
  const log = j.log;
  const prev = codeOf(log, here);
  const extras = here.isAir ? [] : dependents(j, loc, here);
  /** @type {Set<any>} */
  const cleared = new Set();
  /** 치웠던 칸을 되돌려 놓음 (놓지 못했을 때, 아래부터) */
  const putBackCleared = () => {
    for (const e of extras) {
      if (!cleared.has(e)) continue;
      try {
        restoreAt(j.dim, { x: e.x, y: e.y, z: e.z }, log, e.code);
      } catch {
        // 무시
      }
    }
  };
  for (let k = extras.length - 1; k >= 0; k--) {
    const e = extras[k];
    try {
      j.dim.setBlockPermutation({ x: e.x, y: e.y, z: e.z }, permOf(wetOfCode(log, e.code) ? WATER : AIR));
      cleared.add(e);
    } catch {
      j.errors++;
      putBackCleared();
      return false;
    }
  }
  try {
    j.dim.setBlockPermutation(loc, permOf(op.block, op.states));
  } catch (e) {
    j.errors++;
    if (j.errors === 1) console.warn(`[rpg] world tree: ${e}`);
    putBackCleared();
    return false;
  }
  log.codes[gi] = prev;
  if (extras.length) log.extras.set(gi, extras);
  j.placed++;
  return true;
}

/** 이번 틱에 놓을 줄기·잎 칸 (from ~ to) 에 몸이 걸친 사람·동물·몬스터는 옆으로 옮김 (나무에 끼지 않게) */
function clearEntities(j, st, from, to) {
  const { dim, base } = j;
  // 이번 틱에 놓을 칸들의 범위 (그 둘레만 찾음)
  let x0 = Infinity;
  let x1 = -Infinity;
  let y0 = Infinity;
  let y1 = -Infinity;
  let z0 = Infinity;
  let z1 = -Infinity;
  for (let i = from; i < to; i++) {
    const o = st.ops[i];
    if (o.mode !== "wood" && o.mode !== "leaf") continue;
    x0 = Math.min(x0, o.dx);
    x1 = Math.max(x1, o.dx);
    y0 = Math.min(y0, o.dy);
    y1 = Math.max(y1, o.dy);
    z0 = Math.min(z0, o.dz);
    z1 = Math.max(z1, o.dz);
  }
  if (x0 > x1) return;
  const c = { x: base.x + (x0 + x1 + 1) / 2, y: base.y + (y0 + y1 + 1) / 2, z: base.z + (z0 + z1 + 1) / 2 };
  const rad = Math.hypot(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1) / 2 + 4;
  /** @type {any[]} */
  let ents = [];
  try {
    ents = dim.getPlayers({ location: c, maxDistance: rad });
  } catch {
    ents = [];
  }
  try {
    ents = ents.concat(dim.getEntities({ location: c, maxDistance: rad, excludeTypes: ["minecraft:player", ...STAY] }));
  } catch {
    // 사람만
  }
  for (const e of ents) {
    const isPlayer = e.typeId === "minecraft:player";
    if (!isPlayer && !movable(e)) continue;
    const l = e.location;
    // 몸: 사람 0.6 × 1.8, 동물·몬스터는 넉넉히 (말·골렘·거미도) 2 × 3
    const w = isPlayer ? 0.3 : 1.0;
    const h = isPlayer ? 1.8 : 3.0;
    const ex0 = Math.floor(l.x - w) - base.x;
    const ex1 = Math.floor(l.x + w) - base.x;
    const ey0 = Math.floor(l.y) - base.y;
    const ey1 = Math.floor(l.y + h - 0.01) - base.y;
    const ez0 = Math.floor(l.z - w) - base.z;
    const ez1 = Math.floor(l.z + w) - base.z;
    if (ex1 < x0 || ex0 > x1 || ey1 < y0 || ey0 > y1 || ez1 < z0 || ez0 > z1) continue;
    for (let i = from; i < to; i++) {
      const o = st.ops[i];
      if ((o.mode === "wood" || o.mode === "leaf") && o.dx >= ex0 && o.dx <= ex1 && o.dy >= ey0 && o.dy <= ey1 && o.dz >= ez0 && o.dz <= ez1) {
        moveOut(j, e, isPlayer);
        break;
      }
    }
  }
}
/** 옮길 엔티티: 바닐라 생물 (체력이 있는 것, 아이템·투사체·NPC·갑옷 거치대 빼고) 과 이 애드온의 몬스터 (보스·소품·허수아비 과녁은 빼고) */
function movable(e) {
  if (STAY.has(e.typeId)) return false;
  try {
    if (e.typeId.startsWith("minecraft:")) return !!e.getComponent("minecraft:health");
    return !!e.getComponent("minecraft:type_family")?.hasTypeFamily("rpg_monster");
  } catch {
    return false;
  }
}

/** 나무 밖 (뿌리 끝 너머)의 땅으로 옮기고 나무를 바라보게 */
function moveOut(j, e, isPlayer) {
  const { dim, base, plan } = j;
  const l = e.location;
  let dx = l.x - (base.x + 0.5);
  let dz = l.z - (base.z + 0.5);
  const d = Math.hypot(dx, dz);
  if (d < 0.5) {
    dx = 0;
    dz = 1;
  } else {
    dx /= d;
    dz /= d;
  }
  const R = plan.rootReach + WORLD_TREE.clearance;
  const x = Math.floor(base.x + 0.5 + dx * R);
  const z = Math.floor(base.z + 0.5 + dz * R);
  const y = groundY(dim, x, z, base.y + 4) ?? base.y + 1;
  try {
    e.teleport({ x: x + 0.5, y, z: z + 0.5 }, { dimension: dim, facingLocation: { x: base.x + 0.5, y: base.y + plan.top * 0.4, z: base.z + 0.5 } });
  } catch {
    return;
  }
  if (!isPlayer) {
    j.movedMobs++;
    return;
  }
  j.moved++;
  try {
    e.sendMessage("§e[세계수] §f자라는 나무에 끼지 않게 옆으로 옮겼습니다");
  } catch {
    // 무시
  }
}

/** 단계 연출: 뿌리 = 흙먼지, 줄기 = 자라는 높이에 초록 반짝임 고리, 가지·잎 = 가지 끝 반짝임·꽃잎, 빛 = 빛 알갱이 */
function stageFx(j, st) {
  const { dim, base, plan } = j;
  const c = { x: base.x + 0.5, y: base.y + st.y, z: base.z + 0.5 };
  play(dim, st.sound, c, st.pitch);
  try {
    if (st.kind === "root") {
      for (let i = 0; i < 12; i++) {
        const a = (i / 12) * TAU;
        const r = plan.rootReach * 0.7;
        fx(dim, "rpg:dust", { x: c.x + Math.cos(a) * r, y: base.y + 0.3, z: c.z + Math.sin(a) * r }, { size: 2.2, life: 1.1, color: [0.45, 0.36, 0.28], alpha: 0.85 });
      }
      fx(dim, "rpg:shock_ring", { x: c.x, y: base.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: plan.rootReach * 0.55, life: 0.8, color: [0.55, 0.85, 0.35], alpha: 0.85 });
    } else if (st.kind === "trunk") {
      for (let i = 0; i < 10; i++) {
        const a = (i / 10) * TAU;
        const r = plan.trunkR * 0.9 + 1;
        particle(dim, "minecraft:crop_growth_emitter", { x: c.x + Math.cos(a) * r, y: c.y, z: c.z + Math.sin(a) * r });
      }
    } else if (st.kind === "branch" || st.kind === "leaf") {
      for (const m of plan.branches) particle(dim, "minecraft:crop_growth_emitter", { x: base.x + m.x + 0.5, y: base.y + m.y + 1, z: base.z + m.z + 0.5 });
      if (st.kind === "leaf") {
        play(dim, "item.bone_meal.use", c, 1.1);
        for (let i = 0; i < 8; i++) {
          const a = (i / 8) * TAU;
          particle(dim, "minecraft:cherry_leaves_particle", { x: c.x + Math.cos(a) * plan.canopy * 0.6, y: c.y, z: c.z + Math.sin(a) * plan.canopy * 0.6 });
        }
      }
    } else if (st.kind === "glow") {
      for (let i = 0; i < 16; i++) {
        const a = (i / 16) * TAU;
        particle(dim, "minecraft:totem_particle", { x: c.x + Math.cos(a) * plan.canopy * 0.7, y: c.y - 2 + (i % 4), z: c.z + Math.sin(a) * plan.canopy * 0.7 });
      }
    }
  } catch {
    // 표시만
  }
}

/** 다 자람: 기록 저장·틱킹 영역 치움, 큰 글자·소리·빛 고리, 심은 사람에게 되돌리는 법 */
function finishTree(j) {
  const { dim, base, plan, player } = j;
  saveLog(j.id, base, j.log, plan.total);
  releaseArea(dim, j.area);
  const c = { x: base.x + 0.5, y: base.y, z: base.z + 0.5 };
  play(dim, "random.levelup", c, 0.7);
  play(dim, "beacon.activate", c);
  fx(dim, "rpg:shock_ring", { x: c.x, y: base.y + 0.2, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: plan.canopy, life: 1.2, color: [0.6, 1.0, 0.45], alpha: 0.9 });
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * TAU;
    particle(dim, "minecraft:totem_particle", { x: c.x + Math.cos(a) * plan.trunkR * 1.4, y: base.y + 1 + (i % 6) * 2, z: c.z + Math.sin(a) * plan.trunkR * 1.4 });
  }
  bigTitle(dim, base, `§a§l${WORLD_TREE.name}`, "§f아홉 세계를 잇는 나무가 자라났습니다");
  if (player?.isValid) {
    const extra = [];
    if (j.skipped) extra.push(`지형·설치물에 막혀 건너뛴 곳 ${j.skipped}`);
    if (j.moved) extra.push(`옆으로 옮긴 사람 ${j.moved}`);
    if (j.movedMobs) extra.push(`옆으로 옮긴 동물 ${j.movedMobs}`);
    player.sendMessage(`§a[세계수] §f${WORLD_TREE.name}${josa(WORLD_TREE.name, "이", "가")} 자라났습니다 §7(높이 ${plan.H}칸, 블록 ${j.placed}개${extra.length ? `, ${extra.join(", ")}` : ""}) §f— 되돌리려면 ${undoHint()}`);
    if (j.errors) player.sendMessage(`§c[세계수] ${j.errors}곳은 놓지 못했습니다 (불러오지 않은 곳 등)`);
  }
}

/** 되돌리기 한 칸 (gi): 놓은 칸이고 지금도 우리가 놓은 블록일 때만 원래대로, 함께 치운 칸도 (불러오지 않은 곳은 기록에 남겨 다음에) */
function undoOp(j, gi) {
  const log = j.log;
  const code = log.codes[gi];
  if (!code) return;
  const op = j.ops[gi];
  const d = j.dim;
  const loc = { x: j.base.x + op.dx, y: j.base.y + (op.mode === "carpet" || op.mode === "bush" ? (log.dys.get(gi) ?? op.dy) : op.dy), z: j.base.z + op.dz };
  const b = blockAt(d, loc.x, loc.y, loc.z);
  if (!b) {
    j.failed++;
    return;
  }
  if (b.typeId !== op.block) {
    // 누가 캐거나 바꿈: 그대로 둠
    log.codes[gi] = 0;
    log.extras.delete(gi);
    j.changed++;
    return;
  }
  try {
    if (!restoreAt(d, loc, log, code)) {
      // 원래 블록을 알 수 없음 (다른 버전에서 이름이 바뀜 등): 구멍을 내지 않고 그대로 둠
      log.codes[gi] = 0;
      log.extras.delete(gi);
      j.unknown++;
      return;
    }
  } catch {
    j.failed++;
    return;
  }
  log.codes[gi] = 0;
  j.restored++;
  const ex = log.extras.get(gi);
  if (!ex) return;
  log.extras.delete(gi);
  for (const e of ex) {
    const t = blockAt(d, e.x, e.y, e.z);
    if (!t || !(t.isAir || /^minecraft:(flowing_)?water$/.test(t.typeId))) continue;
    try {
      restoreAt(d, { x: e.x, y: e.y, z: e.z }, log, e.code);
    } catch {
      // 그 칸은 그대로
    }
  }
}

/** 되돌리기 끝: 다 되돌렸으면 기록을 지움, 못 한 곳이 있으면 남은 기록을 저장 (다시 하면 이어서) */
function finishUndo(j) {
  job = undefined;
  releaseArea(j.dim, j.area);
  play(j.dim, "dig.wood", { x: j.base.x + 0.5, y: j.base.y + 1, z: j.base.z + 0.5 }, 0.6);
  if (j.failed) saveLog(j.rec.id, j.base, j.log);
  else dropRecord(j.rec.id);
  if (j.player?.isValid) {
    const extra = [];
    if (j.changed) extra.push(`그사이 바뀐 곳 ${j.changed}은 그대로 둠`);
    if (j.unknown) extra.push(`원래 블록을 알 수 없는 곳 ${j.unknown}은 그대로 둠`);
    j.player.sendMessage(`§a[세계수] §f세계수를 되돌렸습니다 §7(블록 ${j.restored}개${extra.length ? `, ${extra.join(", ")}` : ""})`);
    if (j.failed) j.player.sendMessage(`§e[세계수] §f${j.failed}곳은 되돌리지 못했습니다 (불러오지 못한 곳 등) — ${undoHint()}§f 를 한 번 더 해 주세요`);
  }
}

function treeTick() {
  if (!job) return;
  try {
    treeStep();
  } catch (e) {
    // 뜻밖의 오류: 일을 멈춤 (기록은 남아 있어 되돌릴 수 있음), 한 번만 알림
    const j = job;
    job = undefined;
    console.warn(`[rpg] world tree: ${e}`);
    try {
      if (j?.kind === "grow") saveLog(j.id, j.base, j.log);
      else if (j?.kind === "undo") saveLog(j.rec.id, j.base, j.log);
      releaseArea(j?.dim, j?.area);
    } catch {
      // 무시
    }
    if (j?.player?.isValid) j.player.sendMessage(`§c[세계수] 오류로 멈췄습니다 — ${undoHint()}§c 로 치울 수 있습니다`);
  }
}

function treeStep() {
  const j = job;
  // 서버가 느려지면 (틱 사이가 lagMs 넘게 벌어지면) 한 틱에 놓는 수를 줄이고, 괜찮으면 조금씩 늘림
  const now = Date.now();
  const gap = j.lastT ? now - j.lastT : 50;
  j.lastT = now;
  const cap = j.kind === "undo" ? WORLD_TREE.perTick * 2 : WORLD_TREE.perTick;
  j.budget = gap > WORLD_TREE.lagMs ? Math.max(WORLD_TREE.minPerTick, Math.floor(j.budget * 0.6)) : Math.min(cap, j.budget + 40);
  if (j.pause > 0) {
    j.pause--;
    return;
  }
  const ms = WORLD_TREE.msPerTick;
  if (j.kind === "undo") {
    let n = 0;
    let k = 0;
    while (n < j.budget && j.gi >= 0) {
      if (j.log.codes[j.gi]) n++;
      undoOp(j, j.gi--);
      if ((++k & 31) === 0 && Date.now() - now > ms) break;
    }
    if (j.gi < 0) finishUndo(j);
    return;
  }
  const st = j.plan.stages[j.si];
  if (!st) {
    job = undefined;
    finishTree(j);
    return;
  }
  const end = Math.min(st.ops.length, j.oi + j.budget);
  if (st.kind !== "ground") clearEntities(j, st, j.oi, end);
  const i0 = j.starts[j.si];
  let n = 0;
  while (j.oi < end) {
    placeOp(j, i0 + j.oi, st.ops[j.oi]);
    j.oi++;
    if ((++n & 15) === 0 && Date.now() - now > ms) break;
  }
  if (j.oi >= st.ops.length) {
    stageFx(j, st);
    j.si++;
    j.oi = 0;
    j.pause = WORLD_TREE.stagePause;
    saveLog(j.id, j.base, j.log, j.starts[j.si] ?? j.plan.total);
    j.saveAt = system.currentTick + WORLD_TREE.saveEvery;
    if (j.player?.isValid) {
      const done = j.starts[j.si] ?? j.plan.total;
      notify(j.player, `§a세계수가 자라는 중 §7— ${st.name} §f${Math.min(100, Math.round((done / Math.max(1, j.plan.total)) * 100))}%`, 30);
    }
  } else if (system.currentTick >= j.saveAt) {
    saveLog(j.id, j.base, j.log, i0);
    j.saveAt = system.currentTick + WORLD_TREE.saveEvery;
  }
}

const DIM_NAMES = { "minecraft:overworld": "오버월드", "minecraft:the_end": "엔드", "minecraft:nether": "네더" };

/**
 * 마지막으로 심은 세계수를 되돌림 (확인 창 → 나무 자리를 불러오고 설계를 다시 계산 → 늦게 놓은 것부터 원래 블록으로)
 * @param {import("@minecraft/server").Player} player
 */
export async function undoTree(player) {
  if (busy(player)) return false;
  const rec = lastRecord();
  if (!rec) {
    player.sendMessage(`§c[세계수] 되돌릴 세계수가 없습니다 §7(최근 심은 ${WORLD_TREE.keep}그루까지 기록)`);
    return false;
  }
  let dim;
  try {
    dim = world.getDimension(rec.d);
  } catch {
    dim = undefined;
  }
  if (!dim) {
    player.sendMessage(`§c[세계수] 세계수가 있는 곳(${rec.d})을 찾지 못했습니다`);
    return false;
  }
  const base = { x: rec.x, y: rec.y, z: rec.z };
  asking++;
  let sel;
  try {
    sel = await new MenuForm(
      "§l세계수 되돌리기",
      "§f마지막으로 심은 세계수를 없애고, 심을 때 적어 둔 원래 블록으로 되돌립니다.\n\n" +
        `§7- 자리: §e${rec.x} ${rec.y} ${rec.z}§7 (${DIM_NAMES[rec.d] ?? rec.d})\n` +
        `§7- 높이: §e${rec.h}칸§7, 심은 사람: §e${rec.by}\n` +
        "§7- 나무가 놓은 칸 중 지금도 나무 블록인 곳만 되돌립니다 (누가 캐거나 바꾼 곳은 그대로)\n" +
        "§7- 자라는 도중에 월드를 나갔다면 마지막 1초쯤 사이에 놓인 블록은 남을 수 있습니다",
    )
      .button("§c되돌리기")
      .button("그만두기")
      .show(player);
  } finally {
    asking--;
  }
  if (sel !== 0 || !player.isValid) return false;
  if (busy(player)) return false;
  /** @type {{name?: string, loaded: boolean} | undefined} */
  let area;
  let handed = false;
  const me = beginPrep();
  try {
    if (lastRecord()?.id !== rec.id) {
      player.sendMessage("§c[세계수] 그사이 세계수 기록이 바뀌었습니다 — 다시 해 주세요");
      return false;
    }
    notify(player, "§7세계수 자리를 불러오는 중...", 40);
    area = await holdArea(dim, base, rec.r);
    if (!area.loaded) {
      if (player.isValid) player.sendMessage(`§c[세계수] 세계수(${rec.x} ${rec.y} ${rec.z}) 자리를 되돌릴 수 없습니다: ${loadWhy(area)}`);
      return false;
    }
    const plan = await planAsync(rec.h, rec.s);
    if (plan.fp !== rec.fp || plan.total !== rec.n) {
      if (player.isValid) player.sendMessage("§c[세계수] 심은 뒤 애드온이나 세계수 설정이 바뀌어 설계가 달라졌습니다 — 되돌릴 수 없으니 직접 치워 주세요");
      return false;
    }
    const log = loadLog(rec);
    if (!log) {
      if (player.isValid) player.sendMessage("§c[세계수] 이 세계수의 블록 기록이 없거나 망가져 되돌릴 수 없습니다");
      return false;
    }
    if (job || lastRecord()?.id !== rec.id) {
      if (player.isValid) player.sendMessage("§c[세계수] 그사이 다른 세계수 일이 시작되었습니다 — 끝난 뒤에 다시 해 주세요");
      return false;
    }
    const ops = plan.stages.flatMap((s) => s.ops);
    job = { kind: "undo", id: rec.id, rec, dim, base, plan, ops, log, area: area.name, gi: ops.length - 1, player, restored: 0, changed: 0, unknown: 0, failed: 0, budget: WORLD_TREE.perTick * 2, lastT: 0, pause: 0 };
    handed = true;
  } finally {
    endPrep(me);
    if (!handed) releaseArea(dim, area?.name);
  }
  if (player.isValid) player.sendMessage("§6[세계수] §f세계수를 되돌리는 중...");
  return true;
}

/** 심을 자리 표시: 밑동 원판·잎 넓이 원 (확인 창을 여는 동안) */
function markSpot(dim, base, plan) {
  const c = { x: base.x + 0.5, y: base.y + 0.1, z: base.z + 0.5 };
  fx(dim, "rpg:tele_disc", c, { dir: { x: 0, y: 0, z: 0 }, size: plan.trunkR, life: 6, color: [0.55, 0.9, 0.35], alpha: 0.8, grow: 1 });
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * TAU;
    particle(dim, "minecraft:villager_happy", { x: c.x + Math.cos(a) * plan.canopy, y: base.y + 1, z: c.z + Math.sin(a) * plan.canopy });
  }
}

/**
 * /worldtree [plant|undo] [높이]: 바라보는 곳을 살피고 (틱킹 영역으로 자리를 불러와), 심을지 묻고 (그동안은 놓아줌), 다시 붙잡아 심음
 * @param {import("@minecraft/server").Player} player
 * @param {number} [height]
 */
export async function plantTree(player, height) {
  if (busy(player)) return false;
  const H = height === undefined ? WORLD_TREE.height : Math.round(height);
  if (!(H >= WORLD_TREE.minHeight && H <= WORLD_TREE.maxHeight)) {
    player.sendMessage(`§c[세계수] 높이는 ${WORLD_TREE.minHeight} ~ ${WORLD_TREE.maxHeight}칸입니다 §7(기본 ${WORLD_TREE.height}, 예: ${cmdName} plant 80)`);
    return false;
  }
  const dim = player.dimension;
  if (dim.id === "minecraft:nether") {
    player.sendMessage("§c[세계수] 심을 수 없습니다: 네더에서는 자라지 않습니다 (용암·천장)");
    return false;
  }
  const spot = treeSpot(player, rootReachOf(H) + WORLD_TREE.clearance);
  if (!spot.base) {
    player.sendMessage(`§c[세계수] ${spot.reason}`);
    return false;
  }
  const base = spot.base;
  /** @type {TreePlan | undefined} */
  let plan;
  /** @type {{name?: string, loaded: boolean} | undefined} */
  let area;
  let me = beginPrep();
  try {
    notify(player, "§7세계수 자리를 살피는 중...", 40);
    plan = await planAsync(H, seedOf(base));
    if (!player.isValid) return false;
    const why0 = treeProblem(dim, base, plan, false);
    if (why0) {
      player.sendMessage(`§c[세계수] 심을 수 없습니다: ${why0}`);
      return false;
    }
    area = await holdArea(dim, base, plan.reach);
    if (!player.isValid) return false;
    const why = area.loaded ? treeProblem(dim, base, plan) : loadWhy(area);
    if (why) {
      player.sendMessage(`§c[세계수] 심을 수 없습니다: ${why}`);
      return false;
    }
    const survey = await treeSurvey(dim, base, plan);
    if (!player.isValid) return false;
    if (survey.why) {
      player.sendMessage(`§c[세계수] 심을 수 없습니다: ${survey.why}`);
      return false;
    }
  } finally {
    endPrep(me);
    // 확인 창을 여는 동안에는 붙잡지 않음 (창을 오래 열어 두어도 폰이 청크를 계속 돌리지 않게)
    releaseArea(dim, area?.name);
  }
  markSpot(dim, base, plan);
  asking++;
  let sel;
  try {
    sel = await new MenuForm(
      `§l${WORLD_TREE.name}`,
      `§f바라보는 곳 §e${base.x} ${base.y} ${base.z}§f에 높이 §e${plan.H}칸§f, 잎 넓이 §e${plan.canopy * 2}칸§f쯤 되는 세계수를 심습니다 §7(블록 약 ${plan.total}개)§f.\n\n` +
        "§7- 뿌리부터 몇 초 동안 자라납니다 (자라는 자리에 선 사람·동물은 옆으로 옮김)\n" +
        "§7- 줄기·뿌리는 흙·돌·모래·눈·얼음·풀·꽃·물·들의 식물·통나무 자리를 바꾸고, 잎은 빈 곳·풀·꽃·다른 나무의 잎 자리에 자랍니다 (사람이 쌓은 흙·돌이라도 바뀜)\n" +
        "§7- 판자·유리·조약돌·벽돌·껍질 벗긴 통나무 같은 건축 블록, 광석, 상자, 밭, 길, 보스 제단은 건드리지 않습니다\n" +
        `§7- ${undoHint()}§7 로 되돌릴 수 있습니다 (최근 ${WORLD_TREE.keep}그루, 바뀐 블록을 적어 둠)`,
    )
      .button("§2심기")
      .button("그만두기")
      .show(player);
  } finally {
    asking--;
  }
  if (sel !== 0 || !player.isValid) return false;
  if (busy(player)) return false;
  // 다시 붙잡고 심기
  area = undefined;
  let handed = false;
  me = beginPrep();
  try {
    area = await holdArea(dim, base, plan.reach);
    if (!player.isValid) return false;
    const again = area.loaded ? treeProblem(dim, base, plan) : loadWhy(area);
    if (again) {
      player.sendMessage(`§c[세계수] 심을 수 없습니다: ${again}`);
      return false;
    }
    if (job) {
      busy(player);
      return false;
    }
    handed = startTree(dim, base, plan, player, area.name);
    return handed;
  } finally {
    endPrep(me);
    if (!handed) releaseArea(dim, area?.name);
  }
}

export function registerWorldTree() {
  system.runInterval(treeTick, 1);
  world.afterEvents.worldLoad.subscribe(cleanupAreas);
}

/**
 * 시험용: 스크립트의 기억을 지움 (월드를 다시 불러온 것처럼). all 이면 월드의 기록·틱킹 영역 목록도
 * @param {boolean} [all]
 */
export function forgetTree(all = true) {
  job = undefined;
  preparing = 0;
  asking = 0;
  if (!all) return;
  for (const t of treeRecords()) dropLog(t.id);
  saveRecords([]);
  try {
    world.setDynamicProperty(AREAS_KEY, undefined);
  } catch {
    // 무시
  }
}
