import { EntityDamageCause, GameMode, ItemStack, system, world } from "@minecraft/server";
import { ALL_BOSS_ORDER, BOSS, BOSS_MATERIAL_ORDER, BOSS_MATERIALS, BOSSES, RAID_MATERIALS, altarId, bossId, bossUnitKeys, unitHomeRadius } from "./config.js";
import { addAltar, altarAt, altarKeyOf, altars, arenaCenter, arenaFeatures, askBuild, canManage, inArena, isBuilding, playersIn, saveAltars, setAltarRemovedHook } from "./arena.js";
import { addHurtHooks, hitFeedback, isSkillHit } from "./combat.js";
import { MenuForm } from "./forms.js";
import { notify, play, setDamageTextAnchor, showFaceText } from "./fx.js";
import { materialItem } from "./enhance.js";
import { isDying } from "./hp.js";
import { createTicketItem } from "./runes.js";
import { giveItem } from "./shop.js";
import { addTauntHook, fx, setBossFieldCheck } from "./skills.js";
import { audience, bossHit, bossLabel, bossSize, cleanOwn, disc, fanMarks, fighters, groundUnder, hdist, inFan, isOwnHit, lobArc, meleeTarget, say, sound, title } from "./bosskit.js";
import SCARECROW_KING from "./boss_scarecrow.js";
import SCORPIONS from "./boss_scorpions.js";
import KRAKEN from "./boss_kraken.js";
import GREAT_WORM from "./boss_worm.js";
import JORMUNGANDR from "./boss_jormungandr.js";
import NIDHOGG from "./boss_nidhogg.js";
import SKOLL_HATI from "./boss_skoll.js";
import YMIR from "./boss_ymir.js";
import SURTR from "./boss_surtr.js";
import { bossMaterialItem } from "./smith.js";
import { isRooted } from "./status.js";
import { cleanupAreas, holdArea, releaseArea } from "./ticking.js";
import { setSkyBlocker } from "./ultfx.js";

/**
 * 보스 엔진 (v1.8.0): 제단 터치 → 소환 → 싸움 → 보상·재사용 대기 / 초기화
 * - 보스 체력: 바닐라 체력(보스 막대에 보임)을 스크립트가 줄임. 플레이어가 준 피해는 before 이벤트에서 가져가고(바닐라 피해 취소)
 *   플레이어마다 무적 시간(BOSS.iframes)을 따로 셈 → 파티가 때리면 그만큼 빨리 잡힘. 환경·몹 피해는 받지 않음.
 *   v2.1.0: 평타(근접·활·완드)와 스킬·룬 피해는 무적 시간을 따로 셈 (평타 바로 뒤에 쓴 스킬이 막히거나 줄지 않게)
 * - 맞으면 붉게 번쩍임 (엔티티 속성 rpg:flash → 렌더 컨트롤러), 피해 숫자·치명타·참격은 평소와 같이 (combat.js hitFeedback)
 * - 싸우는 플레이어가 필드에 없는 채로 BOSS.resetAfter 틱이 지나면 제단으로 돌아가 초기화 (재사용 대기 없음). 필드 밖으로 밀려나면 가운데로
 * - 잡으면 필드 안에 있었거나 BOSS.participantGrace 틱 안에 때린 플레이어 모두에게 보상, 제단은 cooldown 초 동안 쉼 (실제 시간)
 * - 패턴은 보스별 모듈(boss_*.js)이 고르고, 엔진은 틀(cast)·평타·단계 이동·정리를 맡음
 * - 여러 마리 싸움 (v1.9.0, 설정의 units): 한 마리 = unit. 무적 시간·틀·평타·멈춤·제자리(home) 끌어오기·무적은 마리마다 따로,
 *   한 마리가 쓰러지면 모듈에 알림(unitDied), 모두 쓰러지면 보상. 한 마리 보스는 싸움 자신이 유일한 unit (enc.units = [enc])
 *   모듈 함수는 마리를 받음: pick(u) · hurt(u) · afterHurt(u), 싸움 전체는 start(enc) · tick(enc) · unitDied(enc, u) · end(enc) · propHit(enc)
 * - 움직이지 않는 보스 (v2.0.0 크라켄, 설정 stationary): 발 높이 = 바닥 + stationary.y, 몸 돌리기 face(u, 방향) → 매 틱 stationary.turn 도씩 (teleport facingLocation).
 *   평타도 대상 쪽으로 몸을 돌림
 * - 맞는 소품 (v2.0.0 크라켄 촉수): addProp(enc, e, {hp}) 이면 엔진이 체력을 맡음 — 보스처럼 플레이어마다 무적 시간, 필드 밖 공격 막기, 번쩍임·피해 숫자,
 *   맞은 뒤 모듈 propHit(enc, 소품, 플레이어, 피해, 남은 체력), 체력 0 이면 propDied(enc, 소품, 플레이어) (치우는 것은 모듈이 dropProp). 그냥 소품(분신·폭탄)은 예전처럼 propHit 만
 * - 굴을 옮겨 다니는 보스 (v2.1.0 거대 웜): seat(u, 자리, 방향) 로 제 자리(home)를 바꾸고 바로 옮김 (움직이지 않는 보스).
 *   모듈 targetable(enc, 플레이어) 가 false 인 사람(삼켜진 사람 등)은 평타·패턴 고르기의 대상에서 빠지고 평타에 맞지도 않음
 * - 헤엄쳐 도는 보스 (v2.2.0 요르문간드, 설정 swim): 모듈이 매 틱 pose(u, 자리, 방향) 로 옮기고 몸도 모듈이 돌림 (엔진의 몸 돌리기 없음).
 *   face(u, 방향) 은 바라보려는 쪽(faceTo)만 정하고, 모듈이 목을 그쪽으로 돌림. 평타도 모듈이 (meleeAt = Infinity)
 * - 걷고 나는 보스 (v2.3.0 니드호그, 설정 drive): 헤엄치는 보스처럼 모듈이 매 틱 pose 로 옮김 (땅에서 걷고, 날아오르고, 내려앉음). 몸 돌리기·평타도 모듈이
 * - 근접 보너스 (v2.3.0, BOSS.close): 보스·맞는 소품의 몸 겉면에서 range 칸 안에서 준 피해 × (1 + bonus) — 평타·스킬·룬 모두. 피해 숫자 앞에 붉은 »
 * - 원거리 견제 (v2.3.0, 마리 설정 far): 몸에서 far.from 칸 넘게 떨어진 사람(가장 먼 사람부터 count 명) 발밑에 원 → 보스가 던진 것이 포물선으로 날아와
 *   windup 틱 뒤 떨어짐 (원 안 피해 + 효과, 때린 엔티티 없이). 틀 없이 (보스는 하던 일을 계속). 모듈 farOk(u, now) 가 false 면 쉼 (큰 패턴 동안 등),
 *   쉰 뒤 FAR_GRACE 틱은 더 쉬고, FAR_SETTLE 틱 넘게 멀리 있던 사람만 노림 (밀려나거나 흩어지라는 패턴 직후가 아님). 속박된 사람은 노리지 않고 맞지도 않음.
 *   던지는 곳은 모듈 farFrom(u) (없으면 몸 높이의 3/4), 떨어지는 자리는 서 있는 바닥. 모듈 dying(enc) = 쓰러질 때 (연출 자리를 정리)
 * - v2.4.0 (스콜과 하티): 모듈 hurt(u, p, 피해) 는 수 대신 {amount, mark} 를 돌려줄 수 있음 (mark = 피해 숫자 앞 표시, 예: 약점 — 근접 보너스 » 앞에).
 *   모듈 farStyle(u) = 이번 원거리 견제의 모양 (설정 far 에 덮어씀: 이름·look·효과·불 — 낮밤에 따라 다른 것), farFrom(u, 모양)
 * - v2.5.0 (이미르): 모듈 hurt(u, p, 피해, 맞은 기록) — 맞은 기록 h.near = 근접 보너스 거리에서 때림 (보너스가 0 이어도; 균열은 가까이에서 때린 피해만 온전히 셈).
 *   원거리 견제 모양 ice (얼음 창: 떨어진 자리에 얼음 가시). 보스 안개(/fog 이름표 rpg_boss_fog)는 처음 들어온 플레이어와 월드를 불러온 뒤(/reload) 남은 것을 걷음,
 *   모듈 fogCleared(enc, 플레이어) = 엔진이 그 사람의 보스 안개를 걷었음 (필드 둘레면 새로 씌우게)
 */

/** 보스 키 → 모듈 */
const MODS = { scarecrow_king: SCARECROW_KING, scorpions: SCORPIONS, kraken: KRAKEN, great_worm: GREAT_WORM, jormungandr: JORMUNGANDR, nidhogg: NIDHOGG, skoll_hati: SKOLL_HATI, ymir: YMIR, surtr: SURTR };
/** 보스 엔티티 ID → 싸움 키 (여러 마리 싸움은 마리마다) */
const BOSS_IDS = new Map(ALL_BOSS_ORDER.flatMap((k) => bossUnitKeys(k).map((u) => /** @type {[string, string]} */ ([bossId(u), k]))));

/**
 * 보스 한 마리 (한 마리 보스는 싸움 자신)
 * @typedef {{
 *   enc: any, key: string, index: number, def: any, a: any, dim: any, center: {x: number, y: number, z: number}, feat: any,
 *   parts: Map<string, {p: any, dmg: number, last: number, diedAt: number}>, api: any, mod: any, mem: any, boss?: any,
 *   alive: boolean, home: {x: number, y: number, z: number}, leash: number, iframes: Map<string, {tick: number, amount: number}>,
 *   cast?: {id: string, until: number, steps: {at: number, fn: (u: any, now: number) => void}[]},
 *   meleeAt: number, staggerUntil: number, held: boolean, immune: boolean, fast?: boolean, dmgMult?: number, cdMult?: number,
 *   faceTo?: number, faceNow?: number, farAt?: number, farPausedAt?: number, taunt?: {id: string, until: number}
 * }} Unit
 */
/**
 * 싸움 하나 (제단마다)
 * @typedef {Unit & {
 *   state: "summon" | "fight" | "dead", t0: number, spawnAt: number, units: Unit[],
 *   emptySince?: number, props: Set<any>, starter?: any, farShots: {u: any, spot: {x: number, y: number, z: number}, hitAt: number, style?: any}[], farTold: boolean,
 *   farSince: Map<string, number>, area?: string
 * }} Enc
 */
