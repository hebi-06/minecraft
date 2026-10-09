import { system } from "@minecraft/server";
import { discSide } from "./arena.js";
import { notify, play, showFaceText } from "./fx.js";
import { beam, fx, shake } from "./skills.js";
import { kill } from "./status.js";
import { bossHit, bossSize, disc, faceAll, fanMarks, fighters, groundUnder, hdist, inFan, meleeTarget, nearSegment, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 스콜과 하티 (v2.4.0, Lv.38): 수치는 config.js BOSSES.skoll_hati
 * 해를 쫓는 검은 늑대 스콜(몸 왼쪽 머리)과 달을 쫓는 흰 늑대 하티(몸 오른쪽 머리)가 한 몸인 머리 둘 늑대. 해와 달의 돌 고리에서 싸움.
 * 움직이지 않는 보스(중력·충돌 없음)를 모듈이 매 틱 옮김 (엔진 pose): 가장 가까운 사람 쪽으로 몸을 돌려 걷고, 뛰어들 때는 포물선으로 뜀.
 * 몸(엔티티 자리) = 몸통 가운데, 머리 = 몸 앞 drive.head 칸에서 옆으로 drive.side 칸 (왼쪽 스콜, 오른쪽 하티)
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작), rpg:day (1 = 낮: 스콜이 깨어 머리를 듦 · 0 = 밤: 하티), rpg:dusk (1 = 황혼: 둘 다),
 *   rpg:phase (0 낮 · 1 밤 · 2 황혼 → 텍스처: 깨어난 머리의 불씨·눈이 빛남)
 * - 낮과 밤 (stance): 낮에는 스콜, 밤에는 하티가 깨어 싸움 (every 틱마다 울부짖으며 바뀜). 잠든 머리 쪽 옆구리에서 준 피해 +weak (숫자 앞 "약점", 옆구리 바닥에 표시)
 * - 물어뜯기 (평타): 깨어난 머리가 몸 앞 부채꼴 (낮 = 불, 밤 = 느림, 황혼 = 대상 쪽 머리)
 * - 도약: 멀리(min 칸 넘게) 있는 사람 중 가장 먼 사람 발밑 원 → 뛰어들어 앞발로 내려찍음 (낮 = 불, 밤 = 느림, 황혼 = 둘 다)
 * - 태양 바퀴 (낮·황혼): 몸 둘레 inner 칸 밖으로 햇살 줄기가 뻗어 sweep° 돎 (쓸고 갈 곳을 먼저 보여 줌, 밝은 쪽에서 시작). 몸 곁·틈은 안전 (맞으면 불)
 * - 달빛 사냥 (밤·황혼): 한 사람을 사냥감으로 pounces 번 잇따라 덮침 (서리 자국: 밟으면 느림 + 피해) → 끝나면 지침 (받는 피해 +bonus, 숫자 앞 "지침")
 * - 해와 달의 심판 (체력 70%·25%, 즉사): 가장자리로 물러나 무적 → 사람마다 해·달 표식 → 가운데 원판의 제 색 반원(해 = 동쪽 금빛, 달 = 서쪽 푸른) 밖이면 즉사.
 *   두 번째는 도중에 표식이 뒤바뀜. 판결 뒤 가장 많이 모인 곳으로 도약
 * - 황혼 (체력 45% 아래): 두 머리가 모두 깨어남 — 약점이 사라지고 낮·밤 패턴을 모두 씀, 재사용 대기 × dusk.cd
 * - 높은 곳 (선돌·성문 위): 뛰어올라 끌어내림. 원거리 견제: 낮 = 태양 불씨 (불), 밤 = 달 서리 (느림), 황혼 = 번갈아 (엔진 farStyle)
 * 큰 패턴(심판)과 낮밤 바뀜은 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기를 모듈이)
 */

const SUN = [1.0, 0.8, 0.3];
const EMBER = [1.0, 0.5, 0.12];
/** 해 예고 (눈밭·금빛 반원에서 보이게 짙은 주황) */
const SUN_MARK = [1.0, 0.42, 0.08];
const MOON = [0.62, 0.86, 1.0];
/** 달 예고 (눈밭에서 보이게 짙은 파랑) */
const NIGHT = [0.28, 0.5, 1.0];
const FROST = [0.8, 0.94, 1.0];
const DUSK = [0.78, 0.4, 0.95];
const RED = [1.0, 0.22, 0.14];
const SAFE = [0.4, 1.0, 0.5];
const SNOW = [0.93, 0.95, 1.0];
const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

