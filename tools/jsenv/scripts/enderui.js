import { GameMode, ItemTypes } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { showForm, withFormLock } from "./chestui.js";
import { ENDER } from "./config.js";
import { notify, particle, play } from "./fx.js";
import { BLOCK_FACES, EXTRA_IDS, EXTRA_TOLERANCE, FUTURE_ITEMS, ICON_TABLE_VERSION, KNOWN_EXTRA, MISSING_TOLERANCE, RPG_GLINT, RPG_ICONS, RPG_NAMES, SHIFTED_FROM, UNKNOWN_ICON, VANILLA_IDS, VANILLA_SPRITES } from "./itemicons.js";
import { add, rune, shock, up } from "./skills.js";

/**
 * 엔더 상자 창 (v2.11.0): 스탯 창 아랫줄 칸 (statui.js) → 상자 모양 창 (리소스 팩 ui/rpg_ender.json, 제목 앞 ENDER.marker)
 *   위: 내 엔더 상자 27칸 (스크립트 2.8.0 의 minecraft:ender_inventory — 게임의 엔더 상자와 같은 칸)
 *   아래: 인벤토리 27칸 + 핫바 9칸, 머리줄: 옮기는 양 (전부 · 절반 · 1개)
 *   칸을 누르면 반대쪽으로 옮김 (같은 아이템에 먼저 합치고, 남으면 빈칸에. 엔더 → 인벤토리는 윗칸 먼저, 핫바는 나중). 창은 다시 열려 바뀐 칸을 보여 줌
 * 칸 그림 (itemicons.js — build.py 가 만듦):
 *   이 팩의 아이템 = 팩의 그림 · 바닐라 아이템 = 게임 번호 × 65536 (UI 의 아이템 렌더러가 게임 그림을 그림, 블록은 입체)
 *   게임 번호는 게임 버전마다 달라져서, 게임의 바닐라 아이템 목록이 표의 버전과 맞을 때만 씀 (다르면 평면 그림 · 블록 옆면 · 물음표 — 엉뚱한 그림 대신)
 *   맞는지: 표의 이름 (명령어용 1623개) 이 거의 다 있고 (MISSING_TOLERANCE), 다음 버전 이름 (FUTURE_ITEMS) 이 없고,
 *   표 밖의 이름이 그 버전에 있을 수 있는 것 (KNOWN_EXTRA · 블록 전용 이름 규칙) 말고는 거의 없음 (EXTRA_TOLERANCE)
 *   SHIFTED_FROM 부터의 번호 (사과 · 꾸러미 · 창 등) 는 다른 애드온 아이템 수만큼 밀려 늘 평면 그림
 * 칸 글자 (UI 가 앞 6글자를 읽음): 종류 (i 아이템 · x 빈칸) + 개수 2자리 + 내구도 % 2자리 (00 = 막대 없음) + 반짝임 (g · -), 그 뒤는 설명 (이름 · 설명 줄 · 내구도 · 마법)
 */

/** 버튼 번호 (ui/rpg_ender.json 과 같게) */
export const ENDER_SLOTS = 27;
/** 27~53 = 인벤토리 9~35 (윗줄부터) */
export const INV_START = 27;
/** 54~62 = 핫바 0~8 */
export const HOTBAR_START = 54;
/** 63~65 = 옮기는 양 (전부 · 절반 · 1개) */
export const MODE_START = 63;
export const BUTTON_COUNT = 66;

const RPG_GLINT_SET = new Set(RPG_GLINT);
const KNOWN_EXTRA_SET = new Set(KNOWN_EXTRA);
const FUTURE_SET = new Set(FUTURE_ITEMS);
const ORDER_ENDER = Array.from({ length: ENDER_SLOTS }, (_, i) => i);
/** 엔더 상자 → 인벤토리: 합치기 · 빈칸 모두 윗칸 (9~35) 먼저, 핫바 (0~8) 는 나중 */
const ORDER_INV = [...Array.from({ length: 27 }, (_, i) => i + 9), ...Array.from({ length: 9 }, (_, i) => i)];
const own = (o, k) => Object.prototype.hasOwnProperty.call(o, k);

