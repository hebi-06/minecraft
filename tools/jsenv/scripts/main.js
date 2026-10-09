import { system, world } from "@minecraft/server";
import { isFormOpen } from "./chestui.js";
import { armorInfo, openStore, parseArmor, registerArmor } from "./armor.js";
import { addWeaponHitHook, cleanupMarks, clearGuards, forgetCombat, registerCombat, rememberEnhance, tryUseRanged } from "./combat.js";
import { forgetSlash } from "./slash.js";
import { registerCommands } from "./commands.js";
import { BOSS_MATERIALS, LEVELS, MONSTER_DROPS, RAID_MATERIALS, RUNE, RUNE_SMITH, gradeLevelsText } from "./config.js";
import { openNpcManager } from "./editor.js";
import { registerDummy } from "./dummy.js";
import { forgetFx, notify, startFxLoop } from "./fx.js";
import {
  checkStuck,
  feedTick,
  forceHud,
  forgetHp,
  healFull,
  hpState,
  onPlayerDied,
  onPlayerRespawn,
  refreshMaxHp,
  saveHp,
  showRealTitle,
  syncVanilla,
  updateHud,
} from "./hp.js";
import { forgetMonsterFights, registerMonsters } from "./monsters.js";
import { forgetMount, registerMount } from "./mount.js";
import { registerEnderCleanup } from "./enderchest.js";
import { dropLore, forgetLoot } from "./loot.js";
import { registerHuntZones } from "./huntzone.js";
import { forgetNav, registerNav } from "./nav.js";
import { registerArenas } from "./arena.js";
import { ALTAR_COMPONENT, addBossKillHook, registerBosses } from "./bosses.js";
import { registerWorldTree } from "./worldtree.js";
import { registerPvp } from "./pvp.js";
import { registerUltFx } from "./ultfx.js";
import { registerStatus } from "./status.js";
import { npcEffectsTick, recentToolTap, registerNpc, spawnRoleNpc, tagNearestNpc } from "./npc.js";
import { claimPendingRunes, forgetRunes, onRuneItemUse, parseRune, registerRunes, runeLore } from "./runes.js";
import { NPC_TOOL_ID } from "./npcdata.js";
import { forgetQuestPlayer, onBossKilled, onEntityDied, onWeaponHit, openQuestLog, questTick } from "./quest.js";
import { giveItem } from "./shop.js";
import { forgetSkills, onSkillItemUse, registerSkills } from "./skills.js";
import { openStatWindow } from "./statui.js";
import { addPoints, checkLevelUp, forget, initObjectives, onStatsChanged, readStats, resetStats, setupNewPlayer, reqLevelOf } from "./stats.js";
import {
  STAT_BOOK_ID,
  STAT_BOOK_LORE,
  createStatBook,
  createWeaponItem,
  enhanceOf,
  forgetHeldWeapon,
  levelNote,
  loreMatches,
  noteHeldWeapon,
  parseWeapon,
  setLoreSafe,
  syncEnhanceName,
  weaponInfo,
} from "./weapons.js";

/** MMO RPG 애드온 진입점 */

