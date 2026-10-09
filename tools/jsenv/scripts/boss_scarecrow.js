import { EntityDamageCause, system } from "@minecraft/server";
import { arenaSize, playersIn } from "./arena.js";
import { play, showFaceText } from "./fx.js";
import { fx } from "./skills.js";
import { kill, root, silence, unroot } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fighters, hdist, lineMarks, nearSegment, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 허수아비 킹 (v1.8.0, Lv.10, 수확의 왕): 수치는 config.js BOSSES.scarecrow_king
 * - 평타 낫 베기: 앞 120° 부채꼴 (엔진)
 * - 까마귀 떼: 플레이어 3명을 지나는 선 (필드 끝에서 끝) → 까마귀가 선을 따라 날아가며 피해 + 실명
 * - 호박 폭탄: 플레이어 2명 발밑 + 무작위 3곳에 호박 → 원이 다 차면 폭발
 * - 짚단 감옥 (체력 80% 아래): 가장 먼 플레이어를 속박 + 짚단 감옥 (체력 prison.hp). 부수면 풀림, 못 부수면 불타며 큰 피해
 * - 분신 (체력 60%·25%): 허수아비 넷 사이로 숨음. 진짜는 어깨에 까마귀가 있음 → 진짜를 때리면 5초 비틀거림(받는 피해 +50%),
 *   가짜를 때리면 그 사람 침묵 6초 + 작은 피해, 15초 안에 못 찾으면 '수확의 밤' (모두 큰 피해)
 * - 수확의 낫 (체력 40% 아래, 45초마다): 가운데서 낫을 들어 올림 → 5초 뒤 허수아비 토템 둘레(금빛 원) 밖은 즉사.
 *   토템 하나에 2명까지 (넘치면 그 원의 모두 즉사)
 * 엔진 함수(cast·hold·anim·소품)는 enc.api 로 받음 (bosses.js 와 서로 가져오지 않게)
 */

const KEY = "scarecrow_king";
const DECOY = "rpg:decoy_scarecrow_king";
const BOMB = "rpg:prop_pumpkin_bomb";
/** 정령이 노리지 않는 표시 (runes.js NO_AUTO_TAG) */
const NO_AUTO = "rpg_no_auto";
const CAGE = "rpg:prop_straw_cage";
const GOLD = [1.0, 0.82, 0.25];
const ORANGE = [1.0, 0.5, 0.12];
const CROW = [0.45, 0.2, 0.62];

const rnd = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const shuffle = (list) => {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};

function floorY(enc) {
  return enc.center.y;
}

/** 움직일 수 있는 사람 (감옥에 갇힌 사람은 피할 수 없으므로 까마귀·폭탄이 노리지 않음) */
function free(enc, list) {
  const caged = enc.mem.cage?.p;
  return caged ? list.filter((p) => p !== caged) : list;
}

// ------------------------------------------------------------ 까마귀 떼
function crows(enc, now, list) {
  const d = enc.def.crows;
  const { radius: R } = arenaSize(enc.a);
  const api = enc.api;
  api.hold(enc, true);
  api.anim(enc, "cast");
  sound(enc, "mob.phantom.swoop", enc.boss.location, 0.7, 1);
  const c = enc.center;
  const lines = shuffle(free(enc, list))
    .slice(0, d.targets)
    .map((p) => {
      const ang = Math.random() * Math.PI * 2;
      const dir = { x: Math.cos(ang), z: Math.sin(ang) };
      const m = p.location;
      // 플레이어를 지나는 선을 필드 원에서 자름 (필드 끝에서 끝까지)
      const ox = m.x - c.x;
      const oz = m.z - c.z;
      const bb = ox * dir.x + oz * dir.z;
      const h = Math.sqrt(Math.max(0, bb * bb - (ox * ox + oz * oz - R * R)));
      const a = { x: m.x + dir.x * (-bb - h), y: floorY(enc), z: m.z + dir.z * (-bb - h) };
      const b = { x: m.x + dir.x * (-bb + h), y: floorY(enc), z: m.z + dir.z * (-bb + h) };
      lineMarks(enc.dim, a, b, d.width, d.windup, CROW);
      return { a, b, dir };
    });
  api.cast(enc, "crows", d.windup + 12, [
    [
      d.windup,
      (e) => {
        for (const L of lines) {
          const len = hdist(L.a, L.b);
          const n = Math.ceil(len / 1.5);
          for (let i = 0; i <= n; i++) {
            const t = i / n;
            fx(e.dim, "rpg:crow", { x: L.a.x + (L.b.x - L.a.x) * t, y: floorY(e) + 1.1 + Math.random() * 0.8, z: L.a.z + (L.b.z - L.a.z) * t }, { dir: { x: L.dir.x, y: 0.05, z: L.dir.z }, speed: 6, size: 0.35, life: 0.5 + t * 0.2, delay: t * 0.2, color: [1, 1, 1], alpha: 1 });
          }
        }
        sound(e, "mob.phantom.bite", e.center, 1.2, 1);
        for (const p of fighters(e)) {
          if (!lines.some((L) => nearSegment(p.location, L.a, L.b, d.width))) continue;
          bossHit(e, p, d.damage, { effect: { id: "blindness", ticks: d.blind } });
        }
      },
    ],
    [d.windup + 8, (e) => api.hold(e, false)],
  ]);
}

