import { EntityDamageCause, system } from "@minecraft/server";
import { isleSize, playersIn } from "./arena.js";
import { hpState, isDying } from "./hp.js";
import { notify, play, showFaceText } from "./fx.js";
import { beam, fx, shake } from "./skills.js";
import { kill, root, silence } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fanMarks, fighters, hdist, inFan, lineMarks, meleeTarget, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 요르문간드 (v2.2.0, Lv.32): 수치는 config.js BOSSES.jormungandr
 * 뱀의 섬 (가운데 섬 + 독의 해자 + 둑) 의 해자를 헤엄쳐 돎. 엔티티(맞는 곳) = 머리, 모듈이 매 틱 옮김 (엔진 pose)
 * 몸의 움직임: 목뿌리(머리에서 목 길이 swim.neck 뒤, 모델의 몸 뿌리)가 해자 가운데 원을 따라가고 머리는 목뿌리 앞.
 *   패턴 동안에는 목뿌리가 그대로 있고 목만 돌려 대상 쪽을 봄 (몸통·꼬리가 해자에 남음: 필드 담장을 뚫고 나가지 않게)
 *   엔티티 속성: rpg:tail = 꼬리가 목뿌리에서 나가는 방향 (머리 바로 뒤 기준, 도), rpg:curve = 꼬리 마디마다 굽는 각도 (원을 따라), rpg:sink = 물에 잠김 (0~1)
 * - 물어뜯기 (평타): 목이 닿는 사람 쪽으로 목을 돌려 부채꼴 예고 → 피해 (목뿌리에서 앞으로)
 * - 독니 돌진: 섬 위의 한 명 쪽으로 목을 돌려 길을 예고 → 반대편 해자까지 돌진 (머리가 지나가는 자리: 피해 + 침묵 + 옆으로 밀림),
 *   꼬리는 돌진하며 길 뒤로 따라 들어오고, 끝나면 다시 해자로 휘어 들어가 돎
 * - 꼬리 휘감기: 목뿌리 뒤 꼬리 쪽 (해자 ~ 섬 가장자리) 을 예고 → 피해 + 속박. 그 자리에 아무도 없으면 하지 않음
 * - 독해 범람 (체력 85% 아래): 흰 고리 밖이 독에 잠김 (피해 + 독, 잠시 남음) → 끝나면 고리 안에 모인 곳을 가로질러 독니 돌진
 * - 아홉 걸음의 독 (체력 70% 아래): 표식이 붙은 사람이 스스로 아홉 칸을 걸으면 즉사 (보스에게 맞아 밀린 것은 셈하지 않음).
 *   표식이 없는 동료가 곁(초록 원)에 서 있으면 독을 빼 줌 (도운 사람은 조금 다침). 시간이 다 되면 사라짐
 * - 세계를 감는 고리 (체력 50%·20%): 몸이 필드 둘레에 떠올라 조여 옴 → 머리 앞 틈(초록 길)으로만 빠져나감.
 *   틈 밖에서 몸에 닿거나 몸을 넘어가면(순간이동 스킬로 건너뛰어도) 즉사, 끝까지 고리 안에 남으면 즉사.
 *   고리가 시작될 때 싸우던 사람은 틈을 지나지 않고 고리 밖에 있으면 (성문으로 나가는 등) 즉사. 머리는 틈 가장자리를 따라 들어옴 (때릴 수 있음)
 * - 독의 해자: 해자 물에 들어가 있으면 1초마다 마법 피해. 높은 곳(성문·탑 위): 꼬리로 쳐서 떨어뜨림
 * 큰 패턴(고리·아홉 걸음·범람)은 서로 겹치지 않음 (mem.bigUntil). 엔진 평타는 쓰지 않음 (목을 돌리는 물어뜯기를 모듈이)
 */

const VENOM = [0.46, 0.95, 0.32];
const TEAL = [0.3, 0.86, 0.62];
const PURPLE = [0.72, 0.36, 0.98];
const ORANGE = [1.0, 0.5, 0.14];
const RED = [1.0, 0.22, 0.14];
const SAFE = [0.86, 1.0, 0.9];
const DUST = [0.6, 0.62, 0.56];
const FOAM = [0.7, 0.95, 0.8];
const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

/** 목을 돌릴 수 있는 가장 큰 각도 (몸 방향에서, 라디안) */
export const NECK_MAX = 110 * DEG;
/** 꼬리가 새 방향으로 휘어 들어가는 빠르기 (한 틱에, 라디안) */
export const TAIL_TURN = 8 * DEG;
/** rpg:curve 속성 범위 (도, build.py 와 같음) */
export const CURVE_MAX = 45;
/** rpg:tail 속성 범위 (도, build.py 와 같음) */
export const TAIL_MAX = 170;

/** 동작 틀 (고리 동작은 떠오르기가 같은 틀에서 바꿀 때까지 유지) */
const COIL = "rpg_coil";

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
/** 각도 → 0 ~ 2π */
const wrap = (a) => ((a % TAU) + TAU) % TAU;
/** 각도 차이 → -π ~ π */
const wrapPi = (a) => {
  const w = wrap(a);
  return w > Math.PI ? w - TAU : w;
};
/** 가운데 기준 각도 */
const polarOf = (u, l) => Math.atan2(l.z - u.center.z, l.x - u.center.x);
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

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

/** 머리 (연출용: 엔티티 자리 위) */
function headAt(u) {
  const l = u.boss.location;
  return { x: l.x, y: l.y + 3.6, z: l.z };
}

/** 독 물보라 (rpg:splash: 튀어 오르는 물방울 왕관). 밑동이 바닥에 닿게 올림 */
function splash(dim, at, size, color = VENOM, life = 0.6) {
  fx(dim, "rpg:splash", { x: at.x, y: at.y + size * 0.75, z: at.z }, { size, life, color, alpha: 0.95 });
}

/** 독 안개 (rpg:dust 초록) */
function mist(dim, at, size, color = VENOM, life = 0.8) {
  fx(dim, "rpg:dust", { x: at.x, y: at.y + size * 0.35, z: at.z }, { size, life, color, alpha: 0.8 });
}

