import { GameMode, system, world } from "@minecraft/server";
import { MOUNT } from "./config.js";
import { addHurtHooks } from "./combat.js";
import { particle, play, warn } from "./fx.js";
import { blockedByMount, forgetRiding, isMounted } from "./riding.js";
import { interruptRunes } from "./runes.js";
import { add, burst, fx, rune, shock, sparks, unit, up } from "./skills.js";

/**
 * 탈것 (v2.8.0): 스탯 창 오른쪽 아래 칸 (statui.js) → 다이어울프를 불러 바로 태움 → 내리면 (웅크리기 · 죽음 · 순간이동 · 나가기) 사라짐
 * - 소환: 발밑에 얼음빛 룬 · 퍼지는 고리 두 겹 · 빛기둥 · 터짐 · 반짝이 · 눈송이, 큰 늑대의 으르렁 + 바람 소리. 플레이어가 보던 방향으로
 * - 탄 동안: 달리면 발밑에 얼음 반짝이. 공격 · 스킬 · 룬은 막힘 (riding.js, 피해는 아래 피해 훅). 탈것은 다치지 않음
 * - 사라짐: 몸에서 얼음빛 터짐 · 고리 · 반짝이 · 눈송이, 낑낑 · 몸 터는 소리. 다시 부르기는 MOUNT.cooldownTicks 뒤
 * - 탄 사람이 없는 탈것 (다시 불러온 청크 · 오류로 남은 것) 은 불러올 때와 MOUNT.sweepEvery 틱마다 찾아 치움
 * - 발 위 칸부터 탄 사람의 머리 끝 (발 + 자리 높이 + 키) 까지 막혀 있으면 부르지 않음 (천장에 머리가 끼어 숨 막힘 피해를 받지 않게)
 */

const PLAYER = "minecraft:player";
/** 탈것에 적는 주인 (플레이어 ID) */
const OWNER = "rpg:mount_owner";
const WHITE = [1, 1, 1];
/** 탈것도 막지 않는 피해 (/kill · 세계 밖으로 떨어짐) */
const KILL_CAUSES = new Set(["override", "selfDestruct", "void"]);
/** 플레이어 ID → 부른 탈것 (seated = 한 번이라도 탄 것을 확인함) @type {Map<string, {e: any, p: any, born: number, seated: boolean, last: {x: number, y: number, z: number}}>} */
const mounts = new Map();
/** 플레이어 ID → 탈것이 사라진 틱 (다시 부르기 대기) @type {Map<string, number>} */
const goneAt = new Map();

/** 탄 사람의 키 (자리에서 머리 끝까지) */
const RIDER_H = 1.8;
/**
 * 머리를 넣어도 되는 블록 (공기 · 액체 말고): 풀 · 꽃 · 덩굴 · 횃불 · 표지판 · 작물 · 실 · 보이지 않는 빛 블록 등.
 * 지금 게임의 블록 이름만 하나씩 (뿌리 흙 · 맹그로브 뿌리 · 눈 한 겹 · 랜턴 · 불은 막힘 — 불은 머리가 탐)
 */
const SOFT =
  /^minecraft:(short_grass|tall_grass|fern|large_fern|dandelion|poppy|blue_orchid|allium|azure_bluet|red_tulip|orange_tulip|white_tulip|pink_tulip|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|torchflower|sunflower|lilac|rose_bush|peony|pitcher_plant|pink_petals|wildflowers|open_eyeblossom|closed_eyeblossom|[a-z_]+_sapling|sweet_berry_bush|bush|firefly_bush|short_dry_grass|tall_dry_grass|deadbush|vine|twisting_vines|weeping_vines|cave_vines|cave_vines_body_with_berries|cave_vines_head_with_berries|torch|soul_torch|redstone_torch|unlit_redstone_torch|underwater_torch|copper_torch|[a-z_]*sign|[a-z_]*banner|[a-z_]*carpet|[a-z_]*button|lever|[a-z_]*pressure_plate|[a-z_]*rail|trip_wire|tripwire_hook|redstone_wire|wheat|carrots|potatoes|beetroot|reeds|kelp|seagrass|sea_pickle|waterlily|brown_mushroom|red_mushroom|crimson_fungus|warped_fungus|web|nether_sprouts|crimson_roots|warped_roots|hanging_roots|pale_hanging_moss|mangrove_propagule|leaf_litter|glow_lichen|spore_blossom|small_dripleaf_block|big_dripleaf|ladder|scaffolding|light_block(_[0-9]+)?|structure_void)$/;