/** 버튼 번호 → 인벤토리 칸 */
export function invSlotOfButton(b) {
  return b < HOTBAR_START ? b - INV_START + 9 : b - HOTBAR_START;
}

// ------------------------------------------------------------ 칸 그림
/** @typedef {{ok: boolean, extra: number, missing: number, future: number, extraNames: string[], missingNames: string[], error?: string}} IconCheck */
/** @type {IconCheck | undefined} */
let iconCheck;

/** 블록 전용 이름 (겹반 블록 · 벽 표지판 · 서 있는 표지판 · 벽 산호 부채 · 양초 케이크) → 표에 있는 바탕 이름 */
const BLOCK_ONLY = { double_slab: ["_slab"], wall_sign: ["_sign", "_hanging_sign"], standing_sign: ["_sign", "_hanging_sign"], wall_fan: ["_fan"], candle_cake: ["_candle"] };

/** 표 (VANILLA_IDS) 의 버전 게임에 있을 수 있는 이름인지: 표 · KNOWN_EXTRA · 블록 아이템 (item.이름) · 바탕 이름이 표에 있는 블록 전용 이름 */
function knownName(short) {
  if (own(VANILLA_IDS, short) || KNOWN_EXTRA_SET.has(short)) return true;
  if (short.startsWith("item.")) return knownName(short.slice(5));
  const m = /^(.+)_(double_slab|wall_sign|standing_sign|wall_fan|candle_cake)$/.exec(short);
  return !!m && BLOCK_ONLY[m[2]].some((suffix) => own(VANILLA_IDS, m[1] + suffix));
}

/**
 * 게임의 바닐라 아이템 목록 (ItemTypes.getAll) 이 표 (ICON_TABLE_VERSION) 의 버전과 맞는지 (처음 한 번 셈). 맞을 때만 게임 번호로 그림.
 * extra = 모르는 이름 수 (게임이 더 새 버전일 수 있음), future = 다음 버전 이름 수, missing = 게임에 없는 표의 이름 수 (게임이 더 옛 버전)
 * 맞지 않거나 모르는 이름이 있으면 콘텐츠 로그에 이름을 남김 (다음 빌드 때 표 · 이름 목록을 고치려고)
 * @returns {IconCheck}
 */
export function enderIconCheck() {
  if (iconCheck) return iconCheck;
  /** @type {IconCheck} */
  const r = { ok: false, extra: 0, missing: 0, future: 0, extraNames: [], missingNames: [] };
  const note = (list, name) => {
    if (list.length < 12) list.push(name);
  };
  try {
    const game = new Set();
    for (const t of ItemTypes.getAll()) {
      const id = t.id;
      if (!id.startsWith("minecraft:")) continue;
      const short = id.slice(10);
      game.add(short);
      if (FUTURE_SET.has(short)) {
        r.future++;
        note(r.extraNames, short);
      } else if (!knownName(short)) {
        r.extra++;
        note(r.extraNames, short);
      }
    }
    for (const short of Object.keys(VANILLA_IDS)) {
      if (game.has(short)) continue;
      r.missing++;
      note(r.missingNames, short);
    }
    r.ok = SHIFTED_FROM !== null && r.future === 0 && r.extra <= EXTRA_TOLERANCE && r.missing <= MISSING_TOLERANCE;
  } catch (e) {
    r.ok = false;
    r.error = String(e);
  }
  iconCheck = r;
  if (!r.ok || r.extra || r.missing) {
    console.warn(
      `[rpg-icons] 엔더 상자 창 그림표 ${ICON_TABLE_VERSION}: ${r.ok ? "게임 그림" : "평면 그림"} · 모르는 이름 ${r.extra} · 다음 버전 이름 ${r.future} ${r.extraNames.join(",")} · 없는 이름 ${r.missing} ${r.missingNames.join(",")}${r.error ? ` · ${r.error}` : ""}`,
    );
  }
  return r;
}

