import { EquipmentSlot, ItemStack, system, world } from "@minecraft/server";
import {
  ARMOR_GRADE_LEVELS,
  ARMOR_LEVELS,
  ARMOR_PIECE_ORDER,
  ARMOR_PIECES,
  ARMOR_SHOP,
  ARMOR_TIERS,
  ARMOR_TYPE_ORDER,
  ARMOR_TYPES,
  CONFIG,
  GRADES,
  LEVELS,
  SHOP,
  STAT_INFO,
  armorId,
  reqUnit,
  splitReq,
} from "./config.js";
import { ChestForm, withFormLock } from "./chestui.js";
import { notify, particle } from "./fx.js";
import { isRpgMode } from "./hp.js";
import { SELL_TAB, bagFull, countItem, fitsAfter, giveItem, removeItem, sellLoop, sellTabSlot, shopLoop } from "./shop.js";
import { ensureStats, getBaseStats, reqLevelOf, setStatBonus } from "./stats.js";
import { setLoreSafe } from "./weapons.js";

/**
 * 갑옷 (v1.5.0). 수치·이름: config.js 의 ARMOR_TYPES · ARMOR_TIERS · ARMOR_PIECES
 * - ID rpg:armor_<종류>_l<레벨>_<등급>_<부위> (종류 str·dex·vit·strvit·int, 등급 common·transcend, 부위 helmet·chest·legs·boots)
 * - 입으면: 방어 포인트(아이템의 minecraft:wearable, 바닐라 계산) + 스탯 보너스 (stats.js setStatBonus → 피해·최대 체력·룬)
 * - 요구 조건(최고 레벨·직접 찍은 스탯)이 모자라면 0.5초 안에 벗겨서 인벤토리로 (가득 차면 발밑)
 * - 상점: 무기 상점 윗줄 오른쪽 칸 → 갑옷 상점 (그 레벨 이하의 가장 가까운 갑옷 레벨)
 */

const ID_RE = /^rpg:armor_(str|dex|vit|strvit|int)_l(\d+)_(common|transcend)_(helmet|chest|legs|boots)$/;
/** @type {Map<string, any>} */
const cache = new Map();

/** 세트 보너스를 부위별로 나눔 (합이 정확히 맞게 큰 나머지부터 1씩) → {부위: {스탯: 값}} */
function splitBonus(type, total) {
  const T = ARMOR_TYPES[type];
  /** @type {Record<string, Record<string, number>>} */
  const out = Object.fromEntries(ARMOR_PIECE_ORDER.map((p) => [p, {}]));
  for (const [stat, k] of Object.entries(T.stats)) {
    const sum = Math.round(total * k);
    const raw = ARMOR_PIECE_ORDER.map((p) => sum * ARMOR_PIECES[p].share);
    const got = raw.map(Math.floor);
    let left = sum - got.reduce((a, b) => a + b, 0);
    const order = raw.map((v, i) => ({ i, r: v - Math.floor(v) })).sort((a, b) => b.r - a.r || a.i - b.i);
    for (let j = 0; left > 0; j++, left--) got[order[j % order.length].i]++;
    ARMOR_PIECE_ORDER.forEach((p, i) => {
      if (got[i] > 0) out[p][stat] = got[i];
    });
  }
  return out;
}

/** 아이템 ID → 갑옷 정보 (갑옷이 아니면 undefined) */
export function parseArmor(typeId) {
  if (!typeId || !typeId.startsWith("rpg:armor_")) return undefined;
  if (cache.has(typeId)) return cache.get(typeId) ?? undefined;
  const m = ID_RE.exec(typeId);
  const a = m ? makeArmor(m[1], Number(m[2]), m[3], m[4]) : undefined;
  cache.set(typeId, a ?? null);
  return a;
}

export function armorFrom(type, level, grade, piece) {
  return parseArmor(armorId(type, level, grade, piece));
}

