import { ItemStack } from "@minecraft/server";
import { CONFIG, GRADES, MONSTER_DROPS, MONSTER_ORDER, MONSTERS, SHOP, TYPE_ORDER, isValidWeapon } from "./config.js";
import { ChestForm } from "./chestui.js";
import { notify, particle, showFaceText } from "./fx.js";
import { createWeaponItem, levelNote, shopPrice, weaponFrom, weaponInfo } from "./weapons.js";
import { reqLevelOf } from "./stats.js";

/**
 * 무기 상점 (상자 모양, 9x3). 구매 시 스탯·레벨 제한 없음.
 *   윗줄 가운데: 보유 화폐
 *   둘째 줄: 첫 번째 판매 등급(기본 일반) 6종,  셋째 줄: 두 번째 판매 등급(기본 희귀) 6종
 *   칸을 누르면 구매 확인 창이 열린다.
 * v2.7.0: 윗줄 오른쪽에서 셋째 칸 = 전리품 팔기 (몬스터 전리품 → 에메랄드, 갑옷 상점에도 같은 자리)
 */
const BALANCE_SLOT = 4;
/** v2.7.0 전리품 팔기 창으로 (무기·갑옷 상점 같은 자리) */
export const SELL_TAB = 6;
const ARMOR_TAB = 8; // v1.5.0: 갑옷 상점으로
const ROWS = [
  { badge: 9, first: 11 },
  { badge: 18, first: 20 },
];

function inventory(player) {
  return player.getComponent("minecraft:inventory")?.container;
}

export function countItem(player, typeId) {
  const inv = inventory(player);
  if (!inv) return 0;
  let n = 0;
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (it && it.typeId === typeId) n += it.amount;
  }
  return n;
}

export function removeItem(player, typeId, amount) {
  const inv = inventory(player);
  if (!inv) return false;
  let left = amount;
  for (let i = 0; i < inv.size && left > 0; i++) {
    const it = inv.getItem(i);
    if (!it || it.typeId !== typeId) continue;
    const take = Math.min(left, it.amount);
    left -= take;
    if (it.amount - take <= 0) inv.setItem(i, undefined);
    else {
      it.amount -= take;
      inv.setItem(i, it);
    }
  }
  return left === 0;
}

export function giveItem(player, item) {
  const inv = inventory(player);
  const rest = inv ? inv.addItem(item) : item;
  if (rest) player.dimension.spawnItem(rest, player.location);
}

/**
 * v2.7.0: remove 를 먼저 빼고 (removeItem 과 같은 순서) add 묶음을 게임처럼 넣어 보면 모두 들어가는지
 * (같은 아이템 묶음에 겹칠 수 있으면 겹치고, 남으면 빈 칸). 사고팔 때 가방이 꽉 차서 땅에 떨어지지 않게
 * @param {any} player
 * @param {{id: string, n: number}[]} remove
 * @param {import("@minecraft/server").ItemStack[]} add
 */
export function fitsAfter(player, remove, add) {
  const inv = inventory(player);
  if (!inv) return false;
  /** @type {({item: any, n: number} | undefined)[]} */
  const slots = [];
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    slots.push(it ? { item: it, n: it.amount } : undefined);
  }
  for (const r of remove) {
    let left = r.n;
    for (let i = 0; i < slots.length && left > 0; i++) {
      const sl = slots[i];
      if (!sl || sl.item.typeId !== r.id) continue;
      const take = Math.min(left, sl.n);
      left -= take;
      sl.n -= take;
      if (sl.n <= 0) slots[i] = undefined;
    }
  }
  for (const st of add) {
    let n = st.amount;
    const max = st.maxAmount;
    for (const sl of slots) {
      if (n <= 0) break;
      if (!sl || sl.n >= Math.min(max, sl.item.maxAmount)) continue;
      let same = false;
      try {
        same = sl.item.isStackableWith(st);
      } catch {
        same = false;
      }
      if (!same) continue;
      const k = Math.min(n, max - sl.n);
      sl.n += k;
      n -= k;
    }
    if (n <= 0) continue;
    const i = slots.findIndex((sl) => !sl);
    if (i < 0) return false;
    slots[i] = { item: st, n };
  }
  return true;
}

