import { GameMode, system, world } from "@minecraft/server";
import { BOSS_ORDER, BOSSES, CONFIG, LEVELS, MONSTER_ORDER, MONSTERS, NPC, TRANSCEND_ORDER } from "./config.js";
import { isFormOpen } from "./chestui.js";
import { openNpcSettings } from "./editor.js";
import { actionbarFree, markPrompt, particle } from "./fx.js";
import {
  NPC_TOOL_ID,
  NPC_TYPE,
  greeting,
  isRunesmith,
  isSmith,
  mainNpcRecord,
  nearestNpc,
  noteNpcPos,
  npcKey,
  npcName,
  npcSpotNear,
  refreshNpc,
  setMainNpcRecord,
  setNpcKey,
  setNpcName,
  setRunesmith,
  setShop,
  setSmith,
  shopLevel,
  spawnNpcNear,
} from "./npcdata.js";
import { MAIN_NPCS, REGION_ORDER, REGIONS, STORY_NPC_NUMBERS, mainNpcByKey, mainNpcByNumber } from "./mainquest.js";
import { badgeCount, hasQuestFor, questMark, talkTo } from "./quest.js";

/**
 * RPG NPC 터치와 연출
 *   NPC 설정 도구를 들고 터치 → NPC 설정 창 (운영자·크리에이티브)
 *   RPG NPC 터치            → 대화창 (인사말·퀘스트·상점)
 *   크리에이티브에서 웅크리고 터치 → 기본 NPC 편집 화면 (스킨 바꾸기)
 */

/** 설정 도구로 NPC를 터치한 틱 (같은 터치로 itemUse 가 함께 오면 무시하려고) @type {Map<string, number>} */
const toolTap = new Map();

/** 설정 도구로 NPC(또는 허수아비)를 터치했다고 기록 (같은 터치의 itemUse 로 관리 창이 열리지 않게) */
export function markToolTap(player) {
  toolTap.set(player.id, system.currentTick);
}

/** 방금(2틱 안에) 설정 도구로 NPC를 터치했는지 */
export function recentToolTap(player) {
  return system.currentTick - (toolTap.get(player.id) ?? -100) <= 2;
}

/** 불러와진 RPG NPC의 자리를 적어 둠 (v2.6.1 길 안내 — 예전에 세운 중복 메인 NPC는 빼고) */
function noteLoadedNpc(e) {
  try {
    if (!e?.isValid || e.typeId !== NPC_TYPE || !e.hasTag("rpg_npc")) return;
    const key = npcKey(e);
    if (key && !staleMainNpc(e, key)) noteNpcPos(e, key);
  } catch {
    // 무시
  }
}

export function registerNpc() {
  // v2.6.1 길 안내: 청크가 불러와지며 나타난 NPC · 월드를 열 때 이미 불러와진 NPC의 자리
  world.afterEvents.entityLoad?.subscribe((ev) => noteLoadedNpc(ev.entity));
  world.afterEvents.worldLoad?.subscribe(() => {
    for (const id of ["overworld", "nether", "the_end"]) {
      try {
        for (const e of world.getDimension(id).getEntities({ type: NPC_TYPE, tags: ["rpg_npc"] })) noteLoadedNpc(e);
      } catch {
        // 무시
      }
    }
  });
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    const target = ev.target;
    if (target.typeId !== NPC_TYPE) return;
    const player = ev.player;
    if (ev.itemStack?.typeId === NPC_TOOL_ID) {
      toolTap.set(player.id, system.currentTick);
      ev.cancel = true;
      system.run(() => {
        if (player.isValid && target.isValid) openNpcSettings(player, target);
      });
      return;
    }
    if (!target.hasTag("rpg_npc")) return;
    if (player.getGameMode() === GameMode.Creative && player.isSneaking) return;
    // 이전 버전에서 태그만 붙인 NPC에 설정한 내용(인사말·퀘스트·상점)이 없으면 기본 NPC 대화를 그대로 쓴다
    const managed = typeof target.getDynamicProperty("rpg:npc_name") === "string";
    if (!managed && !greeting(target) && shopLevel(target) < 0 && !isSmith(target) && !isRunesmith(target) && !hasQuestFor(npcKey(target))) return;
    ev.cancel = true;
    system.run(() => {
      if (player.isValid && target.isValid) talkTo(player, target);
    });
  });
}

