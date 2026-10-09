import { ItemStack, system, world } from "@minecraft/server";
import { ENHANCE, RAID_MATERIALS, enhanceBonus, enhanceCost } from "./config.js";
import { ChestForm, wait } from "./chestui.js";
import { notify, particle, play } from "./fx.js";
import { showRealTitle } from "./hp.js";
import { countItem, giveItem, removeItem } from "./shop.js";
import { add, burst, flat, fx, ring, rightOf, rune as floorRune, sparks, up } from "./skills.js";
import { enhanceOf, parseWeapon, setLoreSafe, weaponInfo, writeEnhance } from "./weapons.js";

/**
 * 무기 강화 (v1.5.0, 대장장이). +1 ~ +10
 * - 단계마다 성공 확률·재료 (config.js ENHANCE). 재료: 강화석, +6 부터 빛나는 강화석
 * - 재료는 망치질(연출)이 끝나 결과를 정하는 순간에 받는다 → 도중에 나가거나 무기·재료를 옮기면 아무것도 쓰지 않고 취소
 * - 1~5강은 실패해도 그대로, 6강부터 실패하면 한 단계 떨어짐. 수호 주문서를 쓰면 그대로 (실패했을 때만 사라짐)
 * - 강화 단계는 아이템에 저장 (weapons.js ENHANCE_PROP), 이름 앞에 +N, 무기 피해 × (1 + 보너스)
 * - announceFrom(7)강 이상 성공: 서버 모두에게 "○○님이 초월 등급 [그람] 7강을 성공하였습니다!" + 폭죽
 *
 * 창 (상자 9x3): 윗줄 [레벨 올리기로] · 강화석 · 빛나는 강화석 · 안내 · 수호 주문서 · [보스 무기 제작으로] (v2.0.0), 아래 두 줄에 가진 무기 (최대 18개)
 */
const TAB_SLOT = 0;
const CRAFT_TAB_SLOT = 8;
const STONE_SLOT = 2;
const BRIGHT_SLOT = 3;
const INFO_SLOT = 4;
const SCROLL_SLOT = 6;
const FIRST = 9;
const MAX_LIST = 18;
const GOLD = [1.0, 0.82, 0.3];
const WHITE = [1, 1, 1];
const SMOKE = [0.45, 0.45, 0.5];
const RED = [0.9, 0.2, 0.15];

const M = RAID_MATERIALS;
const pct = (x) => `${Math.round(x * 100)}%`;

/** 인벤토리의 RPG 무기 [{slot, item, w, n}] (최대 18개, 핫바부터) */
export function enhanceable(player) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  const out = [];
  for (let i = 0; inv && i < inv.size && out.length < MAX_LIST; i++) {
    const item = inv.getItem(i);
    const w = parseWeapon(item?.typeId);
    if (item && w) out.push({ slot: i, item, w, n: enhanceOf(item) });
  }
  return out;
}

function costText(c, dark = false) {
  const V = dark ? "§0" : "§f";
  const L = dark ? "§8" : "§7";
  const parts = [`${M.stone.name} ${V}${c.stone}${L}`];
  if (c.bright > 0) parts.push(`${M.bright.name} ${V}${c.bright}${L}`);
  return parts.join(" · ");
}

function failText(c) {
  return c.down ? "실패하면 한 단계 떨어짐 (수호 주문서로 방지)" : "실패해도 그대로";
}

/** 무기 칸 설명 */
function slotLore(e) {
  if (e.n >= ENHANCE.max) return [`§r§6최대 강화 +${ENHANCE.max}`, `§r§7피해 +${pct(enhanceBonus(e.n))}`];
  const c = enhanceCost(e.n, e.w.grade.id);
  if (!c) return [];
  return [
    `§r§7강화 §f+${e.n} → +${e.n + 1}§7 · 성공 §a${pct(c.rate)}`,
    `§r§7피해 +${pct(enhanceBonus(e.n))} → §f+${pct(enhanceBonus(e.n + 1))}`,
    `§r§7재료: ${costText(c)}`,
    `§r${c.down ? "§c" : "§8"}${failText(c)}`,
  ];
}