/** 피해 숫자 앞 표시: 약점 옆구리 · 지친 동안 (근접 보너스 » 앞) */
export const WEAK_MARK = "§e약점 ";
export const WINDED_MARK = "§b지침 ";
/** 동작 틀: 뛰어오르기(pounce·hunt·leap)는 내려앉기(land)가 이어받을 때까지 마지막 모습 유지 */
export const LEAP = "rpg_leap";
/** 동작 틀: 심판의 울부짖음은 판결까지 유지 */
export const HOWL = "rpg_howl";
/** 등장: 선돌 위에서 howl 틱 울부짖고 → time 틱 동안 가운데로 뛰어내림 (높이 height) */
export const ARRIVE = { howl: 20, time: 20, height: 3 };

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
/** 몸 오른쪽 단위 방향 (몸 방향 yaw 에서 오른쪽 90°: 하티 쪽). +x 동쪽·+z 남쪽에서 북쪽(-z)을 보면 오른쪽은 동쪽 */
export function rightOf(yaw) {
  return { x: -Math.sin(yaw), y: 0, z: Math.cos(yaw) };
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

/** 눈보라 먼지 (rpg:dust) */
function dust(dim, at, size, color = SNOW, life = 0.8) {
  fx(dim, "rpg:dust", { x: at.x, y: at.y + size * 0.35, z: at.z }, { size, life, color, alpha: 0.8 });
}

function burn(p, seconds) {
  if (!(seconds > 0)) return;
  try {
    p.setOnFire(seconds, true);
  } catch {
    // 무시
  }
}

/**
 * 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖·선돌 위로 날아가지 않게)
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

// ------------------------------------------------------------ 낮과 밤
/** 깨어 있는 머리: "sun" (낮: 스콜, 몸 왼쪽) | "moon" (밤: 하티, 몸 오른쪽) | "both" (황혼) */
export function awake(u) {
  const m = u.mem;
  return m.dusk ? "both" : m.stance.mode === "day" ? "sun" : "moon";
}

/** 약점 옆구리 쪽: +1 = 몸 오른쪽 (낮: 하티가 잠듦), -1 = 왼쪽 (밤: 스콜이 잠듦), 0 = 없음 (황혼) */
export function weakSide(u) {
  const m = u.mem;
  if (m.dusk) return 0;
  return m.stance.mode === "day" ? 1 : -1;
}

/** l 이 약점 옆구리 쪽인지: 잠든 머리 쪽이고 몸 방향에서 stance.flank[0]°~[1]° */
export function onFlank(u, l) {
  const side = weakSide(u);
  if (!side) return false;
  const b = u.mem.body;
  const dx = l.x - b.x;
  const dz = l.z - b.z;
  const d = Math.hypot(dx, dz);
  if (d < 0.5) return false;
  const f = dirOf(b.yaw);
  const r = rightOf(b.yaw);
  const ang = Math.acos(clamp((dx * f.x + dz * f.z) / d, -1, 1)) / DEG;
  const lat = (dx * r.x + dz * r.z) * side;
  const [lo, hi] = u.def.stance.flank;
  return lat > 0 && ang >= lo && ang <= hi;
}

/** 지금 색 (예고·연출): 낮 = 주황, 밤 = 파랑, 황혼 = 보라 */
function stanceColor(u) {
  const h = awake(u);
  return h === "sun" ? SUN_MARK : h === "moon" ? NIGHT : DUSK;
}

// ------------------------------------------------------------ 몸
/** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h */
export function bodyOf(u) {
  const b = u.mem.body;
  return { x: b.x, y: u.center.y + b.h, z: b.z };
}

/**
 * 머리 (연출·던지는 곳, 입 높이): which = "sun" 왼쪽 스콜 | "moon" 오른쪽 하티 (그 밖 = 두 머리 사이)
 * @param {any} u
 * @param {string} [which]
 */
export function headAt(u, which) {
  const b = u.mem.body;
  const D = u.def.drive;
  const s = which === "moon" ? 1 : which === "sun" ? -1 : 0;
  const r = rightOf(b.yaw);
  return { x: b.x + Math.cos(b.yaw) * D.head + r.x * D.side * s, y: u.center.y + b.h + 3.6, z: b.z + Math.sin(b.yaw) * D.head + r.z * D.side * s };
}

/** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 teleport 하지 않게. 밀려난 것은 엔진이 10틱마다 되돌림) */
function place(u) {
  const b = u.mem.body;
  const key = `${b.x},${b.z},${b.h},${b.yaw}`;
  if (b.placed === key && u.boss?.isValid) return;
  b.placed = key;
  u.api.pose(u, { x: b.x, y: u.center.y + b.h, z: b.z }, b.yaw);
}

/** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게, 땅 위. 시험·미리보기도 씀 */
export function setBody(u, x, z, yaw) {
  const b = u.mem.body;
  const c = u.center;
  const K = u.def.drive.keep;
  const r = Math.hypot(x - c.x, z - c.z);
  b.x = r > K ? c.x + ((x - c.x) / r) * K : x;
  b.z = r > K ? c.z + ((z - c.z) / r) * K : z;
  b.yaw = yaw;
  b.h = 0;
  place(u);
}

/** 속성 보내기: step 넘게 바뀌었거나 끝값(0·1)에 닿았을 때만 */
function setProp(u, id, v, last, step = 0.05) {
  if (last !== undefined && (v === last || (Math.abs(v - last) < step - 1e-9 && v !== 0 && v !== 1))) return last;
  try {
    u.boss.setProperty(id, v);
  } catch {
    return last;
  }
  return v;
}

/** 매 틱: 걷는 빠르기·낮밤·황혼을 목표 쪽으로 (낮밤은 1초, 황혼은 2초에 걸쳐 머리를 들고 내림), 속성 보내기 */
function bodyTick(u) {
  const b = u.mem.body;
  const L = u.mem.look;
  const toward = (v, to, s) => (Math.abs(to - v) <= s ? to : v + Math.sign(to - v) * s);
  b.gait = toward(b.gait, b.gaitTo, 0.15);
  L.day = toward(L.day, L.dayTo, 0.05);
  L.dusk = toward(L.dusk, L.duskTo, 0.025);
  const r2 = (v) => Math.round(clamp(v, 0, 1) * 100) / 100;
  b.gaitSent = setProp(u, "rpg:gait", r2(b.gait), b.gaitSent);
  L.daySent = setProp(u, "rpg:day", r2(L.day), L.daySent);
  L.duskSent = setProp(u, "rpg:dusk", r2(L.dusk), L.duskSent);
  // 텍스처: 깨어난 머리의 불씨(스콜)·눈(하티)이 빛남 — 머리를 반쯤 들었을 때 바뀜
  const phase = L.dusk >= 0.5 ? 2 : L.day >= 0.5 ? 0 : 1;
  if (phase !== L.phaseSent) {
    try {
      u.boss.setProperty("rpg:phase", phase);
      L.phaseSent = phase;
    } catch {
      // 무시
    }
  }
}

/** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음 — 뛰어들어 밖에 있으면 안쪽으로만) */
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
      const lim = Math.max(D.keep, Math.hypot(b.x - c.x, b.z - c.z));
      if (r > lim) {
        nx = c.x + ((nx - c.x) / r) * lim;
        nz = c.z + ((nz - c.z) / r) * lim;
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
  if (b.gaitTo > 0 && system.currentTick % 10 === 0) play(u.dim, u.def.sounds.step, bodyOf(u), 0.5, 1);
}

/** 패턴 동안: 원하는 쪽(body.want)이 있으면 그쪽으로 몸을 돌림 (제자리) */
function standTick(u) {
  const b = u.mem.body;
  b.gaitTo = 0;
  if (b.want !== undefined) b.yaw = turnToward(b.yaw, b.want, u.def.drive.turn * DEG);
  place(u);
}

/** l 이 몸 방향에서 몇 도 떨어져 있는지 (라디안, 0 ~ π) */
function offAngle(u, l) {
  const b = u.mem.body;
  return Math.abs(wrapPi(angOf(dirTo(b, l)) - b.yaw));
}

// ------------------------------------------------------------ 뛰기 (도약·사냥·등장·물러남)
/**
 * 뛰어들 곳: 대상 발밑(spot)에 앞발(몸 앞 drive.paw 칸)이 닿게 몸은 그만큼 뒤 (가운데에서 drive.leap 칸 안).
 * 몸을 안쪽으로 당기면 앞발 자리(원·피해)도 그만큼 안쪽. 시험도 씀
 * @returns {{to: {x: number, z: number}, spot: {x: number, y: number, z: number}, yaw: number}}
 */
export function leapPlan(u, spot) {
  const D = u.def.drive;
  const b = u.mem.body;
  const c = u.center;
  const dir = dirTo(b, spot);
  let bx = spot.x - dir.x * D.paw;
  let bz = spot.z - dir.z * D.paw;
  const r = Math.hypot(bx - c.x, bz - c.z);
  const pulled = r > D.leap;
  if (pulled) {
    bx = c.x + ((bx - c.x) / r) * D.leap;
    bz = c.z + ((bz - c.z) / r) * D.leap;
  }
  const at = pulled ? { x: bx + dir.x * D.paw, y: spot.y, z: bz + dir.z * D.paw } : { x: spot.x, y: spot.y, z: spot.z };
  return { to: { x: bx, z: bz }, spot: at, yaw: angOf(dir) };
}

/** 뛰기 시작: time 틱 동안 포물선 (높이 height) 으로 P.to 까지 (몸은 leapTick 이 옮김) */
function jump(u, P, time, height) {
  const b = u.mem.body;
  u.mem.leap = { x0: b.x, z0: b.z, h0: b.h, x1: P.to.x, z1: P.to.z, t0: system.currentTick, time: Math.max(1, time), height, yaw: P.yaw };
  b.want = undefined;
  play(u.dim, u.def.sounds.swing, bodyOf(u), 0.7, 1);
  if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 2.6, SNOW, 0.8);
}

/** 뛰는 중: 몸을 포물선으로 옮김 (닿으면 땅 위) */
function leapTick(u, now) {
  const L = u.mem.leap;
  if (!L) return;
  const b = u.mem.body;
  const k = clamp((now - L.t0) / L.time, 0, 1);
  b.x = L.x0 + (L.x1 - L.x0) * k;
  b.z = L.z0 + (L.z1 - L.z0) * k;
  b.h = L.h0 * (1 - k) + L.height * 4 * k * (1 - k);
  b.yaw = turnToward(b.yaw, L.yaw, 20 * DEG);
  b.gaitTo = 0;
  if (k >= 1) {
    u.mem.leap = undefined;
    b.h = 0;
  }
  place(u);
}

/** 내려앉은 자리 연출 */
function landFx(u, at, radius, color) {
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life: 0.6, color, alpha: 0.95 });
  fx(u.dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2, life: 1.2, color: [0.34, 0.33, 0.38], alpha: 0.85 });
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    dust(u.dim, { x: at.x + Math.cos(a) * radius * 0.7, y: at.y, z: at.z + Math.sin(a) * radius * 0.7 }, 2, SNOW, 0.9);
  }
  play(u.dim, "mob.ravager.step", at, 0.6, 1);
  play(u.dim, "dig.snow", at, 0.7, 1);
  shakeNear(u, at, 16, 0.3, 0.6);
}

