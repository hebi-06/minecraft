import { EntityDamageCause, MolangVariableMap, system, world } from "@minecraft/server";
import { BOW, CRIT_CAP, MOUNT, RUNE_GRAVITY, SKILLS, SLASH, WAND, tyrfingHpFactor } from "./config.js";
import {
  addHurtHooks,
  aimTarget,
  applySkillDamage,
  arrowPath,
  assistTarget,
  bodyAt,
  bodyInCone,
  bodyPad,
  bodyPoint,
  bodyToward,
  castRanged,
  hasNoHealth,
  heldEnhance,
  heldTypeId,
  lastHurtTick,
  lineOfSight,
  meetsRequirement,
  powerMultiplier,
  recentlyTalked,
  reqText,
  setGuard,
  standSpot,
  wandCircleAt,
} from "./combat.js";
import { actionbarFree, hudSuffix, notify, play, promptedRecently, showDamageText, showFaceText, showTextAt, warn } from "./fx.js";
import { healBy, hpState, isDying, isRpgMode, maxHpOf, payHp } from "./hp.js";
import { isDuelPartner } from "./duel.js";
import { parseWeapon } from "./weapons.js";
import { blockedBySilence, isRooted, isSilenced } from "./status.js";
import { blockedByMount } from "./riding.js";

/**
 * 초월 무기 스킬 (v1.2.0). 수치·이름은 config.js 의 SKILLS
 *
 * 조작
 *   근접 (그람·클라우 솔라스·궁니르·아이기스): 버튼(아이템 사용) = 스킬 1, 웅크리고 버튼 = 스킬 2
 *   활·완드 (아르테미스·간반테인): 버튼 짧게 = 평타, 웅크리고 버튼 = 스킬 1, 길게(SKILLS.holdTicks) = 스킬 2
 *     길게 누르기는 아이템의 use_modifiers(사용 시간)로 잰다: 떼면 itemReleaseUse, 다 누르면 itemCompleteUse
 * 피해 = 무기 공격력 × mult × 스탯 배율 (무기 피해와 같은 1 + 스탯 × 1포인트당 비율) × 치명타
 * 요구 조건은 무기와 같다 (v2.6.2: 최고 레벨 40 + 요구 스탯). 재사용 대기는 스킬마다 따로, 초월 무기를 들고 있으면 화면 하단에 표시 (쓸 수 없으면 까닭)
 * 피해 숫자는 하늘색(스킬), 분홍 !!(스킬 치명타). 대상이 필요한 스킬은 대상이 없으면 재사용 대기 없이 취소
 * v2.16.0 티르빙 (마검, 제작 전용): 검 (공격 피해, 하늘색) + 마력 (마법 피해, 파란 숫자 · 치명타 파란 !!), 스킬2 는 체력을 바침
 * (v2.16.0~2.22.0 저주 해방 → v2.23.0 망자의 각성)
 */

const PLAYER = "minecraft:player";
/** 스킬이 맞지 않는 것 (NPC·주민·골렘·아이템 등) */
const NO_HIT = new Set([
  "minecraft:item",
  "minecraft:xp_orb",
  "minecraft:arrow",
  "minecraft:armor_stand",
  "minecraft:npc",
  "minecraft:villager",
  "minecraft:villager_v2",
  "minecraft:wandering_trader",
  "minecraft:iron_golem",
  "minecraft:snow_golem",
  "minecraft:painting",
  "minecraft:leash_knot",
  "minecraft:fishing_hook",
  "minecraft:ender_crystal",
  "rpg:dmg_text",
  MOUNT.id, // v2.8.0 탈것 (다치지 않음 · 남의 탈것을 얼리거나 밀지 않게)
]);

/** "플레이어|스킬 id" → 다시 쓸 수 있는 틱 @type {Map<string, number>} */
const cooldowns = new Map();
/** 활·완드 누르기 (짧게/길게 구분). started = 엔진이 누르고 있기 시작을 알림 @type {Map<string, {w: any, tick: number, done: boolean, started: boolean}>} */
const presses = new Map();
/** "플레이어|스킬 id" → 준비 알림을 이미 했는지 @type {Set<string>} */
const readyAnnounced = new Set();

// ------------------------------------------------------------ 벡터
export const add = (a, b, k = 1) => ({ x: a.x + b.x * k, y: a.y + b.y * k, z: a.z + b.z * k });
export const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z });
export const len = (a) => Math.hypot(a.x, a.y, a.z);
export const unit = (a) => {
  const l = len(a) || 1;
  return { x: a.x / l, y: a.y / l, z: a.z / l };
};
export const flat = (a) => {
  const l = Math.hypot(a.x, a.z);
  return l < 1e-6 ? { x: 0, y: 0, z: 1 } : { x: a.x / l, y: 0, z: a.z / l };
};
export const up = (a, h) => ({ x: a.x, y: a.y + h, z: a.z });
export const rightOf = (f) => ({ x: -f.z, y: 0, z: f.x });

// ------------------------------------------------------------ 연출 도구
function vars(o) {
  const m = new MolangVariableMap();
  m.setVector3("variable.dir", o.dir ?? { x: 0, y: 1, z: 0 });
  for (const k of ["speed", "spin", "size", "life", "thick", "delay", "grow", "progress"]) m.setFloat(`variable.${k}`, o[k] ?? 0);
  const c = o.color ?? [1, 1, 1];
  m.setColorRGBA("variable.color", { red: c[0], green: c[1], blue: c[2], alpha: o.alpha ?? 1 });
  return m;
}

/** 파티클 변수 (fx 와 같은 이름) — 그 플레이어에게만 보이는 파티클 (player.spawnParticle, v2.6.1 길 안내) 용 */
export function particleVars(o = {}) {
  return vars(o);
}

/** 파티클 하나 (변수: dir, speed, spin, size, life, thick, delay, grow, progress, color, alpha) */
export function fx(dim, id, at, o = {}) {
  try {
    dim.spawnParticle(id, at, vars(o));
  } catch {
    // 로드되지 않은 곳 등
  }
}

function whiten(c, t) {
  return c.map((v) => v + (1 - v) * t);
}

/** a → b 빛줄기 (선 조각을 이어 붙임). delay 초 뒤에 켜지고 life 초 동안 가늘어지며 사라짐 */
export function beam(dim, a, b, color, o = {}) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return;
  const u = { x: d.x / L, y: d.y / L, z: d.z / L };
  const gap = o.gap ?? 0.5;
  for (let s = gap / 2; s <= L; s += gap) {
    const delay = (o.delay ?? 0) + (o.sweep ? (s / L) * o.sweep : 0);
    fx(dim, "rpg:arrow_trail", add(a, u, s), { dir: u, size: o.half ?? 0.32, thick: o.thick ?? 0.08, life: delay + (o.life ?? 0.4), delay, color, alpha: o.alpha ?? 0.9 });
  }
}

/** a → b 번개 (지그재그 선 조각) */
export function bolt(dim, a, b, color, o = {}) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.1) return;
  const u = unit(d);
  const side = unit(Math.abs(u.y) > 0.9 ? { x: 1, y: 0, z: 0 } : { x: -u.z, y: 0, z: u.x });
  const n = Math.max(3, Math.ceil(L / 1.1));
  const pts = [a];
  for (let i = 1; i < n; i++) {
    const j = (Math.random() * 2 - 1) * (o.jag ?? 0.45);
    const k = (Math.random() * 2 - 1) * (o.jag ?? 0.45) * 0.6;
    pts.push(add(add(add(a, u, (L * i) / n), side, j), { x: 0, y: 1, z: 0 }, k));
  }
  pts.push(b);
  for (let i = 0; i < pts.length - 1; i++) {
    const s = sub(pts[i + 1], pts[i]);
    const sl = len(s);
    const mid = add(pts[i], s, 0.5);
    const delay = o.delay ?? 0;
    fx(dim, "rpg:arrow_trail", mid, { dir: unit(s), size: sl / 2 + 0.08, thick: o.thick ?? 0.1, life: delay + (o.life ?? 0.3), delay, color, alpha: 1 });
  }
}

/** 초승달 참격 한 개 (빛 + 흰 심지 + 초월 기운). o.alpha: 전체 투명도 배수, o.core === false 면 흰 심지 없이 (잔상용) */
export function arc(dim, at, dir, o) {
  const id = `rpg:slash_${o.shape ?? "arc"}_${o.flip ? "l" : "r"}`;
  const base = { dir, speed: o.speed ?? 0, spin: o.spin ?? 0, size: o.size, life: o.life ?? 0.3 };
  const A = o.alpha ?? 1;
  if (o.aura) fx(dim, `${id}_aura`, add(at, dir, 0.04), { ...base, size: o.size * 1.03, color: o.aura, alpha: 0.8 * A });
  fx(dim, `${id}_glow`, at, { ...base, color: o.color, alpha: 0.95 * A });
  if (o.core !== false) fx(dim, `${id}_core`, add(at, dir, -0.04), { ...base, size: o.size * 0.99, color: whiten(o.color, 0.8), alpha: A });
}

/** 보는 방향 view 에 수직인 면(화면) 안에서 기울기 angle(도)의 방향 (0° = 화면 가로, +는 오른쪽 위로) */
export function screenDir(view, angle) {
  const side = unit(Math.abs(view.y) > 0.95 ? { x: 1, y: 0, z: 0 } : { x: -view.z, y: 0, z: view.x }); // 화면 오른쪽
  const upv = unit({ x: side.y * view.z - side.z * view.y, y: side.z * view.x - side.x * view.z, z: side.x * view.y - side.y * view.x }); // 화면 위
  const a = (angle * Math.PI) / 180;
  return unit(add({ x: side.x * Math.cos(a), y: side.y * Math.cos(a), z: side.z * Math.cos(a) }, upv, Math.sin(a)));
}

/**
 * 빛의 베인 자국 (rpg:light_cut): 몸에 그어진 뒤 hold 초 동안 그대로 떠 있다가 굵어지며 사라짐.
 * view: 보는 방향 (자국이 그 방향에서 잘 보이게 눕힘), angle: 화면 기준 기울기
 */
export function cutMark(dim, at, view, angle, hold, glow, core, scale = 1) {
  const dir = screenDir(view, angle);
  fx(dim, "rpg:light_cut", at, { dir, size: 0.95 * scale, thick: 0.36 * scale, life: hold + 0.16, delay: hold, color: glow, alpha: 0.85 });
  fx(dim, "rpg:light_cut", add(at, view, -0.05), { dir, size: 0.9 * scale, thick: 0.13 * scale, life: hold + 0.16, delay: hold, color: core, alpha: 1 });
}

/** 바닥 룬 (progress 0~1 = 그려진 단계) */
export function rune(dim, at, o) {
  fx(dim, "rpg:skill_rune", up(at, 0.06), { size: o.size, life: o.life, spin: o.spin ?? Math.random() * 360, speed: o.rate ?? 40, grow: o.grow ?? 0, progress: o.progress ?? 1, color: o.color, alpha: o.alpha ?? 0.9 });
}

export function burst(dim, at, color, size, life = 0.3) {
  fx(dim, "rpg:bash_burst", at, { dir: { x: 0, y: 0, z: 1 }, spin: Math.random() * 360, size, life, color, alpha: 0.95 });
}

export function ring(dim, at, dir, color, size, life = 0.35, speed = 0) {
  fx(dim, "rpg:bash_ring", at, { dir, speed, size, life, color, alpha: 0.9 });
}

export function shock(dim, feet, color, size, life = 0.45) {
  fx(dim, "rpg:shock_ring", up(feet, 0.08), { dir: { x: 0, y: 0, z: 0 }, size, life, color, alpha: 0.9 });
}

export function sparks(dim, at, color, n = 8, speed = 5) {
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const d = unit({ x: Math.cos(a), y: 0.35 + Math.random() * 0.5, z: Math.sin(a) });
    fx(dim, "rpg:arrow_spark", at, { dir: d, speed, size: 0.1, life: 0.3, color, alpha: 0.9 });
  }
}

export function shake(player, intensity, seconds) {
  try {
    player.runCommand(`camerashake add @s ${intensity} ${seconds} rotational`);
  } catch {
    // 무시
  }
}

/**
 * count 번을 gap 틱 간격으로 (0번째는 바로).
 * runTimeout(0) 은 다음 틱에 돌아서 0번째만 한 틱 늦어지면 1→2번째 간격이 gap - 1 이 되어 몹 무적 시간(10틱)에 막힐 수 있다
 */
export function every(count, gap, fn) {
  for (let i = 0; i < count; i++) {
    if (i === 0) fn(0);
    else system.runTimeout(() => fn(i), i * gap);
  }
}

export function colors(w) {
  return SKILLS.color[w.type] ?? [[1, 1, 1], [1, 1, 1]];
}

/** 초월 기운 색 (참격 안쪽) */
const AURA = () => SLASH.gradeAura[4] ?? [1.0, 0.3, 0.35];

// ------------------------------------------------------------ 대상
/** 스킬이 맞는 대상인지: 살아 있는 몹 (NPC·주민·골렘·길들인 동물·다른 플레이어 제외 — v2.26.0 결투 상대는 맞음) */
export function canHit(player, e) {
  if (!e?.isValid || e.id === player.id || NO_HIT.has(e.typeId)) return false;
  if (e.typeId === PLAYER && !SKILLS.hitPlayers && !isDuelPartner(player, e)) return false; // v2.26.0 결투 상대는 맞음
  try {
    if (e.hasTag("rpg_npc") || e.hasTag("rpg_gone")) return false; // v2.0.0 rpg_gone: 쓰러져 사라지는 중인 보스 소품 (크라켄 촉수)
    if (player.getComponent?.("minecraft:riding")?.entityRidingOn?.id === e.id) return false; // v2.17.0 타고 있는 것 (길들이지 않는 돼지 · 낙타 · 스트라이더 등) 은 범위 기술에 맞지 않음
    const hp = e.getComponent("minecraft:health");
    if (!hp || hp.currentValue <= 0) return false;
    if (e.getComponent("minecraft:is_tamed")) return false;
  } catch {
    return false;
  }
  return true;
}

/**
 * 가운데에서 radius 안 (몸 가운데 기준, 몸 폭만큼 여유), 위아래 vertical 칸 안.
 * v2.1.0: 키 큰 몸(보스 등)은 몸의 세로 선분(bodySpan)에서 가장 가까운 높이로, 넓은 몸은 반폭이 보통 몹보다 넓은 만큼 더 여유
 */
export function around(player, center, radius, vertical = 3) {
  let list = [];
  try {
    list = player.dimension.getEntities({ location: center, maxDistance: radius + vertical + 4 });
  } catch {
    list = [];
  }
  return list.filter((e) => {
    if (!canHit(player, e)) return false;
    const p = bodyAt(e, center.y);
    return Math.hypot(p.x - center.x, p.z - center.z) <= radius + 0.5 + bodyPad(e) && Math.abs(p.y - center.y) <= vertical;
  });
}

/** 조준한 대상, 없으면 시야 cone 도 안에서 가운데에 가장 가까운 대상 (벽 뒤는 제외) */
export function pickTarget(player, range, cone) {
  const r = aimTarget(player, range);
  if (r.target && canHit(player, r.target)) return r.target;
  return assistTarget(player, range, cone, canHit)?.e;
}

/** 조준한 곳 (대상 발밑(시선 가까이의 몹 포함) → 막힌 블록 → 사거리 끝) */
export function aimPoint(player, range) {
  const r = aimTarget(player, range, BOW.aimAssist ?? 0);
  if (r.target) return r.target.location;
  const head = player.getHeadLocation();
  return add(head, player.getViewDirection(), r.dist);
}

function healthOf(e) {
  try {
    return e.getComponent("minecraft:health")?.currentValue ?? 0;
  } catch {
    return 0;
  }
}

/**
 * 스킬 피해 한 번. 무기 공격력 × mult × 스탯 배율 × 치명타. 실제로 들어간 피해를 돌려준다 (0 = 안 들어감)
 * @param {{critBonus?: number, sure?: boolean, cause?: string, spell?: boolean}} [o] spell: v2.16.0 마검의 마력 (파란 숫자)
 */
export function skillHit(player, target, w, mult, o = {}) {
  if (!canHit(player, target)) return 0;
  const crit = o.sure === true || Math.random() < Math.min(CRIT_CAP, w.critChance + (o.critBonus ?? 0));
  const dmg = w.damage * mult * powerMultiplier(player, w) * (crit ? w.critMult : 1);
  // 완드는 마법 피해 (방어 무시), 나머지는 공격 피해 (방어구 적용. 활도 실제 화살이 없어서 공격 피해로)
  const cause = o.cause ?? (w.type === "wand" ? EntityDamageCause.magic : EntityDamageCause.entityAttack);
  const before = healthOf(target);
  if (!applySkillDamage(player, target, dmg, crit, cause, o.spell ? (crit ? "spellcrit" : "spell") : undefined)) return 0;
  const after = target.isValid ? healthOf(target) : 0;
  return before > after ? before - after : dmg;
}

/** 스킬 한 번의 예상 피해 (치명타 없이, 스탯 창·설명용) */
export function skillDamage(player, w, mult) {
  return w.damage * mult * powerMultiplier(player, w);
}

export function holding(player, w) {
  return player.isValid && heldTypeId(player) === w.id && (player.getComponent("minecraft:health")?.currentValue ?? 1) > 0;
}

// ------------------------------------------------------------ 그람 (칼) — v2.17.0 개편 (불꽃 · 용)
// 사용자 요청 "멋있고 쎄게, 쿨이 길어도 좋으니 — 그람은 불꽃 날개를 펼쳐 위로 잠깐 올라간 후 내려찍기" · 고른 세기 "영웅 룬~초월 룬 사이"
const G_FIRE = [1.0, 0.45, 0.08];
const G_EMBER = [1.0, 0.62, 0.2];
const G_DEEP = [0.9, 0.18, 0.04];
const G_ASH = [0.16, 0.08, 0.06];
const V_DOWN = { x: 0, y: -1, z: 0 };
const V_UP = { x: 0, y: 1, z: 0 };

/** 수평 방향 f 를 y축으로 deg 도 돌림 */
function turnY(f, deg) {
  const a = (deg * Math.PI) / 180;
  return { x: f.x * Math.cos(a) - f.z * Math.sin(a), y: 0, z: f.x * Math.sin(a) + f.z * Math.cos(a) };
}

/** 불꽃 한 장 (rpg:fire_wall — 밑동이 at, 서서 일렁임. 색은 0.6 + 0.4 × color) */
function flame(dim, at, size, life = 0.7, color = G_FIRE, alpha = 0.95) {
  fx(dim, "rpg:fire_wall", at, { size, life, color, alpha, spin: Math.random() * 360 });
}

/** 불티 (rpg:ember — 넓이 size 안에서 life 초 동안 위로 날림) */
function embers(dim, at, size, life = 0.8, speed = 2) {
  fx(dim, "rpg:ember", up(at, 0.3), { size, life, speed, color: G_EMBER, alpha: 0.9 });
}

/** 땅이 갈라져 그을림 (rpg:ground_crack) */
function scorch(dim, at, size, life = 1.4) {
  fx(dim, "rpg:ground_crack", up(at, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size, life, color: G_ASH, alpha: 0.9 });
}

/** a → b 곧은 빛 한 줄 (rpg:arrow_trail 하나) */
function streakLine(dim, a, b, color, thick, life, alpha = 1, delay = 0) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return;
  fx(dim, "rpg:arrow_trail", add(a, d, 0.5), { dir: unit(d), size: L / 2, thick, life: life + delay, delay, color, alpha });
}

/**
 * 발 s 에서 수평 f 로 max 칸까지 막힘 없이 갈 수 있는 거리 (벽 앞 0.6칸). 1.1 · 1.8 높이 광선 — 한 칸 턱은 넘고, 두 칸 높이 굴에서도 됨
 * (예전 2.2칸 광선은 두 칸 높이 굴의 천장 블록 안에서 시작해 돌진하지 못했음)
 */
function reachFlat(dim, s, f, max) {
  let dist = max;
  for (const h of [1.1, 1.8]) {
    try {
      const hit = dim.getBlockFromRay(up(s, h), f, { maxDistance: max + 0.8, includePassableBlocks: false, includeLiquidBlocks: false });
      if (hit) {
        const b = hit.block.location;
        const fl = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
        dist = Math.min(dist, Math.hypot(b.x + fl.x - s.x, b.z + fl.z - s.z) - 0.6);
      }
    } catch {
      // 무시
    }
  }
  return Math.max(0, dist);
}

/**
 * 돌진 길의 설 자리 (0.5칸마다와 끝, 가까운 순): standSpot (단단한 땅 · 머리 위 1.8칸 빔 · 물 · 용암 · 불 · 마그마 · 선인장 아님) 이
 * 한 칸 안의 오르내림으로 이어지는 데까지 — 낭떠러지 (1.1칸 넘게 낮음) · 두 칸 턱 · 물 · 용암 · 낮은 천장 앞에서 끊김
 * @returns {{s: number, p: {x: number, y: number, z: number}}[]}
 */
function dashSpots(dim, from, f, max) {
  const marks = [];
  for (let s = 0.5; s < max - 0.05; s += 0.5) marks.push(s);
  if (max > 0.2) marks.push(max);
  const out = [];
  let y = from.y;
  for (const s of marks) {
    const q = add(from, f, s);
    const p = standSpot(dim, { x: q.x, y: y + 1.1, z: q.z }, 2.2);
    if (!p || p.y > y + 1.05 || p.y < y - 1.1) break;
    y = p.y;
    out.push({ s, p });
  }
  return out;
}

/** 옮기기 (v2.17.0): 그 자리에서 몸이 블록과 겹치면 옮기지 않고 false (tryTeleport — 예외로 판단하지 않음) */
function moveTo(e, loc) {
  try {
    if (typeof e.tryTeleport === "function") return e.tryTeleport(loc, { keepVelocity: false, checkForBlocks: true }) !== false;
    e.teleport(loc, { keepVelocity: false, checkForBlocks: true });
    return true;
  } catch {
    return false;
  }
}

/** 무엇이든 타고 있는지 (말 · 배 · 우리 탈것) */
function ridingAny(player) {
  try {
    return !!player.getComponent("minecraft:riding")?.entityRidingOn;
  } catch {
    return false;
  }
}

/** from 아래로 maxDown 칸 안의 첫 블록이 단단하면 그 윗면 높이 (물 · 용암 · 땅 없음 = undefined) */
function solidBelow(dim, from, maxDown) {
  try {
    const hit = dim.getBlockFromRay(from, V_DOWN, { maxDistance: maxDown, includePassableBlocks: false, includeLiquidBlocks: true });
    if (!hit) return undefined;
    const b = hit.block;
    if (b.isLiquid || /water|lava/.test(b.typeId ?? "")) return undefined;
    return b.location.y + 1;
  } catch {
    return undefined;
  }
}

/** a → b 사이에 막는 블록이 없는지 (끝 0.3칸 여유) */
function clearPath(dim, a, b) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.3) return true;
  try {
    const hit = dim.getBlockFromRay(a, unit(d), { maxDistance: L, includePassableBlocks: false, includeLiquidBlocks: false });
    if (!hit) return true;
    const p = hit.block.location;
    const fl = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
    return len(sub({ x: p.x + fl.x, y: p.y + fl.y, z: p.z + fl.z }, a)) >= L - 0.3;
  } catch {
    return true;
  }
}

/** 눈 (eye) 에서 보이는 적만 */
function visibleFrom(player, eye, list) {
  return list.filter((e) => lineOfSight(player, eye, bodyAt(e, eye.y)));
}

/** 적 목록 합치기 (같은 적은 한 번) */
function unionById(...lists) {
  const seen = new Map();
  for (const l of lists) for (const e of l) if (!seen.has(e.id)) seen.set(e.id, e);
  return [...seen.values()];
}

/**
 * 화룡 돌진 (v2.17.0): 보는 쪽으로 dash 칸 돌진 — 땅을 따라 (한 칸 오르내림), 벽 · 낭떠러지 · 물 · 용암 · 불 앞에서 멈춤.
 * 돌진 길 (내 몸 폭) 에 몸이 걸치는 첫 적은 그 몸 앞 stop 칸에서 멈춤 (부딪힘, 큰 보스는 몸 폭만큼 앞). 옆에 선 적은 지나치며 벰.
 * 지나온 길에 불꽃이 남고 → 멈춘 자리 앞 cone 도 · range 칸 (눈에서 보이는 적) 과 지나온 길 (폭 width) 의 적을 벰 (mult, 밀쳐냄) →
 * delay 틱 뒤 지나온 길이 앞쪽으로 차례로 불기둥이 되어 터지고, 멈춘 자리 앞 burst 칸이 폭발 (afterMult, 적마다 한 번 — 벤 적은 밀려났어도).
 * 몹 무적 시간에 막히지 않게 적마다 틱을 잡아 넣음 (reserveHit · hitAt). 쓰러지거나 차원을 옮기면 폭발은 없음. 속박 중이거나 무엇이든 탄 동안은 제자리에서 벰
 */
function gramCharge(player, w, k) {
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const start = { ...player.location };
  const f = facingOf(player);
  const [glow, core] = colors(w);
  // 길은 땅 높이에서: 뛰는 중에 써도 발밑 땅 (3칸 안) 기준 — 벽 광선이 담 위로 지나가지 않게
  const g = solidBelow(dim, up(start, 0.3), 3.3);
  const from = { x: start.x, y: g === undefined ? start.y : Math.min(start.y, g), z: start.z };
  const free = reachFlat(dim, from, f, k.dash); // 벽 앞 0.6칸까지
  let dist = isRooted(player) || ridingAny(player) ? 0 : free; // 속박 (보스 기믹) · 무엇이든 탄 동안은 제자리에서 벰
  for (const { e } of dist > 0.2 ? lineHits(player, up(from, 1.0), f, dist, k.width) : []) {
    // 몸이 돌진 길 (내 몸 반폭 0.3 + 몸 반폭 0.4 + pad) 에 걸치는 앞쪽 적: 그 몸 가장자리 앞에서 멈춤 (큰 보스 몸 안으로 들어가지 않게).
    // 옆에 선 적 (길 밖) 은 막지 않음 — 지나치며 벰 (폭 width)
    const pad = bodyPad(e);
    const dx = e.location.x - from.x;
    const dz = e.location.z - from.z;
    const along = dx * f.x + dz * f.z;
    const side = Math.abs(dx * f.z - dz * f.x);
    if (along > 0.2 && side <= 0.7 + pad) dist = Math.min(dist, Math.max(0, along - k.stop - pad));
  }
  const spots = dist > 0.2 ? dashSpots(dim, from, f, dist) : [];
  let at = start;
  let L = 0;
  for (let i = spots.length - 1; i >= 0; i--) {
    // 가장 먼 설 자리부터 (몸이 블록과 겹쳐 못 옮기면 그 앞 자리)
    if (moveTo(player, spots[i].p)) {
      at = spots[i].p;
      L = spots[i].s;
      break;
    }
  }
  const yAt = (s) => {
    let y = from.y;
    for (const x of spots) {
      if (x.s > s + 0.25) break;
      y = x.p.y;
    }
    return y;
  };
  const onPath = (s) => {
    const q = add(from, f, s);
    return { x: q.x, y: yAt(s), z: q.z };
  };
  // 출발: 발밑 불꽃 + 손의 불티
  burst(dim, up(from, 0.9), G_FIRE, 1.3, 0.3);
  shock(dim, from, G_FIRE, 1.3, 0.4);
  embers(dim, from, 1.6, 0.5, 2.5);
  // 지나온 길: 불빛 줄기 (빛 + 흰 심지, 0.8칸 조각 — 한 틱의 파티클 수를 줄임) + 남는 불꽃 + 잔상 참격
  if (L > 0.3) {
    beam(dim, up(from, 1.0), up(at, 1.0), glow, { half: 0.55, thick: 0.26, life: 0.45, sweep: 0.08, gap: 0.8 });
    beam(dim, up(from, 1.0), up(at, 1.0), core, { half: 0.5, thick: 0.09, life: 0.35, sweep: 0.08, gap: 0.8 });
    for (let s = 0.5; s <= L; s += 0.9) flame(dim, onPath(s), 1.1, 0.8 + (s / L) * 0.3, G_FIRE, 0.9);
    for (let s = 1.2; s < L - 0.6; s += 2.2) arc(dim, up(onPath(s), 1.05), f, { shape: "heavy", flip: Math.round(s / 2.2) % 2 === 1, size: 1.5, life: 0.22, color: glow, aura: AURA(), spin: -25, alpha: 0.75 });
  }
  // 부딪힘: 앞에 거대한 불꽃 초승달 둘 (X) — 1인칭 화면을 다 덮지 않게 2.2칸 앞
  const front = up(add(at, f, 2.2), 1.1);
  arc(dim, front, f, { shape: "heavy", flip: false, size: 2.4, life: 0.28, speed: 1.5, color: glow, aura: AURA(), spin: -18 });
  system.runTimeout(() => {
    if (ok()) arc(dim, up(add(player.location, f, 2.3), 1.1), f, { shape: "heavy", flip: true, size: 2.2, life: 0.26, speed: 1.5, color: G_EMBER, aura: AURA(), spin: 22 });
  }, 2);
  play(dim, "item.trident.riptide_3", start, 1.1, 0.9);
  play(dim, "mob.blaze.shoot", at, 0.9, 0.8);
  play(dim, "fire.ignite", at, 1.0, 0.9);
  shake(player, 0.18, 0.15);
  // 벨 적: 앞쪽 부채꼴 (눈에서 보이는) + 지나온 길
  const eye = up(at, 1.62);
  const half = ((k.cone / 2) * Math.PI) / 180;
  const cone = visibleFrom(player, eye, around(player, at, k.range).filter((e) => bodyInCone(e, at, f, half)));
  const path = L > 0.3 ? lineHits(player, up(from, 1.0), f, L, k.width).map((h) => h.e) : [];
  const now = system.currentTick;
  const hitList = unionById(cone, path);
  /** 벤 적 (밀려나 폭발 범위 밖으로 나가도 폭발이 따라감) @type {Map<string, any>} */
  const struck = new Map();
  for (const e of hitList) {
    hitAt(player, e, reserveHit(player, e, now), ok, () => {
      if (skillHit(player, e, w, k.mult) <= 0) return;
      struck.set(e.id, e);
      const c = bodyAt0(e, eye);
      const near = Math.min(1, Math.max(0.5, len(sub(c, eye)) / 3)); // 눈 가까이의 적은 작게 (1인칭)
      fx(dim, "rpg:slash_x", c, { dir: f, size: 1.2 * near, life: 0.3, color: glow, alpha: 1 });
      fx(dim, "rpg:slash_x", add(c, f, -0.04), { dir: f, size: 0.85 * near, life: 0.25, color: core, alpha: 1 });
      burst(dim, c, G_FIRE, 0.8, 0.25);
      try {
        e.applyKnockback({ x: f.x * k.knock, z: f.z * k.knock }, 0.25);
      } catch {
        // 밀리지 않는 몹
      }
    });
  }
  if (hitList.length) play(dim, "game.player.attack.strong", at, 0.9, 0.9);
  // delay 틱 뒤: 지나온 길이 앞쪽으로 차례로 터지고 (불기둥) → 앞쪽 폭발 (afterMult). 폭발 자리는 벽을 넘지 않게 (벽 앞 0.3칸까지)
  const center = add(L > 0 ? at : from, f, Math.max(0, Math.min(1.6, free + 0.3 - L)));
  const n = Math.max(0, Math.floor(L / 1.1));
  for (let i = 0; i < n; i++) {
    const p = onPath(0.6 + i * 1.1);
    const t = Math.max(1, k.delay - 4 + Math.round((i / Math.max(1, n)) * 4));
    system.runTimeout(() => {
      if (!ok()) return;
      flame(dim, p, 2.4, 0.55, G_DEEP);
      burst(dim, up(p, 0.6), G_FIRE, 0.7, 0.22);
    }, t);
  }
  system.runTimeout(() => {
    if (!ok()) return;
    burst(dim, up(center, 0.9), G_FIRE, 2.4, 0.4);
    zonePulseG(dim, center, G_FIRE, k.burst, 0.5);
    for (let j = 0; j < 7; j++) flame(dim, add(center, turnY(f, j * (360 / 7) + 10), 1.9), 2.3, 0.6, j % 2 ? G_DEEP : G_FIRE);
    flame(dim, center, 3.0, 0.55, G_FIRE);
    scorch(dim, center, k.burst * 1.4, 1.2);
    embers(dim, center, 3.5, 0.8, 3);
    sparks(dim, up(center, 0.6), core, 10, 5);
    play(dim, "random.explode", center, 0.8, 1.0);
    play(dim, "mob.blaze.shoot", center, 1.0, 0.6);
    shake(player, 0.22, 0.2);
    const t0 = system.currentTick;
    const pathNow = L > 0.3 ? lineHits(player, up(from, 1.0), f, L, k.width).map((h) => h.e) : [];
    const blast = visibleFrom(player, up(center, 1.2), around(player, center, k.burst));
    // 벤 적은 밀려났어도 (폭발 범위 + 2.5칸 안이면) 폭발이 따라감
    const pushed = [...struck.values()].filter((e) => e.isValid && Math.hypot(e.location.x - center.x, e.location.z - center.z) <= k.burst + 2.5 + bodyPad(e) && Math.abs(e.location.y - center.y) <= 4);
    for (const e of unionById(blast, pathNow, pushed)) {
      hitAt(player, e, reserveHit(player, e, t0), ok, () => {
        if (skillHit(player, e, w, k.afterMult) > 0) flame(dim, e.isValid ? e.location : center, 1.6, 0.45, G_FIRE);
      });
    }
  }, k.delay);
  return true;
}

/** 범위 고리: 안쪽에서 퍼져 반지름 R 에서 사라짐 (shock_ring 은 처음 크기의 약 2.1배까지 퍼짐) */
function zonePulseG(dim, feet, color, R, life = 0.45) {
  shock(dim, feet, color, R / 2.1, life);
}

/** 쓰는 사람 → 낙하 피해를 막는 마지막 틱 (용익 강하: 떠올랐다 내려찍는 동안 · 뒤) @type {Map<string, number>} */
const noFallUntil = new Map();

/**
 * 불꽃 날개 (rpg:fire_wing — 등 뒤, 보는 방향에 수직인 면 한 장, 너비 2 × size 칸): frame 0~3 펼침 · 4~7 날갯짓 (위 · 가운데 · 아래 · 가운데).
 * tips: 날개 끝 · 팔에 작은 불꽃 (옆에서 보면 그림이 얇아서)
 */
function fireWings(player, frame, life, tips = false, size = 3.6) {
  const dim = player.dimension;
  const f = facingOf(player);
  const at = up(add(player.location, f, -0.42), 1.3);
  fx(dim, "rpg:fire_wing", at, { dir: f, size, life, progress: (frame + 0.5) / 8, color: [1, 1, 1], alpha: 0.97 });
  if (!tips) return;
  const side = rightOf(f);
  const lift = frame === 4 ? 0.9 : frame === 6 ? -0.2 : 0.45;
  for (const s of [-1, 1]) {
    flame(dim, up(add(at, side, s * size * 0.82), lift - 0.35), 0.9, life + 0.08, G_FIRE, 0.95);
    flame(dim, up(add(at, side, s * size * 0.4), lift * 0.6), 0.75, life + 0.08, G_EMBER, 0.9);
  }
}

/**
 * q 의 수직선에서 top 아래 · lowest 위의 첫 단단한 땅에 설 자리. strict 면 standSpot 조건 (물 · 용암 · 불 · 마그마 · 선인장 · 머리 위 막힘이면 안 됨).
 * top - 0.7 보다 높은 땅은 안 됨 (지금보다 높은 곳으로 "내려찍지" 않게 — 광선이 기둥 안에서 시작해도)
 */
function landOn(dim, q, top, lowest, strict = true) {
  const gy = solidBelow(dim, { x: q.x, y: top, z: q.z }, top - lowest);
  if (gy === undefined || gy > top - 0.7) return undefined;
  return strict ? standSpot(dim, { x: q.x, y: gy, z: q.z }, 0.6) : { x: q.x, y: gy, z: q.z };
}

/**
 * 내려찍을 곳 (지금 높이에서 잼): 조준한 몹의 바로 앞 → 시선이 닿은 곳에서 반 칸 앞 (가로 reach 칸까지) 의 설 자리.
 * 그곳이 설 수 없거나 (물 · 용암 · 불 · 허공 · 지금보다 높음) 가는 길이 막혔으면 바로 아래, 그다음 쓴 자리 (base — 2칸 안 · 막힘 없을 때만),
 * 마지막으로 바로 아래의 단단한 땅 (불 · 마그마여도 — 어차피 떨어질 곳). 없으면 undefined (내려찍지 않음)
 */
