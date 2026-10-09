import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { showForm } from "./chestui.js";

/**
 * 대화창과 입력창 도우미
 * - DialogForm: 제목 앞에 DIALOG_MARKER 를 붙인 ActionForm. 리소스 팩 UI가 화면 아래 RPG 대화창으로 그린다.
 * - ModalBuilder: 입력창(ModalForm)을 이름 붙은 값으로 돌려받는다.
 */
export const DIALOG_MARKER = "§r§q§s§d§m";

/** 대화창 선택지 한 줄에 들어가는 글자 폭 (UI 픽셀, 버튼 폭 300 - 여백·아이콘) */
const CHOICE_WIDTH = 250;

function glyphWidth(ch) {
  const c = ch.codePointAt(0) ?? 0;
  if (c === 0x20) return 4;
  if ((c >= 0x1100 && c <= 0x11ff) || (c >= 0x3000 && c <= 0x9fff) || (c >= 0xac00 && c <= 0xd7a3) || (c >= 0xff00 && c <= 0xffef)) return 9;
  return 6;
}

/**
 * 화면에 보이는 폭(대략)이 maxPx 를 넘으면 잘라서 "..." 을 붙인다. § 색 코드는 폭에 넣지 않는다.
 * @param {string} text
 * @param {number} maxPx
 */
export function clipText(text, maxPx) {
  const chars = [...String(text ?? "")];
  const widthOf = (/** @type {string[]} */ list) => {
    let w = 0;
    for (let i = 0; i < list.length; i++) {
      if (list[i] === "§") i++;
      else w += glyphWidth(list[i]);
    }
    return w;
  };
  if (widthOf(chars) <= maxPx) return chars.join("");
  const limit = maxPx - 18; // "..." 자리
  let w = 0;
  let out = "";
  for (let i = 0; i < chars.length; i++) {
    const ch = chars[i];
    if (ch === "§") {
      out += ch + (chars[i + 1] ?? "");
      i++;
      continue;
    }
    const g = glyphWidth(ch);
    if (w + g > limit) break;
    w += g;
    out += ch;
  }
  return `${out}§r...`;
}

export const ICON = {
  NEW: "textures/rpg_ui/q_new",
  ACTIVE: "textures/rpg_ui/q_active",
  READY: "textures/rpg_ui/q_ready",
  NEXT: "textures/rpg_ui/icon_next",
  OK: "textures/rpg_ui/icon_check",
  BYE: "textures/rpg_ui/icon_cross",
  SHOP: "textures/items/emerald",
};

export class DialogForm {
  /**
   * @param {string} name 말하는 NPC 이름 (이름표에 표시)
   * @param {string} text 대사
   */
  constructor(name, text) {
    this.name = name;
    this.text = text;
    /** @type {{text: string, icon?: string}[]} */
    this.choices = [];
  }

  /** @param {string} text @param {string} [icon] */
  choice(text, icon) {
    this.choices.push({ text: clipText(text, CHOICE_WIDTH), icon });
    return this;
  }

  /**
   * 누른 선택지 번호를 돌려준다. 닫으면 -1.
   * @param {import("@minecraft/server").Player} player
   */
  async show(player) {
    const form = new ActionFormData().title(DIALOG_MARKER + this.name).body(this.text);
    for (const c of this.choices) {
      if (c.icon) form.button(c.text, c.icon);
      else form.button(c.text);
    }
    const res = await showForm(player, form);
    return res && res.selection !== undefined ? res.selection : -1;
  }
}

/** 기본 모양의 버튼 목록 창 (관리자 메뉴용. 스크롤되고 버튼 글자가 보임) */
export class MenuForm {
  /** @param {string} title @param {string} [text] */
  constructor(title, text = "") {
    this.title = title;
    this.text = text;
    /** @type {{text: string, icon?: string}[]} */
    this.buttons = [];
  }

  /** @param {string} text @param {string} [icon] */
  button(text, icon) {
    this.buttons.push({ text, icon });
    return this;
  }

