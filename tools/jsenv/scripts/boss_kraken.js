import { EntityDamageCause, system } from "@minecraft/server";
import { playersIn } from "./arena.js";
import { isSteadfast } from "./combat.js";
import { play, showFaceText } from "./fx.js";
import { beam, fx, shake } from "./skills.js";
import { kill, root, silence } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fanMarks, fighters, hdist, inFan, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 크라켄 (v2.0.0, Lv.25): 수치는 config.js BOSSES.kraken
 * 움직이지 않음 (가라앉은 신전 가운데에 박힘). 패턴마다 대상 쪽으로 몸을 돌림 (엔진 face, 모델 앞 = 부리)
 * - 평타 물어뜯기 (앞 100°, 엔진)
 * - 촉수 내려치기: 2~3명 쪽으로 차례로 몸을 돌려 앞의 두 촉수를 내려침 (부채꼴) → 피해 + 휘감김 (속박)
 * - 먹물 분사: 한 명 쪽 부채꼴 → 피해 + 실명 + 침묵, 먹물 웅덩이 (밟고 있으면 느림 + 침묵 + 마법 피해)
 * - 소용돌이 (체력 85% 아래): 가운데로 끌어당김 → 끝날 때 가운데 crush 칸 안은 큰 피해 (바깥으로 달려야 함)
 * - 촉수 소환 (체력 80%·45%): 네 물구덩이에서 촉수 (맞는 소품). 살아 있는 동안 크라켄 회복 + 둘레 휘두르기 (속박).
 *   모두 베면 크라켄 그로기 (받는 피해 증가), life 틱 안에 못 베면 물러남
 * - 해일 (체력 60%·25%, 촉수가 없을 때): 한쪽에서 큰 물결 → 물결이 지나갈 때 기둥 그림자에 없으면 즉사 (먹물 웅덩이도 씻겨 나감)
 * - 높은 곳 (기둥·성문 위): 촉수가 끌어내림 (패턴이 닿지 않는 곳에서 버티지 못하게)
 * 큰 패턴(소용돌이·촉수 소환·해일)은 서로 겹치지 않음 (mem.bigUntil). 촉수가 살아 있는 동안에는 물어뜯기·먹물만
 */

export const TENTACLE = "rpg:prop_kraken_tentacle";
const TENT_ANIM = "animation.rpg.prop.kraken_tentacle";
const SEA = [0.3, 0.78, 0.9];
const FOAM = [0.86, 0.96, 1.0];
const DEEP = [0.16, 0.42, 0.85];
const INK = [0.12, 0.05, 0.2];
const INK_MARK = [0.62, 0.3, 0.95];
const HEAL = [0.35, 1.0, 0.55];
/** 해일 그림자 자리: 청록 바닥 위에서 잘 보이게 금빛 */
const SAFE = [1.0, 0.85, 0.2];
const RED = [1.0, 0.25, 0.15];
/** 내려치기 예고: 청록 바닥 위에서 잘 보이게 주황빛 붉은색 */
const SLAM_MARK = [1.0, 0.42, 0.12];
const GOLD = [1.0, 0.85, 0.35];

const rnd = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const shuffle = (list) => {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};
const every = (u, range) => Math.round(rnd(range[0], range[1]) * (u.cdMult ?? 1));
const floorAt = (u, x, z) => ({ x, y: u.center.y, z });
/** 가로 단위 방향 a → b */
function dirTo(a, b) {
  const dx = b.x - a.x;
  const dz = b.z - a.z;
  const L = Math.hypot(dx, dz);
  return L < 1e-6 ? { x: 0, y: 0, z: 1 } : { x: dx / L, y: 0, z: dz / L };
}
/** 방향을 가로로 deg 도 돌림 (+x 에서 +z 쪽으로) */
function turned(d, deg) {
  const a = Math.atan2(d.z, d.x) + (deg * Math.PI) / 180;
  return { x: Math.cos(a), y: 0, z: Math.sin(a) };
}

/** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
function explain(u, id, text) {
  const told = u.mem.told;
  if (told.has(id)) return;
  told.add(id);
  say(u, text);
}

/** 몸 가운데 (끈·연출용) */
function mid(u) {
  const l = u.boss.location;
  return { x: l.x, y: l.y + bossSize(u.def).height * 0.55, z: l.z };
}

/** 물보라 (rpg:splash: 튀어 오르는 물방울 왕관, 먹물이면 검게). 그림의 밑동(아래 1/8)이 바닥에 닿게 올림 */
function splash(dim, at, size, color = FOAM, life = 0.55) {
  fx(dim, "rpg:splash", { x: at.x, y: at.y + size * 0.75, z: at.z }, { size, life, color, alpha: 0.95 });
}

/** 마지막 모습을 유지하는 촉수 동작 (다음 동작이 같은 틀에서 바꿀 때까지): 감아올림·물러남·쓰러짐 */
const TENT_HOLD = new Set(["coil", "sink", "die"]);
/** 촉수 소품 동작: 모두 한 틀(rpg_tent)에서 → 휘두르기가 감아올림을 바꾸고, 유지하는 동작은 기본 멈춤식(끝나면 멈춤) 대신 "0" */
function tentAnim(e, name) {
  try {
    if (e?.isValid) e.playAnimation(`${TENT_ANIM}.${name}`, TENT_HOLD.has(name) ? { controller: "rpg_tent", stopExpression: "0" } : { controller: "rpg_tent" });
  } catch {
    // 표시만
  }
}

