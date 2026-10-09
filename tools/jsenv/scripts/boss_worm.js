import { EntityDamageCause, system } from "@minecraft/server";
import { deepTile } from "./arena.js";
import { play, showFaceText } from "./fx.js";
import { fx, shake } from "./skills.js";
import { kill, root, silence, unroot, unsilence } from "./status.js";
import { bossHit, bossLabel, bossSize, disc, faceAll, fanMarks, fighters, hdist, lineMarks, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 거대 웜 (v2.1.0, Lv.25): 수치는 config.js BOSSES.great_worm
 * 심층의 신전의 굴(가운데 + 대각선 넷)에 박혀 있음 (움직이지 않는 보스). 땅속으로 굴을 옮겨 다님 (땅속에 있는 동안 무적)
 * - 평타 머리 내려찍기 (앞 110°, 엔진)
 * - 몸통 휘두르기: 한 사람 쪽으로 몸을 돌려 예고 → 앞 270° 를 머리로 쓸어 버림 (머리가 지나가는 자리부터 맞음). 등 뒤 90° 는 안전
 * - 굴 이동: 땅속으로 → 사람이 가장 많은 다른 굴 둘레에 예고 → 튀어나옴 (피해 + 띄움)
 * - 대지 분출 (체력 90% 아래): 바닥 체크무늬의 한쪽 칸이 솟고 곧바로 반대쪽 칸이 솟음 (방금 솟은 칸으로 옮겨야 함)
 * - 삼키기 (체력 80% 아래): 노란 원이 한 사람을 따라다니다 멈춤 → 원 안의 한 명을 삼킴 (속박 + 침묵 + 실명 + 계속 피해).
 *   나머지가 웜에게 (다른 사람 수 × perHelper) 피해를 주면 뱉고 구역질 (잠깐 패턴 없음). 못 하면 더 다친 채로 뱉음
 * - 진동 감지 (체력 70%·45%·20%): 땅속에서 발소리를 들음 → 웅크리지 않고 걷거나 뛰면 발밑에서 튀어나와 삼킴 (즉사).
 *   듣는 동안 머리 위로 돌이 떨어짐 (웅크려 걸어 나와야 함). 끝나면 아무 굴에서 튀어나옴
 * - 대지 가르기: 멀리(제 굴에서 far 칸 넘게) 있는 사람 쪽으로 땅에 금 → 바위가 금을 따라 솟음 (옆으로 비켜서면 피함)
 * - 높은 곳 (성문·담장 탑 위): 땅을 울려 떨어뜨림 (진동 감지 중에는 없음: 떨어지는 소리로 들키지 않게)
 * 큰 패턴(굴 이동·대지 분출·삼키기·진동 감지)은 서로 겹치지 않음 (mem.bigUntil). 삼킨 사람이 있는 동안에는 평타·몸통 휘두르기만
 * 밀어내는 공격은 담장 가까이에서 바깥쪽으로 밀지 않음 (keepIn: 필드 밖·탑 위로 날아가지 않게)
 */

const EARTH = [0.6, 0.44, 0.32];
const DUST = [0.62, 0.58, 0.54];
const ORANGE = [1.0, 0.48, 0.12];
const RED = [1.0, 0.22, 0.14];
const YELLOW = [1.0, 0.86, 0.2];
const SOUND = [0.3, 0.88, 0.96];
const BILE = [0.78, 0.86, 0.22];
const ROCK = [0.5, 0.47, 0.5];

/** 몸통 휘두르기 방향: 머리가 (앞 + angle/2) 에서 시작해 (앞 - angle/2) 로 감 (가로 각도: +x 에서 +z 쪽이 +). build.py 의 sweep 동작과 같아야 함 */
export const SWEEP_DIR = 1;
const TAU = Math.PI * 2;

const rnd = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const every = (u, range) => Math.round(rnd(range[0], range[1]) * (u.cdMult ?? 1));
const floorAt = (u, x, z) => ({ x, y: u.center.y, z });
/** 가로 단위 방향 a → b */
function dirTo(a, b) {
  const dx = b.x - a.x;
  const dz = b.z - a.z;
  const L = Math.hypot(dx, dz);
  return L < 1e-6 ? { x: 0, y: 0, z: 1 } : { x: dx / L, y: 0, z: dz / L };
}
const angOf = (d) => Math.atan2(d.z, d.x);

/** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
function explain(u, id, text) {
  const told = u.mem.told;
  if (told.has(id)) return;
  told.add(id);
  say(u, text);
}

/** 몸 가운데 (연출용) */
function mid(u) {
  const l = u.boss.location;
  return { x: l.x, y: l.y + bossSize(u.def).height * 0.5, z: l.z };
}

/** 흙먼지 (rpg:dust: 부풀며 사라지는 먼지 구름) */
function dust(dim, at, size, color = DUST, life = 0.7) {
  fx(dim, "rpg:dust", { x: at.x, y: at.y + size * 0.35, z: at.z }, { size, life, color, alpha: 0.9 });
}

/** 튀는 돌 조각 (rpg:boulder 작게: 위로 던지면 떨어짐) */
function chips(dim, at, n, size = 0.35, up = 7) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * TAU;
    const out = 0.25 + Math.random() * 0.35;
    fx(dim, "rpg:boulder", { x: at.x, y: at.y + 0.3, z: at.z }, { dir: { x: Math.cos(a) * out, y: 1, z: Math.sin(a) * out }, speed: up * (0.7 + Math.random() * 0.4), spin: Math.random() * 360, size: size * (0.7 + Math.random() * 0.6), life: 1.2, color: ROCK, alpha: 1 });
  }
}

/** 땅이 갈라지는 금 (바닥에 누움, 수명 동안 퍼짐) */
function crack(dim, at, size, life) {
  fx(dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.06, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size, life, spin: Math.random() * 360, color: [0.12, 0.08, 0.06], alpha: 0.9 });
}

function shakeNear(u, at, range, intensity, seconds) {
  for (const p of fighters(u)) if (hdist(p.location, at) <= range) shake(p, intensity, seconds);
}

/**
 * 밀어내는 힘 (kx, kz): 담장에서 7칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖·담장 탑 위로 날아가지 않게)
 * @returns {{x: number, z: number}}
 */
