import { EntitySwingSource, MolangVariableMap, system, world } from "@minecraft/server";
import { SLASH } from "./config.js";
import { play } from "./fx.js";
import { parseWeapon } from "./weapons.js";
import { blockedByMount } from "./riding.js";

/**
 * 근접 무기 참격 연출
 * - 공격 동작(허공 포함)마다 눈앞에 참격이 나가며 사라진다. 모양·색·소리는 무기 종류별 (config.js 의 SLASH)
 *   칼: 깔끔한 초승달, 좌우 번갈아 / 단검: 가는 베기가 반대 방향으로 한 번 더 (X자) / 도끼: 두껍게 기울어진 내려찍기
 *   v2.16.0 마검 (티르빙): 장밋빛 두꺼운 베기 + 1틱 뒤 안쪽에 칼날 룬의 하늘빛 가는 베기 (검 + 마력)
 * - 맞혔을 때: 도끼는 발밑 충격파 + 화면 흔들림, 치명타는 칼 금빛 참격 · 단검 붉은 X 표시 · 마검 보랏빛 X + 장밋빛 초승달 (마검은 맞힐 때마다 하늘빛 룬 불꽃)
 * - 방패 (v1.1.7, 방패 치기): 휘두르면 눈앞에 푸른 강철빛 고리, 맞히면 대상 앞에 가시 달린 충격 폭발 + 둔탁한 소리 + 작은 화면 흔들림
 * - 영웅·초월 등급은 참격 안쪽에 금빛·붉은빛 기운이 한 겹 더 (aura)
 * - 요구 조건 (v2.6.2 레벨 · 요구 스탯) 이 모자라면 회색으로 작게 나간다 (피해 없음 표시)
 * - 참격은 연출만 담당한다. 피해 계산은 combat.js (평타 판정 그대로)
 * 파티클(리소스 팩 particles/rpg): rpg:slash_<arc|thin|heavy>_<r|l>_<glow|core|aura>, rpg:slash_x, rpg:shock_ring, rpg:bash_ring, rpg:bash_burst
 */

const MELEE = new Set(["sword", "dagger", "axe", "shield", "greatsword"]); // v2.16.0 마검 (티르빙)
const GREY = [0.55, 0.55, 0.55];
/** 번갈아 베는 방향 @type {Map<string, boolean>} */
const side = new Map();

/** 흰색 쪽으로 섞기 (심지 색) */
function whiten(c, t) {
  return c.map((v) => v + (1 - v) * t);
}

/**
 * @param {import("@minecraft/server").Dimension} dim
 * @param {string} id
 * @param {import("@minecraft/server").Vector3} at
 * @param {import("@minecraft/server").Vector3} dir
 * @param {{speed:number, spin:number, size:number, life:number, color:number[], alpha?:number}} o
 */
