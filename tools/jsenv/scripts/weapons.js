import { EquipmentSlot, ItemStack } from "@minecraft/server";
import {
  BOW,
  CONFIG,
  CRIT_CAP,
  GRADES,
  LEVEL_DAMAGE,
  LOOK,
  SHIELD,
  SHOP,
  SKILLS,
  STAT_INFO,
  TYPE_ONLY,
  TYPES,
  WAND,
  WEAPON_NAMES,
  damageScaling,
  enhanceBonus,
  isValidWeapon,
  nearestGradeLevel,
  requiredStats,
} from "./config.js";

// v2.16.0 greatsword = 마검 (티르빙, 표 밖의 제작 전용 무기 — config.js TYPE_ONLY 의 조합만 무기)
const ID_RE = /^rpg:(sword|dagger|axe|shield|bow|wand|greatsword)_l(\d+)_([a-z]+)$/;
/** @type {Map<string, any>} */
const cache = new Map();

export const STAT_BOOK_ID = "rpg:stat_book";

export function round1(x) {
  return Math.round(x * 10) / 10;
}

export function pct(x) {
  return `${round1(x * 100)}%`;
}

/**
 * 아이템 ID로 무기 정보를 돌려준다. RPG 무기가 아니면 undefined.
 * @param {string | undefined} typeId
 */
export function parseWeapon(typeId) {
  if (!typeId || !typeId.startsWith("rpg:")) return undefined;
  if (cache.has(typeId)) return cache.get(typeId) ?? undefined;
  const m = ID_RE.exec(typeId);
  let w = null;
  if (m) {
    const level = Number(m[2]);
    const gradeIndex = GRADES.findIndex((g) => g.id === m[3]);
    const only = TYPE_ONLY[m[1]];
    if (gradeIndex >= 0 && level in LEVEL_DAMAGE && (!only || isValidWeapon(level, GRADES[gradeIndex].id, m[1]))) w = makeWeapon(m[1], level, gradeIndex);
  }
  cache.set(typeId, w);
  return w ?? undefined;
}

export function weaponFrom(type, level, gradeId) {
  return parseWeapon(`rpg:${type}_l${level}_${gradeId}`);
}

/** 이전 버전 조합(영웅 0~20렙 등)이면 같은 등급의 가장 가까운 레벨 무기로 (퀘스트 보상 등 새로 줄 때) */
export function currentWeapon(w) {
  if (!w || !w.legacy) return w;
  return weaponFrom(w.type, nearestGradeLevel(w.level, w.grade.id), w.grade.id) ?? w;
}

function makeWeapon(type, level, gradeIndex) {
  const t = TYPES[type];
  const g = GRADES[gradeIndex];
  const reqs = requiredStats(level, gradeIndex, type);
  const look = LOOK[`${type}_${g.id}`];
  const title = WEAPON_NAMES[type]?.[g.id] ?? `${g.name} ${t.name}`; // v1.2.0: 디자인 이름
  const legacy = !isValidWeapon(level, g.id, type);
  const w = {
    id: `rpg:${type}_l${level}_${g.id}`,
    type,
    level,
    gradeIndex,
    grade: g,
    typeDef: t,
    stat: t.stat,
    req: reqs[0].value, // 주 스탯 요구치
    reqs, // 모든 요구 스탯 [{stat, value}] (도끼는 힘 + 체력)
    scaling: damageScaling(type), // 피해를 올리는 스탯 [{stat, per}] (도끼는 힘 + 체력)
    icon: `textures/items/rpg/${type}/${type}_l${level}_${g.id}`,
    title,
    name: `${g.color}${title}§r§7 [${level}렙]`,
    darkName: `${g.dark}${title}§r§8 [${level}렙]`,
    typeLabel: look?.label ?? t.name, // 설명 첫 줄의 종류 이름 (궁니르는 "창")
    look: look?.slash, // 연출만 다른 무기 (궁니르: 찌르기)
    legacy, // 이전 버전 조합 (영웅 0~20렙, 초월 0~30렙): 그대로 쓰지만 새로 얻을 수 없음
    // 초월 무기 스킬 두 개 (v1.2.0, 지금 규칙의 40렙 초월 무기만)
    skills: g.id === "transcend" && !legacy ? SKILLS[type] : undefined,
    damage: 0,
    critChance: 0,
    critMult: 1,
    passive: 0,
    block: 0,
  };
  w.damage = round1(LEVEL_DAMAGE[level] * t.damage * g.mult);
  // 치명타는 무기 종류·레벨·등급으로만 정해진다 (스탯은 치명타를 올리지 않음)
  w.critChance = Math.min(CRIT_CAP, t.crit + level * t.critPerLevel + gradeIndex * t.critPerGrade);
  w.critMult = Math.round((t.critMult + level * t.critMultPerLevel + gradeIndex * t.critMultPerGrade) * 100) / 100;
  if (type === "shield") {
    // 방패는 때리는 무기이면서 (v1.1.7) 들고 있으면 받는 피해를 줄이고, 웅크리면 정면 공격을 막는다
    w.passive = Math.min(SHIELD.passiveCap, SHIELD.passiveBase + level * SHIELD.passivePerLevel + gradeIndex * SHIELD.passivePerGrade);
    w.block = Math.min(SHIELD.blockCap, SHIELD.blockBase + level * SHIELD.blockPerLevel + gradeIndex * SHIELD.blockPerGrade);
  }
  return w;
}