function keepIn(u, p, kx, kz) {
  const c = u.center;
  const rx = p.location.x - c.x;
  const rz = p.location.z - c.z;
  const r = Math.hypot(rx, rz);
  if (r < u.enc.def.arena.radius - 7 || r < 1e-6) return { x: kx, z: kz };
  const ux = rx / r;
  const uz = rz / r;
  const out = kx * ux + kz * uz;
  if (out <= 0) return { x: kx, z: kz };
  return { x: kx - ux * (out + 0.15), z: kz - uz * (out + 0.15) };
}

/** 무언가를 타고 있는지 (말·보트: 땅에 닿지 않아도 뛴 것이 아님) */
function riding(p) {
  try {
    return !!p.getComponent("minecraft:riding");
  } catch {
    return false;
  }
}

/** 굴을 옮기거나 땅속에 있는 동안의 동작 틀 (다음 동작이 같은 틀에서 바꿀 때까지 유지) */
const DIG = "rpg_dig";
/** 삼킨 사람이 든 동안 (꿀꺽 불룩한 모습 유지 → 뱉기가 바꿈) */
const GUT = "rpg_gut";

// ------------------------------------------------------------ 대상
/** 삼켜진 사람인지 */
function inside(u, p) {
  const D = u.mem?.devour;
  return !!D && D.phase === "inside" && D.victim?.id === p?.id;
}
/** 공격이 닿는 사람 (삼켜진 사람은 뺌) */
function targets(u) {
  return fighters(u).filter((p) => !inside(u, p));
}
/** 가장 가까운 사람 (없으면 undefined) */
function nearest(u, at) {
  let best;
  let bd = Infinity;
  for (const p of targets(u)) {
    const d = hdist(p.location, at);
    if (d < bd) {
      bd = d;
      best = p;
    }
  }
  return best;
}

// ------------------------------------------------------------ 몸통 휘두르기
/** 머리가 쓸고 간 각도 (시작 방향에서 휘두르는 쪽으로, 0 ~ 2π) */
function sweptTo(start, ang) {
  return (((SWEEP_DIR * (start - ang)) % TAU) + TAU) % TAU;
}

function sweep(u, now, list) {
  const d = u.def.sweep;
  const home = u.home;
  const near = list.filter((p) => hdist(p.location, home) <= d.radius + 1 && Math.abs(p.location.y - home.y) < 4);
  if (!near.length) return false;
  const api = u.api;
  const t = near[Math.floor(Math.random() * near.length)];
  const dir = dirTo(home, t.location);
  const apex = floorAt(u, home.x, home.z);
  api.face(u, dir);
  fanMarks(u.dim, apex, dir, d.radius, d.angle, d.windup, ORANGE, 1.5);
  api.anim(u, "sweep");
  sound(u, "mob.warden.sniff", mid(u), 0.7, 1);
  explain(u, "sweep", `§6[보스] 몸통 휘두르기: §f거대 웜이 머리로 앞 ${d.angle}°를 쓸어 버립니다. §e웜의 등 뒤(${360 - d.angle}°)나 ${d.radius}칸 밖으로 피하세요`);
  const start = angOf(dir) + (SWEEP_DIR * d.angle * Math.PI) / 360;
  const hit = new Set();
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let k = 0; k <= d.time; k++) steps.push([d.windup + k, (x) => sweepStep(x, apex, start, k, hit)]);
  api.cast(u, "sweep", d.windup + d.time + 14, steps);
  return true;
}

/** 휘두르기 k 번째 틱: 머리가 이번 틱까지 쓸고 간 자리의 사람이 맞음 (한 번만) */
function sweepStep(u, apex, start, k, hit) {
  const d = u.def.sweep;
  const arc = (d.angle * Math.PI) / 180;
  const done = (arc * k) / d.time;
  const prev = k === 0 ? -1 : (arc * (k - 1)) / d.time;
  const head = start - SWEEP_DIR * done;
  // 머리가 지나가는 자리: 흙먼지 + 돌 조각
  const hx = { x: apex.x + Math.cos(head) * d.radius * 0.72, y: apex.y, z: apex.z + Math.sin(head) * d.radius * 0.72 };
  dust(u.dim, hx, 1.8, EARTH, 0.6);
  if (k % 3 === 0) chips(u.dim, hx, 2, 0.3, 5);
  if (k % 4 === 0) play(u.dim, "dig.deepslate", hx, 0.7, 1);
  for (const p of targets(u)) {
    if (hit.has(p.id)) continue;
    const r = hdist(p.location, apex);
    if (r > d.radius + 0.3 || Math.abs(p.location.y - apex.y) > 3) continue;
    // 이번 틱에 머리가 지나간 자리의 사람만 (이미 지나간 자리로 들어온 사람은 맞지 않음). 몸 안쪽(굴 위)은 휘두르기 한가운데에
    const sw = sweptTo(start, Math.atan2(p.location.z - apex.z, p.location.x - apex.x));
    const reached = r < 1.2 ? k === Math.ceil(d.time / 2) : sw > prev + 1e-9 && sw <= Math.min(done, arc) + 1e-6;
    if (!reached) continue;
    hit.add(p.id);
    const a = Math.atan2(p.location.z - apex.z, p.location.x - apex.x);
    // 머리가 가는 쪽(접선) + 바깥으로 밀림 (담장 가까이는 안쪽으로)
    const tx = SWEEP_DIR * Math.sin(a);
    const tz = -SWEEP_DIR * Math.cos(a);
    const kk = keepIn(u, p, tx * 0.9 + Math.cos(a) * 0.5, tz * 0.9 + Math.sin(a) * 0.5);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.4 } });
    dust(u.dim, p.location, 1.4, EARTH, 0.5);
  }
}

// ------------------------------------------------------------ 굴 이동 · 튀어나오기
/** 땅속으로 (무적은 틀이 6틱 뒤에 켬): 흙먼지 + 소리 */
function dive(u) {
  const l = u.home;
  u.api.anim(u, "dive", undefined, { controller: DIG, stopExpression: "0" });
  sound(u, "mob.warden.dig", l, 0.9, 1);
  for (let i = 0; i < 4; i++) dust(u.dim, { x: l.x + (Math.random() - 0.5) * 3, y: l.y, z: l.z + (Math.random() - 0.5) * 3 }, 2.2, EARTH, 0.9);
  chips(u.dim, l, 5, 0.4, 6);
}

/** 굴 이동: 둘레 near 칸 안에 사람이 가장 많은 다른 굴 (같으면 무작위) */
function pickHole(u, list) {
  const d = u.def.tunnel;
  const holes = u.feat.holes ?? [];
  const others = holes.map((h, i) => ({ h, i })).filter((x) => x.i !== u.mem.hole);
  if (!others.length) return undefined;
  const score = (h) => list.filter((p) => hdist(p.location, h) <= d.near).length;
  const best = Math.max(...others.map((x) => score(x.h)));
  const pool = others.filter((x) => score(x.h) === best);
  return pool[Math.floor(Math.random() * pool.length)].i;
}