function buildForm(player, list, message) {
  const st = countItem(player, M.stone.item);
  const br = countItem(player, M.bright.item);
  const sc = countItem(player, M.scroll.item);
  const form = new ChestForm("대장장이 §8· §7무기 강화");
  form.set(TAB_SLOT, { icon: "textures/items/iron_ingot", name: "§6레벨 올리기로", lore: ["§7영웅 무기 레벨 제한 올리기 창"] });
  form.set(STONE_SLOT, { icon: M.stone.icon, count: st, name: `${M.stone.color}보유 ${M.stone.name} §f${st}` });
  form.set(BRIGHT_SLOT, { icon: M.bright.icon, count: br, name: `${M.bright.color}보유 ${M.bright.name} §f${br}` });
  form.set(INFO_SLOT, {
    icon: "textures/items/nether_star",
    name: "§6무기 강화 §f(+1 ~ +10)",
    lore: ["§7성공하면 무기 피해가 오릅니다", `§7+${ENHANCE.downFrom}부터 실패하면 한 단계 하락`, `§7${M.scroll.name}가 있으면 하락 방지`, `§7+${ENHANCE.announceFrom} 이상 성공은 서버 전체 알림`],
    deco: true,
  });
  form.set(SCROLL_SLOT, { icon: M.scroll.icon, count: sc, name: `${M.scroll.color}보유 ${M.scroll.name} §f${sc}` });
  form.set(CRAFT_TAB_SLOT, { icon: "textures/items/iron_sword", name: "§6보스 무기 제작으로", lore: ["§7보스 재료로 영웅·초월 무기 만들기"] });
  list.forEach((e, i) => {
    form.set(FIRST + i, { icon: e.w.icon, name: e.n > 0 ? `§r${e.w.grade.color}+${e.n} ${e.w.title}` : e.w.name, lore: slotLore(e) });
  });
  form.fillEmpty();
  const lines = [];
  if (list.length === 0) lines.push("§8강화할 RPG 무기가 없습니다. §0(무기를 가지고 오세요)§r");
  else lines.push("§8무기 칸을 누르면 확인 창이 열립니다.§r");
  lines.push(`§8성공: +1 §095%§8 · +5 §065%§8 · +7 §045%§8 · +10 §015%§r`);
  lines.push(`§8+1~+5 실패: 그대로 · +6~+10 실패: §4한 단계 하락§r`);
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return form;
}

/** 확인 창. 누른 것: "go" | "guard" | "" */
async function confirm(player, e) {
  const c = enhanceCost(e.n, e.w.grade.id);
  if (!c) return "";
  const st = countItem(player, M.stone.item);
  const br = countItem(player, M.bright.item);
  const sc = countItem(player, M.scroll.item);
  const form = new ChestForm(`무기 강화 §8· §7+${e.n} → +${e.n + 1}`);
  form.set(11, { icon: "textures/rpg_ui/icon_check", name: `§a강화하기 §7(성공 ${pct(c.rate)})`, lore: [`§r§7${failText(c)}`] });
  if (c.down && sc > 0) form.set(12, { icon: M.scroll.icon, count: sc, name: `§6${M.scroll.name} 쓰고 강화`, lore: ["§r§7실패해도 단계가 떨어지지 않음", "§r§7(실패했을 때만 사라짐)"] });
  form.set(13, { icon: e.w.icon, name: e.n > 0 ? `§r${e.w.grade.color}+${e.n} ${e.w.title}` : e.w.name, lore: weaponInfo(e.w, false, e.n) });
  form.set(15, { icon: "textures/rpg_ui/icon_cross", name: "§c취소" });
  form.fillEmpty();
  const lines = [
    `${e.n > 0 ? `§0+${e.n} ` : ""}${e.w.darkName} §8→ §0§l+${e.n + 1}§r`,
    `§8성공 확률 §2§l${pct(c.rate)}§r§8 · 피해 +${pct(enhanceBonus(e.n))} → §0+${pct(enhanceBonus(e.n + 1))}§r`,
    `§8재료 ${costText(c, true)} §8(보유 §0${st}§8${c.bright > 0 ? ` / §0${br}` : ""}§8)§r`,
    `${c.down ? "§4" : "§8"}${failText(c)}§r`,
    `§8초록 칸: 강화${c.down && sc > 0 ? " · 주문서 칸: 하락 방지" : ""} · 빨간 칸: 취소§r`,
  ];
  form.body(lines.join("\n"));
  const sel = await form.show(player);
  if (sel === 11) return "go";
  if (sel === 12 && c.down && sc > 0) return "guard";
  return "";
}