// ------------------------------------------------------------ 호박 폭탄
function bombs(enc, now, list) {
  const d = enc.def.bombs;
  const { radius: R } = arenaSize(enc.a);
  const api = enc.api;
  api.anim(enc, "cast");
  sound(enc, "random.fuse", enc.center, 0.8, 1);
  const spots = shuffle(free(enc, list))
    .slice(0, d.onPlayers)
    .map((p) => ({ x: p.location.x, y: floorY(enc), z: p.location.z }));
  for (let tries = 0; spots.length < d.count && tries < 60; tries++) {
    const ang = Math.random() * Math.PI * 2;
    const r = 4 + Math.random() * (R - 6);
    const s = { x: enc.center.x + Math.cos(ang) * r, y: floorY(enc), z: enc.center.z + Math.sin(ang) * r };
    if (spots.some((o) => hdist(o, s) < d.radius * 1.5)) continue;
    spots.push(s);
  }
  const props = [];
  for (const s of spots) {
    disc(enc.dim, s, d.radius, d.fuse, ORANGE);
    try {
      const e = enc.dim.spawnEntity(BOMB, s);
      api.addProp(enc, e);
      props.push(e);
    } catch {
      // 소품 없이 원만
    }
  }
  enc.mem.bombs.push({ at: now + d.fuse, spots, props });
  api.cast(enc, "bombs", 14, []);
}

function explode(enc, b) {
  const d = enc.def.bombs;
  for (const e of b.props) enc.api.dropProp(enc, e);
  for (const s of b.spots) {
    fx(enc.dim, "rpg:bash_burst", { x: s.x, y: s.y + 0.6, z: s.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.radius, life: 0.4, color: ORANGE, alpha: 0.95 });
    fx(enc.dim, "rpg:shock_ring", { x: s.x, y: s.y + 0.1, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius / 2.1, life: 0.4, color: ORANGE, alpha: 0.9 });
    play(enc.dim, "random.explode", s, 1.1, 0.9);
  }
  for (const p of fighters(enc)) {
    const hit = b.spots.find((s) => hdist(p.location, s) <= d.radius && Math.abs(p.location.y - s.y) < 3);
    if (!hit) continue;
    const dx = p.location.x - hit.x;
    const dz = p.location.z - hit.z;
    const L = Math.hypot(dx, dz) || 1;
    bossHit(enc, p, d.damage, { cause: EntityDamageCause.entityExplosion, knock: { x: (dx / L) * 1.1, z: (dz / L) * 1.1, h: 0.45 } });
  }
}

// ------------------------------------------------------------ 짚단 감옥
function prison(enc, now, list) {
  const d = enc.def.prison;
  const api = enc.api;
  const b = enc.boss.location;
  let tgt;
  let far = -1;
  for (const p of list) {
    const k = hdist(p.location, b);
    if (k > far) {
      far = k;
      tgt = p;
    }
  }
  if (!tgt) return;
  api.anim(enc, "point");
  sound(enc, "mob.evocation_illager.prepare_attack", b, 0.8, 1);
  showFaceText(tgt, "§6§l짚단 감옥이 노립니다!", { dist: 2.2, up: 0.3 });
  const target = tgt;
  // 노린 사람을 따라다니는 금빛 원 (피할 수 없는 기믹: 갇히면 동료가 부숨)
  const mark = (e) => {
    if (target.isValid) disc(e.dim, { x: target.location.x, y: floorY(e), z: target.location.z }, 1.3, 6, GOLD, { fill: false });
  };
  mark(enc);
  api.cast(enc, "prison", 26, [
    [5, mark],
    [10, mark],
    [15, mark],
    [
      20,
      (e) => {
        if (!okTarget(target) || !fighters(e).includes(target)) return;
        let cage;
        try {
          cage = e.dim.spawnEntity(CAGE, target.location);
        } catch {
          cage = undefined;
        }
        if (!cage) return;
        api.own(cage);
        root(target, d.hold, "짚단 감옥");
        e.mem.cage = { p: target, e: cage, until: system.currentTick + d.hold };
        say(e, `§6[보스] §e${target.name}§f님이 짚단 감옥에 갇혔습니다! §7감옥을 부수면 풀려납니다 (${Math.round(d.hold / 20)}초)`);
        play(e.dim, "dig.grass", target.location, 0.7, 1);
      },
    ],
  ]);
}