function tunnel(u, now, list) {
  const d = u.def.tunnel;
  const m = u.mem;
  const dest = pickHole(u, list);
  if (dest === undefined) return false;
  m.bigUntil = now + d.sink + d.windup + 40;
  dive(u);
  explain(u, "tunnel", `§6[보스] 굴 이동: §f거대 웜이 땅속(무적)으로 다른 굴로 옮겨 갑니다. §e굴 둘레가 갈라지면 ${d.radius}칸 밖으로 — 튀어나오며 띄웁니다`);
  m.under = { u, dest, moveAt: now + d.sink, riseAt: now + d.sink + d.windup, moved: false, radius: d.radius, damage: d.damage };
  // 튀어나오기는 underTick 이 (그때까지 다른 행동 없음)
  u.api.cast(u, "tunnel", d.sink + d.windup + 16, [[6, (x) => (x.immune = true)]]);
  return true;
}

/** 굴 둘레 예고 (주황 원 + 갈라지는 금 + 흙먼지) */
function riseMarks(u, at, radius, ticks) {
  disc(u.dim, at, radius, ticks, ORANGE);
  crack(u.dim, at, radius * 0.9, ticks / 20 + 0.3);
  play(u.dim, "mob.warden.dig", at, 0.7, 1);
}

function underTick(enc, now) {
  const U = enc.mem.under;
  if (!U) return;
  const u = U.u;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.under = undefined;
    return;
  }
  const h = u.feat.holes[U.dest];
  if (!U.moved && now >= U.moveAt) {
    U.moved = true;
    enc.mem.hole = U.dest;
    u.api.seat(u, floorAt(u, h.x, h.z));
    riseMarks(u, floorAt(u, h.x, h.z), U.radius, U.riseAt - now);
  }
  if (U.moved && now < U.riseAt) {
    if ((U.riseAt - now) % 6 === 0) {
      const a = Math.random() * TAU;
      const r = Math.random() * U.radius * 0.8;
      dust(u.dim, { x: h.x + Math.cos(a) * r, y: h.y, z: h.z + Math.sin(a) * r }, 1.3, EARTH, 0.5);
    }
    if ((U.riseAt - now) % 10 === 0) shakeNear(u, h, U.radius + 5, 0.12, 0.3);
    return;
  }
  if (now >= U.riseAt) {
    enc.mem.under = undefined;
    emerge(u, floorAt(u, h.x, h.z), U.radius, U.damage);
  }
}

/** 굴에서 튀어나옴: 둘레 radius 칸 안은 피해 + 띄움, 가장 가까운 사람 쪽을 봄 */
function emerge(u, at, radius, damage) {
  u.immune = false;
  const t = nearest(u, at);
  u.api.seat(u, at, t ? angOf(dirTo(at, t.location)) : undefined);
  u.api.anim(u, "emerge", undefined, { controller: DIG });
  dust(u.dim, at, radius * 0.9, EARTH, 0.9);
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    dust(u.dim, { x: at.x + Math.cos(a) * radius * 0.75, y: at.y, z: at.z + Math.sin(a) * radius * 0.75 }, 1.6, EARTH, 0.7);
  }
  chips(u.dim, at, 10, 0.45, 9);
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius / 2.1, life: 0.5, color: EARTH, alpha: 0.9 });
  sound(u, "mob.warden.emerge", at, 1.1, 1);
  play(u.dim, "random.explode", at, 0.8, 0.7);
  shakeNear(u, at, radius + 8, 0.3, 0.5);
  for (const p of targets(u)) {
    const r = hdist(p.location, at);
    if (r > radius + 0.3 || Math.abs(p.location.y - at.y) > 3) continue;
    const dir = dirTo(at, p.location);
    const kk = keepIn(u, p, dir.x * 0.5, dir.z * 0.5);
    bossHit(u, p, damage, { knock: { x: kk.x, z: kk.z, h: 0.6 } });
  }
}

// ------------------------------------------------------------ 대지 분출 (체크무늬)
/** 체크무늬 칸 (필드 + 둘레 2.5칸 안): 칸 가운데 월드 좌표 + 어두운 칸인지 */
function quakeTiles(u) {
  const size = u.feat.tile ?? u.def.quake.tile;
  const R = u.enc.def.arena.radius + 2.5;
  const c = u.center;
  const n = Math.ceil(R / size) + 1;
  const out = [];
  for (let i = -n; i <= n; i++) {
    for (let j = -n; j <= n; j++) {
      const x = c.x + i * size;
      const z = c.z + j * size;
      if (Math.hypot(x - c.x, z - c.z) > R) continue;
      out.push({ i, j, x, z, dark: (((i + j) % 2) + 2) % 2 === 0 });
    }
  }
  return out;
}

/** 플레이어가 선 칸이 어두운 칸인지 (바닥 칠하기와 같은 식: 발밑 블록) */
function onDark(u, p) {
  const size = u.feat.tile ?? u.def.quake.tile;
  return deepTile(Math.floor(p.location.x) - u.a.x, Math.floor(p.location.z) - u.a.z, size).dark;
}

function quake(u, now) {
  const d = u.def.quake;
  const m = u.mem;
  const first = Math.random() < 0.5;
  m.bigUntil = now + d.windup + d.gap + 60;
  m.quake = { u, tiles: quakeTiles(u), waves: [{ dark: first, from: now, at: now + d.windup, done: false }, { dark: !first, from: now + d.windup, at: now + d.windup + d.gap, done: false }] };
  u.api.anim(u, "quake");
  sound(u, u.def.sounds.roar, mid(u), 0.8, 1);
  title(u, "§6§l대지 분출", first ? "§f어두운 칸이 먼저 솟습니다 §7→ 다음은 밝은 칸" : "§f밝은 칸이 먼저 솟습니다 §7→ 다음은 어두운 칸", fighters(u));
  explain(u, "quake", "§6[보스] 대지 분출: §f바닥 체크무늬의 한쪽 칸이 솟고 곧바로 반대쪽 칸이 솟습니다. §e빛나는 칸에서 벗어나 방금 솟은 칸으로 옮기세요");
  u.api.cast(u, "quake", d.windup + d.gap + 14, []);
  quakeMarks(u, m.quake, 0);
}