function shakeNear(u, at, range, intensity, seconds) {
  for (const p of fighters(u)) if (hdist(p.location, at) <= range) shake(p, intensity, seconds);
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

// ------------------------------------------------------------ 몸 (목뿌리·목·꼬리)
/** 꼬리가 반지름 r 원을 따라 굽는 마디 각도 (도, 부호: 도는 쪽 dir = +1 이면 음수). build.py 의 slither 동작과 같은 약속 */
export function curveFor(u, r, dir) {
  const S = u.def.swim;
  const deg = ((S.seg / Math.max(0.5, r)) * 180) / Math.PI;
  return -dir * Math.min(CURVE_MAX, deg);
}

/** 목뿌리 (몸통이 시작되는 곳) */
export function pivotOf(u) {
  const sw = u.mem.swim;
  return { x: sw.px, y: u.center.y, z: sw.pz };
}

/** 머리 자리·방향: 목뿌리에서 (몸 방향 + 목 각도) 로 목 길이만큼 */
function headOf(u) {
  const sw = u.mem.swim;
  const y = sw.byaw + sw.nyaw;
  const N = u.def.swim.neck;
  return { x: sw.px + Math.cos(y) * N, z: sw.pz + Math.sin(y) * N, yaw: y };
}

/** 몸 상태대로 엔티티(머리)를 옮김. 목뿌리가 해자 물 위면 몸이 잠김 */
function place(u) {
  const sw = u.mem.swim;
  const h = headOf(u);
  u.api.pose(u, floorAt(u, h.x, h.z), h.yaw);
  sw.sinkTo = overMoat(u, pivotOf(u)) ? 1 : 0;
}

/** 머리를 at 에 두고 yaw 쪽을 보게 (목은 곧게, 목뿌리는 그 뒤로) */
function setHead(u, at, yaw) {
  const sw = u.mem.swim;
  const N = u.def.swim.neck;
  sw.px = at.x - Math.cos(yaw) * N;
  sw.pz = at.z - Math.sin(yaw) * N;
  sw.byaw = yaw;
  sw.nyaw = 0;
  place(u);
}

/** 목뿌리를 해자 가운데 원의 ang 자리에 (몸은 도는 쪽을 봄, 목 곧게, 꼬리는 원을 따라 바로). 시험·미리보기도 씀 */
export function setOnCircle(u, ang) {
  const S = u.def.swim;
  const sw = u.mem.swim;
  const c = u.center;
  sw.ang = wrap(ang);
  sw.px = c.x + Math.cos(sw.ang) * S.radius;
  sw.pz = c.z + Math.sin(sw.ang) * S.radius;
  sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
  sw.nyaw = 0;
  sw.onCircle = true;
  sw.tailTo = sw.byaw + Math.PI;
  sw.tail = sw.tailTo;
  sw.curveTo = curveFor(u, S.radius, sw.dir);
  sw.curve = sw.curveTo;
  place(u);
}

function setProp(u, id, v, last) {
  if (last !== undefined && Math.abs(v - last) < (id === "rpg:sink" ? 0.02 : 0.05)) return last;
  try {
    u.boss.setProperty(id, v);
  } catch {
    // 무시
  }
  return v;
}

/** 매 틱: 꼬리 방향·굽힘·물에 잠김을 목표 쪽으로 조금씩 (한 번에 휙 바뀌지 않게) → 모델 속성 */
function bodyTick(u) {
  const sw = u.mem.swim;
  sw.tail = turnToward(sw.tail, sw.tailTo, TAIL_TURN);
  const dc = sw.curveTo - sw.curve;
  sw.curve = Math.abs(dc) <= 1.2 ? sw.curveTo : sw.curve + Math.sign(dc) * 1.2;
  const ds = sw.sinkTo - sw.sink;
  sw.sink = Math.abs(ds) <= 0.08 ? sw.sinkTo : sw.sink + Math.sign(ds) * 0.08;
  // 꼬리 방향: 머리 바로 뒤(머리 방향 + 180°) 에서 얼마나 돌았는지
  const rel = wrapPi(sw.tail - (sw.byaw + sw.nyaw + Math.PI)) / DEG;
  sw.tailSent = setProp(u, "rpg:tail", Math.round(clamp(rel, -TAIL_MAX, TAIL_MAX) * 100) / 100, sw.tailSent);
  sw.curveSent = setProp(u, "rpg:curve", Math.round(clamp(sw.curve, -CURVE_MAX, CURVE_MAX) * 100) / 100, sw.curveSent);
  sw.sinkSent = setProp(u, "rpg:sink", clamp(sw.sink, 0, 1), sw.sinkSent);
}

/** 해자 물 위인지 */
function overMoat(u, l) {
  const { island, moat } = isleSize(u.enc.key);
  const r = hdist(l, u.center);
  return r > island - 0.3 && r < moat + 0.3;
}

function turnToward(from, to, step) {
  const d = wrapPi(to - from);
  return Math.abs(d) <= step ? to : from + Math.sign(d) * step;
}

// ------------------------------------------------------------ 헤엄 · 목 돌리기
/**
 * 패턴이 없을 때: 목을 곧게 펴며 목뿌리가 해자 가운데 원을 따라 돎.
 * 원에서 벗어나 있으면 (돌진 뒤) 앞으로 나아가며 원의 조금 앞 자리 쪽으로 몸을 돌려 휘어 들어감
 */
function swimTick(u, now) {
  const S = u.def.swim;
  const sw = u.mem.swim;
  const c = u.center;
  const step = S.turn * DEG;
  sw.nyaw = turnToward(sw.nyaw, 0, step);
  if (sw.onCircle) {
    sw.ang = wrap(sw.ang + (sw.dir * S.speed) / S.radius);
    sw.px = c.x + Math.cos(sw.ang) * S.radius;
    sw.pz = c.z + Math.sin(sw.ang) * S.radius;
    sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
    sw.tailTo = sw.byaw + Math.PI;
  } else {
    // 원 밖이면 안쪽으로, 원 안이면 바깥쪽으로 틀어 (1칸마다 20°, 60° 까지) 도는 쪽 접선을 따라감 → 원 위에서는 접선 그대로
    const pa = polarOf(u, pivotOf(u));
    const err = hdist(pivotOf(u), c) - S.radius;
    const want = pa + (sw.dir * Math.PI) / 2 + sw.dir * clamp(err * 20 * DEG, -60 * DEG, 60 * DEG);
    sw.byaw = turnToward(sw.byaw, want, step);
    sw.px += Math.cos(sw.byaw) * S.speed * 1.4;
    sw.pz += Math.sin(sw.byaw) * S.speed * 1.4;
    const pa2 = polarOf(u, pivotOf(u));
    sw.ang = wrap(pa2);
    sw.tailTo = pa2 + (sw.dir * Math.PI) / 2 + Math.PI;
    // 원에 닿고 도는 쪽을 보면 원을 따라 돎
    if (Math.abs(hdist(pivotOf(u), c) - S.radius) < 0.3 && Math.abs(wrapPi(sw.byaw - (pa2 + (sw.dir * Math.PI) / 2))) < 5 * DEG) {
      sw.onCircle = true;
      sw.px = c.x + Math.cos(sw.ang) * S.radius;
      sw.pz = c.z + Math.sin(sw.ang) * S.radius;
      sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
    }
  }
  sw.curveTo = curveFor(u, S.radius, sw.dir);
  u.faceTo = sw.byaw + sw.nyaw;
  place(u);
  // 물살 (머리 옆 작은 물보라)
  if (now % 6 === 0) {
    const h = u.boss.location;
    if (overMoat(u, h)) splash(u.dim, floorAt(u, h.x, h.z), 1.1, FOAM, 0.45);
  }
  if (now % 60 === 0) play(u.dim, "mob.dolphin.swim", u.boss.location, 0.5, 0.7);
}

/** 패턴 동안: 목뿌리는 그대로, 목만 돌려 바라보려는 쪽(u.faceTo: 엔진 face 또는 모듈이 정함)을 봄 (몸 방향에서 NECK_MAX 까지) */
function neckTick(u) {
  const sw = u.mem.swim;
  const look = u.faceTo ?? sw.byaw + sw.nyaw;
  const want = clamp(wrapPi(look - sw.byaw), -NECK_MAX, NECK_MAX);
  const d = want - sw.nyaw;
  const step = u.def.swim.turn * DEG;
  sw.nyaw = Math.abs(d) <= step ? want : sw.nyaw + Math.sign(d) * step;
  place(u);
}

/** 목이 그쪽을 볼 수 있는지 (몸 방향에서 NECK_MAX 안) */
function neckCanFace(u, from, to) {
  return Math.abs(wrapPi(angOf(dirTo(from, to)) - u.mem.swim.byaw)) <= NECK_MAX;
}

// ------------------------------------------------------------ 물어뜯기 (평타)
function bite(u, now, list) {
  const m = u.def.melee;
  const S = u.def.swim;
  const piv = pivotOf(u);
  const { half } = bossSize(u.def);
  const reach = S.neck + half + m.reach;
  // 목이 닿는 사람 가운데 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람)
  const t = meleeTarget(u, list.filter((p) => hdist(p.location, piv) <= reach && Math.abs(p.location.y - u.center.y) <= 4 && neckCanFace(u, piv, p.location)), piv);
  if (!t) return false;
  const dir = dirTo(piv, t.location);
  u.api.face(u, dir);
  fanMarks(u.dim, piv, dir, Math.round(reach), m.cone, m.windup, RED);
  u.api.anim(u, m.anim);
  sound(u, u.def.sounds.swing, headAt(u), 0.8, 1);
  u.api.cast(u, "bite", m.windup + 6, [[m.windup, (x) => biteHit(x, piv, dir, reach)]]);
  return true;
}

function biteHit(u, apex, dir, radius) {
  const m = u.def.melee;
  for (const p of targets(u)) {
    if (Math.abs(p.location.y - apex.y) > 4 || !inFan(p.location, apex, dir, radius, m.cone, 0.5)) continue;
    const kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
    bossHit(u, p, m.damage, { knock: { x: kk.x, z: kk.z, h: 0.35 }, directional: true });
    calm(u, p); // 밀림은 피해가 막혀도 들어감
  }
  sound(u, u.def.sounds.hit, headAt(u), 0.9, 1);
  const h = u.boss.location;
  fx(u.dim, "rpg:slash_heavy_r_glow", { x: h.x + dir.x * 1.5, y: h.y + 1.4, z: h.z + dir.z * 1.5 }, { dir, speed: 0, spin: 0, size: 2.6, life: 0.25, color: u.def.color, alpha: 0.95 });
}

// ------------------------------------------------------------ 독니 돌진
/** 돌진 길: from 에서 dir 쪽으로, 반대편 해자(가운데 원보다 endIn 칸 안쪽 원)까지 (길이 L) */
function lane(u, from, dir) {
  const rm = u.def.swim.radius - u.def.dash.endIn;
  const c = u.center;
  const ox = from.x - c.x;
  const oz = from.z - c.z;
  const b = ox * dir.x + oz * dir.z;
  const q = b * b - (ox * ox + oz * oz - rm * rm);
  const L = q >= 0 ? -b + Math.sqrt(q) : 0;
  return { from: floorAt(u, from.x, from.z), dir, L };
}

function dash(u, now, list, center) {
  const d = u.def.dash;
  const S = u.def.swim;
  const c = u.center;
  const piv = pivotOf(u);
  const cand = list.filter((p) => hdist(p.location, c) <= S.radius - 1 && hdist(p.location, piv) >= S.neck + d.near && Math.abs(p.location.y - c.y) < 4 && neckCanFace(u, piv, p.location));
  if (!cand.length) return false;
  // 범람 뒤: 흰 고리 안에 모인 곳 (가운데에 가장 가까운 사람) 을 가로지름
  const t = center ? [...cand].sort((a, b) => hdist(a.location, c) - hdist(b.location, c))[0] : cand[Math.floor(Math.random() * cand.length)];
  const dir = dirTo(piv, t.location);
  // 목을 그쪽으로 돌린 머리 자리에서 출발
  const ln = lane(u, { x: piv.x + dir.x * S.neck, z: piv.z + dir.z * S.neck }, dir);
  if (ln.L < 6) return false;
  const api = u.api;
  const to = { x: ln.from.x + dir.x * ln.L, y: ln.from.y, z: ln.from.z + dir.z * ln.L };
  api.face(u, dir);
  lineMarks(u.dim, ln.from, to, d.width / 2, d.windup, PURPLE);
  api.anim(u, "dash");
  sound(u, u.def.sounds.swing, headAt(u), 0.6, 1);
  play(u.dim, "mob.breeze.charge", ln.from, 0.6, 1);
  showFaceText(t, "§5§l노림! §f보라 길에서 옆으로 비키세요", { dist: 2.2, up: 0.35 });
  explain(u, "dash", "§5[보스] 독니 돌진: §f요르문간드가 보라 길을 따라 섬을 가로질러 돌진합니다. §e길에서 옆으로 비키세요 §7— 맞으면 침묵 (스킬·룬을 못 씀)");
  const hit = new Set();
  const n = Math.ceil(ln.L / d.speed);
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let k = 0; k <= n; k++) steps.push([d.windup + k, (x) => dashStep(x, ln, k, n, hit)]);
  steps.push([d.windup + n + 1, (x) => dashEnd(x)]);
  api.cast(u, "dash", d.windup + n + 8, steps);
  return true;
}

