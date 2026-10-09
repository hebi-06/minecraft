import { EntityDamageCause, EquipmentSlot, GameMode, ItemStack, system, world } from "@minecraft/server";
import { BOW, GRADES, LEVEL_DAMAGE, RUNE, RUNE_BODY, RUNE_COMBO, RUNE_ELEMENTS, RUNE_FAMILIES, RUNE_FAMILY_ORDER, RUNE_GRADES, RUNE_GRAVITY, RUNE_QUICK, RUNE_SPELLBLADE, RUNE_SPIRITS, RUNE_STAT_ORDER, RUNE_STRIKE, RUNES, STAT_INFO, ULT_FX, runeId } from "./config.js";
import { addCritBonusHook, addRangedCooldownHook, addWeaponBonusHook, addWeaponHitHook, aimTarget, applySkillDamage, bodyAt, bodyInCone, bodyPad, bodyPoint, bodyToward, clearSteadfast, heldTypeId, isMeleeWeapon, lineOfSight, recentlyTalked, setGuard, setSteadfast, skillLandedAt, standSpot } from "./combat.js";
import { actionbarFree, addHudSuffix, hudSuffix, notify, particle, play, promptedRecently, showDamageText, showFaceText, showTextAt, warn } from "./fx.js";
import { addShield, healBy, hpState, isDying, setShieldHooks, setUndying, shieldOf, showRealTitle } from "./hp.js";
import { canAssist, duelHud, isDuelPartner } from "./duel.js";
import { endSky, ultFlash, ultSky } from "./ultfx.js";
import { giveItem } from "./shop.js";
import {
  add,
  arc,
  around,
  beam,
  bolt,
  burst,
  canHit,
  cutMark,
  every,
  facingOf,
  flat,
  fx,
  len,
  particleVars,
  pickTarget,
  rightOf,
  ring,
  rune as floorRune,
  shake,
  shock,
  sparks,
  sub,
  unit,
  up,
} from "./skills.js";
import { getStatsCached, setStatBuff } from "./stats.js";
import { blockedBySilence } from "./status.js";
import { blockedByMount } from "./riding.js";
import { parseWeapon, setLoreSafe } from "./weapons.js";

/**
 * 룬 스킬 (v1.3.0, v1.4.0 계열 8개 · 40종, v1.6.0 계열 10개 · 50종, v2.13.0 계열 11개 · 55종). 수치·이름은 config.js 의 RUNES · RUNE_FAMILIES · RUNE_GRADES · RUNE
 *
 * - 룬 아이템을 핫바에서 골라 버튼을 누르면 사용 (요구 스탯 없음). 재사용 대기는 룬마다 따로, 핫바 아이콘에도 표시
 * - v1.6.0: 터치(모바일)는 룬 칸을 고르기만 해도 바로 사용 (RUNE_QUICK, /runequick). 쓴 뒤에도 룬을 든 채로
 * - v1.6.0 암살자 룬: 2종 이상 가지고 있으면 3초 안에 다른 암살자 룬을 이어 쓸 때마다 콤보 (RUNE_COMBO), 5종을 모두 이으면 그림자 처형
 * - v2.13.0 마검사 룬: 적용 스탯 = (마력 + 힘 + 민첩) × (1 + 섞음), 검 (공격 피해) 과 마력 (마법 피해, 보라 숫자) 을 섞어 씀,
 *   쓰면 마검 부여 (RUNE_SPELLBLADE): 4초 동안 다음 평타 3번에 위력 × imbue 를 더하고 검기가 뒤의 적을 꿰뚫음
 * - 위력 P = 레벨 기본값 × (1 + 적용 스탯 × 2%), 적용 스탯 = max(계열 계수로 더한 스탯, 가장 큰 스탯 × 0.85)
 *   (도끼 = 힘 × 1.0 + 체력 × 0.5, 나머지 계열 = 그 스탯 × 1.0)
 * - 피해 = P × mult (마력 룬 = 마법 피해, 나머지 = 공격 피해), 피해 숫자는 노란색
 * - 룬 뽑기권: 등급 확률대로 뽑고 스탯은 넷 중 하나. 등급 색 빛기둥 연출 뒤 지급
 *   v2.7.0 고급 룬 뽑기권 (룬술사 runesmith.js): 일반 룬이 나오지 않는 확률표 (RUNE.advWeights)
 */

const V_UP = { x: 0, y: 1, z: 0 };
const V_DOWN = { x: 0, y: -1, z: 0 };
const V_ZERO = { x: 0, y: 0, z: 0 };
const ROCK = [0.55, 0.4, 0.28];
const FIRE = [[1.0, 0.46, 0.12], [1.0, 0.9, 0.55]];
const ICE = [[0.55, 0.86, 1.0], [0.93, 0.98, 1.0]];
const STEEL = [0.78, 0.84, 0.95];
const WHITE = [1, 1, 1];
/** 피의 칼날 베기 속 (밝은 분홍) */
const BLOOD_CORE = [1.0, 0.74, 0.76];
/** 뽑기 연출·아이템 이름 색 (등급) */
export const RUNE_GRADE_FX = {
  common: [0.88, 0.88, 0.94],
  rare: [0.36, 0.6, 1.0],
  noble: [0.72, 0.42, 1.0],
  hero: [1.0, 0.62, 0.16],
  transcend: [1.0, 0.26, 0.26],
};
const GRADE_ORDER = ["common", "rare", "noble", "hero", "transcend"];

/** "플레이어|룬 id" → 다시 쓸 수 있는 틱 @type {Map<string, number>} */
const cooldowns = new Map();
/** 준비 알림을 이미 했는지 @type {Set<string>} */
const readyAnnounced = new Set();
/** 뽑는 중인 플레이어 → 그 뽑기의 룬 ID (연출 동안 다시 못 뽑음, 들어올 때 받는 룬에서 뺌) @type {Map<string, string>} */
const drawing = new Map();
/** 불사의 맹약: 플레이어 → 끝나는 틱 @type {Map<string, number>} */
const undying = new Map();
/** 불사 발동 연출을 너무 자주 하지 않게 @type {Map<string, number>} */
const undyingFxAt = new Map();

// ------------------------------------------------------------ 룬 정보
const RUNE_RE = new RegExp(`^rpg:rune_(${RUNE_FAMILY_ORDER.join("|")})_(common|rare|noble|hero|transcend)$`);

/** 아이템 ID → 룬 정보 (룬이 아니면 undefined). family = 계열, stat = 계열의 대표 스탯 */
export function parseRune(typeId) {
  const m = RUNE_RE.exec(typeId ?? "");
  if (!m) return undefined;
  const family = m[1];
  const grade = m[2];
  const def = RUNES[family]?.[grade];
  const F = RUNE_FAMILIES[family];
  if (!def || !F) return undefined;
  const g = GRADES.find((x) => x.id === grade);
  return { id: typeId, family, stat: F.stat, famName: F.name, grade, def, cd: RUNE_GRADES[grade].cd, gradeName: g?.name ?? grade, gradeColor: g?.color ?? "§f" };
}

/** 최고 경험치 레벨 → 레벨 기본값 (무기 표 LEVEL_DAMAGE 를 레벨 사이는 이어서) */
export function runeLevelBase(level) {
  const keys = Object.keys(LEVEL_DAMAGE)
    .map(Number)
    .sort((a, b) => a - b);
  const lv = Math.max(0, Number(level) || 0);
  if (lv <= keys[0]) return LEVEL_DAMAGE[keys[0]];
  for (let i = 1; i < keys.length; i++) {
    if (lv <= keys[i]) {
      const a = keys[i - 1];
      const b = keys[i];
      return LEVEL_DAMAGE[a] + ((LEVEL_DAMAGE[b] - LEVEL_DAMAGE[a]) * (lv - a)) / (b - a);
    }
  }
  return LEVEL_DAMAGE[keys[keys.length - 1]];
}

/**
 * 적용 스탯: 계열 계수로 더한 값(도끼 = 힘 + 체력 × 0.5)과 가장 큰 스탯 × offStat 중 큰 것.
 * stat·raw·coef = 대표로 보이는 스탯, parts = 표시용 (스탯·값·계수)
 */
export function runeStat(player, family) {
  const s = getStatsCached(player);
  /** @type {{stat: string, coef: Record<string, number>, mix?: number, ratio?: number, best?: boolean}} */
  const F = RUNE_FAMILIES[family] ?? { stat: family, coef: { [family]: 1 } };
  if (F.best) {
    // v2.24.0 신체 강화 · 평타 강화: 네 스탯 중 가장 높은 것 × 1.0 (같으면 RUNE_STAT_ORDER 앞의 것)
    let k0 = RUNE_STAT_ORDER[0];
    for (const k of RUNE_STAT_ORDER) if ((s[k] ?? 0) > (s[k0] ?? 0)) k0 = k;
    const raw = s[k0] ?? 0;
    return { stat: k0, raw, value: raw, coef: 1, parts: [{ stat: k0, raw, coef: 1 }], off: false, mix: 0, hasMix: false, best: true };
  }
  const parts = Object.entries(F.coef).map(([k, c]) => ({ stat: k, raw: s[k] ?? 0, coef: c }));
  const mix = mixBonus(s, F.mix, F.ratio);
  const native = parts.reduce((a, x) => a + x.raw * x.coef, 0) * (1 + mix);
  let best = { stat: F.stat, raw: s[F.stat] ?? 0, value: native, coef: F.coef[F.stat] ?? 1, parts, off: false, mix, hasMix: (F.mix ?? 0) > 0 };
  for (const k of RUNE_STAT_ORDER) {
    const v = (s[k] ?? 0) * RUNE.offStat;
    if (v > best.value) best = { stat: k, raw: s[k] ?? 0, value: v, coef: RUNE.offStat, parts: [{ stat: k, raw: s[k] ?? 0, coef: RUNE.offStat }], off: true, mix: 0, hasMix: false };
  }
  return best;
}

/**
 * v2.13.0 마검사 룬의 섞음 보너스 (0 ~ max): 마력 ÷ ratio 와 무기 스탯 (힘·민첩 중 큰 것) 중 작은 쪽 ÷ 큰 쪽 × max.
 * 마력만 · 힘·민첩만이면 0, 마력 = 무기 스탯 × ratio 이면 max (v2.13.1 마검사 ratio 2 = 마력 2 : 힘 1). max 가 없는 계열은 0
 * @param {Record<string, number>} s
 * @param {number} [max]
 * @param {number} [ratio]
 */
export function mixBonus(s, max, ratio = 1) {
  const m = max ?? 0;
  if (!(m > 0)) return 0;
  const phys = Math.max(s.str ?? 0, s.dex ?? 0);
  const mag = (s.int ?? 0) / (ratio > 0 ? ratio : 1);
  const hi = Math.max(phys, mag);
  return hi > 0 ? (m * Math.min(phys, mag)) / hi : 0;
}

/** 적용 스탯 글자: "힘 100 × 0.85", "민첩 60 × 1", 도끼 "힘 100 + 체력 40 × 0.5" (sep = 곱하기 표시) */
export function runeStatLabel(e, sep = " × ") {
  if (e.parts.length === 1) return `${STAT_INFO[e.parts[0].stat].name} ${e.parts[0].raw}${sep}${e.parts[0].coef}`;
  // v2.13.1 마검사 (섞음이 있는 계열): 0 인 스탯은 빼고 (마력만이면 "마력 125")
  const shown = e.hasMix ? e.parts.filter((x, i) => x.raw > 0 || i === 0) : e.parts;
  const sum = shown.map((x) => `${STAT_INFO[x.stat].name} ${x.raw}${x.coef === 1 ? "" : `${sep}${x.coef}`}`).join(" + ");
  return e.mix > 0 ? `${sum} · 섞음 +${Math.round(e.mix * 100)}%` : sum;
}

/** 위력 P = 레벨 기본값 × (1 + 적용 스탯 × perPoint) */
export function runePower(player, family) {
  const lv = getStatsCached(player).lvmax ?? 0;
  return runeLevelBase(lv) * (1 + runeStat(player, family).value * RUNE.perPoint);
}

/** 회복·보호량 배율 (적용 스탯 1포인트당 perPoint 의 절반) */
function supportScale(player, family) {
  return 1 + runeStat(player, family).value * RUNE.perPoint * 0.5;
}

/** v2.24.0 평타마다 위력 × bonus 를 더하는 평타 강화 룬 */
const STRIKE_BONUS_FNS = new Set(["edgeHone", "cleave", "bloodBlade", "swordGod"]);
/** 룬 한 번에 한 대상에게 들어가는 피해 합 (위력 단위, 조건부 추가 피해·언데드 배수·콤보 배율 제외). 중독(dot)은 더함 */
export function runeTotalMult(def) {
  if (def.fn === "flurry") return (def.echo ?? 0) * RUNE_STRIKE.echoHits; // v2.24.0 연타: 근접 7초 동안 약 echoHits 번
  if (STRIKE_BONUS_FNS.has(def.fn)) return (def.bonus ?? 0) * (def.hits ?? 1); // v2.24.0 평타 강화: 평타 hits 번 × bonus (처형의 bonus 는 체력 낮을 때 배수 — 다른 뜻)
  if (!def.mult) return 0;
  if (def.fn === "bulwark") return def.mult * Math.floor(def.ticks / def.pulse);
  if (def.fn === "undyingPact") return def.mult * Math.floor(def.ticks / def.pulse);
  return def.mult * (def.hits ?? 1) + (def.dot ?? 0) * (def.dots ?? 0) + (def.crush ?? 0); // v2.15.0 crush = 마지막 붕괴 · 내리꽂기
}

const round1 = (n) => Math.round(n * 10) / 10;

/** 계수 줄: "민첩 계수 1.0", 도끼 "힘 1.0 + 체력 0.5" */
function coefText(family) {
  const F = RUNE_FAMILIES[family];
  const parts = Object.entries(F?.coef ?? {});
  if (parts.length === 1) return `${STAT_INFO[parts[0][0]].name} 계수 ${parts[0][1].toFixed(1)}`;
  return parts.map(([k, c]) => `${STAT_INFO[k].name} ${c.toFixed(1)}`).join(" + ");
}

const ROMAN = ["I", "II", "III", "IV", "V"];
/** UTF-8 바이트 수 (게임 스크립트에는 TextEncoder 가 없음) */
function utf8Length(text) {
  let n = 0;
  for (const ch of text) {
    const c = ch.codePointAt(0) ?? 0;
    n += c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
  }
  return n;
}
/** v2.14.0 원소 검사 설명 줄: 원소마다 남기는 것 (불붙음 · 구속 · 튐) */
function elementLore(d, L, dark) {
  const fire = `§r${dark ? "§4" : "§6"}불${L}: 불붙음 ${d.fire}초`;
  const ice = `§r${dark ? "§3" : "§b"}얼음${L}: 구속 ${ROMAN[d.slowAmp ?? 0]} ${(d.slowTicks ?? 0) / 20}초`;
  const thunder = `§r${dark ? "§6" : "§e"}번개${L}: ${d.chain}칸 안 한 명에 위력×${d.chainMult}`;
  if (d.el === "fire") return [fire];
  if (d.el === "ice") return [ice];
  if (d.el === "thunder") return [thunder];
  const head = d.fn === "elementRelease" ? [`§r${L}검기 셋: 불 · 얼음 · 번개`, `§r${L}적마다 한 번 (먼저 닿은 검기)`] : [`§r${L}불 → 얼음 → 번개 (${(d.gap ?? 10) / 20}초마다)`];
  return [...head, fire, ice, thunder];
}

/** v2.15.0 중력 설명 줄: 끌어당김 · 띄움 · 붕괴 · 내리꽂기 · 보스 */
function gravityLore(d, L, dark) {
  const N = dark ? "§0" : "§f";
  const sec = (t) => `${round1(t / 20)}초`;
  const out = [];
  if (d.fn === "gravityOrb") out.push(`§r${L}끌어당김: 반지름 ${d.pull}칸 · ${sec(d.pullTicks)}`, `§r${L}→ 반지름 ${d.radius}칸 폭발`);
  if (d.fn === "gravityWell") out.push(`§r${L}끌어당김: 반지름 ${d.pull}칸 · ${sec(d.hits * d.gap)}`, `§r${L}${sec(d.gap)}마다 ${N}위력×${d.mult}${L} · 구속 ${ROMAN[d.slowAmp ?? 0]}`);
  if (d.fn === "blackHole") out.push(`§r${L}빨아들여 띄움: 반지름 ${d.pull}칸 · ${sec(d.hits * d.gap)}`, `§r${L}${sec(d.gap)}마다 ${N}위력×${d.mult}`);
  if (d.lift) out.push(`§r${L}띄움 ${sec(d.gap)} (그 동안 공격 못 함)`);
  if (d.fn === "liftSlam") out.push(`§r${L}내리꽂기: ${N}위력×${d.mult}`);
  if (d.crush) out.push(`§r${L}${d.crushName}: ${N}위력×${d.crush}${d.crushName === "붕괴" ? `${L} (반지름 ${d.radius}칸)` : ""}`);
  if (d.slowTicks) out.push(`§r${L}짓눌림: 구속 ${ROMAN[d.slowAmp ?? 0]} ${sec(d.slowTicks)}`);
  out.push(`§r${L}보스는 끌리거나 뜨지 않음`);
  return out;
}

/** v2.14.0 정령사 설명 줄: 부르는 정령 · 공격 · 노리는 순서 */
function spiritLore(d, L, dark) {
  const G = dark ? "§2" : "§a";
  const secs = d.ticks / 20;
  if (d.spirits.length > 1) {
    return [`§r${G}정령${L}: 넷 ${secs}초 (정령마다 ${Math.round(d.hits / d.spirits.length)}번)`, `§r${L}${d.spirits.map((k) => RUNE_SPIRITS[k].name).join("·")}`, `§r${L}보는 적부터 노림`];
  }
  const S = RUNE_SPIRITS[d.spirits[0]];
  const how = {
    salamander: `불덩이 폭발 ${S.radius}칸 · 불붙음 ${S.fire}초`,
    undine: `물줄기 ${S.radius}칸 · 밀침 · 구속 ${(S.slowTicks ?? 0) / 20}초`,
    sylph: `꿰뚫고 ${S.pierce}칸 너머까지 돌진 · 띄움`,
    gnome: `${(S.warn ?? 0) / 20}초 뒤 가시 ${S.radius}칸 · 구속 II`,
  }[d.spirits[0]];
  return [`§r${G}정령${L}: ${S.name} ${secs}초`, `§r${L}${how}`, `§r${L}보는 적부터 노림`];
}

/** v2.24.0 신체 강화 · 평타 강화 설명 줄 (지속 · 효과) */
function buffLore(r, L, dark) {
  const N = dark ? "§0" : "§f";
  const d = r.def;
  const sec = (t) => `${round1(t / 20)}초`;
  const out = [];
  if (r.family === RUNE_BODY.family) {
    out.push(`§r${L}지속 ${N}${sec(d.ticks)}`);
    if (d.speed !== undefined) out.push(`§r${L}이동 속도 ${N}${ROMAN[d.speed] ?? d.speed + 1}`);
    if (d.statUp) out.push(`§r${L}모든 스탯 ${N}+${Math.round(d.statUp * 100)}%`);
    if (d.steadfast) out.push(`§r${L}보스 기술에 밀려나지 않음`);
    if (d.heal) out.push(`§r${L}${sec(d.pulse)}마다 ${N}최대 체력 ${round1(d.heal * 100)}%+`);
    if (d.mult) out.push(`§r${L}쓸 때 충격파 반지름 ${d.radius}칸`);
  } else {
    out.push(`§r${L}${d.hits ? `평타 ${d.hits}번 · ` : ""}${sec(d.ticks)} 동안`);
    if (d.bonus) out.push(`§r${L}평타마다 ${N}+위력×${d.bonus}`);
    if (d.rangedCd) out.push(`§r${L}활·완드 재사용 ${N}-${Math.round((1 - d.rangedCd) * 100)}%`);
    if (d.echo) out.push(`§r${L}근접 ${N}+위력×${d.echo}${L} · 옆의 적도`);
    if (d.cleave) out.push(`§r${L}근접: 앞 부채꼴 ${d.cleave}칸`, `§r${L}활·완드: 맞은 적 뒤 ${d.splash}칸`);
    if (d.heal) out.push(`§r${L}맞히면 ${N}최대 체력 ${round1(d.heal * 100)}%+`);
    if (d.wave) out.push(`§r${L}평타마다 검기 ${d.wave}칸 관통`);
    if (d.crit) out.push(`§r${L}평타 치명타 ${N}+${Math.round(d.crit * 100)}%p`);
  }
  return out;
}

/** 룬 설명 (한 줄 50바이트 안) */
export function runeLore(r, dark = false) {
  const L = dark ? "§8" : "§7";
  const N = dark ? "§0" : "§f";
  const d = r.def;
  let head = `§r${L}${r.famName} 룬 · ${r.gradeName} · 재사용 ${r.cd}초`;
  if (utf8Length(head) > 50) head = `§r${L}${r.famName} · ${r.gradeName} · 재사용 ${r.cd}초`; // v2.14.0 긴 계열 이름 (원소 검사)
  const lines = [head, `§r${L}${d.desc}`];
  if (d.use) lines.push(`§r${L}용도 ${N}${d.use}`);
  const total = r.family === RUNE_STRIKE.family ? 0 : runeTotalMult(d); // v2.24.0 평타 강화는 buffLore 가 (평타마다)
  if (total > 0 && d.crush) lines.push(`§r${L}피해 ${N}위력×${round1(total)}${L} (${(d.hits ?? 1) > 1 ? `${d.mult}×${d.hits}` : d.mult} + ${d.crush})`); // v2.15.0 중력: 펄스 · 띄움 + 붕괴 · 내리꽂기
  else if (total > 0) lines.push(`§r${L}피해 ${N}위력×${round1(total)}${L}${d.hits ? ` (${d.hits}번)` : ""}`);
  if (d.dot) lines.push(`§r${L}중독 ${N}위력×${d.dot}${L}, ${d.dotGap / 20}초마다 ${d.dots}번`);
  if (d.splashMult) lines.push(`§r${L}주위 ${d.splash}칸 ${N}위력×${d.splashMult}`);
  if (total > 0 && r.family === "holy") lines.push(`§r${L}신성 피해 · 언데드 ${N}${RUNE.undeadBonus}배`);
  if (d.reduce) lines.push(`§r${L}받는 피해 ${N}-${Math.round(d.reduce * 100)}%`);
  const buff = r.family === RUNE_BODY.family || r.family === RUNE_STRIKE.family; // v2.24.0 회복 줄은 buffLore 가
  if (!buff && d.heal && d.healMax) lines.push(`§r${L}흡혈 ${N}적마다 최대 체력 ${Math.round(d.heal * 100)}%+`);
  else if (!buff && d.heal) lines.push(`§r${L}회복 ${N}최대 체력 ${Math.round(d.heal * 100 * (d.fn === "undyingPact" ? d.ticks / d.pulse : d.hits ?? 1))}%+`);
  if (d.shield && d.cap) lines.push(`§r${L}보호막 ${N}${Math.round(d.shield * 100)}%씩${L} (최대 ${Math.round(d.cap * 100)}%)`);
  else if (d.shield) lines.push(`§r${L}보호막 ${N}최대 체력 ${Math.round(d.shield * 100)}%+`);
  const F = RUNE_FAMILIES[r.family];
  if (F?.mix) {
    // v2.13.0 마검사: 섞음 보너스, 검·마력 피해 종류, 마검 부여 (v2.13.1 마력 + 힘·민첩 절반, 마력 2 : 힘·민첩 1 에서 가장 셈)
    lines.push(`§r${L}적용 = 마력 + 힘·민첩 × ${F.coef.str ?? 1}`);
    lines.push(`§r${L}마력 ${F.ratio ?? 1} : 힘·민첩 1 → 최대 ${N}+${Math.round(F.mix * 100)}%`);
    lines.push(`§r${L}피해 종류: ${d.kind === "magic" ? "마력(마법)" : d.kind === "sword" ? "검(공격)" : "검(공격) + 마력(마법)"}`);
    if (d.imbue) {
      const I = RUNE_SPELLBLADE.imbue;
      lines.push(`§r${dark ? "§1" : "§9"}마검 부여${L}: 평타 ${I.hits}번 (룬 뒤 ${I.ticks / 20}초)`);
      lines.push(`§r${L}평타 ${N}+위력×${d.imbue}${L} · 관통 ${I.pierce}칸`);
    }
  } else if (F?.best) lines.push(`§r${L}적용 = 가장 높은 스탯 × 1.0`); // v2.24.0 신체 강화 · 평타 강화
  else lines.push(`§r${L}${coefText(r.family)} · 다른 스탯 ${RUNE.offStat}`);
  if (buff) for (const l of buffLore(r, L, dark)) lines.push(l);
  if (d.el) for (const l of elementLore(d, L, dark)) lines.push(l); // v2.14.0 원소 검사
  if (d.spirits) for (const l of spiritLore(d, L, dark)) lines.push(l); // v2.14.0 정령사
  if (r.family === "gravity") for (const l of gravityLore(d, L, dark)) lines.push(l); // v2.15.0 중력
  lines.push(`§r${L}위력 = 레벨 공격력×(1+스탯×${Math.round(RUNE.perPoint * 100)}%)`);
  if (r.family === RUNE_COMBO.family) {
    lines.push(`§r${dark ? "§5" : "§d"}콤보${L}: ${Math.round(RUNE_COMBO.window / 20)}초 안에 다른 암살자 룬`);
    lines.push(`§r${L}이을 때마다 피해 +${Math.round(RUNE_COMBO.step * 100)}% (${RUNE_COMBO.minOwned}종부터)`);
    lines.push(`§r${L}5종을 모두 이으면 §5${RUNE_COMBO.finisher.name}`);
  }
  lines.push(`§r§8터치: 칸을 고르면 바로 사용`);
  lines.push(`§r§8누르면 사용 · 요구 스탯 없음`);
  return lines;
}

/** 룬 아이템 (설명 포함) */
export function createRuneItem(family, grade) {
  const id = runeId(family, grade);
  const item = new ItemStack(id, 1);
  const r = parseRune(id);
  if (r) setLoreSafe(item, runeLore(r));
  return item;
}

/**
 * 룬 뽑기권 아이템. adv = 고급 룬 뽑기권 (v2.7.0, 일반 룬 없음)
 * @param {number} [amount]
 * @param {boolean} [adv]
 */
export function createTicketItem(amount = 1, adv = false) {
  const item = new ItemStack(adv ? RUNE.advTicketItem : RUNE.ticketItem, Math.max(1, Math.min(64, amount)));
  setLoreSafe(item, adv ? RUNE.advTicketLore : RUNE.ticketLore);
  return item;
}

// ------------------------------------------------------------ 피해·도구
function healthOf(e) {
  try {
    return e.getComponent("minecraft:health")?.currentValue ?? 0;
  } catch {
    return 0;
  }
}

/**
 * 룬 피해 한 번 (P × mult). 실제로 들어간 피해 (0 = 안 들어감). 피해 종류: 마력 = 마법, 나머지 = 공격 (cause 로 바꿈)
 * @param {any} player
 * @param {any} target
 * @param {number} P
 * @param {number} mult
 * @param {string} [stat]
 * @param {any} [cause0]
 * @param {import("./fx.js").TextStyle} [style] 피해 숫자 모양 (기본 노랑 rune, v2.13.0 마검사의 마력은 보라 spell)
 */
export function runeHit(player, target, P, mult, stat, cause0, style = "rune") {
  if (!canHit(player, target)) return 0;
  if (target.typeId === "minecraft:player" && !RUNE.hitPlayers && !isDuelPartner(player, target)) return 0; // v2.26.0 결투 상대는 맞음 (× PVP.mult — combat.js)
  const dmg = P * mult;
  const cause = cause0 ?? (stat === "int" ? EntityDamageCause.magic : EntityDamageCause.entityAttack);
  const before = healthOf(target);
  if (!applySkillDamage(player, target, dmg, false, cause, style)) return 0;
  const after = target.isValid ? healthOf(target) : 0;
  return before > after ? before - after : dmg;
}

/** 밀치기 (방향 d 의 수평, 위로 h) */
function knock(e, d, k, h) {
  try {
    const f = flat(d);
    e.applyKnockback({ x: f.x * k, z: f.z * k }, h);
  } catch {
    // 밀리지 않는 몹
  }
}

function effect(e, id, ticks, amp) {
  try {
    e.addEffect(id, ticks, { amplifier: amp, showParticles: false });
  } catch {
    // 효과가 없는 몹
  }
}

function burn(e, seconds) {
  try {
    e.setOnFire(seconds, true);
  } catch {
    // 불이 붙지 않는 몹
  }
}

const dot = (a, b) => a.x * b.x + a.y * b.y + a.z * b.z;
const scale = (a, k) => ({ x: a.x * k, y: a.y * k, z: a.z * k });
/** 수평 방향 f 를 y축으로 deg 도 돌림 */
function turn(f, deg) {
  const a = (deg * Math.PI) / 180;
  return { x: f.x * Math.cos(a) - f.z * Math.sin(a), y: 0, z: f.x * Math.sin(a) + f.z * Math.cos(a) };
}

/** 조준한 곳의 땅 (조준한 몹의 발밑 → 시선이 닿은 블록 → 사거리 끝, 거기서 아래로 땅을 찾음) */
function groundAim(player, range) {
  const r = aimTarget(player, range, BOW.aimAssist ?? 0);
  if (r.target?.isValid) return { ...r.target.location };
  const head = player.getHeadLocation();
  const dir = r.dir ?? player.getViewDirection();
  const p = add(head, dir, Math.max(0.5, r.dist - 0.3));
  try {
    const hit = player.dimension.getBlockFromRay(up(p, 0.3), V_DOWN, { maxDistance: 32, includePassableBlocks: false, includeLiquidBlocks: false });
    if (hit) return { x: p.x, y: hit.block.location.y + 1, z: p.z };
  } catch {
    // 무시
  }
  return { x: p.x, y: Math.min(p.y, player.location.y), z: p.z };
}

/**
 * 범위 고리: 안쪽에서 퍼져 반지름 R(피해 범위)에서 사라짐.
 * shock_ring 은 처음 크기의 2.6배까지 커지며 흐려지므로 R/2.1 로 시작하면 보이는 끝이 R 과 맞음
 * (그냥 shock(R) 은 R 의 2배 넘게 퍼져서 범위가 실제보다 커 보임)
 */
function zonePulse(dim, feet, color, R, life = 0.45) {
  shock(dim, feet, color, R / 2.1, life);
}

/**
 * 1인칭에서도 보이는 자기 강화 표시 (발밑 룬·몸의 기둥은 1인칭 화면 밖이거나 카메라 안이라 안 보임):
 * 앞쪽 반원에서 솟는 빛 알갱이 + (withRing) 눈앞에서 앞으로 퍼지는 고리
 */
function selfCue(player, color, core, withRing = true) {
  const dim = player.dimension;
  const view = player.getViewDirection();
  const f = flat(view);
  if (withRing) ring(dim, add(player.getHeadLocation(), view, 0.9), view, color, 1.1, 0.45, 2.5);
  for (let i = 0; i < 7; i++) {
    const d = turn(f, (i / 6 - 0.5) * 150);
    const p = add(player.location, d, 1.5 + (i % 2) * 0.5);
    fx(dim, "rpg:arrow_spark", up(p, 0.4 + (i % 3) * 0.3), { dir: V_UP, speed: 2.4, size: 0.16, grow: 1.2, life: 0.7, color: i % 2 ? core : color, alpha: 1 });
  }
}

/** 가까운 플레이어들 화면 흔들기 */
function shakeAround(dim, at, radius, intensity, seconds) {
  let list = [];
  try {
    list = dim.getPlayers({ location: at, maxDistance: radius });
  } catch {
    list = [];
  }
  for (const pl of list) shake(pl, intensity, seconds);
}

/** 앞쪽 원뿔 안의 적 (가까운 순, 벽 뒤 제외) */
function coneTargets(player, range, deg, max) {
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const half = ((deg / 2) * Math.PI) / 180;
  return around(player, player.location, range, 4)
    .filter((e) => bodyInCone(e, player.location, f, half) && lineOfSight(player, head, bodyAt(e, head.y)))
    .map((e) => ({ e, dist: Math.hypot(e.location.x - player.location.x, e.location.z - player.location.z) }))
    .sort((a, b) => a.dist - b.dist)
    .slice(0, max)
    .map((o) => o.e);
}

// ============================================================ v1.4.0 도구
/** t 틱 뒤 (0 이하면 바로: 다음 틱으로 미루면 여러 번 때리는 간격이 한 틱 줄어듦) */
function later(t, fn) {
  if (t <= 0) fn();
  else system.runTimeout(fn, Math.round(t));
}

/** 언데드인지 (좀비·스켈레톤·팬텀 등). 성기사 신성 피해 RUNE.undeadBonus 배 */
function isUndead(e) {
  try {
    return !!e.getComponent("minecraft:type_family")?.hasTypeFamily("undead");
  } catch {
    return false;
  }
}

/** 성기사 신성 피해: 마법 피해(방어구 무시), 언데드는 undeadBonus 배 */
function holyHit(player, e, P, mult) {
  return runeHit(player, e, P, mult * (isUndead(e) ? RUNE.undeadBonus : 1), "vit", EntityDamageCause.magic);
}

/** 기절: ticks 동안 움직이지 못하고(구속 XI) 근접 공격이 거의 들어가지 않음(나약함 V). 머리 위에 작은 룬이 돎 */
function stun(e, ticks, color, mark = true) {
  effect(e, "slowness", ticks, 10);
  effect(e, "weakness", ticks, 4);
  if (!mark) return; // 머리 위 표시 없이 (적이 아주 많을 때 — 파티클 예산)
  try {
    floorRune(e.dimension, up(e.getHeadLocation(), 0.45), { size: 0.42, life: ticks / 20, color, alpha: 0.95, rate: 300 });
  } catch {
    // 무시
  }
}

/** from 에서 dir 로 막는 블록까지 거리 (없으면 max) */
function rayLength(dim, from, dir, max) {
  try {
    const hit = dim.getBlockFromRay(from, dir, { maxDistance: max, includePassableBlocks: false, includeLiquidBlocks: false });
    if (hit) {
      const b = hit.block.location;
      const fl = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
      return Math.max(0, Math.min(max, len(sub({ x: b.x + fl.x, y: b.y + fl.y, z: b.z + fl.z }, from))));
    }
  } catch {
    // 무시
  }
  return max;
}

/** 수평 방향 f 로 max 칸까지 막힘 없이 갈 수 있는 거리 (heights 높이의 광선, 벽 앞 0.6칸) */
function dashDistance(player, f, max, heights = [0.3, 1.5]) {
  const dim = player.dimension;
  const start = player.location;
  let dist = max;
  for (const h of heights) {
    try {
      const hit = dim.getBlockFromRay(up(start, h), f, { maxDistance: max + 0.8, includePassableBlocks: false, includeLiquidBlocks: false });
      if (hit) {
        const b = hit.block.location;
        const fl = hit.faceLocation ?? { x: 0.5, y: 0.5, z: 0.5 };
        dist = Math.min(dist, Math.hypot(b.x + fl.x - start.x, b.z + fl.z - start.z) - 0.6);
      }
    } catch {
      // 무시
    }
  }
  return Math.max(0, dist);
}

/** p 자리의 땅 높이 (p 보다 1.6 위에서 아래로 3.5칸 안, 없으면 fallback) — 한 칸 턱을 넘어 옮길 때 */
function groundAt(dim, p, fallback) {
  try {
    const hit = dim.getBlockFromRay(up(p, 1.6), V_DOWN, { maxDistance: 3.5, includePassableBlocks: false, includeLiquidBlocks: false });
    if (hit) return hit.block.location.y + 1;
  } catch {
    // 무시
  }
  return fallback;
}

/**
 * 착지할 땅: from 에서 아래로 maxDown 칸 안의 첫 블록 (물·용암 포함해서 찾음).
 * 단단한 블록이면 그 위 높이, 물·용암이거나 땅이 없으면 undefined (그곳으로 옮기지 않음)
 */
function solidGround(dim, from, maxDown) {
  try {
    const hit = dim.getBlockFromRay(from, V_DOWN, { maxDistance: maxDown, includePassableBlocks: false, includeLiquidBlocks: true });
    if (!hit) return undefined;
    const b = hit.block;
    if (b.isLiquid || /water|lava/.test(b.typeId ?? "")) return undefined;
    return b.location.y + 1;
  } catch {
    return undefined;
  }
}

/**
 * 옮기기 (막힌 자리면 그대로). 옮겼으면 true
 * @param {any} player
 * @param {{x: number, y: number, z: number}} at
 * @param {{x: number, y: number, z: number}} [facing] 옮긴 뒤 바라볼 곳 (v2.13.0 차원 베기: 벤 적 쪽으로 돌아섬)
 */
function moveTo(player, at, facing = undefined) {
  try {
    player.teleport(at, facing ? { keepVelocity: false, checkForBlocks: true, facingLocation: facing } : { keepVelocity: false, checkForBlocks: true });
    return true;
  } catch {
    return false;
  }
}

/** 주위 플레이어 (나 포함 — v2.26.0 결투 중인 사람은 빼고: 결투 상대는 아군이 아니고, 남의 결투는 밖에서 돕지 못함) */
function playersAround(dim, c, radius, caster = undefined) {
  try {
    const list = dim.getPlayers({ location: c, maxDistance: radius });
    return caster ? list.filter((pl) => canAssist(caster, pl)) : list;
  } catch {
    return [];
  }
}

/** 정화로 없애는 나쁜 효과 */
const BAD_EFFECTS = ["poison", "fatal_poison", "wither", "slowness", "weakness", "blindness", "nausea", "hunger", "mining_fatigue", "darkness", "levitation"];
/** 나쁜 효과 없애기. 없앤 개수 */
function cleanse(pl) {
  let n = 0;
  for (const id of BAD_EFFECTS) {
    try {
      if (pl.getEffect(id) && pl.removeEffect(id)) n++;
    } catch {
      // 없는 효과
    }
  }
  return n;
}

/** 직선(from → dir, 길이 L, 폭 w) 가까이의 적, 앞에서부터 [{e, t: 앞으로 거리}] */
function lineTargets(player, from, dir, L, w) {
  const mid = add(from, dir, L / 2);
  const out = [];
  for (const e of around(player, mid, L / 2 + w + 0.5, L / 2 + 3)) {
    const pad = bodyPad(e);
    const p = sub(bodyToward(e, from, dir), from);
    const t = dot(p, dir);
    if (t < -0.3 - pad || t > L + 0.5 + pad) continue;
    if (len(sub(p, scale(dir, t))) > w + pad) continue;
    out.push({ e, t: Math.max(0, t) });
  }
  return out.sort((a, b) => a.t - b.t);
}

/** c 에서 수평 방향 f 쪽 cone 도·radius 칸 안의 적 (1.2칸 안은 방향 무관) */
function coneAround(player, c, f, radius, cone) {
  const half = ((cone / 2) * Math.PI) / 180;
  return around(player, c, radius).filter((e) => bodyInCone(e, c, f, half));
}

/** 가까운 순 */
function nearestTo(list, p) {
  return [...list].sort((a, b) => len(sub(a.location, p)) - len(sub(b.location, p)));
}

/** a → b 곧은 빛 한 줄 (파티클 하나) */
function streak(dim, a, b, color, thick, life, alpha = 1, delay = 0) {
  const d = sub(b, a);
  const L = len(d);
  if (L < 0.05) return;
  fx(dim, "rpg:arrow_trail", add(a, d, 0.5), { dir: unit(d), size: L / 2, thick, life: life + delay, delay, color, alpha });
}

/** 날아가는 빛 (빛 + 심지). 도착까지 틱 */
function flyStreak(dim, from, to, speed, glow, core, size = 0.7, thick = 0.26) {
  const d = sub(to, from);
  const L = len(d);
  const life = Math.max(0.05, L / speed);
  const u = unit(d);
  fx(dim, "rpg:arrow_shot", from, { dir: u, speed: L / life, size, thick, life, color: glow, alpha: 1 });
  fx(dim, "rpg:arrow_shot", from, { dir: u, speed: L / life, size: size * 0.75, thick: thick * 0.35, life, color: core, alpha: 1 });
  return Math.max(1, Math.round(life * 20));
}

/** 맞힌 자리 번쩍임 (X 참격 + 폭발 + 불꽃) */
function hitFlash(dim, at, dir, glow, core, size = 0.9) {
  fx(dim, "rpg:slash_x", at, { dir, size, life: 0.24, color: glow, alpha: 1 });
  burst(dim, at, glow, size, 0.24);
  sparks(dim, at, core, 5, 4);
}

// ============================================================ v2.24.0 룬 연출 그림 (rpg:rfx_* — generator/rune_art.py)
// 사용자 요청 "초월 룬이나 다른 일반 룬들 이펙트가 부족한 것을 채우고" · 고른 것: 초월 14 + 정령사 + 약한 것 (블리자드 · 뇌운 · 화염구 · 회전 도끼 · 독 표창 · 심판의 망치 · 저격)
/** 그림 칸: 색을 입히는 그림은 [빛, 심지] */
const RFX = {
  sword: [0, 1],
  wave: [0, 1],
  arrow: [0, 1],
  icicle: [0, 1],
  moon: [0, 1],
  axe: [0, 5],
  hammer: [1, 6],
  shuriken: [2, 7],
  feather: [3, 8],
  glint: [4, 9],
  bowCore: 4, // 하늘의 활: 빛 0~3 (시위를 당긴 정도) · 심지 4~7
  wingsCore: 2, // 날개: 빛 0 (위) · 1 (아래) · 심지 2~3
  spearCore: 4, // 번개 창: 빛 0~3 (번쩍임) · 심지 4~7
  boltCore: 4, // 번개: 빛 0~3 (4가지) · 심지 4~7
  holeCore: 1, // 블랙홀: 원반 0 · 핵 1
  spirit: { salamander: 0, undine: 4, sylph: 8, gnome: 12 }, // 정령 4장씩
  // v2.25.0 (rune_art2.py)
  rockCore: 3, // 바위 가시: 모양 0~2 · 틈의 빛 3~5
  shadeCore: 8, // 그림자 분신: 자세 0 섬 · 1 돌진 · 2 베기 · 3 찌르기 (왼쪽을 보면 +4) · 테두리 +8
  crystalCore: 3, // 얼음 결정: 모양 0~2 · 모서리 3~5
  plasmaCore: 4, // 번개 구슬: 빛 0~3 · 심지 4~7
  // v2.27.0 초월 룬 2차 강화 (ult_art.py): 빛 · 심지 두 장씩 (rfx2 의 cells [0, 1] + cell)
  sigil: { arcane: 0, holy: 2, blade: 4, spirit: 6 }, // 영역 문양 4가지 (빛 = 칸, 심지 = 칸 + 1)
  crackV: 2, // 갈라진 금: 모양 v 의 첫 칸 = v × 2
};

/**
 * 룬 연출 그림 하나 (rpg:rfx_<name>). o = {dir 방향 (긴 그림은 그 방향으로 눕고 그 방향으로 움직임), size 반 크기 (긴 쪽), life 초, color, alpha,
 * spin · spinRate (화면을 보는 그림의 돎 — 도 · 도/초), speed (dir 쪽 칸/초), grow (수명 동안 1 + grow 배), cell 첫 장, frames · fps · loop · phase (장 넘기기), fade (끝에서 사라지는 수명 비율),
 * vel (v2.24.0 몸을 따라오는 속도 칸/초 — 몸에 붙은 그림이 걷기 · 달리기에 뒤처지지 않게, bodyVel)}
 */
function rfx(dim, name, at, o) {
  try {
    // 움직임 = dir × speed + vel (vel: 몸을 따라오는 그림 — 칸/초). 그림의 방향 (dir) 과 따로 (variable.move · speed)
    const d = o.dir ?? V_UP;
    const sp = o.speed ?? 0;
    const vel = o.vel ?? V_ZERO;
    const mv = { x: d.x * sp + vel.x, y: d.y * sp + vel.y, z: d.z * sp + vel.z };
    const ms = len(mv);
    const m = particleVars({ dir: d, size: o.size, life: o.life, color: o.color ?? WHITE, alpha: o.alpha ?? 1, spin: o.spin ?? 0, speed: ms, grow: o.grow ?? 0 });
    m.setVector3("variable.move", ms > 1e-6 ? { x: mv.x / ms, y: mv.y / ms, z: mv.z / ms } : d);
    m.setVector3("variable.vel", vel);
    m.setFloat("variable.spin_rate", o.spinRate ?? 0);
    m.setFloat("variable.cell", o.cell ?? 0);
    m.setFloat("variable.frames", o.frames ?? 1);
    m.setFloat("variable.fps", o.fps ?? 0);
    m.setFloat("variable.loop", o.loop ? 1 : 0);
    m.setFloat("variable.phase", o.phase ?? 0);
    m.setFloat("variable.fade", o.fade ?? 0);
    dim.spawnParticle(`rpg:rfx_${name}`, at, m);
  } catch {
    // 로드되지 않은 곳 등
  }
}

/**
 * 색을 입히는 그림의 빛 + 심지 두 장 (cells = [빛 칸, 심지 칸] — o.cell 은 둘에 더함)
 * @param {any} dim
 * @param {string} name
 * @param {{x: number, y: number, z: number}} at
 * @param {any} o
 * @param {number[]} glow
 * @param {number[]} core
 * @param {number[]} [cells]
 * @param {number} [coreAlpha] 심지만 다른 불투명도
 */
function rfx2(dim, name, at, o, glow, core, cells = RFX[name] ?? [0, 1], coreAlpha = undefined) {
  rfx(dim, name, at, { ...o, color: glow, cell: cells[0] + (o.cell ?? 0) });
  rfx(dim, name, at, { ...o, color: core, cell: cells[1] + (o.cell ?? 0), alpha: coreAlpha ?? o.alpha });
}

/** 그림까지 거리: 1인칭 눈과 3인칭 카메라 (보는 쪽 뒤 4.5칸 · 위 1칸 근사) 중 가까운 쪽 */
function camDist(player, at) {
  try {
    const eye = player.getHeadLocation();
    const f = facingOf(player);
    const cam = { x: eye.x - f.x * 4.5, y: eye.y + 1, z: eye.z - f.z * 4.5 };
    return Math.min(len(sub(at, eye)), len(sub(at, cam)));
  } catch {
    return 99;
  }
}

/** 큰 그림이 보는 곳에 가까우면 작게 (full 칸에서 1, 1칸에서 min) — 화면을 덮지 않게 */
function nearK(player, at, full, min = 0.3) {
  return Math.max(min, Math.min(1, (camDist(player, at) - 1) / Math.max(0.1, full - 1)));
}

// ============================================================ v2.27.0 초월 룬 2차 강화: 하늘 · 땅 전체 연출 (예고 → 발동 → 여운)
// 사용자 요청 "초월 룬이나 다른 일반 룬들 이펙트가 부족한 거 (ex. 정령 왕, 마검 시리즈 초월) 등 이펙트가 부족한 거를 채우고"
// 고른 것 "초월 룬 2차 강화" (16종을 한 번 더 크고 화려하게 — 하늘 · 땅 전체 연출, 단계 추가):
//   예고 (모으는 동안) = 하늘빛 (ultfx.js — 둘레 사람의 하늘이 룬의 색으로) · 땅의 거대한 영역 문양 · 하늘의 금 · 문양 · 달 · 구름
//   발동 = 화면 번쩍임 (ultfx.js) · 하늘의 섬광 · 충격 고리 · 빛기둥      여운 = 빛나는 금 · 하늘빛이 천천히 걷힘
// 피해 · 판정 · 시간은 그대로 (연출만 — 피해 비교 시험). 그림은 generator/ult_art.py (rpg:rfx_sigil · rays · crack · nova · flare)

/** 룬 함수의 하늘빛 · 번쩍임 설정 (config.js ULT_FX.runes) */
function ultOf(fn) {
  return ULT_FX.runes?.[fn] ?? {};
}

/** 하늘빛: 룬 함수 fn 의 하늘빛 (fogId 를 주면 그것) 을 at 둘레 사람에게 ticks 틱 */
function ultSkyFor(dim, at, fn, ticks, fogId = undefined) {
  return ultSky(dim, at, fogId ?? ultOf(fn).fog, ticks);
}

/** 번쩍임: 룬 함수 fn 의 색 (key = flash 발동 · still 멈춘 시간 · dark 어둠) 으로 at 둘레 사람 + 룬을 쓴 사람 (멀리 겨눠도) 화면 */
function ultFlashFor(player, dim, at, fn, key = "flash", o = {}) {
  return ultFlash(dim, at, ultOf(fn)[key], { ...o, caster: player });
}

/**
 * 하늘 자리: 시전자 눈에서 anchor 쪽 (수평 — anchor 가 몸 가까이면 보는 쪽) 으로 anchor 보다 back 칸 더 먼 곳.
 * 높이는 눈에서 elev 도 위 (폰 1인칭 세로 시야 ±30° 안 — 화면 위쪽), anchor 땅에서 minH ~ maxH 칸
 */
function skyPoint(player, anchor, back = 8, elev = 20, minH = 6, maxH = 22) {
  let eye;
  try {
    eye = player.getHeadLocation();
  } catch {
    return up(anchor, minH + 4); // 나가는 중: 겨눈 자리 위
  }
  const dx = anchor.x - eye.x;
  const dz = anchor.z - eye.z;
  const D = Math.hypot(dx, dz);
  const d = D > 1.5 ? { x: dx / D, y: 0, z: dz / D } : facingOf(player);
  const H = (D > 1.5 ? D : 0) + back;
  const y = Math.max(anchor.y + minH, Math.min(anchor.y + maxH, eye.y + Math.tan((elev * Math.PI) / 180) * H));
  return { x: eye.x + d.x * H, y, z: eye.z + d.z * H };
}

/** 시전자 눈을 보는 방향 (하늘의 그림이 시전자에게 정면으로 — direction_z) */
function towardEye(player, at) {
  try {
    const v = sub(player.getHeadLocation(), at);
    return len(v) > 1e-3 ? unit(v) : V_UP;
  } catch {
    return V_UP;
  }
}

/**
 * 거대한 영역 문양 (rpg:rfx_sigil, 빛 + 심지): 땅에 눕힘 (o.dir 을 주면 그 쪽을 보는 면 — 하늘에 띄울 때).
 * style: arcane 비전 · holy 성광 · blade 칼날 · spirit 정령. o: life (초) · spin · spinRate (도 · 도/초) · grow (수명 동안 1 + grow 배) · alpha · fade · coreAlpha
 */
function domainSigil(dim, at, R, style, glow, core, o = {}) {
  const at2 = o.dir ? at : up(at, 0.08);
  rfx2(dim, "sigil", at2, { dir: o.dir ?? V_UP, size: R, life: o.life ?? 1, spin: o.spin ?? Math.random() * 360, spinRate: o.spinRate ?? 0, grow: o.grow ?? 0, alpha: o.alpha ?? 0.9, fade: o.fade ?? 0.35, cell: RFX.sigil[style] ?? 0 }, glow, core, [0, 1], o.coreAlpha);
}

/** 하늘의 그림 (시전자 쪽을 봄 — direction_z): name = sigil · crack · nova. o 는 rfx 의 것 (cell · life · grow · spin · spinRate · alpha · fade) */
function skyPlate(player, dim, name, at, size, glow, core, o = {}) {
  rfx2(dim, name, at, { ...o, dir: towardEye(player, at), size }, glow, core, [0, 1]);
}

/**
 * 빛기둥 (rpg:rfx_rays — 서서 봄, 밑동 = 놓은 자리): c 둘레 반지름 R 에 n 개. o.arc = [시작, 끝] 도 (o.f 기준 — 기본 시전자가 보는 쪽, 둘레 전체 [0, 360]),
 * o.h 높이 (칸), life, alpha, colors (기둥마다 [빛, 심지]). 시전자 눈에서 5칸 안이면 밀어냄 (1인칭 화면을 덮지 않게)
 */
function godRays(player, dim, c, R, n, glow, core, o = {}) {
  if (!player?.isValid) return; // 나중에 그리는 기둥: 그 사이 나간 사람
  const f = o.f ?? facingOf(player);
  const [a0, a1] = o.arc ?? [0, 360];
  const full = a1 - a0 >= 359;
  let eye;
  try {
    eye = player.getHeadLocation();
  } catch {
    eye = up(c, 1.6);
  }
  const vf = facingOf(player);
  const cam3 = { x: eye.x - vf.x * 4.5, z: eye.z - vf.z * 4.5 }; // 3인칭 카메라 (보는 쪽 뒤 4.5칸 — camDist 와 같은 근사)
  for (let i = 0; i < n; i++) {
    const deg = full ? a0 + (360 * i) / n : n === 1 ? (a0 + a1) / 2 : a0 + ((a1 - a0) * i) / (n - 1);
    let p = add(c, turn(f, deg + (o.jitter ?? 0) * (Math.random() - 0.5)), R);
    const hx = p.x - eye.x;
    const hz = p.z - eye.z;
    const hd = Math.hypot(hx, hz);
    if (hd < 5) {
      if (o.skipNear) continue; // 제 자리가 아니면 의미 없는 기둥 (화살이 꽂힌 자리 등): 가까우면 그리지 않음
      p = hd > 1e-3 ? { x: eye.x + (hx / hd) * 5, y: p.y, z: eye.z + (hz / hd) * 5 } : add(eye, f, 5);
    }
    if (Math.hypot(p.x - cam3.x, p.z - cam3.z) < 5) continue; // 3인칭 카메라 바로 앞 (뒤쪽의 대상): 화면을 덮지 않게 그리지 않음
    if (playerNear(dim, { x: p.x, y: c.y + 1, z: p.z }, 3)) continue; // 다른 사람 바로 옆의 기둥도 (그 사람 화면을 덮지 않게)
    const [g, k] = o.colors?.[i % o.colors.length] ?? [glow, core];
    rfx2(dim, "rays", { x: p.x, y: c.y, z: p.z }, { size: (o.h ?? 14) / 2, life: o.life ?? 0.8, alpha: o.alpha ?? 0.85, fade: o.fade ?? 0.55, grow: o.grow ?? 0 }, g, k, [0, 1], o.coreAlpha ?? 0.9);
  }
}

/** at 에서 r 칸 안에 플레이어가 있는지 */
function playerNear(dim, at, r) {
  try {
    return dim.getPlayers({ location: at, maxDistance: r }).length > 0;
  } catch {
    return false;
  }
}

/** 충격 고리 (rpg:rfx_nova): 땅에 눕혀 반지름 R0 에서 R0 × (1 + grow) 까지 퍼지며 사라짐 (o.dir 을 주면 그 쪽을 보는 면) */
function novaRing(dim, at, R0, grow, glow, core, o = {}) {
  rfx2(dim, "nova", o.dir ? at : up(at, 0.15), { dir: o.dir ?? V_UP, size: R0, grow, life: o.life ?? 0.45, alpha: o.alpha ?? 0.95, fade: o.fade ?? 0.65, spin: Math.random() * 360 }, glow, core, [0, 1]);
}

/** 빛나는 금 (rpg:rfx_crack): 땅에 눕힘 (o.dir 을 주면 그 쪽을 보는 면 — 갈라진 하늘). o.v = 모양 (0 · 1), life · grow · alpha · fade */
function glowCrack(dim, at, R, glow, core, o = {}) {
  rfx2(dim, "crack", o.dir ? at : up(at, 0.06), { dir: o.dir ?? V_UP, size: R, life: o.life ?? 2, alpha: o.alpha ?? 0.95, fade: o.fade ?? 0.55, grow: o.grow ?? 0, spin: o.spin ?? Math.random() * 360, spinRate: o.spinRate ?? 0, cell: RFX.crackV * (o.v ?? 0) }, glow, core, [0, 1]);
}

/** 하늘의 섬광 (rpg:rfx_flare — 화면을 봄): 긴 빛살 · 무리 */
function skyFlare(dim, at, size, glow, core, o = {}) {
  rfx2(dim, "flare", at, { size, life: o.life ?? 0.5, grow: o.grow ?? 0.45, fade: o.fade ?? 0.65, alpha: o.alpha ?? 1, spin: o.spin ?? Math.random() * 45, spinRate: o.spinRate ?? 20 }, glow, core, [0, 1]);
}

/** 한 틱에 그린 맞은 자리 연출 수 (연출 이름 → 수) — fxQuota */
const fxQuotaCount = new Map();
let fxQuotaTick = -1;
/**
 * v2.27.0 적을 한꺼번에 많이 맞힐 때 (적 16 · 24명): 같은 틱에 같은 연출 (key) 은 max 번까지만 그림 (모바일 한 틱 파티클 예산 60 — 피해는 모두 그대로).
 * 그려도 되면 true
 */
function fxQuota(key, max) {
  const now = system.currentTick;
  if (now !== fxQuotaTick) {
    fxQuotaCount.clear();
    fxQuotaTick = now;
  }
  const n = fxQuotaCount.get(key) ?? 0;
  fxQuotaCount.set(key, n + 1);
  return n < max;
}

/** 몸을 따라오는 속도 (칸/초, 수평만 — 점프 곡선은 따라가지 못하므로). 몸에 붙은 그림 (기운 · 날개 · 어깨 위 검) 이 걷기 · 달리기에 뒤처지지 않게 */
function bodyVel(player) {
  try {
    const v = player.getVelocity();
    return { x: v.x * 20, y: 0, z: v.z * 20 };
  } catch {
    return V_ZERO;
  }
}

/**
 * 몸에 붙은 그림의 수명 (초): 2틱마다 다시 그림 — 짧아야 빨리 돌거나 뒷걸음질할 때 옛 자리 그림이 1인칭 화면을 오래 가리지 않음.
 * 0.15 = 다시 그린 그림이 나타나는 0.05초 동안 앞 그림이 남아 있음 (같은 자리의 날개 · 검은 사라지는 끝 없이 — 깜빡임 없게)
 */
const BODY_LIFE = 0.15;
/** 몸의 기운을 그린 틱 (플레이어 id → 틱): 여러 룬 (신체 강화 · 불사의 맹약 · 광전사의 혼) 이 같은 틱에 그리면 하나만 (파티클 예산) @type {Map<string, number>} */
const auraDrawnAt = new Map();

/**
 * 몸의 기운 (신체 강화 · 광전사의 혼 · 불사의 맹약): 불꽃 n 장을 양옆 · 뒤쪽 옆에 (앞과 바로 뒤는 비움 —
 * 1인칭 화면에 안 들어오고, 3인칭 카메라 바로 앞을 가리지 않음). 2틱마다 부름 (수명 BODY_LIFE 로 이어짐, 몸을 따라옴).
 * 같은 틱에 이미 그렸으면 그리지 않음 (o.force = 쓸 때의 큰 기운)
 */
function bodyAura(player, color, o = {}) {
  const now = system.currentTick;
  if (!o.force && auraDrawnAt.get(player.id) === now) return;
  auraDrawnAt.set(player.id, now);
  const dim = player.dimension;
  const c = player.location;
  const f = facingOf(player);
  const n = o.n ?? 4;
  const size = o.size ?? 1.05;
  const vel = bodyVel(player);
  for (let i = 0; i < n; i++) {
    const sd = i % 2 ? 1 : -1;
    const k = Math.floor(i / 2);
    const d = turn(f, sd * (118 + (k % 3) * 22 + Math.random() * 8));
    rfx(dim, "aura", add(c, d, o.radius ?? 0.7), { size: size * (0.85 + Math.random() * 0.3), life: o.life ?? BODY_LIFE, color, alpha: o.alpha ?? 0.72, frames: 8, fps: 16, loop: true, phase: Math.random() * 8, fade: 0.3, vel });
  }
}

/** 1인칭에서 보이는 기운: 화면 아래 양옆에서 솟는 작은 불티 (눈높이 아래) */
function auraCue(player, color, n = 2) {
  const dim = player.dimension;
  const f = facingOf(player);
  const c = player.location;
  for (let i = 0; i < n; i++) {
    const d = turn(f, (i % 2 ? 1 : -1) * (24 + Math.random() * 26));
    const p = add(c, d, 1.5 + Math.random() * 0.4);
    fx(dim, "rpg:arrow_spark", up(p, 0.2 + Math.random() * 0.5), { dir: V_UP, speed: 1.6, size: 0.12, grow: 1.2, life: 0.6, color, alpha: 1 });
  }
}

// ============================================================ v2.25.0 그림 도구 (rune_art2.py — 남은 44종 이펙트 보강)
/** 바위 가시 (rpg:rfx_rock — 밑동 고정, 모양 3가지): 바위 빛 (o.rock, 기본 ROCK) + 갈라진 틈의 빛 (glow). 1인칭 · 3인칭 카메라에 가까우면 작게 */
function rockSpike(player, dim, at, size, glow, o = {}) {
  const v = o.v ?? Math.floor(Math.random() * 3);
  const s = size * nearK(player, up(at, size), o.full ?? 4, 0.35);
  rfx2(dim, "rock", at, { size: s, life: o.life ?? 0.9, grow: o.grow ?? 0.2, fade: o.fade ?? 0.35, cell: v, alpha: o.alpha ?? 1 }, o.rock ?? ROCK, glow, [0, RFX.rockCore]);
}

/** 바위 가시 고리: c 둘레 반지름 R 에 n 개 (쓴 사람 몸에서 minDist 칸 안은 빼고 — 1인칭 화면을 가리지 않게) */
function rockRing(player, dim, c, R, n, size, glow, o = {}) {
  const p0 = player.location;
  const a0 = Math.random() * Math.PI * 2;
  for (let i = 0; i < n; i++) {
    const a = a0 + (i / n) * Math.PI * 2;
    const rr = R * (0.85 + Math.random() * 0.3);
    const at = { x: c.x + Math.cos(a) * rr, y: c.y, z: c.z + Math.sin(a) * rr };
    if (Math.hypot(at.x - p0.x, at.z - p0.z) < (o.minDist ?? 1.6)) continue;
    rockSpike(player, dim, at, size * (0.8 + Math.random() * 0.4), glow, o);
  }
}

/** 튀는 돌 n 개 (c 에서 둘레로) */
function rockBurst(dim, c, n, speed, size = 0.24) {
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + Math.random() * 0.5;
    fx(dim, "rpg:boulder", c, { dir: unit({ x: Math.cos(a), y: 1.1 + Math.random() * 0.4, z: Math.sin(a) }), speed: speed * (0.8 + Math.random() * 0.4), size, life: 0.7, spin: Math.random() * 360, color: ROCK, alpha: 1 });
  }
}

/**
 * 그림자 분신 (rpg:rfx_shade — 발 고정, 두건을 쓴 사람 모양 그림자 + 빛나는 테두리): pose 0 섬 · 1 돌진 · 2 베기 · 3 찌르기.
 * face = 분신이 보는 쪽 (수평) — 쓴 사람 화면에서 왼쪽을 보면 뒤집은 장. o.vel = 미끄러지는 속도 (칸/초)
 */
function shadeClone(player, dim, feet, face, pose, glow, core, o = {}) {
  let left = false;
  try {
    left = dot(flat(face), rightOf(flat(player.getViewDirection()))) < 0;
  } catch {
    left = false;
  }
  rfx2(dim, "shade", feet, { size: o.size ?? 0.95, life: o.life ?? 0.4, fade: o.fade ?? 0.5, grow: o.grow ?? 0, alpha: o.alpha ?? 0.92, cell: pose + (left ? 4 : 0), vel: o.vel }, glow, core, [0, RFX.shadeCore]);
}

/** 번개 구슬 (rpg:rfx_plasma — 4장이 돎). o.vel = 따라가는 속도 (칸/초) */
function plasmaOrb(dim, at, size, glow, o = {}) {
  rfx2(dim, "plasma", at, { size, life: o.life ?? 0.3, frames: 4, fps: o.fps ?? 18, loop: true, phase: Math.random() * 4, spin: Math.random() * 360, fade: o.fade ?? 0.4, grow: o.grow ?? 0, alpha: o.alpha ?? 1, vel: o.vel }, glow, WHITE, [0, RFX.plasmaCore]);
}

/** 얼음 결정 (rpg:rfx_crystal — 밑동 고정, 모양 3가지). 1인칭 · 3인칭 카메라에 가까우면 작게 */
function iceCrystal(player, dim, at, size, glow, o = {}) {
  const s = size * nearK(player, up(at, size), o.full ?? 4, 0.35);
  rfx2(dim, "crystal", at, { size: s, life: o.life ?? 1.0, grow: o.grow ?? 0.15, fade: o.fade ?? 0.3, cell: o.v ?? Math.floor(Math.random() * 3), alpha: o.alpha ?? 0.95 }, glow, WHITE, [0, RFX.crystalCore]);
}

/** 회오리 (rpg:rfx_vortex — 밑동 고정, 8장이 돎) */
function vortexAt(dim, at, size, color, o = {}) {
  rfx(dim, "vortex", at, { size, life: o.life ?? 0.4, color, alpha: o.alpha ?? 0.9, frames: 8, fps: o.fps ?? 20, loop: true, phase: o.phase ?? Math.random() * 8, fade: o.fade ?? 0.3, grow: o.grow ?? 0, vel: o.vel });
}

// ============================================================ 힘 (빨강·주황: 땅을 부수는 힘)
/** 대지 강타: 앞쪽 땅을 내려쳐 충격파 — v2.25.0 땅이 갈라지고 둘레에 바위 가시가 솟으며 돌이 튐 */
function groundSlam(player, r, P, k) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const c = add(player.location, f, k.dist);
  const [glow, core] = RUNE.color.str;
  arc(dim, up(add(player.getHeadLocation(), f, 1.6), -0.3), f, { shape: "heavy", flip: false, size: 2.2, life: 0.26, speed: 2, spin: -8, color: glow, aura: [1.0, 0.72, 0.3] });
  play(dim, "mace.smash_air", player.location, 0.8, 0.8);
  system.runTimeout(() => {
    if (!player.isValid) return;
    zonePulse(dim, c, glow, k.radius, 0.45);
    zonePulse(dim, c, core, k.radius * 0.55, 0.3);
    burst(dim, up(c, 0.4), glow, 1.3, 0.3);
    fx(dim, "rpg:ground_crack", up(c, 0.05), { dir: V_ZERO, spin: Math.random() * 360, size: k.radius * 1.1, life: 1.2, color: [0.36, 0.16, 0.06], alpha: 0.9 });
    rockRing(player, dim, c, k.radius * 0.7, 6, 0.55, glow, { life: 0.8 });
    rockBurst(dim, up(c, 0.4), 4, 5);
    fx(dim, "rpg:dust", up(c, 0.5), { size: 1.2, life: 0.7, color: [0.62, 0.48, 0.34], alpha: 0.55 });
    sparks(dim, up(c, 0.3), core, 6, 4);
    particle(dim, "minecraft:critical_hit_emitter", up(c, 0.6));
    play(dim, "mace.smash_ground", c, 0.9, 1);
    play(dim, "dig.stone", c, 0.6, 1);
    shake(player, 0.15, 0.15);
    for (const e of around(player, c, k.radius, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, sub(e.location, c), 0.3, k.lift);
    }
  }, 2);
  return true;
}

/** 회오리 베기: 제자리에서 gap 틱마다 hits 번 휘몰아치며 주위를 벰 (조금 끌어당김) — v2.25.0 둘레에 흙먼지 회오리 셋 · 땅의 소용돌이 · 휩쓸려 도는 돌 */
function whirlwind(player, r, P, k) {
  const [glow, core] = RUNE.color.str;
  const dust = [0.86, 0.56, 0.3];
  every(k.hits, k.gap, (i) => {
    if (!player.isValid) return;
    const dim = player.dimension;
    const c = player.location;
    for (let j = 0; j < 6; j++) {
      const a = (j / 6) * Math.PI * 2 + i * 0.5;
      const dir = { x: Math.cos(a + Math.PI / 2), y: 0, z: Math.sin(a + Math.PI / 2) };
      const at = { x: c.x + Math.cos(a) * 2.2, y: c.y + 1.0, z: c.z + Math.sin(a) * 2.2 };
      const draw = () => arc(dim, at, dir, { shape: "heavy", flip: i % 2 === 1, size: 2.0 + i * 0.2, life: 0.28, speed: 2.5, spin: 90, color: glow, aura: j % 2 ? core : undefined });
      if (j < 2) draw();
      else system.runTimeout(draw, Math.floor(j / 2));
    }
    // 흙먼지 회오리 셋이 몸 둘레 (반지름 radius × 0.8) 를 돎 — 몸 한가운데는 비움 (1인칭 카메라가 그 안에 들어가지 않게)
    for (let j = 0; j < 3; j++) {
      const a = (j / 3) * Math.PI * 2 + i * 1.3;
      const at = { x: c.x + Math.cos(a) * k.radius * 0.8, y: c.y, z: c.z + Math.sin(a) * k.radius * 0.8 };
      const tdir = { x: -Math.sin(a), y: 0, z: Math.cos(a) };
      vortexAt(dim, at, 0.85 + i * 0.1, dust, { life: 0.45, alpha: 0.8, vel: scale(tdir, 3) }); // 0.45초에 1.35칸 (범위 안에서 돎)
      fx(dim, "rpg:boulder", up(at, 0.5), { dir: unit({ x: tdir.x, y: 0.4, z: tdir.z }), speed: 4, size: 0.16, life: 0.45, spin: Math.random() * 360, color: ROCK, alpha: 1 });
    }
    fx(dim, "rpg:whirlpool", up(c, 0.1), { dir: V_ZERO, size: k.radius * 0.9, life: 0.5, spin: i * 60, speed: -420, color: dust, alpha: 0.5 });
    zonePulse(dim, c, glow, k.radius * (0.85 + 0.075 * i), 0.4);
    floorRune(dim, c, { size: k.radius * 0.8, life: 0.5, color: glow, alpha: 0.7, rate: 360, grow: 0.2 });
    play(dim, `item.trident.riptide_${Math.min(3, i + 1)}`, c, 1.0, 0.8);
    play(dim, "mace.smash_air", c, 0.9 + 0.1 * i, 0.7);
    play(dim, "mob.breeze.whirl", c, 0.8, 0.6);
    for (const e of around(player, c, k.radius)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, sub(c, e.location), 0.15, 0.1);
    }
  });
  return true;
}

/** 대지 균열: 앞으로 length 칸 땅이 갈라지며 바위가 솟구침 (선 가까이 width 칸 안의 적) — v2.25.0 바위 가시 그림 · 갈라진 땅 · 끝에서 바위가 터짐 */
function fissure(player, r, P, k) {
  const dim = player.dimension;
  const start = player.location;
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE.color.str;
  const hit = new Set();
  play(dim, "mace.heavy_smash_ground", start, 0.8, 1);
  shake(player, 0.2, 0.25);
  arc(dim, up(add(player.getHeadLocation(), f, 1.5), -0.3), f, { shape: "heavy", flip: true, size: 2.0, life: 0.24, speed: 2, spin: 6, color: glow, aura: core });
  const side = rightOf(f);
  for (let s = 1; s <= k.length; s++) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const p = add(start, f, s);
      fx(dim, "rpg:light_cut", up(p, 0.08), { dir: f, size: 0.64, thick: 0.48, life: 0.95, delay: 0.75, color: glow, alpha: 0.95 });
      if (s % 2 === 0) fx(dim, "rpg:ground_crack", up(p, 0.05), { dir: V_ZERO, spin: Math.random() * 360, size: 1.3, life: 1.2, color: [0.36, 0.16, 0.06], alpha: 0.85 });
      // 바위 가시 (바로 앞 1칸은 1인칭 화면을 가리므로 2칸부터): 갈라진 틈 양옆에서 솟음, 멀수록 높게
      for (const o of s >= 2 ? [-0.5, 0.5] : []) {
        const q = add(p, side, o + (Math.random() - 0.5) * 0.3);
        rockSpike(player, dim, q, (0.45 + s * 0.04) * (0.8 + Math.random() * 0.4), glow, { life: 0.95, fade: 0.3 });
      }
      burst(dim, up(p, 0.3), glow, 0.8, 0.25);
      if (s % 3 === 0) play(dim, "mace.smash_ground", p, 0.8 + s * 0.02, 0.8);
      for (const e of around(player, p, k.width, 3)) {
        if (hit.has(e.id)) continue;
        hit.add(e.id);
        if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, f, 0.2, k.lift);
      }
      if (s === k.length) {
        zonePulse(dim, p, glow, k.width * 1.8, 0.45);
        rockRing(player, dim, p, 1.3, 5, 0.75, glow, { life: 1.0 });
        rockBurst(dim, up(p, 0.5), 5, 6);
        sparks(dim, up(p, 0.4), core, 8, 5);
        play(dim, "random.explode", p, 1.2, 0.5);
      }
    }, Math.floor(s / 2));
  }
  return true;
}

/**
 * 거인의 일격: 앞쪽에 붉은 룬이 그려지고 (delay) 하늘에서 거대한 망치 (rpg:rfx_prop 망치 — 머리가 아래) 가 내려찍음 →
 * 망치가 박힌 채 남고 땅이 크게 갈라지며 둘레에 바위 가시가 솟고 돌이 튐 (v2.25.0)
 */
function titanSmash(player, r, P, k) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const c = add(player.location, f, k.dist);
  const [glow, core] = RUNE.color.str;
  for (let t = 0; t < k.delay; t += 2) {
    system.runTimeout(() => floorRune(dim, c, { size: k.radius * 0.9, life: 0.14, color: glow, alpha: 0.9, progress: (t + 2) / k.delay, rate: 0, spin: t * 4 }), t);
  }
  play(dim, "mob.ravager.roar", player.location, 1.2, 0.8);
  const fall = k.delay / 20;
  const H0 = 9;
  const size = 2.2 * nearK(player, up(c, 2), 5, 0.5);
  const rest = size * 0.72; // 망치 가운데 높이 (머리가 땅에 닿음)
  // 떨어지는 망치는 박힌 망치가 나타나는 틱 (delay − 1) 에 박힐 자리에 닿음 (두 망치가 겹침 — 하나가 위에 떠 보이지 않게)
  rfx2(dim, "prop", up(c, H0), { dir: V_DOWN, speed: (H0 - rest) / Math.max(0.05, fall - 0.05), size, life: fall, spin: 180 }, glow, core, RFX.hammer);
  fx(dim, "rpg:arrow_shot", up(c, 6), { dir: V_UP, speed: 0, size: 6, thick: 0.1, life: fall, color: glow, alpha: 0.6 }); // 떨어질 자리의 가는 빛
  system.runTimeout(() => rfx2(dim, "prop", up(c, rest), { size, life: 0.75, spin: 180, fade: 0.5 }, glow, core, RFX.hammer), k.delay - 1); // 박힌 망치 (1틱 먼저 — 깜빡임 없게)
  system.runTimeout(() => {
    if (!player.isValid) return;
    burst(dim, up(c, 0.6), glow, 3.0, 0.4);
    burst(dim, up(c, 0.6), core, 1.6, 0.3);
    shock(dim, c, glow, 2.0, 0.35);
    zonePulse(dim, c, core, k.radius * 0.7, 0.5);
    zonePulse(dim, c, glow, k.radius, 0.65);
    fx(dim, "rpg:ground_crack", up(c, 0.05), { dir: V_ZERO, spin: Math.random() * 360, size: k.radius * 1.3, life: 1.6, color: [0.36, 0.14, 0.05], alpha: 0.95 });
    rockRing(player, dim, c, k.radius * 0.62, 8, 0.8, glow, { life: 1.1, minDist: 1.8 });
    rockBurst(dim, up(c, 0.5), 8, 7, 0.3);
    fx(dim, "rpg:dust", up(c, 0.8), { size: 2.0, life: 0.9, color: [0.62, 0.48, 0.34], alpha: 0.6 });
    sparks(dim, up(c, 0.5), core, 10, 7);
    particle(dim, "minecraft:huge_explosion_emitter", up(c, 0.5));
    play(dim, "mace.heavy_smash_ground", c, 0.8, 1);
    play(dim, "random.explode", c, 0.8, 0.7);
    play(dim, "item.trident.thunder", c, 0.6, 0.6);
    shakeAround(dim, c, 12, 0.35, 0.3);
    for (const e of around(player, c, k.radius, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, sub(e.location, c), 0.6, k.lift);
    }
  }, k.delay);
  return true;
}

/**
 * 천붕: 조준한 곳에 거대한 룬 → 하늘에 거대한 빛의 검 (rpg:rfx_sword, 길이 HEAVEN_SWORD 칸) 이 나타나 칼끝을 아래로 천천히 내려오다가
 * 마지막 8틱에 떨어져 꽂힘 (칼끝이 땅속 1.5칸) → 땅이 갈라지고 바위가 튐 (반지름 radius, 위로 띄우고 둔화). 꽂힌 검은 1.6초 남았다 사라짐
 */
function heavenFall(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = RUNE.color.str;
  const gold = [1.0, 0.8, 0.35];
  const L = 9; // 검 길이 (칸)
  // 떠 있는 검 가운데 높이 (땅 위): 가까이 겨누면 낮게 (1인칭에서 칼끝이 보이게 — 12칸이면 11.4, 최대 18)
  const sky = Math.min(18, 6 + 0.45 * Math.hypot(g.x - player.location.x, g.z - player.location.z));
  const drop = 8; // 떨어지는 틱
  for (let t = 0; t < k.delay; t += 2) {
    system.runTimeout(() => {
      floorRune(dim, g, { size: k.radius, life: 0.14, color: glow, alpha: 0.9, progress: Math.min(1, (t + 2) / (k.delay * 0.7)), rate: 0, spin: t * 3 });
      if (t % 6 === 0) {
        const a = Math.random() * Math.PI * 2;
        fx(dim, "rpg:arrow_spark", { x: g.x + Math.cos(a) * k.radius, y: g.y + 0.2, z: g.z + Math.sin(a) * k.radius }, { dir: V_UP, speed: 3, size: 0.25, grow: 1.2, life: 0.6, color: gold, alpha: 1 });
      }
    }, t);
  }
  // 하늘에서 내려오는 가는 빛 (떨어질 자리) + 하늘의 검: 나타나 천천히 내려옴 (칼끝 아래)
  fx(dim, "rpg:arrow_shot", up(g, 14), { dir: V_UP, speed: 0, size: 14, thick: 0.12, life: k.delay / 20, color: gold, alpha: 0.6 });
  const hover = (k.delay - drop) / 20;
  rfx2(dim, "sword", up(g, sky + 1.5), { dir: V_DOWN, size: L / 2, life: hover + 0.05, speed: 1.5 / hover }, glow, WHITE); // + 1틱: 떨어지는 검이 나타나는 동안 겹침 (깜빡임 없게)
  fx(dim, "rpg:magic_circle_flash", up(g, sky + L / 2 + 1.6), { size: 2.2, life: 0.6, spin: Math.random() * 360, progress: 1, color: gold, alpha: 0.9 });
  play(dim, "beacon.power", g, 0.6, 1);
  play(dim, "mob.wither.spawn", player.location, 1.4, 0.35);
  // v2.27.0 2차 강화 — 예고: 금빛 하늘 (하늘빛) · 겨눈 땅의 성광 문양 (커지며 돎) · 검이 나오는 하늘 뒤쪽이 갈라짐 · 검이 떨어질 때 둘레의 빛기둥 넷
  ultSkyFor(dim, g, "heavenFall", k.delay + 40);
  domainSigil(dim, g, k.radius * 1.15, "holy", gold, WHITE, { life: (k.delay + 1) / 20, spinRate: 28, grow: 0.3, alpha: 0.78, fade: 0.08 });
  const rift = skyPoint(player, g, 6, 21);
  skyPlate(player, dim, "crack", rift, 6.5, gold, WHITE, { life: (k.delay + 10) / 20, grow: 0.4, fade: 0.35 });
  later(k.delay - drop, () => godRays(player, dim, g, k.radius * 0.85, 4, gold, WHITE, { h: 16, life: 0.6, jitter: 24 }));
  // 떨어짐: 하늘 자리 → 칼끝이 땅속 1.5칸 (가운데 = 땅 + L/2 - 1.5)
  const rest = L / 2 - 1.5;
  system.runTimeout(() => {
    rfx2(dim, "sword", up(g, sky), { dir: V_DOWN, size: L / 2, life: drop / 20, speed: (sky - rest) / (drop / 20) }, glow, WHITE);
    for (let s = 2; s < sky - rest; s += 3) {
      const delay = (s / (sky - rest)) * (drop / 20);
      fx(dim, "rpg:arrow_trail", up(g, sky - s + L / 2), { dir: V_DOWN, size: 1.6, thick: 0.9, life: delay + 0.3, delay, color: glow, alpha: 0.7 });
    }
    play(dim, "item.trident.riptide_3", g, 0.6, 1);
  }, k.delay - drop);
  // 꽂힌 검 (1.65초, 마지막 40% 동안 사라짐): 떨어지는 검이 끝나기 1틱 전에 나타남 (깜빡임 없게)
  system.runTimeout(() => rfx2(dim, "sword", up(g, rest), { dir: V_DOWN, size: L / 2, life: 1.65, fade: 0.4 }, glow, WHITE), k.delay - 1);
  system.runTimeout(() => {
    if (!player.isValid) return;
    const mid = up(g, 1.0);
    // v2.27.0 발동: 화면 번쩍임 · 갈라진 하늘의 섬광 → 충격 고리 · 문양이 번쩍 (1틱 뒤) → 빛나는 금 (2틱 뒤 — 이 틱의 파티클 예산)
    ultFlashFor(player, dim, g, "heavenFall");
    skyFlare(dim, rift, 4.6, gold, WHITE, { life: 0.6 });
    later(1, () => {
      novaRing(dim, g, 2.2, 3.2, gold, WHITE, { life: 0.5 });
      domainSigil(dim, g, k.radius * 1.15, "holy", gold, WHITE, { life: 0.6, grow: 0.18, alpha: 1, fade: 0.85 });
    });
    later(2, () => glowCrack(dim, g, k.radius * 1.2, [1.0, 0.55, 0.15], [1.0, 0.92, 0.7], { life: 2.4, fade: 0.6 }));
    // 칼밑의 빛
    rfx(dim, "prop", up(g, rest + L / 2 - 0.4), { size: 2.2, life: 0.5, cell: RFX.glint[0], color: WHITE, alpha: 1, grow: 0.6, fade: 0.6 });
    burst(dim, mid, gold, 5.5, 0.5);
    burst(dim, mid, glow, 3.6, 0.4);
    burst(dim, mid, WHITE, 2.4, 0.3);
    fx(dim, "rpg:arrow_spark", mid, { dir: V_UP, size: 4, grow: 2, life: 0.22, color: WHITE, alpha: 1 });
    for (let i = 0; i < 5; i++) system.runTimeout(() => shock(dim, g, i % 2 ? core : glow, 2.5 + i * 1.5, 0.55 + i * 0.1), i * 2);
    for (let i = 0; i < 12; i++) {
      const d = turn({ x: 1, y: 0, z: 0 }, i * 30 + Math.random() * 10);
      fx(dim, "rpg:light_cut", add(up(g, 0.08), d, 2.6), { dir: d, size: 2.4, thick: 0.45, life: 1.0, delay: 0.75, color: glow, alpha: 0.95 });
    }
    // 갈라진 땅 · 튀는 바위 · 먼지
    fx(dim, "rpg:ground_crack", up(g, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: k.radius * 1.4, life: 1.8, color: [0.36, 0.16, 0.06], alpha: 0.95 });
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * Math.PI * 2 + Math.random() * 0.4;
      fx(dim, "rpg:boulder", up(g, 0.6), { dir: unit({ x: Math.cos(a), y: 1.1, z: Math.sin(a) }), speed: 7 + Math.random() * 3, size: 0.3, life: 0.8, spin: Math.random() * 360, color: ROCK, alpha: 1 });
    }
    fx(dim, "rpg:dust", up(g, k.radius * 0.3), { size: k.radius * 0.8, life: 0.9, color: [0.62, 0.48, 0.34], alpha: 0.7 });
    sparks(dim, mid, gold, 18, 8);
    particle(dim, "minecraft:huge_explosion_emitter", mid);
    play(dim, "item.trident.thunder", g, 0.7, 1);
    play(dim, "random.explode", g, 0.6, 1);
    play(dim, "mace.heavy_smash_ground", g, 0.7, 1);
    system.runTimeout(() => play(dim, "block.bell.hit", g, 0.5, 1), 6);
    shakeAround(dim, g, 18, 0.45, 0.4);
    for (const e of around(player, g, k.radius, 4)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        knock(e, sub(e.location, g), 0.5, k.lift);
        effect(e, "slowness", 40, 2);
      }
    }
  }, k.delay);
  return true;
}

// ============================================================ 민첩 (초록: 빠른 칼날)
/** 질풍 베기: 앞으로 dash 칸 돌진 (벽 앞에서 멈춤), 지나간 길 양옆 width 칸의 적 — v2.25.0 지나간 길에 그림자 분신 잔상 셋 · 바람 · 끝에서 초승달 검기 */
function galeSlash(player, r, P, k) {
  const dim = player.dimension;
  const start = player.location;
  const f = flat(player.getViewDirection());
  const dist = dashDistance(player, f, k.dash);
  const end = add(start, f, dist);
  const [glow, core] = RUNE.color.dex;
  const shadeCol = [0.12, 0.45, 0.24];
  const targets = around(player, add(start, f, dist / 2), dist / 2 + k.width + 0.5).filter((e) => {
    const p = sub(e.location, start);
    const pad = bodyPad(e);
    const along = p.x * f.x + p.z * f.z;
    const across = Math.abs(p.x * f.z - p.z * f.x);
    return along >= -0.5 - pad && along <= dist + 1.0 + pad && across <= k.width + pad;
  });
  for (let s = 0.3; s <= dist; s += 0.8) {
    fx(dim, "rpg:arrow_trail", up(add(start, f, s), 1.0), { dir: f, size: 0.4, thick: 0.2, life: 0.45, color: glow, alpha: 0.85 });
  }
  // 잔상: 지나간 길에 돌진하는 그림자 분신 셋 (뒤로 갈수록 흐림 — 1인칭에서는 등 뒤라 안 보임)
  [0.2, 0.45, 0.7].forEach((q, j) => {
    if (dist * q < 0.4) return;
    shadeClone(player, dim, add(start, f, dist * q), f, 1, shadeCol, glow, { life: 0.3 + j * 0.08, alpha: 0.4 + j * 0.2, fade: 0.7 });
  });
  arc(dim, up(add(start, f, 1.2), 1.1), f, { shape: "thin", flip: false, size: 1.6, life: 0.24, speed: 4, spin: -20, color: glow, aura: core });
  sparks(dim, up(start, 0.5), glow, 6, 4);
  try {
    if (dist > 0.2) player.teleport(end, { keepVelocity: false, checkForBlocks: true });
  } catch {
    // 못 옮기면 제자리에서 벰
  }
  rfx2(dim, "wave", up(add(end, f, 1.9), 1.0), { size: 1.2, life: 0.22, grow: 0.3, fade: 0.6, spin: -15 }, glow, core);
  play(dim, "item.trident.riptide_1", start, 1.4, 0.8);
  play(dim, "mob.enderdragon.flap", start, 1.8, 0.5);
  let n = 0;
  for (const e of targets) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) {
      // 맞은 자리 (파티클 예산): X 12명 · 불꽃 4명까지
      if (n < 12) fx(dim, "rpg:slash_x", bodyPoint(e), { dir: f, size: 0.9, life: 0.25, color: glow, alpha: 1 });
      if (n < 4) sparks(dim, bodyPoint(e), core, 4, 4);
      n++;
    }
  }
  return true;
}

/** 칼날 부채: 칼날 blades 개를 부채꼴(spread 도)로 던짐. 칼날마다 처음 닿는 적, 적마다 한 번 — v2.25.0 날아가는 빛의 칼 (rpg:rfx_sword 작게) */
function bladeFan(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const view = player.getViewDirection();
  const f = flat(view);
  const [glow, core] = RUNE.color.dex;
  const hit = new Set();
  const cands = around(player, player.location, k.range + 1, 4);
  for (let i = 0; i < k.blades; i++) {
    const deg = -k.spread / 2 + (k.spread * i) / Math.max(1, k.blades - 1);
    const h = turn(f, deg);
    const d = unit({ x: h.x, y: view.y * 0.6, z: h.z });
    let best;
    let bestT = k.range;
    for (const e of cands) {
      const q = bodyToward(e, head, d);
      const p = sub(q, head);
      const t = dot(p, d);
      if (t < 0 || t > bestT) continue;
      if (len(sub(p, scale(d, t))) > 0.9 + bodyPad(e)) continue;
      if (!lineOfSight(player, head, q)) continue;
      best = e;
      bestT = t;
    }
    const L = best ? bestT : k.range;
    const life = Math.max(0.05, L / 28);
    const from = add(up(head, -0.25), d, 1.0);
    const speed = Math.max(0, (L - 1.2) / life);
    // 칼끝이 날아가는 쪽 (손잡이 뒤) — 눈에서 1칸 앞부터
    rfx2(dim, "sword", from, { dir: d, speed, size: 0.42, life }, glow, core);
    for (let s2 = 1.0; s2 < L; s2 += 1.6) {
      const delay = (s2 / L) * life;
      fx(dim, "rpg:arrow_trail", add(from, d, s2), { dir: d, size: 0.5, thick: 0.1, life: delay + 0.22, delay, color: glow, alpha: 0.7 });
    }
    if (best && !hit.has(best.id)) {
      hit.add(best.id);
      const target = best;
      system.runTimeout(() => {
        if (!player.isValid || !target.isValid) return;
        if (runeHit(player, target, P, k.mult, r.stat) > 0) {
          fx(dim, "rpg:slash_x", bodyPoint(target), { dir: d, size: 0.8, life: 0.22, color: glow, alpha: 1 });
          sparks(dim, bodyPoint(target), core, 3, 4);
          play(dim, "item.trident.hit", bodyPoint(target), 1.6, 0.5);
        }
      }, Math.max(1, Math.round(life * 20)));
    }
  }
  arc(dim, up(add(head, f, 1.2), -0.2), f, { shape: "thin", flip: false, size: 1.6, life: 0.2, speed: 4, spin: 90, color: glow });
  play(dim, "random.bow", head, 1.8, 0.8);
  play(dim, "item.trident.throw", head, 1.5, 0.7);
  return true;
}

/**
 * 그림자 습격: 앞쪽 cone 도·range 칸의 적 targets 명에게 그림자 분신이 차례로 날아가 벰 — v2.25.0 분신 그림 (rpg:rfx_shade):
 * 내 옆에서 돌진하는 분신이 적의 옆으로 미끄러져 가서 → 그 자리에서 베는 분신 + X (가까운 적의 X 는 작게)
 */
function shadowRaid(player, r, P, k) {
  const list = coneTargets(player, k.range, k.cone, k.targets);
  if (!list.length) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.dex;
  const shade = [0.08, 0.3, 0.16];
  const cloneCol = [0.12, 0.45, 0.24]; // 분신 몸 (그림자보다 밝게 — 풀밭 위에서도 사람 모양이 보이게)
  const c0 = player.location;
  shock(dim, c0, shade, 1.6, 0.4);
  sparks(dim, up(c0, 1), shade, 8, 3);
  play(dim, "mob.vex.charge", c0, 1.3, 0.9);
  list.forEach((e, i) => {
    system.runTimeout(() => {
      if (!player.isValid || !e.isValid) return;
      const fv = flat(player.getViewDirection());
      // 분신이 튀어나가는 곳: 옆 1.1칸 · 앞 1.2칸 (1인칭 화면 아래 구석을 덮지 않게 — 가까우면 작게)
      const fromFeet = add(add(player.location, rightOf(fv), (i % 2 ? 1 : -1) * 1.1), fv, 1.2);
      const toward = flat(sub(e.location, fromFeet));
      // 베는 자리: 적의 옆 (내 쪽 선에서 비켜 — 가까운 적이어도 분신이 1인칭 눈앞을 덮지 않게)
      const flank = scale(rightOf(toward), i % 2 ? 1 : -1);
      const toFeet = add(e.location, flank, 0.9 + bodyPad(e));
      // 날아가는 시간 (= 베는 틱) 은 v2.24.0 그대로: 내 옆 0.8칸 · 몸 높이에서 적의 몸까지 30칸/초
      const L0 = len(sub(bodyPoint(e), up(add(player.location, rightOf(fv), (i % 2 ? 1 : -1) * 0.8), 1.0)));
      const life = Math.max(0.05, L0 / 30);
      const hitT = Math.max(1, Math.round(life * 20));
      shadeClone(player, dim, fromFeet, toward, 1, cloneCol, glow, { life, fade: 0.15, alpha: 0.85, size: 1.1 * nearK(player, up(fromFeet, 1), 3.5, 0.55), vel: scale(sub(toFeet, fromFeet), 1 / life) });
      beam(dim, up(fromFeet, 1.0), bodyPoint(e), shade, { half: 0.32, thick: 0.25, life: 0.35, sweep: life, alpha: 0.6 });
      play(dim, "mob.endermen.portal", up(fromFeet, 1), 1.6, 0.6);
      // 도착: 적 옆에서 베는 분신 (돌진 분신이 끝나기 1틱 전에 나타남 — 이어지게)
      system.runTimeout(() => {
        if (!player.isValid || !e.isValid) return;
        const at2 = add(e.location, flank, 0.9 + bodyPad(e));
        shadeClone(player, dim, at2, scale(flank, -1), 2, cloneCol, glow, { life: 0.42, fade: 0.5, size: 1.1 * nearK(player, up(at2, 1), 3.5, 0.55) });
      }, Math.max(1, hitT - 1));
      system.runTimeout(() => {
        if (!player.isValid || !e.isValid) return;
        const at = bodyPoint(e);
        const v = unit(sub(at, player.getHeadLocation()));
        const xs = nearK(player, add(at, v, -0.2), 3, 0.35); // 가까운 적의 X 는 작게 (1인칭 화면을 덮지 않게)
        burst(dim, add(at, v, -0.05), shade, 1.0 * xs, 0.28);
        fx(dim, "rpg:slash_x", add(at, v, -0.2), { dir: v, size: 1.0 * xs, life: 0.26, color: glow, alpha: 1 });
        cutMark(dim, at, v, 40, 0.18, glow, core, 1.1 * xs);
        cutMark(dim, at, v, -40, 0.18, glow, core, 1.1 * xs);
        sparks(dim, at, core, 5, 5);
        play(dim, "game.player.attack.critical", at, 1.3, 0.8);
        runeHit(player, e, P, k.mult, r.stat);
      }, hitT);
    }, i * k.gap);
  });
  return true;
}

/**
 * 칼바람 폭풍: 빨라지고, gap 틱마다 hits 번 주위 radius 칸을 벰 — v2.25.0 빛의 칼 셋이 몸 둘레 (반지름 2.1) 를 돌고 (앞을 지날 때는 흐리게),
 * 벨 때마다 둘레에 작은 회오리 둘
 */
function bladeStorm(player, r, P, k) {
  const [glow, core] = RUNE.color.dex;
  effect(player, "speed", k.speedTicks, 1);
  const total = (k.hits - 1) * k.gap + 6;
  for (let t = 0; t < total; t += 3) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const dim = player.dimension;
      const c = player.location;
      const fc = facingOf(player);
      const vel = bodyVel(player); // 빨라진 몸 (이동 속도 II) 을 따라옴
      for (let j = 0; j < 3; j++) {
        // 한 틱에 12° (다시 그릴 때마다 36° — 120° 간격의 칼 셋이 멈춰 보이거나 뒤로 도는 것처럼 보이지 않게)
        const a = (t * 12 + j * 120) * (Math.PI / 180);
        const tang = { x: -Math.sin(a), y: 0, z: Math.cos(a) };
        const at = { x: c.x + Math.cos(a) * 2.1, y: c.y + 0.7 + j * 0.4, z: c.z + Math.sin(a) * 2.1 };
        const ahead = dot(flat(sub(at, c)), fc) > 0.4; // 1인칭 화면 앞을 지나는 칼은 흐리게
        rfx2(dim, "sword", at, { dir: tang, size: 0.5, life: 0.17, alpha: ahead ? 0.45 : 0.95, vel }, glow, core);
        if (j === 0) arc(dim, at, tang, { shape: "thin", flip: false, size: 1.8, life: 0.2, speed: 3, spin: 90, color: glow, core: true });
      }
    }, t);
  }
  every(k.hits, k.gap, (i) => {
    if (!player.isValid) return;
    const dim = player.dimension;
    const c = player.location;
    zonePulse(dim, c, glow, k.radius, 0.35);
    for (let j = 0; j < 2; j++) {
      const a = i * 1.7 + j * Math.PI;
      vortexAt(dim, { x: c.x + Math.cos(a) * k.radius * 0.85, y: c.y, z: c.z + Math.sin(a) * k.radius * 0.85 }, 0.7, glow, { life: 0.45, alpha: 0.7 });
    }
    sparks(dim, up(c, 1), core, 4, 5);
    play(dim, "mob.breeze.whirl", c, 1.2 + i * 0.05, 0.8);
    let n = 0;
    for (const e of around(player, c, k.radius)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0 && n++ < 12) fx(dim, "rpg:slash_x", bodyPoint(e), { dir: flat(sub(e.location, c)), size: 0.7, life: 0.2, color: glow, alpha: 1 }); // X 는 12명까지 (파티클 예산)
    }
    if (i === k.hits - 1) play(dim, "wind_charge.burst", c, 1.2, 0.8);
  });
  return true;
}

/**
 * 무영검: 앞쪽 적을 멈추고 (freeze) 적마다 화면에서 그 적을 둘러싼 빛의 칼 (rpg:rfx_sword 작게, 칼끝이 적 쪽) 이 하나씩 나타남 →
 * delay 틱에 칼이 한꺼번에 꽂히며 터짐 (적마다 큰 X · 초승달 검기). 적이 많으면 칼 수를 줄이고 터지는 틱을 셋으로 나눔 (모바일 파티클)
 */
function shadowless(player, r, P, k) {
  const list = coneTargets(player, k.range, k.cone, k.maxTargets);
  if (!list.length) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const view = player.getViewDirection();
  const f = flat(view);
  const [glow, core] = RUNE.color.dex;
  // 칼을 뽑는 한 줄기 빛 (화면을 가로지름) + 시간이 멈춘 고리
  fx(dim, "rpg:light_cut", up(add(head, f, 2.2), -0.2), { dir: rightOf(f), size: 3.2, thick: 0.3, life: 0.5, delay: 0.3, color: core, alpha: 1 });
  ring(dim, add(head, f, 2.0), view, glow, 2.2, 0.8);
  play(dim, "chime.amethyst_block", head, 0.8, 1);
  play(dim, "mob.evocation_illager.cast_spell", head, 1.6, 0.6);
  // v2.27.0 2차 강화 — 예고: 시간이 멈춤 (잿빛 하늘 · 짧은 흰 번쩍임) · 발밑의 칼날 문양 (돌지 않음) · 대상들 뒤 하늘에 멈춘 비전 문양 → 끝에 깨짐
  const c0 = { ...player.location };
  const mid0 = list.reduce((a, e) => ({ x: a.x + e.location.x / list.length, y: a.y + e.location.y / list.length, z: a.z + e.location.z / list.length }), { x: 0, y: 0, z: 0 });
  ultSkyFor(dim, c0, "shadowless", k.delay + 24);
  ultFlashFor(player, dim, c0, "shadowless", "still", { time: { fadeInTime: 0.02, holdTime: 0, fadeOutTime: 0.3 } });
  domainSigil(dim, c0, 6.5, "blade", glow, WHITE, { life: (k.delay + 2) / 20, spinRate: 0, alpha: 0.7, fade: 0.15 });
  const still = skyPoint(player, mid0, 6, 20);
  skyPlate(player, dim, "sigil", still, 5.2, [0.78, 0.84, 0.9], WHITE, { cell: RFX.sigil.arcane, life: (k.delay - 1) / 20, alpha: 0.75, fade: 0.1 });
  later(k.delay - 2, () => skyPlate(player, dim, "crack", still, 5.8, glow, WHITE, { life: 0.5, grow: 0.5, fade: 0.6 })); // 멈춘 시간이 깨짐
  for (const e of list) {
    effect(e, "slowness", k.freeze, 10);
    effect(e, "weakness", k.freeze, 2);
  }
  const blades = Math.max(2, Math.min(6, Math.floor(24 / list.length)));
  const R = 1.45;
  const spots = new Map(); // 적 → [{ang, at}] (마지막에 꽂힐 자리)
  for (let c = 0; c < blades; c++) {
    later(2 + Math.round((c * (k.delay - 6)) / blades), () => {
      if (!player.isValid) return;
      const eye = player.getHeadLocation();
      list.forEach((e, ei) => {
        if (!e.isValid) return;
        const at = bodyPoint(e);
        const v = unit(sub(at, eye));
        const ang = (360 / blades) * c + ei * 23 + 90;
        const out = screenAngle(v, ang);
        const pos = add(add(at, out, R), v, -0.3);
        const hold = (k.delay - (2 + Math.round((c * (k.delay - 6)) / blades))) / 20 + 0.05 + (ei % 3) / 20; // 그 적을 찌르는 틱까지 (i % 3 틱 늦음)
        rfx2(dim, "sword", pos, { dir: scale(out, -1), size: 0.62, life: hold }, glow, WHITE);
        const arr = spots.get(e.id) ?? [];
        arr.push(out);
        spots.set(e.id, arr);
      });
      play(dim, "chime.amethyst_block", eye, 1.2 + c * 0.12, 0.45);
    });
  }
  // 칼자국 몇 개 (멈춘 몸 위)
  for (let c = 0; c < 3; c++) {
    later(4 + c * 6, () => {
      if (!player.isValid) return;
      const eye = player.getHeadLocation();
      for (const e of list) {
        if (!e.isValid) continue;
        const at = bodyPoint(e);
        const v = unit(sub(at, eye));
        const hold = (k.delay - 4 - c * 6) / 20;
        fx(dim, "rpg:light_cut", add(at, screenOffset(v, c * 3), 1), { dir: screenAngle(v, c * 60 + 20), size: 0.9, thick: 0.24, life: hold + 0.16, delay: hold, color: glow, alpha: 1 });
      }
    });
  }
  list.forEach((e, i) => {
    later(k.delay + (i % 3), () => {
      if (!player.isValid || !e.isValid) return;
      const eye = player.getHeadLocation();
      const at = bodyPoint(e);
      const v = unit(sub(at, eye));
      for (const out of spots.get(e.id) ?? []) rfx(dim, "sword", add(at, out, R), { dir: scale(out, -1), size: 0.62, life: 0.1, speed: R / 0.1, color: glow, cell: RFX.sword[0] });
      if (runeHit(player, e, P, k.mult, r.stat) <= 0) return;
      fx(dim, "rpg:slash_x", add(at, v, -0.2), { dir: v, size: 1.6, life: 0.3, color: WHITE, alpha: 1 });
      rfx2(dim, "wave", add(at, v, -0.3), { size: 1.25, life: 0.3, spin: (i * 47) % 180, grow: 0.3, fade: 0.5 }, glow, WHITE);
      burst(dim, add(at, v, -0.15), glow, 1.1, 0.3);
      sparks(dim, at, core, 6, 6);
      fx(dim, "rpg:arrow_shot", up(e.location, 1.5), { dir: V_UP, speed: 0, size: 1.6, thick: 0.4, life: 0.36, color: glow, alpha: 0.85 });
    });
  });
  later(k.delay, () => {
    if (!player.isValid) return;
    const eye = player.getHeadLocation();
    play(dim, "firework.large_blast", eye, 1.2, 1);
    play(dim, "item.trident.thunder", eye, 1.4, 0.5);
    // v2.27.0 발동: 화면 번쩍임 · 깨진 하늘의 섬광 → 발밑에서 퍼지는 충격 고리 (1틱 뒤)
    ultFlashFor(player, dim, eye, "shadowless");
    skyFlare(dim, still, 4, glow, WHITE, { life: 0.5 });
    later(1, () => novaRing(dim, c0, 1.5, 5, glow, WHITE, { life: 0.5 }));
    shakeAround(dim, eye, 10, 0.25, 0.25);
  });
  return true;
}

/** 무영검 칼자국 자리를 조금씩 흩뜨림 (화면 기준) */
function screenOffset(v, c) {
  const side = unit(Math.abs(v.y) > 0.95 ? { x: 1, y: 0, z: 0 } : { x: -v.z, y: 0, z: v.x });
  const s = ((c % 3) - 1) * 0.18;
  const h = ((Math.floor(c / 3) % 3) - 1) * 0.25;
  return { x: side.x * s, y: h, z: side.z * s };
}

/** 보는 방향 v 에 수직인 면에서 ang 도 방향 */
function screenAngle(v, ang) {
  const side = unit(Math.abs(v.y) > 0.95 ? { x: 1, y: 0, z: 0 } : { x: -v.z, y: 0, z: v.x });
  const upv = unit({ x: side.y * v.z - side.z * v.y, y: side.z * v.x - side.x * v.z, z: side.x * v.y - side.y * v.x });
  const a = (ang * Math.PI) / 180;
  return unit(add(scale(side, Math.cos(a)), upv, Math.sin(a)));
}

/** 방패 문장 (rpg:rfx_shield — 화면을 봄): 빛 + 흰 테두리. 1인칭 · 3인칭 카메라에 가까우면 작게 */
function shieldEmblem(player, dim, at, size, glow, o = {}) {
  const s = size * (o.full ? nearK(player, at, o.full, o.min ?? 0.4) : 1);
  rfx2(dim, "shield", at, { size: s, life: o.life ?? 0.6, grow: o.grow ?? 0, fade: o.fade ?? 0.4, alpha: o.alpha ?? 0.95, spin: o.spin ?? 0, vel: o.vel, dir: o.dir, speed: o.speed }, glow, o.core ?? WHITE);
}

/** 육각 판 (rpg:rfx_hex — dir 에 수직인 면, dir = 판이 보는 쪽). o.speed < 0 = 판이 보는 반대쪽 (안쪽) 으로 움직임, o.coreAlpha = 테두리만 다른 불투명도 */
function hexPlate(dim, at, dir, size, glow, o = {}) {
  rfx2(dim, "hex", at, { dir, size, life: o.life ?? 0.4, speed: o.speed ?? 0, grow: o.grow ?? 0, fade: o.fade ?? 0.4, alpha: o.alpha ?? 0.9, spin: o.spin ?? 0, vel: o.vel }, glow, o.core ?? WHITE, undefined, o.coreAlpha);
}

/** 머리 위 h 칸 (표식 자리) */
function overHead(e, h = 0.55) {
  try {
    const hd = e.getHeadLocation();
    return { x: e.location.x, y: hd.y + h, z: e.location.z };
  } catch {
    return up(e.location, 2.3);
  }
}

/** 몹이 움직이는 속도 (칸/초, 수평만) — 적에게 붙인 그림 (도발 표식 · 번개 구슬) 이 적을 따라가게 */
function entVel(e) {
  try {
    const v = e.getVelocity();
    return { x: v.x * 20, y: 0, z: v.z * 20 };
  } catch {
    return V_ZERO;
  }
}

// ============================================================ 체력 (분홍: 지키고 버팀)
/**
 * 수호의 외침: 주위 적에게 피해(→ 나를 노림), ticks 동안 받는 피해 -reduce — v2.25.0 앞 머리 위에 커다란 방패 문장이 번쩍이고,
 * 맞은 적의 머리 위에 작은 방패 표식 (도발), 버티는 동안 몸 양옆 뒤쪽에 작은 방패 둘이 따라다님
 */
function guardShout(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const [glow, core] = RUNE.color.vit;
  const eye = player.getHeadLocation();
  ring(dim, eye, player.getViewDirection(), glow, 1.4, 0.5, 4);
  zonePulse(dim, c, glow, k.radius, 0.55);
  zonePulse(dim, c, core, k.radius * 0.5, 0.4);
  floorRune(dim, c, { size: 1.6, life: 1.0, color: glow, alpha: 0.85, rate: 120, grow: 0.3 });
  // 큰 방패 문장: 앞 1.8칸 · 머리 위쪽 (1인칭 화면 위쪽에 보이고, 3인칭에서는 몸 앞 위)
  const em = up(add(c, facingOf(player), 1.8), 2.35);
  shieldEmblem(player, dim, em, 0.6, glow, { life: 0.8, grow: 0.35, fade: 0.5, alpha: 0.85, vel: bodyVel(player), full: 3, min: 0.45 });
  sparks(dim, up(c, 1), core, 8, 4);
  play(dim, "mob.ravager.roar", eye, 1.4, 0.8);
  play(dim, "random.anvil_land", c, 0.6, 0.4);
  setGuard(player, k.ticks, k.reduce, undefined);
  // 버티는 동안: 몸 양옆 뒤쪽 (보는 쪽에서 ±115°) 의 작은 방패 둘 (1인칭 화면 밖 · 3인칭에서 보임). 10틱마다 다시 그림 (몸을 따라옴)
  for (let t = 4; t < k.ticks; t += 10) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const pc = player.location;
      const f = facingOf(player);
      const vel = bodyVel(player);
      for (const sd of [-1, 1]) {
        const d = turn(f, sd * 115);
        shieldEmblem(player, player.dimension, up(add(pc, d, 0.8), 1.15), 0.3, glow, { life: 0.55, fade: 0.15, alpha: 0.85, vel });
      }
    }, t);
  }
  let marked = 0;
  for (const e of around(player, c, k.radius)) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) {
      // 번쩍임 16명 · 도발 표식 (머리 위 작은 방패 1초) 8명까지 (파티클 예산)
      if (marked < 16) burst(dim, bodyPoint(e), glow, 0.6, 0.25);
      if (marked++ < 8) shieldEmblem(player, dim, overHead(e), 0.3, glow, { life: 1.0, fade: 0.35, grow: 0.2, vel: entVel(e), full: 3, min: 0.4 });
    }
  }
  return true;
}

/**
 * 생명의 샘: pulses 번 나눠 체력 회복 (최대 체력 × heal × 체력 비례), 주위 아군은 share 만큼 — v2.25.0 몸 앞 양옆에 빛의 샘 둘이 솟음
 * (rpg:rfx_fountain — 8장이 흐름, 몸을 따라옴. 1인칭에서는 화면 아래 양쪽에 보임)
 */
function lifeSpring(player, r, P, k) {
  const [glow, core] = RUNE.color.vit;
  const green = [0.5, 1.0, 0.6];
  const amountOf = (pl) => (hpState(pl).max * k.heal * supportScale(player, r.family)) / k.pulses;
  const dim0 = player.dimension;
  floorRune(dim0, player.location, { size: 2.6, life: (k.pulses * k.gap) / 20 + 0.4, color: glow, alpha: 0.85, rate: 40, grow: 0.1 });
  play(dim0, "beacon.power", player.location, 1.5, 0.8);
  play(dim0, "bucket.empty_water", player.location, 1.2, 0.6);
  every(k.pulses, k.gap, (i) => {
    if (!player.isValid) return;
    const dim = player.dimension;
    const c = player.location;
    const f = facingOf(player);
    const vel = bodyVel(player);
    const healed = healBy(player, amountOf(player));
    if (healed > 0) showDamageText(player, healed, "heal");
    selfCue(player, green, glow, i === 0);
    // 빛의 샘 둘 (보는 쪽에서 ±42°, 1.8칸 — 1인칭 화면 아래 양쪽): 다음 회복까지 이어짐 (+0.1초 겹침 — 깜빡임 없게), 마지막은 천천히 사라짐
    const last = i === k.pulses - 1;
    for (const sd of [-1, 1]) {
      const at = add(c, turn(f, sd * 42), 1.8);
      rfx(dim, "fountain", at, { size: 1.0, life: last ? 0.8 : k.gap / 20 + 0.1, color: sd > 0 ? green : core, alpha: 0.95, frames: 8, fps: 16, loop: true, phase: i * 3 + (sd > 0 ? 4 : 0), fade: last ? 0.5 : 0.05, grow: i === 0 ? 0.15 : 0, vel });
    }
    for (let j = 0; j < 6; j++) {
      const a = (j / 6) * Math.PI * 2 + i;
      fx(dim, "rpg:arrow_spark", { x: c.x + Math.cos(a) * 0.8, y: c.y + 0.2, z: c.z + Math.sin(a) * 0.8 }, { dir: V_UP, speed: 2.2, size: 0.24, grow: 1.4, life: 0.8, color: j % 2 ? green : glow, alpha: 1 });
    }
    zonePulse(dim, c, green, k.radius, 0.5); // 나눠 받는 아군 범위
    particle(dim, "minecraft:heart_particle", up(c, 2.1));
    play(dim, "random.orb", c, 0.8 + i * 0.1, 0.6);
    let near = [];
    try {
      near = dim.getPlayers({ location: c, maxDistance: k.radius });
    } catch {
      near = [];
    }
    for (const pl of near) {
      if (pl.id === player.id || !canAssist(player, pl)) continue; // v2.26.0 결투 중인 사람 (내 결투 상대 · 남의 결투) 은 나눠 받지 않음
      const got = healBy(pl, amountOf(pl) * k.share);
      if (got > 0) {
        showDamageText(pl, got, "heal");
        particle(dim, "minecraft:heart_particle", up(pl.location, 2.1));
        // 나눠 받은 아군: 발밑에서 작은 샘
        rfx(dim, "fountain", pl.location, { size: 0.45, life: 0.6, color: green, alpha: 0.75, frames: 8, fps: 16, loop: true, phase: Math.floor(Math.random() * 8), fade: 0.4 });
      }
    }
    if (last) sparks(dim, up(c, 1), core, 8, 3);
  });
  return true;
}

/**
 * 강철 피부: 주위 충격 + ticks 동안 받는 피해 -reduce, 때린 몹에게 위력 × reflect 반사 — v2.25.0 쇳빛 육각 판 (rpg:rfx_hex, 분홍빛 테두리) 여덟 장이
 * 몸 옆 · 뒤 (±100° ~ ±178° — 1인칭 화면에는 들지 않음) 에서 몸으로 모여들어 붙고 번쩍임 → 버티는 동안 몸 옆 · 뒤에서 판이 반짝임
 */
function ironSkin(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const [glow, core] = RUNE.color.vit;
  const gun = [0.5, 0.56, 0.66]; // 쇳빛 판 + 분홍빛 흰 테두리 (체력 계열 색)
  const f = facingOf(player);
  const vel = bodyVel(player);
  const gather = 0.2; // 모여드는 초
  const R0 = 1.9;
  const R1 = 0.55;
  const plates = [];
  for (let i = 0; i < 8; i++) {
    const deg = (i % 2 ? 1 : -1) * (100 + Math.floor(i / 2) * 26); // 옆 · 뒤 (±100° ~ ±178° — 1인칭 화면에는 들지 않음)
    const d = turn(f, deg);
    const y = 0.5 + (i % 3) * 0.55;
    plates.push({ d, y, spin: i * 20 });
    hexPlate(dim, up(add(c, d, R0), y), d, 0.36, gun, { speed: -(R0 - R1) / gather, life: gather, fade: 0, alpha: 0.95, spin: i * 20, vel, core });
  }
  // 몸에 붙은 판 (모여든 자리 — 날아온 판이 끝나기 1틱 전에 나타남, 0.4초 남았다 사라짐)
  system.runTimeout(() => {
    if (!player.isValid) return;
    const pc = player.location;
    const pv = bodyVel(player);
    for (const q of plates) hexPlate(player.dimension, up(add(pc, q.d, R1), q.y), q.d, 0.36, gun, { life: 0.4, fade: 0.6, alpha: 0.95, spin: q.spin, vel: pv, core });
  }, Math.max(1, Math.round(gather * 20) - 1));
  play(dim, "armor.equip_iron", c, 0.8, 1);
  play(dim, "random.anvil_use", c, 1.2, 0.6);
  play(dim, "mob.irongolem.repair", c, 1.0, 0.8);
  setGuard(player, k.ticks, k.reduce, { amount: P * k.reflect, style: "rune" });
  // 판이 몸에 붙는 순간: 강철 빛 · 충격
  system.runTimeout(() => {
    if (!player.isValid) return;
    const pc = player.location;
    burst(dim, up(pc, 1), STEEL, 1.6, 0.35);
    selfCue(player, STEEL, WHITE);
    zonePulse(dim, pc, STEEL, k.radius, 0.45);
    sparks(dim, up(pc, 1), STEEL, 10, 4);
    play(dim, "random.anvil_land", pc, 1.6, 0.5);
  }, Math.round(gather * 20));
  for (let t = 10; t < k.ticks; t += 10) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const pc = player.location;
      const pf = facingOf(player);
      const pv = bodyVel(player);
      // 몸 옆 · 뒤의 작은 강철 판 둘 (번갈아 양쪽)
      for (let j = 0; j < 2; j++) {
        const d = turn(pf, (j ? 1 : -1) * (100 + Math.random() * 60));
        hexPlate(player.dimension, up(add(pc, d, 0.45), 0.5 + Math.random() * 1.1), d, 0.3, gun, { life: 0.45, fade: 0.5, alpha: 0.9, grow: 0.2, vel: pv, core });
      }
      if (t % 20 === 0) sparks(player.dimension, up(pc, 1), glow, 3, 2);
    }, t);
  }
  system.runTimeout(() => {
    if (player.isValid) play(player.dimension, "armor.equip_iron", player.location, 0.6, 0.6);
  }, k.ticks);
  for (const e of around(player, c, k.radius)) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, sub(e.location, c), 0.5, 0.2);
  }
  return true;
}

/**
 * 불굴의 방벽: 선 자리에 방벽 (ticks). 안의 아군은 받는 피해 -reduce, 안의 적은 pulse 틱마다 피해 + 밀려남 —
 * v2.25.0 둘레에 빛의 육각 판 12장이 벽을 이룸 (판마다 바깥을 봄, 금빛 셋째마다 — 밝은 테두리 · 옅은 판). 쓴 사람이 보는 쪽의 판은 테두리만,
 * 카메라 가까이의 판은 흐리게
 */
function bulwark(player, r, P, k) {
  const dim = player.dimension;
  const c = { ...player.location };
  const [glow, core] = RUNE.color.vit;
  const gold = [1.0, 0.82, 0.45];
  const N = 12;
  const plate = (2 * Math.PI * k.radius) / N / 2 / 0.785; // 판 반 크기 (육각 판의 너비 = 크기 × 2 × 0.785 → 이웃과 맞닿음)
  floorRune(dim, c, { size: k.radius, life: k.ticks / 20, color: glow, alpha: 0.75, rate: 20, grow: 0 });
  play(dim, "beacon.activate", c, 1.0, 1);
  play(dim, "random.totem", c, 1.6, 0.3);
  for (let t = 0; t < k.ticks; t += 10) {
    system.runTimeout(() => {
      const first = t === 0;
      let eye;
      let view;
      try {
        eye = player.getHeadLocation();
        view = flat(player.getViewDirection());
      } catch {
        eye = undefined;
      }
      for (let j = 0; j < N; j++) {
        const a = (j / N) * Math.PI * 2;
        const d = { x: Math.cos(a), y: 0, z: Math.sin(a) };
        const p = up(add(c, d, k.radius), plate * 0.93);
        // 카메라 (1인칭 눈 · 3인칭 뒤 4.5칸) 가까이의 판은 흐리게 — 화면을 덮지 않게
        const near = player.isValid ? Math.max(0.15, Math.min(1, (camDist(player, p) - 1) / 2.5)) : 1;
        // 쓴 사람이 보는 쪽 (±60°) 의 판은 테두리만 (1인칭에서 벽 너머의 적 · 조준점이 보이게)
        const front = eye && dot(unit(flat(sub(p, eye))), view) > 0.5;
        hexPlate(dim, p, d, plate, j % 3 ? glow : gold, { life: 0.55, fade: t + 10 >= k.ticks ? 0.4 : 0, alpha: (front ? 0.1 : 0.3) * near, coreAlpha: (front ? 0.7 : 0.95) * near, grow: first ? 0.08 : 0, core });
      }
      zonePulse(dim, up(c, plate * 1.85), gold, k.radius, 0.55); // 위쪽 빛 고리 (방벽 지붕)
    }, t);
  }
  const pulses = Math.floor(k.ticks / k.pulse);
  for (let i = 0; i < pulses; i++) {
    system.runTimeout(() => {
      zonePulse(dim, c, i % 2 ? core : glow, k.radius, 0.5);
      play(dim, "beacon.ambient", c, 1.4, 0.5);
      let allies = [];
      try {
        allies = dim.getPlayers({ location: c, maxDistance: k.radius });
      } catch {
        allies = [];
      }
      // v2.26.0 결투 중인 사람은 빼고 (내 결투 상대는 안에 있으면 적처럼 맞음 · 남의 결투는 밖에서 돕지 못함)
      for (const pl of allies) if (canAssist(player, pl)) setGuard(pl, k.pulse + 5, k.reduce, undefined);
      if (!player.isValid) return;
      let flashed = 0;
      for (const e of around(player, c, k.radius, 3)) {
        if (runeHit(player, e, P, k.mult, r.stat) > 0) {
          knock(e, sub(e.location, c), 0.7, 0.25);
          // 번쩍임 12명 · 방벽에 튕겨 나가는 판 4명까지 (파티클 예산 — 방벽 판 24장과 같은 틱)
          if (flashed++ < 12) burst(dim, bodyPoint(e), gold, 0.6, 0.2);
          if (flashed > 4) continue;
          const d = flat(sub(e.location, c));
          hexPlate(dim, up(add(c, d, k.radius), 1.2), d, 0.7, WHITE, { life: 0.25, fade: 0.6, alpha: 0.8, grow: 0.3, core: gold });
        }
      }
    }, i * k.pulse);
  }
  system.runTimeout(() => play(dim, "beacon.deactivate", c, 1.0, 1), k.ticks);
  return true;
}

/**
 * 불사의 맹약: ticks 동안 체력이 1 아래로 내려가지 않음, pulse 틱마다 회복 + 주위 피해.
 * 쓸 때 눈앞에 불사의 문장 (rpg:rfx_emblem — 핏빛 심장 · 금빛 날개 · 가시 후광) 이 번쩍이고, 그 동안 머리 위에 문장이 떠 있고 몸에 핏빛 기운
 */
function undyingPact(player, r, P, k) {
  const dim0 = player.dimension;
  const [glow] = RUNE.color.vit;
  const crimson = [0.9, 0.12, 0.25];
  const gold = [1.0, 0.8, 0.35];
  undying.set(player.id, system.currentTick + k.ticks);
  const c0 = player.location;
  // 머리 위로 치솟는 핏빛 기둥 (몸에서 시작하면 1인칭 카메라가 기둥 안에 들어감)
  fx(dim0, "rpg:arrow_shot", up(c0, 4.6), { dir: V_UP, speed: 0, size: 2.6, thick: 1.1, life: 0.6, color: crimson, alpha: 0.85 });
  fx(dim0, "rpg:arrow_shot", up(c0, 4.6), { dir: V_UP, speed: 0, size: 2.4, thick: 0.35, life: 0.5, color: gold, alpha: 1 });
  // 눈앞 문장 (화면 위쪽, 1인칭에서 보임)
  const eye0 = player.getHeadLocation();
  const view0 = player.getViewDirection();
  rfx(dim0, "emblem", up(add(eye0, view0, 2.8), 0.45), { size: 0.62, life: 0.9, grow: 0.35, fade: 0.55 });
  burst(dim0, up(c0, 1), crimson, 2.0, 0.4);
  selfCue(player, crimson, gold);
  particle(dim0, "minecraft:totem_particle", up(c0, 1));
  play(dim0, "random.totem", c0, 0.9, 0.9);
  play(dim0, "mob.wither.spawn", c0, 1.8, 0.25);
  // v2.27.0 2차 강화 — 맹약: 진홍 하늘 · 붉은 번쩍임 · 발밑의 성광 문양 (진홍) · 앞쪽 하늘의 진홍 후광 · 앞쪽 핏빛 빛기둥 셋 (2틱 뒤). 죽으면 하늘빛도 걷힘
  ultSkyFor(dim0, c0, "undyingPact", 90);
  ultFlashFor(player, dim0, c0, "undyingPact");
  domainSigil(dim0, c0, 4.6, "holy", crimson, gold, { life: 1.6, spinRate: 40, grow: 0.3, alpha: 0.85 });
  skyPlate(player, dim0, "sigil", skyPoint(player, c0, 12, 22), 5.6, crimson, gold, { cell: RFX.sigil.holy, life: 2.6, spinRate: -22, grow: 0.15, alpha: 0.8, fade: 0.45 }); // 하늘의 진홍 후광 (눈앞 문장과 겹치지 않게 문양)
  later(2, () => {
    if (player.isValid) godRays(player, player.dimension, player.location, 6, 3, crimson, gold, { arc: [-50, 50], h: 15, life: 0.9 });
  });
  const pulses = Math.floor(k.ticks / k.pulse);
  const alive = deathToken(player); // 죽으면 끝 (회복 · 반격 · 기운 · 쓰러지지 않음 모두 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
  for (let i = 0; i < pulses; i++) {
    system.runTimeout(() => {
      if (!aliveSince(player, alive)) {
        endSky([player], ultOf("undyingPact").fog); // v2.27.0 죽으면 진홍 하늘도 걷힘
        return;
      }
      const dim = player.dimension;
      const c = player.location;
      floorRune(dim, c, { size: 2.2, life: k.pulse / 20 + 0.1, color: crimson, alpha: 0.85, rate: 90, spin: i * 40, grow: 0 });
      zonePulse(dim, c, i % 2 ? gold : glow, k.radius, 0.45);
      const healed = healBy(player, hpState(player).max * k.heal * supportScale(player, r.family));
      if (healed > 0) showDamageText(player, healed, "heal");
      if (i > 0) selfCue(player, crimson, gold, false);
      particle(dim, "minecraft:heart_particle", up(c, 2.1));
      play(dim, "note.bass", c, 0.6, 0.8);
      for (const e of around(player, c, k.radius)) {
        if (runeHit(player, e, P, k.mult, r.stat) > 0) burst(dim, bodyPoint(e), crimson, 0.6, 0.22);
      }
    }, i * k.pulse);
  }
  // 그 동안: 몸의 핏빛 기운 (2틱마다) · 머리 위 문장 (4틱마다 짧게 — 몸을 따라옴, 달리다 멈춰도 앞으로 미끄러지지 않게). 죽으면 멈춤
  for (let t = 2; t < k.ticks; t += 2) {
    system.runTimeout(() => {
      if (!aliveSince(player, alive)) return;
      bodyAura(player, crimson, { n: 4, alpha: 0.68 });
      if (t % 12 === 0) auraCue(player, t % 24 ? gold : crimson);
      if (t % 4 === 0) rfx(player.dimension, "emblem", up(player.location, 2.95), { size: 0.42, life: 0.24, vel: bodyVel(player) });
    }, t);
  }
  system.runTimeout(() => {
    if (player.isValid) play(player.dimension, "beacon.deactivate", player.location, 1.2, 0.8);
  }, k.ticks);
  return true;
}

/** 불사가 쓰러짐을 막았을 때: 눈앞에 불사의 문장이 크게 번쩍임 */
function onUndyingSaved(player) {
  const now = system.currentTick;
  if (now - (undyingFxAt.get(player.id) ?? -100) < 10) return;
  undyingFxAt.set(player.id, now);
  const dim = player.dimension;
  const c = up(player.location, 1);
  burst(dim, c, [1.0, 0.8, 0.35], 1.8, 0.35);
  rfx(dim, "emblem", up(add(player.getHeadLocation(), player.getViewDirection(), 2.6), 0.2), { size: 0.85, life: 0.7, grow: 0.4, fade: 0.6 });
  particle(dim, "minecraft:totem_particle", c);
  play(dim, "random.totem", c, 1.4, 0.6);
  notify(player, "§6§l불사! §r§7쓰러지지 않았습니다", 30);
}

export function isUndying(player) {
  return (undying.get(player.id) ?? 0) > system.currentTick;
}

// ============================================================ 마력 (파랑: 원소 마법)
/** 화염구: 조준한 몹(없으면 조준한 곳)으로 불덩이 (rpg:rfx_fireball) 가 날아가 폭발 (rpg:rfx_boom, 반지름 radius, 불 fire 초) */
function fireball(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const target = pickTarget(player, k.range, 20);
  const to = target ? bodyPoint(target) : up(groundAim(player, k.range), 0.5);
  const from = add(up(head, -0.25), rightOf(f), 0.3);
  const d = unit(sub(to, from));
  const L = len(sub(to, from));
  const life = Math.max(0.05, L / k.speed);
  const ticks = Math.max(1, Math.round(life * 20));
  const [glow, core] = FIRE;
  // 불덩이는 손에서 0.8칸 앞부터 (1인칭 화면 가운데를 덮지 않게)
  const s0 = Math.min(0.8, L * 0.5);
  rfx(dim, "fireball", add(from, d, s0), { dir: d, speed: (L - s0) / life, size: 0.5, life, frames: 4, fps: 14, loop: true, phase: Math.random() * 4 });
  for (let s = 0.9; s < L; s += 0.6) {
    const delay = (s / L) * life;
    fx(dim, "rpg:arrow_trail", add(from, d, s), { dir: d, size: 0.34, thick: 0.3, life: delay + 0.32, delay, color: glow, alpha: 0.75 });
  }
  for (let t = 1; t < ticks; t += 2) system.runTimeout(() => particle(dim, "minecraft:basic_flame_particle", add(from, d, (L * t) / ticks)), t);
  fx(dim, "rpg:magic_circle_flash", from, { size: 0.4, life: 0.2, spin: Math.random() * 360, color: glow, alpha: 1 });
  play(dim, "mob.blaze.shoot", head, 1.1, 0.8);
  system.runTimeout(() => {
    if (!player.isValid) return;
    const at = target?.isValid ? bodyPoint(target) : to;
    const sc = nearK(player, at, 4, 0.4);
    rfx(dim, "boom", up(at, 0.2), { size: k.radius * 0.75 * sc, life: 0.55, frames: 8, fps: 15 });
    burst(dim, at, glow, 1.6 * sc, 0.3);
    ring(dim, at, d, core, 0.9, 0.3);
    sparks(dim, at, glow, 10, 5);
    zonePulse(dim, { x: at.x, y: at.y - 0.9, z: at.z }, glow, k.radius, 0.4);
    particle(dim, "minecraft:lava_particle", at);
    play(dim, "random.explode", at, 1.4, 0.5);
    play(dim, "fire.ignite", at, 1.0, 1);
    for (const e of around(player, at, k.radius, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) burn(e, k.fire);
    }
  }, ticks);
  return true;
}

/**
 * 서리 폭발: 주위를 얼려 피해와 둔화 — v2.25.0 얼음 결정 (rpg:rfx_crystal) 이 몸 둘레에서 바깥으로 네 겹 솟고 (쓴 사람 1.4칸 안은 비움),
 * 고드름 (rpg:rfx_icicle) 이 사방으로 튐. 맞은 적은 발밑이 얼음 결정에 갇힘 (둔화 동안)
 */
function frostNova(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const [glow, core] = ICE;
  zonePulse(dim, c, glow, k.radius, 0.5);
  zonePulse(dim, c, core, k.radius * 0.6, 0.35);
  floorRune(dim, c, { size: k.radius * 0.8, life: 0.9, color: glow, alpha: 0.85, rate: -60, grow: 0.3 });
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2 + 0.2;
    const d = unit({ x: Math.cos(a), y: 0.12, z: Math.sin(a) });
    rfx2(dim, "icicle", add(up(c, 0.7), d, 1.0), { dir: d, speed: 10, size: 0.36, life: 0.32 }, i % 2 ? core : glow, WHITE);
  }
  // 얼음 결정 네 겹 (1 ~ 4틱, 틱마다 한 겹씩 바깥으로)
  [1.6, 2.4, 3.2, 4.0].forEach((R0, j) => {
    later(j + 1, () => {
      const R = Math.min(R0, k.radius * 0.95);
      const n = 4 + j * 2;
      const a0 = Math.random() * Math.PI * 2;
      const p0 = player.isValid ? player.location : c;
      for (let i = 0; i < n; i++) {
        const a = a0 + (i / n) * Math.PI * 2;
        const at = { x: c.x + Math.cos(a) * R, y: c.y, z: c.z + Math.sin(a) * R };
        if (Math.hypot(at.x - p0.x, at.z - p0.z) < 1.4) continue;
        iceCrystal(player, dim, at, (0.42 + j * 0.08) * (0.8 + Math.random() * 0.4), glow, { life: 1.1 - j * 0.1, fade: 0.35 });
      }
    });
  });
  burst(dim, up(c, 1), core, 1.4, 0.3);
  sparks(dim, up(c, 0.8), core, 8, 4);
  for (let i = 0; i < 6; i++) {
    const a = Math.random() * Math.PI * 2;
    const rr = Math.random() * k.radius;
    particle(dim, "minecraft:snowflake_particle", { x: c.x + Math.cos(a) * rr, y: c.y + 1 + Math.random(), z: c.z + Math.sin(a) * rr });
  }
  play(dim, "random.glass", c, 1.2, 0.8);
  play(dim, "mob.player.hurt_freeze", c, 0.9, 0.8);
  const frozen = [];
  for (const e of around(player, c, k.radius)) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) {
      effect(e, "slowness", k.slowTicks, k.slowAmp);
      if (frozen.length < 20) burst(dim, bodyPoint(e), glow, 0.7, 0.3); // 번쩍임은 20명까지 (파티클 예산)
      frozen.push(e);
    }
  }
  // 얼어붙음: 결정 고리가 다 솟은 뒤 (5틱), 가까운 6명까지 (파티클 예산)
  frozen.sort((a, b) => len(sub(a.location, c)) - len(sub(b.location, c)));
  later(5, () => {
    for (const e of frozen.slice(0, 6)) if (e.isValid) encase(player, dim, e, glow, 1.2);
  });
  return true;
}

/** 얼어붙음: 적의 발밑 둘레에 얼음 결정 셋 (seconds 초 — 둔화된 적이 천천히 빠져나가므로 짧게, 몸 크기만큼 벌어짐) */
function encase(player, dim, e, glow, seconds) {
  const pad = 0.35 + bodyPad(e);
  const a0 = Math.random() * Math.PI * 2;
  for (let i = 0; i < 3; i++) {
    const a = a0 + (i / 3) * Math.PI * 2;
    const at = { x: e.location.x + Math.cos(a) * pad, y: e.location.y, z: e.location.z + Math.sin(a) * pad };
    iceCrystal(player, dim, at, 0.55 + bodyPad(e) * 0.5, glow, { life: seconds, fade: 0.15, grow: 0.05, v: i % 3, alpha: 0.85 });
  }
}

/**
 * 화염 폭풍: 조준한 곳에 불기둥 소용돌이 (gap 틱마다 hits 번, 가운데로 끌어당김) — v2.25.0 가운데 거대한 불 회오리 (rpg:rfx_vortex) · 밑동의 불꽃 +
 * 둘레를 도는 작은 불 회오리 셋 + 바깥을 휘감는 불길, 마지막에 폭발 (rpg:rfx_boom)
 */
function fireStorm(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = FIRE;
  const total = (k.hits - 1) * k.gap + 10;
  const sc = nearK(player, up(g, 2.5), 6, 0.25); // 가까이 겨누면 작게 (화면을 덮지 않게)
  floorRune(dim, g, { size: k.radius, life: total / 20 + 0.3, color: glow, alpha: 0.8, rate: 90, grow: 0 });
  play(dim, "mob.ghast.fireball", g, 0.8, 1);
  play(dim, "fire.ignite", g, 0.8, 1);
  for (let t = 0; t < total; t += 3) {
    system.runTimeout(() => {
      const a = t * 0.55;
      const last = t + 3 >= total;
      // 바깥을 휘감는 불길 (피해 범위 가장자리 가까이)
      {
        const p = { x: g.x + Math.cos(a) * k.radius * 0.8, y: g.y + 0.9 + (t % 9) * 0.1, z: g.z + Math.sin(a) * k.radius * 0.8 };
        const dir = { x: Math.cos(a + Math.PI / 2), y: 0, z: Math.sin(a + Math.PI / 2) };
        arc(dim, p, dir, { shape: "heavy", flip: false, size: 2.2 * sc, life: 0.32, speed: 2, spin: 70, color: glow, core: t % 6 === 0 });
      }
      // 둘레를 도는 작은 불 회오리 셋 (반대로 돎 — 한 틱에 0.22 라디안, 다시 그리는 사이 원을 따라 미끄러짐)
      const R2 = k.radius * 0.62;
      for (let j = 0; j < 3; j++) {
        const b = -t * 0.22 + (j * Math.PI * 2) / 3;
        const q = { x: g.x + Math.cos(b) * R2, y: g.y, z: g.z + Math.sin(b) * R2 };
        const tang = { x: Math.sin(b), y: 0, z: -Math.cos(b) };
        vortexAt(dim, q, (0.9 + 0.15 * j) * sc, j ? glow : core, { life: 0.2, alpha: 0.85, fade: last ? 0.5 : 0.05, phase: (t + j * 3) % 8, vel: scale(tang, R2 * 4.4) });
      }
      if (t % 6 === 0) {
        // 가운데 거대한 불 회오리 (다음까지 이어짐) + 밑동에서 날름거리는 불꽃 둘
        vortexAt(dim, g, 2.5 * sc, glow, { life: 0.36, alpha: 0.95, fps: 22, fade: t + 6 >= total ? 0.5 : 0, grow: t === 0 ? 0.25 : 0, phase: t % 8 });
        for (let j = 0; j < 2; j++) {
          const b = t * 0.9 + j * Math.PI;
          fx(dim, "rpg:fire_wall", { x: g.x + Math.cos(b) * 0.5 * sc, y: g.y, z: g.z + Math.sin(b) * 0.5 * sc }, { size: 1.5 * sc, life: 0.45, color: j ? core : glow, alpha: 0.9, spin: Math.random() * 360 });
        }
      }
      if (t % 9 === 0) {
        const b = Math.random() * Math.PI * 2;
        fx(dim, "rpg:arrow_spark", { x: g.x + Math.cos(b) * 0.8, y: g.y + 1, z: g.z + Math.sin(b) * 0.8 }, { dir: unit({ x: Math.cos(b), y: 2.2, z: Math.sin(b) }), speed: 6, size: 0.3, grow: 0.8, life: 0.6, color: core, alpha: 1 });
      }
      particle(dim, "minecraft:basic_flame_particle", { x: g.x + (Math.random() - 0.5) * k.radius, y: g.y + 0.4, z: g.z + (Math.random() - 0.5) * k.radius });
    }, t);
  }
  every(k.hits, k.gap, (i) => {
    zonePulse(dim, g, i % 2 ? core : glow, k.radius, 0.4);
    play(dim, "fire.fire", g, 1.0, 0.9);
    if (!player.isValid) return;
    for (const e of around(player, g, k.radius, 4)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        knock(e, sub(g, e.location), k.pull, 0.05);
        burn(e, 2);
      }
    }
    if (i === k.hits - 1) {
      rfx(dim, "boom", up(g, 1.0), { size: k.radius * 0.8 * sc, life: 0.55, frames: 8, fps: 15 });
      burst(dim, up(g, 1), glow, 2.2, 0.35);
      sparks(dim, up(g, 1), core, 12, 6);
      play(dim, "random.fizz", g, 0.8, 1);
      play(dim, "random.explode", g, 1.1, 0.6);
    }
  });
  return true;
}

/**
 * 블리자드: 조준한 곳 위에 눈구름 (rpg:rfx_cloud) 이 끼고 눈보라 (gap 틱마다 hits 번 피해 + 둔화), 얼음 조각 (rpg:rfx_icicle) 이 비스듬히 쏟아짐
 */
function blizzard(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = ICE;
  const total = (k.hits - 1) * k.gap + 8;
  const snow = [0.9, 0.95, 1.0];
  floorRune(dim, g, { size: k.radius, life: total / 20 + 0.3, color: glow, alpha: 0.8, rate: -30, grow: 0 });
  play(dim, "mob.player.hurt_freeze", g, 0.7, 1);
  play(dim, "random.glass", g, 0.7, 0.8);
  const wind = unit({ x: 0.35, y: -1, z: 0.2 });
  const sky = up(g, 6.2);
  // 눈구름: 덩이 다섯 (10틱마다 다시 그림, 조금씩 흘러감)
  const puffs = [0, 1, 2, 3, 4].map((i) => {
    const a = (i / 5) * Math.PI * 2 + Math.random();
    const rr = i === 0 ? 0 : k.radius * 0.6;
    return { x: Math.cos(a) * rr, z: Math.sin(a) * rr, s: i === 0 ? 2.6 : 1.9, cell: i % 4 };
  });
  for (let t = 0; t < total; t += 10) {
    later(t, () => {
      for (const p of puffs) rfx(dim, "cloud", { x: sky.x + p.x + t * 0.02, y: sky.y + (p.s > 2 ? 0.5 : 0), z: sky.z + p.z }, { size: p.s, life: t === 0 ? 0.62 : 0.56, cell: p.cell, color: snow, alpha: 0.9, fade: t + 10 >= total ? 0.5 : 0 });
    });
  }
  for (let t = 0; t < total; t += 3) {
    system.runTimeout(() => {
      for (let j = 0; j < 4; j++) {
        const a = Math.random() * Math.PI * 2;
        const rr = Math.sqrt(Math.random()) * k.radius;
        const ground = { x: g.x + Math.cos(a) * rr, y: g.y, z: g.z + Math.sin(a) * rr };
        const top = add(ground, wind, -5.5);
        rfx2(dim, "icicle", top, { dir: wind, speed: 20, size: 0.42, life: 0.27 }, j % 2 ? core : glow, WHITE);
      }
      if (t % 6 === 0) zonePulse(dim, up(g, 1.1 + (t % 12) * 0.08), core, k.radius * 0.9, 0.5); // 떠도는 눈보라 고리
      particle(dim, "minecraft:snowflake_particle", { x: g.x + (Math.random() - 0.5) * k.radius * 1.6, y: g.y + 1.5 + Math.random(), z: g.z + (Math.random() - 0.5) * k.radius * 1.6 });
    }, t);
  }
  every(k.hits, k.gap, (i) => {
    zonePulse(dim, g, i % 2 ? core : glow, k.radius, 0.45);
    play(dim, "step.powder_snow", g, 0.8, 1);
    if (!player.isValid) return;
    for (const e of around(player, g, k.radius, 4)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        effect(e, "slowness", k.slowTicks, k.slowAmp);
        if (i % 2 === 0) fx(dim, "rpg:ice_spike", e.location, { size: 0.9, life: 0.5, spin: Math.random() * 30 - 15, color: glow, alpha: 0.9 });
      }
    }
  });
  return true;
}

/**
 * 메테오: 조준한 곳에 불의 룬이 그려지고 (delay) 뒤쪽 하늘에서 불타는 운석 (rpg:rfx_meteor) 이 연기 꼬리를 끌며 떨어져 폭발 (rpg:rfx_boom)
 */
function meteor(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const f = flat(player.getViewDirection());
  const [glow, core] = FIRE;
  const red = [1.0, 0.2, 0.1];
  for (let t = 0; t < k.delay; t += 2) {
    system.runTimeout(() => {
      floorRune(dim, g, { size: k.radius, life: 0.14, color: glow, alpha: 0.9, progress: Math.min(1, (t + 2) / (k.delay * 0.7)), rate: 0, spin: -t * 3 });
      if (t % 8 === 0) zonePulse(dim, g, red, k.radius, 0.35); // 떨어질 자리 (피해 범위 그대로)
    }, t);
  }
  play(dim, "mob.blaze.shoot", player.getHeadLocation(), 0.6, 1);
  play(dim, "fire.fire", g, 0.7, 1);
  // v2.27.0 2차 강화 — 예고: 불타는 하늘 · 겨눈 땅의 비전 문양 (불빛, 커지며 거꾸로 돎) · 운석이 오기 전에 앞쪽 하늘이 불타며 갈라짐
  ultSkyFor(dim, g, "meteor", k.delay + 46);
  domainSigil(dim, g, k.radius * 1.15, "arcane", glow, core, { life: (k.delay + 1) / 20, spinRate: -30, grow: 0.3, alpha: 0.78, fade: 0.08 });
  const burning = skyPoint(player, g, 6, 21);
  later(Math.max(0, k.delay - 22), () => skyPlate(player, dim, "crack", burning, 6.4, red, core, { life: 1.4, grow: 0.45, fade: 0.35, cell: RFX.crackV }));
  // 운석: 뒤쪽 하늘에서 비스듬히 (마지막 16틱) — 지나간 자리에 연기가 피어오름
  const fall = 16;
  const start = add(up(g, 22), f, -10);
  const d = unit(sub(g, start));
  const L = len(sub(g, start));
  system.runTimeout(() => {
    rfx(dim, "meteor", start, { dir: d, speed: L / (fall / 20), size: 3.2, life: fall / 20, frames: 4, fps: 12, loop: true });
    for (let t = 0; t < fall; t += 2) {
      system.runTimeout(() => {
        const p = add(start, d, (L * t) / fall);
        particle(dim, "minecraft:lava_particle", p);
        rfx(dim, "cloud", add(p, d, -2.5), { size: 1.3, life: 1.1, grow: 1.4, cell: t % 4, color: [0.32, 0.28, 0.27], alpha: 0.7, fade: 0.7 });
      }, t);
    }
    play(dim, "mob.ghast.fireball", g, 0.5, 1);
  }, k.delay - fall);
  system.runTimeout(() => {
    if (!player.isValid) return;
    const mid = up(g, 1);
    const sc = nearK(player, mid, 8, 0.35);
    // v2.27.0 발동: 주황 번쩍임 · 갈라진 하늘의 섬광 → 충격 고리 (1틱 뒤) → 용암처럼 빛나는 금 (2틱 뒤) → 솟는 버섯구름 (3 · 5 · 7틱 뒤)
    ultFlashFor(player, dim, g, "meteor");
    skyFlare(dim, burning, 4.4, glow, core, { life: 0.6 });
    later(1, () => novaRing(dim, g, 2.4, 3.0, glow, core, { life: 0.5 }));
    later(2, () => glowCrack(dim, g, k.radius * 1.15, [1.0, 0.38, 0.08], [1.0, 0.86, 0.5], { life: 2.8, fade: 0.6, v: 1 }));
    for (let i = 0; i < 3; i++) later(3 + i * 2, () => rfx(dim, "cloud", up(g, 2.5 + i * 2.6), { dir: V_UP, speed: 2.2, size: (2.0 + i * 0.9) * sc, life: 1.8, grow: 0.7, cell: i % 4, color: [0.36, 0.26, 0.22], alpha: 0.75, fade: 0.6 }));
    rfx(dim, "boom", up(g, k.radius * 0.45 * sc), { size: k.radius * 0.95 * sc, life: 0.9, frames: 8, fps: 9 });
    for (let i = 0; i < 4; i++) {
      const a = (i / 4) * Math.PI * 2 + 0.6;
      later(2 + i, () => rfx(dim, "boom", { x: g.x + Math.cos(a) * k.radius * 0.6, y: g.y + 1.0, z: g.z + Math.sin(a) * k.radius * 0.6 }, { size: k.radius * 0.42 * sc, life: 0.6, frames: 8, fps: 14 }));
    }
    burst(dim, mid, glow, 6.0 * sc, 0.55);
    burst(dim, mid, red, 4.0 * sc, 0.45);
    burst(dim, mid, core, 2.6 * sc, 0.35);
    fx(dim, "rpg:arrow_spark", mid, { dir: V_UP, size: 4 * sc, grow: 2, life: 0.22, color: WHITE, alpha: 1 });
    for (let i = 0; i < 5; i++) system.runTimeout(() => shock(dim, g, i % 2 ? red : glow, 2 + i * 1.5, 0.55 + i * 0.1), i * 2);
    for (let i = 0; i < 10; i++) {
      const dd = turn({ x: 1, y: 0, z: 0 }, i * 36 + Math.random() * 12);
      fx(dim, "rpg:light_cut", add(up(g, 0.08), dd, 2.4), { dir: dd, size: 2.2, thick: 0.4, life: 1.2, delay: 0.9, color: glow, alpha: 0.95 });
    }
    // 불타는 땅: 둘레 불꽃 · 갈라진 땅 · 튀는 바위
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * Math.PI * 2 + Math.random() * 0.3;
      fx(dim, "rpg:fire_wall", { x: g.x + Math.cos(a) * k.radius * 0.75, y: g.y, z: g.z + Math.sin(a) * k.radius * 0.75 }, { size: 1.8, life: 1.2, color: glow, alpha: 0.95, spin: Math.random() * 360 });
    }
    fx(dim, "rpg:ground_crack", up(g, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: k.radius * 1.3, life: 2.0, color: [0.3, 0.08, 0.02], alpha: 0.95 });
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * Math.PI * 2 + Math.random() * 0.4;
      fx(dim, "rpg:boulder", up(g, 0.6), { dir: unit({ x: Math.cos(a), y: 1.2, z: Math.sin(a) }), speed: 8 + Math.random() * 3, size: 0.32, life: 0.8, spin: Math.random() * 360, color: [0.4, 0.26, 0.2], alpha: 1 });
    }
    sparks(dim, mid, glow, 16, 8);
    particle(dim, "minecraft:huge_explosion_emitter", mid);
    play(dim, "random.explode", g, 0.6, 1);
    play(dim, "firework.large_blast", g, 0.8, 1);
    play(dim, "item.trident.thunder", g, 0.5, 0.8);
    shakeAround(dim, g, 20, 0.45, 0.45);
    for (const e of around(player, g, k.radius, 4)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        knock(e, sub(e.location, g), 0.6, k.lift);
        burn(e, k.fire);
      }
    }
  }, k.delay);
  return true;
}

// ============================================================ 도끼 (핏빛: 힘 1.0 + 체력 0.5, 무겁게 찍고 피를 빨고 버팀)
/** 회전 도끼: 도끼를 던져 range 칸(벽 앞까지) 날아가 hover 틱 제자리에서 돌다가 돌아옴. 갈 때·올 때 선 가까이 width 칸의 적을 한 번씩 (도는 도끼 = rpg:rfx_prop 도끼) */
function axeBoomerang(player, r, P, k) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const from = up(add(player.location, f, 0.6), 1.2);
  const L = Math.max(1.5, rayLength(dim, from, f, k.range));
  const tip = add(from, f, L);
  const [glow, core] = RUNE.color.axe;
  const out = Math.max(1, Math.round((L / k.speed) * 20));
  const back0 = out + k.hover; // 돌아오기 시작하는 틱
  /** @type {{x: number, y: number, z: number} | undefined} */
  let home;
  let backTicks = out;
  arc(dim, up(add(player.getHeadLocation(), f, 1.2), -0.2), f, { shape: "heavy", flip: true, size: 1.5, life: 0.2, speed: 3, spin: 30, color: glow, aura: core });
  play(dim, "item.trident.throw", player.location, 0.7, 1);
  play(dim, "mob.breeze.whirl", player.location, 1.3, 0.6);
  // 도끼: 2틱마다 한 장씩 돌려 가며 (갈 때 → 끝에서 제자리 → 올 때). 손 앞 1칸 안은 그리지 않음 (1인칭 화면)
  /** @type {{x: number, y: number, z: number} | undefined} */
  let last;
  const frame = (t, at, hovering) => {
    const near = len(sub(at, player.getHeadLocation())) < 1.4;
    if (!near) rfx2(dim, "prop", at, { size: hovering ? 0.78 : 0.66, life: 0.16, spin: t * 67 }, glow, core, RFX.axe);
    if (last && len(sub(at, last)) > 0.3) streak(dim, last, at, glow, 0.14, 0.25, 0.8); // 지나간 자리
    last = at;
    if (t % 4 === 0) sparks(dim, at, core, hovering ? 4 : 2, 3);
  };
  // 갈 때 → 끝에서 제자리 (돌아오는 모습은 돌아오기 시작할 때 그때 거리로)
  for (let t = 0; t <= back0; t += 2) later(t, () => frame(t, t <= out ? add(from, f, (L * t) / out) : tip, t > out));
  const outAt = new Map();
  for (const { e, t } of lineTargets(player, from, f, L, k.width)) {
    const when = Math.round((t / k.speed) * 20);
    outAt.set(e.id, when);
    later(when, () => {
      if (player.isValid && e.isValid && runeHit(player, e, P, k.mult, r.stat) > 0) hitFlash(dim, bodyPoint(e), f, glow, core);
    });
  }
  later(back0, () => {
    if (!player.isValid) return;
    home = up(player.location, 1.2);
    const d = sub(home, tip);
    const L2 = len(d);
    const u = unit(d);
    backTicks = Math.max(1, Math.round((L2 / k.speed) * 20));
    const home0 = home;
    for (let t = 2; t <= backTicks; t += 2) later(t, () => frame(back0 + t, add(tip, sub(home0, tip), t / backTicks), false));
    play(dim, "mob.breeze.whirl", tip, 1.0, 0.7);
    for (const { e, t } of lineTargets(player, tip, u, L2, k.width)) {
      const first = outAt.get(e.id);
      const when = Math.round((t / k.speed) * 20);
      // 갈 때 맞은 적은 그때부터 10틱 뒤 (몹 무적 시간)
      const wait = first === undefined ? when : Math.max(when, first + 10 - back0);
      later(wait, () => {
        if (player.isValid && e.isValid && runeHit(player, e, P, k.mult, r.stat) > 0) hitFlash(dim, bodyPoint(e), u, glow, core);
      });
    }
    later(backTicks, () => {
      if (player.isValid) play(dim, "item.trident.return", player.location, 0.9, 1);
    });
  });
  return true;
}

/**
 * 피의 도끼질: 앞을 찍고 맞힌 적마다 체력 회복 — v2.25.0 거대한 도끼 (rpg:rfx_prop 도끼) 가 오른쪽 위에서 왼쪽 아래로 휘둘러지고,
 * 맞은 적에게 핏빛 초승달 (rpg:rfx_wave) · 피가 나에게로 흘러듦
 */
function bloodCleave(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE.color.axe;
  const blood = [0.85, 0.06, 0.08];
  const side = rightOf(f);
  // 휘두르는 도끼 (3틱): 오른쪽 위 → 가운데 → 왼쪽 아래, 날이 도는 쪽으로 기움 (그림은 날이 위)
  for (let t = 0; t < 3; t++) {
    later(t, () => {
      if (!player.isValid) return;
      const q = t / 2;
      const at = up(add(add(player.location, f, 2.1), side, 1.3 - 2.6 * q), 2.5 - 1.9 * q);
      const sz = 0.95 * nearK(player, at, 3, 0.55);
      rfx2(dim, "prop", at, { size: sz, life: t === 2 ? 0.2 : 0.1, spin: -40 - 60 * t, fade: t === 2 ? 0.6 : 0, alpha: 0.95 }, glow, core, RFX.axe);
    });
  }
  arc(dim, up(add(head, f, 1.6), -0.1), f, { shape: "heavy", flip: false, size: 2.4, life: 0.26, speed: 2, spin: -80, color: glow, aura: core });
  later(2, () => arc(dim, up(add(head, f, 1.9), -0.4), f, { shape: "heavy", flip: true, size: 2.0, life: 0.22, speed: 2, spin: -100, color: blood, core: false, alpha: 0.7 }));
  play(dim, "mace.smash_air", c, 0.7, 0.9);
  later(2, () => {
    if (!player.isValid) return;
    const hitC = add(c, f, 1.8);
    zonePulse(dim, hitC, glow, 2.0, 0.35);
    fx(dim, "rpg:light_cut", up(add(c, f, 2.0), 0.08), { dir: f, size: 1.6, thick: 0.42, life: 0.9, delay: 0.6, color: glow, alpha: 0.95 });
    play(dim, "mace.smash_ground", hitC, 0.8, 1);
    shake(player, 0.15, 0.15);
    let n = 0;
    for (const e of coneAround(player, c, f, k.radius, k.cone)) {
      if (runeHit(player, e, P, k.mult, r.stat) <= 0) continue;
      n++;
      const at = bodyPoint(e);
      if (n <= 12) burst(dim, at, blood, 0.8, 0.22); // 번쩍임은 12명까지 (파티클 예산)
      if (n <= 6) {
        // 가까운 6명: 베인 자국 + 핏빛 초승달 (파티클 예산)
        const v = unit(sub(at, head));
        cutMark(dim, at, v, 70, 0.12, glow, core, 1.0);
        const wv = add(at, v, -0.3);
        rfx2(dim, "wave", wv, { size: 0.65 * nearK(player, wv, 3, 0.4), life: 0.24, spin: -60 + Math.random() * 30, grow: 0.3, fade: 0.5, alpha: 0.9 }, blood, BLOOD_CORE);
      }
      if (n > k.healMax) continue;
      // 피가 나에게로
      const to = up(player.location, 1.0);
      for (let i = 0; i < 3; i++) {
        const from = add(at, { x: (Math.random() - 0.5) * 0.6, y: (Math.random() - 0.3) * 0.6, z: (Math.random() - 0.5) * 0.6 });
        const d = sub(to, from);
        fx(dim, "rpg:arrow_spark", from, { dir: unit(d), speed: len(d) / 0.35, size: 0.16, grow: 0.5, life: 0.35, color: blood, alpha: 1 });
      }
    }
    if (n > 0) {
      const healed = healBy(player, hpState(player).max * k.heal * Math.min(n, k.healMax) * supportScale(player, r.family));
      if (healed > 0) {
        showDamageText(player, healed, "heal");
        later(6, () => {
          if (player.isValid) selfCue(player, blood, core, false);
        });
      }
      play(dim, "mob.zombie.unfect", c, 0.6, 0.5);
    }
  });
  return true;
}

/**
 * 도약 찍기: 조준한 곳(range 칸, 막히면 벽 앞)으로 뛰어올라 내려찍음 (반지름 radius, 띄우고 둔화) — v2.25.0 뛰기 전 머리 위로 든 거대한 도끼
 * (rpg:rfx_prop 도끼) → 내려찍으면 앞에 도끼가 박히고 땅이 갈라지며 둘레에 바위 가시가 솟음
 */
function leapSlam(player, r, P, k) {
  const dim = player.dimension;
  const start = { ...player.location };
  const g = groundAim(player, k.range);
  const dist0 = Math.min(k.range, Math.hypot(g.x - start.x, g.z - start.z));
  const f = dist0 > 0.2 ? flat(sub(g, start)) : flat(player.getViewDirection());
  const want = Math.max(0, dist0 - 1.0); // 조준한 곳 바로 앞에 착지 (몹과 겹치지 않게)
  const can = dashDistance(player, f, want, [1.2, 2.2]); // 한 칸 턱은 뛰어넘음
  const reach = can >= want - 0.3 ? want : can;
  const land = { x: start.x + f.x * reach, y: start.y, z: start.z + f.z * reach };
  // 착지는 그 아래의 단단한 땅 (나는 몹을 조준해도 공중에 서지 않게, 물·용암·허공이면 제자리에서 찍음)
  const top = Math.max(g.y, start.y) + 1.6;
  const gy = reach > 0.3 ? solidGround(dim, { x: land.x, y: top, z: land.z }, top - start.y + 8) : undefined;
  const leap = gy !== undefined;
  if (leap) land.y = gy;
  const [glow, core] = RUNE.color.axe;
  const T = 4; // 공중에 있는 틱
  const peak = 2.0 + want * 0.15;
  const pos = (u) => ({ x: start.x + (land.x - start.x) * u, y: start.y + (land.y - start.y) * u + 4 * peak * u * (1 - u) + 1.0, z: start.z + (land.z - start.z) * u });
  if (leap) for (let i = 0; i < 8; i++) streak(dim, pos(i / 8), pos((i + 1) / 8), glow, 0.32, 0.35, 0.95, ((i / 8) * T) / 20);
  // 머리 위로 든 도끼 (뛰어오르기 전까지 머리 1.3칸 위 · 조금 뒤, 날이 뒤로 젖혀짐 — 몸은 마지막 틱에 옮겨짐)
  for (let t = 0; t < T; t++) {
    later(t, () => {
      if (!player.isValid) return;
      const at = add(up(player.location, 2.9), f, -0.3);
      rfx2(dim, "prop", at, { size: 0.8, life: 0.1, spin: 20 + t * 8, alpha: 0.95, vel: bodyVel(player) }, glow, core, RFX.axe);
    });
  }
  zonePulse(dim, start, [0.6, 0.5, 0.4], 1.6, 0.35);
  sparks(dim, up(start, 0.2), [0.7, 0.6, 0.5], 8, 3);
  play(dim, "mob.enderdragon.flap", start, 1.2, 0.7);
  play(dim, "mob.breeze.jump", start, 0.8, 1);
  later(T - 1, () => {
    if (player.isValid && leap) moveTo(player, land);
  });
  later(T, () => {
    if (!player.isValid) return;
    const c = player.location;
    // 내리찍은 도끼: 앞 1.7칸에 날이 아래로 박힘 (0.45초)
    const axeAt = up(add(c, f, 1.7), 0.9);
    rfx2(dim, "prop", axeAt, { size: 1.15 * nearK(player, axeAt, 3, 0.55), life: 0.45, spin: 180, fade: 0.5, alpha: 0.95 }, glow, core, RFX.axe);
    arc(dim, up(add(c, f, 1.6), 1.3), f, { shape: "heavy", flip: false, size: 2.6, life: 0.24, speed: 1, spin: -90, color: glow, aura: core });
    burst(dim, up(c, 0.5), glow, 2.2, 0.35);
    zonePulse(dim, c, glow, k.radius, 0.5);
    zonePulse(dim, c, core, k.radius * 0.5, 0.35);
    fx(dim, "rpg:ground_crack", up(add(c, f, 1.2), 0.05), { dir: V_ZERO, spin: Math.random() * 360, size: k.radius * 1.0, life: 1.2, color: [0.36, 0.1, 0.06], alpha: 0.9 });
    for (let i = 0; i < 6; i++) {
      const d = turn({ x: 1, y: 0, z: 0 }, i * 60 + Math.random() * 20);
      fx(dim, "rpg:light_cut", add(up(c, 0.08), d, 1.5), { dir: d, size: 1.3, thick: 0.34, life: 0.9, delay: 0.6, color: glow, alpha: 0.95 });
    }
    rockRing(player, dim, c, k.radius * 0.75, 6, 0.6, glow, { life: 0.9, minDist: 1.8 });
    rockBurst(dim, up(c, 0.4), 5, 5);
    sparks(dim, up(c, 0.4), core, 8, 5);
    particle(dim, "minecraft:critical_hit_emitter", up(c, 0.6));
    play(dim, "mace.heavy_smash_ground", c, 0.8, 1);
    play(dim, "random.explode", c, 0.9, 0.5);
    shakeAround(dim, c, 10, 0.3, 0.25);
    for (const e of around(player, c, k.radius, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        knock(e, sub(e.location, c), 0.4, k.lift);
        effect(e, "slowness", k.slowTicks, 1);
      }
    }
  });
  return true;
}

/** 처형: 앞 range 칸의 대상 위로 거대한 도끼 (rpg:rfx_prop 도끼) 가 떠올라 돌다가 (delay) 내려찍음. 체력이 below 이하면 bonus 배 */
function execute(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const f = flat(sub(target.location, player.location));
  const [glow, core] = RUNE.color.axe;
  const blood = [0.85, 0.06, 0.08];
  floorRune(dim, target.location, { size: 1.5, life: k.delay / 20 + 0.1, color: glow, alpha: 0.9, progress: 1, rate: 160 });
  for (let t = 0; t < k.delay; t += 2) {
    later(t, () => {
      if (!target.isValid) return;
      const at = up(target.location, 3.2 + t * 0.06);
      // 떠오르며 돌아 마지막에 날이 아래 (180°) — 내려찍는 도끼와 이어짐
      rfx2(dim, "prop", at, { size: 0.9 + t * 0.05, life: 0.16, spin: 180 - (k.delay - 2 - t) * 24 }, glow, core, RFX.axe);
    });
  }
  play(dim, "mob.ravager.roar", player.location, 0.9, 0.8);
  play(dim, "mob.evocation_illager.prepare_attack", target.location, 0.6, 0.8);
  later(k.delay, () => {
    if (!player.isValid || !target.isValid) return;
    let low = false;
    try {
      const hp = target.getComponent("minecraft:health");
      low = !!hp && hp.currentValue <= hp.effectiveMax * k.below;
    } catch {
      low = false;
    }
    const at = bodyPoint(target);
    const g = target.location;
    // 내려찍는 거대한 도끼 + 땅이 갈라짐
    rfx2(dim, "prop", up(g, 3.4), { dir: V_DOWN, speed: 26, size: 1.4, life: 0.1, spin: 180 }, low ? blood : glow, core, RFX.axe); // 도끼날이 아래 (그림은 날이 위)
    arc(dim, up(add(g, f, -0.4), 1.4), f, { shape: "heavy", flip: false, size: 2.6, life: 0.26, speed: 1, spin: -95, color: low ? blood : glow, aura: core });
    burst(dim, at, low ? blood : glow, low ? 2.6 : 1.8, 0.35);
    zonePulse(dim, g, glow, 2.4, 0.45);
    for (let i = 0; i < 4; i++) {
      const d = turn(f, i * 90 + 45);
      fx(dim, "rpg:light_cut", add(up(g, 0.08), d, 1.1), { dir: d, size: 1.0, thick: 0.3, life: 0.8, delay: 0.5, color: glow, alpha: 0.95 });
    }
    sparks(dim, at, core, 12, 6);
    play(dim, "mace.heavy_smash_ground", g, 0.7, 1);
    play(dim, "game.player.attack.critical", at, 0.7, 1);
    shake(player, 0.25, 0.2);
    const got = runeHit(player, target, P, k.mult * (low ? k.bonus : 1), r.stat);
    if (got > 0) knock(target, f, 0.3, 0.15);
    if (got > 0 && low) {
      fx(dim, "rpg:slash_x", add(at, f, -0.2), { dir: f, size: 2.0, life: 0.35, color: blood, alpha: 1 });
      play(dim, "item.trident.thunder", at, 0.5, 0.5);
      notify(player, `§4§l처형! §r§7체력 ${Math.round(k.below * 100)}% 이하 ×${k.bonus}`, 24);
    }
  });
  return true;
}

/**
 * 광전사의 혼: hits × gap 틱 동안 광폭화 (받는 피해 -reduce). gap 틱마다 주위 radius 칸을 난타, 맞힌 적마다 최대 체력 × heal 회복 (healMax 명까지).
 * 몸에 핏빛 기운 (rpg:rfx_aura), 펄스마다 거대한 핏빛 도끼 둘 (rpg:rfx_prop) 이 몸 둘레를 휩쓸며 돎
 */
function berserker(player, r, P, k) {
  const total = k.hits * k.gap;
  const [glow, core] = RUNE.color.axe;
  const blood = [0.85, 0.06, 0.08];
  const dim0 = player.dimension;
  const c0 = player.location;
  setGuard(player, total, k.reduce, undefined);
  fx(dim0, "rpg:arrow_shot", up(c0, 4.4), { dir: V_UP, speed: 0, size: 2.4, thick: 1.0, life: 0.6, color: blood, alpha: 0.85 });
  burst(dim0, up(c0, 1), glow, 2.2, 0.4);
  zonePulse(dim0, c0, blood, k.radius, 0.6);
  selfCue(player, blood, core);
  play(dim0, "mob.ravager.roar", c0, 0.7, 1);
  play(dim0, "mob.enderdragon.growl", c0, 1.4, 0.4);
  shake(player, 0.2, 0.3);
  // v2.27.0 2차 강화 — 광폭화: 핏빛 하늘 (광폭화 동안 — 죽으면 걷힘) · 검붉은 번쩍임 · 발밑의 칼날 문양 · 앞쪽 하늘이 핏빛으로 찢어짐 · 난타마다 발밑 문양
  ultSkyFor(dim0, c0, "berserker", total + 10);
  ultFlashFor(player, dim0, c0, "berserker");
  domainSigil(dim0, c0, 4.8, "blade", blood, core, { life: 1.4, spinRate: 70, grow: 0.35, alpha: 0.85 });
  skyPlate(player, dim0, "crack", skyPoint(player, c0, 12, 21), 6.2, blood, core, { life: 1.8, grow: 0.4, fade: 0.45 });
  const alive = deathToken(player); // 죽으면 끝 (난타 · 흡혈 · 기운 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
  every(k.hits, k.gap, (i) => {
    if (!aliveSince(player, alive)) {
      endSky([player], ultOf("berserker").fog); // v2.27.0 죽으면 핏빛 하늘도 걷힘
      return;
    }
    const dim = player.dimension;
    const c = player.location;
    // 거대한 도끼 둘이 3틱 동안 몸 둘레를 휩쓸며 돎 (반지름 2.3, 허리 높이)
    for (let j = 0; j < 2; j++) {
      for (let s = 0; s < 3; s++) {
        later(s, () => {
          if (!player.isValid) return;
          const cc = player.location;
          const a = i * 1.1 + j * Math.PI + s * 0.7;
          const at = { x: cc.x + Math.cos(a) * 2.3, y: cc.y + 1.0, z: cc.z + Math.sin(a) * 2.3 };
          // 앞 (1인칭 화면) 을 지나는 도끼는 흐리게 — 화면을 덮지 않게
          const ahead = dot(flat(sub(at, cc)), facingOf(player)) > 0.5;
          rfx2(player.dimension, "prop", at, { size: 0.74, life: 0.14, spin: (i * 140 + s * 90 + j * 180) % 360, alpha: ahead ? 0.5 : 0.95 }, j ? blood : glow, core, RFX.axe);
        });
      }
    }
    for (let j = 0; j < 2; j++) {
      const a = (j / 2) * Math.PI * 2 + i * 0.8;
      const dir = { x: Math.cos(a + Math.PI / 2), y: 0, z: Math.sin(a + Math.PI / 2) };
      const at = { x: c.x + Math.cos(a) * 2.3, y: c.y + 0.9, z: c.z + Math.sin(a) * 2.3 };
      arc(dim, at, dir, { shape: "heavy", flip: i % 2 === 1, size: 2.2, life: 0.26, speed: 2.5, spin: 90, color: glow, aura: j ? blood : undefined });
    }
    zonePulse(dim, c, glow, k.radius, 0.4);
    domainSigil(dim, c, 3.6, "blade", i % 2 ? blood : glow, core, { life: 0.45, spinRate: 120, grow: 0.25, alpha: 0.65, fade: 0.6 }); // v2.27.0
    fx(dim, "rpg:light_cut", up(c, 0.08), { dir: turn({ x: 1, y: 0, z: 0 }, i * 47), size: 2.2, thick: 0.32, life: 0.8, delay: 0.5, color: blood, alpha: 0.9 });
    play(dim, "mace.smash_air", c, 0.8 + i * 0.05, 0.8);
    play(dim, "mace.smash_ground", c, 0.7, 0.6);
    let n = 0;
    for (const e of around(player, c, k.radius)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        n++;
        fx(dim, "rpg:slash_x", bodyPoint(e), { dir: flat(sub(e.location, c)), size: 0.9, life: 0.22, color: glow, alpha: 1 });
      }
    }
    if (n > 0) {
      const healed = healBy(player, hpState(player).max * k.heal * Math.min(n, k.healMax) * supportScale(player, r.family));
      if (healed > 0) showDamageText(player, healed, "heal");
    }
    if (i === k.hits - 1) {
      burst(dim, up(c, 1), blood, 2.6, 0.4);
      play(dim, "random.explode", c, 1.0, 0.5);
    }
  });
  // 광폭화 동안: 몸의 핏빛 기운 (양옆 · 뒤, 2틱마다) + 눈앞에서 솟는 핏빛 불씨 (1인칭에서도 보임). 죽으면 멈춤
  for (let t = 2; t < total; t += 2) {
    later(t, () => {
      if (!aliveSince(player, alive)) return;
      bodyAura(player, t % 8 < 4 ? blood : glow, { n: 4, alpha: 0.7 });
      if (t % 8 === 0) auraCue(player, t % 16 ? glow : blood, 2);
    });
  }
  later(total, () => {
    if (player.isValid) play(player.dimension, "mob.zombie.unfect", player.location, 0.8, 0.5);
  });
  return true;
}

// ============================================================ 궁수 (연두: 멀리서 꿰뚫고, 묶고, 빠지며 쏨)
/** 활 쏘는 손 자리 (눈에서 조금 오른쪽 아래 앞) */
function bowHand(player) {
  const view = player.getViewDirection();
  return add(add(up(player.getHeadLocation(), -0.2), rightOf(flat(view)), 0.25), view, 0.6);
}

/**
 * 빛의 화살 (rpg:rfx_arrow) 이 from → to 로 날아감 (speed 칸/초, 걸리는 틱을 돌려줌 — flyStreak 와 같음).
 * 그림은 손 앞 skip 칸에서 나타나고 (1인칭 화면을 가리지 않게) 촉이 to 에 닿으며 끝남
 */
function flyArrow(dim, from, to, speed, glow, core, size = 0.75, skip = 1.2) {
  const d = sub(to, from);
  const L = len(d);
  const life = Math.max(0.05, L / speed);
  const u = unit(d);
  const s0 = Math.min(skip, Math.max(0, L - size));
  const D = Math.max(0, L - s0 - size * 0.8);
  rfx2(dim, "arrow", add(from, u, s0), { dir: u, speed: D / life, size, life }, glow, core);
  return Math.max(1, Math.round(life * 20));
}

/** 관통 화살: 시선 방향 range 칸(벽 앞까지)을 꿰뚫는 화살, 선 가까이 width 칸의 적 모두 — v2.25.0 빛의 화살 그림 + 화살이 지나간 자리에 바람 고리 */
function pierceArrow(player, r, P, k) {
  const dim = player.dimension;
  const view = player.getViewDirection();
  const from = bowHand(player);
  const L = Math.max(1, rayLength(dim, from, view, k.range));
  const speed = 60;
  const [glow, core] = RUNE.color.bow;
  const s0 = Math.min(1.4, L);
  // 촉이 벽 (L) 에서 멈추게: 가운데가 L − 0.76 까지
  rfx2(dim, "arrow", add(from, view, s0), { dir: view, speed, size: 0.95, life: Math.max(0.05, (L - s0 - 0.76) / speed) }, glow, WHITE);
  fx(dim, "rpg:light_cut", add(from, view, L / 2), { dir: view, size: L / 2, thick: 0.2, life: 0.55, delay: 0.3, color: glow, alpha: 0.85 });
  ring(dim, add(from, view, 0.6), view, glow, 0.38, 0.22);
  // 바람 고리: 화살이 지나갈 때 3칸마다 (화살을 감싸고 퍼짐)
  for (let s = 3; s < L; s += 3) later(Math.round((s / speed) * 20), () => ring(dim, add(from, view, s), view, s % 6 ? glow : core, 0.42, 0.3, 1.5));
  play(dim, "crossbow.shoot", from, 1.2, 0.9);
  play(dim, "random.bow", from, 1.5, 0.6);
  for (const { e, t } of lineTargets(player, from, view, L, k.width)) {
    later(Math.round((t / speed) * 20), () => {
      if (!player.isValid || !e.isValid) return;
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        hitFlash(dim, bodyPoint(e), view, glow, core, 0.7);
        play(dim, "random.bowhit", bodyPoint(e), 1.2, 0.8);
      }
    });
  }
  return true;
}

/**
 * 속박 화살: 대상(range 칸, cone 도 안)에게 화살 → 맞으면 root 틱 동안 땅에 묶임 (움직이지 못함) — v2.25.0 빛의 화살 셋이 둘레에 비스듬히 꽂히고
 * 화살마다 빛의 사슬 (rpg:rfx_chain) 이 대상의 몸으로 이어짐
 */
function pinArrow(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.bow;
  const from = bowHand(player);
  const ticks = flyArrow(dim, from, bodyPoint(target), 40, glow, WHITE, 0.8);
  play(dim, "crossbow.shoot", from, 1.0, 0.9);
  later(ticks, () => {
    if (!player.isValid || !target.isValid) return;
    if (runeHit(player, target, P, k.mult, r.stat) <= 0) return;
    effect(target, "slowness", k.root, 10);
    const g = target.location;
    const hold = k.root / 20;
    const pad = bodyPad(target);
    const chest = bodyPoint(target);
    floorRune(dim, g, { size: 1.5 + pad, life: hold, color: glow, alpha: 0.9, rate: 60 });
    // 사슬이 모이는 허리의 빛 고리 (묶인 동안 돎)
    floorRune(dim, up(g, Math.max(0.7, chest.y - g.y - 0.06)), { size: 0.62 + pad, life: hold, color: core, alpha: 0.95, rate: 220 });
    for (let i = 0; i < 3; i++) {
      const a = (i / 3) * Math.PI * 2 + 0.4;
      const R = 1.05 + pad;
      const p = { x: g.x + Math.cos(a) * R, y: g.y, z: g.z + Math.sin(a) * R };
      // 꽂힌 화살 (말뚝처럼): 촉이 땅속 · 깃이 바깥 위로 기움 — 깃에서 몸으로 사슬
      const tilt = unit({ x: -Math.cos(a) * 0.45, y: -1, z: -Math.sin(a) * 0.45 });
      const S = 0.62;
      later(i, () => {
        rfx2(dim, "arrow", add(p, tilt, -S * 0.75), { dir: tilt, size: S, life: hold - i / 20, fade: 0.15 }, glow, core);
        sparks(dim, up(p, 0.1), core, 3, 3);
        // 빛의 사슬: 화살 깃 → 몸
        const nock = add(p, tilt, -S * 1.5);
        const cd = sub(chest, nock);
        const cl = len(cd);
        if (cl > 0.3) rfx2(dim, "chain", add(nock, cd, 0.5), { dir: unit(cd), size: cl / 2, life: hold - i / 20, fade: 0.15, grow: 0, alpha: 0.95 }, glow, core);
      });
    }
    hitFlash(dim, chest, unit(sub(chest, from)), glow, core, 0.8);
    play(dim, "random.bowhit", g, 0.8, 1);
    play(dim, "use.chain", g, 0.8, 1);
    later(2, () => play(dim, "use.chain", g, 1.1, 0.8));
  });
  return true;
}

/**
 * 도약 사격: 뒤로 back 칸 뛰고(벽 앞까지) 앞쪽 cone 도·range 칸의 적 arrows 명에게 한 발씩 (대상이 없어도 뜀) —
 * v2.25.0 뛰는 자리에 바람 회오리 · 바람 줄기, 화살은 빛의 화살 그림
 */
function backstep(player, r, P, k) {
  const dim = player.dimension;
  const start = { ...player.location };
  const f = flat(player.getViewDirection());
  const back = { x: -f.x, y: 0, z: -f.z };
  const dist = dashDistance(player, back, k.back, [1.1, 1.8]); // 한 칸 턱은 넘음
  const end = add(start, back, dist);
  end.y = groundAt(dim, end, start.y);
  const [glow, core] = RUNE.color.bow;
  const wind = [0.86, 1.0, 0.8];
  zonePulse(dim, start, glow, 1.8, 0.35);
  sparks(dim, up(start, 0.3), core, 6, 4);
  // 박차고 나간 자리의 바람 회오리 (뛴 뒤라 1인칭 화면 앞 멀리 — 벽에 막혀 2.5칸도 못 뛰면 그리지 않음: 카메라가 회오리 안에 들어감)
  if (dist > 2.5) vortexAt(dim, start, 1.0, wind, { life: 0.45, alpha: 0.75, fade: 0.5, grow: 0.2 });
  for (let i = 0; i < 4; i++) streak(dim, up(add(start, back, (dist * i) / 4), 1.0), up(add(start, back, (dist * (i + 1)) / 4), 1.0), glow, 0.18 - i * 0.03, 0.35, 0.85);
  for (const sd of [-1, 1]) streak(dim, up(add(add(start, back, dist * 0.2), rightOf(f), sd * 0.6), 0.5), up(add(add(start, back, dist * 0.8), rightOf(f), sd * 0.6), 0.6), wind, 0.08, 0.3, 0.7);
  play(dim, "mob.breeze.jump", start, 1.1, 1);
  play(dim, "mob.enderdragon.flap", start, 1.6, 0.5);
  if (dist > 0.2) moveTo(player, end);
  later(2, () => {
    if (!player.isValid) return;
    const list = coneTargets(player, k.range, k.cone, k.arrows);
    list.forEach((e, i) => {
      later(i * k.gap, () => {
        if (!player.isValid || !e.isValid) return;
        const from = bowHand(player);
        const ticks = flyArrow(dim, from, bodyPoint(e), 45, glow, WHITE, 0.7);
        ring(dim, add(from, player.getViewDirection(), 0.7), unit(sub(bodyPoint(e), from)), glow, 0.3, 0.18);
        play(dim, "random.bow", from, 1.3 + i * 0.1, 0.8);
        later(ticks, () => {
          if (player.isValid && e.isValid && runeHit(player, e, P, k.mult, r.stat) > 0) hitFlash(dim, bodyPoint(e), f, glow, core, 0.8);
        });
      });
    });
  });
  return true;
}

/**
 * 저격: 대상(range 칸, cone 도 안)을 charge 틱 동안 조준 — 손에 빛나는 화살 (rpg:rfx_arrow) 이 모이고 조준선 · 조여드는 고리 → 강한 한 발 (거대한 화살), 밀쳐냄.
 * 그 사이 벽 뒤로 숨으면 벽에 막힘
 */
function snipe(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.bow;
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!player.isValid || !target.isValid) return;
      const from = bowHand(player);
      const to = bodyPoint(target);
      const d = unit(sub(to, from));
      const q = (t + 2) / k.charge;
      streak(dim, add(from, d, 0.4), to, glow, 0.035, 0.12, 0.75);
      ring(dim, add(to, d, -0.4), d, core, Math.max(0.35, 1.2 - t * 0.05), 0.12);
      // 손 앞에 모이는 화살 (조준선 위, 1.6칸 앞 — 점점 밝게) + 촉의 반짝임
      rfx2(dim, "arrow", add(from, d, 1.6), { dir: d, size: 0.75, life: 0.13, alpha: 0.4 + 0.6 * q }, glow, WHITE);
      rfx(dim, "prop", add(from, d, 2.3), { size: 0.12 + 0.18 * q, life: 0.13, cell: RFX.glint[0], spin: t * 20, color: core, alpha: 0.9 });
    });
  }
  play(dim, "crossbow.loading.start", player.location, 0.8, 1);
  later(k.charge - 4, () => {
    if (player.isValid) play(dim, "crossbow.loading.end", player.location, 0.8, 1);
  });
  later(k.charge, () => {
    if (!player.isValid || !target.isValid) return;
    const from = bowHand(player);
    const to = bodyPoint(target);
    const d = unit(sub(to, from));
    const L = len(sub(to, from));
    const open = rayLength(dim, from, d, L) >= L - 0.6;
    const end = open ? to : add(from, d, rayLength(dim, from, d, L));
    const ticks = flyStreak(dim, from, end, 90, glow, core, 1.6, 0.5);
    const L2 = len(sub(end, from));
    if (L2 > 2) rfx2(dim, "arrow", add(from, d, 1.6), { dir: d, speed: Math.max(0, L2 - 2.2) / Math.max(0.05, ticks / 20), size: 1.2, life: Math.max(0.05, ticks / 20) }, glow, WHITE);
    fx(dim, "rpg:light_cut", add(from, sub(end, from), 0.5), { dir: d, size: L2 / 2, thick: 0.3, life: 0.7, delay: 0.45, color: glow, alpha: 0.9 });
    ring(dim, add(from, d, 0.9), d, glow, 0.6, 0.28, 2);
    ring(dim, add(from, d, 1.5), d, core, 0.4, 0.22, 4);
    play(dim, "crossbow.shoot", from, 0.6, 1);
    play(dim, "wind_charge.burst", from, 1.4, 0.6);
    shake(player, 0.12, 0.15);
    later(ticks, () => {
      if (!player.isValid) return;
      if (!open || !target.isValid) {
        burst(dim, end, glow, 0.8, 0.2);
        return;
      }
      if (runeHit(player, target, P, k.mult, r.stat) <= 0) return;
      knock(target, d, k.push, 0.3);
      const at = bodyPoint(target);
      burst(dim, at, glow, 2.0, 0.35);
      rfx(dim, "prop", add(at, d, -0.4), { size: 1.4, life: 0.35, cell: RFX.glint[0], spin: 45, color: WHITE, grow: 0.5, fade: 0.6 });
      fx(dim, "rpg:slash_x", add(at, d, -0.2), { dir: d, size: 1.4, life: 0.3, color: core, alpha: 1 });
      zonePulse(dim, target.location, glow, 2.0, 0.4);
      sparks(dim, at, core, 10, 6);
      play(dim, "random.bowhit", at, 0.6, 1);
      play(dim, "item.trident.hit", at, 0.8, 1);
    });
  });
  return true;
}

/**
 * 천궁: 앞쪽 하늘에 하늘의 활 (rpg:rfx_bow) 이 나타나 시위를 당긴 뒤 (draw 틱) 하늘로 쏘고, 주위 range 칸의 적 targets 명에게 차례로
 * 거대한 화살 (rpg:rfx_arrow) 이 떨어져 꽂힘 (맞은 곳 splash 칸, 적마다 한 번)
 */
function skyBow(player, r, P, k) {
  const c0 = player.location;
  const list = nearestTo(around(player, c0, k.range, 6), c0).slice(0, k.targets);
  if (!list.length) {
    warn(player, `§c${r.def.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.bow;
  const gold = [1.0, 0.95, 0.6];
  const f0 = flat(player.getViewDirection());
  const center = up(add(c0, f0, 4.6), 2.9); // 앞쪽 하늘 (1인칭 화면 위쪽 — 활 그림은 손잡이가 위쪽이라 가운데를 조금 낮게)
  for (let t = 0; t <= k.draw; t += 4) later(t, () => rfx2(dim, "bow", center, { size: 2.1, life: t >= k.draw ? 0.32 : 0.24, cell: Math.min(3, Math.floor((t / k.draw) * 4)) }, glow, gold, [0, RFX.bowCore]));
  floorRune(dim, c0, { size: 2.0, life: k.draw / 20 + 0.3, color: glow, alpha: 0.8, rate: 90, grow: 0.2 });
  play(dim, "beacon.power", center, 1.6, 0.8);
  play(dim, "crossbow.loading.start", c0, 0.5, 1);
  // v2.27.0 2차 강화 — 예고: 새벽빛 하늘 · 활 뒤 하늘의 성광 문양 (돎) · 발밑 문양
  ultSkyFor(dim, c0, "skyBow", k.draw + 4 + list.length * k.gap + 30);
  const halo = add(up(center, 3.2), f0, 9);
  skyPlate(player, dim, "sigil", halo, 5.2, glow, gold, { cell: RFX.sigil.holy, life: (k.draw + 10) / 20, spinRate: 35, grow: 0.15, alpha: 0.75, fade: 0.3 });
  domainSigil(dim, c0, 4.2, "holy", glow, gold, { life: (k.draw + 6) / 20, spinRate: -40, grow: 0.2, alpha: 0.75 });
  const hit = new Set();
  later(k.draw, () => {
    rfx2(dim, "arrow", up(center, 1.2), { dir: V_UP, speed: 40, size: 1.4, life: 0.3 }, glow, gold);
    rfx(dim, "prop", up(center, 0.4), { size: 1.3, life: 0.3, cell: RFX.glint[0], color: WHITE, grow: 0.6, fade: 0.6 });
    burst(dim, center, core, 1.6, 0.3);
    play(dim, "random.bow", center, 0.5, 1);
    play(dim, "crossbow.shoot", center, 0.5, 1);
    // v2.27.0 발동: 연두 금빛 번쩍임 (짧게) · 하늘 문양에서 퍼지는 고리 (시전자 쪽을 봄)
    ultFlashFor(player, dim, c0, "skyBow", "flash", { time: { fadeInTime: 0.03, holdTime: 0, fadeOutTime: 0.32 } });
    novaRing(dim, halo, 1.6, 3.2, glow, gold, { dir: towardEye(player, halo), life: 0.5 });
    list.forEach((e, i) => {
      later(4 + i * k.gap, () => {
        if (!player.isValid || !e.isValid) return;
        const g = { ...e.location };
        const drop = 5;
        streak(dim, up(g, 14), up(g, 0.1), core, 0.05, drop / 20, 0.6); // 떨어질 자리 빛줄기
        rfx2(dim, "arrow", up(g, 14), { dir: V_DOWN, speed: 12.6 / (drop / 20), size: 1.9, life: drop / 20 }, glow, gold);
        later(drop - 1, () => rfx2(dim, "arrow", up(g, 1.5), { dir: V_DOWN, size: 1.9, life: 0.95, fade: 0.45 }, glow, gold)); // 꽂힌 화살 (촉이 땅속): 떨어지는 화살이 끝나기 1틱 전 (깜빡임 없게)
        later(drop, () => {
          if (!player.isValid) return;
          const at = up(g, 0.9);
          burst(dim, at, glow, 2.0, 0.32);
          burst(dim, at, gold, 1.0, 0.24);
          godRays(player, dim, g, 0, 1, glow, gold, { h: 13, life: 0.55, skipNear: true }); // v2.27.0 화살이 꽂힌 자리의 빛기둥
          zonePulse(dim, g, glow, k.splash, 0.4);
          for (let j = 0; j < 6; j++) {
            const d = turn({ x: 1, y: 0, z: 0 }, j * 60 + i * 17);
            fx(dim, "rpg:light_cut", add(up(g, 0.08), d, 1.0), { dir: d, size: 0.9, thick: 0.24, life: 0.7, delay: 0.45, color: glow, alpha: 0.95 });
          }
          sparks(dim, at, core, 8, 5);
          play(dim, "random.bowhit", g, 0.6, 1);
          play(dim, "random.explode", g, 1.6, 0.35);
          for (const x of around(player, g, k.splash, 3)) {
            if (hit.has(x.id)) continue;
            if (runeHit(player, x, P, k.mult, r.stat) > 0) hit.add(x.id);
          }
        });
      });
    });
  });
  return true;
}

// ============================================================ 성기사 (금빛: 보호막·정화·기절, 신성 피해 = 마법, 언데드 1.5배)
/** 보호막을 받은 사람: 몸 옆 · 뒤에 금빛 방패 문장 셋이 나타나 몸으로 스며듦 (1인칭은 눈앞 고리 · 빛 알갱이). full = false 면 방패 없이 */
function shieldGainFx(pl, glow, core, full = true) {
  const dim = pl.dimension;
  const c = pl.location;
  const f = facingOf(pl);
  const vel = bodyVel(pl);
  // 방패 셋은 쓴 사람과 아군 둘까지 (여럿이 받으면 파티클 예산 — 나머지는 빛 알갱이 · 고리만)
  if (full) {
    for (const deg of [-105, 105, 180]) {
      const d = turn(f, deg);
      rfx2(dim, "shield", up(add(c, d, 0.95), 1.15), { dir: scale(d, -1), speed: 1.2, size: 0.34, life: 0.5, grow: 0.15, fade: 0.5, alpha: 0.9, vel }, glow, WHITE);
    }
  }
  selfCue(pl, glow, core);
  play(dim, "armor.equip_gold", c, 1.2, 1);
}

/**
 * 빛의 보호막: 나와 radius 칸 안 아군에게 보호막 (최대 체력 × shield × 스탯 비례, ticks), 가까운 적(hitRadius)은 밀어내며 신성 피해 —
 * v2.25.0 앞 머리 위에 커다란 금빛 방패 문장 (rpg:rfx_shield), 받은 사람마다 몸 둘레의 방패 셋 (shieldGainFx — 쓴 사람 + 아군 둘까지)
 */
function lightBarrier(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const [glow, core] = RUNE.color.holy;
  const scaleS = supportScale(player, r.family);
  let others = 0;
  for (const pl of playersAround(dim, c, k.radius, player)) {
    const amt = hpState(pl).max * k.shield * scaleS;
    const got = addShield(pl, amt, k.ticks, amt); // 다시 걸면 쌓이지 않고 이 양까지 채움
    if (got > 0) {
      // 받은 사람의 그림: 쓴 사람은 바로, 아군은 한 틱씩 늦게 (같은 틱에 몰리지 않게 — 보호막은 지금)
      const mine = pl.id === player.id;
      const order = mine ? 0 : ++others;
      later(order, () => {
        if (pl.isValid) shieldGainFx(pl, glow, core, mine || order <= 2);
      });
      if (pl.id !== player.id) notify(pl, `§6보호막 +${Math.round(got)} §7(${player.name}님의 ${r.def.name})`, 30);
    }
  }
  floorRune(dim, c, { size: 2.2, life: 0.9, color: glow, alpha: 0.9, rate: 90, grow: 0.3 });
  zonePulse(dim, c, glow, k.radius, 0.55);
  zonePulse(dim, up(c, 1.2), core, k.radius * 0.7, 0.5);
  // 큰 금빛 방패 문장: 앞 1.8칸 · 머리 위쪽 (1인칭 화면 위쪽에 보이고, 3인칭에서는 몸 앞 위)
  const em = up(add(c, facingOf(player), 1.8), 2.55);
  const ek = nearK(player, em, 3, 0.45);
  rfx2(dim, "shield", em, { size: 0.8 * ek, life: 0.75, grow: 0.3, fade: 0.55, alpha: 0.95, vel: bodyVel(player) }, glow, WHITE);
  rfx(dim, "prop", em, { size: 1.1 * ek, life: 0.35, cell: RFX.glint[0], color: WHITE, grow: 0.5, fade: 0.6, vel: bodyVel(player) });
  sparks(dim, up(c, 1), core, 8, 3);
  play(dim, "beacon.activate", c, 1.6, 0.7);
  play(dim, "random.orb", c, 0.8, 0.8);
  let flashed = 0;
  for (const e of around(player, c, k.hitRadius)) {
    if (holyHit(player, e, P, k.mult) > 0) {
      knock(e, sub(e.location, c), 0.6, 0.25);
      // 번쩍임 10명 · 밀려나는 적 앞의 방패 6명까지 (파티클 예산)
      if (flashed < 10) burst(dim, bodyPoint(e), glow, 0.7, 0.22);
      if (flashed++ < 6) {
        const d = flat(sub(e.location, c));
        const sp = up(add(e.location, d, -0.5), 1.1);
        rfx2(dim, "shield", sp, { size: 0.45 * nearK(player, sp, 3, 0.35), life: 0.3, grow: 0.3, fade: 0.6, alpha: 0.85 }, glow, WHITE); // 가까운 적 앞의 방패는 작게
      }
    }
  }
  return true;
}

/**
 * 정화의 빛: 나와 radius 칸 안 아군 회복(최대 체력 × heal × 스탯 비례)·나쁜 효과 제거, 가까운 적(hitRadius) 신성 피해 —
 * v2.25.0 앞 위 하늘에 빛의 문장 (rpg:rfx_sun) 이 떠오르고 둘레에 빛줄기가 비처럼 내림 (쓴 사람 1.3칸 안은 비움)
 */
function purify(player, r, P, k) {
  const dim = player.dimension;
  const c = player.location;
  const [glow, core] = RUNE.color.holy;
  const scaleS = supportScale(player, r.family);
  for (const pl of playersAround(dim, c, k.radius, player)) {
    const healed = healBy(pl, hpState(pl).max * k.heal * scaleS);
    const n = cleanse(pl);
    if (healed > 0) showDamageText(pl, healed, "heal");
    // 머리 위부터 내리쬐는 빛 (몸에 세우면 1인칭 카메라가 기둥 안)
    fx(dim, "rpg:arrow_shot", up(pl.location, 4.6), { dir: V_UP, speed: 0, size: 2.6, thick: 0.9, life: 0.6, color: glow, alpha: 0.75 });
    fx(dim, "rpg:arrow_shot", up(pl.location, 4.6), { dir: V_UP, speed: 0, size: 2.5, thick: 0.25, life: 0.5, color: WHITE, alpha: 1 });
    selfCue(pl, glow, core, pl.id === player.id);
    if (n > 0) notify(pl, `§e정화 §7나쁜 효과 ${n}개를 없앴습니다`, 30);
  }
  // 빛의 문장: 앞 2.4칸 · 위 3칸에서 천천히 돎 (1.1초 — 1인칭 화면 위쪽에 보이고, 3인칭에서는 머리 앞 위)
  const vel = bodyVel(player);
  const sunAt = up(add(c, facingOf(player), 2.4), 3.0);
  rfx2(dim, "sun", sunAt, { size: 1.5 * nearK(player, sunAt, 3.5, 0.5), life: 1.1, grow: 0.25, fade: 0.45, spinRate: 40, alpha: 0.95, vel }, glow, WHITE);
  floorRune(dim, c, { size: 2.4, life: 1.0, color: glow, alpha: 0.85, rate: 60, grow: 0.3 });
  zonePulse(dim, c, glow, k.radius, 0.6);
  // 빛의 비: 둘레 (반지름 radius × 0.8 안) 에 내리꽂히는 빛줄기 12개 (3틱에 걸쳐)
  for (let i = 0; i < 12; i++) {
    later(1 + (i % 3), () => {
      const a = Math.random() * Math.PI * 2;
      const rr = 1.3 + Math.random() * (k.radius * 0.8 - 1.3);
      const p = { x: c.x + Math.cos(a) * rr, y: c.y, z: c.z + Math.sin(a) * rr };
      fx(dim, "rpg:arrow_shot", up(p, 5.5), { dir: V_DOWN, speed: 18, size: 0.7, thick: 0.12, life: 0.28, color: i % 2 ? WHITE : glow, alpha: 0.9 });
      later(6, () => fx(dim, "rpg:arrow_spark", up(p, 0.2), { dir: V_UP, speed: 1.6, size: 0.18, grow: 1.4, life: 0.5, color: core, alpha: 1 }));
    });
  }
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2;
    fx(dim, "rpg:arrow_spark", { x: c.x + Math.cos(a) * 1.4, y: c.y + 0.2, z: c.z + Math.sin(a) * 1.4 }, { dir: V_UP, speed: 2.4, size: 0.2, grow: 1.4, life: 0.9, color: i % 2 ? core : glow, alpha: 1 });
  }
  play(dim, "random.levelup", c, 1.4, 0.5);
  play(dim, "beacon.power", c, 1.4, 0.6);
  for (const e of around(player, c, k.hitRadius)) {
    if (holyHit(player, e, P, k.mult) > 0) burst(dim, bodyPoint(e), glow, 0.7, 0.22);
  }
  return true;
}

/** 심판의 망치: 대상(range 칸)에게 도는 금빛 망치 (rpg:rfx_prop 망치) → 신성 피해 + 기절(stun), bounceRange 칸 안의 새 적에게 bounces 번 튐 */
function judgeHammer(player, r, P, k) {
  const first = pickTarget(player, k.range, k.cone);
  if (!first) {
    warn(player, `§c${r.def.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.holy;
  const done = new Set([first.id]);
  const fly = (from, e, n) => {
    const to = bodyPoint(e);
    const d = unit(sub(to, from));
    const L = len(sub(to, from));
    const ticks = Math.max(1, Math.round((L / k.speed) * 20));
    for (let t = 0; t <= ticks; t += 2) {
      later(t, () => {
        const at = add(from, d, (L * Math.min(t, ticks)) / ticks);
        if (n === 0 && len(sub(at, player.getHeadLocation())) < 1.3) return; // 손 바로 앞 (1인칭 화면)
        rfx2(dim, "prop", at, { size: 0.62, life: 0.16, spin: t * 80 }, glow, core, RFX.hammer);
      });
    }
    streak(dim, from, to, glow, 0.12, 0.3, 0.6, ticks / 40);
    later(ticks, () => {
      if (!player.isValid || !e.isValid) return;
      const at = bodyPoint(e);
      if (holyHit(player, e, P, k.mult) > 0) {
        stun(e, k.stun, glow);
        burst(dim, at, glow, 1.2, 0.28);
        rfx(dim, "prop", add(at, d, -0.4), { size: 0.9, life: 0.3, cell: RFX.glint[0], spin: 45, color: WHITE, grow: 0.5, fade: 0.6 });
        ring(dim, at, d, core, 0.8, 0.3);
        sparks(dim, at, core, 8, 5);
        play(dim, "random.anvil_land", at, 1.4, 0.5);
        play(dim, "block.bell.hit", at, 1.2, 0.7);
      }
      if (n >= k.bounces) return;
      const next = nearestTo(around(player, e.location, k.bounceRange, 3).filter((x) => !done.has(x.id)), e.location)[0];
      if (!next) return;
      done.add(next.id);
      fly(at, next, n + 1);
    });
  };
  fly(bowHand(player), first, 0);
  play(dim, "item.trident.throw", player.location, 0.8, 1);
  return true;
}

/**
 * 천벌: 조준한 곳(range 칸)에 금빛 룬(delay 틱) → 하늘에서 빛기둥. 반지름 radius 적 신성 피해, 안의 아군 회복 —
 * v2.25.0 룬이 그려지는 동안 하늘 (5칸 위) 에 빛의 문장 (rpg:rfx_sun) 이 커지며 열리고 → 굵은 빛기둥이 내리꽂힘 · 문장이 번쩍 · 금빛 깃털이 흩날림
 */
function divineSmite(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = RUNE.color.holy;
  const gold = [1.0, 0.86, 0.45];
  const sky = 5; // 문장 높이 (1인칭에서 땅을 겨눠도 화면 위쪽에 보이게)
  const sk = nearK(player, up(g, sky), 5, 0.4); // 가까이 겨누면 작게
  for (let t = 0; t < k.delay; t += 2) {
    later(t, () => {
      floorRune(dim, g, { size: k.radius, life: 0.14, color: glow, alpha: 0.9, progress: Math.min(1, (t + 2) / k.delay), rate: 0, spin: t * 6 });
      // 하늘의 문장: 0.6 → 2.2 로 커짐 (2틱마다 다시 그림)
      rfx2(dim, "sun", up(g, sky), { size: (0.9 + (2.4 * (t + 2)) / k.delay) * sk, life: 0.14, spin: t * 8, alpha: 0.6 + (0.4 * t) / k.delay }, glow, WHITE);
    });
  }
  fx(dim, "rpg:arrow_shot", up(g, sky / 2), { dir: V_UP, speed: 0, size: sky / 2, thick: 0.1, life: k.delay / 20, color: core, alpha: 0.7 });
  play(dim, "beacon.power", g, 1.2, 1);
  later(k.delay, () => {
    if (!player.isValid) return;
    // 가까이 겨누면 기둥을 가늘게 (1인칭 · 3인칭 카메라가 기둥 안에 들지 않게)
    const th = nearK(player, up(g, 1.6), 1 + k.radius * 0.55 + 1.5, 0.3);
    fx(dim, "rpg:arrow_shot", up(g, sky / 2 + 0.5), { dir: V_UP, speed: 0, size: sky / 2 + 0.5, thick: k.radius * 0.6 * th, life: 0.6, color: glow, alpha: 0.85 });
    fx(dim, "rpg:arrow_shot", up(g, sky / 2 + 0.5), { dir: V_UP, speed: 0, size: sky / 2 + 0.5, thick: k.radius * 0.28 * th, life: 0.55, color: gold, alpha: 0.9 });
    fx(dim, "rpg:arrow_shot", up(g, sky / 2 + 0.5), { dir: V_UP, speed: 0, size: sky / 2 + 0.5, thick: 0.6 * th, life: 0.5, color: WHITE, alpha: 1 });
    rfx2(dim, "sun", up(g, sky), { size: 3.9 * sk, life: 0.6, grow: 0.3, fade: 0.6, spinRate: 90 }, glow, WHITE);
    burst(dim, up(g, 1), glow, 3.0, 0.4);
    burst(dim, up(g, 1), WHITE, 1.6, 0.3);
    zonePulse(dim, g, glow, k.radius, 0.5);
    zonePulse(dim, g, core, k.radius * 0.6, 0.35);
    for (let i = 0; i < 8; i++) {
      const d = turn({ x: 1, y: 0, z: 0 }, i * 45 + Math.random() * 10);
      fx(dim, "rpg:light_cut", add(up(g, 0.08), d, 1.8), { dir: d, size: 1.6, thick: 0.34, life: 0.9, delay: 0.6, color: glow, alpha: 0.95 });
    }
    // 금빛 깃털이 흩날리며 내려옴 (2틱 뒤 — 내리꽂히는 틱의 파티클 예산)
    later(2, () => {
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2 + Math.random() * 0.5;
        const rr = 0.8 + Math.random() * k.radius * 0.8;
        rfx2(dim, "prop", { x: g.x + Math.cos(a) * rr, y: g.y + 2.6 + Math.random() * 1.8, z: g.z + Math.sin(a) * rr }, { dir: V_DOWN, speed: 0.9, size: 0.2, life: 1.5, spin: Math.random() * 360, spinRate: 80, fade: 0.35 }, gold, WHITE, RFX.feather, 0.6);
      }
    });
    sparks(dim, up(g, 1), core, 10, 6);
    play(dim, "item.trident.thunder", g, 1.4, 0.6);
    play(dim, "beacon.activate", g, 1.2, 1);
    shakeAround(dim, g, 12, 0.2, 0.2);
    for (const e of around(player, g, k.radius, 4)) {
      if (holyHit(player, e, P, k.mult) > 0) burst(dim, bodyPoint(e), glow, 0.8, 0.24);
    }
    const scaleS = supportScale(player, r.family);
    for (const pl of playersAround(dim, g, k.radius, player)) {
      const healed = healBy(pl, hpState(pl).max * k.heal * scaleS);
      if (healed > 0) {
        showDamageText(pl, healed, "heal");
        selfCue(pl, glow, core, false);
      }
    }
  });
  return true;
}

/**
 * 대천사 강림: hits × gap 틱 동안 등 뒤에 금빛 날개 (rpg:rfx_wings — 보는 방향에 수직인 면, 날갯짓). gap 틱마다 allyRadius 칸 안 아군 회복·보호막(최대 cap),
 * radius 칸 안 적 신성 피해. 눈앞에 금빛 깃털 (rpg:rfx_prop 깃털) 이 천천히 떨어짐 (1인칭)
 */
function archangel(player, r, P, k) {
  const total = k.hits * k.gap;
  const [glow, core] = RUNE.color.holy;
  const dim0 = player.dimension;
  const c0 = player.location;
  const wingCol = [1.0, 0.9, 0.62];
  fx(dim0, "rpg:arrow_shot", up(c0, 7), { dir: V_UP, speed: 0, size: 5, thick: 1.4, life: 0.8, color: glow, alpha: 0.85 });
  fx(dim0, "rpg:arrow_shot", up(c0, 7), { dir: V_UP, speed: 0, size: 4.8, thick: 0.4, life: 0.7, color: WHITE, alpha: 1 });
  burst(dim0, up(c0, 1.2), glow, 2.4, 0.45);
  floorRune(dim0, c0, { size: 3.0, life: 1.2, color: glow, alpha: 0.9, rate: 40, grow: 0.3 });
  selfCue(player, glow, core);
  play(dim0, "random.totem", c0, 1.3, 0.7);
  play(dim0, "beacon.activate", c0, 0.9, 1);
  play(dim0, "mob.allay.idle", c0, 0.8, 1);
  // v2.27.0 2차 강화 — 강림: 천상의 금빛 하늘 (죽으면 걷힘) · 흰 금빛 번쩍임 · 앞쪽 하늘의 거대한 성광 문양 (후광) · 발밑 문양 · 앞쪽 빛기둥 다섯 (2틱 뒤)
  ultSkyFor(dim0, c0, "archangel", 100);
  ultFlashFor(player, dim0, c0, "archangel");
  const halo = skyPoint(player, c0, 12, 22);
  skyPlate(player, dim0, "sigil", halo, 6.2, glow, WHITE, { cell: RFX.sigil.holy, life: 3.2, spinRate: 18, grow: 0.15, alpha: 0.8, fade: 0.4 });
  domainSigil(dim0, c0, 5.6, "holy", glow, WHITE, { life: 2.2, spinRate: 30, grow: 0.2, alpha: 0.8 });
  later(2, () => {
    if (player.isValid) godRays(player, player.dimension, player.location, 6.5, 5, glow, WHITE, { arc: [-70, 70], h: 18, life: 1.3, jitter: 10 });
  });
  const alive = deathToken(player); // 죽으면 끝 (날개 · 회복 · 보호막 · 피해 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
  for (let t = 0; t < total; t += 2) {
    later(t, () => {
      if (!aliveSince(player, alive)) return;
      const dim = player.dimension;
      const c = player.location;
      const f = facingOf(player);
      // 날개: 등 뒤 0.5칸 · 어깨 높이, 날갯짓 (12틱마다 위 · 아래). 2틱마다 짧게 다시 그림 (몸을 따라옴 — 돌거나 뒷걸음질해도 눈앞을 오래 가리지 않게)
      rfx2(dim, "wings", up(add(c, f, -0.5), 1.4), { dir: f, size: 2.3, life: BODY_LIFE, cell: Math.floor(t / 12) % 2, vel: bodyVel(player) }, wingCol, WHITE, [0, RFX.wingsCore], 0.85);
      if (t % 6 !== 0) return;
      if (t % 12 === 0) floorRune(dim, up(c, 2.35), { size: 0.4, life: 0.65, color: glow, alpha: 0.95, rate: 120 }); // 후광
      // 눈앞에 천천히 떨어지는 금빛 깃털 (1인칭)
      if (t % 12 === 6) {
        for (let j = 0; j < 2; j++) {
          const p = add(add(c, turn(f, (Math.random() - 0.5) * 80), 1.8 + Math.random() * 1.2), V_UP, 2.4 + Math.random() * 0.6);
          rfx2(dim, "prop", p, { dir: V_DOWN, speed: 0.7, size: 0.17, life: 1.4, spin: Math.random() * 360, spinRate: 70, fade: 0.3 }, j ? WHITE : wingCol, WHITE, RFX.feather, 0.6);
        }
      }
    });
  }
  every(k.hits, k.gap, (i) => {
    if (!aliveSince(player, alive)) {
      endSky([player], ultOf("archangel").fog); // v2.27.0 죽으면 천상의 하늘도 걷힘
      return;
    }
    const dim = player.dimension;
    const c = player.location;
    zonePulse(dim, c, i % 2 ? core : glow, k.radius, 0.5);
    play(dim, "beacon.ambient", c, 1.5, 0.6);
    // 앞쪽에 비스듬히 내리쬐는 햇살 두 줄기 (1인칭)
    const fv = flat(player.getViewDirection());
    for (let j = 0; j < 2; j++) {
      const g0 = add(c, turn(fv, (j ? 1 : -1) * (15 + Math.random() * 25)), 3 + Math.random() * 2);
      streak(dim, up(add(g0, fv, 1.5), 7), g0, core, 0.3, 0.6, 0.35);
    }
    const scaleS = supportScale(player, r.family);
    for (const pl of playersAround(dim, c, k.allyRadius, player)) {
      const max = hpState(pl).max;
      const healed = healBy(pl, max * k.heal * scaleS);
      if (healed > 0) showDamageText(pl, healed, "heal");
      addShield(pl, max * k.shield * scaleS, k.gap * 2 + 20, max * k.cap);
    }
    for (const e of around(player, c, k.radius)) {
      if (holyHit(player, e, P, k.mult) > 0 && fxQuota("archangelHit", 10)) {
        // v2.27.0 적이 많으면 빛은 10명까지 (파티클 예산 — 피해는 모두)
        const at = bodyPoint(e);
        fx(dim, "rpg:arrow_shot", up(e.location, 2.2), { dir: V_UP, speed: 0, size: 1.4, thick: 0.3, life: 0.35, color: glow, alpha: 0.9 });
        burst(dim, at, glow, 0.7, 0.22);
      }
    }
  });
  later(total, () => {
    if (!player.isValid) return;
    burst(player.dimension, up(player.location, 1.2), glow, 2.0, 0.4);
    // 날개가 깃털로 흩어짐
    const c = player.location;
    for (let j = 0; j < 6; j++) {
      const a = Math.random() * Math.PI * 2;
      rfx(player.dimension, "prop", { x: c.x + Math.cos(a) * 1.2, y: c.y + 1.6 + Math.random(), z: c.z + Math.sin(a) * 1.2 }, { dir: unit({ x: Math.cos(a), y: 0.4, z: Math.sin(a) }), speed: 1.5, size: 0.18, life: 1.0, spin: Math.random() * 360, spinRate: 120, cell: RFX.feather[0], color: wingCol, fade: 0.5 });
    }
    play(player.dimension, "beacon.deactivate", player.location, 1.2, 0.8);
  });
  return true;
}

// ============================================================ 번개 (보랏빛: 튀고, 순간이동하고, 기절)
/** 번개 한 줄기 (빛 + 흰 심지) */
function zap(dim, a, b, glow, core, thick = 0.22, life = 0.3) {
  bolt(dim, a, b, glow, { jag: 0.5, thick, life });
  bolt(dim, a, b, core, { jag: 0.3, thick: thick * 0.35, life: life * 0.8 });
}

/**
 * 연쇄 번개: 대상(range 칸)에게 번개 → chainRange 칸 안의 새 적에게 chains 번 튐 (gap 틱마다) — v2.25.0 손끝의 번개 구슬 (rpg:rfx_plasma),
 * 맞은 적마다 몸에 번개 구슬이 터지고 연쇄가 끝날 때까지 남음
 */
function chainLightning(player, r, P, k) {
  const first = pickTarget(player, k.range, k.cone);
  if (!first) {
    warn(player, `§c${r.def.name}: §7${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.storm;
  const chain = [first];
  for (let i = 0; i < k.chains; i++) {
    const cur = chain[chain.length - 1];
    const next = nearestTo(around(player, cur.location, k.chainRange, 3).filter((e) => !chain.some((c) => c.id === e.id)), cur.location)[0];
    if (!next) break;
    chain.push(next);
  }
  const hand = bowHand(player);
  const spots = chain.map((e) => bodyPoint(e));
  fx(dim, "rpg:magic_circle_flash", hand, { size: 0.35, life: 0.2, spin: Math.random() * 360, progress: 1, color: glow, alpha: 1 });
  plasmaOrb(dim, add(hand, player.getViewDirection(), 0.7), 0.15, glow, { life: 0.2 });
  chain.forEach((e, i) => {
    later(i * k.gap, () => {
      if (!player.isValid) return;
      const from = i === 0 ? bowHand(player) : spots[i - 1];
      const to = e.isValid ? bodyPoint(e) : spots[i];
      spots[i] = to;
      const hold = (chain.length - i) * (k.gap / 20) + 0.25; // 연쇄가 끝날 때까지 이어진 번개 · 구슬이 남음
      zap(dim, from, to, glow, core, 0.22, hold);
      plasmaOrb(dim, to, 0.55 + bodyPad(e) * 0.4, glow, { life: hold, fade: 0.3, vel: e.isValid ? entVel(e) : undefined }); // 적을 따라감
      burst(dim, to, glow, 1.3, 0.25);
      sparks(dim, to, core, 6, 5);
      play(dim, "ambient.weather.lightning.impact", to, 1.6 + i * 0.1, 0.25);
      if (e.isValid) runeHit(player, e, P, k.mult, r.stat);
    });
  });
  return true;
}

/**
 * 섬광 이동: 앞으로 dist 칸(벽 앞까지) 순간이동, 지나간 길 가까이 width 칸의 적 감전 — v2.25.0 떠난 자리에 번개 빛 그림자 잔상 (rpg:rfx_shade) ·
 * 번개 구슬, 지나간 길의 적마다 번개 구슬
 */
function blink(player, r, P, k) {
  const dim = player.dimension;
  const start = { ...player.location };
  const f = flat(player.getViewDirection());
  const dist = dashDistance(player, f, k.dist, [1.1, 1.8]); // 한 칸 턱은 넘음
  const end = add(start, f, dist);
  end.y = groundAt(dim, end, start.y);
  const [glow, core] = RUNE.color.storm;
  const path = lineTargets(player, up(start, 1.0), f, Math.max(0.5, dist + 0.5), k.width);
  burst(dim, up(start, 1), glow, 1.2, 0.25);
  sparks(dim, up(start, 1), core, 6, 5);
  zap(dim, up(start, 1.0), up(end, 1.0), glow, core, 0.18, 0.35);
  // 떠난 자리: 번개 빛 그림자 잔상 + 번개 구슬 (옮긴 뒤 1인칭에서는 등 뒤)
  if (dist > 1.5) {
    shadeClone(player, dim, start, f, 0, [0.45, 0.35, 0.95], WHITE, { life: 0.5, fade: 0.7, alpha: 0.6 }); // 밝고 반투명한 번개빛 잔상
    plasmaOrb(dim, up(start, 1.0), 0.5, glow, { life: 0.3 });
  }
  play(dim, "mob.endermen.portal", start, 1.4, 0.8);
  play(dim, "item.trident.thunder", start, 1.8, 0.3);
  if (dist > 0.2) moveTo(player, end);
  // 도착: 번쩍 + 눈앞 고리 (1인칭)
  burst(dim, up(end, 1), core, 1.0, 0.25);
  zonePulse(dim, end, glow, 2.2, 0.35);
  ring(dim, add(up(end, 1.5), f, 0.9), f, glow, 1.0, 0.3, 2.5);
  const shocked = [];
  for (const { e } of path) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) {
      burst(dim, bodyPoint(e), glow, 0.7, 0.2);
      shocked.push(bodyPoint(e));
    }
  }
  // 감전 줄기 · 번개 구슬: 한 틱 뒤 (순간이동하는 틱의 파티클 예산), 6명까지
  if (shocked.length) {
    later(1, () => {
      for (const at of shocked.slice(0, 6)) {
        zap(dim, add(up(start, 1.0), f, Math.min(dist, dot(sub(at, start), f))), at, glow, core, 0.1, 0.22);
        plasmaOrb(dim, at, 0.4, glow, { life: 0.3 });
      }
    });
  }
  return true;
}

/**
 * 뇌전 감옥: 조준한 곳(range 칸) 테두리에 번개가 둘러친 뒤(delay 틱) 안쪽 radius 칸의 적 피해 + 기절(stun) — v2.25.0 테두리 기둥 넷에 번개 구슬이 모이고 →
 * 하늘에서 번개 넷 (rpg:rfx_bolt) · 테두리를 따라 번개 창살 8개가 1.5초 동안 깜빡이며 섬, 바닥 · 위 (3칸) 의 고리 (갇힌 모양)
 */
function staticPrison(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = RUNE.color.storm;
  const rim = (a) => ({ x: g.x + Math.cos(a) * k.radius, y: g.y + 0.6, z: g.z + Math.sin(a) * k.radius });
  const posts = [0, 1, 2, 3].map((i) => rim((i / 4) * Math.PI * 2 + 0.4));
  floorRune(dim, g, { size: k.radius, life: k.delay / 20 + 0.3, color: glow, alpha: 0.85, rate: 120, grow: 0 });
  for (let t = 0; t < k.delay; t += 3) {
    later(t, () => {
      const a = Math.random() * Math.PI * 2;
      bolt(dim, rim(a), rim(a + 0.8), glow, { jag: 0.35, thick: 0.08, life: 0.18 });
      // 기둥 넷에 모이는 번개 구슬 (커짐)
      for (const p of posts) plasmaOrb(dim, up(p, 0.6), 0.3 + (0.3 * (t + 3)) / k.delay, glow, { life: 0.17, fade: 0 });
    });
  }
  play(dim, "mob.evocation_illager.prepare_attack", g, 1.2, 0.8);
  play(dim, "respawn_anchor.charge", g, 1.2, 0.8);
  later(k.delay, () => {
    if (!player.isValid) return;
    for (let i = 0; i < 8; i++) bolt(dim, rim((i / 8) * Math.PI * 2), rim(((i + 1) / 8) * Math.PI * 2), glow, { jag: 0.4, thick: 0.2, life: 0.45 });
    for (const p of posts) {
      const gp = up(p, -0.6);
      rfx2(dim, "bolt", up(gp, 3.2), { size: 3.2, life: 0.26, cell: Math.floor(Math.random() * 4) }, glow, WHITE, [0, RFX.boltCore]);
    }
    burst(dim, up(g, 1), glow, 2.2, 0.35);
    zonePulse(dim, g, glow, k.radius, 0.45);
    play(dim, "ambient.weather.lightning.impact", g, 1.2, 0.5);
    play(dim, "mob.ravager.stun", g, 1.0, 0.8);
    const stunned = [];
    for (const e of around(player, g, k.radius, 4)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        stun(e, k.stun, glow, stunned.length < 16); // 머리 위 기절 표시는 16명까지 (파티클 예산 — 기절은 모두)
        stunned.push(e);
      }
    }
    // 감옥: 바닥 고리 + 위 (3칸) 의 뚜껑 고리 — 창살과 함께 갇힌 모양 (기절 동안)
    floorRune(dim, g, { size: k.radius, life: k.stun / 20, color: glow, alpha: 0.85, rate: -40 });
    floorRune(dim, up(g, 3.0), { size: k.radius * 0.95, life: Math.min(k.stun, 32) / 20, color: core, alpha: 0.8, rate: 60 });
    // 가운데에서 갇힌 적에게 튀는 번개: 한 틱 뒤, 4명까지 (파티클 예산)
    later(1, () => {
      for (const e of stunned.slice(0, 4)) if (e.isValid) zap(dim, up(g, 0.6), bodyPoint(e), glow, core, 0.1, 0.25);
    });
  });
  // 번개 창살: 테두리 8곳에 1.5초 (3틱마다 다른 모양으로 다시 그림 — 깜빡임)
  for (let t = k.delay + 2; t < k.delay + 32; t += 3) {
    later(t, () => {
      for (let i = 0; i < 8; i++) {
        const p = rim((i / 8) * Math.PI * 2 + 0.2);
        rfx2(dim, "bolt", up(p, 0.95), { size: 1.55, life: 0.18, cell: (i + t) % 4, alpha: 1 }, glow, WHITE, [0, RFX.boltCore], 0.95);
      }
    });
  }
  return true;
}

/**
 * 뇌운: 조준한 곳(range 칸) 위 height 칸에 먹구름 (rpg:rfx_cloud — 짙은 보랏빛 덩이). gap 틱마다 hits 번, 반지름 radius 안에서 가장 오래 안 맞은 적에게
 * 번개 (rpg:rfx_bolt — 하늘에서 내리침, 떨어진 곳 splash 칸)
 */
function thundercloud(player, r, P, k) {
  const dim = player.dimension;
  const g = groundAim(player, k.range);
  const [glow, core] = RUNE.color.storm;
  const dark = [0.3, 0.25, 0.42];
  const total = (k.hits - 1) * k.gap + 10;
  const sky = up(g, k.height);
  floorRune(dim, g, { size: k.radius, life: total / 20 + 0.3, color: glow, alpha: 0.5, rate: 20, grow: 0 });
  const puffs = [0, 1, 2, 3, 4, 5].map((i) => {
    const a = (i / 5) * Math.PI * 2 + Math.random() * 0.5;
    const rr = i === 0 ? 0 : k.radius * 0.62;
    return { x: Math.cos(a) * rr, z: Math.sin(a) * rr, s: i === 0 ? 2.8 : 2.0, cell: i % 4 };
  });
  for (let t = 0; t < total; t += 8) {
    later(t, () => {
      for (const p of puffs) rfx(dim, "cloud", { x: sky.x + p.x, y: sky.y + (p.s > 2.5 ? 0.6 : 0.1), z: sky.z + p.z }, { size: p.s, life: 0.48, cell: p.cell, color: dark, alpha: 0.92, fade: t + 8 >= total ? 0.5 : 0 });
      // 구름 속 번쩍임
      if (t % 16 === 0) fx(dim, "rpg:arrow_spark", add(sky, { x: (Math.random() - 0.5) * k.radius, y: 0.2, z: (Math.random() - 0.5) * k.radius }, 1), { dir: V_UP, size: 1.6, grow: 1.5, life: 0.18, color: glow, alpha: 0.85 });
    });
  }
  play(dim, "ambient.weather.thunder", g, 1.2, 0.5);
  const struck = new Map();
  every(k.hits, k.gap, (i) => {
    if (!player.isValid) return;
    const list = around(player, g, k.radius, 5);
    for (let a = list.length - 1; a > 0; a--) {
      const b = Math.floor(Math.random() * (a + 1));
      [list[a], list[b]] = [list[b], list[a]];
    }
    list.sort((a, b) => (struck.get(a.id) ?? -1) - (struck.get(b.id) ?? -1));
    const tgt = list[0];
    const ang = Math.random() * Math.PI * 2;
    const rr = Math.sqrt(Math.random()) * k.radius * 0.8;
    const at = tgt ? { ...tgt.location } : { x: g.x + Math.cos(ang) * rr, y: g.y, z: g.z + Math.sin(ang) * rr };
    // 하늘 → 땅 번개 (그림 한 장, 키 = 구름 높이) + 가는 지그재그 줄기
    rfx2(dim, "bolt", up(at, k.height / 2), { size: k.height / 2, life: 0.24, cell: Math.floor(Math.random() * 4) }, glow, WHITE, [0, RFX.boltCore]);
    bolt(dim, { x: at.x + (Math.random() - 0.5) * 1.2, y: sky.y, z: at.z + (Math.random() - 0.5) * 1.2 }, up(at, 0.1), core, { jag: 0.5, thick: 0.1, life: 0.2 });
    burst(dim, up(at, 0.5), core, 1.4, 0.25);
    zonePulse(dim, at, glow, k.splash, 0.35);
    play(dim, "ambient.weather.lightning.impact", at, 1.0 + Math.random() * 0.3, 0.45);
    for (const e of around(player, at, k.splash, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) struck.set(e.id, i);
    }
  });
  return true;
}

/**
 * 뇌신의 창: charge 틱 동안 눈앞 위에 번개 창 (rpg:rfx_spear) 이 모임 → 던짐. 시선 방향 range 칸(벽 앞까지)을 꿰뚫어 폭 width 칸의 적 피해·기절,
 * 맞은 적마다 하늘에서 번개 (rpg:rfx_bolt) + chainRange 칸 안의 새 적 하나에게 연쇄(chainMult)
 */
function thunderSpear(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.storm;
  const spearAt = () => add(up(player.getHeadLocation(), 0.62), flat(player.getViewDirection()), 1.5);
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!player.isValid) return;
      const view = player.getViewDirection();
      const at = spearAt();
      const grow = 0.4 + 0.6 * (t / k.charge);
      rfx2(dim, "spear", at, { dir: view, size: 1.25 * grow, life: 0.14, cell: (t / 2) % 4 }, glow, WHITE, [0, RFX.spearCore]);
      fx(dim, "rpg:magic_circle", add(at, view, -1.8), { size: 0.5 + 0.5 * grow, life: 0.14, spin: t * 18, progress: Math.min(1, (t + 2) / k.charge), color: glow, alpha: 0.9 }); // 창 뒤 · 눈 뒤 (1인칭 화면을 가리지 않음)
      const a = Math.random() * Math.PI * 2;
      bolt(dim, add(at, { x: Math.cos(a) * 1.6, y: Math.sin(a) * 1.0, z: Math.sin(a) * 1.6 }), at, glow, { jag: 0.35, thick: 0.09, life: 0.14 });
    });
  }
  play(dim, "item.trident.riptide_3", player.location, 0.6, 0.8);
  play(dim, "mob.evocation_illager.cast_spell", player.location, 0.8, 0.8);
  // v2.27.0 2차 강화 — 예고: 폭풍 하늘 · 앞쪽 하늘의 먹구름 둘 · 발밑의 칼날 문양 (빠르게 돎) · 모으는 동안 앞쪽 땅에 하늘에서 번개 셋
  const c0 = { ...player.location };
  const f0 = flat(player.getViewDirection());
  ultSkyFor(dim, c0, "thunderSpear", k.charge + 44);
  const storm = skyPoint(player, add(c0, f0, 10), 6, 20);
  for (const sd of [-1, 1]) rfx(dim, "cloud", add(storm, rightOf(f0), sd * 4.5), { size: 5.2, life: (k.charge + 24) / 20, grow: 0.2, cell: sd > 0 ? 1 : 2, color: [0.3, 0.24, 0.46], alpha: 0.85, fade: 0.35 });
  domainSigil(dim, c0, 5, "blade", glow, WHITE, { life: (k.charge + 2) / 20, spinRate: 120, grow: 0.3, alpha: 0.8, fade: 0.15 });
  for (let i = 0; i < 3; i++) {
    later(3 + i * 6, () => {
      if (!player.isValid) return;
      const p = add(add(player.location, f0, 9 + Math.random() * 7), rightOf(f0), (Math.random() - 0.5) * 10);
      const gy = groundAt(dim, p, player.location.y);
      rfx2(dim, "bolt", { x: p.x, y: gy + 6, z: p.z }, { size: 6, life: 0.24, cell: (i * 3) % 4 }, glow, WHITE, [0, RFX.boltCore]);
      play(dim, "ambient.weather.thunder", p, 0.6, 0.6);
    });
  }
  later(k.charge, () => {
    if (!player.isValid) return;
    const view = player.getViewDirection();
    // 막힘·맞는 대상은 눈에서 (머리 위에서 재면 두 칸 높이 굴에서는 천장에 막힘), 날아가는 창은 눈 조금 위
    const from = add(player.getHeadLocation(), view, 0.5);
    const L = Math.max(1, rayLength(dim, from, view, k.range));
    const speed = 50;
    const shown = add(from, { x: 0, y: 0.25, z: 0 });
    rfx2(dim, "spear", add(shown, view, 1.2), { dir: view, speed, size: 1.35, life: Math.max(0.05, (L - 1.2) / speed), cell: Math.floor(Math.random() * 4) }, glow, WHITE, [0, RFX.spearCore]);
    for (let s = 0; s < L; s += 4) {
      later(Math.round((s / speed) * 20), () => bolt(dim, add(from, view, s), add(from, view, Math.min(L, s + 4)), glow, { jag: 0.45, thick: 0.12, life: 0.35 }));
    }
    play(dim, "item.trident.throw", from, 0.5, 1);
    play(dim, "item.trident.thunder", from, 0.8, 1);
    shake(player, 0.2, 0.2);
    // v2.27.0 발동: 보랏빛 번쩍임 · 발밑에서 퍼지는 고리 · 창이 날아가는 길에 하늘에서 번개 셋 (가까운 곳부터)
    ultFlashFor(player, dim, from, "thunderSpear");
    novaRing(dim, player.location, 1.5, 4, glow, WHITE, { life: 0.45 });
    for (let i = 0; i < 3; i++) {
      const s = L * (0.3 + i * 0.3);
      later(Math.round((s / speed) * 20) + 1, () => {
        const p = add(from, view, s);
        const gy = groundAt(dim, p, from.y - 1.6);
        rfx2(dim, "bolt", { x: p.x, y: gy + 7, z: p.z }, { size: 7, life: 0.28, cell: (i + 1) % 4 }, glow, WHITE, [0, RFX.boltCore]);
      });
    }
    const line = lineTargets(player, from, view, L, k.width);
    const hit = new Set(line.map((x) => x.e.id));
    for (const { e, t } of line) {
      later(Math.round((t / speed) * 20), () => {
        if (!player.isValid || !e.isValid) return;
        if (runeHit(player, e, P, k.mult, r.stat) <= 0) return;
        const show = fxQuota("spearHit", 4); // v2.27.0 한 틱에 4명까지 그림 (줄지어 선 적 24명 — 파티클 예산, 피해 · 기절은 모두)
        stun(e, k.stun, glow, show);
        const at = bodyPoint(e);
        if (show) {
          rfx2(dim, "bolt", up(e.location, 3.6), { size: 3.6, life: 0.28, cell: Math.floor(Math.random() * 4) }, glow, WHITE, [0, RFX.boltCore]); // 하늘에서 내리치는 번개
          burst(dim, at, glow, 1.8, 0.3);
          sparks(dim, at, core, 8, 6);
        }
        play(dim, "ambient.weather.lightning.impact", at, 1.1, 0.4);
        const next = nearestTo(around(player, e.location, k.chainRange, 3).filter((x) => !hit.has(x.id)), e.location)[0];
        if (!next) return;
        hit.add(next.id);
        later(3, () => {
          if (!player.isValid || !next.isValid) return;
          zap(dim, at, bodyPoint(next), glow, core, 0.14, 0.3);
          if (runeHit(player, next, P, k.chainMult, r.stat) > 0) {
            stun(next, k.stun, glow);
            burst(dim, bodyPoint(next), glow, 1.0, 0.24);
          }
        });
      });
    }
    later(Math.round((L / speed) * 20), () => {
      const endp = add(from, view, L);
      burst(dim, endp, glow, 3.6, 0.4);
      burst(dim, endp, core, 1.8, 0.3);
      glowCrack(dim, { x: endp.x, y: groundAt(dim, endp, endp.y - 1.2), z: endp.z }, 3.4, glow, WHITE, { life: 1.8 }); // v2.27.0 창이 꽂힌 땅이 갈라져 빛남
      zonePulse(dim, up(endp, -1), glow, 3, 0.45);
      for (let i = 0; i < 3; i++) {
        const a = (i / 3) * Math.PI * 2 + Math.random();
        const g = { x: endp.x + Math.cos(a) * 1.6, y: endp.y - 1.2, z: endp.z + Math.sin(a) * 1.6 };
        rfx2(dim, "bolt", up(g, 3), { size: 3, life: 0.24, cell: i % 4 }, glow, WHITE, [0, RFX.boltCore]);
      }
      for (let i = 0; i < 4; i++) {
        const a = (i / 4) * Math.PI * 2;
        zap(dim, endp, add(endp, { x: Math.cos(a) * 2.2, y: (Math.random() - 0.3) * 1.5, z: Math.sin(a) * 2.2 }), glow, core, 0.14, 0.3);
      }
      play(dim, "ambient.weather.lightning.impact", endp, 0.9, 0.7);
    });
  });
  return true;
}

// ============================================================ v1.6.0 암살자 (자홍: 파고들고·숨고·난도질, 표식과 이어 쓰기 콤보)
const SHADE = [0.26, 0.04, 0.34]; // 그림자 (짙은 자홍)
const SMOKE = [0.38, 0.32, 0.44]; // 연막 (회보라)
const POISON = [0.45, 1.0, 0.35]; // 독 (연두)
const MOON = [0.93, 0.9, 1.0]; // 달빛
const CLONE = [0.4, 0.12, 0.5]; // v2.25.0 그림자 분신의 몸 (그림자보다 밝게 — 풀밭 위에서도 사람 모양이 보이게)

/** 대상 뒤(안 되면 앞) 1.3칸의 설 자리 (standSpot: 단단한 땅 위, 물·용암·허공·막힌 곳 제외). 둘 다 안 되면 undefined */
function behindSpot(dim, target, from) {
  const tl = target.location;
  const d = flat(sub(tl, from));
  for (const k of [1.3, -1.3]) {
    const at = standSpot(dim, add(tl, d, k));
    if (at) return at;
  }
  return undefined;
}

/** 플레이어가 진짜로 죽거나(entityDie) 차원을 옮긴 횟수. 불사의 토템으로 살아남은 것은 세지 않음 */
const lives = new Map();

/**
 * 누른 때의 삶 표시 (차원 + 죽음·차원 이동 횟수): 나중에 나가는 룬·마무리 기술·순간이동은 이것이 같을 때만
 * (그 사이 죽어서 다시 태어났거나 다른 차원으로 가면 멈춤 — 다시 태어난 자리에서 옛 대상에게 옮겨지지 않게)
 */
function lifeToken(player) {
  try {
    return `${player.dimension.id}|${lives.get(player.id) ?? 0}`;
  } catch {
    return "";
  }
}

/** 죽음·차원 이동: 삶 표시가 바뀌고, 콤보와 암살 표식이 끝남 */
function onLifeEnded(playerId) {
  lives.set(playerId, (lives.get(playerId) ?? 0) + 1);
  combos.delete(playerId);
  for (const key of marks.keys()) if (key.startsWith(`${playerId}|`)) marks.delete(key);
  imbues.delete(playerId); // v2.13.0 마검 부여도 끝 (탈것을 부를 때도)
  dismissSpirits(playerId); // v2.14.0 정령도 흩어짐
}

/** v2.8.0 탈것을 부름: 대기 중인 룬 · 이어 쓰기 · 여러 번 베는 룬의 남은 타격 · 순간이동을 멈춤 (죽음 · 차원 이동과 같은 삶 표시) */
export function interruptRunes(playerId) {
  onLifeEnded(playerId);
}

/** v2.24.0 죽은 횟수 (죽음으로만 끝나는 버프 — 광전사의 혼 · 대천사 강림 · 불사의 맹약: 차원 이동 · 탈것 부르기로는 끝나지 않음) @type {Map<string, number>} */
const deaths = new Map();
function deathToken(player) {
  return deaths.get(player.id) ?? 0;
}
/** 그 버프를 건 뒤 죽지 않았는지 (쓰러지는 중도 아님) */
function aliveSince(player, token) {
  try {
    return player.isValid && !isDying(player) && deathToken(player) === token;
  } catch {
    return false;
  }
}

function sameLife(player, token) {
  try {
    return player.isValid && !isDying(player) && lifeToken(player) === token;
  } catch {
    return false;
  }
}

/** 그림자 걸음 자리: 짙은 연기 + 자홍 불꽃 */
function shadowPuff(dim, at, glow) {
  burst(dim, up(at, 1.0), SHADE, 1.3, 0.35);
  burst(dim, up(at, 0.9), glow, 0.7, 0.25);
  sparks(dim, up(at, 1.0), glow, 6, 3);
}

/** 암살자 룬 피해: 맞으면 암살 표식 */
function assassinHit(player, e, P, mult) {
  const d = runeHit(player, e, P, mult, "dex");
  if (d > 0) markTarget(player, e);
  return d;
}

/**
 * 배후 일격: 대상 뒤로 순간이동해 찌름 — v2.25.0 떠난 자리에 그림자 잔상 (rpg:rfx_shade), 대상 옆에서 그림자 분신이 함께 찌르고,
 * 오른손에서 빛의 단검 (rpg:rfx_sword 작게) 이 몸을 꿰뚫음 (가까운 적의 X 는 작게)
 */
function backstab(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const from = { ...player.location };
  const [glow, core] = RUNE.color.assassin;
  const dest = behindSpot(dim, target, from);
  shadowPuff(dim, from, glow);
  play(dim, "mob.endermen.portal", from, 1.5, 0.7);
  if (dest) {
    // 떠난 자리의 그림자 잔상 (대상 쪽을 봄)
    shadeClone(player, dim, from, flat(sub(target.location, from)), 0, CLONE, glow, { life: 0.45, fade: 0.7, alpha: 0.7 });
    beam(dim, up(from, 1), up(dest, 1), SHADE, { half: 0.3, thick: 0.28, life: 0.3, alpha: 0.75 });
    try {
      player.teleport(dest, { keepVelocity: false, checkForBlocks: true, facingLocation: target.getHeadLocation() });
    } catch {
      // 못 옮기면 그 자리에서 찌름
    }
    shadowPuff(dim, dest, glow);
  }
  const eye = player.getHeadLocation();
  const at = bodyPoint(target);
  const v = unit(sub(at, eye));
  // 빛의 단검: 오른손 (눈 오른쪽 아래) 에서 몸 너머 0.6칸까지 찌름 (2틱 — 1인칭에서 손에서 비스듬히 뻗어 보임)
  const fv = flat(v);
  const hand = up(add(add(eye, rightOf(fv), 0.3), fv, 0.45), -0.3);
  const tip = add(at, v, 0.6);
  const dv = unit(sub(tip, hand));
  const L = len(sub(tip, hand));
  const S = 0.35;
  rfx2(dim, "sword", add(hand, dv, Math.min(L, S + 0.15)), { dir: dv, speed: Math.max(0, L - 2 * S - 0.15) / 0.1, size: S, life: 0.1 }, glow, core);
  // 대상 옆 (내 쪽에서 ±100°) 에서 그림자 분신이 함께 찌름 (가까우면 작게)
  const tf = flat(sub(target.location, player.location));
  const flank = turn(tf, Math.random() < 0.5 ? 100 : -100);
  const pad = 0.9 + bodyPad(target);
  const cf = add(target.location, flank, -pad);
  shadeClone(player, dim, cf, flank, 3, CLONE, glow, { life: 0.4, fade: 0.5, alpha: 0.9, size: 0.95 * nearK(player, up(cf, 1), 3.5, 0.55) });
  if (assassinHit(player, target, P, k.mult) > 0) {
    // 몸 앞(보는 쪽 0.6칸)에 그림: 바로 뒤에서 찌르므로 몸통 안에 그리면 몹에 가려짐 — 가까우면 작게 (1인칭 화면을 덮지 않게)
    const xs = nearK(player, add(at, v, -0.6), 3, 0.2);
    fx(dim, "rpg:slash_x", add(at, v, -0.45), { dir: v, size: 1.3 * xs, life: 0.3, color: glow, alpha: 1 });
    fx(dim, "rpg:slash_x", add(at, v, -0.5), { dir: v, size: 0.9 * xs, life: 0.26, color: core, alpha: 1 });
    sparks(dim, add(at, v, -0.5), core, 7, 5);
    play(dim, "game.player.attack.critical", at, 1.3, 0.9);
  }
  return true;
}

/** 독 표창: 표창 stars 개를 부채꼴(spread 도)로 (도는 표창 = rpg:rfx_prop 표창), 표창마다 처음 닿는 적 (적마다 한 번), 맞으면 dotGap 틱마다 dots 번 중독 피해 */
function poisonStars(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const view = player.getViewDirection();
  const f = flat(view);
  const [glow, core] = RUNE.color.assassin;
  const hit = new Set();
  const cands = around(player, player.location, k.range + 1, 4);
  for (let i = 0; i < k.stars; i++) {
    const deg = -k.spread / 2 + (k.spread * i) / Math.max(1, k.stars - 1);
    const h = turn(f, deg);
    const d = unit({ x: h.x, y: view.y * 0.6, z: h.z });
    let best;
    let bestT = k.range;
    for (const e of cands) {
      const q = bodyToward(e, head, d);
      const p = sub(q, head);
      const t = dot(p, d);
      if (t < 0 || t > bestT) continue;
      if (len(sub(p, scale(d, t))) > 0.9 + bodyPad(e)) continue;
      if (!lineOfSight(player, head, q)) continue;
      best = e;
      bestT = t;
    }
    const L = Math.min(best ? bestT : k.range, rayLength(dim, head, d, k.range));
    const from = add(head, d, 0.6);
    const ticks = flyStreak(dim, from, add(head, d, L), k.speed, POISON, core, 0.4, 0.14);
    // 도는 표창 (날아가는 길, 2틱마다 한 장 — 손 앞 1칸 안은 그리지 않음)
    for (let t = 0; t < ticks; t += 2) {
      const s = ((t + 1) / ticks) * Math.max(0, L - 0.6);
      if (s < 0.7) continue;
      const pos = add(from, d, s);
      later(t, () => rfx2(dim, "prop", pos, { size: 0.3, life: 0.12, spin: t * 70 }, glow, core, RFX.shuriken));
    }
    if (best && !hit.has(best.id)) {
      hit.add(best.id);
      const target = best;
      later(ticks, () => {
        if (!player.isValid || !target.isValid) return;
        if (assassinHit(player, target, P, k.mult) <= 0) return;
        const at = bodyPoint(target);
        rfx2(dim, "prop", add(at, d, -0.3), { size: 0.34, life: 0.5, spin: 30, fade: 0.4 }, POISON, core, RFX.shuriken); // 꽂힌 표창
        fx(dim, "rpg:slash_x", at, { dir: d, size: 0.8, life: 0.22, color: glow, alpha: 1 });
        burst(dim, at, POISON, 0.7, 0.3);
        play(dim, "item.trident.hit", at, 1.8, 0.5);
        // 중독: dotGap 틱마다 dots 번 (첫 타와 무적 시간이 겹치지 않게 dotGap 뒤부터, 맞을 때마다 표식도 새로)
        for (let j = 1; j <= k.dots; j++) {
          later(j * k.dotGap, () => {
            if (!player.isValid || !target.isValid) return;
            if (assassinHit(player, target, P, k.dot) > 0) {
              const b = bodyPoint(target);
              sparks(dim, up(b, 0.3), POISON, 4, 1.5);
              fx(dim, "rpg:arrow_spark", up(b, 0.6), { dir: V_UP, speed: 1.2, size: 0.14, grow: 1.4, life: 0.5, color: POISON, alpha: 0.9 });
            }
          });
        }
      });
    }
  }
  arc(dim, up(add(head, f, 1.2), -0.2), f, { shape: "thin", flip: true, size: 1.4, life: 0.2, speed: 4, spin: 80, color: glow });
  play(dim, "item.trident.throw", head, 1.8, 0.7);
  play(dim, "random.bow", head, 2.0, 0.5);
  return true;
}

/**
 * 연막: 연막 속 적을 hits 번 베고 몸을 숨김 — v2.25.0 손에서 연막탄 (rpg:rfx_bomb) 이 발 앞에 떨어져 터지고, 짙은 연기 덩이 (rpg:rfx_cloud) 가 hide 틱 동안 피어오름.
 * 벨 때마다 연기 속에서 그림자 분신이 나타나 벰 (3명까지)
 */
function smokeBomb(player, r, P, k) {
  const dim = player.dimension;
  const c = { ...player.location };
  const [glow, core] = RUNE.color.assassin;
  const f0 = flat(player.getViewDirection());
  effect(player, "invisibility", k.hide, 0);
  effect(player, "speed", k.hide, 0);
  // 연막탄: 손 앞 → 발 앞 1.8칸 (3틱), 도는 탄 + 심지 불티 꼬리
  const hand = add(bowHand(player), player.getViewDirection(), 0.6);
  const drop = up(add(c, f0, 1.8), 0.2);
  const dv = sub(drop, hand);
  rfx(dim, "bomb", hand, { dir: unit(dv), speed: len(dv) / 0.15, size: 0.2, life: 0.15, spin: 0, spinRate: 720, frames: 2, fps: 20, loop: true });
  for (let i = 1; i <= 2; i++) later(i, () => fx(dim, "rpg:arrow_spark", add(hand, dv, i / 3), { dir: V_UP, speed: 0.5, size: 0.1, grow: 0.5, life: 0.3, color: [1.0, 0.7, 0.4], alpha: 1 })); // 지나간 자리의 불티
  later(3, () => {
    burst(dim, up(c, 0.8), SMOKE, 2.4, 0.5);
    burst(dim, up(c, 0.6), SHADE, 1.6, 0.4);
    sparks(dim, drop, [1.0, 0.7, 0.4], 6, 4);
    if (player.isValid) selfCue(player, SMOKE, glow);
    play(dim, "random.explode", c, 1.8, 0.4);
  });
  play(dim, "random.fizz", c, 0.7, 1);
  play(dim, "fire.ignite", c, 0.6, 0.8);
  play(dim, "mob.endermen.portal", c, 1.2, 0.6);
  // 연기 덩이가 hide 틱 동안 피어오름: 둘레와 안쪽 (조금씩 도는 자리), 처음 1초는 짙게
  for (let t = 2; t < k.hide; t += 5) {
    later(t, () => {
      const n = t < 20 ? 7 : 5;
      const last = t + 5 >= k.hide;
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2 + t * 0.07;
        const rr = (0.35 + 0.55 * ((i + Math.floor(t / 5)) % 2)) * k.radius; // 안쪽 0.35 · 바깥 0.9 (범위 안)
        const p = { x: c.x + Math.cos(a) * rr, y: c.y + 0.5 + (i % 3) * 0.45, z: c.z + Math.sin(a) * rr };
        // 1인칭 눈 · 3인칭 카메라 가까이의 연기는 옅게, 1.6칸 안이면 그리지 않음 (화면을 덮는 얼룩이 되지 않게)
        const cd = player.isValid ? camDist(player, p) : 99;
        if (cd < 1.6) continue;
        const near = Math.max(0.2, Math.min(1, (cd - 1) / 2.5));
        rfx(dim, "cloud", p, { size: 1.35 + (i % 2) * 0.4, life: 0.8, grow: 0.5, cell: (i + t) % 4, color: i % 3 ? SMOKE : SHADE, alpha: 0.52 * near, fade: last ? 0.6 : 0.4, dir: V_UP, speed: 0.3, spin: ((i * 37 + t * 3) % 30) - 15 });
      }
      // 연막 안에 선 내 눈앞에도 (1인칭에서 연기 속에 있는 것이 보이게 — 옅게)
      if (player.isValid && len(sub(player.location, c)) <= k.radius) {
        const eye = player.getHeadLocation();
        const f = flat(player.getViewDirection());
        for (const sd of [-1, 1]) rfx(dim, "cloud", up(add(add(eye, f, 2.4), rightOf(f), sd * (1.0 + (t % 10) * 0.05)), -0.6), { size: 0.75, life: 0.7, grow: 0.4, cell: (t + (sd > 0 ? 1 : 2)) % 4, color: SMOKE, alpha: 0.32, fade: 0.5 });
      }
      if (t % 10 === 2) for (let j = 0; j < 2; j++) particle(dim, "minecraft:basic_smoke_particle", { x: c.x + (Math.random() - 0.5) * k.radius, y: c.y + 0.6, z: c.z + (Math.random() - 0.5) * k.radius });
    });
  }
  const life = lifeToken(player);
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    zonePulse(dim, c, glow, k.radius, 0.35);
    let shown = 0;
    for (const e of around(player, c, k.radius)) {
      if (assassinHit(player, e, P, k.mult) <= 0) continue;
      const at = bodyPoint(e);
      const v = unit(sub(at, up(c, 1.2)));
      if (shown < 16) cutMark(dim, at, v, i % 2 ? 35 : -35, 0.08, glow, core, 0.8); // 베인 자국은 16명까지 (파티클 예산)
      // 연기 속 그림자 분신이 벰 (3명까지 — 파티클 예산)
      if (shown++ < 3) {
        const side = turn(flat(sub(e.location, c)), (i % 2 ? 1 : -1) * 70 + 180);
        const cf = add(e.location, side, 0.85 + bodyPad(e));
        shadeClone(player, dim, cf, scale(side, -1), 2, CLONE, glow, { life: 0.3, fade: 0.5, alpha: 0.85, size: 0.95 * nearK(player, up(cf, 1), 3.5, 0.55) });
      }
      if (i === 0) {
        effect(e, "slowness", k.slowTicks, 1);
        effect(e, "weakness", k.slowTicks, 0);
      }
    }
    play(dim, "item.trident.hit", c, 1.5 + i * 0.1, 0.5);
  });
  return true;
}

/**
 * 그림자 난도질: 대상(range 칸)에게 달라붙어 gap 틱마다 hits 번 벰 (대상이 쓰러지면 4칸 안의 가장 가까운 적), 마지막은 큰 X —
 * v2.25.0 벨 때마다 대상 둘레의 다른 쪽에서 그림자 분신이 나타나 베고 (내 쪽 ±55° 는 비움), 마지막은 분신 셋이 한꺼번에 찌름
 */
function shadowFlurry(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.assassin;
  const from = { ...player.location };
  const d = flat(sub(target.location, from));
  const gap0 = Math.hypot(target.location.x - from.x, target.location.z - from.z);
  const want = Math.max(0, gap0 - 1.4);
  const dist = Math.min(want, dashDistance(player, d, want));
  // 설 수 있는 가장 먼 자리까지 (물·용암·허공이면 조금씩 짧게, 없으면 그 자리에서 벰)
  let to;
  for (let s = dist; s > 0.3 && !to; s -= 0.5) to = standSpot(dim, add(from, d, s));
  if (to) {
    beam(dim, up(from, 1), up(to, 1), SHADE, { half: 0.3, thick: 0.28, life: 0.3, alpha: 0.75 });
    shadowPuff(dim, from, glow);
    try {
      player.teleport(to, { keepVelocity: false, checkForBlocks: true, facingLocation: target.getHeadLocation() });
    } catch {
      // 그 자리에서 벰
    }
  }
  play(dim, "mob.vex.charge", from, 1.4, 0.8);
  let cur = target;
  const life = lifeToken(player);
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    if (!cur?.isValid || !canHit(player, cur)) {
      cur = nearestTo(around(player, player.location, 4), player.location)[0];
      if (!cur) return;
    }
    const at = bodyPoint(cur);
    const eye = player.getHeadLocation();
    const v = unit(sub(at, eye));
    const last = i === k.hits - 1;
    // 그림자 분신: 대상 둘레 (내 쪽에서 본 각 — 내 쪽 ±55° 는 비움), 대상을 보고 벰 (마지막은 셋이 찌름)
    const mine = flat(sub(player.location, cur.location));
    const pad = 0.9 + bodyPad(cur);
    for (const deg of last ? [90, 180, 270] : [[100, 250, 150, 210, 125][i % 5]]) {
      const sd = turn(mine, deg);
      const cf = add(cur.location, sd, pad);
      shadeClone(player, dim, cf, scale(sd, -1), last ? 3 : 2, CLONE, glow, { life: last ? 0.4 : 0.28, fade: 0.5, alpha: 0.9, size: 0.95 * nearK(player, up(cf, 1), 3.5, 0.55) });
    }
    const as = nearK(player, add(at, v, -0.6), 3, 0.45); // 달라붙어 베는 참격도 가까우면 작게
    arc(dim, add(at, v, -0.6), v, { shape: "thin", flip: i % 2 === 1, size: 1.5 * as, life: 0.2, speed: 2, spin: (i % 2 ? 1 : -1) * (30 + i * 12), color: glow, aura: SHADE });
    cutMark(dim, add(at, v, -0.5), v, i % 2 ? 50 - i * 8 : -50 + i * 8, last ? 0.05 : 0.12, glow, core, 0.9 * as);
    if (assassinHit(player, cur, P, k.mult) > 0) {
      sparks(dim, add(at, v, -0.5), core, last ? 10 : 4, last ? 6 : 4);
      if (last) {
        const xs = nearK(player, add(at, v, -0.6), 3, 0.25); // 달라붙어 베므로 X 가 눈앞 — 작게 (1인칭 화면을 덮지 않게)
        fx(dim, "rpg:slash_x", add(at, v, -0.6), { dir: v, size: 1.8 * xs, life: 0.32, color: glow, alpha: 1 });
        fx(dim, "rpg:slash_x", add(at, v, -0.65), { dir: v, size: 1.2 * xs, life: 0.28, color: WHITE, alpha: 1 });
        burst(dim, add(at, v, -0.5), SHADE, 1.2 * xs, 0.3);
        play(dim, "game.player.attack.critical", at, 1.0, 1);
      }
    }
    play(dim, last ? "item.trident.hit" : "game.player.attack.strong", at, 1.2 + i * 0.12, 0.7);
  });
  return true;
}

/**
 * 월광 암살: 대상(range 칸)에 달빛 표식 delay 틱 (대상 둔화, 나는 사라짐) — 대상 머리 위에 거대한 초승달 (rpg:rfx_moon) 이 차오름 →
 * 대상 뒤에 나타나 거대한 X 와 달빛 검기: 대상 mult, 대상 주위 splash 칸 splashMult
 */
function moonAssassin(player, r, P, k) {
  const target = pickTarget(player, k.range, k.cone);
  if (!target) {
    warn(player, `§c${r.def.name}: §7앞쪽 ${k.range}칸 안에 대상이 없습니다`);
    return false;
  }
  const dim = player.dimension;
  const [glow, core] = RUNE.color.assassin;
  const life = lifeToken(player);
  effect(target, "slowness", k.delay + 10, 3);
  effect(player, "invisibility", k.delay + 4, 0);
  shadowPuff(dim, player.location, glow);
  play(dim, "mob.evocation_illager.prepare_attack", player.location, 1.4, 0.8);
  play(dim, "chime.amethyst_block", target.location, 0.6, 1);
  // v2.27.0 2차 강화 — 예고: 한밤의 하늘 · 대상 뒤 하늘에 떠오르는 거대한 초승달 · 대상 발밑의 칼날 문양 (자홍)
  ultSkyFor(dim, target.location, "moonAssassin", k.delay + 34);
  const moonAt = skyPoint(player, target.location, 9, 20);
  rfx2(dim, "moon", moonAt, { size: 4.4, life: (k.delay + 6) / 20, grow: 0.12, spin: -20, alpha: 0.95, fade: 0.25 }, MOON, WHITE);
  domainSigil(dim, target.location, 3.4, "blade", glow, MOON, { life: (k.delay + 2) / 20, spinRate: -45, grow: 0.2, alpha: 0.8, fade: 0.15 });
  // 달빛 표식: 대상 머리 위 초승달이 커지며 차오르고 (달 그림 + 자홍 무리), 발밑에 자홍 룬
  floorRune(dim, target.location, { size: 1.6, life: k.delay / 20 + 0.2, color: glow, alpha: 0.85, rate: 120, progress: 1, grow: 0.3 });
  for (let t = 0; t < k.delay; t += 4) {
    later(t, () => {
      if (!target.isValid || !player.isValid) return;
      const top = up(target.getHeadLocation(), 2.2);
      rfx2(dim, "moon", top, { size: 0.9 + t * 0.03, life: 0.24, spin: -20, alpha: 0.55 + (0.45 * t) / k.delay }, MOON, WHITE);
      if (t % 8 === 0) fx(dim, "rpg:light_cut", up(target.location, 2.6), { dir: V_DOWN, size: 1.2, thick: 0.08, life: 0.3, color: MOON, alpha: 0.6 });
    });
  }
  later(k.delay, () => {
    if (!sameLife(player, life)) return; // 그 사이 죽었거나 다른 차원: 옛 대상에게 옮기지 않음
    const tgt = target.isValid && target.dimension.id === player.dimension.id ? target : undefined;
    const c = tgt ? { ...tgt.location } : add(player.location, flat(player.getViewDirection()), 3);
    if (tgt) {
      const dest = behindSpot(dim, tgt, player.location);
      if (dest) {
        try {
          player.teleport(dest, { keepVelocity: false, checkForBlocks: true, facingLocation: tgt.getHeadLocation() });
        } catch {
          // 그 자리에서 벰
        }
      }
    }
    const eye = player.getHeadLocation();
    const at = tgt ? bodyPoint(tgt) : up(c, 1);
    const v = unit(sub(at, eye));
    shadowPuff(dim, player.location, glow);
    // v2.27.0 발동: 자홍 번쩍임 · 하늘의 달이 갈라짐 → 섬광 · 대상 둘레로 퍼지는 고리 (1틱 뒤)
    ultFlashFor(player, dim, at, "moonAssassin");
    skyPlate(player, dim, "crack", moonAt, 5, glow, MOON, { life: 0.6, grow: 0.5, fade: 0.6 });
    later(1, () => {
      skyFlare(dim, moonAt, 3.6, glow, WHITE, { life: 0.5 });
      novaRing(dim, c, 1.2, 3.5, glow, MOON, { life: 0.45 });
    });
    // 달이 부서지며 거대한 X + 달빛 검기 둘
    if (tgt) rfx2(dim, "moon", up(tgt.getHeadLocation(), 2.2), { size: 1.5, life: 0.25, grow: 0.5, fade: 0.8, spin: -20 }, MOON, WHITE);
    fx(dim, "rpg:slash_x", add(at, v, -0.6), { dir: v, size: 2.8, life: 0.4, color: glow, alpha: 1 });
    fx(dim, "rpg:slash_x", add(at, v, -0.7), { dir: v, size: 2.0, life: 0.34, color: MOON, alpha: 1 });
    const wk = nearK(player, at, 4, 0.45);
    for (const sp of [35, -35]) rfx2(dim, "wave", add(at, v, -0.25), { size: 1.2 * wk, life: 0.3, spin: sp, grow: 0.25, fade: 0.5, alpha: 0.75 }, glow, MOON);
    cutMark(dim, add(at, v, -0.55), v, 45, 0.05, glow, WHITE, 1.6);
    cutMark(dim, add(at, v, -0.55), v, -45, 0.05, glow, WHITE, 1.6);
    burst(dim, add(at, v, -0.5), SHADE, 1.8, 0.4);
    ring(dim, add(at, v, -0.7), v, glow, 1.4, 0.4);
    zonePulse(dim, c, glow, k.splash, 0.45);
    sparks(dim, at, core, 12, 7);
    play(dim, "item.trident.thunder", at, 1.6, 0.6);
    play(dim, "game.player.attack.critical", at, 0.8, 1);
    shakeAround(dim, at, 8, 0.2, 0.25);
    if (tgt) assassinHit(player, tgt, P, k.mult);
    for (const e of around(player, c, k.splash)) if (!tgt || e.id !== tgt.id) assassinHit(player, e, P, k.splashMult);
  });
  return true;
}

// ------------------------------------------------------------ 암살 표식 · 이어 쓰기 콤보 (RUNE_COMBO)
/** 암살 표식: "플레이어|몹 id" → {e, until} */
const marks = new Map();
/**
 * 콤보: 플레이어 → {chain: 이은 등급들, last: 마지막으로 누른 틱, end: 마지막 타격 틱 (마무리 기술 포함), finishing: 마무리 기술이 잡힘}.
 * 이어 쓴 룬·마무리 기술은 누를 때의 콤보 객체를 들고 있음 (그 사이 새 콤보가 시작돼도 서로 건드리지 않게)
 * @type {Map<string, {chain: string[], last: number, end: number, finishing?: boolean}>}
 */
const combos = new Map();
/** 그림자 처형: 시작하고 첫 타격까지 틱 */
const FINISH_LEAD = 4;

function markTarget(player, e) {
  marks.set(`${player.id}|${e.id}`, { e, until: system.currentTick + RUNE_COMBO.markTicks });
}

/** 표식이 남은 적 (가까운 순) */
export function markedTargets(player, range) {
  const now = system.currentTick;
  const out = [];
  for (const [key, m] of marks) {
    if (!key.startsWith(`${player.id}|`)) continue;
    if (m.until <= now || !m.e.isValid || !canHit(player, m.e)) {
      marks.delete(key);
      continue;
    }
    if (len(sub(m.e.location, player.location)) <= range) out.push(m.e);
  }
  return nearestTo(out, player.location);
}

/** 콤보가 살아 있는지 (지우지 않고 봄): 3초 안에 눌렀거나 이은 룬·마무리 기술이 아직 나가는 중 */
function comboLive(playerId, now) {
  const st = combos.get(playerId);
  return !!st && (now - st.last <= RUNE_COMBO.window || now <= comboBusyEnd(st) + RUNE_COMBO.gapAfter);
}

/** 표식 표시: 0.5초마다 표식이 남은 몹의 머리 위에 작은 자홍 룬 (몹을 따라다님). 콤보가 이어지는 동안은 표식이 사라지지 않음 (처형 대상) */
function markTick() {
  const now = system.currentTick;
  const [glow] = RUNE.color.assassin;
  for (const [key, m] of marks) {
    if (comboLive(key.slice(0, key.indexOf("|")), now)) m.until = Math.max(m.until, now + 20);
    if (m.until <= now || !m.e.isValid) {
      marks.delete(key);
      continue;
    }
    try {
      floorRune(m.e.dimension, up(m.e.getHeadLocation(), 0.45), { size: 0.38, life: 0.55, color: glow, alpha: 0.95, rate: 260 });
    } catch {
      // 무시
    }
  }
}

/** 가진 암살자 룬 (등급) — 인벤토리 + 왼손 */
export function ownedComboRunes(player) {
  const set = new Set();
  const note = (it) => {
    const r = parseRune(it?.typeId);
    if (r?.family === RUNE_COMBO.family) set.add(r.grade);
  };
  try {
    const inv = player.getComponent("minecraft:inventory")?.container;
    for (let i = 0; inv && i < inv.size; i++) note(inv.getItem(i));
    note(player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Offhand));
  } catch {
    // 무시
  }
  return set;
}

/** 그림자 처형이 시작해서 마지막 타격까지 (대상이 가장 많을 때) */
function finishTicks() {
  const F = RUNE_COMBO.finisher;
  return FINISH_LEAD + (F.maxTargets - 1) * F.gap;
}

/** 이 콤보의 마지막 타격 틱: 다섯 가지를 모두 이었는데 마무리 기술이 아직 안 잡혔으면 (마지막 룬이 대기 중) 그것까지 */
function comboBusyEnd(st) {
  const full = st.chain.length >= GRADE_ORDER.length && !st.finishing;
  return st.end + (full ? RUNE_COMBO.gapAfter + finishTicks() : 0);
}

/** 지금 콤보 (끊겼으면 undefined) */
export function comboOf(player) {
  const st = combos.get(player.id);
  if (!st) return undefined;
  if (system.currentTick - st.last > RUNE_COMBO.window && system.currentTick > comboBusyEnd(st) + RUNE_COMBO.gapAfter) {
    combos.delete(player.id);
    return undefined;
  }
  return st;
}

/**
 * 암살자 룬을 누를 때의 콤보 계획: step (0 = 콤보 없음, 1 = 새로 시작), mult (피해 배율), start (나갈 틱)
 * 이어 쓰기 = 가진 암살자 룬 minOwned 종 이상 · 마지막으로 누른 뒤 window 틱 안 · 이번 연계에서 아직 안 쓴 룬 · 마무리 기술 전
 * 앞 룬이나 마무리 기술이 아직 나가는 중이면 (이어 쓰기가 아니어도) 그 마지막 타격 gapAfter 틱 뒤에 나감: 무적 시간에 피해가 사라지지 않게
 */
function comboPlan(player, r) {
  const C = RUNE_COMBO;
  const now = system.currentTick;
  const owned = ownedComboRunes(player).size;
  const st = combos.get(player.id);
  const start = st ? Math.max(now, comboBusyEnd(st) + C.gapAfter) : now;
  if (owned < C.minOwned) return { step: 0, mult: 1, start, owned };
  const chaining = !!st && !st.finishing && now - st.last <= C.window && !st.chain.includes(r.grade) && st.chain.length < GRADE_ORDER.length;
  const step = chaining ? st.chain.length + 1 : 1;
  return { step, mult: 1 + C.step * (step - 1), start, owned };
}

/** 콤보에 넣음 (누른 순간). 2단계부터 눈앞에 "N 콤보!". 넣은 콤보 객체를 돌려줌 (콤보 없음이면 undefined) */
function comboCommit(player, r, plan) {
  const now = system.currentTick;
  if (plan.step === 0) {
    combos.delete(player.id);
    return undefined;
  }
  const st = plan.step === 1 ? { chain: [], last: now, end: now } : combos.get(player.id);
  if (!st) return undefined;
  st.chain.push(r.grade);
  st.last = now;
  st.end = plan.start + (r.def.span ?? 0);
  combos.set(player.id, st);
  if (plan.step >= 2) {
    // 조준점 오른쪽 조금 아래 (바로 앞 몹의 피해 숫자는 조준점 위에 뜸)
    showFaceText(player, `§d§l${plan.step} 콤보!`, { side: 0.8, up: -0.2 });
    notify(player, `§d${plan.step} 콤보 §7${r.def.name} 피해 §f+${Math.round((plan.mult - 1) * 100)}%${plan.start > now ? " §8(앞 룬 뒤에 나감)" : ""}`, 30);
    try {
      player.playSound("note.pling", { pitch: 0.9 + plan.step * 0.12, volume: 0.7 });
    } catch {
      // 무시
    }
  }
  return st;
}

/** 이어 쓴 룬이 나가지 못함 (대상이 없음 등): 그 콤보에서 빼고 재사용 대기를 돌려줌 */
function comboRollback(player, r, st) {
  if (st) st.chain = st.chain.filter((g) => g !== r.grade);
  cooldowns.delete(`${player.id}|${r.id}`);
}

/** 룬이 실제로 나감: 그 콤보에 다섯 가지를 모두 이었고 이 룬이 마지막이면, 마지막 타격 gapAfter 틱 뒤에 마무리 기술 */
function comboLanded(player, r, st) {
  if (!st || st.chain.length < GRADE_ORDER.length || st.finishing || st.chain[st.chain.length - 1] !== r.grade) return;
  const F = RUNE_COMBO.finisher;
  st.finishing = true;
  const life = lifeToken(player);
  const at = system.currentTick + (r.def.span ?? 0) + RUNE_COMBO.gapAfter;
  // 마무리 기술의 마지막 타격까지 (대상이 가장 많을 때): 그 사이에 누른 암살자 룬은 이 다음에 나감
  st.end = Math.max(st.end, at + finishTicks());
  later(at - system.currentTick, () => {
    const n = sameLife(player, life) ? shadowExecution(player) : 0;
    // 실제 대상 수로 끝나는 틱을 줄이고, 마지막 타격이 끝나면 이 콤보를 지움 (그 사이 새 콤보가 시작됐으면 그대로 둠)
    st.end = Math.min(st.end, system.currentTick + FINISH_LEAD + Math.max(0, n - 1) * F.gap);
    later(Math.max(1, st.end + RUNE_COMBO.gapAfter - system.currentTick), () => {
      if (combos.get(player.id) === st) combos.delete(player.id);
    });
  });
}

/** 마무리 기술 그림자 처형: 표식이 남은 적마다 (없으면 주위 radius 칸) 양옆에서 그림자 분신이 교차하며 벰. 대상 수를 돌려줌 */
export function shadowExecution(player) {
  const F = RUNE_COMBO.finisher;
  const P = runePower(player, RUNE_COMBO.family);
  const dim = player.dimension;
  const [glow, core] = RUNE.color.assassin;
  const c = player.location;
  let list = markedTargets(player, F.range).slice(0, F.maxTargets);
  if (!list.length) list = nearestTo(around(player, c, F.radius), c).slice(0, F.maxTargets);
  const eye = player.getHeadLocation();
  const view = player.getViewDirection();
  // 조준점 아래에서 떠오름 (조준점 위는 맞은 적의 피해 숫자 자리)
  showFaceText(player, `§5§l${F.name}!`, { up: -0.6 });
  fx(dim, "rpg:slash_x", add(eye, view, 2.4), { dir: view, size: 2.2, life: 0.45, color: SHADE, alpha: 0.95 });
  ring(dim, add(eye, view, 2.0), view, glow, 2.4, 0.5);
  floorRune(dim, c, { size: 3.2, life: 1.0, color: glow, alpha: 0.9, rate: 200, grow: 0.4 });
  play(dim, "mob.evocation_illager.cast_spell", c, 0.8, 1);
  play(dim, "item.trident.thunder", c, 1.8, 0.5);
  list.forEach((e, i) => {
    later(FINISH_LEAD + i * F.gap, () => {
      if (!player.isValid || !e.isValid) return;
      const at = bodyPoint(e);
      const toward = flat(sub(at, player.location));
      const side = rightOf(toward);
      for (const s of [-1, 1]) {
        const a = add(up(at, 0.2), side, s * 1.9);
        const b = add(up(at, -0.2), side, -s * 1.9);
        flyStreak(dim, a, b, 38, SHADE, glow, 1.1, 0.55);
      }
      const v = unit(sub(at, player.getHeadLocation()));
      fx(dim, "rpg:slash_x", add(at, v, -0.2), { dir: v, size: 2.1, life: 0.36, color: glow, alpha: 1 });
      fx(dim, "rpg:slash_x", add(at, v, -0.25), { dir: v, size: 1.4, life: 0.3, color: WHITE, alpha: 1 });
      burst(dim, at, SHADE, 1.5, 0.35);
      sparks(dim, at, core, 10, 6);
      fx(dim, "rpg:arrow_shot", up(e.location, 1.6), { dir: V_UP, speed: 0, size: 1.7, thick: 0.45, life: 0.4, color: glow, alpha: 0.85 });
      play(dim, "game.player.attack.critical", at, 0.9 + i * 0.08, 1);
      runeHit(player, e, P, F.mult, "dex");
      marks.delete(`${player.id}|${e.id}`);
    });
  });
  later(6 + list.length * F.gap, () => {
    if (!player.isValid) return;
    play(dim, "firework.large_blast", player.location, 0.9, 1);
    shakeAround(dim, player.location, 12, 0.3, 0.35);
  });
  return list.length;
}

// ============================================================ v1.6.0 검사 (청백: 발도·검기·반격·연속 베기)
/**
 * 발도 일섬: 칼을 뽑으며 앞을 한 번에 벰 — v2.25.0 칼을 뽑는 번쩍임 (rpg:rfx_prop 반짝임) + 앞으로 퍼지는 가로 초승달 검기 (rpg:rfx_wave),
 * 칼을 거두면 (6틱 뒤) 베인 적에게 늦게 베인 자국이 한 번 더 그어짐
 */
function quickDraw(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE.color.blade;
  rfx(dim, "prop", add(up(head, -0.55), rightOf(f), 0.45), { size: 0.35, life: 0.18, cell: RFX.glint[0], spin: 20, color: WHITE, grow: 0.8, fade: 0.6 }); // 칼집에서 뽑는 빛
  fx(dim, "rpg:light_cut", up(add(head, f, 2.0), -0.25), { dir: rightOf(f), size: 2.8, thick: 0.2, life: 0.32, delay: 0.04, color: core, alpha: 1 });
  arc(dim, up(add(head, f, 1.4), -0.3), f, { shape: "arc", flip: false, size: 2.7, life: 0.24, speed: 3, spin: -6, color: glow, aura: core });
  rfx2(dim, "wave", up(add(head, f, 2.3), -0.35), { dir: f, speed: 6, size: 1.5, life: 0.22, grow: 0.35, fade: 0.6, spin: 4, alpha: 0.9 }, glow, WHITE);
  play(dim, "random.anvil_land", head, 2.0, 0.35);
  play(dim, "item.spear.attack_miss", head, 1.3, 0.9);
  const cut = [];
  for (const e of coneTargets(player, k.range, k.cone, 10)) {
    if (runeHit(player, e, P, k.mult, r.stat) > 0) {
      const at = bodyPoint(e);
      const v = unit(sub(at, head));
      cutMark(dim, at, v, -8, 0.1, glow, core, 1.0);
      if (cut.length < 5) sparks(dim, at, core, 4, 4); // 불꽃은 5명까지 (파티클 예산)
      cut.push(e);
    }
  }
  // 칼을 거둠: 늦게 그어지는 자국 (6명까지)
  later(6, () => {
    if (!player.isValid) return;
    play(dim, "random.click", player.location, 1.8, 0.6);
    const eye = player.getHeadLocation();
    for (const e of cut.slice(0, 6)) {
      if (!e.isValid) continue;
      const at = bodyPoint(e);
      cutMark(dim, at, unit(sub(at, eye)), 172, 0.06, WHITE, core, 1.2);
    }
  });
  return true;
}

/** 초승달 검기 한 장 (빛 + 심지): 카메라를 보는 참격이라 멀어질수록 작아 보임 */
function crescent(dim, at, dir, size, glow, core, spin = -90, life = 0.16) {
  arc(dim, at, dir, { shape: "heavy", flip: false, size, life, speed: 0, spin, color: glow, aura: core });
}

/**
 * 검기 날리기: 초승달 검기가 speed 칸/초로 range 칸(벽 앞까지) 날아가며 선 가까이 width 칸의 적 (적마다 한 번) —
 * v2.25.0 날아가는 초승달 검기 그림 (rpg:rfx_wave, 손 앞 1.6칸부터) + 지나간 자리에 남는 빛줄기
 */
function swordWave(player, r, P, k) {
  const dim = player.dimension;
  const view = player.getViewDirection();
  const f = flat(view);
  const dir = unit({ x: f.x, y: view.y * 0.3, z: f.z });
  const from = up(add(player.location, f, 0.6), 1.1);
  const L = Math.max(1, rayLength(dim, from, dir, k.range));
  const [glow, core] = RUNE.color.blade;
  const s0 = Math.min(1.6, L);
  const life = Math.max(0.05, (L - s0) / k.speed);
  rfx2(dim, "wave", add(from, dir, s0), { dir, speed: k.speed, size: k.width * 0.8, life, fade: 0.15, spin: -8 }, glow, WHITE);
  crescent(dim, add(from, dir, 1.0), dir, 1.7, glow, core); // 휘두르는 순간
  for (let s = 2; s < L; s += 2) {
    const delay = (s / L) * (L / k.speed);
    fx(dim, "rpg:light_cut", add(from, dir, s), { dir, size: 1.1, thick: 0.16, life: delay + 0.3, delay, color: s % 4 ? glow : core, alpha: 0.85 });
  }
  play(dim, "item.trident.throw", from, 1.3, 0.9);
  play(dim, "item.spear.attack_miss", from, 1.6, 0.7);
  const perTick = new Map(); // 틱 → 그 틱에 벤 적 수 (파티클 예산: 자국 6명 · 불꽃 3명까지)
  for (const { e, t } of lineTargets(player, from, dir, L, k.width)) {
    later(Math.max(0, Math.round((t / k.speed) * 20)), () => {
      if (!player.isValid || !e.isValid) return;
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        const at = bodyPoint(e);
        const n = perTick.get(system.currentTick) ?? 0;
        perTick.set(system.currentTick, n + 1);
        if (n < 6) cutMark(dim, at, dir, 70, 0.08, glow, core, 1.1);
        if (n < 3) sparks(dim, at, core, 5, 4);
        knock(e, dir, 0.4, 0.15);
      }
    });
  }
  return true;
}

/**
 * 반격 자세: ticks 동안 받는 피해 -reduce, 때린 적(멀리서 쏜 적 포함)을 바로 반격 (적마다 한 번). 끝나면 반격하지 않은 앞쪽 적을 벰 —
 * v2.25.0 오른손 앞에 세운 빛의 칼 (rpg:rfx_sword 작게) · 발밑 검진, 반격은 그림자 분신 (rpg:rfx_shade 청백) 이 때린 적의 옆으로 날아가 벰
 * (한 틱에 여럿이 때리면 분신 · 참격은 둘까지)
 */
function counterStance(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.blade;
  const shade = [0.16, 0.42, 0.5]; // 분신 몸 (청백 계열 — 풀밭 위에서도 보이게)
  const countered = new Set();
  const until = system.currentTick + k.ticks;
  const life = lifeToken(player);
  const perTick = new Map(); // 틱 → 그 틱의 반격 수 (여럿이 한꺼번에 때리면 분신 · 참격은 둘까지 — 파티클 예산)
  setGuard(player, k.ticks, k.reduce, {
    fn: (pl, attacker) => {
      if (!sameLife(pl, life) || !attacker?.isValid || countered.has(attacker.id) || system.currentTick > until) return;
      if (!canHit(pl, attacker)) return;
      countered.add(attacker.id);
      const d = pl.dimension;
      const eye = pl.getHeadLocation();
      const at = bodyPoint(attacker);
      const v = unit(sub(at, eye));
      const now = system.currentTick;
      const nth = perTick.get(now) ?? 0;
      perTick.set(now, nth + 1);
      const xs = nearK(pl, at, 3, 0.4); // 바로 앞의 적이면 X 를 작게 (1인칭 화면을 덮지 않게)
      if (nth < 2) {
        // 분신: 내 옆 1칸 앞에서 때린 적의 옆 (내 쪽 선에서 비켜 — 가까운 적이어도 눈앞을 덮지 않게) 으로 미끄러져 가서 벰
        const fv = flat(v);
        const sd = countered.size % 2 ? 1 : -1;
        const fromFeet = add(add(pl.location, fv, 1.0), rightOf(fv), sd * 0.7);
        const toFeet = add(attacker.location, rightOf(fv), sd * (0.9 + bodyPad(attacker)));
        const D = len(sub(toFeet, fromFeet));
        let wait = 0;
        if (D > 1.2) {
          const t = Math.max(0.08, D / 30);
          wait = Math.max(0, Math.round(t * 20) - 1); // 멀리서 쏜 적: 분신이 닿을 때 벰 (1틱 겹침)
          shadeClone(pl, d, fromFeet, fv, 1, shade, glow, { life: t, fade: 0.2, alpha: 0.8, size: 0.95 * nearK(pl, up(fromFeet, 1), 3.5, 0.55), vel: scale(sub(toFeet, fromFeet), 1 / t) });
        }
        later(wait, () => {
          if (pl.isValid) shadeClone(pl, d, toFeet, scale(rightOf(fv), -sd), 2, shade, glow, { life: 0.35, fade: 0.5, alpha: 0.9, size: 0.95 * nearK(pl, up(toFeet, 1), 3.5, 0.55) });
        });
        arc(d, add(eye, v, 1.3), v, { shape: "thin", flip: countered.size % 2 === 0, size: 2.0, life: 0.2, speed: 3, spin: 35, color: glow, aura: core });
        sparks(d, at, core, 8, 5);
      }
      fx(d, "rpg:slash_x", at, { dir: v, size: 1.4 * xs, life: 0.3, color: glow, alpha: 1 });
      showFaceText(pl, "§b§l반격!", { side: -0.8, up: -0.2 });
      play(d, "item.shield.block", at, 1.4, 0.8);
      play(d, "game.player.attack.strong", at, 1.2, 1);
      runeHit(pl, attacker, P, k.mult, r.stat);
    },
  });
  selfCue(player, glow, core);
  play(dim, "random.anvil_land", player.location, 1.8, 0.4);
  play(dim, "armor.equip_iron", player.location, 1.2, 0.8);
  // 자세: 오른손 앞에 세운 빛의 칼 (칼끝 위 — 1인칭 조준점을 가리지 않게) + 10틱마다 발밑 검진
  for (let t = 0; t < k.ticks; t += 5) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const eye = player.getHeadLocation();
      const f = flat(player.getViewDirection());
      rfx2(player.dimension, "sword", up(add(add(eye, f, 1.5), rightOf(f), 0.5), -0.45), { dir: V_UP, size: 0.42, life: 0.3, alpha: 0.6, vel: bodyVel(player) }, glow, WHITE, RFX.sword, 0.75);
      if (t % 10 === 0) floorRune(player.dimension, player.location, { size: 1.3, life: 0.55, color: glow, alpha: 0.7, rate: 0, spin: t * 3 });
    });
  }
  // 끝: 모은 기운으로 앞을 벰 (반격한 적은 제외 → 적마다 한 번)
  later(k.ticks, () => {
    if (!sameLife(player, life)) return;
    const d = player.dimension;
    const eye = player.getHeadLocation();
    const f = flat(player.getViewDirection());
    arc(d, up(add(eye, f, 1.5), -0.3), f, { shape: "arc", flip: true, size: 2.8, life: 0.26, speed: 3, spin: 8, color: glow, aura: core });
    rfx2(d, "wave", up(add(eye, f, 2.4), -0.4), { dir: f, speed: 5, size: 1.6, life: 0.24, grow: 0.3, fade: 0.6, spin: -4 }, glow, WHITE);
    play(d, "item.spear.attack_miss", eye, 1.1, 0.9);
    let n = 0;
    for (const e of coneTargets(player, k.range, k.cone, 10)) {
      if (countered.has(e.id)) continue;
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        const at = bodyPoint(e);
        cutMark(d, at, unit(sub(at, eye)), 10, 0.1, glow, core, 1.0);
        if (n++ < 4) sparks(d, at, core, 4, 4); // 불꽃은 4명까지 (파티클 예산)
      }
    }
  });
  return true;
}

/**
 * 삼단베기: gap 틱마다 가로 베기 → 세로 베기 (앞 cone 도·range 칸) → 찌르기 (앞 thrust 칸 직선) — v2.25.0 베기마다 가로 · 세로 초승달 검기 (rpg:rfx_wave),
 * 찌르기는 빛의 칼 (rpg:rfx_sword) 이 앞으로 뻗어 나감
 */
function tripleCut(player, r, P, k) {
  const [glow, core] = RUNE.color.blade;
  const life = lifeToken(player);
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const eye = player.getHeadLocation();
    const f = flat(player.getViewDirection());
    let list;
    if (i < k.hits - 1) {
      const vertical = i % 2 === 1;
      arc(dim, up(add(eye, f, 1.5), -0.3), f, { shape: vertical ? "thin" : "arc", flip: vertical, size: vertical ? 2.4 : 2.6, life: 0.24, speed: 3, spin: vertical ? 84 : -6, color: glow, aura: core });
      rfx2(dim, "wave", up(add(eye, f, 2.4), vertical ? -0.1 : -0.4), { dir: f, speed: 5, size: vertical ? 1.3 : 1.5, life: 0.22, grow: 0.3, fade: 0.6, spin: vertical ? 90 : -4 }, glow, WHITE);
      play(dim, "item.spear.attack_miss", eye, 1.2 + i * 0.15, 0.9);
      list = coneTargets(player, k.range, k.cone, 10);
    } else {
      const from = up(add(player.location, f, 0.5), 1.1);
      const L = Math.max(1, rayLength(dim, from, f, k.thrust));
      // 빛의 칼: 오른손에서 칼끝이 L 까지 뻗음 (3틱 — 1인칭에서 손에서 비스듬히 뻗어 보임)
      elementThrust(dim, from, f, L, glow, WHITE);
      fx(dim, "rpg:light_cut", add(from, f, L / 2), { dir: f, size: L / 2, thick: 0.18, life: 0.3, color: core, alpha: 1 });
      ring(dim, add(from, f, L), f, glow, 0.9, 0.3);
      play(dim, "item.trident.throw", eye, 1.1, 1);
      list = lineTargets(player, from, f, L, 1.2).map((o) => o.e);
    }
    let n = 0;
    for (const e of list) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        const at = bodyPoint(e);
        // 맞은 자리 (파티클 예산): 자국 10명 · 불꽃 4명까지
        if (n < 10) cutMark(dim, at, unit(sub(at, eye)), [0, 90, 20][i % 3], 0.08, glow, core, 1.0);
        if (n < 4) sparks(dim, at, core, 4, 4);
        n++;
      }
    }
  });
  return true;
}

/**
 * 천지개벽: charge 틱 동안 눈앞에 초승달 검기 (rpg:rfx_wave) 가 모여 커진 뒤, 거대한 검기가 speed 칸/초로 range 칸(벽 앞까지) — 폭 width 칸의 적 (적마다 한 번), 밀쳐냄
 */
function heavenSplit(player, r, P, k) {
  const [glow, core] = RUNE.color.blade;
  const dim0 = player.dimension;
  play(dim0, "mob.evocation_illager.prepare_attack", player.location, 1.2, 0.9);
  play(dim0, "beacon.power", player.location, 1.4, 0.7);
  // v2.27.0 2차 강화 — 예고: 청백 하늘 · 발밑의 칼날 문양 (빠르게 돎) · 앞쪽 하늘의 칼날 문양
  const c0 = { ...player.location };
  ultSkyFor(dim0, c0, "heavenSplit", k.charge + 34);
  domainSigil(dim0, c0, 4.6, "blade", glow, WHITE, { life: (k.charge + 2) / 20, spinRate: 140, grow: 0.3, alpha: 0.8, fade: 0.15 });
  const skyAt = skyPoint(player, c0, 12, 21);
  skyPlate(player, dim0, "sigil", skyAt, 5.4, glow, WHITE, { cell: RFX.sigil.blade, life: (k.charge + 6) / 20, spinRate: 60, grow: 0.2, alpha: 0.75, fade: 0.3 });
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!player.isValid) return;
      const dim = player.dimension;
      const eye = player.getHeadLocation();
      const f = flat(player.getViewDirection());
      const q = t / k.charge;
      rfx2(dim, "wave", up(add(eye, f, 2.5), -0.2), { size: 0.7 + q * 1.7, life: 0.13, alpha: 0.6 + 0.4 * q }, glow, WHITE);
      const side = rightOf(f);
      for (const s of [-1, 1]) {
        const a = add(up(add(eye, f, 2.4), -0.15), side, s * (2.4 - q * 1.6));
        fx(dim, "rpg:arrow_spark", a, { dir: scale(side, -s), speed: 3, size: 0.14, grow: 0.5, life: 0.25, color: core, alpha: 1 });
      }
      if (t % 6 === 0) play(dim, "block.bell.hit", eye, 1.2 + q, 0.3);
    });
  }
  floorRune(dim0, player.location, { size: 1.8, life: k.charge / 20 + 0.2, color: glow, alpha: 0.85, rate: 160, progress: 1, grow: 0.2 });
  const life = lifeToken(player);
  later(k.charge, () => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const f = flat(player.getViewDirection());
    const from = up(add(player.location, f, 0.8), 1.2);
    const L = Math.max(1, rayLength(dim, from, f, k.range));
    const ticks = Math.max(1, Math.round((L / k.speed) * 20));
    // 거대한 검기: 눈앞 1.6칸에서 나타나 날아감 (한 장이 날아감 — 2틱마다 다시 그리지 않음)
    const s0 = Math.min(1.6, L * 0.5);
    rfx2(dim, "wave", add(from, f, s0), { dir: f, speed: k.speed, size: k.width * 1.15, life: Math.max(0.05, (L - s0) / k.speed), fade: 0.15 }, glow, WHITE);
    for (let t = 0; t <= ticks; t += 2) {
      const s = Math.min(L, (t / 20) * k.speed);
      later(t, () => fx(dim, "rpg:light_cut", up(add(from, f, s), -1.05), { dir: f, size: 1.2, thick: 0.16, life: 0.9, delay: 0.3, color: glow, alpha: 0.9 }));
    }
    flyStreak(dim, from, add(from, f, L), k.speed, glow, core, 1.4, 0.5);
    play(dim, "item.trident.thunder", from, 1.5, 0.7);
    play(dim, "firework.large_blast", from, 1.2, 0.8);
    shakeAround(dim, from, 10, 0.25, 0.3);
    // v2.27.0 발동: 청백 번쩍임 · 하늘이 둘로 갈라짐 (검기 방향으로 하늘을 가로지르는 빛) · 하늘 문양의 섬광 · 검기가 지나간 땅이 갈라져 빛남
    ultFlashFor(player, dim, from, "heavenSplit");
    const sky0 = up(add(player.getHeadLocation(), f, 15), 7.5);
    fx(dim, "rpg:light_cut", sky0, { dir: f, size: 13, thick: 0.9, life: 1.1, delay: 0.6, color: glow, alpha: 0.95 });
    fx(dim, "rpg:light_cut", sky0, { dir: f, size: 12.5, thick: 0.3, life: 1.0, delay: 0.55, color: WHITE, alpha: 1 });
    skyFlare(dim, skyAt, 3.8, glow, WHITE, { life: 0.5 });
    for (let s = 4; s < L; s += 5) {
      const p = add(from, f, s);
      later(Math.round((s / k.speed) * 20) + 1, () => glowCrack(dim, { x: p.x, y: groundAt(dim, p, from.y - 1.2), z: p.z }, 2.4, glow, WHITE, { life: 1.6, v: (s / 5) % 2 > 0.5 ? 1 : 0 }));
    }
    for (const { e, t } of lineTargets(player, from, f, L, k.width)) {
      later(Math.max(0, Math.round((t / k.speed) * 20)), () => {
        if (!player.isValid || !e.isValid) return;
        if (runeHit(player, e, P, k.mult, r.stat) > 0) {
          if (fxQuota("splitHit", 4)) {
            // v2.27.0 한 틱에 4명까지 그림 (줄지어 선 적 — 파티클 예산, 피해 · 밀침은 모두)
            const at = bodyPoint(e);
            const v = unit(sub(at, player.getHeadLocation()));
            fx(dim, "rpg:slash_x", add(at, v, -0.2), { dir: v, size: 2.0, life: 0.34, color: glow, alpha: 1 });
            burst(dim, at, core, 1.2, 0.3);
            sparks(dim, at, core, 8, 6);
          }
          knock(e, f, k.knock, 0.3);
        }
      });
    }
  });
  return true;
}

// ============================================================ v2.13.0 마검사 (남보라 마력 + 청록 칼날: 검과 마력을 섞어 씀, 마검 부여)
/** 검: 공격 피해 (노란 숫자) */
function swordHit(player, e, P, mult) {
  return runeHit(player, e, P, mult, "str", EntityDamageCause.entityAttack, "rune");
}
/** 마력: 마법 피해 (방어구 무시, 보라 숫자) */
function spellHit(player, e, P, mult) {
  return runeHit(player, e, P, mult, "int", EntityDamageCause.magic, "spell");
}

/**
 * 마력 베기: 앞을 베고 (검) gap 틱 뒤 벤 자리에 마력 폭발 (마력) — v2.25.0 앞으로 퍼지는 초승달 검기 (rpg:rfx_wave),
 * 터질 때 마력의 별빛 문장 (rpg:rfx_sun 작게) 이 번쩍 (6명까지)
 */
function arcaneCut(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE.color.spellblade;
  arc(dim, up(add(head, f, 1.4), -0.3), f, { shape: "arc", flip: false, size: 2.7, life: 0.24, speed: 3, spin: -6, color: core, aura: glow });
  rfx2(dim, "wave", up(add(head, f, 2.3), -0.35), { dir: f, speed: 6, size: 1.45, life: 0.22, grow: 0.35, fade: 0.6, spin: 4, alpha: 0.9 }, glow, core);
  fx(dim, "rpg:light_cut", up(add(head, f, 2.0), -0.25), { dir: rightOf(f), size: 2.6, thick: 0.16, life: 0.3, delay: 0.04, color: glow, alpha: 1 });
  play(dim, "item.spear.attack_miss", head, 1.2, 0.9);
  play(dim, "mob.evocation_illager.cast_spell", head, 1.7, 0.4);
  const life = lifeToken(player);
  const hit = [];
  for (const e of coneTargets(player, k.range, k.cone, 10)) {
    if (swordHit(player, e, P, k.mult) <= 0) continue;
    hit.push(e);
    const at = bodyPoint(e);
    cutMark(dim, at, unit(sub(at, head)), -8, 0.1, core, WHITE, 1.0);
    // 벤 자리에 마력 문양 (gap 틱 동안 그려지다 터짐)
    fx(dim, "rpg:magic_circle", at, { size: 0.42, life: k.gap / 20, spin: 0, progress: 1, color: glow, alpha: 0.9 });
  }
  later(k.gap, () => {
    if (!sameLife(player, life)) return;
    let n = 0;
    for (const e of hit) {
      if (!e.isValid) continue;
      const at = bodyPoint(e);
      if (spellHit(player, e, P, k.mult) <= 0) continue;
      // 터지는 자리 (파티클 예산): 별빛 문장 6명 · 번쩍임 10명 · 불꽃 4명까지
      if (n < 10) {
        burst(dim, at, glow, 1.1, 0.3);
        fx(dim, "rpg:magic_circle_flash", at, { size: 0.55, life: 0.25, spin: 0, progress: 1, color: glow, alpha: 1 });
      }
      if (n < 6) rfx2(dim, "sun", at, { size: 0.55, life: 0.26, grow: 0.7, fade: 0.6, spin: Math.random() * 360 }, glow, core);
      if (n < 4) sparks(dim, at, core, 4, 5);
      n++;
    }
    if (hit.length) play(dim, "chime.amethyst_block", player.location, 1.3, 1);
  });
  return true;
}

/**
 * v2.13.1 마나 블라스터 (마검사 희귀 — v2.13.0 환영검 대신, 사용자 요청 "마력 쪽 지속 딜링기"): (hits − 1) × gap 틱 동안 조준점을 따라가는 마력 광선.
 * 광선 = 눈에서 보는 방향으로 range 칸 (벽·땅 앞까지) — 그림은 오른손 앞에서 그 끝까지 (1인칭에서 손에서 조준점으로 뻗음).
 * 누른 순간부터 gap 틱마다 광선 폭 width 칸 안의 적 모두에게 위력 × mult (마력, 마법 피해, 파란 숫자). 대상이 없어도 나감 (취소 없음).
 * 죽음 · 차원 이동 · 탈것 부르기면 멈춤 (삶 표시). v2.25.0 광선 끝에 마력 구슬 (rpg:rfx_plasma — 남보라 빛 · 청록 심지)
 */
function manaBlaster(player, r, P, k) {
  const [glow, core] = RUNE.color.spellblade;
  const life = lifeToken(player);
  const T = (k.hits - 1) * k.gap; // 마지막 타격 틱
  const dim0 = player.dimension;
  play(dim0, "beacon.activate", player.location, 1.6, 0.9);
  play(dim0, "mob.evocation_illager.prepare_attack", player.location, 1.4, 0.6);
  /** 지금의 광선: 피해는 눈 → 보는 방향 (조준점과 같은 줄), 그림은 오른손 앞 → 그 끝 */
  const beamNow = () => {
    const eye = player.getHeadLocation();
    const dir = player.getViewDirection();
    const L = Math.max(0.5, rayLength(player.dimension, eye, dir, k.range));
    const f = flat(dir);
    return { eye, dir, L, end: add(eye, dir, L), hand: up(add(add(eye, f, 0.9), rightOf(f), 0.35), -0.32) };
  };
  // 광선 (2틱마다 다시 그림 — 굵은 남보라 빛 + 청록 심지, 피해가 들어가는 순간은 더 굵게, 끝나는 4틱은 가늘어짐)
  //   빛줄기 조각 (rpg:arrow_trail) 은 양 끝 15% 가 흐려지므로 두 조각을 겹쳐 잇고 끝을 0.8칸 더 늘림 (조준점·맞은 적에서 흐려지지 않게)
  // · 손 앞 작게 도는 마법진 · 끝에서 튀는 빛과 마력 구슬 · 광선을 타고 나가는 고리
  for (let t = 0; t <= T + 4; t += 2) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const dim = player.dimension;
      const b = beamNow();
      const seg = sub(b.end, b.hand);
      const sl = len(seg);
      if (sl < 0.3) return;
      const u = scale(seg, 1 / sl);
      const pulse = t % k.gap === 0 && t <= T;
      const thick = (pulse ? 0.42 : 0.3) * (t > T ? 0.5 : 1);
      const total = sl + 0.8;
      // 손 쪽 조각은 가늘게 · 먼 조각은 굵게 (1인칭 원근: 손 앞이 화면을 덮지 않고 먼 곳도 보이게)
      for (const [s0, s1, w] of [
        [0, total / 2 + 0.6, 0.75],
        [total / 2 - 0.6, total, 1.25],
      ]) {
        const mid = add(b.hand, u, (s0 + s1) / 2);
        fx(dim, "rpg:arrow_trail", mid, { dir: u, speed: 0, size: (s1 - s0) / 2, thick: thick * w, life: 0.15, delay: 0, color: glow, alpha: 0.85 });
        fx(dim, "rpg:arrow_trail", mid, { dir: u, speed: 0, size: (s1 - s0) / 2, thick: thick * w * 0.36, life: 0.15, delay: 0, color: core, alpha: 1 });
      }
      if (t % 4 === 0 && t <= T) {
        fx(dim, "rpg:magic_circle", b.hand, { size: 0.16, life: 0.22, spin: t * 12, progress: 1, color: glow, alpha: 0.9 });
        plasmaOrb(dim, b.end, (pulse ? 0.55 : 0.42) * nearK(player, b.end, 3, 0.2), glow, { life: 0.24, fade: 0.2 }); // 광선 끝 (맞는 자리) 의 마력 구슬 — 가까운 벽을 겨누면 작게
        sparks(dim, b.end, core, 3, 4);
      }
      if (t % 6 === 0 && t <= T && sl > 1.5) ring(dim, add(b.hand, u, 1.0), u, glow, 0.28, Math.min(0.6, (sl - 1) / 24), 24);
    });
  }
  // 피해 (누른 순간 · gap 틱마다): 그때 광선 안의 적 모두 (적마다 한 번)
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const b = beamNow();
    let n = 0;
    for (const { e } of lineTargets(player, b.eye, b.dir, b.L, k.width)) {
      if (spellHit(player, e, P, k.mult) <= 0) continue;
      const at = bodyPoint(e);
      if (n < 10) burst(dim, at, glow, 0.7, 0.2); // 번쩍임 10명 · 불꽃 4명까지 (파티클 예산)
      if (n < 4) sparks(dim, at, core, 4, 5);
      n++;
    }
    burst(dim, b.end, glow, 0.6, 0.2);
    fx(dim, "rpg:magic_circle_flash", b.end, { size: 0.5, life: 0.2, spin: i * 40, progress: 1, color: glow, alpha: 1 }); // 맞는 자리 번쩍임
    play(dim, "mob.evocation_illager.cast_spell", b.hand, 1.9, 0.35);
    if (n) play(dim, "item.trident.hit", b.end, 1.7, 0.4);
    if (i === k.hits - 1) later(4, () => play(dim, "beacon.deactivate", player.location, 1.5, 0.7));
  });
  return true;
}

/**
 * 차원 베기: 앞으로 dist 칸 순간이동하며 (벽 앞까지, 한 칸 턱은 넘음) 지나간 길 폭 width 칸을 벰 (검, 공격 피해)
 * → 남보라로 갈라진 자리가 delay 틱 뒤 터짐 (마력, 마법 피해: 그때 갈라진 자리 폭 width + 0.4 칸 안의 적, 조금 띄움).
 * 벤 적이 있으면 도착해서 갈라진 자리 쪽으로 돌아섬 (1인칭에서 폭발과 피해 숫자가 보이게), 없으면 (이동으로 씀) 앞을 본 채로.
 * v2.25.0 허공이 찢어진 틈 그림 (rpg:rfx_rift — 안은 어두운 공허) 이 길을 따라 열려 있다가 터질 때 크게 벌어지며 사라짐
 */
function riftSlash(player, r, P, k) {
  const dim = player.dimension;
  const start = { ...player.location };
  const f = flat(player.getViewDirection());
  const dist = dashDistance(player, f, k.dist, [1.1, 1.8]);
  const end = add(start, f, dist);
  end.y = groundAt(dim, end, start.y);
  const [glow, core] = RUNE.color.spellblade;
  const L = Math.max(0.5, dist + 0.5);
  const from = up(start, 1.0);
  const path = lineTargets(player, from, f, L, k.width);
  arc(dim, up(add(start, f, 1.2), 1.1), f, { shape: "thin", flip: false, size: 1.8, life: 0.24, speed: 5, spin: -15, color: core, aura: glow });
  burst(dim, from, glow, 1.1, 0.25);
  sparks(dim, from, core, 6, 5);
  play(dim, "mob.endermen.portal", start, 1.3, 0.8);
  play(dim, "item.trident.riptide_1", start, 1.5, 0.7);
  const turn = dist > 0.2 && path.length > 0;
  if (dist > 0.2) moveTo(player, end, turn ? up(add(start, f, L * 0.45), 1.0) : undefined);
  const g = turn ? scale(f, -1) : f; // 도착해서 보는 쪽
  arc(dim, up(add(end, g, 0.8), 1.1), g, { shape: "thin", flip: true, size: 2.0, life: 0.26, speed: 3, spin: 20, color: core, aura: glow });
  ring(dim, add(up(end, 1.5), g, 0.9), g, glow, 1.0, 0.3, 2.5);
  let cutN = 0;
  for (const { e } of path) {
    if (swordHit(player, e, P, k.mult) <= 0) continue;
    const at = bodyPoint(e);
    if (cutN < 10) fx(dim, "rpg:slash_x", at, { dir: f, size: 1.0 * nearK(player, at, 3, 0.4), life: 0.25, color: core, alpha: 1 }); // X 10명 · 불꽃 3명까지 (파티클 예산), 가까우면 작게
    if (cutN++ < 3) sparks(dim, at, core, 4, 4);
  }
  // 갈라진 자리: 바닥의 남보라 금 + 허공의 틈 (delay 동안 열려 있음 — 처음 0.1초에 벌어짐)
  for (let s = 0.4; s <= L; s += 0.9) fx(dim, "rpg:light_cut", up(add(start, f, s), 0.1), { dir: f, size: 0.55, thick: 0.22, life: k.delay / 20 + 0.1, color: glow, alpha: 0.95 });
  // 허공의 틈: 2.2칸 남짓한 조각들이 길을 따라 이어짐 (조각마다 조금씩 비뚤게) — 도착한 내 몸 1.8칸 안의 조각은 빼서 1인칭 화면 아래를 덮지 않게
  const segs = [];
  const nSeg = Math.max(1, Math.round(L / 2.2));
  for (let i = 0; i < nSeg; i++) {
    const s0 = (L * (i + 0.5)) / nSeg;
    const at = up(add(add(start, f, s0), rightOf(f), (i % 2 ? 1 : -1) * 0.12), 1.0 + (i % 3) * 0.08);
    if (Math.hypot(at.x - end.x, at.z - end.z) < 1.8) continue;
    segs.push({ at, dir: unit(add(f, rightOf(f), (i % 2 ? 1 : -1) * 0.1)), size: L / nSeg / 2 + 0.45 }); // 이웃 조각과 겹쳐 한 줄로 이어 보이게 (turn 은 이 함수에서 돌아섰는지를 뜻함)
  }
  for (const g2 of segs) rfx2(dim, "rift", g2.at, { dir: g2.dir, size: g2.size, life: k.delay / 20 + 0.05, grow: 0.15, alpha: 0.95 }, glow, core);
  const life = lifeToken(player);
  later(k.delay, () => {
    if (!sameLife(player, life)) return;
    for (const g2 of segs) rfx2(dim, "rift", g2.at, { dir: g2.dir, size: g2.size * 1.1, life: 0.3, grow: 0.6, fade: 0.8, alpha: 1 }, WHITE, core); // 크게 벌어지며 터짐
    // 터지는 빛 · 충격 고리: 내 몸 2.5칸 안은 빼고 (돌아서서 본 1인칭 화면이 보랏빛으로 덮이지 않게)
    const pc = player.location;
    const far = (q) => Math.hypot(q.x - pc.x, q.z - pc.z) >= 2.5;
    for (let s = 0.6; s <= L; s += 1.2) if (far(add(start, f, s))) burst(dim, up(add(start, f, s), 0.7), glow, 1.2, 0.3);
    for (let s = 0; s <= L; s += 2.4) if (far(add(start, f, s))) shock(dim, add(start, f, s), glow, 0.9, 0.35);
    if (far(add(start, f, L / 2))) sparks(dim, up(add(start, f, L / 2), 0.8), core, 10, 6);
    play(dim, "random.explode", up(add(start, f, L / 2), 0.5), 1.6, 0.5);
    play(dim, "chime.amethyst_block", start, 0.8, 1);
    for (const { e } of lineTargets(player, from, f, L, k.width + 0.4)) {
      if (spellHit(player, e, P, k.mult) > 0) knock(e, f, 0.1, 0.35);
    }
  });
  return true;
}

/**
 * 마검 결계: hits × gap 틱 동안 마력 검 swords 자루가 몸 둘레 orbit 칸을 돌고 (나를 따라옴),
 * gap 틱마다 주위 radius 칸의 적을 벰 — 짝수 번째 검 (공격 피해) · 홀수 번째 마력 (마법 피해).
 * v2.25.0 도는 검 = 빛의 검 그림 (rpg:rfx_sword — 남보라 빛 · 청록 심지, 칼날이 도는 쪽으로 누움). 1인칭 화면 앞을 지날 때는 흐리게
 */
function orbitBlades(player, r, P, k) {
  const [glow, core] = RUNE.color.spellblade;
  const life = lifeToken(player);
  const T = k.hits * k.gap;
  selfCue(player, glow, core);
  play(player.dimension, "mob.evocation_illager.prepare_attack", player.location, 1.3, 0.8);
  play(player.dimension, "beacon.activate", player.location, 1.6, 0.6);
  // 도는 검 (3틱마다 다시 그림, 한 틱에 0.16 라디안 — 약 2초에 한 바퀴)
  for (let t = 0; t < T; t += 3) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const dim = player.dimension;
      const c = up(player.location, 1.05);
      const fc = facingOf(player);
      const vel = bodyVel(player);
      for (let i = 0; i < k.swords; i++) {
        const a = t * 0.16 + (i / k.swords) * Math.PI * 2;
        const rd = { x: Math.cos(a), y: 0, z: Math.sin(a) };
        const at = add(c, rd, k.orbit);
        const tan = { x: -Math.sin(a), y: 0, z: Math.cos(a) };
        const ahead = dot(rd, fc) > 0.45;
        rfx2(dim, "sword", at, { dir: tan, size: 0.72, life: 0.17, alpha: ahead ? 0.45 : 0.95, vel }, glow, core);
      }
    });
  }
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const c = player.location;
    const eye = player.getHeadLocation();
    const magic = i % 2 === 1;
    zonePulse(dim, c, magic ? glow : core, k.radius, 0.35);
    let n = 0;
    for (const e of around(player, c, k.radius, 3)) {
      const d = magic ? spellHit(player, e, P, k.mult) : swordHit(player, e, P, k.mult);
      if (d <= 0) continue;
      const at = bodyPoint(e);
      // 맞은 자리 그림 (파티클 예산): 마력 = 6명까지 번개 구슬 + 번쩍임, 12명까지 번쩍임 · 검 = 10명까지 베인 자국
      if (magic) {
        if (n < 12) burst(dim, at, glow, 0.8, 0.25);
        if (n < 6) plasmaOrb(dim, at, 0.45, glow, { life: 0.22 });
      } else if (n < 10) cutMark(dim, at, unit(sub(at, eye)), ((i * 47) % 180) - 90, 0.08, core, WHITE, 0.9);
      n++;
    }
    play(dim, magic ? "chime.amethyst_block" : "item.spear.attack_miss", c, magic ? 1.5 : 1.2 + (i % 3) * 0.1, 0.8);
  });
  return true;
}

/**
 * 마검 해방: charge 틱 동안 눈앞에 거대한 마력 검 (rpg:rfx_sword — 남보라 빛 · 청록 심지) 이 솟아오른 뒤 — 가로 베기 (거대한 검기, 앞 cone 도·range 칸, 검, 공격 피해)
 * → gap 틱 뒤 그 검이 앞으로 쓰러지며 내려찍음 + 십자 폭발 (앞 length 칸 (벽 앞까지) 폭 width 칸 + 그 한가운데 가로 cross 칸씩 폭 1.2칸, 마력, 마법 피해 — 적마다 한 번)
 */
function spellbladeRelease(player, r, P, k) {
  const [glow, core] = RUNE.color.spellblade;
  const dim0 = player.dimension;
  play(dim0, "mob.evocation_illager.prepare_attack", player.location, 1.0, 1);
  play(dim0, "beacon.power", player.location, 1.2, 0.8);
  floorRune(dim0, player.location, { size: 2.2, life: k.charge / 20 + 0.3, color: glow, alpha: 0.9, rate: 160, progress: 1, grow: 0.2 });
  const life = lifeToken(player);
  // v2.27.0 2차 강화 — 예고: 남보라 하늘 · 발밑의 거대한 비전 문양 · 앞쪽 하늘의 비전 문양 (거꾸로 돎) → 가로 베기 때 섬광 → 십자 폭발 때 번쩍임 · 빛기둥 · 고리
  const c0 = { ...player.location };
  ultSkyFor(dim0, c0, "spellbladeRelease", k.charge + k.gap + 36);
  domainSigil(dim0, c0, 5.6, "arcane", glow, core, { life: (k.charge + k.gap + 2) / 20, spinRate: 50, grow: 0.25, alpha: 0.82, fade: 0.15 });
  const skyAt = skyPoint(player, c0, 12, 21);
  skyPlate(player, dim0, "sigil", skyAt, 5.6, glow, core, { cell: RFX.sigil.arcane, life: (k.charge + k.gap + 8) / 20, spinRate: -30, grow: 0.15, alpha: 0.78, fade: 0.3 });
  // 솟아오르는 검 (눈앞 2.6칸, 칼끝 위 — 1인칭에서 보이게) + 빨려 드는 빛
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const dim = player.dimension;
      const f = flat(player.getViewDirection());
      const q = (t + 2) / k.charge;
      const hilt = up(add(player.location, f, 2.6), 0.4);
      const Ls = 1.5 + q * 4.5;
      rfx2(dim, "sword", up(hilt, Ls / 2), { dir: V_UP, size: Ls / 2, life: 0.13 }, glow, core);
      for (const sd of [-1, 1]) {
        const a = add(up(hilt, 1 + q * 2.5), rightOf(f), sd * (2.6 - q * 1.6));
        fx(dim, "rpg:arrow_spark", a, { dir: scale(rightOf(f), -sd), speed: 3, size: 0.16, grow: 0.5, life: 0.25, color: core, alpha: 1 });
      }
      if (t % 6 === 0) play(dim, "block.bell.hit", hilt, 1.0 + q, 0.35);
    });
  }
  // 가로 베기 (검): 거대한 검기 + 화면을 가로지르는 빛
  later(k.charge, () => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const eye = player.getHeadLocation();
    const f = flat(player.getViewDirection());
    rfx2(dim, "wave", up(add(eye, f, 2.8), -0.4), { size: 3.3, life: 0.3, grow: 0.25, fade: 0.5 }, glow, core);
    fx(dim, "rpg:light_cut", up(add(eye, f, 3.2), -0.4), { dir: rightOf(f), size: 4.8, thick: 0.3, life: 0.4, delay: 0.05, color: glow, alpha: 1 });
    play(dim, "item.trident.thunder", eye, 1.4, 0.8);
    play(dim, "item.spear.attack_miss", eye, 0.9, 1);
    shakeAround(dim, player.location, 10, 0.2, 0.25);
    skyFlare(dim, skyAt, 3.4, glow, core, { life: 0.45 }); // v2.27.0
    for (const e of coneTargets(player, k.range, k.cone, 16)) {
      if (swordHit(player, e, P, k.mult) <= 0) continue;
      const at = bodyPoint(e);
      if (fxQuota("spellbladeCut", 8)) cutMark(dim, at, unit(sub(at, eye)), -4, 0.12, core, WHITE, 1.3); // v2.27.0 적이 많으면 앞 8명만 (파티클 예산)
      if (fxQuota("spellbladeSpark", 4)) sparks(dim, at, core, 6, 5);
      knock(e, sub(e.location, player.location), 0.3, 0.15);
    }
  });
  // 세로 내려찍기 → 십자 폭발 (마력)
  later(k.charge + k.gap, () => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const f = flat(player.getViewDirection());
    const side = rightOf(f);
    const start = { ...player.location };
    const from = up(start, 1.0);
    const L = Math.max(1, rayLength(dim, from, f, k.length));
    // 앞으로 쓰러지며 내려찍는 거대한 검 (세운 검 → 앞으로 눕힘, 4장) + 세로 참격
    const hilt = up(add(start, f, 1.8), 0.5); // 손잡이가 눈앞 1.8칸 (가까운 칼밑이 1인칭 화면을 덮지 않게)
    const tilt = [25, 55, 80];
    tilt.forEach((deg, i) => {
      later(i, () => {
        const a = (deg * Math.PI) / 180;
        const dir = unit(add(scale(V_UP, Math.cos(a)), f, Math.sin(a)));
        rfx2(dim, "sword", add(hilt, dir, L / 2), { dir, size: L / 2, life: 0.08 }, glow, core);
      });
    });
    later(3, () => rfx2(dim, "sword", add(hilt, f, L / 2), { dir: f, size: L / 2, life: 0.55, fade: 0.5 }, glow, core));
    fx(dim, "rpg:light_cut", up(add(start, f, 3), 2.2), { dir: V_UP, size: 2.6, thick: 0.35, life: 0.3, color: core, alpha: 1 });
    const mid = add(start, f, Math.min(L, k.length) * 0.55);
    for (let s = 1; s <= L; s += 1.5) {
      later(3 + Math.round(s / 3), () => {
        burst(dim, up(add(start, f, s), 0.7), glow, 1.3, 0.35);
        shock(dim, add(start, f, s), s % 3 < 1.5 ? glow : core, 0.9, 0.4);
      });
    }
    later(3, () => {
      for (const sd of [-1, 1]) for (let s = 1.2; s <= k.cross; s += 1.2) burst(dim, up(add(mid, side, sd * s), 0.7), glow, 1.2, 0.35);
      fx(dim, "rpg:magic_circle_flash", up(mid, 0.15), { size: 2.4, life: 0.45, spin: Math.random() * 360, progress: 1, color: glow, alpha: 1 });
      zonePulse(dim, mid, glow, k.cross + 1, 0.5);
      sparks(dim, up(mid, 1), core, 14, 7);
    });
    play(dim, "random.explode", mid, 1.2, 0.8);
    play(dim, "mace.heavy_smash_ground", mid, 0.8, 1);
    play(dim, "chime.amethyst_block", mid, 0.7, 1);
    shakeAround(dim, mid, 14, 0.35, 0.35);
    // v2.27.0 발동 (십자 폭발): 남보라 번쩍임 · 십자 끝 넷과 가운데의 빛기둥 (5틱 뒤 — 폭발이 퍼진 뒤) · 가운데에서 퍼지는 고리 (4틱 뒤)
    ultFlashFor(player, dim, mid, "spellbladeRelease");
    later(4, () => novaRing(dim, mid, 1.8, 3.4, glow, core, { life: 0.5 }));
    later(5, () => {
      const span = Math.min(L, k.length) * 0.4;
      for (const p of [mid, add(mid, side, k.cross), add(mid, side, -k.cross), add(mid, f, span), add(mid, f, -span)]) godRays(player, dim, p, 0, 1, glow, core, { h: 14, life: 0.7, skipNear: true });
    });
    const hit = new Set();
    const cross = [...lineTargets(player, from, f, L, k.width), ...lineTargets(player, add(up(mid, 1.0), side, -k.cross), side, k.cross * 2, 1.2)];
    for (const { e } of cross) {
      if (hit.has(e.id)) continue;
      hit.add(e.id);
      if (spellHit(player, e, P, k.mult) > 0) knock(e, V_UP, 0, 0.45);
    }
  });
  return true;
}

// ---- 마검 부여 (RUNE_SPELLBLADE): 마검사 룬을 쓰면 다음 평타 몇 번에 마력 검기
/** 플레이어 → {left: 남은 평타, until: 끝나는 틱, amount: 평타마다 더할 피해, mult: 룬의 imbue, name: 룬 이름, token: 삶 표시} */
const imbues = new Map();

/** 지금 마검 부여 (끝났거나 다 썼거나 그 사이 죽었으면 undefined) */
export function imbueOf(player) {
  const st = imbues.get(player.id);
  if (!st) return undefined;
  if (system.currentTick >= st.until || st.left <= 0 || st.token !== lifeToken(player)) return undefined;
  return st;
}

/** 무기를 든 손 자리 (눈앞 오른쪽 아래 — 1인칭에서도 보임) */
function handOf(player) {
  const eye = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  return { hand: up(add(add(eye, f, 0.9), rightOf(f), 0.42), -0.5), f };
}

/** 마검사 룬을 씀: 마검 부여 (마지막에 쓴 룬의 값으로 새로, 횟수·시간 처음부터 — 시간은 룬이 끝난 뒤 4초까지) */
function grantImbue(player, r, P) {
  const I = RUNE_SPELLBLADE.imbue;
  const mult = r.def.imbue ?? 0;
  if (!(mult > 0) || !(P > 0)) return;
  // 룬의 마지막 타격 (span 틱) 뒤 ticks 동안 (룬 타격 사이에는 몹 무적 시간에 막히므로: 마검 결계 4초 · 마검 해방 1.3초 등). 그 사이에도 다른 적에게는 쓸 수 있음
  imbues.set(player.id, { left: I.hits, until: system.currentTick + (r.def.span ?? 0) + I.ticks, amount: P * mult, mult, name: r.def.name, token: lifeToken(player) });
  const [glow, core] = RUNE.color.spellblade;
  const dim = player.dimension;
  const { hand, f } = handOf(player);
  ring(dim, hand, f, glow, 0.5, 0.3); // 손 앞 작은 고리 (1인칭 화면을 가리지 않게)
  sparks(dim, hand, core, 6, 3);
  showFaceText(player, `§9§l마검 부여 §r§f×${I.hits}`, { dist: 3.0, side: 1.2, up: -1.0 }); // 무기를 든 오른손 쪽 아래 (가운데의 적·피해 숫자를 가리지 않게)
  play(dim, "item.trident.return", player.location, 1.4, 0.7);
}

/** 평타에 더할 피해 (combat.js 가 평타·활·완드 피해를 넣기 직전에 부름 — before 이벤트 안이라 세계를 바꾸지 않음) */
function imbueBonus(player, victim) {
  const st = imbueOf(player);
  if (!st || !canHit(player, victim)) return undefined;
  return { add: st.amount, mark: RUNE_SPELLBLADE.mark, key: RUNE_SPELLBLADE.family };
}

/** 마검 부여가 더해진 평타가 들어감 (after): 한 번 쓰고, 마력 검기가 맞은 적 너머로 날아가 다른 적을 꿰뚫음 */
function imbueLanded(player, victim, _w, bonus) {
  const part = bonus?.parts?.find((x) => x.key === RUNE_SPELLBLADE.family);
  if (!part) return;
  const st = imbues.get(player.id);
  if (st) {
    st.left -= 1;
    if (st.left <= 0) imbues.delete(player.id);
  }
  arcaneWave(player, victim, part.add);
  const left = st ? Math.max(0, st.left) : 0;
  notify(player, left > 0 ? `${imbueGems(left)} §7마검 부여 남은 평타 ${left}번` : "§7마검 부여를 모두 썼습니다", 16);
}

/** 남은 횟수 보석 (◆ 남음 · ◇ 씀) */
function imbueGems(left) {
  const n = RUNE_SPELLBLADE.imbue.hits;
  return `§9${"◆".repeat(Math.max(0, Math.min(n, left)))}§8${"◇".repeat(Math.max(0, n - left))}`;
}

/**
 * 마력 검기 (마검 부여): 맞은 적 앞 lead 칸 (가까우면 내 눈에서 1.5칸, 그 사이 벽이면 벽 앞) 에서 그 너머 pierce 칸 (벽 앞까지) 까지 초승달 검기가 speed 칸/초로,
 * 폭 width 칸 안의 다른 적에게 같은 피해 (마력, 마법 피해, 적마다 한 번 — 맞은 적은 평타에 이미 들어감)
 */
function arcaneWave(player, victim, amount) {
  const I = RUNE_SPELLBLADE.imbue;
  const [glow, core] = RUNE.color.spellblade;
  const dim = player.dimension;
  const eye = player.getHeadLocation();
  // 맞은 적의 몸에서 내 눈높이에 가장 가까운 점 (보통 몹은 몸 가운데, 키 큰 몹·보스는 눈높이 — 몸 높은 곳에서 하늘로 날아가지 않게)
  const vp = victim?.isValid ? bodyAt(victim, eye.y) : add(eye, player.getViewDirection(), 3);
  let d0 = sub(vp, eye);
  // 거의 같은 높이 (근접 등): 맞은 적의 몸 높이로 수평으로 날아감 (눈높이에서 비스듬히 내려가 땅에 박히지 않게)
  const flatDist = Math.hypot(d0.x, d0.z);
  if (Math.abs(d0.y) < flatDist * 0.6) d0 = { x: d0.x, y: 0, z: d0.z };
  const dist = len(d0);
  const dir = dist > 1e-6 ? scale(d0, 1 / dist) : flat(player.getViewDirection());
  // 나타나는 자리: 맞은 적 앞 lead 칸, 단 내 눈에서 1.5칸보다 가깝지 않게 (1인칭 화면을 덮지 않게 — 아주 가까운 적은 그 너머에서),
  // 그 사이에 벽·언덕이 있으면 그 앞
  let back = Math.min(I.lead, dist - 1.5);
  if (back > 0) back = Math.min(back, rayLength(dim, vp, scale(dir, -1), back));
  const start = add(vp, dir, -back);
  const L = back + rayLength(dim, vp, dir, I.pierce); // 맞은 적 너머 pierce 칸 (벽 앞까지)
  if (L < 0.3) return; // 아주 가까운 적 바로 뒤가 벽
  const end = add(start, dir, L);
  const ticks = Math.max(1, Math.round((L / I.speed) * 20));
  for (let t = 0; t <= ticks; t += 2) {
    const s = Math.min(L, (t / 20) * I.speed);
    later(t, () => crescent(dim, add(start, dir, s), dir, 1.25, glow, core, -90, 0.14));
  }
  flyStreak(dim, start, end, I.speed, glow, core, 0.6, 0.18);
  if (victim?.isValid) {
    cutMark(dim, vp, dir, 60, 0.06, glow, core, 0.9);
    sparks(dim, vp, core, 5, 4);
  }
  play(dim, "mob.evocation_illager.cast_spell", start, 1.8, 0.35);
  const life = lifeToken(player);
  for (const { e, t } of lineTargets(player, start, dir, L, I.width)) {
    if (victim && e.id === victim.id) continue;
    later(Math.max(1, Math.round((t / I.speed) * 20)), () => {
      if (!sameLife(player, life) || !e.isValid) return;
      if (runeHit(player, e, amount, 1, "int", EntityDamageCause.magic, "spell") <= 0) return;
      const at = bodyPoint(e);
      cutMark(dim, at, dir, 60, 0.06, glow, core, 0.8);
      burst(dim, at, glow, 0.6, 0.2);
    });
  }
}

/** 손에 든 것이 초월 무기 (스킬이 있음) 인지 — 그 상태 줄은 skills.js 가 그리고 마검 부여는 뒤에 붙음 */
function heldSkillWeapon(player) {
  try {
    return !!parseWeapon(heldTypeId(player))?.skills;
  } catch {
    return false;
  }
}

/** 화면 하단 상태 줄의 마검 부여 (룬·초월 무기 스킬 줄 뒤에 붙음) */
function imbueHud(player) {
  const st = imbueOf(player);
  return st ? `${imbueGems(st.left)} §9마검 §7${Math.max(0, (st.until - system.currentTick) / 20).toFixed(1)}초` : "";
}

/** 마검 부여가 있는 동안: 손 둘레 남보라·청록 빛 (4틱마다, 다른 사람에게도 보임). 끝나면 흩어지며 사라짐 */
function imbueTick() {
  const now = system.currentTick;
  if (!imbues.size) return;
  const [glow, core] = RUNE.color.spellblade;
  for (const player of world.getAllPlayers()) {
    const st = imbues.get(player.id);
    if (!st) continue;
    let dim;
    try {
      dim = player.dimension;
    } catch {
      continue;
    }
    if (!imbueOf(player)) {
      imbues.delete(player.id);
      if (now >= st.until && st.left > 0 && st.token === lifeToken(player)) {
        burst(dim, handOf(player).hand, glow, 0.5, 0.25);
        // 상태 줄의 마지막 "◆◆◇ 마검 0.2초" 가 화면에 남아 아직 있는 것처럼 보이지 않게
        if (actionbarFree(player) && !promptedRecently(player)) notify(player, "§7마검 부여가 끝났습니다", 16);
      }
      continue;
    }
    const { hand, f } = handOf(player);
    const side = rightOf(f);
    for (let i = 0; i < 2; i++) {
      const a = now * 0.5 + i * Math.PI;
      fx(dim, "rpg:arrow_spark", add(up(hand, Math.sin(a) * 0.18), side, Math.cos(a) * 0.22), { dir: V_UP, speed: 0.8, size: 0.09, life: 0.35, color: i ? core : glow, alpha: 1 });
    }
  }
}

// ============================================================ v2.14.0 원소 검사 (마력 + 힘·민첩 절반: 검에 불·얼음·번개를 실음)
/** 원소가 맞은 적에게 남기는 것: 불 = 불붙음 (fire 초) · 얼음 = 구속 (slowAmp, slowTicks 틱). 번개는 thunderChain */
function elementEffect(e, el, k) {
  if (el === "fire") burn(e, k.fire ?? 3);
  else if (el === "ice") effect(e, "slowness", k.slowTicks ?? 40, k.slowAmp ?? 0);
}

/**
 * 원소의 맞힌 자리 연출: 불 = 발밑에서 타오르는 불꽃 · 얼음 = 발밑에 얼음 결정 (rpg:rfx_crystal) · 번개 = 몸에 번개 구슬 (rpg:rfx_plasma) — v2.25.0.
 * fxDelay 틱 뒤에 그림 (적이 많을 때 한 틱의 파티클을 나눔 — 피해는 그대로)
 */
function elementHitFx(dim, e, el, fxDelay = 0) {
  if (!e?.isValid) return;
  if (fxDelay > 0) {
    later(fxDelay, () => elementHitFx(dim, e, el));
    return;
  }
  if (!fxQuota("elementHit", 5)) return; // v2.27.0 한 틱에 5명까지 (적 24명을 한꺼번에 맞혀도 파티클 예산 안 — 피해 · 원소 효과는 모두)
  const [glow, core] = RUNE_ELEMENTS[el].color;
  const at = bodyPoint(e);
  const feet = e.location;
  if (el === "fire") {
    fx(dim, "rpg:fire_wall", feet, { size: 1.6, life: 0.6, color: glow, alpha: 0.95, spin: Math.random() * 360 });
    sparks(dim, at, core, 4, 4);
  } else if (el === "ice") {
    const s = 0.6 + bodyPad(e) * 0.5;
    rfx2(dim, "crystal", add(feet, { x: 0.3, y: 0, z: 0.2 }, 1 + bodyPad(e)), { size: s, life: 0.9, grow: 0.1, fade: 0.3, cell: Math.floor(Math.random() * 3), alpha: 0.95 }, glow, WHITE, [0, RFX.crystalCore]);
    rfx2(dim, "crystal", add(feet, { x: -0.3, y: 0, z: -0.25 }, 1 + bodyPad(e)), { size: s * 0.8, life: 0.85, grow: 0.1, fade: 0.3, cell: Math.floor(Math.random() * 3), alpha: 0.95 }, glow, WHITE, [0, RFX.crystalCore]);
    burst(dim, at, core, 0.8, 0.22);
  } else {
    burst(dim, at, glow, 0.9, 0.22);
    plasmaOrb(dim, at, 0.5 + bodyPad(e) * 0.4, glow, { life: 0.26 });
    sparks(dim, at, core, 3, 6);
  }
}

/**
 * 번개가 튐: from 적의 range 칸 안에서 아직 맞지 않은 (hit 에 없는) 가장 가까운 적 하나에게 위력 × mult (마법). 튄 적 (없으면 undefined).
 * fxDelay = 그림만 늦춤 (피해는 지금)
 */
function thunderChain(player, from, P, mult, range, hit, fxDelay = 0) {
  if (!from?.isValid) return undefined;
  const next = nearestTo(
    around(player, from.location, range, 3).filter((e) => !hit.has(e.id) && e.id !== from.id),
    from.location,
  )[0];
  if (!next) return undefined;
  hit.add(next.id);
  const dim = player.dimension;
  const [glow, core] = RUNE_ELEMENTS.thunder.color;
  const a = bodyPoint(from);
  const to = bodyPoint(next);
  const draw = () => {
    zap(dim, a, to, glow, core, 0.18, 0.3);
    play(dim, "ambient.weather.lightning.impact", to, 1.8, 0.2);
  };
  if (fxDelay > 0) later(fxDelay, draw);
  else draw();
  if (spellHit(player, next, P, mult) > 0) elementHitFx(dim, next, "thunder", fxDelay);
  return next;
}

/** 불꽃이 앞 땅을 따라 번짐 (1인칭 화면 아래쪽에 보임): 부채꼴 cone 도, 거리 dist */
function flameFan(player, glow, cone, dist, n = 5) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  for (let i = 0; i < n; i++) {
    const d = turn(f, -cone / 2 + (cone * i) / (n - 1));
    fx(dim, "rpg:fire_wall", add(player.location, d, dist + (i % 2) * 0.6), { size: 1.4, life: 0.55 + i * 0.03, color: glow, alpha: 0.9, spin: Math.random() * 360 });
  }
}

/** 원소 초승달 검기 (rpg:rfx_wave): 눈앞 2.3칸에서 앞으로 퍼짐 (vertical = 세로) */
function elementWave(player, el, vertical = false, size = 1.55) {
  const [glow, core] = RUNE_ELEMENTS[el].color;
  const eye = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  rfx2(player.dimension, "wave", up(add(eye, f, 2.3), vertical ? -0.1 : -0.35), { dir: f, speed: 6, size, life: 0.24, grow: 0.35, fade: 0.6, spin: vertical ? 90 : 4, alpha: 0.92 }, glow, core);
}

/**
 * 찌르는 빛의 칼 (rpg:rfx_sword, 원소 · 검사 색): 오른손 (몸 높이 from 의 오른쪽 위) 에서 찌르는 끝 (from + f × L) 까지 칼끝이 뻗음 (3틱) 뒤 잠깐 머묾.
 * 1인칭에서 칼이 보는 쪽과 나란해 점으로 보이지 않게 손에서 비스듬히 뻗음. 머무는 칼은 2틱에 나타남 (뻗는 칼과 1틱 겹침 — 깜빡임 없게)
 */
function elementThrust(dim, from, f, L, glow, core) {
  const S = 0.9;
  const hand = up(add(from, rightOf(f), 0.3), 0.15);
  const end = add(from, f, L);
  const dv = unit(sub(end, hand));
  const Lv = len(sub(end, hand));
  const s0 = Math.min(0.6 + S, Lv);
  rfx2(dim, "sword", add(hand, dv, s0), { dir: dv, speed: Math.max(0, Lv - s0 - S) / 0.15, size: S, life: 0.15 }, glow, core);
  later(2, () => rfx2(dim, "sword", add(hand, dv, Math.max(s0, Lv - S)), { dir: dv, size: S, life: 0.25, fade: 0.6 }, glow, core));
}

/** 화염 베기: 앞 cone 도 · range 칸을 불꽃 검으로 벰 (검, 공격 피해) + 불붙음 fire 초. 앞 땅에 불꽃이 번짐 — v2.25.0 불꽃 초승달 검기 */
function flameSlash(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE_ELEMENTS.fire.color;
  arc(dim, up(add(head, f, 1.4), -0.3), f, { shape: "heavy", flip: false, size: 2.9, life: 0.28, speed: 3, spin: -6, color: core, aura: glow });
  elementWave(player, "fire", false, 1.65);
  fx(dim, "rpg:light_cut", up(add(head, f, 2.0), -0.25), { dir: rightOf(f), size: 2.6, thick: 0.2, life: 0.32, delay: 0.04, color: glow, alpha: 1 });
  flameFan(player, glow, 96, 2.4);
  play(dim, "item.firecharge.use", head, 1.1, 0.9);
  play(dim, "item.spear.attack_miss", head, 1.0, 0.9);
  let n = 0;
  for (const e of coneTargets(player, k.range, k.cone, 10)) {
    if (swordHit(player, e, P, k.mult) <= 0) continue;
    elementEffect(e, "fire", k);
    elementHitFx(dim, e, "fire", Math.floor(n / 4));
    const at = bodyPoint(e);
    cutMark(dim, at, unit(sub(at, head)), -8, 0.1, core, WHITE, 1.0);
    n++;
  }
  return true;
}

/**
 * 서리 일섬: 앞 range 칸 (벽 앞까지) 폭 width 를 찌름 (검, 공격 피해) → 땅에 서리 줄 → gap 틱 뒤 그 줄에 얼음 가시가 솟음
 * (얼음, 마법 피해: 그때 줄 위의 적) + 구속 (slowAmp, slowTicks) — v2.25.0 얼음빛 칼 (rpg:rfx_sword) 이 뻗고 고드름 둘이 나란히 날아감,
 * 솟는 가시 = 얼음 결정 (rpg:rfx_crystal, 쓴 사람 1.4칸 안은 비움)
 */
function frostThrust(player, r, P, k) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const feet = { ...player.location };
  const from = up(add(feet, f, 0.5), 1.1);
  const L = Math.max(1, rayLength(dim, from, f, k.range));
  const [glow, core] = RUNE_ELEMENTS.ice.color;
  elementThrust(dim, from, f, L, glow, WHITE);
  for (const sd of [-1, 1]) {
    const o = add(from, rightOf(f), sd * 0.45);
    const s0 = Math.min(1.4, L);
    rfx2(dim, "icicle", add(o, f, s0), { dir: f, speed: 60, size: 0.34, life: Math.max(0.05, (L - s0) / 60) }, glow, WHITE);
  }
  fx(dim, "rpg:light_cut", add(from, f, L / 2), { dir: f, size: L / 2, thick: 0.2, life: 0.3, color: core, alpha: 1 });
  for (let s = 0.9; s <= L + 0.5; s += 1.0) fx(dim, "rpg:light_cut", up(add(feet, f, s), 0.08), { dir: f, size: 0.5, thick: 0.25, life: k.gap / 20 + 0.1, color: glow, alpha: 0.9 }); // 서리 줄 (가시가 솟을 자리)
  play(dim, "item.trident.throw", from, 1.3, 0.9);
  play(dim, "random.glass", from, 1.8, 0.4);
  for (const { e } of lineTargets(player, from, f, L, k.width)) {
    if (swordHit(player, e, P, k.mult) > 0) burst(dim, bodyPoint(e), core, 0.7, 0.2);
  }
  const life = lifeToken(player);
  later(k.gap, () => {
    if (!sameLife(player, life)) return;
    const p0 = player.location;
    for (let s = 0.9; s <= L + 0.5; s += 0.9) {
      const at = add(feet, f, s);
      at.y = groundAt(dim, at, feet.y);
      if (Math.hypot(at.x - p0.x, at.z - p0.z) < 1.4) continue;
      iceCrystal(player, dim, at, (0.62 + (Math.round(s * 10) % 3) * 0.1) * (0.9 + Math.random() * 0.2), glow, { life: 0.95, fade: 0.3, alpha: 0.95 });
    }
    sparks(dim, add(from, f, L / 2), core, 8, 4);
    particle(dim, "minecraft:snowflake_particle", add(from, f, L / 2));
    play(dim, "mob.player.hurt_freeze", from, 1.0, 0.8);
    play(dim, "break.amethyst_cluster", add(from, f, L / 2), 0.9, 1);
    let n = 0;
    for (const { e } of lineTargets(player, from, f, L, k.width)) {
      if (spellHit(player, e, P, k.mult) <= 0) continue;
      elementEffect(e, "ice", k);
      elementHitFx(dim, e, "ice", Math.floor(n++ / 4));
    }
  });
  return true;
}

/**
 * 뇌광 베기: 앞 cone 도 · range 칸 베기 (검, 공격 피해) → gap 틱 뒤 벤 적마다 하늘에서 번개 (번개, 마법 피해)
 * → 그 적의 chain 칸 안 아직 맞지 않은 적 하나에게 튐 (위력 × chainMult, 마법 — 적마다 한 번) —
 * v2.25.0 번개빛 초승달 검기, 하늘에서 내리치는 번개 그림 (rpg:rfx_bolt) · 몸에 번개 구슬. 적이 많으면 그림을 두 명씩 한 틱 늦게 (피해는 같은 틱)
 */
function thunderSlash(player, r, P, k) {
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  const [glow, core] = RUNE_ELEMENTS.thunder.color;
  arc(dim, up(add(head, f, 1.4), -0.3), f, { shape: "arc", flip: true, size: 2.9, life: 0.24, speed: 3, spin: 8, color: core, aura: glow });
  elementWave(player, "thunder", false, 1.6);
  zap(dim, up(add(head, turn(f, -45), 2.0), -0.45), up(add(head, turn(f, 45), 2.0), -0.45), glow, core, 0.12, 0.25); // 칼날을 따라 튀는 전기
  play(dim, "item.spear.attack_miss", head, 1.1, 0.9);
  play(dim, "item.trident.thunder", head, 1.8, 0.3);
  const cut = [];
  for (const e of coneTargets(player, k.range, k.cone, 10)) {
    if (swordHit(player, e, P, k.mult) <= 0) continue;
    cut.push(e);
    sparks(dim, bodyPoint(e), core, 4, 5);
  }
  const life = lifeToken(player);
  later(k.gap, () => {
    if (!sameLife(player, life)) return;
    const hit = new Set(cut.map((e) => e.id));
    cut.forEach((e, idx) => {
      if (!e.isValid) return;
      const fd = Math.floor(idx / 2); // 그림만 두 명씩 한 틱 늦게
      const feet = { ...e.location };
      const strike = () => rfx2(dim, "bolt", up(feet, 3.6), { size: 3.6, life: 0.26, cell: Math.floor(Math.random() * 4) }, glow, WHITE, [0, RFX.boltCore]);
      if (fd > 0) later(fd, strike);
      else strike();
      if (spellHit(player, e, P, k.mult) > 0) elementHitFx(dim, e, "thunder", fd);
      thunderChain(player, e, P, k.chainMult, k.chain, hit, fd);
    });
    if (cut.length) play(dim, "ambient.weather.lightning.impact", player.location, 1.4, 0.4);
  });
  return true;
}

/**
 * 삼원소 연격: gap 틱마다 — 불 가로 베기 (앞 cone 도 · range 칸, 검 + 불붙음) → 얼음 세로 베기 (검 + 구속)
 * → 번개 찌르기 (thrust 칸 직선 폭 1.4, 마법 + chain 칸 안 아직 맞지 않은 적 하나에게 위력 × chainMult) —
 * v2.25.0 불 · 얼음 초승달 검기 (rpg:rfx_wave 가로 · 세로), 얼음 결정, 번개빛 칼 (rpg:rfx_sword) 찌르기
 */
function triElement(player, r, P, k) {
  const life = lifeToken(player);
  const order = ["fire", "ice", "thunder"];
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const eye = player.getHeadLocation();
    const f = flat(player.getViewDirection());
    const el = order[i % 3];
    const [glow, core] = RUNE_ELEMENTS[el].color;
    if (el !== "thunder") {
      const vertical = el === "ice";
      arc(dim, up(add(eye, f, 1.5), -0.3), f, { shape: vertical ? "thin" : "heavy", flip: vertical, size: vertical ? 2.5 : 2.8, life: 0.26, speed: 3, spin: vertical ? 84 : -6, color: core, aura: glow });
      elementWave(player, el, vertical, vertical ? 1.35 : 1.6);
      if (el === "fire") {
        flameFan(player, glow, 80, 2.2, 4);
        play(dim, "item.firecharge.use", eye, 1.2, 0.8);
      } else {
        for (let j = 0; j < 3; j++) iceCrystal(player, dim, add(player.location, turn(f, -30 + j * 30), 2.3), 0.6, glow, { life: 0.6, fade: 0.35, v: j });
        play(dim, "random.glass", eye, 1.5, 0.5);
      }
      play(dim, "item.spear.attack_miss", eye, 1.1 + i * 0.15, 0.9);
      let n = 0;
      for (const e of coneTargets(player, k.range, k.cone, 10)) {
        if (swordHit(player, e, P, k.mult) <= 0) continue;
        elementEffect(e, el, k);
        elementHitFx(dim, e, el, Math.floor(n++ / 4));
      }
    } else {
      const from = up(add(player.location, f, 0.5), 1.1);
      const L = Math.max(1, rayLength(dim, from, f, k.thrust));
      zap(dim, from, add(from, f, L), glow, core, 0.26, 0.35);
      elementThrust(dim, from, f, L, glow, WHITE);
      ring(dim, add(from, f, L), f, glow, 0.9, 0.3);
      play(dim, "item.trident.thunder", eye, 1.4, 0.6);
      const list = lineTargets(player, from, f, L, 1.4).map((o) => o.e);
      const hit = new Set(list.map((e) => e.id));
      list.forEach((e, idx) => {
        const fd = Math.floor(idx / 2);
        if (spellHit(player, e, P, k.mult) > 0) elementHitFx(dim, e, "thunder", fd);
        thunderChain(player, e, P, k.chainMult, k.chain, hit, fd);
      });
    }
  });
  return true;
}

/**
 * 원소 해방: charge 틱 동안 눈앞의 거대한 검 (rpg:rfx_sword) 에 불 → 얼음 → 번개가 차례로 감김 → 거대한 원소 검기 셋 (rpg:rfx_wave) 을 부채꼴로
 * (가운데 불 · 왼쪽 얼음 · 오른쪽 번개, ±spread 도), 각각 range 칸 (벽 앞까지) 폭 width 를 speed 칸/초로 꿰뚫음 — 지나간 자리에 불꽃 · 얼음 가시 · 번개.
 * 적마다 한 번 (먼저 닿은 검기 · 튄 번개): 위력 × mult (마법) + 그 원소 (불붙음 · 구속 · chain 칸 안 한 명에게 위력 × chainMult)
 */
function elementRelease(player, r, P, k) {
  const life = lifeToken(player);
  const dim0 = player.dimension;
  const order = ["fire", "ice", "thunder"];
  play(dim0, "mob.evocation_illager.prepare_attack", player.location, 1.0, 1);
  play(dim0, "beacon.power", player.location, 1.3, 0.8);
  // v2.27.0 2차 강화 — 예고: 모으는 동안 하늘빛이 불 → 얼음 → 번개로 바뀜 · 발밑과 앞쪽 하늘의 비전 문양도 그 원소 색으로 → 발동 때 하늘의 섬광
  const skies = ultOf("elementRelease").fogs ?? [];
  const third = Math.max(1, Math.round(k.charge / 3));
  order.forEach((el, j) => {
    later(j * third, () => {
      if (!sameLife(player, life)) return;
      const [g0, c0] = RUNE_ELEMENTS[el].color;
      const last = j === order.length - 1;
      ultSkyFor(player.dimension, player.location, "elementRelease", last ? k.charge - j * third + 34 : third + 2, skies[j]);
      domainSigil(player.dimension, player.location, 5.2, "arcane", g0, c0, { life: (last ? k.charge - j * third + 2 : third + 1) / 20, spinRate: 70, grow: 0.12, alpha: 0.82, fade: 0.1 });
      skyPlate(player, player.dimension, "sigil", skyPoint(player, player.location, 12, 21), 5, g0, c0, { cell: RFX.sigil.arcane, life: (last ? k.charge - j * third + 4 : third + 1) / 20, spinRate: 50, alpha: 0.75, fade: 0.15 }); // 앞쪽 하늘의 원소 문양
    });
  });
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const dim = player.dimension;
      const f = flat(player.getViewDirection());
      const q = (t + 2) / k.charge;
      const el = order[Math.min(2, Math.floor(q * 3 - 1e-6))];
      const [glow, core] = RUNE_ELEMENTS[el].color;
      const hilt = up(add(player.location, f, 2.4), 0.5);
      const Ls = 1.6 + q * 2.6;
      rfx2(dim, "sword", up(hilt, Ls / 2), { dir: V_UP, size: Ls / 2, life: 0.13 }, glow, core);
      // 검 둘레를 돌며 감기는 세 원소
      for (let j = 0; j < 3; j++) {
        const a = t * 0.5 + (j * Math.PI * 2) / 3;
        const p = add(add(up(hilt, 0.6 + q * 1.8 * ((j + 1) / 3)), rightOf(f), Math.cos(a) * 0.5), f, Math.sin(a) * 0.5);
        fx(dim, "rpg:arrow_spark", p, { dir: V_UP, speed: 1.5, size: 0.16, grow: 0.8, life: 0.25, color: RUNE_ELEMENTS[order[j]].color[0], alpha: 1 });
      }
      if (t % 6 === 0) play(dim, el === "fire" ? "fire.ignite" : el === "ice" ? "random.glass" : "item.trident.thunder", hilt, 1.4, 0.4);
    });
  }
  later(k.charge, () => {
    if (!sameLife(player, life)) return;
    const dim = player.dimension;
    const f = flat(player.getViewDirection());
    const from = up(add(player.location, f, 0.6), 1.1);
    const hit = new Set();
    const plan = [];
    for (const [el, deg] of [
      ["fire", 0],
      ["ice", -k.spread],
      ["thunder", k.spread],
    ]) {
      const d = turn(f, deg);
      const L = Math.max(1, rayLength(dim, from, d, k.range));
      const [glow, core] = RUNE_ELEMENTS[el].color;
      const s0 = Math.min(1.4, L * 0.5);
      rfx2(dim, "wave", add(from, d, s0), { dir: d, speed: k.speed, size: k.width * 1.2, life: Math.max(0.05, (L - s0) / k.speed), fade: 0.15 }, glow, core);
      // 지나간 자리: 불 = 불꽃 · 얼음 = 얼음 가시 · 번개 = 땅을 따라 튀는 번개
      for (let s = 2.5; s < L; s += 2.6) {
        const at = add(add(player.location, f, 0.6), d, s);
        later(Math.round((s / k.speed) * 20), () => {
          if (el === "fire") fx(dim, "rpg:fire_wall", at, { size: 1.5, life: 0.7, color: glow, alpha: 0.9, spin: Math.random() * 360 });
          else if (el === "ice") fx(dim, "rpg:ice_spike", at, { size: 1.2, life: 0.8, spin: Math.random() * 30 - 15, color: glow, alpha: 1 });
          else zap(dim, up(at, 0.2), up(add(at, d, 2.4), 0.2), glow, core, 0.12, 0.25);
        });
      }
      flyStreak(dim, from, add(from, d, L), k.speed, glow, core, 1.2, 0.45);
      const endAt = add(from, d, L);
      later(Math.round((L / k.speed) * 20), () => godRays(player, dim, { x: endAt.x, y: groundAt(dim, endAt, from.y - 1.1), z: endAt.z }, 0, 1, glow, core, { h: 13, life: 0.6, skipNear: true })); // v2.27.0 검기가 닿는 끝의 빛기둥
      for (const { e, t } of lineTargets(player, from, d, L, k.width)) plan.push({ e, el, d, at: Math.max(0, Math.round((t / k.speed) * 20)) });
    }
    arc(dim, up(add(player.getHeadLocation(), f, 1.6), -0.3), f, { shape: "heavy", flip: false, size: 3.6, life: 0.3, speed: 4, spin: -4, color: WHITE, aura: RUNE_ELEMENTS.fire.color[0] });
    play(dim, "item.trident.thunder", from, 1.3, 0.8);
    play(dim, "firework.large_blast", from, 1.0, 0.8);
    shakeAround(dim, player.location, 10, 0.25, 0.3);
    // v2.27.0 발동: 흰 번쩍임 · 발밑에서 퍼지는 고리 (1틱 뒤 — 검기가 나가는 틱의 파티클 예산)
    ultFlashFor(player, dim, from, "elementRelease");
    const feet0 = { ...player.location };
    const skyAt = skyPoint(player, feet0, 12, 21);
    later(1, () => {
      novaRing(dim, feet0, 1.6, 3.6, WHITE, RUNE_ELEMENTS.fire.color[1], { life: 0.45 });
      skyFlare(dim, skyAt, 3.8, RUNE_ELEMENTS.thunder.color[0], WHITE, { life: 0.5 });
    });
    plan.sort((a, b) => a.at - b.at);
    const planned = new Set(plan.map((p) => p.e.id)); // 번개는 검기가 닿지 않는 적에게만 튐 (검기 피해를 가로채지 않게)
    const chainSkip = { has: (id) => hit.has(id) || planned.has(id), add: (id) => hit.add(id) };
    for (const p of plan) {
      later(p.at, () => {
        if (!sameLife(player, life) || !p.e.isValid || hit.has(p.e.id)) return;
        hit.add(p.e.id);
        if (spellHit(player, p.e, P, k.mult) <= 0) return;
        elementEffect(p.e, p.el, k);
        elementHitFx(dim, p.e, p.el);
        knock(p.e, p.d, 0.5, 0.25);
        if (p.el === "thunder") thunderChain(player, p.e, P, k.chainMult, k.chain, chainSkip);
      });
    }
  });
  return true;
}

// ============================================================ v2.14.0 정령사 (마력: 정령을 불러 몇 초 동안 대신 싸우게 함)
// 정령 = 엔티티가 아니라 스크립트가 움직이고 파티클로 그리는 빛 (RUNE_SPIRITS). 내가 보는 적부터 노리고, 없으면 가장 오래 안 때린 적
/** 플레이어 → 정령 목록 @type {Map<string, any[]>} */
const spirits = new Map();
/** 정령 피해 예약 (몹 무적 시간 10틱): 플레이어 → (적 → 마지막으로 잡아 둔 정령 피해 틱) @type {Map<string, Map<string, number>>} */
const spiritHitAt = new Map();
/** 몹이 마지막으로 피해를 받은 틱 (누가 때렸든 — 바닐라 무적 시간은 몹마다 하나). 정령 피해가 내 공격·다른 룬·불에 막히지 않게 @type {Map<string, number>} */
const hurtAt = new Map();
/** 싸운 중립 몹·동물 (정령이 노려도 되는 적): 플레이어 → (몹 → 마지막으로 때리거나 맞은 틱) @type {Map<string, Map<string, number>>} */
const fought = new Map();
/** 싸운 것으로 치는 시간 (틱) */
const FOUGHT_TICKS = 200;
/** 몬스터 무리지만 먼저 건드리지 않으면 싸우지 않는 몹 (정령이 스스로 노리지 않음) */
const NEUTRAL_MONSTERS = new Set(["minecraft:enderman", "minecraft:zombie_pigman", "minecraft:piglin"]);
/** 정령 자리: 보는 방향 (위아래 포함) 기준 가로 h 도 (− 왼쪽) · 세로 v 도 — 위아래를 봐도 1인칭 화면 위쪽 양옆 (폰 세로 시야 ±30° 안) */
const SPIRIT_SLOTS = [
  { h: -20, v: 12 },
  { h: 20, v: 12 },
  { h: -32, v: 3 },
  { h: 32, v: 3 },
  { h: -9, v: 21 },
  { h: 9, v: 21 },
  { h: -33, v: 18 },
  { h: 33, v: 18 },
];
/** 눈에서 정령까지 (칸). 벽이 더 가까우면 벽 앞 */
const SPIRIT_DIST = 2.2;
/** 눈이 한 번 (2틱) 에 이보다 멀리 옮겨지면 순간 이동으로 봄 (바람의 정령 돌진도 멈추고 같이 옮김) */
const SPIRIT_TELEPORT = 8;
/** 정령이 노리지 않는 엔티티 표시 (허수아비 킹이 분신 사이에 숨은 동안: 정령이 진짜를 찾아 주지 않게) */
export const NO_AUTO_TAG = "rpg_no_auto";
const DEG = Math.PI / 180;

/** 눈에서 화면의 (가로 h 도, 세로 v 도) 쪽으로 가는 방향 (보는 방향 위아래 포함, − h 는 왼쪽) */
function viewRay(player, h, v) {
  const f = unit(player.getViewDirection());
  let fwd = flat(f);
  if (Math.hypot(f.x, f.z) < 0.02) {
    // 바로 아래·위를 볼 때: 보는 방향의 가로 성분이 거의 없으므로 몸이 향한 쪽 (yaw) 으로 화면 오른쪽을 정함
    const yaw = ((player.getRotation?.().y ?? 0) * Math.PI) / 180;
    fwd = { x: -Math.sin(yaw), y: 0, z: Math.cos(yaw) };
  }
  const r = rightOf(fwd); // 화면 오른쪽
  const u = { x: r.y * f.z - r.z * f.y, y: r.z * f.x - r.x * f.z, z: r.x * f.y - r.y * f.x }; // 화면 위 (r × f)
  return unit(add(add(f, r, Math.tan(h * DEG)), u, Math.tan(v * DEG)));
}

/** 정령이 떠 있을 자리 (위아래로 천천히 흔들림) */
function spiritSlot(player, slot, now, dim = player.dimension) {
  const s = SPIRIT_SLOTS[slot % SPIRIT_SLOTS.length];
  const eye = player.getHeadLocation();
  const d = viewRay(player, s.h, s.v + Math.sin(now * 0.15 + slot * 1.7) * 2);
  const dist = Math.max(0.8, Math.min(SPIRIT_DIST, rayLength(dim, eye, d, SPIRIT_DIST + 0.4) - 0.4)); // 벽에서 0.4칸 앞
  return add(eye, d, dist);
}

/** 정령 이름이 뜰 자리: 화면에서 정령 바로 위, 더 멀리 (눈앞 2칸에 띄우면 글자가 화면을 가릴 만큼 큼) */
function spiritLabelAt(player, slot, dim) {
  const s = SPIRIT_SLOTS[slot % SPIRIT_SLOTS.length];
  const eye = player.getHeadLocation();
  const d = viewRay(player, s.h, s.v + 6);
  return add(eye, d, Math.max(1.2, Math.min(4.5, rayLength(dim, eye, d, 4.8) - 0.3)));
}

/** 늘 노리는 적: RPG 몬스터 · 보스 · 훈련용 허수아비 · 적대 몹 (중립 몬스터·동물·보스 소품은 아님) */
function hostileMob(e) {
  try {
    const t = e.getComponent("minecraft:type_family");
    if (!t) return false;
    if (t.hasTypeFamily("rpg_monster") || t.hasTypeFamily("rpg_boss") || t.hasTypeFamily("rpg_dummy")) return true;
    if (t.hasTypeFamily("monster")) return !NEUTRAL_MONSTERS.has(e.typeId);
    return t.hasTypeFamily("dragon");
  } catch {
    return false;
  }
}

/** 정령이 노려도 되는 적: 적대 몹, 또는 이 플레이어가 최근 FOUGHT_TICKS 안에 때렸거나 맞은 몹 (화난 중립 몹 · 보스 촉수 등) */
function spiritFoe(player, e) {
  try {
    if (e.hasTag(NO_AUTO_TAG)) return false;
  } catch {
    return false;
  }
  if (e.typeId === "minecraft:player") return isDuelPartner(player, e); // v2.26.0 결투 상대
  if (hostileMob(e)) return true;
  const t = fought.get(player.id)?.get(e.id);
  return t !== undefined && system.currentTick - t <= FOUGHT_TICKS;
}

/** before 이벤트: 플레이어가 때리거나 맞은 중립 몹·동물을 적어 둠 (적대 몹은 늘 노리므로 적지 않음) */
function noteFight(ev) {
  const victim = ev.hurtEntity;
  const src = ev.damageSource?.damagingEntity;
  if (!victim || !src || victim.id === src.id) return;
  const pl = src.typeId === "minecraft:player" ? src : victim.typeId === "minecraft:player" ? victim : undefined;
  if (!pl) return;
  const other = pl === src ? victim : src;
  if (other.typeId === "minecraft:player" || hostileMob(other)) return;
  let m = fought.get(pl.id);
  if (!m) fought.set(pl.id, (m = new Map()));
  const now = system.currentTick;
  m.set(other.id, now);
  if (m.size > 32) for (const [id, t] of m) if (now - t > FOUGHT_TICKS) m.delete(id);
}

/** 정령 부르기 (정령사 룬 다섯): k.spirits 를 ticks 틱 동안 — 정령마다 hits ÷ 정령 수 번, first 틱 뒤부터 gap 틱마다 (둘째 정령부터 stagger 틱씩 늦게). 넷을 부르면 정령왕이 나타남 */
function summonSpirits(player, r, P, k) {
  const now = system.currentTick;
  const dim = player.dimension;
  const list = spirits.get(player.id) ?? [];
  const life = lifeToken(player);
  const each = Math.max(1, Math.round(k.hits / k.spirits.length));
  const king = k.spirits.length > 1 ? spiritKing(player) : undefined;
  k.spirits.forEach((kind, i) => {
    const used = new Set(list.map((x) => x.slot));
    let slot = 0;
    while (used.has(slot)) slot++;
    const sp = { kind, def: RUNE_SPIRITS[kind], owner: player, dim, P, k, born: now, until: now + k.ticks, left: each, nextAt: now + k.first + i * (k.stagger ?? 0), slot, pos: spiritSlot(player, slot, now, dim), eye: player.getHeadLocation(), token: life, lastHit: new Map(), busyUntil: 0, dash: undefined };
    list.push(sp);
    const [glow, core] = sp.def.color;
    burst(dim, sp.pos, glow, 0.55, 0.3); // 눈앞 2칸이므로 작게
    sparks(dim, sp.pos, core, 3, 3);
    if (king) streak(dim, king, sp.pos, glow, 0.08, 0.35, 0.8, 0.1 + i * 0.08); // 정령왕 → 정령 자리
    later(i * 3, () => {
      if (sameLife(player, life)) showTextAt(dim, spiritLabelAt(player, sp.slot, dim), `${sp.def.text}§l${sp.def.name}`);
    });
  });
  spirits.set(player.id, list);
  const [glow] = RUNE.color.spirit;
  floorRune(dim, player.location, { size: 1.6, life: 0.8, color: glow, alpha: 0.9, rate: 120, progress: 1, grow: 0.2 });
  play(dim, "mob.evocation_illager.prepare_summon", player.location, 1.3, 0.7);
  play(dim, "mob.allay.idle", player.location, 1.2, 0.8);
  // v2.27.0 2차 강화 (정령왕의 부름 — 초월): 오로라 하늘 · 청록 번쩍임 · 정령왕 뒤 하늘의 정령 문양 · 발밑의 정령 문양 · 앞쪽 네 원소 빛기둥 (2틱 뒤)
  if (king && r.grade === "transcend") {
    const [sg, sc] = RUNE.color.spirit;
    ultSkyFor(dim, player.location, "summonSpirits", 100);
    ultFlashFor(player, dim, player.location, "summonSpirits");
    skyPlate(player, dim, "sigil", up(add(king, facingOf(player), 3.5), 0.6), 4.8, sg, sc, { cell: RFX.sigil.spirit, life: 2.2, spinRate: 25, grow: 0.15, alpha: 0.8, fade: 0.35 });
    domainSigil(dim, player.location, 4.8, "spirit", sg, sc, { life: 1.9, spinRate: 30, grow: 0.2, alpha: 0.82 });
    later(2, () => {
      if (sameLife(player, life)) godRays(player, player.dimension, player.location, 6, 4, sg, sc, { arc: [-60, 60], h: 15, life: 1.1, colors: k.spirits.map((kind) => RUNE_SPIRITS[kind].color) });
    });
  }
  return true;
}

/**
 * 정령이 노릴 적: 내가 보는 적 (범위 안) → 이 정령이 가장 오래 안 때린 적 (같으면 가까운 적). 벽 너머는 아님.
 * 적 = 적대 몹 · 싸운 몹 (spiritFoe): 지나가며 본 동물이나 건드리지 않은 중립 몹은 노리지 않음
 */
function spiritTarget(player, sp) {
  const R = sp.k.range;
  let aim;
  try {
    aim = aimTarget(player, R).target;
  } catch {
    aim = undefined;
  }
  if (aim?.isValid && canHit(player, aim) && spiritFoe(player, aim)) return aim;
  const head = player.getHeadLocation();
  const pl = player.location;
  const list = around(player, pl, R, 4).filter((e) => spiritFoe(player, e) && lineOfSight(player, head, bodyAt(e, head.y)));
  if (!list.length) return undefined;
  list.sort((a, b) => (sp.lastHit.get(a.id) ?? -1e9) - (sp.lastHit.get(b.id) ?? -1e9) || len(sub(a.location, pl)) - len(sub(b.location, pl)));
  return list[0];
}

/** 적 e 가 마지막으로 피해를 받은 틱: 바닐라 몹은 누구에게든 (무적 시간이 몹마다 하나), 보스는 이 플레이어의 스킬·룬 피해 (사람마다 따로 셈) */
function lastHurtOf(player, e) {
  return Math.max(hurtAt.get(e.id) ?? -1000, skillLandedAt(e, player));
}

/**
 * 정령 피해 한 번 (마력, 마법 피해): 몹 무적 시간 (10틱) 에 막히지 않게 — 다른 정령 피해 · 내 공격 · 다른 룬 · 불 피해에서 10틱 뒤로 미룸.
 * 들어갈 때 그 사이 다른 피해가 있었으면 다시 미룸 (3번까지). fn = 들어간 뒤.
 * sp = 때린 정령 (범위에 맞은 적도 "때린 적" 으로 적어 다음에는 다른 적부터)
 * @param {any} player
 * @param {any} e
 * @param {number} P
 * @param {number} mult
 * @param {(x: any) => void} [fn]
 * @param {{lastHit: Map<string, number>}} [sp]
 */
function spiritDamage(player, e, P, mult, fn, sp = undefined) {
  if (sp) sp.lastHit.set(e.id, system.currentTick);
  const now = system.currentTick;
  let m = spiritHitAt.get(player.id);
  if (!m) spiritHitAt.set(player.id, (m = new Map()));
  const at = Math.max(now, (m.get(e.id) ?? -100) + 10, lastHurtOf(player, e) + 10);
  m.set(e.id, at);
  const life = lifeToken(player);
  const retry = (t, free, tries) => {
    const mm = spiritHitAt.get(player.id);
    if (mm && (mm.get(e.id) ?? -100) < free) mm.set(e.id, free);
    later(free - t, () => land(tries + 1));
  };
  const land = (tries) => {
    if (!sameLife(player, life) || !e.isValid) return;
    const t = system.currentTick;
    const free = lastHurtOf(player, e) + 10;
    if (free > t && tries < 3) {
      retry(t, free, tries);
      return;
    }
    if (runeHit(player, e, P, mult, "int") > 0) {
      hurtAt.set(e.id, t); // 바로 적어 둠 (피해 알림은 이 틱의 타이머가 다 돈 뒤에 오므로, 같은 틱의 다른 정령 · 다른 사람의 정령이 보게)
      fn?.(e);
    } else if (tries < 3 && canHit(player, e)) {
      // 같은 틱에 먼저 들어간 다른 피해 (알림 전) 의 무적 시간에 막힘: 알림이 온 뒤 다시
      retry(t, Math.max(lastHurtOf(player, e) + 10, t + 1), tries);
    }
  };
  later(at - now, () => land(0));
}

/** 정령의 범위 공격에 맞는 적 (at 둘레 radius 칸, 위아래 2.5칸): 노려도 되는 적만 (곁의 동물·중립 몹은 다치지 않음) */
function spiritFoesAround(player, at, radius) {
  return around(player, at, radius, 2.5).filter((e) => spiritFoe(player, e));
}

/** 정령의 공격 한 번 (정령마다 다름: RUNE_SPIRITS) */
function spiritAttack(player, sp, tgt) {
  const dim = player.dimension;
  const d = sp.def;
  const [glow, core] = d.color;
  const from = { ...sp.pos };
  const now = system.currentTick;
  const life = lifeToken(player);
  const { P } = sp;
  const mult = sp.k.mult;
  sp.lastHit.set(tgt.id, now);
  if (sp.kind === "salamander") {
    // 불덩이: 날아가 폭발 (반지름 radius) + 불붙음
    const to = bodyPoint(tgt);
    const ticks = flyStreak(dim, from, to, d.speed, glow, core, 0.45, 0.42);
    sp.busyUntil = now + ticks;
    play(dim, "mob.blaze.shoot", from, 1.5, 0.5);
    later(ticks, () => {
      if (!sameLife(player, life)) return;
      const at = tgt.isValid ? bodyPoint(tgt) : to;
      const feet = tgt.isValid ? tgt.location : { x: at.x, y: at.y - 0.9, z: at.z };
      burst(dim, at, glow, 1.4, 0.3);
      fx(dim, "rpg:fire_wall", feet, { size: d.radius * 1.4, life: 0.6, color: glow, alpha: 0.95, spin: Math.random() * 360 });
      zonePulse(dim, feet, glow, d.radius, 0.35);
      sparks(dim, at, core, 6, 5);
      play(dim, "fire.ignite", at, 1.2, 0.8);
      for (const e of spiritFoesAround(player, at, d.radius)) spiritDamage(player, e, P, mult, (x) => burn(x, d.fire), sp);
    });
  } else if (sp.kind === "undine") {
    // 물줄기: 대상 둘레 반지름 radius 물보라 + 밀침 + 구속 I
    const to = bodyPoint(tgt);
    const L = len(sub(to, from));
    const u = unit(sub(to, from));
    const ticks = Math.max(1, Math.round((L / d.speed) * 20));
    sp.busyUntil = now + ticks;
    for (let s = 0.4; s < L; s += 0.8) {
      const delay = (s / L) * (ticks / 20);
      fx(dim, "rpg:arrow_trail", add(from, u, s), { dir: u, size: 0.48, thick: 0.18, life: delay + 0.3, delay, color: glow, alpha: 0.9 }); // 조각끼리 겹침 (0.96 > 0.8)
    }
    play(dim, "bucket.empty_water", from, 1.4, 0.7);
    later(ticks, () => {
      if (!sameLife(player, life)) return;
      const at = tgt.isValid ? bodyPoint(tgt) : to;
      fx(dim, "rpg:splash", { x: at.x, y: at.y - 0.9 + d.radius * 0.75, z: at.z }, { size: d.radius, life: 0.6, color: glow, alpha: 0.95 });
      for (let i = 0; i < 4; i++) {
        const a = (i / 4) * Math.PI * 2 + Math.random();
        fx(dim, "rpg:drop", up(at, 0.3), { dir: unit({ x: Math.cos(a), y: 1.4, z: Math.sin(a) }), speed: 4, size: 0.35, life: 0.5, color: core, alpha: 0.95 });
      }
      play(dim, "random.splash", at, 1.2, 0.6);
      for (const e of spiritFoesAround(player, at, d.radius)) {
        spiritDamage(
          player,
          e,
          P,
          mult,
          (x) => {
            knock(x, sub(x.location, player.location), d.knock, 0.2);
            effect(x, "slowness", d.slowTicks, 0);
          },
          sp,
        );
      }
    });
  } else if (sp.kind === "sylph") {
    // 돌진: 정령이 대상을 꿰뚫고 pierce 칸 너머까지 날아감 (폭 width 의 적 모두) + 띄움, 끝나면 자리로 돌아옴
    const to = bodyPoint(tgt);
    const u = unit(sub(to, from));
    const L = len(sub(to, from)) + rayLength(dim, to, u, d.pierce);
    const ticks = Math.max(2, Math.round((L / d.speed) * 20));
    sp.dash = { from, u, L, t0: now, T: ticks };
    sp.busyUntil = now + ticks + 6;
    flyStreak(dim, from, add(from, u, L), d.speed, glow, core, 0.8, 0.3);
    play(dim, "mob.breeze.shoot", from, 1.3, 0.7);
    play(dim, "mob.breeze.whirl", to, 1.6, 0.5);
    for (const { e, t } of lineTargets(player, from, u, L, d.width)) {
      if (!spiritFoe(player, e)) continue;
      later(Math.max(1, Math.round((t / d.speed) * 20)), () => {
        if (!sameLife(player, life) || !e.isValid) return;
        const at = bodyPoint(e);
        arc(dim, at, u, { shape: "thin", flip: (t | 0) % 2 === 1, size: 1.2, life: 0.2, speed: 0, spin: 40, color: glow, core: false });
        spiritDamage(player, e, P, mult, (x) => knock(x, u, 0.3, 0.35), sp);
      });
    }
  } else if (sp.kind === "gnome") {
    // 바위 가시: 대상 발밑에 warn 틱 예고 → 반지름 radius 가시 + 구속 II
    const g = { ...tgt.location };
    sp.busyUntil = now + d.warn + 4;
    zonePulse(dim, g, glow, d.radius, d.warn / 20);
    floorRune(dim, g, { size: d.radius * 0.8, life: d.warn / 20 + 0.1, color: glow, alpha: 0.85, rate: 200, progress: 1, grow: 0 });
    fx(dim, "rpg:arrow_shot", from, { dir: unit(sub(g, from)), speed: len(sub(g, from)) / Math.max(0.05, d.warn / 20), size: 0.4, thick: 0.4, life: d.warn / 20, color: glow, alpha: 1 }); // 정령이 던지는 흙덩이
    play(dim, "dig.stone", g, 0.8, 0.8);
    later(d.warn, () => {
      if (!sameLife(player, life)) return;
      for (let i = 0; i < 6; i++) {
        const a = (i / 6) * Math.PI * 2;
        const rr = i === 0 ? 0 : d.radius * 0.55;
        fx(dim, "rpg:ice_spike", { x: g.x + Math.cos(a) * rr, y: g.y, z: g.z + Math.sin(a) * rr }, { size: 1.5 + (i % 2) * 0.4, life: 0.8, spin: Math.random() * 30 - 15, color: [0.66, 0.58, 0.5], alpha: 1 }); // 돌 가시 (얼음 가시 그림을 돌 색으로)
      }
      fx(dim, "rpg:ground_crack", { x: g.x, y: g.y + 0.05, z: g.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: d.radius * 1.6, life: 1.2, color: [0.3, 0.2, 0.1], alpha: 0.9 });
      for (let i = 0; i < 2; i++) {
        const a = Math.random() * Math.PI * 2;
        fx(dim, "rpg:boulder", up(g, 0.5), { dir: unit({ x: Math.cos(a) * 0.5, y: 1, z: Math.sin(a) * 0.5 }), speed: 6, size: 0.35, life: 0.7, spin: Math.random() * 360, color: [0.75, 0.58, 0.4], alpha: 1 });
      }
      fx(dim, "rpg:dust", { x: g.x, y: g.y + d.radius * 0.35, z: g.z }, { size: d.radius, life: 0.8, color: [0.62, 0.5, 0.38], alpha: 0.8 });
      play(dim, "mace.heavy_smash_ground", g, 0.9, 0.9);
      for (const e of spiritFoesAround(player, g, d.radius)) {
        spiritDamage(
          player,
          e,
          P,
          mult,
          (x) => {
            effect(x, "slowness", d.slowTicks, 1);
            knock(x, V_UP, 0, 0.3);
          },
          sp,
        );
      }
    });
  }
}

/** 정령왕 (정령왕의 부름): 앞쪽 하늘에 왕관을 쓴 정령왕 (rpg:rfx_king) 이 팔을 들어 네 정령을 부름. 그 자리 (정령에게 가는 빛줄기의 시작) */
function spiritKing(player) {
  const dim = player.dimension;
  const f = facingOf(player);
  const at = up(add(player.location, f, 5.4), 3.5);
  rfx(dim, "king", at, { size: 2.2, life: 1.6, grow: 0.1, alpha: 0.9, frames: 2, fps: 4, loop: true, fade: 0.35 });
  fx(dim, "rpg:magic_circle_flash", add(at, f, 0.4), { size: 2.6, life: 0.8, spin: Math.random() * 360, progress: 1, color: [1.0, 0.92, 0.66], alpha: 0.75 });
  for (let i = 0; i < 4; i++) {
    const kind = ["salamander", "undine", "sylph", "gnome"][i];
    const a = (i / 4) * Math.PI * 2 + 0.6;
    fx(dim, "rpg:arrow_spark", add(at, { x: Math.cos(a) * 1.6, y: Math.sin(a) * 1.2, z: 0 }, 1), { dir: V_UP, speed: 0.8, size: 0.5, grow: 1.4, life: 0.9, color: RUNE_SPIRITS[kind].color[0], alpha: 0.9 });
  }
  play(dim, "beacon.activate", at, 1.2, 0.8);
  play(dim, "random.totem", player.location, 1.5, 0.5);
  return at;
}

/** 정령 그리기 (2틱마다 부름): 4틱마다 정령 그림 (rpg:rfx_spirit — 샐러맨더 · 운디네 · 실프 · 노움, 4장이 이어 돎) + 사이 틱에 작은 빛 */
function drawSpirit(dim, sp, now) {
  const [, core] = sp.def.color;
  const p = sp.pos;
  if (((now >> 1) & 1) === 1) {
    fx(dim, "rpg:arrow_spark", up(p, -0.3), { dir: V_DOWN, speed: 0.6, size: 0.1, grow: 0.5, life: 0.3, color: core, alpha: 0.9 });
    return;
  }
  rfx(dim, "spirit", p, { size: 0.36, life: 0.25, cell: RFX.spirit[sp.kind] ?? 0, frames: 4, fps: 8, loop: true, phase: ((now / 20) * 8) % 4 });
}

/** 정령이 떠남 (시간이 다 됨 · 공격을 다 함): 빛이 흩어짐 */
function spiritLeave(dim, sp) {
  const [glow, core] = sp.def.color;
  burst(dim, sp.pos, glow, 0.5, 0.3);
  fx(dim, "rpg:arrow_spark", sp.pos, { dir: V_UP, speed: 2, size: 0.2, grow: 0.5, life: 0.5, color: core, alpha: 1 });
  play(dim, "mob.allay.item_given", sp.pos, 1.3, 0.5);
}

/** 정령 움직이기 · 그리기 · 공격 (2틱마다) */
function spiritTick() {
  const now = system.currentTick;
  for (const [id, t] of hurtAt) if (now - t > 20) hurtAt.delete(id);
  for (const [pid, m] of spiritHitAt) {
    for (const [id, t] of m) if (now - t > 40) m.delete(id);
    if (!m.size) spiritHitAt.delete(pid);
  }
  if (!spirits.size) return;
  for (const [pid, list] of spirits) {
    const player = list[0]?.owner;
    let dim;
    try {
      dim = player?.isValid ? player.dimension : undefined;
    } catch {
      dim = undefined;
    }
    if (!dim) {
      spirits.delete(pid);
      continue;
    }
    for (let i = list.length - 1; i >= 0; i--) {
      const sp = list[i];
      if (!sameLife(player, sp.token)) {
        // 죽음 · 차원 이동 (이 틱이 이동 알림보다 먼저 봄) · 탈것: 있던 차원에서 흩어짐
        try {
          burst(sp.dim, sp.pos, sp.def.color[0], 0.5, 0.25);
        } catch {
          // 무시
        }
        list.splice(i, 1);
        continue;
      }
      sp.dim = dim; // 차원 이동으로 흩어질 때 떠난 쪽에 그리려고 (삶 표시가 같을 때만 = 같은 차원)
      if (now >= sp.until || (sp.left <= 0 && now >= sp.busyUntil + 8)) {
        spiritLeave(dim, sp);
        list.splice(i, 1);
        continue;
      }
      // 나와 같이 움직임 (걷기 · 겉날개 · 순간 이동 모두 같은 만큼 옮긴 뒤 자리로 다가감 — 멀리 처져 옛 자리에서 공격하지 않게)
      const eye = player.getHeadLocation();
      const moved = sub(eye, sp.eye ?? eye);
      sp.eye = eye;
      if (sp.dash && len(moved) > SPIRIT_TELEPORT) sp.dash = undefined; // 순간 이동: 돌진을 멈추고 같이 옮김
      if (sp.dash) {
        const q = (now - sp.dash.t0) / sp.dash.T;
        if (q >= 1) sp.dash = undefined;
        else sp.pos = add(sp.dash.from, sp.dash.u, sp.dash.L * q);
      } else sp.pos = add(sp.pos, moved);
      if (!sp.dash) {
        const want = spiritSlot(player, sp.slot, now, dim);
        sp.pos = add(sp.pos, sub(want, sp.pos), 0.45);
      }
      drawSpirit(dim, sp, now);
      if (sp.left > 0 && now >= sp.nextAt && now >= sp.busyUntil) {
        const tgt = spiritTarget(player, sp);
        if (tgt) {
          sp.left--;
          sp.nextAt = now + sp.k.gap;
          spiritAttack(player, sp, tgt);
        }
      }
    }
    if (!list.length) spirits.delete(pid);
  }
}

/** 지금 정령 (테스트·상태 줄) */
export function spiritsOf(player) {
  return spirits.get(player.id) ?? [];
}

/** 화면 하단 상태 줄의 정령 (룬·초월 무기 줄 뒤에 붙음) */
function spiritHud(player) {
  const list = spirits.get(player.id);
  if (!list?.length) return "";
  const now = system.currentTick;
  const left = Math.max(...list.map((x) => x.until - now));
  return `§a정령 ${list.length} §7${Math.max(0, left / 20).toFixed(1)}초`;
}

/** 죽음 · 차원 이동 · 탈것: 정령이 흩어짐 */
function dismissSpirits(playerId) {
  const list = spirits.get(playerId);
  if (!list) return;
  spirits.delete(playerId);
  for (const sp of list) {
    try {
      burst(sp.dim, sp.pos, sp.def.color[0], 0.5, 0.25); // 정령이 있던 차원 (차원 이동이면 떠난 쪽)
    } catch {
      // 무시
    }
  }
}

// ============================================================ v2.15.0 중력 (자줏빛 + 어두운 핵: 끌어모아 터뜨림 · 띄우고 내리꽂기)
// 사용자가 고른 느낌: "띄우고 내리꽂거나 끌어모아서 터트리는 느낌의 룬". 끌기 · 띄우기는 게임 물리 (applyImpulse · 공중 부양 효과) 라 벽을 뚫지 않음
/** 띄운 (곧 떨어질) 몹 → 바닐라 낙하 피해를 막는 마지막 틱 @type {Map<string, number>} */
const lifted = new Map();
const HEAVY = new Set(RUNE_GRAVITY.heavy);
const GRAVITY_VOID = RUNE_GRAVITY.void;
const GRAVITY_ROCK = [0.52, 0.44, 0.5];
const GRAVITY_CRACK = [0.22, 0.1, 0.3];

/** 끌거나 띄울 수 있는 몹: 보스 · 보스 소품 · 아주 큰 몹 · 플레이어는 아님 (피해 · 효과만) */
export function gravityMovable(e) {
  try {
    if (!e?.isValid || e.typeId === "minecraft:player" || HEAVY.has(e.typeId)) return false;
    const t = e.getComponent("minecraft:type_family");
    return !(t?.hasTypeFamily("rpg_boss") || t?.hasTypeFamily("rpg_boss_prop"));
  } catch {
    return false;
  }
}

/** 훈련용 허수아비 (스크립트가 맞을 때마다 제자리로 옮기므로 끌지 않음 — 띄우기만) */
function isDummy(e) {
  try {
    return !!e.getComponent("minecraft:type_family")?.hasTypeFamily("rpg_dummy");
  } catch {
    return false;
  }
}

/** 끌어당김 한 번 (끌 수 있는 몹만, 허수아비 제외): c 쪽 가로로, 거리 × pullK (pullMax 까지). 가운데 pullStop 칸 안은 멈춤 (그 몹의 속도를 지움) */
function pullToward(e, c) {
  if (!gravityMovable(e) || isDummy(e)) return;
  const G = RUNE_GRAVITY;
  const dx = c.x - e.location.x;
  const dz = c.z - e.location.z;
  const dist = Math.hypot(dx, dz);
  const s = dist < G.pullStop ? 0 : Math.min(G.pullMax, dist * G.pullK);
  try {
    e.clearVelocity();
    if (s > 0) e.applyImpulse({ x: (dx / dist) * s, y: 0, z: (dz / dist) * s });
  } catch {
    if (s > 0) knock(e, { x: dx, y: 0, z: dz }, s, 0); // applyImpulse 를 못 쓰는 것
  }
}

/** 띄움: 공중 부양 ticks 틱 (amp) + hold 틱 동안 공격 못 함 (기절과 같은 효과) · 떨어질 때 낙하 피해 없음. 띄웠으면 true */
function liftUp(e, ticks, amp, hold) {
  if (!gravityMovable(e)) return false;
  effect(e, "levitation", ticks, amp);
  effect(e, "slowness", hold, 10);
  effect(e, "weakness", hold, 4);
  lifted.set(e.id, Math.max(lifted.get(e.id) ?? 0, system.currentTick + hold + 60));
  return true;
}

/** 땅에 닿아 있는지 (모르면 닿은 것으로) */
function onGround(e) {
  try {
    return e.isOnGround !== false;
  } catch {
    return true;
  }
}

/** 내리꽂음: 공중 부양을 지우고 아래로 slamPower → 땅에 닿으면 (landWait 틱까지 기다림) fn. 끌 수 없는 몹 (보스) 은 바로 fn */
function slamDown(player, e, life, fn) {
  if (gravityMovable(e)) {
    try {
      e.removeEffect("levitation");
    } catch {
      // 무시
    }
    try {
      e.clearVelocity();
      e.applyImpulse({ x: 0, y: -RUNE_GRAVITY.slamPower, z: 0 });
    } catch {
      knock(e, V_UP, 0, -RUNE_GRAVITY.slamPower * 0.6);
    }
    lifted.set(e.id, Math.max(lifted.get(e.id) ?? 0, system.currentTick + 60));
  }
  let n = 0;
  const wait = () => {
    if (!sameLife(player, life) || !e.isValid) return;
    if (n > 0 && (n >= RUNE_GRAVITY.landWait || onGround(e))) {
      fn(e);
      return;
    }
    if (n === 0 && !gravityMovable(e)) {
      fn(e);
      return;
    }
    n++;
    later(1, wait);
  };
  wait();
}

/** before 이벤트: 중력 룬으로 띄운 몹의 낙하 피해를 막음 (내리꽂기 피해는 룬이 따로 넣음) */
function noFallAfterLift(ev) {
  if (!lifted.size || ev.damageSource?.cause !== EntityDamageCause.fall) return;
  const id = ev.hurtEntity?.id;
  const until = id === undefined ? undefined : lifted.get(id);
  if (until === undefined) return;
  if (system.currentTick <= until) ev.cancel = true;
}

/** 오래된 띄움 기록 지우기 */
function gravityTick() {
  const now = system.currentTick;
  for (const [id, t] of lifted) if (now > t) lifted.delete(id);
}

/**
 * 눈에서 가까울수록 작게 (1인칭 화면을 덮지 않게): 눈에서 at 까지 1칸 → 0.3배, 6칸 이상 → 1배
 * @param {any} player
 * @param {{x: number, y: number, z: number}} at
 */
function nearScale(player, at) {
  try {
    return Math.max(0.3, Math.min(1, (len(sub(at, player.getHeadLocation())) - 1) / 5));
  } catch {
    return 1;
  }
}

/** 수평으로 min 칸보다 가까운 조준점은 보는 방향으로 min 칸까지 밀어냄 (가까이에 쓴 중력장 · 블랙홀이 화면을 덮지 않게) */
function aimAtLeast(player, g, min) {
  const pl = player.location;
  if (Math.hypot(g.x - pl.x, g.z - pl.z) >= min) return g;
  const p = add(pl, flat(player.getViewDirection()), min);
  return { x: p.x, y: groundAt(player.dimension, up(p, 0.5), pl.y), z: p.z };
}

/** 가운데 c 로 빨려드는 빛 알갱이 n 개 (반지름 R 에서 life 초 동안) */
function inwardSparks(dim, c, R, n, life, color, hSpread = 1.2) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2;
    const from = { x: c.x + Math.cos(a) * R, y: c.y + (Math.random() - 0.4) * hSpread, z: c.z + Math.sin(a) * R };
    const d = sub(c, from);
    fx(dim, "rpg:arrow_spark", from, { dir: unit(d), speed: len(d) / life, size: 0.15, grow: 0.3, life, color, alpha: 1 });
  }
}

/** 끌려 오는 돌 조각: 반지름 R 의 한 점에서 c 쪽으로 조금 돌며 (life 초) */
function inwardRock(dim, c, R, life, low = 0.6) {
  const a = Math.random() * Math.PI * 2;
  const from = { x: c.x + Math.cos(a) * R, y: c.y - low + Math.random() * 0.6, z: c.z + Math.sin(a) * R };
  const d = sub(c, from);
  const dir = unit(add(d, { x: -Math.sin(a), y: 0, z: Math.cos(a) }, R * 0.22));
  fx(dim, "rpg:boulder", from, { dir, speed: len(d) / life, size: 0.18 + Math.random() * 0.12, life, spin: Math.random() * 360, color: GRAVITY_ROCK, alpha: 1 });
}

/** 땅에서 천천히 솟는 돌 조각 n 개 (feet 둘레 spread 칸) */
function risingRocks(dim, feet, n, spread, speed, life) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2;
    const rr = Math.random() * spread;
    fx(dim, "rpg:boulder", { x: feet.x + Math.cos(a) * rr, y: feet.y + 0.2, z: feet.z + Math.sin(a) * rr }, { dir: V_UP, speed, size: 0.18 + Math.random() * 0.14, life, spin: Math.random() * 360, color: GRAVITY_ROCK, alpha: 1 });
  }
}

/** 띄운 적: 발밑 고리 + 솟는 빛기둥 + 함께 솟는 돌 */
function liftFx(dim, e) {
  const [glow] = RUNE.color.gravity;
  const feet = e.location;
  floorRune(dim, feet, { size: 0.9, life: 0.8, color: glow, alpha: 0.9, rate: 160, grow: 0.3 });
  fx(dim, "rpg:arrow_shot", up(feet, 0.6), { dir: V_UP, speed: 5, size: 1.0, thick: 0.35, life: 0.45, color: glow, alpha: 0.85 });
  risingRocks(dim, feet, 2, 0.8, 2.6, 0.9);
}

/** 떠 있는 적 둘레의 자줏빛 (4틱마다 부름) */
function floatAura(dim, e) {
  if (!e.isValid) return;
  const [glow] = RUNE.color.gravity;
  fx(dim, "rpg:arrow_spark", bodyPoint(e), { dir: V_UP, speed: 0.6, size: 0.7, grow: 1.2, life: 0.25, color: glow, alpha: 0.5 });
}

/** 위에서 내리꽂는 빛줄기 (at = 몸 가운데, h 칸 위에서) */
function slamStreak(dim, at, h = 3.5) {
  const [glow, core] = RUNE.color.gravity;
  const top = up(at, h);
  fx(dim, "rpg:arrow_shot", top, { dir: V_DOWN, speed: h / 0.12, size: 1.1, thick: 0.55, life: 0.12, color: glow, alpha: 1 });
  fx(dim, "rpg:arrow_shot", top, { dir: V_DOWN, speed: h / 0.12, size: 0.8, thick: 0.2, life: 0.12, color: core, alpha: 1 });
}

/** 작은 블랙홀 (rpg:rfx_hole — 도는 빛 원반 + 어두운 핵): size · life 초 (o.grow = 커짐 · 음수면 꺼짐, o.spin 이어 돌기) */
function miniHole(dim, at, size, life, o = {}) {
  rfx(dim, "hole", at, { size, life, cell: 0, spin: o.spin ?? Math.random() * 360, spinRate: o.spinRate ?? 300, alpha: o.alpha ?? 1, grow: o.grow ?? 0, fade: o.fade ?? 0 });
  if (!o.noCore) rfx(dim, "hole", at, { size, life, cell: RFX.holeCore, grow: o.grow ?? 0, fade: o.fade ?? 0, alpha: o.alpha ?? 1 });
}

/**
 * 내리꽂힌 자리: 충격 고리 · 땅의 금 · 먼지 · 튀는 돌 (size = 반지름) — v2.25.0 n = 같은 틱에 내리꽂힌 몇 번째 적 (파티클 예산):
 * 0~1 번째는 둘레에 자줏빛 바위 가시 셋 (rpg:rfx_rock, player 가 있을 때), 5~11 번째는 번쩍임만, 12 번째부터는 그리지 않음
 */
function slamFx(dim, feet, size, player = undefined, n = 0) {
  const [glow, core] = RUNE.color.gravity;
  if (n >= 5) {
    if (n < 12) burst(dim, up(feet, 0.4), core, size * 0.6, 0.25);
    return;
  }
  zonePulse(dim, feet, glow, size, 0.4);
  fx(dim, "rpg:ground_crack", { x: feet.x, y: feet.y + 0.05, z: feet.z }, { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: size * 1.3, life: 1.0, color: GRAVITY_CRACK, alpha: 0.9 });
  fx(dim, "rpg:dust", { x: feet.x, y: feet.y + size * 0.3, z: feet.z }, { size: size * 0.9, life: 0.7, color: [0.42, 0.32, 0.46], alpha: 0.75 });
  burst(dim, up(feet, 0.4), core, size * 0.6, 0.25);
  if (n < 2 && player?.isValid) rockRing(player, dim, feet, size * 0.7, 3, 0.45, glow, { rock: GRAVITY_ROCK, life: 0.8, minDist: 1.6 });
  for (let i = 0; i < 2; i++) {
    const a = Math.random() * Math.PI * 2;
    fx(dim, "rpg:boulder", up(feet, 0.3), { dir: unit({ x: Math.cos(a) * 0.6, y: 1, z: Math.sin(a) * 0.6 }), speed: 5, size: 0.22, life: 0.6, spin: Math.random() * 360, color: GRAVITY_ROCK, alpha: 1 });
  }
}

/** 안으로 꺼졌다 터지는 붕괴 (핵 at, 땅 g, 반지름 R, 크기 big 0~1, sc = 눈에서 가까우면 작게) */
function collapseFx(dim, at, g, R, big, sc = 1) {
  const [glow, core] = RUNE.color.gravity;
  fx(dim, "rpg:arrow_spark", at, { dir: V_UP, speed: 0, size: (2.2 + big * 2) * sc, grow: 0.08, life: 0.16, color: WHITE, alpha: 1 }); // 안으로 꺼지는 번쩍임
  burst(dim, at, glow, R * 0.8 * sc, 0.4);
  burst(dim, at, core, R * 0.4 * sc, 0.3);
  fx(dim, "rpg:dust", up(g, R * 0.3 * sc), { size: R * 0.75 * sc, life: 0.8, color: GRAVITY_VOID, alpha: 0.8 * sc });
  const rings = big > 0.5 ? 4 : 3;
  for (let i = 0; i < rings; i++) later(i * 2, () => zonePulse(dim, g, i % 2 ? core : glow, R * (0.55 + (0.45 * (i + 1)) / rings), 0.45 + i * 0.05));
  fx(dim, "rpg:ground_crack", up(g, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: R * 1.2, life: 1.2, color: GRAVITY_CRACK, alpha: 0.9 });
  const n = big > 0.5 ? 8 : 4;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + Math.random() * 0.4;
    fx(dim, "rpg:boulder", up(g, 0.6), { dir: unit({ x: Math.cos(a), y: 0.9, z: Math.sin(a) }), speed: 6 + big * 4, size: 0.26, life: 0.7, spin: Math.random() * 360, color: GRAVITY_ROCK, alpha: 1 });
  }
  if (big > 0.5) {
    for (let i = 0; i < 10; i++) {
      const dd = turn({ x: 1, y: 0, z: 0 }, i * 36 + Math.random() * 12);
      fx(dim, "rpg:light_cut", add(up(g, 0.08), dd, R * 0.35), { dir: dd, size: R * 0.3, thick: 0.35, life: 1.1, delay: 0.6, color: glow, alpha: 0.9 });
    }
  }
  sparks(dim, at, core, 10 + Math.round(big * 8), 6 + big * 2);
}

/**
 * 중력탄: 조준한 몹 (없으면 조준한 곳) 으로 어두운 구체 → 도착하면 pull 칸 안을 pullTicks 틱 끌어당긴 뒤 터짐 (radius 칸, 위력 × mult) —
 * v2.25.0 날아가는 작은 블랙홀 (rpg:rfx_hole) → 도착한 자리에서 커지며 빨아들이다가 안으로 꺼지며 터짐
 */
function gravityOrb(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.gravity;
  const target = pickTarget(player, k.range, 20);
  const { hand } = handOf(player);
  const to0 = target ? bodyPoint(target) : up(groundAim(player, k.range), 1.0);
  const ticks = Math.max(1, Math.round((len(sub(to0, hand)) / k.speed) * 20));
  const life = lifeToken(player);
  fx(dim, "rpg:magic_circle_flash", hand, { size: 0.16, life: 0.22, spin: Math.random() * 360, color: glow, alpha: 1 }); // 손 앞 작은 마법진 (1인칭 화면을 가리지 않게)
  flyStreak(dim, hand, to0, k.speed, glow, core, 0.45, 0.16);
  for (let t = 2; t <= ticks; t += 2) later(t, () => miniHole(dim, add(hand, sub(to0, hand), Math.min(1, t / ticks)), 0.32, 0.14, { spin: t * 15 })); // 손 바로 앞에는 그리지 않음
  play(dim, "mob.shulker.shoot", hand, 1.2, 0.6);
  later(ticks, () => {
    if (!sameLife(player, life)) return;
    const c = target?.isValid && len(sub(bodyPoint(target), to0)) < 3 ? bodyPoint(target) : to0;
    const floor = { x: c.x, y: groundAt(dim, c, c.y - 1), z: c.z };
    const sc = nearScale(player, c);
    floorRune(dim, floor, { size: k.pull, life: k.pullTicks / 20 + 0.05, color: glow, alpha: 0.85, rate: -260, grow: -0.8 }); // 줄어드는 고리
    inwardSparks(dim, c, k.pull, 8, k.pullTicks / 20, core);
    // 커지는 블랙홀 (2틱마다 다시 그림: 0.4 → 0.9)
    for (let t = 0; t < k.pullTicks; t += 2) later(t, () => sameLife(player, life) && miniHole(dim, c, (0.4 + (0.5 * (t + 2)) / k.pullTicks) * sc, 0.14, { spin: t * 20, spinRate: 400 }));
    play(dim, "mob.endermen.portal", c, 1.1, 0.9);
    for (let t = 0; t < k.pullTicks; t += RUNE_GRAVITY.pullEvery) {
      later(t, () => {
        if (!sameLife(player, life)) return;
        for (const e of around(player, c, k.pull, 2.5)) pullToward(e, c);
      });
    }
    later(k.pullTicks, () => {
      if (!sameLife(player, life)) return;
      miniHole(dim, c, 1.0 * sc, 0.25, { grow: -0.9, spinRate: 900, noCore: true }); // 안으로 꺼짐
      collapseFx(dim, c, floor, k.radius, 0, sc);
      play(dim, "random.explode", c, 0.7, 1.5);
      play(dim, "mob.shulker.bullet.hit", c, 1.2, 0.7);
      for (const e of around(player, c, k.radius, 2.5)) {
        if (runeHit(player, e, P, k.mult, r.stat) > 0) knock(e, V_UP, 0, 0.25);
      }
    });
  });
  return true;
}

/**
 * 공중 부양: 앞 cone 도 · range 칸의 적에게 위력 × mult + 띄움 (lift 틱, 그 동안 공격 못 함) → gap 틱 뒤 내리꽂음 (땅에 닿을 때 위력 × mult) —
 * v2.25.0 띄운 적의 발밑에 자줏빛 회오리 (rpg:rfx_vortex) 가 돌고 돌이 함께 떠오름, 내리꽂힌 자리에 바위 가시
 */
function liftSlam(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.gravity;
  const f = flat(player.getViewDirection());
  const life = lifeToken(player);
  // 앞 땅을 따라 번지는 자줏빛 물결 (1인칭 화면 아래쪽)
  for (let i = 0; i < 5; i++) {
    const d = turn(f, -k.cone / 2 + (k.cone * i) / 4);
    fx(dim, "rpg:light_cut", up(add(player.location, d, 2.6 + (i % 2) * 0.8), 0.1), { dir: rightOf(d), size: 1.1, thick: 0.22, life: 0.5, delay: Math.abs(i - 2) * 0.03, color: i % 2 ? core : glow, alpha: 0.9 });
  }
  play(dim, "mob.breeze.charge", player.location, 1.2, 0.8);
  play(dim, "mob.evocation_illager.cast_spell", player.location, 0.9, 1.2);
  const hitList = [];
  const floating = [];
  for (const e of coneTargets(player, k.range, k.cone, 10)) {
    runeHit(player, e, P, k.mult, r.stat);
    if (!canHit(player, e)) continue; // 쓰러진 적은 빼고 (첫 피해가 무적 시간에 막혀도 띄우고 내리꽂음)
    hitList.push(e);
    if (liftUp(e, k.lift, k.liftAmp, k.gap + RUNE_GRAVITY.landWait)) {
      floating.push(e);
      if (floating.length <= 8) liftFx(dim, e); // 8명까지 (파티클 예산)
    }
  }
  // 떠 있는 동안: 발밑 회오리 (땅에 붙음) + 둘레의 자줏빛 (4틱마다 — 앞 원뿔의 10명까지라 파티클은 4틱에 20개 안)
  const spots = floating.map((e) => ({ e, g: { ...e.location } }));
  for (let t = 0; t < k.gap; t += 4) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      for (const s of spots) {
        if (!s.e.isValid) continue;
        vortexAt(dim, s.g, 0.75 + bodyPad(s.e) * 0.5, glow, { life: 0.24, alpha: 0.75, fps: 24, fade: t + 4 >= k.gap ? 0.5 : 0 });
        if (t > 0) floatAura(dim, s.e);
      }
    });
  }
  later(k.gap, () => {
    if (!sameLife(player, life)) return;
    const alive = hitList.filter((e) => e.isValid);
    if (alive.length) {
      play(dim, "mace.smash_air", player.location, 1.0, 0.9);
      shakeAround(dim, player.location, 8, 0.2, 0.25);
    }
    const landed = new Map(); // 틱 → 그 틱에 내리꽂힌 수 (파티클 예산)
    for (const e of alive) {
      slamStreak(dim, bodyPoint(e));
      slamDown(player, e, life, (x) => {
        const now = system.currentTick;
        const n = landed.get(now) ?? 0;
        landed.set(now, n + 1);
        slamFx(dim, x.location, 1.4, player, n);
        play(dim, "mace.heavy_smash_ground", x.location, 0.9, 0.9);
        runeHit(player, x, P, k.mult, r.stat);
      });
    }
  });
  return true;
}

/**
 * 중력장: 조준한 곳 (range 칸) 에 hits × gap 틱 — pull 칸 안을 끌어모으며 gap 틱마다 radius 칸 위력 × mult + 구속 → 붕괴 (radius 칸 위력 × crush) —
 * v2.25.0 가운데 블랙홀 (rpg:rfx_hole) 이 자라며 돌고, 끝에 안으로 꺼지며 붕괴
 */
function gravityWell(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.gravity;
  const g = aimAtLeast(player, groundAim(player, k.range), k.minDist);
  const hole = up(g, 1.3);
  const T = k.hits * k.gap;
  const life = lifeToken(player);
  const sc = nearScale(player, hole);
  play(dim, "mob.warden.sonic_charge", g, 0.9, 1.3);
  play(dim, "portal.trigger", g, 0.6, 1.4);
  for (let t = 0; t < T; t += 2) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      if (t % 6 === 0) fx(dim, "rpg:whirlpool", up(g, 0.1), { dir: { x: 0, y: 0, z: 0 }, size: k.radius, life: 0.45, spin: t * 12, speed: -260, color: glow, alpha: 0.7 });
      if (t % 4 === 0) miniHole(dim, hole, (0.7 + (t / T) * 0.6) * sc, 0.24, { spin: (t * 15) % 360, spinRate: 300 });
      inwardSparks(dim, hole, k.radius, 2, 0.5, t % 4 ? core : glow, 1.6);
      if (t % 10 === 0) floorRune(dim, g, { size: k.radius, life: 0.5, color: glow, alpha: 0.8, rate: -200, grow: -0.85 });
      if (t % 8 === 4) inwardRock(dim, hole, k.radius, 0.55, 1.0);
    });
  }
  for (let t = 0; t < T; t += RUNE_GRAVITY.pullEvery) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      for (const e of around(player, g, k.pull, 3)) pullToward(e, g);
    });
  }
  every(k.hits, k.gap, (i) => {
    if (!sameLife(player, life)) return;
    if (i % 2 === 0) play(dim, "mob.endermen.portal", g, 0.8, 0.6 + i * 0.1);
    for (const e of around(player, g, k.radius, 3)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) effect(e, "slowness", k.gap + 4, k.slowAmp);
    }
  });
  later(T, () => {
    if (!sameLife(player, life)) return;
    miniHole(dim, hole, 1.5 * sc, 0.3, { grow: -0.9, spinRate: 900, noCore: true }); // 안으로 꺼짐
    collapseFx(dim, hole, g, k.radius, 0.4, sc);
    play(dim, "random.explode", g, 0.8, 1.2);
    play(dim, "mob.warden.sonic_boom", g, 0.6, 1.3);
    shakeAround(dim, g, 12, 0.3, 0.3);
    for (const e of around(player, g, k.radius, 3)) {
      if (runeHit(player, e, P, k.crush, r.stat) > 0) knock(e, V_UP, 0, 0.35);
    }
  });
  return true;
}

/**
 * 중력 역전: 내 주위 radius 칸의 적에게 위력 × mult + 모두 띄움 (lift 틱, 공격 못 함) → gap 틱 뒤 한꺼번에 내리꽂음 (땅에 닿을 때 위력 × crush + 구속) —
 * v2.25.0 앞 반원에 자줏빛 바위 가시가 솟고 돌이 떠오름, 머리 위 4.6칸에 거꾸로 된 마법진 (돌아가는 소용돌이) + 앞 위 하늘의 중력 문장 (1인칭) → 하늘에서 적마다 빛줄기가 내리꽂힘
 */
function gravityReverse(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.gravity;
  const c = { ...player.location };
  const f = flat(player.getViewDirection());
  const life = lifeToken(player);
  const sky = 4.6;
  // 1인칭에서도 보이는 중력 문장: 앞 3.2칸 · 위 3.3칸 (화면을 보는 그림 — 하늘의 마법진은 1인칭에서 보이지 않으므로)
  const sigil = up(add(c, f, 3.2), 3.3);
  zonePulse(dim, c, glow, k.radius, 0.5);
  floorRune(dim, c, { size: k.radius, life: (k.gap + 4) / 20, color: glow, alpha: 0.85, rate: 120, grow: 0 });
  // 앞 반원에서 솟는 바위 가시 · 떠오르는 돌 (1인칭 화면 — 발밑 룬은 화면 밖)
  for (let i = 0; i < 7; i++) {
    const d = turn(f, (i / 6 - 0.5) * 160);
    const p = add(c, d, 2.3 + (i % 3) * 1.0);
    rockSpike(player, dim, p, 0.5 + (i % 3) * 0.1, glow, { rock: GRAVITY_ROCK, life: k.gap / 20, fade: 0.25 });
    if (i % 2 === 0) fx(dim, "rpg:boulder", up(p, 0.2), { dir: V_UP, speed: 2.4, size: 0.24, life: k.gap / 20, spin: Math.random() * 360, color: GRAVITY_ROCK, alpha: 1 });
  }
  // 하늘의 거꾸로 된 마법진 (gap 동안 4틱마다 — 내리꽂을 때 번쩍)
  for (let t = 0; t < k.gap; t += 4) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      floorRune(dim, up(c, sky), { size: k.radius * 0.75, life: 0.24, color: glow, alpha: 0.85, progress: Math.min(1, (t + 4) / (k.gap * 0.6)), rate: 0, spin: -t * 6 });
      rfx2(dim, "sun", sigil, { size: (0.8 + (0.9 * (t + 4)) / k.gap) * nearK(player, sigil, 3.5, 0.5), life: 0.24, spin: -t * 9, alpha: 0.85 }, glow, core);
      if (t % 8 === 0) fx(dim, "rpg:whirlpool", up(c, sky - 0.1), { dir: { x: 0, y: 0, z: 0 }, size: k.radius * 0.6, life: 0.42, spin: t * 15, speed: 300, color: core, alpha: 0.55 });
    });
  }
  play(dim, "mob.warden.sonic_charge", c, 1.0, 1.1);
  play(dim, "mob.breeze.charge", c, 1.2, 0.7);
  const hitList = [];
  const floating = [];
  for (const e of around(player, c, k.radius, 3)) {
    runeHit(player, e, P, k.mult, r.stat);
    if (!canHit(player, e)) continue; // 쓰러진 적은 빼고 (첫 피해가 무적 시간에 막혀도 띄우고 내리꽂음)
    hitList.push(e);
    if (liftUp(e, k.lift, k.liftAmp, k.gap + RUNE_GRAVITY.landWait)) {
      floating.push(e);
      if (floating.length <= 8) liftFx(dim, e); // 8명까지 (파티클 예산)
    }
  }
  for (let t = 4; t < k.gap; t += 4) {
    later(t, () => {
      if (sameLife(player, life)) for (const e of floating.slice(0, 10)) floatAura(dim, e);
    });
  }
  later(k.gap - 4, () => {
    if (sameLife(player, life)) play(dim, "mace.smash_air", player.location, 1.2, 0.7);
  });
  later(k.gap, () => {
    if (!sameLife(player, life)) return;
    const pc = player.location;
    zonePulse(dim, pc, glow, k.radius, 0.5);
    floorRune(dim, up(c, sky), { size: k.radius * 0.85, life: 0.3, color: WHITE, alpha: 0.9, grow: 0.3, rate: 0, spin: 0 });
    rfx2(dim, "sun", sigil, { size: 1.9 * nearK(player, sigil, 3.5, 0.5), life: 0.3, grow: 0.4, fade: 0.7, spin: -k.gap * 9 }, WHITE, core);
    shakeAround(dim, pc, 12, 0.35, 0.35);
    play(dim, "random.explode", pc, 0.7, 0.9);
    const landed = new Map(); // 틱 → 그 틱에 내리꽂힌 수 (파티클 예산)
    let streaks = 0;
    for (const e of hitList) {
      if (!e.isValid) continue;
      if (streaks++ < 12) slamStreak(dim, bodyPoint(e), 4.5); // 하늘에서 내리꽂는 빛줄기는 12명까지 (파티클 예산)
      slamDown(player, e, life, (x) => {
        const now = system.currentTick;
        const n = landed.get(now) ?? 0;
        landed.set(now, n + 1);
        slamFx(dim, x.location, 1.8, player, n);
        play(dim, "mace.heavy_smash_ground", x.location, 1.0, 0.8);
        if (runeHit(player, x, P, k.crush, r.stat) <= 0) return;
        // 짓눌림: 띄운 동안의 구속 XI 이 남아 있으면 더 약한 구속 II 로 바뀌지 않으므로 지우고 검 (나약함은 남은 몇 틱 그대로)
        try {
          x.removeEffect("slowness");
        } catch {
          // 무시
        }
        effect(x, "slowness", k.slowTicks, k.slowAmp);
      });
    }
  });
  return true;
}

/**
 * 블랙홀: 조준한 곳 (range 칸) 위 height 칸에 charge 틱 동안 블랙홀 (rpg:rfx_hole — 도는 빛 원반 + 어두운 핵) 이 자람 → hits × gap 틱 동안 pull 칸 안을 빨아들이고
 * (핵 가까이 3칸 안은 띄움 — 공격 못 함), gap 틱마다 radius 칸 위력 × mult → 붕괴 (radius 칸 위력 × crush, 떠 있던 적은 내리꽂힘)
 */
function blackHole(player, r, P, k) {
  const dim = player.dimension;
  const [glow, core] = RUNE.color.gravity;
  const g = aimAtLeast(player, groundAim(player, k.range), k.minDist);
  const hole = up(g, k.height);
  const T = k.hits * k.gap;
  const life = lifeToken(player);
  const G = RUNE_GRAVITY;
  const sc = nearScale(player, hole);
  const spin = (t) => (t * 12) % 360; // 원반이 이어 돌게 (240도/초)
  play(dim, "mob.warden.sonic_charge", g, 1.0, 0.8);
  play(dim, "conduit.activate", g, 1.0, 0.6);
  // v2.27.0 2차 강화 — 예고: 공허의 하늘 (어둠) · 겨눈 땅의 거대한 비전 문양 (거꾸로 돌며 빨려 듦) · 뒤쪽 하늘의 검은 일식 (아래) · 핵이 다 열리면 빛을 삼키는 어두운 번쩍임
  ultSkyFor(dim, g, "blackHole", k.charge + T + 34);
  domainSigil(dim, g, k.radius * 1.15, "arcane", glow, core, { life: (k.charge + T + 1) / 20, spinRate: -70, grow: -0.25, alpha: 0.75, fade: 0.1 });
  later(k.charge, () => {
    if (sameLife(player, life)) ultFlashFor(player, dim, g, "blackHole", "dark", { time: { fadeInTime: 0.05, holdTime: 0.05, fadeOutTime: 0.5 } });
  });
  for (let t = 0; t < k.charge; t += 2) {
    later(t, () => {
      if (!sameLife(player, life)) return;
      const q = (t + 2) / k.charge;
      const s = (0.35 + q * 1.55) * sc;
      rfx(dim, "hole", hole, { size: s, life: 0.14, cell: 0, spin: spin(t), spinRate: 240, alpha: 0.6 + 0.4 * q });
      rfx(dim, "hole", hole, { size: s, life: 0.14, cell: RFX.holeCore });
      inwardSparks(dim, hole, 3.5, 3, 0.35, t % 4 ? core : glow, 2);
      floorRune(dim, g, { size: k.radius * 0.8, life: 0.14, color: glow, alpha: 0.9, progress: q, rate: 0, spin: -t * 4 });
    });
  }
  for (let t = 0; t < T; t += 2) {
    later(k.charge + t, () => {
      if (!sameLife(player, life)) return;
      if (t % 4 === 0) {
        rfx(dim, "hole", hole, { size: 1.9 * sc, life: 0.24, cell: 0, spin: spin(k.charge + t), spinRate: 240 });
        rfx(dim, "hole", hole, { size: 1.9 * sc, life: 0.24, cell: RFX.holeCore });
      }
      inwardSparks(dim, hole, k.radius, 3, 0.7, t % 4 ? core : glow, 3);
      if (t % 6 === 0) fx(dim, "rpg:whirlpool", up(g, 0.1), { dir: { x: 0, y: 0, z: 0 }, size: k.radius, life: 0.5, spin: t * 10, speed: -300, color: glow, alpha: 0.6 });
      if (t % 8 === 0) fx(dim, "rpg:dust", hole, { size: 2.6 * sc, life: 0.6, color: GRAVITY_VOID, alpha: 0.6 * sc });
      if (t % 4 === 2) inwardRock(dim, hole, k.radius - 1, 0.7, k.height);
      if (t % 20 === 0) play(dim, "mob.endermen.portal", hole, 1.2, 0.5);
    });
  }
  // 빨아들임 (핵 가까이는 핵 높이 쪽으로 띄움)
  for (let t = 0; t < T; t += G.pullEvery) {
    later(k.charge + t, () => {
      if (!sameLife(player, life)) return;
      for (const e of around(player, g, k.pull, 5)) {
        pullToward(e, g);
        if (Math.hypot(e.location.x - g.x, e.location.z - g.z) < 3 && e.location.y < hole.y - 1.0) liftUp(e, G.pullEvery + 3, 2, G.pullEvery + 14); // 몸 가운데가 핵 높이쯤까지
      }
    });
  }
  for (let i = 0; i < k.hits; i++) {
    later(k.charge + i * k.gap, () => {
      if (!sameLife(player, life)) return;
      for (const e of around(player, g, k.radius, 5)) runeHit(player, e, P, k.mult, r.stat);
    });
  }
  // v2.27.0 뒤쪽 하늘의 검은 일식 (블랙홀 그림을 크게 — 원반 + 핵): 붕괴 때 섬광으로 터짐
  const eclipse = skyPoint(player, g, 8, 21);
  rfx(dim, "hole", eclipse, { size: 3.6, life: (k.charge + T + 2) / 20, cell: 0, spinRate: 50, alpha: 0.85, fade: 0.1 });
  rfx(dim, "hole", eclipse, { size: 3.6, life: (k.charge + T + 2) / 20, cell: RFX.holeCore, alpha: 0.95, fade: 0.1 });
  later(k.charge + T, () => {
    if (!sameLife(player, life)) return;
    // 붕괴: 원반이 안으로 꺼지며 (커졌다 사라짐) 터짐
    rfx(dim, "hole", hole, { size: 2.2 * sc, life: 0.3, cell: 0, spin: spin(k.charge + T), spinRate: 900, grow: -0.9 });
    collapseFx(dim, hole, g, k.radius, 1, sc);
    play(dim, "mob.warden.sonic_boom", g, 1.0, 0.8);
    play(dim, "random.explode", g, 0.8, 0.9);
    shakeAround(dim, g, 24, 0.5, 0.5);
    // v2.27.0 발동 (붕괴): 흰 보랏빛 번쩍임 · 퍼지는 고리 · 하늘의 일식이 터지는 섬광 (1틱 뒤) · 공허의 금 (2틱 뒤)
    ultFlashFor(player, dim, g, "blackHole");
    later(1, () => {
      novaRing(dim, g, 2.0, 4.5, glow, core, { life: 0.55 });
      skyFlare(dim, eclipse, 4.2, glow, core, { life: 0.6 });
    });
    later(2, () => glowCrack(dim, g, k.radius * 1.1, glow, core, { life: 2.2, v: 1 }));
    for (const e of around(player, g, k.radius, 5)) {
      runeHit(player, e, P, k.crush, r.stat);
      if (canHit(player, e) && gravityMovable(e) && !onGround(e)) {
        slamStreak(dim, bodyPoint(e), 3);
        slamDown(player, e, life, () => {});
      }
    }
  });
  return true;
}

// ============================================================ v2.24.0 신체 강화 · 평타 강화 (적용 스탯 = 네 스탯 중 가장 높은 것 × 1.0)
// 사용자 요청 "신체 강화나 평타 강화 같은 룬도 추가" · 고른 것: 새 계열 둘 · 10종 (투지 · 강철 근육 · 재생 · 투신 · 초인 각성 / 날 세우기 · 연타 · 가르기 · 피의 칼날 · 검신 강림)
/** 신체 강화: 플레이어 → {player, grades: 등급 → {def, grade, until, P, scale, nextAt}} @type {Map<string, {player: any, grades: Map<string, any>}>} */
const bodyBuffs = new Map();
/** 평타 강화: 플레이어 → {player, grades: 등급 → {def, grade, until, left, P, token}} @type {Map<string, {player: any, grades: Map<string, any>}>} */
const strikeBuffs = new Map();
/** buffTick 이 돈 횟수 (매 틱 — 신체 강화 연출은 짝수 틱, 평타 강화 연출은 홀수 틱: 한 틱에 몰리지 않게) */
let buffTicks = 0;

/** 지금 걸린 신체 강화 (끝난 것 빼고, 높은 등급 먼저) */
export function bodyBuffsOf(player) {
  const b = bodyBuffs.get(player.id);
  const now = system.currentTick;
  return b ? [...b.grades.values()].filter((s) => s.until > now).sort((a, c) => GRADE_ORDER.indexOf(c.grade) - GRADE_ORDER.indexOf(a.grade)) : [];
}

/** 평타 강화가 아직 있는지: 시간 · 남은 번 · 삶 표시 (죽음 · 차원 이동 · 탈것이면 끝) */
function strikeOn(player, st) {
  return st.until > system.currentTick && st.left > 0 && st.token === lifeToken(player);
}

/** 지금 걸린 평타 강화 (높은 등급 먼저) */
export function strikeBuffsOf(player) {
  const b = strikeBuffs.get(player.id);
  return b ? [...b.grades.values()].filter((s) => strikeOn(player, s)).sort((a, c) => GRADE_ORDER.indexOf(c.grade) - GRADE_ORDER.indexOf(a.grade)) : [];
}

/** 모든 스탯 +: 걸린 신체 강화 중 가장 큰 것 (stats.js — 바뀌면 최대 체력도 다시) */
function refreshStatBuff(player) {
  let pct = 0;
  for (const st of bodyBuffsOf(player)) pct = Math.max(pct, st.def.statUp ?? 0);
  setStatBuff(player, pct);
}

/** 신체 강화 걸기 (같은 등급은 처음부터): 받는 피해 감소 · 이동 속도 · 밀려나지 않음 · 모든 스탯은 지금, 회복은 buffTick 이 pulse 틱마다 */
function startBody(player, r, P, k) {
  const now = system.currentTick;
  let b = bodyBuffs.get(player.id);
  if (!b) bodyBuffs.set(player.id, (b = { player, grades: new Map() }));
  b.player = player;
  b.grades.set(r.grade, { def: k, grade: r.grade, until: now + k.ticks, P, scale: supportScale(player, r.family), nextAt: now + (k.pulse ?? 20) });
  if (k.reduce) setGuard(player, k.ticks, k.reduce, undefined);
  if (k.speed !== undefined) effect(player, "speed", k.ticks, k.speed);
  if (k.steadfast) setSteadfast(player, k.ticks);
  refreshStatBuff(player);
}

/** 신체 강화를 쓸 때: 몸에서 기운이 터짐 (발밑 룬 · 충격 고리 · 머리 위 빛기둥 · 몸의 기운) + 1인칭 눈앞 고리 · 글자 */
function bodyCast(player, r, k, big = 0) {
  const dim = player.dimension;
  const c = player.location;
  const col = k.accent;
  const [, core] = RUNE.color.body;
  floorRune(dim, c, { size: 1.6 + big * 0.5, life: 0.9, color: col, alpha: 0.9, rate: 140, grow: 0.3 });
  shock(dim, c, col, 1.1 + big * 0.5, 0.45);
  burst(dim, up(c, 1), col, 1.5 + big * 0.5, 0.35);
  fx(dim, "rpg:arrow_shot", up(c, 4.4 + big), { dir: V_UP, speed: 0, size: 2.0 + big * 0.6, thick: 0.7 + big * 0.2, life: 0.55, color: col, alpha: 0.8 });
  fx(dim, "rpg:arrow_shot", up(c, 4.4 + big), { dir: V_UP, speed: 0, size: 1.9 + big * 0.6, thick: 0.22, life: 0.5, color: core, alpha: 1 });
  bodyAura(player, col, { n: 6, size: 1.2 + big * 0.15, alpha: 0.85, life: 0.36, force: true }); // 쓸 때의 큰 기운 (조금 길게)
  selfCue(player, col, core);
  showFaceText(player, `§6§l${k.name}!`, { side: -0.9, up: -0.35 });
}

/** 신체 강화 동안 (짝수 틱 2틱마다 — 연출 횟수 n): 몸의 기운 (가장 높은 등급의 색, 짧게 · 몸을 따라옴) + 룬마다 덧붙임 + 1인칭 불티 (12틱마다) */
function bodyFx(player, st, n) {
  const dim = player.dimension;
  const k = st.def;
  const gi = GRADE_ORDER.indexOf(st.grade);
  bodyAura(player, k.accent, { n: [2, 3, 3, 4, 5][gi] ?? 3, size: 0.95 + gi * 0.07, alpha: 0.6 + gi * 0.05 });
  if (n % 6 === 0) auraCue(player, k.accent, gi >= 3 ? 3 : 2);
  const c = player.location;
  const f = facingOf(player);
  if (k.fn === "steelBody" && n % 6 === 2) {
    // 강철 비늘: 몸 옆에서 번쩍이는 쇳빛
    const d = turn(f, (n % 12 === 2 ? 1 : -1) * (80 + Math.random() * 30));
    rfx(dim, "prop", up(add(c, d, 0.45), 0.7 + Math.random() * 0.8), { size: 0.22, life: 0.3, cell: RFX.glint[0], spin: Math.random() * 90, color: WHITE, alpha: 0.9, vel: bodyVel(player) });
  } else if (k.fn === "regenBody" && n % 4 === 0) {
    const a = Math.random() * Math.PI * 2;
    fx(dim, "rpg:arrow_spark", { x: c.x + Math.cos(a) * 0.7, y: c.y + 0.3, z: c.z + Math.sin(a) * 0.7 }, { dir: V_UP, speed: 1.4, size: 0.14, grow: 1.3, life: 0.8, color: k.accent, alpha: 1 });
  } else if (k.fn === "awakening" && n % 4 === 2) {
    // 몸 둘레에 튀는 금빛 번개 (양옆)
    const d = turn(f, (n % 8 === 2 ? 1 : -1) * (95 + Math.random() * 40));
    const a = up(add(c, d, 0.55), 0.4 + Math.random() * 1.2);
    bolt(dim, a, add(a, { x: (Math.random() - 0.5) * 0.8, y: 0.5 + Math.random() * 0.5, z: (Math.random() - 0.5) * 0.8 }, 1), k.accent, { jag: 0.25, thick: 0.06, life: 0.18 });
  }
}

/** 재생 · 초인 각성의 회복 한 번 (pulse 틱마다) */
function bodyPulse(player, st) {
  const k = st.def;
  const healed = healBy(player, hpState(player).max * k.heal * st.scale);
  if (healed > 0) showDamageText(player, healed, "heal");
  const dim = player.dimension;
  const c = player.location;
  zonePulse(dim, c, k.accent, 1.3, 0.4);
  particle(dim, "minecraft:heart_particle", up(c, 2.1));
  for (let i = 0; i < 3; i++) {
    const a = Math.random() * Math.PI * 2;
    fx(dim, "rpg:arrow_spark", { x: c.x + Math.cos(a) * 0.8, y: c.y + 0.2, z: c.z + Math.sin(a) * 0.8 }, { dir: V_UP, speed: 2.0, size: 0.18, grow: 1.4, life: 0.7, color: i ? k.accent : WHITE, alpha: 1 });
  }
  play(dim, "random.orb", c, 1.4, 0.35);
}

/** 신체 강화가 모두 끝남: 기운이 흩어짐 */
function bodyEnd(player) {
  try {
    const dim = player.dimension;
    burst(dim, up(player.location, 1), RUNE.color.body[0], 1.0, 0.3);
    play(dim, "beacon.deactivate", player.location, 1.6, 0.4);
    if (actionbarFree(player) && !promptedRecently(player)) notify(player, "§7신체 강화가 끝났습니다", 16);
  } catch {
    // 무시
  }
}

/** 죽음 · 나감: 신체 강화를 모두 없앰 (모든 스탯 · 밀려나지 않음도) */
function clearBody(player) {
  bodyBuffs.delete(player.id);
  clearSteadfast(player.id);
  try {
    setStatBuff(player, 0);
  } catch {
    // 무시
  }
}

/** 투지: 5초 동안 이동 속도 II · 받는 피해 -15% (주황 기운) */
function fightingSpirit(player, r, P, k) {
  startBody(player, r, P, k);
  bodyCast(player, r, k, 0);
  const dim = player.dimension;
  const f = facingOf(player);
  for (let i = 0; i < 4; i++) streak(dim, up(add(player.location, f, -0.6 - i * 0.5), 0.4 + i * 0.3), up(add(player.location, f, -1.4 - i * 0.6), 0.4 + i * 0.3), k.accent, 0.1, 0.3, 0.8); // 뒤로 흐르는 바람
  play(dim, "mob.ravager.roar", player.location, 1.5, 0.45);
  play(dim, "item.trident.riptide_1", player.location, 1.3, 0.6);
  return true;
}

/** 강철 근육: 7초 동안 받는 피해 -25% · 보스 · 몬스터 기술에 밀려나지 않음 (쇳빛 기운 · 강철 비늘) */
function steelBody(player, r, P, k) {
  startBody(player, r, P, k);
  bodyCast(player, r, k, 0.4);
  const dim = player.dimension;
  const c = player.location;
  const f = facingOf(player);
  for (let i = 0; i < 6; i++) {
    const d = turn(f, (i % 2 ? 1 : -1) * (70 + i * 14));
    rfx(dim, "prop", up(add(c, d, 0.5), 0.5 + (i % 3) * 0.5), { size: 0.3, life: 0.4, cell: RFX.glint[0], spin: i * 30, color: WHITE, grow: 0.4, fade: 0.5 });
  }
  burst(dim, up(c, 1), STEEL, 1.8, 0.35);
  play(dim, "armor.equip_iron", c, 0.8, 1);
  play(dim, "random.anvil_use", c, 0.9, 0.6);
  return true;
}

/** 재생: 9초 동안 1초마다 최대 체력 × heal 회복 · 이동 속도 I (초록 기운) */
function regenBody(player, r, P, k) {
  startBody(player, r, P, k);
  bodyCast(player, r, k, 0.4);
  const dim = player.dimension;
  particle(dim, "minecraft:heart_particle", up(player.location, 2.1));
  play(dim, "beacon.power", player.location, 1.6, 0.6);
  play(dim, "random.levelup", player.location, 1.8, 0.4);
  return true;
}

/** 투신: 12초 동안 모든 스탯 +15% · 받는 피해 -20% (핏빛 기운 · 땅이 울림) */
function warGod(player, r, P, k) {
  startBody(player, r, P, k);
  bodyCast(player, r, k, 0.8);
  const dim = player.dimension;
  const c = player.location;
  fx(dim, "rpg:ground_crack", up(c, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: 2.6, life: 1.4, color: [0.4, 0.06, 0.04], alpha: 0.9 });
  for (let i = 0; i < 3; i++) later(i * 2, () => shock(dim, c, i % 2 ? k.accent : RUNE.color.body[0], 1.6 + i * 0.9, 0.5));
  play(dim, "mob.ravager.roar", c, 0.9, 0.8);
  play(dim, "mob.enderdragon.growl", c, 1.6, 0.35);
  shake(player, 0.15, 0.25);
  return true;
}

/**
 * 초인 각성: 15초 동안 모든 스탯 +25% · 받는 피해 -30% · 1초마다 회복. 쓸 때 하늘에서 금빛 빛기둥이 내려와 몸에 닿고 (머리 위까지)
 * 주위 radius 칸에 충격파 (위력 × mult, 밀쳐냄 · 땅이 갈라지고 바위가 튐)
 */
function awakening(player, r, P, k) {
  startBody(player, r, P, k);
  const dim = player.dimension;
  const c = { ...player.location };
  const life = lifeToken(player);
  // 하늘에서 내려오는 빛기둥 (머리 위 2.6칸까지 — 1인칭 카메라가 기둥 안에 들어가지 않게)
  fx(dim, "rpg:arrow_shot", up(c, 22), { dir: V_DOWN, speed: 17 / 0.25, size: 3.0, thick: 1.4, life: 0.25, color: k.accent, alpha: 0.95 });
  fx(dim, "rpg:arrow_shot", up(c, 22), { dir: V_DOWN, speed: 17 / 0.25, size: 2.8, thick: 0.5, life: 0.25, color: WHITE, alpha: 1 });
  play(dim, "beacon.activate", c, 0.8, 1);
  // v2.27.0 2차 강화 — 각성: 호박빛 하늘 · 충격파 때 번쩍임 → 성광 문양 · 하늘의 섬광 (1틱 뒤) → 앞쪽 빛기둥 넷 (3틱 뒤 — 충격파 틱의 파티클 예산)
  ultSkyFor(dim, c, "awakening", 76);
  later(5, () => {
    if (!sameLife(player, life)) return; // 죽음 · 차원 이동이면 충격파 없음
    const cc = player.location;
    bodyCast(player, r, k, 1.4);
    ultFlashFor(player, dim, cc, "awakening");
    later(1, () => {
      domainSigil(dim, cc, 5.4, "holy", k.accent, WHITE, { life: 1.4, spinRate: 50, grow: 0.3, alpha: 0.85 });
      if (player.isValid) skyFlare(dim, skyPoint(player, cc, 12, 20), 3.6, k.accent, WHITE, { life: 0.7 });
    });
    later(3, () => {
      if (sameLife(player, life)) godRays(player, player.dimension, player.location, 6, 4, k.accent, WHITE, { arc: [-60, 60], h: 16, life: 0.9 });
    });
    rfx(dim, "prop", up(cc, 4.6), { size: 0.9, life: 0.5, cell: RFX.glint[0], color: WHITE, grow: 0.4, fade: 0.6 }); // 머리 위 3칸 (위를 봐도 화면을 덮지 않게)
    for (let i = 0; i < 4; i++) later(i * 2, () => zonePulse(dim, cc, i % 2 ? WHITE : k.accent, k.radius * (0.55 + 0.15 * i), 0.5));
    fx(dim, "rpg:ground_crack", up(cc, 0.05), { dir: { x: 0, y: 0, z: 0 }, spin: Math.random() * 360, size: k.radius * 1.3, life: 1.6, color: [0.36, 0.2, 0.04], alpha: 0.9 });
    // 먼지 · 튀는 바위: 몸에서 떨어진 둘레에서 (몸 가운데 먼지는 1인칭 카메라가 그 안에 들어가 화면 아래를 덮음)
    for (let i = 0; i < 5; i++) {
      const d = turn({ x: 1, y: 0, z: 0 }, i * 72 + Math.random() * 20);
      fx(dim, "rpg:dust", add(up(cc, 0.6), d, k.radius * 0.45), { size: 1.3, life: 0.8, color: [0.66, 0.54, 0.36], alpha: 0.6 });
    }
    for (let i = 0; i < 6; i++) {
      const a = (i / 6) * Math.PI * 2 + Math.random() * 0.4;
      const d = { x: Math.cos(a), y: 0, z: Math.sin(a) };
      fx(dim, "rpg:boulder", add(up(cc, 0.4), d, 1.3), { dir: unit({ x: d.x, y: 1.1, z: d.z }), speed: 6 + Math.random() * 2, size: 0.26, life: 0.7, spin: Math.random() * 360, color: ROCK, alpha: 1 });
    }
    for (let i = 0; i < 8; i++) {
      const dd = turn({ x: 1, y: 0, z: 0 }, i * 45 + Math.random() * 10);
      fx(dim, "rpg:light_cut", add(up(cc, 0.08), dd, k.radius * 0.45), { dir: dd, size: k.radius * 0.4, thick: 0.34, life: 1.0, delay: 0.6, color: k.accent, alpha: 0.95 });
    }
    play(dim, "item.trident.thunder", cc, 0.8, 1);
    play(dim, "random.explode", cc, 0.9, 0.6);
    play(dim, "mob.enderdragon.growl", cc, 1.2, 0.5);
    shakeAround(dim, cc, 12, 0.35, 0.35);
    let shown = 0; // 적이 많으면 빛은 8마리까지 (모바일 파티클 예산 — 이 틱에 충격파 연출이 많음)
    for (const e of around(player, cc, k.radius)) {
      if (runeHit(player, e, P, k.mult, r.stat) > 0) {
        knock(e, sub(e.location, cc), 0.9, 0.4);
        if (shown++ < 8) burst(dim, bodyPoint(e), k.accent, 0.8, 0.25);
      }
    }
  });
  return true;
}

// ---- 평타 강화
/** 평타 강화 걸기 (같은 등급은 처음부터, 다른 등급은 함께 — 더한 피해를 더함) */
function startStrike(player, r, P, k) {
  let b = strikeBuffs.get(player.id);
  if (!b) strikeBuffs.set(player.id, (b = { player, grades: new Map() }));
  b.player = player;
  b.grades.set(r.grade, { def: k, grade: r.grade, until: system.currentTick + k.ticks, left: k.hits ?? Infinity, P, token: lifeToken(player) });
}

/** 평타 강화를 쓸 때: 무기를 든 손에 빛 (고리 · 반짝임) + 글자 */
function strikeCast(player, k) {
  const dim = player.dimension;
  const { hand, f } = handOf(player);
  ring(dim, hand, f, k.accent, 0.5, 0.3);
  rfx(dim, "prop", hand, { size: 0.3, life: 0.35, cell: RFX.glint[0], spin: 20, color: WHITE, grow: 0.6, fade: 0.6 });
  sparks(dim, hand, k.accent, 6, 3);
  showFaceText(player, `§6§l${k.name}${k.hits ? ` §r§f×${k.hits}` : ""}`, { dist: 3.0, side: 1.2, up: -1.0 }); // 무기를 든 오른손 쪽 아래
}

/** 날 세우기: 다음 평타 3번 (6초 안) 에 위력 × bonus — 칼날을 숫돌에 가는 불티 */
function edgeHone(player, r, P, k) {
  startStrike(player, r, P, k);
  strikeCast(player, k);
  const dim = player.dimension;
  const { hand, f } = handOf(player);
  const side = rightOf(f);
  // 손에서 칼날을 따라 그어지는 빛 + 튀는 불티 (1인칭 화면 오른쪽 아래)
  fx(dim, "rpg:light_cut", up(add(hand, side, -0.15), 0.25), { dir: unit(add(V_UP, side, -0.6)), size: 0.45, thick: 0.08, life: 0.35, delay: 0.1, color: WHITE, alpha: 1 });
  for (let i = 0; i < 6; i++) fx(dim, "rpg:arrow_spark", hand, { dir: unit({ x: side.x + (Math.random() - 0.5), y: 0.6 + Math.random() * 0.6, z: side.z + (Math.random() - 0.5) }), speed: 3, size: 0.06, life: 0.3, color: [1.0, 0.85, 0.5], alpha: 1 });
  play(dim, "random.anvil_use", player.location, 2.0, 0.3);
  play(dim, "item.spear.attack_miss", player.location, 1.6, 0.6);
  return true;
}

/** 연타: 7초 동안 활 · 완드 재사용 × rangedCd, 근접 평타마다 잔상 (위력 × echo, 옆의 적 하나에게도) */
function flurry(player, r, P, k) {
  startStrike(player, r, P, k);
  strikeCast(player, k);
  const dim = player.dimension;
  const head = player.getHeadLocation();
  const f = flat(player.getViewDirection());
  arc(dim, up(add(head, f, 1.6), -0.3), f, { shape: "thin", flip: false, size: 1.6, life: 0.18, speed: 4, spin: -20, color: k.accent, core: true });
  later(2, () => {
    if (player.isValid) arc(dim, up(add(player.getHeadLocation(), f, 1.7), -0.35), f, { shape: "thin", flip: true, size: 1.6, life: 0.18, speed: 4, spin: 20, color: k.accent, core: true });
  });
  play(dim, "mob.breeze.whirl", player.location, 1.6, 0.6);
  return true;
}

/** 가르기: 평타 9번 (9초 안) — 위력 × bonus, 근접은 앞 부채꼴 cleave 칸 · cone 도, 활 · 완드는 맞은 적 뒤 splash 칸의 다른 적도 벰 */
function cleave(player, r, P, k) {
  startStrike(player, r, P, k);
  strikeCast(player, k);
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  rfx2(dim, "wave", up(add(player.location, f, 2.4), 1.0), { size: 1.8, life: 0.28, grow: 0.4, fade: 0.6 }, k.accent, WHITE);
  play(dim, "item.spear.attack_miss", player.location, 0.9, 0.8);
  play(dim, "random.anvil_use", player.location, 1.4, 0.3);
  return true;
}

/** 피의 칼날: 평타 12번 (12초 안) — 위력 × bonus, 맞힐 때마다 최대 체력 × heal 회복 (핏빛 손) */
function bloodBlade(player, r, P, k) {
  startStrike(player, r, P, k);
  strikeCast(player, k);
  const dim = player.dimension;
  const { hand } = handOf(player);
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2;
    const from = add(hand, { x: Math.cos(a) * 1.2, y: Math.sin(a) * 0.6, z: Math.sin(a) * 1.2 });
    const d = sub(hand, from);
    fx(dim, "rpg:arrow_spark", from, { dir: unit(d), speed: len(d) / 0.35, size: 0.1, grow: 0.5, life: 0.35, color: k.accent, alpha: 1 });
  }
  play(dim, "mob.vex.charge", player.location, 0.7, 0.8);
  play(dim, "mob.zombie.unfect", player.location, 1.4, 0.4);
  return true;
}

/**
 * 검신 강림: 평타 16번 (15초 안) — 위력 × bonus, 평타마다 거대한 금빛 검기 (wave 칸 · 폭 width, 맞은 적 너머의 적), 평타 치명타 + crit.
 * 쓸 때 앞쪽 하늘에서 거대한 금빛 검이 떨어져 꽂히고, 그 동안 양 어깨 위에 금빛 검 둘이 떠 있음
 */
function swordGod(player, r, P, k) {
  startStrike(player, r, P, k);
  strikeCast(player, k);
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const g = add(player.location, f, 4);
  const gy = groundAt(dim, g, player.location.y);
  const at = { x: g.x, y: gy, z: g.z };
  const L = 7;
  const rest = L / 2 - 1.2;
  rfx2(dim, "sword", up(at, 15), { dir: V_DOWN, size: L / 2, life: 0.3, speed: (15 - rest) / 0.3 }, k.accent, WHITE);
  fx(dim, "rpg:arrow_shot", up(at, 12), { dir: V_UP, speed: 0, size: 12, thick: 0.12, life: 0.3, color: k.accent, alpha: 0.7 });
  later(5, () => rfx2(dim, "sword", up(at, rest), { dir: V_DOWN, size: L / 2, life: 1.45, fade: 0.45 }, k.accent, WHITE)); // 꽂힌 검: 떨어지는 검이 끝나기 1틱 전 (깜빡임 없게)
  later(6, () => {
    rfx(dim, "prop", up(at, rest + L / 2 - 0.3), { size: 1.6, life: 0.45, cell: RFX.glint[0], color: WHITE, grow: 0.6, fade: 0.6 });
    for (let i = 0; i < 3; i++) later(i * 2, () => shock(dim, at, i % 2 ? WHITE : k.accent, 1.4 + i, 0.5));
    for (let i = 0; i < 8; i++) {
      const d = turn({ x: 1, y: 0, z: 0 }, i * 45 + Math.random() * 12);
      fx(dim, "rpg:light_cut", add(up(at, 0.08), d, 1.4), { dir: d, size: 1.2, thick: 0.3, life: 0.9, delay: 0.5, color: k.accent, alpha: 0.95 });
    }
    sparks(dim, up(at, 0.6), k.accent, 12, 6);
    play(dim, "item.trident.thunder", at, 1.0, 0.8);
    play(dim, "mace.heavy_smash_ground", at, 0.8, 0.8);
    shakeAround(dim, at, 10, 0.25, 0.25);
  });
  play(dim, "beacon.activate", player.location, 1.2, 0.8);
  // v2.27.0 2차 강화 — 강림: 흰 금빛 하늘 · 검이 내려오는 앞쪽 하늘이 갈라짐 → (꽂힐 때) 번쩍임 · 섬광 → 칼날 문양 · 충격 고리 (1틱 뒤)
  ultSkyFor(dim, at, "swordGod", 70);
  const rift = skyPoint(player, at, 7, 21);
  skyPlate(player, dim, "crack", rift, 5.2, k.accent, WHITE, { life: 0.75, grow: 0.4, fade: 0.5 });
  later(6, () => {
    ultFlashFor(player, dim, at, "swordGod");
    skyFlare(dim, rift, 3.6, k.accent, WHITE, { life: 0.5 });
  });
  later(7, () => {
    domainSigil(dim, at, 4.2, "blade", k.accent, WHITE, { life: 1.3, spinRate: 70, grow: 0.3, alpha: 0.85 });
    novaRing(dim, at, 1.4, 3.6, k.accent, WHITE, { life: 0.45 });
  });
  return true;
}

/** 평타에 더할 피해 (combat.js — before 이벤트 안이라 세계를 바꾸지 않음): 걸린 평타 강화마다 위력 × bonus (연타는 근접만 위력 × echo) */
function strikeBonus(player, victim, w) {
  const b = strikeBuffs.get(player.id);
  if (!b || !b.grades.size || !canHit(player, victim)) return undefined;
  let add0 = 0;
  const grades = [];
  for (const st of b.grades.values()) {
    if (!strikeOn(player, st)) continue;
    const k = st.def;
    const per = k.echo !== undefined ? (isMeleeWeapon(w) ? st.P * k.echo : 0) : st.P * (k.bonus ?? 0);
    if (!(per > 0)) continue;
    add0 += per;
    grades.push(st.grade);
  }
  return add0 > 0 ? { add: add0, mark: RUNE_STRIKE.mark, key: RUNE_STRIKE.family, style: "rune", grades } : undefined;
}

/** 평타 치명타 + (검신 강림). 맞을 엔티티가 스킬 대상이 아니면 (다른 플레이어 · 길들인 동물 등) 없음 — 더한 피해와 같음 */
function strikeCrit(player, w, victim) {
  if (victim && !canHit(player, victim)) return 0;
  let c = 0;
  for (const st of strikeBuffsOf(player)) c += st.def.crit ?? 0;
  return c;
}

/** 활 · 완드 재사용 배율 (연타) */
function strikeRangedScale(player) {
  let k = 1;
  for (const st of strikeBuffsOf(player)) if (st.def.rangedCd) k = Math.min(k, st.def.rangedCd);
  return k;
}

/** 평타가 들어감 (after): 더한 평타 강화마다 한 번 쓰고 그 룬의 덧붙임 (잔상 · 부채꼴 · 회복 · 검기). 연타는 활 · 완드에도 잔상 연출 */
function strikeLanded(player, victim, w, bonus) {
  const b = strikeBuffs.get(player.id);
  if (!b || !b.grades.size) return;
  const part = bonus?.parts?.find((x) => x.key === RUNE_STRIKE.family);
  const used = new Set(part?.grades ?? []);
  for (const [g, st] of b.grades) {
    if (!strikeOn(player, st)) continue;
    if (used.has(g)) {
      if (Number.isFinite(st.left)) st.left -= 1;
      strikeEffect(player, victim, w, st);
      if (st.left <= 0) {
        b.grades.delete(g);
        strikeEnd(player, st, true);
      }
    } else if (st.def.rangedCd && !isMeleeWeapon(w) && victim?.isValid) {
      // 연타 (활 · 완드): 맞은 자리에 잔상 고리
      ring(player.dimension, bodyPoint(victim), unit(sub(bodyPoint(victim), player.getHeadLocation())), st.def.accent, 0.6, 0.22, 3);
    }
  }
}

/** 평타 강화가 끝남 (used = 평타를 다 씀, 아니면 시간이 다 됨) */
function strikeEnd(player, st, used) {
  try {
    const { hand } = handOf(player);
    burst(player.dimension, hand, st.def.accent, 0.4, 0.25);
    if (actionbarFree(player) && !promptedRecently(player)) notify(player, `§7${st.def.name}${used ? " 평타를 모두 썼습니다" : " 시간이 끝났습니다"}`, 16);
  } catch {
    // 무시
  }
}

/** 평타 강화 룬마다 맞힌 뒤 덧붙임 */
function strikeEffect(player, victim, w, st) {
  const k = st.def;
  const dim = player.dimension;
  const life = lifeToken(player);
  const eye = player.getHeadLocation();
  const at = victim?.isValid ? bodyPoint(victim) : add(eye, player.getViewDirection(), 2);
  const v = unit(sub(at, eye));
  const melee = isMeleeWeapon(w);
  const f = len(sub(at, eye)) > 0.3 ? flat(sub(at, player.location)) : facingOf(player);
  if (k.fn === "edgeHone") {
    rfx(dim, "prop", add(at, v, -0.4), { size: 0.55, life: 0.25, cell: RFX.glint[0], spin: 45, color: WHITE, grow: 0.4, fade: 0.6 });
    fx(dim, "rpg:slash_x", add(at, v, -0.3), { dir: v, size: 0.8, life: 0.2, color: k.accent, alpha: 1 });
    sparks(dim, at, WHITE, 5, 4);
    play(dim, "random.anvil_land", at, 2.0, 0.25);
  } else if (k.fn === "flurry") {
    // 잔상: 2틱 뒤 같은 자리를 한 번 더 베는 빛 + 옆의 적 하나에게 위력 × echo
    later(2, () => {
      if (!sameLife(player, life)) return;
      arc(dim, add(at, v, -0.5), v, { shape: "thin", flip: Math.random() < 0.5, size: 1.1, life: 0.16, speed: 2, spin: (Math.random() - 0.5) * 80, color: k.accent, core: true });
      const other = victim?.isValid ? nearestTo(around(player, victim.location, k.echoRange, 2.5).filter((e) => e.id !== victim.id), victim.location)[0] : undefined;
      if (!other) return;
      const to = bodyPoint(other);
      streak(dim, at, to, k.accent, 0.12, 0.25, 0.9);
      if (runeHit(player, other, st.P, k.echo, "str") > 0) {
        fx(dim, "rpg:slash_x", to, { dir: unit(sub(to, at)), size: 0.8, life: 0.2, color: k.accent, alpha: 1 });
        sparks(dim, to, WHITE, 4, 4);
      }
    });
    play(dim, "item.spear.attack_miss", at, 1.8, 0.4);
  } else if (k.fn === "cleave") {
    // 부채꼴: 근접 = 내 앞, 활 · 완드 = 맞은 적 뒤 (꿰뚫어 퍼짐)
    const c = melee ? player.location : victim?.isValid ? victim.location : add(player.location, f, 3);
    const R = melee ? k.cleave : k.splash;
    const mid = up(add(c, f, R * 0.55), 1.0);
    rfx2(dim, "wave", mid, { size: R * 0.62, life: 0.24, grow: 0.3, fade: 0.6 }, k.accent, WHITE);
    fx(dim, "rpg:light_cut", up(add(c, f, R * 0.7), 0.08), { dir: rightOf(f), size: R * 0.55, thick: 0.22, life: 0.6, delay: 0.35, color: k.accent, alpha: 0.9 });
    later(1, () => {
      if (!sameLife(player, life)) return;
      let shown = 0; // 적이 많으면 칼자국은 12마리까지 (모바일 파티클 예산)
      for (const e of coneAround(player, c, f, R, k.cone)) {
        if (victim && e.id === victim.id) continue;
        if (runeHit(player, e, st.P, k.bonus, "str") > 0 && shown++ < 12) cutMark(dim, bodyPoint(e), unit(sub(bodyPoint(e), eye)), -10, 0.08, k.accent, WHITE, 0.9);
      }
    });
    play(dim, "item.spear.attack_miss", c, 1.0, 0.6);
  } else if (k.fn === "bloodBlade") {
    // 핏빛 X 베기 + 밝은 속 (짙은 빨강만이면 풀밭 위에서 어둡게 보임) + 튀는 핏방울
    fx(dim, "rpg:slash_x", add(at, v, -0.3), { dir: v, size: 0.95, life: 0.24, color: k.accent, alpha: 1 });
    fx(dim, "rpg:slash_x", add(at, v, -0.36), { dir: v, size: 0.62, life: 0.2, color: BLOOD_CORE, alpha: 1 });
    sparks(dim, at, k.accent, 5, 4);
    const healed = healBy(player, hpState(player).max * k.heal * supportScale(player, RUNE_STRIKE.family));
    if (healed > 0) {
      showDamageText(player, healed, "heal");
      const to = up(player.location, 1.0);
      for (let i = 0; i < 3; i++) {
        const from = add(at, { x: (Math.random() - 0.5) * 0.6, y: (Math.random() - 0.3) * 0.6, z: (Math.random() - 0.5) * 0.6 });
        const d = sub(to, from);
        fx(dim, "rpg:arrow_spark", from, { dir: unit(d), speed: len(d) / 0.35, size: 0.14, grow: 0.5, life: 0.35, color: k.accent, alpha: 1 });
      }
    }
  } else if (k.fn === "swordGod") {
    // 거대한 금빛 검기: 근접 = 내 앞에서, 활 · 완드 = 맞은 적에서 그 너머로 (맞은 적은 평타에 이미 들어감)
    const from = melee ? up(add(player.location, f, 0.6), 1.1) : at;
    const dir = melee ? f : unit({ x: v.x, y: Math.max(-0.4, Math.min(0.4, v.y)), z: v.z });
    const L = Math.max(1, rayLength(dim, from, dir, k.wave));
    const s0 = Math.min(1.2, L * 0.5);
    rfx2(dim, "wave", add(from, dir, s0), { dir, speed: k.speed, size: k.width, life: Math.max(0.05, (L - s0) / k.speed), fade: 0.2 }, k.accent, WHITE);
    fx(dim, "rpg:light_cut", add(from, dir, L / 2), { dir, size: L / 2, thick: 0.16, life: 0.35, delay: 0.15, color: k.accent, alpha: 0.8 });
    const targets = lineTargets(player, from, dir, L, k.width).filter(({ e }) => !(victim && e.id === victim.id));
    const sp = targets.length > 6 ? 0 : targets.length > 3 ? 2 : 5; // 적이 많으면 불티를 줄이고 X 는 10마리까지 (모바일 파티클 예산 — 같은 틱에 여럿이 맞음)
    targets.forEach(({ e, t }, j) => {
      later(Math.max(1, Math.round((t / k.speed) * 20)), () => {
        if (!sameLife(player, life) || !e.isValid) return;
        if (runeHit(player, e, st.P, k.bonus, "str") > 0 && j < 10) {
          const p = bodyPoint(e);
          fx(dim, "rpg:slash_x", p, { dir, size: 1.2, life: 0.24, color: k.accent, alpha: 1 });
          if (sp) sparks(dim, p, WHITE, sp, 5);
        }
      });
    });
    play(dim, "item.trident.throw", from, 1.4, 0.5);
  }
}

/**
 * 평타 강화 동안 (홀수 틱 2틱마다 — 연출 횟수 n): 무기를 든 손에 그 룬의 빛 (4틱마다, 1인칭 화면 오른쪽 아래 · 다른 사람에게도 보임),
 * 검신 강림은 양 어깨 위 금빛 검 둘 (2틱마다 짧게 — 몸을 따라옴)
 */
function strikeFx(player, list, n) {
  const dim = player.dimension;
  const top = list[0];
  const vel = bodyVel(player);
  if (n % 2 === 1) {
    const { hand, f } = handOf(player);
    const side = rightOf(f);
    for (let i = 0; i < Math.min(2, list.length); i++) {
      const a = n * 0.45 + i * Math.PI;
      fx(dim, "rpg:arrow_spark", add(up(hand, Math.sin(a) * 0.16), side, Math.cos(a) * 0.2), { dir: V_UP, speed: 0.7, size: 0.09, life: 0.35, color: list[i].def.accent, alpha: 1 });
    }
    if (n % 4 === 1) rfx(dim, "prop", hand, { size: 0.09 + 0.02 * GRADE_ORDER.indexOf(top.grade), life: 0.24, cell: RFX.glint[0], spin: n * 12, color: top.def.accent, alpha: 0.95, vel });
  }
  const god = list.find((s) => s.def.fn === "swordGod");
  if (god) {
    const c = player.location;
    const fc = facingOf(player);
    const sd = rightOf(fc);
    for (const s of [-1, 1]) {
      const p = up(add(add(c, sd, s * 1.05), fc, -0.35), 2.2 + 0.12 * Math.sin(n * 0.2 + s));
      rfx2(dim, "sword", p, { dir: unit({ x: sd.x * s * 0.15, y: -1, z: sd.z * s * 0.15 }), size: 0.85, life: BODY_LIFE, alpha: 0.85, vel }, god.def.accent, WHITE);
    }
  }
}

/**
 * 신체 강화 · 평타 강화 (매 틱): 끝난 것 지우기 · 회복 · 연출. 연출은 2틱마다 (신체 강화 = 짝수 틱, 평타 강화 = 홀수 틱 —
 * 둘 다 걸려도 한 틱에 몰리지 않게, 무거운 룬의 터지는 틱과 겹쳐도 60개 안). m = 연출 횟수 (2틱마다 1씩)
 */
function buffTick() {
  const now = system.currentTick;
  const n = buffTicks++;
  const m = n >> 1;
  for (const [pid, b] of bodyBuffs) {
    const pl = b.player;
    let ok = false;
    try {
      ok = !!pl?.isValid;
    } catch {
      ok = false;
    }
    if (!ok) {
      bodyBuffs.delete(pid);
      continue;
    }
    let changed = false;
    for (const [g, st] of b.grades) {
      // 회복 먼저 (마지막 회복이 끝나는 틱과 겹쳐도 빠지지 않게: 9초 = 9번)
      if (st.def.heal && now >= st.nextAt && st.nextAt <= st.until && !isDying(pl)) {
        st.nextAt += st.def.pulse ?? 20;
        bodyPulse(pl, st);
      }
      if (now >= st.until) {
        b.grades.delete(g);
        changed = true;
      }
    }
    if (changed) refreshStatBuff(pl);
    if (!b.grades.size) {
      bodyBuffs.delete(pid);
      bodyEnd(pl);
      continue;
    }
    if (n % 2 === 0) keepSpeed(pl, now);
    if (n % 2 === 0 && !isDying(pl)) bodyFx(pl, bodyBuffsOf(pl)[0], m);
  }
  for (const [pid, b] of strikeBuffs) {
    const pl = b.player;
    let ok = false;
    try {
      ok = !!pl?.isValid;
    } catch {
      ok = false;
    }
    if (!ok) {
      strikeBuffs.delete(pid);
      continue;
    }
    for (const [g, st] of b.grades) {
      if (strikeOn(pl, st)) continue;
      b.grades.delete(g);
      if (st.token === lifeToken(pl) && st.left > 0) strikeEnd(pl, st, false); // 시간이 다 됨 (죽음 · 차원 이동 · 탈것이면 조용히)
    }
    if (!b.grades.size) {
      strikeBuffs.delete(pid);
      continue;
    }
    if (n % 2 === 1) strikeFx(pl, strikeBuffsOf(pl), m);
  }
}

/**
 * 신체 강화의 이동 속도를 지킴 (2틱마다): 걸린 등급 중 가장 센 이동 속도가 없거나 약해졌으면 그 등급이 끝날 때까지 다시 걺.
 * (투지 II 가 재생 I 을 덮어쓴 뒤 끝나면 게임은 I 을 되살리지 않음 — 다른 데서 건 더 센 효과는 건드리지 않음)
 */
function keepSpeed(player, now) {
  let want = -1;
  let until = now;
  for (const st of bodyBuffsOf(player)) {
    const a = st.def.speed;
    if (a === undefined) continue;
    if (a > want || (a === want && st.until > until)) {
      want = a;
      until = st.until;
    }
  }
  if (want < 0 || until - now < 2) return;
  let cur;
  try {
    cur = player.getEffect("speed");
  } catch {
    return;
  }
  if (cur && cur.amplifier >= want) return;
  effect(player, "speed", until - now, want);
}

/** 화면 하단 상태 줄의 신체 강화 · 평타 강화 (룬 · 초월 무기 줄 뒤에 붙음) */
function buffHud(player) {
  const now = system.currentTick;
  const out = [];
  for (const st of bodyBuffsOf(player)) out.push(`§6${st.def.name} §7${Math.max(0, (st.until - now) / 20).toFixed(1)}초`);
  for (const st of strikeBuffsOf(player)) out.push(`§e${st.def.name}${Number.isFinite(st.left) ? ` §f×${st.left}` : ""} §7${Math.max(0, (st.until - now) / 20).toFixed(1)}초`);
  return out.join(" §8| ");
}

const RUNE_FN = {
  groundSlam,
  whirlwind,
  fissure,
  titanSmash,
  heavenFall,
  galeSlash,
  bladeFan,
  shadowRaid,
  bladeStorm,
  shadowless,
  guardShout,
  lifeSpring,
  ironSkin,
  bulwark,
  undyingPact,
  fireball,
  frostNova,
  fireStorm,
  blizzard,
  meteor,
  // v1.4.0
  axeBoomerang,
  bloodCleave,
  leapSlam,
  execute,
  berserker,
  pierceArrow,
  pinArrow,
  backstep,
  snipe,
  skyBow,
  lightBarrier,
  purify,
  judgeHammer,
  divineSmite,
  archangel,
  chainLightning,
  blink,
  staticPrison,
  thundercloud,
  thunderSpear,
  // v1.6.0
  backstab,
  poisonStars,
  smokeBomb,
  shadowFlurry,
  moonAssassin,
  quickDraw,
  swordWave,
  counterStance,
  tripleCut,
  heavenSplit,
  // v2.13.0 마검사
  arcaneCut,
  manaBlaster,
  riftSlash,
  orbitBlades,
  spellbladeRelease,
  // v2.14.0 원소 검사 · 정령사
  flameSlash,
  frostThrust,
  thunderSlash,
  triElement,
  elementRelease,
  summonSpirits,
  gravityOrb,
  liftSlam,
  gravityWell,
  gravityReverse,
  blackHole,
  // v2.24.0 신체 강화 · 평타 강화
  fightingSpirit,
  steelBody,
  regenBody,
  warGod,
  awakening,
  edgeHone,
  flurry,
  cleave,
  bloodBlade,
  swordGod,
};

// ============================================================ 쓰기
export function runeCooldownLeft(player, r) {
  return Math.max(0, (cooldowns.get(`${player.id}|${r.id}`) ?? 0) - system.currentTick);
}

function startRuneItemCooldown(player, r) {
  try {
    const item = player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand);
    if (item?.typeId === r.id) item.getComponent("minecraft:cooldown")?.startCooldown(player);
  } catch {
    // 표시만
  }
}

/** 핫바 쓸기 표시를 풀어 줌 (1틱): 룬이 나가지 않았을 때·재사용 대기를 돌려줄 때 */
function clearRuneItemCooldown(player, r) {
  try {
    player.startItemCooldown(`rpg_rune_${r.family}_${r.grade}`, 1);
  } catch {
    // 표시만
  }
}

/** 재사용 대기 시작 (+ 핫바 쓸기 표시, 쓴 룬 이름·적용 스탯 알림) */
function startCooldown(player, r) {
  const key = `${player.id}|${r.id}`;
  cooldowns.set(key, system.currentTick + Math.round(r.cd * 20));
  readyAnnounced.delete(key);
  startRuneItemCooldown(player, r);
  notify(player, `${r.gradeColor}${r.def.name}§r §7(${runeStatLabel(runeStat(player, r.family))})`, 24);
}

/**
 * 룬 쓰기. 재사용 대기를 확인하고, 스킬이 대상이 없어 취소하면 재사용 대기 없음
 * v1.6.0 암살자 룬: 콤보 단계만큼 피해 배율, 이어 쓴 룬은 앞 룬의 마지막 타격 뒤에 나감 (그 사이 대상이 사라져 못 나가면 재사용 대기를 돌려줌)
 * @param {import("@minecraft/server").Player} player
 * @param {{quick?: boolean}} [o] quick = 칸을 골라서 쓴 것 (재사용 대기 중이면 소리 없이 알림만)
 */
export function castRune(player, r, o = {}) {
  if (!r || !player.isValid) return false;
  if (blockedBySilence(player)) return false; // v1.8.0 보스 기믹: 침묵
  if (blockedByMount(player, "skill")) return false; // v2.8.0 탈것에 탄 동안
  const left = runeCooldownLeft(player, r);
  if (left > 0) {
    const text = `§c${r.def.name} §7재사용 대기 §f${(left / 20).toFixed(1)}초`;
    if (o.quick) notify(player, text, 20);
    else warn(player, text);
    return false;
  }
  const fn = RUNE_FN[r.def.fn];
  if (!fn) return false;
  const plan = r.family === RUNE_COMBO.family ? comboPlan(player, r) : undefined;
  const P = runePower(player, r.family) * (plan?.mult ?? 1);
  const run = () => {
    try {
      return fn(player, r, P, r.def) !== false;
    } catch (e) {
      console.warn(`[rpg] rune ${r.id}: ${e}`);
      return false;
    }
  };
  const now = system.currentTick;
  if (plan && plan.start > now) {
    startCooldown(player, r);
    const st = comboCommit(player, r, plan);
    const life = lifeToken(player);
    later(plan.start - now, () => {
      if (!player.isValid) return;
      const alive = sameLife(player, life);
      if (!alive && combos.get(player.id) === st) combos.delete(player.id); // 죽거나 차원이 바뀌면 콤보도 끝
      if (alive && run()) comboLanded(player, r, st);
      else {
        comboRollback(player, r, st);
        clearRuneItemCooldown(player, r);
      }
    });
    return true;
  }
  if (!run()) return false;
  startCooldown(player, r);
  if (plan) comboLanded(player, r, comboCommit(player, r, plan));
  if (r.family === RUNE_SPELLBLADE.family) grantImbue(player, r, P); // v2.13.0 마검 부여
  return true;
}

/** 룬 칸을 고르면 바로 쓰는지: 플레이어 설정 on·off, 없으면 마지막 입력이 터치일 때 (v1.6.0) */
export function quickCastOn(player) {
  let mode;
  try {
    mode = player.getDynamicProperty(RUNE_QUICK.prop);
  } catch {
    mode = undefined;
  }
  if (mode === "on") return true;
  if (mode === "off") return false;
  try {
    return player.inputInfo?.lastInputModeUsed === "Touch";
  } catch {
    return false;
  }
}

/** 바로 쓰기 설정 (auto · on · off) */
export function setQuickMode(player, mode) {
  player.setDynamicProperty(RUNE_QUICK.prop, mode === "on" || mode === "off" ? mode : undefined);
}

/** 핫바에서 칸을 고름: 룬이면 바로 사용 (뽑기권은 누를 때만) */
export function onHotbarSelect(player, itemStack) {
  const r = parseRune(itemStack?.typeId);
  if (!r || !quickCastOn(player)) return false;
  try {
    if (player.getGameMode() === GameMode.Spectator || isDying(player)) return false;
  } catch {
    return false;
  }
  castRune(player, r, { quick: true });
  return true;
}

/** 아이템 사용 (main.js). 룬·뽑기권이면 처리하고 true */
export function onRuneItemUse(player, itemStack) {
  const id = itemStack?.typeId;
  if (id === RUNE.ticketItem || id === RUNE.advTicketItem) {
    if (recentlyTalked(player)) return true;
    useTicket(player, Math.random, id === RUNE.advTicketItem);
    return true;
  }
  const r = parseRune(id);
  if (!r) return false;
  // NPC·주민을 터치한 같은 터치는 쓰지 않음. 쓰지 않았거나 나가지 않았으면 (대상 없음 등) 재사용 대기가 없으므로,
  // 누를 때 엔진이 걸었을 수 있는 핫바 쓸기 표시도 풀어 줌 (재사용 대기 중이라 막힌 것은 그대로)
  if (recentlyTalked(player) || !castRune(player, r)) {
    if (runeCooldownLeft(player, r) === 0) clearRuneItemCooldown(player, r);
  }
  return true;
}

// ============================================================ 뽑기
/** 등급 확률 (뽑기권 종류): 일반 뽑기권 = RUNE_GRADES 의 weight, 고급 = RUNE.advWeights (v2.7.0, 일반 0) */
export function gradeWeights(adv = false) {
  return Object.fromEntries(GRADE_ORDER.map((g) => [g, adv ? (RUNE.advWeights[g] ?? 0) : RUNE_GRADES[g].weight]));
}

/**
 * 등급 확률대로 등급, 계열은 RUNE_FAMILY_ORDER 중 같은 확률 (v1.6.0: 열 계열, 10%씩 · v2.13.0: 열한 계열, 약 9.1%씩)
 * @param {() => number} [rand]
 * @param {boolean} [adv] 고급 룬 뽑기권 (v2.7.0)
 */
export function rollRune(rand = Math.random, adv = false) {
  const w = gradeWeights(adv);
  const total = GRADE_ORDER.reduce((s, g) => s + w[g], 0);
  let x = rand() * total;
  let grade = GRADE_ORDER[GRADE_ORDER.length - 1];
  for (const g of GRADE_ORDER) {
    if (w[g] <= 0) continue; // 나오지 않는 등급 (고급 뽑기권의 일반)
    if (x < w[g]) {
      grade = g;
      break;
    }
    x -= w[g];
  }
  const family = RUNE_FAMILY_ORDER[Math.min(RUNE_FAMILY_ORDER.length - 1, Math.floor(rand() * RUNE_FAMILY_ORDER.length))];
  return { family, stat: RUNE_FAMILIES[family].stat, grade };
}

/** 인벤토리에서 뽑기권 한 장 빼기 (손에 든 칸 먼저). id = 뽑기권 아이템 (일반 · 고급) */
function takeTicket(player, id = RUNE.ticketItem) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (!inv) return false;
  const order = [player.selectedSlotIndex, ...Array.from({ length: inv.size }, (_, i) => i).filter((i) => i !== player.selectedSlotIndex)];
  for (const i of order) {
    const it = inv.getItem(i);
    if (it?.typeId !== id) continue;
    if (it.amount > 1) {
      it.amount -= 1;
      inv.setItem(i, it);
    } else inv.setItem(i, undefined);
    return true;
  }
  return false;
}

export const DRAW_TICKS = 26;

/** v2.7.0: 뽑았지만 아직 받지 않은 룬 (룬 ID 목록, 플레이어 동적 속성) — 연출 중에 나가면 다시 들어올 때 받음 */
const PENDING_PROP = "rpg:rune_pending";

function pendingRunes(player) {
  try {
    const raw = player.getDynamicProperty(PENDING_PROP);
    const list = typeof raw === "string" ? JSON.parse(raw) : [];
    return Array.isArray(list) ? list.filter((x) => typeof x === "string" && parseRune(x)) : [];
  } catch {
    return [];
  }
}

function setPendingRunes(player, list) {
  try {
    player.setDynamicProperty(PENDING_PROP, list.length ? JSON.stringify(list) : undefined);
  } catch {
    // 무시
  }
}

/** 적어 둔 룬 하나를 목록에서 빼고 줌. 목록에 없으면 (이미 받음) false */
function deliverRune(player, rid) {
  const list = pendingRunes(player);
  const i = list.indexOf(rid);
  if (i < 0) return false;
  list.splice(i, 1);
  setPendingRunes(player, list);
  const r = parseRune(rid);
  if (!r) return false;
  giveItem(player, createRuneItem(r.family, r.grade));
  return true;
}

/** 들어올 때 (main.js): 지난번 연출 중에 나가서 받지 못한 룬을 줌. 지금 연출 중인 뽑기의 룬은 연출 끝에 (큰 글자 · 알림과 함께) */
export function claimPendingRunes(player) {
  const now = drawing.get(player.id);
  let skipped = false;
  for (const rid of pendingRunes(player)) {
    if (!skipped && rid === now) {
      skipped = true;
      continue;
    }
    const r = parseRune(rid);
    if (!r || !deliverRune(player, rid)) continue;
    player.sendMessage(`§6[RPG] §f지난번에 뽑은 룬을 받았습니다: ${r.gradeColor}${r.def.name}§r §7(${r.famName} 룬 · ${r.gradeName})`);
  }
}

/**
 * 뽑기권 사용: 뽑기권을 바로 쓰고, 연출(DRAW_TICKS) 뒤 룬 지급. 연출 중에는 다시 못 뽑음
 * v2.7.0: 뽑기권은 연출 전에 씀 — 연출의 색 (결과 등급) 을 보고 뽑기권을 버려 공짜로 다시 뽑을 수 없게. 뽑은 룬은 적어 두고 연출 뒤에 줌
 * @param {any} player
 * @param {() => number} [rand]
 * @param {boolean} [adv] 고급 룬 뽑기권 (v2.7.0: 일반 룬 없음 — 연출의 색도 희귀부터)
 */
export function useTicket(player, rand = Math.random, adv = false) {
  if (drawing.has(player.id)) {
    warn(player, "§7룬을 뽑는 중입니다");
    return false;
  }
  const res = rollRune(rand, adv);
  const rid = runeId(res.family, res.grade);
  if (!parseRune(rid)) return false; // 설정 표에 없는 조합 (뽑기권은 그대로)
  // 뽑은 룬을 먼저 적어 두고 (적지 못하면 뽑기권을 쓰지 않음), 뽑기권을 씀 (없으면 적은 것을 되돌림)
  const before = pendingRunes(player);
  setPendingRunes(player, [...before, rid]);
  if (pendingRunes(player).length !== before.length + 1) {
    warn(player, "§c지금은 룬을 뽑을 수 없습니다 §7(뽑기권은 그대로)");
    return false;
  }
  if (!takeTicket(player, adv ? RUNE.advTicketItem : RUNE.ticketItem)) {
    setPendingRunes(player, before);
    warn(player, `§c${adv ? RUNE.advTicketName : RUNE.ticketName}이 없습니다`);
    return false;
  }
  drawing.set(player.id, rid);
  const gi = GRADE_ORDER.indexOf(res.grade);
  const g0 = adv ? 1 : 0; // 연출의 첫 색 (고급은 희귀부터)
  const dim0 = player.dimension;
  play(dim0, "block.enchanting_table.use", player.location, 1.0, 1);
  play(dim0, "beacon.power", player.location, 1.6, 0.6);
  // 연출: 눈앞 마법진이 한 칸씩 그려지며 등급 색이 차례로 바뀌다가 결과 등급에서 멈춤 (1인칭에서도 보이게 눈높이),
  //       발밑 룬은 주위 사람에게 보이는 표시
  for (let t = 0; t < DRAW_TICKS; t += 2) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const dim = player.dimension;
      const c = player.location;
      const step = Math.min(gi, g0 + Math.floor((t / (DRAW_TICKS - 6)) * (gi + 1 - g0)));
      const col = RUNE_GRADE_FX[GRADE_ORDER[step]];
      const { at, side } = altarOf(player);
      floorRune(dim, c, { size: 1.4 + t * 0.03, life: 0.14, color: col, alpha: 0.95, progress: 1, rate: 0, spin: t * 14 });
      fx(dim, "rpg:magic_circle", at, { size: 0.55 + t * 0.012, life: 0.14, spin: t * 14, progress: Math.min(1, (t + 2) / (DRAW_TICKS - 6)), color: col, alpha: 0.95 });
      // 마법진으로 빨려 드는 빛 알갱이
      const a = t * 0.9;
      const from = add(add(at, side, Math.cos(a) * 1.3), V_UP, Math.sin(a) * 1.0);
      fx(dim, "rpg:arrow_spark", from, { dir: unit(sub(at, from)), speed: 2.6, size: 0.14, grow: 0.6, life: 0.45, color: col, alpha: 1 });
      if (t % 6 === 0) play(dim, "note.pling", c, 0.8 + step * 0.2 + t * 0.02, 0.5);
    }, t);
  }
  system.runTimeout(() => {
    drawing.delete(player.id);
    if (!player.isValid) return; // 나갔으면 다시 들어올 때 (claimPendingRunes)
    const r = parseRune(rid);
    if (!r || !deliverRune(player, rid)) return;
    revealFx(player, res, gi);
    const sn = `${r.famName} 룬`;
    try {
      showRealTitle(player, `${r.gradeColor}${r.gradeName} 룬!`, `§f${r.def.name} §7(${sn})`);
    } catch {
      // 무시
    }
    player.sendMessage(`§6[RPG] §f룬 획득: ${r.gradeColor}${r.def.name}§r §7(${sn} · ${r.gradeName}) §8- ${r.def.desc}`);
    if (gi >= GRADE_ORDER.indexOf(RUNE.broadcastFrom)) {
      try {
        world.sendMessage(`§6[RPG] §f${player.name}님이 ${r.gradeColor}${r.gradeName} 룬 ${r.def.name}§r§f을(를) 얻었습니다!`);
      } catch {
        // 무시
      }
    }
  }, DRAW_TICKS);
  return true;
}

/** 뽑기 마법진 자리: 눈앞 2.4칸, 조준점보다 조금 아래 (at), 화면 가로 방향 (side), 그 아래 땅 (ground) */
function altarOf(player) {
  const f = flat(player.getViewDirection());
  const ground = add(player.location, f, 2.4);
  return { at: up(ground, 1.45), side: rightOf(f), ground };
}

/** 결과 연출: 눈앞에 등급 색 빛기둥 + 마법진 섬광 + 폭발 + 소리 (높은 등급일수록 크게) */
function revealFx(player, res, gi) {
  const dim = player.dimension;
  const c = player.location;
  const { at, ground } = altarOf(player);
  const col = RUNE_GRADE_FX[res.grade];
  const [sg] = RUNE.color[res.family] ?? RUNE.color[res.stat];
  const h = 2 + gi * 1.2;
  // 빛기둥은 눈앞 마법진 자리에 (몸에 세우면 1인칭 카메라가 기둥 안에 들어가 안 보임)
  fx(dim, "rpg:arrow_shot", up(ground, h), { dir: V_UP, speed: 0, size: h, thick: 0.5 + gi * 0.2, life: 0.7 + gi * 0.1, color: col, alpha: 0.9 });
  fx(dim, "rpg:arrow_shot", up(ground, h), { dir: V_UP, speed: 0, size: h * 0.95, thick: 0.18 + gi * 0.06, life: 0.6 + gi * 0.1, color: WHITE, alpha: 1 });
  // 1인칭 화면을 다 덮지 않을 크기 (초월도 화면 높이의 3/4 정도)
  fx(dim, "rpg:magic_circle_flash", at, { size: 0.85 + gi * 0.15, life: 0.5 + gi * 0.08, spin: Math.random() * 360, progress: 1, color: col, alpha: 1 });
  burst(dim, at, col, 1.0 + gi * 0.3, 0.4);
  shock(dim, ground, col, 1.2 + gi * 0.4, 0.5);
  sparks(dim, at, sg, 8 + gi * 3, 4 + gi);
  floorRune(dim, c, { size: 1.8 + gi * 0.4, life: 1.0, color: col, alpha: 0.9, progress: 1, rate: 60, grow: 0.3 });
  const sound = ["random.orb", "random.levelup", "beacon.activate", "random.totem", "mob.enderdragon.growl"][gi];
  play(dim, sound, c, [1.4, 1.2, 1.2, 1.0, 0.9][gi], [0.8, 0.8, 1, 1, 0.8][gi]);
  if (gi >= 3) {
    particle(dim, "minecraft:totem_particle", up(c, 1.2));
    play(dim, "firework.twinkle", c, 1.1, 1);
  }
  if (gi >= 4) {
    for (let i = 0; i < 3; i++) system.runTimeout(() => shock(dim, c, i % 2 ? WHITE : col, 3 + i * 1.5, 0.6), 3 + i * 3);
    play(dim, "firework.large_blast", c, 1.0, 1);
  }
}

// ============================================================ 재사용 대기 표시
function hudTick() {
  for (const player of world.getAllPlayers()) {
    let r;
    try {
      r = parseRune(heldTypeId(player));
    } catch {
      r = undefined;
    }
    // 준비 알림 (들고 있지 않아도)
    for (const key of cooldowns.keys()) {
      if (!key.startsWith(`${player.id}|`) || readyAnnounced.has(key)) continue;
      if ((cooldowns.get(key) ?? 0) > system.currentTick) continue;
      readyAnnounced.add(key);
      if (r && key === `${player.id}|${r.id}`) {
        try {
          player.playSound("random.orb", { pitch: 1.6, volume: 0.35 });
        } catch {
          // 무시
        }
      }
    }
    if (!actionbarFree(player) || promptedRecently(player)) continue;
    if (!r) {
      // v2.13.0 마검 부여 · v2.14.0 정령 · v2.24.0 신체 강화 · 평타 강화: 룬도 초월 무기 (skills.js 가 스킬 줄 뒤에 붙임) 도 아닌 것을 들고 있으면 그것만
      const imb = [imbueHud(player), spiritHud(player), buffHud(player), duelHud(player)].filter(Boolean).join(" §8| "); // v2.26.0 결투
      if (!imb || heldSkillWeapon(player)) continue;
      try {
        player.onScreenDisplay.setActionBar(imb);
      } catch {
        // 무시
      }
      continue;
    }
    const left = runeCooldownLeft(player, r);
    const e = runeStat(player, r.family);
    const state = left > 0 ? `§c${(left / 20).toFixed(1)}초` : "§a준비";
    const sh = shieldOf(player);
    let combo = "";
    if (r.family === RUNE_COMBO.family) {
      const st = comboOf(player);
      combo = ` §8| §d암살자 ${ownedComboRunes(player).size}/${GRADE_ORDER.length}${st && st.chain.length >= 2 ? ` §l${st.chain.length} 콤보` : ""}`;
    }
    try {
      player.onScreenDisplay.setActionBar(`§7룬 ${r.gradeColor}${r.def.name}§r ${state} §8| §7${runeStatLabel(e, "×")}${sh > 0 ? ` §8| §6보호막 ${Math.ceil(sh)}` : ""}${combo}${hudSuffix(player)}`);
    } catch {
      // 무시
    }
  }
}

/** 보호막이 깨짐: 유리 깨지는 소리 + 금빛 조각 */
function onShieldBreak(player) {
  const dim = player.dimension;
  const at = up(player.location, 1.0);
  const [glow, core] = RUNE.color.holy;
  burst(dim, at, glow, 1.3, 0.3);
  sparks(dim, at, core, 10, 5);
  play(dim, "random.glass", at, 1.2, 0.8);
  notify(player, "§6보호막이 깨졌습니다", 24);
}

const shieldFxAt = new Map();
/** 보호막이 피해를 막음: 작은 금빛 고리 (너무 자주 하지 않게) */
function onShieldAbsorb(player) {
  const now = system.currentTick;
  if (now - (shieldFxAt.get(player.id) ?? -100) < 5) return;
  shieldFxAt.set(player.id, now);
  const [glow] = RUNE.color.holy;
  ring(player.dimension, up(player.location, 1.0), player.getViewDirection(), glow, 1.0, 0.3);
  play(player.dimension, "item.shield.block", player.location, 1.4, 0.6);
}

/** 보호막이 있는 동안 몸 둘레 금빛 고리 (다른 사람에게 보임) */
function shieldTick() {
  const [glow] = RUNE.color.holy;
  for (const player of world.getAllPlayers()) {
    if (shieldOf(player) <= 0) continue;
    try {
      fx(player.dimension, "rpg:bash_ring", up(player.location, 1.0), { dir: player.getViewDirection(), size: 0.85, life: 0.5, color: glow, alpha: 0.45 });
    } catch {
      // 무시
    }
  }
}

export function registerRunes() {
  setUndying(isUndying, onUndyingSaved);
  setShieldHooks(onShieldBreak, onShieldAbsorb);
  system.runInterval(hudTick, RUNE.hudEvery);
  system.runInterval(shieldTick, 10);
  system.runInterval(markTick, 10); // v1.6.0 암살 표식
  // v2.13.0 마검 부여: 평타·활·완드 피해에 더함 (combat.js), 들어가면 한 번 씀 + 마력 검기, 상태 줄 덧붙임, 손 둘레 빛
  addWeaponBonusHook(imbueBonus);
  addWeaponHitHook(imbueLanded);
  addHudSuffix(imbueHud);
  system.runInterval(imbueTick, 4);
  // v2.24.0 평타 강화: 평타 · 활 · 완드 피해에 더함 (combat.js) · 들어가면 한 번 쓰고 덧붙임 · 치명타 + · 활 · 완드 재사용 배율
  //         신체 강화 · 평타 강화: 끝 · 회복 · 연출 (2틱마다), 상태 줄 덧붙임
  addWeaponBonusHook(strikeBonus);
  addWeaponHitHook(strikeLanded);
  addCritBonusHook(strikeCrit);
  addRangedCooldownHook(strikeRangedScale);
  addHudSuffix(buffHud);
  system.runInterval(buffTick, 1);
  // v2.14.0 정령사: 정령 움직이기·그리기·공격 (2틱마다), 상태 줄 덧붙임
  addHudSuffix(spiritHud);
  system.runInterval(spiritTick, 2);
  addHudSuffix(duelHud); // v2.26.0 결투 상대 · 남은 체력 (룬 · 초월 무기 줄 뒤에)
  // 정령: 싸운 중립 몹 적기 (before: 보스 소품처럼 피해가 취소되는 것도) · 몹이 피해를 받은 틱 (무적 시간 피하기)
  world.beforeEvents.entityHurt.subscribe((ev) => {
    try {
      noteFight(ev);
    } catch {
      // 무시
    }
  });
  world.afterEvents.entityHurt.subscribe((ev) => {
    try {
      hurtAt.set(ev.hurtEntity.id, system.currentTick);
    } catch {
      // 무시
    }
  });
  // v2.15.0 중력: 띄운 몹의 낙하 피해를 막음 · 오래된 기록 지우기
  world.beforeEvents.entityHurt.subscribe((ev) => {
    try {
      noFallAfterLift(ev);
    } catch {
      // 무시
    }
  });
  system.runInterval(gravityTick, 20);
  // v1.6.0 삶 표시: 진짜 죽음(토템으로 산 것 제외)·차원 이동이면 콤보·표식이 끝나고, 나중에 나갈 룬·순간이동이 멈춤
  world.afterEvents.entityDie.subscribe(
    (ev) => {
      if (ev.deadEntity?.typeId !== "minecraft:player") return;
      onLifeEnded(ev.deadEntity.id);
      clearBody(ev.deadEntity); // v2.24.0 신체 강화도 끝 (모든 스탯 · 밀려나지 않음 — 차원 이동 · 탈것으로는 끝나지 않음)
      deaths.set(ev.deadEntity.id, (deaths.get(ev.deadEntity.id) ?? 0) + 1); // v2.24.0 광전사의 혼 · 대천사 강림 · 불사의 맹약도 끝
      undying.delete(ev.deadEntity.id);
    },
    { entityTypes: ["minecraft:player"] },
  );
  world.afterEvents.playerDimensionChange.subscribe((ev) => onLifeEnded(ev.player.id));
  // v1.6.0: 룬 칸을 고르면 바로 사용 (터치, /runequick)
  world.afterEvents.playerHotbarSelectedSlotChange.subscribe((ev) => {
    try {
      onHotbarSelect(ev.player, ev.itemStack);
    } catch (e) {
      console.warn(`[rpg] rune quick cast: ${e}`);
    }
  });
}

export function forgetRunes(playerId) {
  for (const key of cooldowns.keys()) if (key.startsWith(`${playerId}|`)) cooldowns.delete(key);
  for (const key of readyAnnounced) if (key.startsWith(`${playerId}|`)) readyAnnounced.delete(key);
  drawing.delete(playerId);
  undying.delete(playerId);
  undyingFxAt.delete(playerId);
  shieldFxAt.delete(playerId);
  combos.delete(playerId);
  for (const key of marks.keys()) if (key.startsWith(`${playerId}|`)) marks.delete(key);
  lives.delete(playerId);
  imbues.delete(playerId);
  spirits.delete(playerId);
  spiritHitAt.delete(playerId);
  fought.delete(playerId);
  bodyBuffs.delete(playerId);
  strikeBuffs.delete(playerId);
  deaths.delete(playerId);
  auraDrawnAt.delete(playerId);
}