/** 눈앞 연출 자리 (뽑기 마법진과 같은 자리: 눈앞 2.2칸, 조준점 조금 아래) */
function altarOf(player) {
  const f = flat(player.getViewDirection());
  const ground = add(player.location, f, 2.2);
  return { at: up(ground, 1.35), ground, f, side: rightOf(f) };
}

/** 망치질 연출 (animTicks 동안): 눈앞 마법진이 한 칸씩 그려지고 불꽃이 모이며 모루 소리 3번 */
function hammerFx(player, e) {
  const color = [GOLD, GOLD, [0.8, 0.55, 1.0], [1.0, 0.6, 0.2], [1.0, 0.3, 0.3]][e.w.gradeIndex] ?? GOLD;
  const T = ENHANCE.animTicks;
  for (let t = 0; t < T; t += 2) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const dim = player.dimension;
      const { at, side } = altarOf(player);
      fx(dim, "rpg:magic_circle", at, { size: 0.5 + t * 0.01, life: 0.14, spin: t * 16, progress: Math.min(1, (t + 2) / T), color, alpha: 0.95 });
      const a = t * 0.8;
      const from = add(add(at, side, Math.cos(a) * 1.1), { x: 0, y: 1, z: 0 }, Math.sin(a) * 0.8);
      fx(dim, "rpg:arrow_spark", from, { dir: { x: at.x - from.x, y: at.y - from.y, z: at.z - from.z }, speed: 2.4, size: 0.12, grow: 0.5, life: 0.4, color: GOLD, alpha: 1 });
    }, t);
  }
  [0, 8, 16].forEach((t, i) => {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const { at } = altarOf(player);
      play(player.dimension, "random.anvil_use", at, 0.9 + i * 0.15, 0.7);
      sparks(player.dimension, at, GOLD, 6, 4);
    }, t);
  });
}

function successFx(player, n) {
  const dim = player.dimension;
  const { at, ground, f } = altarOf(player);
  fx(dim, "rpg:magic_circle_flash", at, { size: 0.9 + n * 0.04, life: 0.55, spin: Math.random() * 360, progress: 1, color: GOLD, alpha: 1 });
  burst(dim, at, GOLD, 1.3 + n * 0.08, 0.4);
  burst(dim, at, WHITE, 0.8, 0.3);
  ring(dim, at, f, GOLD, 0.8, 0.4, 2);
  sparks(dim, at, GOLD, 12, 6);
  floorRune(dim, ground, { size: 1.4, life: 0.8, color: GOLD, alpha: 0.9, rate: 90, grow: 0.3 });
  particle(dim, "minecraft:totem_particle", up(player.location, 1.2));
  play(dim, "random.levelup", player.location, 1.2 + n * 0.03, 0.9);
  play(dim, "random.anvil_use", player.location, 1.6, 0.5);
}

