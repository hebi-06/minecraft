import { EntityDamageCause, GameMode, system } from "@minecraft/server";
import { BOSS, BOSSES } from "./config.js";
import { playersIn } from "./arena.js";
import { isSteadfast, markNoBlock } from "./combat.js";
import { play, showFaceText } from "./fx.js";
import { isDying, showRealTitle } from "./hp.js";
import { fx } from "./skills.js";

/**
 * 보스 도구 (v1.8.0): 엔진(bosses.js)과 보스별 패턴(boss_*.js)이 같이 씀
 * - 보스 피해(bossHit): 바닐라 근접은 막고 스크립트 공격만 통과 (bosses.js 의 before 이벤트 훅이 isOwnHit 로 확인)
 * - 바닥 예고: 모두 둥근 판(rpg:tele_disc)과 차오르는 판(rpg:tele_fill)으로 그림 (방향에 상관없는 모양이라 게임에서 돌아가 보일 걱정이 없음)
 *   · 원: 판 하나 + 차오르는 판 / 선: 선을 따라 작은 판 / 부채꼴: 극좌표 격자에 작은 판
 */

const PLAYER = "minecraft:player";

/**
 * 보스 이름 (v2.2.0): "[Lv.레벨] 수식어 이름". 머리 위 이름표와 보스 막대 이름이 같은 글
 * (보스 막대 이름은 build.py 가 엔티티 minecraft:boss 의 name 에 같은 모양으로 넣음 — boss_label)
 * @param {number} level
 * @param {string | undefined} title 수식어 (없으면 뺌)
 * @param {string} name
 */
export function bossLabel(level, title, name) {
  return [`§e[Lv.${level}]`, title ? `§6${title}` : "", `§c${name}`].filter((s) => s).join(" ");
}

/** 스크립트가 넣는 보스 공격: "맞는 쪽|보스" → 틱 */
const ownHits = new Map();

export function markOwn(target, src) {
  ownHits.set(`${target.id}|${src.id}`, system.currentTick);
}
export function isOwnHit(target, src) {
  const t = ownHits.get(`${target.id}|${src.id}`);
  return t !== undefined && system.currentTick - t <= 1;
}
export function cleanOwn(now) {
  for (const [k, t] of ownHits) if (now - t > 2) ownHits.delete(k);
}

/** 공격해도 되는 플레이어: 살아 있고, 서바이벌·모험, 쓰러지는 중 아님 */
export function okTarget(p) {
  try {
    if (!p?.isValid || p.typeId !== PLAYER) return false;
    const gm = p.getGameMode();
    if (gm === GameMode.Creative || gm === GameMode.Spectator) return false;
    return !isDying(p);
  } catch {
    return false;
  }
}

/** 싸움에 들어간 것으로 보는 플레이어: 필드 + BOSS.combatMargin 칸 (울타리·성문 자리까지) 안, 싸울 수 있는 상태 */
export function fighters(enc) {
  return playersIn(enc.a, BOSS.combatMargin).filter(okTarget);
}

/** 가로 거리 */
export function hdist(a, b) {
  return Math.hypot(a.x - b.x, a.z - b.z);
}

/**
 * v2.20.0 평타 대상 고르기 (아이기스의 방패 도발): 평타가 닿는 사람들 (pool) 가운데 그 보스 (마리) 를 도발한 사람이 있으면 그 사람 (도발 중일 때만),
 * 없으면 from 에서 가로로 가장 가까운 사람. pool 이 비면 undefined. 패턴 (보스마다의 기술) 은 도발과 상관없이 고름
 * @param {any} u
 * @param {any[]} pool
 * @param {{x: number, z: number}} from
 */
export function meleeTarget(u, pool, from) {
  const t = u?.taunt;
  if (t && system.currentTick < t.until) {
    const tp = pool.find((p) => p.id === t.id);
    if (tp) return tp;
  }
  let best;
  let bd = Infinity;
  for (const p of pool) {
    const d = hdist(p.location, from);
    if (d < bd) {
      bd = d;
      best = p;
    }
  }
  return best;
}

/**
 * 사람이 서 있는 바닥 높이 (v2.3.0): 땅에 서 있으면 발 높이, 뛰거나 떨어지는 중이면 발밑 8칸 안의 막힌 블록 위 (없으면 fallback).
 * 발밑 원·떨어지는 자리를 공중에 그리지 않게
 * @param {any} p
 * @param {number} [fallback]
 */
export function groundUnder(p, fallback) {
  const l = p.location;
  if (p.isOnGround !== false) return l.y;
  try {
    for (let y = Math.floor(l.y); y >= Math.floor(l.y) - 8; y--) {
      const b = p.dimension.getBlock({ x: l.x, y: y - 1, z: l.z });
      if (b && !b.isAir && !b.isLiquid) return y;
    }
  } catch {
    // 로드되지 않은 곳
  }
  return fallback ?? l.y;
}

/** 던진 것(바위·덩이) 파티클의 중력 (칸/초², build.py BOULDER_GRAVITY 와 같음: rpg:boulder · rpg:glob) */
export const LOB_GRAVITY = 20;