/** @type {Map<any, Enc>} 제단 → 싸움 */
const encs = new Map();
/** 보스·소품 엔티티 id → 싸움 */
const byEntity = new Map();
/** 보스 엔티티 id → 그 마리 */
const unitById = new Map();
/** 싸움이 가진 엔티티 (보스·소품·감옥) id: 이 밖의 보스·소품 엔티티는 남은 것 (청크를 다시 불러올 때 지움) */
const owned = new Set();
/** before 이벤트에서 가져간 피해 (다음 틱에 처리) */
const pending = [];
/** 필드 밖에서 때려 막힌 공격 알림 (플레이어 id → 틱) */
const farWarned = new Map();
/** 방금 가져간 공격: "대상|공격자" → 틱 */
const absorbedAt = new Map();
/** 맞는 소품 (v2.0.0): 엔티티 id → {max, textHeight, iframes: 플레이어 id → {tick, amount}, dead} */
const propInfo = new Map();
/** 근접 보너스가 붙은 피해 숫자 앞 표시 (v2.3.0) */
export const CLOSE_MARK = "§c»";

// ------------------------------------------------------------ 조회
export function encounterOf(e) {
  return e ? byEntity.get(e.id) : undefined;
}
/** 보스 엔티티 → 그 마리 (소품이면 undefined) */
export function unitOf(e) {
  return e ? unitById.get(e.id) : undefined;
}
/** 제단의 싸움 */
export function encounterAt(a) {
  return encs.get(a);
}
export function allEncounters() {
  return [...encs.values()];
}
/** 남은 체력 비율 (마리마다) */
function ratio(u) {
  if (!u.alive) return 0;
  try {
    const h = u.boss.getComponent("minecraft:health");
    return h.currentValue / h.effectiveMax;
  } catch {
    return 1;
  }
}
export function bossRatio(u) {
  return ratio(u);
}
const live = (enc) => encs.has(enc.a) && enc.state === "fight";
/** 같은 싸움의 살아 있는 다른 마리 (없으면 undefined) */
function other(u) {
  return u.enc.units.find((x) => x !== u && x.alive);
}

// ------------------------------------------------------------ 소환
function alertNearby(enc, big, small) {
  title(enc, big, small);
}

/**
 * 소환 시작 (제단 연출 BOSS.summonDelay 틱 뒤 보스가 나옴)
 * @param {any} a 제단
 * @param {any} [by] 소환한 플레이어
 */
export function summon(a, by) {
  if (encs.has(a) || !MODS[a.key]) return undefined;
  let dim;
  try {
    dim = world.getDimension(a.dim);
  } catch {
    return undefined;
  }
  const def = BOSSES[a.key];
  const now = system.currentTick;
  const center = arenaCenter(a);
  const enc = /** @type {Enc} */ (/** @type {unknown} */ ({
    a,
    key: a.key,
    index: 0,
    def,
    mod: MODS[a.key],
    dim,
    center,
    feat: arenaFeatures(a),
    state: "summon",
    t0: now,
    spawnAt: now + BOSS.summonDelay,
    parts: new Map(),
    iframes: new Map(),
    meleeAt: 0,
    props: new Set(),
    immune: false,
    staggerUntil: 0,
    mem: {},
    starter: by,
    held: false,
    api: API,
    alive: true,
    home: center,
    leash: unitHomeRadius(a.key) + 0.5,
    units: [],
    farShots: [],
    farTold: false,
    farSince: new Map(),
  }));
  enc.enc = enc;
  enc.units = def.units ? def.units.map((ud, i) => makeUnit(enc, ud, i)) : [enc];
  // v2.12.0 큰 필드 (arena.tick): 싸우는 동안 필드를 틱킹 영역으로 붙잡음 (사람이 한쪽 끝에 있어도 보스·블록이 멈추지 않게, 끝나면 치움)
  if (def.arena?.tick) enc.area = holdArea(dim, center, def.arena.radius + 4, "f");
  encs.set(a, enc);
  alertNearby(enc, `§6§l${def.name}`, `§7${def.title ?? ""} §8· §fLv.${def.level} §8· §7권장 3~4명`);
  say(enc, `§6[보스] §f${by?.name ?? "누군가"}님이 §6${def.name}§f을(를) 깨웠습니다!`);
  sound(enc, def.sounds.summon, enc.center, 0.8, 1);
  return enc;
}

/**
 * 여러 마리 싸움의 한 마리: 설정(units 한 줄) + 싸움 레벨·색·소리, 제 자리 = 가운데 + spawn
 * @param {Enc} enc
 * @returns {Unit}
 */
function makeUnit(enc, ud, i) {
  const c = enc.center;
  const [sx, sz] = ud.spawn ?? [0, 0];
  return {
    enc,
    key: ud.key,
    index: i,
    def: { ...ud, level: enc.def.level, color: ud.color ?? enc.def.color, sounds: { ...enc.def.sounds, ...(ud.sounds ?? {}) } },
    a: enc.a,
    dim: enc.dim,
    center: c,
    feat: enc.feat,
    parts: enc.parts,
    api: enc.api,
    mod: enc.mod,
    mem: enc.mem,
    alive: true,
    home: { x: c.x + sx, y: c.y, z: c.z + sz },
    leash: unitHomeRadius(enc.key, ud) + 0.5,
    iframes: new Map(),
    meleeAt: 0,
    staggerUntil: 0,
    held: false,
    immune: false,
  };
}

/** 보스가 서는 높이 (바닥 기준): 걷는 보스는 1칸 위에서 내려앉고, 움직이지 않는 보스(중력 없음)는 stationary.y 에 그대로 */
function liftOf(u) {
  return u.def.stationary ? (u.def.stationary.y ?? 0) : 1;
}

function spawnBoss(enc) {
  const c = enc.center;
  const now = system.currentTick;
  for (const u of enc.units) {
    let e;
    try {
      e = enc.dim.spawnEntity(bossId(u.key), { x: u.home.x, y: u.home.y + liftOf(u), z: u.home.z });
    } catch (err) {
      console.warn(`[rpg] boss spawn: ${err}`);
      endEncounter(enc, "error");
      return;
    }
    u.boss = e;
    byEntity.set(e.id, enc);
    unitById.set(e.id, u);
    owned.add(e.id);
    try {
      e.nameTag = bossLabel(enc.def.level, u.def.title || enc.def.title, u.def.name); // 보스 막대 이름과 같은 글 (v2.2.0, build.py boss_label 과 같은 규칙)
      // 여러 마리면 가운데(서로)를 보고 나옴
      if (u !== enc) e.teleport({ x: u.home.x, y: u.home.y + 1, z: u.home.z }, { facingLocation: { x: c.x, y: u.home.y + 1, z: c.z } });
      // 움직이지 않는 보스: 깨운 사람 쪽(없으면 남쪽)을 보고 나옴
      if (u.def.stationary) {
        const p = enc.starter?.isValid ? enc.starter.location : { x: c.x, y: c.y, z: c.z + 8 };
        setFacing(u, Math.atan2(p.z - u.home.z, p.x - u.home.x));
        u.faceTo = u.faceNow;
      }
    } catch {
      // 무시
    }
    u.meleeAt = now + 30;
    fx(enc.dim, "rpg:shock_ring", { x: u.home.x, y: u.home.y + 0.1, z: u.home.z }, { dir: { x: 0, y: 0, z: 0 }, size: 3, life: 0.8, color: u.def.color, alpha: 0.9 });
  }
  enc.state = "fight";
  sound(enc, enc.def.sounds.roar, c, 0.8, 1);
  try {
    enc.mod.start?.(enc);
  } catch (err) {
    console.warn(`[rpg] boss start: ${err}`);
  }
  // 모듈이 싸움의 기억(mem)을 새로 만들면 마리들도 같은 것을 봄
  for (const u of enc.units) u.mem = enc.mem;
}

// ------------------------------------------------------------ 피해 (before 이벤트 → 다음 틱)
function attackerOf(src) {
  const e = src?.damagingEntity;
  return e?.typeId === "minecraft:player" ? e : undefined;
}

/**
 * 플레이어마다 무적 시간 (마리·소품마다 따로): 그 사이 더 센 공격은 차이만큼 (바닐라와 같은 규칙을 사람마다 따로). 막히면 0
 * @param {Map<string, {tick: number, amount: number}>} map
 */
function throughIframes(map, id, amount, now) {
  const last = map.get(id);
  if (last && now - last.tick < BOSS.iframes) {
    if (amount <= last.amount) return 0;
    const diff = amount - last.amount;
    last.amount = amount;
    return diff;
  }
  map.set(id, { tick: now, amount });
  return amount;
}

/** 필드 밖(울타리 너머)에서 때린 공격인지 (패턴이 닿지 않는 곳에서 안전하게 때리지 못하게 막음, 가끔 알림) */
function hitFromOutside(enc, p, now) {
  if (inArena(enc.a, enc.a.dim, p.location, BOSS.combatMargin)) return false; // v2.1.0: 싸우는 사람(패턴 대상)과 같은 경계 (그 밖의 안전한 띠에서 때리지 못하게)
  if (now - (farWarned.get(p.id) ?? -100) > 40) {
    farWarned.set(p.id, now);
    system.run(() => {
      if (p.isValid) notify(p, "§c필드 밖에서는 보스에게 피해를 줄 수 없습니다", 40);
    });
  }
  return true;
}

