import { EntityTypes, GameMode, ItemStack, PlayerPermissionLevel } from "@minecraft/server";
import { withFormLock } from "./chestui.js";
import { ALL_LEVELS, CONFIG, GRADES, LEVELS, MONSTER_DROPS, MONSTER_ORDER, TYPES, TYPE_ORDER, gradeLevelsText, isValidWeapon, nearestGradeLevel } from "./config.js";
import { ICON, MenuForm, ModalBuilder, toEditText } from "./forms.js";
import { warn } from "./fx.js";
import { ITEM_NAMES, MOBS, itemName, mobKey, mobName, normalizeId, shortId } from "./names.js";
import {
  NPC_TOOL_ID,
  forgetMainNpcRecord,
  forgetNpc,
  mainNpcRecord,
  greeting,
  nearestNpc,
  noteNpcPos,
  npcKey,
  npcList,
  npcName,
  refreshNpc,
  setGreeting,
  setMainNpcRecord,
  setNpcName,
  isRunesmith,
  isSmith,
  setRunesmith,
  setShop,
  setSmith,
  shopLevel,
  spawnNpcNear,
} from "./npcdata.js";
import {
  QUEST_TYPES,
  QUEST_TYPE_KEYS,
  allQuests,
  badgeCount,
  conditionText,
  deleteQuest,
  giverName,
  isBuiltin,
  objectiveText,
  questsOf,
  rewardText,
  saveQuest,
  talkLoop,
} from "./quest.js";
import { mainNpcByKey } from "./mainquest.js";
import { giveItem } from "./shop.js";
import { dropKeyOf } from "./loot.js";
import { currentWeapon, parseWeapon } from "./weapons.js";

/**
 * 관리자용 NPC·퀘스트 설정 (명령어 없이 창으로)
 *   /npc 또는 NPC 설정 도구로 허공 누르기 → NPC 관리
 *   NPC 설정 도구를 들고 NPC 터치        → 그 NPC 설정
 */

const TOOL_ICON = "textures/items/rpg/npc_tool";
const DELETE_ICON = "textures/ui/cancel";
const NO_PERM = "§cNPC 설정은 운영자 또는 크리에이티브 모드에서만 할 수 있습니다.";

/** 운영자이거나 크리에이티브 모드면 편집 가능 */
export function canEdit(player) {
  try {
    return player.playerPermissionLevel === PlayerPermissionLevel.Operator || player.getGameMode() === GameMode.Creative;
  } catch {
    return false;
  }
}

export function openNpcManager(player) {
  if (!canEdit(player)) {
    warn(player, NO_PERM);
    return Promise.resolve();
  }
  return withFormLock(player, () => managerLoop(player));
}

export function openNpcSettings(player, npc) {
  if (!canEdit(player)) {
    warn(player, NO_PERM);
    return Promise.resolve();
  }
  return withFormLock(player, () => settingsLoop(player, npc));
}

function refresh(npc) {
  return refreshNpc(npc, badgeCount(npcKey(npc, true)));
}

/** 편집 창에서 다루는 퀘스트 (메인 · 반복 의뢰 · 왕실 퀘스트는 애드온에 들어 있어 빼고) */
function editable(list) {
  return list.filter((q) => !isBuiltin(q));
}

function say(player, text) {
  player.sendMessage(`§6[RPG] §f${text}`);
}

function preview(text, max = 24) {
  const t = String(text ?? "").replace(/\|/g, " ").trim();
  if (!t) return "(없음)";
  return t.length > max ? `${t.slice(0, max)}...` : t;
}

// ------------------------------------------------------------ NPC 관리
/** 들어 있는 퀘스트 (고칠 수 없음) 를 종류마다: "메인 퀘스트 2개 · 전설 퀘스트 1개" (없으면 "") */
const BUILTIN_SIDE_NAMES = { repeat: "반복 의뢰", royal: "왕실 퀘스트", legend: "전설 퀘스트" };
function builtinCountText(list) {
  const builtin = list.filter((q) => isBuiltin(q));
  const parts = [];
  const mainN = builtin.filter((q) => q.main).length;
  if (mainN) parts.push(`메인 퀘스트 ${mainN}개`);
  for (const [side, label] of Object.entries(BUILTIN_SIDE_NAMES)) {
    const n = builtin.filter((q) => q.side === side).length;
    if (n) parts.push(`${label} ${n}개`);
  }
  return parts.join(" · ");
}

