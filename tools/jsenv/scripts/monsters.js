import { EntityDamageCause, GameMode, system, world } from "@minecraft/server";
import { MONSTER, MONSTER_ORDER, MONSTERS, monsterId } from "./config.js";
import { hasNoHealth, lineOfSight, setMonsterHooks } from "./combat.js";
import { play, setDamageTextAnchor } from "./fx.js";
import { isDying, setNameRefresh } from "./hp.js";
import { killCredit, lootOnDeath, noteHit } from "./loot.js";
import { add, addTauntHook, arc, beam, burst, flat, shake, shock, sparks, sub, unit, up } from "./skills.js";

/**
 * 일반 몬스터 (v1.7.0): 허수아비 → 굴론 → 스켈야스크림슬리 → 닉스 → 드라우그 → 트롤 (수치는 config.js MONSTERS)
 * - 후공: 맞기 전에는 둔 자리 둘레를 돌아다니기만 함. 맞으면 때린 플레이어(마지막으로 때린 사람)를 쫓아가 공격
 * - 이동·추격·마주 보기는 엔티티(hurt_by_target + melee_attack)가 하고, 바닐라 근접 피해는 막음 (combat.js 훅).
 *   피해·사거리·동작 시점은 여기서: reach 안이고 서로 보이면(벽 너머 X) 공격 동작(playAnimation) →
 *   windup 틱 뒤, 처음 노린 플레이어가 아직 reach + tolerance 안 · 노린 쪽(±MONSTER.arc 도) · 보이면 맞음, 아니면 빗나감
 * - 터치는 6칸 밖에서도 때릴 수 있어서, reach 밖 lungeRange 안이면 달려들며 공격 (lungeEvery 틱마다)
 * - 높이 차이로 닿지도 달려들지도 못하는데 플레이어는 터치로 때릴 수 있는 곳(기둥 위 등)이면 원거리 반격:
 *   플레이어 발밑에 붉은 고리 → windup + counterExtra 틱 뒤 그 자리(반지름 counterRadius)를 침. 고리 밖으로 나가면 피함
 * - 룬·무기 효과: 구속 VII 이상(속박·기절·빙결)이면 달려들기·원거리 반격 없음, 나약함까지 있으면(기절·빙결) 공격 못 함 (하던 동작도 끊김),
 *   나약함 한 단계마다 피해 -20%. 투명한 플레이어에게는 달려들기·원거리 반격 없음 (붙어 있으면 맞음)
 * - 대상이 크리에이티브·관전·쓰러지는 중(불사의 토템)이면 잠깐 멈춤, pauseLimit 틱이 넘으면 싸움 끝. 대상이 죽거나 나가거나 다른 차원·leash 밖이면 끝
 * - 이름표: "Lv.8 굴론", 다치면 아래에 체력 막대. 피해 숫자는 몸통 높이에서 떠올라 이름표 아래에서 끝남
 * - 싸움이 끝나고 MONSTER.regenDelay 틱 동안 맞지 않으면 1초마다 최대 체력 10% 회복 (다친 채 다시 불러와도). 트롤은 싸우는 중에도 재생
 * - v2.7.0: 죽으면 잡은 플레이어에게 경험치 (구슬 대신, 최고 레벨이 xpUntil 이상이면 0) + 전리품 (loot.js)
 */

const PLAYER = "minecraft:player";
const PLAYER_HALF = 0.3;
/** 몬스터 ID → 설정 (키 포함) */
const DEFS = new Map(MONSTER_ORDER.map((key) => [monsterId(key), { key, ...MONSTERS[key] }]));
/**
 * 공격 동작 하나: 시작 틱, 끝(판정) 틱, 종류, 처음 노린 플레이어, 노린 쪽 (가로 단위 벡터), 원거리 반격이 떨어질 자리
 * @typedef {{start: number, at: number, kind: "melee" | "lunge" | "counter", target: any, aim?: {x: number, y: number, z: number}, spot?: {x: number, y: number, z: number}}} Windup
 */
/**
 * 싸우는 몬스터: id → {m, def, player: 쫓는 플레이어, attackers: 때린 플레이어 id → {p, t: 마지막으로 때린 틱}, nextAttack, nextLunge, paused?: 멈추기 시작한 틱, windup?,
 * taunt?: v2.20.0 도발 (그 플레이어 id · 끝나는 틱 — 그동안 다른 사람이 때려도 대상이 바뀌지 않음)}
 * @type {Map<string, {m: any, def: any, player: any, attackers: Map<string, {p: any, t: number}>, nextAttack: number, nextLunge: number, paused?: number, windup?: Windup, taunt?: {id: string, until: number}}>}
 */
