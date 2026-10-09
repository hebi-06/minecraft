import { BlockPermutation, EntityDamageCause, system, world } from "@minecraft/server";
import { CHAIN_HEIGHT, muspelSize } from "./arena.js";
import { notify, play, showFaceText } from "./fx.js";
import { fx, shake } from "./skills.js";
import { kill } from "./status.js";
import { audience, bossHit, bossSize, clearBossFog, disc, faceAll, fighters, groundUnder, hdist, lobArc, meleeTarget, okTarget, say, sound, syncBossFog, title } from "./bosskit.js";

/**
 * 수르트 (v2.12.0, Lv.50, 첫 초월 보스): 수치는 config.js BOSSES.surtr
 * 불꽃 검 레바테인을 오른손에 든 무스펠헤임의 불의 거인 (크기 ×10, 키 약 33칸). 무스펠헤임의 신전 (반지름 44) 에서 싸움.
 * 움직이지 않는 보스(중력·충돌 없음)를 모듈이 매 틱 옮김 (엔진 pose): 가장 가까운 사람 쪽으로 몸을 돌려 걷고 (가운데에서 drive.keep 칸 안), 라그나로크 때 뛰어감.
 * 몸(엔티티 자리) = 발 사이, 오른 어깨 = 몸 오른쪽 drive.arm 칸 (레바테인이 떨어지는 줄). 모델에서 잰 자리 (build.py surtr_animations 와 같은 자세):
 *   내려찍기 끝 = 칼끝이 어깨 앞 약 37.5칸 바닥 (melee.reach), 불의 주먹 = 몸 앞 13.4·왼쪽 7칸 (fist.at), 쓰러짐 = 머리가 몸 앞 약 13~16칸 바닥 (CROWN_AT)
 * 엔티티 속성: rpg:gait (걸음) · rpg:heat (0~1 달아오름 → 붉은 주황 덧칠) · rpg:sword_broken · rpg:fist_broken (텍스처) · rpg:crown_broken (머리 불꽃 숨김)
 *
 * 깨는 맛 (사용자 요청: 무력화 · 부위 파괴 · 신전 구조물 활용 · 반격 타이밍)
 * - 레바테인 내려찍기 (평타): 오른 어깨에서 대상 쪽 긴 직사각형 → 피해 + 밀림. 칼이 박힌 동안 칼끝 쪽 "레바테인의 핵" (맞는 소품) → 부수면 부위 파괴.
 *   칼이 온전하면 내려찍은 자리가 한동안 불탐. 칼 줄 위의 온전한 봉인의 사슬 기둥 (몸에서 bind.min 칸 넘게) 이 칼을 붙잡음 → 속박 (받는 피해 +bonus, 칼의 핵),
 *   기둥 뒤는 맞지 않음, 기둥은 부서짐 (싸움이 끝나면 다시 섬)
 * - 횡베기 · 찌르기 (반격): 예고 마지막 window 틱 동안 칼이 금빛 — 그때 스킬·룬으로 수르트를 때리면 패턴이 끊기고 비틀거림 (받는 피해 +bonus "반격")
 * - 불의 주먹 (건틀릿의 핵) · 무스펠의 불비 · 불의 고리 (뛰어넘기) · 발 구르기 · 높은 곳 (불기둥) · 원거리 견제 (불덩이, 왼손)
 * - 무스펠의 업화 (체력 80·50·20%, 무력화): 업화 막대 (보스 막대 소품) 를 피해로 다 깎으면 쓰러짐 (빈틈 +bonus, 불꽃 왕관의 핵), 못 깎으면 업화 폭발
 * - 라그나로크 (체력 65·35%): 온전한 니플헤임의 얼음 결정 뒤 (수르트 반대쪽 그림자) 만 안전 — 즉사 (레바테인이 부서졌으면 큰 마법 피해). 쓴 결정은 녹음.
 *   들고 있는 동안 담장 밖으로 나가면 끌려 들어오고, 불길은 가운데에서 RAG_REACH 칸까지 퍼짐 (불길이 지나간 뒤에 들어온 사람은 판정하지 않음)
 * - 세계를 태우는 불꽃 (체력 50% 아래): 재사용 대기 × cd, 주는 피해 × dmg
 * 큰 패턴(업화·라그나로크)은 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 내려찍기를 모듈이)
 */

const KEY = "surtr";
/** 동작 틀: 솟아오르기·업화·쓰러짐·속박·라그나로크·뛰기는 다음 동작이 이어받을 때까지 마지막 모습 유지 (쉬기 rest 가 풂) */
export const HOLD = "rpg_hold";
/** 드러난 부위의 핵 (맞는 소품) · 업화 막대 (보스 막대만 보이는 소품) */
export const CORE_ID = "rpg:prop_surtr_core";
export const GAUGE_ID = "rpg:prop_surtr_gauge";
/** 업화 막대 소품의 최대 체력 (build.py 와 같음): 막대 = 남은 업화 비율 × 이 값 */
export const GAUGE_MAX = 1000;
/** 안개 (리소스 팩 fogs/): 라그나로크 = 붉은 하늘, 업화 = 주황 열기 */
export const FOGS = { ragnarok: "rpg:surtr_ragnarok", inferno: "rpg:surtr_inferno" };
/** 피해 숫자 앞 표시 (받는 피해가 늘어난 동안, 근접 보너스 » 앞) */
export const MARKS = { groggy: "§6빈틈 ", bind: "§b속박 ", counter: "§e반격 " };
/** 부위 이름 · 핵 소품의 속성 rpg:part 값 (텍스처 색) */
export const PART_NAMES = { sword: "레바테인", fist: "왼 건틀릿", crown: "불꽃 왕관" };
export const PART_INDEX = { sword: 0, fist: 1, crown: 2 };
export const PART_ORDER = ["sword", "fist", "crown"];
/** 부위를 부수면 바뀌는 것 (알림) */
const PART_EFFECT = { sword: "불길이 꺼지고 라그나로크가 즉사하지 않음", fist: "불의 주먹이 사라지고 불비가 약해짐", crown: "업화를 막기 쉬워지고 불의 고리가 줄어듦" };
/** 쓰러졌을 때 머리 (불꽃 왕관) 가 닿는 자리: 몸 앞 칸 (build.py collapse 자세의 머리 앞 끝) */
export const CROWN_AT = 15;
/** 패턴 사이 쉬는 틱 (틀 길이에 더함: 거인의 다음 동작이 읽히게) */
export const GAP = 14;
/** 라그나로크를 받는 거리 (필드 가운데에서 칸, 명단에 있는 사람): 불길이 여기까지 퍼짐 (담장 밖으로 달아나도 피하지 못함) */
export const RAG_REACH = 64;
/** 라그나로크를 들고 있는 동안 (10틱마다) 담장 안으로 끌어오는 거리 (명단에 있는 사람, 같은 차원) */
export const RAG_PULL = 128;
/** 핵 소품 둘레에 비워 둘 칸 (가운데에서 체비셰프): 서 있는 사슬 기둥 (3×3 기둥 반폭 1.5 + 핵의 맞는 상자 반폭 1.5) · 얼음 결정 (팔 끝 2.5 + 1.5) · 담장 (반지름 - 이 값) */
export const CORE_CLEAR = { chain: 3, crystal: 4, wall: 2.5 };

const RED = [1.0, 0.22, 0.16];
const FIRE = [1.0, 0.45, 0.08];
const LAVA = [1.0, 0.3, 0.05];
const EMBER = [1.0, 0.62, 0.2];
const GOLD = [1.0, 0.84, 0.3];
const ICE = [0.62, 0.9, 1.0];
const SAFE = [0.35, 0.8, 1.0];
const ASH = [0.32, 0.28, 0.26];
const CHAIN = [0.72, 0.78, 0.9];
const STEAM = [0.92, 0.94, 0.96];
const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

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
/** 같은 엔티티인지 (이벤트마다 다른 객체로 올 수 있어 id 로 비교) */
function sameEntity(a, b) {
  if (!a || !b) return false;
  if (a === b) return true;
  try {
    return a.id !== undefined && a.id === b.id;
  } catch {
    return false;
  }
}
function turnToward(cur, want, step) {
  const d = wrapPi(want - cur);
  return Math.abs(d) <= step ? want : cur + Math.sign(d) * step;
}
/** 몸 오른쪽 단위 방향 (+x 동쪽·+z 남쪽, 북쪽(-z)을 보면 오른쪽은 동쪽): 칼을 든 손 쪽 */
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
/** 바닥 높이의 사람 (위아래 4칸 안) */
const level = (u, p) => Math.abs(p.location.y - u.center.y) < 4;

// ------------------------------------------------------------ 연출 조각
function dust(dim, at, size, color = ASH, life = 0.8) {
  fx(dim, "rpg:dust", { x: at.x, y: at.y + size * 0.35, z: at.z }, { size, life, color, alpha: 0.8 });
}
/** 불꽃 기둥 한 장 (rpg:fire_wall: 서서 일렁이는 불꽃) */
function flame(dim, at, size, life = 0.7, color = FIRE) {
  fx(dim, "rpg:fire_wall", { x: at.x, y: at.y, z: at.z }, { size, life, color, alpha: 0.95, spin: Math.random() * 360 });
}
/** 터지는 불 (폭발 + 충격파 + 불꽃 + 먼지) */
function blast(dim, at, radius, color = FIRE) {
  fx(dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: radius * 1.1, life: 0.45, color, alpha: 0.95 });
  fx(dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life: 0.5, color, alpha: 0.9 });
  flame(dim, at, radius * 0.9, 0.6, LAVA);
  dust(dim, at, radius * 0.9, ASH, 0.8);
}
/** 땅이 갈라지고 불이 새어 나옴 */
function scorch(dim, at, size) {
  fx(dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size, life: 1.4, color: [0.16, 0.08, 0.06], alpha: 0.9 });
  fx(dim, "rpg:ember", { x: at.x, y: at.y + 0.3, z: at.z }, { size: size * 0.5, life: 0.8, speed: 2, color: EMBER, alpha: 0.9 });
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

/** 불붙이기 (초) */
function burn(p, seconds) {
  if (!(seconds > 0)) return;
  try {
    p.setOnFire(seconds, true);
  } catch {
    // 무시
  }
}

/** 수르트가 공격할 수 있는 때 (원거리 견제·높은 곳): 등장·라그나로크·업화·뛰기·쓰러짐·속박·비틀거림이 아님 */
function canAttack(u, now) {
  const m = u.mem;
  return !!m?.body && !m.ragnarok && !m.inferno && !m.leap && now >= m.groggyUntil && now >= m.bindUntil && now >= u.staggerUntil && u.cast?.id !== "arrive";
}

// ------------------------------------------------------------ 몸
/** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h (등장 때는 화구 밑 = 음수) */
export function bodyOf(u) {
  const b = u.mem.body;
  return { x: b.x, y: u.center.y + b.h, z: b.z };
}
/** 오른 어깨 밑 (레바테인 줄의 시작, 바닥 높이) */
export function shoulderAt(u, yaw) {
  const b = u.mem.body;
  const r = rightOf(yaw ?? b.yaw);
  const arm = u.def.drive.arm;
  return { x: b.x + r.x * arm, y: u.center.y, z: b.z + r.z * arm };
}
/** 왼손 높이 (원거리 견제·불비를 던지는 곳: 칼은 오른손) */
export function leftHandAt(u) {
  const b = u.mem.body;
  const r = rightOf(b.yaw);
  const arm = u.def.drive.arm;
  return { x: b.x - r.x * arm, y: u.center.y + b.h + 16, z: b.z - r.z * arm };
}
/** 머리 (입 높이) */
export function headAt(u) {
  const b = u.mem.body;
  return { x: b.x, y: u.center.y + b.h + 22, z: b.z };
}
/** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음) */
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
/** 지금 달아오른 정도 (rpg:heat): 업화를 모으는 만큼 · 라그나로크 · 분노 */
function heatOf(u, now) {
  const m = u.mem;
  if (m.inferno) return clamp((now - m.inferno.t0) / u.def.inferno.time, 0.15, 1);
  if (m.ragnarok && m.ragnarok.stage !== "leap") return 0.7;
  return m.enraged ? 0.25 : 0;
}
/** 매 틱: 걷는 빠르기를 목표 쪽으로, 속성 보내기 (걸음 · 달아오름 · 부서진 부위) */
function bodyTick(u, now) {
  const b = u.mem.body;
  const m = u.mem;
  b.gait = Math.abs(b.gaitTo - b.gait) <= 0.1 ? b.gaitTo : b.gait + Math.sign(b.gaitTo - b.gait) * 0.1;
  b.gaitSent = setProp(u, "rpg:gait", Math.round(clamp(b.gait, 0, 1) * 100) / 100, b.gaitSent);
  m.heatSent = setProp(u, "rpg:heat", Math.round(heatOf(u, now) * 100) / 100, m.heatSent);
  for (const k of PART_ORDER) {
    const want = !!m.parts[k].broken;
    if (m.flagSent[k] === want) continue;
    try {
      u.boss.setProperty(`rpg:${k}_broken`, want);
      m.flagSent[k] = want;
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
    if (best > D.near && Math.abs(wrapPi(want - b.yaw)) < 45 * DEG) {
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
  // 발걸음: 쿵 (반 걸음마다, 둘레 사람은 흔들림)
  if (b.gaitTo > 0 && now % 36 === 0) {
    const at = bodyOf(u);
    play(u.dim, u.def.sounds.step, at, 0.5, 1);
    dust(u.dim, floorAt(u, at.x, at.z), 4, ASH, 0.8);
    shakeNear(u, at, 22, 0.08, 0.3);
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

// ------------------------------------------------------------ 뛰기 (라그나로크 자리로)
function jump(u, to, time, height) {
  const b = u.mem.body;
  u.mem.leap = { x0: b.x, z0: b.z, h0: b.h, x1: to.x, z1: to.z, t0: system.currentTick, time: Math.max(1, time), height };
  b.want = angOf(dirTo(b, to));
  play(u.dim, u.def.sounds.swing, bodyOf(u), 0.4, 1);
  if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 7, ASH, 1.0);
}
function leapTick(u, now) {
  const L = u.mem.leap;
  if (!L) return;
  const b = u.mem.body;
  const k = clamp((now - L.t0) / L.time, 0, 1);
  b.x = L.x0 + (L.x1 - L.x0) * k;
  b.z = L.z0 + (L.z1 - L.z0) * k;
  b.h = L.h0 * (1 - k) + L.height * 4 * k * (1 - k);
  b.gaitTo = 0;
  if (b.want !== undefined) b.yaw = turnToward(b.yaw, b.want, u.def.drive.turn * 2 * DEG);
  if (k >= 1) {
    u.mem.leap = undefined;
    b.h = 0;
  }
  place(u);
}
/** 내려앉은 자리·쿵 연출 */
function landFx(u, at, radius) {
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life: 0.8, color: FIRE, alpha: 0.95 });
  fx(u.dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2, life: 1.6, color: [0.16, 0.08, 0.06], alpha: 0.9 });
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    const p = { x: at.x + Math.cos(a) * radius * 0.7, y: at.y, z: at.z + Math.sin(a) * radius * 0.7 };
    dust(u.dim, p, 4, ASH, 1.0);
    if (i % 2 === 0) flame(u.dim, p, 3, 0.6, LAVA);
  }
  play(u.dim, u.def.sounds.hit, at, 0.5, 1);
  play(u.dim, u.def.sounds.lava, at, 0.6, 1);
  shakeNear(u, at, 40, 0.45, 0.9);
}

