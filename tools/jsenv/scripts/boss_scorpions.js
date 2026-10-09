import { EntityDamageCause, system } from "@minecraft/server";
import { play, showFaceText } from "./fx.js";
import { beam, fx } from "./skills.js";
import { kill, root, silence } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fighters, hdist, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 사막 전갈 + 바위 전갈 (v1.9.0, Lv.18, 한 번에 둘): 수치는 config.js BOSSES.scorpions.units
 * 사막 전갈 (서쪽 모래, 빠름)
 * - 평타 집게 (앞 90°, 엔진)
 * - 독침: 가장 먼 플레이어 발밑 초록 원 → 꼬리에서 독침 (피해 + 독)
 * - 모래 잠행: 모래 속으로 사라짐 (무적) → 흙먼지 줄기가 한 사람을 쫓다가 멈춘 자리에 원 → 튀어나오며 띄움
 * - 맹독 표식 (체력 70% 아래): 한 사람에게 표식, 6초 뒤 둘레 3칸 안의 사람끼리 피해를 나눠 받음 (마법). 혼자면 즉사
 * 바위 전갈 (동쪽 바위, 느리고 셈)
 * - 평타 집게 내려찍기 (앞 120°, 엔진)
 * - 바위 던지기: 세 사람 발밑 갈색 원 → 꼬리로 던진 바위가 포물선으로 떨어짐 (피해 + 느림)
 * - 대지의 파동: 세 번 내려찍으며 퍼지는 고리 → 고리가 지나갈 때 땅에 있으면 맞음 (점프로 피함)
 * - 석화의 포효 (체력 70% 아래): 2.5초 울부짖음 → 그때 바위 전갈 쪽을 보던 사람은 4초 석화 (속박 + 침묵)
 * 함께
 * - 공생: 둘이 7칸 안이면 둘 다 받는 피해 -60% (금빛 끈) → 파티를 나눠 떨어뜨림
 * - 짝 잃은 분노: 하나가 쓰러지면 남은 쪽 피해 +40%, 패턴 재사용 ×0.7, 빠른 걸음
 * - 큰 패턴(모래 잠행·맹독 표식·대지의 파동·석화의 포효)은 둘 사이에서도 겹치지 않음 (mem.bigUntil)
 * 엔진 함수(cast·hold·anim·setFast·other·ratio)는 u.api 로 받음. 패턴 함수는 그 마리(u)를 받음
 */

const DESERT = "desert_scorpion";
const ROCK = "rock_scorpion";
const SAND = [0.96, 0.78, 0.4];
const DUST = [0.95, 0.55, 0.2];
const POISON = [0.45, 0.95, 0.3];
const STONE = [0.72, 0.6, 0.48];
const GRAY = [0.62, 0.62, 0.66];
const GOLD = [1.0, 0.85, 0.35];
const RAGE = [1.0, 0.2, 0.12];

const rnd = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const shuffle = (list) => {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};
/** 다음 패턴까지 (분노면 cdMult 배) */
const every = (u, range) => Math.round(rnd(range[0], range[1]) * (u.cdMult ?? 1));
const unitOf = (enc, key) => enc.units.find((u) => u.key === key);
/** 바닥 높이 (필드 가운데) */
const floorAt = (u, x, z) => ({ x, y: u.center.y, z });
/**
 * 공중인지 (대지의 파동을 점프로 피함): isOnGround 가 false 이거나, 바닥보다 0.25칸 넘게 떠 있고 발밑이 비었음
 * (발밑 블록을 보는 것은 isOnGround 가 늦게 바뀌는 경우를 위해. 제단·바위 위에 서 있으면 땅)
 */
function airborne(p, floorY) {
  if (p.isOnGround === false) return true;
  if (p.location.y - floorY <= 0.25) return false;
  try {
    return !!p.dimension.getBlock({ x: p.location.x, y: p.location.y - 0.3, z: p.location.z })?.isAir;
  } catch {
    return false;
  }
}
/** 땅속에 있는 동안 유지 (다음 동작 = 튀어나오기가 같은 틀에서 바꿈) */
const BURROW_ANIM = { controller: "rpg_burrow", stopExpression: "0" };

/** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
function explain(u, id, text) {
  const told = u.mem.told;
  if (told.has(id)) return;
  told.add(id);
  say(u, text);
}

/** 몸 가운데 높이 (끈·연출용) */
function mid(u) {
  const l = u.boss.location;
  return { x: l.x, y: l.y + bossSize(u.def).height * 0.6, z: l.z };
}

