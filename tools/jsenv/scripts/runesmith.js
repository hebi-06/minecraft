import { ItemStack } from "@minecraft/server";
import { RUNE, RUNE_FAMILY_ORDER, RUNE_SMITH } from "./config.js";
import { ChestForm, withFormLock } from "./chestui.js";
import { notify, particle, play, showFaceText } from "./fx.js";
import { RUNE_GRADE_FX, createTicketItem, gradeWeights, parseRune } from "./runes.js";
import { bagFull, countItem, fitsAfter, giveItem, removeItem } from "./shop.js";
import { add, burst, flat, fx, rightOf, sparks, unit, up } from "./skills.js";
import { setLoreSafe } from "./weapons.js";

/**
 * 룬술사 (v2.7.0): 겹치거나 쓰지 않는 룬을 갈아 룬 조각으로 → 룬 조각으로 룬 뽑기권 · 고급 룬 뽑기권을 삼 (config.js RUNE_SMITH)
 * - NPC: 태그 rpg_runesmith (NPC 설정 창의 "상점·대장장이·룬술사 설정", /mainnpc 12 룬술사 에이빈드). 명령어 /runesmith (운영자)
 * - 창 둘 (상자 모양, 윗줄 왼쪽 칸으로 오감)
 *   · 룬 갈기: 가방의 룬 (최대 18개, 겹친 룬 먼저) → 누르면 확인 창 → 갈면 조각. 윗줄 오른쪽 = 겹친 룬 모두 갈기 (룬마다 하나는 남김, 핫바의 것을 남김)
 *   · 뽑기권: 룬 뽑기권 (조각 10) · 고급 룬 뽑기권 (조각 40, 일반 룬 없음) → 1장 · 5장
 * - 연출: 갈면 눈앞에 등급 색 마법진 섬광 + 깨지는 빛 조각 + 숫돌·자수정 소리 + "+8 룬 조각", 사면 보랏빛 마법진 + 반짝이
 */

const GRADE_ORDER = ["common", "rare", "noble", "hero", "transcend"];
const TAB = 0; // 다른 창으로
const SHARD_SLOT = 4; // 보유 조각
const DUP_SLOT = 8; // 겹친 룬 모두 갈기
const LIST_FIRST = 9; // 아래 두 줄: 룬 (최대 18)
const LIST_MAX = 18;
const PURPLE = [0.72, 0.42, 1.0];

/** 룬 조각 아이템 (설명 포함) */
export function createShardItem(amount = 1) {
  const item = new ItemStack(RUNE_SMITH.shardItem, Math.max(1, Math.min(64, Math.floor(amount))));
  setLoreSafe(item, RUNE_SMITH.shardLore);
  return item;
}

/** 룬 한 개를 갈면 나오는 조각 */
export function grindValue(r) {
  return RUNE_SMITH.grind[r?.grade] ?? 0;
}

/**
 * 가방의 룬 (칸마다): 겹친 룬 먼저, 그다음 낮은 등급 · 계열 순서
 * @returns {{slot: number, id: string, r: any, dup: number}[]} dup = 가방에 같은 룬이 몇 개인지
 */
export function runeSlots(player) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (!inv) return [];
  const out = [];
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    const r = parseRune(it?.typeId);
    if (r && it) out.push({ slot: i, id: it.typeId, r, dup: 0 });
  }
  const n = new Map();
  for (const e of out) n.set(e.id, (n.get(e.id) ?? 0) + 1);
  for (const e of out) e.dup = n.get(e.id) ?? 1;
  const rank = (e) => [e.dup > 1 ? 0 : 1, GRADE_ORDER.indexOf(e.r.grade), RUNE_FAMILY_ORDER.indexOf(e.r.family), e.slot];
  return out.sort((a, b) => {
    const x = rank(a);
    const y = rank(b);
    for (let k = 0; k < x.length; k++) if (x[k] !== y[k]) return x[k] - y[k];
    return 0;
  });
}