// ------------------------------------------------------------ 바닥 예고
/**
 * 직사각형 예고 (가운데 줄을 따라 몇 줄의 판, 테두리는 진하게): R = {a, b, dir}, 폭 width
 * 줄 간격 1.6칸·줄 수는 폭에 맞게 (모바일 파티클 수)
 */
function rectMarks(u, R, width, ticks, color) {
  const L = hdist(R.a, R.b);
  const n = Math.max(2, Math.ceil(L / 1.6));
  const r = rightOf(angOf(R.dir));
  const life = Math.max(0.1, ticks / 20);
  const cols = Math.max(3, Math.ceil(width / 1.7) + 1);
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    const c = { x: R.a.x + (R.b.x - R.a.x) * t, y: R.a.y, z: R.a.z + (R.b.z - R.a.z) * t };
    for (let k = 0; k < cols; k++) {
      const s = (k / (cols - 1)) * 2 - 1;
      const edge = k === 0 || k === cols - 1 || i === 0 || i === n;
      const at = { x: c.x + r.x * s * (width / 2 - 0.4), y: c.y + 0.07, z: c.z + r.z * s * (width / 2 - 0.4) };
      fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", at, { dir: { x: 0, y: 0, z: 0 }, size: edge ? 0.6 : 1.6, life, color, alpha: edge ? 0.9 : 0.45, grow: 1 });
    }
  }
}
/** l 이 직사각형 R (a → b, 반폭 half) 안인지: 앞뒤·옆으로 pad 칸 여유 */
export function inRect(l, R, half, pad = 0.3) {
  const ax = l.x - R.a.x;
  const az = l.z - R.a.z;
  const along = ax * R.dir.x + az * R.dir.z;
  const side = Math.abs(ax * R.dir.z - az * R.dir.x);
  return along >= -pad && along <= hdist(R.a, R.b) + pad && side <= half + pad;
}
/**
 * 부채꼴 띠 예고 (apex 에서 ang 방향 가운데, 벌린 각 angle°, inner ~ outer 칸): 바큇살 (판) + 바깥·안쪽 테두리
 */
function bandMarks(u, apex, ang, inner, outer, angle, ticks, color, alpha = 1) {
  const half = (angle * DEG) / 2;
  const life = Math.max(0.1, ticks / 20);
  const spokes = Math.max(3, Math.ceil(angle / 12));
  for (let i = 0; i <= spokes; i++) {
    const a = ang - half + (2 * half * i) / spokes;
    for (let r = inner + 1.5; r <= outer - 1; r += 3.2) fx(u.dim, "rpg:tele_fill", { x: apex.x + Math.cos(a) * r, y: u.center.y + 0.07, z: apex.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: 2.4, life, color, alpha: 0.4 * alpha, grow: 1 });
  }
  for (const [r, step] of [
    [outer, 2.2],
    [inner, 2.2],
  ]) {
    const n = Math.max(2, Math.ceil((r * 2 * half) / step));
    for (let i = 0; i <= n; i++) {
      const a = ang - half + (2 * half * i) / n;
      fx(u.dim, "rpg:tele_disc", { x: apex.x + Math.cos(a) * r, y: u.center.y + 0.08, z: apex.z + Math.sin(a) * r }, { dir: { x: 0, y: 0, z: 0 }, size: 0.8, life, color, alpha: 0.9 * alpha });
    }
  }
}
/** 점 l 이 부채꼴 띠 (apex, 가운데 각 ang, angle°, inner ~ outer) 안인지 (pad 칸 여유) */
export function inBand(l, apex, ang, inner, outer, angle, pad = 0.3) {
  const d = hdist(l, apex);
  if (d < inner - pad || d > outer + pad) return false;
  const off = Math.abs(wrapPi(Math.atan2(l.z - apex.z, l.x - apex.x) - ang));
  return off <= (angle * DEG) / 2 + pad / Math.max(1, d);
}

// ------------------------------------------------------------ 부위의 핵 (맞는 소품)
/** 핵 소품의 이름표: 부위 이름 + 남은 체력 막대 */
function coreTag(u) {
  const c = u.mem.core;
  if (!c?.e?.isValid) return;
  const P = u.mem.parts[c.kind];
  const max = u.def.parts[c.kind];
  const n = Math.max(0, Math.min(10, Math.ceil((P.hp / max) * 10)));
  const text = `§6${PART_NAMES[c.kind]}의 핵\n§c${"|".repeat(n)}§8${"|".repeat(10 - n)} §f${Math.round((P.hp / max) * 100)}%`;
  try {
    if (c.e.nameTag !== text) c.e.nameTag = text;
  } catch {
    // 무시
  }
}
/**
 * 핵이 놓일 자리: at 이 서 있는 구조물 (사슬 기둥 · 얼음 결정) 이나 담장에 걸리면 toward (몸·어깨) 쪽으로 0.5칸씩 당김 — 핵의 맞는 상자가 블록 안에 묻혀 때릴 수 없게 되지 않게.
 * 무너진 기둥의 밑동 (한 칸 높이) · 녹은 결정 (서리 바닥) 은 막지 않음
 * @returns {{x: number, y: number, z: number}}
 */
export function coreSpot(u, at, toward) {
  const m = u.mem;
  const c = u.center;
  const wall = u.enc.def.arena.radius - CORE_CLEAR.wall;
  const cheb = (x, z, q) => Math.max(Math.abs(x - q.at.x), Math.abs(z - q.at.z));
  const blocked = (x, z) => Math.hypot(x - c.x, z - c.z) > wall || m.chains.some((q) => !q.broken && cheb(x, z, q) < CORE_CLEAR.chain) || m.crystals.some((q) => !q.melted && cheb(x, z, q) < CORE_CLEAR.crystal);
  const to = toward ?? bodyOf(u);
  const L = hdist(at, to);
  const v = dirTo(at, to);
  for (let s = 0; s <= L; s += 0.5) {
    const x = at.x + v.x * s;
    const z = at.z + v.z * s;
    if (!blocked(x, z)) return { x, y: at.y, z };
  }
  // 줄 위가 모두 막힘 (거의 없음): at 둘레에서 가장 가까운 빈 자리
  for (let r = 1; r <= 10; r++) {
    for (let k = 0; k < 16; k++) {
      const x = at.x + Math.cos((k / 16) * TAU) * r;
      const z = at.z + Math.sin((k / 16) * TAU) * r;
      if (!blocked(x, z)) return { x, y: at.y, z };
    }
  }
  return { x: at.x, y: at.y, z: at.z };
}
/** 부위의 핵을 드러냄 (at = 바닥 자리 → coreSpot 으로 구조물에서 비킴, ticks 틱 뒤 cover). 이미 부서진 부위는 아님. 다른 핵이 드러나 있으면 먼저 거둠 */
function expose(u, kind, at0, ticks, toward) {
  const m = u.mem;
  const P = m.parts[kind];
  if (P.broken) return undefined;
  cover(u);
  const at = coreSpot(u, at0, toward);
  let e;
  try {
    e = u.dim.spawnEntity(CORE_ID, { x: at.x, y: u.center.y, z: at.z });
    u.api.addProp(u.enc, e, { hp: coreMax(u), textHeight: 2.4 });
  } catch {
    e = undefined;
  }
  if (!e) return undefined;
  try {
    e.getComponent("minecraft:health").setCurrentValue(Math.max(1, P.hp));
  } catch {
    // 무시
  }
  try {
    e.setProperty("rpg:part", PART_INDEX[kind]);
  } catch {
    // 무시
  }
  m.core = { kind, e, until: system.currentTick + ticks };
  coreTag(u);
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: u.center.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 2.6, life: 0.6, color: GOLD, alpha: 0.95 });
  fx(u.dim, "rpg:bash_burst", { x: at.x, y: u.center.y + 1.6, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2, life: 0.4, color: GOLD, alpha: 0.95 });
  play(u.dim, "block.bell.hit", at, 1.4, 0.7);
  explain(u, `core_${kind}`, `§6[보스] ${PART_NAMES[kind]}의 핵: §f${kind === "sword" ? "칼이 박힌 동안" : kind === "fist" ? "주먹이 땅에 있는 동안" : "쓰러진 동안"} 금빛 핵이 드러납니다 — 때려서 부수면 부위 파괴 §7(${PART_EFFECT[kind]})`);
  return e;
}
/** 핵 소품의 최대 체력 (가장 단단한 부위, build.py 의 소품 체력과 같음) */
export function coreMax(u) {
  const P = u.def.parts;
  return Math.max(P.sword, P.fist, P.crown);
}
/** 드러난 핵을 거둠 (부위 체력은 그대로) */
function cover(u) {
  const c = u.mem.core;
  if (!c) return;
  u.mem.core = undefined;
  u.api.dropProp(u.enc, c.e);
}
/** 부위 파괴: 핵이 부서짐 → 속성 (텍스처·불꽃), 패턴이 약해짐, 보상에 재료 더 */
function partBreak(u, kind, p) {
  const m = u.mem;
  const P = m.parts[kind];
  if (P.broken) return;
  P.broken = true;
  P.hp = 0;
  P.by = p?.name ?? "";
  m.brokenOrder.push(kind);
  const at = m.core?.e?.isValid ? { ...m.core.e.location } : bodyOf(u);
  cover(u);
  for (let i = 0; i < 3; i++) fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1 + i, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.5 + i, life: 0.6, color: i === 1 ? GOLD : FIRE, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 6, life: 0.8, color: GOLD, alpha: 0.95 });
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    fx(u.dim, "rpg:boulder", { x: at.x, y: at.y + 1.5, z: at.z }, { dir: { x: Math.cos(a) * 0.6, y: 0.8, z: Math.sin(a) * 0.6 }, speed: 9, size: 0.7, life: 0.9, spin: Math.random() * 360, color: [0.55, 0.3, 0.18], alpha: 1 });
  }
  play(u.dim, u.def.sounds.shatter, at, 0.7, 1);
  play(u.dim, u.def.sounds.erupt, at, 0.9, 1);
  shakeNear(u, at, 30, 0.35, 0.6);
  // 레바테인이 부서지면 불길이 꺼짐
  if (kind === "sword") m.trails = [];
  title(u, "§6§l부위 파괴!", `§f${PART_NAMES[kind]} §7— ${PART_EFFECT[kind]}`, fighters(u));
  say(u, `§6[보스] §e${p?.name ?? "누군가"}§f님이 ${PART_NAMES[kind]}을(를) 부쉈습니다! §7(${PART_EFFECT[kind]} · 보상 재료 더)`);
}

// ------------------------------------------------------------ 받는 피해가 늘어난 때 (빈틈 · 속박 · 반격)
/** 지금 받는 피해 보너스 {bonus, mark} (겹치면 가장 큰 것 하나 — 더하지 않음), 없으면 undefined */
export function openBonus(u, now) {
  const m = u.mem;
  if (!m) return undefined;
  let best;
  for (const [until, bonus, mark] of [
    [m.groggyUntil, u.def.inferno.bonus, MARKS.groggy],
    [m.bindUntil, u.def.bind.bonus, MARKS.bind],
    [m.counterUntil, u.def.counter.bonus, MARKS.counter],
  ]) {
    if (now < until && (!best || bonus > best.bonus)) best = { bonus, mark };
  }
  return best;
}