/** before 이벤트: 보스·소품이 받는 피해를 가져감 (바닐라 피해는 취소) */
function absorb(ev) {
  const e = ev.hurtEntity;
  const enc = byEntity.get(e?.id);
  if (!enc) return false;
  const p = attackerOf(ev.damageSource);
  if (!p) return true; // 환경·몹 피해 없음
  const now = system.currentTick;
  const key = `${e.id}|${p.id}`;
  // 무적 시간 칸: 평타(근접·활·완드)와 스킬·룬 피해는 따로 (v2.1.0: 평타 바로 뒤에 쓴 스킬이 막히거나 줄지 않게)
  // v2.12.0: 스킬·룬 피해인지 (skill) 를 맞은 기록에도 넘김 (수르트의 반격: 칼이 금빛일 때 스킬·룬으로 때림)
  const skill = isSkillHit(e, p);
  const ch = skill ? `${p.id}|skill` : p.id;
  const u = unitById.get(e.id);
  if (!u) {
    const info = propInfo.get(e.id);
    if (info) {
      // 맞는 소품 (촉수 등): 보스처럼 무적 시간·필드 밖 막기, 체력은 엔진이
      if (enc.state !== "fight" || info.dead) {
        absorbedAt.delete(key);
        return true;
      }
      const amount = hitFromOutside(enc, p, now) ? 0 : throughIframes(info.iframes, ch, ev.damage, now);
      if (amount <= 0) {
        absorbedAt.delete(key);
        return true;
      }
      const close = BOSS.close.bonus > 0 && isClose(e, undefined, p, info.textHeight);
      pending.push({ enc, target: e, p, amount: amount * (close ? 1 + BOSS.close.bonus : 1), cause: ev.damageSource.cause, prop: true, info, close, skill });
      absorbedAt.set(key, now);
      return true;
    }
    // 소품 (분신 등): 모듈이 처리
    pending.push({ enc, target: e, p, amount: ev.damage, cause: ev.damageSource.cause, prop: true, skill });
    absorbedAt.set(key, now);
    return true;
  }
  if (enc.state !== "fight" || !u.alive) return true;
  if (hitFromOutside(enc, p, now)) {
    absorbedAt.delete(key);
    return true;
  }
  const amount = throughIframes(u.iframes, ch, ev.damage, now);
  if (amount <= 0) {
    absorbedAt.delete(key); // 막힌 공격은 맞은 것으로 치지 않음 (스킬의 흡혈 등)
    return true;
  }
  // 근접 보너스 (v2.3.0): 몸 가까이에서 준 피해는 더 셈 (무적 시간은 원래 피해로 셈). 설정에서 보너스를 0으로 두면 표시(»)도 없음.
  // near = 보너스와 상관없이 몸 가까이에서 때렸는지 (v2.5.0 이미르의 균열)
  const near = isClose(e, u.def, p);
  const close = BOSS.close.bonus > 0 && near;
  pending.push({ enc, unit: u, target: e, p, amount: amount * (close ? 1 + BOSS.close.bonus : 1), cause: ev.damageSource.cause, prop: false, close, near, skill });
  absorbedAt.set(key, now);
  return true;
}

/**
 * 근접 보너스를 받는 자리인지 (v2.3.0): 때린 사람이 몸 겉면(가로: 가운데 − 반폭)에서 BOSS.close.range 칸 안, 높이도 몸 둘레 (날고 있는 보스 밑은 아님).
 * def 가 없으면 (맞는 소품) 반폭 0.6칸·키 height
 * @param {any} e 맞은 보스·소품
 * @param {any} def 마리 설정 (보스)
 * @param {any} p 때린 플레이어
 * @param {number} [height]
 */
export function isClose(e, def, p, height = 3) {
  try {
    const size = def?.box ? bossSize(def) : { half: 0.6, height };
    const l = e.location;
    const q = p.location;
    return Math.hypot(q.x - l.x, q.z - l.z) - size.half <= BOSS.close.range && q.y > l.y - 3 && q.y < l.y + size.height + 1;
  } catch {
    return false;
  }
}

/**
 * v2.12.0 큰 보스의 피해 숫자 자리: 몸 가운데 l 에서 때린 사람 쪽으로 몸 겉면 (반폭 + 0.6칸, 때린 사람보다 멀지 않게), 높이 = 그 사람 눈높이 (발 + 1 ~ 머리 - 1 안).
 * 때린 사람이 없거나 몸 안이면 undefined (보통 자리)
 * @param {{x: number, y: number, z: number}} l
 * @param {any} def
 * @param {any} attacker
 */
export function textNearAt(l, def, attacker) {
  let q;
  try {
    if (!attacker?.isValid) return undefined;
    q = attacker.location;
  } catch {
    return undefined;
  }
  const size = bossSize(def);
  const dx = q.x - l.x;
  const dz = q.z - l.z;
  const L = Math.hypot(dx, dz);
  if (L < 0.5) return undefined;
  const r = Math.min(size.half + 0.6, Math.max(0, L - 0.8));
  const y = Math.max(l.y + 1, Math.min(l.y + size.height - 1, q.y + 1.5));
  return { x: l.x + (dx / L) * r, y, z: l.z + (dz / L) * r };
}

function absorbed(target, attacker) {
  const t = absorbedAt.get(`${target?.id}|${attacker?.id}`);
  return t !== undefined && system.currentTick - t <= 1;
}

/** 보스의 바닐라 근접(엔티티가 휘두른 것)은 막음. 스크립트 공격과 RPG 체력 0 플레이어의 마무리만 통과 */
function block(ev) {
  const src = ev.damageSource;
  const m = src?.damagingEntity;
  if (!m || !byEntity.has(m.id) || src.cause !== EntityDamageCause.entityAttack) return false;
  const v = ev.hurtEntity;
  if (v?.typeId === "minecraft:player" && isDying(v)) return false;
  return !isOwnHit(v, m);
}

function flash(e) {
  try {
    e.setProperty("rpg:flash", true);
  } catch {
    return;
  }
  system.runTimeout(() => {
    try {
      if (e.isValid) e.setProperty("rpg:flash", false);
    } catch {
      // 무시
    }
  }, BOSS.flashTicks);
}

function processPending() {
  const now = system.currentTick;
  const list = pending.splice(0);
  for (const h of list) {
    try {
      processHit(h, now);
    } catch (e) {
      console.warn(`[rpg] boss hit: ${e}`);
    }
  }
  for (const [k, t] of absorbedAt) if (now - t > 2) absorbedAt.delete(k);
}

/**
 * 싸움에 든 사람 (보상·즉사 대상): 피해가 들어간 공격만 셈 (눈덩이 등 0 피해는 아님).
 * dealt = 실제로 준 피해 (v2.12.0 피해 순위: 무적일 때 때린 것은 싸움에 든 것으로만 셈)
 */
function noteHit(enc, p, amount, now, dealt = amount) {
  if (!(amount > 0)) return;
  const part = enc.parts.get(p.id) ?? { p, dmg: 0, last: now, diedAt: -1 };
  part.dmg += Math.max(0, dealt);
  part.last = now;
  part.p = p;
  enc.parts.set(p.id, part);
}

/** 맞는 소품 (촉수 등): 체력을 줄이고 번쩍임·피해 숫자 → 모듈 propHit, 체력 0 이면 propDied (치우기는 모듈) */
function propHurt(h, now) {
  const { enc, target, p, info } = h;
  if (enc.state !== "fight" || info.dead || !(h.amount > 0)) return;
  const amount = h.amount;
  noteHit(enc, p, amount, now);
  let left = 0;
  try {
    const hc = target.getComponent("minecraft:health");
    left = Math.max(0, hc.currentValue - amount);
    if (left > 0) hc.setCurrentValue(left);
  } catch {
    left = 0;
  }
  flash(target);
  try {
    hitFeedback(target, p, amount, h.cause, 1, "normal", h.close ? CLOSE_MARK : "");
  } catch (e) {
    console.warn(`[rpg] boss feedback: ${e}`);
  }
  if (left <= 0) info.dead = true;
  try {
    enc.mod.propHit?.(enc, target, p, amount, left);
  } catch (e) {
    console.warn(`[rpg] boss prop: ${e}`);
  }
  if (left > 0) return;
  try {
    enc.mod.propDied?.(enc, target, p);
  } catch (e) {
    console.warn(`[rpg] boss prop died: ${e}`);
  }
}

function processHit(h, now) {
  const { enc, target, p } = h;
  {
    if (!encs.has(enc.a) || !target.isValid) return;
    if (h.prop) {
      if (h.info) {
        propHurt(h, now);
        return;
      }
      try {
        enc.mod.propHit?.(enc, target, p, h.amount);
      } catch (e) {
        console.warn(`[rpg] boss prop: ${e}`);
      }
      return;
    }
    const u = h.unit;
    if (enc.state !== "fight" || !u?.alive) return;
    let amount = h.amount;
    // 모듈 hurt: 받을 피해 (수) 또는 {amount, mark} (v2.4.0: mark = 피해 숫자 앞 표시, 예: 약점). v2.5.0: 맞은 기록 h 도 넘김 (h.near = 몸 가까이에서 때림, h.close = 근접 보너스를 받음)
    let mark = "";
    try {
      const r = enc.mod.hurt?.(u, p, amount, h);
      if (typeof r === "number") amount = r;
      else if (r && typeof r === "object") {
        amount = typeof r.amount === "number" ? r.amount : amount;
        mark = r.mark ?? "";
      }
    } catch (e) {
      console.warn(`[rpg] boss hurt: ${e}`);
    }
    noteHit(enc, p, amount, now, u.immune ? 0 : amount);
    if (u.immune || amount <= 0) {
      showFaceText(p, "§7무적", { dist: 2.4, side: 0.5, up: 0.1 });
      return;
    }
    let left = 0;
    try {
      const hc = target.getComponent("minecraft:health");
      left = Math.max(0, hc.currentValue - amount);
      if (left > 0) hc.setCurrentValue(left);
    } catch {
      left = 0;
    }
    flash(target);
    try {
      hitFeedback(target, p, amount, h.cause, 1, "normal", mark + (h.close ? CLOSE_MARK : ""));
    } catch (e) {
      console.warn(`[rpg] boss feedback: ${e}`);
    }
    if (left <= 0) {
      unitDied(enc, u, p);
      return;
    }
    try {
      enc.mod.afterHurt?.(u, p, amount, left);
    } catch (e) {
      console.warn(`[rpg] boss after hurt: ${e}`);
    }
  }
}

// ------------------------------------------------------------ 끝 (처치·초기화)
/**
 * 소품 (분신·폭탄): 받는 공격을 모듈이 처리하고(propHit), 싸움이 끝나면 함께 치움.
 * o.hp 가 있으면 맞는 소품 (v2.0.0 촉수): 엔티티 체력(엔티티 정의의 최대 체력 = o.hp)을 엔진이 줄이고 무적 시간·번쩍임·피해 숫자 (o.textHeight = 피해 숫자 높이)
 * @param {any} enc
 * @param {any} e
 * @param {{hp?: number, textHeight?: number}} [o]
 */