// ------------------------------------------------------------ 물어뜯기 (평타)
/** 무는 머리: 깨어난 머리 (황혼이면 대상 쪽 머리) */
function biter(u, l) {
  const h = awake(u);
  if (h !== "both") return h;
  const b = u.mem.body;
  const r = rightOf(b.yaw);
  return (l.x - b.x) * r.x + (l.z - b.z) * r.z >= 0 ? "moon" : "sun";
}

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
  const head = biter(u, t.location);
  u.mem.body.want = angOf(dir);
  fanMarks(u.dim, apex, dir, Math.round(reach), m.cone, m.windup, head === "sun" ? SUN_MARK : NIGHT);
  u.api.anim(u, `bite_${head}`);
  sound(u, u.def.sounds.swing, headAt(u, head), 0.8, 1);
  u.api.cast(u, "bite", m.windup + 6, [[m.windup, (x) => biteHit(x, apex, dir, reach, head)]]);
  return true;
}

function biteHit(u, apex, dir, radius, head) {
  const m = u.def.melee;
  for (const p of targets(u)) {
    if (Math.abs(p.location.y - apex.y) > 4 || !inFan(p.location, apex, dir, radius, m.cone, 0.5)) continue;
    const kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
    const landed = bossHit(u, p, m.damage, { knock: { x: kk.x, z: kk.z, h: 0.35 }, directional: true, effect: head === "moon" ? { id: "slowness", ticks: m.slow, amp: 0 } : undefined });
    if (landed && head === "sun") burn(p, m.burn);
  }
  const h = headAt(u, head);
  sound(u, u.def.sounds.hit, h, 0.9, 1);
  fx(u.dim, "rpg:slash_heavy_r_glow", { x: h.x, y: h.y - 1.4, z: h.z }, { dir, speed: 0, spin: 0, size: 3.0, life: 0.25, color: head === "sun" ? EMBER : MOON, alpha: 0.95 });
}

// ------------------------------------------------------------ 도약
function pounce(u, now, list) {
  const d = u.def.pounce;
  const b = bodyOf(u);
  const cand = list.filter((p) => {
    const r = hdist(p.location, b);
    return r > d.min && r <= d.range && Math.abs(p.location.y - u.center.y) < 4;
  });
  if (!cand.length) return false;
  cand.sort((x, y) => hdist(y.location, b) - hdist(x.location, b));
  const t = cand[0];
  const P = leapPlan(u, { x: t.location.x, y: groundUnder(t, u.center.y), z: t.location.z });
  startPounce(u, P);
  explain(u, "pounce", "§6[보스] 도약: §f멀리 떨어진 사람 발밑에 원이 뜨면 스콜과 하티가 뛰어들어 앞발로 내려찍습니다 — 원 밖으로 §7(낮 = 불, 밤 = 느림)");
  return true;
}

/** 도약 한 번 (틀): 앞발 자리 원 windup 틱 → time 틱 동안 뛰어듦 → 원 안 피해 (심판 뒤의 도약도) */
function startPounce(u, P) {
  const d = u.def.pounce;
  disc(u.dim, P.spot, d.radius, d.windup, stanceColor(u));
  u.mem.body.want = P.yaw;
  u.api.anim(u, "pounce", undefined, { controller: LEAP, stopExpression: "0" });
  sound(u, u.def.sounds.roar, headAt(u, awake(u) === "moon" ? "moon" : "sun"), 1.0, 0.8);
  u.api.cast(u, "pounce", d.windup + d.time + 8, [
    [d.windup, (x) => jump(x, P, d.time, d.height)],
    [d.windup + d.time, (x) => pounceHit(x, P.spot)],
  ]);
}

function pounceHit(u, spot) {
  const d = u.def.pounce;
  const head = awake(u);
  for (const p of targets(u)) {
    if (hdist(p.location, spot) > d.radius + 0.3 || Math.abs(p.location.y - spot.y) > 3) continue;
    const dir = dirTo(spot, p.location);
    const kk = keepIn(u, p, dir.x * 0.8, dir.z * 0.8);
    const landed = bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: d.lift }, effect: head !== "sun" ? { id: "slowness", ticks: d.slow, amp: 0 } : undefined });
    if (landed && head !== "moon") burn(p, d.burn);
  }
  landFx(u, spot, d.radius, stanceColor(u));
  u.api.anim(u, "land", undefined, { controller: LEAP });
}

// ------------------------------------------------------------ 태양 바퀴 (낮·황혼)
/** 몸(at)에서 방향 v 로 뻗은 햇살이 필드 가장자리(반지름 + 0.5)까지 닿는 길이 (range 까지) */
export function rayEnd(u, at, v, range) {
  const R = u.enc.def.arena.radius + 0.5;
  const wx = at.x - u.center.x;
  const wz = at.z - u.center.z;
  const bd = wx * v.x + wz * v.z;
  const q = bd * bd - (wx * wx + wz * wz - R * R);
  const t = q > 0 ? -bd + Math.sqrt(q) : 0;
  return Math.max(0, Math.min(range, t));
}

/** 줄기 j 의 k 틱째 각도 */
function rayAngle(u, W, j, k) {
  return W.a0 + (j * TAU) / W.n + W.sgn * W.sweep * clamp(k / u.def.wheel.time, 0, 1);
}

/** 줄기 하나 (몸 둘레 r0 칸 → 가장자리): 시작·끝 */
export function raySegment(u, W, a) {
  const v = dirOf(a);
  const r1 = Math.max(W.r0, rayEnd(u, W.at, v, u.def.wheel.range));
  return { v, from: { x: W.at.x + v.x * W.r0, y: W.at.y, z: W.at.z + v.z * W.r0 }, to: { x: W.at.x + v.x * r1, y: W.at.y, z: W.at.z + v.z * r1 } };
}

/** 햇살 줄기 그림 (가슴 높이의 빛 띠) */
function drawRay(u, seg, life, alpha, thick = 0.9) {
  const L = hdist(seg.from, seg.to);
  if (L < 0.5) return;
  const n = Math.max(1, Math.round(L / 3.6));
  const half = L / n / 2;
  for (let i = 0; i < n; i++) {
    const s = (i + 0.5) * (L / n);
    fx(u.dim, "rpg:arrow_trail", { x: seg.from.x + seg.v.x * s, y: seg.from.y + 1.0, z: seg.from.z + seg.v.z * s }, { dir: seg.v, size: half * 1.05, thick, life, color: SUN, alpha });
  }
}

/** 예고: 줄기마다 쓸고 갈 부채꼴 (시작하는 줄은 밝게) + 몸 곁 안전 원 (초록) */
function wheelMarks(u, W, ticks) {
  const d = u.def.wheel;
  const life = Math.max(0.1, ticks / 20);
  // 격자 간격 2.4칸 (판도 그만큼 크게): 한 틱에 뿌리는 판을 200개 안으로
  const step = 2.4;
  const R = u.enc.def.arena.radius + 0.5;
  for (let j = 0; j < W.n; j++) {
    const a0 = rayAngle(u, W, j, 0);
    const a1 = rayAngle(u, W, j, d.time);
    const lo = Math.min(a0, a1);
    const hi = Math.max(a0, a1);
    for (let r = W.r0 + step / 2; r <= d.range; r += step) {
      const n = Math.max(1, Math.ceil(((hi - lo) * r) / step));
      for (let i = 0; i <= n; i++) {
        const a = lo + ((hi - lo) * i) / n;
        const at = { x: W.at.x + Math.cos(a) * r, y: W.at.y, z: W.at.z + Math.sin(a) * r };
        if (hdist(at, u.center) > R) continue;
        const start = W.sgn > 0 ? i === 0 : i === n;
        fx(u.dim, "rpg:tele_fill", { x: at.x, y: at.y + 0.07, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: step * 0.8, life, color: SUN_MARK, alpha: start ? 0.85 : 0.4, grow: 1 });
      }
    }
    drawRay(u, raySegment(u, W, a0), Math.max(0.1, d.windup / 20), 0.45, 0.35);
  }
  disc(u.dim, W.at, W.r0, ticks, SAFE, { fill: false, alpha: 0.95 });
}

