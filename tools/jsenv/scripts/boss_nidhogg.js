import { EntityDamageCause, system } from "@minecraft/server";
import { notify, play, showFaceText } from "./fx.js";
import { beam, fx, shake } from "./skills.js";
import { kill, root, silence } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fanMarks, fighters, groundUnder, hdist, inFan, meleeTarget, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 니드호그 (v2.3.0, Lv.38): 수치는 config.js BOSSES.nidhogg
 * 세계수의 뿌리 방에서 싸우는 흑룡. 움직이지 않는 보스(중력·충돌 없음)를 모듈이 매 틱 옮김 (엔진 pose): 땅에서는 가장 가까운 사람 쪽으로 몸을 돌려 걷고,
 * 비상 때는 날아올라 필드 위를 돌다가 급강하. 몸(엔티티 자리) = 몸통 가운데, 머리는 몸 앞 drive.head 칸.
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작을 섞음), rpg:fly (0~1 날기 → fly 동작을 섞음, 쉬기 동작을 뺌)
 * - 물어뜯기 (평타): 몸 앞 부채꼴 (대상 쪽으로 몸을 돌리며) · 꼬리 후려치기: 등 뒤 부채꼴 (옆·뒤로 밀림)
 * - 독의 숨결: 앞쪽 한 사람을 가운데로 sweep° 를 한쪽에서 반대쪽으로 쓸어 가는 독 불길 (머리 앞 부채꼴이 돎, 맞으면 시듦). 쓸어 갈 곳 전체를 먼저 보여 줌
 * - 날갯짓 폭풍 (침묵): 날개를 펴고 바람 → 날개 밑(inner 칸 안)과 먼 곳(outer 칸 밖)은 안전, 그 사이는 피해 + 침묵 + 바깥으로 날아감 (도넛)
 * - 뿌리의 올가미 (속박): 멀리 있는 사람부터 발밑 원 → 세계수 뿌리가 솟아 피해 + 속박 (뿌리는 속박 동안 남음)
 * - 비상 (체력 75%·40%): 날아올라 필드 위를 돌며 독의 비 (사람마다 발밑 원) 세 번 → 가장 많이 모인 곳에 급강하 (피해 + 띄움) → 내려앉음
 * - 독룡의 낙인 (체력 60% 아래, 즉사): 싸우는 사람 모두에게 낙인 (원이 따라다님, 다른 사람이 안에 들면 붉게) → 터질 때 원 둘 이상에 든 사람은 즉사, 하나면 피해.
 *   니드호그는 낙인 동안에도 싸움 (물어뜯기·꼬리·숨결), 폭풍·올가미·비상은 없음
 * - 높은 곳 (뿌리·담장·탑 위): 날개로 쳐서 떨어뜨림
 * 큰 패턴(비상·낙인)은 서로 겹치지 않음 (mem.bigUntil). 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기를 모듈이)
 */

const VENOM = [0.62, 0.95, 0.3];
const PURPLE = [0.74, 0.3, 0.96];
const RED = [1.0, 0.22, 0.14];
const ORANGE = [1.0, 0.5, 0.14];
const SAFE = [0.86, 1.0, 0.9];
const BARK = [0.72, 0.52, 0.34];
const DUST = [0.52, 0.46, 0.42];
const DARK = [0.62, 0.08, 0.2];
const WIND = [0.86, 0.84, 1.0];
const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

/** 세계수 뿌리 소품 (뿌리의 올가미): 솟아오름(rise)은 나올 때 저절로 (리소스 팩 animate), 가라앉음(sink)은 스크립트가 */
export const ROOTS_ID = "rpg:prop_nid_roots";
const ROOTS_ANIM = "animation.rpg.prop.nid_roots";
/** 떨어지는 독 방울의 중력 (칸/초², build.py NID_DROP_GRAVITY 와 같음) */
export const DROP_GRAVITY = 30;
/** 독 방울이 떨어지는 시간 (틱): 닿기 DROP_TICKS 틱 전에 DROP_HEIGHT 칸 위에서 멈춘 채 떨어지기 시작 → 닿을 때 바닥 */
export const DROP_TICKS = 16;
export const DROP_HEIGHT = 0.5 * DROP_GRAVITY * (DROP_TICKS / 20) ** 2;
/** 동작 틀: 급강하는 내려앉기가 이어받을 때까지 유지 */
const AIR = "rpg_air";

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
const dirOf = (a) => ({ x: Math.cos(a), y: 0, z: Math.sin(a) });
/** 각도 → 0 ~ 2π */
const wrap = (a) => ((a % TAU) + TAU) % TAU;
/** 각도 차이 → -π ~ π */
const wrapPi = (a) => {
  const w = wrap(a);
  return w > Math.PI ? w - TAU : w;
};
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
/** cur 에서 want 쪽으로 step 까지 돎 */
function turnToward(cur, want, step) {
  const d = wrapPi(want - cur);
  return Math.abs(d) <= step ? want : cur + Math.sign(d) * step;
}