/** 한 번의 분출 예고: 칸마다 네모 (테두리 + 수명 동안 가운데서 차오름) */
function quakeMarks(u, Q, wi) {
  const w = Q.waves[wi];
  const size = u.feat.tile ?? u.def.quake.tile;
  const life = Math.max(0.1, (w.at - w.from) / 20);
  const col = w.dark ? ORANGE : RED;
  for (const t of Q.tiles) {
    if (t.dark !== w.dark) continue;
    fx(u.dim, "rpg:tele_square", { x: t.x, y: u.center.y + 0.06, z: t.z }, { dir: { x: 0, y: 0, z: 0 }, size: size * 0.47, life, color: col, alpha: 0.85, grow: 1 });
    fx(u.dim, "rpg:tele_square", { x: t.x, y: u.center.y + 0.08, z: t.z }, { dir: { x: 0, y: 0, z: 0 }, size: size * 0.47, life, color: col, alpha: 0.5, grow: 0 });
  }
}

function quakeTick(enc, now) {
  const Q = enc.mem.quake;
  if (!Q) return;
  const u = Q.u;
  if (!u.alive) {
    enc.mem.quake = undefined;
    return;
  }
  const d = u.def.quake;
  Q.waves.forEach((w, wi) => {
    if (wi > 0 && now === w.from) quakeMarks(u, Q, wi);
    if (w.done || now < w.at) return;
    w.done = true;
    const y = u.center.y;
    // 흙먼지는 칸의 절반쯤만 (한 틱에 파티클이 너무 몰리지 않게: 모바일)
    for (const t of Q.tiles) {
      if (t.dark !== w.dark) continue;
      if ((t.i * 7 + t.j * 3) % 2 === 0) dust(u.dim, { x: t.x, y, z: t.z }, 1.9, EARTH, 0.6);
      if (Math.random() < 0.15) chips(u.dim, { x: t.x, y, z: t.z }, 1, 0.35, 6);
    }
    sound(u, "random.explode", u.center, 0.6, 0.8);
    play(u.dim, "dig.deepslate", u.center, 0.6, 1);
    for (const p of targets(u)) {
      if (Math.abs(p.location.y - y) > 3 || onDark(u, p) !== w.dark) continue;
      if (bossHit(u, p, d.damage, { knock: { x: 0, z: 0, h: d.lift } })) shake(p, 0.25, 0.4);
    }
    if (wi === Q.waves.length - 1) enc.mem.quake = undefined;
  });
}

// ------------------------------------------------------------ 삼키기
/** 머리가 닿는 곳까지만 (reach 칸 밖이면 그 방향으로 reach 칸) */
function inReach(u, at) {
  const reach = u.def.devour.reach;
  const h = u.home;
  const dx = at.x - h.x;
  const dz = at.z - h.z;
  const L = Math.hypot(dx, dz);
  return L <= reach ? floorAt(u, at.x, at.z) : floorAt(u, h.x + (dx * reach) / L, h.z + (dz * reach) / L);
}

function devour(u, now, list) {
  const d = u.def.devour;
  const m = u.mem;
  const home = u.home;
  const cand = list.filter((p) => hdist(p.location, home) <= d.range && Math.abs(p.location.y - home.y) < 4);
  if (!cand.length) return false;
  const t = cand[Math.floor(Math.random() * cand.length)];
  m.bigUntil = now + d.windup + d.hold + 80;
  m.devour = { u, phase: "aim", target: t, t0: now, lockAt: now + d.windup - d.lock, biteAt: now + d.windup, spot: undefined, last: inReach(u, t.location) };
  u.api.face(u, dirTo(home, t.location));
  u.api.anim(u, "rear");
  sound(u, "mob.warden.sniff", mid(u), 1.1, 1);
  showFaceText(t, "§e§l노림! §f원 밖으로 피하세요", { dist: 2.2, up: 0.35 });
  explain(u, "devour", "§6[보스] 삼키기: §f노란 원이 한 사람을 따라다니다 붉게 멈추면 거대 웜이 덮칩니다. 원 안의 한 명은 삼켜져 §c속박·침묵·실명 + 계속 피해§f. §e나머지가 웜을 때려 뱉게 하세요");
  u.api.cast(u, "devour", d.windup + 12, []);
  return true;
}

/** 삼킨 사람을 둘 자리 (웜 몸 안: 굴 가운데, 가운데 굴은 제단 위) */
function gutSpot(u) {
  const h = u.home;
  const c = u.center;
  const atAltar = Math.hypot(h.x - c.x, h.z - c.z) < 0.5;
  return { x: h.x, y: h.y + (atAltar ? 1 : 0), z: h.z };
}

function bite(u, D, now) {
  const d = u.def.devour;
  const m = u.mem;
  const spot = D.spot ?? D.last;
  // 머리가 닿는 거리에 맞는 덮치기 (크기 3에서 약 4.6 · 7.2 · 9.2칸, build.py WORM_LUNGES)
  const k = hdist(spot, u.home);
  u.api.anim(u, k <= 5.5 ? "lunge_near" : k <= 8.5 ? "lunge" : "lunge_far");
  dust(u.dim, spot, 2.2, EARTH, 0.6);
  play(u.dim, "mob.warden.attack", spot, 0.8, 1);
  const caught = targets(u)
    .filter((p) => hdist(p.location, spot) <= d.radius + 0.3 && Math.abs(p.location.y - spot.y) < 3)
    .sort((a, b) => hdist(a.location, spot) - hdist(b.location, spot));
  if (!caught.length) {
    // 빗나감
    m.devour = undefined;
    m.bigUntil = now + 40;
    chips(u.dim, spot, 4, 0.3, 5);
    return;
  }
  const v = caught[0];
  for (const p of caught.slice(1)) {
    const dir = dirTo(spot, p.location);
    const kk = keepIn(u, p, dir.x * 0.8, dir.z * 0.8);
    bossHit(u, p, d.splash, { knock: { x: kk.x, z: kk.z, h: 0.4 } });
  }
  const helpers = Math.max(1, fighters(u).length - 1);
  Object.assign(D, { phase: "inside", victim: v, t1: now, until: now + d.hold, need: helpers * d.perHelper, dealt: 0, inside: gutSpot(u), lastShown: -1 });
  m.bigUntil = now + d.hold + 80;
  root(v, d.hold + 40, "삼켜짐");
  silence(v, d.hold + 40);
  try {
    v.addEffect("blindness", 40, { amplifier: 0, showParticles: false });
  } catch {
    // 무시
  }
  try {
    v.teleport(D.inside);
  } catch {
    // 무시
  }
  u.api.anim(u, "bulge", undefined, { controller: GUT, stopExpression: "0" });
  play(u.dim, "random.eat", spot, 0.6, 1);
  title(u, "§4§l삼켜졌습니다", "§7동료가 웜을 때리면 뱉어 냅니다 — 버티세요", [v]);
  title(u, `§e§l${v.name}님이 삼켜졌습니다!`, `§f웜에게 피해 ${D.need}를 주면 뱉어 냅니다`, targets(u));
  say(u, `§6[보스] §e${v.name}§f님이 삼켜졌습니다! §7(웜에게 피해 ${D.need}를 주면 뱉어 냄, ${Math.round(d.hold / 20)}초)`);
  nameTag(u, D);
}

