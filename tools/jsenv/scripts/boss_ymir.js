import { BlockPermutation, system, world } from "@minecraft/server";
import { glacierSize } from "./arena.js";
import { notify, play, showFaceText } from "./fx.js";
import { fx, shake } from "./skills.js";
import { isRooted, kill, root } from "./status.js";
import { audience, bossHit, bossSize, disc, faceAll, fighters, groundUnder, hdist, lineMarks, lobArc, meleeTarget, okTarget, say, sound, title } from "./bosskit.js";

/**
 * 이미르 (v2.5.0, Lv.40, 마지막 보스): 수치는 config.js BOSSES.ymir
 * 얼음 몽둥이를 오른손에 든 태초의 서리 거인 (크기 ×4, 키 약 9칸). 태초의 빙원에서 싸움.
 * 움직이지 않는 보스(중력·충돌 없음)를 모듈이 매 틱 옮김 (엔진 pose): 가장 가까운 사람 쪽으로 몸을 돌려 걷고, 숨결 때는 가운데로 뛰어옴.
 * 몸(엔티티 자리) = 발 사이, 오른 어깨 = 몸 오른쪽 drive.arm 칸 (몽둥이가 떨어지는 줄)
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작), rpg:crack (0~3 → 텍스처: 얼음 다리의 금, 3 = 무릎 꿇음)
 * - 등장: 가운데 얼어붙은 호수 밑에서 얼음을 깨고 솟아오름 → 포효 (그동안 무적)
 * - 몽둥이 내려치기 (평타): 몸 앞 가장 가까운 사람 — 오른 어깨에서 그 사람 쪽 직사각형 (대상이 몽둥이 줄 위에 오게 몸을 돎). 피해 + 느림
 * - 발 구르기: 옆·뒤(앞 front° 밖)에 붙은 사람이 있으면 몸 둘레 원 → 피해 + 띄움
 * - 얼음 가시: 가장 먼 사람부터 lines 명 쪽으로 몸에서 가시 줄 → 가시가 차례로 솟음 → 피해 + 빙결 (얼음 덩어리에 갇혀 못 움직임)
 * - 고드름 비: 모두의 발밑 + 무작위 자리 원 → 피해 + 느림
 * - 빙결된 사람(얼음에 갇힘)은 이미르의 다른 공격을 받지 않음 (속박된 사람 = 원거리 견제도 노리지 않음)
 * - 균열 → 무릎 꿇기: 몸 가까이에서 준 피해만큼 금 (멀리서는 × far). 다 차면 무릎을 꿇어 공격 없이 받는 피해 +bonus ("빈틈") → 일어난 뒤 lock 틱은 금이 가지 않음
 * - 니플헤임의 숨결 (체력 65%·30%, 즉사): 가운데로 뛰어옴 → 숨을 들이쉼 (눈보라 안개) → 불이 켜진 모닥불 곁(warm 칸)이 아니면 얼어붙음.
 *   불 하나에 싸우는 사람의 절반(올림)까지 — 넘치면 그 불이 꺼지고 곁의 사람도 모두 얼어붙음. 두 번째는 마주 보는 모닥불 한 쌍을 먼저 꺼 버림
 * - 핌불베트르 (체력 20% 아래): 끝날 때까지 눈보라 안개, 재사용 대기 × winter.cd, 가시 줄·고드름이 늘어남
 * - 모닥불 곁에서는 느림이 풀림 · 높은 곳(첨탑·성문 기둥): 몽둥이로 쳐서 끌어내림 · 원거리 견제: 얼음 창 (왼손에서, 느림 II)
 * 큰 패턴(숨결)과 핌불베트르는 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 내려치기를 모듈이)
 */

const ICE = [0.62, 0.9, 1.0];
/** 눈밭에서 보이는 예고 색 (짙은 파랑) */
const DEEP = [0.2, 0.5, 1.0];
const FROST = [0.85, 0.95, 1.0];
const SNOW = [0.95, 0.97, 1.0];
const EMBER = [1.0, 0.55, 0.15];
/** 모닥불 곁 고리 */
const FIRE = [1.0, 0.45, 0.08];
const RED = [1.0, 0.18, 0.12];
/** 꺼진 불 */
const DEAD = [0.3, 0.38, 0.55];
const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

/** 피해 숫자 앞 표시: 무릎 꿇은 동안 (근접 보너스 » 앞) */
export const OPEN_MARK = "§6빈틈 ";
/** 동작 틀: 솟아오르기·무릎 꿇기·뛰기·숨결은 다음 동작이 이어받을 때까지 마지막 모습 유지 (쉬기 rest 가 풂) */
export const HOLD = "rpg_hold";
/** 빙결 얼음 덩어리 (소품) · 모닥불 위 글자 (소품) */
export const ICE_ID = "rpg:prop_ymir_ice";
export const LABEL_ID = "rpg:prop_label";
const ICE_ANIM = "animation.rpg.prop.ymir_ice";
/** 안개 (리소스 팩 fogs/): 숨결 = 짙은 눈보라, 핌불베트르 = 옅은 눈보라. /fog 의 이름표 */
export const FOGS = { breath: "rpg:ymir_blizzard", winter: "rpg:ymir_winter" };
export const FOG_TAG = "rpg_boss_fog";
/** 고드름: fall 틱 동안 height 칸에서 떨어짐 (rpg:icicle 중력 = 2 × height / (fall/20)², build.py ICICLE_FALL 과 같음) */
export const ICICLE_FALL = { ticks: 12, height: 7.2 };
/** 모닥불 네 개 중 마주 보는 쌍 (arena.js FIRE_DEG 순서: 45·135·225·315°) */
export const FIRE_PAIRS = [
  [0, 2],
  [1, 3],
];

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
const wrap = (a) => ((a % TAU) + TAU) % TAU;
const wrapPi = (a) => {
  const w = wrap(a);
  return w > Math.PI ? w - TAU : w;
};
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
function turnToward(cur, want, step) {
  const d = wrapPi(want - cur);
  return Math.abs(d) <= step ? want : cur + Math.sign(d) * step;
}
/** 몸 오른쪽 단위 방향 (+x 동쪽·+z 남쪽에서 북쪽(-z)을 보면 오른쪽은 동쪽): 몽둥이를 든 손 쪽 */
export function rightOf(yaw) {
  return { x: -Math.sin(yaw), y: 0, z: Math.cos(yaw) };
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

/** 얼음 부서짐 (물보라 그림 + 먼지) */
function shatter(dim, at, size, color = ICE) {
  fx(dim, "rpg:splash", { x: at.x, y: at.y + size * 0.7, z: at.z }, { size, life: 0.6, color, alpha: 0.95 });
  dust(dim, at, size * 0.9, FROST, 0.7);
}

/**
 * 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖·벽 위로 날아가지 않게)
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

/** 바닥 높이의 사람 (위아래 4칸 안) */
const level = (u, p) => Math.abs(p.location.y - u.center.y) < 4;

/** 이미르가 공격할 수 있는 때: 등장·숨결 (뛰는 동안 포함)·뛰는 동안·무릎 꿇은 동안이 아님 (원거리 견제·높은 곳) */
function canAttack(u, now) {
  const m = u.mem;
  return !!m?.body && !m.breath && !m.leap && now >= m.kneelUntil && u.cast?.id !== "arrive";
}

/** 이미르가 때릴 수 있는 사람: 빙결(속박)된 사람은 얼음이 막아 줌 */
const open = (p) => !isRooted(p);

// ------------------------------------------------------------ 몸
/** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h (등장 때는 호수 밑 = 음수) */
export function bodyOf(u) {
  const b = u.mem.body;
  return { x: b.x, y: u.center.y + b.h, z: b.z };
}

/** 오른 어깨 밑 (몽둥이 줄의 시작, 바닥 높이) */
export function shoulderAt(u, yaw) {
  const b = u.mem.body;
  const r = rightOf(yaw ?? b.yaw);
  const arm = u.def.drive.arm;
  return { x: b.x + r.x * arm, y: u.center.y, z: b.z + r.z * arm };
}

/** 왼손 (원거리 견제를 던지는 곳: 몽둥이는 오른손) */
export function leftHandAt(u) {
  const b = u.mem.body;
  const r = rightOf(b.yaw);
  const arm = u.def.drive.arm;
  return { x: b.x - r.x * arm, y: u.center.y + b.h + 6, z: b.z - r.z * arm };
}

/** 머리 (입 높이) */
export function headAt(u) {
  const b = u.mem.body;
  const f = dirOf(b.yaw);
  return { x: b.x + f.x * 0.6, y: u.center.y + b.h + 7.6, z: b.z + f.z * 0.6 };
}

/** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 teleport 하지 않게) */
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

/** 매 틱: 걷는 빠르기를 목표 쪽으로, 속성 보내기 (걷기 rpg:gait, 금 rpg:crack) */
function bodyTick(u) {
  const b = u.mem.body;
  const C = u.mem.crack;
  b.gait = Math.abs(b.gaitTo - b.gait) <= 0.15 ? b.gaitTo : b.gait + Math.sign(b.gaitTo - b.gait) * 0.15;
  b.gaitSent = setProp(u, "rpg:gait", Math.round(clamp(b.gait, 0, 1) * 100) / 100, b.gaitSent);
  if (C.stage !== C.sent) {
    try {
      u.boss.setProperty("rpg:crack", C.stage);
      C.sent = C.stage;
    } catch {
      // 무시
    }
  }
}

/** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음) */
function walkTick(u, list, now) {
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
  // 발걸음: 쿵 (가까운 사람은 조금 흔들림)
  if (b.gaitTo > 0 && now % 12 === 0) {
    const at = bodyOf(u);
    play(u.dim, u.def.sounds.step, at, 0.7, 1);
    dust(u.dim, floorAt(u, at.x, at.z), 1.8, SNOW, 0.6);
    shakeNear(u, at, 7, 0.06, 0.25);
  }
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

/** 지금 틀에 ticks 뒤 단계를 끼워 넣음 (틀이 없으면 무시) */
function later(u, ticks, fn) {
  const c = u.cast;
  if (!c) return;
  c.steps.push({ at: system.currentTick + ticks, fn });
  c.steps.sort((a, b) => a.at - b.at);
}

/** 동작 틀기: 유지하는 동작은 틀 HOLD (다음에 같은 틀에 튼 동작이 이어받음) */
function hold(u, name) {
  u.api.anim(u, name, undefined, { controller: HOLD, stopExpression: "0" });
}
function release(u, name = "rest") {
  u.api.anim(u, name, undefined, { controller: HOLD });
}

// ------------------------------------------------------------ 뛰기 (숨결 때 가운데로)
/** 뛰기 시작: time 틱 동안 포물선 (높이 height) 으로 to 까지 (몸은 leapTick 이 옮김) */
function jump(u, to, time, height) {
  const b = u.mem.body;
  u.mem.leap = { x0: b.x, z0: b.z, h0: b.h, x1: to.x, z1: to.z, t0: system.currentTick, time: Math.max(1, time), height };
  b.want = undefined;
  play(u.dim, u.def.sounds.swing, bodyOf(u), 0.5, 1);
  if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 3.2, SNOW, 0.9);
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
  b.gaitTo = 0;
  if (k >= 1) {
    u.mem.leap = undefined;
    b.h = 0;
  }
  place(u);
}

/** 내려앉은 자리·쿵 연출 */
function landFx(u, at, radius, color = ICE) {
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life: 0.7, color, alpha: 0.95 });
  fx(u.dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2, life: 1.4, color: [0.3, 0.4, 0.55], alpha: 0.9 });
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    dust(u.dim, { x: at.x + Math.cos(a) * radius * 0.7, y: at.y, z: at.z + Math.sin(a) * radius * 0.7 }, 2.4, SNOW, 0.9);
  }
  play(u.dim, u.def.sounds.hit, at, 0.6, 1);
  play(u.dim, "dig.snow", at, 0.7, 1);
  shakeNear(u, at, 20, 0.35, 0.7);
}