// ------------------------------------------------------------ /scriptevent 호환 (이전 버전 명령어)
function applyRole(npc, role, arg) {
  if (role === "clear") {
    for (const tag of npc.getTags()) if (tag.startsWith("rpg_")) npc.removeTag(tag);
    npc.nameTag = npcName(npc); // [!]·[상점]·[대장장이] 표시를 뗀 이름만
    return "역할을 지웠습니다.";
  }
  if (role === "smith") {
    setSmith(npc, true);
    if (arg) setNpcName(npc, arg);
    else if (!npc.getDynamicProperty("rpg:npc_name")) setNpcName(npc, "대장장이");
    refreshNpc(npc, badgeCount(npcKey(npc, true)));
    return "대장장이로 지정했습니다. §7영웅 무기의 레벨 제한을 올려 줍니다.";
  }
  if (role === "runesmith") {
    setRunesmith(npc, true);
    if (arg) setNpcName(npc, arg);
    else if (!npc.getDynamicProperty("rpg:npc_name")) setNpcName(npc, "룬술사");
    refreshNpc(npc, badgeCount(npcKey(npc, true)));
    return "룬술사로 지정했습니다. §7룬을 갈아 룬 조각으로, 룬 조각으로 뽑기권을 팝니다.";
  }
  if (role === "shop") {
    const lv = LEVELS.includes(Number(arg)) ? Number(arg) : LEVELS[0];
    setShop(npc, lv);
    if (!npc.getDynamicProperty("rpg:npc_name")) setNpcName(npc, "무기상");
    refreshNpc(npc, badgeCount(npcKey(npc, true)));
    return `${lv}렙 무기 상점으로 지정했습니다.`;
  }
  if (arg) setNpcName(npc, arg);
  refreshNpc(npc, badgeCount(npcKey(npc, true)));
  return "대화 NPC로 지정했습니다. §7퀘스트는 /npc 또는 NPC 설정 도구로 추가하세요.";
}

export function spawnRoleNpc(player, role, arg) {
  return applyRole(spawnNpcNear(player), role, arg || (role === "quest" ? "촌장" : undefined));
}

export function tagNearestNpc(player, role, arg) {
  const npc = nearestNpc(player, 6);
  if (!npc) return "6칸 안에 NPC가 없습니다.";
  return applyRole(npc, role, arg);
}

// ------------------------------------------------------------ 메인 퀘스트 NPC (v2.6.0, /mainnpc 번호)
/** 세운 메인 퀘스트 NPC (불러와진 곳에서만 찾음) */
function findMainNpc(key) {
  for (const id of ["overworld", "nether", "the_end"]) {
    let list = [];
    try {
      list = world.getDimension(id).getEntities({ type: NPC_TYPE });
    } catch {
      continue;
    }
    for (const e of list) {
      try {
        if (e.isValid && npcKey(e) === key) return e;
      } catch {
        // 무시
      }
    }
  }
  return undefined;
}

/** /mainnpc (번호 없이): NPC 목록과 필요한 사냥터·보스 제단 (v2.9.0: 지역마다 무엇을 둘지) */
export function mainNpcListText() {
  const others = MAIN_NPCS.filter((d) => !STORY_NPC_NUMBERS.includes(d.n)).map((d) => d.n);
  const lines = [`§6[메인 퀘스트 NPC] §f/mainnpc 번호§7 → 내 앞에 세움 (이미 세운 NPC는 이 자리로 옮김). ${STORY_NPC_NUMBERS.join("·")}번을 모두 세워야 이야기가 이어집니다 (${others.join("·")}번은 대장장이 · 반복 의뢰 · 왕실 퀘스트 · 룬술사)`];
  for (const d of MAIN_NPCS) lines.push(`§e${d.n} §f${d.name} §7- ${d.role}`);
  // v2.9.0 지역 퀘스트: 지역 NPC 곁에 그 지역의 사냥터 · 보스 제단
  for (const r of REGION_ORDER) {
    const R = REGIONS[r];
    const n = MAIN_NPCS.find((d) => d.key === R.npc)?.n ?? "?";
    lines.push(`§b${R.name} §7(${n}번 곁): 사냥터 §f${R.mobs.map((k) => `${MONSTERS[k]?.name ?? k} Lv.${MONSTERS[k]?.level ?? "?"}`).join(" · ")}§7, 제단 §f${R.bosses.map((k) => `${BOSSES[k]?.name ?? k} Lv.${BOSSES[k]?.level ?? "?"}`).join(" · ")}`);
  }
  lines.push(`§7사냥터 (/mobzone 종류 수 — 잡으면 다시 나옴): §f${MONSTER_ORDER.map((k) => `${MONSTERS[k].name} Lv.${MONSTERS[k].level}`).join(" · ")}`);
  lines.push(`§7보스 제단 (/boss altar): §f${BOSS_ORDER.map((k) => `${BOSSES[k].name} Lv.${BOSSES[k].level}`).join(" · ")}`);
  // v2.12.0 초월 보스: 메인 이야기 밖 (왕녀의 전설 퀘스트)
  if (TRANSCEND_ORDER.length) lines.push(`§c초월 보스 제단 (/boss altar · 왕녀의 전설 퀘스트): §f${TRANSCEND_ORDER.map((k) => `${BOSSES[k].name} Lv.${BOSSES[k].level}`).join(" · ")}`);
  return lines.join("\n");
}