/** 가방이 꽉 차서 사고팔 수 없음: 소리 + 알림, 창 본문 문구 */
export function bagFull(player) {
  player.playSound("note.bass", { pitch: 0.6 });
  notify(player, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)", 40);
  return "§4가방에 빈 칸이 없습니다. 한 칸 비우고 다시 해 주세요.§r";
}

function levelWeapons(level) {
  // 그 등급에 없는 레벨(예: 영웅 10렙)은 팔지 않는다 (SHOP.grades 에 영웅·초월을 넣은 경우)
  return SHOP.grades.slice(0, ROWS.length).map((g) => TYPE_ORDER.map((t) => (isValidWeapon(level, g) ? weaponFrom(t, level, g) : undefined)));
}

function buildShop(player, level, message) {
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const form = new ChestForm(`무기 상점 §8· §7${level}렙`);
  form.set(BALANCE_SLOT, {
    icon: CONFIG.CURRENCY_ICON,
    count: bal,
    name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${bal}`,
  });
  /** @type {Map<number, any>} */
  const bySlot = new Map();
  levelWeapons(level).forEach((row, r) => {
    const layout = ROWS[r];
    const grade = GRADES.find((g) => g.id === SHOP.grades[r]);
    if (grade) form.set(layout.badge, { icon: grade.icon, name: `${grade.color}${grade.name} 등급`, deco: true });
    row.forEach((w, i) => {
      if (!w) return;
      const price = shopPrice(w);
      form.set(layout.first + i, {
        icon: w.icon,
        price,
        name: w.name,
        lore: [...weaponInfo(w), `§r§a가격: ${price} ${CONFIG.CURRENCY_NAME}`],
      });
      bySlot.set(layout.first + i, w);
    });
  });
  form.set(ARMOR_TAB, { icon: "textures/items/iron_chestplate", name: "§b갑옷 상점으로", lore: ["§7힘·민첩·체력·힘+체력·마력 갑옷"] });
  form.set(SELL_TAB, sellTabSlot(player));
  form.fillEmpty();
  const lines = ["§8칸을 누르면 구매 창이 열립니다. 칸의 초록 숫자는 가격입니다.§r"];
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return { form, bySlot };
}

async function confirmPurchase(player, w) {
  const price = shopPrice(w);
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const form = new ChestForm("구매 확인");
  form.set(11, { icon: "textures/rpg_ui/icon_check", name: "§a구매하기" });
  form.set(13, { icon: w.icon, price, name: w.name, lore: weaponInfo(w) });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const lv = reqLevelOf(player);
  // v2.6.2 렙제: 아직 쓸 수 없으면 설명의 "요구 레벨" 줄을 빨간 줄로 (휴대폰 창이 길어지지 않게 같은 자리, 한 줄 180px 안)
  const lines = [w.darkName + "§r", ...weaponInfo(w, true).map((l) => (levelNote(w, lv) && l.startsWith("§r§8요구 레벨:") ? `§r§4요구 레벨: ${w.level} §8(지금 최고 레벨 ${lv})` : l))];
  lines.push(`§r§8가격: §2§l${price}§r§8 ${CONFIG.CURRENCY_NAME} · 보유 §0${bal}§r`);
  lines.push("§8왼쪽 초록 칸: 구매 · 오른쪽 빨간 칸: 취소§r");
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  if (sel !== 11) return "";
  return purchase(player, w);
}

/** 구매 처리. 결과 문구를 돌려준다. */
function purchase(player, w) {
  const price = shopPrice(w);
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const head = player.getHeadLocation();
  if (bal < price) {
    player.playSound("note.bass", { pitch: 0.6 });
    particle(player.dimension, "minecraft:villager_angry", { x: head.x, y: head.y + 0.5, z: head.z });
    notify(player, `§c${CONFIG.CURRENCY_NAME} 부족 §7(필요 ${price}, 보유 ${bal})`, 40);
    return `§4${CONFIG.CURRENCY_NAME} 부족: 필요 ${price}, 보유 ${bal}§r`;
  }
  // 아이템을 먼저 만들고 나서 결제한다 (생성 실패 시 화폐가 사라지지 않게)
  let item;
  try {
    item = createWeaponItem(w);
  } catch (e) {
    console.warn(`[rpg] shop item ${w.id}: ${e}`);
    return "§4아이템을 만들 수 없습니다. 행동 팩 적용 상태를 확인하세요.§r";
  }
  if (!fitsAfter(player, [{ id: CONFIG.CURRENCY_ITEM, n: price }], [item])) return bagFull(player); // v2.7.0: 땅에 떨어지지 않게
  if (!removeItem(player, CONFIG.CURRENCY_ITEM, price)) return "§4결제 오류§r";
  giveItem(player, item);
  player.playSound("random.orb", { pitch: 1.2 });
  player.playSound("mob.villager.yes", { volume: 0.7 });
  particle(player.dimension, "minecraft:villager_happy", { x: head.x, y: head.y - 0.4, z: head.z });
  notify(player, `§a구매 완료: ${w.name}${levelNote(w, reqLevelOf(player))}`, 40);
  return `§2구매 완료: ${w.darkName}§r`;
}

// 상점 열기는 armor.js 의 openStore (무기 ↔ 갑옷 상점을 오가는 창, v1.5.0)

/**
 * 무기 상점 창 반복 (이미 폼 잠금 안에 있을 때 사용, 예: NPC 대화에서 상점으로). "armor" 를 돌려주면 갑옷 상점으로, "sell" 이면 전리품 팔기로 (armor.js storeLoop)
 * @param {import("@minecraft/server").Player} player
 * @param {number} level
 */
export async function shopLoop(player, level) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const { form, bySlot } = buildShop(player, level, message);
    const sel = await form.show(player);
    if (sel < 0) return undefined;
    if (sel === ARMOR_TAB) return "armor";
    if (sel === SELL_TAB) return "sell";
    const w = bySlot.get(sel);
    if (!w) {
      message = "";
      continue;
    }
    message = await confirmPurchase(player, w);
  }
  return undefined;
}

// ------------------------------------------------------------ 전리품 팔기 (v2.7.0)
const SELL_BACK = 0; // 무기 상점으로
const SELL_ALL = 8; // 모두 팔기
const SELL_FIRST = 10; // 가운데 줄: 전리품 6종 (10~15)
const SELL_INFO = 22;

/** 팔 수 있는 전리품 [{key, d, n}] (몬스터 순서, 없는 것도) */
export function sellList(player) {
  return MONSTER_ORDER.filter((k) => MONSTER_DROPS[k]).map((k) => ({ key: k, d: MONSTER_DROPS[k], n: countItem(player, MONSTER_DROPS[k].item) }));
}

/** 가진 전리품을 모두 팔면 받는 에메랄드 */
export function sellTotal(player) {
  return sellList(player).reduce((s, e) => s + e.n * e.d.price, 0);
}

/** 무기·갑옷 상점 윗줄의 "전리품 팔기" 칸 */
export function sellTabSlot(player) {
  const total = sellTotal(player);
  return { icon: "textures/items/bundle", name: "§e전리품 팔기", lore: ["§7몬스터 전리품을 에메랄드로", total > 0 ? `§a지금 모두 팔면 ${total} ${CONFIG.CURRENCY_NAME}` : "§8가진 전리품 없음"] };
}

function sellForm(player, message) {
  const bal = countItem(player, CONFIG.CURRENCY_ITEM);
  const list = sellList(player);
  const total = list.reduce((s, e) => s + e.n * e.d.price, 0);
  const form = new ChestForm("전리품 팔기 §8· §7상점");
  form.set(SELL_BACK, { icon: "textures/items/iron_sword", name: "§b무기 상점으로" });
  form.set(BALANCE_SLOT, { icon: CONFIG.CURRENCY_ICON, count: bal, name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${bal}` });
  form.set(SELL_ALL, total > 0 ? { icon: "textures/rpg_ui/icon_check", price: total, name: `§a모두 팔기 §7(+${total} ${CONFIG.CURRENCY_NAME})`, lore: ["§7가진 전리품을 한 번에 팝니다", "§7(확인 창이 열림)"] } : { icon: "textures/rpg_ui/icon_check", name: "§8모두 팔기 §7(가진 전리품 없음)", deco: true });
  list.forEach((e, i) => {
    const m = MONSTERS[e.key];
    form.set(SELL_FIRST + i, {
      icon: e.d.icon,
      count: e.n,
      name: `${e.d.color}${e.d.name} §7· 1개 ${e.d.price} ${CONFIG.CURRENCY_NAME}`,
      lore: [e.n > 0 ? `§r§a보유 ${e.n}개 → ${e.n * e.d.price} ${CONFIG.CURRENCY_NAME}` : "§r§8가진 것이 없음", `§r§7${m.name} (Lv.${m.level}) 전리품`],
    });
  });
  form.set(SELL_INFO, { icon: "textures/items/bundle", name: "§f전리품", lore: ["§7몬스터를 잡으면 떨어짐 (50%)", "§7잉그리드의 반복 의뢰에 쓰면", "§7에메랄드·경험치를 더 받음"], deco: true });
  form.fillEmpty();
  const lines = ["§8가운데 줄: 전리품 · 칸을 누르면 팔 개수를 고릅니다.§r", "§8반복 의뢰 재료로 쓰면 더 많이 받습니다 (여관 주인 잉그리드).§r"];
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return { form, list };
}