function shuffle(list) {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
function explain(u, id, text) {
  const told = u.mem.told;
  if (told.has(id)) return;
  told.add(id);
  say(u, text);
}

function shakeNear(u, at, range, intensity, seconds) {
  for (const p of fighters(u)) if (hdist(p.location, at) <= range) shake(p, intensity, seconds);
}

/** 흙먼지 (rpg:dust) */
function dust(dim, at, size, color = DUST, life = 0.8) {
  fx(dim, "rpg:dust", { x: at.x, y: at.y + size * 0.35, z: at.z }, { size, life, color, alpha: 0.8 });
}

/**
 * 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖·담장 탑 위로 날아가지 않게)
 * @returns {{x: number, z: number}}
 */
function keepIn(u, p, kx, kz) {
  const c = u.center;
  const rx = p.location.x - c.x;
  const rz = p.location.z - c.z;
  const r = Math.hypot(rx, rz);
  if (r < u.enc.def.arena.radius - 5 || r < 1e-6) return { x: kx, z: kz };
  const ux = rx / r;
  const uz = rz / r;
  const out = kx * ux + kz * uz;
  if (out <= 0) return { x: kx, z: kz };
  return { x: kx - ux * (out + 0.15), z: kz - uz * (out + 0.15) };
}

/** 공격이 닿는 사람 */
function targets(u) {
  return fighters(u);
}

// ------------------------------------------------------------ 몸
/** 몸 (엔티티 자리): 바닥 높이 + 나는 높이 h */
export function bodyOf(u) {
  const b = u.mem.body;
  return { x: b.x, y: u.center.y + b.h, z: b.z };
}

/** 머리 밑 바닥 (몸 앞 drive.head 칸, yaw = 몸 방향) */
export function headGround(u, yaw = u.mem.body.yaw) {
  const b = u.mem.body;
  const H = u.def.drive.head;
  return { x: b.x + Math.cos(yaw) * H, y: u.center.y, z: b.z + Math.sin(yaw) * H };
}

/** 머리 (연출용) */
function headAt(u) {
  const g = headGround(u);
  return { x: g.x, y: u.center.y + u.mem.body.h + 4.4, z: g.z };
}

/** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 teleport 하지 않게. 밀려난 것은 엔진이 10틱마다 되돌림) */
function place(u) {
  const b = u.mem.body;
  const key = `${b.x},${b.z},${b.h},${b.yaw}`;
  if (b.placed === key && u.boss?.isValid) return;
  b.placed = key;
  u.api.pose(u, { x: b.x, y: u.center.y + b.h, z: b.z }, b.yaw);
}

/** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게. 시험·미리보기도 씀 */
export function setBody(u, x, z, yaw) {
  const b = u.mem.body;
  const c = u.center;
  const K = u.def.drive.keep;
  const r = Math.hypot(x - c.x, z - c.z);
  b.x = r > K ? c.x + ((x - c.x) / r) * K : x;
  b.z = r > K ? c.z + ((z - c.z) / r) * K : z;
  b.yaw = yaw;
  place(u);
}

/** 속성 보내기 (값이 바뀔 때만: 조금 바뀐 것은 보내지 않음) */
function setProp(u, id, v, last) {
  if (last !== undefined && Math.abs(v - last) < (id === "rpg:fly" ? 0.02 : 0.05)) return last;
  try {
    u.boss.setProperty(id, v);
  } catch {
    return last;
  }
  return v;
}

/** 매 틱: 걷는 빠르기·날기를 목표 쪽으로 (날기 1초에 걸쳐), 속성 보내기 */
function bodyTick(u) {
  const b = u.mem.body;
  const gs = 0.15;
  b.gait = Math.abs(b.gaitTo - b.gait) <= gs ? b.gaitTo : b.gait + Math.sign(b.gaitTo - b.gait) * gs;
  const fs = 0.05;
  b.fly = Math.abs(b.flyTo - b.fly) <= fs ? b.flyTo : b.fly + Math.sign(b.flyTo - b.fly) * fs;
  b.gaitSent = setProp(u, "rpg:gait", Math.round(clamp(b.gait, 0, 1) * 100) / 100, b.gaitSent);
  b.flySent = setProp(u, "rpg:fly", Math.round(clamp(b.fly, 0, 1) * 100) / 100, b.flySent);
}

/** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음) */
function walkTick(u, list) {
  const D = u.def.drive;
  const b = u.mem.body;
  let tgt;
  let best = Infinity;
  for (const p of list) {
    const d = hdist(p.location, b);
    if (d < best) {
      best = d;
      tgt = p;
    }
  }
  b.gaitTo = 0;
  if (tgt) {
    const want = angOf(dirTo(b, tgt.location));
    b.yaw = turnToward(b.yaw, want, D.turn * DEG);
    if (best > D.near && Math.abs(wrapPi(want - b.yaw)) < 50 * DEG) {
      const c = u.center;
      let nx = b.x + Math.cos(b.yaw) * D.walk;
      let nz = b.z + Math.sin(b.yaw) * D.walk;
      const r = Math.hypot(nx - c.x, nz - c.z);
      if (r > D.keep) {
        nx = c.x + ((nx - c.x) / r) * D.keep;
        nz = c.z + ((nz - c.z) / r) * D.keep;
      }
      if (Math.hypot(nx - b.x, nz - b.z) > 0.02) {
        b.x = nx;
        b.z = nz;
        b.gaitTo = 1;
      }
    }
  }
  place(u);
  // 발소리
  if (b.gaitTo > 0 && system.currentTick % 12 === 0) play(u.dim, "mob.ravager.step", bodyOf(u), 0.5, 0.8);
}

/** 패턴 동안: 원하는 쪽(body.want)이 있으면 그쪽으로 몸을 돌림 (제자리) */
function standTick(u) {
  const b = u.mem.body;
  b.gaitTo = 0;
  if (b.want !== undefined) b.yaw = turnToward(b.yaw, b.want, u.def.drive.turn * DEG);
  place(u);
}

/** p 가 몸 방향에서 몇 도 떨어져 있는지 (라디안, 0 ~ π) */
function offAngle(u, l) {
  const b = u.mem.body;
  return Math.abs(wrapPi(angOf(dirTo(b, l)) - b.yaw));
}

// ------------------------------------------------------------ 물어뜯기 (평타)
function bite(u, now, list) {
  const m = u.def.melee;
  const b = bodyOf(u);
  const { half } = bossSize(u.def);
  const reach = half + m.reach;
  // 물 수 있는 사람 가운데 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람)
  const t = meleeTarget(u, list.filter((p) => hdist(p.location, b) <= reach && Math.abs(p.location.y - u.center.y) <= 4 && offAngle(u, p.location) <= (m.cone / 2 + 25) * DEG), b);
  if (!t) return false;
  const dir = dirTo(b, t.location);
  const apex = floorAt(u, b.x, b.z);
  u.mem.body.want = angOf(dir);
  fanMarks(u.dim, apex, dir, Math.round(reach), m.cone, m.windup, RED);
  u.api.anim(u, m.anim);
  sound(u, u.def.sounds.swing, headAt(u), 0.8, 1);
  u.api.cast(u, "bite", m.windup + 6, [[m.windup, (x) => biteHit(x, apex, dir, reach)]]);
  return true;
}

function biteHit(u, apex, dir, radius) {
  const m = u.def.melee;
  for (const p of targets(u)) {
    if (Math.abs(p.location.y - apex.y) > 4 || !inFan(p.location, apex, dir, radius, m.cone, 0.5)) continue;
    const kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
    bossHit(u, p, m.damage, { knock: { x: kk.x, z: kk.z, h: 0.35 }, directional: true });
  }
  sound(u, u.def.sounds.hit, headAt(u), 0.9, 1);
  const h = headAt(u);
  fx(u.dim, "rpg:slash_heavy_r_glow", { x: h.x, y: h.y - 2.4, z: h.z }, { dir, speed: 0, spin: 0, size: 3.2, life: 0.25, color: u.def.color, alpha: 0.95 });
}

// ------------------------------------------------------------ 꼬리 후려치기 (등 뒤)
function tailWhip(u, now, list) {
  const d = u.def.tail;
  const b = bodyOf(u);
  const back = u.mem.body.yaw + Math.PI;
  const cand = list.filter((p) => hdist(p.location, b) <= d.reach && Math.abs(p.location.y - u.center.y) < 4 && Math.abs(wrapPi(angOf(dirTo(b, p.location)) - back)) <= (d.cone / 2) * DEG);
  if (!cand.length) return false;
  const dir = dirOf(back);
  const apex = floorAt(u, b.x, b.z);
  u.mem.body.want = undefined;
  fanMarks(u.dim, apex, dir, Math.round(d.reach), d.cone, d.windup, ORANGE, 1.5);
  u.api.anim(u, "tail");
  sound(u, "mob.enderdragon.flap", b, 0.7, 1);
  explain(u, "tail", "§6[보스] 꼬리 후려치기: §f니드호그 등 뒤(주황 부채꼴)에 있으면 꼬리에 맞아 옆으로 날아갑니다");
  u.api.cast(u, "tail", d.windup + 8, [[d.windup, (x) => tailHit(x, apex, dir)]]);
  return true;
}

function tailHit(u, apex, dir) {
  const d = u.def.tail;
  // 꼬리가 도는 쪽 (tail 동작: 먼저 한쪽으로 감았다가 반대쪽으로 휘두름) → 옆으로 + 조금 뒤로
  const side = { x: -dir.z, z: dir.x };
  for (const p of targets(u)) {
    if (Math.abs(p.location.y - apex.y) > 4 || !inFan(p.location, apex, dir, d.reach, d.cone, 0.5)) continue;
    const lat = (p.location.x - apex.x) * side.x + (p.location.z - apex.z) * side.z;
    const sg = lat >= 0 ? 1 : -1;
    const kk = keepIn(u, p, (side.x * sg + dir.x * 0.4) * d.knock, (side.z * sg + dir.z * 0.4) * d.knock);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.4 } });
  }
  play(u.dim, "mob.enderdragon.hit", apex, 0.8, 1);
  for (let i = 1; i <= 4; i++) dust(u.dim, floorAt(u, apex.x + dir.x * i * 2, apex.z + dir.z * i * 2), 1.6, DUST, 0.6);
}

