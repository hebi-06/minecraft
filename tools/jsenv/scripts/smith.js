import { ItemStack } from "@minecraft/server";
import { BOSSES, BOSS_CRAFT, BOSS_MATERIALS, CONFIG, MONSTER_DROPS, MONSTERS, SMITH, craftMaterials, isValidWeapon } from "./config.js";
import { ChestForm, withFormLock } from "./chestui.js";
import { notify, particle, play } from "./fx.js";
import { countItem, giveItem, removeItem } from "./shop.js";
import { enhanceLoop } from "./enhance.js";
import { NAME_PROP, createWeaponItem, enhanceOf, levelNote, needsLevel, parseWeapon, reqsText, setLoreSafe, weaponFrom, weaponInfo, writeEnhance } from "./weapons.js";
import { reqLevelOf } from "./stats.js";

/**
 * 대장장이 (v1.1.9): 영웅 무기의 레벨 제한을 SMITH.step 씩 올린다 (30 → 35 → 40).
 * 무기 아이템을 다음 레벨 아이템으로 바꾸는 방식이라 기본 성능·요구 레벨·요구 스탯이 그 레벨 기준이 된다
 * (v2.6.2 렙제: 아직 그 레벨이 아니어도 올릴 수 있고, 창에 "N렙부터 사용" 을 알림).
 * 비용: 에메랄드 + 별철 광석(레이드 재료). 강화 단계(v1.5.0 enhance.js)는 그대로 옮겨 감.
 *
 * 창 (상자 모양 9x3): 윗줄 [무기 강화로]·에메랄드·안내·별철 광석·[보스 무기 제작으로], 아래 두 줄에 올릴 수 있는 영웅 무기 (최대 18개)
 *
 * 보스 무기 제작 (v2.0.0, 세 번째 창): 재료 + 에메랄드 + 별철 광석 → 무기 (config.js BOSS_CRAFT).
 *   v2.9.0: 무기마다 여러 보스의 재료 + 일반 몬스터 전리품을 섞음 (주재료 · 다른 보스 재료 · 전리품, craftMaterials). 영웅 4 · 초월 5 (v2.16.0 티르빙 — 길이 없는 제작 전용 마검)
 *   전설 무기 넷 (듀란드의 방패 · 잃어버린 엘프의 활 · 그람 · 아이기스의 방패) 은 만들 수 없고 그 무기 길의 전설 퀘스트 보상 (sidequest.js LEGENDS)
 *   창: 윗줄 [무기 강화로]·에메랄드·쪽 바꾸기·별철 광석·[레벨 올리기로], 가운데 줄 = 그 쪽의 무기, 아랫줄 = 그 쪽 무기들의 재료 (가진 수)
 *   v2.5.0: 등급마다 한 쪽 (영웅 30렙 · 초월 40렙). 윗줄 가운데 칸을 누르면 다른 쪽 (한 쪽에 9줄까지)
 */
const TAB_SLOT = 0;
const CRAFT_TAB_SLOT = 8;
const EMERALD_SLOT = 2;
const INFO_SLOT = 4;
const ORE_SLOT = 6;
const FIRST = 9;
const MAX_LIST = 18;

/** 별철 광석 아이템 (설명 포함) */
export function createOreItem(amount = 1) {
  const item = new ItemStack(SMITH.oreItem, Math.max(1, Math.min(64, amount)));
  setLoreSafe(item, SMITH.oreLore);
  return item;
}

/** 무기의 다음 단계 (없으면 undefined) */
export function nextLevelOf(w) {
  if (!w || w.grade.id !== "hero" || w.legacy) return undefined;
  const next = w.level + SMITH.step;
  if (!isValidWeapon(next, "hero")) return undefined;
  const cost = SMITH.cost[w.level];
  if (!cost) return undefined;
  const to = weaponFrom(w.type, next, "hero");
  return to ? { to, cost } : undefined;
}