// ------------------------------------------------------------ 반격 (횡베기 · 찌르기)
/** 반격 창 열기: 칼이 금빛으로 번쩍임, 예고 자리가 금빛, 눈앞 "반격!" */
function openCounter(u, kind, show) {
  const m = u.mem;
  const now = system.currentTick;
  const d = u.def[kind];
  m.counter = { kind, from: now, until: now + d.window };
  const s = shoulderAt(u);
  const at = { x: s.x, y: u.center.y + 12, z: s.z };
  fx(u.dim, "rpg:bash_burst", at, { dir: { x: 0, y: 0, z: 1 }, size: 5, life: 0.5, color: GOLD, alpha: 1 });
  fx(u.dim, "rpg:shock_ring", { x: s.x, y: u.center.y + 0.15, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, size: 6, life: d.window / 20, color: GOLD, alpha: 0.95 });
  show?.();
  play(u.dim, u.def.sounds.parry, at, 0.8, 1);
  play(u.dim, "random.anvil_use", at, 1.6, 0.6);
  faceAll(u, "§e§l반격! §f스킬·룬으로 수르트를", { dist: 2.4, up: 0.55 }, fighters(u));
}
/** 반격 성공: 하던 패턴을 끊고 비틀거림, time 틱 동안 받는 피해 +bonus */
function counterHit(u, p, now) {
  const m = u.mem;
  const C = u.def.counter;
  const kind = m.counter?.kind ?? "sweep";
  m.counter = undefined;
  u.cast = undefined;
  u.staggerUntil = now + C.stagger;
  m.counterUntil = now + C.time;
  m.counters++;
  m.body.want = undefined;
  u.api.anim(u, "recoil");
  const s = shoulderAt(u);
  const at = { x: s.x, y: u.center.y + 10, z: s.z };
  for (let i = 0; i < 3; i++) fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + i * 3, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 4 + i, life: 0.5, color: GOLD, alpha: 1 });
  fx(u.dim, "rpg:shock_ring", { x: s.x, y: u.center.y + 0.1, z: s.z }, { dir: { x: 0, y: 0, z: 0 }, size: 9, life: 0.7, color: GOLD, alpha: 0.95 });
  play(u.dim, u.def.sounds.counter, at, 0.6, 1);
  play(u.dim, u.def.sounds.parry, at, 1.2, 1);
  shakeNear(u, at, 30, 0.3, 0.5);
  showFaceText(p, "§6§l반격 성공!", { dist: 2.4, up: 0.6 });
  title(u, "§6§l반격!", `§e${p.name}§f님이 ${kind === "thrust" ? "찌르기" : "횡베기"}를 받아쳤습니다 §7— ${Math.round(C.time / 20)}초 동안 받는 피해 +${Math.round(C.bonus * 100)}%`, fighters(u));
}

// ------------------------------------------------------------ 레바테인 내려찍기 (평타)
/**
 * 대상이 칼 줄(오른 어깨에서 앞) 위에 오게 하는 몸 방향. 몸에 바짝 붙은 사람은 줄 가운데에 올 수 없으므로 직사각형 시작에서 0.5칸 안쪽에 오게
 * @param {number} [from] 줄 시작 (어깨에서 칸)
 */
export function slamYaw(u, l, from = u.def.melee.from) {
  const b = u.mem.body;
  const D = Math.hypot(l.x - b.x, l.z - b.z);
  const th = Math.atan2(l.z - b.z, l.x - b.x);
  const arm = u.def.drive.arm;
  const along = Math.max(Math.sqrt(Math.max(0, D * D - arm * arm)), Math.min(D, from + 0.5));
  const side = Math.sqrt(Math.max(0, D * D - along * along));
  return th - Math.atan2(side, along);
}
/** 어깨 밑에서 앞으로 from ~ reach 칸 직사각형 (시작·끝·방향) */
export function lineRect(u, yaw, from, reach) {
  const s = shoulderAt(u, yaw);
  const f = dirOf(yaw);
  return { a: { x: s.x + f.x * from, y: s.y, z: s.z + f.z * from }, b: { x: s.x + f.x * reach, y: s.y, z: s.z + f.z * reach }, dir: f };
}
export function slamRect(u, yaw) {
  const m = u.def.melee;
  return lineRect(u, yaw, m.from, m.reach);
}
/**
 * 칼 줄 위의 온전한 사슬 기둥 (가장 가까운 것): 몸에서 bind.min 칸 넘게, 줄 시작 ~ 끝 사이, 옆으로 반폭 + 기둥 반폭 안 → {c, along} (줄 시작에서 기둥 가운데까지)
 */
export function bindPillar(u, R, width = u.def.melee.width) {
  const B = u.def.bind;
  const b = bodyOf(u);
  let best;
  let bestAlong = Infinity;
  for (const c of u.mem.chains) {
    if (!c.intact) continue;
    const ax = c.at.x - R.a.x;
    const az = c.at.z - R.a.z;
    const along = ax * R.dir.x + az * R.dir.z;
    const side = Math.abs(ax * R.dir.z - az * R.dir.x);
    if (along < -c.half || along > hdist(R.a, R.b) + c.half || side > width / 2 + c.half || hdist(c.at, b) < B.min) continue;
    if (along < bestAlong) {
      best = c;
      bestAlong = along;
    }
  }
  return best ? { c: best, along: bestAlong } : undefined;
}
/** 직사각형을 기둥 앞에서 자름 (기둥 뒤는 맞지 않음) */
function cutAt(R, along, half) {
  const L = Math.max(0, along - half);
  return { a: R.a, b: { x: R.a.x + R.dir.x * L, y: R.a.y, z: R.a.z + R.dir.z * L }, dir: R.dir };
}

/** 내려찍기 대상: 몸 앞 ±aim° · 칼이 닿는 사람 중 band 칸 사이의 가장 가까운 사람 (없으면 가장 가까운 사람) */
function slamTarget(u, list) {
  const m = u.def.melee;
  const b = u.mem.body;
  const reachC = Math.hypot(m.reach, u.def.drive.arm);
  const cand = list.filter((p) => level(u, p) && hdist(p.location, b) <= reachC && offAngle(u, p.location) <= m.aim * DEG);
  const band = cand.filter((p) => {
    const d = hdist(p.location, b);
    return d >= m.band[0] && d <= m.band[1];
  });
  // v2.20.0 도발 (아이기스의 방패): 칼이 닿는 사람 가운데 도발한 사람이 있으면 그 사람 (띠 밖이어도)
  const taunted = meleeTarget(u, cand, b);
  if (taunted && u.taunt?.id === taunted.id && system.currentTick < u.taunt.until) return taunted;
  return meleeTarget(u, band.length ? band : cand, b);
}

function slam(u, now, list) {
  const m = u.def.melee;
  const b = u.mem.body;
  const t = slamTarget(u, list);
  if (!t) return false;
  const yaw = slamYaw(u, t.location);
  const R = slamRect(u, yaw);
  const pin = bindPillar(u, R);
  const hitR = pin ? cutAt(R, pin.along, pin.c.half) : R;
  b.want = yaw;
  if (hdist(hitR.a, hitR.b) > 0.2) rectMarks(u, hitR, m.width, m.windup, RED);
  // 칼을 붙잡을 기둥: 둘레에 사슬빛 고리 (예고 동안)
  if (pin) {
    disc(u.dim, { x: pin.c.at.x, y: u.center.y, z: pin.c.at.z }, pin.c.half + 1.6, m.windup, CHAIN, { fill: false, alpha: 0.95 });
    for (const h of [4, 9, 14]) fx(u.dim, "rpg:bash_ring", { x: pin.c.at.x, y: u.center.y + h, z: pin.c.at.z }, { dir: { x: 0, y: 1, z: 0 }, size: 2.6, life: m.windup / 20, color: CHAIN, alpha: 0.9 });
  }
  u.api.anim(u, "slam");
  sound(u, u.def.sounds.swing, shoulderAt(u, yaw), 0.5, 1);
  explain(u, "slam", "§6[보스] 레바테인 내려찍기: §f수르트가 칼을 들면 오른 어깨에서 긴 붉은 줄이 뜹니다 — 줄 옆으로 비키세요. §e칼이 박힌 동안 칼끝 쪽 레바테인의 핵을 때리면 칼이 부서집니다");
  if (pin) explain(u, "bind", `§b[보스] 봉인의 사슬 기둥: §f수르트와 나 사이에 사슬 기둥이 오게 서면 내려찍은 칼을 기둥의 사슬이 붙잡습니다 §7— ${Math.round(u.def.bind.time / 20)}초 속박, 받는 피해 +${Math.round(u.def.bind.bonus * 100)}%, 레바테인의 핵 (기둥은 한 번 쓰면 부서짐)`);
  u.api.cast(u, "slam", m.windup + m.embed + 12 + GAP, [
    [m.windup, (x) => slamHit(x, R, hitR, pin, yaw)],
    [m.windup + m.embed, (x) => cover(x)],
  ]);
  return true;
}

function slamHit(u, R, hitR, pin, yaw) {
  const m = u.def.melee;
  const now = system.currentTick;
  const L = hdist(hitR.a, hitR.b);
  if (L > 0.2) {
    for (const p of fighters(u)) {
      if (!level(u, p) || !inRect(p.location, hitR, m.width / 2)) continue;
      const kk = keepIn(u, p, R.dir.x * m.knock, R.dir.z * m.knock);
      bossHit(u, p, m.damage, { knock: { x: kk.x, z: kk.z, h: 0.35 }, directional: true });
    }
  }
  // 칼이 떨어진 줄: 불 터짐·금·먼지 (4칸마다), 끝에 큰 불
  for (let s = 0; s <= L; s += 4) {
    const at = { x: hitR.a.x + R.dir.x * s, y: u.center.y, z: hitR.a.z + R.dir.z * s };
    scorch(u.dim, at, 4);
    if (!u.mem.parts.sword.broken) flame(u.dim, at, 3.2, 0.7);
  }
  const end = { x: hitR.b.x, y: u.center.y, z: hitR.b.z };
  blast(u.dim, end, 4.5, u.mem.parts.sword.broken ? ASH : FIRE);
  sound(u, u.def.sounds.hit, end, 0.6, 1);
  shakeNear(u, end, 26, 0.35, 0.6);
  if (pin) {
    bind(u, pin, R, now);
    return;
  }
  // 레바테인이 온전하면 지나간 자리가 불탐
  if (!u.mem.parts.sword.broken) {
    u.mem.trails.push({ R: hitR, until: now + u.def.trail.time, next: now + u.def.trail.tick });
    explain(u, "trail", "§6[보스] §f레바테인이 지나간 자리는 한동안 불탑니다 §7(불길 안에서는 계속 피해 · 칼을 부수면 사라짐)");
  }
  // 레바테인의 핵: 어깨 앞 core 칸 (칼끝 쪽, 바닥에 박힌 칼날 — 구조물에 걸리면 칼날을 따라 어깨 쪽으로)
  const s = shoulderAt(u, yaw);
  const f = dirOf(yaw);
  expose(u, "sword", { x: s.x + f.x * m.core, y: u.center.y, z: s.z + f.z * m.core }, m.embed, s);
}

/** 사슬 기둥이 칼을 붙잡음: 속박 (time 틱, 받는 피해 +bonus), 칼의 핵은 기둥 앞, 기둥은 쓰임 (속박이 풀릴 때 무너짐) */
function bind(u, pin, R, now) {
  const B = u.def.bind;
  const m = u.mem;
  const c = pin.c;
  c.intact = false;
  m.bindUntil = now + B.time;
  m.binds++;
  u.cast = undefined;
  m.body.want = undefined;
  u.api.anim(u, "rest");
  hold(u, "bound");
  for (const h of [3, 7, 11, 15]) fx(u.dim, "rpg:bash_ring", { x: c.at.x, y: u.center.y + h, z: c.at.z }, { dir: { x: 0, y: 1, z: 0 }, size: 3, life: 0.6, color: CHAIN, alpha: 0.95 });
  dust(u.dim, floorAt(u, c.at.x, c.at.z), 5, [0.6, 0.6, 0.65], 1.0);
  play(u.dim, u.def.sounds.chain, c.at, 0.6, 1);
  play(u.dim, "random.anvil_land", c.at, 0.5, 0.8);
  title(u, "§b§l사슬이 레바테인을 붙잡았습니다!", `§f${Math.round(B.time / 20)}초 동안 받는 피해 §e+${Math.round(B.bonus * 100)}% §7· 레바테인의 핵`, fighters(u));
  // 칼의 핵: 기둥 바로 앞 (수르트 쪽, 기둥에 묻히지 않게 칼날을 따라 어깨 쪽으로 당김)
  const from = u.def.melee.from;
  expose(u, "sword", { x: c.at.x - R.dir.x * (c.half + 1.4), y: u.center.y, z: c.at.z - R.dir.z * (c.half + 1.4) }, B.time, { x: R.a.x - R.dir.x * from, y: u.center.y, z: R.a.z - R.dir.z * from });
  u.api.cast(u, "bound", B.time + GAP, [[B.time, (x) => unbind(x, c)]]);
}
function unbind(u, c) {
  cover(u);
  crumble(u, c);
  release(u, "unbind");
  play(u.dim, u.def.sounds.shatter, c.at, 0.8, 1);
  play(u.dim, u.def.sounds.roar, headAt(u), 0.9, 0.8);
}