// ------------------------------------------------------------ 독의 숨결 (쓸어 가는 부채꼴)
/** 숨결 부채꼴 (몸 방향 yaw 일 때): 머리 밑에서 yaw 쪽으로 range 칸, 폭 cone° */
function breathCone(u, yaw) {
  const d = u.def.breath;
  return { apex: headGround(u, yaw), dir: dirOf(yaw), range: d.range, cone: d.cone };
}

/** 쓸어 가는 동안 한 번이라도 불길에 드는 곳인지 (a0 → a1, 4° 간격으로) */
function inSweep(u, B, l) {
  const n = Math.max(2, Math.ceil(Math.abs(B.a1 - B.a0) / (4 * DEG)));
  for (let i = 0; i <= n; i++) {
    const c = breathCone(u, B.a0 + ((B.a1 - B.a0) * i) / n);
    if (inFan(l, c.apex, c.dir, c.range, c.cone, 0)) return true;
  }
  return false;
}

/** 예고: 쓸어 갈 곳 전체 (몸 둘레 극좌표 격자 중 불길이 닿는 곳), 불길이 시작하는 쪽 가장자리는 밝게 */
function breathMarks(u, B, ticks) {
  const d = u.def.breath;
  const b = u.mem.body;
  const c = u.center;
  const life = Math.max(0.1, ticks / 20);
  const step = 1.8;
  const R = u.def.drive.head + d.range;
  const lo = Math.min(B.a0, B.a1) - (d.cone / 2 + 8) * DEG;
  const hi = Math.max(B.a0, B.a1) + (d.cone / 2 + 8) * DEG;
  for (let r = step; r <= R + 0.01; r += step) {
    const n = Math.max(2, Math.ceil(((hi - lo) * r) / step));
    for (let i = 0; i <= n; i++) {
      const a = lo + ((hi - lo) * i) / n;
      const at = { x: b.x + Math.cos(a) * r, y: c.y, z: b.z + Math.sin(a) * r };
      if (!inSweep(u, B, at)) continue;
      const start = Math.abs(wrapPi(a - B.a0)) < 12 * DEG;
      fx(u.dim, "rpg:tele_fill", { x: at.x, y: c.y + 0.07, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: step * 0.8, life, color: PURPLE, alpha: start ? 0.8 : 0.42, grow: 1 });
    }
  }
}

function breath(u, now, list) {
  const d = u.def.breath;
  const b = u.mem.body;
  const B0 = bodyOf(u);
  const R = u.def.drive.head + d.range;
  const front = list.filter((p) => offAngle(u, p.location) <= 100 * DEG && hdist(p.location, B0) <= R && Math.abs(p.location.y - u.center.y) < 4);
  if (!front.length) return false;
  const t = shuffle(front)[0];
  const aim = angOf(dirTo(B0, t.location));
  const side = Math.random() < 0.5 ? 1 : -1;
  const half = (d.sweep * DEG) / 2;
  const B = { a0: aim - side * half, a1: aim + side * half, side };
  b.want = B.a0;
  breathMarks(u, B, d.windup + d.time);
  u.api.anim(u, "breath");
  sound(u, "mob.enderdragon.growl", headAt(u), 1.2, 0.8);
  explain(u, "breath", "§5[보스] 독의 숨결: §f보라 부채꼴을 밝은 쪽 끝에서 반대쪽 끝으로 쓸어 가며 독 불길을 뿜습니다. §e니드호그 뒤나 목 밑, 불길이 지나간 쪽으로 피하세요 §7(맞으면 시듦)");
  /** @type {[number, (x: any) => void][]} */
  const steps = [[d.windup, (x) => breathStart(x)]];
  for (let k = 0; k <= d.time; k++) steps.push([d.windup + k, (x) => breathStep(x, B, k)]);
  u.api.cast(u, "breath", d.windup + d.time + 8, steps);
  return true;
}

function breathStart(u) {
  play(u.dim, "mob.enderdragon.growl", headAt(u), 0.7, 1);
  shakeNear(u, bodyOf(u), 20, 0.12, 0.5);
}