export function addProp(enc, e, o) {
  enc.props.add(e);
  byEntity.set(e.id, enc);
  owned.add(e.id);
  if (o?.hp) propInfo.set(e.id, { max: o.hp, textHeight: o.textHeight, iframes: new Map(), dead: false });
}
export function dropProp(enc, e) {
  enc.props.delete(e);
  byEntity.delete(e?.id);
  owned.delete(e?.id);
  propInfo.delete(e?.id);
  try {
    if (e?.isValid) e.remove();
  } catch {
    // 무시
  }
}
/** 맞는 소품을 더는 맞지 않게 (물러나는 촉수 등): 공격은 가져가되 피해 없음 */
export function retireProp(e) {
  const i = propInfo.get(e?.id);
  if (i) i.dead = true;
}
/** 싸움이 가진 엔티티로 표시만 (감옥처럼 바닐라 피해를 그대로 받는 것): 남은 것 정리에서 빠짐 */
export function own(e) {
  if (e?.id) owned.add(e.id);
}
export function disown(e) {
  if (e?.id) owned.delete(e.id);
}

function cleanup(enc) {
  try {
    enc.mod.end?.(enc);
  } catch (e) {
    console.warn(`[rpg] boss end: ${e}`);
  }
  for (const e of [...enc.props]) dropProp(enc, e);
  for (const u of enc.units) {
    if (!u.boss) continue;
    byEntity.delete(u.boss.id);
    unitById.delete(u.boss.id);
    owned.delete(u.boss.id);
  }
  releaseArea(enc.dim, enc.area);
  enc.area = undefined;
  encs.delete(enc.a);
}

/**
 * 초기화 (보상 없음, 재사용 대기 없음): 보스는 제단으로 돌아감
 * @param {Enc} enc
 * @param {string} why
 */
export function endEncounter(enc, why) {
  if (!encs.has(enc.a)) return;
  for (const u of enc.units) {
    const b = u.boss;
    try {
      if (b?.isValid && u.alive) {
        const at = b.location;
        fx(enc.dim, "rpg:bash_burst", { x: at.x, y: at.y + 2, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 3, life: 0.5, color: u.def.color, alpha: 0.9 });
        b.remove();
      }
    } catch {
      // 무시
    }
  }
  if (why !== "error" && why !== "removed") say(enc, `§7[보스] ${enc.def.name}이(가) 제단으로 돌아갔습니다 ${why === "empty" ? "(필드에 싸우는 사람이 없음)" : ""}`);
  cleanup(enc);
}

function rollRange(r) {
  if (!r) return 0;
  const [lo, hi, chance] = r;
  let n = lo + Math.floor(Math.random() * (hi - lo + 1));
  if (chance !== undefined && Math.random() < chance) n += 1;
  return Math.max(0, n);
}

/**
 * 보상 한 사람분 (아이템, 경험치). 받은 것 글자.
 * v2.12.0: ticketAdv = 고급 룬 뽑기권, 모듈 bonusLoot(enc) = 더 주는 것 {키: 개수} (수르트: 부순 부위마다 재료) — 굴린 수에 더함
 */
function reward(enc, p) {
  const L = enc.def.loot ?? {};
  /** @type {Record<string, number>} */
  let bonus = {};
  try {
    bonus = enc.mod.bonusLoot?.(enc) ?? {};
  } catch (e) {
    console.warn(`[rpg] boss bonus loot: ${e}`);
  }
  const more = (k) => Math.max(0, Math.floor(Number(bonus[k]) || 0));
  const got = [];
  /** make(n) → 아이템 (설명이 붙은 아이템은 게임에서 얻은 것과 겹치도록 같은 함수로 만듦) */
  const give = (make, n, label) => {
    if (n <= 0) return;
    try {
      giveItem(p, make(n));
      got.push(`${label} ${n}`);
    } catch {
      // 무시
    }
  };
  give((n) => new ItemStack("minecraft:emerald", n), rollRange(L.emerald) + more("emerald"), "에메랄드");
  give((n) => createTicketItem(n), rollRange(L.ticket), "룬 뽑기권");
  give((n) => createTicketItem(n, true), rollRange(L.ticketAdv), "고급 룬 뽑기권");
  for (const k of ["stone", "bright", "scroll", "ore"]) give((n) => materialItem(k, n), rollRange(L[k]), RAID_MATERIALS[k].name);
  // 보스 재료 (v2.0.0): 보스 무기 제작용
  for (const k of BOSS_MATERIAL_ORDER) give((n) => bossMaterialItem(k, n), rollRange(L[k]) + more(k), BOSS_MATERIALS[k].name);
  if (L.xp > 0) {
    try {
      p.addExperience(L.xp);
      got.push(`경험치 ${L.xp}`);
    } catch {
      // 무시
    }
  }
  return got;
}

function canReward(p) {
  try {
    if (!p?.isValid) return false;
    const gm = p.getGameMode();
    return gm !== GameMode.Creative && gm !== GameMode.Spectator;
  } catch {
    return false;
  }
}

/**
 * 한 마리가 쓰러짐: 모두 쓰러졌으면 처치(보상), 아니면 그 마리만 쓰러뜨리고 모듈에 알림 (남은 마리의 분노 등)
 * @param {Enc} enc
 * @param {Unit} u
 */
function unitDied(enc, u, lastHitter) {
  if (!u.alive || enc.state !== "fight") return;
  u.alive = false;
  u.cast = undefined;
  u.immune = false;
  if (enc.units.every((x) => !x.alive)) {
    bossDied(enc, lastHitter, u);
    return;
  }
  const b = u.boss;
  const at = b?.isValid ? { ...b.location } : u.home;
  if (b) {
    byEntity.delete(b.id);
    unitById.delete(b.id);
    owned.delete(b.id);
  }
  try {
    fx(enc.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1.5, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 3.5, life: 0.6, color: u.def.color, alpha: 0.95 });
    fx(enc.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 4, life: 0.8, color: u.def.color, alpha: 0.9 });
  } catch {
    // 무시
  }
  play(enc.dim, u.def.sounds.death, at, 0.7, 1);
  say(enc, `§6[보스] §e${lastHitter?.name ?? "누군가"}§f님이 §6${u.def.name}§f을(를) 쓰러뜨렸습니다!`);
  try {
    enc.mod.unitDied?.(enc, u);
  } catch (e) {
    console.warn(`[rpg] boss unit died: ${e}`);
  }
  try {
    if (b?.isValid) b.kill();
  } catch {
    try {
      if (b?.isValid) b.remove();
    } catch {
      // 무시
    }
  }
}

function bossDied(enc, lastHitter, lastUnit) {
  if (enc.state === "dead") return;
  enc.state = "dead";
  const b = (lastUnit ?? enc.units[0]).boss;
  // 모듈이 쓰러지는 자리를 정리 (v2.3.0 니드호그: 날다가 쓰러지면 땅에 내려놓음 → 쓰러지는 연출도 땅에서)
  try {
    enc.mod.dying?.(enc);
  } catch (e) {
    console.warn(`[rpg] boss dying: ${e}`);
  }
  try {
    celebrate(enc, lastHitter, b?.isValid ? { ...b.location } : enc.center);
  } catch (e) {
    console.warn(`[rpg] boss reward: ${e}`);
  } finally {
    // 보상 중에 오류가 나도 싸움은 반드시 정리 (보스가 무적으로 남지 않게)
    cleanup(enc);
    try {
      if (b?.isValid) b.kill();
    } catch {
      try {
        if (b?.isValid) b.remove();
      } catch {
        // 무시
      }
    }
  }
}

/**
 * 보스 처치 훅 (v2.6.0 메인 퀘스트의 보스 처치): fn(보스 키, 보상을 받은 플레이어들)
 * @type {((key: string, players: any[]) => void)[]}
 */
const killHooks = [];
export function addBossKillHook(fn) {
  if (fn) killHooks.push(fn);
}
/** 처치 훅을 부름 (celebrate, 테스트) */
export function notifyBossKilled(key, players) {
  for (const h of killHooks) {
    try {
      h(key, players);
    } catch (e) {
      console.warn(`[rpg] boss kill hook: ${e}`);
    }
  }
}

/** 세 자리마다 쉼표 (12,345) */
export function commas(n) {
  const s = String(Math.max(0, Math.round(n)));
  return s.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

/**
 * v2.12.0 초월 보스: 준 피해 순위 줄 (싸움 기록 parts 의 dmg = 보스와 맞는 소품(부위의 핵)에 들어간 피해의 합, 많은 순서로 8명까지)
 * @param {any} enc
 * @returns {string[]}
 */
export function damageBoard(enc) {
  const list = [...enc.parts.values()].filter((x) => x.dmg > 0).sort((a, b) => b.dmg - a.dmg);
  const total = list.reduce((n, x) => n + x.dmg, 0);
  if (!(total > 0)) return [];
  const lines = [`§6[딜 순위] §f${enc.def.name} §7— 모두 §f${commas(total)}`];
  list.slice(0, 8).forEach((x, i) => {
    let name = "?";
    try {
      name = x.p?.name ?? "?";
    } catch {
      name = "?";
    }
    lines.push(`§e${i + 1}. §f${name} §6${commas(x.dmg)} §7(${Math.round((x.dmg / total) * 100)}%)`);
  });
  return lines;
}

/** 처치: 보상·재사용 대기·알림·연출 (at = 마지막으로 쓰러진 자리) */
function celebrate(enc, lastHitter, at) {
  const now = system.currentTick;
  // 보상 받을 사람: 지금 필드 안이거나 participantGrace 안에 때린 사람 (크리에이티브·관전은 빼고, 방금 쓰러진 사람은 받음)
  const who = new Map();
  for (const p of playersIn(enc.a, 2)) if (canReward(p)) who.set(p.id, p);
  for (const [id, part] of enc.parts) if (now - part.last <= BOSS.participantGrace && canReward(part.p)) who.set(id, part.p);
  const names = [...who.values()].map((p) => p.name);
  for (const p of who.values()) {
    const got = reward(enc, p);
    try {
      p.sendMessage(`§6[보스] §f${enc.def.name} 처치! §7보상: §f${got.join("§7, §f") || "없음"}`);
    } catch {
      // 무시
    }
  }
  // v2.12.0 초월 보스: 준 피해 순위 + 모듈이 남기는 줄 (수르트: 부위 파괴) — 보상을 받은 사람과 필드 둘레의 사람에게
  if (enc.def.tier === "transcend") {
    let notes = [];
    try {
      notes = enc.mod.killNote?.(enc) ?? [];
    } catch (e) {
      console.warn(`[rpg] boss kill note: ${e}`);
    }
    const lines = [...damageBoard(enc), ...notes];
    const to = new Map(audience(enc).map((p) => [p.id, p]));
    for (const p of who.values()) to.set(p.id, p);
    for (const line of lines) say(enc, line, [...to.values()]);
  }
  // 퀘스트 (v2.6.0): 보상을 받은 사람 모두 처치로 셈
  notifyBossKilled(enc.key, [...who.values()]);
  try {
    world.sendMessage(`§6[보스] §e${names.join(", ") || lastHitter?.name || "누군가"}§f님이 §6${enc.def.name}§f을(를) 쓰러뜨렸습니다!`);
  } catch {
    // 무시
  }
  enc.a.cooldownUntil = Date.now() + (enc.def.cooldown ?? 600) * 1000;
  saveAltars();
  // 연출: 큰 폭발 + 소리, 보스는 바닐라 죽음
  try {
    for (let i = 0; i < 3; i++) fx(enc.dim, "rpg:bash_burst", { x: at.x, y: at.y + 1 + i * 1.5, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: 3 + i, life: 0.6, color: enc.def.color, alpha: 0.95 });
    fx(enc.dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: 5, life: 0.9, color: enc.def.color, alpha: 0.9 });
  } catch {
    // 무시
  }
  play(enc.dim, enc.def.sounds.death, at, 0.9, 1);
  play(enc.dim, "random.totem", at, 1, 0.8);
  title(enc, `§6§l${enc.def.name} 처치!`, "§7보상을 받았습니다");
}