export function shopPrice(w) {
  const base = SHOP.basePrice[w.level] ?? 1;
  return Math.max(1, Math.ceil(base * (SHOP.gradePriceMult[w.grade.id] ?? 1) * (SHOP.typePriceMult[w.type] ?? 1)));
}

/**
 * 무기 설명 줄. dark=true 이면 밝은 배경(상자 창 본문)용 색을 쓴다.
 * 아이템 설명(lore)은 한 줄 50자 제한이 있어서(한글·§ 기호는 바이트로 셀 수도 있음) 줄을 짧게 나눈다.
 */
/**
 * 요구 스탯 글자 (예: "§c힘 22§7 · §d체력 11")
 * @param {{stat: string, value: number}[]} reqs
 */
export function reqsText(reqs, dark = false, sep = dark ? "§8 · " : "§7 · ") {
  return reqs.map((r) => `${dark ? STAT_INFO[r.stat].dark : STAT_INFO[r.stat].color}${STAT_INFO[r.stat].name} ${r.value}`).join(sep);
}

/** 이 무기에 레벨 조건이 있는지 (v2.6.2 렙제: 0렙 무기는 없음) */
export function needsLevel(w) {
  return !!CONFIG.WEAPON_LEVEL_REQUIRED && (w?.level ?? 0) > 0;
}

/**
 * 무기를 쓰는 데 모자란 것 (v2.6.2). lv = 레벨 조건에 쓰는 레벨 (최고 경험치 레벨), s = 직접 찍은 스탯 (갑옷 보너스 제외).
 * 모두 채웠으면 빈 목록
 * @param {any} w
 * @param {number} lv
 * @param {Record<string, number>} s
 * @returns {{level?: number, stat?: string, value: number}[]}
 */
export function weaponLack(w, lv, s) {
  /** @type {{level?: number, stat?: string, value: number}[]} */
  const out = [];
  if (needsLevel(w) && lv < w.level) out.push({ level: w.level, value: w.level });
  if (CONFIG.REQUIREMENT_ENABLED) for (const r of w.reqs) if ((s[r.stat] ?? 0) < r.value) out.push({ stat: r.stat, value: r.value });
  return out;
}

/** v2.6.2: 이 무기를 아직 쓸 수 없는 레벨이면 " §7(레벨 40부터 사용 · 지금 최고 레벨 32)", 아니면 "" (지급·구매 알림 끝에 붙임) */
export function levelNote(w, lv) {
  return needsLevel(w) && lv < w.level ? ` §7(레벨 ${w.level}부터 사용 · 지금 최고 레벨 ${lv})` : "";
}

/**
 * @param {any} w
 * @param {boolean} [dark]
 * @param {number} [enh] 강화 단계 (v1.5.0): 공격력 줄에 강화 뒤 값과 강화 줄을 더함
 */