const fights = new Map();
/** 다친 몬스터 (회복·재생용): id → {m, def, t: 마지막으로 맞은 틱} */
const hurt = new Map();
/** 스크립트가 넣는 몬스터 공격 (바닐라 근접은 막고 이것만 통과): "맞는 쪽|몬스터" → 틱 */
const ownHits = new Map();

/** 몬스터 설정 (몬스터가 아니면 undefined) */
export function monsterDef(e) {
  try {
    return DEFS.get(e?.typeId);
  } catch {
    return undefined;
  }
}

/** 크기 배율을 곱한 충돌 상자: 가로 반폭, 높이 */
export function monsterSize(def) {
  return { half: (def.box[0] * def.scale) / 2, height: def.box[1] * def.scale };
}

/** 몬스터 몸 가장자리 ~ 플레이어 몸 가장자리 (가로 칸), 높이 차 (플레이어 발 - 몬스터 발), 가운데끼리 거리 */
export function gapTo(m, def, p) {
  const a = m.location;
  const b = p.location;
  const d = Math.hypot(b.x - a.x, b.z - a.z);
  return { gap: Math.max(0, d - monsterSize(def).half - PLAYER_HALF), dy: b.y - a.y, dist: d };
}

/** 높이가 닿는지: 플레이어 발이 몬스터 발 아래 1.5칸 ~ 몬스터 키 (+extra) */
function heightOk(def, dy, extra = 0) {
  return dy >= -1.5 - extra && dy <= monsterSize(def).height + extra;
}

/** 피해 숫자 기준 높이 (발에서): 몸통(키 × 0.62)이되, 떠오른 숫자가 이름표에 닿지 않게 키 - 1 을 넘지 않음 (최소 0.45) */
export function damageTextHeight(def) {
  const h = monsterSize(def).height;
  return Math.min(h * 0.62, Math.max(0.45, h - 1.0));
}

/** 이름표: "Lv.8 굴론", 다쳤으면 아래 줄에 체력 막대 */
export function monsterLabel(def, hp, max) {
  const head = `§eLv.${def.level} §f${def.name}`;
  if (!(max > 0) || hp >= max - 1e-6) return head;
  const n = MONSTER.hpBar;
  const r = Math.max(0, hp) / max;
  const filled = Math.min(n, Math.max(hp > 0 ? 1 : 0, Math.round(r * n)));
  const col = r > 0.5 ? "§a" : r > 0.25 ? "§e" : "§c";
  return `${head}\n${col}${"|".repeat(filled)}§8${"|".repeat(n - filled)}`;
}

function refreshTag(m, def) {
  try {
    const h = m.getComponent("minecraft:health");
    if (!h) return;
    const want = monsterLabel(def, h.currentValue, h.effectiveMax);
    if (m.nameTag !== want) m.nameTag = want;
  } catch {
    // 무시
  }
}

/**
 * 싸우는 대상의 상태: ok = 공격 가능, pause = 잠깐 멈춤 (크리에이티브·관전·쓰러지는 중), gone = 싸움 끝 (없어짐·다른 차원)
 * @returns {"ok" | "pause" | "gone"}
 */
function targetState(p, m) {
  try {
    if (!p?.isValid || !m?.isValid || p.typeId !== PLAYER) return "gone";
    if (p.dimension.id !== m.dimension.id) return "gone";
    const gm = p.getGameMode();
    if (gm === GameMode.Creative || gm === GameMode.Spectator) return "pause";
    return isDying(p) ? "pause" : "ok";
  } catch {
    return "gone";
  }
}

/** 효과 세기 (없으면 -1) */
function effectAmp(e, id) {
  try {
    const x = e.getEffect(id);
    return x ? x.amplifier : -1;
  } catch {
    return -1;
  }
}

/** 움직이지 못함: 구속 VII 이상 (속박 화살·기절·빙결·무기 기절) */
function rooted(m) {
  return effectAmp(m, "slowness") >= MONSTER.rootAmp;
}

/** 공격하지 못함: 움직이지 못하고 나약함까지 (기절·빙결) */
function stunned(m) {
  return rooted(m) && effectAmp(m, "weakness") >= 0;
}

/** 나약함: 한 단계마다 피해 -weakPerLevel */
function weakMult(m) {
  const w = effectAmp(m, "weakness");
  return w < 0 ? 1 : Math.max(0, 1 - MONSTER.weakPerLevel * (w + 1));
}

