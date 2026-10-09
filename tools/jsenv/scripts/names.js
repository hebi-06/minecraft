import { MONSTER_ORDER, MONSTERS, monsterId } from "./config.js";

/**
 * 퀘스트 편집에 쓰는 이름 목록 (한국어 표시 이름 ↔ ID)
 * 목록에 없는 몹·아이템은 퀘스트 편집 창에서 ID를 직접 입력하면 된다. 몬스터 전리품 (v2.7.0) 은 편집 창이 config.js MONSTER_DROPS 에서 바로 보여 줌
 */

/**
 * 처치 퀘스트에서 고를 수 있는 몹 [표시 이름, ID] — RPG 몬스터만, 레벨 순 (v2.7.0: 바닐라 몹은 목록에서 뺌.
 * 예전에 만든 바닐라 몹 퀘스트는 그대로 돌아가고, 편집 창에서는 "직접 입력" 칸에 ID 가 보임)
 */
export const MOBS = MONSTER_ORDER.map((k) => [`${MONSTERS[k].name} (Lv.${MONSTERS[k].level})`, monsterId(k)]);

/** 자주 쓰는 아이템의 표시 이름 */
export const ITEM_NAMES = {
  "minecraft:iron_ingot": "철 주괴",
  "minecraft:gold_ingot": "금 주괴",
  "minecraft:copper_ingot": "구리 주괴",
  "minecraft:diamond": "다이아몬드",
  "minecraft:emerald": "에메랄드",
  "minecraft:coal": "석탄",
  "minecraft:redstone": "레드스톤 가루",
  "minecraft:lapis_lazuli": "청금석",
  "minecraft:bread": "빵",
  "minecraft:wheat": "밀",
  "minecraft:carrot": "당근",
  "minecraft:potato": "감자",
  "minecraft:apple": "사과",
  "minecraft:bone": "뼈",
  "minecraft:rotten_flesh": "썩은 살점",
  "minecraft:string": "실",
  "minecraft:gunpowder": "화약",
  "minecraft:spider_eye": "거미 눈",
  "minecraft:leather": "가죽",
  "minecraft:feather": "깃털",
  "minecraft:arrow": "화살",
  "minecraft:ender_pearl": "엔더 진주",
  "minecraft:slime_ball": "슬라임볼",
  "minecraft:blaze_rod": "블레이즈 막대",
  "minecraft:oak_log": "참나무 원목",
  "minecraft:cobblestone": "조약돌",
  "minecraft:beef": "생소고기",
  "minecraft:porkchop": "생돼지고기",
  "minecraft:chicken": "생닭고기",
  "minecraft:egg": "달걀",
  "minecraft:white_wool": "흰색 양털",
};

/** "minecraft:iron_ingot" → "iron_ingot" */
export function shortId(id) {
  const s = String(id ?? "");
  const i = s.indexOf(":");
  return i >= 0 ? s.slice(i + 1) : s;
}

/** 입력한 ID 정리: 공백 제거, 소문자, 네임스페이스가 없으면 minecraft: */
export function normalizeId(text) {
  const s = String(text ?? "").trim().toLowerCase();
  if (!s) return "";
  return s.includes(":") ? s : `minecraft:${s}`;
}

/** 같은 몹의 구·신 ID(_v2)를 하나로 본다 */
export function mobKey(id) {
  return String(id ?? "").replace(/_v2$/, "");
}

export function mobName(id) {
  const k = mobKey(id);
  const m = MOBS.find(([, mid]) => mobKey(mid) === k);
  return m ? m[0] : shortId(id);
}

/** @param {{typeId: string, nameTag?: string}} item */
export function itemName(item) {
  if (item.nameTag) return item.nameTag;
  return ITEM_NAMES[item.typeId] ?? shortId(item.typeId);
}