// ------------------------------------------------------------ 싸움 (매 틱)
/**
 * 패턴 틀 시작 (마리마다): steps = [[시작 뒤 틱, fn(u, now)], ...], length = 끝나는 틱 (그동안 그 마리의 다른 패턴·평타 없음)
 * @param {Unit} u
 */
export function cast(u, id, length, steps) {
  const now = system.currentTick;
  u.cast = { id, until: now + length, steps: steps.map(([t, fn]) => ({ at: now + t, fn })).sort((x, y) => x.at - y.at) };
}

/** 보스를 제자리에 (이동 막기) / 풀기: 엔티티 이벤트로 이동 부품을 바꿈. 분노(fast)면 빠른 걸음으로 풂 */
export function hold(u, on) {
  if (!u.boss?.isValid || u.held === on) return;
  u.held = on;
  try {
    u.boss.triggerEvent(on ? "rpg:hold" : u.fast ? "rpg:release_fast" : "rpg:release");
  } catch {
    // 무시
  }
}

const TAU = Math.PI * 2;
/** 각도 차이 a - b (-π ~ π) */
function angDiff(a, b) {
  let d = (a - b) % TAU;
  if (d > Math.PI) d -= TAU;
  if (d < -Math.PI) d += TAU;
  return d;
}

/**
 * 몸 방향을 바로 정함 (움직이지 않는 보스): ang = 가로 각도 (라디안, +x 에서 +z 쪽으로). 제 자리(home + 높이)에서 그쪽을 봄 (눈높이 그대로: 고개를 숙이지 않게).
 * 늘 제 자리로 옮기므로 스킬 넉백 등으로 조금 밀려도 돌아옴
 */
function setFacing(u, ang) {
  u.faceNow = ang;
  const b = u.boss;
  if (!b?.isValid) return;
  try {
    const l = { x: u.home.x, y: u.home.y + liftOf(u), z: u.home.z };
    let eye = l.y + bossSize(u.def).height * 0.6;
    try {
      eye = b.getHeadLocation().y;
    } catch {
      // 무시
    }
    b.teleport({ x: l.x, y: l.y, z: l.z }, { facingLocation: { x: l.x + Math.cos(ang) * 8, y: eye, z: l.z + Math.sin(ang) * 8 } });
  } catch {
    // 무시
  }
}

/**
 * 몸 돌리기 (움직이지 않는 보스): dir = 가로 방향 {x, z} (길이 상관없음). 매 틱 stationary.turn 도씩 그쪽으로 돌아감
 * @param {Unit} u
 * @param {{x: number, z: number}} dir
 */
export function face(u, dir) {
  if (!u.def.stationary || Math.hypot(dir.x, dir.z) < 1e-6) return;
  u.faceTo = Math.atan2(dir.z, dir.x);
}

/**
 * 제 자리 옮기기 (v2.1.0 거대 웜의 굴 이동): home 을 at(바닥 높이)으로 바꾸고 바로 그 자리로 (ang = 바라볼 가로 각도, 없으면 지금 방향)
 * @param {Unit} u
 * @param {{x: number, y: number, z: number}} at
 * @param {number} [ang]
 */
export function seat(u, at, ang) {
  u.home = { x: at.x, y: at.y, z: at.z };
  const a = ang ?? u.faceNow ?? 0;
  setFacing(u, a);
  u.faceTo = a;
}

/**
 * 자리와 방향을 바로 정함 (v2.2.0 헤엄치는 보스: 모듈이 매 틱 옮김). seat 과 달리 바라보려는 쪽(faceTo)은 그대로 둠
 * @param {Unit} u
 * @param {{x: number, y: number, z: number}} at
 * @param {number} ang
 */
export function pose(u, at, ang) {
  u.home = { x: at.x, y: at.y, z: at.z };
  setFacing(u, ang);
}

/** 아직 더 돌아야 하는 각도 (라디안, 0 = 다 돌았음) */
export function turnLeft(u) {
  if (u.faceTo === undefined || u.faceNow === undefined) return 0;
  return Math.abs(angDiff(u.faceTo, u.faceNow));
}

/** 매 틱: 원하는 방향 쪽으로 stationary.turn 도까지 */
function turnTick(u) {
  if (u.faceTo === undefined || u.faceNow === undefined) return;
  const d = angDiff(u.faceTo, u.faceNow);
  if (Math.abs(d) < 1e-4) return;
  const step = ((u.def.stationary?.turn ?? 16) * Math.PI) / 180;
  setFacing(u, Math.abs(d) <= step ? u.faceTo : u.faceNow + Math.sign(d) * step);
}

/** 빠른 걸음으로 (짝 잃은 분노): 멈춰 있지 않으면 바로 바꿈 */
export function setFast(u) {
  u.fast = true;
  if (u.held || !u.boss?.isValid) return;
  try {
    u.boss.triggerEvent("rpg:release_fast");
  } catch {
    // 무시
  }
}

/**
 * 동작 틀기 (스크립트 동작). opts = playAnimation 선택 (예: 땅속처럼 다음 동작까지 유지할 때 {controller, stopExpression: "0"})
 * @param {any} u
 * @param {string} name
 * @param {any} [target]
 * @param {import("@minecraft/server").PlayAnimationOptions} [opts]
 */
export function anim(u, name, target, opts) {
  try {
    (target ?? u.boss)?.playAnimation(`animation.rpg.boss.${u.key}.${name}`, opts);
  } catch {
    // 표시만
  }
}

/** 모듈이 대상에서 뺀 사람(삼켜진 사람 등, 모듈 targetable)을 뺌 */
function targetable(enc, list) {
  if (!enc.mod?.targetable) return list;
  try {
    return list.filter((p) => enc.mod.targetable(enc, p));
  } catch (e) {
    console.warn(`[rpg] boss targets: ${e}`);
    return list;
  }
}

/** 평타: 앞 부채꼴 (그 마리에게 가장 가까운 플레이어가 닿으면) */
function melee(u, now, list) {
  const m = u.def.melee;
  if (!m || now < u.meleeAt) return false;
  const b = u.boss;
  const { half } = bossSize(u.def);
  // 닿는 사람 가운데 가장 가까운 사람 — v2.20.0 도발 (아이기스의 방패) 한 사람이 닿으면 그 사람
  const tgt = meleeTarget(u, list.filter((p) => hdist(p.location, b.location) - half - 0.3 <= m.reach), b.location);
  if (!tgt) return false;
  const dx = tgt.location.x - b.location.x;
  const dz = tgt.location.z - b.location.z;
  const L = Math.hypot(dx, dz) || 1;
  const dir = { x: dx / L, y: 0, z: dz / L };
  const apex = { x: b.location.x, y: b.location.y, z: b.location.z };
  const radius = half + m.reach;
  fanMarks(u.dim, apex, dir, Math.round(radius), m.cone, m.windup, [1.0, 0.25, 0.15]);
  face(u, dir); // 움직이지 않는 보스: 대상 쪽으로 몸을 돌림 (걷는 보스는 스스로 봄)
  anim(u, m.anim ?? "slash");
  sound(u, u.def.sounds.swing, b.location, 0.8, 1);
  u.meleeAt = now + Math.round(m.every * (u.cdMult ?? 1));
  hold(u, true);
  cast(u, "melee", m.windup + 6, [
    [
      m.windup,
      (e) => {
        for (const p of targetable(e.enc, fighters(e))) {
          if (!inFan(p.location, apex, dir, radius, m.cone, 0.5)) continue;
          bossHit(e, p, m.damage, { knock: { x: dir.x * 1.2, z: dir.z * 1.2, h: 0.35 }, directional: true });
        }
        sound(e, e.def.sounds.hit, apex, 0.9, 1);
        fx(e.dim, "rpg:slash_heavy_r_glow", { x: apex.x + dir.x * radius * 0.5, y: apex.y + 1.2, z: apex.z + dir.z * radius * 0.5 }, { dir, speed: 0, spin: 0, size: radius * 0.6, life: 0.25, color: e.def.color, alpha: 0.95 });
      },
    ],
    [m.windup + 5, (e) => hold(e, false)],
  ]);
  return true;
}