registerCommands();
registerCombat();
registerSkills();
registerRunes();
registerArmor(); // 갑옷 (v1.5.0): 요구 조건 확인·스탯 보너스 (0.5초마다)
registerNpc();
registerDummy(); // 전투 계산(combat.js) 다음에 등록해야 허수아비가 최종 피해를 기록함
registerMonsters(); // 일반 몬스터 (v1.7.0): 후공·사거리 맞춘 공격·달려들기·이름표
registerHuntZones(); // 사냥터 (v2.6.0): /mobzone 으로 둔 자리에 몬스터를 늘 채움
registerNav(); // 길 안내 (v2.6.1): 다음에 갈 NPC·사냥터·보스 제단 (화면 화살표 · 바닥 빛 · 빛기둥)
registerStatus(); // 보스 기믹 상태 (v1.8.0): 속박·침묵
registerArenas(); // 보스 제단·필드 (v1.8.0)
registerBosses(); // 보스 (v1.8.0): 소환·체력·패턴·보상
registerWorldTree(); // 세계수 (v2.5.1): /세계수 · /worldtree 로 심고 자라나게
registerMount(); // 탈것 (v2.8.0): 스탯 창에서 다이어울프를 불러 바로 탐, 내리면 사라짐, 탄 동안 공격 · 스킬 불가
registerPvp(); // 결투 (v2.26.0): /pvp 닉네임 → 수락하면 둘만 서로 피해 (× PVP.mult), 다른 플레이어는 다치지 않음
registerUltFx(); // 초월 룬 2차 강화 (v2.27.0): 하늘빛 (/fog) 걷기 — 들어올 때 · 스크립트를 다시 불러올 때
registerEnderCleanup(); // v2.10.0 에 잠깐 놓였던 엔더 상자가 남아 있으면 치움 (v2.11.0 부터 엔더 상자는 스탯 창의 창 — enderui.js)
addWeaponHitHook(onWeaponHit); // 메인 퀘스트 (v2.6.0): 무기 시험 (무기 종류마다 한 번씩 맞히기)
addBossKillHook(onBossKilled); // 메인 퀘스트 (v2.6.0): 보스 처치 (보상을 받은 사람 모두)
system.beforeEvents.startup.subscribe((ev) => {
  try {
    ev.blockComponentRegistry.registerCustomComponent("rpg:boss_altar", ALTAR_COMPONENT);
  } catch (e) {
    console.warn(`[rpg] altar component: ${e}`);
  }
});
onStatsChanged((player, reason) => refreshMaxHp(player, reason === "equipment")); // 갑옷은 체력 비율 유지 (v1.5.0), v2.24.0 룬 버프 (buff) 는 늘면 그만큼 차고 줄면 넘친 것만 깎임

// ------------------------------------------------------------ 아이템 사용
world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (!itemStack) return;
  if (itemStack.typeId === STAT_BOOK_ID) {
    openStatWindow(source);
    return;
  }
  if (itemStack.typeId === NPC_TOOL_ID) {
    // NPC를 터치한 경우는 그 NPC의 설정 창이 열리므로 관리 창은 열지 않는다
    if (!recentToolTap(source)) openNpcManager(source);
    return;
  }
  // 룬 (v1.3.0): 룬 = 누르면 스킬, 룬 뽑기권 = 누르면 뽑기
  if (onRuneItemUse(source, itemStack)) return;
  // 초월 무기 스킬 (v1.2.0): 근접은 버튼 = 스킬, 활·완드는 짧게 = 평타 / 웅크리고 = 스킬 1 / 길게 = 스킬 2
  if (onSkillItemUse(source, itemStack)) return;
  tryUseRanged(source, itemStack);
});

// 퀘스트 몹 처치 기록
world.afterEvents.entityDie.subscribe(({ deadEntity, damageSource }) => {
  try {
    onEntityDied(deadEntity, damageSource);
  } catch (e) {
    console.warn(`[rpg] quest kill: ${e}`);
  }
});

// ------------------------------------------------------------ 접속·사망·부활
function onJoin(player) {
  const isNew = setupNewPlayer(player);
  readStats(player);
  hpState(player);
  refreshMaxHp(player);
  feedTick(player); // v2.6.3 배고픔 없음
  system.runTimeout(() => {
    if (!player.isValid) return;
    syncVanilla(player);
    forceHud(player);
    claimPendingRunes(player); // v2.7.0: 룬 뽑기 연출 중에 나갔으면 그 룬
  }, 20);
  system.runTimeout(() => {
    if (player.isValid) forceHud(player);
  }, 100);
  if (isNew) {
    system.runTimeout(() => {
      if (!player.isValid) return;
      giveItem(player, createStatBook());
      player.sendMessage("§6[RPG] §f스탯 정보 책을 받았습니다. §7손에 들면 스탯 창이 열립니다.");
    }, 40);
  }
}

// 차원 이동 뒤에는 화면이 새로 만들어지므로 체력 바를 다시 보낸다
world.afterEvents.playerDimensionChange.subscribe(({ player }) => {
  for (const delay of [10, 60]) {
    system.runTimeout(() => {
      if (player.isValid) forceHud(player);
    }, delay);
  }
});

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (initialSpawn) onJoin(player);
  else {
    onPlayerRespawn(player);
    feedTick(player); // v2.6.3 배고픔 없음: 다시 태어나면 바로 가득
  }
});

world.afterEvents.entityDie.subscribe(
  ({ deadEntity }) => {
    if (deadEntity.typeId !== "minecraft:player") return;
    onPlayerDied(deadEntity);
    clearGuards(deadEntity.id); // 방벽·반격 자세 등은 죽으면 끝
  },
  { entityTypes: ["minecraft:player"] },
);