export function weaponInfo(w, dark = false, enh = 0) {
  const L = dark ? "§8" : "§7"; // 항목 이름 색
  const V = dark ? "§0" : "§f"; // 값 색
  const lines = [];
  lines.push(`§r${L}${w.typeLabel} · 레벨 ${w.level} · ${dark ? w.grade.dark : w.grade.color}${w.grade.name}`);
  if (w.type === "shield") lines.push(`§r${L}오른손에 들고 때리는 무기`);
  const label = w.type === "wand" ? "마법 피해" : w.type === "bow" ? "화살 피해" : "공격력";
  if (enh > 0) {
    lines.push(`§r${L}${label} ${V}${round1(w.damage * (1 + enhanceBonus(enh)))}${L} (기본 ${w.damage})`);
    lines.push(`§r${dark ? "§6" : "§6"}강화 +${enh}${L} · 피해 +${Math.round(enhanceBonus(enh) * 100)}%`);
  } else lines.push(`§r${L}${label} ${V}${w.damage}`);
  if (w.type === "wand") {
    lines.push(`§r${L}사거리 ${V}${WAND.range}칸${L} · 재사용 ${V}${WAND.cooldownTicks / 20}초`);
    lines.push(`§r${L}시전 ${V}${WAND.castTicks / 20}초${L} (선딜레이)`);
  }
  if (w.type === "bow") lines.push(`§r${L}사거리 ${V}${BOW.range}칸${L} · 재사용 ${V}${BOW.cooldownTicks / 20}초`);
  lines.push(`§r${L}치명타 ${V}${pct(w.critChance)}${L} · 배율 ${V}×${w.critMult}`);
  for (const b of w.scaling) {
    if (b.per > 0) lines.push(`§r${L}${STAT_INFO[b.stat].name} 1포인트당 피해 ${V}+${round1(b.per * 100)}%`);
  }
  // v2.16.0 마검 (티르빙): 스킬은 검 (공격 피해) + 마력 (마법 피해, 파란 숫자)
  if (w.type === "greatsword") {
    lines.push(`§r${L}스킬: 검(공격) + ${dark ? "§1" : "§9"}마력(마법)`);
    lines.push(`§r${L}스킬 피해 × 최대 체력 ÷ ${V}${SKILLS.tyrfing?.hpBase ?? 100}`); // v2.17.0 (사용자 선택: 내 최대 체력 비례, 스킬에만)
  }
  if (w.type === "shield") {
    lines.push(`§r${L}피해 감소 ${V}${pct(w.passive)}`);
    lines.push(`§r${L}체력 1포인트당 감소 ${V}+${round1(SHIELD.passivePerVit * 100)}%p`);
    lines.push(`§r${L}웅크려 막기 ${V}${pct(w.block)}`);
    lines.push(`§r${L}때리면 ${V}${SHIELD.bashBlockLockTicks / 20}초${L} 동안 막기 풀림`);
  }
  if (needsLevel(w)) lines.push(`§r${L}요구 레벨: ${V}${w.level}`); // v2.6.2 렙제 (최고 경험치 레벨 기준)
  if (CONFIG.REQUIREMENT_ENABLED) lines.push(`§r${L}요구 스탯: ${reqsText(w.reqs, dark)}`);
  if (w.skills) lines.push(...skillLines(w, dark));
  if (w.legacy) lines.push("§r§8구버전 무기 (새로 얻을 수 없음)"); // 50바이트 안
  return lines;
}

/**
 * 초월 무기 스킬 설명 (조작 한 줄 + 스킬마다 이름·재사용 대기, 설명). 한 줄 50바이트 안
 * @param {any} w
 */
export function skillLines(w, dark = false) {
  const L = dark ? "§8" : "§7";
  const N = dark ? "§0" : "§f";
  const B = dark ? "§3" : "§b";
  const ranged = w.type === "bow" || w.type === "wand";
  const out = [ranged ? `§r${L}웅크리면: 스킬1, 길게: 스킬2` : `§r${L}버튼: 스킬1, 웅크리면: 스킬2`];
  w.skills.forEach((k, i) => {
    out.push(`§r${B}스킬${i + 1} ${N}${k.name} ${L}${k.cd}초`);
    out.push(`§r${L} ${k.desc}`);
    if (k.desc2) out.push(`§r${L} ${k.desc2}`); // v2.16.0 둘째 줄 (스킬마다 — 예: v2.23.0 티르빙 망자의 각성 "체력 10% 대가 · 쓰러뜨리면 반환")
  });
  return out;
}