function cageTick(enc, now) {
  const c = enc.mem.cage;
  if (!c) return;
  const d = enc.def.prison;
  let hp = 0;
  try {
    hp = c.e.isValid ? (c.e.getComponent("minecraft:health")?.currentValue ?? 0) : 0;
  } catch {
    hp = 0;
  }
  if (!okTarget(c.p)) {
    endCage(enc, false);
    return;
  }
  if (hp <= 0) {
    // 부서짐: 풀려남
    say(enc, `§a[보스] ${c.p.name}님이 짚단 감옥에서 풀려났습니다`);
    fx(enc.dim, "rpg:bash_burst", { x: c.p.location.x, y: c.p.location.y + 1, z: c.p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.6, life: 0.35, color: GOLD, alpha: 0.9 });
    endCage(enc, false);
    return;
  }
  const left = c.until - now;
  if (left > 0) {
    if (left % 20 === 0) showFaceText(c.p, `§6감옥 §f${Math.ceil(left / 20)}`, { dist: 2.2, up: -0.3 });
    return;
  }
  // 시간 끝: 불타며 큰 피해
  fx(enc.dim, "rpg:bash_burst", { x: c.p.location.x, y: c.p.location.y + 1, z: c.p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2, life: 0.45, color: ORANGE, alpha: 0.95 });
  play(enc.dim, "mob.blaze.shoot", c.p.location, 0.8, 1);
  bossHit(enc, c.p, d.damage, { cause: EntityDamageCause.magic }); // 마법: 갑옷·불 저항 물약으로 피할 수 없음
  endCage(enc, true);
}

function endCage(enc, burned) {
  const c = enc.mem.cage;
  if (!c) return;
  enc.mem.cage = undefined;
  unroot(c.p);
  enc.api.disown(c.e);
  try {
    if (c.e.isValid) c.e.remove();
  } catch {
    // 무시
  }
  void burned;
}

// ------------------------------------------------------------ 분신
function decoys(enc, now) {
  const d = enc.def.decoys;
  const api = enc.api;
  const spots = enc.feat.decoys.slice(0, d.count);
  if (spots.length < 2) return;
  const real = Math.floor(Math.random() * spots.length);
  api.hold(enc, true);
  api.anim(enc, "vanish");
  sound(enc, "mob.evocation_illager.prepare_summon", enc.boss.location, 0.8, 1);
  title(enc, "§6§l허수아비들 사이로!", "§e진짜는 어깨에 까마귀가 있습니다");
  say(enc, `§6[보스] §f허수아비 킹이 숨었습니다! §e진짜(어깨에 까마귀)§f를 찾아 때리세요 §7(가짜를 때리면 침묵, ${Math.round(d.time / 20)}초 안에 못 찾으면 수확의 밤)`);
  enc.mem.decoys = { real, spots, until: now + 12 + d.time, props: [], shown: false, shownAt: 0 };
  hideFromSummons(enc.boss, true);
  api.cast(enc, "decoys", 12 + d.time + 2, [
    [
      12,
      (e) => {
        const m = e.mem.decoys;
        if (!m) return;
        m.shown = true;
        m.shownAt = system.currentTick;
        const face = (s) => ({ x: e.center.x, y: s.y, z: e.center.z });
        spots.forEach((s, i) => {
          fx(e.dim, "rpg:bash_burst", { x: s.x, y: s.y + 2, z: s.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.2, life: 0.4, color: GOLD, alpha: 0.85 });
          if (i === m.real) {
            try {
              e.boss.teleport({ x: s.x, y: s.y + 0.1, z: s.z }, { facingLocation: face(s) });
            } catch {
              // 무시
            }
            api.anim(e, "appear");
            return;
          }
          try {
            const dec = e.dim.spawnEntity(DECOY, { x: s.x, y: s.y + 0.1, z: s.z });
            try {
              dec.nameTag = e.boss.nameTag;
              dec.teleport({ x: s.x, y: s.y + 0.1, z: s.z }, { facingLocation: face(s) });
            } catch {
              // 무시
            }
            api.addProp(e, dec);
            m.props.push(dec);
            api.anim(e, "appear", dec);
          } catch {
            // 무시
          }
        });
      },
    ],
    [12 + d.time, (e) => decoyFail(e)],
  ]);
}