async function managerLoop(player) {
  for (let guard = 0; guard < 100; guard++) {
    const mine = editable(allQuests());
    const count = mine.length;
    const mainCount = allQuests().filter((q) => q.main).length;
    const sideCount = allQuests().filter((q) => q.side).length;
    // v1.1.9: 이전 버전에서 만든 퀘스트의 구버전 무기 보상 (영웅 0~20렙·초월 0~30렙) 알림
    const old = mine.filter((q) => parseWeapon(`rpg:${q.reward.weapon}`)?.legacy || (q.reward.item && parseWeapon(q.reward.item.id)?.legacy)).length;
    const oldNote = old ? `\n\n§e구버전 무기 보상 퀘스트 ${old}개§r§7: 줄 때 같은 등급의 가장 가까운 레벨(영웅 30렙, 초월 40렙)로 바뀝니다. 퀘스트를 열어 저장하면 정리됩니다.` : "";
    // v2.6.0: 메인 퀘스트는 애드온에 들어 있음 (/mainnpc 로 NPC만 세우면 진행)
    const mainNote = mainCount ? `\n\n§6메인 퀘스트 ${mainCount}개§r§7${sideCount ? ` + 반복 의뢰 · 왕실 · 전설 퀘스트 ${sideCount}개` : ""}: /mainnpc 번호 로 NPC를 세우면 바로 진행됩니다 (여기서는 고칠 수 없음).` : "";
    const sel = await new MenuForm(
      "NPC 관리",
      "§lNPC 만들기§r를 누르면 내 앞에 NPC가 생기고 설정 창이 열립니다.\n\n§7NPC 설정 도구를 들고 NPC를 터치해도 설정 창이 열립니다.\n§7스킨은 크리에이티브에서 웅크리고 NPC를 터치해 바꿉니다." + mainNote + oldNote,
    )
      .button("NPC 만들기 §8(대화·퀘스트)", ICON.NEW)
      .button("상점 NPC 만들기", "textures/items/emerald")
      .button("대장장이 NPC 만들기 §8(강화·영웅 레벨)", "textures/items/iron_ingot")
      .button("룬술사 NPC 만들기 §8(룬 갈기·뽑기권)", "textures/items/rpg/rune_shard")
      .button("가까운 NPC 설정 §8(8칸 안)", "textures/items/name_tag")
      .button(`모든 퀘스트 §8(${count}개)`, "textures/items/book_writable")
      .button("NPC 설정 도구 받기", TOOL_ICON)
      .button("닫기", ICON.BYE)
      .show(player);
    if (sel < 0 || sel === 7) return;
    if (sel === 0 || sel === 1) {
      const npc = await createNpcFlow(player, sel === 1);
      if (npc) await settingsLoop(player, npc);
    } else if (sel === 2) {
      const npc = await createNpcFlow(player, false, true);
      if (npc) await settingsLoop(player, npc);
    } else if (sel === 3) {
      const npc = await createNpcFlow(player, false, false, true);
      if (npc) await settingsLoop(player, npc);
    } else if (sel === 4) {
      const npc = nearestNpc(player, 8);
      if (!npc) {
        say(player, "§c8칸 안에 NPC가 없습니다.");
        continue;
      }
      await settingsLoop(player, npc);
    } else if (sel === 5) {
      await questListLoop(player, "");
    } else if (sel === 6) {
      giveItem(player, new ItemStack(NPC_TOOL_ID, 1));
      say(player, "NPC 설정 도구를 받았습니다. §7들고 NPC를 터치하세요.");
    }
  }
}

async function createNpcFlow(player, shop, smith = false, runesmith = false) {
  const b = new ModalBuilder(runesmith ? "룬술사 NPC 만들기" : smith ? "대장장이 NPC 만들기" : shop ? "상점 NPC 만들기" : "NPC 만들기")
    .text("name", "이름", "예: 촌장", runesmith ? "룬술사" : smith ? "대장장이" : shop ? "무기상" : "촌장")
    .text("greet", "인사말 §7(| 줄바꿈, || 다음 장)", "예: 어서 오게, 모험가여.", "");
  if (shop) b.dropdown("lv", "파는 무기 레벨", LEVELS.map((l) => `${l}렙 무기`), 0);
  const v = await b.submit("만들기").show(player);
  if (!v) return undefined;
  const npc = spawnNpcNear(player);
  setNpcName(npc, String(v.name ?? ""));
  setGreeting(npc, String(v.greet ?? ""));
  if (shop) setShop(npc, LEVELS[v.lv] ?? LEVELS[0]);
  if (smith) setSmith(npc, true);
  if (runesmith) setRunesmith(npc, true);
  refresh(npc);
  player.playSound("random.orb", { pitch: 1.2 });
  return npc;
}