/** 불길 (내려찍은 자리): 8틱마다 불꽃, tick 틱마다 안의 사람 피해 + 불 */
function trailTick(u, now) {
  const m = u.mem;
  const T = u.def.trail;
  if (!m.trails.length) return;
  m.trails = m.trails.filter((t) => now < t.until);
  for (const t of m.trails) {
    if (now % 8 === 0) {
      const L = hdist(t.R.a, t.R.b);
      const r = rightOf(angOf(t.R.dir));
      for (let s = 1; s <= L; s += 3.5) {
        const side = ((Math.floor(s) % 3) - 1) * 1.4;
        flame(u.dim, { x: t.R.a.x + t.R.dir.x * s + r.x * side, y: u.center.y, z: t.R.a.z + t.R.dir.z * s + r.z * side }, 2.2, 0.55, s % 2 < 1 ? FIRE : LAVA);
      }
    }
    if (now < t.next) continue;
    t.next = now + T.tick;
    for (const p of fighters(u)) {
      if (!level(u, p) || !inRect(p.location, t.R, u.def.melee.width / 2, 0)) continue;
      if (bossHit(u, p, T.damage, { noSource: true })) burn(p, T.burn);
    }
  }
}

// ------------------------------------------------------------ 횡베기 (반격)
function sweep(u, now, list) {
  const d = u.def.sweep;
  const b = u.mem.body;
  const cand = list.filter((p) => level(u, p) && hdist(p.location, b) >= d.inner && hdist(p.location, b) <= d.outer);
  if (!cand.length) return false;
  // 지금 보는 쪽에서 가장 덜 돌아도 되는 사람
  cand.sort((x, y) => offAngle(u, x.location) - offAngle(u, y.location));
  const center = angOf(dirTo(b, cand[0].location));
  const yaw = center - d.offset * DEG;
  const apex = { x: b.x, y: u.center.y, z: b.z };
  b.want = yaw;
  bandMarks(u, apex, center, d.inner, d.outer, d.angle, d.windup, RED);
  u.api.anim(u, "sweep");
  sound(u, u.def.sounds.swing, headAt(u), 0.4, 1);
  explain(u, "sweep", `§e[보스] 횡베기: §f수르트가 칼을 옆으로 당기면 앞쪽 넓은 부채꼴 (몸에서 ${d.inner}~${d.outer}칸) 을 쓸어 버립니다 — 안쪽이나 바깥으로. §e칼이 금빛으로 번쩍이는 동안 스킬·룬으로 수르트를 때리면 반격!`);
  u.api.cast(u, "sweep", d.windup + 24 + GAP, [
    [d.windup - d.window, (x) => openCounter(x, "sweep", () => bandMarks(x, apex, center, d.inner, d.outer, d.angle, d.window, GOLD, 0.9))],
    [d.windup, (x) => sweepHit(x, apex, center)],
  ]);
  return true;
}
function sweepHit(u, apex, center) {
  const d = u.def.sweep;
  u.mem.counter = undefined;
  for (const p of fighters(u)) {
    if (!level(u, p) || !inBand(p.location, apex, center, d.inner, d.outer, d.angle)) continue;
    const out = dirTo(apex, p.location);
    const kk = keepIn(u, p, out.x * d.knock, out.z * d.knock);
    if (bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.4 }, directional: true })) burn(p, d.burn);
  }
  // 칼이 지나간 띠: 불꽃 줄 (10°마다) + 큰 초승달 참격
  const half = (d.angle * DEG) / 2;
  const mid = (d.inner + d.outer) / 2;
  for (let a = center - half; a <= center + half + 1e-6; a += 10 * DEG) {
    const at = { x: apex.x + Math.cos(a) * mid, y: u.center.y, z: apex.z + Math.sin(a) * mid };
    flame(u.dim, at, 4, 0.6);
    dust(u.dim, at, 3, ASH, 0.6);
  }
  fx(u.dim, "rpg:slash_heavy_r_glow", { x: apex.x + Math.cos(center) * mid * 0.5, y: u.center.y + 3, z: apex.z + Math.sin(center) * mid * 0.5 }, { dir: dirOf(center), speed: 0, spin: 0, size: d.outer * 0.7, life: 0.3, color: FIRE, alpha: 0.95 });
  sound(u, u.def.sounds.hit, apex, 0.7, 1);
  play(u.dim, u.def.sounds.fire, apex, 0.6, 1);
  shakeNear(u, apex, d.outer + 4, 0.3, 0.5);
}

// ------------------------------------------------------------ 찌르기 (반격)
function thrust(u, now, list) {
  const d = u.def.thrust;
  const b = u.mem.body;
  const reachC = Math.hypot(d.reach, u.def.drive.arm);
  const cand = list.filter((p) => level(u, p) && hdist(p.location, b) >= u.def.melee.band[0] && hdist(p.location, b) <= reachC);
  if (!cand.length) return false;
  cand.sort((x, y) => hdist(y.location, b) - hdist(x.location, b));
  const yaw = slamYaw(u, cand[0].location, d.from);
  const R = lineRect(u, yaw, d.from, d.reach);
  b.want = yaw;
  rectMarks(u, R, d.width, d.windup, RED);
  u.api.anim(u, "thrust");
  sound(u, u.def.sounds.swing, shoulderAt(u, yaw), 0.7, 1);
  explain(u, "thrust", "§e[보스] 찌르기: §f가장 먼 사람 쪽으로 긴 줄 — 옆으로 비키세요. §e칼이 금빛일 때 스킬·룬으로 수르트를 때리면 반격!");
  u.api.cast(u, "thrust", d.windup + 20 + GAP, [
    [d.windup - d.window, (x) => openCounter(x, "thrust", () => rectMarks(x, R, d.width, d.window, GOLD))],
    [d.windup, (x) => thrustHit(x, R)],
  ]);
  return true;
}
function thrustHit(u, R) {
  const d = u.def.thrust;
  u.mem.counter = undefined;
  for (const p of fighters(u)) {
    if (!level(u, p) || !inRect(p.location, R, d.width / 2)) continue;
    const kk = keepIn(u, p, R.dir.x * d.knock, R.dir.z * d.knock);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: 0.3 }, directional: true });
  }
  const L = hdist(R.a, R.b);
  for (let s = 0; s <= L; s += 3) {
    const at = { x: R.a.x + R.dir.x * s, y: u.center.y, z: R.a.z + R.dir.z * s };
    fx(u.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: R.dir, size: 1.6, life: 0.3, delay: s / 60, color: s % 6 < 3 ? FIRE : GOLD, alpha: 0.9 });
  }
  blast(u.dim, { x: R.b.x, y: u.center.y, z: R.b.z }, 3.5);
  sound(u, u.def.sounds.hit, R.b, 0.9, 1);
}

// ------------------------------------------------------------ 불의 주먹 (건틀릿의 핵)
/** 주먹이 떨어지는 자리 (몸 기준 앞 at[0] · 오른쪽 at[1]) */
export function fistAt(u, yaw) {
  const b = u.mem.body;
  const [fw, side] = u.def.fist.at;
  const f = dirOf(yaw ?? b.yaw);
  const r = rightOf(yaw ?? b.yaw);
  return { x: b.x + f.x * fw + r.x * side, y: u.center.y, z: b.z + f.z * fw + r.z * side };
}
function fist(u, now, list) {
  const d = u.def.fist;
  const m = u.mem;
  if (m.parts.fist.broken) return false;
  const b = m.body;
  const D0 = Math.hypot(d.at[0], d.at[1]);
  const off = Math.atan2(d.at[1], d.at[0]);
  const cand = list.filter((p) => level(u, p) && Math.abs(hdist(p.location, b) - D0) <= d.radius - 1);
  if (!cand.length) return false;
  cand.sort((x, y) => Math.abs(wrapPi(angOf(dirTo(b, x.location)) - off - b.yaw)) - Math.abs(wrapPi(angOf(dirTo(b, y.location)) - off - b.yaw)));
  const yaw = angOf(dirTo(b, cand[0].location)) - off;
  const at = fistAt(u, yaw);
  b.want = yaw;
  disc(u.dim, at, d.radius, d.windup, RED);
  u.api.anim(u, "fist");
  sound(u, u.def.sounds.swing, at, 0.6, 1);
  explain(u, "fist", "§6[보스] 불의 주먹: §f왼 주먹으로 땅을 내려칩니다 (붉은 원). §e주먹이 땅에 있는 동안 건틀릿의 핵을 때리면 부서집니다 §7(불의 주먹이 사라지고 불비가 약해짐)");
  u.api.cast(u, "fist", d.windup + d.rest + 20 + GAP, [
    [d.windup, (x) => fistHit(x, at)],
    [d.windup + d.rest, (x) => cover(x)],
  ]);
  return true;
}
function fistHit(u, at) {
  const d = u.def.fist;
  for (const p of fighters(u)) {
    if (!level(u, p) || hdist(p.location, at) > d.radius + 0.3) continue;
    const out = dirTo(at, p.location);
    const kk = keepIn(u, p, out.x * 0.8, out.z * 0.8);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: d.lift } });
  }
  blast(u.dim, at, d.radius * 0.8, LAVA);
  fx(u.dim, "rpg:tele_wave", { x: at.x, y: at.y + 0.2, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 24, size: 1, life: d.radius / 24, color: FIRE, alpha: 0.9 });
  scorch(u.dim, at, d.radius * 1.6);
  sound(u, u.def.sounds.hit, at, 0.5, 1);
  play(u.dim, u.def.sounds.lava, at, 0.8, 1);
  shakeNear(u, at, 24, 0.35, 0.6);
  expose(u, "fist", at, d.rest);
}

// ------------------------------------------------------------ 무스펠의 불비 (메테오)
/** 필드 안 무작위 자리 (가운데에서 R-4 칸 안) */
function randomSpot(u) {
  const R = u.enc.def.arena.radius - 4;
  const a = Math.random() * TAU;
  const r = Math.sqrt(Math.random()) * R;
  return floorAt(u, u.center.x + Math.cos(a) * r, u.center.z + Math.sin(a) * r);
}
function meteors(u, now, list) {
  const d = u.def.meteors;
  const m = u.mem;
  const people = list.filter((p) => level(u, p));
  if (!people.length) return false;
  const spots = people.map((p) => ({ x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z }));
  const extra = m.parts.fist.broken ? d.extraBroken : d.extra;
  for (let i = 0; i < extra; i++) spots.push(randomSpot(u));
  for (const s of spots) {
    disc(u.dim, s, d.radius, d.windup, RED);
    fx(u.dim, "rpg:skill_rune", { x: s.x, y: s.y + 18, z: s.z }, { dir: { x: 0, y: 1, z: 0 }, size: d.radius * 0.9, life: d.windup / 20, spin: 0, speed: 40, grow: 0, progress: 1, color: FIRE, alpha: 0.75 });
  }
  m.body.want = undefined;
  u.api.anim(u, "summon");
  sound(u, u.def.sounds.charge, headAt(u), 0.7, 1);
  faceAll(u, "§6§l무스펠의 불비! §f발밑 원 밖으로", { dist: 2.4, up: 0.45 }, people);
  explain(u, "meteors", "§6[보스] 무스펠의 불비: §f수르트가 왼손을 들면 모두의 발밑과 곳곳에 붉은 원 — 메테오가 떨어지기 전에 원 밖으로 §7(왼 건틀릿을 부수면 곳곳의 원이 없어짐)");
  u.api.cast(u, "meteors", d.windup + 12 + GAP, [
    [d.windup - d.fall, (x) => meteorFly(x, spots)],
    [d.windup, (x) => meteorHit(x, spots)],
  ]);
  return true;
}
/** 메테오가 날아옴 (수르트 쪽 하늘에서 비스듬히, fall 틱 뒤 원에 닿음) */
function meteorFly(u, spots) {
  const d = u.def.meteors;
  const b = bodyOf(u);
  const t = d.fall / 20;
  for (const s of spots) {
    const away = dirTo(b, s);
    const from = { x: s.x - away.x * 12, y: s.y + 30, z: s.z - away.z * 12 };
    const v = { x: s.x - from.x, y: s.y + 0.4 - from.y, z: s.z - from.z };
    const L = Math.hypot(v.x, v.y, v.z);
    fx(u.dim, "rpg:meteor", from, { dir: { x: v.x / L, y: v.y / L, z: v.z / L }, speed: L / t, size: 3.2, life: t, color: [1, 1, 1], alpha: 1 });
  }
  play(u.dim, u.def.sounds.meteor, b, 0.6, 1);
}
function meteorHit(u, spots) {
  const d = u.def.meteors;
  const hit = new Set();
  for (const s of spots) {
    blast(u.dim, s, d.radius, FIRE);
    scorch(u.dim, s, d.radius * 2);
    play(u.dim, u.def.sounds.erupt, s, 0.9, 0.8);
    for (const p of fighters(u)) {
      if (hit.has(p.id) || hdist(p.location, s) > d.radius + 0.3 || Math.abs(p.location.y - s.y) > 3) continue;
      hit.add(p.id);
      if (bossHit(u, p, d.damage, { noSource: true, cause: EntityDamageCause.projectile })) burn(p, d.burn);
    }
  }
}

