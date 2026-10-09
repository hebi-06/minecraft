import { system } from "@minecraft/server";
import { CONFIG, ENDER, MOUNT, SHIELD, SKILLS, STAT_INFO, STAT_KEYS, TYPES, damagePerPoint, enhanceBonus, skillTotalMult, tyrfingHpFactor } from "./config.js";
import { ChestForm, withFormLock } from "./chestui.js";
import { enderWindowLoop } from "./enderui.js";
import { particle } from "./fx.js";
import { levelHpOf, maxHpOf } from "./hp.js";
import { dismissMount, mountCooldownLeft, mountOf, summonMount } from "./mount.js";
import { questLogLoop } from "./quest.js";
import { isMounted } from "./riding.js";
import { getStatsCached, readStats, spendPoint, reqLevelOf, statBonusOf, statBuffOf } from "./stats.js";
import { referenceEnhance, referenceWeapon, weaponLack } from "./weapons.js";

/**
 * 스탯 창 (상자 모양, 9x3)
 *   윗줄 가운데: 남은 포인트
 *   가운데 줄: 힘 · 민첩 · 마력 · 체력 (칸을 누르면 1포인트 투자)
 *   아랫줄: 퀘스트 목록 · 닫기 · (v2.11.0) 엔더 상자 창 (내 엔더 상자 ↔ 인벤토리) · (v2.8.0) 오른쪽 끝 = 탈것 소환 (타고 있으면 돌려보내기)
 * 본문에 스탯마다 정확한 효과와, 가진 무기 기준 실제 피해(1포인트당 증가량)를 적는다.
 * v1.5.0: 숫자 = 찍은 스탯, 옆의 +N = 입은 갑옷 보너스. 효과(피해·최대 체력)는 둘을 더한 값으로 계산 (요구 스탯은 찍은 스탯만)
 */
const STAT_SLOTS = { 10: "str", 12: "dex", 14: "int", 16: "vit" };
const POINT_SLOT = 4;
const QUEST_SLOT = 20;
const CLOSE_SLOT = 22;
/** v2.8.0 탈것 소환 (오른쪽 아래 칸) */
const MOUNT_SLOT = 26;
/** v2.10.0~ 엔더 상자 (아랫줄, 닫기 칸 오른쪽 — 퀘스트 목록과 마주 봄). v2.11.0: 엔더 상자 창 */
const ENDER_SLOT = 24;

/** 소수 둘째 자리까지 (끝의 0은 뺌) */
function num(n) {
  return String(Math.round(n * 100) / 100);
}

/** 스탯 key 를 v 만큼 찍었을 때 무기 피해가 몇 % 오르는지 */
function bonusPct(key, v) {
  return num(v * damagePerPoint(key) * 100);
}

function shieldCut(w, vit) {
  return Math.min(SHIELD.passiveCap, w.passive + vit * SHIELD.passivePerVit);
}

/** 갑옷 보너스 글자 (없으면 빈 글자) */
function plusText(key, s, t, dark) {
  const b = (t[key] ?? 0) - (s[key] ?? 0);
  return b > 0 ? `${dark ? "§2" : "§a"}+${b}` : "";
}

/**
 * 스탯 한 줄. dark=true 이면 상자 창 본문(밝은 바탕)용 색
 * @param {string} key
 * @param {Record<string, number>} s 찍은 스탯
 * @param {Record<string, number>} t 찍은 스탯 + 갑옷 보너스
 */
function statLine(key, s, t, player, dark) {
  const info = STAT_INFO[key];
  const L = dark ? "§8" : "§7";
  const V = dark ? "§0" : "§f";
  const head = `${dark ? info.dark : info.color}${info.name} ${s[key]}${plusText(key, s, t, dark)}`;
  if (key === "vit") return `${head}${L} · 최대 체력 ${V}${maxHpOf(player)}${L} (1당 +${CONFIG.HP_PER_VIT})`;
  return `${head}${L} · ${info.weapons} ${V}+${bonusPct(key, t[key])}%${L} (1당 ${bonusPct(key, 1)}%)`;
}