function breathStep(u, B, k) {
  const d = u.def.breath;
  const b = u.mem.body;
  // 몸 방향만 정함 (엔티티는 다음 틱 standTick 이 옮김: 한 틱에 한 번만 teleport)
  b.want = undefined;
  b.yaw = B.a0 + ((B.a1 - B.a0) * k) / d.time;
  const c = breathCone(u, b.yaw);
  const h = headAt(u);
  // 불길: 머리에서 퍼져 나가는 독 구름 (부채꼴 폭 안에서 흩어짐)
  if (k % 2 === 0) {
    for (let i = 0; i < 3; i++) {
      const a = b.yaw + (Math.random() - 0.5) * d.cone * DEG;
      const dir = { x: Math.cos(a), y: -0.22, z: Math.sin(a) };
      fx(u.dim, "rpg:breath", { x: h.x - 0.6 + Math.cos(b.yaw) * 1.2, y: h.y - 1.6, z: h.z + Math.sin(b.yaw) * 1.2 }, { dir, speed: 11 + Math.random() * 4, spin: Math.random() * 360, size: 1.4 + Math.random() * 0.6, life: 1.3, color: i === 0 ? VENOM : PURPLE, alpha: 0.9 });
    }
  }
  if (k % 4 === 0) play(u.dim, "mob.blaze.shoot", h, 0.5, 0.6);
  // 불길 판정은 tick 틱마다 (한 사람이 불길 안에 있으면 tick 틱에 한 번 = 바닐라 무적 시간과 같게)
  if (k % d.tick !== 0) return;
  for (const p of targets(u)) {
    if (Math.abs(p.location.y - u.center.y) > 4 || !inFan(p.location, c.apex, c.dir, c.range, c.cone, 0.4)) continue;
    bossHit(u, p, d.damage, { directional: true, effect: { id: "wither", ticks: d.wither, amp: 0 } });
  }
}

// ------------------------------------------------------------ 날갯짓 폭풍 (도넛, 침묵)
function gustRingMarks(u, at, ticks) {
  const d = u.def.gust;
  const life = Math.max(0.1, ticks / 20);
  for (let r = d.inner + 1.2; r <= d.outer + 0.01; r += 2.4) {
    const n = Math.ceil((TAU * r) / 2.6);
    for (let i = 0; i < n; i++) {
      const a = (i / n) * TAU + r;
      fx(u.dim, "rpg:tele_fill", { x: at.x + Math.cos(a) * r, y: u.center.y + 0.07, z: at.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: 1.4, life, color: PURPLE, alpha: 0.4 });
    }
  }
  fx(u.dim, "rpg:tele_disc", { x: at.x, y: u.center.y + 0.06, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.outer, life, color: PURPLE, alpha: 0.9 });
}

function gust(u, now, list) {
  const d = u.def.gust;
  const b = bodyOf(u);
  if (!list.some((p) => ((r) => r > d.inner && r <= d.outer)(hdist(p.location, b)))) return false;
  const at = floorAt(u, b.x, b.z);
  u.mem.body.want = undefined;
  disc(u.dim, at, d.inner, d.windup, SAFE, { fill: false, alpha: 0.95 });
  gustRingMarks(u, at, d.windup);
  u.api.anim(u, "gust");
  sound(u, u.def.sounds.roar, headAt(u), 0.9, 1);
  play(u.dim, "mob.enderdragon.flap", b, 0.6, 1);
  faceAll(u, "§f§l날개 밑으로! §7또는 멀리", { dist: 2.4, up: 0.45 }, list.filter((p) => ((r) => r > d.inner && r <= d.outer)(hdist(p.location, b))));
  explain(u, "gust", `§f[보스] 날갯짓 폭풍: §f날개를 펴고 바람을 일으킵니다. §e날개 밑(초록 원, ${d.inner}칸 안)이나 먼 곳(${d.outer}칸 밖)은 안전§f, 그 사이에 있으면 날아가며 §5침묵§f(스킬·룬 봉인)`);
  u.api.cast(u, "gust", d.windup + 12, [[d.windup, (x) => gustHit(x, at)]]);
  return true;
}

function gustHit(u, at) {
  const d = u.def.gust;
  for (const p of targets(u)) {
    const r = hdist(p.location, at);
    if (r <= d.inner + 0.2 || r > d.outer + 0.3 || Math.abs(p.location.y - u.center.y) > 4) continue;
    const dir = dirTo(at, p.location);
    const kk = keepIn(u, p, dir.x * d.knock, dir.z * d.knock);
    const landed = bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.5 } });
    if (landed) {
      silence(p, d.silence);
      showFaceText(p, "§5§l침묵! §7바람에 숨이 막혔습니다", { dist: 2.2, up: 0.35 });
    }
  }
  fx(u.dim, "rpg:tele_wave", { x: at.x, y: at.y + 0.2, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 24, size: 1, life: d.outer / 24, color: WIND, alpha: 0.9 });
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * TAU;
    dust(u.dim, floorAt(u, at.x + Math.cos(a) * (d.inner + 1.5), at.z + Math.sin(a) * (d.inner + 1.5)), 2.2, WIND, 0.7);
  }
  play(u.dim, "mob.enderdragon.flap", at, 0.5, 1);
  shakeNear(u, at, d.outer + 4, 0.25, 0.6);
}

// ------------------------------------------------------------ 뿌리의 올가미 (속박)
function snare(u, now, list) {
  const d = u.def.snare;
  const b = bodyOf(u);
  const far = shuffle(list.filter((p) => hdist(p.location, b) > d.far));
  const near = shuffle(list.filter((p) => hdist(p.location, b) <= d.far));
  const chosen = [...far, ...near].slice(0, d.count);
  if (!chosen.length) return false;
  // 발밑 = 서 있는 바닥 (뿌리 위에 서 있으면 뿌리 위)
  const spots = chosen.map((p) => ({ x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z }));
  u.mem.body.want = undefined;
  for (const s of spots) {
    disc(u.dim, s, d.radius, d.windup, BARK);
    fx(u.dim, "rpg:ground_crack", { x: s.x, y: s.y + 0.05, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: d.radius * 2.2, life: d.windup / 20 + 0.4, color: [0.35, 0.22, 0.14], alpha: 0.85 });
  }
  u.api.anim(u, "gnaw");
  sound(u, "mob.ravager.bite", headAt(u), 0.6, 1);
  explain(u, "snare", "§6[보스] 뿌리의 올가미: §f니드호그가 뿌리를 갉으면 멀리 있는 사람 발밑(갈색 원)에서 세계수 뿌리가 솟아 §e속박§f합니다 — 원 밖으로");
  u.api.cast(u, "snare", d.windup + 8, [[d.windup, (x) => snareHit(x, spots)]]);
  return true;
}