/** 돌진 k 번째 틱 (0 = 출발): 머리가 이번 틱에 지나간 자리의 사람이 맞음 (한 번만). 꼬리는 길 뒤로 따라 들어옴 */
function dashStep(u, ln, k, n, hit) {
  const d = u.def.dash;
  const sw = u.mem.swim;
  const s0 = (k - 1) * d.speed;
  const s = Math.min(ln.L, k * d.speed);
  const at = { x: ln.from.x + ln.dir.x * s, y: ln.from.y, z: ln.from.z + ln.dir.z * s };
  sw.charging = k < n;
  sw.onCircle = false;
  setHead(u, at, angOf(ln.dir));
  sw.tailTo = angOf(ln.dir) + Math.PI;
  sw.curveTo = 0;
  if (k === 0) {
    play(u.dim, "item.trident.riptide_1", at, 0.7, 1);
    return;
  }
  if (k % 2 === 0) (overMoat(u, at) ? splash : mist)(u.dim, at, 1.6, overMoat(u, at) ? FOAM : DUST, 0.5);
  const side = { x: -ln.dir.z, z: ln.dir.x };
  for (const p of targets(u)) {
    if (hit.has(p.id) || Math.abs(p.location.y - ln.from.y) > 3) continue;
    const rx = p.location.x - ln.from.x;
    const rz = p.location.z - ln.from.z;
    const t = rx * ln.dir.x + rz * ln.dir.z;
    if (t <= s0 - 0.5 || t > s + 0.5) continue;
    const lat = rx * side.x + rz * side.z;
    if (Math.abs(lat) > d.width / 2 + 0.3) continue;
    hit.add(p.id);
    const sg = lat >= 0 ? 1 : -1;
    const kk = keepIn(u, p, side.x * sg * d.knock, side.z * sg * d.knock);
    const landed = bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.4 } });
    calm(u, p);
    if (landed) {
      silence(p, d.silence);
      showFaceText(p, "§5§l침묵! §7독에 혀가 굳었습니다", { dist: 2.2, up: 0.35 });
      fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.6, life: 0.35, color: PURPLE, alpha: 0.9 });
    }
  }
  if (k === n) shakeNear(u, at, 10, 0.2, 0.3);
}