/**
 * from 에서 던져 t 초 뒤 to 에 떨어지는 포물선의 처음 방향·빠르기 (중력 g)
 * @returns {{dir: {x: number, y: number, z: number}, speed: number}}
 */
export function lobArc(from, to, t, g = LOB_GRAVITY) {
  const v = { x: (to.x - from.x) / t, y: (to.y - from.y) / t + (g * t) / 2, z: (to.z - from.z) / t };
  const speed = Math.hypot(v.x, v.y, v.z) || 1;
  return { dir: { x: v.x / speed, y: v.y / speed, z: v.z / speed }, speed };
}

/** 보스 몸 크기 (크기 배율 포함): 가로 반폭, 키 */
export function bossSize(def) {
  return { half: (def.box[0] * def.scale) / 2, height: def.box[1] * def.scale };
}

/**
 * 보스 피해 한 번 (스크립트). 들어갔으면 true
 * directional = 보스 쪽에서 오는 공격 (평타): 방패를 보스 쪽으로 웅크려 막을 수 있음. 나머지(발밑 폭발·까마귀·기믹 벌)는 방향 막기 없음
 * 여러 마리 싸움은 때리는 마리(unit)를 넘김 (그 마리가 공격한 것으로, 분노 배율 dmgMult 가 곱해짐)
 * @param {any} enc 싸움 또는 마리
 * @param {any} target 플레이어
 * @param {number} amount 바닐라 단위 (갑옷·체력 스탯은 게임·hp.js)
 * noSource = 때린 엔티티 없이 (v2.1.0 떨어지는 돌: 게임이 보스 쪽에서 밀어내는 넉백을 넣지 않게)
 * @param {{cause?: string, knock?: {x: number, z: number, h: number}, from?: any, effect?: {id: string, ticks: number, amp?: number}, directional?: boolean, noSource?: boolean}} [o]
 */
export function bossHit(enc, target, amount, o = {}) {
  const src = o.from ?? enc.boss;
  if (!src?.isValid || !okTarget(target) || !(amount > 0)) return false;
  markOwn(target, src);
  if (!o.directional) markNoBlock(target);
  const dmg = amount * (enc.dmgMult ?? 1);
  let ok = false;
  try {
    ok = target.applyDamage(dmg, o.noSource ? { cause: o.cause ?? EntityDamageCause.fallingBlock } : { cause: o.cause ?? EntityDamageCause.entityAttack, damagingEntity: src });
  } catch {
    ok = false;
  }
  if (o.knock && !isSteadfast(target)) {
    // v2.24.0 강철 근육 (신체 강화): 밀려나지 않음
    try {
      target.applyKnockback({ x: o.knock.x, z: o.knock.z }, o.knock.h);
    } catch {
      // 무시
    }
  }
  if (ok && o.effect) {
    try {
      target.addEffect(o.effect.id, o.effect.ticks, { amplifier: o.effect.amp ?? 0, showParticles: true });
    } catch {
      // 무시
    }
  }
  return ok;
}

/** 필드 둘레의 플레이어 (알림용: 필드 + 8칸) */
export function audience(enc) {
  return playersIn(enc.a, 8);
}

export function say(enc, text, list) {
  for (const p of list ?? audience(enc)) {
    try {
      p.sendMessage(text);
    } catch {
      // 무시
    }
  }
}

/** 큰 글자 (list 가 없으면 필드 둘레 모두) */
export function title(enc, big, small = "", list) {
  for (const p of list ?? audience(enc)) {
    try {
      showRealTitle(p, big, small);
    } catch {
      // 나간 플레이어 등
    }
  }
}

/** 모두의 눈앞 글자 (카운트다운 등, list 가 없으면 필드 둘레 모두) */
export function faceAll(enc, text, o, list) {
  for (const p of list ?? audience(enc)) {
    try {
      showFaceText(p, text, o);
    } catch {
      // 무시
    }
  }
}

export function sound(enc, id, at, pitch = 1, volume = 1) {
  play(enc.dim, id, at ?? enc.boss?.location ?? enc.center, pitch, volume);
}

// ------------------------------------------------------------ 바닥 예고
const RED = [1.0, 0.22, 0.16];

/**
 * 원 (반지름 radius): 테두리 판 + ticks 동안 가운데서 차오르는 판 (다 차면 터짐)
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} at 발 높이
 */
export function disc(dim, at, radius, ticks, color = RED, o = {}) {
  const life = Math.max(0.1, ticks / 20);
  fx(dim, "rpg:tele_disc", { x: at.x, y: at.y + 0.06, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life, color, alpha: o.alpha ?? 0.85 });
  if (o.fill !== false) fx(dim, "rpg:tele_fill", { x: at.x, y: at.y + 0.08, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life, color, alpha: 0.55 });
}