/** 시험용: 다시 셈 */
export function forgetEnderIconCheck() {
  iconCheck = undefined;
}

/** 이 아이템의 칸 그림: 그림 경로 ("textures/...") 또는 게임 번호 × 65536 (숫자 글자) */
export function iconOf(item) {
  const id = item.typeId;
  if (own(RPG_ICONS, id)) return RPG_ICONS[id];
  if (!id.startsWith("minecraft:")) return UNKNOWN_ICON;
  const short = id.slice(10);
  // 게임 번호: 표 (VANILLA_IDS) · 표 밖이지만 가질 수 있는 아이템 (EXTRA_IDS — 서명한 책)
  const raw = own(VANILLA_IDS, short) ? VANILLA_IDS[short] : own(EXTRA_IDS, short) ? EXTRA_IDS[short] : undefined;
  if (raw !== undefined && SHIFTED_FROM !== null && raw < SHIFTED_FROM && enderIconCheck().ok) return String(raw * 65536);
  if (own(VANILLA_SPRITES, short)) return VANILLA_SPRITES[short];
  if (own(BLOCK_FACES, short)) return BLOCK_FACES[short];
  return UNKNOWN_ICON;
}

// ------------------------------------------------------------ 칸 글자
const d2 = (n) => String(Math.max(0, Math.min(99, Math.floor(n)))).padStart(2, "0");

function durabilityOf(item) {
  try {
    const d = item.getComponent("minecraft:durability");
    if (!d || !(d.maxDurability > 0) || !(d.damage > 0)) return undefined;
    return { left: Math.max(0, d.maxDurability - d.damage), max: d.maxDurability };
  } catch {
    return undefined;
  }
}

function enchantCount(item) {
  try {
    return item.getComponent("minecraft:enchantable")?.getEnchantments().length ?? 0;
  } catch {
    return 0;
  }
}

function loreOf(item) {
  try {
    return item.getLore();
  } catch {
    return [];
  }
}

/** 이름 (번역 글): 이름표 → 이 팩 아이템 이름 → 게임 번역 키 */
export function itemName(item) {
  if (item.nameTag) return { text: item.nameTag };
  if (own(RPG_NAMES, item.typeId)) return { text: RPG_NAMES[item.typeId] };
  try {
    return { translate: item.localizationKey };
  } catch {
    return { text: item.typeId.replace(/^minecraft:/, "") };
  }
}

/** 보이는 글자 max 개까지 (§ 색 코드는 세지 않음), 넘으면 … */
export function clipText(text, max) {
  let shown = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "§") {
      i++;
      continue;
    }
    if (++shown > max) return `${text.slice(0, i)}§r…`;
  }
  return text;
}

/** 안내 글의 이름: 이름표 · 팩 이름이 길면 줄임 (창 높이 — 휴대폰 화면) */
function shortName(item) {
  const n = itemName(item);
  return n.text !== undefined ? { text: clipText(n.text, 18) } : n;
}

/**
 * 칸 하나의 버튼 (글자 + 그림)
 * @returns {{text: import("@minecraft/server").RawMessage, icon?: string}}
 */
export function slotButton(item) {
  if (!item) return { text: { rawtext: [{ text: "x0000-" }] } };
  const dur = durabilityOf(item);
  const pct = dur ? Math.max(1, Math.min(99, Math.round((dur.left / dur.max) * 99))) : 0;
  const ench = enchantCount(item);
  const glint = ench > 0 || RPG_GLINT_SET.has(item.typeId);
  /** @type {import("@minecraft/server").RawMessage[]} */
  const parts = [{ text: `i${d2(item.amount)}${d2(pct)}${glint ? "g" : "-"}§r` }, itemName(item)];
  if (item.amount > 1) parts.push({ text: ` §7×${item.amount}` });
  for (const l of loreOf(item).slice(0, 4)) parts.push({ text: `\n${l}` });
  if (dur) parts.push({ text: `\n§7내구도 ${dur.left} / ${dur.max}` });
  if (ench) parts.push({ text: `\n§d마법 ${ench}개` });
  return { text: { rawtext: parts }, icon: iconOf(item) };
}