function descentSpot(player, k, base) {
  const dim = player.dimension;
  const p = player.location;
  const top = p.y + 1.0;
  const lowest = Math.min(base.y, p.y) - 6;
  const clear = (q) => clearPath(dim, up(p, 0.4), up(q, 0.4)) && clearPath(dim, up(p, 1.5), up(q, 1.5));
  let q;
  try {
    const r = aimTarget(player, k.reach + 8, BOW.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) {
      // 조준한 몹: 그 바로 앞 (몸과 겹치지 않게 — 몸 반폭 + 0.9칸 앞)
      const t = r.target.location;
      const hd = Math.hypot(t.x - p.x, t.z - p.z);
      const back = Math.min(hd, bodyPad(r.target) + 0.9);
      q = hd > 1e-6 ? { x: t.x - ((t.x - p.x) / hd) * back, y: t.y, z: t.z - ((t.z - p.z) / hd) * back } : { ...t };
    } else {
      // 시선이 닿은 곳에서 가로로 반 칸 앞 (벽 · 블록 가장자리에 몸이 겹치지 않게). 바로 아래를 보면 그대로
      const dir = r.dir ?? player.getViewDirection();
      const hit = add(player.getHeadLocation(), dir, r.dist ?? k.reach);
      q = Math.hypot(dir.x, dir.z) > 0.2 ? add(hit, flat(dir), -0.45) : hit;
    }
  } catch {
    q = { ...p };
  }
  const dx = q.x - p.x;
  const dz = q.z - p.z;
  const h = Math.hypot(dx, dz);
  if (h > k.reach) q = { x: p.x + (dx / h) * k.reach, y: q.y, z: p.z + (dz / h) * k.reach };
  const aim = landOn(dim, q, top, lowest);
  if (aim && clear(aim)) return aim;
  const below = landOn(dim, p, top, lowest);
  if (below) return below;
  if (Math.hypot(base.x - p.x, base.z - p.z) <= 2) {
    const b = landOn(dim, base, top, lowest);
    if (b && clear(b)) return b;
  }
  return landOn(dim, p, top, lowest, false);
}

/**
 * 용익 강하 (v2.17.0, 사용자 예시 "불꽃 날개를 펼쳐 위로 잠깐 올라간 후 내려찍기"): 발밑에 단단한 땅이 있어야 씀 (물 · 용암 · 허공 · 속박 · 탄 동안이면 재사용 대기 없이 거절).
 * 불꽃 날개를 펴며 떠오름 (세로 넉백 rise, 약 4칸) → 꼭대기 (apex 틱) 부터 hover 틱까지 머묾 (느린 낙하 — 오르는 동안 걸면 더 솟을 수 있어 꼭대기에서) —
 * 내려찍을 곳을 불꽃 룬으로 보임 (조준을 옮기면 따라감) → dive 틱에 걸쳐 그곳 (가로 reach 칸 안) 으로 내려찍음 → 반지름 radius 칸 폭발 (mult, 띄움) →
 * delay 틱 뒤 불꽃 고리 (afterMult, 적마다 한 번 — 띄운 적은 고리 밖으로 밀려났어도).
 * 떠오른 동안 · 내려찍은 뒤 3초는 낙하 피해 없음. 쓰러지거나 차원을 옮기면 남은 것은 멈춤 (무기를 바꿔도 이어짐 — 쓸 때의 무기 · 강화로).
 * 끊김 (보스 기믹 등): 떠 있는 동안 침묵 · 속박 · 무엇이든 탐 · 한순간에 2.5칸 넘게 옮겨짐 (끌려감 · 삼켜짐 · 순간이동) 이면 내려찍지 않음 (그냥 떨어짐)
 */
function gramDescent(player, w, k) {
  const dim = player.dimension;
  const c0 = { ...player.location };
  if (isRooted(player)) {
    warn(player, `§c${k.name}: §7속박된 동안에는 떠오를 수 없습니다`);
    return false;
  }
  if (ridingAny(player)) {
    warn(player, `§c${k.name}: §7탄 것에서 내려야 떠오를 수 있습니다`);
    return false;
  }
  const g0 = solidBelow(dim, up(c0, 0.5), 4.5);
  if (g0 === undefined) {
    warn(player, `§c${k.name}: §7발밑에 단단한 땅이 있어야 합니다`);
    return false;
  }
  const base = { x: c0.x, y: g0, z: c0.z };
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow, core] = colors(w);
  const diveAt = k.hover;
  const landAt = diveAt + k.dive - 1;
  const apex = Math.min(k.apex ?? 9, diveAt - 1);
  noFallUntil.set(player.id, Math.max(noFallUntil.get(player.id) ?? 0, system.currentTick + landAt + 60));
  try {
    player.applyKnockback({ x: 0, z: 0 }, k.rise);
  } catch {
    // 못 띄우면 제자리에서 펼치고 내려찍음
  }
  /** last: 마지막으로 본 자리 (한순간에 옮겨졌는지), slow: 이 스킬이 건 느린 낙하 (내려찍을 때 이것만 지움 — 원래 걸린 물약은 그대로) */
  const st = { last: { ...c0 }, broken: false, slow: false };
  const dropSlow = () => {
    if (!st.slow) return;
    st.slow = false;
    try {
      player.removeEffect("slow_falling");
    } catch {
      // 무시
    }
  };
  const fizzle = (why) => {
    st.broken = true;
    dropSlow();
    const l = player.location;
    burst(dim, up(l, 0.9), G_ASH, 1.0, 0.3);
    embers(dim, l, 1.2, 0.5, 1);
    play(dim, "random.fizz", l, 0.9, 0.8);
    warn(player, `§c${k.name} §7— ${why}`);
  };
  const held = () => isRooted(player) || isSilenced(player) || ridingAny(player);
  /** 떠 있는 동안 매 틱: 끊겼으면 (침묵 · 속박 · 탐 · 2.5칸 넘게 옮겨짐) true */
  const interrupted = () => {
    if (st.broken) return true;
    const l = player.location;
    const jumped = Math.hypot(l.x - st.last.x, l.z - st.last.z) > 2.5 || Math.abs(l.y - st.last.y) > 3;
    st.last = { ...l };
    if (!jumped && !held()) return false;
    fizzle("끊김");
    return true;
  };
  // 펼침: 발밑 불꽃 폭발 · 고리 · 불티
  burst(dim, up(c0, 0.3), G_FIRE, 1.4, 0.3);
  zonePulseG(dim, base, G_FIRE, 3.2, 0.45);
  for (let j = 0; j < 6; j++) flame(dim, add(base, turnY({ x: 1, y: 0, z: 0 }, j * 60), 1.6), 1.5, 0.5, j % 2 ? G_DEEP : G_FIRE);
  embers(dim, base, 3, 0.9, 3.5);
  play(dim, "mob.enderdragon.flap", c0, 1.4, 0.8);
  play(dim, "mob.blaze.shoot", c0, 1.0, 0.7);
  play(dim, "fire.ignite", c0, 1.2, 0.8);
  // 날개: 지금 자리에 (따라 움직임) — 떠오르는 0~9틱은 매 틱, 그 뒤 2틱마다. 0~6틱 펼침 (그림 0 → 3), 머무는 동안 날갯짓 (4 → 7 되풀이). 내려찍을 때는 단계마다 접힘
  // 1인칭 (날개는 등 뒤라 화면 밖): 펼칠 때 화면 양 끝에 불꽃 초승달이 뒤에서 앞으로 · 머무는 동안 눈앞으로 떨어지는 불꽃 깃털
  for (let t = 0; t < diveAt; t++) {
    const tick = () => {
      if (!ok() || interrupted()) return;
      if (t === apex) {
        // 꼭대기: 느린 낙하 (이미 더 오래 걸린 느린 낙하가 있으면 그대로 두고 지우지도 않음)
        try {
          const cur = player.getEffect("slow_falling");
          if (!cur || cur.duration < diveAt - apex + 2) {
            player.addEffect("slow_falling", diveAt - apex + 2, { amplifier: 0, showParticles: false });
            st.slow = true;
          }
        } catch {
          // 무시
        }
      }
      if (t >= 10 && t % 2 === 1) return;
      const frame = t < 8 ? Math.min(3, Math.floor(t / 2)) : 4 + (Math.floor((t - 8) / 2) % 4);
      fireWings(player, frame, t < 10 ? 0.08 : 0.13, t >= 6 && t % 4 === 2);
      const c = player.location;
      if (t % 2 === 0) streakLine(dim, up(c, 0.2), up(c, -1.3), G_FIRE, 0.22, 0.13, 0.9); // 몸 아래로 흐르는 불꽃
      if (t % 4 === 0) embers(dim, up(c, 0.6), 1.2, 0.5, 1.5);
      if (t === 8 || t === 14) play(dim, "mob.enderdragon.flap", c, 1.1, 0.9);
      const fv = facingOf(player);
      if (t === 2 || t === 4) {
        const eye = player.getHeadLocation();
        const sd = rightOf(fv);
        for (const s of [-1, 1]) arc(dim, up(add(add(eye, fv, 1.3), sd, s * (t === 2 ? 1.25 : 1.5)), -0.15), fv, { shape: "heavy", flip: s < 0, size: 1.3, life: 0.2, speed: 1, color: G_FIRE, aura: AURA(), spin: s * 70, alpha: 0.85 });
      }
      if (t >= 6 && t % 4 === 2) {
        for (let j = 0; j < 2; j++) {
          const p = add(add(c, turnY(fv, (j ? 1 : -1) * (18 + Math.random() * 22)), 1.8 + Math.random() * 1.0), V_UP, 2.6 + Math.random() * 0.6);
          fx(dim, "rpg:arrow_spark", p, { dir: V_DOWN, speed: 1.2, size: 0.14, grow: 0.8, life: 0.9, color: j ? core : G_FIRE, alpha: 1 });
        }
      }
    };
    if (t === 0) tick();
    else system.runTimeout(tick, t);
  }
  // 내려찍을 곳 보이기 (조준을 따라감)
  for (let t = 6; t < diveAt; t += 3) {
    system.runTimeout(() => {
      if (!ok() || st.broken) return;
      const spot = descentSpot(player, k, base);
      if (!spot) return;
      rune(dim, spot, { size: k.radius * 0.95, life: 0.2, color: G_FIRE, alpha: 0.85, rate: 90 });
      shock(dim, spot, G_EMBER, 0.9, 0.2);
    }, t);
  }
  // 내려찍기
  system.runTimeout(() => {
    if (!ok() || interrupted()) return;
    dropSlow();
    const from = { ...player.location };
    const land = descentSpot(player, k, base);
    if (!land) {
      fizzle("내려찍을 곳이 없습니다");
      return;
    }
    const fd = sub(land, from);
    // 떨어지는 길: 굵은 불빛 줄기 + 흰 심지. 내려치는 초승달은 내려앉을 곳 앞 (1인칭 카메라가 지나가는 길 밖)
    streakLine(dim, up(from, 1.0), up(land, 1.0), G_FIRE, 0.7, 0.3, 0.9);
    streakLine(dim, up(from, 1.0), up(land, 1.0), core, 0.22, 0.25, 1);
    const fv = facingOf(player);
    arc(dim, up(add(land, fv, 1.8), 1.2), unit(add(fv, V_DOWN, 0.9)), { shape: "heavy", flip: false, size: 2.4, life: 0.22, speed: 2, color: glow, aura: AURA(), spin: 90 });
    play(dim, "item.trident.riptide_3", from, 1.2, 0.7);
    for (let i = 1; i <= k.dive; i++) {
      const step = () => {
        if (!ok() || st.broken) return;
        if (i > 1 && held()) {
          fizzle("끊김");
          return;
        }
        const last = i === k.dive;
        const moved = moveTo(player, last ? land : add(from, fd, i / k.dive)); // 가운데 자리가 막혔으면 건너뜀
        if (!last) {
          fireWings(player, i === 1 ? 2 : 1, 0.08);
          return;
        }
        if (!moved) {
          // 내려앉을 곳이 막혔으면 (몸이 블록과 겹침) 지금 자리 바로 아래
          const pl = player.location;
          const alt = landOn(dim, pl, pl.y + 1.0, Math.min(base.y, pl.y) - 6) ?? landOn(dim, pl, pl.y + 1.0, Math.min(base.y, pl.y) - 6, false);
          if (!alt || !moveTo(player, alt)) {
            fizzle("내려찍을 곳이 막혔습니다");
            return;
          }
        }
        descentImpact(player, w, k, ok);
      };
      if (i === 1) step();
      else system.runTimeout(step, i - 1);
    }
  }, diveAt);
  return true;
}

/** 내려찍은 순간: 반지름 radius 칸 폭발 (mult, 띄움) → delay 틱 뒤 불꽃 고리 (afterMult — 띄운 적은 반지름 + 2.5칸 안이면 밀려났어도) */
function descentImpact(player, w, k, ok) {
  const dim = player.dimension;
  const pl = player.location;
  const c = { x: pl.x, y: Math.min(pl.y, solidBelow(dim, up(pl, 0.6), 3) ?? pl.y), z: pl.z }; // 반 블록 위에 내려앉아도 그 높이
  const core = colors(w)[1];
  const f = facingOf(player);
  noFallUntil.set(player.id, Math.max(noFallUntil.get(player.id) ?? 0, system.currentTick + 60));
  // 폭발: 앞쪽 번쩍임 (1인칭 화면을 다 덮지 않게) · 불꽃 고리 둘 · 땅의 불 룬과 금 · 내려친 날개 · 둘레 불기둥 두 겹 · 불티
  burst(dim, up(add(c, f, 2.2), 0.6), G_FIRE, 2.6, 0.4);
  burst(dim, up(c, 0.25), core, 1.6, 0.3);
  zonePulseG(dim, c, G_FIRE, k.radius, 0.55);
  zonePulseG(dim, c, core, k.radius * 0.5, 0.4);
  rune(dim, c, { size: k.radius * 0.95, life: 1.0, color: G_FIRE, alpha: 0.9, grow: 0.15, rate: -60 });
  scorch(dim, c, k.radius * 1.6, 1.6);
  fireWings(player, 6, 0.3, false, 4.2); // 내려친 날개 (3인칭)
  // 둘레 불기둥 두 겹 (안쪽 0.6 · 바깥 0.95 × 반지름, 바깥은 2틱 늦게 — 퍼져 나가는 느낌)
  for (let j = 0; j < 8; j++) {
    const d = turnY(f, j * 45 + 22.5);
    flame(dim, add(c, d, k.radius * 0.6), 1.8, 0.7, j % 2 ? G_DEEP : G_FIRE);
  }
  for (let j = 0; j < 8; j++) {
    const d = turnY(f, j * 45);
    system.runTimeout(() => flame(dim, add(c, d, k.radius * 0.95), 1.4, 0.6, G_FIRE), 2);
  }
  embers(dim, c, k.radius * 1.2, 1.0, 3.5);
  sparks(dim, up(c, 0.5), core, 12, 6);
  play(dim, "mace.heavy_smash_ground", c, 1.0, 0.8);
  play(dim, "random.explode", c, 1.0, 0.7);
  play(dim, "mob.enderdragon.growl", c, 0.6, 1.3);
  shakeNear(dim, c, 12, 0.35, 0.35);
  // 피해 (띄움)
  const now = system.currentTick;
  const eye = up(c, 1.4);
  /** 띄운 적 (밀려나 고리 밖으로 나가도 고리가 따라감) @type {Map<string, any>} */
  const struck = new Map();
  for (const e of visibleFrom(player, eye, around(player, c, k.radius))) {
    hitAt(player, e, reserveHit(player, e, now), ok, () => {
      if (skillHit(player, e, w, k.mult) <= 0) return;
      struck.set(e.id, e);
      burst(dim, bodyAt0(e, eye), G_FIRE, 0.9, 0.25);
      const d = flat(sub(e.location, c));
      try {
        e.applyKnockback({ x: d.x * 0.35, z: d.z * 0.35 }, 0.55);
      } catch {
        // 밀리지 않는 몹
      }
    });
  }
  // delay 틱 뒤: 바깥으로 퍼지는 불꽃 고리 (rpg:fire_wave) + 적의 발밑에서 솟는 불기둥 (afterMult)
  system.runTimeout(() => {
    if (!ok()) return;
    zonePulseG(dim, c, G_DEEP, k.radius, 0.45);
    zonePulseG(dim, c, G_FIRE, k.radius * 0.7, 0.4);
    for (let j = 0; j < 10; j++) {
      const d = turnY(f, j * 36 + 18);
      system.runTimeout(() => flame(dim, add(c, d, k.radius * (0.8 + 0.15 * (j % 2))), 1.3, 0.5, j % 2 ? G_DEEP : G_FIRE), j % 3);
    }
    play(dim, "mob.blaze.shoot", c, 1.0, 0.5);
    play(dim, "fire.fire", c, 1.0, 0.8);
    const t0 = system.currentTick;
    const pushed = [...struck.values()].filter((e) => e.isValid && Math.hypot(e.location.x - c.x, e.location.z - c.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - c.y) <= 5);
    for (const e of unionById(visibleFrom(player, eye, around(player, c, k.radius)), pushed)) {
      hitAt(player, e, reserveHit(player, e, t0), ok, () => {
        if (skillHit(player, e, w, k.afterMult) > 0) {
          const at = e.isValid ? e.location : c;
          const near = Math.hypot(at.x - player.location.x, at.z - player.location.z) < 2.4; // 1인칭: 몸 가까이는 작게
          flame(dim, at, near ? 0.9 : 1.6, 0.5, G_FIRE);
          embers(dim, at, 1.0, 0.5, 3);
        }
      });
    }
  }, k.delay);
}

/** 가까운 플레이어들 화면 흔들기 */
function shakeNear(dim, at, radius, intensity, seconds) {
  let list = [];
  try {
    list = dim.getPlayers({ location: at, maxDistance: radius });
  } catch {
    list = [];
  }
  for (const pl of list) shake(pl, intensity, seconds);
}

/** before 이벤트: 용익 강하로 떠오른 사람의 낙하 피해를 막음 */
function noFallBlock(ev) {
  if (!noFallUntil.size || ev.damageSource?.cause !== EntityDamageCause.fall) return false;
  const until = noFallUntil.get(ev.hurtEntity?.id);
  return until !== undefined && system.currentTick <= until;
}

/** 오래된 낙하 피해 막기 기록 지우기 */
function pruneNoFall() {
  const now = system.currentTick;
  for (const [id, t] of noFallUntil) if (t < now) noFallUntil.delete(id);
}

// ------------------------------------------------------------ 클라우 솔라스 (단검) — v2.19.0 개편 (빛 · 섬광)
// 사용자가 고른 컨셉 "섬광 난무 / 광휘의 검진" · 세기 "영웅 룬~초월 룬" (Lv.40 민첩 125 의 민첩 룬 위력 기준) · 빛의 검 그림은 모델 옆모습 (rpg:solas_sword)
const SOL_LIGHT = [1.0, 0.95, 0.72]; // 흰 금빛 기운
const SOL_WHITE = [1, 1, 1];
/** 섬광 난무가 이어서 고르는 적이 될 수 있는 무리 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비). 조준한 적은 무엇이든 */
const RUSH_FAMILIES = ["monster", "rpg_monster", "rpg_boss", "rpg_boss_prop", "rpg_dummy"];
/** 이어서 고르지 않는 중립 몹 (때리면 무리가 화를 냄 — 조준하면 됨) */
const RUSH_NEUTRAL = new Set(["minecraft:zombie_pigman", "minecraft:piglin", "minecraft:enderman", "minecraft:wolf", "minecraft:bee", "minecraft:polar_bear", "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:dolphin", "minecraft:goat"]);
/** 서면 다치는 블록 (돌아갈 자리 확인 — combat.js 의 standSpot 과 같은 것) */
const SOL_HAZARD = /lava|fire|campfire|magma|cactus|sweet_berry|wither_rose|powder_snow/;

/** 보스 필드 확인 (bosses.js 가 넣음): 그 보스 (소품) 의 싸움 필드 밖 (울타리 너머) 에 선 자리면 false — 필드 밖에서는 보스에게 피해를 줄 수 없는 규칙을 순간이동으로 넘지 않게 */
let bossFieldOk = (_player, _e, _at) => true;
export function setBossFieldCheck(fn) {
  bossFieldOk = fn;
}

/** 빛의 검 그림 하나 (rpg:solas_sword — 궁니르 창과 같은 변수): at = 칼 가운데, dir = 칼끝 방향 (단위), half = 칼 반 길이 (칸), life = 초 */
function swordFx(dim, at, dir, half, life, o = {}) {
  spearFx(dim, at, dir, half, life, o, "rpg:solas_sword");
}

/** 포물선으로 나는 빛의 검 (rpg:solas_sword_arc — 칼끝이 날아가는 방향을 따라 돎): 처음 속도 v (칸/초), 중력 grav (칸/초²) */
function swordArcFx(dim, at, v, half, life, grav) {
  try {
    const speed = len(v);
    const m = vars({ dir: speed > 1e-6 ? unit(v) : V_DOWN, size: half, life, speed, progress: -1, color: [1, 1, 1], alpha: 1 });
    m.setVector3("variable.move", speed > 1e-6 ? unit(v) : V_DOWN);
    m.setFloat("variable.grav", grav);
    m.setFloat("variable.seg", 0);
    dim.spawnParticle("rpg:solas_sword_arc", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** a → b 곧은 빛 한 줄 (조각 하나) 에서 눈 near 칸 안의 부분은 빼고 (beamAway 와 같은 규칙, 파티클 하나 — 한 틱의 파티클 수를 줄임) */
function streakAway(dim, eye, a, b, color, thick, life, alpha = 1, near = 1.4) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return;
  const u = unit(d);
  let t0 = 0;
  let t1 = L;
  const w = sub(eye, a);
  const tc = w.x * u.x + w.y * u.y + w.z * u.z;
  const off2 = len(w) ** 2 - tc * tc;
  if (off2 < near * near) {
    const hw = Math.sqrt(near * near - off2);
    if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
    else t1 = Math.min(t1, tc - hw);
  }
  if (t1 - t0 > 0.3) streakLine(dim, add(a, u, t0), add(a, u, t1), color, thick, life, alpha);
}

/** 눈 (from) 에서 to 를 보는 회전 {x: 위아래, y: 좌우} (0 = 남쪽 +z, 90 = 서쪽 -x) */
function lookRotation(from, to) {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dz = to.z - from.z;
  return { x: (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI, y: (Math.atan2(-dx, dz) * 180) / Math.PI };
}

/** 순간이동 (몸이 블록과 겹치면 옮기지 않고 false). o = rotation 등 */
function blinkTo(player, p, o = {}) {
  try {
    if (typeof player.tryTeleport === "function") return player.tryTeleport(p, { keepVelocity: false, checkForBlocks: true, ...o }) !== false;
    player.teleport(p, { keepVelocity: false, checkForBlocks: true, ...o });
    return true;
  } catch {
    return false;
  }
}

/** 그 자리 (발 · 머리 칸) 가 서면 다치는 곳인지 (용암 · 불 · 마그마 등) */
function hazardAt(dim, p) {
  try {
    for (const dy of [0.1, 1.1]) {
      const b = dim.getBlock({ x: p.x, y: p.y + dy, z: p.z });
      if (b && SOL_HAZARD.test(b.typeId ?? "")) return true;
    }
  } catch {
    // 모름 — 다치지 않는 곳으로 봄
  }
  return false;
}

/** 무리 이름이 하나라도 있는지 */
function hasFamily(e, list) {
  try {
    const t = e.getComponent("minecraft:type_family");
    return !!t && list.some((f) => t.hasTypeFamily(f));
  } catch {
    return false;
  }
}

/**
 * 섬광 난무의 적: 앞쪽 cone 도 · range 칸 (위아래 3칸) 안의 눈에서 보이는 적 가운데 — 조준한 적 (조준 보정) 부터, 바로 앞 적에서 가까운 순으로 이어 최대 targets 명.
 * 이어서 고르는 적은 적대 무리만 (동물 · 중립 몹은 조준해야 — 무리가 화를 내지 않게)
 */
function rushTargets(player, k) {
  const start = player.location;
  const eye = player.getHeadLocation();
  const f = facingOf(player);
  const half = ((k.cone / 2) * Math.PI) / 180;
  const pool = visibleFrom(player, eye, around(player, start, k.range)).filter((e) => bodyInCone(e, start, f, half));
  let cur;
  try {
    const r = aimTarget(player, k.range, BOW.aimAssist ?? 0);
    if (r.target?.isValid && pool.some((e) => e.id === r.target.id)) cur = r.target; // 조준한 적도 앞쪽 · 높이 안이어야 (멀리 위 턱의 적에게 올라가지 않게)
  } catch {
    // 조준한 적 없음
  }
  const left = new Map(pool.filter((e) => hasFamily(e, RUSH_FAMILIES) && !RUSH_NEUTRAL.has(e.typeId)).map((e) => [e.id, e]));
  const nearestTo = (p) => {
    let best;
    let bd = Infinity;
    for (const e of left.values()) {
      const d = Math.hypot(e.location.x - p.x, e.location.z - p.z);
      if (d < bd) {
        bd = d;
        best = e;
      }
    }
    return best;
  };
  cur ??= nearestTo(start);
  const out = [];
  while (cur && out.length < k.targets) {
    out.push(cur);
    left.delete(cur.id);
    cur = nearestTo(cur.location);
  }
  return out;
}

/**
 * 섬광 난무로 옮길 자리: 적의 몸 반폭 + side 칸 떨어진, want 쪽 (적에서 본 단위 방향) 자리 — 안 되면 want 에서 ±35° · ±70° · ±115° · 반대쪽 순.
 * 설 자리 (standSpot — 물 · 용암 · 불 · 허공 · 막힘 아님) 이고, 처음 자리와 높이가 3칸 안 · 가로 range + 3칸 안이고, 거기서 적의 몸이 보여야 (벽 너머 아님).
 * 없으면 undefined (그 자리에서 빛의 참격)
 */
function rushSpot(dim, e, want, k, start) {
  const T = e.location;
  const reach = 0.4 + bodyPad(e) + k.side;
  const body = bodyPoint(e);
  for (const a of [0, 35, -35, 70, -70, 115, -115, 180]) {
    const q = add(T, turnY(want, a), reach);
    const p = standSpot(dim, { x: q.x, y: T.y, z: q.z }, 2.0);
    if (!p) continue;
    if (Math.abs(p.y - start.y) > 3 || Math.hypot(p.x - start.x, p.z - start.z) > k.range + 3) continue;
    if (!clearPath(dim, up(p, 1.2), body)) continue;
    return p;
  }
  return undefined;
}

/**
 * 섬광 난무 (v2.19.0): 앞쪽 (cone 도) range 칸 안의 적 — 조준한 적부터 가까운 순으로 이어 최대 targets 명 — 사이를 gap 틱마다 빛이 되어 순간이동하며 벰 (모두 blinks 번,
 * 적이 적으면 돌아가며 — 한 명이면 그 둘레를 90° 씩 돌며 네 방향에서). 옮길 때마다 그 적을 바라봄. 적마다 처음 벨 때 mult (치명타는 무기 그대로), 몸에 빛의 자국이 남음
 * (적을 따라감) → 처음 자리로 돌아와 (보던 방향으로) delay 틱 뒤 검을 거두면 지나온 길이 다시 빛나고 모든 자국이 한꺼번에 터짐 (afterMult — 처음 자리에서 range + 6칸 안이면).
 * 옮기지 않음 (그 자리에서 빛의 참격, 피해 같음): 설 자리가 없음 · 속박 · 침묵 (보스가 잡음 · 삼킴) · 탐 · 다른 것이 나를 옮김 (끌어당김 · 엔더 진주 — 그 뒤로는 돌아오지도 않음) ·
 * 보스 필드 밖에서 그 보스에게. 처음 자리가 서면 다치는 곳 (용암 · 불) 이 되었으면 돌아오지 않음. 쓰는 동안 · 뒤 0.4초는 낙하 피해 없음.
 * 앞쪽에 적이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
function solasRush(player, w, k) {
  const list = rushTargets(player, k);
  if (!list.length) {
    warn(player, `§c${k.name}: §7앞쪽 ${k.range}칸 안에 적이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow, core] = colors(w);
  const start = { ...player.location };
  let rot0;
  try {
    const r = player.getRotation();
    rot0 = { x: r.x, y: r.y };
  } catch {
    rot0 = undefined;
  }
  const backAt = k.blinks * k.gap; // 처음 자리로 돌아오는 틱
  const burstAt = backAt + k.delay; // 자국이 터지는 틱
  noFallUntil.set(player.id, Math.max(noFallUntil.get(player.id) ?? 0, system.currentTick + burstAt + 8));
  /** 벤 적 → 자국 기울기 (처음 벨 때만 피해 · 마지막에 자국이 터짐) @type {Map<string, {e: any, angles: Set<number>}>} */
  const marked = new Map();
  /** 적마다 옮겨 간 횟수 · 처음 선 쪽 (다시 갈 때 90° 씩 돌아감 — 한 명이면 네 방향) @type {Map<string, {n: number, dir: any}>} */
  const visits = new Map();
  /** moved: 한 번이라도 옮겼는지, path: 지나온 자리 (몸 높이 — 검을 거둘 때 다시 빛남), last: 이 스킬이 마지막으로 둔 자리, broken: 다른 것이 나를 옮김 */
  const st = { moved: false, path: [up(start, 1.1)], last: { ...start }, broken: false };
  const held = () => isRooted(player) || isSilenced(player) || ridingAny(player);
  /** 다른 것이 나를 옮겼는지 (한 틱 사이 가로 2.5칸 · 세로 3칸 넘게 — 이 스킬이 둔 자리에서) */
  const displaced = () => {
    const l = player.location;
    if (Math.hypot(l.x - st.last.x, l.z - st.last.z) > 2.5 || Math.abs(l.y - st.last.y) > 3) st.broken = true;
    return st.broken;
  };
  // 시작: 손의 금빛 번쩍임 + 발밑 고리
  const f0 = facingOf(player);
  const eye0 = player.getHeadLocation();
  fx(dim, "rpg:arrow_spark", up(add(add(eye0, f0, 0.9), rightOf(f0), 0.35), -0.35), { dir: f0, size: 0.3, grow: 3, life: 0.2, color: core, alpha: 1 });
  shock(dim, start, glow, 1.4, 0.35);
  play(dim, "chime.amethyst_block", eye0, 1.6, 1.0);
  for (let i = 0; i < k.blinks; i++) {
    const blink = () => {
      if (!ok()) return;
      let e = list[i % list.length];
      if (!canHit(player, e)) e = list.find((x) => canHit(player, x)); // 쓰러졌으면 남은 적
      if (!e) return;
      const from = { ...player.location };
      const look = bodyPoint(e);
      const T = e.location;
      // 처음 가는 적: 지금 자리에서 그 적을 지나 반대편 (번갈아 ±35° 비낌). 다시 가는 적: 처음 선 쪽에서 90° 씩 돌아감
      const been = visits.get(e.id);
      const through = Math.hypot(T.x - from.x, T.z - from.z) > 0.05 ? flat(sub(T, from)) : facingOf(player);
      const want = been ? turnY(been.dir, 90 * been.n) : turnY(through, i % 2 ? -35 : 35);
      const free = !held() && !displaced() && bossFieldOk(player, e, start);
      const spot = free ? rushSpot(dim, e, want, k, start) : undefined;
      const moved = !!spot && blinkTo(player, spot, { rotation: lookRotation(up(spot, 1.62), look) });
      if (been) been.n++;
      else visits.set(e.id, { n: 1, dir: moved ? flat(sub(spot, T)) : want });
      const eye = player.getHeadLocation();
      if (moved) {
        st.moved = true;
        st.last = { ...player.location };
        st.path.push(up(spot, 1.1));
        // 빛이 되어 지나간 길 (한 줄, 눈 1.2칸 안은 빼고 — 1인칭 화면을 덮지 않게) + 떠난 자리의 잔상 번쩍임
        streakAway(dim, eye, up(from, 1.1), up(spot, 1.1), glow, 0.4, 0.3, 0.9, 1.2);
        streakAway(dim, eye, up(from, 1.1), up(spot, 1.1), SOL_WHITE, 0.13, 0.24, 1, 1.2);
        burst(dim, up(from, 1.0), SOL_LIGHT, 0.9, 0.22);
        sparks(dim, up(from, 1.0), glow, 5, 3);
        play(dim, "mob.endermen.portal", spot, 1.5 + 0.1 * i, 0.7);
      } else {
        // 옮기지 못함: 손에서 그 적으로 빛의 참격이 날아감
        const hand = add(up(eye, -0.25), rightOf(facingOf(player)), 0.3);
        streakAway(dim, eye, hand, look, glow, 0.32, 0.26, 0.9, 1.2);
        streakAway(dim, eye, hand, look, SOL_WHITE, 0.1, 0.2, 1, 1.2);
      }
      // 벤 자리: 빛의 초승달 (눈 가까이는 작게) + 빛의 자국 (/ \ 번갈아 — 적을 따라 다시 그림, 터질 때까지) + 불꽃
      const v = unit(sub(look, eye));
      const near = Math.min(1, Math.max(0.5, len(sub(look, eye)) / 3));
      arc(dim, add(look, v, -0.2), v, { shape: "arc", flip: i % 2 === 1, size: 1.9 * near, life: 0.24, speed: 1.2, spin: (i % 2 ? -1 : 1) * 40, color: glow, aura: SOL_LIGHT });
      const angle = i % 2 ? -38 : 38;
      cutMark(dim, look, v, angle, 0.2, glow, core, near);
      sparks(dim, look, core, 5, 4);
      play(dim, `item.enchant.lunge${(i % 3) + 1}`, look, 1.1 + 0.08 * i, 1.0);
      play(dim, "item.spear.attack_miss", look, 1.4 + 0.1 * i, 1.0);
      const m = marked.get(e.id);
      if (m) {
        m.angles.add(angle);
        return;
      }
      marked.set(e.id, { e, angles: new Set([angle]) });
      hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
        const at = bodyAt0(e, look);
        if (skillHit(player, e, w, k.mult) > 0) play(dim, "chime.amethyst_block", at, 1.6 + 0.1 * i, 1.0);
      });
    };
    if (i === 0) blink();
    else system.runTimeout(blink, i * k.gap);
  }
  // 빛의 자국: 4틱마다 적의 지금 몸에 다시 그림 (걸어가도 따라감) — 터질 때까지
  for (let t = 4; t < burstAt; t += 4) {
    system.runTimeout(() => {
      if (!ok()) return;
      const eye = player.getHeadLocation();
      for (const { e, angles } of marked.values()) {
        if (!e.isValid) continue;
        const c = bodyAt0(e, eye);
        const v = unit(sub(c, eye));
        const near = Math.min(1, Math.max(0.5, len(sub(c, eye)) / 3));
        for (const a of angles) cutMark(dim, c, v, a, Math.min(0.2, (burstAt - t) / 20), glow, core, near);
      }
    }, t);
  }
  // 처음 자리로 돌아옴 (보던 방향으로). 다른 것이 나를 옮겼거나 · 묶였거나 · 그 자리가 용암 · 불이 되었으면 그대로
  system.runTimeout(() => {
    if (!ok() || !st.moved || held() || displaced() || hazardAt(dim, start)) return;
    const from = { ...player.location };
    if (!blinkTo(player, start, rot0 ? { rotation: rot0 } : {})) return;
    st.last = { ...start };
    st.path.push(up(start, 1.1));
    const eye = player.getHeadLocation();
    streakAway(dim, eye, up(from, 1.1), up(start, 1.1), glow, 0.4, 0.32, 0.9, 1.2);
    streakAway(dim, eye, up(from, 1.1), up(start, 1.1), SOL_WHITE, 0.13, 0.26, 1, 1.2);
    burst(dim, up(from, 1.0), SOL_LIGHT, 0.9, 0.22);
    shock(dim, start, glow, 1.2, 0.3);
    play(dim, "mob.endermen.portal", start, 1.9, 0.8);
  }, backAt);
  // 검을 거둠: 지나온 길이 다시 빛나고 모든 빛의 자국이 한꺼번에 터짐
  system.runTimeout(() => {
    if (!ok()) return;
    const eye = player.getHeadLocation();
    const f = facingOf(player);
    fx(dim, "rpg:arrow_spark", up(add(add(eye, f, 0.8), rightOf(f), 0.3), -0.45), { dir: f, size: 0.25, grow: 3, life: 0.18, color: SOL_LIGHT, alpha: 1 });
    play(dim, "armor.equip_gold", eye, 1.3, 1.0);
    for (let j = 0; j + 1 < st.path.length; j++) {
      streakAway(dim, eye, st.path[j], st.path[j + 1], glow, 0.5, 0.36, 0.95, 1.4);
      streakAway(dim, eye, st.path[j], st.path[j + 1], SOL_WHITE, 0.16, 0.3, 1, 1.4);
    }
    let any = false;
    for (const { e } of marked.values()) {
      if (!canHit(player, e) || Math.hypot(e.location.x - start.x, e.location.z - start.z) > k.range + 6) continue;
      any = true;
      const c = bodyAt0(e, eye);
      const feet = { ...e.location };
      hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
        if (skillHit(player, e, w, k.afterMult) > 0) lightBurst(dim, c, feet, player.getHeadLocation(), glow, core);
      });
    }
    if (any) {
      play(dim, "firework.blast", eye, 1.2, 1.0);
      play(dim, "block.bell.hit", eye, 1.6, 0.8);
      shake(player, 0.18, 0.18);
      system.runTimeout(() => {
        if (player.isValid) play(player.dimension, "firework.twinkle", player.getHeadLocation(), 1.1, 1.0);
      }, 3);
    }
  }, burstAt);
  return true;
}