// ------------------------------------------------------------ NPC 한 명 설정
async function settingsLoop(player, npc) {
  for (let guard = 0; guard < 100; guard++) {
    if (!npc.isValid) {
      say(player, "§cNPC가 사라졌습니다.");
      return;
    }
    const key = refresh(npc);
    const name = npcName(npc);
    const lv = shopLevel(npc);
    const all = questsOf(key);
    const list = editable(all);
    const builtinText = builtinCountText(all);
    const greet = greeting(npc);
    const main = mainNpcByKey(key);
    const info = [
      `§l${name}§r §8(번호 ${key})`,
      main ? `§6메인 퀘스트 NPC ${main.n}번§7 · ${main.role}` : "",
      `§7상점: §f${lv >= 0 ? `${lv}렙 무기·갑옷 상점` : "없음"}§7 · 대장장이: §f${isSmith(npc) ? "예" : "아니오"}§7 · 룬술사: §f${isRunesmith(npc) ? "예" : "아니오"}`,
      `§7퀘스트: §f${list.length}개${builtinText ? ` §8(+ ${builtinText}, 고칠 수 없음)` : ""}`,
      `§7인사말: §f${preview(greet, 40)}${main && !greet ? " §8(비우면 메인 퀘스트 인사말)" : ""}`,
    ]
      .filter(Boolean)
      .join("\n");
    const sel = await new MenuForm(`NPC 설정 · ${name}`, info)
      .button("이름 바꾸기", "textures/items/name_tag")
      .button("인사말 바꾸기", "textures/items/book_writable")
      .button("상점·대장장이·룬술사 설정", "textures/items/emerald")
      .button("퀘스트 추가", ICON.NEW)
      .button(`퀘스트 목록·수정 §8(${list.length}개)`, "textures/items/book_normal")
      .button("대화 미리보기", "textures/items/paper")
      .button("내 자리로 옮기기", "textures/items/ender_pearl")
      .button("§cNPC 삭제", DELETE_ICON)
      .button("닫기", ICON.BYE)
      .show(player);
    if (sel < 0 || sel === 8) return;
    switch (sel) {
      case 0: {
        const v = await new ModalBuilder("이름 바꾸기").text("name", "이름", "예: 촌장", name).submit("저장").show(player);
        if (v) setNpcName(npc, String(v.name ?? ""));
        break;
      }
      case 1: {
        const v = await new ModalBuilder("인사말 바꾸기")
          .label("§7| 는 줄바꿈, || 는 다음 장입니다. (다음 장은 '다음' 버튼으로 넘김)")
          .text("greet", "인사말", "예: 어서 오게.|요즘 마을이 흉흉하다네.", toEditText(greet))
          .submit("저장")
          .show(player);
        if (v) setGreeting(npc, String(v.greet ?? ""));
        break;
      }
      case 2: {
        const opts = ["상점 아님", ...LEVELS.map((l) => `${l}렙 무기 상점 (갑옷 · 전리품 팔기)`)];
        const v = await new ModalBuilder("상점·대장장이·룬술사 설정")
          .dropdown("lv", "이 NPC를 터치하면 여는 상점", opts, lv >= 0 ? LEVELS.indexOf(lv) + 1 : 0)
          .toggle("smith", "대장장이 §7(무기 강화 +1~+10 · 영웅 무기 레벨 30→35→40렙)", isSmith(npc))
          .toggle("runesmith", "룬술사 §7(룬 갈기 → 룬 조각 → 룬 뽑기권 · 고급 룬 뽑기권)", isRunesmith(npc))
          .submit("저장")
          .show(player);
        if (v) {
          setShop(npc, v.lv > 0 ? LEVELS[v.lv - 1] : -1);
          setSmith(npc, !!v.smith);
          setRunesmith(npc, !!v.runesmith);
        }
        break;
      }
      case 3:
        await editQuestFlow(player, undefined, key);
        break;
      case 4:
        await questListLoop(player, key);
        break;
      case 5:
        await talkLoop(player, npc);
        break;
      case 6:
        try {
          const at = { ...player.location };
          npc.teleport(at, { rotation: { x: 0, y: player.getRotation().y } });
          // v2.6.1 길 안내: 옮긴 자리 (옮긴 직후에는 엔티티 위치가 아직 예전일 수 있어서 직접 적음)
          noteNpcPos(npc, key, at, npc.dimension.id);
          if (main && mainNpcRecord(key)?.id === npc.id) setMainNpcRecord(key, npc, at, npc.dimension.id);
          say(player, "NPC를 옮겼습니다.");
        } catch (e) {
          say(player, `§c옮기지 못했습니다: ${e}`);
        }
        break;
      case 7:
        if (await confirmDelete(player, npc, key, list.length)) return;
        break;
    }
  }
}