// ------------------------------------------------------------ 옮기기
/**
 * src 의 칸 from 에서 k개를 dst 로: 같은 아이템 칸에 먼저 합치고 (order 차례), 남으면 첫 빈칸에. 옮긴 수
 * 남김없이 빈칸으로 옮길 때는 게임의 moveItem (아이템 정보를 그대로), 일부만 옮길 때는 복제 (개수만 다름)
 * 차례: 계획 (아무것도 안 바꿈) → 원래 칸을 먼저 줄임 → 받는 쪽에 넣음. 넣다가 오류가 나면 못 넣은 만큼 원래 칸에 되돌림 (늘어나거나 사라지지 않게)
 * @param {{failed?: boolean}} [report] 넣다가 오류가 났으면 failed = true
 */
export function moveItems(src, from, dst, order, k, report) {
  const item = src.getItem(from);
  if (!item) return 0;
  const total = item.amount;
  const want = Math.max(0, Math.min(Math.floor(k), total));
  if (!(want > 0)) return 0;
  // 1. 계획: 합칠 칸 · 양 (설명 · 이름이 있는 아이템은 isStackable 이 거짓이라 한 칸 최대로 봄 — 합칠 수 있는지는 isStackableWith)
  /** @type {[number, number][]} */
  const merges = [];
  let merged = 0;
  if (item.maxAmount > 1) {
    for (const s of order) {
      if (merged >= want) break;
      const d = dst.getItem(s);
      if (!d || d.amount >= d.maxAmount || !d.isStackableWith(item)) continue;
      const n = Math.min(want - merged, d.maxAmount - d.amount);
      merges.push([s, n]);
      merged += n;
    }
  }
  const empty = merged < want ? order.find((i) => !dst.getItem(i)) : undefined;
  const rest = empty === undefined ? 0 : want - merged;
  if (merged + rest <= 0) return 0;
  // 남은 것 모두를 빈칸으로: 원래 칸에 rest 만큼 남겨 두었다가 칸째로 옮김
  const native = rest > 0 && merged + rest === total;
  // 2. 원래 칸을 먼저 줄임
  const keep = native ? total - merged : total - merged - rest;
  if (keep !== total) {
    if (keep > 0) {
      const left = item.clone();
      left.amount = keep;
      src.setItem(from, left);
    } else src.setItem(from, undefined);
  }
  // 3. 받는 쪽
  let doneMerge = 0;
  let doneRest = false;
  try {
    for (const [s, n] of merges) {
      const d = dst.getItem(s);
      if (!d || !d.isStackableWith(item) || d.amount + n > d.maxAmount) throw new Error(`slot ${s} changed`);
      d.amount += n;
      dst.setItem(s, d);
      doneMerge += n;
    }
    if (rest > 0 && empty !== undefined) {
      if (native) src.moveItem(from, empty, dst);
      else {
        const part = item.clone();
        part.amount = rest;
        dst.setItem(empty, part);
      }
      doneRest = true;
    }
  } catch (e) {
    // 못 넣은 만큼 되돌림 (native 의 rest 는 아직 원래 칸에 있음)
    const back = merged - doneMerge + (!native && !doneRest ? rest : 0);
    console.warn(`[rpg] ender move: ${e} (되돌림 ${back})`);
    if (report) report.failed = true;
    if (back > 0) {
      try {
        // 원래 칸은 2. 에서 줄인 그대로 (같은 아이템 · keep 개) 여야 함 — 설명 · 이름이 있는 아이템은 isStackableWith 가 거짓일 수 있어 개수로 봄
        const cur = src.getItem(from);
        if (cur && cur.typeId === item.typeId && cur.amount === keep && keep + back <= cur.maxAmount) {
          cur.amount += back;
          src.setItem(from, cur);
        } else if (!cur) {
          const again = item.clone();
          again.amount = back;
          src.setItem(from, again);
        } else throw new Error("source slot changed");
      } catch (e2) {
        console.warn(`[rpg] ender move undo: ${e2}`);
      }
    }
  }
  return doneMerge + (doneRest ? rest : 0);
}