// ------------------------------------------------------------ 빙결 (얼음 덩어리)
/** 얼음 덩어리 소품 동작 (부서짐은 마지막 모습 유지) */
function iceAnim(e, name) {
  try {
    if (e?.isValid) e.playAnimation(`${ICE_ANIM}.${name}`, { controller: "rpg_ice", stopExpression: "0" });
  } catch {
    // 표시만
  }
}

/** 얼음 덩어리를 p 발밑에 (until 틱에 부서지고 drop 틱에 사라짐) */
function encase(u, p, ticks) {
  const now = system.currentTick;
  const at = { x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z };
  let e;
  try {
    e = u.dim.spawnEntity(ICE_ID, at);
    u.api.addProp(u.enc, e);
  } catch {
    e = undefined;
  }
  if (e) u.mem.ice.push({ e, pid: p.id, breakAt: now + ticks, dropAt: now + ticks + 12, broken: false });
  shatter(u.dim, at, 1.4, FROST);
  play(u.dim, u.def.sounds.ice, at, 1.3, 1);
}

/** 빙결: 속박 ticks (얼음에 갇힘, 그동안 이미르의 다른 공격은 막아 줌) + 얼음 덩어리 */
function freeze(u, p, ticks) {
  root(p, ticks, "빙결");
  encase(u, p, ticks);
}

/** 얼음 덩어리: 때가 되면 부서지고 사라짐 */
function iceTick(u, now) {
  const I = u.mem.ice;
  for (let i = I.length - 1; i >= 0; i--) {
    const c = I[i];
    if (!c.e?.isValid) {
      I.splice(i, 1);
      continue;
    }
    if (now >= c.breakAt && !c.broken) {
      c.broken = true;
      iceAnim(c.e, "shatter");
      const l = c.e.location;
      shatter(u.dim, l, 1.6, ICE);
      play(u.dim, u.def.sounds.ice, l, 0.9, 1);
    }
    if (now >= c.dropAt) {
      u.api.dropProp(u.enc, c.e);
      I.splice(i, 1);
    }
  }
}

// ------------------------------------------------------------ 몽둥이 내려치기 (평타)
/**
 * 대상이 몽둥이 줄(오른 어깨에서 앞) 위에 오게 하는 몸 방향. 몸에 바짝 붙은 사람(몸 가운데에서 √(arm² + (from+0.5)²) 칸 안)은
 * 줄 가운데에 올 수 없으므로 직사각형 시작에서 0.5칸 안쪽 (줄에서 조금 왼쪽이지만 폭 안)에 오게 함
 */
export function slamYaw(u, l) {
  const b = u.mem.body;
  const D = Math.hypot(l.x - b.x, l.z - b.z);
  const th = Math.atan2(l.z - b.z, l.x - b.x);
  const arm = u.def.drive.arm;
  // 대상 자리 (몸 기준: 앞으로 along, 오른쪽으로 side)
  const along = Math.max(Math.sqrt(Math.max(0, D * D - arm * arm)), Math.min(D, u.def.melee.from + 0.5));
  const side = Math.sqrt(Math.max(0, D * D - along * along));
  return th - Math.atan2(side, along);
}

/** 내려치는 직사각형: 어깨 밑에서 앞으로 from ~ reach 칸 (시작·끝·방향) */
export function slamRect(u, yaw) {
  const m = u.def.melee;
  const s = shoulderAt(u, yaw);
  const f = dirOf(yaw);
  return { a: { x: s.x + f.x * m.from, y: s.y, z: s.z + f.z * m.from }, b: { x: s.x + f.x * m.reach, y: s.y, z: s.z + f.z * m.reach }, dir: f };
}

/** 직사각형 예고 (가운데 줄을 따라 판, 테두리는 진하게) */
function rectMarks(u, R, width, ticks, color) {
  const L = hdist(R.a, R.b);
  const n = Math.max(2, Math.ceil(L / 1.2));
  const r = rightOf(angOf(R.dir));
  const life = Math.max(0.1, ticks / 20);
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    const c = { x: R.a.x + (R.b.x - R.a.x) * t, y: R.a.y, z: R.a.z + (R.b.z - R.a.z) * t };
    for (const s of [-1, 0, 1]) {
      const edge = s !== 0 || i === 0 || i === n;
      const at = { x: c.x + r.x * s * (width / 2 - 0.4), y: c.y + 0.07, z: c.z + r.z * s * (width / 2 - 0.4) };
      fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", at, { dir: { x: 0, y: 0, z: 0 }, size: edge ? 0.6 : 1.1, life, color, alpha: edge ? 0.9 : 0.45, grow: 1 });
    }
  }
}

function slam(u, now, list) {
  const m = u.def.melee;
  const b = u.mem.body;
  // 몽둥이 줄이 닿는 사람만 (어깨에서 reach 칸: 몸 가운데에서 √(reach² + arm²)) — 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람)
  const reachC = Math.hypot(m.reach, u.def.drive.arm);
  const t = meleeTarget(u, list.filter((p) => open(p) && level(u, p) && hdist(p.location, b) <= reachC && offAngle(u, p.location) <= m.aim * DEG), b);
  if (!t) return false;
  const yaw = slamYaw(u, t.location);
  const R = slamRect(u, yaw);
  b.want = yaw;
  rectMarks(u, R, m.width, m.windup, DEEP);
  u.api.anim(u, "slam");
  sound(u, u.def.sounds.swing, shoulderAt(u, yaw), 0.7, 1);
  u.api.cast(u, "slam", m.windup + 8, [[m.windup, (x) => slamHit(x, R)]]);
  return true;
}