/**
 * /mainnpc 번호: 미리 짜 둔 메인 퀘스트 NPC를 내 앞 2칸에 세운다 (이름·대장장이 역할·퀘스트가 붙은 채로).
 * 같은 번호의 NPC가 불러와진 곳에 이미 있으면 새로 만들지 않고 이 자리로 옮김
 * @param {import("@minecraft/server").Player} player
 * @param {number | undefined} n
 * @returns {string} 알림 글자
 */
export function mainNpcCommand(player, n) {
  const def = n === undefined ? undefined : mainNpcByNumber(Math.floor(n));
  if (!def) return mainNpcListText();
  const loc = npcSpotNear(player);
  const face = player.getHeadLocation();
  let npc = findMainNpc(def.key);
  if (npc) {
    npc.teleport(loc, { dimension: player.dimension, facingLocation: face });
    refreshNpc(npc, badgeCount(def.key));
    // 옮긴 직후에는 엔티티 위치가 아직 예전일 수 있어서 옮긴 자리를 직접 적음 (길 안내 v2.6.1)
    setMainNpcRecord(def.key, npc, loc, player.dimension.id);
    noteNpcPos(npc, def.key, loc, player.dimension.id);
    return `§6[메인 퀘스트] §f${def.n}번 §e${npcName(npc)}§f을(를) 이 자리로 옮겼습니다.`;
  }
  // 전에 세운 NPC가 불러와지지 않은 곳에 있으면: 새로 세우고, 예전 NPC는 가까이 가는 사람이 생기면 치움 (npcEffectsTick)
  const old = mainNpcRecord(def.key);
  npc = player.dimension.spawnEntity(NPC_TYPE, loc);
  try {
    npc.teleport(loc, { facingLocation: face });
  } catch {
    // 무시
  }
  setNpcKey(npc, def.key);
  setNpcName(npc, def.name);
  if (def.smith) setSmith(npc, true);
  if (def.runesmith) setRunesmith(npc, true);
  refreshNpc(npc, badgeCount(def.key));
  setMainNpcRecord(def.key, npc, loc, player.dimension.id);
  noteNpcPos(npc, def.key, loc, player.dimension.id); // 길 안내 (v2.6.1): 새로 세운 자리 (기록을 바꾼 뒤에 — 그 전에는 예전 NPC의 번호)
  const head = npc.getHeadLocation();
  particle(npc.dimension, "minecraft:totem_particle", { x: head.x, y: head.y + 0.4, z: head.z });
  player.playSound("random.orb", { pitch: 1.2 });
  const note = old ? `\n§7전에 세운 ${def.n}번 NPC(${old.x} ${old.y} ${old.z})는 불러와지지 않은 곳에 있어 찾지 못했습니다. 그 NPC는 근처에 사람이 가면 사라집니다.` : "";
  return `§6[메인 퀘스트] §f${def.n}번 §e${def.name}§f을(를) 세웠습니다 §7(${def.role})${note}`;
}

/** 예전에 세운 메인 NPC (같은 번호로 더 나중에 세운 NPC가 있음)인지 */
function staleMainNpc(npc, key) {
  if (!mainNpcByKey(key)) return false;
  const rec = mainNpcRecord(key);
  return !!rec && rec.id !== npc.id;
}

// ------------------------------------------------------------ 연출
/** @type {Map<string, number>} */
const nearSince = new Map();

function promptText(mark, lv, name, smith, runesmith = false) {
  if (mark === "ready") return `§a§l? §r§f${name} §7- 터치해서 퀘스트 완료 보고`;
  if (mark === "available") return `§e§l! §r§f${name} §7- 터치해서 퀘스트 받기`;
  if (mark === "repeat") return `§b§l! §r§f${name} §7- 터치해서 반복 의뢰 받기`;
  if (smith) return `§6§l[대장장이] §r§f${name} §7- 터치해서 무기 강화·레벨 올리기`;
  if (runesmith) return `§d§l[룬술사] §r§f${name} §7- 터치해서 룬 갈기·뽑기권 사기`;
  if (lv >= 0) return `§a§l[상점] §r§f${name} §7- 터치해서 무기·갑옷 상점·전리품 팔기`;
  return `§f${name} §7- 터치해서 대화하기`;
}