/** 쓰러지거나 물러나는 소품: 스킬·원거리 조준에서 빠짐 (skills.js canHit·combat.js 가 rpg_gone 을 건너뜀) */
function gone(e) {
  try {
    e.addTag("rpg_gone");
  } catch {
    // 무시
  }
}

/** 둘레의 사람들 화면 흔들기 */
function shakeNear(u, at, range, intensity, seconds) {
  for (const p of fighters(u)) if (hdist(p.location, at) <= range) shake(p, intensity, seconds);
}

// ------------------------------------------------------------ 촉수 내려치기
/**
 * 부채꼴 안인지 (촉수 내려치기·먹물): 가로 + 높이 3칸 안
 * @param {any} p 플레이어
 */
function inCone(p, apex, dir, radius, cone) {
  return inFan(p.location, apex, dir, radius, cone, 0.5) && Math.abs(p.location.y - apex.y) < 3;
}

function slam(u, now, list) {
  const d = u.def.slam;
  const api = u.api;
  const c = u.center;
  const near = shuffle(list.filter((p) => hdist(p.location, c) <= d.radius + 1));
  if (!near.length) return false;
  const n = api.ratio(u) <= d.low ? d.countLow : d.count;
  const dirs = near.slice(0, n).map((p) => dirTo(c, p.location));
  const apex = floorAt(u, c.x, c.z);
  const at = dirs.map((_, i) => d.windup + i * d.gap);
  dirs.forEach((dir, i) => fanMarks(u.dim, apex, dir, d.radius, d.cone, at[i], SLAM_MARK, 1.5));
  /** 이번 내려치기에서 맞은 사람 (한 번만) */
  const struck = new Set();
  api.face(u, dirs[0]);
  sound(u, "mob.elderguardian.idle", mid(u), 0.7, 1);
  explain(u, "slam", `§b[보스] 촉수 내려치기: §f크라켄이 몸을 돌려 앞의 두 촉수로 부채꼴을 내려칩니다. §e맞으면 ${(d.root / 20).toFixed(1)}초 휘감겨 움직일 수 없습니다`);
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  dirs.forEach((dir, i) => {
    // 앞 내려치기가 끝나면 다음 쪽으로 몸을 돌림 (동작은 부딪히기 0.9초 전부터: 촉수를 들어 올리는 동안 마저 돎)
    if (i > 0) steps.push([at[i - 1] + 2, (x) => api.face(x, dir)]);
    steps.push([at[i] - 18, (x) => api.anim(x, "slam")]);
    steps.push([at[i], (x) => slamHit(x, apex, dir, struck)]);
  });
  // 끝나면 1초 동안 물어뜯기 없음 (휘감김이 풀린 뒤 피할 틈)
  steps.push([at[at.length - 1] + 12, (x) => (x.meleeAt = Math.max(x.meleeAt, system.currentTick + 20))]);
  api.cast(u, "slam", at[at.length - 1] + 14, steps);
  return true;
}

function slamHit(u, apex, dir, struck) {
  const d = u.def.slam;
  // 앞의 두 촉수 (±22.5°) 가 떨어지는 줄을 따라 물보라, 끝에 고리
  for (const side of [-22.5, 0, 22.5]) {
    const g = turned(dir, side);
    for (let s = 3; s <= d.radius - 0.5; s += 2.5) splash(u.dim, { x: apex.x + g.x * s, y: apex.y, z: apex.z + g.z * s }, side === 0 ? 1.0 : 1.5);
    if (side !== 0) fx(u.dim, "rpg:shock_ring", { x: apex.x + g.x * (d.radius - 1.5), y: apex.y + 0.1, z: apex.z + g.z * (d.radius - 1.5) }, { dir: { x: 0, y: 0, z: 0 }, size: 1.6, life: 0.45, color: SEA, alpha: 0.9 });
  }
  fx(u.dim, "rpg:bash_burst", { x: apex.x + dir.x * 6, y: apex.y + 0.8, z: apex.z + dir.z * 6 }, { dir: { x: 0, y: 0, z: 1 }, size: 3.2, life: 0.4, color: SEA, alpha: 0.9 });
  play(u.dim, "random.explode", { x: apex.x + dir.x * 6, y: apex.y, z: apex.z + dir.z * 6 }, 0.7, 0.9);
  play(u.dim, "entity.generic.splash", { x: apex.x + dir.x * 6, y: apex.y, z: apex.z + dir.z * 6 }, 0.6, 1);
  shakeNear(u, apex, d.radius + 4, 0.2, 0.3);
  const side = { x: -dir.z, z: dir.x };
  for (const p of fighters(u)) {
    if (struck.has(p.id) || !inCone(p, apex, dir, d.radius, d.cone)) continue;
    // 옆으로 밀림 (촉수가 떨어진 쪽 바깥으로)
    const s = (p.location.x - apex.x) * side.x + (p.location.z - apex.z) * side.z >= 0 ? 1 : -1;
    if (!bossHit(u, p, d.damage, { knock: { x: side.x * s * 0.5, z: side.z * s * 0.5, h: 0.3 } })) continue;
    struck.add(p.id);
    root(p, d.root, "휘감김");
    fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.4, life: 0.4, color: SEA, alpha: 0.9 });
  }
}