const LORE_MAX_BYTES = 50;
const LORE_MAX_LINES = 20;

function utf8Bytes(ch) {
  const c = ch.codePointAt(0) ?? 0;
  return c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
}

/** 한 줄을 UTF-8 기준 50바이트 안으로 자른다 (글자 수 기준보다 항상 안전). */
function fitLoreLine(line) {
  let out = "";
  let n = 0;
  for (const ch of line) {
    const b = utf8Bytes(ch);
    if (n + b > LORE_MAX_BYTES) break;
    out += ch;
    n += b;
  }
  return out.endsWith("§") ? out.slice(0, -1) : out;
}

/**
 * 아이템 설명이 lines 와 같은지. setLoreSafe 가 자르는 대로 (줄 수·줄 길이) 맞춰 비교한다:
 * 긴 줄을 그대로 비교하면 매번 달라 보여 들고 있는 동안 계속 다시 쓰게 되므로 (v1.6.0)
 * @param {ItemStack} item
 * @param {string[]} lines
 */
export function loreMatches(item, lines) {
  let have;
  try {
    have = item.getLore();
  } catch {
    return true;
  }
  const want = lines.slice(0, LORE_MAX_LINES).map(fitLoreLine);
  return have.length === want.length && have.every((line, i) => line === want[i]);
}

/**
 * 설명을 붙인다. 실패해도 예외를 던지지 않고 false 를 돌려준다 (설명은 표시용일 뿐).
 * @param {ItemStack} item
 * @param {string[]} lines
 */
export function setLoreSafe(item, lines) {
  try {
    item.setLore(lines.slice(0, LORE_MAX_LINES).map(fitLoreLine));
    return true;
  } catch (e) {
    console.warn(`[rpg] lore: ${e}`);
    return false;
  }
}

/** 플레이어가 마지막으로 손에 든 RPG 무기 (스탯 창의 기준 무기) @type {Map<string, string>} */
const lastHeld = new Map();

export function noteHeldWeapon(player, id) {
  lastHeld.set(player.id, id);
}

export function forgetHeldWeapon(playerId) {
  lastHeld.delete(playerId);
}

/**
 * 스탯 창에서 실제 피해를 보여 줄 무기:
 * 손에 든 RPG 무기 → 마지막으로 든 RPG 무기(가지고 있을 때) → 가진 무기 중 공격력이 가장 높은 것 (v2.6.2: 쓸 수 있는 무기 먼저) → 왼손 방패
 * @param {import("@minecraft/server").Player} player
 * @param {(w: any) => boolean} [usable] 지금 쓸 수 있는 무기인지 (레벨·요구 스탯)
 */
export function referenceWeapon(player, usable = () => true) {
  let eq;
  try {
    eq = player.getComponent("minecraft:equippable");
  } catch {
    eq = undefined;
  }
  const main = parseWeapon(eq?.getEquipment(EquipmentSlot.Mainhand)?.typeId);
  if (main) return main;
  const off = parseWeapon(eq?.getEquipment(EquipmentSlot.Offhand)?.typeId);
  const last = lastHeld.get(player.id);
  if (off && off.id === last) return off;
  const inv = player.getComponent("minecraft:inventory")?.container;
  let best;
  for (let i = 0; inv && i < inv.size; i++) {
    const w = parseWeapon(inv.getItem(i)?.typeId);
    if (!w) continue;
    if (w.id === last) return w;
    // 쓸 수 있는 무기가 먼저, 같으면 공격력이 높은 것 (아직 쓰지 못하는 높은 레벨 무기가 기준이 되어 "피해 0" 만 보이지 않게)
    if (!best) {
      best = w;
      continue;
    }
    const a = usable(w);
    const b = usable(best);
    if (a !== b ? a : w.damage > best.damage) best = w;
  }
  return best ?? off;
}

/** 설명이 붙은 무기 아이템을 만든다. */
export function createWeaponItem(w) {
  const item = new ItemStack(w.id, 1);
  setLoreSafe(item, weaponInfo(w));
  return item;
}