/** 겹친 룬 중 갈 것 (룬마다 하나는 남김 — 핫바에 있는 것을 먼저 남김) */
export function duplicateSlots(player) {
  const all = runeSlots(player);
  const byId = new Map();
  for (const e of all) byId.set(e.id, [...(byId.get(e.id) ?? []), e]);
  const out = [];
  for (const list of byId.values()) {
    if (list.length < 2) continue;
    const keep = [...list].sort((a, b) => (a.slot < 9 ? 0 : 1) - (b.slot < 9 ? 0 : 1) || a.slot - b.slot)[0];
    for (const e of list) if (e !== keep) out.push(e);
  }
  return out;
}

/** 갈기 연출: 눈앞에 등급 색 마법진 섬광 + 깨져 흩어지는 빛 조각 + 숫돌·자수정 소리 */
function grindFx(player, grade, shards) {
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const at = up(add(player.location, f, 2.2), 1.4);
  const col = RUNE_GRADE_FX[grade] ?? PURPLE;
  const gi = Math.max(0, GRADE_ORDER.indexOf(grade));
  fx(dim, "rpg:magic_circle_flash", at, { size: 0.6 + gi * 0.08, life: 0.45, spin: Math.random() * 360, progress: 1, color: col, alpha: 1 });
  burst(dim, at, col, 0.8 + gi * 0.15, 0.3);
  const side = rightOf(f);
  for (let i = 0; i < 8 + gi * 2; i++) {
    const a = (i / (8 + gi * 2)) * Math.PI * 2;
    const dir = unit({ x: side.x * Math.cos(a), y: Math.sin(a) + 0.3, z: side.z * Math.cos(a) });
    fx(dim, "rpg:arrow_spark", at, { dir, speed: 3 + Math.random() * 2, size: 0.11, grow: 0.4, life: 0.45, color: i % 2 ? col : PURPLE, alpha: 1 });
  }
  play(dim, "block.grindstone.use", player.location, 1.1, 0.8);
  play(dim, "break.amethyst_cluster", player.location, 1.0 + gi * 0.1, 0.8);
  showFaceText(player, `§d§l+${shards} ${RUNE_SMITH.shardName}`, { dist: 2.0, side: 0.5, up: -0.3 });
}

/**
 * 룬 갈기: 고른 칸의 룬을 가져가고 조각을 줌 (칸에 그 룬이 그대로 있을 때만). 결과 문구 (창 본문용)
 * @param {any} player
 * @param {{slot: number, id: string}[]} picks
 */
export function grindRunes(player, picks) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (!inv) return "";
  let shards = 0;
  let count = 0;
  let top = "common";
  for (const p of picks) {
    const it = inv.getItem(p.slot);
    if (!it || it.typeId !== p.id) continue;
    const r = parseRune(it.typeId);
    const v = grindValue(r);
    if (!r || v <= 0) continue;
    inv.setItem(p.slot, undefined);
    shards += v;
    count++;
    if (GRADE_ORDER.indexOf(r.grade) > GRADE_ORDER.indexOf(top)) top = r.grade;
  }
  if (count === 0) {
    player.playSound("note.bass", { pitch: 0.6 });
    return "§4룬이 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
  }
  for (let left = shards; left > 0; left -= 64) giveItem(player, createShardItem(Math.min(64, left)));
  grindFx(player, top, shards);
  notify(player, `§d룬 ${count}개를 갈았습니다 §7→ §d${RUNE_SMITH.shardName} +${shards}`, 50);
  return `§2룬 ${count}개 → §5${RUNE_SMITH.shardName} +${shards}§r`;
}

/** 뽑기권 정보 (adv = 고급) */
function ticketInfo(adv) {
  const w = gradeWeights(adv);
  const odds = GRADE_ORDER.filter((g) => w[g] > 0).map((g) => `${{ common: "일반", rare: "희귀", noble: "고귀", hero: "영웅", transcend: "초월" }[g]} ${w[g]}%`);
  return {
    adv,
    id: adv ? RUNE.advTicketItem : RUNE.ticketItem,
    name: adv ? RUNE.advTicketName : RUNE.ticketName,
    icon: adv ? RUNE.advTicketIcon : RUNE.ticketIcon,
    price: adv ? RUNE_SMITH.price.adv : RUNE_SMITH.price.ticket,
    odds,
    color: adv ? "§6" : "§d",
  };
}