// ------------------------------------------------------------ 먹물 분사
function ink(u, now, list) {
  const d = u.def.ink;
  const api = u.api;
  const c = u.center;
  const cand = list.filter((p) => hdist(p.location, c) <= d.range);
  if (!cand.length) return false;
  const t = cand[Math.floor(Math.random() * cand.length)];
  const dir = dirTo(c, t.location);
  const radius = Math.max(8, Math.min(d.range, hdist(t.location, c) + 2.5));
  // 먼 사람(가장자리까지)에게는 좁은 줄기
  const cone = radius > 13 ? d.coneFar : d.cone;
  const apex = floorAt(u, c.x, c.z);
  api.face(u, dir);
  api.anim(u, "ink");
  fanMarks(u.dim, apex, dir, radius, cone, d.windup, INK_MARK, 1.5);
  // 실명이 끝날 때까지 내려치기 없음 (예고를 볼 수 있게)
  u.mem.next.slam = Math.max(u.mem.next.slam, now + d.windup + d.blind);
  sound(u, "mob.squid.ambient", mid(u), 0.5, 1);
  explain(u, "ink", `§5[보스] 먹물 분사: §f맞으면 실명 + 스킬 봉인(침묵). §d바닥의 먹물 웅덩이§f를 밟고 있으면 느려지고 계속 봉인됩니다`);
  // 웅덩이 자리: 대상이 있던 곳 + 부채꼴 안 아무 데나 (가운데 4칸 밖)
  const spots = [floorAt(u, t.location.x, t.location.z)];
  for (let i = 1; i < d.puddles; i++) {
    const g = turned(dir, (Math.random() - 0.5) * cone * 0.8);
    const s = 4 + Math.random() * (radius - 5);
    spots.push(floorAt(u, c.x + g.x * s, c.z + g.z * s));
  }
  api.cast(u, "ink", d.windup + 12, [
    [d.windup - 3, (x) => sound(x, "mob.squid.ink_squirt", mid(x), 0.6, 1)],
    [d.windup, (x) => inkHit(x, apex, dir, radius, cone, spots)],
  ]);
  return true;
}

function inkHit(u, apex, dir, radius, cone, spots) {
  const d = u.def.ink;
  const now = system.currentTick;
  // 먹물 줄기: 부채꼴을 따라 검은 물보라
  for (const side of [-cone * 0.35, 0, cone * 0.35]) {
    const g = turned(dir, side);
    for (let s = 2.5; s <= radius; s += 2.2) {
      const at = { x: apex.x + g.x * s, y: apex.y, z: apex.z + g.z * s };
      splash(u.dim, at, 1.2, INK, 0.6);
      if (side === 0) fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1.1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.6, life: 0.45, color: INK, alpha: 0.9 });
    }
  }
  play(u.dim, "mob.glow_squid.ink_squirt", apex, 0.5, 1);
  for (const p of fighters(u)) {
    if (!inCone(p, apex, dir, radius, cone)) continue;
    if (!bossHit(u, p, d.damage, { effect: { id: "blindness", ticks: d.blind } })) continue;
    silence(p, d.silence);
    showFaceText(p, "§5§l먹물! §7스킬 봉인", { dist: 2.2, up: 0.35 });
  }
  for (const s of spots) {
    u.mem.puddles.push({ ...s, r: d.puddleRadius, until: now + d.puddleLife });
    splash(u.dim, s, 1.8, INK, 0.6);
  }
}

/** 먹물 웅덩이: 보이기 (10틱마다 새로), 1초마다 밟은 사람은 느림 + 침묵 + 마법 피해 */
function puddleTick(enc, now) {
  const m = enc.mem;
  if (!m.puddles.length) return;
  m.puddles = m.puddles.filter((pd) => now < pd.until);
  const u = enc.units[0];
  const d = u.def.ink;
  if (now % 10 === 0) {
    for (const pd of m.puddles) {
      fx(enc.dim, "rpg:tele_fill", { x: pd.x, y: pd.y + 0.07, z: pd.z }, { dir: { x: 0, y: 0, z: 0 }, size: pd.r, life: 0.7, color: INK, alpha: 0.8, grow: 1 });
      fx(enc.dim, "rpg:tele_disc", { x: pd.x, y: pd.y + 0.09, z: pd.z }, { dir: { x: 0, y: 0, z: 0 }, size: pd.r, life: 0.7, color: INK_MARK, alpha: 0.7 });
    }
  }
  if (now % 16 === 0) for (const pd of m.puddles) splash(enc.dim, { x: pd.x + (Math.random() - 0.5) * pd.r, y: pd.y, z: pd.z + (Math.random() - 0.5) * pd.r }, 0.6, INK, 0.5);
  if (now % 20 !== 0 || !u.alive) return;
  for (const p of fighters(enc)) {
    if (!m.puddles.some((pd) => hdist(p.location, pd) <= pd.r + 0.3 && Math.abs(p.location.y - pd.y) < 2)) continue;
    try {
      p.addEffect("slowness", 40, { amplifier: 1, showParticles: true });
    } catch {
      // 무시
    }
    silence(p, 30);
    bossHit(u, p, d.puddleDamage, { cause: EntityDamageCause.magic });
    if (now - (m.inkTold.get(p.id) ?? -100) >= 60) {
      m.inkTold.set(p.id, now);
      showFaceText(p, "§5먹물 웅덩이 §7— 빠져나오세요", { dist: 2.4, up: 0.3 });
    }
  }
}