function makeArmor(type, level, grade, piece) {
  const T = ARMOR_TYPES[type];
  const tier = ARMOR_TIERS[`${grade}_${level}`];
  const P = ARMOR_PIECES[piece];
  const gradeIndex = GRADES.findIndex((g) => g.id === grade);
  if (!T || !tier || !P || gradeIndex < 0 || !(ARMOR_GRADE_LEVELS[grade] ?? []).includes(level)) return undefined;
  const g = GRADES[gradeIndex];
  const pi = ARMOR_PIECE_ORDER.indexOf(piece);
  // 요구 스탯: 무기와 같은 요구 단위 × 종류 배율 (v1.8.2, 40렙 일반 100 · 초월 110)
  const reqs = splitReq(
    reqUnit(level, gradeIndex),
    Object.entries(T.req).map(([stat, k]) => /** @type {[string, number]} */ ([stat, Number(k)])),
  ).filter((r) => r.value > 0);
  const levelText = grade === "transcend" ? "초월" : `${level}렙`;
  return {
    id: armorId(type, level, grade, piece),
    type,
    typeName: T.name,
    level,
    grade: g,
    gradeIndex,
    piece,
    pieceName: P.name,
    slot: P.slot,
    set: tier.set,
    setName: tier.name,
    prot: Math.max(1, Math.round(tier.prot[pi] * T.prot)),
    setProt: tier.prot.reduce((sum, v) => sum + Math.max(1, Math.round(v * T.prot)), 0),
    bonus: splitBonus(type, tier.bonus)[piece],
    setBonus: Object.fromEntries(Object.entries(T.stats).map(([s, k]) => [s, Math.round(tier.bonus * k)])),
    reqLevel: level,
    reqs,
    icon: `textures/items/rpg/armor/${type}_l${level}_${grade}_${piece}`,
    title: `${tier.name} ${P.name}`,
    name: `${g.color}${tier.name} ${P.name}§r§7 · ${T.name} [${levelText}]`,
    darkName: `${g.dark}${tier.name} ${P.name}§r§8 · ${T.name} [${levelText}]`,
  };
}

/** 스탯 보너스 글자 (예: "힘 +4 · 체력 +4") */
export function bonusText(b, dark = false) {
  const V = dark ? "§0" : "§f";
  return Object.entries(b)
    .filter(([, v]) => v > 0)
    .map(([s, v]) => `${dark ? STAT_INFO[s].dark : STAT_INFO[s].color}${STAT_INFO[s].name} ${V}+${v}`)
    .join(dark ? "§8 · " : "§7 · ");
}

/** 요구 조건 글자 (예: "레벨 30 · 힘 30", 색 없음) */
export function armorReqText(a) {
  return [`레벨 ${a.reqLevel}`, ...a.reqs.map((r) => `${STAT_INFO[r.stat].name} ${r.value}`)].join(" · ");
}

/** 스탯 보너스 글자, 색 없음 (아이템 설명용: 힘+체력 갑옷도 한 줄 50바이트 안에 들어가게) */
function bonusPlain(b) {
  return Object.entries(b)
    .filter(([, v]) => v > 0)
    .map(([s, v]) => `${STAT_INFO[s].name} +${v}`)
    .join(" · ");
}

/** 갑옷 설명 (한 줄 50바이트 안). 2줄: 이 부위, 3줄: 네 부위를 다 입었을 때 */
export function armorInfo(a, dark = false) {
  const L = dark ? "§8" : "§7";
  const V = dark ? "§0" : "§f";
  const piece = bonusPlain(a.bonus);
  const set = bonusPlain(a.setBonus);
  return [
    `§r${L}${a.typeName} 갑옷 · ${a.pieceName} · ${dark ? a.grade.dark : a.grade.color}${a.grade.name}`,
    `§r${L}방어 ${V}+${a.prot}${L}${piece ? ` · ${piece}` : ""}`,
    `§r${L}세트 방어 ${a.setProt}${set ? ` · ${set}` : ""}`,
    `§r${L}요구: ${armorReqText(a)}`,
    `§r§8요구 조건이 모자라면 벗겨짐`,
  ];
}