async function confirmDelete(player, npc, key, count) {
  // 이 NPC에게 말 걸기 퀘스트는 NPC가 없으면 끝낼 수 없으므로 함께 지운다 (메인 퀘스트는 애드온에 있어 남음)
  const talkTo = editable(allQuests()).filter((q) => q.type === "talk" && q.target === key && q.giver !== key);
  const main = mainNpcByKey(key);
  const lines = [`§c${npcName(npc)}§r NPC를 지울까요?`];
  if (count) lines.push(`이 NPC가 주는 퀘스트 ${count}개도 함께 지워집니다.`);
  if (talkTo.length) lines.push(`이 NPC에게 말 걸기 퀘스트 ${talkTo.length}개도 함께 지워집니다.`);
  if (main) lines.push(`§6메인 퀘스트 NPC입니다.§r 지운 뒤 /mainnpc ${main.n} 으로 다시 세울 수 있습니다 (플레이어 진행은 그대로).`);
  const sel = await new MenuForm("NPC 삭제", lines.join("\n")).button("§c지우기", DELETE_ICON).button("취소", ICON.BYE).show(player);
  if (sel !== 0) return false;
  for (const q of [...editable(questsOf(key)), ...talkTo]) deleteQuest(q.id);
  forgetNpc(key);
  if (main && mainNpcRecord(key)?.id === npc.id) forgetMainNpcRecord(key); // 예전 중복 NPC를 지울 때는 지금 NPC의 기록을 남김
  try {
    npc.remove();
  } catch {
    // 무시
  }
  say(player, "NPC를 지웠습니다.");
  return true;
}

// ------------------------------------------------------------ 퀘스트 목록 (관리자)
/** @param {string} giverKey 빈 문자열이면 모든 퀘스트 */
async function questListLoop(player, giverKey) {
  for (let guard = 0; guard < 100; guard++) {
    const list = editable(giverKey ? questsOf(giverKey) : allQuests());
    const title = giverKey ? "퀘스트 목록" : "모든 퀘스트";
    const text = list.length ? "고칠 퀘스트를 고르세요." : "퀘스트가 없습니다.";
    const form = new MenuForm(title, text);
    if (giverKey) form.button("§2+ 퀘스트 추가", ICON.NEW);
    for (const q of list) form.button(`${q.title} §8(${giverKey ? QUEST_TYPES[q.type].name : giverName(q)})`, QUEST_TYPES[q.type].icon);
    form.button("뒤로", ICON.BYE);
    let sel = await form.show(player);
    if (sel < 0) return;
    if (giverKey) {
      if (sel === 0) {
        await editQuestFlow(player, undefined, giverKey);
        continue;
      }
      sel--;
    }
    const q = list[sel];
    if (!q) return;
    await questActions(player, q);
  }
}

async function questActions(player, q) {
  const text = [
    `§l${q.title}§r`,
    `§7주는 NPC: §f${giverName(q)}`,
    `§7목표: §f${objectiveText(q)}`,
    `§7보상: §f${rewardText(q)}`,
    `§7조건: §f${conditionText(q)}`,
  ].join("\n");
  const sel = await new MenuForm("퀘스트", text)
    .button("수정", "textures/items/book_writable")
    .button("§c삭제", DELETE_ICON)
    .button("뒤로", ICON.BYE)
    .show(player);
  if (sel === 0) await editQuestFlow(player, q, q.giver);
  else if (sel === 1) {
    const c = await new MenuForm("퀘스트 삭제", `§c${q.title}§r 퀘스트를 지울까요?\n진행 중인 플레이어의 기록도 사라집니다.`)
      .button("§c지우기", DELETE_ICON)
      .button("취소", ICON.BYE)
      .show(player);
    if (c === 0) {
      deleteQuest(q.id);
      say(player, `퀘스트를 지웠습니다: ${q.title}`);
    }
  }
}