/** 이름표에 구출 진행 (평소에는 보스 막대와 같은 이름) */
function nameTag(u, D) {
  try {
    const base = bossLabel(u.def.level, u.def.title, u.def.name);
    if (!D) {
      u.boss.nameTag = base;
      return;
    }
    const n = Math.max(0, Math.min(10, Math.floor((D.dealt / D.need) * 10)));
    u.boss.nameTag = `${base} §e${D.victim.name} 구출 §a${"|".repeat(n)}§8${"|".repeat(10 - n)} §f${Math.min(D.need, Math.round(D.dealt))}/${D.need}`;
  } catch {
    // 무시
  }
}

/** 뱉음: ok = 구출(웜 구역질), 아니면 시간이 다 됨 (더 다친 채로) */
function spit(u, D, ok, now) {
  const d = u.def.devour;
  const m = u.mem;
  const v = D.victim;
  m.devour = undefined;
  m.bigUntil = now + 60 + (ok ? d.gag : 0);
  nameTag(u);
  u.api.anim(u, "spit", undefined, { controller: GUT });
  // 뱉는 쪽: 대각선 굴은 필드 가운데 쪽 (바깥쪽 장식·담장 속에 떨어지지 않게), 가운데 굴은 보는 쪽
  let dir = { x: 0, y: 0, z: 1 };
  if (hdist(u.home, u.center) > 0.5) dir = dirTo(u.home, u.center);
  else {
    try {
      const vd = u.boss.getViewDirection();
      const L = Math.hypot(vd.x, vd.z);
      if (L > 1e-3) dir = { x: vd.x / L, y: 0, z: vd.z / L };
    } catch {
      // 무시
    }
  }
  u.api.face(u, dir);
  const out = floorAt(u, u.home.x + dir.x * 4, u.home.z + dir.z * 4);
  fx(u.dim, "rpg:splash", { x: out.x, y: out.y + 1.4, z: out.z }, { size: 2, life: 0.6, color: BILE, alpha: 0.95 });
  play(u.dim, "random.burp", out, 0.6, 1);
  if (v?.isValid) {
    unroot(v);
    unsilence(v);
    try {
      v.removeEffect("blindness");
    } catch {
      // 무시
    }
    try {
      v.teleport({ x: out.x, y: out.y + 0.6, z: out.z });
      v.applyKnockback({ x: dir.x * 0.8, z: dir.z * 0.8 }, 0.45);
    } catch {
      // 무시
    }
    if (!ok) bossHit(u, v, d.fail, { cause: EntityDamageCause.magic });
    showFaceText(v, ok ? "§a§l뱉어 냈습니다!" : "§c§l뱉어 냈습니다 §7(소화되다 나옴)", { dist: 2.2, up: 0.3 });
  }
  if (ok) {
    u.staggerUntil = now + d.gag;
    u.cast = undefined;
    u.api.anim(u, "gag");
    title(u, "§a§l구출!", `§f거대 웜이 구역질합니다 §7(${Math.round(d.gag / 20)}초)`, targets(u));
  } else say(u, `§6[보스] §f거대 웜이 §e${v?.name ?? "누군가"}§f님을 뱉었습니다 §7(구하지 못함)`);
}

/** 삼킨 사람을 놓아 줌 (죽음·나감·싸움 끝): 뱉는 연출 없이 상태만 풂 */
function release(u, D) {
  const v = D.victim;
  if (u.mem.devour === D) u.mem.devour = undefined;
  if (u.boss?.isValid) {
    nameTag(u);
    u.api.anim(u, "spit", undefined, { controller: GUT }); // 불룩한 모습(유지 동작)을 끝냄
  }
  if (v?.isValid) {
    unroot(v);
    unsilence(v);
    try {
      v.removeEffect("blindness");
    } catch {
      // 무시
    }
  }
}

function devourTick(enc, now) {
  const D = enc.mem.devour;
  if (!D) return;
  const u = D.u;
  const d = u.def.devour;
  if (!u.alive || !u.boss?.isValid) {
    release(u, D);
    return;
  }
  if (D.phase === "aim") {
    const t = D.target;
    if (now < D.lockAt) {
      if (okTarget(t) && fighters(u).includes(t)) {
        D.last = inReach(u, t.location); // 머리가 닿는 곳 밖으로는 따라가지 않음
        u.api.face(u, dirTo(u.home, t.location));
      }
      if ((now - D.t0) % 3 === 0) disc(u.dim, D.last, d.radius, 4, YELLOW, { fill: false });
      return;
    }
    if (!D.spot) {
      D.spot = D.last;
      disc(u.dim, D.spot, d.radius, D.biteAt - now, RED);
      play(u.dim, "mob.warden.clicking", D.spot, 1, 1);
    }
    if (now >= D.biteAt) bite(u, D, now);
    return;
  }
  // 삼킨 뒤
  const v = D.victim;
  if (!v?.isValid || !okTarget(v)) {
    release(u, D);
    return;
  }
  if (hdist(v.location, D.inside) > 0.6 || Math.abs(v.location.y - D.inside.y) > 1.2) {
    try {
      v.teleport(D.inside);
    } catch {
      // 무시
    }
  }
  // 소화: 1초마다 (마지막은 뱉기 11틱 전: 못 구했을 때의 추가 피해가 플레이어 무적 시간에 줄지 않게)
  if ((now - D.t1) % 20 === 9) {
    bossHit(u, v, d.dot, { cause: EntityDamageCause.magic });
    try {
      v.addEffect("blindness", 40, { amplifier: 0, showParticles: false });
    } catch {
      // 무시
    }
    const left = Math.ceil((D.until - now) / 20);
    showFaceText(v, `§4삼켜졌습니다 §7— 동료가 구해 줄 때까지 §f${left}초`, { dist: 2.2, up: 0.3 });
    play(u.dim, "mob.warden.heartbeat", D.inside, 0.8, 1);
  }
  if (now % 5 === 0 && D.lastShown !== Math.round(D.dealt)) {
    D.lastShown = Math.round(D.dealt);
    nameTag(u, D);
  }
  if (D.dealt >= D.need) spit(u, D, true, now);
  else if (now >= D.until) spit(u, D, false, now);
}