// ------------------------------------------------------------ 소용돌이
function whirl(u, now) {
  const d = u.def.whirl;
  const api = u.api;
  const m = u.mem;
  const c = u.center;
  m.bigUntil = now + d.time + 60;
  m.whirl = { t0: now, until: now + d.time };
  api.anim(u, "spin"); // 동작 길이 = time (build.py): 다섯 바퀴 돌고 끝에 모든 촉수로 내려침
  title(u, "§3§l소용돌이", `§b바깥으로 달리세요! §7(가운데 ${d.crush}칸은 짓눌립니다)`, fighters(u));
  explain(u, "whirl", `§3[보스] 소용돌이: §f${Math.round(d.time / 20)}초 동안 가운데로 끌려갑니다. §c끝날 때 가운데 ${d.crush}칸 안에 있으면 크게 다칩니다 §f— 바깥으로 달리세요`);
  sound(u, "ambient.underwater.enter", c, 0.6, 1);
  sound(u, "mob.elderguardian.curse", c, 0.7, 0.8);
  disc(u.dim, floorAt(u, c.x, c.z), d.crush, d.time, RED);
  api.cast(u, "whirl", d.time + 26, [[d.time, (x) => whirlCrush(x)]]);
}

/** 소용돌이 도는 동안: 바닥 물살 무늬, tug 틱마다 가운데로 끌어당김 */
function whirlTick(enc, now) {
  const w = enc.mem.whirl;
  if (!w) return;
  const u = enc.units[0];
  const d = u.def.whirl;
  const c = enc.center;
  const age = now - w.t0;
  if (now >= w.until || !u.alive) {
    enc.mem.whirl = undefined;
    return;
  }
  if (age % 8 === 0) fx(enc.dim, "rpg:whirlpool", { x: c.x, y: c.y + 0.12, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius, life: 0.6, spin: age * 9, speed: -220, color: DEEP, alpha: 0.75 });
  if (age % 4 === 0) {
    // 네 갈래 나선 물보라 (안으로 말려 들어감)
    for (let k = 0; k < 4; k++) {
      for (const r of [4, 7, 10, 13, 16]) {
        const a = (k * Math.PI) / 2 - age * 0.12 + r * 0.22;
        splash(enc.dim, { x: c.x + Math.cos(a) * r, y: c.y, z: c.z + Math.sin(a) * r }, 0.7, FOAM, 0.4);
      }
    }
  }
  if (age % 30 === 0) play(enc.dim, "bubble.down", c, 0.7, 1);
  if (age % d.tug !== 0) return;
  for (const p of fighters(enc)) {
    const r = hdist(p.location, c);
    if (r > d.radius || r < 2.2 || isSteadfast(p)) continue; // v2.24.0 강철 근육: 끌려가지 않음
    const dir = dirTo(p.location, c);
    try {
      p.applyKnockback({ x: dir.x * d.strength, z: dir.z * d.strength }, 0);
    } catch {
      // 무시
    }
  }
}

function whirlCrush(u) {
  const d = u.def.whirl;
  const c = u.center;
  u.mem.whirl = undefined;
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    splash(u.dim, { x: c.x + Math.cos(a) * d.crush, y: c.y, z: c.z + Math.sin(a) * d.crush }, 1.8);
  }
  fx(u.dim, "rpg:shock_ring", { x: c.x, y: c.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.crush / 2.1, life: 0.5, color: SEA, alpha: 0.95 });
  fx(u.dim, "rpg:bash_burst", { x: c.x, y: c.y + 1.5, z: c.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.crush, life: 0.5, color: DEEP, alpha: 0.9 });
  sound(u, "random.explode", c, 0.6, 1);
  sound(u, "entity.generic.splash", c, 0.5, 1);
  shakeNear(u, c, d.radius, 0.3, 0.4);
  for (const p of fighters(u)) {
    const r = hdist(p.location, c);
    if (r > d.crush + 0.3 || Math.abs(p.location.y - c.y) > 3) continue;
    const dir = dirTo(c, p.location);
    bossHit(u, p, d.damage, { knock: { x: dir.x * 0.9, z: dir.z * 0.9, h: 0.6 } });
  }
}

// ------------------------------------------------------------ 촉수 소환
function hpTag(cur, max) {
  const n = Math.max(0, Math.min(10, Math.ceil((cur / max) * 10)));
  return `§3크라켄의 촉수\n§a${"|".repeat(n)}§8${"|".repeat(10 - n)} §f${Math.ceil(cur)}`;
}

function summonAdds(u, now) {
  const T = u.def.tentacles;
  const api = u.api;
  const m = u.mem;
  const holes = u.feat.holes ?? [];
  if (!holes.length) return false;
  m.bigUntil = now + 60;
  m.adds = { list: [], t0: now, until: now + 14 + T.life };
  api.anim(u, "summon");
  title(u, "§3§l촉수 소환", "§b촉수를 모두 베세요! §7(살아 있는 동안 크라켄이 회복)", fighters(u));
  explain(u, "adds", `§3[보스] 촉수 소환: §f네 물구덩이의 촉수가 살아 있는 동안 §a크라켄이 회복§f합니다. §e${Math.round(T.life / 20)}초 안에 모두 베면 크라켄이 그로기 (받는 피해 +${Math.round(T.bonus * 100)}%)`);
  sound(u, u.def.sounds.roar, mid(u), 0.6, 1);
  for (const h of holes) disc(u.dim, h, T.radius, 14, SEA, { fill: false });
  api.cast(u, "summon", 40, [[14, (x) => spawnAdds(x, holes)]]);
  return true;
}