/** 인벤토리의 영웅 무기 중 레벨을 올릴 수 있는 것 [{slot, item, w, to, cost}] */
export function raisable(player) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  const out = [];
  for (let i = 0; inv && i < inv.size && out.length < MAX_LIST; i++) {
    const item = inv.getItem(i);
    const w = parseWeapon(item?.typeId);
    const n = nextLevelOf(w);
    if (item && w && n) out.push({ slot: i, item, w, to: n.to, cost: n.cost });
  }
  return out;
}

function statLine(w) {
  if (w.type === "shield") return `공격력 ${w.damage} · 감소 ${Math.round(w.passive * 1000) / 10}%`;
  return `공격력 ${w.damage} · 치명타 ${Math.round(w.critChance * 1000) / 10}%`;
}

/** v2.6.2 렙제: 이 무기를 아직 쓸 수 없으면 [무기 레벨, 지금 최고 레벨], 아니면 undefined */
function levelGap(player, w) {
  const lv = reqLevelOf(player);
  return needsLevel(w) && lv < w.level ? [w.level, lv] : undefined;
}

function costText(cost) {
  return `${CONFIG.CURRENCY_NAME} ${cost.emerald} · ${SMITH.oreName} ${cost.ore}`;
}

function buildForm(player, list, message) {
  const em = countItem(player, CONFIG.CURRENCY_ITEM);
  const ore = countItem(player, SMITH.oreItem);
  const form = new ChestForm("대장장이 §8· §7영웅 무기 레벨 올리기");
  form.set(TAB_SLOT, { icon: "textures/items/nether_star", name: "§6무기 강화로", lore: ["§7+1 ~ +10 무기 강화 창"] });
  // 보유 개수는 아이콘 위 숫자로 보인다 (장식 칸은 숫자가 안 보여서 상점 잔액 칸처럼 일반 칸으로, 누르면 새로고침)
  form.set(EMERALD_SLOT, { icon: CONFIG.CURRENCY_ICON, count: em, name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${em}` });
  form.set(INFO_SLOT, {
    icon: "textures/items/iron_ingot",
    name: "§6레벨 제한 올리기",
    lore: [`§7영웅 무기: 30 → 35 → 40렙`, "§7기본 성능·요구 레벨·스탯이", "§7올린 레벨 기준이 됩니다"],
    deco: true,
  });
  form.set(ORE_SLOT, { icon: SMITH.oreIcon, count: ore, name: `§e보유 ${SMITH.oreName} §f${ore}` });
  form.set(CRAFT_TAB_SLOT, { icon: "textures/items/iron_sword", name: "§6보스 무기 제작으로", lore: ["§7보스 재료로 영웅·초월 무기 만들기"] });
  list.forEach((e, i) => {
    const gap = levelGap(player, e.to);
    form.set(FIRST + i, {
      icon: e.w.icon,
      name: e.w.name,
      lore: [
        `§r§7레벨 제한 §f${e.w.level} → ${e.to.level}`,
        `§r§7${statLine(e.w)}`,
        `§r§f→ ${statLine(e.to)}`,
        `§r§a${costText(e.cost)}`,
        ...(gap ? [`§r§c${gap[0]}렙부터 사용 · 최고 ${gap[1]}렙`] : []),
      ],
    });
  });
  form.fillEmpty();
  const lines = [];
  if (list.length === 0) lines.push("§8올릴 수 있는 영웅 무기가 없습니다. §0(30·35렙 영웅 무기를 가져오세요)§r");
  else lines.push("§8무기 칸을 누르면 확인 창이 열립니다.§r");
  lines.push(`§8비용: 30→35렙 §0${costText(SMITH.cost[30])}§r`);
  lines.push(`§8      35→40렙 §0${costText(SMITH.cost[35])}§r`);
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return form;
}

async function confirm(player, e) {
  const em = countItem(player, CONFIG.CURRENCY_ITEM);
  const ore = countItem(player, SMITH.oreItem);
  const form = new ChestForm("레벨 제한 올리기");
  form.set(11, { icon: "textures/rpg_ui/icon_check", name: "§a올리기" });
  form.set(13, { icon: e.to.icon, name: e.to.name, lore: weaponInfo(e.to) });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const gap = levelGap(player, e.to);
  const lines = [
    `${e.w.darkName} §8→ §0${e.to.level}렙§r`,
    `§8${statLine(e.w)} §8→ §0${statLine(e.to)}§r`,
    `§8요구 스탯: ${reqsText(e.to.reqs, true)}§r`,
    ...(gap ? [`§4${gap[0]}렙부터 사용 가능 §8(지금 최고 ${gap[1]})§r`] : []), // 휴대폰 창 한 줄 (180px)
    `§8비용: §0${costText(e.cost)}§8 · 보유 §0${em}§8 / §0${ore}§r`,
    "§8왼쪽 초록 칸: 올리기 · 오른쪽 빨간 칸: 취소§r",
  ];
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  if (sel !== 11) return "";
  return raise(player, e);
}

/**
 * 레벨 올리기 처리. 결과 문구를 돌려준다 (창 본문용).
 * 무기가 그 칸에 그대로 있는지, 비용이 있는지 다시 확인한 뒤 비용을 내고 무기를 바꾼다.
 */
export function raise(player, e) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  const cur = inv?.getItem(e.slot);
  const head = player.getHeadLocation();
  if (!inv || !cur || cur.typeId !== e.w.id) return "§4무기가 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
  const em = countItem(player, CONFIG.CURRENCY_ITEM);
  const ore = countItem(player, SMITH.oreItem);
  if (em < e.cost.emerald || ore < e.cost.ore) {
    player.playSound("note.bass", { pitch: 0.6 });
    particle(player.dimension, "minecraft:villager_angry", { x: head.x, y: head.y + 0.5, z: head.z });
    const lack = [];
    if (em < e.cost.emerald) lack.push(`${CONFIG.CURRENCY_NAME} ${e.cost.emerald - em}`);
    if (ore < e.cost.ore) lack.push(`${SMITH.oreName} ${e.cost.ore - ore}`);
    notify(player, `§c재료 부족: ${lack.join(", ")} 더 필요`, 40);
    return `§4재료 부족: ${lack.join(", ")} 더 필요§r`;
  }
  // 새 무기를 먼저 만든다 (실패하면 비용을 받지 않음)
  let next;
  try {
    next = new ItemStack(e.to.id, 1);
    // 강화 단계(v1.5.0)와 직접 붙인 이름은 그대로 (이름 속 레벨은 새 레벨로)
    const custom = cur.getDynamicProperty(NAME_PROP);
    if (typeof custom === "string") next.setDynamicProperty(NAME_PROP, custom);
    if (cur.nameTag) next.nameTag = cur.nameTag;
    writeEnhance(next, e.to, enhanceOf(cur));
  } catch (err) {
    console.warn(`[rpg] smith ${e.to.id}: ${err}`);
    return "§4무기를 만들 수 없습니다. 행동 팩 적용 상태를 확인하세요.§r";
  }
  if (!removeItem(player, CONFIG.CURRENCY_ITEM, e.cost.emerald) || !removeItem(player, SMITH.oreItem, e.cost.ore)) return "§4결제 오류§r";
  inv.setItem(e.slot, next);
  play(player.dimension, "random.anvil_use", head, 1.0, 0.8);
  play(player.dimension, "random.levelup", head, 1.4, 0.5);
  for (let i = 0; i < 6; i++) particle(player.dimension, "minecraft:lava_particle", { x: head.x, y: head.y - 0.5, z: head.z });
  particle(player.dimension, "minecraft:totem_particle", { x: head.x, y: head.y - 0.3, z: head.z });
  notify(player, `§6레벨 제한 ${e.w.level} → ${e.to.level}: ${e.to.name}${levelNote(e.to, reqLevelOf(player))}`, 60);
  return `§2완료: ${e.to.darkName} §8(${statLine(e.to)})§r`;
}

// ------------------------------------------------------------ 보스 무기 제작 (v2.0.0, v2.9.0 재료 섞기)
const CRAFT_INFO_SLOT = 4;
const CRAFT_FIRST = 9; // 가운데 줄: 만들 무기 (최대 9개)
const MAT_FIRST = 18; // 아랫줄: 그 쪽 무기들의 재료 (최대 9종)

/** 보스 재료 아이템 (설명 포함) */
export function bossMaterialItem(kind, amount = 1) {
  const m = BOSS_MATERIALS[kind];
  const item = new ItemStack(m.item, Math.max(1, Math.min(64, amount)));
  setLoreSafe(item, m.lore);
  return item;
}

/**
 * 재료 하나의 보이는 정보: 아이템 · 이름 · 색 · 아이콘 · 나오는 곳 (보스 이름 또는 "몬스터 (Lv.N)")
 * @param {{key: string, kind: string, count: number}} m
 */
function matInfo(m) {
  if (m.kind === "boss") {
    const b = BOSS_MATERIALS[m.key];
    return b ? { ...m, item: b.item, name: b.name, color: b.color, icon: b.icon, from: BOSSES[b.boss]?.name ?? b.boss } : undefined;
  }
  const d = MONSTER_DROPS[m.key];
  const mob = MONSTERS[m.key];
  return d ? { ...m, item: d.item, name: d.name, color: d.color, icon: d.icon, from: mob ? `${mob.name} Lv.${mob.level}` : m.key } : undefined;
}

/**
 * 제작 목록 (config.js BOSS_CRAFT 중 무기와 재료가 모두 있는 것, 적힌 순서). mats = 재료 [주재료, 다른 보스 재료…, 전리품…]
 * @returns {{r: any, w: any, mat: any, mats: any[]}[]}
 */
export function craftRecipes() {
  const out = [];
  for (const r of BOSS_CRAFT) {
    const w = weaponFrom(r.type, r.level, r.grade);
    const mats = craftMaterials(r).map(matInfo);
    if (w && !w.legacy && mats.length && mats.every(Boolean)) out.push({ r, w, mat: mats[0], mats });
  }
  return out;
}

/** 제작 창의 쪽 (v2.5.0): 등급마다 한 쪽 — 영웅 30렙 · 초월 40렙 */
export const CRAFT_PAGES = [
  { grade: "hero", label: "영웅 30렙" },
  { grade: "transcend", label: "초월 40렙" },
];

/** 한 쪽의 제작 목록 (가운데 줄 9칸까지) */
export function craftPage(list, page) {
  const g = CRAFT_PAGES[page]?.grade;
  return list.filter((e) => e.r.grade === g).slice(0, 9);
}

/**
 * 한 쪽 무기들의 재료 (아랫줄 9칸): 주재료 → 전리품 → 다른 보스 재료 (무기마다 둘째 재료, 그다음 셋째 재료 순서), 같은 재료는 한 번.
 * 9종이 넘으면 앞의 8종 + 아홉째 칸에 나머지 (rest: "그 밖의 재료")
 * @param {{mats: any[]}[]} list
 * @returns {{shown: any[], rest: any[]}}
 */
export function pageMaterials(list) {
  /** @type {Map<string, any>} */
  const seen = new Map();
  const add = (m) => {
    if (m && !seen.has(m.item)) seen.set(m.item, m);
  };
  for (const e of list) add(e.mats[0]);
  for (const e of list) for (const m of e.mats) if (m.kind === "drop") add(m);
  const depth = Math.max(0, ...list.map((e) => e.mats.length));
  for (let c = 1; c < depth; c++) for (const e of list) if (e.mats[c]?.kind === "boss") add(e.mats[c]);
  const all = [...seen.values()];
  return all.length <= 9 ? { shown: all, rest: [] } : { shown: all.slice(0, 8), rest: all.slice(8) };
}

/** 처음 여는 쪽: 그 쪽 무기들의 재료를 더 많이 모은 쪽 (무기마다 필요한 만큼까지 셈, 같으면 영웅) */
export function firstCraftPage(player, list) {
  /** @type {Map<string, number>} */
  const have = new Map();
  const held = (item) => {
    if (!have.has(item)) have.set(item, countItem(player, item));
    return have.get(item) ?? 0;
  };
  const score = CRAFT_PAGES.map((_, i) => craftPage(list, i).reduce((n, e) => n + e.mats.reduce((k, m) => k + Math.min(held(m.item), m.count), 0), 0));
  return score[1] > score[0] ? 1 : 0;
}

function craftCostText(e) {
  return [...e.mats.map((m) => `${m.name} ${m.count}`), `${CONFIG.CURRENCY_NAME} ${e.r.emerald}`, `${SMITH.oreName} ${e.r.ore}`].join(" · ");
}

/**
 * 모자란 것 ["칠흑 먹물 3", ...] (없으면 빈 목록). 같은 아이템이 여러 줄이면 합쳐서 셈 (재료 · 에메랄드 · 별철 광석 —
 * 모두 있는 것을 확인한 뒤 craft 가 차례로 내므로, 내는 도중에 모자라 앞의 것만 사라지는 일이 없게)
 */
export function craftLack(player, e) {
  /** @type {Map<string, {name: string, need: number}>} */
  const need = new Map();
  const want = (item, name, n) => {
    const x = need.get(item);
    if (x) x.need += n;
    else need.set(item, { name, need: n });
  };
  for (const m of e.mats) want(m.item, m.name, m.count);
  want(CONFIG.CURRENCY_ITEM, CONFIG.CURRENCY_NAME, e.r.emerald);
  want(SMITH.oreItem, SMITH.oreName, e.r.ore);
  const lack = [];
  for (const [item, x] of need) {
    const have = countItem(player, item);
    if (have < x.need) lack.push(`${x.name} ${x.need - have}`);
  }
  return lack;
}

/** 무기 칸 설명의 재료 한 줄: "칠흑 먹물 3/8 (크라켄)" — 채웠으면 초록 */
function matLine(player, m) {
  const have = countItem(player, m.item);
  return `§r${have >= m.count ? "§a" : "§7"}${m.name} §f${Math.min(have, m.count)}/${m.count} §8(${m.from})`;
}

/**
 * @param {any} player
 * @param {{r: any, w: any, mat: any, mats: any[]}[]} list 이 쪽의 제작 목록 (craftPage)
 * @param {string} message
 * @param {number} [page] 0 = 영웅 · 1 = 초월
 */
function craftForm(player, list, message, page = 0) {
  const em = countItem(player, CONFIG.CURRENCY_ITEM);
  const ore = countItem(player, SMITH.oreItem);
  const P = CRAFT_PAGES[page] ?? CRAFT_PAGES[0];
  const other = CRAFT_PAGES[1 - page] ?? CRAFT_PAGES[1];
  const form = new ChestForm(`대장장이 §8· §7보스 무기 제작 §8· §7${P.label}`);
  form.set(TAB_SLOT, { icon: "textures/items/nether_star", name: "§6무기 강화로", lore: ["§7+1 ~ +10 무기 강화 창"] });
  form.set(EMERALD_SLOT, { icon: CONFIG.CURRENCY_ICON, count: em, name: `§a보유 ${CONFIG.CURRENCY_NAME} §f${em}` });
  form.set(CRAFT_INFO_SLOT, {
    icon: page === 0 ? "textures/items/diamond_sword" : "textures/items/netherite_sword",
    name: `§6보스 무기 제작 §7· §f${P.label}`,
    lore: [`§e누르면 ${other.label} 무기 보기`, "§7여러 보스의 재료와 일반 몬스터", "§7전리품을 섞어 무기를 만듭니다", "§7(강화 +0) 영웅 무기는 30렙으로 만들어", "§7레벨 올리기 창에서 40렙까지", "§7초월 무기는 40렙으로 만듦"],
  });
  form.set(ORE_SLOT, { icon: SMITH.oreIcon, count: ore, name: `§e보유 ${SMITH.oreName} §f${ore}` });
  form.set(CRAFT_TAB_SLOT, { icon: "textures/items/iron_ingot", name: "§6레벨 올리기로", lore: ["§7영웅 무기 레벨 제한 올리기 창"] });
  list.forEach((e, i) => {
    const ok = craftLack(player, e).length === 0;
    const gap = levelGap(player, e.w);
    form.set(CRAFT_FIRST + i, {
      icon: e.w.icon,
      name: e.w.name,
      lore: [
        `§r§7${e.w.typeLabel} · ${statLine(e.w)}`,
        ...e.mats.map((m) => matLine(player, m)),
        `§r§7${CONFIG.CURRENCY_NAME} §f${e.r.emerald} §7· ${SMITH.oreName} §f${e.r.ore}`,
        ok ? "§r§a만들 수 있음" : "§r§c재료 부족",
        ...(gap ? [`§r§c${gap[0]}렙부터 사용 · 최고 ${gap[1]}렙`] : []),
      ],
    });
  });
  // 아랫줄: 이 쪽 무기들의 재료 (가진 수). 9종이 넘으면 마지막 칸 = 그 밖의 재료 (설명에 하나씩)
  const pm = pageMaterials(list);
  pm.shown.forEach((m, i) => {
    const n = countItem(player, m.item);
    form.set(MAT_FIRST + i, { icon: m.icon, count: n, name: `${m.color}보유 ${m.name} §f${n}`, lore: [`§7${m.kind === "boss" ? `${m.from}에게서 얻음` : `${m.from} 전리품`}`] });
  });
  if (pm.rest.length) {
    const n = pm.rest.reduce((k, m) => k + countItem(player, m.item), 0);
    form.set(MAT_FIRST + pm.shown.length, {
      icon: pm.rest[0].icon,
      count: n,
      name: `§7그 밖의 재료 §f${pm.rest.length}종`,
      lore: pm.rest.map((m) => `§r§7${m.name} §f${countItem(player, m.item)} §8(${m.from})`),
    });
  }
  form.fillEmpty();
  const lines = [list.length ? `§8무기 칸을 누르면 확인 창이 열립니다. 윗줄 가운데 칸: ${other.label} 쪽.§r` : "§8만들 수 있는 보스 무기가 없습니다.§r"];
  lines.push(`§8재료: 여러 보스의 재료 + 일반 몬스터 전리품 (무기 칸 설명).§r`);
  lines.push(`§8아랫줄: 이 쪽 무기들의 재료를 가진 수.§r`);
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return form;
}

async function confirmCraft(player, e) {
  const form = new ChestForm("보스 무기 제작");
  form.set(11, { icon: "textures/rpg_ui/icon_check", name: "§a만들기" });
  form.set(13, { icon: e.w.icon, name: e.w.name, lore: weaponInfo(e.w) });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const gap = levelGap(player, e.w);
  // 재료는 "이름 가진 수/필요" 로 한 줄에 둘씩 (휴대폰 창 한 줄 180px)
  const mats = e.mats.map((m) => {
    const have = countItem(player, m.item);
    return `${have >= m.count ? "§2" : "§4"}${m.name} ${have}/${m.count}`;
  });
  const matRows = [];
  for (let i = 0; i < mats.length; i += 2) matRows.push(`§8${mats.slice(i, i + 2).join(" §8· ")}§r`);
  const lines = [
    `${e.w.darkName}§r`,
    `§8${statLine(e.w)} · 요구 스탯: ${reqsText(e.w.reqs, true)}§r`,
    ...(gap ? [`§4${gap[0]}렙부터 사용 가능 §8(지금 최고 ${gap[1]})§r`] : []),
    ...matRows,
    `§8${CONFIG.CURRENCY_NAME} §0${countItem(player, CONFIG.CURRENCY_ITEM)}/${e.r.emerald} §8· ${SMITH.oreName} §0${countItem(player, SMITH.oreItem)}/${e.r.ore}§r`,
    "§8왼쪽 초록 칸: 만들기 · 오른쪽 빨간 칸: 취소§r",
  ];
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  if (sel !== 11) return "";
  return craft(player, e);
}

/**
 * 보스 무기 만들기. 결과 문구를 돌려준다 (창 본문용). 비용을 다시 확인한 뒤 무기를 먼저 만들고, 비용을 내고 준다 (가방이 차면 발밑에)
 */
export function craft(player, e) {
  const head = player.getHeadLocation();
  const lack = craftLack(player, e);
  if (lack.length) {
    player.playSound("note.bass", { pitch: 0.6 });
    particle(player.dimension, "minecraft:villager_angry", { x: head.x, y: head.y + 0.5, z: head.z });
    notify(player, `§c재료 부족: ${lack.join(", ")} 더 필요`, 40);
    return `§4재료 부족: ${lack.join(", ")} 더 필요§r`;
  }
  let item;
  try {
    item = createWeaponItem(e.w);
  } catch (err) {
    console.warn(`[rpg] boss craft ${e.w.id}: ${err}`);
    return "§4무기를 만들 수 없습니다. 행동 팩 적용 상태를 확인하세요.§r";
  }
  // 모두 있는 것을 위에서 확인했으므로 차례로 냄 (하나라도 실패하면 그 뒤는 내지 않음)
  for (const m of e.mats) if (!removeItem(player, m.item, m.count)) return "§4결제 오류§r";
  if (!removeItem(player, CONFIG.CURRENCY_ITEM, e.r.emerald) || !removeItem(player, SMITH.oreItem, e.r.ore)) return "§4결제 오류§r";
  giveItem(player, item);
  play(player.dimension, "random.anvil_use", head, 0.9, 0.9);
  play(player.dimension, "random.levelup", head, 1.2, 0.6);
  for (let i = 0; i < 8; i++) particle(player.dimension, "minecraft:lava_particle", { x: head.x, y: head.y - 0.5, z: head.z });
  particle(player.dimension, "minecraft:totem_particle", { x: head.x, y: head.y - 0.3, z: head.z });
  notify(player, `§6보스 무기 제작: ${e.w.name}${levelNote(e.w, reqLevelOf(player))}`, 60);
  return `§2완료: ${e.w.darkName} §8(${statLine(e.w)})§r`;
}

/**
 * 보스 무기 제작 창 반복 (폼 잠금 안에서). "enhance" → 강화 창, "smith" → 레벨 올리기 창
 * @param {import("@minecraft/server").Player} player
 */
export async function craftLoop(player) {
  let message = "";
  let page = firstCraftPage(player, craftRecipes());
  for (let guard = 0; guard < 100; guard++) {
    const list = craftPage(craftRecipes(), page);
    const sel = await craftForm(player, list, message, page).show(player);
    if (sel < 0) return undefined;
    if (sel === TAB_SLOT) return "enhance";
    if (sel === CRAFT_TAB_SLOT) return "smith";
    // v2.5.0: 윗줄 가운데 칸 = 다른 쪽 (영웅 ↔ 초월)
    if (sel === CRAFT_INFO_SLOT) {
      page = 1 - page;
      message = "";
      continue;
    }
    const e = list[sel - CRAFT_FIRST];
    if (!e || sel >= MAT_FIRST) {
      message = "";
      continue;
    }
    message = await confirmCraft(player, e);
  }
  return undefined;
}

/**
 * 레벨 올리기 창 반복 (이미 폼 잠금 안에 있을 때). "enhance" 를 돌려주면 강화 창으로, "craft" 면 보스 무기 제작 창으로
 * @param {import("@minecraft/server").Player} player
 */
export async function raiseLoop(player) {
  let message = "";
  for (let guard = 0; guard < 100; guard++) {
    const list = raisable(player);
    const sel = await buildForm(player, list, message).show(player);
    if (sel < 0) return undefined;
    if (sel === TAB_SLOT) return "enhance";
    if (sel === CRAFT_TAB_SLOT) return "craft";
    const e = list[sel - FIRST];
    if (!e) {
      message = "";
      continue;
    }
    message = await confirm(player, e);
  }
  return undefined;
}

/**
 * 대장장이 창 (레벨 올리기 ↔ 무기 강화 ↔ 보스 무기 제작, 윗줄 양 끝 칸으로 오감). 이미 폼 잠금 안에 있을 때 (예: NPC 대화에서)
 * @param {import("@minecraft/server").Player} player
 * @param {"smith" | "enhance" | "craft"} [start]
 */
export async function smithLoop(player, start = "smith") {
  /** @type {string | undefined} */
  let next = start;
  for (let i = 0; next && i < 20; i++) next = next === "enhance" ? await enhanceLoop(player) : next === "craft" ? await craftLoop(player) : await raiseLoop(player);
}

/** 대장장이 창 열기 (명령어용) */
export function openSmith(player, start = "smith") {
  return withFormLock(player, () => smithLoop(player, /** @type {any} */ (start)));
}