function snareHit(u, spots) {
  const d = u.def.snare;
  const now = system.currentTick;
  const hit = new Set();
  for (const s of spots) {
    let e;
    try {
      e = u.dim.spawnEntity(ROOTS_ID, { x: s.x, y: s.y, z: s.z });
      u.api.addProp(u.enc, e);
      e.teleport({ x: s.x, y: s.y, z: s.z }, { facingLocation: { x: s.x + Math.random() - 0.5, y: s.y, z: s.z + 1 } });
    } catch {
      e = undefined;
    }
    if (e) u.mem.roots.push({ e, sinkAt: now + d.root + 4, dropAt: now + d.root + 16 });
    dust(u.dim, s, 2.4, BARK, 0.7);
    play(u.dim, "dig.wood", s, 0.6, 1);
    for (const p of targets(u)) {
      if (hit.has(p.id) || hdist(p.location, s) > d.radius + 0.3 || Math.abs(p.location.y - s.y) > 3) continue;
      hit.add(p.id);
      bossHit(u, p, d.damage, { noSource: true });
      root(p, d.root, "뿌리에 휘감김");
    }
  }
}

/** 뿌리 소품 동작: 가라앉음은 마지막 모습(땅속)을 사라질 때까지 유지 */
function rootsAnim(e, name) {
  try {
    if (e?.isValid) e.playAnimation(`${ROOTS_ANIM}.${name}`, { controller: "rpg_roots", stopExpression: "0" });
  } catch {
    // 표시만
  }
}

/** 뿌리 소품: 속박이 끝나면 가라앉고 사라짐 */
function rootsTick(enc, now) {
  const R = enc.mem.roots;
  for (let i = R.length - 1; i >= 0; i--) {
    const r = R[i];
    if (!r.e?.isValid) {
      R.splice(i, 1);
      continue;
    }
    if (now >= r.sinkAt && !r.sunk) {
      r.sunk = true;
      rootsAnim(r.e, "sink");
    }
    if (now >= r.dropAt) {
      enc.api.dropProp(enc, r.e);
      R.splice(i, 1);
    }
  }
}

// ------------------------------------------------------------ 비상 (날아오름 → 독의 비 → 급강하 → 내려앉음)
function takeoff(u, now) {
  const F = u.def.flight;
  const m = u.mem;
  const b = m.body;
  const c = u.center;
  const r = Math.hypot(b.x - c.x, b.z - c.z);
  m.flight = {
    stage: "rise",
    t0: now,
    x0: b.x,
    z0: b.z,
    ang: r > 0.5 ? Math.atan2(b.z - c.z, b.x - c.x) : b.yaw,
    dir: Math.random() < 0.5 ? 1 : -1,
    volleys: 0,
    nextVolley: now + F.rise + F.first,
    rains: [],
    target: undefined,
    diveAt: 0,
  };
  m.bigUntil = Infinity;
  b.flyTo = 1;
  b.gaitTo = 0;
  b.want = undefined;
  u.api.anim(u, "takeoff");
  sound(u, u.def.sounds.roar, headAt(u), 0.8, 1);
  play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.6, 1);
  const at = floorAt(u, b.x, b.z);
  fx(u.dim, "rpg:tele_wave", { x: at.x, y: at.y + 0.2, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 14, size: 1, life: 0.8, color: WIND, alpha: 0.85 });
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * TAU;
    dust(u.dim, floorAt(u, at.x + Math.cos(a) * 4, at.z + Math.sin(a) * 4), 2.6, DUST, 0.9);
  }
  title(u, "§5§l니드호그가 날아오릅니다", "§f독의 비를 피하고 §c한곳에 모여 있지 마세요", fighters(u));
  explain(u, "flight", "§5[보스] 비상: §f니드호그가 날아올라 필드 위를 돌며 독의 비(초록 원)를 세 번 떨어뜨리고, §c가장 많이 모인 곳§f으로 급강하합니다 §7— 날아 있는 동안 근접 공격은 닿지 않음 (활·완드·멀리 쏘는 스킬은 닿음)");
  u.api.cast(u, "flight", 100000, []);
}

/** 독의 비 한 번: 싸우는 사람마다 발밑 원 → windup 틱 뒤 떨어짐 */
function rainVolley(u, now) {
  const F = u.def.flight;
  const d = F.rain;
  const Fl = u.mem.flight;
  const h = headAt(u);
  for (const p of targets(u)) {
    // 서 있는 바닥에 (뿌리·담장 위에 올라가 있어도 비를 피하지 못함)
    const s = { x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z };
    disc(u.dim, s, d.radius, d.windup, VENOM);
    Fl.rains.push({ at: s, hitAt: now + d.windup, dropAt: now + Math.max(0, d.windup - DROP_TICKS) });
    beam(u.dim, h, { x: s.x, y: s.y + 0.5, z: s.z }, VENOM, { half: 0.3, thick: 0.1, life: 0.35, gap: 0.8 });
  }
  play(u.dim, "mob.enderdragon.growl", h, 1.4, 0.6);
}

/** 떨어지는 독: 떨어지기 시작 (방울) → 닿음 (원 안: 피해 + 독) */
function rainTick(u, now) {
  const d = u.def.flight.rain;
  const Fl = u.mem.flight;
  for (let i = Fl.rains.length - 1; i >= 0; i--) {
    const r = Fl.rains[i];
    if (now === r.dropAt) {
      // 독 방울 셋: 닿는 틱에 바닥에 닿게 (중력 DROP_GRAVITY 로 DROP_TICKS 틱 동안 DROP_HEIGHT 칸)
      const t = (r.hitAt - now) / 20;
      const h = 0.5 * DROP_GRAVITY * t * t;
      for (let k = 0; k < 3; k++) {
        fx(u.dim, "rpg:drop", { x: r.at.x + (Math.random() - 0.5) * 1.2, y: r.at.y + h, z: r.at.z + (Math.random() - 0.5) * 1.2 }, { dir: { x: 0, y: -1, z: 0 }, speed: 0, size: 0.9, life: t, color: VENOM, alpha: 0.95 });
      }
    }
    if (now < r.hitAt) continue;
    Fl.rains.splice(i, 1);
    fx(u.dim, "rpg:splash", { x: r.at.x, y: r.at.y + d.radius * 0.75, z: r.at.z }, { size: d.radius, life: 0.6, color: VENOM, alpha: 0.95 });
    dust(u.dim, r.at, 2, VENOM, 0.8);
    play(u.dim, "random.fizz", r.at, 0.8, 0.8);
    for (const p of targets(u)) {
      if (hdist(p.location, r.at) > d.radius + 0.3 || Math.abs(p.location.y - r.at.y) > 3) continue;
      bossHit(u, p, d.damage, { noSource: true, effect: { id: "poison", ticks: d.poison, amp: 0 } });
    }
  }
}