/** 선 (from → to, 폭 width): 선을 따라 작은 판 */
export function lineMarks(dim, from, to, width, ticks, color = RED) {
  const dx = to.x - from.x;
  const dz = to.z - from.z;
  const L = Math.hypot(dx, dz);
  if (L < 0.1) return;
  const step = Math.max(0.6, width * 0.9);
  const n = Math.max(2, Math.ceil(L / step));
  const life = Math.max(0.1, ticks / 20);
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    fx(dim, "rpg:tele_fill", { x: from.x + dx * t, y: from.y + 0.07, z: from.z + dz * t }, { dir: { x: 0, y: 0, z: 0 }, size: width, life, color, alpha: 0.5, grow: 1 });
  }
}

/**
 * 부채꼴 (꼭짓점 apex, 방향 dir(가로 단위), 반지름 radius, 벌린 각 angle 도): 극좌표 격자에 작은 판 + 테두리.
 * step = 격자 간격 (칸, 큰 부채꼴은 1.5 로 파티클 수를 줄임 — 판도 그만큼 크게)
 */
export function fanMarks(dim, apex, dir, radius, angle, ticks, color = RED, step = 1) {
  const base = Math.atan2(dir.z, dir.x);
  const half = (angle * Math.PI) / 360;
  const life = Math.max(0.1, ticks / 20);
  for (let r = step; r <= radius + 0.01; r += step) {
    const n = Math.max(2, Math.ceil((r * 2 * half) / step));
    for (let i = 0; i <= n; i++) {
      const a = base - half + (2 * half * i) / n;
      const edge = r + step > radius + 0.01 || i === 0 || i === n;
      fx(dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", { x: apex.x + Math.cos(a) * r, y: apex.y + 0.07, z: apex.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: (edge ? 0.55 : 0.75) * step, life, color, alpha: edge ? 0.9 : 0.45, grow: edge ? 0 : 1 });
    }
  }
}

/** 점 p 가 부채꼴 안인지 (가로, pad 만큼 너그럽게) */
export function inFan(p, apex, dir, radius, angle, pad = 0) {
  const dx = p.x - apex.x;
  const dz = p.z - apex.z;
  const d = Math.hypot(dx, dz);
  if (d > radius + pad) return false;
  if (d < 0.8) return true;
  const cos = (dx * dir.x + dz * dir.z) / d;
  return cos >= Math.cos(((angle / 2) * Math.PI) / 180) - 1e-9;
}

/** 점 p 가 선분 a-b 에서 width 안인지 (가로) */
export function nearSegment(p, a, b, width) {
  const dx = b.x - a.x;
  const dz = b.z - a.z;
  const L2 = dx * dx + dz * dz;
  const t = L2 > 0 ? Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.z - a.z) * dz) / L2)) : 0;
  return Math.hypot(p.x - (a.x + dx * t), p.z - (a.z + dz * t)) <= width;
}

/** 보스 설정 */
export function bossDef(key) {
  return BOSSES[key];
}

// ------------------------------------------------------------ 보스 안개 (v2.12.0 공통: /fog 이름표 rpg_boss_fog — v2.5.0 이미르와 같은 규칙)
export const BOSS_FOG_TAG = "rpg_boss_fog";

/**
 * 안개 바꾸기: had (이 싸움이 씌운 안개가 있음) 이면 먼저 걷고, id 가 있으면 씌움. 씌웠으면 true.
 * 지우기와 씌우기는 따로 (씌운 적 없는 안개를 지우는 명령이 실패해도 씌움)
 */
export function setBossFog(p, id, had) {
  if (had) {
    try {
      p.runCommand(`fog @s remove ${BOSS_FOG_TAG}`);
    } catch {
      // 이미 없음·나간 사람
    }
  }
  if (!id) return false;
  try {
    p.runCommand(`fog @s push ${id} ${BOSS_FOG_TAG}`);
    return true;
  } catch {
    return false;
  }
}

/**
 * 매 틱 (바뀔 때)·10틱마다: 필드 둘레 사람의 안개를 want 로 맞추고, 떠난 사람은 걷음. 기억은 mem.fog (플레이어 id → {p, id}) · mem.fogWant
 * @param {any} u
 * @param {string | undefined} want
 * @param {number} now
 */
export function syncBossFog(u, want, now, force = false) {
  const m = u.mem;
  m.fog ??= new Map();
  if (!force && want === m.fogWant && now % 10 !== 0) return;
  m.fogWant = want;
  const here = new Set();
  for (const p of audience(u)) {
    here.add(p.id);
    const cur = m.fog.get(p.id);
    if ((cur?.id ?? undefined) === want) continue;
    // 씌우지 못하면 기억하지 않음 (다음 0.5초에 다시)
    if (setBossFog(p, want, !!cur)) m.fog.set(p.id, { p, id: want });
    else m.fog.delete(p.id);
  }
  for (const [id, f] of [...m.fog]) {
    if (here.has(id)) continue;
    if (f.p?.isValid) setBossFog(f.p, undefined, true);
    m.fog.delete(id);
  }
}

/** 싸움이 끝나면 이 싸움이 씌운 안개를 모두 걷음 */
export function clearBossFog(enc) {
  const m = enc.mem;
  if (!m?.fog) return;
  for (const f of m.fog.values()) if (f.p?.isValid) setBossFog(f.p, undefined, true);
  m.fog.clear();
  m.fogWant = undefined;
}