// ------------------------------------------------------------ 강화 (v1.5.0)
export const ENHANCE_PROP = "rpg:enhance";

/** 아이템의 강화 단계 (없으면 0) */
export function enhanceOf(item) {
  try {
    const v = item?.getDynamicProperty(ENHANCE_PROP);
    return typeof v === "number" && v > 0 ? Math.min(10, Math.floor(v)) : 0;
  } catch {
    return 0;
  }
}

/** 모루로 붙인 이름 (강화 이름 "+7 내 검 [40렙]" 에 씀) */
export const NAME_PROP = "rpg:name";
/** enhancedName 이 만든 이름인지 (§r + 등급 색 + "+숫자 ") */
const ENHANCED_NAME_RE = /^§r(?:§.)+\+\d+ /;

function customNameOf(item) {
  try {
    const v = item.getDynamicProperty(NAME_PROP);
    return typeof v === "string" && v ? v : undefined;
  } catch {
    return undefined;
  }
}

/** 강화된 무기 이름 (예: "+7 그람 [40렙]", 모루 이름이 있으면 "+7 내 검 [40렙]") */
export function enhancedName(w, n, custom) {
  return `§r${w.grade.color}+${n} ${custom ?? w.title}§r§7 [${w.level}렙]`;
}

/**
 * 아이템 이름을 강화 단계에 맞춘다. 바꿨으면 true (칸에 다시 넣어야 반영)
 * - 모루로 붙인 이름은 기억해 두고 강화 이름 가운데에 씀 (+0 이면 그 이름 그대로)
 * - 모루에서 이름을 지우면 무기 이름으로 돌아감
 * @param {ItemStack} item
 */
export function syncEnhanceName(item, w, n) {
  const tag = item.nameTag || undefined;
  let custom = customNameOf(item);
  if (tag && !ENHANCED_NAME_RE.test(tag)) {
    custom = tag.slice(0, 40); // 새로 붙인 모루 이름
  } else if (!tag && custom && n > 0) {
    custom = undefined; // 강화 무기의 이름을 모루에서 지움
  }
  if (custom !== customNameOf(item)) {
    try {
      item.setDynamicProperty(NAME_PROP, custom);
    } catch {
      // 겹치는 아이템 등 (무기는 한 칸 1개라 없음)
    }
  }
  const want = n > 0 ? enhancedName(w, n, custom) : custom;
  if (tag === want) return false;
  item.nameTag = want;
  return true;
}

/**
 * 아이템에 강화 단계를 적고 이름·설명을 맞춘다 (0 이면 지움, 모루 이름은 유지). 아이템을 돌려줌 (칸에 다시 넣어야 반영)
 * @param {ItemStack} item
 */
export function writeEnhance(item, w, n) {
  item.setDynamicProperty(ENHANCE_PROP, n > 0 ? n : undefined);
  syncEnhanceName(item, w, n);
  setLoreSafe(item, weaponInfo(w, false, n));
  return item;
}

/** 스탯 창 기준 무기의 강화 단계: 손에 든 것 → 가진 것 중 가장 높은 것 */
export function referenceEnhance(player, w) {
  if (!w) return 0;
  let best = 0;
  try {
    const main = player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand);
    if (main?.typeId === w.id) return enhanceOf(main);
    const inv = player.getComponent("minecraft:inventory")?.container;
    for (let i = 0; inv && i < inv.size; i++) {
      const it = inv.getItem(i);
      if (it?.typeId === w.id) best = Math.max(best, enhanceOf(it));
    }
  } catch {
    return best;
  }
  return best;
}

export const STAT_BOOK_LORE = ["§r§7손에 들면 스탯 창이 열립니다.", "§r§7(누르기로도 열 수 있음)", "§r§7아랫줄: 엔더 상자 · 탈것 소환"]; // v2.8.0 탈것, v2.10.0 엔더 상자 (가진 책도 다음 확인 때 바뀜)

export function createStatBook() {
  const item = new ItemStack(STAT_BOOK_ID, 1);
  setLoreSafe(item, STAT_BOOK_LORE);
  return item;
}