/** l 이 직사각형 R (a → b, 반폭 half) 안인지: 앞뒤·옆으로 pad 칸 여유 (끝이 둥글지 않음 — 그린 예고 그대로) */
export function inRect(l, R, half, pad = 0.3) {
  const ax = l.x - R.a.x;
  const az = l.z - R.a.z;
  const along = ax * R.dir.x + az * R.dir.z;
  const side = Math.abs(ax * R.dir.z - az * R.dir.x);
  return along >= -pad && along <= hdist(R.a, R.b) + pad && side <= half + pad;
}

function slamHit(u, R) {
  const m = u.def.melee;
  for (const p of targets(u)) {
    if (!open(p) || Math.abs(p.location.y - R.a.y) > 4 || !inRect(p.location, R, m.width / 2)) continue;
    const kk = keepIn(u, p, R.dir.x * 1.0, R.dir.z * 1.0);
    bossHit(u, p, m.damage, { knock: { x: kk.x, z: kk.z, h: 0.35 }, directional: true, effect: { id: "slowness", ticks: m.slow, amp: 0 } });
  }
  // 몽둥이가 떨어진 곳: 얼음 깨짐 + 줄을 따라 금
  const head = { x: R.a.x + (R.b.x - R.a.x) * 0.8, y: R.a.y, z: R.a.z + (R.b.z - R.a.z) * 0.8 };
  fx(u.dim, "rpg:shock_ring", { x: head.x, y: head.y + 0.1, z: head.z }, { dir: { x: 0, y: 0, z: 0 }, size: 2.6, life: 0.5, color: ICE, alpha: 0.95 });
  for (let i = 1; i <= 3; i++) {
    const t = i / 4;
    fx(u.dim, "rpg:ground_crack", { x: R.a.x + (R.b.x - R.a.x) * t, y: R.a.y + 0.05, z: R.a.z + (R.b.z - R.a.z) * t }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: 3.2, life: 1.2, color: [0.3, 0.42, 0.6], alpha: 0.85 });
  }
  shatter(u.dim, head, 2.2, ICE);
  sound(u, u.def.sounds.hit, head, 0.8, 1);
  shakeNear(u, head, 10, 0.25, 0.5);
}

// ------------------------------------------------------------ 발 구르기
function stomp(u, now, list) {
  const d = u.def.stomp;
  const b = u.mem.body;
  const flank = list.filter((p) => open(p) && level(u, p) && hdist(p.location, b) <= d.radius && offAngle(u, p.location) > (d.front / 2) * DEG);
  if (!flank.length) return false;
  const at = floorAt(u, b.x, b.z);
  b.want = undefined;
  disc(u.dim, at, d.radius, d.windup, DEEP);
  u.api.anim(u, "stomp");
  sound(u, u.def.sounds.roar, headAt(u), 1.4, 0.5);
  explain(u, "stomp", "§b[보스] 발 구르기: §f이미르의 옆이나 뒤에 붙어 있으면 발을 굴러 몸 둘레(파란 원)를 칩니다 — 원 밖으로 잠깐 물러나세요");
  u.api.cast(u, "stomp", d.windup + 10, [[d.windup, (x) => stompHit(x, at)]]);
  return true;
}

function stompHit(u, at) {
  const d = u.def.stomp;
  for (const p of targets(u)) {
    if (!open(p) || hdist(p.location, at) > d.radius + 0.3 || Math.abs(p.location.y - at.y) > 3) continue;
    const dir = dirTo(at, p.location);
    const kk = keepIn(u, p, dir.x * 0.9, dir.z * 0.9);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: d.lift } });
  }
  fx(u.dim, "rpg:tele_wave", { x: at.x, y: at.y + 0.2, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 26, size: 1, life: d.radius / 26, color: ICE, alpha: 0.9 });
  landFx(u, at, 3.2, ICE);
}

// ------------------------------------------------------------ 얼음 가시 (빙결)
/** 몸(at)에서 방향 v 로 뻗은 줄이 필드 가장자리 안쪽(반지름 - 0.5)까지 닿는 길이 (range 까지) */
export function rayEnd(u, at, v, range) {
  const R = u.enc.def.arena.radius - 0.5;
  const wx = at.x - u.center.x;
  const wz = at.z - u.center.z;
  const bd = wx * v.x + wz * v.z;
  const q = bd * bd - (wx * wx + wz * wz - R * R);
  const t = q > 0 ? -bd + Math.sqrt(q) : 0;
  return Math.max(0, Math.min(range, t));
}

/** 가시 줄: 몸 겉면 바로 밖에서 대상 쪽으로 (필드 끝까지), 가시 자리 목록 */
export function spikeLines(u, list) {
  const d = u.def.spikes;
  const b = bodyOf(u);
  const start = bossSize(u.def).half + 0.5;
  return list.map((p) => {
    const dir = dirTo(b, p.location);
    const len = Math.max(start, rayEnd(u, b, dir, d.range));
    const pts = [];
    for (let s = start; s <= len + 1e-6; s += d.step) pts.push({ x: b.x + dir.x * s, y: u.center.y, z: b.z + dir.z * s, s });
    return { dir, from: { x: b.x + dir.x * start, y: u.center.y, z: b.z + dir.z * start }, to: { x: b.x + dir.x * len, y: u.center.y, z: b.z + dir.z * len }, pts, hit: new Set(), done: 0 };
  });
}

function spikes(u, now, list) {
  const d = u.def.spikes;
  const m = u.mem;
  const b = bodyOf(u);
  const n = d.lines + (m.winter ? u.def.winter.lines : 0);
  const cand = list
    .filter((p) => open(p) && level(u, p))
    .sort((x, y) => hdist(y.location, b) - hdist(x.location, b))
    .slice(0, n);
  if (!cand.length) return false;
  const lines = spikeLines(u, cand);
  const start = bossSize(u.def).half + 0.5;
  const longest = Math.max(...lines.map((L) => hdist(L.from, L.to)));
  const travel = Math.ceil((longest / d.speed) * 20);
  // 줄 예고: 판의 크기는 반폭 (가시가 닿는 곳 = 줄 가운데에서 width/2 + 0.3)
  for (const L of lines) lineMarks(u.dim, L.from, L.to, d.width / 2, d.windup + travel, DEEP);
  m.body.want = angOf(lines[0].dir);
  u.api.anim(u, "spike");
  sound(u, u.def.sounds.swing, bodyOf(u), 0.6, 1);
  faceAll(u, "§b§l얼음 가시! §f파란 줄 밖으로", { dist: 2.4, up: 0.45 }, cand);
  explain(u, "spikes", "§b[보스] 얼음 가시: §f이미르가 몽둥이를 땅에 박으면 먼 사람들 쪽으로 파란 줄이 뻗고 가시가 차례로 솟습니다 — 줄 밖으로. §7맞으면 빙결 (얼음에 갇혀 못 움직이지만, 그동안 이미르의 다른 공격은 막아 줌)");
  /** @type {[number, (x: any) => void][]} */
  const steps = [[d.windup, (x) => play(x.dim, "mace.smash_ground", bodyOf(x), 0.8, 1)]];
  for (let k = 0; k <= travel; k++) steps.push([d.windup + k, (x) => spikeStep(x, lines, start + (k * d.speed) / 20)]);
  u.api.cast(u, "spikes", d.windup + travel + 10, steps);
  return true;
}

/** 가시가 솟음: 줄마다 reach 칸까지 아직 솟지 않은 가시 → 곁의 사람 (줄마다 한 번) 피해 + 빙결 */
function spikeStep(u, lines, reach) {
  const d = u.def.spikes;
  const list = targets(u);
  for (const L of lines) {
    while (L.done < L.pts.length && L.pts[L.done].s <= reach + 1e-6) {
      const pt = L.pts[L.done++];
      fx(u.dim, "rpg:ice_spike", { x: pt.x, y: pt.y, z: pt.z }, { size: 1.9 + (L.done % 3) * 0.25, life: 1.3, spin: Math.random() * 30 - 15, color: ICE, alpha: 1 });
      if (L.done % 3 === 0) play(u.dim, "break.amethyst_block", pt, 0.8 + Math.random() * 0.3, 0.8);
      for (const p of list) {
        if (L.hit.has(p.id) || !open(p) || Math.abs(p.location.y - pt.y) > 3 || hdist(p.location, pt) > d.width / 2 + 0.3) continue;
        L.hit.add(p.id);
        // 때린 엔티티 없이 (땅에서 솟은 가시: 보스 쪽에서 밀어내지 않음)
        if (bossHit(u, p, d.damage, { noSource: true })) freeze(u, p, d.root);
      }
    }
  }
}