// ------------------------------------------------------------ 퀘스트 편집
function inventoryItems(player) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  /** @type {Map<string, {id: string, amount: number, name: string, nameTag?: string}>} */
  const map = new Map();
  if (!inv) return [];
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (!it || it.typeId === NPC_TOOL_ID) continue;
    const prev = map.get(it.typeId);
    if (prev) prev.amount += it.amount;
    else map.set(it.typeId, { id: it.typeId, amount: it.amount, name: it.nameTag || parseWeapon(it.typeId)?.name.replace(/§./g, "") || itemName(it), nameTag: it.nameTag });
  }
  return [...map.values()];
}

function isItemId(id) {
  try {
    new ItemStack(id, 1);
    return true;
  } catch {
    return false;
  }
}

/** 선행 퀘스트를 따라가다 자기 자신으로 돌아오는지 */
function requiresLoop(d) {
  const seen = new Set([d.id || "(new)"]);
  let cur = d.requires;
  for (let i = 0; cur && i < 100; i++) {
    if (seen.has(cur)) return true;
    seen.add(cur);
    cur = allQuests().find((q) => q.id === cur)?.requires ?? "";
  }
  return false;
}

function newQuest(giverKey) {
  return {
    id: "",
    title: "새 퀘스트",
    giver: giverKey,
    type: "kill",
    target: "rpg:scarecrow",
    targetName: "",
    count: 10,
    offer: "",
    progress: "",
    done: "",
    reward: { emerald: 5, xp: 0, points: 0, weapon: "", item: null },
    repeat: false,
    requires: "",
    minLevel: 0,
  };
}

/**
 * 퀘스트 만들기·수정 창. 저장하면 저장된 퀘스트를 돌려준다.
 * @param {import("@minecraft/server").Player} player
 * @param {any} quest 수정할 퀘스트 (새로 만들면 undefined)
 * @param {string} giverKey 주는 NPC 번호
 */