/** 높은 단계 성공: 서버 모두에게 알림 + 하늘로 폭죽 */
function announce(player, w, n) {
  const top = n >= ENHANCE.max ? " §d§l(최고 강화!)" : "";
  try {
    world.sendMessage(`§6§l[강화] §r§f${player.name}님이 ${w.grade.color}${w.grade.name} 등급§r §f[${w.title}] §e§l${n}강§r§f을 성공하였습니다!${top}`);
  } catch {
    // 무시
  }
  for (const pl of world.getAllPlayers()) {
    try {
      pl.playSound(n >= ENHANCE.max ? "firework.large_blast" : "firework.blast", { pitch: 1.0, volume: 0.8 });
      if (pl.id !== player.id) pl.playSound("firework.twinkle", { pitch: 1.2, volume: 0.5 });
    } catch {
      // 무시
    }
  }
  const colors = [GOLD, [1.0, 0.4, 0.4], [0.5, 0.8, 1.0], [0.7, 1.0, 0.5], [1.0, 0.55, 1.0]];
  const shots = n >= ENHANCE.max ? 7 : 4;
  // 강화한 사람의 눈앞 하늘 (1인칭 시야 안: 4.5~5.3칸 앞, 눈높이 +1.5~2.4칸), 왼쪽·오른쪽을 번갈아
  const order = shots === 7 ? [3, 1, 5, 0, 6, 2, 4] : [1, 2, 0, 3];
  const width = shots === 7 ? 6 : 4.8;
  for (let i = 0; i < shots; i++) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      const dim = player.dimension;
      const f = flat(player.getViewDirection());
      const eye = player.getHeadLocation();
      const p = add(add(eye, f, 4.5 + (i % 2) * 0.8), rightOf(f), (order[i] / (shots - 1) - 0.5) * width);
      p.y = eye.y + 1.5 + (i % 3) * 0.45;
      const col = colors[i % colors.length];
      fx(dim, "rpg:arrow_shot", up(p, -2.2), { dir: { x: 0, y: 1, z: 0 }, speed: 0, size: 2, thick: 0.08, life: 0.2, color: col, alpha: 0.8 }); // 올라가는 꼬리
      burst(dim, p, col, 1.8, 0.45);
      sparks(dim, p, col, 12, 6);
      play(dim, "firework.twinkle", p, 1.0 + i * 0.05, 0.6);
    }, 4 + i * 5);
  }
}

function failFx(player, down, guarded) {
  const dim = player.dimension;
  const { at } = altarOf(player);
  burst(dim, at, down ? RED : SMOKE, 1.0, 0.35);
  for (let i = 0; i < 4; i++) particle(dim, "minecraft:basic_smoke_particle", add(at, { x: (Math.random() - 0.5) * 0.8, y: Math.random() * 0.4, z: (Math.random() - 0.5) * 0.8 }));
  if (guarded) {
    ring(dim, at, flat(player.getViewDirection()), GOLD, 0.9, 0.45, 1.5);
    play(dim, "item.shield.block", at, 1.1, 0.8);
  }
  play(dim, down ? "random.anvil_break" : "random.anvil_land", at, down ? 0.8 : 0.6, 0.7);
  play(dim, "random.fizz", at, 0.8, 0.6);
}

/** 비용을 가졌는지. 모자라면 부족한 것 목록 */
function lacking(player, c, useScroll) {
  const out = [];
  const st = countItem(player, M.stone.item);
  const br = countItem(player, M.bright.item);
  if (st < c.stone) out.push(`${M.stone.name} ${c.stone - st}`);
  if (br < c.bright) out.push(`${M.bright.name} ${c.bright - br}`);
  if (useScroll && countItem(player, M.scroll.item) < 1) out.push(`${M.scroll.name} 1`);
  return out;
}

/** 레이드 재료 아이템 (설명 포함). kind: ore · stone · bright · scroll */
export function materialItem(kind, amount = 1) {
  const m = RAID_MATERIALS[kind];
  const item = new ItemStack(m.item, Math.max(1, Math.min(64, amount)));
  setLoreSafe(item, m.lore);
  return item;
}

/** 재료가 모자라면 알림 + 문구, 충분하면 undefined */
function shortage(player, c, guard) {
  const lack = lacking(player, c, guard);
  if (!lack.length) return undefined;
  player.playSound("note.bass", { pitch: 0.6 });
  notify(player, `§c재료 부족: ${lack.join(", ")} 더 필요`, 40);
  return `§4재료 부족: ${lack.join(", ")} 더 필요§r`;
}