// ------------------------------------------------------------ 고드름 비
/** 필드 안 무작위 자리 (가운데에서 R-3 칸 안) */
function randomSpot(u) {
  const R = u.enc.def.arena.radius - 3;
  const a = Math.random() * TAU;
  const r = Math.sqrt(Math.random()) * R;
  return floorAt(u, u.center.x + Math.cos(a) * r, u.center.z + Math.sin(a) * r);
}

function icicles(u, now, list) {
  const d = u.def.icicles;
  const m = u.mem;
  const people = list.filter((p) => open(p) && level(u, p));
  if (!people.length) return false;
  const spots = people.map((p) => ({ x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z }));
  const extra = d.extra + (m.winter ? u.def.winter.extra : 0);
  for (let i = 0; i < extra; i++) spots.push(randomSpot(u));
  for (const s of spots) {
    disc(u.dim, s, d.radius, d.windup, DEEP);
    fx(u.dim, "rpg:skill_rune", { x: s.x, y: s.y + ICICLE_FALL.height + 0.5, z: s.z }, { dir: { x: 0, y: 1, z: 0 }, size: d.radius * 0.9, life: d.windup / 20, spin: 0, speed: 40, grow: 0, progress: 1, color: FROST, alpha: 0.75 });
  }
  m.body.want = undefined;
  u.api.anim(u, "summon");
  sound(u, u.def.sounds.roar, headAt(u), 1.2, 0.6);
  faceAll(u, "§b§l고드름 비! §f발밑 원 밖으로", { dist: 2.4, up: 0.45 }, people);
  explain(u, "icicles", "§b[보스] 고드름 비: §f이미르가 두 팔을 들면 모두의 발밑과 곳곳에 파란 원이 뜨고 고드름이 떨어집니다 — 원 밖으로 §7(맞으면 느림)");
  const fall = d.windup - ICICLE_FALL.ticks;
  u.api.cast(u, "icicles", d.windup + 8, [
    [fall, (x) => icicleDrop(x, spots)],
    [d.windup, (x) => icicleHit(x, spots)],
  ]);
  return true;
}

/** 고드름이 떨어지기 시작 (ICICLE_FALL.ticks 뒤에 바닥에 닿음) */
function icicleDrop(u, spots) {
  const d = u.def.icicles;
  for (const s of spots) {
    for (let i = 0; i < 3; i++) {
      const a = (i / 3) * TAU + Math.random();
      const r = i === 0 ? 0 : d.radius * 0.5;
      fx(u.dim, "rpg:icicle", { x: s.x + Math.cos(a) * r, y: s.y + ICICLE_FALL.height, z: s.z + Math.sin(a) * r }, { size: 1.4, life: ICICLE_FALL.ticks / 20 + 0.05, color: ICE, alpha: 1 });
    }
  }
}

function icicleHit(u, spots) {
  const d = u.def.icicles;
  const hit = new Set();
  for (const s of spots) {
    shatter(u.dim, s, d.radius * 0.8, ICE);
    fx(u.dim, "rpg:ground_crack", { x: s.x, y: s.y + 0.05, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: d.radius * 2, life: 1.0, color: [0.35, 0.45, 0.62], alpha: 0.85 });
    play(u.dim, u.def.sounds.ice, s, 1.1, 0.9);
    for (const p of targets(u)) {
      if (hit.has(p.id) || !open(p) || hdist(p.location, s) > d.radius + 0.3 || Math.abs(p.location.y - s.y) > 3) continue;
      hit.add(p.id);
      bossHit(u, p, d.damage, { noSource: true, effect: { id: "slowness", ticks: d.slow, amp: 0 } });
    }
  }
}

// ------------------------------------------------------------ 균열 → 무릎 꿇기
/** 금이 가는 중인지 (등장·숨결·뛰기·무적·무릎 꿇은 뒤 쉬는 동안은 아님) */
function canCrack(u, now) {
  const m = u.mem;
  return !u.immune && !m.breath && !m.leap && now >= m.kneelUntil && now >= m.crack.lockUntil && u.cast?.id !== "arrive";
}

/** 맞을 때: 몸 가까이(근접 보너스 거리)에서 준 피해는 그대로, 멀리서 준 피해는 × far 만큼 금. 다 차면 무릎 꿇기 */
function addCrack(u, amount, close, now) {
  const C = u.def.crack;
  const K = u.mem.crack;
  if (!(amount > 0) || !canCrack(u, now)) return;
  K.g += amount * (close ? 1 : C.far);
  if (K.g >= C.need) {
    kneel(u, now);
    return;
  }
  const stage = Math.min(2, Math.floor((3 * K.g) / C.need));
  if (stage > K.stage) {
    K.stage = stage;
    const b = bodyOf(u);
    const r = rightOf(u.mem.body.yaw);
    for (const s of [1, -1]) shatter(u.dim, { x: b.x + r.x * s * 0.75, y: u.center.y + 1.2, z: b.z + r.z * s * 0.75 }, 1.2, ICE);
    play(u.dim, u.def.sounds.ice, b, 0.7, 1);
    faceAll(u, `§b§l균열 ${stage}/3`, { dist: 2.4, up: 0.6 }, targets(u));
  }
}

/** 무릎 꿇기: 하던 패턴을 끊고 time 틱 동안 공격 없음, 받는 피해 +bonus → 일어남 */
function kneel(u, now) {
  const C = u.def.crack;
  const m = u.mem;
  u.cast = undefined;
  m.body.want = undefined;
  m.crack.g = 0;
  m.crack.stage = 3;
  m.kneelUntil = now + C.time;
  m.crack.lockUntil = now + C.time + C.lock;
  // 하던 공격 동작(기본 틀: 내려치기·가시 박기 등)을 끊고 무릎 꿇는 모습 (유지 틀)
  u.api.anim(u, "rest");
  hold(u, "kneel");
  const b = bodyOf(u);
  const r = rightOf(m.body.yaw);
  for (const s of [1, -1]) {
    const k = { x: b.x + r.x * s * 0.75, y: u.center.y + 1.2, z: b.z + r.z * s * 0.75 };
    shatter(u.dim, k, 2.4, ICE);
    fx(u.dim, "rpg:bash_burst", { x: k.x, y: k.y + 0.8, z: k.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.6, life: 0.5, color: FROST, alpha: 0.95 });
  }
  landFx(u, floorAt(u, b.x, b.z), 4.5, FROST);
  play(u.dim, "random.anvil_land", b, 0.5, 1);
  play(u.dim, u.def.sounds.ice, b, 0.6, 1);
  title(u, "§b§l이미르가 무릎을 꿇었습니다!", `§f${Math.round(C.time / 20)}초 동안 받는 피해 §e+${Math.round(C.bonus * 100)}% §7(빈틈)`, targets(u));
  explain(u, "kneel", `§b[보스] 균열: §f이미르의 얼음 다리를 가까이에서 때리면 금이 갑니다 (멀리서 때린 것은 ${Math.round(C.far * 100)}%만). §e금이 가득 차면 무릎을 꿇고 ${Math.round(C.time / 20)}초 동안 공격 없이 받는 피해 +${Math.round(C.bonus * 100)}% §7(피해 숫자 앞 "빈틈")`);
  u.api.cast(u, "kneel", C.time + 20, [
    [C.time, (x) => rise(x)],
    [C.time + 12, (x) => (x.mem.crack.stage = 0)],
  ]);
}

function rise(u) {
  release(u, "rise");
  play(u.dim, u.def.sounds.roar, headAt(u), 1.1, 0.7);
  dust(u.dim, floorAt(u, u.mem.body.x, u.mem.body.z), 3, SNOW, 0.8);
}

// ------------------------------------------------------------ 모닥불
/** 모닥불 블록 켜기·끄기 (화덕 가운데 칸 한 칸 위) */
export function setFire(u, f, lit) {
  f.lit = lit;
  try {
    u.dim.setBlockPermutation({ x: Math.floor(f.at.x), y: Math.floor(f.at.y) + 1, z: Math.floor(f.at.z) }, BlockPermutation.resolve("minecraft:campfire", { extinguished: !lit, "minecraft:cardinal_direction": "south" }));
  } catch {
    // 불러오지 않은 곳 등
  }
}