function spawnAdds(u, holes) {
  const T = u.def.tentacles;
  const m = u.mem;
  const now = system.currentTick;
  if (!m.adds) return;
  holes.forEach((h, i) => {
    let e;
    try {
      e = u.dim.spawnEntity(TENTACLE, { x: h.x, y: h.y - 1, z: h.z });
    } catch (err) {
      console.warn(`[rpg] kraken tentacle: ${err}`);
      return;
    }
    try {
      e.teleport({ x: h.x, y: h.y - 1, z: h.z }, { facingLocation: { x: u.center.x, y: h.y + 2, z: u.center.z } });
      e.nameTag = hpTag(T.hp, T.hp);
    } catch {
      // 무시
    }
    u.api.addProp(u, e, { hp: T.hp, textHeight: 4 });
    tentAnim(e, "rise");
    splash(u.dim, h, 2.2);
    fx(u.dim, "rpg:shock_ring", { x: h.x, y: h.y + 0.1, z: h.z }, { dir: { x: 0, y: 0, z: 0 }, size: 1.5, life: 0.5, color: SEA, alpha: 0.9 });
    play(u.dim, "mob.guardian.flop", h, 0.6, 1);
    // 휘두르기는 나온 뒤 2초부터, 촉수마다 1초씩 어긋나게
    m.adds.list.push({ e, hole: h, next: now + 40 + i * 20, hitAt: 0, dead: false });
  });
  play(u.dim, "entity.generic.splash", u.center, 0.5, 1);
}

/** 촉수: 휘두르기 (예고 → 맞음), 10틱마다 크라켄 회복 (초록 끈), 모두 베면 그로기, 시간이 다 되면 물러남 */
function addsTick(enc, now) {
  const A = enc.mem.adds;
  if (!A) return;
  const u = enc.units[0];
  const T = u.def.tentacles;
  if (!A.list.length) {
    if (now > A.t0 + 20) enc.mem.adds = undefined; // 하나도 나오지 않음 (소환 실패)
    return;
  }
  const alive = A.list.filter((a) => !a.dead && a.e.isValid);
  if (!alive.length) {
    enc.mem.adds = undefined;
    if (u.alive) stagger(u, now);
    return;
  }
  if (now >= A.until) {
    retreat(u, alive, now);
    enc.mem.adds = undefined;
    return;
  }
  for (const a of alive) {
    if (!a.hitAt && now >= a.next) {
      a.hitAt = now + T.windup;
      a.next = now + T.every; // 주기 = every (예고 windup 포함)
      disc(enc.dim, a.hole, T.radius, T.windup, RED);
      tentAnim(a.e, "coil");
    } else if (a.hitAt && now >= a.hitAt) {
      a.hitAt = 0;
      whip(u, a);
    }
  }
  if (now % 10 === 0 && u.alive) {
    let hc;
    try {
      hc = u.boss.getComponent("minecraft:health");
    } catch {
      hc = undefined;
    }
    if (hc) {
      const add = hc.effectiveMax * T.heal * 0.5 * alive.length;
      try {
        hc.setCurrentValue(Math.min(hc.effectiveMax, hc.currentValue + add));
      } catch {
        // 무시
      }
      A.healed = (A.healed ?? 0) + add;
    }
    const to = mid(u);
    for (const a of alive) {
      const top = { x: a.hole.x, y: a.hole.y + 3.6, z: a.hole.z };
      beam(enc.dim, top, to, HEAL, { life: 0.55, half: 0.28, thick: 0.14, gap: 0.6 });
    }
    if (now % 20 === 0) fx(enc.dim, "rpg:bash_burst", to, { dir: { x: 0, y: 0, z: 1 }, size: 1.8, life: 0.4, color: HEAL, alpha: 0.8 });
  }
}

/** 촉수 휘두르기: 물구덩이 둘레 radius 칸 → 피해 + 속박 */
function whip(u, a) {
  const T = u.def.tentacles;
  const h = a.hole;
  tentAnim(a.e, "whip");
  for (let i = 0; i < 8; i++) {
    const ang = (i / 8) * Math.PI * 2;
    splash(u.dim, { x: h.x + Math.cos(ang) * T.radius * 0.8, y: h.y, z: h.z + Math.sin(ang) * T.radius * 0.8 }, 1.1);
  }
  fx(u.dim, "rpg:shock_ring", { x: h.x, y: h.y + 0.1, z: h.z }, { dir: { x: 0, y: 0, z: 0 }, size: T.radius / 2.1, life: 0.4, color: SEA, alpha: 0.9 });
  play(u.dim, "entity.generic.splash", h, 0.8, 1);
  play(u.dim, "mob.guardian.hit", h, 0.6, 0.8);
  for (const p of fighters(u)) {
    if (hdist(p.location, h) > T.radius + 0.3 || Math.abs(p.location.y - h.y) > 3) continue;
    const dir = dirTo(h, p.location);
    if (!bossHit(u, p, T.damage, { from: a.e, knock: { x: dir.x * 0.6, z: dir.z * 0.6, h: 0.3 } })) continue;
    root(p, T.root, "휘감김");
  }
}

/** 촉수를 모두 벰: 크라켄 그로기 (패턴 없음, 받는 피해 증가) */
function stagger(u, now) {
  const T = u.def.tentacles;
  u.mem.staggerUntil = now + T.stagger;
  u.staggerUntil = now + T.stagger;
  // 하던 패턴(물어뜯기·먹물)은 끊음: 비틀거리는 동작 뒤에서 예고 없이 맞지 않게
  u.cast = undefined;
  u.api.anim(u, "stagger");
  title(u, "§e§l그로기!", `§f크라켄이 받는 피해 +${Math.round(T.bonus * 100)}% §7(${Math.round(T.stagger / 20)}초)`);
  say(u, "§e[보스] 촉수를 모두 베었습니다! §f크라켄이 비틀거립니다");
  sound(u, "mob.elderguardian.hit", mid(u), 0.6, 1);
  fx(u.dim, "rpg:shock_ring", { x: u.center.x, y: u.center.y + 0.1, z: u.center.z }, { dir: { x: 0, y: 0, z: 0 }, size: 3, life: 0.6, color: GOLD, alpha: 0.9 });
}