/** 룬의 짧은 은신 (연막·월광 암살, 남은 시간 hideMax 틱 이하): 멀리서는 못 찾음 (달려들기·원거리 반격 없음). 투명화 물약처럼 긴 투명은 셈하지 않음 */
function hidden(p) {
  try {
    const e = p.getEffect("invisibility");
    return !!e && e.duration >= 0 && e.duration <= MONSTER.hideMax;
  } catch {
    return false;
  }
}

/**
 * 플레이어 눈에서 몬스터 몸이 보이는지 (벽 너머로는 서로 때리지 못함): 몸 위 가운데 · 가운데 · 위 네 모서리 중 하나라도 보이면
 * (기둥 밑에 붙은 몬스터처럼 가운데가 가려도 모서리가 보이면 터치로 때릴 수 있으니 보이는 것으로)
 */
function canSee(m, def, p) {
  try {
    const eye = p.getHeadLocation();
    const l = m.location;
    const { half, height } = monsterSize(def);
    const top = l.y + height - 0.1;
    const k = Math.max(0, half - 0.05);
    const pts = [
      { x: l.x, y: top, z: l.z },
      { x: l.x, y: l.y + height * 0.5, z: l.z },
      { x: l.x + k, y: top, z: l.z + k },
      { x: l.x - k, y: top, z: l.z + k },
      { x: l.x + k, y: top, z: l.z - k },
      { x: l.x - k, y: top, z: l.z - k },
    ];
    return pts.some((to) => lineOfSight(p, eye, to));
  } catch {
    return true;
  }
}

/** 플레이어가 터치로 때릴 수 있는 거리: 눈 → 몬스터 충돌 상자의 가장 가까운 점이 lungeRange 안 */
function touchReach(m, def, p) {
  const { half, height } = monsterSize(def);
  const eye = p.getHeadLocation();
  const l = m.location;
  const dh = Math.max(0, Math.hypot(eye.x - l.x, eye.z - l.z) - half);
  const dv = eye.y < l.y ? l.y - eye.y : eye.y > l.y + height ? eye.y - (l.y + height) : 0;
  return Math.hypot(dh, dv) <= MONSTER.lungeRange;
}

/** 공격 동작 끝에 노린 쪽(시작할 때 몬스터 → 플레이어)에서 arc 도 안인지. 몸에 붙어 있으면 늘 안 */
function inArc(m, def, aim, p) {
  const dx = p.location.x - m.location.x;
  const dz = p.location.z - m.location.z;
  const d = Math.hypot(dx, dz);
  if (!aim || d <= monsterSize(def).half + 0.1) return true;
  return (dx * aim.x + dz * aim.z) / d >= Math.cos((MONSTER.arc * Math.PI) / 180);
}

/** 지금 싸우는 대상 (시험·다른 모듈용) */
export function fightOf(m) {
  return fights.get(m?.id);
}

/**
 * 도발 (v2.20.0 아이기스의 방패): ticks 동안 이 몬스터가 그 플레이어를 노림 — 다음 공격 대상이 그 사람이고, 그동안 다른 사람이 때려도 바뀌지 않음
 * (싸우지 않던 몬스터도 싸움을 시작함). 걷는 쪽은 엔티티 (hurt_by_target) 가 정함 — 도발한 스킬의 피해가 마지막 피해면 같은 사람.
 * 몬스터가 아니거나 · 쓰러지는 중이거나 · 그 사람을 노릴 수 없으면 (크리에이티브 · 다른 차원) false
 */
export function tauntMonster(m, p, ticks) {
  const def = monsterDef(m);
  if (!def || hasNoHealth(m) || targetState(p, m) !== "ok") return false;
  const now = system.currentTick;
  let f = fights.get(m.id);
  if (!f) {
    f = { m, def, player: p, attackers: new Map(), nextAttack: now, nextLunge: now + 6 };
    fights.set(m.id, f);
  }
  f.player = p;
  f.attackers.set(p.id, { p, t: now });
  f.taunt = { id: p.id, until: now + ticks };
  return true;
}

// ------------------------------------------------------------ 연출
function eyeish(m, def) {
  return up(m.location, monsterSize(def).height * 0.62);
}

/** 공격 동작 시작: 몬스터 앞 바닥에 사거리만큼 퍼지는 고리 (공격 예고) */
function telegraph(m, def, toward) {
  const dim = m.dimension;
  const d = flat(sub(toward, m.location));
  const reachAll = monsterSize(def).half + def.reach;
  const c = add(m.location, d, reachAll * 0.55);
  shock(dim, c, [1.0, 0.28, 0.2], (reachAll * 0.55) / 2.1, def.windup / 20);
}