/**
 * 뽑기권 사기: 조각을 가져가고 뽑기권 n 장. 결과 문구
 * @param {any} player
 * @param {boolean} adv
 * @param {number} n
 */
export function buyTicket(player, adv, n) {
  const t = ticketInfo(adv);
  const cost = t.price * n;
  const have = countItem(player, RUNE_SMITH.shardItem);
  if (n <= 0 || have < cost) {
    player.playSound("note.bass", { pitch: 0.6 });
    notify(player, `§c${RUNE_SMITH.shardName} 부족 §7(필요 ${cost}, 보유 ${have})`, 40);
    return `§4${RUNE_SMITH.shardName} 부족: 필요 ${cost}, 보유 ${have}§r`;
  }
  let item;
  try {
    item = createTicketItem(n, adv);
  } catch (e) {
    console.warn(`[rpg] rune ticket: ${e}`);
    return "§4뽑기권을 만들 수 없습니다. 행동 팩 적용 상태를 확인하세요.§r";
  }
  if (!fitsAfter(player, [{ id: RUNE_SMITH.shardItem, n: cost }], [item])) return bagFull(player); // 땅에 떨어지지 않게
  if (!removeItem(player, RUNE_SMITH.shardItem, cost)) return "§4결제 오류§r";
  giveItem(player, item);
  const dim = player.dimension;
  const f = flat(player.getViewDirection());
  const at = up(add(player.location, f, 2.2), 1.4);
  const col = adv ? [1.0, 0.75, 0.3] : PURPLE;
  fx(dim, "rpg:magic_circle_flash", at, { size: 0.7, life: 0.5, spin: Math.random() * 360, progress: 1, color: col, alpha: 1 });
  sparks(dim, at, col, 10, 3);
  play(dim, "vault.eject_item", player.location, 1.2, 0.9);
  play(dim, "chime.amethyst_block", player.location, adv ? 1.4 : 1.1, 1);
  particle(dim, "minecraft:totem_particle", up(player.location, 1.2));
  showFaceText(player, `${t.color}§l${t.name} ×${n}`, { dist: 2.0, side: 0.5, up: -0.3 });
  notify(player, `${t.color}${t.name} ${n}장 §7(${RUNE_SMITH.shardName} -${cost})`, 50);
  return `§2구매: ${t.name} ${n}장 §8(${RUNE_SMITH.shardName} -${cost})§r`;
}

function shardSlot(player) {
  const n = countItem(player, RUNE_SMITH.shardItem);
  return { icon: RUNE_SMITH.shardIcon, count: n, name: `§d보유 ${RUNE_SMITH.shardName} §f${n}`, lore: [`§7갈기: ${GRADE_ORDER.map((g) => RUNE_SMITH.grind[g]).join(" · ")} (일반~초월)`] };
}

// ------------------------------------------------------------ 창: 룬 갈기
function grindForm(player, message) {
  const list = runeSlots(player).slice(0, LIST_MAX);
  const dups = duplicateSlots(player);
  const dupShards = dups.reduce((s, e) => s + grindValue(e.r), 0);
  const form = new ChestForm("룬술사 §8· §7룬 갈기");
  form.set(TAB, { icon: RUNE.ticketIcon, name: "§d뽑기권 사기로", lore: [`§7${RUNE.ticketName} · ${RUNE.advTicketName}`] });
  form.set(SHARD_SLOT, shardSlot(player));
  form.set(
    DUP_SLOT,
    dups.length
      ? { icon: "textures/rpg_ui/icon_check", price: dupShards, name: `§a겹친 룬 모두 갈기 §7(${dups.length}개 · +${dupShards})`, lore: ["§7같은 룬은 하나씩 남김", "§7(핫바의 것을 남김, 확인 창)"] }
      : { icon: "textures/rpg_ui/icon_check", name: "§8겹친 룬 없음", deco: true },
  );
  list.forEach((e, i) => {
    const v = grindValue(e.r);
    form.set(LIST_FIRST + i, {
      icon: `textures/items/rpg/runes/rune_${e.r.family}_${e.r.grade}`,
      price: v,
      name: `${e.r.gradeColor}${e.r.def.name}§r§7 · ${e.r.famName} 룬`,
      lore: [`§r§7${e.r.gradeName} · 갈면 §d+${v} ${RUNE_SMITH.shardName}`, e.dup > 1 ? `§r§e같은 룬 ${e.dup}개` : "§r§8하나뿐인 룬", e.slot < 9 ? `§r§8핫바 ${e.slot + 1}번 칸` : "§r§8가방"],
    });
  });
  form.fillEmpty();
  const lines = [list.length ? "§8룬 칸을 누르면 확인 창 · 초록 숫자 = 받는 룬 조각§r" : "§8갈 룬이 없습니다. 룬을 가방에 넣고 오세요.§r"];
  if (runeSlots(player).length > LIST_MAX) lines.push(`§8룬은 ${LIST_MAX}개까지 보입니다 (겹친 룬 먼저).§r`);
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return { form, list, dups };
}