/** 시간이 다 됨: 남은 촉수는 물속으로 (크라켄은 그만큼 회복한 채로) */
function retreat(u, alive, now) {
  for (const a of alive) {
    a.dead = true;
    u.api.retireProp(a.e);
    gone(a.e);
    tentAnim(a.e, "sink");
    splash(u.dim, a.hole, 2);
    u.mem.corpses.push({ e: a.e, at: now + 18 });
  }
  play(u.dim, "entity.generic.splash", u.center, 0.6, 1);
  say(u, `§3[보스] 남은 촉수 ${alive.length}개가 물속으로 물러났습니다 §7(크라켄이 회복했습니다)`);
}

/** 쓰러지거나 물러난 촉수: 동작이 끝나면 치움 */
function corpseTick(enc, now) {
  const m = enc.mem;
  if (!m.corpses.length) return;
  const due = m.corpses.filter((c) => now >= c.at);
  if (!due.length) return;
  m.corpses = m.corpses.filter((c) => now < c.at);
  for (const c of due) enc.api.dropProp(enc, c.e);
}

// ------------------------------------------------------------ 해일
/**
 * 기둥 그림자인지: p 에서 물결이 오는 쪽(-dir)으로 depth 칸 안에 기둥(가로 반폭 half + pad 상자)이 있으면 가려짐
 * @param {{x: number, z: number, half: number}[]} pillars
 * @param {{x: number, z: number}} dir 물결이 가는 방향 (단위)
 * @param {{x: number, z: number}} p
 */
export function sheltered(pillars, dir, p, depth, pad) {
  const rx = -dir.x;
  const rz = -dir.z;
  for (const c of pillars) {
    const h = c.half + pad;
    // 가로 선분 p → p + r·depth 와 상자 [c-h, c+h] 가 만나는지 (slab)
    let t0 = 0;
    let t1 = depth;
    let hit = true;
    for (const [o, r, lo, hi] of [
      [p.x, rx, c.x - h, c.x + h],
      [p.z, rz, c.z - h, c.z + h],
    ]) {
      if (Math.abs(r) < 1e-9) {
        if (o < lo || o > hi) hit = false;
        continue;
      }
      let a = (lo - o) / r;
      let b = (hi - o) / r;
      if (a > b) [a, b] = [b, a];
      t0 = Math.max(t0, a);
      t1 = Math.min(t1, b);
    }
    if (hit && t0 <= t1) return true;
  }
  return false;
}

/** 물결이 오는 쪽 이름 (예: 북동쪽) */
function sideName(dir) {
  const o = { x: -dir.x, z: -dir.z };
  const ns = o.z < -0.4 ? "북" : o.z > 0.4 ? "남" : "";
  const ew = o.x > 0.4 ? "동" : o.x < -0.4 ? "서" : "";
  return `${ns}${ew}쪽`;
}

function tide(u, now) {
  const d = u.def.tide;
  const api = u.api;
  const m = u.mem;
  const R = u.enc.def.arena.radius;
  const k = Math.floor(Math.random() * 8);
  const dir = { x: Math.round(Math.cos((k * Math.PI) / 4) * 1e6) / 1e6, y: 0, z: Math.round(Math.sin((k * Math.PI) / 4) * 1e6) / 1e6 };
  // 필드 밖 성문 자리(+6)까지 휩씀
  const s0 = -(R + 7);
  const s1 = R + 7;
  const travel = Math.ceil(((s1 - s0) / d.speed) * 20);
  m.bigUntil = now + d.windup + travel + 60;
  m.tide = { u, dir, t0: now, crashAt: now + d.windup, s0, s1, last: new Map(), hit: new Set() };
  api.face(u, { x: -dir.x, z: -dir.z });
  api.anim(u, "call");
  title(u, "§9§l해일", `§b${sideName(dir)}에서 큰 물결! §e기둥 뒤 노란 자리§f로 숨으세요`, playersIn(u.a, 8));
  explain(u, "tide", `§9[보스] 해일: §f큰 물결이 필드와 둘레 6칸(성문 밖 가까이)까지 휩쓸고 지나갑니다. §e물결 반대쪽 기둥 뒤(노란 자리)§f에 숨지 않으면 §4즉사§f합니다`);
  sound(u, u.def.sounds.roar, mid(u), 0.5, 1);
  sound(u, "ambient.underwater.enter", u.center, 0.5, 1);
  api.cast(u, "tide", d.windup + travel + 12, [[d.windup + travel + 10, () => undefined]]);
}