/** 이 플레이어가 지금 타고 있는 엔티티 (없으면 undefined) */
function ridingOn(player) {
  try {
    return player.getComponent("minecraft:riding")?.entityRidingOn;
  } catch {
    return undefined;
  }
}

function ridersOf(e) {
  try {
    return e.getComponent("minecraft:rideable")?.getRiders() ?? [];
  } catch {
    return [];
  }
}

/** 탈것에 태움. 태웠으면 true */
function ride(player, e) {
  try {
    return !!e.getComponent("minecraft:rideable")?.addRider(player);
  } catch {
    return false;
  }
}

/**
 * 발이 있는 칸 바로 위부터 탄 사람의 머리 끝 (발 + 자리 높이 MOUNT.seat[1] + 키) 이 있는 칸까지 비었는지. 불러오지 않은 곳은 막지 않음
 * 평평한 땅: 발 + 1 (기어가기 · 헤엄으로 낮은 틈) · + 2 · + 3 의 세 칸. 흙길 · 경작지 · 진흙 · 영혼 모래처럼 조금 낮은 블록 위: 네 칸
 */
function headroom(player) {
  const c = player.location;
  const base = Math.floor(c.y + 1e-3);
  const top = Math.floor(c.y + MOUNT.seat[1] + RIDER_H);
  for (let y = base + 1; y <= top; y++) {
    let b;
    try {
      b = player.dimension.getBlock({ x: Math.floor(c.x), y, z: Math.floor(c.z) });
    } catch {
      b = undefined;
    }
    if (!b || b.isAir || b.isLiquid || SOFT.test(b.typeId ?? "")) continue;
    return false;
  }
  return true;
}

/** 다시 부를 수 있을 때까지 남은 틱 (0 = 지금) */
export function mountCooldownLeft(player) {
  return Math.max(0, MOUNT.cooldownTicks - (system.currentTick - (goneAt.get(player.id) ?? -1e9)));
}

/** 이 플레이어가 부른 탈것 (없으면 undefined) */
export function mountOf(player) {
  return mounts.get(player.id)?.e;
}

// ------------------------------------------------------------ 연출
/** 소환: 발밑 룬 · 고리 두 겹 · 빛기둥 · 터짐 · 반짝이 · 눈송이 + 큰 늑대 으르렁 · 바람 */
function summonFx(dim, at) {
  const C = MOUNT.color;
  rune(dim, at, { size: 2.6, life: 0.9, color: C, alpha: 0.95, progress: 1, rate: 120, grow: 0.3 });
  shock(dim, at, C, 3.4, 0.5);
  shock(dim, at, WHITE, 2.0, 0.35);
  fx(dim, "rpg:arrow_shot", up(at, 2.4), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: 2.4, thick: 0.55, life: 0.55, color: C, alpha: 0.8 });
  fx(dim, "rpg:arrow_shot", up(at, 2.4), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: 2.3, thick: 0.18, life: 0.45, color: WHITE, alpha: 0.95 });
  burst(dim, up(at, 1.2), C, 1.8, 0.35);
  sparks(dim, up(at, 1.0), C, 14, 5);
  for (let i = 0; i < 6; i++) particle(dim, "minecraft:snowflake_particle", add(at, { x: (Math.random() - 0.5) * 2.4, y: 0.5 + Math.random() * 1.8, z: (Math.random() - 0.5) * 2.4 }));
  play(dim, "mob.wolf.big.growl", at, 0.85, 1.0);
  play(dim, "breeze_wind_charge.burst", at, 1.3, 0.6);
}

/** 사라짐: 몸에서 얼음빛 터짐 · 고리 · 반짝이 (흰 · 파랑) · 눈송이 + 낑낑 · 몸 터는 소리 */
function goneFx(dim, at) {
  const C = MOUNT.color;
  const mid = up(at, 1.1);
  burst(dim, mid, C, 2.0, 0.4);
  shock(dim, at, C, 2.8, 0.45);
  sparks(dim, mid, WHITE, 10, 4);
  sparks(dim, mid, C, 10, 3);
  for (let i = 0; i < 5; i++) particle(dim, "minecraft:snowflake_particle", add(at, { x: (Math.random() - 0.5) * 2, y: 0.4 + Math.random() * 1.6, z: (Math.random() - 0.5) * 2 }));
  particle(dim, "minecraft:white_smoke_particle", mid);
  play(dim, "mob.wolf.big.whine", at, 1.1, 0.8);
  play(dim, "mob.wolf.shake", at, 1.0, 0.7);
}