// ------------------------------------------------------------ 진동 감지
function tremor(u, now) {
  const d = u.def.tremor;
  const m = u.mem;
  m.bigUntil = Infinity; // 끝(튀어나오기)에서 풂
  m.tremor = { u, t0: now, listenAt: now + d.warn, endAt: now + d.warn + d.listen, track: new Map(), queue: [], rocks: [], heard: new Set(), rise: undefined };
  dive(u);
  sound(u, "shriek.sculk_shrieker", mid(u), 0.8, 1);
  title(u, "§3§l진동 감지", "§f움직이지 마세요! §7웅크려 걸으면 들키지 않습니다", fighters(u));
  explain(u, "tremor", `§3[보스] 진동 감지: §f거대 웜이 땅속(무적)에서 ${Math.round(d.listen / 20)}초 동안 발소리를 듣습니다. §c걷거나 뛰면 발밑에서 튀어나와 삼킵니다 (즉사). §e멈추거나 웅크려 걸으세요 §7— 떨어지는 돌은 웅크려 걸어서 피하세요`);
  countdown(u, d.warn);
  // 끝은 tremorTick 이 (길게 잡고 튀어나온 뒤 줄임)
  u.api.cast(u, "tremor", d.warn + d.listen + 400, [[6, (x) => (x.immune = true)]]);
}

/** 듣기 전 3·2·1 (듣기까지 남은 틱이 20의 배수이고 3초 이하일 때) */
function countdown(u, left) {
  if (left % 20 === 0 && left <= 60) faceAll(u, `§3§l움직이지 마세요 §f${left / 20}`, { dist: 2.4, up: 0.45 }, fighters(u));
}

/** 들킴: strike 틱 뒤 (앞의 것과 gap 틱 간격) 발밑에서 튀어나와 삼킴 */
function heard(u, T, p, now) {
  const d = u.def.tremor;
  T.heard.add(p.id);
  const lastAt = T.queue.length ? T.queue[T.queue.length - 1].at : -Infinity;
  T.queue.push({ p, at: Math.max(now + d.strike, lastAt + d.gap), done: false });
  showFaceText(p, "§4§l들켰습니다!", { dist: 2.2, up: 0.35 });
  play(u.dim, "mob.warden.listening_angry", p.location, 1, 1);
  disc(u.dim, floorAt(u, p.location.x, p.location.z), 2.2, d.strike, RED);
}

/** 발밑에서 튀어나와 삼킴 (즉사): 웜이 그 자리로 옮겨 튀어나왔다 다시 들어감 */
function strike(u, q) {
  const p = q.p;
  q.done = true;
  if (!okTarget(p) || !fighters(u).includes(p)) return;
  const at = floorAt(u, p.location.x, p.location.z);
  u.api.seat(u, at);
  u.api.anim(u, "strike", undefined, { controller: DIG, stopExpression: "0" });
  dust(u.dim, at, 3.2, EARTH, 0.8);
  chips(u.dim, at, 8, 0.4, 9);
  crack(u.dim, at, 2.6, 1.2);
  sound(u, "mob.warden.emerge", at, 1.3, 1);
  play(u.dim, "random.eat", at, 0.5, 1);
  shakeNear(u, at, 10, 0.3, 0.5);
  say(u, `§3[보스] §e${p.name}§f님이 발소리를 들켜 삼켜졌습니다`);
  kill(p, u.boss);
}