/** 꼬리 끝 (독침·바위를 던지는 곳): 몸 위 조금 앞 */
function tailTip(u) {
  const l = u.boss.location;
  let f = { x: 0, z: 0 };
  try {
    const v = u.boss.getViewDirection();
    const L = Math.hypot(v.x, v.z) || 1;
    f = { x: v.x / L, z: v.z / L };
  } catch {
    // 무시
  }
  const s = u.def.scale;
  return { x: l.x + f.x * 0.3 * s, y: l.y + 1.75 * s, z: l.z + f.z * 0.3 * s };
}

// ============================================================ 사막 전갈
// ------------------------------------------------------------ 독침
/** 가장 먼 사람 (range 안) */
function stingTarget(u, list) {
  const d = u.def.sting;
  let tgt;
  let far = -1;
  for (const p of list) {
    const k = hdist(p.location, u.boss.location);
    if (k <= d.range && k > far) {
      far = k;
      tgt = p;
    }
  }
  return tgt;
}

function sting(u, now, target) {
  const d = u.def.sting;
  const api = u.api;
  const spot = floorAt(u, target.location.x, target.location.z);
  api.hold(u, true);
  api.anim(u, "sting");
  sound(u, "mob.spider.say", u.boss.location, 1.3, 1);
  disc(u.dim, spot, d.radius, d.windup, POISON);
  api.cast(u, "sting", d.windup + 10, [
    [
      d.windup - 3,
      (x) => {
        // 꼬리에서 독침이 날아감
        beam(x.dim, tailTip(x), { x: spot.x, y: spot.y + 0.3, z: spot.z }, POISON, { life: 0.3, half: 0.3, thick: 0.12, gap: 0.45 });
      },
    ],
    [
      d.windup,
      (x) => {
        fx(x.dim, "rpg:bash_burst", { x: spot.x, y: spot.y + 0.5, z: spot.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.radius * 1.2, life: 0.35, color: POISON, alpha: 0.9 });
        play(x.dim, "mob.bee.sting", spot, 0.7, 1);
        for (const p of fighters(x)) {
          if (hdist(p.location, spot) > d.radius + 0.3 || Math.abs(p.location.y - spot.y) > 3) continue;
          bossHit(x, p, d.damage, { effect: { id: "poison", ticks: d.poison } });
        }
      },
    ],
    [d.windup + 6, (x) => api.hold(x, false)],
  ]);
}

// ------------------------------------------------------------ 모래 잠행
/** 쫓을 사람: 사막 전갈이 닿는 곳(제 자리 둘레 + 3칸) 안의 아무나 */
function burrowTarget(u, list) {
  const near = list.filter((p) => hdist(p.location, u.home) <= u.leash + 3);
  return near.length ? near[Math.floor(Math.random() * near.length)] : undefined;
}

function burrow(u, now, target) {
  const d = u.def.burrow;
  const api = u.api;
  const m = u.mem;
  m.bigUntil = now + d.sink + d.trail + d.windup + 40;
  api.hold(u, true);
  api.anim(u, "burrow", undefined, BURROW_ANIM);
  sound(u, "mob.warden.dig", u.boss.location, 1.3, 1);
  const l = u.boss.location;
  for (let i = 0; i < 3; i++) fx(u.dim, "rpg:bash_burst", { x: l.x + (Math.random() - 0.5) * 2, y: u.center.y + 0.4, z: l.z + (Math.random() - 0.5) * 2 }, { dir: { x: 0, y: 0, z: 1 }, size: 1.6, life: 0.45, color: SAND, alpha: 0.9 });
  showFaceText(target, "§6§l땅속에서 쫓아옵니다! §e흙먼지를 보세요", { dist: 2.2, up: 0.35 });
  explain(u, "burrow", `§6[보스] 모래 잠행: §f사막 전갈이 땅속으로 사라져(무적) 흙먼지 줄기로 한 사람을 쫓습니다. §e줄기가 멈춘 자리의 원 밖으로 피하세요`);
  m.burrow = { u, target, head: floorAt(u, l.x, l.z), start: now + d.sink, stop: now + d.sink + d.trail, spot: undefined, hitAt: 0 };
  // 끝(튀어나오기)은 burrowTick 이 틀을 바꿔서: 그때까지 이 마리는 다른 행동 없음
  api.cast(u, "burrow", d.sink + d.trail + d.windup + 30, [[6, (x) => (x.immune = true)]]);
}

/** 흙먼지 줄기 한 칸 (제 자리 둘레 안으로 잘라 둠: 튀어나온 뒤 끌려가지 않게) */
function clampHome(u, p) {
  const h = u.home;
  const dx = p.x - h.x;
  const dz = p.z - h.z;
  const L = Math.hypot(dx, dz);
  const max = u.leash - 1;
  if (L <= max) return p;
  return { x: h.x + (dx / L) * max, y: p.y, z: h.z + (dz / L) * max };
}