/** 급강하할 곳: 싸우는 사람 중 dive.radius 칸 안에 다른 사람이 가장 많은 사람 자리 (몸이 설 수 있게 keep 칸 안으로) */
export function diveSpot(u, list) {
  const R = u.def.flight.dive.radius;
  let best;
  let n = -1;
  for (const p of shuffle(list)) {
    const k = list.filter((q) => hdist(q.location, p.location) <= R).length;
    if (k > n) {
      n = k;
      best = p;
    }
  }
  const c = u.center;
  if (!best) return floorAt(u, c.x, c.z);
  const K = u.def.drive.keep;
  const x = best.location.x - c.x;
  const z = best.location.z - c.z;
  const r = Math.hypot(x, z);
  return r > K ? floorAt(u, c.x + (x / r) * K, c.z + (z / r) * K) : floorAt(u, best.location.x, best.location.z);
}

function flightTick(enc, now) {
  const Fl = enc.mem.flight;
  if (!Fl) return;
  const u = enc;
  const F = u.def.flight;
  const b = u.mem.body;
  const c = u.center;
  if (Fl.stage === "arrive") {
    // 등장: 하늘에서 내려앉음
    const k = clamp((now - Fl.t0) / Fl.time, 0, 1);
    b.h = Fl.h0 * (1 - k) * (1 - k);
    if (k >= 0.75) b.flyTo = 0;
    if (now === Fl.t0 + Math.round(Fl.time * 0.7)) u.api.anim(u, "land", undefined, { controller: AIR });
    place(u);
    if (k >= 1) {
      enc.mem.flight = undefined;
      dust(u.dim, floorAt(u, b.x, b.z), 4, DUST, 1);
      play(u.dim, "mob.ravager.step", bodyOf(u), 0.5, 1);
    }
    return;
  }
  if (Fl.stage === "rise") {
    const k = clamp((now - Fl.t0) / F.rise, 0, 1);
    const e = k * k * (3 - 2 * k);
    const tx = c.x + Math.cos(Fl.ang) * F.circle;
    const tz = c.z + Math.sin(Fl.ang) * F.circle;
    b.x = Fl.x0 + (tx - Fl.x0) * e;
    b.z = Fl.z0 + (tz - Fl.z0) * e;
    b.h = F.height * e;
    const tan = Fl.ang + (Fl.dir * Math.PI) / 2;
    b.yaw = turnToward(b.yaw, Math.hypot(tx - b.x, tz - b.z) > 1.5 ? Math.atan2(tz - b.z, tx - b.x) : tan, 6 * DEG);
    if (now % 8 === 0) play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.7, 0.8);
    if (k >= 1) Fl.stage = "circle";
    place(u);
    return;
  }
  if (Fl.stage === "circle") {
    Fl.ang = wrap(Fl.ang + (Fl.dir * F.speed) / F.circle);
    b.x = c.x + Math.cos(Fl.ang) * F.circle;
    b.z = c.z + Math.sin(Fl.ang) * F.circle;
    b.h = F.height;
    b.yaw = turnToward(b.yaw, Fl.ang + (Fl.dir * Math.PI) / 2, 6 * DEG);
    if (now % 10 === 0) play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.8, 0.7);
    if (Fl.volleys < F.volleys && now >= Fl.nextVolley) {
      rainVolley(u, now);
      Fl.volleys += 1;
      Fl.nextVolley = now + F.gap;
    }
    rainTick(u, now);
    if (Fl.volleys >= F.volleys && !Fl.rains.length && !Fl.target) {
      const D = F.dive;
      Fl.target = diveSpot(u, targets(u));
      Fl.diveAt = now + D.windup;
      disc(u.dim, Fl.target, D.radius, D.windup, DARK);
      fx(u.dim, "rpg:tele_disc", { x: Fl.target.x, y: Fl.target.y + 0.08, z: Fl.target.z }, { dir: { x: 0, y: 0, z: 0 }, size: D.radius + 0.4, life: D.windup / 20, color: RED, alpha: 0.95 });
      sound(u, u.def.sounds.roar, headAt(u), 0.7, 1);
      faceAll(u, "§4§l급강하! §f검붉은 원 밖으로", { dist: 2.4, up: 0.45 }, targets(u).filter((p) => hdist(p.location, Fl.target) <= D.radius + 3));
    }
    if (Fl.target && now >= Fl.diveAt - F.dive.time) {
      Fl.stage = "dive";
      Fl.d0 = now;
      Fl.from = { x: b.x, z: b.z, h: b.h };
      u.api.anim(u, "dive", undefined, { controller: AIR, stopExpression: "0" });
    }
    place(u);
    return;
  }
  if (Fl.stage === "dive") {
    const T = F.dive.time;
    const k = clamp((now - Fl.d0) / T, 0, 1);
    b.x = Fl.from.x + (Fl.target.x - Fl.from.x) * k;
    b.z = Fl.from.z + (Fl.target.z - Fl.from.z) * k;
    b.h = Fl.from.h * (1 - k);
    if (Math.hypot(Fl.target.x - Fl.from.x, Fl.target.z - Fl.from.z) > 0.5) b.yaw = turnToward(b.yaw, Math.atan2(Fl.target.z - Fl.from.z, Fl.target.x - Fl.from.x), 12 * DEG);
    place(u);
    if (k >= 1) {
      diveHit(u, Fl.target);
      Fl.stage = "land";
      Fl.landUntil = now + F.land;
      b.h = 0;
      b.flyTo = 0;
      u.api.anim(u, "land", undefined, { controller: AIR });
    }
    return;
  }
  if (Fl.stage === "land") {
    b.h = 0;
    place(u);
    if (now >= Fl.landUntil) {
      enc.mem.flight = undefined;
      enc.mem.bigUntil = now + 80;
      if (u.cast?.id === "flight") u.cast.until = now;
    }
  }
}

function diveHit(u, at) {
  const D = u.def.flight.dive;
  for (const p of targets(u)) {
    const r = hdist(p.location, at);
    // 큰 몸이 내려꽂힘: 바닥에서 6칸 위(뿌리 위)까지 닿음
    if (r > D.radius + 0.3 || p.location.y - at.y > 6 || p.location.y - at.y < -3) continue;
    const dir = dirTo(at, p.location);
    const kk = keepIn(u, p, dir.x * 0.9, dir.z * 0.9);
    bossHit(u, p, D.damage, { knock: { x: kk.x, z: kk.z, h: D.lift } });
  }
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: D.radius, life: 0.7, color: u.def.color, alpha: 0.95 });
  fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1.5, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 4, life: 0.5, color: DARK, alpha: 0.95 });
  fx(u.dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: D.radius * 2.2, life: 1.6, color: [0.3, 0.12, 0.16], alpha: 0.9 });
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * TAU;
    dust(u.dim, floorAt(u, at.x + Math.cos(a) * D.radius * 0.8, at.z + Math.sin(a) * D.radius * 0.8), 2.8, DUST, 1);
  }
  play(u.dim, "random.explode", at, 0.6, 1);
  play(u.dim, "mob.enderdragon.hit", at, 0.5, 1);
  shakeNear(u, at, 24, 0.45, 0.9);
}