/** 돌진 끝 (반대편 해자): 앞으로 나아가며 해자로 휘어 들어가 다시 돎 (가끔 도는 방향을 바꿈) */
function dashEnd(u) {
  const sw = u.mem.swim;
  sw.charging = false;
  if (Math.random() < u.def.swim.flip) sw.dir = -sw.dir;
  const at = u.boss.location;
  splash(u.dim, floorAt(u, at.x, at.z), 2.4, FOAM, 0.7);
  play(u.dim, "random.splash", at, 0.7, 1);
}

// ------------------------------------------------------------ 꼬리 휘감기
/** 꼬리 쪽 자리: 목뿌리 뒤 (해자를 따라) from ~ to 칸, 가운데에서 inner 칸 밖 → {각도 범위, 반지름 범위} */
function tailZone(u) {
  const d = u.def.tail;
  const sw = u.mem.swim;
  const rm = u.def.swim.radius;
  const R = u.enc.def.arena.radius;
  return { base: sw.ang, dir: sw.dir, a0: d.from / rm, a1: d.to / rm, r0: d.inner, r1: R + 1.5 };
}
function inTail(u, Z, l) {
  const r = hdist(l, u.center);
  if (r < Z.r0 - 0.3 || r > Z.r1 + 0.3) return false;
  const behind = wrap((Z.base - polarOf(u, l)) * Z.dir);
  const pad = 0.3 / Math.max(1, r);
  return behind >= Z.a0 - pad && behind <= Z.a1 + pad;
}

/** 고리 부채꼴 예고 (가운데 기준 반지름 r0 ~ r1, 각도 th0 ~ th1): 극좌표 격자에 작은 판, 가장자리는 밝게 */
function sectorMarks(u, r0, r1, th0, th1, ticks, color) {
  const life = Math.max(0.1, ticks / 20);
  const c = u.center;
  const step = 1.5;
  for (let r = r0 + step / 2; r <= r1 + 0.01; r += step) {
    const n = Math.max(2, Math.ceil(((th1 - th0) * r) / step));
    for (let i = 0; i <= n; i++) {
      const a = th0 + ((th1 - th0) * i) / n;
      const edge = r + step > r1 + 0.01 || r - step < r0 || i === 0 || i === n;
      fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", { x: c.x + Math.cos(a) * r, y: c.y + 0.07, z: c.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: (edge ? 0.55 : 0.75) * step, life, color, alpha: edge ? 0.9 : 0.45, grow: edge ? 0 : 1 });
    }
  }
}

/** 꼬리가 해자를 따라 놓여 있는지 (원을 따라 돌고, 꼬리 방향·굽힘이 다 맞춰짐) */
function tailSettled(u) {
  const sw = u.mem.swim;
  return sw.onCircle && Math.abs(wrapPi(sw.tail - sw.tailTo)) < 5 * DEG && Math.abs(sw.curve - sw.curveTo) <= 1 && Math.abs(sw.nyaw) < 30 * DEG;
}

function tailLash(u, now, list) {
  const d = u.def.tail;
  if (!tailSettled(u)) return false;
  const Z = tailZone(u);
  if (!list.some((p) => inTail(u, Z, p.location) && Math.abs(p.location.y - u.center.y) < 4)) return false;
  const thA = Z.base - Z.dir * Z.a0;
  const thB = Z.base - Z.dir * Z.a1;
  sectorMarks(u, Z.r0, Z.r1, Math.min(thA, thB), Math.max(thA, thB), d.windup, ORANGE);
  u.api.anim(u, "coil");
  sound(u, "mob.cat.hiss", headAt(u), 0.5, 1);
  play(u.dim, "mob.enderdragon.flap", pivotOf(u), 0.6, 1);
  explain(u, "tail", "§6[보스] 꼬리 휘감기: §f요르문간드가 꼬리 쪽(목 뒤 해자와 섬 가장자리)을 휘감습니다. §e주황 자리에서 벗어나세요 §7— 맞으면 속박");
  u.api.cast(u, "tail", d.windup + 16, [[d.windup, (x) => tailHit(x, Z)]]);
  return true;
}

function tailHit(u, Z) {
  const d = u.def.tail;
  const c = u.center;
  const rm = u.def.swim.radius;
  // 꼬리가 지나간 자리: 물보라·흙먼지
  for (let i = 0; i <= 6; i++) {
    const a = Z.base - Z.dir * (Z.a0 + ((Z.a1 - Z.a0) * i) / 6);
    splash(u.dim, floorAt(u, c.x + Math.cos(a) * rm, c.z + Math.sin(a) * rm), 1.8, FOAM, 0.6);
    mist(u.dim, floorAt(u, c.x + Math.cos(a) * (Z.r0 + 1.5), c.z + Math.sin(a) * (Z.r0 + 1.5)), 1.6, DUST, 0.6);
  }
  play(u.dim, "random.splash", pivotOf(u), 0.6, 1);
  play(u.dim, "mob.enderdragon.hit", pivotOf(u), 0.6, 1);
  for (const p of targets(u)) {
    if (!inTail(u, Z, p.location) || Math.abs(p.location.y - c.y) > 3) continue;
    const landed = bossHit(u, p, d.damage, { knock: { x: 0, z: 0, h: 0.2 } });
    calm(u, p);
    if (landed) root(p, d.root, "꼬리에 휘감김");
  }
}

// ------------------------------------------------------------ 독해 범람
function flood(u, now) {
  const d = u.def.flood;
  const m = u.mem;
  const { safe } = isleSize(u.enc.key);
  m.bigUntil = now + d.windup + d.hold + 80;
  m.flood = { u, t0: now, waveAt: now + d.windup, endAt: now + d.windup + d.hold };
  u.api.face(u, dirTo(pivotOf(u), u.center));
  u.api.anim(u, "rear");
  sound(u, u.def.sounds.roar, headAt(u), 0.7, 1);
  title(u, "§2§l독해 범람", "§f흰 고리 안으로! §7고리 밖은 독에 잠깁니다", fighters(u));
  explain(u, "flood", `§2[보스] 독해 범람: §f해자의 독이 차올라 섬의 흰 고리(반지름 ${safe}칸) 밖을 모두 덮습니다. §e흰 고리 안으로 모이세요 §7— 독이 빠지면 모인 곳으로 돌진합니다`);
  u.api.cast(u, "flood", d.windup + d.hold + 10, []);
  floodMarks(u, d.windup);
}

/** 범람 때 안전한 곳의 바깥 끝: 흰 석영 고리(칸 가운데가 safe - 1 ~ safe 인 칸) 의 바깥 가장자리 */
export function safeEdge(safe) {
  return safe + 0.7;
}

/** 범람 예고: 흰 고리(안전) 테두리 + 고리 밖 초록 판 */
function floodMarks(u, ticks) {
  const { safe } = isleSize(u.enc.key);
  const R = u.enc.def.arena.radius;
  const c = u.center;
  const life = Math.max(0.1, ticks / 20);
  const edge = safeEdge(safe);
  disc(u.dim, c, edge, ticks, SAFE, { fill: false, alpha: 0.95 });
  for (let r = edge + 1.4; r <= R + 1.5; r += 2.8) {
    const n = Math.ceil((TAU * r) / 2.8);
    for (let i = 0; i < n; i++) {
      const a = (i / n) * TAU + r;
      fx(u.dim, "rpg:tele_fill", { x: c.x + Math.cos(a) * r, y: c.y + 0.07, z: c.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: 1.5, life, color: VENOM, alpha: 0.5 });
    }
  }
}