/**
 * 자국이 터지는 모습 (v1.2.1 빛의 삼연격에서): 흰 X 번쩍임 → 금빛 폭발 · 고리 · 불꽃 + 발밑에서 솟는 빛기둥.
 * c = 몸 가운데, feet = 발 (맞기 전에 잰 자리 — 쓰러져도 그림), eye = 보는 사람 눈 (가까우면 작게 · 2.5칸 안이면 빛기둥 심지는 뺌 — 1인칭 화면을 덮지 않게)
 */
function lightBurst(dim, c, feet, eye, glow, core) {
  const d = len(sub(c, eye));
  const v = d > 1e-3 ? unit(sub(c, eye)) : V_UP;
  const s = Math.min(1, Math.max(0.45, (d - 0.8) / 3));
  cutMark(dim, c, v, 38, 0.05, SOL_WHITE, SOL_WHITE, 1.2 * s);
  cutMark(dim, c, v, -38, 0.05, SOL_WHITE, SOL_WHITE, 1.2 * s);
  burst(dim, add(c, v, -0.15), glow, 0.95 * s, 0.3);
  sparks(dim, c, core, 4, 6); // 한 틱에 넷이 함께 터져도 60개 안 (모바일)
  lightPillar(dim, feet, glow, s, d >= 2.5);
}

/** 발밑에서 솟는 빛기둥 (금빛 빛 + 흰 심지) + 발밑 고리. size 1 = 높이 약 3칸. withCore = 흰 심지 (가까우면 뺌) */
function lightPillar(dim, feet, glow, size, withCore = true) {
  fx(dim, "rpg:arrow_shot", up(feet, 1.5 * size), { dir: V_UP, speed: 0, size: 1.6 * size, thick: 0.5 * size, life: 0.42, color: glow, alpha: 0.85 });
  if (withCore) fx(dim, "rpg:arrow_shot", up(feet, 1.5 * size), { dir: V_UP, speed: 0, size: 1.5 * size, thick: 0.2 * size, life: 0.34, color: SOL_WHITE, alpha: 1 });
  shock(dim, feet, glow, 1.3 * size, 0.4);
}

/** 광휘의 검진의 조준한 곳: 조준한 몹 (조준 보정) 의 발 → 시선이 닿은 곳 (벽 면이면 가로 0.4칸 앞) → 사거리 끝 */
function haloSpot(player, k) {
  const eye = player.getHeadLocation();
  try {
    const r = aimTarget(player, k.range, BOW.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) return { ...r.target.location };
    const d = r.dir ?? player.getViewDirection();
    const q = add(eye, d, r.dist ?? k.range);
    return Math.hypot(d.x, d.z) > 0.2 ? add(q, flat(d), -0.4) : q;
  } catch {
    return add(eye, player.getViewDirection(), k.range);
  }
}

/** 땅 높이 (at 아래 maxDown 칸 안의 블록 윗면 — 물 · 용암도), 없으면 undefined */
function groundBelow(dim, at, maxDown) {
  try {
    const hit = dim.getBlockFromRay(up(at, 0.5), V_DOWN, { maxDistance: maxDown + 0.5, includePassableBlocks: false, includeLiquidBlocks: true });
    if (!hit) return undefined;
    const fy = hit.faceLocation?.y;
    return hit.block.location.y + (typeof fy === "number" && fy > 0.05 && fy <= 1 ? fy : 1);
  } catch {
    return undefined;
  }
}

/**
 * 광휘의 검진 (v2.19.0): 조준한 곳 (range 칸) 반지름 radius 칸의 적 (눈에서 보이는 · 적대 무리 먼저 · 가까운 순 targets 명) — 없으면 재사용 대기 없이 거절.
 * 머리 위 반원 (반지름 ring, 오른쪽 → 위 → 왼쪽 — 칼끝이 바깥, 햇살처럼. 따라다님, 머리 위가 낮으면 작게) 에 빛의 검 swords 자루가 한 틱에 하나씩 나타남
 * (적을 몸의 오른쪽 → 왼쪽 순으로 나눠 맡음) → hold 틱 뒤 launchGap 틱마다 오른쪽부터 하나씩 포물선으로 날아올라 맡은 적에게 위에서 내리꽂힘
 * (거리에 따라 0.3~0.6초 — 칼끝이 나는 쪽을 따라 돌아서 옆에서 보임, 맡은 적이 쓰러졌으면 다른 적에게): 적마다 처음 꽂힐 때 mult (꽂힌 검은 적을 따라감) →
 * 마지막 검이 꽂히고 pillar 틱 뒤 꽂힌 검이 빛기둥으로 터짐 (pillarMult — 꽂힌 적) → cross 틱 뒤 조준한 곳 (아래 땅) 에 빛의 X 참격 (afterMult —
 * 반지름 안의 적 + 꽂힌 적은 밀려났어도 radius + 2.5칸 안이면, 1인칭 화면을 다 덮지 않게 거리에 맞춘 크기). 꽂힌 적에게 한 대상 mult + pillarMult + afterMult.
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
function solasHalo(player, w, k) {
  const dim = player.dimension;
  const head0 = player.getHeadLocation();
  const spot = haloSpot(player, k);
  const hostile = (e) => hasFamily(e, RUSH_FAMILIES);
  const near = visibleFrom(player, head0, around(player, spot, k.radius)).sort((a, b) => (hostile(b) ? 1 : 0) - (hostile(a) ? 1 : 0) || len(sub(a.location, spot)) - len(sub(b.location, spot)));
  const list = near.slice(0, k.targets);
  if (!list.length) {
    warn(player, `§c${k.name}: §7조준한 곳 ${k.radius}칸 안에 적이 없습니다`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow, core] = colors(w);
  // 머리 위가 낮으면 (굴 · 지붕 아래) 반원 · 검을 그만큼 작게, 포물선도 낮게 (천장을 뚫지 않게)
  const room = rayDist(dim, up(head0, 0.1), V_UP, 3.5) + 0.1;
  const fit = Math.min(1, Math.max(0.2, (room - 0.2) / (1.1 + k.ring + k.half)));
  const ring = k.ring * fit;
  const half = k.half * fit;
  /** 지금 머리 위 반원에서 i 번째 검의 자리 · 칼끝 방향 (따라다님) */
  const haloAt = (i) => {
    const f = facingOf(player);
    const side = rightOf(f);
    const C = add(up(player.getHeadLocation(), 1.1 * fit), f, -0.3);
    const th = (i / Math.max(1, k.swords - 1)) * Math.PI; // 0 = 오른쪽, π = 왼쪽
    const ray = add({ x: side.x * Math.cos(th), y: 0, z: side.z * Math.cos(th) }, V_UP, Math.sin(th));
    return { pos: add(C, ray, ring), ray };
  };
  // 몸의 오른쪽에 있는 적부터 (오른쪽 검이 오른쪽 적을 맡음)
  const side0 = rightOf(facingOf(player));
  const sideOf = (e) => (e.location.x - head0.x) * side0.x + (e.location.z - head0.z) * side0.z;
  const order = [...list].sort((a, b) => sideOf(b) - sideOf(a));
  const n = k.swords;
  const swords = [];
  for (let i = 0; i < n; i++) {
    const e = order[Math.floor((i * order.length) / n)];
    swords.push({ i, e, last: bodyPoint(e), launch: k.form + k.hold + i * k.launchGap, flight: 0 });
  }
  /** 꽂힌 적 (빛기둥 · X 참격이 따라감) @type {Map<string, any>} */
  const stabbed = new Map();
  /** 꽂힌 검 (적을 따라 다시 그림 · 빛기둥 때 흩어짐) @type {{e: any, rel: any, dir: any, at: any, half: number, since: number}[]} */
  const stuck = [];
  // 들어 올림: 손의 금빛 번쩍임 + 발밑 고리 + 머리 위 빛
  const f0 = facingOf(player);
  fx(dim, "rpg:arrow_spark", up(add(add(head0, f0, 0.9), side0, 0.35), -0.3), { dir: f0, size: 0.35, grow: 3, life: 0.25, color: SOL_LIGHT, alpha: 1 });
  shock(dim, player.location, glow, 1.6, 0.45);
  burst(dim, add(up(head0, 1.1 * fit), f0, -0.3), SOL_LIGHT, 1.4 * fit, 0.35);
  play(dim, "beacon.activate", head0, 1.6, 1.0);
  play(dim, "mob.evocation_illager.prepare_attack", head0, 1.4, 0.7);
  for (const s of swords) {
    // 나타남 (한 틱에 하나) → 날아갈 때까지 3틱마다 지금 머리 위에 다시 그림 (따라다님)
    const appear = Math.min(s.i, k.form - 1);
    for (let t = appear; t < s.launch; t += 3) {
      const draw = () => {
        if (!ok()) return;
        const h = haloAt(s.i);
        swordFx(dim, h.pos, h.ray, half, Math.min(3, s.launch - t) / 20 + 0.02);
        if (t !== appear) return;
        burst(dim, h.pos, SOL_LIGHT, 0.5 * fit, 0.18);
        play(dim, "chime.amethyst_block", h.pos, 1.0 + s.i * 0.08, 0.8);
        // 1인칭: 화면 위쪽으로 떨어지는 금빛 알갱이 (검은 머리 위라 화면 밖)
        const fv = facingOf(player);
        fx(dim, "rpg:arrow_spark", up(add(add(player.getHeadLocation(), fv, 1.7), rightOf(fv), (s.i / Math.max(1, n - 1) - 0.5) * -2.2), 1.0), { dir: V_DOWN, speed: 1.0, size: 0.16, grow: 0.8, life: 0.6, color: SOL_LIGHT, alpha: 1 });
      };
      if (t === 0) draw();
      else system.runTimeout(draw, t);
    }
    // 날아감: 포물선으로 날아올라 맡은 적 (쓰러졌으면 다른 꽂힌 · 남은 적, 없으면 마지막 자리) 에게 위에서 내리꽂힘
    system.runTimeout(() => {
      if (!ok()) return;
      if (!canHit(player, s.e)) s.e = [...stabbed.values(), ...order].find((x) => canHit(player, x)) ?? s.e;
      const P = haloAt(s.i).pos;
      const body = bodyAt0(s.e, s.last);
      s.last = body;
      // 같은 적에 꽂히는 검은 몸 둘레로 조금씩 비껴 꽂힘 (겹쳐 하나로 보이지 않게 — 황금각으로 돌며 점점 바깥)
      const j = swords.filter((x) => x.e === s.e && x.i < s.i).length;
      const toward = flat(sub(body, P));
      const sideU = rightOf(toward);
      const r = Math.min(0.5, 0.16 * j);
      const ga = j * 2.4;
      const aim = add(add(body, sideU, Math.cos(ga) * r), V_UP, Math.sin(ga) * r * 1.4);
      const hd = Math.hypot(aim.x - P.x, aim.z - P.z);
      const T = Math.min(0.6, Math.max(0.3, hd / 22)); // 날아가는 시간 (초)
      const flight = Math.max(1, Math.round(T * 20));
      const Ts = flight / 20;
      const arcH = (2.0 + 0.1 * hd) * fit; // 포물선이 곧은 줄보다 솟는 높이
      const g = (8 * arcH) / (Ts * Ts);
      // 칼끝이 몸에 박히게: 끝 자리 = 겨눈 곳에서 내리꽂는 방향으로 칼 반 길이의 0.55 앞 (두 번 잼 — 방향이 끝 자리에 따라 조금 바뀜)
      // 처음 속도 v0 = (끝 − 처음) ÷ 시간 + 위로 ½ g T (그 시간에 끝에 닿는 포물선), 끝 속도 = v0 − g T
      const launchV = (to) => add({ x: (to.x - P.x) / Ts, y: (to.y - P.y) / Ts, z: (to.z - P.z) / Ts }, V_UP, 0.5 * g * Ts);
      let end = aim;
      let vF = V_DOWN;
      for (let it = 0; it < 2; it++) {
        vF = add(launchV(end), V_DOWN, g * Ts);
        end = add(aim, unit(vF), -k.half * fit * 0.55);
      }
      const v0 = launchV(end);
      vF = add(v0, V_DOWN, g * Ts);
      const u = unit(vF);
      s.flight = flight;
      swordArcFx(dim, P, v0, k.half * fit, Ts, g);
      play(dim, "item.trident.throw", P, 1.5 + 0.05 * s.i, 0.6);
      // 꽂힘
      system.runTimeout(() => {
        if (!ok()) return;
        const e = s.e;
        stuck.push({ e, rel: sub(end, bodyAt0(e, aim)), dir: u, at: end, half: k.half * fit, since: system.currentTick });
        swordFx(dim, end, u, k.half * fit, 0.22);
        burst(dim, aim, SOL_LIGHT, 0.8, 0.22);
        sparks(dim, aim, glow, 5, 5);
        play(dim, "item.trident.hit", aim, 1.4, 0.9);
        play(dim, "hit.amethyst_cluster", aim, 1.2, 1.0);
        if (stabbed.has(e.id) || !canHit(player, e)) return;
        stabbed.set(e.id, e);
        hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
          skillHit(player, e, w, k.mult);
        });
      }, flight);
    }, s.launch);
  }
  // 마지막 검이 꽂힌 뒤 pillar 틱: 꽂힌 검이 빛기둥으로 터짐 → cross 틱 뒤 빛의 X (꽂히는 시각은 거리에 따라 달라서 마지막 검이 날아간 뒤에 정함)
  const lastLaunch = swords[n - 1].launch;
  system.runTimeout(() => {
    if (!ok()) return;
    const lastHit = Math.max(...swords.map((s) => s.launch + (s.flight || 1)));
    const pillarIn = lastHit + k.pillar - lastLaunch;
    // 꽂힌 검: 4틱마다 적의 지금 몸에 다시 그림 (걸어가도 따라감)
    for (let t = 4; t < pillarIn; t += 4) {
      system.runTimeout(() => {
        if (!ok()) return;
        for (const x of stuck) {
          if (!x.e.isValid || system.currentTick - x.since < 3) continue; // 막 꽂힌 검은 그대로 (같은 자리에 두 장 겹치지 않게)
          x.at = add(bodyAt0(x.e, x.at), x.rel);
          swordFx(dim, x.at, x.dir, x.half, Math.min(0.22, (pillarIn - t) / 20 + 0.02));
        }
      }, t);
    }
    system.runTimeout(() => solasPillars(player, w, k, { ok, spot, glow, core, stabbed, stuck, fit }), pillarIn);
  }, lastLaunch);
  return true;
}

/** 광휘의 검진 끝부분: 꽂힌 검이 빛기둥으로 터짐 (pillarMult) → cross 틱 뒤 조준한 곳에 빛의 X 참격 (afterMult) */
function solasPillars(player, w, k, o) {
  const { ok, spot, glow, core, stabbed, stuck } = o;
  if (!ok()) return;
  const dim = player.dimension;
  for (const x of stuck) {
    if (x.e.isValid) x.at = add(bodyAt0(x.e, x.at), x.rel);
    swordFx(dim, x.at, x.dir, x.half, 0.4, { progress: 2 });
  }
  const t0 = system.currentTick;
  for (const e of stabbed.values()) {
    if (!e.isValid) continue;
    const feet = { ...e.location };
    const c = bodyAt0(e, spot);
    lightPillar(dim, feet, glow, 1.25);
    burst(dim, c, SOL_WHITE, 1.1, 0.25);
    hitAt(player, e, reserveHit(player, e, t0), ok, () => {
      skillHit(player, e, w, k.pillarMult);
    });
  }
  play(dim, "beacon.power", spot, 1.4, 1.2);
  play(dim, "firework.blast", spot, 1.0, 1.2);
  // 거대한 빛의 X 참격: 조준한 곳 아래 땅 (4칸 안) 위 1.2칸 — 나를 향해 섬, 1인칭에서 화면을 다 덮지 않게 거리에 맞춘 크기
  system.runTimeout(() => {
    if (!ok()) return;
    const eye = player.getHeadLocation();
    const gy = groundBelow(dim, spot, 4);
    const ground = { x: spot.x, y: gy ?? spot.y, z: spot.z };
    const c = up(ground, 1.2);
    const dist = Math.hypot(c.x - eye.x, c.z - eye.z);
    const fv = facingOf(player);
    const dirX = dist > 1e-3 ? unit({ x: c.x - eye.x, y: 0, z: c.z - eye.z }) : fv;
    const big = Math.min(k.radius * 0.95, Math.max(1.0, dist * 0.32));
    const at = dist > 1e-3 ? c : add(eye, fv, 3);
    arc(dim, at, dirX, { shape: "heavy", flip: false, size: big, life: 0.42, speed: 1.5, spin: 45, color: glow, aura: SOL_LIGHT });
    system.runTimeout(() => arc(dim, add(at, dirX, 0.08), dirX, { shape: "heavy", flip: true, size: big, life: 0.42, speed: 1.5, spin: -45, color: glow, aura: SOL_LIGHT }), 1);
    fx(dim, "rpg:slash_x", add(at, dirX, -0.1), { dir: dirX, size: big * 0.45, life: 0.3, color: SOL_WHITE, alpha: 1 });
    burst(dim, at, SOL_LIGHT, big * 0.5, 0.35);
    shock(dim, ground, glow, k.radius / 2.1, 0.5);
    shock(dim, ground, SOL_WHITE, k.radius / 4.2, 0.4);
    sparks(dim, at, core, 12, 7);
    play(dim, "firework.large_blast", spot, 1.0, 1.2);
    play(dim, "game.player.attack.critical", spot, 0.9, 1.2);
    play(dim, "block.bell.hit", spot, 1.3, 1.0);
    shakeNear(dim, spot, 14, 0.3, 0.3);
    const t1 = system.currentTick;
    const pushed = [...stabbed.values()].filter((e) => e.isValid && Math.hypot(e.location.x - spot.x, e.location.z - spot.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - spot.y) <= 5);
    for (const e of unionById(visibleFrom(player, up(spot, 1.2), around(player, spot, k.radius)), pushed)) {
      const b = bodyAt0(e, c);
      hitAt(player, e, reserveHit(player, e, t1), ok, () => {
        if (skillHit(player, e, w, k.afterMult) <= 0) return;
        const u = unit(sub(b, player.getHeadLocation()));
        cutMark(dim, b, u, 45, 0.08, glow, core, 1.1);
        cutMark(dim, b, u, -45, 0.08, glow, core, 1.1);
      });
    }
  }, k.cross);
}