/** 설명이 붙은 갑옷 아이템 */
export function createArmorItem(a) {
  const item = new ItemStack(a.id, 1);
  setLoreSafe(item, armorInfo(a));
  return item;
}

/** 요구 조건을 채웠는지 (최고 경험치 레벨 — v2.6.2 무기와 같은 reqLevelOf: 막 오른 레벨도 바로, 직접 찍은 스탯). 모자란 것 목록 */
export function armorLack(player, a) {
  const s = getBaseStats(player);
  const out = [];
  if (reqLevelOf(player) < a.reqLevel) out.push(`레벨 ${a.reqLevel}`);
  for (const r of a.reqs) if ((s[r.stat] ?? 0) < r.value) out.push(`${STAT_INFO[r.stat].name} ${r.value}`);
  return out;
}

// ------------------------------------------------------------ 입고 있는 갑옷
/**
 * 입은 갑옷을 확인: 요구 조건이 모자란 부위는 벗기고, 나머지 보너스를 스탯에 넣는다
 * - 스탯을 아직 읽지 않았으면(접속 직후) 먼저 읽는다 (0으로 보고 벗기지 않게)
 * - 크리에이티브·관전 모드는 벗기지 않는다 (보너스도 없음)
 * - 다른 부위 칸에 들어간 갑옷(명령어로 넣은 경우)은 보너스 없음
 * @param {import("@minecraft/server").Player} player
 */
export function armorTick(player) {
  let eq;
  try {
    eq = player.getComponent("minecraft:equippable");
  } catch {
    return;
  }
  if (!eq) return;
  ensureStats(player);
  const enforce = isRpgMode(player);
  const total = { str: 0, dex: 0, int: 0, vit: 0 };
  for (const piece of ARMOR_PIECE_ORDER) {
    const slot = EquipmentSlot[ARMOR_PIECES[piece].slot];
    let item;
    try {
      item = eq.getEquipment(slot);
    } catch {
      item = undefined;
    }
    const a = parseArmor(item?.typeId);
    if (!a || !item || a.piece !== piece) continue;
    const lack = armorLack(player, a);
    if (lack.length) {
      if (enforce) takeOff(player, eq, slot, item, a, lack);
      continue;
    }
    for (const [s, v] of Object.entries(a.bonus)) total[s] += v;
  }
  setStatBonus(player, total);
}

/** 벗겨서 인벤토리로 (가득 차면 발밑). 칸이 실제로 비었을 때만 돌려준다 (복사 방지) */
function takeOff(player, eq, slot, item, a, lack) {
  try {
    if (!eq.setEquipment(slot, undefined) || eq.getEquipment(slot)) return;
  } catch {
    return;
  }
  giveItem(player, item);
  notify(player, `§c${a.title}: ${lack.join(" · ")} 필요 §7- 벗겼습니다`, 50);
  try {
    player.playSound("mob.villager.no", { volume: 0.6 });
  } catch {
    // 무시
  }
}

export function registerArmor() {
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        armorTick(p);
      } catch {
        // 무시
      }
    }
  }, 10);
}

// ------------------------------------------------------------ 상점
/** 상점 레벨 → 파는 갑옷 레벨 (그 레벨 이하에서 가장 높은 것, 예: 15렙 상점 → 10렙 갑옷) */
export function armorLevelFor(level) {
  let best = ARMOR_LEVELS[0];
  for (const l of ARMOR_LEVELS) if (l <= level) best = l;
  return best;
}

export function armorPrice(a) {
  const base = SHOP.basePrice[a.level] ?? 1;
  return Math.max(1, Math.ceil(base * ARMOR_PIECES[a.piece].price - 1e-9));
}

export function setPrice(type, level) {
  const sum = ARMOR_PIECE_ORDER.reduce((s, p) => s + armorPrice(armorFrom(type, level, "common", p)), 0);
  return Math.max(1, Math.floor(sum * ARMOR_SHOP.setDiscount));
}