function floodTick(enc, now) {
  const F = enc.mem.flood;
  if (!F) return;
  const u = F.u;
  const d = u.def.flood;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.flood = undefined;
    return;
  }
  const { island, moat, safe } = isleSize(enc.key);
  const c = u.center;
  const outside = (p) => hdist(p.location, c) > safeEdge(safe);
  if (now < F.waveAt) {
    // 차오르는 독: 해자를 따라 물보라, 남은 시간
    if ((now - F.t0) % 6 === 0) {
      for (let i = 0; i < 4; i++) {
        const a = Math.random() * TAU;
        const r = island + Math.random() * (moat - island);
        splash(u.dim, floorAt(u, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 1.4 + ((now - F.t0) / d.windup) * 1.2, VENOM, 0.6);
      }
      play(u.dim, "bubble.pop", c, 0.6, 0.6);
    }
    const left = F.waveAt - now;
    if (left % 20 === 0 && left <= 60) faceAll(u, `§2§l독해 범람 §f${left / 20}`, { dist: 2.4, up: 0.45 }, fighters(u));
    return;
  }
  if (now === F.waveAt) {
    // 물결: 둘레에서 흰 고리까지 안쪽으로 밀려듦
    const R = enc.def.arena.radius;
    for (let i = 0; i < 20; i++) {
      const a = (i / 20) * TAU;
      const dir = { x: -Math.cos(a), y: 0, z: -Math.sin(a) };
      fx(u.dim, "rpg:tide_wave", { x: c.x + Math.cos(a) * (R + 1), y: c.y + 1.2, z: c.z + Math.sin(a) * (R + 1) }, { dir, speed: 14, size: 2.6, thick: 1.6, life: (R + 1 - safe) / 14, color: VENOM, alpha: 0.95 });
    }
    play(u.dim, "random.splash", c, 0.5, 1);
    play(u.dim, "ambient.weather.thunder", c, 0.7, 0.5);
    shakeNear(u, c, R + 8, 0.3, 0.8);
    for (const p of targets(u)) {
      if (!outside(p)) continue;
      // 독 물결: 보스가 때린 것이 아님 (게임이 보스 쪽에서 밀어내지 않게: 아홉 걸음이 밀린 걸음을 셈하지 않도록)
      if (bossHit(u, p, d.damage, { cause: EntityDamageCause.entityAttack, noSource: true, effect: { id: "poison", ticks: d.poison, amp: 0 } })) splash(u.dim, p.location, 1.8, VENOM, 0.6);
    }
  }
  if (now < F.endAt) {
    // 남은 독: 고리 밖이면 1초마다 마법 피해, 초록 안개
    if ((now - F.waveAt) % 5 === 0) {
      for (let i = 0; i < 5; i++) {
        const a = Math.random() * TAU;
        const r = safe + 1 + Math.random() * (enc.def.arena.radius - safe);
        mist(u.dim, floorAt(u, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 2.2, VENOM, 0.8);
      }
    }
    if ((now - F.waveAt) % 20 === 19) {
      for (const p of targets(u)) {
        if (!outside(p)) continue;
        bossHit(u, p, d.tick, { cause: EntityDamageCause.magic, noSource: true });
        showFaceText(p, "§2§l독에 잠겼습니다 §7— 흰 고리 안으로", { dist: 2.2, up: 0.35 });
      }
    }
    return;
  }
  enc.mem.flood = undefined;
  // 독이 빠짐 → 곧바로 모인 곳을 가로질러 돌진
  enc.mem.next.dash = now + 4;
  enc.mem.floodDash = true;
  faceAll(u, "§a§l독이 빠졌습니다 §7— 돌진에 조심", { dist: 2.4, up: 0.45 }, fighters(u));
}

// ------------------------------------------------------------ 아홉 걸음의 독
/** 보스에게 맞아 밀린 사람: calm 틱 동안 걸음으로 셈하지 않음 */
function calm(u, p) {
  const V = u.mem?.venom;
  if (!V) return;
  for (const mk of V.marks) if (mk.p.id === p.id) mk.calm = system.currentTick + u.def.venom.calm;
}

function venomMark(u, now, list) {
  const d = u.def.venom;
  const m = u.mem;
  if (!list.length) return false;
  const n = list.length > 3 ? 2 : 1;
  const marks = shuffle(list)
    .slice(0, n)
    .map((p) => ({ p, used: 0, last: { ...p.location }, calm: now + d.grace, help: 0, until: now + d.time, shown: -1, done: false }));
  m.venom = { u, marks, t0: now };
  m.bigUntil = now + d.time + 40;
  u.api.face(u, dirTo(pivotOf(u), marks[0].p.location));
  u.api.anim(u, "hiss");
  sound(u, "mob.cat.hiss", headAt(u), 0.45, 1);
  for (const mk of marks) {
    beam(u.dim, headAt(u), { x: mk.p.location.x, y: mk.p.location.y + 1.2, z: mk.p.location.z }, VENOM, { half: 0.4, thick: 0.14, life: 0.6, gap: 0.6 });
    title(u, "§2§l아홉 걸음의 독", `§f스스로 §c${d.steps}칸§f을 걸으면 쓰러집니다 §7— 동료가 곁에 서면 독을 빼 줍니다`, [mk.p]);
    notify(mk.p, `§2아홉 걸음의 독 §f남은 걸음 §a§l${d.steps}§r §7(${Math.round(d.time / 20)}초)`, 25);
    play(u.dim, "mob.zombie.unfect", mk.p.location, 0.8, 1);
  }
  const names = marks.map((mk) => mk.p.name).join(", ");
  const others = fighters(u).filter((p) => !marks.some((mk) => mk.p.id === p.id));
  title(u, `§2§l${names}님에게 아홉 걸음의 독!`, "§f곁(초록 원)에 서서 독을 빼 주세요", others);
  explain(u, "venom", `§2[보스] 아홉 걸음의 독: §f표식이 붙은 사람이 스스로 ${d.steps}칸을 움직이면 쓰러집니다 (즉사, 순간이동 스킬도 셈). §e표식이 붙은 사람은 멈추고, 동료가 곁(초록 원)에 ${(d.need / 20).toFixed(1)}초 서 있으면 독이 빠집니다 §7(${Math.round(d.time / 20)}초 버티면 사라짐)`);
  u.api.cast(u, "venom", 30, []);
  return true;
}

function venomTick(enc, now) {
  const V = enc.mem.venom;
  if (!V) return;
  const u = V.u;
  const d = u.def.venom;
  if (!u.alive || !u.boss?.isValid) {
    enc.mem.venom = undefined;
    return;
  }
  // 독을 빼 줄 수 있는 사람: 필드 둘레 (표식이 붙은 사람이 필드 밖으로 밀려나도 동료가 따라가 빼 줄 수 있게)
  const all = playersIn(u.a, d.leave).filter(okTarget);
  for (const mk of V.marks) {
    if (mk.done) continue;
    const p = mk.p;
    if (!p?.isValid || !okTarget(p)) {
      mk.done = true; // 쓰러짐·나감
      continue;
    }
    // 싸움에서 멀리 떠남 (순간이동·다시 태어남 등, 걸어서는 그 전에 쓰러짐): 표식을 거둠
    if (hdist(p.location, u.center) > u.enc.def.arena.radius + d.leave) {
      mk.done = true;
      continue;
    }
    // 걸음: 스스로 움직인 가로 거리 (보스에게 맞아 밀린 뒤 calm 틱은 셈하지 않음)
    const moved = hdist(p.location, mk.last);
    mk.last = { ...p.location };
    if (now > mk.calm && moved > 0.01) mk.used += moved;
    const left = Math.max(0, d.steps - mk.used);
    if (mk.used >= d.steps) {
      mk.done = true;
      const at = { ...p.location };
      splash(u.dim, at, 2.4, VENOM, 0.7);
      fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.4, life: 0.5, color: VENOM, alpha: 0.95 });
      play(u.dim, "mob.zombie.unfect", at, 0.5, 1);
      say(u, `§2[보스] §e${p.name}§f님이 아홉 걸음 끝에 쓰러졌습니다`);
      kill(p, u.boss);
      continue;
    }
    // 독 빼기: 표식이 없는 동료가 radius 안
    const helpers = all.filter((q) => q.id !== p.id && !V.marks.some((x) => !x.done && x.p.id === q.id) && hdist(q.location, p.location) <= d.radius && Math.abs(q.location.y - p.location.y) < 2.5);
    if (helpers.length) {
      mk.help += 1;
      if (mk.help % 4 === 1) {
        for (const q of helpers) {
          beam(u.dim, { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { x: q.location.x, y: q.location.y + 1, z: q.location.z }, VENOM, { half: 0.25, thick: 0.08, life: 0.25, gap: 0.4 });
          notify(q, `§a${p.name}님의 독을 빼는 중 §f${Math.min(99, Math.round((mk.help / d.need) * 100))}%`, 10);
        }
      }
      if (mk.help >= d.need) {
        mk.done = true;
        const q = helpers.sort((a, b) => hdist(a.location, p.location) - hdist(b.location, p.location))[0];
        bossHit(u, q, d.helper, { cause: EntityDamageCause.magic, noSource: true });
        showFaceText(p, "§a§l독이 빠졌습니다!", { dist: 2.2, up: 0.35 });
        showFaceText(q, "§a§l독을 빼 주었습니다 §7(조금 다침)", { dist: 2.2, up: 0.35 });
        fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 1.8, life: 0.4, color: SAFE, alpha: 0.9 });
        play(u.dim, "mob.zombie.remedy", p.location, 1, 1);
        say(u, `§a[보스] §e${q.name}§f님이 §e${p.name}§f님의 독을 빼 주었습니다`);
        continue;
      }
    }
    if (now >= mk.until) {
      mk.done = true;
      showFaceText(p, "§a§l독이 사라졌습니다", { dist: 2.2, up: 0.35 });
      continue;
    }
    // 표시: 발밑 초록 원(독을 빼 주는 곳), 남은 걸음 (액션바)
    if ((now - V.t0) % 10 === 0) disc(u.dim, floorAt(u, p.location.x, p.location.z), d.radius, 12, VENOM, { fill: false, alpha: 0.9 });
    const shown = Math.ceil(left);
    if (shown !== mk.shown || (now - V.t0) % 10 === 0) {
      mk.shown = shown;
      const col = shown <= 3 ? "§c" : shown <= 6 ? "§e" : "§a";
      const help = mk.help > 0 ? ` §7· §a독 빼는 중 ${Math.round((mk.help / d.need) * 100)}%` : "";
      notify(p, `§2아홉 걸음의 독 §f남은 걸음 ${col}§l${shown}§r §7(${Math.ceil((mk.until - now) / 20)}초)${help}`, 25);
    }
  }
  if (V.marks.every((mk) => mk.done)) {
    enc.mem.venom = undefined;
    enc.mem.bigUntil = Math.min(enc.mem.bigUntil, now + 60);
  }
}