// ------------------------------------------------------------ 궁니르 (도끼 규칙의 창) — v2.18.0 개편 (번개 · 신창)
// 사용자가 고른 컨셉 "신창 귀환 / 신창 강림" · 세기 "영웅 룬~초월 룬" (Lv.40 힘 83 · 체력 42 의 도끼 룬 위력 기준) · 그림은 "파티클 그림만" (rpg:gungnir_spear = 모델 옆모습)
const GN_BOLT = [0.62, 0.86, 1.0];
const GN_WHITE = [0.94, 0.98, 1.0];
const GN_DEEP = [0.22, 0.4, 0.95];
const GN_DARK = [0.05, 0.07, 0.16];
const GN_CLOUD = [0.2, 0.22, 0.34];
const HEAVY_MOB = new Set(RUNE_GRAVITY.heavy ?? []);
/** 끌지 않는 바닐라 몹 (몸이 넓거나 무거움 — 몸 크기를 스크립트가 모름) */
const DRAG_SKIP = new Set(["minecraft:ravager", "minecraft:sniffer", "minecraft:camel", "minecraft:hoglin", "minecraft:zoglin", "minecraft:polar_bear", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:strider", "minecraft:breeze"]);
const SPEAR_PARTS = 4; // build.py 의 SPEAR_PARTS (거대한 창을 나눠 그리는 조각 수)
const SPEAR_TEX = { w: 512, len: 469 }; // 그림 가로 · 그 안의 창 길이 (점)

/**
 * 궁니르 그림 하나 (rpg:gungnir_spear): at = 창 가운데, dir = 창끝 방향 (단위), half = 창 반 길이 (칸), life = 초.
 * o.move · o.speed = 움직이는 방향 · 초당 칸 (돌아올 때는 창끝과 반대로 움직임), o.progress = -1 번개 되풀이 (기본) · 0~1 그 장 · 2 수명 동안 흩어짐
 */
function spearFx(dim, at, dir, half, life, o = {}, id = "rpg:gungnir_spear", seg = 0) {
  try {
    const m = vars({ dir, size: half, life, speed: o.speed ?? 0, progress: o.progress ?? -1, color: [1, 1, 1], alpha: o.alpha ?? 1 });
    m.setVector3("variable.move", o.move ?? dir);
    m.setFloat("variable.seg", seg);
    dim.spawnParticle(id, at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 거대한 창: SPEAR_PARTS 조각으로 나눠 그림 (rpg:gungnir_spear_part — 한 장이면 가운데가 화면 밖일 때 창 전체가 빠질 수 있어서). center · dir · half 는 창 전체 */
function spearParts(dim, center, dir, half, life, o = {}) {
  const pw = SPEAR_TEX.w / SPEAR_PARTS;
  for (let s = 0; s < SPEAR_PARTS; s++) {
    const off = ((pw * (s + 0.5) - SPEAR_TEX.w / 2) * 2 * half) / SPEAR_TEX.len; // 조각 가운데가 창 가운데에서 dir 쪽으로 몇 칸
    spearFx(dim, add(center, dir, off), dir, half, life, o, "rpg:gungnir_spear_part", s);
  }
}

/** 보는 사람을 향한 창이 화면에서 돎 (rpg:gungnir_spin): 처음 각 deg, 초당 rate 도 — 틱마다 다시 그리지 않아 매끄러움 */
function spinFx(dim, at, half, life, deg, rate) {
  try {
    const m = vars({ size: half, life, spin: deg, speed: rate, progress: -1, color: [1, 1, 1], alpha: 1 });
    m.setVector3("variable.move", { x: 0, y: 1, z: 0 });
    dim.spawnParticle("rpg:gungnir_spin", at, m);
  } catch {
    // 무시
  }
}

/** 몸이 향한 수평 방향 (바로 위 · 아래를 보면 시선에 수평 성분이 없어서 머리의 y 회전으로 — 0도 = 남쪽 +z) */
export function facingOf(player) {
  const v = player.getViewDirection();
  if (Math.hypot(v.x, v.z) > 0.05) return flat(v);
  try {
    const yaw = (player.getRotation().y * Math.PI) / 180;
    return { x: -Math.sin(yaw), y: 0, z: Math.cos(yaw) };
  } catch {
    return { x: 0, y: 0, z: 1 };
  }
}

/** 신창 귀환이 꿰어 끌어올 수 있는 몹: 살아 있는 보통 몹 — 보스 · 보스 소품 · 허수아비 · 아주 큰 몹 (엔더 드래곤 등) · 넓거나 키 큰 몹 (엔더맨 · 트롤 등) 은 아님 (피해만) */
function draggable(e) {
  try {
    if (!e?.isValid || hasNoHealth(e) || e.typeId === PLAYER || HEAVY_MOB.has(e.typeId) || DRAG_SKIP.has(e.typeId) || bodyPad(e) > 0.6) return false;
    if (e.getHeadLocation().y - e.location.y > 2.3) return false; // 키 큰 몸 (손 높이로 끌면 발이 땅에 박혀 옮겨지지 않음)
    const t = e.getComponent("minecraft:type_family");
    return !(t?.hasTypeFamily("rpg_boss") || t?.hasTypeFamily("rpg_boss_prop") || t?.hasTypeFamily("rpg_dummy"));
  } catch {
    return false;
  }
}

/** 창이 지나가며 벤 자리: 하늘빛 X 번쩍임 + 불꽃 (눈 가까이는 작게) */
function spearHitFx(dim, e, eye, dir, glow, core) {
  const c = bodyAt0(e, eye);
  const near = Math.min(1, Math.max(0.45, len(sub(c, eye)) / 3));
  fx(dim, "rpg:slash_x", c, { dir, size: 1.0 * near, life: 0.25, color: glow, alpha: 1 });
  fx(dim, "rpg:slash_x", add(c, dir, -0.04), { dir, size: 0.7 * near, life: 0.2, color: core, alpha: 1 });
  sparks(dim, c, GN_WHITE, 5, 5);
}

/** a → b 빛줄기에서 눈 near 칸 안의 부분은 빼고 그림 (1인칭 화면을 덮지 않게) */
function beamAway(dim, eye, a, b, color, o, near = 1.6) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return;
  const u = unit(d);
  let t0 = 0;
  let t1 = L;
  // 선에서 눈까지 near 칸 안인 구간을 잘라냄 (앞쪽이나 뒤쪽 한쪽만 남김)
  const w = sub(eye, a);
  const tc = w.x * u.x + w.y * u.y + w.z * u.z;
  const off2 = len(w) ** 2 - tc * tc;
  if (off2 < near * near) {
    const hw = Math.sqrt(near * near - off2);
    if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
    else t1 = Math.min(t1, tc - hw);
  }
  if (t1 - t0 > 0.3) beam(dim, add(a, u, t0), add(a, u, t1), color, o);
}

/**
 * 신창 귀환 (v2.18.0): 조준한 쪽 (조준 보정 — 시선에 몹이 겹치지 않으면 가까운 몹 쪽, 창이 닿는 거리 안) 으로 궁니르를 던짐. 창끝이 손에서 range 칸 (벽 앞까지) 에 닿을 때까지
 * 틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult) → 끝에서 hover 틱 동안 돌며 (보는 사람을 향해 한 바퀴) 하늘의 번개를 모음 → 손으로 돌아옴 (3틱마다 지금 손을 다시 겨눔, 창끝은 바깥):
 * 지나는 적을 다시 벰 (backMult) — 끌 수 있는 몹은 hook 마리까지 창끝에 꿰어 내 앞까지 끌어옴 (번개 사슬). 몹은 나에게서 멀어지는 쪽으로는 옮기지 않고 (창이 따라잡을 때까지 기다림),
 * 벽 너머 · 막힌 자리로 옮기게 되면 · 쓰러지면 빠짐. 몹 무적 시간을 피해 적마다 틱을 잡아 넣음 (reserveHit · hitAt). 쓰러지거나 차원을 옮기면 멈춤,
 * 돌아오는 동안 손이 멀리 순간이동하면 창은 바로 손에 돌아옴. 벽이 가까우면 (창이 날 거리가 없으면) 창 그림 없이 번개로만 (1인칭 화면을 덮지 않게)
 */
function gungnirReturn(player, w, k) {
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow, core] = colors(w);
  const handOf = () => add(up(player.getHeadLocation(), -0.25), rightOf(facingOf(player)), 0.3);
  const hand = handOf();
  const eye0 = player.getHeadLocation();
  let dir = player.getViewDirection();
  try {
    const r = aimTarget(player, k.range - 1, BOW.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) dir = unit(sub(bodyPoint(r.target), hand));
    else if (r.dir) dir = unit(r.dir);
  } catch {
    // 보는 쪽
  }
  const reach = Math.max(1.5, rayDist(dim, hand, dir, k.range) - 0.3); // 창끝이 닿는 거리 (벽 앞)
  const c0 = add(hand, dir, 0.9); // 판정의 창 가운데 (창끝 = 가운데 + half)
  const travel = Math.max(0, reach - 0.9 - k.half);
  const shown = travel >= 1.5; // 창 그림을 그릴 만큼 날아가는지
  const hover = shown ? k.hover : 0;
  const outT = Math.max(1, Math.ceil(travel / k.speed));
  const cEnd = add(c0, dir, travel);
  const now = system.currentTick;
  // 던짐: 손 앞의 작은 번쩍임 (1.6칸 앞) + 날아가는 창 (그림은 손 앞 2.4칸부터 — 창 뒤끝이 눈을 스치지 않게) + 창끝의 빛 (멀어지면 창이 끝만 보여서) + 지나간 자리의 빛 · 번개 줄기
  burst(dim, add(hand, dir, 1.6), glow, 0.5, 0.18);
  if (shown) {
    const vc0 = add(hand, dir, 2.4);
    const vTravel = Math.max(0, len(sub(cEnd, vc0)));
    const vOut = vTravel / (outT / 20);
    spearFx(dim, vc0, dir, k.half, outT / 20, { move: dir, speed: vOut });
    fx(dim, "rpg:arrow_spark", add(vc0, dir, k.half * 0.8), { dir, speed: vOut, size: 0.75, grow: 1, life: outT / 20 + 0.05, color: GN_BOLT, alpha: 0.75 });
    fx(dim, "rpg:arrow_spark", add(vc0, dir, k.half * 0.8), { dir, speed: vOut, size: 0.35, grow: 1, life: outT / 20 + 0.05, color: GN_WHITE, alpha: 1 });
    const a = add(hand, dir, 1.6);
    beam(dim, a, cEnd, glow, { half: 0.5, thick: 0.28, life: 0.4, sweep: outT / 20, gap: 0.8, alpha: 0.85 });
    beam(dim, a, cEnd, GN_WHITE, { half: 0.45, thick: 0.08, life: 0.3, sweep: outT / 20, gap: 1.6, alpha: 1 });
    const n = Math.max(1, Math.round(travel / 5));
    for (let i = 0; i < n; i++) {
      const s0 = add(a, dir, (len(sub(cEnd, a)) * i) / n);
      const s1 = add(a, dir, (len(sub(cEnd, a)) * (i + 1)) / n);
      bolt(dim, s0, s1, GN_BOLT, { thick: 0.1, life: 0.25, jag: 0.35, delay: ((i + 1) / n) * (outT / 20) });
    }
  } else {
    // 벽이 가까움: 창끝이 닿는 자리에 번개만
    const tip = add(hand, dir, reach);
    burst(dim, tip, GN_WHITE, 0.9, 0.22);
    sparks(dim, tip, GN_BOLT, 8, 5);
    bolt(dim, up(tip, 6), tip, GN_BOLT, { thick: 0.2, life: 0.25, jag: 0.5 });
  }
  play(dim, "item.trident.throw", hand, 0.8, 1.0);
  play(dim, "item.trident.riptide_1", hand, 1.2, 0.6);
  // 갈 때: 창끝이 지나가는 순간 꿰뚫음
  for (const { e, t } of lineHits(player, hand, dir, reach, k.width)) {
    const at = now + Math.max(0, Math.ceil((t - 0.9 - k.half) / k.speed));
    hitAt(player, e, reserveHit(player, e, at), ok, () => {
      if (skillHit(player, e, w, k.mult) > 0) {
        spearHitFx(dim, e, eye0, dir, glow, core);
        play(dim, "item.trident.hit", bodyAt0(e, eye0), 1.1, 0.7);
      }
    });
  }
  // 끝: hover 틱 동안 돎 (보는 사람을 향한 창이 화면에서 한 바퀴 — 가까우면 작게) + 하늘에서 번개가 창에 떨어져 모임
  if (hover > 0) {
    system.runTimeout(() => {
      if (!ok()) return;
      const dEye = len(sub(cEnd, player.getHeadLocation()));
      spinFx(dim, cEnd, k.half * Math.min(1, Math.max(0.35, (dEye - 1.5) / 4.5)), hover / 20 + 0.02, Math.random() * 360, 360 / (hover / 20));
      shock(dim, up(cEnd, -0.3), GN_BOLT, 1.2, 0.35);
      play(dim, "item.trident.thunder", cEnd, 1.6, 0.5);
    }, outT);
    for (let h = 0; h < hover; h++) {
      system.runTimeout(() => {
        if (!ok()) return;
        const side = rightOf(flat(dir));
        const a = Math.random() * Math.PI * 2;
        const r = 1.4 + Math.random() * 1.0;
        bolt(dim, cEnd, add(up(cEnd, Math.sin(a) * r), side, Math.cos(a) * r), GN_BOLT, { thick: 0.09, life: 0.12, jag: 0.3 });
        if (h === 1) {
          bolt(dim, up(cEnd, 12), cEnd, GN_BOLT, { thick: 0.32, life: 0.3, jag: 0.7 });
          bolt(dim, up(cEnd, 12), cEnd, GN_WHITE, { thick: 0.11, life: 0.24, jag: 0.7 });
          burst(dim, cEnd, GN_WHITE, 1.5, 0.25);
          sparks(dim, cEnd, GN_BOLT, 8, 6);
          play(dim, "item.trident.thunder", cEnd, 1.2, 0.9);
        }
      }, outT + h);
    }
  }
  // 돌아옴: 3틱마다 지금 손을 다시 겨눠 그 3틱은 곧게 (그림과 판정이 같은 길). 지나는 적을 벰 (적마다 한 번), 끌 수 있는 몹은 창끝에 꿰어 끌어옴
  const backHit = new Set();
  /** @type {{e: any, lift: number, free?: boolean}[]} */
  const hooked = [];
  const st = { c: cEnd, u: dir, per: k.speed, left: 0, last: false };
  const finish = (handNow) => {
    // 손에 돌아옴: 손 앞의 작은 번쩍임 · 불꽃 (1인칭 화면 가운데를 덮지 않게 — 오른손 쪽 아래, 1.1칸 앞)
    const f = facingOf(player);
    burst(dim, add(up(handNow, -0.1), f, 1.1), GN_WHITE, 0.35, 0.16);
    sparks(dim, add(handNow, f, 1.1), GN_BOLT, 5, 3);
    play(dim, "item.trident.return", handNow, 1.3, 1.0);
    if (hooked.length) play(dim, "item.trident.thunder", handNow, 1.8, 0.5);
  };
  const step = (i) => {
    if (!ok()) return;
    if (st.left <= 0) {
      const handNow = handOf();
      const v = sub(handNow, st.c);
      const dist = len(v);
      if (dist > k.range + 6) {
        // 손이 멀리 순간이동함: 창은 바로 손에 돌아옴 (꿰인 몹은 그 자리에 남음)
        finish(handNow);
        return;
      }
      st.u = dist > 1e-3 ? unit(v) : dir;
      const segT = Math.max(1, Math.min(3, Math.ceil(dist / k.speed)));
      st.per = dist / Math.max(segT, Math.ceil(dist / k.speed));
      st.left = segT;
      st.last = segT * st.per >= dist - 1e-3;
      // 그림: 이 3틱 동안 곧게 (창끝은 바깥 = 움직이는 쪽과 반대) + 가운데의 빛. 손에 거의 왔으면 (2.6칸 안) 그리지 않음 — 창이 눈을 지나가지 않게
      if (shown && dist > 2.6) {
        const segLen = Math.min(st.per * segT, dist - 2.6); // 손 2.6칸 앞에서 사라짐
        spearFx(dim, st.c, { x: -st.u.x, y: -st.u.y, z: -st.u.z }, k.half, segLen / (st.per * 20) + 0.001, { move: st.u, speed: st.per * 20 });
        fx(dim, "rpg:arrow_spark", st.c, { dir: st.u, speed: st.per * 20, size: 0.7, grow: 1, life: segLen / (st.per * 20) + 0.03, color: GN_BOLT, alpha: 0.7 });
      }
      if (i === 0) play(dim, "item.trident.return", st.c, 0.9, 0.9);
    }
    const u = st.u;
    const next = add(st.c, u, st.per);
    const eyeNow = player.getHeadLocation();
    if (shown) beamAway(dim, eyeNow, st.c, next, glow, { half: 0.55, thick: 0.14, life: 0.22, gap: 0.8, alpha: 0.75 });
    // 지나는 적 (첫 틱은 창끝부터 — 돌아선 자리에서 가운데와 창끝 사이에 있던 적도. 창끝은 벽 앞 (reach) 을 넘지 않음: 벽이 가까워 창이 날지 않았을 때 벽 면에서 재지 않게)
    const tipBack = i === 0 ? Math.min(k.half, Math.max(0, reach - 0.9)) : 0;
    const from = add(st.c, u, -tipBack);
    for (const { e } of lineHits(player, from, u, st.per + tipBack + 0.4, k.width)) {
      if (backHit.has(e.id)) continue;
      backHit.add(e.id);
      if (hooked.filter((h) => !h.free).length < k.hook && draggable(e)) {
        let lift = 1.0;
        try {
          lift = bodyPoint(e).y - e.location.y;
        } catch {
          // 무시
        }
        hooked.push({ e, lift });
      }
      hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
        if (skillHit(player, e, w, k.backMult) > 0) {
          spearHitFx(dim, e, player.getHeadLocation(), u, glow, core);
          play(dim, "item.trident.hit", bodyAt0(e, next), 0.9, 0.8);
        }
      });
    }
    st.c = next;
    st.left--;
    // 꿰인 몹: 창끝 쪽에 줄지어 끌려옴 + 번개 사슬. 나에게서 멀어지는 쪽으로는 옮기지 않음 (창이 따라잡을 때까지),
    // 끌려갈 자리가 벽 너머 · 막힘이면 · 쓰러지면 빠짐. 발은 그 아래 땅 위 (땅이 가까우면)
    const tipDir = { x: -u.x, y: -u.y, z: -u.z };
    const handNow = handOf();
    let slot = 0; // 줄 자리: 빠진 몹의 자리는 비우고 앞으로 당김
    hooked.forEach((hk) => {
      if (hk.free) return;
      if (!hk.e.isValid || hasNoHealth(hk.e)) {
        hk.free = true;
        return;
      }
      const body = add(next, tipDir, k.half * 0.55 + 1.0 + 0.75 * slot); // 손에 돌아왔을 때 첫 몹이 약 2.2칸 앞
      const now0 = bodyAt0(hk.e, body);
      if (i % 2 === 0) bolt(dim, add(next, tipDir, k.half * 0.4), now0, GN_BOLT, { thick: 0.08, life: 0.1, jag: 0.25 });
      if (len(sub(body, handNow)) >= len(sub(now0, handNow)) - 0.05) {
        slot++; // 아직 창이 따라잡지 못함 (자리는 그대로)
        return;
      }
      if (!clearPath(dim, now0, body)) {
        hk.free = true;
        return;
      }
      let feet = body.y - hk.lift;
      const gy = solidBelow(dim, up(body, 0.3), hk.lift + 2.5);
      if (gy !== undefined && gy > feet) feet = gy;
      if (!moveTo(hk.e, { x: body.x, y: feet, z: body.z })) {
        hk.free = true; // 옮길 수 없으면 (몸이 블록과 겹침) 거기서 빠짐 — 자리를 비움
        return;
      }
      slot++;
    });
    if (st.left <= 0 && st.last) {
      finish(handNow);
      return;
    }
    if (i < 40) system.runTimeout(() => step(i + 1), 1);
  };
  system.runTimeout(() => step(0), outT + hover);
  return true;
}

/**
 * 신창 강림이 떨어질 땅: 조준한 몹의 발밑 → 시선이 닿은 곳 (사거리 끝) 에서 가로로 0.4칸 앞 (벽 면에 붙지 않게) 의 아래 땅 (물 · 용암 위도 됨).
 * 가로 min 칸보다 가까우면 min 칸 앞 — 그 자리가 눈에서 보이지 않으면 (벽 너머) 밀지 않음. 없으면 undefined
 */
function fallSpot(player, k) {
  const dim = player.dimension;
  const p = player.location;
  const eye = player.getHeadLocation();
  let q;
  try {
    const r = aimTarget(player, k.range, BOW.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) q = { ...r.target.location };
    else {
      const d = r.dir ?? player.getViewDirection();
      q = add(eye, d, r.dist ?? k.range);
      if (Math.hypot(d.x, d.z) > 0.2) q = add(q, flat(d), -0.4);
    }
  } catch {
    q = add(eye, player.getViewDirection(), k.range);
  }
  const f = facingOf(player);
  const dx = q.x - p.x;
  const dz = q.z - p.z;
  const h = Math.hypot(dx, dz);
  if (h < k.min) {
    const pushed = { x: p.x + (h > 0.5 ? dx / h : f.x) * k.min, y: q.y, z: p.z + (h > 0.5 ? dz / h : f.z) * k.min };
    if (lineOfSight(player, eye, { x: pushed.x, y: Math.max(q.y, p.y) + 1.0, z: pushed.z })) q = pushed;
  }
  try {
    const hit = dim.getBlockFromRay({ x: q.x, y: q.y + 1.5, z: q.z }, V_DOWN, { maxDistance: 24, includePassableBlocks: false, includeLiquidBlocks: true });
    if (!hit) return undefined;
    const fy = hit.faceLocation?.y;
    return { x: q.x, y: hit.block.location.y + (typeof fy === "number" && fy > 0.05 && fy <= 1 ? fy : 1), z: q.z };
  } catch {
    return undefined;
  }
}

/** at 위로 트인 높이 (최대 max 칸) — throughLeaves 면 나뭇잎은 지나침 (나무 밑에서 거대한 창이 작아지지 않게 — v2.21.0 만월의 사냥은 잎도 막힘: 활이 잎 속에 그려지지 않게) */
function skyRoom(dim, at, max, throughLeaves = true) {
  let y = at.y;
  for (let i = 0; i < 5; i++) {
    const left = max - (y - at.y);
    if (left <= 0) return max;
    try {
      const hit = dim.getBlockFromRay({ x: at.x, y, z: at.z }, V_UP, { maxDistance: left, includePassableBlocks: false, includeLiquidBlocks: false });
      if (!hit) return max;
      const top = hit.block.location.y + (hit.faceLocation?.y ?? 0);
      if (!throughLeaves || !/leaves/.test(hit.block.typeId ?? "")) return Math.max(0, top - at.y);
      y = hit.block.location.y + 1.01;
    } catch {
      return max;
    }
  }
  return Math.max(0, y - at.y);
}

/**
 * 신창 강림 (v2.18.0): 창을 하늘로 던짐 → 조준한 곳 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞 · 그 아래 땅) 에 delay 틱 동안 번개 룬 · 떨어지는 그림자 · 먹구름 →
 * 거대한 궁니르 (모델 giant 배 — 굴 · 지붕 아래면 트인 높이에 맞게 줄임) 가 하늘에서 내리꽂힘: 반지름 radius 칸 번개 폭발 (mult, 띄움) → 꽂힌 창에서 pulseGap 틱마다 pulses 번 번개가 튐
 * (pulseMult — 반지름 radius + 1 칸, 띄운 적은 밀려났어도 radius + 2.5 칸 안이면) → stay 틱 뒤 빛으로 흩어짐. 준 피해의 heal 만큼 회복 (도끼 규칙 딜탱 — 한 번 쓸 때 실제로
 * 회복한 양이 최대 체력의 healCap 까지). 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절. 거대한 창은 조각 넷으로 그림 (가운데가 화면 밖이어도 창날이 보이게)
 */
function gungnirFall(player, w, k) {
  const dim = player.dimension;
  const spot = fallSpot(player, k);
  if (!spot) {
    warn(player, `§c${k.name}: §7땅을 조준해야 합니다 (${k.range}칸 안)`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const glow = colors(w)[0];
  const sink = 0.1; // 창끝이 땅에 박힌 몫
  // 하늘이 트인 높이 (굴 · 지붕 아래면 거기에 맞게 창을 줄이고 덜 떨어뜨림 — 그림이 천장을 뚫지 않게)
  const room = skyRoom(dim, up(spot, 0.2), k.length * k.giant + k.drop + 2) + 0.2;
  const L = Math.min(k.length * k.giant, Math.max(1.5, room / (1 - sink))); // 거대한 창 길이
  const half = L / 2;
  const drop = Math.max(0, Math.min(k.drop, room - L * (1 - sink)));
  const cloud = Math.max(1.2, Math.min(k.cloud, room - 0.8));
  const center = up(spot, half - L * sink);
  const head = up(spot, Math.min(L * 0.18, 2.2)); // 땅 위로 드러난 창날쯤 (번개가 튀는 곳)
  const f = facingOf(player);
  const hand = add(up(player.getHeadLocation(), -0.2), rightOf(f), 0.3);
  // 던짐: 눈앞 1.2칸에서 하늘로 솟는 창 (눈을 스치지 않게) + 천둥
  const rise = add(hand, f, 1.2);
  spearFx(dim, up(rise, 2.6), V_UP, k.half, 0.5, { move: V_UP, speed: 64 });
  beam(dim, up(rise, 1.8), up(rise, 14), glow, { half: 0.5, thick: 0.2, life: 0.35, sweep: 0.2, gap: 0.8 });
  play(dim, "item.trident.throw", hand, 0.6, 1.0);
  play(dim, "item.trident.riptide_3", player.location, 0.7, 0.6);
  play(dim, "ambient.weather.thunder", spot, 1.5, 0.35);
  // 예고: 번개 룬 (그려짐) · 커지는 그림자 · 먹구름 · 솟는 불꽃
  const fallT = Math.min(k.fall, k.delay - 1);
  for (let t = 0; t < k.delay; t += 2) {
    system.runTimeout(() => {
      if (!ok()) return;
      const g = (t + 2) / k.delay;
      rune(dim, spot, { size: k.radius * 0.95, life: 0.13, progress: Math.min(1, g * 1.2), spin: t * 9, color: GN_BOLT, alpha: 0.9 });
      fx(dim, "rpg:tele_disc", up(spot, 0.05), { size: k.radius * (0.25 + 0.6 * g), life: 0.13, color: GN_DARK, alpha: 0.55 + 0.3 * g });
      if (t % 4 === 0) {
        rune(dim, up(spot, cloud), { size: k.radius * 1.15, life: 0.25, spin: -t * 6, color: GN_CLOUD, alpha: 0.85, rate: 30 });
        const a = Math.random() * Math.PI * 2;
        const rr = k.radius * (0.5 + Math.random() * 0.45);
        fx(dim, "rpg:arrow_spark", up({ x: spot.x + Math.cos(a) * rr, y: spot.y, z: spot.z + Math.sin(a) * rr }, 0.2), { dir: V_UP, speed: 3, size: 0.12, life: 0.4, color: GN_BOLT, alpha: 1 });
      }
      if (t === 8 && cloud > 3) bolt(dim, up(spot, cloud), up(spot, Math.max(1, cloud - 6)), GN_BOLT, { thick: 0.16, life: 0.2, jag: 0.8 });
    }, t);
  }
  // 낙하: 하늘에서 거대한 창이 fallT 틱 동안 곧게 떨어짐 (뒤에 빛줄기)
  system.runTimeout(() => {
    if (!ok()) return;
    spearParts(dim, up(center, drop), V_DOWN, half, fallT / 20, { move: V_DOWN, speed: drop / (fallT / 20) });
    if (drop > 2) beam(dim, up(spot, drop + L * 0.9), up(spot, L * 0.8), GN_BOLT, { half: 1.2, thick: 1.4, life: 0.3, sweep: fallT / 20, gap: 2.0, alpha: 0.55 });
    play(dim, "item.trident.riptide_3", spot, 0.5, 1.0);
  }, k.delay - fallT);
  /** 띄운 적 (밀려나도 번개가 따라감) @type {Map<string, any>} */
  const struck = new Map();
  // 회복: 준 피해의 heal 만큼 — 모아서 다음 틱에 한 번 (숫자가 적마다 뜨지 않게), 한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지
  const heal = { pool: 0, pending: false, left: maxHpOf(player) * (k.healCap ?? 1) };
  const addHeal = (got) => {
    heal.pool += got;
    if (heal.pending) return;
    heal.pending = true;
    system.runTimeout(() => {
      heal.pending = false;
      if (heal.pool <= 0 || heal.left <= 0 || !ok()) return;
      const want = Math.min(heal.left, heal.pool * k.heal);
      heal.pool = 0;
      const healed = healBy(player, want);
      heal.left -= Math.max(0, healed);
      if (healed > 0) showDamageText(player, healed, "heal");
    }, 1);
  };
  const fell = { landed: false };
  // 꽂힘
  system.runTimeout(() => {
    if (!ok()) return;
    fell.landed = true;
    spearParts(dim, center, V_DOWN, half, k.stay / 20);
    // 먹구름에서 창으로 번개 (창대 가운데쯤 — 먹구름보다 창이 길면 창대에)
    const strike = Math.min(cloud - 0.5, L * (1 - sink) * 0.55);
    if (cloud > 2.5) {
      const a = Math.random() * Math.PI * 2;
      const top = { x: spot.x + Math.cos(a) * 1.5, y: spot.y + cloud, z: spot.z + Math.sin(a) * 1.5 };
      bolt(dim, top, up(spot, strike), GN_BOLT, { thick: 0.5, life: 0.4, jag: 1.0 });
      bolt(dim, top, up(spot, strike), GN_WHITE, { thick: 0.18, life: 0.32, jag: 1.0 });
    }
    burst(dim, up(spot, 0.8), GN_WHITE, 4.5, 0.4);
    burst(dim, up(spot, 2.2), glow, 3.2, 0.35);
    shock(dim, spot, GN_BOLT, k.radius / 2.1, 0.55);
    shock(dim, spot, GN_WHITE, k.radius / 4.2, 0.4);
    rune(dim, spot, { size: k.radius * 0.95, life: 0.8, color: GN_BOLT, alpha: 0.9, grow: 0.12, rate: -80 });
    fx(dim, "rpg:ground_crack", up(spot, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: k.radius * 1.5, life: 1.6, color: GN_DEEP, alpha: 0.85 });
    for (let j = 0; j < 8; j++) {
      const a = (j / 8) * Math.PI * 2 + Math.random() * 0.4;
      const end = { x: spot.x + Math.cos(a) * k.radius, y: spot.y + 0.15, z: spot.z + Math.sin(a) * k.radius };
      bolt(dim, up(spot, 0.3), end, GN_BOLT, { thick: 0.16, life: 0.3, jag: 0.6 });
    }
    sparks(dim, up(spot, 1.0), GN_WHITE, 14, 8);
    play(dim, "item.trident.thunder", spot, 0.7, 1.3);
    play(dim, "ambient.weather.thunder", spot, 1.0, 1.0);
    play(dim, "random.explode", spot, 0.8, 0.8);
    play(dim, "mace.heavy_smash_ground", spot, 0.9, 0.9);
    shakeNear(dim, spot, 14, 0.4, 0.4);
    const t0 = system.currentTick;
    const eye = up(spot, 1.2);
    for (const e of visibleFrom(player, eye, around(player, spot, k.radius))) {
      hitAt(player, e, reserveHit(player, e, t0), ok, () => {
        const got = skillHit(player, e, w, k.mult);
        if (got <= 0) return;
        struck.set(e.id, e);
        addHeal(got);
        burst(dim, bodyAt0(e, eye), GN_WHITE, 0.9, 0.25);
        const d = flat(sub(e.location, spot));
        try {
          e.applyKnockback({ x: d.x * 0.3, z: d.z * 0.3 }, 0.6);
        } catch {
          // 밀리지 않는 몹
        }
      });
    }
  }, k.delay);
  // 꽂힌 창에서 번개가 튐
  for (let i = 1; i <= k.pulses; i++) {
    system.runTimeout(() => {
      if (!ok()) return;
      const t0 = system.currentTick;
      const near = visibleFrom(player, up(head, 0), around(player, spot, k.radius + 1));
      const pushed = [...struck.values()].filter((e) => e.isValid && Math.hypot(e.location.x - spot.x, e.location.z - spot.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - spot.y) <= 5);
      const list = unionById(near, pushed);
      list.slice(0, 8).forEach((e) => bolt(dim, head, bodyAt0(e, head), GN_BOLT, { thick: 0.14, life: 0.22, jag: 0.5 }));
      if (!list.length) for (let j = 0; j < 3; j++) bolt(dim, head, up(add(spot, turnY({ x: 1, y: 0, z: 0 }, Math.random() * 360), 2 + Math.random() * 3), 0.1), GN_BOLT, { thick: 0.12, life: 0.2, jag: 0.5 });
      burst(dim, head, GN_WHITE, 1.6, 0.25);
      shock(dim, spot, GN_BOLT, 1.4, 0.35);
      play(dim, "ambient.weather.lightning.impact", spot, 1.2 + 0.2 * i, 0.7);
      for (const e of list) {
        hitAt(player, e, reserveHit(player, e, t0), ok, () => {
          const got = skillHit(player, e, w, k.pulseMult, { cause: EntityDamageCause.magic }); // 번개 = 마법 피해 (방어구 무시 — 예전 궁니르 번개와 같은 규칙)
          if (got <= 0) return;
          addHeal(got);
          burst(dim, bodyAt0(e, head), GN_BOLT, 0.7, 0.2);
        });
      }
    }, k.delay + k.pulseGap * i);
  }
  // 빛으로 흩어짐 (꽂힌 창만 — 떨어지기 전에 끊겼으면 없음. 꽂힌 뒤에는 쓴 사람이 쓰러져도 창은 흩어짐)
  system.runTimeout(() => {
    if (!fell.landed) return;
    spearParts(dim, center, V_DOWN, half, k.fade / 20, { progress: 2 });
    for (let j = 0; j < 6; j++) fx(dim, "rpg:arrow_spark", up(spot, 1 + Math.random() * L * 0.6), { dir: V_UP, speed: 2 + Math.random() * 2, size: 0.18, life: 0.6, color: GN_WHITE, alpha: 1 });
    play(dim, "beacon.deactivate", spot, 1.6, 0.6);
  }, k.delay + k.stay);
  return true;
}

// ------------------------------------------------------------ 아이기스의 방패 (탱커) — v2.20.0 개편 (고르곤 · 신의 방벽)
// 사용자가 고른 컨셉 "고르곤 돌진 / 신의 방벽" · 세기 "영웅 룬~초월 룬" (Lv.40 체력 125 의 체력 룬 위력 기준) · 빛의 방패 그림은 모델 앞모습 (rpg:aegis_shield)
const AE_EYE = [0.55, 1.0, 0.9]; // 메두사의 눈 (청록빛)
const AE_WHITE = [1.0, 0.97, 0.84];
const AE_STONE = [0.74, 0.73, 0.7]; // 석화 돌 조각 (그림이 회색 — 거의 그대로)
const AE_DUST = [0.62, 0.6, 0.56];
/** 빛의 방패 그림에서 메두사의 두 눈 (그림 가운데 기준, 반 크기 = 1 — build.py aegis_eyes: 256점 그림의 (116, 116) · (140, 116)) */
const AE_EYES = [
  { x: -12 / 128, y: 12 / 128 },
  { x: 12 / 128, y: 12 / 128 },
];
const AE_SHIELD = 0.8; // 고르곤 돌진의 빛의 방패 반 크기 (칸) — 지름 1.6칸
const AE_SHIELD_Y = 0.72; // 빛의 방패 가운데 높이 (발에서) — 위끝 1.52칸: 눈 (1.62) 보다 조금 아래 (1인칭에서 앞을 가리지 않게)
const RING_GROW = 2.6; // rpg:shock_ring 이 수명 동안 커지는 배수 (build.py 의 grow 1.6: 크기 × (1 + 1.6 × 진행) — 그림의 고리는 반 크기의 약 0.8)

/** 도발 (v2.20.0): 몬스터 (monsters.js) · 보스 (bosses.js) 가 넣음 — (플레이어, 적, 틱) → 걸렸는지 @type {((player: any, e: any, ticks: number) => boolean)[]} */
const tauntHooks = [];
export function addTauntHook(fn) {
  tauntHooks.push(fn);
}

/** 그 적이 ticks 동안 이 플레이어를 노리게 함 (몬스터 · 보스는 스크립트가 — 바닐라 몹은 이 플레이어가 준 피해로 엔진이 이미 노림). 스크립트가 걸었으면 true */
function taunt(player, e, ticks) {
  for (const h of tauntHooks) {
    try {
      if (h(player, e, ticks)) return true;
    } catch (err) {
      console.warn(`[rpg] taunt: ${err}`);
    }
  }
  return false;
}

/** 보스 · 보스 소품 (석화 · 둔화 · 밀기가 듣지 않음 — 보스 기믹을 깨지 않게) */
function bossLike(e) {
  return hasFamily(e, ["rpg_boss", "rpg_boss_prop"]);
}

/** 도발 표시 (머리 위 "도발") — 몬스터 · 보스 (스크립트가 건 도발) 와 바닐라 적대 몹 (맞으면 엔진이 노림) */
function tauntMark(dim, e, scripted) {
  if (!scripted && !hasFamily(e, ["monster"])) return;
  try {
    showTextAt(dim, up(e.getHeadLocation(), 1.25), "§6§l도발"); // 피해 숫자 (머리 위 0.3칸 근처) 보다 위
  } catch {
    // 무시
  }
}

/** 석화: ticks 동안 움직이지 못하고 (구속 XI) 근접 공격이 거의 들어가지 않음 (나약함 V) — 룬 기절과 같음 (이 애드온 몬스터는 공격하지 못함) */
function petrify(e, ticks) {
  try {
    e.addEffect("slowness", ticks, { amplifier: 10, showParticles: false });
    e.addEffect("weakness", ticks, { amplifier: 4, showParticles: false });
  } catch {
    // 효과가 없는 몹
  }
}

/**
 * 빛의 방패 한 장 (rpg:aegis_shield — 보는 사람을 향해 섬): at = 방패 가운데, size = 반 크기 (칸), life = 초.
 * o.progress = -1 빛이 지나감 되풀이 (기본 — o.phase = 시작 장, 틱마다 다시 그려도 이어짐) · 0 수명 동안 메두사의 눈이 번쩍 · 2 수명 동안 흩어짐
 */
function shieldFx(dim, at, size, life, o = {}) {
  fx(dim, "rpg:aegis_shield", at, { dir: V_UP, speed: 0, size, life, spin: o.phase ?? 0, progress: o.progress ?? -1, color: [1, 1, 1], alpha: o.alpha ?? 1 });
}

/** 육각 칸 하나 (rpg:aegis_hex — 보는 사람을 향함): o.fold 초부터 0.4초 동안 o.dir (단위) 쪽으로 o.reach 칸 접혀 들어감, o.frame 0 보통 · 1 번쩍 */
function hexFx(dim, at, o) {
  try {
    const m = vars({ dir: o.dir ?? V_UP, size: o.size, life: o.life, color: o.color ?? AE_WHITE, alpha: o.alpha ?? 0.55 });
    m.setFloat("variable.fold", o.fold ?? 99);
    m.setFloat("variable.reach", o.reach ?? 0);
    m.setFloat("variable.frame", o.frame ?? 0);
    dim.spawnParticle("rpg:aegis_hex", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 돌 조각 하나 (rpg:aegis_stone): o.kind 0~3, o.dir · o.speed (칸/초), 아래로 o.grav (칸/초²), o.spin · o.rate (도 · 도/초) */
function stoneFx(dim, at, o) {
  try {
    const m = vars({ dir: o.dir ?? V_UP, speed: o.speed ?? 0, size: o.size, life: o.life, spin: o.spin ?? 0, color: o.color ?? AE_STONE, alpha: o.alpha ?? 1 });
    m.setFloat("variable.grav", o.grav ?? 0);
    m.setFloat("variable.rate", o.rate ?? 0);
    m.setFloat("variable.kind", o.kind ?? 0);
    dim.spawnParticle("rpg:aegis_stone", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 돌 껍질: 몸 둘레에 돌 조각 n 개가 붙음 — part 0 아래 · 1 가운데 · 2 위 (세 틱에 나눠 그려 발부터 굳어 감), life 초 동안 그대로 */
function stoneCrust(dim, e, part, life, n = 6) {
  let l;
  let hgt;
  try {
    l = e.location;
    hgt = Math.max(0.5, e.getHeadLocation().y - l.y + 0.3);
  } catch {
    return;
  }
  const r = 0.34 + bodyPad(e);
  for (let j = 0; j < n; j++) {
    const a = ((j + part * 0.37) / n) * Math.PI * 2 + part;
    const rr = r * (0.8 + (0.25 * ((j * 7 + part * 3) % 5)) / 4);
    const y = l.y + (hgt * (part + (j + 0.5) / n)) / 3;
    stoneFx(dim, { x: l.x + Math.cos(a) * rr, y, z: l.z + Math.sin(a) * rr }, { size: 0.15 + 0.03 * ((j + part) % 3), life, kind: (j + part) % 4, spin: (j * 97 + part * 41) % 360, alpha: 0.95 });
  }
}

/** 돌이 깨짐: 몸 가운데 c 에서 돌 조각이 튀어 떨어지고 발밑 feet 에 먼지. 조각은 보는 사람 (eye) 쪽으로는 튀지 않음 (1인칭 화면을 가리지 않게) */
function stoneBreak(dim, c, feet, pad, eye) {
  const toEye = flat(sub(eye, c));
  for (let j = 0; j < 7; j++) {
    const a = (j / 7) * Math.PI * 2 + 0.4;
    let h = { x: Math.cos(a), z: Math.sin(a) };
    if (h.x * toEye.x + h.z * toEye.z > 0.2) h = { x: -h.x, z: -h.z };
    const d = unit({ x: h.x, y: 0.55 + 0.1 * (j % 3), z: h.z });
    stoneFx(dim, add(c, d, 0.25), { dir: d, speed: 3.2 + 0.4 * (j % 3), grav: 16, size: 0.16 + 0.04 * (j % 3), life: 0.7, kind: j % 4, spin: j * 51, rate: j % 2 ? 420 : -420 });
  }
  fx(dim, "rpg:dust", up(feet, 0.5), { size: 1.3 + pad, life: 0.6, spin: 30, color: AE_DUST, alpha: 0.8 });
}

/**
 * 고르곤 돌진 (v2.20.0): 아이기스 모양 금빛 빛의 방패 (몸 앞 front 칸) 를 앞세워 보는 쪽 (가로) 으로 dash 칸 돌진 — 틱마다 speed 칸, 땅을 따라 (한 칸 오르내림),
 * 벽 · 낭떠러지 · 물 · 용암 앞에서 멈춤. 방패에 닿은 적 (길에서 옆으로 width 칸, 몸 반폭 여유 — 방패에서 보이는 적) 은 부딪힘 (mult):
 * 끌 수 있는 몹은 carry 마리까지 방패 앞에 붙여 밀고 감 (더 많으면 옆으로 밀쳐냄), 끌 수 없는 몹 (보스 · 큰 몹 · 허수아비) 이면 그 몸 앞에서 멈춤.
 * 밀던 적이 길 끝 (벽 · 낭떠러지) 에 닿으면 거기서 멈춤 (몰아붙임). 끝에서 방패의 메두사 눈이 번쩍 — 밀던 적과 방패 앞 gaze 칸 · cone 도 (방패에서 보이는) 의 적이
 * stone 틱 동안 돌이 됨 (석화 — 보스 · 소품은 걸리지 않음, 돌 껍질만 잠깐) → 돌이 깨지며 afterMult + 도발 (taunt 틱).
 * 속박 · 무엇이든 탄 동안은 돌진 없이 그 자리에서 메두사의 시선. 돌진 중 침묵 · 속박 · 다른 것이 나를 옮김 (끌어당김 등) 이면 그 자리에서 멈춤.
 * 쓰러지거나 차원을 옮기면 남은 것은 없음. 적이 없어도 돌진 (재사용 대기)
 */
function aegisCharge(player, w, k) {
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const start = { ...player.location };
  const f = facingOf(player);
  const side = rightOf(f);
  const [glow, core] = colors(w);
  // 길은 땅 높이에서: 뛰는 중에 써도 발밑 땅 (3칸 안) 기준 (화룡 돌진과 같음)
  const g = solidBelow(dim, up(start, 0.3), 3.3);
  const from = { x: start.x, y: g === undefined ? start.y : Math.min(start.y, g), z: start.z };
  const held = () => isRooted(player) || isSilenced(player) || ridingAny(player);
  // 길: 돌진 거리 + 밀고 가는 적이 설 자리 (방패 앞 약 1.25칸 + 몸 · 줄) 까지 — 땅이 이어지는 데까지 (벽 · 낭떠러지 · 물 · 용암 앞에서 끊김)
  const reach = held() ? 0 : reachFlat(dim, from, f, k.dash + 2.5);
  const spots = reach > 0.2 ? dashSpots(dim, from, f, reach) : [];
  const Lm = spots.length ? spots[spots.length - 1].s : 0; // 밀고 가는 적이 갈 수 있는 데까지 (벽이면 그 앞 0.6칸)
  // 내가 갈 수 있는 거리: 돌진 거리 · 땅이 이어지는 데까지 — 벽이면 방패 (몸 앞 front 칸) 가 벽에 들어가지 않게 (방패 가운데가 벽 앞 0.1칸)
  const wall = reach < k.dash + 2.5 - 1e-6;
  const L = Math.max(0, Math.min(k.dash, Lm, wall ? reach - (k.front - 0.5) : Infinity));
  const yAt = (s) => {
    let y = from.y;
    for (const x of spots) {
      if (x.s > s + 0.25) break;
      y = x.p.y;
    }
    return y;
  };
  const onPath = (s) => {
    const q = add(from, f, s);
    return { x: q.x, y: yAt(s), z: q.z };
  };
  /** 방패에 닿은 적 (한 번씩) */
  const touched = new Set();
  /** 밀고 가는 적: lat = 옆 자리 (-0.65 · 0 · 0.65), row = 줄 (0 · 1 — 뒷줄은 0.75칸 앞), pad = 몸 반폭 여유, free = 놓침 @type {{e: any, lat: number, row: number, pad: number, free?: boolean}[]} */
  const carried = [];
  const SLOTS = [-0.65, 0, 0.65];
  const ROW = 0.75;
  /** 밀고 가는 적의 자리: 앞줄부터, 옆으로 가장 가까운 빈 자리 (몸이 겹치지 않게). 없으면 undefined */
  const slotFor = (lat) => {
    for (let row = 0; row < 2; row++) {
      let best;
      let bd = Infinity;
      for (const x of SLOTS) {
        if (carried.some((c) => !c.free && c.row === row && c.lat === x)) continue;
        if (Math.abs(x - lat) < bd) {
          bd = Math.abs(x - lat);
          best = x;
        }
      }
      if (best !== undefined) return { lat: best, row };
    }
    return undefined;
  };
  /** 밀고 가는 적의 몸 가운데까지 (방패 앞 0.35칸 + 몸 반폭 + 줄) */
  const ahead = (c) => k.front + 0.35 + c.pad + ROW * c.row;
  /** 밀고 가는 적 가운데 가장 앞의 몸 끝까지 (방패에서) — 끌 수 없는 몹 앞에서 멈출 때 */
  const depth = () => carried.reduce((m, c) => (c.free ? m : Math.max(m, 0.35 + 0.3 + 2 * c.pad + ROW * c.row + 0.05)), 0);
  /** s: 지금까지 간 거리, last: 이 스킬이 마지막으로 둔 자리, broken: 다른 것이 나를 옮김 */
  const st = { s: 0, last: { ...player.location }, broken: false };
  /** 다른 것이 나를 옮겼는지 (한 틱 사이 가로 2.5칸 · 세로 3칸 넘게 — 이 스킬이 둔 자리에서) */
  const displaced = () => {
    const l = player.location;
    if (Math.hypot(l.x - st.last.x, l.z - st.last.z) > 2.5 || Math.abs(l.y - st.last.y) > 3) st.broken = true;
    return st.broken;
  };
  /** 몸 앞으로 방패를 둘 수 있는 거리: front 칸까지 — 막는 블록 (벽 · 턱) 이 있으면 그 앞 0.3칸 (방패 · 메두사의 시선이 블록 안에서 시작하지 않게) */
  const roomAt = (base, h) => Math.max(0.3, Math.min(k.front, rayDist(dim, up(base, h), f, k.front + 0.3) - 0.3));
  const shieldAt = () => {
    const l = player.location;
    return add(up(l, AE_SHIELD_Y), f, roomAt(l, AE_SHIELD_Y));
  };
  /** 메두사의 시선이 나가는 곳: 가슴 높이 (1.2) 의 방패 자리 — 블록 안이 아님 */
  const gazeFrom = () => {
    const l = player.location;
    return add(up(l, 1.2), f, roomAt(l, 1.2));
  };
  /** 길 위 s 칸의 자리 (옆 lat 칸) 로 옮김 — 막히면 (몸이 턱 · 벽 모서리에 걸침) 0.3칸 뒤 · 앞도 해 봄 (lo ~ hi 안). 옮긴 거리 (못 옮기면 undefined) */
  const moveAlong = (e, s, lo, hi, lat = 0) => {
    for (const ds of [0, -0.3, 0.3]) {
      const s2 = s + ds;
      if (s2 < lo - 1e-9 || s2 > hi + 1e-9) continue;
      if (moveTo(e, add(onPath(s2), side, lat))) return s2;
    }
    return undefined;
  };
  /** 방패에 부딪힘: 금빛 번쩍임 · 불꽃 + 피해 (heavy = 끌 수 없는 몹 — 더 크게, 눈 가까이는 작게) */
  const bash = (e, heavy) => {
    const at0 = bodyPoint(e);
    hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
      if (skillHit(player, e, w, k.mult) <= 0) return;
      const at = bodyAt0(e, at0);
      const face = add(at, f, -(0.3 + bodyPad(e)));
      const nearEye = Math.min(1, Math.max(0.45, (len(sub(face, player.getHeadLocation())) - 0.8) / 2.5));
      burst(dim, face, heavy ? AE_WHITE : core, (heavy ? 1.6 : 0.9) * nearEye, 0.25);
      sparks(dim, face, glow, heavy ? 8 : 5, heavy ? 6 : 4);
      play(dim, "mob.irongolem.hit", at, heavy ? 0.6 : 0.9, 1.0);
      if (heavy) {
        play(dim, "random.anvil_land", at, 0.8, 0.6);
        shake(player, 0.25, 0.2);
      }
    });
  };
  /** 끝: 메두사의 눈이 번쩍 → 돌이 됨 → stone 틱 뒤 깨지며 피해 · 도발 */
  const finish = () => {
    const eye = player.getHeadLocation();
    const sa = shieldAt();
    const ga = gazeFrom();
    // 빛의 방패: 눈이 번쩍 (0.4초) → 그 자리에서 흩어짐 (0.3초). 두 눈의 빛살 + 앞쪽 부채꼴로 퍼지는 청록빛 시선 (눈 1.2칸 안은 빼고)
    shieldFx(dim, sa, AE_SHIELD, 0.4, { progress: 0 });
    system.runTimeout(() => {
      if (ok()) shieldFx(dim, sa, AE_SHIELD, 0.3, { progress: 2 });
    }, 8);
    for (const ey of AE_EYES) {
      const at = add(add(sa, side, ey.x * AE_SHIELD), V_UP, ey.y * AE_SHIELD);
      fx(dim, "rpg:arrow_spark", add(at, f, 0.05), { dir: f, size: 0.2, grow: 4, life: 0.3, color: AE_EYE, alpha: 1 });
    }
    for (let j = -2; j <= 2; j++) {
      const d = turnY(f, (j * k.cone) / 4);
      streakAway(dim, eye, add(sa, d, 0.4), add(up(sa, -0.3), d, k.gaze), AE_EYE, 0.2, 0.35, 0.8, 1.2);
    }
    play(dim, "mob.elderguardian.curse", sa, 1.4, 0.7);
    // 돌이 될 적: 밀던 적 + 시선 앞 gaze 칸 · cone 도 (가슴 높이의 방패 자리에서 보이는 — 위아래 2.5칸)
    const half = ((k.cone / 2) * Math.PI) / 180;
    const gazed = visibleFrom(player, ga, around(player, ga, k.gaze, 2.5)).filter((e) => bodyInCone(e, ga, f, half));
    const stoned = unionById(carried.filter((c) => !c.free && canHit(player, c.e)).map((c) => c.e), gazed);
    // 돌 조각: 적이 많으면 하나에 덜 (한 틱 파티클 60 안 — 셋까지는 한 틱에 여섯씩)
    const chips = Math.max(2, Math.min(6, Math.floor(45 / Math.max(1, stoned.length))));
    let n = 0;
    for (const e of stoned) {
      if (bossLike(e)) {
        stoneCrust(dim, e, 1, 0.25, chips); // 보스는 석화되지 않음: 돌 껍질이 잠깐 붙었다 떨어짐
        continue;
      }
      petrify(e, k.stone);
      for (let part = 0; part < 3; part++) {
        const crust = () => {
          if (ok() && e.isValid) stoneCrust(dim, e, part, (k.stone - part) / 20, chips);
        };
        if (part === 0) crust();
        else system.runTimeout(crust, part);
      }
      if (n++ < 3) play(dim, "dig.stone", bodyAt0(e, sa), 0.6, 1.0);
    }
    // 깨짐: 피해 + 돌 조각 · 먼지 + 도발 (다섯씩 한 틱 — 한 틱 파티클 60 안)
    system.runTimeout(() => {
      if (!ok()) return;
      let any = false;
      let j = 0;
      for (const e of stoned) {
        if (!canHit(player, e)) continue;
        any = true;
        const c0 = bodyAt0(e, sa);
        const feet = { ...e.location };
        const pad = bodyPad(e);
        hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j++ / 5)), ok, () => {
          if (skillHit(player, e, w, k.afterMult) <= 0) return;
          stoneBreak(dim, bodyAt0(e, c0), e.isValid ? { ...e.location } : feet, pad, player.getHeadLocation());
          tauntMark(dim, e, taunt(player, e, k.taunt));
        });
      }
      if (any) {
        play(dim, "mob.irongolem.crack", up(player.location, 1.2), 1.2, 1.0);
        play(dim, "random.anvil_land", up(player.location, 1.2), 1.7, 0.4);
        shake(player, 0.15, 0.2);
      }
    }, k.stone);
  };
  // 출발: 방패를 들어 올리는 소리 + 발밑 고리
  shock(dim, from, glow, 1.3, 0.35);
  play(dim, "item.shield.block", start, 0.8, 1.0);
  play(dim, "mob.breeze.charge", start, 1.2, 0.8);
  const n = Math.max(0, Math.ceil(L / k.speed - 1e-6)); // 움직이는 틱 수
  if (n === 0) {
    // 움직일 수 없음 (벽 바로 앞 · 속박 · 탐): 방패에 닿아 있는 적만 부딪히고 그 자리에서 메두사의 시선
    const room = roomAt(player.location, 1.0);
    for (const { e } of lineHits(player, up(from, 1.0), f, room + 0.35, k.width + 0.3)) {
      const along = (e.location.x - from.x) * f.x + (e.location.z - from.z) * f.z;
      if (along < -0.1 || along - 0.3 - bodyPad(e) > room + 0.05) continue;
      touched.add(e.id);
      bash(e, !draggable(e));
    }
    shieldFx(dim, shieldAt(), AE_SHIELD, 0.1, { phase: 0, alpha: 0.6 });
    finish();
    return true;
  }
  /** 밀고 가는 적이 길 끝 (벽 · 낭떠러지 앞) 을 넘지 않는 데까지 */
  const pinLimit = (want) => {
    let lim = want;
    for (const c of carried) if (!c.free) lim = Math.min(lim, Lm - ahead(c));
    return lim;
  };
  const step = (i) => {
    if (!ok()) return;
    const prev = st.s;
    let want = Math.min(L, prev + k.speed);
    let stop = false;
    if (held() || displaced()) {
      want = prev; // 묶이거나 다른 것이 옮김: 그 자리에서 멈춤
      stop = true;
    }
    // 밀던 적이 벽 · 낭떠러지에 닿으면 거기서 멈춤 (몰아붙임)
    const lim0 = pinLimit(want);
    if (lim0 < want) {
      want = Math.max(prev, lim0);
      stop = true;
    }
    // 방패 (와 밀고 가는 적의 몸) 에 새로 닿는 적: 지난 자리 (내 몸 가운데) 부터 이번 자리의 방패 앞 (+ 밀고 가는 적의 몸) 까지,
    // 길에서 옆으로 width 칸 (몸 반폭 여유) — 가슴 높이에서 보이는 적
    const dep = depth();
    const hits = lineHits(player, up(onPath(prev), 1.0), f, Math.max(0, want - prev) + k.front + 0.35 + dep, k.width + 0.3);
    for (const { e } of hits) {
      if (touched.has(e.id)) continue;
      const rel = sub(e.location, from);
      const along = rel.x * f.x + rel.z * f.z;
      const pad = bodyPad(e);
      if (along < prev - 0.1 || along - 0.3 - pad > want + k.front + 0.05 + dep) continue;
      touched.add(e.id);
      if (!draggable(e)) {
        // 끌 수 없는 몹 (보스 · 큰 몹 · 허수아비): 그 몸 앞에서 멈춤 (부딪힘 — 밀던 적이 있으면 그 적이 그 몸에 닿는 데서)
        want = Math.min(want, Math.max(prev, along - 0.3 - pad - k.front - 0.05 - depth()));
        stop = true;
        bash(e, true);
        continue;
      }
      const slot = carried.filter((c) => !c.free).length < k.carry ? slotFor(rel.x * side.x + rel.z * side.z) : undefined;
      if (slot) {
        carried.push({ e, lat: slot.lat, row: slot.row, pad });
      } else {
        // 더 밀 수 없으면 옆으로 밀쳐냄
        const sg = rel.x * side.x + rel.z * side.z >= 0 ? 1 : -1;
        try {
          e.applyKnockback({ x: side.x * sg * 0.7, z: side.z * sg * 0.7 }, 0.25);
        } catch {
          // 무시
        }
      }
      bash(e, false);
    }
    // 새로 밀게 된 적도 길 끝을 넘지 않게
    const lim1 = pinLimit(want);
    if (lim1 < want) {
      want = Math.max(prev, lim1);
      stop = true;
    }
    if (want > prev + 1e-3) {
      // 막힌 자리 (tryTeleport 거절 — 몸이 턱 · 모서리에 걸침) 면 0.3칸 뒤 (다음 틱에 다시 앞으로), 그것도 막히면 그 앞에서 멈춤
      const got = moveAlong(player, want, prev + 0.05, want);
      if (got !== undefined) {
        st.s = got;
        st.last = { ...player.location };
      } else stop = true;
    }
    // 밀고 가는 적: 방패 앞 자기 자리에 (발은 길의 땅). 옮길 수 없으면 (막힘 — 0.3칸 앞뒤도) 놓침
    for (const c of carried) {
      if (c.free) continue;
      if (!c.e.isValid || hasNoHealth(c.e)) {
        c.free = true;
        continue;
      }
      if (moveAlong(c.e, Math.min(Lm, st.s + ahead(c)), -Infinity, Lm, c.lat) === undefined) c.free = true;
    }
    // 그림: 빛의 방패 (틱마다 다시 — 빛이 지나가는 장은 이어짐) + 양옆 금빛 빛줄기 + 발밑 흙먼지
    const eye = player.getHeadLocation();
    shieldFx(dim, shieldAt(), AE_SHIELD, 0.09, { phase: i * 0.6, alpha: 0.6 });
    if (st.s > prev + 1e-3) {
      for (const sg of [-1, 1]) streakAway(dim, eye, add(up(onPath(prev), 0.6), side, sg * 0.95), add(up(onPath(st.s), 0.6), side, sg * 0.95), glow, 0.18, 0.25, 0.85, 1.0);
      fx(dim, "rpg:dust", up(onPath(prev), 0.25), { size: 0.9, life: 0.5, spin: i * 40, color: AE_DUST, alpha: 0.6 });
    }
    if (stop || st.s >= L - 1e-3 || i >= n + 3) {
      finish();
      return;
    }
    system.runTimeout(() => step(i + 1), 1);
  };
  step(1);
  return true;
}

/** 신의 방벽 돔의 육각 칸 자리 (가운데 기준 단위 벡터): 아래부터 고리 8개 — 고리마다 둘레 ÷ 1.15칸 개 (한 고리 건너 반 칸씩 엇갈림) + 꼭대기 하나 */
function domeLayout(R) {
  const rings = [];
  const N = 8;
  for (let j = 0; j < N; j++) {
    const th = ((j + 0.35) / N) * (Math.PI / 2);
    const n = Math.max(3, Math.round((2 * Math.PI * R * Math.cos(th)) / 1.15));
    const ring = [];
    for (let q = 0; q < n; q++) {
      const ph = ((q + (j % 2) * 0.5) / n) * Math.PI * 2;
      ring.push({ x: Math.cos(th) * Math.cos(ph), y: Math.sin(th), z: Math.cos(th) * Math.sin(ph) });
    }
    rings.push(ring);
  }
  rings[N - 1].push({ x: 0, y: 1, z: 0 });
  return rings;
}

/** a → b 번개 (굵은 조각 — 조각 길이 seg 칸, 한 틱의 파티클 수를 줄임) */
function boltCoarse(dim, a, b, color, o = {}, seg = 2.0) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.1) return;
  const u = unit(d);
  const s0 = unit(Math.abs(u.y) > 0.9 ? { x: 1, y: 0, z: 0 } : { x: -u.z, y: 0, z: u.x });
  const n = Math.max(3, Math.ceil(L / seg));
  const pts = [a];
  for (let i = 1; i < n; i++) {
    const j = (Math.random() * 2 - 1) * (o.jag ?? 0.45);
    pts.push(add(add(a, u, (L * i) / n), s0, j));
  }
  pts.push(b);
  for (let i = 0; i < pts.length - 1; i++) {
    const s = sub(pts[i + 1], pts[i]);
    fx(dim, "rpg:arrow_trail", add(pts[i], s, 0.5), { dir: unit(s), size: len(s) / 2 + 0.08, thick: o.thick ?? 0.12, life: o.life ?? 0.3, color, alpha: 1 });
  }
}

/** 신의 방벽이 선 사람: 플레이어 id → 끝나는 틱 · 돔 안인지 (맞을 때 육각 칸이 번쩍 — registerSkills 의 피해 알림) @type {Map<string, {until: number, inside: (pl: any) => boolean}>} */
const domes = new Map();

/** 신의 방벽이 막음: 때린 쪽 (때린 것이 없으면 보는 쪽) 1.6칸에 육각 칸이 번쩍 + 불꽃 + 막는 소리 */
function domeBlocked(player, attacker) {
  try {
    const dim = player.dimension;
    const b = bodyPoint(player);
    const a = attacker?.isValid ? bodyAt0(attacker, b) : add(b, facingOf(player), 1);
    const d = len(sub(a, b)) > 1e-3 ? unit(sub(a, b)) : facingOf(player);
    const at = add(b, d, 1.6); // 1인칭 화면을 덮지 않게 몸에서 1.6칸
    hexFx(dim, at, { frame: 1, size: 0.42, life: 0.22, alpha: 0.85, color: AE_WHITE });
    sparks(dim, at, SKILLS.color.shield?.[0] ?? AE_WHITE, 4, 3);
    play(dim, "item.shield.block", at, 1.3, 0.7);
  } catch {
    // 무시
  }
}

/**
 * 신의 방벽 (v2.20.0): 방패를 땅에 꽂아 (발밑 땅 — 3칸 안에 없으면 그 자리) 반지름 radius 칸 금빛 육각 돔을 ticks 틱 동안 세움.
 * 꽂는 충격: 돔 안의 적 (가운데 위 1.2칸에서 보이는) 에게 mult + 도발 · 둔화. 돔 안에 서 있는 동안 내가 받는 피해 -reduce (막을 때마다 육각 칸이 번쩍 —
 * 돔 밖으로 나가면 없음), 10틱마다 돔 안의 적을 도발 (taunt 틱) · 둔화 (구속 slow + 1, 보스는 아님). ticks 틱에 돔이 안으로 접힘 (fold 틱 — 육각 칸이 가운데로,
 * 돔 안의 적을 가운데로 끌어당기며 foldMult) → burst 틱 뒤 번개와 함께 바깥으로 터짐 (afterMult, 밖으로 밀쳐냄 — 접힐 때 맞은 적은 밀려났어도 radius + 2.5칸 안이면).
 * 쓰러지거나 차원을 옮기면 막기 · 남은 타격 (접힘 · 폭발) 은 없음 (이미 그린 돔은 그대로 접히며 사라짐)
 */
function aegisDome(player, w, k) {
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const p0 = { ...player.location };
  const g = solidBelow(dim, up(p0, 0.3), 3.3);
  const c = { x: p0.x, y: g === undefined ? p0.y : Math.min(p0.y, g), z: p0.z };
  const f = facingOf(player);
  const R = k.radius;
  const T = k.ticks;
  const [glow, core] = colors(w);
  /** 돔 안에 서 있는지: 몸 가운데 (발 위 0.9칸) 가 반구 (반지름 radius + 0.3) 안, 발이 땅 아래 1.5칸까지 */
  const inside = (pl) => {
    const l = pl.location;
    return Math.hypot(l.x - c.x, l.y + 0.9 - c.y, l.z - c.z) <= R + 0.3 && l.y >= c.y - 1.5;
  };
  const enemiesIn = (extra = 0) => visibleFrom(player, up(c, 1.2), around(player, c, R + extra, 3));
  const slowAndTaunt = (e, mark) => {
    if (!bossLike(e)) {
      try {
        e.addEffect("slowness", 15, { amplifier: k.slow, showParticles: false });
      } catch {
        // 효과가 없는 몹
      }
    }
    const scripted = taunt(player, e, k.taunt);
    if (mark) tauntMark(dim, e, scripted);
  };
  // 막기: 돔 안에 서 있는 동안만 (돔이 접힐 때까지) — 맞을 때마다 육각 칸이 번쩍 (domes)
  setGuard(player, T, k.reduce, undefined, (pl) => ok() && inside(pl));
  const dome = { until: system.currentTick + T, inside: (pl) => ok() && inside(pl) };
  domes.set(player.id, dome);
  system.runTimeout(() => {
    if (domes.get(player.id) === dome) domes.delete(player.id);
  }, T);
  // 꽂는 충격: 앞 1.5칸 (벽이 가까우면 그 앞) 에 꽂힌 빛의 방패 (밑이 땅, 0.6초 → 흩어짐) + 땅의 룬 (돔이 접힐 때까지) · 충격 고리 + 불꽃.
  // 고리 (rpg:shock_ring) 는 수명 동안 2.6배로 커짐 — R ÷ 2.6 에서 시작해 돔 가장자리 안에서 사라짐 (돔 밖까지 퍼져 범위를 헷갈리게 하지 않게)
  const plantAt = Math.max(0.6, Math.min(1.5, rayDist(dim, up(c, 0.85), f, 1.9) - 0.4));
  const planted = add(up(c, 0.85), f, plantAt);
  shieldFx(dim, planted, 0.85, 0.6, { phase: 0 });
  system.runTimeout(() => {
    if (ok()) shieldFx(dim, planted, 0.85, 0.35, { progress: 2 });
  }, 12);
  rune(dim, c, { size: R, life: (T + k.fold) / 20, color: glow, alpha: 0.7, rate: 20 });
  shock(dim, c, glow, R / RING_GROW, 0.5);
  shock(dim, c, core, (R * 0.55) / RING_GROW, 0.4);
  // 번쩍임 · 불꽃은 방패가 꽂힌 밑동에서 (내 발밑이 아니라 — 아래를 볼 때 1인칭 화면을 덮지 않게)
  const foot = add(up(c, 0.35), f, plantAt);
  burst(dim, foot, core, 1.4, 0.3);
  sparks(dim, foot, glow, 8, 5);
  play(dim, "mace.heavy_smash_ground", c, 0.9, 1.0);
  play(dim, "item.shield.block", c, 0.7, 1.0);
  play(dim, "beacon.activate", c, 1.2, 0.9);
  shakeNear(dim, c, 10, 0.2, 0.25);
  for (const e of enemiesIn()) {
    hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
      if (skillHit(player, e, w, k.mult) > 0) slowAndTaunt(e, true);
    });
  }
  // 돔: 아래 고리부터 한 틱에 하나씩 솟아오름 (ticks + fold 틱까지) — ticks 틱부터 0.4초 동안 가운데 쪽으로 접혀 들어감 (파티클 식 — 가운데 1.8칸에서 멈춤: 가운데 선 사람의 머리를 지나지 않게)
  domeLayout(R).forEach((ring, j) => {
    const draw = () => {
      if (!ok()) return;
      for (const u of ring) hexFx(dim, add(c, u, R), { dir: { x: -u.x, y: -u.y, z: -u.z }, size: 0.62, life: (T + k.fold - j) / 20, fold: (T - j) / 20, reach: R - 1.8, alpha: 0.5, color: glow });
    };
    if (j === 0) draw();
    else system.runTimeout(draw, j);
  });
  // 10틱마다 돔 안의 적을 도발 · 둔화, 20틱마다 바닥 고리가 가운데에서 돔 가장자리까지 일렁임
  for (let t = 10; t < T; t += 10) {
    system.runTimeout(() => {
      if (!ok()) return;
      for (const e of enemiesIn()) slowAndTaunt(e, false);
      if (t % 20 === 0) {
        shock(dim, c, glow, R / RING_GROW, 0.4);
        play(dim, "beacon.ambient", c, 1.4, 0.7);
      }
    }, t);
  }
  // 접힘: 땅의 룬 둘이 육각 칸과 함께 0.4초 동안 가운데로 말려 듦 (바깥으로 퍼지는 고리가 아니라 안으로) · 소리 + 돔 안의 적을 가운데로 끌어당기며 벰
  /** 접힐 때 맞은 적 (밀려났어도 터질 때 맞음) @type {Map<string, any>} */
  const folded = new Map();
  system.runTimeout(() => {
    if (!ok()) return;
    play(dim, "beacon.deactivate", c, 1.4, 1.0);
    play(dim, "mob.warden.sonic_charge", c, 1.5, 0.7);
    // (땅의 룬보다 조금 위 — 같은 높이의 평면 둘이 깜빡이지 않게)
    rune(dim, up(c, 0.04), { size: R, life: k.fold / 20, grow: -0.8, color: core, alpha: 0.95, rate: -360 });
    rune(dim, up(c, 0.08), { size: R * 0.62, life: k.fold / 20, grow: -0.75, color: glow, alpha: 0.9, rate: 300 });
    for (const e of enemiesIn(0.5)) {
      folded.set(e.id, e);
      hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
        if (skillHit(player, e, w, k.foldMult) <= 0 || bossLike(e) || !e.isValid) return;
        const d = flat(sub(c, e.location));
        try {
          e.applyKnockback({ x: d.x * 0.55, z: d.z * 0.55 }, 0.12);
        } catch {
          // 무시
        }
      });
    }
  }, T);
  // 터짐: 가운데 번쩍임 → 번개와 함께 바깥으로 — 바닥으로 번개 여섯 줄 · 고리 둘, 적마다 하늘에서 번개 (한 틱에 셋씩), 밖으로 밀쳐냄
  system.runTimeout(() => {
    if (!ok()) return;
    // 가운데 번쩍임은 돔 가운데 가슴 높이 — 내 눈이 그 3칸 안이면 (가운데에 선 채로) 보는 쪽 앞 (벽 앞까지 2.2칸) 에 작게
    // (화면에 나란한 그림이 눈 바로 밑에 크게 그려지면 아래를 볼 때 1인칭 화면을 다 덮음 — 그람 용익 강하의 앞쪽 번쩍임과 같음)
    const mid = up(c, 1.2);
    if (len(sub(mid, player.getHeadLocation())) >= 3) {
      burst(dim, mid, AE_WHITE, 3.2, 0.35);
      burst(dim, mid, glow, 4.6, 0.45);
    } else {
      const pl = player.location;
      const fN = facingOf(player);
      const at = up(add(pl, fN, Math.max(0.8, Math.min(2.2, rayDist(dim, up(pl, 0.6), fN, 2.6) - 0.4))), 0.6);
      burst(dim, at, AE_WHITE, 1.8, 0.35);
      burst(dim, at, glow, 2.6, 0.45);
    }
    shock(dim, c, glow, R + 1.5, 0.55);
    shock(dim, c, core, R + 0.5, 0.45);
    for (let q = 0; q < 6; q++) {
      const d = turnY(f, q * 60 + 30);
      boltCoarse(dim, up(c, 0.5), up(add(c, d, R + 1.5), 0.3), glow, { thick: 0.16, life: 0.35, jag: 0.3 });
    }
    play(dim, "ambient.weather.thunder", c, 1.3, 0.8);
    play(dim, "item.trident.thunder", c, 1.0, 1.0);
    play(dim, "random.explode", c, 1.2, 0.7);
    shakeNear(dim, c, 14, 0.35, 0.4);
    const list = unionById(
      enemiesIn(0.5),
      [...folded.values()].filter((e) => canHit(player, e) && Math.hypot(e.location.x - c.x, e.location.z - c.z) <= R + 2.5 + bodyPad(e) && Math.abs(e.location.y - c.y) <= 5),
    );
    list.forEach((e, j) => {
      const strike = () => {
        if (!ok()) return;
        hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
          const b = bodyAt0(e, up(c, 1));
          boltCoarse(dim, up(b, 8), b, glow, { thick: 0.24, life: 0.3, jag: 0.5 });
          boltCoarse(dim, up(b, 8), b, AE_WHITE, { thick: 0.09, life: 0.25, jag: 0.5 });
          if (skillHit(player, e, w, k.afterMult) <= 0) return;
          fx(dim, "rpg:arrow_spark", b, { dir: V_UP, size: 0.5, grow: 2.2, life: 0.22, color: AE_WHITE, alpha: 1 });
          if (bossLike(e) || !e.isValid) return;
          const d = flat(sub(e.location, c));
          try {
            e.applyKnockback({ x: d.x * 0.9, z: d.z * 0.9 }, 0.35);
          } catch {
            // 무시
          }
        });
      };
      const at = Math.floor(j / 3);
      if (at === 0) strike();
      else system.runTimeout(strike, at);
    });
  }, T + k.burst);
  return true;
}