function wheel(u, now, list) {
  const d = u.def.wheel;
  const m = u.mem;
  const b = bodyOf(u);
  if (!list.some((p) => hdist(p.location, b) <= d.range + 2)) return false;
  const n = m.dusk ? d.duskBeams : d.beams;
  const W = {
    at: floorAt(u, b.x, b.z),
    a0: Math.random() * TAU,
    n,
    sweep: (m.dusk ? d.duskSweep : d.sweep) * DEG,
    sgn: Math.random() < 0.5 ? 1 : -1,
    r0: bossSize(u.def).half + d.inner,
    hit: new Set(),
  };
  m.wheel = W;
  m.body.want = undefined;
  wheelMarks(u, W, d.windup + d.time);
  u.api.anim(u, "wheel");
  // 몸 위에 떠오르는 해
  fx(u.dim, "rpg:skill_rune", { x: b.x, y: u.center.y + 7, z: b.z }, { dir: { x: 0, y: 1, z: 0 }, size: 2.6, life: (d.windup + d.time) / 20, spin: 0, speed: W.sgn * 60, grow: 0, progress: 1, color: SUN, alpha: 0.9 });
  sound(u, "beacon.activate", b, 0.7, 1.2);
  sound(u, u.def.sounds.howl, headAt(u, "sun"), 0.7, 1);
  faceAll(u, "§6§l태양 바퀴! §f몸 곁이나 주황 부채꼴 사이로", { dist: 2.4, up: 0.45 }, list);
  explain(u, "wheel", `§6[보스] 태양 바퀴: §f스콜이 해를 불러 몸에서 햇살 줄기 ${n}개를 뻗어 돌립니다 (주황 부채꼴 = 쓸고 갈 곳, 밝은 줄에서 시작). §e몸 바로 곁(초록 원)이나 부채꼴 사이는 안전 §7(맞으면 불)`);
  /** @type {[number, (x: any) => void][]} */
  const steps = [[d.windup, (x) => wheelStart(x)]];
  for (let k = 0; k <= d.time; k++) steps.push([d.windup + k, (x) => wheelStep(x, W, k)]);
  steps.push([d.windup + d.time + 1, (x) => (x.mem.wheel = undefined)]);
  u.api.cast(u, "wheel", d.windup + d.time + 8, steps);
  // 바로 이어서 달빛 사냥이 나오지 않게
  m.next.hunt = Math.max(m.next.hunt, now + d.windup + d.time + 60);
  return true;
}

function wheelStart(u) {
  const b = bodyOf(u);
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: u.mem.wheel?.r0 ?? 4, life: 0.5, color: SUN, alpha: 0.95 });
  play(u.dim, "mob.blaze.shoot", b, 0.5, 1);
  play(u.dim, "fire.ignite", b, 0.7, 1);
  shakeNear(u, b, 22, 0.15, 0.6);
}

function wheelStep(u, W, k) {
  const d = u.def.wheel;
  for (let j = 0; j < W.n; j++) {
    const seg = raySegment(u, W, rayAngle(u, W, j, k));
    if (k % 2 === 0) drawRay(u, seg, 0.15, 0.95);
    if (k % 4 === 0) fx(u.dim, "rpg:dust", { x: seg.to.x, y: seg.to.y + 0.6, z: seg.to.z }, { size: 1.2, life: 0.5, color: EMBER, alpha: 0.85 });
    for (const p of targets(u)) {
      const key = `${j}|${p.id}`;
      // 몸 곁 (안전한 초록 원 안)은 줄기 끝에 닿아도 안전
      if (W.hit.has(key) || Math.abs(p.location.y - W.at.y) > 3 || hdist(p.location, W.at) < W.r0 || !nearSegment(p.location, seg.from, seg.to, d.width / 2)) continue;
      W.hit.add(key);
      // 때린 엔티티 없이 (보스 쪽에서 밀어내지 않음), 방어구로 줄어드는 피해 (마법 피해가 아님)
      const landed = bossHit(u, p, d.damage, { noSource: true });
      if (landed) {
        burn(p, d.burn);
        showFaceText(p, "§6§l햇살에 데었습니다", { dist: 2.2, up: 0.35 });
      }
    }
  }
  if (k % 8 === 0) play(u.dim, "mob.blaze.breathe", bodyOf(u), 0.6, 0.8);
}

// ------------------------------------------------------------ 달빛 사냥 (밤·황혼)
function hunt(u, now, list) {
  const d = u.def.hunt;
  const b = bodyOf(u);
  const level = (p) => Math.abs(p.location.y - u.center.y) < 4;
  const far = list.filter((p) => level(p) && hdist(p.location, b) > d.min);
  const pool = far.length ? far : list.filter(level);
  if (!pool.length) return false;
  const prey = shuffle(pool)[0];
  const H = { prey, n: 0, plan: undefined };
  u.mem.hunt = H;
  u.mem.body.want = angOf(dirTo(b, prey.location));
  u.api.anim(u, "howl");
  sound(u, u.def.sounds.howl, headAt(u, "moon"), 0.9, 1.2);
  title(u, "§b§l달빛 사냥감!", `§f하티가 ${d.pounces}번 잇따라 덮칩니다 §7— 푸른 원 밖으로 계속 움직이세요`, [prey]);
  faceAll(u, `§b${prey.name}§f님이 사냥감입니다`, { dist: 2.4, up: 0.45 }, list.filter((p) => p.id !== prey.id));
  explain(u, "hunt", `§b[보스] 달빛 사냥: §f하티가 한 사람을 사냥감으로 정해 ${d.pounces}번 잇따라 덮칩니다 (푸른 원). 내려앉은 자리에는 서리가 남아 밟으면 느려지고 아픕니다. §e사냥이 끝나면 ${Math.round(d.winded / 20)}초 동안 지쳐 받는 피해 +${Math.round(d.bonus * 100)}%`);
  const lead = 16;
  const cycle = d.windup + d.time + 6;
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let i = 0; i < d.pounces; i++) {
    const t0 = lead + i * cycle;
    steps.push([t0, (x) => huntAim(x, H)]);
    steps.push([t0 + d.windup, (x) => H.plan && jump(x, H.plan, d.time, d.height)]);
    steps.push([t0 + d.windup + d.time, (x) => huntHit(x, H)]);
  }
  const end = lead + d.pounces * cycle;
  steps.push([end, (x) => winded(x)]);
  u.api.cast(u, "hunt", end + d.winded, steps);
  // 바로 이어서 태양 바퀴가 나오지 않게
  u.mem.next.wheel = Math.max(u.mem.next.wheel, now + end + d.winded + 60);
  return true;
}

/** 사냥감이 쓰러지거나 나가면 남은 사람 중 가장 가까운 사람으로 */
function preyOf(u, H) {
  const list = targets(u).filter((p) => Math.abs(p.location.y - u.center.y) < 4);
  if (H.prey?.isValid && okTarget(H.prey) && list.some((p) => p.id === H.prey.id)) return H.prey;
  const b = bodyOf(u);
  let best;
  let bd = Infinity;
  for (const p of list) {
    const r = hdist(p.location, b);
    if (r < bd) {
      bd = r;
      best = p;
    }
  }
  H.prey = best;
  return best;
}

function huntAim(u, H) {
  const d = u.def.hunt;
  const p = preyOf(u, H);
  if (!p) {
    H.plan = undefined;
    return;
  }
  H.plan = leapPlan(u, { x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z });
  disc(u.dim, H.plan.spot, d.radius, d.windup, NIGHT);
  fx(u.dim, "rpg:skill_rune", { x: p.location.x, y: p.location.y + 2.6, z: p.location.z }, { dir: { x: 0, y: 1, z: 0 }, size: 1.1, life: d.windup / 20, color: MOON, alpha: 0.9 });
  u.mem.body.want = H.plan.yaw;
  u.api.anim(u, "hunt", undefined, { controller: LEAP, stopExpression: "0" });
  play(u.dim, u.def.sounds.roar, headAt(u, "moon"), 1.2, 0.7);
  showFaceText(p, `§b§l덮칩니다! §f(${H.n + 1}/${d.pounces})`, { dist: 2.4, up: 0.45 });
}