/** 맞힘: 플레이어 가슴 앞(몬스터 쪽)에 몬스터 색 할퀴기 참격 + 불꽃 (1인칭에서도 화면 아래쪽에 보임) */
function impactFx(m, def, p) {
  const dim = p.dimension;
  const chest = up(p.location, 1.2);
  const toM = flat(sub(m.location, p.location));
  const at = add(chest, toM, 0.7);
  const v = unit(sub(chest, eyeish(m, def)));
  const heavy = def.key === "troll";
  arc(dim, at, v, { shape: heavy ? "heavy" : "thin", flip: Math.random() < 0.5, size: heavy ? 1.45 : 1.0, life: 0.2, speed: 1, spin: Math.random() * 60 - 30, color: def.color });
  sparks(dim, at, def.color, 5, 3);
}

/** 헛손질: 몬스터 앞 허공을 가르는 흐린 참격 */
function missFx(m, def, toward) {
  const dim = m.dimension;
  const d = flat(sub(toward, m.location));
  const at = up(add(m.location, d, monsterSize(def).half + def.reach * 0.6), monsterSize(def).height * 0.5);
  arc(dim, at, d, { shape: "thin", flip: false, size: 1.1, life: 0.18, speed: 1, spin: -20, color: def.color, alpha: 0.5, core: false });
  play(dim, "item.spear.attack_miss", at, 0.9, 0.5);
}

function attackAnim(m, def) {
  try {
    m.playAnimation(`animation.rpg.monster.${def.key}.attack`);
  } catch {
    // 표시만
  }
}

// ------------------------------------------------------------ 공격
/** 공격 동작 시작 (근접·달려들기): windup 틱 뒤 처음 노린 플레이어가 맞았는지 봄. 다음 공격은 시작부터 every 틱 뒤 */
function startAttack(f, now, kind) {
  const { m, def, player } = f;
  attackAnim(m, def);
  play(m.dimension, def.sounds.windup, m.location, 0.9 + Math.random() * 0.2, 0.8);
  telegraph(m, def, player.location);
  f.nextAttack = now + def.every;
  f.windup = { start: now, at: now + def.windup, kind, target: player, aim: flat(sub(player.location, m.location)) };
}

/** 달려들기: 플레이어 쪽으로 뛰어듦 (남은 거리 - reach 절반만큼) + 공격 동작 */
function lunge(f, g, now) {
  const { m, def, player } = f;
  const dir = flat(sub(player.location, m.location));
  const travel = Math.max(0, g.gap - def.reach * 0.5);
  const h = Math.max(0.3, Math.min(1.0, travel * MONSTER.lungePerBlock));
  const vy = g.dy > 0.6 ? 0.5 : 0.32;
  try {
    m.clearVelocity();
    m.applyImpulse({ x: dir.x * h, y: vy, z: dir.z * h });
  } catch {
    // 못 움직이면 제자리에서 공격 동작만
  }
  const dim = m.dimension;
  burst(dim, up(m.location, 0.3), [0.55, 0.5, 0.45], 0.9 + monsterSize(def).half, 0.3);
  beam(dim, up(m.location, monsterSize(def).height * 0.45), up(add(m.location, dir, Math.min(travel, 4)), monsterSize(def).height * 0.45), def.color, { half: 0.3, thick: 0.1, life: 0.25, alpha: 0.6 });
  play(dim, "mob.breeze.jump", m.location, 0.8, 0.8);
  f.nextLunge = now + def.lungeEvery;
  startAttack(f, now, "lunge");
}

/**
 * 원거리 반격: 높이 차이로 닿지도 달려들지도 못하는데 플레이어는 때릴 수 있는 곳 → 플레이어 발밑에 붉은 고리,
 * windup + counterExtra 틱 뒤 몬스터 색 기운이 날아와 그 자리를 침 (달려들기와 같은 lungeEvery 마다)
 */
function counter(f, now) {
  const { m, def, player } = f;
  const dim = m.dimension;
  const spot = { ...player.location };
  const ticks = def.windup + MONSTER.counterExtra;
  attackAnim(m, def);
  play(dim, def.sounds.windup, m.location, 0.8, 1);
  shock(dim, spot, [1.0, 0.28, 0.2], MONSTER.counterRadius / 2.1, ticks / 20);
  // 날아가는 기운: 마지막 0.2초 동안 몬스터 → 그 자리
  beam(dim, eyeish(m, def), up(spot, 0.9), def.color, { half: 0.3, thick: 0.12, life: 0.15, delay: Math.max(0, ticks / 20 - 0.2), sweep: 0.2, alpha: 0.85 });
  f.nextLunge = now + def.lungeEvery;
  f.nextAttack = now + Math.max(def.every, ticks + 4);
  f.windup = { start: now, at: now + ticks, kind: "counter", target: player, spot };
}