/** 물결이 오는 쪽 가장자리 거품 벽 (예고, 점점 높아짐) + 기둥 그림자 노란 자리 */
function tideWarn(u, T, k) {
  const d = u.def.tide;
  const c = u.center;
  const R = u.enc.def.arena.radius;
  const side = { x: -T.dir.z, z: T.dir.x };
  const s = -(R - 1.5);
  for (let l = -R; l <= R; l += 3) {
    if (Math.hypot(s, l) > R) continue;
    const at = { x: c.x + T.dir.x * s + side.x * l, y: c.y, z: c.z + T.dir.z * s + side.z * l };
    fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 0.6 + k * 2.2, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.3 + k, life: 0.55, color: FOAM, alpha: 0.85 });
    if (k > 0.3) splash(u.dim, at, 1 + k, DEEP, 0.5);
  }
  // 그림자 자리: 기둥 뒤 판 (실제 판정과 같은 식으로 고름)
  for (const pl of u.feat.pillars ?? []) {
    for (let a = 0.8; a <= d.depth + 1.5; a += 1.2) {
      for (let l = -1.6; l <= 1.61; l += 1.6) {
        const back = pl.half * (Math.abs(T.dir.x) + Math.abs(T.dir.z));
        const at = { x: pl.x + T.dir.x * (back + a) + side.x * l, y: c.y, z: pl.z + T.dir.z * (back + a) + side.z * l };
        if (!sheltered(u.feat.pillars, T.dir, at, d.depth, d.pad)) continue;
        fx(u.dim, "rpg:tele_fill", { x: at.x, y: at.y + 0.07, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 0.8, life: 0.6, color: SAFE, alpha: 0.55, grow: 1 });
      }
    }
  }
}

/** 해일: 예고 → 물결이 speed 칸/초로 지나감. 이번 틱에 물결이 지나간 사람은 그림자에 없으면 즉사 */
function tideTick(enc, now) {
  const T = enc.mem.tide;
  if (!T) return;
  const u = T.u;
  const d = u.def.tide;
  const c = enc.center;
  if (!u.alive) {
    enc.mem.tide = undefined;
    return;
  }
  if (now < T.crashAt) {
    const k = (now - T.t0) / d.windup;
    if ((now - T.t0) % 10 === 0) tideWarn(u, T, k);
    const left = T.crashAt - now;
    if (left % 20 === 0 && left <= 60) faceAll(u, `§9§l해일 ${left / 20}`, { dist: 2.4, up: 0.5 }, playersIn(u.a, 8));
    return;
  }
  const age = now - T.crashAt;
  const f0 = T.s0 + (d.speed * Math.max(0, age - 1)) / 20;
  const f1 = T.s0 + (d.speed * age) / 20;
  const side = { x: -T.dir.z, z: T.dir.x };
  const R = u.enc.def.arena.radius;
  if (age === 0) {
    // 물결: 옆으로 4칸마다 한 조각, 물결 방향으로 날아감 (조각은 옆으로 길쭉하게 서서 보는 쪽을 향함)
    const life = (T.s1 - T.s0) / d.speed;
    for (let l = -(R + 6); l <= R + 6.01; l += 4) {
      const at = { x: c.x + T.dir.x * T.s0 + side.x * l, y: c.y + 1.7, z: c.z + T.dir.z * T.s0 + side.z * l };
      fx(u.dim, "rpg:tide_wave", at, { dir: T.dir, speed: d.speed, size: 2.4, thick: 1.9, life, color: [1, 1, 1], alpha: 0.95 });
    }
    play(u.dim, "ambient.weather.thunder", c, 0.6, 0.6);
    play(u.dim, "random.splash", c, 0.5, 1);
    shakeNear(u, c, R + 8, 0.35, 1.2);
  }
  if (age % 3 === 0) {
    // 물결 앞 거품
    for (let l = -R; l <= R; l += 3) {
      if (Math.hypot(f1, l) > R + 1) continue;
      splash(u.dim, { x: c.x + T.dir.x * f1 + side.x * l, y: c.y, z: c.z + T.dir.z * f1 + side.z * l }, 1.3);
    }
  }
  if (age % 10 === 0) play(u.dim, "entity.generic.splash", { x: c.x + T.dir.x * f1, y: c.y, z: c.z + T.dir.z * f1 }, 0.6, 1);
  // 먹물 웅덩이는 씻겨 나감
  const proj = (p) => (p.x - c.x) * T.dir.x + (p.z - c.z) * T.dir.z;
  enc.mem.puddles = enc.mem.puddles.filter((pd) => proj(pd) > f1);
  for (const p of playersIn(u.a, 6).filter(okTarget)) {
    const s = proj(p.location);
    const was = T.last.get(p.id) ?? s;
    T.last.set(p.id, s);
    // 지난 틱에 물결 앞에 있었고 이번 틱에 물결이 닿음 (거슬러 달려도 빠지지 않게)
    if (T.hit.has(p.id) || !(was > f0 && s <= f1)) continue;
    T.hit.add(p.id);
    if (sheltered(u.feat.pillars ?? [], T.dir, p.location, d.depth, d.pad)) {
      splash(u.dim, p.location, 1.2);
      showFaceText(p, "§b기둥 뒤에서 버텼습니다", { dist: 2.4, up: 0.3 });
      continue;
    }
    splash(u.dim, p.location, 2.4, DEEP);
    fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.4, life: 0.5, color: DEEP, alpha: 0.95 });
    showFaceText(p, "§1§l해일에 휩쓸렸습니다", { dist: 2.2, up: 0.3 });
    say(u, `§9[보스] ${p.name}님이 해일에 휩쓸렸습니다`);
    kill(p, u.boss);
  }
  if (f1 >= T.s1) enc.mem.tide = undefined;
}

// ------------------------------------------------------------ 높은 곳
/** 기둥·성문 위처럼 높은 곳(바닥보다 high.above 칸 넘게)에 서 있는 사람: 촉수가 끌어내림 (가운데 쪽으로 밀어 떨어뜨림 + 피해, 사람마다 cd 틱에 한 번) */
function highTick(enc, now) {
  if (now % 10 !== 0) return;
  const u = enc.units[0];
  if (!u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const c = enc.center;
  for (const p of fighters(enc)) {
    if (p.isOnGround === false || p.location.y - c.y < d.above) continue;
    if (now - (enc.mem.high.get(p.id) ?? -1000) < d.cd) continue;
    enc.mem.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    beam(enc.dim, mid(u), { x: p.location.x, y: p.location.y + 1, z: p.location.z }, SEA, { life: 0.4, half: 0.35, thick: 0.3, gap: 0.6 });
    splash(enc.dim, p.location, 1.6);
    play(enc.dim, "entity.generic.splash", p.location, 0.7, 1);
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 } });
    showFaceText(p, "§b§l촉수가 끌어내립니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", "§b[보스] §f기둥·성문 위처럼 높은 곳에 올라서면 크라켄의 촉수가 끌어내립니다");
  }
}