// ------------------------------------------------------------ 세계를 감는 고리
/** 고리 (몸통 모형): 가운데에 세우고 틈이 g 쪽을 보게. 반지름은 속성 rpg:radius */
function spawnRing(u, g) {
  const c = u.center;
  let e;
  try {
    e = u.dim.spawnEntity(RING_ID, { x: c.x, y: c.y, z: c.z });
    u.api.addProp(u.enc, e);
    e.teleport({ x: c.x, y: c.y, z: c.z }, { facingLocation: { x: c.x + Math.cos(g) * 10, y: c.y, z: c.z + Math.sin(g) * 10 } });
    e.setProperty("rpg:radius", u.def.ring.start);
  } catch {
    // 무시
  }
  return e;
}
export const RING_ID = "rpg:prop_jorm_ring";

function ring(u, now, at) {
  const d = u.def.ring;
  const m = u.mem;
  const sw = m.swim;
  const half = (d.gap * Math.PI) / 360;
  const h = u.boss.location;
  const head = polarOf(u, h);
  // 틈: 머리 바로 앞 (머리가 틈의 한쪽 끝, 꼬리 끝이 반대쪽 끝)
  const g = wrap(head + sw.dir * half);
  m.bigUntil = Infinity; // 끝에서 풂
  // 고리가 시작될 때 싸우던 사람: 틈을 지나야만 고리 밖으로 나갈 수 있음 (in = 지난번에 본 자리가 몸 안쪽인지, at = 그 자리)
  const roster = new Map(fighters(u).map((p) => [p.id, { p, in: hdist(p.location, u.center) < d.start, at: { x: p.location.x, z: p.location.z }, passed: false }]));
  m.ring = { u, at, g, half, head, dir: sw.dir, r0: hdist(h, u.center), t0: now, startAt: now + d.windup, R: d.start, prop: spawnRing(u, g), roster, crushed: new Map() };
  u.api.anim(u, "ring", undefined, { controller: COIL, stopExpression: "0" });
  sound(u, u.def.sounds.roar, headAt(u), 0.5, 1);
  play(u.dim, "mob.elderguardian.curse", u.center, 0.7, 1);
  title(u, "§4§l세계를 감는 고리", "§f머리 앞 초록 틈으로 빠져나가세요! §7몸에 닿으면 즉사", fighters(u));
  explain(u, "ring", "§4[보스] 세계를 감는 고리: §f요르문간드가 몸으로 필드를 감싸고 조여 옵니다. §e머리 앞 초록 틈으로만 빠져나갈 수 있습니다 §7— 틈 밖에서 몸에 닿거나 끝까지 고리 안에 남으면 즉사");
  // 틈 (안전한 길): 가운데에서 틈 쪽 부채꼴
  fanMarks(u.dim, u.center, { x: Math.cos(g), y: 0, z: Math.sin(g) }, u.enc.def.arena.radius + 1, d.gap - 6, d.windup + Math.round(((d.start - d.end) / d.speed) * 20), SAFE, 2);
  u.api.cast(u, "ring", 100000, []);
}

/** 고리의 지금 반지름 */
function ringRadius(u, Rg, now) {
  const d = u.def.ring;
  if (now < Rg.startAt) return d.start;
  return Math.max(d.end, d.start - ((now - Rg.startAt) * d.speed) / 20);
}

/** 틈 안인지 (가운데 기준 각도, 가장자리 0.3칸 너그럽게) */
function inGap(u, Rg, l) {
  return Math.abs(wrapPi(polarOf(u, l) - Rg.g)) <= Rg.half + 0.3 / Math.max(1, hdist(l, u.center));
}