const WEAPON_TAB = 0;
const BALANCE_SLOT = 4;
const INFO_SLOT = 8;
const SET_FIRST = 11; // 11~15: 종류 5개

function buildShop(player, level, message) {
  const lv = armorLevelFor(level);
  const tier = ARMOR_TIERS[`common_${lv}`];
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const form = new ChestForm(`갑옷 상점 §8· §7${lv}렙 ${tier.name}`);
  form.set(WEAPON_TAB, { icon: "textures/items/iron_sword", name: "§b무기 상점으로" });
  form.set(BALANCE_SLOT, { icon: CONFIG.CURRENCY_ICON, count: bal, name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${bal}` });
  form.set(INFO_SLOT, { icon: "textures/items/iron_chestplate", name: `§f${tier.name} 세트 §7(${lv}렙)`, lore: ["§7같은 모양, 종류마다 스탯이 다름", "§7요구 조건이 모자라면 벗겨짐", "§7초월 갑옷은 왕실 퀘스트 보상"], deco: true });
  form.set(SELL_TAB, sellTabSlot(player)); // v2.7.0 전리품 팔기 (무기 상점과 같은 자리)
  ARMOR_TYPE_ORDER.forEach((type, i) => {
    const chest = armorFrom(type, lv, "common", "chest");
    const price = setPrice(type, lv);
    form.set(SET_FIRST + i, {
      icon: chest.icon,
      price,
      name: `${ARMOR_TYPES[type].color}${ARMOR_TYPES[type].name} 갑옷 §f${tier.name} 세트`,
      lore: [`§r§7세트 방어 §f${chest.setProt}§7 · ${bonusText(chest.setBonus)}`, `§r§7요구: ${armorReqText(chest)}`, `§r§a세트 ${price} ${CONFIG.CURRENCY_NAME} §7(낱개도 팜)`],
    });
  });
  form.fillEmpty();
  const lines = [`§8가운데 줄: 종류별 ${tier.name} 세트 · 칸을 누르면 부위별로 살 수 있습니다.§r`];
  lines.push(`§8힘·민첩·체력·힘+체력·마력 갑옷은 모양이 같고 스탯이 다릅니다.§r`);
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return { form, lv };
}

/** 한 종류의 부위 창: 10~13 부위 (누르면 구매), 15 세트 구매, 22 돌아가기 */
async function typeWindow(player, type, lv) {
  let message = "";
  for (let guard = 0; guard < 50; guard++) {
    const bal = countItem(player, CONFIG.CURRENCY_ITEM);
    const T = ARMOR_TYPES[type];
    const pieces = ARMOR_PIECE_ORDER.map((p) => armorFrom(type, lv, "common", p));
    const sp = setPrice(type, lv);
    const form = new ChestForm(`${T.name} 갑옷 §8· §7${ARMOR_TIERS[`common_${lv}`].name} (${lv}렙)`);
    pieces.forEach((a, i) => form.set(10 + i, { icon: a.icon, price: armorPrice(a), name: a.name, lore: [...armorInfo(a), `§r§a가격: ${armorPrice(a)} ${CONFIG.CURRENCY_NAME}`] }));
    form.set(15, { icon: "textures/rpg_ui/icon_check", price: sp, name: `§a세트 구매 §7(4부위, ${Math.round((1 - ARMOR_SHOP.setDiscount) * 100)}% 할인)`, lore: [`§r§a${sp} ${CONFIG.CURRENCY_NAME}`] });
    form.set(4, { icon: CONFIG.CURRENCY_ICON, count: bal, name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${bal}` });
    form.set(22, { icon: "textures/rpg_ui/icon_cross", name: "§7돌아가기" });
    form.fillEmpty();
    const lack = armorLack(player, pieces[0]);
    const lines = [`§8부위 칸: 그 부위 구매 · 초록 칸: 세트 구매 (§0${sp}§8)§r`, `§8요구: ${armorReqText(pieces[0])}${lack.length ? ` §4(지금 ${lack.join(" · ")} 부족)` : ""}§r`];
    if (message) lines.push(message);
    form.body(lines.join("\n"));
    const sel = await form.show(player);
    if (sel < 0 || sel === 22) return;
    if (sel >= 10 && sel <= 13) message = buy(player, [pieces[sel - 10]], armorPrice(pieces[sel - 10]));
    else if (sel === 15) message = buy(player, pieces, sp);
    else message = "";
  }
}