function huntHit(u, H) {
  const d = u.def.hunt;
  const P = H.plan;
  H.plan = undefined;
  H.n += 1;
  if (!P) return;
  const spot = P.spot;
  for (const p of targets(u)) {
    if (hdist(p.location, spot) > d.radius + 0.3 || Math.abs(p.location.y - spot.y) > 3) continue;
    const dir = dirTo(spot, p.location);
    const kk = keepIn(u, p, dir.x * 0.7, dir.z * 0.7);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.4 }, effect: { id: "slowness", ticks: d.slow, amp: 0 } });
  }
  landFx(u, spot, d.radius, NIGHT);
  frostPatch(u, spot);
  u.api.anim(u, "land", undefined, { controller: LEAP });
}

/** 사냥이 끝남: 지침 (받는 피해 +bonus, 틀이 끝날 때까지 공격 없음) */
function winded(u) {
  const d = u.def.hunt;
  const now = system.currentTick;
  u.mem.hunt = undefined;
  u.mem.windedUntil = now + d.winded;
  u.mem.body.want = undefined;
  u.api.anim(u, "winded");
  play(u.dim, u.def.sounds.pant, headAt(u), 0.7, 1.2);
  for (const w of ["sun", "moon"]) dust(u.dim, headAt(u, w), 1.1, FROST, 0.9);
  faceAll(u, `§b§l지쳤습니다! §f받는 피해 +${Math.round(d.bonus * 100)}%`, { dist: 2.4, up: 0.45 }, targets(u));
}

/** 서리 자국: life 틱 동안 남음, 1초마다 안의 사람: 느림 + 약한 피해 (자국이 겹쳐도 사람마다 1초에 한 번) */
function frostPatch(u, at) {
  const F = u.def.hunt.frost;
  const now = system.currentTick;
  u.mem.frost.push({ at: { x: at.x, y: at.y, z: at.z }, t0: now, until: now + F.life });
  const life = F.life / 20;
  fx(u.dim, "rpg:tele_disc", { x: at.x, y: at.y + 0.06, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: F.radius, life, color: NIGHT, alpha: 0.85 });
  fx(u.dim, "rpg:tele_fill", { x: at.x, y: at.y + 0.07, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: F.radius, life, color: FROST, alpha: 0.45, grow: 1 });
  play(u.dim, "random.glass", at, 0.8, 0.6);
}

function frostTick(u, now) {
  const F = u.def.hunt.frost;
  const list = u.mem.frost;
  for (let i = list.length - 1; i >= 0; i--) {
    const f = list[i];
    if (now >= f.until) {
      list.splice(i, 1);
      continue;
    }
    const k = now - f.t0;
    if (k % 10 === 5) {
      const a = Math.random() * TAU;
      const r = Math.random() * F.radius;
      fx(u.dim, "rpg:dust", { x: f.at.x + Math.cos(a) * r, y: f.at.y + 0.4, z: f.at.z + Math.sin(a) * r }, { size: 0.7, life: 0.8, color: FROST, alpha: 0.85 });
    }
    if (k === 0 || k % 20 !== 0) continue;
    for (const p of targets(u)) {
      if (hdist(p.location, f.at) > F.radius + 0.2 || Math.abs(p.location.y - f.at.y) > 2) continue;
      // 겹친 자국 (같은 자리에 두 번 덮침 등)도 사람마다 1초에 한 번
      if (now - (u.mem.frostHit.get(p.id) ?? -100) < 20) continue;
      u.mem.frostHit.set(p.id, now);
      bossHit(u, p, F.dmg, { noSource: true, effect: { id: "slowness", ticks: F.slow, amp: 0 } });
    }
  }
}

// ------------------------------------------------------------ 해와 달의 심판 (즉사: 원판의 제 색 반원)
const SIDE_NAME = { sun: "해", moon: "달" };
const SIDE_TEXT = { sun: "§6", moon: "§b" };

/** p 가 서 있는 원판 쪽 */
function sideOf(u, p) {
  return discSide(p.location, u.center, u.enc.def.arena.disc);
}

function judge(u, now, list, second) {
  const J = u.def.judge;
  const m = u.mem;
  const c = u.center;
  const b = m.body;
  const K = u.def.drive.keep;
  // 필드 북쪽 가장자리 (원판 경계선을 늘인 곳이라 해·달 반원에서 같은 거리). 늘 북쪽: 가운데(남쪽)를 보면 왼쪽 스콜(해)이 동쪽 해 반원 위,
  // 오른쪽 하티(달)가 서쪽 달 반원 위 — 머리 색이 반원 색과 맞음
  const E = { x: c.x, y: c.y, z: c.z - K };
  m.judge = { stage: "retreat", second, t0: now, until: Infinity, swapAt: undefined, swapped: false, marks: [], E };
  u.immune = true;
  b.want = undefined;
  u.api.anim(u, "leap", undefined, { controller: LEAP, stopExpression: "0" });
  jump(u, { to: { x: E.x, z: E.z }, spot: E, yaw: angOf(dirTo(b, E)) }, J.leap, 5);
  sound(u, u.def.sounds.roar, headAt(u), 0.7, 1.2);
  title(u, "§e§l해와 달의 심판", "§f스콜과 하티가 물러나 울부짖습니다", list);
  const markAt = J.leap + 6;
  /** @type {[number, (x: any) => void][]} */
  const steps = [
    [J.leap, (x) => judgeLand(x)],
    [markAt, (x) => judgeMark(x)],
    [markAt + J.windup, (x) => judgeVerdict(x)],
  ];
  if (second) steps.push([markAt + J.swapAt, (x) => judgeSwap(x)]);
  u.api.cast(u, "judge", markAt + J.windup + 2, steps);
}

function judgeLand(u) {
  const J = u.mem.judge;
  if (!J) return;
  landFx(u, floorAt(u, J.E.x, J.E.z), 3, SUN);
  u.api.anim(u, "land", undefined, { controller: LEAP });
  u.mem.body.want = angOf(dirTo(J.E, u.center));
}

function judgeMark(u) {
  const J = u.def.judge;
  const m = u.mem;
  const G = m.judge;
  if (!G) return;
  const now = system.currentTick;
  const first = Math.random() < 0.5 ? "sun" : "moon";
  const second = first === "sun" ? "moon" : "sun";
  const list = targets(u);
  pullIn(u, list);
  G.marks = shuffle(list).map((p, i) => ({ p, id: p.id, side: i % 2 === 0 ? first : second, done: false }));
  G.stage = "mark";
  G.t0 = now;
  G.until = now + J.windup;
  G.swapAt = G.second ? now + J.swapAt : undefined;
  u.api.anim(u, "judge", undefined, { controller: HOWL, stopExpression: "0" });
  sound(u, u.def.sounds.howl, headAt(u), 0.6, 1.4);
  play(u.dim, "mob.wither.spawn", u.center, 1.4, 0.4);
  const h = headAt(u);
  for (const mk of G.marks) {
    const l = mk.p.location;
    beam(u.dim, h, { x: l.x, y: l.y + 1.4, z: l.z }, mk.side === "sun" ? SUN : MOON, { half: 0.4, thick: 0.14, life: 0.6, gap: 0.6 });
    title(u, mk.side === "sun" ? "§6§l해의 표식" : "§b§l달의 표식", mk.side === "sun" ? "§f가운데 원판의 §6금빛 반원(동쪽)§f에 서세요" : "§f가운데 원판의 §b푸른 반원(서쪽)§f에 서세요", [mk.p]);
  }
  explain(u, "judge", `§e[보스] 해와 달의 심판: §f사람마다 해(금빛) 또는 달(푸른) 표식이 붙습니다. ${Math.round(J.windup / 20)}초 뒤 §c가운데 원판에서 제 표식 색 반원 위에 서 있지 않으면 즉사 §7(가운데 흰 줄·원판 밖도 즉사, 발밑 고리가 초록이면 안전). 스콜과 하티는 그동안 무적`);
  if (G.second) say(u, `§d[보스] §f두 번째 심판은 ${(J.swapAt / 20).toFixed(1)}초 뒤 §d표식이 뒤바뀝니다!`);
}