function tremorTick(enc, now) {
  const T = enc.mem.tremor;
  if (!T) return;
  const u = T.u;
  const d = u.def.tremor;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.tremor = undefined;
    return;
  }
  const floorY = u.center.y;
  const holes = u.feat.holes ?? [];
  // 듣는 소리: 굴마다 퍼지는 물결 (예고·듣는 동안)
  if (now < T.endAt && (now - T.t0) % 10 === 0) {
    for (const h of holes) fx(u.dim, "rpg:tele_wave", { x: h.x, y: floorY + 0.07, z: h.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 9, size: 1, life: 1.0, color: SOUND, alpha: 0.75 });
  }
  if (now < T.listenAt) {
    countdown(u, T.listenAt - now);
    return;
  }
  if (now < T.endAt) {
    const cur = fighters(u);
    if (now === T.listenAt) {
      sound(u, "mob.warden.listening", u.center, 0.8, 1);
      faceAll(u, "§b§l듣는 중… §7움직이면 들킵니다", { dist: 2.4, up: 0.45 }, cur);
      T.listeners = new Set(cur.map((p) => p.id)); // 예고를 본 사람만 들음 (듣는 중에 필드에 들어온 사람은 듣지 않음)
    }
    if ((now - T.listenAt) % 20 === 0) play(u.dim, "mob.warden.heartbeat", u.center, 0.7, 1);
    // 필드를 나간 사람의 기록은 지움 (돌아오면 그 자리부터 새로 셈)
    const ids = new Set(cur.map((p) => p.id));
    for (const id of [...T.track.keys()]) if (!ids.has(id)) T.track.delete(id);
    // 발소리 듣기: 웅크리지 않고 grace 칸 넘게 움직였거나 뛰면 들킴 (웅크리고 뛰어도 들킴)
    for (const p of cur) {
      if (T.heard.has(p.id) || !T.listeners?.has(p.id)) continue;
      let rec = T.track.get(p.id);
      if (!rec) {
        rec = { last: { ...p.location }, acc: 0, warned: false, calm: 0 };
        T.track.set(p.id, rec);
        continue;
      }
      const moved = hdist(p.location, rec.last);
      rec.last = { ...p.location };
      if (now < rec.calm) continue; // 돌에 맞아 휘청이는 동안은 셈하지 않음
      const sneaking = p.isSneaking === true;
      if (!sneaking && moved > 0.02) rec.acc += moved;
      const jumped = !riding(p) && p.isOnGround === false && p.location.y - floorY > 0.2;
      if (jumped || rec.acc > d.grace) heard(u, T, p, now);
      else if (!rec.warned && rec.acc > d.grace * 0.4) {
        rec.warned = true;
        showFaceText(p, "§e§l쉿! §7움직이면 들킵니다 — 웅크리세요", { dist: 2.2, up: 0.35 });
      }
    }
    // 떨어지는 돌: 한 사람씩 머리 위 원 → 떨어짐 (웅크려 걸어 나와야 함)
    d.rockAt.forEach((k) => {
      if (now !== T.listenAt + k) return;
      const pool = fighters(u).filter((p) => !T.heard.has(p.id));
      for (let i = pool.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [pool[i], pool[j]] = [pool[j], pool[i]];
      }
      for (const p of pool.slice(0, d.rocks)) {
        const spot = floorAt(u, p.location.x, p.location.z);
        disc(u.dim, spot, d.rockRadius, d.rockWindup, ORANGE);
        T.rocks.push({ spot, at: now + d.rockWindup, fell: false, done: false });
      }
    });
  }
  // 돌: 떨어지기 18틱 전에 위에서 떨어뜨림 (중력으로 원 가운데에), 닿으면 원 안 피해 (밀지 않음: 밀리면 발소리가 남)
  for (const rk of T.rocks) {
    if (!rk.fell && now >= rk.at - 18) {
      rk.fell = true;
      fx(u.dim, "rpg:boulder", { x: rk.spot.x, y: rk.spot.y + 8.1, z: rk.spot.z }, { dir: { x: 0, y: -1, z: 0 }, speed: 0, spin: Math.random() * 360, size: 1.1, life: 0.95, color: ROCK, alpha: 1 });
    }
    if (rk.done || now < rk.at) continue;
    rk.done = true;
    dust(u.dim, rk.spot, 1.8, DUST, 0.6);
    chips(u.dim, rk.spot, 3, 0.3, 4);
    play(u.dim, "dig.deepslate", rk.spot, 0.6, 1);
    for (const p of targets(u)) {
      if (hdist(p.location, rk.spot) > d.rockRadius + 0.3 || Math.abs(p.location.y - floorY) > 3) continue;
      // 떨어지는 돌: 때린 엔티티 없이 (게임이 웜 쪽에서 밀어내지 않게), 맞은 사람은 calm 틱 동안 발소리를 셈하지 않음
      if (bossHit(u, p, d.rockDamage, { cause: EntityDamageCause.fallingBlock, noSource: true })) {
        const rec = T.track.get(p.id);
        if (rec) rec.calm = now + d.calm;
      }
    }
  }
  // 들킨 사람: 차례로 발밑에서
  for (const q of T.queue) if (!q.done && now >= q.at) strike(u, q);
  if (now < T.endAt || T.queue.some((q) => !q.done) || T.rocks.some((r) => !r.done)) return;
  // 끝: 아무 굴에 예고 → 튀어나옴
  if (!T.rise) {
    const i = Math.floor(Math.random() * Math.max(1, holes.length));
    const h = holes[i] ?? u.center;
    T.rise = { i, at: floorAt(u, h.x, h.z), when: now + d.emerge };
    u.api.seat(u, T.rise.at);
    riseMarks(u, T.rise.at, d.radius, d.emerge);
    faceAll(u, "§a§l소리를 내도 됩니다", { dist: 2.4, up: 0.45 }, fighters(u));
    return;
  }
  if (now < T.rise.when) return;
  enc.mem.tremor = undefined;
  enc.mem.hole = T.rise.i;
  enc.mem.bigUntil = now + 60;
  emerge(u, T.rise.at, d.radius, d.damage);
  if (u.cast?.id === "tremor") u.cast.until = now + 16;
}

// ------------------------------------------------------------ 대지 가르기 (멀리 있는 사람)
/** 금 한 줄: 웜 몸 가장자리(굴에서 2칸)에서 대상 쪽으로 대상 너머 over 칸까지, 필드 가장자리(반지름 + 2)에서 멈춤 */
function rendLine(u, p) {
  const d = u.def.rend;
  const home = u.home;
  const dir = dirTo(home, p.location);
  const from = floorAt(u, home.x + dir.x * 2, home.z + dir.z * 2);
  let L = Math.max(2, hdist(p.location, from) + d.over);
  const edge = u.enc.def.arena.radius + 2;
  const ox = from.x - u.center.x;
  const oz = from.z - u.center.z;
  const b = ox * dir.x + oz * dir.z;
  const q = b * b - (ox * ox + oz * oz - edge * edge);
  if (q >= 0) L = Math.max(2, Math.min(L, -b + Math.sqrt(q)));
  return { from, dir, L };
}

function rend(u, now, list) {
  const d = u.def.rend;
  const home = u.home;
  const far = list.filter((p) => hdist(p.location, home) > d.far && Math.abs(p.location.y - home.y) < 4);
  if (!far.length) return false;
  const pool = [...far];
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  const lines = pool.slice(0, d.count).map((p) => rendLine(u, p));
  u.api.face(u, lines[0].dir);
  u.api.anim(u, "rend");
  sound(u, u.def.sounds.roar, mid(u), 0.7, 1.2);
  for (const ln of lines) {
    const to = { x: ln.from.x + ln.dir.x * ln.L, y: ln.from.y, z: ln.from.z + ln.dir.z * ln.L };
    lineMarks(u.dim, ln.from, to, d.width / 2, d.windup, ORANGE);
    crack(u.dim, ln.from, 1.2, d.windup / 20);
  }
  explain(u, "rend", "§6[보스] 대지 가르기: §f거대 웜이 멀리 있는 사람 쪽으로 땅을 가릅니다. §e발밑으로 이어진 주황 줄에서 옆으로 비켜서세요 §7— 멀리 있어도 안전하지 않습니다");
  const hit = new Set();
  const ticks = Math.ceil(Math.max(...lines.map((ln) => ln.L)) / d.speed);
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let k = 0; k <= ticks; k++) steps.push([d.windup + k, (x) => rendStep(x, lines, k, hit)]);
  u.api.cast(u, "rend", d.windup + ticks + 10, steps);
  return true;
}