// ------------------------------------------------------------ 독룡의 낙인 (즉사: 흩어지기)
function brand(u, now, list) {
  const d = u.def.brand;
  if (!list.length) return false;
  const marks = list.map((p) => ({ p, id: p.id, done: false }));
  u.mem.brand = { t0: now, until: now + d.time, marks };
  u.mem.bigUntil = now + d.time + 60;
  u.api.anim(u, "roar");
  sound(u, u.def.sounds.roar, headAt(u), 0.6, 1);
  play(u.dim, "mob.wither.spawn", u.center, 1.2, 0.5);
  for (const mk of marks) beam(u.dim, headAt(u), { x: mk.p.location.x, y: mk.p.location.y + 1.4, z: mk.p.location.z }, PURPLE, { half: 0.4, thick: 0.14, life: 0.6, gap: 0.6 });
  title(u, "§4§l독룡의 낙인", `§f서로 §c${d.radius}칸§f 넘게 흩어지세요! §7다른 사람의 원 안 = 즉사`, list);
  explain(u, "brand", `§4[보스] 독룡의 낙인: §f모두에게 낙인이 붙고 ${Math.round(d.time / 20)}초 뒤 터집니다 (반지름 ${d.radius}칸). §c다른 사람의 낙인 원 안에 있으면 즉사 §7— 서로 흩어지세요 (원이 붉게 보이면 위험). 원 하나만 맞으면 피해`);
  return true;
}

/** q 가 at 에서 터지는 낙인 원 안인지 (반지름 brand.radius, 위아래 4칸 안) */
function inBrand(u, q, at) {
  return hdist(q.location, at) <= u.def.brand.radius && Math.abs(q.location.y - at.y) < 4;
}

/** 지금 낙인이 남은 사람 */
function liveMarks(B) {
  return B.marks.filter((mk) => !mk.done && mk.p.isValid && okTarget(mk.p));
}

function brandTick(enc, now) {
  const B = enc.mem.brand;
  if (!B) return;
  const u = enc;
  const d = u.def.brand;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.brand = undefined;
    return;
  }
  // 쓰러짐·나감·다른 차원·필드에서 64칸 넘게 멀어짐: 낙인도 사라짐
  for (const mk of B.marks) if (!mk.done && (!mk.p.isValid || !okTarget(mk.p) || mk.p.dimension?.id !== u.dim.id || hdist(mk.p.location, u.center) > 64)) mk.done = true;
  const live = liveMarks(B);
  if (now < B.until) {
    // 표시: 발밑 원 (다른 사람이 안에 있으면 붉게), 머리 위 문양, 남은 초
    if ((now - B.t0) % 4 === 0) {
      for (const mk of live) {
        const l = mk.p.location;
        const bad = targets(u).some((q) => q.id !== mk.id && inBrand(u, q, l));
        disc(u.dim, { x: l.x, y: l.y, z: l.z }, d.radius, 5, bad ? RED : PURPLE, { fill: false, alpha: 0.95 });
      }
    }
    if ((now - B.t0) % 20 === 0) for (const mk of live) fx(u.dim, "rpg:skill_rune", { x: mk.p.location.x, y: mk.p.location.y + 2.6, z: mk.p.location.z }, { dir: { x: 0, y: 1, z: 0 }, size: 1.2, life: 1.0, color: PURPLE, alpha: 0.9 });
    const left = B.until - now;
    if (left % 20 === 0 && left <= 60) for (const mk of live) showFaceText(mk.p, `§4§l낙인 ${left / 20}`, { dist: 2.4, up: 0.45 });
    if ((now - B.t0) % 10 === 0) {
      for (const mk of live) {
        const near = targets(u).filter((q) => q.id !== mk.id && inBrand(u, q, mk.p.location)).length;
        notify(mk.p, near ? `§4§l독룡의 낙인 §c— 원 안에 ${near}명! 흩어지세요` : `§5독룡의 낙인 §7(${Math.ceil(left / 20)}초) §a원 안에 아무도 없음`, 15);
      }
    }
    return;
  }
  // 터짐: 낙인마다 원 → 원 둘 이상에 든 사람 즉사, 하나면 마법 피해
  enc.mem.brand = undefined;
  enc.mem.bigUntil = Math.min(enc.mem.bigUntil, now + 60);
  const blasts = live.map((mk) => ({ x: mk.p.location.x, y: mk.p.location.y, z: mk.p.location.z }));
  for (const at of blasts) {
    fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1.2, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 3, life: 0.5, color: PURPLE, alpha: 0.95 });
    fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: d.radius, life: 0.6, color: PURPLE, alpha: 0.9 });
    play(u.dim, "random.explode", at, 1.2, 0.6);
  }
  // 필드 안의 사람 + 낙인이 남은 사람 (필드 밖으로 달아나도 제 낙인은 터짐)
  const who = new Map(targets(u).map((p) => [p.id, p]));
  for (const mk of live) who.set(mk.id, mk.p);
  for (const p of who.values()) {
    const n = blasts.filter((at) => inBrand(u, p, at)).length;
    if (n >= 2) {
      say(u, `§4[보스] §e${p.name}§f님이 독룡의 낙인이 겹쳐 쓰러졌습니다`);
      kill(p, u.boss);
    } else if (n === 1) {
      bossHit(u, p, d.damage, { cause: EntityDamageCause.magic, noSource: true });
    }
  }
}