/**
 * 담장 밖(성문·둑·필드 바깥 띠)에서 싸우던 사람은 표식을 받기 전에 담장 안으로 끌려 들어옴 (선돌 안쪽, 제 방향 그대로):
 * 담장을 돌아 성문으로 들어오면 제 반원까지 제때 가지 못하므로
 */
function pullIn(u, list) {
  const R = u.enc.def.arena.radius;
  const c = u.center;
  const to = R - 4.5;
  for (const p of list) {
    const l = p.location;
    const r = hdist(l, c);
    if (r <= R + 0.3) continue;
    const at = { x: c.x + ((l.x - c.x) / r) * to, y: c.y, z: c.z + ((l.z - c.z) / r) * to };
    try {
      p.teleport(at, { facingLocation: { x: c.x, y: c.y + 1.6, z: c.z } });
    } catch {
      continue;
    }
    dust(u.dim, l, 1.6, MOON, 0.7);
    dust(u.dim, at, 1.6, SUN, 0.7);
    showFaceText(p, "§e§l심판에 끌려 들어왔습니다", { dist: 2.4, up: 0.45 });
  }
}

function judgeSwap(u) {
  const G = u.mem.judge;
  if (!G || G.stage !== "mark") return;
  G.swapped = true;
  const h = headAt(u);
  for (const mk of G.marks) {
    if (mk.done) continue;
    mk.side = mk.side === "sun" ? "moon" : "sun";
    if (!mk.p.isValid) continue;
    const l = mk.p.location;
    beam(u.dim, h, { x: l.x, y: l.y + 1.4, z: l.z }, DUSK, { half: 0.4, thick: 0.14, life: 0.6, gap: 0.6 });
    title(u, "§d§l표식이 뒤바뀝니다!", `${SIDE_TEXT[mk.side]}${SIDE_NAME[mk.side]}의 표식 §f→ ${mk.side === "sun" ? "§6금빛 반원(동쪽)" : "§b푸른 반원(서쪽)"}`, [mk.p]);
  }
  play(u.dim, "mob.evocation_illager.prepare_wololo", u.center, 0.8, 1.4);
  sound(u, u.def.sounds.howl, h, 1.1, 1.2);
}

/** 매 틱: 표식 (발밑 고리: 제 반원 위면 초록, 아니면 붉게 · 머리 위 해/달 문양 · 원판 가장자리 반원 · 남은 초) */
function judgeTick(u, now) {
  const G = u.mem.judge;
  if (!G || G.stage !== "mark") return;
  for (const mk of G.marks) if (!mk.done && (!mk.p.isValid || !okTarget(mk.p) || mk.p.dimension?.id !== u.dim.id || hdist(mk.p.location, u.center) > 64)) mk.done = true;
  const live = G.marks.filter((mk) => !mk.done);
  const k = now - G.t0;
  const left = G.until - now;
  if (k % 5 === 0) {
    for (const mk of live) {
      const safe = sideOf(u, mk.p) === mk.side;
      disc(u.dim, mk.p.location, 1.3, 5, safe ? SAFE : RED, { fill: false, alpha: 0.95 });
    }
  }
  if (k % 20 === 0) {
    for (const mk of live) fx(u.dim, "rpg:skill_rune", { x: mk.p.location.x, y: mk.p.location.y + 2.6, z: mk.p.location.z }, { dir: { x: 0, y: 1, z: 0 }, size: 1.2, life: 1.0, color: mk.side === "sun" ? SUN : MOON, alpha: 0.9 });
    // 원판 가장자리: 동쪽 반원 금빛, 서쪽 반원 푸른 빛
    const R = u.enc.def.arena.disc;
    const c = u.center;
    for (let i = 0; i <= 16; i++) {
      const a = -Math.PI / 2 + (i / 16) * Math.PI;
      for (const s of [1, -1]) fx(u.dim, "rpg:tele_disc", { x: c.x + s * Math.cos(a) * R, y: c.y + 0.07, z: c.z + Math.sin(a) * R }, { dir: { x: 0, y: 0, z: 0 }, size: 0.8, life: 1.0, color: s > 0 ? SUN : MOON, alpha: 0.9 });
    }
  }
  if (left > 0 && left % 20 === 0 && left <= 60) for (const mk of live) showFaceText(mk.p, `${SIDE_TEXT[mk.side]}§l${SIDE_NAME[mk.side]}의 심판 ${left / 20}`, { dist: 2.4, up: 0.45 });
  // 두 번째 심판: 표식이 뒤바뀌기 3·2·1초 전
  const toSwap = G.swapAt !== undefined && !G.swapped ? G.swapAt - now : -1;
  if (toSwap > 0 && toSwap % 20 === 0 && toSwap <= 60) for (const mk of live) showFaceText(mk.p, `§d§l표식이 뒤바뀜 ${toSwap / 20}`, { dist: 2.4, up: 0.25 });
  if (k % 10 === 0) {
    for (const mk of live) {
      const safe = sideOf(u, mk.p) === mk.side;
      const where = mk.side === "sun" ? "§6금빛 반원(동쪽)" : "§b푸른 반원(서쪽)";
      notify(mk.p, safe ? `${SIDE_TEXT[mk.side]}${SIDE_NAME[mk.side]}의 표식 §a— 제자리, 안전 §7(${Math.ceil(left / 20)}초)` : `${SIDE_TEXT[mk.side]}${SIDE_NAME[mk.side]}의 표식 §c— ${where}§c으로!`, 15);
    }
  }
}

/** 판결: 표식이 남은 사람 중 제 반원 밖이면 즉사 → 무적 풀림 → 가장 많이 모인 곳으로 도약 */
function judgeVerdict(u) {
  const m = u.mem;
  const G = m.judge;
  if (!G) return;
  const now = system.currentTick;
  const c = u.center;
  const R = u.enc.def.arena.disc;
  m.judge = undefined;
  u.immune = false;
  m.bigUntil = now + 100;
  // 원판 두 반원에서 빛 (해 = 금빛 폭발, 달 = 푸른 물보라)
  fx(u.dim, "rpg:bash_burst", { x: c.x + R * 0.5, y: c.y + 1.5, z: c.z }, { dir: { x: 0, y: 0, z: 1 }, size: 4, life: 0.6, color: SUN, alpha: 0.95 });
  fx(u.dim, "rpg:splash", { x: c.x - R * 0.5, y: c.y + 2, z: c.z }, { size: 4, life: 0.7, color: MOON, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: c.x, y: c.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: R + 1, life: 0.8, color: [1, 1, 1], alpha: 0.95 });
  play(u.dim, "mob.warden.sonic_boom", c, 0.8, 1);
  for (const mk of G.marks) {
    if (mk.done || !mk.p.isValid || !okTarget(mk.p)) continue;
    if (sideOf(u, mk.p) === mk.side) {
      showFaceText(mk.p, "§a§l심판을 견뎠습니다", { dist: 2.4, up: 0.45 });
      continue;
    }
    say(u, `§4[보스] §e${mk.p.name}§f님이 ${SIDE_TEXT[mk.side]}${SIDE_NAME[mk.side]}의 심판§f을 받아 쓰러졌습니다`);
    kill(mk.p, u.boss);
  }
  // 울부짖는 모습을 풂 (빈 동작 rest 가 같은 틀을 이어받음)
  u.api.anim(u, "rest", undefined, { controller: HOWL });
  // 판결 뒤: 가장 많이 모인 곳으로 도약 (원판에 모인 사람들이 흩어지게)
  const list = targets(u).filter((p) => Math.abs(p.location.y - c.y) < 4);
  const spot = crowdSpot(u, list);
  if (spot) {
    startPounce(u, leapPlan(u, spot));
    faceAll(u, "§6§l도약! §f원 밖으로 흩어지세요", { dist: 2.4, up: 0.45 }, list.filter((p) => hdist(p.location, spot) <= u.def.pounce.radius + 3));
  }
}