/** 제 자리(home)에서 너무 멀어졌으면 (밀려남·끼임) 제 자리로. 움직이지 않는 보스는 조금만 밀려도 조용히 제 자리로 (방향은 그대로) */
function leash(u) {
  const b = u.boss;
  const h = u.home;
  if (u.def.stationary) {
    const l = b.location;
    if (Math.hypot(l.x - h.x, l.z - h.z) > 0.25 || Math.abs(l.y - (h.y + liftOf(u))) > 0.25) setFacing(u, u.faceNow ?? 0);
    return;
  }
  if (hdist(b.location, h) <= u.leash && Math.abs(b.location.y - h.y) < 6) return;
  try {
    b.teleport({ x: h.x, y: h.y + liftOf(u), z: h.z });
    fx(u.dim, "rpg:bash_burst", { x: h.x, y: h.y + 2, z: h.z }, { dir: { x: 0, y: 0, z: 1 }, size: 2.5, life: 0.4, color: u.def.color, alpha: 0.8 });
  } catch {
    // 무시
  }
}

function fightTick(enc, now) {
  for (const u of enc.units) {
    if (u.alive && !u.boss?.isValid) {
      endEncounter(enc, "lost");
      return;
    }
  }
  const list = fighters(enc);
  if (list.length === 0) {
    if (enc.emptySince === undefined) enc.emptySince = now;
    if (now - enc.emptySince > BOSS.resetAfter) {
      endEncounter(enc, "empty");
      return;
    }
  } else enc.emptySince = undefined;
  if (now % 10 === 0) for (const u of enc.units) if (u.alive) leash(u);
  // 헤엄치는 보스(swim)·걷고 나는 보스(drive)는 모듈이 몸을 돌림
  for (const u of enc.units) if (u.alive && u.def.stationary && !u.def.swim && !u.def.drive) turnTick(u);
  try {
    enc.mod.tick?.(enc, now, list);
  } catch (e) {
    console.warn(`[rpg] boss tick: ${e}`);
  }
  if (!live(enc)) return;
  // 평타·패턴 고르기의 대상 (삼켜진 사람 등은 모듈이 뺌)
  const targets = targetable(enc, list);
  // 원거리 견제 (v2.3.0): 멀리 떨어진 사람에게 던짐 (틀 없이), 날아가는 것이 떨어짐
  try {
    farTrack(enc, now, targets);
  } catch (e) {
    console.warn(`[rpg] boss far track: ${e}`);
  }
  for (const u of enc.units) {
    if (!u.alive || !u.def.far) continue;
    try {
      farTick(enc, u, now, targets);
    } catch (e) {
      console.warn(`[rpg] boss far: ${e}`);
    }
  }
  try {
    farShotTick(enc, now);
  } catch (e) {
    console.warn(`[rpg] boss far shot: ${e}`);
  }
  if (!live(enc)) return;
  for (const u of enc.units) {
    if (!u.alive) continue;
    unitTick(enc, u, now, targets);
    if (!live(enc)) return;
  }
}

// ------------------------------------------------------------ 원거리 견제 (v2.3.0)
/**
 * 원거리 견제 모양: 날아가는 파티클(shot)·색(color)·크기(size)·발밑 원 색(disc)·떨어질 때(hit: splash 물보라 | dust 흙먼지 | fire 불 | spike 가시, 색 mark)·던질 때·떨어질 때 소리
 * @type {Record<string, {shot: string, color: number[], size: number, disc: number[], hit: string, mark: number[], sound: string, land: string}>}
 */
export const FAR_LOOKS = {
  fire: { shot: "rpg:glob", color: [1.0, 0.55, 0.15], size: 1.0, disc: [1.0, 0.5, 0.14], hit: "fire", mark: [1.0, 0.5, 0.14], sound: "mob.blaze.shoot", land: "random.explode" },
  rock: { shot: "rpg:boulder", color: [1, 1, 1], size: 0.9, disc: [0.66, 0.52, 0.38], hit: "dust", mark: [0.56, 0.48, 0.4], sound: "mob.irongolem.throw", land: "dig.stone" },
  ink: { shot: "rpg:glob", color: [0.14, 0.1, 0.22], size: 1.1, disc: [0.62, 0.3, 0.96], hit: "splash", mark: [0.2, 0.12, 0.3], sound: "mob.squid.ambient", land: "random.splash" },
  venom: { shot: "rpg:glob", color: [0.55, 0.95, 0.25], size: 1.0, disc: [0.55, 0.95, 0.25], hit: "splash", mark: [0.55, 0.95, 0.25], sound: "mob.llama.spit", land: "random.fizz" },
  spike: { shot: "rpg:glob", color: [0.74, 0.3, 0.96], size: 0.8, disc: [0.74, 0.3, 0.96], hit: "spike", mark: [0.74, 0.3, 0.96], sound: "mob.enderdragon.flap", land: "random.fizz" },
  // v2.4.0 스콜과 하티의 밤: 달 서리 (눈밭에서도 보이게 원은 짙은 파랑)
  frost: { shot: "rpg:glob", color: [0.78, 0.93, 1.0], size: 1.0, disc: [0.3, 0.55, 1.0], hit: "splash", mark: [0.75, 0.92, 1.0], sound: "mob.snowgolem.shoot", land: "random.glass" },
  // v2.5.0 이미르: 얼음 창 (날아가는 쪽으로 길쭉한 창, 떨어진 자리에 얼음 가시가 솟음)
  ice: { shot: "rpg:ice_lance", color: [0.72, 0.93, 1.0], size: 1.6, disc: [0.2, 0.5, 1.0], hit: "ice", mark: [0.75, 0.93, 1.0], sound: "mob.irongolem.throw", land: "random.glass" },
};

/** 큰 패턴(모듈 farOk 가 false)이 끝난 뒤 원거리 견제를 쉬는 틱 (흩어지라는·밀어내는 패턴 직후에 바로 던지지 않게) */
export const FAR_GRACE = 80;
/** 이만큼(틱) 계속 멀리 있어야 노림 (보스에게 밀려나거나 잠깐 물러선 사람은 아님) */
export const FAR_SETTLE = 40;

/** 살아 있는 마리 중 가장 가까운 몸 겉면(가로: 가운데 − 반폭)까지의 거리 (여러 마리 싸움: 다른 마리와 붙어 싸우는 사람은 멀지 않음) */
function farDist(enc, p) {
  let d = Infinity;
  for (const x of enc.units) {
    if (!x.alive || !x.boss?.isValid) continue;
    d = Math.min(d, hdist(p.location, x.boss.location) - bossSize(x.def).half);
  }
  return d;
}

/**
 * 원거리 견제 대상: 몸 겉면에서 far.from 칸 넘게 떨어진 사람, 가장 먼 사람부터 count 명. 속박된 사람(움직일 수 없음)은 노리지 않음.
 * ok(p) = 더 거를 것 (farTick: FAR_SETTLE 틱 넘게 멀리 있던 사람만)
 * @param {any} u
 * @param {any[]} list
 * @param {(p: any) => boolean} [ok]
 */
export function farTargets(u, list, ok = () => true) {
  const F = u.def.far;
  return list
    .filter((p) => !isRooted(p) && ok(p))
    .map((p) => ({ p, d: farDist(u.enc, p) }))
    .filter((x) => x.d > F.from && x.d < Infinity)
    .sort((a, b) => b.d - a.d)
    .slice(0, F.count ?? 2)
    .map((x) => x.p);
}

/** 매 틱: 모듈이 쉬게 하는지(쉰 마지막 틱) · 사람마다 멀리 있기 시작한 틱 */
function farTrack(enc, now, list) {
  const units = enc.units.filter((u) => u.alive && u.def.far && u.boss?.isValid);
  if (!units.length) return;
  for (const u of units) {
    let ok = !u.immune && now >= u.staggerUntil;
    if (ok && enc.mod.farOk) {
      try {
        ok = enc.mod.farOk(u, now) !== false;
      } catch (e) {
        console.warn(`[rpg] boss far ok: ${e}`);
        ok = false;
      }
    }
    if (!ok) u.farPausedAt = now;
  }
  const from = Math.min(...units.map((u) => u.def.far.from));
  const seen = new Set();
  for (const p of list) {
    if (farDist(enc, p) <= from) continue;
    seen.add(p.id);
    if (!enc.farSince.has(p.id)) enc.farSince.set(p.id, now);
  }
  for (const id of [...enc.farSince.keys()]) if (!seen.has(id)) enc.farSince.delete(id);
}

/** 마리마다: 때가 되면 멀리 떨어진 사람 발밑에 원 + 던짐 (떨어지는 것은 farShotTick). 쉬는 중·쉰 직후·대상이 없으면 1초 뒤 다시 봄 */
function farTick(enc, u, now, list) {
  const F = u.def.far;
  if (!u.boss?.isValid) return;
  if (u.farAt === undefined) u.farAt = now + (F.first ?? 200);
  if (now < u.farAt) return;
  const rested = now - (u.farPausedAt ?? -Infinity) >= FAR_GRACE;
  const far = rested ? farTargets(u, list, (p) => now - (enc.farSince.get(p.id) ?? now) >= FAR_SETTLE) : [];
  if (!far.length) {
    u.farAt = now + 20;
    return;
  }
  const [lo, hi] = F.every;
  u.farAt = now + Math.round((lo + Math.floor(Math.random() * (hi - lo + 1))) * (u.cdMult ?? 1));
  const S = farStyleOf(enc, u);
  const L = FAR_LOOKS[S.look] ?? FAR_LOOKS.rock;
  let from;
  try {
    from = enc.mod.farFrom?.(u, S);
  } catch {
    from = undefined;
  }
  const at = u.boss.location;
  from ??= { x: at.x, y: at.y + bossSize(u.def).height * 0.75, z: at.z };
  const t = S.windup / 20;
  for (const p of far) {
    // 떨어지는 자리 = 서 있는 바닥 (뛰는 중이면 발밑 땅)
    const spot = { x: p.location.x, y: groundUnder(p, u.center.y), z: p.location.z };
    disc(u.dim, spot, S.radius, S.windup, L.disc);
    const arc = lobArc(from, { x: spot.x, y: spot.y + 0.3, z: spot.z }, t);
    fx(u.dim, L.shot, from, { dir: arc.dir, speed: arc.speed, size: L.size, life: t, spin: Math.random() * 360, color: L.color, alpha: 1 });
    showFaceText(p, `§e§l${S.name}! §f발밑 원 밖으로`, { dist: 2.4, up: 0.45 });
    enc.farShots.push({ u, spot, hitAt: now + S.windup, style: S });
  }
  sound(u, L.sound, from, 0.9, 1);
  if (!enc.farTold) {
    enc.farTold = true;
    say(enc, `§e[보스] ${S.name}: §f${u.def.name}에게서 ${F.from}칸 넘게 떨어져 있으면 가장 먼 사람부터 발밑에 원이 뜨고 ${S.name}이(가) 날아옵니다 — 원 밖으로${BOSS.close.bonus > 0 ? ` §7(가까이 붙어 싸우면 준 피해 +${Math.round(BOSS.close.bonus * 100)}%)` : ""}`);
  }
}