// ------------------------------------------------------------ 높은 곳
/** 뿌리·담장·탑 위처럼 높은 곳(바닥보다 high.above 칸 넘게)에 서 있는 사람: 날개로 쳐서 떨어뜨림 (사람마다 cd 틱에 한 번). 날아 있는 동안은 없음 */
function highTick(enc, now) {
  if (now % 10 !== 0 || enc.mem.flight) return;
  const u = enc;
  if (!u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const c = enc.center;
  const since = enc.mem.highSince;
  const seen = new Set();
  for (const p of targets(u)) {
    // 뛰는 중이어도 셈 (높은 곳에서 계속 뛰며 버티는 것을 막음): 내려오면(아래로 보이면) 처음부터 다시
    if (p.location.y - c.y < d.above) continue;
    seen.add(p.id);
    // 뿌리를 넘어가는 것은 괜찮음: grace 틱 넘게 높은 곳에 머물면 (처음 올라서면 경고)
    if (!since.has(p.id)) {
      since.set(p.id, now);
      showFaceText(p, "§6§l내려오세요! §f머물면 날개로 칩니다", { dist: 2.2, up: 0.35 });
    }
    if (now - since.get(p.id) < d.grace) continue;
    if (now - (enc.mem.high.get(p.id) ?? -1000) < d.cd) continue;
    enc.mem.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    dust(enc.dim, p.location, 1.8, WIND, 0.6);
    play(enc.dim, "mob.enderdragon.flap", p.location, 0.7, 1);
    // 때린 엔티티 없이 (게임의 밀어내기는 니드호그에게서 먼 쪽 = 담장 밖일 수 있음): 가운데 쪽으로만 떨어뜨림
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 }, noSource: true });
    showFaceText(p, "§6§l날개에 맞아 떨어집니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", `§6[보스] §f뿌리·담장·탑 위처럼 높은 곳에 ${(d.grace / 20).toFixed(1)}초 넘게 머물면 니드호그가 날개로 쳐서 떨어뜨립니다 (뿌리를 넘어가는 것은 괜찮음)`);
  }
  for (const id of [...since.keys()]) if (!seen.has(id)) since.delete(id);
}

/** 땅에 내려놓음 (날다가 쓰러지거나 싸움이 끝날 때): 높이 0, 날기·걸음 속성 0, 급강하 모습 대신 내려앉기 */
function ground(enc) {
  const m = enc.mem;
  if (!m?.body || !enc.boss?.isValid) return;
  const b = m.body;
  const flying = b.h > 0 || b.fly > 0;
  b.h = 0;
  b.flyTo = 0;
  b.fly = 0;
  b.gaitTo = 0;
  b.gait = 0;
  try {
    place(enc);
  } catch {
    // 무시
  }
  b.flySent = setProp(enc, "rpg:fly", 0, undefined);
  b.gaitSent = setProp(enc, "rpg:gait", 0, undefined);
  if (flying) enc.api.anim(enc, "land", undefined, { controller: AIR });
}

// ------------------------------------------------------------ 모듈
export default {
  key: "nidhogg",
  propIds: [ROOTS_ID],
  /** 원거리 견제 (v2.3.0): 비상(독의 비)·독룡의 낙인(흩어져야 함) 동안은 쉼 */
  farOk(u) {
    return !u.mem.flight && !u.mem.brand;
  },
  /** 독 가시는 입에서 */
  farFrom(u) {
    return headAt(u);
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    const c = enc.center;
    // 하늘에서 내려앉으며 등장: 깨운 사람 쪽을 봄 (없으면 남쪽)
    const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
    const yaw = Math.atan2(p.z - c.z, p.x - c.x);
    enc.mem = {
      next: { bite: now + 40, tail: now + d.tail.first, breath: now + d.breath.first, gust: now + d.gust.first, snare: now + d.snare.first, brand: now + d.brand.first },
      bigUntil: now + 100,
      told: new Set(),
      body: { x: c.x, z: c.z, h: 14, yaw, want: undefined, gait: 0, gaitTo: 0, fly: 1, flyTo: 1, gaitSent: undefined, flySent: undefined },
      flight: { stage: "arrive", t0: now, time: 40, h0: 14 },
      flightDone: new Set(),
      brand: undefined,
      roots: [],
      high: new Map(),
      highSince: new Map(),
    };
    for (const u of enc.units) u.mem = enc.mem;
    // 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기는 모듈이)
    enc.meleeAt = Infinity;
    place(enc);
    bodyTick(enc);
    enc.api.cast(enc, "arrive", 44, []);
    play(enc.dim, "mob.enderdragon.flap", c, 0.7, 1);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    const r = u.api.ratio(u);
    if (m.flight) return false;
    // 큰 패턴: 비상 → 독룡의 낙인 (서로 겹치지 않음)
    if (!m.brand && now >= m.bigUntil) {
      const at = d.flight.at.find((x) => r <= x && !m.flightDone.has(x));
      if (at !== undefined) {
        m.flightDone.add(at);
        takeoff(u, now);
        return true;
      }
      if (r <= d.brand.from && now >= m.next.brand) {
        if (brand(u, now, list)) {
          m.next.brand = now + every(u, d.brand.every);
          return false; // 낙인은 틀 없이: 니드호그는 계속 싸움
        }
      }
    }
    if (!m.brand && now >= m.next.gust) {
      if (gust(u, now, list)) {
        m.next.gust = now + every(u, d.gust.every);
        return true;
      }
      m.next.gust = now + 20;
    }
    if (now >= m.next.breath) {
      if (breath(u, now, list)) {
        m.next.breath = now + every(u, d.breath.every);
        return true;
      }
      m.next.breath = now + 20;
    }
    if (!m.brand && now >= m.next.snare) {
      if (snare(u, now, list)) {
        m.next.snare = now + every(u, d.snare.every);
        return true;
      }
      m.next.snare = now + 20;
    }
    if (now >= m.next.tail) {
      if (tailWhip(u, now, list)) {
        m.next.tail = now + every(u, d.tail.every);
        return true;
      }
      m.next.tail = now + 10;
    }
    // 평타: 몸 앞의 가장 가까운 사람을 물어뜯음
    if (now >= m.next.bite && bite(u, now, list)) {
      m.next.bite = now + Math.round(d.melee.every * (u.cdMult ?? 1));
      return true;
    }
    return false;
  },
  tick(enc, now, list) {
    const u = enc;
    if (!u.alive || !u.boss?.isValid || !enc.mem?.body) return;
    flightTick(enc, now);
    brandTick(enc, now);
    rootsTick(enc, now);
    highTick(enc, now);
    if (!enc.alive) return;
    // 몸: 비상 중에는 flightTick 이, 패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
    if (!enc.mem.flight) {
      if (u.cast) standTick(u);
      else {
        u.mem.body.want = undefined;
        walkTick(u, list ?? targets(u));
      }
    }
    bodyTick(u);
  },
  /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 날다가 쓰러지면 땅에 내려놓고 서 있는 모습으로 */
  dying(enc) {
    ground(enc);
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.body) return;
    ground(enc);
    for (const r of m.roots ?? []) enc.api.dropProp(enc, r.e);
    m.roots = [];
    m.flight = undefined;
    m.brand = undefined;
  },
};

/** 시험용: 지금 낙인 */
export function brandOf(enc) {
  return enc.mem?.brand;
}
/** 시험용: 비상 상태 */
export function flightOf(enc) {
  return enc.mem?.flight;
}
/** 시험용: 쓸어 가는 동안 불길이 닿는 곳인지 */
export function sweepCovers(u, a0, a1, l) {
  return inSweep(u, { a0, a1 }, l);
}