/**
 * 판매 처리: 전리품 n 개를 가져가고 에메랄드를 줌. 결과 문구 (창 본문용)
 * @param {any} player
 * @param {{key: string, n: number}[]} what 몬스터 키 · 개수
 */
export function sellLoot(player, what) {
  // 같은 전리품이 여러 번 오면 합쳐서 가진 만큼만 (가져가고 값을 치르지 않는 일이 없게)
  /** @type {Map<string, number>} */
  const want = new Map();
  for (const w of what) if (MONSTER_DROPS[w.key]) want.set(w.key, (want.get(w.key) ?? 0) + Math.max(0, Math.floor(Number(w.n) || 0)));
  /** @type {{d: any, n: number}[]} */
  const plan = [];
  for (const [key, total] of want) {
    const d = MONSTER_DROPS[key];
    const n = Math.min(total, countItem(player, d.item));
    if (n > 0) plan.push({ d, n });
  }
  const pay = (v) => {
    const out = [];
    for (let left = v; left > 0; left -= 64) out.push(new ItemStack(CONFIG.CURRENCY_ITEM, Math.min(64, left)));
    return out;
  };
  const total = plan.reduce((s, x) => s + x.n * x.d.price, 0);
  if (total <= 0) {
    player.playSound("note.bass", { pitch: 0.6 });
    return "§4팔 전리품이 없습니다§r";
  }
  // 받을 에메랄드가 들어갈 자리 (전리품을 뺀 뒤) 가 없으면 팔지 않음 (땅에 떨어지지 않게)
  if (!fitsAfter(player, plan.map((x) => ({ id: x.d.item, n: x.n })), pay(total))) return bagFull(player);
  const lines = [];
  let got = 0;
  for (const x of plan) {
    if (!removeItem(player, x.d.item, x.n)) continue;
    got += x.n * x.d.price;
    lines.push(`${x.d.name} ${x.n}`);
  }
  const head = player.getHeadLocation();
  if (got <= 0) {
    player.playSound("note.bass", { pitch: 0.6 });
    return "§4팔 전리품이 없습니다§r";
  }
  for (const stack of pay(got)) giveItem(player, stack);
  player.playSound("random.orb", { pitch: 1.3 });
  player.playSound("mob.villager.yes", { volume: 0.7 });
  particle(player.dimension, "minecraft:villager_happy", { x: head.x, y: head.y - 0.4, z: head.z });
  showFaceText(player, `§a§l+${got} ${CONFIG.CURRENCY_NAME}`, { dist: 2.0, side: 0.5, up: -0.3 });
  notify(player, `§a판매: ${lines.join(", ")} §7→ §a${CONFIG.CURRENCY_NAME} +${got}`, 50);
  return `§2판매: §0${lines.join(", ")}§2 → ${CONFIG.CURRENCY_NAME} +${got}§r`;
}