/** 달리는 동안 발밑에 얼음 반짝이 두 개 (6틱마다, 0.6칸 넘게 움직였을 때) */
function trailFx(m) {
  let loc;
  try {
    loc = { ...m.e.location };
  } catch {
    return;
  }
  const moved = Math.hypot(loc.x - m.last.x, loc.z - m.last.z);
  m.last = loc;
  if (moved < 0.6) return;
  const dim = m.e.dimension;
  for (let i = 0; i < 2; i++) {
    const s = add(loc, { x: (Math.random() - 0.5) * 1.2, y: 0.1, z: (Math.random() - 0.5) * 1.2 });
    fx(dim, "rpg:arrow_spark", s, { dir: unit({ x: (Math.random() - 0.5) * 0.4, y: 1, z: (Math.random() - 0.5) * 0.4 }), speed: 1.2, size: 0.08, grow: 0.3, life: 0.5, color: i ? WHITE : MOUNT.color, alpha: 0.85 });
  }
}

// ------------------------------------------------------------ 부르기 · 돌려보내기
/**
 * 탈것을 치움 (탄 사람은 내려 놓음). fxOn = 사라지는 연출
 * @param {string} pid 주인 ID (모르면 "")
 */
function despawn(pid, e, fxOn = true) {
  if (pid) {
    mounts.delete(pid);
    goneAt.set(pid, system.currentTick);
  }
  let at;
  let dim;
  try {
    at = { ...e.location };
    dim = e.dimension;
  } catch {
    at = undefined;
  }
  try {
    e.getComponent("minecraft:rideable")?.ejectRiders();
  } catch {
    // 무시
  }
  try {
    e.remove();
  } catch {
    // 이미 없음
  }
  if (fxOn && at && dim) goneFx(dim, at);
}

/**
 * 탈것을 불러 바로 태움. 불렀으면 true (못 부르면 까닭을 화면 하단에)
 * @param {any} player
 */
export function summonMount(player) {
  if (!player?.isValid) return false;
  try {
    if (player.getGameMode() === GameMode.Spectator) return false;
  } catch {
    return false;
  }
  if (isMounted(player)) return true;
  if (ridingOn(player)) {
    warn(player, "§c다른 것을 타고 있어 탈것을 부를 수 없습니다");
    return false;
  }
  const left = mountCooldownLeft(player);
  if (left > 0) {
    warn(player, `§7탈것은 §f${(left / 20).toFixed(1)}초§7 뒤에 다시 부를 수 있습니다`);
    return false;
  }
  if (!headroom(player)) {
    warn(player, "§c머리 위가 막혀 탈것을 부를 수 없습니다 §7(머리 위로 두 칸이 비어야 함)");
    return false;
  }
  const prev = mounts.get(player.id);
  if (prev) despawn(player.id, prev.e, false); // 안 탄 채 남은 것
  const dim = player.dimension;
  const at = { ...player.location };
  let e;
  try {
    e = dim.spawnEntity(MOUNT.id, at);
  } catch (err) {
    console.warn(`[rpg] mount spawn: ${err}`);
    warn(player, "§c탈것을 부를 수 없습니다 §7(행동 팩 적용 상태를 확인하세요)");
    return false;
  }
  try {
    e.setRotation({ x: 0, y: player.getRotation().y });
  } catch {
    // 무시
  }
  try {
    e.setDynamicProperty(OWNER, player.id);
  } catch {
    // 무시
  }
  const m = { e, p: player, born: system.currentTick, seated: false, last: at };
  mounts.set(player.id, m);
  interruptRunes(player.id); // 하던 룬 (대기 중인 이어 쓰기 · 남은 타격 · 순간이동) 은 멈춤 — 탄 채로 나가지 않게
  summonFx(dim, at);
  // 못 태우면 tick 이 graceTicks 동안 다시 해 보고, 그래도 안 되면 치움
  if (ride(player, e) && ridersOf(e).some((r) => r.id === player.id)) m.seated = true;
  return true;
}