function burrowTick(enc, now) {
  const b = enc.mem.burrow;
  if (!b) return;
  const u = b.u;
  const d = u.def.burrow;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.burrow = undefined;
    return;
  }
  if (now < b.start) return;
  if (!b.spot) {
    // 쫓기: 대상 쪽으로 speed 칸/초
    const t = b.target;
    const alive = okTarget(t);
    if (alive) {
      const dx = t.location.x - b.head.x;
      const dz = t.location.z - b.head.z;
      const L = Math.hypot(dx, dz);
      const step = Math.min(L, d.speed / 20);
      if (L > 0.01) b.head = clampHome(u, { x: b.head.x + (dx / L) * step, y: b.head.y, z: b.head.z + (dz / L) * step });
    }
    if (now % 2 === 0) {
      fx(u.dim, "rpg:bash_burst", { x: b.head.x, y: b.head.y + 0.35, z: b.head.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.1, life: 0.35, color: SAND, alpha: 0.9 });
      try {
        u.boss.teleport({ x: b.head.x, y: b.head.y + 0.05, z: b.head.z });
      } catch {
        // 무시
      }
    }
    if (now % 4 === 0) disc(u.dim, b.head, 0.9, 8, DUST, { alpha: 0.7 });
    if (now % 6 === 0) play(u.dim, "dig.sand", b.head, 0.8, 1);
    const reached = alive && hdist(t.location, b.head) <= 0.8;
    if (reached || !alive || now >= b.stop) {
      // 멈춘 자리: 원이 차오르면 튀어나옴
      b.spot = { ...b.head };
      b.hitAt = now + d.windup;
      disc(u.dim, b.spot, d.radius, d.windup, DUST);
      play(u.dim, "mob.warden.emerge", b.spot, 1.4, 0.9);
    }
    return;
  }
  if (now < b.hitAt) {
    if ((b.hitAt - now) % 6 === 0) fx(u.dim, "rpg:bash_burst", { x: b.spot.x + (Math.random() - 0.5) * d.radius, y: b.spot.y + 0.3, z: b.spot.z + (Math.random() - 0.5) * d.radius }, { dir: { x: 0, y: 0, z: 1 }, size: 1.0, life: 0.3, color: SAND, alpha: 0.85 });
    return;
  }
  emerge(enc, b);
}

/** 튀어나옴: 원 안은 피해 + 띄움, 전갈이 그 자리에 나타남 */
function emerge(enc, b) {
  const u = b.u;
  const d = u.def.burrow;
  const api = u.api;
  const s = b.spot;
  enc.mem.burrow = undefined;
  u.immune = false;
  let face = { x: s.x, y: s.y + 1, z: s.z - 1 };
  if (okTarget(b.target)) face = { x: b.target.location.x, y: s.y + 1, z: b.target.location.z };
  try {
    u.boss.teleport({ x: s.x, y: s.y + 0.1, z: s.z }, { facingLocation: face });
  } catch {
    // 무시
  }
  api.anim(u, "emerge", undefined, { controller: BURROW_ANIM.controller });
  fx(u.dim, "rpg:bash_burst", { x: s.x, y: s.y + 1, z: s.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.radius * 1.3, life: 0.5, color: SAND, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: s.x, y: s.y + 0.1, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius / 2.1, life: 0.45, color: DUST, alpha: 0.9 });
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    fx(u.dim, "rpg:bash_burst", { x: s.x + Math.cos(a) * d.radius * 0.7, y: s.y + 0.5, z: s.z + Math.sin(a) * d.radius * 0.7 }, { dir: { x: 0, y: 0, z: 1 }, size: 1.2, life: 0.4, color: SAND, alpha: 0.85 });
  }
  play(u.dim, "random.explode", s, 1.3, 0.8);
  play(u.dim, "dig.sand", s, 0.6, 1);
  for (const p of fighters(u)) {
    if (hdist(p.location, s) > d.radius + 0.3 || Math.abs(p.location.y - s.y) > 3) continue;
    const dx = p.location.x - s.x;
    const dz = p.location.z - s.z;
    const L = Math.hypot(dx, dz) || 1;
    bossHit(u, p, d.damage, { knock: { x: (dx / L) * 0.4, z: (dz / L) * 0.4, h: d.lift } });
  }
  // 틀을 바꿈: 잠깐 뒤 다시 움직임
  api.cast(u, "emerge", 20, [[16, (x) => api.hold(x, false)]]);
}