// ------------------------------------------------------------ 불의 고리 (뛰어넘기)
function rings(u, now, list) {
  const d = u.def.rings;
  const m = u.mem;
  if (!list.some((p) => level(u, p))) return false;
  const waves = m.parts.crown.broken ? d.wavesBroken : d.waves;
  m.body.want = undefined;
  u.api.anim(u, "plunge");
  sound(u, u.def.sounds.swing, headAt(u), 0.6, 1);
  explain(u, "rings", "§6[보스] 불의 고리: §f칼을 땅에 꽂으면 수르트에게서 불의 고리가 퍼집니다 — 고리가 지나갈 때 뛰어서 넘으세요 §7(불꽃 왕관을 부수면 고리가 줄어듦)");
  /** @type {[number, (x: any) => void][]} */
  const steps = [];
  for (let k = 0; k < waves; k++) steps.push([d.plunge + k * d.gap, (x) => ringStart(x)]);
  u.api.cast(u, "rings", d.plunge + (waves - 1) * d.gap + 30 + GAP, steps);
  return true;
}
function ringStart(u) {
  const d = u.def.rings;
  const b = bodyOf(u);
  const R = u.enc.def.arena.radius + 6 + hdist(b, u.center);
  const life = R / d.speed;
  u.mem.rings.push({ x: b.x, z: b.z, t0: system.currentTick, last: 0, max: R, hit: new Set() });
  fx(u.dim, "rpg:fire_wave", { x: b.x, y: u.center.y, z: b.z }, { speed: d.speed, size: 1.8, life, color: FIRE, alpha: 0.95 });
  fx(u.dim, "rpg:tele_wave", { x: b.x, y: u.center.y + 0.2, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, speed: d.speed, size: 1, life, color: LAVA, alpha: 0.95 });
  blast(u.dim, floorAt(u, b.x, b.z), 6, FIRE);
  play(u.dim, u.def.sounds.fire, b, 0.5, 1);
  play(u.dim, u.def.sounds.lava, b, 0.7, 1);
  shakeNear(u, b, 30, 0.2, 0.4);
}
/** 고리마다: 앞머리가 지나간 사람 (땅에 있으면) 피해 + 불, 뛰는 중이면 넘음 */
function ringTick(u, now) {
  const d = u.def.rings;
  const m = u.mem;
  if (!m.rings.length) return;
  const list = fighters(u);
  for (const g of m.rings) {
    const front = (d.speed * (now - g.t0)) / 20;
    const prev = g.last;
    g.last = front;
    for (const p of list) {
      if (g.hit.has(p.id) || !level(u, p)) continue;
      const r = Math.hypot(p.location.x - g.x, p.location.z - g.z);
      if (r < prev - 0.3 || r > front + 0.3) continue;
      g.hit.add(p.id);
      if (p.isOnGround === false) {
        showFaceText(p, "§a뛰어넘음!", { dist: 2.2, up: 0.2 });
        continue;
      }
      if (bossHit(u, p, d.damage, { noSource: true })) burn(p, d.burn);
    }
  }
  m.rings = m.rings.filter((g) => g.last < g.max);
}

// ------------------------------------------------------------ 발 구르기
function stomp(u, now, list) {
  const d = u.def.stomp;
  const b = u.mem.body;
  const flank = list.filter((p) => level(u, p) && hdist(p.location, b) <= d.radius && offAngle(u, p.location) > (d.front / 2) * DEG);
  if (!flank.length) return false;
  const at = floorAt(u, b.x, b.z);
  b.want = undefined;
  disc(u.dim, at, d.radius, d.windup, RED);
  u.api.anim(u, "stomp");
  sound(u, u.def.sounds.roar, headAt(u), 1.3, 0.5);
  explain(u, "stomp", "§6[보스] 발 구르기: §f수르트의 옆이나 뒤에 붙어 있으면 발을 굴러 몸 둘레 (붉은 원) 를 칩니다 — 잠깐 물러나세요");
  u.api.cast(u, "stomp", d.windup + 12 + GAP, [[d.windup, (x) => stompHit(x, at)]]);
  return true;
}
function stompHit(u, at) {
  const d = u.def.stomp;
  for (const p of fighters(u)) {
    if (!level(u, p) || hdist(p.location, at) > d.radius + 0.3) continue;
    const dir = dirTo(at, p.location);
    const kk = keepIn(u, p, dir.x * 1.0, dir.z * 1.0);
    bossHit(u, p, d.damage, { knock: { x: kk.x, z: kk.z, h: d.lift } });
  }
  fx(u.dim, "rpg:tele_wave", { x: at.x, y: at.y + 0.2, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, speed: 30, size: 1, life: d.radius / 30, color: FIRE, alpha: 0.9 });
  landFx(u, at, 6);
}

// ------------------------------------------------------------ 무스펠의 업화 (무력화)
/** 업화 막대 소품 (보스 막대만 보임): 가운데 바닥 */
function spawnGauge(u) {
  let e;
  try {
    e = u.dim.spawnEntity(GAUGE_ID, { x: u.center.x, y: u.center.y + 0.5, z: u.center.z });
    u.api.addProp(u.enc, e);
    e.nameTag = "§6무스펠의 업화";
  } catch {
    e = undefined;
  }
  return e;
}
/** 업화 막대 = 남은 업화 비율 */
function gaugeTick(u) {
  const I = u.mem.inferno;
  if (!I?.gauge?.isValid) return;
  const v = Math.max(1, Math.ceil((GAUGE_MAX * Math.max(0, I.left)) / I.need));
  if (I.shown === v) return;
  I.shown = v;
  try {
    I.gauge.getComponent("minecraft:health").setCurrentValue(v);
  } catch {
    // 무시
  }
}
function inferno(u, now, list) {
  const I = u.def.inferno;
  const m = u.mem;
  const n = Math.max(I.min, list.length);
  const need = Math.round(n * I.per * (m.parts.crown.broken ? I.crownCut : 1));
  m.inferno = { need, left: need, t0: now, until: now + I.time, gauge: spawnGauge(u), nextErupt: now + 30, erupts: [], shown: -1 };
  gaugeTick(u);
  m.body.want = undefined;
  u.api.anim(u, "rest");
  hold(u, "channel");
  sound(u, u.def.sounds.charge, headAt(u), 0.5, 1);
  play(u.dim, u.def.sounds.roar, headAt(u), 0.6, 1);
  title(u, "§6§l무스펠의 업화", `§f업화가 모이기 전에 §e피해로 업화 막대§f를 깎으세요! §7(${Math.round(I.time / 20)}초)`, list);
  explain(u, "inferno", `§6[보스] 무스펠의 업화 (무력화): §f수르트가 칼을 하늘로 들고 업화를 모읍니다. §e업화 막대를 ${Math.round(I.time / 20)}초 안에 피해로 다 깎으면 쓰러져 ${Math.round(I.groggy / 20)}초 동안 받는 피해 +${Math.round(I.bonus * 100)}% (빈틈), 불꽃 왕관의 핵이 드러납니다. §c못 깎으면 업화 폭발 §7(모두 큰 피해, 수르트 회복)`);
  say(u, `§6[보스] §f이번 업화: §e${need}§f 피해 §7(싸우는 사람 ${n}명${m.parts.crown.broken ? " · 불꽃 왕관이 부서져 쉬움" : ""})`);
  u.api.cast(u, "inferno", I.time + 10, [[I.time, (x) => infernoFail(x)]]);
}
/** 업화 동안 매 틱: 용암 분출 (사람마다 곁에 원) · 업화 막대 · 알림 · 3·2·1 · 불꽃이 모임 */
function infernoTick(u, now) {
  const m = u.mem;
  const G = m.inferno;
  if (!G) return;
  const I = u.def.inferno;
  // 새 분출은 업화가 끝나기 전에 터질 수 있을 때만 (예고만 뜨고 터지지 않는 원이 없게)
  if (now >= G.nextErupt && G.until - now >= I.eruptWindup) {
    G.nextErupt = now + I.erupt;
    for (const p of fighters(u)) {
      if (!level(u, p)) continue;
      const a = Math.random() * TAU;
      const r = Math.random() * 2.5;
      const at = { x: p.location.x + Math.cos(a) * r, y: groundUnder(p, u.center.y), z: p.location.z + Math.sin(a) * r };
      disc(u.dim, at, I.eruptRadius, I.eruptWindup, RED);
      G.erupts.push({ at, hitAt: now + I.eruptWindup });
    }
  }
  for (const e of G.erupts.filter((x) => now >= x.hitAt)) {
    blast(u.dim, e.at, I.eruptRadius, LAVA);
    flame(u.dim, e.at, I.eruptRadius * 1.6, 0.8, FIRE);
    play(u.dim, u.def.sounds.lava, e.at, 1.0, 0.8);
    for (const p of fighters(u)) {
      if (hdist(p.location, e.at) > I.eruptRadius + 0.3 || Math.abs(p.location.y - e.at.y) > 3) continue;
      bossHit(u, p, I.eruptDamage, { noSource: true, knock: { x: 0, z: 0, h: 0.6 } });
    }
  }
  G.erupts = G.erupts.filter((x) => now < x.hitAt);
  gaugeTick(u);
  const left = G.until - now;
  if (now % 10 === 0) {
    const pct = Math.max(0, Math.ceil((G.left / G.need) * 100));
    for (const p of fighters(u)) notify(p, `§6업화 §f${pct}% 남음 §7— 수르트를 때려 깎으세요 §8(${Math.ceil(left / 20)}초)`, 15);
  }
  if (left > 0 && left % 20 === 0 && left <= 60) faceAll(u, `§6§l업화 ${left / 20}`, { dist: 2.4, up: 0.45 }, fighters(u));
  // 몸 둘레로 불꽃이 모임
  if (now % 4 === 0) {
    const b = bodyOf(u);
    const a = Math.random() * TAU;
    const r = 6 + Math.random() * 6;
    flame(u.dim, { x: b.x + Math.cos(a) * r, y: u.center.y, z: b.z + Math.sin(a) * r }, 2.5 + ((now - G.t0) / I.time) * 3, 0.6, LAVA);
  }
  if (now % 20 === 0) fx(u.dim, "rpg:ember", { x: bodyOf(u).x, y: u.center.y + 18, z: bodyOf(u).z }, { size: 8, life: 1.0, speed: 3, color: EMBER, alpha: 0.9 });
}
/** 맞은 피해만큼 업화를 깎음 → 다 깎으면 쓰러짐 */
function gaugeHit(u, amount) {
  const G = u.mem.inferno;
  if (!G || !(amount > 0)) return;
  G.left -= amount;
  gaugeTick(u);
  if (G.left <= 0) infernoBreak(u);
}
function dropGauge(u, G) {
  if (G?.gauge) u.api.dropProp(u.enc, G.gauge);
}
/** 업화를 막음: 쓰러짐 (그로기) — groggy 틱 동안 받는 피해 +bonus, 불꽃 왕관의 핵 */
function infernoBreak(u) {
  const I = u.def.inferno;
  const m = u.mem;
  const now = system.currentTick;
  const G = m.inferno;
  m.inferno = undefined;
  dropGauge(u, G);
  // 아직 터지지 않은 용암 분출은 식어 꺼짐 (재 연기)
  for (const e of G?.erupts ?? []) dust(u.dim, e.at, I.eruptRadius * 1.2, ASH, 0.7);
  if (G?.erupts?.length) play(u.dim, u.def.sounds.melt, G.erupts[0].at, 0.8, 1);
  m.infernoWins++;
  u.cast = undefined;
  m.body.want = undefined;
  m.groggyUntil = now + I.groggy;
  u.api.anim(u, "rest");
  hold(u, "collapse");
  const b = bodyOf(u);
  const f = dirOf(m.body.yaw);
  landFx(u, floorAt(u, b.x + f.x * 8, b.z + f.z * 8), 12);
  play(u.dim, "random.anvil_land", b, 0.4, 1);
  play(u.dim, u.def.sounds.roar, headAt(u), 0.5, 1);
  title(u, "§6§l수르트가 쓰러졌습니다!", `§f${Math.round(I.groggy / 20)}초 동안 받는 피해 §e+${Math.round(I.bonus * 100)}% §7(빈틈)${m.parts.crown.broken ? "" : " · 불꽃 왕관의 핵"}`, fighters(u));
  say(u, "§6[보스] §f업화를 막았습니다! 수르트가 쓰러졌습니다");
  expose(u, "crown", { x: b.x + f.x * CROWN_AT, y: u.center.y, z: b.z + f.z * CROWN_AT }, I.groggy);
  u.api.cast(u, "groggy", I.groggy + 24 + GAP, [[I.groggy, (x) => rise(x)]]);
  m.bigUntil = Math.max(m.bigUntil, now + I.groggy + 100);
}
function rise(u) {
  cover(u);
  release(u, "rise");
  play(u.dim, u.def.sounds.roar, headAt(u), 0.8, 0.9);
  dust(u.dim, floorAt(u, u.mem.body.x, u.mem.body.z), 8, ASH, 1.0);
}
/** 업화를 막지 못함: 업화 폭발 (모두 마법 피해 + 불), 수르트 회복 */
function infernoFail(u) {
  const I = u.def.inferno;
  const m = u.mem;
  const G = m.inferno;
  if (!G) return;
  m.inferno = undefined;
  dropGauge(u, G);
  m.infernoFails++;
  release(u, "rest");
  const b = bodyOf(u);
  fx(u.dim, "rpg:fire_wave", { x: b.x, y: u.center.y, z: b.z }, { speed: 40, size: 2.4, life: 1.4, color: FIRE, alpha: 0.95 });
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 20, life: 1.0, color: FIRE, alpha: 0.95 });
  for (const p of fighters(u)) {
    blast(u.dim, p.location, 2.5, LAVA);
    if (bossHit(u, p, I.failDamage, { noSource: true, cause: EntityDamageCause.magic })) burn(p, I.failBurn);
    shake(p, 0.5, 1.0);
  }
  try {
    const hc = u.boss.getComponent("minecraft:health");
    hc.setCurrentValue(Math.min(hc.effectiveMax, hc.currentValue + hc.effectiveMax * I.heal));
  } catch {
    // 무시
  }
  play(u.dim, u.def.sounds.erupt, b, 0.5, 1);
  play(u.dim, u.def.sounds.roar, headAt(u), 0.7, 1);
  title(u, "§c§l무스펠의 업화 폭발!", `§f업화를 막지 못했습니다 §7— 수르트가 체력을 ${Math.round(I.heal * 100)}% 되찾았습니다`, fighters(u));
  m.bigUntil = Math.max(m.bigUntil, system.currentTick + 120);
}