/** 한 가지 팔기: 1개 · 10개 · 모두 (가진 만큼만 보임). 결과 문구 */
async function sellOne(player, e) {
  const d = e.d;
  const n = countItem(player, d.item);
  if (n <= 0) {
    player.playSound("note.bass", { pitch: 0.7 });
    return `§4${d.name}: 가진 것이 없습니다§r`;
  }
  const form = new ChestForm(`팔기 §8· §7${d.name}`);
  form.set(4, { icon: d.icon, count: n, name: `${d.color}${d.name}`, lore: [`§r§7보유 ${n}개 · 1개 ${d.price} ${CONFIG.CURRENCY_NAME}`], deco: true });
  form.set(11, { icon: d.icon, price: d.price, name: `§a1개 팔기 §7(+${d.price})` });
  if (n >= 10) form.set(12, { icon: d.icon, price: d.price * 10, name: `§a10개 팔기 §7(+${d.price * 10})` });
  form.set(13, { icon: "textures/rpg_ui/icon_check", price: d.price * n, name: `§a모두 팔기 §7(${n}개, +${d.price * n})` });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  form.body([`§0${d.name}§8 보유 §0${n}개§8 · 1개 §0${d.price} ${CONFIG.CURRENCY_NAME}§r`, "§8초록 숫자 칸: 그만큼 팔기 · 빨간 칸: 취소§r"].join("\n"));
  const sel = await form.show(player);
  const count = sel === 11 ? 1 : sel === 12 && n >= 10 ? 10 : sel === 13 ? n : 0;
  return count > 0 ? sellLoot(player, [{ key: e.key, n: count }]) : "";
}