// ------------------------------------------------------------ 맹독 표식
function venom(u, now, list) {
  const d = u.def.venom;
  const api = u.api;
  const m = u.mem;
  const p = list[Math.floor(Math.random() * list.length)];
  m.bigUntil = now + d.time + 20;
  m.venom = { u, p, at: now + d.time };
  api.hold(u, true);
  api.anim(u, "hiss");
  sound(u, "mob.spider.say", u.boss.location, 0.6, 1);
  title(u, "§2§l맹독 표식", `§a${p.name}§f 곁으로 모이세요! §7(${d.radius}칸 안에서 나눠 받음, 혼자면 즉사)`, fighters(u));
  showFaceText(p, "§2§l맹독 표식! §f동료 곁으로", { dist: 2.2, up: 0.35 });
  explain(u, "venom", `§2[보스] 맹독 표식: §f${Math.round(d.time / 20)}초 뒤 표식이 터집니다. §a표식 둘레 ${d.radius}칸 안에 모인 사람끼리 피해를 나눠 받고, §4혼자면 즉사§f합니다`);
  api.cast(u, "venom", 24, [[20, (x) => api.hold(x, false)]]);
}

function venomTick(enc, now) {
  const v = enc.mem.venom;
  if (!v) return;
  const u = v.u;
  const d = u.def.venom;
  // 표식은 필드 밖으로 달아나도 따라감 (싸움에서 아주 멀어지면·다른 차원이면·쓰러지면 사라짐)
  let gone = !u.alive || !okTarget(v.p);
  try {
    gone = gone || v.p.dimension.id !== enc.a.dim || hdist(v.p.location, enc.center) > 64;
  } catch {
    gone = true;
  }
  if (gone) {
    enc.mem.venom = undefined;
    return;
  }
  const at = v.p.location;
  if (now % 5 === 0) {
    disc(u.dim, floorAt(u, at.x, at.z), d.radius, 6, POISON, { fill: false, alpha: 0.95 });
    try {
      const h = v.p.getHeadLocation();
      fx(u.dim, "rpg:skill_rune", { x: h.x, y: h.y + 0.75, z: h.z }, { dir: { x: 0, y: 1, z: 0 }, size: 0.5, life: 0.3, spin: (now * 14) % 360, progress: 1, color: POISON, alpha: 0.95 });
    } catch {
      // 무시
    }
  }
  const left = v.at - now;
  if (left > 0) {
    if (left % 20 === 0 && left <= 60) faceAll(u, `§2§l맹독 ${left / 20}`, { dist: 2.4, up: 0.5 }, fighters(u));
    return;
  }
  enc.mem.venom = undefined;
  // 나눠 받는 사람: 표식 둘레의 모든 플레이어 (필드 밖 성문 자리에 함께 있어도 셈)
  let near = [];
  try {
    near = u.dim.getPlayers().filter((q) => okTarget(q) && hdist(q.location, at) <= d.radius + 0.3 && Math.abs(q.location.y - at.y) < 3);
  } catch {
    near = [];
  }
  const group = near;
  if (!group.includes(v.p)) group.push(v.p);
  fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.radius * 1.2, life: 0.5, color: POISON, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius / 2.1, life: 0.5, color: POISON, alpha: 0.9 });
  play(u.dim, "mob.slime.big", at, 0.5, 1);
  play(u.dim, "random.explode", at, 1.6, 0.6);
  if (group.length <= 1) {
    showFaceText(v.p, "§4§l즉사 §7— 혼자였습니다", { dist: 2.2, up: 0.3 });
    say(u, `§4[보스] ${v.p.name}님이 맹독 표식을 혼자 받았습니다`);
    kill(v.p, u.boss);
    return;
  }
  const each = d.total / group.length;
  for (const q of group) {
    fx(u.dim, "rpg:bash_burst", { x: q.location.x, y: q.location.y + 1, z: q.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.2, life: 0.35, color: POISON, alpha: 0.9 });
    showFaceText(q, `§2맹독 §f÷${group.length}`, { dist: 2.4, side: 0.5, up: 0.2 });
    bossHit(u, q, each, { cause: EntityDamageCause.magic }); // 마법: 갑옷으로 줄지 않음 (나눠 받는 게 요령)
  }
}

// ============================================================ 바위 전갈
// ------------------------------------------------------------ 바위 던지기
const GRAV = 20; // 바위 파티클의 중력 (build.py rpg:boulder 와 같게)

/** from → to 로 t 초 동안 포물선 (파티클 속도·방향) */
export function boulderArc(from, to, t) {
  const v = { x: (to.x - from.x) / t, y: (to.y - from.y) / t + (GRAV * t) / 2, z: (to.z - from.z) / t };
  const speed = Math.hypot(v.x, v.y, v.z) || 1;
  return { dir: { x: v.x / speed, y: v.y / speed, z: v.z / speed }, speed };
}