async function confirmGrind(player, e) {
  const v = grindValue(e.r);
  const form = new ChestForm("룬 갈기");
  form.set(11, { icon: "textures/rpg_ui/icon_check", price: v, name: `§a갈기 §7(+${v} ${RUNE_SMITH.shardName})` });
  form.set(13, { icon: `textures/items/rpg/runes/rune_${e.r.family}_${e.r.grade}`, name: `${e.r.gradeColor}${e.r.def.name}`, lore: [`§r§7${e.r.famName} 룬 · ${e.r.gradeName}`], deco: true });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const lines = [`§0${e.r.def.name}§8 (${e.r.famName} 룬 · ${e.r.gradeName}) → ${RUNE_SMITH.shardName} §0${v}§8개§r`];
  if (e.dup <= 1) lines.push("§4하나뿐인 룬입니다. 갈면 되돌릴 수 없습니다.§r");
  lines.push("§8왼쪽 초록 칸: 갈기 · 오른쪽 빨간 칸: 취소§r");
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  return sel === 11 ? grindRunes(player, [{ slot: e.slot, id: e.id }]) : "";
}

async function confirmDups(player, dups) {
  const total = dups.reduce((s, e) => s + grindValue(e.r), 0);
  const form = new ChestForm("겹친 룬 모두 갈기");
  form.set(11, { icon: "textures/rpg_ui/icon_check", price: total, name: `§a모두 갈기 §7(${dups.length}개 · +${total})` });
  form.set(13, { icon: RUNE_SMITH.shardIcon, count: total, name: `§d${RUNE_SMITH.shardName} +${total}`, deco: true });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const names = [...new Set(dups.map((e) => e.r.def.name))];
  const rows = [];
  for (let i = 0; i < names.length && rows.length < 3; i += 3) rows.push(`§0${names.slice(i, i + 3).join(" · ")}§r`);
  form.body([`§8같은 룬은 하나씩 남기고 §0${dups.length}개§8를 갑니다 → §5+${total}§r`, ...rows, "§8왼쪽 초록 칸: 갈기 · 오른쪽 빨간 칸: 취소§r"].join("\n"));
  const sel = await form.show(player);
  return sel === 11 ? grindRunes(player, dups.map((e) => ({ slot: e.slot, id: e.id }))) : "";
}

/** 룬 갈기 창 반복. "tickets" 를 돌려주면 뽑기권 창으로 */
export async function grindLoop(player) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const { form, list, dups } = grindForm(player, message);
    const sel = await form.show(player);
    if (sel < 0) return undefined;
    if (sel === TAB) return "tickets";
    if (sel === DUP_SLOT) {
      message = dups.length ? await confirmDups(player, dups) : "";
      continue;
    }
    const e = sel >= LIST_FIRST ? list[sel - LIST_FIRST] : undefined;
    message = e ? await confirmGrind(player, e) : "";
  }
  return undefined;
}

// ------------------------------------------------------------ 창: 뽑기권
const NORMAL_SLOT = 11;
const ADV_SLOT = 15;
const INFO_SLOT = 13;