// ------------------------------------------------------------ 라그나로크 (얼음 결정 뒤)
/** 월드의 플레이어 모두 (나갔다 다시 들어온 사람은 새 객체라 id 로 찾음) */
function allPlayers() {
  try {
    return world.getAllPlayers();
  } catch {
    return [];
  }
}
/** 명단 맞추기: 지금 싸우는 사람은 넣고, 쓰러졌거나 싸울 수 없게 된 사람은 뺌 */
function ragRoll(u, G) {
  for (const p of fighters(u)) {
    G.gone.delete(p.id);
    G.who.add(p.id);
  }
  for (const p of allPlayers()) if (G.who.has(p.id) && !G.gone.has(p.id) && !okTarget(p)) G.gone.add(p.id);
}
/** 라그나로크를 받는 사람: 지금 싸우는 사람 + 명단에 있는 사람 중 같은 차원·필드 가운데에서 reach 칸 안 (필드를 떠나도 피하지 못함) */
function ragVictims(u, G, reach = RAG_REACH) {
  const out = new Map();
  for (const p of fighters(u)) out.set(p.id, p);
  for (const p of allPlayers()) {
    if (out.has(p.id) || !G.who.has(p.id) || G.gone.has(p.id)) continue;
    let near = false;
    try {
      near = okTarget(p) && p.dimension.id === u.dim.id && hdist(p.location, u.center) <= reach;
    } catch {
      near = false;
    }
    if (near) out.set(p.id, p);
  }
  return [...out.values()];
}
/** 담장 밖(성문·둑)에서 싸우던 사람은 담장 안으로 끌려 들어옴 (제 방향 그대로, 가운데에서 R-5 칸) */
function pullIn(u, list) {
  const R = u.enc.def.arena.radius;
  const c = u.center;
  for (const p of list) {
    const l = p.location;
    const r = hdist(l, c);
    if (r <= R + 0.3) continue;
    const at = { x: c.x + ((l.x - c.x) / r) * (R - 5), y: c.y, z: c.z + ((l.z - c.z) / r) * (R - 5) };
    try {
      p.teleport(at, { facingLocation: { x: c.x, y: c.y + 1.6, z: c.z } });
    } catch {
      continue;
    }
    showFaceText(p, "§c§l불길에 끌려 들어왔습니다", { dist: 2.4, up: 0.45 });
  }
}
/**
 * p 를 지켜 주는 온전한 얼음 결정 (불길이 오는 곳 from 에서 결정 너머 그림자: 결정 반폭 ~ 반폭 + depth 칸 뒤, 옆으로 반폭 + pad), 없으면 undefined
 * @param {any} u
 * @param {{x: number, y: number, z: number}} l
 * @param {{x: number, z: number}} from
 */
export function shadowOf(u, l, from) {
  const G = u.def.ragnarok;
  for (const c of u.mem.crystals) {
    if (!c.intact) continue;
    const v = dirTo(from, c.at);
    const px = l.x - c.at.x;
    const pz = l.z - c.at.z;
    const along = px * v.x + pz * v.z;
    const side = Math.abs(px * v.z - pz * v.x);
    if (along >= c.half - 0.3 && along <= c.half + G.depth && side <= c.half + G.pad && Math.abs(l.y - c.at.y) < 3) return c;
  }
  return undefined;
}
function ragnarok(u, now, list, second) {
  const G = u.def.ragnarok;
  const m = u.mem;
  const c = u.center;
  const a = Math.random() * TAU;
  const spot = { x: c.x + Math.cos(a) * G.spot, y: c.y, z: c.z + Math.sin(a) * G.spot };
  m.ragnarok = { stage: "leap", second, who: new Set(list.map((p) => p.id)), gone: new Set(), judged: new Set(), saved: 0, killed: 0, wave: undefined, melted: [] };
  u.immune = true;
  u.api.anim(u, "rest");
  hold(u, "leap");
  jump(u, spot, G.leap, 10);
  sound(u, u.def.sounds.roar, headAt(u), 0.6, 1);
  title(u, "§4§l라그나로크", "§f세계를 태우는 불길이 옵니다 §7— §b얼음 결정 뒤로!", list);
  const raise = G.leap + 6;
  u.api.cast(u, "ragnarok", raise + G.windup + 90 + GAP, [
    [G.leap, (x) => ragLand(x)],
    [raise, (x) => ragRaise(x)],
    [raise + G.windup, (x) => ragRelease(x)],
  ]);
}
function ragLand(u) {
  const m = u.mem;
  if (!m.ragnarok) return;
  u.immune = false;
  landFx(u, floorAt(u, m.body.x, m.body.z), 10);
}
function ragRaise(u) {
  const G = u.def.ragnarok;
  const m = u.mem;
  const R = m.ragnarok;
  if (!R) return;
  const now = system.currentTick;
  R.stage = "raise";
  R.t0 = now;
  R.until = now + G.windup;
  m.body.want = undefined;
  hold(u, "ragnarok");
  ragRoll(u, R);
  pullIn(u, ragVictims(u, R, RAG_PULL));
  play(u.dim, u.def.sounds.thunder, u.center, 0.6, 1);
  sound(u, u.def.sounds.charge, headAt(u), 0.4, 1);
  // 두 번째부터: 사람들 곁의 온전한 결정을 melt 개까지 먼저 녹임 (meltPick: 온전한 결정 keepIntact 개 이상 · 모두에게 safe 칸 안의 그림자를 남김)
  if (R.second) {
    const people = ragVictims(u, R);
    const pick = meltPick(u, people, bodyOf(u));
    const from = leftHandAt(u);
    for (const c of pick) {
      const arc = lobArc(from, { x: c.at.x, y: c.at.y + 4, z: c.at.z }, 0.8);
      fx(u.dim, "rpg:glob", from, { dir: arc.dir, speed: arc.speed, size: 2.2, life: 0.8, spin: Math.random() * 360, color: FIRE, alpha: 1 });
      R.melted.push(c);
      c.intact = false; // 날아가는 동안에도 이미 쓸 수 없음 (그림자 표시에서 빠짐)
    }
    if (pick.length) {
      play(u.dim, u.def.sounds.fire, from, 0.6, 1);
      later(u, 16, (x) => {
        for (const c of pick) meltCrystal(x, c);
      });
    }
  }
  explain(u, "ragnarok", `§4[보스] 라그나로크: §f수르트가 칼을 하늘로 들면 ${Math.round(G.windup / 20)}초 뒤 세계를 태우는 불길이 퍼집니다 — §b수르트 반대쪽 얼음 결정 뒤 (파란 그림자)§f에 숨으세요 §c(밖은 즉사)§7. 숨겨 준 결정은 녹아 없어지고, 레바테인을 부수면 즉사 대신 큰 피해`);
  if (R.second && R.melted.length) say(u, `§4[보스] §f수르트가 얼음 결정 ${R.melted.length}개를 녹여 버립니다! §7(남은 결정 ${m.crystals.filter((c) => c.intact).length}개)`);
}
/** 결정 c 의 그림자 한가운데 (불길이 오는 곳 from 에서 결정 너머 반폭 + depth / 2) */
export function shadowMid(u, c, from) {
  const v = dirTo(from, c.at);
  const s = c.half + u.def.ragnarok.depth / 2;
  return { x: c.at.x + v.x * s, y: c.at.y, z: c.at.z + v.z * s };
}
/**
 * 두 번째 라그나로크에 먼저 녹일 결정: 사람들 (가운데) 에 가까운 온전한 결정부터 melt 개까지.
 * 온전한 결정을 keepIntact 개 이상 남기고, 녹인 뒤에도 모두에게 safe 칸 안에 온전한 결정의 그림자가 있을 때만 녹임 (닿을 수 없는 즉사는 없음)
 * @returns {any[]}
 */
export function meltPick(u, people, from) {
  const G = u.def.ragnarok;
  if (!people.length) return [];
  const cx = people.reduce((s, p) => s + p.location.x, 0) / people.length;
  const cz = people.reduce((s, p) => s + p.location.z, 0) / people.length;
  const left = new Set(u.mem.crystals.filter((c) => c.intact));
  const order = [...left].sort((x, y) => Math.hypot(x.at.x - cx, x.at.z - cz) - Math.hypot(y.at.x - cx, y.at.z - cz));
  const reachable = () => people.every((p) => [...left].some((c) => hdist(p.location, shadowMid(u, c, from)) <= G.safe));
  const out = [];
  for (const c of order) {
    if (out.length >= G.melt || left.size <= G.keepIntact) break;
    left.delete(c);
    if (reachable()) out.push(c);
    else left.add(c);
  }
  return out;
}
/** 들고 있는 동안: 명단 · 그림자 표시 (온전한 결정마다) · 알림 · 3·2·1 · 붉은 하늘 */
function ragTick(u, now) {
  const m = u.mem;
  const R = m.ragnarok;
  if (!R) return;
  ragRoll(u, R);
  if (R.stage === "raise") {
    const from = bodyOf(u);
    if (now % 6 === 0) for (const c of m.crystals) if (c.intact) shadowMarks(u, c, from, 8);
    // 들고 있는 동안 담장 밖으로 나간 사람은 다시 끌려 들어옴 (10틱마다)
    if (now % 10 === 0) pullIn(u, ragVictims(u, R, RAG_PULL));
    const list = ragVictims(u, R);
    const left = R.until - now;
    if (now % 10 === 0) {
      for (const p of list) {
        const s = Math.ceil(left / 20);
        notify(p, shadowOf(u, p.location, from) ? `§b얼음 결정 그림자 안 §a— 안전 §7(${s}초)` : `§c불길이 옵니다! §f수르트 반대쪽 얼음 결정 뒤로 §7(${s}초)`, 15);
      }
    }
    if (left > 0 && left % 20 === 0 && left <= 60) faceAll(u, `§4§l라그나로크 ${left / 20}`, { dist: 2.4, up: 0.45 }, list);
    if (now % 20 === 0) for (const p of list) fx(u.dim, "rpg:ember", { x: p.location.x, y: p.location.y + 4, z: p.location.z }, { size: 6, life: 1.0, speed: 2, color: EMBER, alpha: 0.8 });
    return;
  }
  if (R.stage === "wave") waveTick(u, now);
}
/** 결정의 그림자 (수르트 반대쪽 depth 칸, 폭 = 반폭 + pad) 를 바닥에 파란 판으로 */
function shadowMarks(u, c, from, ticks) {
  const G = u.def.ragnarok;
  const v = dirTo(from, c.at);
  const r = rightOf(angOf(v));
  const w = c.half + G.pad;
  const life = Math.max(0.1, ticks / 20);
  for (let s = c.half + 0.6; s <= c.half + G.depth; s += 1.4) {
    for (const k of [-1, 0, 1]) {
      const at = { x: c.at.x + v.x * s + r.x * k * (w - 0.5), y: u.center.y + 0.08, z: c.at.z + v.z * s + r.z * k * (w - 0.5) };
      fx(u.dim, k === 0 ? "rpg:tele_fill" : "rpg:tele_disc", at, { dir: { x: 0, y: 0, z: 0 }, size: k === 0 ? 1.6 : 0.7, life, color: SAFE, alpha: k === 0 ? 0.55 : 0.9, grow: 1 });
    }
  }
}
function ragRelease(u) {
  const G = u.def.ragnarok;
  const m = u.mem;
  const R = m.ragnarok;
  if (!R) return;
  const now = system.currentTick;
  const b = bodyOf(u);
  R.stage = "wave";
  // 불길은 라그나로크를 받는 거리 (가운데에서 RAG_REACH) 끝까지 퍼짐. ahead = 앞머리가 아직 닿지 않은 사람 (지금 있는 사람 모두 + 앞머리 앞에서 본 사람)
  R.wave = { t0: now, from: { x: b.x, y: u.center.y, z: b.z }, max: RAG_REACH + hdist(b, u.center) + 1, ahead: new Set(ragVictims(u, R).map((p) => p.id)) };
  hold(u, "release");
  const life = R.wave.max / G.speed;
  fx(u.dim, "rpg:fire_wave", { x: b.x, y: u.center.y, z: b.z }, { speed: G.speed, size: 3, life, color: FIRE, alpha: 1 });
  fx(u.dim, "rpg:tele_wave", { x: b.x, y: u.center.y + 0.2, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, speed: G.speed, size: 1, life, color: LAVA, alpha: 0.95 });
  blast(u.dim, floorAt(u, b.x, b.z), 10, FIRE);
  play(u.dim, u.def.sounds.erupt, b, 0.4, 1);
  play(u.dim, u.def.sounds.roar, headAt(u), 0.5, 1);
  for (const p of fighters(u)) shake(p, 0.6, 1.4);
}
/**
 * 불길: 앞머리가 지나간 사람을 판정 (온전한 결정의 그림자 안이면 삶, 그 결정은 녹음) → 다 지나가면 끝.
 * 판정은 앞머리가 닿기 전에 있던 사람만 (불길이 지나간 뒤에 들어온 사람은 아님 · 앞머리를 건너뛰어 뒤로 간 사람은 판정). 끝날 때 남은 사람도 판정
 */