/**
 * v2.14.0 숨은 동안은 정령 (정령사 룬) 이 스스로 노리지 않게 (정령이 진짜를 찾아 주지 않게): runes.js 의 NO_AUTO_TAG 와 같은 이름표.
 * 남은 보스 엔티티는 월드를 다시 불러올 때 치우므로 표시가 남지 않음
 */
function hideFromSummons(e, on) {
  try {
    if (on) e?.addTag(NO_AUTO);
    else e?.removeTag(NO_AUTO);
  } catch {
    // 무시
  }
}

function clearDecoys(enc) {
  const m = enc.mem.decoys;
  if (!m) return;
  enc.mem.decoys = undefined;
  hideFromSummons(enc.boss, false);
  for (const e of m.props) {
    try {
      if (e.isValid) fx(enc.dim, "rpg:bash_burst", { x: e.location.x, y: e.location.y + 2, z: e.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2, life: 0.35, color: GOLD, alpha: 0.8 });
    } catch {
      // 무시
    }
    enc.api.dropProp(enc, e);
  }
}

/** 진짜를 찾음: 분신이 흩어지고 5초 비틀거림 (받는 피해 +50%) */
function decoyFound(enc, p) {
  const d = enc.def.decoys;
  clearDecoys(enc);
  enc.cast = undefined;
  enc.staggerUntil = system.currentTick + d.stagger;
  enc.mem.staggering = true;
  enc.api.anim(enc, "stagger");
  say(enc, `§a[보스] §e${p.name}§f님이 진짜를 찾았습니다! §7허수아비 킹이 비틀거립니다 (받는 피해 +${Math.round(d.staggerBonus * 100)}%, ${Math.round(d.stagger / 20)}초)`);
  sound(enc, "random.anvil_land", enc.boss.location, 1.2, 0.8);
}

/** 못 찾음: 수확의 밤 (모두 큰 피해), 가운데로 돌아옴 */
function decoyFail(enc) {
  if (!enc.mem.decoys) return;
  const d = enc.def.decoys;
  clearDecoys(enc);
  title(enc, "§4§l수확의 밤", "§7진짜를 찾지 못했습니다");
  sound(enc, "mob.wither.spawn", enc.center, 1.2, 0.8);
  for (const p of fighters(enc)) {
    fx(enc.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.4, life: 0.35, color: CROW, alpha: 0.9 });
    bossHit(enc, p, d.failDamage, { cause: EntityDamageCause.magic });
  }
  try {
    enc.boss.teleport({ x: enc.center.x, y: enc.center.y + 1, z: enc.center.z });
  } catch {
    // 무시
  }
  enc.api.anim(enc, "appear");
  enc.api.hold(enc, false);
}

/** 가짜를 때림: 그 사람 침묵 + 작은 피해, 가짜는 흩어짐 */
function decoyWrong(enc, prop, p) {
  const d = enc.def.decoys;
  const m = enc.mem.decoys;
  if (!m) return;
  try {
    fx(enc.dim, "rpg:bash_burst", { x: prop.location.x, y: prop.location.y + 2, z: prop.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.2, life: 0.35, color: CROW, alpha: 0.9 });
  } catch {
    // 무시
  }
  m.props = m.props.filter((e) => e !== prop);
  enc.api.dropProp(enc, prop);
  silence(p, d.silence);
  bossHit(enc, p, d.wrongDamage, { cause: EntityDamageCause.magic });
  showFaceText(p, "§5§l가짜였습니다!", { dist: 2.2, up: 0.4 });
  play(enc.dim, "mob.witch.celebrate", p.location, 0.8, 1);
}