/** 체력 스탯이 피해를 올려 주는 무기 설명 (예: "도끼·방패 피해 +0.5%") */
function vitBonusText() {
  /** @type {Map<string, string[]>} 1포인트당 % → 무기 이름들 */
  const by = new Map();
  for (const t of Object.values(TYPES)) {
    const per = (t.bonus?.vit ?? 0) + (t.stat === "vit" ? (t.perPoint ?? 0) : 0);
    if (per <= 0) continue;
    const k = num(per * 100);
    by.set(k, [...(by.get(k) ?? []), t.name]);
  }
  return [...by].map(([k, names]) => `${names.join("·")} 피해 +${k}%`).join(", ");
}

/**
 * 가진 무기 기준 실제 수치 (밝은 바탕용)
 * @param {Record<string, number>} s 찍은 스탯 (요구 스탯 확인)
 * @param {Record<string, number>} t 찍은 스탯 + 갑옷 보너스 (피해 계산)
 */
function weaponLines(player, s, t) {
  const w = referenceWeapon(player, (x) => weaponLack(x, reqLevelOf(player), s).length === 0);
  if (!w) return ["§8RPG 무기를 가지고 있으면 실제 피해가 나옵니다."];
  const enh = referenceEnhance(player, w); // v1.5.0 강화: 기본 공격력 × (1 + 강화 보너스)
  const base = w.damage * (1 + enhanceBonus(enh));
  const lines = [`§8기준 무기: ${enh > 0 ? `§6+${enh} ` : ""}${w.darkName}`];
  const lack = weaponLack(w, reqLevelOf(player), s); // v2.6.2: 레벨 조건 + 요구 스탯 (찍은 스탯만)
  const critAvg = 1 + w.critChance * (w.critMult - 1); // 치명타를 넣은 평균 배율
  const now = base * (1 + w.scaling.reduce((sum, b) => sum + (t[b.stat] ?? 0) * b.per, 0));
  const [main, ...extra] = w.scaling;
  lines.push(`§8피해 §0${num(now)}§8 · 치명타 §0${num(w.critChance * 100)}%§8 ×${num(w.critMult)}`);
  lines.push(`§8평균 §0${num(now * critAvg)}§8 · ${STAT_INFO[main.stat].name} 1당 §0+${num(base * main.per * critAvg)}`);
  // 도끼: 체력 1포인트가 피해와 최대 체력을 함께 올림
  for (const b of extra) {
    const hp = b.stat === "vit" ? `§8 · 최대 체력 §0+${CONFIG.HP_PER_VIT}` : "";
    lines.push(`§8${STAT_INFO[b.stat].name} 1당 피해 §0+${num(base * b.per * critAvg)}${hp}`);
  }
  // 방패: 체력 1포인트가 피해·최대 체력·피해 감소를 함께 올림
  if (w.type === "shield") {
    const per = shieldCut(w, t.vit + 1) - shieldCut(w, t.vit);
    lines.push(`§8피해 감소 §0${num(shieldCut(w, t.vit) * 100)}%§8 · 체력 1당 §0+${num(per * 100)}%p`);
  }
  // 초월 무기 스킬을 한 번 쓸 때 한 대상에게 들어가는 피해 합 (여러 번 때리는 스킬은 모두 더함, 스탯 반영, 치명타 제외). 방벽은 반사 한 번
  if (w.skills) {
    // v2.16.0 티르빙 마력 몫 · v2.17.0 그람 뒤따르는 타격 (skillTotalMult), 티르빙은 × 최대 체력 ÷ hpBase (스킬에만)
    const hpf = w.type === "greatsword" ? tyrfingHpFactor(maxHpOf(player)) : 1;
    const parts = w.skills.map((k) => num(now * skillTotalMult(k) * hpf));
    lines.push(`§8스킬 피해 §0${parts.join("§8 / §0")}§8 (스탯 반영)`);
    if (w.type === "greatsword") lines.push(`§8스킬 × 최대 체력 §0${num(maxHpOf(player))}§8 ÷ ${SKILLS.tyrfing?.hpBase ?? 100} = §0×${num(hpf)}`);
  }
  if (lack.length) {
    // 레벨이 모자라면 레벨만 (스탯은 그 레벨이 되어 포인트를 받은 뒤), 아니면 요구 스탯 모두 (도끼는 힘·체력 함께). 한 줄 180px 안
    const zero = `지금은 ${w.type === "shield" ? "피해·방어" : "피해"} 0`;
    if (lack.some((x) => x.level !== undefined)) lines.push(`§4레벨 ${w.level}부터 - ${zero}`);
    else lines.push(`§4${w.reqs.map((r) => `${STAT_INFO[r.stat].name} ${r.value}`).join(" · ")} 필요 - ${zero}`);
  }
  return lines;
}