/** 모닥불 곁의 사람 (가로 warm 칸 안, 위아래 3칸) */
export function nearFire(u, f, list) {
  const W = u.enc.def.arena.warm;
  return list.filter((p) => hdist(p.location, f.at) <= W && Math.abs(p.location.y - f.at.y) < 3);
}

/** 켜진 모닥불 중 p 가 곁에 있는 불 */
function fireOf(u, p) {
  const W = u.enc.def.arena.warm;
  return u.mem.fires.find((f) => f.lit && hdist(p.location, f.at) <= W && Math.abs(p.location.y - f.at.y) < 3);
}

/** 매 틱: 꺼진 불 다시 켜기 · 불씨 연출 · 곁에서는 느림이 풀림 */
function fireTick(u, now) {
  const m = u.mem;
  for (const f of m.fires) {
    if (!f.lit && f.relightAt !== undefined && now >= f.relightAt && !m.breath) {
      f.relightAt = undefined;
      setFire(u, f, true);
      fx(u.dim, "rpg:bash_burst", { x: f.at.x, y: f.at.y + 1.6, z: f.at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.2, life: 0.5, color: EMBER, alpha: 0.95 });
      play(u.dim, u.def.sounds.fire, f.at, 1, 1);
    }
    if (f.lit && now % 20 === 7) fx(u.dim, "rpg:dust", { x: f.at.x, y: f.at.y + 2.4, z: f.at.z }, { size: 1.2, life: 1.2, color: EMBER, alpha: 0.7 });
    // 2초마다: 다른 방법(물병 던지기·명령 등)으로 꺼지거나 켜진 모닥불은 규칙대로 (스크립트가 기억하는 상태로) 되돌림
    if (now % 40 === 13) {
      let b;
      try {
        b = u.dim.getBlock({ x: Math.floor(f.at.x), y: Math.floor(f.at.y) + 1, z: Math.floor(f.at.z) });
      } catch {
        b = undefined;
      }
      if (b && (b.typeId !== "minecraft:campfire" || b.permutation?.getState("extinguished") !== !f.lit)) setFire(u, f, f.lit);
    }
  }
  const W = u.def.warmth;
  if (now % W.every !== 0) return;
  for (const p of targets(u)) {
    if (!fireOf(u, p)) continue;
    let slow;
    try {
      slow = p.getEffect("slowness");
    } catch {
      slow = undefined;
    }
    if (!slow) continue;
    try {
      p.removeEffect("slowness");
    } catch {
      continue;
    }
    fx(u.dim, "rpg:dust", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { size: 1.1, life: 0.6, color: EMBER, alpha: 0.8 });
    if (now - (m.warmTold.get(p.id) ?? -1000) >= 100) {
      m.warmTold.set(p.id, now);
      showFaceText(p, "§6따뜻함 §f— 느림이 풀렸습니다", { dist: 2.2, up: 0.1 });
    }
  }
}

// ------------------------------------------------------------ 니플헤임의 숨결 (즉사: 불이 켜진 모닥불 곁)
/**
 * 담장 밖(성문·둑·필드 바깥 띠)에서 싸우던 사람은 숨결을 들이쉬기 시작할 때 담장 안으로 끌려 들어옴 (제 방향 그대로, 가운데에서 R-4.5 칸):
 * 벽을 돌아 성문으로 들어오면 모닥불까지 제때 가지 못하므로
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
    dust(u.dim, l, 1.6, FROST, 0.7);
    dust(u.dim, at, 1.6, FROST, 0.7);
    showFaceText(p, "§b§l눈보라에 끌려 들어왔습니다", { dist: 2.4, up: 0.45 });
  }
}

function breath(u, now, list, second) {
  const B = u.def.breath;
  const m = u.mem;
  const c = u.center;
  // 숨결을 받는 사람 (id): 뛰기 시작할 때 싸우던 사람 + 그 뒤 들어온 사람 (breathRoll). 자리 = 절반 (올림, 사람이 늘면 늘어남)
  const who = new Set(targets(u).map((p) => p.id));
  m.breath = { stage: "leap", second, t0: now, until: Infinity, who, gone: new Set(), cap: Math.max(1, Math.ceil(who.size / 2)), labels: [], snuffed: [] };
  u.immune = true;
  m.body.want = undefined;
  hold(u, "leap");
  jump(u, c, B.leap, 4);
  sound(u, u.def.sounds.roar, headAt(u), 0.8, 1);
  title(u, "§b§l니플헤임의 숨결", "§f이미르가 숨을 들이쉽니다 §7— §6불이 켜진 모닥불 곁으로!", list);
  const inhale = B.leap + 6;
  u.api.cast(u, "breath", inhale + B.windup + 30, [
    [B.leap, (x) => breathLand(x)],
    [inhale, (x) => breathInhale(x)],
    [inhale + B.windup, (x) => breathExhale(x)],
    [inhale + B.windup + 26, (x) => release(x, "rest")],
  ]);
}

function breathLand(u) {
  const m = u.mem;
  if (!m.breath) return;
  u.immune = false;
  const b = bodyOf(u);
  landFx(u, floorAt(u, b.x, b.z), 6, FROST);
  // 내려앉자마자 가슴을 펴고 숨을 들이쉬는 모습 (내쉴 때까지 유지)
  hold(u, "inhale");
  // 호수 얼음에 금
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * TAU + Math.random() * 0.4;
    fx(u.dim, "rpg:ground_crack", { x: b.x + Math.cos(a) * 3, y: u.center.y + 0.05, z: b.z + Math.sin(a) * 3 }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: 4, life: 3, color: [0.85, 0.95, 1.0], alpha: 0.9 });
  }
}

function breathInhale(u) {
  const B = u.def.breath;
  const m = u.mem;
  const G = m.breath;
  if (!G) return;
  const now = system.currentTick;
  // 숨결을 받는 사람 (뛰는 동안 필드를 벗어난 사람도: 64칸 안) 중 담장 밖에 있는 사람은 끌려 들어옴
  breathRoll(u, G);
  pullIn(u, breathVictims(u, G));
  G.stage = "inhale";
  G.t0 = now;
  G.until = now + B.windup;
  sound(u, u.def.sounds.inhale, headAt(u), 0.5, 1);
  play(u.dim, "mob.warden.sonic_charge", headAt(u), 0.6, 1);
  // 두 번째 숨결: 마주 보는 모닥불 한 쌍에 얼음 창을 던져 끔 (남은 한 쌍도 마주 봄: 어디서든 가까운 불이 있음)
  if (G.second && m.fires.length === 4) {
    const pair = FIRE_PAIRS[Math.floor(Math.random() * FIRE_PAIRS.length)];
    const from = leftHandAt(u);
    for (const i of pair) {
      const f = m.fires[i];
      const arc = lobArc(from, { x: f.at.x, y: f.at.y + 1.5, z: f.at.z }, 0.8);
      fx(u.dim, "rpg:ice_lance", from, { dir: arc.dir, speed: arc.speed, size: 1.6, life: 0.8, color: ICE, alpha: 1 });
      G.snuffed.push(i);
    }
    play(u.dim, "mob.irongolem.throw", from, 0.7, 1);
    later(u, 16, (x) => snuff(x, pair));
  }
  // 모닥불 위 글자 (곁의 사람 수 / 한 불의 자리)
  for (const f of m.fires) {
    let e;
    try {
      e = u.dim.spawnEntity(LABEL_ID, { x: f.at.x, y: f.at.y + 3.1, z: f.at.z });
      u.api.addProp(u.enc, e);
    } catch {
      e = undefined;
    }
    if (e) G.labels.push({ e, f });
  }
  labelTick(u);
  explain(u, "breath", `§b[보스] 니플헤임의 숨결: §f이미르가 숨을 들이쉬는 ${Math.round(B.windup / 20)}초 안에 §6불이 켜진 무스펠헤임의 모닥불 곁(주황 원)§f으로 — 원 밖은 얼어붙어 즉사. §c불 하나에 싸우는 사람의 절반까지 §7(넘치면 불이 꺼져 곁의 사람도 모두 얼어붙음)`);
  say(u, `§b[보스] §f이번 숨결: 불 하나에 §e${G.cap}명§f까지${G.second ? " §7· §c이미르가 모닥불 둘을 꺼 버립니다!" : ""}`);
}

/** 두 번째 숨결: 얼음 창이 닿은 모닥불이 꺼짐 (숨결이 끝나고 relight 틱 뒤 다시 켜짐) */
function snuff(u, pair) {
  const m = u.mem;
  for (const i of pair) {
    const f = m.fires[i];
    if (!f) continue;
    setFire(u, f, false);
    shatter(u.dim, { x: f.at.x, y: f.at.y + 1, z: f.at.z }, 2.4, ICE);
    fx(u.dim, "rpg:dust", { x: f.at.x, y: f.at.y + 2, z: f.at.z }, { size: 2.2, life: 1.2, color: [0.5, 0.5, 0.55], alpha: 0.8 });
    play(u.dim, u.def.sounds.snuff, f.at, 0.6, 1);
    play(u.dim, "random.fizz", f.at, 0.8, 1);
  }
  labelTick(u);
}