/** a → b 로 옮긴 사람이 가운데 c, 반지름 R 원(고리 몸)을 건넌 자리. 선분이 원과 만나지 않으면 (몸이 지나간 것) b */
function crossAt(c, a, b, R) {
  if (!a) return b;
  const ax = a.x - c.x;
  const az = a.z - c.z;
  const dx = b.x - a.x;
  const dz = b.z - a.z;
  const A = dx * dx + dz * dz;
  if (A < 1e-9) return b;
  const B = 2 * (ax * dx + az * dz);
  const disc = B * B - 4 * A * (ax * ax + az * az - R * R);
  if (disc < 0) return b;
  const q = Math.sqrt(disc);
  for (const t of [(-B - q) / (2 * A), (-B + q) / (2 * A)]) if (t >= 0 && t <= 1) return { x: a.x + dx * t, z: a.z + dz * t };
  return b;
}

/** 짓눌림 (즉사): 불사의 토템으로 살아나면 잠시 뒤 다시 셈 (영영 빠지지 않게) */
function crush(u, Rg, p, why) {
  const now = system.currentTick;
  if (now - (Rg.crushed.get(p.id) ?? -1000) < 20) return;
  Rg.crushed.set(p.id, now);
  const at = { ...p.location };
  splash(u.dim, at, 2.4, VENOM, 0.6);
  fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.6, life: 0.5, color: TEAL, alpha: 0.95 });
  play(u.dim, "random.anvil_land", at, 0.5, 0.8);
  say(u, `§4[보스] §e${p.name}§f님이 ${why}`);
  kill(p, u.boss);
}

function ringTick(enc, now) {
  const Rg = enc.mem.ring;
  if (!Rg) return;
  const u = Rg.u;
  const d = u.def.ring;
  const c = u.center;
  if (!u.alive || !u.boss?.isValid) {
    endRing(enc, Rg);
    return;
  }
  const R = ringRadius(u, Rg, now);
  Rg.R = R;
  // 머리: 틈의 한쪽 끝을 따라 (예고 동안 제자리에서 고리 자리로 나감), 틈 쪽(꼬리 끝)을 봄. 몸·꼬리는 고리를 따라 굽음
  const hr = now < Rg.startAt ? Rg.r0 + (d.start - Rg.r0) * Math.min(1, (now - Rg.t0) / Math.max(1, d.windup - 10)) : R;
  const sw = enc.mem.swim;
  sw.onCircle = false;
  setHead(u, { x: c.x + Math.cos(Rg.head) * hr, z: c.z + Math.sin(Rg.head) * hr }, Rg.head + (Rg.dir * Math.PI) / 2);
  sw.tailTo = polarOf(u, pivotOf(u)) + (Rg.dir * Math.PI) / 2 + Math.PI;
  sw.curveTo = curveFor(u, hr, Rg.dir);
  try {
    if (Rg.prop?.isValid) Rg.prop.setProperty("rpg:radius", Math.round(R * 100) / 100);
  } catch {
    // 무시
  }
  if (now < Rg.startAt) {
    rosterTick(u, Rg, R, true);
    const left = Rg.startAt - now;
    if (left % 20 === 0 && left <= 60) faceAll(u, `§4§l고리가 조여 옵니다 §f${left / 20}`, { dist: 2.4, up: 0.45 }, fighters(u));
    return;
  }
  if (now === Rg.startAt) {
    play(u.dim, "mob.enderdragon.growl", c, 0.5, 1);
    shakeNear(u, c, enc.def.arena.radius + 8, 0.25, 0.8);
  }
  // 조이는 몸: 흙먼지·물보라 (틈 밖 몇 곳)
  if ((now - Rg.startAt) % 4 === 0) {
    for (let i = 0; i < 5; i++) {
      const a = Rg.g + Rg.half + Math.random() * (TAU - 2 * Rg.half);
      const at = floorAt(u, c.x + Math.cos(a) * R, c.z + Math.sin(a) * R);
      (overMoat(u, at) ? splash : mist)(u.dim, at, 1.6, overMoat(u, at) ? FOAM : DUST, 0.5);
    }
  }
  if ((now - Rg.startAt) % 20 === 0) play(u.dim, "block.grindstone.use", c, 0.4, 0.8);
  rosterTick(u, Rg, R, false);
  // 나중에 들어온 사람 (명단 밖): 틈 밖에서 몸에 닿으면 즉사 (명단처럼 높이 상관없이)
  for (const p of targets(u)) {
    if (Rg.roster.has(p.id)) continue;
    const r = hdist(p.location, c);
    if (Math.abs(r - R) <= d.thick && !inGap(u, Rg, p.location)) crush(u, Rg, p, "세계를 감는 고리에 짓눌렸습니다");
  }
  if (R > d.end) return;
  // 다 조임: 고리 안에 남은 사람은 즉사
  for (const p of targets(u)) {
    if (hdist(p.location, c) < d.end + d.thick) crush(u, Rg, p, "고리 안에 갇혀 짓눌렸습니다");
  }
  splash(u.dim, c, 3.2, VENOM, 0.8);
  fx(u.dim, "rpg:shock_ring", { x: c.x, y: c.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: 4, life: 0.7, color: TEAL, alpha: 0.9 });
  play(u.dim, "random.explode", c, 0.6, 0.8);
  endRing(enc, Rg);
  // 해자로 돌아가 다시 돎 (틈 쪽에서)
  sw.dir = Rg.dir;
  setOnCircle(u, Rg.g);
  u.faceTo = sw.byaw;
  u.api.anim(u, "emerge", undefined, { controller: COIL });
  splash(u.dim, pivotOf(u), 3, FOAM, 0.8);
  enc.mem.bigUntil = now + 100;
  if (u.cast?.id === "ring") u.cast.until = now + 20;
}

/**
 * 고리가 시작될 때 싸우던 사람 (명단): 틈 밖에서 몸(두께 ±thick)에 닿거나 몸을 건너면 (한 틱에 건너뛰어도, 건넌 자리로 셈) 즉사.
 * 틈에서 몸을 건너면 빠져나간 것, 틈을 지나지 않고 고리 밖에 있으면 (성문으로 나가는 등) 즉사. 높이는 보지 않음 (탑 위도).
 * 예고 동안(warm)에는 죽이지 않음: 틈으로 나간 것(틈에서 몸을 건넜거나 틈 쪽 몸 바깥에 있음)은 받아 두고, 몸 안쪽에 있으면 지금 자리를 기준으로 삼음
 * — 틈 밖으로 건너간 것은 조이기 시작할 때 셈 (그 전에 돌아오면 없던 일)
 * 쓰러지는 중(불사의 토템으로 살아날 수 있음)에는 건너뛰고 명단에 남김, 죽으면 뺌. 나갔다 다시 들어오면 (같은 id 의 새 플레이어) 이어서 셈
 */