/**
 * 몬스터 피해 한 번 (스크립트 공격: 바닐라 근접 막기를 통과하도록 표시하고 넣음). 들어갔으면 true
 * (무적 시간 등으로 안 들어가면 false: 맞힘 연출은 그대로, 맞으면 붙는 효과만 없음)
 * @param {any} m 몬스터
 * @param {any} def
 * @param {any} target 맞는 플레이어
 * @param {number} amount 바닐라 단위 피해 (갑옷·체력 스탯은 게임·hp.js 가 계산)
 */
export function monsterHit(m, def, target, amount) {
  const key = `${target.id}|${m.id}`;
  ownHits.set(key, system.currentTick);
  let ok = false;
  try {
    ok = amount > 0 && target.applyDamage(amount, { cause: EntityDamageCause.entityAttack, damagingEntity: m });
  } catch {
    ok = false;
  }
  if (!ok) return false;
  if (def.onHit) {
    try {
      target.addEffect(def.onHit.effect, Math.round(def.onHit.seconds * 20), { amplifier: def.onHit.amplifier, showParticles: true });
    } catch {
      // 무시
    }
  }
  return true;
}

/** 트롤 내려치기 충격파: 맞은 곳 둘레 radius 칸의 다른 플레이어도 피해 × mult */
function shockwave(f, target, dmg) {
  const { m, def } = f;
  const dim = m.dimension;
  const at = target.location;
  shock(dim, at, def.color, def.shock.radius / 2.1, 0.45);
  burst(dim, up(at, 0.2), [0.5, 0.42, 0.32], 1.6, 0.35);
  let near = [];
  try {
    near = dim.getPlayers({ location: at, maxDistance: def.shock.radius + 0.5 });
  } catch {
    near = [];
  }
  for (const p of near) {
    if (targetState(p, m) !== "ok") continue;
    shake(p, 0.25, 0.3);
    if (p.id === target.id) continue;
    if (Math.hypot(p.location.x - at.x, p.location.z - at.z) > def.shock.radius) continue;
    monsterHit(m, def, p, dmg * def.shock.mult);
  }
}

/** 맞힘: 피해(나약함만큼 줄어듦) + 연출 + 트롤 충격파 */
function strike(f, target) {
  const { m, def } = f;
  const dmg = def.attack * weakMult(m);
  monsterHit(m, def, target, dmg);
  impactFx(m, def, target);
  play(m.dimension, def.sounds.hit, target.location, 0.9 + Math.random() * 0.2, 1);
  if (def.shock) shockwave(f, target, dmg);
}

/** 공격 동작 끝: 처음 노린 플레이어가 아직 사거리·노린 쪽 안이고 보이면 맞음, 아니면 빗나감. 대상이 사라졌으면 조용히 끝 */
function resolve(f, now) {
  const { m, def } = f;
  const w = f.windup;
  f.windup = undefined;
  f.nextAttack = Math.max(f.nextAttack, now + 4);
  if (!w) return false;
  const target = w.target;
  if (targetState(target, m) !== "ok") return false;
  if (w.kind === "counter") {
    const dim = m.dimension;
    const at = /** @type {{x: number, y: number, z: number}} */ (w.spot);
    shock(dim, at, def.color, MONSTER.counterRadius / 2.1, 0.35);
    burst(dim, up(at, 0.3), def.color, 1.2, 0.3);
    const l = target.location;
    if (Math.hypot(l.x - at.x, l.z - at.z) <= MONSTER.counterRadius && Math.abs(l.y - at.y) <= 1.5 && canSee(m, def, target)) {
      strike(f, target);
      return true;
    }
    play(dim, "item.spear.attack_miss", at, 0.9, 0.5);
    return false;
  }
  const g = gapTo(m, def, target);
  if (g.gap <= def.reach + MONSTER.tolerance && heightOk(def, g.dy, 0.5) && inArc(m, def, w.aim, target) && canSee(m, def, target)) {
    strike(f, target);
    return true;
  }
  missFx(m, def, target.location);
  return false;
}