/** 사람이 가장 많이 모인 곳 (그 사람 발밑): pounce.radius 칸 안에 다른 사람이 가장 많은 사람 */
export function crowdSpot(u, list) {
  const R = u.def.pounce.radius;
  let best;
  let n = -1;
  for (const p of shuffle(list)) {
    const k = list.filter((q) => hdist(q.location, p.location) <= R).length;
    if (k > n) {
      n = k;
      best = p;
    }
  }
  return best ? { x: best.location.x, y: groundUnder(best, u.center.y), z: best.location.z } : undefined;
}

// ------------------------------------------------------------ 낮밤 바뀜 · 황혼
function swapStance(u, now) {
  const m = u.mem;
  const S = u.def.stance;
  const night = m.stance.mode === "day";
  m.stance.mode = night ? "night" : "day";
  m.stance.next = now + S.every;
  m.look.dayTo = night ? 0 : 1;
  m.body.want = undefined;
  u.api.anim(u, "howl");
  const h = headAt(u, night ? "moon" : "sun");
  sound(u, u.def.sounds.howl, h, night ? 0.9 : 0.6, 1.2);
  const b = bodyOf(u);
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 6, life: 0.8, color: night ? MOON : SUN, alpha: 0.9 });
  fx(u.dim, "rpg:bash_burst", h, { dir: { x: 0, y: 0, z: 1 }, size: 2.5, life: 0.5, color: night ? MOON : EMBER, alpha: 0.95 });
  title(u, night ? "§b§l밤 — 하티가 깨어납니다" : "§6§l낮 — 스콜이 깨어납니다", night ? "§f약점: §e스콜(검은 머리) 쪽 옆구리 §7· 달빛 사냥" : "§f약점: §e하티(흰 머리) 쪽 옆구리 §7· 태양 바퀴", targets(u));
  u.api.cast(u, "howl", S.howl, []);
  // 깨어난 머리의 큰 패턴은 울부짖은 뒤 조금 쉬고
  const k = night ? "hunt" : "wheel";
  m.next[k] = Math.max(m.next[k], now + S.howl + 60);
}

function startDusk(u, now) {
  const m = u.mem;
  const D = u.def.dusk;
  m.dusk = true;
  u.cdMult = D.cd;
  m.look.duskTo = 1;
  m.body.want = undefined;
  u.api.anim(u, "howl");
  sound(u, u.def.sounds.howl, headAt(u, "sun"), 0.6, 1.4);
  sound(u, u.def.sounds.howl, headAt(u, "moon"), 0.9, 1.4);
  const b = bodyOf(u);
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 8, life: 1.0, color: DUSK, alpha: 0.95 });
  for (const w of ["sun", "moon"]) fx(u.dim, "rpg:bash_burst", headAt(u, w), { dir: { x: 0, y: 0, z: 1 }, size: 2.5, life: 0.6, color: w === "sun" ? EMBER : MOON, alpha: 0.95 });
  title(u, "§5§l황혼", "§f스콜과 하티가 함께 깨어났습니다 §7— 약점이 사라집니다", targets(u));
  explain(u, "dusk", `§5[보스] 황혼: §f두 머리가 모두 깨어나 태양 바퀴와 달빛 사냥을 모두 씁니다. §7약점 옆구리는 사라지고 패턴이 더 자주 나옵니다`);
  u.api.cast(u, "dusk", D.howl, []);
  m.next.wheel = Math.max(m.next.wheel, now + D.howl + 40);
  m.next.hunt = Math.max(m.next.hunt, now + D.howl + 160);
}

// ------------------------------------------------------------ 높은 곳 · 둘레 연출
/** 선돌·성문 위처럼 높은 곳(바닥보다 high.above 칸 넘게)에 grace 틱 넘게 머문 사람: 뛰어올라 끌어내림 (사람마다 cd 틱에 한 번) */
function highTick(u, now) {
  if (now % 10 !== 0 || !u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const m = u.mem;
  const c = u.center;
  const seen = new Set();
  for (const p of targets(u)) {
    // 뛰는 중이어도 셈 (높은 곳에서 계속 뛰며 버티는 것을 막음): 내려오면 처음부터 다시
    if (p.location.y - c.y < d.above) continue;
    seen.add(p.id);
    if (!m.highSince.has(p.id)) {
      m.highSince.set(p.id, now);
      showFaceText(p, "§6§l내려오세요! §f머물면 끌어내립니다", { dist: 2.2, up: 0.35 });
    }
    if (now - m.highSince.get(p.id) < d.grace) continue;
    if (now - (m.high.get(p.id) ?? -1000) < d.cd) continue;
    m.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    fx(u.dim, "rpg:slash_x", { x: p.location.x, y: p.location.y + 1.2, z: p.location.z }, { dir: { x: -dir.x, y: 0, z: -dir.z }, speed: 0, spin: 0, size: 1.6, life: 0.3, color: stanceColor(u), alpha: 0.95 });
    dust(u.dim, p.location, 1.8, SNOW, 0.6);
    play(u.dim, u.def.sounds.swing, p.location, 0.8, 1);
    // 때린 엔티티 없이 (게임의 밀어내기는 보스에게서 먼 쪽 = 담장 밖일 수 있음): 가운데 쪽으로만 끌어내림
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 }, noSource: true });
    showFaceText(p, "§6§l끌려 내려갑니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", `§6[보스] §f선돌·성문 위처럼 높은 곳에 ${(d.grace / 20).toFixed(1)}초 넘게 머물면 스콜과 하티가 뛰어올라 끌어내립니다`);
  }
  for (const id of [...m.highSince.keys()]) if (!seen.has(id)) m.highSince.delete(id);
}

/** 둘레 연출: 약점 옆구리 바닥 표시 (잠든 머리 색), 깨어난 쪽 선돌의 눈이 빛남 */
function ambientTick(u, now) {
  const m = u.mem;
  if (now % 10 === 0 && !m.judge && !m.leap && m.body.h <= 0.01) {
    const side = weakSide(u);
    if (side) {
      const r = rightOf(m.body.yaw);
      const off = bossSize(u.def).half + 1.3;
      const at = { x: m.body.x + r.x * side * off, y: u.center.y, z: m.body.z + r.z * side * off };
      fx(u.dim, "rpg:tele_disc", { x: at.x, y: at.y + 0.06, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 1.3, life: 0.55, color: side > 0 ? MOON : SUN, alpha: 0.8 });
    }
  }
  if (now % 40 !== 0) return;
  const head = awake(u);
  const c = u.center;
  for (const s of u.feat?.stones ?? []) {
    if ((head === "sun" && !s.sun) || (head === "moon" && s.sun)) continue;
    const d = dirTo(s, c);
    fx(u.dim, "rpg:dust", { x: s.x + d.x * 0.9, y: s.y + 2.4, z: s.z + d.z * 0.9 }, { size: 1.1, life: 1.6, color: s.sun ? SUN : MOON, alpha: 0.75 });
  }
}

/** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 뛰는 모습 대신 내려앉기 */
function ground(enc) {
  const m = enc.mem;
  if (!m?.body || !enc.boss?.isValid) return;
  const b = m.body;
  // 뛰는 중이면 그 자리에서 땅으로 (앞으로 순간이동하지 않음)
  const airborne = b.h > 0.01 || !!m.leap;
  m.leap = undefined;
  b.h = 0;
  b.gaitTo = 0;
  b.gait = 0;
  try {
    place(enc);
  } catch {
    // 무시
  }
  b.gaitSent = setProp(enc, "rpg:gait", 0, undefined);
  // 들고 있던 모습(웅크림·뛰는 모습)을 풂: 떠 있었으면 내려앉기, 아니면 빈 동작
  enc.api.anim(enc, airborne ? "land" : "rest", undefined, { controller: LEAP });
}