/** @type {Map<string, string>} */
const held = new Map();
/** 레이드 재료·보스 재료(v2.0.0)·룬 조각(v2.7.0) 아이템 ID → 설명 (/give 로 받은 것도 손에 들면 붙음) */
const MATERIAL_LORE = new Map([...Object.values(RAID_MATERIALS), ...Object.values(BOSS_MATERIALS)].map((m) => [m.item, m.lore]));
MATERIAL_LORE.set(RUNE_SMITH.shardItem, RUNE_SMITH.shardLore);
MATERIAL_LORE.set(RUNE.advTicketItem, RUNE.advTicketLore);
// v2.7.0 몬스터 전리품 (설명에 상점 값이 들어 있어 값을 바꾸면 들 때 고쳐짐)
for (const k of Object.keys(MONSTER_DROPS)) MATERIAL_LORE.set(MONSTER_DROPS[k].item, dropLore(k));

world.afterEvents.playerLeave.subscribe(({ playerId }) => {
  forgetHp(playerId);
  forget(playerId);
  forgetCombat(playerId);
  forgetFx(playerId);
  forgetHeldWeapon(playerId);
  forgetSlash(playerId);
  forgetQuestPlayer(playerId);
  forgetSkills(playerId);
  forgetRunes(playerId);
  forgetMonsterFights(playerId);
  forgetNav(playerId);
  forgetLoot(playerId);
  forgetMount(playerId);
  held.delete(playerId);
});

// ------------------------------------------------------------ 손에 든 아이템 확인
/** @param {import("@minecraft/server").Player} player */
function heldTick(player) {
  const inv = player.getComponent("minecraft:inventory")?.container;
  if (!inv) return;
  const slot = player.selectedSlotIndex;
  const item = inv.getItem(slot);
  const id = item?.typeId ?? "";
  const prev = held.get(player.id);
  held.set(player.id, id);
  if (!item) return;

  // RPG 무기: 스탯 창 기준 무기로 기억하고, 설명이 없거나 예전 설명이면 새 설명으로 바꾼다
  const w = parseWeapon(id);
  if (w) {
    noteHeldWeapon(player, w.id);
    const enh = enhanceOf(item); // 강화 단계 (v1.5.0): 설명의 공격력과 이름 앞 +N
    rememberEnhance(player, w.id, enh); // 쏜 뒤 무기를 바꿔도 날아가던 화살·스킬은 이 단계로
    const want = weaponInfo(w, false, enh);
    let dirty = false;
    if (!loreMatches(item, want)) dirty = setLoreSafe(item, want) || dirty;
    if (enh > 0 && syncEnhanceName(item, w, enh)) dirty = true; // 모루로 이름을 바꾸면 "+N 새 이름"
    if (dirty) inv.setItem(slot, item);
    return;
  }
  // 갑옷 (v1.5.0): 설명이 없거나 예전 설명이면 새 설명
  const a = parseArmor(id);
  if (a) {
    const want = armorInfo(a);
    if (!loreMatches(item, want) && setLoreSafe(item, want)) inv.setItem(slot, item);
    return;
  }
  // 룬: 설명이 없거나 예전 설명이면 새 설명 (수치를 바꾸면 들 때 고쳐짐)
  const r = parseRune(id);
  if (r) {
    const want = runeLore(r);
    if (!loreMatches(item, want) && setLoreSafe(item, want)) inv.setItem(slot, item);
    return;
  }
  // 룬 뽑기권·레이드 재료(별철 광석·강화석·빛나는 강화석·수호 주문서)·스탯 책: /give·퀘스트 보상으로 받은 것에도 설명을 붙이고,
  // 설명이 바뀌었으면 (예: v1.6.0 뽑기권 "계열 10개") 들 때 새 설명으로
  if (id === RUNE.ticketItem) {
    if (!loreMatches(item, RUNE.ticketLore) && setLoreSafe(item, RUNE.ticketLore)) inv.setItem(slot, item);
    return;
  }
  const mat = MATERIAL_LORE.get(id);
  if (mat) {
    if (!loreMatches(item, mat) && setLoreSafe(item, mat)) inv.setItem(slot, item);
    return;
  }
  if (id === STAT_BOOK_ID) {
    if (!loreMatches(item, STAT_BOOK_LORE) && setLoreSafe(item, STAT_BOOK_LORE)) inv.setItem(slot, item);
    // 책을 손에 드는 순간 창을 연다 (접속 직후 첫 확인은 제외)
    if (prev !== undefined && prev !== STAT_BOOK_ID && !isFormOpen(player)) openStatWindow(player);
  }
}