/** 모닥불 위 글자: 켜진 불 = 곁의 사람 수 / 자리, 넘치면 붉게, 꺼진 불 = 꺼짐 */
function labelTick(u) {
  const G = u.mem.breath;
  if (!G) return;
  const list = targets(u);
  for (const L of G.labels) {
    if (!L.e?.isValid) continue;
    const n = nearFire(u, L.f, list).length;
    let text;
    if (!L.f.lit) text = "§8꺼진 불";
    else if (n > G.cap) text = `§c불씨 §f${n}§7/§f${G.cap} §4넘침!`;
    else text = `§6불씨 §f${n}§7/§f${G.cap}`;
    try {
      if (L.e.nameTag !== text) L.e.nameTag = text;
    } catch {
      // 무시
    }
  }
}

/** 월드의 플레이어 모두 (나갔다 다시 들어온 사람은 새 객체라 id 로 찾음) */
function allPlayers() {
  try {
    return world.getAllPlayers();
  } catch {
    return [];
  }
}

/**
 * 숨결 명단 맞추기 (뛰는 동안·들이쉬는 동안 매 틱): 지금 싸우는 사람은 넣고 (쓰러졌다가 필드로 돌아온 사람도 다시), 자리 수는 사람이 늘면 늘림 (줄이지 않음).
 * 쓰러졌거나 싸울 수 없게 된 사람(크리에이티브 등)은 뺌 — 다시 살아나 필드 밖에 있으면 판정하지 않음
 */
function breathRoll(u, G) {
  for (const p of targets(u)) {
    G.gone.delete(p.id);
    G.who.add(p.id);
  }
  G.cap = Math.max(G.cap, Math.ceil(G.who.size / 2));
  for (const p of allPlayers()) if (G.who.has(p.id) && !G.gone.has(p.id) && !okTarget(p)) G.gone.add(p.id);
}

/** 숨결을 받는 사람: 지금 싸우는 사람 + 명단에 있는 사람 중 아직 싸울 수 있고 같은 차원·필드 가운데에서 64칸 안인 사람 (필드를 떠나도 피하지 못함) */
function breathVictims(u, G) {
  const out = new Map();
  for (const p of targets(u)) out.set(p.id, p);
  for (const p of allPlayers()) {
    if (out.has(p.id) || !G.who?.has(p.id) || G.gone?.has(p.id)) continue;
    let near = false;
    try {
      near = okTarget(p) && p.dimension.id === u.dim.id && hdist(p.location, u.center) <= 64;
    } catch {
      near = false;
    }
    if (near) out.set(p.id, p);
  }
  return [...out.values()];
}

/** 숨결 동안 매 틱: 명단 맞추기 (뛰는 동안부터) · 들이쉬는 동안 모닥불 곁 고리 (주황 = 자리 있음, 붉음 = 넘침, 꺼진 불 = 흐린 고리) · 글자 · 알림 · 3·2·1 · 눈보라 */
function breathTick(u, now) {
  const G = u.mem.breath;
  if (!G) return;
  // 뛰는 동안부터 명단을 맞춤 (그 사이 들어온 사람도 받고, 쓰러진 사람은 빠짐)
  breathRoll(u, G);
  if (G.stage !== "inhale") return;
  const W = u.enc.def.arena.warm;
  const list = breathVictims(u, G);
  const left = G.until - now;
  if (now % 5 === 0) {
    for (const f of u.mem.fires) {
      const n = nearFire(u, f, list).length;
      const color = !f.lit ? DEAD : n > G.cap ? RED : FIRE;
      disc(u.dim, f.at, f.lit ? W : 1.5, 6, color, { fill: false, alpha: 0.95 });
    }
    labelTick(u);
  }
  if (now % 10 === 0) {
    for (const p of list) {
      const f = fireOf(u, p);
      const n = f ? nearFire(u, f, list).length : 0;
      const s = Math.ceil(left / 20);
      notify(p, !f ? `§b얼어붙습니다! §f불이 켜진 모닥불 곁(주황 원)으로 §7(${s}초)` : n > G.cap ? `§c불 하나에 ${G.cap}명까지 §f— 다른 불로! §7(${n}명, ${s}초)` : `§6모닥불 곁 §a— 안전 §7(${n}/${G.cap}, ${s}초)`, 15);
      // 눈보라: 이미르 쪽으로 몰아치는 눈 (1초마다, 모바일 파티클 수)
      if (now % 20 !== 0) continue;
      const toward = dirTo(p.location, bodyOf(u));
      fx(u.dim, "rpg:snowfall", { x: p.location.x - toward.x * 3, y: p.location.y + 3, z: p.location.z - toward.z * 3 }, { dir: { x: toward.x, y: -0.3, z: toward.z }, speed: 9, size: 7, life: 0.8, color: SNOW, alpha: 0.9 });
    }
    // 가장자리에서 가운데로 빨려 드는 서리
    const c = u.center;
    const R = u.enc.def.arena.radius - 2;
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * TAU + now * 0.05;
      const at = { x: c.x + Math.cos(a) * R, y: c.y + 1.5, z: c.z + Math.sin(a) * R };
      const v = dirTo(at, bodyOf(u));
      fx(u.dim, "rpg:breath", at, { dir: { x: v.x, y: 0.1, z: v.z }, speed: 14, size: 3, life: 1.2, spin: Math.random() * 360, color: FROST, alpha: 0.75 });
    }
  }
  if (left > 0 && left % 20 === 0 && left <= 60) faceAll(u, `§b§l숨결 ${left / 20}`, { dist: 2.4, up: 0.45 }, list);
}

/** 내쉼: 켜진 모닥불 곁이 아니면 얼어붙음. 곁의 사람이 자리보다 많으면 그 불이 꺼지고 곁의 사람도 모두 얼어붙음 */
function breathExhale(u) {
  const B = u.def.breath;
  const m = u.mem;
  const G = m.breath;
  if (!G) return;
  const now = system.currentTick;
  const list = breathVictims(u, G);
  const safe = new Set();
  const doomed = new Set();
  for (const f of m.fires) {
    if (!f.lit) continue;
    const near = nearFire(u, f, list);
    if (near.length > G.cap) {
      // 넘침: 불이 꺼지고 곁의 사람도 얼어붙음
      setFire(u, f, false);
      fx(u.dim, "rpg:dust", { x: f.at.x, y: f.at.y + 2, z: f.at.z }, { size: 2.4, life: 1.2, color: [0.5, 0.5, 0.55], alpha: 0.85 });
      play(u.dim, u.def.sounds.snuff, f.at, 0.5, 1);
      for (const p of near) doomed.add(p.id);
    } else for (const p of near) safe.add(p.id);
  }
  hold(u, "exhale");
  const b = bodyOf(u);
  sound(u, u.def.sounds.exhale, headAt(u), 0.8, 1);
  play(u.dim, "wind_charge.burst", b, 0.6, 1);
  fx(u.dim, "rpg:tele_wave", { x: b.x, y: u.center.y + 0.3, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 30, size: 1, life: u.enc.def.arena.radius / 30, color: FROST, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 8, life: 0.8, color: FROST, alpha: 0.95 });
  for (let i = 0; i < 12; i++) {
    const v = dirOf((i / 12) * TAU);
    fx(u.dim, "rpg:breath", headAt(u), { dir: { x: v.x, y: -0.35, z: v.z }, speed: 18, size: 4, life: 1.2, spin: Math.random() * 360, color: FROST, alpha: 0.85 });
  }
  for (const p of fighters(u)) shake(p, 0.4, 0.8);
  for (const p of list) {
    if (safe.has(p.id) && !doomed.has(p.id)) {
      showFaceText(p, "§6§l불씨가 지켜 주었습니다", { dist: 2.4, up: 0.45 });
      continue;
    }
    encase(u, p, 20);
    say(u, `§b[보스] §e${p.name}§f님이 니플헤임의 숨결에 얼어붙었습니다${doomed.has(p.id) ? " §7(한 불에 너무 많이 모임)" : ""}`);
    kill(p, u.boss);
  }
  // 글자 치우기, 꺼진 불은 relight 틱 뒤 다시 켜짐
  for (const L of G.labels) u.api.dropProp(u.enc, L.e);
  for (const f of m.fires) if (!f.lit) f.relightAt = now + B.relight;
  m.breath = undefined;
  m.bigUntil = now + 100;
  // 숨결 바로 뒤에 원거리 견제·큰 공격이 겹치지 않게
  m.next.spikes = Math.max(m.next.spikes, now + 80);
  m.next.icicles = Math.max(m.next.icicles, now + 120);
}