// ------------------------------------------------------------ 모듈
export default {
  key: "kraken",
  propIds: [TENTACLE],
  /** 원거리 견제 (v2.3.0): 소용돌이·해일(필드 전체 패턴)·촉수가 나와 있는 동안(물구덩이 곁에서 촉수와 싸우는 사람은 크라켄에게서 멂)은 쉼 */
  farOk(u) {
    return !u.mem.whirl && !u.mem.tide && !u.mem.adds;
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    enc.mem = {
      next: { slam: now + d.slam.first, ink: now + d.ink.first, whirl: now + d.whirl.first },
      bigUntil: now + 100,
      told: new Set(),
      inkTold: new Map(),
      staggerTold: new Map(),
      puddles: [],
      whirl: undefined,
      adds: undefined,
      addsDone: new Set(),
      tide: undefined,
      tideDone: new Set(),
      corpses: [],
      staggerUntil: 0,
      high: new Map(),
    };
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    const r = u.api.ratio(u);
    // 체력 문턱 패턴: 촉수 소환 → 해일 (촉수가 없고 다른 큰 패턴이 끝났을 때)
    if (!m.adds && !m.tide && !m.whirl && now >= m.bigUntil) {
      const at = d.tentacles.at.find((x) => r <= x && !m.addsDone.has(x));
      if (at !== undefined) {
        m.addsDone.add(at);
        if (summonAdds(u, now)) return true;
      }
      const tt = d.tide.at.find((x) => r <= x && !m.tideDone.has(x));
      if (tt !== undefined) {
        m.tideDone.add(tt);
        tide(u, now);
        return true;
      }
      if (r <= d.whirl.from && now >= m.next.whirl) {
        m.next.whirl = now + every(u, d.whirl.every);
        whirl(u, now);
        return true;
      }
    }
    if (!m.adds && now >= m.next.slam) {
      if (slam(u, now, list)) {
        m.next.slam = now + every(u, d.slam.every);
        return true;
      }
      m.next.slam = now + 20; // 닿는 곳에 아무도 없음: 조금 뒤 다시
    }
    if (now >= m.next.ink) {
      if (ink(u, now, list)) {
        m.next.ink = now + every(u, d.ink.every);
        return true;
      }
      m.next.ink = now + 20;
    }
    return false;
  },
  /** 받는 피해: 그로기 동안 증가 (때린 사람에게 가끔 알림) */
  hurt(u, p, amount) {
    const m = u.mem;
    const now = system.currentTick;
    if (now >= m.staggerUntil) return amount;
    const bonus = u.def.tentacles.bonus;
    if (now - (m.staggerTold.get(p.id) ?? -100) >= 30) {
      m.staggerTold.set(p.id, now);
      showFaceText(p, `§e그로기 §f+${Math.round(bonus * 100)}%`, { dist: 2.4, side: -0.5, up: 0.25 });
    }
    return amount * (1 + bonus);
  },
  /** 촉수가 맞음: 이름표의 체력 막대 */
  propHit(enc, prop, p, amount, left) {
    if (prop.typeId !== TENTACLE || left === undefined) return;
    try {
      prop.nameTag = hpTag(left, enc.def.tentacles.hp);
    } catch {
      // 무시
    }
  },
  /** 촉수가 쓰러짐: 무너지는 동작 뒤 치움 */
  propDied(enc, prop, p) {
    const m = enc.mem;
    const a = m.adds?.list.find((x) => x.e.id === prop.id);
    if (a) a.dead = true;
    gone(prop);
    tentAnim(prop, "die");
    const at = a?.hole ?? prop.location;
    splash(enc.dim, at, 2.4);
    fx(enc.dim, "rpg:bash_burst", { x: at.x, y: at.y + 2, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.6, life: 0.45, color: SEA, alpha: 0.9 });
    play(enc.dim, "mob.guardian.death", at, 0.6, 1);
    const left = m.adds ? m.adds.list.filter((x) => !x.dead).length : 0;
    say(enc, `§b[보스] §e${p?.name ?? "누군가"}§f님이 촉수를 베었습니다 §7(남은 촉수 ${left})`);
    m.corpses.push({ e: prop, at: system.currentTick + 20 });
  },
  tick(enc, now) {
    puddleTick(enc, now);
    whirlTick(enc, now);
    addsTick(enc, now);
    tideTick(enc, now);
    corpseTick(enc, now);
    highTick(enc, now);
    // 몸 둘레 거품 (가끔)
    if (now % 40 === 0 && enc.alive && enc.boss?.isValid) {
      const a = Math.random() * Math.PI * 2;
      splash(enc.dim, { x: enc.center.x + Math.cos(a) * 3, y: enc.center.y, z: enc.center.z + Math.sin(a) * 3 }, 0.8, FOAM, 0.5);
    }
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.next) return;
    m.puddles = [];
    m.whirl = undefined;
    m.adds = undefined;
    m.tide = undefined;
    m.corpses = [];
  },
};