function ticketForm(player, message) {
  const form = new ChestForm("룬술사 §8· §7뽑기권");
  form.set(TAB, { icon: "textures/items/rpg/runes/rune_int_common", name: "§d룬 갈기로", lore: ["§7겹치거나 쓰지 않는 룬 → 룬 조각"] });
  form.set(SHARD_SLOT, shardSlot(player));
  for (const [slot, adv] of [
    [NORMAL_SLOT, false],
    [ADV_SLOT, true],
  ]) {
    const t = ticketInfo(adv);
    form.set(/** @type {number} */ (slot), { icon: t.icon, price: t.price, name: `${t.color}${t.name} §7· ${RUNE_SMITH.shardName} ${t.price}`, lore: [`§r§7${t.odds.slice(0, 3).join(" · ")}`, `§r§7${t.odds.slice(3).join(" · ")}`, "§r§7누르면 1장 · 5장"].filter((l) => l !== "§r§7") });
  }
  form.set(INFO_SLOT, { icon: RUNE_SMITH.shardIcon, name: `§d${RUNE_SMITH.shardName}`, lore: ["§7룬을 갈면 나옴", `§7일반 ${RUNE_SMITH.grind.common} · 희귀 ${RUNE_SMITH.grind.rare} · 고귀 ${RUNE_SMITH.grind.noble}`, `§7영웅 ${RUNE_SMITH.grind.hero} · 초월 ${RUNE_SMITH.grind.transcend}`], deco: true });
  form.fillEmpty();
  const lines = [`§8왼쪽: §0${RUNE.ticketName}§8 · 오른쪽: §0${RUNE.advTicketName}§8 (일반 룬 없음)§r`, "§8초록 숫자 = 필요한 룬 조각 · 누르면 확인 창§r"];
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return form;
}

async function confirmBuy(player, adv) {
  const t = ticketInfo(adv);
  const have = countItem(player, RUNE_SMITH.shardItem);
  const form = new ChestForm(`${t.name} 사기`);
  form.set(11, { icon: t.icon, price: t.price, name: `§a1장 사기 §7(${RUNE_SMITH.shardName} ${t.price})` });
  if (have >= t.price * 5) form.set(12, { icon: t.icon, price: t.price * 5, name: `§a5장 사기 §7(${RUNE_SMITH.shardName} ${t.price * 5})` });
  form.set(13, { icon: RUNE_SMITH.shardIcon, count: have, name: `§d보유 ${RUNE_SMITH.shardName} ${have}`, deco: true });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const lines = [`§0${t.name}§8 1장 = ${RUNE_SMITH.shardName} §0${t.price}§8 · 보유 §0${have}§r`, `§8${t.odds.join(" · ")}§r`];
  if (have < t.price) lines.push(`§4${RUNE_SMITH.shardName}이 ${t.price - have}개 모자랍니다.§r`);
  lines.push("§8초록 숫자 칸: 그만큼 사기 · 빨간 칸: 취소§r");
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  const n = sel === 11 ? 1 : sel === 12 && have >= t.price * 5 ? 5 : 0;
  return n > 0 ? buyTicket(player, adv, n) : "";
}

/** 뽑기권 창 반복. "grind" 를 돌려주면 룬 갈기 창으로 */
export async function ticketLoop(player) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const sel = await ticketForm(player, message).show(player);
    if (sel < 0) return undefined;
    if (sel === TAB) return "grind";
    message = sel === NORMAL_SLOT ? await confirmBuy(player, false) : sel === ADV_SLOT ? await confirmBuy(player, true) : "";
  }
  return undefined;
}

/**
 * 룬술사 창 (룬 갈기 ↔ 뽑기권). 폼 잠금 안에서 (NPC 대화에서)
 * @param {any} player
 * @param {"grind" | "tickets"} [start]
 */
export async function runesmithLoop(player, start = "grind") {
  /** @type {string | undefined} */
  let next = start;
  for (let i = 0; next && i < 20; i++) next = next === "tickets" ? await ticketLoop(player) : await grindLoop(player);
}

/** 룬술사 창 열기 (명령어용) */
export function openRunesmith(player, start = "grind") {
  return withFormLock(player, () => runesmithLoop(player, /** @type {any} */ (start)));
}