// ------------------------------------------------------------ 핌불베트르 (체력 20% 아래)
function startWinter(u, now) {
  const W = u.def.winter;
  const m = u.mem;
  m.winter = true;
  u.cdMult = W.cd;
  m.body.want = undefined;
  u.api.anim(u, "roar");
  sound(u, u.def.sounds.roar, headAt(u), 0.7, 1);
  play(u.dim, "ambient.weather.thunder", u.center, 0.6, 0.8);
  const b = bodyOf(u);
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 10, life: 1.0, color: FROST, alpha: 0.95 });
  title(u, "§b§l핌불베트르", "§f끝나지 않는 겨울 — 눈보라가 몰아칩니다", targets(u));
  explain(u, "winter", `§b[보스] 핌불베트르: §f이미르가 끝나지 않는 겨울을 부릅니다. 눈보라 속에서 패턴이 빨라지고 얼음 가시 줄·고드름이 늘어납니다`);
  u.api.cast(u, "winter", 40, []);
  m.next.spikes = Math.max(m.next.spikes, now + 80);
  m.next.icicles = Math.max(m.next.icicles, now + 140);
}

/** 핌불베트르 동안: 사람마다 눈보라 (1초마다) */
function winterTick(u, now) {
  if (!u.mem.winter || u.mem.breath || now % 20 !== 3) return;
  for (const p of targets(u)) fx(u.dim, "rpg:snowfall", { x: p.location.x - 2, y: p.location.y + 4, z: p.location.z - 1 }, { dir: { x: 0.6, y: -0.8, z: 0.2 }, speed: 6, size: 9, life: 1.0, color: SNOW, alpha: 0.8 });
}

// ------------------------------------------------------------ 안개 (/fog: 숨결 = 짙은 눈보라, 핌불베트르 = 옅은 눈보라)
/** 지금 필드 둘레 사람에게 씌울 안개 */
function fogWant(u) {
  const m = u.mem;
  if (m.breath?.stage === "inhale") return FOGS.breath;
  return m.winter ? FOGS.winter : undefined;
}

/**
 * 안개 바꾸기: had (이 싸움이 씌운 안개가 있음) 이면 먼저 걷고, id 가 있으면 씌움. 씌웠으면 true.
 * 지우기와 씌우기는 따로 (씌운 적 없는 안개를 지우는 명령이 실패해도 씌움)
 */
function setFog(p, id, had) {
  if (had) {
    try {
      p.runCommand(`fog @s remove ${FOG_TAG}`);
    } catch {
      // 이미 없음·나간 사람
    }
  }
  if (!id) return false;
  try {
    p.runCommand(`fog @s push ${id} ${FOG_TAG}`);
    return true;
  } catch {
    return false;
  }
}

/** 매 틱(바뀔 때)·10틱마다: 필드 둘레 사람의 안개를 맞추고, 떠난 사람은 걷음 */
function fogTick(u, now, force = false) {
  const m = u.mem;
  const want = fogWant(u);
  if (!force && want === m.fogWant && now % 10 !== 0) return;
  m.fogWant = want;
  const here = new Set();
  for (const p of audience(u)) {
    here.add(p.id);
    const cur = m.fog.get(p.id);
    if ((cur?.id ?? undefined) === want) continue;
    // 씌우지 못하면 기억하지 않음 (다음 0.5초에 다시)
    if (setFog(p, want, !!cur)) m.fog.set(p.id, { p, id: want });
    else m.fog.delete(p.id);
  }
  for (const [id, f] of [...m.fog]) {
    if (here.has(id)) continue;
    if (f.p?.isValid) setFog(f.p, undefined, true);
    m.fog.delete(id);
  }
}

/** 싸움이 끝나면 안개를 모두 걷음 */
function fogOff(enc) {
  const m = enc.mem;
  if (!m?.fog) return;
  for (const f of m.fog.values()) if (f.p?.isValid) setFog(f.p, undefined, true);
  m.fog.clear();
  m.fogWant = undefined;
}

// ------------------------------------------------------------ 높은 곳 · 둘레 연출
/**
 * 첨탑·성문 기둥 위처럼 높은 곳(선 자리의 바닥이 필드 바닥보다 high.above 칸 넘게)에 grace 틱 넘게 머문 사람: 몽둥이로 쳐서 끌어내림
 * (사람마다 cd 틱에 한 번, 얼음에 갇힌 사람은 아님). 이미르가 공격하지 못하는 동안(등장·무릎 꿇기·뛰기·숨결)은 새로 세거나 치지 않고 내려온 사람의 기록만 지움
 * (그 전부터 머문 사람은 쉬는 게 끝나면 바로 맞을 수 있음, 그동안 올라간 사람은 끝난 뒤 경고부터)
 */
function highTick(u, now) {
  if (now % 10 !== 0 || !u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const m = u.mem;
  const paused = !canAttack(u, now);
  const c = u.center;
  const seen = new Set();
  for (const p of targets(u)) {
    // 선 자리의 바닥 높이 (뛰는 중이면 발밑 블록 위). 올라가 있던 사람이 뛰는 중에 발밑이 낮게 잡히면 (가장자리에서 뛰기) 기록을 그대로 둠 — 내려와 땅을 디뎌야 지움
    const up = groundUnder(p, p.location.y) - c.y >= d.above;
    if (!up && !(p.isOnGround === false && m.highSince.has(p.id))) continue;
    seen.add(p.id);
    if (paused) continue;
    if (!m.highSince.has(p.id)) {
      m.highSince.set(p.id, now);
      showFaceText(p, "§b§l내려오세요! §f머물면 몽둥이로 칩니다", { dist: 2.2, up: 0.35 });
    }
    // 치는 것은 높은 곳을 디디고 있거나 그 위에서 뛰는 동안만 (내려가는 중은 아님)
    if (!up || now - m.highSince.get(p.id) < d.grace) continue;
    // 얼음에 갇힌 사람은 얼음이 막아 줌 (셈은 이어짐)
    if (!open(p)) continue;
    if (now - (m.high.get(p.id) ?? -1000) < d.cd) continue;
    m.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    fx(u.dim, "rpg:slash_x", { x: p.location.x, y: p.location.y + 1.2, z: p.location.z }, { dir: { x: -dir.x, y: 0, z: -dir.z }, speed: 0, spin: 0, size: 1.8, life: 0.3, color: ICE, alpha: 0.95 });
    shatter(u.dim, p.location, 1.6, ICE);
    play(u.dim, u.def.sounds.hit, p.location, 1.2, 0.8);
    // 때린 엔티티 없이 (게임의 밀어내기는 보스에게서 먼 쪽 = 벽 밖일 수 있음): 가운데 쪽으로만 끌어내림
    bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 }, noSource: true });
    showFaceText(p, "§b§l얻어맞고 떨어집니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", `§b[보스] §f첨탑·성문 기둥 위처럼 높은 곳에 ${(d.grace / 20).toFixed(1)}초 넘게 머물면 이미르가 몽둥이로 쳐서 떨어뜨립니다`);
  }
  for (const id of [...m.highSince.keys()]) if (!seen.has(id)) m.highSince.delete(id);
}

/** 둘레 연출: 입김 (1.5초마다) */
function ambientTick(u, now) {
  const m = u.mem;
  if (now % 30 !== 0 || m.leap || m.body.h < -0.5) return;
  const h = headAt(u);
  const f = dirOf(m.body.yaw);
  fx(u.dim, "rpg:breath", { x: h.x + f.x * 0.6, y: h.y - 0.6, z: h.z + f.z * 0.6 }, { dir: { x: f.x, y: -0.2, z: f.z }, speed: 2.5, size: 1.6, life: 1.0, spin: Math.random() * 360, color: FROST, alpha: 0.6 });
}