/**
 * 강화 한 번 (확인 창에서 누른 뒤). 결과 문구를 돌려준다 (창 본문용).
 * 재료 확인 → 망치질 연출 → 무기·재료가 그대로면 그 틱에 재료를 받고 판정 (옮겼거나 나갔으면 아무것도 쓰지 않고 취소)
 * 수호 주문서는 실패했을 때만 쓴다
 * @param {import("@minecraft/server").Player} player
 */
export async function enhanceOnce(player, e, useScroll = false, rand = Math.random) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  const cur = inv?.getItem(e.slot);
  if (!inv || !cur || cur.typeId !== e.w.id || enhanceOf(cur) !== e.n) return "§4무기가 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
  const c = enhanceCost(e.n, e.w.grade.id);
  if (!c) return `§8이미 최대 강화 +${ENHANCE.max}입니다.§r`;
  const guard = useScroll && c.down;
  const short0 = shortage(player, c, guard);
  if (short0) return short0;
  hammerFx(player, e);
  await wait(ENHANCE.animTicks);
  if (!player.isValid) return "";
  const now = inv.getItem(e.slot);
  if (!now || now.typeId !== e.w.id || enhanceOf(now) !== e.n) return "§4강화 중에 무기를 옮겨서 취소했습니다. 재료는 쓰지 않았습니다.§r";
  const short1 = shortage(player, c, guard);
  if (short1) return short1;
  if (!removeItem(player, M.stone.item, c.stone) || (c.bright > 0 && !removeItem(player, M.bright.item, c.bright))) return "§4재료를 쓰지 못했습니다§r";
  const ok = rand() < c.rate;
  let n = e.n;
  if (ok) n = e.n + 1;
  else if (c.down && !guard) n = e.n - 1;
  if (!ok && guard && !removeItem(player, M.scroll.item, 1)) n = e.n - 1; // (위에서 확인했으므로 없는 일)
  writeEnhance(now, e.w, n);
  inv.setItem(e.slot, now);
  const name = `${e.w.grade.color}${e.w.title}`;
  if (ok) {
    successFx(player, n);
    showRealTitle(player, `§6§l+${n} 강화 성공!`, `§r${name}§r §7피해 +${pct(enhanceBonus(n))}`);
    if (n >= ENHANCE.announceFrom) announce(player, e.w, n);
    return `§2§l성공! §r§0+${n} ${e.w.darkName}§8 (피해 +${pct(enhanceBonus(n))})${guard ? " · 주문서는 쓰지 않았습니다" : ""}§r`;
  }
  failFx(player, n < e.n, guard);
  if (n < e.n) {
    showRealTitle(player, "§c강화 실패", `§c+${e.n} → +${n}`);
    return `§4실패: 한 단계 떨어졌습니다 (+${e.n} → +${n})§r`;
  }
  if (guard) {
    showRealTitle(player, "§e강화 실패", `§e${M.scroll.name}가 지켜 주었습니다 (+${n})`);
    return `§6실패: ${M.scroll.name}가 지켜 주어 +${n} 그대로입니다§r`;
  }
  showRealTitle(player, "§7강화 실패", `§7+${n} 그대로`);
  return `§8실패: +${n} 그대로입니다§r`;
}

/**
 * 강화 창 반복 (폼 잠금 안에서). "smith" 를 돌려주면 레벨 올리기 창으로, "craft" 면 보스 무기 제작 창으로 (v2.0.0)
 * @param {import("@minecraft/server").Player} player
 */
export async function enhanceLoop(player) {
  let message = "";
  for (let guard = 0; guard < 200; guard++) {
    const list = enhanceable(player);
    const sel = await buildForm(player, list, message).show(player);
    if (sel < 0) return undefined;
    if (sel === TAB_SLOT) return "smith";
    if (sel === CRAFT_TAB_SLOT) return "craft";
    const e = list[sel - FIRST];
    if (!e) {
      message = "";
      continue;
    }
    if (e.n >= ENHANCE.max) {
      message = `§8이미 최대 강화 +${ENHANCE.max}입니다.§r`;
      continue;
    }
    const pick = await confirm(player, e);
    if (!pick) {
      message = "";
      continue;
    }
    message = await enhanceOnce(player, e, pick === "guard");
  }
  return undefined;
}