// ------------------------------------------------------------ 아르테미스의 은활 (활) — v2.21.0 개편 (달빛 · 사냥)
// 사용자가 고른 컨셉 "은월 관통 / 만월의 사냥" · 세기 "영웅 룬~초월 룬" (Lv.40 민첩 125 의 민첩 룬 위력 기준) · 빛의 은활 그림은 모델 옆모습 (rpg:artemis_bow)
const AR_MOON = [0.78, 0.88, 1.0]; // 달빛 (은빛 푸른)
const AR_WHITE = [1, 1, 1];
/** 빛의 은활 그림에서 시위 · 줌통의 높이 (가운데 기준, 반 크기 = 1 — build.py artemis_bow_base: 320점 그림의 시위 줄 · 줌통) */
const AR_STRING_Y = 0.053;
const AR_GRIP_Y = -0.546;
/** 활 반폭 · 위아래 끝 (반 크기 = 1) */
const AR_BOW_HALF = 0.8;
const AR_BOW_TOP = 0.83;
/** 달 그림에서 원판 반지름 (반 크기 = 1 — build.py artemis_moon_frame: 256점 그림의 반지름 88) */
const AR_MOON_DISK = 88 / 128;

/** 거대한 은빛 화살 하나 (rpg:artemis_arrow — 궁니르 창 · 빛의 검과 같은 변수): at = 화살 가운데, dir = 화살촉 방향 (단위), half = 화살 반 길이 (칸), life = 초 */
function arrowFx(dim, at, dir, half, life, o = {}) {
  spearFx(dim, at, dir, half, life, o, "rpg:artemis_arrow");
}

/** 초승달 참격 (rpg:artemis_crescent — 화면을 향해 돎): size = 반 크기, life = 나타난 뒤의 초, o.spin 처음 각 · o.rate 초당 각 · o.grow 커지는 몫 · o.delay 초 뒤에 나타남 */
function crescentFx(dim, at, size, life, color, o = {}) {
  const delay = o.delay ?? 0;
  fx(dim, "rpg:artemis_crescent", at, { size, life: life + delay, spin: o.spin ?? 0, speed: o.rate ?? 0, grow: o.grow ?? 0.3, delay, color, alpha: o.alpha ?? 1 });
}