/**
 * 경험치 레벨이 처음 오른 값이면 포인트 + 알림. 4틱마다 (v2.6.2 렙제: 오른 레벨을 1초 안에 써 버리거나 잃어도
 * 최고 레벨 — 무기·갑옷·퀘스트의 요구 레벨 · 포인트 · 최대 체력 — 이 기록되게)
 */
function levelUpTick(p) {
  const gained = checkLevelUp(p);
  if (gained > 0) {
    notify(p, `§e§l레벨 업! §r§f스탯 포인트 +${gained} §7(스탯 책으로 투자)`, 60);
    p.playSound("random.levelup", { pitch: 1.2 });
  }
}

// ------------------------------------------------------------ 주기 작업
function startLoops() {
  startFxLoop();

  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        updateHud(p);
      } catch {
        // 무시
      }
    }
  }, 2);

  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        levelUpTick(p);
        heldTick(p);
      } catch {
        // 무시
      }
    }
  }, 4);

  system.runInterval(() => {
    try {
      npcEffectsTick();
    } catch (e) {
      console.warn(`[rpg] npc fx: ${e}`);
    }
  }, 10);

  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        readStats(p);
        feedTick(p); // v2.6.3 배고픔 없음: 배고픔·포화 가득
        checkStuck(p);
        syncVanilla(p);
        saveHp(p);
        if (system.currentTick % 40 < 20) questTick(p);
      } catch {
        // 무시
      }
    }
    if (system.currentTick % 200 < 20) cleanupMarks();
  }, 20);
}

world.afterEvents.worldLoad.subscribe(() => {
  initObjectives();
  for (const p of world.getAllPlayers()) onJoin(p);
  startLoops();
});

// ------------------------------------------------------------ 명령어 (/scriptevent rpg:...)
const HELP = [
  "§6[RPG 명령어] §7채팅에 바로 입력",
  "§f/npc §7- NPC 관리 창 (NPC 만들기·이름·대사·퀘스트)",
  "§f/mainnpc 1 §7- 메인 퀘스트 NPC 세우기 (1~8 마을 · 13~16 지역 NPC, 9 대장장이 · 10 여관 · 11 왕녀 · 12 룬술사, 번호 없이 치면 목록)",
  "§f/quest §7- 내 퀘스트 목록, §f/stats §7- 스탯 창 (아랫줄 = 엔더 상자 · 탈것 소환, 탄 동안 공격 · 스킬 불가)",
  "§f/nav §7- 길 안내 켜기·끄기 (받은 퀘스트의 NPC·사냥터·보스 제단을 화살표와 빛으로 안내)",
  "§f/shop 10 §7- 10렙 무기·갑옷 상점, §f/weapon sword 10 rare §7- 무기 받기",
  "§f/smith §7- 대장장이 창, §f/enhance §7- 무기 강화 창, §f/enhanceset 7 §7- 손에 든 무기 강화 단계",
  "§f/armor str 40 §7- 갑옷 한 벌 (종류 str·dex·vit·strvit·int, 초월은 뒤에 transcend)",
  "§f/raidmat stone 10 §7- 레이드 재료 (ore·stone·bright·scroll), §f/ore 10 §7- 별철 광석",
  "§f/rune assassin hero §7- 룬 받기, §f/runequick auto·on·off §7- 룬 칸을 고르면 바로 사용",
  "§f/runeticket 10 advanced §7- (고급) 룬 뽑기권, §f/runeshard 40 §7- 룬 조각, §f/runesmith §7- 룬술사 창 (룬 갈기 · 뽑기권)",
  "§f/loot troll 10 §7- 몬스터 전리품 받기 (시험용 — 상점에서 팔기 · 반복 의뢰)",
  "§f/mob gulon 3 §7- 몬스터 소환 (scarecrow·gulon·skeljaskrimsli·nix·draugr·troll), §f/mob clear §7- 주변 몬스터 치우기",
  "§f/mobzone gulon 5 §7- 내 발밑에 사냥터 (잡으면 다시 나옴), §f/mobzone §7- 목록, §f/mobzone clear §7- 가까운 사냥터 지우기",
  "§f/boss altar scarecrow_king §7- 보스 제단 받기 (놓으면 보스 필드), §f/boss list·build·summon·reset·cooldown §7- 가까운 제단 관리",
  "§f/points 5 §7- 스탯 포인트, §f/heal §7- 체력 회복, §f/questreset §7- 퀘스트 기록 초기화",
  "§6[이전 명령어] §7/scriptevent 뒤에 입력",
  "§frpg:book §7- 스탯 책, §frpg:reset §7- 스탯 초기화, §frpg:title 제목|부제목 §7- 제목 표시",
  "§frpg:npc shop 10 §7/ §frpg:npc smith §7/ §frpg:npc runesmith §7/ §frpg:npc quest 촌장 §7/ §frpg:tag ... §7- NPC 지정",
];