function boulders(u, now, list) {
  const d = u.def.boulders;
  const api = u.api;
  const spots = shuffle(list)
    .slice(0, d.count)
    .map((p) => floorAt(u, p.location.x, p.location.z));
  api.hold(u, true);
  api.anim(u, "throw");
  sound(u, "mob.irongolem.throw", u.boss.location, 0.6, 1);
  for (const s of spots) disc(u.dim, s, d.radius, d.windup, STONE);
  const launch = 8;
  api.cast(u, "boulders", launch + 14, [
    [
      launch,
      (x) => {
        const from = tailTip(x);
        const t = (d.windup - launch) / 20;
        for (const s of spots) {
          const arc = boulderArc(from, { x: s.x, y: s.y + 0.4, z: s.z }, t);
          fx(x.dim, "rpg:boulder", from, { dir: arc.dir, speed: arc.speed, size: 0.75, life: t, spin: Math.random() * 360, color: [1, 1, 1], alpha: 1 });
        }
      },
    ],
    [launch + 12, (x) => api.hold(x, false)],
  ]);
  u.mem.boulders.push({ u, spots, at: now + d.windup });
}

function boulderTick(enc, now) {
  const m = enc.mem;
  const due = m.boulders.filter((b) => now >= b.at);
  if (!due.length) return;
  m.boulders = m.boulders.filter((b) => now < b.at);
  for (const b of due) {
    const u = b.u;
    if (!u.alive) continue;
    const d = u.def.boulders;
    for (const s of b.spots) {
      fx(u.dim, "rpg:bash_burst", { x: s.x, y: s.y + 0.6, z: s.z }, { dir: { x: 0, y: 0, z: 1 }, size: d.radius, life: 0.4, color: STONE, alpha: 0.95 });
      fx(u.dim, "rpg:shock_ring", { x: s.x, y: s.y + 0.1, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius / 2.1, life: 0.4, color: STONE, alpha: 0.9 });
      play(u.dim, "random.explode", s, 1.2, 0.8);
      play(u.dim, "dig.stone", s, 0.6, 1);
    }
    for (const p of fighters(u)) {
      if (!b.spots.some((s) => hdist(p.location, s) <= d.radius + 0.3 && Math.abs(p.location.y - s.y) < 3)) continue;
      bossHit(u, p, d.damage, { cause: EntityDamageCause.entityExplosion, effect: { id: "slowness", ticks: d.slow, amp: 1 } });
    }
  }
}

// ------------------------------------------------------------ 대지의 파동
function tremor(u, now) {
  const d = u.def.tremor;
  const api = u.api;
  const m = u.mem;
  const len = d.first + d.gap * (d.waves - 1) + 20;
  m.bigUntil = now + len + 30;
  api.hold(u, true);
  api.anim(u, "stomp");
  sound(u, "mob.ravager.roar", u.boss.location, 0.5, 1);
  title(u, "§6§l대지의 파동", "§e고리가 발밑을 지날 때 점프!", fighters(u));
  explain(u, "tremor", `§6[보스] 대지의 파동: §f바위 전갈이 ${d.waves}번 내려찍으며 땅이 고리 모양으로 퍼집니다. §e고리가 지나가는 순간 땅에 있으면 맞습니다 (점프로 피함)`);
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let i = 0; i < d.waves; i++) steps.push([d.first + i * d.gap, (x) => startWave(x)]);
  steps.push([len, (x) => api.hold(x, false)]);
  api.cast(u, "tremor", len + 2, steps);
}

function startWave(u) {
  const d = u.def.tremor;
  const l = u.boss.location;
  const o = floorAt(u, l.x, l.z);
  // 필드 끝(성문 자리)까지 닿는 반지름
  const maxR = hdist(o, u.center) + u.enc.def.arena.radius + 3;
  fx(u.dim, "rpg:tele_wave", { x: o.x, y: o.y + 0.08, z: o.z }, { dir: { x: 0, y: 0, z: 0 }, speed: d.speed, size: 0.7, life: maxR / d.speed, color: DUST, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: o.x, y: o.y + 0.1, z: o.z }, { dir: { x: 0, y: 0, z: 0 }, size: 1.6, life: 0.4, color: STONE, alpha: 0.9 });
  fx(u.dim, "rpg:bash_burst", { x: o.x, y: o.y + 0.6, z: o.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.6, life: 0.4, color: STONE, alpha: 0.9 });
  play(u.dim, "random.explode", o, 0.5, 1);
  play(u.dim, "dig.stone", o, 0.5, 1);
  u.mem.waves.push({ u, o, t0: system.currentTick, maxR, hit: new Set() });
}