/**
 * 이번 원거리 견제의 모양 (v2.4.0): 마리 설정 far 에 모듈 farStyle(u) 가 돌려준 것 (이름·모양·효과·불 등)을 덮어씀 (스콜과 하티: 낮 = 태양 불씨, 밤 = 달 서리).
 * 던질 때 정해 날아가는 것에 붙임 (떨어지기 전에 낮밤이 바뀌어도 던진 것 그대로)
 */
function farStyleOf(enc, u) {
  const F = u.def.far;
  let over;
  try {
    over = enc.mod.farStyle?.(u);
  } catch (e) {
    console.warn(`[rpg] boss far style: ${e}`);
    over = undefined;
  }
  return over ? { ...F, ...over } : F;
}

/** 날아간 것이 떨어짐: 원 안(반지름 + 0.3칸, 위아래 3칸)의 사람에게 피해 + 효과 (때린 엔티티 없이: 게임이 보스 쪽에서 밀어내지 않음) */
function farShotTick(enc, now) {
  if (!enc.farShots.length) return;
  const due = enc.farShots.filter((s) => now >= s.hitAt);
  if (!due.length) return;
  enc.farShots = enc.farShots.filter((s) => now < s.hitAt);
  const list = targetable(enc, fighters(enc));
  for (const s of due) {
    const u = s.u;
    const F = s.style ?? u.def.far;
    const L = FAR_LOOKS[F.look] ?? FAR_LOOKS.rock;
    farImpact(enc.dim, L, s.spot, F.radius);
    play(enc.dim, L.land, s.spot, 0.9, 1);
    if (!u.alive || !u.boss?.isValid) continue;
    for (const p of list) {
      // 원이 뜬 뒤 속박된 사람(감옥·뿌리 등으로 비킬 수 없게 된 사람)은 맞지 않음
      if (hdist(p.location, s.spot) > F.radius + 0.3 || Math.abs(p.location.y - s.spot.y) > 3 || isRooted(p)) continue;
      const landed = bossHit(u, p, F.damage, { noSource: true, cause: EntityDamageCause.projectile, effect: F.effect });
      if (landed && F.burn) {
        try {
          p.setOnFire(F.burn, true);
        } catch {
          // 무시
        }
      }
    }
  }
}

/** 떨어진 자리 연출 */
function farImpact(dim, L, at, radius) {
  const c = L.mark;
  if (L.hit === "splash") {
    fx(dim, "rpg:splash", { x: at.x, y: at.y + radius * 0.75, z: at.z }, { size: radius, life: 0.6, color: c, alpha: 0.95 });
  } else if (L.hit === "fire") {
    fx(dim, "rpg:bash_burst", { x: at.x, y: at.y + 1, z: at.z }, { dir: { x: 0, y: 0, z: 1 }, size: radius * 1.2, life: 0.45, color: c, alpha: 0.95 });
    fx(dim, "rpg:shock_ring", { x: at.x, y: at.y + 0.1, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, size: radius, life: 0.5, color: c, alpha: 0.9 });
  } else if (L.hit === "ice") {
    fx(dim, "rpg:ice_spike", { x: at.x, y: at.y, z: at.z }, { size: radius * 0.95, life: 1.2, spin: 0, color: c, alpha: 1 });
    fx(dim, "rpg:splash", { x: at.x, y: at.y + radius * 0.6, z: at.z }, { size: radius * 0.9, life: 0.5, color: c, alpha: 0.9 });
    fx(dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2, life: 1.0, color: [0.35, 0.45, 0.62], alpha: 0.85 });
  } else if (L.hit === "spike") {
    fx(dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2.2, life: 1.0, color: c, alpha: 0.9 });
    fx(dim, "rpg:splash", { x: at.x, y: at.y + radius * 0.6, z: at.z }, { size: radius * 0.8, life: 0.5, color: c, alpha: 0.9 });
  } else {
    fx(dim, "rpg:ground_crack", { x: at.x, y: at.y + 0.05, z: at.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: radius * 2.2, life: 1.0, color: [0.36, 0.28, 0.22], alpha: 0.85 });
  }
  fx(dim, "rpg:dust", { x: at.x, y: at.y + radius * 0.35, z: at.z }, { size: radius, life: 0.7, color: c, alpha: 0.8 });
}

/** 한 마리의 틱: 진행 중인 틀의 단계, 없으면 패턴 고르기 → 평타 */
function unitTick(enc, u, now, list) {
  if (u.cast) {
    const c = u.cast;
    while (c.steps.length && c.steps[0].at <= now) {
      const st = c.steps.shift();
      try {
        st?.fn(u, now);
      } catch (e) {
        console.warn(`[rpg] boss step ${c.id}: ${e}`);
      }
      if (!live(enc) || !u.alive) return;
      // 단계가 틀을 끊거나 새 틀로 바꿨으면 (속박·반격 등) 옛 틀의 남은 단계는 하지 않음
      if (u.cast !== c) return;
    }
    if (!c.steps.length && now >= c.until && u.cast === c) u.cast = undefined;
    return;
  }
  if (now < u.staggerUntil || list.length === 0) return;
  let started = false;
  try {
    started = !!enc.mod.pick?.(u, now, list);
  } catch (e) {
    console.warn(`[rpg] boss pick: ${e}`);
  }
  if (!started) melee(u, now, list);
}

function tick() {
  const now = system.currentTick;
  processPending();
  for (const enc of [...encs.values()]) {
    if (enc.state === "summon") {
      summonFx(enc, now);
      if (now >= enc.spawnAt) spawnBoss(enc);
      continue;
    }
    if (enc.state === "fight") fightTick(enc, now);
  }
  cleanOwn(now);
}

function summonFx(enc, now) {
  const k = now - enc.t0;
  if (k % 5 !== 0) return;
  const c = enc.center;
  const r = 1 + (k / BOSS.summonDelay) * 3;
  fx(enc.dim, "rpg:shock_ring", { x: c.x, y: c.y + 0.1, z: c.z }, { dir: { x: 0, y: 0, z: 0 }, size: r, life: 0.5, color: enc.def.color, alpha: 0.85 });
  fx(enc.dim, "rpg:skill_rune", { x: c.x, y: c.y + 0.12, z: c.z }, { size: 2.5, life: 0.4, spin: k * 9, speed: 60, grow: 0, progress: Math.min(1, k / BOSS.summonDelay), color: enc.def.color, alpha: 0.9 });
  // 여러 마리: 나올 자리마다 모이는 고리
  for (const u of enc.units) {
    if (u === enc) continue;
    fx(enc.dim, "rpg:shock_ring", { x: u.home.x, y: u.home.y + 0.1, z: u.home.z }, { dir: { x: 0, y: 0, z: 0 }, size: Math.max(0.5, 3.5 - (k / BOSS.summonDelay) * 3), life: 0.5, color: u.def.color, alpha: 0.85 });
  }
  if (k % 20 === 0) play(enc.dim, "mob.warden.heartbeat", c, 0.8, 1);
}

// ------------------------------------------------------------ 제단 터치
function fmtTime(sec) {
  const s = Math.max(0, Math.ceil(sec));
  return `${Math.floor(s / 60)}분 ${String(s % 60).padStart(2, "0")}초`;
}

/**
 * 제단 터치 (블록 사용자 부품 rpg:boss_altar). 웅크리고(운영자·크리에이티브) = 관리 창
 * @param {any} player
 * @param {any} block
 */
export async function onAltarTouch(player, block) {
  const key = altarKeyOf(block?.typeId);
  if (!key || !player?.isValid) return;
  const dimId = block.dimension.id;
  const a = altarAt(dimId, block.location) ?? addAltar(key, dimId, block.location);
  const def = BOSSES[key];
  if (player.isSneaking && canManage(player)) {
    await manageMenu(player, a);
    return;
  }
  const enc = encs.get(a);
  if (enc) {
    const pct = (u) => (enc.state === "fight" ? Math.round(ratio(u) * 100) : 100);
    const hp = enc.units.length > 1 ? enc.units.map((u) => `${u.def.short ?? u.def.name} ${pct(u)}%`).join(" · ") : `${pct(enc)}%`;
    notify(player, `§6${def.name} §7전투 중 §8· §f체력 ${hp} §8· §f필드 안 ${fighters(enc).length}명`, 60);
    return;
  }
  if (isBuilding(a)) {
    notify(player, "§7보스 필드를 짓는 중입니다", 40);
    return;
  }
  if (!a.built) {
    notify(player, canManage(player) ? "§7보스 필드가 없습니다 §8· §f웅크리고 터치해 필드를 지으세요" : "§7보스 필드가 없어 도전할 수 없습니다 §8· §f운영자가 필드를 지어야 합니다", 60);
    return;
  }
  const left = ((a.cooldownUntil ?? 0) - Date.now()) / 1000;
  if (left > 0) {
    notify(player, `§7${def.name}이(가) 다시 나타나기까지 §f${fmtTime(left)}`, 60);
    return;
  }
  // 보스 재료 (v2.0.0): 이 보스가 주는 것만
  const mats = BOSS_MATERIAL_ORDER.filter((k) => def.loot?.[k]).map((k) => `${BOSS_MATERIALS[k].color}${BOSS_MATERIALS[k].name}§7`);
  // v2.3.0 근접 보너스·원거리 견제 안내
  const far = def.far ?? def.units?.find((u) => u.far)?.far;
  // 규칙 줄: 근접 보너스(설정에서 0 이면 뺌) · 원거리 견제
  const rules = [];
  if (BOSS.close.bonus > 0) rules.push(`§c근접 보너스: §f보스 몸 ${BOSS.close.range}칸 안에서 준 피해 +${Math.round(BOSS.close.bonus * 100)}%`);
  // v2.4.0: 밤에 다른 것을 던지는 보스 (스콜과 하티: far.night)
  if (far) rules.push(`§e원거리 견제: §f${far.from}칸 넘게 떨어지면 ${far.name}${far.night ? ` §7(밤: ${far.night.name})` : ""}`);
  const rule = rules.length ? `\n${rules.join(" §8· ")}` : "";
  // v2.12.0 초월 보스: 머리말 + 깨는 법 (설정 notes 한 줄씩), 보스 재료 줄은 "초월 장비 재료"
  const transcend = def.tier === "transcend";
  const notes = transcend && def.notes?.length ? `\n\n§6§l패턴을 깨는 법§r\n${def.notes.join("\n")}` : "";
  const matNote = mats.length ? `\n§7보스 재료: ${mats.join(" · ")} §8(${transcend ? "초월 장비 재료" : "대장장이 보스 무기 제작"})` : "";
  const sel = await new MenuForm(
    `§l${def.name}에게 도전`,
    `${transcend ? "§c§l[초월 보스]§r " : ""}§f${def.title ?? ""} §7— §fLv.${def.level}${def.units ? `\n§7함께 나옴: §f${def.units.map((u) => u.name).join(" §7· §f")} §7(모두 쓰러뜨려야 보상)` : ""}\n§7권장: ${transcend ? "초월 장비를 갖춘 4명 파티" : "3~4명 파티 (혼자서는 사실상 어려움)"}${rule}${notes}\n\n§7보상: 에메랄드 · 경험치 · 룬 뽑기권${transcend ? " · 고급 룬 뽑기권" : ""} · 강화 재료${matNote}\n§7잡힌 뒤 ${Math.round((def.cooldown ?? 600) / 60)}분 동안 다시 나오지 않습니다`,
  )
    .button("§4도전")
    .button("취소")
    .show(player);
  if (sel !== 0 || encs.has(a) || !altars().includes(a) || !a.built) return;
  if (((a.cooldownUntil ?? 0) - Date.now()) / 1000 > 0) return;
  summon(a, player);
}