/**
 * 머리 위 파티클과 접근 안내 (10틱마다)
 * - 받을 퀘스트가 있으면 노란 파티클, 보고할 퀘스트가 있으면 흰 반짝임 (플레이어마다 다르게 보임)
 * - 상점 NPC는 초록 파티클, 대장장이는 불똥, 룬술사는 보랏빛 (v2.7.0). 받을 것이 반복 의뢰뿐이면 파란 불꽃 (그 플레이어에게만)
 */
export function npcEffectsTick() {
  if (!CONFIG.NPC_EFFECTS) return;
  const now = system.currentTick;
  const shopDone = new Set();
  const checked = new Set();
  for (const player of world.getAllPlayers()) {
    let npcs;
    try {
      npcs = player.dimension.getEntities({
        type: NPC_TYPE,
        tags: ["rpg_npc"],
        location: player.location,
        maxDistance: NPC.particleRange,
      });
    } catch {
      continue;
    }
    for (const npc of npcs) {
      const key = npcKey(npc);
      // 퀘스트가 생기거나 없어지면 이름표의 [!] 를 맞춘다
      if (!checked.has(npc.id)) {
        checked.add(npc.id);
        // v2.6.0: 같은 번호의 메인 NPC를 다른 곳에 새로 세웠으면 예전 것은 치움
        if (staleMainNpc(npc, key)) {
          try {
            const h = npc.getHeadLocation();
            particle(npc.dimension, "minecraft:death_explosion_emitter", { x: h.x, y: h.y - 0.6, z: h.z });
            npc.remove();
          } catch {
            // 무시
          }
          continue;
        }
        const count = badgeCount(key);
        if ((count > 0) !== npc.hasTag("rpg_quest")) refreshNpc(npc, count);
        if (key) noteNpcPos(npc, key); // v2.6.1 길 안내: 옮긴 NPC의 새 자리 (바뀌었을 때만 저장)
      }
      if (!npc.isValid) continue;
      const mark = questMark(player, key);
      const lv = shopLevel(npc);
      const smith = isSmith(npc);
      const runesmith = isRunesmith(npc);
      const h = npc.getHeadLocation();
      const at = { x: h.x, y: h.y + 0.75, z: h.z };
      if (mark) {
        try {
          player.spawnParticle(mark === "ready" ? NPC.readyParticle : mark === "repeat" ? NPC.repeatParticle : NPC.questParticle, at);
        } catch {
          // 무시
        }
      } else if ((lv >= 0 || smith || runesmith) && !shopDone.has(npc.id)) {
        shopDone.add(npc.id);
        particle(npc.dimension, smith ? NPC.smithParticle : runesmith ? NPC.runesmithParticle : NPC.shopParticle, at);
      }

      const pk = `${player.id}:${npc.id}`;
      const d = Math.hypot(npc.location.x - player.location.x, npc.location.y - player.location.y, npc.location.z - player.location.z);
      if (d <= NPC.promptRange) {
        const since = nearSince.get(pk);
        if (since === undefined) {
          nearSince.set(pk, now);
          if (mark) player.playSound("random.orb", { pitch: mark === "ready" ? 1.8 : 1.4, volume: 0.7 });
          else if (smith) player.playSound("random.anvil_land", { pitch: 1.4, volume: 0.35 });
          else if (runesmith) player.playSound("chime.amethyst_block", { pitch: 1.2, volume: 0.6 });
          else if (lv >= 0) player.playSound("mob.villager.yes", { volume: 0.8 });
        }
        const due = since === undefined || (now - since) % 40 < 10;
        // 창이 열려 있거나 퀘스트 알림을 보여 주는 중이면 덮어쓰지 않는다
        if (due && !isFormOpen(player) && actionbarFree(player)) {
          player.onScreenDisplay.setActionBar(promptText(mark, lv, npcName(npc), smith, runesmith));
          markPrompt(player);
        }
      } else if (nearSince.has(pk) && d > NPC.promptRange + 1) {
        nearSince.delete(pk);
      }
    }
  }
  if (nearSince.size > 500) nearSince.clear();
  if (toolTap.size > 200) toolTap.clear();
}