/** 파동: 고리가 이번 틱에 지나간 거리에 있고 땅에 서 있으면 맞음 (파동마다 한 번) */
function waveTick(enc, now) {
  const m = enc.mem;
  if (!m.waves.length) return;
  const list = fighters(enc);
  m.waves = m.waves.filter((w) => {
    const u = w.u;
    const d = u.def.tremor;
    if (!u.alive) return false;
    const age = now - w.t0;
    const r1 = (d.speed * age) / 20;
    const r0 = (d.speed * Math.max(0, age - 1)) / 20;
    if (r0 > w.maxR) return false;
    for (const p of list) {
      if (w.hit.has(p.id)) continue;
      const k = hdist(p.location, w.o);
      if (k < r0 - 0.35 || k > r1 + 0.35 || Math.abs(p.location.y - w.o.y) > 3) continue;
      if (airborne(p, w.o.y)) continue;
      w.hit.add(p.id);
      const L = k || 1;
      bossHit(u, p, d.damage, { knock: { x: ((p.location.x - w.o.x) / L) * 0.3, z: ((p.location.z - w.o.z) / L) * 0.3, h: 0.35 } });
      fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 0.4, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.0, life: 0.3, color: STONE, alpha: 0.9 });
    }
    return true;
  });
}

// ------------------------------------------------------------ 석화의 포효
function roar(u, now) {
  const d = u.def.roar;
  const api = u.api;
  const m = u.mem;
  // 석화가 풀릴 때까지 큰 패턴 없음 (움직이지도 뛰지도 못하는 동안 파동·모래 잠행이 오면 피할 수 없으므로)
  m.bigUntil = now + d.windup + d.hold + 10;
  api.hold(u, true);
  api.anim(u, "roar");
  sound(u, "mob.ravager.roar", u.boss.location, 0.6, 1);
  title(u, "§6§l석화의 포효", "§e바위 전갈에게서 눈을 돌리세요!", fighters(u));
  explain(u, "roar", `§6[보스] 석화의 포효: §f울부짖음이 끝날 때 §e바위 전갈 쪽을 보고 있으면 ${Math.round(d.hold / 20)}초 석화§f(움직임·스킬 봉인)됩니다. 등을 돌리세요`);
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let t = 0; t < d.windup; t += 10) steps.push([t, (x) => roarCharge(x, t / d.windup)]);
  for (let k = 2; k >= 1; k--) steps.push([d.windup - k * 20, (x) => faceAll(x, `§6§l${k}`, { dist: 2.4, up: 0.5 }, fighters(x))]);
  steps.push([d.windup, (x) => roarRelease(x)]);
  steps.push([d.windup + 24, (x) => api.hold(x, false)]);
  api.cast(u, "roar", d.windup + 26, steps);
}

/** 모이는 고리 + 발밑 마법진 (k = 0~1 진행) */
function roarCharge(u, k) {
  const l = u.boss.location;
  fx(u.dim, "rpg:shock_ring", { x: l.x, y: u.center.y + 0.1, z: l.z }, { dir: { x: 0, y: 0, z: 0 }, size: Math.max(0.6, 5 - k * 4.4), life: 0.5, color: GRAY, alpha: 0.9 });
  fx(u.dim, "rpg:skill_rune", { x: l.x, y: u.center.y + 0.12, z: l.z }, { size: 3.2, life: 0.55, spin: k * 180, speed: 40, grow: 0, progress: k, color: STONE, alpha: 0.9 });
}

/** 울부짖음 끝: 바위 전갈 쪽을 보던 사람은 석화 (속박 + 침묵) */
function roarRelease(u) {
  const d = u.def.roar;
  const m = u.mem;
  const head = mid(u);
  const cosMax = Math.cos(((d.cone / 2) * Math.PI) / 180);
  fx(u.dim, "rpg:shock_ring", { x: head.x, y: u.center.y + 0.2, z: head.z }, { dir: { x: 0, y: 0, z: 0 }, size: 8, life: 0.6, color: GRAY, alpha: 0.9 });
  fx(u.dim, "rpg:bash_burst", { x: head.x, y: head.y, z: head.z }, { dir: { x: 0, y: 0, z: 1 }, size: 4, life: 0.5, color: GRAY, alpha: 0.9 });
  sound(u, "mob.warden.roar", head, 0.8, 1);
  const stoned = [];
  for (const p of fighters(u)) {
    let eye;
    let v;
    try {
      eye = p.getHeadLocation();
      v = p.getViewDirection();
    } catch {
      continue;
    }
    const to = { x: head.x - eye.x, y: head.y - eye.y, z: head.z - eye.z };
    const L = Math.hypot(to.x, to.y, to.z);
    if (L > d.range) continue;
    const vl = Math.hypot(v.x, v.y, v.z) || 1;
    const cos = L < 0.5 ? 1 : (v.x * to.x + v.y * to.y + v.z * to.z) / (L * vl);
    if (cos < cosMax) continue;
    root(p, d.hold, "석화");
    silence(p, d.hold);
    m.stone.set(p.id, { p, until: system.currentTick + d.hold });
    fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.6, life: 0.45, color: GRAY, alpha: 0.95 });
    play(u.dim, "mob.elderguardian.curse", p.location, 1, 0.7);
    stoned.push(p.name);
  }
  if (stoned.length) say(u, `§7[보스] 석화: §f${stoned.join(", ")}`);
}