/** 탈것 칸이 돌려보내기인지 (창을 만들 때 정함) */
function hasMount(player) {
  return isMounted(player) || !!mountOf(player);
}

function buildForm(player, s, message, riding = hasMount(player)) {
  const t = getStatsCached(player); // 찍은 스탯 + 갑옷 보너스
  const form = new ChestForm(`스탯 §8· §7남은 포인트 ${s.point}`);
  form.set(POINT_SLOT, {
    icon: "textures/rpg_ui/icon_point",
    count: s.point,
    name: `§e§l남은 포인트 §r§f${s.point}`,
    lore: ["§7경험치 레벨이 오르면 포인트를 받습니다."],
  });
  for (const [slot, key] of Object.entries(STAT_SLOTS)) {
    const info = STAT_INFO[key];
    const lore = [statLine(key, s, t, player, false)];
    if (key === "vit") {
      const extra = vitBonusText();
      lore.push(`§71포인트: 최대 체력 +${CONFIG.HP_PER_VIT}${extra ? ` · ${extra}` : ""}`);
      lore.push(`§7레벨 보너스 +${levelHpOf(player)} (레벨당 +${CONFIG.HP_PER_LEVEL})`);
      lore.push(`§7티르빙 스킬 피해 × 최대 체력 ÷ ${SKILLS.tyrfing?.hpBase ?? 100}`); // v2.17.0
    } else {
      lore.push(`§71포인트: ${info.effect} +${bonusPct(key, 1)}%`);
    }
    // v2.24.0 룬 버프 (신체 강화): 갑옷 보너스와 따로 (둘 다 요구 스탯에는 안 들어감)
    const armorB = statBonusOf(player)[key] ?? 0;
    const buffB = t[key] - s[key] - armorB;
    if (armorB > 0) lore.push(`§a갑옷 보너스 +${armorB} §7(요구 스탯에는 안 들어감)`);
    if (buffB > 0) lore.push(`§6룬 버프 +${buffB} §7(모든 스탯 +${Math.round(statBuffOf(player) * 100)}%)`);
    lore.push(s.point > 0 ? "§a눌러서 +1" : "§8포인트 없음");
    form.set(Number(slot), {
      icon: info.icon,
      count: s[key],
      name: `${info.color}§l${info.name} §r§f${s[key]}${plusText(key, s, t, false)}`,
      lore,
    });
  }
  form.set(QUEST_SLOT, { icon: "textures/items/book_writable", name: "§e퀘스트 목록", lore: ["§7진행 중인 퀘스트 보기"] });
  form.set(CLOSE_SLOT, { icon: "textures/rpg_ui/icon_cross", name: "§c닫기" });
  form.set(ENDER_SLOT, enderSlot());
  form.set(MOUNT_SLOT, mountSlot(player, riding));
  form.fillEmpty();

  // 휴대폰 화면 높이(210)에 맞게 한 줄 180px 안쪽으로 짧게
  const lines = [`§0남은 포인트 §2§l${s.point}§r§8 · 칸을 누르면 +1`];
  for (const key of STAT_KEYS) lines.push(`${statLine(key, s, t, player, true)}§r`);
  lines.push(...weaponLines(player, s, t).map((l) => `${l}§r`));
  if (message) lines.push(message);
  form.body(lines.join("\n"));
  return form;
}