/**
 * 이번에 쫓을 플레이어: 지금 대상 → 최근에 때린 순서. 공격할 수 있는(ok) 첫 사람, 없으면 잠깐 멈춘(pause) 사람. 모두 없어졌거나 leash 밖이면 undefined
 * (마지막으로 때린 사람이 크리에이티브가 되거나 멀리 가도, 옆에서 싸우던 사람을 계속 상대함)
 */
function pickTarget(f) {
  const { m, def } = f;
  const list = [f.player, ...[...f.attackers.values()].sort((a, b) => b.t - a.t).map((a) => a.p)];
  let paused;
  for (const p of list) {
    const st = targetState(p, m);
    if (st === "gone") {
      if (p?.id !== undefined) f.attackers.delete(p.id);
      continue;
    }
    if (gapTo(m, def, p).dist > MONSTER.leash) continue;
    if (st === "ok") return { p, st };
    if (!paused) paused = p;
  }
  return paused ? { p: paused, st: "pause" } : undefined;
}

function fightTick() {
  const now = system.currentTick;
  for (const [id, f] of fights) {
    const { m, def } = f;
    if (!m.isValid || hasNoHealth(m)) {
      fights.delete(id);
      continue;
    }
    const pick = pickTarget(f);
    if (!pick) {
      fights.delete(id); // 때린 사람이 모두 없어지거나 leash 밖
      continue;
    }
    f.player = pick.p;
    const st = pick.st;
    const g = gapTo(m, def, f.player);
    if (st === "pause") {
      // 크리에이티브·관전·쓰러지는 중: 하던 동작은 끊고 기다림 (토템으로 살아나면 이어서), 오래가면 싸움 끝
      f.windup = undefined;
      if (f.paused === undefined) f.paused = now;
      if (now - f.paused > MONSTER.pauseLimit) fights.delete(id);
      continue;
    }
    f.paused = undefined;
    if (stunned(m)) {
      f.windup = undefined; // 기절·빙결: 하던 동작도 끊김
      continue;
    }
    if (f.windup) {
      if (now >= f.windup.at) resolve(f, now);
      continue;
    }
    if (now < f.nextAttack) continue;
    const p = f.player;
    if (g.gap <= def.reach && heightOk(def, g.dy)) {
      if (canSee(m, def, p)) startAttack(f, now, "melee");
      continue;
    }
    if (now < f.nextLunge || rooted(m) || hidden(p)) continue;
    if (g.gap <= MONSTER.lungeRange && Math.abs(g.dy) <= MONSTER.lungeHeight) {
      if (canSee(m, def, p)) lunge(f, g, now);
    } else if (now - (f.attackers.get(p.id)?.t ?? -1e9) <= MONSTER.counterWindow && touchReach(m, def, p) && canSee(m, def, p)) {
      counter(f, now); // 닿지 못하는 곳에서 방금(counterWindow 틱 안) 때렸을 때만
    }
  }
  for (const [k, t] of ownHits) if (now - t > 2) ownHits.delete(k);
}

/** 회복: 싸움이 끝나고 regenDelay 틱 동안 맞지 않으면 1초마다 10%, 트롤은 싸우는 중에도 regen.after 틱 동안 안 맞으면 */
function regenTick() {
  const now = system.currentTick;
  for (const [id, s] of hurt) {
    const { m, def } = s;
    if (!m.isValid) {
      hurt.delete(id);
      continue;
    }
    let rate = 0;
    if (fights.has(id)) {
      if (def.regen && now - s.t >= def.regen.after) rate = def.regen.perSec;
    } else if (now - s.t >= MONSTER.regenDelay) rate = MONSTER.regenPerSec;
    else if (def.regen && now - s.t >= def.regen.after) rate = def.regen.perSec;
    if (rate <= 0) continue;
    try {
      const h = m.getComponent("minecraft:health");
      const max = h.effectiveMax;
      const cur = h.currentValue;
      if (cur <= 0) continue; // 죽는 중
      const next = Math.min(max, cur + max * rate);
      if (next > cur + 1e-9) {
        h.setCurrentValue(next);
        if (def.regen && rate === def.regen.perSec) sparks(m.dimension, up(m.location, monsterSize(def).height * 0.5), [0.45, 1.0, 0.45], 3, 1.5);
      }
      if (next >= max - 1e-6 && !fights.has(id)) hurt.delete(id);
    } catch {
      hurt.delete(id);
    }
    refreshTag(m, def);
  }
}