// ------------------------------------------------------------ 수확의 낫 (즉사)
function reaping(enc, now) {
  const d = enc.def.reaping;
  const api = enc.api;
  const { radius: R } = arenaSize(enc.a);
  const totems = enc.feat.totems;
  api.hold(enc, true);
  try {
    enc.boss.teleport({ x: enc.center.x, y: enc.center.y + 1, z: enc.center.z });
  } catch {
    // 무시
  }
  api.anim(enc, "raise");
  sound(enc, enc.def.sounds.roar, enc.center, 0.6, 1);
  const warned = reapWarned(enc);
  title(enc, "§4§l수확의 낫", `§e허수아비 토템 곁 금빛 원으로! §7(한 곳에 ${d.perTotem}명까지)`, warned);
  say(enc, `§4[보스] 수확의 낫! §f${Math.round(d.count / 20)}초 뒤 금빛 원 밖은 즉사합니다. §7토템 하나에 ${d.perTotem}명까지 — 넘치면 그 원도 즉사 (필드 밖으로 달아나도 닿음)`, warned);
  const steps = [];
  for (let t = 0; t < d.count; t += 10) {
    steps.push([
      t,
      (e) => {
        disc(e.dim, e.center, R, 12, [0.9, 0.08, 0.06], { fill: false, alpha: 0.5 });
        for (const s of totems) disc(e.dim, s, d.safe, 12, GOLD, { fill: false, alpha: 0.95 });
      },
    ]);
  }
  for (let k = 5; k >= 1; k--) {
    const t = d.count - k * 20;
    if (t >= 0) steps.push([t, (e) => faceAll(e, `§4§l${k}`, { dist: 2.4, up: 0.5 }, reapWarned(e))]);
  }
  steps.push([d.count, (e) => reapJudge(e)]);
  steps.push([d.count + 20, (e) => api.hold(e, false)]);
  api.cast(enc, "reaping", d.count + 22, steps);
}

/**
 * 낫이 닿는 사람: 필드 둘레(+8칸) 모두 + 최근 30초 안에 보스를 때린 사람은 64칸 안 어디든 (필드 밖으로 달아나 피할 수 없게).
 * 때린 뒤 쓰러진 사람은 다시 때리기 전까지 빠짐 (멀리서 다시 살아나 모르는 채 또 죽지 않게). 경고도 이 사람들에게 모두 감
 */
function reapTargets(enc) {
  const who = new Map();
  for (const p of playersIn(enc.a, 8)) who.set(p.id, p);
  const now = system.currentTick;
  for (const [id, part] of enc.parts) {
    const p = part.p;
    try {
      if (now - part.last <= 600 && part.last > (part.diedAt ?? -1) && p?.isValid && p.dimension.id === enc.a.dim && hdist(p.location, enc.center) <= 64) who.set(id, p);
    } catch {
      // 무시
    }
  }
  return [...who.values()].filter(okTarget);
}

/** 경고를 받을 사람: 필드 둘레 + 낫이 닿는 사람 */
function reapWarned(enc) {
  const who = new Map();
  for (const p of playersIn(enc.a, 8)) who.set(p.id, p);
  for (const p of reapTargets(enc)) who.set(p.id, p);
  return [...who.values()];
}

function reapJudge(enc) {
  const d = enc.def.reaping;
  const totems = enc.feat.totems;
  const groups = totems.map(() => []);
  const doomed = [];
  for (const p of reapTargets(enc)) {
    const i = totems.findIndex((s) => hdist(p.location, s) <= d.safe + 0.3);
    if (i < 0) doomed.push(p);
    else groups[i].push(p);
  }
  groups.forEach((g, i) => {
    if (g.length > d.perTotem) {
      doomed.push(...g);
      say(enc, `§4[보스] 토템 ${i + 1}에 ${g.length}명 — 제물이 넘쳤습니다`);
    }
  });
  // 낫 한 바퀴
  const { radius: R } = arenaSize(enc.a);
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    fx(enc.dim, "rpg:slash_heavy_r_glow", { x: enc.center.x + Math.cos(a) * R * 0.5, y: enc.center.y + 1, z: enc.center.z + Math.sin(a) * R * 0.5 }, { dir: { x: -Math.sin(a), y: 0, z: Math.cos(a) }, speed: 0, spin: 0, size: R * 0.35, life: 0.3, color: [0.9, 0.1, 0.08], alpha: 0.95 });
  }
  sound(enc, "mob.wither.break_block", enc.center, 0.7, 1);
  for (const p of doomed) {
    showFaceText(p, "§4§l즉사", { dist: 2.2, up: 0.3 });
    kill(p, enc.boss);
  }
}