/** v2.8.0 탈것 칸: 탄 탈것이 있으면 (riding) 돌려보내기, 아니면 소환 (다시 부를 수 있을 때까지 남은 시간) */
function mountSlot(player, riding) {
  if (riding) {
    return { icon: MOUNT.icon, name: `§b${MOUNT.name} 돌려보내기`, lore: ["§7지금 타고 있습니다", "§7누르면 내려서 돌려보냄 (웅크려도 됨)"] };
  }
  const left = mountCooldownLeft(player);
  const lore = ["§7누르면 불러서 바로 탑니다", "§7웅크려 내리면 사라집니다", "§c탄 동안: 공격 · 스킬 · 룬 불가"];
  if (left > 0) lore.push(`§8${(left / 20).toFixed(1)}초 뒤에 다시 부를 수 있음`);
  return { icon: MOUNT.icon, name: `§b§l탈것 소환 §r§7(${MOUNT.name})`, lore };
}

/** v2.11.0 엔더 상자 칸: 누르면 엔더 상자 창 (내 엔더 상자 27칸 ↔ 인벤토리) */
function enderSlot() {
  return { icon: ENDER.icon, name: `§d§l${ENDER.name}`, lore: ["§7누르면 내 엔더 상자가 열립니다", "§7칸을 누르면 인벤토리와 서로 옮김", `§8옮기는 양: ${ENDER.modes.join(" · ")}`] };
}

/** 1포인트 올린 뒤 알림 (무엇이 얼마나 올랐는지, 갑옷 보너스 포함) */
function gainText(key, player) {
  const info = STAT_INFO[key];
  if (key === "vit") return `${info.dark}체력 +1§8 → 최대 체력 §0${maxHpOf(player)}§r`;
  return `${info.dark}${info.name} +1§8 → ${info.effect} §0+${bonusPct(key, getStatsCached(player)[key])}%§r`;
}

/** @param {import("@minecraft/server").Player} player */
export function openStatWindow(player) {
  return withFormLock(player, async () => {
    let message = "";
    for (let guard = 0; guard < 200; guard++) {
      const s = readStats(player);
      const riding = hasMount(player);
      const sel = await buildForm(player, s, message, riding).show(player);
      if (sel < 0 || sel === CLOSE_SLOT) return;
      if (sel === MOUNT_SLOT) {
        // 창을 닫은 뒤 부름 (창이 열린 동안에는 탈것을 움직일 수 없음)
        system.run(() => {
          if (!player.isValid) return;
          if (riding) dismissMount(player);
          else summonMount(player);
        });
        return;
      }
      if (sel === ENDER_SLOT) {
        // 엔더 상자 창 (닫으면 스탯 창도 닫힘 — 게임의 상자처럼)
        await enderWindowLoop(player);
        return;
      }
      if (sel === QUEST_SLOT) {
        await questLogLoop(player);
        message = "";
        continue;
      }
      const key = STAT_SLOTS[sel];
      if (!key) {
        message = "";
        continue;
      }
      if (!spendPoint(player, key)) {
        message = "§4포인트가 없습니다.§r";
        player.playSound("note.bass", { pitch: 0.7 });
        continue;
      }
      readStats(player);
      message = gainText(key, player);
      player.playSound("random.levelup", { pitch: 1.6, volume: 0.6 });
      const h = player.getHeadLocation();
      particle(player.dimension, "minecraft:totem_particle", { x: h.x, y: h.y - 0.6, z: h.z });
    }
  });
}