  /** @param {import("@minecraft/server").Player} player */
  async show(player) {
    const form = new ActionFormData().title(this.title).body(this.text);
    for (const b of this.buttons) {
      if (b.icon) form.button(b.text, b.icon);
      else form.button(b.text);
    }
    const res = await showForm(player, form);
    return res && res.selection !== undefined ? res.selection : -1;
  }
}

/** 대사 문자열을 장으로 나눈다. "||" = 다음 장, "|" = 줄바꿈 */
export function toPages(text) {
  return String(text ?? "")
    .split("||")
    .map((p) => p.split("|").join("\n").trim())
    .filter((p) => p.length > 0);
}

/** 저장된 대사를 입력창에 넣을 때 (줄바꿈 → |) */
export function toEditText(text) {
  return String(text ?? "").split("\n").join("|");
}

/**
 * 여러 장짜리 대사를 '다음' 버튼으로 넘겨 보여주고 마지막 장을 돌려준다. 중간에 닫으면 undefined.
 * @param {import("@minecraft/server").Player} player
 * @param {string} name
 * @param {string} text
 * @param {string} fallback 대사가 비었을 때 쓸 문장
 */
export async function showPages(player, name, text, fallback) {
  const pages = toPages(text);
  if (pages.length === 0) pages.push(fallback);
  for (let i = 0; i < pages.length - 1; i++) {
    const sel = await new DialogForm(name, pages[i]).choice("§f다음", ICON.NEXT).show(player);
    if (sel !== 0) return undefined;
  }
  return pages[pages.length - 1];
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

/**
 * 입력창 만들기. 값은 추가할 때 붙인 이름으로 돌려준다.
 * (제목·설명·구분선이 값 목록에 자리를 차지하는 버전과 아닌 버전을 모두 처리)
 */
export class ModalBuilder {
  /** @param {string} title */
  constructor(title) {
    this.form = new ModalFormData().title(title);
    /** @type {{key: string, at: number, input: number}[]} */
    this.keys = [];
    this.total = 0;
  }

  /** @param {string} key */
  add(key) {
    this.keys.push({ key, at: this.total, input: this.keys.length });
    this.total++;
    return this;
  }

  /** @param {string} text */
  header(text) {
    this.form.header(text);
    this.total++;
    return this;
  }

  /** @param {string} text */
  label(text) {
    this.form.label(text);
    this.total++;
    return this;
  }

  divider() {
    this.form.divider();
    this.total++;
    return this;
  }

  /** @param {string} key @param {string} label @param {string} placeholder @param {string} [value] */
  text(key, label, placeholder, value = "") {
    this.form.textField(label, placeholder, { defaultValue: String(value ?? "") });
    return this.add(key);
  }

  /** @param {string} key @param {string} label @param {string[]} items @param {number} [index] */
  dropdown(key, label, items, index = 0) {
    const list = items.length ? items : ["-"];
    this.form.dropdown(label, list, { defaultValueIndex: clamp(Math.floor(index) || 0, 0, list.length - 1) });
    return this.add(key);
  }

  /** @param {string} key @param {string} label @param {number} min @param {number} max @param {number} [value] */
  slider(key, label, min, max, value = min) {
    this.form.slider(label, min, max, { valueStep: 1, defaultValue: clamp(Math.round(Number(value) || 0), min, max) });
    return this.add(key);
  }

  /** @param {string} key @param {string} label @param {boolean} [value] */
  toggle(key, label, value = false) {
    this.form.toggle(label, { defaultValue: !!value });
    return this.add(key);
  }

  /** @param {string} text */
  submit(text) {
    this.form.submitButton(text);
    return this;
  }

  /**
   * @param {import("@minecraft/server").Player} player
   * @returns {Promise<Record<string, any> | undefined>} 닫으면 undefined
   */
  async show(player) {
    const res = await showForm(player, this.form);
    const vals = res?.formValues;
    if (!vals) return undefined;
    const byControl = vals.length >= this.total && this.total > this.keys.length;
    /** @type {Record<string, any>} */
    const out = {};
    for (const k of this.keys) out[k.key] = vals[byControl ? k.at : k.input];
    return out;
  }
}