/** 빛의 은활 (rpg:artemis_bow — 보는 사람을 향해 섬, 아래를 겨눔): at = 가운데, size = 반 크기 (칸), life 초 — 마지막 fade 초 동안 흩어짐 */
function bowFx(dim, at, size, life, fade) {
  try {
    const m = vars({ size, life, spin: Math.floor(Math.random() * 4), color: [1, 1, 1], alpha: 1 });
    m.setFloat("variable.fade", fade);
    dim.spawnParticle("rpg:artemis_bow", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 만월 (rpg:artemis_moon — 보는 사람을 향함): at = 가운데, size = 반 크기 (칸), 처음 wax 초 동안 차오름, 마지막 fade 초 동안 번쩍인 뒤 흩어짐 */
function moonFx(dim, at, size, life, wax, fade) {
  try {
    const m = vars({ size, life, color: [0.86, 0.9, 1.0], alpha: 1 }); // 조금 푸르게 — 앞의 흰 활과 구별
    m.setFloat("variable.wax", wax);
    m.setFloat("variable.fade", fade);
    dim.spawnParticle("rpg:artemis_moon", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/**
 * 조준 보정으로 고른 적 쪽 (은월 관통): 가로는 눈에서 그 몸 쪽, 세로는 시선의 기울기 그대로 — 그 몸 높이에서 0.6칸보다 벗어나면 0.6칸까지만 기울임.
 * 몸 가운데로 곧장 겨누면 눈보다 낮은 몸 쪽으로 내려가 거대한 화살이 뒤의 땅에 일찍 박힘 (평지에서 앞을 보고 쏘면 수평으로 24칸 — 줄 위의 적은 폭 안)
 */
function assistDir(eye, view, body) {
  const q = sub(body, eye);
  const h = Math.hypot(q.x, q.z);
  if (h < 0.3) return unit(q);
  const vh = Math.hypot(view.x, view.z);
  const yView = vh > 1e-3 ? (view.y / vh) * h : q.y; // 시선 기울기로 그 몸까지 갔을 때의 높이 (눈 기준)
  return unit({ x: q.x, y: Math.max(q.y - 0.6, Math.min(q.y + 0.6, yView)), z: q.z });
}

/** 달빛 화살이 지나가며 꿰뚫은 자리: 은빛 X 번쩍임 + 불꽃 (눈 가까이는 작게 — 1.5칸 안이면 불꽃만). light = 번쩍임 하나만 (한 틱에 여럿 맞을 때) */
function moonHitFx(dim, e, eye, dir, glow, light = false) {
  const c = bodyAt0(e, eye);
  const de = len(sub(c, eye));
  if (de < 1.5) {
    // 몸에 붙은 적: X 가 1인칭 화면을 덮지 않게 불꽃만
    if (!light) sparks(dim, c, AR_MOON, 3, 4);
    return;
  }
  const near = Math.min(1, Math.max(0.45, (de - 0.8) / 2.5));
  fx(dim, "rpg:slash_x", c, { dir, size: 1.0 * near, life: 0.25, color: glow, alpha: 1 });
  if (light) return;
  fx(dim, "rpg:slash_x", add(c, dir, -0.04), { dir, size: 0.7 * near, life: 0.2, color: AR_WHITE, alpha: 1 });
  sparks(dim, c, AR_MOON, 4, 5);
}

/**
 * 은월 관통 (v2.21.0): charge 틱 동안 활에 달빛이 모임 (손 앞에서 차오르는 초승달 · 둘레에서 모여드는 빛 알갱이) → 그때 조준한 쪽 (시선 — 시선에 몹이 겹치지 않으면
 * 조준 보정으로 고른 3칸 밖의 몹 쪽: 가로는 그 몸 쪽, 기울기는 시선 (assistDir). 고른 줄이 그 몸에 닿기 전에 막히면 — 낮은 담 너머 — 그 몸 쪽으로 곧게) 으로 거대한 은빛 화살:
 * 눈에서 range 칸 (벽 앞까지) 을 틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult — 화살촉이 지나가는 틱, 눈에서 가려진 적 · 벽 너머의 적은 아님).
 * 판정 (벽 · 꿰뚫는 줄 · 가려짐) 은 모두 눈에서 — 문 · 유리판에 붙어 서도 그 너머 (판에 붙은 얇은 몹도) 를 맞히지 않음, 그림은 손에서 같은 끝으로.
 * 지나간 길에 달빛 줄기가 남음 → 화살이 끝에 닿고 delay 틱 뒤 길이 다시 빛나며 초승달 참격으로 갈라짐 (afterMult — 그때 길 위의 적 + 꿰뚫린 적은 밀려났어도 길에서
 * width + 2.5칸 안이면 (몸에서 길에 가장 가까운 점으로 — 키 큰 보스), 다섯씩 한 틱). 벽에 닿으면 화살이 벽에 박혔다가 갈라질 때 흩어짐. 모으는 동안 활을 바꾸거나 침묵이면
 * 쏘지 않음 (달빛이 흩어짐 — 재사용 대기는 그대로). 쓰러지거나 차원을 옮기면 남은 것은 없음. 적이 없어도 쏨. 화살 그림은 손 앞 2.4칸부터 (1인칭 화면을 덮지 않게 —
 * 벽이 가까우면 그림 없이 빛만, 눈 가까이의 연출은 작게 · 빼고: 몸에 붙은 적은 X · 초승달 없이 불꽃만)
 */
function artemisPierce(player, w, k) {
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow] = colors(w);
  /** 활을 든 손 (화살이 나오는 곳 — 평타 화살과 같은 arrowPath 의 시작) */
  const handFor = (dir) => arrowPath(player.getHeadLocation(), dir, 3).from;
  // 모음: 손 앞에서 초승달이 차오름 (커지며 돎) + 둘레에서 빛 알갱이가 손으로 모여듦
  play(dim, "crossbow.loading.start", player.location, 1.0, 0.8);
  play(dim, "resonate.amethyst_block", player.location, 0.9, 1.2);
  for (let t = 0; t < k.charge; t++) {
    const draw = () => {
      if (!ok()) return;
      const view = player.getViewDirection();
      const hand = handFor(view);
      const g = (t + 1) / k.charge;
      crescentFx(dim, add(hand, view, 0.9), 0.14 + 0.22 * g, 0.1, t % 2 ? AR_WHITE : AR_MOON, { spin: -t * 25, alpha: 0.55 + 0.45 * g, grow: 0 });
      const side = rightOf(facingOf(player));
      const eyeNow = player.getHeadLocation();
      for (let j = 0; j < 3; j++) {
        const a = (j / 3) * Math.PI * 2 + t * 0.9;
        const off = add(add(up(hand, Math.sin(a) * 1.1), side, Math.cos(a) * 1.1), view, 0.4);
        if (len(sub(off, eyeNow)) < 1.0) continue; // 바로 아래 · 위를 볼 때 눈앞에서 나오는 알갱이는 빼기
        const d = sub(hand, off);
        fx(dim, "rpg:arrow_spark", off, { dir: unit(d), speed: len(d) / 0.2, size: 0.07, life: 0.2, color: AR_MOON, alpha: 0.9 });
      }
      if (t === k.charge - 3) play(dim, "crossbow.loading.middle", hand, 1.0, 1.1);
    };
    if (t === 0) draw();
    else system.runTimeout(draw, t);
  }
  const shoot = () => {
    const eye = player.getHeadLocation();
    const view = player.getViewDirection();
    const hand = handFor(view);
    // 조준: 시선에 겹친 적은 시선 그대로 (가까운 적의 몸 쪽으로 꺾여 땅에 박히거나 옆으로 새지 않게), 조준 보정으로 고른 적 (3칸 밖) 은 그 몸 쪽으로 (assistDir — 기울기는 시선)
    let dir = view;
    /** 조준 보정으로 고른 몸 (눈에서 보이는 점 — assistTarget 이 확인) */
    let aimBody;
    try {
      const r = aimTarget(player, k.range, BOW.aimAssist ?? 0);
      if (r.dir && r.target?.isValid && canHit(player, r.target)) {
        aimBody = bodyToward(r.target, eye, view);
        if (r.dist >= 3) dir = assistDir(eye, view, aimBody);
      }
    } catch {
      // 보는 쪽
    }
    // 고른 줄이 그 몸에 닿기 전에 막히면 (낮은 담 · 턱 너머의 적 — 시선이 담에 닿아 조준 보정이 고름) 그 몸 쪽으로 곧게
    if (aimBody) {
      const toBody = sub(aimBody, eye);
      if (rayDist(dim, eye, dir, k.range) < len(toBody) - 0.5) dir = unit(toBody);
    }
    // 판정 (벽까지의 거리 · 꿰뚫는 줄 · 가려짐) 은 눈에서 — 손은 눈 앞 0.6칸이라 문 · 유리판 · 울타리에 붙어 서면 그 너머에 있음. 그림은 손에서 같은 끝으로
    const wallD = rayDist(dim, eye, dir, k.range); // 눈에서 막는 블록까지
    const reach = Math.max(0.5, wallD - 0.3); // 화살촉이 닿는 거리 (벽 앞)
    const wall = reach < k.range - 0.3 - 1e-6;
    /** 벽 · 판 너머의 적은 빼기 (판에 붙어 선 얇은 몹은 시선 검사의 여유 0.35칸으로 보일 수 있음) */
    const before = (x) => !wall || x.t <= wallD;
    const outT = Math.max(1, Math.ceil(reach / k.speed));
    const end = add(eye, dir, reach);
    const vd = sub(end, hand);
    const vreach = len(vd); // 그림의 길이 (손 → 끝)
    const vdir = vreach > 1e-3 ? unit(vd) : dir;
    const now = system.currentTick;
    const tip0 = 2.4 + 2 * k.half; // 화살 그림의 처음 촉 자리 (손에서 — 깃 끝이 손 앞 2.4칸)
    const shown = vreach > tip0 + 0.5; // 화살 그림을 그릴 만큼 날아가는지
    // 쏨: 손 앞의 작은 번쩍임 (눈 1.6칸 안이면 없음 — 문 · 벽에 붙어 쏠 때) + 거대한 화살 (끝에 닿는 틱에 촉이 끝에) + 촉의 빛 + 지나가며 켜지는 달빛 줄기
    // (갈라질 때까지 옅어지며 남음, 눈 1.6칸 안은 빼고)
    const flash = add(hand, vdir, Math.min(1.4, vreach));
    if (len(sub(flash, eye)) >= 1.6) burst(dim, flash, glow, 0.3, 0.18);
    if (shown) {
      const vSpeed = (vreach - tip0) / (outT / 20);
      arrowFx(dim, add(hand, vdir, tip0 - k.half), vdir, k.half, outT / 20, { move: vdir, speed: vSpeed });
      fx(dim, "rpg:arrow_spark", add(hand, vdir, tip0), { dir: vdir, speed: vSpeed, size: 1.0, grow: 1, life: outT / 20 + 0.05, color: glow, alpha: 0.7 });
      fx(dim, "rpg:arrow_spark", add(hand, vdir, tip0), { dir: vdir, speed: vSpeed, size: 0.45, grow: 1, life: outT / 20 + 0.05, color: AR_WHITE, alpha: 1 });
    }
    // 화살촉이 지나가는 자리마다 달빛 고리 (화면을 향함) + 두 틱마다 도는 작은 초승달 — 화살 그림은 화살 방향을 따라 누워서 쏜 사람 (1인칭 · 뒤) 에게는 거의 보이지 않으므로,
    // 뒤에서는 멀어지는 고리로 보임 (눈 2.5칸 안은 빼고)
    for (let i = 0; i < outT; i++) {
      const s = Math.min(vreach, (i + 1) * k.speed);
      const passFx = () => {
        if (!ok()) return;
        const at = add(hand, vdir, s);
        if (len(sub(at, player.getHeadLocation())) < 2.5) return;
        ring(dim, at, vdir, glow, 0.55, 0.3);
        if (i % 2 === 0) crescentFx(dim, at, 0.5, 0.12, AR_WHITE, { spin: i * 70, rate: 900, grow: 0 });
      };
      if (i === 0) passFx();
      else system.runTimeout(passFx, i);
    }
    const hold = (outT + k.delay) / 20 + 0.1;
    beamAway(dim, eye, add(hand, vdir, 1.2), end, glow, { half: 0.7, thick: 0.3, life: hold, sweep: outT / 20, gap: 1.4, alpha: 0.8 });
    beamAway(dim, eye, add(hand, vdir, 1.2), end, AR_WHITE, { half: 0.6, thick: 0.09, life: hold * 0.6, sweep: outT / 20, gap: 3.0, alpha: 1 });
    play(dim, "crossbow.shoot", hand, 1.2, 0.7);
    play(dim, "random.bow", hand, 1.4, 0.5);
    // 꿰뚫음: 화살촉이 지나가는 틱 (가까운 적부터). 한 틱에 넷째부터는 번쩍임만 (한 틱 파티클 60 안 — 쏜 틱에 손 앞 적이 여럿일 때)
    /** 꿰뚫린 적 (밀려나도 갈라질 때 맞음) @type {Map<string, any>} */
    const pierced = new Map();
    /** 틱 → 그 틱에 그린 꿰뚫음 연출 수 @type {Map<number, number>} */
    const perTick = new Map();
    for (const { e, t } of lineHits(player, eye, dir, reach, k.width).filter(before)) {
      pierced.set(e.id, e);
      hitAt(player, e, reserveHit(player, e, now + Math.max(0, Math.ceil(t / k.speed) - 1)), ok, () => {
        if (skillHit(player, e, w, k.mult) <= 0) return;
        const tk = system.currentTick;
        const n = (perTick.get(tk) ?? 0) + 1;
        perTick.set(tk, n);
        moonHitFx(dim, e, player.getHeadLocation(), dir, glow, n > 3);
        play(dim, "random.bowhit", bodyAt0(e, end), 1.0, 0.7);
      });
    }
    // 끝: 벽이면 화살이 박힘 (갈라질 때까지) + 번쩍임 · 불꽃 (눈 가까이는 작게 — 1.6칸 안이면 없음), 사거리 끝이면 은빛 알갱이로 흩어짐
    const stuck = add(end, vdir, 0.35 - k.half); // 촉이 벽에 0.35칸 박힌 화살의 가운데
    system.runTimeout(() => {
      if (!ok()) return;
      if (wall) {
        if (shown) arrowFx(dim, stuck, vdir, k.half, k.delay / 20 + 0.05);
        const de = len(sub(end, player.getHeadLocation()));
        if (de >= 1.6) {
          const sz = Math.min(1, Math.max(0.3, (de - 0.8) / 2.5));
          burst(dim, end, AR_WHITE, 0.9 * sz, 0.22);
          sparks(dim, end, glow, de >= 2.5 ? 8 : 4, 5 * sz);
        }
        play(dim, "random.bowhit", end, 1.4, 0.5);
      } else if (shown) arrowFx(dim, add(end, vdir, -k.half), vdir, k.half, 0.35, { progress: 2 });
    }, outT);
    // 갈라짐: 길이 다시 빛나고 2칸마다 초승달 참격이 앞쪽으로 차례로 (0.15초 — 눈 2.2칸 안은 빼고, 가까우면 작게) + 맞는 적마다 엇갈린 초승달
    system.runTimeout(() => {
      if (!ok()) return;
      const eyeNow = player.getHeadLocation();
      beamAway(dim, eyeNow, add(hand, vdir, 1.2), end, AR_WHITE, { half: 0.6, thick: 0.16, life: 0.25, gap: 2.0, alpha: 1 });
      for (let s = 1.5, j = 0; s <= vreach + 1e-6; s += 2.0, j++) {
        const at = add(hand, vdir, s);
        const de = len(sub(at, eyeNow));
        if (de < 2.2) continue;
        crescentFx(dim, at, Math.min(1.3, 0.35 + de * 0.12), 0.35, j % 2 ? AR_WHITE : AR_MOON, { spin: j % 2 ? 40 : -40, rate: j % 2 ? 300 : -300, grow: 0.5, delay: (s / vreach) * 0.15 });
      }
      if (wall && shown) arrowFx(dim, stuck, vdir, k.half, 0.3, { progress: 2 });
      const toPath = (e) => {
        let b;
        try {
          b = bodyToward(e, eye, dir); // 몸에서 길에 가장 가까운 점 (키 큰 보스는 몸 가운데가 길보다 한참 위)
        } catch {
          b = bodyAt0(e, end);
        }
        const q = sub(b, eye);
        const t = Math.max(0, Math.min(reach, q.x * dir.x + q.y * dir.y + q.z * dir.z));
        return len(sub(q, { x: dir.x * t, y: dir.y * t, z: dir.z * t }));
      };
      const list = unionById(
        lineHits(player, eye, dir, reach, k.width).filter(before).map((x) => x.e),
        [...pierced.values()].filter((e) => canHit(player, e) && toPath(e) <= k.width + 2.5 + bodyPad(e)),
      );
      let j = 0;
      for (const e of list) {
        hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j++ / 5)), ok, () => {
          if (skillHit(player, e, w, k.afterMult) <= 0) return;
          const c = bodyAt0(e, end);
          const dc = len(sub(c, player.getHeadLocation()));
          if (dc >= 1.5) {
            // 몸에 붙은 적은 초승달 없이 (1인칭 화면을 덮지 않게)
            const sz = Math.min(1, Math.max(0.45, (dc - 0.8) / 2.5));
            crescentFx(dim, c, 0.95 * sz, 0.3, AR_WHITE, { spin: 35, rate: 520, grow: 0.4 });
            crescentFx(dim, c, 0.85 * sz, 0.3, glow, { spin: 215, rate: 520, grow: 0.4 });
          }
          sparks(dim, c, AR_MOON, list.length > 5 ? 2 : 4, 4);
        });
      }
      play(dim, "chime.amethyst_block", end, 1.6, 1.3);
      play(dim, "break.amethyst_cluster", add(hand, vdir, Math.min(vreach, 5)), 1.3, 0.9);
      play(dim, "firework.twinkle", add(hand, vdir, Math.min(vreach, 6)), 1.4, 1.1);
      if (list.length) shake(player, 0.15, 0.2);
    }, outT + k.delay);
  };
  system.runTimeout(() => {
    if (!ok()) return;
    if (!holding(player, w) || isSilenced(player)) {
      // 활을 바꿨거나 침묵: 모은 달빛이 흩어짐 (쏘지 않음)
      const h = handFor(player.getViewDirection());
      sparks(dim, h, AR_MOON, 6, 3);
      play(dim, "random.fizz", h, 0.5, 1.6);
      return;
    }
    shoot();
  }, k.charge);
  return true;
}

/**
 * 만월의 사냥 (v2.21.0): 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘에 만월을 품은 빛의 은활 (모델 모양 — 아래를 겨눔, 처음부터 시위에 은빛 화살,
 * 만월은 활 뒤에서 활 몸에 안긴 듯 조금 위 — 그쪽이 벽이면 벽 앞까지) + 땅의 은빛 룬 + 달빛 기둥. 활의 크기 · 높이는 그곳까지의 가로 거리에 맞춤 (위끝이 눈높이에서 26° 위까지),
 * 굴 · 지붕 · 나뭇잎 아래면 트인 높이에 맞게 작고 낮게 → rise 틱부터 gap 틱마다 화살비 waves 번 (활에서 반지름 radius 칸 안의 적마다 한 발 — 3틱 뒤 꽂힘 mult (한 틱에 열둘씩),
 * 활에서 보이는 적만, 빈 땅에도 넷) → final 8틱 전에 시위에 거대한 화살을 메겨 당겼다가 4틱 전에 쏨 → final 틱에 가운데에 내리꽂혀 달빛 폭발 (afterMult, 밀쳐 띄움 —
 * 보스는 밀지 않음, 가운데에 선 적은 나에게서 먼 쪽으로, 화살비에 맞은 적은 밀려났어도 radius + 2.5칸 안이면) · 만월이 번쩍인 뒤 흩어지고 활도 흩어짐.
 * 땅을 조준하지 않았거나 (허공) · 그 땅이 눈에서 보이지 않거나 (천장 · 지붕 위) · min 칸 앞으로 밀 수 없으면 (벽 · 나무) 재사용 대기 없이 거절.
 * 쓰러지거나 차원을 옮기면 남은 화살 · 폭발은 없음 (이미 그린 달 · 활은 그대로 흩어짐)
 */
function artemisMoon(player, w, k) {
  const dim = player.dimension;
  const spot = fallSpot(player, k);
  const eye = player.getHeadLocation();
  // 땅이 없거나 (허공) · 그 땅이 눈에서 보이지 않으면 (천장 · 지붕 위를 조준 — 광선이 블록 안에서 시작) 쓰지 않음
  if (!spot || (!lineOfSight(player, eye, up(spot, 1.2)) && !lineOfSight(player, eye, up(spot, 2.0)))) {
    warn(player, `§c${k.name}: §7땅을 조준해야 합니다 (${k.range}칸 안)`);
    return false;
  }
  const pl = player.location;
  const hd = Math.hypot(spot.x - pl.x, spot.z - pl.z);
  // 가로 min 칸보다 가까운데 밀어낼 수 없었으면 (그 너머가 벽 · 나무) 쓰지 않음 — 거대한 활 · 폭발이 1인칭 화면을 덮지 않게
  if (hd < k.min - 0.5) {
    warn(player, `§c${k.name}: §7${k.min}칸 밖의 땅을 조준해야 합니다`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow, core] = colors(w);
  const away = hd > 0.5 ? flat(sub(spot, pl)) : facingOf(player);
  // 크기 · 높이: 트인 높이 (나뭇잎도 막힘 — 나무 밑이면 잎 아래에) 안에서 (아래 끝 땅 위 0.6칸 ~ 만월 위끝 천장 아래 0.2칸 = 1.68 × 반 크기 + 0.8 — 낮은 굴이면 작게,
  // 그래도 모자라면 천장 쪽을 지킴). 가까우면 작고 낮게: 활 위끝이 눈높이에서 26° 위 (tan 26° = 0.49 — 그곳을 내려다볼 때는 화면 가운데에서 12칸 34° · 6칸 41° 위) 를 넘지 않게
  const room = skyRoom(dim, up(spot, 0.2), 12, false) + 0.2;
  const s = Math.max(0.6, Math.min(2.6, (room - 0.8) / (AR_BOW_TOP * 2 + 0.02), Math.max(1.6, hd * 0.3))); // 활 반 크기
  const bowH = Math.min(room - (AR_BOW_TOP + 0.02) * s - 0.2, Math.max(AR_BOW_TOP * s + 0.6, Math.min(6, 1.62 + hd * 0.49 - AR_BOW_TOP * s)));
  const bowC = up(spot, bowH);
  const nock = up(bowC, AR_STRING_Y * s); // 시위
  const rest = up(bowC, AR_GRIP_Y * s); // 줌통 (화살이 나가는 곳)
  const moonSize = ((0.75 * AR_BOW_HALF) / AR_MOON_DISK) * s; // 달 원판이 활 반폭의 0.75
  // 만월은 활 뒤 (나에게서 먼 쪽) 0.8칸 · 조금 위 — 활 몸에 안긴 듯. 그쪽이 벽이면 (벽 면 · 벽에 붙은 몹을 조준) 벽 앞 0.3칸까지 (벽 속에 그려지면 보이지 않음)
  const back = Math.max(-0.3, Math.min(0.8, rayDist(dim, up(bowC, 0.25 * s), away, 1.1) - 0.3));
  const moonC = add(up(bowC, 0.25 * s), away, back);
  // 나타남: 만월 (rise 틱 동안 차오름 → final 에 번쩍 · 흩어짐) + 빛의 은활 (final 2틱 전부터 흩어짐) + 땅의 은빛 룬 + 달빛 기둥 + 소리
  moonFx(dim, moonC, moonSize, (k.final + 12) / 20, k.rise / 20, 0.6);
  bowFx(dim, bowC, s, (k.final + 8) / 20, 0.5);
  // 처음부터 시위에 메긴 은빛 화살 (꼬리가 시위, 촉이 아래 — 아래를 겨눈 활로 보이게) — 거대한 화살을 메길 때 사라짐
  arrowFx(dim, up(nock, -0.45 * s), V_DOWN, 0.45 * s, (k.final - 8) / 20);
  rune(dim, spot, { size: k.radius, life: (k.final + 6) / 20, color: glow, alpha: 0.75, rate: 25 });
  beam(dim, up(moonC, -AR_MOON_DISK * moonSize), { x: moonC.x, y: spot.y + 0.15, z: moonC.z }, AR_MOON, { half: 0.9, thick: 0.9, life: k.final / 20, gap: 1.6, alpha: 0.18 }); // 달 바로 아래 땅까지 곧게
  shock(dim, spot, glow, k.radius / RING_GROW, 0.5);
  play(dim, "beacon.activate", spot, 1.4, 1.3);
  play(dim, "chime.amethyst_block", bowC, 1.2, 0.6);
  play(dim, "random.bow", player.getHeadLocation(), 0.7, 0.6);
  /** 화살비에 맞은 적 (밀려나도 폭발에 맞음) @type {Map<string, any>} */
  const struck = new Map();
  for (let i = 0; i < k.waves; i++) {
    system.runTimeout(() => {
      if (!ok()) return;
      const targets = visibleFrom(player, rest, around(player, spot, k.radius, 3));
      const many = targets.length > 6;
      burst(dim, rest, AR_WHITE, 0.3 * s, 0.2);
      play(dim, "random.bow", bowC, 1.2, 1.2 + 0.15 * i);
      // 적마다 한 발: 줌통에서 몸으로 날아가는 빛줄기 → 3틱 뒤 꽂힘 (꽂힌 화살 · 불꽃 — 한 틱에 열둘씩, 적이 아주 많아도 한 틱 파티클 60 안)
      targets.forEach((e, j) => {
        const b = bodyAt0(e, spot);
        const d = sub(b, rest);
        const dl = len(d);
        const u = dl > 1e-3 ? unit(d) : V_DOWN;
        if (dl >= 0.6) {
          fx(dim, "rpg:arrow_shot", add(rest, u, 0.6), { dir: u, speed: Math.max(0, dl - 1.2) / 0.15, size: 0.6, thick: 0.22, life: 0.15, color: glow, alpha: 1 });
          if (!many) fx(dim, "rpg:arrow_shot", add(rest, u, 0.6), { dir: u, speed: Math.max(0, dl - 1.2) / 0.15, size: 0.55, thick: 0.08, life: 0.15, color: core, alpha: 1 });
        }
        hitAt(player, e, reserveHit(player, e, system.currentTick + 3 + Math.floor(j / 12)), ok, () => {
          if (skillHit(player, e, w, k.mult) <= 0) return;
          struck.set(e.id, e);
          const c = bodyAt0(e, b);
          arrowFx(dim, add(c, u, -0.25), u, 0.45, 0.6);
          sparks(dim, c, AR_MOON, many ? 2 : 4, 3);
          play(dim, "random.bowhit", c, 0.8, 1.0);
        });
      });
      // 빈 땅에도 넷 (화살비) — 꽂힌 화살
      for (let j = 0; j < 4; j++) {
        const a = Math.random() * Math.PI * 2;
        const r = Math.sqrt(Math.random()) * k.radius * 0.9;
        const gx = spot.x + Math.cos(a) * r;
        const gz = spot.z + Math.sin(a) * r;
        const gy = solidBelow(dim, { x: gx, y: spot.y + 1.5, z: gz }, 4) ?? spot.y;
        const tgt = { x: gx, y: gy, z: gz };
        const d = sub(tgt, rest);
        const u = unit(d);
        fx(dim, "rpg:arrow_shot", add(rest, u, 0.6), { dir: u, speed: Math.max(0, len(d) - 1.2) / 0.15, size: 0.5, thick: 0.18, life: 0.15, color: j % 2 ? glow : core, alpha: 0.9 });
        system.runTimeout(() => {
          if (ok()) arrowFx(dim, add(tgt, u, -0.3), u, 0.4, 0.8);
        }, 3);
      }
    }, k.rise + i * k.gap);
  }
  // 거대한 화살: 시위에 메김 (꼬리가 시위, 촉이 아래) → 4틱 동안 당김 (위로) → 쏨: 4틱에 땅으로 (굵은 달빛 줄기)
  const gHalf = 0.62 * s;
  const pull = 0.25 * s;
  system.runTimeout(() => {
    if (!ok()) return;
    arrowFx(dim, up(nock, -gHalf), V_DOWN, gHalf, 0.2, { move: V_UP, speed: pull / 0.2 });
    crescentFx(dim, rest, 0.35 * s, 0.4, AR_MOON, { spin: 0, rate: 400, grow: 0.6 });
    play(dim, "crossbow.loading.middle", bowC, 1.4, 0.8);
    play(dim, "beacon.power", bowC, 1.2, 1.2);
  }, k.final - 8);
  system.runTimeout(() => {
    if (!ok()) return;
    const c = up(nock, -gHalf + pull);
    const drop = c.y - (spot.y + gHalf * 0.7); // 촉의 30% 가 땅에 박힘
    arrowFx(dim, c, V_DOWN, gHalf, 0.2, { move: V_DOWN, speed: Math.max(0, drop) / 0.2 });
    beam(dim, rest, up(spot, 0.2), AR_WHITE, { half: 0.6, thick: 0.35, life: 0.3, sweep: 0.2, gap: 1.2, alpha: 0.9 });
    beam(dim, rest, up(spot, 0.2), glow, { half: 0.7, thick: 0.8, life: 0.35, sweep: 0.2, gap: 1.6, alpha: 0.6 });
    play(dim, "crossbow.shoot", bowC, 1.6, 0.7);
    play(dim, "random.bow", bowC, 1.6, 0.5);
  }, k.final - 4);
  // 꽂힘: 박힌 거대한 화살 (0.6초 → 흩어짐) + 흰 · 은빛 폭발 + 바깥으로 도는 초승달 여덟 + 고리 둘 + 룬 번쩍 + 솟는 달빛 + 흔들림 → 폭발 (적마다, 여섯씩 한 틱)
  system.runTimeout(() => {
    if (!ok()) return;
    const center = up(spot, gHalf * 0.7);
    arrowFx(dim, center, V_DOWN, gHalf, 0.6);
    system.runTimeout(() => arrowFx(dim, center, V_DOWN, gHalf, 0.4, { progress: 2 }), 12);
    burst(dim, up(spot, 1.0), AR_WHITE, 3.0, 0.35);
    burst(dim, up(spot, 1.6), glow, 4.2, 0.45);
    shock(dim, spot, glow, k.radius / 1.8, 0.5);
    shock(dim, spot, AR_WHITE, k.radius / 3.6, 0.4);
    rune(dim, spot, { size: k.radius, life: 0.6, grow: 0.25, rate: -120, color: AR_WHITE, alpha: 0.9 });
    for (let j = 0; j < 8; j++) {
      const a = (j / 8) * Math.PI * 2;
      const at = { x: spot.x + Math.cos(a) * k.radius * 0.55, y: spot.y + 1.0, z: spot.z + Math.sin(a) * k.radius * 0.55 };
      crescentFx(dim, at, 1.2, 0.4, j % 2 ? AR_WHITE : AR_MOON, { spin: j * 45, rate: j % 2 ? 420 : -420, grow: 0.8, delay: 0.03 * (j % 4) });
    }
    for (let j = 0; j < 8; j++) fx(dim, "rpg:arrow_spark", up(spot, 0.4 + Math.random()), { dir: V_UP, speed: 2 + Math.random() * 3, size: 0.16, life: 0.8, color: AR_MOON, alpha: 1 });
    play(dim, "random.explode", spot, 0.9, 1.1);
    play(dim, "firework.large_blast", spot, 1.4, 0.8);
    play(dim, "chime.amethyst_block", spot, 1.6, 0.6);
    play(dim, "firework.twinkle", spot, 1.4, 0.9);
    shakeNear(dim, spot, 14, 0.35, 0.4);
    const list = unionById(
      visibleFrom(player, up(spot, 1.2), around(player, spot, k.radius, 3)),
      [...struck.values()].filter((e) => canHit(player, e) && Math.hypot(e.location.x - spot.x, e.location.z - spot.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - spot.y) <= 5),
    );
    list.forEach((e, j) => {
      hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j / 6)), ok, () => {
        if (skillHit(player, e, w, k.afterMult) <= 0) return;
        burst(dim, bodyAt0(e, spot), AR_WHITE, 0.8, 0.22);
        if (bossLike(e) || !e.isValid) return;
        const d = Math.hypot(e.location.x - spot.x, e.location.z - spot.z) > 0.3 ? flat(sub(e.location, spot)) : away; // 가운데에 선 적 (조준한 적) 은 나에게서 먼 쪽으로
        try {
          e.applyKnockback({ x: d.x * 0.5, z: d.z * 0.5 }, 0.45);
        } catch {
          // 밀리지 않는 몹
        }
      });
    });
  }, k.final);
  return true;
}

// ------------------------------------------------------------ 간반테인 (완드) — v2.22.0 개편 (오딘의 까마귀 · 눈)
// 사용자가 고른 컨셉 "후긴과 무닌 / 오딘의 눈" · 세기 "영웅 룬~초월 룬" (Lv.40 마력 125 의 마력 룬 위력 기준) · 오딘의 눈 테두리는 모델 머리 고리 (rpg:odin_eye)
const OD_GOLD = [1.0, 0.8, 0.36]; // 금빛 (지팡이 · 까마귀 빛)
const OD_WHITE = [1.0, 0.97, 0.86]; // 흰 금빛
/** 빛의 까마귀 반 크기 (칸) — 그림 192점 = 2 × 반 크기 (날개 폭 약 160점 → 약 1.7칸) */
const OD_RAVEN = 1.0;
/** 까마귀를 그리지 않는 눈 둘레 (칸) — 1인칭 화면을 덮지 않게 */
const OD_RAVEN_NEAR = 2.5;
/** 까마귀가 돌아오는 가장 빠른 빠르기 (틱당 칸) — 멀리서 쪼고 돌아올 때 순간이동처럼 보이지 않게 돌아오는 틱을 늦춤 */
const OD_BACK_SPEED = 1.6;
/** 돌아오는 길의 가장 긴 거리 (칸) — 내가 멀리 순간이동하면 그만큼 떨어진 곳에서 돌아옴 (쫓는 적은 나에게서 range + jump 칸 안이라 보통은 닿지 않음) */
const OD_HOME_FAR = 40;
/** 오딘의 눈 그림의 위아래 끝 (반 크기 = 1 — build.py odin_eye_frame: 256점 그림에서 고리 위 뾰족 · 아래 받침이 가운데에서 104점) · 고리 바깥 반지름 (88점) */
const OD_EYE_TOP = 104 / 128;
const OD_RING = 88 / 128;
/** 오딘의 눈은 그곳 위에서 내 왼쪽으로 이만큼 (칸) — 1인칭에서 오른손에 든 긴 지팡이의 머리가 화면 가운데 위를 가리므로 */
const OD_EYE_SIDE = 2.5;