function emit(dim, id, at, dir, o) {
  const m = new MolangVariableMap();
  m.setVector3("variable.dir", dir);
  m.setFloat("variable.speed", o.speed);
  m.setFloat("variable.spin", o.spin);
  m.setFloat("variable.size", o.size);
  m.setFloat("variable.life", o.life);
  m.setColorRGBA("variable.color", { red: o.color[0], green: o.color[1], blue: o.color[2], alpha: o.alpha ?? 1 });
  try {
    dim.spawnParticle(id, at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 매번 조금씩 다른 각도 (±jitter 도) */
function jitter(style) {
  return (Math.random() * 2 - 1) * (style.jitter ?? 0);
}

/**
 * 겹치는 순서를 확실히 하려고 겹마다 보는 방향으로 조금씩 앞뒤로 둔다 (뒤: 기운 → 빛 → 앞: 심지).
 * 같은 자리에 두면 게임이 그리는 순서가 정해지지 않아 흰 심지가 색 빛에 덮일 수 있다.
 */
const LAYER_GAP = 0.03;

function along(p, dir, d) {
  return { x: p.x + dir.x * d, y: p.y + dir.y * d, z: p.z + dir.z * d };
}

/**
 * 참격 한 개: (등급 기운) + 무기 색 빛 + 흰 심지. 세 겹은 같은 자리·같은 크기로 겹친다.
 * flip=false 는 오른쪽 위 → 왼쪽 아래, true 는 반대 (방향·기울기는 텍스처에 들어 있음)
 * @param {import("@minecraft/server").Dimension} dim
 * @param {import("@minecraft/server").Vector3} at 참격 가운데
 * @param {import("@minecraft/server").Vector3} dir 보는 방향 (단위 벡터, 이 방향으로 나아간다)
 * @param {number} dist 보는 사람 눈에서 at 까지 거리
 */
function slashAt(dim, at, dir, dist, w, style, flip, ok, scale = 1, speed = style.speed) {
  const size = style.size * (ok ? (SLASH.gradeSize[w.gradeIndex] ?? 1) : 0.7) * scale;
  const id = `rpg:slash_${style.shape}_${flip ? "l" : "r"}`;
  const base = { speed, spin: jitter(style), size, life: style.life };
  const glow = ok ? style.glow : GREY;
  const aura = ok ? SLASH.gradeAura[w.gradeIndex] : null;
  // k 칸만큼 앞뒤로 옮긴 겹. 거리에 비례해 크기를 맞춰서 눈에는 세 겹이 딱 겹쳐 보인다
  const layer = (k) => ({ at: along(at, dir, k * LAYER_GAP), size: (size * (dist + k * LAYER_GAP)) / dist });
  if (aura) {
    const L = layer(1);
    emit(dim, `${id}_aura`, L.at, dir, { ...base, size: L.size, color: aura, alpha: 0.8 });
  }
  emit(dim, `${id}_glow`, at, dir, { ...base, color: glow, alpha: ok ? 0.95 : 0.5 });
  const C = layer(-1);
  emit(dim, `${id}_core`, C.at, dir, { ...base, size: C.size, color: ok ? whiten(glow, 0.8) : GREY, alpha: ok ? 1 : 0.5 });
}

/** 공격 동작의 참격: 눈앞 가운데 (눈높이보다 SLASH.drop 만큼 아래) */
function slashOnce(player, w, style, flip, ok, scale = 1) {
  const dir = player.getViewDirection();
  const eye = player.getHeadLocation();
  const at = along({ x: eye.x, y: eye.y - (SLASH.drop ?? 0), z: eye.z }, dir, style.dist);
  slashAt(player.dimension, at, dir, style.dist, w, style, flip, ok, scale);
}

/**
 * 보는 방향에서 대상이 얼마나 벗어났는지 (도). 대상의 발~머리 사이에서 가장 가까운 곳 기준, 몸 폭만큼 빼준다.
 * 모바일 기본 조작은 화면 가운데가 아니라 누른 곳을 때리므로, 가운데 참격과 맞은 자리가 다를 수 있다.
 */
function offAxisDegrees(eye, dir, victim) {
  const feet = victim.location;
  const head = victim.getHeadLocation();
  let best = -1;
  let dist = 1;
  for (let i = 0; i <= 4; i++) {
    const s = i / 4;
    const p = { x: feet.x + (head.x - feet.x) * s, y: feet.y + 0.1 + (head.y - feet.y - 0.1) * s, z: feet.z + (head.z - feet.z) * s };
    const v = { x: p.x - eye.x, y: p.y - eye.y, z: p.z - eye.z };
    const len = Math.hypot(v.x, v.y, v.z) || 1;
    const dot = (v.x * dir.x + v.y * dir.y + v.z * dir.z) / len;
    if (dot > best) {
      best = dot;
      dist = len;
    }
  }
  const angle = (Math.acos(Math.max(-1, Math.min(1, best))) * 180) / Math.PI;
  return angle - (Math.atan(0.35 / Math.max(0.5, dist)) * 180) / Math.PI;
}

/**
 * 방패 치기: 휘두를 때 눈앞(참격과 같은 높이)에서 퍼지며 사라지는 고리. 영웅·초월은 바깥에 등급 빛 고리가 한 겹 더
 * @param {import("@minecraft/server").Player} player
 */
function bashSwing(player, w, style, ok) {
  const dim = player.dimension;
  const dir = player.getViewDirection();
  const eye = player.getHeadLocation();
  const at = along({ x: eye.x, y: eye.y - (SLASH.drop ?? 0), z: eye.z }, dir, style.dist);
  const size = style.size * (ok ? (SLASH.gradeSize[w.gradeIndex] ?? 1) : 0.7);
  const aura = ok ? SLASH.gradeAura[w.gradeIndex] : null;
  if (aura) {
    const k = style.dist + LAYER_GAP;
    emit(dim, "rpg:bash_ring", along(at, dir, LAYER_GAP), dir, { speed: style.speed, spin: 0, size: (size * 1.18 * k) / style.dist, life: style.life * 1.1, color: aura, alpha: 0.75 });
  }
  emit(dim, "rpg:bash_ring", at, dir, { speed: style.speed, spin: 0, size, life: style.life, color: ok ? style.glow : GREY, alpha: ok ? 0.9 : 0.5 });
  play(dim, "item.spear.attack_miss", eye, 0.7, 0.6);
}

/**
 * 방패 치기 맞힘: 대상 몸 앞(공격자 쪽)에 가시 달린 충격 폭발 + 흰 고리 + 둔탁한 소리 + 작은 화면 흔들림.
 * 치명타는 금빛으로 더 크고 길게.
 * @param {import("@minecraft/server").Player} attacker
 * @param {import("@minecraft/server").Entity} victim
 */
function bashHit(attacker, victim, w, crit, style) {
  const dim = victim.dimension;
  const eye = attacker.getHeadLocation();
  const head = victim.getHeadLocation();
  const mid = { x: head.x, y: head.y - 0.45, z: head.z };
  const to = { x: mid.x - eye.x, y: mid.y - eye.y, z: mid.z - eye.z };
  const len = Math.hypot(to.x, to.y, to.z) || 1;
  const u = { x: to.x / len, y: to.y / len, z: to.z / len };
  const at = along(mid, u, -Math.min(0.4, len * 0.5)); // 대상 몸 앞
  const grade = SLASH.gradeSize[w.gradeIndex] ?? 1;
  const size = style.hitSize * grade * (crit ? 1.35 : 1);
  const life = style.hitLife * (crit ? 1.25 : 1);
  emit(dim, "rpg:bash_burst", at, u, { speed: 0, spin: jitter(style), size, life, color: crit ? style.critColor : whiten(style.glow, 0.35), alpha: 0.95 });
  emit(dim, "rpg:bash_ring", along(at, u, -LAYER_GAP), u, { speed: 0, spin: 0, size: size * 0.55, life: life * 0.8, color: [1, 1, 1], alpha: 0.9 });
  play(dim, "item.shield.block", at, crit ? 0.7 : 0.85, 0.9);
  if (SLASH.cameraShake && attacker.typeId === "minecraft:player") shake(attacker, crit ? 0.2 : 0.08, crit ? 0.15 : 0.08);
}

/**
 * 공격 동작마다 참격
 * @param {import("@minecraft/server").Player} player
 * @param {any} w 무기 정보
 * @param {boolean} ok 요구 조건 (레벨 · 요구 스탯) 을 채웠는지
 */
export function slashSwing(player, w, ok) {
  if (!SLASH.enabled) return;
  const style = SLASH[w.look ?? w.type];
  if (!style) return;
  if (style.bash) {
    bashSwing(player, w, style, ok);
    return;
  }
  if (style.thrust) {
    thrustSwing(player, w, style, ok);
    return;
  }
  const flip = !(side.get(player.id) ?? false);
  side.set(player.id, flip);
  slashOnce(player, w, style, flip, ok);
  const eye = player.getHeadLocation();
  if (w.type === "dagger") {
    play(player.dimension, "item.spear.attack_miss", eye, 1.7, 0.45);
    // 반대 방향으로 한 번 더 베어 X자를 만든다
    system.runTimeout(() => {
      if (!player.isValid) return;
      slashOnce(player, w, style, !flip, ok, 0.9);
      play(player.dimension, "item.spear.attack_miss", player.getHeadLocation(), 1.95, 0.35);
    }, style.second ?? 2);
  } else if (w.type === "axe") {
    play(player.dimension, "item.spear.attack_miss", eye, 0.6, 0.8);
  } else if (w.type === "greatsword") {
    play(player.dimension, "item.spear.attack_miss", eye, 0.85, 0.75);
    if (ok && style.rune) {
      system.runTimeout(() => {
        if (player.isValid) runeSlash(player, w, style, flip);
      }, 1);
    }
  } else {
    play(player.dimension, "item.spear.attack_miss", eye, 1.15, 0.6);
  }
}

/** v2.16.0 마검의 안쪽 룬 베기: 같은 방향의 가는 하늘빛 베기 (빛 + 흰 심지, 기운 없이), 무거운 베기보다 조금 작고 가깝게 */
function runeSlash(player, w, style, flip) {
  const dir = player.getViewDirection();
  const eye = player.getHeadLocation();
  const dist = style.dist - 0.15;
  const at = along({ x: eye.x, y: eye.y - (SLASH.drop ?? 0), z: eye.z }, dir, dist);
  const size = style.size * 0.78 * (SLASH.gradeSize[w.gradeIndex] ?? 1);
  const id = `rpg:slash_thin_${flip ? "l" : "r"}`;
  const base = { speed: style.speed, spin: jitter(style), size, life: style.life * 0.85 };
  emit(player.dimension, `${id}_glow`, at, dir, { ...base, color: style.rune, alpha: 0.9 });
  emit(player.dimension, `${id}_core`, along(at, dir, -LAYER_GAP), dir, { ...base, size: (size * (dist - LAYER_GAP)) / dist, color: whiten(style.rune, 0.75), alpha: 1 });
}

/** 창 찌르기 (궁니르): 눈앞으로 곧게 뻗는 빛줄기 두 겹 (색 빛 + 흰 심지), 초월 기운 한 겹 */
function thrustSwing(player, w, style, ok) {
  const dim = player.dimension;
  const dir = player.getViewDirection();
  const eye = player.getHeadLocation();
  const at = along({ x: eye.x, y: eye.y - (SLASH.drop ?? 0) - 0.1, z: eye.z }, dir, style.dist + style.length);
  const grade = ok ? (SLASH.gradeSize[w.gradeIndex] ?? 1) : 0.7;
  const glow = ok ? style.glow : GREY;
  const shot = (color, thick, alpha) => {
    const m = new MolangVariableMap();
    m.setVector3("variable.dir", dir);
    m.setFloat("variable.speed", style.speed);
    m.setFloat("variable.size", style.length * grade);
    m.setFloat("variable.thick", thick * grade);
    m.setFloat("variable.life", style.life);
    m.setColorRGBA("variable.color", { red: color[0], green: color[1], blue: color[2], alpha });
    try {
      dim.spawnParticle("rpg:arrow_shot", at, m);
    } catch {
      // 무시
    }
  };
  const aura = ok ? SLASH.gradeAura[w.gradeIndex] : null;
  if (aura) shot(aura, style.thick * 2.2, 0.7);
  shot(glow, style.thick * 1.4, 0.95);
  shot(ok ? style.core : GREY, style.thick * 0.5, 1);
  play(dim, "item.trident.throw", eye, 1.3, 0.45);
}

/** 창으로 맞혔을 때: 대상 앞에 얼음빛 섬광 + 흰 고리 (치명타는 금빛·크게) */
function thrustHit(attacker, victim, w, crit, style) {
  const dim = victim.dimension;
  const eye = attacker.getHeadLocation();
  const head = victim.getHeadLocation();
  const mid = { x: head.x, y: head.y - 0.45, z: head.z };
  const to = { x: mid.x - eye.x, y: mid.y - eye.y, z: mid.z - eye.z };
  const len = Math.hypot(to.x, to.y, to.z) || 1;
  const u = { x: to.x / len, y: to.y / len, z: to.z / len };
  const at = along(mid, u, -Math.min(0.35, len * 0.5));
  const size = style.hitSize * (SLASH.gradeSize[w.gradeIndex] ?? 1) * (crit ? 1.4 : 1);
  emit(dim, "rpg:bash_burst", at, u, { speed: 0, spin: Math.random() * 360, size, life: crit ? 0.3 : 0.22, color: crit ? [1.0, 0.85, 0.35] : style.glow, alpha: 0.95 });
  emit(dim, "rpg:bash_ring", along(at, u, -LAYER_GAP), u, { speed: 0, spin: 0, size: size * 0.6, life: 0.2, color: [1, 1, 1], alpha: 0.9 });
  play(dim, "item.trident.hit", at, crit ? 0.8 : 1.1, 0.8);
}

function shake(player, intensity, seconds) {
  try {
    player.runCommand(`camerashake add @s ${intensity} ${seconds} rotational`);
  } catch {
    // 무시
  }
}

/**
 * 맞혔을 때 연출. 피해가 실제로 들어간 뒤 combat.js 의 after 이벤트에서 부른다.
 * @param {import("@minecraft/server").Player} attacker
 * @param {import("@minecraft/server").Entity} victim
 * @param {any} w
 * @param {boolean} crit
 */
export function slashHit(attacker, victim, w, crit) {
  if (!SLASH.enabled || !victim.isValid || !attacker.isValid) return;
  const dim = victim.dimension;
  const head = victim.getHeadLocation();
  const mid = { x: head.x, y: head.y - 0.45, z: head.z };
  const dir = attacker.getViewDirection();
  const style = SLASH[w.look ?? w.type];
  if (style?.bash) {
    bashHit(attacker, victim, w, crit, style);
    if (crit) play(dim, "game.player.attack.critical", mid, 1.0, 0.7);
    return;
  }
  if (style?.thrust) {
    thrustHit(attacker, victim, w, crit, style);
    if (crit) play(dim, "game.player.attack.critical", mid, 1.0, 0.7);
    return;
  }
  // 화면 가운데에서 벗어난 대상을 눌러 맞혔으면 맞은 자리에도 참격 (가운데 참격과 같은 방향)
  const eye = attacker.getHeadLocation();
  if (style && offAxisDegrees(eye, dir, victim) > (SLASH.offAxis ?? 12)) {
    const to = { x: mid.x - eye.x, y: mid.y - eye.y, z: mid.z - eye.z };
    const len = Math.hypot(to.x, to.y, to.z);
    if (len > 0.6) {
      const u = { x: to.x / len, y: to.y / len, z: to.z / len };
      const d = Math.max(0.6, len - 0.4); // 대상 바로 앞
      slashAt(dim, along(eye, u, d), u, d, w, style, side.get(attacker.id) ?? false, true, 0.7, 0);
    }
  }
  if (w.type === "axe") {
    const feet = victim.location;
    emit(dim, "rpg:shock_ring", { x: feet.x, y: feet.y + 0.08, z: feet.z }, { x: 0, y: 0, z: 0 }, {
      speed: 0,
      spin: 0,
      size: crit ? 1.6 : 1.0,
      life: crit ? 0.5 : 0.35,
      color: SLASH.axe.glow,
      alpha: 0.9,
    });
    play(dim, "mace.smash_ground", feet, crit ? 0.9 : 1.2, crit ? 0.9 : 0.5);
    if (SLASH.cameraShake && attacker.typeId === "minecraft:player") shake(attacker, crit ? 0.35 : 0.12, crit ? 0.2 : 0.1);
  }
  if (w.type === "greatsword" && style?.rune) {
    // v2.16.0 마검: 맞힌 자리에 칼날 룬의 하늘빛 불꽃 (검 + 마력)
    emit(dim, "rpg:arrow_spark", along(mid, dir, -0.2), dir, { speed: 0, spin: 0, size: 0.32, life: 0.18, color: style.rune, alpha: 1 });
  }
  if (!crit) return;
  // 밝은 겹을 공격자 쪽으로 조금 당겨서 위에 그려지게 한다
  const front = along(mid, dir, -LAYER_GAP);
  if (w.type === "dagger") {
    emit(dim, "rpg:slash_x", mid, dir, { speed: 0, spin: 0, size: 0.8, life: 0.28, color: [1.0, 0.35, 0.3], alpha: 1 });
    emit(dim, "rpg:slash_x", front, dir, { speed: 0, spin: 0, size: 0.55, life: 0.22, color: [1.0, 0.95, 0.8], alpha: 1 });
  } else if (w.type === "sword") {
    // 대상 위에 금빛 초승달 (각도는 무작위)
    const spin = (Math.random() * 2 - 1) * 40;
    const id = Math.random() < 0.5 ? "rpg:slash_arc_r" : "rpg:slash_arc_l";
    emit(dim, `${id}_glow`, mid, dir, { speed: 0, spin, size: 1.1, life: 0.25, color: [1.0, 0.85, 0.35], alpha: 0.95 });
    emit(dim, `${id}_core`, front, dir, { speed: 0, spin, size: 1.1, life: 0.25, color: [1.0, 1.0, 0.9], alpha: 1 });
  } else if (w.type === "greatsword") {
    // v2.16.0 마검: 보랏빛 X + 장밋빛 초승달 (무거운 베기 모양)
    const spin = (Math.random() * 2 - 1) * 30;
    const id = Math.random() < 0.5 ? "rpg:slash_heavy_r" : "rpg:slash_heavy_l";
    emit(dim, "rpg:slash_x", mid, dir, { speed: 0, spin: 0, size: 0.95, life: 0.28, color: [0.62, 0.36, 0.9], alpha: 1 });
    emit(dim, `${id}_glow`, along(mid, dir, -LAYER_GAP), dir, { speed: 0, spin, size: 1.2, life: 0.26, color: style?.glow ?? [0.96, 0.52, 0.5], alpha: 0.95 });
    emit(dim, `${id}_core`, along(front, dir, -LAYER_GAP), dir, { speed: 0, spin, size: 1.19, life: 0.26, color: [1.0, 0.92, 0.88], alpha: 1 });
  }
  play(dim, "game.player.attack.critical", mid, 1.0, 0.7);
}

/**
 * @param {(player: import("@minecraft/server").Player, w: any) => boolean} canHit 요구 조건 (레벨 · 요구 스탯) 검사
 */
export function registerSlashes(canHit) {
  world.afterEvents.playerSwingStart.subscribe(
    (ev) => {
      // 필터 옵션과 같은 조건을 한 번 더 확인 (캐기·블록 놓기·아이템 사용 동작은 제외)
      if (ev.swingSource !== EntitySwingSource.Attack) return;
      const w = parseWeapon(ev.heldItemStack?.typeId);
      if (!w || !MELEE.has(w.type)) return;
      if (blockedByMount(ev.player, "attack")) return; // v2.8.0 탈것에 탄 동안 참격 없음 (피해도 막힘)
      try {
        slashSwing(ev.player, w, canHit(ev.player, w));
      } catch (e) {
        console.warn(`[rpg] slash: ${e}`);
      }
    },
    { swingSource: EntitySwingSource.Attack },
  );
}

export function forgetSlash(playerId) {
  side.delete(playerId);
}