async function editQuestFlow(player, quest, giverKey) {
  const isNew = !quest;
  /** @type {any} */
  const d = quest ? JSON.parse(JSON.stringify(quest)) : newQuest(giverKey);
  let error = "";
  for (let tries = 0; tries < 10; tries++) {
    const npcs = npcList();
    const npcLabels = npcs.length ? npcs.map(([k, n]) => `${n} §8(${k})`) : ["(NPC 없음)"];
    const others = editable(allQuests()).filter((q) => q.id !== d.id);
    const items = inventoryItems(player);
    // v2.7.0: 가져오기 목표는 몬스터 전리품 6종이 먼저, 그다음 내 인벤토리 (전리품은 한 번만)
    const collectables = [...MONSTER_ORDER.filter((k) => MONSTER_DROPS[k]).map((k) => ({ id: MONSTER_DROPS[k].item, amount: 0, name: MONSTER_DROPS[k].name, drop: true })), ...items.filter((it) => !dropKeyOf(it.id)).map((it) => ({ ...it, drop: false }))];
    const mobIdx = MOBS.findIndex(([, id]) => mobKey(id) === mobKey(d.target));
    const itemIdx = collectables.findIndex((it) => it.id === d.target);
    const w = d.reward.weapon ? currentWeapon(parseWeapon(`rpg:${d.reward.weapon}`)) : undefined; // 구버전 보상은 실제로 줄 레벨로 보여 줌
    const customDefault = (d.type === "kill" && mobIdx < 0) || (d.type === "collect" && itemIdx < 0) ? d.target : "";
    const hadItem = !!d.reward.item;

    const b = new ModalBuilder(isNew ? "퀘스트 만들기" : "퀘스트 수정");
    if (error) b.label(`§c${error}`);
    b.header("기본");
    b.text("title", "퀘스트 이름", "예: 허수아비 소탕", d.title);
    b.dropdown("giver", "퀘스트를 주는 NPC", npcLabels, Math.max(0, npcs.findIndex(([k]) => k === d.giver)));
    b.dropdown("type", "목표 종류", QUEST_TYPE_KEYS.map((k) => QUEST_TYPES[k].name), Math.max(0, QUEST_TYPE_KEYS.indexOf(d.type)));
    b.dropdown("mob", "[몹 처치] 처치할 몹", [...MOBS.map((m) => m[0]), "직접 입력 (아래 ID 칸)"], d.type === "kill" ? (mobIdx >= 0 ? mobIdx : MOBS.length) : 0);
    b.dropdown("item", "[아이템 가져오기] 가져올 아이템 (몬스터 전리품 · 내 인벤토리)", ["직접 입력 (아래 ID 칸)", ...collectables.map((it) => `${it.name} §8(${it.drop ? "전리품" : shortId(it.id)})`)], d.type === "collect" ? itemIdx + 1 : 0);
    b.text("custom", "몹·아이템 ID 직접 입력", "예: rpg:gulon, rpg:loot_troll", customDefault);
    b.dropdown("talk", "[NPC에게 말 걸기] 대상 NPC", npcLabels, d.type === "talk" ? Math.max(0, npcs.findIndex(([k]) => k === d.target)) : 0);
    b.slider("count", "개수 (몹·아이템) / 목표 레벨", 1, 100, d.count);
    b.text("targetName", "화면에 보일 대상 이름 (비우면 자동)", "예: 허수아비", d.targetName);
    b.divider();
    b.header("대사 (| 줄바꿈, || 다음 장)");
    b.text("offer", "퀘스트를 줄 때", "예: 밭에 허수아비가 늘었네.|10마리만 처치해 주게.", toEditText(d.offer));
    b.text("progress", "진행 중일 때", "예: 아직 멀었나?", toEditText(d.progress));
    b.text("done", "완료했을 때", "예: 고맙네! 약속한 보상일세.", toEditText(d.done));
    b.divider();
    b.header("보상");
    b.slider("emerald", CONFIG.CURRENCY_NAME, 0, 64, d.reward.emerald);
    b.slider("xp", "경험치 레벨", 0, 30, d.reward.xp);
    b.slider("points", "스탯 포인트", 0, 10, d.reward.points);
    b.dropdown("wtype", "무기 보상", ["없음", ...TYPE_ORDER.map((t) => TYPES[t].name)], w ? TYPE_ORDER.indexOf(w.type) + 1 : 0);
    b.dropdown("wlevel", "무기 레벨 §7(영웅 30·35·40, 초월 40)", ALL_LEVELS.map((l) => `${l}렙`), w ? Math.max(0, ALL_LEVELS.indexOf(w.level)) : 0);
    b.dropdown("wgrade", "무기 등급", GRADES.map((g) => g.name), w ? w.gradeIndex : 0);
    const rewardLabels = ["없음"];
    if (hadItem) rewardLabels.push(`지금 설정 유지: ${d.reward.item.name || shortId(d.reward.item.id)}`);
    for (const it of items) rewardLabels.push(`${it.name} §8(${shortId(it.id)})`);
    b.dropdown("ritem", "아이템 보상 (내 인벤토리에서 고르기, 이름만 저장되고 마법 부여는 저장 안 됨)", rewardLabels, hadItem ? 1 : 0);
    b.slider("ramount", "아이템 보상 개수", 1, 64, d.reward.item?.amount ?? 1);
    b.divider();
    b.header("조건");
    b.dropdown("requires", "먼저 끝내야 하는 퀘스트", ["없음", ...others.map((q) => `${q.title} §8(${giverName(q)})`)], others.findIndex((q) => q.id === d.requires) + 1);
    b.slider("minLevel", "최소 경험치 레벨", 0, 100, d.minLevel);
    b.toggle("repeat", "반복 가능 (몹 처치·아이템 가져오기만, 완료 후 다시 받기)", d.repeat);
    b.submit(isNew ? "퀘스트 만들기" : "저장");

    const v = await b.show(player);
    if (!v) return undefined;

    // ---- 입력 반영
    error = "";
    d.title = String(v.title ?? "").trim() || "퀘스트";
    if (npcs.length && npcs[v.giver]) d.giver = npcs[v.giver][0];
    const prevTarget = d.target;
    d.type = QUEST_TYPE_KEYS[v.type] ?? "kill";
    d.count = Math.max(1, Math.floor(Number(v.count) || 1));
    const custom = normalizeId(v.custom);
    let autoName = "";
    if (d.type === "kill") {
      if (v.mob < MOBS.length) {
        d.target = MOBS[v.mob][1];
        autoName = MOBS[v.mob][0];
      } else if (!custom) error = "처치할 몹을 고르거나 몹 ID를 입력하세요.";
      else if (!EntityTypes.get(custom)) error = `알 수 없는 몹 ID입니다: ${custom}`;
      else {
        d.target = custom;
        autoName = mobName(custom);
      }
    } else if (d.type === "collect") {
      const it = v.item > 0 ? collectables[v.item - 1] : undefined;
      if (it) {
        d.target = it.id;
        autoName = it.name;
      } else if (!custom) error = "가져올 아이템을 고르거나 아이템 ID를 입력하세요.";
      else if (!isItemId(custom)) error = `알 수 없는 아이템 ID입니다: ${custom}`;
      else {
        d.target = custom;
        autoName = ITEM_NAMES[custom] ?? shortId(custom);
      }
    } else if (d.type === "talk") {
      const t = npcs[v.talk];
      if (!t) error = "말 걸 NPC가 없습니다. NPC를 먼저 하나 더 만드세요.";
      else if (t[0] === d.giver) error = "퀘스트를 주는 NPC와 다른 NPC를 고르세요.";
      else {
        d.target = t[0];
        autoName = t[1];
      }
    } else {
      d.target = "";
    }
    // 대상이 바뀌었는데 이름 칸을 그대로 두었으면 자동 이름으로 바꾼다
    const typed = String(v.targetName ?? "").trim();
    d.targetName = typed && !(typed === quest?.targetName && d.target !== prevTarget) ? typed : autoName;
    d.offer = String(v.offer ?? "").trim();
    d.progress = String(v.progress ?? "").trim();
    d.done = String(v.done ?? "").trim();
    d.reward.emerald = v.emerald;
    d.reward.xp = v.xp;
    d.reward.points = v.points;
    d.reward.weapon = "";
    if (v.wtype > 0) {
      // 그 등급에 없는 레벨이면 가장 가까운 레벨로 바꾼다 (영웅 30·35·40렙, 초월 40렙, 나머지는 35렙 없음)
      const gid = GRADES[v.wgrade]?.id ?? "common";
      const want = ALL_LEVELS[v.wlevel] ?? 0;
      const lv = isValidWeapon(want, gid) ? want : nearestGradeLevel(want, gid);
      if (lv !== want) say(player, `§e${GRADES[v.wgrade]?.name} 등급은 ${gradeLevelsText(gid)} 무기만 있어서 보상을 ${lv}렙으로 바꿨습니다.`);
      d.reward.weapon = `${TYPE_ORDER[v.wtype - 1]}_l${lv}_${gid}`;
    }
    if (v.ritem === 0) d.reward.item = null;
    else if (hadItem && v.ritem === 1) d.reward.item = { ...d.reward.item, amount: v.ramount };
    else {
      const it = items[v.ritem - (hadItem ? 2 : 1)];
      d.reward.item = it ? { id: it.id, amount: v.ramount, name: it.name, nameTag: it.nameTag } : null;
    }
    const rw = d.reward.item ? parseWeapon(d.reward.item.id) : undefined;
    if (rw?.legacy) {
      const cw = currentWeapon(rw);
      d.reward.item = { ...d.reward.item, id: cw.id, name: d.reward.item.nameTag || cw.name.replace(/§./g, "") };
      say(player, `§e${rw.grade.name} 등급은 ${gradeLevelsText(rw.grade.id)} 무기만 있어서 아이템 보상을 ${cw.level}렙 무기로 바꿨습니다.`);
    }
    d.requires = v.requires > 0 ? (others[v.requires - 1]?.id ?? "") : "";
    d.minLevel = v.minLevel;
    d.repeat = !!v.repeat;
    if (!error && !d.giver) error = "퀘스트를 줄 NPC가 없습니다.";
    if (!error && d.requires && requiresLoop(d)) error = "먼저 끝내야 하는 퀘스트가 서로를 가리킵니다. 다른 퀘스트를 고르세요.";
    if (error) continue;

    const saved = saveQuest(d);
    say(player, `퀘스트 ${isNew ? "만듦" : "저장"}: §e${saved.title} §7(${objectiveText(saved)})`);
    player.playSound("random.orb", { pitch: 1.3 });
    return saved;
  }
  return undefined;
}