/** 옮기는 양: 0 전부 · 1 절반 (올림) · 2 한 개 */
export function amountFor(mode, amount) {
  if (mode === 1) return Math.ceil(amount / 2);
  if (mode === 2) return 1;
  return amount;
}

function enderOf(player) {
  try {
    return player.getComponent("minecraft:ender_inventory")?.container;
  } catch {
    return undefined;
  }
}

function invOf(player) {
  try {
    return player.getComponent("minecraft:inventory")?.container;
  } catch {
    return undefined;
  }
}

/**
 * 누른 칸의 아이템을 반대쪽으로 (지금 그 칸에 있는 것을 다시 읽음 — 창이 열린 사이 바뀌었을 수 있어서). 안내 글 (번역 글)
 * @returns {import("@minecraft/server").RawMessage}
 */
export function pressSlot(player, sel, mode) {
  const ender = enderOf(player);
  const inv = invOf(player);
  if (!ender || !inv) return { rawtext: [{ text: "§c엔더 상자를 열 수 없습니다" }] };
  const toEnder = sel >= ENDER_SLOTS;
  const src = toEnder ? inv : ender;
  const from = toEnder ? invSlotOfButton(sel) : sel;
  const item = src.getItem(from);
  if (!item) return { rawtext: [{ text: "§7빈칸입니다" }] };
  if (item.lockMode && item.lockMode !== "none") return { rawtext: [{ text: "§c잠긴 아이템은 옮길 수 없습니다" }] };
  const want = amountFor(mode, item.amount);
  let moved;
  /** @type {{failed?: boolean}} */
  const report = {};
  try {
    moved = moveItems(src, from, toEnder ? ender : inv, toEnder ? ORDER_ENDER : ORDER_INV, want, report);
  } catch (e) {
    // 원래 칸을 줄이기 전의 오류 (아무것도 바뀌지 않음)
    console.warn(`[rpg] ender move: ${e}`);
    return { rawtext: [{ text: "§c옮기지 못했습니다 §7(다시 눌러 주세요)" }] };
  }
  const where = toEnder ? ENDER.name : "인벤토리";
  if (report.failed && moved <= 0) return { rawtext: [{ text: "§c옮기지 못했습니다 §7(다시 눌러 주세요)" }] };
  if (moved <= 0) {
    try {
      player.playSound("note.bass", { pitch: 0.7, volume: 0.7 });
    } catch {
      // 무시
    }
    return { rawtext: [{ text: `§c${where}에 자리가 없습니다` }] };
  }
  try {
    player.playSound("random.pop", { pitch: toEnder ? 0.75 : 1.25, volume: 0.6 });
  } catch {
    // 무시
  }
  /** @type {import("@minecraft/server").RawMessage[]} */
  const parts = [{ text: "§2" }, shortName(item), { text: `§r §8×${moved} → ${where}` }];
  if (moved < want) parts.push({ text: ` §c(${want - moved}개는 자리가 없어 그대로)` });
  return { rawtext: parts };
}

// ------------------------------------------------------------ 창
/** @returns {import("@minecraft/server").RawMessage} */
function bodyOf(status) {
  /** @type {import("@minecraft/server").RawMessage[]} */
  const parts = status ? [...(status.rawtext ?? [status])] : [{ text: "§8칸을 누르면 반대쪽으로 옮깁니다" }];
  const chk = enderIconCheck();
  if (!chk.ok) parts.push({ text: chk.error ? "\n§8(평면 그림: 아이템 목록을 읽지 못함)" : `\n§8(평면 그림: 게임 버전이 그림표 ${ICON_TABLE_VERSION} 와 다름 · 새 ${chk.extra + chk.future} · 없음 ${chk.missing})` });
  return { rawtext: parts };
}