/** 석화된 사람: 몸에 돌 조각 (끝나면 뺌) */
function stoneTick(enc, now) {
  const m = enc.mem;
  for (const [id, s] of m.stone) {
    if (now >= s.until || !s.p.isValid) {
      m.stone.delete(id);
      continue;
    }
    if (now % 8 === 0) fx(enc.dim, "rpg:bash_burst", { x: s.p.location.x, y: s.p.location.y + 0.9, z: s.p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.0, life: 0.4, color: GRAY, alpha: 0.9 });
  }
}

// ============================================================ 함께
/** 공생: 둘이 가까우면 금빛 끈 + 받는 피해 감소 (처음 이어질 때 알림) */
function bondTick(enc, now) {
  const m = enc.mem;
  const a = unitOf(enc, DESERT);
  const b = unitOf(enc, ROCK);
  const S = enc.def.symbiosis;
  const was = m.bond;
  m.bond = !!(a?.alive && b?.alive && a.boss?.isValid && b.boss?.isValid && hdist(a.boss.location, b.boss.location) <= S.distance);
  if (!m.bond) return;
  if (!was) {
    if (!m.told.has("bond")) title(enc, "§e§l공생", `§f두 전갈이 가까우면 받는 피해 -${Math.round(S.cut * 100)}% §7— 떨어뜨리세요!`, fighters(enc));
    explain(a, "bond", `§e[보스] 공생: §f두 전갈이 ${S.distance}칸 안에 있으면 금빛 끈으로 이어져 둘 다 받는 피해가 ${Math.round(S.cut * 100)}% 줄어듭니다. §e파티를 나눠 서쪽(모래)과 동쪽(바위)으로 떨어뜨리세요`);
    play(enc.dim, "mob.evocation_illager.cast_spell", enc.center, 1.4, 0.6);
  }
  // 끈: 0.3초마다 새로 (수명 0.5초 → 끊기지 않고 보임), 두 몸에 금빛 반짝임
  if (now % 6 === 0) beam(enc.dim, mid(a), mid(b), GOLD, { life: 0.5, half: 0.3, thick: 0.24, gap: 0.55 });
  if (now % 12 === 0) for (const x of [a, b]) fx(enc.dim, "rpg:bash_burst", mid(x), { dir: { x: 0, y: 0, z: 1 }, size: 1.3, life: 0.35, color: GOLD, alpha: 0.8 });
}

/** 분노한 마리: 등에서 붉은 기운 */
function rageTick(enc, now) {
  const o = enc.mem.enraged;
  if (!o?.alive || !o.boss?.isValid || now % 16 !== 0) return;
  const p = mid(o);
  fx(enc.dim, "rpg:bash_burst", { x: p.x, y: p.y + 0.8, z: p.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.4, life: 0.4, color: RAGE, alpha: 0.85 });
}

function endBurrow(enc) {
  const b = enc.mem.burrow;
  if (!b) return;
  enc.mem.burrow = undefined;
  b.u.immune = false;
}