async function manageMenu(player, a) {
  const def = BOSSES[a.key];
  const enc = encs.get(a);
  const sel = await new MenuForm(`§l${def.name} 제단 관리`, `§7필드: ${a.built ? "§a지어짐" : "§c없음"} §8· §7재사용 대기: §f${Math.max(0, Math.ceil(((a.cooldownUntil ?? 0) - Date.now()) / 1000))}초${enc ? " §8· §c전투 중" : ""}`)
    .button(a.built ? "필드 다시 짓기" : "필드 짓기")
    .button("재사용 대기 없애기")
    .button("보스 초기화 (돌려보내기)")
    .button("닫기")
    .show(player);
  if (sel === 0) await askBuild(player, a);
  else if (sel === 1) {
    a.cooldownUntil = 0;
    saveAltars();
    notify(player, "§a재사용 대기를 없앴습니다", 40);
  } else if (sel === 2 && encs.get(a)) endEncounter(/** @type {Enc} */ (encs.get(a)), "manual");
}

/** 제단이 없어지면 싸움도 정리 */
function onAltarRemoved(a) {
  const enc = encs.get(a);
  if (enc) endEncounter(enc, "removed");
}

/** 남아 있는 보스·소품 엔티티 치우기 (월드를 다시 불러오면 싸움 기록이 없으므로) */
export function clearStrays() {
  const ids = strayTypes();
  for (const d of ["overworld", "nether", "the_end"]) {
    let dim;
    try {
      dim = world.getDimension(d);
    } catch {
      continue;
    }
    for (const id of ids) {
      let list = [];
      try {
        list = dim.getEntities({ type: id });
      } catch {
        list = [];
      }
      for (const e of list) {
        if (owned.has(e.id)) continue;
        try {
          e.remove();
        } catch {
          // 무시
        }
      }
    }
  }
}

/** 보스·소품 엔티티 종류 */
function strayTypes() {
  return new Set([...BOSS_IDS.keys(), ...ALL_BOSS_ORDER.flatMap((k) => MODS[k]?.propIds ?? [])]);
}

export function registerBosses() {
  setSkyBlocker(hasBossFog); // v2.27.0 보스 안개 아래에서는 초월 룬 하늘빛을 씌우지 않음
  addHurtHooks({ block, absorb, absorbed });
  // v2.19.0 섬광 난무: 필드 밖 (울타리 너머) 에 선 사람은 그 보스 · 소품 옆으로 순간이동하지 않음 (필드 밖에서는 보스에게 피해를 줄 수 없는 규칙을 넘지 않게)
  setBossFieldCheck((p, e, at) => {
    const enc = byEntity.get(e?.id);
    if (!enc) return true;
    try {
      return inArena(enc.a, p.dimension.id, at, BOSS.combatMargin);
    } catch {
      return false;
    }
  });
  // v2.20.0 아이기스의 방패 도발: ticks 동안 그 보스 (마리) 의 평타가 도발한 사람을 먼저 노림 (닿는 거리 안이면 — 패턴은 그대로). 소품은 아님
  addTauntHook((p, e, ticks) => {
    const u = unitById.get(e?.id);
    if (!u?.alive) return false;
    u.taunt = { id: p.id, until: system.currentTick + ticks };
    return true;
  });
  setAltarRemovedHook(onAltarRemoved);
  // 보스·분신은 피해 숫자를 가슴 높이에서 (머리는 너무 높음)
  setDamageTextAnchor((victim, attacker) => {
    const enc = byEntity.get(victim?.id);
    if (!enc) return undefined;
    // 맞는 소품 (촉수): 소품마다 정한 높이
    const pi = propInfo.get(victim.id);
    if (pi?.textHeight) return { x: victim.location.x, y: victim.location.y + pi.textHeight, z: victim.location.z };
    const def = unitById.get(victim.id)?.def ?? enc.def;
    if (!def.box) return undefined;
    const l = victim.location;
    // v2.12.0 큰 보스 (textNear, 수르트 폭 10칸): 때린 사람 쪽 몸 겉면, 그 사람 눈높이 (몸 높이 안) — 가운데에 띄우면 몸에 가려 멀리 보임
    const near = def.textNear ? textNearAt(l, def, attacker) : undefined;
    if (near) return near;
    return { x: l.x, y: l.y + (def.textHeight ?? Math.min(bossSize(def).height * 0.45, 3.2)), z: l.z };
  });
  system.runInterval(tick, 1);
  // 청크를 다시 불러와 나타난 보스·소품 중 싸움이 없는 것 (청크가 내려가 싸움이 끝난 뒤 남은 것) 은 지움
  const types = strayTypes();
  world.afterEvents.entityLoad?.subscribe((ev) => {
    const e = ev.entity;
    let id;
    try {
      if (!types.has(e.typeId)) return;
      id = e.id;
    } catch {
      return;
    }
    system.run(() => {
      try {
        if (e.isValid && !owned.has(id)) e.remove();
      } catch {
        // 무시
      }
    });
  });
  // v2.5.0: 처음 들어온 사람에게 남아 있을 수 있는 보스 안개(이미르의 눈보라)를 걷음 (싸움 중이면 모듈이 다시 씌움)
  world.afterEvents.playerSpawn.subscribe((ev) => {
    if (ev.initialSpawn) clearBossFog([ev.player]);
  });
  // 쓰러진 사람: 다시 때리기 전까지는 즉사 기믹이 따라가지 않게 (다시 살아난 곳이 멀어도)
  world.afterEvents.entityDie.subscribe((ev) => {
    const d = ev.deadEntity;
    if (d?.typeId !== "minecraft:player") return;
    for (const enc of encs.values()) {
      const part = enc.parts.get(d.id);
      if (part) part.diedAt = system.currentTick;
    }
  });
  world.afterEvents.worldLoad.subscribe(() => {
    // v2.12.0: 지난번에 남은 큰 필드의 틱킹 영역 (짓기·싸움 중에 월드를 나감) — 기록은 바로 떼어 내고 1초 뒤 치움 (그 뒤에 만든 것은 그대로)
    try {
      cleanupAreas(20);
    } catch (e) {
      console.warn(`[rpg] boss ticking areas: ${e}`);
    }
    system.runTimeout(() => {
      try {
        clearStrays();
      } catch (e) {
        console.warn(`[rpg] boss strays: ${e}`);
      }
      // v2.5.0: 스크립트를 다시 불러오면 (/reload) 싸움 기록이 없어지므로 이미 들어와 있는 사람의 보스 안개도 걷음
      let list = [];
      try {
        list = world.getAllPlayers();
      } catch {
        list = [];
      }
      clearBossFog(list);
    }, 20);
  });
}

/** v2.27.0 이 사람에게 보스 안개가 씌워져 있는지 (싸움마다 mem.fog — 보스 모듈이 씌운 사람을 기억함). 초월 룬의 하늘빛이 보스 안개를 가리지 않게 (ultfx.js) */
export function hasBossFog(player) {
  for (const enc of encs.values()) {
    try {
      if (enc.mem?.fog?.get(player.id)?.id) return true;
    } catch {
      // 무시
    }
  }
  return false;
}

/** 보스 안개(/fog 이름표 rpg_boss_fog) 걷기 → 싸움 중인 보스 모듈에 알림 (fogCleared: 필드 둘레의 사람이면 모듈이 다시 씌움) */
function clearBossFog(list) {
  for (const p of list) {
    try {
      p.runCommand("fog @s remove rpg_boss_fog");
    } catch {
      continue;
    }
    for (const enc of encs.values()) {
      try {
        enc.mod?.fogCleared?.(enc, p);
      } catch (e) {
        console.warn(`[rpg] boss fog: ${e}`);
      }
    }
  }
}

/** 제단 블록 사용자 부품 (startup 에서 등록) */
export const ALTAR_COMPONENT = {
  onPlayerInteract(ev) {
    const player = ev.player;
    const block = ev.block;
    if (!player) return;
    system.run(() => {
      onAltarTouch(player, block).catch((e) => console.warn(`[rpg] altar: ${e}`));
    });
  },
};

/** 보스 엔티티인지 */
export function isBossEntity(e) {
  return BOSS_IDS.has(e?.typeId);
}

/** 보스 모듈이 쓰는 엔진 함수 (enc.api) */
const API = { cast, hold, anim, setFast, addProp, dropProp, retireProp, own, disown, other, ratio: bossRatio, face, turnLeft, seat, pose };

export { altarId };