/** 창 하나 (버튼 66개: 엔더 27 · 인벤토리 27 · 핫바 9 · 옮기는 양 3) */
export function buildEnderForm(player, mode, status) {
  const ender = enderOf(player);
  const inv = invOf(player);
  const form = new ActionFormData().title(ENDER.marker + ENDER.name).body(bodyOf(status));
  const addSlot = (b) => {
    if (b.icon) form.button(b.text, b.icon);
    else form.button(b.text);
  };
  for (let i = 0; i < ENDER_SLOTS; i++) addSlot(slotButton(ender?.getItem(i)));
  for (let b = INV_START; b < MODE_START; b++) addSlot(slotButton(inv?.getItem(invSlotOfButton(b))));
  ENDER.modes.forEach((label, m) => form.button(`m${m === mode ? 1 : 0}${label}`));
  return form;
}

/** 열고 닫을 때: 발밑 보랏빛 룬 · 고리 · 차원문 알갱이 + 엔더 상자 소리 */
function openFx(player) {
  try {
    const dim = player.dimension;
    const feet = player.location;
    rune(dim, feet, { size: 1.3, life: 0.6, color: ENDER.color, alpha: 0.9, progress: 1, rate: 160, grow: 0.25 });
    shock(dim, feet, ENDER.color, 1.8, 0.4);
    for (let i = 0; i < 8; i++) particle(dim, "minecraft:portal_reverse_particle", add(feet, { x: (Math.random() - 0.5) * 1.4, y: 0.3 + Math.random() * 1.5, z: (Math.random() - 0.5) * 1.4 }));
    play(dim, "random.enderchestopen", up(feet, 1), 1, 0.8);
  } catch {
    // 무시
  }
}

function spectating(player) {
  try {
    return player.getGameMode() === GameMode.Spectator;
  } catch {
    return false;
  }
}

function closeFx(player) {
  try {
    if (player.isValid) play(player.dimension, "random.enderchestclosed", up(player.location, 1), 1, 0.8);
  } catch {
    // 무시
  }
}

/**
 * 엔더 상자 창을 닫을 때까지 (다른 창 안에서 부름 — 스탯 창은 이미 창 잠금 안). 칸을 누를 때마다 옮기고 다시 그림
 * @param {import("@minecraft/server").Player} player
 */
export async function enderWindowLoop(player) {
  if (!enderOf(player) || !invOf(player)) {
    notify(player, "§c엔더 상자를 열 수 없습니다 §7(게임 1.26.30 이상 필요)", 60);
    return;
  }
  if (spectating(player)) {
    notify(player, "§c관전 모드에서는 엔더 상자를 열 수 없습니다", 60);
    return;
  }
  openFx(player);
  let mode = 0;
  /** @type {import("@minecraft/server").RawMessage | undefined} */
  let status;
  try {
    for (let guard = 0; guard < 1000; guard++) {
      // 다른 화면 때문에 기다렸다 열 때도 지금 칸으로 (창을 시도할 때마다 새로 만듦)
      const res = await showForm(player, () => buildEnderForm(player, mode, status));
      if (!res || res.selection === undefined || !player.isValid || spectating(player)) return;
      const sel = res.selection;
      if (sel >= MODE_START && sel < BUTTON_COUNT) {
        mode = sel - MODE_START;
        status = undefined;
        continue;
      }
      if (sel < 0 || sel >= MODE_START) continue;
      status = pressSlot(player, sel, mode);
    }
  } finally {
    closeFx(player);
  }
}

/** 따로 열 때 (창 잠금 포함) */
export function openEnderWindow(player) {
  return withFormLock(player, () => enderWindowLoop(player));
}