// ------------------------------------------------------------ 모듈
export default {
  key: KEY,
  propIds: [DECOY, BOMB, CAGE],
  /** 원거리 견제 (v2.3.0): 분신 동안(숨어 있음)·수확의 낫·짚단 감옥(갇힌 사람을 구해야 함)·비틀거리는 동안은 쉼 */
  farOk(u) {
    return !u.mem.decoys && !u.mem.staggering && !u.mem.cage && u.cast?.id !== "reaping" && u.cast?.id !== "prison";
  },
  start(enc) {
    const now = system.currentTick;
    enc.mem = { next: { crows: now + 120, bombs: now + 220, prison: now + 160 }, decoyDone: new Set(), reapAt: undefined, cage: undefined, decoys: undefined, bombs: [], staggering: false, tell: 0 };
  },
  pick(enc, now, list) {
    const d = enc.def;
    const r = enc.api.ratio(enc);
    for (const at of d.decoys.at) {
      if (r <= at && !enc.mem.decoyDone.has(at)) {
        enc.mem.decoyDone.add(at);
        decoys(enc, now);
        return true;
      }
    }
    // 감옥에 갇힌 사람이 있으면 기다림 (갇힌 채로는 토템까지 갈 수 없음)
    if (r <= d.reaping.from && !enc.mem.cage && (enc.mem.reapAt === undefined || now >= enc.mem.reapAt)) {
      enc.mem.reapAt = now + d.reaping.every;
      reaping(enc, now);
      return true;
    }
    const ready = [];
    if (now >= enc.mem.next.crows) ready.push("crows");
    if (now >= enc.mem.next.bombs) ready.push("bombs");
    if (r <= d.prison.from && now >= enc.mem.next.prison && !enc.mem.cage && list.length > 0) ready.push("prison");
    if (!ready.length) return false;
    const id = ready[Math.floor(Math.random() * ready.length)];
    const every = d[id].every;
    enc.mem.next[id] = now + rnd(every[0], every[1]);
    if (id === "crows") crows(enc, now, list);
    else if (id === "bombs") bombs(enc, now, list);
    else prison(enc, now, list);
    return true;
  },
  /** 받는 피해: 분신 중 진짜를 때리면 찾은 것, 비틀거리는 동안 +50% */
  hurt(enc, p, amount) {
    const d = enc.def.decoys;
    if (enc.mem.decoys) {
      if (!enc.mem.decoys.shown) return 0; // 숨는 중 (분신이 나오기 전)
      decoyFound(enc, p);
      return amount * (1 + d.staggerBonus);
    }
    return system.currentTick < enc.staggerUntil ? amount * (1 + d.staggerBonus) : amount;
  },
  propHit(enc, prop, p) {
    if (prop.typeId === DECOY) decoyWrong(enc, prop, p);
  },
  tick(enc, now) {
    cageTick(enc, now);
    const due = enc.mem.bombs.filter((b) => now >= b.at);
    if (due.length) {
      enc.mem.bombs = enc.mem.bombs.filter((b) => now < b.at);
      for (const b of due) explode(enc, b);
    }
    if (enc.mem.staggering && now >= enc.staggerUntil) {
      enc.mem.staggering = false;
      enc.api.hold(enc, false);
    }
    // 분신 중: 진짜 어깨의 까마귀가 가끔 날갯짓 (알아볼 단서). 나타난 뒤 1초부터 (사라지기·나타나기 동작을 덮지 않게)
    const dm = enc.mem.decoys;
    if (dm?.shown && now - dm.shownAt >= 20 && now - enc.mem.tell >= 40) {
      enc.mem.tell = now;
      enc.api.anim(enc, "tell");
      const l = enc.boss.location;
      const h = bossSize(enc.def).height * 0.8; // 어깨 높이
      play(enc.dim, "mob.parrot.idle", l, 0.6, 0.5);
      // 깃털 까마귀 둘이 어깨에서 날아오름 (멀리서도 보이는 단서)
      for (let i = 0; i < 2; i++) {
        const a = Math.random() * Math.PI * 2;
        fx(enc.dim, "rpg:crow", { x: l.x, y: l.y + h, z: l.z }, { dir: { x: Math.cos(a) * 0.6, y: 0.8, z: Math.sin(a) * 0.6 }, speed: 3, size: 0.45, life: 0.9, delay: i * 0.15, color: [1, 1, 1], alpha: 1 });
      }
    }
  },
  end(enc) {
    endCage(enc, false);
    clearDecoys(enc);
    for (const b of enc.mem.bombs ?? []) for (const e of b.props) enc.api.dropProp(enc, e);
    enc.mem.bombs = [];
  },
};