function waveTick(u, now) {
  const G = u.def.ragnarok;
  const m = u.mem;
  const R = m.ragnarok;
  const W = R.wave;
  const front = (G.speed * (now - W.t0)) / 20;
  const done = front > W.max;
  for (const p of ragVictims(u, R)) {
    if (R.judged.has(p.id)) continue;
    if (!done && hdist(p.location, W.from) > front + 0.5) {
      W.ahead.add(p.id);
      continue;
    }
    if (!W.ahead.has(p.id)) continue;
    R.judged.add(p.id);
    const c = shadowOf(u, p.location, W.from);
    if (c) {
      c.used = true;
      R.saved++;
      showFaceText(p, "§b§l얼음 결정이 막아 주었습니다", { dist: 2.4, up: 0.45 });
      fx(u.dim, "rpg:splash", { x: c.at.x, y: c.at.y + 3, z: c.at.z }, { size: 3, life: 0.6, color: ICE, alpha: 0.95 });
      continue;
    }
    R.killed++;
    flame(u.dim, p.location, 3.5, 0.8, LAVA);
    if (m.parts.sword.broken) {
      if (bossHit(u, p, G.brokenDamage, { noSource: true, cause: EntityDamageCause.magic })) burn(p, 4);
      showFaceText(p, "§c§l불길에 휩쓸렸습니다 §7(레바테인이 부서져 버팀)", { dist: 2.4, up: 0.45 });
      continue;
    }
    say(u, `§4[보스] §e${p.name}§f님이 세계를 태우는 불길에 휩쓸렸습니다`);
    kill(p, u.boss);
  }
  if (!done) return;
  // 끝: 숨겨 준 결정은 녹음, 붉은 하늘이 걷힘
  for (const c of m.crystals) {
    if (!c.used) continue;
    c.used = false;
    if (c.intact) meltCrystal(u, c);
  }
  m.ragnarok = undefined;
  m.ragDone++;
  release(u, "rest");
  m.bigUntil = now + 100;
  for (const k of ["sweep", "thrust", "meteors", "rings"]) m.next[k] = Math.max(m.next[k], now + 60);
  const left = m.crystals.filter((c) => c.intact).length;
  say(u, `§4[보스] §f라그나로크가 지나갔습니다 §7(남은 얼음 결정 ${left}개)`);
}

// ------------------------------------------------------------ 신전 구조물 (얼음 결정·사슬 기둥: 싸움 중 녹고 부서짐 → 끝나면 되돌림)
const AIR = () => BlockPermutation.resolve("minecraft:air");
function permOf(block, states) {
  try {
    return BlockPermutation.resolve(block, states ?? {});
  } catch {
    try {
      return BlockPermutation.resolve(block);
    } catch {
      return undefined;
    }
  }
}
function setCell(dim, q, perm) {
  if (!perm) return false;
  try {
    dim.setBlockPermutation({ x: q.x, y: q.y, z: q.z }, perm);
    return true;
  } catch {
    return false;
  }
}
/** 얼음 결정이 녹음: 블록 → 공기, 둘레 서리 → 그을린 현무암 (김·물보라) */
export function meltCrystal(u, c) {
  c.intact = false;
  c.melted = true;
  const air = AIR();
  const basalt = permOf("minecraft:smooth_basalt");
  for (const q of [...c.cells].sort((a, b) => b.y - a.y)) setCell(u.dim, q, air);
  for (const q of c.frost) setCell(u.dim, q, basalt);
  fx(u.dim, "rpg:splash", { x: c.at.x, y: c.at.y + 2.5, z: c.at.z }, { size: 3.5, life: 0.7, color: ICE, alpha: 0.95 });
  for (let i = 0; i < 3; i++) dust(u.dim, { x: c.at.x, y: c.at.y + i * 2.5, z: c.at.z }, 3.5, STEAM, 1.2);
  play(u.dim, u.def.sounds.melt, c.at, 0.7, 1);
  play(u.dim, u.def.sounds.ice, c.at, 0.6, 1);
}
/** 사슬 기둥이 무너짐 (칼을 놓아준 뒤): 블록 → 공기 (매단 랜턴 먼저: 받침이 먼저 없어지면 랜턴이 아이템으로 떨어짐), 밑동은 금 간 흑암 벽돌 */
export function crumble(u, c) {
  c.intact = false;
  c.broken = true;
  const air = AIR();
  const rubble = permOf("minecraft:cracked_polished_blackstone_bricks");
  const hung = (q) => (q.block === "minecraft:lantern" || q.block === "minecraft:soul_lantern" ? 0 : 1);
  for (const q of [...c.cells].sort((a, b) => hung(a) - hung(b) || b.y - a.y)) setCell(u.dim, q, q.y === c.at.y && Math.abs(q.x + 0.5 - c.at.x) <= 1 && Math.abs(q.z + 0.5 - c.at.z) <= 1 ? rubble : air);
  for (let i = 0; i < 4; i++) dust(u.dim, { x: c.at.x, y: c.at.y + i * 4, z: c.at.z }, 4, [0.4, 0.38, 0.4], 1.2);
  play(u.dim, "dig.chain", c.at, 0.7, 1);
}
/** 구조물이 처음 모습인지 (몇 칸만 봄: 가장 높은 칸 · 가운데 밑동) */
function looksIntact(dim, c) {
  const top = c.cells.reduce((a, q) => (q.y > a.y ? q : a), c.cells[0]);
  const base = c.cells.find((q) => q.y === c.at.y && Math.abs(q.x + 0.5 - c.at.x) < 0.6 && Math.abs(q.z + 0.5 - c.at.z) < 0.6) ?? c.cells[0];
  for (const q of [top, base]) {
    try {
      const b = dim.getBlock({ x: q.x, y: q.y, z: q.z });
      if (!b) return true; // 불러오지 않은 곳: 모름 (건드리지 않음)
      if (b.typeId !== q.block) return false;
    } catch {
      return true;
    }
  }
  return true;
}
/** 구조물 안에 사람이 있는지 (되돌리면 갇힘) */
function occupied(c, list) {
  return list.some((p) => Math.abs(p.location.x - c.at.x) <= c.half + 1.6 && Math.abs(p.location.z - c.at.z) <= c.half + 1.6 && p.location.y >= c.at.y - 1 && p.location.y <= c.at.y + c.h + 1);
}
/** 구조물 되돌리기 (블록 + 결정 둘레 서리 바닥). force 면 안에 있는 사람을 바깥으로 밀어 내고 */
function restore(dim, c, list, force) {
  if (occupied(c, list)) {
    if (!force) return false;
    for (const p of list) {
      if (!occupied(c, [p])) continue;
      const v = dirTo(c.at, p.location);
      try {
        p.teleport({ x: c.at.x + v.x * (c.half + 3), y: c.at.y, z: c.at.z + v.z * (c.half + 3) });
      } catch {
        // 무시
      }
    }
  }
  for (const q of c.cells) setCell(dim, q, permOf(q.block, q.states));
  for (const q of c.frost ?? []) setCell(dim, q, permOf(q.block, q.states));
  c.intact = true;
  c.melted = false;
  c.broken = false;
  return true;
}
function allNear(u) {
  try {
    return u.dim.getPlayers({ location: u.center, maxDistance: u.enc.def.arena.radius + 12 });
  } catch {
    return audience(u);
  }
}

// ------------------------------------------------------------ 세계를 태우는 불꽃 (체력 50% 아래)
function enrage(u, now) {
  const E = u.def.enrage;
  const m = u.mem;
  m.enraged = true;
  u.cdMult = E.cd;
  u.dmgMult = E.dmg;
  m.body.want = undefined;
  u.api.anim(u, "roar");
  sound(u, u.def.sounds.roar, headAt(u), 0.5, 1);
  play(u.dim, u.def.sounds.thunder, u.center, 0.7, 0.8);
  const b = bodyOf(u);
  fx(u.dim, "rpg:shock_ring", { x: b.x, y: u.center.y + 0.1, z: b.z }, { dir: { x: 0, y: 0, z: 0 }, size: 18, life: 1.0, color: FIRE, alpha: 0.95 });
  fx(u.dim, "rpg:fire_wave", { x: b.x, y: u.center.y, z: b.z }, { speed: 24, size: 1.6, life: 0.9, color: LAVA, alpha: 0.9 });
  title(u, "§c§l세계를 태우는 불꽃", "§f수르트의 불길이 거세집니다 — 패턴이 빨라지고 아파집니다", fighters(u));
  explain(u, "enrage", `§c[보스] 세계를 태우는 불꽃: §f체력 ${Math.round(E.from * 100)}% 아래에서 수르트의 재사용 대기가 ×${E.cd}, 주는 피해가 ×${E.dmg}`);
  u.api.cast(u, "enrage", 50, []);
  m.next.meteors = Math.max(m.next.meteors, now + 80);
}

// ------------------------------------------------------------ 높은 곳 · 둘레 연출
/**
 * 결정·기둥·담장·탑 위처럼 높은 곳 (선 자리의 바닥이 필드 바닥보다 high.above 칸 넘게) 에 grace 틱 넘게 머문 사람: 불기둥 (사람마다 cd 틱에 한 번).
 * 수르트가 공격하지 못하는 동안은 새로 세거나 치지 않음
 */
function highTick(u, now) {
  if (now % 10 !== 0 || !u.alive || !u.boss?.isValid) return;
  const d = u.def.high;
  const m = u.mem;
  const paused = !canAttack(u, now);
  const c = u.center;
  const seen = new Set();
  for (const p of fighters(u)) {
    const up = groundUnder(p, p.location.y) - c.y >= d.above;
    if (!up && !(p.isOnGround === false && m.highSince.has(p.id))) continue;
    seen.add(p.id);
    if (paused) continue;
    if (!m.highSince.has(p.id)) {
      m.highSince.set(p.id, now);
      showFaceText(p, "§c§l내려오세요! §f머물면 불기둥이 솟습니다", { dist: 2.2, up: 0.35 });
    }
    if (!up || now - m.highSince.get(p.id) < d.grace) continue;
    if (now - (m.high.get(p.id) ?? -1000) < d.cd) continue;
    m.high.set(p.id, now);
    const dir = dirTo(p.location, c);
    for (let i = 0; i < 3; i++) flame(u.dim, { x: p.location.x, y: p.location.y - 1 + i * 2, z: p.location.z }, 3, 0.6, i ? FIRE : LAVA);
    fx(u.dim, "rpg:bash_burst", { x: p.location.x, y: p.location.y + 1, z: p.location.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.4, life: 0.4, color: FIRE, alpha: 0.95 });
    play(u.dim, u.def.sounds.erupt, p.location, 1.2, 0.8);
    if (bossHit(u, p, d.damage, { knock: { x: dir.x * 1.1, z: dir.z * 1.1, h: 0.35 }, noSource: true })) burn(p, 2);
    showFaceText(p, "§c§l불기둥에 떨어집니다", { dist: 2.2, up: 0.35 });
    explain(u, "high", `§6[보스] §f결정·기둥·담장 위처럼 높은 곳에 ${(d.grace / 20).toFixed(1)}초 넘게 머물면 발밑에서 불기둥이 솟습니다`);
  }
  for (const id of [...m.highSince.keys()]) if (!seen.has(id)) m.highSince.delete(id);
}
/** 둘레 연출: 불씨 (1.5초마다), 핵 이름표·시간 */
function ambientTick(u, now) {
  const m = u.mem;
  if (m.core) {
    if (!m.core.e?.isValid || now >= m.core.until + 40) cover(u);
    else if (now % 10 === 0) fx(u.dim, "rpg:bash_ring", { x: m.core.e.location.x, y: u.center.y + 1.6, z: m.core.e.location.z }, { dir: { x: 0, y: 1, z: 0 }, size: 1.6, life: 0.5, color: GOLD, alpha: 0.85 });
  }
  if (now % 30 !== 0 || m.leap || m.body.h < -0.5) return;
  const h = headAt(u);
  if (!m.parts.crown.broken) fx(u.dim, "rpg:ember", { x: h.x, y: h.y + 4, z: h.z }, { size: 5, life: 1.2, speed: 2.5, color: EMBER, alpha: 0.85 });
  if (m.enraged) fx(u.dim, "rpg:ember", { x: h.x, y: u.center.y + 8, z: h.z }, { size: 10, life: 1.2, speed: 3, color: LAVA, alpha: 0.8 });
}