// ------------------------------------------------------------ 모듈
export default {
  key: "scorpions",
  propIds: [],
  /** 원거리 견제 (v2.3.0, 바위 전갈): 꼬리 끝에서 바위 조각을 날림 */
  farFrom(u) {
    return tailTip(u);
  },
  /** 맹독 표식 동안은 쉼 (표식이 붙은 사람 곁으로 모여야 함) */
  farOk(u) {
    return !u.mem.venom;
  },
  start(enc) {
    const now = system.currentTick;
    const dd = unitOf(enc, DESERT).def;
    const rd = unitOf(enc, ROCK).def;
    enc.mem = {
      next: { sting: now + 100, burrow: now + 260, venom: now + dd.venom.first, boulders: now + 140, tremor: now + 360, roar: now + rd.roar.first },
      bigUntil: now + 120,
      bond: false,
      bondTold: new Map(),
      told: new Set(),
      burrow: undefined,
      venom: undefined,
      boulders: [],
      waves: [],
      stone: new Map(),
      enraged: undefined,
    };
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    const r = u.api.ratio(u);
    const big = now >= m.bigUntil;
    if (u.key === DESERT) {
      if (big && r <= d.venom.from && !m.venom && now >= m.next.venom && list.length > 0) {
        m.next.venom = now + Math.round(d.venom.every * (u.cdMult ?? 1));
        venom(u, now, list);
        return true;
      }
      if (big && now >= m.next.burrow) {
        const t = burrowTarget(u, list);
        if (t) {
          m.next.burrow = now + every(u, d.burrow.every);
          burrow(u, now, t);
          return true;
        }
      }
      if (now >= m.next.sting) {
        const t = stingTarget(u, list);
        if (t) {
          m.next.sting = now + every(u, d.sting.every);
          sting(u, now, t);
          return true;
        }
      }
      return false;
    }
    if (big && r <= d.roar.from && now >= m.next.roar && list.length > 0) {
      m.next.roar = now + Math.round(d.roar.every * (u.cdMult ?? 1));
      roar(u, now);
      return true;
    }
    if (big && now >= m.next.tremor) {
      m.next.tremor = now + every(u, d.tremor.every);
      tremor(u, now);
      return true;
    }
    if (now >= m.next.boulders && list.length > 0) {
      m.next.boulders = now + every(u, d.boulders.every);
      boulders(u, now, list);
      return true;
    }
    return false;
  },
  /** 받는 피해: 공생이면 줄어듦 (때린 사람에게 가끔 알림) */
  hurt(u, p, amount) {
    const m = u.mem;
    if (!m.bond || !u.api.other(u)) return amount;
    const now = system.currentTick;
    const cut = u.enc.def.symbiosis.cut;
    if (now - (m.bondTold.get(p.id) ?? -100) >= 30) {
      m.bondTold.set(p.id, now);
      showFaceText(p, `§e공생 §7피해 -${Math.round(cut * 100)}%`, { dist: 2.4, side: -0.5, up: 0.25 });
    }
    return amount * (1 - cut);
  },
  tick(enc, now) {
    bondTick(enc, now);
    burrowTick(enc, now);
    venomTick(enc, now);
    boulderTick(enc, now);
    waveTick(enc, now);
    stoneTick(enc, now);
    rageTick(enc, now);
  },
  /** 한 마리가 쓰러짐: 그 마리의 패턴을 거두고, 남은 쪽은 짝 잃은 분노 */
  unitDied(enc, u) {
    const m = enc.mem;
    const now = system.currentTick;
    if (u.key === DESERT) {
      endBurrow(enc);
      m.venom = undefined;
    } else {
      m.boulders = m.boulders.filter((b) => b.u !== u);
      m.waves = m.waves.filter((w) => w.u !== u);
    }
    m.bond = false;
    const o = u.api.other(u);
    if (!o) return;
    const E = enc.def.enrage;
    o.dmgMult = E.damage;
    o.cdMult = E.cd;
    u.api.setFast(o);
    // 이미 잡혀 있던 다음 패턴 시각도 당김
    for (const k of Object.keys(m.next)) if (m.next[k] > now) m.next[k] = now + Math.round((m.next[k] - now) * E.cd);
    m.enraged = o;
    // 계속 옅게 붉은 몸 (렌더 컨트롤러: rpg:rage)
    try {
      o.boss.setProperty("rpg:rage", true);
    } catch {
      // 무시
    }
    // 땅속에 있으면 (모래 잠행) 동작을 틀지 않음: 가라앉은 모습이 풀려 땅 위로 보이지 않게
    if (m.burrow?.u !== o) u.api.anim(o, "rage");
    title(o, "§4§l짝 잃은 분노", `§c${o.def.name}: 피해 +${Math.round((E.damage - 1) * 100)}% · 패턴이 빨라짐`);
    sound(o, "mob.ravager.roar", o.boss?.location, 0.7, 1);
    fx(enc.dim, "rpg:shock_ring", { x: o.boss.location.x, y: enc.center.y + 0.1, z: o.boss.location.z }, { dir: { x: 0, y: 0, z: 0 }, size: 4, life: 0.6, color: RAGE, alpha: 0.9 });
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.next) return;
    endBurrow(enc);
    m.venom = undefined;
    m.boulders = [];
    m.waves = [];
    m.stone?.clear();
  },
};