/** @param {import("@minecraft/server").ScriptEventCommandMessageAfterEvent} ev */
function resolvePlayer(ev) {
  const cands = [ev.initiator, ev.sourceEntity];
  for (const c of cands) if (c && c.typeId === "minecraft:player" && c.isValid) return /** @type {any} */ (c);
  const origin = ev.sourceEntity?.location ?? ev.sourceBlock?.location;
  const dim = ev.sourceEntity?.dimension ?? ev.sourceBlock?.dimension;
  if (!origin || !dim) return undefined;
  const [p] = dim.getPlayers({ location: origin, closest: 1, maxDistance: 16 });
  return p;
}

system.afterEvents.scriptEventReceive.subscribe(
  (ev) => {
    const cmd = ev.id.slice(4);
    const raw = ev.message.trim();
    const args = raw.split(/\s+/).filter(Boolean);
    const player = resolvePlayer(ev);
    if (!player) return;
    const say = (t) => player.sendMessage(`§6[RPG] §f${t}`);
    try {
      switch (cmd) {
        case "help":
          player.sendMessage(HELP.join("\n"));
          break;
        case "stats":
          openStatWindow(player);
          break;
        case "quest":
          openQuestLog(player);
          break;
        case "npcmenu":
          openNpcManager(player);
          break;
        case "book":
          giveItem(player, createStatBook());
          say("스탯 정보 책을 받았습니다.");
          break;
        case "shop":
          openStore(player, Number(args[0] ?? LEVELS[0]), args[1] === "armor" ? "armor" : "weapons");
          break;
        case "give": {
          const key = args[0] ?? "";
          const w = parseWeapon(key.startsWith("rpg:") ? key : `rpg:${key}`);
          if (!w) {
            say("§c무기 ID 형식: 종류_l레벨_등급 (예: sword_l10_rare)");
            break;
          }
          if (w.legacy) {
            say(`§c${w.grade.name} 등급은 ${gradeLevelsText(w.grade.id)} 무기만 있습니다.`);
            break;
          }
          giveItem(player, createWeaponItem(w));
          say(`${w.name}§r§f 지급${levelNote(w, reqLevelOf(player))}`);
          break;
        }
        case "points": {
          const n = Math.floor(Number(args[0] ?? 1));
          if (!Number.isFinite(n)) break;
          addPoints(player, n);
          say(`스탯 포인트 ${n >= 0 ? "+" : ""}${n}`);
          break;
        }
        case "reset":
          say(`스탯 초기화: 포인트 ${resetStats(player)} 반환`);
          break;
        case "heal":
          healFull(player);
          say("체력을 회복했습니다.");
          break;
        case "title": {
          const [title, sub] = raw.split("|");
          showRealTitle(player, title ?? "", sub);
          break;
        }
        case "npc": {
          const role = args[0] === "shop" || args[0] === "smith" || args[0] === "runesmith" ? args[0] : "quest";
          const arg = role === "shop" ? args[1] : args.slice(1).join(" ");
          say(spawnRoleNpc(player, role, arg));
          break;
        }
        case "tag": {
          const role = ["shop", "smith", "runesmith", "clear"].includes(args[0]) ? args[0] : "quest";
          const arg = role === "shop" ? args[1] : args.slice(1).join(" ");
          say(tagNearestNpc(player, role, arg));
          break;
        }
        default:
          player.sendMessage(HELP.join("\n"));
      }
    } catch (e) {
      say(`§c오류: ${e}`);
    }
  },
  { namespaces: ["rpg"] },
);