// ------------------------------------------------------------ 안개
function fogWant(u) {
  const m = u.mem;
  if (m.ragnarok && m.ragnarok.stage !== "leap") return FOGS.ragnarok;
  if (m.inferno) return FOGS.inferno;
  return undefined;
}

// ------------------------------------------------------------ 등장 (화구 밑에서 솟아오름)
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
        x.mem.body.h = -A.depth * (1 - t) * (1 - t);
        if (k % 5 === 0) {
          const a = Math.random() * TAU;
          const r = 1 + Math.random() * (muspelSize(x.key).pit - 1);
          const at = floorAt(x, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r);
          flame(x.dim, at, 3 + t * 3, 0.7, k % 10 ? FIRE : LAVA);
          if (k % 10 === 0) play(x.dim, x.def.sounds.lava, c, 0.5 + t * 0.4, 1);
        }
        if (k % 20 === 0) shakeNear(x, c, 48, 0.12 + t * 0.25, 0.6);
      },
    ]);
  }
  steps.push([
    A.rise,
    (x) => {
      x.mem.body.h = 0;
      landFx(x, floorAt(x, c.x, c.z), 12);
      hold(x, "roar_hold");
      sound(x, x.def.sounds.roar, headAt(x), 0.5, 1);
      title(x, `§c§l${x.def.name}`, `§6${x.def.title}`, audience(x));
    },
  ]);
  steps.push([
    A.rise + A.roar,
    (x) => {
      x.immune = false;
      release(x, "rest");
      say(x, `§c[초월 보스] §f${x.def.name}: §7${x.def.notes?.length ? "패턴을 깨는 법 — 반격 · 무력화 · 신전 구조물 · 부위 파괴 (도전 창에 정리)" : ""}`);
    },
  ]);
  enc.api.cast(enc, "arrive", A.rise + A.roar + 2, steps);
}

// ------------------------------------------------------------ 모듈
/** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 들고 있던 모습을 풂 */
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
  key: KEY,
  propIds: [CORE_ID, GAUGE_ID],
  /**
   * 맞을 때: 반격 창 (칼이 금빛) 에 스킬·룬 피해 (h.skill) → 반격, 업화 동안 업화 막대를 깎음, 받는 피해 보너스 (빈틈·속박·반격 중 가장 큰 것) + 표시
   */
  hurt(u, p, amount, h) {
    const m = u.mem;
    if (!m?.body) return amount;
    const now = system.currentTick;
    if (m.counter && h?.skill && now <= m.counter.until && amount > 0) counterHit(u, p, now);
    const o = openBonus(u, now);
    const out = o ? amount * (1 + o.bonus) : amount;
    if (m.inferno) gaugeHit(u, out);
    return o ? { amount: out, mark: o.mark } : out;
  },
  /** 맞는 소품 (부위의 핵): 체력을 기억하고 이름표 막대 */
  propHit(enc, target, p, amount, left) {
    const m = enc.mem;
    if (!m?.core || !sameEntity(m.core.e, target) || left === undefined) return;
    m.parts[m.core.kind].hp = Math.max(0, left);
    coreTag(enc);
  },
  /** 핵의 체력이 0: 부위 파괴 */
  propDied(enc, target, p) {
    const m = enc.mem;
    if (!m?.core || !sameEntity(m.core.e, target)) return;
    partBreak(enc, m.core.kind, p);
  },
  /** 보상에 더하는 것: 부순 부위마다 (설정 partLoot) */
  bonusLoot(enc) {
    const out = {};
    for (const k of PART_ORDER) {
      if (!enc.mem?.parts?.[k]?.broken) continue;
      for (const [item, n] of Object.entries(enc.def.partLoot?.[k] ?? {})) out[item] = (out[item] ?? 0) + Number(n);
    }
    return out;
  },
  /** 처치 뒤 남기는 줄: 부위 파괴 · 반격 · 업화 · 속박 */
  killNote(enc) {
    const m = enc.mem;
    if (!m?.parts) return [];
    const broken = PART_ORDER.filter((k) => m.parts[k].broken);
    return [`§6[부위 파괴] §f${broken.length}/3${broken.length ? ` §7— ${broken.map((k) => `${PART_NAMES[k]}${m.parts[k].by ? ` (${m.parts[k].by})` : ""}`).join(" · ")}` : ""}`, `§7반격 ${m.counters}번 · 업화 막음 ${m.infernoWins}/${m.infernoWins + m.infernoFails} · 사슬 속박 ${m.binds}번`];
  },
  /** 원거리 견제: 큰 패턴 동안은 쉼 */
  farOk(u, now) {
    return canAttack(u, now ?? system.currentTick);
  },
  /** 던지는 곳: 왼손 (칼은 오른손) */
  farFrom(u) {
    return leftHandAt(u);
  },
  fogCleared(enc, p) {
    enc.mem?.fog?.delete(p.id);
  },
  start(enc) {
    const now = system.currentTick;
    const d = enc.def;
    const c = enc.center;
    const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
    const yaw = angOf(dirTo(c, p));
    const feat = enc.feat ?? {};
    const after = d.arrive.rise + d.arrive.roar;
    enc.mem = {
      next: { slam: now + after + 20, stomp: now + after + d.stomp.first, fist: now + after + d.fist.first, sweep: now + after + d.sweep.first, meteors: now + after + d.meteors.first, thrust: now + after + d.thrust.first, rings: now + after + d.rings.first },
      bigUntil: now + after + 160,
      told: new Set(),
      body: { x: c.x, z: c.z, h: -d.arrive.depth, yaw, want: undefined, gait: 0, gaitTo: 0, gaitSent: undefined, placed: undefined },
      leap: undefined,
      parts: { sword: { hp: d.parts.sword, broken: false, by: "" }, fist: { hp: d.parts.fist, broken: false, by: "" }, crown: { hp: d.parts.crown, broken: false, by: "" } },
      brokenOrder: [],
      flagSent: {},
      heatSent: undefined,
      core: undefined,
      counter: undefined,
      groggyUntil: 0,
      bindUntil: 0,
      counterUntil: 0,
      trails: [],
      rings: [],
      inferno: undefined,
      infDone: new Set(),
      ragnarok: undefined,
      ragSeen: new Set(),
      ragDone: 0,
      enraged: false,
      counters: 0,
      binds: 0,
      infernoWins: 0,
      infernoFails: 0,
      chains: (feat.chains ?? []).map((q) => ({ at: { x: q.x, y: q.y, z: q.z }, half: q.half, h: q.h, cells: q.cells, frost: [], intact: true, broken: false })),
      crystals: (feat.crystals ?? []).map((q) => ({ at: { x: q.x, y: q.y, z: q.z }, half: q.half, h: q.h, cells: q.cells, frost: q.frost ?? [], intact: true, melted: false, used: false })),
      high: new Map(),
      highSince: new Map(),
      fog: new Map(),
      fogWant: undefined,
    };
    for (const u of enc.units) u.mem = enc.mem;
    // 지난 싸움에서 되돌리지 못한 구조물 (안에 사람이 있었음·월드를 나감) 은 지금 되돌림 (안의 사람은 밀어 냄)
    const near = allNear(enc);
    for (const s of [...enc.mem.chains, ...enc.mem.crystals]) if (!looksIntact(enc.dim, s)) restore(enc.dim, s, near, true);
    // 엔진 평타는 쓰지 않음 (몸을 돌리는 내려찍기는 모듈이)
    enc.meleeAt = Infinity;
    enc.immune = true;
    place(enc);
    bodyTick(enc, now);
    hold(enc, "emerge");
    sound(enc, d.sounds.summon, c, 0.6, 1);
    arrive(enc);
  },
  pick(u, now, list) {
    const m = u.mem;
    const d = u.def;
    // 큰 패턴이 아직 이어지는 중이면 (불길이 퍼지는 중 등) 쉼
    if (!m?.body || m.leap || m.ragnarok || m.inferno) return false;
    const r = u.api.ratio(u);
    // 1) 큰 패턴: 라그나로크 · 무스펠의 업화 (체력 차례대로, 한 번씩)
    if (now >= m.bigUntil) {
      const rag = d.ragnarok.at.find((x) => r <= x && !m.ragSeen.has(x));
      const inf = d.inferno.at.find((x) => r <= x && !m.infDone.has(x));
      // 둘 다 왔으면 체력 문턱이 높은 것 (먼저 온 것) 부터
      if (rag !== undefined && (inf === undefined || rag >= inf)) {
        m.ragSeen.add(rag);
        ragnarok(u, now, list, d.ragnarok.at.indexOf(rag) > 0);
        return true;
      }
      if (inf !== undefined) {
        m.infDone.add(inf);
        inferno(u, now, list);
        return true;
      }
    }
    // 2) 세계를 태우는 불꽃
    if (!m.enraged && r <= d.enrage.from) {
      enrage(u, now);
      return true;
    }
    // 3) 불비 · 불의 고리 (넓은 공격, 서로 바로 이어지지 않게)
    if (now >= m.next.meteors) {
      if (meteors(u, now, list)) {
        m.next.meteors = now + every(u, d.meteors.every);
        m.next.rings = Math.max(m.next.rings, now + d.meteors.windup + 80);
        return true;
      }
      m.next.meteors = now + 20;
    }
    if (now >= m.next.rings) {
      if (rings(u, now, list)) {
        m.next.rings = now + every(u, d.rings.every);
        m.next.meteors = Math.max(m.next.meteors, now + 120);
        return true;
      }
      m.next.rings = now + 20;
    }
    // 4) 반격 패턴: 횡베기 · 찌르기 (서로 바로 이어지지 않게)
    if (now >= m.next.sweep) {
      if (sweep(u, now, list)) {
        m.next.sweep = now + every(u, d.sweep.every);
        m.next.thrust = Math.max(m.next.thrust, now + d.sweep.windup + 60);
        return true;
      }
      m.next.sweep = now + 20;
    }
    if (now >= m.next.thrust) {
      if (thrust(u, now, list)) {
        m.next.thrust = now + every(u, d.thrust.every);
        m.next.sweep = Math.max(m.next.sweep, now + d.thrust.windup + 60);
        return true;
      }
      m.next.thrust = now + 20;
    }
    // 5) 불의 주먹 (왼 건틀릿이 온전할 때)
    if (now >= m.next.fist) {
      if (fist(u, now, list)) {
        m.next.fist = now + every(u, d.fist.every);
        return true;
      }
      m.next.fist = now + 20;
    }
    // 6) 발 구르기 (옆·뒤에 붙은 사람)
    if (now >= m.next.stomp) {
      if (stomp(u, now, list)) {
        m.next.stomp = now + every(u, d.stomp.every);
        m.next.slam = Math.max(m.next.slam, now + d.stomp.windup + 16);
        return true;
      }
      m.next.stomp = now + 20;
    }
    // 7) 평타: 레바테인 내려찍기
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
    trailTick(u, now);
    ringTick(u, now);
    infernoTick(u, now);
    ragTick(u, now);
    highTick(u, now);
    ambientTick(u, now);
    syncBossFog(u, fogWant(u), now);
    if (!enc.alive) return;
    // 반격 창이 지나면 닫음 (틀이 끊긴 경우 등)
    if (m.counter && now > m.counter.until + 2) m.counter = undefined;
    // 몸: 뛰는 중에는 leapTick 이, 패턴·비틀거림 중에는 제자리 (원하는 쪽으로 돎), 아니면 걸음
    if (!m.leap) {
      if (u.cast || now < u.staggerUntil) standTick(u);
      else {
        m.body.want = undefined;
        walkTick(u, list ?? fighters(u), now);
      }
    }
    bodyTick(u, now);
  },
  /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 땅에 내려놓음, 드러난 핵·업화 막대를 거둠, 큰 불 */
  dying(enc) {
    ground(enc);
    const m = enc.mem;
    if (!m?.body) return;
    cover(enc);
    if (m.inferno) dropGauge(enc, m.inferno);
    m.inferno = undefined;
    const b = bodyOf(enc);
    for (let i = 0; i < 4; i++) blast(enc.dim, { x: b.x, y: enc.center.y + i * 6, z: b.z }, 6, i % 2 ? LAVA : FIRE);
    fx(enc.dim, "rpg:fire_wave", { x: b.x, y: enc.center.y, z: b.z }, { speed: 20, size: 2.2, life: 1.2, color: FIRE, alpha: 0.9 });
    play(enc.dim, enc.def.sounds.erupt, b, 0.5, 1);
  },
  end(enc) {
    const m = enc.mem;
    if (!m?.body) return;
    ground(enc);
    cover(enc);
    if (m.inferno) dropGauge(enc, m.inferno);
    clearBossFog(enc);
    // 녹은 결정·무너진 기둥은 처음 모습으로 (안에 사람이 있으면 다음 싸움을 시작할 때)
    const near = allNear(enc);
    for (const s of [...m.chains, ...m.crystals]) if (!s.intact || s.melted || s.broken) restore(enc.dim, s, near, false);
    m.inferno = undefined;
    m.ragnarok = undefined;
    m.trails = [];
    m.rings = [];
    m.counter = undefined;
    m.groggyUntil = 0;
    m.bindUntil = 0;
    enc.immune = false;
  },
};

/** 시험용: 지금 상태 */
export function stateOf(enc) {
  return enc.mem;
}
export { CHAIN_HEIGHT };