// ------------------------------------------------------------ 모듈
export default {
  key: "skoll_hati",
  /** 약점 옆구리 (+weak, "약점") · 지친 동안 (+hunt.bonus, "지침") */
  hurt(u, p, amount) {
    const m = u.mem;
    if (!m?.body) return amount;
    let mult = 1;
    let mark = "";
    if (u.def.stance.weak > 0 && !m.judge && onFlank(u, p.location)) {
      mult *= 1 + u.def.stance.weak;
      mark += WEAK_MARK;
    }
    if (u.def.hunt.bonus > 0 && system.currentTick < m.windedUntil) {
      mult *= 1 + u.def.hunt.bonus;
      mark += WINDED_MARK;
    }
    return { amount: amount * mult, mark };
  },
  /** 원거리 견제: 등장·심판·달빛 사냥 동안은 쉼 */
  farOk(u) {
    const m = u.mem;
    return !!m?.body && !m.judge && !m.hunt && u.cast?.id !== "arrive";
  },
  /** 낮 = 태양 불씨 (불), 밤 = 달 서리 (느림 II), 황혼 = 번갈아 */
  farStyle(u) {
    const F = u.def.far;
    const m = u.mem;
    let night;
    if (m.dusk) {
      m.farFlip = !m.farFlip;
      night = m.farFlip;
    } else night = m.stance.mode === "night";
    if (!night) return { name: F.name, look: F.look, effect: F.effect, burn: F.burn };
    explain(u, "farNight", `§b[보스] ${F.night.name}: §f밤에는 하티가 ${F.night.name}를 던집니다 — 원 안에 있으면 피해 + 느림`);
    return { name: F.night.name, look: F.night.look, effect: F.night.effect, burn: 0 };
  },
  /** 던지는 곳: 그 모양의 머리 입 (불씨 = 스콜, 서리 = 하티) */
  farFrom(u, style) {
    return headAt(u, style?.look === "frost" ? "moon" : "sun");
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    const c = enc.center;
    // 등장: 깨운 사람에게서 가장 먼 선돌 위에서 울부짖고 가운데로 뛰어내림
    const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
    const stones = enc.feat?.stones ?? [];
    const st = stones.length ? stones.reduce((a, s) => (hdist(s, p) > hdist(a, p) ? s : a)) : { x: c.x, z: c.z };
    const yaw = stones.length ? angOf(dirTo(st, c)) : angOf(dirTo(c, p));
    enc.mem = {
      next: { bite: now + 40, pounce: now + d.pounce.first, wheel: now + d.wheel.first, hunt: now + d.hunt.first },
      bigUntil: now + 100,
      told: new Set(),
      body: { x: st.x, z: st.z, h: stones.length ? 5 : 0, yaw, want: undefined, gait: 0, gaitTo: 0, gaitSent: undefined, placed: undefined },
      look: { day: 1, dayTo: 1, dusk: 0, duskTo: 0, daySent: undefined, duskSent: undefined, phaseSent: undefined },
      stance: { mode: "day", next: now + d.stance.first },
      dusk: false,
      leap: undefined,
      hunt: undefined,
      wheel: undefined,
      judge: undefined,
      judgeDone: new Set(),
      frost: [],
      frostHit: new Map(),
      windedUntil: 0,
      high: new Map(),
      highSince: new Map(),
      farFlip: false,
    };
    for (const u of enc.units) u.mem = enc.mem;
    // 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기는 모듈이)
    enc.meleeAt = Infinity;
    place(enc);
    bodyTick(enc);
    enc.api.anim(enc, "howl");
    sound(enc, d.sounds.howl, headAt(enc, "sun"), 0.6, 1.4);
    const A = ARRIVE;
    enc.api.cast(enc, "arrive", A.howl + A.time + 16, [
      [
        A.howl,
        (x) => {
          x.api.anim(x, "leap", undefined, { controller: LEAP, stopExpression: "0" });
          jump(x, { to: { x: c.x, z: c.z }, spot: floorAt(x, c.x, c.z), yaw }, A.time, A.height);
        },
      ],
      [
        A.howl + A.time,
        (x) => {
          landFx(x, floorAt(x, c.x, c.z), 4, SUN);
          x.api.anim(x, "land", undefined, { controller: LEAP });
          explain(x, "stance", `§e[보스] 낮과 밤: §f${Math.round(d.stance.every / 20)}초마다 낮(스콜 — 검은 머리, 몸 왼쪽)과 밤(하티 — 흰 머리, 몸 오른쪽)이 바뀝니다. §e잠든 머리 쪽 옆구리를 때리면 피해 +${Math.round(d.stance.weak * 100)}%§f (옆구리 바닥에 표시, 피해 숫자 앞 "약점")`);
        },
      ],
    ]);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    if (!m?.body || m.leap) return false;
    const r = u.api.ratio(u);
    // 1) 해와 달의 심판 (큰 패턴)
    if (now >= m.bigUntil) {
      const at = d.judge.at.find((x) => r <= x && !m.judgeDone.has(x));
      if (at !== undefined) {
        m.judgeDone.add(at);
        judge(u, now, list, d.judge.at.indexOf(at) > 0);
        return true;
      }
    }
    // 2) 황혼
    if (!m.dusk && r <= d.dusk.from) {
      startDusk(u, now);
      return true;
    }
    // 3) 낮과 밤 바뀜
    if (!m.dusk && now >= m.stance.next) {
      swapStance(u, now);
      return true;
    }
    const head = awake(u);
    // 4) 태양 바퀴 (낮·황혼) · 달빛 사냥 (밤·황혼)
    if (head !== "moon" && now >= m.next.wheel) {
      if (wheel(u, now, list)) {
        m.next.wheel = now + every(u, d.wheel.every);
        return true;
      }
      m.next.wheel = now + 20;
    }
    if (head !== "sun" && now >= m.next.hunt) {
      if (hunt(u, now, list)) {
        m.next.hunt = now + every(u, d.hunt.every);
        return true;
      }
      m.next.hunt = now + 20;
    }
    // 5) 도약
    if (now >= m.next.pounce) {
      if (pounce(u, now, list)) {
        m.next.pounce = now + every(u, d.pounce.every);
        return true;
      }
      m.next.pounce = now + 20;
    }
    // 6) 평타: 몸 앞의 가장 가까운 사람을 물어뜯음
    if (now >= m.next.bite && bite(u, now, list)) {
      m.next.bite = now + Math.round(d.melee.every * (u.cdMult ?? 1));
      return true;
    }
    return false;
  },
  tick(enc, now, list) {
    const u = enc;
    if (!u.alive || !u.boss?.isValid || !enc.mem?.body) return;
    leapTick(u, now);
    judgeTick(u, now);
    frostTick(u, now);
    highTick(u, now);
    ambientTick(u, now);
    if (!enc.alive) return;
    // 몸: 뛰는 중에는 leapTick 이, 패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
    if (!u.mem.leap) {
      if (u.cast) standTick(u);
      else {
        u.mem.body.want = undefined;
        walkTick(u, list ?? targets(u));
      }
    }
    bodyTick(u);
  },
  /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 뛰다가 쓰러지면 땅에 내려놓음 */
  dying(enc) {
    ground(enc);
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.body) return;
    ground(enc);
    if (m.judge) enc.api.anim(enc, "rest", undefined, { controller: HOWL });
    m.frost = [];
    m.hunt = undefined;
    m.wheel = undefined;
    m.judge = undefined;
    enc.immune = false;
  },
};

/** 시험용: 지금 심판 · 사냥 · 태양 바퀴 · 서리 자국 · 뛰는 중 */
export function judgeOf(enc) {
  return enc.mem?.judge;
}
export function huntOf(enc) {
  return enc.mem?.hunt;
}
export function wheelOf(enc) {
  return enc.mem?.wheel;
}
export function frostOf(enc) {
  return enc.mem?.frost;
}
export function leapOf(enc) {
  return enc.mem?.leap;
}