/** 탄 탈것을 돌려보냄 (내려 놓고 사라짐). 돌려보냈으면 true */
export function dismissMount(player) {
  const m = mounts.get(player.id);
  const e = m?.e ?? (isMounted(player) ? ridingOn(player) : undefined);
  if (!e) return false;
  despawn(player.id, e, true);
  return true;
}

// ------------------------------------------------------------ 확인 (2틱마다 · 치우기)
function tick() {
  const now = system.currentTick;
  for (const [pid, m] of [...mounts]) {
    const e = m.e;
    if (!e?.isValid) {
      mounts.delete(pid); // 청크가 내려가 사라짐 등 — 다시 불러오면 치움 (stray)
      continue;
    }
    const ownerOn = ridersOf(e).some((r) => r.id === pid);
    if (ownerOn) {
      m.seated = true;
      if ((now >> 1) % 3 === 0) trailFx(m);
      continue;
    }
    // 부른 직후 아직 한 번도 태우지 못했으면 다시 (graceTicks 안). 한 번 탔다가 내렸으면 (곧바로 웅크려도) 바로 치움
    if (!m.seated && now - m.born < MOUNT.graceTicks && m.p?.isValid) {
      ride(m.p, e);
      continue;
    }
    despawn(pid, e, true);
  }
}

/** 부른 기록이 없는 탈것: 탄 사람이 있으면 기록 (세계를 다시 연 뒤 탄 채로 들어온 사람), 없으면 치움 */
function adopt(e) {
  if (!e?.isValid || e.typeId !== MOUNT.id) return;
  for (const m of mounts.values()) if (m.e?.id === e.id) return;
  const rider = ridersOf(e).find((r) => r.typeId === PLAYER && r.isValid);
  if (rider) {
    let at;
    try {
      at = { ...e.location };
    } catch {
      return;
    }
    const old = mounts.get(rider.id);
    if (old && old.e?.id !== e.id && old.e?.isValid) despawn("", old.e, false); // 남아 있던 다른 탈것
    mounts.set(rider.id, { e, p: rider, born: system.currentTick - MOUNT.graceTicks, seated: true, last: at });
    return;
  }
  let owner = "";
  try {
    owner = String(e.getDynamicProperty(OWNER) ?? "");
  } catch {
    owner = "";
  }
  despawn(owner && !mounts.has(owner) ? owner : "", e, true);
}

function sweep() {
  const seen = new Set();
  for (const id of ["overworld", "nether", "the_end"]) {
    let dim;
    try {
      dim = world.getDimension(id);
    } catch {
      continue;
    }
    if (!dim || seen.has(dim.id)) continue;
    seen.add(dim.id);
    let list = [];
    try {
      list = dim.getEntities({ type: MOUNT.id });
    } catch {
      list = [];
    }
    for (const e of list) adopt(e);
  }
}

/** 플레이어가 나감: 그 탈것을 치움 */
export function forgetMount(playerId) {
  const m = mounts.get(playerId);
  if (m) despawn(playerId, m.e, true);
  goneAt.delete(playerId);
  forgetRiding(playerId);
}

export function registerMount() {
  // 탄 사람의 피해는 모두 막음 (평타 · 활 · 완드 · 스킬 · 룬 · 남아 있던 장판 · 화살), 탈것은 다치지 않음. before 이벤트라 판정만, 안내는 system.run
  addHurtHooks({
    block(ev) {
      const v = ev.hurtEntity;
      if (v?.typeId === MOUNT.id) return !KILL_CAUSES.has(ev.damageSource?.cause); // /kill · 공허는 그대로 (운영자가 치울 수 있게)
      const a = ev.damageSource?.damagingEntity;
      if (a?.typeId !== PLAYER || a.id === v?.id || !isMounted(a)) return false;
      const cause = ev.damageSource?.cause;
      if (cause === "entityAttack" || cause === "projectile") {
        system.run(() => {
          if (a.isValid) blockedByMount(a, "attack");
        });
      }
      return true;
    },
  });
  system.runInterval(tick, 2);
  system.runInterval(sweep, MOUNT.sweepEvery);
  world.afterEvents.entityLoad?.subscribe((ev) => {
    if (ev.entity?.typeId !== MOUNT.id) return;
    const e = ev.entity;
    system.runTimeout(() => adopt(e), 5); // 탄 사람이 붙을 시간을 조금 줌
  });
}