// ------------------------------------------------------------ 이벤트
function onHurt(ev) {
  const m = ev.hurtEntity;
  const def = monsterDef(m);
  if (!def || hasNoHealth(m)) return; // 죽는 중인 몸은 싸움을 시작하지 않음
  const now = system.currentTick;
  hurt.set(m.id, { m, def, t: now });
  refreshTag(m, def);
  const p = ev.damageSource?.damagingEntity;
  if (p?.typeId === PLAYER) noteHit(m, p); // v2.7.0: 불에 타 죽어도 마지막으로 때린 사람이 잡은 것으로 (loot.js killCredit)
  if (!p || p.typeId !== PLAYER || targetState(p, m) !== "ok") return;
  const f = fights.get(m.id);
  if (f) {
    // 다음 공격은 마지막으로 때린 사람에게 (엔티티 hurt_by_target 과 같게). 하던 동작은 처음 노린 사람에게. v2.20.0 도발 중에는 도발한 사람 그대로
    if (!f.taunt || now >= f.taunt.until || f.taunt.id === p.id) f.player = p;
    f.attackers.set(p.id, { p, t: now });
    return;
  }
  // 처음 맞음: 사거리 안이면 바로 반격, 멀면 잠깐 뒤 달려듦
  fights.set(m.id, { m, def, player: p, attackers: new Map([[p.id, { p, t: now }]]), nextAttack: now, nextLunge: now + 6 });
}

function onDie(ev) {
  const e = ev.deadEntity;
  if (e?.typeId === PLAYER) {
    forgetMonsterFights(e.id); // 죽은 플레이어와의 싸움 끝 (되살아나도 다시 때려야 싸움)
    return;
  }
  const def = monsterDef(e);
  if (!def) return;
  fights.delete(e.id);
  hurt.delete(e.id);
  try {
    const at = up(e.location, monsterSize(def).height * 0.5);
    burst(e.dimension, at, def.color, 1.2 + monsterSize(def).half, 0.35);
    sparks(e.dimension, at, def.color, 8, 4);
  } catch {
    // 무시
  }
  // v2.7.0: 잡은 플레이어에게 경험치 (최고 레벨이 xpUntil 이상이면 0) + 전리품 (loot.js). 불에 타 죽으면 10초 안에 마지막으로 때린 사람
  try {
    lootOnDeath(e, def, killCredit(e, ev.damageSource), monsterSize(def).height);
  } catch (err) {
    console.warn(`[rpg] monster loot: ${err}`);
  }
}

/** 바닐라 근접 공격(엔티티가 쫓아가서 휘두른 것)은 막음. 스크립트 공격과 RPG 체력이 0이 된 플레이어의 마무리 피해만 통과 */
function blockVanillaHit(ev) {
  const src = ev.damageSource;
  const m = src?.damagingEntity;
  if (!m || !DEFS.has(m.typeId) || src.cause !== EntityDamageCause.entityAttack) return false;
  const v = ev.hurtEntity;
  if (v?.typeId === PLAYER && isDying(v)) return false; // hp.js 가 때린 몬스터를 원인으로 마무리 (사망 메시지)
  const t = ownHits.get(`${v.id}|${m.id}`);
  return !(t !== undefined && system.currentTick - t <= 1);
}

/** 몬스터가 받는 피해 (플레이어가 때린 것): 스켈야스크림슬리 껍질 -20% */
function adjustIncoming(ev) {
  const def = DEFS.get(ev.hurtEntity?.typeId);
  if (!def?.shell) return;
  const a = ev.damageSource?.damagingEntity;
  if (a?.typeId !== PLAYER) return;
  ev.damage = ev.damage * (1 - def.shell);
}

/** 이름표를 달고, 다친 채로 (다시) 불러온 몬스터는 바로 회복을 시작 */
function track(e, def) {
  refreshTag(e, def);
  try {
    const h = e.getComponent("minecraft:health");
    if (h && h.currentValue > 0 && h.currentValue < h.effectiveMax - 1e-6 && !hurt.has(e.id)) hurt.set(e.id, { m: e, def, t: system.currentTick - MONSTER.regenDelay });
  } catch {
    // 무시
  }
}

/** 이미 불러와 있는 몬스터 (/reload 뒤): 이름표·회복 */
export function trackLoadedMonsters() {
  for (const d of ["overworld", "nether", "the_end"]) {
    let dim;
    try {
      dim = world.getDimension(d);
    } catch {
      continue;
    }
    for (const [id, def] of DEFS) {
      let list = [];
      try {
        list = dim.getEntities({ type: id });
      } catch {
        list = [];
      }
      for (const e of list) track(e, def);
    }
  }
}