/** 빛의 까마귀 한 장 (rpg:odin_raven — 머리 방향 dir 을 따라 눕고 카메라를 봄): vel = 초당 움직임 (다음 틱 자리로 — 틱마다 다시 그려도 매끄럽게), phase = 날갯짓 장의 시작 */
function ravenFx(dim, at, dir, vel, phase, size = OD_RAVEN, life = 0.075) {
  try {
    const sp = len(vel);
    const m = vars({ dir, size, life, speed: sp, spin: phase, color: [1, 1, 1], alpha: 1 });
    m.setVector3("variable.move", sp > 1e-6 ? unit(vel) : dir);
    dim.spawnParticle("rpg:odin_raven", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 안수즈 룬 표식 (rpg:odin_mark — 보는 사람을 향함): size 반 크기, life 초, o.grow 커지는 몫 */
function odinMarkFx(dim, at, size, life, o = {}) {
  fx(dim, "rpg:odin_mark", at, { size, life, grow: o.grow ?? 0, color: o.color ?? [1, 1, 1], alpha: o.alpha ?? 1 });
}

/** 눈에서 d 칸 떨어진 곳에 그릴 반 크기: 가까우면 작게 (반 크기 ≤ 거리 × k — 1인칭 화면을 덮지 않게), near 칸 안이면 그리지 않음 (0) */
function nearSize(size, d, k = 0.2, near = 1.2) {
  return d < near ? 0 : Math.min(size, k * d);
}

/** 오딘의 눈 (rpg:odin_eye — 보는 사람을 향함): 처음 wax 초 동안 뜸, close 초부터 fade 초 동안 감음, 감은 뒤 번쩍이며 남은 수명 동안 사라짐 */
function odinEyeFx(dim, at, size, life, wax, close, fade) {
  try {
    const m = vars({ size, life, color: [1, 1, 1], alpha: 1 });
    m.setFloat("variable.wax", wax);
    m.setFloat("variable.close", close);
    m.setFloat("variable.fade", fade);
    dim.spawnParticle("rpg:odin_eye", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 내적 */
const dot3 = (a, b) => a.x * b.x + a.y * b.y + a.z * b.z;

/** 2차 베지에 곡선 위의 점 · 기울기 (p0 → 조절점 c → p1, v 0~1) */
function bezAt(p0, c, p1, v) {
  const a = (1 - v) * (1 - v);
  const b = 2 * (1 - v) * v;
  const d = v * v;
  return { x: a * p0.x + b * c.x + d * p1.x, y: a * p0.y + b * c.y + d * p1.y, z: a * p0.z + b * c.z + d * p1.z };
}
function bezTan(p0, c, p1, v) {
  return { x: 2 * (1 - v) * (c.x - p0.x) + 2 * v * (p1.x - c.x), y: 2 * (1 - v) * (c.y - p0.y) + 2 * v * (p1.y - c.y), z: 2 * (1 - v) * (c.z - p0.z) + 2 * v * (p1.z - c.z) };
}

/**
 * 까마귀가 한 구간을 나는 길의 조절점 (o = 구간): 가운데에서 위로 · 옆으로 (후긴 sgn +1 · 무닌 -1 — 서로 엇갈림). 같은 적을 다시 쪼면 (1칸 안) 높이 솟았다가 내리꽂힘 —
 * 그 옆은 구간을 시작할 때의 내 오른쪽 (o.loopSide — 화면에서 옆으로 도는 고리). 돌아오는 구간 (o.home) 은 낮고 완만하게.
 * 곡선의 가장 높은 곳이 o.top (천장 아래 높이) 을 넘지 않게 조절점을 낮춤 — 2차 곡선 (끝 높이 a · b, 조절점 c) 의 꼭대기가 M 이 되는 c = M + √((M − a)(M − b))
 */
function ravenCtrl(p0, p1, sgn, o = {}) {
  const d = sub(p1, p0);
  const L = len(d);
  const hz = Math.hypot(d.x, d.z);
  const loopSide = o.loopSide ?? { x: 1, y: 0, z: 0 };
  let c;
  if (L < 1.0 && !o.home) c = add(up(p1, 4.0), loopSide, 3.0 * sgn);
  else {
    const side = hz > 0.5 ? { x: -d.z / hz, y: 0, z: d.x / hz } : loopSide;
    const rise = o.home ? 0.4 + 0.06 * L : 1.0 + 0.18 * L;
    const lat = o.home ? 0.3 + 0.08 * L : 0.6 + 0.22 * L;
    c = add(up(add(p0, d, 0.5), rise), side, lat * sgn);
  }
  const M = o.top ?? Infinity;
  if (M === Infinity) return c;
  const hiEnd = Math.max(p0.y, p1.y);
  const cMax = M >= hiEnd ? M + Math.sqrt((M - p0.y) * (M - p1.y)) : hiEnd;
  return c.y > cMax ? { x: c.x, y: cMax, z: c.z } : c;
}

/** 까마귀가 쪼은 자리: 금빛 X 번쩍임 + 깃털 (눈 가까이는 작게 — 1.5칸 안이면 깃털만) */
function peckFx(dim, c, eye, glow) {
  const de = len(sub(c, eye));
  if (de < 1.5) {
    sparks(dim, c, OD_GOLD, 3, 4);
    return;
  }
  const near = Math.min(1, Math.max(0.45, (de - 0.8) / 2.5));
  const dir = unit(sub(c, eye));
  fx(dim, "rpg:slash_x", c, { dir, size: 0.9 * near, life: 0.22, color: glow, alpha: 1 });
  fx(dim, "rpg:slash_x", add(c, dir, -0.04), { dir, size: 0.6 * near, life: 0.18, color: OD_WHITE, alpha: 1 });
  sparks(dim, c, OD_GOLD, 4, 4);
}

/**
 * 후긴과 무닌 (v2.22.0): 조준한 적 (range 칸 — 시선에 겹친 몹, 없으면 조준 보정) + 그 적에서 jump 칸 안의 적 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비 — 그 적의 몸에서 보이는,
 * 가까운 순) targets 명까지. 지팡이 앞 마법진에서 빛의 까마귀 둘 (후긴 · 무닌) 이 날아올라 번갈아 hits 번 쪼음 (mult — 적을 돌아가며).
 * 까마귀는 쪼고 나면 다음 쪼음을 잡음: 덜 쪼인 적 (같으면 처음 고른 차례) — 그 틱은 날아갈 시간 (첫 쪼음은 날아오름 launch 틱 + 거리, 그 뒤는 6틱 이상) ·
 * 다른 쪼음에서 gap 틱 · 같은 적은 0.5초 (몹 무적 시간 — 피해가 미뤄지지 않게) 뒤. 곡선으로 날아 쪼는 틱에 그 몸에 닿음 (적이 움직이면 따라감 · 한 틱에 튀면 지금 자리에서 다시 ·
 * 천장이 낮으면 낮게) — 나는 동안 그 적이 쓰러지거나 나에게서 range + jump 칸 밖으로 달아나면 덜 쪼인 다른 적에게로 꺾음 (틱을 다시 잡음). 쪼인 적에게 안수즈 룬 표식 →
 * 쪼음을 다 하면 (남은 적이 없으면 바로) 까마귀가 내 앞으로 돌아와 (마지막 쪼음 back 틱 뒤 이상 · 틱당 OD_BACK_SPEED 칸 이하로) 빛으로 흩어지며
 * 표식이 터짐 (afterMult — 쪼인 적마다 한 번, 넷씩 한 틱 — 놓친 적은 아님). 대상이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 없음.
 * 까마귀 그림은 눈 2.5칸 안이면 그리지 않음, 표식 · 터짐은 가까우면 작게
 */
function odinRavens(player, w, k) {
  const dim = player.dimension;
  let first;
  try {
    const r = aimTarget(player, k.range, WAND.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) first = r.target;
  } catch {
    first = undefined;
  }
  if (!first) {
    warn(player, `§c${k.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow] = colors(w);
  const fb = bodyAt0(first, first.location);
  const others = visibleFrom(player, fb, around(player, first.location, k.jump, 4))
    .filter((e) => e.id !== first.id && hasFamily(e, RUSH_FAMILIES) && !RUSH_NEUTRAL.has(e.typeId))
    .sort((a, b) => len(sub(a.location, first.location)) - len(sub(b.location, first.location)))
    .slice(0, Math.max(0, k.targets - 1));
  const targets = [first, ...others];
  const N = targets.length;
  const { at: launch } = wandCircleAt(player);
  const leash = k.range + k.jump;
  /** 적마다 마지막으로 본 몸 */
  const seen = targets.map((e) => bodyAt0(e, e.location));
  /** 그 적의 지금 몸 (사라졌으면 마지막으로 본 자리) */
  const body = (i) => {
    const e = targets[i];
    if (e?.isValid) seen[i] = bodyAt0(e, seen[i]);
    return seen[i];
  };
  /** 쫓을 수 있는 적: 맞을 수 있고 · 같은 차원 · 나에게서 leash 칸 안 (멀리 달아나거나 날아가면 놓침) */
  const alive = (i) => {
    const e = targets[i];
    if (i < 0 || !canHit(player, e)) return false;
    try {
      return e.dimension.id === dim.id && len(sub(e.location, player.location)) <= leash;
    } catch {
      return false;
    }
  };
  /** 적마다 쪼은 수 (쪼러 가는 중 포함) · 마지막으로 쪼은 틱 */
  const pecked = targets.map(() => 0);
  const doneOn = targets.map(() => -100);
  let issued = 0; // 잡은 쪼음 수 (hits 면 더 잡지 않음 — 꺾은 쪼음은 그대로 하나)
  let lastPeck = -100; // 마지막으로 쪼은 틱
  let T = Infinity; // 돌아와 흩어지는 틱 (쪼음을 다 잡으면 정함)
  /** 쪼인 적: id → {i (적 번호), n (쪼인 수)} */
  const marked = new Map();
  /** 돌아올 자리의 높이: 눈높이에서 1칸 위 (낮은 굴이면 쓸 때 앞 천장 아래 0.6칸까지) */
  const lift = Math.max(0, Math.min(1.0, skyRoom(dim, add(player.getHeadLocation(), facingOf(player), 3.2), 3) - 0.6));
  /** 돌아올 자리: 내 앞 3.2칸 · lift 위, 후긴은 왼쪽 · 무닌은 오른쪽 (1인칭 화면 위쪽 양옆 — 가까이 오지 않게) */
  const home = (rv) => {
    const eye = player.getHeadLocation();
    const f = facingOf(player);
    return up(add(add(eye, f, 3.2), rightOf(f), rv === 0 ? -1.3 : 1.3), lift);
  };
  /**
   * 까마귀 둘 (rv 0 후긴 · 1 무닌). leg = 지금 나는 구간 {t0 → t1 틱, p0 에서, idx 적의 몸으로 (-1 은 내 앞으로 돌아옴), top · loopSide (ravenCtrl), steady (고른 빠르기),
   * end (지난 틱에 잰 끝 자리 — 한 틱에 튀었는지 보려고)}
   * @type {{rv: number, sgn: number, leg: any, dir: any}[]}
   */
  const ravens = [0, 1].map((rv) => ({ rv, sgn: rv === 0 ? 1 : -1, leg: undefined, dir: undefined }));
  /** 구간의 끝 자리: 그 적의 지금 몸 (따라감), 돌아오는 구간은 내 앞 */
  const endOf = (r) => (r.leg.idx >= 0 ? body(r.leg.idx) : home(r.rv));
  /** t 틱에 그 구간 위의 자리 · 다음 틱 자리 · 나는 방향 (끝으로 갈수록 빨라짐 — 돌아오는 중에 다시 잡은 구간 (steady) 은 고른 빠르기) */
  const flight = (r, t, p1 = endOf(r)) => {
    const g = r.leg;
    const c = ravenCtrl(g.p0, p1, r.sgn, g);
    const span = Math.max(1, g.t1 - g.t0);
    const ease = (u) => (g.steady ? u : Math.pow(u, 1.35));
    const v = ease(Math.min(1, Math.max(0, (t - g.t0) / span)));
    const v2 = ease(Math.min(1, Math.max(0, (t + 1 - g.t0) / span)));
    const tan = bezTan(g.p0, c, p1, Math.min(0.999, v));
    const chord = sub(p1, g.p0);
    return { at: bezAt(g.p0, c, p1, v), next: bezAt(g.p0, c, p1, v2), dir: len(tan) > 1e-4 ? unit(tan) : len(chord) > 1e-4 ? unit(chord) : V_UP };
  };
  /**
   * r 이 t 틱에 p0 에서 t1 틱까지 idx 적 (-1 은 내 앞) 으로 나는 구간 — 곡선은 그 가운데 위 천장의 0.6칸 아래까지 (천장에 박히지 않게 · 트였으면 끝없음).
   * 돌아오는 길은 OD_HOME_FAR 칸까지 (내가 멀리 순간이동했으면 그만큼 떨어진 곳에서)
   */
  const setLeg = (r, t, p0, t1, idx) => {
    const p1 = idx >= 0 ? body(idx) : home(r.rv);
    const far = sub(p0, p1);
    if (idx < 0 && len(far) > OD_HOME_FAR) p0 = add(p1, unit(far), OD_HOME_FAR);
    const mid = len(sub(p1, p0)) < 1.0 ? p1 : add(p0, sub(p1, p0), 0.5);
    const room = skyRoom(dim, mid, 8);
    r.leg = { t0: t, t1: Math.max(t + 1, t1), p0, idx, home: idx < 0, top: room >= 8 ? Infinity : mid.y + room - 0.6, loopSide: rightOf(facingOf(player)), end: p1 };
  };
  /** 다른 까마귀 (but 말고) 가 쪼러 가는 틱 중 가장 늦은 것 (i 적 — i < 0 이면 어느 적이든). 꺾어서 버린 쪼음은 셈하지 않음 */
  const pendingMax = (but, i) => {
    let m = -100;
    for (const r of ravens) if (r !== but && r.leg && r.leg.idx >= 0 && (i < 0 || r.leg.idx === i)) m = Math.max(m, r.leg.t1);
    return m;
  };
  /** 덜 쪼인 적 먼저 (같으면 from 에서 가까운 적) — 쫓을 적이 없으면 -1 */
  const pick = (from) => {
    let best = -1;
    let key = Infinity;
    for (let j = 0; j < N; j++) {
      if (!alive(j)) continue;
      const kj = pecked[j] * 1000 + len(sub(body(j), from));
      if (kj < key) {
        best = j;
        key = kj;
      }
    }
    return best;
  };
  /**
   * 돌아오는 틱 T (쪼음을 다 잡았을 때): 마지막 쪼음 back 틱 뒤 이상, 까마귀마다 (쪼는 틱 또는 지금) + 내 앞까지 거리 / OD_BACK_SPEED 이상.
   * 돌아오는 중인 까마귀는 지금 자리에서 T 에 닿게 다시 (T 가 바뀌어도 이어지게)
   */
  const planT = (t) => {
    if (issued < k.hits) return;
    let want = Math.max(t + 1, lastPeck + k.back);
    for (const r of ravens) {
      const g = r.leg;
      if (!g) continue;
      const from = g.idx >= 0 ? body(g.idx) : flight(r, t).at;
      want = Math.max(want, (g.idx >= 0 ? g.t1 : t) + Math.ceil(Math.min(OD_HOME_FAR, len(sub(from, home(r.rv)))) / OD_BACK_SPEED));
      if (g.idx >= 0) want = Math.max(want, g.t1 + k.back);
    }
    T = want;
    for (const r of ravens) {
      if (!r.leg || r.leg.idx >= 0 || r.leg.t1 === T) continue;
      const moving = r.leg.t0 < t; // 이미 돌아오는 중: 멈칫하지 않게 (처음부터 다시 빨라지지 않고) 고른 빠르기로 이어서
      setLeg(r, t, flight(r, t).at, T, -1);
      r.leg.steady = moving;
    }
  };
  /** 쫓을 적이 없음: 남은 쪼음은 없이 돌아옴 */
  const goHome = (r, t, from) => {
    issued = k.hits;
    setLeg(r, t, from, t + 1, -1);
    planT(t);
  };
  /** j 적을 쪼는 구간을 잡음 (t1 틱 — 정수 틱으로: 설정이 소수여도 쪼는 틱을 지나치지 않게) */
  const aimAt = (r, t, from, j, t1) => {
    pecked[j]++;
    setLeg(r, t, from, Math.ceil(t1), j);
  };
  /**
   * r 이 t 틱에 from 에서 다음 쪼음을 잡음: 덜 쪼인 적 (같으면 처음 고른 차례 — 조준한 적부터 가까운 순, 꺾은 쪼음도 셈).
   * 틱 = ready(나는 틱 수) · 다른 쪼음 (한 것 · 다른 까마귀가 쪼러 가는 것) 에서 gap 틱 · 같은 적은 IFRAMES 틱 뒤. 쪼음을 다 잡았거나 쫓을 적이 없으면 돌아옴
   */
  const issue = (r, t, from, ready) => {
    let i = -1;
    if (issued < k.hits) for (let j = 0; j < N; j++) if (alive(j) && (i < 0 || pecked[j] < pecked[i])) i = j;
    if (i < 0) return goHome(r, t, from);
    issued++;
    aimAt(r, t, from, i, Math.max(ready(Math.ceil(len(sub(body(i), from)) / k.speed)), Math.max(lastPeck, pendingMax(r, -1)) + k.gap, Math.max(doneOn[i], pendingMax(r, i)) + IFRAMES));
    planT(t);
  };
  /** 나는 동안 그 적이 쓰러지거나 달아남: 지금 자리에서 덜 쪼인 다른 적에게로 꺾음 (틱을 다시 잡음 — 버린 쪼음은 셈하지 않음) · 쫓을 적이 없으면 돌아옴 */
  const retarget = (r, t) => {
    const from = flight(r, t).at;
    pecked[r.leg.idx] = Math.max(0, pecked[r.leg.idx] - 1);
    const j = pick(from);
    if (j < 0) return goHome(r, t, from);
    aimAt(r, t, from, j, Math.max(t + Math.max(4, Math.ceil(len(sub(body(j), from)) / k.speed)), Math.max(doneOn[j], pendingMax(r, j)) + IFRAMES));
    planT(t);
  };
  /** 쪼음: 그 몸에 닿음 — 금빛 X · 표식 · 피해 → 다음 쪼음 */
  const strike = (r, t, eye) => {
    const i = r.leg.idx;
    if (!alive(i)) return retarget(r, t); // 이 틱에 먼저 쪼인 피해 등으로 쓰러짐
    const e = targets[i];
    const at = body(i);
    lastPeck = t;
    doneOn[i] = t;
    peckFx(dim, at, eye, glow);
    play(dim, "mob.phantom.bite", at, 1.0, 1.3);
    const m = marked.get(e.id) ?? { i, n: 0 };
    m.n++;
    marked.set(e.id, m);
    const ms = nearSize(0.32 + 0.06 * m.n, len(sub(at, eye)));
    if (ms > 0) odinMarkFx(dim, at, ms, 0.3, { alpha: 0.95 });
    hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => skillHit(player, e, w, k.mult));
    issue(r, t, at, (fly) => t + Math.max(6, fly));
  };
  /** 까마귀 그리기 (눈 2.5칸 안은 빼고) + 지나간 자리의 금빛 알갱이 */
  const draw = (r, t, eye, view) => {
    const { at, next, dir } = flight(r, t);
    const de = len(sub(at, eye));
    if (de < OD_RAVEN_NEAR) return;
    // 그림 방향: 나는 쪽을 내 화면 면에 눕힘 (나는 쪽 그대로면 멀어지거나 다가올 때 날개 끝만 보이는 얇은 줄이 됨)
    const dv = dot3(dir, view);
    const sd = sub(dir, { x: view.x * dv, y: view.y * dv, z: view.z * dv });
    const sdir = len(sd) > 0.2 ? unit(sd) : (r.dir ?? screenDir(view, 0));
    r.dir = sdir;
    // 가까이 오면 작게 (3.6칸 안 — 돌아올 때 1인칭 화면 위쪽을 크게 덮지 않게)
    ravenFx(dim, at, sdir, { x: (next.x - at.x) * 20, y: (next.y - at.y) * 20, z: (next.z - at.z) * 20 }, t * 0.8 + r.rv * 3, OD_RAVEN * Math.min(1, Math.max(0.55, (de - 1.5) / 4)));
    fx(dim, "rpg:arrow_spark", add(at, dir, -0.6), { dir, speed: 0.3, size: 0.1, life: 0.35, color: OD_GOLD, alpha: 0.9 });
  };
  /** 돌아옴: 까마귀가 내 앞에서 빛으로 흩어짐 → 표식이 터짐 (넷씩 한 틱 — 가까우면 작게) */
  const scatter = (eye) => {
    for (const r of ravens) {
      const h = home(r.rv);
      burst(dim, h, glow, 0.35, 0.22);
      sparks(dim, h, OD_GOLD, 4, 3);
    }
    play(dim, "mob.phantom.flap", eye, 1.2, 0.9);
    play(dim, "beacon.power", eye, 0.8, 1.5);
    let j = 0;
    for (const m of marked.values()) {
      if (!alive(m.i)) continue;
      const e = targets[m.i];
      hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j++ / 4)), ok, () => {
        if (skillHit(player, e, w, k.afterMult) <= 0) return;
        const c = bodyAt0(e, seen[m.i]);
        const dc = len(sub(c, player.getHeadLocation()));
        const ms = nearSize(0.7, dc, 0.16, 1.5); // 1.9배로 커짐 — 다 커져도 거리 × 0.3 안
        if (ms > 0) odinMarkFx(dim, c, ms, 0.3, { grow: 0.9 });
        const bs = nearSize(1.0, dc, 0.3, 1.0);
        if (bs > 0) burst(dim, c, glow, bs, 0.25);
        const th = Math.min(1, Math.max(0.3, (dc - 0.8) / 3));
        streakLine(dim, up(c, 3.2), c, OD_WHITE, 0.14 * th, 0.2);
        streakLine(dim, up(c, 3.2), c, glow, 0.38 * th, 0.25, 0.85);
        sparks(dim, c, OD_GOLD, 4, 5);
        play(dim, "item.trident.thunder", c, 0.8, 1.5);
        play(dim, "firework.blast", c, 1.0, 1.2);
      });
    }
  };
  let done = false;
  /** @type {number | undefined} */
  let runId;
  const finish = () => {
    done = true;
    if (runId !== undefined) system.clearRun(runId);
  };
  const step = (t) => {
    if (!ok() || t > 600) return finish();
    const eye = player.getHeadLocation();
    const view = player.getViewDirection();
    // 끝 자리가 한 틱에 2칸 넘게 옮겨짐 (그 적이 순간이동 · 날려 감, 내가 순간이동 · 확 돌아섬): 옮겨지기 전 끝으로 잰 지금 자리에서 다시 날아감 (까마귀가 튀지 않게)
    for (const r of ravens) {
      const g = r.leg;
      const e1 = endOf(r);
      if (len(sub(e1, g.end)) > 2) {
        setLeg(r, t, flight(r, t, g.end).at, g.t1, g.idx);
        r.leg.steady = g.steady || g.idx < 0;
      }
      r.leg.end = e1;
    }
    // 쫓던 적을 놓침 (쓰러짐 · 달아남 · 다른 차원) → 다른 적에게로 꺾거나 돌아옴
    for (const r of ravens) if (r.leg.idx >= 0 && !alive(r.leg.idx)) retarget(r, t);
    // 쪼음: 이 틱에 끝나는 구간 (그 몸에 닿음) → 다음 쪼음을 잡음
    for (const r of ravens) if (r.leg.idx >= 0 && r.leg.t1 <= t) strike(r, t, eye);
    for (const r of ravens) draw(r, t, eye, view);
    // 쪼인 적의 표식 (3틱마다 지금 몸에 다시 — 걸어가도 따라감, 겹쳐 그려 깜빡이지 않게)
    if (t % 3 === 0 && t < T) {
      for (const m of marked.values()) {
        if (!alive(m.i)) continue;
        const at = body(m.i);
        const ms = nearSize(0.32 + 0.06 * m.n, len(sub(at, eye)));
        if (ms > 0) odinMarkFx(dim, at, ms, 0.3, { alpha: 0.9 });
      }
    }
    if (t < T) return;
    scatter(eye);
    finish();
  };
  for (const r of ravens) issue(r, 0, launch, (fly) => k.launch + fly);
  // 나타남: 지팡이 앞 마법진 번쩍임 · 금빛 불꽃 · 날갯짓 소리
  fx(dim, "rpg:magic_circle_flash", launch, { size: 0.3, life: 0.25, spin: Math.random() * 360, color: glow, alpha: 1 }); // 평타 시전 마법진 (반 크기 0.45) 보다 작게
  sparks(dim, launch, OD_GOLD, 6, 3);
  play(dim, "mob.phantom.flap", launch, 1.4, 1.2);
  play(dim, "mob.evocation_illager.cast_spell", launch, 1.0, 1.3);
  step(0);
  let t = 0;
  if (!done) runId = system.runInterval(() => step(++t), 1);
  return true;
}

/**
 * 오딘의 눈 (v2.22.0): 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘 (그곳 위에서 내 왼쪽으로 2.5칸 — 1인칭에서 오른손의 지팡이 머리에 가리지 않게)
 * 에 지팡이 고리 테두리의 거대한 금빛 눈 (모델 머리 고리 — 보는 사람을 향함) + 땅의 금빛 룬 + 빛 기둥. 눈의 크기 · 높이는 가로 거리에 맞춤 (위끝이 눈높이에서 26° 위까지), 굴 · 지붕 · 나뭇잎 아래면 트인 높이에 맞게 작고 낮게,
 * 옆에 벽이 있으면 고리가 벽에 묻히지 않게 덜 옮기거나 작게 (옮긴 자리 위가 막혔으면 그곳 위 그대로) →
 * open 틱 동안 뜸 → gap 틱마다 waves 번 광선이 반지름 radius 칸의 적 (눈에서 보이는) 을 둘레를 따라 차례로 훑음 (mult — 한 번에 적마다 한 번, 같은 적은 0.5초 간격) →
 * final 6틱 전부터 감기며 final 틱에 대폭발 (afterMult, 밀쳐 띄움 — 보스는 밀지 않음, 가운데에 선 적은 나에게서 먼 쪽으로, 광선에 맞은 적은 밀려났어도 radius + 2.5칸 안이면).
 * 땅을 조준하지 않았거나 (허공) · 그 땅이 눈에서 보이지 않거나 (천장 · 지붕 위) · min 칸 앞으로 밀 수 없으면 (벽 · 나무) 재사용 대기 없이 거절.
 * 쓰러지거나 차원을 옮기면 남은 광선 · 폭발은 없음 (이미 그린 눈은 그대로 감김)
 */
function odinEye(player, w, k) {
  const dim = player.dimension;
  const spot = fallSpot(player, k);
  const eye = player.getHeadLocation();
  if (!spot || (!lineOfSight(player, eye, up(spot, 1.2)) && !lineOfSight(player, eye, up(spot, 2.0)))) {
    warn(player, `§c${k.name}: §7땅을 조준해야 합니다 (${k.range}칸 안)`);
    return false;
  }
  const pl = player.location;
  const hd = Math.hypot(spot.x - pl.x, spot.z - pl.z);
  if (hd < k.min - 0.5) {
    warn(player, `§c${k.name}: §7${k.min}칸 밖의 땅을 조준해야 합니다`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const [glow] = colors(w);
  const away = hd > 0.5 ? flat(sub(spot, pl)) : facingOf(player);
  const left = { x: away.z, y: 0, z: -away.x };
  // 크기 · 높이: 트인 높이 room (나뭇잎도 막힘) 안에서 (아래 끝 땅 위 1.2칸 ~ 위끝 천장 아래 0.2칸 — 낮은 굴이면 작게, 그래도 모자라면 천장 쪽을 지킴).
  // 위끝이 눈높이에서 26° 위 (tan 26° = 0.49) 를 넘지 않게 — 가까우면 작고 낮게
  const sizeIn = (room) => Math.max(0.6, Math.min(2.8, (room - 1.4) / (OD_EYE_TOP * 2), Math.max(1.8, hd * 0.32)));
  const highIn = (s, room) => Math.min(room - OD_EYE_TOP * s - 0.2, Math.max(OD_EYE_TOP * s + 1.2, Math.min(7, 1.62 + hd * 0.49 - OD_EYE_TOP * s)));
  const room0 = skyRoom(dim, up(spot, 0.2), 12, false) + 0.2;
  let s = sizeIn(room0); // 눈 반 크기
  // 옆: 내 왼쪽으로 OD_EYE_SIDE 칸. 눈 높이 (가운데 · 고리 반지름의 절반 위아래) 에서 양옆 벽까지 재어 고리 (반지름 OD_RING × s, 0.2칸 여유) 가 벽에 묻히지 않게 —
  // 왼쪽 벽이 가까우면 덜 옮기고, 그래도 들어가지 않으면 (그곳 바로 옆 벽 · 좁은 굴) 눈을 줄임 (가장 작아도 안 되면 두 벽 가운데)
  const h0 = highIn(s, room0);
  const hs = [h0 - OD_RING * s * 0.5, h0, h0 + OD_RING * s * 0.5].map((h) => Math.max(0.6, Math.min(room0 - 0.3, h)));
  const openTo = (d) => Math.min(...hs.map((h) => rayDist(dim, up(spot, h), d, OD_EYE_SIDE + 3.2)));
  const dL = openTo(left);
  const dR = openTo({ x: -left.x, y: 0, z: -left.z });
  s = Math.max(0.6, Math.min(s, (Math.min(dL, (dL + dR) / 2, dR + OD_EYE_SIDE) - 0.2) / OD_RING));
  const ring = OD_RING * s + 0.2;
  const hi = Math.min(OD_EYE_SIDE, dL - ring);
  let side = hi >= ring - dR ? hi : (dL - dR) / 2;
  // 옮긴 자리 위가 막혔으면 (처마 · 나뭇가지 — 그곳 위보다 낮음) 그곳 위 그대로 (눈이 처마 밑에 작아지지 않게)
  let room = room0;
  if (side > 0.05) {
    const roomCol = skyRoom(dim, up(add(spot, left, side), 0.2), 12, false) + 0.2;
    if (roomCol < room0 - 0.5) {
      side = 0;
      s = Math.max(0.6, Math.min(s, (Math.min(dL, dR) - 0.2) / OD_RING));
    } else room = Math.min(room0, roomCol);
  }
  s = Math.min(s, sizeIn(room));
  const col = add(spot, left, side);
  const eyeH = highIn(s, room);
  const eyeC = up(col, eyeH);
  const close = k.final - 6;
  odinEyeFx(dim, eyeC, s, (k.final + 10) / 20, k.open / 20, close / 20, 6 / 20);
  rune(dim, spot, { size: k.radius, life: (k.final + 6) / 20, color: glow, alpha: 0.75, rate: 20 });
  beam(dim, up(eyeC, -OD_RING * s), up(col, 0.15), OD_GOLD, { half: 0.9, thick: 0.9, life: k.final / 20, gap: 1.6, alpha: 0.16 }); // 눈 아래 땅까지 곧게 내리는 옅은 빛
  shock(dim, spot, glow, k.radius / RING_GROW, 0.5);
  play(dim, "beacon.activate", spot, 1.4, 0.9);
  play(dim, "mob.evocation_illager.prepare_summon", eyeC, 1.2, 0.8);
  /** 광선에 맞은 적 (밀려나도 폭발에 맞음) @type {Map<string, any>} */
  const struck = new Map();
  /** 이번 물결의 적 (맞는 순) · 그 몸 자리 (사라지면 마지막으로 본 자리) · 맞는 틱 (물결 시작에서 — 잡아 둔 틱) */
  let wave = [];
  /** @type {any[]} */
  let wavePos = [];
  /** @type {number[]} */
  let waveAt = [];
  const bodyOf = (j) => (wavePos[j] = bodyAt0(wave[j], wavePos[j]));
  /** 적마다 훑는 차례: 처음 본 물결의 둘레 차례 (그 뒤에 들어온 적은 뒤에) — 물결마다 다시 재지 않아 0° 줄을 넘어간 적이 차례를 바꿔 무적 시간에 밀리지 않게 */
  const rank = new Map();
  const order = (list) => {
    const key = (e) => {
      const a = Math.atan2(e.location.z - spot.z, e.location.x - spot.x);
      return (a + Math.PI * 2) % (Math.PI * 2);
    };
    for (const e of list.filter((x) => !rank.has(x.id)).sort((a, b) => key(a) - key(b))) rank.set(e.id, rank.size);
    return list.sort((a, b) => rank.get(a.id) - rank.get(b.id));
  };
  /** 한 틱에 맞는 적이 많으면 (몰려 있음 · 미뤄진 타격) 그림은 앞의 여섯까지 · 소리는 둘까지 */
  let fxTick = -1;
  let fxN = 0;
  const fxSlot = () => {
    if (system.currentTick !== fxTick) {
      fxTick = system.currentTick;
      fxN = 0;
    }
    return ++fxN;
  };
  /** 이번 물결에서 j 번째 적을 맞히는 틱 (물결 시작에서) */
  const hitOff = (j, n) => Math.floor((j * k.gap) / Math.max(1, n));
  /**
   * 광선 발: 맞는 틱 (잡아 둔 틱 — 무적 시간에 밀린 적은 그만큼 뒤) 에 그 적의 몸에 있고 그 사이는 다음 적으로 미끄러짐 (마지막 적 뒤에는 이 물결의 첫 적 쪽으로) —
   * 적이 없으면 둘레의 땅을 돎 (비탈이면 그 높이), 하나면 그 몸 둘레를 작게
   */
  const footAt = (i, t) => {
    const n = wave.length;
    if (n === 0) {
      const a = (i + t / k.gap) * Math.PI * 2;
      const gx = spot.x + Math.cos(a) * k.radius * 0.55;
      const gz = spot.z + Math.sin(a) * k.radius * 0.55;
      return { x: gx, y: (solidBelow(dim, { x: gx, y: spot.y + 2, z: gz }, 5) ?? spot.y) + 0.2, z: gz };
    }
    if (n === 1) {
      const c = bodyOf(0);
      const a = (t / k.gap) * Math.PI * 2;
      const r = 0.35 * Math.abs(Math.sin(((t - waveAt[0]) / k.gap) * Math.PI)); // 맞는 틱에는 그 몸에
      return { x: c.x + Math.cos(a) * r, y: c.y, z: c.z + Math.sin(a) * r };
    }
    if (t <= waveAt[0]) return bodyOf(0);
    let j = 0;
    while (j + 1 < n && waveAt[j + 1] <= t) j++;
    const t0 = waveAt[j];
    const t1 = j + 1 < n ? waveAt[j + 1] : Math.max(k.gap, t0 + 1);
    const a = bodyOf(j);
    const b = bodyOf((j + 1) % n);
    return add(a, sub(b, a), Math.min(1, (t - t0) / (t1 - t0)));
  };
  /** 눈동자에서 to 까지 빛줄기 (금빛 · 흰 심지): 내 눈 2칸 안을 지나는 부분은 빼고, 가까이 지나면 가늘게 — 둘레 안의 가까운 적을 비출 때 1인칭 화면을 덮지 않게 */
  const ray = (to, glowT, coreT, life, coreLife = life, glowAlpha = 0.85) => {
    const me = player.getHeadLocation();
    const d = sub(to, eyeC);
    const L = len(d);
    const u = L > 1e-6 ? unit(d) : V_DOWN;
    const tc = Math.max(0, Math.min(L, dot3(sub(me, eyeC), u)));
    const th = Math.min(1, Math.max(0.3, (len(sub(me, add(eyeC, u, tc))) - 0.8) / 3));
    streakAway(dim, me, eyeC, to, glow, glowT * th, life, glowAlpha, 2.0);
    streakAway(dim, me, eyeC, to, OD_WHITE, coreT * th, coreLife, 1, 2.0);
    return me;
  };
  for (let i = 0; i < k.waves; i++) {
    const t0 = k.open + i * k.gap;
    system.runTimeout(() => {
      if (!ok()) return;
      const list = order(visibleFrom(player, eyeC, around(player, spot, k.radius, 3)));
      const n = list.length;
      const base = system.currentTick;
      // 맞는 틱을 먼저 잡고 (무적 시간에 밀린 적은 그만큼 뒤로) 그 순서로 — 광선 발이 맞는 틱에 그 몸에 있게
      const at = list.map((e, j) => reserveHit(player, e, base + hitOff(j, n)) - base);
      const idx = list.map((_, j) => j).sort((a, b) => at[a] - at[b] || a - b);
      wave = idx.map((j) => list[j]);
      waveAt = idx.map((j) => at[j]);
      wavePos = wave.map((e) => bodyAt0(e, spot));
      play(dim, "beacon.power", eyeC, 1.2, 1.4 + 0.1 * i);
      wave.forEach((e, j) => {
        hitAt(player, e, base + waveAt[j], ok, () => {
          if (skillHit(player, e, w, k.mult) <= 0) return;
          struck.set(e.id, e);
          const slot = fxSlot();
          if (slot > 6) return;
          const c = bodyAt0(e, spot);
          const dc = len(sub(c, player.getHeadLocation()));
          const bs = nearSize(0.75, dc, 0.3, 1.0);
          if (bs > 0) burst(dim, c, glow, bs, 0.2);
          const ms = nearSize(0.42, dc, 0.14, 1.5); // 1.4배로 커짐
          if (ms > 0) odinMarkFx(dim, c, ms, 0.35, { grow: 0.4 });
          sparks(dim, c, OD_GOLD, n > 6 ? 2 : 3, 4);
          if (slot <= 2) play(dim, "mob.shulker.bullet.hit", c, 0.8, 1.4);
        });
      });
    }, t0);
    // 광선 그림: 이 물결의 gap 틱 동안 틱마다 (눈동자에서 발까지 — 금빛 + 흰 심지, 발의 번쩍임)
    for (let t = 0; t < k.gap; t++) {
      system.runTimeout(() => {
        if (!ok()) return;
        const f = footAt(i, t);
        const me = ray(f, 0.6, 0.18, 0.1);
        const fs = nearSize(0.45, len(sub(f, me)), 0.13, 1.5); // 1.5배로 커짐 — 다 커져도 거리 × 0.2 안
        if (fs > 0) fx(dim, "rpg:arrow_spark", f, { dir: V_UP, size: fs, grow: 1.5, life: 0.12, color: OD_GOLD, alpha: 0.9 });
      }, t0 + t);
    }
  }
  // 감음 → 대폭발: 흰 · 금빛 폭발 + 고리 둘 + 흰 룬 번쩍 + 눈에서 땅으로 내리꽂히는 빛줄기 여섯 + 솟는 금빛 알갱이 + 흔들림 → 적마다 (여섯씩 한 틱)
  system.runTimeout(() => {
    if (!ok()) return;
    play(dim, "beacon.deactivate", eyeC, 1.2, 0.8);
  }, close);
  system.runTimeout(() => {
    if (!ok()) return;
    burst(dim, up(spot, 1.0), OD_WHITE, 3.0, 0.35);
    burst(dim, up(spot, 1.6), glow, 4.2, 0.45);
    shock(dim, spot, glow, k.radius / 1.8, 0.5);
    shock(dim, spot, OD_WHITE, k.radius / 3.6, 0.4);
    rune(dim, spot, { size: k.radius, life: 0.6, grow: 0.25, rate: -120, color: OD_WHITE, alpha: 0.9 });
    for (let j = 0; j < 6; j++) {
      const a = (j / 5) * Math.PI * 2;
      const r = j === 0 ? 0 : k.radius * 0.6;
      const gx = spot.x + Math.cos(a) * r;
      const gz = spot.z + Math.sin(a) * r;
      const gy = solidBelow(dim, { x: gx, y: spot.y + 1.5, z: gz }, 4) ?? spot.y;
      ray({ x: gx, y: gy + 0.1, z: gz }, 0.45, 0.14, 0.35, 0.3, 0.9);
    }
    for (let j = 0; j < 8; j++) fx(dim, "rpg:arrow_spark", up(spot, 0.4 + Math.random()), { dir: V_UP, speed: 2 + Math.random() * 3, size: 0.16, life: 0.8, color: OD_GOLD, alpha: 1 });
    play(dim, "random.explode", spot, 0.9, 1.1);
    play(dim, "firework.large_blast", spot, 1.4, 0.8);
    play(dim, "item.trident.thunder", spot, 1.6, 0.8);
    shakeNear(dim, spot, 14, 0.35, 0.4);
    const list = unionById(
      visibleFrom(player, up(spot, 1.2), around(player, spot, k.radius, 3)),
      [...struck.values()].filter((e) => canHit(player, e) && Math.hypot(e.location.x - spot.x, e.location.z - spot.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - spot.y) <= 5),
    );
    list.forEach((e, j) => {
      hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j / 6)), ok, () => {
        if (skillHit(player, e, w, k.afterMult) <= 0) return;
        const c = bodyAt0(e, spot);
        const bs = nearSize(0.8, len(sub(c, player.getHeadLocation())), 0.3, 1.0);
        if (bs > 0) burst(dim, c, OD_WHITE, bs, 0.22);
        if (bossLike(e) || !e.isValid) return;
        const d = Math.hypot(e.location.x - spot.x, e.location.z - spot.z) > 0.3 ? flat(sub(e.location, spot)) : away; // 가운데에 선 적 (조준한 적) 은 나에게서 먼 쪽으로
        try {
          e.applyKnockback({ x: d.x * 0.5, z: d.z * 0.5 }, 0.45);
        } catch {
          // 밀리지 않는 몹
        }
      });
    });
  }, k.final);
  return true;
}

// ------------------------------------------------------------ 티르빙 (v2.16.0 마검 — 마력 2 : 힘 1, 대장장이 제작 전용) · 여러 스킬이 함께 쓰는 타격 도구
const IFRAMES = 10; // 몹 무적 시간 (틱)
/** 티르빙 연출 색 (config.js SKILLS.tyrfing): spell 하늘빛 룬 (마력) · rune 보랏빛 룬 · curse 핏빛 · dark 어두운 자주 */
function tyColors() {
  return SKILLS.tyrfing ?? { spell: [0.53, 0.8, 1.0], rune: [0.62, 0.36, 0.9], curse: [0.8, 0.08, 0.2], dark: [0.32, 0.05, 0.26] };
}

/** 마력 한 번: 마법 피해 (방어 무시), 파란 숫자 (치명타는 파란 !!) */
function spellHit(player, target, w, mult, o = {}) {
  return skillHit(player, target, w, mult, { ...o, cause: EntityDamageCause.magic, spell: true });
}

/** 쓰러졌는지 (체력 0 — 쓰러지는 모습 중 —, 사라짐, 보스 소품의 쓰러짐 표시) */
function fallen(e) {
  try {
    return !e?.isValid || hasNoHealth(e) || e.hasTag("rpg_gone");
  } catch {
    return true;
  }
}

/** 체력 대가 스킬 (v2.23.0 망자의 각성 — 예전 저주 해방) 이 체력을 돌려주는 적: 적대 몹 (monster 무리) · RPG 몬스터 · 보스 · 드래곤 (동물 · 보스 소품 · 허수아비는 아님) */
function soulWorthy(e) {
  try {
    const t = e.getComponent("minecraft:type_family");
    return !!t && (t.hasTypeFamily("monster") || t.hasTypeFamily("rpg_monster") || t.hasTypeFamily("rpg_boss") || t.hasTypeFamily("dragon"));
  } catch {
    return false;
  }
}

/** 플레이어가 죽거나 차원을 옮긴 횟수 — 나중에 나가는 타격이 다시 태어난 자리 · 다른 차원에서 나가지 않게 @type {Map<string, number>} */
const lives = new Map();
function lifeOf(player) {
  try {
    return `${player.dimension.id}|${lives.get(player.id) ?? 0}`;
  } catch {
    return "";
  }
}
/** 쓸 때와 같은 삶인지 (살아 있고, 그 사이 죽거나 차원을 옮기지 않음) */
function sameLife(player, token) {
  try {
    return player.isValid && !isDying(player) && lifeOf(player) === token;
  } catch {
    return false;
  }
}

/** "플레이어|적" → 이 플레이어의 티르빙 타격이 그 적에게 다음으로 들어갈 수 있는 틱 (먼저 잡아 둔 타격과 겹치지 않게) @type {Map<string, number>} */
const nextFree = new Map();
/**
 * 몹 무적 시간 (10틱) 을 피해 넣을 틱을 잡아 둠: want 와, 그 적이 마지막으로 피해를 받은 틱 (누가 때렸든 — 보스는 이 플레이어의 스킬) + 10 과,
 * 이 플레이어가 그 적에게 먼저 잡아 둔 타격 + 10 중 늦은 것 (스킬1 → 스킬2 를 이어 써도 피해가 사라지지 않게)
 */
function reserveHit(player, e, want) {
  const key = `${player.id}|${e.id}`;
  const at = Math.max(want, lastHurtTick(e, player) + IFRAMES, nextFree.get(key) ?? -1000);
  nextFree.set(key, at + IFRAMES);
  return at;
}

/**
 * at 틱에 fn() (잡아 둔 틱). 그때 그 적이 다른 피해로 무적 시간이면 끝날 때까지 미룸 (두 번까지).
 * ok() 가 거짓이면 (쓰러짐 · 다른 삶 · 무기 바꿈 등) 취소, 맞을 수 없는 적 (쓰러짐 · NPC) 이면 취소
 */
function hitAt(player, e, at, ok, fn, retry = 2) {
  const run = () => {
    if (!ok() || !canHit(player, e)) return;
    const wait = lastHurtTick(e, player) + IFRAMES - system.currentTick;
    if (wait > 0 && retry > 0) {
      const key = `${player.id}|${e.id}`;
      nextFree.set(key, Math.max(nextFree.get(key) ?? -1000, system.currentTick + wait + IFRAMES));
      hitAt(player, e, system.currentTick + wait, ok, fn, retry - 1);
      return;
    }
    fn();
  };
  const d = at - system.currentTick;
  if (d <= 0) run();
  else system.runTimeout(run, d);
}

/** 오래된 잡아 둔 틱 지우기 */
function pruneReserved() {
  const now = system.currentTick;
  for (const [k, t] of nextFree) if (t < now - 40) nextFree.delete(k);
}

/** 몸 가운데 (못 읽으면 마지막으로 안 자리) */
function bodyAt0(e, fallback) {
  try {
    return e.isValid ? bodyPoint(e) : fallback;
  } catch {
    return fallback;
  }
}

/** from 에서 단위 방향 dir 로 max 칸까지 막힘 없이 가는 거리 (블록 면까지) */
function rayDist(dim, from, dir, max) {
  try {
    const hit = dim.getBlockFromRay(from, dir, { maxDistance: max, includePassableBlocks: false, includeLiquidBlocks: false });
    if (hit) {
      const b = hit.block.location;
      const fl = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
      return Math.max(0, Math.min(max, len(sub({ x: b.x + fl.x, y: b.y + fl.y, z: b.z + fl.z }, from))));
    }
  } catch {
    // 무시
  }
  return max;
}

/** from 에서 단위 방향 dir 로 L 칸, 선에서 폭 width 칸 안의 적 {e, t} (t = 선 위 거리, 가까운 순). 눈 (from) 에서 그 몸까지 벽이 막으면 빼기 */
function lineHits(player, from, dir, L, width) {
  const out = [];
  for (const e of around(player, add(from, dir, L / 2), L / 2 + width + 0.5, L / 2 + 3)) {
    const pad = bodyPad(e);
    const q = bodyToward(e, from, dir);
    const p = sub(q, from);
    const t = p.x * dir.x + p.y * dir.y + p.z * dir.z;
    if (t < -0.3 - pad || t > L + 0.5 + pad) continue;
    if (len(sub(p, { x: dir.x * t, y: dir.y * t, z: dir.z * t })) > width + pad) continue;
    if (!lineOfSight(player, from, q)) continue;
    out.push({ e, t: Math.max(0, t) });
  }
  return out.sort((a, b) => a.t - b.t);
}

/** 바친 피를 돌려받는 모습: 쓰러진 적에서 내 가슴으로 핏빛 빛줄기 + 초록 회복 숫자 */
function soulReturn(player, from, amount, C) {
  if (!player.isValid) return;
  const dim = player.dimension;
  const chest = up(player.location, 1.1);
  const L = len(sub(chest, from));
  if (L > 0.5) beam(dim, from, chest, C.curse, { half: 0.3, thick: 0.12, life: 0.35, sweep: 0.15, gap: Math.max(0.6, L / 12) }); // 멀리 걸어가 있어도 조각 열두 개까지
  sparks(dim, add(chest, flat(player.getViewDirection()), 1.2), C.curse, 6, 2);
  const healed = healBy(player, amount);
  if (healed > 0) showDamageText(player, healed, "heal");
  play(dim, "respawn_anchor.charge", chest, 1.4, 0.7);
}

/** 체력 대가 (망자의 각성) 로 바칠 체력 (최대 체력 × cost, 소수 첫째 자리) */
function curseCost(player, k) {
  return Math.round(maxHpOf(player) * k.cost * 10) / 10;
}

/** 지금 대가를 바칠 수 있는지 (바친 뒤 1 이상 남음 — 소수 오차는 봐줌. 크리에이티브 등 RPG 체력이 없는 모드는 늘 됨) */
function canPayCurse(player, k) {
  return !isRpgMode(player) || hpState(player).hp - curseCost(player, k) >= 1 - 1e-6;
}

// ------------------------------------------------------------ 티르빙 — v2.23.0 개편 (앙간튀르의 무덤: 무덤불 · 망자의 각성)
// 사용자가 고른 컨셉 "앙간튀르의 무덤" (전설: 헤르보르가 무덤불 속에서 아버지 앙간튀르를 깨워 티르빙을 받음) · 세기 "스킬1 영웅 룬 · 스킬2 초월 룬 × 1.4 (체력 대가)"
// (Lv.40 125포인트 — 마력 83 · 힘 42 — 의 마검사 룬 위력 P 41.9 기준). 모든 타격 × 최대 체력 ÷ hpBase (v2.17.0). 검 = 공격 피해 (하늘색 숫자) · 마력 = 마법 피해 (파란 숫자)
const TY_FIRE = [0.6, 0.46, 1.0]; // 무덤불 보랏빛 (번쩍임 · 고리)
const TY_GHOST = [0.74, 0.76, 1.0]; // 유령 흰 보랏빛 (벤 자국 · 불티)
const TY_DEEP = [0.3, 0.16, 0.8]; // 짙은 보랏빛 (기운)
/** 무덤불을 그리지 않는 눈 둘레 (칸) · 가까우면 키를 거리 × 이 비율까지 — 1인칭 화면을 덮지 않게 */
const TY_NEAR = 1.4;
const TY_NEAR_K = 0.75;

/** 무덤불 한 장 (rpg:tyr_ghostfire — 밑동이 at, 보는 사람을 향해 섬): size = 키 (칸), life 초 */
function tyrFireFx(dim, at, size, life, color = [1, 1, 1], alpha = 1) {
  fx(dim, "rpg:tyr_ghostfire", at, { size, life, color, alpha });
}

/**
 * 유령 전사 한 장 (rpg:tyr_warrior — 꼬리 끝이 at, 보는 사람을 향해 섬): o = {size 반 크기, life 초, frame 시작 장, frames 장 수, fps 초당 장, loop (되풀이),
 * sink 칸 아래에서 rise 초 동안 솟음, fade 마지막 초 동안 사라짐}
 */
function tyrWarriorFx(dim, at, o) {
  try {
    const m = vars({ size: o.size, life: o.life, color: o.color ?? [1, 1, 1], alpha: o.alpha ?? 1 });
    m.setFloat("variable.frame", o.frame ?? 4);
    m.setFloat("variable.frames", Math.max(1, o.frames ?? 1));
    m.setFloat("variable.fps", o.fps ?? 0);
    m.setFloat("variable.loop", o.loop ? 1 : 0);
    m.setFloat("variable.sink", o.sink ?? 0);
    m.setFloat("variable.rise", o.rise ?? 0);
    m.setFloat("variable.fade", o.fade ?? 0);
    dim.spawnParticle("rpg:tyr_warrior", at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/** 갈라진 땅 (rpg:ground_crack — 짙은 보랏빛) */
function tyCrack(dim, at, size, life) {
  fx(dim, "rpg:ground_crack", up(at, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size, life, color: TY_DEEP, alpha: 0.9 });
}

/**
 * 그림을 보는 곳까지 거리: 1인칭 눈과 3인칭 카메라 (보는 쪽 뒤 4.5칸 · 위 1칸 — 기본 3인칭의 근사) 중 가까운 쪽.
 * 가까운 그림은 작게 · 흐리게 (어느 시점에서도 화면을 덮지 않게 — 등 뒤의 그림은 3인칭 카메라 바로 앞)
 */
function viewDist(player, at) {
  try {
    const { cam } = tpCam(player); // 바로 위 · 아래를 볼 때도 몸이 향한 쪽 뒤
    return Math.min(len(sub(at, player.getHeadLocation())), len(sub(at, cam)));
  } catch {
    return 99;
  }
}

/** 3인칭 카메라 근사 자리 (보는 쪽 뒤 4.5칸 · 눈 위 1칸) 와 보는 쪽 */
function tpCam(player) {
  const eye = player.getHeadLocation();
  const f = facingOf(player);
  return { cam: { x: eye.x - f.x * 4.5, y: eye.y + 1.0, z: eye.z - f.z * 4.5 }, f };
}

/**
 * at 아래 (at.y - maxDown 까지) 의 첫 단단한 블록 윗면 높이 (반 블록 · 계단은 맞은 면 높이 — 물 · 용암 · 땅 없음 = undefined).
 * ceilFrom 을 주면 그 높이가 빈 칸 (공기) 일 때 위로 천장을 찾아 at 을 천장 바로 밑으로 (광선이 천장 블록 안에서 시작하면 그 블록 위를 땅으로 봄).
 * 그 높이가 블록 속 (그 자리 땅이 높음 — 반 블록 · 턱) 이면 천장을 찾지 않음
 */
function tyGround(dim, at, maxDown, ceilFrom) {
  let top = at.y;
  try {
    if (ceilFrom !== undefined && dim.getBlock({ x: at.x, y: ceilFrom, z: at.z })?.isAir) {
      const c = dim.getBlockFromRay({ x: at.x, y: ceilFrom, z: at.z }, V_UP, { maxDistance: Math.max(0.1, at.y - ceilFrom), includePassableBlocks: false, includeLiquidBlocks: false });
      if (c) top = Math.max(ceilFrom, Math.min(top, c.block.location.y + (c.faceLocation?.y ?? 0) - 0.05));
    }
    const hit = dim.getBlockFromRay({ x: at.x, y: top, z: at.z }, V_DOWN, { maxDistance: Math.max(0.1, maxDown - (at.y - top)), includePassableBlocks: false, includeLiquidBlocks: true });
    if (!hit) return undefined;
    const b = hit.block;
    if (b.isLiquid || /water|lava/.test(b.typeId ?? "")) return undefined;
    const fy = hit.faceLocation?.y;
    return b.location.y + (typeof fy === "number" && fy > 0.05 && fy <= 1 ? fy : 1);
  } catch {
    return undefined;
  }
}

/** 발밑 땅 높이: 땅에 서 있으면 발 높이 그대로 (낭떠러지 끝에 걸쳐 서도), 뛰는 중이면 3칸 안의 땅 (반 블록은 그 윗면) */
function tyFeet(player) {
  const feet = { ...player.location };
  try {
    if (player.isOnGround !== false) return feet;
  } catch {
    return feet;
  }
  const g = tyGround(player.dimension, up(feet, 0.3), 3.3);
  return { x: feet.x, y: g === undefined ? feet.y : Math.min(feet.y, g), z: feet.z };
}

/** a → b 사이에 막는 블록이 하나도 없는지 (끝 여유 없이 — 얇은 문 · 유리판 · 쇠창살 · 울타리도 막음, 풀 · 꽃은 아님) */
function tyClear(dim, a, b) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return true;
  try {
    return !dim.getBlockFromRay(a, unit(d), { maxDistance: L, includePassableBlocks: false, includeLiquidBlocks: false });
  } catch {
    return true;
  }
}

/** 가까운 무덤불은 흐리게 (눈에서 TY_NEAR 칸 0.55 → 2.6칸 더 멀면 1) — 뒤의 적이 비쳐 보이게 */
function tyNearAlpha(d) {
  return Math.max(0.55, Math.min(1, 0.55 + (0.45 * (d - TY_NEAR)) / 2.6));
}

/** 한 틱에 맞는 적이 많으면 그림은 앞의 n 까지 (몰린 적 · 미뤄진 타격) — 스킬 한 번마다 하나 */
function fxBudget(n) {
  let tick = -1;
  let used = 0;
  return () => {
    if (system.currentTick !== tick) {
      tick = system.currentTick;
      used = 0;
    }
    return ++used <= n;
  };
}

/**
 * 무덤불 (v2.23.0): 칼끝으로 땅을 앞으로 그음 — 발밑 땅에서 (뛰는 중이면 3칸 안의 땅) 보는 쪽 (조준한 적이 있으면 그 적 쪽, 가로만) 으로 땅을 따라
 * (한 칸씩 오르내림 · 벽 · 낭떠러지 · 물 · 용암 · 닫힌 문 · 유리판 · 울타리 앞에서 끊김)
 * start ~ range 칸에 고르게 (좌우로 zig 칸씩 번갈아 비껴) pillars 개 무덤불 기둥이 lead 틱부터 한 틱씩 차례로 솟음 (갈라진 금 · 번쩍임) — 기둥마다 width 칸 안의 적 (기둥 가운데에서 보이는) 을 벰
 * (검 mult, 적마다 한 번) → burst 틱에 기둥이 한꺼번에 터짐 (마력 spellMult — 기둥 width + 0.4칸 안의 보이는 적 + 벤 적은 밀려났어도 기둥에서 width + 2칸 안이면, 적마다 한 번).
 * 앞이 바로 막혔거나 땅이 없어 (물 · 낭떠러지) 기둥을 하나도 세울 수 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 없음 (무기를 바꿔도 이미 그은 불은 끝까지)
 */
function tyrfingFire(player, w, k) {
  const dim = player.dimension;
  // 땅을 긋는 자리: 발 높이 (뛰는 중이면 3칸 안의 발밑 땅 — 반 블록은 그 윗면, 낭떠러지 끝에 걸쳐 서면 선 땅 그대로)
  const start = tyFeet(player);
  let f = facingOf(player);
  try {
    const r = aimTarget(player, k.range + 1, BOW.aimAssist ?? 0);
    if (r.target?.isValid && canHit(player, r.target)) {
      const d = sub(r.target.location, start);
      if (Math.hypot(d.x, d.z) >= 1.5) f = flat(d);
    }
  } catch {
    // 보는 쪽
  }
  const spots = dashSpots(dim, start, f, k.range);
  const side = rightOf(f);
  /** 기둥 (갈라진 금을 따라 좌우로 zig 칸씩 번갈아 — 1인칭에서도 겹치지 않고 하나하나 보이게): o = 옆으로 비낀 칸 (+ 오른쪽) @type {{x: number, y: number, z: number, o: number}[]} */
  const posts = [];
  let prev = start;
  for (let j = 0; j < k.pillars; j++) {
    const s = k.start + ((k.range - k.start) * j) / Math.max(1, k.pillars - 1);
    let best;
    for (const q of spots) if (Math.abs(q.s - s) <= 0.3 && (!best || Math.abs(q.s - s) < Math.abs(best.s - s))) best = q;
    if (!best) break;
    // 기둥 자리 그대로 다시 봄 (0.5칸마다 본 길 사이 · 옆으로 비낀 자리 — 벽 · 낭떠러지 끝을 넘어 벽 속 · 허공에 세우지 않게).
    // 비낀 자리가 옆 벽 속 · 옆 낭떠러지면 가운데 줄에 (좁은 굴 · 다리 · 벽에 붙어서도 줄이 끊기지 않게)
    // 앞 기둥 (처음은 발밑) 에서 이 기둥까지 1.2칸 높이로 막는 것이 없어야 함 (0.5칸마다 아래로만 보는 길 사이의 얇은 문 · 유리판 · 울타리)
    const spotAt = (o) => {
      const q = standSpot(dim, { x: start.x + f.x * s + side.x * o, y: best.p.y + 1.1, z: start.z + f.z * s + side.z * o }, 2.2);
      return q && Math.abs(q.y - best.p.y) <= 1.05 && tyClear(dim, up(prev, 1.2), up(q, 1.2)) ? { ...q, o } : undefined;
    };
    const at = spotAt((j % 2 === 0 ? 1 : -1) * k.zig) ?? spotAt(0);
    if (!at) break;
    posts.push(at);
    prev = at;
  }
  if (!posts.length) {
    warn(player, `§c${k.name}: §7앞이 막혔거나 땅이 없습니다`);
    return false;
  }
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const hf = tyrfingHpFactor(maxHpOf(player)); // v2.17.0 스킬 × 최대 체력 ÷ 100 (쓸 때의 최대 체력)
  const C = tyColors();
  const budget = fxBudget(6);
  /** 벤 적 (밀려나도 터질 때 맞음) */
  const struck = new Map();
  // 칼끝이 땅을 긁음: 눈앞 아래 불티 · 짧은 금 · 소리
  const tip = add(start, f, 1.3);
  sparks(dim, up(tip, 0.2), TY_GHOST, 5, 3);
  tyCrack(dim, add(start, f, 1.6), 0.9, 1.0);
  play(dim, "mace.smash_ground", tip, 0.9, 1.0);
  play(dim, "particle.soul_escape", tip, 0.8, 1.2);
  shake(player, 0.08, 0.1);
  posts.forEach((p, j) => {
    const at = k.lead + j;
    system.runTimeout(() => {
      if (!ok()) return;
      const eye = player.getHeadLocation();
      const de = viewDist(player, up(p, 1.0));
      // 갈라진 금 · 무덤불 기둥 (터질 때까지 탐 — 가까우면 작게 · 흐리게) · 밑동 번쩍임 · 불티
      tyCrack(dim, p, 1.3, (k.burst - at) / 20 + 0.6);
      const h = Math.min(2.6, TY_NEAR_K * de);
      if (de >= TY_NEAR) {
        tyrFireFx(dim, p, h, (k.burst - at) / 20 + 0.45, [1, 1, 1], tyNearAlpha(de)); // 터질 때까지 세게 탐 (사라지기 시작하는 65% 가 터지는 틱 즈음)
        if (j % 3 === 0 && p.o) tyrFireFx(dim, add(p, side, Math.sign(p.o) * 0.5), h * 0.6, (k.burst - at) / 20 + 0.4, [1, 1, 1], tyNearAlpha(de)); // 바깥쪽 작은 불혀 (0 · 3 · 6 — 좌우 번갈아)
      }
      burst(dim, up(p, 0.3), TY_FIRE, Math.min(0.7, 0.3 * de), 0.25);
      sparks(dim, up(p, 0.6), TY_GHOST, 3, 3);
      play(dim, "particle.soul_escape", p, 0.8 + 0.06 * j, 1.0); // 기둥마다 한 음씩 높게
      if (j % 2 === 0) play(dim, "mob.blaze.shoot", p, 0.7 + 0.05 * j, 0.6);
      // 검: 이 기둥의 width 칸 안 (기둥에서 보이는 — 벽 너머는 아님 · 아직 베지 않은 적)
      for (const e of visibleFrom(player, up(p, 1.0), around(player, up(p, 1.0), k.width, 2.0))) {
        if (struck.has(e.id)) continue;
        struck.set(e.id, e);
        hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => {
          if (skillHit(player, e, w, k.mult * hf) <= 0 || !budget()) return;
          const c = bodyAt0(e, up(p, 1));
          const dc = viewDist(player, c);
          if (dc >= 1.2) cutMark(dim, c, unit(sub(c, eye)), 58, 0.06, TY_FIRE, [1, 1, 1], Math.min(1, 0.4 * dc));
          sparks(dim, c, TY_GHOST, 2, 4);
        });
      }
    }, at);
  });
  // 터짐: 기둥마다 높이 솟는 무덤불 · 번쩍임 · 고리 · 솟는 불티 → 마력
  system.runTimeout(() => {
    if (!ok()) return;
    for (const p of posts) {
      const de = viewDist(player, up(p, 1.2));
      if (de >= TY_NEAR) tyrFireFx(dim, p, Math.min(4.2, TY_NEAR_K * 1.3 * de), 0.4, [1, 1, 1], tyNearAlpha(de));
      burst(dim, up(p, 1.0), TY_GHOST, Math.min(1.2, 0.3 * de), 0.3);
      shock(dim, p, TY_FIRE, Math.min(1.4, 0.4 * de), 0.4);
      fx(dim, "rpg:arrow_spark", up(p, 0.5), { dir: V_UP, speed: 3 + Math.random() * 2, size: 0.15, life: 0.6, color: TY_GHOST, alpha: 1 });
    }
    const mid = posts[Math.floor(posts.length / 2)];
    play(dim, "mob.ghast.fireball", mid, 0.7, 1.2);
    play(dim, "random.explode", mid, 1.2, 0.6);
    play(dim, "particle.soul_escape", mid, 0.6, 1.4);
    shakeNear(dim, mid, 10, 0.25, 0.3);
    const near = (e, r) => posts.some((p) => Math.hypot(e.location.x - p.x, e.location.z - p.z) <= r + bodyPad(e) && Math.abs(e.location.y - p.y) <= 3);
    const list = unionById(
      posts.flatMap((p) => visibleFrom(player, up(p, 1.0), around(player, up(p, 1.0), k.width + 0.4, 2.0))),
      [...struck.values()].filter((e) => canHit(player, e) && near(e, k.width + 2)),
    );
    list.forEach((e, j) => {
      hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j / 6)), ok, () => {
        if (spellHit(player, e, w, k.spellMult * hf) <= 0 || !budget()) return;
        const c = bodyAt0(e, start);
        const bs = nearSize(0.9, viewDist(player, c), 0.3, 1.0);
        if (bs > 0) burst(dim, c, C.rune, bs, 0.25);
      });
    });
  }, k.burst);
  return true;
}

/**
 * 망자의 각성 (v2.23.0 — 체력 대가: 전설의 마검은 뽑을 때마다 생명을 앗아야 함): 둘레 안 (radius 칸 · 발밑 위 1.2칸에서 보이는) 에 맞을 적이 없으면
 * 쓰지 않음 (대가 · 재사용 대기 없음 — 벽 너머의 적만 있을 때도).
 * 최대 체력 × cost 를 바침 (생존 · 모험 모드 — 피해가 아니라 보호막 · 방어와 상관없음, 바치면 1 아래가 되면 쓰지 못함 · 재사용 대기 없음).
 * 발밑 (그 자리에 남음) 에 핏빛 무덤 룬 → 둘레 radius 칸에 앙간튀르의 열두 형제 (warriors — 앞에서부터 오른쪽으로 30° 마다, 그 땅 높이) 가 앞에서부터
 * 양옆 · 뒤로 한 틱 사이로 무덤에서 솟음 (rise 틱) → first 틱부터 step 틱마다 차례로 자기 쪽 (30°) 을 안으로 벰 (검 mult — 적마다 한 번, 마지막 전사 때 아직 안 베인 둘레 안의 적도) →
 * second 틱에 다 같이 내리벰 (검 mult) → final 틱에 무덤이 무덤불로 터짐 (마력 spellMult, 밀쳐 띄움 — 보스는 밀지 않음 · 벤 적은 밀려났어도 radius + 2.5칸 안이면).
 * 둘레 안의 적은 무덤 가운데 위 1.2칸에서 보이는 적. 이 스킬로 적대 몹 · 보스를 쓰러뜨리면 바친 체력을 돌려받음 (한 번 — 보스처럼 다음 틱에 쓰러지는 적은 2틱 뒤에 봄).
 * 쓰러지거나 차원을 옮기면 남은 것은 없음 (무기를 바꿔도 깨운 망령은 끝까지)
 */
function tyrfingWaking(player, w, k) {
  const C = tyColors();
  // 무덤 자리: 발 높이 (뛰는 중이면 3칸 안의 발밑 땅 — 반 블록은 그 윗면 · 룬 · 땅의 빛이 허공에 뜨지 않게, 낭떠러지 끝에 걸쳐 서면 선 땅 그대로)
  const c0 = tyFeet(player);
  const center = up(c0, 1.2);
  /** 둘레 안의 적 (무덤 가운데 위 1.2칸에서 보이는 — 벽 너머는 아님) */
  const inside = () => visibleFrom(player, center, around(player, c0, k.radius, 3));
  if (!inside().length) {
    warn(player, `§c${k.name}: §7주위 ${k.radius}칸 안에 적이 없습니다`);
    return false;
  }
  const cost = curseCost(player, k);
  const rpgMode = isRpgMode(player);
  if (!canPayCurse(player, k)) {
    warn(player, `§c${k.name}: §7체력 ${Math.round((cost + 1) * 10) / 10} 이상 필요 §8(대가 ${cost} · 1은 남김)`);
    return false;
  }
  const paid = rpgMode ? payHp(player, cost) : 0;
  if (rpgMode && paid <= 0) return false;
  const hf = tyrfingHpFactor(maxHpOf(player)); // v2.17.0 스킬 × 최대 체력 ÷ 100
  const dim = player.dimension;
  const life = lifeOf(player);
  const ok = () => sameLife(player, life);
  const f0 = facingOf(player);
  const a0 = Math.atan2(f0.z, f0.x);
  const budget = fxBudget(6);
  const castTick = system.currentTick;
  // 대가: 눈앞 아래 핏빛 불꽃 + 발밑 고리 + 심장 소리 + 얼굴 앞 왼쪽 아래 "-10 피의 대가" (회복 숫자는 오른쪽 아래)
  const front = up(add(c0, f0, 1.4), 0.9);
  burst(dim, front, C.curse, 0.6, 0.3);
  sparks(dim, front, C.curse, 6, 3);
  shock(dim, c0, C.curse, 1.8, 0.45);
  if (paid > 0) showFaceText(player, `§c§l-${Math.round(paid * 10) / 10} §r§c피의 대가`, { dist: 2.6, side: -0.95, up: -0.8 });
  play(dim, "mob.warden.heartbeat", c0, 0.8, 1.0);
  play(dim, "respawn_anchor.deplete", c0, 0.8, 0.8);
  // 무덤: 핏빛 룬 (반지름 radius) + 짙은 보랏빛 고리, 망령이 깨어나는 소리
  rune(dim, c0, { size: k.radius + 0.4, life: (k.final + 10) / 20, color: C.curse, alpha: 0.8, rate: 14 });
  shock(dim, c0, TY_DEEP, (k.radius + 0.5) / 1.8, 0.6);
  play(dim, "mob.warden.emerge", c0, 0.9, 1.1);
  play(dim, "mob.evocation_illager.prepare_summon", c0, 1.0, 0.8);
  // 전사 자리: 앞에서부터 오른쪽으로 30° 마다 (위에서 볼 때 시계 방향), 그 땅 높이
  const N = k.warriors;
  const posts = [];
  for (let i = 0; i < N; i++) {
    const a = a0 + (i / N) * Math.PI * 2;
    const u = { x: Math.cos(a), y: 0, z: Math.sin(a) };
    const x = c0.x + u.x * k.radius;
    const z = c0.z + u.z * k.radius;
    const gy = tyGround(dim, { x, y: c0.y + 2.5, z }, 6, c0.y + 0.1);
    posts.push({ i, u, at: { x, y: gy ?? c0.y, z }, rise: Math.min(i, N - i) });
  }
  /** 적의 쪽 (전사 번호): 무덤 가운데에서 본 각 */
  const sectorOf = (e) => {
    const dx = e.location.x - c0.x;
    const dz = e.location.z - c0.z;
    if (Math.hypot(dx, dz) < 0.3) return 0;
    let a = Math.atan2(dz, dx) - a0;
    a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
    return Math.round(a / ((Math.PI * 2) / N)) % N;
  };
  /** 벤 적 (밀려나도 터질 때 맞음) */
  const struck = new Map();
  let refunded = false;
  const refund = (at) => {
    if (refunded) return;
    refunded = true;
    // 무덤불이 터지는 틱이면 한 틱 뒤에 (그 틱의 파티클이 60개를 넘지 않게)
    if (system.currentTick - castTick !== k.final) soulReturn(player, at, paid, C);
    else
      system.runTimeout(() => {
        if (sameLife(player, life)) soulReturn(player, at, paid, C);
      }, 1);
  };
  /** 한 번 침 (검 또는 마력) → 쓰러뜨렸으면 한 번 돌려받음. 들어갔는지 돌려줌 */
  const strike = (e, spell) => {
    const at = bodyAt0(e, center);
    const got = spell ? spellHit(player, e, w, k.spellMult * hf) : skillHit(player, e, w, k.mult * hf);
    if (got <= 0) return false;
    struck.set(e.id, e);
    if (budget()) {
      const dc = viewDist(player, at);
      const bs = nearSize(spell ? 1.0 : 0.6, dc, 0.3, 1.0);
      if (bs > 0) burst(dim, at, spell ? C.rune : TY_GHOST, bs, spell ? 0.28 : 0.22);
      if (spell && dc >= TY_NEAR) tyrFireFx(dim, e.isValid ? e.location : at, Math.min(2.2, TY_NEAR_K * dc), 0.35, [1, 1, 1], tyNearAlpha(dc));
    }
    if (refunded || paid <= 0 || !soulWorthy(e)) return true;
    if (fallen(e)) refund(at);
    else if (bossLike(e))
      // 보스는 받은 피해를 다음 틱에 넣어 쓰러짐 — 2틱 뒤에 다시 봄 (보통 몹은 맞은 그 틱에만: 다른 사람이 곧바로 쓰러뜨린 것은 아님)
      system.runTimeout(() => {
        if (!refunded && fallen(e) && sameLife(player, life)) refund(at);
      }, 2);
    return true;
  };
  /** 베기 그림: 전사의 칼 → 안쪽 (무덤 가운데) 으로 땅을 가르는 빛 + 전사 앞의 초승달 참격 (가운데 1.8칸 안은 빼고 · 눈 · 3인칭 카메라 가까이는 작게) */
  const slashFx = (p, core) => {
    const from = up(add(c0, p.u, k.radius - 0.4), 0.15);
    const to = up(add(c0, p.u, 1.8), 0.15);
    streakLine(dim, from, to, TY_FIRE, 0.32, 0.3, 0.9);
    if (core) streakLine(dim, from, to, TY_GHOST, 0.1, 0.25, 1);
    const at = up(add(c0, p.u, k.radius * 0.72), 1.0);
    const s = nearSize(1.6, viewDist(player, at), 0.3, 1.6);
    if (core && s > 0) arc(dim, at, { x: -p.u.x, y: 0, z: -p.u.z }, { shape: "heavy", flip: false, size: s, life: 0.28, speed: 0, spin: 70, color: TY_GHOST, aura: TY_DEEP });
  };
  /**
   * 전사 한 토막 (from 틱부터 to 틱까지 — 다음 토막과 0.05초 겹쳐 끊기지 않게). 그릴 때마다 지금 눈 · 3인칭 카메라로 다시 봄 (쓴 뒤 걷거나 돌아섰을 수 있음):
   * 눈에서 2칸 안 · 3인칭 카메라 앞 2칸 안이면 그리지 않고, 눈 · 3인칭 카메라에서 3칸 안이면 흐리게 (0.55)
   */
  const pose = (p, from, to, o) => {
    const c = up(p.at, 1.5);
    let eyeD = 99;
    let camD = 99;
    try {
      eyeD = len(sub(c, player.getHeadLocation()));
      const { cam, f } = tpCam(player);
      const rel = sub(c, cam);
      camD = len(rel);
      if (rel.x * f.x + rel.z * f.z < 0) camD = Math.max(camD, 2); // 카메라 뒤는 보이지 않음 (흐림만)
    } catch {
      eyeD = 99;
    }
    if (eyeD < 2 || camD < 2) return;
    tyrWarriorFx(dim, p.at, { size: k.size, life: (to - from) / 20 + 0.05, alpha: Math.min(eyeD, camD) < 3 ? 0.55 : 1, ...o });
  };
  /** 틱마다 할 일 (전사 토막: 솟음 → 섬 → 벰 → 섬 → 벰 → 섬 → 흩어짐) */
  const events = new Map();
  const on = (t, fn) => {
    if (!events.has(t)) events.set(t, []);
    events.get(t).push(fn);
  };
  const IDLE = { frame: 4, frames: 2, fps: 5, loop: true };
  const SWING = { frame: 6, frames: 2, fps: 12 };
  /** 섬 (되풀이 4-5, 한 바퀴 8틱) 은 8틱씩 나눠 그림 — 오래 남는 한 장이 나중에 다가간 눈앞을 덮지 않게 (바퀴가 끊기지 않게 한 바퀴씩) */
  const idle = (p, from, to) => {
    for (let t = from; t < to; t += 8) on(t, () => pose(p, t, Math.min(to, t + 8), IDLE));
  };
  for (const p of posts) {
    const s1 = k.first + p.i * k.step;
    on(p.rise, () => {
      pose(p, p.rise, p.rise + k.rise, { frame: 0, frames: 4, fps: 4 / (k.rise / 20), sink: 1.2, rise: k.rise / 20 });
      tyrFireFx(dim, p.at, 1.5, k.rise / 20 + 0.2);
      tyCrack(dim, p.at, 1.1, (k.final - p.rise) / 20 + 0.5);
      if (p.i % 4 === 0) play(dim, "particle.soul_escape", p.at, 0.7 + 0.05 * p.i, 1.0);
    });
    idle(p, p.rise + k.rise, s1);
    on(s1, () => pose(p, s1, s1 + 4, SWING));
    idle(p, s1 + 4, k.second);
    on(k.second, () => pose(p, k.second, k.second + 4, SWING));
    idle(p, k.second + 4, k.final);
    on(k.final, () => pose(p, k.final, k.final + 8, { frame: 8, frames: 4, fps: 10, fade: 0.15 }));
  }
  let done = false;
  /** @type {number | undefined} */
  let runId;
  const finish = () => {
    done = true;
    if (runId !== undefined) system.clearRun(runId);
  };
  const step = (t) => {
    if (!ok() || t > k.final + 2) return finish();
    for (const fn of events.get(t) ?? []) fn();
    // 첫 바퀴: 차례로 자기 쪽을 안으로 벰
    const i1 = (t - k.first) / k.step;
    if (Number.isInteger(i1) && i1 >= 0 && i1 < N) {
      const p = posts[i1];
      slashFx(p, true);
      sparks(dim, up(p.at, 2.4), TY_GHOST, 2, 3);
      play(dim, "mob.vex.charge", p.at, 0.8 + 0.03 * i1, 0.8); // 차례마다 한 음씩 높게
      const last = i1 === N - 1;
      for (const e of inside()) {
        if (struck.has(e.id) || (!last && sectorOf(e) !== i1)) continue;
        struck.set(e.id, e);
        hitAt(player, e, reserveHit(player, e, system.currentTick), ok, () => strike(e, false));
      }
    }
    // 둘째 바퀴: 다 같이 내리벰
    if (t === k.second) {
      for (const p of posts) slashFx(p, false);
      shock(dim, c0, TY_FIRE, (k.radius * 0.7) / 1.8, 0.4);
      play(dim, "mace.smash_air", c0, 0.7, 1.0);
      play(dim, "mob.vex.charge", c0, 0.6, 1.0);
      play(dim, "item.trident.thunder", c0, 1.4, 0.6);
      shakeNear(dim, c0, 10, 0.2, 0.25);
      inside().forEach((e, j) => hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j / 6)), ok, () => strike(e, false)));
    }
    if (t !== k.final) return;
    // 무덤이 무덤불로 터짐: 전사가 유령불로 흩어지며 그 자리에서 높이 솟는 무덤불 · 핏빛 룬 번쩍 · 고리 둘 → 마력 (밀쳐 띄움)
    for (const p of posts) {
      const de = viewDist(player, up(p.at, 1.5));
      if (de >= TY_NEAR) tyrFireFx(dim, p.at, Math.min(3.6, TY_NEAR_K * 1.3 * de), 0.5, [1, 1, 1], tyNearAlpha(de));
      burst(dim, up(p.at, 1.2), TY_GHOST, Math.min(1.1, 0.3 * de), 0.3);
    }
    rune(dim, c0, { size: k.radius + 0.4, life: 0.6, grow: 0.25, rate: -120, color: C.curse, alpha: 0.95 });
    shock(dim, c0, TY_FIRE, (k.radius + 1) / 1.8, 0.5);
    shock(dim, c0, C.curse, k.radius / 3.6, 0.4);
    play(dim, "mob.wither.shoot", c0, 0.8, 0.8);
    play(dim, "mace.heavy_smash_ground", c0, 0.9, 1.0);
    play(dim, "mob.ghast.fireball", c0, 0.6, 1.2);
    play(dim, "particle.soul_escape", c0, 0.5, 1.4);
    shakeNear(dim, c0, 14, 0.35, 0.4);
    const list = unionById(
      inside(),
      [...struck.values()].filter((e) => canHit(player, e) && Math.hypot(e.location.x - c0.x, e.location.z - c0.z) <= k.radius + 2.5 + bodyPad(e) && Math.abs(e.location.y - c0.y) <= 5),
    );
    list.forEach((e, j) => {
      hitAt(player, e, reserveHit(player, e, system.currentTick + Math.floor(j / 6)), ok, () => {
        if (!strike(e, true) || bossLike(e) || !e.isValid) return; // 들어간 적만 밀침 (보스는 밀지 않음)
        const d = Math.hypot(e.location.x - c0.x, e.location.z - c0.z) > 0.3 ? flat(sub(e.location, c0)) : f0;
        try {
          e.applyKnockback({ x: d.x * 0.5, z: d.z * 0.5 }, 0.45);
        } catch {
          // 밀리지 않는 몹
        }
      });
    });
    finish();
  };
  step(0);
  let t = 0;
  if (!done) runId = system.runInterval(() => step(++t), 1);
  return true;
}

const SKILL_FN = {
  tyrfing_fire: tyrfingFire,
  tyrfing_waking: tyrfingWaking,
  gram_charge: gramCharge,
  gram_descent: gramDescent,
  solas_rush: solasRush,
  solas_halo: solasHalo,
  gungnir_return: gungnirReturn,
  gungnir_fall: gungnirFall,
  aegis_charge: aegisCharge,
  aegis_dome: aegisDome,
  artemis_pierce: artemisPierce,
  artemis_moon: artemisMoon,
  odin_ravens: odinRavens,
  odin_eye: odinEye,
};

// ------------------------------------------------------------ 쓰기
/** 남은 재사용 대기 (틱) */
export function cooldownLeft(player, skill) {
  return Math.max(0, (cooldowns.get(`${player.id}|${skill.id}`) ?? 0) - system.currentTick);
}

/**
 * 스킬 쓰기. 요구 조건 (레벨·요구 스탯)·재사용 대기를 확인하고, 스킬이 대상이 없어 취소하면 재사용 대기 없음
 * @param {import("@minecraft/server").Player} player
 * @param {any} w
 * @param {number} index 0 = 스킬 1, 1 = 스킬 2
 */
export function castSkill(player, w, index) {
  const k = w.skills?.[index];
  if (!k || !player.isValid) return false;
  if (blockedBySilence(player)) return false; // v1.8.0 보스 기믹: 침묵
  if (blockedByMount(player, "skill")) return false; // v2.8.0 탈것에 탄 동안
  if (!meetsRequirement(player, w)) {
    warn(player, reqText(w, player));
    return false;
  }
  const left = cooldownLeft(player, k);
  if (left > 0) {
    warn(player, `§c${k.name} §7재사용 대기 §f${(left / 20).toFixed(1)}초`);
    return false;
  }
  const fn = SKILL_FN[k.id];
  if (!fn) return false;
  let ok = false;
  try {
    ok = fn(player, w, k) !== false;
  } catch (e) {
    console.warn(`[rpg] skill ${k.id}: ${e}`);
    ok = false;
  }
  if (!ok) return false;
  cooldowns.set(`${player.id}|${k.id}`, system.currentTick + Math.round(k.cd * 20));
  readyAnnounced.delete(`${player.id}|${k.id}`);
  notify(player, `§b§l${k.name}`, 20);
  return true;
}

// ------------------------------------------------------------ 입력
function isRanged(w) {
  return w.type === "bow" || w.type === "wand";
}

/** itemUse 뒤 이 틱 안에 itemStartUse(누르고 있기 시작)가 오지 않으면, 엔진이 누르고 있기를 재지 않는 것으로 보고 평타 */
const START_WAIT = 3;

/**
 * 활·완드 누르기 시작 (itemUse·itemStartUse 중 먼저 온 것). 웅크리고 누르면 바로 스킬 1
 * @param {"use" | "start"} source itemUse 면 "use", itemStartUse 면 "start"
 */
function pressStart(player, w, source) {
  const now = system.currentTick;
  const prev = presses.get(player.id);
  if (prev && prev.w.id === w.id && now - prev.tick <= START_WAIT) {
    // 같은 누르기의 두 번째 이벤트 (itemUse·itemStartUse)
    if (source === "start") prev.started = true;
    return;
  }
  if (prev && !prev.done) {
    // 떼는 이벤트 없이 다시 눌렸으면 앞의 누르기는 짧게 누른 것으로 본다
    prev.done = true;
    if (holding(player, prev.w)) castRanged(player, prev.w);
  }
  const press = { w, tick: now, done: false, started: source === "start" };
  presses.set(player.id, press);
  if (player.isSneaking) {
    press.done = true;
    castSkill(player, w, 0);
    return;
  }
  // 안전장치: 누르고 있기 시작(itemStartUse)이 오지 않으면 떼기(itemReleaseUse)도 오지 않으므로 바로 평타 (길게 누르기 스킬은 못 씀)
  system.runTimeout(() => {
    if (presses.get(player.id) !== press || press.done || press.started) return;
    press.done = true;
    presses.delete(player.id);
    if (holding(player, w)) castRanged(player, w);
  }, START_WAIT);
  // 길게 누르기: holdTicks 뒤에도 안 뗐으면 스킬 2 (itemCompleteUse 가 먼저 오면 그쪽에서)
  system.runTimeout(() => {
    if (presses.get(player.id) !== press || press.done) return;
    press.done = true;
    if (holding(player, w)) castSkill(player, w, 1);
  }, SKILLS.holdTicks);
}

/** 뗐을 때: 짧게 눌렀으면 평타 (다 누른 뒤의 떼기는 이미 스킬 2로 처리됨) */
function pressEnd(player) {
  const press = presses.get(player.id);
  if (!press) return;
  presses.delete(player.id);
  if (press.done) return;
  press.done = true;
  if (!holding(player, press.w)) return; // 누른 채로 다른 아이템으로 바꿨으면 쏘지 않음
  if (system.currentTick - press.tick >= SKILLS.holdTicks - 1) castSkill(player, press.w, 1); // 다 누른 순간과 같은 틱에 뗀 경우
  else castRanged(player, press.w);
}

/** 다 눌렀을 때 (길게): 스킬 2 */
function pressComplete(player) {
  const press = presses.get(player.id);
  if (!press || press.done) return;
  press.done = true;
  if (holding(player, press.w)) castSkill(player, press.w, 1);
}

/**
 * 아이템 사용 (main.js). 스킬이 있는 초월 무기면 처리하고 true.
 * @param {import("@minecraft/server").Player} player
 */
export function onSkillItemUse(player, itemStack) {
  const w = parseWeapon(itemStack?.typeId);
  if (!w?.skills) return false;
  if (recentlyTalked(player)) return true; // NPC·주민을 터치한 같은 터치
  heldEnhance(player, w); // 쓸 때의 강화 단계를 기록 (늦게 맞는 스킬 파도·반사도 이 단계로, v1.5.0)
  if (isRanged(w)) pressStart(player, w, "use");
  else castSkill(player, w, player.isSneaking ? 1 : 0);
  return true;
}

// ------------------------------------------------------------ 재사용 대기 표시
function hudText(player, w) {
  if (!meetsRequirement(player, w)) return reqText(w, player); // v2.6.2: 아직 쓸 수 없는 무기는 "준비" 대신 까닭 (레벨 · 요구 스탯)
  const ranged = isRanged(w);
  return w.skills
    .map((k, i) => {
      const left = cooldownLeft(player, k);
      const how = ranged ? (i === 0 ? "웅크리고" : "길게") : i === 0 ? "버튼" : "웅크리고";
      // v2.16.0 체력 대가 스킬 (v2.23.0 망자의 각성): 체력이 대가보다 적으면 "체력 부족"
      const state = left > 0 ? `§c${(left / 20).toFixed(1)}초` : k.cost && !canPayCurse(player, k) ? "§c체력 부족" : "§a준비";
      return `§7${how} §f${k.name} ${state}`;
    })
    .join(" §8| ");
}

/** 초월 무기를 들고 있으면 화면 하단에 재사용 대기, 준비되면 작은 소리 */
function hudTick() {
  for (const player of world.getAllPlayers()) {
    let w;
    try {
      w = parseWeapon(heldTypeId(player));
    } catch {
      w = undefined;
    }
    if (!w?.skills) continue;
    for (const k of w.skills) {
      const key = `${player.id}|${k.id}`;
      if (cooldownLeft(player, k) === 0 && cooldowns.has(key) && !readyAnnounced.has(key)) {
        readyAnnounced.add(key);
        try {
          player.playSound("random.orb", { pitch: 1.9, volume: 0.35 });
        } catch {
          // 무시
        }
      }
    }
    if (!actionbarFree(player) || promptedRecently(player)) continue;
    try {
      player.onScreenDisplay.setActionBar(hudText(player, w) + hudSuffix(player)); // v2.13.0 덧붙임 (마검 부여)
    } catch {
      // 무시
    }
  }
}

export function registerSkills() {
  const after = world.afterEvents;
  // v2.16.0 티르빙: 죽음 · 차원 이동마다 삶이 바뀜 (나중에 나가는 타격을 멈춤), 잡아 둔 틱 정리
  after.entityDie.subscribe((ev) => {
    const e = ev.deadEntity;
    if (e?.typeId === PLAYER) lives.set(e.id, (lives.get(e.id) ?? 0) + 1);
  });
  after.playerDimensionChange.subscribe((ev) => lives.set(ev.player.id, (lives.get(ev.player.id) ?? 0) + 1));
  system.runInterval(pruneReserved, 100);
  // v2.17.0 그람 용익 강하: 떠올랐다 내려찍는 사람의 낙하 피해를 막음 (before 이벤트), 오래된 기록 정리
  addHurtHooks({ block: noFallBlock });
  system.runInterval(pruneNoFall, 100);
  // v2.20.0 신의 방벽: 돔 안에서 맞으면 육각 칸이 번쩍 (피해는 combat.js 의 감소 버프가 줄임)
  after.entityHurt.subscribe((ev) => {
    if (!domes.size) return;
    const v = ev.hurtEntity;
    const d = v?.typeId === PLAYER ? domes.get(v.id) : undefined;
    if (!d) return;
    if (system.currentTick >= d.until) {
      domes.delete(v.id);
      return;
    }
    if (!d.inside(v)) return;
    const from = ev.damageSource?.damagingEntity;
    system.run(() => domeBlocked(v, from));
  }, { entityTypes: [PLAYER] }); // 플레이어가 맞을 때만 (모든 엔티티의 피해에 돌지 않게)
  after.itemStartUse.subscribe((ev) => {
    const w = parseWeapon(ev.itemStack?.typeId);
    if (w?.skills && isRanged(w) && !recentlyTalked(ev.source)) pressStart(ev.source, w, "start");
  });
  after.itemReleaseUse.subscribe((ev) => pressEnd(ev.source));
  after.itemStopUse.subscribe((ev) => pressEnd(ev.source));
  after.itemCompleteUse.subscribe((ev) => pressComplete(ev.source));
  system.runInterval(hudTick, SKILLS.hudEvery);
}

export function forgetSkills(playerId) {
  presses.delete(playerId);
  for (const key of nextFree.keys()) if (key.startsWith(`${playerId}|`)) nextFree.delete(key);
  noFallUntil.delete(playerId);
  domes.delete(playerId);
  for (const key of cooldowns.keys()) if (key.startsWith(`${playerId}|`)) cooldowns.delete(key);
  for (const key of readyAnnounced) if (key.startsWith(`${playerId}|`)) readyAnnounced.delete(key);
}