// ------------------------------------------------------------ 등장 (호수 밑에서 솟아오름)
function arrive(enc) {
  const A = enc.def.arrive;
  const c = enc.center;
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let k = 1; k <= A.rise; k++) {
    steps.push([
      k,
      (x) => {
        const t = k / A.rise;
        // 처음엔 빨리, 끝에서 느리게 (얼음을 밀고 올라옴)
        x.mem.body.h = -A.depth * (1 - t) * (1 - t);
        if (k % 6 === 0) {
          const a = Math.random() * TAU;
          const r = 1 + Math.random() * 3.5;
          shatter(x.dim, floorAt(x, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 1.8, ICE);
          play(x.dim, x.def.sounds.ice, c, 0.6 + t * 0.4, 1);
        }
        if (k % 10 === 0) shakeNear(x, c, 24, 0.12 + t * 0.2, 0.5);
      },
    ]);
  }
  steps.push([
    A.rise,
    (x) => {
      x.mem.body.h = 0;
      landFx(x, floorAt(x, c.x, c.z), 6, FROST);
      hold(x, "roar_hold");
      sound(x, x.def.sounds.roar, headAt(x), 0.7, 1);
    },
  ]);
  steps.push([
    A.rise + A.roar,
    (x) => {
      x.immune = false;
      release(x, "rest");
      explain(x, "crack", `§b[보스] 균열: §f이미르의 얼음 다리를 가까이에서 때리면 금이 갑니다 §7(멀리서 때린 것은 ${Math.round(x.def.crack.far * 100)}%만)§f. §e금이 가득 차면 무릎을 꿇고 ${Math.round(x.def.crack.time / 20)}초 동안 받는 피해 +${Math.round(x.def.crack.bonus * 100)}%`);
    },
  ]);
  enc.api.cast(enc, "arrive", A.rise + A.roar + 2, steps);
}

// ------------------------------------------------------------ 모듈
/** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 들고 있던 모습(뛰기·숨결·무릎)을 풂 */
function ground(enc) {
  const m = enc.mem;
  if (!m?.body || !enc.boss?.isValid) return;
  const b = m.body;
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
  release(enc, "rest");
}

export default {
  key: "ymir",
  propIds: [ICE_ID, LABEL_ID],
  /** 무릎 꿇은 동안 받는 피해 +bonus ("빈틈") · 균열 (몸 가까이 = h.near: 근접 보너스 거리, 보너스를 꺼도 같음) */
  hurt(u, p, amount, h) {
    const m = u.mem;
    if (!m?.body) return amount;
    const now = system.currentTick;
    if (now < m.kneelUntil) return { amount: amount * (1 + u.def.crack.bonus), mark: OPEN_MARK };
    addCrack(u, amount, !!h?.near, now);
    return amount;
  },
  /** 원거리 견제: 등장·숨결·뛰는 동안·무릎 꿇은 동안은 쉼 */
  farOk(u, now) {
    return canAttack(u, now ?? system.currentTick);
  },
  /** 던지는 곳: 왼손 (몽둥이는 오른손) */
  farFrom(u) {
    return leftHandAt(u);
  },
  /** 엔진이 이 사람의 보스 안개를 걷었음 (월드에 들어올 때·스크립트를 다시 불러온 뒤): 기억도 지움 → 필드 둘레면 0.5초 안에 다시 씌움 */
  fogCleared(enc, p) {
    enc.mem?.fog?.delete(p.id);
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    const c = enc.center;
    const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
    const yaw = angOf(dirTo(c, p));
    enc.mem = {
      next: { slam: now + d.arrive.rise + d.arrive.roar + 20, stomp: now + d.stomp.first, spikes: now + d.spikes.first, icicles: now + d.icicles.first },
      bigUntil: now + d.arrive.rise + d.arrive.roar + 100,
      told: new Set(),
      body: { x: c.x, z: c.z, h: -d.arrive.depth, yaw, want: undefined, gait: 0, gaitTo: 0, gaitSent: undefined, placed: undefined },
      crack: { g: 0, stage: 0, sent: undefined, lockUntil: 0 },
      kneelUntil: 0,
      leap: undefined,
      breath: undefined,
      breathDone: new Set(),
      winter: false,
      fires: (enc.feat?.fires ?? []).map((f) => ({ at: { x: f.x, y: f.y, z: f.z }, lit: true, relightAt: undefined })),
      ice: [],
      high: new Map(),
      highSince: new Map(),
      warmTold: new Map(),
      fog: new Map(),
      fogWant: undefined,
    };
    for (const u of enc.units) u.mem = enc.mem;
    // 모닥불은 모두 켜고 시작 (지난 싸움이 꺼진 채 끝났어도)
    for (const f of enc.mem.fires) setFire(enc, f, true);
    // 엔진 평타는 쓰지 않음 (몸을 돌리는 내려치기는 모듈이)
    enc.meleeAt = Infinity;
    enc.immune = true;
    place(enc);
    bodyTick(enc);
    hold(enc, "emerge");
    sound(enc, d.sounds.summon, c, 0.6, 1);
    arrive(enc);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    if (!m?.body || m.leap) return false;
    const r = u.api.ratio(u);
    // 1) 니플헤임의 숨결 (큰 패턴)
    if (now >= m.bigUntil) {
      const at = d.breath.at.find((x) => r <= x && !m.breathDone.has(x));
      if (at !== undefined) {
        m.breathDone.add(at);
        breath(u, now, list, d.breath.at.indexOf(at) > 0);
        return true;
      }
    }
    // 2) 핌불베트르
    if (!m.winter && r <= d.winter.from) {
      startWinter(u, now);
      return true;
    }
    // 3) 얼음 가시 · 고드름 비 (서로 바로 이어지지 않게)
    if (now >= m.next.spikes) {
      if (spikes(u, now, list)) {
        m.next.spikes = now + every(u, d.spikes.every);
        m.next.icicles = Math.max(m.next.icicles, now + d.spikes.windup + 60);
        return true;
      }
      m.next.spikes = now + 20;
    }
    if (now >= m.next.icicles) {
      if (icicles(u, now, list)) {
        m.next.icicles = now + every(u, d.icicles.every);
        m.next.spikes = Math.max(m.next.spikes, now + d.icicles.windup + 60);
        return true;
      }
      m.next.icicles = now + 20;
    }
    // 4) 발 구르기 (옆·뒤에 붙은 사람)
    if (now >= m.next.stomp) {
      if (stomp(u, now, list)) {
        m.next.stomp = now + every(u, d.stomp.every);
        m.next.slam = Math.max(m.next.slam, now + d.stomp.windup + 16);
        return true;
      }
      m.next.stomp = now + 20;
    }
    // 5) 평타: 몽둥이 내려치기
    if (now >= m.next.slam && slam(u, now, list)) {
      m.next.slam = now + Math.round(d.melee.every * (u.cdMult ?? 1));
      return true;
    }
    return false;
  },
  tick(enc, now, list) {
    const u = enc;
    if (!u.alive || !u.boss?.isValid || !enc.mem?.body) return;
    const m = enc.mem;
    leapTick(u, now);
    iceTick(u, now);
    fireTick(u, now);
    breathTick(u, now);
    winterTick(u, now);
    highTick(u, now);
    ambientTick(u, now);
    fogTick(u, now);
    if (!enc.alive) return;
    // 몸: 뛰는 중에는 leapTick 이, 등장·패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
    if (!m.leap) {
      if (u.cast) standTick(u);
      else {
        m.body.want = undefined;
        walkTick(u, list ?? targets(u), now);
      }
    }
    bodyTick(u);
  },
  /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 뛰다가 쓰러지면 땅에 내려놓음, 몸 둘레 얼음이 부서짐 */
  dying(enc) {
    ground(enc);
    const m = enc.mem;
    if (!m?.body) return;
    const b = bodyOf(enc);
    for (let i = 0; i < 3; i++) shatter(enc.dim, { x: b.x, y: enc.center.y + i * 2.5, z: b.z }, 3.2, ICE);
    play(enc.dim, enc.def.sounds.ice, b, 0.5, 1);
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.body) return;
    ground(enc);
    fogOff(enc);
    // 꺼진 모닥불은 모두 다시 켬 (필드를 처음 모습으로)
    for (const f of m.fires) {
      f.relightAt = undefined;
      if (!f.lit) setFire(enc, f, true);
    }
    m.ice = [];
    m.breath = undefined;
    m.kneelUntil = 0;
    enc.immune = false;
  },
};

/** 시험용: 지금 숨결 · 균열 · 모닥불 · 얼음 덩어리 · 뛰는 중 */
export function breathOf(enc) {
  return enc.mem?.breath;
}
export function crackOf(enc) {
  return enc.mem?.crack;
}
export function firesOf(enc) {
  return enc.mem?.fires;
}
export function iceOf(enc) {
  return enc.mem?.ice;
}
export function leapOf(enc) {
  return enc.mem?.leap;
}
export { glacierSize };