/** 눈앞(4칸)에 몬스터 n 마리 (시험·배치용, 운영자) */
export function spawnMonsters(player, key, n = 1) {
  const id = monsterId(key);
  if (!DEFS.has(id)) return [];
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const side = { x: -f.z, y: 0, z: f.x };
  const out = [];
  const count = Math.max(1, Math.min(10, Math.floor(n)));
  for (let i = 0; i < count; i++) {
    const off = (i - (count - 1) / 2) * 1.8;
    const at = add(add(player.location, f, 4 + monsterSize(DEFS.get(id)).half), side, off);
    try {
      const e = dim.spawnEntity(id, at);
      e.nameTag = monsterLabel(DEFS.get(id), 1, 1);
      out.push(e);
    } catch {
      // 무시
    }
  }
  return out;
}

/** 주변 radius 칸의 몬스터 치우기. 치운 수 */
export function clearMonsters(player, radius = 48) {
  let n = 0;
  for (const id of DEFS.keys()) {
    let list = [];
    try {
      list = player.dimension.getEntities({ type: id, location: player.location, maxDistance: radius });
    } catch {
      list = [];
    }
    for (const e of list) {
      try {
        // v2.6.0: 사냥터 몬스터는 남김 (치워도 다시 채워짐 → /mobzone clear)
        if (e.getTags().some((t) => t.startsWith("rpg_zone_"))) continue;
        fights.delete(e.id);
        hurt.delete(e.id);
        e.remove();
        n++;
      } catch {
        // 무시
      }
    }
  }
  return n;
}

export function registerMonsters() {
  setMonsterHooks({ block: blockVanillaHit, incoming: adjustIncoming });
  addTauntHook((p, m, ticks) => tauntMonster(m, p, ticks)); // v2.20.0 아이기스의 방패 도발
  setDamageTextAnchor((victim) => {
    const def = DEFS.get(victim?.typeId);
    if (!def) return undefined;
    const l = victim.location;
    return { x: l.x, y: l.y + damageTextHeight(def), z: l.z };
  });
  world.afterEvents.entityHurt.subscribe((ev) => {
    try {
      onHurt(ev);
    } catch (e) {
      console.warn(`[rpg] monster hurt: ${e}`);
    }
  });
  world.afterEvents.entityDie.subscribe((ev) => {
    try {
      onDie(ev);
    } catch (e) {
      console.warn(`[rpg] monster die: ${e}`);
    }
  });
  // 알·/summon 으로 나오거나 청크와 함께 불러온 몬스터: 이름표, 다쳤으면 회복
  world.afterEvents.entitySpawn.subscribe((ev) => {
    const e = ev.entity;
    const def = monsterDef(e);
    if (!def) return;
    system.run(() => {
      if (e.isValid) track(e, def);
    });
  });
  // 청크를 다시 불러오거나 차원을 옮겨 온 몬스터 (게임은 entityLoad 로 알림)
  world.afterEvents.entityLoad?.subscribe((ev) => {
    const e = ev.entity;
    const def = monsterDef(e);
    if (!def) return;
    system.run(() => {
      if (e.isValid) track(e, def);
    });
  });
  // 몬스터에게 쓰러진 플레이어의 사망 메시지를 위해 줄였던 이름표를 hp.js 가 다시 맞출 때 (지금 체력으로)
  setNameRefresh((e) => {
    const def = monsterDef(e);
    if (!def) return false;
    refreshTag(e, def);
    return true;
  });
  world.afterEvents.worldLoad.subscribe(() => {
    system.runTimeout(() => {
      try {
        trackLoadedMonsters();
      } catch (e) {
        console.warn(`[rpg] monster scan: ${e}`);
      }
    }, 20);
  });
  system.runInterval(fightTick, 2);
  system.runInterval(regenTick, 20);
}

/** 플레이어가 나가거나 죽으면 그 플레이어와의 싸움을 끝냄 (같은 몬스터를 때린 다른 플레이어가 있으면 그 사람과 계속) */
export function forgetMonsterFights(playerId) {
  for (const [id, f] of fights) {
    try {
      f.attackers.delete(playerId);
      if (f.windup?.target?.id === playerId) f.windup = undefined;
      if (f.taunt?.id === playerId) f.taunt = undefined; // v2.20.0 죽은 사람의 도발은 끝
      if (f.player?.id !== playerId && f.player?.isValid) continue;
      const next = [...f.attackers.values()].filter((a) => a.p?.isValid && a.p.id !== playerId).sort((a, b) => b.t - a.t)[0];
      if (next) f.player = next.p;
      else fights.delete(id);
    } catch {
      fights.delete(id);
    }
  }
}