/** 바위가 솟는 k 번째 틱: 금을 따라 이번 틱에 솟은 구간 위의 사람이 맞음 (한 번만, 이미 솟은 곳으로 들어온 사람은 맞지 않음) */
function rendStep(u, lines, k, hit) {
  const d = u.def.rend;
  const s0 = (k - 1) * d.speed;
  const s1 = k * d.speed;
  for (const ln of lines) {
    if (s0 > ln.L) continue;
    const s = Math.min(s1, ln.L);
    const at = { x: ln.from.x + ln.dir.x * s, y: ln.from.y, z: ln.from.z + ln.dir.z * s };
    // 솟는 바위: 흙먼지 + 금 + 위로 튀는 큰 돌 조각 (금을 따라 달려가는 모습)
    dust(u.dim, at, 2.0, EARTH, 0.55);
    crack(u.dim, at, 1.4, 1.0);
    chips(u.dim, at, 2, 0.6, 8);
    if (k % 3 === 0) play(u.dim, "dig.deepslate", at, 0.8, 0.9);
    for (const p of targets(u)) {
      if (hit.has(p.id) || Math.abs(p.location.y - ln.from.y) > 3) continue;
      const rx = p.location.x - ln.from.x;
      const rz = p.location.z - ln.from.z;
      const t = rx * ln.dir.x + rz * ln.dir.z;
      if (t <= s0 - 0.5 || t > s + 0.5) continue;
      if (Math.abs(rx * ln.dir.z - rz * ln.dir.x) > d.width / 2 + 0.3) continue;
      hit.add(p.id);
      if (bossHit(u, p, d.damage, { knock: { x: 0, z: 0, h: d.lift } })) shake(p, 0.2, 0.3);
    }
  }
}

// ------------------------------------------------------------ 높은 곳
/** 성문·담장 탑 위처럼 높은 곳(바닥보다 high.above 칸 넘게)에 서 있는 사람: 땅을 울려 떨어뜨림 (사람마다 cd 틱에 한 번) */
function highTick(enc, now) {
  if (now % 10 !== 0 || enc.mem.tremor) return; // 진동 감지 중에는 없음 (밀려 떨어지는 소리로 들키지 않게)
  const u = enc.units[0];
  if (!u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const c = enc.center;
  for (const p of targets(u)) {
    if (p.isOnGround === false || p.location.y - c.y < d.above) continue;
    if (now - (enc.mem.high.get(p.id) ?? -1000) < d.cd) continue;
    enc.mem.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    dust(enc.dim, p.location, 1.8, EARTH, 0.6);
    chips(enc.dim, p.location, 3, 0.3, 5);
    play(enc.dim, "dig.deepslate", p.location, 0.6, 1);
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 } });
    showFaceText(p, "§6§l땅이 울려 떨어집니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", "§6[보스] §f성문·담장 탑 위처럼 높은 곳에 올라서면 거대 웜이 땅을 울려 떨어뜨립니다");
  }
}

// ------------------------------------------------------------ 모듈
export default {
  key: "great_worm",
  /** 원거리 견제 (v2.3.0): 땅속·삼키는 중·진동 감지(움직이면 안 됨) 동안은 쉼 */
  farOk(u) {
    return !u.mem.under && !u.mem.devour && !u.mem.tremor;
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    enc.mem = {
      next: { sweep: now + d.sweep.first, tunnel: now + d.tunnel.first, quake: now + d.quake.first, devour: now + d.devour.first, rend: now + d.rend.first },
      bigUntil: now + 100,
      told: new Set(),
      hole: 0,
      under: undefined,
      quake: undefined,
      devour: undefined,
      tremor: undefined,
      tremorDone: new Set(),
      high: new Map(),
    };
    // 굴에서 튀어나오며 등장
    enc.api.anim(enc, "emerge", undefined, { controller: DIG });
    dust(enc.dim, enc.center, 4, EARTH, 0.9);
    chips(enc.dim, enc.center, 10, 0.45, 9);
  },
  /** 삼켜진 사람은 평타·패턴의 대상이 아님 */
  targetable(enc, p) {
    return !inside(enc, p);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    const r = u.api.ratio(u);
    if (m.under || m.tremor) return false;
    // 큰 패턴: 진동 감지 → 삼키기 → 대지 분출 → 굴 이동 (삼킨 사람이 있거나 다른 큰 패턴이 끝나지 않았으면 없음)
    if (!m.devour && !m.quake && now >= m.bigUntil) {
      const at = d.tremor.at.find((x) => r <= x && !m.tremorDone.has(x));
      if (at !== undefined) {
        m.tremorDone.add(at);
        tremor(u, now);
        return true;
      }
      if (r <= d.devour.from && now >= m.next.devour) {
        if (devour(u, now, list)) {
          m.next.devour = now + every(u, d.devour.every);
          return true;
        }
        m.next.devour = now + 40; // 닿는 곳에 아무도 없음: 조금 뒤 다시
      }
      if (r <= d.quake.from && now >= m.next.quake) {
        m.next.quake = now + every(u, d.quake.every);
        quake(u, now);
        return true;
      }
      if (now >= m.next.tunnel) {
        m.next.tunnel = now + every(u, d.tunnel.every);
        if (tunnel(u, now, list)) return true;
      }
    }
    if (now >= m.next.sweep) {
      if (sweep(u, now, list)) {
        m.next.sweep = now + every(u, d.sweep.every);
        return true;
      }
      m.next.sweep = now + 20;
    }
    // 멀리 있는 사람: 대지 가르기 (삼킨 사람이 있으면 없음: 입이 막힘)
    if (!m.devour && now >= m.next.rend) {
      if (rend(u, now, list)) {
        m.next.rend = now + every(u, d.rend.every);
        return true;
      }
      m.next.rend = now + 40;
    }
    return false;
  },
  /** 받는 피해: 삼킨 사람이 있으면 구출에 셈 (삼켜진 사람 자신은 빼고) */
  hurt(u, p, amount) {
    const D = u.mem.devour;
    if (D?.phase === "inside" && amount > 0 && p?.id !== D.victim?.id) D.dealt += amount;
    return amount;
  },
  tick(enc, now) {
    underTick(enc, now);
    tremorTick(enc, now);
    quakeTick(enc, now);
    devourTick(enc, now);
    highTick(enc, now);
    // 굴 둘레 흙먼지 (가끔, 땅 위에 있을 때)
    if (now % 40 === 0 && enc.alive && enc.boss?.isValid && !enc.mem.under && !enc.mem.tremor) {
      const a = Math.random() * TAU;
      dust(enc.dim, { x: enc.home.x + Math.cos(a) * 2, y: enc.center.y, z: enc.home.z + Math.sin(a) * 2 }, 1, EARTH, 0.5);
    }
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.next) return;
    if (m.devour) release(enc, m.devour);
    m.devour = undefined;
    m.under = undefined;
    m.quake = undefined;
    m.tremor = undefined;
  },
};