/** 구매 처리. 결과 문구 (창 본문용) */
export function buy(player, list, price) {
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const head = player.getHeadLocation();
  if (bal < price) {
    player.playSound("note.bass", { pitch: 0.6 });
    particle(player.dimension, "minecraft:villager_angry", { x: head.x, y: head.y + 0.5, z: head.z });
    notify(player, `§c${CONFIG.CURRENCY_NAME} 부족 §7(필요 ${price}, 보유 ${bal})`, 40);
    return `§4${CONFIG.CURRENCY_NAME} 부족: 필요 ${price}, 보유 ${bal}§r`;
  }
  let items;
  try {
    items = list.map(createArmorItem);
  } catch (e) {
    console.warn(`[rpg] armor item: ${e}`);
    return "§4아이템을 만들 수 없습니다. 행동 팩 적용 상태를 확인하세요.§r";
  }
  if (!fitsAfter(player, [{ id: CONFIG.CURRENCY_ITEM, n: price }], items)) return bagFull(player); // v2.7.0: 땅에 떨어지지 않게
  if (!removeItem(player, CONFIG.CURRENCY_ITEM, price)) return "§4결제 오류§r";
  for (const it of items) giveItem(player, it);
  player.playSound("armor.equip_iron", { pitch: 1.1 });
  player.playSound("random.orb", { pitch: 1.2, volume: 0.6 });
  particle(player.dimension, "minecraft:villager_happy", { x: head.x, y: head.y - 0.4, z: head.z });
  const one = list.length === 1;
  notify(player, `§a구매 완료: ${one ? list[0].name : `${list[0].setName} 세트 · ${list[0].typeName}`}`, 40);
  return `§2구매 완료: ${one ? list[0].darkName : `§0${list[0].setName} 세트§8 · ${list[0].typeName}`}§r`;
}

/**
 * 갑옷 상점 반복 (폼 잠금 안에서). "weapons" 를 돌려주면 무기 상점으로, "sell" 이면 전리품 팔기로
 * @param {import("@minecraft/server").Player} player
 * @param {number} level 상점 레벨 (무기 상점과 같은 값)
 */
export async function armorShopLoop(player, level) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const { form, lv } = buildShop(player, level, message);
    const sel = await form.show(player);
    if (sel < 0) return undefined;
    if (sel === WEAPON_TAB) return "weapons";
    if (sel === SELL_TAB) return "sell";
    const i = sel - SET_FIRST;
    if (i >= 0 && i < ARMOR_TYPE_ORDER.length) {
      await typeWindow(player, ARMOR_TYPE_ORDER[i], lv);
      message = "";
      continue;
    }
    message = "";
  }
  return undefined;
}

/**
 * 상점 (무기 ↔ 갑옷 ↔ 전리품 팔기 v2.7.0, 윗줄 칸으로 오감). 폼 잠금 안에서
 * @param {import("@minecraft/server").Player} player
 * @param {number} level
 * @param {"weapons" | "armor" | "sell"} [start]
 */
export async function storeLoop(player, level, start = "weapons") {
  /** @type {string | undefined} */
  let next = start;
  for (let i = 0; next && i < 20; i++) next = next === "armor" ? await armorShopLoop(player, level) : next === "sell" ? await sellLoop(player) : await shopLoop(player, level);
}

/** 상점 열기 (명령어용) */
export function openStore(player, level, start = "weapons") {
  if (!LEVELS.includes(level)) {
    player.sendMessage(`§c상점 레벨은 ${LEVELS.join(", ")} 중 하나여야 합니다.`);
    return Promise.resolve();
  }
  return withFormLock(player, () => storeLoop(player, level, /** @type {any} */ (start)));
}
