import { system } from "@minecraft/server";
import { ActionFormData, FormCancelationReason } from "@minecraft/server-ui";
import { forceHud } from "./hp.js";

/**
 * 상자 모양 창.
 * 제목 앞에 CHEST_MARKER 를 붙인 ActionForm 을 리소스 팩 UI(ui/server_form.json)가 상자 화면으로 그린다.
 * 버튼 27개가 9x3 칸에 순서대로 들어간다.
 *
 * 버튼 글자 앞 4글자는 UI용 정보:
 *   1글자 종류 (c: 개수 표시, p: 가격 표시, n: 숫자 없음, d: 장식·누를 수 없음)
 *   + 3자리 숫자
 */
export const CHEST_MARKER = "§r§p§g§u§i";
export const CHEST_SIZE = 27;

/** @typedef {{icon?: string, name?: string, lore?: string[], count?: number, price?: number, deco?: boolean}} Slot */

/** @type {Set<string>} */
const openFor = new Set();

export function isFormOpen(player) {
  return openFor.has(player.id);
}

/**
 * 창을 하나만 열도록 잠근 상태로 비동기 작업을 실행한다.
 * @param {import("@minecraft/server").Player} player
 * @param {() => Promise<void>} fn
 */
export async function withFormLock(player, fn) {
  if (openFor.has(player.id)) return;
  openFor.add(player.id);
  try {
    await fn();
  } catch (e) {
    console.warn(`[rpg] form: ${e}`);
  } finally {
    openFor.delete(player.id);
    // 창이 열려 있는 동안 화면(HUD)이 놓쳤을 수 있는 체력 바·길 안내 글자를 다시 보냄 (v2.6.1)
    try {
      if (player.isValid) forceHud(player);
    } catch {
      // 무시
    }
  }
}

export function wait(ticks) {
  return new Promise((resolve) => system.runTimeout(() => resolve(undefined), ticks));
}

/**
 * 폼을 보여준다. 다른 화면이 열려 있으면(UserBusy) 잠시 뒤 다시 시도한다. 닫으면 undefined.
 * v2.11.0: form 이 함수면 시도할 때마다 새로 만듦 (기다리는 동안 바뀐 내용 — 엔더 상자 창의 칸)
 * @template {{ canceled: boolean, cancelationReason?: string }} R
 * @param {import("@minecraft/server").Player} player
 * @param {{ show: (player: import("@minecraft/server").Player) => Promise<R> } | (() => { show: (player: import("@minecraft/server").Player) => Promise<R> })} form
 * @returns {Promise<R | undefined>}
 */
export async function showForm(player, form) {
  // 채팅창 등 다른 화면이 열려 있으면 닫을 때까지 최대 60초 기다린다 (명령어를 친 직후 등)
  for (let tries = 0; tries < 240; tries++) {
    if (!player.isValid) return undefined;
    const res = await (typeof form === "function" ? form() : form).show(player);
    if (res.canceled && res.cancelationReason === FormCancelationReason.UserBusy) {
      if (tries === 2) {
        try {
          player.onScreenDisplay.setActionBar("§7채팅창을 닫으면 창이 열립니다.");
        } catch {
          // 무시
        }
      }
      await wait(5);
      continue;
    }
    if (res.canceled) return undefined;
    return res;
  }
  return undefined;
}

function pad3(n) {
  const v = Math.max(0, Math.min(999, Math.floor(Number(n) || 0)));
  return String(v).padStart(3, "0");
}

/** @param {Slot | undefined} slot */
function encode(slot) {
  if (!slot) return { text: "", icon: undefined };
  let kind = "n";
  let num = 0;
  if (slot.deco) kind = "d";
  else if (slot.price !== undefined) {
    kind = "p";
    num = slot.price;
  } else if (slot.count !== undefined) {
    kind = "c";
    num = slot.count;
  }
  let text = kind + pad3(num) + (slot.name ?? "");
  if (slot.lore && slot.lore.length) text += "\n" + slot.lore.join("\n");
  return { text, icon: slot.icon };
}

export const PANE = { icon: "textures/rpg_ui/pane", deco: true };

export class ChestForm {
  /** @param {string} title */
  constructor(title) {
    this.title = title;
    /** @type {(Slot | undefined)[]} */
    this.slots = new Array(CHEST_SIZE).fill(undefined);
    this.text = "";
  }

  /** @param {number} index @param {Slot | undefined} slot */
  set(index, slot) {
    if (index >= 0 && index < CHEST_SIZE) this.slots[index] = slot;
    return this;
  }

  /** 비어 있는 칸을 모두 장식으로 채운다. */
  fillEmpty(slot = PANE) {
    for (let i = 0; i < CHEST_SIZE; i++) if (!this.slots[i]) this.slots[i] = slot;
    return this;
  }

  /** @param {string} text */
  body(text) {
    this.text = text;
    return this;
  }

  /**
   * 창을 보여주고 누른 칸 번호를 돌려준다. 닫으면 -1.
   * @param {import("@minecraft/server").Player} player
   */
  async show(player) {
    const form = new ActionFormData().title(CHEST_MARKER + this.title).body(this.text);
    for (const slot of this.slots) {
      const { text, icon } = encode(slot);
      if (icon) form.button(text, icon);
      else form.button(text);
    }
    const res = await showForm(player, form);
    return res && res.selection !== undefined ? res.selection : -1;
  }
}