function rosterTick(u, Rg, R, warm) {
  const d = u.def.ring;
  const c = u.center;
  for (const [id, e] of Rg.roster) {
    if (!e.p.isValid) {
      const back = targets(u).find((q) => q.id === id);
      if (!back) continue;
      e.p = back;
    }
    const p = e.p;
    if (hpState(p).awaitRespawn) {
      Rg.roster.delete(id); // 죽은 사람은 다시 태어난 곳까지 쫓지 않음
      continue;
    }
    if (!okTarget(p)) continue;
    const at = { x: p.location.x, z: p.location.z };
    const r = hdist(at, c);
    const cross = e.in !== (r < R);
    const gap = inGap(u, Rg, cross ? crossAt(c, e.at, at, R) : at);
    const out = r > R + d.thick;
    if (warm) {
      const passed = gap && (cross || out);
      if (passed) e.passed = true;
      if (passed || r < R) {
        e.in = r < R;
        e.at = at;
      }
      continue;
    }
    const touch = cross || Math.abs(r - R) <= d.thick;
    e.in = r < R;
    e.at = at;
    if (gap) {
      if (touch || out) e.passed = true;
    } else if (touch || (out && !e.passed)) {
      crush(u, Rg, p, out ? "틈을 지나지 않고 고리 밖으로 나가려다 짓눌렸습니다" : "세계를 감는 고리에 짓눌렸습니다");
    }
  }
}

function endRing(enc, Rg) {
  enc.mem.ring = undefined;
  if (Rg.prop) enc.api.dropProp(enc, Rg.prop);
}

// ------------------------------------------------------------ 독의 해자 · 높은 곳
/** 해자 물에 들어가 있는 사람 (발이 바닥보다 낮음): 1초마다 마법 피해 */
function moatTick(enc, now) {
  if (now % 20 !== 7) return;
  const u = enc.units[0];
  if (!u.alive || !u.boss?.isValid) return;
  const { island, moat } = isleSize(enc.key);
  const c = enc.center;
  for (const p of targets(u)) {
    const r = hdist(p.location, c);
    if (r <= island + 0.2 || r >= moat + 0.3 || p.location.y > c.y - 0.4) continue;
    bossHit(u, p, u.def.moat.damage, { cause: EntityDamageCause.magic, noSource: true });
    mist(enc.dim, p.location, 1.2, VENOM, 0.5);
    if (!enc.mem.venom?.marks.some((mk) => !mk.done && mk.p.id === p.id)) notify(p, "§2독의 해자 §7— 물에 있으면 1초마다 독 피해", 30);
    explain(u, "moat", "§2[보스] 독의 해자: §f해자 물에 들어가 있으면 1초마다 독 피해를 받습니다");
  }
}

/** 성문·담장 탑 위처럼 높은 곳(바닥보다 high.above 칸 넘게)에 서 있는 사람: 꼬리로 쳐서 떨어뜨림 (사람마다 cd 틱에 한 번) */
function highTick(enc, now) {
  if (now % 10 !== 0 || enc.mem.ring) return;
  const u = enc.units[0];
  if (!u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const c = enc.center;
  for (const p of targets(u)) {
    if (p.isOnGround === false || p.location.y - c.y < d.above) continue;
    if (now - (enc.mem.high.get(p.id) ?? -1000) < d.cd) continue;
    enc.mem.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    splash(enc.dim, p.location, 1.8, FOAM, 0.6);
    play(enc.dim, "mob.enderdragon.flap", p.location, 0.7, 1);
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 } });
    calm(u, p);
    showFaceText(p, "§6§l꼬리에 맞아 떨어집니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", "§6[보스] §f성문·담장 탑 위처럼 높은 곳에 올라서면 요르문간드가 꼬리로 쳐서 떨어뜨립니다");
  }
}

// ------------------------------------------------------------ 모듈
export default {
  key: "jormungandr",
  propIds: [RING_ID],
  /** 원거리 견제 (v2.3.0): 세계를 감는 고리·아홉 걸음의 독(표식이 붙은 사람은 움직이면 안 됨)·독해 범람 동안은 쉼 */
  farOk(u) {
    return !u.mem.ring && !u.mem.venom && !u.mem.flood;
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    const c = enc.center;
    // 해자에서 떠오르며 등장: 깨운 사람 쪽 (없으면 남쪽)
    const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
    const ang = Math.atan2(p.z - c.z, p.x - c.x);
    const dir = Math.random() < 0.5 ? 1 : -1;
    enc.mem = {
      next: { bite: now + 30, dash: now + d.dash.first, tail: now + d.tail.first, flood: now + d.flood.first, venom: now + d.venom.first },
      bigUntil: now + 100,
      told: new Set(),
      swim: { ang, dir, px: c.x, pz: c.z, byaw: 0, nyaw: 0, tail: 0, tailTo: 0, curve: 0, curveTo: 0, sink: 1, sinkTo: 1, onCircle: true, charging: false, tailSent: undefined, curveSent: undefined, sinkSent: undefined },
      flood: undefined,
      floodDash: false,
      venom: undefined,
      ring: undefined,
      ringDone: new Set(),
      high: new Map(),
    };
    for (const u of enc.units) u.mem = enc.mem;
    // 엔진 평타는 쓰지 않음 (목을 돌리는 물어뜯기는 모듈이)
    enc.meleeAt = Infinity;
    setOnCircle(enc, ang);
    enc.faceTo = enc.mem.swim.byaw;
    bodyTick(enc);
    enc.api.anim(enc, "emerge", undefined, { controller: COIL });
    splash(enc.dim, pivotOf(enc), 3.2, FOAM, 0.9);
    play(enc.dim, "random.splash", pivotOf(enc), 0.6, 1);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    const r = u.api.ratio(u);
    if (m.ring || m.flood) return false;
    // 큰 패턴: 세계를 감는 고리 → 아홉 걸음의 독 → 독해 범람 (서로 겹치지 않음)
    if (!m.venom && now >= m.bigUntil) {
      const at = d.ring.at.find((x) => r <= x && !m.ringDone.has(x));
      if (at !== undefined) {
        m.ringDone.add(at);
        ring(u, now, at);
        return true;
      }
      if (r <= d.venom.from && now >= m.next.venom) {
        if (venomMark(u, now, list)) {
          m.next.venom = now + every(u, d.venom.every);
          return true;
        }
      }
      if (r <= d.flood.from && now >= m.next.flood) {
        m.next.flood = now + every(u, d.flood.every);
        flood(u, now);
        return true;
      }
    }
    if (now >= m.next.dash) {
      const center = m.floodDash;
      m.floodDash = false;
      if (dash(u, now, list, center)) {
        m.next.dash = now + every(u, d.dash.every);
        return true;
      }
      m.next.dash = now + 20;
    }
    if (now >= m.next.tail) {
      if (tailLash(u, now, list)) {
        m.next.tail = now + every(u, d.tail.every);
        return true;
      }
      m.next.tail = now + 30;
    }
    // 평타: 목이 닿는 사람을 물어뜯음
    if (now >= m.next.bite && bite(u, now, list)) {
      m.next.bite = now + Math.round(d.melee.every * (u.cdMult ?? 1));
      return true;
    }
    return false;
  },
  tick(enc, now) {
    const u = enc;
    if (!u.alive || !u.boss?.isValid || !enc.mem?.swim) return;
    ringTick(enc, now);
    floodTick(enc, now);
    venomTick(enc, now);
    moatTick(enc, now);
    highTick(enc, now);
    if (!enc.alive) return;
    // 몸: 고리 중에는 ringTick 이, 돌진 중에는 돌진 단계가 옮김. 다른 패턴 중에는 목만 돌리고, 패턴이 없으면 해자를 돎
    if (!enc.mem.ring && !enc.mem.swim.charging) {
      if (u.cast) neckTick(u);
      else swimTick(u, now);
    }
    bodyTick(u);
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.next) return;
    if (m.ring) endRing(enc, m.ring);
    m.ring = undefined;
    m.flood = undefined;
    m.venom = undefined;
  },
};