/** 모두 팔기 확인. 결과 문구 */
async function sellEverything(player) {
  const list = sellList(player).filter((e) => e.n > 0);
  const total = list.reduce((s, e) => s + e.n * e.d.price, 0);
  if (total <= 0) return "§4팔 전리품이 없습니다§r";
  const form = new ChestForm("모두 팔기");
  form.set(11, { icon: "textures/rpg_ui/icon_check", price: total, name: `§a모두 팔기 §7(+${total} ${CONFIG.CURRENCY_NAME})` });
  form.set(13, { icon: CONFIG.CURRENCY_ICON, count: total, name: `§a${CONFIG.CURRENCY_NAME} +${total}`, deco: true });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const parts = list.map((e) => `${e.d.name} ${e.n}`);
  const rows = [];
  for (let i = 0; i < parts.length; i += 3) rows.push(`§0${parts.slice(i, i + 3).join(" · ")}§r`);
  form.body([...rows, `§8합계 §2${total} ${CONFIG.CURRENCY_NAME}§8 · 왼쪽 초록 칸: 팔기 · 빨간 칸: 취소§r`].join("\n"));
  const sel = await form.show(player);
  return sel === 11 ? sellLoot(player, list.map((e) => ({ key: e.key, n: e.n }))) : "";
}

/**
 * 전리품 팔기 창 반복 (폼 잠금 안에서). "weapons" 를 돌려주면 무기 상점으로 (armor.js storeLoop)
 * @param {import("@minecraft/server").Player} player
 */
export async function sellLoop(player) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const { form, list } = sellForm(player, message);
    const sel = await form.show(player);
    if (sel < 0) return undefined;
    if (sel === SELL_BACK) return "weapons";
    if (sel === SELL_ALL) {
      message = await sellEverything(player);
      continue;
    }
    const e = list[sel - SELL_FIRST];
    message = e && sel >= SELL_FIRST ? await sellOne(player, e) : "";
  }
  return undefined;
}
